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
| **1** | `DRIFT-USERAPI-013` ＋ `DRIFT-FOLLOW-008`（**同族，两条一起对账**） | ① **MaxConns 闸门粒度**：Go **每路由一个** latch（RAG = 全局一个）② **breaker 键空间**：Go key = 注册期 `route.Path` **模板**（基数 = 路由数）vs RAG = raw `getRequestURI()`（基数 = 参数取值数 ⇒ 无界）③ `GoStyleBreakerFilter` 的 javadoc 给「无界风险为零」的**错误安全保证** | ① `rest/handler/maxconnshandler.go:12`（`func MaxConnsHandler(n int) func(http.Handler) http.Handler`）<br>② `rest/engine.go:149` → `rest/handler/breakerhandler.go:15,19`（`breakerSeparator="://"`）<br>③ `rest/config.go:52` | `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java`（全局单闸门）· `GoStyleBreakerFilter.java`（raw URI 建 key）· 11 个 `protection/` 类 | **L1 源码逐行对照**（域台账 2026-10-26 只读复核：键语义已钉死 · **可观察面在 `article-api`**，user-api 的 9 条路由零路径变量 ⇒ 本组件不可观察） | 两侧都应是 **Y** = 「**每路由一个 latch**」＋「**breaker key 用注册期 `route.Path` 模板**」＋「**404 取不到模板 ⇒ 不得用 raw URI 兜底**」（这是方案 B 的硬约束） | `service/common` 的 `protection/`（11 类）⇒ **编译面 16 个组件** | 🟡 **不阻塞**（但**与 #2 同族** ⇒ 若 #2 落码，#1 宜同批） |
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
//     ⇒ 而该中间件在链上位于 Authorize **之后**（rest/engine.go:108 先 appendAuthHandler，
//       :110-112 才 append ng.middlewares；rest/chain/chain.go:38-42 Append = 「追加到请求流最后」）
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
#    （rest/config.go:55 `Timeout int64 json:",default=3000"`）
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

⇒ ⇒ **我域三份 api yaml 全部 0 个 `Timeout` 键** ⇒ ⇒ **零 `Timeout` 键 ≠ 无超时，而是走 struct tag 默认值**（默认值有行号可指 ⇒ 不是推测）⇒ ⇒ **我域 http = 3s、rpc = 2s**（`zrpc/config.go:31` `default=2000`）。

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

