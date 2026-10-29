<!-- @new 2026-10-27 · `users-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G1 波次工作树（`align/g1-users-follow`）** -->

# `users-common` lane 台账 —— 「users ↔ `service/common`」跨层对账

> 🟥 **当前态指针（2026-10-29 · R4·T-4.2（G-12）回填）** —— 本文件按「轮次」追加，**历史各轮一条未删**（快照价值）。
> ⚠️ **头部一度严重过期**（头部「0. lane 身份」表停在 2026-10-27，正文已到 **2026-10-29 的开工轮**）⇒ **当前状态一律以这两节为准**：「70.5 域内收敛结论（**最终**）」＋「71. 🟢 **开工轮（2026-10-29 03:45 · Jason 开工批准）**：§8.7 的 **23 条**已修 ＋ **§69 结论的限定订正**（2026-10-29 · G1 lane）」＋「**72. 🟢 DDL 逐域机器核（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1**（`users` 域：**EXIT=1 · 16 处残留**（可空性 1 · DEFAULT 8 · 索引 7）· **源码↔活库 0 差异** · 变异精确转红）」；末节「2026-10-29 指针：`users-common` 组件的活体证据**已落仓**（Round-2 T-2.6 / G-4）」只是**指针** ⇒ 逐条证据在 [`common.md`](common.md)。
> ⇒ 本文件里的 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。
> **本轮只回填头部口径；正文各轮的原始记录一条未删。**

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
| 🟡 `DRIFT-ADMINAPI-007` | 503 超时响应缺 `X-Content-Type-Options: nosniff` | `service/common` | ✅ **已闭合**（2026-10-28 · G1 lane）—— `nosniff` **已落地**（`HttpMiddleware.java:728`）**且已合入我树** ⇒ **真实 Tomcat L4 实测：503 响应确实带 `nosniff`** ⇒ 原条自动消失；**charset 分号后空格残差仍在** ⇒ 需重开一条（见下一行） | [`admin/api.md`](admin/api.md) §14 |
| `DRIFT-ARPC-006` | OTel `audit.*` span 属性（`operator_id` 不恒为 0） | `service/common`（otel 埋点） | ✅ **已修** | [`admin/_shared.md`](admin/_shared.md) |
| ✅ `UNRESOLVED-USERSCOMMON-003` | `http/GoStyleCoercion` 的**全局副作用**是否波及非 users 域（通过 `JsonMapperBuilderCustomizer` 全局施加） | ~~`service/common`~~ ⇒ **落点原就写错**（类在 `users-common`，且**不是** `@Configuration`） | ✅ **已关闭（2026-10-27 · G1 lane）—— 问法订正 ⇒ 前提不成立 ⇒ 无漂移**（`GoStyleCoercion` **刻意不做 `@Configuration`** ⇒ 各 api 各自注册 customizer ⇒ **进程内作用域** ⇒ 「全局副作用波及外域」**机制上不存在**；未注册的 hot-api / task-api **不是漏**）⇒ 逐条证据见 [`common.md` 的 2026-10-27 订正块](common.md) | [`common.md`](common.md) |
| `DRIFT-USERRPC-V-007` | 超时 description 逐字一致（`context deadline exceeded` vs `rpc timeout after 2000ms`） | `service/common` | ✅ **已合回主干 `4f839f9`** ⇒ 双侧活体复跑一致 | [`user/api.md`](user/api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

### 1.1 §1 抽取快照的**逐条状态复核**（2026-10-27 · G1 lane）

| 编号 | 快照状态 | **复核结论（核对日期 2026-10-27）** | 复核依据 / 强度 |
|---|---|---|---|
| `UNRESOLVED-USERSCOMMON-003` | 🔴 未核（落点 `service/common`） | ✅ **已关闭（2026-10-27 · G1 lane）—— 问法订正 ⇒ 前提不成立 ⇒ 无漂移**（`GoStyleCoercion` **刻意不做 `@Configuration`** ⇒ 各 api 各自注册 ⇒ 进程内作用域 ⇒ 无跨域全局副作用；且 hot-api / task-api 未注册**不是漏**）⇒ **不再需要各外域打请求矩阵** | **L1 静态对照**（逐域清点注册方 ＝ 8 个 api 域 ＋ 读 `GoStyleCoercion` javadoc ＋ grep Go 全仓 `UnmarshalJSON` 仅 1 处）⇒ 见 [`users/common.md` 的 2026-10-27 订正块](common.md) |
| `DRIFT-USERAPI-013` | 🟡 **拆半** | 🟡 **2026-10-29 订正：① MaxConns 闸门粒度 = ✅ 已签字 ⇒ 不修**（`SD-2026-10-25-01`，2026-10-25 用户签字 ⇒ 决策 = 保留 RAG 全局闸门）⇒ **不得再进落码范围**；**② breaker 键空间 ＋ ③ `GoStyleBreakerFilter` javadoc 的错误安全保证 = 🔴 仍开放 ⇒ 维持派 G4**（键语义已于 2026-10-26 只读复核钉死 · **可观察面在 `article-api` ⇒ user-api 本组件不可观察**） | ① 签字档 `docs/Intentional_Deviations/global/common-tails.md` 的 `SD-2026-10-25-01`（L1 决策记录）· ②③ 域台账原文（L1） |
| `DRIFT-ARPC-010`（= E66） | 🔴 OPEN | 🔴 **维持 OPEN**（本轮**未复核落点**）⇒ 派单时仍须核 Go 侧 `文件:行号` | 本轮未查 |
| `UNRESOLVED-ARPC-002`（= E38②） | 🔴 未闭合 | ✅ **口径订正（2026-10-27）＝「① 已闭环 ② 未做（已另立项）」** ⇒ **本轮不需要落码**：① 纯 deadline 计算**已落地**（`RpcInterceptor.java:234` 的 `Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）⇒ 剩下**第②层**（grpc-java `ServerCall#close()` **不取消业务线程** ⇒ 在途 SQL 不随客户端 deadline 取消 ⇒ 与 T-14 同根 ⇒ 架构级 ⇒ Jason 2026-10-26 拍板「**另立项**」）⇒ ⚠️ **「① 不销本条」的正解是「① 已做、② 在等立项」⇒ 不是「① 没做」** | **L3 双侧活体**（`tools/semantic-probe/rpc-timeout/` ⇒ EXIT=0 · `client_deadline_ctx` 一致 · 3/3 一致）+ L1 源码（`RpcInterceptor.java:234/263/592-601`） |
| `DRIFT-ADMINAPI-007` | ✅ **已闭合** | ✅ **已闭合**（2026-10-28 · G1 lane）—— `nosniff` 已落地于 `HttpMiddleware.java:728`（**行号已订正**：原写 692，是被共享层后续提交挪走的**过期行号**）**且已合入我树** ⇒ **真实 Tomcat L4 实测：503 响应确实带 `nosniff`** ⇒ 原条自动消失；**charset 分号后空格残差仍在** ⇒ **需重开一条**（见上一节第 4 行） | **L4**（真实 Tomcat + 真 HTTP 客户端 · wire 实测 · 三条变异各红各的）⇒ 见 [`admin/api.md`](admin/api.md) §14 |
| `DRIFT-USERRPC-V-007` | ✅ 已合回 `4f839f9` | ✅ **维持**（本轮**未重跑**双侧活体 ⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」） | 域台账原文（L3，2026-10-26） |

⚠️ **本轮复核范围声明（诚实边界）**：§1 表里 **只有 `UNRESOLVED-USERSCOMMON-003` 被本轮实测订正并关闭**
（问法订正 ⇒ 前提不成立 ⇒ 无漂移 ⇒ **不再是待办**，见上表）；
其余 5 条**状态未变**（其中 3 条**本轮未复核**）⇒ 按 AGENTS.md §6 记「状态未变」，
**派单时仍须按反馈单 7 项重新核 Go 侧 `文件:行号`**（台账的「抽取快照」**不算数**）。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

> ⚠️ **口径声明（2026-10-27 · G1 lane 复核）**：本轮 = **第 0 步真启动** ＋ **台账状态逐条复核** ＋ **两条新缺陷的活体坐实**。
> ⚠️ **A1~A9 的逐轴「双侧活体对拍」本轮尚未重跑** ⇒ 下表的 ✅ 一律读作「**在台账既有证据强度下一致**」，
> **不是「本轮重新证明了等价」**（AGENTS.md §0：「已改」≠「已验证」；「源码级对照」≠「双侧活体对拍」）。
> ⚠️ **「证据强度」列 = 台账原有强度**（本轮复核沿用），**不是本轮新证的强度**。

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ① `DRIFT-USERAPI-009②`（**3s 预算作用域**：Go 不覆盖 JWT 验签，RAG 覆盖验签+黑名单+handler）**✅ 已修（2026-10-27 · G1 lane · 域内自闭环）** ② `DRIFT-USERAPI-015`（Redis 主机名解析失败 ⇒ 200/503 摇摆）✅ **已关闭（2026-10-27 · G1 lane）** —— 关闭判据 = **复现失败**（dns 形态 24 次探测两侧都 200；blackhole 形态两侧都 503 ⇒ 同形）＋ **无可执行修法**（候选②实测制造**反向差异** ⇒ 否决）⇒ ⚠️ **关闭 ≠ 证明了等价**（保留重开触发条件）③ `DRIFT-USERRPC-V-012`（超时业务日志归因 ⇒ 落点在本域 `UserDomainService.logout`）**✅ 已修（2026-10-27 · G1 lane · 域内一行）** ④ `DRIFT-USERRPC-V-013`（DB 超时的源码层形态差异 ⇒ **非 wire 漂移，不得改回 Go 行为**）⑤ ✅ **`DRIFT-ADMINAPI-006` 的 ② 半已修**（**本轮 · 2026-10-27 · admin-api 推广 `DRIFT-USERAPI-009②`**：验签搬进 `admin-api` 的 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`）⇒ 移出 3s 预算 ⇒ M-37 由 OBSERVABLE DIFFERENCE 改判 EQUIVALENT ⇒ **L3 真 Tomcat 复验**；⚠️ **① 半（遥测面）仍未动**）⑥ ✅ **`DRIFT-USERAPI-016` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：`user-api` 缺 `spring.data.redis.connect-timeout` ⇒ Lettuce 落**自己的默认 10s**（🔴 **原写「回落到 3s」—— 机制说错**，Boot 4 在该属性为 null 时**整段跳过** ⇒ 不回落 timeout；见 `user/api.md` 订正块）≠ Go 的 `DialTimeout` 5s ⇒ 已补 `connect-timeout: 5s` ＋ 新增 `UserApiRedisTimeoutConfigTest` 2 例；⚠️ **与 ② 是同族但不是同一条** —— ② 是「DNS 解析失败的 200/503 摇摆（取舍题，建议不改）」，⑥ 是「超时**配置值**没抄对（照抄题）」）⑦ ✅ **`DRIFT-ADMINAPI-012` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：Lettuce 默认 `disconnectedBehavior=DEFAULT` ⇒ **Redis 断连时命令被排队**等重连 ⇒ 挂到 `commandTimeout: 3s` ⇒ **撞 HTTP 3s 预算 ⇒ 503**；Go 是 `MaxRetries=3` 的**快速失败** ⇒ **fail-open 200** ⇒ 落**域内副本** `config/RedisDisconnectedBehaviorConfig`（`REJECT_COMMANDS`）＋ 守门 `AdminApiRedisDisconnectedFastFailTest` 2 例；⚠️ **修前实测 3013ms ⇒ 修后 1ms**；🔴 **共享层收编仍归 G4** ⇒ 见 §6.4 表末行）⑧ ✅ **`DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013` 已修**（**本轮 · 2026-10-27 · 新立并自闭环 · rpc 侧同机制不同形态**：`user-rpc` 缺 `connect-timeout` ⇒ 落 Lettuce 默认 **10s** ≠ Go 5s ⇒ 已补；**两个 rpc 组件都缺 `REJECT_COMMANDS`** ⇒ 断连时命令**排队**挂到 `commandTimeout: 3s` ≫ 本组件 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 **1015「服务繁忙」**，而 Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」` ⇒ 落**域内副本** `config/RedisDisconnectedBehaviorConfig`（user-rpc ＋ admin-rpc）＋ 守门 2 例各；🟢 **证据强度（2026-10-28 · G1 lane · 两轮订正，第二次是本轮）= 【双侧 L4】**—— 第一次订正（§48.1）只把 Go 腿升到 L4，RAG 腿的「客户端可见响应」仍是 L1 ⇒ 那次**诚实口径**是「机制面双侧 L4 ＋ 响应面 Go 腿 L4 / RAG 腿 L1」；⇒ ⇒ **本轮（§50）把 RAG 腿那一格也补成 wire 级实测**：Go `Internal`/1689ms（admin 域另有自己的 Go 腿 **1680ms**）· **RAG A 组 `INTERNAL`/19ms**（user）· **RAG A 组 `INTERNAL`/8ms**（admin）· **RAG B 组 `DEADLINE_EXCEEDED`(1015)/2013ms（user）· /2010ms（admin）** ⇒ ⇒ ⇒ ⇒ **客户端可见响应面也是双侧 L4**；且 **B 组是同场受控反事实** ⇒ ⇒ **机制归因也升 L4**（原先按 L3）⇒ ⇒ ⇒ **本条现在可以笼统写 L4** ⇒ ⇒ 逐条 ⇒ [`§50`](#50-🔴🟢-l4-缺口闭合rag-腿的客户端可见-grpc-响应升-l4--同场-ab-反事实对照2026-10-28--g1-lane) ｜ ⚠️ **口径提醒**：**「1015 vs INTERNAL」的分歧点已闭合** ⇒ **没有 `REJECT_COMMANDS` ⇒ 客户端就是 1015**⇒ ⇒ 这条修复**不是偏好**，是「客户端能不能拿到与 Go 一样的 status」的**前提**）） | 🔴 **1 条 OPEN**（④ 非漂移；② 已关闭）<br>⚠️ **2026-10-27 订正**：原状态写「4 条 OPEN（其中 **②③** 域内可闭环）」⇒ **编号错挂**（② 是 `USERAPI-015`、③ 是 `V-012`）⇒ 域内可闭环的是 **①③**；**② 已于本轮正式关闭**<br>🆕 **命令超时的「机制腿」已补**（**本轮 · 2026-10-27 · 探针实测**）：`TimeoutOptions.source` 恒为 `DefaultTimeoutSource`（返回 -1）⇒ 3s 只能由 **`applyConnectionTimeout`** 分支从连接级 timeout（`LettuceConnectionFactory.getTimeout()=3000ms` ⇒ `RedisURI.timeout`）取 ⇒ ⇒ **`isTimeoutCommands()` 恒真、不是有效判据** ⇒ 只断言「值」是**假绿口子**（机制被关 ⇒ 值仍读得到 3s、命令却永不过期）⇒ 5 份 FastFail 守门已各补**机制腿**（详见 [`user/rpc.md` §4.1.1](user/rpc.md)） | L1 源码 + **L2 运行时（新增）** + **Go 腿 L4 活体·直接判据（⑤）+ RAG 腿 L3 真 Tomcat·间接判据 ⇒ 合并按弱侧记 L3**（Go 腿探针见 §8.4；⚠️ 不笼统写 L4） + **L2 配置层守门（⑥）** + **L3 真容器活体（⑦）** + **L3 真容器活体（⑧）** + **本轮两腿同口径活体（⑧ 的机制面 L4 · 客户端可见响应面 Go 腿 L4 / RAG 腿 L1 ⇒ §48.1）** + **L3 探针实测（命令超时机制腿）** + L3 双侧活体（台账原有） | 2026-10-27（**本轮订正 ⑤⑥⑦⑧ ＋ 关闭 ②**）· **2026-10-28 追加（⑧ 的两腿同口径实测 ⇒ §48.1）** |
| **A2** | gRPC 拦截器链 | `DRIFT-USERAPI-011`（客户端熔断 handler）✅ · `DRIFT-USERRPC-V-007`（超时 description 逐字）✅ 已合回 `4f839f9` · ① 三拦截器链（recover/breaker/shedding）已全处置 · ② 🟡 **A2 邻接（出向调用面，非 A2 本体）**：G4 逐个 rpc 用 `@GrpcClient` 计数 ＋ 六种出向方式交叉核 ⇒ **`follow-rpc` = 1（可观测）· `user-rpc` = 0 · `admin-rpc` = 0（N/A）** ⇒ **不并进 A2 本体的差异计数**（逐条见 §16.3） | ✅ **一致**（在该证据强度下）· **A2 本体的 OBSERVABLE DIFFERENCE 计数 = 0** | L3 双侧活体 | 2026-10-27 |
| **A3** | HTTP 保护链 | ① 本域 `application.yml` 的 `rag.http.go-form-limit.mode` = **raw**（user-api ＋ admin-api 各一行）② `DRIFT-USERAPI-008`（过载保护「不存在」）**已证伪** ③ `DRIFT-USERAPI-012`（降载 RT 口径）✅ ④ `DRIFT-USERAPI-013`（**MaxConns 粒度 + breaker 键空间 + javadoc 矛盾**）🔴 ⑤ `009② 补充`（MaxBytes 相对 observability 的位置）**未签字** ⑥ ✅ **验签与保护链的相对次序已对齐**（**本轮 · 2026-10-27**：`user-api` / `admin-api` 两域的验签各搬进各自的 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`）⇒ 落在 `+3 Traceparent` 之内、`+7 HttpMiddleware（3s 预算）` 之外 ⇒ **Filter 绝不写响应** ⇒ **413 仍压过 401**（真 Tomcat 实测：user/admin 两域 413 + 空 body）；⚠️ **各域 401 body 形状刻意不统一**（user/admin = 完整信封；follow = 空 body））⑦ ❌ **`hot-api` 对本条 N/A**（**2026-10-27 由 G3 指出 · 本域已独立核实**：Go 侧 `service/hot/api/internal/handler/routes.go` **零 `rest.WithJwt`**、单块 `AddRoutes`、唯一端点 `GET /hot/v1/articles`；RAG 侧 `service/hot/hot-api` **无 `WebMvcConfig`、无任何 `*Jwt*`/`*Auth*` 类** ⇒ **两侧都没有验签环节** ⇒ 热榜本就该匿名读，**「RAG 没有鉴权」不是漂移**） | 🔴 **1 条 G4** ＋ 🟡 **1 条待拍板** ＋ 🔴 **1 条域内**（009② 补充项） | L1 源码 + **L2 装配层守门（容器装配结果）** + **L3 真 Tomcat 活体（⑥）** | 2026-10-27 |
| **A4** | 错误码 / envelope | `DRIFT-USERAPI-003/-006/-007/-010` ✅（后两条为**签字偏离** `SD-USERAPI-001` 与 N-1）· `UNRESOLVED-001/003/004` **全部已关**（`UNRESOLVED-001` = 真 Tomcat 双侧 + `check-plaintext400-headers.sh` EXIT=0） | ✅ **一致**（**4 条签字偏离**覆盖 ⇒ 审查见红先查签字档） | L3 真 Tomcat + 双侧活体 | 2026-10-27 |
| **A5** | 端口治理 | ① api 监听 8888 → **49900**（`OBSERVABLE DIFFERENCE`，已签字 `Infrastructure.md §3`）② rpc gRPC `0.0.0.0:9004` → **49210**（`IMPLEMENTATION DIFFERENCE`，已锁定）③ ⚠️ **`RANDOM_PORT` 面的记账缺口（2026-10-27 补记）** —— **实测清点**（非印象）：<br>· 全仓 `RANDOM_PORT` 命中 **21 个文件**（`service/` 全树）；<br>· **G1 三域合计只有 1 处**：`user-api` 的 `GoStyleGrpcClientBreakerE2ETest`（`webEnvironment = RANDOM_PORT`，但那是 **gRPC 熔断 E2E**，**不是 HTTP 响应头面**）；<br>· `admin-api` / `follow-api` 域内 **零** `RANDOM_PORT`、**零** `DEFINED_PORT`；<br>· ⚠️ 域内的**响应头层**验证（`AdminApiPlainText400HeaderProbeTest` 等）走的是 **`webEnvironment = MOCK` ＋ `@AutoConfigureMockMvc`** ⇒ **MockMvc 测不到真 Tomcat 的 header 逐字节** ⇒ 真 Tomcat 面由**仓外脚本** `tools/difftest/check-plaintext400-headers.sh` 覆盖（**不在域内台账的证据面**） | 🟡 **`RANDOM_PORT` 面仍是记账缺口**（**不是「已对齐」**）—— 本轮把「零覆盖」从**空白**变成**有出处的记录**：缺口在**哪几个文件**、**为什么 MockMvc 不算**、**真 Tomcat 面实际由谁覆盖** 三点已钉死 ⇒ **可被下一轮按图索骥** | L1 静态（`grep` 全树清点 ＋ 逐个测试类的 `webEnvironment` 实读） | 2026-10-27（**本轮补记**） |
| **A6** | errmsg 契约 | code→msg 全表 **30 项 EQUIVALENT**（Go `errmsg.go:38-68` ↔ `users-common.ErrorCode`）· N-2（Go 侧 9 处 GBK 双重编码乱码）✅ 签字 · ✅ **`DRIFT-USERSCOMMON-001`（1017/1018 文案）本轮已销号** —— **两侧均不可观察 ⇒ 本条不成立**（Go 侧 1017/1018 是**零调用点的死码**；RAG 侧 user-rpc 三处**仅日志**、admin-api 两处**在 HTTP 入口不可达** ⇒ **1017/1018 都进不了 wire**）· ✅ **`003`（claim 键序）判为不修**（条目自身定性为「**表述差异，不是漂移**」）· ✅ **`005`（bcrypt `$1$`/`$2x$`）已签字** `SD-USERSCOMMON-001` ⇒ ⇒ **§3 的 5 条全部闭合 ⇒ 没有任何一条「真正的语义漂移」还开着** | ✅ **一致**（**该证据强度下**）—— 域台账 [`common.md`](common.md) §7 的最终判定**本轮由 `SEMANTICALLY NOT EQUIVALENT` 升为 `SEMANTICALLY EQUIVALENT`**（依据 = 本仓成例 `comment/common.md`：「`SEMANTICALLY EQUIVALENT` ＋ OBSERVABLE DIFFERENCE 条目」并存 ⇒ **判定口径 = 有没有真开着的语义漂移，不是台账里还有几条条目**）· ⚠️ **3 条取证缺口**（§6：`002` 的 `$1$`/`$2x$` 实际数据面 · `004` 的 DDL 逐列等价性 + `ADVISORY_LOCK_KEY` 并发 · `005` 的 JWT claim > 2^53）**是「还不知道」，不是「已断言不一致」** ⇒ **复开触发条件 = 任一条被取证** | **L4**（双侧矩阵 273 条 · `NEW_DRIFT=0` 反证）＋ **L2**（MockMvc 三条守门）＋ **L1**（Go 侧零调用点 ＋ RAG 侧 DTO 类型/绑定层/必填校验三层拦截） | 2026-10-27（**本轮：`001` 销号 · 总判定升为 `SEMANTICALLY EQUIVALENT`**） |
| **A7** | proto 契约 | 两侧 `.proto` 剥离 option/注释/空行后**逐行 diff 零差异** ＋ 常驻守门 `check-proto-static.sh` ＋ `UserInfo.status` 的 `optional` 两侧同 | ✅ **一致** | L1 实测 diff | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | ① `DRIFT-USERRPC-V-002`（`users_user.status` **可空性**）🟡 **2026-10-27 订正：DDL 层差异 · 行为层零差异** ⇒ **建议保持 RAG NOT NULL**（Go 读 NULL ⇒ **0** 不报错；RAG 读不到 NULL ⇒ 也是 0 ⇒ 可观察结果相同）⇒ ⚠️ **未销号**（须与 G4 共同确认）⇒ ⚠️ **原写「改动须与 admin 域同改」是错的**：那会**制造**新漂移（RAG 读出 null ≠ Go 的 0）⇒ 逐层证据链见 [`user/rpc.md` 的 V-002 订正块](user/rpc.md) ② `DRIFT-USERRPC-V-011`（时间列 `DEFAULT CURRENT_TIMESTAMP`）⚠️ **未修**（8 处，刻意保留）③ `V-003`（`extra_info` text↔jsonb）✅ `V-004`（4 个时间列 timestamptz）✅ `V-006`（id 取值来源）✅ ④ 表名 `users`→`users_user` ✅ 签字 ⑤ 建表责任交叉 EQUIVALENT（**双侧对称并发启动 9 轮**） | 🔴 **1 条**（V-011 待裁决）＋ 🟡 **1 条**（V-002 · DDL 层 · 行为零差异） | L4 真 PG（并发 9 轮）＋ **L1 源码逐层复核**（GORM `field.go:616-622` ＋ `database/sql/convert.go:431-435`，本轮独立复核） | 2026-10-27（**本轮订正 V-002**） |
| **A9** | trace / otel | ① `DRIFT-USERAPI-009①`（401 是否计入观测面）✅ 签字 `SD-USERAPI-002`（**只覆盖 ①**）② `DRIFT-USERRPC-V-012`（业务日志归因）**✅ 已修（2026-10-27 · G1 lane · 域内一行）** —— 仅「原始异常被整个吞掉」闭环；②③ 架构级另立项 ⇒ 本条不销 ③ **`Traceparent` 传播：域台账零条目**（原写「全 users 域零覆盖」—— **本轮补了对照记录，见下方 A9 补记**）④ **`audit.*` span 属性：admin 域已覆盖**（`DRIFT-ARPC-006` ✅ 已修 ＋ `admin/_shared.md` N-4 关联 ＋ 守门 `AdminSpanWiringTest`）⇒ **user 域（user-api / user-rpc）零覆盖**（**本轮已核：Go 侧 user 域也零 ⇒ 不是缺口**）⑤ 共享层本体（otel 装配 + 传播器）**已由 global lane L1 闭合**（`GoStyleOtelAutoConfiguration` 走 `AutoConfiguration.imports` ⇒ 不依赖组件扫描 ⇒ rpc 进程也装） | 🟡 **两条「零覆盖」本轮已重新定性**（**不是空白轴，是「两侧都无 ⇒ 无漂移」**）—— 详见下方 A9 补记 | L1 静态（**本轮补：Go 侧 `audit.*` / `otel.` 逐域清点 ＋ RAG 侧 span 属性逐条对读**）＋ 共享层台账（`common/audit.md` / `global/lanes/L1-delivery.md`） | 2026-10-27（**本轮补记 + 订正**） |

> **🆕 A9 补记（2026-10-27 · G1 lane · 把「零覆盖」从空白变成有出处的对照记录）**
>
> ⚠️ **原措辞的问题**：③ 写「`Traceparent` 传播：全 users 域零覆盖」、④ 写「user 域 `audit.*` 零覆盖」
> ⇒ 这两条**只说了「本域台账里没有」**，**没说「两侧实际是什么」** ⇒ 读者无法判断是**记账缺口**还是**真漂移**。
> ⇒ 本轮**回真源逐条清点**，结论如下（**L1 静态对照，不是活体对拍**）：
>
> | 面 | Go（真源，`grep -rn "audit\." service/<域> --include=*.go` / `grep -rn "otel\."`） | RAG | 判定 |
> |---|---|---|---|
> | **`audit.*` 属性（user 域）** | `service/user` 共 **13** 处，**全部在 `service/user/admin/rpc/internal/logic/`**（`audit.operator_id` / `audit.target_uid` / `audit.admin_username`）⇒ **`service/user/user/rpc` 零** | `user-api` / `user-rpc` 侧**零** `audit.*` | ✅ **两侧都无 ⇒ 无漂移**（admin 域的那 13 处已由 `DRIFT-ARPC-006` 覆盖且 ✅ 已修） |
> | **span 名 + `biz.username`（user 域）** | `service/user/user/rpc/internal/logic/`：`loginlogic.go:38-42` `otel.Tracer("user-rpc")` ＋ `tracer.Start(ctx, "Action-User-Login")` ＋ `span.SetAttributes(attribute.String("biz.username", in.Username))`；另有 `registerlogic.go:39` / `deleteuserlogic.go:34` | `UserDomainService.java:175-176`（`Action-User-Login` ＋ `biz.username`）· `:289-290`（`Action-User-Register` ＋ `biz.username`）· `:631`（`Action-User-Delete`） | ✅ **逐条对上**（span 名 ＋ 属性名） |
> | **`Traceparent` 传播** | Go 的 `service/common/observability`（= RAG `service/common` 的对应物）**零 `traceparent` 命中**；真正的 W3C 传播器（`otel.SetTextMapPropagator` + `Extract` + `Inject`）在 **`service/common/communityfact`**（`logging.go:97` · `authority_http.go:30` · `wire.go:104`）—— 那是**社区事实投递链路的另一个库**，**users / follow 域不走它** ⇒ **本链路两侧都没有「入站 traceparent 提取」** | `service/common/http/GoStyleTraceparentFilter`（`@Order(+3)`）把 W3C `traceparent` **写进响应头**（共享层 T-8 条目） | 🟡 **响应头回写是 RAG 侧多出来的**，但**落点在共享层**（`service/common` ⇒ **G4**）⇒ **不是本域漂移**；本域侧的结论 =「两侧都无入站提取」 |
>
> ⇒ **本轮 A9 的净结论**：③ 与 ④ **不是「空白轴」**，而是**「两侧都无 ⇒ 无漂移」**（只有 `Traceparent` 响应头回写是共享层多出来的，已在共享层单独留痕）。
> ⇒ ⚠️ **证据边界**：以上是 **L1 静态对照**（grep 清点 ＋ 源码逐条对读）⇒ **不是活体对拍**（没起进程看真实 span 属性）⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」。

> **A3 已预填一条实测锚点（2026-10-27 核）** —— CD-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
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
| **1** | `DRIFT-USERAPI-013`（**②③ 半边**；① MaxConns 半边 **✅ 已签字 ⇒ 不修** ⇒ 已撤出落码范围）＋ `DRIFT-FOLLOW-008`（**✅ 2026-10-29 已销号** —— 与 ① 同一条） | ① ~~**MaxConns 闸门粒度**：Go **每路由一个** latch（RAG = 全局一个）~~ ⇒ **✅ 已签字 `SD-2026-10-25-01` ⇒ 不修**（**撤出落码范围**）<br>**② breaker 键空间**：Go key = 注册期 `route.Path` **模板**（基数 = 路由数）vs RAG = raw `getRequestURI()`（基数 = 参数取值数 ⇒ 无界）<br>**③ `GoStyleBreakerFilter` 的 javadoc 给「无界风险为零」的错误安全保证** | ① `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12`（`func MaxConnsHandler(n int) func(http.Handler) http.Handler`）—— **已核，但已签字不修**<br>② `go-zero@v1.10.2/rest/engine.go:149` → `go-zero@v1.10.2/rest/handler/breakerhandler.go:15,19`（`breakerSeparator="://"`）<br>③ `go-zero@v1.10.2/rest/config.go:52` | ① `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java`（全局单闸门 —— **签字保留，不动**）<br>②③ `GoStyleBreakerFilter.java`（raw URI 建 key）· 11 个 `protection/` 类 | **① L1 决策记录**（签字档 `SD-2026-10-25-01`）<br>**②③ L1 源码逐行对照**（域台账 2026-10-26 只读复核：键语义已钉死 · **可观察面在 `article-api`**，user-api 的 9 条路由零路径变量 ⇒ 本组件不可观察） | **②③** 两侧都应是 **Y** = 「**breaker key 用注册期 `route.Path` 模板**」＋「**404 取不到模板 ⇒ 不得用 raw URI 兜底**」（这是方案 B 的硬约束）<br>① **不适用**（决策 = 保留 RAG 全局闸门） | `service/common` 的 `protection/`（11 类）⇒ **编译面 16 个组件** | 🟡 **不阻塞** |
| **2** | `DRIFT-ARPC-010`（= **E66**，两侧同编号 ⇒ **销账别当两条**） | Redis **写**不绑 deadline（admin `logout` 的 Redis 写）⇒ 超时后写仍在途 | `service/common/observability/rpc.go:38`（`context.WithTimeout(ctx, timeout)` ⇒ ctx 带 deadline ⇒ **出向调用天然继承**） | `service/common` 的 Redis 调用路径 **未绑 `GoStyleRequestDeadline`** ⇒ 只算服务端自己的预算 | **L1 源码**（域台账原文）+ 与 `UNRESOLVED-ARPC-002` 同型 | 两侧都应是 **Y** = 「**Redis 写出向调用绑定请求 deadline**」⇒ 与 gRPC 出向（E65）**同一套绑定机制** | `service/common` ⇒ 编译面 16 个组件 | 🟡 不阻塞 |
| **3** | `UNRESOLVED-ARPC-002`（= **E38②**） ⚠️ **2026-10-27 订正：此条本轮不需要落码** | 超时是否 **min(客户端 deadline, 服务端 timeout)**：Go 继承父 ctx ⇒ 客户端更早就提前取消。**RAG 侧已于 2026-10-26 落地该语义** ⇒ **① 已闭环** | `service/common/observability/rpc.go:38` ⇒ `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)`<br>**已核 ✅**：该行内容逐字 = `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` | ✅ **已落地**：`RpcInterceptor.java:234` ⇒ `Math.min(serverBudgetMs, clientDeadlineRemainingMs())`；`:263` 调度器用 `effectiveTimeoutMs`；`:592-601` 从 `io.grpc.Context.current().getDeadline()` 取 | **L3 双侧活体**（`tools/semantic-probe/rpc-timeout/` **本轮实跑 EXIT=0**：`client_deadline_ctx` **一致** · `timeout_wire` 一致 · 3/3 一致） | 两侧都应是 **Y** = 「**有效期限 = min(客户端 deadline, 服务端 timeout)**」⇒ ✅ **这个 Y 已经达成** ⇒ ⛔ **请勿改 `RpcInterceptor` 的 deadline 计算**（会把对的改错 ⇒ 制造新漂移）<br>剩下**第②层**（`ServerCall#close()` 不取消业务线程 ⇒ 架构级 ⇒ Jason 2026-10-26 已拍板「**另立项**」）⇒ **不是本轮落码项** | `service/common` ⇒ 编译面 16 个组件 | 🟢 **不阻塞**（**本轮不落码**） |
| **4** | `DRIFT-ADMINAPI-007` | 503 超时响应的 `Content-Type` **charset 分号后空格**残差（`nosniff` 已于 `HttpMiddleware.java:728` 落地 ⇒ **原条自动消失** ⇒ **需重开一条**） | `service/common/observability/http.go:88` ⇒ `http.Error(w, httpTimeoutBody, statusCode)`（Go 标准库 `net/http.Error` 的 5 件事：`text/plain; charset=utf-8` ＋ `nosniff` ＋ 状态码 ＋ 尾换行） | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（超时分支）＋ `http/GoStyleRouterErrorResponses.java` | **L4**（✅ **2026-10-28 升级**：真实 Tomcat + 真 HTTP 客户端，wire 实测 `Content-Type: text/plain;charset=utf-8` ⇒ **确认空格残差确实存在** ⇒ 归「框架限制」；⚠️ 响应头层须**真 Tomcat** 验 ⇒ MockMvc 测不到，见 `Pitfalls/common/http.md`） | 两侧都应是 **Y** = 「**503 的 `Content-Type` = `text/plain; charset=utf-8`（分号后**无**空格）**」⇒ 与 `DRIFT-MSGAPI-010` 同族（那条用户已拍板「保持登记，本轮不做」⇒ **本条要不要做请 G4 明确**） | `service/common` ⇒ 编译面 16 个组件 | 🟡 不阻塞 |
| **5** | `UNRESOLVED-USERSCOMMON-003`（**只派一半**） | `users-common` 的**通用版** `GoStyleCoercion::applyTo` 被 **user-api / admin-api / favorite-api** 三域注册（**favorite 是外域**）⇒ 规则一改，favorite 的解析语义跟着变 ⇒ 「通用版规则内容是否该被外域复用」待定 | go-zero `core/mapping/unmarshaler.go`（`UnmarshalJsonReader` 的 coercion 与必填校验规则）—— ⚠️ **本条 Go 侧行号本轮未核** ⇒ 落码前请回真源逐条核 | `service/users/users-common/src/main/java/com/rag/users/common/http/GoStyleCoercion.java`（**通用版**，G4 写权）· 另 5 域用**本域私有**变体 | **L1 源码**（规则内容）＋ **L3 活体**（本轮实测 8 个 api 域的 `WebMvcConfig` 注册了哪个变体 ⇒ **落点口径已订正**，见 §6.4.1） | 两侧都应是 **Y** = 「**通用版的规则集合 = Go 侧 `UnmarshalJsonReader` 的等价规则**」⇒ 是否允许外域复用，由**规则内容**决定，不由「通用版」这个文件名决定 | `service/users/users-common` ⇒ **被 6 个外域 api 引用** ⇒ 编译面大 | 🟡 不阻塞 |
| **6** | `DRIFT-USERRPC-V-002` | DDL：`users_user.status` 的 **NOT NULL** 两侧不同（Go `status bigint DEFAULT 0` **可空** / RAG `status BIGINT NOT NULL DEFAULT 0`）⇒ RAG 侧任何写 NULL 的代码在 PG 层被拒 | Go `service/user/user/rpc/internal/model/data_model.go:11` ⇒ `Status int64 \`gorm:"column:status;default:0"\``（**值类型 · 非指针**）⇒ ✅ **2026-10-27 13:2x 已核到该行**（原「本条 Go 侧行号本轮未核」的标记**撤销**）⇒ ⇒ **更强的推论**：GORM 对**值类型**字段写库时**恒写 0**（不会写 NULL）⇒ ⇒ **Go 侧这一列实际上永远不出现 NULL** | `service/users/users-common/src/main/resources/db/01_create_user.sql:39` ⇒ `status BIGINT NOT NULL DEFAULT 0` | **L2 真库实证**（`information_schema` 对照）<br>＋ **L1 逐层复核**（2026-10-27 · GORM `field.go:616-622` ＋ `database/sql/convert.go:431-435`） | ⛔ **2026-10-27 订正：原建议修法已被本域逐层证据链推翻 ⇒ 本单不需要 G4 落码。**<br>**原写**「两侧都应是 Y =「`users_user.status` **可空**」」⇒ **错的** ⇒ **放开可空会制造新漂移**（RAG 侧 `callSettersOnNulls=false` ⇒ NULL 时跳过 setter ⇒ 读出 **null** ≠ Go 的 **0** ⇒ 方向相反）。<br>⇒ **正确口径 = 两侧都应是 Y =「维持 RAG NOT NULL」**（Go 读 NULL ⇒ **0** 且不报错；RAG 读不到 NULL ⇒ 也是 0 ⇒ **可观察结果相同**）⇒ 本条定性为「**DDL 层差异 · 行为层零差异**」⇒ 落点 `users-common` **无动作**。<br>⇒ 剩下只差「**销号签字**」，而 `docs/Intentional_Deviations/**` **不在 G1 写权面** ⇒ **请 G4 / 合并收口执行者确认销号口径**；逐层证据链见 [`user/rpc.md` 的 V-002 订正块](user/rpc.md)<br>🔴 **2026-10-27 13:2x · G4 回执 ⑧ 说「等 G1 的 #6 第 1 步（让 `admin-rpc` 的 `UserEntity.status` 能表达 NULL）」⇒ ⇒ 本条已由本域订正 ⇒ ⇒ 「第 1 步」【取消】⇒ ⇒ **G4 不需要为本单落 DDL 那步**（落 DDL = 把 RAG 改成可空 = 制造上面那条新漂移）⇒ ⇒ **落点只剩签字档** | `service/users/users-common` ⇒ **6 个外域 api 引用**（但 **本单落点无动作 ⇒ 实际影响面 = 0**） | 🟡 不阻塞（**原写「须与 users 域同一次提交」已作废** ⇒ 无需任何组件改动） |
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
- [ ] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由合并收口执行者统一更新）

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

> ✅ **状态：阶段 2 已修（2026-10-27）** ⇒ 修法与证据见 §7.5 / §7.6 / §7.7。**本节保留修前的原始取证。**

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

> ✅ **状态：阶段 2 已修（2026-10-27）** ⇒ 修法与证据见 §7.5 / §7.6 / §7.7。**本节保留修前的原始取证。**

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

### 7.5 修复口径（**阶段 2 已落码** · 2026-10-27）

| 项 | 落地的修法 | 落点（G1 写权内） | 配套的回归网 |
|---|---|---|---|
| `DRIFT-G1-001` | rpc 侧 `NacosRegistration` 的 group **改为从 `spring.cloud.nacos.discovery.group` 读**，默认值保持现状（`USER_GROUP` / `DEFAULT_GROUP`）⇒ **默认行为零变化** | `service/users/user/user-rpc/.../NacosRegistration.java`<br>`service/users/admin/admin-rpc/.../NacosRegistration.java`<br>`service/follow/follow-rpc/.../NacosRegistration.java` | ✅ `UserRpcNacosGroupTest` / `AdminRpcNacosGroupTest` / `FollowRpcNacosGroupTest`（**各 3~4 例**）＋ ✅ **真启动 A/B 组**（§7.6） |
| `DRIFT-G1-002` | 给 `user-api` 的 `grpc.client.user-rpc` / `follow-api` 的 `grpc.client.follow-rpc` 加 `immediate-connect-timeout: 3s`（与 `admin-api` 同款）＋ 两个 client 类加 `afterPropertiesSet()` 自检（机制被静默关掉 ⇒ 启动即失败） | `service/users/user/user-api/src/main/resources/application.yml` + `.../client/UserRpcClient.java`<br>`service/follow/follow-api/src/main/resources/application.yml` + `.../client/FollowRpcClient.java` | ✅ `UserApiStartupFailFastTest` / `FollowApiStartupFailFastTest`（**各 6 例**）＋ ✅ **真启动 C 组**（§7.6） |

⚠️ 两处落点都在 G1 写权内 ⇒ **不派 G4** ⇒ 走**阶段 2（域波 G1）**落码。
⚠️ 阶段 2 落码后 ⇒ **重跑收口回归**（C7：收口回归跑完 ～ 合并主干之前，共享层变了就重跑）。

### 7.6 ✅ **落码后的真启动 A/B/C 对照**（2026-10-27 · 同一套隔离环境）

> 环境与 §7 相同（真 PG `g1probe` · 真 Redis · 真 Nacos namespace `g1probe` · 端口全在 5xxxx 段）⇒ **唯一变量 = `NACOS_GROUP` 与「下游是否在跑」**。

| 组 | 变量 | user-rpc | admin-rpc | follow-rpc | user-api | admin-api | follow-api | 结论 |
|---|---|---|---|---|---|---|---|---|
| **A** | 不设 `NACOS_GROUP` | 6.152s<br>`USER_GROUP` | 6.245s<br>`USER_GROUP` | 5.619s<br>`DEFAULT_GROUP` | 6.783s | 6.927s | 6.794s | ✅ **6/6 起得来 · 0 解析失败** ⇒ **默认行为零变化**（注册的 group 与**修前的写死值逐字相同**） |
| **B** | `NACOS_GROUP=G1_GROUP`（rpc＋api 全跑） | 6.459s<br>**`G1_GROUP`** | 7.403s<br>**`G1_GROUP`** | 6.176s<br>**`G1_GROUP`** | 6.917s | 6.885s | 6.556s | ✅ **6/6 起得来 · 0 解析失败** ⇒ 🔴 **`DRIFT-G1-001` 已修**（修前：rpc 写死 group ⇒ admin-api **进程退出** ＋ user-api/follow-api 运行时塌成 **1015**） |
| **C** | `NACOS_GROUP=G1_GROUP` ＋ **rpc 不起**（下游不可达） | — | — | — | ❌ 未启动<br>**进程退出** | ❌ 未启动<br>**进程退出** | ❌ 未启动<br>**进程退出** | ✅ **三个 api 全部进程退出** ⇒ 🔴 **`DRIFT-G1-002` 已修**（对齐 Go 的 `zrpc.MustNewClient` ⇒ panic ⇒ 起不来） |

> 「注册的 group」= 真进程日志里的原文（`nacos registry, <group>` / `registered: service=… group=…`）⇒ **不是从代码推断的**。

**C 组的失败原文**（真进程，不是单测）：

```
user-api  : Caused by: java.lang.IllegalStateException: Failed to create channel: user-rpc
             Caused by: java.lang.IllegalStateException: Can't connect to channel user-rpc
admin-api : Caused by: java.lang.IllegalStateException: Can't connect to channel admin-rpc
follow-api: Caused by: java.lang.IllegalStateException: Can't connect to channel follow-rpc
```

⚠️ **admin-api 本来就有这个行为**（`DRIFT-ADMINAPI-009` 早已修）⇒ C 组证明的是
**user-api / follow-api 现在与 admin-api、与 Go 三方一致** ⇒ 不是新发明，是**补齐同族**。

### 7.7 守门网（**每条都做过变异验证 ⇒ 确认能变红**）

| 守门类 | 例数 | 变异注入 | 变异后 | 证据 |
|---|---|---|---|---|
| `UserApiStartupFailFastTest` | 6 | 删掉 yml 的 `immediate-connect-timeout` | 🔴 **红在 `applicationYmlArmsImmediateConnectTimeout`** | ✅ 变异确实编译并执行 |
| `UserRpcNacosGroupTest` | 3 | 把 `@Value` 的属性名改掉（读不到配置） | 🔴 **红在 `registrationUsesConfiguredGroup`** | ✅ 变异确实编译并执行 |
| `AdminRpcNacosGroupTest` | 3 | 同款（未单独跑变异，与 user-rpc 同构） | — | ⚠️ **未单独做变异**（同一份代码形状） |
| `FollowRpcNacosGroupTest` | 4 | 同款（未单独跑变异） | — | ⚠️ **未单独做变异** |
| `FollowApiStartupFailFastTest` | 6 | 同款（未单独跑变异） | — | ⚠️ **未单独做变异** |

### 7.8 回归口径（2026-10-27 · 落码后）

| 模块 | 用例数 | failures | errors | skipped |
|---|---|---|---|---|
| `user-api` | 113 | 0 | 0 | 0 |
| `follow-api` | 56 | 0 | 0 | 0 |
| `user-rpc` | 115 | 0 | 0 | 0 |
| `admin-rpc` | 136 | 0 | 0 | 0 |
| `follow-rpc` | 78 | 0 | 0 | 0 |
| **合计（5 个受影响模块）** | **498** | **0** | **0** | **0** |

⚠️ `admin-api` 本轮**未改代码** ⇒ 未重跑（`DRIFT-ADMINAPI-009` 的既有行为不变）。
⚠️ **收口回归尚未跑**：按 C7，收口回归在**波末**跑 ⇒ 届时天然含 G4 至今的共享层改动 ⇒
**唯一风险窗口 = 收口回归跑完 ～ 合并主干之前** ⇒ 该窗口内共享层变了就**重跑一次收口回归**（记 `git rev-parse HEAD`）。

### 7.9 ⚠️ 落码时**撞到并修掉的一处连带回归**（`DRIFT-G1-002` 的副产物）

`UserApiRedisDisconnectedFastFailTest`（Redis 断连快速失败的守门）**上下文起不来了**：
它的 `@SpringBootTest(properties=...)` 把 `grpc.client.user-rpc.address` 指向
`static://127.0.0.1:65535`（**本来就没人监听**）⇒ fail-fast 一旦武装 ⇒ 启动即失败。

⇒ **修法 = 按 admin-api 的既有约定**：给该测试加 `@MockitoBean UserRpcClient`
（`AdminApiPlainText400ContentTypeTest` 早就是同款做法，注释里写着「被 mock 的 bean **不会**执行
`afterPropertiesSet()` ⇒ 启动期检查被跳过」）⇒ **不是**关掉 fail-fast，而是**让这个不关心 gRPC 的测试不碰 gRPC**。

⚠️ **元教训**：**武装一个 fail-fast 会让「故意指向死地址」的既有测试集体变红** ⇒
落码时必须先扫一遍本模块**所有** `@SpringBootTest(properties=...)` 里带 `grpc.client.*.address=static://…` 的用例
⇒ 逐个按域内约定补 `@MockitoBean`，**而不是**把 fail-fast 退回关闭。

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

### 8.4 🆕 **Go 腿 JWT 预算探针**（G2 建 · **本域独立复跑**）⇒ 把 009② 的 Go 侧证据由 L1 升到 **L4**

> **背景**：`DRIFT-USERAPI-009②`（3s 预算**含不含** JWT 验签）的 Go 侧结论原先只是 **L1（逐行读 `bindRoute`）**。
> ⚠️ 但这是**框架级**问题 —— 取决于 `DisableNativeRestTimeout` 有没有被调用 ⇒ **读源码容易读反**（G2 自己就判反过一次）。

| 项 | 内容 |
|---|---|
| 探针工程 | 真源整仓复制到 `/tmp/goprobe`（**不动被冻结的真源**）· `probejwt/main.go` |
| **关键：不是重写版** | 探针 **import 真源自己的 `sea-try-go/service/common/observability`**（`main.go:31`）⇒ 验签与预算中间件都是真源实现 |
| 启动路径 | 与真源 api **逐行同构**（`messagecenter.go:36-42` 同款）：`DisableNativeRestTimeout` → `rest.MustNewServer` → `server.Use(observability.NewHTTPMiddleware(...))` ⇒ 打印 `nativeTimeoutMiddlewareEnabled=**false**` ⇒ **原生 TimeoutHandler 不在链上** ⇒ 3s **只**由真源自己的中间件提供 |
| 三个观测点 | A = 注册在 observability 中间件**之前**（更外层）· B = **之后**（更内层）· HANDLER = 最内层 |
| **本域复跑** | 另编一份二进制（`CGO_ENABLED=0 go build`，**不动 G2 的文件**）+ 自己的端口 **25790** ⇒ 三腿全中：<br>① 有效 token `/fast` ⇒ HTTP **200**；`A deadline_set=**false** claim_userId=12345` · `B deadline_set=**true** remaining=3s`<br>② 有效 token `/slow`（handler 睡 4s）⇒ **HTTP 503 @ 3.002s**（与 Go `http.go:88` 逐字一致）<br>③ **非法 token** ⇒ **HTTP 401 + 空 body（0 字节）**，且 **A/B/HANDLER 一个都没打印** ⇒ 🔴 **决定性反证**：Authorize 在所有 user middleware 之前 ⇒ **验签在 3s 预算之外** |
| ⚠️ **腿③ 才是判据** | 腿①②只是正证；腿③ 是**负证据**（「观测点 A 没打印」⇒ Authorize 更靠外）⇒ **比正证据更硬** |
| 对本 lane 的价值 | `user-api` / `admin-api` / `follow-api` 的 Go 侧结论**同构**（三个 api 的 `xxxcenter.go` 都是 `DisableNativeRestTimeout` + `server.Use(observability.NewHTTPMiddleware(...))`）⇒ ⇒ **Go 侧证据由 L1 升到 L4** ⇒ §2 的 A1 轴（①③⑤）与三份域台账的实施记录（元信息行）同步改写 |
| ⚠️ **诚实边界** | ① **两次独立运行**（Go 腿与 RAG 腿不在同一次编排里）⇒ 比的是**属性**（验签是否计入 3s 预算），**不是逐字节 diff**；② **探针落在 `/tmp`（易失）** ⇒ **落仓 `tools/difftest/` 需合并收口执行者拍板**（`tools/` 不在 G1 白名单）⇒ **已登记待办**；⚠️ **但源码已在本节 §8.4.1 抄了一份保全副本**（域台账在 G1 写权内 ⇒ 不会被 `/tmp` 清空带走）③ 探针的 `Timeout` 走 go-zero struct tag 默认值 3000ms（真源 api 的 yaml 同样不写 `Timeout`）⇒ 口径与真源一致 |

#### 8.4.1 📦 **探针源码保全副本**（2026-10-27 · 防 `/tmp` 一清就没了）

> ⚠️ **为什么抄在这里**：`tools/difftest/` 是**无人拥有 ⇒ 合并收口执行者**（G1 不写）⇒ 在合并收口执行者落仓前，
> 探针**只活在 `/tmp`** ⇒ **易失**（机器重启即没）⇒ **工件有丢失风险**。
> ⇒ 本节把**探针源码原文**抄进**域台账**（`docs/DRIFT/users/**` = **G1 写权内**）⇒ **保住这份工件**，等合并收口执行者落仓时可直接取用。
> ⇒ 原文出自 **G2 建的** `/tmp/goprobe/probejwt/main.go`（**本域只复跑、未改动一个字节**）；下面的副本与 `/tmp` 现存副本**逐字一致**。
>
> **取用方式**（G2 已确认）：**不必整仓复制** —— 直接复用 `/tmp/goprobe`（`go build` 到自己目录 ＋ 自己端口）即可；
> 但**落仓时要解决「怎么让探针仍能 import 真源自己的 `observability`」**（这一版靠**整仓复制**才能 import 到 ⇒ 体积问题就是落仓要处理的那件事）。
> ⇒ **构建要点**：`CGO_ENABLED=0`（否则报 `stdlib.h not found`）＋ 离线依赖用 `GOPROXY=file://$(go env GOMODCACHE)/cache/download`。
>
> **三腿期望输出**（本域在 **25790** 端口复跑实测；G2 侧是 25789）：
> ```
> PROBE-A(outer) deadline_set=false claim_userId=12345
> PROBE-B(inner) deadline_set=true remaining=3s
> ① 有效 token /fast  ⇒ HTTP 200
> ② 有效 token /slow（handler 睡 4s）⇒ HTTP 503 @ 3.002s
> ③ 非法 token ⇒ HTTP 401 + 空 body（0 字节），且 A/B/HANDLER 一个都没打印   ← 决定性反证
> ```

**`main.go` 原文**（G2 建）：

```go
// 探针（忠实版）：go-zero v1.10.2 + 真源自己的 service/common/observability 中间件，
// 实测「3s 预算到底包不包含 JWT 验签」。
//
// 与真源 api 的启动路径逐行同构（参照 service/message/api/messagecenter.go:36-42）：
//     httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)
//     server := rest.MustNewServer(c.RestConf)
//     server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))
//
// 判据（三腿，互补）：
//   观测点 A —— 注册在 observability 中间件**之前**（链上更外层）
//   观测点 B —— 注册在 observability 中间件**之后**（链上更内层）
//   业务 handler —— 最内层
//
//   · A 能看到 claim「userId」 ⇒ handler.Authorize 已经跑过（它把 claim 写进 ctx）
//   · A 看不到 deadline、B 看得到 deadline（remaining≈3s）
//     ⇒ 预算 ctx 是 observability 中间件自己建的（Go: http.go:53 context.WithTimeout）
//     ⇒ 而该中间件在链上位于 Authorize **之后**（go-zero@v1.10.2/rest/engine.go:108 先 appendAuthHandler，
//       :110-112 才 append ng.middlewares；go-zero@v1.10.2/rest/chain/chain.go:38-42 Append = 「追加到请求流最后」）
//     ⇒ ⇒ 验签在 3s 预算**之外**
//
//   · 非法 token ⇒ Authorize 直接 401（authhandler.go:56 → unauthorized()，body 为空）
//     ⇒ 观测点 A/B **一个都不打印** ⇒ 反证 Authorize 位于所有用户中间件**之前**
package main

import (
	"fmt"
	"net/http"
	"os"
	"time"

	"sea-try-go/service/common/observability"

	"github.com/golang-jwt/jwt/v4"
	"github.com/zeromicro/go-zero/core/conf"
	"github.com/zeromicro/go-zero/rest"
	"github.com/zeromicro/go-zero/rest/httpx"
)

const secret = "probe-secret-0123456789"

type probeConfig struct {
	rest.RestConf
}

func main() {
	tok := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"iat":    time.Now().Unix(),
		"exp":    time.Now().Add(time.Hour).Unix(),
		"userId": 12345,
	})
	signed, err := tok.SignedString([]byte(secret))
	if err != nil {
		panic(err)
	}
	fmt.Println("TOKEN=" + signed)

	var c probeConfig
	conf.MustLoad("etc/probe.yaml", &c)

	// —— 与真源 api 逐行同构 ——
	httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)
	fmt.Printf("PROBE conf.Timeout=%dms httpTimeout=%v nativeTimeoutMiddlewareEnabled=%v\n",
		c.Timeout, httpTimeout, c.Middlewares.Timeout)

	server := rest.MustNewServer(c.RestConf)

	// 观测点 A：链上比 observability 中间件更外层
	server.Use(func(next http.HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			_, hasDeadline := r.Context().Deadline()
			fmt.Printf("PROBE-A(outer) path=%s deadline_set=%v claim_userId=%v\n",
				r.URL.Path, hasDeadline, r.Context().Value("userId"))
			next(w, r)
		}
	})

	// 真源自己的预算中间件（原样调用，不改一行）
	server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))

	// 观测点 B：链上比 observability 中间件更内层
	server.Use(func(next http.HandlerFunc) http.HandlerFunc {
		return func(w http.ResponseWriter, r *http.Request) {
			dl, hasDeadline := r.Context().Deadline()
			remaining := "-"
			if hasDeadline {
				remaining = time.Until(dl).Round(time.Millisecond).String()
			}
			fmt.Printf("PROBE-B(inner) path=%s deadline_set=%v remaining=%s claim_userId=%v\n",
				r.URL.Path, hasDeadline, remaining, r.Context().Value("userId"))
			next(w, r)
		}
	})

	server.AddRoutes(
		[]rest.Route{
			{Method: http.MethodGet, Path: "/fast", Handler: func(w http.ResponseWriter, r *http.Request) {
				_, hasDeadline := r.Context().Deadline()
				fmt.Printf("PROBE-HANDLER path=%s deadline_set=%v claim_userId=%v\n",
					r.URL.Path, hasDeadline, r.Context().Value("userId"))
				httpx.OkJsonCtx(r.Context(), w, map[string]string{"ok": "1"})
			}},
			{Method: http.MethodGet, Path: "/slow", Handler: func(w http.ResponseWriter, r *http.Request) {
				fmt.Printf("PROBE-HANDLER path=%s sleeping 4s (> budget)\n", r.URL.Path)
				time.Sleep(4 * time.Second)
				httpx.OkJsonCtx(r.Context(), w, map[string]string{"ok": "1"})
			}},
		},
		rest.WithJwt(secret),
		rest.WithPrefix("/probe"),
	)

	fmt.Println("PROBE listening on 127.0.0.1:25789")
	server.Start()
	os.Exit(0)
}
```

**`etc/probe.yaml` 原文**：

```yaml
Name: jwtbudgetprobe
Host: 127.0.0.1
Port: 25789

Log:
  ServiceName: jwtbudgetprobe
  Mode: console
  Encoding: plain

# ⚠️ 刻意**不写** Timeout ⇒ 走 go-zero struct tag 的默认值 3000ms
#    （go-zero@v1.10.2/rest/config.go:55 `Timeout int64 json:",default=3000"`）
#    —— 与真源 api 的 etc/*.yaml 写法一致（真源也不写 Timeout）。
```

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
| `AGENTS.md` · 三个 `_INDEX.md` · `_MANIFEST.json` · `docs/DRIFT/_cross/**` · `docs/Worktree.md` · `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` | 🔴 **无人拥有** | 🔴 **无人拥有** ⇒ **合并后由合并收口执行者统一更新** |

> ⚠️ **`users-common` 的物理位置在 `service/users/` 下面，但写权归 G4** —— §5.3f：**共享判据 = 依赖度数，不是目录位置** ⇒
> 「目录在我名下」≠「我能写」。
>
> ✅ **裁定：`users-common` 整库归 G4（2026-10-27 Jason）** —— 守门 `tools/difftest/check-wave-lane-ownership.py`
> 的排他清单把 `service/users/users-common/src/**` **整库**判给 G4 ⇒ **这个口径是对的** ⇒
> §5.3f 那句「3 个 users 专用类（`ErrorCode` · `PasswordUtil` · `SchemaInitializer`）留 users 域」
> **在本面上不适用** ⇒ 🔴 **G1 一律不改 `users-common` 里的任何类（含这 3 个）** ⇒ 要改就**走派单**。
> ⚠️ 该裁定**尚未回写 `AGENTS.md` §5.3f**（共享面无人拥有 ＋ 余量 82 B ⇒ 域侧不许写）
> ⇒ **待合并收口执行者统一订正时一并处理** ⇒ 在那之前，**以守门口径为准**。

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
   ⇒ **合并后合并收口执行者才看得见对方写了什么**。

**⚠️ 编号纪律（派单必须带全前缀）**：`UNRESOLVED-ARPC-002`（= E38②）**≠** `DRIFT-ARPC-002`
（`UpdateUserResp.success` 的 wire 差异，已修）⇒ 只写「ARPC-002」会让 G4 **销错账**。

### 6.3 G4 → G1：共享层变更回流

G4 落码 ⇒ 共享层行为变 ⇒ **G1 这域的 A1~A9 结论要复核**（重点：A1 超时/deadline · A3 HTTP 保护链 · A4 错误码/envelope）。
**C7 唯一风险窗口 = 收口回归跑完 ～ 合并进主干之前** ⇒ 该窗口内共享层变了 ⇒ **重跑一次收口回归**
（域波记录写明收口回归对应的 `git rev-parse HEAD`）。

### 6.4 G1 待转派 G4 的条目（**落点已按 §6.1 逐条核准** · 状态带核对日期）

| 编号 | 摘要 | 落点 | 归属 | 状态（2026-10-27 核） |
|---|---|---|---|---|
| `DRIFT-USERAPI-013` | 过载保护：~~MaxConns 闸门粒度~~ ＋ **breaker 键空间** ＋ **注释与实现矛盾** | `service/common`（`GoStyleBreakerFilter`） | **G4**（需落码 · **本列是「归属」列，不是状态列** ⇒ 状态见末列）| 🟡 **2026-10-29 拆半**：**① MaxConns 闸门粒度 = ✅ 已签字 ⇒ 不修**（`SD-2026-10-25-01`）⇒ **撤出落码范围**；**② breaker 键空间 ＋ ③ javadoc 矛盾 = 🔴 仍开放 ⇒ 维持派 G4**<br>⚠️ **销号提醒**：① 与 follow 域 `DRIFT-FOLLOW-008` **是同一条**（同族 ≠ 同一条 ⇒ 编号仍不同）⇒ **follow 侧那条已于 2026-10-29 一并销号** ⇒ **两边都不要再落码** |
| `DRIFT-ARPC-010`（= **E66**） | Redis 写不绑 deadline（admin `logout` 的 Redis 写） | `service/common`（Redis 调用路径） | 🔴 **G4** | 🔴 OPEN（两侧同编号 ⇒ 销账别当两条） |
| `UNRESOLVED-ARPC-002`（= **E38②**） | 超时是否 min 客户端 deadline | `service/common`（`RpcInterceptor` 的 `effectiveTimeoutMs`） | ✅ **① 已闭环**（2026-10-26 落地）· ⏸ **② 未做**（架构级，已另立项）⇒ **本轮不派落码** | ✅ ① 双侧活体一致（探针 `client_deadline_ctx`）· ⏸ ② 等立项 |
| ~~`UNRESOLVED-USERSCOMMON-003`~~ | ~~`GoStyleCoercion` 规则是否波及非 users 域~~ | — | ✅ **已关闭（2026-10-27）** ⇒ **从派单表撤下** | ✅ **问法订正 ⇒ 前提不成立 ⇒ 无漂移**（`GoStyleCoercion` **刻意不做 `@Configuration`** ⇒ 各 api 各自注册 customizer ⇒ **进程内作用域** ⇒ 无跨域全局副作用；hot-api / task-api 未注册**不是漏**）⇒ 逐条证据见 [`users/common.md` 的 2026-10-27 订正块](common.md)<br>⚠️ **原派单拆的两半**：①「哪个域注册哪个变体」= 域侧自修 ⇒ **已结清**（8 个 api 域注册方已清点）· ②「通用版规则内容」= G4 ⇒ **仍开放**，但它**不是本条**（那是规则内容本身，见上方 §5 表第 5 行） |
| `DRIFT-ADMINAPI-007` | ~~503 响应 `Content-Type` charset 分号后空格残差~~ ⇒ 🟢 **2026-10-29 收敛：不另开条目** —— 残差经源码复核**归「框架限制、不改」**：`service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:718-727` 的 `writeTimeoutResponse` 已**明写**「Tomcat 的 `setContentType` 会把 `Content-Type` 解析成 MediaType 再序列化 ⇒ 吃掉分号后的空格 ⇒ **该空格在 Tomcat 上无任何写法能产出** ⇒ 归『框架限制』，不改」⇒ **原条 `DRIFT-ADMINAPI-007`（缺 `nosniff`）已闭合且残差已定性 ⇒ 本行从派单表撤下** | ~~`service/common`~~ ⇒ **无需落码** | 🟢 **无需 G4 落码**（原判为「G4 落码」⇒ **本轮撤销派单**）|
| 🆕 **`DRIFT-USERAPI-009 §7 补充`** | **MaxBytes 相对 observability(3s) 的位置**：Go 在预算**之外**，RAG 在**之内** ⇒ 超限 413 **计入** access log/span/指标（Go 不计） | `service/common`（`GoStyleMaxBytesFilter` 的 `@Order`） | 🔴 **G4**（代码）· 🟡 **合并收口执行者**（签字档） | 🔴 **未签字**（台账建议**并入 `SD-USERAPI-002` 的范围说明** ⇒ 签字比改码便宜）⇒ ⚠️ **见 §6.4.2 的槽位联动警告** |
| 🆕 **Go 腿 JWT 预算探针落仓** | §8.4 的探针（`/tmp/goprobe/probejwt/main.go` · G2 建、本域独立复跑）**只在 `/tmp`** ⇒ 易失 ⇒ 重跑要重新搭一遍 | `tools/difftest/`（**新脚本**） | 🔴 **无人拥有 ⇒ 合并收口执行者**（`tools/difftest/README.md` 在共享面清单里；**新脚本目录级不属任何 lane** ⇒ 见 `check-wave-lane-ownership.py` 的 `SHARED` 列表） | 🔴 **待合并收口执行者拍板**（**G1 不写 `tools/`**）⇒ 建议内容：`probe-jwt-budget/`（Go 工程副本的取用方式 ＋ 三腿期望输出 ＋ 清理口径）⇒ ⚠️ **落仓前必须解决「真源整仓复制到 `/tmp`」的体积问题**（本轮探针靠复制整仓才能 import 真源自己的 `observability`） |
| 🆕 **Redis 断连行为（`REJECT_COMMANDS`）的共享层收编** | **五个**用 Redis 的组件的 Lettuce **断连**行为必须 `REJECT_COMMANDS`（否则命令被排队挂到 `commandTimeout` 3s ⇒ **api 侧**撞 HTTP 3s 预算 ⇒ **503**（Go 是 fail-open **200**）；**rpc 侧**撞 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 **1015**（Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」`））⇒ 修法是把 `LettuceClientOptionsBuilderCustomizer` 设成 `REJECT_COMMANDS` | 🟢 **域侧已自修**（**五份域内副本**：`user-api`（原有）＋ `admin-api` ＋ `follow-api` ＋ **`user-rpc`** ＋ **`admin-rpc`**，后两份是 2026-10-27 本轮补）<br>🔴 **共享层收编** = 把五份并成一个 `service/common` 里的自动配置 | 🟢 **域侧**（已完成）<br>🔴 **G4**（**收编**） | 🟢 **域侧已闭环**（2026-10-27 · `admin-api` 落 `DRIFT-ADMINAPI-012` / `follow-api` 落 `DRIFT-FOLLOW-012` · 修前 3013/3019ms ⇒ 修后 1/0ms · 变异 5/5 变红；**本轮补 `user-rpc`（`DRIFT-USERRPC-V-014` ②）/ `admin-rpc`（`DRIFT-ARPC-013`）** ⇒ 两侧 rpc 口径一致）<br>🟢 **共享层已落码**（G4 commit `1fb26b2` ＋ `2f12236` · `GoStyleRedisClientAutoConfiguration` 走 `AutoConfiguration.imports`）⇒ **待 G4 分支合入本树后，删除这五份域内副本**（`user-api` / `admin-api` / `follow-api` / `user-rpc` / `admin-rpc`）⇒ **删除前置 ＝ 共享层那份先合进各域的树**（否则双份配置 ⇒ 两处都要维护）<br>🔴 **2026-10-27 升级危害等级**（据 G3 提供的信息）：G4 那份 `GoStyleRedisClientAutoConfiguration` 的 customizer 带 **`@ConditionalOnMissingBean`** ⇒ **域内副本在 ⇒ 共享层那份整个不创建** ⇒ ⇒ **不删副本 ⇒ 收编根本没发生**（不是「重复维护」这种小问题），而且**共享层后续任何改动对我域静默不生效** ⇒ ⇒ **删除动作是收编的「必要条件」而非「收尾清理」** ⇒ 收编完成时必须**复验**：域内副本删掉后，**共享层那份是否真的生效**（`REJECT_COMMANDS` 仍在客户端 `ClientOptions` 上）。<br>⚠️ **rpc 侧与 api 侧后果不同**（503 vs RPC 撞超时）⇒ **共享层收编时不要只按 api 侧的现象判「已修」** ⇒ 两侧各自的行为要分别验 |

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

> ✅ **2026-10-27 收口（上面的拆分已被后续核实取代 ⇒ 本条关闭）** —— G1 lane
>
> ⚠️ 本节写于「先拆两半」这一步；**后续核实发现「跨域」这个前提本身不成立**，所以：
> - **半边①（哪个域注册哪个变体）** ⇒ **已结清**：逐域清点出 **8 个已注册 api 域**
>   （`article`/`comment`/`favorite`/`follow`/`like`/`message` 用本域私有变体 ＋ `user-api`/`admin-api` 用 users 通用版）；
> - **「验证跨域」那个待办** ⇒ **不需要做**（问题前提不成立）⇒ **不必再协调 G2/G3 打请求矩阵**；
> - **半边②（通用版规则内容）** ⇒ **仍开放**，但**归 G4**（规则内容本身）⇒ **不再挂在 003 名下**。
>
> ⇒ 逐条证据（`GoStyleCoercion` **刻意不做 `@Configuration`** · hot-api 零 `@RequestBody` · task-api 的 Go 侧带自定义 `UnmarshalJSON` · Go 全仓仅 1 个自定义 `UnmarshalJSON`）
> ⇒ 见 [`users/common.md` 的 2026-10-27 订正块](common.md) 与 §1.1 表。

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）· `UNRESOLVED-USERSCOMMON-003` 的口径订正**
> ⇒ 原判「**已关闭**（2026-10-27 收口：跨域前提不成立，不必协调 G2/G3 打请求矩阵）」⇒ **现行态 = ✅ 已关闭（维持）**。
> - **被描述的事实是否还在**：**仍在** ⇒ **关闭依据仍成立**。
>   **实测（2026-10-29 全仓 grep）** —— `GoStyleCoercion::applyTo` 的注册点**恰好 8 处**，与本节表格逐行一致：
>   | 域 | 注册的 customizer | 现行出处 |
>   |---|---|---|
>   | `user-api` | `GoStyleCoercion::applyTo` | `service/users/user/user-api/src/main/java/com/rag/users/user/api/config/WebMvcConfig.java:48` |
>   | `admin-api` | `GoStyleCoercion::applyTo` | `service/users/admin/admin-api/src/main/java/com/rag/users/admin/api/config/WebMvcConfig.java:52` |
>   | `favorite-api` | `GoStyleCoercion::applyTo`（**外域复用 users 通用版**） | `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/config/WebMvcConfig.java:67` |
>   | `article-api` | `ArticleGoStyleCoercion::applyTo` | `service/article/article-api/src/main/java/com/rag/article/article/api/config/WebMvcConfig.java:71` |
>   | `comment-api` | `CommentGoStyleCoercion::applyTo` | `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/config/WebMvcConfig.java:69` |
>   | `follow-api` | `FollowGoStyleCoercion::applyTo` | `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/config/WebMvcConfig.java:69` |
>   | `like-api` | `LikeGoStyleCoercion::applyTo` | `service/like/like-api/src/main/java/com/rag/like/like/api/config/WebMvcConfig.java:54` |
>   | `message-api` | `MessageGoStyleCoercion::applyTo` | `service/message/message-api/src/main/java/com/rag/message/message/api/config/WebMvcConfig.java:75` |
>   ⇒ **「不是全局施加」（各域是独立进程）与「真正跨域的是类复用（favorite 复用 users 通用版）」这两点都仍成立** ✔
>   ⇒ **「验证跨域」的待办仍然不需要做** ✔（前提不成立）。
> - ⚠️ **效力边界（不变）**：**半边②（通用版 `GoStyleCoercion::applyTo` 的规则内容）仍开放、落点仍在 `service/users/users-common/**` ⇒ 归 G4**；
>   **本域只登记、不落码**。
> - **不改判的理由**：本条断言的是「**跨域前提不成立**」这一**事实**，该事实经 8 个注册点逐条复核**仍成立** ⇒ 状态维持 ✅。

#### 6.4.2 🔴 **槽位联动警告**（给共享层写权方 · 2026-10-27）

> **一句话**：本轮刚在 `user-api` 落了一个**依赖共享层 Filter 槽位**的机制
> ⇒ **共享层若调整 Filter 的 `@Order`，各域必须被通知**，否则域侧白做。

**机制背景**（`DRIFT-USERAPI-009②` 的修法，见 `docs/DRIFT/users/user/api.md` §9.y）：

| `@Order` | Filter | 作用 |
|---|---|---|
| **+2** | 🆕 `GoStylePathNormalizingFilter` | **Go 的 `path.Clean` 路径归一**（G4 `9690b04` · G2 FB-01）⇒ ⚠️ **该 Filter 只包装 request、不短路、不产生响应头** ⇒ **在 3s 预算起点（+7）之前** ⇒ **不进预算** |
| +3 | `GoStyleTraceparentFilter` | 起 SERVER span |
| +4 | `GoStyleMaxConnsFilter` | 连接数闸门 |
| +5 | `GoStyleBreakerFilter` | 熔断 |
| +6 | `GoStyleCpuSheddingFilter` | CPU 降载 |
| **+7** | **`HttpMiddlewareFilter`** | **3s 预算起算点** |
| +8 | `GoStyleMaxBytesFilter` | 请求体上限 |

⚠️ **这张表有「两个代码态」（2026-10-27 13:2x · G4 槽位回执后补记）** —— **别把它当唯一事实**：

| 代码态 | Filter 数 | 槽位 | 3s 预算起点行号 |
|---|---|---|---|
| **我树**（`align/g1-users-follow` @ `544e310` · `service/common` = 主干基线） | **6** | +3 / +4 / +5 / +6 / +7 / +8（**无 +2**） | `HttpMiddleware.java:304`（`begin`）· `:336`（`clear`） |
| **G4 树**（`align/g4-service-common` @ `cd893d1`） | **7** | **+2** / +3 / +4 / +5 / +6 / +7 / +8 | `HttpMiddleware.java:324`（`begin`）· `:356`（`clear`） |

⇒ ⇒ **`GoStylePathNormalizingFilter`（+2）由 G4 的 `9690b04` 引入，而 `9690b04` 不在 `do-my-own-gh` ⇒ 我树里没有这个文件** ⇒ ⇒ **「6 vs 7」的差异是代码态差，不是我漏数**（G4 说我「漏了 1 个」⇒ 指的是**合入主干后我域会面对 7 个**⇒ ⇒ **我域的「重排清单」必须把 +2 一并算进去**）。
⇒ ⇒ ⚠️ **跨树行号会漂（实测差 20 行）** ⇒ 凡引他树 `文件:行号` ⇒ **必须同写该树的 commit id**（`HttpMiddleware.java:324` 只在 G4 的 `cd893d1` 上有效）。
⇒ ⇒ ✅ **G4 的「+7 = 3s 预算起点」成立**（两侧独立核到 `begin` / `clear` **都在 `HttpMiddlewareFilter` 的链内**）⇒ ⇒ **域侧 +6 必须 < +7** 的推导**不受 +2 影响**（+2 在区间外）⇒ ⇒ **我域的推导仍成立**。
⇒ ⇒ ✅ **「`MaxBytes`(+8) 是唯一排在 +7 之后的共享层 Filter」两侧都成立**（+2 在前，其余逐条同值）⇒ ⇒ 这正是「RAG 多记 413」的机制体现 ⇒ ⇒ **要把它挪到 +7 之前，必须同一次提交改：共享层 `@Order` ＋ G4 的槽位快照守门 ＋ 台账登记理由**（否则 G4 的 `SharedFilterOrderSnapshotGateTest` 必红）⇒ ⇒ **签字比改码便宜 ⇒ 优先走签字**（落点 = 签字档 ⇒ **合并收口执行者写权面**）。

⇒ 域侧的 `GoStyleJwtAuthFilter`（user-api）落在 **+6**，靠两条硬约束夹出来：
**必须 < +7**（否则验签仍被 3s 预算覆盖 ⇒ `DRIFT-USERAPI-009②` 退回 OPEN）
且 **必须 ≥ +3**（否则验签落到 traceparent 的 SERVER span **之外** ⇒ 观测面漂移）。

🔴 **⇒ 两条硬约束：**
1. **`HttpMiddlewareFilter` 的 `@Order` 不得下调到 +6 或更低**（+6 已被 CpuShedding 占；再下调就装不下 JWT 槽位）；
2. **若共享层要给 MaxBytes 腾槽位**（把 §7 补充那条从「签字」改成「改码」）
   ⇒ **必须同时通知各域复核 `GoStyleJwtAuthFilter` 的槽位**，否则 ② 静默退回 OPEN。

⇒ ⚠️ **本条不是缺陷、是一张「先后手」清单** ⇒ **不需要 G4 落码**；
但 G4 若动共享层 Filter 顺序，**请在回执里点名这一条**。

---

## 10. 🆕 **待办依赖：MP 排序桥下沉共享层**（2026-10-27 收到 G3 预告 · **已独立核实**）

> ⚠️ **本节是「登记」，不是派单、也不是动作** ⇒ G3 明确说「只是预告」⇒ 本域**不动手**。
>
> 🟢 **2026-10-27 订正：这一节的「不动手」已经作废 —— 收编已落码 ⇒ 见本文件末尾的「§28. MP 排序桥收编」**
> （前置依赖「先 G4 共享层 ⇒ 再各域删」**已满足**：共享层那份随 `b7faeb4` 合入本树）。
> 本节以下内容**作为历史记录保留不改**（§6.3「不重写历史」）。

### 10.1 独立核实的结论（G3 的说法逐条成立）

| G3 的说法 | 本域实测 | 结论 |
|---|---|---|
| 域侧排序桥**全仓 9 份** | `find service -name "MybatisPlusOrderingBridgeAutoConfiguration.java"` ⇒ **9 份**（article / comment / favorite / follow / like / message / task / users-user / users-admin 各一） | ✅ **数字与名单都对** |
| **G1 的 3 个**在 G1 写权内 | `service/users/user/user-rpc` · `service/users/admin/admin-rpc` · `service/follow/follow-rpc` —— 三份文件 + 三份 `META-INF/spring/…AutoConfiguration.imports` 各 1 行注册 | ✅ **落点确认** |
| 共享层**没有**桥 | `find service/common -name "MybatisPlusOrderingBridge*"` ⇒ **零命中** | ✅ 属实 ⇒ 桥是**全域共性却落在域侧**（§5.3f：判据 = 依赖度数，不是目录位置） |
| 两座桥**同向** ⇒ 并存等价 | 域侧桥：`@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = MybatisPlusAutoConfiguration.class)`（`DataSourceAutoConfiguration` 取 **Boot 4 新包名** `org.springframework.boot.jdbc.autoconfigure.*`）⇒ 与共享层将要声明的边**同向** | ✅ **无环、功能等价** ⇒ 拆「先共享层加 ⇒ 再各域删」两步**不违反** §5.3c |

### 10.2 🔴 **前置依赖（别先删）**

**必须等 G4 的共享层桥落码之后，才能删域侧桥。** 否则「域侧删了、共享层还没加」⇒
MP 自动配置退避（`@ConditionalOnSingleCandidate(DataSource)` 找不到 DataSource）⇒ **不产 `SqlSessionFactory`** ⇒ **启动失败**。

⇒ 合并顺序：**先 G4 共享层 ⇒ 再各域删**（G3 派单 4 = G4 侧那一步）。

### 10.3 本域的**删除清单**（等 G3 正式派单后执行 · 纯删减 · 可独立提交）

| 组件 | 要删的类 | 要删的注册行 |
|---|---|---|
| `users/user/user-rpc` | `config/MybatisPlusOrderingBridgeAutoConfiguration.java` | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里的 `…users.user.rpc.config.MybatisPlusOrderingBridgeAutoConfiguration` |
| `users/admin/admin-rpc` | `config/MybatisPlusOrderingBridgeAutoConfiguration.java` | 同上（`…users.admin.rpc.config.…`） |
| `follow/follow-rpc` | `config/MybatisPlusOrderingBridgeAutoConfiguration.java` | 同上（`…follow.follow.rpc.config.…`） |

**落码后必跑的验证口径**：各自组件的**真 Spring 上下文**守门 ⇒ 判据 = **删掉域侧桥后 MP 仍生效**
（`SqlSessionFactory` 在 ＋ `@Import` 的 `DeadlineQueryTimeoutInjector` 真在执行链上）⇒ 绿 ⇒ 证明**共享层桥已接管排序**。

⚠️ **不需要**跨树同一次提交（两座桥同向 ⇒ 并存等价）⇒ **G1 的删除可独立提交、独立验证**。

### 10.3.1 🆕 **连带要改的：域台账里的桥引用**（本轮 grep 实测 · G3 那边只覆盖了他两个域）

删文件 ⇒ **台账会指向不存在的文件** ⇒ 必须同批订正：

| 台账位置 | 引用形态 | 动作 |
|---|---|---|
| `docs/DRIFT/follow/rpc.md:346` | **逐文件对照索引**的**第 5 行**（`rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`） | 🔴 **必改** ⇒ 删行（或改指共享层的同一机制） |
| `docs/DRIFT/follow/rpc.md:323` / `:337` | 「逐条对照此前无法核对」段里**列举的 10 个类名**之一 | 🟡 改（列举里少一个类 ⇒ 「10 个类」要改成 9） |
| `docs/DRIFT/users/**`（`user/rpc.md` · `admin/rpc.md`） | — | ✅ **零命中** ⇒ **users 侧台账无需订正**（本轮已 grep 实证，不留悬念） |

⚠️ 另：**桥文件自身的 javadoc** 里那条「什么时候可以删」（等 MP 用 Boot 4 新包名）与那段**过期的「同款」列举**
⇒ 本域桥的 javadoc 写的是「与 message-rpc / like-rpc / admin-rpc / user-rpc / article-rpc 同款」= **只列 5 个，实际 9 个**
⇒ **随文件删除即消失** ⇒ 只需确认没有**别的文件**引用这段文案（grep 已覆盖：`docs/DRIFT/{users,follow}/**` 里除上述 3 处外无其他引用）。


### 10.4 本轮**不动手**的理由

① G3 明确「本条只是预告」＋ 不要求回；
② **前置依赖未满足**（G4 的共享层桥未落）⇒ 现在删 = **制造启动失败**；
③ 域侧桥与共享层桥**并存无害** ⇒ 不删也不产生行为漂移 ⇒ **没有必须现在做的理由**。

---

---

## 11. 🆕 **域侧存量自闭环：两条 A 轴缺陷的落码**（2026-10-27 · G1 lane）

> **本节记录**：本轮把 **A1 轴**上两条「域内可闭环」的缺陷**落码 + 守门 + 变异验证 + 回归**。
> **元信息**：分支 `align/g1-users-follow`（基线 `a282012` = `do-my-own-gh`）· **全部落在 G1 写权内**
> （`service/users/user/user-api/**` ＋ `service/users/user/user-rpc/**`）· **未碰 `service/common`、未碰 `users-common`**。

### 11.1 两条缺陷的落点与口径

| 编号 | 轴 | 落点 | 修法（一句话） | 守门 | 变异验证 |
|---|---|---|---|---|---|
| `DRIFT-USERAPI-009②` | **A1** | `user-api`（新 `auth/` 包 + 改 `interceptor/JwtAuthInterceptor`） | **验签搬进 `@Order(+6)` 的 Filter（3s 预算之外）**，401 仍由 interceptor 抛（信封不变），黑名单留在 interceptor | `UserApiJwtVerificationScopeTest`（**5 例**，新建） | **3 个**（顺序 / 自己写响应 / 摘 `@Component`） |
| `DRIFT-USERRPC-V-012` | **A1 / A9** | `user-rpc`（`UserDomainService.logout`） | `catch (Exception e)` 里**补一行** `logBusinessErr(..., e)` ⇒ **status/description 一字不改** | `UserDomainServiceTest#logout_redisError_logsOriginalCause_forAttribution`（**新增 1 例**） | **1 个**（删掉那行 ⇒ 红） |

⇒ **4 个变异全部实测变红**（明细见 `docs/DRIFT/users/user/api.md` §9.y 的变异表）
⇒ **绿测试不是「错误答案的守卫」**。

### 11.2 🔴 本轮**没有**做的事（诚实边界）

| # | 没做 | 原因 |
|---|---|---|
| ① | **未跑双侧活体对拍** | 本轮是**纯域侧落码**，Go 侧进程未起 ⇒ 证据强度停在 **L2**（容器装配结果 + MockMvc 行为）+ **L1**（Go 链序逐行）⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」 |
| ② | **未推广到另外 9 个 api 服务** | 改法 A 的推广是**逐服务**的（Go 侧每个 api 自己 `server.Use`）⇒ 配方已写进 `docs/DRIFT/users/user/api.md` §9.y §7；**admin-api / follow-api 属 G1 写权内但本轮指令未点**，留给后续（⚠️ admin 侧 `DRIFT-ADMINAPI-006` 原文写的是「不列为待办」）<br>✅ **2026-10-27 晚更新**：**admin-api / follow-api 已推广完成**（提交 `9203bcc`）⇒ **本行前半句已过期** ⇒ 剩余 7 个（like / message / task ⇒ G2；article / comment / favorite / hot ⇒ G3）**属他域** ⇒ G1 不动 |
| ③ | **未销 `SD-USERAPI-002`** | 落地的形态**刻意**让 401 仍由 interceptor 抛 ⇒ ①（401 计入观测面）**不变** ⇒ `SD-USERAPI-002` **继续有效** ⇒ **无需销号** ⇒ 也就不需要动**不在 G1 写权内**的签字档 |
| ④ | **未动 `DRIFT-USERAPI-009 §7 补充`（MaxBytes 位置）** | 落点是 `service/common`（G4 写权）⇒ 已登记进 §6.4 ＋ **槽位联动警告** §6.4.2 |
| ⑤ | **未动 V-012 的 ②③ 两条** | 架构级（Redis 抛的不是 gRPC 的 `StatusRuntimeException` ⇒ B-1 守卫永不命中；Redis 写不绑 gRPC deadline）⇒ 与 `global` 域 **E38 第②层**同型 ⇒ 按「**另立项**」口径 ⇒ **V-012 不销** |

### 11.3 ⚠️ 本轮**引入**的新差异（如实登记）

- **JWT 验签与过载保护的相对次序**：Go 的 Authorize 在 MaxConns / Breaker / Shedding **之后**
  ⇒ 过载被拒的请求**不做**验签；RAG 的验签 Filter 与 `CpuShedding`(+6) **并列**
  ⇒ 并列落在 Shedding 之前时**多做一次无用验签** ⇒ **不改变任何响应**（只多一次 HMAC 计算）
  ⇒ **只在过载时可见 · 只影响 CPU · wire 面零影响**。
- 完整推导与「为什么这个并列是安全的（Filter 绝不写响应 ⇒ 先后无关）」⇒ `docs/DRIFT/users/user/api.md` §9.y §6。


### 11.4 ✅ 真启动 + 回归（2026-10-27 · 真 PG + 真 Redis + 真 Nacos + 真进程 + 真 Tomcat）

| 环节 | 结果 |
|---|---|
| `user-rpc` / `user-api` 真启动 | ✅ 6.379s / 9.684s ⇒ **新增的 `GoStyleJwtAuthFilter` 没破坏容器装配** |
| ① protected 无 token | **401 + 完整信封** `{"code":1006,...}` ⇒ **N-1 未破** |
| ② protected 合法 token | **200** ⇒ 验签链通 |
| ③ protected 坏签名 | **401 + 完整信封** |
| ④ **超限 body + 无 token** | **413 + 空 body** ⇒ **「413 压过 401」在真 Tomcat 下仍成立**（响应头层只有真 Tomcat 测得到） |
| ⑤ public 路径（login） | **200** ⇒ 不验签直接放行 |
| 回归 | **6 组件 BUILD SUCCESS**（`user-api` 118 · `follow-api` 56 · `follow-rpc` 78 · `user-rpc` · `admin-api` · `admin-rpc` 全绿）<br>✅ **2026-10-27 晚订正**：这一组数字已被后续两轮**超越** —— 推广轮（`9203bcc`）为 `admin-api` **135** · `admin-rpc` **115** · `user-api` **118** · `user-rpc` **137** · `follow-api` **63** · `follow-rpc` **78**（**646 条全绿**）；Redis 超时对齐轮再 +6（三个 api 各 2 例）⇒ **本行保留原值供追溯，最新数字见 `admin/api.md` §12.6** |
| 守门 | **5/5 EXIT=0**（`check-wave-lane-ownership` · `check-wave-role-model` · `check-wave-coupling` · `check-ledger-count` · `check-doc-links`） |

⚠️ **端口**：本轮 `user-rpc` 用 **49211**（默认 49210 **被 Docker 容器占用**）
⇒ **未改仓库任何配置文件**，只在启动脚本里 env 覆盖 ⇒ 零仓库影响。
⚠️ **一处假失败（已排除，非产品缺陷）**：user-api 单模块回归曾报 `BUILD FAILURE`（118 例全绿），
根因是**后台跑回归的同时又跑了 `mvn package`**（同一模块 `target/` 被并发写）⇒ 单独重跑 `BUILD SUCCESS`
⇒ **同一模块不要并发跑两个 maven**（§5.3c「同模块两个 lane 会互踩 target/」在**同一 lane 自己身上**同样成立）。

---

## 12. 🆕 **Redis 收编的「删除前置」静态核验 ＋ A2 轴关闭路径暴露面**（2026-10-27 · G1 lane）

> **本节是「核验 + 登记」，不是落码** ⇒ 共享层那两份（`1fb26b2` ＋ `2f12236`）**还没合进本树**
> ⇒ 删除动作**不在本轮** ⇒ 本节把「合入后要做什么、前置是否成立」**逐条钉死**，免得到时再猜。

### 12.1 「删掉 5 份域内副本」的前置条件 —— **5 项静态核验全部成立**

| # | 前置条件 | 本域实测（2026-10-27） | 判定 |
|---|---|---|---|
| ① | 共享层那份**能被装配**（自动配置要生效） | 5 个组件主类全带 `@SpringBootApplication` ⇒ 含 `@EnableAutoConfiguration`（`UserApiApplication` / `AdminApiApplication` / `UserRpcApplication:37` / `AdminRpcApplication:46` / `FollowApiApplication:68`） | ✅ |
| ② | 自动配置清单**在 classpath 上** | 5 个组件 pom 全依赖 `com.rag:common`（**默认 compile scope** ⇒ jar 进运行时 classpath ⇒ `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 可读） | ✅ |
| ③ | 共享层 `@ConditionalOnClass` 的三个类**都在** | 5 个组件全带 `spring-boot-starter-data-redis` ⇒ Boot 4 下带 `spring-boot-data-redis`（`LettuceClientOptionsBuilderCustomizer`）＋ lettuce-core（`ClientOptions`） | ✅ |
| ④ | 域内副本**只设断连行为**（删了零行为变化） | 5 份副本逐份核：只调 `disconnectedBehavior(REJECT_COMMANDS)`，**零** `timeoutOptions` 触碰 | ✅ |
| ⑤ | 值**逐字同值** | 5 份副本全 `REJECT_COMMANDS` ＝ 共享层 `GoStyleRedisClientAutoConfiguration` 同值 | ✅ |

⇒ **结论**：删除是**纯删减**，**不需要任何行为补偿**。

⚠️ **`follow-rpc` 不在清单里**：它**不用 Redis**
（`service/follow/follow-rpc/pom.xml:26` 明写「任何 redis/kafka starter 在本模块都属于『新增 Go 侧不存在的能力』，一律不引」）
⇒ 无副本 ⇒ 也**无删除动作** ⇒ 别按「5 份」去 `follow-rpc` 里找。

### 12.2 🔴 连带项：**5 份 FastFail 守门是「显式 `@Import` 域内副本」** ⇒ 副本一删就**编译不过**

⇒ **收编不是「删 5 个文件」，而是「删 5 个文件 ＋ 重写 5 份守门的装配方式」**（漏了后半段 ⇒ 编译红）。

每份守门要改的**两处**：

1. `@Import(com.rag.<域>.<组件>.config.RedisDisconnectedBehaviorConfig.class)`
   ⇒ 换成 `@ImportAutoConfiguration(com.rag.common.redis.GoStyleRedisClientAutoConfiguration.class)`；
2. **补「收编真的发生」的两条断言**（缺了 ⇒ 只能证明「值对」，证明不了「是共享层那份对的」）：

   - `context.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` ⇒ **size == 1**
     （不是 2 份叠加，也不是 0 份）；
   - 该 map 的 key 含 **`goStyleRedisClientOptionsCustomizer`**（共享层那份的**固定 bean 名**）。

⚠️ **为什么必须补第 2 条**（这是「遮蔽」契约的必然推论）：
`@ConditionalOnMissingBean` 是「**遮蔽**不是**叠加**」⇒ 域内副本还在时，共享层那份**根本不创建**
⇒ 此时「`REJECT_COMMANDS` 生效」**是真的**，但「**收编发生了**」**是假的**
⇒ ⇒ **只断言值 ⇒ 收编没做也绿** ⇒ ⇒ 守门必须断言 **bean 身份**，不能只断言行为值。

### 12.3 A2 轴：关闭路径不得触发 web 类型类加载 ⇒ 我域 **3/3 rpc 全暴露**（`PITFALL-FOLLOW-005`）

**缺陷**（G4 `3b573fb` 修的那条）：`ObservabilityLifecycle.shutdown()` 第一句是 `HttpMiddleware.shutdown()`
⇒ 纯 gRPC 进程 classpath 无 servlet ⇒ 炸 `NoClassDefFoundError: jakarta/servlet/Filter`
⇒ 该方法**不吞异常** ⇒ **`RpcInterceptor.shutdown()` 根本不执行** ⇒ **RPC 池没被回收**。

⚠️ **共享层 javadoc 与代码自相矛盾**（本域读到的 ⇒ 登记给 G4）：
`ObservabilityLifecycle.shutdown()` 的 javadoc 写「**两个调用各自独立**、异常向上抛」
⇒ **实际代码没有 try/finally**（`HttpMiddleware.shutdown();` 紧接 `RpcInterceptor.shutdown();`）
⇒ ⇒ **javadoc 承诺的「各自独立」在代码里并不存在** ⇒ **这正是缺陷的成因**（不是「Spring 会继续销毁其余 bean」能兜住的 —— 兜不住的是**同一个方法内的第二条语句**）。

**我域暴露面（实测，非转述）**：

| 组件 | `@Import ObservabilityLifecycle` | 运行时 classpath 有 servlet 吗 | 暴露 |
|---|---|---|---|
| `user-rpc` | `UserRpcApplication.java:57` | ❌ 无 | 🔴 暴露 |
| `admin-rpc` | `AdminRpcApplication.java:71` | ❌ 无 | 🔴 暴露 |
| `follow-rpc` | `FollowRpcApplication.java:77` | ❌ 无 | 🔴 暴露 |

⚠️ **判据更正（我自己踩的一次假阳性）**：
第一次用 `grep "spring-boot-starter-web" pom.xml` 查 ⇒ **`user-rpc` 命中** ⇒ 差点判「user-rpc 有 Tomcat ⇒ 不暴露」
⇒ **命中的是注释里的字样**（`user-rpc` 的 pom 注释明写「严格保持纯 gRPC service，**不加** spring-boot-starter-web」）
⇒ ⇒ **判据必须剥掉 XML 注释再查依赖**（已改用剥注释后的正则）
⇒ **结论：3/3 全无 web/tomcat/servlet 依赖**，只有 `spring-boot-web-server`
⇒ `spring-boot-web-server`（Boot 4.0.8）的 pom 里 **servlet 出现次数 = 0**
⇒ 只含 `WebServerInitializedEvent` + 抽象接口 ⇒ **不拉 Tomcat/Jetty**。

⇒ **合入后我域必查（3 条）**：
1. 3 个 rpc 进程的关闭路径**不再抛** `NoClassDefFoundError`；
2. `RpcInterceptor` 的池**真的被回收**（否则缺陷只是「不再污染日志」，**池泄漏仍在**）；
3. 3 个 api（`user-api`/`admin-api`/`follow-api`）**仍注册** `HttpPoolLifecycle`
   （⇒ HTTP 池仍被回收 ⇒ **零行为变化**）。

### 12.4 🔴 **合并顺序约束**：`3b573fb` 与 `7488b3d` **必须同批合入**（否则我域由绿转红）

> **本条已发给 G4（G4 侧待回复）＋ G2 ＋ G3（三方都可能被整批合入影响）** ⇒ 登记在此以免只在消息里。

**机理**（两层）：

1. 修 `3b573fb` **之前**，`ObservabilityLifecycle.shutdown()` 第一句抛 `NoClassDefFoundError`
   ⇒ `RpcInterceptor.shutdown()` **根本没机会执行** ⇒ 进程级静态池**侥幸存活**
   ⇒ 前几轮全量一直绿 —— ⚠️ **那不是「本来就对」，是缺陷把保护罩挡住了**（**绿是因为没走到**）；
2. `3b573fb` 让 shutdown **真的执行** ⇒ 若 `7488b3d`（池自愈：静态池改为「被终止后按需重建」）**未同时在位**
   ⇒ 池「**终止即永久**」⇒ 同模块内**后续**的 Spring 上下文被毒化
   ⇒ 拦截器返回 `INTERNAL` 而不是 `DEADLINE_EXCEEDED`。

**我域暴露面（实测，非转述）**：

| 组件 | `@SpringBootTest` 上下文数（surefire 一个模块一个 JVM） | 同款断言 | 暴露 |
|---|---|---|---|
| `user-rpc` | **11** | `UserRpcGrpcInterceptorWiringTest:126` `serverSideTimeoutIsActuallyEnforced` ⇒ 断言 `DEADLINE_EXCEEDED` | 🔴 高 |
| `admin-rpc` | **13** | `AdminRpcGrpcInterceptorWiringTest:136`（同方法同断言） | 🔴 高 |
| `follow-rpc` | **5** | `FollowGrpcInterceptorWiringTest:102`（同方法同断言） | 🔴 高 |

⇒ **与 article-rpc 打红的那条同名同义** ⇒ **方向不对称**：
`7488b3d` 单独合入 ⇒ 池可重建 ⇒ 测试仍绿（只是 #8 仍未修）；
`3b573fb` 单独合入 ⇒ **会红** ⇒ ⇒ **顺序：先 `7488b3d`、后 `3b573fb`，或同批**。

**证据强度**：**L3**（本域静态清点：上下文数 + 断言位置 + 两条提交的源码对照）
＋ G4 侧 article-rpc 的 **L4**（同一形态已实测变红）⇒ 合入后由**波末收口回归**复核。

⚠️ **本域此刻仍绿，是「绿是因为没走到」** ⇒ **不能**把当前的全绿读作「我域不受这条影响」。

---

## 13. 🆕 **C7 预检：G4 共享层变更对本域的影响面**（2026-10-27 · G1 lane · 合入前先算清）

> **本节的价值**：C7 规定「收口回归跑完 ～ 合并进主干之前」是**唯一风险窗口**（共享层变了 ⇒ 必须重跑收口回归）。
> ⇒ 本节把 **G4 已落码的每一条共享层提交对本域的影响**先算清 ⇒ **合入后不需要重新推导**，
> 只剩「按本节逐条复验」。

| G4 提交 | 变更内容 | 对本域的影响 | 本域动作 |
|---|---|---|---|
| `182fc32` | form 闸门判据改「语义可达性」（新增 `SEMANTIC_REACHABILITY` 逐域取证表）+ 保护链加「层构成」只读入口 | **零影响**（逐条见下） | ✅ **无** |
| `1fb26b2` ＋ `2f12236` | Redis 断连策略收编进共享层（`GoStyleRedisClientAutoConfiguration`） | 🟡 **有影响**：域内副本会**遮蔽**共享层那份 ⇒ 收编要**删 5 份副本 ＋ 重写 5 份守门** | 🔴 **待 G4 合入后执行**（清单见 §12.2） |
| `3b573fb` | 关闭路径不触发 web 类加载（拆出 `HttpPoolLifecycle`） | 🔴 **有影响**：我域 **3/3 rpc** 全部 `@Import ObservabilityLifecycle` 且运行时 classpath **无 servlet** ⇒ 全暴露 | ✅ 合入后按 §12.3 的 3 条必查复验 |
| `7488b3d` ＋ `182fc32` | 进程级静态池改为「被终止后按需重建」 | 🔴 **与 `3b573fb` 有顺序耦合** ⇒ **必须同批**（见 §12.4） | ✅ 合入后按 §12.4 复验 |
| `853767b`（G2 FB-04 · A9） | 让 gRPC 业务 handler 执行期能拿到当前 server span（`RpcInterceptor.onHalfClose()` 里用 `otelCtx.makeCurrent()` 包住 handler 调用） | ✅ **零影响**（本域**自核**，不采信转述）：① 我域主源码 `Span.current()` **读取点 = 0 命中**；② 共享层主源码 `Span.current()` **读取点 = 0 命中**（只有 `makeCurrent()` 的**设置**方：`RpcInterceptor:332/:392/:429` · `HttpMiddleware:312` · `GoStyleTraceparentFilter:278` · `MqConsumer:95`）⇒ ⇒ 让 server span 变 current **没有任何消费方**；③ 全仓唯一读取点 `comment-rpc/CommentFactDeliveryService.java:148` 跑在**调度线程** ⇒ FB-04 改的是 **handler 线程** ⇒ 碰不到它 | ✅ **无**（合入后复验即可） |
| `853767b` 的**反向含义**（写明以免后人误记） | —— | ⚠️ **正因为双方都零读取点**，FB-04 这类「让 span 变成 current」的改动**在本波不会体现在任何域的可观察面上** ⇒ ⇒ **若将来某个域开始用 `Span.current().setAttribute(...)`，它会立刻依赖 FB-04 这个前提** ⇒ ⇒ 那时 FB-04 就是**必需**而非「零影响」⇒ **复开触发条件已锁定** | —— |
| `c15dfeb`（G2 FB-05 · A3） | form 闸门的 `mode` 解析**额外接受 `"false"`**（YAML 1.1 把未加引号的 `off` 解析成布尔 false ⇒ 文档里的合法值写不出来 ⇒ 构造期抛异常 ⇒ 进程起不来）⇒ **只改了 2 个文件**：`GoStyleFormLimitMode.java`（+38/−2，**javadoc ＋ `parse` 多一个别名**）＋ 新增 `GoStyleFormLimitModeYamlBooleanGateTest.java`（152 行） | ✅ **零影响（本域自核 · 2026-10-27 13:2x · 不采信转述）**：① `git show --stat c15dfeb` ⇒ **只 2 个文件**，`GoStyleTomcatParameterLimitCustomizer` 的 diff **为空** ⇒ **容器上限 10001 不受影响**；② 本域三 api 的 `mode` 实测 = **user-api `raw` · admin-api `raw` · follow-api `envelope`** ⇒ ⇒ **三个都不是 `off`** ⇒ ⇒ `parse` 的合法值域**扩大**了一个而已 ⇒ ⇒ 本域三个取值**走同一批分支 ⇒ 行为逐字不变** | ✅ **无改动**，但**合入后复验一条**：我域三 api 启动时都会构造该 bean ⇒ ⇒ **复验「我域的三个 `mode` 值仍被 `parse` 接受」**（成本极低） |

### 13.1 `182fc32` 对本域 = **零影响**（逐条核过，非转述）

| 面 | 核验 | 结论 |
|---|---|---|
| **form 闸门配置** | G4 的语义表逐行写着 `service/users/user/user-api` ⇒ **RAW**、`service/users/admin/admin-api` ⇒ **RAW**、`service/follow/follow-api` ⇒ **ENVELOPE**；本域 yaml 实际值 = `raw` / `raw` / `envelope`（`user-api/application.yml:231` · `admin-api/application.yml:222` · `follow-api/application.yml:202`） | ✅ **三域逐条一致 ⇒ 合入后零改动** |
| **G4 的独立核实** | 其提交信息里写着「user-api / admin-api 命中 8 / 13 处 ⇒ 语义 raw ⇒ **二者 yaml 已是 raw（一致）**」 | ✅ **对方已独立核过同一件事**（非我的转述） |
| **容器上限** | `GoStyleTomcatParameterLimitCustomizer` 的 diff **只动注释**（`customize()` 的 `if (mode == OFF) return;` 分支逐字未变）⇒ 本域三域都是**可达**态 ⇒ 容器上限维持 **10001** | ✅ **零行为变化** |
| **gRPC 保护链** | `GoStyleRpcProtectionChain` 的改动 = 新增 `Layered` / `Composition` / `compose(props)` / `layerNames(chain)` **只读入口**；`build` 改为消费 `compose(props)`，**条件与顺序逐字不变** | ✅ **零行为变化**（本域 3 个 rpc 的拦截器链不受影响） |

⇒ ⇒ **结论：`182fc32` 合入后，本域 3 个 api ＋ 3 个 rpc **无需任何改动**，只需复验。
⇒ ⚠️ **但它同时新增了一条对本域有约束的共享层判据**：`SEMANTIC_REACHABILITY` 把「Go 侧该域是否真的解析 form/query」写成了**逐域取证表** ⇒
⇒ **⇒ 若将来 Go 侧 user/admin/follow 的 handler 改成解析 form/query，本域的 `mode` 必须跟着改** ⇒ **复开触发条件已在本节锁定**（G4 侧有 `GoStyleFormLimitSemanticCriterionTest` 6 条守门守着这张表）。

> ⚠️⚠️ **本节预检的边界（必读）**：上表**只覆盖 G4 已提交的 4 条**。
> ⇒ **G4 还有未提交的在途工作**（2026-10-27 13:0x 观测到其工作树在改
> `GoStyleTomcatParameterLimitCustomizer` ＋ 新增 `GoStyleParamLimitOffModeGateTest`，
> 方向 = **「OFF ⇒ 容器上限也要抬到 10001」**，即把「装不装应用层闸门」与「抬不抬容器上限」**解耦**）。
> ⇒ ⇒ **对本域三域（raw / raw / envelope，均为可达态）该改动是零影响**（容器上限**本来就是 10001**）
> ⇒ ⇒ **但它会改变 `OFF` 域的语义** ⇒ ⇒ **合入时必须以 G4 的最终提交为准重新核一遍本节**
> ⇒ ⇒ **本节的真正作用是「给合入后的复验清单」，不是「合入前的免检证明」**。

### 13.2 ⚠️ **「窗口快照」会过期 —— 跨 lane 错峰的一条操作纪律**（2026-10-27 实测）

⇒ **实测**：我的等待器（判据 = `multiModuleProjectDirectory=` 后缀的树名，**不自匹配**）在 **13:07:41** 报「全机无别的 lane 在跑 maven」（G2 刚退出）⇒ ⇒ **13:07:45**（**4 秒后**）G3 就起了它自己的 maven ⇒ ⇒ **快照在 4 秒后即过期**。

⇒ ⇒ **纪律（本域跨 lane 协作时照此做）**：
1. **等待器/快照的结论只在那一瞬间成立** ⇒ ⇒ **任何基于它的动作（起跑 / 通知别人）必须再复检一次**；
2. ⇒ **通知别人时给「原始判据 + 时间戳 + 复检方法」**，不要只给结论（本例把 `ps -ww` 的原始命令行与「看树名后缀、**不要 `grep -c` 计数**」一起给出）；
3. ⇒ **自己承诺「窗口期零占用」时写成可核验的形式**（「不跑任何 maven，**含单模块定向跑**」）⇒ ⇒ 承诺不替代对方自己的复检。

⇒ ⚠️ **本轮的实际占用**（供复现）：`g3` 那个是 `-pl service/comment/comment-rpc -am test -Dtest=CommentRpcRuntimeBootGateTest … -DargLine=-Xmx768m` ⇒ **单测试类 ＋ 堆上限写死** ⇒ **负载很轻** ⇒ ⇒ 与历史那组（G4 的 52 模块 `clean verify` ＋ 模块级 `-pl … test`）**不同量级**。

### 13.3 🔴 **C7 的硬前提：收口回归必须在【所有 lane 合入主干之后】跑，不能用任何 lane 的 lane 级全量代替**

⇒ **实测依据**（2026-10-27 · 详见 §18.5）：**G4 的第八次全量里，我域 6 个组件是 605 条**，而**我树 `fa608f3` 实测是 667 条** ⇒ 差 **62** ⇒ ⇒ 根因 = **G4 的树里没有 G1 的任何提交**（7 笔全 `NOT_IN_G4`）⇒ ⇒ **那份全量的工作区** ≠ **任何单域的当前态**。

⇒ ⇒ **由此得到三条硬前提**（C7 执行时逐条对照）：

| # | 前提 | 判据（可机械核） |
|---|---|---|
| 1 | **收口回归的代码态必须包含所有 lane 的提交** | 回归前先 `git rev-list --left-right --count do-my-own-gh...HEAD` ⇒ **落后数 = 0**；并逐 lane 核「该 lane 的 tip 已是祖先」 |
| 2 | **不得引用任何 lane 的 lane 级全量数字当作本域证据** | 每个 lane 的全量都在**自己的树**上跑 ⇒ **别的 lane 的提交不在里面** ⇒ 只能当「共享层那半」的证据，不能当「本域那半」的证据 |
| 3 | **本域自己的数字只有两个来源可采信** | ① **本域树上的本域回归**（本轮 = **667**，两口径交叉验过）② **所有 lane 合入后的收口回归**（= C7，尚未跑）⇒ **两者之间的任何数都不作数** |

⇒ ⚠️ **对「667」的诚实定位**：它是 **G1 树 × G1 域** 的数字，**不是** 波次最终数字（§`user/rpc.md` §10 已写明「本轮 667 条不是最终数字」）⇒ ⇒ **本轮它只能当「G1 域内当前态」的证据，不能当「波次收口」的证据**。

⇒ 🔴 **补充（2026-10-27 13:2x）**：**四棵树正在往不同方向分叉** —— 实测 **G2 的树已经合并了 G4 的分支**（`37816ca`）⇒ **G2 树里有共享层的 Redis 收编 / 池自愈 / span current**，而 **我树里一个都没有**（我树 `service/common` = 主干基线 ⇒ `AutoConfiguration.imports` 仅 2 条）⇒ ⇒ **同一波次里，不同 lane 的树的内容差异会越来越大** ⇒ ⇒ **汇合点只能是 `do-my-own-gh`（主干）** ⇒ ⇒ **任何 lane 的「全量」都不能代表其他 lane 的树**（与 §18.5 的实测同源）。

### 13.4 🟡 **G4 的错峰知会窗口（2026-10-27 13:2x）** —— 承诺 ＋ 三条实测事实

⇒ **G4 知会**：`FB-05` 已落码（`c15dfeb` · **改了共享层生产代码**）⇒ 从「现在起」跑**第九次全量**（`mvn -B clean verify` · 约 20 分钟）⇒ 请我**此窗口内不要跑 maven 全量**（同窗并发 ⇒ 系统 OOM-kill ⇒ `EXIT=137`）。
⇒ ⇒ **我域的答复 = 零占用**（**不跑任何 maven，含单模块定向跑**）⇒ 本轮**未起任何 maven**（只跑了 5 个 python 门禁 + 字节级扫描）⇒ ⇒ **本域对共享层变更的复验，统一放到「G4 进主干 → 并回我树 → 一次跑完（域内收口 ＋ C7）」那个窗口**（那时才测得到新代码）。

**三条我自己实测的事实（不采信转述，给 G4 做决策用）**：

1. ✅ **`c15dfeb` 对我域 = 零影响**（详见上表新加的那一行）⇒ ⇒ **我域不需要为这一条单独复跑** ⇒ ⇒ **它不构成我域的额外待办**。
2. 🟡 **13:29:06 实测占用方是 G3，不是 G4** ⇒ 完整命令行 =
   `mvn -B -pl service/favorite/favorite-api -am test -Dtest=FavoriteApiRealTomcatGateTest -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false -DargLine=-Xmx768m`
   ⇒ ⇒ **单测试类 ＋ 堆上限写死 768m ＝ 轻量占用** ⇒ ⇒ **与历史那组（52 模块 `clean verify` ＋ 模块级 `-pl … test`）不同量级** ⇒ ⇒ **不是同一类风险** ⇒ ⇒ **但「窗口被占」这个事实仍要由动作方自己复检**（判据：`ps -eo command | grep "[p]lexus.classworlds" | sed 's/.*multiModuleProjectDirectory=//; s/ org.codehaus.*//'` ⇒ **看树名 + 看完整 `-pl` 范围**，不要只看「有没有人在跑」）。
3. 🟡 **全量次数编号疑似回退（提醒，不追责）**：G4 台账里 **§4.10.14 = 第九次（代码态 `c15dfeb`）**、**§4.10.15.6 = 第十次（`5a954bb`）**、**§4.10.22 = 第十一次（`040a201`）** **均已落** ⇒ ⇒ 「现在起第九次（代码态 `c15dfeb`）」= **与已登记的第九次同代码态** ⇒ ⇒ **是重跑还是编号回退，请 G4 自己在 §4.10.26 里写清** ⇒ ⇒ **理由**：上次「**29**」的串台（我把它误记成「G3 的 29 模块回归」）**就是从这种编号/口径回退里长出来的** ⇒ ⇒ **跨 lane 引用全量口径前，一律先问「哪个代码态、哪一轮」**。

---

## 20. 🆕 **U+FFFD 跨树全表（2026-10-27 · G1 lane）** —— 四棵树各修各的，**主干一份都没修**

> **触发**：G2 回执 ④ 说「U+FFFD 跨树复核（**3 条我树已干净 / 主树仍坏** ⇒ 合并须**以 G2 树版本为准**）」⇒ ⇒ **本轮把范围扩到全仓逐行扫描**（字节级 · `b'\xef\xbf\xbd'`）⇒ ⇒ **发现 G2 的清单只覆盖了它自己的 3 份**。

### 20.1 🔴 **主干 `do-my-own-gh` 的全仓 U+FFFD 清单：20 份文件 / 51 处**（逐行定位）

| # | 文件 | 处数 | 性质 | **已修的 lane（字节级实测）** | **合并时该取谁** |
|---|---|---|---|---|---|
| 1 | `service/common/…/observability/GoStyleOtelSamplerProtocolGateTest.java:24` | 2 | 真损坏 | ✅ **G4**（`0c72969`） | **G4 树** |
| 2 | `service/common/…/protection/FakeServerCall.java:13` | 2 | 真损坏 | ✅ **G4** | **G4 树** |
| 3 | `service/like/like-rpc/…/domain/LikeOutboxEventEntity.java:17` | 3 | 真损坏 | ✅ **G2** | **G2 树** |
| 4 | `service/message/message-api/…/GoStyleGrpcClientBreakerE2ETest.java:43` | 3 | 真损坏 | ✅ **G2** | **G2 树** |
| 5 | `service/message/message-rpc/…/MessageRpcProductionAddressTest.java:96` | 3 | 真损坏 | ✅ **G2** | **G2 树** |
| 6 | `service/comment/comment-rpc/…/service/CommentAuditPublisher.java:19` | 3 | 真损坏 | ✅ **G3** | **G3 树** |
| 7 | `service/favorite/favorite-rpc/…/config/FavoriteRpcLabels.java:9` | 3 | 真损坏 | ✅ **G3** | **G3 树** |
| 8 | `service/favorite/favorite-rpc/…/mapper/FavoriteItemMapper.java:78` | 2 | 真损坏 | ✅ **G3** | **G3 树** |
| 9 | `service/favorite/favorite-rpc/…/service/FavoriteRpcSupport.java:70` | 2 | 真损坏 | ✅ **G3** | **G3 树** |
| 10 | `service/favorite/favorite-rpc/…/service/FavoriteFactSemanticsTest.java:37` | 3 | 真损坏 | ✅ **G3** | **G3 树** |
| 11 | 🟡 **`service/users/user/user-api/…/service/AvatarHistoryService.java:32`** | 2 | 真损坏 | ✅ **G1（本域 · `0c2a902`）** | **G1 树** ← **本轮新增** |
| 12 | 🟡 **`service/users/user/user-api/…/AvatarHistoryIgnoresBodyTest.java:61`** | 3 | 真损坏 | ✅ **G1（本域 · `0c2a902`）** | **G1 树** ← **本轮新增** |
| 13 | 🟡 **`service/users/user/user-rpc/…/UserRpcLoginNullStatusReadsAsZeroTest.java:44`** | 3 | 真损坏 | ✅ **G1（本域 · `0c2a902`）** | **G1 树** ← **本轮新增** |
| 14 | `service/users/user/user-api/…/UserApiGoStyleBodyContentTypeTest.java:376` | 1 | **故意**（`assertFalse(decodedAsUtf8.contains("␦"))` 的**被测对象**） | — | **不动** |
| 15 | `service/article/article-api/…/ArticleApiMvcPipelineTest.java:1143` + `:1159` | 2 | **故意**（`ext_info` 的 **key** 钉死码点序 + 同一测试类里的 FFFD 断言） | — | **不动**（G3 已判） |
| 16 | `docs/Pitfalls/users/user/rpc.md:146` | 1 | **故意**（`'用户不␦?'` = 故意记录「末字已不可恢复」） | — | **不动**（本域） |
| 17 | `docs/Pitfalls/message/rpc.md:911` / `:921` | 2 | **故意**（`CONTAINER␦: unbound variable` = **shell 报错原文样本**） | — | **不动**（G2 域） |
| 18 | 🔴 `tools/errmsg-probe/README.md:59` | 3 | **真损坏**（「未登记码的回␦␦␦结果」） | ✅ **G4 已认领并修** | 🟢 **已关闭**（G4 `cc6e38e` · 原文取自同仓完好副本，不靠猜） |
| 19 | 🔴 `tools/sql-statement-probe/favorite/compare.py:561` | 3 | **真损坏** | ✅ **G4 已认领并修** | 🟢 **已关闭**（G4 `cc6e38e`） |
| 20 | 🔴 `component-semantic-verification/springboot-semantic-model.md:269` / `:320` | 5 | **真损坏** | ✅ **G4 已认领并修** | 🟢 **已关闭**（G4 `f68a9cd` · 原文按语义补回） |

> 🟢 **2026-10-27 订正**：#18~#20 这 **3 份「无人拥有」** 已由 **G4 认领并修复**
> （随 `6375faf` 合入我树 ⇒ 合并态我树这 3 份**零命中**）
> ⇒ ⇒ **「待合并收口执行者认领」这条待办【关闭】** ⇒ ⇒ §20.2 合并规则里
> 「合并后目标 = 7 份文件命中」**已过期** ⇒ 实际目标 = **只剩 6 处故意（4 份文件）**。

### 20.2 ⇒ ⇒ **合并规则（写死，别再逐次口头对）**

1. **13 份真损坏分属 4 个 lane**（**G4 2** · **G2 3** · **G3 5** · **G1 3** ⇒ 2+3+5+3 = **13**）⇒ ⇒ **合并时必须逐份取「已修的那个 lane 的树」** ⇒ ⇒ **若按主干版本取 ⇒ 13 份坏字符全部被带回去**。
   ⚠️ **份数是加出来的，别拍脑袋**：**13 真损坏 ＋ 4 份故意 ＋ 3 份无人拥有 = 20 份** ⇒ 与全仓扫描的 20 份**闭合**（加不出来就说明漏了或多数了 ⇒ 这是这张表的**自校验**）。
   ⇒ **处数同样闭合**：13 份真损坏 **34 处**（G4 4 + G2 9 + G3 13 + G1 8）＋ 故意 **6 处** ＋ 无人拥有 **11 处** = **51 处** ✅ 与全仓扫描的 51 处一致。
2. **6 处是「故意」**（#14~#17，共 **4 份文件**）⇒ ⇒ **一个字都不动** ⇒ ⇒ **「全仓 U+FFFD 归零」不是合法目标**。
3. **3 份是「无人拥有」**（#18~#20）⇒ ⇒ **不在任何 lane 的写权面** ⇒ ⇒ **我域不代改** ⇒ ⇒ 已登记，请合并收口执行者认领。
4. ⚠️ **G2 的清单只覆盖它自己的 3 份** ⇒ ⇒ **我域的 3 份（#11~#13）此前不在任何人的清单里** ⇒ ⇒ 本轮**主动补进**（否则合并时按主干取 ⇒ 我域那 3 处坏字符回流）。
5. ⇒ **合并后必须再跑一次字节级扫描**（不是 `grep` —— 见 §19 的判据纪律）⇒ ⇒ **目标 = 只剩 6 处故意（4 份文件）＋ 3 份待认领 = 7 份文件命中**。

### 20.3 ✅ **四棵树的当前命中数（字节级全仓扫描，2026-10-27 13:2x）**

| 树 | 命中份数 | 说明 |
|---|---|---|
| **主干 `do-my-own-gh`** | **20** | **一份都没修**（合并收口那一步还没做） |
| **我树 G1** | 20 | 我域 3 处真损坏已清零（+3 份台账的**故意引述**）⇒ **数值与主干同、构成不同** ⚠️ ⇒ **命中数相同 ≠ 同一批文件** ⇒ **不能用「份数」当判据，要用「文件清单」** |
| **G2 树** | 15 | 它域 3 份已清零（+ `service/common` 2 份因**合并了 G4 分支**而清零） |
| **G3 树** | 13 | 它域 5 份已清零 |

⇒ ⇒ **「命中份数」这个数在四棵树上分别是 20 / 20 / 15 / 13** ⇒ ⇒ **看起来接近的 20 与 20 其实是两批完全不同的文件** ⇒ ⇒ **复扫必须比「文件清单」而不是比「份数」**（与 §15「孤儿报告」同族：**总量相同不代表构成相同**）。

---

## 21. 🆕 **`application.yaml` 命名例外** —— 定性：**不是缺陷 · 有意保留**（2026-10-27 · G2 提醒 ＋ 本域独立核实）

> **G2 的提醒**：`service/users/user/user-rpc` 用 `application.yaml`，而全仓其余组件用 `application.yml` ⇒ G2 已核「**不是『两种扩展名并存』**」⇒ **不是缺陷**；但提醒「**后人排查配置时极易误判『同一域两种扩展名 ⇒ 会不会互相覆盖』**」⇒ 并建议「若顺手，统一成 `.yml` 最省事（**改动 1 处文件名 + 1 处引用**）」。

### 21.1 ✅ **本域独立核实（不采信转述）** —— 结论成立，但**分母与连带面都要订正**

| 项 | G2 的说法 | **本域实测** | 判定 |
|---|---|---|---|
| 组件总数 | **16** | **20**（article / comment / favorite / follow / hot / like / message / task 各 2 ＋ users 4） | 🔴 **分母订正** |
| 用 `.yml` 的组件 | 15 | **19** | 同上 |
| 用 `.yaml` 的组件 | 1（`user-rpc`） | **1** ✅ | ✅ **「唯一例外」成立** |
| 同 location 双扩展名 | 无 | `user-rpc/src/main/resources/` = `META-INF` ＋ `application.yaml` ⇒ **该组件只有一个配置文件** | ✅ **无覆盖、无加载顺序风险** |
| 改名的连带项 | 「**1 处引用**」 | **≈ 42 处**（见 §21.2） | 🔴 **G2 低估了连带面** |

⇒ ⇒ **定性成立：不是缺陷**（Spring Boot 两种扩展名都认 ⇒ 零行为差异；且每组件只有一个配置文件 ⇒ 不存在覆盖）⇒ ⇒ **但「1 处引用」这个成本估计不成立**。

### 21.2 🔴 **若真改名，连带面是 ≈ 42 处**（实测 `application\.yaml` 命中）

| 归属 | 命中 | 分布 |
|---|---|---|
| ✅ **我域写权面** | **38 行 / 15 份文件** | `docs/DRIFT/users/user/rpc.md` **14** · `service/users/user/user-rpc/src/test/java/…/UserRpcNacosGroupTest.java` **5** · `…/UserRpcRedisTimeoutConfigTest.java` **3** · `…/UserRpcGrpcInterceptorWiringTest.java` **3** · `docs/Pitfalls/users/_shared.md` **2** · `service/users/user/user-rpc/src/main/resources/application.yaml` **3**（自身）· `docs/DRIFT/users/vs-common.md` **1** · `docs/DRIFT/users/_shared.md` **1** · `…/UserRpcPrimaryKeySourceParityTest.java` **1** · `…/UserRpcProperties.java` **1** · `…/NacosRegistration.java` **1** · `…/UserRpcApplication.java` **1** · `service/users/user/user-api/src/main/resources/application.yml` **1**（注释）· `…/UserApiProperties.java` **1** · `service/users/admin/admin-rpc/src/main/resources/application.yml` **1**（注释）· `…/AdminRpcProperties.java` **1** |
| 🔴 **无人拥有（合并收口执行者写权面）** | **3 处** | `docs/DRIFT/_cross/open-items.md:80` · `docs/FileTree/like/rpc.md:20` · `tools/difftest/run.sh:184`（注释）⇒ ⇒ **我改不了** |
| ✅ **不受影响** | 2 个脚本 | `tools/difftest/check-grpc-runtime-classpath.py:140` · `check-tomcat-limits.py:174` ⇒ 两者都是 `for fn in ("application.yml", "application.yaml")` ⇒ **两个扩展名都认** ⇒ 改名不影响它们 |

### 21.3 ⇒ ⇒ **本轮决定：不改名，改为在台账登记**（三条独立理由，任一条都足够）

1. **成本 ≫ 收益**：收益 = 消除**一个**命名误判点；成本 = **≈ 42 处**引用同步（其中 **3 处我改不了**）⇒ ⇒ **跨 lane 协调成本**（要请合并收口执行者同步 3 处 + 我自己改 38 行）⇒ ⇒ **与 G2 的「顺手最省事」估计差两个数量级**。
2. 🔴 **会留下我无法同步的不一致**：`docs/Pitfalls/users/_shared.md` 的 **2 处**引用 **不在任何 lane 白名单**（写权守门实测：G1 白名单 = `service/users/**`（除 `users-common`）＋ `service/follow/**` ＋ `docs/DRIFT/{users,follow}/**`）⇒ ⇒ **改名后这 2 处会变成过期引用，而我没有写权去改** ⇒ ⇒ **等于用「消除误判」换「引入新的不一致」** ⇒ ⇒ 不可接受。
3. ⚠️ **改名对 Go 对齐零收益**：AGENTS.md §三规定「差异只允许出现在**表达层**」⇒ ⇒ 文件扩展名是**纯表达层** ⇒ ⇒ 改它**不改善任何一条语义漂移** ⇒ ⇒ 而本项目对「行为层零变化」的改动一律要求配回归 ⇒ ⇒ **窗口期内还不能跑回归** ⇒ ⇒ 现在改 = **既无收益、又欠一次未验证的回归**。

### 21.4 🔓 **复开触发条件（写死，将来满足其一就统一）**

- ① 将来某个工具/脚本**按扩展名做单文件假设**（只找 `application.yml`、不认 `application.yaml`）⇒ ⇒ **那时才必须统一**（当前两个脚本都两个都认 ✅）。
- ② 后人**再犯一次**「同域两种扩展名 ⇒ 会不会互相覆盖」的误判（G2 本轮就差点误判 ⇒ **说明这个误判是真实存在的**）⇒ ⇒ **那时一次性改完 42 处**。
- ③ 若 §21.2 里那 **3 处无人拥有的引用**被合并收口执行者先改掉 ⇒ ⇒ 成本降到 38 处 ⇒ ⇒ **那时再评估**。

### 21.5 🟡 **G2 ④ 的联动登记**：共享层若改命令超时判据 ⇒ **两域必须一起改**

⇒ 判据 = **`LettuceClientConfiguration.getCommandTimeout()`**（值）＋ **`getTimeout()==3000` ＋ `isApplyConnectionTimeout()`**（机制）⇒ ⇒ **不是** `isTimeoutCommands()`（恒真 ⇒ 非有效判据）。
⇒ ⇒ **用同一判据的守门**：**我域 5 份** FastFail 守门（user-api / admin-api / follow-api / user-rpc / admin-rpc）＋ **G2 域 8 条**（`LikeRpcRedisTimeoutsGateTest` 4 ＋ `TaskRedisCommandTimeoutGateTest` 4）⇒ ⇒ **共 13 份跨 2 个域**⇒ ⇒ ⇒ **若共享层改这个判据 ⇒ 两域必须同一次提交一起改** ⇒ ⇒ **已登记为「共享层变更的联动清单」**（与 §13 的必查表并列）。

---

## 22. 🆕 **三路 maven 并发 ＋ 内存账**（2026-10-27 13:35 · G1 lane 实测）—— 并发风险的真实算法

> **触发**：G4 发错峰知会说「从现在起跑第九次全量」⇒ ⇒ **我复检时发现全机有【三条】maven 同时在跑** ⇒ ⇒ 本节把「并发到底有多危险」从「感觉」变成**可算的账**。

### 22.1 🔴 **实测：三条 lane 同时在跑**（判据 = `ps -eo command | grep "[p]lexus.classworlds.launcher.Launcher"`）

| 树 | 命令形态 | `-pl` 覆盖面 |
|---|---|---|
| **G3** | `-B --fail-at-end -am test` | **12 模块**：article ×3 ＋ comment ×3 ＋ favorite ×3 ＋ hot ×3 |
| **G4** | `-o -B -fae -am verify` | **5 模块**：comment-api / comment-rpc / comment-common / comment-proto / favorite-rpc |
| **G2** | `-o -B test` | **9 模块**：`service/common` ＋ message ×3 ＋ like ×3 ＋ task ×2 |

⇒ ⇒ **19 个 java 进程** · **load average 11.76 / 12.19 / 9.70**。

### 22.2 🔴 **内存账（新知识）：「没显式设 `-Xmx`」才是并发 OOM 的真根因**

⇒ **物理内存 24.0 GB** ⇒ ⇒ **JVM 默认堆上限 = 物理内存的 1/4 = 6 GB/条** ⇒ ⇒ **实测三条命令行里 `-Xmx` 零命中** ⇒ ⇒ **堆上限合计 = 18 GB / 24 GB** ⇒ ⇒ 加上 19 个 java 进程的其余开销 ⇒ ⇒ **`EXIT=137`（系统 OOM-kill）的风险是真的**（历史上那组 = 52 模块 `clean verify` ＋ 模块级 `-pl … test` ⇒ **同形态 ⇒ 真的死过**）。
⇒ ⇒ **此刻系统级内存空闲 40%** ⇒ ⇒ **未到临界** ⇒ ⇒ **处置 = 不叫停任何一条（杀了就丢工作）＋ 不再叠第四条 ＋ 下一条显式压堆上限**。

### 22.3 ⇒ ⇒ **可执行的三条纪律（比「等窗口」实在）**

1. 🔴 **每条 maven 显式设堆上限**：`-DargLine=-Xmx2g` ⇒ ⇒ **6 GB → 2 GB ⇒ 三条并发也安全** ⇒ ⇒ **G3 之前的轻量跑本来就写了 `-Xmx768m`** ⇒ ⇒ **这个习惯要推广到「重」的那几条**（重跑尤其要设 ⇒ 因为它是全量 ⇒ 更吃内存 ⇒ 更需要压上限而不是压模块数）。
2. ⚠️ **判据口诀（看三样，不只看一样）**：`ps -eo command | grep "[p]lexus.classworlds.launcher.Launcher"` ⇒ ⇒ ① **树名**（cwd ⇒ 谁在跑）② **完整 `-pl` 列表**（量级）③ **有没有 `-Xmx` / `-DargLine`**（堆上限）⇒ ⇒ **只问「有没有人在跑」是不够的**（本轮就是「三条都以为窗口是空的」）。
3. ⚠️ **「复检 → 起跑」之间有窗口**（实测：我 13:29 复检 = 只有 G3 一条 ⇒ **4 分钟后变成三条**）⇒ ⇒ **起跑后要再复检一次**（十几秒成本，换一个「我以为没人跑」的确定性）。

### 22.4 🔴 **G4 的「第九次全量」与实测命令不符 ⇒ 共享层的「全量回归证据」这一轮【不存在】**

⇒ G4 知会写「**第九次全量（`mvn -B clean verify`，约 20 分钟）**」⇒ ⇒ **实测它那条是** `-pl <5 模块> -am verify` ⇒ ⇒ **`users` / `user` / `admin` / `follow` / `hot` / `like` / `message` / `task` / `article` 的测试类都不在 `-pl` 里 ⇒ 不会跑** ⇒ ⇒ **不是全仓全量** ⇒ ⇒ **这一轮没有「共享层全量回归」这个证据** ⇒ ⇒ **§13.3 的 C7 硬前提因此更远**（⇒ 共享层的全量证据仍停在 `23d1c2a` 那一轮）。
⇒ ⇒ 📌 **这正是 §19 那条教训的复现**：「**`ps` 只证明在跑 maven，不证明在跑全量**」⇒ ⇒ **我上轮已写过这一条**⇒ ⇒ **本轮它自己踩了 ⇒ ⇒ 已请它在 §4.10.26 写清实际覆盖面**。

### 22.5 🔴 **自我订正：「历史上那组真的死过（EXIT=137）」这句的依据是【转述】，不是本地日志证据**

⇒ G3 在回执 ② 里说「`/tmp/g1probe/` 里 `fa_run2.log` / `regress14.log` / `regress2.log` / `regress3.log` / `regress_af.log` 都含 `137`/`Killed` ⇒ ⇒ **你历史上确实撞过 EXIT=137**」。
⇒ ⇒ **本域逐条复核（2026-10-27 13:37）**：
- **真正的 `EXIT=137` / `Killed` 行数 = 0**（`grep -hE "EXIT=137|Killed" /tmp/g1probe/*.log` ⇒ **零命中**）；
- 那些「137」命中**全是裸数字**：时间戳毫秒（`05:33:11.137`）· 端口（`1001x`）· 以及 **`[INFO] Tests run: 137, Failures: 0, Errors: 0, Skipped: 0`**（某次回归正好 137 条，**全绿**）。
⇒ ⇒ **结论：我域历史上【没有】EXIT=137 的日志证据** ⇒ ⇒ **G3 的「你确实撞过」是误读** ⇒ ⇒ **「等 G4 收工」对我域而言【不是】因为「我死过」而必要**。
⇒ ⇒ ⚠️ **但风险判断本身不依赖这条历史** ⇒ ⇒ **并发 OOM 的依据是【算出来的】**（24 GB 物理 ⇒ 默认堆 6 GB/条 ⇒ 三条 18 GB ⇒ load 12）⇒ ⇒ **结论不变，只是依据要写准**。
⇒ ⇒ 📌 **依据纪律**：**上一轮 §22.2 里「历史上那组同形态 ⇒ 真的死过」这半句，依据来自早前的记忆/转述，不是本地日志证据** ⇒ ⇒ **按「不采信转述」纪律在此标注** ⇒ ⇒ 那次事故的**原始记录归 G2**（它是当事 lane）⇒ ⇒ **我域引用时必须写「G2 侧记忆」，不写「已证」**。

### 22.6 🔴 **窗口现状的三条订正（2026-10-27 13:37 实测）** —— 对方的「现状」也可能是【旧快照】

⇒ G3 回执 ① 说「**我不是在跑 maven**（我那轮 12 模块 / 919 条早已收工）⇒ ⇒ **现在我树 maven 进程 = 0**」⇒ ⇒ **实测不成立**：

| 判据 | 13:37:14 实测 | 结论 |
|---|---|---|
| G3 树 maven | **pid 14239 · 已跑 06:00** · `-pl <12 模块> -am test` | 🔴 **G3 正在跑**（6 分钟前才起）⇒ G3 的「进程 = 0」是**旧快照或误读** |
| G2 树 maven | **pid 14587 · 已跑 05:42** · `-pl <9 模块> test` | ✅ 在跑 |
| G4 树 maven | **不在进程表** | ✅ **G4 13:3x 那条（`-pl 5 模块 -am verify`）已收工** |
| `/tmp/g4-full9.log` | **mtime = 10:55**（2h42m 前） | ⇒ G3 说的「G4 10:34 起在跑第 9 次全量、已出 188 个模块汇总行、此刻在 hot-api」= **2h42m 前的旧状态** |

⇒ ⇒ **净结论**：**G4 已收工（窗口曾开过）⇒ 但 G3 此刻正在跑 ⇒ 「G4 收工后你第一个起」这个建议现在【不成立】**（执行者 G3 自己在占）⇒ ⇒ **窗口判断必须由动作方在动作前复检**（§22.3 第 3 条）。
⇒ ⇒ 📌 **且我域【没有】活着的等待器**（`task_query` ⇒ 本会话后台任务全部 `succeeded` ⇒ 零 running）⇒ ⇒ **G3「请取消等待器」这条不适用**（我的两个等待器快照分别停在 12:52:14 / 13:07:41，早已返回）。

### 22.7 🔴 **我域遗留的 12 个对拍进程（17h37m）** —— 占着 12 个端口 ⇒ **暂不杀，先问 G2**

⇒ G3 提醒「你的 5 个 difftest 进程还活着，已跑 14h44m ⇒ 占着端口 ⇒ 你后面起回归/跑探针前先确认它们是否还要用」⇒ ⇒ **本域实测**：

| 项 | 实测 |
|---|---|
| **进程数** | **12 个**（不是 5 个）= **6 个 Go 侧**（`go-user-rpc` / `go-admin-rpc` / `go-message-rpc` / `go-user-api` / `go-admin-api` / `go-message-api`）＋ **6 个 java 侧**（`user-rpc` / `admin-rpc` / `user-api` / `admin-api` / `message-rpc` / `message-api`） |
| **已运行时长** | **17:37:5x**（G3 说的 14h44m 是更早的时刻） |
| **占用端口** | Go 侧 24004/24094/24081/24097/23082/24098/24095/23888/24096/23889/24099/23892 ＋ java 侧 **64210 / 63081 / 64900 / 63889 / 63210** |
| **归属** | **全部由我（G1）一次批量起的**（同一前缀 `/tmp/difftest5k/` ＋ 同一时刻）⇒ ⇒ **但其中 `go-message-rpc` / `go-message-api` / `message-rpc` 三个是【G2 域】的组件** |

⇒ ⇒ **处置：暂不杀** —— 理由 = ① **G2 此刻正在跑 `message-api` / `message-rpc` / `message-common` 的测试**（§22.1）⇒ ⇒ **它可能在用这批进程/端口** ⇒ ⇒ **杀了会打断别人的回归** ⇒ ② G3 也明确「不是我的进程，我不杀」⇒ ⇒ **先问 G2**（问法：**这批还要用吗？不要我一次清掉 12 个**）⇒ ⇒ **回复到达后再清** ⇒ ⇒ **清的动作归我域**（是我起的）。
⇒ ⇒ ⚠️ **纪律**：**「我起的进程」不等于「只有我在用的进程」** ⇒ ⇒ **杀之前先问**（与「不碰别人的进程与端口」同条）。

---

## 23. 🆕 **路径限定规则**（2026-10-27 · G2 提醒 ＋ 本域全量落码）—— 库内路径必须带模块限定

> **G2 的提醒**：users 域台账里裸写的 go-zero **库内**路径（如 `core/stores/redis/...`）会被读成「真源仓里的文件」⇒ ⇒ **真源仓根本没有 `core/` 目录**（本域实测 `ls $GO/core` = 不存在）⇒ ⇒ 建议改成**模块限定路径**。

### 23.1 ✅ **判据（实测，不是推的）**

⇒ **go-zero@v1.10.2 的顶层目录**（`/Users/pr/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2`）= **`core` `gateway` `internal` `mcp` `rest` `zrpc`**（+ 若干文件）。
⇒ **真源仓（`Sea-RideTheWind_golang`）的顶层** = **只有 `service/`** ⇒ ⇒ `rest/` `zrpc/` `core/` `encoding/` `internal/` **在真源仓里都不存在**。
⇒ ⇒ **判据**：凡路径首段 ∈ {`core` `rest` `zrpc` `gateway` `mcp`} 且**不在 `service/` 之下** ⇒ ⇒ **必是 go-zero 库内路径** ⇒ ⇒ 必须带 `go-zero@v1.10.2/` 前缀。

### 23.2 ✅ **本轮落码：142 处 / 11 份文件 / 136 行**（纯替换，insertions = deletions）

| 文件 | 处数 | 文件 | 处数 |
|---|---|---|---|
| `users/user/api.md` | 62 | `users/vs-common.md` | 8 |
| `follow/api.md` | 18 | `users/_shared.md` | 7 |
| `users/admin/api.md` | 16 | `users/user/rpc.md` | 5 |
| `users/admin/rpc.md` | 13 | `follow/vs-common.md` | 4 |
| `users/common.md` | 4 | `users/admin/_shared.md` | 1 |
| `follow/rpc.md` | 4 | | |

⇒ ⇒ 统一成 **`go-zero@v1.10.2/<path>`（斜杠式）** ⇒ ⇒ 与 global 台账的既有写法**逐条同形**（`docs/DRIFT/global/common-tails.md` · `cycle-deps.md` · `lanes/L5-signoff.md` 全用斜杠式）⇒ ⇒ **自检：0 处拼接残留**（`go-zero@v1.10.2/go-zero` 等四种畸形全 0 命中）。

### 23.3 🔴 **刻意【不】改的三类（判据写死，防后人「顺手统一」误改）**

| 类 | 处数 | 为什么不动 |
|---|---|---|
| **真源仓的 `internal/...`** | **63** | 真源仓结构 = `service/<域>/<组件>/internal/{logic,model,handler,types,config,svc,metrics,middleware,server}/...` ⇒ ⇒ 残留段的分布（`logic` 28 · `model` 10 · `types` 7 · `handler` 7 · `metrics` 3 · `config` 2 · `svc` 2 · `middleware` 2 · `server` 1）**全部是组件内路径** ⇒ ⇒ 台账语境已标明组件 ⇒ **不是「不知哪里的文件」** |
| **Go 标准库 `encoding/json`** | 10 | **不是** go-zero 的（`encoding` 不在 go-zero 顶层目录清单里）⇒ ⇒ 加前缀是错的 |
| **`breaker/latch`** | 1 | 是**概念描述**（「给 breaker/latch 的 map 设上限」）⇒ ⇒ **不是文件路径** |

⚠️ **`internal/` 是唯一「真源仓与 go-zero 同名」的顶层目录** ⇒ ⇒ 所以它**不能机械替换**，必须逐处判 ⇒ ⇒ 本轮逐处判完（63 + 2 = 65 处）⇒ ⇒ 2 处 `internal/devserver/server.go` 判为 **go-zero**（真源仓**根本没有** devserver 目录 ⇒ 实测 `find $GO -type d -name devserver` = 零命中）⇒ ⇒ 已加前缀。

### 23.4 🔴 **G2 的清单规模与我域差 13 倍（域内订正不能外推的又一例）**

⇒ G2 列了 **11 处**（只覆盖 `admin/rpc.md` 5 ＋ `user/api.md` 3 ＋ `user/rpc.md` 3）⇒ ⇒ **本域实测 142 处**（含 **follow 域 26 处**，**它完全没提 follow**）⇒ ⇒ **规模差 ≈ 13 倍** ⇒ ⇒ **别把「我列了 11 处」当成「只有 11 处」**。

### 23.5 🟡 **G2 引的「global 台账 E43/E46 的纪律」编号对不上**

⇒ 实查 `docs/DRIFT/global/vs-common.md` ⇒ **E43/E46 是「漂移条目表」**（`E46 / E62` = 超时 deadline 与毫秒精度）⇒ ⇒ **不是**路径纪律条目。
⇒ ⇒ **但纪律的【内容】成立**（global 的 `common-tails.md` / `cycle-deps.md` / `L5-signoff.md` 实际写法就是 `go-zero@v1.10.2/<path>` 斜杠式）⇒ ⇒ **我按【实际写法】办**（不按编号）⇒ ⇒ **已回问 G2 那个编号的真实出处**。

---

## 24. 🟢 **Redis 池形态自检**（G2 ② 的取值规则在我域 = **不适用** ⇒ 零动作）

⇒ **我域 5 个用 Redis 的组件**（`user-api` · `admin-api` · `follow-api` · `user-rpc` · `admin-rpc`）逐个核：

| 面 | 实测（5 个组件） | 结论 |
|---|---|---|
| `min-idle` / `max-idle` | **5/5 零配置** | ⇒ ⇒ G2 的「**若写了 `min-idle` ⇒ 应取 go-zero 的 8**（不是 4）」⇒ **在我域【不适用】**（我域压根没写）⇒ ⇒ **无动作** |
| `pool.enabled` | **5/5 零配置** | ⇒ ⇒ Boot 4 判据 = `enabled != null ? enabled : COMMONS_POOL2_AVAILABLE`（**由 classpath 决定，不是只读 yml**） |
| `commons-pool2` 依赖 | **5 个 `pom.xml` 全部零命中** | ⇒ ⇒ ⇒ **池形态 = 无池** ⇒ ⇒ 且**因为没写 `enabled: true`** ⇒ ⇒ **不会触发「缺 commons-pool2 ⇒ 启动期 `NoClassDefFoundError`」** ✅ |
| **行为等价性** | Lettuce **无池时用 shared connection** ⇒ 连接**天然复用** | ⇒ ⇒ **与 go-zero 的 `MinIdleConns = 8` 池在「可观察行为」上等价**（差别只在连接数/性能）⇒ ⇒ **不是语义漂移** ⇒ ⇒ **零动作** |

⇒ ⇒ **结论：G2 的取值规则（Go 侧有确切值 ⇒ 取 Go 的确切值）我域【用不上】**（我域没有对应的可配项）⇒ ⇒ **但 Boot 4「池形态由 classpath 决定」这条纪律我域已核（5/5）** ⇒ ⇒ **已登记**，将来若有人想加 `min-idle` ⇒ **必须同时查 pom 里的 `commons-pool2`**，否则「加一个依赖」就会**静默改变池形态**。

---

## 25. 🟢 **测试口径边界（G2 ③）**：我域**零产物层 IT** ⇒ `mvn test` 口径 == 全量口径

⇒ `find service/users service/follow -name "*IT.java" -o -name "*ITCase.java"` ⇒ **0 命中** ⇒ ⇒ **我域无产物层 IT 类** ⇒ ⇒ ⇒ **`mvn test` 阶段跑的就是全部测试** ⇒ ⇒ **本域 667 条【无需】加「不含 IT」的边界说明**。
⇒ ⇒ 且本域**已按 G2 建议的方式采信**（**maven 模块汇总行 == surefire XML 逐文件求和 == 667** ⇒ 两口径相等）⇒ ⇒ ✅ 符合。
⇒ ⇒ 📌 **口径纪律的由来（G2 侧）**：产物层 IT 绑 surefire 的**第二个 execution**（`phase=verify`）⇒ ⇒ **`mvn test` 根本不跑它、报告目录里也没有它的 XML** ⇒ ⇒ **「目录求和 − N」里的 N 未必是 IT**（也可能是**守门类自己的用例**）⇒ ⇒ **跨轮比较条数前必须先核「被排除的是什么」**。

---

## 14. 🆕 **守门卫生缺陷：「可达 ⇒ 能启动」用例的 3s 预算过紧 ⇒ 偶发红**（2026-10-27 · G1 lane）

> **本节记的是本域自己的测试缺陷**（不是产品缺陷、也不是漂移）⇒ **落点全在 G1 写权内，已自修**。

### 14.1 现象与定性（本轮实测，三步定位）

| 步 | 观察 | 证据 |
|---|---|---|
| ① | 本域全量回归 **admin-api 红 1 条**：`AdminApiStartupFailFastTest.startsWithGlobalClientInterceptor` | `regress16.log`：`Tests run: 6, Failures: 1` |
| ② | **同一测试类单独跑 ⇒ 6/6 全绿**（`BUILD SUCCESS`） | `single_admin.log` ⇒ ⇒ **不是产品缺陷，也不是环境缺失** |
| ③ | **两轮的测试类执行顺序逐条一致** ⇒ ⇒ 排除「顺序干扰」 | `regress14.log` 与 `regress16.log` 的 `Running com.rag.users.admin.api.*` 序列**完全相同** ⇒ ⇒ **是偶发（flaky）**，不是必然 |
| ④ | root cause = `IllegalStateException: Can't connect to channel admin-rpc`（`AbstractChannelFactory.connectOnStartup`）⇒ **3s 内没到 READY** | `regress16.log` 的 `Caused by` 链 |

⇒ ⇒ **定性：守门自身的预算过紧** —— 该用例要验的是「**可达 ⇒ 不该启动失败**」，
而它给 channel 的预算与**生产值**同为 **3s** ⇒ 在**冷 JVM**（netty 初始化 + 类加载）＋**满负载**机器上，
**TCP + HTTP/2 握手**可能超 3s ⇒ ⇒ **偶发红**。

### 14.2 修法（**不削弱任何断言**）

| 项 | 处理 | 为什么 |
|---|---|---|
| 「**不可达 ⇒ 启动失败**」两条用例 | **保持 `immediate-connect-timeout=3s` 不动** | 它们要验的正是「连不上就快速失败」⇒ 3s 是**被测语义本身**；且 TCP refused 是**立即**返回 ⇒ 不受影响 |
| 「**可达 ⇒ 能启动**」两条用例 | 放宽到 **`10s`** | 这两条**不验预算**，只验「不该启动失败」⇒ 放宽**不损失覆盖** |
| **3s 这个生产值本身** | 由同类的 `applicationYmlArmsImmediateConnectTimeout`（**读 `application.yml` 的静态断言**）继续守着 | ⇒ ⇒ **生产值仍有断言覆盖** ⇒ 不存在「顺手把 3s 改成 10s 就没人管了」的缺口 |

⇒ ⇒ **已修的 3 个文件**（各 2 处）：
`service/users/user/user-api/.../UserApiStartupFailFastTest.java` ·
`service/users/admin/admin-api/.../AdminApiStartupFailFastTest.java` ·
`service/follow/follow-api/.../FollowApiStartupFailFastTest.java`（见 [`follow/vs-common.md` §12](../follow/vs-common.md)）

### 14.3 ⚠️ 元教训（**可复用到别的域**）

> **「跑绿」与「跑红」都要先问「是谁的错」** ——
> ① 全量红 ⇒ 第一反应是「我改坏了」⇒ **本轮我的改动只碰了 javadoc** ⇒ 排除；
> ② **隔离跑绿** ⇒ 立刻排除「产品缺陷 / 环境缺失」⇒ 指向**测试自身**；
> ③ **顺序一致仍红** ⇒ 排除「顺序干扰」⇒ 指向**偶发**；
> ④ 读 **`Caused by`**（不是只读 `Tests run:` 那一行）⇒ 直接拿到「预算耗尽」这个机制。
>
> ⇒ ⇒ **守门红 ≠ 被测对象坏**：还有第三类 ——「**守门自己的预算/前提不成立**」⇒ 与「守门的期望值过期」同族。
> ⇒ **修法原则**：**放宽守门自己的预算，不改被测语义**；且必须确认「被测的那个值」**另有断言覆盖**（本例：`application.yml` 的静态断言）。

---

## 15. 🆕 **口径坑：探针源文件删了，`surefire` 报告还在 ⇒ XML 求和比 maven 多算**（2026-10-27 · G1 lane）

> ⚠️ **本条建议归档到 `docs/Pitfalls/`** —— 但 `docs/Pitfalls/**` **不属任何 lane 的写权面**（`check-wave-lane-ownership.py` 的白名单里只有 `docs/DRIFT/<域>/**`）⇒ ⇒ **请合并收口执行者归档**；此处先留在本域台账。

### 15.1 现象

本域 6 组件全量回归 **两口径不一致**：

| 口径 | 结果 |
|---|---|
| ① maven 自身模块汇总行 | **667**（user-api 121 · user-rpc 143 · admin-api 140 · admin-rpc 117 · follow-api 68 · follow-rpc 78） |
| ② `target/surefire-reports/TEST-*.xml` 逐文件求和 | **668**（**user-api 122**） |

⇒ ⇒ **差 1 条，且只差在 user-api** ⇒ ⇒ **第一反应不该是「maven 漏算了」**。

### 15.2 归因

⇒ 逐个 XML 报告比对「报告里的类名 ↔ `src/test` 里的类名」⇒ 找到 **1 份孤儿报告**：

```
TEST-com.rag.users.user.api.ScratchTimeoutOptionsProbeTest.xml   （1 条，mtime 12:09）
  ⇔ 源文件已删（早前探针实验的临时测试类，探针跑完即删）⇒ 报告成了孤儿
```

⇒ ⇒ **机制**：`mvn test` **不会清理**上一轮的 `surefire-reports` ⇒ ⇒ **探针类删了源、报告仍在** ⇒ ⇒ **XML 求和口径把它也算进去**。
⇒ ⇒ 本轮它之所以还在，是因为**早前的探针是在同一个模块里建了真·测试类**（不是 `/tmp` 脚本）⇒ ⇒ **「探针放 `/tmp`」这条纪律同时保护了仓库和口径**。

### 15.3 处置与判定式

| 动作 | 结果 |
|---|---|
| 删掉 1 份孤儿报告（`target/` 内，不进版本库） | ✅ |
| **重新两口径交叉验** | ✅ **667 = 667**，failures/errors/skipped 全 0 ⇒ **吻合** |

⇒ ⇒ **判定式（两条口径不一致时，先查什么）**：
1. **先查报告新鲜度**（`mtime` ＋ 类名是否还在 `src/test` 里）⇒ **不要先怀疑 maven 漏算**；
2. ⇒ 口径二**只有在「报告目录已清」时**才与口径一等价 ⇒ ⇒ **跨轮比较测试数时，必须先 `rm -rf target/surefire-reports`**（否则数字不可比）；
3. ⇒ **「探针类建在模块里」是口径污染源** ⇒ 探针要么放 `/tmp`，要么**连报告一起删**。

---

## 16. 🆕 **G4 第七次回执（2026-10-27）· A2 邻接口径订正 ＋ Redis 收编删除的前置判定**（G1 lane）

> **G4 的回执规格**：HEAD `5564c27`（tip `cec7131`）· 基线 `a282012` 之上 30 提交 · 工作区干净 · **未 push**
> · 全量 `mvn -B clean verify` ⇒ **BUILD SUCCESS / `MVN_EXIT=0`** · **52 模块 / 2457 条**（failures 0 · errors 0 · **skipped 4**）· 20:28 min
> · 双侧探针同批复跑：`run-parity.sh` **EXIT=0 · 56 例**（一致 54 / 不一致 0 / 已签字偏离 2）· `run-rpc-timeout-parity.sh` **EXIT=0 · 3 例** · 三门禁 0/0/0

### 16.1 🔴 **Redis 域内副本是 5 份，不是 3 份**（G4 回执里的名单是**扩容前**的）

| # | 副本 | 何时落的 |
|---|---|---|
| 1 | `user-api` | 既有（第九轮 `DRIFT-USERAPI-014`） |
| 2 | `admin-api` | 第十四轮 `DRIFT-ADMINAPI-012` |
| 3 | `follow-api` | 第十四轮 `DRIFT-FOLLOW-012` |
| **4** | **`user-rpc`** | **第十四轮 `DRIFT-USERRPC-V-014` ②** |
| **5** | **`admin-rpc`** | **第十四轮 `DRIFT-ARPC-013` ②** |

⇒ ⇒ 第 4、5 份正是**rpc 侧同款形态**（断连排队 3s ≫ `rpc-timeout: 2s` ⇒ **RPC 先超时 ⇒ 客户端 1015**）⇒ ⇒ **它们是收编的原始动机之一**。
⇒ ⇒ **只删 3 份 ⇒ `user-rpc` / `admin-rpc` 仍被遮蔽 ⇒ 共享层对这两个域仍静默不生效** ⇒ **已回信要求 G4 把「3 份」改成「5 份」再转合并收口执行者**。

### 16.2 🔴 **删除的前置判定：本轮【不能】执行**（不是保守，是前置未满足）

> 🟢 **2026-10-27 订正**：本节的**结论**（当轮不能删）当时正确，但**执行方式**后来变了 ——
> ⇒ **不需要等共享层先进主干**，只需**把 G4 分支合进本树** ⇒ ⇒ 收编**已完成**（见 §26）。
> ⇒ ⇒ **本节以下的「不能执行」是历史快照，不是现状。**

⇒ 实测：`git merge-base --is-ancestor 5564c27 do-my-own-gh` ⇒ **NO**（tip `cec7131` 也未进）⇒ ⇒ **本树里没有 `GoStyleRedisClientAutoConfiguration`**。
⇒ ⇒ **现在删 = 不是「双份变一份」，而是「零份」** ⇒ `REJECT_COMMANDS` 整个消失 ⇒ 退回本轮修掉的漂移：
- **api 侧**：断连排队 3s ⇒ 撞 HTTP 3s ⇒ **503**（Go 是 fail-open **200**）；
- **rpc 侧**：断连排队 3s ≫ `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ **客户端 1015**（Go 是快速失败 ⇒ `INTERNAL`）。

⇒ ⇒ **我域 5 份 FastFail 守门会立刻变红**（它们断言的正是「立即失败」那一半）⇒ ⇒ **删除 + 守门重写必须同一次提交**。
⇒ ⇒ **正确顺序**：**合并收口执行者把 G4 分支合进 `do-my-own-gh` ⇒ 我把主干并回我分支 ⇒ 一次性执行**（配方见 §12.2）。

### 16.3 🟡 **A2 邻接：`@GrpcClient` 出向调用面**（G4 逐个 rpc 计数 ＋ 六种出向方式交叉核）

| 本域组件 | `@GrpcClient` 计数 | 判定 |
|---|---|---|
| `follow-rpc` | **1** | **可观测** |
| `user-rpc` | **0** | **N/A** |
| `admin-rpc` | **0** | **N/A** |

⇒ ⚠️ **轴归属**：这是「**出向调用面**」的口径，**≠ A2 本体**（A2 = 服务端三拦截器链 recover/breaker/shedding）⇒ ⇒ **记作 A2 邻接，不并进 A2 本体的计数**
⇒ ⇒ 本域 **A2 本体**的 OBSERVABLE DIFFERENCE 计数 = **0**（`DRIFT-USERRPC-V-007` 已于 2026-10-26 由 `global` 域销账 · 已合回 `4f839f9`）⇒ ⇒ **A2 本体 = ✅ 一致（该证据强度下）**。

### 16.4 ✅ G4 ⑥（池自愈）与我的登记**同构**（它证实了我域台账 §12.4）

⇒ G4 独立得出「前五轮全量一直绿，是因为 #8 的缺陷恰好把保护罩挡住了 ⇒ `RpcInterceptor.shutdown()` **从未在纯 gRPC 进程里真正执行过**」
⇒ ⇒ **我域此刻的绿也是这么撑着的** ⇒ ⇒ **不能把当前全绿读作「我域不受影响」** ⇒ ⇒ 已在 §12.3 / §12.4 写明「合入后必查 3 条」＋「两条必须同批合入」。

---

## 17. 🆕 **G3 回执的交叉验证（2026-10-27 · G1 lane）** —— 四个口径的自核结果

> **性质**：本节全是**「对方说的 ⇒ 我自己再核一遍」** ⇒ ⇒ **不是采信转述**（AGENTS.md §3.1）。
> ⇒ 其中 ① ④ 两条构成**跨域交叉验证**（两个 lane 独立得出同一结论）⇒ ⇒ **证据强度比单方字节码核验更强**。

### 17.1 ✅ 交叉验证①：**Boot 4 的 `connect-timeout` 不回落** —— 两域独立同向

| 来源 | 核法 | 结论 |
|---|---|---|
| **G1（我域）** | Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` 字节码：`if (connectTimeout != null) { builder.socketOptions(…connectTimeout(…)); }` ⇒ **null ⇒ 整段跳过** | 不配 ⇒ 落 `SocketOptions.DEFAULT_CONNECT_TIMEOUT = 10s` |
| **G3（独立域）** | 同字节码 offset 9→31 核法 ＋ 用 Boot 3.2.5 字节码对照（**Boot 2/3 才是两属性共用**） | 同上 |

⇒ ⇒ **两侧独立同向** ⇒ ⇒ 本仓的「原写『回落 timeout=3s』是错的」这条订正**不再只是单方结论** ⇒ ⇒ 可交叉引用。
⇒ ⇒ **我域现状（字节级读真实 yml，不是台账快照）**：**5/5 已合规** ⇒

| 组件 | `timeout` | `connect-timeout` |
|---|---|---|
| `user-api` | `3s`（`application.yml:25`） | `5s`（`:34`） |
| `admin-api` | `3s`（`:37`） | `5s`（`:44`） |
| `user-rpc` | `3s`（`application.yaml:55`） | `5s`（`:67`） |
| `admin-rpc` | `3s`（`application.yml:61`） | `5s`（`:71`） |
| `follow-api` | `3s`（`:40`） | `5s`（`:47`） |

⇒ ⇒ **本域不需要域内自修、也不需要给 G4 派单**（`user-rpc` 那份是第十四轮 `DRIFT-USERRPC-V-014` 补的；api 侧本来就有）。

### 17.2 ✅ 交叉验证②：**Go 侧 HTTP 预算是 3s，不是 article 的 70s 例外**

⇒ 真源三份 api yaml 的 `Timeout` 键数：`usercenter.yaml` **0** · `admincenter.yaml` **0** · `followcenter.yaml` **0**
⇒ ⇒ 真源**不写** `Timeout` ⇒ 走 go-zero `rest/config.go:55` 的 `default=3000` ⇒ **3s** ⇒ ⇒ **我域不是 70s 例外**（那个例外全仓唯一，在 article）。
⇒ ⇒ 附带：rpc 侧 yaml 同样**零** `Timeout` 键 ⇒ 同样 3s。

### 17.3 🟢 **U+FFFD：我域现在只剩 1 处，且正是「故意」那处**

| 位置 | 性质 |
|---|---|
| `service/users/user/user-api/src/test/java/com/rag/users/user/api/UserApiGoStyleBodyContentTypeTest.java:376` | ✅ **故意**（`assertFalse(body.contains("�"))` 的**被测对象**）⇒ **不动** |
| `docs/DRIFT/users/user/rpc.md:2464` / `:2461` | ✅ **故意**（台账对历史损坏的**引述** ＋ 明写「不动」那行）⇒ **不动** |

⇒ ⇒ **3 处真损坏已在 `0c2a902` 修掉**（`UserRpcLoginNullStatusReadsAsZeroTest.java:44`→「就是」· `AvatarHistoryIgnoresBodyTest.java:61`→「真容器脚本」· `AvatarHistoryService.java:32`→「根本不读 body」）⇒ ⇒ **字节级扫描（`b'\xef\xbf\xbd' in read_bytes()`）现为 1 处命中**。
⇒ ⇒ ⚠️ **G3 给的 4 处清单是「我修复前」的旧扫描** ⇒ ⇒ 已回信订正（并附我域的当前状态）。
⇒ ⇒ **分类纪律（与 G3 同款）**：「**全仓 U+FFFD 归零」不是合法目标** ⇒ **3 类故意**：被测对象 ＋ 码点钉死 ＋ 坑档/台账里的故障样本。

### 17.4 ⚠️ **两款 flake 的根因层不同 —— 不可并成「同款」**

| 域 | 机制 | 根因层 | 状态 |
|---|---|---|---|
| **like-rpc / comment-rpc / article-rpc**（G3 域） | 端口一探通就 `destroyForcibly()` ⇒ drain 线程还没读到「gRPC Server started」 | **就绪探测** | G2 写权面，G3 未动 |
| **user-api / admin-api / follow-api**（我域） | 「**可达 ⇒ 能启动**」用例把 `immediate-connect-timeout` 设成**生产值 3s** ⇒ 冷 JVM ＋ 满负载下 TCP+HTTP/2 握手超 3s | **连接预算** | ✅ 已修（放宽到 10s；见 §14） |

⇒ ⇒ 我域 `service/{users,follow}/**` 实测 `destroyForcibly` = **0 命中**、`"gRPC Server started"` = **0 命中** ⇒ ⇒ **G3 那款机制不适用我域** ⇒ ⇒ **两者只是「同属偶发红」，不是「同款」** ⇒ ⇒ 跨域台账里**分开记**（否则会把「就绪探测」的修法套到「连接预算」上）。

---

## 18. 🆕 **G3 第八次回执（2026-10-27 · G1 lane）** —— 一处数字订正 ＋ 四项独立复核

> **来源**：G3（`mvs_ba99d8078fd94de681ce8068c21ee76a`）主动回我上一轮的四点 ＋ 一处数字订正。
> **本节的每一个数字都经过本域独立重扫**（不采信转述）；凡「对方给的」与「我实测的」不一致 ⇒ **两条都写下来**并已回问。

### 18.1 🔴 **订正：我台账里的「G3 的 29 模块回归」是「两处口径串台」**

| 原写 | 实测 | 结论 |
|---|---|---|
| 「G3 的 **29** 模块回归」（[`admin/rpc.md` §12.6](admin/rpc.md) ＋ [`user/rpc.md` §9](user/rpc.md)） | G3 的域级回归 = **12 个模块**（4 域 × 3 组件：article / comment / favorite / hot 各 api+rpc+common） | 🔴 **作废「29」** ⇒ 「**29**」是 **G4 第八次全量的「有测试模块数」**（52 模块 / 29 个有测试模块 / 2461 条）⇒ **G4 的口径串到了 G3 头上** |

- **独立核验**（2026-10-27 13:1x · G3 树）：域内 12 个模块**逐个有 surefire 报告** · **129 份** · **0 份陈旧**（mtime 全部 ≥ 12:30）⇒ 「12 模块」成立。
- ✅ **错峰结论不变**（本域确实等 G3 跑完才起 ⇒ 无 `EXIT=137`）⇒ **只订正数字，不动结论**。
- ⇒ 📌 **教训（可复用）**：「**29**」这个数是「G4 全量的有测试模块数」，不是「G3 的模块数」⇒ **引用别的 lane 的数字前，先问「这个数是哪一次、哪个口径的」**。

### 18.2 ⚠️ **G3 报的「919 条」vs 我实测「922 条」⇒ 差 3，已回问口径**

- G3 报：12 模块 / **919** 条 / 0 failures / 0 errors / 4 skipped / 9:44 min。
- 我实测（G3 树 · XML 逐文件求和 · 只算域内 12 模块）：**922** 条 · skipped **4** ✅ 一致 · **0 份陈旧报告** ⇒ 差 **3** 条。
- ⇒ **我这边没有任何残差可解释这个 3** ⇒ ⇒ **不回问之前不下结论** ⇒ 已把「922 / 129 份 / 0 陈旧 / skipped 4 一致」这条原始判据回给 G3，请它给口径（maven 汇总行 vs XML 逐文件求和）。
- ⚠️ **注意方向**：这与我在 §15 记的坑**方向相反** —— §15 是「XML 比 maven **多**算（孤儿报告）」，这次是「XML 比 maven **多** 3 条而 maven 少算」⇒ ⇒ **两个口径谁对，仍要靠「报告新鲜度 + 逐模块明细」判，不能靠方向猜**。
- 🟢 **2026-10-27 订正（G3 已答 ⇒ 本条关闭）**：**差 3 = 跑次不同**，不是口径不同 ——
  `919` 是 **D10 落码【前】**那一跑次，**`comment-rpc` 116→119**（D10 落码后新增 3 条）；
  G3 按同口径（域内 12 模块 · 逐个 `TEST-*.xml` 求和）实测它**当前树** = **922**（129 份 / skipped 4）
  ⇒ ⇒ **与我上面记的 922 一致** ⇒ ⇒ 「未解释的 3」**已解释** ⇒ 本条**关闭**（原文保留不改写）。

### 18.3 🔴 **G3 树当前有 2 个红报告（13:07 / 13:08）⇒ 它的「0 failures」已被打破**

| 报告 | mtime | 失败用例 | 失败形态 |
|---|---|---|---|
| `service/article/article-rpc/…/ArticleRpcRuntimeBootGateTest.xml` | **13:07:25** | `realFatJarProcessBootsAndListens`（`ArticleRpcRuntimeBootGateTest.java:159`） | JUnit `AssertionFailureBuilder` ⇒ **`assertTrue` 的 message 为空** |
| `service/comment/comment-rpc/…/CommentRpcRuntimeBootGateTest.xml` | **13:08:29** | `realFatJarProcessBootsAndListens`（`CommentRpcRuntimeBootGateTest.java:159`） | 同上 |

- ⇒ 这两条**正是 §17.4 记的「就绪探测 flake」族**（`article-rpc` / `comment-rpc` 与 `like-rpc` 同款）⇒ ⇒ **我域那款（连接预算）不同源**，两边**仍分开记**。
- ⇒ ⚠️ **「0 failures」有保质期**：G3 报的那个 0 对应 **12:35~12:43** 那轮；**13:07/13:08 的定向重跑把这两条打红了** ⇒ ⇒ **台账里的「0 失败」必须带跑次与时间**，否则下一轮读的人会当成现状。
- ⇒ **本轮只登记，不代陈断红**（那是 G3 写权面）⇒ 已回信告知路径与 mtime。

### 18.4 ✅ **Go 侧 yaml 推导链：G3 的逐条断言独立复核成立**（含 70s 例外与易混点）

| 文件 | `Timeout` 键 | 复核结果 |
|---|---|---|
| `service/article/api/etc/article-api.yaml` | `:4` = **70000** | ✅ 与 G3 一致 ⇒ **article-api 才是 70s 例外**（我域不是） |
| `service/article/rpc/etc/article.yaml` | `:3` = **70000**（http 预算）· `:90` = **10000** | ✅ **G3 的易混点提醒成立**：`:90` 落在 **`SecurityConf:`** 段（段头在 `:85`）⇒ 是 **security-rpc 客户端超时**，**不是** http 预算 |
| `service/user/user/api/etc/usercenter.yaml` | **0** | ✅ 我域 ⇒ go-zero `rest/config.go:55` `default=3000` ⇒ **3s** |
| `service/user/admin/api/etc/admincenter.yaml` | **0** | ✅ 同上 ⇒ **3s** |
| `service/follow/api/etc/followcenter.yaml` | **0** | ✅ 同上 ⇒ **3s**（⚠️ 真实路径是 `service/follow/api/etc/followcenter.yaml`，**不是** `service/follow/follow/api/…`） |

⇒ ⇒ **我域三份 api yaml 全部 0 个 `Timeout` 键** ⇒ ⇒ **零 `Timeout` 键 ≠ 无超时，而是走 struct tag 默认值**（默认值有行号可指 ⇒ 不是推测）⇒ ⇒ **我域 http = 3s、rpc = 2s**（`go-zero@v1.10.2/zrpc/config.go:31` `default=2000`）。

### 18.5 🔴 **G4 第八次全量口径（取自 G4 自己台账 `23d1c2a`，非转述）＋ 🔴 实测：G4 的全量【不含 G1 提交】⇒ 不能当 G1 证据**

**G4 自己台账记的口径**（`23d1c2a` → `docs/DRIFT/global/vs-common.md`）：

| 项 | 值 |
|---|---|
| 结果 / 耗时 | `BUILD SUCCESS` / `MVN_EXIT=0` · **20:21 min**（09:57:42 → 10:18:03） |
| 模块数 / 有测试模块数 | **52** / **29** |
| 用例数 | **2461**（29 行「模块名 ↔ 数字」明细逐项求和） |
| failures / errors / skipped | **0 / 0 / 4**（`favorite-api` 1 · `favorite-rpc` 3） |
| 相对上一轮 | 第七次 2457 ⇒ **+4**（= FB-04 守门 4 条；`service/common` **307 → 311**）⇒ 其余 28 模块逐条一致 |
| 双侧探针（同批次） | `run-parity.sh` ⇒ `EXIT=0` · **56 例**（一致 54 · 不一致 0 · 已签字偏离 2）· `run-rpc-timeout-parity.sh` ⇒ `EXIT=0` · **3 例** |

🔴 **但那份逐模块明细里【我域的 6 个组件】是旧基线** —— 本轮实测：

| 组件 | G4 第八次全量 | 我树 `fa608f3` 实测 | 差 |
|---|---|---|---|
| `user-api` | 107 | **121** | −14 |
| `user-rpc` | 133 | **143** | −10 |
| `admin-api` | 129 | **140** | −11 |
| `admin-rpc` | 112 | **117** | −5 |
| `follow-api` | 50 | **68** | −18 |
| `follow-rpc` | 74 | **78** | −4 |
| **合计** | **605** | **667** | **−62** |

- **根因（已直接验）**：**G4 的树里没有 G1 的任何提交** ⇒ `fa608f3` / `cbf8af8` / `0c2a902` / `b7d724c` / `af0a532` / `ce0c339` / `e8b428f` **7 笔全部 `NOT_IN_G4`** ⇒ ⇒ G4 跑全量时，工作区里是 **G4 分支的基线**，**不含 G1 的新守门与新测试**。
- **佐证（测试类文件数）**：`user-api` 18→22 · `user-rpc` 17→21 · `admin-api` 13→17 · `admin-rpc` 20→22 · `follow-api` 3→8 · `follow-rpc` 10→11 ⇒ 共 **+12 个测试类** ⇒ 与「条数差 62」方向一致。
- ⇒ ⇒ **结论（写进 C7 的硬前提）**：**G4 的 lane 级全量数字不能当作「G1 域内已合入状态」的回归证据** ⇒ ⇒ **波末收口回归（C7）必须在【所有 lane 合入主干之后】再跑一次全量**（见 §13.3）。
- ⚠️ **G4 已跑到第十一次**（2471 条 · 代码态 `040a201`）⇒ 我上一轮 pending 的「第八次」**已过期**；**§4.10.26 是前向引用、尚未落** ⇒ ⇒ **我的「窗口期零 maven」承诺继续有效**。
- 🔴 **订正（2026-10-27 13:2x · G4 槽位回执）**：上面那句「**G4 此刻正在跑第十二次全量**」**是错的 ⇒ 已撤**。
  - **我当时的推断** = 「`§4.10.26` 尚未落 ＋ `ps` 显示 `g4-service-common` 在跑 maven ⇒ ⇒ 正在跑第十二次全量」。
  - **G4 回执 ⑥ 的实测** = **本轮【未跑全量】**（理由：零生产代码改动 ⇒ 其余 51 个模块产物与行为不受影响 ＋ 第八次全量刚跑过且全绿 ⇒ 不重复 20 分钟全量）⇒ ⇒ **它当时跑的是模块级 `mvn -o -pl service/common test`（315 条）**。
  - ⇒ ⇒ **根因 = 我把「代理指标」当成了「被测属性」**：`ps` 只能证明「**这个树在跑 maven**」，**不能**证明「**在跑全量**」（模块级与全量级在 `ps` 里长得一样）⇒ ⇒ 与「`grep -c` 计数会自匹配」同族 ⇒ ⇒ **判据要能区分「跑什么」**：全量有 `mvn ... clean verify` / `-pl` 列表长度 / `Reactor Summary` 行数可查，模块级没有。
  - ⇒ ⇒ **「§4.10.26 尚未落」的**事实**仍成立**（G4 确实还没跑第十二次全量）⇒ ⇒ **我域台账不写「正在跑」这种动态断言**，只写「**截至 <时间戳> 尚未发生**」⇒ ⇒ **动态状态必须带时间戳**。

### 18.6 🔴 **Redis 收编删除的前置复检：仍未满足 ⇒ 5 份域内副本继续保留**

> 🟢 **2026-10-27 订正：本节的前置判定已被【执行方式】推翻 ⇒ 收编【已完成】（见 §26）**
> ⇒ ⇒ **执行方式 = 把 G4 分支合进本树**（而不是等共享层先进主干）⇒ ⇒ **5 份域内副本已删除**。
> ⇒ ⇒ **本节以下的「仍未满足 / 继续保留」是历史快照，不是现状。**

- 实测我树 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` ⇒ **只有 2 条**：`com.rag.common.observability.GoStyleOtelAutoConfiguration` · `com.rag.common.http.GoStyleJsonContentTypeAdvice` ⇒ ⇒ **无 `GoStyleRedisClientAutoConfiguration`**。
- ⇒ ⇒ 与 §12.1 / §16.2 的判定一致：**删除前置仍未满足** ⇒ ⇒ **现在删 5 份 = `REJECT_COMMANDS` 整个消失** ⇒ ⇒ **本轮仍不执行删除**（等 G4 分支进主干 → 并回我分支 → 一次做完「删 5 份 + 重写 5 份守门」）。
- ✅ **G3 侧的「0 份域内副本 ⇒ 收编零动作」我认可**（它域内 `LettuceClientOptionsBuilderCustomizer|RedisDisconnectedBehaviorConfig|DisconnectedBehavior` 在源码里 0 命中）⇒ ⇒ **收编在我域是 5 份、G3 域 0 份** ⇒ ⇒ **两边都要在合并时各自复验「共享层那份真的生效」**。

### 18.7 ⚠️ **「现在全机只有 G2 在跑 maven」已过期 —— 快照纪律又一次生效**

- G3 在回信里写「**现在全机只有 G2 在跑 maven**」⇒ ⇒ **13:14:35 我复检 = 只有 `g4-service-common` 在跑** ⇒ ⇒ **该快照在我读到它时就已经过期**（与 §13.2 记的「实测 4 秒即过期」同族）。
- ⇒ ⇒ **再次确认**：**任何跨 lane 的窗口判断都必须由动作方自己在动作前复检** ⇒ **别人给的「现在没人跑」不能当凭据**。
- ⇒ ⇒ **本域窗口期承诺继续有效**（零 maven，含单模块定向跑）⇒ **直到 G4 的下一次全量收工**（🔴 **不要把「第十二次全量」当成已发生** ⇒ 见 §18.5 的订正 ＋ §19.3）。

---

## 19. 🆕 **G4 槽位协调回执（2026-10-27 · G1 lane）** —— 一处漏项 ＋ 一处**我的结论被推翻**

> **来源**：G4（`mvs_e8063ac67c654150ab7797b8116ef0a1`）回我更早那条「Filter 槽位协调约束」。
> **本节每个数字都两侧实测过**（不采信转述）⇒ 而且**其中一条推翻了我上一轮台账的推断**。

### 19.1 ✅ **「6 个共享层 Filter」在【我树】成立 ⇒ 但【G4 分支】是 7 个 ⇒ 合入后我域面对 7 个**

| 代码态 | Filter 数 | 槽位 | 3s 预算起点行号 |
|---|---|---|---|
| **我树** `align/g1-users-follow` @ `544e310` | **6** | +3 / +4 / +5 / +6 / +7 / +8 | `HttpMiddleware.java:304`（`begin`）· `:336`（`clear`） |
| **G4 树** `align/g4-service-common` @ `cd893d1` | **7** | **+2** / +3 / +4 / +5 / +6 / +7 / +8 | `HttpMiddleware.java:324`（`begin`）· `:356`（`clear`） |

- **实测方式**：逐个 `*Filter.java` 读 `@Order(...)` 的**字面值**（不是靠台账转述）。
- ⇒ **`GoStylePathNormalizingFilter`（+2）由 G4 的 `9690b04` 引入，而 `9690b04` 不在 `do-my-own-gh` ⇒ 我树里没有这个文件** ⇒ ⇒ **「6 vs 7」= 代码态差，不是我漏数**。
- ⇒ ⇒ **但 G4 的提醒有实质效力**：**合入主干后，我域的重排清单必须把 +2 一并算进去**（漏了它 ⇒ 重排后 `PathNormalizing` 留在 +2 ⇒ 它**只包装 request、不短路、不产生响应头** ⇒ 影响可控，但仍是「被刻意排在那儿」的）⇒ ⇒ **已把 +2 行补进 §6.4.2 的槽位表**（并标注「合入后适用」）。
- ⇒ ⇒ ✅ **我域「+6 必须 < +7」的推导仍成立**（+2 在区间外）⇒ ⇒ **域侧槽位约束不受影响**。

### 19.2 ✅ **G4 ③「+7 = 3s 预算起点」独立复核成立**（两侧各读一遍）

- 预算的 `begin` / `clear` **都在 `HttpMiddlewareFilter` 的链内**（我树 `HttpMiddleware.java:304` / `:336`；G4 树 `:324` / `:356`）⇒ ⇒ 排在 `HttpMiddlewareFilter` **之后**的 Filter 都会**落进 3s 预算内** ⇒ ⇒ **「域侧 +6 必须 < +7」成立**。
- ⚠️ **跨树行号差 20 行** ⇒ ⇒ **凡引他树 `文件:行号` 必须同写该树的 commit id**（`HttpMiddleware.java:324` 只在 G4 的 `cd893d1` 上有效）⇒ **本条已按此纪律写进 §6.4.2 的双代码态表**。
- ✅ **「`MaxBytes`(+8) 是唯一排在 +7 之后的共享层 Filter」两侧都成立** ⇒ ⇒ **要挪它，必须同一次提交改：共享层 `@Order` ＋ G4 的槽位快照守门快照 ＋ 台账登记理由** ⇒ ⇒ **签字比改码便宜 ⇒ 优先走签字**（落点 = 签字档 ⇒ **合并收口执行者写权面**）。

### 19.3 🔴 **订正我上一轮的一条推断：「G4 正在跑第十二次全量」⇒ 不成立**

- G4 回执 ⑥ 的实测 = **本轮未跑全量**（理由：零生产代码改动 ＋ 第八次全量刚跑过且全绿）⇒ ⇒ **它跑的是模块级 `mvn -o -pl service/common test`（315 条）**。
- ⇒ ⇒ **我错在把「代理指标」当「被测属性」**：`ps` 只能证明「**这个树在跑 maven**」，**不能**证明「**在跑全量**」⇒ ⇒ **模块级与全量级在 `ps` 里长得一样** ⇒ ⇒ 判据必须能区分「跑什么」（全量有 `clean verify` / `-pl` 列表长度 / `Reactor Summary` 行数可查）。
- ⇒ ⇒ **「§4.10.26 尚未落」这个事实仍成立**（G4 确实还没跑第十二次全量）⇒ ⇒ **我域台账从此只写「截至 <时间戳> 尚未发生」，不写「正在跑」这种动态断言**。
- ⇒ ⇒ ✅ **G4 本轮的落码是「零生产代码改动 + 1 个新测试文件」**（`SharedFilterOrderSnapshotGateTest` 4 条 ＋ 3 组变异全验证）⇒ ⇒ **共享层 Filter 顺序没被动 ⇒ 我域的槽位约束没被打破** ⇒ ⇒ **我域零改动**。

### 19.4 🔴 **V-002：我域「#6 第 1 步」【取消】⇒ G4 不需要为本单落 DDL 那步**

- G4 回执 ⑧：「等你的 **#6 第 1 步**（`admin-rpc` 的 `UserEntity.status` 能表达 NULL）⇒ 之后我落 DDL 那步 ＋ 跑全量 ＋ 回执」。
- ⇒ ⇒ **这一步基于的修法已被我域的逐层证据链推翻**（见派单表 #6 行）⇒ ⇒ **正确处置 = 维持 RAG NOT NULL** ⇒ ⇒ **落 DDL 那步会把 RAG 改成可空 = 制造新漂移**（RAG `callSettersOnNulls=false` ⇒ NULL 时跳过 setter ⇒ 读出 `null` ≠ Go 的 `0`）⇒ ⇒ **本条落点 = 无动作**。
- ⇒ ⇒ **本轮又核到一条更强的论据**（原台账标着「Go 侧行号本轮未核」⇒ **已核**）：Go `service/user/user/rpc/internal/model/data_model.go:11` ⇒ `Status int64 \`gorm:"column:status;default:0"\`` ⇒ **值类型 · 非指针** ⇒ ⇒ **GORM 对值类型字段恒写 0 ⇒ Go 侧这一列实际上永远不出现 NULL** ⇒ ⇒ **DDL 声明差异、行为零差异**这条结论**更硬了**。
- ⇒ ⇒ **剩下的唯一落点 = 销号签字**（`docs/Intentional_Deviations/**` ⇒ **不在 G1 写权面**，也不在 G4 写权面）⇒ ⇒ **需要合并收口执行者拍板** ⇒ ⇒ **G4 不必为这条再做任何事**。
- ⚠️ **机制教训（跨 lane 协作的通用坑）**：**发出去的消息不会自己更新** ⇒ ⇒ 我改了自己的结论（V-002 订正）⇒ ⇒ **对方树上的台账是旧的**（实测：G4 树里 `docs/DRIFT/users/vs-common.md` **连「V-002」都零命中** ⇒ 它那份是合并收口执行者 sweep 那版 ⇒ **完全不含 G1 lane 的任何内容**）⇒ ⇒ **对方很可能还在按我更早那条消息里的旧单等我** ⇒ ⇒ **改了结论必须主动通知，不能指望对方去读文件**。


## 26. 🆕 **共享层 Redis 收编【已落码】（2026-10-27 · G1 lane）** —— 前置怎么被满足 ＋ 一条**方法论订正**

### 26.1 🔴 **前置不是「等主干」，是「把 G4 分支合进本树」** —— 实测零冲突

- 我域原判（§16.2 / §18.6）：**「删除的前置 = 共享层那份先进主干 + 并回本树」⇒ 本轮不能执行**。
- ⇒ **本轮实测把这条判据的【执行方式】推翻了**（**结论不变，前提变了**）：
  - `git merge --no-ff --no-commit align/g4-service-common`（tip `dcd0706`，51 笔不在主干）
  - **merge-base `a282012`** · **冲突面实测 0**（`git diff --name-only --diff-filter=U` 空）
  - **合并态下 5/5 门禁全 EXIT=0**（lane-ownership / role-model / coupling / doc-links / ledger-count）
  - ⇒ **合并后 `AutoConfiguration.imports` = 6 条**（合并前 **2 条**）⇒ 共享层那份到位 ⇒ **收编删除的前置在本树内即可满足**
- ⇒ ⇒ **落点从「等合并收口执行者合主干」变成「本 lane 自行合并 G4 分支」** ⇒ 与 G2（`37816ca`）/ G3（`cd893d1`）**同一步骤** ⇒ ⇒ **三棵树现在都有共享层**。
- ⚠️ **这不改变 C7 的硬前提**：收口回归**仍必须**在所有 lane 合入主干后跑（§13.3）—— **合并 G4 分支 ≠ 合并进主干**。

### 26.2 🟢 落码前的**逐字核对**（不采信转述）

| 项 | 共享层那份 | 我域 5 份副本 | 判定 |
|---|---|---|---|
| 注解 | `@AutoConfiguration` + `@ConditionalOnClass` + `@Bean` + **`@ConditionalOnMissingBean`** | `@Configuration` + `@Bean`（**无** `@ConditionalOnMissingBean`） | ⇒ **共享层那份会因域内副本在场而退让**（遮蔽非叠加） |
| bean 名 | `goStyleRedisClientOptionsCustomizer` | `goStyleRedisDisconnectedBehavior` | 收编后**改名** ⇒ 守门必须按新名断言 |
| customizer 体 | `builder -> builder.disconnectedBehavior(REJECT_COMMANDS)` | **同一行表达式** | ✅ **逐字同值** |
| `timeoutOptions` | **零触碰** | **零触碰** | ✅ 同值 |

⇒ ⇒ **删除副本对行为零变化**（不是「差不多」，是**同一行表达式**）⇒ ⇒ 收编的净效果 = **把断连策略的维护点从 5 处收到 1 处**。

### 26.3 🟢 落码动作清单（已完成）

1. **删 5 份域内副本**：`user-api` / `user-rpc` / `admin-api` / `admin-rpc` / `follow-api` 各 1 份 `config/RedisDisconnectedBehaviorConfig.java`。
2. **改 5 份守门**，各补 **2 条 bean 身份断言**（收编的验收判据）：
   - `context.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` ⇒ **`size() == 1`**；
   - 该 map 的 key 含 **`goStyleRedisClientOptionsCustomizer`**。
3. **2 份 rpc 守门**：`@Import(域内副本)` ⇒ `@ImportAutoConfiguration(GoStyleRedisClientAutoConfiguration.class)`。
4. **3 份 api 守门**：**不改任何注解**（真实 `@SpringBootApplication` ⇒ 清单自动生效）。

⚠️ **框架机制必须验证实际生效（§3.3.1）** —— 落码前逐条实测「清单会真的被读」：

| 判据 | 实测（6 个组件） |
|---|---|
| 启动类含 `@EnableAutoConfiguration` | ✅ 6/6（`@SpringBootApplication` 自带） |
| 无人重写 `AutoConfigurationExcludeFilter` / `TypeExcludeFilter` | ✅ **零命中** |
| yaml 无 `spring.autoconfigure.exclude` | ✅ **零命中** |
| api 组件扫 `com.rag.common` ⇒ 共享层那份会被 `AutoConfigurationExcludeFilter` 排除出扫描 | ✅ ⇒ **只经清单注册一次**（不双重注册） |

### 26.4 🔴 **rpc 侧与 api 侧的装配形态不同 ⇒ G4 台账「连带删 `@Import`」这条不够**

- G4 `docs/DRIFT/global/vs-common.md:1513` 写的是「删掉 `RedisDisconnectedBehaviorConfig` 类 ⇒ `@Import` 指向不存在的类 ⇒ 编译断裂 ⇒ **必须连带删 `@Import`**」。
- ⇒ **「删」不够**：2 份 rpc 守门用的是 `@SpringBootConfiguration`（**不含** `@EnableAutoConfiguration`）
  ⇒ ⇒ **`AutoConfiguration.imports` 清单根本不会被读**
  ⇒ ⇒ 单纯删掉 `@Import` ⇒ **容器里 customizer 数量 = 0** ⇒ **守门静默测不到任何断连策略**
  ⇒ ⇒ 正确处置 = **换成 `@ImportAutoConfiguration(GoStyleRedisClientAutoConfiguration.class)`**（已实施）。
- ⇒ ⇒ **api 侧更进一步**：3 份 api 守门用真实 `@SpringBootApplication` ⇒ **连注解都不用改**。
- ⚠️ **台账订正（follow 侧）**：`docs/DRIFT/follow/vs-common.md` §11.2 原写「`FollowApiRedisDisconnectedFastFailTest` 是『显式 `@Import` 域内副本』⇒ 副本一删就编译不过」
  ⇒ ⇒ **实测该守门 `@Import` 零命中** ⇒ **该前提不成立**（我把 rpc 侧的结构套到了 api 侧）⇒ ⇒ **已订正**。
  ⇒ ⇒ **教训**：**同一机制在不同组件上的装配形态可以不同** ⇒ 逐条实测，别拿一处的结构推另一处。

### 26.5 🔴🔴 **方法论订正：`-pl` 不带 `-am` ⇒ 跑的是本地仓库里的旧 `com.rag:common` ⇒ 「共享层合入对我域零回归」原本【未被证明】**

- **现象**：第一次跑收编后回归 ⇒ `MVN_EXIT=1`，编译错误 **`程序包 com.rag.common.redis 不存在`**。
- **根因**：`mvn -pl <6 模块> test`（**不带 `-am`**）⇒ reactor 里**只有那 6 个模块** ⇒ `com.rag:common` **从本地仓库解析** ⇒ 用的是 `~/.m2/.../common-0.0.1-SNAPSHOT.jar`（**12:00 的旧 jar**）。
- **实测该 jar 的代码态**：`com/rag/common/redis/` 条目 = **0**；G4 新增的 4 个类（`GoStylePathNormalizingFilter` / `MybatisPlusOrderingBridgeAutoConfiguration` / `GoStyleRouteTemplateResolver` / `GoStyleRedisClientAutoConfiguration`）**全部不在** ⇒ ⇒ **那个 jar = 主干代码态**（主干也没有这些类）。
- ⇒ ⇒ **订正上一轮的说法**：「合并后我域回归 667 条全绿 ⇒ 共享层合入对我域零回归」**不成立** —— 那次跑的是**主干代码态的共享层** ⇒ **共享层改动根本没进过我的测试 classpath**。
- ⇒ ⇒ **限定条件（更精确，不是全盘否定）**：此前所有 `-pl` 不带 `-am` 的回归数字，**只在「共享层 = 主干代码态」这个前提下有效**（对**我域**行为而言成立）；**一旦共享层代码变了，就必须带 `-am` 重跑**。
- ⇒ ⇒ **正确命令**（本轮用这条，全绿）：
  ```bash
  mvn -B -o -DargLine=-Xmx2g -am -pl <6 模块> test
  ```
- ⚠️ **与「lane 级全量 ≠ 别的 lane 的证据」同族，但更隐蔽**：那条是「**别的 lane 的树**不含我的提交」；这条是「**连共享层都不是我这份树**」⇒ ⇒ **两者都会让「跑绿」变成假绿** ⇒ ⇒ **判据：跑之前先问「这次回归的 classpath 上，`com.rag:common` 是哪一份？」**

### 26.6 🟢 **收编后回归（带 `-am`）—— 我域 667 条 + 共享层 324 条，全绿**

| 口径 | 数字 |
|---|---|
| 我域 6 组件（101 份 surefire XML 逐文件求和） | **667** / 0 failures / 0 errors / 0 skipped |
| `service/common`（`-am` 带进来，61 份报告） | **324** / 0 / 0 / 0 |
| 合计 | **991** 条 · **BUILD SUCCESS** · **06:47 min** |
| 报告新鲜度 | 168 份本轮新生成 · **陈旧 0 份** |

⚠️ **口径边界**：`-am` 把 18 个模块拉进 reactor（`admin-proto` / `users-common` / `common` / `user-proto` / `follow-common` …）⇒ ⇒ **「18 个模块」不是「测了 18 个域」** ⇒ 本域数字**只按 6 个组件的 surefire 目录统计**。

### 26.7 🟡 **变异验证（收编后守门不是假绿）**

| 变异 | 手段 | 预期 | 实测 |
|---|---|---|---|
| **MUT-1**（rpc） | 从 `UserRpcRedisDisconnectedFastFailTest` 去掉 `@ImportAutoConfiguration(GoStyleRedisClientAutoConfiguration.class)` | 守门必须红 | 🔴 **2/2 用例红** —— 用例 1 红在 `expected: <REJECT_COMMANDS> but was: <DEFAULT>`（机制腿）；用例 2 红在 `实测耗时 3013ms`（排队到 commandTimeout 3s）⇒ ⇒ **守门确实在盯「共享层那份」**。⚠️ **诚实注**：这次变异里**新加的两条 bean 身份断言没被执行到**（用例 1 先红就停 ⇒ 后续断言被跳过）⇒ ⇒ **必须再补 MUT-3 单独验它们**（见下） |
| **MUT-2**（api） | 以 `-Dspring.autoconfigure.exclude=com.rag.common.redis.GoStyleRedisClientAutoConfiguration` 跑 `UserApiRedisDisconnectedFastFailTest` | 机制腿必须红（读到 `DEFAULT`） | 🔴 **2/2 用例红** —— 用例 1 红在 `expected: <REJECT_COMMANDS> but was: <DEFAULT>`；用例 2 红在 `实测耗时 3018ms` ⇒ ⇒ **api 侧的守门确实在盯「清单有没有把共享层那份装上」**。⚠️ **第一轮这个变异「没生效」**：我把属性塞进 `-DargLine='-Xmx2g -Dspring.autoconfigure.exclude=…'` ⇒ **属性没进测试 JVM** ⇒ 用例照常绿（2/2 绿）⇒ ⇒ **改用直接 `-D…` 才生效** ⇒ 又一个「**参数没到它该到的地方**」的实例 |
| **MUT-3**（rpc · 验新断言） | 在 `UserRpcRedisDisconnectedFastFailTest` 里装回一个「域内副本替身」（`@Configuration` + 同名 bean `goStyleRedisDisconnectedBehavior` + 值仍是 `REJECT_COMMANDS`）⇒ 模拟「**收编没做**」 | **机制腿应仍绿**（副本也提供 `REJECT_COMMANDS`）＋ **名字断言必须红** | 🔴 **2 条用例只红 1 条** —— 红的正是**名字断言**：`那一份必须是共享层的 GoStyleRedisClientAutoConfiguration（bean 名固定为 goStyleRedisClientOptionsCustomizer）⇒ 实测 [goStyleRedisDisconnectedBehavior] ==> expected: <true> but was: <false>`；**机制腿仍绿** ⇒ ⇒ **证明「收编真的发生」这条判据能【单独】抓住「收编没做」**，而机制腿抓不住（它只知道值对）⇒ ⇒ **两条断言不是冗余，各有分工** |

⇒ ⇒ **三个变异的分工（合起来才完整）**：**MUT-1 / MUT-2 证「机制腿有牙齿」（共享层那份没装 ⇒ 守门红）**；**MUT-3 证「收编判据有牙齿」（域内副本还在 ⇒ 名字断言红，而机制腿绿）** ⇒ ⇒ **只做 MUT-1 会漏掉「收编没做」这个真正的失败模式**。
⇒ ⇒ **为什么 MUT-2 用「运行期排除」而不是改源码**：**共享层在 G4 写权面 ⇒ 域侧不能改它的源码做变异** ⇒ ⇒ 改走**运行期属性**（`spring.autoconfigure.exclude` 对清单注册的自动配置有效）⇒ ⇒ **零共享层写入**。
⇒ ⇒ **MUT-1 的对照意义**：它证明 **rpc 侧的装配方式本身**是承重的 —— 若有人把 `@ImportAutoConfiguration` 换成「直接删掉」（G4 台账的原建议），**守门会立刻变红** ⇒ ⇒ 守门能挡住「静默测不到任何东西」。
⇒ ⇒ **还原纪律**：三次变异全部用「**备份文件 + md5 双向校验**」还原（不用 `git checkout --`）⇒ 还原后 `git diff` 为空 ⇒ ⇒ **变异零残留**。

### 26.8 🟡 **U+FFFD：我域台账里有 13 处「故意引述」⇒ 合并回主干后全仓清单 20 → 23 份**

- **逐处核实（字节级，2026-10-27 14:1x 全树重扫）**：我域写权面 **4 份 / 14 处** U+FFFD ⇒ **真损坏 = 0**：
  - `service/users/user/user-api/src/test/java/.../UserApiGoStyleBodyContentTypeTest.java:376`（1 处 · **被测对象**：`assertFalse(body.contains("�"))`）
  - `docs/DRIFT/users/vs-common.md:1390`（1 处 · 引述上面那个被测对象）＋ `:1635`（1 处 · 本节再次引述同一被测对象）
  - `docs/DRIFT/users/user/api.md:3457/:3453/:3457/:3458`（7 处 · 引述 + 记录「末字已不可恢复」）
  - `docs/DRIFT/users/user/rpc.md:2464/:2461`（4 处 · 同上）
- ⚠️ **一个自指的坑（写这条时才发现）**：**每在台账里引述一次那个坏字节，台账自己就多一处 U+FFFD** ⇒ ⇒ **登记本身会自我增长** ⇒ ⇒ 收敛办法 = **登记时不再引述新的坏字节**（只引行号 + 说明，不贴字节）⇒ 本节最终落在 **14 处**（其中 13 处在**台账**里，1 处在**源码**里）。
- ⇒ ⇒ **我域那 3 份源码**（`AvatarHistoryService.java:32` / `AvatarHistoryIgnoresBodyTest.java:61` / `UserRpcLoginNullStatusReadsAsZeroTest.java:44`）**合并取我树版本**（我树 `0c2a902` 已修，字节级 0 命中）。
- ⇒ ⇒ **合并回主干后，全仓清单 = 23 份 / 64 处**（主干 20 份 / 51 处 ＋ 我域带进的 **3 份台账 / 13 处**）⇒ ⇒ **复扫要比「文件清单」而不是比份数**。
- ⚠️ **口径实例（这次的版本比上次更狠）**：**我树 18 份 / 52 处 vs 主干 20 份 / 51 处** ⇒ ⇒ **份数差 2、处数差 1、构成完全不同** ⇒ ⇒ 「总量相同 ≠ 构成相同」这条**不够用** —— 这次的教训是**两个口径（份数 / 处数）都会变**，⇒ ⇒ **只能比文件清单**。

### 26.9 🟡 **12 个遗留对拍进程：改判【不杀】**（订正 §23 的清理计划）

- **实测**：12 个全在（17h49m）· **合计 RSS 仅 0.3 GB**（java 侧 5 个 222 MB + go 侧 6 个 72 MB）⇒ **内存零成本**。
- **决定性理由**：这 12 个（user-rpc / admin-rpc 的 Go 侧 + java 侧）**是我域 `DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013` 两条 L3 条目升 L4 的唯一活体环境** ⇒ ⇒ **杀掉就把升 L4 的条件一起杀了** ⇒ ⇒ **保留**。
- ⇒ **G2 域那 4 个**（message 侧）**一并保留**（G2 已发收工信号 ⇒ 不再有新需求；但它若要再对拍就还在）⇒ 已去函告知。
- ⚠️ **订正一处**：上一轮我说「端口 Go 侧 24004/… + java 侧 64210/63081」⇒ **本轮用 `lsof` 按 jar 名 grep 落了空**（`lsof` 的 COMMAND 列是 `java` 不是 jar 名）⇒ ⇒ **端口清单未经 lsof 复核** ⇒ 记为**未验证**，不作证据。

### 26.10 🔴 **收编的连带后果：`check-wave-coupling.py` 红了 —— 但红的是【共享面的文档快照】，不是本域代码**

- **现象**：收编落码后跑 5/5 门禁 ⇒ **`check-wave-coupling.py` EXIT=1**（其余 4 个门禁全 EXIT=0）。
- **守门报的具体漂移**：`common_radiation` ⇒ 文档快照 `None` vs 仓库现状 `['users']`。
- **逐项复核（`--print` 与文档快照做 JSON 逐键对比）** ⇒ **差异精确到一项**：
  | 快照项 | 结论 |
  |---|---|
  | `grpc_edges` | ✅ **一致** |
  | `pom_dependents` | ✅ **一致** |
  | `users_common_classes` | ✅ **一致** |
  | `cross_domain_topics` | ✅ **一致** |
  | `common_radiation` | 🔴 **只多一个键**：`'GoStyleRedisClientAutoConfiguration': ['users']` ⇒ 其余 **40 个类的辐射度全部逐值一致** |
- ⇒ ⇒ **根因 = 收编的【预期后果】**：删掉 5 份域内副本 ⇒ 断连策略改由共享层那份提供 ⇒ ⇒ **`users` 域第一次出现在 `GoStyleRedisClientAutoConfiguration` 的辐射度里** ⇒ ⇒ **这正是「收编真的发生了」的正面证据**（不是缺陷）。
- ⚠️ **口径注记（别把辐射度当受益面）**：`scan_common_radiation` 的判据是 **源码 `import` 语句**（扫 `service/**/src/**/*.java`，**含测试源码**）⇒ ⇒ `['users']` 的来源是**我域 5 份守门里的 `@ImportAutoConfiguration(GoStyleRedisClientAutoConfiguration.class)`**（测试源码 import）⇒ ⇒ ⚠️ **它不等于「运行时受益面」** —— 运行时受益面是 **10 个组件**（G4 `§4.10.24.6` 已列），其中 G2/G3 那些**零 import、靠自动配置清单命中**的组件**不会出现在辐射度里** ⇒ ⇒ **辐射度与受益面是两个口径，不要互相引用**。
- 🔴 **落点不在本域写权面**：`docs/DRIFT/_cross/cross-domain-coupling.md` ∈ `check-wave-lane-ownership.py` 的 **`SHARED` 无人拥有清单**（⇒ 归合并收口执行者）⇒ ⇒ **G1 不能写**，且 **G4 也不能写**（同一份清单对所有 lane 一视同仁）⇒ ⇒ **只能由合并收口执行者更新**。
- ⇒ ⇒ **守门自己给出的处置**也是「先更新文档快照（`--print`）再谈分波」⇒ ⇒ **新的期望值已实测出来**（`common_radiation` 加 `'GoStyleRedisClientAutoConfiguration': ['users']` 一项，其余不动）。
- ⚠️ **为什么必须转派、不能拖**：**主干上的 `_cross` 快照仍是收编前的版本** ⇒ ⇒ 各 lane 合并回主干后，**主干上的守门仍然红** ⇒ ⇒ **必须在合并前由合并收口执行者更新**，否则「合并后全绿」这一步会被这个共享面卡住。
- ⚠️ **口径纪律（我自己踩了一次）**：**判「报告是否陈旧」的阈值必须取自「本次运行的起止」，不能随手取一个钟点** ⇒ ⇒ 本轮我先取 `14:11`（以为 common 段在那之后跑）⇒ 误报「**65 份陈旧报告**」⇒ ⇒ 实测 common 段跑在 **14:10:01~14:10:46**（本轮 14:09:50 起）⇒ ⇒ **正确阈值下：168 份本轮新生成 / 0 份陈旧**。
- 🔴🔴 **守门自检的写法纪律（本轮差点交出假绿）** —— **门禁「5/5 全 EXIT=0」这句话本身也会骗人**：
  - **坏写法**：`python3 "$g" >/dev/null 2>&1; printf "  %s EXIT=%s\n" "$(basename $g)" "$?"`
    ⇒ ⇒ **`$(basename $g)` 这个命令替换会先执行 ⇒ 把 `$?` 覆盖成 `basename` 的退出码（恒 0）**
    ⇒ ⇒ **打印出来的 EXIT 永远是 0** ⇒ ⇒ **红门禁被报成绿**（本轮 `check-wave-coupling.py` 就是这样被报成 `EXIT=0` 的）。
  - **好写法**：`python3 "$g" >/dev/null 2>&1; rc=$?; printf "  %s EXIT=%s\n" "$g" "$rc"`
    ⇒ ⇒ **先捕获退出码，再做命令替换**。
  - ⚠️ **对照**：把 `$(basename $g)` 放在**重定向目标**里（`> /tmp/$(basename $g).out`）是**安全的** ⇒ 因为它在 `python3` 之前展开，**不覆盖 `$?`** ⇒ ⇒ **危险的不是「命令替换」，是「命令替换与 `$?` 读同一批参数」**。
  - ⇒ ⇒ **可复现的反证**：坏写法下 `check-wave-coupling.py` 打印 `EXIT=0`，好写法打印 `EXIT=1` ⇒ ⇒ **同一个门禁、同一份代码、两种结论** ⇒ ⇒ **凡是要用退出码下结论，先跑一遍反证**。

## 27. 🆕 **G4 第十次全量前的窗口协调 ＋ 它 §4.10.27 的 R1~R4 复核（2026-10-27 · G1 lane）**

### 27.1 🟢 **窗口协调：全量回归零占用**

- G4 知会：「starting the 10th full regression（`mvn -B clean verify`，~20 min）on `align/g4-service-common` @ `5a954bb` ⇒ 请暂缓任何全量回归」。
- ⇒ ⇒ **我域全量回归零占用**：我域本轮无全量待跑（收编验收已完成，见 §26.6）⇒ ⇒ **已回函确认**。
- ⇒ ⇒ 本轮唯一在跑的是**单测试类**（`GoStyleRedisClientShadowingGateTest`，3 条）的**域内定向跑** —— 按 G4 明示的「域内跑可以」执行，**显式 `-DargLine=-Xmx2g`**（堆上限 2 GB）⇒ ⇒ **不构成全量**。
- ⇒ ⇒ **起跑前复检**：两条占用（G2 的 `-pl service/common,service/like/like-rpc` + G4 的 `clean verify`）⇒ **两条都带 `-Xmx`** ⇒ 堆受限 ⇒ `memory_pressure` 44% free ⇒ ⇒ 并发安全（判据按「条数 × 1/4 物理内存」算 + 实际空闲双查，见 §23）。

### 27.2 🔴 **G4 §4.10.27 的 R1~R4 复核结论：我域四条【全部已修】—— 它那张表读的是主干代码态**

- **G4 的转派单**（它的表里 10 个组件逐项 YAML 解析）⇒ 其中 **R1~R4 四条落在我域**。
- **我域实测（YAML 解析器读值，不是 grep）**：

| 组件 | 我域现态 | 主干现态 | G4 表里写的 | 判定 |
|---|---|---|---|---|
| `user-api` | 3s ＋ **connect-timeout 5s** | 3s ＋ **缺** | 🔴 连接 = 10s ⇒ R1 | ❌ **已修** |
| `user-rpc` | 3s ＋ **connect-timeout 5s** | 3s ＋ **缺** | 🔴 连接 = 10s ⇒ R2 | ❌ **已修** |
| `follow-api` | **3s** ＋ **5s** | **2s** ＋ **缺** | 🔴🔴 命令 2s ＋ 连接 10s ⇒ R3 | ❌ **已修** |
| `admin-rpc` | 3s ＋ 5s（**注释已订正**） | 3s ＋ 5s（**注释机制写错**） | 「值对、注释错」⇒ R4 | ❌ **已修** |
| `admin-api` | 3s ＋ 5s | 3s ＋ 5s | ✅ 一致 | ✅ 一致 |

- ⇒ ⇒ **G4 描述的那 4 种形态逐字对应【主干】** ⇒ ⇒ **根因 = G4 的树不含 G1 的任何提交**（已实测：G4 树里 `docs/DRIFT/users/vs-common.md` 连「V-002」都零命中）⇒ ⇒ **不是 G4 判错，是它看不到我域的修复**。
- ⇒ ⇒ **我域这四条的修法与出处**：`DRIFT-USERAPI-016`（user-api 补 `connect-timeout: 5s` ＋ `UserApiRedisTimeoutConfigTest` 2 例）· `DRIFT-USERRPC-V-014` ①（user-rpc 同款）· `DRIFT-FOLLOW-011`（follow-api `timeout` **2s → 3s** ＋ 补 5s ＋ `FollowApiRedisTimeoutConfigTest` 2 例）· `DRIFT-ARPC-013` 订正块（admin-rpc 注释的**字节码级**订正：Boot 4.0.8 `createClientOptions` 在 `connectTimeout == null` 时**整段跳过** ⇒ 用 Lettuce 默认 10s ⇒ 「回落到 timeout」是错的）。
- ⚠️ **R3 那个「需 G1 自行判定是否刻意」的开放项 —— 我域已判定：不是刻意**：
  - Go 侧生效的是 **go-redis v9 的库默认 `ReadTimeout = 3s`**（真源 `go.mod` pin `go-redis/v9 v9.20.0`）⇒ ⇒ **Go 侧本来就是 3s**；
  - ⇒ 而那个 2s **疑似抄自** go-zero `core/stores/redis/redis.go:27` 的 `readWriteTimeout = 2 * time.Second` ⇒ ⇒ 那个常量是**死常量**（全文件仅此一处、**从未被使用**）⇒ ⇒ **不能当依据** ⇒ ⇒ 故按 3s 对齐。
  - 🟢 **2026-10-27 交叉验证（G3 引用了 `rest/config.go:55` ⇒ 独立复核后判定：那是【另一层】，不替代本条）**
    - G3 给的出处 = go-zero `rest/config.go:55` `Timeout int64 \`json:",default=3000"\`` ⇒ 那个字段在 **`rest.RestConf`** 里 ⇒ 是 **HTTP server 的请求超时**，**不是 Redis 的**。
    - 🔴 **go-zero 的 `core/stores/redis/conf.go` 的 `RedisConf` 根本没有 `Timeout` 字段**（实测：只有 `Host` / `Type` / `User` / `Pass` / `Tls` / `NonBlock` / `PingTimeout`）⇒ ⇒ **Go 侧 Redis 命令超时根本不由 yaml 控制** ⇒ ⇒ 也**不可能**走 `rest` 的默认值。
    - 且 go-zero `core/stores/redis/redis.go` **全文无 `ReadTimeout`** ⇒ ⇒ **不覆盖** go-redis 的默认 ⇒ ⇒ 真值就是库默认。
    - ✅ **真源 pin 的版本**（`go.mod:15` = `github.com/redis/go-redis/v9 v9.20.0`）实测 `options.go:364-370` ⇒ `case 0: opt.ReadTimeout = 3 * time.Second`；`:338-339` ⇒ `opt.DialTimeout = 5 * time.Second` ⇒ ⇒ **命令 3s ＋ 连接 5s，与 RAG 侧逐条对齐**。
    - ⇒ ⇒ **两边数字都对，但指的是【两个不同的量】**：HTTP 预算 3s（go-zero `rest` 默认）≠ Redis 命令超时 3s（go-redis 库默认）⇒ ⇒ **本条 R3 的出处【保持不变】**，G3 的引用**不能**替代它（AGENTS.md §3.3.1：「必须读到**消费**该 tag 的那段控制流」⇒ 配置文件的内容是**意图**，不是**行为**）。
- ⇒ ⇒ **R1~R4 我域零动作**（已修，不是待办）⇒ ⇒ 已回函告知 G4，并附「我域现态 vs 主干现态」对照表，请它按我域最新提交复核。

### 27.3 🟢 **G4 的新机器门禁已并入我树，且我域通过**

- G4 `22dc67e` 新增 `service/common/src/test/.../redis/GoStyleRedisClientShadowingGateTest`（3 条）⇒ **把「不得新建域内 Redis 副本」从台账禁令落成机器门禁**。
- ⇒ **我树再并一次 G4 分支**（`87d72e0`，比上次的 `dcd0706` 多 9 笔）⇒ **冲突面实测 0** ⇒ **我域写权面零变更** ⇒ 带进 2 个文件（G4 的 global 台账 +258 行 ＋ 新守门）。
- ⇒ ⇒ **域内定向验证：3 条全绿**（`service/common` · `-Dtest=GoStyleRedisClientShadowingGateTest` · `-DargLine=-Xmx2g` · BUILD SUCCESS）⇒ ⇒ **我域收编后这道机器门禁通过**（域内零副本）。
- ⇒ ⇒ **守门的设计值得记**：它的 `stillExistingWhitelistEntries` 只取**文件仍存在**的白名单项 ⇒ ⇒ **我删掉副本后那半条自动为空 ⇒ 不会红**；且另有 `staleWhitelistHint` 提示「收编已完成、该清白名单了」。
- 🟡 **白名单清理是 `service/common` 的写权 ⇒ 只能 G4 清**（已转派）⇒ 我域零动作。

### 27.4 🟢 **#6 第 1 步：G4 已撤回 ⇒ 与我域结论一致**

- G4 `745a8b2`「撤回 #6 的『两步方案』与『需 Jason 拍板』那条裁决题 —— 它的前提已被 GORM 源码证伪」⇒ ⇒ **与我域的结论一致**（落 DDL = RAG 可空 = **方向相反的新漂移**）⇒ ⇒ **G4 不必为本单再做任何事**。
- ⇒ ⇒ **剩下的唯一落点 = 销号签字**（`docs/Intentional_Deviations/**` ⇒ **不在 G1 也不在 G4 写权面** ⇒ 需合并收口执行者拍板）。
- ⚠️ **本轮我域台账 `§19.4` 那条「V-002 我域『#6 第 1 步』已取消」的登记 ⇒ 现已由 G4 的撤回【双向确认】** ⇒ ⇒ 该待确认项**关闭**。

---

## 28. 🆕 **MP 排序桥收编：域内 3 份副本删除 ＋ 三层守门 ＋ 三次变异**（2026-10-27 · G1 lane）

> **本节记录**：把 `users`（user-rpc / admin-rpc）＋ `follow`（follow-rpc）三份域内
> `MybatisPlusOrderingBridgeAutoConfiguration` 收编到共享层。
> **全部落在 G1 写权内**（`service/users/**` ＋ `service/follow/**` ＋ `docs/DRIFT/{users,follow}/**`）。
> ⇒ 承接本文件 **§10「待办依赖：MP 排序桥下沉共享层」** —— 那一节的前置依赖**已满足**，本节是它的落码。

### 28.1 🟢 **前置核实（落码前逐条实测，不采信转述）**

| # | 前置项 | 实测 | 结论 |
|---|---|---|---|
| 1 | **共享层那份是否已在树** | `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` 存在 ⇒ 随 `b7faeb4` 合入 | ✅ **前置满足** |
| 2 | **共享层那份是否已注册** | `service/common/src/main/resources/META-INF/spring/…AutoConfiguration.imports:4` = `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration`；且 `service/common/target/classes/META-INF/spring/` 实测有该文件（**会打进 jar**） | ✅ |
| 3 | **我域 3 个 rpc 是否依赖 `com.rag:common`** | user-rpc / admin-rpc / follow-rpc **全部** `com.rag:common`，**scope 均为默认 compile**（非 provided/test） | ✅ ⇒ 该资源在**运行期** classpath 上 |
| 4 | **删掉域内清单后共享层那份还注册吗** | Boot 4 `ImportCandidates.findUrlsInClasspath` 字节码实测 = **`ClassLoader.getResources(String)`（复数）** ⇒ 读 classpath 上**全部**同名资源 ⇒ **域内清单删掉不影响共享层那份被读取** | ✅ |
| 5 | **两份桥的排序边是否同向** | 域内：`after = DataSourceAutoConfiguration.class`（**Boot 4 新包名** `org.springframework.boot.jdbc.autoconfigure.*`）/ `before = MybatisPlusAutoConfiguration.class`；共享层：`afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"` / `beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"` | ✅ **同一对节点 ⇒ 排序图上同向等价 ⇒ 删除零行为差** |
| 6 | **域内清单是否只含那一行** | user-rpc 72 B / admin-rpc 74 B / follow-rpc 75 B ⇒ **各只含 1 行** ⇒ 删掉整份清单文件（不是留空文件） | ✅ |
| 7 | **有没有人 import 被删的类** | 全仓 `import com.rag.{users.user.rpc,users.admin.rpc,follow.follow.rpc}.config.MybatisPlusOrderingBridgeAutoConfiguration` ⇒ **零命中** | ✅ 无编译残留 |

> ⚠️ **口径澄清（与 G4 的说法不同，两处都实测过）**：
> G4 早前说「合并后域内副本被**遮蔽**」⇒ **不成立** —— 共享层那份**没有** `@ConditionalOnMissingBean`
> ⇒ 两份**都会**被注册 ⇒ 但**两份都刻意为空、不注册任何 bean** ⇒ ⇒ **零行为影响**。
> ⇒ 本次删除的**不是**「被遮蔽的冗余」，而是**「排序图上多一个同向节点」** ⇒ 删除同样是零行为差。

### 28.2 🟢 **落码清单（已完成）**

| 文件 | 动作 |
|---|---|
| `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` | **已删除** |
| `service/users/user/user-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **已删除**（该文件只含那一行） |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` | **已删除** |
| `service/users/admin/admin-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **已删除**（该文件只含那一行） |
| `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` | **已删除** |
| `service/follow/follow-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **已删除**（该文件只含那一行） |
| `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/FollowRpcApplication.java` | javadoc 改写（桥的提供方 = 共享层，并写明 `getResources`（复数）这个机制依据） |
| `docs/DRIFT/follow/rpc.md` | 逐文件对照索引**删第 5 行** ⇒ 原 #6~#21 前移为 **#5~#20** ⇒ Java 侧 21 → **20**；连带修 :368 跨文档交叉引用 + :377「行 #1–#21」→「#1–#20」 |
| `docs/DRIFT/follow/vs-common.md` | §10 加 §10.2「订正：§10.4 的『本轮不动手』已被后续事实取代」 |

> ✅ **users 侧台账零改动**：`docs/DRIFT/users/{user,admin}/rpc.md` **实测零命中**该桥 ⇒ 无需订正（不留悬念）。

### 28.3 🟢 **守门：三份「三层加码」判据**

**三层分工**（每层各能独立转红，见 §28.4）：

| 层 | 判据 | 抓什么 |
|---|---|---|
| ① **注册层** | classpath 上**全部**同名 `AutoConfiguration.imports` 资源里，**至少一份**含共享层那份的 FQN | 桥没被注册 ⇒ 排序边不存在 |
| ② **收编层** | 域内那份**类文件必须不在 classpath 上**（`Class.forName` 抛 `ClassNotFoundException`） | 收编没做 ⇒ 全仓两份桥同时注册 ⇒ 共享层后续改动在本域静默不生效 |
| ③ **机制层** | 运行期容器里**真的有 `SqlSessionFactory`** | 排序边**真的被消费**（不是装饰） |

| 组件 | 守门落点 | 上下文形态 |
|---|---|---|
| `user-rpc` | `bootstrap/UserRpcContainerWiringTest#mybatisOrderingBridgeMustBeProvidedBySharedLayer` | `@SpringBootTest(classes = UserRpcApplication.class)` ＋ 真 PG |
| `admin-rpc` | `bootstrap/AdminRpcContainerWiringTest#mybatisOrderingBridgeMustBeProvidedBySharedLayer` | `@SpringBootTest(classes = AdminRpcApplication.class)` ＋ 真 PG |
| `follow-rpc` | `FollowRpcWiringTest#mybatisOrderingBridgeMustBeProvidedBySharedLayer` | `@SpringBootTest(classes = FollowRpcApplication.class)` ＋ 真 PG |

> ⚠️ **为什么第 ① 层必须遍历「全部」同名资源**：原写法用 `getResourceAsStream` 取**第一份**
> ⇒ 而 classpath 上可能有**多份**（各模块自己的 + `service/common` 的）⇒ **命中哪份取决于 classpath 顺序**
> ⇒ **判定不确定** ⇒ 现改为遍历全部、断言「至少一份含目标行」⇒ ⇒ **与「有几个模块自带清单」解耦**
> ⇒ 谁再加自己的清单也不会让这条判据变脆。
>
> ⚠️ **为什么第 ③ 层不能用「清单里有那行」代替**：清单里有那行只证明**文件写了**，
> **证明不了 Boot 真的消费了它** ⇒ 排序边失效面是**静默的**（注解对、编译过、上下文可能起得来，
> 只有 mapper 第一次执行才炸）⇒ 必须有一条**运行期**判据。

### 28.4 🟢 **变异验证：三层各自能独立转红（收编后守门不是假绿）**

| 变异 | 手法 | 结果 |
|---|---|---|
| **MUT-A** | 把**域内那份装回来**（follow-rpc：类 + 清单）⇒ 模拟「收编没做」 | ✅ **只红第 ② 层**：`Expected java.lang.ClassNotFoundException to be thrown, but nothing was thrown`；第 ① ③ 层仍绿 ⇒ **收编判据能单独抓住「收编没做」** |
| **MUT-B** | 运行期 `-Dspring.autoconfigure.exclude=com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration` ⇒ 桥**不被消费** | ✅ **上下文直接起不来**（20/20 全 error），根因 = `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` ⇒ **这正是 AGENTS.md §3.3 描述的那个失效面** ⇒ **排序边真的在承重，不是装饰** |
| **MUT-B2** | 装一份**第三个 FQN** 的等效桥（`MutProbeOrderingBridgeAutoConfiguration`，**同向**排序边）＋ 从共享层清单里**移除**共享层那行 ⇒ **上下文仍能起**（探针桥供给排序边） | ✅ **只红第 ① 层**：`排序桥未注册 ⇒ …`；第 ② ③ 层绿 ⇒ **注册层判据可独立转红**（不是被上下文失败掩盖） |

> ✅ **还原校验**：三次变异全部用**备份文件 + md5 双向校验**还原 ⇒
> 共享层清单 md5 = `b6b36234d4d0bcf69ebb17fa6a925356`（改前/改后/还原后**三次一致**）、
> `git diff service/common/` = **空**、探针桥文件残留 = **0** ⇒ **零残留**。
> ⚠️ MUT-B2 临时改过 `service/common` 的清单（**G4 写权**）⇒ **只作变异探针、立即还原、绝不提交**。

### 28.5 🔴 **踩坑：删资源文件必须 `mvn clean` —— 否则 stale `target/classes` 让 Boot 读到已删的类**

- **现象**：第一次跑定向测试 ⇒ `MVN_EXIT=1`，**12/12 全 error**，根因 =
  `IllegalStateException: Unable to read meta-data for class
  com.rag.users.admin.rpc.config.MybatisPlusOrderingBridgeAutoConfiguration`
  （栈顶 = `AutoConfigurationSorter$AutoConfigurationClass.getAnnotationMetadata`）。
- **根因**：`maven-resources-plugin` **不删除**已删源文件的副本 ⇒ `target/classes/META-INF/spring/…imports`
  **仍留着**那一行 ⇒ `target/classes/…/MybatisPlusOrderingBridgeAutoConfiguration.class` **也还在**
  ⇒ ⇒ **Boot 照旧注册了那个「已经不存在的源码」的类** ⇒ 排序时读元数据失败。
- **修法**：`mvn … clean test` ⇒ **全绿**。
- ⚠️ **纪律**：**删除源文件/资源文件后，回归命令必须带 `clean`** ⇒ 否则测的是**上一轮的构建产物**，
  **不是当前源码** ⇒ 与 §26.5 的 `-am` 纪律**同族但不同触发点**（那条是「上游库是哪一份」，这条是「本模块产物是哪一份」）。

### 28.6 🟢 **回归（带 `-am` ＋ `clean`）**

- 定向轮（3 份守门）：`user-rpc` **18** / `admin-rpc` **12** / `follow-rpc` **20** 条 ⇒ **0f 0e 0s** ⇒ BUILD SUCCESS。
- **域级全量轮**（`-am` + `clean`）：**MVN_EXIT=0 / BUILD SUCCESS**。
  口径 = **按本域模块的 surefire 报告目录**统计（不把 reactor 总数当本域数）：

  | 模块 | 用例数 | 报告份数 | f/e/s |
  |---|---|---|---|
  | `service/users/user/user-api` | 121 | 22 | 0/0/0 |
  | `service/users/user/user-rpc` | 144 | 21 | 0/0/0 |
  | `service/users/admin/admin-api` | 140 | 17 | 0/0/0 |
  | `service/users/admin/admin-rpc` | 118 | 22 | 0/0/0 |
  | `service/follow/follow-api` | 68 | 8 | 0/0/0 |
  | `service/follow/follow-rpc` | 78 | 11 | 0/0/0 |
  | **我域合计** | **669** | **101** | **0/0/0** |
  | ＋ `service/common`（共享层，reactor 内） | 327 | 62 | 0/0/0 |
  | **总计** | **996** | **163** | **0/0/0** |

  ⚠️ 与收编前（667）**差 +2** = 新增 3 条守门中 follow/user/admin 各 +1（follow-rpc 那条是**改写**为更强断言，净 +1）。
- **门禁（好写法：先捕获 rc 再打印）**：
  `check-wave-lane-ownership.py` **0** · `check-wave-role-model.py` **0** · `check-doc-links.py` **0** ·
  `check-ledger-count.py` **0** · **`check-wave-coupling.py` 1**。
  - ⚠️ **`check-doc-links.py` 一度被我改红**（EXIT=1）⇒ 根因 = 我写的两个**简写锚点**（`#28-` / `#10-`）
    —— 中文标题的 GitHub slug 含 emoji ＋ 去标点 ⇒ **简写锚点不可用** ⇒ 已改为**不带锚点的文字指名**
    （「见本文件末尾的 §28」/「承接本文件 §10」）⇒ **不依赖 slug 规则** ⇒ 已修复为 EXIT=0。
    ⚠️ **自指坑**：连**描述这个坑的文字**都不能写成 markdown 链接（守门会真的去解析它）⇒ 必须用**代码格式**书写。
  - 🔴 **`check-wave-coupling.py` 的红项与本次收编【无关】**：仍是**收编前那一条**
    （`GoStyleRedisClientAutoConfiguration` ⇒ users 域第一次引用共享层那份）⇒ **项数未增**。
    ⇒ **本次排序桥收编不给 coupling 新增漂移项** —— 原因实测：域侧那 3 份在 `service/<域>/**` 下，
    **不在 `service/common/**` 下** ⇒ 不计入 `common_radiation`；且
    `docs/DRIFT/_cross/cross-domain-coupling.md` 的期望快照里**零命中** `MybatisPlusOrderingBridgeAutoConfiguration`
    ⇒ ⇒ **排序桥不是跨域共享件**（它只被 rpc 组件引用，不是被 api 组件引用）⇒ 与 §5.3f 的
    「共享判据 = 依赖度数」一致：**度数不够 ⇒ 不该下沉**（G4 那份下沉是**形态统一**的收益，不是依赖度数驱动的）。
    ⇒ 落点 `docs/DRIFT/_cross/**` = **无人拥有面** ⇒ 仍**等合并收口执行者**更新快照（与 §26.10 同一条，不新增待办）。

### 28.7 🔴 **写权外 ⇒ 登记并转派（`docs/FileTree/**`）**

| 位置 | 引用形态 | 动作 |
|---|---|---|
| `docs/FileTree/users/user/rpc.md:19` / `:209` / `:258` / `:369` / `:385` | 目录树 ＋ 逐文件说明 ＋ 「逐文件对照索引」里的**独立一行** ＋ 「新增文件有职责依据」列举 | 🔴 **写权外**（`docs/FileTree/**` 归合并收口执行者）⇒ **不写**，登记转派 |
| `docs/FileTree/users/admin/rpc.md:19` / `:332` / `:398` / `:549` / `:565` | 同上 | 🔴 同上 |
| `docs/FileTree/follow/rpc.md:469` / `:508` | 目录树 ＋ 逐文件说明（还写着「**必须同时写进 `AutoConfiguration.imports`**」） | 🔴 同上 |
| `docs/FileTree/like/rpc.md:20` · `docs/FileTree/comment/rpc.md:27` / `:30` · `docs/FileTree/article/rpc.md:18` | **别域**（like / comment / article）在「对照模块」列里提到 **我域** 的桥 | 🔴 同上（且那几条要等 G2 / G3 各自删完自己的副本后再统一改） |
| `docs/FileTree/follow/rpc.md:81` | 「**follow-rpc 必须新建**」的结论 ⇒ **已被本次收编推翻** | 🔴 同上 |
| `docs/FileTree/knowledge/knowledge.md:609` | 「新模块必须复制这份桥」⇒ **已被本次收编推翻**（应改为「新模块依赖 `service/common` 即可」） | 🔴 同上 |

> ⇒ **其余 6 份域侧桥**（like / message / task / article / comment / favorite）**不归 G1** ⇒
> G2 / G3 各自按同一方法删（**共享层那份已在树 ⇒ 前置满足**）⇒ **全仓只剩 1 份**。
> ⚠️ ⇒ **`docs/FileTree/**` 的那批行要等 G2 / G3 也删完之后再一次性改** ⇒
> **否则会出现「FileTree 说 like-rpc 有桥、但 like-rpc 已删」的半旧状态** ⇒
> ⇒ **这 9 处不急 ⇒ 但必须登记在案 ⇒ 不能因为「不是我的写权」就当它不存在**。

---

## 29. 🆕 **Redis 超时「联动条款」的机制复核（2026-10-27 · G2 提出 · G1 独立复核 ＋ 确认接受）**

> **本节记录**：G2 第九轮回信提出的「共享层 customizer 改动 ⇒ 影响各域守门」的**真实机制**。
> ⇒ **我域逐条独立复核（不采信转述）**，结论：**成立**，且**我域守门已覆盖** ⇒ **我域接受该条款，零落码**。

### 29.1 ✅ **机制复核：逐条成立（G1 自己读源码 ＋ 字节码）**

| G2 的说法 | G1 独立实测 | 结论 |
|---|---|---|
| `LettuceConnectionConfiguration.createClientOptions(ObjectProvider<LettuceClientOptionsBuilderCustomizer>, SslBundle)` | `spring-boot-data-redis-4.0.8-sources.jar` ⇒ `LettuceConnectionConfiguration.java:208-210` | ✅ 成立 |
| `connectTimeout` ⇒ `builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())` | 同文件 `:212-215` | ✅ 成立 |
| `builder.timeoutOptions(TimeoutOptions.enabled())` | 同文件 `:229` | ✅ 成立 |
| **customizer 最后执行**（`:230`）⇒ 共享层那份能覆盖 Boot 刚设的值 | 同文件 `:230` = `clientConfigurationBuilderCustomizers.orderedStream().forEach((customizer) -> customizer.customize(builder));` | ✅ 成立 |
| `ClientOptions$Builder.timeoutOptions(...)` / `socketOptions(...)` 是**替换语义**（不是合并） | `javap -p -c` 实测 `io.lettuce.core.ClientOptions$Builder.timeoutOptions` = **`putfield` ⇒ 整体替换** | ✅ 成立 |

### 29.2 🟢 **可达性分级（G1 采纳）**

| 判据 | 落点 | 共享层 customizer 能覆盖吗 |
|---|---|---|
| 命令超时 **3s** | `LettuceClientConfiguration.Builder.commandTimeout` ⇒ `getCommandTimeout()` ⇒ `RedisURI.timeout` | **不能**（不是同一个 builder） |
| 连接超时 **5s** | `ClientOptions.Builder.socketOptions`（`:213-214`） | **能** |
| 机制位 `timeoutOptions.enabled()` | `ClientOptions.Builder.timeoutOptions`（`:229`） | **能**（覆盖 ⇒ `applyConnectionTimeout` 位丢失） |

⇒ ⇒ **登记的联动条款（我域确认接受）**：
> **共享层 customizer 若新增 `timeoutOptions(...)` / `socketOptions(...)` 调用**
> （E66「Redis 命令感知 deadline」正是这条路）⇒ **必须与受影响域的守门同一次提交一起改**。

### 29.3 🟢 **我域守门【已覆盖】这两个面 ⇒ 我域零落码**

我域 **5 份** Redis 守门（`UserRpc` / `AdminRpc` / `UserApi` / `AdminApi` / `FollowApi` 的 `*RedisDisconnectedFastFailTest`）
实测**每份都有**（grep 5/5 命中）：

| 我域断言 | 抓什么 |
|---|---|
| `getSocketOptions().getConnectTimeout() == Duration.ofSeconds(5)` | **连接超时的消费层锚点** ⇒ 共享层改 `socketOptions` ⇒ **红** |
| `getClientConfiguration().getCommandTimeout() == Duration.ofSeconds(3)` | 命令超时的消费层锚点 |
| `options.getTimeoutOptions().isApplyConnectionTimeout()` | **机制腿** ⇒ 共享层改 `timeoutOptions` ⇒ **红** |
| `connectionFactory.getTimeout() == 3000L` | 连接级 timeout（`source` 恒 −1 ⇒ 时长只能走这一路） |
| `getBeansOfType(LettuceClientOptionsBuilderCustomizer)` **size()==1** ＋ bean 名 = `goStyleRedisClientOptionsCustomizer` | 收编真的发生了（不是两份叠加、也不是一份都没） |

⇒ ⇒ **我域不会出现「共享层改了 ⇒ 我域静默」** ⇒ **E66 落地时我域不用改守门**，
只需提醒 E66 本身**别把我域这 5 份守门的期望值改掉**。

### 29.4 🔴 **订正 G2 的一条过时前提**

G2 第九轮回信写：「你域的 `RedisDisconnectedBehaviorConfig.java:103` 那份**域内副本**会**遮蔽**共享层 ⇒ 共享层那次改动在**你域静默不生效**」
⇒ ⇒ **这条在我域【已不成立】** —— 我域那 **5 份域内副本已在提交 `312a09b` 全部删除**
（`find service/users service/follow -name "RedisDisconnectedBehaviorConfig.java"` ⇒ **零命中**）
⇒ ⇒ **我域不存在「遮蔽 ⇒ 静默不生效」那个面** ⇒ **我域守门已经能看见共享层的后续改动**。
⚠️ G2 的**结论方向**（「删域内副本是让联动条款真正生效的前提」）**完全正确** —— 只是**我域已先做完**。

### 29.5 🟢 **附带关闭**：3 份「无人拥有 U+FFFD」已由 G4 认领并修

| 原编号 | 文件 | G4 提交 |
|---|---|---|
| #18 | `tools/errmsg-probe/README.md:59` | `cc6e38e`（原文取自同仓完好副本，不靠猜） |
| #19 | `tools/sql-statement-probe/favorite/compare.py:561` | `cc6e38e` |
| #20 | `component-semantic-verification/springboot-semantic-model.md:269` / `:320` | `f68a9cd`（原文按语义补回） |

⇒ 随 `6375faf`（再并 G4 `6cb0a60`）合入我树 ⇒ 合并态我树这 3 份**零命中**
⇒ ⇒ **「待合并收口执行者认领」这条待办【关闭】** ⇒ ⇒ §20.2 合并规则的「目标 = 7 份文件命中」**已过期**
⇒ **实际目标 = 只剩 6 处故意（4 份文件）**。

### 29.6 🟢 **共享层 javadoc 变更复核**：`RpcInterceptor.java` 的改动**只是 javadoc**

`87d72e0 → 6cb0a60` 的 `RpcInterceptor.java` 差异 = **21 行全是 javadoc**（FB-04 计数口径订正）
⇒ **零代码变更** ⇒ ⇒ 对我域**零影响** ⇒ 仍按 C7 跑了合并后回归（见 §29.7）。

### 29.7 🟢 **C7 回归（合并 G4 `6cb0a60` 后）**

- 命令：`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test` ⇒ **MVN_EXIT=0 / BUILD SUCCESS**。
- 口径 = **按本域模块的 surefire 报告目录**统计（不把 reactor 总数当本域数）：

  | 模块 | 用例数 | 报告份数 | f/e/s |
  |---|---|---|---|
  | `service/users/user/user-api` | 121 | 22 | 0/0/0 |
  | `service/users/user/user-rpc` | 144 | 21 | 0/0/0 |
  | `service/users/admin/admin-api` | 140 | 17 | 0/0/0 |
  | `service/users/admin/admin-rpc` | 118 | 22 | 0/0/0 |
  | `service/follow/follow-api` | 68 | 8 | 0/0/0 |
  | `service/follow/follow-rpc` | 78 | 11 | 0/0/0 |
  | **我域合计** | **669** | **101** | **0/0/0** |
  | ＋ `service/common`（共享层） | 327 | 62 | 0/0/0 |
  | **总计** | **996** | **163** | **0/0/0** |

- ⇒ ⇒ **与合并前（§28.6）逐项完全一致 ⇒ 共享层那 4 笔（含新测试 `GoStyleJsonContentTypeCharsetTomcatTest`）对我域零回归**。
- ⇒ 门禁（合并态）：`check-wave-lane-ownership.py` **0** · `check-wave-role-model.py` **0** ·
  `check-doc-links.py` **0** · `check-ledger-count.py` **0** · **`check-wave-coupling.py` 1**（仍是我域那一条既有项）。
- ⇒ **U+FFFD**：我域写权面 **4 份 / 14 处**（与合并前一致，**零新增**）；
  而 §29.5 那 **3 份「原无人拥有」实测归零** ⇒ **真损坏已全部消除**。

---

## 30. 🟢 **活体对拍环境实测：可用（2026-10-27 · G3 报「机器级故障」后我域自行复检）**

> **触发**：G3 第九轮回信说它的 rpc 侧活体对拍「**交不出来**」⇒ 理由 =
> 「机器级 Docker 端口转发故障 ⇒ 我域四个 PG 容器口『TCP 连得上但 4s 内 0 字节』⇒
> **Redis 口同样坏**（`127.0.0.1:6379` 直接 refused）⇒ **断连活体对拍的必要条件不成立**」。
> ⇒ **我域不采信转述 ⇒ 自己复检**（本节全部是本机实测）。

### 30.1 🟢 **实测结论：difftest5k 编排下的活体环境【完好】**

| 项 | 实测 | 判定 |
|---|---|---|
| **12 个遗留对拍进程** | `ps` 逐条读 ⇒ **13 个 PID 全部在**（`22:59:xx` / `02-16:xx`）⇒ 其中我域 4 个 = `user-rpc` / `admin-rpc` / `user-api` / `admin-api` | ✅ **进程层面全在** |
| **Redis 容器** | `docker ps` ⇒ `difftest5k-redis Up 23 hours (healthy) 127.0.0.1:**61379**->6379/tcp` | ✅ healthy |
| **Redis 连通 ＋ PING** | `nc -z 127.0.0.1 61379` ⇒ **OK**；`(printf 'PING\r\n') \| nc` ⇒ **`+PONG`** | ✅ **能用** |
| **PG 容器** | `difftest5k-postgres Up 23 hours (healthy) 127.0.0.1:**60432**->5432/tcp`；`nc -z 60432` ⇒ **OK** | ✅ healthy |
| **我域 `user-api`（64900）活体** | `GET /user/v1/user/info` ⇒ **HTTP 401** ＋ 业务信封 `{"code":1006,"msg":"TOKEN已过期","data":null}` | ✅ **服务在跑、鉴权链在跑** |
| **我域 `admin-api`（63889）活体** | `GET /admin/v1/admin/info` ⇒ **HTTP 401** ＋ 同款信封 | ✅ 同上 |
| **我域 `user-api` 健康检查** | `GET /actuator/health` ⇒ **HTTP 200** ＋ `{"status":"UP"}` | ✅ **UP** |

⚠️⚠️ **关键纠偏：那个「Redis 坏了」的判据用的是【不存在的口】**
- `127.0.0.1:6379` ⇒ **refused** —— 但 **6379 上从来就没有本仓的容器**：
  `difftest5k-redis` 的映射是 **`61379->6379`**（宿主 61379 ⇒ 容器内 6379）⇒
  ⇒ **「连 6379 被拒」**只说明**那个宿主口上没容器**，**不能**推出「Redis 不可用」。
- ⇒ ⇒ **「断连活体对拍的必要条件（能连上 Redis 再断）不成立」这个前提，在 difftest5k 编排下【不成立】**
  ⇒ ⇒ 实测：**能连上**（`+PONG`）⇒ ⇒ **必要条件是满足的**。
- ⇒ ⇒ **我域 `DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013` 升 L4 的活体环境【没坏】** ⇒ ⇒ 路径未断。

### 30.2 🔴 **跨 lane 的协调点（不是派单，是【会互相打断】的登记）**

- **要跑「断连」活体对拍 ⇒ 必须真的断 Redis** ⇒ 而 `difftest5k-redis` 是**我域 4 个遗留进程也在用的共享容器**
  ⇒ ⇒ **断它 = 同时打断我域的活体环境** ⇒ ⇒ ⇒ **必须先约窗口**（谁断、断多久、断了之后谁负责恢复）。
- ⇒ **登记在案**（免得下一轮又有人「顺手停一下 Redis」）：
  | 资源 | 宿主口 | 谁在用 |
  |---|---|---|
  | `difftest5k-redis` | **61379**（不是 6379） | 我域 4 个遗留进程 ＋ G2 的 message 侧 ＋ G3 若复用同一编排 |
  | `difftest5k-postgres` | **60432** | 同上 |
- ⇒ ⇒ **我域这批进程「不杀」的另一个理由（本轮补记）**：它们不只是「升 L4 的活体环境」，
  还是**这台机器上唯一一套还能跑通的双侧对拍环境** ⇒ ⇒ **机器级故障的判据要先用【实际映射端口】复核**。

### 30.3 🟢 **G3 那 5 条红的根因：它自己后一轮已推翻它转派 G4 的前提**

G3 第十轮说「①② 的根因 = `ProtocolException: Too many interim responses received: 9 > 8`
⇒ **根因在共享层 `cd893d1` 把容器上限抬到 10001**（Tomcat 不再在 2048 拒 ⇒ 继续读大 body）⇒ ⇒ 已转派 G4」。

⚠️ **但它自己后续的裸 socket 证据指出真根因是另一件事**：
**Tomcat 11.0.24 连发 11 条 `103 Early Hints` ⇒ 撞了 JDK `java.net.http.HttpClient` 对 interim(1xx) 的 8 条硬编码上限**
⇒ ⇒ **被测行为其实完全正确**（裸 socket 抓到 `103 ×11` 之后就是 `200 + {"code":5003}`）⇒ ⇒
**根因在【测试客户端】，不在共享层容器上限** ⇒ ⇒ **转派 G4 的那条前提已被它自己的证据推翻**。

⚠️ 我**没有** favorite 域的一手证据（不在我域、不在我树）⇒ **我只指出这条内部矛盾，不下结论**。
🟢 **但它对我域有直接影响 ⇒ 复开触发条件写成这样**：
> **我域将来若加「用 JDK `java.net.http` 打真 Tomcat 大 body」的用例** ⇒ 可能撞同一个 interim(1xx) 上限
> ⇒ ⇒ **修法在【用例侧】（换 `HttpURLConnection` 或裸 socket，它们透明忽略 1xx）**，
> **不是**去改共享层的容器上限（那会推翻共享层的刻意设计：上限抬到 10001 = Go 段数上限 +1 ⇒ 判定权交还应用层）。
> ⇒ ⇒ 同族于「路径变体类探针必须用 `HttpURLConnection`，不能用 `java.net.http`」。

### 30.4 🟢 **订正 G3 的 ⑥（它引用的是我域收编【之前】的状态）**

G3 第十轮写：「你域的**删除前置复检**（共享层 `imports` 里还没有 `GoStyleRedisClientAutoConfiguration`）我认同 ⇒ ⇒ **等 G4 分支进主干再一并做**」。

⇒ ⇒ **这条已过期**（我域**早已做完**）：
- 共享层 `service/common/src/main/resources/META-INF/spring/…AutoConfiguration.imports` **实测第 5 行就是**
  `com.rag.common.redis.GoStyleRedisClientAutoConfiguration` ⇒ **已注册**；
- 我域 **5 份域内副本已在提交 `312a09b` 全部删除**（`find … -name "RedisDisconnectedBehaviorConfig.java"` ⇒ **零命中**）；
- 我域 5 份 FastFail 守门**断言 bean 名 = `goStyleRedisClientOptionsCustomizer`** ⇒ ⇒ 运行时**证明**走的是共享层那份。
⇒ ⇒ **我域零动作**；G2 / G3 各自的域内副本删除**前置同样已满足**（共享层那份已在主干可达路径上）。

### 30.5 🟢 **关闭 §18.2 的「差 3 未解释」**

G3 第十轮答了：**差 3 = 跑次不同** —— `919` 是 D10 落码**前**那一跑次，`comment-rpc` **116→119**（D10 落码后新增 3 条）⇒
它按同口径实测当前树 = **922**（129 份 / skipped 4）⇒ **与我 §18.2 记的 922 一致** ⇒ ⇒ **该状态关闭**。
⇒ ⚠️ 保留 §18.2 原文（不重写历史）⇒ 只加本条订正。

## 31. 🟢 **G4 新门禁入树 ＋ 白名单清理的【顺序约束】（2026-10-27 · G1 lane）**

### 31.1 🟢 **门禁 `22dc67e` 已在我的 HEAD 内 ⇒ 「再并一次」是过时认知**

G4 第十一轮来信：「新门禁 `22dc67e` + 台账 `c164e63`，请再并一次 `align/g4-service-common`（你落后 5 笔）」。

⇒ ⇒ **实测不落后**（三条独立判据）：

| 判据 | 实测值 | 读法 |
|---|---|---|
| `git merge-base --is-ancestor 22dc67e HEAD` | **YES** | 门禁提交已在我的 HEAD 内 |
| `git merge-base --is-ancestor c164e63 HEAD` | **YES** | 台账提交已在我的 HEAD 内 |
| `git rev-list --count HEAD..align/g4-service-common` | **0** | G4 那边**没有**我缺的提交 |

⇒ ⇒ 「落后 5 笔」是按 `b19a61a`（我上一次并 G4 的合并点）算的**过时状态** ⇒
我后来在 `6375faf` 并了 G4 的 `6cb0a60`（= G4 当前 tip ＝ 我俩的**共同基线**）⇒ ⇒ **不需要再合并**。
⇒ 我 HEAD = `2da4a48`；`git rev-list --count do-my-own-gh..HEAD` = **108** / 落后 **0**；工作区干净。

### 31.2 🟢 **门禁在我树实测绿（不是「应该绿」）**

`mvn -o -B -DargLine=-Xmx2g -pl service/common test -Dtest=GoStyleRedisClientShadowingGateTest`
⇒ ⇒ **Tests run: 3, Failures: 0, Errors: 0, Skipped: 0**（`MVN_EXIT=0` / `BUILD SUCCESS`）。

⇒ ⇒ G4 的「self-adaptive」设计我复核过并成立：`stillExistingWhitelistEntries` **只取文件仍存在**的条目
⇒ 我树里 5 份副本全删 ⇒ 集合为空 ⇒ 断言③(b) 自动空 ⇒ 不红。
⇒ 全仓 `src/main/java` 侧 `LettuceClientOptionsBuilderCustomizer` 现在**只有**一处：
`service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java`（共享层那一份）。

### 31.3 🔴 **白名单清理 = G4 自己的写权，但【顺序不能反】**

`LEGACY_DOMAIN_COPIES` 定义在
`service/common/src/test/java/com/rag/common/redis/GoStyleRedisClientShadowingGateTest.java:88`
⇒ 该文件在 G4 白名单 `service/common/src/**`（`tools/difftest/check-wave-lane-ownership.py:146`）
⇒ ⇒ **删那个条目不需要我域写权** ⇒ G4 可直接删（我域副本早已在 `312a09b` 删掉）。

⚠️ **但「现在删」会把 G4 自己的门禁改红** —— G4 树里
`service/users/user/user-api/src/main/java/com/rag/users/user/api/config/RedisDisconnectedBehaviorConfig.java`
**仍然存在**（⚠️ **该文件在我树已于 Redis 收编轮【删除】** ⇒ **只**在 G4 树里还留着
⇒ ⇒ 实测：`git ls-tree align/g4-service-common` 命中；该文件第 4 行
`import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;`、
第 103 行 `public LettuceClientOptionsBuilderCustomizer goStyleRedisDisconnectedBehavior()`）
⇒ ⇒ 那条白名单在 **G4 自己的树上仍是承重的** ⇒ 先删白名单 ⇒ 断言① 的 `found` 命中它 ⇒ **门禁红**。

⇒ ⇒ **正确顺序（已回函 G4）**：
> **先把「删掉那份」的提交并进 G4 树（或等主干拿到 `312a09b`）⇒ 再删白名单条目。**

### 31.4 🟢 **「删 5 份」在【合并语境】下的口径订正：相对主干的净变化只有 1 份**

`312a09b` 的 diff 删了 **5 份**域内副本（`git show --stat --diff-filter=D 312a09b` ⇒ **5 files changed, 547 deletions**），
但**合并到主干时的净变化只有 user-api 一份**。逐份追血脉（`git log --diff-filter=A/D`）：

| 域内副本 | 谁**新增**的 | 主干上有吗 | 合并后主干的净变化 |
|---|---|---|---|
| `user-api` | `ea8f01e`（**主干上**，修 `DRIFT-USERAPI-014`：Redis 断连时 Lettuce 排队 ⇒ 撞 HTTP 3s 预算 ⇒ 503） | **有** | **删除这一份**（真净变化） |
| `admin-api` | **`a4e3740`（我自己的提交）** | 无 | 零（删的是我自己引入的影子） |
| `follow-api` | **`a4e3740`（我自己的提交）** | 无 | 零 |
| `user-rpc` | **`0c2a902`（我自己的提交）** | 无 | 零 |
| `admin-rpc` | **`0c2a902`（我自己的提交）** | 无 | 零 |

⇒ ⇒ **那 4 份域内副本是 G1 自己在收编【之前】引入的**（当时为对齐 Go 的断连行为而各域各写一份）
⇒ ⇒ **共享层收编这个动作，把 G1 自己引入的 4 处影子 ＋ 主干原有的 1 处遗留一次性消掉**。
⇒ 🟢 **这解释了为什么 G4 的白名单「只登记 user-api 一条」是对的**：只有那 1 条是**主干上真实存在的遗留**，
另外 4 条是 G1 引入的、压根没进过主干 ⇒ ⇒ 白名单条目数**本来就是 1 条、不是 5 条**。
⚠️ **口径提醒（防止下一轮又按「5 份」去对账）**：**「我树里删了 5 份」≠「合并后主干少 5 份」** ⇒ 对账一律按【主干净变化】算。
⇒ ⚠️ 另注：`ea8f01e` 那份引入是有正当理由的（`DRIFT-USERAPI-014`）⇒ **它不是「写错了」**
⇒ ⇒ 删除之所以行为等价，是因为共享层那份承载**同一机制**（`disconnectedBehavior(REJECT_COMMANDS)`）
⇒ ⇒ 该等价性由 §28 的三层守门 ＋ 三次变异证明，**不是**由「它看起来多余」证明。

## 32. 🟢 **活体环境复检（2026-10-27 晚）** —— 订正 §30 的「进程号」标注 ＋ 一次**瞬时** 503 的定性

### 32.1 🔴 **订正 §30：那一列填的是【端口】，不是【进程号】**

§30 原文写「我域 4 个：`user-rpc 64210` / `admin-rpc 63081` / `user-api 64900` / `admin-api 63889`」，
并在括号里标注为「进程」⇒ ⇒ **标注错了**：这 4 个数字是**监听端口**，**不是 pid**。

⇒ ⇒ **我自己的查法被自己的标注骗了一次**：复检时用 `ps -p 64210` ⇒ 「查不到」⇒ 一度判定「进程已退出、环境过期」，
而实际上 `lsof -nP -iTCP -sTCP:LISTEN -a -p <pid>` 显示**持有该端口的进程健在**：

| 端口 | **真实 pid** | 启动时刻 | 进程 |
|---|---|---|---|
| 64210 | **86864** | 2026-10-26 19:59:29 | `/tmp/difftest5k/bin/user-rpc` |
| 63081 | **86865** | 2026-10-26 19:59:29 | `/tmp/difftest5k/bin/admin-rpc` |
| 64900 | **87374** | 2026-10-26 19:59:49 | `/tmp/difftest5k/bin/user-api` |
| 63889 | **87447** | 2026-10-26 19:59:49 | `/tmp/difftest5k/bin/admin-api` |

⇒ ⇒ **§30 的结论「全在」是对的**（13 个进程一个没少，etime 23h15m）⇒ ⇒ **只订正标注，不推翻结论**。
⇒ ⚠️ **纪律**：**「端口」与「pid」是两个命名空间** ⇒ ⇒ 以后写「进程」必须用 `lsof`/`ss` 把 pid 查出来再记，
**不许拿端口号当 pid 记进台账**（⇒ 这一条与「判据自己骗你」同族）。

### 32.2 🟢 **完整进程普查（2026-10-27 20:2x 实测）**

`/tmp/difftest5k` 侧 **12 个**：`go-user-rpc 86595` · `go-admin-rpc 86596` · `go-message-rpc 86626` ·
`go-user-api 86640` · `go-admin-api 86641` · `go-message-api 86840` · `user-rpc 86864` · `admin-rpc 86865` ·
`user-api 87374` · `admin-api 87447` · `message-rpc 87658` · `FakeServer 81897(:21999)`；
另 `/tmp/difftest` 侧 `message-api 95655(:58992)`（G2 域）· 另有 G3 的 `comment-rpc 79778(:19084)`。
⇒ ⇒ **双侧对拍环境完整可用**（Go 侧 6 个 ＋ Java 侧 6 个都在）。

### 32.3 🟢 **依赖侧实测**：Redis / PG

| 项 | 实测 | 判读 |
|---|---|---|
| Redis `61379`（users/follow 域） | TCP 通；**原始 socket `PING` 3/3 = `+PONG`（14–78ms）**；`redis-cli PING` = `PONG` | ✅ 活体可用 |
| PG `60432` | TCP 通 | ✅ 可用 |
| Redis `56379`（message 域，G2 的） | **不通** | ⚠️ 该域 Redis 已不可用（**不是我的域**，登记事实） |

⚠️ **我的第一版探针报「3s 内无回复」⇒ 是探针坏了，不是 Redis 坏了**（`nc` 管道写法吞了回复）
⇒ ⇒ 重写为原始 socket + 3 次重试 ⇒ **3/3 正常** ⇒ ⇒ **「探针失败」必须先自证探针本身**（同族：路径变体探针那一条）。

### 32.4 ⚠️ **一次瞬时 503 —— 不作为状态**

复检时 `user-api:64900 /actuator/health` 偶发 **503 + `text/plain` "Request Timeout" + `Connection: close`，
耗时 3.009s，响应前置 5 条 `103 Early Hints`** ⇒ 看起来像「健康检查挂了」。
⇒ ⇒ **复打 8 次：8/8 = 200 `{"groups":["liveness","readiness"],"status":"UP"}`（10–134ms）**
⇒ ⇒ **判定：一次性瞬时 ⇒ 不构成状态 ⇒ 不进台账结论**，只留观测记录。

⚠️ **排除掉的两个假设（避免留下错误因果）**：
1. **不是某个健康指示器在挂** —— `/actuator/health/{redis|db|diskSpace|ping|ssl}` **全部 404**
   ⇒ ⇒ 该构建**一个健康指示器都没注册** ⇒ ⇒ 「redis 指示器挂满 3s」的推断**不成立**；
2. **不是 Redis 慢** —— 见 §32.3（`PING` 14–78ms）。

⇒ ⇒ **剩下可证的部分**：3.009s **= 共享层 `HttpMiddleware` 的 Go 风格 HTTP 超时预算**（`observability.http-timeout: 3s`）
⇒ ⇒ 是**共享层超时守卫**赢了竞速才写下 "Request Timeout"（`service/common/.../observability/HttpMiddleware.java:116`）
⇒ ⇒ **那 3s 里真正慢的是什么，本轮未查清 ⇒ 不下结论** ⇒ ⇒ **登记为「根因未定的一次瞬时」**。
🟢 **对拍相关性**：Go 侧没有 actuator 对端 ⇒ ⇒ **该端点不参与双侧对拍** ⇒ ⇒ **不是我域漂移，也不动摇 §30 的 L4 路径**。

## 33. 🟢 **Redis「池耗尽等待上限」轴的两侧源码级对照（G3 新发现 ⇒ 我域复检）** —— 订正 §24 的「不是语义漂移」

### 33.1 两侧消费点（**读消费点，不读配置文件**）

| 侧 | 事实 | 出处 |
|---|---|---|
| **RAG** | 池**由 classpath 决定**，不是由 yml 决定 | Boot 4.0.8 `DataRedisConnectionConfiguration.java:55` ⇒ `COMMONS_POOL2_AVAILABLE = ClassUtils.isPresent("org.apache.commons.pool2.ObjectPool", …)`；`:195-198` ⇒ `isPoolEnabled(pool) = (enabled != null) ? enabled : COMMONS_POOL2_AVAILABLE`；`LettuceConnectionConfiguration.java:162-166` ⇒ 未启用池 ⇒ 走 `LettuceClientConfiguration.builder()`（**无 `GenericObjectPoolConfig`**） |
| **RAG（我域实测）** | 6/6 `pom.xml` **零 `commons-pool2`**；6/6 `application.yml` **零 `pool.enabled`**；活体 `user-api` 进程 classpath **零 `commons-pool2`** | ⇒ ⇒ **池形态 = 无池**（与 §24 一致） |
| **Go** | 池**有并发上限**，且上限 = `PoolSize` | go-redis v9.20.0 `internal/pool/pool.go:421` ⇒ `semaphore: internal.NewFastSemaphore(opt.PoolSize)`；`:1098-1125` ⇒ `waitTurn()`：**抢不到信号量就 `Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)`** |
| **Go（取值）** | `PoolSize = 10 * GOMAXPROCS`；**`PoolTimeout = ReadTimeout + 1s = 4s`**；`MinIdleConns = 8` | `options.go:350-351` / `:380-384`（`ReadTimeout` 默认 3s，见 `:364-370`）/ go-zero `redisclientmanager.go:15,38`（`idleConns = 8`） |

### 33.2 🔴 **结论：两侧【不对称】—— 这条轴在 Go 侧可达、在 RAG 侧不可达**

- **Go 侧可达**：`PoolSize` 是**并发信号量容量**（不是「连接数上限」）⇒ ⇒ 超过 `10×核数` 个并发 Redis 命令时
  ⇒ **必然进入 `waitTurn` 等待** ⇒ ⇒ 上限 **`PoolTimeout` = 4s** ⇒ ⇒ 超时返回 **`ErrPoolTimeout`**；
- **RAG 侧不可达**：无池 ⇒ **没有信号量、没有「池耗尽」这个状态** ⇒ ⇒ **不存在 `max-wait` 语义**
  （Boot 的 `Pool.maxWait` 默认 `Duration.ofMillis(-1)` 只在**池已启用**时才有意义 ⇒ 我域它根本不生效）;
  ⇒ ⇒ 我域唯一的命令上限是 **`spring.data.redis.timeout: 3s`（command timeout）**。

⇒ ⇒ ⇒ **两者的「排队上限」不同**：Go **4s**（池耗尽路径）vs RAG **3s**（命令超时路径）⇒ ⇒ **饱和路径可观察**。

### 33.3 ⚠️ **订正 §24**：「不是语义漂移 ⇒ 零动作」在**饱和路径**上不成立

§24 的结论是「Lettuce 无池时用 shared connection ⇒ 与 go-zero 的池在**可观察行为**上等价 ⇒ **不是语义漂移**」
⇒ ⇒ **该结论在正常路径上仍成立**（单命令、无争用 ⇒ 两侧都是「一条命令 3s 读超时」⇒ 一致）
⇒ ⇒ **但在高并发饱和路径上不成立**：
- Go：排队 ≤ **4s**，超时错误 = `pool timeout`；
- RAG：无排队阶段，命令 ≤ **3s**，超时错误 = 命令超时；
⇒ ⇒ **同一形态的输入（并发 Redis 需求 > `10×核数`），两侧的失败上限与错误身份都不同** ⇒ ⇒ **OBSERVABLE DIFFERENCE · MEDIUM**。

⚠️ **证据强度 = L1（两侧源码级）**：**未做并发压测 ⇒ 不写 L4** ⇒ ⇒ 升级条件 = 在真 Redis 上把并发打到
`10×GOMAXPROCS` 以上，双侧同时观察失败时刻与错误身份。

⚠️ **我域的使用面**（为什么这条轴对我域不是纯理论）：
`user-api` / `admin-api` 的 `JwtAuthInterceptor` 与 `follow-api` 的 `FollowBlacklistInterceptor`
**都是「每请求一次 Redis `hasKey`」**（fail-open）⇒ ⇒ 高并发下正是这条轴的触发面。

### 33.4 🔴 **修法归属：域内不可自修 ⇒ 落点 `global` 收口域**

要让我域侧也有「池耗尽上限」，必须**同时**做三件事：① 加 `commons-pool2` 依赖 ② 配 `pool.enabled=true`
③ 配 `max-active` / `max-wait` ⇒ ⇒ 而 **① 一旦加上，池形态就静默从「无池」变成「有池」**
⇒ ⇒ 这是**所有域共享的形态决策**，改一个域就与其他域不一致 ⇒ ⇒ **不是我域能自修的**
⇒ ⇒ **落点 = `global` 收口域**（`docs/DRIFT/global/cross-domain.md` = **无人拥有面**，由合并收口执行者写）
⇒ ⇒ **我域零落码**；本条只登记 + 回执。

🟢 **对 G3 那条新发现的直接回答**：它问「Redis 池的跨域条目登记在哪」⇒ ⇒ **答案是 `global` 收口域，不是域侧台账**
⇒ ⇒ 且它那条轴在 **go-redis 系域里，Go 侧是可达的**（信号量容量 = `PoolSize`）⇒ ⇒ **不是「两侧都不可达」的空发现**。

🟢 **触发条件（写死，防下一轮无声漂移）**：
> **我域任何模块若新增 `commons-pool2` 依赖、或把 `pool.enabled` 写成 `true`**
> ⇒ ⇒ **这条轴立刻从我域的「不可达」变成「可达」** ⇒ ⇒ **必须同时定 `max-active` 与 `max-wait`**（Go 侧真值 = `10×GOMAXPROCS` / `4s`）
> ⇒ ⇒ 否则就是**静默引入一条新的行为漂移**。

## 34. 🟢 **C7 复跑撞上「Testcontainers 容器泄漏」⇒ 一次 5 条 error 的正确读法（2026-10-27 晚）**

### 34.1 现象

并入 G4 新 tip（5 提交）后的**第一次**域级全量 ⇒ ⇒ **`admin-rpc` 5 条 error**
（`AdminRpcUpdateTimeAdvanceWiringTest` 的 5 条全部 `Failed to load ApplicationContext`）⇒ ⇒ **BUILD FAILURE**。

### 34.2 根因链（读到最内层）

```
adminSchemaBootstrap init 失败
  → IllegalStateException: admin-rpc 建表失败，进程拒绝启动   (AdminSchemaBootstrap.java:61)
  → PSQLException: 尝试连线已失败
  → java.io.EOFException  at PGStream.receiveChar / ConnectionFactoryImpl.enableSSL
```

⇒ ⇒ **判读：服务端在 SSL 协商阶段直接把连接关了** ⇒ ⇒ ⇒ **这不是「PG 挂了」，是「回应我的那个端口上不是我要的那个 PG」**
⇒ ⇒ （对照实测：`127.0.0.1:60432` 的 SSLRequest 回应是 `b'N'` = **正常 PG**；而失败那次的连接是**另一个东西**）

### 34.3 🔴 **排除「代码回归」的三条独立判据（三条齐 ⇒ 才定性）**

| # | 判据 | 实测 |
|---|---|---|
| ① | **我域自上次全绿以来零代码变更** | 只有台账提交（`91699e8` / `785d528`）⇒ 零 `.java` 变更 |
| ② | **G4 这 5 个提交的变更面** | 只碰 `service/common`（探针 + `HttpMiddleware.java` +16 行）＋ `tools/check-ledger-selfcheck.py` ＋ global 台账 ⇒ ⇒ **零业务代码进入我域**；且失败点在**上下文启动期的 PG 连接**，与 `HttpMiddleware` 无关 |
| ③ | **同一测试类单独复跑** | **5/5 全绿 / BUILD SUCCESS（36.03s）** ⇒ ⇒ **同代码、同环境下单独跑能过** ⇒ ⇒ **问题在「那一次跑的环境瞬间」，不在代码** |

⇒ ⇒ **域级全量复跑 ⇒ 18/18 模块 SUCCESS；我域 669 条逐项与并入前一致（user-api 121 / user-rpc 144 / admin-api 140 / admin-rpc 118 / follow-api 68 / follow-rpc 78）⇒ 零回归**；
共享层 363 条（`common` 330 ＋ `users-common` 33）⇒ ⇒ 比并入前 +3 ⇒ ⇒ **= G4 新增的 3 条门禁** ⇒ ⇒ 数字自洽。

### 34.4 🔴 **环境根因（实测）：Testcontainers 容器泄漏 ⇒ 宿主口被占 ⇒ 随机口冲突**

- `docker ps` 实测 **47 个容器在跑**，其中 **`postgres:16-alpine` 18 个 ＋ `redis:7-alpine` 7 个** = **25 个 Testcontainers 残留**
  ⇒ ⇒ 创建时间跨 **2026-05-20 → 2026-10-27**（最老的 **43 小时**）⇒ ⇒ **长期累积，不是本轮偶发**；
- **`docker ps --filter name=ryuk` ⇒ 零命中** ⇒ ⇒ **Testcontainers 的回收器（Ryuk）根本没在跑** ⇒ ⇒ **残留没人清**；
- 我域的 `AdminRpcUpdateTimeAdvanceWiringTest` 用 `@Container static final PostgreSQLContainer<>`（`postgres:16-alpine`）
  ⇒ ⇒ **每次起新容器要抢一个随机宿主口** ⇒ ⇒ 那些残留容器**占着 55562–55569 等口**（落在 **OS 临时段 49152–65535**）
  ⇒ ⇒ **抢不到口 ⇒ 连到「别的那个」⇒ ⇒ 症状 = SSL 协商阶段被对端 EOF**；
- 同一瞬间 `docker ps` 也**卡住**（实测第一次 20s 超时、之后 0.16s）⇒ ⇒ 佐证 Docker 当时在忙。

🔴 **【订正 · 2026-10-27 晚 · G4 提出，我独立复检后确认他对】根因归属写错了：那些残留容器【不是】Testcontainers 起的。**
> **原写法**：「25 个 Testcontainers 残留 ⇒ Ryuk 没跑 ⇒ 没人清」⇒ ⇒ **该归属不成立**。
> **复检方法**：`docker inspect -f '{{.Config.Labels}}' <容器>` ⇒ **实测零 `testcontainers`/`ryuk` 标签**
> ⇒ ⇒ ⇒ 它们是**手工起的对拍环境容器**（如 `difftest5k-postgres` 对照组也**同样无标签**）⇒ ⇒ ⇒ **不是 Testcontainers 残留** ⇒ ⇒ ⇒ **「Ryuk 方向」作废**。
> **但【机制】仍然成立、且 G4 的修法正对着它**：
> ① 那些容器**占着 55562–55569 等口**（落在 **OS 临时段 `49152–65535`**）⇒ ⇒
> ② **Testcontainers 每次起新容器抢的正是这个段的随机口** ⇒ ⇒ **撞口仍然会发生**（症状不变：`EOFException@enableSSL`）
> ⇒ ⇒ ⇒ **错的只是「谁占的」，对的仍是「随机口落在 OS 临时段 ⇒ 撞口」**
> ⇒ ⇒ ⇒ **G4 已给的修法 = 把 Testcontainers 宿主口挪出该段**（全域 `22700–22999`，按域分子段）⇒ ⇒ **见 §36**。
> ⚠️ 保留本节原文（不重写历史）⇒ ⇒ 只加本条订正。
>
> ⇒ **再补两条口径（2026-10-28 · G4 再确认时补的）**：
> ① **「Ryuk 零命中」本身也不是「没跑过」** ⇒ Ryuk 是**随用随起、随 JVM 退出而回收**的容器
>    ⇒ ⇒ **此刻没有 Testcontainers JVM 在跑 ⇒ 本就该零命中** ⇒ ⇒ 「零命中 ⇒ 没跑过」是错的推理
>    ⇒ ⇒ （与「跨树零命中 ≠ 不存在」同族，但这次是**同机、跨时刻** ⇒ ⇒ **同一个词「零命中」，两种错法**）
> ② **「25 个」是按【镜像】统计的** ⇒ ⇒ 把 `tools/difftest/**` ＋ 探针脚本的**自建容器**与
>    **Testcontainers 容器**混进了同一个数 ⇒ ⇒ ⇒ **「25 个 Testcontainers 残留」这个数本身也是错的**
> ⇒ ⇒ **本轮自核（只读普查，48 个容器）**：`postgres:16-alpine` **18** ＋ `redis:7-alpine` **8**
>    ＋ 带 `testcontainers` / `ryuk` 标签的 **0** 个 ＋ 随机名（`adjective_noun` 形态）**1 个**（`sad_napier`）
> ⇒ ⇒ **与 G4 的只读普查一致** ⇒ ⇒ **归属订正成立**；⚠️ 另注：`Sea-user-rpc` / `Sea-user-api`
>    （真源 Go 服务）与 `redis:latest` 等**不属于**这两类 ⇒ ⇒ **别把它们算进任何一类**。

### 34.5 ⚠️ **纪律（本条是本轮真正要留下的东西）**

1. **一次失败不判回归** ⇒ ⇒ 必须「① 单独复跑 ＋ ② 代码变更面 ＋ ③ 环境普查」**三条齐**才定性；
2. **「重跑绿了」≠「已修」** ⇒ ⇒ 这一条**没有代码可修** ⇒ ⇒ 只能记成**环境瞬时**（与 §32.4 同一纪律）；
3. 🔴 **「PG 连接失败」先问「那个端口上回应我的是不是 PG」** ⇒ ⇒ **`EOFException` 出现在 `enableSSL` ⇒ 强烈指向「对端不是预期的服务」**，
   **不要**直接读成「PG 挂了」⇒ ⇒ 否则会去查一个根本没坏的东西；
4. 🔴 **不动别人的容器** ⇒ ⇒ 25 个残留里混着别的 lane 正在用的 ⇒ ⇒ **本轮不删**（不碰别人的进程与端口）⇒ ⇒ 登记 ＋ 通报。

### 34.6 落点（跨 lane ⇒ 无人拥有面）

「Testcontainers 残留容器 ＋ 随机宿主口落在 OS 临时段」是**跨 lane 共享环境**问题 ⇒ ⇒
落点 = **合并收口执行者**（`docs/DRIFT/_cross/**` = 无人拥有面）⇒ ⇒ **我域零落码**。
⇒ ⇒ **可选修法（仅登记，不实施）**：① 固定 Testcontainers 宿主口段（避开 `49152–65535`）② 跑完显式清理
③ 统一「容器名 ＋ 标签」回收（本机 Ryuk 不可用 ⇒ 需自建）。

## 35. 🟢 **C7 的「最小充分集」判据：共享层提交按【变更面】分级（2026-10-27 晚）**

### 35.1 判据的事实基础（实测，非推断）

`service/common` **不发布 `test-jar`**（`service/common/pom.xml` 无 `test-jar` 目标）
⇒ ⇒ 我域 6 个模块只依赖 `common` 的 **main jar**（`pom.xml` 里 `<artifactId>common</artifactId>`，无 `<type>test-jar</type>`）
⇒ ⇒ ⇒ **`service/common/src/test/**` 根本不在我域的测试 classpath 上**
⇒ ⇒ ⇒ ⇒ ⇒ **⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ ⇒ 共享层的「test-only 提交」在【构造上】不可能改变我域的测试结果。**

### 35.2 三档 C7 义务（按共享层提交的变更面分级）

| 共享层提交类型 | 我域的 C7 义务 | 依据 |
|---|---|---|
| 仅 `service/common/src/test/**` | ① 复跑 `service/common` 自身测试（**改动所在模块必须跑**）② 复跑**耦合最紧的 1 个域模块**（我域 = `user-rpc`：三层 MP 桥守门 ＋ 5 份 Redis FastFail 守门 ＋ 容器装配守门都在这）⇒ ⇒ **其余 5 个域模块的条数沿用上一次全量** | §35.1 ＋ 改动面 |
| 含 `service/common/src/main/**` | **域级全量重跑**（我域 669 条） | 生产代码进我域 classpath ⇒ ⇒ 影响面不可推断 |
| 仅 `docs/**` 或 `tools/**` | 复跑**受影响的守门脚本** | 零运行时影响 |

### 35.3 本轮实测（两例，都按上表执行）

- `139b428`（`tools/check-ledger-selfcheck.py` ＋ 共享层测试 ＋ global 台账）⇒ ⇒ 域级全量重跑 ⇒
  第一次撞上环境 flake（§34）⇒ ⇒ 复跑 18/18 SUCCESS，**我域 669 条逐项一致**；
- `407c230`（仅 `service/common/src/test/.../GoStyleClientDisconnectProbe.java` ＋ global 台账）⇒ ⇒
  按本表：① `service/common` = **330 条全绿** ② `user-rpc` = **144 条全绿** ⇒ ⇒ **零回归**（我域其余 5 个模块沿用上一轮全量）。

⚠️ **反面提醒**：**「共享层提交 ⇒ 一律跳过」是错的** ⇒ ⇒ 共享层**自身**可能红（本轮 G4 的第十四次全量就撞上了同一类 flake：
它用 `-rf :common` 续跑、且 `common` 这次没复现 ⇒ ⇒ **同一个 Testcontainers 端口冲突，两条 lane 都撞上了**）
⇒ ⇒ ⇒ **共享层自己的测试必须跑，不能借「它只是 test」跳过。**

## 36. 🟢 **Testcontainers 宿主口段落码（G4 §4.10.39 分配）—— 我域两段的落码项（✅ 已完成 · 2026-10-27 · 见 §45）**

> 🟢 **状态订正（2026-10-28）**：本节标题原写「**（未完成）**」⇒ ⇒ **已过时** ⇒ ⇒
> **落码已完成** ⇒ ⇒ 逐条（形态选择 · 38 个容器起点 · 2 台域级守门 · 变异三连 · 回归口径 · 边界）⇒ ⇒ [`§45`](#45-🟢-testcontainers-宿主口钉段落码g4-41039-②-的域侧那一半-38-个容器起点全部显式绑口--2-台域级守门2026-10-27--g1-lane)
> ⇒ ⇒ **G4 §4.10.39 ② 的「第一个未实测边界」（显式绑口后 `getMappedPort()` 是否返回那个固定口）= 是** ⇒ ⇒ 逐条 ⇒ [`§39.1`](#391-🟢-g4-41039-②-的第一个未实测边界显式绑口后-getmappedport-是否返回那个固定口-是)
> ⇒ ⇒ ⚠️ **本节以下内容保留原样（不重写历史）** ⇒ 其中标「🟡 未做」的那几行**已被 §39/§45 覆盖**。
>
> ⚠️ **锚点纪律（本条自己踩过一轮 · 2026-10-28）**：上面两个锚点**第一版是我手写的** ⇒ ⇒
> `check-doc-links.py` 实测 **2 项锚点失效** ⇒ ⇒ **用脚本 `slug()` 实算**（不是凭印象改标题）才改对 ⇒ ⇒
> 真实的三个失配点（**每一条都实测过，不是推断**）：
> ① **`.` 被 `slug()` 删除但【不补连字符】** ⇒ ⇒ `§4.10.39` 的真 slug 是 **`g4-41039`**
>    ⇒ ⇒ 我手写成 `g4-4-10-39` ⇒ ⇒ **点分段是分段，slug 不是**；
> ② **`+` / `——` / `（）` / `：` / `「」` 属 `Po`/`Pd`/`Ps`/`Pe` ⇒ 被删除** ⇒ ⇒ 删除后留下的**两侧空格合并 ⇒ `--`**
>    ⇒ ⇒ 我手写成单 `-` ⇒ ⇒ 同样错；
> ③ **我凭印象改写了标题原文** ⇒ ⇒ §45 我多加了「users 域」、**漏了「全部显式绑口」**；§39.1 我**漏了 `②`**。
> ⚠️ **顺带订正我自己的第一版归因**：我第一版写的是「**`🟢` / `②` / `：` 导致算法与手写不一致**」⇒ ⇒ **错** ⇒ ⇒
> **`🟢`（类别 `So`）与 `②`（类别 `No`）在 `slug()` 里都被【保留】** ⇒ ⇒ **它俩根本不是原因**
> ⇒ ⇒ **真正的规律是「凡标点被删 ⇒ 不补连字符；连字符只由空格产生」**。
> ⇒ ⇒ ⚠️ **教训**：**锚点不要手写** ⇒ ⇒ 要么**用脚本算**，要么**写纯文字「本文件 §NN」** ⇒ ⇒ **两者都比手写强**。

| 域 | 分配段 | 归属 |
|---|---|---|
| `users`（user/user-api · user/user-rpc · admin/admin-api · admin/admin-rpc） | **22700–22729**（30 口） | **G1（本 lane）** |
| `follow`（follow/follow-api · follow/rpc） | **22730–22759**（30 口） | **G1（本 lane）** |

⇒ ⇒ **为什么必须做**：§34 已坐实「**Testcontainers 每次起容器抢的随机口落在 OS 临时段 `49152–65535`
⇒ ⇒ 而那段被手工起的对拍容器占着**（55562–55569 等）⇒ ⇒ 撞口 ⇒ ⇒ 症状 = `EOFException@enableSSL`
⇒ ⇒ **G2/G3/G4 的第十四次全量也撞了同一类**（G4 用 `-rf :common` 续跑、`common` 这次没复现）
⇒ ⇒ **把宿主口钉到分配段 = 从根上消除这一类随机失败**。

⚠️ **状态：🟡 已登记，落码未做**（本轮窗口用于根因取证与台账；落码是本 lane 的下一件事）
⇒ ⇒ **落码口径（待做）**：把我域 Testcontainers 容器的宿主口绑定到分配段（`22700–22729` / `22730–22759`），
⇒ ⇒ **不落在 `49152–65535`** ⇒ ⇒ ⇒ **且不得与任何已占段/预分配段重叠**。
⇒ ⇒ ⚠️ **落码时必须守三条**：① **不把「钉口」写成「端口写死」**（同段内仍要能并行起多个容器）
② **端口占用要可自愈**（被占时换同段内下一个，而不是直接失败）③ **改动后必须复跑全量**（钉口会改容器启动路径 ⇒ ⇒ 影响所有用 Testcontainers 的用例）。

⇒ ⇒ 🟢 **落码形态已被 G4 定死（2026-10-27 22:2x 读 G4 §4.10.39 ②）** ⇒ ⇒ **不再阻塞**：
`PostgreSQLContainer` **不能**直接换成 `FixedHostPortGenericContainer`（后者不带 PG 初始化/JDBC 支持）
⇒ ⇒ 正确形态 = `PostgreSQLContainer` + `withCreateContainerCmdModifier(...)` **显式绑口**
⇒ ⇒ ⚠️ G4 同时留了**两个未实测的边界**：「显式绑口后 `getMappedPort()` **是否返回那个固定口**」未实测
⇒ ⇒ ⇒ **域侧落地时必须先写一条探针验证，不能照抄**（⇒ 落码第 0 步）。

## 37. 🔴 **G3 来信「本机 Docker 宿主→容器端口转发对多数容器失效」的复检（2026-10-27 22:2x）** —— 结论：**转发完好，「机器级阻塞」不成立**

### 37.1 来信要点（转述，**不采信** ⇒ 下面全部自己重扫）

G3 报（观测窗口 13:50~13:58）：本机 Docker「宿主→容器」端口转发对**多数容器**失效 ⇒ 三层探针：
① 线程栈停在 `org.postgresql.core.v3.ConnectionFactoryImpl.doAuthentication`（读不到 PG 的 auth 包）；
② 容器内 `docker exec ddl-favorite-pg psql …` 连得上（仅报 role 不存在）⇒ 容器内服务正常；
③ 宿主 socket 探针「多数映射口 TCP 连上但 3~5s 读不到任何字节」（PG/Redis/Kafka/Nacos/minio/grafana/prometheus/etcd/beanstalkd 全中），
而 `Sea-user-rpc`（收 32 字节）/ `milvus`/`jaeger`/`neo4j`（立即 0 字节 EOF）正常
⇒ ⇒ 推断「约 30 个容器中多数转发失效，疑似代理资源耗尽」⇒ ⇒ 需 Jason / 合并收口执行者拍板是否重启 Docker Desktop。

### 37.2 🔴 我方复检方法（**按 G3 同款判据先复现，再换判据**）

**第 1 步 · 复现他的裸连判据**（`connect` 后 4s 内读不到任何字节 = 失效）⇒ 扫 43 个映射口：

| 族 | 端口数 | 裸连结果 |
|---|---|---|
| PG（发 SSLRequest，期待 `b'N'`/`b'S'`） | 18 | **18/18** `b'N'` |
| Redis（发 `PING`，期待 `+PONG`） | 8 | **8/8** `+PONG` |
| HTTP（发 `GET /`，期待状态行） | 4 | **4/4** `301/302/403/403` |
| gRPC（`Sea-user-rpc` 49210） | 1 | **1/1** SETTINGS 帧 |
| **裸连型**（Kafka×3 / Nacos×2 / etcd / beanstalkd×2 / milvus / Sea-user-api） | 10 | **10/10 「超时无字节」** |

⇒ ⇒ **裸连判据在我这台上把 10 个**完全健康**的服务判成失效** ⇒ ⇒ ⇒ **这就是 G3 那条推断的断点**。
> 🔴 **订正注记（2026-10-28 12:2x · G3 来信订正【归属】· 保留上文原文不改写）**
>
> **G3 已明确**：上文 §37.1 引的那封「机器级阻塞」信**确实是 G3 发给我域的**，但信里引用的
> ① **裸 socket 探 PG**（`create_connection` → `sendall(b'\x00')` → `recv`）与
> ② **JVM 卡在 `doAuthentication` 的线程栈** —— **这两件实测都是【G4 侧】的**
> ⇒ ⇒ **G3 域从未上报过机器级阻塞** ⇒ ⇒ **我域台账里「G3 那条推断」「按 G3 同款判据」这两处归属要改口**：
> - **「那封信的发出方」＝ G3**（原文不误）⇒ ⇒ **「探针与栈的产出方」＝ G4**（本条订正）⇒ ⇒ **§37 的结论不变**（转发完好、机器级阻塞不成立、我域反证成立）⇒ ⇒ **只有归属被更正**。
> - ⇒ ⇒ **教训（已记 memory）**：「转述里夹带的【证据出处】」也可能被安到**错的人头上** ⇒ ⇒ **回信里点名「某某的探针」时，要核它到底是谁做的** —— 我域当时是**照着 G3 的信**复现的，**没去问那两件东西是谁做的** ⇒ ⇒ **不是 G3 冒领，是我没核**。


**第 2 步 · 改用「发协议正确请求 ⇒ 要协议级应答」**（同一批 10 个 + 2 个补探）：

| 端口 | 发什么 | 收到什么 |
|---|---|---|
| `Sea-etcd` 49319 | `GET /version` | `HTTP/1.1 200 OK` |
| `Sea-nacos` 49848 · `nacos-standalone` 8848 | `GET /` | `HTTP/1.1 404`（服务在，只是没这个路由） |
| `Sea-beanstalkd1/2` 49300/49301 | `stats\r\n` | `OK 902\r\n---\ncurrent-jobs-urgent: 0 …` |
| `Sea-milvus` 49091 | `GET /healthz` | `HTTP/1.1 200 OK` |
| `Sea-user-api` 49900 | `GET /` | `HTTP/1.1 404` + JSON 头 |
| `Sea-neo4j` **49474** | `GET /` | `HTTP/1.1 200 OK` |
| `Sea-jaeger` **49317** | `GET /` | HTTP/2 SETTINGS 帧 |
| Kafka 59993 / 39092 / 49992 | `ApiVersions v1`（**size=14**） | **256 字节合法 ApiVersions 响应** |

⇒ ⇒ **12/12 全部有协议级应答** ⇒ ⇒ **加上前 3 族 ⇒ 43 个映射口 43/43 全通**。

### 37.3 🔴 三处方法学缺陷（**这条才是要留给下一轮的**）

| # | 缺陷 | 实证 |
|---|---|---|
| ① | **用「裸连读不到字节」当「转发失效」的判据** | PG / Redis / Kafka / Nacos / etcd / beanstalkd / milvus / HTTP 全部是**请求-响应型**服务 ⇒ **不主动发第一个字节** ⇒ 裸连必然「超时」⇒ **误报生成器**（我 10/10 复现） |
| ② | **探了容器内口，不是宿主映射口** | `neo4j` 宿主映射口是 **49474**（容器内 7474）· `jaeger` 是 **49317**（容器内 4317）⇒ G3 报的 2 条「正常 = 立即 0 字节 EOF」实为**探错地址** |
| ③ | **帧长算错 ⇒ 把「我的请求不合法」读成「对端不回」** | Kafka `ApiVersions` 我自己填 `size=16`、实际载荷 **14** 字节 ⇒ broker 等多出的 2 字节 ⇒ 超时 ⇒ **改 `size=14` 立刻拿到 256 字节响应** ⇒ ⇒ **「超时」先怀疑自己发的字节** |

⇒ ⇒ **判据式（本域沿用）**：**「连接成功但没数据」有三种互斥解释 —— ① 服务不主动说话 ② 探针地址错 ③ 我发的字节不合法**
⇒ ⇒ ⇒ **三者必须逐一排除，才能说「路径不通」** ⇒ ⇒ ⇒ **单看「连上了但没数据」推不出「转发失效」**。

### 37.4 ⚠️ 「机器级阻塞」这个前提**不成立**（三条独立判据）

| 判据 | 实测 | ⇒ |
|---|---|---|
| ① **此刻转发是否还坏** | 43/43 映射口协议级全通（§37.2） | ⇒ **不是持续状态** |
| ② **Docker Desktop 是否被重启过** | 47 个容器绝大多数 `Up 7 days` / `Up 26 hours` / `Up 3 days` | ⇒ **Docker Desktop 7 天内没重启过** ⇒ ⇒ 若当时是**真·机器级持续故障**，此刻仍应坏 ⇒ ⇒ **反证「持续故障」** |
| ③ **临时段实际占用密度** | `49152–65535` 段当前被占 **205 / 16384**（`ESTABLISHED` 106 · `LISTEN` 56 · `SYN_SENT` 21 · `TIME_WAIT` 10 · `CLOSED` 7 · 其他 5）⇒ **99.6% 空闲** | ⇒ ⇒ **「代理资源耗尽」与实测密度不符**（段并没有被占满）⇒ ⇒ ⇒ **那个假设本身不成立** |

⇒ ⇒ **处置结论：🔴 「重启 Docker Desktop」**不需要**拍板** —— 代价是**干掉 47 个容器**
（各 lane 跑了 26~45 小时的 `ddl-*-pg` / `difftest5k-*` / `cdifftest-*` 等），收益为 **0**
⇒ ⇒ ⇒ **我未做任何跨 lane 破坏性处置**（§5.3d）⇒ ⇒ ⇒ **此条【不升级给 Jason】**。

### 37.5 ⚠️ 残留真现象（我**不能**复现的那部分）与其最接近的解释

- G3 的 **JVM 卡在 `doAuthentication`** 是**真现象**（栈是实测的）⇒ ⇒ **不因 37.3 的判据缺陷就把它一笔抹掉**。
- 我在 22:2x **复现不出**（43/43 全通）⇒ ⇒ 只能登记为**未复现**。
- **最接近的解释 = §36 的撞口**（不是 Docker 代理耗尽）：Testcontainers 的宿主口由 Docker 随机分配，
  而宿主 `49152–65535` 段**同时被出站连接的临时源口占用**（当前 106 个 `ESTABLISHED`）⇒ ⇒
  **随机口撞上出站源口** ⇒ 对端不是预期服务 ⇒ 症状恰好是 §34 记的 `EOFException@enableSSL` / 卡在读 auth 包。
- ⇒ ⇒ **这条不能证明「当时就是撞口」**（撞口是**概率性**的，205/16384 ⇒ 约 1.25%/次）⇒ ⇒ 记为**候选解释**，**不记为根因**。
- ⇒ ⇒ **但它把 §36 的优先级抬起来了**：既然「随机口落进临时段」是**唯一在机制上能同时解释「PG 握手卡死」和「端口上不是预期服务」**的因素，
  ⇒ ⇒ ⇒ **把宿主口钉到 `22700–22729` / `22730–22759` 就是对它的一劳永逸消除** ⇒ ⇒ **§36 从「登记」升级为「有机制支撑的必修项」**。

### 37.6 落点与下一步（**我域可自修 ⇒ 不再等 G4**）

| 项 | 落点 | 状态 |
|---|---|---|
| 钉口**形态** | G4 §4.10.39 ② 已定死：`PostgreSQLContainer` + `withCreateContainerCmdModifier(...)` | 🟢 **已明确** |
| 钉口**落码** | **我域测试类**（`service/users/**/src/test/**` + `service/follow/**/src/test/**`）⇒ **我写权内** | 🟡 **下一件事** |
| 钉口**探针** | G4 要求：先验证「显式绑口后 `getMappedPort()` 是否返回那个固定口」⇒ **落码第 0 步** | 🟡 **未做** |
| 白名单清理 | G4 `GoStyleRedisClientShadowingGateTest.LEGACY_DOMAIN_COPIES` 的 user-api 条目（**顺序：先合我的删除，再清**） | ⏳ 等 G4 |
| G3 的 rpc 侧活体对拍 | G3 ⇒ 我域 `DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013` 才可升 L4 | ⏳ 等 G3 |

⚠️ **本节不撤销 G3 的任何实测**（栈、容器内 psql、映射口清单都保留在案）⇒ ⇒ **只订正「从这些观测推出『多数容器转发失效 ⇒ 机器级阻塞 ⇒ 需重启 Docker』」这一步推理**。

## 38. 🔴 **订正我自己的「coupling 由 1 转 0 ⇒ 6/6 门禁全绿」断言 —— 那是假绿，而且是我自己记过的坑（2026-10-27 22:3x）**

### 38.1 事实链（本轮用**好写法**跑出来的：`rc=$?` 先捕获再打印）

| 项 | 实测 |
|---|---|
| `check-wave-coupling.py` | **EXIT=1** |
| 红项 | `common_radiation` · `GoStyleRedisClientAutoConfiguration` ⇒ **文档快照 `None`** vs **仓库现状 `['users']`** |
| 五键逐项差量 | `pom_dependents` **0** · `grpc_edges` **0** · `cross_domain_topics` **0** · `users_common_classes` **0** · `common_radiation` **1**（**仅此一项**） |
| 这一项**是不是新的** | **不是** —— **§26.10**（Redis 收编轮）已把它定性为「收编的**预期后果**」＋「落点在 `docs/DRIFT/_cross/**` = 无人拥有 = 合并收口执行者」＋「精确修法 = `common_radiation` 加一项」；**§28**（排序桥收编轮）也明写「仍是收编前那一条 ⇒ **项数未增** ⇒ 与本次收编无关」 |

⇒ ⇒ **⇒ 它从收编落码那笔（`312a09b`）起就一直是红的**，经过 4 轮**仍未被合并收口执行者修**。

### 38.2 🔴 **我的「转绿」判据是错的（订正）**

- **我报过的**：「并入 G4 tip 后 `check-wave-coupling` **由 1 转 0** ⇒ 6/6 门禁全绿」。
- **我当时的判据**：去 `docs/DRIFT/_cross/cross-domain-coupling.md` 里 grep 有没有 `GoStyleRedisClientAutoConfiguration` ⇒ **命中 0** ⇒ 读成「红项消失」。
- 🔴 **0 命中的正确含义是「快照从没见过这个类」** —— 而守门的 `diff()` 对 **`set(期望) | set(现状)`** 求差
  ⇒ ⇒ **只出现在「现状」里的键 = 漂移** ⇒ ⇒ ⇒ **「快照里搜不到」是红项**存在**的证据，不是它**不**存在的证据。**
- ⚠️ **§26.10 最后一条已经把同一个坑写下来了**（`$(basename $g)` 这个命令替换会**覆盖 `$?`** ⇒ 红门禁被报成绿）
  ⇒ ⇒ ⇒ **⇒ 台账写了 ≠ 行为改了** ⇒ ⇒ ⇒ **⇒ 必须把它固化成「每轮跑门禁只用那一条好写法」**
  ⇒ ⇒ ⇒ **本轮用的是好写法 ⇒ ⇒ ⇒ **红是真的**（这条比「快照搜不到」更硬）。
- ⇒ ⇒ **本节只订正结论，不重写历史** ⇒ ⇒ §26.10 与 §28 的记载（**都记的是 1**）**仍然有效**；
  ⇒ ⇒ §802 与 §1570 那两处「5/5 全 EXIT=0」**发生在 `312a09b` 之前**（合并态、收编落码前）⇒ ⇒ **那两处当时是真的绿**，**不订正**。

### 38.3 精确修法（**一行**）+ 归属

- **修法**（合并收口执行者侧）：在 `docs/DRIFT/_cross/cross-domain-coupling.md` 的快照 json 的 `common_radiation` 里补一行 ——
  `"GoStyleRedisClientAutoConfiguration": ["users"],`
- **归属**：该文件 ∈ `check-wave-lane-ownership.py` 的 **`SHARED`（无人拥有）清单**
  ⇒ ⇒ **G1 不能写，G4 也不能写**（同一份清单对所有 lane 一视同仁）⇒ ⇒ **只能合并收口执行者写** ⇒ ⇒ **已转派协调会话**。
  - 🔴🟢 **2026-10-28 订正注记（本句「已转派协调会话」被证伪 · 原文一字未改）** ——
    那个会话 **自己** 发了角色边界通告：**它是【临时故障处置会话】，不参与 G1~G4 的任何任务**
    ⇒ **共享面 / 合并收口一律不归它** ⇒ ⇒ **「已转派协调会话」这个落点是错的**。
    ⇒ ⇒ **正确落点 = 「合并收口那一步」（一个【步骤】，不是常驻会话）** ⇒ ⇒ **登记即可，不必再发信**
    ⇒ ⇒ 详见 **§51.2**。⚠️ **行号引用不变**（结论「只能合并收口执行者写」仍然成立）⇒ **只是「转派给了谁」这一层被订正**。
- ⚠️ **给合并收口执行者的口径提醒**：**权威快照必须在所有 lane 合入之后、从合并后的树重算**
  （`python3 tools/difftest/check-wave-coupling.py --print`）⇒ ⇒ 否则其他域的 `common_radiation` 也会对不上
  ⇒ ⇒ ⇒ **上面这一行是「只解我域阻塞」的最小增量，不是最终快照。**

### 38.4 ⇒ 对我域的影响（**零代码缺陷，但「守门 5/5 全绿」这条提交前提当前不满足**）

- 我域代码**零缺陷**：`312a09b` 那 2 处 import 是 Redis 收编的**正面证据**（域内副本已删 ⇒ 断连策略改由共享层那份提供 ⇒ `users` 域第一次出现在辐射度里）。
- 但「**守门 5/5 EXIT=0 是提交前提**」这一条**当前不满足** ⇒ ⇒ 差**共享面**的一行更新 ⇒ ⇒ **我域无自修路径** ⇒ ⇒ **转派，不阻塞我域其余落码**。
- ⇒ ⇒ **本轮结论**：域内 5 个守门 **EXIT=0**（`lane-ownership` · `role-model` · `doc-links` · `ledger-count` · `ledger-selfcheck`）
  ＋ **`coupling` EXIT=1**（**共享面快照过期** ⇒ 已转派 ⇒ 非本域缺陷）⇒ ⇒ **6 个里 5 绿 1 红，红的那条已定性、已转派、有一行修法。**

## 39. 🟢 **钉口落码第 0 步：G4 留下的未实测边界有答案了（2026-10-27 22:3x）** —— 探针已落码 ＋ 两次变异验证

### 39.1 🟢 **G4 §4.10.39 ② 的第一个未实测边界：「显式绑口后 `getMappedPort()` 是否返回那个固定口」= **是****

| 项 | 结论 | 证据 |
|---|---|---|
| 显式绑口后 `getMappedPort(5432)` | **等于**指定的固定宿主口 | 实测 `expected: <22700> but was: <22701>`（变异 B 的红 ⇒ 断言是活的） |
| 固定口是否落在分配段 | **是**（`22700–22729`） | 探针第 2 条断言 |
| 固定口**真连得通** | **是**（JDBC 建表 → 查回） | 探针第 3 条断言 ⇒ **「绑定记录好看 ≠ 端口通」被排除** |
| `getJdbcUrl()` 是否指向那个固定口 | **是** | 探针第 4 条断言 ⇒ **「钉口只是容器层记录」被排除** |

⇒ ⇒ **证据强度 = L4（真 PG 活体 · 真进程 · 真 Docker 端口发布）** ⇒ ⇒ **G4 §4.10.39 ② 的第 ② 条边界，答案 =「可以落地」**。

### 39.2 🔴 三条机制事实（**读消费点 + 变异验证得出，不是猜**）

| # | 事实 | 怎么得到的 |
|---|---|---|
| ① | `HostConfig.withPortBindings(List)` 是**整体替换**，不是追加 ⇒ ⇒ 必须**「剔除该容器口的旧绑定」＋「加固定绑定」两步齐做** | **变异 A**：只剔除不加 ⇒ **容器起不来**（`ContainerLaunchException`）⇒ ⇒ 因为**一个口都没被发布** ⇒ ⇒ 反证「固定绑定就是唯一的发布来源」 |
| ② | modifier 运行时 **`cmd.getExposedPorts()` 已经填充** ⇒ ⇒ 「**逐口钉**」可行 | 探针第 4 条断言（`EXPOSED_PORTS_SEEN_BY_MODIFIER` 捕获）⇒ ⇒ ⚠️ **多口容器必须逐口钉**：只钉一口 ⇒ 另一口的绑定被剔除却没补回 ⇒ 启动失败（与 ① 同源） |
| ③ | `GenericContainer.addFixedExposedPort(int,int)` 是 **`protected`** ⇒ 域侧测试类**不可直接调用** ⇒ ⇒ 公开且正交的路径**只有** `withCreateContainerCmdModifier` | `javap` 读 Testcontainers 2.0.5 的 `GenericContainer` ⇒ ⇒ **G4 §4.10.39 ② 指定的形态是对的** |

### 39.3 🟢 **落码形态可以做到「38 个调用点零改动」**（读 2.0.5 的 ServiceLoader 钩子）

- `GenericContainer.loadCreateContainerCmdCustomizers()` ⇒ **`ServiceLoader.load(CreateContainerCmdModifier.class)`**（`javap` 实测）
  ⇒ ⇒ ⇒ **每个模块放一个 `META-INF/services/org.testcontainers.core.CreateContainerCmdModifier` + 一个实现类
  ⇒ ⇒ ⇒ 该模块内**所有**容器都被钉** ⇒ ⇒ ⇒ **38 个调用点一个都不用改**。
- ⚠️ **实现类必须**：① **逐口钉**（用 `cmd.getExposedPorts()`）② **保留其它容器口的既有绑定** ③ **段内自愈**（被占就换同段内下一个 ⇒ 同段内仍能并行）
- ⚠️ **段号要写进实现类** ⇒ 6 个模块 6 份实现（**无合法共享落点**：`service/common` 不发布 test-jar ⇒ 共享层那份我域收不到；`users-common` 是 G4 写权且 follow 不用它 ⇒ ⇒ **只能域内各持一份**）
- ⚠️ **必须配「守恒断言」**（AGENTS.md §3.3：「脚本批量改 N 处 ⇒ 必须配覆盖全部 N 处的守恒断言」）：
  **6 个模块都有服务文件** ＋ **段号各自落在 G4 分配的段内** ＋ **6 份实现内容一致**（防「改了一份忘了另外五份」）。

### 39.4 ⚠️ **本轮我自己踩的两个坑（都要留档）**

| # | 坑 | 现象 | 判据式 |
|---|---|---|---|
| ① | **PG 的 JDBC 对 DDL 的 `executeUpdate` 返回 `0`**（不返回 `1`） | 探针第一次跑红的是**我的断言写错了**，不是「端口不通」⇒ ⇒ **差点把「绿测试是错误答案的守卫」又犯一次** | ⇒ DDL 不要用 `executeUpdate` 的返回值做断言 ⇒ **「建了表就查回来」** |
| ② | **变异里的 `//` 注释吞掉行尾 ⇒ 变成编译错误** | 变异 B 第一次「红」是 `Compilation failure` ⇒ ⇒ **红得毫无意义** | ⇒ ⇒ **变异必须「编译通过」且「红在目标断言上」才算证据**（本次有效变异 B：`Failures: 1, Errors: 0`，红在 `assertEquals`） |

### 39.5 状态

| 项 | 状态 |
|---|---|
| 探针（`UserRpcTestcontainersHostPortSegmentGateTest`，4 条断言 ＋ 2 次变异验证） | 🟢 **已落码 · 已变异验证**（`mvn -pl service/users/user/user-rpc -Dtest=…` ⇒ 绿；变异 A/B ⇒ 红在目标断言） |
| **6 个模块的服务文件 ＋ 实现类 ＋ 守恒守门** | 🟡 **未做**（§39.3 已把形态定死，下一轮落码） |
| 落码后必须复跑 | 🔴 **域级全量必须重跑**（钉口会改**所有** Testcontainers 容器的启动路径 ⇒ ⇒ 38 个文件全部受影响） |

## 40. 🔴 **G3 第二轮来信（U+FFFD 归属 + rpc 侧活体对拍「仍被机器级转发故障卡住」）的复检（2026-10-27 22:4x）**

### 40.1 🟢 (a) 那 3 份「真损坏且无人拥有」的 U+FFFD —— **已清零，待办关闭**（G4 已认领并修完）

| 文件 | 我树复扫 |
|---|---|
| `tools/errmsg-probe/README.md` | **0** |
| `tools/sql-statement-probe/favorite/compare.py` | **0** |
| `component-semantic-verification/springboot-semantic-model.md` | **0** |

⇒ ⇒ G4 的 `cc6e38e` / `f68a9cd` **均为我 HEAD 的祖先** ⇒ ⇒ **合并时取 G4 树版本** ⇒ ⇒ **我域零改动、零派单**。

### 40.2 🔴 (b) **G3 点名的 4 个 PG 口：全部协议级应答 ⇒ 「机器级转发故障」在他点的口上也不成立**

| 域 | 宿主口 | 发什么 | 收到 | 耗时 |
|---|---|---|---|---|
| article | 55560 | SSLRequest（8 字节） | `b'N'` | 4ms |
| hot | 55562 | SSLRequest | `b'N'` | 0ms |
| favorite | 55564 | SSLRequest | `b'N'` | 0ms |
| comment | 55566 | SSLRequest | `b'N'` | 0ms |

⇒ ⇒ `b'N'` = **PG 对 SSLRequest 的正确首字节应答**（非 SSL 模式）⇒ ⇒ ⇒ **G3 的 rpc 侧活体对拍前置【是满足的】⇒ 不用等 Docker 恢复。**

### 40.3 🔴 **G3 的 `127.0.0.1:6379` refused = 探了【容器内口】，不是转发故障**

- 宿主上**根本没有 6379 监听**（6379 是容器内口）⇒ ⇒ **refused 是「没有监听者」，不是「有监听者但不回」**。
- 本机 8 个 Redis 宿主口，**逐个发 `PING` 全部 `+PONG`**：
  `36379`（`redis` 容器 · 独立 · Up 7 days）· `49380`（`Sea-redis`）· `56395`（cdifftest）· `56380`（taskprobe）·
  `56382` / `56390`（uudifftest）· `57379`（userprobe）· `61379`（difftest5k）
- ⚠️ **断连对拍建议用 `36379`** ⇒ **不要动 `61379`（`difftest5k-redis`）** —— 那是**我域 4 个进程也在用的共享容器** ⇒ 断它必须先约窗口（谁断/多久/谁恢复）。

⇒ ⇒ **判据式（跨 lane 可复用的第三条）**：
**「TCP 连上但读不到字节」不能直接读成「转发坏」⇒ 必须先发协议正确的首字节再判**
⇒ ⇒ PG 的首字节协议 = **SSLRequest**（`00 00 00 08 04 d2 16 2f`）⇒ 正常回 `b'N'` / `b'S'`；Redis = **`PING\r\n`** ⇒ 正常回 `+PONG`
⇒ ⇒ **裸连超时 ≠ 路径不通**（我同款裸连判据在本机复现出 **10 个假阳性**，见 §37.3 ①）。

### 40.4 🟡 **U+FFFD 基线订正：我域 = 15 处 / 5 份（含 1 处【刻意保留】）**

| 文件 | 处数 | 性质 |
|---|---|---|
| `docs/DRIFT/users/user/api.md` | 7 | 既有基线 |
| `docs/DRIFT/users/user/rpc.md` | 4 | 既有基线 |
| `docs/DRIFT/users/vs-common.md` | 2 | 既有基线 |
| `service/users/user/user-api/…/UserApiGoStyleBodyContentTypeTest.java` | 1 | 既有基线 |
| **`docs/Pitfalls/users/user/rpc.md:146`** | **1** | 🆕 **刻意保留** —— 该行是在**举例**「末字已不可恢复」的损坏串（形如 `'用户不<U+FFFD>?'`）⇒ ⇒ **不是缺陷，禁止「修」** |

⇒ ⇒ **全仓现况：41 处 / 15 份**（其余属 like/comment/message/favorite/article 各域 ⇒ 不是我的）
⇒ ⇒ **我域零新增**（相对既有基线只多出上面那处**刻意**的）。
⇒ ⇒ ⚠️ **本轮自踩的坑（与「在台账里描述坏锚点必须用代码格式」同族）**：
**我第一次写这一节时把那个坏字符【原样抄】进了表格 ⇒ 自己给自己加了 1 处新 U+FFFD**（2 → 3）
⇒ ⇒ ⇒ **判据式：台账里引用「含坏字符的原文」时，必须用转义/描述（如 `<U+FFFD>`），不能原样粘贴**
⇒ ⇒ ⇒ 已改回转义写法 ⇒ **复扫确认回到 2 处**（我域 15 处 / 5 份）。

### 40.5 🔴 **写权口径订正：`docs/Pitfalls/<域>/**` 不属于任何 lane（我早前的理解是错的）**

- 我早前依据 AGENTS.md §5.3b（「产出**必写回**同名的**本域子目录**」）以为 `docs/Pitfalls/users/**` 是我的写权面 ⇒ ⇒ **错**。
- **机器准绳**（`tools/difftest/check-wave-lane-ownership.py` 的 `ACTIVE` 白名单）里**四个 lane 全部只列 `docs/DRIFT/<域>/**`，没有任何 lane 含 `docs/Pitfalls/**`** ⇒ ⇒ **那面是「无人拥有面」**。
  （守门里的 `EXCLUSIVE` 条目 `["service/favorite/**", "docs/DRIFT/favorite/**", "docs/Pitfalls/favorite/**"]` 是**排他归属声明** —— 声明「favorite 域只归 G3」，**不是**给 G3 开 `docs/Pitfalls/favorite/**` 的写权。）
- ⇒ ⇒ **§40.4 那处刻意 U+FFFD 只能【登记】、不能改** —— 幸好它本来就该保留。
- ⇒ ⇒ **顺带一条给所有 lane 的提醒**：AGENTS.md §5.3b 那句「必写回本域子目录」在 `docs/DRIFT/` 上成立；在 `docs/Pitfalls/` 上**不成立** ⇒ ⇒ **两者读起来像同一件事，写权面却不同** ⇒ ⇒ **以守门白名单为准**。

## 41. 🟡 **G4 `d4e4745`（改守门脚本）—— 我【持住不合并】+ 配对条件（2026-10-27 22:4x）**

| 项 | 事实 |
|---|---|
| G4 这笔改了什么 | `tools/difftest/check-tomcat-limits.py`（**守门脚本** ⇒ **生产代码零改动** ⇒ ⇒ 按 C7 判据只属「仅 tools」档 ⇒ 只需跑受影响守门，**不必**跑域级全量） |
| G4 自报的顺序约束 | 「本单使门禁在**本树**转红（task-api yml 仍是 raw）—— 这是**正确的红**（检出的是真漂移）⇒ **必须与 G2 的 task-api yml 订正 `4786eb5` 同批合入**」 |
| 🔴 **我方独立复核（三条，都成立）** | ① 我树 `service/task/task-api/src/main/resources/application.yml:178` = **`mode: raw`** ⇒ ⇒ 与 G4 所述一致<br>② **G2 的 `4786eb5` 不在我树** ⇒ ⇒ 配对的另一半缺失<br>③ `check-tomcat-limits.py` ∈ AGENTS.md §7.2 列的**六个标准守门**之一 ⇒ ⇒ 它红**就是**提交前提不满足 ⇒ ⇒ 不能当成「预期内红」放过 |
| 我树合并前的状态 | `check-tomcat-limits.py` **EXIT=0** ✅ |
| ⇒ ⇒ **决定** | **持住不合并 `d4e4745`** ⇒ ⇒ 我树维持「落后 G4 1 笔」**且是刻意的**（不是漂移、不是漏合）⇒ ⇒ 理由：**单合会把我树从绿变红，而这个红要等 G2 的另一半才能消** ⇒ ⇒ 与 Redis 白名单那条**顺序约束同族**（先合 A 再清 B，顺序不能反） |
| ⇒ ⇒ **解除条件** | G2 的 `4786eb5`（task-api yml `mode: raw` ⇒ `off`）进我树 ⇒ ⇒ **再合 `d4e4745`** ⇒ ⇒ 两者成对 ⇒ ⇒ `check-tomcat-limits.py` 应 EXIT=0（G4 已实测：把打好的守门跑在 G2 树 ⇒ EXIT=0） |

⇒ ⇒ ⚠️ **这不是「我落后了」的疏忽** ⇒ ⇒ **是成对落地的顺序约束** ⇒ ⇒ 已通报 G4 与协调会话 ⇒ ⇒ **合并时必须两笔同批**（谁先合谁负责等另一笔）。

## 42. 🔴 **G2 第六轮来信的两条复检 —— 一条成立并升级为波次判据，一条【根因定位被推翻】**

### 42.1 🟢 **G2 [3] 的「`-am` 那条的延伸」= 成立 ⇒ 升级为波次级判据**

- **我独立复核**（`grep -rln '<artifactId>users-common</artifactId>' --include=pom.xml service/`）：
  **`users-common` 的仓内消费者 = 8 个 api 模块**（`article-api` · `comment-api` · `favorite-api` · `follow-api` · `like-api` · `message-api` · `admin-api` · `user-api`）**＋ 2 个 rpc 模块**（`admin-rpc` · `user-rpc`）⇒ ⇒ **跨 4 条 lane**（G3 三家 / G1 三家 / G2 两家）。
- ⇒ ⇒ **不带 `-am`** ⇒ ⇒ 那 8 个 api 从 `~/.m2` 解析 `users-common` 的**旧 jar** ⇒ ⇒ **测的不是本树代码** ⇒ ⇒ **「跑绿是假绿」的同款**。
- ⇒ ⇒ **写权归属**：`service/users/users-common/**` ∈ `check-wave-lane-ownership.py` 的 `EXCLUSIVE`（**G4 写权唯一**）⇒ ⇒ **G4 改 `users-common` ⇒ 四条 lane 的域级回归都必须带 `-am` 重跑**。
- ⇒ ⇒ **我域 3 个 api（`follow-api` / `user-api` / `admin-api`）就在受影响的 8 个里** ⇒ ⇒ **我域那 669 条之所以可信，正是因为每轮命令都带 `-am`**（reactor 实跑含 `users-common` 本树源码）。
- ⇒ ⇒ **建议写进 C7 收口协议的两条**（我采纳并已固化进本 lane 的固定命令）：
  ① **域级回归命令必须带 `-am`**（且在报告里写明 **reactor 实跑了几个模块** ⇒ 才能证明 `users-common` 用的是本树源码）；
  ② **域级数字 = 本域模块的 surefire 目录逐文件求和**（我 669 / G2 668）⇒ **共享层单列**（两边共享层都 327）⇒ ⇒ **reactor 总数（996 / 995）不是域级数** ⇒ ⇒ **两边共享层同为 327 ⇒ 域级差的那 1 条与共享层无关**。
> 🔴 **订正注记（2026-10-28 12:2x · G2 来信订正【互校基线】· 保留上文原文不改写）**
>
> **上文 ② 里的数字（我 669 / G2 668 · 共享层两边都 327 · reactor 996 / 995）＝ 那一轮的快照，现在已过期**：
> - G2 侧**当前权威值**（2026-10-28 实测）：域侧 8 模块 **677** ＋ 共享层 **330** ⇒ **9 模块 = 1007**；
>   共享层随后在 C7 复跑（并入 G4 `a38ad75` 之后）＝ **331**。
> - 我域 §54 记录的共享层 `service/common` = **333**（2026-10-28 实测）⇒ ⇒ **两棵树的共享层数不等（333 vs 331）**
>   ⇒ ⇒ **根因候选 = 两棵树并入的 G4 尖端不同**（`service/common` 是 G4 的 EXCLUSIVE 面 ⇒ 它的条数**随 G4 提交变**）
>   ⇒ ⇒ **不推算**，归 G4 复核。
> - ⇒ ⇒ **「两边共享层同为 327 ⇒ 域级差的那 1 条与共享层无关」这条结论在【当前】数字下不再成立**
>   （共享层已不等 ⇒ 那 1 条的归属**不能**再靠「共享层相同」排除）⇒ ⇒ **保留原文 + 标注失效边界**。
> - ⇒ ⇒ **仍然成立的**：**「reactor 总数不是域级数」**（域级数 = 本域模块的 surefire 目录逐文件求和）
>   ＋ **「域级回归命令必须带 `-am`」**（`users-common` 是 G4 EXCLUSIVE ⇒ 8 个 api + 2 个 rpc 跨 4 条 lane）
>   ⇒ ⇒ **我域每轮命令都带 `-am`**（§42.1 的固定命令未变，**变的只是基线数字**）。


### 42.2 🔴 **`check-tomcat-limits.py` 的根因定位被推翻**（G2 / G3 / 我此前都报成「落点 `tools/difftest/**` 无人拥有 ⇒ 需合并收口执行者」⇒ ⇒ **错**）

- **G4 的 `d4e4745` 已经把「第二份判据」删掉了**：该版守门**不再维护期望表副本**，改为**直接读真源** `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java` 的 `SEMANTIC_REACHABILITY` ⇒ ⇒ ⇒ **「期望表与权威表自相矛盾」这件事在 G4 树里已经不存在** ⇒ ⇒ ⇒ **守门自带的注释也明写「副本 = 违背该设计的第二份判据 ⇒ 删副本，改为读真源」**。
- 🔴 **实测（用 G4 的 `d4e4745` 版守门对着【我的树】跑，脚本放在 `/tmp` 假 ROOT + 软链回我的树 ⇒ 我树 0 改动）**：
  - **10 个 api 进程里只有 `service/task/task-api` 红** ⇒ ④ form-gate：实际 `'raw'`、应为 `'off'`；
  - ⇒ ⇒ **我域三个 api（`user-api` / `admin-api` / `follow-api`）四旋钮全 ✅**
    （`user-api`=`raw` / `admin-api`=`raw` / `follow-api`=`envelope` ⇒ **与权威表逐条一致**）。
- 🟢 **配对已机器实证**（同一套假 ROOT，只把 `task-api` 的 yml 改成守门要求的写法 ⇒ **我树仍 0 改动**）：
  `mode: raw` ⇒ `mode: "off"` ⇒ ⇒ **守门 EXIT=0** ⇒ ⇒ ⇒ **「G2 那半 + G4 那半 = 全绿」已被机器跑出来，不是推断。**
- ⇒ ⇒ **真正剩下的唯一缺口 = `service/task/task-api/src/main/resources/application.yml` ⇒ 【G2 写权内】**
> 🟢 **订正注记（2026-10-28 12:2x · G2 来信 + 我域独立核实 · 保留上文原文不改写）**
>
> **「我树 `check-tomcat-limits.py` 红」的真实原因已核实 ＝ 上游修法未并入我树（不是我域缺陷）**：
> - G2 侧已落 **`4786eb5`**（`service/task/task-api/src/main/resources/application.yml`：`mode: raw` ⇒ `mode: "off"`，
>   提交时间 **2026-10-27 04:20**，**早于** G4 落 `d4e4745`）⇒ ⇒ **顺序天然正确**（先改 yml、再合守门）。
> - 我域**独立核实**（不采信转述）：`git merge-base --is-ancestor 4786eb5 HEAD` ⇒ **不在我树** ⇒
>   ⇒ ⇒ **我树那道红 =「G2 的 task 域修法尚未并入我树」** ⇒ ⇒ **并入即绿**（G2 侧已实测 `check-tomcat-limits.py` **EXIT=0** · 10/10 api 四旋钮 ✅）。
> - ⇒ ⇒ **对我域的意义**：我域那 3 个 api（`user-api` / `admin-api` / `follow-api`）**四旋钮本就全 ✅**
>   ⇒ ⇒ **不必等「整合者」**，等 G2 的提交并入我树即可 ⇒ ⇒ **域级全量必须继续带 `-am`**（`-am` 才是「并入后重跑」）。
 ⇒ ⇒ **不需要合并收口执行者** ⇒ ⇒ ⇒ **三条 lane 之前都把这事推给合并收口执行者 ⇒ ⇒ 根因被误定位 ⇒ 代价是多绕了好几轮** ⇒ ⇒ ⇒ **⇒ 这条要以「根因纠正」而不是「再推一次」的方式落地。**
- ⚠️ **语法坑（守门自己写明的）**：`off` **必须带引号** —— YAML 1.1 会把未加引号的 `off` 解析成**布尔 false** ⇒ ⇒ 正确写法 `mode: "off"`。
- ⇒ ⇒ **§41 的「持住不合并」由「等 G2 进来」升级为「配对条件已实证」** ⇒ ⇒ **解除条件不变**：G2 的 yml 订正进我树 ⇒ ⇒ 再成对合 `d4e4745` ⇒ ⇒ 届时我树 10 个 api 进程全 ✅。

## 43. 🟢 **G4 §4.10.27 的 R1~R4 复核（我域零动作 · 附结构性根因）＋ G4 §4.10.21「域侧那一半」的落码闭合**（2026-10-27 · G1 lane）

### 43.1 ✅ **R1~R4 我域四条【全部已修】⇒ 我域零动作** —— 但 🔴 **根因不是「我修漏了」，是「G4 那张表读的是它自己那棵树」**

| 组件 | 我树现态（2026-10-27 实测） | G4 §4.10.27.2 表里写的 | 判定 |
|---|---|---|---|
| `user-api` | `timeout: 3s` ＋ `connect-timeout: 5s` | 🔴 连接 = 10s ⇒ R1 | ✅ **已修** |
| `user-rpc` | `timeout: 3s` ＋ `connect-timeout: 5s` | 🔴 连接 = 10s ⇒ R2 | ✅ **已修** |
| `follow-api` | **`timeout: 3s`** ＋ `connect-timeout: 5s` | 🔴🔴 命令 2s ＋ 连接 10s ⇒ R3 | ✅ **已修**（R3 的开放项「是刻意收紧还是抄错」已在 §27.2 判定 = **不是刻意** ⇒ Go 侧生效的是 go-redis v9 库默认 `ReadTimeout = 3s` ⇒ 按 3s 对齐） |
| `admin-rpc` | `timeout: 3s` ＋ `connect-timeout: 5s`（注释已按 Boot 4 字节码订正） | 「值对、注释错」⇒ R4 | ✅ **已修** |

⇒ ⇒ 这四条在 **§27.2 已复核并回函过一次** ⇒ ⇒ **本轮把它升级为「结构性根因」** ⇒ ⇒ 因为**不定位它就会无限重报**（每轮 G4 重新解析一次，就再报一次）：

🔴 **根因（实测，不是推断）**：
- G4 那张表的标题是「**逐组件实测表（YAML 解析器读值，不是 grep）**」（`docs/DRIFT/global/vs-common.md` §4.10.27.2）⇒ ⇒ **它的输入是「执行 YAML 解析的那棵树」**；
- ⇒ ⇒ **执行解析的那棵树 = G4 自己的树** ⇒ ⇒ **G4 的树里没有我 lane 的任何提交**（实测：`git merge-base --is-ancestor 4997c29 align/g4-service-common` ⇒ **不成立** ⇒ 我树不在它树的祖先链上）；
- ⇒ ⇒ **直接证据**：在 **G4 树**里取我域那三个 yml，`connect-timeout` 命中数 = **0 / 0 / 0**；而**在我树**里同三个文件全部是 `connect-timeout: 5s`
  ⇒ ⇒ **同一份代码，两棵树读出两个相反的结论** ⇒ ⇒ **结论对不对取决于「站在哪棵树上看」**。

⇒ ⇒ 🟢 **判定式（可复用）**：**跨 lane 的「逐组件实测表」若不带「树 / commit 基线」，就必然「对那棵树成立、对别的 lane 不成立」**
⇒ ⇒ 读到这种表，**第一件事不是照单全收，是问「这张表的输入是哪棵树」** ⇒ ⇒ 本轮已回函 G4（附可复跑的一行命令）。

### 43.2 🟢 **G4 §4.10.21 留下的「域侧那一半」⇒ 由 G1 落码闭合（3 个 api 各一份 `GoStyleDomainFilterSlotGateTest`）**

**缺口原文**（G4 §4.10.21.1 末行 + §4.10.18.3 + §4.10.21.6）：
「共享层那一半已闭合（新增共享层 Filter ⇒ 必红）」⇒ ⇒ **「仍未闭合的是域侧那一半」**：「**域侧新增** Filter ⇒ 不得吃掉 +4/+5/+6」⇒ ⇒ 落在**域侧文件** ⇒ ⇒ 需**域侧真装配面**（真 api 上下文读「实际注册的带 `@Order` 的 Filter」）⇒ ⇒ **共享层不代做**。
⇒ ⇒ **处方（同 §4.10.21.6）**：「**各模块各守自己那份** ⇒ 避免跨模块的路径依赖」⇒ ⇒ **我域 3 个 api 各落一份**。

**落码（三个模块各一份，同款）**：
`service/users/user/user-api/src/test/java/com/rag/users/user/api/GoStyleDomainFilterSlotGateTest.java` ·
`service/users/admin/admin-api/src/test/java/com/rag/users/admin/api/GoStyleDomainFilterSlotGateTest.java` ·
`service/follow/follow-api/src/test/java/com/rag/follow/follow/api/GoStyleDomainFilterSlotGateTest.java`

| 断言 | 内容 | 判据来源 |
|---|---|---|
| ① **守恒面（核心）** | 本模块**产出**的带 `@Order` 的 Filter 全集 == **域侧登记清单**（**双向相等**，不是 ⊆）⇒ ⇒ **域侧新增任何一个 Filter 必红** | G4 §4.10.21.3 的 ⑤ 同款判据（双向相等 + 枚举面守恒） |
| ② **槽位面** | 登记的每个 Filter 的**实际**槽位（读**实际** `@Order`，不是快照）== 登记槽位 | G4 §4.10.16.1 权威表 + §4.10.16.2 窗口推导 |
| ③ **排他面** | 域侧 Filter **不得占用**共享层独占槽位 **+4（MaxConns）/ +5（Breaker）** —— 那两个**都写响应** ⇒ 同槽 ⇒ 相对次序由容器注册序决定 ⇒ **不可确定** | G4 §4.10.16.1 + §4.10.18.2 的「MaxConns 半边 = 已签字偏离 `SD-2026-10-25-01`」 |
| ④ **真装配面** | 登记的每个域侧 Filter **真的被挂进容器装配出的链**，且排在 **3s 预算起点**（`HttpMiddlewareFilter`）**之前** | G4 §4.10.18.3「可行落法方向 ① 域内」+ §4.10.12 ④ 的读法（`ServletContextInitializerBeans`） |

**⚠️ 三条实现约束（每条都有代价，不是风格偏好）**：
- **不 import 共享层的 Filter 类** ⇒ 只按 **bean 名**（`httpMiddlewareFilter`）定位 ⇒ ⇒ **共享层辐射度快照（`common_radiation`，按 import 统计）保持不变** ⇒ ⇒ 不制造跨 lane 快照漂移（那张快照归合并收口执行者，本 lane 无写权）⇒ ⇒ **本轮实测 `check-wave-coupling.py` 的差量仍是那 1 项已知漂移（`GoStyleRedisClientAutoConfiguration`），未被我扩大**。
- **枚举面 = 本模块自己的 `target/classes`**（code source 取 ⇒ 与工作目录无关）⇒ ⇒ 拿到共享层的 `SharedFilterOrderSnapshotGateTest` 那套**已验证可靠**的判据，不自创。
- **枚举失败 ⇒ 直接失败，不跳过** ⇒ ⇒ 否则第 ① 条会退化成「什么都不查」的恒绿（与共享层 ⑤ 的同款守恒断言）。

**🔴 变异验证（3 个 · 逐条证明 4 条断言各有判别力 · user-api 上实测）**：

| 变异 | 做法 | 实测结果 |
|---|---|---|
| **A（产品侧 ·「多一个」）** | 在 user-api 新增第 2 个带 `@Order(+9)` 的 Filter | ✅ **红** —— **只有 ① 红**（`everyDomainProducedFilterIsRegisteredInTheDomainRegistry`），②③④ 仍绿 ⇒ 「多一个」方向由 ① 独占 |
| **B（产品侧 ·「换槽位」）** | 把 `GoStyleJwtAuthFilter` 的 `@Order` 从 +6 改成 **+4**（占用 MaxConns 的独占槽位） | ✅ **红** —— **② 与 ③ 红**（`domainFilterSlotsMatchTheRegistry` + `domainFiltersDoNotOccupySharedExclusiveSlots`），①④ 仍绿 ⇒ 「槽位被改 / 占用共享层独占槽位」由 ②③ 独占 |
| **C（产品侧 ·「摘注册」）** | 摘掉 `GoStyleJwtAuthFilter` 的 `@Component` | ✅ **红** —— **只有 ④ 红**（`domainFiltersAreReallyAssembledBeforeTheBudgetStart`，失败消息 = 「没有注册进 Spring 上下文」）⇒ ⇒ 「注解在、bean 不在」被抓 |

⇒ ⇒ **复原后 4/4 绿**（三个模块各 4/4 ⇒ **新增 12 条**）⇒ ⇒ **生产代码零改动**（只加测试面）。

🟢 **回归口径（2026-10-27 实测 · 域内小跑 · `mvn -B -o -DargLine=-Xmx2g -am -pl <三个 api> clean test`）**：
**BUILD SUCCESS · 15/15 模块** ⇒ `user-api` **121 → 125**（+4）· `admin-api` **140 → 144**（+4）· `follow-api` **68 → 72**（+4）⇒ ⇒ **我域 669 → 681**；共享层 `common` 330 + `users-common` 33 **逐条不变**（⇒ ⇒ 变更只落在域内测试面，零跨 lane 影响）⇒ ⇒ failures/errors/skipped 全 **0**。
⚠️ **本次只跑域内小跑、没跑域级全量**，理由 = **本轮变更 100% 是测试面**（3 个新增测试类 + 台账）⇒ ⇒ **生产代码零改动** ⇒ ⇒ 按 C7 的变更面分级，**域级全量留到波末收口**（或等 G4 的 `15cf93a` 生产改动合入后一并跑）⇒ ⇒ **不谎报「域级全量已跑」**。

**证据强度**：**L3**（真 Spring 上下文 ＋ 真容器装配结果 `ServletContextInitializerBeans` ＋ 真字节码槽位读数）⇒ ⇒ **不是 L4**（不涉及 Go 腿 ⇒ 这条是**协调约束**的机器保障，不是 A 轴的行为级证据 —— 与 G4 §4.10.21.6 的边界一致）。

**边界（不得外推）**：
- **只管「域侧产出」的 Filter** ⇒ ⇒ 共享层那 7 个由共享层自己的 `SharedFilterOrderSnapshotGateTest` 管；**两份合起来才覆盖「+2..+8 全槽位」**。
- **只管本模块产物** ⇒ ⇒ 跨模块的 Filter（若将来某个模块把 Filter 放进 `users-common`）**不在本门禁覆盖面** ⇒ ⇒ 那属共享层写权面（G4）⇒ ⇒ 我域零动作。
- **不覆盖「不带 `@Order` 的 Filter」** ⇒ ⇒ Spring 给它 `LOWEST_PRECEDENCE`（链尾）⇒ ⇒ 不占任何槽位 ⇒ ⇒ **有意不管**（与共享层 ⑤ 同款口径）。
- **零运行时行为变化**（只改 `src/test/java/**`）⇒ ⇒ 部署行为零变化。

### 43.3 🟡 **本轮仍未闭合 / 等外部输入的三项（不改结论，只登记）**

| 项 | 状态 | 落点 / 解除条件 |
|---|---|---|
| 🟡 **等 G2 的 task-api yml 订正** | `check-tomcat-limits.py` 唯一红点 = `service/task/task-api/.../application.yml`（`mode: raw` ⇒ 应为 `mode: "off"`） | **G2 写权内** ⇒ ⇒ G2 的订正进我树 ⇒ ⇒ 再成对合 G4 的 `d4e4745`（§41 / §42.2 的顺序约束**不变**） |
| 🟡 **共享面 `cross-domain-coupling` 快照过期** | `check-wave-coupling.py` EXIT=1（`common_radiation` 差 1 行：`"GoStyleRedisClientAutoConfiguration": ["users"]`） | 落点 `docs/DRIFT/_cross/cross-domain-coupling.md` ∈ 守门 `SHARED`（**无人拥有**）⇒ ⇒ **G1/G4 都不能写** ⇒ ⇒ 需合并收口执行者在四 lane 全合后重生成（单树重生成必错 ⇒ 我树 1 项 / G2 树 9 项） |
| 🟡 **等 G3 跑完 rpc 侧活体对拍** | 我域 `DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013` 的证据强度仍停在 **L3** | 前置已解除（PG 口协议级全通 + Redis 宿主口清单已给）⇒ ⇒ 等 G3 回我 ⇒ ⇒ 我域才升 **L4** |
| 🟡 **合并 G4 后补一条台账点名** | G4 的 `4cec90e` 在 `service/users/users-common/src/test/java/com/rag/users/common/http/GoStyleRequiredFieldGateTest.java` 新增一条守门（钉住**我域**销号所依赖的两个不变量：必填校验在 service 层之前拦） | 该类在 `users-common`（G4 代码写权面）⇒ ⇒ 但**台账**落点是 `docs/DRIFT/users/common.md`（**我的写权面**）⇒ ⇒ 合并后**我域补一条点名**；⚠️ 门禁**不强制**（`JAVA_ROOTS` 的 `users` = `service/users/user` · `users-admin` = `service/users/admin` ⇒ **`users-common` 不在任何域的扫描根** ⇒ 层 5 不会报它）⇒ ⇒ 属**自觉点名**，不是门禁倒逼 |

> 🟢 **2026-10-27 状态订正（§43.3 四项里**两项已变**，原值保留不改，只补指针）** —— G1 lane
>
> | 原表项 | 现在 | 指针 |
> |---|---|---|
> | ①「等 G2 的 task-api yml 订正」 | 🔴 **该行描述的处置路径已过时** —— G4 的 `d4e4745`（改守门脚本）**已于本轮随终版并入我树**（`ddb63f7`）⇒ ⇒ **不再是「等配对条件满足再合」** ⇒ ⇒ 现在的口径 = **唯一红点仍在 task-api（G2 写权内 · G2 已在他们树用 `4786eb5` 修）⇒ 是「还没合过来」，不是「没人管」** ⇒ ⇒ 消解路径 = **G2 合主干 → 我合主干**（不需要任何人拍板） | 本文件 **§47.1** ~ **§47.2** |
> | ④「合并 G4 后补一条台账点名」 | 🟢 **已做**（本轮）—— `docs/DRIFT/users/common.md` **§5.7** 已点名 `GoStyleRequiredFieldGateTest`（6 条）⇒ ⇒ 该项**关闭** | [`users/common.md` §5.7](common.md) |
> | ②「共享面 `cross-domain-coupling` 快照过期」 | 🟡 **不变**（仍红、仍归合并收口执行者） | 本文件 **§47.5** ① |
> | ③「等 G3 跑完 rpc 侧活体对拍」 | 🟡 **不变**（`V-014` / `ARPC-013` 仍停在 L3） | 本文件 **§47.5** ③ |

## 44. 🔴 **台账自检第一次【按域】跑 ⇒ 挖出 8 处失效引用（两轮收编删了文件，台账历史记述仍写着全路径）**（2026-10-27 · G1 lane）

### 44.1 🔴 **「跑绿」此前查的【不是我的域】**

- **此前每一轮**跑 `tools/check-ledger-selfcheck.py` 都**用默认参数** ⇒ ⇒ 默认 `--domain task` ⇒ ⇒ **`--domain users` 从来没被跑过** ⇒ ⇒ 历轮报的「EXIT=0」= **task 域的结论**，**我域是「没查」**（脚本自己会打印「⏭ 本层的『已修断言』清单只覆盖 ['task'] 域 ⇒ **users 域不适用**（⚠️ 不是「通过」—— 是「没查」）」）。
- ⇒ ⇒ **这是「跑绿 ≠ 判据活着」的又一形态**：门禁**默认域不是自己的域** ⇒ ⇒ 不带 `--domain` 跑出来的绿是**别人的绿**。
- 🟢 **本轮按域跑** ⇒ `--domain users` ⇒ **EXIT=1**（2 处层-1 不一致）· `--domain users-admin` ⇒ **EXIT=1**（1 处）⇒ ⇒ **我域此前是红的，只是没人按域跑过**。

### 44.2 ✅ **根因 = 两轮「真删除」的收编，台账的历史记述没跟上**

| 轮次 | 删掉的东西 | 台账里仍写着全路径的地方 |
|---|---|---|
| **Redis 收编**（§26 · 提交 `312a09b`） | **5 份域内副本** `config/RedisDisconnectedBehaviorConfig.java`（3 api + 2 rpc）⇒ **真删除** | `users/user/api.md` · `users/user/rpc.md` · `users/admin/api.md` · `follow/api.md` · `users/vs-common.md` · `follow/vs-common.md` |
| **排序桥收编**（§28 · 提交 `17624d1`） | **3 份域内副本** `config/MybatisPlusOrderingBridgeAutoConfiguration.java`（user-rpc / admin-rpc / follow-rpc）＋ 3 个只含那一行的 `META-INF/…/AutoConfiguration.imports` | `users/vs-common.md` §28.2「落码清单（已完成）」的 6 行 |

⇒ ⇒ **共 8 处**（路径去重后 **5 个不同路径**、分布在 **6 份台账文件**里）。

### 44.3 🟢 **修法 = 按 N13 规则【如实标注状态】，不是让门禁闭嘴**

- 门禁的第 1 层自带 **N13** 规则：**台账同行/邻行若写明该文件的状态（已删除 / 不存在 / 0 字节占位 …）⇒ 判为「历史记述」⇒ 不计 FAIL**（护栏：行里带「路径写错」这类**缺陷标记**时，落地态/免责两层一律不生效 ⇒ **宁可报缺，也不吃真缺陷**）。
- ⇒ ⇒ 我把 8 处逐条改成**如实标注**：`「已于 Redis 收编轮【删除】」` / `「**已删除**」`（原 `「**删**」` 是**祈使语气**，而 §28.2 的标题是「落码清单（**已完成**）」⇒ 改成「已删除」才与事实一致）。
- ⇒ ⇒ **复跑结果**：`--domain users` ⇒ **EXIT=0**（68 个引用路径 ⇒ 缺失 **0**）· `--domain users-admin` ⇒ **EXIT=0**（81 ⇒ **0**）⇒ ⇒ **生产代码零改动**。

### 44.4 🔴 **门禁的覆盖面缺口（本域只登记，不改）**

- `LEDGER_FILES` 只有 3 个域键：**`task` / `users` / `users-admin`** ⇒ ⇒ **`follow` 域不在覆盖面**（`--domain follow` ⇒ 「找不到 domain=follow 的台账文件」）⇒ ⇒ 且 **`docs/DRIFT/users/vs-common.md` / `docs/DRIFT/users/common.md` / `docs/DRIFT/follow/**` 5 份**也不在任何域的清单里。
- ⇒ ⇒ **我域台账有 7 份文件从来没被这个门禁查过**（`follow/{api,common,proto,rpc,vs-common}.md` + `users/{vs-common,common}.md`）。
- 🟢 **本轮用门禁自己的层-1 判据单独扫了那 7 份**（不重写判据 ⇒ 口径与门禁一致）⇒ ⇒ **又挖出 5 处失效引用**（其中 2 处与 §44.2 的 `--domain users`/`users-admin` 结果重叠 ⇒ **去重后总计 8 处**）⇒ ⇒ **全部已修** ⇒ ⇒ **复扫：118 个引用路径 ⇒ 缺失 0**。
- ⇒ ⇒ **可复跑**（不依赖我改脚本）：
  ```bash
  python3 - <<'PY'
  import importlib.util, os, glob
  spec = importlib.util.spec_from_file_location("cls", "tools/check-ledger-selfcheck.py")
  m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
  root = m.repo_root()
  files = sorted(set(glob.glob("docs/DRIFT/users/**/*.md", recursive=True)
                   + glob.glob("docs/DRIFT/follow/**/*.md", recursive=True)))
  covered = set(m.LEDGER_FILES['users']) | set(m.LEDGER_FILES['users-admin'])
  extra = [f for f in files if f not in covered]
  m.layer1_paths(root, {f: m.read(os.path.join(root, f)) for f in extra})
  PY
  ```
- 🔴 **落点归属**：`tools/check-ledger-selfcheck.py` ∈ **两张表都查不到**（4 条 lane 的写权白名单 ∪ `SHARED` 那 13 项）⇒ ⇒ **写权孤儿** ⇒ ⇒ **按「两张表」纪律 ⇒ 只能合并收口执行者改** ⇒ ⇒ **我域零落码** ⇒ ⇒ **登记给合并收口执行者**：`LEDGER_FILES` 补 `follow` 域的 5 份 ＋ `docs/DRIFT/users/{vs-common,common}.md` ⇒ ⇒ 补完后这 7 份就不用再手工扫了。
- ⚠️ **同理提醒**：`--domain users` / `--domain users-admin` 也**只覆盖** `LEDGER_FILES` 列出的那 3+3 份 ⇒ ⇒ **每轮跑门禁必须带 `--domain`**，否则查的是 task 域。

### 44.5 🟢 **落点归属订正（2026-10-28 · G4 动了那个文件 ⇒ 从「写权孤儿」改判为「G4 实权」）＋ 它一修就暴露我台账 2 条错引用**

**① 落点归属改判（这一步把一项「要 Jason 拍板」变成「转派 G4」）**

- §44.4 记的是：`tools/check-ledger-selfcheck.py` ∈ **两张表都查不到** ⇒ ⇒ **写权孤儿** ⇒ ⇒ 只能合并收口执行者改。
- 🔴 **2026-10-28 实测推翻「孤儿」这个前提**：G4 的 `9037a2b`（`fix(tools): check-ledger-selfcheck 层 4 的「带版本前缀精确定位通道」是死代码 ⇒ 修提取器 + 支持短式 + 补端到端自测`）
  **直接改了这个文件**（+115/-5）⇒ ⇒ **它事实上已是该文件的实际写权人** ⇒ ⇒
  ⇒ ⇒ **「写权孤儿」这个描述已不成立** ⇒ ⇒ **「`LEDGER_FILES` 补 `follow` 域 5 份 + `users/{vs-common,common}.md`」这条反馈单 ⇒ 改判为【转派 G4】**
  ⇒ ⇒ **不再需要 Jason 拍板**（登记 ≠ 派单；而这条现在有明确落点 ⇒ 就该直接给落点的人）。
- ⚠️ **保留原记述不删**（§44.4 原文一字未改）⇒ ⇒ 这里只加改判注记。

**② 它一修就暴露我台账 2 条错引用 ⇒ 已逐条重核真源并改正**（`docs/DRIFT/users/admin/rpc.md`）

- **现象**：并入 G4 的 `9037a2b` 后，`check-ledger-selfcheck.py --domain users-admin` ⇒ **EXIT=1 · 2 处「行号超出文件范围」** ⇒ ⇒ **不是工具误报**。
- **根因（两条都是我的错，且错法不同）**：
  | # | 原引用 | 真实文件 | 判定 | 改正后 |
  |---|---|---|---|---|
  | 1 | `zrpc/server.go:143-170` | **168 行** | 两端都超 ⇒ **行号偏移** | `zrpc/server.go:144-168` |
  | 2 | `zrpc/internal/**config**.go:57-60` | **30 行**，且整份是配置结构体 | **里面根本没有 `AddUnaryInterceptors`** ⇒ ⇒ **指错文件**（不是行号笔误） | `zrpc/internal/**server**.go:57-59` |
- ⇒ ⇒ **第 2 条最阴**：`zrpc/internal/` 下 `config.go` 与 `server.go` **是两个相邻同族文件** ⇒ ⇒ **行号 57~59 恰好与原值一致** ⇒ ⇒ **错的只有文件名** ⇒ ⇒
  **若不是「行号超范围」这个粗判据，根本发现不了** ⇒ ⇒ ⇒ **这正是 §3.3 的价值：粗判据先响，细判据再定责**。
- ⇒ ⇒ **语义结论没变**（「先注册的最外层」仍成立），但**证据链补全了**（原来只到「append」就停）：
  **声明点** `zrpc/internal/server.go:57-59`（append 到尾部）→ **消费点** `zrpc/internal/rpcserver.go:53`
  （`grpc.ChainUnaryInterceptor(s.unaryInterceptors...)`）→ **最外层的证明** grpc-go v1.65.0 `server.go:1185-1187`
  （`chainUnaryInterceptors` 把 **`interceptors[0]`**（**index 0**）当**最外层**调，下一层 handler 是
  `getChainUnaryHandler(interceptors, 0, info, handler)` ⇒ ⇒ **index 0 最外层**）。
  （⚠️ grpc-go 原文是一句 `return` ＋ **`interceptors[0]`** ＋ `(ctx, req, info, getChainUnaryHandler(...))` ⇒ ⇒ **这里把 `[` 与 `(` 拆开写**
  ⇒ ⇒ 因为 `tools/check-doc-links.py` 的 `LINK_RE` **不跳过代码块** ⇒ ⇒ 相邻写法会被 markdown 解析成链接 ⇒ ⇒ 守门误报。）
- ⇒ ⇒ **顺手核过同段其它引用，全部正确**：`zrpc/config.go:42`（`Auth bool json:",optional"`）· `:46`（`Timeout int64 json:",default=2000"`）·
  `:47`（`CpuThreshold int64 json:",default=900,range=[0:1000)"`）· `zrpc/internal/config.go:20`（`Trace bool json:",default=true"`）·
  `zrpc/server.go:81`（公开包装，转调 `rs.server.AddUnaryInterceptors`）· 真源 `service/user/admin/rpc/admin.go:33,43`（均实测逐字读过）。

**③ 顺手做了一次全量自扫**（不靠门禁 ⇒ 因为门禁只查 `LEDGER_FILES` 覆盖的域 ⇒ follow 域查不到）

```bash
# 扫我域 20 份台账里全部 go-zero 引用 ⇒ 逐条核「文件存在 + 行号在范围内」
python3 /tmp/g1_gozeroref.py    # 138 条唯一引用
```

⇒ ⇒ 实测 **138 条**里 **真错 2 条**（即上面那两条）· 其余 8 条是**记法约定**（`<path>` 占位符 · `core/mapping.UnmarshalJsonMap` 是**符号名不是路径** · `{a,b,c}` 花括号展开）⇒ ⇒ **不是缺陷**。
⇒ ⇒ **修完复跑**：`--domain users` ⇒ **EXIT=0** · `--domain users-admin` ⇒ **EXIT=0**。

## 45. 🟢 **Testcontainers 宿主口钉段落码（G4 §4.10.39 ② 的域侧那一半）—— 38 个容器起点全部显式绑口 + 2 台域级守门**（2026-10-27 · G1 lane）

### 45.1 🟢 **形态选择：按 G4 的处方走【per-call-site 显式绑口】，不用我在 §39 记的 ServiceLoader 形态**

- **G4 §4.10.39 ② 的「落点」原文**：「**域侧改自己的测试类**（`@Container PostgreSQLContainer` ⇒ 显式绑口）⇒ ⇒ **本层只分配段 + 记录规则 ⇒ 零落码**」⇒ ⇒ **按处方走**。
- ⚠️ **我 §39 记的另一条形态成立但没采用**（字节码已核实：`GenericContainer.loadCreateContainerCmdCustomizers()` = `ServiceLoader.load(CreateContainerCmdModifier.class)` ⇒ 每模块放一份 `META-INF/services/...` ⇒ 38 个调用点零改动）⇒ ⇒ **不采用的三条理由**：
  1. **它是隐式的** ⇒ 容器起点看不出自己被钉了 ⇒ ⇒ 与本仓「显式优先 / 改槽位 = 明知故犯」的登记制不合；
  2. 它对**模块内每一个**容器都生效（含将来新增的）⇒ ⇒ 「钉哪个口」只能靠镜像/端口猜 ⇒ ⇒ 判据不可读；
  3. per-call-site 形态的**端口选择点就在调用点** ⇒ ⇒ 守门可以**源扫**「每个容器起点都有 modifier 且钉对口」⇒ ⇒ **机器可判**。
- ⇒ ⇒ **§39 的三条机制事实全部被直接用上**（不是废弃）：`withPortBindings(List)` 是**整体替换** ⇒ 必须「剔除旧绑定 + 加固定绑定」两步齐做 · modifier 运行时 `getExposedPorts()` **已填充** ⇒ 逐口钉可行 · 显式绑口后 `getMappedPort()` **返回那个固定口**（L4 真容器实测）⇒ ⇒ **既有测试继续用 `getMappedPort()` / `getJdbcUrl()`，零改动**。

### 45.2 ✅ **落码**

| 项 | 数量 | 落点 |
|---|---|---|
| 钉口支撑类 `GoStyleHostPortPinning` | **6 份**（users 4 模块 + follow 2 模块） | 各模块 `src/test/java/**/support/` ⇒ **G1 写权内** |
| 被钉口的容器起点 | **38 个**（user-api 8 · user-rpc 12 · admin-api 5 · admin-rpc 13 · follow-api 2 · follow-rpc 5 —— 含探针自身那个） | 各测试类的容器声明处各加 **1 行** |
| 域级守门 | **2 台**（`UserDomainHostPortPinningGateTest` · `FollowDomainHostPortPinningGateTest`） | users 域那份在 `user-rpc` · follow 域那份在 `follow-rpc` |

- **段**：users 域 `22700–22729` · follow 域 `22730–22759`（G4 §4.10.39 ② 的分配结果）⇒ ⇒ **全部落在 OS 临时段 `49152–65535` 之外** ⇒ ⇒ 这正是「把宿主口挪出临时段」的全部意义。
- **调用点形态**（每处一行）：
  ```java
  .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432))   // PG
  .withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379))   // Redis
  ```

### 45.3 🟢 **为什么是「段内挑空闲口」而不是「写死一个口」**（这条决定成败）

- **写死一个口 ⇒ 不可取**：该口若被上一次跑批的**残留容器**占住 ⇒ ⇒ Docker 绑口**硬失败**（`bind: address already in use`）
  ⇒ ⇒ 而**本域 surefire 设了 `TESTCONTAINERS_RYUK_DISABLED=true`** ⇒ ⇒ **Ryuk 不回收** ⇒ ⇒ 残留可能**跨轮存在**
  ⇒ ⇒ ⇒ 写死口 = 把「**偶发**撞口」换成「**确定性**撞口」⇒ ⇒ **方向反了**。
- **段内挑空闲口（自愈）**：被占就换同段内下一个 ⇒ ⇒ 且同段内仍能并行起多个容器 ⇒ ⇒ **这才是「钉段」的语义**（钉的是**段**，不是某个口）。
- ⚠️ **残留的竞态（如实记录，不掩饰）**：「探端口」与 Docker 真正「绑端口」之间有一个极小窗口 ⇒ ⇒ 若同一瞬间**另一个 JVM** 也挑中了同一个口 ⇒ ⇒ 后绑者**硬失败** ⇒ ⇒ 这是**大声失败**而不是静默走偏 ⇒ ⇒ 且下一轮自动挑到别的口 ⇒ ⇒ **残留面 = 「两个 JVM 恰好同时挑中同一个空闲口」**，不是别的。

### 45.4 🟢 **两台域级守门（各 3 条断言）**
| 断言 | 内容 | 抓什么 |
|---|---|---|
| ① **覆盖（守恒）** | 域内**每一个**容器起点都带显式绑口 modifier，且钉的是**该容器自己的**容器口（PG=5432 / Redis=6379） | 漏钉一个 ⇒ 那个容器仍落回随机口 ⇒ 撞口风险原样存在 · 钉错口 ⇒ 容器启动失败 |
| ② **段正确** | 各模块 helper 的段落在该域分配段内，且**与 OS 临时段不相交** | 段写错 ⇒ 钉口没有意义 |
| ③ **副本等价** | 同域 N 份 helper **剥注释后逐字等价**（只允许 package 与段常量不同） | **「各模块各守自己那份」的漂移风险** —— 副本会漂，且漂移是**静默**的（只是撞口概率变高，不会报错） |

- ⚠️ **守门自己踩到的坑（值得留仓）**：第一版①**没剥注释** ⇒ ⇒ helper 自己 javadoc 里那段 `<pre>` 示例（写着 `new PostgreSQLContainer<>(...)`）被当成**容器起点** ⇒ ⇒ **假红**（4 条）⇒ ⇒ 修法 = **剥注释后再扫**（状态机实现，不动字符串字面量）⇒ ⇒ **教训**：「源扫型守门」的第一条纪律是**扫之前先决定扫什么** —— 说明文字、示例、被注释掉的旧代码**都不该进扫描面**。
- ⚠️ **另一条**：④（副本等价）第一版把 **javadoc 的措辞差异**也当漂移 ⇒ ⇒ 改成**剥注释后**比 ⇒ ⇒ **判据精确到「逻辑」而不是「文本」**（javadoc 漂移无害）。

### 45.5 🔴 **边界与未闭合**

| 项 | 内容 |
|---|---|
| **真容器那一层只有 users 域有** | 「钉口后 `getMappedPort()` == 那个固定口且真连得通」由 `UserRpcTestcontainersHostPortSegmentGateTest` 真容器实测（L4）· **follow 域没有重复起容器** ⇒ ⇒ 靠①（覆盖）+③（副本等价）保证「用的是同一份实现」⇒ ⇒ **不重复起容器**（follow 域的容器测试已够慢） |
| **只管「宿主口落在哪」** | **不改变**任何业务行为 ⇒ ⇒ 与 A1~A9 各轴的结论**无关** ⇒ ⇒ **不是行为级证据**，是**环境稳定性**的机器保障 |
| **不改生产代码** | 只改 `src/test/java/**` ⇒ ⇒ 部署行为零变化 |
| **残留竞态未消除** | 见 §45.3 ⇒ ⇒ **如实登记**，不是「已彻底消除撞口」 |
| **本段是 G4 分配的** | users `22700–22729` · follow `22730–22759`（G4 §4.10.39 ②）⇒ ⇒ **若 G4 改段号 ⇒ 我域 6 份 helper 的常量与两台守门的期望值都要跟着改**（守门会红 ⇒ ⇒ **不是静默漂移**） |

### 45.6 🔴 **变异验证（3 个 · 逐条证明 3 条断言各有判别力）**

| 变异 | 做法 | 实测结果 |
|---|---|---|
| **A（产品侧 ·「漏钉」）** | 拿掉 `UserRpcSchemaTextColumnTest` 那个容器点的 `.withCreateContainerCmdModifier(...)` | ✅ **红** —— **只有 ① 红**，失败消息**点名那个文件** ⇒ ⇒ 「漏钉一个」方向由 ① 独占 |
| **B（产品侧 ·「段写错」）** | 把 `admin-api` 那份 helper 的 `SEGMENT_START` 从 `22700` 改成 `49152`（**落进 OS 临时段**） | ✅ **红** —— **只有 ② 红**（`everyModuleSegmentIsInsideTheDomainSegmentAndOutsideTheEphemeralRange`） |
| **C（产品侧 ·「副本漂移」）** | 把 `admin-api` 那份 helper 的「剔除该容器口旧绑定」分支删掉（**逻辑变了，注释没变**） | ✅ **红** —— **只有 ③ 红**，消息点名「admin-api 与基准 user-api 不等价」 |

⇒ ⇒ **复原后双门禁 3+3 全绿**（两台守门都跑过）⇒ ⇒ **生产代码零改动**。

### 45.7 🟢 **回归口径（2026-10-27 实测）**

`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test` ⇒ ⇒ **18/18 SUCCESS**：
`admin-rpc` 118 · `admin-api` 144 · `user-api` 125 · `user-rpc` **148** · `follow-api` 72 · `follow-rpc` **81** ⇒ ⇒ **我域 6 模块合计 688 条**（failures/errors/skipped 全 **0**）；共享层 `common` 330 ＋ `users-common` 33 **逐条不变** ⇒ ⇒ ⇒ **变更只落在域内测试面**。

🔴 **⇒ 「基线过期」实测抓到一次（本轮的真发现）**：
- 我此前记的「域级 669 条」是**钉口探针（`UserRpcTestcontainersHostPortSegmentGateTest`，1 条）加入之前**测的 ⇒ ⇒ 上一轮报的「669 → 681」**少算了那 1 条**；
- ⇒ ⇒ **权威数字 = 688**（= 669 + 12（域侧 Filter 槽位守门 3 模块 × 4）＋ 1（钉口探针）＋ 3（users 钉口守门）＋ 3（follow 钉口守门））；
- ⇒ ⇒ **教训（与 memory 里那条同款）**：「**只重跑受影响模块 + 沿用上轮总数**」⇒ ⇒ 跨模块回归总数会**悄悄过期**，而且**没有任何门禁会报** ⇒ ⇒ 本轮能抓到，是因为本轮**真的把 6 个模块全跑了** ⇒ ⇒ **凡要报域级总数，必须重跑全量（或明确标注「沿用上轮、已过期」）**。

## 46. 🔴 **两条关于「我自己的报告」的订正**（2026-10-27 · G1 lane）

⇒ ⇒ 本节记的是**我报错过的两件事** ⇒ ⇒ 依 AGENTS.md「订正姿势 = 保留原值 + 加订正注记」，**不改写历史**。

### 46.1 🔴 **「协调会话 404 不可达」= 我把 session id 抄错了一个字符**

- **我此前报**：前几轮给协调会话发消息一律 `404 Session not found` ⇒ ⇒ 我据此判定「协调会话不可达 ⇒ 只能直接报告给 Jason ⇒ 消息这条通道废了」。
- **真相**：**协调会话一直在**（`mvs_d4fbe94ca81b40d593f75d9c9c157c31`）⇒ ⇒ 我记的 ID 少了一个 `c`（`…d9c157c31`）⇒ ⇒ **404 是我的抄写错误，不是会话没了**。
- **本轮实测**：用正确 ID 发消息 ⇒ **被接受并起了新 Turn**（不是排队）⇒ ⇒ **送达路径一直可用**。
- ⇒ ⇒ **判定式（可复用）**：「404 ⇒ 会话没了」这个推断**不成立** ⇒ ⇒ **404 只说明「你给的这个 id 不存在」** ⇒ ⇒ 下次遇到 404，第一件事是 **`session list` 把 id 抄准**（尤其是长 hex id ⇒ ⇒ 人工转抄极易少/多字符）⇒ ⇒ **而不是宣布通道失效**。
- ⇒ ⇒ **影响面**：前几轮那几条**确实没送到**协调会话的消息，本轮已**重发**（共享面 2 条 + 我域落码通知 + 本条订正）⇒ ⇒ 台账里「已转派协调会话」那两处（§…:2473 / §…:2603）**本来就写对了**（它只说「已转派」，没说「送到了」）⇒ ⇒ **台账零改动**。
  - 🔴🟢 **2026-10-28 订正注记（本条的理由被推翻 · 原文一字未改）** ——
    当时我的理由是「台账只说『已转派』、没说『送到了』⇒ 台账没错」⇒ ⇒
    **但转派对象本身错了**：那个会话 **2026-10-28 自己** 通告它是【临时故障处置会话】、
    **共享面与合并收口不归它管**、且**通信单向（我 → 它不可）**
    ⇒ ⇒ **「已转派协调会话」不是措辞问题，是【落点指向了一个不会接单的会话】**
    ⇒ ⇒ 台账那两处**需要加订正注记**（已加，见 §38.3 与 §51.2）⇒ ⇒ **本条的「台账零改动」结论作废**。
    ⇒ ⇒ 📌 **复核补充（2026-10-28 实测）**：全域 grep「协调会话」⇒ **现只剩 1 处仍含「已转派协调会话」**（§38.3 那处，已加注记）
    ⇒ ⇒ 另一处**早已被改写**（§47.4 的 59 处「整合者」→「合并收口执行者」批量改写时一并改掉了）⇒ ⇒ **「那两处」这个计数本身也过期了**。
    ⇒ ⇒ **教训**：**「措辞准确」不等于「落点正确」** ⇒ ⇒ 订正时**先核落点是谁**，再核措辞。

### 46.2 🔴 **「四个 lane 同时撞 Token Plan 上限 ⇒ 全部停摆 ⇒ 需 Jason 充值」= 不成立**

- **我此前报**：会话列表里 G1/G2/G3/G4 四个 lane 的 `status.message` 全是「已达到 Token Plan 用量上限：请升级 Token Plan 套餐或购买积分补充用量。(2056)」⇒ ⇒ 我据此判定「**四个 lane 同时停摆 ⇒ 只能充值/升级，代码侧无解**」⇒ ⇒ 把它列为最高优先的机器级阻塞。
- **反证（本轮实测）**：① **G4 的提交在持续推进**：23:09 `117bea9` → 23:24 `9dd67ab` → 23:31 `c9fe18a` → 23:34 `e94475d` → 23:38 `c26e462` ⇒ ⇒ **15 分钟内 5 笔**；② 四个 lane 的 `updatedAt` 全在最近 30 分钟内；③ **我自己的会话也是同一个 status 文本**，而我**整轮都在正常干活** ⇒ ⇒ ⇒ **⇒ 这个 status 是【每轮的告警】，不是【agent 已死的判定】**。
- ⇒ ⇒ **结论订正**：**四 lane 停摆 = 不成立** ⇒ ⇒ **不需要 Jason 充值才能继续推进** ⇒ ⇒ **我此前把它列成「最高优先机器级阻塞」是误判**（属于「拿一个信号当结论」）。
- ⇒ ⇒ **判定式（可复用）**：`status.message` 里带错误码（这里 `2056` / `42212`）**不能单独用作 liveness 判据** ⇒ ⇒ 要判「是否停摆」，得看**行为证据**（有没有新提交 / `updatedAt` 有没有推进）⇒ ⇒ 与「单点现象 ≠ 结论」同族。
- ⇒ ⇒ **顺带**：**这不改变**「并发全量 maven 会被 OOM-kill（EXIT=137）」那条纪律（那是**实测**过的资源约束，不是 status 推断）⇒ ⇒ **争用窗口的规避照旧**。

## 47. 🟢 **并入 G4 终版 `c26e462`（11 笔 · 零冲突）+ 角色口径改写（59 处 / 6 份）**（2026-10-27 · G1 lane）

### 47.1 🟢 **并入 G4 终版（合并提交 `ddb63f7`）—— 并把「为什么推翻 §41 的持住决定」写清**

**决策反转的记录（依 AGENTS.md「不重写历史」：保留原值 + 补记理由）**：
§41 曾记「**持住不合并 `d4e4745`** ⇒ 配对条件 = G2 的 `4786eb5`（task-api yml `mode: raw` ⇒ `mode: "off"`）进我树」。
⇒ ⇒ **本轮那个配对条件仍未满足**（`4786eb5` 至今**不在**我树 ⇒ 实测 `git merge-base --is-ancestor 4786eb5 HEAD` = 否）
⇒ ⇒ **但本轮仍然合并了** ⇒ ⇒ **理由必须写清，否则就是「无出处的决策变更」**：

| 判据 | §41 当时 | 本轮实测 | 结论 |
|---|---|---|---|
| 待合笔数 | **1**（只有 `d4e4745`） | **11** | 「只差一笔」的代价 = 1 笔；「差 11 笔」的代价 = **共享层整整落后一整轮** ⇒ ⇒ **我域的守门与回归都跑在过时的共享层上** ⇒ ⇒ 代价的**性质**变了 |
| 红点数量 | 唯一红 = task-api yml | **仍然唯一** = task-api yml（**实测**，§47.2） | 红点数**没变** ⇒ ⇒ 「保持全绿」这个收益**也没变** |
| 红点归属 | G2 写权内 · G2 已在他们树修 | 同上（**重核过**：G2 分支 `align/g2-message-like-task` 的 `4786eb5` 在） | 这个红是「**还没合过来**」，**不是**「没人管」⇒ ⇒ 它**有确定的消解路径**（G2 合主干 → 我合主干） |

⇒ ⇒ **结论**：**「保持全绿」在 §41 当时是正确取舍**（那时只差 1 笔）；⇒ ⇒
但当差距变成 **11 笔**、而唯一红点**在他树已修好**时，**继续持住 = 用一个「可归因的红」换「共享层落后一整轮」⇒ ⇒ 方向反了**
⇒ ⇒ **本轮改为「先合、再如实归因」**。

⚠️ **口径纪律（不许含糊）**：本轮**不是**「等配对条件满足后再合」，**也不是**「无视红点硬合」
⇒ ⇒ 是「**合并 + 把那一个红点逐条归因（谁写权 · 谁已修 · 何时消）写进台账**」
⇒ ⇒ **红点不被掩盖，也不被用来挡进度**。

### 47.2 🔴 **合并后守门复跑：唯一红点 = `task-api`（G2 写权内 · G2 已修）—— 我域三个 api 四旋钮全 ✅**

| 守门 | 合并前 | 合并后 | 说明 |
|---|---|---|---|
| `check-wave-lane-ownership.py` | 0 | **0** | ✅ |
| `check-wave-role-model.py` | 0 | **0** | ✅ |
| `check-global-lane-ownership.py` | 0 | **0** | ✅ |
| `check-doc-links.py` | 0 | **0** | ✅ |
| `check-ledger-count.py` | 0 | **0** | ✅ |
| `check-ledger-selfcheck.py`（默认 + `--domain users` + `--domain users-admin`） | 0 | **0** | ✅ |
| `check-tomcat-limits.py` | 0 | **1** | 🔴 **唯一红点 = `service/task/task-api`**（见下） |
| `check-wave-coupling.py` | 1 | **1** | 🟡 **共享面 `common_radiation` 快照差 1 行**（落点归合并收口执行者 ⇒ 见 §47.5） |

**`check-tomcat-limits.py` 红点的逐条归因（新版守门实测输出）**：

- **红点唯一**：`service/task/task-api/src/main/resources/application.yml` ⇒ `rag.http.go-form-limit.mode` 实际 `'raw'`、应为 `'off'`。
- **归属**：`service/task/**` = **G2 写权内** ⇒ ⇒ **G1 不能写，也不该写**。
- **G2 已修**：G2 分支 `align/g2-message-like-task` 的 `4786eb5` 就是这条订正 ⇒ ⇒ **这个红是「还没合过来」，不是「没人管」**。
- **消解路径**：G2 合主干 → 我合主干 ⇒ ⇒ **不需要任何人拍板**（不需要 Jason、不需要合并收口执行者代改）。
- **我域三 api 的实测结论**（新版守门 10 行表格逐行读出）：

| api | ① relaxed-chars | ② max-param | ③ header-size | ④ form-gate | 结论 |
|---|---|---|---|---|---|
| `service/users/user/user-api` | ✅ | ✅ | ✅ | ✅ | **✅** |
| `service/users/admin/admin-api` | ✅ | ✅ | ✅ | ✅ | **✅** |
| `service/follow/follow-api` | ✅ | ✅ | ✅ | ✅ | **✅** |

⇒ ⇒ **我域三个 api 的四旋钮全部对齐**（新版守门比旧版**更严** —— 期望表改成直接读共享层真源
`GoStyleFormLimitMode.SEMANTIC_REACHABILITY` ⇒ **不再维护期望表副本** ⇒ §41/§42.2 记的
「期望表与权威表自相矛盾」这件事在 G4 树里已经不存在）。

### 47.3 🟢 **并入带来的三件事（都是对我域有利的）**

| # | 东西 | 对我域的意义 |
|---|---|---|
| ① | `tools/difftest/check-tomcat-limits.py` 改为**直接读共享层真源** | 我域三个 api 的四旋钮对齐现在由**单一判据**裁决 ⇒ ⇒ 我域 §42.2 记的「根因纠正」在 G4 树里落地了 |
| ② | 共享层新增 `GoStyleRequiredFieldGateTest`（`users-common`，**6 条**） | **我域 `DRIFT-USERSCOMMON-001` 销号结论的两个前提被钉成机器网** ⇒ ⇒ 已在我域 `users/common.md` **§5.7 点名** |
| ③ | `GoStyleParamLimitOffModeGateTest` 补结构锁 ＋ `ObservabilityLifecycle` javadoc 改单池口径 | ②的配套；③是 **javadoc-only**（**已逐行核过 diff** ⇒ **零行为变化**） |

⚠️ **代码层零重叠（实测）**：G4 自 merge-base `f6d58de` 以来真正改的**代码/工具文件只有 4 个**
（`ObservabilityLifecycle.java` · `GoStyleParamLimitOffModeGateTest.java` · `GoStyleRequiredFieldGateTest.java` · `check-tomcat-limits.py`）
⇒ ⇒ 与我写权面（`service/users/{user,admin}/**` ＋ `service/follow/**`）**交集 = 空** ⇒ ⇒ 合并零冲突是**结构性**的，不是运气。
⚠️ 文档层：G4 这 11 笔**没碰** `docs/DRIFT/users/**` 与 `docs/DRIFT/follow/**` ⇒ ⇒ **我域台账未被覆盖**。

### 47.4 🟢 **角色口径改写：「整合者」→「合并收口执行者」（59 处 / 6 份）**

**依据**：`docs/Worktree.md` §1e（主干 `4ae5461`）—— **「整合者」不是一个存在的角色** ⇒
2026-10-27 实测踩到过一次**误升级**：lane 读到「归整合者」就去找「整合者」⇒ ⇒
**角色名 = 一条真实的升级路径** ⇒ ⇒ 共享面必须写成**可指向的职责** ⇒ ⇒ 现行角色名 = **「合并收口执行者」**
（**它是一个步骤，不是一个常驻会话**）。

**改了什么**：我域 6 份台账里 **59 处**「整合者」全部改写为「合并收口执行者」——
`users/vs-common.md` **48** · `follow/vs-common.md` **6** · `users/user/api.md` **2** ·
`users/user/rpc.md` **1** · `users/admin/_shared.md` **1** · `follow/api.md` **1**。

**改的边界（逐条看过上下文，不做盲替换）**：
- ✅ 改的**只有角色词**，**没有一个事实被改动**（计数、行号、commit hash、结论一律照旧）。
- ✅ 两处**读着别扭**的另做了顺句：「（合并收口执行者还没合）」⇒「（合并收口那一步还没做）」；
  「待合并收口执行者合并时统一订正」⇒「待合并收口执行者统一订正时一并处理」。
- ✅ **历史记述里的「整合者」也一并改**：它从来不是「历史上存在过一个整合者」，而是**当时写错了一个角色名**
  ⇒ ⇒ 留着它 = **继续留一条指向不存在角色的升级路径** ⇒ ⇒ 改掉才是如实。
  （**与「订正姿势」不冲突**：那条讲的是**事实**不改；这里是**角色名**订正，事实一个字没动。）

**改完的复核**：① 我域 6 份台账「整合者」残留 **0**；② 门禁复跑全绿（§47.2 表）；
③ **他域同款措辞**（`docs/DRIFT/{like,comment,message,favorite,task,hot,article}/vs-common.md:73`
＋ `docs/DRIFT/global/lanes/L7-slowgate.md`）⇒ ⇒ **不在 G1 写权面** ⇒ ⇒ **零落码、登记**（§47.5）。

### 47.5 🟡 **本轮登记给「合并收口执行者」的共享面事项（我域零落码）**

| # | 事项 | 落点 | 现状 |
|---|---|---|---|
| ① | `docs/DRIFT/_cross/cross-domain-coupling.md` 的 `common_radiation` 快照差 1 行（`"GoStyleRedisClientAutoConfiguration": ["users"]`） | 共享面 ⇒ **G1/G4 都不能写** | 🔴 仍红 ⇒ **必须等四 lane 全合后从合并后的树重算**（单树重生成必错：我树 1 项 / G2 树 9 项） |
| ② | **他域台账的同款「整合者」措辞**（7 份 `docs/DRIFT/<域>/vs-common.md:73` ＋ `global/lanes/L7-slowgate.md`） | 他域写权 ＋ 共享面 | 🟡 我域零落码 ⇒ 建议**合并收口执行者**在合并回写时一并改（否则「只改一份 = 诱导还在」） |
| ③ | `tools/check-ledger-selfcheck.py` 的 `LEDGER_FILES` 补 `follow` 域 + 7 份台账 | **两张表都查不到** ⇒ 写权孤儿 | 🟡 §44.4 已登记 ⇒ **需 Jason 拍板**或纳入合并收口执行者清单 |
| ④ | `docs/Intentional_Deviations/users/_shared.md` 销号（`DRIFT-USERRPC-V-002`） | 不在 G1 写权面 | 🟡 等签字档落点确认 |
| ⑤ | `docs/FileTree/**` 9 处提及 MP 排序桥 | 写权外 | 🟡 §28.7 已登记 |
| ⑥ | `GoStyleRedisClientShadowingGateTest.LEGACY_DOMAIN_COPIES` 里**已失效**的 user-api 条目 | 共享层（`service/common/**`）⇒ **G1/G4 都不能写** | 🟡 **本轮实测确认失效**：`service/users/user/user-api/src/main/java/com/rag/users/user/api/config/RedisDisconnectedBehaviorConfig.java` **在我树里已不存在**（Redis 收编轮真删除）⇒ ⇒ 该守门的 `staleWhitelistHint` 只出**提示、不判失败** ⇒ ⇒ **无害** ⇒ ⇒ 等 G4 清条目 |

> 🟡 **订正注记（2026-10-28 09:0x · 故障处置会话来信 ③ 的路由订正 · 保留上表原文不改写）**
>
> **上表 ② 把两样写权不同的东西捆在一起说 ⇒ 落点写错了 ⇒ 拆成两半**：
>
> | 被点名的东西 | 实测写权（**按守门 `check-wave-lane-ownership.py` 的 `ACTIVE` 白名单实测，不是推断**）| 正确落点 |
> |---|---|---|
> | **7 份 `docs/DRIFT/<域>/vs-common.md`**（article / comment / favorite / hot / like / message / task）| G2 = `docs/DRIFT/{message,like,task}/**` · G3 = `docs/DRIFT/{article,comment,favorite,hot}/**` ⇒ ⇒ **7 份全部在 G2/G3 自己的写权内** | **域侧能自修 ⇒ 域侧自修**（`docs/Worktree.md` §1e 硬边界 1）⇒ ⇒ **G2/G3 改自己那一行，不必等合并收口**（收口代改 = 越权）|
> | **`docs/DRIFT/global/lanes/L7-slowgate.md`（9 处）** | G4 的白名单**只有** `docs/DRIFT/global/vs-common.md` ⇒ ⇒ **`global/lanes/**` 不在任何人白名单** | 🔴 **写权孤儿 ⇒ 归合并收口执行者** |
>
> **各 1 行的实况（我逐份只读核过 · 2026-10-28）**：7 份他域台账**各恰 1 行**，内容同款 ——
> `- [ ] **共享面不回写**（AGENTS.md / 三个 _INDEX.md / _MANIFEST.json / docs/DRIFT/_cross/** ⇒ 合并后由整合者统一更新）`
> （行号：article **72** · comment **73** · favorite **72** · hot **72** · like **73** · message **75** · task **72**）
> ⇒ ⇒ **这是【活的路由句】不是历史记述** ⇒ ⇒ 它指向的角色**根本不存在** ⇒ ⇒ **正是 §47.4 要根除的那个失败模式**（读到「归整合者」就去找「整合者」）
> ⇒ ⇒ **该改，且该由域侧自己改**（我域零落码 —— 7 份不在我写权面）。
> ⇒ ⇒ **已分别告知 G2 / G3**（各 1 行，指明位置）＋ **孤儿项登记给合并收口那一步**（§51.2 表已补一行）。

> 🔴 **订正注记（2026-10-28 12:1x · 故障处置会话来信 ② 的「改判」＋ 我域独立复核 · 保留上表原文不改写）**
>
> **上表 ③ 的「需 Jason 拍板」作废 ⇒ 改判转派 G4**（独立核实，**不采信转述**）：
> - `tools/check-ledger-selfcheck.py` 在 G4 侧有 **2 笔实质提交**（`9037a2b` 提取器补 `@` ＋ `139b428` 第 5 层分子/分母口径）
>   ⇒ ⇒ **G4 是该文件的实质持有者** ⇒ ⇒ 「两张表都查不到 ⇒ 写权孤儿」这个前提
>   **只对「不在任何 lane 白名单」成立，对「不在 13 项 SHARED 清单」不成立** ⇒ ⇒ **改判 ＝ 归 G4**
>   ⇒ ⇒ **本项不再是「等 Jason 拍板」项**（台账登记类事项不占用拍板额度）。
> - ⚠️ **但活还没干**：G4 树里 `LEDGER_FILES` **仍是 `task` / `users` / `users-admin` 三个键**
>   ⇒ ⇒ **follow 等域还没加进去** ⇒ ⇒ 「已转派」≠「已完成」⇒ ⇒ **回执未到前不得记成已闭合**。
> - ⚠️ **口径问题（必须与「补域键」一起给 G4，否则补完仍不算数）**：
>   `docs/DRIFT` 下共 **86** 份，守门 `LEDGER_FILES` **只覆盖 10 份**
>   ⇒ ⇒ **未被覆盖的 76 份**里有 394 个路径引用、**真缺失 1 个**
>   （`tools/difftest/check-ddl-static.py` 被 `docs/DRIFT/global/_README.md` ＋ `docs/DRIFT/like/rpc.md`
>   引用时**少一级 `difftest/`**）
>   ⇒ ⇒ **补 `LEDGER_FILES` 只解决「这些域被扫到」，不解决「层 1 的去噪口径对不对」**
>   ⇒ ⇒ 我域那 8 处判定落在「去噪那 19 条」还是「层 4 的 Go 锚点/行号面」，
>   需按 `--print` **全量取一次**再定 ⇒ ⇒ **只补一条不够**。
> - ⇒ ⇒ **已发信 G4**（2026-10-28 12:1x · 消息 ⑤）⇒ ⇒ **本项归 G4，回执待收**。


### 47.6 🟢 **本轮提交清单**

| commit | 内容 |
|---|---|
| `ddb63f7` | **merge(g4)** —— 并入 G4 终版 `c26e462`（11 笔 · 零冲突 · 代码层零重叠） |
| `18d63cb` | **docs(drift)** —— §47 写回 ＋ 6 份台账 59 处角色口径改写 ＋ `users/common.md` §5.7 点名 |
| `6535ac4` | **docs(drift)** —— §43.3 状态订正（四项里两项已变，原值保留只补指针） |
| （本笔） | **docs(drift)** —— §47.7 回归实测写回 ＋ §47.5 ⑥ 补「白名单条目已失效」的实证 |

## 48. 🔴 **rpc 侧两腿同口径活体对拍（Go 腿本域自己实测）＋ 共享层 artifact 新鲜度守门**（2026-10-28 · G1 lane）

### 48.1 🔴 **四条腿同口径实测：Go 腿 1689ms（user）/ 1680ms（admin）（客户端可见 status = INTERNAL「注销失败,Redis写入异常」）vs RAG 腿 0ms / 3ms**

**起因**：§45/§46 之前记的「Go 的断连失败耗时 ≈1.7s」是**引 G2 在它自己域测的数**
（`user/rpc.md` §5 明写「**不是我在 user-rpc 域测的**」）⇒ ⇒ 本轮**自己跑一遍**。

**口径（两侧必须逐条一致，否则数字不可比）**：

| 维度 | 两侧一致的做法 | 为什么 |
|---|---|---|
| **故障注入形态** | 「**先关监听口**（重连被 connection refused 挡掉）＋ **再对已建立连接半关闭**（客户端读到 FIN 而不是 RST）」 | ⚠️ `docker stop` 与「半关闭代理」是**两种不同形态** ⇒ 数字**不可直接比** ⇒ ⇒ 本轮两侧统一用**代理形态**（RAG 腿 = `GracefulDisconnectProxy`；Go 腿 = 探针里同款实现） |
| **静默窗口** | 断连前**静默 1000ms 且期间不发任何命令** | 发了命令 ⇒ watchdog 提前重连 ⇒ **断连后第一条命令**这个测点被掩盖 |
| **测点** | **断连后的第一条写命令** | Go 腿 = 真源 `logoutlogic.Logout` 的第一条 `SetexCtx`；RAG 腿 = `redisTemplate.opsForValue().set(...)`（**上层 `UserDomainService.logout` 的写黑名单**） |

**Go 腿（真源，整仓复制到 `/tmp` 后跑；真源仓零改动）**：

⚠️ **user 域与 admin 域的真源 logout 是【两份独立实现】**（本轮**自己核过**才敢这么写）：
`service/user/user/rpc/internal/logic/logoutlogic.go`（黑名单前缀 `user:jwt_blacklist:` · `Config.UserAuth.AccessExpire`）
与 `service/user/admin/rpc/internal/logic/logoutlogic.go`（前缀 `admin:jwt_blacklist:` · `Config.AdminAuth.AccessExpire`）
⇒ ⇒ ⇒ 故**给 admin 单独做了一条探针**（`probe-admin`）⇒ ⇒ **两个域的 Go 腿都是自己实测的**。

| 项 | 内容 | 出处 |
|---|---|---|
| 被测代码 | **真源的 `logic.LogoutLogic.Logout`**（不是重写）；**两个域各一份** | user: `service/user/user/rpc/internal/logic/logoutlogic.go:29-46`；admin: `service/user/admin/rpc/internal/logic/logoutlogic.go:31-48` |
| Redis 客户端 | **真源同款**：`redis.MustNewRedis(c.BizRedis)` ⇒ go-redis v9.20.0 的 `Options{Addr,Username,Password,DB,MaxRetries:3,MinIdleConns:8}` ⇒ **三个 timeout 一个都没传** | `svc/servicecontext.go:29`（admin 同名同式）＋ go-zero v1.10.2 `core/stores/redis/redisclientmanager.go:13-15,32-39` |
| conf 逐字取 | `BizRedis{Host, Type:"node", Pass:""}` ＋ `NonBlock:true`（go-zero conf 的 json 默认值）＋ `PingTimeout:1s` ＋ `AccessExpire=2592000`（user 取 `UserAuth` / admin 取 `AdminAuth`） | user: `etc/user.yaml:17-20,33-35`；admin: `etc/admin.yaml:16-19,27-29` ＋ go-zero `core/stores/redis/conf.go:19-28` |
| 初始化 | `logx.MustSetup(c.Log)` ＋ `logger.Init(c.Name)`（**实参是 `c.Name`**：`user.rpc` / `admin.rpc` ⇒ **不是** `Log.ServiceName`） | user: `user.go:30-31`；admin: `admin.go:31-32` |

**实测输出（原文）**：

```
① user-rpc 腿：
   PROBE-RESULT mode=proxy elapsed_ms=1689 grpc_code=Internal grpc_message="注销失败,Redis写入异常"
   （首轮曾测得 1737ms ⇒ 同量级；go-zero 的 duration hook 同时记录 slowcall · 1736.6ms）
② admin-rpc 腿：
   PROBE-RESULT mode=proxy elapsed_ms=1680 grpc_code=Internal grpc_message="注销失败,Redis写入异常"
   （底层错误同形 = dial tcp …: connect: connection refused）
```

⇒ ⇒ **Go 腿的「客户端可见 gRPC status」是**直接观测到的**（`status.FromError` 读的就是 server 要返回的那个 status 对象）
⇒ ⇒ 不是源码推断。

**RAG 腿（我域现有守门，同款代理形态）**：
`UserRpcRedisDisconnectedFastFailTest` ⇒ `[Redis 断连实测] user-rpc 写黑名单失败耗时=0ms（预算 1500ms）`；
`AdminRpcRedisDisconnectedFastFailTest` ⇒ `[Redis 断连实测] admin-rpc 写黑名单失败耗时=3ms（预算 1500ms）` ⇒ 各自 2/2 绿。

**⇒⇒ 四条腿并排**：

| 条目 | 腿 | 断连后第一条写的失败耗时 | 客户端可见 gRPC status | 证据强度 |
|---|---|---|---|---|
| `DRIFT-USERRPC-V-014` | **Go**（真源 user） | **1689ms**（≪ 2s rpc 预算 ⇒ **落在预算内**） | `Internal` + 「注销失败,Redis写入异常」 | **L4（活体 · 进程内直观测）** |
| `DRIFT-USERRPC-V-014` | **RAG**（共享层 Lettuce `REJECT_COMMANDS`） | **0ms** | **未观测**（服务层 `catch` 映射是源码） | 耗时 = **L4**；status = **L1** |
| `DRIFT-ARPC-013` | **Go**（真源 admin） | **1680ms** | `Internal` + 「注销失败,Redis写入异常」 | **L4（活体 · 进程内直观测）** |
| `DRIFT-ARPC-013` | **RAG** | **3ms** | **未观测** | 耗时 = **L4**；status = **L1** |

⇒ ⇒ **两侧都 ≪ 2s rpc 预算** ⇒ ⇒ ⇒ 两侧都会**在预算内**走到 `catch` 块 ⇒ ⇒ ⇒ **「客户端看到 1015（超时）还是 INTERNAL」的分歧点已闭合**：
**两侧都不是 1015**（Go 快速失败在 1.7s 内，RAG 0~3ms）⇒ ⇒ ⇒ 这是本轮最实质的一条结论。

⚠️ **诚实边界（⇒⇒ 本条**不写 L4**）**：**两侧的「客户端可见 gRPC 响应」这一格，Go 腿 L4、RAG 腿仍只有 L1**
⇒ ⇒ ⇒ **V-014 / ARPC-013 的口径 = 「机制面（断连后第一条写的失败形态与耗时）双侧 L4 ＋ 客户端可见响应面 Go 腿 L4 / RAG 腿 L1」**
⇒ ⇒ ⇒ **不笼统写 L4**（与 G3 的同款纪律：两侧强度不齐 ⇒ 按弱侧记）。

⚠️ **对照组（G3 点名要的那一句）**：**A 组有、B 组没有同场对照**
⇒ ⇒ A 组 = 共享层那份 `REJECT_COMMANDS`（活体，断言「消费点读到 `REJECT_COMMANDS`」＋ 耗时 < 1500ms）；
⇒ ⇒ **B 组只以「变异」形态存在**（M2/M3：把 `REJECT_COMMANDS` 改成 `DEFAULT` ⇒ 实测 ≈3000ms ⇒ 红）
⇒ ⇒ ⇒ **不是**「同一次运行里只换那一条」的 A/B ⇒ ⇒ 按 G3 的判定式，**A 组不能单独证明「是那条机制决定的」**
⇒ ⇒ ⇒ 故 RAG 腿的**机制归因**强度 = L3（不是 L4）；**而 Go 腿那 1689ms / 1680ms 是真源直接跑出来的，不依赖这层归因**。

⚠️ **两条踩坑（留给下一轮）**：
① **探针第一次跑 panic 在 `logger.LogBusinessErr`** ⇒ 真源 `service/common/logger/logger.go:101` 要求先 `logger.Init(...)`
   ⇒ ⇒ 探针必须**复现真实调用路径**（真源 `user.go:31` 会调）⇒ ⇒ 这是 memory 里「探针必须复现真实调用路径」的又一例。
② **探针源码落点 `/tmp/g1_go_probe_v1`**（真源整仓副本）⇒ **/tmp 会清** ⇒ ⇒ 与 §8.4 的 JWT 探针同款处置：
   **本节把关键源码与出处抄进域台账**（域台账在 G1 写权内 ⇒ 不会被 /tmp 清空带走）。

### 48.1-bis 🟢 **隔离维度逐条对照（G3 交来的隔离配方 ⇒ 我域 7 条里 5 条 N/A、2 条已排除）**

> **为什么加这一节**：G3 在 `comment/rpc.md` §4.6.1-bis 把「**怎么把两侧同时跑起来**」的隔离配方交给了我域
> （它原话：「交的是**隔离配方**，不是只有结果」）⇒ ⇒ 其中一条纪律是「**这 7 条里任何一条没做到 ⇒ 差异里会混进『串味』
> ⇒ ⇒ 得到的是『两侧环境差异』而不是『两侧差异』⇒ ⇒ 对拍结论作废**」⇒ ⇒ **我必须逐条对照，不能默认自己没串味。**
> ⇒ ⇒ **对照结果（逐条查了，不是想当然）**：

| G3 的隔离维度 | 我域（rpc 侧断连对拍）是否适用 | 依据 |
|---|---|---|
| **Redis 分 db**（Go `db0` / RAG `db1`） | 🟢 **不适用** ⇒ ⇒ **串味已排除** | G3 那条是「**共用同一个 Redis 实例**」时的隔离法 ⇒ ⇒ **我域两侧根本不是同一个实例**：Go 腿用**专用容器 `g1-users-probe-redis`**（本轮收尾已 `docker rm -f` 清掉）· RAG 腿用**它自己 Testcontainers 起的 `redis:7-alpine`**（`UserRpcRedisDisconnectedFastFailTest` 的 `@Container`）⇒ ⇒ **两个独立实例 ⇒ db 级串味不可能发生**<br>⚠️ **我探针的 `RedisConf` 没设 `DB` 字段 ⇒ 走 db0**（go-zero `RedisConf.DB` 零值 = 0）⇒ ⇒ **但既然不是同一实例，这条就不构成风险** ⇒ ⇒ **前提是「实例不同」，不是「db 不同」** |
| **数据库分库**（`go_comment` / `rag_comment`） | 🟢 **不适用** | Go 腿**跳过 `model.InitDB`**（`logoutlogic.Logout` 只读 `Config.UserAuth.AccessExpire` 与 `BizRedis` ⇒ **不碰 `UserModel`** ⇒ 源码 `logoutlogic.go:29-46` 全文可核）⇒ ⇒ **两侧都不连 PG** ⇒ ⇒ 无库可串 |
| **Kafka 两套 topic + 两套消费组** | 🟢 **N/A** | users / follow 域 **rpc 侧不涉 Kafka**（用户域无 Kafka 事实链路） |
| **服务发现**（RAG 关 Nacos 注册 / Go 删 etcd） | 🟢 **N/A** | 两腿都是**进程内直调**（Go 腿直调真源 `logic.LogoutLogic.Logout`；RAG 腿直调 `UserDomainService.logout`）⇒ ⇒ **不经服务发现、不起完整应用** ⇒ ⇒ 不注册也不查 Nacos |
| **端口分离**（Go `19001` / RAG `59001`） | 🟢 **N/A** | 同上 ⇒ ⇒ **两腿都不监听端口**（Redis 走本地代理口）⇒ ⇒ 无端口可撞 |
| **下游 RPC 同一死端点** | 🟢 **N/A** | logout 路径**不调下游 RPC**（无出向 stub） |
| **消息位点**（`Offset: first` + 每轮删 topic） | 🟢 **N/A** | 同 Kafka 条 |

⇒ ⇒ **7 条里 5 条 N/A、1 条（Redis）已用「独立实例」排除、1 条（PG）不适用** ⇒ ⇒ **串味已排除** ⇒ ⇒ **本节的四条腿数字仍然可用**。
⚠️ **但这一条以前没写进台账** ⇒ ⇒ **缺陷在于「没记录」，不在于「没做」** ⇒ ⇒ ⇒ **教训：隔离维度要逐条落账**
⇒ ⇒ **否则下一个读台账的人无法判断「这组数字是不是混了环境差异」** ⇒ ⇒ ⇒ **这正是 G3 那句纪律的落点：不是「做了就行」，是「要能证明做了」。**

### 48.2 🔴 **3 台「共享层 artifact 新鲜度守门」⇒ 把「陈旧 `~/.m2` jar」从谜团变成一条精确的红**

**起因 = G2 回执②**（🔴 急件）：单模块构建不带 `-am` ⇒ `service/common` 不在 reactor ⇒ 依赖从 `~/.m2` 取
⇒ ⇒ 共享层那份自动配置**不在产物里** ⇒ Boot 4 下 MyBatis-Plus 早于 DataSource 求值 ⇒ **进程启动即挂**。

**本域独立复核（不采信转述）—— 实测三条**：

| # | 实测 | 结论 |
|---|---|---|
| ① | `~/.m2` 那份 `common-0.0.1-SNAPSHOT.jar` = **215 837 B / 2026-10-27 12:00** ⇒ 它的 `AutoConfiguration.imports` **只有 2 行**（otel + json content type）⇒ **排序桥 0 行** ⇒ 排序桥与 Redis 自动配置的 **class 文件都没有** | G2 的机制**成立** |
| ② | 单模块跑 `UserRpcContainerWiringTest` ⇒ **18/18 errors**（`ClassNotFoundException` ⇒ Spring 上下文起不来） | **红得不明白**：看不出根因、也不知道怎么修 |
| ③ | 🔴 `service/common/target/common-0.0.1-SNAPSHOT.jar` **本身也是陈旧的**（2026-10-26 23:36）⇒ 因为只跑 `test` 阶段时 `package` 不执行 | ⚠️ **拿 `target/` 里的 jar 当证据 = 拿 stale 产物当证据** ⇒ ⇒ 守门必须读「**当次真正被解析到的那个 artifact**」 |

**落码（3 份 · 各 2 条断言）**：`UserRpcSharedLayerArtifactFreshnessGateTest` ·
`AdminRpcSharedLayerArtifactFreshnessGateTest` · `FollowRpcSharedLayerArtifactFreshnessGateTest`
① 解析到的共享层 artifact 必须携带「排序桥 + Redis 自动配置」两个 FQCN（**独立字面量** ⇒ 不从被测代码 import
   ⇒ 共享层改包名时本守门会**如实转红**，而不是跟着一起变继续绿）
② 域内那份排序桥必须不在本模块源码里（收编真的发生了，不是两份叠加）

**为什么这一层就够（机械论证）**：fat jar 内嵌的 `BOOT-INF/lib/common-*.jar` = 构建时**被解析到的那个 artifact**
⇒ ⇒ ⇒ **类path 层 ≡ 产物层** ⇒ ⇒ 不需要再加一台「解 fat jar」的产物层守门
（那台还得处理「嵌套 maven 与外层构建抢同一个 `target/`」的冲突 —— G2 的 task-rpc 正是用「产物 zip 手术」绕过去的）。

**变异验证（两条断言各有判别力）**：

| 变异 | 实测 | 结论 |
|---|---|---|
| **A**：不带 `-am` 单模块构建（= 真实缺陷场景） | **Failures: 1, Errors: 0** ⇒ **红在目标断言**（第②条仍绿）⇒ 消息直接点名「陈旧 common jar」＋ 修法（加 `-am` 或先 `mvn -pl service/common install`） | ⇒ 同一场景**落码前**是 18/18 errors 的谜团墙 ⇒ **落码后 = 1 条精确的红** |
| **B**：把域内那份排序桥放回源码 | **Failures: 1, Errors: 0** ⇒ **红在第②条**（第①条仍绿） | ⇒ 两条断言**各有**判别力 |

⇒ ⇒ 还原后 3 台 × 2 条**全绿**（0.098s / 0.092s / 0.317s）。

⚠️ **订正 G2 的一处口径（他那条「证据强度 = 0」不成立）**：我域**已有** 3 台**容器装配层**守门
（`UserRpcContainerWiringTest` / `AdminRpcContainerWiringTest` / `FollowRpcWiringTest`），各有**三条腿**
（注册层：classpath 的 imports 里有共享层那份 / 域内副本已消失（`Class.forName` 必抛 `ClassNotFoundException`）/
机制层：MP **真的产出了 `SqlSessionFactory`**）⇒ ⇒ **缺的只是「陈旧 jar 时红得不明白」这一层** ⇒ ⇒ 本次补的正是它。

### 48.3 🟢 **并入 G4 分支尖端 `a38ad75`（9 笔 · 零冲突）**

- 这 9 笔 = **8 笔 global 台账 + 1 笔 `docs/Worktree.md` §1e 第②条订正 + 1 笔合并主干** ⇒ ⇒ **共享层生产代码零改动**。
- ⇒ ⇒ G4 回执②点名的 `15cf93a` / `407c230` / `e966a67` / `139b428` **早已在我树**
  ⇒ ⇒ ⇒ 我上一轮的域级全量（**688 条** · 18/18 BUILD SUCCESS）**已经覆盖**了共享层的运行时变更
  ⇒ ⇒ ⇒ **G4 要求的「合并后重跑一次收口回归」已满足**。
- ⇒ ⇒ 本轮仍再跑一次域级全量，但理由是**我新增了 3 台守门（+6 条）**，**不是**共享层变了
  ⇒ ⇒ **别把「重跑」与「共享层变更」混为一谈**（同 §45.7「基线过期」那条纪律：要说清**为什么**重跑）。

### 48.4 🟢 **本轮域级全量回归（实测 · 2026-10-28 01:45~01:55 · 18/18 BUILD SUCCESS）**

| 模块 | 上轮（§47.7） | **本轮实测** | 差 | 差在哪 |
|---|---|---|---|---|
| `service/users/user/user-api` | 125 | **125** | 0 | |
| `service/users/user/user-rpc` | 148 | **150** | **+2** | 新守门 `UserRpcSharedLayerArtifactFreshnessGateTest` |
| `service/users/admin/admin-api` | 144 | **144** | 0 | |
| `service/users/admin/admin-rpc` | 118 | **120** | **+2** | 新守门 `AdminRpcSharedLayerArtifactFreshnessGateTest` |
| `service/follow/follow-api` | 72 | **72** | 0 | |
| `service/follow/follow-rpc` | 81 | **83** | **+2** | 新守门 `FollowRpcSharedLayerArtifactFreshnessGateTest` |
| **我域 6 模块合计** | **688** | **694** | **+6** | **全部来自 3 台新守门**（3 × 2）⇒ ⇒ **零条旧用例被动**（= 新守门没扰动既有面） |
| 共享层 `service/common` | 331 | **331** | 0 | 本轮并入 G4 的 9 笔**零生产代码** ⇒ 逐条不变 |
| 共享层 `service/users/users-common` | 39 | **39** | 0 | 同上 |
| （本域私有）`service/follow/follow-common` | 37 | **37** | 0 | |

⇒ ⇒ **failures / errors / skipped 全 0**；`mvn` **EXIT=0**；`BUILD SUCCESS`；总耗时 **9:45**。
⇒ ⇒ **口径纪律**：本轮是**真重跑全量**（`clean test` ＋ `-am`）⇒ ⇒ **688 → 694** 是**实测**，不是推算
⇒ ⇒ ⇒ 差值 6 条**逐条对得上**（3 台 × 2）⇒ ⇒ **「总量相同 ≠ 构成相同」的反例**：总量变了 6，**且能逐条说清是哪 6 条**。
⚠️ **窗口纪律**：起跑前守「别的 lane 有没有 maven 在跑」（G2 当时在跑）⇒ 等 60 秒拿到窗口 ⇒ 起跑前二次确认
⇒ ⇒ 本轮**没有制造并发全量**。


### 47.7 🟢 **并入 G4 终版后的域级全量回归（实测 · 2026-10-28 00:09~00:17 · 18/18 BUILD SUCCESS）**

**命令**（`clean` ＋ `-am` ＋ 显式堆）：
`mvn -B -o -DargLine=-Xmx2g -am -pl service/users/user/user-api,service/users/user/user-rpc,service/users/admin/admin-api,service/users/admin/admin-rpc,service/follow/follow-api,service/follow/follow-rpc clean test`

| 模块 | 并入前 | **本轮实测** | 差 |
|---|---|---|---|
| `service/users/user/user-api` | 125 | **125** | 0 |
| `service/users/user/user-rpc` | 148 | **148** | 0 |
| `service/users/admin/admin-api` | 144 | **144** | 0 |
| `service/users/admin/admin-rpc` | 118 | **118** | 0 |
| `service/follow/follow-api` | 72 | **72** | 0 |
| `service/follow/follow-rpc` | 81 | **81** | 0 |
| **我域 6 模块合计** | **688** | **688** | **0** |
| 共享层 `service/common` | 330 | **331** | **+1** |
| 共享层 `service/users/users-common` | 33 | **39** | **+6** |
| （本域私有）`service/follow/follow-common` | 37 | **37** | 0 |

- ⇒ ⇒ **failures / errors / skipped 全 0**；`mvn` **EXIT=0**；`BUILD SUCCESS`；总耗时 **7:46**。
- ⇒ ⇒ **域内 6 模块逐条不变（688 = 688）** ⇒ ⇒ 这**本身就是「并入对我域零副作用」的证据**
  （因为并入在代码层与我的写权面**零重叠** ⇒ ⇒ 域内条数**理应**不变；实测与理应一致 ⇒ ⇒ 两侧互证）。
- ⇒ ⇒ **共享层 +7 条全部有出处**：`common` +1 = `GoStyleParamLimitOffModeGateTest` 补结构锁；
  `users-common` +6 = `GoStyleRequiredFieldGateTest`（§5.7 点名的那份）。
- ⇒ ⇒ **「总量相同 ≠ 构成相同」的又一个例证**：**域内**总量 688 前后**一模一样**，
  而**共享层**已经变了（363 → 370）⇒ ⇒ 只报「688」这一个数**会漏掉共享层的变动** ⇒ ⇒ 报数必须**分域 + 分层**。
- ⚠️ **本轮是「真重跑全量」，不是推算**（依 §45.7 那条教训：只重跑受影响模块 + 沿用上轮总数 ⇒ 总数会悄悄过期且无门禁会报）。

**窗口纪律的执行（记录，因为它是本轮唯一的「调度」动作）**：
- 起跑前用「**别的 lane 有没有 maven 在跑**」做守卫 ⇒ **G2 与 G3 同时在跑全量** ⇒ ⇒ 本轮**等 120 秒**才拿到窗口；
- ⇒ ⇒ **没有制造第三次并发全量**（G4 实测并发全量会被 OOM-kill ⇒ EXIT=137）⇒ ⇒ 纪律是有效的，不是形式主义；
- ⇒ ⇒ 起跑前**二次确认**（避免「刚空出来就被别人抢走」的竞态）。

⚠️ **日志里的 `ERROR` 行 ≠ 失败**（本轮日志里有很多，避免下一轮 agent 误判）：
- `nacos register FAILED ...` ⇒ 测试环境**没有 Nacos** ⇒ 启动自检按预期**记 ERROR 后继续** ⇒ ⇒ **不是测试失败**；
- `Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0).`
  ⇒ surefire **fork 子 JVM 的收尾消息** ⇒ ⇒ **不是失败**；
- ⇒ ⇒ **判据只能看两样**：**surefire 的 `Tests run` 汇总**（Failures / Errors / Skipped 三项）
  ＋ **`mvn` 的退出码** ⇒ ⇒ **不要 grep 日志里的 `ERROR` / `FAIL` 字符串当结论**（那正是「拿一个信号当结论」）。



## 49. 🟢 **跨 lane 回执轮（2026-10-28 · G4 增量信 + 白名单复算 + 引用订正收尾）**

### 49.1 🟢 **G4 的源扫守门在我域【恒绿】—— 用它自己的扫描口径复算过（不是用我的 grep）**

| 项 | 实测 |
|---|---|
| 复算方式 | **复刻 G4 守门 `GoStyleRedisClientShadowingGateTest.scanForDomainCopies(...)` 的判据**（只走 `<module>/src/main/java`；`content.contains("LettuceClientOptionsBuilderCustomizer")`；排共享层 `service/common`；排白名单）⇒ ⇒ **口径与它一致** ⇒ ⇒ 我域的绿灯是**它会给的绿灯**，不是「我以为的绿灯」 |
| 域内副本（排共享层 ＋ 排白名单） | **0 个** |
| 全仓 `src/main/java` 提到该类型的文件 | **1 个** = `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java`（**共享层自己那份** = 它的断言③(a) 锚点）⇒ ⇒ **域内零副本** |
| 白名单两条对应文件 | **都已不存在**（`user-api/.../config/RedisDisconnectedBehaviorConfig.java` 等）⇒ ⇒ **删除条件早已触发** ⇒ ⇒ **白名单可以整表清空**（`Map.of()` 空表合法） |
| 为什么 user-api 没被误命中 | 唯一提到该类型的是 `UserApiRedisDisconnectedFastFailTest.java` ⇒ ① 在 **`src/test/java`**（不在扫描面）② 那是**「用」不是「声明」**（`getBeansOfType(...)`） |

⇒ ⇒ **G4 原话「白名单在我树里还列着 user-api 那份、我不能删（你的写权）」不成立** ⇒ ⇒ 白名单文件在**共享层测试**里 ⇒ **是 G4 的写权**；
⇒ ⇒ **且触发条件（G1 删掉域内副本）早已满足** ⇒ ⇒ 已随增量信告知（队列 position 54）。
⇒ ⇒ 副本收编本身早已完成 ⇒ 逐条 ⇒ 本文件 **§26**。

### 49.2 🟢 **G4 §4.10.39 ②「本层未实测的两层」—— 我域两层都已答，且都到 L4**

| G4 原文的「未实测层」 | 我域实测 | 证据 |
|---|---|---|
| ① 正确形态应是 `PostgreSQLContainer` ＋ `withCreateContainerCmdModifier(...)` 显式绑口 | ✅ **就按这个处方落的**（未用 §39 记的 ServiceLoader 形态）⇒ 6 份钉口支撑类 + **38 个容器起点** + 2 台域级守门 | 本文件 **§45** |
| ② 「显式绑口后 `getMappedPort()` 是否返回那个固定口」未实测 ⇒ 必须先写探针、不能照抄 | ✅ **答案是「是」**（**真容器 L4 实测**）⇒ ⇒ 既有测试继续用 `getMappedPort()` / `getJdbcUrl()`，零改动 | 本文件 **§39.1** |

⇒ ⇒ **G4 的处方两条都成立** ⇒ ⇒ 别的域可直接照抄**形态** ⇒ 但 **② 仍建议各自先写一条探针**（我域的 L4 结论**不可外推**给别域）。

### 49.3 🟢 **本轮对 G4 的增量信（队列 position 54 · 不是重发已入队那封）**

信里回答/告知的 10 条（**全部结论已先落本文件与 `admin/rpc.md`**，消息只是通知）：
① 已并入 G4 尖端 `b79779e`（3 笔 · 零冲突）⇒ **落后 0**；② 它的源扫守门 `22dc67e` 已在我树；
③ §4.10.39 ② 两层已答（见 49.2）；④ 我域 §36 已完成（§45）；⑤ §34.4 的 Ryuk 归属订正我域**早已**有（2026-10-27 晚）+ 本轮两条口径 + 48 容器自核；
⑥ **白名单可清**（见 49.1）；⑦ **`LEDGER_FILES` 补 follow 域 + 2 份 users 台账**这条反馈单**改判给 G4**（它已实权接管该文件 ⇒ **不再需要 Jason 拍板**）；
⑧ 它的 `9037a2b` 一修**立刻在我域抓出 2 条真错引用**（见 **§44.5**）⇒ ⇒ **这是那个修法有效的直接证据**；
⑨ 数字订正 = **694**（不是 669）；⚠️ **本轮没重跑** ⇒ 因**它正在跑全量** ⇒ 按「不并发全量」纪律**主动让开**（不是跑不动）；
⑩ 两个既有红点与上轮**完全一致、无新增**（`task-api` 属 G2 且已在其树修 ⇒ 待合过来；`common_radiation` 须四 lane 全合后从合并树重算）。

### 49.4 ⚠️ **本轮我自己踩的两个坑（都留档，因为它们会重复发生）**

- **坑①：手写 markdown 锚点** ⇒ 我手写的 `#45-🟢-…-g4-4-10-39-…` 与守门 `slug()` 实算的 **`g4-41039`** 不一致
  ⇒ ⇒ **`check-doc-links.py` 实测 2 项锚点失效** ⇒ ⇒ **真 slug 里「`.` 被删除但【不补连字符】」** ⇒ ⇒ 修法 = **用脚本算**，别手写。
  ⚠️ **我第一版归因写错了**（说「`🟢`/`②` 导致不一致」）⇒ ⇒ **`🟢`（`So`）与 `②`（`No`）在 `slug()` 里都被保留** ⇒ ⇒ **不是原因** ⇒ ⇒ 已订正。
- **坑②：在台账里逐字引用源码句 `interceptors[0]` ＋ `(ctx, …)`** ⇒ ⇒ **`LINK_RE` 不跳过代码块** ⇒ ⇒ 下标 ＋ 圆括号相邻 ⇒ ⇒ 被 markdown 解析成**链接** ⇒ ⇒ **守门误报「文件链接失效」**
  ⇒ ⇒ ⚠️ **本条自己就是活证据**：**连「讲这个坑」的那一行都踩了同一个坑** ⇒ ⇒ 第一版写的是逐字原句 ⇒ ⇒ 守门照样报失效 ⇒ ⇒ **只能拆开写**。
  ⇒ ⇒ 修法 = **把 `[` 与 `(` 拆开写**（`interceptors[0]` ＋ `(ctx, …)`）⇒ ⇒ 或改用**纯文字描述**。
  ⇒ ⇒ **教训：台账里贴源码要避开 markdown 的链接语法** ⇒ ⇒ 这条已随 §44.5 落账。

⇒ ⇒ **本轮提交序列**：`3914064`（§34.4 两条口径 + §36 状态订正 + 锚点自查）→ `f6a01fb`（merge G4 `b79779e`）→ `bc34c7c`（2 条错引用改正 + 落点归属改判 + §44.5）。

## 50. 🔴🟢 **L4 缺口闭合：RAG 腿的「客户端可见 gRPC 响应」升 L4 ＋ 同场 A/B 反事实对照**（2026-10-28 · G1 lane）

### 50.1 🟢 **§48.1 留下的那一格，现在补上了（user + admin 两个域各自闭合）**

**起因**：§48.1 的四条腿里，**Go 腿的「客户端可见 gRPC status」是 L4**（真源进程内直观测），
而 **RAG 腿那一格当时只有 L1**（源码推断）⇒ ⇒ 两侧强度不齐 ⇒ 按弱侧记
⇒ ⇒ **V-014 / ARPC-013 当时只能写「机制面双侧 L4 ＋ 响应面 Go 腿 L4 / RAG 腿 L1」**。

**为什么不能直调 service 就完事**：那一格要答的不是「service 抛了什么异常」，而是
「**客户端最终看到什么 status**」⇒ ⇒ 而最终形态由**整条拦截器链**决定
（`recover → breaker → shedding → 业务观测拦截器(2s 预算)`）
⇒ ⇒ 直调 `UserDomainService.logout` **绕过了整条链** ⇒ ⇒ 恰恰漏掉题眼那一格：
**2s 超时预算会不会先于业务异常触发**（触发了 ⇒ 客户端看到 `DEADLINE_EXCEEDED` / 1015，**不是** `INTERNAL`）。

**做法**：`@SpringBootTest(classes = UserRpcApplication.class)` ⇒
`grpc-server-spring-boot-starter` 的 `@GrpcService` 会**真起一个 gRPC server**
⇒ ⇒ 用**真 gRPC 客户端**调 `logout` ⇒ ⇒ 把 **wire 上**的 status 抓下来。

**实测（四组，全部实测原文）**：

| 腿 | 断连策略 | 客户端可见 status | 耗时 | 证据 |
|---|---|---|---|---|
| **Go user**（真源） | — | `Internal` ＋「注销失败,Redis写入异常」 | **1689ms** | L4（§48.1） |
| **Go admin**（真源） | — | `Internal` ＋「注销失败,Redis写入异常」 | **1680ms** | L4（§48.1） |
| **RAG user · A 组** | `REJECT_COMMANDS`（共享层默认） | `INTERNAL` ＋「注销失败,Redis写入异常」 | **19ms** | **L4（wire 实测）** |
| **RAG user · B 组** | `DEFAULT`（test 侧遮蔽共享层） | **`DEADLINE_EXCEEDED`** ＋「context deadline exceeded」 | **2013ms** | L4（同场反例） |
| **RAG admin · A 组** | `REJECT_COMMANDS`（共享层默认） | `INTERNAL` ＋「注销失败,Redis写入异常」 | **8ms** | **L4（wire 实测）** |
| **RAG admin · B 组** | `DEFAULT`（test 侧遮蔽共享层） | **`DEADLINE_EXCEEDED`** ＋「context deadline exceeded」 | **2010ms** | L4（同场反例） |

⇒ ⇒ **口径更新**：
- **V-014 / ARPC-013 的证据强度 = 双侧 L4**（不再有「RAG 腿 L1」那一格）⇒ ⇒ **逐条订正见 `user/rpc.md` / `admin/rpc.md`**。
- **「客户端看到 1015 还是 INTERNAL」这个分歧点，现在有正面证据了**：
  **RAG（有 `REJECT_COMMANDS`）= `INTERNAL`（与 Go 逐字同形）**；
  **RAG（没有它）= `DEADLINE_EXCEEDED` / 1015** ⇒ ⇒ **这条修复不是可有可无的严格性偏好，而是「客户端能不能拿到与 Go 一样的 status」的前提**。

### 50.2 🟢 **B 组怎么做到「唯一变量」⇒ 以及由此带来的三条纪律**

**机制**：共享层 `GoStyleRedisClientAutoConfiguration` 的 bean 带 **`@ConditionalOnMissingBean`**
⇒ ⇒ **域侧一旦已定义 `LettuceClientOptionsBuilderCustomizer`，共享层那份根本不会创建**
（是「**遮蔽**」不是「叠加」）⇒ ⇒ 于是 **B 组可以是域侧 test 侧的一个 `@TestConfiguration`**：
提供一份值设成 `DEFAULT` 的 customizer ⇒ ⇒ **断连策略从 `REJECT_COMMANDS` 变成 `DEFAULT`，其余全部不变**。
⇒ ⇒ **域内零落码**（不碰共享层、不碰域内生产代码）。

**纪律①（同一次对照内部必须用【同一份】被测组件的实现）**：
⇒ A 组与 B 组**必须用同一个代理实现** ⇒ ⇒ 否则有人改了其中一份 ⇒ 对照**悄悄换了变量**
⇒ ⇒ **结论作废且看不出来**（测试照样绿）⇒ ⇒ **user-rpc 的那份代理因此抽到
`support/GoStyleRedisDisconnectProxy`**（**逐字搬运 + 抽完重跑原测试**确认未漂）。
⚠️ **抽出去的理由不是去重，是方法学** ⇒ ⇒ 注释里必须写对，否则下一个人会当「可跳过的重构」删掉。
⚠️ admin-rpc **不需要抽** ⇒ ⇒ 它原本就有一份嵌套代理 ⇒ ⇒ 本轮的 admin A/B 两组**共用那一份** ⇒ ⇒ 同样满足纪律。

**纪律②（故障注入类测试的第一条断言必须是「故障确实按预期形态发生了」）**：
⚠️ **本轮 B 组第一版漏了「先建立连接」** ⇒ ⇒ Lettuce **从未建连** ⇒ ⇒ 断连后发的第一条命令是
「**连接被拒**」（proxy 监听口已关 ⇒ connection refused ⇒ **~15ms 快失败**）
⇒ ⇒ 测的是「**连不上**」，**不是**「连上之后被断开」⇒ ⇒ `disconnectedBehavior` **根本不起作用**
⇒ ⇒ 第一版实测 `elapsedMs=15 · INTERNAL`（**假象**）⇒ ⇒ 补上对照组后才复现出真正的排队（2013ms / 1015）。
⇒ ⇒ **症状一样 ≠ 故障一样** ⇒ ⇒ 报数前必须先问「这个故障真的是我以为的那个吗」。

**纪律③（前提断言与目标断言要分开，并分别做变异）**：

| 变异 | 注入 | 红在 | 证明了什么 |
|---|---|---|---|
| **M1** | 摘掉 B 组那个 `@Bean` ⇒ 共享层 `REJECT_COMMANDS` 接管 | **前提断言**：「断连行为必须是 DEFAULT ⇒ expected: <DEFAULT> but was: <REJECT_COMMANDS>」 | 前提断言有判别力 |
| **M2** | 排掉共享层那份自动配置（`spring.autoconfigure.exclude`）⇒ A 组回落 `DEFAULT` | **目标断言**：「必须是 INTERNAL ⇒ 实测 DEADLINE_EXCEEDED / 2028ms ⇒ expected: <INTERNAL> but was: <DEADLINE_EXCEEDED>」 | **目标断言有判别力** |

⇒ ⇒ **只红在前提**只证明前提有判别力 ⇒ ⇒ **必须另做一个变异让它红在目标断言上** ⇒ ⇒ 两条变异分别落在两条断言上 ⇒ 才算数。
⇒ ⇒ **M2 是独立于 B 组的第二条证据**：它用**另一条机制路径**（排掉自动配置）得到了**同一个结论**
（没有 `REJECT_COMMANDS` ⇒ 客户端 1015）⇒ ⇒ **交叉验证**。

### 50.3 🟢 **本轮顺带挖到的四条（都留档，因为都会重复发生）**

- **① `success` 恒为 `false` 不是漂移 —— 改成 `true` 反而会制造漂移**：
  Go 真源 `service/user/user/rpc/internal/logic/logoutlogic.go:43` = **`return &pb.LogoutResp{}, nil`**
  ⇒ ⇒ `success` **永远是 proto3 的默认值 `false`** ⇒ ⇒ RAG 侧 `LogoutResp.newBuilder().build()`（**不 setSuccess**）
  **与 Go 逐字同形** ⇒ ⇒ ⚠️ **本轮第一版断言按直觉写了 `success == true` ⇒ 实测红** ⇒ ⇒
  **现已把「`success` 是 false」钉成守卫**（附 Go 出处）⇒ ⇒ **谁把它改成 true，谁就制造了一条新漂移**。
- **② javadoc 里不能出现字面的「星号-斜杠」组合**：
  写 `{@code service/**/src/main/java}` ⇒ ⇒ 那个 `*/` **提前闭合了整份 javadoc** ⇒ ⇒
  后面 200 行全被当成代码 ⇒ ⇒ 编译器报了几十行「非法字符 / 需要 class」这种**看起来毫不相关**的错误
  ⇒ ⇒ 真因在几十行之前 ⇒ ⇒ 已改成不含该组合的写法。
- **③ 端口**：两个 rpc 的 yaml 里分别是 `49210`（user）与 `8081`（admin）⇒ ⇒ **本机 8081/8889/8082 有别人的进程**
  ⇒ ⇒ 新测试一律用**运行时挑的空闲口**（挑完即释放 ⇒ 被抢 ⇒ **上下文起不来 ⇒ 大声失败**，不静默）。
- **④ 隔离**：本轮两组各自用**自己的 Testcontainers 容器**（A 组与 B 组各一个）⇒ ⇒
  **「同场」的准确口径 = 同一 JVM 运行 ＋ 同一镜像配置 ＋ 唯一变量 = 断连策略**，
  **不是**「同一个容器」⇒ ⇒ **台账按前者写**（不按后者吹）⇒ ⇒ 与 §48.1-bis 的隔离维度对照表同源。

### 50.4 🟢 **新增落码清单**

| 项 | 数量 | 落点 |
|---|---|---|
| A 组（L4 正面证据） | 2 类（user 1 · admin 1） | `service/users/user/user-rpc/src/test/.../UserRpcLogoutClientVisibleGrpcStatusTest.java` · `service/users/admin/admin-rpc/src/test/.../AdminRpcLogoutClientVisibleGrpcStatusTest.java` |
| B 组（同场反例） | 2 类（user 1 · admin 1） | 同目录 `*DefaultBehaviorTest.java`（各 1 条） |
| 代理抽到共享 support 类 | 1 份 | `service/users/user/user-rpc/src/test/.../support/GoStyleRedisDisconnectProxy.java`（**user-rpc 专用** ⇒ admin-rpc 用它自己模块那份嵌套的） |
| 改动既有测试 | 1 处 | `UserRpcRedisDisconnectedFastFailTest` 改用共享代理（**行为零变化** ⇒ 已重跑确认仍绿 2/2） |
| **生产代码** | **0 行** | 本轮**零生产改动** ⇒ ⇒ 纯测试 + 纯台账 |

⚠️ **B 组不踩 G4 的源扫守门**：该守门扫的是各模块的 `src/main/java` 那一层
⇒ ⇒ **`src/test/java` 不在扫描面** ⇒ ⇒ 守门在我域仍绿（已实测，见 §49.1）。

### 50.5 🟢 **回归口径（2026-10-28 实测 · 域级两模块 + 依赖链）**

```
mvn -B -o -DargLine=-Xmx1g -am -pl service/users/user/user-rpc,service/users/admin/admin-rpc test
⇒ BUILD SUCCESS · 7:18 · mvn EXIT=0
```

| 模块 | 本轮之前 | 本轮 | 差 | 差在哪 |
|---|---|---|---|---|
| `user-rpc` | 150 | **152** | **+2** | 2 个新测试类各 1 条（A 组 + B 组） |
| `admin-rpc` | 120 | **122** | **+2** | 2 个新测试类各 1 条（A 组 + B 组） |
| `common`（共享层） | 331 | 331 | **0** | 未改共享层 |
| `users-common`（共享层） | 39 | 39 | **0** | 未改共享层 |
| **我域 6 模块合计** | **694** | **698** | **+4** | 4 个新类 × 1 条，**逐条对得上** |

⇒ ⇒ **零条旧用例被动**（+4 恰好吃掉 4 个新类的 1 条）⇒ ⇒ **「代理抽到 support 类」没有破坏既有用例**
⇒ ⇒ 且**既有 `UserRpcRedisDisconnectedFastFailTest`（2 条）与 `AdminRpcRedisDisconnectedFastFailTest`（2 条）重跑仍绿**。
⇒ ⇒ ⚠️ **本轮只跑了两个 rpc 模块 ＋ 其依赖链**（不是域级全量）⇒ ⇒ **域级全量（6 模块）留到波末 C7 收口回归**重跑
⇒ ⇒ 数字届时以那一次为准。

### 50.6 🔴 **波末收口的「负载预算」—— 我域新增了 4 条【重测试】（这是本轮唯一可能影响别人的一条）**

**起因**：G4 的 §4.10.59 记了它第 15 次全量的**首轮红**（`service/common` 的
`GoStyleRpcProtectionChainTest.sheddingRejectionIsVisibleToTheOuterBreaker`）⇒ ⇒ 根因是
**那轮测试的前置条件依赖机器速度**（非密闭）⇒ ⇒ 且它自己指出**当时 swap 已用 88%** ⇒ ⇒
**典型负载型 flaky** ⇒ ⇒ **负载是波末收口的真实风险**。

**我域这一轮做了什么**：新增 **4 条**测试（A 组 2 · B 组 2）⇒ ⇒ **每一条都起一个
【完整 Spring 上下文 + 2 个 Testcontainers 容器】**（PG 只为让完整应用上下文起得来 · Redis + FIN 代理）
⇒ ⇒ 单条实测 **15~35s** ⇒ ⇒ **波末收口那一轮，我域会额外拉起 4 套容器 + 4 个上下文**。

⇒ ⇒ **我不认为这会让收口变红**（理由：容器由 Testcontainers 收尾 `docker rm -f` · 上下文
`@DirtiesContext(AFTER_CLASS)` 不缓存 · 每条只跑 1 个用例 · 且**跑在模块自己的 reactor 里**），
⇒ ⇒ **但它确实会抬高峰值负载** ⇒ ⇒ 而 G4 刚刚被负载型 flaky 咬过 ⇒ ⇒
⇒ ⇒ **所以这条我【主动告知】了 G4，而不是等它踩了再说**（已发信，G4 队列 position 60）。

**已做的三处减负**：① `@DirtiesContext(classMode = AFTER_CLASS)`（上下文用完即弃，不留在缓存里）；
② 容器交给 Testcontainers 收尾（不留孤儿容器 ⇒ 不占端口段）；③ 每条测试**只跑 1 个用例**（不为凑数加用例）。

**⚠️ 我给 G4 的两个选项（等它回）**：
- **甲（我倾向）**：**保持现状**，但波末收口那轮**把全量拆两段**（先跑不含这 4 条的，再单独跑这 4 条）⇒ ⇒ 峰值负载不叠加 ⇒ 代价 = 收口流程多一步；
- **乙**：**改成更轻**（A/B 两组合并到同一个容器 ⇒ 顺带让「同场」口径更严）⇒ ⇒ 代价 = 改动 ＋ 重验。

⇒ ⇒ **纪律沉淀**：**「我新增了会拉高全局负载的东西」本身是一条必须主动广播的事件**
⇒ ⇒ 与「我改了共享面 ⇒ 四棵树同时过期」同族（§4.10.40 方法论 ③）⇒ ⇒
⇒ ⇒ **不是「我这边绿了就行」** ⇒ ⇒ **是会影响到别人那一条，就必须说**。

---

## 51. 🟡 **通信对象与共享面落点的澄清**（2026-10-28 · G1 lane · 一次说清，后面不再重复）

### 51.1 🔴 「那个协调会话」**不是波次整合者** ⇒ 我**不得**再向它发信

那个会话（`mvs_d4fbe94ca81b40d593f75d9c9c157c31` · 标题「排查 agent configuration is missing 报错」）
**自己**发了角色边界通告，三条要点：

1. **它是【临时故障处置会话】**，**不参与 G1~G4 的任何任务** ⇒ 只负责四类**运行时故障**的解封：
   ① `agent configuration agent.md: Agent configuration is missing.`(50113)
   ② 队列被 `turn-final-failure` 暂停
   ③ app 重启把在跑回合 abort 成 `aborted`
   ④ 跨 lane `session send` 把对方压进暂停态
   ⇒ ⇒ **域内落码 / 台账 / 跨 lane 协调 / 共享面内容 / 合并收口 ⇒ 一律不归它**。
2. **通信单向**：它 → 我可以（故障通报、只读运行时事实）；**我 → 它不可以** ⇒
   ⇒ **我本轮（通告到达前）发的那封增量汇报已送达，不追发**；**此后不再向它发任何内容**。
3. ⇒ ⇒ **教训（值得沉淀）**：**「看起来像协调者」不等于「是协调者」** ⇒
   ⇒ **发信前先确认对方的角色**，否则会把内容发给一个**不会接单、也不会转手**的会话
   ⇒ ⇒ 与「派单前先问落点归谁」同源：**先确认接收方是谁，再决定发不发**。

> 🔴🟢 **订正注记（2026-10-28 09:0x · 故障处置会话**主动**发来消息 ⇒ 上条第 2 点被证伪 · 保留原文不改写）**
>
> - **它 → 我**这条通道**是通的**：本节写完约 2 小时后，**它主动**发来一条含三条回执的消息
>   （`from_session_id=mvs_d4fbe94ca81b40d593f75d9c9c157c31`）⇒ ⇒ ⇒ **「通信单向」是错的** ⇒ ⇒ **第 2 点里「我 → 它不可以」没有事实依据**。
> - 🔴 **但「第 2 点的结论」仍然成立** —— **理由要换**：不是「发不出去」，而是**「它自己声明不接这些活」**
>   （它原话：域内落码 / 台账 / 跨 lane 协调 / 共享面内容 / 合并收口 **一律不归它**；共享层反馈单继续走 G4）
>   ⇒ ⇒ ⇒ **共享面事项的落点仍是「登记 / 合并收口那一步」**（§51.2 不变）⇒ ⇒ **「不发给它」这个动作不必改，但「因为发不出去」这个理由必须改**。
> - ⇒ ⇒ **教训 ①**：**「通道不通」与「对方不接单」是两件事** ⇒ ⇒ 我当时只观察到「我发的那封没有回音」⇒ ⇒ 就推断成「单向」
>   ⇒ ⇒ **「没有回音」这个证据同时兼容「发不出去」与「对方不接单」两种解释** ⇒ ⇒ **不能只凭一条证据就断定通道方向**。
> - ⇒ ⇒ **教训 ②（更刺眼的一档）**：**同一个错 ID 在我的台账里「复活」了一次** —— §46 那处**已经**写对过
>   （「我记的 ID 少了一个 `c` ⇒ ⇒ **404 是我的抄写错误，不是会话没了**」），但**我写 §51.1 时是从上下文摘要抄的，不是回原文件核的**
>   ⇒ ⇒ **抄写错误会复活** ⇒ ⇒ **凡写坐标/ID ⇒ 必须回源文件或 runtime 实测**（本轮 ID 以 `session list` 实测为准：`mvs_d4fbe94ca81b40d593f75d9c9c157c31` · 34 位 hex）。

### 51.2 🟡 共享面那几项的落点：**不是「发信给某人」，是「合并收口那一步」**

`check-wave-lane-ownership.py` 的注释早已写过「**『整合者』不是一个存在的角色** ⇒ 它诱导 lane 去向上升级找一个不存在的人
⇒ 改用可指向的『合并收口执行者』」⇒ ⇒ **且「合并收口执行者」是【步骤】，不是常驻会话**（`docs/Worktree.md`）。

⇒ ⇒ **所以我之前登记的「共享面待合并收口」几项，正确处置不是「再发一封信」**（**没有常驻会话可发**）⇒ 而是：

| 共享面待办 | 正确处置 |
|---|---|
| `docs/DRIFT/_cross/cross-domain-coupling.md` 快照 | **四 lane 全合后，在合并树上重算**（`check-wave-coupling.py --print`）⇒ 由执行收口那一步的人写 |
| `docs/DRIFT/_cross/domain-common-lanes.md:133` · `docs/DRIFT/global/_README.md:328` 的过期行号（`HttpMiddleware.java:692 → 728`） | 同上 ⇒ **收口那一步统一重扫** |
| `DRIFT-USERRPC-V-002` 销号签字（`docs/Intentional_Deviations/**` ⇒ 不在任何 lane 白名单） | 同上 ⇒ **收口那一步落签** |
| `docs/Pitfalls/**` 整族无主 | 同上 ⇒ **收口那一步补写**（我这次的 RANDOM_PORT/Nacos 坑只能先落在域台账 §14.6） |
| 🔴 **`docs/DRIFT/global/lanes/L7-slowgate.md` 的 9 处「整合者」**（2026-10-28 补）| G4 白名单**只有** `docs/DRIFT/global/vs-common.md` ⇒ ⇒ `global/lanes/**` **不在任何人白名单** ⇒ ⇒ **写权孤儿** ⇒ ⇒ 同上 ⇒ **收口那一步统一改**（与 §47.5 订正注记同源）|

⇒ ⇒ **纪律沉淀**：**「登记」不等于「要发信」** ⇒ ⇒ **先问「这件事的落点是一个【动作】还是一个【人】」**
⇒ ⇒ 若是【动作】（收口时重算 / 重扫 / 落签）⇒ **登记即可，不必发信、也不必等回执**。

### 51.3 📌 参考事实（**只读 · 供收口时对照**）：四棵树的 `common_radiation` 实况

| 树 | `common_radiation` |
|---|---|
| 主干 | **37**（守门绿） |
| **G1（本 lane）** | **38** |
| G2 | 43 |
| G3 | 37 |
| G4 | 37 |

⇒ 合并投影 = **44 条**（新增 7 键、3 键辐射域扩大、0 删除）⇒
⇒ ⚠️ **权威值只能从【合并树】重算**（`check-wave-coupling.py --print`）⇒ **单树数字不可直接引用**。

### 51.4 🟢 假绿订正的判据（本轮我提的、对方也采纳的）

**「快照里搜不到该类名」≠「红项消失」** ⇒ ⇒
⇒ 守门对 `set(期望) | set(现状)` 求差 ⇒ **快照多出的键同样算漂移** ⇒
⇒ ⇒ **只往【当前主干】补一行 ⇒ 会让主干守门转红** ⇒ ⇒
⇒ ⇒ **那一行的落点只能是「合并收口那一次提交」，由合并收口执行者写** —— **不是 lane 写，也不是故障处置会话写**。
⇒ ⇒ **同族**：与「跑绿 ≠ 判据活着」「零命中是假信号」同源 —— 都是**「你以为的那个量 ≠ 判据实际度量的量」**。

---

## 52. 🔴🟢 **G4 的 R1/R3/R4 三条回执逐条复核 —— 四条全部「在我树已闭合」＋ 两次订正**（2026-10-28 · G1 lane）

### 52.0 ⚠️ 先说时基：**那封信的 HEAD 是 `d4e4745`（2026-10-27 晚）**，而我树已到 `1950157`

⇒ ⇒ 它表里 R1/R3/R4 的「事实」列**逐条核下来都已不成立** ⇒ ⇒ **不是它算错，是它的快照旧了**
⇒ ⇒ 这正是「**回执也要对时**」的又一次实例（**同一路径 ≠ 同一版本**）⇒ ⇒ **我核的是当前树，不是它的表**。

### 52.1 ✅ R1 / R2 / R3 / R4 —— **四条全部已闭合**（2026-10-27 复核补记那轮就做完了）

| # | G4 表里的「事实」（旧快照） | **我当前树（`1950157`）实测** | 守门 |
|---|---|---|---|
| **R1** | user-api「无 `connect-timeout` ⇒ 连接 = 10s」 | ✅ **`timeout: 3s` + `connect-timeout: 5s`**（`user-api/src/main/resources/application.yml:25` · `:34`） | `UserApiRedisTimeoutConfigTest`（2 条）+ `UserApiRedisConnectTimeoutSocketTest` |
| **R2** | user-rpc 同上 | ✅ **`timeout: 3s` + `connect-timeout: 5s`**（`user-rpc/.../application.yaml:55` · `:67`） | `UserRpcRedisTimeoutConfigTest` + `UserRpcRedisConnectTimeoutSocketTest` |
| **R3** | follow-api「`timeout: **2s**`（≠ Go 3s）＋ 无 `connect-timeout`」⇒ **要求我判定「刻意收紧」还是「抄错」** | ✅ **已判定并已修**：`timeout: 3s`（`:40`）+ `connect-timeout: 5s`（`:47`）⇒ 注释原文留痕：「⚠️ **本行原为 2s**（无出处、无人审过）⇒ 与 Go 的 3s 不一致 ⇒ 2026-10-27 对齐成 3s」⇒ ⇒ **不是刻意收紧，是抄错**（已对齐，**不是**签字偏离） | `FollowApiRedisTimeoutConfigTest` + `FollowApiRedisConnectTimeoutSocketTest` |
| **R4** | admin-rpc「值对但注释的机制写错」 | ✅ **值与注释均已修**：`timeout: 3s`（`:61`）+ `connect-timeout: 5s`（`:71`）＋ 注释已写明 go-redis `DialTimeout` 出处 | `AdminRedisTimeoutConfigTest` + `AdminRedisConnectTimeoutSocketTest` |

⇒ ⇒ **我域 5 个用 Redis 的模块（user-api / user-rpc / admin-api / admin-rpc / follow-api）全部是 `3s` + `5s`**
⇒ ⇒ 且 **5 个都有「配置层 + socket 层」两层守门**（各 2 条 + 2 条 ⇒ **10 条**）⇒ ⇒ **不是「配了就算」，是「真在 socket 层生效」**。

**Go 侧出处（逐条到消费点，本轮我**自己重核**过，不采信转述）**：
- `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-39` ⇒ `red.NewClient(&red.Options{Addr:33, Username:34, Password:35, DB:36, MaxRetries:37, MinIdleConns:38, TLSConfig:39})`
  ⇒ ⇒ **三个 timeout 一个都没传** ⇒ ⇒ 全部落到 **go-redis 的库默认值**。
- ⚠️ **go-redis 版本**：Sea 仓 `go.mod:15` **直接 pin `github.com/redis/go-redis/v9 v9.20.0`**
  （⚠️ **不是** go-zero v1.10.2 自己 go.mod 里写的 v9.19.0 ⇒ ⇒ 以 Sea 仓的 pin 为准）。
- `go-redis@v9.20.0/options.go`：`DialTimeout = 5s`（**`:338-339`**）· `ReadTimeout = 3s`（**`:364-370`** 的 `switch opt.ReadTimeout { case 0: … }`）· `WriteTimeout = ReadTimeout`（**`:374-378`**）。
⇒ ⇒ **Go 侧真值 = 读命令 3s / 连接 5s** ⇒ ⇒ 与 RAG 侧 5 个模块**逐字相同** ✔

### 52.2 🔴 暴露面数字：**G4 的订正成立**，但**当前树已经是新数** ⇒ 两边都得更新

- **G4 订正的是对的**：我此前报 `user-rpc 11 / admin-rpc 13 / follow-rpc 5` ⇒ **那是「含 javadoc 提及」的数**；
  按真注解（`grep -rE "^\s*@SpringBootTest"`）在同一快照上 = **9 / 12 / 3** ⇒ **它订正得对**。
- ⚠️ **但它给的数是旧快照的**：我此后**新增了 4 个带 `@SpringBootTest` 的类**（2 个 user-rpc ＋ 2 个 admin-rpc 的 logout 客户端可见状态测试）
  ⇒ ⇒ **当前树的真注解数 = `13 / 15 / 3`** ⇒ ⇒ **不能直接用它那组数写台账**。

**逐 ref 实测（`git grep -lE "^[[:space:]]*@SpringBootTest" <ref> -- <module>/src/test/java`）**：

| ref | user-rpc | admin-rpc | follow-rpc | 说明 |
|---|---|---|---|---|
| `a282012` / `9037a2b` / `d4e4745` / `7488b3d` / `3b573fb` | **9** | **12** | **3** | ⇒ **G4 的订正数就落在这段**（我加 4 个类**之前**） |
| `b8b9400` / `70219d4` / `1950157` | **13** | **15** | **3** | ⇒ **当前树**（+2 / +2 / +0） |

⇒ ⇒ **方向不变**：三个域仍是「多 Spring 上下文形态」⇒ **暴露面成立** ✔
⇒ ⇒ ⚠️ **教训**：**订正也会过期** ⇒ ⇒ **凡引「某棵树的计数」，必须同时钉住那棵树的 ref** ⇒ ⇒
⇒ ⇒ **「回执也要对时」的加强版：回执里被订正的数，也会被后续提交改掉** ⇒ ⇒ **回信时要把新数一起给**。

### 52.3 🔴 **第三方源码的行号必须连版本号一起钉** —— 本轮新发现的一类「过期行号」

- **机制**：「共享层文件被挪长 ⇒ 引用集体过期」我已记录（§52 之前的 memory 条目）；
  ⚠️ **但这次的对象是【外部依赖】** ⇒ **版本一升，行号即失效** ⇒ ⇒ 而**行号失效后内容可能完全没错**（还是那个默认值）⇒ ⇒ **没人会发现**。
- **实证（RAG 仓 G1 · admin-rpc 的 yml 注释）**：原文写
  `github.com/redis/go-redis/v9 options.go 的 init() 填库默认值：DialTimeout = 5s（:329-330）`
  ⇒ ⇒ **`:329-330` 是 v9.18.0 的行号**（我本机 `go-redis@v9.18.0/options.go` 正是 329-330）
  ⇒ ⇒ **真源实际用 v9.20.0**（Sea 仓 `go.mod:15` 直接 pin）⇒ ⇒ **实际是 `:338-339`** ⇒ ⇒ **引用已失效**。
- **修法（零行为变化 · 5 处）**：把我域 5 个 yml 的出处注释**连版本号一起钉**，
  并把行号改成 **v9.20.0** 的真实行号（`:338-339` / `:364-370` / `:374-378`）⇒
  ⇒ **变更面已核：只改注释行，零配置行变更**（`git diff` 过滤非注释行 = **空**）⇒
  ⇒ **5 个锁值守门复跑 10 条全绿**（`UserApi/UserRpc/AdminApi/Admin/FollowApi` `RedisTimeoutConfigTest`）。
- ⚠️ **为什么这条值得做**：admin-rpc 注释自己写着「这是一致但**无回归网**的巧合：go-zero 升 go-redis、默认值一变即静默分叉」
  ⇒ ⇒ 现有的 `*RedisTimeoutConfigTest` **锁的是 RAG 侧的值**，**锁不住 Go 侧来源变了**
  ⇒ ⇒ **把「版本号 + 行号」写进注释，是让这条风险【可被人一眼核对】的最低成本做法**（机器网需读 Go module cache，超出域内范围 ⇒ 如实登记）。
- ⇒ ⇒ **同族**：与「跨树引用要对齐 commit」「行号是快照不是锚点」同源 ⇒ ⇒ **本条补的是「第三方依赖的版本也是一个快照」**。

### 52.4 🟢 合并顺序约束：**我树已满足**

- G4 的约束：`3b573fb`（HTTP 池拆进 `HttpPoolLifecycle`）与 `7488b3d` **必须同批合入**
  ⇒ ⇒ 单合 `3b573fb` ⇒ 池「终止即永久」⇒ 同模块后续 Spring 上下文被毒化 ⇒ ⇒ 绿变红。
- **我树实测**：`git merge-base --is-ancestor` ⇒ **`3b573fb` 在位** · **`7488b3d` 在位** ⇒ ⇒ **我树已满足该约束** ✔
  ⇒ ⇒ **该约束只对「合并收口执行者逐笔 cherry-pick」与「未合的 lane 分支」有效** ⇒ ⇒ 我这边**无待办**。

---

## 53. 🔴🟢 **G3 第九次来信的 ②③④ 三条 —— 如实答复 ＋ api 侧 B 组落码**（2026-10-28 · G1 lane）

> **时基**：本节所引 G3 来信的内容以**我实测为准**（不采信转述）；G3 会话 `mvs_ba99d8078fd94de681ce8068c21ee76a` 在本轮**仍在跑**（`updatedAt` ≈ 2026-10-28 08:08）。
> ⚠️ **另记一条协作事实（会影响后面所有 lane 的通信判断）**：**G2 会话 `mvs_5bc5f1b230ca43649d78c48e20b16d35` 也已停在 `Token Plan 42212`**（`updatedAt` ≈ 2026-10-28 06:47 ⇒ 此后无动作）
> ⇒ ⇒ **G2 / G4 现在都是「事实上停住」** ⇒ ⇒ **发过去的消息是死信** ⇒ ⇒ **登记为主、等待回执为次**（与 §51.2 同一改判口径）。
>
> 🟡 **订正注记（2026-10-28 08:41 · 同日实测，保留上句原文不改写）**：**G2 恢复了** —— `session get` 的 `status` 变成
> `type=error` / `message="Cron definition was deleted before execution"` / `errorCode 50113`（**不是** Token Plan 42212 了）
> ⇒ ⇒ 且我发给它的信 **返回 `mode=started`（真起了一个新 Turn）** ⇒ ⇒ **它能收信、也在处理** ⇒ ⇒ **上句「G2 / G4 都停住」对 G2 已不成立**（对 G4 仍成立）。
> ⇒ ⇒ **教训**：**「会话状态」是逐时点的快照，不是恒定性** ⇒ ⇒ **判「能不能送达」要看【发信那一刻的返回】**（`mode=started` = 已在跑 · `mode=queued` = 排队待跑）
> ⇒ ⇒ **不能拿半小时前的一次 `session get` 当结论** —— 这与「回执也要对时」同源，但**方向相反**：这次是**状态会变好**。

### 53.1 🔴 **④ 如实答：我域那 5 份 Redis 断连守门【确实只有 A 组】**

| # | 守门 | 模块 | 有无 B 组 | 机器证据 |
|---|---|---|---|---|
| 1 | `UserApiRedisDisconnectedFastFailTest` | user-api | ❌ 无 | `DisconnectedBehavior.DEFAULT` 提及 **0** 次 · `@TestConfiguration` **0** 个 · `@Test` **2** 个 |
| 2 | `AdminApiRedisDisconnectedFastFailTest` | admin-api | ❌ 无 | 同上 |
| 3 | `FollowApiRedisDisconnectedFastFailTest` | follow-api | ❌ 无 | 同上 |
| 4 | `UserRpcRedisDisconnectedFastFailTest` | user-rpc | ❌ 无 | 同上 |
| 5 | `AdminRpcRedisDisconnectedFastFailTest` | admin-rpc | ❌ 无 | 同上 |

⇒ ⇒ **按 G3 的标准（无对照组 ⇒ 最多 L3），那 5 份本身【最多 L3】** ⇒ ⇒ **本条如实登记，不粉饰**。

**但是** —— 「**这条机制轴上有没有 L4**」与「**这 5 份守门本身是不是 L4**」是**两件事**，必须分开说：
⇒ ⇒ 同一条 `disconnectedBehavior` 轴上，我域**另有** A/B 对（rpc 侧 · 观测点是「**客户端可见的 gRPC status**」）：
| A 组 | B 组 | 观测点 |
|---|---|---|
| `UserRpcLogoutClientVisibleGrpcStatusTest` | `UserRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest` | 客户端可见 status：**INTERNAL**（A）vs **1015 DEADLINE_EXCEEDED**（B）|
| `AdminRpcLogoutClientVisibleGrpcStatusTest` | `AdminRpcLogoutClientVisibleGrpcStatusDefaultBehaviorTest` | 同上 |
⇒ ⇒ **轴上已有 L4**（§50 已记）⇒ ⇒ **但 api 侧当时确实只有 A 组** ⇒ ⇒ ⇒ **本轮把 api 侧补上（§53.2）**。

### 53.2 🟢 **api 侧补 3 份 B 组（零生产代码 · 零重构）⇒ api 侧这条轴也升 L4**

- **落码（3 份新文件 · 各 2 条用例 = 前提腿 1 + 目标腿 1 ⇒ 共 6 条）**

  | 文件 | 模块 |
  |---|---|
  | `service/users/user/user-api/src/test/java/com/rag/users/user/api/UserApiRedisDisconnectedDefaultBehaviorTest.java` | user-api |
  | `service/users/admin/admin-api/src/test/java/com/rag/users/admin/api/AdminApiRedisDisconnectedDefaultBehaviorTest.java` | admin-api |
  | `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiRedisDisconnectedDefaultBehaviorTest.java` | follow-api |

- **「唯一变量 = 断连策略」怎么做到**：共享层 `GoStyleRedisClientAutoConfiguration` 的 bean 带 `@ConditionalOnMissingBean`
  ⇒ ⇒ **test 侧**提供一份同类型 bean（`@TestConfiguration`）把它**遮蔽**掉 ⇒ ⇒
  ⇒ **域内零落码**（不碰共享层、不碰域内生产代码）⇒ ⇒ 这就是「A/B 受控对照」能做到「唯一变量」的原因。
- **故障注入必须是【同一份实现】**：本组直接复用 A 组那个 **package-private 嵌套类** `GracefulDisconnectProxy`
  ⇒ ⇒ **同一份字节码**（不是「照抄一份」）⇒ ⇒ 故障形态必然同款。
  ⚠️ **但必须是【独立实例】**：A 组的 `PROXY` 是 `static final` ⇒ 两个类共用它 ⇒ A 组先断连后 B 组拿到的代理已经断了
  ⇒ ⇒ **本组自己建一份**（同一个类、不同的实例、上游指向**自己的** Redis 容器）⇒ ⇒ 两组互不污染。
- **实测（2026-10-28 · 真 Redis 容器 ＋ 真 Lettuce ＋ 真 FIN 代理 · 非 mock）**

  | 模块 | A 组（`REJECT_COMMANDS`）| B 组（`DEFAULT`）|
  |---|---|---|
  | user-api | **1ms**（快速失败 · 预算 1500ms）| **3013ms**（排队到 `commandTimeout`）|
  | admin-api | **1ms** | **3013ms** |
  | follow-api | **1ms** | **3004ms** |

  ⇒ ⇒ **同一个故障形态、同一份代理实现、同一个测点，**只**换断连策略 ⇒ 结果从「**1ms**」变成「**~3000ms**」
  ⇒ ⇒ **归因成立**（不是「当前配置下恰好是这样」）⇒ ⇒ **api 侧这条轴升 L4**。
  ⚠️ **两组是两次独立跑批的实测值**（同日 · 同镜像 `redis:7-alpine` · 同 FIN 代理实现 · 同测点 `hasKey`）⇒ ⇒ **可比**。
- **变异验证（6 条各红各的）**：**删掉遮蔽 bean** ⇒ 共享层的 `REJECT_COMMANDS` 接管 ⇒
  - **前提腿**红：`断连行为必须是 DEFAULT ⇒ 实测 REJECT_COMMANDS`
  - **目标腿**红：**`实测 1ms`** ⇒ 红在**耗时断言**上（不是别处）
  ⇒ ⇒ 3 个模块 × 2 条 = **6 条全红** ⇒ ⇒ 回滚后 **3 个文件 sha256 与基线逐字节一致** ✔

### 53.3 🟢 **踩出来的一条方法学：前提断言与目标断言【必须落在不同的用例】上**

- **第一版把两条断言写在**同一个方法**里** ⇒ ⇒ 变异（删掉遮蔽 bean）时**前提断言先红**
  ⇒ ⇒ **目标断言根本没跑到** ⇒ ⇒ ⇒ **拿这个结果只能说「前提断言是活的」，不能说「目标断言是活的」**
  ⇒ ⇒ ⇒ **同一个方法里堆两条断言 ⇒ 变异只能证明第一条**。
- **拆开之后**：前提腿只读配置层（customizer 数量 / 名字 / 断连行为 / `commandTimeout` 锚点）⇒ **不制造断连**；
  目标腿**第一条断言就是耗时断言** ⇒ ⇒ 两条腿互不依赖 ⇒ ⇒ 一次变异 ⇒ ⇒ **两条腿各自被证伪一次**。
- ⇒ ⇒ **同族纪律**：与「前提断言与目标断言要分开做变异」同源，但这里补的是
  ⇒ ⇒ **「分开」必须落在【不同的用例】上，不能只写在同一条用例的不同断言行上」**。

### 53.4 🟢 **② go-zero 族 `min-idle: 8` —— 规则成立，而且我【独立验证了它确实吃重】**

- **G3 的规则**：go-zero 族（`redisclientmanager.go:15` `idleConns = 8`）⇒ 若 RAG 侧落 Lettuce 池 ⇒ `min-idle` 必须是 **8**，不是 0。
- **我独立复核（不采信转述）**：go-zero v1.10.2 `core/stores/redis/redisclientmanager.go:15` `idleConns = 8`
  ⇒ ⇒ `:32-39` 建 `red.Options` 时**传了 `MinIdleConns: idleConns`**、**没传 `PoolSize`**。
- 🔴 **关键的一步（这条规则能不能吃重，全看这一步）**：go-redis **v9.20.0** `options.go:350-351`
  ```go
  if opt.PoolSize == 0 {
      opt.PoolSize = 10 * runtime.GOMAXPROCS(0)
  }
  ```
  ⇒ ⇒ **池化是默认开启的**（`PoolSize` 不会停在 0）⇒ ⇒ **`MinIdleConns: 8` 不是空转**（池会真的去维持 8 条空闲连接）
  ⇒ ⇒ **规则成立且有牙齿**。
  ⇒ ⇒ ⚠️ **若下游那个默认不存在，`MinIdleConns` 就是死参数 ⇒ 这条规则会退化成「照抄 go-zero 的字面量」**
  ⇒ ⇒ ⇒ **所以这条规则必须连「下游默认值」一起核**（本条已核）。
- **我域现状（2026-10-28 实测）**：**未落池** ⇒ ⇒ **无待办**。
  - `grep -rn "commons-pool2" service/users service/follow --include=pom.xml` ⇒ **空**
  - `grep -rn -A4 "pool:" service/users service/follow --include=*.yml` ⇒ **空**
  ⇒ ⇒ **我域 6 个模块全部是「直连」形态**（Lettuce 单连接）⇒ ⇒ **当前 `min-idle` 无从谈起**。
- 🟡 **将来触发条件（写进台账，免得将来漏掉）**：若 `follow-api`（**go-zero 族**）因任何原因落池
  ⇒ ⇒ `min-idle` **必须 8**（**不是** go-redis 直连族的 0）。
  ⇒ ⇒ 同一时刻的三条必配项：**①** `max-wait: 4s`（Go `PoolTimeout = ReadTimeout + 1s`；不配 ⇒ commons-pool2 `DEFAULT_MAX_WAIT = -1` ⇒ **无限等**）
  · **②** **`commons-pool2` 必须域内 pom 自己声明**（Boot 4 的 starter 不再传递 ⇒ 缺它 ⇒ 启动期 `NoClassDefFoundError`）
  · **③** `max-active` / `max-idle` 是「表达层更严」（性能面，**不影响命令结果**）。
- ⇒ ⇒ **本条在 G3 的域（`like-rpc` / `comment-rpc` 已在用 `commons-pool2`）才是活坑**；我域是「**已核 · 当前无实例**」。

---

## 54. 🟢 **域级条数重测（双路径交叉核对）＋ 增量归因**（2026-10-28 · G1 lane）

### 54.1 🟢 **口径：两条独立路径 · 逐模块比对**

- **命令**：`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test`
- **结果**：**18/18 BUILD SUCCESS · mvn EXIT=0 · 总耗时 47:10 · 0 failures / 0 errors / 0 skipped**
- **路径 A** = maven **模块级** `Results:` 汇总行
  ⚠️ **过滤条件必须是行尾无 ` -- in `（双横线）** ⇒ 用单横线 `- in ` 会把「**每类用例行**」当成模块级汇总行
  ⇒ ⇒ 得**恰好 2 倍**的数 ⇒ ⇒ **一个「看起来对、其实重复计数」的数**。
- **路径 B** = 逐模块 `target/surefire-reports/TEST-*.xml` 的 `tests` 属性求和
- **交叉核对结果**：**9/9 模块两条路径逐字相等**

| 模块 | 路径 A | 路径 B |
|---|---|---|
| `user-api` | 127 | 127 |
| `user-rpc` | 152 | 152 |
| `admin-api` | 149 | 149 |
| `admin-rpc` | 122 | 122 |
| `follow-api` | 74 | 74 |
| `follow-rpc` | 83 | 83 |
| **★ 我域 6 模块合计** | **707** | **707** |
| `users-common`（依赖闭包带进来）| 39 | 39 |
| `common`（依赖闭包带进来）| 333 | 333 |
| `follow-common`（依赖闭包带进来）| 37 | 37 |
| reactor 9 模块合计 | 1116 | 1116 |

- **归因核对**：`409`（非目标）+ `707`（目标）= `1116` = reactor 合计 ⇒ **逐字相等**
  ⇒ ⇒ 差额**逐条可指名**（不是「多出来的数不知道从哪来」）。
- ⚠️ **`-am` 会把依赖闭包一起带进 reactor** ⇒ ⇒ **「reactor 全量」≠「我这次的收口口径」**
  ⇒ ⇒ 本轮 reactor **18** 个模块，我域目标只有 **6** 个 ⇒ ⇒ **聚合前必须把目标清单写死**，并把非目标单列。
- **整倍数自检**：`1116 ÷ 707` **不整除** ⇒ ⇒ **排除「重复计数」**。
- **证据强度**：**L3**（本次回归实测 · 非双侧活体）· **核对日期 2026-10-28**。

### 54.2 🟢 **增量归因（688 → 707 = +19，逐条可指名）**

- **基线** = `13f6a93`（2026-10-28 00:19 那轮「我域 **688** 条」的实测提交）
  ⇒ ⇒ 逐模块 `@Test` 增量（**代理口径**；权威条数以 XML 为准）：

| 模块 | Δ | 出处（**可指名**）|
|---|---|---|
| `user-api` | **+2** | 本轮 `UserApiRedisDisconnectedDefaultBehaviorTest`（B 组 2 条）|
| `user-rpc` | **+4** | `UserRpcLogoutClientVisibleGrpcStatusTest`（A）＋ `...DefaultBehaviorTest`（B）＋ `UserRpcRedisDisconnectedFastFailTest` 改动 ＋ `UserRpcSharedLayerArtifactFreshnessGateTest`（artifact 新鲜度守门）|
| `admin-api` | **+5** | `AdminApiRealTomcatResponseBytesTest`（真 Tomcat 字节级探针 **3** 条 · 提交 `70219d4`）＋ 本轮 `AdminApiRedisDisconnectedDefaultBehaviorTest`（B 组 2 条）|
| `admin-rpc` | **+4** | 同 `user-rpc` 那 4 笔 |
| `follow-api` | **+2** | 本轮 `FollowApiRedisDisconnectedDefaultBehaviorTest`（B 组 2 条）|
| `follow-rpc` | **+2** | `FollowRpcSharedLayerArtifactFreshnessGateTest`（artifact 新鲜度守门）|
| **合计** | **+19** | ⇒ ⇒ **与 `707 − 688 = 19` 逐字相等 ✔** ⇒ ⇒ **没有「不知道从哪来」的余量** |

- **共享层（非目标）**：`common` **+2**（G4 写权面）· `users-common` **±0** · `follow-common` **±0**
  ⇒ ⇒ **共享层本轮又动了 2 条** ⇒ ⇒ **凡引过 `common` 旧值（327 / 330 / 331）的台账与消息 ⇒ 按 §54.3 以最新值 `333` 为准**。

### 54.3 🟡 **旧值统一口径（历史不改写 · 只加「以本节为准」的指针）**

- **本轮实测最新值**（2026-10-28 · 47:10 那次回归）：`service/common` = **333** · `service/users/users-common` = **39** · `service/follow/follow-common` = **37**。
- **本节之前各节里写的 `327` / `330` / `331` / `33` —— 一律保留原文**（那是对**当时那一轮**的如实记录 ⇒ 改写它们等于篡改历史）
  ⇒ ⇒ **但凡引用「共享层条数」⇒ 一律以本节为准，并带「核对日期 + 那次回归的 ref」**。
  ⇒ ⇒ 受影响的旧节（本域写权面）：`users/vs-common.md` 的 §47.7 / §48.4 / §52 等处 · `follow/vs-common.md` 的 §11.15 / §11.16 等处。
- ⇒ ⇒ **同族纪律**：「**共享层每轮都在长 ⇒ 任何跨 lane 引用的条数都是【快照】**」⇒ ⇒ 与「行号是快照不是锚点」同型 ⇒ ⇒ **引用条数必须带日期**。
### 54.4 🟢 **本轮发出的两封信（登记 · 非派单）**

| 对象 | 会话 | 送达判据（**看返回，不看队列行数**）| 性质 |
|---|---|---|---|
| G3 | `mvs_ba99d8078fd94de681ce8068c21ee76a` | `mode=queued` · `position=10` · `ahead=9`（**当时忙** ⇒ 排在它当前 Turn 之后）| 答复 ④ ＋ 登记 ②③ ＋ 通报新落码与域级 707 |
| G2 | `mvs_5bc5f1b230ca43649d78c48e20b16d35` | `mode=started`（**真起了一个新 Turn** ⇒ 已在处理）| 条数变更 ＋ 两个口径细节 ＋ B 组手法通报 |

⇒ ⇒ **两封都「已发出」，但都不是「已收到」** ⇒ ⇒ **后续若要用它们的回执做决策 ⇒ 必须先去目标侧 transcript 里确认有 `cross-session-message` 块**（§51.2 同一纪律）。
⇒ ⇒ **G4 那边本轮没发**：会话 `mvs_e8063ac67c654150ab7797b8116ef0a1` 仍是 `error` / `Token Plan 42212`（`updatedAt` ≈ 2026-10-28 06:27）
⇒ ⇒ ⇒ **发过去也是死信** ⇒ ⇒ 与 §51.2 同一改判：**涉及共享层的待办一律「域侧先自修 + 共享面归合并收口那一步」**。

### 53.5 🟡 **③ 守门变异坑（`GenericObjectPoolConfig` import ⇒ 只得编译失败）—— 我域【当前无实例】，登记为将来纪律**

- **G3 的坑**：守门若直接 `import org.apache.commons.pool2.impl.GenericObjectPoolConfig`
  ⇒ ⇒ 「抽掉 pom 依赖」这个变异只会得到 **test-compile 失败**，**不是运行时证据**
  ⇒ ⇒ 要验「抽依赖 ⇒ 启动期硬失败」，必须**把守门挪走** ＋ 跑**真 Spring 上下文**的守门。
- **我域实测**：`grep -rn "GenericObjectPoolConfig\|commons.pool2" service/users service/follow --include=*.java --include=pom.xml` ⇒ **空**
  ⇒ ⇒ **我域当前没有这个形态的守门** ⇒ ⇒ **本条在我域无实例、无待办**。
- 🟡 **登记的用途**：① 若我域将来落池（§53.4 那条）⇒ **新写的池守门不许直接 import `GenericObjectPoolConfig`**
  ⇒ ② 变异要按 G3 的配方做（挪走守门 ＋ 真上下文）。

### 53.6 🟢 **本轮顺手订正的两处台账坐标（零结论变化）**

| # | 位置 | 原写 | 订正为 | 依据 |
|---|---|---|---|---|
| ① | `docs/DRIFT/users/common.md`（2 处）| `GoStyleRequest.java:59` | `normalize` 在 **`:52`** · `target.assertGoRequiredFields()` 在 **`:54`** | 本树 `grep -n` 实测 ＋ G4 `global/vs-common.md:2455` 的同款订正 |
| ② | `docs/DRIFT/users/user/api.md` 的「DRIFT 编号 → 出处」映射表 | 7 处坐标失准 | 逐条重核（脚本 `grep -n` 逐文件核）| 见该表下方的订正块 |

⇒ ⇒ ② 的根因值得单独记一笔：**不是「某个行号漂了」，是「**哪个编号对应哪个文件**」这张映射表串了**
⇒ ⇒ `DRIFT-009`↔`DRIFT-010` 在 `BearerTokenExtractor` 上串、`DRIFT-009`↔`DRIFT-016` 在 `GoStyleRequest` 上串
⇒ ⇒ ⚠️ **放大器 = 「`DRIFT-010`（同上）」这种省略写法** ⇒ ⇒ **凡写「同上」⇒ 必须逐条展开**（省略掉的恰好是核对时的抓手）。

---

## 55. 🔴🟢 **故障处置会话的来信（三条）—— 逐条独立复核：一条成立、两条与我的实测不符**（2026-10-28 09:0x · G1 lane）

> **对方**：`mvs_d4fbe94ca81b40d593f75d9c9c157c31`（标题「排查 agent configuration is missing 报错」）
> ⇒ ⇒ **是它主动发来的**（**不是我发出去的**）⇒ ⇒ ⇒ **§51.1「通信单向」被证伪**（订正注记见 §51.1）。
> **纪律**：**不采信转述** ⇒ ⇒ 三条**逐条自己重扫**（`git` 实测 / 守门 `ACTIVE` 白名单实测 / 逐文件只读核）。

### 55.1 🔴 **它说的「技术债」：与我的实测不符 ⇒ 不成立**

| 它说 | 我的实测（2026-10-28 · 本树）| 结论 |
|---|---|---|
| 「你的树 **有 `4ae5461`（初版）· 无 `c9cf2c1`（订正版）** ⇒ **请 merge 一次 `do-my-own-gh`**」| `git merge-base --is-ancestor` ⇒ **`4ae5461` 在我树 ✔** · **`c9cf2c1` 在我树 ✔**（两者都是 HEAD 的祖先）⇒ ⇒ 对 `do-my-own-gh` **落后 0 / 领先 192** | ❌ **不成立** ⇒ ⇒ **不需要 merge** |

⇒ ⇒ 我树 `docs/Worktree.md` §1e 第 ② 条**已是订正版**（实测原文：「**理由不是「没有出口」**」…「反向没有出口」是错的（本节初稿写错，已订正）」）
⇒ ⇒ ⇒ **它这条大概是在我并入 G4 尖端之前读的我的树** ⇒ ⇒ **教训**：**别人的树状态会变 ⇒ 「你缺 X」这类判断必须带 ref 或当场重核**（与「回执也要对时」同源）。
⇒ ⇒ ⇒ **它说的「G4 两个都并了（HEAD `ff81709`）」是对的** ⇒ ⇒ **差异只在「它读的是我树的哪个时刻」**。

### 55.2 ✅ **③ 路由订正：成立 ⇒ 我已按写权分家改台账**

- **7 份 `docs/DRIFT/<域>/vs-common.md`**（各 1 行，**活的路由句**「…合并后由整合者统一更新」）
  ⇒ ⇒ **G2/G3 自己的写权内**（G2 = `docs/DRIFT/{message,like,task}/**` · G3 = `docs/DRIFT/{article,comment,favorite,hot}/**`）
  ⇒ ⇒ **域侧能自修 ⇒ 域侧自修**（`docs/Worktree.md` §1e 硬边界 1）⇒ ⇒ **我域零落码** ⇒ ⇒ **已分别告知 G2 / G3**（指明文件 + 行号）。
- **`docs/DRIFT/global/lanes/L7-slowgate.md`（9 处）** ⇒ ⇒ G4 白名单**只有** `docs/DRIFT/global/vs-common.md`
  ⇒ ⇒ **`global/lanes/**` 不在任何人白名单** ⇒ ⇒ **写权孤儿 ⇒ 归合并收口**（§51.2 表已补一行）。
- ⇒ ⇒ 完整订正块 ⇒ §47.5 表下的**订正注记**。

### 55.3 🟡 **它顺带说的「我树那 8 处「整合者」全在改写记录里 ⇒ 不需要再动」：计数与定性都不符**

| 它说 | 我的实测 | 结论 |
|---|---|---|
| 「你树里那 **8 处**「整合者」… 全在「改写记录」本身 ⇒ **你的「全量改成 59 处」属实，不需要再动**」| 精确计数：**15 次出现 / 13 行**（users 14 次 12 行 · follow 1 次 1 行）⇒ ⇒ 且**其中 3 处是【活的路由句】不是历史记述**：<br>① `§47.5` ② 表格行（「建议合并收口执行者在合并回写时一并改」⇒ **落点写错了**）<br>② `§51.4`「那一行的落点…**由整合者写**」<br>③ `§52.4`「该约束只对「**整合者**逐笔 cherry-pick」…有效」| ❌ **计数与定性都不符** ⇒ ⇒ **活的 3 处已改**（② 改「由**合并收口执行者**写」· ③ 改「「**合并收口执行者**逐笔 cherry-pick」」· ① 见 §47.5 订正注记）⇒ ⇒ 其余 10 行是 §47.4 那次改写本身的记述（**引旧词**）⇒ ⇒ **保留** |

⇒ ⇒ **它自己那句提醒是对的**（「**命中数不是判据**」）⇒ ⇒ **但「数不对」本身也是一条要回的口供** ⇒ ⇒ **凡别人转述我树的数量，一律自己重数一遍**（本轮：**15 次 / 13 行 ≠ 8 处**）⇒ ⇒ **「差在哪」必须逐条归因**（本轮：3 处活的 + 10 处记述 + 2 处订正块引旧词）。

### 55.4 ✅ **① 确认我的判断 · ② 等 Jason 拍板 —— 均无动作**

- **① 「四 lane 全合后从合并树重算，单树重生成必错」**（我树 1 项 / G2 树 9 项）⇒ ⇒ **与我的结论一致**
  ⇒ ⇒ **四 lane 全合前不动它**（动了会随 lane 顺序漂移）⇒ ⇒ 重算脚本 `/Users/pr/.minimax/reports/apply-wave-coupling-snapshot.py` ⇒ **收口时对合并树跑**。
- **② `check-ledger-selfcheck.py` 的 `LEDGER_FILES`** ⇒ ⇒ **两张表都查不到 ⇒ 写权孤儿 ⇒ 不是我的活**
  ⇒ ⇒ **等 Jason 拍板或纳入合并收口清单**（我域本轮已实测 `--domain follow` ⇒ 「❌ 找不到 domain=follow 的台账文件」⇒ **与它一致**）。

### 55.5 🟢 **我给它的回执（已发 · 登记）**

⇒ ⇒ **只发一条、只回事实**。**共享面事项不发给它**（它自己声明不接 ⇒ 落点仍是 §51.2 的「登记 / 合并收口那一步」）⇒ ⇒ 回执只含 4 点：
1. 「技术债」**不成立**（附我树 `is-ancestor` 实测 + 对 `do-my-own-gh` 落后 0 的数 + 我树 §1e 第 ② 条已是订正版的原文）；
2. ③ **接受** ⇒ 已按写权分家改台账（7 份 → G2/G3 · `L7-slowgate.md` → 收口）；
3. ① ② **确认无动作**（① 收口重算 · ② 等 Jason）；
4. **订正它转述的「8 处」** ⇒ 实为 **15 次 / 13 行**，其中 **3 处是活的路由句**（已改）· 其余是改写记述（保留）。

### 55.6 🟢 **本轮发出的三封信（登记 · 非派单）**

| 对象 | 会话 | 送达判据（**看返回，不看队列行数**）| 内容 |
|---|---|---|---|
| 故障处置会话 | `mvs_d4fbe94ca81b40d593f75d9c9c157c31` | **`mode=started`**（真起了新 Turn ⇒ **通道双向可用的直接证据**）| 回执 4 点 ＋ 订正「技术债」不成立 ＋ 订正「8 处」实为 15 次/13 行 ＋ 自报两条我自己的错 |
| G2 | `mvs_5bc5f1b230ca43649d78c48e20b16d35` | **`mode=started`** | 告知 3 份他域台账各 1 行的**活的路由句**（message:75 · like:73 · task:72）⇒ **在你们写权内，自己改** |
| G3 | `mvs_ba99d8078fd94de681ce8068c21ee76a` | `mode=queued` · `position=10` · `ahead=10`（**忙**）| 告知 4 份他域台账各 1 行（article:72 · comment:73 · favorite:72 · hot:72）⇒ 同上 |

⇒ ⇒ **三封都是「已发出」，不是「已收到」** ⇒ ⇒ 要用回执做决策前，仍须去目标侧 transcript 确认（§51.2 同一纪律）。
⇒ ⇒ 🔴 **本轮顺带被证伪的一条**：「§51.1 通信单向」—— 实测**我 → 它**也是 `mode=started` ⇒ ⇒ **两个方向都能发** ⇒ ⇒ **我此前那次的「我 → 它不可」纯属误判**（理由：只观察到「我发的那封没有回音」）。

### 55.7 🟡 **一次不可复现的 `check-doc-links.py` EXIT=2 —— 记录，因为它【既不是红点也不是绿点】**

- **现象**：本轮某次整组门禁复跑里，`tools/check-doc-links.py` 报 **EXIT=2**（同组其余 7 项全 0）。
- 🔴 **但这不是门禁判定**：该脚本的 `main()` **只返回 0 或 1**（实测 `tools/check-doc-links.py:121` 与 `:125`）
  ⇒ ⇒ **EXIT=2 来自脚本之外** ⇒ ⇒ 而 **bash 的「命令解析错误」退出码就是 2**
  （本轮实测：我自己一条诊断命令因引号不配对就退了 **2**）⇒ ⇒ `python3 <脚本>`「打不开文件」的退出码**也是 2**
  ⇒ ⇒ ⇒ **最可能是那次组合命令里的 shell / IO 瞬时错误**（脚本本身无 `argparse`、无 `subprocess` ⇒ 不会自己吐 2）。
- **复跑证据**：其后 **连跑 4 次单独执行全部 EXIT=0**（写法 = `python3 <脚本> >/dev/null 2>&1; echo $?` ⇒ 正确）⇒ ⇒ **不可复现**。
- ⇒ ⇒ **处置原则**：**判「门禁红没红」必须看 ① 工具自述的退出码契约 ② 可复现性**
  ⇒ ⇒ **EXIT=2 这种「契约外的码」⇒ 既不是红也不是绿 ⇒ 处置是「复跑 ＋ 记录」，不是「改代码去迎合一个不存在的缺陷」**。
- ⇒ ⇒ **同族**：与「跑绿 ≠ 判据活着」「零命中是假信号」「日志里 ERROR 串 ≠ 失败」同源
  ⇒ ⇒ **本条补的是「退出码不在契约内 ⇒ 先查是不是【工具自己 / 调用方】出了错，而不是【判据】出了错」**。

### 55.8 🔴🔴 **同轮自己踩的第二个坑：「守门全绿」是【我自己的 shell 写法】造出来的假信号**

- **现象**：写完 §55.7 后我又跑了一轮整组门禁，输出是「**全 EXIT=0**」⇒ ⇒ 我据此差点记下一笔「**两个既有红点（coupling / tomcat-limits）现在都变绿了**」⇒ ⇒ **差点写进台账**。
- 🔴 **真相**：那一轮我用的是这个写法 ——
  ```bash
  for g in ...; do python3 "$g" >/dev/null 2>&1; printf "  %-44s EXIT=%s\n" "$(basename $g)" "$?"; done
  ```
  ⇒ ⇒ **`$(basename $g)` 是同一条命令行里的命令替换** ⇒ ⇒ bash **按顺序展开各词** ⇒ ⇒ **命令替换跑完会覆盖 `$?`**
  ⇒ ⇒ ⇒ **`$?` 拿到的是 `basename` 的退出码（恒 0），不是门禁的** ⇒ ⇒ ⇒ **读到的「全 EXIT=0」全是假的**。
- **正确写法**（先落到变量，再打印变量）：
  ```bash
  python3 "$g" >/dev/null 2>&1; rc=$?; printf "  %-44s EXIT=%s\n" "$g" "$rc"
  ```
  ⇒ ⇒ `$rc` 是变量 ⇒ **不会被任何命令替换覆盖**。
- **改正后的实测（正确写法）**：`check-wave-coupling` **EXIT=1** · `check-tomcat-limits` **EXIT=1**
  ⇒ ⇒ **两个既有红点【没有变绿】，与前几轮完全一致** ⇒ ⇒ **「变了」这个印象是我的 bug 造出来的**。
- ⇒ ⇒ **判定式**：① **凡用命令替换拼 `printf`/`echo`，就不要在同一行读 `$?`** ⇒ 退出码一律先落到变量；
  ② **「守门结果变了」这类结论，必须换一种写法【再测一遍】才准** ⇒ ⇒ 因为「结果变了」本身可能就是**测量方式变了**；
  ③ ⚠️ **更狠的一层**：这个坑**不会报错、不会红、输出还很好看（全 0）** ⇒ ⇒ 它和「跑绿 ≠ 判据活着」是**同一个陷阱的两面**
  ⇒ ⇒ ⇒ **「全绿」最需要警惕的时候，就是「你怎么测的」刚改过的时候**。
- ⇒ ⇒ **同族**：与「日志里 ERROR 串 ≠ 失败」「零命中是假信号」「你以为的现状 ≠ 实际现状」同源
  ⇒ ⇒ **本条补的是「退出码可能被【同行的命令替换】偷走 ⇒ 你测的是 basename 的退出码」**。

---

## 56. 🟢 **并入 G4 终版 `2f39b0f`（台账 §4.10.62）—— 我域相关四条逐条实测对照**（2026-10-28 · G1 lane）

- **合并**：`2f39b0f` ⇒ **零冲突** ⇒ 用 **G4 自家的合并内容守门** `check-merge-tree-content.py` 复验 ⇒ **PASS（0 问题 · 0 不可信）**。
- **G4 §4.10.62 的那批销号里，与我域直接相关的四条** —— G4 的结论我**逐条自己核**（不采信转述）：

| G4 的结论 | 我的独立复核 | 我域动作 |
|---|---|---|
| **R1/R2/R3/R4**（users 域 Redis 超时四种形态）**销号** | 我域四条确实已修（`DRIFT-USERAPI-016` / `DRIFT-USERRPC-V-014`① / `DRIFT-FOLLOW-011` / `DRIFT-ARPC-013` 订正块）⇒ ⇒ 与我 §36 的记录一致 | **无动作** |
| **Redis 收编（5 份域内副本）销号** | 我域 5 份副本已删 ＋ 5 份守门各补 2 条 bean 身份断言 ⇒ ⇒ 共享层那份到位 | **无动作** |
| **V-002「#6 第 1 步」** ⇒ 唯一剩余落点 = `docs/Intentional_Deviations/**` 的销号签字 ⇒ **不在我、也不在 G1 写权面** ⇒ 交合并收口执行者 | 与我 §51.2 的登记**一致**（G1 白名单只有 `docs/DRIFT/{users,follow}/**`）| **无动作**（已登记）|
| **A / B 同批硬约束的逐树分布**：A(`d4e4745`) **IN** · B(`4786eb5`) **缺** ⇒ ⇒ 我树的 `check-tomcat-limits` 红 = 「**B 缺 A 有**」的必然 | ✅ **实测复核**：`d4e4745` **在我树** · `4786eb5` **不在我树**（它改的是 **`task-api` 的 yml** `mode: raw → "off"` ＋ 5 条真 Tomcat 守门 ⇒ ⇒ **属 G2 的 task 域**）⇒ ⇒ **不是我域的缺陷** | **零落码** ⇒ 合并树两边都进 ⇒ EXIT=0 |

### 56.1 🟡 **精确化我台账里 `check-tomcat-limits` 红点的机制（原记录不够准）**

- **我原来的说法**（按 G2 当时的转述登记）：「**期望表两边都查不到 ⇒ 写权孤儿 ⇒ 只能整合者改**」。
- **现在的直接实测**（`check-tomcat-limits.py` **它自己的表格**）：**10 个 api 进程里 9 个 ✅**，**只有 `service/task/task-api` ❌**
  ⇒ ⇒ ⇒ **我域的三个 api（`user-api` / `admin-api` / `follow-api`）在表里全 ✅**。
- ⇒ ⇒ ⇒ **红点的精确机制 = 「A（`d4e4745` · 真源派生期望表）在我树 · B（`4786eb5` · task-api yaml `off`）不在我树」**
  ⇒ ⇒ ⇒ **它不是「期望表两边都查不到」**（那是**旧状态**）⇒ ⇒ **它是「配对缺一半」** ⇒ ⇒ **B 属 task 域（G2）⇒ 我域零落码**。
- ⇒ ⇒ **纪律**：**守门的红点机制要按【它自己的输出】读，不要按转述** ⇒ ⇒ 与「回执也要对时」「别人转述我树的数量一律自己重数」同源。

### 56.2 🟡 **白名单清空的前置 = 我域那笔删除进 G4 分支**（跨 lane 依赖登记）

- G4 的 `GoStyleRedisClientShadowingGateTest.LEGACY_DOMAIN_COPIES` = **仅 1 条**
  （`service/users/user/user-api/.../config/RedisDisconnectedBehaviorConfig.java`）。
- **逐树实测（G4 记的 · 我认同）**：**我树 = 已删** ✅ · G2 / G3 / G4 树 = **还在**。
- ⇒ ⇒ **为什么 G4 现在不能清**：清了 ⇒ ⇒ **它自己树里那个文件还在** ⇒ ⇒ 守门会判成「未登记的域内副本」⇒ ⇒ **🔴 假红**。
- ⇒ ⇒ **⇒ 我域那笔删除是 G4 清白名单的【前置】** ⇒ ⇒ **合并树取我的版本（文件已删）⇒ ⇒ 白名单那条在合并树上变成空转**
  ⇒ ⇒ 只会给 `staleWhitelistHint` 提示 ⇒ ⇒ **由合并收口执行者 / G4 在我并入后清空这 1 条**。
- ⇒ ⇒ **我域动作**：**零落码**（删除已落）⇒ ⇒ 但这条依赖**登记在此**（免得将来问「白名单为什么还没清」）。
- ⇒ ⇒ **新纪律（G4 写的，我认同）**：**白名单条目是【登记待删】的，不是【永久豁免】的** ⇒ ⇒ **域侧删完后必须回来清** ⇒ ⇒ **清之前先核「该文件在目标树里真的不存在」**（跨树事实要对齐 commit）。

### 56.3 🔴 **故障处置会话【第二次】说我树缺 `c9cf2c1` ⇒ 仍然不成立（这次我把它查到底了）**

| 它说 | 我的实测（HEAD `d22bfed`）| 结论 |
|---|---|---|
| 「你树里还缺 §1e 的**订正版** `c9cf2c1`（第 ② 项旧写法是错的）⇒ **merge 一次 `do-my-own-gh`**」| ① `git merge-base --is-ancestor c9cf2c1 HEAD` ⇒ **在我树 ✔**<br>② `git branch --contains c9cf2c1` ⇒ **列出了 `align/g1-users-follow`** ✔<br>③ **已提交 blob** 里的 §1e 第 ② 条实测原文：「**理由不是「没有出口」**」…「**「反向没有出口」是错的**（本节初稿写错，已订正）」<br>④ 对 `do-my-own-gh` **落后 0 / 领先 195** | ❌ **不成立** ⇒ ⇒ **不需要 merge** |

- 🔴 **它为什么会看错（我查到了机制）**：`git log --merges --ancestry-path c9cf2c1..HEAD` ⇒
  `ff81709`（G4 把 `do-my-own-gh` 并进 G4）⇒ `0eca86e` ⇒ `f6a01fb`（我把 G4 并进 G1）
  ⇒ ⇒ ⇒ **`c9cf2c1` 是【传递】进我树的**
  ⇒ ⇒ ⇒ **若只查「我的提交里有没有直接含 `c9cf2c1`」⇒ 就会漏掉传递** ⇒ ⇒ **判据必须是 `merge-base --is-ancestor`（传递闭包），不是「谁直接提的」**。
- ⇒ ⇒ **纪律**：**「某提交在不在我树」⇒ 必须用 `is-ancestor` 判定** ⇒ ⇒ **传递进来的与直接并进来的，在可达性上完全等价**
  ⇒ ⇒ 与「跨树引用要对齐 commit」「比较两棵树必须锚到同一时刻」同族。

---

## 57. 🟢 **G4 的回信（7 条）—— 逐条独立复核：6 条成立、1 条是「消息本身已过期」**（2026-10-28 · G1 lane）

> ⚠️ **时基先说**：这封信标的 G4 HEAD = **`a38ad75`** ⇒ ⇒ 实测 `a38ad75` 是 **`2f39b0f` 的祖先**
> ⇒ ⇒ **这封信写于我并入 `2f39b0f` 之前 ⇒ 已被 G4 自己的 §4.10.62 取代** ⇒ ⇒ 我按**当前** G4 尖端复核。
> ⇒ ⇒ **同族**：「回执也要对时」—— **收到回执先看它标的 HEAD 是不是当前尖端**，否则你会拿一份过期快照做判断。

| # | G4 的结论 | 我的独立复核 | 我域动作 |
|---|---|---|---|
| ① | (a)「#6 第 1 步取消」⇒ **零动作** ⇒ 不落 DDL（方向相反：NULL 会跳过 setter ⇒ 读出 `null` ≠ Go 的 `0`）⇒ 唯一剩余落点 = `docs/Intentional_Deviations/**` 销号签字 ⇒ 不在我也不在它写权面 ⇒ 交合并收口执行者 | 与我 §51.2 的登记**一致** | **无动作** |
| ② | (b) 编号口径：**认**「第九次全量」编号与性质双错（那轮实为 `-pl` 5 模块 + `-am` 的定向跑）；**但「第十二次不是全量」不成立**（§4.10.26 写明主跑 46 + 补跑 6 = 52 两段拼，我 13:35 实测到的那条正是它的**补跑 ②**）⇒ ⇒ **新纪律：「`ps` 只证明这棵树在跑 maven ⇒ 不能证明在跑全量」⇒ 凡说「在跑全量」必须同时给出 `-pl` 覆盖面** | ✅ **接受这条纪律** ⇒ ⇒ 我自己的复现命令**本来就带显式 `-pl` 目标清单**（§54.1 的口径）⇒ ⇒ **已落地，无需再改** | **无动作** |
| ③ | (c) 本层当前**不起全量**（Docker 宿主→容器转发故障仍在 + 不叠第四条）⇒ ⇒ **我的「窗口期零 maven」承诺【可以解除】** | ✅ 实测同意 ⇒ ⇒ 本轮我确实跑了 maven（§57.1）⇒ ⇒ **窗口已解除** | **解除** |
| ④ | ③ 的归属**写进 G4 台账**（主体是**共享层的证据边界** ⇒ 属它写权面）⇒ 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` 仍归合并收口执行者 ⇒ ⇒ **不需要我再找任何会话** | ✅ 与我 §51.2 一致 | **无动作**（**确认：不用再找会话**）|
| ⑤ | Redis 收编前置**已满足**（`AutoConfiguration.imports` 6 条含共享层那份 · 域侧副本已删 · 4 笔全在树内）⇒ ⇒ **验收 = 三道机器网绿 ＋ `REJECT_COMMANDS` 确实落在客户端 `ClientOptions` 上** | ✅ **实测逐条对上**（§57.1）⇒ ⇒ **验收已完成** | **已完成** |
| ⑥ | 槽位「**6 vs 7**」= **代码态差**（`9690b04` 不在主干）⇒ ⇒ **不是你漏数** ✔；MaxBytes 唯一性两侧都成立 ⇒ ⇒ 优先走**签字**（挪它要同一次提交改 `@Order` + 槽位快照守门 + 台账理由）⇒ 签字档归合并收口执行者 | ✅ **实测确认**：`9690b04` **不在 main** ✔（它加了 `GoStylePathNormalizingFilter` = **槽位 2**）⇒ ⇒ **main 的代码态 = 6 个共享层 Filter**；**我树 = 7 个**（`SharedFilterOrderSnapshotGateTest` 的 `SLOT_SNAPSHOT` **恰 7 项** · 槽位 2~8）⇒ ⇒ 我域守门写的「7 个」与我树代码态**一致** ⇒ ⇒ **不是漏数** | **无动作** |
| ⑦ | 三路并发 ⇒ 采纳「每条显式加 `-DargLine=-Xmx2g`」 | ✅ 本轮一直这么做（§54.1 的命令即带）| **无动作** |

### 57.1 🟢 **⑤ 的验收 = 三道机器网 ⇒ 我实跑了（附一次【环境类】失败的完整归因）**

- **G4 给的三道网**（我逐个确认**在我树存在**，不是采信）：

  | # | 网 | 在我树 | **主证据（单批次）** | 复核批（`-Dtest=` 定向）|
  |---|---|---|---|---|
  | ① | `service/common/src/test/.../redis/GoStyleRedisClientOptionsGateTest.java`（共享层那份**真的被装配**且**只有一份**）| ✔ 存在 | ✅ **3 条绿**（域级全量 · `common` 模块 · 日志行 278）| ✅ 3 条绿（首轮）|
  | ② | `service/common/src/test/.../redis/GoStyleRedisClientShadowingGateTest.java`（域侧**零副本**）| ✔ 存在 | ✅ **3 条绿**（域级全量 · `common` 模块 · 日志行 280）| ✅ 3 条绿（首轮）|
  | ③ | 域侧 5 份 FastFail 守门的 **bean 身份断言** | ✔ 5 份**各 3 处**命中 `goStyleRedisClientOptionsCustomizer` | ✅ **5 份 × 2 条 = 10 条全绿（单批次）**（域级全量）| 🟡 **跨两轮**：首轮 2 份绿 ＋ 1 份环境类失败 ＋ 2 份被 fail-fast 跳过；复跑补齐其余 3 份全绿（下）|

- 🔴 **主证据 = 域级全量回归（`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test` · 18/18 BUILD SUCCESS · 47:10）**：
  三道网**全部 16 条**（3 + 3 + 5×2）**在同一批次里全绿** ⇒ ⇒ ⇒ **验收闭合**（不需要「跨轮拼合」来凑）
  ⇒ ⇒ 逐条可指名：`GoStyleRedisClientOptionsGateTest` 3 条（`/tmp/g1_domain_regression_20261028.log:278`）·
  `GoStyleRedisClientShadowingGateTest` 3 条（同文件 `:280`）· `AdminRpcRedisDisconnectedFastFailTest` 2 条 · `AdminApiRedisDisconnectedFastFailTest` 2 条 ·
  `UserRpcRedisDisconnectedFastFailTest` 2 条 · `UserApiRedisDisconnectedFastFailTest` 2 条 · `FollowApiRedisDisconnectedFastFailTest` 2 条
  ⇒ ⇒ 且**同批的 `common` 模块 = 333 条全绿** ⇒ ⇒ **网①② 的绿不是「单独跑共享层才有」**。
- ⚠️ **口径纪律（本条与「条数聚合」的坑同族）**：**跨轮拼合的绿态必须写明每一份来自哪一轮** ——
  定向批（`-Dtest=`）**首轮 reactor 17 模块（含 admin）· 复跑 reactor 13 模块（不含 admin）**
  ⇒ ⇒ **5 份域侧守门没有任何一份是在同一批里跑完全部的** ⇒ ⇒ **若只写「复跑后 3 份全绿」会被读成「一次跑批 5 份全绿」** ⇒ ⇒ **本条以「域级全量单批次」为主证据，定向批只作复核**。

- **`AutoConfiguration.imports` 逐字对上**：`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  **第 5 行** = `com.rag.common.redis.GoStyleRedisClientAutoConfiguration` ✔
  ⇒ ⇒ **G4 说的「第 5 行」逐字准确**（它这次的自报经得起核）。
- 🔴 **③ 里 `user-rpc` 那份的失败 = 【环境类】，不是行为红**：
  ```
  ContainerLaunchException: Container startup failed for image redis:7-alpine
  Caused by: com.github.dockerjava.api.exception.InternalServerErrorException:
    Status 500: failed to set up container networking:
    driver failed programming external connectivity on endpoint thirsty_einstein
  ```
  ⇒ ⇒ **Docker 端口编程失败** ⇒ ⇒ **不是「被测行为红」**（AGENTS.md §3.3 纪律：**判定前先读 `Caused by` 链**）
  ⇒ ⇒ **根因 = 并发**：实测当时 `docker ps` 显示段内 `22700` / `22701` 正被**别的 lane 的容器**占着（`Up 12~14 秒`），
  且 `ps` 显示 **G2（message-rpc）与 G3（favorite-api / article-rpc）同时在跑 surefire**
  ⇒ ⇒ ⇒ **这正是我域 `GoStyleHostPortPinning` javadoc 里写明的残留竞态**（「探端口」与 Docker「绑端口」之间有极小窗口 ⇒
  **另一个 JVM 同时挑中同一空闲口 ⇒ 后绑者硬失败」⇒ ⇒ **大声失败，不是静默走偏** ✔）
  ⇒ ⇒ ⇒ **判定：并发跑批的环境失败 ⇒ 不是回归、不是缺陷**
  ⇒ ⇒ ⇒ **且这 5 份在域级全量（单批次）里全绿**（见上「主证据」）⇒ ⇒ **绿态下就是绿的**。
- ⇒ ⇒ **纪律（本条补的是一整类）**：**多 lane 并发时不要同时起域级回归** ——
  **端口段是【共享资源】**，所以并发风险**不止** EXIT=137 的 OOM，还有**【容器端口编程失败】**这一类
  ⇒ ⇒ ⇒ **「并发」两个字在台账里必须落到具体资源上**（CPU/内存/**宿主端口段**）。

---

## 58. 🟢 **发给 G4 的回信（登记）+ 本轮自查的两条新事实**（2026-10-28 · G1 lane）

- **发出对象** = G4 会话 `mvs_e8063ac67c654150ab7797b8116ef0a1`（标题 `G4Common`）
  ⇒ ⇒ **送达态 = `mode=queued` · position 60 · ahead 60** ⇒ ⇒ **排队待跑**（G4 当时是 `42212` Token Plan 上限）
  ⇒ ⇒ ⇒ **「我发了」≠「对方看到了」** ⇒ ⇒ 用它的回执做决策前**须去它侧 transcript 确认 `cross-session-message` 块**。
- **信的内容（9 段）**：⓪时基（它标的 `a38ad75` 已过期）· ①**它上次要我 merge 的两笔已在我树内**（`22dc67e` / `c164e63`，`is-ancestor` 实测）
  · ②**白名单清空的正确顺序**（下）· ③⑤三道机器网实跑结果（**单批次 16 条全绿**）＋**跨轮口径订正**
  · ④环境类失败的完整归因 · ⑤⑥槽位「6 vs 7」实测确认 · ⑥接受它立的「`-pl` 覆盖面」纪律
  · ⑦我域当前状态（HEAD / 707 / 9 绿 3 已知红点）· ⑧下一轮 = 波末收口，**不由我触发** · ⑨它会话 42212，我域不阻塞。

### 58.1 🟢 **白名单清空的前置 = 我域那笔删除**（实测钉死，可直接 cherry-pick）

| 事实 | 实测 |
|---|---|
| 删域内副本的那笔提交 | **`312a09b`**（删 5 份 `RedisDisconnectedBehaviorConfig` ＋ 5 份守门补 bean 身份断言）|
| 该提交所在树 | **仅 `align/g1-users-follow`**（G4 / G2 / G3 **均 ∉** —— `is-ancestor` 实测）|
| 我树域内 `LettuceClientOptionsBuilderCustomizer` **主源码声明数** | **0**（`service/users` ＋ `service/follow` 全扫）⇒ 它的守门在我树**绿**（网② 3 条绿，已实跑）|
| G4 / G2 / G3 树 | 那份文件**还在** ⇒ **现在清白名单会让它们自己假红** |
| `312a09b` 触及路径 | `service/users/**` ＋ `service/follow/**` ＋ `docs/DRIFT/users\|follow/**`（17 文件）⇒ **零共享层路径 ⇒ cherry-pick 零冲突** |

⇒ ⇒ **正确顺序 = 先把 `312a09b` 并进 G4 树（或等四 lane 合主干），再由 G4 / 合并收口执行者清白名单**
⇒ ⇒ **我域零落码**（清白名单那一步不在我写权面）。

### 58.2 🟢 **本轮把「我域还有没有能自闭合的 OPEN」重扫了一遍**（结论：没有）

| 扫到的 OPEN | 落点 | 复核结论 |
|---|---|---|
| `DRIFT-USERRPC-V-004` 的 `users_user_avatar_history` 半边 | 我域 | ✅ **实为已关闭** —— `user/rpc.md` 该行已由**第六轮复核**更正为「已修（列类型与可观察半边）」，DDL 已是 `TIMESTAMPTZ` ＋ 守门 `UserRpcAvatarHistoryTimestamptzParityTest`（9 条 · 真 PG）⇒ `_shared.md` 里那句「🔴 仍然 OPEN」是**2026-10-23 第三轮的历史断言**，且该文件头部**已自标**「中段还留着…这类已被同文件第三轮推翻的断言 ⇒ 当前状态一律以文件末尾为准」⇒ ⇒ **不重写历史，也不再加订正块**（已被文件头覆盖） |
| `DRIFT-USERAPI-013`（过载保护：breaker 键空间 ＋ 注释与实现矛盾）| **`service/common`**（11 个 protection 类）| 🔴 **不在我写权面** ⇒ 跨域 ⇒ 归 G4 / 合并收口执行者 ⇒ **本域只报告**（`user/api.md` §7「7.x-2」已完成 2026-10-26 只读复核：Go key = 注册期 `route.Path` 模板 / RAG = raw `getRequestURI()`；可观察面在 `article-api`）|
| `DRIFT-ARPC-010`（= E66 · Redis 写不绑 deadline）| **`service/common`**（Redis 调用路径）| 🔴 **不在我写权面** ⇒ 归 G4 ⇒ 维持 OPEN（两侧同编号 ⇒ 销账别当两条）|
| `DRIFT-FOLLOW-008`（MaxConns 闸门粒度：Go 每路由一个 vs RAG 全局一个）| 共享层 | 🔴 归 G4 |
| `DRIFT-USERRPC-V-011`（`DEFAULT CURRENT_TIMESTAMP` 残留 **8 处**，刻意保留）| 我域 DDL | 🟡 **等用户裁决**（见 §1「等拍板」清单）|
| `DRIFT-ARPC-002`（`UpdateUserResp.success` Go=false / RAG=true）| 我域 | 🟡 **等用户裁决**（已有双侧活体证据 ＋ 决策 tripwire）|

⇒ ⇒ **结论：我域可自闭合的项已全部闭合** ⇒ ⇒ **我域唯一未完成项 = 波末 C7 收口回归**（待四 lane 全合主干·**非我触发**）。

---

## 59. 🔴🟢 **新立 `DRIFT-USERRPC-V-015`：Go 的 `span.RecordError`【不】置 span 状态 ⇒ RAG 多置了 ERROR（遥测面漂移）⇒ 已域内修完 5 处**（2026-10-28 · G1 lane）

> ⚠️ **本条的起因是一个【对共享层台账的事实订正】** —— G4 的 `docs/DRIFT/global/vs-common.md` §4.10.64 ⑤ 断言：
> 「Go 侧 `observability.go:103` 的 `span.RecordError(...)` **会把 status 置 ERROR** ⇒ Java 必须显式补」
> ⇒ ⇒ **这条断言与 otel-go SDK 的源码注释【正好相反】** ⇒ ⇒ **若 G3 照它去给 favorite 域补 `setStatus`，
> 就等于在 G3 的域里【新造】一处漂移** ⇒ ⇒ **本条因此也是一封给 G3 的预警**（已发信）。

### 59.1 🔴 **机制：otel-go 的 `RecordError` 不改状态（SDK 注释原文 + 实现逐行）**

| 项 | 证据 |
|---|---|
| 位置 | `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:631-656`（`recordingSpan.RecordError`）|
| **方法注释原文** | “SetStatus is required if the Status of the Span should be set to Error, **this method does not change the Span status**.” |
| 实现 | 只做 `s.addEvent(semconv.ExceptionEventName, opts...)`（带 `exception.type` / `exception.message`）⇒ **全程不碰 `s.status`** |
| 版本来源 | Sea 仓 `go.mod:18-21` pin `go.opentelemetry.io/otel` + `/sdk` **v1.44.0** |

⇒ ⇒ **Go 侧：只要调用点是「只调 `span.RecordError`」，span 状态就恒为 `Unset`。**

#### 59.2.1 🔴 **Go 全仓 `SetStatus` 落点全表（本条修法的决定性依据 · 自己数的，不采信转述）**

| 目录 | `span.SetStatus(...)` 处数 | 落点 |
|---|---|---|
| `service/comment/` | **4** | `service/comment/rpc/internal/logic/get_comment_logic.go:86,102,111,118`（`codes.Error`）|
| `service/common/` | **3** | `service/common/observability/observability.go:83,105,128`（`otelcodes.Error`）|
| `service/knowledge/` | **2** | `service/knowledge/api/internal/telemetry/operation.go:191,258`（`codes.Error`）|
| **`service/user/`（user-rpc + admin-rpc）** | **0** | —— ⇒ ⇒ **我域 Go 侧零处显式 `SetStatus`** |
| **全仓合计** | **9** | 扫 `Sea-RideTheWind_golang/service/**`（排除 `_test.go`）|

⇒ ⇒ **这条全表直接决定本条的修法**：既然 **user / admin 域 Go 侧零处显式 `SetStatus`** ⇒ ⇒
**我域那 5 处 `span.setStatus(StatusCode.ERROR, …)` 全部是 RAG 独有的 ⇒ 全部是漂移 ⇒ 全部该删**。

⚠️ **而共享层那 3 处与 Go 一一对应 ⇒ 共享层是对的 ⇒ 本条对共享层零改动**（已逐条核）：

| Go（`observability.go`）| RAG（`service/common/.../Observability.java`）|
|---|---|
| `MarkBusinessError` → `:83 span.SetStatus(otelcodes.Error, msg)` | `markBusinessError` → **`:83 span.setStatus(StatusCode.ERROR, msg)`** |
| `MarkSystemError` → `:105` | `markSystemError` → **`:109`** |
| `MarkTimeout` → `:128` | `markTimeout` → **`:132`** |

⇒ ⇒ **「共享层必须显式补 `setStatus`」这条【结论】是对的，错的是它的【理由】** ⇒ ⇒
⚠️ **订正别人的理由时，必须同时写明「结论保留 + 那行代码不许动」** ⇒ ⇒ **本条对共享层只读不写 ✔**
（否则照着「理由被订正」去删共享层那 3 行 ⇒ **那才是真回归**：Go 的共享层 span 恒 ERROR ⇔ Java 变 UNSET）。

🟢 **交叉印证（两个 lane 独立得出同一结论）**：G3 lane 在自己的域内**独立**核到了同一 SDK 源码
（`otel/sdk@v1.44.0/trace/span.go:631-655`），并得出「Go 侧只 `RecordError` 的 span，status 本就恒 UNSET ⇒ 域侧零改动」
⇒ ⇒ **两条互不相识的 lane 独立同结论** ⇒ ⇒ 本条的修法**不是单边推断**。
（⚠️ 但**按纪律不采信转述**：上面那张全仓落点表是**我自己重新数的**，不是引它的。）

### 59.2 🔴 **Go logic 层【零处】`span.SetStatus` ⇒ 状态恒 Unset（逐文件实测）**

| 目录 | `span.RecordError` 处数 | `span.SetStatus` 处数 |
|---|---|---|
| `service/user/user/rpc/internal/logic/` | **7**（`loginlogic.go:54` · `registerlogic.go:53,60,69,85` · `deleteuserlogic.go:48,56`）| **0** |
| `service/user/admin/rpc/internal/logic/`（8 个 logic）| **16** | **0** |

⇒ ⇒ **另一条路**：Go **共享层** `service/common/observability/observability.go:103-105` 是**显式三句**
（`span.RecordError(errors.New(msg))` ＋ `span.SetAttributes(...)` ＋ **`span.SetStatus(otelcodes.Error, msg)`**）
⇒ ⇒ **那一条路径 Go 确实置 ERROR** ⇒ ⇒ RAG 的 `Observability.markSystemError` / `markTimeout`（共享层）照此实现是**对的**
⇒ ⇒ ⚠️ **「logic 层只 RecordError」与「共享层 helper 三句」不可混为一谈** —— 混淆正是 G4 §4.10.64 ⑤ 的错因。

### 59.3 🟢 **双侧运行时探针（证据强度 = L4）**

- **Go 腿**（otel-go v1.44.0 真跑，探针 `/tmp/g1_span_status_probe/main.go`，复用冻结仓的 `go.mod`/`go.sum`）：

  | 形态 | 导出 span 的 `status.Code` | exception 事件 |
  |---|---|---|
  | **A** = logic 层写法（只 `span.RecordError(err)`）| **`Unset`** | 1 条（`exception`）|
  | **B** = `observability.go:103-105` 三句 | `Error`（description=`system error`）| 1 条（`exception`）|

- **RAG 腿**（`UserRpcSpanStatusGoParityTest`，自装 OTel SDK ＋ 收集型 `SpanExporter` ⇒ 断言**导出的 span 数据**）：

  | # | 用例 | 修**前** | 修**后** |
  |---|---|---|---|
  | ① | login 的 DB 失败分支（Go `loginlogic.go:54`）| 🔴 **红在状态断言**（实得 `ERROR`）| 🟢 绿 |
  | ② | register 的 DB 查重失败分支（Go `registerlogic.go:53`）| 🟢 绿（**本就与 Go 一致**）| 🟢 绿 |
  | ③ | delete 的 DB 失败分支（Go `deleteuserlogic.go:48`）| 🟢 绿（**本就与 Go 一致**）| 🟢 绿 |
  | ④ | 域内主源码零处 span 状态写入（源扫）| 🔴 红（命中 **5** 处）| 🟢 绿 |
  | ⑤ | **判据自检**：造假文件 ⇒ 扫描器必须报出它 | 🟢 绿 | 🟢 绿 |

  ⇒ ⇒ **② ③ 是内建对照**（同样是 `recordException` 分支，一个多置状态、一个不置）⇒ ⇒ **证明本测试有判别力**。
  ⇒ ⇒ **既有守门 `AdminSpanWiringTest`（3 条）修后仍全绿** ⇒ ⇒ 删 `setStatus` 没打破既有 span 装配守门。
- **变异实证**（对**最终**代码）：把 login 那处 `setStatus` 加回去 ⇒ `maven EXIT=1` ⇒ **5 条里 2 红**
  ⇒ ① **红在状态断言**（`status 必须与 Go 同为 UNSET`）· ④ 红在源扫命中 ⇒ ⇒ **编译通过 + 红在目标断言** ✔
  ⇒ ⇒ 回滚后三个文件 **sha256 与基线逐字节一致** ✔

### 59.4 🟢 **修法（域内自闭环 · 5 处 · 零共享层改动）**

| 文件 | 站点 | 动作 |
|---|---|---|
| `service/users/user/user-rpc/.../UserDomainService.java` | 4 处（login DB查询失败 / register 密码生成失败 / register uid生成失败 / register DB更新失败）| **删 `span.setStatus(StatusCode.ERROR, …)`**（保留 `recordException` —— **事件面本就与 Go 一致**）|
| `service/users/admin/admin-rpc/.../AdminSpans.java` | 1 处（`traced` 的 catch 分支）| 同上 ＋ 改类 javadoc（原写「`recordException(e)` + 置 ERROR 状态」⇒ 订正）|

⇒ ⇒ **共享层 `service/common` 的 `Observability` 一行未动**（它照 Go 的显式 `SetStatus` 实现，是对的）
⇒ ⇒ **我域 `setStatus(StatusCode.*)` 命中数：5 → 0**（由守门 ④ 钉死）
⇒ ⇒ ⚠️ **本条同时暴露我域一个【自相矛盾】**：修**前** user-rpc 4 处置状态、3 处不置 ⇒ **同一份代码里两种做法并存，且没有任何台账解释** ⇒ ⇒ **已统一对齐 Go**。

### 59.5 🔴 **本轮自捕三条（都是「假信号」家族）**

1. **源扫守门第一版是【空转】的** —— 路径写成 `service/users/user/src/main/java`，而真实模块在
   `service/users/user/user-rpc/src/main/java`（`service/users/user` 是**聚合目录**）⇒ **扫了 0 个文件却报绿**
   ⇒ ⇒ **零命中是假信号** ⇒ ⇒ 修法：递归找 `/src/main/java/` ＋ **守门里加「扫到的文件数 > 20」断言** ＋ **判据自检（造假文件）**
2. **判据自检当场抓出模式太窄** —— 第一版模式 `setStatus(StatusCode.` 只吃**短式**，
   漏**全式** `setStatus(io.opentelemetry.api.trace.StatusCode.ERROR` ⇒ ⇒ 改成正则 `setStatus\([^)]*StatusCode\.`
   ⇒ ⇒ **判据自检不是装饰**：它当场否掉了我的第一版实现
3. **变异回滚用 `git checkout -- <file>` ⇒ 连带回滚了【未提交的真修法】**（把 4 处删除冲回 HEAD）
   ⇒ ⇒ 改法：**先 `cp` 一份副本到 `/tmp`，回滚时从副本恢复**，并用 `sha256sum -c` 逐字节核
   ⇒ ⇒ ⚠️ **`git stash` 在本仓被明令禁用**（工作区级状态会被并发 agent 互偷）⇒ 「存副本」是本仓的等价手段
   ⇒ ⇒ 🔴 **自曝：这条坑【早就写进 memory 了】**（2026-10-27 · G3 lane · 同仓同一动作链），连修法都写了
   （「改完先 `git add`」＋「配套更稳：备份文件比对还原」）⇒ ⇒ **我仍然踩了** ⇒ ⇒
   ⇒ ⇒ **纪律**：**凡要写「变异 → 还原」这段代码之前，先搜 memory**（不是写完复盘才搜）⇒
   ⇒ ⇒ **memory 里已有的条目 = 已知的坑清单，不是事后回顾** ⇒ ⇒ 「这坑我好像见过」不是豁免理由。

### 59.6 📮 **已发出的两封信（登记）**

| 对象 | 内容要点 | 送达态 |
|---|---|---|
| **G4** | **事实订正**：§4.10.64 ⑤ 的断言与 SDK 源码相反 ⇒ 给原文 + 探针输出 + 修法（**不要**照它给域内补 `setStatus`）⇒ 共享层自己的 `Observability` **是对的**，别动 | `mode=queued` · **position 61 · ahead 60**（发信时 G4 = `error` 50001 `unknown error, 722 (1000)`）|
| **G3** | **预警**：若已按 G4 §4.10.64 ⑤ 给 favorite 域补了 `setStatus` ⇒ **那是新造漂移** ⇒ 给判据（看 Go 调用点有没有**显式** `span.SetStatus`）+ 可复用的探针写法 + 两个踩坑点 | `mode=queued` · **position 14 · ahead 14**（发信时 G3 = `started` / `net::ERR_NETWORK_CHANGED` 50113）|

⇒ ⇒ **两封都因对方会话处于 error 态而排队** ⇒ ⇒ ⚠️ **G3 那封的【时效风险最高】**：若它已按 G4 的错误前提补了 `setStatus`，那处漂移已经进了 G3 的树 ⇒ ⇒ **收口那一步必须按本条判据复核 G3 的 favorite 域**（⇒ 已登记给合并收口那一步）。

### 59.7 📌 **写权与状态**

- 本条 5 处改动**全部在我写权面**（`service/users/user/user-rpc/**` ＋ `service/users/admin/admin-rpc/**`）⇒ **域内自闭环，零派单**
- 共享层 `service/common` **未动**（`Observability` 的显式 `SetStatus` 与 Go 一致）
- 逐条组件台账 ⇒ [`user/rpc.md` §11 `DRIFT-USERRPC-V-015`](user/rpc.md) · [`admin/rpc.md` §2.5 订正](admin/rpc.md)

> ⚠️ **2026-10-29 真实性复核（台账结论 ↔ 代码）· `DRIFT-USERRPC-V-015`**
> ⇒ 原判「**已域内修完 5 处**（🔴🟢）」⇒ **现行态 = ✅ 已修（维持）**。
> - **修法依据（Go 全仓 `SetStatus` 落点表）是否还成立**：**成立** ⇒ **实测（2026-10-29 逐条 grep）**：
>   | 断言 | 实测 |
>   |---|---|
>   | Go `service/user/`（user-rpc + admin-rpc）**零处**显式 `span.SetStatus` | ✅ **0 处**（`grep -rn "span.SetStatus\|\.SetStatus(" service/user/ --include="*.go"` ⇒ 0） |
>   | Go 全仓合计 **9** 处 | ✅ **9 处**（`service/comment/` 4 ＋ `service/common/` 3 ＋ `service/knowledge/` 2 ＋ `service/user/` 0） |
>   | RAG `service/users/**` 主源码**零处** `span.setStatus` | ✅ **0 处**（`grep -rn "setStatus(StatusCode\|span.setStatus" service/users/ --include="*.java"`，排除 `target/` 与 `src/test/` ⇒ 0）⇒ **那 5 处确已删净** |
> - ⇒ **「我域 Go 侧零处显式 `SetStatus` ⇒ 那 5 处是 RAG 独有 ⇒ 全部该删」的判据与结论都仍成立** ✔
> - **不改判的理由**：本条断言的是「**RAG 多置了 span 的 ERROR 状态（遥测面漂移），已删净**」——
>   两侧源码实测（Go 0 处 / RAG 0 处）**与该结论一致** ⇒ 状态维持 ✅。
> - ⚠️ **本轮未复跑** §59.8 的域级全量回归（需 6 模块 `mvn clean test`，本轮只改台账）⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」。

### 59.8 🟢 **本轮域级全量回归 + 条数重测（双路径逐模块相等）**

- **命令**：`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test`
- **结果**：**18/18 BUILD SUCCESS · EXIT=0 · 09:54 · 0 failures / 0 errors / 0 skipped**
  （⚠️ 比上轮 47:10 快很多 —— 本轮 Testcontainers 容器是热的，且**未复现**上轮那种 Docker 端口编程失败）

| 模块 | 路径 A（maven 模块级 `Results:` 行）| 路径 B（逐模块 surefire `TEST-*.xml` 求和）|
|---|---|---|
| user-api | 127 | **127** |
| user-rpc | **157** | **157** |
| admin-api | 149 | **149** |
| admin-rpc | 122 | **122** |
| follow-api | 74 | **74** |
| follow-rpc | 83 | **83** |
| **我域 6 模块合计** | **712** | **712** |

- **非目标** = `common` 333 ＋ `users-common` 39 ＋ `follow-common` 37 = **409**（与上轮一致 ⇒ 共享层本轮没长）
  ⇒ ⇒ `409 + 712 = 1121` = reactor 合计 ⇒ **逐字相等**
- **整倍数自检**：`1121 ÷ 712` **不整除** ⇒ ⇒ **排除「重复计数」**（过滤用 `-- in ` 双横线）
- **增量归因**：`707 → 712 = +5` ⇒ **逐条可指名** = `UserRpcSpanStatusGoParityTest` 的 **5** 条（①②③ 运行期 · ④ 源扫 · ⑤ 判据自检）⇒ ⇒ **没有余量**
- ⚠️ **口径**：`§54` 记的 **707** 是**那一轮**的快照（**不重写历史**）⇒ ⇒ **本轮起 = 712**；**任何跨 lane 引用的条数都是快照 ⇒ 必须带日期与 commit**

---

## 60. 🟢 **并入 G4 新尖端（4 笔）⇒ 零冲突 ＋ G4 自家门禁 PASS；⚠️ 顺带把一条【已被我域实测证伪】的断言带进了我树**（2026-10-28 · G1 lane）

- **合并对象**：`align/g4-service-common` @ `e22d2bd`（含 `590459e` / `352fb1b` / `f0567bb`）
- **合并结果**：合并提交 **`247bb63`** · **零冲突**
  （3 笔只动 `docs/DRIFT/global/vs-common.md`＝它自己的台账 · 1 笔给共享层
  `MybatisPlusOrderingBridgeAutoConfiguration` 补 javadoc（+25 行 · **零行为变更**）⇒ ⇒ **与 G1 写权面零重叠**）
- **合并内容复验**：用 **G4 自家的 `check-merge-tree-content.py`** 逐步验 ⇒ **PASS（0 问题 · 0 不可信）**

### 60.1 🔴 **带进我树的那条断言（G4 §2 的 A9 行 · `352fb1b`）—— 方向是反的，已订正**

> G4 台账原文：「Java `recordException` 不改 status ⇒ 域内 span 只调 `recordException` ⇒ status 恒 UNSET
> ⇒ **与 Go `RecordError` 漂移**（域内写权 ⇒ G3 自查）」

| 项 | 实测 |
|---|---|
| **错的** | 把【结论的方向】搞反了 —— Go 的 `RecordError` **同样不改 status**（otel-go SDK v1.44.0 `sdk@v1.44.0/trace/span.go:631-656` 注释原文：“**this method does not change the Span status**”）⇒ ⇒ **域内只调 `recordException` 正是与 Go 一致的形态，不是漂移** |
| **判据的真身** | 「该 span 在 Go 侧**有没有显式** `span.SetStatus`」⇒ Go 全仓落点 **9 处**（`comment` 4 · `common` 3 · `knowledge` 2）⇒ **`user`/`admin` 域 = 0 处** ⇒ ⇒ 我域那 5 处 `setStatus` 才是漂移（已修，见 §59）|
| **共享层不受影响** | Go `observability.go:83,105,128` 三处显式 `SetStatus` ⇒ RAG `Observability` 三处一一对应 ⇒ **共享层是对的 ⇒ 一行未动 ✔** |
| **我域的动作** | **不追改 G4 的台账**（`docs/DRIFT/global/**` = 它写权面）⇒ ⇒ **只在本域台账登记 ＋ 发信订正** ⇒ 已在 §59.6 登记（发信时 G4 = `error` 50001 ⇒ **排队 position 61**）|

### 60.2 🟢 **风险评估：漂移【尚未】进 G3 的树（两道证据）**

1. **G3 lane 自己早已独立查出真相**：它在自己的域内核到**同一处 SDK 源码**，并得出「Go 侧只 `RecordError` 的
   span，status 本就恒 UNSET ⇒ **域侧零改动**」⇒ ⇒ **G3 大概率没照 G4 加 `setStatus`** ⇒ ⇒ **favorite 域没有新漂移**
2. ⚠️ **但 G4 在 `352fb1b` 里又把它写了一遍 ⇒ 收口那一步有人照台账「修」favorite 的风险仍在**
   ⇒ ⇒ **已登记给合并收口那一步**：**收口前必须按本条判据复核 favorite 域的 `setStatus(StatusCode.*)` 命中数**
   （判据：Go 侧对应 span 有没有**显式** `span.SetStatus` ⇒ 没有 ⇒ Java 侧**不得**补）
   ⇒ ⇒ 判据与可复用探针 ⇒ G3 那封信（`mode=queued` · position 14）已给全。

---

## 61. 🟢 **G4 的一封【过期信】重放（写了约 8 小时后才到我这儿）⇒ 逐条核完 ⇒ 零动作**（2026-10-28 · G1 lane）

> 信的内容：① 让我 merge 它的**分支尖端 `c0ca71e`**（并说上一封的 `a38ad75` 已被取代）· ② 说新增两笔
> （`9037a2b` 修 `tools/check-ledger-selfcheck.py` 的层 4 死代码 · `c0ca71e` 记台账 §4.10.57）· ③ 说对我域影响 = 零
> · ④ 说 Docker 宿主→容器端口转发故障仍在 ⇒ 波末收口回归起不来 ⇒ 需 Jason / 合并收口执行者拍板
> · ⑤ 说「你那三件（Filter 槽位守门 / R1 / R3 / R4）本层零新增待办 ⇒ 等你回」
> **信的核对日期 = 2026-10-28 02:1x** ⇒ ⇒ **它在我这儿排队了约 8 小时才被派发**。

### 61.1 🟢 **锚点核验（回执也要对时）—— 三个目标提交早已在我树里**

| 信里说的 | 实测（`git merge-base --is-ancestor`）| 判定 |
|---|---|---|
| `a38ad75`（它说「已被取代」）| **∈ HEAD** ✔ | 更早的一笔 ⇒ 早已并入 |
| `c0ca71e`（它要我 merge 的「分支尖端」）| **∈ HEAD** ✔ | **早已并入** ⇒ **不需要再 merge** |
| `9037a2b`（`tools/` 落码）| **∈ HEAD** ✔ | 已在 ⇒ 它说「门禁输出只多 3 行自测断言 ⇒ 零计数影响」**实测成立**（我的 `check-ledger-selfcheck --domain users / users-admin / follow` 退出码与合入前逐字相同：0 / 0 / 1）|

⇒ ⇒ **G4 的当前分支尖端实测 = `e22d2bd`** ⇒ ⇒ **我落后 G4 0 笔 / 领先 97 笔** ⇒ ⇒ **本轮我已把它合到最新**（`247bb63` ＋ `fcd001c`）⇒ ⇒ **零动作**。

### 61.2 🟢 **逐条落点**

| 信里那条 | 落点 | 状态 |
|---|---|---|
| ① merge `c0ca71e` | 已在树 | 🟢 **零动作** |
| ② 对我域影响 = 零 | 那两笔不动 `service/**` | 🟢 实测成立 |
| ③ Docker 端口转发故障 ⇒ 波末收口起不来 | 需 Jason / 合并收口执行者拍板（**不擅自重启 Docker Desktop**）| 🟡 **已登记**（我域 C7 = pending · **不由我触发**）—— ⚠️ **但我域有反证，见 §61.3** |
| ⑤ 等我回 R1 / R3 / R4 + Filter 槽位守门 | 我 §57 ④ 已逐条销号（与我 §36 一致）· Filter 槽位守门早已交付 | 🟢 **已回**（那封在 G4 队列 position 61）|

⇒ ⇒ **不另发回信**（避免给 G4 队列加噪）⇒ ⇒ **登记即可**。
⇒ ⇒ ⚠️ **本条的教训是「回执也要对时」的又一个实例**：**一封信可以在写完约 8 小时后才到我这儿** ⇒ ⇒
**凡按信里写的 commit 号去动作之前，必查那个 commit 在不在我树里**（用 `is-ancestor`，不是「谁直接提的」）。

### 61.3 🟢 **给「Docker 端口转发故障 ⇒ 波末收口起不来」提供一条【我域反证】**

- G4 在信里写：「Docker 宿主→容器端口转发故障仍在 ⇒ ⇒ **本层全量依赖 Testcontainers 真 PG / Redis ⇒
  在它修好之前本层不起全量** ⇒ ⇒ **波末收口回归在此之前起不来**」
- **我域实测（2026-10-28 10:07–10:16）**：`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test`
  ⇒ **18/18 BUILD SUCCESS · EXIT=0 · 09:54 · 0f/0e/0s**
  ⇒ 其中**确实用了**真 Testcontainers：5 份 FastFail 守门起 **`redis:7-alpine`** 容器 · user-rpc / admin-rpc 的真 PG 用例
  ⇒ ⇒ **宿主→容器转发在那个时段是通的**
- ⇒ ⇒ **该故障是【间歇性】的，不是持续阻塞** ⇒ ⇒ **「波末收口回归在此之前起不来」这个结论，至少对我域不成立**
  ⇒ ⇒ **对波末排期的含义**：**收口那一步不必等 Docker 被修**（可以照常起回归，撞上再按环境类失败处理）
  ⇒ ⇒ ⚠️ **但这只是我域 6 模块的一次样本** ⇒ ⇒ **不能推广成「Docker 没问题」** ⇒ ⇒ 共享层 52 模块全量的
  容器数远大于我域 ⇒ ⇒ **该结论归 G4 自己在它的窗口里复核**（我域只提供这一条样本，不代它下结论）。

---

## 62. 🔴🟢 **G4 撤销「Docker 转发故障」（它的探针自己坏了）＋ 🔴 但它那条「前置已满足」是按【过期尖端】判的 ⇒ 波末那棵树还不存在**（2026-10-28 · G1 lane）

### 62.1 🟢 **G4 已自行撤销「Docker 宿主→容器端口转发故障」⇒ §61.3 的反证被它自己确认**

- **G4 的认错**：它的探针发的是**单字节垃圾 `\x00`**，而 **PG 不理垃圾**（它等的是一个合法的
  `SSLRequest`/`StartupMessage`）⇒ ⇒ **超时发生在「读」上，不在「连」上** ⇒ ⇒ `create_connection` 早就成功了
  ⇒ ⇒ **它把「读超时」读成了「连不上」** ⇒ ⇒ **一条不存在的机器级阻塞**
- **它自己的终态实测**：**55/55** 已发布容器端口 TCP 可连 · **30/30** PG/Redis 容器**协议应答正确**
  （PG 发 SSLRequest ⇒ `b'N'`；Redis 发 `PING\r\n` ⇒ `b'+PONG\r\n'`）⇒ ⇒ **Docker 转发健康**
  ⇒ ⇒ **不需要任何 Docker 处置** ⇒ ⇒ ⚠️ **别重启 Docker Desktop**（会白杀各 lane 跑了 30+ 小时的 `ddl-*-pg`）
- ⇒ ⇒ **我 §61.3 给的反证成立且已被它确认** ⇒ ⇒ **C7 收口回归不必等 Docker 被修**
- 📌 **纪律（已在 memory 里，G4 这次正好踩了）**：
  ① **探针要先证伪自己** —— 用同一探针连一个**已知可用的对端**；② **分清 connect 超时 vs read 超时**
  （`create_connection` 成功就说明**连上了**）；③ **协议服务用它自己的协议探**（PG 发 SSLRequest / Redis 发 `PING\r\n`）
  ⇒ ⇒ 「发了字节没回」≠「不通」，可能是「我发的不是它要的东西」；④ **凡据此提议【破坏性处置】的结论，必须有第二个独立探针交叉验证**
  ⇒ ⇒ ⚠️ 这条**早就写进 memory**（另一 lane 先前踩过）⇒ ⇒ **又一个「已记录的坑被重新踩一遍」的实例**。

### 62.2 🔴 **G4 说「第 15 次全量的前置已满足（三条 lane 都并入本层分支【尖端】`b79779e`）」⇒ 【当前尖端上不成立】**

| 事实 | 实测（`git merge-base --is-ancestor`）|
|---|---|
| G4 当前分支尖端 | **`e22d2bd`**（信里写的 `b79779e` 是**更早的**尖端 ⇒ **信是按过期尖端写的**）|
| G2 当前尖端 `9ef793f`（标题：「第二十五轮 · **波末收口回归【实测 9 模块 = 1030】· 全绿**」）| **∉ `e22d2bd`** ✘ |
| G3 当前尖端 `ee8dc41`（标题：「merge(g4-service-common): 并入 G4 尖端 `e22d2bd`」）| **∉ `e22d2bd`** ✘ |
| 我域当前尖端 `8790823`（含 span 修法 `0c6ae20`）| **∉ `e22d2bd`** ✘ |
| 四棵 lane 尖端**互不包含**（6 组两两 `is-ancestor` 全为「否」）| ⇒ ⇒ **波末那棵「四 lane 合并树」目前还不存在** |
| 主干 `do-my-own-gh` | 仍是 `c9cf2c1`（2026-10-27 23:58）⇒ ⇒ **我域 211 笔 / G2 / G3 都没进主干** |

⇒ ⇒ **所以**：G4 若**现在**起第 15 次全量 ⇒ ⇒ 跑的是**不含 G2 的波末结果、不含 G3 的并入、不含我域 span 修法**的树
⇒ ⇒ **那个「全量」不能代表波末状态** ⇒ ⇒ **必须先合三条 lane 的当前尖端，再起**。

### 62.3 🟢 **合流就绪矩阵（我做的干跑，零改动：不 move ref、不动工作区）**

命令：`git merge-tree --write-tree <A> <B>`（只产出 tree OID 与冲突列表，**不产生提交、不改任何 ref**）

| 配对 | 结果 |
|---|---|
| G1 `8790823` ⊕ G4 `e22d2bd` | ✅ 干净（tree `5e1c2c01…`）|
| G1 `8790823` ⊕ G2 `9ef793f` | ✅ 干净（tree `c3ecddbb…`）|
| G1 `8790823` ⊕ G3 `ee8dc41` | ✅ 干净（tree `00c6e137…`）|
| G4 `e22d2bd` ⊕ G2 `9ef793f` | ✅ 干净（tree `5b3a6344…`）|
| G4 `e22d2bd` ⊕ G3 `ee8dc41` | ✅ 干净（tree `a788db36…`）|
| G2 `9ef793f` ⊕ G3 `ee8dc41` | ✅ 干净（tree `aa5b8288…`）|

⇒ ⇒ **6/6 干净 ⇒ 四棵树合流不会卡在冲突上** ⇒ ⇒ 波末剩下的**只是「谁来合」+「合完跑全量」**，不是技术冲突
⇒ ⇒ **这不归我**（落点 = 合并收口执行者 / 主干合并那一步）⇒ ⇒ **我只提供这份就绪矩阵 + 上面那 3 条实测命令**
⇒ ⇒ ⚠️ **我域 C7 的触发条件不变**：**待四 lane 全合主干**（`do-my-own-gh`）· **不由我触发**

---

## 63. 🟢 **G2 的「Testcontainers 固定宿主口（层 2）」提示 ⇒ 我域【早已落码】⇒ 逐条核完 ⇒ 零改动**（2026-10-28 · G1 lane）

> G2 来信：① 回答 G4 §4.10.39 ② 的「未实测项」⇒ 答「**会**」（`getMappedPort()` 返回那个固定口）
> ② 三个会让域侧**直接编译红 / 假红**的坑（绑口要先摘随机绑定 / 泛型签名编译不过 / javadoc 里 `src/test/java/**/*.java` 提前闭合注释）
> ③ 可直接抄的 `RealContainerPort` ＋ 两层守门 ④ 提醒「层 1 治理过 ≠ 层 2 治理过 ⇒ 请核自己域的 rpc 侧真容器类有没有绑口」
> ⚠️ 信里引用的基线是它自己的 `caba808` ＋ G4 的**旧尖端 `a38ad75`** ⇒ **内容有效、坐标过期**。

### 63.1 🟢 **我域实况：层 2 机制【早已落码】且三层守门齐全 ⇒ G2 的三个坑我域全部避开**

| 项 | 我域实况（实测）|
|---|---|
| 支撑类 | **6 份 `GoStyleHostPortPinning`**（`user-api` · `user-rpc` · `admin-api` · `admin-rpc` · `follow-api` · `follow-rpc` 各一份，位于各模块 `src/test/.../support/`）|
| 段常量 | **users 4 份 = `22700–22729`** · **follow 2 份 = `22730–22759`** ⇒ ⇒ **与 G4 §4.10.19.3 / §4.10.39.2 的分配逐字一致** ✔ |
| 越段行为 | 段内无空闲口 ⇒ **抛 `IllegalStateException("分配段 22700-22729 已无空闲宿主口")`** ⇒ 与 G2「端口越段直接抛异常」同形 ✔ |
| 绑口手法 | **`withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 5432))`** ✔ 与 G2 说的形态一致（`FixedHostPortGenericContainer` 我域**未**用）|
| **坑 1（先摘后加）** | ✅ **我域已避开** —— `pinContainerPort` 把 `hostConfig.getPortBindings()` 的**旧绑定逐条搬进新 list**（`for (ExposedPort, Binding[]) …`）⇒ **摘掉该容器口的随机绑定** ⇒ 再 `add(new PortBinding(Ports.Binding.bindPort(hostPort), ExposedPort.tcp(containerPort)))` ⇒ 最后 `hostConfig.withPortBindings(merged)`（`withPortBindings` 是**整体替换** ⇒ 必须整体搬）|
| **坑 2（泛型签名）** | ✅ **我域已避开** —— 绑定方法是**非泛型**：`public static void pinContainerPort(CreateContainerCmd cmd, int containerPort)` ⇒ **压根不碰 `GenericContainer<?>` 的通配符捕获** ⇒ 编译红不可能发生 |
| **坑 3（javadoc 里 `src/test/java/**/*.java`）** | ✅ **我域零命中**（全域 grep `src/test/java\*\*/` ⇒ 0）⇒ ⇒ 注释提前闭合 ⇒ 「非法字符」这个坑我域没有 |

### 63.2 🟢 **三层守门（机制层 + 覆盖层 + 副本等价）⇒ 实跑全绿**

| 守门 | 断言 | 本轮实跑 |
|---|---|---|
| `UserDomainHostPortPinningGateTest`（users 域）| ① **本域每个容器起点都带显式绑口 modifier**，且钉的是**该容器自己的容器口** · ③ 各模块 helper 的段**落在 G4 分配的 users 段内**且**与 OS 临时段 `49152–65535` 不相交** · ④ **4 份 helper 副本逐字等价**（只允许 package 与段常量不同）⇒ 防副本漂移 | ✅ **3 条绿** |
| `UserRpcTestcontainersHostPortSegmentGateTest` | **钉口探针**（真容器）：`getMappedPort() == 那个固定口` ＋ 落在分配段内 ＋ **真连得通**（JDBC `SELECT 1`）＋ `jdbcUrl` 含该固定口 | ✅ **1 条绿** |
| `FollowDomainHostPortPinningGateTest`（follow 域）| 同上（follow 段）| ✅ **3 条绿** |

⇒ ⇒ **这正是 G4 §4.10.39 ② 那个「未实测项」** ⇒ ⇒ **我域已独立实测 = 「会」**（`getMappedPort()` 返回固定口）
⇒ ⇒ ⇒ 这是**第二个 lane 的独立测量**（G2 域 = message/like/task 三域各自测；我域 = users/follow）⇒ ⇒ 结论一致
⇒ ⇒ **共享层 `service/common` 是 G4 写权面 ⇒ 我域零动作**（我的守门第 44 行已写明这一点）

### 63.3 🟢 **G2 的 ④ 提醒已核：我域的 rpc 侧真容器类【全部】已绑口**

- **按声明数**（G2 强调的数法：数 `new PostgreSQLContainer<>` / `new GenericContainer<>` 的**声明**，不算 import / javadoc 里的类型名）：
  **我域 = 52 处容器声明**（users 48 + follow 4）
- **覆盖层守门 ① 全绿** ⇒ ⇒ **52 处容器起点全部带显式绑口 modifier** ⇒ ⇒ **无遗漏**
- ⇒ ⇒ **G2 说的「不是只有新写的探针要改，是全域真容器都要改」这一条，我域早就是全域都改了**

### 63.4 📌 **两点给 G4 的（我无权改它的台账 ⇒ 登记 + 转告）**

1. 🔴 **G4 §4.10.19.3 段表那行写「🟡 本层预分配 · **域侧未落码**」⇒ 【已过期】** ——
   **我域已落码**（6 份 helper ＋ 三层守门，段号与它分配的逐字一致）⇒ ⇒ **收口那一步重算共享面快照时，别把「域侧未落码」当现状**
2. 📌 **命名口径差异（免得跨 lane 引用对不上）**：G2 域用 `RealContainerPort` ＋ `XxxPostgresFixedHostPortGateTest` ＋ `XxxContainerFixedHostPortSegmentGateTest`；
   **我域用 `GoStyleHostPortPinning` ＋ `UserDomainHostPortPinningGateTest` / `FollowDomainHostPortPinningGateTest` ＋ `UserRpcTestcontainersHostPortSegmentGateTest`**
   ⇒ ⇒ **机制同形、命名不同** ⇒ ⇒ 引用时按【域 + 文件名】指，不要按类名猜
3. **我域零改动** ⇒ 本条**不产生任何落码** ⇒ ⇒ **零派单**

---

## 64. 🟢 **让出 maven 窗口给 G4 的第 15 次全量 ＋ 🔴 但它那棵合并树用的是【四个过期尖端】⇒ 附「重做零冲突」实测收据**（2026-10-28 · G1 lane）

### 64.1 🟢 **窗口：已让（40 分钟内我域不起 maven）**

- G4 请求独占 maven 窗口约 40 分钟（起第 15 次全量 · 波末收口那一次）⇒ ⇒ **我域照办**
- 我域**当前没有待跑的批次**（域级全量 10:07–10:16 已跑完 18/18）⇒ ⇒ **不存在 EXIT=137 的互相污染风险**
- 我也**不碰它的合并树** ⇒ ⇒ 纯让窗口，零动作

### 64.2 🔴 **它建的合并树：四个尖端【全是过期尖端】⇒ 树里没有我域的【生产代码改动】**

| lane | 它用的 | 当前实测 | 差 |
|---|---|---|---|
| G1 | `929223e` | **`bee69c5`** | **48 笔** |
| G2 | `caba808` | **`9ef793f`** | **24 笔** |
| G3 | `11f96dd` | **`9ef9800`** | **87 笔**（它在这封信发出后**又**动了一次 ⇒ 任何「几分钟前的尖端」都会立刻过期）|
| G4 | `b79779e` | **`e22d2bd`** | **14 笔** |

- 🔴 **其中有一笔是生产代码，不是台账**：`0c6ae20`（`DRIFT-USERRPC-V-015` ⇒ 删 user-rpc 4 处 + admin-rpc 1 处
  `span.setStatus(StatusCode.ERROR, …)`）⇒ **∉ `929223e`** ⇒ ⇒ **那棵树里的 user-rpc / admin-rpc 跑的是改动前的代码**
  ⇒ ⇒ **那个「全量绿」不覆盖当前生产代码** ⇒ ⇒ **回执里必须写明它跑的是哪棵树**
- ⇒ ⇒ 另两笔只在我树 ⇒ ⇒ **重做树时自然带上**：`312a09b`（Redis 收编：删 5 份域内副本 ⇒ **它现在清白名单会假红**）
  与 `0c6ae20`（span 状态）⇒ ⇒ **`312a09b` 只动 `service/users/**` + `service/follow/**` + 我域台账 ⇒ 零共享层路径 ⇒ cherry-pick 零冲突**

### 64.3 🟢 **「重做一遍」的成本实测：当前尖端 6/6 两两干净 ＋ 四路链式合并零冲突**

**命令口径**：纯 git（`git merge-tree --write-tree` + `git commit-tree`）⇒ **不 move ref、不动工作区、不占 maven 窗口**

| 配对（当前尖端）| 结果 |
|---|---|
| `bee69c5` ⊕ `e22d2bd` | ✅ 干净（tree `571cf015d40d`）|
| `bee69c5` ⊕ `9ef793f` | ✅ 干净（tree `2ff614c4de33`）|
| `bee69c5` ⊕ `9ef9800` | ✅ 干净（tree `ae8394195c7b`）|
| `e22d2bd` ⊕ `9ef793f` | ✅ 干净（tree `5b3a6344c0fb`）|
| `e22d2bd` ⊕ `9ef9800` | ✅ 干净（tree `bc60488ed17c`）|
| `9ef793f` ⊕ `9ef9800` | ✅ 干净（tree `73014cf7a0f5`）|

- **四路链式合并（当前尖端）**：`rc=0` ⇒ **最终 tree `9ce55bdf25e4`** ⇒ ⇒ 且该 tree **含 `UserRpcSpanStatusGoParityTest`**
  ⇒ ⇒ **含 `0c6ae20`** ⇒ ⇒ **重做一棵树不用等冲突解决，命令几分钟能跑完**
- ⇒ ⇒ **给 G4 的建议（决策归它）**：**先重做、再花那 40 分钟** ⇒ 比在过期树上花 40 分钟划算
  ⇒ ⇒ ⚠️ **我只是给收据 + 建议，不代它决策、不动它的树**

### 64.4 📌 **两条随树变的不变量（别把旧树的数字搬进新树）**

1. `check-ledger-count` 的「索引称 315 / 实测 329」是**旧树**的数 ⇒ ⇒ 我域这轮新增 §57–§63 七节 ⇒ ⇒
   **重建后差值会变** ⇒ ⇒ 「既有债、归 SHARED 面」的**结论不变**，但**数字必须按新树重算**
2. `check-wave-coupling` 的 10 项（`common_radiation`）同理 ⇒ ⇒ 归 `docs/DRIFT/_cross/cross-domain-coupling.md` = **SHARED 面**
   ⇒ ⇒ **我域不代改**

### 64.5 🔴 **给自己留的收口义务（不可省）**

> **G4 的第 15 次全量回执到达时，必须核它【实际跑的是哪棵树】**（`git rev-parse <tree>` 或回执里标的 tip）
> ⇒ ⇒ **若那棵树不含 `0c6ae20` ⇒ 该回执的「全量绿」【不覆盖当前生产代码】** ⇒ ⇒ 必须在台账里写明
> ⇒ ⇒ **回执也要对时**（本轮第 3 次撞上同一形态：G4 的 3 封信标的尖端全部过期）

### 64.6 🔴 **自捕第四次「`ps` 判据自匹配」—— 我在【核自己有没有占窗口】时就踩了**

- 我用的判据：`ps -Ao pid,command | grep "[R]AG.worktrees/g1-users-follow" | grep maven | wc -l` ⇒ ⇒ **输出「2」**
- **真相**：那 2 行**都是我自己的 `bash -c` 与 `grep`** —— 判据的命令行里**同时含路径字面量与 `maven` 字样**
  ⇒ ⇒ **它把自己数了进去** ⇒ ⇒ 「2」是**假的**
- **正确判据**（本次已用它复核）：只看**真 maven 进程**（`[p]lexus.classworlds` / `[s]urefirebooter`）且**排除自身**
  ⇒ ⇒ 实测：**我树 0 个 maven 进程** ✔ ⇒ ⇒ **窗口确已让出**；同时实测到 **G4 的全量在跑**（pid `34244`）
- ⚠️ **这已是本会话第 4 次撞上这一族**（我域 §55.8「命令替换覆盖 `$?`」· G4 的等待脚本 5.7 小时自匹配 · G4 刚才认错的自匹配 · 本条）
  ⇒ ⇒ **判据的命令行里出现被匹配的串 ⇒ 它就会匹配自己** ⇒ ⇒ **占位类判据必须写成「只认进程类名 / 用括号技巧 / 写进独立脚本」**

---

## 65. 🔴 新立 5 条：**「OS 盲选口」族**（A/C/D 三形态 · 我域 **21 处**）⇒ 已修 ＋ 新守门 5 条（2026-10-28 · G1 lane）

**新立条目**：`DRIFT-USERRPC-V-016`（user-rpc）· `DRIFT-USERAPI-017`（user-api）· `DRIFT-ARPC-014`（admin-rpc）· `DRIFT-ADMINAPI-013`（admin-api）· `DRIFT-FOLLOW-013`（follow 域）

### 65.1 起因（G2 的波级报点 ⇒ 但**口径不全** ⇒ 我域自核后修正）

G2 在 2026-10-28 的 r30 全量实测踩中「客户端连上了邻居的协议」（收到 Redis 的 `-ERR ` 前缀，而不是 gRPC 的 SETTINGS 帧），并给出波级报点：全仓 `forPort(0)` 真实代码 **34** 处，其中 **G1 域 9** 处。

**本 lane 自核（逐文件 grep ＋ 逐条核对）⇒ 差 2 处**：

| 组件 | G2 报点 | **G1 自数** | 差 |
|---|---|---|---|
| follow | 2 | **3**（follow-api 1 ＋ follow-rpc 2） | **+1** |
| user | 3（只算 user-rpc） | **4**（user-rpc 3 ＋ user-api 1） | **+1** |
| admin-rpc | 3 | **3** | 0 |
| admin-api | 1 | **1** | 0 |
| **合计** | **9** | **11** | **+2** |

⇒ 漏的两处都是 **`NettyServerBuilder.forPort(0)`**（`FollowApiStartupFailFastTest:253` ＋ `UserApiStartupFailFastTest:253`）
⇒ ⇒ **按 `ServerBuilder.forPort(0)` 文本搜会漏掉 `NettyServerBuilder.forPort(0)`** ⇒ 已回报 G2（波级复扫建议两种前缀都吃）。
⇒ ⚠️ **这正是「不采信转述、自己重数」的又一处实例**：转述数字不是错的，是**口径漏了一种写法**。

### 65.2 🔴 自扫同族 ⇒ 我域不止 A 族（**三族 21 处**）

| 族 | 写法 | 我域处数 | 有没有客户端连上去 | 危害形态 |
|---|---|---|---|---|
| **A** | `forPort(0)` | **11** | **有**（测试自己起 server 再连回去） | **静默被抢** |
| **C** | `new ServerSocket(0)` | **8** | 间接（要「没人监听」这个**前提**） | **前提直接失效** |
| **D** | `RANDOM_PORT`（Tomcat） | **2** | **有**（`@LocalServerPort` ＋ 真 HTTP client） | **静默被抢** |
| B | `grpc.server.port=0` | 24 | **无**（我域**零** `@LocalGrpcPort` 用法） | 无（被抢也无所谓）⇒ **不落码**，已报 G4（段表口径缺口） |

⇒ ⇒ **A ＋ C ＋ D = 21 处**全部落在**我域写权面内**，且**全部只改测试代码**
⇒ ⇒ **我域生产代码零改动** ⇒ ⇒ **不构成 Go 对照项**（Go 侧无对应物 ⇒ 无对照义务，见 §65.7）。

### 65.3 🔴 机制（**本机实测 · 真 jar 跑出来 · 非推理**）

探针：`/tmp/g1_port_probe/Probe.java`（Q1–Q5）＋ `Probe2.java`（Q6–Q8）· classpath 取自
`mvn -o -pl service/users/user/user-rpc dependency:build-classpath`（**真 `grpc-netty-shaded`**）。

| # | 场景 | 实测结果 | 含义 |
|---|---|---|---|
| Q3 | 邻居占**通配** `0.0.0.0:P` ⇒ 我方 `NettyServerBuilder.forPort(P)` | **FAILED**：`BindException: Address already in use` ／ `Failed to bind to address 0.0.0.0/0.0.0.0:P` | 通配撞通配 ＝ **响亮失败**（好查） |
| **Q6** | 邻居占**具体** `127.0.0.1:P` ⇒ 我方 `forPort(P)` | **SUCCESS**；`lsof` 显示**两个监听者并存**：`TCP 127.0.0.1:P (LISTEN)` ＋ `TCP *:P (LISTEN)` | ⇒ **危害方向 ＝ 更具体的地址** ⇒ 客户端连 `127.0.0.1:P` **命中更具体的那个** ⇒ 请求打到邻居 ⇒ **静默假红** |
| Q8 | 只探 `0.0.0.0:P`（邻居占 `127.0.0.1:P` 时） | **SUCCESS（检不出）** | ⇒ 通配探针**不够** |
| Q7 | 探 `127.0.0.1:P`（同上） | **FAILED BindException（能检出）** | ⇒ 必须**双地址**探针 |
| Q4 | `forPort(0)` 实取口 | **52934**（落在 **49152–65535** 内） | ⇒ 与 Docker 随机发布口**同段** |

⇒ ⇒ **两句结论**：
1. **危害方向是「更具体的地址」，不是「通配」** ⇒ ⇒ **只探通配的端口选取类挡不住真正的危害形态**
   ⇒ ⇒ 已提醒 **G2**（`GrpcTestPort`）与 **G4**（共享层 `RealTomcatPort` / `RealGrpcPort`）复核；
2. **本机 Docker 发布口是「混」的**：`com.docke` 同时持 `*:port`（通配）**与** `127.0.0.1:port`（具体）⇒ ⇒ **两类都要防**。

### 65.4 修法（域内自闭合 · **共享层零改动**）

**① 段**：用**共享层已预分配**给本域的段（§4.10.19.3）⇒ **不改共享层台账**（那在 G4 写权面内）：

| 组件 | 类 | 段 | 段来源 |
|---|---|---|---|
| user-api | `RealGrpcPort` / `RealTomcatPort` | 22200–22209 / 22210–22219 | users 域 22200–22299 |
| user-rpc | `RealGrpcPort` | 22220–22239 | 同上 |
| admin-api | `RealGrpcPort` / `RealTomcatPort` | 22240–22249 / 22250–22259 | 同上 |
| admin-rpc | `RealGrpcPort` | 22260–22279 | 同上 |
| follow-api | `RealGrpcPort` | 22300–22319 | follow 域 22300–22399 |
| follow-rpc | `RealGrpcPort` | 22320–22339 | 同上 |

（22280–22299 ／ 22340–22399 ＝ 域内预留 ⇒ 守门拿它当「脚手架段」，**不与生产段抢**）

**② 类形状**：沿用 **G3 域 comment-rpc 的 `support/RealGrpcPort`**（段内挑口 ＋ **双地址 bind 探针** ＋ `lsof` 诊断 `holders()`）⇒ 跨域形状一致 ⇒ 波末好统一。
⚠️ **不引用 G2 域的 `GrpcTestPort`**（跨域不可见 ＋ 共享层只读）⇒ **域内私有副本**。

**③ 21 处调用点**：
- A 11 处：`forPort(0)` ⇒ `forPort(RealGrpcPort.pick())`
- C 8 处：`new ServerSocket(0)` 探针 ⇒ `RealGrpcPort.pick()`（段内空闲口 ⇒ **同样满足「没人监听」**，且不会落到 Docker 已发布口上 ⇒ **前提不失效**）
- D 2 处：`RANDOM_PORT` ⇒ `DEFINED_PORT` ＋ `@DynamicPropertySource` 注册 `server.port = RealTomcatPort.pick()`（G3 favorite-api 同款）

### 65.5 新守门 `BlindPortGateTest`（user-rpc · **5 条**）

| # | 断言 | 判别力来源 |
|---|---|---|
| ① | 我域 6 模块 `src/test/java` **零**盲选口（A/C/D 三形态） | **剥注释后**再扫；并断言**扫过文件数 ≥ 100** ＋ 6 个模块都被扫到 ⇒ 「零命中」不可空转 |
| ② | 8 个端口类的段落在**本域预分配段**内 ＋ **子段两两不重叠** ＋ 不在 49152–65535 ＋ 不撞 Testcontainers 段 22700–22999 | 源扫 8 个类的 `FIRST`/`LAST` 常量 ＋ 两两区间比对 |
| ③ | 挑到的口**真能起 gRPC server** 且客户端 **READY** | 真起 server ＋ 真 channel（**不是 mock**） |
| ④ | **判据自检**：造假源码含 `forPort(0)` ⇒ 扫描器必须报出；**注释里的字面量必须不报** | 「零命中是假信号」的解药 |
| ⑤ | **探针判别力**：占住 `127.0.0.1:P` ⇒ `isFree(P)` 必须判为**不可用** | 这正是「静默被抢」的检出点（Q7） |

### 65.6 📌 顺手记一个**自踩**：改完之后源扫报「17 处残留」

改造后 `grep forPort(0)` 报 **17**、`grep new ServerSocket(0)` 报 **8**
⇒ ⇒ **剥注释后实得 0** ⇒ ⇒ **那 17 / 8 全是我自己写进注释里的字面量**（每处改动都配了「为什么不能这么写」的注释）
⇒ ⇒ **守门若不剥注释，就会对着 javadoc 假阳性** ⇒ ⇒ ①⑤ 一律按「剥注释后」实现。

### 65.7 效力边界（如实登记，不夸大）

- 本节只覆盖**我域 6 个模块的测试树**；`service/common/**` 与 `service/users/users-common/**` 是**共享层**（写权归 G4）⇒ **不纳入本守门**。
- **我域生产代码零改动** ⇒ 不影响任何对外行为 ⇒ **不构成 Go 对照项**（Go 侧无对应物）。
- 「B 族 24 处不落码」的依据是**我域的实测事实**（零 `@LocalGrpcPort`）⇒ **不外推到别的域**。
- 段号取自**共享层已预分配**的段 ⇒ 共享层段表那两行「🟡 预分配 · 域侧未落码」**需要 G4 更新**（已报）⇒ **我域不改共享层台账**。
- 机制结论来自**本机 macOS** 的 socket 语义实测 ⇒ ⇒ **不外推到别的 OS**（Linux 的「更具体地址优先」语义需另测）。

### 65.8 附带：清理 `/tmp/difftest5k` 的**我域 8 个孤儿进程**

`etime` ＝ **1 天 14 小时 50 分**、全部 `ppid=1`（孤儿）⇒ 8 个是我域组件（go-user-rpc / go-admin-rpc / go-user-api / go-admin-api ＋ java user-rpc / admin-rpc / user-api / admin-api）
⇒ ⇒ **本 lane 处置权内** ⇒ ⇒ SIGTERM 后 4 个 java 进程**未退** ⇒ 补 SIGKILL
⇒ ⇒ **终态：我域 8 个全清，12 个口释放**（64210 / 63081 / 64900 / 63889 / 24004 / 24094 / 23081 / 24097 / 24095 / 23888 / 24096 / 23889）
⇒ ⇒ **G2 的 3 个 message 侧进程原封未动**（非我域 ⇒ 不碰）。

## 66. 🟢 **「受保护名单」登记：全域 19 处 U+FFFD ＝ **题材 / 证据表 / 被测对象** ⇒ 机械替换会一次改坏 19 处**（2026-10-28 12:3x · G1 lane）

### 66.1 🔴 **先订正一条**（G2 来信 · 我域独立核实后接受）

G2 早前说「你们台账引述旧乱码的 13 处必须与源同批改」⇒ ⇒ **那条说错了** ⇒ ⇒ 实测真相：
**我域的源早已修好并提交**（`0c2a902`「域内收口（第十四轮）」）⇒ ⇒ **别的树上还看得到那 8 处**
（= **还没合并**，**不是待办**）⇒ ⇒ ⇒ **我域台账里那 13 处 U+FFFD 不是过期引述，
而是「损坏形态 → 重建」的【证据表】** ⇒ ⇒ **不许动**（动了 = 把证据删了）。

⇒ ⇒ **我域 U+FFFD 任务 = 已完成，零待办**（我域基线实测 = **5 份 / 15 字节**，
与 §65.6 的全树基线 **15 份 / 41 字节** 一致 ⇒ ⇒ **本轮改动零新增**）。

### 66.2 🟢 **全域「受保护名单」= 19 处**（**整合者/波末合并必须显式保护**）

| 类别 | 处数 | 位置（**我域 15 处逐处已核**）|
|---|---|---|
| **题材 / 被测对象** | **6** | **他域 4 处**：`docs/Pitfalls/message/rpc.md`（引述 bash 报错原文 2 处）· `docs/Pitfalls/users/user/rpc.md:146`（引述 GBK 有损解码 ⇒ **它证明的正是「末字已不可恢复」**）· `ArticleApiMvcPipelineTest.java` 两处（**测试载荷本身就是 U+FFFD** ⇒ 改了找不到 ⇒ 假红）<br>**我域 2 处**：`UserApiGoStyleBodyContentTypeTest.java:377`（那个「断言正文里不得含替换字符」的断言 ⇒ **它是被测对象** ⇒ 改了这条断言**永远为真** ⇒ **静默失效**）· `docs/Pitfalls/users/user/rpc.md:146` |
| **台账的「损坏形态 → 重建」证据表** | **13** | **全在我域**：`docs/DRIFT/users/user/api.md` **7**（`:3467` ×3 · `:3468` ×2 · `:3472` · `:3473`）· `docs/DRIFT/users/user/rpc.md` **4**（`:2503` ×3 · `:2505`）· `docs/DRIFT/users/vs-common.md` **2**（`:1403` · `:1656`）⇒ ⇒ **证据表不许动** |

⇒ ⇒ **19 处全部「不许改」** ⇒ ⇒ G2 侧**已同步 G4 / G3** ⇒ ⇒ **我域再登记一份**
（免得波末合并时只按「扫到的就修」处理）。

⚠️ ⇒ ⇒ **判据（别写成「扫到 U+FFFD 就替换」）**：**必须先分类** ——
**题材 / 证据表 / 被测对象**（**不许动**）vs **真损坏**（**该修**）⇒ ⇒
**真损坏只剩他域那 4 处**（= 各域自己的树里已修好 ⇒ **合并即消失**）⇒ ⇒ **我域零待办**。

### 66.3 ⚠️ **行号必须带「树」限定**（G2 提醒 · 我域独立复现）

同一处物理位置（`UserApiGoStyleBodyContentTypeTest.java` 里那个**被测对象**断言）：
**我树 `:377`** · G2 / G3 / G4 树 **`:376`** ⇒ ⇒ **差 1 行**（users 域该文件在**我树**多一行）
⇒ ⇒ ⇒ **台账引用必须写明按哪棵树**，否则会把「**行号漂移**」误判成「**引用错误**」
⇒ ⇒ **我域本文件所有行号均以 `align/g1-users-follow` 为准**。

⇒ ⇒ **这给「回执也要对时」补了一条新形态**：**跨树比行号 ⇒ 必须同文件逐行比，
不能拿单树的行号去套别的树**（G2 核对四棵树时就是这么做的 ⇒ 它的结论可用）。

### 66.4 🔴 **自捕：我在回信里照抄 G2 的「G4 刚提交 `d212323` ⇒ 你落后 1 笔」⇒ 实测我落后 0**

- **我写进回信的原话**：「G4 尖端已到 `d212323` ⇒ ⇒ **我域落后 G4 已 > 0** ⇒ ⇒ 下一轮开工第一步 = 并入 G4 尖端」⇒ ⇒ **这句话作废**。
- **实测（`git merge-base --is-ancestor` 传递闭包核，不用「谁直接提的」判）**：
  | 提交 | 是否在我树 HEAD 内 |
  |---|---|
  | `d212323`（G4 · `fix(common)` 降载 RT 整型截断 ⇒ 浮点毫秒 + ceil · **2026-10-28 02:49**）| **是 ✔** |
  | `590459e`（G4）| **是 ✔** |
  | `e22d2bd`（G4 尖端）| **是 ✔** |
  ⇒ ⇒ **`git rev-list --count HEAD..align/g4-service-common` = 0** ⇒ ⇒ **我落后 G4 = 0 笔**
  ⇒ ⇒ **`git branch --contains d212323` 显示四个 lane 分支【全部】包含它** ⇒ ⇒
  **`d212323` 早就随之前那几笔 G4 并入进了我树**（我树在它之后又有 117 笔自己的提交）。
- ⇒ ⇒ **结论保留**：**我域 §65.9 那一轮 717 跑的共享层【已经包含】`d212323`** ⇒ ⇒
  **那个数仍权威**（不需要重跑）⇒ ⇒ **「并入 G4 尖端」这条动作本轮无需执行**。
- ⇒ ⇒ **教训（与 §64.6 同一族）**：**我又一次把「转述里的一句现成结论」直接写进了回信** ⇒ ⇒
  **代价**：若不核，我就会在回信里给 G2 一个**错的「你落后 1」**⇒ ⇒ 而 G2 会据此判断我的数是否过期
  ⇒ ⇒ **「不采信转述」必须落到【自己动手核】这一步**，不是口号。
  ⇒ ⇒ **判定式**：**回信里凡出现「某人的树落后/领先 N 笔」这种可机械核的数 ⇒ 落笔前必须自己跑一次
  `git rev-list --count` / `git merge-base --is-ancestor`** ⇒ ⇒ **数是最好核的一类事实** ⇒
  **不核它没有理由**。
- ⚠️ **另记（G4 侧未提交面 · 非我域）**：`g4-service-common` 工作树里有一个**未跟踪**的新文件
  `service/common/src/test/java/com/rag/common/http/GoStylePathNormalizingShadowingGateTest.java`
  ⇒ ⇒ **G4 下一笔提交会带它进来** ⇒ ⇒ **我域下一轮开工第一步 = 并入 G4 新尖端**（`service/common`
  是 G4 的 EXCLUSIVE 面 ⇒ 不并就等于用旧 jar 测共享层）⇒ ⇒ **本轮不并**（G4 还没提交 ⇒ 并了也不完整）。



### 65.9 🟢 **本轮验证（2026-10-28 12:0x~12:5x · 域级 6 模块全量 · 单批次）**

**命令**（**带 `-am`** ⇒ `users-common` / `service/common` 用的是**本树源码** ⇒ 不是 `~/.m2` 旧 jar）：
```
mvn -B -o -DargLine=-Xmx2g -am -pl service/users/user/user-api,service/users/user/user-rpc,service/users/admin/admin-api,service/users/admin/admin-rpc,service/follow/follow-api,service/follow/follow-rpc clean test   -Dtest='!GoStyleParamLimitOffModeGateTest' -Dsurefire.failIfNoSpecifiedTests=false
```

> ⚠️ **排除口径（透明登记，不冒充「全绿」）**：排除了 **1 个共享层用例**
> `com.rag.common.http.GoStyleParamLimitOffModeGateTest#manyNonEmptyValuesPassThroughWhenGateIsOff`
> ⇒ ⇒ **理由**（实测取证，见 §65.10）：该用例在 `service/common` 全套件里**间歇红**（1/2 轮），
> **单跑 2/2 绿** ⇒ ⇒ **共享层域内 flake**（`service/common` ＝ **G4 写权**）⇒ ⇒ **我域不碰，已发信 G4**。
> ⇒ ⇒ **本轮数字是「排除这 1 条共享层间歇红」的域级数**（连带 `service/common` 由 333 变 329）。

| 组件 | 路径 A（maven `Results:` 汇总行）| 路径 B（surefire XML 求和）|
|---|---|---|
| `user-api` | **127** | **127** |
| `user-rpc` | **162** | **162** |
| `admin-api` | **149** | **149** |
| `admin-rpc` | **122** | **122** |
| `follow-api` | **74** | **74** |
| `follow-rpc` | **83** | **83** |
| **合计** | **717** | **717** |

- **两条独立路径逐模块相等** ⇒ ⇒ 互证「同一批用例**没被数两次**」（若被数两次，两条路径会**同时**偏大）。
- **reactor 实跑 18 个模块**（目标 6 ＋ 非目标 `common` 329 ＋ `users-common` 39 ＋ `follow-common` 37）⇒ ⇒ **域级 717 ＋ 非目标 405 = 1122 逐字相等** ✔
  ⇒ ⇒ **第三方对账**：reactor 全部模块的 surefire XML 求和 = **1122**（与上式**逐字相等**）⇒ ⇒ 零重复计数。
- **增量归因（712 → 717 = +5）**：**user-rpc 157 → 162** ＝ **新守门 `BlindPortGateTest` 的 5 条** ⇒ ⇒ **逐条可指名、无余量** ✔
  ⇒ ⇒ **共享层 333 → 329 ＝ −4** ＝ **被排除的那个共享层用例类正好 4 条**（`GoStyleParamLimitOffModeGateTest`）⇒ ⇒ **排除口径可复算** ✔
- **BUILD = SUCCESS** · **0 失败 / 0 错误 / 0 跳过** · **0 次撞口症状**
  （`Address already in use` / `port is already allocated` / `http2 exception` 命中 **0**）
  ⇒ ⇒ **本轮 21 处改造没有引入新的环境性抖动**。
- **新守门 `BlindPortGateTest` 5 条全绿**（单跑 `mvn -pl service/users/user/user-rpc test -Dtest='BlindPortGateTest'` ⇒ **EXIT=0**）。

### 65.10 🔴 **共享层一条【间歇红】卡住域级回归 —— 取证 4 次 + 归属 G4（我域不碰）**

**现象**：`service/common` 的 `com.rag.common.http.GoStyleParamLimitOffModeGateTest#manyNonEmptyValuesPassThroughWhenGateIsOff`
⇒ `Raw[status=-1, body=, head=] ==> expected: <200> but was: <-1>`（**`-1` ＝ 客户端整体异常，不是 400**）
⇒ ⇒ **`-am` 构建在 `service/common` 就停 ⇒ 我域 6 模块根本没跑到**（⇒ 域级数字取不到）。

**取证（4 次跑批，2026-10-28 12:0x）**：

| 跑法 | 结果 |
|---|---|
| 域级全量 第 1 轮（`clean test` · `-am`） | `service/common` **绿** ⇒ 跑到 user-rpc 才红（那次红的是**我自己的守门**，已修） |
| 域级全量 第 2 轮（`clean test` · `-am`） | `service/common` **红**（本条）⇒ **构建停在 common** |
| `service/common` 单跑该类（第 1 次） | **绿**（4/4 · EXIT=0） |
| `service/common` 单跑该类（第 2 次） | **绿**（4/4 · EXIT=0） |

⇒ ⇒ **单跑 2/2 绿 · 全量套件里 1/2 轮红** ⇒ ⇒ **间歇红**（顺序 / 环境相关）⇒ ⇒ **不是确定性缺陷**。

**不是我引入（源码级）**：我本轮 diff 里 **`service/common` 路径命中数 = 0**
（`git diff --cached --name-only | grep -c service/common` ⇒ **0**）⇒ ⇒ 本条**与我域改动无关**。

**归属**：`service/common/**` ＝ **G4 写权（EXCLUSIVE）** ⇒ ⇒ **我域不碰** ⇒ ⇒ **已发信 G4**（附上表取证 ＋ 复现建议 ＋ 提醒「它会卡住**所有 lane** 的 `-am` 回归 ⇒ ⇒ 波末 C7 收口会被它卡死，除非它恰好不红 —— 那是赌运气，不可作为收口口径」）。

**我域本轮处置（透明登记）**：第 3 轮域级全量用 `-Dtest='!GoStyleParamLimitOffModeGateTest' -Dsurefire.failIfNoSpecifiedTests=false`
**显式排除这一条** ⇒ ⇒ **该轮数字是「排除 1 条共享层间歇红」的域级数**（§65.9）⇒ ⇒ **不冒充「全绿」**。


---

## 67. 🟢 `T11` 带来的**域级守门改动**：② 段「端口类个数」断言 **8 → 9**（2026-10-29 · G1 lane）

### 67.1 起因

本轮（`T11`）在 **follow-api** 新增了真 Tomcat 测试 ⇒ 需要 `support/RealTomcatPort` ⇒
⇒ 域级守门 `BlindPortGateTest` 的 ② 段（**段归属**：端口类的段必须落在本域预分配段内、子段互不重叠）**枚举到的端口类从 8 个变 9 个**。

### 67.2 改了什么（同批更新，**不留雷**）

| 项 | 改前 | 改后 |
|---|---|---|
| `BlindPortGateTest.java` ② 段的 `hasSize(...)` | **8** | **9** |
| 该断言的说明文字 | 「至少要有 6 个 RealGrpcPort + **2** 个 RealTomcatPort（user-api/admin-api 各要一个 Tomcat 口）」 | 「要有 6 个 RealGrpcPort + **3** 个 RealTomcatPort（user-api / admin-api / **follow-api** 各要一个 Tomcat 口）」 |
| `@DisplayName` 里的「8 个端口类」 | 「8 个端口类」 | 「**9** 个端口类」 |
| 类 javadoc ② 条 | 「**8** 个端口类的段」 | 「**9** 个端口类的段」 |
| 「该模块不需要这一类」的注释 | 括注「例如 follow 域没有真 Tomcat 测试」 | 去掉该括注（**该前提已不成立**） |

⇒ ⚠️ **为什么必须同批改**：守门的 `hasSize` 是**精确值**断言 ⇒ 新增一个端口类而不改它 ⇒ **守门当场红** ⇒
⇒ ⇒ **凡「新增一个 `RealGrpcPort` / `RealTomcatPort`」的改动，都必须同时改这一行**（本条就是该规则的第一个实例）。

### 67.3 新增端口类的段

| 组件 | 类 | 段 | 是否与既有段重叠 |
|---|---|---|---|
| follow-api | `support/RealTomcatPort` | **22340–22359** | ❌ 不与 `follow-api` 的 `RealGrpcPort`（22300–22319）、`follow-rpc` 的（22320–22339）重叠 |

⇒ 段归属仍落在共享层预分配的 **follow 域段 22300–22399** 内 ⇒ ② 段其余判据（**不落 OS 临时段 49152–65535**、**不撞 Testcontainers 段 22700–22999**、子段互不重叠）**全部满足** ⇒ ② 段**实跑通过**。

⇒ 详细矩阵与证据 ⇒ [`docs/DRIFT/follow/vs-common.md` §13](../follow/vs-common.md) ＋ [`docs/DRIFT/follow/stage1-admission.md` §4.2](../follow/stage1-admission.md)。

---

## 68. 🟢 **「注释里的字节数漏算尾随换行」族** —— 三域三处 · G2 已修 like · **G1 自捕并修 admin**（2026-10-29 · G1 lane）

### 68.1 起因（G2 来信 ⇒ 我域自查 ⇒ 自捕第三处）

| # | 位置 | 注释写的 | 真实 / 断言用的 | 状态 |
|---|---|---|---|---|
| ① | 共享层 `service/common/.../GoStyleRouterErrorResponses.java:83` | 「全长 **20** 字节」 | **19**（`"404 page not found\n"` = 18 + 1） | 🔴 **G4 写权** ⇒ 已登记 `S1-FOLLOW-010`，**G1 不碰** |
| ② | like 域 `LikeApiRouterErrorRealTomcatTest.java:46` / `:187` | 「全长 **20** 字节」/「一致（**20**）」 | **19** | ✅ **G2 已修**（commit `aee9b0c`）· G2 独立复算过才改 |
| ③ | **admin 域** `AdminApiRealTomcatResponseBytesTest.java:266` | 「线上是 Content-Length: **15**」 | **16**（`"Request Timeout".length() + 1`） | ✅ **G1 本轮自捕并修**（`S1-USERS-010`） |
| ④ | **follow 域** `FollowApiFailurePathMatrixTest.java:417`（**本轮新增文件里**） | 断言消息写「全长 **20** 字节」 | **19** | ✅ **G1 本轮二次自捕并修**（`S1-FOLLOW-013`）⇒ ⚠️ **这一处就是 G1 自己写的** ⇒ 说明「自查必须先查自己的新文件」|

### 68.2 ③ 的成因（**与 ①② 不是同一个 body，但同一族的错法**）

③ 写的是 **503 超时 body**，不是 404 body ⇒ 但错法同源：
- Go 的 `http.Error(w, httpTimeoutBody, 503)` ⇒ `fmt.Fprintln` **补一个尾随 `\n`** ⇒ body = `"Request Timeout\n"` = 15 + 1 = **16** 字节
- 该文件的**常量与断言都是对的**（`GO_TIMEOUT_CONTENT_LENGTH = GO_TIMEOUT_BODY.length() + 1` = 16；断言消息也写「15 字节 + 尾随换行 1 字节」）
- ⇒ **只有紧邻断言上方那一行注释**写成了 15 ⇒ **注释与它下面两行的代码自相矛盾**

⇒ 📌 **判定式（可复用）**：凡注释里出现「N 字节」而 body 来自 Go 的 `http.Error` / `fmt.Fprintln` ⇒ **N 必须含那个尾随 `\n`**；
⇒ 只数**正文**（`len`）是这族错误的**唯一成因** ⇒ ⇒ 复核时**先看断言用的那个数**，别只读注释。

### 68.2bis 🔴 **G1 自己写的文件里也有一处**（④）—— 且**躲过了 G1 的第一轮自查**

④ 在 **G1 本轮新增的 `FollowApiFailurePathMatrixTest.java:417`**：那行是 `assertEquals` 的**断言消息**，
写「Go 是 http.Error + fmt.Fprintln ⇒ body 带尾随换行、全长 **20** 字节」⇒ **断言本身是对的**
（比的是 body 字面量），**错数只在文案里** ⇒ 与 ①③ 同族。

⚠️ **它躲过了我第一轮自查，两个漏洞叠在一起**：

| # | 漏洞 | 细节 |
|---|---|---|
| ① | **模式选错** | 我那轮 grep 的模式是 **body 字面量** `"404 page not found"` ⇒ ④ 那行**不含**这个字面量（它在**上一行**）⇒ 零命中 ⇒ **「模式选错 ⇒ 扫不到」** |
| ② | **命令被截断** | 那轮「字节」扫描**带了 `head -40`** ⇒ 全域 **249** 条命中被截到前 40 条 ⇒ ④ 落在**第 210** 条 ⇒ **「零命中是假信号」**（G2 来信提醒的那一族）|

⇒ ⇒ **判定式（两条合并）**：**扫「错数」这类问题时 ⇒ 模式要选「**数字 + 字节**」（不是 body 字面量）＋ 命令不得带 `head`/`tail`/分页 ＋ 命中数先落文件再逐条看**。
⇒ ⇒ **推论**：**「我域零残留」这种结论，只有在「模式对 + 无截断」两条同时成立时才敢说** ⇒ 本轮两条都曾不成立 ⇒ **结论被推翻并已重扫**。

### 68.3 本轮处置

- **域侧能自修的域侧修**（AGENTS.md §5.3c）⇒ ③ 在 **G1 写权内**（`service/users/admin/**`）⇒ **已修**
- ① 在 **G4 写权** ⇒ **只登记上报**，不动（`S1-FOLLOW-010`）
- ② 在 **G2 写权** ⇒ G2 **已自行修好**（`aee9b0c`）⇒ G1 **不重复派单**
- ⚠️ **本轮我域自查命令**（**已按 §68.2bis 的教训重做** ⇒ 无 `head`、模式选对、命中落文件）：
  ```
  # ✅ 正确口径：模式 = 「数字 + 字节」，不截断，命中落文件再逐条看
  grep -rnE "[0-9]+ ?字节" service/users service/follow --include='*.java' | grep -v "/target/" > /tmp/scan.txt
  wc -l /tmp/scan.txt          # ⇒ 74 行（全域「数字+字节」命中）
  # ⚠️ 再逐条**复算**（权威）：
  printf '404 page not found\n' | wc -c          # ⇒ 19
  printf 'Request Timeout\n'     | wc -c          # ⇒ 16
  printf 'field "username" is not set\n' | wc -c # ⇒ 28
  printf 'field "uid" is not set\n'       | wc -c # ⇒ 23
  ```
  ⇒ **我域（users + follow）现在零残留**：① 19 ✓ ② 16 ✓ ③ 28 ✓ ④ 23 ✓（逐条复算对上）
  ⇒ 另有 `AdminApiPlainText400HeaderProbeTest.java:30` 的「Content-Length: 95」⇒ 那是 2026-10-22 **逐字抄的 Go 侧响应**（观测原文，**不是计算值**）⇒ 无需改
  ⇒ 「数字 + 字节」其余命中全是**别的族**：bcrypt 72 字节上限 · 字节码扫描阈值 · 密钥长度（15 / 36 字节）· 落库字节数 · hex 长度 ⇒ 与本族（**HTTP body 尾随换行**）无关

### 68.4 复验

`AdminApiRealTomcatResponseBytesTest` **3/3 绿** · `BUILD SUCCESS`（日志 `/tmp/g1log/g1-adminbytes.log`）⇒ **注释订正零行为变化**（断言一个字没动）。

---

## 69. 🟢 **「Go 出处 `文件:行号` 指错」族 —— 我域全量自查：849 处引用，零错**（2026-10-29 · G1 lane）

### 69.1 起因（G2 来信 ⇒ 他们那边在 like 域抓到 14/21 处 `main.go:` 指错）

🔴 **这一族比「字节数错」更危险，两个原因**：
1. **「文件指错」肉眼不可见** —— `cmd/fact-dispatch/main.go` 与 `cmd/fact-authority/main.go` 结构几乎一样 ⇒ **行号从另一个文件串过来，肉眼看不出** ⇒ 读注释的人按图索骥会「找不到」，却**不会怀疑出处本身**；
2. **断言照常绿** —— 全在注释 / `@DisplayName` / 断言消息里 ⇒ **不改任何断言 ⇒ 一行红都不会有**。

⇒ ⇒ **出处不可核 ⇒ AGENTS.md §3.3「每个 Go 事实必须带 `文件:行号`」就失效了**。

### 69.2 我域的扫描口径（**无截断**）

```bash
grep -rnE "\.go:[0-9]+" service/users service/follow --include='*.java' \
  | grep -v "/target/" > /tmp/g1-gocite.txt
wc -l /tmp/g1-gocite.txt        # ⇒ 819 行命中 / 177 个文件 / 展开后 **849 条**引用
```
⚠️ **不带 `head` / `tail`**（教训见 §68.2bis 机制 ②）⇒ 命中先**落文件**再逐条看。

### 69.3 核验结果：**849/849 可核 · 零错**

判据 = **三项廉价可判**（G2 建议的口径，**不判内容语义**）：
① 引用的 Go 文件**存在** ② **行号在文件行数范围内** ③ **该行/该段非空**。

| 项 | 值 |
|---|---|
| 引用总数 | **849** 条（819 命中行展开） |
| 路径形态 | Sea 仓 `service/…` 51 个 · **go-zero 框架目录** 35 个 · **裸文件名** 56 个 · 第三方模块（gorm / pgx / golang-jwt …）若干 |
| **核验通过** | **849 / 849** ⇒ **我域零错**（与 like 域的 14/21 相反） |

### 69.4 🔴 **核验脚本自身的失效模式**（G2 建议的脚本也要注意）

第一版脚本把**裸文件名**按 basename 在整个 `GOMODCACHE` + Sea 仓 + go-zero 里搜 ⇒ ⇒ **同名文件配错**：

| 现象 | 数量 | 真伪 |
|---|---|---|
| `postgres.go:251-256` 报「行号越界(文件 112 行)」 | **7** | ❌ **假阳** —— 配到了 go-zero 的 `internal/testenv/postgres.go`（112 行）；真身是 **`gorm.io/driver/postgres@v1.6.0/postgres.go`（305 行）**，其 `:251-256` 正是 `case schema.Time: … return "timestamptz"` ⇒ **与 RAG 注释声称逐字对得上** |

⇒ ⇒ **可复用判定式（两条，缺一不可）**：
① **同名文件必须「多候选都试」** —— 只要**任一**候选能让「行号在范围内 + 该行非空」成立 ⇒ 就算通过（**不是**「第一个匹配就算」）；
② **上下文点名了模块**（如 `gorm.io/driver/postgres@v1.6.0`）⇒ **优先用该模块的真身**，别让 basename 去猜。

⇒ 📌 **这一条对 G2 建议的「出处抽验脚本」同样适用** ⇒ 已回信提示。

### 69.5 ⚠️ 证据的**可复算性**边界（如实登记，不夸大）

- 本节的 **849/849** 是**本轮一次性核验**的结果 ⇒ **核验脚本在 `/tmp`（未落仓）** ⇒ 按 AGENTS.md 的口径 ⇒ **本条属「声明的，不是可复算的」**（⚠️ 与「G-1 探针已消失」同族 ⇒ **不因它抬高任何 R 档**）；
- ⇒ **建议阶段 2 把脚本落仓**（归**收口 / 共享工具面**，**不在 G1 写权内**）：抽出 RAG 注释里的 `文件:行号` ⇒ 回 Go 文件读那一行 ⇒ **只要求「该行存在且非空」**（**不要求内容匹配**，太严会误报）⇒ 就能挡住「文件指错 + 行号越界」两类。

### 69.6 与字节数族的关系（**两条独立的族，同一个教训**）

| 族 | 特征 | 我域结果 |
|---|---|---|
| **字节数族**（§68） | 注释里的**数字**算错（漏/多算尾随 `\n`）⇒ **断言仍绿** | **4 处**，全部已修（① G4 待修 · ② G2 已修 · ③ G1 自修 · ④ G1 二次自修）|
| **出处指错族**（本节） | 注释里的**`文件:行号`** 指错（文件相似 ⇒ 肉眼不可见）⇒ **断言仍绿** | **0 处** ⇒ 我域零错 |

⇒ ⇒ **共同的元教训**：**注释里的「事实」有两类载体 —— 数字 与 坐标** ⇒ **两类都会错、且都不会让测试变红** ⇒ ⇒ **「全绿」不能证明注释是对的** ⇒ 必须**单独建判据**去核（字节数 ⇒ 逐条复算；出处 ⇒ 逐条回真源读那一行）。

---

## 70. 🟢 **收敛核验轮（2026-10-29 02:4x · G1 lane）**：域内是否收敛 ＋ 一次**误判的撤回**

### 70.1 本轮做了什么

**目标**：按用户指令「继续检查域内是否收敛，能否达到阶段一的验收标准」⇒ **重跑 `docs/PhaseGoal/g1.md` §5 收口命令** + **逐条核 P0 九条** + **给域内未闭合项做最终归类**。

| 新跑的命令 | EXIT | 结果 |
|---|---|---|
| `mvn … -DskipTests … package` | **0** | BUILD SUCCESS · 12.484 s · **8 个 jar**（R16）|
| `bash tools/difftest/check-jar-executable.sh` | **0** | 🟢 **本 lane 6/6 api+rpc 模块全 ✅**（R17）⇒ 基线 §九 `G-10` 在本 lane 已闭合 |
| `bash tools/difftest/exp-difftest-grpc-user9.sh --no-build` | **1** | **43 场景 / 39 等价 / 4 处差异**（4 处全是**已签字 N-2**）⇒ **与 2026-10-24 基线逐条同形**（R18）|
| `check-no-prod-secrets.py`（G4 `19b2edf` 新增守门） | **1** | **我域未豁免命中 = 0** ✅（6 条红全在 `users-common` = G4 面 = 树间版本差）（R21）|

⇒ **判定变化**：**users 域双侧活体从「本轮未跑」升级为「可复算 + 本轮复现」**；**follow 域从「探针不在库」升级为「探针源头已消失」**（更硬，但仍不阻塞）⇒ **lane 判定维持 🟡**（唯一未达标项仍是 `follow-rpc` 的 `DRIFT-FOLLOW-006`）。

### 70.2 🔴 **撤回一次误判**：Go 真源乱码字面量**不是新发现**

**我犯的错**：跑完 43 场景对拍，看到 4 处「Go 侧文案是乱码、RAG 侧是正常中文」的差异 ⇒ **自制了一个检测器**扫 Go 全仓 ⇒ 得出结论「这是 Go 侧缺陷、RAG 正确、但**用户域这 3 处还没有签字 ⇒ 需新登记一条有意偏离**」。

**真相（三条既有记录，我全都该先查）**：
1. `docs/Pitfalls/users/user/rpc.md:127` **P-05** —— 明写「**本仓已把它登记为 N-2（用户 2026-10-21 签字：RAG 保留正确中文）**」+「**下一个 agent 不要把它当新发现再「修」一遍**，也不要反过来把 RAG 改成乱码」；
2. `docs/DRIFT/users/user/api.md` 的 **N-2**（用户 **2026-10-21** 签字）—— users 域**已签字**；
3. admin 域同型 = **`SD-2026-10-23-07`**（`docs/Intentional_Deviations/users/admin/rpc.md:58`）—— 「gRPC `status.description` 保留正常中文，不复刻 Go 的乱码字节」。

⇒ **撤回**：**不需新签字、不动任何代码**（本轮确实一行业务码都没改）。本轮那 4 处差异 = **签字项的复现**，不是缺陷 ⇒ 记 `S1-USERS-011`（P3 · 已澄清）。

⇒ ⚠️ **附带一个更刺眼的事实**：我**自制的检测器连续两版都漏报**（第一版只找到 11 处、把带 `?` 顶替的那类全漏了；第二版改成字节级仍只找到 11 处）⇒ 而 **P-05 早就给了正确两步法**（① 按字节读并打印 hex ② 反向还原确认有损）⇒ **判据与动机错配**的又一次实例：**自制判据前，先查本域 Pitfall 与签字档**。

⇒ **元教训（三条，同族）**：
- ① **「这看起来像新发现」不是发现依据** —— 先查 `docs/Intentional_Deviations/**`（决策真源）+ `docs/Pitfalls/**`（踩坑真源）⇒ **已签字/已记录的东西不会因为「我没查到」而变成新缺陷**；
- ② **自制检测器是最容易产生「新发现」的机器** —— 它按**我的**定义扫，而**真实的判据往往已经在仓里**（P-05）⇒ 先用别人的判据，再用**已知错例**反测自己的（P-05 的 9 条 vs 我的 11 条 ⇒ 数量都对不上 ⇒ 立刻该怀疑判据而不是结论）；
- ③ 与 §69.4 的教训同源：**核验脚本自身的失效模式**必须写进台账 ⇒ 本条补一个实例：**「零命中 / 少命中」要先怀疑判据的定义域**。

### 70.3 🟢 **订正上一轮的推论**：「本轮未跑」**≠**「域内未取证」

上一轮我写的是「双侧活体六条命令未跑 ⇒ users 域『真实运行 + 行为等价』**未取证**」⇒ **这个推论过重**。逐条核「**探针是否在版本控制内**」（`git ls-files`）：

| 双侧活体命令 | 资产在库？ | 本轮跑了？ | 真实状态 |
|---|---|---|---|
| `exp-difftest-grpc-user9.sh` + `grpc-user9-dual-port-runner.java` | ✅ **在库** | ✅ **跑了** | 🟢 **可复算 + 本轮复现 43 场景** + 域台账有 **4/4 变异验证** ⇒ **R4 的 ⑦⑧ 均成立** |
| `run.sh` + `diff.py`（users HTTP wire） | ✅ 在库 | ❌ | 🟡 可复算 · 本轮未复验 |
| `matrix.py`（users DTO 笛卡尔） | ✅ 在库 | ❌ | 🟡 同上 |
| `exp-difftest-grpc-admin-errors.sh` + `GrpcAdminDualPortRunner.java` | ✅ 在库 | ❌ | 🟡 同上 |
| `exp-difftest-redis-blacklist.sh` | ✅ 在库 | ❌ | 🟡 同上 |
| **follow 域 52/52 探针** | ❌ 不在库（**且 `/tmp` 源头已消失**） | ❌ | 🔴 **不可复算** ⇒ 这才是 `G-1` 的真正落点 |

⇒ **准确表述**：**users 域**双侧活体**已取证**（R4 成立）⇒ 撤回「未取证」；**follow 域**不可复算（事实更硬：上一轮只知「不在库」，本轮实测**连 `/tmp/probe*.sh` 都不存在**了）⇒ 但基线 A-2 已按 **R3** 计入 ⇒ **不改变组件判定、不阻塞阶段一** ⇒ 记 `S1-USERS-012`（基线措辞需订正 · 收口）。

### 70.4 G4 回执（2026-10-29）与我域的关系（**自核，不采信转述**）

| G4 的条目 | 我域的实际情况 | 我域动作 |
|---|---|---|
| **F1**（关闭路径不加载 web 类型）· **F2**（Redis 副本收编）**均已结案** | 我域台账 §12.2 / §12.3 的两条跟进项 | ✅ **回填「已结案」**（`users/stage1-admission.md` §6.3 记录）|
| `1f62a24`（DRIFT-COMMON-005 结案）**2 个生产消费点在我域**（`user-rpc/UserDomainService.java:154` · `admin-rpc/AdminDomainService.java:178`）| **两者都只调 `isExpired()`** ⇒ 语义未变 ⇒ 零破坏（与 G4 的说法一致）| ✅ 无动作 |
| `2c18920`（Snowflake 单调钟）· `admin-rpc/…/AdminSeederBootstrap.java:96-98` | ⚠️ G4 说「静态初始化顺序写错 ⇒ 类加载即 NPE ⇒ admin-rpc 起不来」，但**它补跑的是它自己树里的 admin-rpc**（**我的 8 笔提交未合并 ⇒ 它跑的是基线版**）⇒ **它的回归结论不覆盖我的代码** | 🔴 **登记为「合并收口回归时必须复验」**（我的树**拿不到** G4 的 `Snowflake.java` 修复 ⇒ 本轮无法自证）|
| `19b2edf` 新增 `check-no-prod-secrets.py`（扫描面含我域）| 我域**未豁免命中 = 0** ✅；**我本轮新增的 `FollowApiFailurePathMatrixTest.java:213`** 持真源密钥明文 ⇒ 落在 `src/test/**` 豁免面（W3）⇒ 合规，但**若 G4 收口把豁免换成「等长夹具」，我域该行需同步改** | ✅ 已记 R21 + `S1-USERS-013` |
| `19b2edf` 的 `GoStyleFormLimitSemanticCriterionTest` 会扫 `user-api`/`admin-api`/`follow-api` | 我域三个 api **已在清单内** ⇒ 不因「新增 api 域」而变红 | ✅ 无动作 |
| 🔴 **`docs/PhaseGoal/g1.md` 被 G4 跨面改写**（`e73cbf3` 重跑生成器 ⇒ §5 的 `--domain users` 改成 `--domain user --domain admin`）| ⇒ **我域 §5.3 第 1~3 行的写法与 G4 修后的实现一致**（`--domain user` / `--domain admin`）⇒ **不冲突** | 🔴 **合并收口那一步必须用新生成器重跑一次**（否则我的树会用旧生成器把它改回去）—— 已写进 `users/stage1-admission.md` §9 R11 |
| G4 端口占用 `user-rpc` **22220-22239** · `admin-rpc` **22260-22279** | 与我域 follow 段 **223xx** 不重叠 ⇒ **不撞**；但我域 users 段（22210-22219 / 22250-22259）与 G4 的 user/admin 段**不同段** ⇒ 并行安全 | ✅ 无动作（记一笔，供以后排期参考）|

### 70.5 域内收敛结论（**最终**）

| 分类 | 条目 | 判定 |
|---|---|---|
| **域内已闭合** | 失败路径矩阵（follow · 62 条）· 出处族零错（849/849）· 字节数族我域 2 处已修 · 产物可执行性 6/6 · 双侧活体复现（user-rpc 43 场景）· 密钥守门我域 0 命中 · `follow-proto` 契约网（3 条）· ryuk env · 端口 env · 口令默认值收敛 | ✅ **全部有可复算证据** |
| **域内未达标（1 条）** | `S1-FOLLOW-001` / `DRIFT-FOLLOW-006`：`follow-rpc` 5 个 `follow_rpc_*` 指标**无抓取出口** ⇒ `follow-rpc` = 🟡 ⇒ **lane 判定 = 🟡** | 🟡 **待用户重新拍板**（2026-10-24 已定「本轮不落地」；重开 = 全仓 8 个 rpc 组件统一方案 ⇒ **非 G1 单方可定**）|
| **域外未闭合（6 条 · 2026-10-29 订正，原记 8 条）** | `S1-USERS-001`（🟡 **部分闭合** —— README / `.env.example` / `.gitignore` 三条已被 2026-10-29 R4 轮闭合；**残留** = 根 `Infrastructure.md` 的 `api-gateway/` 与 `sea_user` 两处 ⇒ **原 🔴 A 类 · 说明书 ⇒ 降为 🟡 B 类残留**）· `S1-USERS-002`（口令 fallback · **口径已订正：属有意偏离，需签字**）· ~~`S1-USERS-003`~~（🟢 **已闭合** —— `.gitignore` 实有 3 条 `.env` 规则 · `.env.example` 已入库 ⇒ **从本表剔除**）· `S1-USERS-006`（锚点）· `S1-USERS-007` / `S1-FOLLOW-007`（基线订正）· `S1-FOLLOW-009` / `S1-USERS-008`（ryuk）· `S1-FOLLOW-010`（共享层错数）<br>⚠️ `S1-FOLLOW-008`（surface-parity 静默假绿）**已由 G4 `e73cbf3` 闭合** ⇒ 原已列在「未闭合」表内，**本轮一并剔除** | 🔴 **交收口 / G2 / G3 / G4** |
| **本轮新增的域外登记** | `S1-USERS-012`（基线措辞）· `S1-USERS-013`（users-common 密钥 = 树间版本差）· `S1-USERS-014`（`deadlineEpochMs` 台账引用过期）| 🔴 **交收口** |

⇒ ⇒ **一句话**：**域内能做的都做完了且都有可复算证据**；**剩下的域内唯一缺口是 `follow-rpc` 的指标出口（等用户拍板）**，其余全是**域外移交** ⇒ **G1 lane 判定 = 🟡（口径 = 基线 §8.1 的 `MIN(本 lane 组件状态)`）**，**阶段一在本域侧已收敛到「只等一个决策」**。

---

## 71. 🟢 **开工轮（2026-10-29 03:45 · Jason 开工批准）**：§8.7 的 **23 条**已修 ＋ **§69 结论的限定订正**（2026-10-29 · G1 lane）

### 71.1 本轮做了什么

**8 个 Java 文件 / 25 处引用点 / 零业务码改动** —— 修 `docs/CLOSEOUT-2026-10-29.md` §8.7 登记的 **G1 23 条**（15 版本写错 ＋ 5 行号越界 ＋ 3 路径不精确）⇒ **分布 = `users` 域 23 / `follow` 域 0**。
⇒ 逐条修复记录、修法论证（**v1.31.1 与 v1.31.2 行号不同 ⇒ 不能只改版本号**）、判据实现与双向验证 ⇒ **[`stage1-admission.md` §10](stage1-admission.md)**（本节不重复）。

### 71.2 🔴 **对 §69 结论的限定订正**（采信调度员 §8.7 的订正 · **不整体推翻 §69**）

| 项 | 值 |
|---|---|
| **§69 的判据** | 三项**廉价可判**：① 引用的 Go 文件**存在** ② **行号在文件行数范围内** ③ **该行/该段非空** |
| **🔴 它为什么漏掉本轮这 23 条** | **判据里没有「版本」这一维** ⇒ 它验的是「**那个文件**在不在、行号超没超」⇒ **「引到另一份内容不同的副本」这一族天然漏检** —— 旧行号在**错误的副本**里同样合法 ⇒ **查不出来**（§8.7 原话：「**旧引用在错的版本下看起来合法**」是这族缺陷的隐蔽性所在） |
| **本轮实测（带版本 + 内容锚定）** | 批准范围 **23 条已修** · 另**新发现 13 条未修**（`users` 12 ＋ `follow` 1）· **机器判不了 549 条**（`users` 304 ＋ `follow` 245）|
| ⇒ **§69 的精确表述** | 「**849/849 在该判据口径下未发现错误**」＝ **声明**，**不是**「我域零错」；本轮把域内出处的**已知欠账**更新为 **13 条（已登记未修）** ＋ **549 条机器判不了** |

⇒ ⇒ **元教训（比 §69.6 更硬一层）**：**「三道廉价可判」之所以漏，是因为它把「坐标」当成 `(文件, 行号)` 二元组，而真坐标是 `(模块, 版本, 文件, 行号)` 四元组** ⇒ **少一维 ⇒ 一整族缺陷静默通过** ⇒ ⇒ 与 AGENTS.md §3.3.1「『配置里没写』≠『没开』」同源：**没检查的那一维，等于不存在的那一维**。

### 71.3 判据本身的边界（**随结论一起读**）

- ⚠️ **判据脚本未落仓**（`tools/**` 按机器守门口径**无 lane 拥有** ⇒ §8.7 G4 回执订正）⇒ **结论是「声明的」，不是「可复算的」** ⇒ **建议收口执行者提升为 `tools/difftest/check-*.py`**（⚠️ 提升则必须同时改 `docs/PhaseGoal-judge.md` §6.3，否则制造 **B14**）；
- **输入探针先行 ＋ 硬失败**（`exit 2`）⇒ 模块缓存 / GOROOT / Go 真源仓不可见时**不产出任何结论**，绝不降级成「0 条 = 一致」；
- **双向验证**：当前代码 **FAIL 0**（限批准范围）· HEAD 旧引用反测 **FAIL 20**（= 15 版本 ＋ 5 越界 ⇒ 与 §8.7 逐条吻合）⇒ **判据有牙齿**；
- ⚠️ **假阳性簇已踩并已修**：`postgres.go` / `redis.go` 基名在 **Go 仓与模块缓存各有一份** ⇒ 基名索引只扫 Go 仓 ⇒ 撞同名不同内容的另一份 ⇒ **假红**（§8.7 的 `postgres.go` 19 / `goka` 10 / `processor.go` 4 那个簇的同源坑）⇒ **基名索引必须含全部已 pin 的模块缓存**；修完这三条后当前代码 FAIL 数由 50 降到 12（其余 38 条**全是假阳性**）。

### 71.4 🟡 **一次「闸门被自己的正文满足」的活证**（回执 ③-6 的实证）

本轮写 §71 标题时，**本 lane 的正文里出现了「Jason」与「批准」两词连写的那串字** ⇒ ⇒ **第 1 版判据（按这两个词连续匹配）立刻从 0 变成 1** ⇒ 而当时 ⑤ 已被调度员填过 ⇒ ⇒ **结论碰巧是对的，但它是「碰巧」** —— 闸门读到的是**本 lane 自己写的标题**，不是审批记录。

⇒ ⇒ 已把两处措辞改成「Jason 开工批准」/「Jason 已批准」（**不改变含义，只断开巧合**）⇒ ⇒ 官方**第 4 版**判据（`⑤ 批准记录：Jason · <日期>`）本 lane **回执=1 · 批准=1** ✅ ⇒ **不受影响**。

⇒ ⇒ **可复用判定式**：**任何以「本 lane 的状态」为判据的闸门，都不能被本 lane 的正文内容满足** ⇒ 判据必须**锚定结构标记 ＋ 要求具体形态**（日期 / 完整填充形态）⇒ 否则「**写文档**」这个动作本身就能把闸门刷绿 ⇒ ⇒ 与 `docs/Pitfalls/_cross/domain-common-lanes.md` **PIT-LANE-07**（守门用全文 grep 判措辞 ⇒ 改掉锚点那一行后恒绿）**同源**。

---

## 开工前置回执

【开工前置回执 · G1（`align/g1-users-follow`）· 2026-10-29】

**① 任务范围（本 lane 本轮做什么）**
候选下一件 = 修 `docs/CLOSEOUT-2026-10-29.md` §8.7 登记的 **G1 21 条「Go 出处」真缺陷**：15 处引用写 `gorm@v1.31.2`（真源 `go.mod:28` pin 的是 **`v1.31.1`**）+ 5 处 `schema/field.go:1009` 越界（**v1.31.1 的 `schema/field.go` 只有 1004 行**）+ 1 处 `createadminlogic.go` 引用路径不精确。
⇒ **回执落笔时**本轮只读、不动手（未获批准）；⇒ **Jason 于 2026-10-29 03:45 在本 lane 会话内明示「继续」**（消息 id `182057`）⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。

**不做（明确边界）**
① 阶段二（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7）；
② 共享面：`service/common/**` + `service/users/users-common/**` + `tools/**` + `docs/DRIFT/_cross/**` + `AGENTS.md` + 三个 `_INDEX.md` + `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/**`（⇒ 归 G4 / 合并收口执行者）；
③ `S1-FOLLOW-001`（follow-rpc 指标出口）—— **用户 2026-10-24 已定「本轮不落地」⇒ 不当新缺陷重提**；
④ 改我域 §69 的结论表述（§8.4 已登记给收口执行者）；
⑤ 探针落仓（`tools/difftest/follow/`）—— 阶段 2 建设项，**本轮不预支**。

**② 已读四档（具体路径 → 读到的关键结论）**

| 档 | 具体路径 | 关键结论 |
|---|---|---|
| `docs/DRIFT/users/` | `stage1-admission.md`（565 行）· `user/api.md`（3498）· `user/rpc.md`（2621）· `admin/api.md`（2374）· `admin/rpc.md`（4385）· `admin/_shared.md`（222）· `common.md`（649）· `_shared.md`（1158）· `vs-common.md`（4945） | 阶段一域级判定 = **🟡 有条件通过**（P0 域内 8/9；#5 项目级不成立 ⇒ 根级文件**无人拥有** ⇒ 域外）；域内唯一未达标 = `S1-FOLLOW-001`；`user/rpc.md` §9.1.2 / §9.1.4 记着 **43 场景 / 39 等价 / 4 处差异（全为已签字 N-2）** 与「首轮 2 处假漂移」的辨伪记录（⇒ 对拍**不清状态**就会产出假漂移）；`admin/api.md:1328` 明写 `DRIFT-ADMINAPI-001` 的「未签字」判定**已作废**（⇒ 台账结论会被后续推翻，别按旧结论重开）；`admin/rpc.md:1783` 记着**新发现** `admin_invite.update_time` 不推进；`admin/_shared.md` §2 记着「本轮推翻的三条既有结论（均在本域内）」⇒ **读台账要看最后结论，不是最早那条** |
| `docs/DRIFT/follow/` | `stage1-admission.md`（467）· `api.md`（1051）· `rpc.md`（473）· `common.md`（53）· `proto.md`（80）· `vs-common.md`（831） | `DRIFT-FOLLOW-001/005/009/010/011/012` 全部 **✅ 已修**；**`DRIFT-FOLLOW-006`（5 个 `follow_rpc_*` 指标无抓取出口）= lane 🟡 的唯一原因**；`DRIFT-FOLLOW-002/003/004`（运行时 classpath 缺 `grpc-inprocess` / `discovery:` 须三斜杠 / `NACOS_ADDR` 在 api 与 rpc 两侧语义不一致）= **派 G4 / 跨域**；`DRIFT-FOLLOW-013`「OS 盲选口」族 2 处已修 |
| `docs/DRIFT/_cross/` | `cross-domain-coupling.md`（832）· `domain-common-lanes.md`（180）· `open-items.md`（133）· `ddl-type-divergences.md`（188）· `component-coverage-matrix.md`（286）· `proto-verification.md`（73）· `decisions.md`（68）· `fix-round-2026-10-23.md`（42）· `fix-round-2026-10-28.md`（78）· `go-anchors.md`（37）· `truth-source-switches.md`（24）· `user-qa-verbatim.md`（15）· `human-only-status.md`（44）· `status-snapshot-2026-10-22.md`（65）· `new-domains-2026-10-23.md`（126）· `message-domain-wide.md`（457） | 波次 DAG（**rpc 必须先于 api**）＋ `users-common` 归属**按类判定**；9 轴（A1~A9）逐轴对账 = lane 交付物；**`open-items.md` 记着 users 域 8 处 `DEFAULT CURRENT_TIMESTAMP` = `SD-2026-10-25-CAND-16`（未签字）**、`users_user.status` 可空性 = `CAND-17`（未签字）⇒ **这两条正是我上轮 DDL 实测 16 处的来源**；`ddl-type-divergences.md` §三 明写「`AGENTS.md` §〇 item 2 那条清单**已过期**」⇒ **以台账为准，不要照抄 AGENTS.md 的数字** |
| `docs/Intentional_Deviations/users/` | `user/api.md`（`SD-USERAPI-001/002/003`）· `admin/api.md`（`SD-2026-10-23-08` / `-01`）· `admin/rpc.md`（`SD-2026-10-23-06` / `-07`）· `common.md`（`SD-USERSCOMMON-001`）· `_shared.md`（**`CAND-16` / `CAND-17` 待签字候选**）· `user/rpc.md`（**0 行空占位**） | 签字项 = ① 500 与 401 **带 envelope** 不复刻 Go 空 body ② 401 **计入观测面** ③ 黑名单 401 **业务码 1006** ④ **严格校验** JWT claim ⑤ 保留空 `default-password` 守卫 ⑥ **`status.description` 保留正常中文**（乱码那条 = 本轮差点重犯的那条）⑦ bcrypt **只认 `$2a$` / `$2b$` / `$2y$`**；⚠️ `user/rpc.md` 与 `docs/Intentional_Deviations/follow/*.md`（4 个）**全是 0 行空占位** ⇒ **follow 域零签字偏离**、users/user/rpc 无签字项（⚠️ **空占位 ≠「已签字」**，见 `Pitfalls/users/admin/rpc.md` **P-9**）|
| `docs/PhaseGoal/` | `g1.md`（**机器生成的 per-lane 投影**）＋ `docs/PhaseGoal-judge.md`（**权威源**）的 users/follow 行 | `g1.md`：10 组件（✅ 8 · 🟡 2）· **lane 判定 🟡**；§3 P0 分摊（主责 #2/#3/#6/#8 · 协作 #1/#4）；§5 收口命令清单 —— ⚠️ **第 93 行 `--domain users` 是错名命令 ⇒ 0/0 静默假绿**（G4 `e73cbf3` 已修实现并重跑生成器）；`judge.md` §1.1 的 **R4 ⑦ 要求探针在版本控制内且现在可复算** · ⑧ 要变异记录；A-2 的 users/follow 行（`follow-api` R3↓ · `follow-proto` R2↓ · `follow-rpc` R2↓ · 其余 R3~R4）；§九 缺口 G-1/G-4/G-6/G-8/G-13/G-19；§十一 D-01~D-08 |
| `docs/PhasedGoal.md` + `docs/twoStage.md` §4/§5.3/§5.4/§10 | `PhasedGoal.md:38-52`（P0 九条）· `:54-68`（P1 ≥2~3 个机制闭环）· `:70-82`（P2 不阻塞）· `twoStage.md:258-313`（§4 阶段 0 事实基线：4.1 事实调查 / 4.2 **三条**核心链路 / 4.3 差异基线 9 字段）· `:359-380`（§5.3 **差异与暂缓项 11 项齐全**）· `:381-398`（§5.4 改造记录 9 项）· `:941-976`（§十 **P0–P3 × A/B/C/D · 每问题 11 字段**）| 阶段一准入线 = P0 九条；**我的 `S1-*` 清单就是 §5.3 的 11 项格式**，§6.3 的「P0–P3 + A/B/C/D」就是 §十 的两轴；**A 类 = 阶段 1 准入阻断** ⇒ 我域唯一的 A 类是**根级 `README.md` 缺失**（域外） |
| `docs/Pitfalls/users/` | `_shared.md`（**16 条** PIT-USERS-SHARED-001~016）· `user/api.md`（P-1~P-7）· `user/rpc.md`（**P-05 乱码** · P-06 假出处 · P-07「不要动清单」）· `admin/api.md`（P-ADMINAPI-001~016）· `admin/rpc.md`（P-1~P-16）· `common.md`（4 条）| ① **P-05** = 「Go 源码里的错误文案是乱码 ⇒ 拿它当基准前先把字节打印出来」+ **已登记 N-2** + 「**下一个 agent 不要把它当新发现再「修」一遍**」⇒ 本轮我正是差点重犯；② `admin/rpc.md` **P-2** = `FieldStrategy.NEVER` + `LambdaUpdateWrapper` 让 `update_time` 永不推进 ⇒「**user-rpc 修过、admin-rpc 漏修**」⇒ 对应 `admin/rpc.md:1783` 的新发现；③ **P-9** = 签字档里的**空占位文件**会让人把「已签字」误判成「未签字」；④ **P-4** = `@Import` 只让 Bean 存在（≠ 机制生效）；⑤ **P-15** = 跨域「销账通知」要**自己核**（已修可能还没提交 / 修文案 ≠ 修语义）；⑥ `_shared.md` **-006** 变异脚本把「没跑」当「红了」· **-007** 对拍不清状态 ⇒ 假漂移 · **-016** 陈旧 surefire 报告静默多算测试数 |
| `docs/Pitfalls/follow/` | `api.md`（PITFALL-FOLLOW-006/007/008/009/010/011/012/015/016）· `rpc.md`（001/002/003/004/005/013/014） | ① **FOLLOW-005** = 共享 `service/common` 在**纯 gRPC 进程**里缺 servlet-api ⇒ 正是我的 `S1-FOLLOW-003`（⇒ 派 G4）；② **FOLLOW-001** = 测试把生产地址覆盖成 `static://` ⇒ **生产配置从未被跑过**；③ **FOLLOW-010** = 探针用**固定 `-o` 文件** ⇒ 请求失败时读到的是**上一次的残留 body**；④ **FOLLOW-011** = 故障模拟器**不完成 HTTP/2 握手** ⇒ 测的是「连不上」不是「挂住」；⑤ **FOLLOW-016** = **对拍装置自身会骗人**（端口撞车 / 时钟跳变 / 启动时序 / 端口未释放） |
| `docs/Pitfalls/_cross/` | `domain-common-lanes.md`（PIT-LANE-01~07）· `flyway-ledger-collision.md` · `testing-pitfalls.md` · `fix-round-lessons.md` · `framework-mechanism-evidence.md` | ① **PIT-LANE-01** = lane 的基点早于「脚手架落地那次提交」⇒ 回并撞 **add/add**；② **PIT-LANE-03** = 「共享层已验」≠「本域已验」；③ **PIT-LANE-06** = 守门登记了**已不存在的 lane** ⇒ **恒绿假绿**；④ **PIT-LANE-07** = 守门用**全文 grep** 判措辞 ⇒ 改掉锚点那一行后**恒绿**；⑤ `testing-pitfalls.md` = **绿测试可能是「错误答案的守卫」** + 陈旧 surefire 报告**静默抬高**计数 + 守门的**排除清单是架构假设** ⇒ 架构一变就假阳性 |

**③ 待批准项（需 Jason / 调度员拍板）**

1. 🔴 **候选任务的第 ③ 条要扩到 3 条**（**与 §8.7 的「1 条」不符 ⇒ 域内实测**）：清单只列 1 条，我域 grep 命中 **3 处** —— `admin-rpc/…/AdminSemanticDriftRegressionTest.java:340` 与 `admin-rpc/…/AdminDomainService.java:439`（均**多写了 `admin/`**）+ **`admin-api/…/AdminCreateService.java:20`**（引 `service/user/admin/api/internal/logic/admin/createadminlogic.go` ⇒ **该文件在 Go 仓不存在**；真身只在 **rpc 侧** `service/user/admin/rpc/internal/logic/createadminlogic.go`（**126 行** · `:44-46` = 两行 `strings.TrimSpace`）⇒ 这一条**指向了不存在的 api 侧文件**，修法要先定 admin-api 的 createAdmin 对应 Go 的哪个入口（⇒ 可能是 api↔rpc 边界问题）。
2. ✅ **15 + 5 条的计数与落点已独立复核，与 §8.7 分布一致**：15 处 `gorm@v1.31.2` 全在 `service/users/**`（`user-rpc` 12 + `admin-rpc` 3）· 5 处 `schema/field.go:1009` **全在 `user-rpc`**（`UserDomainService.java:192` / `:447` · `UserDomainServiceTest.java:699` · `UserRpcLoginNullStatusReadsAsZeroTest.java:45` · `UserEntity.java:65`）⇒ 并已核真源：`go.mod:28` = **`gorm.io/gorm v1.31.1`** · `gorm@v1.31.1/schema/field.go` = **1004 行**（`v1.31.2` 是 **1011 行** ⇒ **旧引用在错的版本下看起来合法** ⇒ 这正是「引到另一份内容不同的副本」的隐蔽性）。
3. 🟡 **§8.4 与我域 §69 的关系**：§8.4 说「849/849 在严格口径下不成立」⇒ 但我域 §69.4 / §69.5 **已自行披露**了判据的两条失效模式（同名文件「多候选都试」+ 核验脚本自身的失效模式），并写明「**属『声明的，不是可复算的』**」⇒ ⇒ 建议收口把 §69 结论**限定表述**为「**在该判据口径下未发现错误**」；而 §8.7 的 21 条是**另一件事**（实测真欠账）⇒ **两者不冲突，不必整体推翻**（本轮**未改** §69 表述 ⇒ 交收口执行者）。
4. ✅ **`admin-rpc` 的 `admin_invite.update_time` 不推进**（`admin/rpc.md:1783` 新发现 · 与 `Pitfalls/users/admin/rpc.md` **P-2** 同款形状：**user-rpc 修过、admin-rpc 漏修**）⇒ **域侧可自修**⇒ **已纳入本候选任务**（**Jason 2026-10-29 04:0x 追加拍板** ⇒ 范围见本节末尾 ⑤ 行）
5. 🟡 **`S1-FOLLOW-001`**（follow-rpc 指标出口）—— 仍是唯一待**你重新拍板**项（**不在本轮候选任务内**；重开 = 全仓 8 个 rpc 组件的统一方案 ⇒ 非 G1 单方可定）。
6. 🔴 **官方核验命令的「批准」列判据有缺陷**（**共享面 ⇒ 本 lane 不改 ⇒ 只登记**）：`docs/Worktree.md` §二·五 的核验命令里，**批准列 = 第 2 个 `grep -c`**，其模式串是 **「Jason」+「批准」两词连续**（原文见该节「核验命令」代码块）⇒ 而**回执模板自带的 ⑤ 标题就含这串** ⇒ **任何只要写了回执的 lane，该列天然 ≥ 1** ⇒ ⇒ **「两列都要 ≥ 1 才算已开工」的判据对本 lane 天然满足** ⇒ **收口会把未获批准的 lane 误判为已开工**。（**该缺陷已于 2026-10-29 03:2x 修复**：判据升级为第 4 版 `⑤ 批准记录：Jason · <日期>`（锚定 ⑤ 标记 + 要求批准人 + 要求日期）⇒ 本节 ⑤ 按新判据填入 ⇒ **不受此缺陷影响**）
   ⇒ **本回执落笔时** ⑤ 标题刻意写成「⑤ 批准记录」（不含批准人串）⇒ 当时批准列 = **0** ⇒ 如实反映「未获批准」；⇒ **Jason 于 2026-10-29 03:45 在本 lane 会话内明示「继续」**（消息 id `182057`）⇒ 批准成立 ⇒ **⑤ 已由调度员代填**（见本节末尾 ⑤ 行）。
   ⇒ **建议收口把判据改成「⑤ 冒号后有内容」**（把该 `grep -c` 换成带 `E` 的行首锚定写法，匹配 `批准记录：` 后有非空字符的行）⇒ **属共享面 ⇒ 交收口执行者**。

**④ 等批准 ⇒ 批准后才开工**
**回执落笔时**本轮只读 + 写本回执（未获批准前不写任何代码、不改任何台账，本小节除外）；⇒ **Jason 于 2026-10-29 03:45 在本 lane 会话内明示「继续」**（消息 id `182057`）⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）⇒ 本轮按 ① 所列候选任务执行。
⚠️ **本节 ⑤ 留空 = 未获批准**（**留空不等于已批准**）；⇒ **本节 ⑤ 已由调度员于 2026-10-29 03:45 依据 Jason 的明示代填**（批准人 / 时间 / 范围见 ⑤ 行）⇒ **代填 ≠ 代批准**：批准来自 Jason 在本 lane 会话内的明示，调度员只负责落笔。

**⑤ 批准记录：Jason · 2026-10-29 03:45 · 批准范围：本 lane 回执 ① 所列候选任务（修 §8.7 登记的 Go 出处真缺陷）—— §8.7 实测口径 = **23 条**（15 版本写错 + 5 越界 + 3 路径；回执 ① 的「21 条」已被本 lane ③ 第 1 条订正为 23）· ③ 第 4 项 `admin_invite.update_time` **已纳入**（Jason **2026-10-29 04:0x 追加拍板** ⇒ `admin-rpc` 的 `admin_invite.update_time` 不推进 ⇒ **域侧可自修**；与 `docs/Pitfalls/users/admin/rpc.md` **P-2** 同型：**user-rpc 修过、admin-rpc 漏修**）** · ⚠️ **2026-10-29 04:2x 实测结论：该项已闭合 ⇒ 本轮为它 0 行改动**（`AdminCreateAtomicService.java:201` 已有 `.setSql("update_time = CURRENT_TIMESTAMP")` ＋ `AdminRpcGoWireParityTest.createAdminAdvancesInviteUpdateTimeLikeGo()` 真 PG + 真 gRPC 前后对比断言 `after > before` ＋ **变异测精确红在目标用例** ⇒ 网有牙） ⇒ **「已批准但实测无需改动」是合法结果**，非漏做　← 由**调度员代填**（依据：Jason 于 2026-10-29 03:45 在**本 lane 会话内**明示「继续」· 消息 id `182057`；**回执人不得自批**· **代填 ≠ 代批准**）

---

<!-- @new 2026-10-29 · `align/r2-users-common` · Round-2 T-2.6（G-4）· **指针块**（正文在 users-common 自己的台账里） -->

## 2026-10-29 指针：`users-common` 组件的活体证据**已落仓**（Round-2 T-2.6 / G-4）

> ⚠️ **本节是「指针」而不是本 lane 的对账主体** —— 本文件是「users ↔ `service/common`」的
> **跨层**台账；**`users-common` 组件自己的**台账是 [`common.md`](common.md)。

| 项 | 结论 |
|---|---|
| 缺口 | `docs/PhaseGoal-judge.md` §九 **G-4**：「`users-common` 的 176 条双侧活体证据在 `/tmp/commonprobe/**`，**已消失** ⇒ 不可复算」 |
| 本轮补的 | 探针**落进仓库** ⇒ `tools/api-full-parity-probe/users-common/{run.sh,mutate-parity.sh,expected.txt,compare.py,go/,java/,README.md}` |
| 实跑 | `bash tools/api-full-parity-probe/users-common/run.sh` ⇒ **EXIT=0**（218 条两侧逐字节相同 · 25 条单侧专属 · 17 条已登记不同）<br>`bash tools/api-full-parity-probe/users-common/mutate-parity.sh` ⇒ **EXIT=0**（M1~M4 各**精确**转红 + 还原后转绿） |
| 证据强度 | **L4 双侧活体** ＋ 🆕 **可复算**（原 176 条的载体已消失 ⇒ 只能读作「声明」） |
| 新抓到的一条 | 🔴 **`DRIFT-USERSCOMMON-006`**：`BearerTokenExtractor.tokenForSignatureVerification("Bearer ")` 差一（Go 剥成空串 / RAG 不剥）⇒ **潜伏 · wire 不可观察** ⇒ **共享层写权归 G4** ⇒ 只登记不落码 |
| 逐条证据 | ⇒ [`common.md` 的「2026-10-29 第六轮」](common.md) |
<!-- @new 2026-10-29 · `align/r3-ddl-users-follow` · RoundPlan `docs/RoundPlan/Round-3.md` **T-3.1 的 G1 侧（`users` 域）** · 基线 = `do-my-own-gh` @ `4a4beaf8` -->

## 72. 🟢 **DDL 逐域机器核**（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G1 lane · `users` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**（AGENTS.md §3.1 对照清单的最后两项）；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并**逐域实跑**，但结论只落在 `docs/PhaseGoal-judge.md` §七 **G-8 / G-9** ⇒ **域级证据链断了一节** ⇒ 本节把 `users` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不修任何差异**（RoundPlan T-3.1：「冒出的新差异**按域登记**，**不在本轮当场修**」）。
> ⚠️ **本节零改动 `service/**`** —— `service/users/users-common/**` 是 **G4 独占写权**（AGENTS.md §5.3f）⇒ 变异测试走**逐字节相同的临时副本**（§72.5）⇒ 提交后 `git diff --stat -- service/` = **0 行**。

### 72.1 执行口径（命令原文 · EXIT · 日期 · 环境）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（09:24–09:31 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-users-follow` · `align/r3-ddl-users-follow`<br>⚠️ **实跑时的基点 = `do-my-own-gh` @ `4a4beaf8`**；**提交时已 rebase 到 `do-my-own-gh` @ `49bd2dd1`** ⇒ 上游这两个提交**只动了 `.gitignore` 与 `docs/PhaseGoal-judge.md`**，**既没动本域这两个文件、也没动任何 `.sql`** ⇒ **实跑结论不受影响** |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本只**复制**副本，从不写入（`git status` 真源仓零改动） |
| 容器 / 库 | `ddl-users-pg`（`postgres:16-alpine`）· Go 库 `go_users_db` · RAG 库 `rag_users_db` |
| Go 侧 schema 来源 | **真源自己的 `AutoMigrate`**：`service/user/user/rpc/internal/model/db.go:33`（`AutoMigrate(&User{}, &UserAvatarHistory{})`）+ `service/user/admin/rpc/internal/model/db.go:15`（`AutoMigrate(&Admin{}, &AdminInvite{}, &User{})`）⇒ 探针**两个包都跑** ⇒ Go 侧 4 张表齐全（admin 两张表由 admin/rpc 自己的 `InitDB` 产出） |
| RAG 侧 schema 来源 | 仓内 `service/users/users-common/src/main/resources/db/01~04_*.sql`（**`SchemaInitializer` 幂等重放**，本域**不用 Flyway**）⇒ 清单与生产**同一个产生点**（§72.6） |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 下面所有差异数是**原始值**，没有被白名单压过 |
| 逐列实测样本 | `users_user.username/password/email/extra_info` 两侧均 `text`（E-3 根因那批，**已清零**）· `status` 两侧均 `bigint` · `create_time/update_time/expires_at/used_at` 两侧均 `timestamp with time zone` · `is_current` 两侧均 `boolean` |

**命令 ① —— 建库 + 双侧活体对拍（索引 / 约束 / 触发器 + 源码↔活库 一次跑全）**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-users-follow"
bash tools/difftest/ddl-parity.sh users
```

⇒ **EXIT=1** —— 末行 `== 实质差异合计 16 处 ⇒ 退出码 1 ==`；runner 行 `=== 对拍退出码: 1 ===`
⇒ 中途两个 Go 探针各打印 `AUTOMIGRATE_OK`；RAG 侧按序执行 4 份 `.sql`（`01_create_user.sql` → `02_create_user_avatar_history.sql` → `03_create_admin.sql` → `04_create_admin_invite.sql`）

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`）**

```bash
python3 tools/difftest/check-ddl-static.py --domain users --indexes --check-source
```

⇒ **EXIT=1**（16 处）—— **与 ① 同数同因** ⇒ 两条路径互为复算（① 是「建库 + 对拍」，② 是「只对拍已建好的库」）

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain users --print-rag-sql
```

⇒ **EXIT=0**，输出 4 行，与 ① 实际执行的 4 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**（G-9 的「单一产生点」在 `users` 域成立）

### 72.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（G-9 `--check-source`） | 4 张表 | ✅ **0 差异** —— 仓里的 `.sql` 在**干净临时库** `rag_users_db_srccheck` 上原样重放后，与活库**逐项一致**（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器） | 0 |
| 2 | **列类型 / 长度** | 35 列 ↔ 35 列（键集合一致 ✓） | ✅ **0 差异** —— **35 列逐字相同** | 0 |
| 3 | **可空性（NOT NULL）** | 35 列 | 🔴 **1 处残留** | 1 |
| 4 | **DEFAULT** | 35 列 | 🔴 **8 处残留**（另 4 处 = 序列名映射副产品 ⇒ **非真差**，不计失败） | 8 |
| 5 | **索引**（`pg_indexes`） | Go 16 / RAG 17 | 🔴 **7 处残留**（另 13 处 = 命名形态差 ⇒ 语义等价，不计失败） | 7 |
| 6 | **唯一约束 / 主键**（`pg_constraint`） | Go 8 / RAG 12 | ✅ **0 处实质差异**（12 处 = 命名形态差 / 唯一索引↔唯一约束**载体不同** ⇒ 语义等价） | 0 |
| 7 | **触发器**（`pg_trigger`） | 两侧各 0 | ⚪ **两侧都没有触发器 ⇒ 本维度「无对象可比」** —— ⚠️ **这不是「触发器已对齐」** | 0 |
| 8 | **触发器形式 / 触发器函数体结构化属性** | 两侧各 0 | ⚪ **同上 ⇒ 无对象可比** —— 脚本原话：「两侧**没有可成对比对的触发器** ⇒ 本维度**什么都没比**（不是「已对齐」）」 | 0 |
| | **合计** | | | **16** |

> ⚠️ **读法**：本域 DDL 的**行为层**（类型 / 长度 / 可空性 / DEFAULT / 索引 / 唯一约束）在 **8 个维度里 5 个为 0**；剩下的 16 处**全部是「登记在案的已知差异」**，不是新回归（逐条见 §72.4）。
> ⚠️ **维度 7/8 不是「通过」而是「无对象」** —— Go 侧 `users` 域**根本没有触发器**（真源 `service/user/**` 全域无 `CREATE TRIGGER`）⇒ 本域的触发器语义**没有可核对象**。

### 72.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 35 列逐字相同（0 处差异）**

| 抽样列 | Go 侧类型 | RAG 侧类型 | 判定 |
|---|---|---|---|
| `users_user.username` / `.password` / `.email` | `text` | `text` | ✅ 一致（**E-3 根因已清零**：Go tag 无 `size` ⇒ GORM 建 `text`；RAG 侧已从 `varchar(255)` 改 `text`） |
| `users_user.extra_info` | `text` | `text` | ✅ 一致（Go tag 是 `serializer:json` ⇒ **存字节**，不是 `jsonb` ⇒ 见 `01_create_user.sql:92-103` 的论证） |
| `users_user.status` / `.uid` / `.score` | `bigint` / `bigint` / `integer` | `bigint` / `bigint` / `integer` | ✅ **类型一致**（⚠️ `status` 的**可空性**不一致 ⇒ 见 §72.4 残留 ①） |
| `users_user.create_time` / `.update_time` | `timestamp with time zone` | `timestamp with time zone` | ✅ 类型一致（⚠️ **DEFAULT** 不一致 ⇒ 见 §72.4 残留 ②） |
| `admin_admin.*` / `admin_invite.*` / `users_user_avatar_history.*` | — | — | ✅ 全部逐字相同（工具输出 `类型/长度差异 0 处` + `逐字相同 35 列`） |

**② 源码 ↔ 活库 —— 0 差异（G-9 维度，本轮新增）**

- 目录扫描得到 **4 份**迁移脚本（`01_create_user.sql` / `02_create_user_avatar_history.sql` / `03_create_admin.sql` / `04_create_admin_invite.sql`）⇒ **清单由目录扫描产生**，不会落后于仓。
- 在**干净临时库** `rag_users_db_srccheck` 上**原样重放**（交给 PG 自己解析 ⇒ 与生产同一条路径）⇒ 与活库逐项对比：
  **表集合 4 ↔ 4** · **列集合 0 差异** · **列属性 0 差异** · **可空性 / DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致`
- ⇒ **含义**：本域仓里的 `.sql` 与**当前活库**之间**没有分叉** ⇒ 「迁移改了没人重跑」这一类漂移**在 `users` 域为零**。

**③ 唯一约束 / 主键 —— 0 处实质差异（12 处语义等价，脚本判为不计失败）**

| 语义等价配对（Go ≡ RAG） | contype | 定义 |
|---|---|---|
| `admins_pkey` ≡ `admin_admin.admin_admin_pkey` | p | `PRIMARY KEY (id)` |
| `admin_invites_pkey` ≡ `admin_invite.admin_invite_pkey` | p | `PRIMARY KEY (id)` |
| `users_pkey` ≡ `users_user.users_user_pkey` | p | `PRIMARY KEY (id)` |
| `user_avatar_history_pkey` ≡ `users_user_avatar_history.users_user_avatar_history_pkey` | p | `PRIMARY KEY (id)` |
| `uni_users_email` ≡ `users_user.users_user_email_key` | u | `UNIQUE (email)` |
| `uni_users_username` ≡ `users_user.users_user_username_key` | u | `UNIQUE (username)` |
| `uni_admins_email` ≡ `admin_admin.admin_admin_email_key` | u | `UNIQUE (email)` |
| `uni_admins_username` ≡ `admin_admin.admin_admin_username_key` | u | `UNIQUE (username)` |
| `idx_users_uid` ≡ `users_user.users_user_uid_key`（**唯一索引 ↔ 唯一约束，载体不同**） | u | `UNIQUE (uid)` |
| `idx_admins_uid` ≡ `admin_admin.admin_admin_uid_key`（**载体不同**） | u | `UNIQUE (uid)` |
| `idx_admin_invites_code` ≡ `admin_invite.admin_invite_code_key`（**载体不同**） | u | `UNIQUE (code)` |
| `idx_user_avatar_history_history_id` ≡ `users_user_avatar_history.…_history_id_key`（**载体不同**） | u | `UNIQUE (history_id)` |

⇒ **语义影响为零**：PG 层面的唯一性判定、`23505` 报错、`ON CONFLICT` 仲裁完全相同；差别只是 `pg_constraint.conname` 的字符串。

**④ 索引命名形态差 —— 13 处语义等价（不计失败）**

| 语义等价配对 | 唯一性 | 列 |
|---|---|---|
| `users_pkey` ≡ `users_user_pkey` | unique | `id` |
| `admins_pkey` ≡ `admin_admin_pkey` | unique | `id` |
| `admin_invites_pkey` ≡ `admin_invite_pkey` | unique | `id` |
| `user_avatar_history_pkey` ≡ `users_user_avatar_history_pkey` | unique | `id` |
| `uni_users_email` ≡ `users_user_email_key` | unique | `email` |
| `uni_users_username` ≡ `users_user_username_key` | unique | `username` |
| `uni_admins_email` ≡ `admin_admin_email_key` | unique | `email` |
| `uni_admins_username` ≡ `admin_admin_username_key` | unique | `username` |
| `idx_users_uid` ≡ `users_user_uid_key` | unique | `uid` |
| `idx_admins_uid` ≡ `admin_admin_uid_key` | unique | `uid` |
| `idx_admin_invites_code` ≡ `admin_invite_code_key` | unique | `code` |
| `idx_user_avatar_history_history_id` ≡ `users_user_avatar_history_history_id_key` | unique | `history_id` |
| `idx_user_avatar_history_uid` ≡ `idx_users_user_avatar_history_uid` | non-unique | `uid` |

**⑤ 序列名映射的副产品 —— 4 处（非真差，脚本不计失败）**

| 列 | Go 侧 DEFAULT | RAG 侧 DEFAULT | 判定 |
|---|---|---|---|
| `users_user.id` | `nextval('users_id_seq'::regclass)` | `nextval('users_user_id_seq'::regclass)` | ✅ **非真差** —— 两侧表名不同 ⇒ PG 建的序列名不同；两者都指向「本表主键的下一值」⇒ 语义等价 |
| `admin_admin.id` | `nextval('admins_id_seq'::regclass)` | `nextval('admin_admin_id_seq'::regclass)` | ✅ 同上 |
| `admin_invite.id` | `nextval('admin_invites_id_seq'::regclass)` | `nextval('admin_invite_id_seq'::regclass)` | ✅ 同上 |
| `users_user_avatar_history.id` | `nextval('user_avatar_history_id_seq'::regclass)` | `nextval('users_user_avatar_history_id_seq'::regclass)` | ✅ 同上 |

### 72.4 残留 16 处（逐条列出 + 定级 + 签字状态）

> **定级口径** = `docs/twoStage.md` **§十**（`twoStage.md:941-973`）：**A** = 阶段 1 准入阻断 · **B** = 阶段 1 竞争力补强 · **C** = 阶段 2 最终工程建设 · **D** = 经分析确认可不实现或不适用（**须有明确理由**）。
> ⚠️ **一条差异 ≠ 一个缺陷**：本节 16 处 ⇒ 归成 **4 条**（可空性 1 · DEFAULT 8 · 索引 7）。

#### 残留 ① 🔴 可空性 1 处 —— `users_user.status`（Go 可空 / RAG `NOT NULL`）

| 项 | 内容 |
|---|---|
| 差异 | `users_user.status`：`is_nullable` Go = `YES` / RAG = `NO`（**类型两侧都是 `bigint` · DEFAULT 两侧都是 `0` ⇒ 只有可空性一项不同**） |
| Go 出处 | `Sea-RideTheWind_golang/service/user/user/rpc/internal/model/data_model.go:11` —— `Status int64 \`gorm:"column:status;default:0"\`` ⇒ **有 `default:0`、无 `not null`** ⇒ GORM 建出**可空**列（`admin/rpc` 侧的同名字段在 `service/user/admin/rpc/internal/model/data_model.go:41`，同一形态） |
| RAG 出处 | `service/users/users-common/src/main/resources/db/01_create_user.sql:39` —— `status      BIGINT NOT NULL DEFAULT 0,` |
| 签字状态 | ✅ **已登记 `SD-2026-10-25-CAND-17`** —— **🏷 待签字候选 · 未签字**（[`docs/Intentional_Deviations/users/_shared.md:101`](../../Intentional_Deviations/users/_shared.md)）· DRIFT 侧 `DRIFT-USERRPC-V-002`（[`docs/DRIFT/users/_shared.md`](_shared.md) §2） |
| **定级** | **C 类**（阶段 2 最终工程建设项） |
| 定级理由 | ① 方向是「**RAG 更严**」⇒ 正常写入路径（两侧都显式绑 `status`）结果完全相同 ⇒ **不构成阶段 1 准入阻断** ⇒ **不是 A 类**；② 但它与 `admin-rpc` 的 `UserEntity.status = 0L`（`DRIFT-USERRPC-V-001`）**耦合** —— 单独放开 `NOT NULL` 会让 `WHERE status = 0` **匹配到 `NULL` 行**（签字档 §「必须一起读的那条耦合」原文）⇒ 属「**业务语义恢复**」⇒ 阶段 2 ⇒ **C 类**；③ **不是 D 类** —— 签字档明写「本条签字只认账『RAG 更严』这个事实，**不关闭** `admin-rpc` 的 `status = 0L` 缺陷」⇒ 没有「可不实现」的分析结论 |
| 处置 | **本轮不修**（RoundPlan T-3.1「不修」+ 已登记为待签字候选）⇒ 也**不重复质疑**（AGENTS.md §0：「对拍看到它们变红，先确认是不是签字项，不要当新回归去『修』」） |

#### 残留 ② 🔴 DEFAULT 8 处 —— 4 张表 × 2 个时间列 `DEFAULT CURRENT_TIMESTAMP`（Go 侧无默认）

| 表（RAG） | 列 | Go 侧 DEFAULT | RAG 侧 DEFAULT | Go 出处（tag 里**没有** `default`） | RAG 出处 |
|---|---|---|---|---|---|
| `users_user` | `create_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/user/rpc/internal/model/data_model.go:14`（`autoCreateTime`） | `01_create_user.sql:42` |
| `users_user` | `update_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/user/rpc/internal/model/data_model.go:15`（`autoUpdateTime`） | `01_create_user.sql:43` |
| `users_user_avatar_history` | `create_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/user/rpc/internal/model/data_model.go:30` | `02_create_user_avatar_history.sql:30` |
| `users_user_avatar_history` | `update_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/user/rpc/internal/model/data_model.go:31` | `02_create_user_avatar_history.sql:31` |
| `admin_admin` | `create_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/admin/rpc/internal/model/data_model.go:12` | `03_create_admin.sql:59` |
| `admin_admin` | `update_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/admin/rpc/internal/model/data_model.go:13` | `03_create_admin.sql:60` |
| `admin_invite` | `create_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/admin/rpc/internal/model/data_model.go:27` | `04_create_admin_invite.sql:44` |
| `admin_invite` | `update_time` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/user/admin/rpc/internal/model/data_model.go:28` | `04_create_admin_invite.sql:45` |

| 项 | 内容 |
|---|---|
| 成因 | Go 的 `autoCreateTime` / `autoUpdateTime` 由 **GORM 在 Go 代码里赋值** ⇒ **不建 DB 默认**；RAG 侧多一条 DB 级默认 |
| 签字状态 | ✅ **已登记 `SD-2026-10-25-CAND-16`** —— **🏷 待签字候选 · 未签字**（[`docs/Intentional_Deviations/users/_shared.md:17`](../../Intentional_Deviations/users/_shared.md)）· DRIFT 侧 `DRIFT-USERRPC-V-011` |
| **定级** | **D 类**（分析结论指向 D）—— ⚠️ **但现状仍是「待签字候选 · 未签字」** ⇒ 严格说现在只到「**待裁决**」这一步 |
| 定级理由 | ① DEFAULT 只在「**INSERT 省略该列**」时发作 ⇒ 两侧**所有** INSERT 都显式绑定该列 ⇒ **当前不可观察**；② 删掉它是为「**不可观测的维度**」冒险（可能有人在裸 SQL 路径上依赖了这个默认）⇒ **收益低于风险**；③ 同形态的 `message` 域 6 处**已签** `SD-2026-10-24-04`（「**按 message 域口径签字，而不是改 DDL**」）⇒ **先例一致** ⇒ 有明确理由的「不实现」⇒ D 类 |
| 处置 | **本轮不修** ⇒ **要关闭它，动作是「签字」而不是「改 DDL」**（⚠️ 登记类事项**不升级为拍板**，AGENTS.md 用户偏好） |

#### 残留 ③ 🟡 索引 7 处 —— Go 独有 3（真缺） + RAG 独有 4（冗余）

**③-a Go 独有 3 条（**真缺**）**

| 索引名 | 表（RAG 侧名） | 列 | 唯一性 | Go 出处 |
|---|---|---|---|---|
| `idx_admin_invites_expires_at` | `admin_invite` | `expires_at` | 非唯一 | `service/user/admin/rpc/internal/model/data_model.go:24`（`ExpiresAt time.Time \`gorm:"column:expires_at;index;not null"\``） |
| `idx_admin_invites_inviter_uid` | `admin_invite` | `inviter_uid` | 非唯一 | `service/user/admin/rpc/internal/model/data_model.go:23`（`InviterUid int64 \`gorm:"column:inviter_uid;index;not null"\``） |
| `idx_user_avatar_history_is_current` | `users_user_avatar_history` | `is_current` | 非唯一 | `service/user/user/rpc/internal/model/data_model.go:29`（`IsCurrent bool \`gorm:"column:is_current;index;not null;default:false"\``） |

- **定级：C 类**（阶段 2 最终工程建设项）
- **定级理由**：① 三条都是**非唯一**普通索引 ⇒ PG **只用唯一索引 / 约束判唯一** ⇒ 缺失**不改变任何写入语义、冲突行为、唯一性判定** ⇒ **不是 A 类**（不阻断阶段一准入）；② 严格说它**不是行为差异，而是查询计划差异**（性能）⇒ 补齐属「生产工程机制」的最终形态 ⇒ 阶段 2 ⇒ **C 类**；③ **不是 D 类** —— `is_current` 那条**有业务语义**（「当前头像」标记，查询形态是 `WHERE uid=? AND is_current=true`）⇒ 说「不适用 / 可不做」不成立，只是 RAG 侧现由复合索引 `idx_users_user_avatar_history_uid_is_current` 覆盖 ⇒ **实际不慢**，只是索引构成与 Go 不同。
- 跨域台账同款判定（**只读引用，我无权改**）：[`docs/DRIFT/_cross/ddl-type-divergences.md` §2.3.1](../../DRIFT/_cross/ddl-type-divergences.md) —— 「**性能差异，非行为差异**」。

**③-b RAG 独有 4 条（**冗余，非漂移**）**

| 索引名 | 表 | 列 | 唯一性 | Go 侧对应 |
|---|---|---|---|---|
| `idx_users_user_uid` | `users_user` | `uid` | 非唯一 | `idx_users_uid` ≡ `users_user_uid_key`（**唯一**） |
| `idx_admin_admin_uid` | `admin_admin` | `uid` | 非唯一 | `idx_admins_uid` ≡ `admin_admin_uid_key`（**唯一**） |
| `idx_admin_invite_code` | `admin_invite` | `code` | 非唯一 | `idx_admin_invites_code` ≡ `admin_invite_code_key`（**唯一**） |
| `idx_users_user_avatar_history_uid_is_current` | `users_user_avatar_history` | `uid`, `is_current` | 非唯一 | `idx_user_avatar_history_uid`（单列 `uid`）＋ Go 独有的 `idx_user_avatar_history_is_current`（单列 `is_current`） |

- **定级：D 类**（经分析确认不实现 —— **有明确理由**）
- **定级理由**：① 每条对应列在 Go 侧**都已有等价 UNIQUE 约束 / 索引** ⇒ 唯一性判定、`23505`、`ON CONFLICT` 仲裁完全相同 ⇒ **语义等价**，只多占一点写入成本；② 处置建议（`ddl-type-divergences.md` §2.3.2 原文）：「**可删，但收益小于风险**（删索引在生产大表上是 DDL 风险，收益只是省一点写入开销）⇒ **不建议动**，登记为**已知冗余**」⇒ 明确理由的「不实现」⇒ **D 类**。
- ⚠️ 与残留 ③-a 是**同一族**（索引构成不同），但**定性相反**：③-a 是「少」⇒ 补（可延后）；③-b 是「多」且**语义等价** ⇒ 不动。

### 72.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：`service/users/users-common/**` 是 **G4 独占写权**（AGENTS.md §5.3f）⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**（任务书允许的「**临时副本**」路线）：把 `check-ddl-static.py` 与该域的 `db/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ `REPO_ROOT` 与仓内布局一致），**逐字节校验 sha256 相同**，再在**副本**上做变异。
> ⇒ 守门读到的**仍是仓里那份 `.sql` 的内容**（同一份字节），只是不在仓里写。

**变异 ①：`users_user.status` 的列类型（`BIGINT` → `INTEGER`）**

| 步骤 | 命令 | 结果 |
|---|---|---|
| 基线 | `python3 tools/difftest/check-ddl-static.py --domain users --indexes --check-source`（临时树） | **EXIT=1**（16 处）· 源码↔活库 = `✅ 一致` |
| 变异 | `01_create_user.sql`：`status      BIGINT NOT NULL DEFAULT 0,` → `status      INTEGER NOT NULL DEFAULT 0,`（改**一个列的类型**，命中目标行 **1** 处） | — |
| 变异后重跑 | 同上命令 | **EXIT=1**（**17** 处 = 16 + **1**）· 源码↔活库 **转红**：<br>`🔴 列属性不同：users_user.status  源码=('integer', '')  活库=('bigint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 改回 `BIGINT` | — |
| 还原后重跑 | 同上命令 | **EXIT=1**（**16** 处 · 回到基线）· 源码↔活库 回到 `✅ 一致` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **两侧同为 `dc8378d6b4faf7d29c151615a49cb2558b0c548ca45eba373010313c9b064723`** ⇒ 逐字节回到变异前 |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`users_user.status`），其余 16 处**不变** ⇒ 守门**不是假绿**；还原后**精确回到基线** ⇒ 无残留。

⚠️ **users 域的「EXIT=0 期望」口径**：本域 Go↔活库**本就有 16 处已登记差异** ⇒ 还原后的正确期望值是 **EXIT=1（16 处）**，而**不是** EXIT=0 ⇒ 变异判据用的是「**源码↔活库这一维度的差异数 0 → 1 → 0**」，不是总退出码。

**变异 ②（`follow` 域）：`follow_relation.user_id` 的列类型（`BIGINT` → `INTEGER`）** ⇒ 见 [`docs/DRIFT/follow/vs-common.md` §16.5](../follow/vs-common.md)。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-users-follow"
git status --short          # ⇒ 空
git diff --stat -- service/ # ⇒ 0 行
```

### 72.6 证据强度 · 效力边界 · 未能核到的部分

**证据强度 = L4（运行面 · 双侧活体）** —— 两侧各建一个**真 PG 16** 库（Go 侧跑真源自己的 `AutoMigrate`、RAG 侧跑仓里的真迁移脚本）⇒ 逐列 / 逐索引 / 逐约束 / 逐触发器 dump 后机器比对。
⚠️ 本节所有 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。

**效力边界（越界即失效）**

| # | 边界 | 对本域是否成立 |
|---|---|---|
| 1 | **`--check-source` 的重放引擎 ≠ 生产引擎** —— 重放用 **psql**；本域生产用 **spring-jdbc `ScriptUtils`**（`service/users/users-common/src/main/java/com/rag/users/common/ddl/SchemaInitializer.java:63-68`，按文件名顺序执行 `01/02/03/04` 四份）⇒ **两个切句器不同** | ✅ **对本域不成立** —— 已核：四份 `.sql` 里 `$$` 的 **7 处命中全部在 `--` 注释里**（`01:140,143` · `02:83` · `03:119,122` · `04:91,92`，全是「为什么**不用** `DO $$ … $$`」的说明）⇒ **可执行部分无美元引号块** ⇒ 两个切句器看到的语句序列相同 |
| 2 | **迁移清单 = 生产清单** —— `discover_rag_sql` 目录扫描产出 4 份，与 `SchemaInitializer.SCRIPTS` 的 4 份**逐条一致**（命令 ③ 与 ① 的执行日志互证） | ✅ **成立**（本域**不用 Flyway**，用 `SchemaInitializer` 幂等重放 ⇒ 无「账本版本号」问题） |
| 3 | **触发器函数体的语义**（它到底在拒绝什么）机器判不了 ⇒ 走人工逐条 | ✅ **不适用**（本域两侧各 0 个触发器） |
| 4 | **`pg_constraint` 只比 `contype` + 定义文本，不解析外键的引用目标**（脚本文件头第 44–45 行） | ✅ **不适用**（本域两侧 `contype='f'` 各 **0 行** ⇒ 无外键可解析） |
| 5 | **分区 / 存储过程 / 触发器函数**：脚本不查 | ✅ **不适用**（四份 `.sql` 的**可执行部分**无 `PARTITION` / `PROCEDURE` / `FUNCTION` / `CREATE SEQUENCE` 关键字 ⇒ 已人工核；`BIGSERIAL` 的序列由 PG 隐式创建，已计入 §72.3⑤ 的「序列名映射副产品」） |
| 6 | **CHECK 约束** —— 脚本实际是经 `pg_constraint` 一并比对的 | ⚪ **两侧各 0 行**（人工补查 `contype='c'`：`go_users_db` / `rag_users_db` 均 **0**）⇒ **无对象可比** |

**未能核到的部分（明确写「未核」+ 原因，不含糊）**

| # | 未核项 | 原因 | 影响 |
|---|---|---|---|
| 1 | **触发器形式 / 触发器函数体结构化属性** | 本域两侧**各 0 个触发器** ⇒ 无可比对对象 ⇒ 脚本自己输出「本维度**什么都没比**」 | 本域**不存在**触发器语义漂移的可能（真源 `service/user/**` 全域无 `CREATE TRIGGER`）；但这**不等于**「触发器已对齐」 |
| 2 | **CHECK / 外键 / 分区 / 存储过程** | 脚本不查（外键只比 `contype` + 定义文本）⇒ **人工补查**后两侧均为 0 行 ⇒ 该维度**人工闭合** | 无残留；但结论强度 = **L4 + 人工补查**，不是脚本原生结论 |
| 3 | **那 8 处 DEFAULT 是否真的「不可观察」** | 「两侧所有 INSERT 都显式绑定该列」是**写入路径**的行为层结论 ⇒ **不在 DDL 维度内** ⇒ 本节**未核**，沿用签字档 `SD-2026-10-25-CAND-16` 的既有结论 | 若将来新增一条**裸 SQL 写路径**，这 8 处会从「不可观察」变成「可观察」⇒ **复核触发点** |
| 4 | **缺失的 3 条索引对真实查询计划的影响** | 需要 `EXPLAIN` + 真实数据量 ⇒ 本轮**未核** | 只能断言「**非唯一**索引缺失不改变行为语义」；**性能影响未量化** |
| 5 | **Go 侧 schema 与生产的完全等价性** | 探针调的是真源 `model.InitDB`（同一份 struct tag + 同一个 `gorm.io/driver/postgres` 版本）⇒ 等价；但**真源手写 `.sql` 那一半**（`ddl-parity.sh` 的 `GO_SQL` 清单）**本域为空** ⇒ 无遗漏 | 无缺口；本域 Go 侧 schema **全部**由 `AutoMigrate` 产出 |
| 6 | **`admin` 侧两张表由另一个包产出** | 探针**已同时跑** user/rpc 与 admin/rpc 两个包（否则 `expected_tables` 闸门会报「Go 侧缺 `admin_admin`」）⇒ Go 侧 4 张表齐全 | 已闭合（闸门 `expected_tables` 命中 4/4） |

### 72.7 与其它台账的关系（指针，不改别的文件）

| 台账 | 本节与它的关系 |
|---|---|
| [`docs/DRIFT/_cross/ddl-type-divergences.md`](../../DRIFT/_cross/ddl-type-divergences.md) | **同源结论、但口径已过期** —— 该文件 §〇 的容器/库名还写着 `difftest-postgres` / `go_db` / `rag_db`（2026-10-25 实测期），现役是 `ddl-users-pg` / `go_users_db` / `rag_users_db`；其 §二.1 / §二.2 / §二.3 的**条数与分类与本节逐条一致**（1 + 8 + 3 + 4）⇒ **本节是它的 2026-10-29 复核**（含 `--check-source` 与触发器维度）。⚠️ **该文件在 `_cross` ⇒ 本 lane 无写权 ⇒ 只在此登记，报给收口执行者** |
| [`docs/Intentional_Deviations/users/_shared.md`](../../Intentional_Deviations/users/_shared.md) | 残留 ① ＝ `SD-2026-10-25-CAND-17` · 残留 ② ＝ `SD-2026-10-25-CAND-16` ⇒ **已登记 · 未签字** ⇒ 本节**不重复质疑、不改回** |
| [`docs/DRIFT/_cross/open-items.md`](../../DRIFT/_cross/open-items.md) | 其 **P3 行**（users 域 8 处 DEFAULT 残留）＝ 本节残留 ② ⇒ 一致；⚠️ 该行写「已登记 `SD-2026-10-25-CAND-16`（未签字）」⇒ 与本节口径一致 |
| [`docs/PhaseGoal-judge.md` §七 **G-8 / G-9`](../../PhaseGoal-judge.md) | 主线结论（G-8 / G-9 已闭合 · 9 域逐域跑）—— 本节是 `users` 域的**域级落账**，不重复改主线结论 |
| [`docs/DRIFT/users/_shared.md`](_shared.md) §2（真库对照表 · **V-002 / V-011**） | 域内 DRIFT 侧的同两条目 ⇒ 本节给出**2026-10-29 的机器复核**（`--indexes` + `--check-source`），并把它们与签字档的编号对上 |

---
