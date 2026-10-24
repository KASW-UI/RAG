<!-- @new 2026-10-24 · comment 域 rpc 组件的待签字有意偏离（DDL 更严侧 + 部署形态） -->
<!-- 产出归属：`align/comment` 工作树 · 基线 commit `8422116` -->

# `comment` 域 rpc 层待签字偏离

> 归类：**「决定不对齐的差异」**（见 [`../_INDEX.md`](../_INDEX.md)）。
> 与 [`../../DRIFT/`](../../DRIFT/_INDEX.md) 的分工：
> **DRIFT 记「需要对齐的差异」，本文记「决定不对齐的差异」**。
>
> ⚠️ **读之前先记住：签字偏离是决策，不是缺陷。**
> 审查时**不必重复质疑**，**也不得「顺手修正回 Go 的旧行为」**。
>
> 🟡 **本文条目状态分两种** —— 依据 AGENTS.md §0 第 1 条，
> 「RAG 做了更严 / 更安全的那一侧」是 2026-10-23 修复轮（`SD-2026-10-23-01/02/03`）已确立的**签字惯例**。
> 本轮沿用该惯例**提出候选**，但**代签权在用户** ⇒ 每条都写明了签字栏。
>
> ✅ **2026-10-24 用户已签字**：**CAND-02**（`report_record` 表）、**CAND-06**（`manage` 清位）。
> 🟡 **仍待签字**：**CAND-01**（9 处时间列）、**CAND-03**（authority / dispatch 默认关闭）、**CAND-09**（RAG 的 DDL 比 Go 生产 DDL 更严：约束 / 索引 / 触发器层）。

---

## SD-2026-10-24-CAND-01：9 处时间列的 `NOT NULL DEFAULT CURRENT_TIMESTAMP`（RAG 更严）

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字**（候选，建议签） |
| 组件 | `service/comment/comment-rpc` |
| 行为层 | DDL 约束层（**列级 60/60 已一致**，差异只在 `NOT NULL` 与 `DEFAULT`） |
| 涉及表 | `subject` · `comment_index` · `comment_content` · `comment_like` · `report_record` |
| 涉及列 | 9 处 `created_at` / `updated_at`（`report_record` 只有 `created_at`） |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | gorm tag 只有 `autoCreateTime` / `autoUpdateTime` ⇒ **不产生 NOT NULL、也不产生 DB 层 DEFAULT**（`schema/field.go:129` NotNull 只认 tag；`migrator.go:90-108` 不为 autoTime 加 DEFAULT） | `service/comment/rpc/internal/model/data_model.go:14-15,34-35,47-48,95-96,63` |
| **Go（手写 DDL）** | `comment_like` 的 `001` 迁移脚本明文写 `created_at timestamptz` / `updated_at timestamptz` —— **可空、无默认** | `migrations/001_*.sql:19-20` |
| **RAG** | 统一 `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | `V1:27-28` · `V2:22-23` · `V3:18-19` · `V4:24-25` · `V5:34` |

### 为什么这是「更严」而不是「更松」

| 情形 | Go 侧 | RAG 侧 |
|---|---|---|
| 应用层**漏传** `created_at` | 落 **NULL** ⇒ wire 上被 `get_comment_logic.go:139` 格式化成 `0001-01-01 00:00:00`（**明显错**，能立刻发现） | 补「**入库时刻**」（**看起来合理的错值**，能悄悄骗过人） |
| 应用层漏传 `updated_at` | 落 NULL | 补入库时刻 |
| 应用层正常传值 | 正常 | 正常（**当前全部写路径都显式传值**） |

⇒ **当前行为等价**（RAG 的 5 条写路径全部显式传值：`SubjectMapper.java:86` · `CommentIndexMapper.java:42-43` · `CommentContentMapper.java:44-47` · `CommentLikeMapper.java:42` · `ReportRecordMapper.java:44`）。
⇒ 差异是**潜伏的**：RAG 侧一旦有人漏传，`DEFAULT` 会**掩盖**那个 bug 而不是暴露它。

### 🔴 反向理由（为什么**不建议**直接改回 Go）

1. **改回去要删 9 处 `NOT NULL` + 9 处 `DEFAULT`**，而 `comment_domain_fact_outbox.created_at` 的 Go tag **明写** `not null;default:CURRENT_TIMESTAMP`（`domain_fact.go:29`）⇒ RAG 的 `V6:24` 是**对的**。
   ⇒ **同一个 Go 仓里，两张表的时间列策略就不一样** —— 强行把 9 处改成「可空无默认」只会让 RAG 内部也不自洽。
2. `DEFAULT CURRENT_TIMESTAMP` 在 PG 中是**事务开始时刻**、以 `timestamptz` 存储 ⇒ **不引入时区偏移**（与 `comment.yaml:9` / `application.yml:27` 的 `TimeZone=Asia/Shanghai` 无关）。
3. 本仓已确立的签字惯例（`SD-2026-10-23-01/02/03`）**共性正是「RAG 做了更严/更安全的那一侧」** ⇒ 本条落在该惯例内。

### 修法（若将来要「更严且不掩盖」）

把 `DEFAULT CURRENT_TIMESTAMP` 去掉、**保留** `NOT NULL` ⇒ 漏传时直接**插入失败**（比 Go 的落 NULL 更早暴露，比 RAG 现在的「静默补值」更安全）。
⚠️ 这是**第三条路**，比「照搬 Go」和「保持现状」都好 —— 但它**改变了 DDL**，需要单独决策。

**签字人：______　签字日期：______**

---

## SD-2026-10-24-CAND-02：`report_record` 表在 RAG 侧存在，Go 侧**根本不存在**

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字**（2026-10-24，用户 Jason） |
| 组件 | `service/comment/comment-rpc` |
| 行为层 | 表存在性（**直接决定 `ReportComment` 能否成功**） |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `AutoMigrate` 列表**不含** `&ReportRecord{}` ⇒ **表不会被创建**；3 个手写迁移脚本也都不建它 | `service/comment/rpc/internal/model/db.go:15`（列表：`Subject` / `CommentContent` / `CommentIndex` / `CommentLike` / `CommentDomainFactOutbox` / `CommentFactStream`） |
| **Go** | `ReportRecord` 的 struct **存在**且 `TableName() = "report_record"` ⇒ 代码路径完整 | `data_model.go:55-68` |
| **Go** | `InsertReport` 用 `ON CONFLICT DO NOTHING` 插入 ⇒ 表不存在时**插入报错** ⇒ logic 发 `errmsg.ErrorDbInsert`(5004) | `comment.go:242-250` + `report_comment_logic.go:88-92` |
| **RAG** | `V5__create_comment_report_record.sql` 建表（8 列 + 复合唯一索引 `idx_user_comment_report`） | `V5:26-45` |

⇒ **Go 侧全新部署上，`ReportComment` 必然返回 5004** —— 这不是「Go 的 bug」，是**Go 侧漏把表加进 AutoMigrate**。
⇒ **RAG 侧比 Go 多一张表 ⇒ 「举报」功能可用**。

### 🟢 活体对拍实证（2026-10-24，本条从「静态推断」升级为「运行期观测」）

| 侧 | 实际响应 | 证据 |
|---|---|---|
| **Go** | `code=5004` `msg=DB插入失败` | Go rpc 日志：`comment.go:243 ERROR: relation "report_record" does not exist (SQLSTATE 42P01)`；`go_comment` 库的 `\dt` 只有 6 张业务表、**无 `report_record`** |
| **RAG** | `code=200` `{success:true}` | `rag_comment` 库有 `report_record`；行已落库（`count=1`） |

⚠️ 这次对拍**收紧了一个前提**：本条原先只写「Go 侧全新部署必然 5004」，
容易被读成「**Go 现有环境有这张表、只是迁移脚本没写**」。
**实测否定了这一点** —— 全新库上 Go 侧**根本没有**这张表，**RAG 侧与 Go 侧的差异是运行期真实存在的**。
⇒ 本条**不是「表述差异」**，是**功能面差异**：同一份请求，Go 报错、RAG 成功。

### 为什么这是「更全」而不是「更宽」

多一张表**不改变任何其他路径的行为**，只让一条 Go 侧走不通的路径走得通。
⇒ 与 CAND-01 不同，它**不是「更严」**，而是「**RAG 补上了 Go 的缺口**」。

### ⚠️ 需要一并决策的点

`V5` 的脚本头部**自己写明了**这是「有意偏离、尚未签字」，并给出了撤销方式（**删掉 V5 即完全退回 Go 的「全新部署必 5004」**）。
⇒ **本条不代签**。

### ✅ 签字结论（2026-10-24，用户 Jason：**接受签字**）

- **决策**：保留 `V5__create_comment_report_record.sql` ⇒ RAG 侧 `report_record` 表**继续存在**，
  `ReportComment` 在 RAG 侧**继续可用**（Go 侧仍会 5004，这是**已知的真源侧缺陷**，不在本轮修复范围）。
- **撤回方式（保留备查）**：删除 `V5` 迁移 ⇒ RAG 侧退化成与 Go 一致的「全新部署必 5004」。
  ⚠️ **已应用的迁移不可回滚**（Flyway 只增不改）⇒ 真要撤，需要**新写一个 V9 DROP TABLE**，而不是删文件。
- **连带动作**：`V5` 脚本头部的「该决定留给用户签字，本文件不代签」⇒ 已改为「**已签字（2026-10-24）**」。

**签字人：Jason（用户）　签字日期：2026-10-24**

---

## SD-2026-10-24-CAND-09：RAG 的 DDL **比 Go 生产 DDL 更严**（约束 / 索引 / 触发器层）

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字**（候选，建议签）—— **不代签** |
| 组件 | `service/comment/comment-rpc` |
| 行为层 | **DDL 约束层**（索引 / 唯一约束 / CHECK / 触发器） |
| 证据强度 | ✅ **活体 schema 差分**（两个活库 `go_comment` vs `rag_comment` 的 `pg_constraint` / `pg_indexes` / `pg_trigger` 逐条 diff） |

### 根因：**RAG 抄的是 Go 的「测试夹具 DDL」，不是「生产 DDL」**

| 路径 | 谁执行 | 何时 |
|---|---|---|
| `rpc/internal/model/db.go:15` `db.AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{}, &CommentDomainFactOutbox{}, &CommentFactStream{})` | **生产启动路径** | 每次进程启动 |
| `rpc/internal/model/migrations/001_*.sql` / `002_*.sql` / `003_*.sql` | **仅** `rpc/internal/model/test-community-postgres.sh:47,49` | **只被测试脚本执行** |

⇒ **Go 生产的权威 DDL = GORM `AutoMigrate` 的产物**；
`001/002/003` 那三份手写迁移是**测试夹具**，**不进生产**。
⇒ **RAG 的 V4/V6/V7/V8 照抄的是那三份测试夹具** ⇒ 于是 RAG **多**了 Go 生产没有的约束。

### 活体差分清单（`go_comment` = Go 生产 DDL 的产物 vs `rag_comment`）

| 对象 | Go 生产 | RAG | 可观察性 |
|---|---|---|---|
| **trigger** `comment_fact_identity_immutable`（BEFORE UPDATE，禁改 `event_id`/`event_type`/`payload`/`aggregate_id`/`fact_version`/`delivery_envelope`） | ❌ **无** | ✅ **有** | ⚠️ **唯一原则上可观察的** —— UPDATE 受保护列 ⇒ RAG 抛 `comment fact envelope is immutable`，Go 生产不抛。**但现有写入/投递路径都不碰受保护列**（`CommentDomainFactOutboxMapper` 的 UPDATE 只写 `delivery_status`/`retry_count`/`technical_*`/`delivered_at`）⇒ **当前不可观察** |
| **CHECK** `comment_new_fact_has_dc_wire`（NOT VALID） | ❌ 无 | ✅ 有 | 不可观察 —— `append` 永远填齐 `aggregate_id`/`fact_version`/`delivery_envelope` |
| **CHECK** `comment_fact_stream_version_range`（`version BETWEEN 1 AND 2^53-1`） | ❌ 无 | ✅ 有 | 不可观察 —— `version` 从 1 递增，要到 2^53-1 才触发 |
| **索引** `idx_comment_fact_delivery`（`delivery_status, created_at, event_id`） | ❌ 无 | ✅ 有 | 纯性能，无行为差异 |
| **索引** `idx_comment_legacy_fact_aggregate`（部分索引） | ❌ 无 | ✅ 有 | 纯性能，无行为差异 |
| `uk_comment_fact_stream` | ✅ **有**（模型 tag `uniqueIndex` ⇒ AutoMigrate 会建） | ✅ 有 | ✅ 一致 |
| `idx_user_comment_like` | ✅ **唯一索引** | ✅ **表约束** | 形态差异，**语义等价**（都保证 `(user_id, comment_id)` 唯一） |

### 为什么倾向签字（而不是改回 Go）

1. **全部是「RAG 更严」** —— RAG 多出来的约束**只会更早拒绝**，不会让 Go 能过的写入在 RAG 上失败（当前写入路径已验证）。
2. **改回 Go 意味着删掉这些约束/索引** ⇒ **降低数据完整性**，且要改**已应用的 Flyway 迁移**（V6/V7/V8 已落库 ⇒ 只能新写 V9 DROP）⇒ 成本高、风险大。
3. **与 CAND-01 同族**（RAG 的 DDL 更严侧），保持处置口径一致。

### ⚠️ 需要一并决策的点

- **签字 = 接受「RAG 生产库比 Go 生产库约束更多」** ⇒ 将来任何「让 Go 侧直连 RAG 的库」的方案都要先评估这层差异。
- 若不签 ⇒ 必须为 V6/V7/V8 的这些对象**新写 V9/V10 DROP** ⇒ 但**触发器一去，事实信封的不可变性就没了** ⇒ 与 Go 的设计意图相反 ⇒ **不建议**。
- ⚠️ **与 `CAND-01` 的关系**：CAND-01 是「时间列 NOT NULL DEFAULT」；本条是「约束/索引/触发器」⇒ **两个独立条目**，可分别签。

**签字人：______　签字日期：______**

---

## SD-2026-10-24-CAND-03：authority / dispatch 两个子命令**默认关闭**

| 项 | 值 |
|---|---|
| 状态 | 🟡 **待签字**（候选，建议签） |
| 组件 | `service/comment/comment-rpc` |
| 行为层 | **部署形态**（默认部署下的可观察面） |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | `cmd/fact-authority/main.go:52/75-80` = **独立进程 + 独立监听端口**（`COMMENT_FACT_AUTHORITY_LISTEN`）⇒ **默认就在跑** | |
| **Go** | `cmd/fact-dispatch/main.go:52-77` = 独立进程，缺 `COMMENT_DATABASE_URL` / `DC_PLATFORM_EVENT_URL` / `DC_PLATFORM_SERVICE_TOKEN(≥32B)` 任一即**进程退出** ⇒ **默认就在投递** | |
| **RAG** | `CommentAuthorityEndpointConfig.java:42` `@ConditionalOnProperty(comment.authority.enabled=true)`；`application.yml:196` 默认 `false`；且 **comment-rpc 的 pom 无 `spring-boot-starter-web`** ⇒ 即使打开也起不来（该类 `:32-34` 自记此点） | |
| **RAG** | `CommentFactDispatchScheduler.java:58-63` + `application.yml:178` `dispatch-enabled: false` ⇒ 默认整条投递链路**不参与运行** | |

### 为什么这是「有意」而不是「漏做」

1. Go 的 authority / dispatch 是**两个独立进程**，各自的失败不影响 comment-rpc 主进程。
2. RAG 是**单 JVM**（同时跑 gRPC server + Kafka 消费者 + promhttp）⇒ 若照搬「缺配置即退出」，**一个 DC 配置缺失就会让整个 comment-rpc 起不来**。
   ⇒ RAG 改成「**默认关闭 + 启动时显式告警**」，关闭时该功能完全不参与运行（`application.yml:171-176` 有完整推导）。
3. **同仓先例**：`like-rpc` / `message-rpc` / `article-rpc` 三个模块的同类文件都采用同一形态。

### 🔴 残留风险（必须与签字一起记录）

- 默认部署下 **RAG 侧不会向 DC 投递任何事实**（Go 侧会）⇒ **DC 侧看到的事实流会与 Go 侧不一致**。
- 开启 authority 端点需要**新增一个 HTTP 端口** ⇒ 属 **wire 面变更**，须人工拍板对外暴露面。

**签字人：______　签字日期：______**

---

## SD-2026-10-24-CAND-06：`manage` 的**清位**分支 —— Go 侧 SQL **必然报错**，RAG 侧正常

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字**（2026-10-24，用户 Jason） |
| 组件 | `service/comment/comment-rpc` |
| 行为层 | **RPC 响应码**（`ManageComment` 的 4 个 action_type 里有 2 个） |
| 触发动作 | `MANAGE_UNPIN`（取消置顶）/ `MANAGE_UNFEATURE`（取消精选）—— 即 `action_type=2` / `action_type=4` |
| 证据 | ✅ **2026-10-24 双侧活体对拍**（Go 真源 + RAG 同跑，同一份请求） |

### 差异的实际面

| 侧 | 事实 | 出处 |
|---|---|---|
| **Go** | 清位用 `gorm.Expr("attribute & ~?", val)`，`val` 是 `uint` ⇒ PG 收到**未定型参数** ⇒ **`operator is not unique: ~ unknown (SQLSTATE 42725)`** ⇒ logic 发 `errmsg.ErrorDbUpdate`(5002) | `service/comment/rpc/internal/model/comment.go:157-165`（`expr = gorm.Expr("attribute & ~?", val)` 在 `:165`）；错误日志 `comment.go:168` |
| **Go** | `manage_comment_logic.go:66-86` 的映射：`MANAGE_PIN ⇒ bitOffset=2, isSet=true`；`MANAGE_UNPIN ⇒ bitOffset=2, isSet=false` ⇒ **只有清位分支会炸** | `manage_comment_logic.go:66-86` |
| **RAG** | 清位用 `attribute & ~#{mask}`，`mask` 是**有类型的 `long`** ⇒ PG 解析为 `~ bigint` ⇒ **成功**（200 `{success:true}`） | `CommentIndexMapper.java:189`（`clearAttributeBits`）+ `CommentDomainService.java:627-651`（mask 计算与 Go 逐位一致） |

### 证据链（可复现）

1. **活体对拍**：同一请求 `action_type=2` ⇒ Go `code=5002 msg=DB更新失败` / RAG `code=200 {success:true}`。
2. **Go 日志原文**：`comment.go:168 ERROR: operator is not unique: ~ unknown (SQLSTATE 42725)`。
3. **PG 直接复现**（排除「是不是 RAG 侧环境问题」）：
   ```sql
   PREPARE t(unknown) AS SELECT 4::bigint & ~$1;  -- $1 为未定型
   EXECUTE t('4');
   -- ERROR:  operator is not unique: ~ unknown
   -- HINT:  Could not choose a best candidate operator.
   ```
   ⇒ **同样的 SQL 形态，在 PG 上必然报错** ⇒ 根因在 Go 的**参数定型**，不在 RAG。

### 为什么这是「更对」而不是「更宽」

- Go 的**意图**很清楚：`attribute & ~mask`（清掉第 N 位）。
  RAG 实现的是**同一个意图**，只是让 PG 能定型 ⇒ **RAG 侧执行的是 Go 想执行的那条语句**。
- ⚠️ **不能改成 `attribute - mask`**：借位会把相邻低位全部翻成 1（Go 的注释在 `comment.go:163` 附近也强调过位运算而非减法）。
- **Go 侧「取消置顶 / 取消精选」两个功能在真源上就是 100% 失败的** ⇒ 照搬 Go ⇒ RAG 侧这两个功能也会 100% 失败。

### ⚠️ 一并需要决策的点

1. **同款缺陷在 Go 的点赞路径上潜伏**：`comment.go:571` 是**同一个** `attribute & ~?` 写法
   （仅当 `userId == ownerId` 且取消赞时触发）⇒ **Go 侧「作者取消自己的赞」也会 5002**。
   本轮对拍用 `user 1001 ≠ owner 2002` ⇒ **未触发**（两侧一致）。
   ⇒ RAG 侧 `applyInteractionDeltas` 用**有类型的** `long` mask ⇒ 正常。
   ⚠️ **本条若签字，含义要覆盖两处**（manage 清位 + 点赞清位），不只是 manage。
2. **若不签**（要求与 Go 完全一致）⇒ RAG 侧必须**故意**让这两个 action 失败 ⇒ 需要一条测试把这个行为钉死，
   否则下一次「顺手优化」就会把它改回去。

### 撤销方式

把 `CommentDomainService` 的清位分支改成**故意抛 `dbUpdateOnManage()`**（模拟 Go 的 5002）⇒ 即完全退回 Go 的可观察行为。
**该决定留给用户签字，本条不代签。**

### ✅ 签字结论（2026-10-24，用户 Jason：**接受签字**）

- **决策**：RAG 侧**保留**正确实现 —— `manage` 的清位（`MANAGE_UNPIN` / `MANAGE_UNFEATURE`）
  与点赞的清位（作者取消自己的赞）**都继续正常工作**，
  **不**去复刻 Go 的 `operator is not unique: ~ unknown` 缺陷。
- **覆盖范围**（如上文「需要一并决策的点」第 1 条所列，签字含义覆盖**两处**）：
  ① `comment.go:165`（manage 清位）② `comment.go:571`（点赞清位）。
  ⚠️ RAG 侧对应实现**两处都已是有类型的 `long` mask**（`CommentIndexMapper.clearAttributeBits` 与
  `applyInteractionDeltas` 的 `ownerAttributeClear`）⇒ **无需改代码**，签字即闭环。
- **后续影响**：Go 侧这两个动作 100% 失败 ⇒ **Go↔RAG 对拍时它们必然是差异**，属**已签字偏离**，
  **不得**被当成新回归去「修」回 Go 侧行为。
- **真源侧遗留**（记录，不在 RAG 修复范围）：Go `comment.go:165` 与 `:571` 的未定型参数，
  以及 Go 侧缺 `report_record` 表（CAND-02）⇒ 均应向真源仓反馈。

**签字人：Jason（用户）　签字日期：2026-10-24**

---

## 其他

| 项 | 说明 |
|---|---|
| `SD-2026-10-23-04`（proto `package` 归一） | ✅ **已签字**，明细在 [`comment/proto.md`](./proto.md) —— **本轮复核结论不变**，但其「无实际影响」的前提已过期，见该文件 §3 |
