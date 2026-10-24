<!-- @new 2026-10-23 23:1x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `message-rpc` 签字偏离（域内索引）

> **正本在本文件**；总索引见 [`../_INDEX.md`](../_INDEX.md)（⚠️ 该文件是**共享面**，
> 按 `docs/Worktree.md` §三，所有工作树都不许碰 ⇒ 合并后由主会话统一登记本条的编号）。

## 本域签字偏离：7 条（其中 `SD-07` 已于 2026-10-24 **撤销**）

> 🆕 **2026-10-24 第十轮新增 3 条**（`SD-2026-10-24-01/02/03`）——
> 分别是 `DRIFT-MSG-V-021`（索引名）/ `DRIFT-MSG-V-009`（直方图桶）/ `DRIFT-MSG-V-010`（错误解包深度）。
> 三条都是用户 **2026-10-24 问卷拍板**：**签字保留 RAG 现状，不改代码**。
> ⚠️ 三条都在本轮**拿到了活体证据**（不再是静态推断）：
> V-021 靠双侧活体 DDL 对拍，V-009 靠双侧直方图桶实测，V-010 靠两侧消费点逐行核对。
>
> 🆕 **2026-10-24 第十轮再新增 1 条**（`SD-2026-10-24-04`）——
> `DRIFT-MSG-V-031`（6 个时间列的 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧多出来的）
> ⇒ **用户拍板：保留签字**（不加 `V6` 迁移去 `DROP DEFAULT`）。

> ⚠️ **2026-10-24 第八轮：`DRIFT-MSG-V-003` 的签字范围已收窄**（不是撤销）——
> 原签字：「DB/内部故障业务码 Go `1015` → RAG `5001`/`5008`，保留 RAG 侧」。
> 本轮修了 rpc 侧兜底码（`INTERNAL` → `UNKNOWN`，见 `DRIFT-MSG-V-024`）⇒
> **业务码这一半已经与 Go 一致（都是 1015）**；
> **保留的只有「不回显原始错误文本」这一半** ⇒ 即 Go 的 envelope 会把 gRPC description 原文
> （SQL 错误文本）塞进 `msg`，RAG 只回码表文案（`service busy`）⇒ **信息泄露防护**。
> ⇒ 详见本文件 §V-003 收窄说明。

---

### SD-2026-10-24-01 🆕 · `message_conversation` 两个索引的**名字**与 Go 不同（列完全相同）

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（DDL 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-021`） |
| **Go 行为** | `service/message/rpc/internal/model/data_model.go:60`（`LastMessageAt … ;not null;index`）与 `:61`（`UpdatedAt … ;index`）用的是**无名** `index` tag ⇒ GORM 按 `idx_<表名>_<列名>` 命名 ⇒ 建出 `idx_message_conversation_last_message_at` / `idx_message_conversation_updated_at` |
| **RAG 行为** | `service/message/message-rpc/src/main/resources/db/migration/V2__create_message_conversation.sql:43,48` 手写为 `idx_conversation_last_message_at` / `idx_conversation_updated_at` |
| **类别** | `DDL / STRUCTURE`（**索引名**不同，**列完全相同** ⇒ 查询能力、唯一性保证、性能特征**完全等价**） |
| **签字结论** | **2026-10-24 用户拍板：签字保留 RAG 短名，不改名** |
| **⚠️ 活体证据（2026-10-24 第十轮，双侧真 PG）** | 两侧各建一个真库（Go 侧跑**真源自己的** `AutoMigrate`，RAG 侧按序执行 V1~V5），逐条 dump `pg_indexes`：<br>Go `idx_message_conversation_last_message_at` ⇔ RAG `idx_conversation_last_message_at`，两边 `cols=['last_message_at']`、`unique=False` **完全一致**<br>Go `idx_message_conversation_updated_at` ⇔ RAG `idx_conversation_updated_at`，两边 `cols=['updated_at']`、`unique=False` **完全一致**<br>⇒ 全域 12 个索引里**只有这 2 个名字不同**，其余 10 个（含 `uk_conversation_pair`）**逐条一致** |
| **⚠️ 为什么保留是对的（不是「将就」）** | Go 的名字是 GORM 的**命名算法副产品**，不是设计意图 —— 业务代码里没有任何地方引用索引名（两侧的 mapper 都是纯列名 SQL）。RAG 的短名**更短更可读**，且 `V2` 的注释已把「为什么这么建」写清楚了。<br>⇒ 改名的收益 = 让 `\d` 输出好看；改名的代价 = 一次 `DROP INDEX` + `CREATE INDEX` 的迁移窗口 + 线上若有运维脚本按索引名操作就得同步改 |
| **⚠️ 代价（必须知情）** | ① 两侧 `pg_indexes` / `\d` 的**索引名**不同；② 任何**按索引名做逐字节比对**的 DDL 对拍工具在**这一条上必然为红**（`tools/difftest/check-ddl-static.py --domain message --indexes` 会把它判成「名字不同但列等价」）⇒ 属**预期差异，不是回归**<br>⇒ 已把这两个名字加进门禁的豁免名单（`DDL_ALLOW_IDX`），使门禁对**新**的索引漂移仍然为红 |
| **⚠️ 不得做的事** | 不要为了让 DDL 对拍变绿而改名 —— 除非**同时**确认线上没有任何对象（运维脚本 / 监控 / 报表）引用旧名。若将来确需改名，**必须走一条新的 Flyway 迁移**（`DROP INDEX IF EXISTS` + `CREATE INDEX`），**不得**改 `V2`（已部署过的库不会重跑 `V2`） |

---

### SD-2026-10-24-02 🆕 · `message_rpc_request_duration_seconds` 的直方图**桶边界零交集**

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（DRIFT 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-009`） |
| **Go 行为** | `service/message/rpc/internal/metrics/metrics.go:23` `Buckets: prometheus.DefBuckets` ⇒ **11 个桶 + `+Inf`**：`0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10`（秒）<br>（常量来源：`github.com/prometheus/client_golang@v1.23.2/prometheus/histogram.go:271` —— go.mod 钉的就是 v1.23.2） |
| **RAG 行为** | `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/metrics/MessageMetrics.java:86-92` `Timer.builder(…).publishPercentileHistogram()` ⇒ Micrometer 1.16.7 的**指数默认桶**：**69 个桶 + `+Inf`**，从 `0.001` 起、步长 `1.048576`（≈2^(1/20)）、到 `30.0` 为止 |
| **类别** | `可观测指标 / 运维口径`（**不影响 wire 契约、不影响业务行为**） |
| **签字结论** | **2026-10-24 用户拍板：签字留档，不改代码**（两个方向都不做 —— 不改 RAG 去迁就 Go，也不改 Prometheus 抓取端） |
| **⚠️ 活体证据（2026-10-24 第十轮，双侧实测）** | Go 侧：跑一个复刻 `metrics.go:18-24` 的 `HistogramOpts` 的探针，打印桶边界；<br>Java 侧：跑一个复刻 `MessageMetrics.java:86-92` 的 `Timer`（`PrometheusMeterRegistry` + `publishPercentileHistogram()`），`registry.scrape()` 后解析 `le="…"`；<br>⇒ **两侧桶边界精确交集 = 0 个**。Go 的 11 个桶**全部**不在 Java 的 69 个桶里 |
| **⚠️ 实际影响（必须知情）** | ① `histogram_quantile(0.95, rate(..._bucket[5m]))` **两边都能算**，但因为桶边不同，**同一份流量在两侧会算出不同的 p95 数字**（不是「谁错了」，是分桶不同）；<br>② **低于 5ms 的请求**：Go 落在第一个桶（`0.005`）之前 ⇒ 计入 `+Inf` 之前的隐含桶；RAG 落在 `0.001` 桶 ⇒ 可区分；<br>③ **超过 10s 的请求**：Go 全部落进 `10 → +Inf`（**分辨率丢失**）；RAG 仍能分到 `10 / 12.58 / 15.78 / 19.8 / 24.8 / 30` 等桶 ⇒ **RAG 在长尾上分辨率更高** |
| **⚠️ 为什么保留是对的** | ① **两侧的桶都不是「对的那个」** —— Go 的 11 个是 Prometheus 客户端的历史默认档（为「秒级 Web 服务」设计，对 gRPC 毫秒级服务偏粗）；Micrometer 的 69 个指数桶是为「自适应分辨率」设计的通用档；<br>② **真正对齐的做法不是抄桶，而是对齐口径** —— 两侧的**指标名 / label 集 / 观测点**（`method` 标签、`success`/`fail` 计数）本就一致，桶只是分箱方式；<br>③ 改 RAG 的桶 = 要么手写 11 个桶（放弃长尾分辨率），要么改抓取端（运维侧动作，超出本域）⇒ **两边都不值得** |
| **⚠️ 不得做的事** | 不要把「桶不同」当成**回归**反复「修」—— 它已被签字。⚠️ 也不要**只改一边**：只改 RAG 不改抓取端，会让运维侧已有的 `histogram_quantile` 告警阈值失去意义（分母的桶集合变了） |

---

### SD-2026-10-24-03 🆕 · 嵌在 cause 链里的 `CodeError`：Go `errors.As` **穿透** / RAG `instanceof` **不穿透**

| 项 | 内容 |
|---|---|
| **组件** | `message-api` + `message-common`（DRIFT 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-010`） |
| **Go 行为** | api 的**全局错误处理器**：`service/message/api/messagecenter.go:57` `if errors.As(err, &codeErr)` ⇒ `errors.As` **沿 `Unwrap()` 链逐层找** ⇒ **穿透** |
| **RAG 行为** | `service/message/message-common/src/main/java/com/rag/message/message/common/errmsg/MessageGrpcError.java:230` `if (err instanceof CodeError)` ⇒ **只看最外层那一个对象**，**不穿透** |
| **类别** | `错误传播`（**latent**：当前没有会发作的调用路径） |
| **签字结论** | **2026-10-24 用户拍板：签字留档，不改代码** |
| **⚠️ 活体证据（2026-10-24 第十轮，逐行核对两侧消费点）** | 两侧**不是**「一个穿透一个不穿透」，而是**分两支**（台账原文「Go 穿透 / Java 不穿透」**过宽**，本条已订正）：<br>① **`CodeError` 支** —— Go `service/message/common/errmsg.go:104` `err.(*CodeError)`（**不穿透**）↔ RAG `MessageGrpcError.java:230` `instanceof CodeError`（**不穿透**）⇒ **两侧一致 ✓**<br>② **`status` 支** —— Go `service/message/common/errmsg.go:108` `status.FromError(err)`（grpc-go 内部走 `errors.As` ⇒ **穿透**）↔ RAG `MessageGrpcError.java:300` `extractStatus(Throwable)`（**显式走 cause 链** ⇒ **穿透**）⇒ **两侧一致 ✓**（这条是 `DRIFT-MSG-COMMON-001` 修好的）<br>⇒ **真正不一致的只剩一处**：Go **api 的全局错误处理器**（`messagecenter.go:57`）用 `errors.As` 取 `*CodeError` ⇒ 它**能**认嵌套；RAG 的 api 侧 envelope（`MessageApiSupport.java:140`）走 `bizCodeFromError` ⇒ 它**只认最外层** |
| **⚠️ 实际影响（为什么是 latent）** | RAG 侧 `CodeError` 由 api 自己的 logic **直接抛出** ⇒ 异常处理器拿到的就是它本身 ⇒ `instanceof` 命中。<br>⇒ **只有当 `CodeError` 被包进别的异常再抛**（Spring AOP / `@Async` / 异步 stub 的 `CompletionException` 包装）时，两侧才会给出不同的码。<br>⚠️ **Go 侧同样会被 `CompletionException` 之类的包装影响吗？** 不会 —— `errors.As` 会穿透。⇒ **这正是差异所在**，只是当前 Go 的 api 路径上没有这种包装 |
| **⚠️ 为什么保留是对的** | ① **改 RAG 去穿透 = 会引入新的分歧点**：`bizCodeFromError` 的 `CodeError` 分支**刻意**不穿透（`MessageGrpcError.java:227-229` 的注释写明：「照 Go 排，别再对调」）—— 它对齐的是 Go `errmsg.go:104` 那条**不穿透**的实现；改成穿透反而**不再对齐** Go 的 `BizCodeFromError`；<br>② 真正的差异只在 **api 全局错误处理器**那一层，而 RAG 侧那层是 `service/common` 的**共享异常处理** ⇒ 改它**会影响 20 个组件**；<br>③ 当前**没有发作路径** ⇒ 属于「为不可观测的隐患付出跨域改动代价」 |
| **⚠️ 不得做的事** | 不要**笼统**地「把 RAG 改成穿透」—— `bizCodeFromError` 里那两支**各自都已经和 Go 对齐**，动它们会**制造新漂移**。<br>⚠️ 若将来 api 侧真的引入了会包装 `CodeError` 的异步路径，**本条必须重新评估**（且要评估的是「api 全局错误处理器要不要像 Go 那样用 `errors.As`」，**不是**改 `bizCodeFromError`） |

---

---

### SD-2026-10-24-04 🆕 · 6 个时间列的 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧**多出来的**（Go 侧无默认）

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（DDL 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-031`） |
| **Go 行为** | **6 个时间列都没有 DEFAULT** —— GORM 只在 tag 里写 `autoCreateTime` / `autoUpdateTime`（`service/message/rpc/internal/model/data_model.go` 的 `Notification` / `Conversation` / `ConversationMessage`），**迁移器不生成 DEFAULT** |
| **RAG 行为** | 同 6 列带 `DEFAULT CURRENT_TIMESTAMP`（`V1__create_message_notification.sql` / `V2__create_message_conversation.sql` / `V3__create_message_conversation_message.sql` 建表时写的；`V5` 只改了类型与可空性、**没删默认**） |
| **具体 6 列** | `message_notification.{created_at, updated_at}` · `message_conversation.{created_at, updated_at, last_message_at}` · `message_conversation_message.created_at` |
| **类别** | `DDL / STRUCTURE`（**当前不可观测** —— 见「为什么保留是对的」） |
| **签字结论** | **2026-10-24 用户拍板：保留签字**（保留 RAG 侧的 DEFAULT，不加 `V6` 迁移去 `DROP DEFAULT`） |
| **⚠️ 活体证据（2026-10-24 第十轮，双侧真 PG）** | Go 侧跑**真源自己的** `AutoMigrate`、RAG 侧按序执行 `V1`~`V5`，两侧各建一个真库后逐列 dump `information_schema.columns`：<br>**列类型 / 长度 0 差异 · 可空性 0 差异 · DEFAULT 恰好 6 处差异**（Go 一律 `<NULL>`，RAG 一律 `CURRENT_TIMESTAMP`）<br>⇒ 与 `V-020` 迁移文件里早就写下的「`DEFAULT` 残差是 **6** 列，不是 7」**数字完全对上** —— 这是该残差**首次拿到活体证据**。<br>⇒ 复现：`bash tools/difftest/run-message-ddl-parity.sh` |
| **⚠️ 为什么保留是对的（不是「将就」）** | ① **当前不可观测**：RAG 的 3 条 INSERT **全部显式绑定**这些列（`ConversationMapper.java:143-155` / `NotificationMapper.java:205-219` / `ConversationMessageMapper.java:159-166`）⇒ 默认**永远不触发** ⇒ 两侧行为等价。<br>② **V-020 当时已论证过保留的理由**（`V5__message_time_columns_timestamptz.sql` 的 ⚠️ 段：「删除默认对『省略该列的 INSERT』才有意义……为一个不可观测的维度冒『有人在某条裸 SQL 路径上依赖了默认』的风险不划算」）—— 本轮只是把那个推理**用活体证据确认了一遍**。<br>③ **删它的代价不对称**：加一条 `V6` 迁移 `DROP DEFAULT`，会**静默改变**任何「省略该列的 INSERT」的行为（Go 写 NULL / RAG 写 now()），而这类路径**当前不存在** ⇒ 为不存在的场景引入一次迁移窗口 + 一次线上行为变更，不划算。 |
| **⚠️ 代价（必须知情）** | **这不是「永远等价」，是「当前无路径」。** 将来若有人写一条**省略该列**的 INSERT：<br>· Go 侧：该列可空 ⇒ 写 **NULL**（`created_at`/`updated_at`/`read_at`/`extra`）或 **NOT NULL 违规**（`last_message_at`）<br>· RAG 侧：写 **now()**（`last_message_at` 则是 now()）<br>⇒ **两侧会分岔**，且**分岔方向是「RAG 悄悄填了个时间」** ⇒ 排查成本高。<br>⇒ **必须知情的是这条，不是「现在没事」** |
| **⚠️ 不得做的事** | ① **不要**在别的域/别的会话里「顺手把默认删了」或「顺手加个 `V6`」—— 那是**另一条决策**，必须重新签字；<br>② **不要**因为「当前不可观测」就把它从台账上抹掉 —— 它是**已登记的偏离**，抹掉 = 下一个人会重新踩；<br>③ **不要**以为「Go 侧没有 DEFAULT ⇒ RAG 也不该有」就去对照 —— 本条**正是**有意保留 RAG 侧。 |
| **关联** | `DRIFT-MSG-V-020`（时间列 `TIMESTAMP` ⇒ `TIMESTAMPTZ`，已修）· `V-023`（`uk_conversation_pair` 约束 ⇒ 索引，已修）· 本条的活体证据来自同一轮双侧 DDL 对拍（见 `DRIFT/message/rpc.md` 第十轮小节） |

---

### SD-2026-10-23-08 · CPU 采样读不到时 **fail-open**（不降载）

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（**实现落在共享模块** `service/common/.../protection/GoStyleAdaptiveShedder.java`） |
| **Go 行为** | `stat.CpuUsage()`（`core/stat/usage.go:51`）= `atomic.LoadInt64(&cpuUsage)`，由后台采集器读 `/proc/stat`（Linux）更新，取值 **0..1000 千分比**，**永远不会返回 -1**。<br>⇒ `systemOverloadChecker(threshold)` 永远是**真实判定**：`CpuUsage() >= threshold` |
| **RAG 行为** | `OperatingSystemMXBean.getSystemCpuLoad()`（`SYSTEM` 口径，对齐 Go 的整机口径）；**某些容器里 JVM 读不到 CPU ⇒ 返回 -1** ⇒ `readCpuPermille()` 返回 -1 ⇒ `systemOverloaded()` **fail-open**（视作「未过载」⇒ **不降载**） |
| **类别** | `框架限制`（想对齐但 JVM 的指标接口做不到 —— Go 的 `/proc/stat` 没有"读不到"这一态） |
| **签字结论** | **2026-10-24 用户明示：「接受 fail-open」** |
| **⚠️ 为什么 fail-open 是对的** | 读不到指标时若 **fail-closed**（当作已过载）⇒ **误杀全部真实流量**，而且这类误判在生产上极难归因。<br>Go 侧的等价语义正是「`systemOverloadChecker` 返回 false」⇒ **不降载**（`adaptiveshedder.go` 首个分支 `!systemOverloadChecker → false`）。⇒ **fail-open 与 Go 的失败分支同构**，不是拍脑袋的取舍 |
| **⚠️ 代价（必须知情）** | 在 **JVM 读不到整机 CPU** 的容器里：Go 仍会按真实 CPU 降载，**RAG 完全不会降载** ⇒ **RAG 在那种环境里失去了降载保护**，过载时会一路把请求打进 DB。<br>⇒ 缓解手段：① `cpuSource: PROCESS`（`getProcessCpuLoad()`）在部分环境可读；② 部署侧确认 JVM 能读到整机 CPU；③ 观察日志里是否长期无 CPU 采样值 |
| **⚠️ 不得做的事** | 不要为了让两侧"看起来一致"把 -1 当成 **0**（＝直接低于阈值，等价 fail-open，无害但会掩盖问题）或当成**过载**（＝误杀流量）。<br>⚠️ 也不要**悄悄**把 `cpuSource` 从 `SYSTEM` 改成 `PROCESS` —— 那是**另一条**偏离（口径从整机变成本进程），必须单独签字 |
| **关联** | `SD-2026-10-23-07`（breaker / CPU shedding 的**挂起**与重新评估前置条件）—— ⚠️ **该条已于 2026-10-24 撤销**（breaker / shedding 已实施）⇒ 本条现在**已是生效中的签字**，不再是「待实施的前置」 |

---

### SD-2026-10-23-07 🗄️ **已撤销（2026-10-24）** · 服务端熔断 / CPU 自适应降载：原「本轮不实施（挂起式签字）」

> **撤销时间**：2026-10-24 —— breaker / shedding **已实施**
> （见 [`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-016`）。
> ⇒ 本条**不再是「RAG 与 Go 的差异」**，保留它是为了记录**决策过程**：
> 「为什么当初挂起、后来为什么又做了、当时哪条判断是错的」。
>
> **⚠️ 保留的三条结论（仍然有效）**：
> ① 「breaker/shedding 无等价物、只能引 resilience4j、算法不可能对齐」—— **该判断是错的**：
>    仓里早就有 go-zero `googlebreaker.go` / `adaptiveshedder.go` 的**逐行复刻**
>    （`service/common/.../protection/GoStyleBreaker.java` 278 行 / `GoStyleAdaptiveShedder.java` 373 行，
>    各带 8 / 10 例测试）；
> ② 真正该做的不是"引框架"，而是**各包一层 `ServerInterceptor` 薄壳** ——
>    这正是 2026-10-24 落地的东西（`GoStyleRpcBreakerInterceptor` / `GoStyleRpcSheddingInterceptor`）；
> ③ 降载阈值的**前置条件**（CPU 采样源 fail-open）已由
>    [`SD-2026-10-23-08`](#sd-2026-10-23-08--cpu-采样读不到时-fail-open不降载) 解决。

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（DRIFT 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) §7 的 `DRIFT-MSG-V-016`） |
| **Go 行为** | 真源默认全开（`etc/message.yaml` 一个都没配 ⇒ 取结构体默认值）：<br>· **熔断**：`zrpc/internal/config.go:25` `Breaker default=true` ⇒ 开路时 `UNAVAILABLE` + description `"circuit breaker is open"`（`breakerinterceptor.go:45-48`、`core/breaker/breaker.go:19`）<br>· **CPU 降载**：`zrpc/config.go:47` `CpuThreshold default=900` ⇒ 拒绝时 `RESOURCE_EXHAUSTED` + description `"service overloaded"`（`sheddinginterceptor.go:30-35`、`core/load/adaptiveshedder.go:34`） |
| **RAG 行为（2026-10-24 起）** | **两个都已对齐**：wire 契约、隔离 key（gRPC 全方法名）、失败判据（Go 的 6 码白名单）逐条一致 |
| **⚠️ 撤销前那条「连锁风险」的结论要更正** | 原文写：「服务端一旦开始吐 `UNAVAILABLE`，api 侧客户端熔断可能被打开 ⇒ **两条必须一起设计**，单独补服务端会引入一条**没人设计过的新失效模式**」。<br>⇒ **2026-10-24 实测更正**：Go 的 api→rpc 走 **zrpc client**，它**自带** client breaker（`zrpc/internal/client.go:112-113`，默认开），失败判据就是**同一套 6 码白名单** ⇒ **Go 本来就会把服务端的 `UNAVAILABLE` / `RESOURCE_EXHAUSTED` 记成失败**。<br>⇒ **这个「放大效应」Go 就有** ⇒ 补服务端熔断是**与 Go 对齐（含放大效应）**，不是新造缺陷。<br>⇒ 真正成立的约束只有一条：**breaker 与 shedding 必须一起上**（Go 链序 `Breaker → Shedding` ⇒ shedding 的拒绝会被 breaker 记成失败）。本轮两者同交付 ✓ |
| **⚠️ 实施后带出的两条尾巴（**不属于**本条）** | ① [`DRIFT-MSG-V-024`](../../DRIFT/message/rpc.md)：DB / 未知异常两侧 status 不同（Go `UNKNOWN` / RAG `INTERNAL`）⇒ **熔断判定分岔**，RAG 会比 Go 灵敏得多<br>② [`DRIFT-MSG-V-026`](../../DRIFT/message/rpc.md)：**其余 9 个 rpc 组件尚未接入**本链 ⇒ 跨组件不变量未闭合 |

---

### SD-2026-10-23-06 · `idx_notification_recipient_read` 的列数：Go 1 列 / RAG 2 列

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（DDL 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) §7 的 `DRIFT-MSG-V-022`） |
| **Go 行为** | `CREATE UNIQUE … ON (is_read)` —— 普通索引，**只有 1 列** |
| **RAG 行为** | `ON (recipient_id, is_read)` —— **2 列** |
| **类别** | `DDL / STRUCTURE`（非 wire 契约；数据语义与唯一性保证**完全相同**） |
| **签字结论** | **2026-10-23 用户拍板：保留 RAG 现状，不回改成 Go 的单列索引** |
| **根因** | Go 的 struct tag 里 `recipient_id` 挂的是**另一个**索引（`index:idx_notification_recipient_time,priority:1`），而 `is_read` 用 `index:idx_notification_recipient_read,priority:2` —— 该索引**没有 priority:1 成员**，GORM 就只建一列。RAG 侧按索引名的字面意图（"recipient + read"）建了两列 |
| **⚠️ 为什么保留是对的（不是「将就」）** | `ListNotifications` 的过滤条件是 `WHERE recipient_id = ? AND is_read = false`。RAG 的两列索引**正好**是这个前缀，能同时过滤两列；Go 的单列索引只能定位 `is_read=false` 的全部行再回表过滤 `recipient_id` —— **在这个服务的真实查询形态下，RAG 的版本是更优的**，回改会造成计划回退 |
| **⚠️ 代价（必须知情）** | 两侧 `pg_indexes` / `\d` 的输出不同；任何**按索引定义做逐字节比对**的 DDL 对拍工具（含 `tools/difftest/check-ddl-static.py`）在这一条上**必然为红**，属预期差异，不是回归 |
| **⚠️ 不得做的事** | 不要为了让 DDL 对拍变绿而把它改回单列。若日后 `ListNotifications` 的查询形态变化（`recipient_id` 不再总是等值过滤），本条应**重新评估**，而不是当作既成决策继承下去 |

### `DRIFT-MSG-V-003` 签字范围收窄（2026-10-24 第八轮 · 不是撤销）

| 判据 | 原签字时的状态（2026-10-22） | **本轮之后（2026-10-24）** | 处置 |
|---|---|---|---|
| **业务码**（DB / 内部故障） | Go `1015` vs RAG `5001`/`5008` ⇒ 签字保留 RAG | ✅ **两侧都是 `1015`**（rpc 兜底码 `INTERNAL` → `UNKNOWN`）⇒ **已对齐，不再是差异** | **这一半作废** |
| **envelope 的 `msg`** | Go = gRPC description 原文（SQL 错误文本）⇒ 信息泄露；RAG = 码表文案 ⇒ 更安全 | ✅ **RAG 仍只回码表文案**（`service busy`）⇒ 防护保留 | **这一半继续签字** |
| **可运维性** | Go 把 DB 故障说成「服务繁忙」，值班会去看容量/限流 | ⚠️ **两侧现在都归到 `1015`** ⇒ 这一判据**已失效**（不再有分歧可谈） | 随业务码一并作废 |

⇒ **签字保留的最终范围**：**只有「envelope 不回显原始错误文本」这一条**。
⚠️ **不得做的事**：不要把这个签字读成「DB 故障就该是 5001」——
  业务码已与 Go 一致（1015），再改回去是**制造新的漂移**。
⚠️ **也不要**把「Go 会泄露原始错误文本」当成「RAG 也该泄露」——
  那是**签字明确不采纳**的一面。

**依据**：Go 的 `shared.RPCError`（`service/message/api/internal/logic/shared/helper.go`）
把 `st.Message()` **原文**塞进 envelope；Go 的 `BizCodeFromError` 对**非 status 错误**
（`status.FromError` 返回 `ok=false`）**直接**返回 `CodeServerBusy(1015)`、**不走 description 反查**
⇒ Go 的 `1015` 来自「code class 兜底」这一支，与 RAG 改后的路径**同构**。

---

## 本域**没有**的签字偏离（避免后人误补）

| 曾被怀疑的点 | 实测结论 |
|---|---|
| **服务端 panic 恢复 / 熔断 / CPU 降载**（`DRIFT-MSG-V-016` 三个拦截器） | ❌ **不存在偏离 —— 已全部对齐**。<br>· recover（2026-10-23 23:5x）：`INTERNAL` + `"panic: "` 前缀 + 进程存活；5 例真 in-process gRPC + 3 轮变异验证<br>· breaker（2026-10-24）：`UNAVAILABLE` + `"circuit breaker is open"`，隔离 key = gRPC 全方法名，失败判据 = Go 的 6 码白名单；含 message-rpc **真 gRPC 端到端** 2 例（连续失败真的跳闸）<br>· CPU 降载（2026-10-24）：`RESOURCE_EXHAUSTED` + `"service overloaded"`，全局 shedder，阈值沿用 Go 默认 900<br>⚠️ **不要**把任何一条当偏离放行；⚠️ 也**不要**以为「已经对齐了」就顺手把**其余 9 个 rpc 组件**也改了 —— 那是别的域的文件，见 `DRIFT-MSG-V-026` |
| proto `package` 归一（`comment` / `like` 的 `SD-04` / `-05` 同款） | ❌ **不存在**。两侧 proto package 都是 `message`；Go 生成物的语言包名 `pb` 来自 `option go_package`，不是 proto package。见 [`DRIFT/message/proto.md`](../../DRIFT/message/proto.md) §2 |
| `Content-Type` 缺 `charset`（`DRIFT-MSGAPI-010`） | ❌ 未签字 —— 属 **DRIFT**，且是 `OBSERVABLE DIFFERENCE` / LOW。见 `DRIFT/message/api.md`<br>⚠️ **2026-10-24 用户拍板「保持登记，本轮不做」**（**不是**签字）—— 理由：改 charset 会让**全部 10 个 api** 的 JSON 响应头都变 ⇒ 属跨域共享面改动。<br>⇒ 读法：它**仍是登记在册的待办**，只是**这一轮不动**；⚠️ **不要**在别的域的会话里「顺手把它修了」 |
| 状态行缺 reason phrase（`DRIFT-MSGAPI-011`） | ❌ 未签字 —— 同上，**2026-10-24 用户拍板「保持登记，本轮不做」**；且归因尚未定位到共享层的具体一行（台账里写「待查」，不臆断） |
| `message_conversation` 的时间列 `TIMESTAMP`（`DRIFT-MSG-V-020`） | ✅ **已修（2026-10-23 第五轮）—— 早已不是待办** ⇒ `V5__message_time_columns_timestamptz.sql` 把 8 列改成 `TIMESTAMPTZ`，第十轮双侧活体对拍实测两侧 `data_type` **0 差异**。<br>⚠️ **2026-10-24 第十一轮更正**：本行原先写「❌ 未签字 —— 属 DRIFT」，但该条**早已修好** ⇒ 放在这张「本域**没有**的签字偏离」表里，会让人去重新评审一条**已闭合**的漂移 ⇒ 已改成「已修」。**不要**再去动时间列的类型 |