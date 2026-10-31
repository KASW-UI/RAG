<!-- @new 2026-10-27 · `follow-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G1 波次工作树（`align/g1-users-follow`）** -->

# `follow-common` lane 台账 —— 「follow ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/follow/follow-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G1` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `follow-common`（**原第 4/10 条**） |
| **现分支 / 工作树** | **`align/g1-users-follow`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g1-users-follow`<br>⚠️ 原 `align/follow-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/follow/{follow-api, follow-rpc, follow-common, follow-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/follow/**` ＋ `docs/DRIFT/follow/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| ✅ `DRIFT-FOLLOW-008` | **MaxConns 闸门**：Go **每路由一个**（`go-zero@v1.10.2/rest/handler/maxconnshandler.go:12` 按 `route.Path` 建闸）vs RAG **全局一个** | `service/common`（`GoStyleMaxConnsFilter`） | ✅ **已签字偏离 ⇒ 不修**（`SD-2026-10-25-01`，2026-10-25 用户拍板）· ⚠️ **2026-10-29 订正**：原记「🔴 未修 ⇒ 派单」**是陈旧的** ⇒ **不是缺陷、撤出派单表** | [`api.md`](api.md) · 签字档 [`Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md) |
| `DRIFT-FOLLOW-002` | 运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败 | `service/common`（pom 作用域） | ✅ **已修**<br>⚠️ **2026-10-30 复核（follow+task lane · 补指针）**：RAG `service/follow/follow-rpc/pom.xml:256-260` = `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`**（compile 默认）⇒ **现行态 = ✅ 已修（不变）**；核对日期 2026-10-30 | [`rpc.md`](rpc.md) |
| `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（CD-6） | ✅ **已落地**<br>⚠️ **2026-10-30 真实性复核（follow+task lane）**：短确认 —— 共享层 **6 个类仍在位**（`service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:170` = `public static final int MAX_PARAMETER_COUNT = 10001;` ＋ `GoStyleFormLimitMode.java` · `GoStyleFormLimitWebMvcConfig.java` · `GoStyleFormValueLimitInterceptor.java` · `GoStyleFormValues.java` · `GoStyleFormLimitExceededException.java`）· 本域消费点 RAG `service/follow/follow-api/src/main/resources/application.yml:219` = `mode: envelope`（Go `service/follow/api/followcenter.go:53` 注册了 `httpx.SetErrorHandler` ⇒ **两侧同向**）⇒ **现行态 = ✅ 已落地（不变）**；⚠️ **「后续格（段数轴）」仍 🔴 在 `global`/G4** ⇒ **本行只确认主轴**。核对日期 2026-10-30。 | [`api.md`](api.md) <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0**（2026-10-30 03:48:23 · 8s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ client_deadline_ctx                      一致 ⏐ ⚠️  timeout_telemetry                        预期差异（不计入失败） ⏐ 理由：RAG 的 code 段用 grpc-java 的 Code.name()（DEADLINE_EXCEEDED），Go 用 grpc-go 的 codes.Code.String()（DeadlineExceeded）⇒。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。<br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/api-full-parity-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 03:20:49 · 48s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ follow_self_relation  Go == RAG == expected.txt（HTTP 200 · 124 字节） ⏐ ✅ follow_target_not_found  Go == RAG == expected.txt（HTTP 200 · 96 字节） ⏐ ✅ follow_new_relation  Go == RAG == expected.txt（HTTP 200 · 94 字节） ⏐ ✅ unfol；<br>`tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` ⇒ **EXIT=0**（2026-10-30 03:48:23 · 8s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ client_deadline_ctx                      一致 ⏐ ⚠️  timeout_telemetry                        预期差异（不计入失败） ⏐ 理由：RAG 的 code 段用 grpc-java 的 Code.name()（DEADLINE_EXCEEDED），Go 用 grpc-go 的 codes.Code.String()（DeadlineExceeded）⇒；<br>`tools/semantic-probe/run-parity.sh` ⇒ **EXIT=0**（2026-10-30 03:16:43 · 4s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

### 1.1 §1 抽取快照的**逐条状态复核**（2026-10-27 · G1 lane）

| 编号 | 快照状态 | **复核结论（核对日期 2026-10-27）** | 复核依据 / 强度 |
|---|---|---|---|
| `DRIFT-FOLLOW-008` | 🔴 未修（落点 `service/common`） | ✅ **2026-10-29 复核订正：快照状态本身是陈旧的** ⇒ 该差异已于 **2026-10-25 用户签字**为**有意偏离** ⇒ [`SD-2026-10-25-01`](../../Intentional_Deviations/global/common-tails.md)（签字决定 = **保留 RAG 全局闸门，不改成 per-route**；签字档「对应的 DRIFT 条目」栏**明写 = T-2 = 本条**）⇒ ⇒ **不是缺陷 ⇒ 撤出派单表**<br>⚠️ **原先「维持未修 ⇒ 派单 G4」的复核结论是错的** —— 它**只看了域台账原文**，**没查签字档** | 签字档 `SD-2026-10-25-01`（L1 决策记录）· 共享层代码 javadoc `GoStyleMaxConnsFilter.java:67-72`（已指向签字档 + 明文「不要顺手改回」）· global 收口档 `docs/DRIFT/global/_README.md:88`（T-2 ✅ 已签字 ⇒ 已移出本档）· hot 域 `docs/DRIFT/hot/vs-common.md:31`（`D27` 同族已闭合）· users 域 `docs/DRIFT/users/vs-common.md:2740`（「MaxConns 半边 = 已签字偏离」）<br>⚠️ **2026-10-30 真实性复核（follow+task lane · 指针订正）**：上面这个 users 侧指针 **`:2740` 已指错行** （该行现在讲「同一份代码，两棵树读出两个相反的结论」，与 MaxConns 无关）⇒ **现行指针 = `docs/DRIFT/users/vs-common.md:111`**（该行明写「① **MaxConns 半边 ✅ 已签字 ⇒ 不修** ⇒ 已撤出落码范围」＋ `DRIFT-FOLLOW-008` 已销号）＋ `:47`（同款「MaxConns 闸门粒度 = ✅ 已签字」）⇒ **签字事实不变 ⇒ 本行现行态仍 = ✅ 仍成立（不变）**；核对日期 2026-10-30 |
| `DRIFT-FOLLOW-002` | ✅ 已修 | ✅ **维持已修**，且**本轮真启动复验**（`follow-rpc` 6.03s 起 · `NoClassDefFoundError` 未复现）<br>⚠️ **2026-10-30 真实性复核（follow+task lane）**：短确认 —— RAG `service/follow/follow-rpc/pom.xml:256-260` = `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`**（compile 默认）＋ `:236`（注释明写「scope 必须是 compile（默认），不能是 test」）⇒ **运行期 classpath 有该类** ⇒ **现行态 = ✅ 已修（不变）**；⚠️ **跨域那半边**（域外 4 模块仍是 `test` scope）**2026-10-29 已订正**，本轮逐模块 `grep -A4 grpc-inprocess` 复核**属实**（`article-rpc` / `like-rpc` / `task-rpc` = 无 `<scope>`、`favorite-rpc` = `<scope>runtime</scope>`）⇒ **全仓已无 `grpc-inprocess` 的 `test` scope**。核对日期 2026-10-30 | **L3 活体**（真容器） |
| `DRIFT-HOTAPI-030` | ✅ 已落地 | ✅ **维持已落地**（共享层 6 个类在位）<br>⚠️ **2026-10-30 真实性复核（follow+task lane）**：短确认 —— 6 个类逐个实测仍在位（`service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:170` = `MAX_PARAMETER_COUNT = 10001` ＋ `GoStyleFormLimitMode.java` · `GoStyleFormLimitWebMvcConfig.java` · `GoStyleFormValueLimitInterceptor.java` · `GoStyleFormValues.java` · `GoStyleFormLimitExceededException.java`）· 本域消费点 RAG `service/follow/follow-api/src/main/resources/application.yml:219` = `mode: envelope` ⇒ **主轴 ✅ 已落地（不变）**（⚠️ **「后续格（段数轴）」仍 🔴 在 `global`/G4** ⇒ 本行只确认主轴）。核对日期 2026-10-30 | L1 |
| 🆕 `DRIFT-G1-001` / `DRIFT-G1-002` | —（快照里没有） | 🔴 **本轮新立**（真启动抓出）⇒ **落点在本域写权内 ⇒ 域侧自修，不派 G4** | **L3 活体 A/B + Go 侧 L1 源码行号** ⇒ 见 §7 |
| 🆕 `DRIFT-FOLLOW-010` | —（快照里没有） | ✅ **本轮新立并已修**（推广 `DRIFT-USERAPI-009②` 时顺手查出）⇒ **落点在本域写权内 ⇒ 域侧自修，不派 G4**（共享层槽位只**读取**、未改） | **L3 真 Tomcat 活体（10 条探针）＋ L2 装配层守门 + Go 侧 L1 源码行号** ⇒ 见 [`api.md` §11](api.md) |
| 🆕 `DRIFT-FOLLOW-011` | —（快照里没有） | ✅ **本轮新立并已修**（给 users 域对齐 Redis 超时时顺手查出）⇒ **落点在本域写权内 ⇒ 域侧自修，不派 G4**（只改本域 `application.yml`） | **L2 配置层守门（Spring 真实 Binder 绑定结果）＋ Go 侧 L1 出处链（`redisclientmanager.go:32-39` + go-redis `options.go` init()）** ⇒ 见 [`api.md`](api.md) 的 `DRIFT-FOLLOW-011` |

⚠️ **本轮复核范围声明（诚实边界）**：`DRIFT-FOLLOW-002` 有**真启动活体复验**；
`DRIFT-HOTAPI-030` **状态未变**（按 AGENTS.md §6 记「状态未变」）
⇒ **派单时仍须按反馈单 7 项重新核 Go 侧 `文件:行号`**（台账的「抽取快照」**不算数**）。
⚠️ **2026-10-29 补记**：`DRIFT-FOLLOW-008` 的 2026-10-27 复核结论**已被本轮推翻**
（见上表）⇒ **「快照里的状态」与「复核时的状态」都可能过期于签字档** ⇒
⇒ **派单前必须先查 `docs/Intentional_Deviations/**`（决策真源）**，否则会把**用户已决定保留的行为**当缺陷派出去。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ① `DRIFT-FOLLOW-009`（下游挂死：Go **2s ⇒ 200+1015** / RAG **3s ⇒ 503**）✅ 已修 ② ✅ **`DRIFT-G1-002` 已修**（**阶段 2 · 2026-10-27**：`follow-api` 补 `immediate-connect-timeout: 3s` + `FollowRpcClient.afterPropertiesSet()` 自检 ⇒ 真启动 C 组实测**进程退出**，对齐 Go `zrpc.MustNewClient` 的 panic 语义 ⇒ 证据见 §7.5）③ ✅ **`DRIFT-FOLLOW-010` 已修**（**本轮 · 2026-10-27**：Go 的 3s 计时器在 `Authorize` **之后**起算 ⇒ **验签不计入**；RAG 把验签搬进 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`，在 3s 预算的 `+7` **之外**）⇒ 与 Go 一致 ⇒ 证据见 [`api.md` §11](api.md)）④ ✅ **`DRIFT-FOLLOW-011` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：`follow-api` 的 `spring.data.redis.timeout` 原为 **2s** ≠ Go 的 `ReadTimeout` **3s**，且**缺 `connect-timeout: 5s`** ⇒ 已对齐 ＋ 新增 `FollowApiRedisTimeoutConfigTest` 2 例；⚠️ **Go 侧 go-zero `redis.go:27` 的 `readWriteTimeout=2s` 是死常量**（只有 BLPop 用）⇒ 那个 2s 看起来正是从这里抄来的 ⇒ **不能当依据**）⑤ 🟡 ~~follow-api 的 Redis 命令超时 2s ≠ go-redis `ReadTimeout` 默认 3s~~（`DRIFT-USERAPI-015 附带` 登记的待办）—— ✅ **本轮已审并归入 ④**（原条目作废，见 `api.md` 的 `DRIFT-FOLLOW-011`）⑥ ✅ **`DRIFT-FOLLOW-012` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：Lettuce 默认 `disconnectedBehavior=DEFAULT` ⇒ **Redis 断连时命令被排队**等重连 ⇒ 挂到 `commandTimeout: 3s` ⇒ **撞 HTTP 3s 预算 ⇒ 503**；Go 侧 `MaxRetries=3` ⇒ **快速失败** ⇒ **fail-open 200** ⇒ 落**域内副本** `config/RedisDisconnectedBehaviorConfig`（`REJECT_COMMANDS`）＋ 守门 `FollowApiRedisDisconnectedFastFailTest` 2 例；⚠️ **修前实测 3012ms ⇒ 修后 0ms**；🔴 **共享层收编仍归 G4**）⑦ 🆕 **命令超时的「机制腿」已补**（**2026-10-27 · G1 lane · 探针实测**）：`TimeoutOptions.source` 恒为 `DefaultTimeoutSource`（返回 -1）⇒ 3s 只能由 **`applyConnectionTimeout`** 分支从连接级 timeout（`LettuceConnectionFactory.getTimeout()=3000ms` ⇒ `RedisURI.timeout`）取 ⇒ ⇒ **`isTimeoutCommands()` 恒真、不是有效判据** ⇒ 只断言「值」是**假绿口子**（机制被关 ⇒ 值仍读得到 3s、命令却永不过期 ⇒ 断连时从「3s 快速失败」退化成「无限等待」）⇒ `FollowApiRedisDisconnectedFastFailTest` 已补**机制腿**（＋ 变异：期望值 3000L→3001L ⇒ 红 ⇒ 断言在读运行期值）⇒ 证据详见 [`users/user/rpc.md` §4.1.1](../users/user/rpc.md) | ✅ **4 条已修** ＋ ⑦ 覆盖缺口已补 | L3 双侧活体（台账原有）＋ **本轮真启动活体（A/B/C 三组 ＋ L3 真 Tomcat 10 条）** ＋ **Go 腿 L4 活体·直接判据（③）+ RAG 腿 L3 真 Tomcat·间接判据 ⇒ 合并按弱侧记 L3**（G2 建、本域独立复跑 ⇒ 见 `users/user/api.md` §9.y.0 与 `users/vs-common.md` §8.4；⚠️ 不笼统写 L4） ＋ **L2 配置层守门（④）** ＋ **L3 真容器活体（⑥ · 真 Redis + 可控 TCP 代理造 FIN）** ＋ **L3 探针实测（⑦ · 命令超时机制腿）** | 2026-10-27 |
| **A2** | gRPC 拦截器链 | ① `DRIFT-FOLLOW-002`（运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败）✅ 已修 ② `DRIFT-FOLLOW-003`（`discovery://user-rpc` 两斜杠被 devh 拒绝 ⇒ 启动失败）✅ 已修 ③ **服务端拦截器链**：`FollowRpcApplication.java:118` 挂 `GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps)`（recover→breaker→shedding→业务观测）⇒ 有**组件级守门网** `FollowProtectionChainWiringTest`（断言 `@Import` 含 `GoStyleProtectionConfiguration` ＋ 链**真包住**业务 interceptor ⇒ 能区分「在链里」与「链没挂上」） | ✅ **一致**（①② = 启动阻塞型硬失败，已修）＋ ✅ **③ 链已挂且有装配层守门** | L1 + 真启动复验（①②）＋ **L2 装配层守门**（③） | 2026-10-27 |
| **A3** | HTTP 保护链 | ① `follow-api` 的 `rag.http.go-form-limit.mode` = **envelope**（`application.yml:159`）② ✅ `DRIFT-FOLLOW-008`（**MaxConns 闸门：Go 每路由一个 vs RAG 全局一个**）⇒ **已签字偏离 `SD-2026-10-25-01` ⇒ 不修**（⚠️ **2026-10-29 订正**：原记 🔴 未修 ⇒ 派单，**是陈旧的** —— 2026-10-25 已签字） ③ `OPEN-001`（Content-Type 不支持 ⇒ RAG 415 分支）✅ **已关闭（实测不可达）** ④ ⚠️ **`follow-rpc` 无 `rag:` 块** ⇒ 纯 gRPC 进程，表单限闸门**不适用**（N/A）⑤ ✅ **验签与保护链的相对次序已对齐**（**本轮 · 2026-10-27** `DRIFT-FOLLOW-010`）：验签搬进 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`）⇒ 落在共享层 `+3 Traceparent` 之内、`+7 HttpMiddleware（3s 预算）` 之外、`+8 MaxBytes` 之外侧 ⇒ ① **Filter 绝不写响应** ⇒ **413 仍压过 401**（真 Tomcat 实测 413 + 空 body）② **401 仍由 `FollowJwtAuthInterceptor` 抛** ⇒ **空 body** 形状逐字不变 ③ **未注册路径仍 404**（`handler instanceof HandlerMethod` 判据保留）⇒ 共享层槽位实测值见 [`api.md` §11.2](api.md) | ✅ **1 条已签字** ＋ ✅ 3 条 ＋ **N/A 1 条** ⇒ **本轴 0 条 G4**（2026-10-29 订正：原先的「🔴 1 条 G4」= `DRIFT-FOLLOW-008` ⇒ 已签字 ⇒ 撤出） | L1 源码 + **L2 装配层守门（容器装配结果）** + **L3 真 Tomcat 活体** + **L1 决策记录（签字档）** | 2026-10-29（**A3 第 ② 项订正**；其余沿用 2026-10-27） |
| **A4** | 错误码 / envelope | ① `DRIFT-FOLLOW-001`（**A7/A4 邻接**：wire 级出参 Go `"user_ids":null` / RAG `"user_ids":[]`）✅ 已修 ② `DRIFT-FOLLOW-009` ✅ ③ A3 锚点：Go 侧**已注册**（真源 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler`）⇒ Go = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅ | ✅ **一致** | L1 + 活体对拍（台账原有） | 2026-10-27 |
| **A5** | 端口治理 | ① `follow-api` HTTP 端口：Go `followcenter.yaml:3` `Port: 8891` ↔ RAG `application.yml:52` `${FOLLOW_API_PORT:8891}` ⇒ **同默认值**，RAG 另加 env 覆盖（**更宽**，非收窄）② `follow-rpc` gRPC 端口：Go `follow.yaml:2` `ListenOn: 0.0.0.0:8082` ↔ RAG `application.yml:103` **`${FOLLOW_RPC_PORT:8082}`** ⇒ **默认端口一字不变**（仍是 8082 ⇒ 行为零变化）＋ **RAG 另加 env 覆盖**（**更宽**，非收窄）⇒ ⚠️ **2026-10-29 订正**：本项原写「`port: 8082` ⇒ 两侧同为 yaml 写死」——**那是 2026-10-28 落码前的状态**；**2026-10-28 · G1 lane 已改为可 env 覆盖**（`S1-FOLLOW-005` / `S1-USERS-005` ⇒ **本机 8082 被一个运行了 34 天的 `python -m http.server 8082` 占着 ⇒ 组件无法真启动**）⇒ ⇒ **现在两侧组件的端口策略一致**（`follow-api` = `${FOLLOW_API_PORT:8891}` / `follow-rpc` = `${FOLLOW_RPC_PORT:8082}`）｜ ⚠️ **改口时必须同时确认 Nacos 注册地址跟着变** —— devh 用同一端口既监听又注册，两者不同步 ⇒ `follow-api` 找到旧地址 ⇒ 业务码塌成 1015③ ⚠️ **`RANDOM_PORT` 面的记账缺口（2026-10-27 补记）** —— **实测清点**（非印象）：全仓 `RANDOM_PORT` 命中 **21 个文件**（`service/` 全树），**follow-api 域内零命中**、**零** `DEFINED_PORT` ⇒ 本域的 HTTP 响应头层验证走的是 **`webEnvironment = MOCK` ＋ `@AutoConfigureMockMvc`** ⇒ **MockMvc 测不到真 Tomcat 的 header 逐字节** ⇒ 真 Tomcat 面由**仓外脚本** `tools/difftest/check-plaintext400-headers.sh` 覆盖（**不在域内台账的证据面**） | 🟡 **端口值两侧同源 ✅** ＋ **`RANDOM_PORT` 面仍是记账缺口**（**不是「已对齐」**）—— 本轮把「零覆盖」从**空白**变成**有出处的记录**（缺口在哪个文件、为什么 MockMvc 不算、真 Tomcat 面由谁覆盖 三点已钉死） | L1 静态（`grep` 全树清点 ＋ 逐个测试类的 `webEnvironment` 实读） | 2026-10-27（**本轮补记**） |
| **A6** | errmsg 契约 | 本域 `follow-common`（**16 个业务码 + 16 条文案 + 三条错误转换路径**）↔ Go `service/follow/common/errmsg.go` | ✅ **零漂移**（域台账 [`common.md`](common.md) 判定 **`SEMANTICALLY EQUIVALENT`**：16 码逐条一致（含 `CodeServerBusy=1015` 排在 5001 之前这个细节）＋ 37 条契约测试 ＋ api 层 52/52 活体对拍） | **L4 双侧活体**（域台账核对日期 **2026-10-24**） | 2026-10-27（**本轮由「UNKNOWN」订正为 ✅**） |
| **A7** | proto 契约 | ① `DRIFT-FOLLOW-005`（recommend 出参**字段顺序** Go=`target_id` 在前 / RAG=`mutual_score` 在前）✅ 已修 ② 两侧 `.proto` 逐行 diff ③ **A7 邻接** `DRIFT-FOLLOW-001`（空列表出参 Go `"user_ids":null` / RAG `"user_ids":[]` ⇒ **wire 级**，非 A3 面）✅ 已修 | ✅ **一致** | L1 实测 diff（②）＋ L3 活体（①③） | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | follow 域 **五维全等**（**10 列 / 8 索引 / 2 约束**）⇒ 类型/长度 0、可空性 0、DEFAULT 0、索引 8≡8、约束 2≡2、触发器 0≡0 · 建表走 Flyway 独立账本 `flyway_schema_history_follow` ＋ `baseline-version: 0` | ✅ **一致** | **L3 双侧活体**（真 PG 16）· 2026-10-25 · 权威源 `docs/DRIFT/global/lanes/L3-ddl.md` §8.1/§8.9 ＋ 🆕 **本轮真启动复验**（非空 schema 下 baseline 打在 version 0 ⇒ V1/V2 照常执行） | 2026-10-27 |
| **A9** | trace / otel | ① **A9 本体（span 属性 `audit.*` ＋ `Traceparent` 传播）**：本域台账对 `Traceparent` / `audit.` **零命中**（`grep -c` 实证：整个 `docs/DRIFT/follow/` 只有本文件命中）⇒ **原记「域侧零覆盖」** —— ⚠️ **本轮已回真源逐条清点并重新定性**（见下方 A9 补记：**不是空白轴，是「两侧都无 ⇒ 无漂移」**）② 共享层本体**已闭合**：`GoStyleOtelAutoConfiguration` 走 **`META-INF/spring/…AutoConfiguration.imports`** ⇒ **不依赖组件扫描** ⇒ 纯 gRPC 的 `follow-rpc` 也自动装配（这正是该类注释写明「rpc 模块刻意不扫 `com.rag.common`」的解法）③ `GoStyleOtelConfig.java:27,96` 明确支持「endpoint 带/不带 `http://` 两种写法」＋「`protocol` 默认 grpc」⇒ **本域 api 与 rpc 的 otel 属性写法不一致（`otel.resource.attributes.service.name` vs `otel.service.name`；`always_on` vs `parentbased_always_on`）但落点等价 ⇒ 不构成漂移** ④ `DRIFT-FOLLOW-007` = **A9 邻接**（Prometheus 文本层，**非 A9 本体**）🟡 **已修（残留未清 ⇒ 与 `api.md` 收敛到同一状态）** ⑤ `DRIFT-FOLLOW-006` = **A9 邻接 ＋ A5 邻接**（Go DevServer `:16065/metrics`）✅ **已修（2026-10-29 · 编号 `S1-FOLLOW-001`）** —— 出口已落地（Tomcat + Actuator，端口 16065 与 Go 一字不差）⇒ 双侧 series 集合逐条一致（仅余 P3/D 类的 8 条 `_max` 增项）⇒ 详见 [`rpc.md`](rpc.md) §「DRIFT-FOLLOW-006 的落地（2026-10-29）」 | 🟡 **两条「零覆盖」本轮已重新定性**（**不是空白轴，是「两侧都无 ⇒ 无漂移」**）—— 详见下方 A9 补记 | L1 静态（**本轮补：Go 侧 `audit.*` / `otel.` 逐域清点**）＋ 共享层台账（`common/audit.md` / `global/lanes/L1-delivery.md`） | 2026-10-27（**本轮补记 + 订正**）<br>🆕 **2026-10-29 对齐（W-B lane）**：本行原写「`DRIFT-FOLLOW-006` **🔴 未修**」⇒ **该状态已过期** —— 与 `rpc.md:20` / `rpc.md:182` / 本文件 `:26`/`:39` 的「✅ 已修（2026-10-29 · `S1-FOLLOW-001`）」**收敛到同一状态：✅ 已修** ⇒ **原值保留不改写** |

> **🆕 A9 补记（2026-10-27 · G1 lane · 把「零覆盖」从空白变成有出处的对照记录）**
>
> ⚠️ **原措辞的问题**：① 只说了「本域台账里没有 `Traceparent` / `audit.` 的条目」，
> **没说「两侧实际是什么」** ⇒ 读者无法判断是**记账缺口**还是**真漂移**。
> ⇒ 本轮**回真源逐条清点**（**L1 静态对照，不是活体对拍**）：
>
> | 面 | Go（真源） | RAG | 判定 |
> |---|---|---|---|
> | **`audit.*` 属性** | `grep -rn "audit\." service/follow --include=*.go` ⇒ **零命中** | follow 域**零** `audit.*` | ✅ **两侧都无 ⇒ 无漂移**（⇒ 原「域侧零覆盖」**不是缺口**） |
> | **otel span 本身** | `grep -rn "otel\." service/follow --include=*.go` ⇒ **零命中** ⇒ **follow 域 Go 侧连 span 都不开** | follow 域有共享层 otel 装配（`GoStyleOtelAutoConfiguration`）⇒ **RAG 比 Go 多** | 🟡 **多出来的部分落点在共享层**（`service/common` ⇒ **G4**）⇒ **不是本域漂移**（域台账 `DRIFT-FOLLOW-006` 记的 DevServer 指标面是另一条，🔴 仍开放） |
> | **`Traceparent` 传播** | Go 的 `service/common/observability`（= RAG `service/common` 的对应物）**零 `traceparent` 命中**；W3C 传播器（`otel.SetTextMapPropagator` + `Extract` + `Inject`）在 **`service/common/communityfact`**（`logging.go:97` · `authority_http.go:30` · `wire.go:104`）—— **社区事实投递链路的另一个库**，**follow 域不走它** ⇒ **本链路两侧都没有「入站 traceparent 提取」** | `service/common/http/GoStyleTraceparentFilter`（`@Order(+3)`）把 W3C `traceparent` **写进响应头**（共享层 T-8 条目） | 🟡 **响应头回写是 RAG 侧多出来的**，**落点在共享层** ⇒ **不是本域漂移** |
>
> ⇒ **本轮 A9 的净结论**：follow 域的 A9 本体 **两侧都无 `audit.*`、两侧都无入站 traceparent 提取**
> ⇒ **域侧零漂移**；RAG 侧「多出来」的共享层 otel 装配与 traceparent 响应头回写 ⇒ **落点 `service/common` ⇒ G4**。
> ⇒ ⚠️ **证据边界**：**L1 静态对照** ⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」（**不是活体对拍**）。

> **A3 已预填一条实测锚点（2026-10-27 核）** —— CD-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
> （`GoStyleFormValues` / `GoStyleFormLimitMode` / `GoStyleFormLimitExceededException` /
> `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）；
> 本域 `go-form-limit.mode` = **envelope**；Go 侧：**已注册**（真源 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler`）⇒ Go 侧 = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅
> ⇒ ⚠️ **共享层已验 ≠ 本域已验** ⇒ lane 仍须在本域**跑一次真实请求**确认拒绝形状。

> **🆕 A2/A9 的「域侧记账缺口」（2026-10-27 本轮订正）** —— 下面两条**不是行为漂移**，是**本域台账漏记**：
> ① **A2 ③**：`follow-rpc` 的服务端拦截器链（recover→breaker→shedding→业务观测）**已挂上**且**有组件级守门网**
>    （`FollowProtectionChainWiringTest`），但**域台账 `rpc.md` 零记 `DRIFT-MSG-V-026` / `GoStyleRpcProtectionChain`**
>    ⇒ ⇒ **台账缺口**：实现与守门都在，只是没进台账 ⇒ **域侧自修（补登记）**，**不派 G4**。
> ② **A9 ①②**：本域台账对 `Traceparent` / `audit.` **零命中** ⇒ **A9 本体在本域是空白轴**（不是「已对齐」）。
>    共享层本体（otel 装配 + 传播器）已由 `global` lane L1 闭合 ⇒ **不需要 G4 再动共享层**。
>    ⇒ ⇒ **台账缺口**：域侧该补 A9 的对照记录（**下一轮**，含双侧活体对拍）⇒ 本轮只登记缺口，**不落码**。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

- 本域**调用** `GoStyleMaxConnsFilter` 的**粒度参数**（若 RAG 侧只是传参口径问题 ⇒ 域侧可自修；**只有共享层本身实现错**才派单）
- `follow-api` 的 A3 接线复核：`mode=envelope`

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> ⚠️ **本节 = 正式轨（派单单据）**。**实时轨 = `session send` 给 G4 会话**（两棵工作树合并前互相看不见对方的文件 ⇒ **两轨都要有**）。
> ⚠️ **派单前置（2026-10-27 已完成）**：Go 侧 `文件:行号` **已回冻结真源核过存在性**（不是台账快照）。

| # | 编号 | 现象 | Go 侧 `文件:行号`（**已核 ✅**） | RAG 侧现状 | 证据强度 | 建议修法（**两侧都应是 Y**） | 影响面 | 阻塞本波？ |
|---|---|---|---|---|---|---|---|---|
| ~~**1**~~ | ~~`DRIFT-FOLLOW-008`~~ | ⛔ **已撤销派单（2026-10-29 · G1 lane 阶段 1 复核）** —— 本条**不是缺陷** ⇒ **不得落码** | **（不再需要 Go 出处）** | **（不再需要 RAG 现状）** | **L1 决策记录**：[`docs/Intentional_Deviations/global/common-tails.md`](../../Intentional_Deviations/global/common-tails.md) 的 **`SD-2026-10-25-01`**（2026-10-25 **用户签字**；签字决定 = **保留 RAG 的全局闸门，不改成 per-route**；签字档「对应的 DRIFT 条目」栏**明写 = T-2 = 本条**） | **（不适用 —— 决策为「不修」）** | `service/common/protection/` ⇒ 编译面 16 组件（**受影响，但不动**） | ✅ **不阻塞**（**已销号**：派下去 = 让 G4 去改掉一个用户已决定保留的行为 ⇒ AGENTS.md §0 第 1 条 / §5.3b 明令禁止） |
| **2** | 🆕 `PITFALL-FOLLOW-005`（**本轮新登记** · 域台账 [`../../Pitfalls/follow/rpc.md`](../../Pitfalls/follow/rpc.md) §PITFALL-FOLLOW-005 · **状态：🔴 未修，本轮已复核仍在**） | **共享层在纯 gRPC 进程里缺 servlet-api** ⇒ 进程**关闭路径**抛 `NoClassDefFoundError: jakarta/servlet/Filter`（污染关闭日志、掩盖真正的关闭原因） | **Go 侧无等价形态**（不是双向语义漂移，是 **RAG 共享层混装**）：`service/follow/rpc/follow.go:3-21` —— **已核 ✅**：import 清单里**零** `net/http` / servlet 类（`grep -rn "net/http\|servlet\|rest\." service/follow/rpc/` 零命中）＋ `:45` `defer s.Stop()` ⇒ Go 侧 rpc 进程的**关闭路径不加载任何 web 类型** | ① `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:8` `import jakarta.servlet.Filter;`（`:236` `newHttpMiddleware(...)` 返回 `Filter`）② `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:54` `HttpMiddleware.shutdown();` ⇒ **该类在纯 gRPC 进程里的首次加载恰好落在 destroy 路径** ⇒ 抛 `NoClassDefFoundError` | **L1 源码逐行**（根因链已读通：import → 类加载 → destroy 触发）＋ 域台账既有活体症状（`Pitfalls/follow/rpc.md`） | 两侧都应是 **Y** = 「**关闭路径不得触发 web 类型的类加载**」⇒ ① HTTP 侧的池回收挪进**只在 servlet 进程存在**的 Bean（`@ConditionalOnWebApplication(type = SERVLET)`，与 `GoStyleTraceparentFilter` 同款），gRPC 进程只回收 `RpcInterceptor` 的池；② 或把 `HttpMiddleware` 的静态线程池提到**无 web 依赖**的独立类，`shutdown()` 不碰 `HttpMiddleware` 本身 | `service/common/{observability,spring}/` ⇒ 编译面 16 组件 · 受害面 = **所有纯 gRPC 进程**（follow-rpc / user-rpc / admin-rpc / like-rpc / …） | 🟡 **不阻塞**（只污染关闭日志，不影响启动与业务） |

> ⚠️ **派单纪律**：① **缺项不进排期** ② **编号必须带全前缀** ③ **同族 ≠ 同一条**（本条与 users 侧那条编号不同）
> ④ **回执要求 4 项**：`git rev-parse HEAD` + 改动文件清单（逐个 `文件:行号`）+ 触及哪几条 A 轴 ＋ 对应 Go 出处 + 回归口径与结果
> ⇒ **只给 hash ⇒ G1 只能盲 merge**。
>
> 📌 **本域的两条新缺陷不派 G4**：`DRIFT-G1-001`（`follow-rpc` 的 group 写死）· `DRIFT-G1-002`（`follow-api` 缺 `immediate-connect-timeout`）⇒ 落点全在 `service/follow/**` = G1 写权 ⇒ **域侧自修**（见 §7）。

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）⇒ §7（follow 侧受影响部分）· **L3 活体**
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期** ⇒ **本轮又订正了 7 行**（见 §9）
- [x] **两侧同时跑**的探针（AGENTS.md §3.3）—— 只跑 RAG 的探针**不算数** ⇒ §8（`run-parity` / `rpc-timeout` 均 EXIT=0）
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）⇒ §1.1
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）⇒ ⏸ **阶段 2**（本轮 = 阶段 1 只读对账，不落码）
- [x] §4 的派单**登记到 G4** ⇒ **本域台账已写**（§4 表）；**实时轨** = `session send` 给 G4 会话；`docs/DRIFT/global/vs-common.md` 由 **G4 写**（G1 只读）
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由合并收口执行者统一更新）

---

## 6. 🆕 **G1 ⇄ G4 配合范围**（常驻 · 2026-10-27 02:4x 记）

> ⚠️ 本节 = **操作口径**（谁写哪 · 怎么派 · 派单长什么样）⇒ **不替代** AGENTS.md §5.3d·§5.3e 与
> [`cross-domain-coupling.md`](../_cross/cross-domain-coupling.md) §三 **C1~C7**。
> ⇒ **通用口径写在 [`docs/DRIFT/users/vs-common.md` §6](../users/vs-common.md)（users 侧那份更全）** ⇒
> **本节只记 follow 域特有的部分**，不重复抄一遍。

### 6.1 follow 侧写权（守门：`tools/difftest/check-wave-lane-ownership.py` ⇒ EXIT=0）

| 面 | G1（`align/g1-users-follow`） | G4（`align/g4-service-common`） |
|---|---|---|
| `service/follow/**`（api/rpc/common/proto） | ✅ **可写**（含 `follow-common` ⇒ **本域真私有**，§5.3f） | 🔴 只读 |
| `service/common/**` · `service/users/users-common/**` | 🔴 **只读** | ✅ **可写（唯一）** |
| `docs/DRIFT/follow/**` | ✅ 可写 | 🔴 只读 |
| `docs/DRIFT/global/vs-common.md` | 🔴 只读 | ✅ 可写 |
| `AGENTS.md` · 三个 `_INDEX.md` · `_MANIFEST.json` · `docs/DRIFT/_cross/**` · `docs/Worktree.md` · `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` | 🔴 **无人拥有** | 🔴 **无人拥有** ⇒ **合并后由合并收口执行者统一更新** |

### 6.2 follow 侧派单（G4）—— **2 条，已核准落点**（⚠️ **2026-10-29 撤销 1 条**：原 3 条）

| 编号 | 摘要 | 落点 | 归属 | 状态（**2026-10-29** 核） |
|---|---|---|---|---|
| ~~`DRIFT-FOLLOW-008`~~ | ~~**MaxConns 闸门**：Go **每路由一个** vs RAG **全局一个**~~ | ~~`service/common`（`GoStyleMaxConnsFilter`）~~ | — | ✅ **已销号（不是缺陷）**：**2026-10-25 用户签字** ⇒ [`SD-2026-10-25-01`](../../Intentional_Deviations/global/common-tails.md)（**保留 RAG 全局闸门，不改成 per-route**）⇒ ⚠️ **原登记「🔴 未修 ⇒ 派 G4」是陈旧的** ⇒ **撤出派单表**（AGENTS.md §0 第 1 条 / §5.3b） |
| 🆕 `PITFALL-FOLLOW-005` | 共享层在**纯 gRPC 进程**里缺 servlet-api ⇒ **关闭路径**抛 `NoClassDefFoundError: jakarta/servlet/Filter` | `service/common`（`HttpMiddleware` + `ObservabilityLifecycle`） | 🔴 **G4** | 🔴 未修 · **本轮已复核仍在**（`HttpMiddleware.java:8` import ＋ `ObservabilityLifecycle.java:54` destroy 触发）⇒ **受害面 = 所有纯 gRPC 进程**，不只 follow |
| 🆕 **Redis 断连行为（`REJECT_COMMANDS`）的共享层收编** | **五个**用 Redis 的组件的 Lettuce **断连**行为必须 `REJECT_COMMANDS`（否则命令被排队挂到 `commandTimeout` 3s ⇒ **api 侧**撞 HTTP 预算 ⇒ **503**（Go 是 fail-open **200**）；**rpc 侧**撞 `rpc-timeout: 2s` ⇒ **RPC 先超时** ⇒ 客户端看到 **1015**（Go 是**快速失败** ⇒ `INTERNAL「注销失败,Redis写入异常」`）） | 🟢 **域侧已自修**（**本域**：`config/RedisDisconnectedBehaviorConfig`）<br>🔴 **共享层收编** = 把**五份**域内副本（3 api ＋ **2 rpc**：`user-rpc` / `admin-rpc`，2026-10-27 本轮补）并成一个 `service/common` 自动配置 | 🟢 **域侧**（已完成）<br>🔴 **G4**（**收编**） | 🟢 **域侧已闭环**（2026-10-27 · `DRIFT-FOLLOW-012` · 修前 **3012ms** ⇒ 修后 **0ms** · 变异 2/2 变红）<br>🟢 **共享层已落码**（G4 commit `1fb26b2` ＋ `2f12236` · `GoStyleRedisClientAutoConfiguration` 走 `AutoConfiguration.imports`）⇒ **待 G4 分支合入本树后，删除这五份域内副本**（`user-api` / `admin-api` / `follow-api` / `user-rpc` / `admin-rpc`）⇒ **删除前置 ＝ 共享层那份先合进各域的树**（否则双份配置 ⇒ 两处都要维护）<br>🔴 **2026-10-27 升级危害等级**（据 G3 提供的信息）：G4 那份的 customizer 带 **`@ConditionalOnMissingBean`** ⇒ **域内副本在 ⇒ 共享层那份整个不创建** ⇒ ⇒ **不删副本 ⇒ 收编根本没发生**，且**共享层后续改动对本域静默不生效** ⇒ ⇒ **删除动作是收编的「必要条件」而非「收尾清理」** ⇒ 收编完成时必须**复验**共享层那份是否真的生效。<br>⚠️ **rpc 侧与 api 侧后果不同**（503 vs RPC 撞超时）⇒ 收编时**不要只按 api 侧的现象判「已修」** |

⚠️ **派单口径（7 项 · 缺项不进排期）**见 [`users/vs-common.md` §6.2](../users/vs-common.md) ⇒
**编号必须带全前缀**（`DRIFT-FOLLOW-008` ≠ `DRIFT-USERAPI-013` ⇒ 同族 ≠ 同一条）⇒
**落点双轨**：实时 `session send` 给 G4 会话 ＋ 正式按写权写台账（**G1 写本文件、G4 写 `docs/DRIFT/global/vs-common.md`** ⇒ 合并前互相看不见对方的文件）。

### 6.3 follow 侧**自修**（不甩 G4）

- `follow-api` 的 A3 接线：`mode=envelope`（Go 侧 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler` ⇒ Go = 200 + 业务信封 ⇒ 同向 ✅）
- `follow-api` 的 `JsonMapperBuilderCustomizer` 注册的是**本域私有**的 `FollowGoStyleCoercion::applyTo` ⇒
  **不共用 `users-common` 的通用版** ⇒ ✅ 域侧自修面，**不牵连 users 通用版**（对比：`favorite-api` 直接复用了 users 通用版 ⇒ 见 users 侧 §6.4.1）
- `follow-common` 是**本域真私有**（§5.3f：只被本域引用）⇒ **G1 可写** ⇒ 共享层缺陷不适用
- 🆕 **Redis 断连行为**（`DRIFT-FOLLOW-012` · 2026-10-27）：`config/RedisDisconnectedBehaviorConfig`
  —— **域内副本**（`REJECT_COMMANDS`）⇒ **完全在 G1 写权内** ⇒ **不需要等共享层**；
  ⚠️ **但共享层收编（把五份副本并成一个自动配置）仍归 G4** ⇒ 见 §6.2 末行。

---

## 7. 🆕 **第 0 步真启动抓到的两条域内真缺陷**（2026-10-27 · G1 lane · follow 侧也在链上）

> ⚠️ **完整证据链见 [`docs/DRIFT/users/vs-common.md` §7](../users/vs-common.md)**（同一轮、同一 A/B 实验、同一批进程）。
> ⇒ **本节只记 follow 侧受影响的部分** ⇒ **两条都落在 G1 写权内 ⇒ 域侧能自修 ⇒ 不派 G4**。

### 7.1 `DRIFT-G1-001` — 🔴 `NACOS_GROUP` 对 `follow-rpc` **完全无效**（配置项形同虚设）

> ⚠️ **2026-10-29 真实性复核**：标题的 **🔴** ⇒ **现行态 = ✅ 已修（2026-10-27 阶段 2 落码 · 本轮复核属实）** ⇒ **标题标记是「本条新立时」的陈旧快照**（正文首行横幅已写「✅ 状态：阶段 2 已修（2026-10-27）」）。
> **依据（当场核过）**：`service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/bootstrap/NacosRegistration.java:104` = `@Value("${spring.cloud.nacos.discovery.group:DEFAULT_GROUP}")`（**注册方现在真的读配置** ⇒ 原「写死 `DEFAULT_GROUP`」的前提已消除）＋ `service/follow/follow-rpc/src/main/resources/application.yml:97` = `group: ${NACOS_GROUP:DEFAULT_GROUP}`（**rpc 侧与 api 侧同源**）⇒ ⇒ **两侧现在读同一个变量** ⇒ 原「注册到 A 组、api 去 B 组找」的失配形态**已消除**。
> ⚠️ **下方表格「修前的原始取证」保留不改写**（正文自己已声明「本节保留修前的原始取证」）⇒ 其中 `NacosRegistration.java:113` / `:133` 的行号是**修前**的行号（现已随落码位移）⇒ **读时以本订正段为准**。

> ✅ **状态：阶段 2 已修（2026-10-27）** ⇒ 修法与 A/B/C 真启动证据见 §7.5。**本节保留修前的原始取证。**

| 侧 | 位置 | 值 | 读配置？ |
|---|---|---|---|
| `follow-rpc`（注册方） | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/bootstrap/NacosRegistration.java:113` | `.registerInstance(SERVICE_NAME, DEFAULT_GROUP, instance)` | ❌ **写死** |
| `follow-rpc`（注销方） | 同文件 `:133` | `.deregisterInstance(SERVICE_NAME, DEFAULT_GROUP, …)` | ❌ **写死** |
| `follow-api`（查找方） | `service/follow/follow-api/src/main/resources/application.yml:42` | `group: ${NACOS_GROUP:DEFAULT_GROUP}` | ✅ 读配置 |
| Go（真源） | `service/follow/rpc/etc/follow.yaml:4-7` | `Etcd: { Hosts: [...], Key: follow.rpc }` | ✅ **可配** |

**活体 A/B（唯一变量 = `NACOS_GROUP`）**：

| 组 | `follow-rpc` 注册到 | `follow-api` 查询的 group | 结果 |
|---|---|---|---|
| **A**（不设） | `DEFAULT_GROUP` | `DEFAULT_GROUP` | ✅ 一致 ⇒ follow-api 6.62s 起 · 解析正常 |
| **B**（`G1_GROUP`） | `DEFAULT_GROUP`（**无视环境变量**） | `G1_GROUP` | ❌ **失配** |

⚠️ **`DRIFT-FOLLOW-004`（`server-addr` 硬编码 `:8848` ⇒ 忽略 `NACOS_PORT`）已于 2026-10-24 修复** ⇒
那一轮审的是「`NACOS_ADDR` / `NACOS_PORT` 的**变量语义**」这一维 ⇒ ⚠️ **「`NACOS_GROUP` 是否真被消费」这一维没人查过**
⇒ **同域、同族、漏审一维** ⇒ 见 users 侧 §7.4 的元教训（「配置项存在」≠「配置项被消费」）。

### 7.2 `DRIFT-G1-002` — 🔴 `follow-api` 缺 `immediate-connect-timeout` ⇒ 与 Go 的 `MustNewClient` panic 语义不一致

> ⚠️ **2026-10-29 真实性复核**：标题的 **🔴** ⇒ **现行态 = ✅ 已修（2026-10-27 阶段 2 落码 · 本轮复核属实）** ⇒ **标题标记是「本条新立时」的陈旧快照**（正文首行横幅已写「✅ 状态：阶段 2 已修（2026-10-27）」）。
> **依据（当场核过）**：`service/follow/follow-api/src/main/resources/application.yml:129` = `immediate-connect-timeout: 3s`（**已配** ⇒ 原「`follow-api` 的 `grpc.client.follow-rpc` **没有** `immediate-connect-timeout`」的前提已消除）＋ `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/client/FollowRpcClient.java:115`（注释复述同一处配置）＋ `:126`（结论：用 devh 的原生 `immediate-connect-timeout`，不自己摸 channel）⇒ ⇒ **与 Go `zrpc.MustNewClient` 的「失败即 panic」语义对齐的那一侧已落码**（A/B/C 真启动证据见本文件 §7.5）。
> ⚠️ **下方「RAG 侧……**没有** `immediate-connect-timeout`」是修前原始取证，保留不改写**（正文自己已声明「本节保留修前的原始取证」）⇒ **读时以本订正段为准**。

> ✅ **状态：阶段 2 已修（2026-10-27）** ⇒ 修法与 A/B/C 真启动证据见 §7.5。**本节保留修前的原始取证。**

- **Go**：`service/follow/api/internal/svc/servicecontext.go:27` ⇒ `followservice.NewFollowService(zrpc.MustNewClient(c.FollowRpc))`
  ⇒ go-zero v1.10.2 `zrpc/client.go:50-54` `logx.Must(err)` ⇒ **失败即 panic ⇒ 进程起不来**；`go-zero@v1.10.2/zrpc/internal/client.go:18` `dialTimeout = 3s`
- **RAG**：`follow-api` 的 `grpc.client.follow-rpc` **没有** `immediate-connect-timeout`
  ⇒ 对照：`admin-api` **有**（`application.yml:120`，`DRIFT-ADMINAPI-009` 已修）⇒ **同一 Go 语义，RAG 三个 api 里只对齐了一个**

**活体实证（B 组）**：`follow-api` **照样启动**（6.50s）⇒ 启动日志**无异常** ⇒ 首次业务请求才解析失败
⇒ 与 `user-api` 同款（实测 `user-api` ⇒ `HTTP 200 {"code":1015,"msg":"服务繁忙"}` + 日志 `No servers found for user-rpc`）
⇒ 🔴 **DRIFT：Go = 起不来 / RAG = 起来但请求全失败**。

### 7.3 🆕 A8 活体新证据：`baseline-version: 0` 在**非空 schema** 下真的生效

本轮 follow-rpc 与 users 域两个 rpc **同库并发启动**（`g1probe` 库，`public` schema）⇒
users 域的 `SchemaInitializer` **先**建了 `users_user` / `admin_admin` 等表 ⇒ schema **非空** ⇒
follow-rpc 的 Flyway 触发 `baseline-on-migrate: true` ⇒ 实测账本：

```
installed_rank | version |      description       | success
              1 |       0 | << Flyway Baseline >> | t
              2 |       1 | create follow relation | t
              3 |       2 | create block relation  | t
```

⇒ ✅ **baseline 标记打在 version 0 ⇒ V1/V2 照常执行**（若 `baseline-version` 是默认的 1 ⇒ **V1 会被整条跳过**）
⇒ ⇒ **AGENTS.md §6.3 问题 4 记的残留风险（「新增模块若忘加 `baseline-version: 0` ⇒ 起得来、首次查库才炸」）本轮实测**没踩** ⇒
⇒ 🟢 **follow-rpc 的 `baseline-version: 0` 有活体证据**（此前只有源码级）。

⚠️ 但这**只证明 follow-rpc 一家**。其余模块仍需各自验证（`article-rpc` / `favorite-rpc` 缺 `baseline-version: 0` ⇒ 那两条**是 G2/G3 的活**，不是 G1 的）。

### 7.4 第 0 步执行口径（先记，避免「跑绿」被误读）

| 项 | 值 |
|---|---|
| 组件来源 | 全部由本工作树 `mvn package` 产出（**不碰 `Sea-user-api` 容器里的旧镜像**） |
| 隔离 | PG `userprobe-postgres:56432` 的自建库 `g1probe` · Redis `userprobe-redis:57379` · Nacos `nacos-standalone:8848` 的自建 namespace `g1probe` · 端口全在 5xxxx 段 —— **不碰 `8081`/`8889`/`8082`（有别人的进程）** |
| A 组（不设 `NACOS_GROUP`） | ✅ **6/6 组件真启动** · **0 解析失败** |
| B 组（设 `NACOS_GROUP=G1_GROUP`） | ⚠️ 3 rpc 注册到写死 group · `admin-api` 进程退出 · `user-api`/`follow-api` 起得来但运行时解析不到 |
| 证据强度 | **RAG 侧 = L3 活体**（真容器 + 真 Nacos + 真 PG + 真 Redis + 真 HTTP 请求）· **Go 侧 = L1 源码行号** ⇒ **两侧未同时活体对拍 ⇒ 不是 L4** |

### 7.5 ✅ **阶段 2 落码后的真启动 A/B/C 对照**（follow 侧，2026-10-27）

> 同一套隔离环境、同一批进程、唯一变量 = `NACOS_GROUP` 与「下游是否在跑」。

| 组 | 变量 | follow-rpc | follow-api | 结论 |
|---|---|---|---|---|
| **A** | 不设 `NACOS_GROUP` | 5.619s<br>`DEFAULT_GROUP` | 6.794s | ✅ **起得来 · 0 解析失败** ⇒ **默认行为零变化**（注册 group 与修前写死值逐字相同） |
| **B** | `NACOS_GROUP=G1_GROUP`（rpc＋api 全跑） | 6.176s<br>**`G1_GROUP`** | 6.556s | ✅ **起得来 · 0 解析失败** ⇒ 🔴 **`DRIFT-G1-001` 已修**（修前：follow-api 运行时解析不到 ⇒ 业务码塌成 **1015**） |
| **C** | `NACOS_GROUP=G1_GROUP` ＋ **rpc 不起** | — | ❌ **未启动（进程退出）** | 🔴 **`DRIFT-G1-002` 已修**：`Caused by: java.lang.IllegalStateException: Can't connect to channel follow-rpc` ⇒ 对齐 Go 的 `zrpc.MustNewClient` ⇒ panic ⇒ 起不来 |

⇒ 完整 6 组件的 A/B/C 表 + 守门网 + 变异验证 ⇒ 见 [`users/vs-common.md` §7.6~§7.8](../users/vs-common.md)。
⇒ 本域侧的守门类：`FollowRpcNacosGroupTest`（4 例）· `FollowApiStartupFailFastTest`（6 例）。

---

## 8. 🆕 **双侧同时跑的探针**（2026-10-27 · G1 lane 实跑）

> ⚠️ **完整结果见 [`docs/DRIFT/users/vs-common.md` §8](../users/vs-common.md)**（同一轮、同一批命令）。
> ⇒ **本节只记 follow 侧的定位**。

| 探针 | 结果 | 对 follow 域的定位 |
|---|---|---|
| 🟢 **`tools/api-full-parity-probe/follow/run.sh`**（**2026-10-29 落仓** · Round-2 T-2.5 / G-1） | ✅ **EXIT=0** —— **20 条**用例 · **Go 真源 == RAG == 冻结期望值** 三方**逐字节**一致<br>（读路径 9 + 协议层 6 + 写路径 5） | 🟢 **follow 域自己的 HTTP 层**首次有**可复算**的双侧活体证据（此前「52/52」的脚本全在 `/tmp` 且已消失 ⇒ 不可复算）<br>🔴 **顺带修掉 FAILING-010 / PITFALL-FOLLOW-010**：旧探针**固定 `-o` 文件** ⇒ 请求失败时读到**上一次的残留 body** ⇒ 假绿 ⇒ 现为**每请求一个 `mktemp` 唯一文件** + **`http_code == 000` 守卫**（`mutate-parity.sh` **M4/M5** 把假绿**复现**出来）<br>⚠️ **覆盖面小于历史 52 条**：**故障注入族**（黑洞 / 熔断 / 指标 / 保护层四道 / JWT 边界 / 并发竞态）**仍不可复算** ⇒ 「52/52」**降级为历史声明** |
| `tools/semantic-probe/run-parity.sh` | ✅ **EXIT=0**（56 例 · 一致 54 · 不一致 0 · 已签字偏离 2） | ⚠️ 覆盖的是 **message 域 DTO ＋ `users-common` 的必填校验/coercion** ⇒ 对 follow 是**间接旁证**（users-common 侧）<br>⇒ ⚠️ **`follow` 自己的 DTO 解析语义（`FollowGoStyleCoercion` 私有变体）本轮无双侧活体对拍** ⇒ **A4 / A6 / A7 在 follow 侧仍是「台账既有证据强度」** |
| `tools/semantic-probe/rpc-timeout/run-rpc-timeout-parity.sh` | ✅ **EXIT=0**（3/3 一致 · `client_deadline_ctx` 一致） | 🟢 **`client_deadline_ctx` = E38② 第①层的验证用例** ⇒ **① 层已闭环** ⇒ 直接改写了 §1.1 的 `UNRESOLVED-ARPC-002` 状态与 §4 #3 的派单内容（**已向 G4 发订正**） |

⚠️ **follow 域的 A8 轴本轮反而拿到了新证据**（不是探针，是**真启动**）：`baseline-version: 0` 在非空 schema 下真生效 ⇒ 见 §7.3。

⚠️ **本轮探针推翻了我自己的一条派单** ⇒ 教训见 users 侧 §8.3（**「落点」核准 ≠ 「状态」核准；状态的校准器是探针**）。

---

## 9. 🆕 **本轮自我订正记录**（2026-10-27 · G1 lane）

> ⚠️ **本节是台账纪律的一部分**：阶段 1 的九轴表**是我写的**，抽完后逐条回原始台账复核 ⇒ **查出我自己写错的 5 处**。
> ⇒ 与 users 侧 §8.3 同族，但**性质不同**：那次是**状态**抄了过期快照；这次是**台账没读全就下了结论**。

| # | 我原来写的 | 订正为 | 复核依据（读文件实证） |
|---|---|---|---|
| 1 | A6 = 🟡「本轮未逐条复核 ⇒ 记 `UNKNOWN`」 | ✅ **零漂移**（`SEMANTICALLY EQUIVALENT`，16 码 + 16 文案 + 三条转换路径 ＋ 37 契约测试 ＋ 52/52 活体） | `docs/DRIFT/follow/common.md`（**3,921 B，非空**）· 头部「核对日期：2026-10-24」· 结论段「`SEMANTICALLY EQUIVALENT` —— 零漂移」 |
| 2 | A9 = 🟡「1 条 OPEN ＋ 1 条 ✅」（把 `DRIFT-FOLLOW-006/-007` 当 A9 本体） | 🟡 **域侧零覆盖（记账缺口）**；`-007` = **A9 邻接**（Prometheus 文本层）· `-006` = **A9 邻接 + A5 邻接** | `grep -i "otel\|traceparent\|audit\." docs/DRIFT/follow/` ⇒ **零命中**（只有本文件命中）⇒ A9 本体（`audit.*` ＋ `Traceparent`）在本域**没有台账条目** |
| 3 | A3 里挂了 `DRIFT-FOLLOW-001` | `DRIFT-FOLLOW-001` = **A7/A4 邻接**（wire 级出参 `null` vs `[]`），**不属于 A3** | `api.md:17`「空列表出参 Go=`"user_ids":null` / RAG=`"user_ids":[]`」⇒ wire 级 ⇒ A7/A4 |
| 4 | A5「🟡 rpc 端口不可配 ⇒ **配置面收窄**」 | **两侧同为 yaml 写死 ⇒ 语义等价**（不是收窄） | Go `service/follow/rpc/etc/follow.yaml:2` `ListenOn: 0.0.0.0:8082` ↔ RAG `follow-rpc/application.yml:103` `port: 8082`；api 侧 Go `followcenter.yaml:3` `Port: 8891` ↔ RAG `${FOLLOW_API_PORT:8891}`（同默认值 + 额外 env 覆盖） |
| 5 | A2 只列两条**启动阻塞型**缺陷 ⇒ 写「✅ 一致」 | 补 ③ **服务端拦截器链**（`FollowRpcApplication.java:118` 挂 `GoStyleRpcProtectionChain.build(...)`，**有组件级守门网** `FollowProtectionChainWiringTest`）⇒ ✅ 但**域台账 `rpc.md` 零记 `DRIFT-MSG-V-026`** ⇒ **记账缺口** | 读 `FollowRpcApplication.java:68-119` ＋ `FollowProtectionChainWiringTest.java:32-66` |

### 9.1 域台账的**行号 / 端口漂移**（同批订正，G1 写权内 `docs/DRIFT/follow/**`）

| 文件:行 | 原引用 | 实测 | 动作 |
|---|---|---|---|
| `rpc.md:92` | `application.yml:81` | `application.yml:123`（`address: discovery:///user-rpc`） | ✅ 已订正 |
| `rpc.md:135` | `application.yml:65` | `application.yml:95`（`server-addr: ${NACOS_ADDR:nacos}:${NACOS_PORT:8848}`） | ✅ 已订正 |
| `rpc.md:176` | `application.yml:59` | `application.yml:79`（`table: flyway_schema_history_follow`） | ✅ 已订正 |
| `api.md:351` | Go api DevServer `:16065/metrics` | **16064**（真源 `service/follow/api/etc/followcenter.yaml:28`；**16065 是 rpc 侧** —— `service/follow/rpc/etc/follow.yaml:23`） | ✅ 已订正（并注明 rpc 侧那处是对的） |

### 9.2 🆕 本轮新登记 ⇒ **派 G4**：`PITFALL-FOLLOW-005`

> ⚠️ **2026-10-29 真实性复核**：原判 **「🔴 未修 ⇒ 派 G4」** ⇒ **现行态 = ✅ 已闭环（共享层已于 2026-10-27 落码）** ⇒ ⇒ **不再需要派 G4**。
> **依据（当场核过）**：共享层 `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:53-54` = `@ConditionalOnWebApplication(type = SERVLET)` + `class HttpPoolLifecycle`（**只有 servlet 进程才注册这个 Bean**）＋ `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:85`（「本方法**只碰** `RpcInterceptor`」）＋ `:94`（「HTTP 池改由 `HttpPoolLifecycle` 关 ⇒ **不在本方法里**」）⇒ ⇒ **关闭路径已不再触及 `jakarta.servlet.Filter`**。
> **指针订正**：下方正文引 `ObservabilityLifecycle.java:54`（原指「destroy 路径触及 web 类型」）⇒ **该行现在是讲这个根因的 javadoc**（行号合法但语义已变）⇒ 当前判据应读 `:85` / `:94`。
> **域侧前提也已变**：`service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web`（2026-10-29 补）⇒ ⇒ **follow-rpc 运行时 classpath 现在有 servlet 容器** ⇒ 原「无 servlet-api」的前提**对本域也不再成立**。
> ⇒ **原判是 2026-10-27 落码前的陈旧快照**；**共享层写权仍归 G4**，本 lane 只登记不落码（本次亦未落码）。

域台账 `docs/Pitfalls/follow/rpc.md` 记着一条 🔴 未修：**共享 `service/common` 在纯 gRPC 进程里缺 servlet-api** ⇒ 关闭路径抛 `NoClassDefFoundError: jakarta/servlet/Filter`。
**本轮复核根因链已读通**：`HttpMiddleware.java:8` `import jakarta.servlet.Filter` ⇒ `ObservabilityLifecycle.java:54` `HttpMiddleware.shutdown()` ⇒ **该类在纯 gRPC 进程里的首次加载恰好落在 destroy 路径** ⇒ 落点 **`service/common`** ⇒ **不在 G1 写权** ⇒ **派 G4**（§4 #2）。

### 9.3 共享索引过时（**无人拥有 ⇒ 交合并收口执行者**，G1 不写）

`AGENTS.md:32` 与 `docs/DRIFT/_INDEX.md:32,85` 仍写 follow「**零台账 / 仅 proto 层**」⇒ **与事实不符**（follow 域 4 个组件均已完成组件级对照，9 条 DRIFT + 15 条 PITFALL + 逐文件索引，见 `docs/DRIFT/follow/{api,rpc,common,proto}.md`）。
⇒ **共享面文件**（`AGENTS.md` / `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）**G1 一律不写** ⇒ 登记在此，**合并后由合并收口执行者统一刷新**（`docs/DRIFT/follow/rpc.md` 待办 6 早已记同一件事）。

### 9.4 元教训

**「台账文件非空」≠「台账有状态日期」** ⇒ 我第一版 A6 写「`common.md` 无状态日期 ⇒ 记 UNKNOWN」，
**但那份文件有 3,921 B、有明确核对日期 2026-10-24、有 9 行对照表** ⇒
⇒ **我判 UNKNOWN 的依据（文件小/内容少）本身是错的** ⇒ **读之前先看大小和结论段**，
⇒ **否则会把「已对齐」误记成「未知」，凭空多出一条假待办**。
（同族：users 侧 §8.3「状态的校准器是探针，不是台账」⇒ 本条是**结论强度的校准器也是台账本身**。）

---

## 10. 🆕 **待办依赖：MP 排序桥下沉共享层**（2026-10-27 收到 G3 预告 · **已独立核实**）

> ⚠️ **本节是「登记」，不是派单、也不是动作** ⇒ G3 明确说「只是预告」⇒ 本域**不动手**。

| 项 | 本域实测 | 结论 |
|---|---|---|
| `follow-rpc` 的域侧排序桥 | `service/follow/follow-rpc/.../config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ `META-INF/spring/…AutoConfiguration.imports` 1 行注册 | ✅ 存在，**落点在 G1 写权内** |
| 全仓份数 | `find service -name "MybatisPlusOrderingBridgeAutoConfiguration.java"` ⇒ **9 份** | ✅ 与 G3 的数字一致 |
| 共享层 | 零命中 ⇒ **没有**桥 | ✅ 属实 ⇒ 桥是全域共性却落在域侧（§5.3f：判据 = 依赖度数） |
| 排序边方向 | 域侧桥 = `after=DataSourceAutoConfiguration.class`（**Boot 4 新包名** `org.springframework.boot.jdbc.autoconfigure.*`）`before=MybatisPlusAutoConfiguration.class` | ✅ 与共享层将要声明的边**同向** ⇒ 并存等价、无环 ⇒ **不需要跨树同一次提交** |

🔴 **前置依赖**：**必须等 G4 的共享层桥落码之后才能删** ⇒ 否则 MP 自动配置退避
⇒ 不产 `SqlSessionFactory` ⇒ **启动失败** ⇒ 合并顺序：**先 G4 共享层 ⇒ 再各域删**。

⇒ 删除清单 + 落码后验证口径（真 Spring 上下文守门 ⇒ 删桥后 `SqlSessionFactory` 仍在 ＋
`DeadlineQueryTimeoutInjector` 真在执行链上）⇒ 见 [`users/vs-common.md` §10](../users/vs-common.md)。

### 10.1 🆕 **本域台账里的桥引用**（删文件时必须同批订正）

| 台账位置 | 引用形态 | 动作 |
|---|---|---|
| `docs/DRIFT/follow/rpc.md:346` | **逐文件对照索引**的**第 5 行**（`rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`） | 🔴 **必改** ⇒ 删行（或改指共享层的同一机制） |
| `docs/DRIFT/follow/rpc.md:323` / `:337` | 「逐条对照此前无法核对」段里**列举的 10 个类名**之一 | 🟡 改（少一个类 ⇒「10 个类」要改成 9） |

⚠️ 桥文件**自身 javadoc** 的「什么时候可以删」与那段**过期的「同款」列举**（写 5 个、实际 9 个）
⇒ **随文件删除即消失** ⇒ 只需确认没有别的文件引用该文案（已 grep：`docs/DRIFT/{users,follow}/**` 内除上述 3 处外无其他引用）。

### 10.2 🟢 **订正（2026-10-27 · G1 lane · 收编已落码）**：§10.3/§10.4 的「本轮不动手」**已被后续事实取代**

> ⚠️ **上表两行的处置动作已完成**（本节记录最终状态，**不改写 §10 的历史陈述**）：
> - `docs/DRIFT/follow/rpc.md` 的**逐文件对照索引**：原第 5 行**已删** ⇒ 原 #6~#21 顺次前移为 **#5~#20**
>   ⇒ Java 侧 **21 → 20** 个文件 ⇒ **Go 侧计数不变**（删掉的那行本来就没有 Go 对应物 ⇒ 仍是 17 + 5 = 22）。
>   连带改了 :368 的跨文档交叉引用（「不是本表的 #6」⇒ 现明确写出本表 #6 = `rpc/domain/BlockRelationEntity.java`）
>   与 :377 的「行 #1–#21」→「行 #1–#20」。
> - `docs/DRIFT/follow/rpc.md:323` / `:337` 的「**10 个类**」：**按 §6.3「不重写历史」保留原文**
>   （那是 2026-10-24 的历史陈述，当时该类**确实存在**）⇒ 改为**加订正注记**（见 `rpc.md` 索引表上方）。
>
> ⚠️ **§10.4「本轮不动手」的三条理由现已全部失效**：
> ① G3 的「只是预告」已变成 G4 的正式落码（共享层那份**已随 `b7faeb4` 合入本树**）；
> ② **前置依赖已满足**（共享层那份在树 ⇒ `service/common` 的 `AutoConfiguration.imports` 里有它
> ⇒ 而本模块 `compile` 依赖 `com.rag:common` ⇒ 该资源在本模块 classpath 上）；
> ③ 「并存无害所以不急」是**当时**的判断 ⇒ 现在**共享层那份已改用字符串形式**
> （`afterName`/`beforeName` ⇒ 对「没有 MyBatis-Plus」的消费者结构性安全）⇒
> **域内那份（类字面量形式）已成待清理项** ⇒ 收编是**必要的**，不是可选的。
>
> ⇒ 完整落码过程 / 守门 / 变异 / 回归 ⇒ [`users/vs-common.md` §28](../users/vs-common.md)。

---

## 11. 🆕 **Redis 收编的「删除前置」静态核验 ＋ A2 轴关闭路径暴露面**（2026-10-27 · G1 lane）

> **本节是「核验 + 登记」，不是落码** ⇒ 共享层那两份（`1fb26b2` ＋ `2f12236`）**还没合进本树**。
> 逐条证据与 users 侧的完整推导 ⇒ [`users/vs-common.md` §12](../users/vs-common.md)；
> 本节只记 **follow 侧**的部分。

### 11.1 本域的删除前置 —— **3 项核验成立**（follow 侧只涉及 1 份副本）

| # | 前置条件 | follow 侧实测 | 判定 |
|---|---|---|---|
| ① | 自动配置能被装配 | `FollowApiApplication.java:68` 带 `@SpringBootApplication` ⇒ 含 `@EnableAutoConfiguration` | ✅ |
| ② | 自动配置清单在 classpath 上 | `follow-api` pom 依赖 `com.rag:common`（默认 compile scope） | ✅ |
| ③ | 值逐字同值 ＋ 只设断连行为 | `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/config/RedisDisconnectedBehaviorConfig.java:111-112` 只调 `disconnectedBehavior(REJECT_COMMANDS)`，**零** `timeoutOptions` 触碰（⚠️ **该域内副本已于 Redis 收编轮【删除】** ⇒ 本行是**收编前的核验记录**） | ✅ |

⇒ **follow 侧要删的只有 1 份**：`follow-api` 的 `config/RedisDisconnectedBehaviorConfig.java`。

⚠️ **`follow-rpc` 不在删除清单里**：它**不用 Redis**
（`service/follow/follow-rpc/pom.xml:26` 明写「任何 redis/kafka starter 在本模块都属于『新增 Go 侧不存在的能力』，一律不引」）
⇒ **无副本 ⇒ 无删除动作**。

### 11.2 🔴 **订正（2026-10-27 · 收编落码时实测）**：`FollowApiRedisDisconnectedFastFailTest` **不是**「显式 `@Import` 域内副本」⇒ 「副本一删就编译不过」**在本域不成立**

> **原写（本节落笔时的前提）**：「`FollowApiRedisDisconnectedFastFailTest` 是『显式 `@Import` 域内副本』⇒ 副本一删就编译不过
> ⇒ 收编 = 删 1 份副本 **＋ 重写这 1 份守门的装配方式**（漏了后半段 ⇒ 编译红）」。
>
> 🔴 **实测推翻**：该守门是 `@SpringBootTest(classes = FollowApiApplication.class)`
> ⇒ ⇒ **它<b>没有</b>任何 `@Import`**（实测：`grep "@Import"` **零命中**）
> ⇒ ⇒ 域内副本是**被组件扫描**装进去的（`FollowApiApplication` 扫 `com.rag.follow.follow.api` + `com.rag.common`）
> ⇒ ⇒ **删掉副本不会造成编译断裂**（编译断裂只发生在**显式 `@Import` 一个已删类**的地方）。
> ⇒ ⇒ **本域（follow-api）真正需要改的只有第 2 条（补 bean 身份断言）**。
> ⇒ ⚠️ **「副本一删就编译不过」在 users 域的 rpc 侧成立**（`UserRpcRedisDisconnectedFastFailTest` /
> `AdminRpcRedisDisconnectedFastFailTest` 确实 `@Import` 了域内副本）⇒ ⇒ **我把 rpc 侧的结构套到了 api 侧**
> ⇒ ⇒ **教训**：**同一机制在不同组件上的装配形态可以不同** ⇒ 逐条实测，别拿一处的结构推另一处。

⇒ **本域收编落码的实际动作（2026-10-27 · 已实施）**：

1. **删除** `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/config/RedisDisconnectedBehaviorConfig.java`；
2. **补「收编真的发生」的两条断言**（`FollowApiRedisDisconnectedFastFailTest`）：
   - `context.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` ⇒ **size == 1**；
   - 该 map 的 key 含 **`goStyleRedisClientOptionsCustomizer`**（共享层那份的固定 bean 名）。
   ⇒ ⚠️ **api 侧不需要改任何注解** —— 但**必须核实「清单真的生效」**，判据三条：
   ① 启动类含 `@EnableAutoConfiguration`（`@SpringBootApplication` 自带）；
   ② **无人重写** `AutoConfigurationExcludeFilter` / `TypeExcludeFilter`（实测：`follow-api` **零命中**）；
   ③ yaml 里**无** `spring.autoconfigure.exclude`（实测：**零命中**）。

⚠️ **为什么必须补第 2 条**：`@ConditionalOnMissingBean` 是「**遮蔽**不是**叠加**」⇒ 域内副本还在时共享层那份**根本不创建**
⇒ 此时「`REJECT_COMMANDS` 生效」**是真的**、但「**收编发生了**」**是假的**
⇒ ⇒ **只断言行为值 ⇒ 收编没做也绿** ⇒ 必须断言 **bean 身份**。

### 11.3 A2 轴：关闭路径不得触发 web 类型类加载 ⇒ **`follow-rpc` 暴露**（`PITFALL-FOLLOW-005`）

**缺陷**（G4 `3b573fb` 修的那条）：`ObservabilityLifecycle.shutdown()` 第一句是 `HttpMiddleware.shutdown()`
⇒ 纯 gRPC 进程 classpath 无 servlet ⇒ 炸 `NoClassDefFoundError: jakarta/servlet/Filter`
⇒ 该方法**不吞异常** ⇒ **`RpcInterceptor.shutdown()` 根本不执行** ⇒ **RPC 池没被回收**。

**follow 侧暴露面（实测，非转述）**：

| 组件 | `@Import ObservabilityLifecycle` | 运行时 classpath 有 servlet 吗 | 暴露 |
|---|---|---|---|
| `follow-rpc` | `FollowRpcApplication.java:77` | ❌ 无（剥掉 pom 注释后逐条查：**零** web/tomcat/servlet 依赖，只有 `spring-boot-web-server`；而 `spring-boot-web-server`（Boot 4.0.8）的 pom 里 **servlet 出现次数 = 0**） | 🔴 暴露 |

⇒ **合入后 follow 侧必查（2 条）**：
1. `follow-rpc` 关闭路径**不再抛** `NoClassDefFoundError`；
2. `RpcInterceptor` 的池**真的被回收**（否则缺陷只是「不再污染日志」，**池泄漏仍在**）。

⚠️ **附带（改 `follow-api` 侧的连带面）**：`follow-api` 是 servlet 应用 ⇒ 合入后应**仍注册** `HttpPoolLifecycle`
⇒ HTTP 池仍被回收 ⇒ **零行为变化**。

### 11.4 🔴 **合并顺序约束**：`3b573fb` 与 `7488b3d` **必须同批合入**（否则本域 `follow-rpc` 由绿转红）

> 已发给 G4（待回复）＋ G2 ＋ G3 ⇒ 登记在此以免只在消息里。完整推导 ⇒ [`users/vs-common.md` §12.4](../users/vs-common.md)。

**follow 侧暴露面（实测，非转述）**：

| 组件 | `@SpringBootTest` 上下文数（surefire 一个模块一个 JVM） | 同款断言 | 暴露 |
|---|---|---|---|
| `follow-rpc` | **5** | `FollowGrpcInterceptorWiringTest:102` `serverSideTimeoutIsActuallyEnforced` ⇒ 断言 `DEADLINE_EXCEEDED` | 🔴 暴露 |

⇒ **方向不对称**：`7488b3d` 单独合入 ⇒ 池可重建 ⇒ 测试仍绿；
`3b573fb` 单独合入 ⇒ 池「终止即永久」⇒ **会红** ⇒ ⇒ **顺序：先 `7488b3d`、后 `3b573fb`，或同批**。

⚠️ **本域此刻仍绿，是「绿是因为没走到」**（修 #8 之前 shutdown 第一句就抛异常 ⇒ 池侥幸存活）
⇒ **不能**把当前的全绿读作「本域不受这条影响」。

**证据强度**：**L3**（本域静态清点）＋ G4 侧 article-rpc 的 **L4**。

### 11.5 🆕 **C7 预检：G4 共享层变更对 follow 域的影响面**（2026-10-27 · G1 lane · 合入前先算清）

> 完整表在 users 侧 ⇒ [`users/vs-common.md` §13](../users/vs-common.md)。此处只记 follow 域的部分。

| G4 提交 | 对 follow 域的影响 | 本域动作 |
|---|---|---|
| `182fc32` | ✅ **零影响**：语义表写着 `service/follow/follow-api` ⇒ **ENVELOPE**，本域 yaml 实值 = `mode: envelope`（`follow-api/application.yml:202`）⇒ 逐条一致；`GoStyleTomcatParameterLimitCustomizer` 的 diff **只动注释**；保护链改动是**只读入口**、**零行为变化** | ✅ **无** |
| `1fb26b2` ＋ `2f12236` | 🟡 **有影响**：域内副本会**遮蔽**共享层那份 ⇒ 收编要**删 1 份副本（follow-api）＋ 重写 1 份守门** | 🔴 **待 G4 合入后执行**（清单见 §11.2） |
| `3b573fb` ＋ `7488b3d` | 🔴 **有影响**：`follow-rpc` `@Import ObservabilityLifecycle` 且 classpath **无 servlet** ⇒ 暴露；且两条**必须同批**否则由绿转红 | ✅ 合入后按 §11.3 / §11.4 复验 |

⇒ ⇒ **`182fc32` 对 follow 域零影响** ⇒ **合入后不需要改任何配置**。
⇒ ⚠️ **但它新增了一条对本域有约束的共享层判据**：`SEMANTIC_REACHABILITY` 把「Go 侧该域是否真的解析 form/query」写成**逐域取证表**
⇒ ⇒ **若将来 Go 侧 follow 的 handler 改成解析 form/query，本域的 `mode: envelope` 必须跟着改** ⇒ **复开触发条件已锁定**。

### 11.6 🆕 **守门卫生缺陷：「可达 ⇒ 能启动」用例的 3s 预算过紧 ⇒ 偶发红**（2026-10-27 · G1 lane）

> 完整推导（三步定位 + 元教训）在 users 侧 ⇒ [`users/vs-common.md` §14](../users/vs-common.md)。此处只记 follow 侧。

**本域同款缺陷**：`service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiStartupFailFastTest.java`
的 **2 处「可达 ⇒ 能启动」用例**同样把 `grpc.client.follow-rpc.immediate-connect-timeout` 设成 **3s**（= 生产值）
⇒ 冷 JVM ＋ 满负载下 TCP+HTTP/2 握手可能超 3s ⇒ **偶发红**（root cause = `Can't connect to channel`）。

**已修**：那 **2 处**放宽到 **`10s`**；**2 处「不可达 ⇒ 启动失败」用例保持 `3s` 不动**（那才是被测语义，且 TCP refused 是立即返回）
⇒ **生产值 3s 仍由 `applicationYmlArmsImmediateConnectTimeout`（读 `application.yml` 的静态断言）守着** ⇒ **无覆盖损失**。

⚠️ **同批同款修的还有**（users 域）：`UserApiStartupFailFastTest` ＋ `AdminApiStartupFailFastTest` 各 2 处。

### 11.7 🆕 **G4 第七次回执（2026-10-27）** —— follow 域相关三条

> 完整记录在 users 侧 ⇒ [`users/vs-common.md` §16](../users/vs-common.md)。

① **A2 邻接（出向调用面）**：`follow-rpc` 的 `@GrpcClient` 计数 = **1**（**可观测**）⇒ 而 `user-rpc` / `admin-rpc` = 0（N/A）⇒
⇒ ⚠️ 这是「出向调用面」的口径，**≠ A2 本体**（A2 = 服务端三拦截器链）⇒ ⇒ **记作 A2 邻接，不并进 A2 本体的差异计数**。

② **Redis 收编的删除清单 = 5 份（G4 回执写「3 份」是扩容前的名单）** ⇒ **follow 域那 1 份在清单内**
（`service/follow/follow-api/.../config/RedisDisconnectedBehaviorConfig.java`）；
⇒ ⚠️ **`follow-rpc` 不在清单里**（它**不用 Redis** ⇒ 无副本 ⇒ 无删除动作）。
⇒ 🔴 **删除的前置未满足**：G4 的 `5564c27` / tip `cec7131` **还没进 `do-my-own-gh`** ⇒ 本树里没有共享层那份
⇒ ⇒ **现在删 = `REJECT_COMMANDS` 整个消失**（api 侧退回「排队 3s ⇒ 撞 HTTP 3s ⇒ 503」，Go 是 fail-open 200）
⇒ ⇒ **我域 FastFail 守门会立刻变红** ⇒ ⇒ **等合并收口执行者把 G4 分支合进主干后一次性执行**（配方见 §11.2）。

③ **G4 ⑥（池自愈）与我域登记同构**：`follow-rpc` 与另两个 rpc 一样 `@Import ObservabilityLifecycle` 且 classpath **无 servlet**
⇒ ⇒ **受影响面 = 3/3** ⇒ ⇒ `3b573fb` 与 `7488b3d` **必须同批合入**（只合前者 ⇒ 池「终止即永久」⇒ 我域由绿转红）⇒ 详见 §11.3 / §11.4。

### 11.8 🆕 **G3 第八次回执 ＋ G4 第八次全量口径**（2026-10-27 · follow 域相关部分）

> 完整记录在 users 侧 ⇒ [`users/vs-common.md` §18](../users/vs-common.md)。本节只留 **follow 域那半**。

① 🔴 **「G3 的 29 模块回归」是口径串台** ⇒ G3 的域级回归 = **12 模块**（4 域 × 3 组件）⇒ **「29」= G4 第八次全量的「有测试模块数」**
⇒ ⇒ 本域两处台账（[`users/admin/rpc.md` §12.6](../users/admin/rpc.md) · [`users/user/rpc.md` §9](../users/user/rpc.md)）**已同批订正**（保留原值 + 加注记）⇒ ⇒ **错峰结论不变**（只订正数字）。

② 🔴 **G4 第八次全量里 follow 域的两个组件是旧基线** ⇒ `follow-api` **50**（我树 `fa608f3` 实测 **68**）· `follow-rpc` **74**（我树实测 **78**）
⇒ ⇒ 根因 = **G4 的树不含 G1 的任何提交**（`fa608f3` 等 7 笔全 `NOT_IN_G4`）⇒ ⇒ **那份全量不能当 follow 域的回归证据** ⇒ ⇒ 收口回归（C7）必须在**所有 lane 合入主干后**跑 ⇒ 详见 §11.5 与 [`users/vs-common.md` §13.3](../users/vs-common.md)。

③ 🔴 **删除前置仍未满足（复检）**：本树 `service/common/…/AutoConfiguration.imports` 仍**只有 2 条**（`GoStyleOtelAutoConfiguration` · `GoStyleJsonContentTypeAdvice`）⇒ **无 `GoStyleRedisClientAutoConfiguration`** ⇒ ⇒ **follow 域那 1 份副本继续保留** ⇒ ⇒ 现在删 = `REJECT_COMMANDS` 消失 ⇒ 等 G4 分支进主干后**同批**执行「删副本 + 重写 `FollowApiRedisDisconnectedFastFailTest` 的装配方式」。

④ ✅ **G3 侧「0 份域内副本 ⇒ 收编零动作」我认可** ⇒ ⇒ **收编在 G3 域零动作、在 G1 域 5 份** ⇒ ⇒ **两边都要在合并时各自复验「共享层那份真的生效」**（`@ConditionalOnMissingBean` 是**遮蔽**不是叠加）。

⑤ ⚠️ **G3 树当前有 2 个红报告**（`article-rpc` 13:07:25 / `comment-rpc` 13:08:29 的 `realFatJarProcessBootsAndListens`）⇒ 是**就绪探测 flake** 族 ⇒ ⇒ **与我域那款（连接预算）不同源** ⇒ ⇒ follow 域的 `FollowApiStartupFailFastTest` **不要照搬** G3 的修法（我域已用「放宽到 10s」修完，见 §11.6）。

### 11.9 🆕 **G4 槽位协调回执（2026-10-27 · follow 域相关）** —— 补 +2 槽位 ＋ 一处**我的推断被推翻**

> 完整记录在 users 侧 ⇒ [`users/vs-common.md` §19](../users/vs-common.md)。

① ✅ **槽位表已补 +2 行**（[`api.md` §11.2](api.md)）：G4 独立核实共享层在**它分支上是 7 个 Filter**（**+2 `GoStylePathNormalizingFilter`**，由 `9690b04` 引入）⇒ ⇒ **该文件不在我树**（`9690b04` 不在 `do-my-own-gh`）⇒ ⇒ **「6 vs 7」是代码态差，不是本域漏数** ⇒ ⇒ **合入主干后本域的重排清单必须把 +2 算进去**。
⇒ ⇒ ✅ **本域「`GoStyleJwtAuthFilter` 落 +6（预算起点 +7 之外）」的推导不受影响**（+2 在区间外）⇒ ⇒ **本域零改动**。

② ✅ **G4 ③「+7 = 3s 预算起点」两侧独立复核成立**：`begin`/`clear` 都在 `HttpMiddlewareFilter` 链内（我树 `HttpMiddleware.java:304`/`:336`；G4 树 `:324`/`:356` ⇒ **跨树行号差 20 行 ⇒ 引他树行号必须带 commit id**）。

③ ✅ **G4 本轮「零生产代码改动 + 只加 1 个测试文件」⇒ 共享层 Filter 顺序未被动 ⇒ 本域槽位约束未被打破** ⇒ ⇒ **本域不需要任何改动**。

④ 🔴 **订正我上一轮的一条推断**：我曾写「G4 此刻正在跑第十二次全量」⇒ **不成立**（G4 回执 ⑥ = 本轮**未跑全量**，跑的是模块级 `mvn -o -pl service/common test` 315 条）⇒ ⇒ **我把「`ps` 显示在跑 maven」当成了「在跑全量」** ⇒ ⇒ **模块级与全量级在 `ps` 里长得一样** ⇒ ⇒ **动态状态只写「截至 <时间戳> 尚未发生」**。

⑤ 🟡 **V-002 与 follow 域无关**（落点 `users-common`）⇒ 本域无动作；但**同一条回执里 G4 说「等 G1 的 #6 第 1 步（让 `status` 能表达 NULL）」⇒ ⇒ 该步已被我域逐层证据链推翻 ⇒ ⇒ 已在 users 侧订正并回复 G4「取消」**。

### 11.10 🟢 **U+FFFD：follow 域源码零命中**（2026-10-27 · 全仓字节级扫描）

⇒ 四棵树（主干 / G1 / G2 / G3）全仓字节级扫描 ⇒ **`service/follow/**` 在【每一棵】树里都是 0 命中** ⇒ ⇒ **follow 域源码零坏字符** ⇒ ⇒ **本域在这件事上零动作**。
⇒ ⇒ ⚠️ **但本域台账里有一处**（`docs/DRIFT/follow/**` 本身）—— 属**我域写权面** ⇒ 本轮已随 users 侧 §20 一并核对：**我域台账里的 U+FFFD 全是「故意引述」**（引用被测对象 / 记录不可恢复的末字）⇒ ⇒ **不动**。
⇒ ⇒ 📌 **合并取版本的清单**（G1 贡献 3 份）见 [`users/vs-common.md` §20.2](../users/vs-common.md) ⇒ ⇒ **G2 的清单只覆盖它自己的 3 份 ⇒ 我域那 3 份此前不在任何人的清单里 ⇒ 本轮主动补进**（否则合并按主干取 ⇒ 我域坏字符回流）。
⇒ ⇒ ⚠️ **判据纪律**：复扫要比「**文件清单**」而不是「**命中份数**」—— 实测我树与主干的命中份数**都是 20**，但构成完全不同（我域 3 处真损坏已清零、另 3 份台账的故意引述却只在我树有）⇒ ⇒ **份数相同 ≠ 同一批文件**。

### 11.11 🟢 **路径限定规则 + 池形态 + 口径边界（2026-10-27 · G2 提醒）** —— follow 域那 26 处已一并落码

> 完整记录在 users 侧 ⇒ [`users/vs-common.md` §23~§25](../users/vs-common.md)。

① ✅ **路径限定已完成**：follow 域台账里 **26 处**裸写的 go-zero 库内路径已改成 **`go-zero@v1.10.2/<path>`**（`follow/api.md` 18 · `follow/rpc.md` 4 · `follow/vs-common.md` 4）⇒ ⇒ **判据** = go-zero 顶层目录 = `core`/`gateway`/`internal`/`mcp`/`rest`/`zrpc`，而**真源仓顶层只有 `service/`** ⇒ ⇒ 凡不以 `service/` 开头的这些路径 ⇒ **必是库内路径**。
⇒ ⇒ **刻意不动**：真源仓的 `internal/...`（组件内路径 · 语境已含组件）· Go 标准库 `encoding/json` · `breaker/latch`（概念描述，非路径）⇒ ⇒ **三类判据已写进 users 侧 §23.3，防后人「顺手统一」误改**。

② 🟢 **池形态自检**：`follow-api` **零 `min-idle`** ⇒ ⇒ G2 的「`min-idle` 应取 go-zero 的 8」**在 follow 域不适用**；**零 `pool.enabled`** ＋ **pom 里零 `commons-pool2`** ⇒ ⇒ **池形态 = 无池** ⇒ ⇒ **不会**触发「`enabled: true` + 缺 commons-pool2 ⇒ 启动期 `NoClassDefFoundError`」⇒ ⇒ **零动作**。
⇒ ⇒ 📌 **将来若给 follow-api 加 `min-idle`，必须同时查 pom 的 `commons-pool2`**（Boot 4 的 `Pool.enabled` 由 classpath 决定 ⇒ 加/减一个依赖 ⇒ 池形态**静默变化**）。
⇒ ⇒ 🟢 **补一条轴（G3 的「池耗尽等待上限」在 follow 域的复检）**：Go 侧**池耗尽可达**（go-redis v9 `internal/pool/pool.go:421` 信号量容量 = `PoolSize` ⇒ 上限 = `PoolTimeout` = **4s**），
而 **follow 域 RAG 侧无池 ⇒ 该状态不可达**（唯一上限是命令超时 **3s**）⇒ ⇒ **两侧不对称 ⇒ 饱和路径可观察**（follow-api 每请求一次 `hasKey` ⇒ 正是触发面）
⇒ ⇒ **修法是共享层形态决策（加池）⇒ 落点 `global` 收口域 ⇒ follow 域零落码** ⇒ ⇒ 完整对照与出处 ⇒ [`users/vs-common.md` §33](../users/vs-common.md)。

③ 🟢 **口径边界**：follow 域**零产物层 IT** ⇒ ⇒ **`mvn test` 口径 == 全量口径** ⇒ ⇒ 本域 68 条**无需**加「不含 IT」的边界说明 ⇒ ⇒ 且已按「maven 模块汇总行 == surefire XML 逐文件求和」双口径相等采信 ✅。

### 11.12 🔴 **G3「端口转发对多数容器失效」来信的复检 ⇒ follow 域零影响，但钉口落码项已就位（2026-10-27 22:2x）**

**① 来信与复检**：G3 报本机 Docker「宿主→容器」转发对多数容器失效 ⇒ 我域**自己重扫**（不采信转述）：
**43 个映射口逐个发协议正确请求 ⇒ 43/43 全部有协议级应答**（PG 18/18 `b'N'` · Redis 8/8 `+PONG` · HTTP 4/4 状态行 · Kafka 3/3 合法 `ApiVersions` 响应 · etcd/nacos/beanstalkd/milvus/neo4j/jaeger 9/9 · gRPC 1/1）⇒ ⇒ **转发完好** ⇒ ⇒ **「机器级阻塞」不成立 ⇒ 不需要重启 Docker**（重启会干掉 47 个容器，各 lane 跑了 26~45 小时）。
⇒ ⇒ **G3 那条推断的断点 = 判据未按协议分流**：「裸连读不到字节」被当成「路径不通」，而 PG/Redis/Kafka/Nacos/etcd/beanstalkd **全是请求-响应型服务，本就不主动发第一个字节** ⇒ 我用同款裸连判据**复现出 10 个假阳性** ⇒ ⇒ ⇒ **「连上了但没数据」有三种互斥解释（服务不主动说话 / 探针地址错 / 我发的字节不合法），三者齐排才能说「路径不通」**。
⇒ ⇒ 完整取证与判据 ⇒ [`users/vs-common.md` §37](../users/vs-common.md)。

**② follow 域的连带影响 = 零**：follow 域的 Testcontainers 只连**自己起的容器**（`localhost:<getMappedPort>`），从不连共享的 `difftest5k-*` ⇒ ⇒ **转发坏不掉 follow 域的用例** ⇒ ⇒ **零动作**。

**③ 🟡 follow 域钉口落码项已就位（G4 §4.10.39 分配 `22730–22759`）**：
follow 域 **5 个测试文件**用 Testcontainers（`follow-api` 1 个 Redis `GenericContainer` · `follow-rpc` 4 个 `PostgreSQLContainer`）
⇒ ⇒ 宿主口由 Docker 随机分配 ⇒ ⇒ **落在 OS 临时段 `49152–65535`** ⇒ ⇒ 而该段当前被占 **205/16384**（`ESTABLISHED` 出站源口 106 · `LISTEN` 56 · `SYN_SENT` 21）⇒ ⇒ **撞口面真实存在**（约 1.25%/次）⇒ ⇒ 症状 = §34 记的 `EOFException@enableSSL`（端口上不是预期的服务）⇒ ⇒ **钉口 = 从根上消除这一类随机失败**。
⇒ ⇒ ⚠️ **形态已被 G4 定死**：`PostgreSQLContainer` + `withCreateContainerCmdModifier(...)` 显式绑口（**不能**换成 `FixedHostPortGenericContainer`——它不带 PG 初始化/JDBC 支持）⇒ ⇒ **G4 要求落码第 0 步先写探针验证「显式绑口后 `getMappedPort()` 是否返回那个固定口」** ⇒ ⇒ **不能照抄**。

### 11.13 🟢 **G4 §4.10.27 的 R3 复核（follow 域零动作 · 附根因）＋ 域侧 Filter 槽位守门在 follow-api 落码**（2026-10-27 · G1 lane）

**① R3 复核：follow 域【已修】⇒ 零动作**。
`follow-api` 的 Redis 段现态 = **`timeout: 3s` ＋ `connect-timeout: 5s`**（两个值都在 yml 里，且都带出处注释）⇒ ⇒ **不是** G4 §4.10.27.2 表里写的「命令 2s ＋ 连接 10s」。
⇒ ⇒ R3 里那个「需 G1 自行判定是『刻意收紧』还是『抄错』」的开放项，**follow 域已判定 = 不是刻意**（§27.2 的交叉验证：Go 侧生效的是 **go-redis v9 库默认 `ReadTimeout = 3s`**，真源 `go.mod` pin `go-redis/v9 v9.20.0` ⇒ `options.go:364-370`；那个 2s 疑似抄自 go-zero `core/stores/redis/redis.go:27` 的 `readWriteTimeout = 2 * time.Second`，而它是**死常量**（全文件仅此一处、从未被使用）⇒ ⇒ 不能当依据）⇒ ⇒ **按 3s 对齐，已落码**。

**② 🔴 结构性根因（与 users 侧 §43.1 同一条，不重复展开）**：
G4 那张「逐组件实测表」是**在 G4 自己的树上解析 YAML** 得出的 ⇒ ⇒ **G4 的树里没有我 lane 的任何提交** ⇒ ⇒ 在 G4 树里取 `follow-api` 的 yml，`connect-timeout` 命中数 = **0** ⇒ ⇒ **同一份代码，两棵树读出两个相反的结论** ⇒ ⇒ **跨 lane 的实测表若不带「树 / commit 基线」，必然只对那棵树成立** ⇒ ⇒ 已在 §43.1 记完整判定式并回函 G4。

**③ 🟢 域侧 Filter 槽位守门在 follow-api 落码**（闭合 G4 §4.10.21 留下的「**域侧那一半**」）：
新增 `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/GoStyleDomainFilterSlotGateTest.java`（**4 条断言**）——
**①** 本模块产出的带 `@Order` 的 Filter 全集 == 域侧登记清单（**双向相等** ⇒ **域侧新增 Filter 必红**）·
**②** 实际槽位 == 登记槽位（读**实际** `@Order`）· **③** 不得占用共享层独占槽位 **+4（MaxConns）/ +5（Breaker）**（那两个都写响应 ⇒ 同槽 ⇒ 相对次序不可确定）·
**④** 真装配面：登记的域侧 Filter 真的挂进了容器装配出的链，且排在 **3s 预算起点**（`HttpMiddlewareFilter`）**之前**（读法 = `ServletContextInitializerBeans`，与既有 `FollowApiJwtVerificationScopeTest` 同款）。
⇒ ⇒ **follow 域现态**：唯一的有序 Filter = `GoStyleJwtAuthFilter`（`@Order(+6)`，与共享层 `CpuShedding(+6)` 并列；它**只把验签结果放进 request attribute、不写响应** ⇒ 并列安全）⇒ ⇒ **4/4 绿**。
⇒ ⇒ ⚠️ **不 import 共享层的 Filter 类**（只按 bean 名定位）⇒ ⇒ **共享层辐射度快照（`common_radiation`）不变** ⇒ ⇒ 本轮实测 `check-wave-coupling.py` 的差量**仍是那 1 项已知漂移，未被 follow 域扩大**。
⇒ ⇒ 完整判据、变异三连与边界 ⇒ [`users/vs-common.md` §43.2](../users/vs-common.md)。

**④ 🔴 顺带挖出：台账自检**从未查过 follow 域** ⇒ 本轮按域跑才见红**：`tools/check-ledger-selfcheck.py` 的 `LEDGER_FILES` 只有 `task` / `users` / `users-admin` 三个域键 ⇒ ⇒ **`follow` 域 + `users/{vs-common,common}.md` 这 7 份台账从来没被它查过** ⇒ ⇒ **默认参数跑出来的 EXIT=0 查的是 task 域** ⇒ ⇒ 本轮用**门禁自己的层-1 判据**单独扫了那 7 份 ⇒ ⇒ **8 处失效引用**（两轮「真删除」收编删了文件、台账历史记述仍写全路径）⇒ ⇒ 已逐条**如实标注状态**（不是让门禁闭嘴）⇒ ⇒ 复扫 **118 个引用路径 ⇒ 缺失 0**。
⇒ ⇒ 落点 `tools/check-ledger-selfcheck.py` ∈ **两张表都查不到** ⇒ ⇒ **写权孤儿 ⇒ 只能合并收口执行者改** ⇒ ⇒ **follow 域零落码** ⇒ ⇒ 完整经过与可复跑命令 ⇒ [`users/vs-common.md` §44](../users/vs-common.md)。
⇒ ⇒ 🟢 **落点归属订正（2026-10-28 · G1）**：上面「写权孤儿」这个描述**已被推翻** ⇒ ⇒ **G4 的 `9037a2b` 直接改了这个文件** ⇒ ⇒
**它事实上已是该文件的实际写权人** ⇒ ⇒ **「`LEDGER_FILES` 补 follow 域 5 份」这条反馈单改判为【转派 G4】** ⇒ ⇒ **不再需要 Jason 拍板**
⇒ ⇒ 顺带：**它一修（提取器从死代码变成真能定位）就暴露了 follow 域之外的 2 条错引用**（在 `users/admin/rpc.md`）
⇒ ⇒ **已逐条重核真源并改正** ⇒ ⇒ 逐条 ⇒ [`users/vs-common.md` §44.5](../users/vs-common.md)。

### 11.14 🟢 **Testcontainers 宿主口钉段落码（G4 §4.10.39 ② 的域侧那一半）—— follow 域 6 个容器起点 + 1 台域级守门**（2026-10-27 · G1 lane）

**① 落码（follow 域部分）**：
- **2 份** `GoStyleHostPortPinning`（`follow-api` / `follow-rpc` 各一份）⇒ 段 = **follow 域 `22730–22759`**（G4 §4.10.39 ②）⇒ ⇒ **全部落在 OS 临时段 `49152–65535` 之外**。
- **6 个容器起点**各加一行 `.withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, <5432|6379>))`：`follow-api` 2 个（Redis 6379）· `follow-rpc` 4 个（PG 5432）—— `FollowApiRedisDisconnectedFastFailTest` · `FollowFlywaySharedSchemaGateTest` · `FollowGrpcInterceptorWiringTest` · `FollowRpcSlowThresholdGateTest` · `FollowRpcWiringTest`。
- **1 台域级守门** `FollowDomainHostPortPinningGateTest`（3 条断言）：① **覆盖（守恒）**：域内每个容器起点都带 modifier 且钉的是该容器自己的口 · ② **段正确**：在 `22730–22759` 内且**与 OS 临时段不相交** · ③ **副本等价**：2 份 helper 剥注释后逐字等价。

**② 为什么是「段内挑空闲口」而不是写死一个口**：本模块 surefire 设 `TESTCONTAINERS_RYUK_DISABLED=true` ⇒ **Ryuk 不回收** ⇒ 残留容器可能**跨轮存在** ⇒ ⇒ 写死一个口 ⇒ 残留占住 ⇒ Docker 绑口**硬失败** ⇒ ⇒ **把「偶发撞口」换成「确定性撞口」⇒ 方向反了** ⇒ ⇒ 「段内挑空闲」可自愈。
⚠️ **残留的竞态（如实登记）**：「探端口」与 Docker「绑端口」之间有极小窗口 ⇒ ⇒ 两个 JVM 同时挑中同一个口 ⇒ ⇒ 后绑者**硬失败**（大声失败，不是静默走偏）。

**③ 真容器那一层 follow 域不重复起容器**：「钉口后 `getMappedPort()` == 那个固定口且真连得通」由 users 域的 `UserRpcTestcontainersHostPortSegmentGateTest` 实测（L4）⇒ ⇒ follow 域靠①（覆盖）+③（副本等价）保证「用的是同一份实现」⇒ ⇒ **不重复起容器**（follow 域的容器测试已够慢）。

**④ 变异验证**（users 域那份守门上实测，三条断言各有判别力）：A 拿掉一个钉口 ⇒ 只有①红 · B 把段起点改成 `49152` ⇒ 只有②红 · C 改一份 helper 的逻辑 ⇒ 只有③红 ⇒ ⇒ 复原后 **3+3 全绿**。
⇒ ⇒ 完整经过与 users 域那半 ⇒ [`users/vs-common.md` §45](../users/vs-common.md)。

**⑤ 回归口径**：`follow-api` **72**（+0）· `follow-rpc` **81**（+3 = 新守门）⇒ ⇒ follow 域 **153**；我域 6 模块合计 **688** ⇒ ⇒ 详见 [`users/vs-common.md` §45.7](../users/vs-common.md)（含一条「基线过期」的实测订正）。

### 11.15 🟢 **并入 G4 终版 `c26e462`（11 笔 · 零冲突）+ 角色口径改写**（2026-10-27 · G1 lane）

**① 并入 G4 终版（合并提交 `ddb63f7`）**：
- **代码层零重叠**（实测）：G4 自 merge-base `f6d58de` 以来真正改的代码/工具文件**只有 4 个**，
  全在共享层 ⇒ ⇒ 与 follow 域写权面（`service/follow/**`）**交集 = 空** ⇒ ⇒ 零冲突是结构性的。
- **对 follow 域最直接的一条**：`tools/difftest/check-tomcat-limits.py` 改为**直接读共享层真源**
  ⇒ ⇒ **新版守门实测 `service/follow/follow-api` 四旋钮全 ✅**（① relaxed-chars ② max-param ③ header-size ④ form-gate）
  ⇒ ⇒ §11.14 之外的 follow 域 api 容器面**没有被并入改坏**。
- ⚠️ **合并后唯一红点 = `service/task/task-api`**（G2 写权内 · G2 已在他们树用 `4786eb5` 修）
  ⇒ ⇒ 与 follow 域**无关** ⇒ ⇒ 完整归因与「为什么本轮推翻 §41 的持住决定」⇒ [`users/vs-common.md` §47.1~§47.2](../users/vs-common.md)。

**② 角色口径改写**：本台账 **6 处**「整合者」→ **「合并收口执行者」**（依据 `docs/Worktree.md` §1e · 主干 `4ae5461`）
⇒ ⇒ 改的**只有角色词**，事实一个没动；**他域台账的同款措辞不在 G1 写权面** ⇒ ⇒ 零落码、登记给合并收口执行者
⇒ ⇒ 完整边界与清单 ⇒ [`users/vs-common.md` §47.4~§47.5](../users/vs-common.md)。

**③ 共享层新增守门对 follow 域无影响**：`GoStyleRequiredFieldGateTest` 落在 `service/users/users-common/**`（G4 写权）
⇒ ⇒ 与 follow 域无 import 关系 ⇒ ⇒ **零影响** ⇒ ⇒ 已在我域 `users/common.md` §5.7 点名。

**④ 并入后的域级全量回归（实测 · 2026-10-28 00:09~00:17）**：
`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test` ⇒ ⇒ **18/18 BUILD SUCCESS · mvn EXIT=0 · 总耗时 7:46**；
`follow-api` **72**（+0）· `follow-rpc` **81**（+0）· `follow-common` **37**（+0）⇒ ⇒ **follow 域 190**（含本域私有 `follow-common`）；
⇒ ⇒ **域内条数逐条不变**（并入在代码层与我域**零重叠** ⇒ 理应不变 ⇒ 实测与理应一致 ⇒ 两侧互证）；
共享层 `common` 330→**331** · `users-common` 33→**39**（+7 全部有出处：结构锁 1 + 新守门 6）。
⇒ ⇒ 完整逐模块表 ＋ **窗口纪律的执行记录** ＋ **「日志里 ERROR ≠ 失败」的判据** ⇒ [`users/vs-common.md` §47.7](../users/vs-common.md)。

### 11.16 🟢 **follow 域的「共享层 artifact 新鲜度守门」（G2 回执② 的域侧那半）**（2026-10-28 · G1 lane）

- **落码**：`FollowRpcSharedLayerArtifactFreshnessGateTest`（`service/follow/follow-rpc/src/test/java/**`，**2 条断言**）
  ① 本模块解析到的共享层 artifact 必须携带「MP 排序桥 + Redis 自动配置」两个 FQCN（**独立字面量**）
  ② 域内那份排序桥必须不在本模块源码里（收编真的发生了）
- **它防的是什么**：单模块构建（`mvn -pl <module>` **不带 `-am`**）⇒ `service/common` 不在 reactor
  ⇒ 依赖从 `~/.m2` 取 ⇒ 拿到**陈旧**的 `common` jar ⇒ ⇒ **共享层那份排序桥不在产物里**
  ⇒ ⇒ Boot 4 下 MyBatis-Plus 早于 DataSource 求值 ⇒ **进程启动即挂**。
- **本域已有的那层**：`FollowRpcWiringTest`（容器装配层 · 三条腿：注册层 / 域内副本已消失 / 机制层 `SqlSessionFactory` 真产出）
  ⇒ ⇒ **不是「零覆盖」** ⇒ ⇒ 新守门补的是「**陈旧 jar 时红得不明白**」那一层。
- **变异验证**：A 不带 `-am` 单模块构建 ⇒ **恰红 1 条**（红在第①条，消息点名「陈旧 common jar」+ 修法 `-am`）；
  B 放回域内副本 ⇒ **恰红 1 条**（红在第②条）⇒ ⇒ 两条断言**各有**判别力 ⇒ 复原后全绿。
- ⇒ ⇒ 完整经过与「类path 层 ≡ 产物层」的机械论证 ⇒ [`users/vs-common.md` §48.2](../users/vs-common.md)。

### 11.17 🟢 **follow-api 的 Redis 断连 B 组落码（api 侧这条轴升 L4）**（2026-10-28 · G1 lane）

- **背景**：G3 第九次来信的 ④ 反问 —— 「你域那 5 份 Redis 断连守门是否**只有 A 组**」
  ⇒ ⇒ **如实答：是，只有 A 组**（机器证据：`DisconnectedBehavior.DEFAULT` 提及 **0** 次 · `@TestConfiguration` **0** 个）
  ⇒ ⇒ ⇒ **那 5 份本身【最多 L3】**，本条如实登记、不粉饰。
  ⇒ ⇒ **但**同一条 `disconnectedBehavior` 轴上我域**另有** A/B 对（rpc 侧 · 观测点是「客户端可见的 gRPC status」）
  ⇒ ⇒ **轴上已有 L4**；**api 侧当时确实只有 A 组** ⇒ ⇒ ⇒ **本轮把 api 侧补上**。
- **本域落码**：`FollowApiRedisDisconnectedDefaultBehaviorTest`
  （`service/follow/follow-api/src/test/java/com/rag/follow/follow/api/` · **2 条** = 前提腿 1 ＋ 目标腿 1）
  - **零生产代码**（只加测试文件，不碰 `src/main/java`）
  - **零重构**：故障注入直接复用 A 组那个 **package-private 嵌套类**
    `FollowApiRedisDisconnectedFastFailTest.GracefulDisconnectProxy` ⇒ ⇒ **同一份字节码**（不是照抄）
    ⇒ ⚠️ **但必须是独立实例**：A 组的 `PROXY` 是 `static final` ⇒ 两个类共用它 ⇒ A 组先断连后 B 组拿到的代理已经断了
    ⇒ ⇒ **本组自己建一份**（同一个类、不同的实例、上游指向**自己的** Redis 容器）⇒ ⇒ 两组互不污染
  - **唯一变量 = 断连策略**：test 侧 `@TestConfiguration` 提供一份 `DEFAULT` 的 `LettuceClientOptionsBuilderCustomizer`
    ⇒ ⇒ **遮蔽**共享层那份 `REJECT_COMMANDS`（靠 `@ConditionalOnMissingBean`）⇒ ⇒ **域内零落码**
- **实测（2026-10-28 · 真 Redis 容器 ＋ 真 Lettuce ＋ 真 FIN 代理 · 非 mock）**：**3004ms**（排队到 `commandTimeout`）
  ⇒ ⇒ 与 A 组的「**1ms** 快速失败（预算 1500ms）」**同故障形态 · 同代理实现 · 只换策略** ⇒ ⇒ **归因成立**
  ⚠️ **两组是两次独立跑批的实测值**（同日 · 同镜像 `redis:7-alpine` · 同 FIN 代理实现 · 同测点 `hasKey`）⇒ ⇒ **可比**。
- **变异**：**删掉遮蔽 bean** ⇒ 共享层 `REJECT_COMMANDS` 接管 ⇒ ⇒
  **前提腿**红（`断连行为必须是 DEFAULT ⇒ 实测 REJECT_COMMANDS`）**＋ 目标腿**红（**`实测 1ms`**，红在**耗时断言**上）
  ⇒ ⇒ **2 条全红** ⇒ ⇒ 回滚后 **sha256 与基线逐字节一致** ✔
- ⚠️ **本域的两条硬约束（写下来，免得将来重踩）**：
  1. **不要**用 `container.stop()` 做故障注入 ⇒ 那是 **RST** ⇒ 不走 `disconnectedBehavior` 分支 ⇒ **测不出这条漂移**
     ⇒ ⇒ 只能用「先关监听口 ＋ 再半关闭已建立连接」的 **FIN** 代理
  2. **B 组必须先把连接建立起来**再断连 ⇒ ⇒ 否则测的是「连接被拒」（~15ms 快失败）
     ⇒ ⇒ **会长得跟 A 组一样 ⇒ ⇒ 假象**（这是 2026-10-28 第一版 B 组真实踩到并修掉的）
- ⇒ ⇒ 完整经过 · A/B 方法学 · 变异口径 ⇒ [`users/vs-common.md` §53](../users/vs-common.md)。

### 11.18 🟡 **go-zero 族 `min-idle: 8` 规则 —— follow-api 属该族 · 当前未落池 ⇒ 无待办**（2026-10-28 · G1 lane）

- **规则（G3 提出）**：go-zero 族 ⇒ 若落 Lettuce 池 ⇒ `min-idle` 必须是 **8**，**不是** 0。
- **我独立复核（不采信转述）**：go-zero v1.10.2 `core/stores/redis/redisclientmanager.go:15` `idleConns = 8`
  ⇒ ⇒ `:32-39` 建 `red.Options` 时**传了 `MinIdleConns`**、**没传 `PoolSize`**。
  ⇒ 🔴 **规则能不能吃重，全看 go-redis 的下游默认值**：go-redis **v9.20.0** `options.go:350-351`
  ```go
  if opt.PoolSize == 0 {
      opt.PoolSize = 10 * runtime.GOMAXPROCS(0)
  }
  ```
  ⇒ ⇒ **池化默认开启** ⇒ ⇒ **`MinIdleConns: 8` 不是空转** ⇒ ⇒ **规则成立且有牙齿**。
  ⇒ ⇒ ⚠️ 若那个默认不存在 ⇒ `MinIdleConns` 就是死参数 ⇒ ⇒ **这条规则必须连「下游默认值」一起核**。
- **本域现状（2026-10-28 实测）**：**未落池**
  - `grep -rn "commons-pool2" service/follow --include=pom.xml` ⇒ **空**
  - `grep -rn -A4 "pool:" service/follow --include=*.yml` ⇒ **空**
  ⇒ ⇒ **follow-api 当前是「直连」形态** ⇒ ⇒ **`min-idle` 无从谈起 ⇒ 无待办**。
- 🟡 **将来触发条件**：若 `follow-api` 因任何原因落池 ⇒ ⇒ `min-idle` **必须 8**
  ⇒ ⇒ 同一时刻的三条必配项：**①** `max-wait: 4s`（Go `PoolTimeout = ReadTimeout + 1s`；不配 ⇒ commons-pool2 `DEFAULT_MAX_WAIT = -1` ⇒ **无限等**）
  · **②** **`commons-pool2` 必须域内 pom 自己声明**（Boot 4 的 starter 不再传递 ⇒ 缺它 ⇒ 启动期 `NoClassDefFoundError`）
  · **③** `max-active` / `max-idle` 是「表达层更严」（性能面，**不影响命令结果**）。
- 🟡 **守门变异坑（登记，本域当前无实例）**：池守门若直接 `import org.apache.commons.pool2.impl.GenericObjectPoolConfig`
  ⇒ ⇒ 「抽掉 pom 依赖」这个变异**只得 test-compile 失败**，**不是运行时证据**
  ⇒ ⇒ 要验「抽依赖 ⇒ 启动期硬失败」必须**把守门挪走** ＋ 跑**真 Spring 上下文**的守门。
  ⇒ ⇒ 本域实测 `GenericObjectPoolConfig` / `commons.pool2` **零命中** ⇒ ⇒ **无实例、无待办**。
- ⇒ ⇒ 完整论证 ⇒ [`users/vs-common.md` §53.4 / §53.5](../users/vs-common.md)。

### 11.19 🟢 **follow 域条数重测（2026-10-28 · 双路径交叉核对）**（2026-10-28 · G1 lane）

- **回归**：`mvn -B -o -DargLine=-Xmx2g -am -pl <我域 6 模块> clean test` ⇒ ⇒ **18/18 BUILD SUCCESS · EXIT=0 · 47:10 · 0f/0e/0s**
- **两条独立路径逐模块相等**：**路径 A** = maven 模块级 `Results:` 汇总行 · **路径 B** = 逐模块 surefire `TEST-*.xml` 求和
  | follow 域模块 | 路径 A | 路径 B |
  |---|---|---|
  | `follow-api` | **74** | **74** |
  | `follow-rpc` | **83** | **83** |
  | `follow-common`（本域私有）| **37** | **37** |
  | **follow 域合计** | **194** | **194** |
- **增量归因（688 → 707 = +19 的 follow 侧那一半）**：`follow-api` **+2**（本轮 B 组 2 条）· `follow-rpc` **+2**（`FollowRpcSharedLayerArtifactFreshnessGateTest` artifact 新鲜度守门）· `follow-common` **±0**
  ⇒ ⇒ **逐条可指名，无余量**。
- ⚠️ **共享层旧值**：`common` 327/330/331 ⇒ ⇒ **最新 = 333**（2026-10-28 实测）⇒ ⇒ **凡引用条数以 [`users/vs-common.md` §54](../users/vs-common.md) 为准**（历史原文保留不改写）。
- ⇒ ⇒ 完整口径（含 reactor 18 模块 vs 目标 6 模块的差值归因 409 + 707 = 1116）⇒ [`users/vs-common.md` §54](../users/vs-common.md)。

---

## 12. 🔴 新立 `DRIFT-FOLLOW-013`：**「OS 盲选口」族**（我域 **4 处**）⇒ 已修 ＋ 守门（2026-10-28 · G1 lane）

> ⚠️ **2026-10-29 真实性复核**：标题的 **🔴** ⇒ **现行态 = ✅ 已修（2026-10-28 落码 · 本轮复核属实）** ⇒ **标题的 🔴 是「本条新立时」的标记**（标题后半句「⇒ 已修 ＋ 守门」与正文 §12.1–§12.3 的表一致）。
> **依据（当场核过 · 4 处逐处）**：`service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowGrpcInterceptorWiringTest.java:115` = `ServerBuilder.forPort(RealGrpcPort.pick());` · `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcSlowThresholdGateTest.java:274` = 同款 · `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiStartupFailFastTest.java:92` = `return RealGrpcPort.pick();` · 同文件 `:258` = `NettyServerBuilder.forPort(RealGrpcPort.pick())` ⇒ ⇒ **4 处全部已改**；段归属亦实测相符：`follow-api/support/RealGrpcPort.java:66,69` = 22300/22319 · `follow-rpc/support/RealGrpcPort.java:66,69` = 22320/22339 · `follow-api/support/RealTomcatPort.java:84,87` = 22340/22359（**三段互不重叠**，与 §12.2 的表一致）⇒ ⇒ **全域实测 `grep -rn "forPort(0)\|new ServerSocket(0)\|RANDOM_PORT" service/follow/ --include='*.java'` 零处真实命中**（只余注释里的反面教材）。

> 📌 **完整机制 / 实测 / 守门 / 效力边界 ⇒ [`docs/DRIFT/users/vs-common.md` §65](../users/vs-common.md)（users 域与 follow 域共用同一族）**
> ⇒ 本节只记 **follow 域自己那一份**（不重复抄机制表，避免两处漂移）。

### 12.1 我域暴露面（自数，**不采信转述**）

| 族 | 写法 | follow 域处数 | 位置 |
|---|---|---|---|
| A | `forPort(0)` | **2** | `FollowGrpcInterceptorWiringTest.java:111` · `FollowRpcSlowThresholdGateTest.java:270` |
| C | `new ServerSocket(0)` | **1** | `FollowApiStartupFailFastTest.java:87`（`findClosedPort()`） |
| D | `RANDOM_PORT` | **0** | 🆕 **2026-10-29 `T11` 订正**：本域**已有**真 Tomcat 测试（`FollowApiFailurePathMatrixTest`）⇒ **仍然 0** ⇒ 它用的是 **`DEFINED_PORT` + `RealTomcatPort.pick()`** ⇒ **不引入 `RANDOM_PORT`** |
| B | `grpc.server.port=0` | 若干 | **无人连接** ⇒ 不落码（口径见 §65.2） |
| | **合计** | **4** | |

⇒ ⚠️ **G2 的波级报点说 follow 域 2 处** ⇒ ⇒ **实为 4 处**（漏 `FollowApiStartupFailFastTest` 的 C 族 1 处 ＋ `follow-rpc` 的 A 族 1 处）
⇒ ⇒ 差异原因：G2 的口径是「`forPort(0)` 文本」⇒ **C 族（`new ServerSocket(0)`）不在那个口径里** ⇒ 已回报。

### 12.2 修法（与 users 域同款，**段不同**）

| 组件 | 类 | 段 | 段来源 |
|---|---|---|---|
| follow-api | `support/RealGrpcPort` | **22300–22319** | 共享层预分配的 **follow 域段 22300–22399** |
| follow-rpc | `support/RealGrpcPort` | **22320–22339** | 同上 |
| follow-api | `support/RealTomcatPort` | **22340–22359** | 同上（🆕 **2026-10-29 `T11` 新增** ⇒ 本域**首次**有真 Tomcat 测试）|

⇒ 🆕 **订正（2026-10-29）**：本节原写「（22340–22399 ＝ 域内预留 ⇒ 域级守门拿它当「脚手架段」）」⇒ **那是错的** ⇒ 域级守门 `BlindPortGateTest` 的**脚手架段实测是 `SCRATCH_FIRST=22280` / `SCRATCH_LAST=22299`**（落在 **users** 段 22200–22299 内）⇒ ⇒ 域级守门的脚手架段**不在 follow 段内**。
⇒ ⇒ 现状：follow 段 **22300–22359 已占 3 段**，**22360–22399 仍空**（域内备用）。

⇒ 机制、探针实测（Q3/Q6/Q7/Q8）、类形状来源（G3 comment-rpc 的 `support/RealGrpcPort`）⇒ **全部见 §65**。
⇒ ⇒ **follow 域生产代码零改动**（4 处全在 `src/test/java`）⇒ ⇒ **不构成 Go 对照项**。

### 12.3 守门

域级守门 `BlindPortGateTest`（落在 **user-rpc**，`service/users/user/user-rpc/.../support/BlindPortGateTest.java`）**一并覆盖 follow 域**：
① 源扫 follow 两模块测试树零盲选口 ② 段归属（follow 两段落在 22300–22399 内 ＋ 子段不重叠）③④⑤ 见 §65.5
⇒ ⇒ **follow 域不单独建守门**（避免两份规则漂移）⇒ ⇒ 跨域引用按「**域 ＋ 文件名**」指。
⇒ 🆕 **2026-10-29 `T11` 订正**：② 段的「端口类个数」断言 **8 → 9**（follow-api 新增 `RealTomcatPort`）⇒ **同批更新，不留雷**。

---

## 13. 🟢 新立 `T11`：**follow 域独立失败路径矩阵**（8 端点 × 4 类失败 × **真 Tomcat**）⇒ 基线 §九 `G-13` 在本域**闭合**（2026-10-29 · G1 lane）

### 13.1 起因（基线原文）

`docs/PhaseGoal-judge.md` P0 **#2** 的判据：
> 「存在失败路径处理」**推不出**「参数校验 / 业务规则 / 认证 / 权限四项都有效」；且失败路径**域覆盖不全**

＋ §九 **`G-13`**：「**4 域无独立失败路径矩阵**（`article` / `follow` / `task` / `hot`）」⇒ 🟡 **否**（P0 #2/#4 因此为 🟡）。
⇒ **follow 是这 4 域之一** ⇒ 本轮补齐。

### 13.2 落点（3 个文件）

| 文件 | 动作 | 内容 |
|---|---|---|
| `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/support/RealTomcatPort.java` | **新增** | 真 Tomcat 的端口选取 + **双地址** bind 自检（`0.0.0.0` **与** `127.0.0.1` **两个都要探**）· 段 **22340–22359** |
| `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiFailurePathMatrixTest.java` | **新增** | **62 条**：8 端点 × 4 类失败 ＋ 基础设施类 ＋ 7 条单点 |
| `service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/support/BlindPortGateTest.java` | **修改** | ② 段「端口类个数」断言 **8 → 9** |

⇒ ⚠️ **follow 域生产代码零改动** ⇒ ⇒ **不构成 Go 对照项**（与 §12 同款口径）。

### 13.3 为什么**必须真 Tomcat**（不是 MockMvc）

本域原先 **10 个测试类全部是 `webEnvironment = MOCK`** ⇒ **真容器覆盖率为零**。MockMvc 有三处**系统性**测不到：

1. **Content-Type 的容器归一化** —— 真 Tomcat 把 `text/plain; charset=utf-8` 归一化成 `text/plain;charset=utf-8`（分号后无空格）⇒ **MockMvc 里断言「带空格」的写法会绿、真容器下会红** ⇒ 这正是 **DRIFT-024**（`text/plain 400 丢 Content-Type`）当初漏网的机制；
2. **`Content-Length`** —— 由容器依据实际写入字节数计算，MockMvc 不参与；
3. **body 是否被容器补写** —— 405 这类「body 为 null」的响应，容器会不会自作主张补空 body 或补 Content-Type，**只有真容器说了算**。

### 13.4 矩阵（期望值逐条带 Go `文件:行号`）

| 类 | Go 出处 | 形态 | 条数 |
|---|---|---|---|
| **认证** | `rest/handler/authhandler.go:106` · `middleware/checkblacklistmiddleware.go:42-48` · `logic/follow/helper.go:13-33` | ① 401 **空 body** ＋ **无 Content-Type** ② 401 **两字段** ＋ `application/json;charset=utf-8`（**逐字节**）③ **HTTP 200** ＋ `{code:5004}`（**不是 401**）| 8×3 |
| **权限** | `getblocklistlogic.go:45-50` · `getrecommendationslogic.go:45-50`（**只有这两个**有 Forbidden 分支） | 200 ＋ `{code:5005}` ＋ **RPC 未被调用**；`getfollowlistlogic.go:45` / `getfollowerlistlogic.go:45` **没有** ⇒ **公开可查** | 8 |
| **参数校验** | `types/types.go:25-27`（`target_id` **必填**）vs `:6-10`（三字段**全 optional**） | 动作端点 **200 ＋ `{code:5001}`**（**不是 400**）；list 端点按**零值**继续 | 8 |
| **业务规则** | 8 个 logic 里逐条相同的 `NewErrCodeMsg(int(rpcResp.Code), rpcResp.Msg)` | **原样透传** code ＋ msg | 8 |
| **基础设施** | `common/errmsg.go` `BizCodeFromError` | rpc 抛 `UNAVAILABLE` ⇒ **200 ＋ `{code:1015,msg:"service busy"}`** | 8 |

### 13.5 ⚠️ 首轮 12 条红：**是判据自身的错，不是产品缺陷**

| # | 错在哪 | 处置 |
|---|---|---|
| ① | `assertEnvelope` 对**成功**信封也要求 `"data":null` —— 而**成功**信封的 `data` 是**载荷**（`{"success":true}` / `{"user_ids":null}` / `{"users":[]}`）⇒ **11 条**假红 | 拆成 `assertErrorEnvelope`（`data` 必须 null）/ `assertSuccessEnvelope`（`data` 非 null）⇒ **改判据，不是改产品** |
| ② | 404 的 `Content-Length` 被我**写死成 20** —— 实测 **19**（`"404 page not found\n"` = 18 + 1）⇒ **1 条**假红 | 改成「按**实际 body 字节数**比」（与 like-api 同法）⇒ ⇒ 并**顺带查出两处陈旧注释**（见 §13.6）|

⇒ 📌 **这正是 AGENTS.md §3.3 规则 ③ 的应用**：**先改/删错误断言，再改实现** ⇒ 否则会先撞「测试红了」而误判自己改错。

### 13.6 🆕 顺带查出的**两处陈旧注释**（**域外 ⇒ 只登记上报**）

| 编号 | 位置 | 内容 | 归属 |
|---|---|---|---|
| `S1-FOLLOW-010` | `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:83`<br>⚠️ **2026-10-30 复核**：现行指针 = **`:80`**（`:83` 现指向「订正说明」那一行） | javadoc 写「全长 **`20`** 字节」⇒ 实测 **19** ⇒ ⚠️ **常量本身是对的**（真容器实测 `Content-Length: 19`）⇒ 只是**注释**错<br>⚠️ **2026-10-30 真实性复核（follow+task lane）**：原判「🔴 注释错 ⇒ 派 G4」⇒ **现行态 = ✅ 已闭环（2026-10-29 已由 G4 落码订正）** —— RAG `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:80` 现写「…**带一个尾随换行**，全长 {@code 19} 字节」＋ `:83` = 「🔴 **2026-10-29（R3 轮）订正**：本 javadoc 原写「全长 {@code 20} 字节」⇒ **错**」⇒ ⇒ **所报缺陷已不存在** ⇒ **不再派 G4**；⚠️ **指针订正**：原 `:83` → 现行 **`:80`**。核对日期 2026-10-30 | **G4**（`service/common/**`）· **2026-10-30 ⇒ 已闭环，不再派 G4**|
| `S1-FOLLOW-011` | `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiRouterErrorRealTomcatTest.java:46` 与 `:187`<br>⚠️ **2026-10-30 复核**：`:187` 已位移（现为空行）⇒ 现行指针 = **`:46`** ＋ **`:190`**（`@DisplayName` 那行） | javadoc 与 `@DisplayName` 都写「20」⇒ ⚠️ **断言本身是对的**（按实际长度比）⇒ **一直绿** ⇒ 只有注释与显示名错<br>⚠️ **2026-10-30 真实性复核（follow+task lane）**：原判「🔴 注释/显示名错 ⇒ 派 G2」⇒ **现行态 = ✅ 已闭环（2026-10-29 已由 G2 落码订正）** —— RAG `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiRouterErrorRealTomcatTest.java:46` 现写「…全长 **19** 字节」＋ `:48` = 「⚠️ 2026-10-29 订正：原写「20 字节」是**错的**」＋ `:190` 的 `@DisplayName` 现写「…= **19** 字节」⇒ ⇒ **所报缺陷已不存在** ⇒ **不再派 G2**；⚠️ **指针订正**：原 `:187` → 现行 **`:46` / `:190`**。核对日期 2026-10-30 | **G2**（`service/like/**`）· **2026-10-30 ⇒ 已闭环，不再派 G2**|
| `S1-FOLLOW-013` | `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiFailurePathMatrixTest.java:417` | **本轮新增文件自己的断言消息也写了「全长 20 字节」**（**G1 自己写的**）⇒ **断言本身是对的**（比 body 字面量）⇒ **错数只在文案里** ⇒ ⚠️ 且它**躲过了 G1 的第一轮自查**（**模式选错** ＋ **`head -40` 截断**，两个漏洞叠加 ⇒ 详见 users 档 §68.2bis）⇒ **已二次订正** | **G1 自修**（`service/follow/**`）<br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/ddl-parity.sh` ⇒ **EXIT=2**（2026-10-30 03:34:15 · 2s）—— 🟡 **探针判定为「环境问题」**（结论不可用）；覆盖断言：（未抓到结论行）；<br>`tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两；<br>`tools/difftest/ddl-parity.sh` ⇒ **EXIT=2**（2026-10-30 03:34:15 · 2s）—— 🟡 **探针判定为「环境问题」**（结论不可用）；覆盖断言：（未抓到结论行）；<br>`tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|

⇒ ⇒ **判据**：① 本域**不复述**那个错数（改按实际长度比）；② **不擅自改**别家写权面的文件 ⇒ 只登记 + 上报。

### 13.7 效力边界（如实登记，不夸大）

| 边界 | 内容 |
|---|---|
| **证据强度** | **L3 / R3**（期望值逐条抄自 Go 源码 + 有 RAG 侧自动化断言）⇒ 满足基线 §1.1 **R3** 的 ④⑤⑥ |
| ⛔ **不是 R4** | R4 要求**双侧活体对拍 ＋ 变异** ⇒ 本类**不是**双侧对拍 ⇒ ⇒ **不得**据此宣称「与 Go 逐字节一致」 |
| ⚠️ **P0#2 仍不是 ✅** | 其余 **3 域**（`article` / `task` / `hot`）**仍无**独立失败路径矩阵 ⇒ 本条只闭合 **follow 域** ⇒ **不因本条把 P0#2 判成 ✅** |
| ⚠️ **双侧对拍缺口不受影响** | 基线 §九 `G-1`（follow 域探针在 `/tmp` ⇒ 已不可复算）**本轮未动** ⇒ `follow-api` / `follow-rpc` 仍按 **R3** 计 |

---

## 14. 🟢 **「Go 出处 `文件:行号` 指错」族 —— follow 域部分：零错**（2026-10-29 · G1 lane）

> 📌 **完整扫描口径 / 判据 / 脚本失效模式 / 可复算性边界 ⇒ [`docs/DRIFT/users/vs-common.md` §69](../users/vs-common.md)**（该节覆盖 **users + follow 两个域的 849 条引用**）⇒ 本条只记 **follow 域那一份**。

| 项 | 值 |
|---|---|
| 扫描面 | `service/follow/**`（排除 `target/`）⇒ `grep -rnE "\.go:[0-9]+"` ⇒ **命中 37 行在 `FollowApiFailurePathMatrixTest`**（本轮新增文件，引用最密）＋ 其余 follow 文件 |
| **核验结果** | **follow 域的引用全部可核、零错**（文件存在 ＋ 行号在范围内 ＋ 该行非空）|
| ⚠️ **本轮新增文件的引用单独核过** | `FollowApiFailurePathMatrixTest` 的 Go 出处**逐条带 `文件:行号`**（`routes.go:20-63` · `types/types.go:6-10,25-27` · 8 个 logic 的各行 · `common/errmsg.go` 的 `BizCodeFromError`）⇒ **全部可核** |
| **元教训** | 注释里的「事实」有**两类载体**：**数字**（§13.6 的字节数族）与 **坐标**（本节的出处族）⇒ **两类都会错、且都不会让测试变红** ⇒ ⇒ **「全绿」不能证明注释是对的** ⇒ 必须**单独建判据**去核 |

⇒ ⇒ **推论**：**基线 G-1（follow 双侧探针未落仓）仍未闭合** —— 本节证明的是**「出处坐标是对的」**，**不是**「行为逐字节等价」⇒ **不因此提升 R 档**。

---

## 15. 🟡 **开工轮（2026-10-29 03:45 · Jason 开工批准）**：follow 域 **0 处命中** ＋ **1 处新发现未修**（2026-10-29 · G1 lane）

### 15.1 本轮做了什么（**follow 域零代码改动**）

§8.7 登记的 **G1 23 条**（15 版本写错 ＋ 5 行号越界 ＋ 3 路径不精确）**全部落在 `users` 域** ⇒ **follow 域本轮零改动**。
⇒ 逐条修复记录 / 修法论证 / 判据实现 ⇒ [`docs/DRIFT/users/vs-common.md` §71](../users/vs-common.md) · [`docs/DRIFT/users/stage1-admission.md` §10](../users/stage1-admission.md)。

### 15.2 🔴 **对 §14「零错」结论的限定订正**（与 §71.2 同一理由）

§14 的判据**没有「版本」这一维**（三项廉价可判：文件存在 / 行号在范围内 / 该行非空）⇒ **「引到另一份内容不同的副本」这一族天然漏检** ⇒ ⇒ §14 的准确表述是「**follow 域的引用在**该判据口径下**未发现错误**」，**不是**「follow 域零错」。

| 项 | 值 |
|---|---|
| 本轮对 follow 域跑的判据 | **可判定 80 条 ⇒ OK 79 · FAIL 1**（那 1 条 = §15.3）· **机器判不了 245 条**（模块相对路径 / 裸文件名）|
| ⇒ follow 域出处的**已知欠账** | **1 条**（已登记未修）＋ **245 条机器判不了** ⇒ **不得**宣称「follow 域零错」|

### 15.3 🟡 本轮新发现（**1 条 · 不在批准范围 · 未修**）

| 引用处 | 现引用（**在 Go 仓不存在**） | 真身候选 |
|---|---|---|
| `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/service/FollowUserExistenceService.java:78` | `service/user/user/rpc/.../getuserlogic.go` | `service/user/user/rpc/internal/logic/getuserlogic.go` |

⚠️ **为什么没修**：① 不在 Jason 已批准的封闭集合（**23 条**）内；② 本条真身虽可判定（基名唯一），但**所属域是 `users`** ⇒ 与 users 域的 10 条**合并一次修完**更省事（**建议下一轮一次修 11 条**）。
⚠️ **与既有登记的关系**：`docs/Pitfalls/users/user/rpc.md` 里的 `getuserlogic.go:48-54` 是 **Go 侧的文件引用**，**不是**「RAG 引了不存在的路径」的登记 ⇒ **本条是新的、未登记**。

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
<!-- @new 2026-10-29 · `align/r3-ddl-users-follow` · RoundPlan `docs/RoundPlan/Round-3.md` **T-3.1 的 G1 侧（`follow` 域）** · 基线 = `do-my-own-gh` @ `4a4beaf8` -->

## 16. 🟢 **DDL 逐域机器核**（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G1 lane · `follow` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并逐域实跑，结论只落在 `docs/PhaseGoal-judge.md` §七 **G-8 / G-9** ⇒ **域级证据链断了一节** ⇒ 本节把 `follow` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不改任何差异**。
> ⚠️ **`follow` 域不是 N/A** —— `hot` 域才是 N/A（两侧无数据库）；`follow` 域**两侧都有真库**（Go `follow_relation` / `block_relation` · RAG 同名两表）⇒ 本节是**实跑**结论，不是「本维度不适用」。

### 16.1 执行口径（命令原文 · EXIT · 日期 · 环境）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（09:25–09:31 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-users-follow` · `align/r3-ddl-users-follow`<br>⚠️ **实跑时的基点 = `do-my-own-gh` @ `4a4beaf8`**；**提交时已 rebase 到 `do-my-own-gh` @ `49bd2dd1`** ⇒ 上游这两个提交**只动了 `.gitignore` 与 `docs/PhaseGoal-judge.md`**，**既没动本域这两个文件、也没动任何 `.sql`** ⇒ **实跑结论不受影响** |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本只**复制**副本，从不写入 |
| 容器 / 库 | `ddl-follow-pg`（`postgres:16-alpine`）· Go 库 `go_follow_db` · RAG 库 `rag_follow_db` |
| Go 侧 schema 来源 | **真源自己的 `AutoMigrate`**：`Sea-RideTheWind_golang/service/follow/rpc/internal/model/db.go:20`（`func InitDB(conf DBConf) *gorm.DB`）→ `:42`（`db.AutoMigrate(&FollowRelation{}, &BlockRelation{})`）⇒ 探针调的正是**真源自己的 `InitDB`**（`dbconf` 探针）⇒ 跑的是**真源同一份 struct tag + 同一个 gorm 版本** |
| Go struct 出处 | `service/follow/rpc/internal/model/data_model.go:5-15`（`FollowRelation` · `TableName()` → `follow_relation`）· `:17-27`（`BlockRelation` · → `block_relation`） |
| RAG 侧 schema 来源 | 仓内 `service/follow/follow-rpc/src/main/resources/db/migration/V1__create_follow_relation.sql` · `V2__create_block_relation.sql`（**Flyway**；生产 `locations: classpath:db/migration` ⇒ `application.yml:77`） |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 差异数是**原始值** |
| 两侧表集合 | Go `go_follow_db` = {`follow_relation`, `block_relation`} · RAG `rag_follow_db` = {`follow_relation`, `block_relation`} ⇒ **各 2 张，逐条一致**（对拍环境**直接用 psql 放 `.sql`** ⇒ 两侧都**没有** Flyway 账本表 ⇒ `exclude_tables` 本轮**无对象可排除**） |

**命令 ① —— 建库 + 双侧活体对拍（索引 / 约束 / 触发器 + 源码↔活库 一次跑全）**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-users-follow"
bash tools/difftest/ddl-parity.sh follow
```

⇒ **EXIT=0** —— 末行 `== 实质差异合计 0 处 ⇒ 退出码 0 ==`；runner 行 `=== 对拍退出码: 0 ===`
⇒ Go 探针打印 `AUTOMIGRATE_OK`；RAG 侧按序执行 `V1__create_follow_relation.sql` → `V2__create_block_relation.sql`

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`）**

```bash
python3 tools/difftest/check-ddl-static.py --domain follow --indexes --check-source
```

⇒ **EXIT=0**（0 处）—— 与 ① 同数同因 ⇒ 两条路径互为复算

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain follow --print-rag-sql
```

⇒ **EXIT=0**，输出 2 行，与 ① 实际执行的 2 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**

### 16.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（G-9 `--check-source`） | 2 张表 | ✅ **0 差异** —— 仓里的两份 `.sql` 在**干净临时库** `rag_follow_db_srccheck` 上原样重放后，与活库**逐项一致**（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器） | 0 |
| 2 | **列类型 / 长度** | 10 列 ↔ 10 列（键集合一致 ✓） | ✅ **0 差异** —— **10 列逐字相同** | 0 |
| 3 | **可空性（NOT NULL）** | 10 列 | ✅ **0 差异** —— 两侧完全一致 | 0 |
| 4 | **DEFAULT** | 10 列 | ✅ **0 差异** —— 两侧完全一致（**无** `CURRENT_TIMESTAMP` 残留） | 0 |
| 5 | **索引**（`pg_indexes`） | Go 8 / RAG 8 | ✅ **0 差异** —— **两侧索引定义逐条一致**（⚠️ 本域连**索引名**都逐字相同 ⇒ 比 `users` 域还干净） | 0 |
| 6 | **唯一约束 / 主键**（`pg_constraint`） | Go 2 / RAG 2 | ✅ **0 差异** —— **2 行逐条一致** | 0 |
| 7 | **触发器**（`pg_trigger`） | 两侧各 0 | ⚪ **两侧都没有触发器 ⇒ 本维度「无对象可比」** —— ⚠️ **这不是「触发器已对齐」** | 0 |
| 8 | **触发器形式 / 触发器函数体结构化属性** | 两侧各 0 | ⚪ **同上 ⇒ 无对象可比** —— 脚本原话：「两侧**没有可成对比对的触发器** ⇒ 本维度**什么都没比**（不是「已对齐」）」 | 0 |
| | **合计** | | | **0** |

⇒ **本域 DDL 在 8 个被核维度上 0 处残留。**

### 16.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 10 列逐字相同**

| 表 | 列 | Go 侧 | RAG 侧 | 判定 |
|---|---|---|---|---|
| `follow_relation` | `id` | `bigserial`（PK） | `bigserial`（PK） | ✅ 一致 |
| `follow_relation` | `user_id` | `bigint` · `NOT NULL` | `bigint` · `NOT NULL` | ✅ 一致 |
| `follow_relation` | `target_id` | `bigint` · `NOT NULL` | `bigint` · `NOT NULL` | ✅ 一致 |
| `follow_relation` | `create_time` / `update_time` | `timestamp with time zone` · **可空** | `timestamp with time zone` · **可空** | ✅ 一致 |
| `block_relation` | 同上 5 列 | 同上 | 同上 | ✅ 一致 |

⇒ Go 出处（tag 逐条对应 `data_model.go:6-10` / `:18-22`）：`Id uint64 \`gorm:"primaryKey"\`` · `UserId int64 \`gorm:"column:user_id;not null;index;uniqueIndex:uk_follow_pair"\`` · `TargetId int64 \`gorm:"column:target_id;not null;index;uniqueIndex:uk_follow_pair"\`` · `CreateTime time.Time \`gorm:"column:create_time;autoCreateTime"\`` · `UpdateTime time.Time \`gorm:"column:update_time;autoUpdateTime"\``
⇒ ⚠️ **`create_time` / `update_time` 两侧都是 `timestamptz` 且都可空** —— 与 `users` 域不同：**本域 RAG 侧**没有加 `DEFAULT CURRENT_TIMESTAMP`（Go 的 `autoCreateTime` / `autoUpdateTime` 由 GORM 在 Go 代码里赋值 ⇒ 不建 DB 默认；RAG 侧照抄了这一形态）⇒ **本域不存在 `users` 域那种「DEFAULT 残留」**。

**② 索引 —— 8 / 8 逐条一致（**连名字都逐字相同**）**

| 表 | 索引名 | 唯一性 | 列 |
|---|---|---|---|
| `follow_relation` | `follow_relation_pkey` | unique | `id` |
| `follow_relation` | `idx_follow_relation_user_id` | non-unique | `user_id` |
| `follow_relation` | `idx_follow_relation_target_id` | non-unique | `target_id` |
| `follow_relation` | `uk_follow_pair` | unique | `user_id`, `target_id` |
| `block_relation` | `block_relation_pkey` | unique | `id` |
| `block_relation` | `idx_block_relation_user_id` | non-unique | `user_id` |
| `block_relation` | `idx_block_relation_target_id` | non-unique | `target_id` |
| `block_relation` | `uk_block_pair` | unique | `user_id`, `target_id` |

⇒ **两侧 `pg_indexes.indexdef` 逐字相同**（脚本输出「两侧索引定义逐条一致 ✓」）。
⇒ ⚠️ **这 4 条不是「名字不同但语义等价」，而是字面全同** —— 原因：RAG 侧迁移脚本**照抄了 GORM 的命名规则**（`uk_follow_pair` / `uk_block_pair` 是 struct tag 里**显式指定**的 `uniqueIndex` 名，不是按表名推导 ⇒ 两侧必然同名）。
⇒ **其中 `uk_follow_pair` / `uk_block_pair` 是行为载体、不是优化** —— Go 侧 INSERT 走 `ON CONFLICT (user_id, target_id) DO NOTHING`（RAG 侧对应 `FollowRelationMapper#insertIgnoreConflict`）⇒ 删掉这两条 ⇒ 重复关注/重复拉黑会从「静默成功」变成 `5008 database update failed`（迁移脚本 `V1:44-49` 的注释原文）。

**③ 唯一约束 / 主键 —— 2 / 2 逐条一致**

| 表 | 约束 | contype | 定义 |
|---|---|---|---|
| `follow_relation` | `follow_relation_pkey` | p | `PRIMARY KEY (id)` |
| `block_relation` | `block_relation_pkey` | p | `PRIMARY KEY (id)` |

⇒ 两侧**同表同名同定义** ⇒ 连「命名形态差」都没有。

**④ 源码 ↔ 活库 —— 0 差异（G-9 维度，本轮新增）**

- 目录扫描得到 **2 份**迁移脚本（`V1__create_follow_relation.sql` / `V2__create_block_relation.sql`）⇒ **清单由目录扫描产生**，不会落后于仓。
- 在**干净临时库** `rag_follow_db_srccheck` 上**原样重放**（交给 PG 自己解析）⇒ 与活库逐项对比：
  **表集合 2 ↔ 2** · **列集合 0 差异** · **列属性 0 差异** · **可空性 0 差异** · **DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致`
- ⇒ **含义**：本域仓里的 `.sql` 与**当前活库**之间**没有分叉** ⇒ 「迁移改了没人重跑」这一类漂移**在 `follow` 域为零**。

**⑤ 可空性 / DEFAULT —— 两侧完全一致（脚本输出「（两侧完全一致 ✓）」）**

⇒ `users_id`/`target_id` 两侧都 `NOT NULL`；`create_time`/`update_time` 两侧都**可空**；`id` 两侧都由 PG 的 `bigserial` 序列供值（`nextval`）⇒ **无序列名映射副作用**（两侧表名相同 ⇒ 序列名也相同 ⇒ 不存在 `users` 域那种「序列名映射的副产品」）。

### 16.4 残留

**本域残留 = 0 处。** ⇒ **无一条需要按 `docs/twoStage.md` §十 定级**。

⚠️ **「0 残留」不等于「全维度已核」** —— 下列维度**无对象可比**，其结论是「**无对象可比**」，不是「已对齐」（§16.6）：

| 维度 | 状态 | 说明 |
|---|---|---|
| 触发器集合 | ⚪ 两侧各 0 | 真源 `service/follow/**` 全域无 `CREATE TRIGGER`；RAG 侧两份迁移脚本也未建触发器（`grep` 命中 0）⇒ **本域不存在触发器语义漂移的可能** |
| 触发器形式 / 函数体结构化属性 | ⚪ 两侧各 0 | 同上 ⇒ **脚本自己输出「本维度什么都没比」** |
| CHECK / 外键 / 分区 / 存储过程 | ⚪ 人工补查 ⇒ 两侧各 0 | 见 §16.6 的效力边界表 |

**签字状态**：`docs/Intentional_Deviations/follow/` 下 `api.md` / `common.md` / `proto.md` / `rpc.md` **四份全是 0 行空占位** ⇒ **本域零签字偏离** ⇒ 本节**没有**「已签字 ⇒ 不要重复质疑」的条目需要回避，也**没有**「已登记待签字候选」条目。
⚠️ **空占位 ≠「已签字」**（`docs/Pitfalls/users/admin/rpc.md` **P-9**）—— 这里只是说「**本域没有需要按签字档回避的差异**」。

### 16.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：`service/follow/follow-rpc/**` 的**代码**由本波次的另一条 lane 在改（AGENTS.md §5.3d 并发纪律）⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**（任务书允许的「**临时副本**」路线）：把 `check-ddl-static.py` 与该域的 `db/migration/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ `REPO_ROOT` 与仓内布局一致），**逐字节校验 sha256 相同**，再在**副本**上做变异。
> ⇒ 守门读到的**仍是仓里那份 `.sql` 的内容**（同一份字节），只是不在仓里写。

**变异 ①：`follow_relation.user_id` 的列类型（`BIGINT` → `INTEGER`）**

| 步骤 | 命令 | 结果 |
|---|---|---|
| 基线 | `python3 tools/difftest/check-ddl-static.py --domain follow --indexes --check-source`（临时树） | **EXIT=0**（0 处）· 源码↔活库 = `✅ 一致` |
| 变异 | `V1__create_follow_relation.sql`：`user_id     BIGINT      NOT NULL,` → `user_id     INTEGER     NOT NULL,`（改**一个列的类型**，命中目标行 **1** 处） | — |
| 变异后重跑 | 同上命令 | **EXIT=1**（**1** 处）· 源码↔活库 **转红**：<br>`🔴 列属性不同：follow_relation.user_id  源码=('integer', '')  活库=('bigint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 改回 `BIGINT` | — |
| 还原后重跑 | 同上命令 | **EXIT=0**（**0** 处 · 回到基线）· 源码↔活库 回到 `✅ 一致` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **两侧同为 `21a437cf306632f7df845b588c9e2504b9c9d6f692f284d54b13cb75c4abee30`** ⇒ 逐字节回到变异前 |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`follow_relation.user_id`），**没有多红也没有漏红** ⇒ 守门**不是假绿**；还原后**精确回到 EXIT=0** ⇒ 无残留。
⚠️ 与 `users` 域不同，本域基线就是 **EXIT=0** ⇒ 「变异必红 ⇒ 还原必绿」的**两端都是干净的 0/1 往返**。

**变异 ②（`users` 域）：`users_user.status` 的列类型（`BIGINT` → `INTEGER`）** ⇒ 见 [`docs/DRIFT/users/vs-common.md` §72.5](../users/vs-common.md)。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-users-follow"
git status --short          # ⇒ 空
git diff --stat -- service/ # ⇒ 0 行
```

### 16.6 证据强度 · 效力边界 · 未能核到的部分

**证据强度 = L4（运行面 · 双侧活体）** —— 两侧各建一个**真 PG 16** 库（Go 侧跑真源自己的 `AutoMigrate`、RAG 侧跑仓里的真迁移脚本）⇒ 逐列 / 逐索引 / 逐约束 / 逐触发器 dump 后机器比对。
⚠️ 本节所有 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。

**效力边界（越界即失效）**

| # | 边界 | 对本域是否成立 |
|---|---|---|
| 1 | **`--check-source` 的重放引擎 ≠ 生产引擎** —— 重放用 **psql**；本域生产用 **Flyway 引擎**（`application.yml:77` `locations: classpath:db/migration`）⇒ 两个切句器不同 | ✅ **对本域不成立** —— 已核：两份 `.sql` 的 `$$` 命中 **0** ⇒ 无美元引号块 ⇒ 两个引擎看到的语句序列相同；且两份脚本只含 `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` |
| 2 | **迁移清单 = 生产清单** —— `discover_rag_sql` 目录扫描的 2 份，与生产 Flyway `locations` 指向的目录内容**逐条一致**（命令 ③ 与 ① 的执行日志互证） | ✅ **成立** |
| 3 | **Flyway 账本表** —— 生产库会有 `flyway_schema_history_follow`（`application.yml:79`），但**对拍环境直接用 psql 放 `.sql`** ⇒ 两个库里**都没有**账本表 ⇒ 本轮表集合就是 {2 张业务表} | ✅ **本轮无干扰**；⚠️ 换用真 Flyway 起环境时，账本表会出现在 RAG 侧 ⇒ 工具靠 `exclude_tables=("flyway_schema_history",)` 前缀匹配排除（**已配置**） |
| 4 | **`baseline-version: 0`**（`application.yml:76`）—— 生产拓扑（共享非空 schema）下 V1 **必须**执行，否则 `follow_relation` 建不出来 | ⚠️ **本轮未核** —— 对拍用的是**全新空库** ⇒ baseline 不触发 ⇒ V1 正常执行。⚠️ **这正是该配置注释里点名的陷阱**（「全新空库的测试测不出来」）⇒ **该维度的守门是 `FollowFlywaySharedSchemaGateTest`（真 PG 16 + 真 Flyway 引擎）**，不在本节范围 ⇒ **登记为「未核」** |
| 5 | **触发器函数体的语义**机器判不了 ⇒ 走人工逐条 | ✅ **不适用**（两侧各 0 个触发器） |
| 6 | **`pg_constraint` 只比 `contype` + 定义文本，不解析外键的引用目标**（脚本文件头第 44–45 行） | ✅ **不适用**（本域两侧 `contype='f'` 各 **0 行**） |
| 7 | **分区 / 存储过程 / 触发器函数** —— 脚本不查 | ✅ **不适用**（两份 `.sql` 的**可执行部分**无 `PARTITION` / `PROCEDURE` / `FUNCTION` / `CREATE RULE` / `CREATE SEQUENCE` 关键字 ⇒ 已人工核） |

**未能核到的部分（明确写「未核」+ 原因，不含糊）**

| # | 未核项 | 原因 | 影响 |
|---|---|---|---|
| 1 | **触发器形式 / 触发器函数体结构化属性** | 本域两侧**各 0 个触发器** ⇒ 无可比对对象 ⇒ 脚本自己输出「本维度**什么都没比**」 | 本域**不存在**触发器语义漂移的可能；但这**不等于**「触发器已对齐」 |
| 2 | **CHECK / 外键 / 分区 / 存储过程** | 脚本不查（外键只比 `contype` + 定义文本）⇒ **人工补查**后两侧均为 0 行 ⇒ 该维度**人工闭合** | 无残留；但结论强度 = **L4 + 人工补查**，不是脚本原生结论 |
| 3 | **共享库拓扑下的 Flyway `baseline-version: 0` 行为** | 对拍用**全新空库** ⇒ baseline 不触发 ⇒ 该配置**未被本节验证** | ⚠️ **该维度的守门是 `FollowFlywaySharedSchemaGateTest`（真 PG 16 + 真 Flyway 引擎）**（`application.yml:75` 明写）⇒ 本节**不重复核**，只登记边界 |
| 4 | **索引是否被真实查询计划选中** | 需要 `EXPLAIN` + 真实数据量 ⇒ 本轮**未核** | 本域两侧索引**定义全同** ⇒ 查询计划**无索引层面的差异来源**；实际代价未量化 |
| 5 | **Go 侧 schema 与生产的完全等价性** | 探针调的是真源 `model.InitDB`（`db.go:20` → `:42`）⇒ 与生产**同一份 struct tag + 同一个 gorm 版本**；且真源 `service/follow/**` **没有手写 `.sql`**（`ddl-parity.sh` 的 `GO_SQL` 清单对本域为空）⇒ **无遗漏** | 无缺口 |

### 16.7 与其它台账的关系（指针，不改别的文件）

| 台账 | 本节与它的关系 |
|---|---|
| [`docs/DRIFT/_cross/ddl-type-divergences.md` §四](../../DRIFT/_cross/ddl-type-divergences.md) | 🔴 **该文件对本域的一行已过期** —— §四「仍未覆盖」表把 `follow` 列在「**其余 5 域 … 均**无 DDL 探针** ⇒ 这 5 域的表结构**从未做过双侧逐列对照**」⇒ **已被 2026-10-25 L3 那轮实测推翻**（`open-items.md` P2 行记着 `follow` **0 差异** / 10 列 / 8 索引 / 2 约束），**本轮独立复跑并新增了 `--check-source` 与触发器两个维度** ⇒ 结论一致。⚠️ **该文件在 `_cross` ⇒ 本 lane 无写权 ⇒ 只在此登记，报给收口执行者** |
| [`docs/DRIFT/_cross/open-items.md` P2 行](../../DRIFT/_cross/open-items.md) | 该行记「`follow` **0 差异**（10 列 / 8 索引 / 2 约束）」⇒ 与本节**逐条一致**（本轮为**独立复跑 + 新增源码↔活库维度 + 变异验证**） |
| [`docs/DRIFT/follow/rpc.md`](rpc.md) · [`common.md`](common.md) | 本域**组件级**台账；本节是 **DDL 这一维度**的域级落账（域内 DDL 差异**本节为 0**）⇒ 与那两份台账的其它维度结论**互不冲突** |
| [`docs/Intentional_Deviations/follow/`](../../Intentional_Deviations/follow/) | 四份**0 行空占位** ⇒ **本域零签字偏离** ⇒ 本节没有需要回避的已签字条目 |
| [`docs/PhaseGoal-judge.md` §七 **G-8 / G-9**](../../PhaseGoal-judge.md) | 主线结论（G-8 / G-9 已闭合 · 9 域逐域跑）—— 本节是 `follow` 域的**域级落账** |

---

---

## 17. 🆕 **本轮结论（2026-10-29 · G1 lane · `S1-FOLLOW-001` 落地）**

> 本节是**本轮**（DRIFT-FOLLOW-006 落地）的结论；上一轮的回执见 §16 及之前的章节（§16 = DDL 逐域机器核）。
> ⇒ 逐条证据、逐行比对、差异定级、变异必红 ⇒ [`rpc.md`](rpc.md) §「DRIFT-FOLLOW-006 的落地（2026-10-29）」。

### 17.1 本轮做了什么（**只做 follow 域**）

| # | 落点 | 改动 | 依据 |
|---|---|---|---|
| 1 | `service/follow/follow-rpc/pom.xml` | 加 `spring-boot-starter-web`（**BOM 管版本，不写 `<version>`**） | `spring-boot-starter-actuator` **不给 servlet 容器** ⇒ `management.endpoints.web.*` 一直是**死配置**（**不报错、不生效**）⇒ 零抓取出口。**Boot 4 拆模块核对**：`WebEndpointAutoConfiguration` **仍在** `spring-boot-actuator-autoconfigure:4.0.8` ✔ ⇒ 唯一缺的就是 servlet 容器 + Spring MVC |
| 2 | `service/follow/follow-rpc/src/main/resources/application.yml` | 加 `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}` | Go `service/follow/rpc/etc/follow.yaml:23` `DevServer.Port: 16065` ⇒ **端口一字不差** |
| 3 | 同上 | 加 `spring.cloud.nacos.discovery.register-enabled: false` | 自动注册注册的是 **web 容器端口** ⇒ 会把「观测口」注册成 `follow-rpc` ⇒ `follow-api` 连过去 ⇒ **gRPC 链路断**（业务码塌成 1015）；本模块注册是**手写**的（`bootstrap/NacosRegistration.java:128` 用 `GrpcServerProperties.getPort()`）⇒ 与 Go `zrpc.MustNewServer` 注册 `ListenOn` 同语义 |
| 4 | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/metrics/FollowRpcMetrics.java` | 直方图改用 **`serviceLevelObjectives(...)`** 逐个钉 Go 的 `DefBuckets`（**不再用 `publishPercentileHistogram()`**） | Go `client_golang@v1.23.2/prometheus/histogram.go:271` `DefBuckets`（由 `service/follow/rpc/internal/metrics/metrics.go:23` 消费）⇒ 修前 RAG 侧用 Micrometer 自己的 **68 个 HdrHistogram 桶** ⇒ 与 Go **零重合** ⇒ 同一段 `histogram_quantile()` 两侧结果不同 ⇒ **不是表达层差异** |

### 17.2 双侧活体对拍结论（**L4**）

| 项 | 结果 |
|---|---|
| **RAG 侧能不能抓到 `follow_rpc_*`？** | ✅ **能** —— `/actuator/prometheus`（**观测端口 16065**，与 Go 同口） |
| **行数** | Go **147** 行 / RAG **155** 行 `follow_rpc_*` |
| **series 集合差异** | **仅 Go 有 0 条**；**仅 RAG 有 8 条**（全是 `follow_rpc_request_duration_seconds_max{method=…}`）；**值差异 0 条** |
| **请求矩阵** | **28 步**（18 步业务 + 10 步故障注入）⇒ 两侧 gRPC 状态码 + 响应体**逐条一致** |
| **桶边界** | ✅ **逐个相同**（仅 `1`/`1.0`、`5`/`5.0`、`10`/`10.0` 的**文本**差异 ⇒ Prometheus 按浮点解析 ⇒ 语义等价） |

### 17.3 残余差异定级（`docs/twoStage.md` §10）—— **全部 D 类**

| # | 差异 | 严重度 | 阶段归属 |
|---|---|---|---|
| 1 | RAG 多 8 条 `…_seconds_max`（Micrometer 的 Timer max；Go 不导出） | P3 | **D 类** |
| 2 | 暴露路径不同（Go `/metrics` ↔ RAG `/actuator/prometheus`，**端口相同**） | P3 | **D 类** |
| 3 | 载体不同（Go `http.ListenAndServe` ↔ RAG Tomcat，**RAG 无任何业务 HTTP 路由**） | P3 | **D 类** |
| 4 | `le` 文本 `1` vs `1.0` 等 | P3 | **D 类** |
| 5 | Go DevServer 另有 `/debug/pprof/*`（`follow.yaml:27`） | P3 | **D 类** |

⇒ **一条 A/B/C 类都没有** ⇒ **DRIFT-FOLLOW-006 关闭（CLOSED）**。

### 17.4 守门与变异

* 探针：**`tools/metrics-probe/follow/run.sh`** ⇒ `EXIT=0` 绿 / `EXIT=1` 守门失效 / `EXIT=2` 环境问题。
* **变异 ①（改实现）**：摘掉 `serviceLevelObjectives(...)` ⇒ RAG 侧 `_bucket` **一条不剩** ⇒ **`EXIT=1`**（报「期望 8 / 实际 104」）⇒ 还原后 **`EXIT=0`**。
* **变异 ②（改期望值）**：往 `expected-diff.txt` 加一条不存在的差异 ⇒ **`EXIT=1`**（报「- 期望有、实际无」）⇒ 还原后 **`EXIT=0`**。
* ⚠️ **cleanup 实测无残留**：探针跑完后 `18171-18180` **无端口占用**、**无探针容器**、**无进程**、**Nacos 实例已注销**。

### 17.5 本轮**没做**的（明确边界）

* **另外 5 个 rpc 组件**（`favorite-rpc` / `message-rpc` / `like-rpc` / `user-user-rpc` / `user-admin-rpc`）
  仍是「Go 有 DevServer、RAG 零出口」⇒ **跨域派单保持原样**（本 lane 不碰域外文件）。
* **共享层 `service/common` 的 `ObservabilityLifecycle` 关闭路径引用 `jakarta.servlet.Filter`**
  ⇒ 本轮 follow-rpc 因引入 servlet-api 而**不再复现**，但**共享层代码未动**（**G4 写权**）
  ⇒ 其它仍无 servlet 的 rpc 组件**仍会踩** ⇒ **只登记，不在本条结案**。
* **不做阶段二**（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7）；**不改共享索引**（`docs/DRIFT/_cross/**` / `AGENTS.md` / 三个 `_INDEX.md` / `docs/PhaseGoal-judge.md`）⇒ **交合并收口执行者**。
---

## 18. 🆕 **2026-10-29 真实性复核回执**（W-B lane · 「台账结论 ↔ 代码」逐条复核 · follow 域）

> **口径**：每条带编号的结论 → 回 **Go 真源（只读）** ＋ **RAG 源码** 两侧核「被描述的那个行为现在还在不在」，
> 再核「证据指针那一行是不是真的在讲这件事」（**指针能解析 ≠ 结论为真**）。
> **原值一律保留**；订正以 ⚠️ 段**追加**（本节只汇总，明细订正在各条正文）。
> **机器判据**：`python3 tools/difftest/ledger-claim-audit.py --domain follow` ⇒ **🔴 落空 = 0**（唯一 1 处真落空 **`/tmp/probe.sh` 的第 3 行** 已按 §三·5 处理：标注「一次性临时探针、不随仓留存 ⇒ 本机不可核验」并改指仓内可复算落点）。

### 18.1 逐条结论表（编号 · 原状态 · 现行态 · 依据 · 是否改判）

| 编号 | 原状态 | 现行态 | 依据（两侧 `文件:行号` · 核对日期 2026-10-29） | 改判 |
|---|---|---|---|---|
| `DRIFT-FOLLOW-001` | ✅ 已修 | ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/service/FollowApiSupport.java:265` = `out.setUserIds(null);` ＋ `…/FollowApiMvcPipelineTest.java:625-626` = 断言 `"user_ids":null` ⇒ Go `service/follow/api/internal/logic/follow/getrecommendationslogic.go:70` = `users := make([]types.RecommendUser, 0, len(rpcResp.Users))` ⇒ 空 repeated 在 wire 上消失 ⇒ Go 得 nil | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-005` | ✅ 已修 | ✅ 已修（不变） | RAG `…/follow-api/…/dto/RecommendUser.java:57` = `@JsonPropertyOrder({"target_id", "mutual_score"})` ＋ Go `service/follow/api/internal/types/types.go:20-22` = `TargetId`（`:21`）在 `MutualScore`（`:22`）之前 ⇒ 字段顺序对齐 | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-007` | 🟡 | 🟡（不变：已修 tag + 桶 · 残留 80 桶） | RAG `…/follow-api/…/metrics/FollowApiMetrics.java:131` = `.publishPercentileHistogram()` ＋ `:132` = `.serviceLevelObjectives(GO_DEF_BUCKETS)` ⇒ **api 侧仍 80 桶**（残留成立）· RAG `…/follow-rpc/…/metrics/FollowRpcMetrics.java:134` = **只用** `.serviceLevelObjectives(` ⇒ rpc 侧 12 桶 ⇒ 台账自述的「前提待复核」本轮**坐实** | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-008` | ✅ 已移出 DRIFT 侧（签字偏离） | ✅ 仍成立（不变） | RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:67-72` = javadoc 明写「经签字批准的有意偏离 ⇒ 落点 `SD-2026-10-25-01`」＋ `:102,106` = 全进程一个 `Semaphore` ⇒ 与 Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go:20`（`latch := syncx.NewLimit(n)`，**每路由一个**）的差异**仍在两侧** ⇒ **签字偏离仍成立**（不是缺陷） | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-009` | ✅ 已修 | ✅ 已修（不变）**＋ 指针订正** | RAG `…/follow-api/…/client/FollowRpcClient.java:164` = `CLIENT_TIMEOUT_MILLIS = 2000L` ＋ `:247` = `capByClientTimeout(` ⇒ 2s 客户端超时在位 | **指针订正**（`go-zero@v1.10.2/zrpc/config.go:34` ⇒ `:31`）（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-010` | ✅ 已修 | ✅ 已修（不变） | RAG `…/follow-api/…/auth/GoStyleJwtAuthFilter.java:100` = `@Order(Ordered.HIGHEST_PRECEDENCE + 6)` ＋ 共享层 `service/common/src/main/java/com/rag/common/spring/HttpMiddlewareFilter.java:40` = `@Order(Ordered.HIGHEST_PRECEDENCE + 7)` ⇒ **JWT 验签在 3s 预算之外** ⇒ 与 Go `go-zero@v1.10.2/rest/engine.go:101-113`（`buildChainWithNativeMiddlewares` → `appendAuthHandler` → `ng.middlewares` ⇒ 计时器在 Authorize 之后）的链序同形 | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-011` | ✅ 已修 | ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/resources/application.yml` = `timeout: 3s` ＋ `connect-timeout: 5s` ⇒ 与 Go 的 go-redis 库默认（`ReadTimeout 3s` / `DialTimeout 5s`）逐值一致；Go 侧 `go-zero@v1.10.2/core/stores/redis/redis.go:27` = `readWriteTimeout = 2 * time.Second` 确为**死常量**（台账的「别被它误导」成立） | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-012` | ✅ 已修 | ✅ 已修（不变） | RAG `…/follow-api/…/interceptor/FollowBlacklistInterceptor.java:155-157` = `catch (Throwable t) { … fail-open … }` ⇒ 断连**放行**；Go `service/follow/api/internal/middleware/checkblacklistmiddleware.go:40-41` = `if err == nil && exists` ⇒ **同形** | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-013` | 🔴 | **✅ 已修** | 见本文件 §12 与 `docs/DRIFT/follow/{api,rpc}.md` 的订正段（4 处盲选口逐处核过 + 三个端口段实测 + 全域 grep 零命中）<br>⚠️ **2026-10-30 复核补指针（follow+task lane）**：RAG `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowGrpcInterceptorWiringTest.java:115` = `ServerBuilder.forPort(RealGrpcPort.pick());` · `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowRpcSlowThresholdGateTest.java:274` = 同款 · `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiStartupFailFastTest.java:92` = `return RealGrpcPort.pick();` ＋ 同文件 `:258` = `NettyServerBuilder.forPort(RealGrpcPort.pick()).build().start();` ⇒ ⇒ **4 处全部已改**（本轮逐处重核）⇒ **现行态 = ✅ 已修（不变）**；核对日期 2026-10-30 | **是** |
| `DRIFT-MSG-V-030` | ✅ 已修 | ✅ 已修（不变） | RAG `service/follow/follow-api/pom.xml:196` = `spring-boot-maven-plugin` ⇒ 产物为可执行 jar | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `OPEN-001` | ✅ 已关闭 | ✅ 已关闭（不变） | RAG `…/follow-api/…/advice/GlobalExceptionHandler.java:219` = 「转换器已被共享组件覆盖 ⇒ 415 分支不可达」 | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-002` | ✅ 已修 | ✅ 已修（不变）**＋ 跨域子结论订正** | RAG `service/follow/follow-rpc/pom.xml:256-260` = grpc-inprocess **无** `<scope>test</scope>` ⇒ 域侧成立；但「域外 4 个模块仍是 `test` scope」**已陈旧**（见 `rpc.md` 订正段） | **子结论订正**（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-003` | ✅ 已修 | ✅ 已修（不变） | RAG `service/follow/follow-rpc/src/main/resources/application.yml:163` = `address: discovery:///user-rpc`（**三斜杠**） | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-004` | ✅ 已修 | ✅ 已修（不变） | RAG `service/follow/follow-api/…/application.yml:58` ＋ `service/follow/follow-rpc/…/application.yml:95` = **同一套** `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ⇒ 两端必指同一台 Nacos | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-006` | ✅ 已落地（🔴→✅ 2026-10-29） | ✅ 已落地（不变） | RAG `service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web` ＋ `…/application.yml:131` = `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}`（Go `service/follow/rpc/etc/follow.yaml:23` = `Port: 16065`）＋ `:112` = `register-enabled: false` ＋ 探针 `tools/metrics-probe/follow/run.sh` 存在（`:355` 就绪判据 = 真抓到 `/actuator/prometheus`） | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两；<br>`tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两；<br>`tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| `S1-FOLLOW-001` | 🟡 B 类（跨域方案待定） | **✅ 已落地** | 见 `docs/DRIFT/follow/stage1-admission.md` 的订正段<br>⚠️ **2026-10-30 复核补指针（follow+task lane）**：RAG `service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web` ＋ `service/follow/follow-rpc/src/main/resources/application.yml:131` = `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}` ＋ `:112` = `register-enabled: false` · Go `service/follow/rpc/etc/follow.yaml:23` = `Port: 16065` ⇒ **端口一字不差** ⇒ **现行态 = ✅ 已落地（不变）**；核对日期 2026-10-30 | **是** |
| `S1-FOLLOW-002` | ✅ 已签字偏离 ⇒ 不修 | ✅ 仍成立（不变） | 签字档 `docs/Intentional_Deviations/global/common-tails.md` §`SD-2026-10-25-01` 存在、「对应的 DRIFT 条目」栏明写 = T-2 = `DRIFT-FOLLOW-008` ⇒ 与本条一致<br>⚠️ **2026-10-30 复核补指针（follow+task lane）**：`docs/Intentional_Deviations/global/common-tails.md:11` = `## SD-2026-10-25-01：… ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**` ＋ `:18`（「对应的 DRIFT 条目」栏 = **T-2** = `follow` 域 `DRIFT-FOLLOW-008`）· RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:67` = 「⚠️ 本条是经签字批准的『有意偏离』」· Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go:20` = `latch := syncx.NewLimit(n)`（**每路由一个**）⇒ **差异仍在两侧 ⇒ 现行态 = ✅ 仍成立（不变）**；核对日期 2026-10-30 | 否 |
| `S1-FOLLOW-003` | 🔴 共享层待办 | **✅ 已修** | 见 `stage1-admission.md` 订正段（共享层 `HttpPoolLifecycle` 已落码 + follow-rpc 已补 servlet 容器）<br>⚠️ **2026-10-30 复核补指针（follow+task lane）**：RAG `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:53-54` = `@ConditionalOnWebApplication(type = …Type.SERVLET)` ＋ `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:85` = 「本方法**只碰** `RpcInterceptor`」＋ `:94` = 「HTTP 池改由 `HttpPoolLifecycle` 关 ⇒ **不在本方法里**」⇒ ⇒ **纯 gRPC 进程的关闭路径不再触及 web 类型**；＋ RAG `service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web` ⇒ **本域侧前提亦不成立** ⇒ **现行态 = ✅ 已修（不变）**；核对日期 2026-10-30 | **是** |
| `S1-FOLLOW-004` | 🟡 B 类 | 🟡 仍开放（不变） | RAG `docker-compose.yaml` 实测 **7 个 service**（postgres / redis / nacos / jaeger / prometheus / user-rpc / user-api）⇒ **无 follow 编排入口** ⇒ 仍开放<br>⚠️ **2026-10-30 复核补指针（follow+task lane）**：RAG `docker-compose.yaml:10` = `postgres:` · `:47` = `redis:` · `:65` = `nacos:` · `:88` = `jaeger:` · `:102` = `prometheus:` · `:125` = `user-rpc:` · `:199` = `user-api:`（**逐个重核**）⇒ **无 `follow-rpc` / `follow-api`** ⇒ **现行态 = 🟡 仍开放（不变）**；⚠️ **根级 `docker-compose.yaml` 不在本 lane 写权面 ⇒ 维持 🟡 B 类，不升级 🔴**；核对日期 2026-10-30 | 否 |
| `S1-FOLLOW-005` | ✅ 已修 | ✅ 已修（不变） | RAG `service/follow/follow-rpc/src/main/resources/application.yml:143` = `port: ${FOLLOW_RPC_PORT:8082}` | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `S1-FOLLOW-006` | 🟢 正面范例 | 🟢 仍成立（不变） | RAG `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/service/FollowBlockTransactionService.java:43` = 独立 bean ＋ `:70` = `@Transactional(rollbackFor = Exception.class)` ＋ `:24` = AOP 自调用陷阱注释 | 否（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-G1-001` | 🔴 | **✅ 已修** | 见本文件 §7.1 订正段（`NacosRegistration.java:104` 读配置） | **是**（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-G1-002` | 🔴 | **✅ 已修** | 见本文件 §7.2 订正段（`application.yml:129` = `immediate-connect-timeout: 3s`） | **是**（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两；<br>`tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两；<br>`tools/metrics-probe/follow/run.sh` ⇒ **EXIT=0**（2026-10-30 04:32:14 · 32s）—— **活体跑通**（探针自身断言全部成立）；覆盖断言：✅ 故障[follow_relation] Follow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[follow_relation] Unfollow 两侧一致：0\|rpc error: code = Internal desc = database update failed ⏐ ✅ 故障[block_relation] Block 两。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| `PITFALL-FOLLOW-005` | 🔴 未修 ⇒ 派 G4 | **✅ 已闭环** | 见本文件 §9.2 订正段 | **是**（核对日期 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |

### 18.2 仍未核到（**待核实**，不默认「仍生效」也不默认「已闭环」）

| 编号 | 为什么判不了 | 本轮做过什么 |
|---|---|---|
| `DRIFT-FOLLOW-007` 的**字节级残差**（`le="1.0"` vs `le="1"`、多 8 条 `_max`、80 vs 12 桶） | 属 **Prometheus 文本渲染层**，必须**真容器 + 真抓取**才能量到；本轮只做了源码级核对 | 源码级核对通过（`FollowApiMetrics.java:131-132` 两个开关同开 ⇒ 残留形态成立） |
| `S1-FOLLOW-001` 的**双侧活体对拍（L4）**结论 | 探针 `tools/metrics-probe/follow/run.sh` 需 **真 PG + 真 Redis + 真 Nacos + 真 Go 进程**（约 6 分钟起）⇒ 本轮**未重跑** | 已核：**脚本存在** ＋ 落码四要素齐 ＋ 端口/注册/桶三处逐行对齐 |
| `DRIFT-FOLLOW-009` / `-010` / `-012` 的**双侧活体**结论 | 同上（需起 Go 侧进程）⇒ 本轮**未重跑** | 已核：RAG 侧落码 + Go 侧源码逐行 |

### 18.3 本轮**只核台账、不改产品代码**（AGENTS.md §3.3）

⇒ 本 lane **未修改任何 `service/**` / `tools/**` 文件**；所有订正只落在 `docs/DRIFT/follow/**`（本文件 + `api.md` + `rpc.md` + `stage1-admission.md`）。


---

## 19. 🆕 **2026-10-30 真实性复核回执**（follow+task lane · 「台账结论 ↔ 代码」逐条复核 · follow 域 · 本文件）

> **口径**：同 [`api.md`](api.md) 的 2026-10-30 回执（Go 真源 + RAG 两侧逐条核 · 指针逐条核到「那一行确实讲这件事」）。
> **原值一律保留**；本轮 **2 条改判**（`S1-FOLLOW-010` / `S1-FOLLOW-011` ⇒ **✅ 已闭环**）＋ **若干指针补齐 / 订正**。
> **机器判据**：`python3 tools/difftest/ledger-claim-audit.py --domain follow --strict` ⇒ **🔴 落空 = 0**。
> ⚠️ **本轮额外做的机器判据抓不到的一层**：`ledger-claim-audit.py` 的 `_resolve()` 在**直连路径不存在**时会**退化成按 basename 匹配** ⇒
> ⇒ 「**指错文件**」会被判成 `resolved` ⇒ **机器绿灯 ≠ 指针指对文件** ⇒ 本轮对**每个指针的目录与行内容**都手工核过。

### 19.1 逐条结论表（编号 · 落点 · 原状态 → 现行态 · 两侧依据 · 是否改判）

| 编号 | 落点 | 原状态 → 现行态 | 依据（两侧 `文件:行号` · 核对日期 2026-10-30） | 改判 |
|---|---|---|---|---|
| `DRIFT-FOLLOW-002` | §1 `:27` | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-rpc/pom.xml:256-260` = `io.grpc:grpc-inprocess` **无 `<scope>test</scope>`** ⇒ **运行期 classpath 有该类**（**本轮重核**） | 否（核对日期 2026-10-30） |
| `DRIFT-HOTAPI-030` | §1 `:28` | ✅ 已落地 → ✅ 已落地（不变） | RAG `service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:170` = `MAX_PARAMETER_COUNT = 10001` ＋ 本域消费点 RAG `service/follow/follow-api/src/main/resources/application.yml:219` = `mode: envelope`（Go `service/follow/api/followcenter.go:53` 注册 `httpx.SetErrorHandler` ⇒ **同向**）⇒ **主轴已落地**（⚠️ **后续格（段数轴）仍 🔴 在 `global`/G4** ⇒ 本行只确认主轴） | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-008` | §1.1 `:37` | 🔴 未修（快照）→ ✅ 仍成立（不变） | 签字档 `docs/Intentional_Deviations/global/common-tails.md:11` ＋ `:18`（「对应的 DRIFT 条目」= **T-2** = `DRIFT-FOLLOW-008`）· RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:67` / `:102` / `:106`（**全进程一个 `Semaphore`**）· Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go:20`（**每路由一个**）⇒ **差异仍在两侧 ⇒ 签字仍有效**；⚠️ **指针订正**：users 侧 `docs/DRIFT/users/vs-common.md:2740` ⇒ **`:111`**（原行已指错行） | 否（**指针订正**）（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-002` | §1.1 `:38` | ✅ 已修 → ✅ 已修（不变） | 同 §1 `:27` ＋ 跨域半边**本轮复核属实**（全仓已无 `grpc-inprocess` 的 `test` scope） | 否（核对日期 2026-10-30） |
| `S1-FOLLOW-010` | §13.6 `:821` | 🔴 注释错 ⇒ 派 G4 → **✅ 已闭环** | RAG `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:80` = 「…全长 {@code 19} 字节」＋ `:83` = 「🔴 2026-10-29（R3 轮）订正：原写「全长 {@code 20} 字节」⇒ **错**」⇒ **所报缺陷已不存在** ⇒ **不再派 G4**；⚠️ **指针订正** `:83` → **`:80`** | **是**｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `S1-FOLLOW-011` | §13.6 `:822` | 🔴 注释/显示名错 ⇒ 派 G2 → **✅ 已闭环** | RAG `service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiRouterErrorRealTomcatTest.java:46` = 「…全长 **19** 字节」＋ `:48`（订正说明）＋ `:190` 的 `@DisplayName` = 「…= **19** 字节」⇒ **所报缺陷已不存在** ⇒ **不再派 G2**；⚠️ **指针订正** `:187` → **`:46` / `:190`** | **是**（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-001` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/service/FollowApiSupport.java:265` = `out.setUserIds(null);` ＋ `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiMvcPipelineTest.java:625-626` = 断言 `"user_ids":null` · Go `service/follow/api/internal/logic/follow/getrecommendationslogic.go:70` = `users := make([]types.RecommendUser, 0, len(rpcResp.Users))` ⇒ **proto3 空 repeated 在 wire 上消失 ⇒ Go 得 nil** ⇒ 两侧同形 | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-005` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/dto/RecommendUser.java:57` = `@JsonPropertyOrder({"target_id", "mutual_score"})` · Go `service/follow/api/internal/types/types.go:21` / `:22` ⇒ **字段顺序两侧同形** | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-007` | §18.1 | 🟡 → 🟡（不变：已修 tag + 桶 · 残留 80 桶） | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/metrics/FollowApiMetrics.java:131` = `.publishPercentileHistogram()` ＋ `:132` = `.serviceLevelObjectives(GO_DEF_BUCKETS)` ⇒ **api 侧仍 80 桶**（残留成立）· RAG `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/metrics/FollowRpcMetrics.java:134` = **只用** `.serviceLevelObjectives(` ⇒ rpc 侧 12 桶 ⇒ 台账自述的「前提待复核」**本轮坐实** ⇒ **🟡 不变**（**不因措辞升级成 🔴**） | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-008` | §18.1 | ✅ 已移出 DRIFT 侧 → ✅ 仍成立（不变） | 同 §1.1 `:37`（RAG `GoStyleMaxConnsFilter.java:67-72` / `:102,106` · Go `maxconnshandler.go:20`） | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-009` | §18.1 | ✅ 已修 → ✅ 已修（不变）**＋ 指针订正** | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/client/FollowRpcClient.java:164` = `CLIENT_TIMEOUT_MILLIS = 2000L` ＋ `:247` = `capByClientTimeout(` · Go `go-zero@v1.10.2/zrpc/config.go:31` = `Timeout int64 \`json:",default=2000"\``（**本轮逐行核过**：`:31`=`Timeout`、`:34`=`BalancerName` ⇒ 2026-10-29 的订正成立）⇒ **Go 2s / RAG 2s 同形** | 否（指针订正**沿用** 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-010` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/auth/GoStyleJwtAuthFilter.java:100` = `@Order(Ordered.HIGHEST_PRECEDENCE + 6)` ＋ 共享层 `service/common/src/main/java/com/rag/common/spring/HttpMiddlewareFilter.java:40` = `@Order(Ordered.HIGHEST_PRECEDENCE + 7)` · Go `go-zero@v1.10.2/rest/engine.go:101-113` ⇒ **验签在 3s 预算之外 ⇒ 两侧链序同形** | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-011` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/resources/application.yml:41` = `timeout: 3s` ＋ `:48` = `connect-timeout: 5s` · Go `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-39`（不传 timeout ⇒ 落 go-redis 库默认 5s/3s）⇒ **两侧逐值一致**；⚠️ Go `go-zero@v1.10.2/core/stores/redis/redis.go:27` = `readWriteTimeout = 2 * time.Second` **仍是死常量** | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-012` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/interceptor/FollowBlacklistInterceptor.java:155-157` = `catch (Throwable t) { … fail-open … }` · Go `service/follow/api/internal/middleware/checkblacklistmiddleware.go:40-41` = `if err == nil && exists {` ⇒ **同形（都 fail-open）** | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-013` | §18.1 | 🔴 → **✅ 已修**（不变） | RAG `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowGrpcInterceptorWiringTest.java:115` · `…/FollowRpcSlowThresholdGateTest.java:274` · `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiStartupFailFastTest.java:92` ＋ `:258` ⇒ **4 处盲选口全部已改**（**本轮逐处重核**）⇒ **✅ 已修** | 否（**指针补齐**）（核对日期 2026-10-30） |
| `DRIFT-MSG-V-030` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/pom.xml:196` = `spring-boot-maven-plugin` ⇒ **产物为可执行 jar**（本轮重核该行内容） | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-002` | §18.1 | ✅ 已修 → ✅ 已修（不变）**＋ 跨域子结论订正** | RAG `service/follow/follow-rpc/pom.xml:256-260` ⇒ 本域成立；**「域外 4 模块仍是 `test` scope」已陈旧**（本轮逐模块 `grep -A4 grpc-inprocess` 复核**属实** ⇒ 全仓已无 `test` scope） | 否（子结论订正**沿用** 2026-10-29）｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `DRIFT-FOLLOW-003` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-rpc/src/main/resources/application.yml:163` = `address: discovery:///user-rpc`（**三斜杠**）⇒ **本轮逐模块复核：全仓已无两斜杠** ⇒ 「域外 5 模块待修」这半边**已陈旧**（订正段见 [`rpc.md`](rpc.md)） | 否（**子结论订正**）（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-004` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-api/src/main/resources/application.yml:58` ＋ `service/follow/follow-rpc/src/main/resources/application.yml:95` = **同一串** `${NACOS_ADDR:nacos}:${NACOS_PORT:8848}` ⇒ **两端必指同一台 Nacos** | 否（核对日期 2026-10-30） |
| `DRIFT-FOLLOW-006` | §18.1 | ✅ 已落地（🔴→✅ 2026-10-29）→ ✅ 已落地（不变） | RAG `service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web` ＋ `service/follow/follow-rpc/src/main/resources/application.yml:131` = `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}` ＋ `:112` = `register-enabled: false` · Go `service/follow/rpc/etc/follow.yaml:23` = `Port: 16065` ＋ 探针 `tools/metrics-probe/follow/run.sh:355`（就绪判据 = 真抓到 `/actuator/prometheus`）⇒ **出口已在** | 否｜⚠️ **2026-10-30 真实性复核（复核面）**：本行即本轮逐条复核记录（原状态 → 现行态 · 两侧依据见本行）· 核对日期 2026-10-30 |
| `S1-FOLLOW-001` | §18.1 | 🟡 B 类 → **✅ 已落地**（不变） | 同上（RAG `follow-rpc/pom.xml:216` ＋ `application.yml:131` / `:112` · Go `follow.yaml:23`）⇒ **本域侧出口已落地**（⚠️ **全仓统一方案 + scrape job 仍是跨域面**） | 否（**指针补齐**）（核对日期 2026-10-30） |
| `S1-FOLLOW-002` | §18.1 | ✅ 已签字偏离 ⇒ 不修 → ✅ 仍成立（不变） | `docs/Intentional_Deviations/global/common-tails.md:11` ＋ `:18` · RAG `GoStyleMaxConnsFilter.java:67` / `:102` · Go `maxconnshandler.go:20` ⇒ **签字仍有效** | 否（**指针补齐**）（核对日期 2026-10-30） |
| `S1-FOLLOW-003` | §18.1 | 🔴 共享层待办 → **✅ 已修**（不变） | RAG `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:53-54` ＋ `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:85` / `:94` ＋ RAG `service/follow/follow-rpc/pom.xml:216` ⇒ **关闭路径不再触及 web 类型；本域侧前提亦不成立** | 否（**指针补齐**）（核对日期 2026-10-30） |
| `S1-FOLLOW-004` | §18.1 | 🟡 B 类 → 🟡 仍开放（不变） | RAG `docker-compose.yaml:10` / `:47` / `:65` / `:88` / `:102` / `:125` / `:199` = 7 个 service ⇒ **无 follow 入口** ⇒ **仍开放**（⚠️ 根级文件不在本 lane 写权面 ⇒ **维持 🟡 B 类**） | 否（**指针补齐**）（核对日期 2026-10-30） |
| `S1-FOLLOW-005` | §18.1 | ✅ 已修 → ✅ 已修（不变） | RAG `service/follow/follow-rpc/src/main/resources/application.yml:143` = `port: ${FOLLOW_RPC_PORT:8082}` · Go `service/follow/rpc/etc/follow.yaml:2` = `ListenOn: 0.0.0.0:8082` ⇒ **默认端口一字不变** | 否（核对日期 2026-10-30） |
| `S1-FOLLOW-006` | §18.1 | 🟢 正面范例 → 🟢 仍成立（不变） | RAG `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/service/FollowBlockTransactionService.java:42` = `@Service` ＋ `:43`（**独立 bean**）＋ `:70` = `@Transactional(rollbackFor = Exception.class)` ＋ `:24`（AOP 自调用陷阱注释）⇒ **结构性规避自调用陷阱** | 否（核对日期 2026-10-30） |
| `DRIFT-G1-001` | §18.1 | 🔴 → **✅ 已修**（不变） | RAG `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/bootstrap/NacosRegistration.java:104` = `@Value("${spring.cloud.nacos.discovery.group:DEFAULT_GROUP}")`（**注册方现在真的读配置**）＋ `service/follow/follow-rpc/src/main/resources/application.yml:97` = `group: ${NACOS_GROUP:DEFAULT_GROUP}`（**rpc 与 api 同源**）⇒ **两侧读同一个变量** ⇒ 原失配形态已消除 | 否（核对日期 2026-10-30） |
| `DRIFT-G1-002` | §18.1 | 🔴 → **✅ 已修**（不变） | RAG `service/follow/follow-api/src/main/resources/application.yml:129` = `immediate-connect-timeout: 3s`（**本轮重核该行内容**）⇒ 与 Go `zrpc.MustNewClient` 的 fail-fast 语义同向 ⇒ **✅ 已修** | 否（核对日期 2026-10-30） |

### 19.2 本轮新增/订正的证据指针（汇总）

| 落点 | 原指针 | 现行指针 | 性质 |
|---|---|---|---|
| §1.1 `DRIFT-FOLLOW-008` · §`DRIFT-FOLLOW-008`（[`api.md`](api.md)） | `docs/DRIFT/users/vs-common.md:2740` | **`docs/DRIFT/users/vs-common.md:111`**（＋ `:47`） | **指错行**（原行讲的是别的内容） |
| §13.6 `S1-FOLLOW-010` | `…/GoStyleRouterErrorResponses.java:83` | **`:80`**（`:83` 现指「订正说明」） | **指错行**（已被上游订正 ⇒ 原指针语义已变） |
| §13.6 `S1-FOLLOW-011` | `…/LikeApiRouterErrorRealTomcatTest.java:46` 与 `:187` | **`:46` / `:190`**（`:187` 现为空行） | **指错行**（已被上游订正 ⇒ 原指针语义已变） |
| §18.1 `DRIFT-FOLLOW-013` / `S1-FOLLOW-001` / `-002` / `-003` / `-004` | **无指针** | 分别补：`FollowGrpcInterceptorWiringTest.java:115` · `FollowRpcSlowThresholdGateTest.java:274` · `FollowApiStartupFailFastTest.java:92,258` ／ `follow-rpc/pom.xml:216` + `application.yml:131,112` + Go `follow.yaml:23` ／ `common-tails.md:11,18` + `GoStyleMaxConnsFilter.java:67,102` + Go `maxconnshandler.go:20` ／ `HttpPoolLifecycle.java:53-54` + `ObservabilityLifecycle.java:85,94` + `follow-rpc/pom.xml:216` ／ `docker-compose.yaml:10,47,65,88,102,125,199` | **补指针**（原本不可复算） |
| §1 `DRIFT-HOTAPI-030` · §1.1 `DRIFT-HOTAPI-030` · §1.1 `DRIFT-FOLLOW-002` | **无指针** / **无指针** | 补 `GoStyleTomcatParameterLimitCustomizer.java:170` + `follow-api/application.yml:219` + Go `followcenter.go:53` ／ 补 `follow-rpc/pom.xml:256-260` | **补指针** |

### 19.3 仍未核到（**待核实**，不默认「仍成立」也不默认「已闭环」）

| 项 | 为什么判不了 | 本轮做过什么 |
|---|---|---|
| `DRIFT-HOTAPI-030` 的**「后续格（段数轴）」**（共享层 `MAX_PARAMETER_COUNT` = 10001 vs Go 的 10000） | 落点**共享层** ⇒ **G4 写权**；且需真容器压测才能量 ⇒ **不在本 lane 写权面** | 已核**主轴**（共享层 6 类在位 + 本域 `mode: envelope` 与 Go 同向）⇒ **主轴 ✅；后续格仍 🔴 在 global/G4 ⇒ 本文件不写成「已对齐」** |
| `DRIFT-FOLLOW-006` / `S1-FOLLOW-001` 的**双侧 series 逐条一致** | 需**真 Go + 真 RAG 进程同时跑** ⇒ 本轮未起 | 已核**配置层**（端口 / `register-enabled` / starter-web / 探针就绪判据 `tools/metrics-probe/follow/run.sh:355,361`）⇒ 未独立改判 |
| `S1-FOLLOW-004`（compose 编排） | 根级 `docker-compose.yaml` **不在本 lane 写权面** | 已核**现状**（7 个 service ⇒ 无 follow 入口）⇒ 未独立改判 |
| `DRIFT-G1-002` 的**真启动 fail-fast 实测**（进程退出） | 需真 Nacos + 真进程 ⇒ 本轮未起 | 已核**配置层**（`application.yml:129` = `immediate-connect-timeout: 3s`）⇒ 未独立改判 |

## 阶段归属定级附录（2026-10-30 · Go 对照）

> 本节由 `docs/DRIFT/_cross/grading/*.md`（**U5-O 显式定级叠加层**）同步而来。
> **权威来源 = 叠加层**（本节是它的镜像，不另立权威）。
> **判据 = 逐条与 Go 源码对比**（[`docs/twoStage.md`](../../twoStage.md) §10 · A-D 阶段归属 + P0-P3 严重程度）。
> **纪律**：原值一律保留；本节只**追加**，不改任何既有结论。
> ⚠️ 本节是**派生层维护的小节** ⇒ 派生层扫描时**不把它当条目**（保留区机制）。
>
> ⚠️ **状态订正提示（2026-10-30 · Go 对照）**：本节「依据」列结论为
> **「差异已消失」**的条目，其台账**既有状态标记（如 🔴 未闭合）已过期**
> ⇒ **以本节为准**；按「原值一律保留」纪律，**既有标记不回改**。


### lane W28

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| `S1-FOLLOW-013` | D（差异已消失 ⇒ 本条无待实现内容） | P3 | 否 | Go `go-zero@v1.10.2/rest/router/patrouter.go:71-74`（路径未命中且方法不匹配 ⇒ `pr.handleNotFound(w, r)`）＋ `go-zero@v1.10.2/rest/router/patrouter.go:93-99`（`handleNotFound` ⇒ `pr.notFound` 或 `http.NotFound(w, r)`）＋ `go-zero@v1.10.2/rest/engine.go:261`（`chn.Then(http.NotFoundHandler())` ⇒ 标准库 NotFoundHandler）＋ Go 标准库 `net/http/server.go:2358`（`NotFound` ⇒ `Error(w, "404 page not found", 404)`）＋ `net/http/server.go:2337`／`:2351-2354`（`Error` 写 `Content-Type: text/plain; charset=utf-8` ＋ `X-Content-Type-Options: nosniff` ＋ `fmt.Fprintln` ⇒ body = `"404 page not found\n"` = 18 ＋ 1 = **19 字节**）＋ 真源 `service/follow/api/internal/handler/routes.go:16-63`（8 条 `/follow/v1` 路由注册 ⇒ 未命中路径落到上面的 404 出口）vs RAG `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiFailurePathMatrixTest.java:417`（断言消息现写「…body 带尾随换行、全长 **19** 字节」⇒ **错数 20 已不存在**）＋ `service/follow/follow-api/src/test/java/com/rag/follow/follow/api/FollowApiFailurePathMatrixTest.java:427-434`（注释自述 2026-10-29 二次订正 ＋ 为何躲过第一轮自查）＋ 域内复扫 `grep -rnE "[0-9]+ ?字节" service/follow --include=*.java`（无 head ／ 无分页）⇒ 7 行命中，其中「20 字节」只剩 `:428` 那句**订正说明**（描述旧版本）⇒ 域内零残留 ⇒ 差异已消失（该错数 2026-10-29 已二次订正）⇒ 且断言本身一直按 body 字面量比（`:416`）＋ Content-Length 按实际字节数比（`:427`／`:440`）⇒ **从未影响测试判据**，只是**文案面** ⇒ 本条无待实现内容 | 2026-10-30 |

### lane W7

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| PITFALL-FOLLOW-005 | D | P3 | 否 | Go `service/common/observability/http.go:36`（`NewHTTPMiddleware` —— 只被 **api 侧** `service/follow/api/followcenter.go:42` 等 7 个 api main 引用；**rpc 侧零引用** ⇒ Go 的纯 gRPC 进程**根本不存在**「关闭路径触及 web 类型」这回事）。RAG `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:52-54`（`@Component` + `@ConditionalOnWebApplication(type = SERVLET)` ⇒ **只有 servlet 进程才注册这个 Bean** ⇒ 纯 gRPC 进程里 HTTP 池的关闭路径根本不存在）＋ `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:85`（「本方法**只碰** `RpcInterceptor`」）＋ `:94`（「HTTP 池改由 `HttpPoolLifecycle` 关 ⇒ **不在本方法里**」）＋ 域侧前提也已变：`service/follow/follow-rpc/pom.xml:216`（`spring-boot-starter-web` ⇒ classpath 上已有 servlet-api）⇒ ⇒ `NoClassDefFoundError: jakarta/servlet/Filter` 的前提**两侧都不成立** ⇒ **差异已消失** ⇒ **D**：本条无待实现项（原「派 G4」是 2026-10-27 落码前的陈旧快照） | 2026-10-30 |

---

## 🟢 2026-10-30 轮 · G1 lane 落账（派工面 `wave=G1` × A/B 回源后分流）

> **落账口径**：本轮取件 = `docs/DRIFT/_cross/stage1-derivation.tsv` 的 **`wave` 列**（派工面）⇒ `wave` 含 `G1` × `stage ∈ {A,B}` = 19 条 ⇒ **状态仍开放 = 10 条** ⇒ 逐条按 `basis` **回源抽查**后分流（**不照抄标题** —— `docs/Worktree.md` §0-1 效力边界 ③）。
> **基线**：`do-my-own-gh` @ `bdac3607` · 分支 `align/g1-users-follow` · 工作树 `../RAG.worktrees/g1-users-follow`。
> **授权**：Jason 于 **2026-10-30 10:31** 在本 lane 会话内明示「**开始工作**」⇒ 范围见本文件 `## 开工前置回执` 的 **2026-10-30 轮 ⑤**。
> ⚠️ **本节不含**：阶段二 · 共享面（`service/common/**` + `service/users/users-common/**` + `tools/**` + `docs/DRIFT/_cross/**` + `AGENTS.md` + 三个 `_INDEX.md` + `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/**`）· 已签字偏离的重开。

### 1. 本轮落码（**follow 域自身 = 0 条**）

| 域 | 落码 | 理由 |
|---|---|---|
| **follow** | **0 条** | ① `DRIFT-FOLLOW-006` 已于 **2026-10-29** 落地并关闭（`S1-FOLLOW-001` ⇒ 观测口 **16065** = Go `DevServer.Port` **一字不差**）⇒ **不重复做**；② `S1-FOLLOW-001`（全仓 rpc 层指标出口统一方案）**用户 2026-10-24 已定交主会话拍板** ⇒ **不当新缺陷重提**；③ `S1-FOLLOW-004` / `S1-FOLLOW-007` / `S1-FOLLOW-008` / `S1-FOLLOW-009` **均落在他域 / 共享面 / 基线** ⇒ **只登记 + 上报**（§3）；④ `E7` 的残余（**32 条故障注入族**）**台账已定为阶段 2 建设项** ⇒ **不在阶段一范围** |
| **users**（同一棵工作树内） | **1 条** | `S1-USERS-004` 的**防退化护栏**（新增测试 `AvatarTransactionEntryProxyGateTest` · **5 条断言** · **M1 / M2 变异实测变红**）⇒ 完整记录见 [`docs/DRIFT/users/vs-common.md`](../users/vs-common.md) §1 ⇒ **事务边界本身一行未动**（台账 §4 明写「阶段 1 不动」） |

### 2. follow 域在 **共享层缺陷**里的落点（本域**只登记**，修法落 `service/common` ⇒ 转 G4）

| 编号 | follow 域的落点 | 共享层根因 | 本轮动作 |
|---|---|---|---|
| ✅ **`E65`**（**本体已闭合** · 2026-10-30 双侧活体逐键相等）⚠️ **【2026-10-31 订正】** 原判「**A 类**」**已过期**（权威行 = `docs/DRIFT/global/_README.md:430`「**E65 已闭合**」；另见主干 `5fc15de6`「把挂靠点从已闭合的 E65 解绑、改挂 `X-6` 轴②」）⇒ ⇒ **本域不因 E65 领新条目**；另有一条**独立**残余轴 = 客户端硬上界 2000ms ⇒ 归 `P2-T04`（域侧 yml）⇒ ⇒ **不与 E65 并回** | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/client/UserRpcClient.java:89-90` —— `userStub.getUser(...)`（**裸 stub** ⇒ 本类**不再**自己套客户端上界）⇒ 客户端硬上界改由**共享层**的出向 deadline 拦截器承担 ⇒ 域侧**只配参数** `rag.protection.outbound-deadline.cap-millis=2000`（**2026-10-31 · G1 lane · 域侧那半落码**）<br>⚠️ **【2026-10-31 订正】** 原写的 `:50` **落点一直是错的**（`:50` 是空 javadoc 行，复核者按它定位会落到 javadoc）⇒ ⇒ 已改指真实的出向调用点 `:89-90`（`:88` 是方法签名行；`6d04dc19` 删掉内联副本后，调用体上移到 `:89-90`）<br>**Go 侧对应锚点（2026-10-30 实测订正）**：`service/follow/rpc/internal/logic/helper.go:82` = `resp, err := svcCtx.UserRpc.GetUser(ctx, &userservice.GetUserReq{Uid: userID})` ⇒ **直接透传 handler 的 ctx** ⇒ 继承父 deadline | `service/common/.../RpcInterceptor.java` 的 `onHalfClose` **只绑 `DeadlineState`**、**从不绑** `GoStyleRequestDeadline` ⇒ `GoStyleRequestDeadline.applyTo`（`:110`）在 gRPC 路径是 **no-op** | 📤 **转 G4 反馈单**（**E65 本体已闭合 ⇒ 本单 2026-10-31 结案**；残余轴 = `P2-T04`，转 `docs/PhaseGoal/` 派工面）（7 项格式见 [`users/vs-common.md`](../users/vs-common.md) §3.1）⇒ ⚠️ **修法同时涉及共享层与域内代码 ⇒ `AGENTS.md` §3c「必须同一次提交」⇒ G1 不能单独改 follow-rpc 的裸 stub（会留半截）** |
| 🟡 **`E66`**（= `DRIFT-ARPC-010`） | follow 域的 Redis 调用点（与 comment / like / hot / task 同型） | 同上（`DeadlineState` / `GoStyleRequestDeadline` **都不覆盖 Redis**）⇒ **与 E65 同源** | 📤 **转 G4**（同上文件 §3.2）· **建议与 `E65` 一起立项** |
| 🟡 **`DRIFT-ARPC-009` 残留** | follow-rpc 的拦截器链（`GoStyleRpcProtectionChain.build(...)` ⇒ 只装 recover + breaker + shedding） | `service/common/.../GoStyleRpcProtectionChain.java:126-131` 的 `compose()` **缺 Stat + Prometheus 两层** ⇒ Go zrpc 框架级**默认全装**（`zrpc/internal/config.go:20-25`） | 📤 **转 G4**（同上文件 §3.3）⇒ **落点必在共享层**（10 个 rpc 域共用）⇒ **域侧不可自修**<br>✅ **2026-10-30 14:4x · G4 已落共享层半边**（Jason 裁定「**只落 Stat 的慢调用日志**」⇒ `RpcInterceptor` 的 close 路径一行 ＋ 两个私有辅助方法，**零新依赖、不动 `Composition`** ＋ 守门 `GoStyleSlowCallLogGateTest` 真 in-process gRPC 捕获 ⇒ 变异精确转红）⇒ ⇒ **follow-rpc 一并受益（零改动）** ⇒ **Prometheus 那层按 Jason 裁定不做** ⇒ ⇒ 残留 = ① Go 的 **Info 分支**（每请求打全量 body）**刻意不落** ② Go 的 Stat 装在 **breaker/shedding 之外**、RAG 在**最内层** ⇒ **慢调用判定口径有差异** ⇒ 转派单 `G4-15b`/`G4-15c`<br>🔴 **两处前提偏差订正**：Stat **不走** Prometheus（`core/stat/metrics.go:3-18` 无 prometheus 依赖 ⇒ 进程内聚合器）＋ 慢阈值 500ms **不是** `StatConf.SlowThreshold`（真源所有 yaml 无 `Middlewares:` 块 ⇒ 恒零值 ⇒ 走全局兜底）⇒ **数值等价、机制不同** |

### 3. 域外 / 他域 ⇒ **只登记 + 上报**（follow 域 6 条 · 本轮 0 行改动）

| 编号 | 事项 | 落点 | 为何不在 G1 写权面 | 本轮动作 | 核对日期 |
|---|---|---|---|---|---|
| `S1-FOLLOW-001` | 全仓 rpc 层指标出口统一方案 | `service/common` 之外的**各 rpc 组件** + 部署形态 | **用户 2026-10-24 已定「交主会话拍板」** ⇒ 不是 G1 单方可定 | 📤 **登记**（follow 那一半已于 2026-10-29 落地；**users 那一半本轮只订正被推翻的前提** ⇒ 见 §4） | 2026-10-30 |
| `S1-FOLLOW-004` | `follow` 域**无编排入口**（compose 只含 users 两个组件） | 根 `docker-compose.yaml` | 根文件**无人拥有**（G1/G2/G3/G4 白名单都不含）⇒ 归**合并收口执行者** | 📤 **登记上报** | 2026-10-30 |
| `S1-FOLLOW-007` | 基线仍把**已签字偏离**记成开放缺陷 | `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/g1.md` | **基线文件 + 机器生成物** ⇒ 归**合并收口执行者** | 📤 **登记**；⚠️ **风险实际已解除** —— `docs/PhaseGoal/g1.md:21` 的「开放项归属」列**已改为** `→ 非开放（已签字 · 不是缺陷）` ⇒ **派单指令已修正**（`DRIFT-FOLLOW-008` 已于 2026-10-25 经用户签字 ⇒ `SD-2026-10-25-01`）⇒ 只剩**台账侧登记未同步** | 2026-10-30 |
| `S1-FOLLOW-008` | `check-surface-parity.py` 两个**静默假绿**口子 | `tools/difftest/` | `tools/**` **不在任何 lane 的白名单** ⇒ 归**合并收口执行者** | 📤 **登记上报** | 2026-10-30 |
| `S1-FOLLOW-009` | 5 个 api 模块缺 `TESTCONTAINERS_RYUK_DISABLED` | article-api / comment-api / hot-api（**G3**）· like-api / task-api（**G2**） | **他域** ⇒ G1 只登记 | 📤 **登记上报 G2 / G3**（⚠️ 本域 `follow-api` **已自修**） | 2026-10-30 |
| `E7` | follow 域双侧活体对拍**未复验** | 域内探针 `tools/api-full-parity-probe/follow/` | **域内探针目录 `tools/<kind>/<域>/` 归对应域 lane** ⇒ **本 lane 可写** | 📤 **只登记** ⇒ **HTTP 层 20 条已 L4**（2026-10-29 Round-2 T-2.5 · 探针已落仓 · EXIT=0 · 三方逐字节一致）⇒ **残余 = 32 条故障注入族** ⇒ ⚠️ **故障注入装置（`blackhole3.py` 等）原在 `/tmp` 且已消失** ⇒ **「52/52」今天不可复算** ⇒ **把故障注入装置从 `/tmp` 提升为仓库资产 = 阶段 2 建设项**（台账 §`S1-FOLLOW-007` 邻近行已定为 B 类）⇒ **本轮不预支** | 2026-10-30 |

### 4. 本轮订正的一条**被推翻的前提**（零代码改动）

| 对象 | 原判 | 现行态 | 依据 |
|---|---|---|---|
| `docs/DRIFT/users/user/rpc.md:280` 的「指标**可抓取性**」行 | **EQUIVALENT**（两侧生产都不可抓）· Go 侧依据写「`DevServer 仅 dev/test 起，生产无端点`」 | 🔴 **OBSERVABLE DIFFERENCE** —— **Go 侧前提被推翻**：`go-zero@v1.10.2/core/service/serviceconf.go:77` `devserver.StartAgent(sc.DevServer)` 在 `SetUp()` 里**无条件**调用 ⇒ `:83-89` `initMode()` **不门控** DevServer ⇒ `internal/devserver/server.go:79-82` 只判 `c.Enabled` ⇒ `internal/devserver/config.go:5` `Enabled` **default=true** ⇒ `zrpc/server.go:63` `c.SetUp()` 走同一条路 ⇒ ⇒ **Go 生产就有** `/metrics` + `/healthz`（`service/user/user/rpc/etc/user.yaml:23-30` ⇒ **9094**）⇒ 而 RAG `application.yaml:155-167` ⇒ **零出口** | ⚠️ **本轮只订正判定、只登记，不落码** —— 修法 = 引入 `spring-boot-starter-web`（**纯 gRPC 进程 → 带 Tomcat ⇒ 部署形态变更**）+ 关 Nacos 自动注册（否则注册的是 web 容器端口）⇒ **属「全仓 rpc 层指标出口统一方案」** ⇒ **用户 2026-10-24 已定交主会话拍板** ⇒ 且**不在 `stage1-derivation.tsv` 的 `wave=G1` 开放行内** ⇒ **不在本波派工面**。⚠️ 落点归属已由 `docs/DRIFT/follow/rpc.md:330-336` 明确划归 **users 域**（**9094 / 9097**）。核对日期 2026-10-30 |

### 5. 本轮**不做**的 5 条（明确边界 · 逐条给了理由，避免下轮重开）

① **阶段二**（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7 —— 含 `E7` 的故障注入族装置提升）；② **共享面**的一切改动（`service/common/**` + `service/users/users-common/**` + `tools/difftest/**` + `docs/DRIFT/_cross/**` + `AGENTS.md` + 三个 `_INDEX.md` + `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/**` + `docs/Worktree.md`）；③ **`S1-FOLLOW-001` 落码**（**用户 2026-10-24 已定交主会话**）；④ **已签字偏离的重开**（`DRIFT-FOLLOW-008` / `SD-2026-10-25-01`）；⑤ **派生层重生成**（`tools/ledger-derive/derive-stage1.py` 的输出落 `docs/DRIFT/_cross/**` = **共享面** ⇒ **G1 不写** ⇒ 交**合并收口执行者**在合并后统一重跑）。
### 6. 🆕 **第二轮落码：follow-rpc 出向 user-rpc 的 2s 客户端超时**（2026-10-30 · G1 lane · 域侧自修）

> **零共享面触碰**（`service/common/**` ＋ `users-common/**` ＋ `docs/DRIFT/_cross/**` ＋ 三个 `_INDEX.md` ＋ `AGENTS.md` 全部**只读**）。
> **独立成立、不依赖 G4** ⇒ 与共享层那半边（rpc 进程的入站预算载体）**互不阻塞**。

#### 6.1 落码清单（1 个模块 · 1 处出向调用点）

| 模块 | 落码文件 | 改动 | 覆盖 |
|---|---|---|---|
| `follow-rpc` | `service/follow/follow-rpc/src/main/java/com/rag/follow/follow/rpc/client/UserRpcClient.java` | 新增 `CLIENT_TIMEOUT_MILLIS = 2000L` ＋ 包可见 `capByClientTimeout` ＋ `getUser` 改走它 ＋ **订正**旧注释 | **1 处**（`FollowUserExistenceService.java:73`） |

⇒ **follow 域本轮从「0 行代码改动」变成「1 个主类 + 1 个守门类」**（这是 follow 域第一次因超时口径落码）。

#### 6.2 Go 侧依据（本轮**亲自读过** · 逐条带行号）

| # | 出处 | 事实 |
|---|---|---|
| ① | `go-zero@v1.10.2/zrpc/config.go:31` | `RpcClientConf.Timeout int64 `json:",default=2000"` ` ⇒ **默认 2000ms** |
| ② | `go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25` | `context.WithTimeout(ctx, t)` ⇒ **取更早者** |
| ③ | `service/follow/rpc/etc/follow.yaml:37-41` | `UserRpc:` 段**只有 `Etcd`、没有 `Timeout`** ⇒ ⇒ 走 ① 的默认值 |
| ④ | `service/follow/rpc/internal/logic/helper.go:82` | `svcCtx.UserRpc.GetUser(ctx, &userservice.GetUserReq{Uid: userID})` ⇒ 出向调用**直接透传 handler 的 ctx**（**无 `depCtx`、无 otel 派生** ⇒ 该行曾被台账误描述，已订正并写入「订正记录 + 教训」段） |
| ⑤ | `service/follow/rpc/internal/logic/helper.go:84-89` | `NotFound` → `GRPCError(codes.NotFound, ErrorUserNotFound)`；**其余一切（含 `DeadlineExceeded`）→ `GRPCError(codes.Internal, ErrorServerCommon)`** |
| ⑥ | `service/follow/common/errmsg.go:38` / `:47` | `ErrorServerCommon = 5001` ／ `ErrorUserNotFound = 5101` |

⇒ ⇒ **Go 的失败在客户端侧被观测 ⇒ 下游拿到的是 `INTERNAL` + 业务码 `5001`**。

#### 6.3 ⚠️ 差异的准确边界（**不要读宽**）

RAG 侧下游 `user-rpc` **自己**带服务端 2s 超时（`service/users/user/user-rpc/src/main/resources/application.yaml:209` = `rpc-timeout: 2s`；接线在位由 `check-grpc-interceptor-wiring.py` = EXIT=0 实测确认）⇒ ⇒

| 形态 | Go（真源） | RAG **修前** | RAG **修后** |
|---|---|---|---|
| **① 请求到达下游、业务挂死** | 客户端 2s 收口 ⇒ `helper.go:84-89` ⇒ **`INTERNAL` + 5001** | **服务端** 2s 收口 ⇒ `DEADLINE_EXCEEDED` ⇒ `FollowUserExistenceService.java:86-91` 分支② ⇒ **`INTERNAL` + 5001** | 同左 ⇒ **与 Go 同形** |
| **② 请求没到达下游** | 客户端 2s 收口 ⇒ **`INTERNAL` + 5001** | **无任何客户端收口** ⇒ 只能由**服务端**兜底（`service/follow/follow-rpc/src/main/resources/application.yml:308` = `rpc-timeout: 2s`，管的是**入站**调用）⇒ ⇒ **形态②才是差异** | 客户端 2s 收口 ⇒ **`INTERNAL` + 5001** ⇒ **与 Go 同形** |

⚠️ **rpc 进程没有入站 ctx 预算载体**：`GoStyleRequestDeadline.begin` 的**生产**调用点全仓**只有 1 处** = `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:324` ⇒ **只在 api 进程** ⇒ **rpc 进程恒 no-op** ⇒ ⇒ 这里**只有「2s 上界」这一维** ⇒ ⇒ 与 Go 的 min(服务端 2s, 客户端 2s) **同值**。

#### 6.4 守门 ＋ 变异实测（AGENTS.md §3.3.3）

- **守门**：`service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/client/UserRpcClientDeadlineCapTest.java`（**3 条**）
  - `clientTimeoutConstantMatchesGoDefault` —— 常量 == 2000ms（对齐 ①）
  - `capProducesRealTwoSecondDeadline` —— **真**构造 stub ＋ **真**读回 `CallOptions.getDeadline()`
  - `downstreamHangs_clientTimesOutOnItsOwnDeadline` —— **真发一次调用**：起 **in-process 真实 gRPC server**，其 `GetUser` **故意不响应** ⇒ 客户端**真的阻塞** ⇒ 只能靠**自己套的 2s deadline** 收口 ⇒ 断言 **`DEADLINE_EXCEEDED`** ＋ 耗时 ≈2s（防「秒失败」被误判成「超时生效」）＋ `@Timeout(30s)`（防变异时挂死）
- **变异 M4**：把 `getUser` 的 `capByClientTimeout(userStub)` 改成 `userStub` ⇒ **用例挂死 ⇒ `@Timeout(30s)` 触发 ⇒ 红** ⇒ ⇒ **证明 `DEADLINE_EXCEEDED` 确实是那 2s cap 造成的**（不是别的机制兜的底）⇒ 还原后**逐字节比对一致 ＋ 复绿**（**2.094s / 3 条全绿**）。
- ⚠️ **域内先例**：follow-api 已有同款（`service/follow/follow-api/.../FollowRpcClient.java` 的 `capByClientTimeout(applyTo(stub))`，对应 `DRIFT-FOLLOW-009` 已落地）⇒ ⇒ **本轮是同款形状在 rpc 侧的补齐**，不另立形状。

#### 6.5 本轮实跑记录

| 命令 | EXIT | 说明 |
|---|---|---|
| `mvn -o -B -DargLine=-Xmx2g -DfailIfNoTests=false --fail-at-end -pl <G1 八模块> -am test` | **0** | **BUILD SUCCESS** · **17 模块** · **8:17** · G1 八模块 **907 条 / 0F / 0E / 0S** ⇒ 较上轮 **894** 增加 **13**（5 + 5 + 3）✅ |
| `python3 tools/difftest/check-wave-lane-ownership.py` 等 **8 项**轻量守门 | **全 0** | |
| `python3 tools/difftest/check-surface-parity.py --domain follow` | **0** | |
| `python3 tools/difftest/check-grpc-interceptor-wiring.py` | **0** | 顺带复核下游服务端 2s 超时在位 |
| `bash tools/difftest/check-proto-static.sh` | **0** | |
| `python3 tools/difftest/check-ddl-static.py --domain follow --indexes` | **0** | |
| `python3 tools/ledger-derive/derive-stage1.py --check` | **1** | ⚠️ **预期交接**（`_cross/**` = 共享面 ⇒ G1 不写） |
#### 6.6 🆕 **与 G4 共享层的叠加（2026-10-30 15:0x · 已逐行独立核实）**

> G4 已提交共享层半边：**commit `3d5b4924`**（`align/g4-service-common` · 未 push / 未开 PR）。

- **叠加语义**：`GoStyleOutboundDeadlineInterceptor.java:151-154`（请求载体剩余 → min）＋ `:157-159`（`cap-millis` → min，**默认 0 = 不启用** ⇒ **follow 无需开 yaml**）＋ **`:162-166`（调用点自带 deadline → `Math.min`，不覆盖）** ⇒ ⇒ **本轮 follow-rpc 的 2s 不会被顶掉 ⇒ 域侧那层保留** ⇒ ⇒ 叠加后 = **min(请求剩余, 调用点自带, cap-millis)** ⇒ **与 Go 的 `context.WithTimeout`（取更早者）同形**。
- **服务端侧**：`RpcInterceptor.java:302-303` ＋ **`:304`（min(服务端 2s, 客户端剩余)）** ⇒ 与 Go 服务端同形 ⇒ ⇒ **本域 §6.3「形态① 两侧同形」的结论被加强**。
- ⚠️ **复跑口径**：**G1 不能在自己的工作树合入 `3d5b4924`**（`service/common/**` 不在 G1 写权面 ⇒ 会被 `check-wave-lane-ownership.py` 判越界）⇒ ⇒ **本域 3 条守门的复跑由合并收口执行者在主干上做** ⇒ 本轮**不预支「已复跑」**。
### 🔴 订正小节：本域「Redis 写不绑 deadline」登记行的两处指针订正 + 命中面收窄（2026-10-30 15:5x · 调度员指出后 G1 自查）

> ⚠️ **本小节标题刻意不含任何编号 token** ⇒ 派生层不把它当条目候选 ⇒ 也不劫持任何既有编号的 `domain` / `wave` / `basis`。
> ⚠️ **本小节不新登记、不改编号** ⇒ E66 / `DRIFT-ARPC-010` 仍是那一条 ⇒ ⇒ **销账时不要当两条**。

**（1）本域那行 E66 登记的「Go 侧」写的是「同上」⇒ 指的是上一行 E65 的 gRPC 出向锚点 ⇒ ⇒ 指错层（张冠李戴）**

- 上一行 E65 的 Go 侧锚点是 `service/follow/rpc/internal/logic/helper.go:82`（`svcCtx.UserRpc.GetUser(ctx, …)` ⇒ **gRPC 出向**）⇒ ⇒ **它撑不了 Redis**（Redis 是另一条链路）⇒ ⇒ **本行不能靠「同上」继承它**。
- ⇒ **本域 E66 的 Go 侧指针应为（Redis 侧）**：① `service/user/admin/rpc/internal/logic/logoutlogic.go:38`（`l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))` ⇒ **写绑 ctx**）② `go-redis/v9@v9.20.0/internal/pool/pool.go:1099-1104`（`waitTurn` fast path **无条件先查 `ctx.Done()`** ⇒ **命令根本不下发**）③ 同文件 `:837`（`getConn` **必调** `p.waitTurn(ctx)` ⇒ **派发前就拦住**）⇒ ⇒ **「Redis 写不绑 deadline ⇒ E66 是真漂移」这个结论不依赖任何 gRPC 锚点**。
- ⚠️ **为什么本轮不直接改那一行**：该行首格含编号 token ⇒ **它本身就是派生层的条目候选** ⇒ 改它的文字会连带改该候选的 `domain` / `wave` / `basis` ⇒ ⇒ **G1 不越界动派生层归属** ⇒ ⇒ **只在此登记订正内容**，由**收口执行者**在重生成时统一改（落点：`docs/DRIFT/_cross/**` = 共享面）。

**（2）命中面收窄：本域命中的是 E66 的「读」这一半，不是「写」那一半**

- E66 的本体是「Redis **写**不绑 deadline」（admin `logout` 的黑名单写入）⇒ ⇒ **本域没有 Redis 写调用点**（实测：`service/follow/**` 的 `main` 代码里 Redis 调用仅 3 处命中，**全在 `follow-api` 的 `FollowBlacklistInterceptor`** ⇒ 是**读**黑名单）。
- ⇒ ⇒ 本域命中的是**读**这一面：`FollowBlacklistInterceptor` 的 `redisTemplate.hasKey(blacklistKey)`（`follow-api/src/main/java/com/rag/follow/follow/api/interceptor/FollowBlacklistInterceptor.java:154`）⇒ **不绑 ctx** ⇒ 与 Go 侧 `service/follow/api/internal/middleware/checkblacklistmiddleware.go:40` 的 `m.Redis.ExistsCtx(r.Context(), blacklistKey)`（**绑 HTTP ctx**）形成对照。
- ⇒ ⚠️ **但读侧的后果与写侧不同**：Go 的判据是 `:41` 的 `if err == nil && exists` ⇒ **fail-open**（Redis 不可达 ⇒ 放行）⇒ RAG 侧同样 fail-open（`FollowBlacklistInterceptor.java:155-158` 刻意吞异常 ⇒ `exists = null` ⇒ 放行）⇒ ⇒ **fail-open 语义两侧同形**；读侧的差异（超时/慢）**归属 follow api 域自己的 Redis 条目**，**不在 E66 的「写」半边** ⇒ ⇒ **本域不因 E66 领新条目**。
### 🟡 订正小节：rpc 层「指标抓取出口」必须**分三层**看 —— 配置面 / 载体面 / 可抓取面（**口径源 = `docs/DRIFT/global/vs-common.md` §4.19.23** · 2026-10-31 · G1 lane 订正）

> ⚠️ **本小节标题刻意不含任何编号 token** ⇒ 派生层不把它当条目候选 ⇒ 也不劫持任何既有编号的 `domain` / `wave` / `basis`。

**（1）为什么必须分三层**

- **配置面（L1）** = yml 的 `management:` 段**没有把 prometheus endpoint 关掉**：`endpoint.prometheus.enabled` ≠ false ∧ `endpoints.enabled-by-default` ≠ false ∧ `endpoints.web.exposure.include` 含 `prometheus` ⇒ ⇒ **实测 10/10**（10 个 rpc 域的 `exposure.include` 全含 `prometheus`；**无一个**被 `enabled-by-default:false` / `endpoint.prometheus.enabled:false` 关掉）。
- **载体面（L2）** = **这个进程真能不能起 HTTP 容器** ⇒ 判据 = 部署 classpath **同时**含 `jakarta.servlet.Servlet` ＋ `org.springframework.web.context.ConfigurableWebApplicationContext` ⇒ `WebApplicationType.deduceFromClasspath()` 推出 **SERVLET** **∧ 未被 `spring.main.web-application-type: none` 关掉** **∧ 真有 servlet 容器实现**（`tomcat-embed-core` / `jetty-server` / `undertow-core`）⇒ ⇒ **没有载体 ⇒ 配置写得再对，抓取方也连不上**。
- ⚠️ **只报配置面 ⇒ 会得出「10/10 都有出口」的假结论**（**配置面 ≠ 可抓取面**）⇒ ⇒ **这是「配置里写了 ≠ 开得了」在指标出口这一面上的同族**。

**（2）三层判据（判据 = 运行时语义，不是文本命中；逐层可复算）**

- **判据与逐域清单 = `docs/DRIFT/global/vs-common.md` §4.19.23**（**唯一口径源** · 2026-10-31 · 合并收口执行者实测）⇒ ⇒ **本节只引用、不复述判据**（两处各写一套判据 ⇒ 正是这个数分叉三次的成因）⇒ ⇒ 复算入口 = `javap -p -c -cp ~/.m2/.../spring-boot-4.0.8.jar org.springframework.boot.WebApplicationType`（**读常量、不推理**）＋ `mvn -o -B dependency:list -pl <模块>`（**不是 grep pom**）。
- ⚠️ **为什么不用 grep pom**：`service/common/pom.xml:361` 的 `spring-boot-starter-web` 是 **`test` scope ⇒ 不传递**；但在父子继承场景下「grep 不到子模块 pom」**不构成**「运行时没有」的等价 ⇒ ⇒ 必须查依赖树（AGENTS.md §3.3.1：配置/声明 ≠ 实际生效）。⚠️ **且依赖树里有 `tomcat-embed-core` 只是三条件之一**（另两个 = `spring-web` 指示类在 classpath 上 ＋ 未被 `web-application-type: none` 关掉）⇒ ⇒ **单看「依赖树里有 tomcat」就判「有载体」不成立**。

| 组件 | L1 配置面 | L2 载体面 | L3 可抓取面 | 备注 |
|---|---|---|---|---|
| `article-rpc` | ✔ | ✔ | ✔ | |
| `favorite-rpc` | ✔ | ✔ | ✔ | |
| `follow-rpc` | ✔ | ✔ | ✔ | 载体端口 16065 |
| `hot-rpc` | ✔ | ✔ | ✔ | |
| `like-rpc` | ✔ | ✔ | ✔ | |
| `message-rpc` | ✔ | ✔ | ✔ | |
| `users/admin/admin-rpc` | ✔ | ✔ | ✔ | 载体端口 9097 |
| `users/user/user-rpc` | ✔ | ✔ | ✔ | 载体端口 9094 |
| `comment-rpc` | ✔ | ✘ | ✘ | 显式 `spring.main.web-application-type: none` ＋ 产物无 servlet 容器实现 ⇒ yml 的 `exposure.include` 是**死配置** |
| `task-rpc` | ✔ | ✘ | ✘ | classpath 无 `jakarta.servlet.Servlet` ⇒ `WebApplicationType` 推出 NONE ⇒ yml 的 `exposure.include` 是**死配置** |

⇒ ⇒ **三层口径 = 配置面 10/10 · 载体面 8/10 · 可抓取面 8/10** ⇒ ⇒ **无载体 / 不可抓 = 2 域**（`comment-rpc` 显式 `web-application-type: none` ＋ 产物无容器实现 · `task-rpc` classpath 无 servlet 指示物）⇒ ⇒ 逐格见 `docs/DRIFT/global/vs-common.md` §4.19.23（**唯一口径源**）⇒ ⇒ ⇒ **本表原写的 9 行「无 web starter / 无 tomcat」已作废**（那 9 行是「依赖树有 tomcat ⇒ 有载体」这个**已判不成立的判据**的产物）。

**（3）叠上载体面后的净结论（Go 侧逐域实测 · 2026-10-31 订正）**

- **配置面 = 10/10**（逐域读 `management:` 段实测：10 域 `exposure.include` 全含 `prometheus`，**无一个**被 `enabled-by-default:false` / `endpoint.prometheus.enabled:false` 关掉）⇒ ⇒ ⚠️ **原写「① Go 有 / RAG 无 = `admin-rpc` · `user-rpc` · `hot-rpc`（三重关闭）」已过期** —— `admin-rpc` / `user-rpc` 现**显式写 true** · `hot-rpc` **从未关过** ⇒ ⇒ **「RAG 配置面比 Go 窄」的域 = 0 个**。
- ⚠️ **叠载体面后**：配置面「有 prometheus endpoint」的 10 域里，**`comment-rpc` / `task-rpc` 两域无载体 ⇒ 实际抓不到**（其余 8 域可抓）⇒ ⇒ **三态划分只覆盖配置面，不覆盖运行时可抓取性** ⇒ ⇒ 且 **`article-rpc` / `hot-rpc` 的 Go 侧根本没有 DevServer**（真源 yaml **无该段**）⇒ ⇒ 那 2 域属**反向差异（RAG 更宽）**，不是「RAG 更窄」。
- ⇒ ⇒ **净结论（2026-10-31 订正 · Go 侧逐域实测 ＋ RAG 侧三层判据）**：**Go 侧可抓 = 6/10**（真源 yaml 实测有 `DevServer` 段 = `favorite` `16062` · `follow` `16065` · `like` `9098` · `message` `16067` · `user` `9094` · `admin` `9097`；`article` / `hot` / `task` / `comment` **无 DevServer 段** ⇒ `optional` ⇒ 整段缺失 ⇒ `Enabled=false` ⇒ **不启动** ⇒ 无 `/metrics`）· **RAG 侧可抓 = 8/10**（§4.19.23）⇒ ⇒ **「RAG 侧抓不到」的域 = 0 个**（⚠️ **原写「净差 = 5/10 个 rpc 域『RAG 侧抓不到』』已作废、且方向反了**）⇒ ⇒ **净差方向 = RAG 更宽 2/10**（`article-rpc` / `hot-rpc`：Go 侧无 DevServer ⇒ 无出口，而 RAG 侧 L1/L2/L3 全 ✔ ⇒ **RAG 多出可抓性**）⇒ ⇒ **两侧都不可抓 = 2 域**（`comment-rpc` / `task-rpc`）。
- ⚠️ **JMX 边界（别拿它当出口）**：actuator 无 web 载体时 JMX 端点仍可暴露（10/10 都**未配** `management.endpoints.jmx.exposure` ⇒ 走默认 health/info），但 **Prometheus 的抓取模型是 HTTP scrape endpoint** ⇒ ⇒ **JMX 不能替代** ⇒ ⇒ 「抓取出口」口径下仍记为无。

**（4）对我域台账的两处影响（已同步）**

- `docs/DRIFT/follow/rpc.md:273` 的「8 个 rpc / 0/8」⇒ 订正为「**10 个 rpc / 载体层 8/10 有 / 2/10 无**」＋ 补两层口径与判据来源（⚠️ **2026-10-31 再订正**：原写的「1/10 有 / 9/10 无」**已被推翻** ⇒ ⇒ 有载体 8 个 · 无载体 2 个（comment-rpc / task-rpc）⇒ ⇒ ⚠️ **归因也改了**：不是「依赖树实测推翻」（**那个判据本身已被判不成立** —— 依赖树里有 `tomcat-embed-core` 只是三条件之一）⇒ ⇒ 现在的口径 = **`docs/DRIFT/global/vs-common.md` §4.19.23 的三层判据**（配置面 10/10 · 载体面 8/10 · 可抓取面 8/10））。
- `docs/DRIFT/follow/stage1-admission.md:79` 的「`favorite-rpc` / `message-rpc` / `like-rpc` / `user-user-rpc` **均已补上** ⇒ 出口已开」⇒ **被本轮实测证伪**（这 4 个模块的依赖树里都没有 web starter/tomcat）⇒ 已订正。
- ✅ **users 域 `user/rpc.md:280` 的「指标可抓取性」改判（OBSERVABLE DIFFERENCE）成立且更强** —— 已补载体层证据（`user-rpc` 依赖树只有 actuator、无 tomcat）⇒ ⇒ **不是 users 域孤例，是 9/10 的通例** ⇒ ⇒ **该改判不撤**。

### 🔴 订正小节：follow-rpc 的 `rag.side: rpc` —— 这不是防御性的一行，**它现在就是闸门**（2026-10-30 · G1 lane）

> ⚠️ **本小节标题刻意不含任何编号 token** ⇒ 派生层不把它当条目候选 ⇒ 也不劫持任何既有编号的 `domain` / `wave` / `basis`。

**一、为什么 follow 域这一行与另外两个 rpc 域不同（关键区别）**

- `DRIFT-FOLLOW-006` 的载体 **2026-10-29 就落地了**（commit `573cf08f`）：`service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web`（给 Actuator 一个 servlet 载体）＋ `…/application.yml:141-142` = `server.port: ${FOLLOW_RPC_MANAGEMENT_PORT:16065}`（与 Go 侧 `follow.yaml:23` = `DevServer.Port: 16065` **一字不差**）＋ `:123` = `register-enabled: false`（关掉 Nacos 自动注册 ⇒ 防「同一服务名下出现两个端口的实例」）。
- ⇒ ⇒ **本模块的主上下文就是 servlet web 应用** ⇒ ⇒ 共享层那 13 个 api 侧 web Bean **会挂到 16065 的抓取口上**（主干上 `@ConditionalOnApiSide` 的 `matchIfMissing=true` ⇒ 没写 `rag.side` 就按 api 侧注册）。
- ⇒ ⇒ 其中 **`GoStyleCpuSheddingFilter` 在 CPU 高负载时会拒掉抓取请求** ⇒ ⇒ 而 Go 的 rpc 域**一个 rest 中间件都没挂**（全部 `zrpc.MustNewServer`）⇒ ⇒ **与 Go 不同形** ⇒ ⇒ ⇒ 即：**载体的目的（抓得到 `/actuator/prometheus`）会被自己域的中间件挡掉**。

**二、落码**

- `service/follow/follow-rpc/src/main/resources/application.yml` ⇒ 加顶层 `rag:` / `side: rpc`。
- **Go 出处**：Go 侧 rpc 进程零 rest 中间件 ⇒ ⇒ RAG 的 rpc 域不得挂 Go 侧没有的中间件。
- **机制**：共享层 `@ConditionalOnApiSide`（`@ConditionalOnProperty(name="rag.side", havingValue="api", matchIfMissing=true)`）挡住 api 侧那 **13 个类**（11 个带 `@ConditionalOnWebApplication(SERVLET)` ＋ `HttpMiddlewareFilter`（无条件 `@Component`）＋ `GoStyleJsonBodyConverterConfig`（`@Configuration`）—— 清单见 users 域同款小节，两域共用同一份）⇒ ⇒ 其中**会「拒请求」的只有 `GoStyleCpuSheddingFilter`（CPU 高负载）与 `GoStyleMaxConnsFilter`（连接数超限）**⇒ ⇒ **默认 = api 侧** ⇒ ⇒ rpc 域必须显式写这一行。

**三、诚实边界（本轮不写「已封住」）**

- ✅ **现状（2026-10-30 · 合并主干后订正）**：共享层的 `ConditionalOnApiSide` **已落库**（`53bc05ec`）⇒ ⇒ **本行现在真的被读了**（原写「本分支基线上尚未落库 ⇒ 死配置」是**分支相对**的说法 ⇒ 叠到主干后已不成立 ⇒ 原值保留备查）⇒ ⇒ **本行是必做项**（注解默认值 = api · `matchIfMissing=true` ⇒ rpc 域**必须显式写这一行**）⇒ ⇒ 且本模块**已是 servlet 形态**（`management.server.port: 16065`）⇒ ⇒ 不写它 ⇒ ⇒ 那 13 个 api 侧 web 类就挂到抓取口上 ⇒ ⇒ **仍不写「已封住」**（按 AGENTS.md §0 读作「在该证据强度下一致」）。
- ⚠️ **运行期守门仍未落**：收口执行者要求配一条**运行期**断言（起 rpc 形态的 context ＋ `rag.side=rpc` ⇒ ⇒ 断言那 13 个**带门的类**不在容器里）⇒ ⇒ **该门现已在主干 ⇒ ⇒ 这条断言此刻可写（不再是恒红）** ⇒ ⇒ **仍待域侧补**（该守门要断的 13 个类名 · 权威清单见本节「机制」行所指的 users 域同款小节）⇒ ⇒ 在它落之前 ⇒ ⇒ **不写「已封住」**（原写「本分支基线上必然红 · 写它只会得到一个恒红的守门」是**分支相对**的说法 ⇒ 叠到主干后已不成立 ⇒ 原值保留备查）。

**四、与既有结论的关系**

- 本节**不改** `DRIFT-FOLLOW-006` / `S1-FOLLOW-001` 的既有结论（载体已落地、端口与 Go 一字不差、双侧 series 集合逐条一致 —— 那是**指标面**的结论）。
- 本节说的是**另一个面**：**载体上挂着的中间件集合** ⇒ ⇒ Go 的 rpc 域零中间件 ⇒ ⇒ RAG 的观测口上**曾经**会多出 13 个 ⇒ ⇒ 这一行就是用来对齐**这个面**的 ⇒ ⇒ **两条不矛盾，是两个面**。
---

> ⚠️ **以下内容原位于本文件中部 —— 2026-10-30（G1 lane）移到末尾**
> 理由 = **零行位移**：`docs/RoundPlan/_unresolved.md` 与 `docs/DRIFT/_cross/grading/*` 里有指向本文件的
> **硬编码 `文件:行号` 指针** ⇒ ⇒ 派生层按**纯行号算术**解析 ⇒ ⇒ 中部插一行 ⇒ 指针解析到别的条目
> ⇒ ⇒ 「排期指针 / 证据缺口组」的成员被静默改写 ⇒ ⇒ **派工面被改掉**。
> ⇒ ⇒ 纪律：**新增内容一律放文件末尾**；要改中部 ⇒ 用「**就地替换**」（行数不变）而不是「插入新行」。
> ⇒ ⇒ 判据：`python3 tools/ledger-derive/check-pointer-consistency.py`（派生层输入完整性棘轮）。

   > ⚠️ **2026-10-30 口径订正（G1 lane · `align/g1-users-follow`）** —— 上句的**两条落点断言均不成立**：「15 处全在 `service/users/**`（`user-rpc` 12 + `admin-rpc` 3）」与「5 处 `schema/field.go:1009` 全在 `user-rpc`」。实测：`grep -rn "gorm@v1\.31\.2" service/users/` ⇒ **0 命中**；`grep -rn "field\.go:1009" service/users/` ⇒ **0 命中** ⇒ **15 处的真实落点 = 12 处 users 域台账 + 2 处 follow 域台账 + 1 处 like 域代码**：
   > - **真错引（消费点引用）= 8 处**，全在 **users 域台账**：`admin/rpc.md` × 3（`:423` / `:1089` / `:1316`）· `user/rpc.md` × 4（`:550` / `:580` / `:1384` / `:1433`）· `_shared.md` × 1（`:986`）⇒ ⇒ **本轮已逐条订正**（`gorm@v1.31.2` → **`gorm@v1.31.1`**；行号按 **v1.31.1 真源重锚**：`callbacks/update.go:243-259` → **`:238-254`**、`:279-281` → **`:274-276`**（两段 **md5 逐字相同**）· `scan.go:153` **两版同行** ✓ · `schema/field.go:616-622` 与 `:195-199` **两版同行** ✓）。
   > - **不是错引、⛔ 不得改 = 6 处**：`docs/DRIFT/users/vs-common.md` × 2 · `docs/DRIFT/users/stage1-admission.md` × 2 · `docs/DRIFT/follow/vs-common.md` × 2 —— 这 6 处的 `gorm@v1.31.2` **就是「被引用的那个错版本」本身**（描述缺陷的句子）⇒ 改了就把缺陷描述改没了。
   > - **他域 = 1 处**：`service/like/like-rpc/src/main/resources/db/migration/V6__align_like_domain_fact_outbox_with_go.sql:21` ⇒ **like 域 = G2 面** ⇒ 📤 **登记上报 G2**（不在 G1 写权面）。（附：该处所引 `schema/field.go:330-341` 在 **v1.31.1 与 v1.31.2 内容逐字相同** ⇒ **版本标签错、内容不错**。）
   > ⇒ **5 处 `schema/field.go:1009` 越界 ⇒ 已核销**：代码侧**早已全部订正为 `v1.31.1`**（`UserDomainService.java:192` / `:447` · `UserEntity.java:66` · `UserDomainServiceTest.java:696` / `:699` · `UserRpcLoginNullStatusReadsAsZeroTest.java:46` / `:52`）⇒ ⇒ **本轮 0 行代码改动**，只把「仍待修」的台账登记改成「**已核销**」。核对日期 **2026-10-30**。
### 【开工前置回执 · G1（`align/g1-users-follow`）· 2026-10-30 轮】

> **本轮定位**：**派工面 = 派生层施工单**（`docs/DRIFT/_cross/stage1-derivation.tsv` 的 **`wave` 列**）⇒ 本轮**不是**照抄 2026-10-29 轮的候选清单，而是**按 `wave=G1` 重新取件 + 按 `basis` 逐条回源**。
> ⚠️ **基线**：`do-my-own-gh` @ `bdac3607`（含 `wave` 列那次提交）· 分支 `align/g1-users-follow` · 工作树 `../RAG.worktrees/g1-users-follow`。

**① 任务范围（本 lane 本轮做什么）**

| 项 | 值 |
|---|---|
| 取件口径 | `wave` 含 `G1` × `stage ∈ {A,B}` = **19 条** ⇒ 其中**状态仍开放** = **10 条** |
| 10 条开放项 | `E65` · `S1-FOLLOW-007` · `DRIFT-ARPC-009` · `E1` · `E7` · `S1-FOLLOW-004` · `S1-FOLLOW-008` · `S1-FOLLOW-009` · `S1-USERS-002` · `S1-USERS-004` |
| 分流结果 | **域侧落码 1 条**（`S1-USERS-004` 的守门半边，落在 **users 域** `user-rpc`）· **转派 G4 反馈单 3 条**（`E65` / `DRIFT-ARPC-010`=E66 / `DRIFT-ARPC-009` 残留）· **域外或他域只登记 6 条** · **台账订正 3 组**（零行为变更） |
| **follow 域本轮自身** | **0 条代码落码** —— `DRIFT-FOLLOW-006` 已于 2026-10-29 落地并关闭；`S1-FOLLOW-001` 属**用户保留给主会话**的拍板项（**不落码**）；`S1-FOLLOW-004/007/008/009` **均落在他域/共享面/基线** ⇒ **只登记 + 上报** |
| 域内基线判定 | `docs/PhaseGoal/g1.md` §6 = **✅**（组件行 **10/10 ✅**）· §2.1「本 lane 自修」= **（无）** |

**不做（明确边界）**
① 阶段二（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7）；
② 共享面：`service/common/**` + `service/users/users-common/**` + `tools/**` + `docs/DRIFT/_cross/**` + `AGENTS.md` + 三个 `_INDEX.md` + `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/**` + `docs/Worktree.md`；
③ **`S1-FOLLOW-001`（follow-rpc 指标出口）** —— **已于 2026-10-29 落地**（`DRIFT-FOLLOW-006` 关闭）⇒ **本轮不重复做**；**users 域的 9094 / 9097 同款缺口不在派工面**（`stage1-derivation.tsv` 里 users 侧无对应开放行）⇒ **本轮只订正 `users/user/rpc.md:280` 被推翻的前提**，**不落码**；
④ **不重开已签字偏离**（`DRIFT-FOLLOW-008` / `SD-2026-10-25-01` MaxConns 半边 / `docs/Intentional_Deviations/follow/*` 4 份**空占位** ≠「已签字」，见 `docs/Pitfalls/users/admin/rpc.md` **P-9**）；
⑤ 🔴 **订正（2026-10-30 16:5x）** —— 原写「**不改** `S1-USERS-002` 的 datasource 口令 fallback（台账已裁定为**有意偏离 · 需签字**，非纯域侧自修）」⇒ ⇒ **那个前提是错的**：`docs/DRIFT/users/stage1-admission.md:477` 原句「Go 真源 yaml 是**字面量默认值**，恒能起栈」与 Go 源码不符 ⇒ ⇒ **Go 侧全部 rpc yaml 都是 `"${POSTGRES_PASSWORD}"`（无默认值）**（`service/follow/rpc/etc/follow.yaml:33` 实证）+ go-zero `core/conf/config.go:63` 用 `os.ExpandEnv`（**不认 `${VAR:default}`**）⇒ **缺变量即空串 ⇒ 连库失败 = fail-fast** ⇒ ⇒ **RAG 保留 `:password` 兜底 = 比 Go 松 = 真漂移** ⇒ ⇒ **本轮已落码：follow 域那 1 处也取消了**（`service/follow/follow-rpc/src/main/resources/application.yml:38` ⇒ `password: ${POSTGRES_PASSWORD}` · ⚠️ **行号因本轮加注释位移 +11**）⇒ ⇒ 残余 **7 处**在 G2/G3 面。⚠️ **取消后的残余差异（RAG 更严的一侧）**：Go = 启动成功、连库时失败；RAG = **启动即失败** ⇒ **失败时刻**不同 ⇒ 这一条残余才属有意偏离候选（同 JWT fail-closed 族）。

**② 已读四档（本 lane 面的具体路径 → 读到的关键结论）**

| 档 | 具体路径 | 关键结论（本轮真正改变了判断的 5 条 · follow 侧） |
|---|---|---|
| `docs/DRIFT/follow/` | `rpc.md`（§283-350 六 rpc 的 Go 有 DevServer / RAG 零出口对照表 · `:330-336` **跨域待办表** · `DRIFT-FOLLOW-006` 落地全记录 4 步）· `api.md` · `vs-common.md` | ① **`DRIFT-FOLLOW-006` 已关闭**（2026-10-29 · `573cf08f`）⇒ follow-rpc 观测口 **16065** = Go `DevServer.Port` **一字不差** ⇒ **同款缺口在 users 域的 9094 / 9097**（同文件 `:330-336` **已明确划归 users 域**）⇒ ⇒ **本轮不落码、只订正 users 侧台账前提**（理由：① ③ + 派工面无 users 侧开放行）· ② **follow 域的指标出口修法是「加 `spring-boot-starter-web` 给 Actuator 一个 servlet 载体 + `register-enabled: false` 防 Nacos 注册错端口」** ⇒ 属**部署形态变更** ⇒ **不在本轮授权面**（用户 2026-10-24 已把「全仓 rpc 层统一方案」交主会话） |
| `docs/DRIFT/users/` | `stage1-admission.md`（§3 · §4 任务表）· `admin/rpc.md`（§1 `DRIFT-ARPC-009~012`）· `user/rpc.md`（`:280` 指标可抓取性判定）· `_shared.md`（`U-1`）· `vs-common.md` | ① **`S1-USERS-004` 的可做面只有「守门」**（台账 §4 任务表明写「阶段 1 不动事务边界」，§518 写「缺防退化」）⇒ ⇒ **本轮只加守门**（落在 users 域 `user-rpc`，零行为变更）· ② **`DRIFT-ARPC-009` 台账「合并后本条自动消失」实测不成立**（共享链已合进主干但只装 3 层 ⇒ 残留 = Stat + Prometheus ⇒ 落 `service/common` ⇒ **转 G4**）· ③ **`user/rpc.md:280` 的「两侧生产都不可抓 ⇒ EQUIVALENT」前提被推翻** |
| `docs/DRIFT/_cross/` | `stage1-derivation.md` §四 · `stage1-derivation.tsv`（17 列 / `wave` 列）· `domain-common-lanes.md`（9 轴 A1~A9）· `cross-domain-coupling.md`（§二 波次表 + 规则 C1~C7）· `global/lanes/_PROTOCOL.md`（反馈单 7 项） | ① **派生层只是台账的投影**（「台账结论会过期 ⇒ 派工前**按 `basis` 抽查**」）⇒ 10 条开放项里 **4 条前提已变**（`S1-FOLLOW-007` / `E1` / `E7` / `S1-USERS-002`）· ② **反馈单 7 项**字段固定 ⇒ 本轮 3 条按此格式出单 · ③ **域内探针目录 `tools/<kind>/<域>/` 归对应域 lane** ⇒ `tools/api-full-parity-probe/follow/` 是 G1 面 |
| `docs/Intentional_Deviations/` | `_INDEX.md`（**已签字 60 / 未签字候选 12 / 总数 93**）· `users/_shared.md`（`CAND-16` / `CAND-17`）· `follow/api.md` · `global/common-tails.md:11`（`SD-2026-10-25-01`） | **`DRIFT-FOLLOW-008` 已于 2026-10-25 签字** ⇒ ⇒ `S1-FOLLOW-007` 登记的「基线仍把已签字偏离记成开放缺陷」**风险实际已解除**（`docs/PhaseGoal/g1.md:21` 的「开放项归属」列**已改为** `→ 非开放（已签字 · 不是缺陷）`）⇒ **本轮只在本域台账登记订正**，基线文件交收口执行者 |
| `docs/PhaseGoal/` | `g1.md`（机器生成投影 · §1 组件行 · §2 · §5 收口命令 · §6 lane 判定 · §7 交付物）· `judge.md`（A-2 follow 行） | G1 **lane 判定 = ✅** · **§2.1 自修清单 = （无）** ⇒ 本轮不新开行为修复 · `follow-rpc` 行已记 **`DRIFT-FOLLOW-006` 关闭** + **5 条残余差异全 P3 / D 类**（A/B/C 类 **0**） |
| `docs/PhasedGoal.md` + `docs/twoStage.md` | `PhasedGoal.md:38-82` · `twoStage.md` §4 / §5.3 / §5.4 / §十 | **A 类 = 阶段 1 准入阻断** ⇒ G1 唯一的 A 类开放项经复核**前提已解除** ⇒ **G1 无 A 类阻断** |
| `docs/Pitfalls/follow/` + `docs/Pitfalls/_cross/` | `follow/rpc.md`（PIT-FOLLOW-001~005 · 013/014）· `follow/api.md`（PITFALL-FOLLOW-006~012 · 015/016）· `_cross/testing-pitfalls.md` | ① **FOLLOW-005** = 共享 `service/common` 在**纯 gRPC 进程**里缺 servlet-api ⇒ 与本轮 `DRIFT-ARPC-009` 同族（**落点共享层 ⇒ 转 G4**）· ② **FOLLOW-016** = **对拍装置自身会骗人**（端口撞车 / 时钟跳变 / 启动时序）⇒ ⇒ `E7`（follow 双侧活体未复验）**不得照抄旧日期的结论** · ③ **`testing-pitfalls.md`** = 「绿测试可能是**错误答案的守卫**」⇒ 本轮新守门必须**真触发** |
| `docs/Worktree.md` | §二·五（回执模板 + **核验命令** + 7 种不成立形态）· §三·1d · §三·1e | **核验命令 = `grep -c '^## 开工前置回执'` + `grep -cE '⑤ 批准记录：Jason · [0-9]{4}-'`** ⇒ 本轮**在既有小节内追加**（全仓 10 份 `vs-common.md` 均恰好 1 份 ⇒ 追加后仍是 1）· **「回执里自己写『已批准』不算批准」** ⇒ ⑤ 填的是 Jason 在本 lane 会话内的明示 |

**③ 待批准项（需 Jason / 调度员拍板）**

**本轮 = 0 条新增待拍板项。**
⚠️ **1 条既有待拍板项仍在（不属本轮、也不重复提）**：**`S1-FOLLOW-001`** —— 全仓 rpc 层指标出口统一方案（用户 2026-10-24 已定「交主会话拍板」）⇒ **follow 域那一半已于 2026-10-29 落地**；**users 域那一半（9094 / 9097）本轮只订正前提、不落码** ⇒ **仍待主会话拍板**。

**④ 等批准 ⇒ 批准后才开工**
**回执落笔时**本轮只读 + 写本回执（未获批准前不写任何代码、不改任何台账，本小节除外）；⇒ **Jason 于 2026-10-30 10:31 在本 lane 会话内明示「开始工作」**⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）⇒ 本轮按 ① 所列范围执行。

**⑤ 批准记录：Jason · 2026-10-30 10:31 · 批准范围：本 lane 回执 ① 所列任务范围（派工面 `wave=G1` × A/B 的开放条目逐条回源后分流 = 域侧落码 1 条〔落 users 域 `user-rpc` 守门〕+ 台账订正 3 组 + 转派 G4 反馈单 3 条 + 域外登记 6 条；**follow 域本轮自身 0 条代码落码**）** —— ⚠️ **不含**共享面、阶段二、`S1-FOLLOW-001` 落码、已签字偏离重开　← 由**调度员代填**（依据：Jason 于 2026-10-30 10:31 在**本 lane 会话内**明示「开始工作」；**回执人不得自批**· **代填 ≠ 代批准**）


## 客户端硬上界 · 域侧落码的共享层对账记录（2026-10-31 · G1 lane）

> ⚠️ **本小节标题刻意不含任何编号 token** ⇒ 派生层不把它当条目候选。
> ⚠️ **本小节用列表而非表格** ⇒ 派生层不把列表行当条目候选。
> ⚠️ **本小节在文件末尾** ⇒ 零行位移 ⇒ 不影响任何既有 `文件:行号` 指针。
> ⚠️ **完整落码记录见** `docs/DRIFT/follow/rpc.md` 末尾同题小节 ⇒ 本小节只记**与共享层对账**的那几笔。

**一、本域这一轴与共享层的关系（机制面轴 A1~A9 的落点）**

- **共享层给唯一实现**：`GoStyleOutboundDeadlineInterceptor`（`@GrpcGlobalClientInterceptor`）
  ⇒ 装在**每个**出向 devh 调用上 ⇒ ⇒ **域侧零代码改动**，只配一个参数。
- **域侧只配参数**：`rag.protection.outbound-deadline.cap-millis`
  ⇒ 本域 = `${FOLLOW_RPC_OUTBOUND_DEADLINE_CAP_MILLIS:2000}`（Go 侧 `follow.yaml:37-41` 无显式 Timeout ⇒ 恒 2000ms）。
- **域侧原先那份内联副本（内联封顶）已删** ⇒ ⇒ **副本数 = 漂移面**这条纪律在本域得到遵守。
  - ⚠️ 共享层的单源棘轮 `GoStyleClientCallTimeoutSingleSourceGateTest` 的**已知盲区**（按类名识别 + api 侧内联形态不在检测面）
    **仍然覆盖不到本域这三处内联副本** ⇒ ⇒ 本轮在**域侧**补了形态面判据（第 ⑤ 条：本域 `src/main/java` 不得出现 `withDeadlineAfter` 的代码行）
    ⇒ ⇒ **该盲区的根治属共享层写权面** ⇒ ⇒ **转 G4 / 收口执行者**。

**二、共享面【已过期】的登记（转收口执行者）**

- `docs/DRIFT/global/vs-common.md:46` 里「**零上界的 11 个类**」的枚举 **④ `follow-rpc/.../UserRpcClient.java:50`**
  ⇒ ⇒ 本域这一类**已不再零上界**（本轮已配 2000ms）⇒ ⇒ 该格**已过期**。
  - ⚠️ **那一格是共享面** ⇒ ⇒ **本 lane 不能改** ⇒ ⇒ **转收口执行者订正**。
  - ⚠️ 顺带：④ 的**落点** `:50` **一直是错的**（`:50` 是空 javadoc 行 ⇒ 复核者按它定位会落到 javadoc）
    ⇒ ⇒ 本 lane 已在**本文件**与 `docs/DRIFT/users/vs-common.md` 内就地改指真实调用点 `:89-90`；共享面那一处**同样转收口执行者**。

**三、本域【未做】的（残余）—— 本轮已清空（唯一一项 = follow-api 的内联副本）**

- **`follow-api` 的内联副本已删**（`FollowRpcClient.java:230` 调用点**行号不变**；`capByClientTimeout` 方法连同 javadoc 已删）⇒ ⇒ 出向封顶改由**共享层唯一实现**承担 ⇒ ⇒ 域侧只配 `rag.protection.outbound-deadline.cap-millis` ⇒ ⇒ **可 env 覆盖**。
  - 落码记录与变异实测见 `docs/DRIFT/follow/rpc.md` 与 `docs/DRIFT/follow/api.md` 末尾同题小节 ⇒ ⇒ **不静默留着**。


---

## follow-api 的内联副本清理 —— 共享层对账记录（2026-10-31 · G1 lane）

- **本域出向硬上界的「第二份实现」已清零**：follow-rpc / user-api / admin-api（上一轮）＋ **follow-api（本轮）** ⇒ ⇒ 四域的域侧实现全部退化为「**只配 `rag.protection.outbound-deadline.cap-millis`**」⇒ ⇒ 出向封顶的**唯一实现** = 共享层 `GoStyleOutboundDeadlineInterceptor`（`@GrpcGlobalClientInterceptor`）
- **共享层在位证据（自读，非转述）**：`FollowApiApplication.java:68-71` 的 `scanBasePackages = {"com.rag.follow.follow.api", "com.rag.common"}` ⇒ ⇒ `GoStyleOutboundDeadlineConfiguration`（`@Configuration` + `@ConditionalOnClass(name = {"net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor", "io.grpc.ManagedChannel"})` + `@Bean`）在扫描面内 ⇒ ⇒ 其产出的拦截器（`:121` 带 `@GrpcGlobalClientInterceptor`）被 devh 的 `GlobalClientInterceptorRegistry` 收集 ⇒ ⇒ 装在**每个**出向 devh 调用上 ⇒ ⇒ **域侧配 cap 即生效，不需要改启动类**
- **本轮新增的域侧守门**：`FollowRpcClientDeadlineCapTest`（**5 条**）⇒ ⇒ 形态面第 ⑤ 条（本域 `src/main/java` 不得出现 `withDeadlineAfter` 的代码行）**补上了共享层单源棘轮的已知盲区**（`GoStyleClientCallTimeoutSingleSourceGateTest` 按类名识别 ⇒ 覆盖不到 api 侧内联形态）⇒ ⇒ **根治（把棘轮扩到内联形态）属共享层写权面 ⇒ 转 G4**
- **已知残余（shared 面，本轮不动）**：`docs/Pitfalls/_cross/testing-pitfalls.md:489` 按**方法名**提到 `FollowRpcClient.capByClientTimeout` ⇒ ⇒ 本轮删掉该方法后，那一行**按名指向一个已不存在的方法** ⇒ ⇒ 语义悬空（不是行号悬空）⇒ ⇒ **该文件不在本 lane 写权面 ⇒ 转收口执行者 / G4 订正**
- **指针状态**：`:164`（常量行，被 `docs/Pitfalls/follow/api.md:481/:509` 指着）⇒ **行号不变**；`:230`（调用点）⇒ **行号不变**；`docs/DRIFT/follow/vs-common.md` 内指向 `:230` 的那一条**仍然有效**（已就地改指真实形态）
---

## 指针漂移订正 —— 删副本引起的行号位移（2026-10-31 · G1 lane）

- **本轮订正 4 处落点**（全部**就地替换、行数不变** ⇒ 派生层关键数字逐项零变化 ⇒ 派生层产物校验和不变）：
  - **出向调用点**：裸 stub 调用点由 `:88` 改指 **`:89-90`**（2 处 = 本文件「出向调用是否继承请求 deadline」那一行 ＋「共享面【已过期】的登记」里的嵌套引用）。
    ⇒ ⇒ **成因**：`6d04dc19` 删掉内联 `capByClientTimeout` 方法（该方法原占 `:91-105`）⇒ `getUser` 的调用体整体上移；
    同时常量行由 `:75` 移到 `:76`（其 javadoc 多 1 行）⇒ ⇒ **删除前 `:88` = `return capByClientTimeout(userStub)`（带 cap，**不是**裸 stub）**，
    **删除后 `:88` = 方法签名行** ⇒ ⇒ **两种状态下 `:88` 都不是「裸 stub 出向调用点」** ⇒ ⇒ 真实落点只能是 `:89-90`。
  - **两处测试文件**：`FollowGrpcInterceptorWiringTest` 的 `:110` ⇒ **`:111`**、`FollowRpcSlowThresholdGateTest` 的 `:269` ⇒ **`:270`**
    ⇒ ⇒ 原落点是**空行**，真实落点是那条「⚠️ 不能用 `forPort(0)`」注释 ⇒ ⇒ **差 1 行**。
- **⚠️ 上一笔提交的一处判断，本轮被实测推翻**：那笔提交的说明写「独立 verifier 另报的 `:88` 漂移**不成立** —— `:88` 正是出向调用语句首行」
  ⇒ ⇒ **该判断不成立**（`:88` 是方法签名行）⇒ ⇒ **连带**：`6d04dc19` 的提交说明写「三个 client 文件的既有指针行全部守住（follow-rpc `:34`／`:50`／`:75` …）」
  ⇒ ⇒ **实测 `:75` 没守住（⇒ `:76`）· `:50` 的落点也变了（空 javadoc 行 ⇒「Go 的出向 deadline = min(…)」那行）**
  ⇒ ⇒ **教训**：「零行位移」的自证**不能只凭 diff hunk 的起点对齐**，必须做「删除前／删除后**同一行号的内容对比**」。
- **共享面【转收口执行者 / G4】**（本 lane 无写权，记在此处免得丢）：
  - `docs/DRIFT/global/vs-common.md` 的枚举 ④ 已被订正为 `:88` ⇒ ⇒ **那个 `:88` 在 `6d04dc19` 落地后同样悬空**（`capByClientTimeout` 已不存在 ⇒ `:88` 是方法签名行）⇒ ⇒ 真实落点同样是 `:89-90`。
  - 同一格的枚举 ⑤／⑥ 也因删副本而位移 ⇒ ⇒ `user-api` 的 `userRpc()` 由 `:194-196` 上移到 `:208-210`；
    `admin-api` 的 `adminRpc()` 由 `:219-221` 上移到 `:235-237` ⇒ ⇒ 且三处 `withDeadlineAfter` 副本**已不存在** ⇒ ⇒ 请一并订正。
  - shared 面的踩坑档有一条**按方法名**提到 `FollowRpcClient.capByClientTimeout` ⇒ ⇒ 本轮删掉该方法后那一行**语义悬空**（不是行号悬空）⇒ ⇒ 一并转。
---

## 删副本的语义口径订正 —— 本域这一侧是「覆盖」还是「取 min」（2026-10-31 · G1 lane）

- **本轮实测旧副本的实现，取证来源 = 删除前的源码**（`git show 6d04dc19^:<file>`，不是转述）：
  - **follow-rpc 的旧副本 = 纯覆盖**：`return stub.withDeadlineAfter(capNanos, NANOSECONDS);` ⇒ **不看入站剩余**
    ⇒ ⇒ 与 Go 的 `context.WithTimeout(ctx, 2s)`（**取更早者**）**方向相反**
    ⇒ ⇒ ⇒ **删副本在本域这一侧 = 修漂移（把「覆盖」改成「取 min」），不是「行为等价」**
    ⇒ ⇒ ⇒ 在「入站预算 < 2000ms」这一形态上：删前按 2000ms 走、删后按 min 走 ⇒ ⇒ **这是本轮唯一一处「行为变了」的形态**
  - **共享层那份 = 取 min**：出向拦截器的 `budgetNanos` = min(入向剩余 · cap · 既有 CallOptions 剩余)
    ⇒ ⇒ 与 Go 的 `context.WithTimeout` **逐字同形** ⇒ ⇒ 且 `capMillis = 0` = **不启用**（⇒ 「cap=0 负控」的语义是确定的，不是歧义）
  - **覆盖面没扩大**：follow-rpc 只有 **1 个**出向 stub（`@GrpcClient("user-rpc")` 一个注入点）⇒ ⇒ 共享层的 channel 级拦截器覆盖的**正是**旧副本覆盖的那一个 ⇒ ⇒ 「承担者换了」这半成立
- **⚠️ 口径订正（承 G4 之问）**：「**上界一直都在，变的只是由谁承担**」这句 ——
  **在 user-api / admin-api 上成立**（那两处的旧副本**自带** `Math.min(remainingNanos, capNanos)` ⇒ 本来就是 min），
  **在本域（follow-rpc）不成立**（旧副本是纯覆盖）⇒ ⇒ **引用该措辞时须逐域限定**，不要写成跨域通则。
