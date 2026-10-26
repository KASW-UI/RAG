<!-- @new 2026-10-27 · `users-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G1 波次工作树（`align/g1-users-follow`）** -->

# `users-common` lane 台账 —— 「users ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/users/users-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G1` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `users-common`（**原第 9/10 条**） |
| **现分支 / 工作树** | **`align/g1-users-follow`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow`<br>⚠️ 原 `align/users-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/users/{user/{user-api, user-rpc, user-proto}, admin/{admin-api, admin-rpc, admin-proto}, users-common}` |
| 域侧台账 | [`_shared.md`](_shared.md) · [`user/api.md`](user/api.md) · [`user/rpc.md`](user/rpc.md) · [`admin/api.md`](admin/api.md) · [`admin/rpc.md`](admin/rpc.md) · [`admin/_shared.md`](admin/_shared.md) |
| **写权** | ✅ 可写**本域** `service/users/{user,admin}/**` ＋ `docs/DRIFT/users/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；🔴 **`service/users/users-common/**` 也归 G4**（跨 6 个外域 ⇒ 第二个共享层）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| 🔴 `DRIFT-ARPC-010` = **E66** | Redis 写**不绑 deadline**（admin `logout` 的 Redis 写） | `service/common`（Redis 调用路径） | 🔴 **OPEN**（两侧同编号 ⇒ 销账别当两条） | [`admin/api.md`](admin/api.md) |
| ✅ `UNRESOLVED-ARPC-002` = **E38②** | 超时是否 **min 客户端 deadline**（Go `context.WithTimeout(parent,2s)` 继承）⇒ 🔴 曾定性为 **CONFIRMED SEMANTIC MISMATCH** ⇒ ✅ **2026-10-27 口径订正：① 已闭环**（RAG 侧 2026-10-26 已落地 `Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）⇒ **② 架构级（业务线程不被取消）已另立项** ⇒ **不是「① 没做」** | `service/common` | ✅ **① 已闭环**（双侧活体探针 `client_deadline_ctx` 一致）· ⏸ **② 未做**（Jason 2026-10-26 拍板另立项）⇒ **本轮不落码** | [`admin/rpc.md`](admin/rpc.md) |
| 🔴 `DRIFT-USERAPI-013` | 过载保护：**MaxConns 闸门粒度** + **breaker 键空间**（Go key = 注册期 `route.Path` 模板 ⇒ 基数 = 路由数；RAG = raw `getRequestURI()` ⇒ 基数 = 参数取值数）+ 注释与实现矛盾 | `service/common` | 🔴 **待拍板**（域台账原文）；⚠️ **与 follow 域 `DRIFT-FOLLOW-008` 同族** | [`user/api.md`](user/api.md) |
| 🟡 `DRIFT-ADMINAPI-007` | 503 超时响应缺 `X-Content-Type-Options: nosniff` | `service/common` | 🟡 **半修** —— `nosniff` **已落地**（`HttpMiddleware.java:692`）⇒ 原条自动消失；**charset 分号后空格残差仍在** ⇒ 需重开一条 | [`admin/api.md`](admin/api.md) |
| `DRIFT-ARPC-006` | OTel `audit.*` span 属性（`operator_id` 不恒为 0） | `service/common`（otel 埋点） | ✅ **已修** | [`admin/_shared.md`](admin/_shared.md) |
| 🔴 `UNRESOLVED-USERSCOMMON-003` | `http/GoStyleCoercion` 的**全局副作用**是否波及非 users 域（通过 `JsonMapperBuilderCustomizer` 全局施加） | `service/common` | 🔴 **未核** —— 本轮只审了 users 域 ⇒ **需在 message/like/article 各打一次带标量的请求矩阵** | [`common.md`](common.md) |
| `DRIFT-USERRPC-V-007` | 超时 description 逐字一致（`context deadline exceeded` vs `rpc timeout after 2000ms`） | `service/common` | ✅ **已合回主干 `4f839f9`** ⇒ 双侧活体复跑一致 | [`user/api.md`](user/api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

### 1.1 §1 抽取快照的**逐条状态复核**（2026-10-27 · G1 lane）

| 编号 | 快照状态 | **复核结论（核对日期 2026-10-27）** | 复核依据 / 强度 |
|---|---|---|---|
| `UNRESOLVED-USERSCOMMON-003` | 🔴 未核（落点 `service/common`） | ✅ **口径已订正** ⇒ **不再整条甩 G4**（拆成「域侧自修」＋「共享层」两半） | **本轮实测 8 个域的 `WebMvcConfig`**（L3 活体）⇒ 见 §6.4.1 |
| `DRIFT-USERAPI-013` | 🔴 待拍板 | 🔴 **维持待拍板**；只读复核已于 2026-10-26 完成（键语义钉死 · **可观察面在 `article-api` ⇒ user-api 本组件不可观察**）⇒ 落点确认在共享层 ⇒ **派 G4** | 域台账原文（L1） |
| `DRIFT-ARPC-010`（= E66） | 🔴 OPEN | 🔴 **维持 OPEN**（本轮**未复核落点**）⇒ 派单时仍须核 Go 侧 `文件:行号` | 本轮未查 |
| `UNRESOLVED-ARPC-002`（= E38②） | 🔴 未闭合 | ✅ **口径订正（2026-10-27）＝「① 已闭环 ② 未做（已另立项）」** ⇒ **本轮不需要落码**：① 纯 deadline 计算**已落地**（`RpcInterceptor.java:234` 的 `Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）⇒ 剩下**第②层**（grpc-java `ServerCall#close()` **不取消业务线程** ⇒ 在途 SQL 不随客户端 deadline 取消 ⇒ 与 T-14 同根 ⇒ 架构级 ⇒ Jason 2026-10-26 拍板「**另立项**」）⇒ ⚠️ **「① 不销本条」的正解是「① 已做、② 在等立项」⇒ 不是「① 没做」** | **L3 双侧活体**（`tools/semantic-probe/rpc-timeout/` ⇒ EXIT=0 · `client_deadline_ctx` 一致 · 3/3 一致）+ L1 源码（`RpcInterceptor.java:234/263/592-601`） |
| `DRIFT-ADMINAPI-007` | 🟡 半修 | 🟡 **维持半修**（`nosniff` 已落地于 `HttpMiddleware.java:692` ⇒ 原条自动消失；**charset 分号后空格残差仍在** ⇒ **需重开一条**） | 域台账原文（L1） |
| `DRIFT-USERRPC-V-007` | ✅ 已合回 `4f839f9` | ✅ **维持**（本轮**未重跑**双侧活体 ⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」） | 域台账原文（L3，2026-10-26） |

⚠️ **本轮复核范围声明（诚实边界）**：§1 表里**只有 `UNRESOLVED-USERSCOMMON-003` 的落点被本轮实测订正**；
其余 5 条**状态未变**（其中 3 条**本轮未复核**）⇒ 按 AGENTS.md §6 记「状态未变」，
**派单时仍须按反馈单 7 项重新核 Go 侧 `文件:行号`**（台账的「抽取快照」**不算数**）。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

> ⚠️ **口径声明（2026-10-27 · G1 lane 复核）**：本轮 = **第 0 步真启动** ＋ **台账状态逐条复核** ＋ **两条新缺陷的活体坐实**。
> ⚠️ **A1~A9 的逐轴「双侧活体对拍」本轮尚未重跑** ⇒ 下表的 ✅ 一律读作「**在台账既有证据强度下一致**」，
> **不是「本轮重新证明了等价」**（AGENTS.md §0：「已改」≠「已验证」；「源码级对照」≠「双侧活体对拍」）。
> ⚠️ **「证据强度」列 = 台账原有强度**（本轮复核沿用），**不是本轮新证的强度**。

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ① `DRIFT-USERAPI-009②`（**3s 预算作用域**：Go 不覆盖 JWT 验签，RAG 覆盖验签+黑名单+handler）② `DRIFT-USERAPI-015`（Redis 主机名解析失败 ⇒ 200/503 摇摆，**已降级为「条件性成立·证据不足」**）③ `DRIFT-USERRPC-V-012`（超时业务日志归因 ⇒ **落点在本域 `UserDomainService.logout`**）④ `DRIFT-USERRPC-V-013`（DB 超时的源码层形态差异 ⇒ **非 wire 漂移，不得改回 Go 行为**） | 🔴 **4 条 OPEN**（其中 ②③ **域内可闭环**） | L1 源码 + L3 双侧活体（台账原有） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | `DRIFT-USERAPI-011`（客户端熔断 handler）✅ · `DRIFT-USERRPC-V-007`（超时 description 逐字）✅ 已合回 `4f839f9` · ① 三拦截器链（recover/breaker/shedding）已全处置 | ✅ **一致**（在该证据强度下） | L3 双侧活体 | 2026-10-27 |
| **A3** | HTTP 保护链 | ① 本域 `application.yml` 的 `rag.http.go-form-limit.mode` = **raw**（user-api ＋ admin-api 各一行）② `DRIFT-USERAPI-008`（过载保护「不存在」）**已证伪** ③ `DRIFT-USERAPI-012`（降载 RT 口径）✅ ④ `DRIFT-USERAPI-013`（**MaxConns 粒度 + breaker 键空间 + javadoc 矛盾**）🔴 ⑤ `009② 补充`（MaxBytes 相对 observability 的位置）**未签字** | 🔴 **1 条 G4** ＋ 🟡 **1 条待拍板** ＋ 🔴 **1 条域内**（009②） | L1 源码 + L2 运行时 | 2026-10-27 |
| **A4** | 错误码 / envelope | `DRIFT-USERAPI-003/-006/-007/-010` ✅（后两条为**签字偏离** `SD-USERAPI-001` 与 N-1）· `UNRESOLVED-001/003/004` **全部已关**（`UNRESOLVED-001` = 真 Tomcat 双侧 + `check-plaintext400-headers.sh` EXIT=0） | ✅ **一致**（**4 条签字偏离**覆盖 ⇒ 审查见红先查签字档） | L3 真 Tomcat + 双侧活体 | 2026-10-27 |
| **A5** | 端口治理 | ① api 监听 8888 → **49900**（`OBSERVABLE DIFFERENCE`，已签字 `Infrastructure.md §3`）② rpc gRPC `0.0.0.0:9004` → **49210**（`IMPLEMENTATION DIFFERENCE`，已锁定）③ ⚠️ **台账无 `RANDOM_PORT` / `RealTomcatPort` 任何记录** | 🟡 **台账零覆盖 `RANDOM_PORT` 面**（非「已对齐」） | L1 静态 | 2026-10-27 |
| **A6** | errmsg 契约 | code→msg 全表 **30 项 EQUIVALENT**（Go `errmsg.go:38-68` ↔ `users-common.ErrorCode`，以 users-common 为公理，本组件不复验）· N-2（Go 侧 9 处 GBK 双重编码乱码）✅ 签字 | ✅ **一致**（N-2 已签字 ⇒ **不得当新发现再「修」**） | L1 静态 + L3 双侧活体 | 2026-10-27 |
| **A7** | proto 契约 | 两侧 `.proto` 剥离 option/注释/空行后**逐行 diff 零差异** ＋ 常驻守门 `check-proto-static.sh` ＋ `UserInfo.status` 的 `optional` 两侧同 | ✅ **一致** | L1 实测 diff | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | ① `DRIFT-USERRPC-V-002`（`users_user.status` **可空性**）🔴 **被卡**（改动须与 admin 域同改 ⇒ 落点 `users-common` ⇒ **G4 写权**）② `DRIFT-USERRPC-V-011`（时间列 `DEFAULT CURRENT_TIMESTAMP`）⚠️ **未修**（8 处，刻意保留）③ `V-003`（`extra_info` text↔jsonb）✅ `V-004`（4 个时间列 timestamptz）✅ `V-006`（id 取值来源）✅ ④ 表名 `users`→`users_user` ✅ 签字 ⑤ 建表责任交叉 EQUIVALENT（**双侧对称并发启动 9 轮**） | 🔴 **2 条**（V-002 落 G4 ＋ V-011 待裁决） | L4 真 PG（并发 9 轮） | 2026-10-27 |
| **A9** | trace / otel | ① `DRIFT-USERAPI-009①`（401 是否计入观测面）✅ 签字 `SD-USERAPI-002`（**只覆盖 ①**）② `DRIFT-USERRPC-V-012`（业务日志归因）🔴 ③ ⚠️ **本域台账无任何 `Traceparent` / `audit.*` / span 属性条目** | 🟡 **本域台账零覆盖**（A9 在 users/user 域几乎是**空白轴**，不是「已对齐」） | L1 静态 | 2026-10-27 |

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> 本域 `go-form-limit.mode` = **raw（user-api ＋ admin-api 各一行）**；Go 侧：**未注册**（真源 `service/user/api/` 与 `service/admin/api/` 均无 `httpx.SetErrorHandler`）⇒ Go 侧 = **400 + Go 原文** ⇒ 与 RAG `mode=raw` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

- `user-api` / `admin-api` 的 A3 接线复核：各一行 `mode=raw` ⇒ 拒绝形状 = **400 + Go 原文**
- 本域**调用**共享层的方式（`operator_id` 传值 / `GoStyleCoercion` 的**调用点**）⇒ 域侧可自修的先域侧修

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> ⚠️ **本节 = 正式轨（派单单据）**。**实时轨 = `session send` 给 G4 会话**（两棵工作树合并前互相看不见对方的文件 ⇒ **两轨都要有**）。
> ⚠️ **派单前置（2026-10-27 已完成）**：第 2 项的 Go 侧 `文件:行号` **逐条回到冻结真源核过存在性**（不是台账快照）⇒ 见每条「已核 ✅」。
> ⚠️ **本节不含两条新缺陷**（`DRIFT-G1-001` / `DRIFT-G1-002`）⇒ 那两条**落点在本域写权内** ⇒ **域侧自修，不派 G4**（见 §7）。

| # | 编号 | 现象 | Go 侧 `文件:行号`（**已核 ✅**） | RAG 侧现状 | 证据强度 | 建议修法（**两侧都应是 Y**） | 影响面 | 阻塞本波？ |
|---|---|---|---|---|---|---|---|---|
| **1** | `DRIFT-USERAPI-013` ＋ `DRIFT-FOLLOW-008`（**同族，两条一起对账**） | ① **MaxConns 闸门粒度**：Go **每路由一个** latch（RAG = 全局一个）② **breaker 键空间**：Go key = 注册期 `route.Path` **模板**（基数 = 路由数）vs RAG = raw `getRequestURI()`（基数 = 参数取值数 ⇒ 无界）③ `GoStyleBreakerFilter` 的 javadoc 给「无界风险为零」的**错误安全保证** | ① `rest/handler/maxconnshandler.go:12`（`func MaxConnsHandler(n int) func(http.Handler) http.Handler`）<br>② `rest/engine.go:149` → `rest/handler/breakerhandler.go:15,19`（`breakerSeparator="://"`）<br>③ `rest/config.go:52` | `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java`（全局单闸门）· `GoStyleBreakerFilter.java`（raw URI 建 key）· 11 个 `protection/` 类 | **L1 源码逐行对照**（域台账 2026-10-26 只读复核：键语义已钉死 · **可观察面在 `article-api`**，user-api 的 9 条路由零路径变量 ⇒ 本组件不可观察） | 两侧都应是 **Y** = 「**每路由一个 latch**」＋「**breaker key 用注册期 `route.Path` 模板**」＋「**404 取不到模板 ⇒ 不得用 raw URI 兜底**」（这是方案 B 的硬约束） | `service/common` 的 `protection/`（11 类）⇒ **编译面 16 个组件** | 🟡 **不阻塞**（但**与 #2 同族** ⇒ 若 #2 落码，#1 宜同批） |
| **2** | `DRIFT-ARPC-010`（= **E66**，两侧同编号 ⇒ **销账别当两条**） | Redis **写**不绑 deadline（admin `logout` 的 Redis 写）⇒ 超时后写仍在途 | `service/common/observability/rpc.go:38`（`context.WithTimeout(ctx, timeout)` ⇒ ctx 带 deadline ⇒ **出向调用天然继承**） | `service/common` 的 Redis 调用路径 **未绑 `GoStyleRequestDeadline`** ⇒ 只算服务端自己的预算 | **L1 源码**（域台账原文）+ 与 `UNRESOLVED-ARPC-002` 同型 | 两侧都应是 **Y** = 「**Redis 写出向调用绑定请求 deadline**」⇒ 与 gRPC 出向（E65）**同一套绑定机制** | `service/common` ⇒ 编译面 16 个组件 | 🟡 不阻塞 |
| **3** | `UNRESOLVED-ARPC-002`（= **E38②**） ⚠️ **2026-10-27 订正：此条本轮不需要落码** | 超时是否 **min(客户端 deadline, 服务端 timeout)**：Go 继承父 ctx ⇒ 客户端更早就提前取消。**RAG 侧已于 2026-10-26 落地该语义** ⇒ **① 已闭环** | `service/common/observability/rpc.go:38` ⇒ `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)`<br>**已核 ✅**：该行内容逐字 = `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` | ✅ **已落地**：`RpcInterceptor.java:234` ⇒ `Math.min(serverBudgetMs, clientDeadlineRemainingMs())`；`:263` 调度器用 `effectiveTimeoutMs`；`:592-601` 从 `io.grpc.Context.current().getDeadline()` 取 | **L3 双侧活体**（`tools/semantic-probe/rpc-timeout/` **本轮实跑 EXIT=0**：`client_deadline_ctx` **一致** · `timeout_wire` 一致 · 3/3 一致） | 两侧都应是 **Y** = 「**有效期限 = min(客户端 deadline, 服务端 timeout)**」⇒ ✅ **这个 Y 已经达成** ⇒ ⛔ **请勿改 `RpcInterceptor` 的 deadline 计算**（会把对的改错 ⇒ 制造新漂移）<br>剩下**第②层**（`ServerCall#close()` 不取消业务线程 ⇒ 架构级 ⇒ Jason 2026-10-26 已拍板「**另立项**」）⇒ **不是本轮落码项** | `service/common` ⇒ 编译面 16 个组件 | 🟢 **不阻塞**（**本轮不落码**） |
| **4** | `DRIFT-ADMINAPI-007` | 503 超时响应的 `Content-Type` **charset 分号后空格**残差（`nosniff` 已于 `HttpMiddleware.java:692` 落地 ⇒ **原条自动消失** ⇒ **需重开一条**） | `service/common/observability/http.go:88` ⇒ `http.Error(w, httpTimeoutBody, statusCode)`（Go 标准库 `net/http.Error` 的 5 件事：`text/plain; charset=utf-8` ＋ `nosniff` ＋ 状态码 ＋ 尾换行） | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（超时分支）＋ `http/GoStyleRouterErrorResponses.java` | **L1 源码**（⚠️ 响应头层须**真 Tomcat** 验 ⇒ MockMvc 测不到，见 `Pitfalls/common/http.md`） | 两侧都应是 **Y** = 「**503 的 `Content-Type` = `text/plain; charset=utf-8`（分号后**无**空格）**」⇒ 与 `DRIFT-MSGAPI-010` 同族（那条用户已拍板「保持登记，本轮不做」⇒ **本条要不要做请 G4 明确**） | `service/common` ⇒ 编译面 16 个组件 | 🟡 不阻塞 |
| **5** | `UNRESOLVED-USERSCOMMON-003`（**只派一半**） | `users-common` 的**通用版** `GoStyleCoercion::applyTo` 被 **user-api / admin-api / favorite-api** 三域注册（**favorite 是外域**）⇒ 规则一改，favorite 的解析语义跟着变 ⇒ 「通用版规则内容是否该被外域复用」待定 | go-zero `core/mapping/unmarshaler.go`（`UnmarshalJsonReader` 的 coercion 与必填校验规则）—— ⚠️ **本条 Go 侧行号本轮未核** ⇒ 落码前请回真源逐条核 | `service/users/users-common/src/main/java/com/rag/users/common/http/GoStyleCoercion.java`（**通用版**，G4 写权）· 另 5 域用**本域私有**变体 | **L1 源码**（规则内容）＋ **L3 活体**（本轮实测 8 个 api 域的 `WebMvcConfig` 注册了哪个变体 ⇒ **落点口径已订正**，见 §6.4.1） | 两侧都应是 **Y** = 「**通用版的规则集合 = Go 侧 `UnmarshalJsonReader` 的等价规则**」⇒ 是否允许外域复用，由**规则内容**决定，不由「通用版」这个文件名决定 | `service/users/users-common` ⇒ **被 6 个外域 api 引用** ⇒ 编译面大 | 🟡 不阻塞 |
| **6** | `DRIFT-USERRPC-V-002` | DDL：`users_user.status` 的 **NOT NULL** 两侧不同（Go `status bigint DEFAULT 0` **可空** / RAG `status BIGINT NOT NULL DEFAULT 0`）⇒ RAG 侧任何写 NULL 的代码在 PG 层被拒 | Go `service/user/user/rpc/internal/model/data_model.go:11` ⇒ `Status int64`（值类型 ⇒ GORM 建出**可空**）· ⚠️ **本条 Go 侧行号本轮未核** | `service/users/users-common/src/main/resources/db/01_create_user.sql:39` ⇒ `status BIGINT NOT NULL DEFAULT 0` | **L2 真库实证**（`information_schema` 对照） | 两侧都应是 **Y** = 「**`users_user.status` 可空**」⇒ ⚠️ **改动必须与 admin 域同一次提交**（域台账原文「被卡」） | `service/users/users-common` ⇒ **6 个外域 api 引用** | 🟡 不阻塞（但**须与 users 域同一次提交** ⇒ 属 C3「不甩单」的例外：落点真在共享层） |
| **7** | `DRIFT-USERAPI-014` 的**跨域推广** | `RedisDisconnectedBehaviorConfig`（Redis 断连 ⇒ Lettuce 排队 ⇒ 撞 3s 预算 ⇒ 503；Go 是 200 fail-open）**只装在 `user-api`** ⇒ 其余 api 服务**有同款漂移** | `service/common/middleware/checkblacklistmiddleware.go:35-36`（Go 侧 Redis 断连 ⇒ fail-open 200）· ⚠️ **本条 Go 侧行号本轮未核** | `service/users/user/user-api/**`（**域侧**，G1 可改）⇒ 但**同款机制应在共享层统一** ⇒ 落点 `service/common` | **L3 双侧活体**（域台账 2026-10-24 第九轮：真进程+真 PG+真 Redis ⇒ 已修 `DRIFT-USERAPI-014` 本体） | 两侧都应是 **Y** = 「**Redis 断连 ⇒ fail-open（不排队等重连）**」⇒ **配置上移到 `service/common`** ⇒ 一次修，全域同向 | `service/common` ⇒ 编译面 16 个组件 | 🟡 不阻塞 |

> ⚠️ **派单纪律**：① **缺项不进排期**（本表 7 项齐了才够）② **编号必须带全前缀**（`UNRESOLVED-ARPC-002` ≠ `DRIFT-ARPC-002`）③
> **同族 ≠ 同一条**（#1 的两条编号不同）④ **落点必须先核准**（本表已核准：#5 是**拆两半后的一半**）⑤
> **回执要求 4 项**：`git rev-parse HEAD` + 改动文件清单（逐个 `文件:行号`）+ 触及哪几条 A 轴 ＋ 对应 Go 出处 + 回归口径与结果（模块数/用例数/failures/errors/skipped/耗时）⇒ **只给 hash ⇒ G1 只能盲 merge**。

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）
- [ ] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数**
- [ ] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二的跨域桶）
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）

---

## 7. 🆕 **第 0 步真启动抓到的两条域内真缺陷**（2026-10-27 · G1 lane）

> ⚠️ **这两条是「跑绿测试永远抓不到、只有真启动才暴露」的类型**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）。
> ⇒ **落点全在本域写权内**（`service/users/{user,admin}/**`）⇒ **域侧能自修 ⇒ 不派 G4**。
> ⇒ **编号**：本 lane 自立 `DRIFT-G1-001` / `DRIFT-G1-002`（**G1 波次编号段**，避免与各域既有编号撞号）。

### 7.0 第 0 步的执行口径（先记，避免「跑绿」被误读）

| 项 | 值 |
|---|---|
| 工作树 | `align/g1-users-follow` @ `e8b428f`（基线 `a282012`）· **组件全部由本工作树 `mvn package` 产出**（不碰 `Sea-user-api` 容器里的旧镜像） |
| 隔离 | PG `userprobe-postgres:56432` 上的**自建库 `g1probe`**（不碰 `go_db`）· Redis `userprobe-redis:57379` · Nacos `nacos-standalone:8848` 的**自建 namespace `g1probe`**（不碰 `public` / `sea-rtw`）· 端口全在 5xxxx 段（`59210`/`58081`/`58082`/`59900`/`58889`/`58891`）—— **不碰 `8081`/`8889`/`8082`（有别人的进程）** |
| **A 组（不设 `NACOS_GROUP`）** | ✅ **6/6 组件全部真启动**（user-rpc 7.52s · admin-rpc 7.13s · follow-rpc 6.26s · user-api 6.66s · admin-api 6.81s · follow-api 6.62s）· **0 解析失败** |
| **B 组（设 `NACOS_GROUP=G1_GROUP`）** | ⚠️ 3 个 rpc 起得来但**注册到写死的 group** · `admin-api` **进程退出** · `user-api`/`follow-api` **起得来但运行时解析不到** |
| 建表 | users 域走 `SchemaInitializer`（`users_user`/`users_user_avatar_history`/`admin_admin`/`admin_invite`）· follow 域走 Flyway（`flyway_schema_history_follow`）⇒ **同库共存、账本独立** ✅ |
| 🆕 **A8 活体证据（新增）** | `flyway_schema_history_follow` 里 **`installed_rank=1 / version=0 / << Flyway Baseline >>`** ⇒ 因为 users 域先建了表 ⇒ `public` schema **非空** ⇒ `baseline-on-migrate: true` 触发 ⇒ **baseline 标记打在 version 0** ⇒ **V1/V2 照常执行**（`installed_rank=2/3`）⇒ ✅ **`baseline-version: 0` 这道闸在非空 schema 下真的生效**（AGENTS.md §6.3 问题 4 的残留风险，本轮实测**没踩**） |
| 证据强度 | **RAG 侧 = L3 活体**（真容器 + 真 Nacos + 真 PG + 真 Redis + 真 HTTP 请求）· **Go 侧 = L1 源码行号** ⇒ **两侧未同时活体对拍 ⇒ 不是 L4** |

### 7.1 `DRIFT-G1-001` — 🔴 `NACOS_GROUP` 对三个 rpc 组件**完全无效**（配置项形同虚设）

**现象**：设 `NACOS_GROUP=<自定义>` ⇒ 三个 api 全部解析不到自己的 rpc 下游。

**根因（逐条读消费点，不是读 yml）**：

| 组件 | 位置 | 代码 | 读配置？ |
|---|---|---|---|
| `user-rpc` | `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/bootstrap/NacosRegistration.java:77` | `String groupName = "USER_GROUP";` | ❌ **写死** |
| `admin-rpc` | `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/bootstrap/NacosRegistration.java:62` | `String groupName = "USER_GROUP";` | ❌ **写死** |
| `follow-rpc` | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/bootstrap/NacosRegistration.java:113` | `.registerInstance(SERVICE_NAME, DEFAULT_GROUP, instance)` | ❌ **写死** |
| `user-api` / `admin-api` | 各自 `application.yml` 的 `spring.cloud.nacos.discovery.group` | `group: ${NACOS_GROUP:USER_GROUP}` | ✅ 读配置 |
| `follow-api` | `service/follow/follow-api/src/main/resources/application.yml:42` | `group: ${NACOS_GROUP:DEFAULT_GROUP}` | ✅ 读配置 |

⚠️ **yml 里的占位符看着完全正常** ⇒ **纯读代码 / 读配置都发现不了** ⇒ 只有真启动 + 改 `NACOS_GROUP` 才暴露。

**Go 侧（group/key 可配置）**：
- `service/user/user/rpc/etc/user.yaml:44-47` ⇒ `Etcd: { Hosts: [...], Key: user.rpc }`
- `service/follow/rpc/etc/follow.yaml:4-7` ⇒ `Etcd: { Hosts: [...], Key: follow.rpc }`

⇒ **DRIFT 性质 = 配置面收窄**（Go 可配 / RAG 写死）⇒ **不是纯表达层差异**（改环境变量即断链 ⇒ 行为面漂移）。

**活体 A/B 对照（唯一变量 = `NACOS_GROUP`）**：

| 组 | `NACOS_GROUP` | user-rpc | admin-rpc | follow-rpc | user-api | admin-api | follow-api |
|---|---|---|---|---|---|---|---|
| **A** | 未设置 | ✅ 注册 `USER_GROUP` | ✅ 注册 `USER_GROUP` | ✅ 注册 `DEFAULT_GROUP` | ✅ 6.66s 起 · 解析正常 | ✅ 6.81s 起 · 解析正常 | ✅ 6.62s 起 · 解析正常 |
| **B** | `G1_GROUP` | ✅ 注册 `USER_GROUP`（**无视环境变量**） | ✅ 注册 `USER_GROUP`（**无视**） | ✅ 注册 `DEFAULT_GROUP`（**无视**） | ⚠️ 起得来（6.68s）· **运行时解析不到** | ❌ **进程退出** | ⚠️ 起得来（6.50s）· **运行时解析不到** |

**B 组的两种表现（取决于 api 有没有 `immediate-connect-timeout`）**：
- `admin-api`（**有** `application.yml:120` `immediate-connect-timeout: 3s`）⇒ `No servers found for admin-rpc` ×4 ⇒ **上下文启动失败 ⇒ 进程退出**；
- `user-api` / `follow-api`（**没有**）⇒ **照样启动** ⇒ 首次业务请求才解析失败（见 `DRIFT-G1-002`）。

⚠️ **默认配置下两侧恰好都是 `USER_GROUP` / `DEFAULT_GROUP` ⇒ 一致 ⇒ 不暴露** ⇒ 这就是它一直没被发现的原因。

### 7.2 `DRIFT-G1-002` — 🔴 `user-api` / `follow-api` 缺 `immediate-connect-timeout` ⇒ 与 Go 的 `MustNewClient` panic 语义不一致

**Go 侧（真源逐条核实行号）**：
- `service/user/user/api/internal/svc/servicecontext.go:26` ⇒ `userservice.NewUserService(zrpc.MustNewClient(c.UserRpc))`
- `service/user/admin/api/internal/svc/servicecontext.go:27` ⇒ `adminservice.NewAdminService(zrpc.MustNewClient(c.AdminRpc))`
- `service/follow/api/internal/svc/servicecontext.go:27` ⇒ `followservice.NewFollowService(zrpc.MustNewClient(c.FollowRpc))`
- go-zero v1.10.2 `zrpc/client.go:50-54` ⇒ `logx.Must(err)` ⇒ **失败即 panic** ⇒ **进程起不来**
- go-zero v1.10.2 `zrpc/internal/client.go:18` ⇒ `dialTimeout = time.Second * 3`

⇒ **Go 三个 api 的语义一致**：下游连不上 ⇒ **进程起不来**。

**RAG 侧**：
- ✅ `admin-api` ⇒ `application.yml:120` `immediate-connect-timeout: 3s` ⇒ **已对齐**（`DRIFT-ADMINAPI-009` 已修）
- 🔴 `user-api` ⇒ `grpc.client.user-rpc` **没有** `immediate-connect-timeout`
- 🔴 `follow-api` ⇒ `grpc.client.follow-rpc` **没有**

**活体实证（B 组 · user-api）**：

```
POST /user/v1/login  {"username":"probe","password":"probe123"}
  → HTTP 200  {"code":1015,"msg":"服务繁忙","data":null}
日志：DiscoveryClientNameResolver : No servers found for user-rpc
      ManagedChannelImpl            : Failed to resolve name … UNAVAILABLE
      GoStyleGrpcClientBreakerInterceptor : [grpc-breaker] failure, method=user.UserService/Login, code=UNAVAILABLE
      GlobalExceptionHandler        : unhandled exception      ← 走的是 /login 的另一条路径，渲染成 5001
```

⇒ **RAG user-api = 「起得来 + 每次请求塌成 1015（service busy）」**；**Go = 「起不来（panic）」**
⇒ 🔴 **DRIFT：启动期语义不一致**（一个 fail-fast、一个静默降级）⇒ **落点 = 各域自己的 `application.yml`** ⇒ **域内可修**。
⇒ ⚠️ 这个形态**正是 `DRIFT-FOLLOW-004` 台账原文描述过的「所有业务码塌成 1015（service busy）」** ⇒ 本条是它的**新变体**（不是同一个成因，但**可观察面一模一样**）。

### 7.3 两条缺陷的**关系**：同一条链上的两个断点，且互相放大

| 只修哪条 | 后果 |
|---|---|
| 只修 `DRIFT-G1-002`（加 `immediate-connect-timeout`） | ⚠️ **B 组会从「起来但全失败」变成「三个 api 全部起不来」** ⇒ 运维改 `NACOS_GROUP` 时，故障面从**静默降级**扩大成**全面不可用** |
| 只修 `DRIFT-G1-001`（rpc 侧读配置） | 两侧 group 一致 ⇒ `DRIFT-G1-002` 的**暴露面归零**（但 Go 语义仍不一致，只是更难触发） |
| **两条同批修** | ✅ 默认配置下行为零变化；`NACOS_GROUP` 可配；Go 的 fail-fast 语义对齐 |

⇒ ⇒ **必须同一次提交**（否则留下「一半改完」的半截状态）。

### 7.4 元教训：**「配置项存在」≠「配置项被消费」**

⚠️ `DRIFT-FOLLOW-004`（`server-addr` 硬编码 `:8848` ⇒ 忽略 `NACOS_PORT`）**已于 2026-10-24 修复** ⇒ 那一轮审的是
「`NACOS_ADDR` / `NACOS_PORT` 的**变量语义**」这一维 ⇒ ⚠️ **「`NACOS_GROUP` 是否真被消费」这一维没人查过** ⇒

⇒ 这是 AGENTS.md §3.3.1 的**反向形态**：不是「配置里没写却生效了」，而是「**配置里写了、yml 看着正常，但代码根本不读**」。
⇒ **审 Nacos / 配置面时，必须逐个变量问「谁在消费它、消费点在哪个文件的哪一行」** ⇒ **yml 占位符齐不齐不能作为证据**。

### 7.5 修复口径建议（**阶段 2 落码时用；本轮阶段 1 只读 ⇒ 不落码**）

| 项 | 建议修法 | 落点（G1 写权内） | 必需的回归网 |
|---|---|---|---|
| `DRIFT-G1-001` | rpc 侧 `NacosRegistration` 的 group **改为从 `spring.cloud.nacos.discovery.group` 读**，默认值保持现状（`USER_GROUP` / `DEFAULT_GROUP`）⇒ **默认行为零变化** | `service/users/user/user-rpc/.../NacosRegistration.java`<br>`service/users/admin/admin-rpc/.../NacosRegistration.java`<br>`service/follow/follow-rpc/.../NacosRegistration.java` | **真启动**守门：改 `NACOS_GROUP` ⇒ rpc 注册到新 group 且 api 能解析 ⇒ **MockMvc / 纯单测测不到**（§3.3） |
| `DRIFT-G1-002` | 给 `user-api` 的 `grpc.client.user-rpc` / `follow-api` 的 `grpc.client.follow-rpc` 加 `immediate-connect-timeout: 3s`（与 `admin-api` 同款） | `service/users/user/user-api/src/main/resources/application.yml`<br>`service/follow/follow-api/src/main/resources/application.yml` | **真启动**守门：下游不可达 ⇒ **进程退出**（`exp-admin-startup-failfast.sh` 同款，user / follow 各一条） |

⚠️ 两处落点都在 G1 写权内 ⇒ **不派 G4** ⇒ 走**阶段 2（域波 G1）**落码。
⚠️ 阶段 2 落码后 ⇒ **重跑收口回归**（C7：收口回归跑完 ～ 合并主干之前，共享层变了就重跑）。

---

## 8. 🆕 **双侧同时跑的探针**（2026-10-27 · G1 lane 实跑）

> ⚠️ **口径**：AGENTS.md §3.3 —— 「凡写『与 Go 一致』，必须有**两侧同时跑**的探针（不是只跑 RAG）」。
> ⚠️ 本轮实跑 2 套，**两侧都是真跑**（Go 半边跑冻结真源 / RAG 半边跑生产类）⇒ **不是只跑 RAG**。

### 8.1 `tools/semantic-probe/run-parity.sh` ⇒ ✅ **EXIT=0**

```
总计 56 例：一致 54，不一致 0，已签字偏离 2
```

| 项 | 值 |
|---|---|
| Go 半边 | **冻结真源** `Sea-RideTheWind_golang/service/message/api/internal/types/types.go`（`go/main.go` 的 struct 逐字段对齐） |
| RAG 半边 | **生产类** `com.rag.message.message.api.dto.*` ＋ `com.rag.message.message.api.config.*` ＋ **`com.rag.users.common.http.*`** |
| 已签字偏离 2 条 | `SD-2026-10-23-03`（出参 `affected`：Go 输出数字 / RAG 按签字结论输出字符串）—— `X1` / `X2` |
| ⚠️ **覆盖面边界（对本 lane 的诚实定位）** | ① 覆盖的是 **message 域**的 DTO（`conversation_id` / `target_id` / `broadcast` / `kind` / `extra` / `unread_only`）**＋ `users-common` 的必填校验与 coercion** ⇒ 对 G1 是**间接旁证**（users-common 侧）<br>② ⚠️ **`users/user` 与 `follow` 自己的 DTO 解析语义**（users-common 的**通用版** `GoStyleCoercion` 在 user-api / admin-api / follow-api 上的实际行为）**本轮仍无双侧活体对拍** ⇒ ⇒ **A4 / A6 / A7 在 G1 侧仍是「台账既有证据强度」，不是本轮新证** |
| 🟡 **文档过期（不在 G1 写权 ⇒ 仅报备 G4）** | `tools/semantic-probe/README.md` 写「用例清单（**23 例**）」，脚本本轮实跑 **56 例** ⇒ README 的用例数已过期 ⇒ `tools/` 不在 G1 白名单 ⇒ 已随派单调剂报备 G4 |

### 8.2 `tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ ✅ **EXIT=0**

```
✅ client_deadline_ctx    一致
✅ timeout_wire           一致
⚠️  timeout_telemetry     预期差异（不计入失败）= C-1 表述差异
✅ PASS —— 3 个用例两侧一致
```

| 项 | 值 |
|---|---|
| Go 半边 | 冻结真源 `service/common/observability` ＋ **真 gRPC server**（grpc-go） |
| RAG 半边 | **生产类** `RpcInterceptor` ＋ 真 Netty server ＋ 真 channel（`dns:///127.0.0.1:<port>` 直连，绕开 Nacos） |
| `timeout_telemetry` 已知差异 | Go `codes.Code.String()` = `DeadlineExceeded`（驼峰）vs RAG grpc-java `Code.name()` = `DEADLINE_EXCEEDED`（全大写）⇒ **已登记的表述差异 C-1**（`docs/Intentional_Deviations/common/_signed-deviations.md`）⇒ 不计入失败但始终打印 |
| ⭐ **对本 lane 的直接价值** | **`client_deadline_ctx` 正是 E38② 第①层的验证用例** ⇒ **① 层已闭环** ⇒ 直接改写 §1.1 / §4 #3 / §6.4 的状态（见 §8.3） |

### 8.3 🆕 本轮探针**推翻了我自己的一条派单**（教训记在这里）

⚠️ 我第一版派单把 `UNRESOLVED-ARPC-002`（E38②）写成「RAG 只算 `now+timeout` ⇒ 客户端 deadline 完全不参与」⇒
**那是域台账的过期快照**（同一份台账的「E38 第①层影响面记账」段已记「本组件结论：无变化、无可观察差异」）⇒
**本轮探针 + 读 `RpcInterceptor.java:234/263/592-601` 证实 ① 层已落地** ⇒ **已向 G4 发订正**（`session send`，queued）。

⇒ **元教训（比 §7.4 更强的一层）**：我这次**做了「落点」核准、却漏了「状态」核准** ⇒
⇒ **派单前必须同时核「落点」与「状态」；而「状态」的校准器是探针** ⇒ **凡有对应探针的条目，落码前先跑探针复核状态**（探针几十秒，比读 200 行台账更快更准）。

⚠️ **风险已经发生**（所以这条必须记）：若 G4 按错误描述去改 `RpcInterceptor` 的 deadline 计算 ⇒ **会把已经对的改错 ⇒ 制造新漂移** ⇒
⇒ 这正是 AGENTS.md §3.2 说的「**无出处 idiomatization**」的同族：**基于过期快照的改动 = 无出处改动**。

---

## 6. 🆕 **G1 ⇄ G4 配合范围**（常驻 · 2026-10-27 02:4x 记）

> ⚠️ 本节 = **操作口径**（谁写哪 · 怎么派 · 派单长什么样）⇒ **不替代** AGENTS.md §5.3d·§5.3e 与
> [`cross-domain-coupling.md`](../_cross/cross-domain-coupling.md) §三 **C1~C7**（那两份是规范，本节是落地版）。

### 6.1 写权边界（机器守门可证：`tools/difftest/check-wave-lane-ownership.py` ⇒ EXIT=0）

| 面 | G1（`align/g1-users-follow`） | G4（`align/g4-service-common`） |
|---|---|---|
| `service/users/{user,admin}/**`（api/rpc/proto） | ✅ **可写** | 🔴 只读 |
| `service/follow/**`（api/rpc/common/proto） | ✅ **可写** | 🔴 只读 |
| `service/common/**` | 🔴 **只读** | ✅ **可写（唯一）** |
| `service/users/users-common/**` | 🔴 **只读** | ✅ **可写（唯一）** |
| `docs/DRIFT/users/**` · `docs/DRIFT/follow/**` | ✅ 可写 | 🔴 只读 |
| `docs/DRIFT/global/vs-common.md` | 🔴 只读 | ✅ 可写 |
| `AGENTS.md` · 三个 `_INDEX.md` · `_MANIFEST.json` · `docs/DRIFT/_cross/**` · `docs/Worktree.md` · `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` | 🔴 **无人拥有** | 🔴 **无人拥有** ⇒ **合并后由整合者统一更新** |

> ⚠️ **`users-common` 的物理位置在 `service/users/` 下面，但写权归 G4** —— §5.3f：**共享判据 = 依赖度数，不是目录位置** ⇒
> 「目录在我名下」≠「我能写」。
>
> ✅ **裁定：`users-common` 整库归 G4（2026-10-27 Jason）** —— 守门 `tools/difftest/check-wave-lane-ownership.py`
> 的排他清单把 `service/users/users-common/src/**` **整库**判给 G4 ⇒ **这个口径是对的** ⇒
> §5.3f 那句「3 个 users 专用类（`ErrorCode` · `PasswordUtil` · `SchemaInitializer`）留 users 域」
> **在本面上不适用** ⇒ 🔴 **G1 一律不改 `users-common` 里的任何类（含这 3 个）** ⇒ 要改就**走派单**。
> ⚠️ 该裁定**尚未回写 `AGENTS.md` §5.3f**（共享面无人拥有 ＋ 余量 82 B ⇒ 域侧不许写）
> ⇒ **待整合者合并时统一订正** ⇒ 在那之前，**以守门口径为准**。

### 6.2 G1 → G4：派单（**反馈单 7 项**，缺项不进排期）

**触发**：G1 对账中根因落在 `service/common/**` 或 `service/users/users-common/**`。
**不触发**：域侧能自修的（**本域的调用方式 / 配置 / 断言 / 装配**）⇒ G1 自修、**不甩单**（C3）。

| # | 反馈单项 | 要求 |
|---|---|---|
| 1 | 现象 | 可复现的观察，不是结论 |
| 2 | Go 侧 `文件:行号` | 指向**消费点**，不是声明点（AGENTS.md §3.3.1） |
| 3 | RAG 侧现状 | `文件:行号` + 现状描述 |
| 4 | 证据强度 | **L1 源码级 / L3 活体对拍 / L4 真容器** ⇒ **只跑 RAG 的探针不算数** |
| 5 | 建议修法 | 口径写成「**两侧都应是 Y**」（不是「RAG 改成 X」） |
| 6 | 影响面 | 跨几个域 / 哪 16 个组件依赖它 |
| 7 | 是否阻塞本波 | 阻塞 / 不阻塞 |

**落点 = 双轨**（G1 与 G4 合并前**互相看不见对方的文件** ⇒ 实时通道只能是会话消息）：

1. **实时**：`session send` 给 G4 会话；
2. **正式**：按各自写权写台账 —— G1 写**本域** `docs/DRIFT/users/vs-common.md`（本节 §4）＋
   [`docs/DRIFT/follow/vs-common.md`](../follow/vs-common.md)；G4 写 `docs/DRIFT/global/vs-common.md`
   ⇒ **合并后整合者才看得见对方写了什么**。

**⚠️ 编号纪律（派单必须带全前缀）**：`UNRESOLVED-ARPC-002`（= E38②）**≠** `DRIFT-ARPC-002`
（`UpdateUserResp.success` 的 wire 差异，已修）⇒ 只写「ARPC-002」会让 G4 **销错账**。

### 6.3 G4 → G1：共享层变更回流

G4 落码 ⇒ 共享层行为变 ⇒ **G1 这域的 A1~A9 结论要复核**（重点：A1 超时/deadline · A3 HTTP 保护链 · A4 错误码/envelope）。
**C7 唯一风险窗口 = 收口回归跑完 ～ 合并进主干之前** ⇒ 该窗口内共享层变了 ⇒ **重跑一次收口回归**
（域波记录写明收口回归对应的 `git rev-parse HEAD`）。

### 6.4 G1 待转派 G4 的条目（**落点已按 §6.1 逐条核准** · 状态带核对日期）

| 编号 | 摘要 | 落点 | 归属 | 状态（2026-10-27 核） |
|---|---|---|---|---|
| `DRIFT-USERAPI-013` | 过载保护：MaxConns 闸门粒度 + breaker 键空间 + 注释与实现矛盾 | `service/common`（`GoStyleMaxConnsFilter` / breaker） | 🔴 **G4** | 🔴 待拍板 · **与 `DRIFT-FOLLOW-008` 同族 ⇒ 两条一起对账** |
| `DRIFT-ARPC-010`（= **E66**） | Redis 写不绑 deadline（admin `logout` 的 Redis 写） | `service/common`（Redis 调用路径） | 🔴 **G4** | 🔴 OPEN（两侧同编号 ⇒ 销账别当两条） |
| `UNRESOLVED-ARPC-002`（= **E38②**） | 超时是否 min 客户端 deadline | `service/common`（`RpcInterceptor` 的 `effectiveTimeoutMs`） | ✅ **① 已闭环**（2026-10-26 落地）· ⏸ **② 未做**（架构级，已另立项）⇒ **本轮不派落码** | ✅ ① 双侧活体一致（探针 `client_deadline_ctx`）· ⏸ ② 等立项 |
| `UNRESOLVED-USERSCOMMON-003` | `GoStyleCoercion` 规则是否波及非 users 域 | 🟡 **拆两半**（见 §6.4.1） | 🟡 **域侧 + G4 各一半** | 🔴 未核 · **口径已订正**（2026-10-27） |
| `DRIFT-ADMINAPI-007` | 503 响应 `Content-Type` charset 分号后空格残差 | `service/common` | 🔴 **G4** | 🟡 半修（`nosniff` 已落地 ⇒ 原条消失）⇒ **需重开一条** |

> ⚠️ **状态带核对日期**（AGENTS.md §6）⇒ 复核后就地更新 + 改日期。

#### 6.4.1 🆕 `UNRESOLVED-USERSCOMMON-003` 的口径订正（2026-10-27 实测）

**台账原措辞**：「`http/GoStyleCoercion` 的**全局副作用**是否波及非 users 域（通过 `JsonMapperBuilderCustomizer` 全局施加）」⇒ 落点 `service/common`。

**实测**（8 个 api 域逐个读自己的 `WebMvcConfig`）：

| 域 | 注册的 customizer | 变体在哪 |
|---|---|---|
| `user-api` | `GoStyleCoercion::applyTo` | `users-common`（**通用版**） |
| `admin-api` | `GoStyleCoercion::applyTo` | `users-common`（**通用版**） |
| `favorite-api` | `GoStyleCoercion::applyTo` | `users-common`（**通用版** · ⚠️ **外域直接复用**） |
| `article-api` | `ArticleGoStyleCoercion::applyTo` | 本域私有 |
| `comment-api` | `CommentGoStyleCoercion::applyTo` | 本域私有 |
| `follow-api` | `FollowGoStyleCoercion::applyTo` | 本域私有 |
| `like-api` | `LikeGoStyleCoercion::applyTo` | 本域私有 |
| `message-api` | `MessageGoStyleCoercion::applyTo` | 本域私有 |

⇒ **订正两点**：

1. **不是「全局施加」** —— `JsonMapperBuilderCustomizer` 是 **Spring 容器级 Bean**（**进程内**作用域）⇒
   各域是**独立 Spring Boot 进程** ⇒ **一个域注册它，不可能波及另一个域的进程** ⇒ 原措辞的「全局副作用」**机制上不成立**；
2. **真正跨域的是「类复用」** —— `users-common` 的**通用版** `GoStyleCoercion` 被 **user-api / admin-api / favorite-api**
   三个域注册，其中 **favorite 是外域**（它复用的正是 users 的通用版 ⇒ 规则一改，favorite 的解析语义跟着变）。

⇒ **落点因此拆两半**（这就是「域侧能自修的自修」的典型，别整条甩出去）：

| 半边 | 落点 | 归属 | 动作 |
|---|---|---|---|
| ① **哪个域注册哪个变体**（含 favorite 复用 users 通用版要不要改） | 各域自己的 `service/<域>/<域>-api/**/WebMvcConfig.java` | ✅ **域侧** | 各域自己决定（C3） |
| ② **通用版 `GoStyleCoercion::applyTo` 的规则内容** | `service/users/users-common/**` | 🔴 **G4** | 派单 |

⇒ ⚠️ **「验证跨域」的那部分**（各打一次带标量的请求矩阵）**G1 登记 + 协调 G2/G3**，
但**别把它整条塞进派单** —— 矩阵是域侧行为，落点不在共享层。
