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
| 🔴 `DRIFT-FOLLOW-008` | **MaxConns 闸门**：Go **每路由一个**（`go-zero@v1.10.2/rest/handler/maxconnshandler.go:12` 按 `route.Path` 建闸）vs RAG **全局一个** | `service/common`（`GoStyleMaxConnsFilter`） | 🔴 **未修**（域台账原文即写「派单」）⇒ **本 lane 的头号事项** | [`api.md`](api.md) |
| `DRIFT-FOLLOW-002` | 运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败 | `service/common`（pom 作用域） | ✅ **已修** | [`rpc.md`](rpc.md) |
| `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（X-6） | ✅ **已落地** | [`api.md`](api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。

### 1.1 §1 抽取快照的**逐条状态复核**（2026-10-27 · G1 lane）

| 编号 | 快照状态 | **复核结论（核对日期 2026-10-27）** | 复核依据 / 强度 |
|---|---|---|---|
| `DRIFT-FOLLOW-008` | 🔴 未修（落点 `service/common`） | 🔴 **维持未修**，**落点已核准** ⇒ 派单 G4（**与 `DRIFT-USERAPI-013` 同族 ⇒ 两条一起对账**） | 域台账原文（L1）+ 共享层台账 `alignment-2026-10-23.md` §十二·二 T-2/T-5 |
| `DRIFT-FOLLOW-002` | ✅ 已修 | ✅ **维持已修**，且**本轮真启动复验**（`follow-rpc` 6.03s 起 · `NoClassDefFoundError` 未复现） | **L3 活体**（真容器） |
| `DRIFT-HOTAPI-030` | ✅ 已落地 | ✅ **维持已落地**（共享层 6 个类在位） | L1 |
| 🆕 `DRIFT-G1-001` / `DRIFT-G1-002` | —（快照里没有） | 🔴 **本轮新立**（真启动抓出）⇒ **落点在本域写权内 ⇒ 域侧自修，不派 G4** | **L3 活体 A/B + Go 侧 L1 源码行号** ⇒ 见 §7 |
| 🆕 `DRIFT-FOLLOW-010` | —（快照里没有） | ✅ **本轮新立并已修**（推广 `DRIFT-USERAPI-009②` 时顺手查出）⇒ **落点在本域写权内 ⇒ 域侧自修，不派 G4**（共享层槽位只**读取**、未改） | **L3 真 Tomcat 活体（10 条探针）＋ L2 装配层守门 + Go 侧 L1 源码行号** ⇒ 见 [`api.md` §11](api.md) |
| 🆕 `DRIFT-FOLLOW-011` | —（快照里没有） | ✅ **本轮新立并已修**（给 users 域对齐 Redis 超时时顺手查出）⇒ **落点在本域写权内 ⇒ 域侧自修，不派 G4**（只改本域 `application.yml`） | **L2 配置层守门（Spring 真实 Binder 绑定结果）＋ Go 侧 L1 出处链（`redisclientmanager.go:32-39` + go-redis `options.go` init()）** ⇒ 见 [`api.md`](api.md) 的 `DRIFT-FOLLOW-011` |

⚠️ **本轮复核范围声明（诚实边界）**：`DRIFT-FOLLOW-002` 有**真启动活体复验**；
`DRIFT-FOLLOW-008` / `DRIFT-HOTAPI-030` **状态未变**（按 AGENTS.md §6 记「状态未变」）
⇒ **派单时仍须按反馈单 7 项重新核 Go 侧 `文件:行号`**（台账的「抽取快照」**不算数**）。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | ① `DRIFT-FOLLOW-009`（下游挂死：Go **2s ⇒ 200+1015** / RAG **3s ⇒ 503**）✅ 已修 ② ✅ **`DRIFT-G1-002` 已修**（**阶段 2 · 2026-10-27**：`follow-api` 补 `immediate-connect-timeout: 3s` + `FollowRpcClient.afterPropertiesSet()` 自检 ⇒ 真启动 C 组实测**进程退出**，对齐 Go `zrpc.MustNewClient` 的 panic 语义 ⇒ 证据见 §7.5）③ ✅ **`DRIFT-FOLLOW-010` 已修**（**本轮 · 2026-10-27**：Go 的 3s 计时器在 `Authorize` **之后**起算 ⇒ **验签不计入**；RAG 把验签搬进 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`，在 3s 预算的 `+7` **之外**）⇒ 与 Go 一致 ⇒ 证据见 [`api.md` §11](api.md)）④ ✅ **`DRIFT-FOLLOW-011` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：`follow-api` 的 `spring.data.redis.timeout` 原为 **2s** ≠ Go 的 `ReadTimeout` **3s**，且**缺 `connect-timeout: 5s`** ⇒ 已对齐 ＋ 新增 `FollowApiRedisTimeoutConfigTest` 2 例；⚠️ **Go 侧 go-zero `redis.go:27` 的 `readWriteTimeout=2s` 是死常量**（只有 BLPop 用）⇒ 那个 2s 看起来正是从这里抄来的 ⇒ **不能当依据**）⑤ 🟡 ~~follow-api 的 Redis 命令超时 2s ≠ go-redis `ReadTimeout` 默认 3s~~（`DRIFT-USERAPI-015 附带` 登记的待办）—— ✅ **本轮已审并归入 ④**（原条目作废，见 `api.md` 的 `DRIFT-FOLLOW-011`）⑥ ✅ **`DRIFT-FOLLOW-012` 已修**（**本轮 · 2026-10-27 · 新立并自闭环**：Lettuce 默认 `disconnectedBehavior=DEFAULT` ⇒ **Redis 断连时命令被排队**等重连 ⇒ 挂到 `commandTimeout: 3s` ⇒ **撞 HTTP 3s 预算 ⇒ 503**；Go 侧 `MaxRetries=3` ⇒ **快速失败** ⇒ **fail-open 200** ⇒ 落**域内副本** `config/RedisDisconnectedBehaviorConfig`（`REJECT_COMMANDS`）＋ 守门 `FollowApiRedisDisconnectedFastFailTest` 2 例；⚠️ **修前实测 3012ms ⇒ 修后 0ms**；🔴 **共享层收编仍归 G4**）⑦ 🆕 **命令超时的「机制腿」已补**（**2026-10-27 · G1 lane · 探针实测**）：`TimeoutOptions.source` 恒为 `DefaultTimeoutSource`（返回 -1）⇒ 3s 只能由 **`applyConnectionTimeout`** 分支从连接级 timeout（`LettuceConnectionFactory.getTimeout()=3000ms` ⇒ `RedisURI.timeout`）取 ⇒ ⇒ **`isTimeoutCommands()` 恒真、不是有效判据** ⇒ 只断言「值」是**假绿口子**（机制被关 ⇒ 值仍读得到 3s、命令却永不过期 ⇒ 断连时从「3s 快速失败」退化成「无限等待」）⇒ `FollowApiRedisDisconnectedFastFailTest` 已补**机制腿**（＋ 变异：期望值 3000L→3001L ⇒ 红 ⇒ 断言在读运行期值）⇒ 证据详见 [`users/user/rpc.md` §4.1.1](../users/user/rpc.md) | ✅ **4 条已修** ＋ ⑦ 覆盖缺口已补 | L3 双侧活体（台账原有）＋ **本轮真启动活体（A/B/C 三组 ＋ L3 真 Tomcat 10 条）** ＋ **Go 腿 L4 活体·直接判据（③）+ RAG 腿 L3 真 Tomcat·间接判据 ⇒ 合并按弱侧记 L3**（G2 建、本域独立复跑 ⇒ 见 `users/user/api.md` §9.y.0 与 `users/vs-common.md` §8.4；⚠️ 不笼统写 L4） ＋ **L2 配置层守门（④）** ＋ **L3 真容器活体（⑥ · 真 Redis + 可控 TCP 代理造 FIN）** ＋ **L3 探针实测（⑦ · 命令超时机制腿）** | 2026-10-27 |
| **A2** | gRPC 拦截器链 | ① `DRIFT-FOLLOW-002`（运行时 classpath 缺 `grpc-inprocess` ⇒ `NoClassDefFoundError` ⇒ 启动失败）✅ 已修 ② `DRIFT-FOLLOW-003`（`discovery://user-rpc` 两斜杠被 devh 拒绝 ⇒ 启动失败）✅ 已修 ③ **服务端拦截器链**：`FollowRpcApplication.java:118` 挂 `GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps)`（recover→breaker→shedding→业务观测）⇒ 有**组件级守门网** `FollowProtectionChainWiringTest`（断言 `@Import` 含 `GoStyleProtectionConfiguration` ＋ 链**真包住**业务 interceptor ⇒ 能区分「在链里」与「链没挂上」） | ✅ **一致**（①② = 启动阻塞型硬失败，已修）＋ ✅ **③ 链已挂且有装配层守门** | L1 + 真启动复验（①②）＋ **L2 装配层守门**（③） | 2026-10-27 |
| **A3** | HTTP 保护链 | ① `follow-api` 的 `rag.http.go-form-limit.mode` = **envelope**（`application.yml:159`）② `DRIFT-FOLLOW-008`（**MaxConns 闸门：Go 每路由一个 vs RAG 全局一个**）🔴 ③ `OPEN-001`（Content-Type 不支持 ⇒ RAG 415 分支）✅ **已关闭（实测不可达）** ④ ⚠️ **`follow-rpc` 无 `rag:` 块** ⇒ 纯 gRPC 进程，表单限闸门**不适用**（N/A）⑤ ✅ **验签与保护链的相对次序已对齐**（**本轮 · 2026-10-27** `DRIFT-FOLLOW-010`）：验签搬进 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`）⇒ 落在共享层 `+3 Traceparent` 之内、`+7 HttpMiddleware（3s 预算）` 之外、`+8 MaxBytes` 之外侧 ⇒ ① **Filter 绝不写响应** ⇒ **413 仍压过 401**（真 Tomcat 实测 413 + 空 body）② **401 仍由 `FollowJwtAuthInterceptor` 抛** ⇒ **空 body** 形状逐字不变 ③ **未注册路径仍 404**（`handler instanceof HandlerMethod` 判据保留）⇒ 共享层槽位实测值见 [`api.md` §11.2](api.md) | 🔴 **1 条 G4** ＋ ✅ 3 条 ＋ **N/A 1 条** | L1 源码 + **L2 装配层守门（容器装配结果）** + **L3 真 Tomcat 活体** | 2026-10-27 |
| **A4** | 错误码 / envelope | ① `DRIFT-FOLLOW-001`（**A7/A4 邻接**：wire 级出参 Go `"user_ids":null` / RAG `"user_ids":[]`）✅ 已修 ② `DRIFT-FOLLOW-009` ✅ ③ A3 锚点：Go 侧**已注册**（真源 `service/follow/api/followcenter.go:53` `httpx.SetErrorHandler`）⇒ Go = **200 + 业务信封** ⇒ 与 RAG `mode=envelope` 同向 ✅ | ✅ **一致** | L1 + 活体对拍（台账原有） | 2026-10-27 |
| **A5** | 端口治理 | ① `follow-api` HTTP 端口：Go `followcenter.yaml:3` `Port: 8891` ↔ RAG `application.yml:52` `${FOLLOW_API_PORT:8891}` ⇒ **同默认值**，RAG 另加 env 覆盖（**更宽**，非收窄）② `follow-rpc` gRPC 端口：Go `follow.yaml:2` `ListenOn: 0.0.0.0:8082` ↔ RAG `application.yml:103` `port: 8082` ⇒ **两侧同为 yaml 写死 ⇒ 语义等价**（⚠️ 本轮订正：原写「配置面收窄」是**错的**）③ ⚠️ **`RANDOM_PORT` 面的记账缺口（2026-10-27 补记）** —— **实测清点**（非印象）：全仓 `RANDOM_PORT` 命中 **21 个文件**（`service/` 全树），**follow-api 域内零命中**、**零** `DEFINED_PORT` ⇒ 本域的 HTTP 响应头层验证走的是 **`webEnvironment = MOCK` ＋ `@AutoConfigureMockMvc`** ⇒ **MockMvc 测不到真 Tomcat 的 header 逐字节** ⇒ 真 Tomcat 面由**仓外脚本** `tools/difftest/check-plaintext400-headers.sh` 覆盖（**不在域内台账的证据面**） | 🟡 **端口值两侧同源 ✅** ＋ **`RANDOM_PORT` 面仍是记账缺口**（**不是「已对齐」**）—— 本轮把「零覆盖」从**空白**变成**有出处的记录**（缺口在哪个文件、为什么 MockMvc 不算、真 Tomcat 面由谁覆盖 三点已钉死） | L1 静态（`grep` 全树清点 ＋ 逐个测试类的 `webEnvironment` 实读） | 2026-10-27（**本轮补记**） |
| **A6** | errmsg 契约 | 本域 `follow-common`（**16 个业务码 + 16 条文案 + 三条错误转换路径**）↔ Go `service/follow/common/errmsg.go` | ✅ **零漂移**（域台账 [`common.md`](common.md) 判定 **`SEMANTICALLY EQUIVALENT`**：16 码逐条一致（含 `CodeServerBusy=1015` 排在 5001 之前这个细节）＋ 37 条契约测试 ＋ api 层 52/52 活体对拍） | **L4 双侧活体**（域台账核对日期 **2026-10-24**） | 2026-10-27（**本轮由「UNKNOWN」订正为 ✅**） |
| **A7** | proto 契约 | ① `DRIFT-FOLLOW-005`（recommend 出参**字段顺序** Go=`target_id` 在前 / RAG=`mutual_score` 在前）✅ 已修 ② 两侧 `.proto` 逐行 diff ③ **A7 邻接** `DRIFT-FOLLOW-001`（空列表出参 Go `"user_ids":null` / RAG `"user_ids":[]` ⇒ **wire 级**，非 A3 面）✅ 已修 | ✅ **一致** | L1 实测 diff（②）＋ L3 活体（①③） | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | follow 域 **五维全等**（**10 列 / 8 索引 / 2 约束**）⇒ 类型/长度 0、可空性 0、DEFAULT 0、索引 8≡8、约束 2≡2、触发器 0≡0 · 建表走 Flyway 独立账本 `flyway_schema_history_follow` ＋ `baseline-version: 0` | ✅ **一致** | **L3 双侧活体**（真 PG 16）· 2026-10-25 · 权威源 `docs/DRIFT/global/lanes/L3-ddl.md` §8.1/§8.9 ＋ 🆕 **本轮真启动复验**（非空 schema 下 baseline 打在 version 0 ⇒ V1/V2 照常执行） | 2026-10-27 |
| **A9** | trace / otel | ① **A9 本体（span 属性 `audit.*` ＋ `Traceparent` 传播）**：本域台账对 `Traceparent` / `audit.` **零命中**（`grep -c` 实证：整个 `docs/DRIFT/follow/` 只有本文件命中）⇒ **原记「域侧零覆盖」** —— ⚠️ **本轮已回真源逐条清点并重新定性**（见下方 A9 补记：**不是空白轴，是「两侧都无 ⇒ 无漂移」**）② 共享层本体**已闭合**：`GoStyleOtelAutoConfiguration` 走 **`META-INF/spring/…AutoConfiguration.imports`** ⇒ **不依赖组件扫描** ⇒ 纯 gRPC 的 `follow-rpc` 也自动装配（这正是该类注释写明「rpc 模块刻意不扫 `com.rag.common`」的解法）③ `GoStyleOtelConfig.java:27,96` 明确支持「endpoint 带/不带 `http://` 两种写法」＋「`protocol` 默认 grpc」⇒ **本域 api 与 rpc 的 otel 属性写法不一致（`otel.resource.attributes.service.name` vs `otel.service.name`；`always_on` vs `parentbased_always_on`）但落点等价 ⇒ 不构成漂移** ④ `DRIFT-FOLLOW-007` = **A9 邻接**（Prometheus 文本层，**非 A9 本体**）✅ 已修 ⑤ `DRIFT-FOLLOW-006` = **A9 邻接 ＋ A5 邻接**（Go DevServer `:16065/metrics`）🔴 未修 | 🟡 **两条「零覆盖」本轮已重新定性**（**不是空白轴，是「两侧都无 ⇒ 无漂移」**）—— 详见下方 A9 补记 | L1 静态（**本轮补：Go 侧 `audit.*` / `otel.` 逐域清点**）＋ 共享层台账（`common/audit.md` / `global/lanes/L1-delivery.md`） | 2026-10-27（**本轮补记 + 订正**） |

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

> **A3 已预填一条实测锚点（2026-10-27 核）** —— X-6（`DRIFT-HOTAPI-030`）共享层 6 个类已落码
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
| **1** | `DRIFT-FOLLOW-008`（**与 users 侧 `DRIFT-USERAPI-013` 同族 ⇒ 两条一起对账，别各修各的**） | **MaxConns 闸门**：Go **每路由一个** latch（按 `route.Path` 建闸）vs RAG **全局一个** | `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12` —— **已核 ✅**：逐字 = `func MaxConnsHandler(n int) func(http.Handler) http.Handler` | `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java`（**全局单闸门**） | **L1 源码逐行对照**（域台账原文）＋ 共享层台账 `alignment-2026-10-23.md` §十二·二 **T-2 / T-5**（两条指同一处） | 两侧都应是 **Y** = 「**每路由一个 latch**（按注册期 `route.Path` 模板建闸）」⇒ ⚠️ **404 取不到模板 ⇒ 不得用 raw URI 兜底** | `service/common/protection/` ⇒ 编译面 16 组件 | 🟡 **不阻塞**（与 users 侧 `DRIFT-USERAPI-013` 同批） |
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
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）

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
| `AGENTS.md` · 三个 `_INDEX.md` · `_MANIFEST.json` · `docs/DRIFT/_cross/**` · `docs/Worktree.md` · `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md` | 🔴 **无人拥有** | 🔴 **无人拥有** ⇒ **合并后由整合者统一更新** |

### 6.2 follow 侧派单（G4）—— **3 条，已核准落点**

| 编号 | 摘要 | 落点 | 归属 | 状态（2026-10-27 核） |
|---|---|---|---|---|
| `DRIFT-FOLLOW-008` | **MaxConns 闸门**：Go **每路由一个**（`go-zero@v1.10.2/rest/handler/maxconnshandler.go:12` 按 `route.Path` 建闸）vs RAG **全局一个** | `service/common`（`GoStyleMaxConnsFilter`） | 🔴 **G4** | 🔴 未修 · **与 users 侧 `DRIFT-USERAPI-013` 同族 ⇒ 两条一起对账，别各修各的** |
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

域台账 `docs/Pitfalls/follow/rpc.md` 记着一条 🔴 未修：**共享 `service/common` 在纯 gRPC 进程里缺 servlet-api** ⇒ 关闭路径抛 `NoClassDefFoundError: jakarta/servlet/Filter`。
**本轮复核根因链已读通**：`HttpMiddleware.java:8` `import jakarta.servlet.Filter` ⇒ `ObservabilityLifecycle.java:54` `HttpMiddleware.shutdown()` ⇒ **该类在纯 gRPC 进程里的首次加载恰好落在 destroy 路径** ⇒ 落点 **`service/common`** ⇒ **不在 G1 写权** ⇒ **派 G4**（§4 #2）。

### 9.3 共享索引过时（**无人拥有 ⇒ 交整合者**，G1 不写）

`AGENTS.md:32` 与 `docs/DRIFT/_INDEX.md:32,85` 仍写 follow「**零台账 / 仅 proto 层**」⇒ **与事实不符**（follow 域 4 个组件均已完成组件级对照，9 条 DRIFT + 15 条 PITFALL + 逐文件索引，见 `docs/DRIFT/follow/{api,rpc,common,proto}.md`）。
⇒ **共享面文件**（`AGENTS.md` / `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）**G1 一律不写** ⇒ 登记在此，**合并后由整合者统一刷新**（`docs/DRIFT/follow/rpc.md` 待办 6 早已记同一件事）。

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
| ③ | 值逐字同值 ＋ 只设断连行为 | `service/follow/follow-api/src/main/java/com/rag/follow/follow/api/config/RedisDisconnectedBehaviorConfig.java:111-112` 只调 `disconnectedBehavior(REJECT_COMMANDS)`，**零** `timeoutOptions` 触碰 | ✅ |

⇒ **follow 侧要删的只有 1 份**：`follow-api` 的 `config/RedisDisconnectedBehaviorConfig.java`。

⚠️ **`follow-rpc` 不在删除清单里**：它**不用 Redis**
（`service/follow/follow-rpc/pom.xml:26` 明写「任何 redis/kafka starter 在本模块都属于『新增 Go 侧不存在的能力』，一律不引」）
⇒ **无副本 ⇒ 无删除动作**。

### 11.2 🔴 连带项：**`FollowApiRedisDisconnectedFastFailTest` 是「显式 `@Import` 域内副本」** ⇒ 副本一删就**编译不过**

⇒ 收编 = **删 1 份副本 ＋ 重写这 1 份守门的装配方式**（漏了后半段 ⇒ 编译红）。

1. `@Import(com.rag.follow.follow.api.config.RedisDisconnectedBehaviorConfig.class)`
   ⇒ 换成 `@ImportAutoConfiguration(com.rag.common.redis.GoStyleRedisClientAutoConfiguration.class)`；
2. **补「收编真的发生」的两条断言**：
   - `context.getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` ⇒ **size == 1**；
   - 该 map 的 key 含 **`goStyleRedisClientOptionsCustomizer`**（共享层那份的固定 bean 名）。

⚠️ **为什么必须补**：`@ConditionalOnMissingBean` 是「**遮蔽**不是**叠加**」⇒ 域内副本还在时共享层那份**根本不创建**
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
⇒ ⇒ **我域 FastFail 守门会立刻变红** ⇒ ⇒ **等整合者把 G4 分支合进主干后一次性执行**（配方见 §11.2）。

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

③ 🟢 **口径边界**：follow 域**零产物层 IT** ⇒ ⇒ **`mvn test` 口径 == 全量口径** ⇒ ⇒ 本域 68 条**无需**加「不含 IT」的边界说明 ⇒ ⇒ 且已按「maven 模块汇总行 == surefire XML 逐文件求和」双口径相等采信 ✅。
