<!-- @new 2026-10-23 23:1x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `message-rpc` 签字偏离（域内索引）

> **正本在本文件**；总索引见 [`../_INDEX.md`](../_INDEX.md)（⚠️ 该文件是**共享面**，
> 按 `docs/Worktree.md` §三，所有工作树都不许碰 ⇒ 合并后由主会话统一登记本条的编号）。

## 本域签字偏离：3 条（其中 `SD-07` 已于 2026-10-24 **撤销**）

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

## 本域**没有**的签字偏离（避免后人误补）

| 曾被怀疑的点 | 实测结论 |
|---|---|
| **服务端 panic 恢复 / 熔断 / CPU 降载**（`DRIFT-MSG-V-016` 三个拦截器） | ❌ **不存在偏离 —— 已全部对齐**。<br>· recover（2026-10-23 23:5x）：`INTERNAL` + `"panic: "` 前缀 + 进程存活；5 例真 in-process gRPC + 3 轮变异验证<br>· breaker（2026-10-24）：`UNAVAILABLE` + `"circuit breaker is open"`，隔离 key = gRPC 全方法名，失败判据 = Go 的 6 码白名单；含 message-rpc **真 gRPC 端到端** 2 例（连续失败真的跳闸）<br>· CPU 降载（2026-10-24）：`RESOURCE_EXHAUSTED` + `"service overloaded"`，全局 shedder，阈值沿用 Go 默认 900<br>⚠️ **不要**把任何一条当偏离放行；⚠️ 也**不要**以为「已经对齐了」就顺手把**其余 9 个 rpc 组件**也改了 —— 那是别的域的文件，见 `DRIFT-MSG-V-026` |
| proto `package` 归一（`comment` / `like` 的 `SD-04` / `-05` 同款） | ❌ **不存在**。两侧 proto package 都是 `message`；Go 生成物的语言包名 `pb` 来自 `option go_package`，不是 proto package。见 [`DRIFT/message/proto.md`](../../DRIFT/message/proto.md) §2 |
| `Content-Type` 缺 `charset`（`DRIFT-MSGAPI-010`） | ❌ 未签字 —— 属 **DRIFT**（要修），且是 `OBSERVABLE DIFFERENCE` / LOW。见 `DRIFT/message/api.md` |
| 时间列 `timestamptz` → `timestamp`（`DRIFT-MSG-V-020`） | ❌ 未签字 —— 属 **DRIFT**，且**后果是双向的**（见 `DRIFT/message/rpc.md` 该节 ⚠️ 段：Go 的 AutoMigrate 会反向 `ALTER` 回来）。不要当偏离放行 |