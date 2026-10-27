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
| ✅ `UNRESOLVED-USERSCOMMON-003` | `http/GoStyleCoercion` 的**全局副作用**是否波及非 users 域（通过 `JsonMapperBuilderCustomizer` 全局施加） | ~~`service/common`~~ ⇒ **落点原就写错**（类在 `users-common`，且**不是** `@Configuration`） | ✅ **已关闭（2026-10-27 · G1 lane）—— 问法订正 ⇒ 前提不成立 ⇒ 无漂移**（`GoStyleCoercion` **刻意不做 `@Configuration`** ⇒ 各 api 各自注册 customizer ⇒ **进程内作用域** ⇒ 「全局副作用波及外域」**机制上不存在**；未注册的 hot-api / task-api **不是漏**）⇒ 逐条证据见 [`common.md` 的 2026-10-27 订正块](common.md) | [`common.md`](common.md) |
| `DRIFT-USERRPC-V-007` | 超时 description 逐字一致（`context deadline exceeded` vs `rpc timeout after 2000ms`） | `service/common` | ✅ **已合回主干 `4f839f9`** ⇒ 双侧活体复跑一致 | [`user/api.md`](user/api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

### 1.1 §1 抽取快照的**逐条状态复核**（2026-10-27 · G1 lane）

| 编号 | 快照状态 | **复核结论（核对日期 2026-10-27）** | 复核依据 / 强度 |
|---|---|---|---|
| `UNRESOLVED-USERSCOMMON-003` | 🔴 未核（落点 `service/common`） | ✅ **已关闭（2026-10-27 · G1 lane）—— 问法订正 ⇒ 前提不成立 ⇒ 无漂移**（`GoStyleCoercion` **刻意不做 `@Configuration`** ⇒ 各 api 各自注册 ⇒ 进程内作用域 ⇒ 无跨域全局副作用；且 hot-api / task-api 未注册**不是漏**）⇒ **不再需要各外域打请求矩阵** | **L1 静态对照**（逐域清点注册方 ＝ 8 个 api 域 ＋ 读 `GoStyleCoercion` javadoc ＋ grep Go 全仓 `UnmarshalJSON` 仅 1 处）⇒ 见 [`users/common.md` 的 2026-10-27 订正块](common.md) |
| `DRIFT-USERAPI-013` | 🔴 待拍板 | 🔴 **维持待拍板**；只读复核已于 2026-10-26 完成（键语义钉死 · **可观察面在 `article-api` ⇒ user-api 本组件不可观察**）⇒ 落点确认在共享层 ⇒ **派 G4** | 域台账原文（L1） |
| `DRIFT-ARPC-010`（= E66） | 🔴 OPEN | 🔴 **维持 OPEN**（本轮**未复核落点**）⇒ 派单时仍须核 Go 侧 `文件:行号` | 本轮未查 |
| `UNRESOLVED-ARPC-002`（= E38②） | 🔴 未闭合 | ✅ **口径订正（2026-10-27）＝「① 已闭环 ② 未做（已另立项）」** ⇒ **本轮不需要落码**：① 纯 deadline 计算**已落地**（`RpcInterceptor.java:234` 的 `Math.min(serverBudgetMs, clientDeadlineRemainingMs())`）⇒ 剩下**第②层**（grpc-java `ServerCall#close()` **不取消业务线程** ⇒ 在途 SQL 不随客户端 deadline 取消 ⇒ 与 T-14 同根 ⇒ 架构级 ⇒ Jason 2026-10-26 拍板「**另立项**」）⇒ ⚠️ **「① 不销本条」的正解是「① 已做、② 在等立项」⇒ 不是「① 没做」** | **L3 双侧活体**（`tools/semantic-probe/rpc-timeout/` ⇒ EXIT=0 · `client_deadline_ctx` 一致 · 3/3 一致）+ L1 源码（`RpcInterceptor.java:234/263/592-601`） |
| `DRIFT-ADMINAPI-007` | 🟡 半修 | 🟡 **维持半修**（`nosniff` 已落地于 `HttpMiddleware.java:692` ⇒ 原条自动消失；**charset 分号后空格残差仍在** ⇒ **需重开一条**） | 域台账原文（L1） |
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
| **A1** | 超时 / deadline | ① `DRIFT-USERAPI-009②`（**3s 预算作用域**：Go 不覆盖 JWT 验签，RAG 覆盖验签+黑名单+handler）**✅ 已修（2026-10-27 · G1 lane · 域内自闭环）** ② `DRIFT-USERAPI-015`（Redis 主机名解析失败 ⇒ 200/503 摇摆）✅ **已关闭（2026-10-27 · G1 lane）** —— 关闭判据 = **复现失败**（dns 形态 24 次探测两侧都 200；blackhole 形态两侧都 503 ⇒ 同形）＋ **无可执行修法**（候选②实测制造**反向差异** ⇒ 否决）⇒ ⚠️ **关闭 ≠ 证明了等价**（保留重开触发条件）③ `DRIFT-USERRPC-V-012`（超时业务日志归因 ⇒ 落点在本域 `UserDomainService.logout`）**✅ 已修（2026-10-27 · G1 lane · 域内一行）** ④ `DRIFT-USERRPC-V-013`（DB 超时的源码层形态差异 ⇒ **非 wire 漂移，不得改回 Go 行为**）⑤ ✅ **`DRIFT-ADMINAPI-006` 的 ② 半已修**（**本轮 · 2026-10-27 · admin-api 推广 `DRIFT-USERAPI-009②`**：验签搬进 `admin-api` 的 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`）⇒ 移出 3s 预算 ⇒ M-37 由 OBSERVABLE DIFFERENCE 改判 EQUIVALENT ⇒ **L3 真 Tomcat 复验**；⚠️ **① 半（遥测面）仍未动**）⑥ ✅ **`DRIFT-USERAPI-016` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：`user-api` 缺 `spring.data.redis.connect-timeout` ⇒ Lettuce 落**自己的默认 10s**（🔴 **原写「回落到 3s」—— 机制说错**，Boot 4 在该属性为 null 时**整段跳过** ⇒ 不回落 timeout；见 `user/api.md` 订正块）≠ Go 的 `DialTimeout` 5s ⇒ 已补 `connect-timeout: 5s` ＋ 新增 `UserApiRedisTimeoutConfigTest` 2 例；⚠️ **与 ② 是同族但不是同一条** —— ② 是「DNS 解析失败的 200/503 摇摆（取舍题，建议不改）」，⑥ 是「超时**配置值**没抄对（照抄题）」）⑦ ✅ **`DRIFT-ADMINAPI-012` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：Lettuce 默认 `disconnectedBehavior=DEFAULT` ⇒ **Redis 断连时命令被排队**等重连 ⇒ 挂到 `commandTimeout: 3s` ⇒ **撞 HTTP 3s 预算 ⇒ 503**；Go 是 `MaxRetries=3` 的**快速失败** ⇒ **fail-open 200** ⇒ 落**域内副本** `config/RedisDisconnectedBehaviorConfig`（`REJECT_COMMANDS`）＋ 守门 `AdminApiRedisDisconnectedFastFailTest` 2 例；⚠️ **修前实测 3013ms ⇒ 修后 1ms**；🔴 **共享层收编仍归 G4** ⇒ 见 §6.4 表末行）⑧ ✅ **`DRIFT-USERRPC-V-014` / `DRIFT-ARPC-013` 已修**（**本轮 · 2026-10-27 · 新立并自闭环 · rpc 侧同机制不同形态**：`user-rpc` 缺 `connect-timeout` ⇒ 落 Lettuce 默认 **10s** ≠ Go 5s ⇒ 已补；**两个 rpc 组件都缺 `REJECT_COMMANDS`** ⇒ 断连时命令**排队**挂到 `commandTimeout: 3s` ≫ 本组件 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 **1015「服务繁忙」**，而 Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」` ⇒ 落**域内副本** `config/RedisDisconnectedBehaviorConfig`（user-rpc ＋ admin-rpc）＋ 守门 2 例各；⚠️ **证据强度 L1**（Go 腿是源码对照，**未**做双侧活体对拍 ⇒ 「客户端可见值」这一半未实测）） | 🔴 **1 条 OPEN**（④ 非漂移；② 已关闭）<br>⚠️ **2026-10-27 订正**：原状态写「4 条 OPEN（其中 **②③** 域内可闭环）」⇒ **编号错挂**（② 是 `USERAPI-015`、③ 是 `V-012`）⇒ 域内可闭环的是 **①③**；**② 已于本轮正式关闭**<br>🆕 **命令超时的「机制腿」已补**（**本轮 · 2026-10-27 · 探针实测**）：`TimeoutOptions.source` 恒为 `DefaultTimeoutSource`（返回 -1）⇒ 3s 只能由 **`applyConnectionTimeout`** 分支从连接级 timeout（`LettuceConnectionFactory.getTimeout()=3000ms` ⇒ `RedisURI.timeout`）取 ⇒ ⇒ **`isTimeoutCommands()` 恒真、不是有效判据** ⇒ 只断言「值」是**假绿口子**（机制被关 ⇒ 值仍读得到 3s、命令却永不过期）⇒ 5 份 FastFail 守门已各补**机制腿**（详见 [`user/rpc.md` §4.1.1](user/rpc.md)） | L1 源码 + **L2 运行时（新增）** + **Go 腿 L4 活体·直接判据（⑤）+ RAG 腿 L3 真 Tomcat·间接判据 ⇒ 合并按弱侧记 L3**（Go 腿探针见 §8.4；⚠️ 不笼统写 L4） + **L2 配置层守门（⑥）** + **L3 真容器活体（⑦）** + **L3 真容器活体（⑧）** + **L3 探针实测（命令超时机制腿）** + L3 双侧活体（台账原有） | 2026-10-27（**本轮订正 ⑤⑥⑦⑧ ＋ 关闭 ②**） |
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
| **1** | `DRIFT-USERAPI-013` ＋ `DRIFT-FOLLOW-008`（**同族，两条一起对账**） | ① **MaxConns 闸门粒度**：Go **每路由一个** latch（RAG = 全局一个）② **breaker 键空间**：Go key = 注册期 `route.Path` **模板**（基数 = 路由数）vs RAG = raw `getRequestURI()`（基数 = 参数取值数 ⇒ 无界）③ `GoStyleBreakerFilter` 的 javadoc 给「无界风险为零」的**错误安全保证** | ① `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12`（`func MaxConnsHandler(n int) func(http.Handler) http.Handler`）<br>② `go-zero@v1.10.2/rest/engine.go:149` → `go-zero@v1.10.2/rest/handler/breakerhandler.go:15,19`（`breakerSeparator="://"`）<br>③ `go-zero@v1.10.2/rest/config.go:52` | `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java`（全局单闸门）· `GoStyleBreakerFilter.java`（raw URI 建 key）· 11 个 `protection/` 类 | **L1 源码逐行对照**（域台账 2026-10-26 只读复核：键语义已钉死 · **可观察面在 `article-api`**，user-api 的 9 条路由零路径变量 ⇒ 本组件不可观察） | 两侧都应是 **Y** = 「**每路由一个 latch**」＋「**breaker key 用注册期 `route.Path` 模板**」＋「**404 取不到模板 ⇒ 不得用 raw URI 兜底**」（这是方案 B 的硬约束） | `service/common` 的 `protection/`（11 类）⇒ **编译面 16 个组件** | 🟡 **不阻塞**（但**与 #2 同族** ⇒ 若 #2 落码，#1 宜同批） |
| **2** | `DRIFT-ARPC-010`（= **E66**，两侧同编号 ⇒ **销账别当两条**） | Redis **写**不绑 deadline（admin `logout` 的 Redis 写）⇒ 超时后写仍在途 | `service/common/observability/rpc.go:38`（`context.WithTimeout(ctx, timeout)` ⇒ ctx 带 deadline ⇒ **出向调用天然继承**） | `service/common` 的 Redis 调用路径 **未绑 `GoStyleRequestDeadline`** ⇒ 只算服务端自己的预算 | **L1 源码**（域台账原文）+ 与 `UNRESOLVED-ARPC-002` 同型 | 两侧都应是 **Y** = 「**Redis 写出向调用绑定请求 deadline**」⇒ 与 gRPC 出向（E65）**同一套绑定机制** | `service/common` ⇒ 编译面 16 个组件 | 🟡 不阻塞 |
| **3** | `UNRESOLVED-ARPC-002`（= **E38②**） ⚠️ **2026-10-27 订正：此条本轮不需要落码** | 超时是否 **min(客户端 deadline, 服务端 timeout)**：Go 继承父 ctx ⇒ 客户端更早就提前取消。**RAG 侧已于 2026-10-26 落地该语义** ⇒ **① 已闭环** | `service/common/observability/rpc.go:38` ⇒ `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)`<br>**已核 ✅**：该行内容逐字 = `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` | ✅ **已落地**：`RpcInterceptor.java:234` ⇒ `Math.min(serverBudgetMs, clientDeadlineRemainingMs())`；`:263` 调度器用 `effectiveTimeoutMs`；`:592-601` 从 `io.grpc.Context.current().getDeadline()` 取 | **L3 双侧活体**（`tools/semantic-probe/rpc-timeout/` **本轮实跑 EXIT=0**：`client_deadline_ctx` **一致** · `timeout_wire` 一致 · 3/3 一致） | 两侧都应是 **Y** = 「**有效期限 = min(客户端 deadline, 服务端 timeout)**」⇒ ✅ **这个 Y 已经达成** ⇒ ⛔ **请勿改 `RpcInterceptor` 的 deadline 计算**（会把对的改错 ⇒ 制造新漂移）<br>剩下**第②层**（`ServerCall#close()` 不取消业务线程 ⇒ 架构级 ⇒ Jason 2026-10-26 已拍板「**另立项**」）⇒ **不是本轮落码项** | `service/common` ⇒ 编译面 16 个组件 | 🟢 **不阻塞**（**本轮不落码**） |
| **4** | `DRIFT-ADMINAPI-007` | 503 超时响应的 `Content-Type` **charset 分号后空格**残差（`nosniff` 已于 `HttpMiddleware.java:692` 落地 ⇒ **原条自动消失** ⇒ **需重开一条**） | `service/common/observability/http.go:88` ⇒ `http.Error(w, httpTimeoutBody, statusCode)`（Go 标准库 `net/http.Error` 的 5 件事：`text/plain; charset=utf-8` ＋ `nosniff` ＋ 状态码 ＋ 尾换行） | `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java`（超时分支）＋ `http/GoStyleRouterErrorResponses.java` | **L1 源码**（⚠️ 响应头层须**真 Tomcat** 验 ⇒ MockMvc 测不到，见 `Pitfalls/common/http.md`） | 两侧都应是 **Y** = 「**503 的 `Content-Type` = `text/plain; charset=utf-8`（分号后**无**空格）**」⇒ 与 `DRIFT-MSGAPI-010` 同族（那条用户已拍板「保持登记，本轮不做」⇒ **本条要不要做请 G4 明确**） | `service/common` ⇒ 编译面 16 个组件 | 🟡 不阻塞 |
| **5** | `UNRESOLVED-USERSCOMMON-003`（**只派一半**） | `users-common` 的**通用版** `GoStyleCoercion::applyTo` 被 **user-api / admin-api / favorite-api** 三域注册（**favorite 是外域**）⇒ 规则一改，favorite 的解析语义跟着变 ⇒ 「通用版规则内容是否该被外域复用」待定 | go-zero `core/mapping/unmarshaler.go`（`UnmarshalJsonReader` 的 coercion 与必填校验规则）—— ⚠️ **本条 Go 侧行号本轮未核** ⇒ 落码前请回真源逐条核 | `service/users/users-common/src/main/java/com/rag/users/common/http/GoStyleCoercion.java`（**通用版**，G4 写权）· 另 5 域用**本域私有**变体 | **L1 源码**（规则内容）＋ **L3 活体**（本轮实测 8 个 api 域的 `WebMvcConfig` 注册了哪个变体 ⇒ **落点口径已订正**，见 §6.4.1） | 两侧都应是 **Y** = 「**通用版的规则集合 = Go 侧 `UnmarshalJsonReader` 的等价规则**」⇒ 是否允许外域复用，由**规则内容**决定，不由「通用版」这个文件名决定 | `service/users/users-common` ⇒ **被 6 个外域 api 引用** ⇒ 编译面大 | 🟡 不阻塞 |
| **6** | `DRIFT-USERRPC-V-002` | DDL：`users_user.status` 的 **NOT NULL** 两侧不同（Go `status bigint DEFAULT 0` **可空** / RAG `status BIGINT NOT NULL DEFAULT 0`）⇒ RAG 侧任何写 NULL 的代码在 PG 层被拒 | Go `service/user/user/rpc/internal/model/data_model.go:11` ⇒ `Status int64 \`gorm:"column:status;default:0"\``（**值类型 · 非指针**）⇒ ✅ **2026-10-27 13:2x 已核到该行**（原「本条 Go 侧行号本轮未核」的标记**撤销**）⇒ ⇒ **更强的推论**：GORM 对**值类型**字段写库时**恒写 0**（不会写 NULL）⇒ ⇒ **Go 侧这一列实际上永远不出现 NULL** | `service/users/users-common/src/main/resources/db/01_create_user.sql:39` ⇒ `status BIGINT NOT NULL DEFAULT 0` | **L2 真库实证**（`information_schema` 对照）<br>＋ **L1 逐层复核**（2026-10-27 · GORM `field.go:616-622` ＋ `database/sql/convert.go:431-435`） | ⛔ **2026-10-27 订正：原建议修法已被本域逐层证据链推翻 ⇒ 本单不需要 G4 落码。**<br>**原写**「两侧都应是 Y =「`users_user.status` **可空**」」⇒ **错的** ⇒ **放开可空会制造新漂移**（RAG 侧 `callSettersOnNulls=false` ⇒ NULL 时跳过 setter ⇒ 读出 **null** ≠ Go 的 **0** ⇒ 方向相反）。<br>⇒ **正确口径 = 两侧都应是 Y =「维持 RAG NOT NULL」**（Go 读 NULL ⇒ **0** 且不报错；RAG 读不到 NULL ⇒ 也是 0 ⇒ **可观察结果相同**）⇒ 本条定性为「**DDL 层差异 · 行为层零差异**」⇒ 落点 `users-common` **无动作**。<br>⇒ 剩下只差「**销号签字**」，而 `docs/Intentional_Deviations/**` **不在 G1 写权面** ⇒ **请 G4 / 整合者确认销号口径**；逐层证据链见 [`user/rpc.md` 的 V-002 订正块](user/rpc.md)<br>🔴 **2026-10-27 13:2x · G4 回执 ⑧ 说「等 G1 的 #6 第 1 步（让 `admin-rpc` 的 `UserEntity.status` 能表达 NULL）」⇒ ⇒ 本条已由本域订正 ⇒ ⇒ 「第 1 步」【取消】⇒ ⇒ **G4 不需要为本单落 DDL 那步**（落 DDL = 把 RAG 改成可空 = 制造上面那条新漂移）⇒ ⇒ **落点只剩签字档** | `service/users/users-common` ⇒ **6 个外域 api 引用**（但 **本单落点无动作 ⇒ 实际影响面 = 0**） | 🟡 不阻塞（**原写「须与 users 域同一次提交」已作废** ⇒ 无需任何组件改动） |
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
| ⚠️ **诚实边界** | ① **两次独立运行**（Go 腿与 RAG 腿不在同一次编排里）⇒ 比的是**属性**（验签是否计入 3s 预算），**不是逐字节 diff**；② **探针落在 `/tmp`（易失）** ⇒ **落仓 `tools/difftest/` 需整合者拍板**（`tools/` 不在 G1 白名单）⇒ **已登记待办**；⚠️ **但源码已在本节 §8.4.1 抄了一份保全副本**（域台账在 G1 写权内 ⇒ 不会被 `/tmp` 清空带走）③ 探针的 `Timeout` 走 go-zero struct tag 默认值 3000ms（真源 api 的 yaml 同样不写 `Timeout`）⇒ 口径与真源一致 |

#### 8.4.1 📦 **探针源码保全副本**（2026-10-27 · 防 `/tmp` 一清就没了）

> ⚠️ **为什么抄在这里**：`tools/difftest/` 是**无人拥有 ⇒ 整合者**（G1 不写）⇒ 在整合者落仓前，
> 探针**只活在 `/tmp`** ⇒ **易失**（机器重启即没）⇒ **工件有丢失风险**。
> ⇒ 本节把**探针源码原文**抄进**域台账**（`docs/DRIFT/users/**` = **G1 写权内**）⇒ **保住这份工件**，等整合者落仓时可直接取用。
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
| ~~`UNRESOLVED-USERSCOMMON-003`~~ | ~~`GoStyleCoercion` 规则是否波及非 users 域~~ | — | ✅ **已关闭（2026-10-27）** ⇒ **从派单表撤下** | ✅ **问法订正 ⇒ 前提不成立 ⇒ 无漂移**（`GoStyleCoercion` **刻意不做 `@Configuration`** ⇒ 各 api 各自注册 customizer ⇒ **进程内作用域** ⇒ 无跨域全局副作用；hot-api / task-api 未注册**不是漏**）⇒ 逐条证据见 [`users/common.md` 的 2026-10-27 订正块](common.md)<br>⚠️ **原派单拆的两半**：①「哪个域注册哪个变体」= 域侧自修 ⇒ **已结清**（8 个 api 域注册方已清点）· ②「通用版规则内容」= G4 ⇒ **仍开放**，但它**不是本条**（那是规则内容本身，见上方 §5 表第 5 行） |
| `DRIFT-ADMINAPI-007` | 503 响应 `Content-Type` charset 分号后空格残差 | `service/common` | 🔴 **G4** | 🟡 半修（`nosniff` 已落地 ⇒ 原条消失）⇒ **需重开一条** |
| 🆕 **`DRIFT-USERAPI-009 §7 补充`** | **MaxBytes 相对 observability(3s) 的位置**：Go 在预算**之外**，RAG 在**之内** ⇒ 超限 413 **计入** access log/span/指标（Go 不计） | `service/common`（`GoStyleMaxBytesFilter` 的 `@Order`） | 🔴 **G4**（代码）· 🟡 **整合者**（签字档） | 🔴 **未签字**（台账建议**并入 `SD-USERAPI-002` 的范围说明** ⇒ 签字比改码便宜）⇒ ⚠️ **见 §6.4.2 的槽位联动警告** |
| 🆕 **Go 腿 JWT 预算探针落仓** | §8.4 的探针（`/tmp/goprobe/probejwt/main.go` · G2 建、本域独立复跑）**只在 `/tmp`** ⇒ 易失 ⇒ 重跑要重新搭一遍 | `tools/difftest/`（**新脚本**） | 🔴 **无人拥有 ⇒ 整合者**（`tools/difftest/README.md` 在共享面清单里；**新脚本目录级不属任何 lane** ⇒ 见 `check-wave-lane-ownership.py` 的 `SHARED` 列表） | 🔴 **待整合者拍板**（**G1 不写 `tools/`**）⇒ 建议内容：`probe-jwt-budget/`（Go 工程副本的取用方式 ＋ 三腿期望输出 ＋ 清理口径）⇒ ⚠️ **落仓前必须解决「真源整仓复制到 `/tmp`」的体积问题**（本轮探针靠复制整仓才能 import 真源自己的 `observability`） |
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
⇒ ⇒ ✅ **「`MaxBytes`(+8) 是唯一排在 +7 之后的共享层 Filter」两侧都成立**（+2 在前，其余逐条同值）⇒ ⇒ 这正是「RAG 多记 413」的机制体现 ⇒ ⇒ **要把它挪到 +7 之前，必须同一次提交改：共享层 `@Order` ＋ G4 的槽位快照守门 ＋ 台账登记理由**（否则 G4 的 `SharedFilterOrderSnapshotGateTest` 必红）⇒ ⇒ **签字比改码便宜 ⇒ 优先走签字**（落点 = 签字档 ⇒ **整合者写权面**）。

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
> ⇒ ⇒ **「待整合者认领」这条待办【关闭】** ⇒ ⇒ §20.2 合并规则里
> 「合并后目标 = 7 份文件命中」**已过期** ⇒ 实际目标 = **只剩 6 处故意（4 份文件）**。

### 20.2 ⇒ ⇒ **合并规则（写死，别再逐次口头对）**

1. **13 份真损坏分属 4 个 lane**（**G4 2** · **G2 3** · **G3 5** · **G1 3** ⇒ 2+3+5+3 = **13**）⇒ ⇒ **合并时必须逐份取「已修的那个 lane 的树」** ⇒ ⇒ **若按主干版本取 ⇒ 13 份坏字符全部被带回去**。
   ⚠️ **份数是加出来的，别拍脑袋**：**13 真损坏 ＋ 4 份故意 ＋ 3 份无人拥有 = 20 份** ⇒ 与全仓扫描的 20 份**闭合**（加不出来就说明漏了或多数了 ⇒ 这是这张表的**自校验**）。
   ⇒ **处数同样闭合**：13 份真损坏 **34 处**（G4 4 + G2 9 + G3 13 + G1 8）＋ 故意 **6 处** ＋ 无人拥有 **11 处** = **51 处** ✅ 与全仓扫描的 51 处一致。
2. **6 处是「故意」**（#14~#17，共 **4 份文件**）⇒ ⇒ **一个字都不动** ⇒ ⇒ **「全仓 U+FFFD 归零」不是合法目标**。
3. **3 份是「无人拥有」**（#18~#20）⇒ ⇒ **不在任何 lane 的写权面** ⇒ ⇒ **我域不代改** ⇒ ⇒ 已登记，请整合者认领。
4. ⚠️ **G2 的清单只覆盖它自己的 3 份** ⇒ ⇒ **我域的 3 份（#11~#13）此前不在任何人的清单里** ⇒ ⇒ 本轮**主动补进**（否则合并时按主干取 ⇒ 我域那 3 处坏字符回流）。
5. ⇒ **合并后必须再跑一次字节级扫描**（不是 `grep` —— 见 §19 的判据纪律）⇒ ⇒ **目标 = 只剩 6 处故意（4 份文件）＋ 3 份待认领 = 7 份文件命中**。

### 20.3 ✅ **四棵树的当前命中数（字节级全仓扫描，2026-10-27 13:2x）**

| 树 | 命中份数 | 说明 |
|---|---|---|
| **主干 `do-my-own-gh`** | **20** | **一份都没修**（整合者还没合） |
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
| 🔴 **无人拥有（整合者写权面）** | **3 处** | `docs/DRIFT/_cross/open-items.md:80` · `docs/FileTree/like/rpc.md:20` · `tools/difftest/run.sh:184`（注释）⇒ ⇒ **我改不了** |
| ✅ **不受影响** | 2 个脚本 | `tools/difftest/check-grpc-runtime-classpath.py:140` · `check-tomcat-limits.py:174` ⇒ 两者都是 `for fn in ("application.yml", "application.yaml")` ⇒ **两个扩展名都认** ⇒ 改名不影响它们 |

### 21.3 ⇒ ⇒ **本轮决定：不改名，改为在台账登记**（三条独立理由，任一条都足够）

1. **成本 ≫ 收益**：收益 = 消除**一个**命名误判点；成本 = **≈ 42 处**引用同步（其中 **3 处我改不了**）⇒ ⇒ **跨 lane 协调成本**（要请整合者同步 3 处 + 我自己改 38 行）⇒ ⇒ **与 G2 的「顺手最省事」估计差两个数量级**。
2. 🔴 **会留下我无法同步的不一致**：`docs/Pitfalls/users/_shared.md` 的 **2 处**引用 **不在任何 lane 白名单**（写权守门实测：G1 白名单 = `service/users/**`（除 `users-common`）＋ `service/follow/**` ＋ `docs/DRIFT/{users,follow}/**`）⇒ ⇒ **改名后这 2 处会变成过期引用，而我没有写权去改** ⇒ ⇒ **等于用「消除误判」换「引入新的不一致」** ⇒ ⇒ 不可接受。
3. ⚠️ **改名对 Go 对齐零收益**：AGENTS.md §三规定「差异只允许出现在**表达层**」⇒ ⇒ 文件扩展名是**纯表达层** ⇒ ⇒ 改它**不改善任何一条语义漂移** ⇒ ⇒ 而本项目对「行为层零变化」的改动一律要求配回归 ⇒ ⇒ **窗口期内还不能跑回归** ⇒ ⇒ 现在改 = **既无收益、又欠一次未验证的回归**。

### 21.4 🔓 **复开触发条件（写死，将来满足其一就统一）**

- ① 将来某个工具/脚本**按扩展名做单文件假设**（只找 `application.yml`、不认 `application.yaml`）⇒ ⇒ **那时才必须统一**（当前两个脚本都两个都认 ✅）。
- ② 后人**再犯一次**「同域两种扩展名 ⇒ 会不会互相覆盖」的误判（G2 本轮就差点误判 ⇒ **说明这个误判是真实存在的**）⇒ ⇒ **那时一次性改完 42 处**。
- ③ 若 §21.2 里那 **3 处无人拥有的引用**被整合者先改掉 ⇒ ⇒ 成本降到 38 处 ⇒ ⇒ **那时再评估**。

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

> ⚠️ **本条建议归档到 `docs/Pitfalls/`** —— 但 `docs/Pitfalls/**` **不属任何 lane 的写权面**（`check-wave-lane-ownership.py` 的白名单里只有 `docs/DRIFT/<域>/**`）⇒ ⇒ **请整合者归档**；此处先留在本域台账。

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
⇒ ⇒ **只删 3 份 ⇒ `user-rpc` / `admin-rpc` 仍被遮蔽 ⇒ 共享层对这两个域仍静默不生效** ⇒ **已回信要求 G4 把「3 份」改成「5 份」再转整合者**。

### 16.2 🔴 **删除的前置判定：本轮【不能】执行**（不是保守，是前置未满足）

> 🟢 **2026-10-27 订正**：本节的**结论**（当轮不能删）当时正确，但**执行方式**后来变了 ——
> ⇒ **不需要等共享层先进主干**，只需**把 G4 分支合进本树** ⇒ ⇒ 收编**已完成**（见 §26）。
> ⇒ ⇒ **本节以下的「不能执行」是历史快照，不是现状。**

⇒ 实测：`git merge-base --is-ancestor 5564c27 do-my-own-gh` ⇒ **NO**（tip `cec7131` 也未进）⇒ ⇒ **本树里没有 `GoStyleRedisClientAutoConfiguration`**。
⇒ ⇒ **现在删 = 不是「双份变一份」，而是「零份」** ⇒ `REJECT_COMMANDS` 整个消失 ⇒ 退回本轮修掉的漂移：
- **api 侧**：断连排队 3s ⇒ 撞 HTTP 3s ⇒ **503**（Go 是 fail-open **200**）；
- **rpc 侧**：断连排队 3s ≫ `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ **客户端 1015**（Go 是快速失败 ⇒ `INTERNAL`）。

⇒ ⇒ **我域 5 份 FastFail 守门会立刻变红**（它们断言的正是「立即失败」那一半）⇒ ⇒ **删除 + 守门重写必须同一次提交**。
⇒ ⇒ **正确顺序**：**整合者把 G4 分支合进 `do-my-own-gh` ⇒ 我把主干并回我分支 ⇒ 一次性执行**（配方见 §12.2）。

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
| `docs/DRIFT/users/user/rpc.md:2459` / `:2461` | ✅ **故意**（台账对历史损坏的**引述** ＋ 明写「不动」那行）⇒ **不动** |

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
- ✅ **「`MaxBytes`(+8) 是唯一排在 +7 之后的共享层 Filter」两侧都成立** ⇒ ⇒ **要挪它，必须同一次提交改：共享层 `@Order` ＋ G4 的槽位快照守门快照 ＋ 台账登记理由** ⇒ ⇒ **签字比改码便宜 ⇒ 优先走签字**（落点 = 签字档 ⇒ **整合者写权面**）。

### 19.3 🔴 **订正我上一轮的一条推断：「G4 正在跑第十二次全量」⇒ 不成立**

- G4 回执 ⑥ 的实测 = **本轮未跑全量**（理由：零生产代码改动 ＋ 第八次全量刚跑过且全绿）⇒ ⇒ **它跑的是模块级 `mvn -o -pl service/common test`（315 条）**。
- ⇒ ⇒ **我错在把「代理指标」当「被测属性」**：`ps` 只能证明「**这个树在跑 maven**」，**不能**证明「**在跑全量**」⇒ ⇒ **模块级与全量级在 `ps` 里长得一样** ⇒ ⇒ 判据必须能区分「跑什么」（全量有 `clean verify` / `-pl` 列表长度 / `Reactor Summary` 行数可查）。
- ⇒ ⇒ **「§4.10.26 尚未落」这个事实仍成立**（G4 确实还没跑第十二次全量）⇒ ⇒ **我域台账从此只写「截至 <时间戳> 尚未发生」，不写「正在跑」这种动态断言**。
- ⇒ ⇒ ✅ **G4 本轮的落码是「零生产代码改动 + 1 个新测试文件」**（`SharedFilterOrderSnapshotGateTest` 4 条 ＋ 3 组变异全验证）⇒ ⇒ **共享层 Filter 顺序没被动 ⇒ 我域的槽位约束没被打破** ⇒ ⇒ **我域零改动**。

### 19.4 🔴 **V-002：我域「#6 第 1 步」【取消】⇒ G4 不需要为本单落 DDL 那步**

- G4 回执 ⑧：「等你的 **#6 第 1 步**（`admin-rpc` 的 `UserEntity.status` 能表达 NULL）⇒ 之后我落 DDL 那步 ＋ 跑全量 ＋ 回执」。
- ⇒ ⇒ **这一步基于的修法已被我域的逐层证据链推翻**（见派单表 #6 行）⇒ ⇒ **正确处置 = 维持 RAG NOT NULL** ⇒ ⇒ **落 DDL 那步会把 RAG 改成可空 = 制造新漂移**（RAG `callSettersOnNulls=false` ⇒ NULL 时跳过 setter ⇒ 读出 `null` ≠ Go 的 `0`）⇒ ⇒ **本条落点 = 无动作**。
- ⇒ ⇒ **本轮又核到一条更强的论据**（原台账标着「Go 侧行号本轮未核」⇒ **已核**）：Go `service/user/user/rpc/internal/model/data_model.go:11` ⇒ `Status int64 \`gorm:"column:status;default:0"\`` ⇒ **值类型 · 非指针** ⇒ ⇒ **GORM 对值类型字段恒写 0 ⇒ Go 侧这一列实际上永远不出现 NULL** ⇒ ⇒ **DDL 声明差异、行为零差异**这条结论**更硬了**。
- ⇒ ⇒ **剩下的唯一落点 = 销号签字**（`docs/Intentional_Deviations/**` ⇒ **不在 G1 写权面**，也不在 G4 写权面）⇒ ⇒ **需要整合者拍板** ⇒ ⇒ **G4 不必为这条再做任何事**。
- ⚠️ **机制教训（跨 lane 协作的通用坑）**：**发出去的消息不会自己更新** ⇒ ⇒ 我改了自己的结论（V-002 订正）⇒ ⇒ **对方树上的台账是旧的**（实测：G4 树里 `docs/DRIFT/users/vs-common.md` **连「V-002」都零命中** ⇒ 它那份是整合者 sweep 那版 ⇒ **完全不含 G1 lane 的任何内容**）⇒ ⇒ **对方很可能还在按我更早那条消息里的旧单等我** ⇒ ⇒ **改了结论必须主动通知，不能指望对方去读文件**。


## 26. 🆕 **共享层 Redis 收编【已落码】（2026-10-27 · G1 lane）** —— 前置怎么被满足 ＋ 一条**方法论订正**

### 26.1 🔴 **前置不是「等主干」，是「把 G4 分支合进本树」** —— 实测零冲突

- 我域原判（§16.2 / §18.6）：**「删除的前置 = 共享层那份先进主干 + 并回本树」⇒ 本轮不能执行**。
- ⇒ **本轮实测把这条判据的【执行方式】推翻了**（**结论不变，前提变了**）：
  - `git merge --no-ff --no-commit align/g4-service-common`（tip `dcd0706`，51 笔不在主干）
  - **merge-base `a282012`** · **冲突面实测 0**（`git diff --name-only --diff-filter=U` 空）
  - **合并态下 5/5 门禁全 EXIT=0**（lane-ownership / role-model / coupling / doc-links / ledger-count）
  - ⇒ **合并后 `AutoConfiguration.imports` = 6 条**（合并前 **2 条**）⇒ 共享层那份到位 ⇒ **收编删除的前置在本树内即可满足**
- ⇒ ⇒ **落点从「等整合者合主干」变成「本 lane 自行合并 G4 分支」** ⇒ 与 G2（`37816ca`）/ G3（`cd893d1`）**同一步骤** ⇒ ⇒ **三棵树现在都有共享层**。
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
  - `docs/DRIFT/users/user/api.md:3452/:3453/:3457/:3458`（7 处 · 引述 + 记录「末字已不可恢复」）
  - `docs/DRIFT/users/user/rpc.md:2459/:2461`（4 处 · 同上）
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
- 🔴 **落点不在本域写权面**：`docs/DRIFT/_cross/cross-domain-coupling.md` ∈ `check-wave-lane-ownership.py` 的 **`SHARED` 无人拥有清单**（⇒ 归整合者）⇒ ⇒ **G1 不能写**，且 **G4 也不能写**（同一份清单对所有 lane 一视同仁）⇒ ⇒ **只能由整合者更新**。
- ⇒ ⇒ **守门自己给出的处置**也是「先更新文档快照（`--print`）再谈分波」⇒ ⇒ **新的期望值已实测出来**（`common_radiation` 加 `'GoStyleRedisClientAutoConfiguration': ['users']` 一项，其余不动）。
- ⚠️ **为什么必须转派、不能拖**：**主干上的 `_cross` 快照仍是收编前的版本** ⇒ ⇒ 各 lane 合并回主干后，**主干上的守门仍然红** ⇒ ⇒ **必须在合并前由整合者更新**，否则「合并后全绿」这一步会被这个共享面卡住。
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
- ⇒ ⇒ **剩下的唯一落点 = 销号签字**（`docs/Intentional_Deviations/**` ⇒ **不在 G1 也不在 G4 写权面** ⇒ 需整合者拍板）。
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
| `service/users/user/user-rpc/src/main/java/com/rag/users/user/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` | **删** |
| `service/users/user/user-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **删**（只含那一行） |
| `service/users/admin/admin-rpc/src/main/java/com/rag/users/admin/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` | **删** |
| `service/users/admin/admin-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **删**（只含那一行） |
| `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` | **删** |
| `service/follow/follow-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | **删**（只含那一行） |
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
    ⇒ 落点 `docs/DRIFT/_cross/**` = **无人拥有面** ⇒ 仍**等整合者**更新快照（与 §26.10 同一条，不新增待办）。

### 28.7 🔴 **写权外 ⇒ 登记并转派（`docs/FileTree/**`）**

| 位置 | 引用形态 | 动作 |
|---|---|---|
| `docs/FileTree/users/user/rpc.md:19` / `:209` / `:258` / `:369` / `:385` | 目录树 ＋ 逐文件说明 ＋ 「逐文件对照索引」里的**独立一行** ＋ 「新增文件有职责依据」列举 | 🔴 **写权外**（`docs/FileTree/**` 归整合者）⇒ **不写**，登记转派 |
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
⇒ ⇒ **「待整合者认领」这条待办【关闭】** ⇒ ⇒ §20.2 合并规则的「目标 = 7 份文件命中」**已过期**
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
| **Redis 连通 ＋ PING** | `nc -z 127.0.0.1 61379` ⇒ **OK**；`(printf 'PING\r\n') | nc` ⇒ **`+PONG`** | ✅ **能用** |
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
**仍然存在**（实测：`git ls-tree align/g4-service-common` 命中；该文件第 4 行
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
⇒ ⇒ **落点 = `global` 收口域**（`docs/DRIFT/global/cross-domain.md` = **无人拥有面**，由整合者写）
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

### 34.5 ⚠️ **纪律（本条是本轮真正要留下的东西）**

1. **一次失败不判回归** ⇒ ⇒ 必须「① 单独复跑 ＋ ② 代码变更面 ＋ ③ 环境普查」**三条齐**才定性；
2. **「重跑绿了」≠「已修」** ⇒ ⇒ 这一条**没有代码可修** ⇒ ⇒ 只能记成**环境瞬时**（与 §32.4 同一纪律）；
3. 🔴 **「PG 连接失败」先问「那个端口上回应我的是不是 PG」** ⇒ ⇒ **`EOFException` 出现在 `enableSSL` ⇒ 强烈指向「对端不是预期的服务」**，
   **不要**直接读成「PG 挂了」⇒ ⇒ 否则会去查一个根本没坏的东西；
4. 🔴 **不动别人的容器** ⇒ ⇒ 25 个残留里混着别的 lane 正在用的 ⇒ ⇒ **本轮不删**（不碰别人的进程与端口）⇒ ⇒ 登记 ＋ 通报。

### 34.6 落点（跨 lane ⇒ 无人拥有面）

「Testcontainers 残留容器 ＋ 随机宿主口落在 OS 临时段」是**跨 lane 共享环境**问题 ⇒ ⇒
落点 = **整合者**（`docs/DRIFT/_cross/**` = 无人拥有面）⇒ ⇒ **我域零落码**。
⇒ ⇒ **可选修法（仅登记，不实施）**：① 固定 Testcontainers 宿主口段（避开 `49152–65535`）② 跑完显式清理
③ 统一「容器名 ＋ 标签」回收（本机 Ryuk 不可用 ⇒ 需自建）。
