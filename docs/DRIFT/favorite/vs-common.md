<!-- @new 2026-10-27 · `favorite-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G3 波次工作树（`align/g3-article-comment-favorite-hot`）**  · **2026-10-27 03:0x G3 阶段 1 复核轮：§1 状态复核 ＋ §2 九轴逐轴填实 ＋ §3 自修项 ＋ §4 派单项（全部带核对日期与证据强度）** -->

# `favorite-common` lane 台账 —— 「favorite ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/favorite/favorite-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G3` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `favorite-common`（**原第 3/10 条**） |
| **现分支 / 工作树** | **`align/g3-article-comment-favorite-hot`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot`<br>⚠️ 原 `align/favorite-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27`（= lane 脚手架落地那次提交）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/favorite/{favorite-api, favorite-rpc, favorite-common, favorite-proto}` |
| 域侧台账 | [`rpc.md`](rpc.md) · [`api.md`](api.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/favorite/**` ＋ `docs/DRIFT/favorite/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |
| **本轮复核口径** | **核对日期 2026-10-27** · 阶段 1（**全只读对账**，不落任何代码）· 真源 = `Sea-RideTheWind_golang`（`go.mod:17` 锁 go-zero **v1.10.2**，框架源码在 `/Users/pr/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2/`）· **证据强度上限 L4**（本域有域级双侧活体探针） |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取 + 同日复核**）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `E21` | `grpc-inprocess` 是 `test` scope ⇒ 运行时 classpath 上整个 artifact 消失 | `service/common`（pom 依赖作用域） | ✅ **已修**（global L12/L13 · 随 `4f839f9` 合入主干 ⇒ 真缺 = 0） | [`rpc.md`](rpc.md) |
| `E62` | 共享层 `DeadlineQueryTimeoutInjector` 的**毫秒精度** + 「deadline 已过 ⇒ 不执行 SQL」两处行为变化 | `service/common` | ✅ **已修并合回主干**；✅ **favorite 域零条依赖旧行为**（第十九轮实测） | [`rpc.md`](rpc.md) |
| `E65` | 出向 gRPC 调用**不继承请求 deadline**（**2026-10-30 订正**：共享层 `53bc05ec` 起 `RpcInterceptor.onHalfClose` **也绑** `GoStyleRequestDeadline` ⇒ `applyTo` **不再是 no-op** ⇒ 本域两条出向出口**已继承**入向 deadline ⇒ 域侧事实**已消解**） | `service/common` | 🔴 **OPEN**（**复核 2026-10-27 仍 OPEN** ⇒ **2026-10-30 域侧事实已变**）：`favorite-rpc` 两处出向 client（`client/UserRpcClient.java:55-57` / `client/ArticleRpcClient.java:56-58`）在 gRPC 服务端路径上的 `applyTo` **已真正生效**（载体已绑 ⇒ 出向预算 = `min(入向剩余, 2s 封顶)` ⇒ 与 Go 的 `context.WithTimeout(depCtx, 2000ms)` 同形）⇒ ⇒ **域侧那半已闭合**；⚠️ **销账权不在本域**（落点 `service/common`）⇒ 本域**不自行改判 OPEN/CLOSED** ⇒ 证据与结论见 [`rpc.md`](rpc.md) 的「共享层『出向 deadline 载体』落码后的本域侧确认」段 | [`rpc.md`](rpc.md) |
| `E68` | 「行号合法但指错文件」的错锚点判据（共享复核器第 4+ 层补判据） | 共享工具 | ✅ **已落**（`97a1fa8` / `0107845`）⇒ favorite 域**抓出 1 条**，已订正 | [`rpc.md`](rpc.md) |
| （域侧 · 非编号） | `favorite-rpc` **缺 `baseline-version: 0`** ⇒ 同 `article-rpc` 的基线缺陷（起得来、首次查库才炸） | **域侧** `service/favorite/favorite-rpc/src/main/resources/application.yml:104-109` | 🔴 **未修**（**复核 2026-10-27 实测确认**：flyway 段 = `enabled:true` / `baseline-on-migrate:true` / `locations` / `schemas` / `table: flyway_schema_history_favorite` ⇒ **无 `baseline-version`**） | — |
| （域侧 · 非编号） | `favorite-api` **无 `rag.http.go-form-limit.mode` 声明** ⇒ 落缺省 `off` ⇒ **无 form 参数闸门** | **域侧** `service/favorite/favorite-api/src/main/resources/application.yml` | 🔴 **未修**（**复核 2026-10-27**：实测无该键 ⇒ 默认 `OFF`；⇒ 与 Go 侧**不一致**，详见 §2 的 A3） | [`api.md`](api.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ ✅ **2026-10-27 复核轮已就地订正/确认 6 条**（`E65` 复核仍 OPEN ＋ 两条域侧项复核 ＋ 三条已闭合项标注日期）。
> ⇒ **教训**：抽取快照的「落点」与「状态」两列**都要独立复核**，落点尤其不能照抄。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体** · 核对日期 **2026-10-27**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | 服务端：`favorite-rpc/.../application.yml:252` `observability.rpc-timeout: 2s` ＋ `FavoriteRpcApplication.java:101,108`（`server.intercept(...)`）；消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:75-82`；出向 api：`favorite-api/.../client/FavoriteRpcClient.java:111`（`CLIENT_TIMEOUT_MILLIS=2000`）`:156-168`（`capByClientTimeout`）；出向 rpc：`favorite-rpc/.../client/UserRpcClient.java:45-47` / `ArticleRpcClient.java:49-51`；**HTTP 侧 3s 预算作用域**（本轮新增）：`favorite-api/.../auth/GoStyleJwtAuthFilter.java:95`（`@Order(+6)`，**只验签**）＋ `favorite-api/.../auth/FavoriteJwtSignatureVerifier.java`（**只验签、不写响应、不查 Redis**）＋ `favorite-api/.../interceptor/FavoriteJwtAuthInterceptor.java`（**消费结果 + 黑名单留在预算内**）| 🟡 **服务端 ✅ / 出向 🔴 ＋ ✅ HTTP 预算作用域已对齐（见结论列末）** —— **服务端一致**：Go `service/favorite/rpc/etc/favorite.yaml` 未配 `Timeout` ⇒ 走 go-zero default **2000ms**（`zrpc/config.go:46`）⇒ 两侧同为 **2s**；Go 侧 `favorite.go:40` `DisableNativeRpcTimeout` ＋ `:49` `NewUnaryServerInterceptor` ⇒ 唯一超时执行者，RAG 侧同构。<br>🔴 **出向 rpc 两处零期限**：`GoStyleRequestDeadline.begin` 的**唯一**主代码调用点在 `service/common/.../observability/HttpMiddleware.java:304`（HTTP worker）⇒ gRPC 服务端路径**永不绑定** ⇒ `applyTo` 恒返回原 stub ⇒ `favorite-rpc → user-rpc / article-rpc` **既无 2s 客户端超时、也无服务端 deadline 继承**；Go 侧 `zrpc/internal/client.go:115-117` + `clientinterceptors/timeoutinterceptor.go:17-29` **无条件**套 2s 且经 `depCtx` 传播父 deadline ⇒ **= `E65`**（落点共享层 ⇒ §4-G4-1）<br>✅ **HTTP 侧 3s 预算作用域已对齐（2026-10-27 · 本轮落码 · §3-L-8）**：Go 的计时器在 Authorize **之后**起算 ⇒ **验签在预算之外**；RAG 原先把验签内联在 MVC interceptor（预算**之内**）⇒ **真漂移** ⇒ ⇒ 本轮把验签搬进域内 `auth/GoStyleJwtAuthFilter`（`@Order(+6)` ⇒ 落在 `+3 Traceparent` **之内**、`+7 HttpMiddleware（3s 预算）` **之外**）⇒ **验签不再计入预算**；守门 `FavoriteApiJwtVerificationScopeTest` **7 例 ＋ 变异验证 3/3 精确变红** | **L3**（服务端侧：Go L1 ＋ RAG 装配面；**HTTP 预算作用域侧 = L3**：真 Spring 上下文装配层 ＋ 既有真 Tomcat 守门）<br>**L1**（出向侧：零期限是可静态判定的否定事实） | 2026-10-27（本轮订正 **HTTP 预算作用域**） |
| **A2** | gRPC 拦截器链 | `FavoriteRpcApplication.java:100-109`（`@Bean GrpcServerConfigurer` ⇒ `server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps))`）＋ `:68-85`（`@Import RpcServerInterceptorFactory` / `ObservabilityProperties` / `GoStyleProtectionConfiguration`）；链序唯一定义点 `service/common/.../protection/GoStyleRpcProtectionChain.java:61-78`（recover → breaker → shedding → 业务观测） | ✅ **服务端链一致** —— Go `zrpc/server.go:144-167`（`setupUnaryInterceptors`：Trace/Recover/Stat/Prometheus/Breaker/Shedding/Timeout 逐条按 `c.Middlewares.*` 装配）⇒ RAG 三层 ＋ 业务观测层的**顺序与判据逐条对齐**；breaker key＝gRPC 全方法名（`GoStyleRpcBreakerInterceptor.java:117-129`）、shedding `fail()` 判据＝客户端 deadline 过期而非任意 `DEADLINE_EXCEEDED`（`GoStyleRpcSheddingInterceptor.java:56-66`）⇒ 与 Go 语义同。<br>🔴 **但出向侧无熔断（client breaker 缺失 · 本轮补记）**：`GoStyleGrpcClientBreakerInterceptor.java:103-106` 是 `@Component` ＋ **`@GrpcGlobalClientInterceptor`** ⇒ **只能靠组件扫描生效**；而 `favorite-rpc` **刻意不扫 `com.rag.common`**（`FavoriteRpcApplication.java` 的 `@SpringBootApplication` **无** `scanBasePackages`）＋ 其 `@Import` 清单**不含**该类 ⇒ 该拦截器**不在容器里** ⇒ **favorite-rpc → user-rpc/article-rpc 的出向调用无熔断**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）⇒ **落点在共享层 ⇒ §4-G4-5**。<br>⚠️ **本域确实有出向调用**（不是 N/A）：`favorite-rpc` 实测 **`@GrpcClient` = 2**（`client/UserRpcClient.java` / `client/ArticleRpcClient.java`）⇒ 缺口**可观测**。✅ **favorite-api 侧在位**（扫 `com.rag.common`）⇒ 只有 rpc 侧缺<br>🟡 Go 专属的 `Stat`/`Prometheus` 两层 RAG 改用 Micrometer 承接（**表达层**，非行为漂移） | **L3**（链序 ＋ `@Import`/`@Bean` 装配面扫描）；🟡 **本域缺「真触发」级装配测试**（见 §3-L-5）<br>（client breaker 缺失侧为 **L1**：否定事实可静态判定） | 2026-10-27 |
| **A3** | HTTP 保护链 | 共享层 `service/common/.../protection/{GoStyleBreakerFilter,GoStyleCpuSheddingFilter,GoStyleMaxBytesFilter,GoStyleMaxConnsFilter}.java`（靠 `favorite-api/.../FavoriteApiApplication.java:36-39` 的 `scanBasePackages` 含 `com.rag.common` ⇒ 四个 Filter ＋ `GoStyleTraceparentFilter` 全部注册）＋ 共享层 `service/common/.../http/{GoStyleFormValueLimitInterceptor,GoStyleFormLimitMode,GoStyleTomcatParameterLimitCustomizer}.java`（本域 `off` ⇒ 共享层那道**不装**）；**域侧闸门** `favorite-api/.../service/FavoriteApiSupport.java:369-392`（`goFormFirstValue`）＋ `:428-436`（`parameterMap`） | ✅ **一致（闸门在域内，不在共享层）** —— Go 侧 favorite 的 **7 个 handler 自己捕获 `httpx.Parse` 的错** ⇒ `httpx.OkJsonCtx` ＋ `{Code: ErrorInvalidParam}` ⇒ **HTTP 200 + `{code:5003, msg:"参数错误", data:null}`**（`get_favorites_by_folder_handler.go:18-25`）⇒ ⚠️ 与「是否注册 `httpx.SetErrorHandler`」**无关**（favorite 未注册，但 handler 自捕获 ⇒ 仍走 200+5003）。<br>✅ **RAG 侧同形状，且是域内实现**：共享层那道闸门（`off` 档）**不是**本域的闸门 ⇒ 本域自己逐字复刻了 Go 的 2048 判据（空值不计数 ＋ 计数跨所有 key 共享 ＋ 第 2049 个非空值抛错）＋ 把 Tomcat 的「参数过多」异常**归一**到同一个出口 ⇒ 统一落 **HTTP 200 + `{code:5003, msg:"参数错误", data:null}`**（与 Go 逐字同形状）⇒ ⇒ **两侧可观察行为一致**。<br>✅ **运行证据（L4）**：`FavoriteApiRealTomcatGateTest`（**真 Tomcat**）4 组判别用例（2049 个非空值 ⇒ 200 + `code:5003` · 2048 个非空值 ⇒ **放行** · 容器层 2049 个「值」⇒ 200 + 5003）＋ `tools/api-envelope-probe/favorite/`（**双侧活体对拍 EXIT=0** ⇒ 逐字节相同）。<br>🟡 **一处有界残余**（**不是**本轴缺口，已登记在 `docs/DRIFT/favorite/api.md` §3，跨域收口在 global）：总「值」数 2049~10000 且非空值数 ≤2048 ⇒ Go 放行（200 + 业务结果）/ RAG 拒（200 + 5003）⇒ 与 hot 域 `DRIFT-HOTAPI-030` 是**同一格** ⇒ 改 `server.tomcat.max-parameter-count` 会被全仓守门 `tools/difftest/check-tomcat-limits.py` 打红 ⇒ **不在 favorite 域单方面改**。<br>📌 **2026-10-29 对齐（台账对齐 phase · W-E lane）—— 原值保留 + 追加**：⚠️ **上面这条残余描述的是 2026-10-27 之前的状态** ⇒ **该窗口已闭合** —— 共享层把容器上限**无条件**抬到 **10001**（`service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java`：`MAX_PARAMETER_COUNT = 10001`，`customize(...)` **无 `mode` 分支**）⇒ **Tomcat 不再在 2048 处拒** ⇒ 「2049~10000 且非空值 ≤2048」这一格 **Go 放行 / RAG 也放行** ⇒ ⇒ **原 🟡 残余已消**（**2026-10-29 复核代码仍如此**）。<br>⇒ **本轴（A3 HTTP 保护链）当前唯一仍 OPEN 的跨域格 =「段数轴（后续格）」**：总**段数** > Go 的 `net/url` `defaultMaxParams = 10000` ⇒ Go 解析失败 / RAG 放行 ⇒ **RAG 翻转点 = 10002 段 / Go = 10001 段** ⇒ 共享层 `MAX_PARAMETER_COUNT` **至今 = 10001** ⇒ 差一格 ⇒ 归 `DRIFT-HOTAPI-030` 后续格 / `global` / G4 ⇒ 逐条 ⇒ 本文件 §4「仍未闭合的下一格」行 + `F-3` 行。<br>✅ 其余四层（breaker / shedding / maxBytes / maxConns）阈值与拒绝形状（裸 503 / 裸 413 空 body）**与 Go 逐条同值同形**<br>✅ **验签与保护链的相对次序已对齐（2026-10-27 · 本轮 · §3-L-8）**：本域新增的 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`）落在 `+3 Traceparent` **之内**、`+7 HttpMiddleware（3s 预算）` **之外**、`+8 MaxBytes` **之内** ⇒ ⚠️ **它排在 MaxBytes 之前** ⇒ **若它自己写 401 就会压过 413**（与 Go 相反）⇒ ⇒ 故它**绝不写响应**（只验签、只写 request attribute、401 仍由 interceptor 抛）⇒ **413 仍压过 401**（守门：`FavoriteApiJwtVerificationScopeTest` 的「413 压过 401」＋「未注册路径仍 404」两条 ＋ 既有真 Tomcat `FavoriteApiRealTomcatGateTest` 的「超限 body + 无 token ⇒ 413」；**变异验证**：让 Filter 自己写 401 ⇒ 这两条**当场变红**） | **L4**（真 Tomcat 门禁 ＋ 双侧活体对拍 EXIT=0；**次序面新增 L3**：容器装配层守门） | 2026-10-27（本轮补 **验签与保护链次序**） |
| **A4** | 错误码 / envelope | 共享层 `service/common/.../response/Response.java:14-17`（三字段、**无** `@JsonInclude` ⇒ `data:null` 也输出）＋ `service/common/.../http/GoStyleRouterErrorResponses.java:121-140`（404 = `text/plain; charset=utf-8` + `nosniff` + `"404 page not found\n"`；405 裸状态码 + `Allow`）；域侧 `favorite-api/.../advice/GlobalResponseAdvice.java:65-89` / `GlobalExceptionHandler.java:80-136,144-190,200-230` | ✅ **一致** —— Go `service/common/response/response.go:3-7`（`Code/Msg/Data` 三字段**无 omitempty**）⇒ `data:null` 一定出现；业务错误恒 **HTTP 200 + `{code,msg,data}`**；gRPC 失败经 `FavoriteGrpcError.bizCodeFromError`（先按 description 反查文案、再按 6 码白名单兜底）还原业务码 ⇒ 与 Go `service/favorite/common/errmsg.go:108-137`（`BizCodeFromError`）**同序**；404/405 裸响应形状与 Go `rest/router/patrouter.go:73,79-82` 一致。<br>🟡 **鉴权失败 body 形状**：Go 空 body（`rest/handler/authhandler.go:106-122`）/ 两字段无 `data`（`checkblacklist_middleware.go:39-45`）vs RAG 统一三字段（**2026-10-27 本轮起落点已迁**：`favorite-api/.../auth/FavoriteJwtSignatureVerifier.java` 的 `unauthorized()` —— 原在 `FavoriteJwtAuthInterceptor` 内联，随 §3-L-8 的验签搬迁）⇒ HTTP 码与业务码 5004 对齐，**差 body 字段集合** ⇒ 已登记 `DEV-FAV-API-001`（✅ 2026-10-25 用户签字）<br>✅ **本轮落码后形状逐字未变**：验签失败由 Filter 产出**异常对象**、interceptor **原样重抛** ⇒ `@ExceptionHandler` 仍管得到 ⇒ 仍落 **401 + `{code:5004, msg, data:null}`**（守门 `FavoriteApiJwtVerificationScopeTest` 的「401 信封形状不变」一条 ＋ **变异验证**：Filter 自己写 401 ⇒ 该条当场变红） | **L1**（两侧逐行对照）；**L3**（信封形状：真 Spring 上下文行为层守门） | 2026-10-27 |
| **A5** | 端口治理 | `favorite-api/.../application.yml:51` `server.port: ${FAVORITE_API_PORT:18893}` ＋ `favorite-rpc/.../application.yml:104` `grpc.server.port: 8082` ＋ `:126` `reflection-service-enabled: false`；测试用专用段 `favorite-api/src/test/.../support/RealTomcatPort.java:61,64`（**21300–21399**，避开 OS 临时段 49152–65535） | 🟡 **OBS** —— ✅ **gRPC 8082 与 Go 完全一致**（Go `service/favorite/rpc/etc/favorite.yaml:2` `ListenOn: 0.0.0.0:8082`）；🟡 **HTTP 18893 ≠ Go 8890**（`service/favorite/api/etc/favorite.yaml:3` `Port: 8890`）⇒ 属 `Infrastructure.md §3` 给 favorite 域预占的号段（**已记录的部署口径差异**）。⚠️ **Go 的 DevServer 面（16062/16063 metrics/healthz/pprof）RAG 没有** ⇒ 已登记为域内未决项 **U9**（部署形态类，待拍板）。🔴 ~~**`favorite-rpc` 无 `RealTomcatPort` 等价物** —— 其 gRPC 测试用 `port=0`（OS 临时段）⇒ **暴露面未治理**~~ ✅ **已治理（2026-10-27 阶段 2）** —— 新增域内 `favorite-rpc/src/test/.../support/RealGrpcPort.java`（**专用段 21400–21499**，与 favorite-api 的 21300–21399 **不重叠**；挑端口前在 `0.0.0.0` 与 `127.0.0.1` **两个地址上做 bind 自检**，macOS 上二者可共存 ⇒ 缺一不可）⇒ 8 个真进程测试的 `grpc.server.port=0` **全部改掉**（改由 `@DynamicPropertySource` 提供 `RealGrpcPort::pick`）⇒ 配套守门 `RealGrpcPortTest`（4 例，含「被占 ⇒ 判为不可用」的两组判别用例）<br>📌 **2026-10-27 G3 再订正（段号已迁，原文保留）**：**专用段从 `21400–21499` 迁到 `22100–22199`** —— 原段**嵌套在 favorite 域的「探针段」`21400–21599` 内**（global 段表 §4.10.19.3）⇒ ⇒ **两边都以为对方不在自己段里** ⇒ ⇒ 迁到 global 段表**预分配给 favorite-rpc** 的 `22100–22199`（2026-10-27 实测整段可绑 **100/100**）⇒ ⇒ **探针段 `21400–21599` 不动**、**脚手架段 `22050–22099` 不动** ⇒ ⇒ `tools/probe-gate/favorite/check-port-ownership.sh` 的段断言**也不用动**（它守的是**探针工具自己的段**）⇒ ⇒ 改动面 = `RealGrpcPort.java` 的 **2 个常量 + javadoc** | **L3**（yaml 静态 ＋ 测试支撑类源码 ＋ 守门用例；未实测端口占用） | 2026-10-27 |
| **A6** | errmsg 契约 | `favorite-common/.../errmsg/FavoriteErrorCode.java:41-108`（24 个枚举，声明顺序＝Go const 块顺序）· `FavoriteErrorMessages.java:44-93`（24 条文案，未知码回落 `"FAIL"`）· `FavoriteGrpcError.java:94-98`（`grpcError`＝标准码＋文案 description）`:129-142`（文案反查表，`putIfAbsent`＝Go 的 first-write-wins）`:234-242`（`extractStatus` **单层**判定，不沿 cause 链） | ✅ **一致** —— 24 个码值与文案与 Go `service/favorite/common/errmsg.go:8-41`（const）`:43-76`（codeMsg）**逐条相同**（含 5002＝英文 `"not found"`、5004＝`"未登录或登录已失效"`）；`bizCodeFromError` 判定顺序＝null→200 / 非 status→1015 / description 反查 / 6 码兜底 / default→1015，与 Go `:108-137` **同序**；`FAILED_PRECONDITION` **刻意不入**白名单（还原 5304 只能靠文案）⇒ 与 Go 一致 | **L4**（`bash tools/errmsg-probe/favorite/run-parity.sh` ⇒ **EXIT=0**：Go sha256 == Java sha256 == `03be9aa6e55916453e015e0b9c11139d1a64f7118256ab5da46939a139150f81`，**108 行** = CONST ＋ TABLE ＋ FALLBACK ＋ WIRE ＋ BIZCODE；真源 sha256 校验通过）<br>ℹ️ OBS 段 2 条已知不可达差异仍在登记状态（`obs_custom_grpcstatus` Go=5007/Java=1015 · `obs_ok_status` Go=200/Java=5001） | 2026-10-27 |
| **A7** | proto 契约 | `service/favorite/favorite-proto/src/main/proto/favorite.proto` | ✅ **一致** —— 逐条对照：10 个 message（`FavoriteFolder`/`FavoriteItem`/`CreateFavoriteFolderReq·Resp`/`CreateFavoriteReq·Resp`/`DeleteFavoriteFolderReq·Resp`/`DeleteFavoriteReq·Resp`/`UpdateFavoriteFolderNameReq·Resp`/`GetFavoriteFoldersReq·Resp`/`GetFavoritesByFolderReq·Resp`）＋ `service FavoriteService` 的 7 个 rpc ⇒ **message 名 / 字段名 / 字段号 / 类型 / 顺序全部一致**；唯一差异在 option 块：Go `option go_package = ".";` ⇒ RAG 换成 `option java_package = "com.rag.favorite.favorite.proto";` ＋ `option java_multiple_files = true;`（**表达层**，Go 专用项 Java 不消费）；注释与空行也保持。 | **L1**（逐行对照）；📌 **本域有现成双侧探针** `tools/proto-wire-probe/favorite/run.sh`（Go 侧跑真源生成码 ＋ sha256 校验，Java 侧跑**真实** `favorite-proto` 生成码的 `FavoriteProtoWireParityTest`，两侧对同一份冻结 `expected.txt`）⇒ **可升 L4**，~~本轮未跑~~ ✅ **已跑（2026-10-27 阶段 2）= EXIT=0**（见下） | **L4**（`bash tools/proto-wire-probe/favorite/run.sh` ⇒ **EXIT=0**：① 真源生成码 `service/favorite/rpc/pb/favorite.pb.go` **逐字节复制 + sha256 校验**（`ee7593476da63d97…`）② Go 侧（真 `google.golang.org/protobuf` v1.36.11，依赖版本**从真源 go.mod/go.sum 整体继承**）输出 **6 条 `WIRE|<name>|<hex>`** ③ 与冻结的 `expected.txt` **逐字节 diff 一致**（sha256 `03aed8cc1f785864…`）④ Java 侧跑**真实** `favorite-proto` 生成码的 `FavoriteProtoWireParityTest` ⇒ **双侧逐字节一致**）<br>ℹ️ **本域的「契约层」与「wire 字节层」两道网都在**：契约层＝`favorite-rpc/src/test/.../proto/FavoriteProtoWireParityTest`（字节级）；**字段号/枚举值/presence 的契约层**由 `docs/DRIFT/favorite/proto.md` 的逐行对照承担（`check-proto-static.sh` **不覆盖 favorite** ⇒ 它只覆盖 user/admin/task ⇒ ⚠️ **favorite 的 proto 字段号仍无静态守门**，见 §3-L-6 的剩余项） | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | `favorite-rpc/.../db/migration/V1__create_favorite_folder.sql:14-40` · `V2__create_favorite_item.sql:24-69` · `V3__create_favorite_fact_outbox.sql:25,32,34` · `V6__align_favorite_schema_with_go.sql:19-165` · `V7__align_favorite_outbox_time_columns_with_go.sql:31-32`；jsonb typeHandler `favorite-rpc/.../mapper/JsonbTypeHandler.java:33-48` | ✅ **一致** —— 时间列全 `timestamptz`；`jsonb` **保留为 jsonb**（轴描述的「jsonb⇒text」**在本域不适用**：`favorite_item` 无 jsonb 列，`outbox.payload` 是 jsonb 且有 TypeHandler）⇒ 与 Go `type:jsonb` 一致；outbox 时间列已按 Go **去掉 DEFAULT**（V7）。<br>✅ ~~🔴 **但 `baseline-version: 0` 缺**（`application.yml:86-91`）⇒ 非空 schema 下 **V1 被整条跳过** ⇒ **域侧自修**（见 §3-L-1）~~ ✅ **已补（2026-10-27 阶段 2）** —— `application.yml` 的 flyway 段已加 `baseline-version: 0` ＋「为什么必须钉 0」的论证注释（照抄 `comment-rpc` 同款写法）⇒ 非空 schema 下 **V1 会照常执行**（见 §3-L-1）。<br>ℹ️ **V7 的论证**（质量很高，供参照）：Go 侧 gorm tag 是 `autoCreateTime`/`autoUpdateTime`（**无 default tag**）⇒ AutoMigrate 产物无 DEFAULT；且 **GORM 的 AutoMigrate 会主动把已存在的 DEFAULT 删掉**（`gorm.io/gorm@v1.31.1/migrator/migrator.go:543-550` 的「default value → null」分支）⇒ Go 侧 live schema 确定无 DEFAULT ⇒ V7 用 `ALTER COLUMN … DROP DEFAULT` 对齐（理由：Go 侧漏绑时间列会**硬失败**，RAG 侧留 DEFAULT 会**静默兜底**成 DB 时钟 ⇒ 两边分叉且无告警） | **L4**（`bash tools/ddl-parity-probe/favorite/run.sh` ⇒ **EXIT=0**：MAIN 指纹 `fd69ec7e5854b1cc37734c706fcdebf8c39bcf45a1b9765384d6725b4e6854ab` **两侧一致** ＋ OBS 段 **13 条逐条一致**（12 命名差异 ＋ 1 条表达层 `::text` 归一）；Go 侧跑真源 `InitDB()`（AutoMigrate ＋ 4 个真源 .sql，**逐字节复制 + sha256 校验**），RAG 侧跑**真 Flyway V1..V7**，两侧共用同一份 dump 查询）<br>＋ 配置面 L3 | 2026-10-27 |
| **A9** | trace / otel | 共享层 `service/common/.../observability/ObservabilityConstants.java:57-116`（属性名 = **`sea.error.kind` / `sea.biz_code` / `sea.biz_msg` / `sea.timeout` / `sea.slow` / `sea.duration_ms`**）+ `RpcInterceptor.java:207-336`（SERVER span ＋ `rpc.system`/`rpc.method`/`rpc.grpc.status_code`）+ `service/common/.../http/GoStyleTraceparentFilter.java:188-191`（`@Component` ＋ `@Order(HIGHEST_PRECEDENCE+3)`） | 🔴 **漂移（域侧业务 span 全缺）** —— 共享层的 span 属性与 `Traceparent` 传播**已就位**；但 **favorite 域自己的业务 span 全缺**：Go 侧 favorite-api 7 个（`FavoriteAPI.CreateFavoriteFolder` 等，`api/internal/logic/favorite/*.go:33,42-48`）＋ favorite-rpc 7 个 logic span（`rpc/internal/logic/*.go:42-49`）＋ 2 个依赖 span（`Dependency.User.GetUser` / `Dependency.Article.GetArticle`，`rpc/internal/logic/helper.go:93,96,127,131`）及 `biz.user_id` / `biz.folder_id` / `biz.favorite_id` / `biz.target_id` / `biz.target_type` / `biz.folder_name` / `biz.article_id` 属性 ⇒ RAG 侧**一个都没有** ⇒ 遥测里 favorite 的业务维度全空（`favorite/**` 全域 grep `otel|OpenTelemetry|Span|Tracer` 仅 2 处命中，均为固定依赖 touch）。<br>⇒ **域侧可自修**（域内私有 `FavoriteSpans` 类，**不动共享层**）<br>ℹ️ **属性名是 `biz.*`（本域）**：共享层通用前缀是 `sea.*`；`audit.*` 只存在于 users/admin 域 ⇒ **本轴定义的「`audit.*`」措辞对本域 = N/A** | **L3**（两侧逐行 = Go 侧 **L1**；RAG 侧 = **L4 运行证据**：`FavoriteApiSpanWiringTest` **11 条** ＋ `FavoriteRpcSpanWiringTest` **12 条** 全绿 ＋ **7 个变异全部精确转红**）⚠️ **Go 侧无同时跑的探针** ⇒ **不是「双侧活体对拍」** | 2026-10-27 |<br>🟡 **2026-10-27 · G3 落码订正：本轴原结论「🔴 漂移（域侧业务 span 全缺）」已落码** —— 域侧私有 `FavoriteSpans`（`service/favorite/favorite-common/.../span/FavoriteSpans.java`）已建，**16 个 span 全部落位**（api 侧 7 个 + rpc 侧 9 个，含 2 个 `Dependency.*` 依赖 span）⇒ ✅ **守门已补（第二十九轮）** ⇒ `FavoriteApiSpanWiringTest`（**11 条**）＋ `FavoriteRpcSpanWiringTest`（**12 条**）⇒ 自装**真 OTel SDK** ＋ 收集型 exporter ⇒ 断言**导出的 span**（AGENTS.md §3.3.3）⇒ ⇒ **证据强度升到 L3**（Go 侧 L1 逐行 ＋ RAG 侧 L4 运行证据）⇒ ⇒ **本轴结论改读作「🟢 已落码 · 已验证（到 L3）」**（原「🔴 全缺」**保留作历史**，不改写）⇒ ⚠️ **Go 侧仍无同时跑的探针** ⇒ ⇒ **不是「双侧活体对拍」** ⇒ 详见 [`api.md` §16](api.md#16-🟡-favorite-域的-16-个业务-span-已落码go-侧-oteltracer-逐条对齐-2026-10-27g3--l-4) ＋ [`api.md` §16.6](api.md#166-✅-变异验证--7-个变异全部精确转红第二十九轮)<br>🟢 **2026-10-28 · G3 订正 G4 §2 A9 行 ＋ §4.10.64 ⑤⑥ 的两条读数（G4 原文保留，不改写）** —— G4 在共享面写「🔴 **守门未写** ⇒ 本轴 = 🟡 半闭环（代码在 · 网未张）」＋「⚠️ **对域内的警告**：域内 span 若只调 `recordException` ⇒ status 恒 UNSET ⇒ **与 Go 的 `RecordError` 漂移**」⇒ ⇒ **两条对本域【均不成立】**：<br>① **「守门未写」不成立** ⇒ 域内守门**已存在**：`FavoriteApiSpanWiringTest`（**11 条**）＋ `FavoriteRpcSpanWiringTest`（**12 条**）＝ **23 条** ⇒ **2026-10-28 复跑：23 条 / 0 failures / 0 errors / 0 skipped / BUILD SUCCESS**（`mvn -B -o --fail-at-end -pl service/favorite/favorite-api,service/favorite/favorite-rpc -am test -Dtest='FavoriteApiSpanWiringTest,FavoriteRpcSpanWiringTest'` ⇒ 日志 `/tmp/g3_span_wiring_rerun.log`）⇒ 且**变异 7/7 精确转红**（[`api.md` §16.6](api.md#166-✅-变异验证--7-个变异全部精确转红第二十九轮)，其中 **MUT-C 直接钉「status 恒 UNSET」**）⇒ ⇒ **本轴 = 🟢 已落码 · 网已张**（原「🟡 半闭环」**保留作历史**）⇒ ⇒ **根因 = G4 只读了共享层与代码面，漏看域内守门**（域侧守门是**域内文件** · 零 Docker / 零 Testcontainers）；<br>② **「status 漂移」不成立** ⇒ Go otel-go **v1.44.0** 的 `RecordError` **不改 status**（接口契约 `go.opentelemetry.io/otel/trace@v1.44.0/span.go:47-51` ＋ SDK 实现 `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:626-630` javadoc ＋ `:631-655` 实现**无 `SetStatus` 调用**）⇒ ⇒ **Go 侧 favorite 的 span status 本就恒 UNSET**（`service/favorite/**` 的 `SetStatus` **零命中** · `RecordError` **59 处** · 且**不经**共享层 `Mark*` ⇒ 域内 span 的 status 归域内代码独占）⇒ ⇒ **RAG 侧只调 `recordException`（UNSET）⇒ 两侧一致 ⇒ 域侧零改动**（**不改 `FavoriteSpans`**）⇒ ⚠️ **方向别搞反**：共享层 `Observability.java:108-109` / `:131-132` 的**显式** `setStatus(ERROR)` **必须保留**（Go 侧 `observability.go:83` / `:105` / `:128` **自己就显式 `SetStatus`**）⇒ ⇒ **G4 的「共享层是对的」结论保留、仅【理由】订正** ⇒ ⇒ ⛔ **切勿因理由被订正就删共享层的 `setStatus`**（删了 = 反向漂移）；<br>⇒ 逐条证据（含 5 行取证表 ＋ 共享层 4 行对照表）⇒ [`api.md` §16.4.1](api.md#1641-🟢-补证2026-10-28-go-侧-recorderror-也不改-status--两侧-status-语义同构--status-漂移不成立)

> **A3 原预填锚点的复核结论（2026-10-27 核 · 原文保留供追溯）**
> 原文断言：「本域 `go-form-limit.mode` = **off（缺省）**；Go 侧：**未注册** ⇒ Go 侧 = **400 + Go 原文**；⚠️ **RAG 侧 CD-6 也刻意置 `off`** ⇒ **两侧都不过 form 限 ⇒ 零触碰** ✅」
> ⚠️ **该锚点已过期并订正**：
> ① **Go 侧 favorite 确实未注册 `httpx.SetErrorHandler`**（实测：全仓仅 `comment/api/commentservice.go:47` ＋ `hot/api/hotcenter.go:53` 注册）—— 这一半成立；
> ② ❌ **「Go 侧 = 400 + 原文」不成立** —— favorite 的 **7 个 handler 自己捕获** `httpx.Parse` 的错 ⇒ 走 `httpx.OkJsonCtx` ＋ `{code:5003}` ⇒ **200 + 业务信封**（`get_favorites_by_folder_handler.go:18-25`）；
> ③ ⚠️ **「两侧都不过 form 限 ⇒ 零触碰」不成立** —— Go 侧**有** 2048 闸门（`rest/httpx/util.go:15`）⇒ 但 **RAG 侧也有**（**域内自装**，见下）⇒ ⇒ **两侧行为一致**（原先此处写「RAG 侧无闸门 ⇒ 两侧行为分叉」＝ **本轮自查订正的假阳性**）；
> ⇒ **教训**：**「未注册 `SetErrorHandler`」不能推出「400 + 原文」** —— 要看**handler 自己有没有捕获** `httpx.Parse` 的错；favorite 与 article 在这一点上**恰好相反**（article 用 `httpx.ErrorCtx` ⇒ 400；favorite 自捕获 ⇒ 200+5003）。
>
> 🔴 **2026-10-27 阶段 2 自查：③ 这一条本身是「假阳性」⇒ 已撤销（§3-L-3）**
> 原判「RAG 侧无闸门」的依据是「`favorite-api` 未声明 `rag.http.go-form-limit.mode` ⇒ 落缺省 `off` ⇒ 无闸门」⇒ **这个推断把「共享层那道闸门没装」当成了「本域没有闸门」**。
> ⇒ **实测**：本域的闸门**在域内自己装**，与共享层那道**无关** ——
> `favorite-api/.../service/FavoriteApiSupport.java:369-392` `goFormFirstValue`（Go `httpx.GetFormValues` 的**逐字复刻**：空值不计数 ＋ 计数跨所有 key 共享 ＋ 第 2049 个非空值抛 `IllegalArgumentException("too many form values")`）
> ＋ `:428-436` `parameterMap`（把 Tomcat 的 `InvalidParameterException` **归一**成 `IllegalArgumentException`）
> ⇒ 统一落 `GlobalExceptionHandler#handleIllegalArgument` ⇒ **HTTP 200 + `{code:5003, msg:"参数错误", data:null}`** ⇒ **与 Go 逐字同形状**。
> ⇒ **运行证据**：`FavoriteApiRealTomcatGateTest`（**真 Tomcat**）4 组判别用例（2049 非空 ⇒ 200+5003 / 2048 非空 ⇒ 放行 / 容器层 2049「值」⇒ 200+5003）＋ `tools/api-envelope-probe/favorite/`（**双侧活体对拍 EXIT=0**）。
> ⇒ ⚠️ **元教训**：**「配置里没写」≠「没有」**（AGENTS.md §3.3.1 的同款形态，但这次栽在**域内 vs 共享层**的边界上）⇒ **判某个机制有没有，必须读到「本域真正消费它的那段控制流」**，不能只看「共享层的开关有没有打开」。

### 📌 订正注记（2026-10-27 · G3 · 21:0x）：A1 轴 **1 处引用文件名不存在**已实修（**行号原本就对**）

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上表 A1 轴的**结论原样有效** ⇒ 本轮只改**引用指针**。

| 位置 | 原引用 | 事实 | 实修 |
|---|---|---|---|
| 上表 **A1** 轴（L-8 行） | `service/favorite/api/favoritecenter.go:30` | 🔴 **该文件不存在**（真源 + 本仓全仓搜 `favoritecenter.go` ⇒ **0 命中**）⇒ 真源真名 = **`service/favorite/api/favorite.go`**（38 行） | ⇒ 改为 **`service/favorite/api/favorite.go:30`**（**行号不变** ⇒ `:30` 正是 `server.Use(observability.NewHTTPMiddleware(...))` 那一行 ⇒ **原行号本来就对**）|

⇒ ⚠️ **一条方法论（跨域可复用）**：**真源的 api 入口文件名【逐域不同】，不能按 go-zero 惯例推** —— 实测：`service/article/api/article.go` · `service/favorite/api/favorite.go`（**无 center**）· 但 `service/hot/api/hotcenter.go` · `service/comment/api/commentservice.go`（**有 center / service**）⇒ ⇒ **按惯例猜文件名 ⇒ 必错** ⇒ ⇒ **必须逐个 `ls` 真源**。
⇒ **核对来源**：G2 lane 通报（2026-10-27 14:10）＋ 本域**自建核对器全量扫**（本域 **1262** 处「文件:行号」引用逐条核「文件存在 + 行号在范围内」）⇒ ⇒ **真缺陷 7 处已全部实修**。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

| # | 事项 | 落点 | 依据 / 修法 |
|---|---|---|---|
| **L-1** | ✅ **补 `baseline-version: 0`（2026-10-27 阶段 2 已落码）** | `service/favorite/favorite-rpc/src/main/resources/application.yml`（flyway 段） | **已落码**：加了 `baseline-version: 0` ＋「为什么必须钉 0 / 为什么关掉 baseline-on-migrate 更糟 / 为什么全新空库测不出来」三段论证注释（写法照抄 `comment-rpc` 同款）。⚠️ **本项落码后**仍需在**非空 schema**下验证（守门：各域 `*FlywaySharedSchemaGateTest` 那类真 PG + 真 Flyway 的用例）⇒ 已列入收口回归项 |
| **L-2** | ✅ **favorite-rpc 两处出向 client 补 2s 客户端封顶（2026-10-27 阶段 2 已落码）** | `favorite-rpc/.../client/UserRpcClient.java`（`userRpc()`）· `ArticleRpcClient.java`（`articleRpc()`）＋ **新增** `favorite-rpc/.../client/GoStyleClientCallTimeout.java`（域内 package-private 工具类） | **已落码**：两个出口都改成 `GoStyleClientCallTimeout.capByClientTimeout(GoStyleRequestDeadline.applyTo(stub))` ⇒ **无请求预算时也硬套 2000ms**（`capByClientTimeout` 在 deadline 为 `null` 时取 `Long.MAX_VALUE` ⇒ min(2s, MAX) = 2s）⇒ 与 Go 的「客户端超时是**拦截器级别**、与有没有 HTTP 超时中间件无关」一致（`zrpc/internal/client.go:113-117` ＋ `zrpc/internal/config.go:15` 的 `default=true` ＋ `zrpc/config.go:31` 的 `default=2000`）。<br>⚠️ **落码前的前提已独立复核**：`GoStyleRequestDeadline.begin` 的**唯一主代码调用点**是 `service/common/.../observability/HttpMiddleware.java:304`（HTTP worker）⇒ **gRPC 服务端路径永不绑定** ⇒ `applyTo` 恒返回原 stub ⇒ 落码前**出向调用确实零期限**（真阳性，非假阳性）。<br>⚠️ **范式照抄本域现成实现**（`favorite-api/.../client/FavoriteRpcClient.java:111,156-168`）⇒ **不发明第三种写法**；本域只是把它提到 rpc 侧的 package-private 工具类，供两个出口共用一处。<br>⚠️ **只解决「2s 封顶」这一半**：「继承服务端 deadline」那一半落点在**共享层** ⇒ §4-G4-1（global `E65`） |
| ~~**L-3**~~ | ⛔ **原项撤销（2026-10-27 阶段 2 · 前提不成立）** —— 原写「🔴 **form 值闸门：装上 ＋ 形状对齐 `200 + {code:5003}`**」，落点写的是「`favorite-api/.../application.yml`（声明 `rag.http.go-form-limit.mode`）＋ `GlobalExceptionHandler` 新增 `@ExceptionHandler(GoStyleFormLimitExceededException.class)`」⇒ **实测证伪**：**本域的 form 闸门早就在位，且是域内实现、与共享层无关** —— `favorite-api/.../service/FavoriteApiSupport.java:369-392` `goFormFirstValue` 逐字复刻 Go `httpx.GetFormValues`（空值不计数 ＋ 计数跨所有 key 共享 ＋ 第 2049 个非空值抛 `IllegalArgumentException("too many form values")`）＋ `:428-436` `parameterMap` 把 Tomcat 的「参数过多」异常**归一**到同一出口 ⇒ 统一落 **HTTP 200 + `{code:5003, msg:"参数错误", data:null}`**（与 Go 逐字同形状）⇒ **原项要修的东西已经修好了**<br>⇒ **证据**：① `FavoriteApiRealTomcatGateTest`（**真 Tomcat**）4 组判别用例（2049 非空 ⇒ 200+5003 / 2048 非空 ⇒ 放行 / 容器层 2049「值」⇒ 200+5003 / 变异 MUT-A～MUT-E）；② `tools/api-envelope-probe/favorite/`（**双侧活体对拍 EXIT=0** ⇒ Go 侧 2049 非空值 ⇒ `{"code":5003,"msg":"参数错误","data":null}`，RAG 侧**逐字节相同**）；③ `docs/DRIFT/favorite/api.md` §2-D4 已记录该修复与其变异证据<br>⇒ **为什么不落码**：① 再装一道闸门 ⇒ **同一个输入被拒两次**（域内那层先拒 ⇒ 共享层那道永远走不到）⇒ 是**重复机制**；② 原落点「声明 `rag.http.go-form-limit.mode`」**会打红全仓共享守门** `tools/difftest/check-tomcat-limits.py`（其 `FORBIDDEN_FORM_LIMIT_MODE_MODULES` 明确列了 `service/favorite/favorite-api` ⇒ 判据是「favorite 不在 global 写权范围」＝ **权限理由，不是语义理由** ⇒ 属共享面缺陷，已登记给 G4/合并收口执行者，**不在本域单方面绕**）<br>⇒ ⚠️ **教训（与本轮另外两处订正同源）**：**「共享层的那道闸门没装」≠「本域没有闸门」** ⇒ 判某个机制在不在，必须读到**本域真正消费它的那段控制流** | — | **撤销，不落码**（A3 行已同步订正为 ✅，见 §2） |
| **L-4** | 🔴 **补 favorite 域自己的 otel span ＋ `biz.*` 属性** | `favorite-api/.../service/*`（7 个 Service）＋ `favorite-rpc/.../service/*`（`FavoriteDomainService` / `FavoriteUserGuard` / `FavoriteArticleSnapshotResolver`） | Go 清单：`api/internal/logic/favorite/*.go:33,42-48`（7 个 `FavoriteAPI.*` span）· `rpc/internal/logic/*.go:42-49`（7 个 `FavoriteRPC.*` span）· `rpc/internal/logic/helper.go:93,96,127,131`（2 个 `Dependency.*` span）。**仓内已有正确先例**：`service/users/admin/admin-rpc/.../AdminSpans.java:91`（域内私有 `ATTR_*` 常量类）⇒ 建同款 `FavoriteSpans` ｜ ✅ **已落码（第二十九轮收尾）**：`FavoriteSpans` 已建 ＋ **16/16 span 落位** ＋ **守门 23 条全绿** ＋ **7 个变异全部精确转红** ⇒ 证据强度 **L3** ⇒ 详见 [`api.md` §16](api.md#16-🟡-favorite-域的-16-个业务-span-已落码go-侧-oteltracer-逐条对齐-2026-10-27g3--l-4) |
| **L-5** | ✅ **favorite-rpc 的测试端口治理（2026-10-27 阶段 2 已落码）** | **新增** `favorite-rpc/src/test/.../support/RealGrpcPort.java` ＋ `RealGrpcPortTest.java`；8 个真进程测试（`FavoriteOutboxTimeColumnTest` / `FavoriteSchemaParityTest` / `FavoriteFolderItemTimeColumnTest` / `FavoriteSqlTimeoutProductionPathTest` / `FavoriteDdlParityTest` / `fact/FavoriteAuthority{LiveParity,LookupTimeout,V2ResponseShape}Test`） | **已落码**：`grpc.server.port=0` → `registry.add("grpc.server.port", RealGrpcPort::pick)`。`RealGrpcPort` 与 favorite-api 的 `RealTomcatPort` 同款（**本域专用段 21400–21499**，与 21300–21399 **不重叠**；挑端口前在 `0.0.0.0` 与 `127.0.0.1` **两个地址**上 bind 自检 —— macOS 上二者可共存 ⇒ **缺一不可**）⇒ 守门 `RealGrpcPortTest` 4 例（专用段内 / 两个地址都能绑 / 两种「被占」判别用例）。⚠️ **为什么必须治**：`port=0` 让 OS 从**临时端口段**（macOS 49152–65535）里挑 ⇒ 那是 **Docker 随机发布容器端口的同一段** ⇒ 别的会话的容器先占住 `0.0.0.0:P` ⇒ 我们再绑 ⇒ **EADDRINUSE ⇒ 整个测试类启动失败** ⇒ 表现为「偶尔红、换个时间又绿」的**环境性抖动** |
| **L-6** | ✅ **A7 proto 回归网 —— 字节层已跑通（2026-10-27 阶段 2）**；🟡 **字段号层仍缺静态守门** | `tools/proto-wire-probe/favorite/`（**共享面**）＋ `favorite-rpc/src/test/.../proto/FavoriteProtoWireParityTest.java`（域内） | ✅ **已跑**：`bash tools/proto-wire-probe/favorite/run.sh` ⇒ **EXIT=0**（① 真源生成码逐字节复制 + sha256 校验 `ee7593476da63d97…` ② Go 侧 6 条 `WIRE|…` ③ 与冻结 `expected.txt` 逐字节 diff 一致 ④ Java 侧真实生成码的 `FavoriteProtoWireParityTest` ⇒ **双侧逐字节一致**）⇒ **A7 的 wire 字节层 = L4**。<br>🟡 **剩余项（不属 G3 写权 ⇒ 登记）**：「把它挂进收口回归的例行执行」= 在**共享脚本**里加一行 ⇒ `tools/**` 是**共享面** ⇒ **G3 不写** ⇒ 登记给合并收口执行者/G4；同理 `check-proto-static.sh` **只覆盖 user/admin/task** ⇒ **favorite 的字段号/枚举值仍无静态守门**（本轮 article / hot 两域已各自补了域内契约网，favorite 若要同款 ⇒ 需按同一范式在域内补，**本轮未做**） |

| **L-7** | 🔴 **删本域 rpc 组件的域侧排序桥**（桥下沉共享层后的冗余清理）|<br>⚠️ **删除时必须连带处理 javadoc（G1 提醒 · 2026-10-27 已逐条核实）**：① 该文件的「**什么时候可以删**：升级到已用 Boot 4 新包名声明 `@AutoConfiguration(after = ...)` 的 MyBatis-Plus 版本之后」⇒ 这是**第二条、独立的**删除条件（MP 自己修好即可删）⇒ **共享层下沉后它对被删组件已被取代** ⇒ **不处理就会留下一条已经不成立的删除条件**；② 「与 … 同款」的**列举已过期** ⇒ 实际是 **9 个** rpc 组件（article / comment / favorite / follow / like / message / task / users-user / users-admin），不是文件里写的那几个 ⇒ 随文件删除或一并订正| `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ `service/favorite/favorite-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（删那一行注册） | **前置依赖**：① **G4 共享层桥先落码**（否则本域删了、共享层还没加 ⇒ MP 退避 ⇒ **启动失败**）② 阶段 2 放行 ⇒ **本项属阶段 2**。⚠️ **本域侧删除是纯删减、可独立验证**（域侧 `FavoriteSqlTimeoutProductionPathTest`（真 Spring 上下文）应仍绿 ⇒ 说明共享层桥已接管排序）⇒ **删完必须跑本域的真上下文守门**（证明共享层桥真的接管了排序）<br>✅ **2026-10-27 G3 已落码（L-7）**：① 删 `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`；② 本域自己的 `imports` 文件**只有那 1 行** ⇒ ⇒ **整份文件随类一起删**（与 article-rpc 的「2 行、只删第 1 行」形态**不同** ⇒ ⇒ **合并清单要分开写**）<br>⇒ **前置依赖已满足**：共享层桥在本树（`service/common/.../AutoConfiguration.imports:4`）<br>⇒ **验证（先 `clean` 再跑 —— 否则 `target/classes` 里的旧 class / 旧 imports 会掩盖删除）**：`mvn -o -pl service/favorite/favorite-rpc -am test -Dtest='FavoriteSqlTimeoutProductionPathTest,FavoriteRpcSpanWiringTest'` ⇒ **17 条 / 0 failures / 0 errors / 0 skipped**（2026-10-27T23:18:34+08:00）⇒ `FavoriteSqlTimeoutProductionPathTest` **5 条**（**真 Spring 上下文** ＋ Testcontainers PG ＋ **注入 `SqlSessionFactory`** ⇒ ⇒ 排序边缺失就**上下文起不来** ⇒ ⇒ **这条就是本域的验收口径**）<br>⚠️ **本域的残留缺口（结构性，不假装闭合）**：**favorite-rpc 没有产物层守门、也没有进程级守门**（实测：`service/favorite/favorite-rpc/src/test/**` 里**零**「`maven.test.skip=true`」的 fat jar 构建点 ⇒ **没有任何测试解包过本域的 fat jar**）⇒ ⇒ ⇒ **「fat jar 内嵌的 common jar 带不带共享层排序桥」这一格在 favorite-rpc 上**当前无人验** ⇒ ⇒ ⇒ 建议 **G4 在共享层加一条通用产物层守门**（或各域各自补）⇒ ⇒ **本域的 L-7 只能验到「上下文层」这一级** |
> ⚠️ **编号订正（2026-10-27）**：原「删域侧排序桥」项误用 `L-5`（与「favorite-rpc 的测试端口未治理」撞号）⇒ **已改为 `L-7`** ⇒ 本域 §3 编号现为 `L-1`…`L-7` 无重复。⚠️ **自撞号也是撞号** —— 我刚替 G4 抓过编号撞号，**自己域内的同一类错误一并订正**。

| **L-8** | 🔴 **favorite-api 的 JWT 验签被计入 3s 预算**（Go 的计时器在 Authorize **之后**起算 ⇒ 验签在预算**之外**） | `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/interceptor/FavoriteJwtAuthInterceptor.java`（把验签拆出去）＋ **新增**同域 `@Order(+6)` Servlet Filter（**只验签、只写 request attribute、绝不写响应**） | **G1 同步（2026-10-27）· 前提已由我方独立复核成立**：<br>① **Go 侧执行序（真源证据 · go-zero v1.10.2 `rest/engine.go:bindRoute`）**：原生中间件 → **`chn.Append(handler.Authorize(...))`（JWT 验签，`engine.go:74`）** → **再 append `ng.middlewares`（`server.Use` 注册的 observability 3s，`engine.go:110-111`）** ⇒ `chain.Append` = **更靠内** ⇒ ⇒ **Go 执行序：原生 → JWT 验签 → 3s 预算 → route 级黑名单 → handler** ⇒ ⇒ **验签在 3s 预算之外**。⚠️ `service/favorite/api/favorite.go:30` 的 `server.Use(NewHTTPMiddleware(...))` 虽是**全局**中间件，但 go-zero 把它 append 在 Authorize **内侧** ⇒ **「全局」≠「最外层」**（⚠️ 我第一轮按「全局包住 route 级」的直觉判断，差点把这条判成假阳性 ⇒ **必须读 go-zero 的 `bindRoute`，不能凭注册方式推链序**）。<br>② **RAG 现状**：验签内联在 `FavoriteJwtAuthInterceptor.preHandle`（`HandlerInterceptor` ⇒ DispatcherServlet **内**）⇒ 排在 `HttpMiddlewareFilter`（`@Order(+7)`，`service/common/.../spring/HttpMiddlewareFilter.java:37`）**之后** ⇒ ⇒ **验签被计入 3s 预算 ⇒ 与 Go 相反 ⇒ 真漂移**。<br>③ **共享层槽位（我方独立实测 `service/common/**`，不照抄 G1 的数字）**：+3 Traceparent（起 SERVER span）· +4 MaxConns · +5 Breaker · +6 CpuShedding · **+7 HttpMiddlewareFilter（3s 预算起算）** · +8 MaxBytes ⇒ 合法窗口 **[+3,+6]** ⇒ **四槽全被占满 ⇒ 只能并列 +6**（+6 严格小于 +7 ⇒ 顺序确定 ⇒ 并列无害）。<br>④ **修法（4 步）**：验签搬进本域 `@Order(+6)` Filter（**只验签、只写 request attribute、绝不写响应**）· **401 仍由本域 interceptor 抛**（失败**原样重抛异常对象** ⇒ `@ExceptionHandler` 仍管得到 ⇒ **本域的三字段信封形状逐字不变** ⇒ **已签字的 `DEV-FAV-API-001` 不受影响**）· **黑名单留在 interceptor**（Go 侧黑名单是 route 级中间件 ⇒ 在 3s 预算**之内** ⇒ 现状本就一致，**别动**）· 守门断言**容器装配出的链**（`ServletContextInitializerBeans` 的装配结果），**不是**两个 `@Order` 数字。<br>⑤ **本域与 users 域的差异（抄配方前必须逐域自己核）**：**401 body = 三字段信封**（`FavoriteErrorCode.ERROR_UNAUTHORIZED`，**已签字的 `DEV-FAV-API-001`** ⇒ 与 users 域的「完整信封」同款、但与 comment/article 域的「空 body」不同 ⇒ **别统一**）· **有黑名单**（Redis，22 处命中；`BLACKLIST_KEY_PREFIX = "user:jwt_blacklist:"`）· **受保护路径 = 全部**（实测 Go `favorite/api/internal/handler/routes.go` **只有一个** `AddRoutes` 块且带 `rest.WithJwt` ⇒ RAG 的 `FAVORITE_PREFIX` 全保护**与 Go 一致** ⇒ **无伴随的路径集合漂移**）。<br>⑥ **验收成本最低（本域最适合先做）**：**已有真 Tomcat 测试** `FavoriteApiRealTomcatGateTest` ⇒ 「413 压过 401」这条**响应级**语义可直接在既有真 Tomcat 用例里证（MockMvc 测不到响应头层）。⚠️ **真启动须先起 rpc、等 READY 再起 api**（api 侧对 rpc 有 `immediate-connect-timeout: 3s`）。<br>⇒ **状态**：✅ **已落码（2026-10-27 · 本轮）** —— 落点全在本域 ⇒ **不派 G4**（域侧自修）。<br>⇒ **实施记录**：<br>① **新增** `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/auth/` **4 个类**（与 users/admin 域**同款配方**，同构便于跨域比对）：`FavoriteJwtAuthPaths`（**路径判据单源** —— 本域**无 public 路径**，受保护前缀 `/favorite/v1`）· `FavoriteJwtVerificationResult`（失败带**异常对象**而非标志位 ⇒ 可**原样重抛** ⇒ `@ExceptionHandler` 仍管得到 ⇒ **三字段信封逐字不变**）· `FavoriteJwtSignatureVerifier`（**只验签、不写响应、不查 Redis**；兼作本域 **401 的唯一构造点** `unauthorized()`）· `GoStyleJwtAuthFilter`（`@Order(+6)`，**只验签、只写 request attribute、绝不写响应**）。<br>② **改** `FavoriteJwtAuthInterceptor`（构造器改为注入 verifier，**不新增任何依赖**）：**消费** Filter 的验签结果 → 失败**原样重抛** → **黑名单留在 interceptor**（Go 侧 route 级 ⇒ 在预算内 ⇒ 现状本就一致 ⇒ **没动**）→ ⚠️ **「未注册路径 404」的 `handler instanceof HandlerMethod` 判据原样保留**（它是「要不要拦」的判据，与 Filter 的「要不要验签」判据**刻意不同源**，已写进代码注释）。<br>③ **守门** `FavoriteApiJwtVerificationScopeTest`（**7 例**，真 Spring 上下文 ＋ 容器装配层）⇒ **7/7 绿**；**变异验证 3/3 精确变红**：<br>　　· 变异 1：`@Order(+6)` → `+8`（跑到 3s 预算之后）⇒ **1 条红** = 「JWT Filter 必须排在 3s 预算 Filter **之前**」⇒ 精确命中；<br>　　· 变异 2：让 Filter 验签失败时**自己写 401 并短路** ⇒ **3 条红** = 「413 压过 401」（实测 401 ≠ 413）/「401 信封形状」（实测**无 `$.code`**）/「未注册路径仍 404」（实测 401 ≠ 404）⇒ 精确命中 ⇒ **证明「Filter 绝不写响应」这条不变式被守门真的咬住**；<br>　　· 变异 3：摘掉 Filter 的 `@Component` ⇒ **1 failure + 2 errors** = 来源标记由 `filter` 变 **`inline`**（证明兜底分支真会生效）＋ bean 不存在 ⇒ 精确命中。<br>④ **证据强度**：**L3**（真 Tomcat 既有守门 `FavoriteApiRealTomcatGateTest` 覆盖「413 压过 401」的**响应级**语义 ＋ 真 Spring 上下文覆盖**装配层/行为层**）⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」；**未跑双侧活体对拍**（Go 侧 favorite-api 依赖 etcd + favorite-rpc + Redis，本环境起不来）。<br>⑤ **本域三处差异已逐条保留**（**不「顺手统一」**）：**401 body 仍是三字段信封**（已签字 `DEV-FAV-API-001` **不受影响**）· **黑名单两套取法**（验签用大小写不敏感的 strip / 黑名单用大小写敏感的 `TrimPrefix` ＋ Go 语义 `TrimSpace`）**原样保留** · **未注册路径仍 404**（不被鉴权遮蔽）。 |
| **L-9** | ✅ **favorite-api 的 Redis TCP 连接超时**（Go `DialTimeout` 5s vs Lettuce 默认 **10s** ⇒ 漂移 2 倍） | `service/favorite/favorite-api/src/main/resources/application.yml:50` ＋ **新增** `src/test/java/com/rag/favorite/favorite/api/config/FavoriteApiRedisTimeoutContractTest.java` | **已落码（域侧自修，2026-10-27 · commit `0c6295c`）**：yml 加 `connect-timeout: 5s`。<br>**Go 出处（消费点）**：`service/favorite/api/internal/svc/service_context.go:20` → go-zero `core/stores/redis/redisclientmanager.go:32-40` 构造 `red.Options{…}` ⇒ **不设 DialTimeout** → go-redis v9.20.0 `options.go:338-340` ⇒ **5s**（Read/Write 同理 `:364-379` ⇒ 3s）。<br>**RAG 侧**：Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` 字节码 offset 9→31 ⇒ **只有** `spring.data.redis.connect-timeout` 非空时才设 `SocketOptions.connectTimeout` ⇒ 不配 ⇒ Lettuce `DEFAULT_CONNECT_TIMEOUT = 10s`；且 `spring.data.redis.timeout` **只**喂命令超时（Boot 2/3 才是共用 ⇒ 已用 3.2.5 字节码复核）。<br>**为什么是域侧、不派 G4**：共享层 `GoStyleRedisClientAutoConfiguration` **只**设 `disconnectedBehavior(REJECT_COMMANDS)`、**不碰** `socketOptions`/`timeoutOptions` ⇒ 连接超时**完全由域内 yml 决定**。<br>**守门**：**真容器**（真实 yml → Boot 真实 `DataRedisAutoConfiguration` → 真 `LettuceConnectionFactory` → 断言**消费点**）**1/1 绿 ＋ 变异 3/3 精确变红**（删键 ⇒ 「expected: 5S but was: 10S」）。<br>⚠️ **可观察性分层**：本域黑名单查询**在 3s HTTP 预算之内** ⇒ 差异被预算先截断 ⇒ 影响有限；**裸露的是无 HTTP 预算的 rpc 组件**（comment-rpc / hot-rpc）。<br>📌 **G4 派单回执（2026-10-28 · G3）**：G4 第七封信把本条列为 **R7**（「你域的 yml 没有 `connect-timeout` ⇒ 连接超时 10s ⇒ 比 Go 的 5s 松 2 倍」）⇒ ⇒ **该派单在本域已过期**：本条落码（`0c6295c`，2026-10-27）**早于**派单 ⇒ ⇒ **不重做、不重派**。⇒ ⇒ **复验（2026-10-28）**：守门 `FavoriteApiRedisTimeoutContractTest`（**真容器** ⇒ 真 yml ⇒ 真 `LettuceConnectionFactory` ⇒ 断言**消费点** `getSocketOptions().getConnectTimeout()` == 5s ＋ `getCommandTimeout()` == 3s）**绿** ＋ **变异 3/3 精确变红**（`expected: 5S but was: 10S`）⇒ ⇒ **判据活着 ⇒ 差异不存在**。⚠️ **版本差**：G4 测的是 **Boot 4.0.3 / lettuce-core 6.1.8** ⇒ ⇒ **本域树上是 Boot 4.0.8 / lettuce-core 6.8.2.RELEASE** ⇒ ⇒ **两个版本都验过**（lettuce `SocketOptions` 静态块 `ldc2_w long 10l → Duration.ofSeconds` ⇒ 默认 10s ✔；Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` offset 9→31 = `getConnectTimeout()` → `SocketOptions.builder().connectTimeout(...)` ⇒ **只在属性非空时才设** ✔）⇒ ⇒ **是基线过期，不是版本差异**。 |

> 🟡 **共享层 U+FFFD 反馈单只登记一次** ⇒ 见 [`comment/vs-common.md`](../../DRIFT/comment/vs-common.md) §4-**G4-4**（`service/common` 两个测试类，各丢 1 个汉字，纯注释）⇒ **本域不重复派单**。


## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> 派单走**实时轨**（`session send` ＋ 反馈单 7 项）＋ **正式轨**（本表）⇒ 两轨内容须一致（AGENTS.md §5.3e）。
> **回执规格（2026-10-27 双方议定 · G4 已写入 `docs/DRIFT/global/vs-common.md` §4.1，commit `6e3e612`）**：回执 **第 4 项 = 「字段本身写全」** ⇒ ① 跑没跑 ② **未跑时把理由写进第 4 项字段本身**（不写在正文里）③ 跑了则给 **模块数 / 用例数 / failures / errors / skipped / 耗时** ④ **三门禁结果**。> ⚠️ 另外三项不变：**① commit 号 ② 逐个 `文件:行号` 改动清单 ③ 触及的 A1~A9 轴 + Go 出处**。
> ⚠️ **落点必须独立核准**（不要照抄台账快照）—— 本轮已实测两处落点与快照不同。

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| **G4-1** | 🔴 **`E65`**：出向 gRPC **不继承请求 deadline**（服务端路径恒 no-op）＋ 缺**共享的客户端 2s 封顶** | `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:110-120`（`applyTo`）＋ `service/common/.../observability/HttpMiddleware.java:304`（`begin` 的唯一主代码调用点，只在 HTTP worker） | **两侧都应是 Y**：Go 侧 zrpc client **无条件**套 2s（`zrpc/internal/client.go:115-117`）且父 deadline 经 `depCtx` 传播（`rpc/internal/logic/helper.go:98,133`）⇒ 共享层应让 **gRPC 服务端路径也把 deadline 写进 `GoStyleRequestDeadline`**（或让 client 直接读 `DeadlineState`），并提供**一个共享的客户端 2s 封顶**（目前只有 favorite-api 有域内私有实现 `FavoriteRpcClient.capByClientTimeout` ⇒ 10 域各写一遍 = 10 个可能写错的地方）。**影响面**：全部 rpc → 下游的出向调用（10 域）。**是否阻塞本波**：否 |
| **G4-2** | 🔴 **MyBatis-Plus 自动收集 `Interceptor[]` Bean 的机制** —— 全仓**无任何测试**证明 `@Import` 的 `DeadlineQueryTimeoutInjector` 真进了 MyBatis-Plus 执行链 | `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java` ＋ MyBatis-Plus 自动配置 | **实测缺口**：`CommentSqlDeadlinePrecisionGateTest.java:130-142` 走**手工** `new Configuration()` ＋ `configuration.addInterceptor(new DeadlineQueryTimeoutInjector())` ⇒ **绕开 Spring 与 MyBatis-Plus 自动配置**；`FavoriteSqlTimeoutProductionPathTest.java:161` 也是手工 `addInterceptor(PROBE)`（PROBE 是测试自己的探针）⇒ **两条测试都证明不了「`@Import` 的 Bean 被自动收集」**（AGENTS.md §3.3.3：注解存在 ≠ 机制生效）。⇒ 建议：在**共享层**补一条**真 Spring 上下文 + 真 mapper + 真 PG** 的测试，一次性为 10 个 rpc 域钉死该机制。**是否阻塞本波**：是（它决定 A1 在 SQL 侧是否真生效） |
| **G4-3** | 🟡 **A9 轴定义的措辞缺陷**（共享面 `_cross`） | `docs/DRIFT/_cross/domain-common-lanes.md` 的 A9 行 | 轴定义写「span 属性 **`audit.*`**」⇒ 实测：**共享层 = `sea.*`**（`ObservabilityConstants.java:57-116`）· **admin = `audit.*`** · **favorite = `biz.*`** ⇒ 轴定义应按域区分 ⇒ **共享面文件，G3 不写** ⇒ 已请 G4 登记进 `global/vs-common.md` 的建议项段 |
| **G4-4** | 🟡 **A3 form 闸门：共享层的「轴 ② 拒成什么形状」判据对 favorite 不成立** ＋ **共享层内部两份文件自相矛盾** | `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:46-54`（轴 ② 判据＝「有没有注册 `httpx.SetErrorHandler`」）＋ `:131-132`（`SEMANTIC_REACHABILITY` 里 favorite = `RAW`）＋ `tools/difftest/check-tomcat-limits.py:156-159,334`（`FORBIDDEN_FORM_LIMIT_MODE_MODULES`） | **两侧都应是 Y**：<br>① **形状判据错**：轴 ② 用「有没有注册 `httpx.SetErrorHandler`」推形状 ⇒ favorite 落在 `RAW`（400 + text/plain + Go 原文）⇒ **实测不是**：favorite 的 **7 个 handler 自己捕获** `httpx.Parse` 的错 ⇒ 走 `httpx.OkJsonCtx` ＋ `{Code: ErrorInvalidParam}` ⇒ **HTTP 200 + `{code:5003, msg:"参数错误", data:null}`**（`favorite/api/internal/handler/favorite/get_favorites_by_folder_handler.go:18-25`）⇒ **与是否注册无关** ⇒ **既不是 `RAW` 也不是 `ENVELOPE`（那条是 5001）** ⇒ 真源事实是「**handler 有没有自己捕获 `httpx.Parse` 的错**」（证据：L4 双侧活体 `tools/api-envelope-probe/favorite/` ⇒ **EXIT=0**，Go 侧 2049 非空值 ⇒ `{"code":5003,"msg":"参数错误","data":null}`，RAG 侧**逐字节相同**；另有域内 `FavoriteApiRealTomcatGateTest` 真 Tomcat 4 组判别用例）⇒ 建议：轴 ② 判据改成「handler 有没有自捕获」，或给 favorite 单开一档「域自定义业务码信封」。<br>② **共享层自相矛盾**：G4 的 `SEMANTIC_REACHABILITY` 判 favorite 语义模式 = `RAW`（httpx.Parse 命中 7 处）⇒ **共享层的表说 favorite「该声明 raw」**，而**共享层的守门脚本说 favorite「一项都不许声明」**（实测两文件在 G3 树与 G4 HEAD **逐字节相同** ⇒ 矛盾是现装的）⇒ 且脚本的理由原文是「**favorite 域不在 global 的写权范围内（AGENTS.md §5.3c）**」＝ **权限理由，不是语义理由**（违反「判据必须是能在真源上求值的事实」）。<br>⚠️ **这不是「favorite 要不要装闸门」**（favorite 的闸门**在域内自装**、已与 Go 逐字同形 ⇒ **不装第二道**）；问题是**共享层两份文件互相打架** ⇒ 迟早有人照 `SEMANTIC_REACHABILITY` 给 favorite 声明 `raw` ⇒ 守门立刻打红。<br>⚠️ **`tools/**` 是共享面 ⇒ G3 不写**（只登记）⇒ 由 G4 代转合并收口执行者。**是否阻塞本波**：否 |
| **G4-6** | 🔴 **本地 `~/.m2` 的 `service/common` jar 是 E62 修前旧版 ⇒ 单模块跑法伪装成「SQL deadline 漂移」** | `~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-0.0.1-SNAPSHOT.jar`（**本地环境**，非仓内文件）＋ **跑法**：`mvn -o -pl <模块> test`（**不带 `-am`**） | **现象**：`mvn -o -pl service/comment/comment-rpc test` ⇒ 3 条红、`mvn -o -pl service/favorite/favorite-rpc test` ⇒ 1 条红（症状：驱动收到 2000 而非原值毫秒 / 在途查询 1044ms / 过期后仍执行 SQL）。**根因（实测到字节级）**：该 jar（安装于 **2026-10-25 21:24**）的 `DeadlineQueryTimeoutInjector` **不含 `unwrapDriverStatement`** ⇒ **E62 修前**版本 ⇒ 毫秒探测必然失败（退回秒级 `ceil`）＋「已过期」分支还是旧行为「设 1 秒」而非抛 57014 ⇒ 与症状**逐条吻合**。**判定**：基线 commit `9f034e8` 的临时 worktree 跑 `-am` ⇒ **comment-rpc 3/3 绿 ＋ favorite-rpc 5/5 绿** ⇒ **不是漂移、不改仓内任何代码** ⇒ **不派单**（已在本轮 §5 记成踩坑并同步 G4）。**建议（给所有 lane）**：单模块跑域内测试**一律加 `-am`**。**是否阻塞本波**：否 |
| **G4-5** | 🔴 **`GoStyleGrpcClientBreakerInterceptor` 在 rpc 侧无装配路径** ⇒ `favorite-rpc` 出向调用无熔断（**本轮补记**） | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:103-106` ＋ `GoStyleProtectionConfiguration.java:51-53` | **现象**：该拦截器是 `@Component` ＋ `@GrpcGlobalClientInterceptor` ⇒ **只能组件扫描**；`favorite-rpc` **刻意不扫 `com.rag.common`**（`FavoriteRpcApplication.java` 的 `@SpringBootApplication` **无** `scanBasePackages`）＋ `@Import` 清单**不含**该类 ⇒ 该 client 侧能力**不在容器里** ⇒ **favorite-rpc → user-rpc/article-rpc 的出向调用无熔断**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）。⚠️ **本域确实有出向调用**：`favorite-rpc` 实测 **`@GrpcClient` = 2**（`client/UserRpcClient.java` / `client/ArticleRpcClient.java`）⇒ 缺口**可观测**。**建议**：共享层提供 **rpc 进程可用的装配入口** + 一条「rpc 侧出向调用确实经过 breaker」的**装配层**测试（§3.3.3：注解存在 ≠ 机制生效）。⚠️ **修法可行性已独立核准**：选项「改为可被 `@Import` 引入」要改 **10 个域侧 Application 类** ⇒ **越 G4 写权** ⇒ 只有「由 `RpcServerInterceptorFactory` 一并产出 client 侧拦截器」**落点 100% 在 `service/common`**（实测 **10/10 rpc 组件已 `@Import` `RpcServerInterceptorFactory`**）⇒ **本条已按全域共性正式派单给 G4（单 2）**，favorite 域**不单独重复排期**。**影响面**：G3 域内 **3/4**（article/comment/favorite 真实命中；hot-rpc 无出向 ⇒ N/A）。**是否阻塞本波**：否 |


### 4.4 🆕 2026-10-29 · G3 · 本轮新增派单/回执（阶段 1）

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| **G4-13** | 🔴 **favorite-api 现在「消费」共享层的 Go query 判据** ⇒ 共享层该类的语义变成**跨域承重** ⇒ **改它就会改 favorite 域的可观察行为**（本轮 G3 落码引入的新依赖面，**G4 需要知道**） | **现象**：G3 本轮把 `favorite-api` 的 query 闸门从「域内手写 2 条规则」改成「**调共享层判据** `GoStyleFormValues#goParseViolation(rawQuery)`」⇒ ⇒ favorite 域的**可观察行为**（2049 个非空 query 值 ⇒ 200+5003 · `%zz` ⇒ 200+5003 · 10001 段 ⇒ 200+5003 · 分号 ⇒ 200+5003）**现在取决于共享层那个类的实现**。<br>**Go 侧 `文件:行号`**：`go-zero@v1.10.2/rest/httpx/util.go:22-65`（`GetFormValues`：非空值计数 + `r.ParseForm()` 错误传播）＋ `rest/httpx/requests.go:50-68`（`httpx.Parse` 对**每个请求**都过一遍）＋ Go 1.25 `net/url/url.go:1019-1054`（段数 / 分号 / 非法转义）⇒ ⇒ **4 条规则的权威顺序在 Go 侧**<br>**RAG 侧现状**：`service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/interceptor/FavoriteGoQueryLimitInterceptor.java`（**域侧新类**，只**只读调用**共享层，不改共享层）＋ `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:238-259`（`goParseViolation`：段数 → 解析错误 → 非空值计数，**次序与 Go 一致**）<br>**证据强度**：**L1**（Go 侧逐行）＋ **L4**（favorite-api 真 Tomcat 门禁 **16/16 绿**，含 3 条新用例：7 端点全部 5003 / 2048 边界放行 / `%zz` 5003；变异 **M-A 8/10 转红**、**M-B 5/10 转红**（精确命中新修的两条规则）＋ 端到端 **2/2 转红**）<br>**建议修法**：**不需要改** ⇒ 只需**知道**：若 G4 要动 `GoStyleFormValues` 的规则/次序 ⇒ **必须同步通知 favorite 域**（域侧守门 `FavoriteApiRealTomcatGateTest` ＋ `FavoriteGoQueryLimitInterceptorTest` 会红 ⇒ 可当回归网）⇒ ⇒ 建议在共享层那个类的 javadoc 里加一行「**被 favorite-api 域内闸门直接消费**」的**消费者清单**（防「以为只有共享层闸门在用」）。<br>**影响面**：favorite-api 的 7 个端点的**全部 query 解析失败路径**<br>**是否阻塞本波**：**否** |
| **G4-14** | 🟡 **favorite 域两条「待拍板」项的**前提已变**（U9 · 部署形态）+ **U7 的环境受阻前提部分解除** | **现象**：① **U9**（Go 生产形态有 **16062** 那个 HTTP 面：`/metrics` `/healthz` pprof ⇒ RAG 侧 `web-application-type: none` ⇒ 没有）⇒ ⇒ **2026-10-30 Jason 批准「补指标抓取载体」⇒ U9 的 rpc 侧已落地并销账**（`service/favorite/favorite-rpc/**` = **域内** ⇒ ⇒ 「共享面 ⇒ G3 不写」这句对 rpc 侧**已过期**）⇒ ⇒ **U9 的 api 侧（16063）仍缺** ⇒ 落点 = `service/favorite/favorite-api/**` ⇒ **仍是 G3 域内** ⇒ ⇒ **下一轮 G3 自修**（本轮未做 ⇒ 已按 C 类登记在同文件「追加 · 🟢 rpc 域指标抓取载体落地」段）。② **U7**（双侧活体对拍 + 变异验证未做）原写「Go 侧 `etcd` / Kafka 依赖不可拉起」⇒ ⇒ **2026-10-29 实测 `Sea-etcd` 已有宿主端口映射 `49319->2379`** ⇒ ⇒ **受阻前提已部分解除**（Kafka 亦可达）⇒ ⇒ U7 从「环境受限」降为「**可做但未做**」⇒ ⇒ **本轮仍未做**。<br>**Go 侧 `文件:行号`**：`service/favorite/rpc/etc/favorite.yaml:23-30`（rpc 侧 DevServer `Port: 16062`）＋ `service/favorite/api/etc/favorite.yaml:25-32`（api 侧 DevServer `Port: 16063`；⚠️ 文件名是 `favorite.yaml`，**不是** `favorite-api.yaml` —— Go 侧 `service/favorite/api/etc/` 下**只有** `favorite.yaml`）<br>**RAG 侧现状**：`service/favorite/favorite-rpc/src/main/resources/application.yml:177-178`（**已有** 16062 那个面：`server.port: ${FAVORITE_RPC_MANAGEMENT_PORT:16062}` ＋ `:317-325` Actuator 暴露）＋ `service/favorite/favorite-api/src/main/resources/application.yml`（**无** 16063 那个面）＋ `docs/DRIFT/favorite/api.md:592`（U9 原文）＋ `docs/DRIFT/favorite/rpc.md:163`（U7 原文）<br>**证据强度**：**L4**（rpc 侧 2026-10-30 真启动实测：`/actuator/prometheus` 200 ＋ 62 指标族 ＋ gRPC 8082 同时 LISTEN）＋ **L3**（`docker ps` 实测端口映射在）＋ **L1**（Go 侧配置逐行）<br>**建议修法**：U9 的 **rpc 侧 ⇒ 已由 Jason 2026-10-30 拍板批准并落地**（本轮完成 ⇒ 销账）；U9 的 **api 侧 ⇒ 落点是域内 `favorite-api` ⇒ G3 下一轮自修**（登记即交付 ⇒ **不推拍板**）。U7 ⇒ 解除条件已具备 ⇒ **建议 G4 排进共享层的收口回归窗口**（`tools/**` 属共享面 ⇒ 探针落点也在共享面）⇒ **G3 本轮未做**。<br>**影响面**：favorite 域的 **R4 判定** ＋ P0 #1<br>📌 **2026-10-29 归属订正**：本单里出现的**根级文件**（根级 compose 的 `:?` · 根 README 的 `.env.example` · `Infrastructure.md`）⇒ ⇒ **不归 G4** —— 根级文件**无人拥有**（既不在任何 lane 白名单，也不在 `tools/difftest/check-wave-lane-ownership.py` 的 `SHARED` 13 条 ⇒ 该守门**自己的口径**：`:372`「N 个共享文件无人拥有 ⇒ 归合并收口执行者」）⇒ ⇒ **归「合并收口执行者」**，根因**并入 G1 的 `S1-USERS-001`**（**不另开编号**）⇒ ⇒ **G4 本单只涉及共享层本身**（`service/common` / `users-common`）⇒ ⇒ **不要动根级文件**（🔴 该守门的断言**全是声明侧** ⇒ 越权改动**不会被任何守门拦**）<br>**是否阻塞本波**：**否** |
| **G4-15** | 🟡 **favorite-api 的 405 多一个 `Content-Type`**（既有项，落点 `service/common`）—— 本轮**状态复核**：**仍在，未修** | **现象**：`405`（方法不匹配）响应**多带一个 `Content-Type`** ⇒ 与 Go 的「裸状态码」形状有差。<br>**Go 侧 `文件:行号`**：`go-zero@v1.10.2/rest/router/patrouter.go:73,79-82`（Go 的 notAllowed 路径 ⇒ 裸状态码）<br>**RAG 侧现状**：`service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java`（**共享层** ⇒ G3 只读）<br>**证据强度**：**L4**（域内真 Tomcat 用例实测，见 `docs/DRIFT/favorite/api.md` 的 405 条目）<br>**建议修法**：**共享层**统一去掉 405 的 `Content-Type`（与 404 的 `text/plain; charset=utf-8` 形状区分开）⇒ **落点 100% 在 `service/common`** ⇒ **G4 落码** ⇒ G3 只登记<br>**影响面**：10 个 api 域的 405 形状<br>**是否阻塞本波**：**否** |

### 4.1 §4 派单闭合状态（2026-10-27 · G4 第七次回执 4 项规格）

| 本域派单 | G4 回执对应 | 状态 | 证据 |
|---|---|---|---|
| **G4-2**（MyBatis-Plus 自动收集 `Interceptor[]` Bean） | **单 1** | ✅ **已闭合**（2026-10-27） | G4 树 commit `e3f2046`「test(common): 钉死『MyBatis-Plus 自动收集 Interceptor[] Bean』这条机制（**G3 单 1**）」⇒ 新增 `service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java`（**3 条 · 三层加码**）：① **收集层**（`MybatisPlusAutoConfiguration.interceptors` 里确有本拦截器）② **装载层**（`SqlSessionFactory.getConfiguration().getInterceptors()` 里确有本拦截器）③ **行为层（真 PG ＋ 真 mapper）**：绑一个已过期 deadline ⇒ 查询以 `57014` / `DEADLINE_EXCEEDED` 失败（前置：无 deadline 时同一查询**必须成功** ⇒ 失败才可归因于拦截器）⇒ **3/3 绿（实测）**。⚠️ **我域原登记的「全仓无任何测试证明」在登记时成立、现已由该守门闭合** ⇒ **保留原登记不改写**，此处加注记。⚠️ **该 commit 未进本树**（`git merge-base --is-ancestor` = 否）⇒ 本域要自己复跑该守门时**需先合入共享层**。 |
| **G4-5**（client breaker 在 rpc 侧无装配路径） | **单 2** | ✅ **已闭合**（2026-10-27） | G4 回执：「**client breaker 进共享层**」⇒ **本域零改动**（落点全在 `service/common`）。⚠️ **影响面口径（不得外推）**：G4 逐个 rpc 自核（`@GrpcClient` 计数 ＋ **六种出向方式**交叉核）后给出**全局**口径 = **6/10 可观测 ＋ 4/10 N/A**（N/A = `hot-rpc` · `task-rpc` · `user-rpc` · `admin-rpc`）⇒ ⇒ **本域面 = **3/4**（`article-rpc` 5 · `comment-rpc` 2 · `favorite-rpc` 2 可观测；`hot-rpc` = 0 ⇒ N/A）** ⇒ **「3/4」只覆盖本域四个 rpc 组件** ⇒ **不得被读成全局口径**。 |


> ℹ️ **口径说明**：上表的 ✅ 指 **「G4 的回执给了该单的落码 commit ＋ 验证口径」** ⇒ **不等于「本域已复验」** ⇒ 本域复验的前置 = **G4 的共享层 commit 进本树**（本树 HEAD 暂无 `2e2a84f` / `e3f2046` / `a1a2e05` ⇒ `git merge-base --is-ancestor` 全部为否）。
> ⚠️ **G4 回执给的是第七次全量**（`5564c27` / 2457 条）⇒ 本域台账已按**第八次**（`23d1c2a` / 2461 条）回填（**加注记、不覆写原值**）。

> ⚠️ **单 1 的含义订正（2026-10-27 · 复核 G4 落码时查出，重要）** ——
> 本条说的「**无任何测试证明**」**仍然成立**；但**机制本身在生产是通的**，别读成「生产上已失效」：
> - **生产早有修法**：MP 3.5.17 的 `MybatisPlusAutoConfiguration` 声明 `@AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)` —— 那是 **Boot 3** 的包名；**Boot 4.0.8** 已把该类搬到 **`org.springframework.boot.jdbc.autoconfigure`**（在 `spring-boot-jdbc-4.0.8.jar`）⇒ **排序引用静默悬空** ⇒ MP 被排到 DataSource 之前求值 ⇒ `@ConditionalOnSingleCandidate(DataSource.class)` 找不到 DataSource ⇒ **MP 退避、不产 `SqlSessionFactory`**。
> - **域侧已用排序桥补上**：**9/9 rpc 组件**都有 `MybatisPlusOrderingBridgeAutoConfiguration`（空类，**只为排序而存在**：`@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = MybatisPlusAutoConfiguration.class)`）＋ 每个组件自己的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里注册一行 ⇒ 权威条目 **`docs/DRIFT/like/rpc.md:703` `DRIFT-LIKE-014`**（2026-10-23 已登记并已修）。<br>📌 **订正（2026-10-27 · G3）**：**「9/9」这个数已变** —— G3 本轮删了**自己三域**的域侧桥（article-rpc / comment-rpc / favorite-rpc）⇒ ⇒ **全仓现存 6 座域侧桥**（follow / like / message / task / users-user / users-admin）⇒ ⇒ **排序桥的唯一来源 = 共享层那座**（`service/common/.../persistence/MybatisPlusOrderingBridgeAutoConfiguration`）⇒ ⇒ 「域侧重复」这个**附带发现**已**部分消解**（余下 6 座归各自域写权）
> - ⇒ **所以缺的只是「把这条链钉死的运行证据」**：现有域侧测试只断言「桥**已注册**」（`ArticleSpringContextWiringTest` / `FollowRpcWiringTest` / `LikeRpcSpringContextWiringTest`），**没有一条断言「`@Import` 的 interceptor 真进了 MP 执行链」** ⇒ **单 1 仍成立、仍是阻塞项**。
> - ⚠️ **⇒ 守门测试必须复刻那座桥**（否则测的是「没有桥的装配」⇒ **假阴性**：实测 G4 的探针在缺桥时 3/3 error，根因原文＝ `MybatisPlusAutoConfiguration: Did not match: @ConditionalOnSingleCandidate (types: javax.sql.DataSource) did not find any beans`）⇒ 修法 = 测试作用域放一座**等价**的空桥 **＋ 在测试资源的 `AutoConfiguration.imports` 里注册它**（**光有类不注册 ⇒ 排序边不建立**）。
> - 🟡 **附带发现（已登记，未派单）**：桥在 **9 个域各写一份**（域侧重复），**共享层 `service/common` 没有**（其 `imports` 只有 `GoStyleOtelAutoConfiguration` ＋ `GoStyleJsonContentTypeAdvice`）⇒ 下沉共享层可消除重复，但**要改 9 个域的 `imports` 文件 ⇒ 域侧写权** ⇒ 跨写权变更（AGENTS.md §5.3c 须同一次提交）⇒ **需 Jason 排期，本轮不动**。

### 4.2 ✅ G4 来问「favorite 容器上限走 (a) 域内改 yaml 还是 (b) 共享层解耦」⇒ **本域答复：(b)，且本域零改动**（2026-10-27）

| 判据 | 实测 |
|---|---|
| **Go 侧 favorite-api 怎么读参** | **7 个 handler 全部** `httpx.Parse(r, &req)`（`delete_favorite_folder_handler.go:18` · `create_favorite_folder_handler.go:18` · `update_favorite_folder_name_handler.go:18` · `get_favorites_by_folder_handler.go:18` · `delete_favorite_handler.go:18` · `create_favorite_handler.go:18` · `get_favorite_folders_handler.go:18`）⇒ ⇒ Go 在**应用层**强制 `httpx/util.go:15 maxFormParamCount=2048` ⇒ ⇒ 越限形态 = **HTTP 200 + {code:5003, msg:"参数错误"}**（**不是 400**） |
| **RAG 侧现状：两道闸门都在** | ① **容器层**：`favorite-api/application.yml:81` `server.tomcat.max-parameter-count: 2048` ⇒ Tomcat 在 `getParameterMap()` 抛 `InvalidParameterException` ⇒ 被 `FavoriteApiSupport.java:435-442` 捕获并转成 `IllegalArgumentException` ⇒ 对外仍是 Go 的形状（**200 + 5003**）；② **应用层**：`FavoriteApiSupport.java:381-382` **按 Go 口径数非空值个数**，超过 2048 ⇒ 抛 `IllegalArgumentException("too many form values")` ⇒ 对外 **200 + 5003** |
| **残余窗口（本域唯一一条）** | **计数口径差 1**：Tomcat 数**参数条目数**（key+value 合计），Go 数**非空值个数** ⇒ ⇒ 同名 2048 个值时 **Tomcat 拒、Go 放行** ⇒ ⇒ **RAG 在这一格比 Go 严** ⇒ ⇒ 已登记为 **`DRIFT-HOTAPI-030`**（`global/cross-domain`，权威条目在 `docs/DRIFT/hot/api.md`）⇒ **不在 favorite 域单方面改**<br>📌 **2026-10-29 对齐（台账对齐 phase · W-E lane）—— 原值保留 + 追加**：⚠️ **本行描述的那个窗口已闭合** —— 共享层把容器上限**无条件**抬到 **10001**（`service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java`：`MAX_PARAMETER_COUNT = 10001`，`customize(...)` **无 `mode` 分支**）⇒ **Tomcat 不再在 2048 处拒** ⇒ ⇒ **「同名 2048 个值时 Tomcat 拒、Go 放行」已不成立** ⇒ ⇒ **本行状态 = ✅ 已闭合**（2026-10-27 G4 落码 `cd893d1`；**2026-10-29 复核代码仍如此**）。<br>⇒ **本域当前仍 🔴 的只有「后续格（段数轴）」**（见本文件 §4 的「仍未闭合的下一格」行 + `F-3` 行）⇒ **本行不再代表「本域唯一一条残余」** |
| **⇒ 为什么是 (b) 而不是 (a)** | (a) 域内改 yaml ⇒ 要动 `server.tomcat.max-parameter-count`，而该值由**全仓**守门 `tools/difftest/check-tomcat-limits.py` 统一期望为 **2048** ⇒ ⇒ (a) 必然撞跨域门禁 ⇒ ⇒ **需跨域拍板**；(b) 共享层把容器上限抬到 **10001**（容器**不再自己判**）＋ 由应用层按 Go 口径判 ⇒ ⇒ **差 1 自动闭合** ⇒ ⇒ 且 **本域那道 Go 口径的应用层检查已经在位（`:381-382`）⇒ ⇒ 抬上限不会「让 favorite 失去闸门」**（若没有它，(b) 会把 favorite 从「严 1」变成「完全不设限」—— **这正是必须先核域侧那半的原因**） |
| **⇒ 本域需要的改动** | **零** —— (b) 落地后本域自动对齐 ⇒ **不需要 (a)**，也不需要新增域侧检查 |
| **🔴 给 G4 的实现要求（本轮新增的一条）** | 本域 mode = **OFF**（未配 `rag.http.go-form-limit.mode` ⇒ 默认 `off`）⇒ 当前共享层 `GoStyleTomcatParameterLimitCustomizer.java:109` 在 OFF 时**直接 return** ⇒ **不动容器上限** ⇒ ⇒ **(b) 落地时，容器上限的抬升必须对所有域**无条件**生效**（OFF 只该关掉**应用层**的 Go 风格判定，**不该关掉**容器上限的抬升）⇒ ⇒ 否则 OFF 域会继续卡在「2048 条目数」这一层 ⇒ ⇒ 差 1 不会闭合 |

### 4.3 ✅ G4 已落码（`cd893d1`）⇒ 本域已合并（**选项 D**）⇒ **域侧动作已完成**（2026-10-27 第十八轮）

| 判据 | 实测 |
|---|---|
| **G4 那条实现要求是否被满足** | 「容器上限的抬升必须对所有域**无条件**生效，OFF 只该关掉**应用层**的 Go 风格判定」⇒ ✅ **满足**：`GoStyleTomcatParameterLimitCustomizer#customize()` **不再按 `mode` 分叉**（`mode` 只在构造期做**校验**，非法值仍 fail-fast）⇒ ⇒ **OFF 域（favorite / task）同样抬到 10001** |
| **本域合并** | **选项 D**：`git merge --no-ff --no-commit align/g4-service-common`（起始 `cec7131` → 落点 `cd893d1`）⇒ ⇒ **共享层改动与本域改动进同一笔 merge commit**（不留半截）⇒ **冲突面实测 0**（merge-base `a282012`：本树 77 个改动文件 ∩ G4 树 52 个改动文件 = **空集**） |
| **本域可观察变化（唯一一格）** | 总「值」数 **2049~10000 且非空值 ≤2048** ⇒ Go 放行 / RAG 由「拒（200 + 5003）」变「放行」⇒ ⇒ **差 1 闭合**（`DRIFT-HOTAPI-030` 的这一格） |
| **域侧动作（G4 §4.10.25.4 点名的那件事）** | ✅ **已完成**：`FavoriteApiRealTomcatGateTest#containerParameterLimitIsSurfacedAsGoParseError` 的断言从「必须落 `5003`」改为 **Go 语义**（200 ＋ 非 5003 ＋ **含 1015**（证明**真的进了业务层**）＋ 非 Tomcat HTML）⇒ ⇒ **方法名故意保留旧名**（不打断 `global/vs-common.md` §4.10.25.4 的引用）⇒ 详见 [`favorite/api.md`](./api.md) **§14** |
| **变异守护** | 🔴 **两条旧变异当场失效**（MUT-A / MUT-C 实测 **0 红**）⇒ ⇒ 新增 **MUT-F**（域内判据「数非空值」→「数总值」）⇒ ✅ **1 例转红** ⇒ ⇒ 坑档 ⇒ [`PIT-FAV-API-010`](../../Pitfalls/favorite/api.md) |
| **仍未闭合的下一格** | 总「值」数**超过 Go 的 10000 段上限**：Go 拒（400 + `net/url` 原文）/ favorite **没有**实现这道段数上限（域内判据只数**非空值**；共享层的段数闸门因本域 = `OFF` 而**不装**）⇒ ⇒ 该区间两侧不一致 ⇒ 归 `DRIFT-HOTAPI-030` 的**后续格** ⇒ **显式登记，不假装闭合**<br>📌 **2026-10-29 订正（台账对齐 phase · W-E lane）—— 原句保留 + 追加**：⚠️ **本行的「Go 拒（400 + `net/url` 原文）」这个前提**已被 2026-10-27 G3 实测**证伪** —— Go 侧 handler 把 `httpx.Parse` 失败写成 **HTTP 200 + `{code:5003}`**（`service/favorite/api/internal/handler/favorite/get_favorites_by_folder_handler.go:18-24`）⇒ ⇒ **「拒绝形状」这一维不是漂移**。<br>⇒ **真正仍 OPEN 的是「**翻转点**差一格」**：Go 的 `net/url` 段数上限 = `defaultMaxParams = 10000`（Go 1.25.11 `src/net/url/url.go:1001`）⇒ **≥10001 段解析失败**；RAG 侧共享层上限**至今 = 10001** ⇒ **≤10001 段放行** ⇒ ⇒ **RAG 首个翻转点 = 10002 段 / Go = 10001 段** ⇒ 差一格 ⇒ **修法 = 共享层 `MAX_PARAMETER_COUNT` `10001 → 10000`**（共享面 ⇒ 本域无权改）<br>⇒ **本行状态仍 = 🔴 仍 OPEN**（**这一格是本域当前唯一仍 OPEN 的跨域格**） |
| **证据强度** | **L3**（真容器活体：合并后实测 **200 + `{"code":1015,"msg":"服务繁忙"}`** ⇒ 请求**真的进了业务层**）⇒ ⚠️ **不是 L4**：这一格**缺双侧活体用例**（`tools/api-envelope-probe/favorite/` 现有的 2049 用例是「**全非空值**」＝应用层判据，**不是**本次闭合的「1 非空 + 2048 空」那格）⇒ ⇒ **L4 待补**（探针属 favorite 域工具面；G4 已建议 G3 补一条 ⇒ **该目录不在任何 lane 的写权名单里 ⇒ 归属待合并收口执行者裁定**） |

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）—— ✅ **2026-10-27 阶段 2 已完成**：`mvn -o -pl service/favorite/favorite-rpc,... -am test` ⇒ **favorite-rpc 114 条 / 0 failures / 0 errors / skipped 3**（含 `FavoriteSqlTimeoutProductionPathTest`（**真 Spring 上下文 ＋ 真 PG ＋ 真 Hikari**）5/5 绿、`FavoriteDdlParityTest`（**真 Flyway**）、`fact/FavoriteAuthority*Test`（**真进程 gRPC**））。⚠️ **必须带 `-am`**（否则拿旧共享层跑，见文末「本轮踩坑」）。
  - ⚠️ **订正（本轮自查）**：原稿此处误勾 `[x]` ⇒ **已改回 `[ ]`**。理由：`tools/ddl-parity-probe/favorite/run.sh` 的 RAG 侧是**用 `psql` 直接执行 Flyway 迁移脚本**（`run.sh:180-190` 逐个 `psql_q -d ragdb -f -`）⇒ **全程不启 Java 进程** ⇒ 它证明的是「**迁移脚本产出的 DDL** 与 Go `AutoMigrate` 一致」（A8 的 L4 成立），**不构成「组件真的启动过」的证据** ⇒ 第 0 步仍**未完成**。
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（**2026-10-27**）
- [x] **两侧同时跑**的探针（AGENTS.md §3.3）—— ✅ `tools/errmsg-probe/favorite/run-parity.sh` **EXIT=0**（A6 ⇒ **L4**）· ✅ `tools/ddl-parity-probe/favorite/run.sh` **EXIT=0**（A8 ⇒ **L4**）· ✅ `tools/proto-wire-probe/favorite/run.sh` **EXIT=0**（A7 wire 字节层 ⇒ **L4**，2026-10-27 阶段 2 补跑）⇒ **本域三道探针全绿**
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）
- [x] §3 的域侧修法**落码 + 守门**（含变异验证）—— 🟡 **阶段 2 落码部分已完成**（口径：`mvn -o -pl service/favorite/favorite-common,service/favorite/favorite-api,service/favorite/favorite-rpc -am test` ⇒ **favorite-common 29 / favorite-api 104（skipped 1）/ favorite-rpc 114（skipped 3）** ⇒ **本域三模块合计 247 条 / 0 failures / 0 errors** · 核对日期 **2026-10-27**）：
  - **L-1** ✅ 补 `baseline-version: 0`（`favorite-rpc/src/main/resources/application.yml` flyway 段 ＋ 三段论证注释）
  - **L-2** ✅ 两个出向 client 补 2s 封顶（新增域内 package-private `client/GoStyleClientCallTimeout.capByClientTimeout` ＋ `UserRpcClient.userRpc()` / `ArticleRpcClient.articleRpc()`）
  - **L-3** ⛔ **撤销**（前提不成立 ⇒ 域内闸门早已在位且与 Go 逐字同形 ⇒ **不装第二道**）
  - **L-5** ✅ 测试端口治理（新增域内 `support/RealGrpcPort`（**专用段 21400–21499**）＋ `RealGrpcPortTest` 4 例；8 个真进程测试的 `grpc.server.port=0` 全部改掉）
  - **L-6** ✅ A7 proto 回归网（`tools/proto-wire-probe/favorite/run.sh` ⇒ **EXIT=0** ⇒ wire 字节层 = **L4**）；🟡 **字段号层仍缺静态守门**（`check-proto-static.sh` 只覆盖 user/admin/task ⇒ 属**共享面**，已登记）
  - **L-8** ✅ **JWT 验签搬出 3s 预算**（新增 `favorite-api/.../auth/` 4 个类：`FavoriteJwtAuthPaths` / `FavoriteJwtVerificationResult` / `FavoriteJwtSignatureVerifier` / `GoStyleJwtAuthFilter`（`@Order(+6)`）＋ 改 `FavoriteJwtAuthInterceptor` 消费结果并原样重抛 ⇒ **黑名单仍在 interceptor**、**未注册路径仍 404**、**401 三字段信封不变**（已签字 `DEV-FAV-API-001` 不受影响））⇒ 守门 `FavoriteApiJwtVerificationScopeTest` **7 例 ＋ 变异验证 3/3 精确变红**（`+8` 顺序 ⇒ 1 条红 / Filter 自写 401 ⇒ 3 条红 / 摘 `@Component` ⇒ 来源标记 `filter`→`inline`）
  - ✅ **L-4**（16 个业务 span ＋ `biz.*` 7 属性）**已完成**（落码 ＋ 守门 23 条全绿 ＋ 7 变异精确转红 ⇒ **L3**）· ✅ **L-7**（删域侧排序桥）**已落码**（2026-10-27 G3 ⇒ 删 `favorite-rpc` 的域侧桥 ＋ 它自己的 `imports` 整份 ⇒ **clean** 后重跑 **17 条 / 0f 0e 0s** ⇒ 详见 §3 的 **L-7** 行；⚠️ 残留缺口 = 本域**无产物层/进程级守门** ⇒ fat jar 那层无人验）
- [x] §4 的派单**已走实时轨**（`session send` → `G4Common` ＋ 本表正式轨，两轨一致）：**单 1** ＝ 🔴 G4-2（MyBatis-Plus 自动收集 `Interceptor[]` Bean 无运行证据，**阻塞本波**）· **单 2** ＝ 🔴 G4-5（client breaker 在 rpc 侧无装配路径）—— 两条 7 项反馈单齐全（全域共性 ⇒ 请 G4 收口去重）
  - 🔴 G4-1（`E65`）＝ **不重复派**（已是 global 台账的 **OPEN** 项）⇒ G3 只在域台账**引用**
  - 🟡 G4-3（A9 轴措辞）/ G4-4（`GoStyleFormLimitMode` 档位）＝ **登记/转合并收口执行者项**，已随单 1 消息同批发给 G4（**非 7 项反馈单**）
  - 🔴 `docs/DRIFT/global/vs-common.md` ＋ `global/_README.md` §七之二属 **SHARED 面 ⇒ G3 不写**（AGENTS.md §5.3e）⇒ 由 G4 / 合并收口执行者落
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）—— ✅ **本轮实测（2026-10-28 · G3，域侧自修 · §1e）**：`check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=1**（索引 **315** vs 本树实测 **332**，差 **+17** ⇒ **合并收口执行者**统一 `--fix` ⇒ ⛔ **各 lane 不各自 `--fix`**）· `_MANIFEST.json` **不收录** `vs-common.md`（`grep -c` = **0**）⇒ ⇒ **本域无需改共享面** ⇒ ⇒ ⚠️ **措辞订正**：「合并收口执行者」⇒ 改称「**合并收口执行者**」

> ⚠️🔴 **本轮踩坑（四域共用 · 已在 G4 同步）——「单模块跑法」会拿 `~/.m2` 里的旧共享层做测试**
> `mvn -o -pl service/favorite/favorite-rpc test`（**不带 `-am`**）⇒ **本域 1 条红**（`productionPathDriverRawMillisIsOriginalRemaining`：实测驱动收到 **2000** 而非原样 R）⇒ 症状像「毫秒精度失效」。
> ⇒ **根因**：本地 `~/.m2` 的 `service/common` jar 是 **E62 修前**旧版（**不含 `unwrapDriverStatement`**）⇒ 毫秒探测失败 ⇒ 退回秒级 `ceil` ⇒ 写进去的就是 `ceil(R/1000)*1000 = 2000` ⇒ 与症状**逐条吻合**。
> ⇒ **判定**：基线 commit `9f034e8` 的临时 worktree 跑 `-am` ⇒ **favorite-rpc 5/5 绿**（含本条）⇒ **不是漂移、不派 G4**。⇒ **纪律：单模块跑域内测试一律加 `-am`**。
> ⇒ **全文与跨域影响面见** [`docs/DRIFT/article/vs-common.md`](../article/vs-common.md) §5 的「本轮踩坑」条。

> ℹ️ **G4 回执核账（2026-10-27）**：单 1 / 单 2 / 单 4 **全部已落码、零域侧改动** ⇒ 本域 §4 的 **G4-2**（MP 自动收集 `Interceptor[]` 无运行证据）**已被共享层 `MybatisPlusInterceptorChainGateTest` 覆盖** ⇒ 本域不重复做；**G4-5**（client breaker 在 rpc 侧无装配路径）由 G4 统一落（**全域共性**）。⚠️ **单 4 的全量回归数字（基线 `a1a2e05`）待 G4 补**。

---

## 🔴 **本档的「阈值类结论」依赖共享层哪个版本**（2026-10-27 · 按 G2 [3] 的要求做的重核）

⚠️ **纪律**：本档若写了「**运行期上限 / 阈值 / 池形态**」类结论，**必须标「依赖共享层哪个版本」** ⇒ ⇒ **共享层一改 ⇒ 那条要重核**（重核，不是重写）⇒ ⇒ **不得把旧结论当现状引用**。

| 本档结论 | 依赖面 | 当前实测（2026-10-27 重核） | 共享层一改会怎样 |
|---|---|---|---|
| **A3 的 form 容器上限 = 10001** | 共享层 `cd893d1` 的 `GoStyleTomcatParameterLimitCustomizer:207`（**无条件** `setMaxParameterCount` ⇒ 与 `mode` **彻底解耦**） | ✅ 我树含 `cd893d1`（`e18fd52` 合并）⇒ ⇒ **运行期一律 10001** | 若该类再按 `mode` 分叉 ⇒ ⇒ 运行期上限掉回各域 yaml 值 ⇒ ⇒ **A3 那格要重核** |
| **A3 的判定权口径差 1**（Tomcat 数总「值」数 vs Go 数非空值） | **不依赖**共享层（域内 `FavoriteApiSupport#goFormFirstValue` 自己数非空值） | 🔴 仍 OPEN | 不受影响 |
| **A9 的 16 个业务 span** | **不依赖**共享层（域侧私有 `FavoriteSpans`） | 🟢 已落码 ＋ **守门 23 条全绿**，证据强度 **L3** （Go 侧 L1 逐行 ＋ RAG 侧 L4 运行证据） | 不受影响；但**共享层若改 OTel 装配**（如 `GlobalOpenTelemetry.set` 的收口方式）⇒ ⇒ **守门设计要重核**（守门自己也 `GlobalOpenTelemetry.resetForTest()` ＋ `set(...)` ⇒ 装配层变了会**直接**反映到守门） |
| **Redis 池形态 = 无池** | **依赖共享层的 dependency 状态**（`isPoolEnabled` 的 classpath 回退） | ✅ 共享层 classpath 上 `commons-pool2` **零命中** ⇒ ⇒ 结论成立 | 共享层依赖树一变 ⇒ ⇒ 池形态**静默翻转** ⇒ ⇒ 要重核 |

⇒ **判据明细表见 [`favorite/api.md` §17](api.md#17-🔴-阈值类结论的共享层依赖声明2026-10-27--按-g2-3-的要求做的重核)**（同款机制，三域共用一条判据）⇒ ⇒ **改一处要三域一起重核**。

---

## 6. 🔴 域内未闭合项清单（2026-10-29 · G3 域内收敛自查 A · 全量盘点）

> **盘点口径**：把本域**全部**台账（`api.md` / `rpc.md` / `common.md` / `proto.md` ＋ 本文件）里
> 所有「未闭合 / 开放 / 待办 / 未修 / 仍 OPEN」条目**逐条**取出 ⇒ 定状态
> （**已闭 / 仍开 / 范围外 / 归属他方**）＋ 是否阻塞阶段 1 ＋ 证据指针。
> ⚠️ **本清单是「现状面」**；各轮的「判定变化」表是**历史记录**（AGENTS.md §6.3 状态表纪律）。
> ⚠️ **登记类事项不进本清单**：已进 `docs/Intentional_Deviations/` 的候选（🏷 候选，建议签）
> **按 AGENTS.md §5.3c「登记完就躺着，不升级为拍板」** ⇒ 不重复列出、不推拍板。

| # | 项 | 状态 | 归属 | 阻塞阶段 1？ | 证据 |
|---|---|---|---|---|---|
| **F-1** | **`U7` rpc 层整域双侧活体对拍**（错误码契约层已做成双侧对拍，**但那是 `favorite-common`，不是 rpc 层**；指标 result 语义只有单侧 Go 锚定 ＋ RAG 侧运行时守门） | 🔴 **仍开 —— 本域最大缺口** | 🟢 **G3（本域）** ⇒ ⚠️ **环境受限**（Go 侧 `etcd` / Kafka 仍不可拉起） | 否 | [`rpc.md` §9.3 `U7`](rpc.md) |
| **F-2** | **§5 交付物清单「第 0 步：组件真的启动一次」** —— `tools/ddl-parity-probe/favorite/run.sh` 的 RAG 侧是**用 `psql` 直接执行 Flyway 迁移脚本**（`run.sh:180-190` 逐个 `psql_q -d ragdb -f -`）⇒ **全程不启 Java 进程** ⇒ 它证明的是「迁移脚本产出的 DDL 与 Go `AutoMigrate` 一致」（A8 的 L4 成立），**不构成「组件真的启动过」的证据** | 🔴 **仍未完成**（原稿误勾 `[x]` ⇒ 已改回 `[ ]`） | 🟢 **G3（本域）** | 否（影响 A8 结论的**解读**，不改 A8 判定） | 本文件 §5 交付物清单第 1 项 |
| **F-3** | **`DRIFT-HOTAPI-030` 后续格**（**query 段数轴**：总「段」数超过 Go 的 10000 段上限） | ✅ **已闭合（2026-10-30 · G3 · **L4 真 Tomcat 活体**）** —— 🔴 **原「仍 OPEN」是过报，已销账**<br>📌 **销账依据（三条，逐条可复算）**：<br>① **Go 真源逐行**：go1.25.11 `src/net/url/url.go:1019-1021`（`parseQuery` 里 `if !urlParamsWithinMax(strings.Count(query, "&") + 1) { return errors.New("number of URL query parameters exceeded limit") }`）+ `:1001`（`defaultMaxParams = 10000`）+ `:1003-1005`（`urlParamsWithinMax` ⇒ `params <= defaultMaxParams`）⇒ ⇒ **Go 的翻转点 = 第 10001 段**；<br>② **共享层判据与 Go 逐字同形**：`service/common/.../http/GoStyleFormValues.java:215`（`MAX_URL_QUERY_PARAMS = 10000`）· `:262`（`countQuerySegments(query) > MAX_URL_QUERY_PARAMS` ⇒ 拒）· `:307`（`countQuerySegments` = `1 + count('&')` ⇒ ≡ Go 的 `Count("&")+1`）⇒ ⇒ **RAG 的翻转点 = 第 10001 段 = 与 Go 同**；<br>③ **本域 L4 活体**：`service/favorite/favorite-api/src/test/java/com/rag/favorite/favorite/api/FavoriteApiRealTomcatGateTest.java` —— **2026-10-30 实跑 16/16 绿**（`querySegmentCountOver10000IsRejectedLikeGo` ✔ + `exactly10000QuerySegmentsArePassedThrough` ✔ + `querySegmentLimitIsUniformAcrossEndpoints` ✔ + `containerParameterLimitIsSurfacedAsGoParseError` ✔）⇒ **RAG 在第 10001 段拒、在第 10000 段放行 ⇒ 与 Go 同格**<br>⇒ ⛔ **原「修法 = 共享层 `MAX_PARAMETER_COUNT` `10001 → 10000`」已作废**（`api.md` §21.3 · 2026-10-28 · G3 · L1：改成 10000 ⇒ Tomcat 在第 10000 段就早于 Go 拒 ⇒ 400+HTML ⇒ **反而制造新漂移**）⇒ ⇒ **不得派这张单给 G4**（会让人做一件已判作废的改动）<br>⇒ ⚠️ **本条只闭合「query 段数轴」** ⇒ **body form 参数轴是另一条、真实残留** ⇒ 见本文件 §6 的 **`F-8`**（C 类 · RAG 比 Go **宽一格**） | ⛔ **不再需要共享层改动**（域侧判据已在位 · 共享层常量**不许动**）<br>⇒ ⚠️ **原写「共享层段数闸门因本域 = `OFF` 而不装」是对的**（本域 `application.yml` **无** `go-form-limit` 段 ⇒ 默认 `off`）⇒ **判据由本域自己的 `FavoriteGoQueryLimitInterceptor` 承担**（它调共享层 `GoStyleFormValues#goParseViolation` ⇒ 判据仍是同一份） | 否 | 本文件 §4 表「仍未闭合的下一格」行（**已销账**）<br>新增：**F-8**（body form 参数轴 · C 类） |
| **F-4** | **P0 #8 的共享面半边**（根级 compose 的 `:?` ＋ 根 README 的 `.env.example`） | 🔴 **仍开**（**域侧那半已修**：`application.yml` 去明文默认值 ＋ `@PostConstruct verifyAccessSecret()` ＋ test scope `application.properties` ＋ **3 例真 `ApplicationContextRunner`** 守门 ⇒ **3/3 绿**） | 🔴 **合并收口执行者**（根级文件**无人拥有** ⇒ 根因**并入 G1 的 `S1-USERS-001`**） | **否**（域侧已修 ⇒ P0 #8 的**域侧部分**闭合；⚠️ **P0 #8 整体仍不能判 ✅**） | [`api.md` §24](../favorite/api.md) 第 6/8/11 项 |
| **F-5** | **405 响应多一个 `Content-Type`**（2026-10-25 第十四轮新发现） | 🔴 **仍开** | 🔴 **global / 收口**（落点在 `service/common` ⇒ **不在 G3 写权面**） | 否 | [`api.md` 头部「未决」行](../favorite/api.md) |
| **F-6** | **rpc 域的其余明文默认值**（`${POSTGRES_PASSWORD:password}` / `${MINIO_SECRET_KEY:minioadmin}` / `${*_AUTHORITY_TOKEN:}`） | 🟡 **已登记，本轮未动** | 🔴 **G4**（需**单独单据**） | 否 | [`api.md` §24.2 第 8 项第 ③ 条](../favorite/api.md) |
| **F-7** | **本机 `ddl-favorite-pg` 需重跑一次真 Flyway 迁移**（或重建容器）才能让静态对照与 L4 结论一致 | ⚠️ **环境动作**（**不是代码缺陷** ⇒ 登记为未闭合项，不阻塞阶段一） | 🟢 **G3（本域）** | 否 | [`rpc.md` §「可执行动作」](rpc.md) |
| **F-8** | **body form 参数轴**（**与 F-3 是两条不同的轴** —— F-3 = **query 段数**，本条 = **urlencoded POST body 的参数个数**） | 🟡 **真实残留（2026-10-30 · G3 新登记）**<br>**阶段归属（`docs/twoStage.md` §10）：C 类**（阶段 2 最终工程建设项）· **优先级 P3** · **不阻断阶段一**<br>⚠️ **方向：RAG 比 Go 宽一格**（⚠️ **不是「RAG 更严」，方向相反**） | 🟢 **域侧可自修**（**但不得改容器常量**）<br>⇒ **依据**：Go 侧 `net/http/request.go:1294`（`parsePostForm` 的 `application/x-www-form-urlencoded` 分支 ⇒ **`url.ParseQuery(string(b))`** ⇒ **Go 的 10000 段上限同样作用于 body**）+ `net/url/url.go:1019-1021` ⇒ ⇒ **Go 在第 10001 个 body 参数就拒**；而 RAG 侧：① 共享层的 Go 语义闸门 `GoStyleFormValues#goParseViolation` **只判 `request.getQueryString()`**（**不判 body**）⇒ body 轴上没有 Go 语义判据；② 兜底只剩**容器** `maxParameterCount`（运行时由 `GoStyleTomcatParameterLimitCustomizer` 抬到 **10001**）⇒ ⇒ **RAG 允许第 10001 个 body 参数 ⇒ 比 Go 宽一格**<br>⇒ ⛔ **不许改容器常量**（改 10000 ⇒ Tomcat 抢在应用层之前 HTML 400 拒 ⇒ **制造新漂移**；改 -1 ⇒ 容器这道闸门整个消失）⇒ ⇒ **正确修法 = 域侧闸门也判 body**（照 favorite 域 `FavoriteGoQueryLimitInterceptor` 的形态：判据仍取共享层那份，只多传一个 body 的 query 串）<br>⇒ ⚠️ **可达性**：需 `Content-Type: application/x-www-form-urlencoded` 的 POST ＋ > 10000 个 body 参数 ＋ 其中**非空值 ≤ 2048**（否则先撞 Go 的 2048 非空值规则）⇒ ⇒ **窄但真实** | 否 | **跨域同源**：`article` / `hot` 两域同轴同因（见 [`article/vs-common.md` 的 `A-3`](../article/vs-common.md) 与 [`hot/vs-common.md` 的 `H-A2'`](../hot/vs-common.md) 的销账行）⇒ **四域共用一条修法** ⇒ 建议**同一次提交**落码（AGENTS.md §5.3c「修法同时涉及多处 ⇒ 必须同一次提交」） |

⇒ **favorite 域的「域内可动」未闭合项 = F-1 / F-2 / F-7 / F-8 四条**（F-3 **已于 2026-10-30 销账** · F-5 属 global · F-4 属根级 · F-6 属 G4）⇒ ⇒ **本域阶段一判定不变**（F-8 = C 类 · 不阻断）。

---

## 7. 🔴 Go 出处引用漂移自查（2026-10-29 · G3 域内收敛自查 B）

> **为什么这是缺陷而不是笔误**：以「Go: …」措辞写在 javadoc / `@DisplayName` / 断言消息里的断言
> **改一行不会让任何测试变红** ⇒ ⇒ **绿测试可能守着一个错的事实**（AGENTS.md §3.3.2）。
> ⚠️ **守门本体见 [`article/vs-common.md` §7.1](../article/vs-common.md)（四域共用，G3 域侧落码）**
> ⇒ 本节只列**本域**的订正项。

| # | 落点 | 原引用 | 订正为 | 缺陷类型 |
|---|---|---|---|---|
| 1 | `favorite-api/.../FavoriteApiEnvelopeParityTest.java:101` ＋ `favorite-api/.../FavoriteApiRealTomcatGateTest.java:148` | 「favorite-api 的**默认** AccessSecret（**`application.yml:94` 的默认值**，测试未覆盖它）」 | 「favorite-api 的 AccessSecret 取值 —— ⚠️ 主 yaml 的**明文默认值已于 2026-10-29 去掉**（P0 #8 域侧修）⇒ 这里不再是「默认值」，而是 **test scope** 的 `src/test/resources/application.properties`（**不进产物 jar** ⇒ 生产拿不到）」 | **前提失效 + 行号错**（⚠️ **本轮 `45e0af4` 把主 yaml 的默认值去掉后**，原注释描述的是**已不存在的代码**；且 `:94` 本来就不是那一行 —— 旧文件该行是 `max-wait: 4s`）⇒ ⇒ **典型「台账/注释的现在时描述已过期」形态** |

⇒ **本域 1 类（2 处）已订正（零行为变更：注释 / `@DisplayName`）** ⇒ 本域守门实测 **EXIT=0**（`LINE_OOB` / `FILE_MISSING` / `EMPTY_LINE` / `VERSION_MISMATCH` **全 0**）。

---

## 8. 🔴 台账内部矛盾 / 过期结论订正（2026-10-29 · G3 域内收敛自查 C）

| # | 结果 |
|---|---|
| — | ✅ **本域真矛盾 = 0 条** —— 跨行扫描（**排除「同一行『修复前 → 修复后』两列」**这个第一版误报）⇒ favorite 域**无「前提失效」的真矛盾** ⇒ ⇒ **记录「扫过、0 条」本身也是证据**（免得后人重复扫一遍）<br>⚠️ **但本域有一处「过期描述」已在本轮随 §7 一并订正**：`favorite-api` 的两处注释仍把 AccessSecret 写成「**主 yaml 的默认值**」⇒ 而那个默认值**已被本轮 P0 #8 的域侧修复去掉** ⇒ ⇒ **描述的是已不存在的代码** ⇒ 已改指 **test scope** 的 `src/test/resources/application.properties` |

---

## 开工前置回执

> ⚠️ **本节是 AGENTS.md §五·3b-0 第 ③ 步的「开工前置回执」**（模板 ⇒ `docs/Worktree.md` §二·五）⇒
> **本 lane 的每个域台账都要有这一节**（收口时才能一眼看出「这个域开工前到底读没读」）。
> ⚠️ **回执人不得自批**：⑤ 由**调度员**在 Jason 批准后代填；本节**不构成开工批准**（**已代填**：Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」· 消息 id `182054` ⇒ 见本节末尾 ⑤ 行）。

【开工前置回执 · G3 · 2026-10-29】

**① 任务范围**（本 lane = `article` + `comment` + `favorite` + `hot`，**阶段 1**）：

- 候选任务（**回执落笔时：待 Jason 批准，本轮只确认、不动手**；⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182054`）⇒ **已获批开工**）：
  1. **H-A1** —— hot proto **消息体** wire 字节的双侧对拍探针落码（`tools/proto-wire-probe/hot/`，照 favorite 域 `tools/proto-wire-probe/favorite/{run.sh,go/main.go}` 骨架复制）。
  2. **A-1** —— `article` 域**双侧活体对拍**（Go 侧需 `etcd` 宿主端口映射；实测 `Sea-etcd` 的 `49319→2379` 转发已在跑 ⇒ **`judge` §九 `G-3` 写「⚫ 受阻（宿主端口未映射）」的前提已过期**）。
  3. **F-1** —— `favorite-rpc` **整域**双侧活体对拍（Go 侧 `etcd` / Kafka 仍不可拉起 ⇒ 环境受限）。
- **不做**（明确边界）：
  - **阶段 2**（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7 · §9 故障矩阵 · 生产流量 / SLO）⇒ 收口执行者的事，**不预支**。
  - **共享层** `service/common/**` ＋ `service/users/users-common/**` ⇒ **只有 G4 能写**，本 lane 只读。
  - **共享索引面**：`AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` / `docs/PhaseGoal-judge.md` / `docs/PhaseGoal/g3.md`（**机器生成物**）⇒ 不写。
  - **根级文件**（`docker-compose.yaml` / 根 `README.md` / `.env.example`）⇒ **无人拥有 ⇒ 归「合并收口执行者」**（根因并入 G1 的 `S1-USERS-001`，**不另开编号**）。
  - **已登记的签字偏离候选**（`docs/Intentional_Deviations/` 的 `CAND` 族）⇒ 按 AGENTS.md §5.3c「登记完就躺着，**不升级为拍板**」⇒ 不重复质疑、不推拍板。

**② 已读四档**（**具体路径 ＋ 读到的关键结论**）：

- `docs/DRIFT/article/api.md` ⇒ 权威现状面 = **§7 状态表（`:556`）+ §9 UNRESOLVED（`:878`）**（文件头 `:17-24` 有读取指针表；⚠️ 组件判定行 `:26` 是**首轮**判定，**不可直接引用** —— 见 `PhaseGoal-judge.md:104-108`「5 份台账头已被正文推翻」）；§8.3（`:858`）四条「待补探针」经核对 **3 条早已消解**（`DRIFT-001/002/003` 已修 + 守门在位）⇒ 本轮已改写为「活状态」表；`UNRESOLVED-002`（`:886`）跨组件、仍缺跨栈活体。
- `docs/DRIFT/article/rpc.md` ⇒ 权威面 = §7（`:343`）+ §9（`:654`）；**`S-5`（`:95` / `:318`）content security 被 security 域阻塞 ⇒ 外部客户端恒抛、审核链路不可用 ⇒ ⛔ 本项目内不可闭环**；`U-3`（`:658` 真 Kafka 失败位点）· `U-4`（`:659`）· `U-9`（`:664`）· `U-4'`（`:666`）**四项取证缺口仍 OPEN**；「跨域待办」（`:1271` / `:1789`）= 其它域 `*SchemaGate` 同款写法 ⇒ **只登记不修**。
- `docs/DRIFT/article/common.md` ⇒ ✅ Go↔Java **sha256 逐字节一致**（`cad5cb4f…`，49 行，`:50`）+ 契约测试 15 条（`:51`）+ 变异 **8/8 全红**（`:52`）⇒ **无编号未决项**；⚠️ `:22` / `:74-77` 的「消费侧仍是空骨架 / 零生产调用方」描述**已被现状推翻**（覆盖矩阵 `:40-41` 记 `article-api` 41 文件 5759 LOC）⇒ **一条未被本文件自身订正的过期描述**（见 ③ 待批准项 #5）。
- `docs/DRIFT/article/proto.md` ⇒ ✅ 行为层一致、**唯一差异是 option 块**（`:51-62`）；§3.4 三项效力边界（presence / 字段号硬编码 / Jackson）**逐条已验**（`:23-27`）；`P-U-1` 跨栈 gRPC 活体**仍 OPEN**（`:175`）。
- `docs/DRIFT/article/vs-common.md` ⇒ §2 **9 条机制面轴 A1~A9 逐轴结论**（`:36-49`，核对日期 2026-10-27）；§6 **域内未闭合项清单**（`:345-363`，本轮新增）⇒ 域内可动 = **A-1 / A-2 两条**，均不阻塞阶段一；§4 派单仍挂 `G4-2`（MP 自动收集无运行证据，**阻塞本波** `:276`）· `G4-10`（`check-surface-parity.py` 路径指错 ⇒ **假红/假绿** `:296`）· `G4-11`（双侧活体受阻前提已过期 `:297`）。
- `docs/DRIFT/comment/api.md` ⇒ §5 未决 `U1~U11`（`:330-345`）：**`U4`（`:337`）共享台账 `_cross/new-domains-2026-10-23.md:43-45` 的「熔断缺失」结论已被实测推翻 ⇒ 🔴 需收口执行者更正共享台账**；`U10`（`:343`）机制待查（**可观察行为已定**）；判定行 `:24` 注明 3 条 MISMATCH 全修 ⇒ 当前无「必须对齐」分歧项。
- `docs/DRIFT/comment/rpc.md` ⇒ §5 `U1~U16`（`:566-585`）：**`U15`（`:584`）= global `E65` 出向 gRPC 不继承 deadline ⇒ 待修法拍板 + 待共享层** · **`U16`（`:585`）= `E66` Redis 不感知 deadline ⇒ ✅ 已销账（2026-10-31 · A 活体：双侧对拍逐键相等 + 本域装配面守门 3 例）**· `U13`（`:582`）跨栈 `grpc-status:1012` **协议层不可修** · `U10`（`:579`）**销账待 global 合回** · `U4`（`:573`）Go 侧 `t.Skip` 真源缺口 · `U11`（`:580`）Go 侧潜伏缺陷；⚠️ `:1146` 诚实边界：**本域新守门只到「编译通过」、测试未重跑 ⇒ 不得宣布「comment 守门已绿」**。
- `docs/DRIFT/comment/common.md` ⇒ ✅ **SEMANTICALLY EQUIVALENT**（错误码契约层，`:20-33`），MISMATCH 0 条、OBS 1 条；§4 三条 `U1/U2/U3` 全闭（`:123-125`）；⚠️ 同表 `:110`（🔴 RAG 现状）与 `:112`（🟢 2026-10-24 已修复并活体验证）**两行口径不同 ⇒ 读 `:110` 会误判为仍断**。
- `docs/DRIFT/comment/proto.md` ⇒ ✅ 消费方式层 EQUIVALENT（`:15-24`），`U4` 跨栈业务码不可传输 = 协议层（`:112`）。
- `docs/DRIFT/comment/vs-common.md` ⇒ §2 九轴（`:39-51`）＋ §6 未闭合清单（`:207-228`，本轮新增）⇒ 域内可动 = **C-5 / C-7**；§4.2 拍板结论（`:110-119`）：`C-3`/`U15` 方向 = **走 global 统一载体**，本域不擅自选边；`:126-137` `:9091` metrics 暴露面 = **登记类、不占拍板额度**。
- `docs/DRIFT/favorite/api.md` ⇒ §24 **P0 #8 域侧已修**（yaml 去明文默认值 ＋ `@PostConstruct verifyAccessSecret()` ＋ test scope `application.properties` ＋ **3 例真 `ApplicationContextRunner`**）⇒ **域侧半边闭合**；根级半边（compose `:?` ＋ 根 README `.env.example`）⇒ **归合并收口执行者**（`:1550-1579`）；`U9`（`:316-325`）Go 16062 DevServer HTTP 面 ⇒ **待拍板**；405 多一个 `Content-Type` ⇒ 落点 `service/common`（`:25`）。
- `docs/DRIFT/favorite/rpc.md` ⇒ §9.3 **`U7`（`:268`）rpc 层整域双侧活体 = 本域最大缺口**（错误码契约层已双侧对拍，但**那是 `favorite-common`，不是 rpc 层**）；`U1~U6` 逐条复核结论（`:260-268`）；§10-D1 3s deadline 已修（复用 `DeadlineQueryTimeoutInjector`）。
- `docs/DRIFT/favorite/common.md` / `favorite/proto.md` ⇒ 无未闭合项；proto 行为层 **0 行差异**。
- `docs/DRIFT/favorite/vs-common.md` ⇒ §2 九轴；§4「仍未闭合的下一格」= **`DRIFT-HOTAPI-030` 后续格**（总段数 > Go 的 10000 段上限，**显式登记不假装闭合**）；§5 第 0 步「组件真的启动一次」**仍未完成**（DDL 探针是 `psql` 直跑迁移脚本，**全程不启 Java 进程** ⇒ 证明的是 DDL 一致，不是「组件启动过」）。
- `docs/DRIFT/hot/api.md` ⇒ 「未闭合 / 已知差异」表（`:119-206`）`D1~D24`；本轮**订正三处过期状态列**：`D9`（状态列停在「静态确认」而同行备注已是「活体坐实」）· `D14`（停在「已移交 common 域」，实际 2026-10-26 已实现 ⇒ wire 级 L4）· **`D15`（写「🟡 需登记」但早已「✅ 已修」）**；§6 残余 `DRIFT-HOTAPI-031`（`:1238`，已登记 `SD-2026-10-25-CAND-10` ⇒ **登记类**）。
- `docs/DRIFT/hot/rpc.md` ⇒ 域内未闭合项清单为空（`:932`）；`DRIFT-HOTRPC-005`（已取证未签字）· `DRIFT-HOTRPC-006`（保持不修、已登记）。
- `docs/DRIFT/hot/common.md` ⇒ 无未闭合差异（`:73`）；§「2026-10-29 · G3 阶段 1 回填：**G-5 闭合**」= 错误码探针落仓 `tools/errmsg-probe/hot/`（**EXIT=0** ＋ 变异 **4/4** 精确转红）⇒ **`hot-common` 的 R 判定由 R3 升 R4** ⇒ `judge` §九 `G-5` **可撤**。
- `docs/DRIFT/hot/proto.md` ⇒ §3 未决：**`H-3`**（proto 消息体 wire 探针，仍开）· **`G-19`**（proto **消费方式**未取证：`optional` presence / 字段号硬编码 / Jackson 配置）· `G-15`·`G-18`（台账矛盾与位置不统一）**本轮已订正**。
- `docs/DRIFT/hot/vs-common.md` ⇒ §2 九轴；§3 `H-1`（proto 契约网已落码，基准由真源脚本生成、sha256 `4ede7100…`）· **`H-2` 已闭合**（本轮）· **`H-3` 归属已订正**（按 §5.3f，hot-only 探针目录 = **真私有 ⇒ G3 可写**）；§5 交付物清单。
- `docs/DRIFT/_cross/open-items.md` ⇒ §6.2 已知未修按优先级：**`DRIFT-HOTAPI-030`**（`:15`，2049~10000 参数区间两侧不一致，**跨域待拍板**）· **`DRIFT-HOTAPI-031`**（`:16`，超长请求行 400 vs 431 ⇒ 候选 `SD-2026-10-25-CAND-10` **未签字**）· DDL **9 域已做**（`hot` = **N/A** ⇒ **不许写成「已对齐」**）· users 域 8 处 `DEFAULT CURRENT_TIMESTAMP` 残留（`SD-2026-10-25-CAND-16` 未签）· `DRIFT-INFRA-001`（`:59-93`）Flyway 账本撞号（配置层已修；**`FlywayLedgerIsolationTest` 全仓不存在** ⇒ 文档误称其存在）；⚠️ `:65` 的「article-rpc 按指示未动」**仍是 2026-10-23 的旧状态**。
- `docs/DRIFT/_cross/component-coverage-matrix.md` ⇒ **「当前态」节（`:23-78`）才是现状**，历史节（2026-10-23 快照）只作追溯（`:5-14`）；⚠️ 其「当前态」节是 **2026-10-25** ⇒ `article-api` 残留 `-007`（`:40`）已被 `article/api.md:723` 推翻、计数 86 已被 **102** 推翻 ⇒ **共享面落后于域台账**；仍有效的是「**rpc 必须先于 api**」的排序约束（`:13-14`）。<br>⚠️ **2026-10-30 指针订正**：原写 `article/api.md:696` ⇒ 实测 `:696` 落在 **`DRIFT-ARTICLE-API-006` 的条目正文内**（讲的是「2049~10000 段窗口」与 `DRIFT-HOTAPI-030` 的残余登记）⇒ **不是** `-007` 的落点 ⇒ 现行指针 = **`article/api.md:723`**（`#### DRIFT-ARTICLE-API-007 —— … ✅ 已修` 的标题行）⇒ **语义不变**（推翻 `-007` 的结论本身仍成立，只是指针指错了条目）。
- `docs/DRIFT/_cross/proto-verification.md` ⇒ 三档回归网：**user/admin 有 `check-proto-static.sh`**；其余 8 个「只有一次性实测」⇒ 但 `:45` 的「脚本一个都没覆盖」**已过期**（实际已覆盖 5 个）⇒ 共享面待收口订正；`comment`/`like` 的 `package` 归一**已签字**。
- `docs/DRIFT/_cross/domain-common-lanes.md` ⇒ **A1~A9 九条机制面轴的定义**（`:77-85`）＋ 写权边界（§3）＋ 跨 2+ 域未闭合项（§4）⇒ 我域的逐轴结论写在各域 `vs-common.md` §2。
- `docs/Intentional_Deviations/article/rpc.md` ⇒ 已签 **2 条**：`SD-2026-10-24-01`（`:16` 全局锁序统一 `article → publication`）· `SD-2026-10-24-02`（`:147` Kafka 四通道不能逐通道切 broker）⇒ 倾向 = **全局一致性优先于逐字复刻**（两处都写了「为什么**不修回去**」）；`article/{api,common}.md` = **0 字节空档**（非漏写）。
- `docs/Intentional_Deviations/comment/` ⇒ 已签 5 条：`SD-2026-10-23-04`（proto `package` 归一，`proto.md:11`）· `CAND-02`（`report_record` 表 Go 侧不存在，`rpc.md:67`）· `CAND-06`（Go 的 `manage` 清位 SQL **必然报错**，`rpc.md:202`）· `CAND-07`（HS384/HS512 Go 接受 RAG 拒，`api.md:79`）· `CAND-08`（保护层覆盖范围 RAG 更严，`api.md:127`）；**未签字候选 3 条**：`CAND-01`（9 处 `NOT NULL DEFAULT`，`rpc.md:22`）· `CAND-03`（authority/dispatch 默认关闭，`rpc.md:169`）· `CAND-09`（DDL 更严，`rpc.md:121`）⇒ **登记类，不推拍板**；`CAND-04` ⛔ **前提已被实测推翻 ⇒ 作废**（`api.md:18`）。
- `docs/Intentional_Deviations/favorite/` ⇒ 已签 **9 条**（`DEV-FAV-API-001/002` · `DEV-FAV-COMMON-001/002` · `DEV-FAV-RPC-001/003/004/006/007`）⇒ 倾向 = **表达层/部署面为主**，仅 1 条是「RAG 更严」（`DEV-FAV-RPC-007` 的 CHECK）；**已撤销 2 条**（`DEV-FAV-RPC-002` / `-005` ⇒ 前提被逐行核对证伪）⇒ **登记 ≠ 签字的前提必须先复核**；`favorite/proto.md:11`「本域暂无条目」。
- `docs/Intentional_Deviations/hot/` ⇒ 已签 **17 条**（`SD-HOT-RPC-001~004` + `SD-1/2/3` + `SD-HOT-API-001~005/007~010`）⇒ 倾向 = **「RAG 更稳 / 更保守 / 单向增强」占多数**；`SD-HOT-API-006` ⛔ 2026-10-25 已撤销（`api.md:22`）；**未签字候选 1 条** = `SD-2026-10-25-CAND-10`（`api.md:332`）⇒ 登记类；`hot/common.md:7`「本组件无单独登记的签字偏离」＋ `:22-34` 两条「看起来像偏离、实际不是」。
- `docs/Intentional_Deviations/_INDEX.md` ⇒ **CAND 族共 14 条未签字** ⇒ 按 §5.3c **不升级为拍板**；⚠️ `SD-` 编号**存在跨域撞号** ⇒ 引用必须给「**文件 + 域**」，不能只写编号。
- `docs/PhaseGoal/g3.md` ⇒ **lane 判定 🟡**（16 组件：✅15 · 🟡1 · 🔴0）；**差距 = `article-rpc` 一个组件**；**§2.1「本 lane 自修」只有 `article-rpc` 一行**（`S-5` + `U-3/U-4/U-9/U-4′`）；**§2.2 转派 G4** = comment-rpc `D8/D9/U13` · favorite-api `U9/405/B2` · favorite-rpc `U7/U9/E65`；**§3 P0 主责 = #4**，协作 #1/#2/#3/#6/#8，不涉及 #5/#7/#9；§5 收口验收命令全可复算。
- `docs/PhaseGoal-judge.md` ⇒ **§1.3** ✅ 判据 = `R ≥ R3` **且** 归属本组件的必相对齐项 = 0 **且** 有**可复算**判定命令；**§1.5 判定权威层模型**（`:93-108`）= **L0 台账「条目级最新状态」是唯一权威** ⇒ **不得引用台账头的「组件判定」行**；**§8.4 阶段一准入 = ❌ 未通过**，三类硬阻断 = **X-11 密钥链（P0 #8）** · **X-12 项目说明（P0 #5）** · like-rpc `DRIFT-LIKE-003` + global `E60` + 共享层 27 条；**§九 证据缺口**：`G-3`（article 双侧活体 0 条，⚫ **可解除**）· `G-5`（hot-common 探针在 `/tmp` ⇒ **本轮已闭合，可撤**）· `G-6` / `G-7` / `G-13`（4 域无失败路径矩阵，含 **article + hot**）· `G-15` / `G-18`（**本轮已订正**）· **`G-19`**（proto 消费方式未取证，10 个 proto 全部）· `G-20`（共享面 `:45` 已过期）。
- `docs/PhasedGoal.md` ⇒ `:38-52` **P0 九条**（准入）· `:54-68` **P1**（至少 2~3 个机制形成闭环）· `:70-78` **P2**（生产级 SLO / 容量规划 / 灾备 / 灰度 / 穷尽故障组合 ⇒ **不阻塞**）；`:26-36` 明确「本节是语言描述，**可判定版在 `PhaseGoal-judge.md`**」。
- `docs/twoStage.md` ⇒ **§4 阶段 0 事实基线**（`:258-310`：事实调查 / 三条核心链路 / 差异基线 9 字段，**必须保留到阶段 2 验收完成**）· **§5.3 差异与暂缓项 11 项必填**（`:359-379`，明写「不得只写『后续优化』」）· **§5.4 改造记录 9 项必填**（`:381-397`，「必须根据计划**实际修改**项目，而不是只输出建议」）· **§10 阶段归属 A/B/C/D**（`:941-975`：**A 类 = 阶段 1 准入阻断项**；**P0–P3 是严重度、A–D 是阶段归属，两者不能互相替代**）。
- `docs/Pitfalls/article/api.md` ⇒ **P-1**（`:11`「没有 error handler ⇒ 一律 400」只走了一半推理）· **P-4**（`:141`「注解存在 ≠ 机制生效」）· **P-5**（`:171`「代码里写了 ≠ 客户端收到」）· **P-15**（`:516` 变异模块没重编 ⇒ **假的仍全绿**）· **P-21**（`:789`「仍全绿」的第一嫌疑是「**变异没打中**」）· **P-22**（`:830`「**javadoc 写了 Go 出处是待核实的断言，不是证据**」）。
- `docs/Pitfalls/article/rpc.md` ⇒ 16 条 `PF-ARTICLE-RPC-001~016`：`007`（`:244` javadoc 里写的「锁死用例」可能根本不存在）· `008`（`:290` 改的是 bean 读的是 Environment ⇒ 不会变红的假绿）· `016`（`:875` `EOFException` 出现在 `PGStream.receiveChar` ⇒ **不是「PG 挂了」**）。
- `docs/Pitfalls/comment/` ⇒ **PIT-COMMENT-RPC-02**（`:69`「**javadoc 里『对应 Go xxx.go:行号』的断言本身可能与真源相反**」）· `PIT-COMMENT-API-03`（`:101`「**自己的 javadoc 会成为『证据』**」）· `PIT-COMMENT-RPC-05`（`:192`「因错误原因而绿」的假守卫）· `PIT-COMMENT-RPC-13`（`:563`「某域把异常改写了」这类登记**必须逐条核 Go 侧**）。
- `docs/Pitfalls/favorite/` ⇒ **PIT-FAV-API-001**（`:11`「javadoc 里的『Go: …』断言会变成**免检通道**」）· `PIT-FAV-API-007`（`:227`「读源码判定」不是「已验」）· `PIT-FAV-API-008`（`:271` `RANDOM_PORT` 挑的端口可能根本不是你的）· `PIT-FAV-API-017`（`:836`「全仓 grep 零命中」⇒ 模式选错时零命中什么也不证明）· **PIT-FAV-RPC-010**（`:384` 注释里的 Go 断言会「指错行」—— **没有任何测试会红**）· `PIT-FAV-RPC-030`（`:1338` 新写了带 Go 锚点的文件 ⇒ **必须跑「管它的那个门禁」**）· `PIT-FAV-RPC-033`（`:1488` 引用 `文件:行号` 前先确认该符号在**本分支**存在）· `PIT-FAV-RPC-037`（`:1714`「盘点归零」的口径外还有一层从未被机检覆盖）。
- `docs/Pitfalls/hot/api.md` ⇒ **P8**（`:296` 读 go-zero 源码前先确认版本 —— 同一台机器的 module cache 里有三个版本）· **P15**（`:512`「变异没红」的第一反应应是「我的变异脚本错了」）· **P16**（`:561` MockMvc 不是「真 Tomcat」）· **P19**（`:674` 单模块跑会用 `~/.m2` 的 jar）· `P-HOTAPI-25`（`:965` `git add -A` 撞上后台正在改工作区的任务 ⇒ **变异代码被提交进 HEAD**）；`docs/Pitfalls/hot/rpc.md` ⇒ **P-HOTRPC-19**（`:623` 变异打在 `target/classes` 会被 `mvn test` 的资源拷贝覆盖 ⇒ 守门**假绿**）· `P-HOTRPC-22`（`:724` **Boot 2/3 的行号在 Boot 4 上对不上**）· `P-HOTRPC-16`（`:519`「缺陷在别的域的台账里」**跨域登记 ≠ 域内认领**）。
- `docs/Pitfalls/_cross/` ⇒ `domain-common-lanes.md` **PIT-LANE-01~07**（`:17` lane 基点早于脚手架那次提交 ⇒ 回并撞 add/add · `:59`「改一处受益全域」**不是**「可以并行改」⇒ 共享层只有一条写权 · `:168` 守门登记了一条**已不存在**的 lane = 恒绿假绿 · `:196` 守门用**全文 grep** 判措辞 ⇒ 改掉**锚点那一行**后守门恒绿）· `testing-pitfalls.md`（`:35` 绿测试可能是「错误答案的守卫」⇒ 修漂移**先改/删错误断言，再改实现**；`:55` **陈旧 surefire 报告会静默抬高测试计数**；`:108` 守门的**排除清单是架构假设** ⇒ 架构一变就假阳性）· `flyway-ledger-collision.md`（`:5` 唯一例外 = `favorite-rpc` 缺 `baseline-version: 0` ⇒ 只记账不修）· `framework-mechanism-evidence.md`（`:3` 四类**静默失效**的实证：`@Transactional` self-invocation / `@ResponseBodyAdvice` 二次包裹 / `@TableField(typeHandler)` 丢类型信息 / 命令行注入的属性前缀写错）· `fix-round-lessons.md`（`:3-110` 11 条元教训；`:64` **第一轮+第二轮 8 条里 6 条是「跑绿 ≠ 对」**的证据）。

**③ 待批准项**（需 Jason 拍板；**回执人不得自批**）：

1. 🔴 **候选任务与权威基线不一致（本轮最需要裁定的一条）** —— `docs/PhaseGoal/g3.md` **§2.1「本 lane 自修」只有 `article-rpc` 一行**（`S-5` + `U-3/U-4/U-9/U-4′`），而调度员给的候选三条（H-A1 / A-1 / F-1）**一条都不在 §2.1 里**；反过来 §2.1 那 5 项在候选任务里**一条也没有** ⇒ ⇒ **两处对不上**。⇒ **本 lane 不自行改任务范围**，请裁定以哪一份为准（另注：`S-5` 的阻塞源在 Go 侧 `security` 域 ⇒ 按 AGENTS.md §一「`knowledge`/`points`/`security` **范围外 ⇒ 不是待办**」⇒ **`S-5` 本 lane 内不可闭环**）。
2. **H-A1 是否本轮做** —— 归属已按 §5.3f 订正为**域侧自修项**（`tools/proto-wire-probe/hot/` 只被 hot 域引用 ⇒ 真私有）；成本低（hot.proto **无 `optional` / 无 `map`** ⇒ 形态比 favorite 简单）。
3. **A-1 的前提已变** —— `judge` §九 `G-3` 写「⚫ 受阻（宿主端口未映射）」，但实测 `Sea-etcd` 的 `49319→2379` **已在跑** ⇒ **已不是「环境不可得」**，而是「需给 Go 真源副本配 `etcd:2379`（真源 yaml 写死、冻结不可改）」⇒ 是否本轮补跑请裁定。
4. **F-1 环境受限** —— Go 侧 `etcd` / Kafka 仍不可拉起 ⇒ 需裁定「是否仍尝试 / 是否维持登记为最大缺口」。
5. 🟡 **读档时发现的新增台账缺陷（本轮不修，待批准后处理）** —— ① `docs/DRIFT/article/common.md:22` 与 `:74-77` 仍称「消费侧是空骨架 / 本模块零生产调用方」⇒ **与现状冲突**（覆盖矩阵 `:40-41` 记 41 文件 / 5759 LOC）⇒ **未被本文件自身订正**；② `docs/DRIFT/comment/common.md:110` 与 `:112` 同表两行口径相反 ⇒ 读 `:110` 会误判为仍断；③ `docs/DRIFT/_cross/open-items.md:65` 的「article-rpc 按指示未动」仍是 **2026-10-23** 旧状态 ⇒ 与 `article/vs-common.md:319`（`L-1` 补 `baseline-version: 0`）冲突（⇒ **共享面，不在 G3 写权面**）。
6. **登记类事项不占拍板额度** —— `CAND` 族 14 条（`_INDEX.md`）+ `hot` 的 `SD-HOT-API-001~005/007~010` 等已签字项 + `SD-2026-10-25-CAND-10` ⇒ 按 §5.3c **不推拍板、不重复质疑** ⇒ 本回执**无相关待批准项**。
7. ℹ️ **共享面已过期项（登记 ⇒ 归合并收口执行者）** —— `docs/DRIFT/_cross/component-coverage-matrix.md` 当前态节（2026-10-25）落后于域台账 · `docs/DRIFT/_cross/proto-verification.md:45` 仍写「脚本一个都没覆盖」 · `docs/DRIFT/_cross/open-items.md:65` 旧状态 ⇒ **三处均属 SHARED 面 ⇒ 不在 G3 写权面**。

**④ 等批准** ⇒ **批准后才开工**（**回执落笔时**本轮除本回执小节外不写任何代码、不改任何台账；已读动作仅限只读：四档台账 + `_cross` + 阶段标准 + 回执模板）⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182054`）⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。

**⑤ 批准记录：Jason · 2026-10-29 03:44 · 批准范围：本 lane 回执 ① 所列候选任务 **H-A1**（hot proto 消息体 wire 探针落码）+ **A-1**（article 域双侧活体对拍）+ **F-1**（favorite-rpc 整域双侧活体对拍）· **G4 反馈单 1 张**（7 项，含补守门 `PINNED` 两行 `gorm.io/driver/postgres: v1.6.0` + `github.com/lovoo/goka: v1.1.16`）· **自有写权面 2 处台账过期描述**（`article/common.md:22` / `:74-77`、`comment/common.md:110` vs `:112`）**　← 由**调度员代填**（依据：Jason 于 2026-10-29 03:44 在**本 lane 会话内**明示「继续」· 消息 id `182054`；**回执人不得自批**· **代填 ≠ 代批准**）

---

【开工前置回执 · G3 · **2026-10-30**】（第二轮 · 派生层施工单轮）

**① 任务范围**（本 lane = `article` + `comment` + `favorite` + `hot`，**阶段 1**）：

本轮任务来源 = [`docs/DRIFT/_cross/stage1-derivation.md`](../_cross/stage1-derivation.md) §4.0（波次分布）+ §4.4（④ 已闭合 · **台账没销账**）＋ 四域各自的「阶段归属定级附录（2026-10-30 · Go 对照）」。
⚠️ **施工单 §4.1「A/B 类 · 开放且未排期」= 0 条** ⇒ **本 lane 没有「漏排」要补** ⇒ 本轮做的是**销账与订正**，不是新功能开发。

- **W1 · 「段数轴」跨三域假开口的核验与销账**（同一条 `DRIFT-HOTAPI-030` 后续格在三域各登记一份：`A-3`@article · `F-3`@favorite · `H-A2'`@hot）
  - **台账原文的修法 = 共享层 `GoStyleTomcatParameterLimitCustomizer.MAX_PARAMETER_COUNT` 10001 → 10000** ⇒ 三域都写「🔴 global / G4」。
  - ⚠️ **该修法已作废**：`docs/DRIFT/favorite/api.md` §21.3（**2026-10-28 · G3 · L1**）已判「改成 10000 ⇒ Tomcat 在第 10000 段就早于 Go 拒 ⇒ 400+HTML ⇒ **反而制造新漂移**」⇒ ⇒ **派这张单给 G4 = 让 G4 做一件已判作废的改动**。
  - ⇒ **本轮改为核验「RAG 首个翻转点到底在第几段」**，据实定级，**不派单**。
- **W2 · C-5 销账**（comment 域 · `tools/errmsg-probe/comment/` 探针）—— **域侧可自修 / 域侧可销账**。
- **W3 · `docs/DRIFT/hot/rpc.md` §十二.7 待办表**里「comment C-5 探针骨架 ⇒ G4」= **过期待办** ⇒ 订正。
- **W4 · `tools/errmsg-probe/comment/run-parity.sh` 头部注释**残留 follow 字样 ⇒ 域内可自修（`tools/errmsg-probe/comment/` 真私有 ⇒ 按 AGENTS.md §5.3f 归 G3）。
- **W5 · hot 域段数轴补真 Tomcat 门禁**（`hot-api` **无**任何段数用例 ⇒ 取证缺口）⇒ 补齐后才谈定级。

- **不做**（明确边界）：
  - **阶段 2**（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7 / §9 故障矩阵 · 生产流量 · SLO）⇒ 收口执行者的事，**不预支**。
  - **共享层** `service/common/**` ＋ `service/users/users-common/**` ⇒ **只有 G4 能写** ⇒ 本 lane **只读**；发现共享层缺陷 ⇒ **只登记反馈单（7 项）**，不自己动手。
  - **共享索引面**：`AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` / `docs/PhaseGoal-judge.md` / `docs/PhaseGoal/g3.md`（机器生成物）⇒ **不写**。
  - **根级文件**（`docker-compose.yaml` / 根 `README.md` / `.env.example`）⇒ **无人拥有 ⇒ 归合并收口执行者**。
  - **已签字偏离 + `CAND` 族未签字候选**（`docs/Intentional_Deviations/`）⇒ 按 AGENTS.md §5.3c「**登记完就躺着，不升级为拍板**」⇒ 不重复质疑、不推拍板、不「顺手修回 Go 行为」。

**② 已读四档**（**本 lane 面**的具体路径 + 读到的关键结论）：

- `docs/DRIFT/favorite/api.md` ⇒ §24 **P0 #8 域侧已修**（yaml 去明文默认值 + `@PostConstruct verifyAccessSecret()` + **3 例真 `ApplicationContextRunner`**）；**§21.3 = 本轮 W1 的关键依据**（判「共享层 10001→10000」的修法**作废**）· §21.4 域侧自装段数判据 · §21.5 五条真 Tomcat 守门 · §21.7 L4 实测（10001 段 ⇒ `200 + 5003`）；`U9`（Go 16062 DevServer HTTP 面）待拍板 · 405 多一个 `Content-Type`（落点 `service/common`）。
- `docs/DRIFT/favorite/rpc.md` ⇒ §9.3 **`U7` rpc 层整域双侧活体**（`tools/api-full-parity-probe/favorite-rpc/` 已落仓 ⇒ 环境受限已解除）；`U1~U6` 逐条复核结论；§5 照抄 Go 的三处「不完美」（`Location` 二次转义 / 301 body 两个 `\n` / 404·401 一个 `\n`）。
- `docs/DRIFT/favorite/common.md` ⇒ ✅ 错误码契约层双侧逐字节（sha256 `f23fe570…`）；`DEV-FAV-COMMON-001/002` 已签字（`GRPCError(codes.OK,…)` 与 `status.FromError` 判定面 ⇒ **不可达豁免**，实测重数 = 56 个调用点无一传 `codes.OK`）。
- `docs/DRIFT/favorite/proto.md` ⇒ 行为层 **0 行差异**；`PIT-FAV-PROTO-001/002` ⇒「复制忠实 ≠ 消费方式已验」+「默认值不落 wire 只能从生成码看 ⇒ **生成码不在版本库**（`target/` 被忽略）⇒ 必须先构建再读 `writeTo`」。
- `docs/DRIFT/favorite/vs-common.md` ⇒ §2 九轴（**A3/A4/A6/A7/A8 = L4**；A1/A2/A5/A9 = L1~L3）· §4 仍未闭合的下一格 = **`DRIFT-HOTAPI-030` 后续格**（**本轮 W1 的销账对象**）· §5 第 0 步「组件真的启动一次」（DDL 探针是 `psql` 直跑迁移脚本 ⇒ 证的是 DDL 一致，不是「组件启动过」）；**文末定级附录（2026-10-30）= 权威面**（`F-2`/`F-4`/`F-6` 判 D 或 B · 域侧可修）。
- `docs/Intentional_Deviations/favorite/` ⇒ 共 9 条已签（`DEV-FAV-API-001/002` 401·500 body 形态 · `DEV-FAV-COMMON-001/002` · `DEV-FAV-RPC-001/003/004/006/007`）+ **2 条已撤销**（`DEV-FAV-RPC-002/005` ⇒ 前提被逐行核对证伪 ⇒ **登记 ≠ 签字的前提必须先复核**）；`favorite/proto.md` = 0 条；⚠️ `DEV-FAV-API-001/002` 与 `DEV-FAV-RPC-006/007` **有探针断言差异仍在**（`api-envelope-probe` / `ddl-parity-probe`）⇒ 「改成和 Go 一样」会直接 **exit 1**。
- `docs/Pitfalls/favorite/` ⇒ api 17 + rpc 40 + common 2 + proto 2 = 61 条；最关键：`PIT-FAV-API-012`「守门期望值若引用**被测代码的常量** ⇒ 那条守门是哑的 ⇒ 期望值写死字面量」· `PIT-FAV-API-008`「`RANDOM_PORT` 挑的端口可能不是你的（macOS 同号端口被别的会话容器抢走）」· `PIT-FAV-RPC-033`「引用 `文件:行号` 前先确认该符号在**本分支**存在」· `PIT-FAV-RPC-030`「新写了带 Go 锚点的文件 ⇒ **必须跑管它的那个门禁**」· `PIT-FAV-COMMON-001`「拿我的 stub 验我的 stub ⇒ 真源 import 了 grpc 就**不可能** stub」。
- `docs/PhaseGoal/g3.md` ⇒ lane 判定 **✅**（16 组件 ✅ 16 · 🟡 0 · 🔴 0）· §2.1「本 lane 自修」= **无** · §2.2 转派 G4 = 4 行（comment-rpc `D8/D9/U13` · favorite-api `U9/405/B2` · favorite-rpc `U7/U9/E65` · hot-api `DRIFT-HOTAPI-030/031`）⇒ **落点 100% 在共享层 ⇒ 本 lane 只登记**；§3 **P0 #4 主责**（article 失败路径最密集）· 协作 #1/#2/#3/#6/#8；§4 矩阵 C 11 项协作；§5 收口验收命令全可复算。
- `docs/PhaseGoal-judge.md` ⇒ 权威判定源；§七 **P0 #4 = ✅ 满足**（227 条失败路径矩阵 · 变异 13/13 精确转红 · 错误码 9/9 域双侧逐字节）· §九 **`G-19`**（proto **消费方式**在 10 个 proto 上全部未取证）· §九 `G-3`（article 双侧活体，2026-10-29 已闭合为 3 场景 + 两侧播种同一行）；⚠️ **证据强度不许混写**：双侧活体 = L4 · 失败路径矩阵 = **L3（只起 RAG 单侧）** · 错误码 = L1/L2（双侧**静态**）。
- `docs/PhasedGoal.md` ⇒ P0 九条（`:38-52`）/ P1 竞争力（`:54-68`）/ P2 不阻塞（`:70-78`）⇒ 可判定版在 `PhaseGoal-judge.md`（`:26-36` 明写）。
- `docs/twoStage.md` ⇒ §4 阶段 0 事实基线（`:258-310`）· §5.3 差异与暂缓项 **11 项必填**（`:359-379`，明写「不得只写『后续优化』」）· §5.4 改造记录 **9 项必填**（`:381-397`，「必须根据计划**实际修改**项目，而不是只输出建议」）· §10 阶段归属 A/B/C/D（`:941-975`）。
- `docs/RoundPlan/_unresolved.md` ⇒ `GAP-11`（B 类 6 条 · 全不阻阶段一）· `GAP-12` **已关闭**（指针失效）· `GAP-13/14/15`；⇒ **`GAP-11` 里落到本域的两条 = `A-5`/`F-6`（明文默认值，已由 `DEP-03`/`S1-USERS-002` 覆盖 ⇒ 不新开任务）与 `C-5`（comment 探针）**。
- `docs/RoundPlan/_task-universe.md` + `docs/DRIFT/_cross/stage1-derivation.tsv` ⇒ 施工单机器层（16 列 · 含 `wave` 派工面）⇒ **G3 命中 414 行 / 状态开放 179 / A/B 类未排期 0**。
- `docs/DRIFT/_cross/stage1-derivation.md` ⇒ §4.0 波次分布（G1 275/G2 281/**G3 404**/G4 274）· §4.1 **空表**（真缺口 A/B = 0）· §4.4 **④ 已闭合 103 条**（「台账没销账 · 无待实现项」⇒ 处置 = 回写台账）· §七 效力边界（只读台账不读代码 ⇒ 台账写 ✅ 仍须按 §3.3 确认）。
- `docs/DRIFT/_cross/live-parity-2026-10-30.md` ⇒ 三条硬纪律 + **17 条「工具自己骗人」** + 本轮台账改判 9 条（代码是真相 · 台账错就改台账）+ 三层证据报告（A 活体 115 / B 静态 974 / C 待核实 94）。
- `docs/DRIFT/_cross/ddl-type-divergences.md` ⇒ §四 **9 域逐域机器核**（`article` 残留 0 · `comment` 21 实质 · `favorite` 2 · `hot` **N/A**）· §4.1「**没有结论 ≠ 没问题**」5 档；⚠️ **DEFAULT 残留 22 处**（users 8 + message 6 + comment 8）**仍未修**、**刻意保留**。
- `docs/DRIFT/_cross/open-items.md` + `_cross/component-coverage-matrix.md` + `_cross/domain-common-lanes.md` + `_cross/cross-domain-coupling.md` + `_cross/go-anchors.md` ⇒ 跨组件不变量（共享 Flyway 账本 · 建表责任交叉 · 事实投递链路 · **rpc 必须先于 api** 的排序约束）+ A1~A9 九轴定义。
- `docs/Pitfalls/_cross/` ⇒ `testing-pitfalls.md`（绿测试可能是「错误答案的守卫」⇒ 修漂移**先改/删错误断言再改实现**）· `framework-mechanism-evidence.md`（四类静默失效）· `flyway-ledger-collision.md` · `fix-round-lessons.md`（11 条元教训）· `signed-deviations.md`（签字偏离的处置纪律）。
- `docs/Worktree.md` §二·五 + §三·1d/§1f ⇒ **开工前置四步**（① 定任务 ② 读四档 ③ 回执 ④ 经批准才开工）· 回执落点与核验命令 · 波次写权表 · **域内探针按域授权（T-0.5 选项 A）**。

**③ 待批准项**（需 Jason 拍板；**回执人不得自批**）：

1. 🔴 **「body form 参数轴」= 真实残留（本轮核出并已登记为 `F-8`）** —— ⚠️ **方向与本回执落笔时的初判相反，已订正**：**RAG 比 Go 宽一格，不是「RAG 更严」**。
   ⇒ **依据（Go 侧逐行）**：`net/http/request.go:1294`（`parsePostForm` 的 `application/x-www-form-urlencoded` 分支 ⇒ **`url.ParseQuery(string(b))`** ⇒ **Go 的 10000 段上限同样作用于 body**）+ `net/url/url.go:1019-1021` ⇒ ⇒ **Go 在第 10001 个 body 参数就拒**；而 RAG 侧：① 共享层 Go 语义闸门 `GoStyleFormValues#goParseViolation` **只判 `request.getQueryString()`**（**不判 body**）⇒ body 轴上没有 Go 语义判据；② 兜底只剩**容器** `maxParameterCount`（运行时被 `GoStyleTomcatParameterLimitCustomizer` 抬到 **10001**）⇒ ⇒ **RAG 允许第 10001 个 body 参数 ⇒ 宽一格**。
   ⇒ ⚠️ 与 query 段数轴（两侧同在第 10001 段翻转 ⇒ 本轮已销账）是**两条不同的轴**，不能一起销账。
   ⇒ ⛔ **不许改容器常量**（改 10000 ⇒ Tomcat 抢在应用层之前 HTML 400 拒 ⇒ **制造新漂移**；改 -1 ⇒ 容器这道闸门整个消失）⇒ ⇒ **正确修法 = 域侧闸门也判 body**（照 favorite 域 `FavoriteGoQueryLimitInterceptor` 的形态）。
   ⇒ **已按 C 类登记**（`docs/DRIFT/favorite/vs-common.md` §6 的 **`F-8`** · 跨域同源 ⇒ `article` / `hot` 同轴）⇒ 按 AGENTS.md §5.3c「**登记完就躺着**」⇒ **不推拍板**。
2. 🔴 **共享面 / 机器生成物的过期表述** ⇒ **本 lane 不写，只登记给合并收口执行者**：
   - `docs/DRIFT/_cross/open-items.md:15`（`DRIFT-HOTAPI-030`「跨域待拍板」条）与 §四.4 的「三域销账」**不再自洽**。
   - `docs/PhaseGoal/g3.md` §3 **P0 #4** 行仍写「`article` 域**没有**失败路径探针」，而 `docs/PhaseGoal-judge.md:375`（§七 P0 #4）已写「失败路径矩阵 **0 域无缺口**（227 条 · article **104** 条）」⇒ **生成器输入未同步**（该文件是机器生成物 ⇒ 改基线后重跑生成器，不是手改）。
3. ℹ️ **W5 的两种结局都已预判**：门禁绿 ⇒ `H-A2'` 销账；门禁红 ⇒ **真漂移** ⇒ 转域侧自修或升为 A/B 类 ⇒ **届时再定级**（本回执不预判结论）。
4. ℹ️ **登记类事项不占拍板额度** —— `CAND` 族（comment 4 条未签字 · hot `SD-2026-10-25-CAND-10`）＋ 四域已签字偏离（article 2 · comment 5 · favorite 9 · hot 17）⇒ 按 §5.3c **不推拍板、不重复质疑** ⇒ 本回执**无相关待批准项**。

**④ 等批准** ⇒ **批准后才开工**（本回执落笔时除本小节外未写任何代码 / 未改任何台账；已读动作仅限只读：四档台账 + `_cross` + 阶段标准 + 本轮实跑验证）。
⇒ **Jason 于 2026-10-30 10:32 在本 lane 会话内明示「开始工作」** ⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。

**⑤ 批准记录：Jason · 2026-10-30 10:32 · 批准范围：本轮 W1~W5 五项（段数轴跨三域核验与销账 · C-5 销账 · hot §十二.7 过期待办订正 · comment 探针头部注释订正 · hot 域段数轴真 Tomcat 门禁补齐）＋ ③ 第 1 条的「body form 参数轴」登记（已落 `favorite/vs-common.md` §6 `F-8` · C 类）　← 由**调度员代填**（依据：Jason 于 2026-10-30 10:32 在**本 lane 会话内**明示「开始工作」；**回执人不得自批**· **代填 ≠ 代批准**）

---


---

【开工前置回执 · G3 · **2026-10-30**】（第三轮 · rpc 域「指标抓取载体」轮）

**① 任务范围**（本 lane = `article` + `comment` + `favorite` + `hot`，**阶段 1**）：

本轮任务来源 = **调度员跨会话下发**（Jason 已明示批准「这波就补载体」）⇒ 给 rpc 域补「指标抓取载体」：

- **落码三域**（载体 = **Actuator 的 servlet 载体**，形态照 `service/follow/follow-rpc` 已落地的同款：
  `pom` 加 `spring-boot-starter-web` ＋ 顶层 `server.port` = Go 侧观测端口 ⇒ `management.endpoints.web.exposure.include` 才有出口）：
  - `article-rpc` —— Go 观测端口 **9092**（go-zero `Prometheus:` 块）
  - `favorite-rpc` —— Go 观测端口 **16062**（go-zero `DevServer:` 块）
  - `hot-rpc` —— Go 观测端口 **19091**（go-zero `Prometheus:` 块）
- **`comment-rpc` —— 不落码**，只写台账依据（**理由见 ③ 第 1 条：派工给的前提经实测证伪**）。

- **不做**（明确边界）：
  - **阶段 2**（`docs/twoStage.md` §1.3 / §2.2 / §6 / §7 · §9 故障矩阵 · 生产流量 · SLO）⇒ 收口执行者的事，**不预支**。
  - **共享层** `service/common/**` ＋ `service/users/users-common/**` ⇒ **只有 G4 能写** ⇒ 本 lane **只读**；**本轮尤其不加任何 opt-out 属性**（共享层正在裁「rpc 域观测端口要不要挂 Go 的 rest 中间件」⇒ 那是 G4 的落点）。
  - **共享索引面**：`AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` / `docs/PhaseGoal-judge.md` / `docs/PhaseGoal/g3.md`（机器生成物）⇒ **不写**。
  - **根级文件**（`docker-compose.yaml` / 根 `README.md` / `.env.example`）⇒ **无人拥有 ⇒ 归合并收口执行者**。
  - **已签字偏离 ＋ `CAND` 族未签字候选**（`docs/Intentional_Deviations/`）⇒ 按 AGENTS.md §5.3c「**登记完就躺着，不升级为拍板**」⇒ 不重复质疑、不推拍板。

**② 已读四档 ＋ 本轮自读原文**（**每条带 `文件:行号`**；且**指消费点、不指声明点** —— AGENTS.md §3.3.1）：

- **Go 侧观测面机制链（本轮核心，全部自读 ＋ 用真类型探针坐实）**：
  - `zrpc.RpcServerConf` **内嵌** `service.ServiceConf` ⇒ `go-zero@v1.10.2/zrpc/config.go:39`
  - `ServiceConf` 里有**两个**观测块字段 ⇒ `go-zero@v1.10.2/core/service/serviceconf.go:38`（`Prometheus prometheus.Config`）/ `:40`（`DevServer DevServerConfig`）
  - **消费点**（不是声明点）⇒ `go-zero@v1.10.2/zrpc/server.go:63` `c.SetUp()` ⇒ `core/service/serviceconf.go:62` `prometheus.StartAgent(sc.Prometheus)` ＋ `:77` `devserver.StartAgent(sc.DevServer)`
  - `Prometheus` 代理 ⇒ `go-zero@v1.10.2/core/prometheus/agent.go:31`（`Host` 为空 ⇒ **直接 return，不启动**）/ `:38`（`http.Handle(c.Path, promhttp.Handler())` ⇒ 只有 `/metrics` 一个路由）/ `:39-41`（**独立端口** `http.ListenAndServe`）
  - `DevServer` 代理 ⇒ `go-zero@v1.10.2/internal/devserver/config.go:4-13`（`Enabled bool json:",default=true"`）+ `internal/devserver/server.go:79-88`（`:80` `if !c.Enabled { return }` ⇒ **整段缺失 ⇒ 零值 false ⇒ 不启动**）/ `:42`（`HealthPath`）/ `:48`（`MetricsPath`）/ `:53-57`（pprof）/ `:70-73`（**独立端口**）
  - ⚠️ **yaml 里的块名对得上「go-zero 的内建字段」** —— 这是本轮最关键的一步：`Prometheus` / `DevServer` 看起来像 Go 仓自有的配置，**其实来自 go-zero 本身**（`service.ServiceConf`）⇒ ⇒ **它们是真的在被消费**（此前「grep Go 仓零命中 ⇒ 以为是死配置」的推断是错的）。
- **Go 侧四域 yaml 的实测填充值**（探针 = **真类型** `zrpc.RpcServerConf` ＋ 真源 `conf.Load` ⇒ **不复刻结构体** ⇒ 避开「漏 tag ⇒ 结论翻转」那个坑）：
  - `article-rpc`：`etc/article.yaml:21-24` ⇒ 探针读出 `Prometheus{Host:"0.0.0.0" Port:9092 Path:"/metrics"}` ⇒ Go 侧真起 **`0.0.0.0:9092` 的 `/metrics`**
  - `hot-rpc`：`etc/hot.yaml:8-11` ⇒ 探针读出 `Prometheus{Host:"0.0.0.0" Port:19091 Path:"/metrics"}` ⇒ Go 侧真起 **`0.0.0.0:19091` 的 `/metrics`**
  - `favorite-rpc`：`etc/favorite.yaml:23-30` ⇒ 探针读出 `DevServer{Enabled:true Host:"0.0.0.0" Port:16062 MetricsPath:"/metrics" HealthPath:"/healthz" EnableMetrics:true EnablePprof:true}` ⇒ Go 侧真起 **`0.0.0.0:16062` 的 `/metrics` ＋ `/healthz` ＋ `/debug/pprof/*`**
  - `comment-rpc`：`etc/comment.yaml` **无该段** ⇒ 探针读出 `Prometheus{Host:""}` ＋ `DevServer{Enabled:false}` ⇒ **go-zero 层不起**；但 Go 侧**自建** ⇒ `service/comment/rpc/comment.go:38-41`（`http.Handle("/metrics", promhttp.Handler())` ＋ `http.ListenAndServe(":9091", nil)`）⇒ ⇒ Go 侧 comment-rpc **有**指标面，在 **`:9091` 的 `/metrics`**
  - ⚠️ **去段变体探针**（把 yaml 里对应整段删掉再加载）⇒ 读出 `Prometheus{Host:""}` ＋ `DevServer{Enabled:false}` ⇒ ⇒ **坐实「整段缺失 ⇒ 零值 ⇒ `default=true` 不跨缺失段生效 ⇒ 不启动」**
- **Go 侧两处「假声明 / 死代码」（本轮核出 ⇒ 不作为对齐依据）**：
  - `service/hot/rpc/internal/metrics/metrics.go:14-26`（硬编码 `:9091`）**未被调用** —— `service/hot/rpc/hot.go` 全文无 `metrics.InitMetrics` ⇒ **死代码**；hot-rpc 的观测面**只有** go-zero 的 **19091**。
  - `service/article/rpc/internal/` 下**无 metrics 包**（`service/article/rpc/article.go` 全文无 metrics import）⇒ **Go article-rpc 没有自定义 counter** ⇒ RAG 侧 `article-rpc` 的 `article_service_*` meter 是 **RAG 自有**（比 Go **宽**的一面）⇒ 本轮据实订正 RAG 注释里的假出处。
- **RAG 侧现状（自读）**：`article-rpc` 的 `pom.xml:171`（actuator）＋ `:175`（micrometer-prometheus）**但无 `spring-boot-starter-web`** ⇒ `application.yml:232-247` 的 `management.endpoints.web.exposure.include` 是**死配置**（classpath 上没有 servlet 载体 ⇒ `WebApplicationType` 推导出 `NONE` ⇒ 那一族自动配置根本不加载）；`favorite-rpc` 的 `application.yml:42` **`spring.main.web-application-type: none`** ⇒ 即便补上 web starter 也**不会起容器** ⇒ 必须一并反转；`hot-rpc` 的 `application.yml:213-218` 是三处**反转项**（`management.server.port: -1` / `management.endpoints.enabled-by-default: false` / `management.prometheus.enabled: false`）。
- **守门基建（自读）**：`article-rpc` 的 `ArticleRpcRuntimeBootGateTest`（**真 fat jar** ＋ **真 PG 容器** ＋ Nacos/Kafka/Redis 指向不可达地址）＝ 本轮「真启动守门」的模板；`support/RealGrpcPort`（本域专用端口段 `22400–22479`）＋ `support/RealContainerHostPort`（容器宿主口段 `22850–22869` ＋ 登记册守恒）＝ 端口基建。

**③ 待批准项**：**无**（Jason 已明示批准「这波就补载体」⇒ 范围 / 端口 / 载体形态均已定）。

1. ℹ️ **一条「派工前提被实测证伪」必须如实登记**（⇒ **不构成拍板项**，按 §5.3c 登记完就躺着）：
   ⇒ 派工给的口径是「`comment-rpc` Go 侧无该面 ⇒ 不补」⇒ **本轮实测证伪**：Go 侧 `service/comment/rpc/comment.go:38-41` **自建** `promhttp` 处理器并在 **`:9091`** 起 HTTP 服务 ⇒ ⇒ **Go 侧 comment-rpc 有指标面（`:9091` `/metrics`）** ⇒ ⇒ **RAG 侧 comment-rpc 缺该面 = 一条真实漂移**（不是「无该面」）。
   ⇒ **本轮仍不落码 comment-rpc**：① 超出本轮派工范围（本轮只批了三个域的载体）；② comment 的 Go 侧是**自建** `promhttp`（与另三域的 go-zero 代理**机制不同**）⇒ 载体形态要另做判断，不能照抄 follow-rpc。
   ⇒ **已按 C 类登记**在 `docs/DRIFT/comment/vs-common.md` ⇒ **下一轮**补 ⇒ **不推拍板**。
2. ℹ️ **暴露形态差异（路径）按 follow-rpc 已登记的口径处理，不新造偏离登记**：Go `/metrics`（＋ favorite 的 `/healthz` ＋ pprof）↔ RAG `/actuator/prometheus`（＋ `/actuator/health`）⇒ **端口一字不差、路径不同**（Actuator 的固定前缀）⇒ 属 `twoStage.md` §10 的**暴露形态差异**，与 `DRIFT-FOLLOW-006` 同款。

**④ 等批准** ⇒ **批准后才开工**（本回执落笔时除本小节外未写任何代码 / 未改任何台账；已读动作仅限只读：四档台账 ＋ `_cross` ＋ 阶段标准 ＋ Go/RAG 原文自读 ＋ 本轮探针实跑）。
⇒ **Jason 于 2026-10-30 明示批准「这波就补载体」**（经调度员跨会话转达）⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。

**⑤ 批准记录：Jason · 2026-10-30 · 批准范围：本轮 rpc 域「指标抓取载体」＝ `article-rpc`（9092）/ `favorite-rpc`（16062）/ `hot-rpc`（19091）三个模块的 pom ＋ yml 改造 ＋ 各一条真启动守门 ＋ 真启动验收证据 ＋ 台账回写 ＋ 提交到 G3 分支（不 push、不开 PR）；`comment-rpc` 只写台账依据　← 由**调度员代填**（依据：调度员跨会话转达 Jason 2026-10-30 明示批准「这波就补载体」；**回执人不得自批**· **代填 ≠ 代批准**）

- **本域落码点**：`service/favorite/favorite-rpc` 补 Actuator servlet 载体（Go 观测端口 **16062**，go-zero `DevServer:` 型）
  - RAG 侧现状：`pom.xml:247`（actuator）＋ `:251`（micrometer-prometheus）**无** `spring-boot-starter-web`；`application.yml:230-247` 的 exposure 是**死配置**
  - 🔴 **必须一并反转的一处**：`application.yml:42` 的 `spring.main.web-application-type: none` ⇒ **不反转则补了 web starter 也不会起容器**（`WebApplicationType` 被显式钉成 `NONE`）
  - 🔴 **必须同步更新的一条既有守门**：`service/favorite/favorite-rpc/src/test/java/com/rag/favorite/favorite/rpc/FavoriteRpcProductionStartupTest.java:84-95` 断言「生产 yaml **必须**写 `web-application-type: none`」⇒ 载体落地后该断言的前提反转 ⇒ 按 AGENTS.md §3.3 ③「**先改/删错误断言，再改实现**」处理
  - **本域可销账的一条**：同文件 `application.yml:39-40` 已把「RAG 侧没有 16062 那个面」登记为域内未决项 `U9`（待拍板）⇒ **本轮 Jason 已批准落地** ⇒ 端口面销账（**路径差异**仍按 ③ 第 2 条登记）
  - 本域新守门：`FavoriteRpcPrometheusCarrierGateTest`（**真 fat jar** ⇒ 真启动后打 `/actuator/prometheus`）

## 📌 追加 · 🔵 **本域 rpc 层的「双侧活体」证据已补齐**（2026-10-29 · R2 lane · `align/r2-favorite`）

### ① 一句话

本 lane（`favorite` 域）此前**没有任何「两侧真栈同时跑」的 rpc 层证据**
⇒ RoundPlan `docs/RoundPlan/Round-2.md` **T-2.3 · F-1** 把这条记成「🔴 环境受限（Go 侧 etcd / Kafka 拉不起来）」。
⇒ **该判定已过期**（2026-10-29 实测推翻：宿主 `Sea-etcd` 49319 / `Sea-nacos` 49848 / `Sea-postgres` 49432 / `Sea-kafka` 49992 / `Sea-jaeger` 49317 **全部在跑**）
⇒ ⇒ 新增产物 **`tools/api-full-parity-probe/favorite-rpc/`**（`run.sh` / `expected.txt` / `mutate-parity.sh` / `README.md` / `java/` / `seed.sql`）
⇒ ⇒ **25 条 FROZEN 用例：Go 真源 == RAG == 冻结期望值（逐字节 · gRPC wire 层）**，`EXIT=0`。

⇒ 逐条证据（含 26 条用例表、12 条实测坑、3 条变异）⇒ [`rpc.md`](rpc.md) 末尾的
「📌 追加 · ✅ favorite-rpc 的 rpc 层双侧活体对拍已落地」一节。

### ② ⚠️ **对 common 面（9 条机制面轴）的影响：只读，不改判**

| 项 | 结论 |
|---|---|
| **A1（超时 / deadline）· 出向侧 `E65`** | 🔴 **仍 OPEN** —— 本探针**不测**出向 deadline 传播 ⇒ **既不构成反驳，也不构成销案** ⇒ 仍以 §1 的 `E65` 行（共享层 `service/common` · `RpcInterceptor` 只绑 `DeadlineState`）为准 |
| **A2~A9 其余各轴** | **不变** —— 本探针验的是 **favorite 域自己的 wire 契约**，不涉及共享层机制的实现面 ⇒ §2 的九轴逐轴结论**一字不改** |
| **共享层写权** | ✅ **零触碰** —— 本轮对 `service/common/**` 与 `service/users/users-common/**` 的改动 = **0 行**（AGENTS.md §5.3f：共享层只有 `align/g4-service-common` 能写） |
| **域侧产品代码** | ✅ **零改动** —— 本轮对 `service/favorite/**` 的改动 = **0 行**（探针只读它 / 编译它 / 启动它） |

### ③ 本 lane 对 common 面**新登记**的一条事实（**不修，只登记**）

| 编号 | 摘要 | 落点 | 状态 | 证据 |
|---|---|---|---|---|
| （域侧 · 非编号） | **`favorite-rpc` 的两个出向 client 走的是**「服务端路径 ⇒ `GoStyleRequestDeadline.applyTo` 恒 no-op」** ⇒ 出向**零期限**（与 Go 的 `zrpc` 无条件 2s 客户端超时 + `depCtx` 传播父 deadline 相反）⇒ ⇒ 一旦下游 `user.rpc` **在场但慢**，RAG 侧会**一直等**而 Go 侧 **2s 就断** | **共享层** `service/common`（`RpcInterceptor` / `GoStyleRequestDeadline`）⇒ 落点同 `E65` | 🔴 **OPEN**（**与 §1 的 `E65` 同一条**，本轮只是给它补了一个**可观测后果**） | 本轮探针**未直接测**（下游缺席 ⇒ 走不到超时分支）⇒ **本条只登记，不改判** |


---

## 9. 🔴 **DDL 逐域机器核**（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G3 lane · `favorite` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并逐域实跑，结论只落在 `docs/PhaseGoal-judge.md` §七 **G-8 / G-9** ⇒ **域级证据链断了一节** ⇒ 本节把 `favorite` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不改任何差异**（RoundPlan T-3.1：冒出的差异按域登记，不当场修）。
> ⚠️ **`favorite` 域不是 N/A** —— `hot` 域才是 N/A；**本域两侧都有真库**（Go 侧 4 张表 / RAG 侧 4 张表）⇒ 本节是**实跑**结论。

### 9.0 🔴 **本域有一条「机器报成实质差异、实为口径差」的残留 —— 根因 = Go 侧 DDL 的「执行顺序」**

> ⚠️ **先说清楚这一条**，否则 §9.4 的「2 处残留」会被误读成「RAG 无出处地加严」。

**机器报出的原文**（`tools/difftest/ddl-parity.sh favorite`）：

```
实质差异: favorite_fact_outbox.favorite_fact_outbox_aggregate_version_range（仅 RAG 有）  contype=c
  CHECK ((aggregate_version = ANY (ARRAY[(1)::bigint, (2)::bigint])))
```

**但 Go 真源的 `.sql` 里逐字写着这条 CHECK** —— `service/favorite/rpc/internal/model/001_favorite_fact_outbox.sql:6`：

```sql
aggregate_version bigint NOT NULL CHECK (aggregate_version IN (1,2)),
```

**为什么它没进 Go 活库** —— `tools/difftest/ddl-parity.sh` 的 Go 侧顺序是 **app-first**（先跑真源 `InitDB` 的 AutoMigrate，再补放手写 `.sql`）⇒ `AutoMigrate` 已经建好 `favorite_fact_outbox` ⇒ 补放 `001` 时 **`CREATE TABLE IF NOT EXISTS` 变成 no-op** ⇒ 整表跳过 ⇒ **内联在 CREATE TABLE 里的 CHECK 建不出来**。
⇒ **实跑日志原文**（`bash tools/difftest/ddl-parity.sh favorite`）：

```
  --- Go 手写迁移: 001_favorite_fact_outbox.sql ---
NOTICE:  relation "favorite_fact_outbox" already exists, skipping
```

⇒ ⚠️ **注意**：`001` 里的**部分索引** `favorite_fact_outbox_pending` 是**独立语句** ⇒ 它**照样建出来**（⇒ 这也是本域两个工具口径**唯一**真正不同的地方）。

**真源文档指定的顺序是 sql-first** —— `service/favorite/rpc/DELIVERY_ACCEPTANCE.md:7`（"## 部署和交接"）：

> 「先在现有 Favorite PostgreSQL 库执行 `internal/model/001_favorite_fact_outbox.sql`，再执行 `internal/model/002_favorite_fact_delivery.sql`。」

⇒ **本轮已在真 PG 上把 sql-first 口径直接验出来**（只放 Go 的 `001`、不带 AutoMigrate）：

| 侧 | 约束名 | 定义 |
|---|---|---|
| Go（**sql-first**，本轮实跑） | `favorite_fact_outbox_aggregate_version_check` | `CHECK ((aggregate_version = ANY (ARRAY[(1)::bigint, (2)::bigint])))` |
| RAG（活库） | `favorite_fact_outbox_aggregate_version_range` | `CHECK ((aggregate_version = ANY (ARRAY[(1)::bigint, (2)::bigint])))` |

⇒ ⇒ **定义逐字相同、名字不同** ⇒ ⇒ **在真源文档指定的顺序下，这条不是「RAG 多出来的约束」，而是「同一条约束的不同名字」** ⇒ ⇒ **它正是已签字项 `DEV-FAV-RPC-006` 记的 12 条命名差里的第 1 条约束**（该条原文就写「`favorite_fact_outbox_aggregate_version_check`↔`..._aggregate_version_range`」）。

⚠️ **本域因此有「两个 Go 侧 DDL 口径」并存** —— 两者都不是错的，差别只在**执行顺序**：

| 口径 | 载体 | Go 侧 `favorite_fact_outbox` 的 CHECK | 判定基准 |
|---|---|---|---|
| **app-first**（应用先起 → 运维后跑 `001..004`） | `tools/difftest/ddl-parity.sh`（**本轮跑的就是它**） | **没有**（`CREATE TABLE IF NOT EXISTS` 变 no-op） | 本轮机器口径 |
| **sql-first**（运维先跑 `001..004` → 应用后起） | `tools/ddl-parity-probe/favorite/`（**域内自有探针**）· 真源 `DELIVERY_ACCEPTANCE.md:7` 指定 | **有**（`..._aggregate_version_check`） | **真源文档指定的权威口径** |

⇒ ⇒ **本节的净结论**：机器跑出的 2 处「实质差异」**全部落在已签字范围内**（① 落在 `DEV-FAV-RPC-006` 的同一对象；② 落在 `DEV-FAV-RPC-007`）⇒ ⇒ **本域零「未签字的残留」**；① 的成因**不是 RAG 加严，而是执行顺序口径差**。

### 9.1 执行口径（命令原文 · EXIT · 日期 · 环境）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（14:4x–15:2x 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b` · `align/r3-ddl-g3-2`<br>基点 = `do-my-own-gh` @ **`d10227c5`** |
| 容器 / 库 | `ddl-favorite-pg`（`postgres:16-alpine` · 端口 **55564**）· Go 库 `go_favorite_db` · RAG 库 `rag_favorite_db` |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本只**复制**副本，从不写入 |
| Go 侧 schema 来源 | **真源自己的 `AutoMigrate`**：`service/favorite/rpc/internal/model/db.go:20`（`func InitDB(conf DBConf) *gorm.DB`）→ `:33`（`db.AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{})`）⇒ **3 张表** ⇒ 之后由真源手写 `.sql` 补放：`001_favorite_fact_outbox.sql` · `002_favorite_fact_delivery.sql` · `003_favorite_target_revision.sql` · `004_favorite_legacy_fact_marker.sql`（`service/favorite/rpc/internal/model/`） |
| ⚠️ Go 侧手写 `.sql` 的**执行者** | **没有任何 Go 代码执行它们** —— 全仓只有 `_test.go` 读它们（`service/favorite/rpc/internal/model/favorite_fact_test.go:58` · `service/favorite/rpc/internal/server/favorite_fact_rpc_test.go:129`）⇒ ⇒ **属运维手工步骤** ⇒ 顺序由运维决定 ⇒ ⇒ **这正是 §9.0 口径差的根因** |
| Go 侧的权威部署顺序 | **`service/favorite/rpc/DELIVERY_ACCEPTANCE.md:7`** —— 「先执行 `001`，再执行 `002`」⇒ ⇒ **sql-first** |
| RAG 侧 schema 来源 | 仓内 `service/favorite/favorite-rpc/src/main/resources/db/migration/`：`V1__create_favorite_folder.sql` · `V2__create_favorite_item.sql` · `V3__create_favorite_fact_outbox.sql` · `V4__create_favorite_legacy_fact_marker.sql` · `V5__add_favorite_fact_delivery.sql` · `V6__align_favorite_schema_with_go.sql` · `V7__align_favorite_outbox_time_columns_with_go.sql`（**Flyway**；生产 `locations: classpath:db/migration` ⇒ `application.yml:109` · `baseline-version: 0` ⇒ `application.yml:108`） |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 差异数是**原始值** |
| 两侧表集合 | Go `go_favorite_db` = {`favorite_folder`, `favorite_item`, `favorite_fact_outbox`, `favorite_legacy_fact_marker`} · RAG `rag_favorite_db` = **同名 4 张** ⇒ **表名两侧完全相同** ⇒ `table_map` 为空 |

**命令 ① —— 建库 + 双侧活体对拍（索引 / 约束 / 触发器 + 源码↔活库 一次跑全）**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b"
bash tools/difftest/ddl-parity.sh favorite
```

⇒ **EXIT=1** —— 末行 `== 实质差异合计 2 处 ⇒ 退出码 1 ==`；runner 行 `=== 对拍退出码: 1 ===`
⇒ Go 探针打印 `AUTOMIGRATE_OK`；Go 手写迁移 `001~004` 按序补放（`001` 整表跳过 ⇒ 见 §9.0）；RAG 侧按序执行 `V1`→`V7`

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`）**

```bash
python3 tools/difftest/check-ddl-static.py --domain favorite --indexes --check-source
```

⇒ **EXIT=1**（**2 处**）—— 与 ① 同数同因 ⇒ 两条路径互为复算

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain favorite --print-rag-sql
```

⇒ **EXIT=0**，输出 **7 行**（V1~V7），与 ① 实际执行的 7 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**（含 `V7__align_favorite_outbox_time_columns_with_go.sql` ⇒ **G-9 当初抓出的那一份就在清单里**）

**命令 ④ —— 真源文档指定顺序（sql-first）的直接验证**

```bash
docker exec ddl-favorite-pg psql -U admin -d postgres \
  -c "CREATE DATABASE go_favorite_sqlfirst_probe;"
docker exec -i ddl-favorite-pg psql -U admin -d go_favorite_sqlfirst_probe -v ON_ERROR_STOP=1 \
  < "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/favorite/rpc/internal/model/001_favorite_fact_outbox.sql"
docker exec ddl-favorite-pg psql -U admin -d go_favorite_sqlfirst_probe \
  -c "SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='favorite_fact_outbox'::regclass ORDER BY conname;"
```

⇒ **3 条约束**（`..._aggregate_version_check` / `..._favorite_id_aggregate_version_key` / `..._pkey`）⇒ ⇒ **sql-first 下 Go 侧**确实**有**那条 CHECK（名字不同、定义逐字相同）⇒ **§9.0 的结论由本轮实跑坐实，不是文档转述**。

### 9.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（G-9 `--check-source`） | 4 张表 ↔ 4 张表 | ✅ **0 差异** —— 仓里的 7 份 `.sql` 在**干净临时库** `rag_favorite_db_srccheck` 上原样重放后，与活库**逐项一致**（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器） | 0 |
| 2 | **列类型 / 长度** | **38 列 ↔ 38 列**（键集合一致 ✓） | ✅ **0 差异** —— 脚本原话「**逐字相同 38 列**」 | 0 |
| 3 | **可空性（NOT NULL）** | 38 列 | ✅ **0 差异** —— 脚本原话「（两侧完全一致 ✓）」 | 0 |
| 4 | **DEFAULT** | 38 列 | ✅ **0 差异** —— 脚本原话「（两侧完全一致 ✓）」⇒ **本域无 `DEFAULT CURRENT_TIMESTAMP` 残留**（`V7__align_favorite_outbox_time_columns_with_go.sql` 已把 outbox 的两个时间列对齐到 Go 的「无默认」） | 0 |
| 5 | **索引**（`pg_indexes`） | Go **16** / RAG **16** | ✅ **0 差异** —— 脚本原话「（两侧索引定义逐条一致 ✓）」＋ **2 处「语义等价（仅命名形态差）」不计失败**（见 §9.3③） | 0 |
| 6 | **唯一约束 / 主键**（`pg_constraint`） | Go **12** / RAG **15** | 🔴 **2 处实质差异**（均**仅 RAG 有** · `contype=c`）＋ **4 行逐条一致** ＋ **9 处「语义等价（仅命名形态差）」不计失败** | **2** |
| 7 | **触发器**（`pg_trigger`） | Go **2** / RAG **2** | ✅ **0 差异** —— 脚本原话「同名同表 2 个 ✓」（两侧触发器集合完全一致 ✓）⇒ ⚠️ **本维度有对象可比**（不是「无对象可比」） | 0 |
| 8 | **触发器形式**（`pg_get_triggerdef` → 结构化属性） | 两侧各 2，**可比对 2** | ✅ **0 差异** —— 「2 个可比对的触发器形式逐条一致 ✓」 | 0 |
| 8′ | **触发器函数体**（`pg_proc` → 结构化语义属性） | 两侧各 2，**可比对 2** | ✅ **0 差异** —— 「2 个可比对的函数属性逐条一致 ✓」 | 0 |
| | **合计** | | | **2** |

⇒ **本域 DDL 在 8 个被核维度（触发器拆三条 ⇒ 实为 **9** 条判据）上共 2 处机器残留。**

### 9.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 38 列逐字相同**

- 脚本原话：`DDL 列级对比  go_favorite_db=38 列  rag_favorite_db=38 列  （键集合一致 ✓）` + `逐字相同 38 列。`
- ⇒ 符合 AGENTS.md §0 的总体结论「类型/长度 0 差异」；⚠️ `favorite_item.target_id` 两侧都是 **`varchar(255)`**（**不是** `bigint`）⇒ RAG 侧 `V2:33` 的注释自记了这条（雪花 ID > 2^53 会溢出）⇒ **两侧同型**。

**② 可空性 / DEFAULT —— 两侧完全一致（0 处）**

- `favorite_item` 的 `target_revision` 两侧**都可空、无默认**（Go `data_model.go:25-26` 的注释是硬约束：「Nil means … do not backfill legacy rows」）⇒ RAG 侧 `V2:38` 照抄 ⇒ **一致**。
- ⚠️ `favorite_fact_outbox` 的 `created_at` / `updated_at` 两侧**可空、无默认**（Go 的 `autoCreateTime` 不建 DB 默认）⇒ RAG 侧靠 `V7__align_favorite_outbox_time_columns_with_go.sql` 对齐（**G-9 当初抓出的正是这一条**）⇒ ⇒ **本域 DEFAULT 0 差异**。

**③ 索引 / 约束的「语义等价（仅命名形态差）」—— 11 处（脚本原话「**不计失败**」）**

| # | 表 | Go 侧 | RAG 侧 | 备注 |
|---|---|---|---|---|
| 1 | `favorite_folder` | `idx_favorite_folder_folder_id`（unique 索引） | `uk_favorite_folder_folder_id`（unique 索引） | 列集合同为 `folder_id` ⇒ 等价 |
| 2 | `favorite_item` | `idx_favorite_item_favorite_id`（unique 索引） | `uk_favorite_item_favorite_id`（unique 索引） | 列集合同为 `favorite_id` ⇒ 等价 |
| 3 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_approval_ref_check` | `favorite_legacy_marker_approval_ref_nonempty` | `CHECK (approval_ref <> '')` ⇒ 表达式逐字相同 |
| 4 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_check` | `favorite_legacy_marker_revision_scope` | `CHECK (target_revision IS NULL OR (target_type='article' AND target_revision<>''))` ⇒ 表达式逐字相同 |
| 5 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_favorite_id_check` | `favorite_legacy_marker_favorite_id_positive` | `CHECK (favorite_id > 0)` |
| 6 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_folder_id_check` | `favorite_legacy_marker_folder_id_positive` | `CHECK (folder_id > 0)` |
| 7 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_source_snapshot_sha256_check` | `favorite_legacy_marker_snapshot_hash_format` | `CHECK (source_snapshot_sha256 ~ '^[a-f0-9]{64}$')` |
| 8 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_target_id_check` | `favorite_legacy_marker_target_id_nonempty` | `CHECK (target_id <> '')` |
| 9 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_target_type_check` | `favorite_legacy_marker_target_type_nonempty` | `CHECK (target_type <> '')` |
| 10 | `favorite_legacy_fact_marker` | `favorite_legacy_fact_marker_user_id_check` | `favorite_legacy_marker_user_id_positive` | `CHECK (user_id > 0)` |
| 11 | `favorite_fact_outbox` | `favorite_fact_outbox_favorite_id_aggregate_version_key`（**表约束** `contype=u`） | `uk_favorite_fact_version`（**唯一索引**，载体不同） | `UNIQUE (favorite_id, aggregate_version)` ⇒ 列集合与唯一性相同 ⇒ 等价 |

⇒ **11/11 全部落在已签字项 `DEV-FAV-RPC-006`（12 条）的范围内** ⇒ 该条把其中 3 条记在「索引」段、9 条记在「约束」段；本轮脚本按**约束载体**归类 ⇒ ⇒ **同一批对象、归类不同**（第 11 条在本轮记在「约束」段，因 RAG 侧是表约束）。
⇒ ⚠️ **不要重复质疑这 11 条**：它们是**已签字的纯标识符差异**（签字档原文：「纯标识符差异 ⇒ 约束的语义（唯一性、列集合、冲突行为、CHECK 表达式）完全相同」）。

**④ 触发器 —— 2 / 2 同名同表（**本域最硬的那条约束**）**

| 侧 | 触发器 | 表 | 函数 |
|---|---|---|---|
| Go | `favorite_fact_identity_immutable` | `favorite_fact_outbox` | `reject_favorite_fact_identity_change()` |
| RAG | `favorite_fact_identity_immutable` | `favorite_fact_outbox` | `reject_favorite_fact_identity_change()` |
| Go | `favorite_legacy_marker_immutable` | `favorite_legacy_fact_marker` | `reject_favorite_legacy_marker_change()` |
| RAG | `favorite_legacy_marker_immutable` | `favorite_legacy_fact_marker` | `reject_favorite_legacy_marker_change()` |

⇒ 脚本的结构化属性逐项一致（两条触发器各一行，**go 与 rag 逐字相同**）：`fn=… rettype=trigger args=<无> volatility=VOLATILE is_trigger=trigger lang=plpgsql`。
⇒ Go 出处：`service/favorite/rpc/internal/model/002_favorite_fact_delivery.sql:15`（`OR NEW.aggregate_version IS DISTINCT FROM OLD.aggregate_version`）· `004_favorite_legacy_fact_marker.sql:18+`；RAG 出处：`V5__add_favorite_fact_delivery.sql` · `V4__create_favorite_legacy_fact_marker.sql`。

**⑤ 源码 ↔ 活库 —— 0 差异（G-9 维度）**

- 目录扫描得到 **7 份**迁移脚本 ⇒ **清单由目录扫描产生**，不会落后于仓。
- 在**干净临时库** `rag_favorite_db_srccheck` 上**原样重放**（交给 PG 自己解析）⇒ 与活库逐项对比：**表集合 4 ↔ 4** · **列集合 0 差异** · **列属性 0 差异** · **可空性 0 差异** · **DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器）`
- ⇒ ⇒ 「迁移改了没人重跑」这一类漂移**在 `favorite` 域为零**（**且含 `V7`** —— 正是 G-9 当初抓出「写死清单漏了 V7」的那一份）。

### 9.4 残留（逐条 · 定级 · 签字状态）

> ⚠️ **一条差异 ≠ 一个缺陷** —— 本域 2 处机器残留，按根因归成 **2 条**，**两条都落在已签字范围内**。

**① `favorite_fact_outbox.favorite_fact_outbox_aggregate_version_range`（仅 RAG 有）**

| 项 | 值 |
|---|---|
| 机器报出的定义 | `CHECK ((aggregate_version = ANY (ARRAY[(1)::bigint, (2)::bigint])))` |
| RAG 出处 | `service/favorite/favorite-rpc/src/main/resources/db/migration/V3__create_favorite_fact_outbox.sql:42`（`CONSTRAINT favorite_fact_outbox_aggregate_version_range CHECK (aggregate_version IN (1, 2))`）· 另有 `V6__align_favorite_schema_with_go.sql:73-74` 的「对存量库补加」段 |
| Go 出处 | `service/favorite/rpc/internal/model/001_favorite_fact_outbox.sql:6`（`aggregate_version bigint NOT NULL CHECK (aggregate_version IN (1,2))`）⇒ **逐字同源**（RAG `V3:37` 的脚本头自记：「Go 侧逐字：CHECK (aggregate_version IN (1,2))」） |
| **为何 Go 活库没有** | **执行顺序（app-first）** —— `ddl-parity.sh` 先 AutoMigrate（`db.go:33` 建 `favorite_fact_outbox`）⇒ 补放 `001` 时 `CREATE TABLE IF NOT EXISTS` 变 no-op（实跑日志：`NOTICE: relation "favorite_fact_outbox" already exists, skipping`）⇒ 内联 CHECK 建不出来。⚠️ **真源文档指定的顺序是 sql-first**（`DELIVERY_ACCEPTANCE.md:7`）⇒ **本轮已在真 PG 上验出 sql-first 下的 Go 侧约束名 = `favorite_fact_outbox_aggregate_version_check`、定义逐字相同** ⇒ ⇒ **在权威顺序下这是「同一条约束的不同名字」** |
| **签字状态** | ✅ **已签字** —— `DEV-FAV-RPC-006`（12 条命名差，2026-10-25 用户 Jason）的第 1 条约束：原文「`favorite_fact_outbox_aggregate_version_check`↔`..._aggregate_version_range`」⇒ ⇒ **已登记 ⇒ 不重复质疑、不「修回去」** |
| ⚠️ **口径提示（登记给收口执行者）** | 该签字条的**前提**是「Go 侧有这条约束、只是名字不同」（**sql-first 口径**）。本轮机器跑的是 **app-first** ⇒ 同一对象**被报成「实质差异」** ⇒ ⇒ **不是新回归**，但**机器口径与签字档口径不一致** ⇒ ⇒ 登记给收口执行者（**签字档的表述要不要加一句「app-first 下 Go 侧无此约束」** ⇒ 属 `_cross` / 签字档 ⇒ **本 lane 不写**）。**决策不变**（保留现状） |
| **定级** | **D 类（经分析确认可不实现或不适用的事项）** —— 理由：① **已签字**（`DEV-FAV-RPC-006`）⇒ 按 §十属「经分析确认」⇒ **不修、不改回**；② 在**真源文档指定的顺序**下它**只是命名差**（定义逐字相同）⇒ **不构成 DDL 语义漂移**；③ 即便按 app-first 口径把 RAG 侧这条去掉理解，RAG 侧也只是**更严**（会拒绝 `aggregate_version ∉ {1,2}` 的写入）⇒ **不产生数据丢失 / 重复处理 / 状态错误** ⇒ **不是 A 类**（不阻断阶段 1） |

**② `favorite_item.favorite_item_target_revision_scope`（仅 RAG 有）**

| 项 | 值 |
|---|---|
| 机器报出的定义 | `CHECK (((target_revision IS NULL) OR (((target_type)::text = 'article'::text) AND ((target_revision)::text <> ''::text))))` |
| RAG 出处 | `service/favorite/favorite-rpc/src/main/resources/db/migration/V6__align_favorite_schema_with_go.sql:60-61`；脚本头 `:47-52` **自述「这是有意加强」**并给出依据 |
| Go 侧事实 | ① Go 的 **`favorite_item` 表本体没有任何 CHECK**；② 同语义的复合 CHECK 只存在于**另一张表** `favorite_legacy_fact_marker`（`service/favorite/rpc/internal/model/004_favorite_legacy_fact_marker.sql:15`：`CHECK (target_revision IS NULL OR (target_type = 'article' AND target_revision <> ''))`）⇒ 而那张表**只在迁移期**用（人工批准的 pre-Outbox 旧行凭证）⇒ ⇒ **日常写入路径的 `favorite_item` 在 Go 侧不受任何 DB 级约束**；③ Go 的**应用层**有同一条断言：`service/favorite/rpc/internal/model/favorite_fact.go:138`（`(item.TargetRevision != nil && (item.TargetType != "article" \|\| *item.TargetRevision == ""))` ⇒ `invalid favorite fact`） |
| **签字状态** | ✅ **已签字** —— `DEV-FAV-RPC-007`（2026-10-25，用户 Jason：「保留现状，登记为签字偏离」⇒ **不是缺陷**；改动需**重新征签**）⇒ ⇒ **不重复质疑、不「修回去」** |
| 签字档自记的「什么时候会变成真差异」 | 出现**第二个写入方**（人工脚本 / 另一个服务 / 数据修复工具）直接写 `favorite_item` 且**故意**写非法 `target_revision` ⇒ RAG 侧被拒（SQLSTATE 23514）、Go 侧不拒 ⇒ ⇒ **届时不需要改 RAG**（RAG 更安全），但**写入方需要知道 RAG 多这条约束** |
| **定级** | **D 类（经分析确认可不实现或不适用的事项）** —— 理由：① **已签字** ⇒ 不修；② **加强型差异**（RAG 更严）⇒ **不产生数据丢失 / 重复处理 / 状态错误** ⇒ **不是 A 类**；③ Go 侧**本来就没有**这条 DB 级约束 ⇒ RAG 的这条是**把 Go 的应用层断言下沉到 DB 层**（应用层断言两侧都有 ⇒ 正常写入路径**两侧都不可能触发**）⇒ ⇒ **可接受 RAG 更严** ⇒ 归 D；⚠️ **但保留签字档那条「第二写入方」触发条件**（本节把它复述在此，**不改签字档**） |

**残留汇总**

| # | 对象 | 机器口径 | 权威口径（真源指定顺序） | 签字 | 定级（§十） |
|---|---|---|---|---|---|
| ① | `favorite_fact_outbox_aggregate_version_range` | **实质差异**（仅 RAG 有） | ✅ **命名差**（Go 侧有同名不同名的同一 CHECK） | ✅ `DEV-FAV-RPC-006` | **D** |
| ② | `favorite_item_target_revision_scope` | **实质差异**（仅 RAG 有） | **实质差异**（Go 侧 `favorite_item` 本体无此约束） | ✅ `DEV-FAV-RPC-007` | **D** |
| | **合计** | **2** | | **2 条均已签字** | ⇒ **零未签字残留** |

### 9.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：`service/favorite/**` 的**代码与台账由本波次另一条 lane 在改**（AGENTS.md §5.3d 并发纪律）⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**（任务书允许的「**临时副本**」路线）：把 `check-ddl-static.py` 与该域的 `db/migration/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ `REPO_ROOT` 与仓内布局一致），**逐字节校验 sha256 相同**，再在**副本**上做变异。

**变异 ①：`favorite_item.favorite_id` 的列类型（`bigint` → `integer`）**

| 步骤 | 命令 | 结果 |
|---|---|---|
| 副本 sha256（变异前） | `shasum -a 256` 副本 vs 仓内 7 份 `.sql` | **7/7 逐字节相同**（`V1` = `fbb8b1ef664947976c5477f55c1943f15f8fa4908c8c5c14b694fedebfca6156` · `V2` = `5a50122facf623404bb731c6ac7eac4f9e079311931d899a7b0c9ebe5022f722` · `V3` = `674160fb2039e8f499b9b4dcef50ae44f4cca72d9c92ca79fd5bb7ec3ca74f09` · `V4` = `895f41e89c746769f1201acee3e4f6162ff0615e7f077a135a664a59f72e7c27` · `V5` = `343c1097b594e72ae38343dda4180b856ed424926f71aba5a08e549ab4905c31` · `V6` = `bf5fdd40a706336c383ad3ac189cc1c61deb932a5a0997c9110465e09cbef0ba` · `V7` = `8d839e896e1170be8e99055d3b3c2328ae4a9f8c26a58d4873fb6bc1d625b824`） |
| 基线 | `python3 tools/difftest/check-ddl-static.py --domain favorite --indexes --check-source`（临时树） | **EXIT=1**（**2 处** · 基线那 2 条）· 源码↔活库 = `✅ 一致` |
| 变异 | `V2__create_favorite_item.sql`：`    favorite_id bigint      NOT NULL,` → `    favorite_id integer     NOT NULL,`（改**一个列的类型**，命中目标行 **1** 处） | — |
| 变异后重跑 | 同上命令 | **EXIT=1**（**3 处** = 基线 2 ＋ **新 1**）· 源码↔活库 **转红**：<br>`🔴 列属性不同：favorite_item.favorite_id  源码=('integer', '')  活库=('bigint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 改回 `bigint` | — |
| 还原后重跑 | 同上命令 | **EXIT=1**（**2 处** · **回到基线**）· 源码↔活库 回到 `✅ 一致` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **7/7 仍逐字节相同**（`V2` 仍是 `5a50122f…2f722`）⇒ 逐字节回到变异前 |
| 副本清理 | `rm -rf` 临时树 | **已删除**（复查：目录不存在）⇒ **工作树里不留临时副本** |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`favorite_item.favorite_id`），**没有多红也没有漏红**（基线那 2 条仍在，总数从 2 → 3）⇒ 守门**不是假绿**；还原后**精确回到 EXIT=1 的基线计数（2 处）**⇒ 无残留。
⚠️ 本域基线**不是 0** ⇒ 「变异必红 ⇒ 还原必回基线」的**两端是 1(2 处) → 1(3 处) → 1(2 处)** ⇒ ⇒ **计数守恒**（这正是 §3.3 规则③「脚本批量改 N 处必须配覆盖全部 N 处的守恒断言」的本域形态）。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b"
git status --short -- service/     # ⇒ 空
git diff --stat -- service/        # ⇒ 0 行
```

⇒ `service/favorite/**` 与 `service/article/**`（同波次另一 lane 在改）**零改动**。

### 9.6 证据强度 · 效力边界 · 未能核到的部分

**证据强度 = E4（`docs/twoStage.md` §8.1「运行证据」）＋ L4（域台账分层：运行面 · 双侧活体）** —— 两侧各建一个**真 PG 16** 库（Go 侧跑真源自己的 `AutoMigrate`、RAG 侧跑仓里的真迁移脚本）⇒ 逐列 / 逐索引 / 逐约束 / 逐触发器 dump 后机器比对。
⚠️ 本节所有 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。

**效力边界（越界即失效）**

| # | 边界 | 对本域是否成立 |
|---|---|---|
| 1 | **机器口径（app-first）≠ 真源文档指定的权威口径（sql-first）** | 🔴 **成立** —— 见 §9.0 ⇒ ⇒ **「2 处残留」这个数字是 app-first 口径下的**；在 sql-first 口径下其中 1 处只是命名差 ⇒ ⇒ **不得把「2」读成「2 条真漂移」** |
| 2 | **`ddl-parity.sh` 的 Go 侧顺序 = app-first**（AutoMigrate → 补放 `001..004`） | 🔴 **成立** —— 真源 `db.go:33` 的 AutoMigrate 先建 `favorite_fact_outbox` ⇒ `001` 的 `CREATE TABLE IF NOT EXISTS` 变 no-op（实跑日志逐字佐证） |
| 3 | **`--check-source` 的重放引擎 ≠ 生产引擎** —— 重放用 **psql**；本域生产用 **Flyway 引擎**（`application.yml:109`） | ⚠️ **需人工核（本轮已核）** —— 7 份 `.sql` 里含 `DO $$ ... $$` 守卫块（`V6` 段）⇒ **psql 与 Flyway 都会把 `$$` 块交给 PG 自己解析** ⇒ 两者看到的语句序列相同 ⇒ **对本域不成立** |
| 4 | **迁移清单 = 生产清单** —— `discover_rag_sql` 目录扫描的 7 份，与生产 Flyway `locations` 指向的目录内容**逐条一致**（命令 ③ 与 ① 的执行日志互证） | ✅ **成立** |
| 5 | **Flyway 账本表** —— 生产库会有 `flyway_schema_history_favorite`（AGENTS.md §6.3 问题 4），但**对拍环境直接用 psql 放 `.sql`** ⇒ 两个库里**都没有**账本表 | ✅ **本轮无干扰**；工具靠 `exclude_tables=("flyway_schema_history",)` 前缀匹配排除（**已配置**） |
| 6 | **`baseline-version: 0`**（`application.yml:108`） | ⚠️ **本轮未核** —— 对拍用**全新空库** ⇒ baseline 不触发 ⇒ V1 正常执行。⚠️ **这正是该配置注释里点名的陷阱**（`application.yml:89-107`）⇒ **登记为「未核」** |
| 7 | **触发器函数体的语义**机器判不了 ⇒ 走人工逐条 | ⚠️ **本轮未逐行核** —— 两侧函数**同名**、结构化属性逐项一致 ⇒ **形式面无差异来源**；但**函数体到底拒绝什么**（Go 的 `002:15` 的 `IS DISTINCT FROM` 与 RAG 的对应实现是否逐字等价）**未逐行核** ⇒ **登记为「未核」** |
| 8 | **`pg_constraint` 只比 `contype` + 定义文本，不解析外键的引用目标** | ✅ **不适用**（本域两侧 `contype='f'` 各 **0 行**，见 §9.6 的人工补查表） |
| 9 | **分区 / 存储过程 / 触发器函数** —— 脚本不查 | ✅ **不适用**（7 份 `.sql` 的**可执行部分**无 `PARTITION` / `PROCEDURE` 关键字 ⇒ 已人工核；两侧 `pg_partitioned_table` / `prokind='p'` 均 **0**） |

**人工补查（脚本**不查**的维度 —— 结论强度 = **L4 + 人工补查**，**非脚本原生结论**）**

| 维度 | Go 侧 `go_favorite_db` | RAG 侧 `rag_favorite_db` | 判定 |
|---|---|---|---|
| 外键（`pg_constraint` `contype='f'`） | **0 行** | **0 行** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 分区表（`relkind='p'` / `pg_partitioned_table`） | **0** / **0** | **0** / **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 存储过程（`prokind='p'`） | **0** | **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 函数（`prokind='f'`，**非触发器**） | **0** | **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 触发器函数（被 `pg_trigger` 引用） | **2**：`reject_favorite_fact_identity_change` · `reject_favorite_legacy_marker_change` | **2**：**同名两个** | ✅ **同名 ⇒ 人工闭合** |

**未能核到的部分（明确写「未核」+ 原因，不含糊）**

| # | 未核项 | 原因 | 影响 |
|---|---|---|---|
| 1 | **共享库拓扑下的 Flyway `baseline-version: 0` 行为** | 对拍用**全新空库** ⇒ baseline 不触发 ⇒ 该配置**未被本节验证** | ⚠️ 该维度的守门在域侧真 PG + 真 Flyway 引擎的用例里（`application.yml:89-107` 明写这个陷阱）⇒ 本节**不重复核**，只登记边界 |
| 2 | **触发器函数体的逐行语义等价** | 机器只比结构化属性（函数名 / 返回类型 / 参数 / volatility / 语言）⇒ **函数体文本未比** | ⇒ 两侧函数**同名 + 同属性** ⇒ **形式面无差异来源**；但「函数体到底拒绝哪些列变更」**未逐行核** ⇒ ⇒ **需人工逐行核 `002_favorite_fact_delivery.sql` 与 RAG `V5` 的函数体** |
| 3 | **sql-first 口径下的**完整**双侧对照**（不只是 `001` 那一条 CHECK） | 本轮只把 Go 的 `001` 单独放进空库验了 `favorite_fact_outbox` 的约束集 ⇒ **没有**在 sql-first 下把 Go 侧 4 张表整体重建后再跑一遍七项对照 | ⇒ §9.4 ① 的结论**已坐实**（那条 CHECK 的有无与名字）；但「sql-first 下是否还有别的差异」**未核** ⇒ ⇒ **域内自有探针 `tools/ddl-parity-probe/favorite/` 就是干这件事的** ⇒ ⇒ **本节建议以该探针为 sql-first 口径的权威证据源**（本轮**未跑**它 ⇒ 登记为「未跑」） |
| 4 | **索引是否被真实查询计划选中** | 需 `EXPLAIN` + 真实数据量 ⇒ 本轮**未核** | 本域 2 个 RAG 独有索引是**纯性能** ⇒ 实际代价未量化 |
| 5 | **「运维到底按哪个顺序跑 `001..004`」** | 属**部署面**，不是 schema ⇒ schema 对拍**看不出** | ⚠️ **这正是 §9.0 口径差的现实来源** ⇒ ⇒ 本域的 DDL 一致性**依赖运维纪律**（真源文档 `DELIVERY_ACCEPTANCE.md:7` 规定了 sql-first）⇒ ⇒ **登记为「未核」（部署面）** |

### 9.7 与其它台账的关系（指针，不改别的文件）

| 台账 | 本节与它的关系 |
|---|---|
| [`docs/Intentional_Deviations/favorite/rpc.md`](../../Intentional_Deviations/favorite/rpc.md) | **本节 2 处机器残留在此有归属**：`DEV-FAV-RPC-006`（12 条命名差 · 2026-10-25 已签字）· `DEV-FAV-RPC-007`（`favorite_item` 多一道 CHECK · 2026-10-25 已签字）⇒ ⇒ **本节不新增签字条目**，只把机器结论落到域台账；⚠️ §9.4 ① 的「口径提示」**只在此登记**（签字档属 `_cross` 口径面 ⇒ **本 lane 不写**） |
| [`docs/DRIFT/favorite/rpc.md`](rpc.md) · [`api.md`](api.md) · [`common.md`](common.md) · [`proto.md`](proto.md) | 本域**组件级**台账；本节是 **DDL 这一维度**的域级落账 ⇒ 与那几份台账的其它维度结论**互不冲突** |
| [`tools/ddl-parity-probe/favorite/`](../../../tools/ddl-parity-probe/favorite/) | 🔴 **域内自有探针 · 判定基准 = sql-first**（README「两个执行顺序都跑」节 + `run.sh:315-325`）⇒ ⇒ **它与本节机器口径（app-first）是同域的两个 Go 侧口径** ⇒ ⇒ **两者结论「不矛盾」**（差在执行顺序）⇒ ⇒ **本节的 §9.0 就是把这两个口径显式对齐**（本轮**未跑**该探针 ⇒ 登记为「未跑」） |
| [`docs/PhaseGoal-judge.md` §七 **G-8 / G-9**](../../PhaseGoal-judge.md) | 主线结论 —— 本节是 `favorite` 域的**域级落账**；⚠️ 该文件对 `favorite` 域记的残留是「**favorite 2**（额外 `CHECK` ⇒ **RAG 更严**）」⇒ ⇒ **与本节一致**（2 处）⇒ ⇒ **但本节补充了它没写的成因**（① 是**执行顺序口径差**、两条**都已签字**）⇒ ⇒ 该文件**主线写** ⇒ **本 lane 只读** |
| [`docs/DRIFT/_cross/ddl-type-divergences.md`](../../DRIFT/_cross/ddl-type-divergences.md) | 🔴 **属 `_cross` ⇒ 本 lane 无写权** ⇒ 只在此登记：该文件对 `favorite` 域的「类型/长度 0 差异」**与本节一致**（38 列逐字相同）⇒ ⇒ **无需更正** |

---

## 📌 追加 · 🟡 **共享层轴后果：rpc 域首次成为 servlet web app（2026-10-30 · G3 第三轮）**

> 本节只记**共享层轴**的后果（域内落地的细节在同域的 `rpc.md`）。🟡 = **待共享层裁定**，**不是**缺陷。

**🔴 轴事实一：共享层那批 `@ConditionalOnWebApplication(SERVLET)` 的自动配置，在 rpc 域**首次生效**

- 触发条件：本轮给 `favorite-rpc` 补了 `spring-boot-starter-web`（Actuator 的 servlet 载体）⇒ ⇒ **rpc 进程首次成为 servlet web app** ⇒ ⇒ 共享层里带 servlet 条件的**自动配置**（`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 列的那几个）**第一次**会在 rpc 容器里装载。
- 实测命中（`favorite` 域）：`com.rag.common.http.GoStyleJsonContentTypeAdvice`（`@AutoConfiguration` ＋ `@ConditionalOnWebApplication(SERVLET)` ＋ `@RestControllerAdvice`）⇒ 它**只改 JSON 响应的 `Content-Type`**（`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:187-206`）⇒ ⇒ `/actuator/prometheus` 是 **text/plain** ⇒ **不受影响** ⇒ 实测抓取 **HTTP 200** ＋ 指标族正常；⇒ 但 `/actuator/health`（JSON）会拿到 `application/json;charset=utf-8`。
- ⇒ ⇒ 这条**正是**共享层正在裁的那个议题（「rpc 域观测端口要不要挂 Go 的 rest 中间件」）的**落点** ⇒ ⇒ **归 G4 裁定**，域侧**只登记、不自决**。

**🔴 轴事实二：`rag.side` 这个属性共享层**当前还没有消费点**

- 域侧本轮已在三份 rpc 的生产 yml 里写 `rag.side: rpc`（`favorite` 域的落点是 `service/favorite/favorite-rpc/src/main/resources/application.yml`）⇒ 那是**为 G4 正在扩的那道门预留的对侧**（门若写成 `matchIfMissing=true` ⇒ **不写 = 默认 api 侧** ⇒ rpc 域会挂上 api 侧的 web Bean）。
- ⚠️ **如实标注**：**2026-10-30 实测** `grep -rn "rag.side" service/common/src/main/java/` **零命中** ⇒ ⇒ 共享层**当前还没有**消费这个属性的条件 ⇒ ⇒ 域侧这三行**现在还没有消费点** ⇒ ⇒ 门落地前**不生效**、落地后**必须已在位**。
- ⇒ ⇒ 域侧**运行期**断言已就位（`favoriteRpcSharedLayerWebBeanGateTest`，3 例：属性在位 ＋ **正向对照**（证明 servlet 前提成立 ⇒ 断言非空转）＋ api 侧那 5 个 web Bean **不在**容器里）⇒ ⇒ 它现在证明的是「那 5 个 `@Component` 中间件因为 rpc **不扫** `com.rag.common` 而不在容器里」⇒ ⇒ 等 G4 把门扩到全部 api 侧中间件后，**自动升级**为「`rag.side` 真的挡住了门」的运行期证据。

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


### lane W12

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| F-6 | B（阶段一竞争力补强项 · 落点在各域自己的 rpc 模块 ⇒ 域内可自修） | P1 | 否 | Go `service/favorite/rpc/etc/favorite.yaml:13`（`Password: "${POSTGRES_PASSWORD}"` ⇒ **yaml 里不带兜底值**）＋ `go-zero@v1.10.2/core/conf/config.go:63`（`os.ExpandEnv(string(content))` ⇒ **消费点**：缺变量时展开成**空串** ⇒ Go 侧拿到空口令，**没有任何默认值**）vs RAG `service/favorite/favorite-rpc/src/main/resources/application.yml:73`（`password: ${POSTGRES_PASSWORD:password}` ⇒ Spring 占位符**自带明文默认值 `password`**）＋ `:221`（`token: ${FAVORITE_AUTHORITY_TOKEN:}` ⇒ 空默认）⇒ **差异仍成立**：缺变量时两侧拿到的口令**不同**（Go = 空串 / RAG = `password`）⇒ 部署凭据链在 RAG 侧留了公开兜底值。⚠️ 台账「归属 G4」已被 `docs/PhaseGoal-judge.md:265` 订正为**域内**（落点在各域自己的 rpc 模块 ⇒ 域侧可自修）；⚠️ 同处另有两处订正：① `${*_AUTHORITY_TOKEN:}` 这个**通配形态**全仓不存在（存在的是各域具体形态，如 `${FAVORITE_AUTHORITY_TOKEN:}`）；② `${MINIO_SECRET_KEY:minioadmin}` 落在 **article-rpc**，**不在 favorite 域** ⇒ ⇒ **剩余实现面 = 8 个 rpc 模块共 10 处部署凭据默认值**（已登记 `S1-USERS-002`，🟡 B 类 · 按 §1.1 ⑥ 第 4 条不计入）⇒ **不阻断阶段一** | 2026-10-30 |
| G4-14 | C（阶段二 · U9 部署形态待拍板（**rpc 侧 2026-10-30 已销账**）＋ U7 双侧活体对拍取证补齐） | P2 | 否 | Go `service/favorite/rpc/etc/favorite.yaml:23-30`（`DevServer: {Enabled: true, Host: 0.0.0.0, Port: 16062, MetricsPath: /metrics, HealthPath: /healthz, EnableMetrics: true, EnablePprof: true}`）＋ `service/favorite/api/etc/favorite.yaml:25-32`（api 侧 DevServer `Port: 16063`）＋ `go-zero@v1.10.2/core/service/serviceconf.go:77`（`devserver.StartAgent(sc.DevServer)` ⇒ **消费点**：Go 的 rpc 与 api **真的会起**那个 DevServer HTTP 面）vs RAG `service/favorite/favorite-rpc/src/main/resources/application.yml:177-178`（`server.port: ${FAVORITE_RPC_MANAGEMENT_PORT:16062}` ＋ `:317-325` Actuator 暴露 ⇒ **rpc 侧已有** 16062 那个面）＋ `service/favorite/favorite-api/src/main/resources/application.yml`（**仍无** 16063 那个面）⇒ **差异部分成立**：Go 生产形态有 16062 / 16063 两个独立 HTTP 面（`/metrics` ＋ `/healthz` ＋ pprof），RAG favorite 域 **rpc 侧已闭（2026-10-30 载体落地）· 仅剩 api 侧 16063**。⚠️ **本条是复合项**：U9 = **部署形态决策**（要不要 RAG 侧也开这个面 ⇒ 属决策不是缺陷）⇒ ⇒ **rpc 侧已由 Jason 2026-10-30 拍板批准并落地 ⇒ U9 的 rpc 侧已销账**；U7 = **双侧活体对拍未做**（其「Go 侧 etcd/Kafka 拉不起来」的受阻前提，按台账 `docs/DRIFT/favorite/vs-common.md:121` 自记已于 2026-10-29 部分解除 ⇒ U7 从「环境受限」降为「可做但未做」）⇒ ⇒ **剩余实现面 = U9 的 api 侧（16063 · 域内自修）＋ U7 取证补齐**（U7 归共享层收口窗口）⇒ **不阻断阶段一** | 2026-10-30 |
| G4-15 | C（阶段二 · 落点 100% 在共享层 `service/common` ⇒ 域内只读 ⇒ 归收口执行者） | P2 | 否 | Go `go-zero@v1.10.2/rest/router/patrouter.go:79-82`（`w.Header().Set(allowHeader, allows)` ＋ `w.WriteHeader(http.StatusMethodNotAllowed)` ⇒ **裸状态码**：既无 body 也无 `Content-Type`）vs RAG `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:138-144`（`methodNotAllowed` ⇒ 刻意返回 `ResponseEntity<Void>`；`:141-143` 的 javadoc 自称「容器不会补 Content-Type」）＋ `tools/api-full-parity-probe/favorite/expected.txt:71-72`（`r02/r03` 两例的 Go 侧冻结值 = `SAME_BODY` 档且 `ct` 字段为空（即**无** `Content-Type`）⇒ `SAME_BODY` 档的定义就是「**Content-Type 允许不同**」⇒ 探针把这一项**单列为已知跨域漂移**）＋ `tools/api-full-parity-probe/favorite/README.md:128-134`（「一条跨域漂移（405 多一个 Content-Type）… 实测被证伪：Spring 仍会选出一个 MediaType ⇒ RAG 多一项 `Content-Type: application/json;charset=utf-8`」）⇒ **差异仍成立**：两侧 405 的**状态码与 body 全等**，RAG 仅多一项 `Content-Type` ⇒ 响应头字节级不等价。⚠️ `service/common/src/test/java/com/rag/common/http/GoStyleRouterErrorResponsesContractGateTest.java:94-99` 只断言 **ResponseEntity 对象上**的 `Content-Type` 为 null，**证不到容器实际写出的响应头** ⇒ 不能当「已修」的证据（AGENTS.md §3.3 同型：断言对象存在 ≠ 机制生效）⇒ ⇒ **剩余实现面 = 共享层让 405 出口不带 `Content-Type`** ⇒ **不阻断阶段一** | 2026-10-30 |

### lane W27

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| `F-1` | B（阶段一竞争力补强项：残留 = 探针自述的未验分支「下游 article-rpc 在场的 create 成功路径」⇒ 属验证闭环补齐，非行为漂移） | P1 | 否（不阻断阶段一） | Go `service/favorite/rpc/favorite.go:42-55`（`zrpc.MustNewServer` + `favoritepb.RegisterFavoriteServiceServer` + `s.Start()` ⇒ 真源 rpc 侧真进程入口）＋ `service/favorite/rpc/internal/server/favorite_service_server.go:26-29`（7 个 rpc 的真入口）vs RAG `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/FavoriteRpcApplication.java:111-112`（Spring Boot main）＋ `tools/api-full-parity-probe/favorite-rpc/run.sh:305`（`go build ./service/favorite/rpc` ⇒ 起 Go 侧真栈）＋ `:433`（`java -jar favorite-rpc-0.0.1-SNAPSHOT.jar` ⇒ 起 RAG 侧真栈）＋ `:445`（三次起不来即 die）＋ `:454`（断言已注册进 Nacos）＋ `tools/api-full-parity-probe/favorite/run.sh:338-339` 与 `:376` `:388`（真 jar 起 favorite-rpc 与 favorite-api）⇒ **差异已消失**：台账记的「Go 侧 etcd／Kafka 仍不可拉起 ⇒ 环境受限 ⇒ rpc 层无双侧活体对拍」已被 2026-10-29 R2 lane 的**版本控制内**产物推翻（`docs/DRIFT/favorite/rpc.md:4058`「favorite-rpc 的 rpc 层双侧活体对拍已落地」＋ `:4113-4114` 25 条 FROZEN 用例 wire 层逐字节一致且 EXIT=0 ＋ `:4124-4125` L4 证据强度 ＋ `:4167`「以本节为准」）⇒ 本条记的那条缺口已不存在；⚠️ 残留只余 `docs/DRIFT/favorite/rpc.md:4136`（下游 article-rpc 在场的 create 成功路径「未验」）与 `:4139`（NULL title／cover 显式排除）⇒ 归 B（不阻断阶段一） | 2026-10-30 |
| `F-2` | D（差异已消失 ⇒ 无待实现内容） | P1 | 否（不阻断阶段一） | Go `service/favorite/rpc/internal/model/db.go:20-41`（真源建表路径 `InitDB` = `gorm.Open` + `AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{})` + `migrateFavoriteTargetIDToString` ⇒ ddl 探针 Go 侧跑的就是它）＋ `service/favorite/rpc/favorite.go:26-56`（真源 rpc 组件 `main()` ⇒ 真进程）vs RAG `tools/ddl-parity-probe/favorite/run.sh:185`（RAG 侧逐个 `psql_q -d ragdb -f -` 执行 Flyway 脚本 ⇒ **全程不启 Java 进程** ⇒ 台账这一半断言成立）＋ `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/FavoriteRpcApplication.java:111-112`（Spring Boot main ＝ 真正该被启动的那个组件）＋ `tools/api-full-parity-probe/favorite/run.sh:338-339`（两个 fat jar 路径）＋ `:376` `:388`（真 `java -jar` 起 favorite-rpc 与 favorite-api）＋ `tools/api-full-parity-probe/favorite-rpc/run.sh:433` `:445` `:454`（真 fat jar + 端口监听 + 真 Nacos 注册）⇒ **差异已消失**：台账记的「第 0 步未完成（ddl 探针不启 Java 进程 ⇒ 构不成『组件真的启动过』的证据）」这一缺口，已由 2026-10-29 R2 lane 的 `tools/api-full-parity-probe/favorite` 与 `tools/api-full-parity-probe/favorite-rpc`（真 Spring Boot 进程 ＋ 真 Flyway ＋ 真 PG ＋ 真 Redis ＋ 真 Nacos 注册）闭合 ⇒ ddl 探针仍只跑 psql 是它的**设计**（它证明「迁移脚本产出的 DDL 与 Go AutoMigrate 一致」，不证明启动）⇒ 本条无剩余待实现项 | 2026-10-30 |
| `F-4` | D（差异已消失 ⇒ 无待实现内容） | P0 | 否（不阻断阶段一） | Go `service/favorite/api/internal/config/config.go:11-13`（`UserAuth.AccessSecret` 字段 ＝ 验签密钥的消费点）＋ `service/favorite/api/etc/favorite.yaml:10-12`（`AccessSecret: ae0536f9-6450-4606-8e13-5a19ed505da0` ⇒ 真源把密钥写成仓内字面量 ⇒ P0 #8 的根因在仓里）vs RAG `docker-compose.yaml:182`（`USER_JWT_SECRET: ${USER_JWT_SECRET:?set USER_JWT_SECRET in .env ...}` ⇒ compose 级 fail-closed，理由见 `:177-181`）＋ `docker-compose.yaml:227`（user-api 侧同一 `:?`）＋ `docker-compose.yaml:15`（`POSTGRES_PASSWORD` 同一 `:?`）＋ `.env.example:43`（`FAVORITE_JWT_SECRET`）＋ `.env.example:63`（`FAVORITE_AUTHORITY_TOKEN`）＋ `README.md:28`（`cp .env.example .env`）＋ `README.md:29`（点名 `FAVORITE_JWT_SECRET`）＋ `README.md:75`（`.env.example` 清单行）＋ `README.md:123`（「见 .env.example」）＋ `README.md:209`（compose `:?` 排障说明）＋ 域侧已修锚点 `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/config/FavoriteApiProperties.java:109-130`（`@PostConstruct verifyAccessSecret()`）＋ `service/favorite/favorite-api/src/main/resources/application.yml:152`（明文默认值已去掉）⇒ **差异已消失**：台账记的「共享面半边（根级 compose 的 `:?` ＋ 根 README 的 `.env.example`）仍开」已被 2026-10-29 两笔 commit 闭合（`0bcd3536`「README 重写 + .env.example + compose fail-closed」＋ `0b707b39`「修 compose 三处新机器必然起不来的缺陷」；三份根级文件均已被 `git ls-files` 跟踪）⇒ ⚠️ favorite 域**不在**根级 compose 的服务清单里（该 compose 只有 postgres／redis／nacos／jaeger／prometheus／user-rpc／user-api）⇒ 对 favorite 而言「compose `:?`」这一半本就是 N/A，落点实为 `.env.example` 的必填清单（已列）⇒ 本条无剩余待实现项 | 2026-10-30 |
| `G4-4` | D（差异已消失 ⇒ 无待实现内容） | P2 | 否（不阻断阶段一） | Go `service/favorite/api/internal/handler/favorite/get_favorites_by_folder_handler.go:18-25`（handler **自己捕获** `httpx.Parse` 的错 ⇒ `httpx.OkJsonCtx` + `{Code: favoritecommon.ErrorInvalidParam}` ⇒ **HTTP 200 + {code:5003, msg:"参数错误", data:null}** ⇒ 与「有没有注册 `httpx.SetErrorHandler`」**无关** ⇒ 轴 ② 的旧判据不成立）vs RAG `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:47`（轴 ② 第一判据已改成「Go 侧 handler 是否自己捕获 `httpx.Parse` 的错」）＋ `:61-64`（新增「域自定义 ⇒ 共享层不装 ⇒ OFF」一行，favorite = 200 + {code:5003,…}）＋ `:215-224`（`SEMANTIC_REACHABILITY` 里 favorite 的语义模式 = **`Mode.OFF`**，不是台账记的 `RAW`）＋ `:254-270`（原「favorite 刻意偏离 = raw」的登记已删除，并写明三个前提为何全错）＋ `tools/difftest/check-tomcat-limits.py:155-157`（旋钮期望表改为从共享层真源派生 ⇒ 不再持有第二份判据 ⇒ 「两份文件互相打架」的成因已除）＋ `:163-165`（`FORBIDDEN_FORM_LIMIT_MODE_MODULES` 保留「零触碰」口径，并注明「语义答案」与「边界口径」是两件不同的事）⇒ **差异已消失**：台账 G4-4 的两半（① 轴 ② 形状判据错 ② 共享层两份文件自相矛盾）分别由 commit `5a954bb1`（判据补「域自捕获」第一判据 ⇒ favorite 语义答案 = OFF）与 `d4e47455`（旋钮期望表改为从共享层真源派生）落码 ⇒ ⚠️ 台账提到的「边界口径」残留（Tomcat 总值数 vs Go 非空值数、段数翻转点差一格）**不属本条**，已由 `F-3` 与 `DRIFT-HOTAPI-030` 承载 ⇒ 本条无剩余待实现项 | 2026-10-30 |
| `L-4` | D（差异已消失 ⇒ 无待实现内容） | P2 | 否（不阻断阶段一） | Go `service/favorite/api/internal/logic/favorite/create_favorite_folder_logic.go:33`（`otel.Tracer("favorite-api").Start(l.ctx, "FavoriteAPI.CreateFavoriteFolder")`）＋ `:42-45`（`biz.user_id` 与 `biz.folder_name`）＋ `service/favorite/rpc/internal/logic/create_favorite_folder_logic.go:43`（`FavoriteRPC.CreateFavoriteFolder`）＋ `:46-49`（`biz.user_id` 与 `biz.folder_name`）＋ `service/favorite/rpc/internal/logic/helper.go:93` `:96`（`Dependency.User.GetUser` ＋ `biz.user_id`）＋ `service/favorite/rpc/internal/logic/helper.go:127` `:131`（`Dependency.Article.GetArticle` ＋ `biz.article_id`）⇒ 真源共 16 个手写 span ＋ 7 个 `biz.*` 属性 key vs RAG `service/favorite/favorite-common/src/main/java/com/rag/favorite/favorite/common/span/FavoriteSpans.java:163`（`TRACER_NAME_API`）＋ `:172`（`TRACER_NAME_RPC`）＋ `:177-195`（api 侧 7 个 span 名）＋ `:200-218`（rpc 侧 7 个 span 名）＋ `:223` `:226`（2 个 `Dependency.*` span 名）＋ `:231-258`（7 个 `biz.*` 属性 key）＋ 调用点 `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/service/FavoriteCreateFolderService.java:44-52`（`tracedApi` ＋ 两个 `setAttribute`）＋ `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteDomainService.java:139` `:244` `:404` `:488` `:580` `:685` `:742`（rpc 侧 7 处 `tracedRpc`）＋ `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteUserGuard.java:90-92`（`Dependency.User.GetUser`）＋ `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteArticleSnapshotResolver.java:140-141`（`Dependency.Article.GetArticle`）⇒ **差异已消失**：台账 §3 的 L-4 行记的「域侧业务 span 全缺 ⇒ favorite 遥测业务维度全空」已落码 —— 16／16 span 与 7 个 `biz.*` 属性全部在位（api 侧 7 个 Service 逐个 `tracedApi` ＋ rpc 侧 `FavoriteDomainService` 7 处 `tracedRpc` ＋ 2 处 `tracedDependency`）⇒ 本条无剩余待实现项 | 2026-10-30 |

### lane W4

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| E68 | D | P3 | 否 | Go `service/favorite/rpc/internal/logic/delete_favorite_folder_logic.go:96`（该文件**只有 96 行** ⇒ 台账里写成 `delete_favorite_folder_logic.go:336-378` 的锚点**行号在范围内但指错文件**）＋ `service/favorite/rpc/internal/model/favorite_fact.go:336`（`func (m *FavoriteModel) DeleteFolderCascade(ctx context.Context, folderID, userID int64) error {` ⇒ 符号**真实**所在）vs RAG `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/service/FavoriteFactWriteService.java:187`（javadoc「🔴 对应 Go {@code DeleteFolderCascade}（favorite_fact.go:336-378）」⇒ 台账那个错锚点 `delete_favorite_folder_logic.go:336-378` 的**出处就在这一行** —— RAG 侧写对了文件、只有台账抄错）＋ `:212`（`public void deleteFolderCascade(long folderId, long userId) {` ⇒ RAG 侧实现的真实行）＋ `tools/check-ledger-selfcheck.py:1250`（「🔴 **为什么要有这一层**（favorite 域第二十二轮实测带出 · 2026-10-26）」⇒ 第 4 层「唯一声明」补判据已落）＋ `tools/check-ledger-selfcheck.py:26-28`（判据原文：「台账说这个锚点处是符号 X；X 在真源里**唯一声明**过；但 X **不在**这个锚点文件里」）＋ `tools/check-ledger-selfcheck.py:1350`（「符号必须**在全真源里唯一声明**（否则『声明处唯一』这条判据会把它挡掉）」）⇒ **差异已消失**（台账 `docs/DRIFT/global/_README.md:433` 记的「🔴 第 4 层只能判行号越界 ⇒ 判不出『行号合法但指错文件』⇒ 这类锚点的证据是假的」已被**第四十七轮**闭合：判据收紧四道 + 变异自证（正例必须被抓到 + 阴性对照必须不报）⇒ favorite 域 20 个 Go 锚点逐条回源核对 = 18 正确 + 2 条错锚点**已订正**）⇒ **经分析确认不需要再实现** ⇒ 无落码动作（⚠️ 台账 `docs/DRIFT/global/_README.md:433` 末尾仍记「🟡 **仍开着**：新判据只在 `LEDGER_FILES` 已配置的域生效（task / users / users-admin）⇒ favorite 域的 12 份台账不在配置里」—— 但该脚本 `tools/check-ledger-selfcheck.py:57` 的 `LEDGER_FILES` 确实**不含** favorite ⇒ **favorite 域跑这条新判据仍需 favorite owner 自行加配置**，该残留属**域侧配置动作**而非本条判据本身失效） | 2026-10-30 |
| F-5 | C | P2 | 否 | Go `go-zero@v1.10.2/rest/router/patrouter.go:79-82`（`w.Header().Set(allowHeader, allows)` + `w.WriteHeader(http.StatusMethodNotAllowed)` ⇒ **裸状态码**：既不 `fmt.Fprintln` 也不 `http.Error` ⇒ **无 body、无 Content-Type**）vs RAG `service/common/src/main/java/com/rag/common/http/GoStyleRouterErrorResponses.java:138`（`public static ResponseEntity<Void> methodNotAllowed(java.util.Collection<String> supportedMethods) {` ⇒ 返回 `ResponseEntity<Void>`、body=null）＋ `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/advice/GlobalResponseAdvice.java:81`（`response.getHeaders().setContentType(GO_JSON_CONTENT_TYPE);` ⇒ **在 `body == null` 判断之前**就写 Content-Type ⇒ **多一项 `Content-Type: application/json;charset=utf-8`**）⇒ **差异仍成立**（405 的 status 与 body 两侧全等，**仅多一个 Content-Type** ⇒ 落点 = **favorite 域的 `GlobalResponseAdvice` 写序** + 共享层 `GoStyleRouterErrorResponses` 的形状约定；台账 `docs/DRIFT/favorite/vs-common.md:227` 记的「🔴 仍开」与 `tools/api-full-parity-probe/favorite/expected.txt:71`（`r02_method_not_allowed_post SAME_BODY status=405 · ct=- · body= · biz=-`）一致 ⇒ 两侧 405 归在 **SAME_BODY** 档（status+body 全等、Content-Type 允许不同））⇒ 修法 = 把 `setContentType` 移到 `body == null` 早退**之后**（与 `hot`/`follow`/`message`/`like` 四域已有的同款写法一致：`service/follow/follow-api/src/main/java/com/rag/follow/follow/api/advice/GlobalResponseAdvice.java:66` 的「body 为 null 或是 Response 实例时直接 return body」在前）⇒ 归 **C**（阶段二共享层/域侧工程化统一收口，**不阻断**阶段一准入；影响面 = 10 个 api 域的 405 形状） | 2026-10-30 |
| G4-13 | C | P2 | 否 | Go `go-zero@v1.10.2/rest/httpx/util.go:22-65`（`GetFormValues`：**非空值计数** + `r.ParseForm()` 错误传播）＋ `go-zero@v1.10.2/rest/httpx/requests.go:50-68`（`httpx.Parse` 对**每个请求**都过一遍）＋ Go 1.25.11 `src/net/url/url.go:1019-1022`（**段数 → 解析错误** 的判据顺序）vs RAG `service/favorite/favorite-api/src/main/java/com/rag/favorite/favorite/api/interceptor/FavoriteGoQueryLimitInterceptor.java:122`（`String violation = GoStyleFormValues.goParseViolation(request.getQueryString());` ⇒ **只读调用**共享层判据）＋ `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:257`（`public static String goParseViolation(String query) {` ⇒ 段数 → 解析错误 → 非空值计数，**次序与 Go 一致**）⇒ **差异仍成立（不是 Go↔RAG 漂移，而是「跨域承重」状态变更）**：favorite 域的可观察行为（2049 个非空值 ⇒ 200+5003 · `%zz` ⇒ 200+5003 · 10001 段 ⇒ 200+5003 · 分号 ⇒ 200+5003）**已与 Go 一致**；残留动作 = **共享层那个类的语义变成跨域承重** ⇒ G4 若要动 `GoStyleFormValues` 的规则/次序**必须同步通知 favorite 域**（域侧守门 `FavoriteApiRealTomcatGateTest` + `FavoriteGoQueryLimitInterceptorTest` 会红 ⇒ 可当回归网）⇒ 归 **C**（阶段二共享层工程化时加「消费者清单」到该类 javadoc；**不需要改行为**，不阻断阶段一准入） | 2026-10-30 |
