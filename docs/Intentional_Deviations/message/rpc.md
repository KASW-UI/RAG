<!-- @new 2026-10-23 23:1x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `message-rpc` 签字偏离（域内索引）

> **正本在本文件**；总索引见 [`../_INDEX.md`](../_INDEX.md)（⚠️ 该文件是**共享面**，
> 按 `docs/Worktree.md` §三，所有工作树都不许碰 ⇒ 合并后由主会话统一登记本条的编号）。

## 本域签字偏离：2 条

### SD-2026-10-23-07 · 服务端熔断 / CPU 自适应降载：**本轮不实施**（挂起式签字）

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（DRIFT 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) §7 的 `DRIFT-MSG-V-016`） |
| **Go 行为** | 真源默认全开（`etc/message.yaml` 一个都没配 ⇒ 取结构体默认值）：<br>· **熔断**：`zrpc/internal/config.go:25` `Breaker default=true` ⇒ 开路时返回 `UNAVAILABLE` + description `"circuit breaker is open"`（`breakerinterceptor.go:45-48`、`core/breaker/breaker.go:19`）<br>· **CPU 降载**：`zrpc/config.go:47` `CpuThreshold default=900` ⇒ 拒绝时返回 `RESOURCE_EXHAUSTED` + description `"service overloaded"`（`sheddinginterceptor.go:30-35`、`core/load/adaptiveshedder.go:34`） |
| **RAG 行为** | **两个都没有**。服务端 panic 已有 recover（`SD` 同轮的 V-016 recover 半边），但**过载/批量失败时 RAG 会继续把请求打进 PG**，直到 Hikari 池耗尽 / 超时 |
| **类别** | `Resilience`（**过载保护缺失**，非 wire 契约上的字段差异，而是**过载时的可观察行为差异**） |
| **签字依据** | **2026-10-23 用户指示**：「做recover，breaker/shedding先放着」<br>⚠️ **性质**：这是**挂起式签字**（本轮明确不做），不是「已评估后决定永久不同」。详见下方「重新评估的前置条件」 |
| **⚠️ 为什么现在做不了（真实原因，**不是**成本高）** | ① **跨组件不变量**：api 侧的**客户端**熔断已由 `DRIFT-MSGAPI-004` 就位（`GoStyleGrpcClientBreakerInterceptor`）。服务端一旦开始吐 `UNAVAILABLE`，客户端熔断可能被打开 ⇒ **两端必须一起设计**，单独补服务端会造出一条**没人设计过的新失效模式**（双侧重试放大）。<br>② 属**运维策略**：降载阈值（多严）该由用户定，不该由「对齐 Go」顺手带进来。 |
| **⚠️ 成本判断已被更正（2026-10-23 23:5x）** | 此前 DRIFT 台账里写「只能引 resilience4j、算法不可能逐字对齐」—— **该判断是错的**。仓里**已有** go-zero `googlebreaker.go` 的**逐行 Java 复刻**（`service/common/.../protection/GoStyleBreaker.java`，278 行 + 8 例测试）与 `adaptiveshedder.go` 的判据复刻（`GoStyleAdaptiveShedder.java`，373 行 + 10 例测试）。⇒ **真实成本 = 各包一层 `ServerInterceptor` 薄壳**（breaker 还需把隔离 key 从 REST 的 `METHOD://path` 换成 zrpc 的 `info.FullMethod`，并实现 `serverSideAcceptable`）。**本条挂起不是因为做不起，是因为上面①那条跨组件约束。** |
| **⚠️ 重新评估的前置条件** | 补这两条**必须**同时满足：① 与 api 侧客户端熔断**一起设计**（含「双侧重试放大」的处置）；② 降载阈值由用户显式给定。<br>⚠️ 补的时候**不得**引入 resilience4j —— 会引入算法不等价的固定阈值语义，与 Go 的自适应概率丢弃不是一回事。 |
| **⚠️ 不得做的事** | 不要因为「recover 已经补了，breaker 看起来只差一个类」就顺手补上（这正是本条存在的理由）。<br>⚠️ 也不要**反过来**把 RAG 改成「主动抛 `UNAVAILABLE`」来「对齐」——那会让 RAG 在**没有熔断算法**的情况下瞎拒流，比现在更糟。 |
| **⚠️ 副作用（知情）** | 真源在过载时**主动拒流并保住进程**；RAG 不会。⇒ **过载场景下 RAG 的表现与 Go 不同**，且 RAG 更脆弱（会把请求打进 DB 直到池耗尽）。<br>⇒ 但注意：**这个差异在本仓的测试环境里观测不到**（没有过载注入）—— 属于**没有守门测试的静默差异**，靠本条签字兜住。 |

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

## 本域**没有**的签字偏离（避免后人误补）

| 曾被怀疑的点 | 实测结论 |
|---|---|
| **服务端 panic 恢复**（`DRIFT-MSG-V-016` 的 recover 半边） | ❌ **不存在偏离 —— 已对齐**。2026-10-23 23:5x 实施 `GoStyleRecoverInterceptor`：`INTERNAL` + `"panic: "` 前缀 + 进程存活，5 例真 in-process gRPC + 3 轮变异验证。<br>⚠️ **不要**把它当偏离放行；⚠️ 也**不要**因为「recover 已经补了」就顺手把 breaker/shedding 也补上 —— 那两条是 `SD-2026-10-23-07` |
| proto `package` 归一（`comment` / `like` 的 `SD-04` / `-05` 同款） | ❌ **不存在**。两侧 proto package 都是 `message`；Go 生成物的语言包名 `pb` 来自 `option go_package`，不是 proto package。见 [`DRIFT/message/proto.md`](../../DRIFT/message/proto.md) §2 |
| `Content-Type` 缺 `charset`（`DRIFT-MSGAPI-010`） | ❌ 未签字 —— 属 **DRIFT**（要修），且是 `OBSERVABLE DIFFERENCE` / LOW。见 `DRIFT/message/api.md` |
| 时间列 `timestamptz` → `timestamp`（`DRIFT-MSG-V-020`） | ❌ 未签字 —— 属 **DRIFT**，且**后果是双向的**（见 `DRIFT/message/rpc.md` 该节 ⚠️ 段：Go 的 AutoMigrate 会反向 `ALTER` 回来）。不要当偏离放行 |