# L3 · `align/global-ddl` —— CD-7 残留（5 个域的 DDL 双侧活体逐列对照）

> 本文件既是**任务书**也是本 lane 的**台账片段**。协议见 [`_PROTOCOL.md`](_PROTOCOL.md)。

## 一、目标

把 `global` 域待办 **B2** 做完：DDL 双侧活体逐列对照现在**只有 4 个域预设**
（`users` / `task` / `message` / `admin`）⇒ 其余域**只到源码级**。

**剩余 5 域**：`article` / `like` / `hot` / `follow` / `favorite`

⚠️ **`favorite` 是硬禁区** ⇒ **只建库、不碰其代码**（§七之二 ⛔ 条）⇒
本 lane 对 favorite 的口径 = **只跑对照、不提修复**。

## 二、现状（别重复已做的）

| 域 | 状态 | 出处 |
|---|---|---|
| `users` / `admin` | ✅ 类型/长度 0 差异 | `tools/difftest/check-ddl-static.py` 的 `DOMAINS` |
| `task` | ✅ 表名两侧相同 ⇒ 映射表为空 | 脚本注释（`AutoMigrate` vs Flyway，**表名完全一致**） |
| `message` | ✅ 已补 | 脚本注释 + `tools/difftest/run-message-ddl-parity.sh`（⚠️ message 的基建是 `msgddl-pg`，不是 `difftest-postgres`） |

⚠️ **踩过的坑（照抄即可）**：
- `admin` 侧是**两端都改名**（不是一端改）⇒ 写错映射会让脚本**静默「列集合不一致」而不报错**。
- 脚本**默认不开**某些比对（已知差异会刷屏）⇒ 别把默认行为当「没差异」。

## 三、必读（定向读）

| 读什么 | 在哪 | 读哪几段 |
|---|---|---|
| CD-7 的逐条实测 | `docs/DRIFT/global/cross-domain.md` | **§二·五**（含「① 已有域预设本身已失效」/「④ 工具改动」） |
| 域待办 | `docs/DRIFT/global/_README.md` | §七之二 **B2** 行 |
| 工具现状 | `tools/difftest/check-ddl-static.py` | 全文（**脚本头注释就是使用说明**，含 `--domain` / `--indexes` / 容器参数） |
| runner 样板 | `tools/difftest/run-message-ddl-parity.sh` | 全文（message 域的专用基建怎么拉起来的） |
| DDL 结论（已知未修项） | `docs/DRIFT/_cross/ddl-type-divergences.md` | 全文 |
| Go 真源 | `Sea-RideTheWind_golang/service/<域>/**/model/*.go` 的 `AutoMigrate` | 逐字读 |

## 四、写权范围（白名单）

**只写这些**：
- `tools/difftest/check-ddl-static.py`（扩 `DOMAINS` 预设）
- `tools/difftest/` 下**新**建的脚本 ⇒ ⚠️ **文件名必须以 `ddl` 开头**（如 `check-ddl-article.sh` / `run-ddl-parity.sh`）
  ⇒ 与 L4 的 `check-flyway-concurrent.sh` **天然不撞名**（两条 lane 同写 `tools/difftest/` 目录 ⇒ git 只跟踪文件不跟踪目录 ⇒ 文件名不撞即零冲突）
- `docs/DRIFT/global/lanes/L3-ddl.md`（本文件）

**⛔ 禁区**：
- `service/**`（**本 lane 一行生产代码都不改** —— 这是**对照**任务，不是修复任务）
- `service/favorite/**` 与 `docs/*/favorite/`
- `tools/difftest/check-flyway-concurrent.sh`（L4 的）
- 4 个规范台账文件 · `AGENTS.md` · 三个 `_INDEX.md`

## 五、交付物

1. **5 个域的逐列对照结果**（类型 / 长度 / 可空性 / DEFAULT / 索引与约束）。
2. 扩域后的 `check-ddl-static.py` ⇒ **能重跑**、**幂等**、**退出码有语义**（有差异 ⇒ 非 0）。
3. 一张「差异表」：哪些是真漂移、哪些是已签字偏离、哪些是「只记不修（性能非行为）」。
4. 真漂移**登记**（不修 —— 修是别的 lane / 别的域的事）⇒ 写进本文件末尾。
5. 台账：本文件末尾追加「逐域结果 + 差异分类 + 证据（`文件:行号`）」。

## 六、验收标准

- [ ] `python3 tools/difftest/check-ddl-static.py --domain article` 等 **5 个域**都能跑（favorite 除外 ⇒ 4 个 + favorite 只建库口径）
- [ ] 脚本**幂等**：连跑两次结果一致
- [ ] 每个差异都归类到「真漂移 / 已签字 / 只记不修」，**不许留「未知」**
- [ ] 明确写出**证据强度**：本轮是**双侧活体**（真 PG 两边建库后比），不是源码级
- [ ] 若某域跑不起来（基建缺失）⇒ **显式登记为「未覆盖 + 缺什么」**，不许静默跳过

## 七、⚠️ 本 lane 的已知坑

| 坑 | 说明 |
|---|---|
| **基建不是同一个** | message 域用 `msgddl-pg`（不是 `difftest-postgres`）⇒ 每个域的容器/DSN 都要**逐域确认**，不要假设 |
| **映射表写错 ⇒ 静默不一致** | admin 侧两端都改名 ⇒ 映射写错时脚本**不报错**，只是列集合对不上 ⇒ 必须**逐域核对映射表** |
| **已知差异刷屏** | 脚本默认不开某些比对 ⇒ 「没差异」可能只是没查 ⇒ 扩域时要把该开的都打开并说明 |
| **Docker 抢资源** | 全仓并行跑会互抢容器（实测 `hot-rpc` 拉不到 `apache/kafka:3.7.2`、`user-api` 503 —— 单跑都绿，是环境层面既有现象）⇒ 本 lane 建议**串行跑自己的域**，别与其他 lane 同时起容器 |

---

## 八、执行结果（2026-10-25 · L3 执行者）

> **证据强度 = 双侧活体**（真 PG 16，两侧各建一个真库后逐列比对），**不是源码级**。
> 工具：[`tools/difftest/check-ddl-static.py`](../../../../tools/difftest/check-ddl-static.py) ·
> 环境：[`tools/difftest/ddl-parity.sh`](../../../../tools/difftest/ddl-parity.sh)（本轮新建）。
> ⚠️ 本 lane **一行生产代码都没改**（`service/**` 零改动）—— 这是**对照**任务，不是修复任务。

### 8.1 五个域的逐列对照结果（一行一个域 · 五维）

| 域 | 类型/长度 | 可空性 | DEFAULT | 索引 | 约束 | 触发器 | 退出码 |
|---|---|---|---|---|---|---|---|
| **`article`** | ✅ **0**（56 列逐字相同） | ✅ 0 | ✅ 0 | ✅ 16 ≡ 16 逐条一致 | ✅ 12 ≡ 12 逐条一致 | ✅ 1 ≡ 1 | **0** |
| **`like`** | ✅ **0**（42 列） | ✅ 0 | ✅ 0 | 🔴 Go 17 / RAG 19 ⇒ **RAG 多 2 条** | 🔴 Go 5 / RAG 7 ⇒ **RAG 多 2 条** | 🔴 Go 0 / RAG 1 ⇒ **RAG 多 1 条** | **1**（5 处） |
| **`hot`** | — | — | — | — | — | — | **3**（**N/A**，两侧都没有数据库） |
| **`follow`** | ✅ **0**（10 列） | ✅ 0 | ✅ 0 | ✅ 8 ≡ 8 逐条一致 | ✅ 2 ≡ 2 逐条一致 | ✅ 0 ≡ 0 | **0** |
| **`favorite`** | ✅ **0**（38 列） | ✅ 0 | 🔴 **2** | ✅ 16 ≡ 16（2 处仅命名差） | 🔴 Go 12 / RAG 15 ⇒ **RAG 多 2 条** | ✅ 2 ≡ 2 | **1**（4 处） |

> ⚠️ **`like` 的 5 处差异是「基线选择」敏感项** ⇒ 见 §8.2 的 A/B 两档。
> ⚠️ **`hot` 不是「已对齐」而是「本维度不适用」** ⇒ 脚本给独立退出码 **3**，不许用 `EXIT=0` 冒充。

### 8.2 ⚠️ `like` 域：Go 侧手写 `.sql` 是不是「生产 DDL」？—— 两档基线都要留痕

Go 侧 `service/like/rpc/internal/model/migrations/001~003` 与 **comment 域那三份同一路径形态**，
而 comment 域那三份已被签字认定为「**测试夹具**，不进生产」
（[`docs/Intentional_Deviations/comment/rpc.md`](../../../Intentional_Deviations/comment/rpc.md) §`SD-2026-10-24-CAND-02` 的「根因」表：
`001/002/003` 只被 `service/comment/rpc/internal/model/test-community-postgres.sh:47,49` 执行）。
⇒ **like 域的 `001~003` 默认按「不进生产」处理**，但两档都实测过：

| 基线 | Go 侧建了什么 | 对拍结果 | 证据 |
|---|---|---|---|
| **A（生产权威 · 本 lane 口径）** | 只跑 **AutoMigrate**（`service/like/rpc/internal/model/db.go:15`） | **5 处**：RAG 多 2 CHECK + 1 触发器 + 2 索引 | `/tmp/ddl-like-prod.log` |
| **B（连测试夹具一起放）** | AutoMigrate + `migrations/001~003` | **1 处**：RAG 多 `like_fact_stream_version_range` CHECK | `/tmp/ddl-like-fixture.log` |

复现：`bash tools/difftest/ddl-parity.sh like`（A） / `bash tools/difftest/ddl-parity.sh like --with-go-sql`（B）。

⇒ **两档都归到同一族**（RAG 更严侧，见 §8.3），**结论不随基线变化**。

### 8.3 差异分类表（本轮 5 个域 + 复核的 4 个旧域）

| # | 域 | 差异 | 分类 | 证据（`文件:行号` / 脚本输出行） |
|---|---|---|---|---|
| 1 | `like` | RAG 多 CHECK `like_new_fact_has_dc_wire` | 🟡 **RAG 更严 ⇒ 已签字族候选**（`SD-2026-10-24-CAND-02` 同族） | Go：`service/like/rpc/internal/model/migrations/002_like_dc_wire.sql:25`（**只在夹具里**）· RAG：`service/like/like-rpc/src/main/resources/db/migration/V6__align_like_domain_fact_outbox_with_go.sql:83-87` |
| 2 | `like` | RAG 多 CHECK `like_fact_stream_version_range` | 🟡 **RAG 更严 ⇒ 已签字族候选**（**连夹具基线 B 下 Go 侧也没有**） | Go：`002_like_dc_wire.sql:8-11`（CHECK **内联在 `CREATE TABLE IF NOT EXISTS` 里** ⇒ AutoMigrate 先建过表 ⇒ 整条 CREATE 是空操作 ⇒ Go 侧**永远**没有这条 CHECK）· RAG：`V6__align_like_domain_fact_outbox_with_go.sql:141-145`（**抄成了独立 ALTER** ⇒ 与 comment 域 `comment_fact_stream_version_range` **完全同型**） |
| 3 | `like` | RAG 多索引 `idx_like_fact_delivery` | 🟢 **只记不修（性能非行为）** | Go：`migrations/003_like_fact_delivery.sql:12`（夹具）· RAG：`V6:103-104` |
| 4 | `like` | RAG 多索引 `idx_like_legacy_fact_subject_target`（部分索引） | 🟢 **只记不修（性能非行为）** | Go：`migrations/002_like_dc_wire.sql:15`（夹具）· RAG：`V6:93-95` |
| 5 | `like` | RAG 多触发器 `like_domain_fact_identity_immutable` | 🟡 **RAG 更严 ⇒ 已签字族候选**（**唯一原则上可观察的一类**） | Go：`migrations/003_like_fact_delivery.sql:30`（夹具）· RAG：`V6:130-133` |
| 6 | `favorite` | `favorite_fact_outbox.created_at` DEFAULT：Go 无 / RAG `now()` | 🟢 **只记不修**（与 users 8 处 / message 6 处**同族**：两侧 INSERT 都显式绑定 ⇒ 默认永不触发） | Go：`service/favorite/rpc/internal/model/favorite_fact.go:72`（`gorm:"autoCreateTime;not null"` ⇒ 无 default）· RAG：`service/favorite/favorite-rpc/src/main/resources/db/migration/V3__create_favorite_fact_outbox.sql:32` |
| 7 | `favorite` | `favorite_fact_outbox.updated_at` DEFAULT：Go 无 / RAG `now()` | 🟢 **只记不修**（同上） | Go：`favorite_fact.go:73` · RAG：`V3:34` |
| 8 | `favorite` | RAG 多 CHECK `favorite_fact_outbox_aggregate_version_range` | 🟡 **RAG 更严 ⇒ 已签字族候选** | Go：`service/favorite/rpc/internal/model/001_favorite_fact_outbox.sql:6`（**内联在 `CREATE TABLE IF NOT EXISTS` 里** ⇒ AutoMigrate 先建过 ⇒ 空操作 ⇒ Go 侧没有）· RAG：`V3:42` |
| 9 | `favorite` | RAG 多 CHECK `favorite_item_target_revision_scope` | 🟡 **RAG 更严 ⇒ 已签字族候选**（RAG 侧**明确写了「有意加强」**） | Go：`service/favorite/rpc/internal/model/003_favorite_target_revision.sql:5`（**只加列**，不加 CHECK）· RAG：`service/favorite/favorite-rpc/src/main/resources/db/migration/V6__align_favorite_schema_with_go.sql:54-63`（文件头 ③ 明写「**这里是有意加强**」） |
| 10 | `favorite` | `uk_favorite_fact_version`：Go = **唯一索引** / RAG = **唯一约束** | ✅ **语义等价（载体不同）⇒ 不是漂移** | 脚本输出「唯一约束 ↔ 唯一索引，载体不同」行 · 唯一性判定 / 23505 / ON CONFLICT 仲裁三者完全相同 |
| 11 | `favorite` | `idx_favorite_folder_folder_id` ≡ `uk_favorite_folder_folder_id`；`idx_favorite_item_favorite_id` ≡ `uk_favorite_item_favorite_id` | ✅ **语义等价（仅命名形态差）⇒ 不是漂移** | 脚本输出「语义等价」节 |
| 12 | `like` | `idx_like_record_delete_at` ≡ `idx_like_record_deleted_at` | ✅ **语义等价（仅命名形态差）⇒ 不是漂移** | Go 列名是 `delete_at`（`service/like/rpc/internal/model/data_model.go:20`）⇒ GORM 索引名 `idx_like_record_delete_at`；RAG 迁移脚本写成 `deleted_at`（`V1__create_like_record.sql:49`） |
| 13 | `article` | — | ✅ **五维全等（56 列 / 16 索引 / 12 约束 / 1 触发器逐条一致）** | `/tmp/ddl-all.log` article 段 |
| 14 | `follow` | — | ✅ **五维全等（10 列 / 8 索引 / 2 约束）** | `/tmp/ddl-all.log` follow 段 |
| 15 | `hot` | — | ⚪ **N/A（本维度不适用）** | Go 侧 `service/hot/` 全域 `grep gorm` = **0 命中**（只有 Redis + Kafka）· RAG 侧 `service/hot/hot-rpc/` **无 `db/migration` 目录、无 Mapper/Entity** ⇒ **两侧都没有数据库** |
| 16 | `users`（复核） | `users_user.status` 可空性 | 🔴 **已知真漂移（未修）** `DRIFT-USERRPC-V-002` | 本轮**复现**：`users_user.status go=(nullable=YES) rag=(nullable=NO)` |
| 17 | `users`（复核） | 8 处时间列 DEFAULT `CURRENT_TIMESTAMP` | 🟢 **已知、刻意保留**（待裁决） | 与 [`docs/DRIFT/_cross/ddl-type-divergences.md`](../../_cross/ddl-type-divergences.md) §2.2 **逐条一致** |
| 18 | `users`（复核） | Go 独有 3 条索引 / RAG 独有 4 条索引 | 🟢 **只记不修（性能非行为）** | 与同文件 §2.3.1 / §2.3.2 **逐条一致** |
| 19 | `comment`（复核） | `report_record` 表 **仅 RAG 有** | ✅ **已签字** `SD-2026-10-24-CAND-02`（2026-10-24，用户 Jason） | Go：`service/comment/rpc/internal/model/db.go:15` 的 AutoMigrate 列表**不含** `ReportRecord`，全仓无任何 `.sql` 建它 ⇒ Go 生产侧**根本没有这张表** |
| 20 | `message`（复核） | 6 处 DEFAULT + 索引 `idx_notification_recipient_read` 列不同 | ✅ **已签字** `DRIFT-MSG-V-020` / `SD-2026-10-23-06`（= **CD-10**） | 本轮**复现**，未重复登记 |
| 21 | `task`（复核） | — | ✅ **五维全等（11 列 / 2 索引 / 2 约束，仅主键名不同）** | 退出码 **0**（改口径后，见 §8.4） |

### 8.4 ⚠️ 工具改动（`check-ddl-static.py`）—— 改了「什么算差异」的口径，退出码语义因此变了

| 改动 | 为什么 | 影响 |
|---|---|---|
| **新增 5 个域预设**（`article`/`like`/`hot`/`follow`/`favorite`）+ `users`/`comment` 切到 runner 管理的容器 | 原 `users` 预设的容器 `uudifftest-postgres` **Go 侧没有 admin 两张表**（admin 的表由 `service/user/admin/rpc/internal/model/db.go:15` 另一个包产出）⇒ 每次跑都撞「列集合不一致」 | users 域从「环境缺口」变成**可完整对照**（35 列全量） |
| **加 `expected_tables` 环境闸门** | 「两侧都是 0 张表」原来会**静默判成一致** | 少一张表 ⇒ `EXIT=2` + 点名缺哪张（实测：like 域容器重建中途被扫到 ⇒ 报「Go 侧缺 like_consume_inbox / like_domain_fact_outbox / like_fact_stream」⇒ **闸门有效**） |
| **退出码 3 = N/A** | `hot` 域两侧都没有数据库 ⇒ `EXIT=0` 会冒充「已对齐」 | hot 域现在**明确**是 N/A |
| **「命名形态差」不再算失败**（索引 + 约束） | 名字不同但「表 + 唯一性 + 列」完全等价 ⇒ PG 语义零影响（[`ddl-type-divergences.md`](../../_cross/ddl-type-divergences.md) §2.4 已这么判定，**但脚本原来把它算成失败**） | `task` 域从 `EXIT=1`（误报 4 条主键名差）变成 **`EXIT=0`**；`message`/`users` 退出码不变（仍 1） |
| **「唯一约束 ↔ 唯一索引」载体不同不再算失败** | 唯一性判定 / 23505 / ON CONFLICT 仲裁完全相同 | favorite 域的 `uk_favorite_fact_version` 从「实质差异」降为「语义等价」 |
| **DEFAULT 的「序列名映射」不再算真差** | 两侧表名不同 ⇒ 序列名必然不同（`users_id_seq` ↔ `users_user_id_seq`）⇒ 是**表名映射的副产品**（同文件 §2.2「已知的 4 处假差」） | users 域 DEFAULT 差异从 12 处收敛到 **8 处**（与既有台账一致） |
| **新增触发器维度**（`pg_trigger`） | article / like / favorite 的「不可变信封」语义**只由触发器承载** ⇒ 不查它等于把最硬的约束漏在门外 | like 域的 RAG 多 1 个触发器**因此才可见** |

⇒ **复现命令**（退出码语义：**0** 一致 / **1** 有实质差异 / **2** 环境不满足 / **3** 本域 N/A）：

```bash
# 一键重建环境 + 对拍（单域）
bash tools/difftest/ddl-parity.sh <article|like|hot|follow|favorite|users|comment>
# 全域（串行）
bash tools/difftest/ddl-parity.sh all
# 连 Go 侧「测试夹具」一起放（like 域基线 B）
bash tools/difftest/ddl-parity.sh like --with-go-sql
# 只跑对拍（环境已在）
python3 tools/difftest/check-ddl-static.py --domain <域> --indexes
```

### 8.5 幂等性与可重跑性（实测）

- **脚本幂等**：9 个域各**连跑两次**，输出**逐字一致**、退出码一致 ⇒ `/tmp/idem-<域>-1.txt` vs `-2.txt`（`diff -q` 全绿）。
- **环境可重建**：runner 每次**从零重建容器 + 两个库**（`docker rm -f` → `docker run` → 建库 → 跑探针 → 跑迁移）⇒
  同样的命令重跑得到**同样的结论**（article/like/favorite/users 各重跑过一轮，结论与首跑一致）。
- **只读性**：对拍脚本**只读**两个库，不写任何 DDL ⇒ 不污染环境，可随时重跑。

### 8.6 ⚠️ 本轮**没有比 / 没有覆盖**的部分（不许用「没差异」掩盖「没查」）

| 项 | 状态 | 原因 |
|---|---|---|
| **外键的引用目标** | ❌ **未比** | 脚本只取 `pg_get_constraintdef` 的文本，不解析被引用表；本仓 9 个域两侧都**没有** FK ⇒ 当前无影响 |
| **分区 / 存储过程 / 物化视图** | ❌ **未比** | 两侧都没有这些对象 |
| **列的 COLLATION / 排序规则** | ❌ **未比** | `information_schema.columns` 取的是 `data_type` + `character_maximum_length`；本仓全是默认 collation |
| **`numeric(p,s)` 的精度标度** | ⚠️ **部分未比** | 脚本只比 `character_maximum_length`（字符型）；本轮 9 个域**没有** numeric 列 ⇒ 无影响 |
| **触发器函数体**（`reject_*_mutation()` 的 SQL 文本） | ❌ **未比**（只比「表 + 名字 + 定义」） | 函数体是 SQL 文本，逐字比对会把「写法不同但语义相同」也报成差异 ⇒ 反而制造噪音；**需要人工逐条核对**（本轮**未做** ⇒ 登记为**未覆盖**） |
| **`task` / `message` 两域的 runner** | ⚠️ **未建** | 这两域的环境是 B3 轮遗留的 `taskprobe-postgres` / `msg_ddl_pg`（**仍可用、退出码 0 / 1**）⇒ **本轮未重建**，可重跑但**不是本 runner 产物** |
| **`favorite` 域的代码 / 台账** | ⛔ **未碰** | favorite 是**只建库、不碰其代码/台账**的域（域零写权）⇒ §8.3 #6~#11 **只登记** |

### 8.7 遗留待办（**本 lane 不修**，交整合者路由）

| 待办 | 落点建议 | 理由 |
|---|---|---|
| `like` / `favorite` 的 **4 条「RAG 更严」对象**（#1 #2 #5 #8 #9） | 走 **`docs/Intentional_Deviations/`** 签字流程（**与 `SD-2026-10-24-CAND-02` 同族**，可合并成一条「DDL 更严侧」条目） | 全部是「RAG 多了 Go 没有的约束/触发器」⇒ 只会**更早拒绝**，当前写入路径不碰受保护列 ⇒ 不可观察；**改回 Go = 降完整性 + 动已落库的 Flyway**（只能新写 V7/V9 DROP）⇒ **不建议改** |
| `favorite` 的 **2 处 DEFAULT**（#6 #7） | 归入 **users 8 处 / message 6 处** 的「DEFAULT 残留待裁决」同一条 | 三族同型：Go 无 default、RAG `CURRENT_TIMESTAMP`；两侧 INSERT 都显式绑定 ⇒ 默认永不触发 |
| `users` 域 `status` 可空性 + 3 条缺失索引 | **不动**（`DRIFT-USERRPC-V-002` 等已在 users 域台账） | 本 lane 只登记、只复现 |
| `hot` 域 DDL **N/A** | **不需要任何动作** | 该域根本没有数据库；**不要**在台账里写「hot DDL 已对齐」 |
| 触发器**函数体**的人工逐条核对 | 下一轮（或 favorite/like 域自己的 lane） | 见 §8.6 |

### 8.8 本轮**没有**产出「真漂移（两侧行为不一致且未签字）」

⇒ 5 个新域的**全部**差异都归入三类：**语义等价（不是差异）** / **只记不修（性能非行为）** / **RAG 更严侧（已签字族候选）**。
⇒ **没有一条需要改 RAG 的生产代码**；本 lane `service/**` **零改动**。

### 8.9 全域复跑留痕（2026-10-25 · 本轮结束时的最终状态）

```bash
# 9 个域逐个跑（--indexes 打开全部维度），输出即下表
for d in article like hot follow favorite users comment task message; do
  out=$(python3 tools/difftest/check-ddl-static.py --domain $d --indexes 2>&1); rc=$?
  echo "$d EXIT=$rc | $(echo "$out" | grep -E '实质差异合计|不适用' | head -1)"
done
```

```
article  EXIT=0 | == 实质差异合计 0 处 ⇒ 退出码 0 ==
like     EXIT=1 | == 实质差异合计 5 处 ⇒ 退出码 1 ==      # 全部「RAG 更严」⇒ 已签字族候选
hot      EXIT=3 | ⚠️ 本域两侧都没有业务表 ⇒ **DDL 维度不适用（N/A）**，不是「已对齐」。
follow   EXIT=0 | == 实质差异合计 0 处 ⇒ 退出码 0 ==
favorite EXIT=1 | == 实质差异合计 4 处 ⇒ 退出码 1 ==      # 2 DEFAULT + 2 RAG 更严 CHECK
users    EXIT=1 | == 实质差异合计 16 处 ⇒ 退出码 1 ==     # 1 可空性 + 8 DEFAULT + 7 索引形态
comment  EXIT=2 | （report_record 仅 RAG 有 ⇒ 已签字 SD-2026-10-24-CAND-02）
task     EXIT=0 | == 实质差异合计 0 处 ⇒ 退出码 0 ==
message  EXIT=1 | == 实质差异合计 7 处 ⇒ 退出码 1 ==      # 6 DEFAULT + 1 索引（CD-10 已签字）
```

⚠️ **`EXIT=1` ≠ 「有 bug」** —— users / message 的 1 是**已登记的已知差异**（与
[`docs/DRIFT/_cross/ddl-type-divergences.md`](../../_cross/ddl-type-divergences.md) 逐条一致），
本轮只是**复现**，不重复登记。
