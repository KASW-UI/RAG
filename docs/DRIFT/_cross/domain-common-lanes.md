<!-- @new 2026-10-27 · 由主会话（Jason 指定）产出 · 基线 commit e519073（main == do-my-own-gh） -->

# 域 × `service/common` 漂移 lane 台账（10 条 lane 的总纲与清单）

> **建立时间**：2026-10-27（Jason 指定：合并所有工作树后，重建「域 ↔ `service/common`」专项 lane）
> **命名含义**：lane 名 `<域>-common` = 「**本域 ↔ `service/common`**」这条**跨层**专项
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/<域>/<域>-common`，另见各域 `docs/DRIFT/<域>/common.md`）
> **权威状态**：本文件是 **lane 的总纲**；逐条证据仍以 **各域自己的台账** + `docs/DRIFT/common/`（`service/common` 机制面）为准
> ⇒ **§0 的 ✅ 一律读作「在该证据强度下一致」**，不是已闭环（AGENTS.md §0 证据强度分层）

---

## 0. 一句话

`service/common` 是**所有域共用的机制层**（超时/deadline 传播、拦截器链、防护链、错误码映射、端口治理、trace/otel）⇒
**任何一域的行为漂移，只要根因落在 `service/common`，就不可能单域闭环** ⇒ 每域必须有一条**自己负责的对账 lane**，
把「本域看到的现象」与「common 侧的真实机制」逐条对上；**域侧能修的域侧修，common 侧只能由 `global` 修**（AGENTS.md §5.3d）。

| 项 | 值 |
|---|---|
| lane 数 | **10**（9 个微服务域 + `global` 收口域） |
| 工作树目录 | `/Users/pr/Documents/java&golang/RAG.worktrees/<域>-common` |
| 分支 | `align/<域>-common`（全部从 `do-my-own-gh` 切，主干最后接合并） |
| 基点 | `e519073`（2026-10-27 · 合并 25/25 分支 + 全量回归 BUILD SUCCESS 之后） |
| `service/common/**` 写权 | 🔴 **只有 `align/global-common` 能写**（AGENTS.md §5.3c·§5.3d）；其余 9 条 lane **只读 common 侧** |
| **每条 lane 的台账** | **`docs/DRIFT/<域>/vs-common.md`**（**每条 lane 独占一份** ⇒ 10 路并发零冲突） |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

---

## 1. 10 条 lane 清单

| # | lane | 工作树 | 分支 | lane 台账（独占） | 域台账里与 common 相关的条目（实测抽取） |
|---|---|---|---|---|---|
| 1 | `article-common` | `RAG.worktrees/article-common` | `align/article-common` | [`article/vs-common.md`](../article/vs-common.md) | **96 处** · `DRIFT-HOTAPI-030`（Tomcat 参数数口径）· `DRIFT-ARTICLE-API-006`（必填字段校验）· `S-6`（`SqlTimeoutStatusTranslator` 产出的 `DEADLINE_EXCEEDED` 被降级为 `UNKNOWN`） |
| 2 | `comment-common` | `RAG.worktrees/comment-common` | `align/comment-common` | [`comment/vs-common.md`](../comment/vs-common.md) | **41 处** · `D8`/`D9` = global **E65**/**E66** · **E30/E38/E46/E47 只读指针（归属 global）** · `PIT-GLOBAL-051` · `SD-2026-10-24-CAND-08`（保护层覆盖范围·已签字） |
| 3 | `favorite-common` | `RAG.worktrees/favorite-common` | `align/favorite-common` | [`favorite/vs-common.md`](../favorite/vs-common.md) | **121 处** · **E21**（`grpc-inprocess` test scope）✅ 已修 · **E62** 毫秒精度 ✅ 已修 · **E65** 出向 deadline 🔴 OPEN · **E68** 锚点实体判据 |
| 4 | `follow-common` | `RAG.worktrees/follow-common` | `align/follow-common` | [`follow/vs-common.md`](../follow/vs-common.md) | **43 处** · 🔴 `DRIFT-FOLLOW-008` **MaxConns 闸门：Go 每路由一个 vs RAG 全局一个**（**未修** · 落点 `service/common` ⇒ 派单）· `DRIFT-FOLLOW-002`（`grpc-inprocess`）✅ 已修 |
| 5 | `hot-common` | `RAG.worktrees/hot-common` | `align/hot-common` | [`hot/vs-common.md`](../hot/vs-common.md) | **53 处** · `DRIFT-HOTAPI-024`（charset）✅ 已修（global T-6）· `DRIFT-HOTAPI-014`（跨域移交 common）· `DRIFT-HOTRPC-004`（client breaker 装配）✅ 已闭合 · D14/D25/D27 **已全部闭合** |
| 6 | `like-common` | `RAG.worktrees/like-common` | `align/like-common` | [`like/vs-common.md`](../like/vs-common.md) | **96 处** · 🔴 `DRIFT-LIKEAPI-015` **Content-Type 大小写**（落点 `GoStyleJsonBodyConverter`，**6 个域共用 ⇒ 须单域串行**）· `DRIFT-LIKEAPI-017` 单次 RPC 超时（Go 固定 2000ms）· `DRIFT-LIKE-006`/`-013` |
| 7 | `message-common` | `RAG.worktrees/message-common` | `align/message-common` | [`message/vs-common.md`](../message/vs-common.md) | **185 处** · `DRIFT-MSG-V-016`（三拦截器）✅ **已全处置** · `DRIFT-MSG-V-025`（`SheddingStat` 三计数）✅ **已修**（`GoStyleSheddingStat`）· 🔴 `DRIFT-MSGAPI-010`（响应 charset）· `DRIFT-MSGAPI-011`（reason phrase）· `DRIFT-MSGAPI-013`（缺 `Traceparent`）—— **三条均落 `service/common`** |
| 8 | `task-common` | `RAG.worktrees/task-common` | `align/task-common` | [`task/vs-common.md`](../task/vs-common.md) | **21 处** · `DRIFT-MSG-V-026` **熔断/降载链必须真挂到 gRPC server**（`TaskProtectionChainWiringTest` 2 条）· task **无 `-common` 模块**（Go 侧同样没有 ⇒ 不是遗漏） |
| 9 | `users-common` | `RAG.worktrees/users-common` | `align/users-common` | [`users/vs-common.md`](../users/vs-common.md) | **324 处** · `DRIFT-ARPC-010` = **E66**（Redis 写不绑 deadline）🔴 OPEN · `UNRESOLVED-ARPC-002` = **E38②**（定性为 CONFIRMED SEMANTIC MISMATCH）· `DRIFT-USERAPI-013`（breaker 键空间 + MaxConns 粒度）🔴 待拍板 · `DRIFT-ADMINAPI-007`（503 缺 `nosniff` ⇒ **已修**，charset 空格残差仍在） |
| 10 | `global-common` | `RAG.worktrees/global-common` | `align/global-common` | [`global/vs-common.md`](../global/vs-common.md) | **收口域** ⇒ 本 lane 就是「**`service/common` 本身**」的 owner lane：E21 / E38② / E46 / E47 / E48 / E62 / E65 / E66 / E68 / E69 / E70 + `GoStyleBreakerFilter` 注释订正 + `SqlTimeoutStatusTranslator` javadoc 归因 + `manifest_chain --no-merges` |

> ⚠️ **写死数字的纪律**（AGENTS.md §6）：上表的「条目数」是 **2026-10-27 的抽取快照**（按 `service/common` / `GoStyle*` / `RpcInterceptor` / `DeadlineQueryTimeoutInjector` / `grpc-inprocess` / `DEADLINE_EXCEEDED` / `Traceparent` 等关键词计数）⇒ **只作规模参考**，逐条结论以各域台账为准。

---

## 2. common 侧机制面轴（每条 lane 都要逐轴对账）

> ⚠️ **Go 出处的行号均已逐条核验**（go-zero v1.10.2 + 真源 `Sea-RideTheWind_golang`）；
> go-zero 的路径在**第三方库**里（不在真源仓内）⇒ 引用时**不带**仓库前缀（沿用本仓既有写法）。

| 轴 | 机制 | RAG 落点（共享层 `service/common/src/main/java/com/rag/common/`） | Go 出处 | 机制面台账 |
|---|---|---|---|---|
| **A1 超时 / deadline** | 请求 deadline 绑到 SQL；超时取消；`DEADLINE_EXCEEDED` 描述；毫秒 vs 秒精度 | `persistence/DeadlineQueryTimeoutInjector.java` | `zrpc/config.go:31`（client `Timeout` default=2000）· `zrpc/config.go:46`（server `Timeout` default=2000） | [`common/deadline.md`](../common/deadline.md) |
| **A2 gRPC 拦截器链** | 服务端 unary 三拦截器（recover / breaker / CPU shedding）＋ 客户端超时与 deadline | `observability/RpcInterceptor.java` | `zrpc/internal/serverinterceptors/recoverinterceptor.go` · `.../breakerinterceptor.go` · `.../sheddinginterceptor.go` · `zrpc/internal/config.go:21`（Recover default=true）· `zrpc/internal/config.go:25`（Breaker default=true）· `zrpc/config.go:47`（CpuThreshold default=900） | [`global/cross-domain.md`](../global/cross-domain.md) |
| **A3 HTTP 保护链** | breaker / shedding / maxBytes / maxConns / form 参数上限 / Content-Type 判据 / 路径归一 | `protection/*` ＋ `http/*` | `rest/handler/breakerhandler.go:18-42`（熔断）· `rest/handler/maxconnshandler.go:12` · `rest/handler/maxbyteshandler.go` · `rest/handler/sheddinghandler.go:22` | [`common/protection.md`](../common/protection.md) · [`common/http.md`](../common/http.md) |
| **A4 错误码 / envelope** | gRPC code → HTTP 状态码 → `{code,msg,data}` 信封；404/405 裸响应；`nosniff` | `http/GoStyleRouterErrorResponses.java` ＋ `observability/HttpMiddleware.java` | `service/common/response/response.go`（Go 侧三字段无 `omitempty`） | [`common/http.md`](../common/http.md) |
| **A5 端口治理** | `RANDOM_PORT` 暴露面 / 各域端口段（不与 devh 分配段撞车） | 各域 `application.yml` ＋ `RealTomcatPort` | — | 各域台账 §9.3 / §9.5 |
| **A6 errmsg 契约** | 业务码 ↔ gRPC code ↔ description 的逐字节保真 | 各域 `<域>-common` ＋ `service/common` | `<域>/common/errmsg.go` | 各域 `docs/DRIFT/<域>/common.md` |
| **A7 proto 契约** | message / 字段号 / 枚举值 | `service/<域>/<域>-proto` | `<域>/pb/*.proto` | [`_cross/proto-verification.md`](proto-verification.md) |
| **A8 DDL 时间列 / 类型** | `timestamptz` / `CURRENT_TIMESTAMP` / jsonb⇒text；**Flyway 独立账本 + `baseline-version: 0`** | 各域 `db/migration/*.sql` ＋ `service/common` 的 typeHandler | GORM `AutoMigrate` 产物 | [`_cross/ddl-type-divergences.md`](ddl-type-divergences.md) |
| **A9 trace / otel** | span 属性（`audit.*`）＋ `Traceparent` 传播 | `observability/*`（`GoStyleOtelAgent.java` 等） | go-zero `otel` 埋点 | [`common/audit.md`](../common/audit.md) |

⇒ **每条 lane 的交付物 = 这 9 条轴上「本域侧」的逐轴结论**（✅ 一致 / 🔴 漂移 / 🟡 OBS / N/A）+ 证据强度 + 核对日期。

---

## 3. 写权边界（lane 的硬约束）

| 面 | 谁可写 | 依据 |
|---|---|---|
| `service/common/**` | 🔴 **只有 `align/global-common`** | AGENTS.md §5.3c·§5.3d |
| 本域的 `service/<域>/**` ＋ `docs/DRIFT/<域>/**` | ✅ 本域 lane | AGENTS.md §5.3b（只写自己域） |
| `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` | 🔴 合并后统一更新 | AGENTS.md §5.3 |

⇒ **lane 发现 common 侧缺陷时**：① 域侧先把**现象 + 证据 + 建议修法**写进**本域台账**（`docs/DRIFT/<域>/vs-common.md`）；
② 转派 `global-common`（`docs/DRIFT/global/_README.md` §七之二的跨域桶）；③ **不得**在域 lane 里改 `service/common`（§5.3d：否则留下半截）。
⇒ **域侧能自修的**（本域代码里的调用方式、配置、断言、装配）**一律本域修**，不要甩给 global。

---

## 4. 跨 2+ 域的未闭合项（多域 lane 的交汇点）

> ⚠️ **状态以 2026-10-27 核对为准**；每条的**逐条证据**以总纲 §1 指向的域台账为准。

| 项 | 涉及域 | 落点 | 状态（2026-10-27） |
|---|---|---|---|
| 🔴 `DRIFT-LIKEAPI-015` Content-Type 大小写 | like ＋ **6 个域共用** `GoStyleJsonBodyConverter` | `service/common` | **未修** ⇒ **须单域串行**（一域改、全域受益 ⇒ 别 6 个域并行改同一文件） |
| 🔴 `DRIFT-FOLLOW-008` MaxConns 闸门（每路由 vs 全局） | follow ＋ 全域 | `service/common` | **未修** · 落点 common ⇒ 派单 global |
| 🔴 `DRIFT-USERAPI-013` 过载保护（breaker 键空间 + MaxConns 粒度 + 注释与实现矛盾） | users ＋ 全域 | `service/common` | **待拍板**（users 域台账原文；与 `DRIFT-FOLLOW-008` **同族** ⇒ 两域须同时对账） |
| 🔴 `DRIFT-MSGAPI-010` 成功/错误响应 `Content-Type` 缺 `charset` | message ＋ **全部 10 个 api** | `service/common` | **OPEN**（2026-10-24 用户拍板「保持登记，本轮不做」） |
| 🔴 `DRIFT-MSGAPI-011` 状态行缺 reason phrase | message ＋ 全域 | `service/common` | **OPEN**（同上） |
| 🔴 `DRIFT-MSGAPI-013` 缺 `Traceparent` | message ＋ 全域 | `service/common` | **OPEN** |
| 🔴 E38② 业务层吞掉「SQL 被 deadline 取消」（机制层） | **users ＋ comment ＋ favorite ＋ …** | `service/common` | **A 类 backlog**（`global` 写权内 ⇒ 不需拍板） |
| 🔴 E65 出向 gRPC 不继承请求 deadline | favorite ＋ comment ＋ users | `service/common`（`RpcInterceptor` 只绑 `DeadlineState`、**不绑** `GoStyleRequestDeadline` ⇒ gRPC 路径 `applyTo` 是 **no-op**） | **OPEN** |
| 🔴 E66 = `DRIFT-ARPC-010` Redis 写不绑 deadline | users/admin ＋ 各域 | `service/common` | **OPEN**（两侧同编号 ⇒ 销账别当两条） |
| 🟡 `DRIFT-ADMINAPI-007` 503 响应的 `Content-Type` **分号后空格**残差 | users/admin ＋ 全域 | `service/common` | **半修**（`nosniff` 已落地于 `HttpMiddleware.java:692` ⇒ 原条自动消失；**charset 空格残差仍在** ⇒ 需重开一条） |
| 🟡 `article-rpc` / `favorite-rpc` 缺 `baseline-version: 0` | article ＋ favorite | 各自 `application.yml`（**域侧** ⇒ 域 lane 自修） | **未修** ⇒ 非空 schema + `baseline-on-migrate=true` ⇒ **V1 被整条跳过**（起得来、首次查库才炸） |
| ✅ `DRIFT-MSG-V-016` go-zero 三个 unary 拦截器 | message ＋ 全域 | `service/common` | ✅ **已全处置**（recover 2026-10-23 ＋ breaker/shedding 2026-10-24） |
| ✅ `DRIFT-MSG-V-025` 熔断/降载指标缺三计数 | message | `service/common` | ✅ **已修**（第九轮 · `GoStyleSheddingStat.java` 三计数已落码） |
| ✅ E21 `grpc-inprocess` test scope ⇒ 运行时 artifact 消失 | article / like / task（+ 4 个 `-common` 库） | `service/common` | ✅ **已修**（global L12/L13，随 `4f839f9` 合入主干 ⇒ 真缺 = 0） |
| ✅ E46 / E62 超时 deadline 与毫秒精度 | 全域 | `service/common` | ✅ **已修并合回主干**（post-E46 + E62 ⇒ 毫秒级） |

---

## 5. 一条 lane 的执行协议（10 条统一）

1. **先让它真的启动一次**（AGENTS.md §0 第 3 条）：「跑绿」≠「对」⇒ 本域组件的**真启动**是 lane 的第 0 步。
2. **按 §2 的 9 条轴逐轴对账**：轴上**本域侧**的现状 ⇒ 结论写「✅/🔴/🟡 + 证据强度 + 核对日期」。
3. **两侧同时跑**（AGENTS.md §3.3）：探针必须**两侧同时跑**（Go 侧调真源解析包，RAG 侧用**真实** mapper/容器）⇒ **只跑 RAG 的探针不算数**。
4. **common 侧缺陷 ⇒ 转派 `global-common`**（本域只登记，不改 `service/common`）。
5. **落账位置**：域侧条目写**本域台账**（`docs/DRIFT/<域>/vs-common.md` ＋ 既有域台账）；跨域项同时在**本文件 §4** 登记一行（便于多域对齐时看交汇点）。
6. **收口判据**：本 lane 的 common 相关条目**要么 ✅（在该证据强度下一致），要么 🔴 已登记并转派**，且**每条都有核对日期**（§6 状态表纪律）。

---

## 6. per-domain lane 台账（每条 lane 独占一份）

> ⚠️ **命名固定为 `docs/DRIFT/<域>/vs-common.md`** —— 与该域自己的 `-common` 库台账 `docs/DRIFT/<域>/common.md` **刻意不同名**，避免混淆。

| lane | lane 台账 | 域台账（既有，逐条证据） |
|---|---|---|
| `article-common` | [`article/vs-common.md`](../article/vs-common.md) | [`article/api.md`](../article/api.md) · [`article/rpc.md`](../article/rpc.md) |
| `comment-common` | [`comment/vs-common.md`](../comment/vs-common.md) | [`comment/api.md`](../comment/api.md) · [`comment/rpc.md`](../comment/rpc.md) |
| `favorite-common` | [`favorite/vs-common.md`](../favorite/vs-common.md) | [`favorite/rpc.md`](../favorite/rpc.md) |
| `follow-common` | [`follow/vs-common.md`](../follow/vs-common.md) | [`follow/api.md`](../follow/api.md) · [`follow/rpc.md`](../follow/rpc.md) |
| `hot-common` | [`hot/vs-common.md`](../hot/vs-common.md) | [`hot/api.md`](../hot/api.md) · [`hot/rpc.md`](../hot/rpc.md) |
| `like-common` | [`like/vs-common.md`](../like/vs-common.md) | [`like/api.md`](../like/api.md) · [`like/rpc.md`](../like/rpc.md) |
| `message-common` | [`message/vs-common.md`](../message/vs-common.md) | [`message/api.md`](../message/api.md) · [`message/rpc.md`](../message/rpc.md) |
| `task-common` | [`task/vs-common.md`](../task/vs-common.md) | [`task/rpc.md`](../task/rpc.md) · [`task/_README.md`](../task/_README.md) |
| `users-common` | [`users/vs-common.md`](../users/vs-common.md) | [`users/_shared.md`](../users/_shared.md) · [`users/user/`](../users/user/) · [`users/admin/`](../users/admin/) |
| `global-common` | [`global/vs-common.md`](../global/vs-common.md) | [`global/_README.md`](../global/_README.md) · [`global/cross-domain.md`](../global/cross-domain.md) · [`global/common-tails.md`](../global/common-tails.md) · [`global/lanes/`](../global/lanes/) |

---

## 7. 相关文件

| 文件 | 作用 |
|---|---|
| [`docs/DRIFT/common/`](../common/) | **common 侧**机制面台账（deadline / http / protection / audit / 首轮对照） |
| [`docs/DRIFT/global/`](../global/) | **收口域**台账（E 系列 + 跨域桶 + lane 协议） |
| 各域 `docs/DRIFT/<域>/` | **域侧**台账（含各域自己的 `-common` 库台账 `common.md`） |
| [`docs/Pitfalls/global/cross-domain.md`](../../Pitfalls/global/cross-domain.md) | 跨域踩坑（N1~N7 等） |
| [`docs/DRIFT/_INDEX.md`](../_INDEX.md) | 全部台账索引（共享面，合并后统一更新） |
