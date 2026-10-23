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
> 🟡 **本文全部条目状态为「待签字」** —— 依据 AGENTS.md §0 第 1 条，
> 「RAG 做了更严 / 更安全的那一侧」是 2026-10-23 修复轮（`SD-2026-10-23-01/02/03`）已确立的**签字惯例**。
> 本轮沿用该惯例**提出候选**，但**代签权在用户** ⇒ 每条都写明了「签字人：______」。

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
| 状态 | 🟡 **待签字**（候选，建议签） |
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

### 为什么这是「更全」而不是「更宽」

多一张表**不改变任何其他路径的行为**，只让一条 Go 侧走不通的路径走得通。
⇒ 与 CAND-01 不同，它**不是「更严」**，而是「**RAG 补上了 Go 的缺口**」。

### ⚠️ 需要一并决策的点

`V5` 的脚本头部**自己写明了**这是「有意偏离、尚未签字」，并给出了撤销方式（**删掉 V5 即完全退回 Go 的「全新部署必 5004」**）。
⇒ **本条不代签**。

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

## 其他

| 项 | 说明 |
|---|---|
| `SD-2026-10-23-04`（proto `package` 归一） | ✅ **已签字**，明细在 [`comment/proto.md`](./proto.md) —— **本轮复核结论不变**，但其「无实际影响」的前提已过期，见该文件 §3 |
