<!-- @new 2026-10-27 · `article-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G3 波次工作树（`align/g3-article-comment-favorite-hot`）** -->

# `article-common` lane 台账 —— 「article ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/article/article-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G3` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `article-common`（**原第 1/10 条**） |
| **现分支 / 工作树** | **`align/g3-article-comment-favorite-hot`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot`<br>⚠️ 原 `align/article-common` 工作树已于 **2026-10-27 01:44 撤掉** ⇒ 粒度从「一个域一棵」改为「**一个波次一棵**」 |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交；此前已合并 25/25 分支 + 全量回归 BUILD SUCCESS）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/article/{article-api, article-rpc, article-common, article-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/article/**` ＋ `docs/DRIFT/article/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取**，逐条证据以域台账为准）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `DRIFT-HOTAPI-030` | Tomcat 数「**总参数个数**」vs Go 数「**非空值个数**」⇒ 2049~10000 段且非空值 ≤2048 时 **Go 200 / RAG 5001** | `service/common`（CD-6 共享层） | ✅ **已落地**（global 第十四轮 · 6 个共享类 + 每域一行 yaml）<br>✅ **本域侧已验**（核对日期 **2026-10-27**）：`article-api/src/main/resources/application.yml:159` = `mode: raw` ⇒ 与 Go 同向（Go 侧 `service/article/api/` **未注册** `httpx.SetErrorHandler` ⇒ `httpx.ErrorCtx` ⇒ **400 + text/plain + nosniff + Go 原文**；见 `rest/httpx/responses.go:155`） | [`api.md`](api.md) <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/ddl-parity.sh` ⇒ **EXIT=2**（2026-10-30 03:34:15 · 2s）—— 🟡 **探针判定为「环境问题」**（结论不可用）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| （域侧 · **等价项 · 非编号**） | Go 必填字段校验：`core/mapping` **只认 `,optional`**，`,omitempty` **不算** optional ⇒ Go 缺字段即 400 | ⚠️ **落点已订正**：契约在 `service/users/users-common/src/main/java/com/rag/users/common/http/{GoStyleRequired,GoStyleRequiredFields,GoStyleRequest,GoRequiredFieldException}.java`（**实测四类全在 `users-common`**，不在 `service/common`）⇒ 属**共享层** | ✅ **两侧等价（EQUIVALENT）· 无缺口 · 不派单**（核对日期 **2026-10-27**）—— ⚠️ **本条不是漂移条目** ⇒ **没有编号**（`article/api.md:407` 判 `parseOption`（只认 inherit/string/optional*）↔ `GoStyleRequiredFields` = **EQUIVALENT**；`:460` 判「必填缺席 / 显式 null」↔ `GoRequiredFieldException` = **EQUIVALENT**；`:826-828` 的 CASE-02/03/04 全部 **EQUIVALENT**）<br>**实现已就位**：`CreateArticleReq.java:57`（`implements GoStyleRequiredFields`）`:109-119`（`assertGoRequiredFields()` ⇒ `GoStyleRequired.field("title"/"content", null)` ＋ `explicitNull(...)`）`:69-96`（`@JsonProperty` setter 记录 **presence**，区分「缺席」与「显式 null」）；`DeleteArticleReq.java:14` 与 `UpdateArticleReq.java:29-38` **显式不实现并各附 Go 侧理由**（Go 侧 `,optional` ⇒ `processNamedFieldWithoutValue` 的 `default:` 分支永不触发 ⇒ 加校验就是**凭空新增拒绝路径**）<br>⇒ **共享 helper 的失败文案是参数化的通用文案**（`GoStyleRequired` ⇒ `field "X" is not set` / `field "X" mustn't be nil`，字段名是入参）⇒ **不存在「文案基线取自 users 域」的问题** ⇒ **无残留 ⇒ 不派 G4** | [`api.md`](api.md) |
| `S-6` | `SqlTimeoutStatusTranslator` 产出的 `DEADLINE_EXCEEDED` 被 `invoke` 的 `catch(RuntimeException)` 降级为 `UNKNOWN` ⇒ 业务码 5001 → 1015 | `service/common` | ✅ **已改**（纯 Mockito 证据）<br>✅ **本域侧复核：在 article 域不成立**（核对日期 **2026-10-27**）—— `article-rpc/.../service/impl/ArticleServiceGrpcImpl.java:119`（`invoke`）→ `:127`（`catch (StatusRuntimeException e)` **在前**）→ `:147`（`catch (RuntimeException e)` **在后**）⇒ `DEADLINE_EXCEEDED` **不会**被二次抹掉 | [`rpc.md`](rpc.md) |
| （域侧 · 非编号） | `article-rpc` **缺 `baseline-version: 0`** ⇒ `baseline-on-migrate=true` + 非空 schema ⇒ `V1` 被整条跳过（起得来、首次查库才炸） | **域侧** `service/article/article-rpc/src/main/resources/application.yml:67-71` | 🔴 **未修**（核对日期 **2026-10-27** 实测确认：flyway 块只有 `enabled` / `baseline-on-migrate` / `locations` / `schemas` 四行，**无 `baseline-version`**、无 `table:`）<br>⚠️ **AGENTS.md §6.3 问题 4 只点名了 `favorite-rpc` 这个例外 ⇒ 记载不完整，漏了 `article-rpc`**（⇒ 共享面台账订正项）<br>**全仓 9 个 rpc 组件普查（核对日期 2026-10-27 · G4 扫的 9 个，我独立复扫逐条吻合）**：**已钉 `baseline-version: 0`（5）** = `comment-rpc` · `follow-rpc` · `like-rpc` · `message-rpc` · `task-rpc`｜**有 flyway 段但缺（2）** = **`article-rpc`** · **`favorite-rpc`** ← **真正的缺陷面**｜**N/A · 无 flyway 段（3）** = `hot-rpc` · `admin-rpc` · `user-rpc`（后者配置文件名是 **`application.yaml`** 不是 `.yml`）⇒ **我先前那张表只列了 7 个 ⇒ 漏了 `admin-rpc` 与 `user-rpc`；但两者都是 N/A ⇒ 不影响结论** | — |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ lane 复核后请就地更新本表并写入核对日期。
> ✅ **2026-10-27 复核轮已就地订正 3 条过期状态**（**必填字段校验**（原误挂 `DRIFT-ARTICLE-API-006` ⇒ **该编号属另一条已签字不修的条目** ⇒ 本条**无编号**、两侧 **EQUIVALENT**、**不派单**）· `S-6` 适用性 · `baseline-version`）——
> ⇒ **教训**：抽取快照的「落点」与「状态」两列**都要独立复核**，落点尤其不能照抄（本次实测落点由 `service/common` 改为 `users-common`）。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | `article-rpc/.../application.yml:248` `observability.rpc-timeout: 70s` ＋ `article-api/.../application.yml:233-234` `http-timeout: 70s`；消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:76-82`；装配点 `ArticleRpcApplication.java:124-131`（`server.intercept(...)` 真正挂载）；出向 `article-api/.../client/UserRpcClient.java`（**2026-11-01 起 = 裸 stub** · 域侧内联副本**已剪除**） | ✅ **一致** —— Go `service/article/rpc/etc/article.yaml:3` `Timeout: 70000` ⇒ 两侧同为 **70s**（**四域里唯一显式配超时值的域**）。Go 侧 `article.go:36` `DisableNativeRpcTimeout` ＋ `:51` `NewUnaryServerInterceptor` ⇒ **超时唯一执行者**；RAG 侧 `rpcInterceptorConfigurer` → `server.intercept(...)` 同构。出向（**2026-11-01 起**：域侧内联副本**已剪除** ⇒ ⇒ 上界与请求预算传播改由**共享层** `GoStyleOutboundDeadlineInterceptor` 承担 ＋ 域 yml `cap-millis-by-service.user.UserService: 2000` ⇒ ⇒ 语义仍是「取父子较早者」）≡ Go `context.WithTimeout` 的「取父子较早者」语义（`zrpc/internal/clientinterceptors/timeoutinterceptor.go:17-29`） | **L3**（Go 侧 L1 逐行对照 ＋ RAG 侧装配面扫描；未起进程）<br>✅ **HTTP 侧预算作用域已对齐（2026-10-27 · 本轮落码 · §3-L-8）**：Go 的计时器在 Authorize **之后**起算 ⇒ **验签在预算之外**；RAG 原先把验签内联在 MVC interceptor（预算**之内**）⇒ **真漂移** ⇒ ⇒ 本轮把验签搬进域内 `auth/GoStyleJwtAuthFilter`（`@Order(+6)` ⇒ 落在 `+3 Traceparent` **之内**、`+7 HttpMiddlewareFilter` **之外**）⇒ **验签不再计入预算**；守门 `ArticleApiJwtVerificationScopeTest` **7 例 ＋ 变异验证 3/3 精确变红**；⚠️ **本域预算是 70s**（`application.yml:234` ≡ Go `etc/article.yaml:4` `Timeout: 70000`）⇒ **全仓唯一不是 3s 的域** | | 2026-10-27 |
| **A2** | gRPC 拦截器链 | `ArticleRpcApplication.java:124-131` → 共享层 `GoStyleRpcProtectionChain.build(...)`（`service/common/.../protection/GoStyleRpcProtectionChain.java:61-78` 定义链序：recover → breaker → shedding → 业务观测） | ✅ **服务端链一致** —— Go `zrpc/server.go:144-167`（`setupUnaryInterceptors`：Trace/Recover/Stat/Prometheus/Breaker/Shedding/Timeout 逐条按 `c.Middlewares.*` 装配）⇒ RAG 三层 ＋ 业务观测层**顺序与判据逐条对齐**；Go 侧 `Timeout` 被 `DisableNativeRpcTimeout` 置 0 ⇒ Go 的 `UnaryTimeoutInterceptor` 不生效 ⇒ **两侧都只有一个超时执行者**。<br>🟡 Go 专属的 `Stat`/`Prometheus` 两层 RAG 改用 Micrometer 承接（**表达层**）<br>🔴 **但出向侧无熔断（client breaker 缺失）**：`GoStyleGrpcClientBreakerInterceptor.java:103-106` 是 `@Component` ＋ **`@GrpcGlobalClientInterceptor`** ⇒ **只能靠组件扫描**；`article-rpc` **刻意不扫 `com.rag.common`**（`ArticleRpcApplication.java` 的 `@SpringBootApplication` **无** `scanBasePackages`）⇒ 该 client 侧能力**不在容器里** ⇒ **出向调用无熔断**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）⇒ **落点在共享层 ⇒ §4-G4-6**。<br>⚠️ **本域确实有出向调用**（不是 N/A）：`article-rpc` 实测 **`@GrpcClient` = 5**（`client/SecurityRpcClient.java` / `MessageRpcClient.java` / `ContentSecurityPort.java` / `ImageSecurityRpcClient.java` 等）⇒ 缺口**可观测** | **L3**（链序 ＋ 装配面）；装配真挂另有 `ArticleGrpcInterceptorWiringTest`（`@Testcontainers` 真 PG ＋ 真发 gRPC ＋ 断言 `DEADLINE_EXCEEDED` ＋ 断言「取消时仍在跑」）⇒ **测试存在但本轮未跑**<br>（client breaker 缺失侧为 **L1**：否定事实可静态判定） | 2026-10-27 |
| **A3** | HTTP 保护链 | `article-api/.../application.yml:159` `rag.http.go-form-limit.mode: raw` ＋ `:160-174` `rag.protection.*`（max-conns 10000 / max-bytes 10485760 / cpu-threshold 900 / breaker enabled）＋ 共享层 4 个 Filter（`GoStyleBreakerFilter` / `GoStyleCpuSheddingFilter` / `GoStyleMaxBytesFilter` / `GoStyleMaxConnsFilter`）；扫描面 `ArticleApiApplication.java:81-84`（含 `com.rag.common`） | ✅ **一致** —— `mode: raw` **同向**：Go 侧 `service/article/api/` **未注册** `httpx.SetErrorHandler`（实测：全仓仅 `comment/api/commentservice.go:47` ＋ `hot/api/hotcenter.go:53` 注册）⇒ Go handler 走 `httpx.ErrorCtx`（`get_article_handler.go:21-24`）⇒ **400 + text/plain + nosniff + Go 原文**；RAG `raw` 档同形。其余阈值与 go-zero `rest/config.go` 的 tag 默认逐条同值 | **L3**（yaml ＋ 装配面；Filter 实现体未逐行读）<br>✅ **验签与保护链的相对次序已对齐（2026-10-27 · 本轮 · §3-L-8）**：本域新增的 `auth/GoStyleJwtAuthFilter`（`@Order(+6)`）落在 `+3 Traceparent` **之内**、`+7 HttpMiddlewareFilter`（预算起算）**之外**、`+8 MaxBytes` **之内** ⇒ ⚠️ **它排在 MaxBytes 之前** ⇒ **若它自己写 401 就会压过 413** ⇒ ⇒ 故它**绝不写响应**（只验签、只写 request attribute、401 仍由 interceptor 抛）⇒ **413 仍压过 401**（守门「413 压过 401」一条 ＋ 变异验证：让 Filter 自己写 401 ⇒ 该条**当场变红**）⇒ ⚠️ **它与 `GoStyleCpuSheddingFilter` 并列 +6**（共享层已占满 [+3,+6]）⇒ **两者相对位置由组件扫描顺序决定、**不承重**（本 Filter 绝不写响应 ⇒ 互换不改变任何可观察行为）⇒ 已在既有守门 `ArticleApiAssemblyWiringTest` 的失败提示里写明，避免后人把「顺序互换」误判成行为回归 | | 2026-10-27 |
| **A4** | 错误码 / envelope | 共享层 `service/common/.../http/GoStyleRouterErrorResponses.java:121-140`（404/405 裸响应）＋ `service/common/.../response/Response.java:14-17`（三字段、**无** `@JsonInclude`）＋ 域侧 `article-api/.../advice/GlobalResponseAdvice.java:69-113` / `GlobalExceptionHandler.java` | ✅ **一致** —— Go `service/common/response/response.go:3-7`（`Code/Msg/Data` 三字段**无 omitempty**）⇒ `data:null` 一定出现；Go `rest/router/patrouter.go:73,79-82`（404/405）≡ RAG（404 = `404 page not found\n` ＋ `text/plain; charset=utf-8` ＋ `nosniff`）。<br>🟡 **方法不匹配（405）被刻意分叉成 404 空 body**（article-api 是四域中唯一开 `rest.WithCors()` 的 ⇒ Go 的 `notAllowed` 被 CORS handler 接管 ⇒ `rest/internal/cors/handlers.go:44-48`）⇒ 已登记差异<br>✅ **本轮落码后 401 空 body 形状逐字未变**（验签失败由 Filter 产出**异常对象**、interceptor **原样重抛** ⇒ `@ExceptionHandler` 仍管得到 ⇒ 仍落 **401 + 空 body**；⚠️ 变异验证时发现：**本域「401 空 body」那条不区分**（Filter 裸写 401 恰好也是空 body ⇒ 只能证明「形状没变」、不能证明「谁写的」）⇒ ⇒ **本域判「Filter 不写响应」靠「413 压过 401」那一条**） | **L4**（`tools/errmsg-probe/run-parity.sh` ⇒ **EXIT=0**，Go sha256 == Java sha256 == `cad5cb4f…d1134`，49 行 = CONST 15 ＋ TABLE 15 ＋ FALLBACK 19；真源 sha256 校验通过） | 2026-10-27 |
| **A5** | 端口治理 | `article-api/.../application.yml:43` `server.port: ${ARTICLE_API_PORT:8889}` ＋ `article-rpc/.../application.yml:96` `grpc.server.port: 9001` | ✅ **一致** —— Go `service/article/api/etc/article-api.yaml:3` `Port: 8889` ＋ `service/article/rpc/etc/article.yaml:2` `ListenOn: 0.0.0.0:9001` ⇒ **端口图 1:1 复刻**（含 Go 自身存在的重号：article-rpc 9001 ＝ comment-rpc 9001、article-api 8889 ＝ admin-api 8889 ⇒ 靠容器隔离）。`RANDOM_PORT` **只出现在测试**（`ArticleApiAssemblyWiringTest.java:72` 用 `${local.server.port}` 读回，不硬编码）⇒ 生产面无随机端口<br>🔴 **2026-10-27 G3 实测补登（本域 gRPC 测试端口段未治理 · 待 G4 分配段号）**：**生产面**无随机端口 ✔，但**测试面**有 **9 处**落 **OS 临时段**（49152–65535 ＝ **Docker 随机发布容器端口用的同一段** ⇒ 别的会话的容器先占住 ⇒ `EADDRINUSE` ⇒ 环境性抖动）：**8 处** `@SpringBootTest` 写 `"grpc.server.port=0"`（`ArticleGrpcInterceptorWiringTest.java:97` · `ArticleSchemaGateStartupOrderTest.java:154` · `ArticleTransactionWiringTest.java:64` · `ArticleJsonbResultMapWiringTest.java:64` · `ArticleSpringContextWiringTest.java:66` · `ArticleRpcSlowThresholdGateTest.java:111` · `service/ArticleNullJsonbToProtoMappingTest.java:81` · `ArticleNotNullUpdateStrategyWiringTest.java:87`）＋ **1 处** `ArticleRpcRuntimeBootGateTest.java:185` 的 `new ServerSocket(0)`（真 fat jar 进程口）⇒ ⇒ **与 comment 域同款缺陷**（comment 域本轮已迁：补 `RealGrpcPort` 同款 ＋ 5 处全改）⇒ ⇒ ⚠️ **本域**同样**没有**段管理类（实测 `find service/article -name "*Port*.java"` ⇒ 零命中）⇒ ⇒ **需要 global 段表先分配一段**（预留池 `22200–22599` 里挑一段）<br>📌 **订正（2026-10-28 10:5x · G3）—— 上面最后那句「需要 global 段表先分配一段」【已过期，且是本域自己的活】**：global 段表 §4.10.19.3 **早就把 `22400–22499` 预分配给 article 域**（状态「本层预分配 · **域侧未落码**」）⇒ ⇒ **不需等 G4 派单** ⇒ ⇒ 本轮已**全域自修**（9 处全迁 ＋ 机制类 `RealGrpcPort` ＋ 守门 6 条含 2 条源扫）⇒ ⇒ 逐条 ⇒ [`article/rpc.md` §二十三.7](./rpc.md#二十三7-🟢🔴-grpc-真进程端口也迁出-os-临时段13-处--本域专用段-2240022479-2026-10-28-105x--g3) |⇒ ⇒ **段表是 global 协调面 ⇒ 本域不擅自占预留段 ⇒ 已转派 G4**（本域落码待段号到位）<br>✅ **2026-10-28 订正（落码后回填 · 原值保留不改写）**：① **「待 G4 分配」是误判** ⇒ ⇒ global 段表 §4.10.19.3 **早就把 `22400–22499` 预分配给 article 域**（状态「本层预分配 · **域侧未落码**」）⇒ ⇒ **本项本就是域侧自修**（AGENTS.md §5.3d）⇒ ⇒ **本域已自修**（段 `22400–22479`；机制类 `article-rpc/.../support/RealGrpcPort.java` ＋ 守门 `RealGrpcPortTest.java` 6 条）⇒ ⇒ 逐条落账见 [`article/rpc.md` §二十三.7](./rpc.md#二十三7-🟢🔴-grpc-真进程端口也迁出-os-临时段13-处--本域专用段-2240022479-2026-10-28-105x--g3)（**含本行「9 处」的口径订正 ⇒ 实迁 13 处**）② **「9 处」只数了 Spring 上下文形态** ⇒ ⇒ 另有 **4 处** `ServerBuilder.forPort(0)`（同域 · 同一 OS 临时段）⇒ ⇒ **实迁 = 13 处** ⇒ ⇒ **本行按订正读**。 | **L3**（yaml 面扫描 + 测试源码实测；未起进程验绑定） | 2026-10-27 |
| **A6** | errmsg 契约 | `article-common/.../errmsg/ErrorCode.java:95-178`（15 个常量 ＋ 文案）`:229-231`（`of`）`:254-256`（`getMsg`）；消费点 `article-api/.../advice/GlobalResponseAdvice.java:113` ＋ 各 Service | ✅ **一致** —— 15 个码值/文案与 Go `service/article/common/errmsg/errmsg.go:3-19`（const）`:21-37`（codeMsg）`:39-45`（`GetErrMsg`）**逐条相同**；未知码回落（`of` → `ERROR` ⇒ `"FAIL"`）一致；**业务码从不上 gRPC 线**（rpc 发标准 gRPC 码、api 侧映射回业务码）≡ Go `get_author_article_logic.go:44-52` | **L4**（同 A4 的探针 ⇒ **错误码值/文案/未知码回落语义逐字节一致**） | 2026-10-27 |
| **A7** | proto 契约 | `service/article/article-proto/src/main/proto/article.proto:1-134` | ✅ **一致**（差异仅 option 块＝表达层）—— 与 Go `proto/article.proto:1-133` **逐行相同**：`ArticleStatus` 6 个枚举值、6 个 message 的字段名/字段号/类型、`repeated`/`optional`/`map` 标注、`service ArticleService` 的 6 个 rpc 全部一致；唯一差异 = 删 `option go_package`（Go 原 `:4`）＋ 加 `java_package` ＋ `java_multiple_files=true`。<br>🔴 **但无机器回归网**：`tools/difftest/check-proto-static.sh` **只覆盖 `user` / `admin` / `task` 三域** ⇒ **article 的 proto 契约无任何自动守门**（AGENTS.md §3.4 已记「10 个 proto 中 8 个无回归网」）⇒ **域侧可自修**（域内加一条 proto 契约测试） | **L1**（逐行对照）；⚠️ **无回归网 ⇒ 下次改动无人守** | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | `article-rpc/.../db/migration/V8__create_article.sql:21-49` ＋ `V9__create_article_sync_outbox.sql:11-40` ＋ `V10__create_article_community_revision.sql:16-57`；jsonb typeHandler 在**域内** `article-rpc/.../mapper/JsonbTypeHandler.java:48-93`（配 `autoResultMap=true` ＋ 自定义 `@Select` 必须挂 `@ResultMap`，见 `ArticleMapper.java:43-48,55-70`） | ✅ **DDL 列级一致**（类型/可空性/DEFAULT/索引/触发器全对齐，含 `secondary_tags`/`ext_info` = JSONB、三个时间列 = TIMESTAMPTZ）<br>🔴 **但 `baseline-version: 0` 缺**（`article-rpc/.../application.yml:53-57`）⇒ 非空 schema 下 **V1 被整条跳过** ⇒ **域侧自修**（见 §3） | **L4**（`python3 tools/difftest/check-ddl-static.py --domain article` ⇒ **EXIT=0**：两侧各 56 列、**逐字相同 56 列**、可空性差异 **0**、DEFAULT 差异 **0**）＋ 配置面 L3 | 2026-10-27 |
| **A9** | trace / otel | 共享层 `service/common/.../http/GoStyleTraceparentFilter.java:188-287`（`@Component` ＋ `@Order(HIGHEST_PRECEDENCE+3)`：Extract→Start→Inject→chain）＋ `service/common/.../observability/GoStyleOtelAutoConfiguration.java:51-61`（`@AutoConfiguration`，经 `META-INF/spring/...AutoConfiguration.imports` 注册 ⇒ **rpc 模块也生效**）＋ `RpcInterceptor.java:197-210,302-308`；域侧 `article-api/.../application.yml:205-214` ＋ `article-rpc/.../application.yml:231-240`（otel exporter ＋ sampler） | ✅ **一致** —— Go `rest/handler/tracehandler.go:47-63`（Extract→Start→**Inject 到响应头**→next）＋ `rest/engine.go:134-137,245-251`（Trace 是 native 链第一个；404 也挂 TraceHandler）≡ RAG；`Traceparent` **canonical 化**（Go `textproto.MIMEHeader.Set` 同样首字母大写）。<br>ℹ️ **属性名是 `sea.*`（共享层：`sea.error.kind` / `sea.biz_code` / `sea.biz_msg` / `sea.timeout` / `sea.slow` / `sea.duration_ms`），不是 `audit.*`** —— `audit.*` 只存在于 users/admin 域 ⇒ **本轴的「`audit.*`」措辞对 article = N/A**（见 §4 的共享面订正项） | **L3**（Go 侧 L1 ＋ RAG 侧装配面扫描） | 2026-10-27 |<br>🔴 **2026-10-27 · G3 订正：本轴原结论「✅ 一致」**只覆盖了 trace 传播面**，**漏了域侧业务 span**这一面** —— 实测 Go 侧 `service/article/rpc/internal/logic/` 有 **6 个手写业务 span**（`create_article_logic.go:43` · `get_article_logic.go:47` · `list_articles_logic.go:31` · `update_article_logic.go:47` · `delete_article_logic.go:41` · `upload_file_logic.go:41`，tracer 名统一 `article-rpc`）⇒ 而 RAG 侧 article 域**一个业务 span 都没有**（实测：`service/article/**/src/main/java` 下 `*Spans*.java` **0 命中**）⇒ ⇒ **这是本域 A9 的真实缺口**（与 `comment` / `favorite` 域同款）⇒ **已登记为域侧自修项 `L-10`**（见 §3）⇒ ⇒ **本轴结论须读作「🟡 trace 传播面一致 · 🔴 域侧业务 span 缺失」**，原「✅ 一致」**不再覆盖业务 span 面**。<br>🟢 **2026-10-28 · G3 落码订正（上面那句「🔴 域侧业务 span 缺失」已被 commit `7726773` 关闭，原文保留不改写）** —— `ArticleSpans.java`（**321 行**）已建，**8 个 span（6 logic + 2 consumer）全部补齐**（tracer 名 `article-rpc`、逐条对齐 Go 的 `otel.Tracer`）⇒ 守门 `ArticleSpanWiringTest` **16 条** ⇒ 变异 **5/5** 精确转红 ⇒ ⇒ **本轴现读作「🟢 域侧业务 span 已落码 · 网已张」**（**L3**：Go 侧 L1 逐行 ＋ RAG 侧 L4 运行证据）⇒ 逐条 ⇒ [`vs-common.md` §3.2](#32-✅-l-10-已落码2026-10-28--g3-8-个-span6-logic--2-consumer全部补齐)。<br>🟢 **2026-10-28 · G3 裁定：G4 §4.10.64 ⑤ 的「域内 status 漂移」警告【对 article 域不成立】，且【方向搞反】**（G4 原文保留，不改写）—— G4 写「域内 span 若只调 `recordException` ⇒ status 恒 UNSET ⇒ **与 Go 的 `RecordError` 漂移**」⇒ ⇒ **中间那一步是错的**：Go otel-go **v1.44.0** 的 `RecordError` **【不改】status**（接口契约 `go.opentelemetry.io/otel/trace@v1.44.0/span.go:47-51`「**as this method does not change the Span status**」＋ SDK 实现 `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:626-630` javadoc ＋ `:631-655` 实现**全程无 `SetStatus` 调用**）⇒ ⇒ **Go 侧 `service/article/**` 的 `SetStatus` 零命中** ⇒ ⇒ **Go 侧 article 的业务 span status 本就恒 UNSET** ⇒ ⇒ **RAG 侧 `ArticleSpans` 零 `setStatus`（类注释 `:81`「本类**不得**调 `setStatus`」）⇒ 两侧一致 ⇒ 不漂移** ⇒ ⇒ **域侧零改动**（**不补 `setStatus`** —— 补了反而是引入 Go 侧不存在的 ERROR）⇒ 且守门 `ArticleSpanWiringTest.java:448-449` **已把这条钉成断言**（⇒ 断言 `StatusCode.UNSET`，注释「Go 侧无 `SetStatus` ⇒ 状态保持 UNSET」）⇒ ⇒ **网已张、语义已对齐** ⇒ 逐条证据 ⇒ [`favorite/api.md` §16.4.1](../favorite/api.md#1641-🟢-补证2026-10-28-go-侧-recorderror-也不改-status--两侧-status-语义同构--status-漂移不成立)

> **A3 预填锚点的复核结论（2026-10-27 核 · 原文保留供追溯）**
> 原文断言：「本域 `go-form-limit.mode` = **raw**；Go 侧：**未注册** ⇒ Go 侧解析失败 = **400 + Go 原文** ⇒ 与 RAG `mode=raw` 同向 ✅」
> ✅ **该锚点成立**（本域 `mode: raw` 实测无误，Go 侧未注册 `httpx.SetErrorHandler` 实测无误）⇒ **A3 一致**。
> ⇒ ⚠️ 但**必须区分**：本域是**显式声明 `raw`**；**`off` 档是 favorite 专属的刻意默认**（`GoStyleFormLimitMode.java:41-49` 的 javadoc 明写「favorite 不在 global 写权范围 ⇒ 共享层对它零影响 ⇒ 默认 `OFF`」）⇒ **其余 9 个域必须显式声明**，漏声明即漏保护（由 `tools/difftest/check-tomcat-limits.py` 静态断言）。

### 📌 订正注记（2026-10-27 · G3 · 21:0x）：A1 轴 **1 处引用文件名不存在**已实修（**行号原本就对**）

⚠️ **本块是「加注记」，不是「改原值」** ⇒ 上表 A1 轴的**结论原样有效** ⇒ 本轮只改**引用指针**。

| 位置 | 原引用 | 事实 | 实修 |
|---|---|---|---|
| 上表 **A1** 轴（L-8 行） | `service/article/api/articlecenter.go:34` | 🔴 **该文件不存在**（真源 + 本仓全仓搜 `articlecenter.go` ⇒ **0 命中**）⇒ 真源真名 = **`service/article/api/article.go`**（42 行） | ⇒ 改为 **`service/article/api/article.go:34`**（**行号不变** ⇒ `:34` 正是 `server.Use(observability.NewHTTPMiddleware(...))` 那一行 ⇒ **原行号本来就对**）|

⇒ ⚠️ **一条方法论（顺带记，跨域可复用）**：**真源的 api 入口文件名【逐域不同】，不能按 go-zero 惯例推** —— 实测：`service/article/api/article.go` · `service/favorite/api/favorite.go`（**无 center**）· 但 `service/hot/api/hotcenter.go` · `service/comment/api/commentservice.go`（**有 center / service**）⇒ ⇒ **按惯例猜文件名 ⇒ 必错** ⇒ ⇒ **必须逐个 `ls` 真源**。
⇒ **核对来源**：G2 lane 通报（2026-10-27 14:10）＋ 本域**自建核对器全量扫**（本域 **1262** 处「文件:行号」引用逐条核「文件存在 + 行号在范围内」）⇒ ⇒ **真缺陷 7 处已全部实修**。

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

| # | 事项 | 落点 | 依据 / 修法 |
|---|---|---|---|
| **L-1** | ✅ **补 `baseline-version: 0`（2026-10-27 阶段 2 已落码）** | `service/article/article-rpc/src/main/resources/application.yml`（flyway 段） | **已落码**：加了 `baseline-version: 0` ＋ 三段论证注释（「为什么必须钉 0」/「为什么关掉 baseline-on-migrate 更糟」/「为什么全新空库测不出来」）⇒ 非空 schema 下 **V1 会照常执行**（原先 baseline 打在 version 1 ⇒ **V1 被整条跳过** ⇒ 本域 V1 建表脚本永远跑不出来 ⇒ 后续迁移报 `relation "…" does not exist` ⇒ 服务起不来）。⚠️ **落码后仍需在非空 schema 下验证**（真 PG + 真 Flyway）⇒ 已列入收口回归项 |
| ~~**L-2**~~ | ⛔ **原项撤销（2026-10-27 · 前提不成立）** —— 原写「`article-rpc` **未** `@Import` `DeadlineQueryTimeoutInjector` ⇒ 补装配」⇒ **实测证伪**：`ArticleRpcApplication.java` 的 `@Import({...})` 块里**确实有** `DeadlineQueryTimeoutInjector` ＋ `SqlTimeoutStatusTranslator`（剥注释后解析 `@Import` 块，真实清单 = `SnowflakeBean` · `SnowflakeProperties` · `LoggerFactoryBean` · `RpcServerInterceptorFactory` · `ObservabilityProperties` · `ObservabilityLifecycle` · **`DeadlineQueryTimeoutInjector`** · **`SqlTimeoutStatusTranslator`** · `GoStyleProtectionConfiguration`）⇒ **article-rpc 的 SQL deadline 装配已在位** ⇒ **「缺 import」这个缺口不存在** ⇒ 原「article-rpc 是三张库表域里唯一没导的」**是错的**（G4 在回执里也独立指出了这一点）<br>⇒ **本域侧无「补装配」这项落码**；原项里「补一条真 Spring 上下文 + 真 mapper + 真 PG 的测试」这半 ⇒ **由单 1 在共享层一次性覆盖**（`service/common` 的守门）⇒ **域侧不重复做**（避免两条线各写一份同类测试）<br>⚠️ **教训**：我当初用**字符串 grep** 判断 `@Import` 清单 ⇒ **跨行数组 `@Import({ ... })` 匹配不到** ⇒ 误判「0 命中」⇒ **行号/清单类结论必须用「剥注释后解析注解块」，不能用 grep 命中数**（同源教训：`scanBasePackages`、`GoStyleProtectionConfiguration` 都栽过） |
| **L-3** | ✅ **补 proto 契约回归网（2026-10-27 阶段 2 已落码）** | **新增** `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/proto/ArticleProtoContractTest.java` ＋ `src/test/resources/proto-contract/{article-proto-contract.expected.txt, generate.py}` | **已落码**（域内文件，**未改共享脚本**）：<br>① **基准由脚本从 Go 真源生成** —— `generate.py` 解析 `Sea-RideTheWind_golang/proto/article.proto` ⇒ 冻结成 `article-proto-contract.expected.txt`（头部记真源路径 + **sha256 `21596ee267ba9052…`**）⇒ **期望值不是手抄的**（「期望值必须由真源生成」：手抄＝把真源的实现细节抄一遍 ⇒ 抄错时看不出错）；<br>② **比对的是「生成码的描述符」而不是「.proto 文本」** —— 读 `Descriptors.FileDescriptor` ⇒ 拿到**真正参与 wire 编码**的那份契约；<br>③ 钉住：package · 枚举名/枚举值编号 · 每个 message 的**字段名/字段号/类型** · **proto3 `optional` 的 presence** · `repeated` · `map<k,v>` · **7 个 rpc 的方法名与出入参**。<br>⇒ **实测绿**：`mvn -o -pl service/article/article-rpc test -Dtest=ArticleProtoContractTest` ⇒ **EXIT=0**（1 条用例）。<br>⚠️ **覆盖边界**：不覆盖 gRPC 帧 / HTTP2 帧（那需要两侧同时起服务）；**字节级**对拍另需 wire 探针（favorite 域有 `tools/proto-wire-probe/favorite/`，article 域**没有** ⇒ 属共享面 ⇒ 登记给合并收口执行者）。<br>⚠️ **放在 article-rpc 而不是 article-proto**：`article-proto` 模块**一行 junit 依赖都没有** ⇒ 往那儿加测试要先改 pom（与 favorite 域同款理由） |
| **L-4** | ✅ A3 `rag.protection.*` 阈值 —— **无需改** | `article-api/.../application.yml:160-174` | 当前值与 go-zero `rest/config.go` 的 tag 默认逐条同值；任何调值都在域内 |
| **L-5** | ✅ `audit.*` 埋点 —— **现在不该补** | — | Go 侧 article **没有** `audit.*` ⇒ 补了就是**新增 Go 没有的行为**（违反 AGENTS.md §3.1「差异只允许出现在表达层」） |

| **L-6** | ✅ **删本域 rpc 组件的域侧排序桥**（桥下沉共享层后的冗余清理 · **2026-10-27 G3 已落码** · **2026-10-31 G3 第十九轮复核：全仓域侧桥已归零** ⇒ 本行由 🔴 改 ✅）|<br>⚠️ **删除时必须连带处理 javadoc（G1 提醒 · 2026-10-27 已逐条核实）**：① 该文件的「**什么时候可以删**：升级到已用 Boot 4 新包名声明 `@AutoConfiguration(after = ...)` 的 MyBatis-Plus 版本之后」⇒ 这是**第二条、独立的**删除条件（MP 自己修好即可删）⇒ **共享层下沉后它对被删组件已被取代** ⇒ **不处理就会留下一条已经不成立的删除条件**；② 「与 … 同款」的**列举已过期** ⇒ 实际是 **9 个** rpc 组件（article / comment / favorite / follow / like / message / task / users-user / users-admin），不是文件里写的那几个 ⇒ 随文件删除或一并订正| `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ `service/article/article-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（删那一行注册） | **前置依赖**：① **G4 共享层桥先落码**（否则本域删了、共享层还没加 ⇒ MP 退避 ⇒ **启动失败**）② 阶段 2 放行 ⇒ **本项属阶段 2**。⚠️ **本域侧删除是纯删减、可独立验证**（域侧 `ArticleSpringContextWiringTest` 应仍绿 ⇒ 说明共享层桥已接管）⇒ **删完必须跑本域的真上下文守门**（证明共享层桥真的接管了排序）<br>✅ **2026-10-27 G3 已落码（L-6）**：① 删 `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`；② 本域自己的 `imports` 文件**只删第 1 行**（保留 `com.rag.article.article.rpc.client.ContentSecurityAutoConfiguration`）—— ⚠️ **该文件有 2 行**，与 comment/favorite 的**只含 1 行**形态**不同** ⇒ ⇒ **合并清单要分开写**<br>⇒ **前置依赖已满足**：共享层桥在本树（`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4`）<br>⇒ **验证（先 `clean` 再跑 —— 否则 `target/classes` 里的旧 class / 旧 imports 会掩盖删除）**：`mvn -o -pl service/article/article-rpc -am test -Dtest='ArticleSpringContextWiringTest,ArticleRpcArtifactClasspathGateTest,ArticleRpcRuntimeBootGateTest'` ⇒ **20 条 / 0 failures / 0 errors / 0 skipped**（2026-10-27T23:15:28+08:00）⇒ `ArticleSpringContextWiringTest` **16 条**（断言 6 个 Mapper Bean 存在 ⇒ **排序边已由共享层接管**）＋ `ArticleRpcArtifactClasspathGateTest` **3 条**（含**新增**第 ③ 条：fat jar 内嵌的 common jar 带共享层排序桥）＋ `ArticleRpcRuntimeBootGateTest` **1 条**（**真 fat jar 真进程起来**）<br>⇒ **产物层复核**：fat jar 的 `BOOT-INF/classes` 里**已无**域侧桥 class；fat jar 根的 `META-INF/spring/...imports` 只剩 `ContentSecurityAutoConfiguration` 一行；内嵌 `BOOT-INF/lib/common-0.0.1-SNAPSHOT.jar` 的 `imports` **含**共享层排序桥<br>⚠️ **配套的 2 处（缺了就会踩 G2 踩过的坑）**：① `ArticleRpcArtifactClasspathGateTest#buildFatJar` 的构建命令**必须带 `-am`**（不带 ⇒ `service/common` 不在 reactor ⇒ 内嵌 `~/.m2` 的**旧** common jar（实测那份的 `imports` **没有**排序桥）⇒ ⇒ 域侧桥一删 ⇒ fat jar 里就**没有**排序桥 ⇒ ⇒ MP 排序边悬空 ⇒ ⇒ **进程启动即挂**）② `isStale` 必须把**共享层源码**算进新鲜度（否则共享层改了而本模块没改 ⇒ fat jar 不重建 ⇒ 守门读到过期内容） |
> 🔴 **2026-10-27 · G3 独立核结论（4 项全过 ⇒ 删除动作本身结构上安全，但**等真 PG 窗口**才落刀）**
> 📌 **2026-10-27 G3 已落刀（原文保留不改写）**：**「等真 PG 窗口」这个条件本轮**没等**（也不需要）——
>   删除是**纯删减**（删一个空类 ＋ 删一行注册）⇒ ⇒ 且三处验证**本身就要真环境**：
>   `ArticleSpringContextWiringTest`（真上下文，断言 6 个 Mapper Bean 存在 ＝ 排序边真接管了）＋
>   `ArticleRpcRuntimeBootGateTest`（**真 fat jar 真进程起来**）⇒ ⇒ **clean 后重跑 20 条 / 0f 0e 0s**
>   （2026-10-27T23:15:28+08:00）⇒ ⇒ 详见 §3 的 **L-6** 行。
> ① **共享层那份是自动配置**：`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4` 登记了 `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration`
>    ⇒ **每个消费者 classpath 上都会加载** ⇒ **前置已满足**（`e18fd52` 已把它合进本树）；
> ② **两份排序边同向同目标**：共享层用**字符串版** `afterName="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"` ＋ `beforeName="com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration"`
>    ＝ 域内那份的**类字面量版** `after = DataSourceAutoConfiguration.class` ＋ `before = MybatisPlusAutoConfiguration.class`
>    ⇒ **同一条边、同一个方向** ⇒ 排序图里多一个同向节点、**无环**、功能等价 ⇒ 共存**不改变任何行为**；
> ③ **两份都不注册任何 bean**（类体只有一行注释）⇒ ⇒ **无同名 bean 冲突** ⇒ **不会**触发 `allow-bean-definition-overriding`
>    ⇒ 共存是**结构上安全**的，**不是「侥幸没炸」**；
> ④ **域内那份外部引用 = 0 命中**（`grep -rn "config.MybatisPlusOrderingBridgeAutoConfiguration" --include=*.java service/article` ⇒ 只命中文件自身）
>    ⇒ ⇒ **删除不断编译**。
> ⇒ **删除动作 = 纯删减**，但**运行期验证**（`ArticleSpringContextWiringTest` 等真 Spring 上下文守门）**需要真 PG**
> ⇒ ⇒ **当前被机器级 Docker 端口转发故障阻塞** ⇒ ⇒ 本轮**只落核结论，不落删除**（AGENTS.md：未验证不进 PR）。
> ⚠️ **附带（删 imports 的具体形态）**：`article-rpc` 的 `imports` 文件**有 2 行**（第 1 行 = 桥类，第 2 行 = `ContentSecurityAutoConfiguration`）
> ⇒ ⇒ 删桥只需**删第 1 行**，**文件保留**（不会变空）。

| **L-7** | 🔴 **删 article-api 的域内路径归一副本**（共享层已有一份 ⇒ 全仓只应有一份）| `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStylePathNormalizingFilter.java`（**整文件删**）| **G4 请求**（2026-10-27）：路径归一（Go 的 `path.Clean`）已进共享层（`service/common/http/GoStylePathNormalizingFilter` ＋ `GoStylePathNormalizingAutoConfiguration`，G4 commit `9690b04`）⇒ 域内副本成冗余。⚠️ **只删这一个文件**，**不要**动 article-api 的其它任何东西（尤其别动 `ArticleApiAssemblyWiringTest`）—— G4 用**按 Bean 名的过渡期去重**保证两份共存期间那条守门仍绿（实测 article-api 13 条全绿），删掉后 article-api 自动改用共享层那份 ⇒ **simple name 与 Bean 名都不变** ⇒ **那条守门不需要任何改动**继续成立；且共享层那份**顺手修掉了旧版移植的 2 处偏差**（`"/"`：Go `/` vs 旧版 `""`；`".."`/`"../a"`：Go 原样 vs 旧版丢点）⇒ 删后本域一并受益<br>⇒ **前置依赖：G4 的 `9690b04` 必须先合进我的树** ⇒ **✅ 2026-10-27 已满足**（G3 分支的 merge `e18fd52` 已把 `align/g4-service-common` @ `cd893d1` 合入 ⇒ `service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingFilter.java` **已在本树**）⇒ ⇒ **本轮已删**（`git rm service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStylePathNormalizingFilter.java`）<br>⇒ **删前我方独立核过 4 件事（不采信 G4 转述）**：① 共享层那份注册在 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:3` ⇒ **自动配置**（与组件扫描无关）✔；② 它的 `@Bean` 方法名 = `goStylePathNormalizingFilter`，而域内 `@Component` 的默认 Bean 名 = 同名（decapitalized simple name）⇒ **同名 ⇒ 过渡期去重（`@ConditionalOnMissingBean(name=…)`）双向都成立** ✔；③ 两份 `@Order` **都是 `Ordered.HIGHEST_PRECEDENCE + 2`** ⇒ **链序位置不变** ✔；④ 域内副本的 `goPathClean` **只在类内被用**（全域 grep：仅该文件自身）⇒ 删类**不断编译** ✔<br>⇒ **验证**：`mvn -pl service/article/article-api -am test` ⇒ ✅ **article-api 99 条 / 0 failures / 0 errors**（含**断言完整 Filter 链序**的 `ArticleApiAssemblyWiringTest` **13 条** ⇒ 共享层那份**确实进了 article-api 的链**、simple name 与位置都不变 ⇒ 那条守门**无需任何改动**继续成立）✔<br>⇒ **附带收益**：共享层那份是**更贴 Go 的重写版**（`"/"`：Go `/` vs 旧版 `""`；`".."`/`"../a"`：Go 原样 vs 旧版丢点）⇒ 删副本后本域一并受益 |
| **L-8** | 🔴 **article-api 的 JWT 验签被计入 70s 预算**（Go 的计时器在 Authorize **之后**起算 ⇒ 验签在预算**之外**）⚠️ **本域预算是 70s 不是 3s**（`application.yml:234` `http-timeout: 70s` ≡ Go `etc/article.yaml:4` `Timeout: 70000`）⇒ **全仓唯一例外** ⇒ **数值必须逐域核，不能照抄配方** | `service/article/article-api/src/main/java/com/rag/article/article/api/interceptor/JwtAuthInterceptor.java`（把验签拆出去）＋ **新增**同域 `@Order(+6)` Servlet Filter（**只验签、只写 request attribute、绝不写响应**） | **G1 同步（2026-10-27）· 前提已由我方独立复核成立**：<br>① **Go 侧执行序（真源证据 · go-zero v1.10.2 `rest/engine.go:bindRoute`）**：原生中间件（`buildChainWithNativeMiddlewares`）→ **`chn.Append(handler.Authorize(...))`（JWT 验签，`engine.go:74`）** → **再 append `ng.middlewares`（`server.Use` 注册的 observability 3s，`engine.go:110-111`）** ⇒ `chain.Append` = **更靠内** ⇒ ⇒ **Go 执行序：原生 → JWT 验签 → 3s 预算 → route 级黑名单 → handler** ⇒ ⇒ **验签在 3s 预算之外**。⚠️ `service/article/api/article.go:34` 的 `server.Use(NewHTTPMiddleware(...))` 虽是**全局**中间件，但 go-zero 把它 append 在 Authorize **内侧** ⇒ **「全局」≠「最外层」**（⚠️ 我第一轮就是按「全局包住 route 级」的直觉判断，差点把这条判成假阳性 ⇒ **必须读 go-zero 的 `bindRoute`，不能凭中间件注册方式推链序**）。<br>② **RAG 现状**：验签内联在 `JwtAuthInterceptor.preHandle`（`HandlerInterceptor` ⇒ DispatcherServlet **内**）⇒ 排在 `HttpMiddlewareFilter`（`@Order(+7)`，`service/common/.../spring/HttpMiddlewareFilter.java:37`）**之后** ⇒ ⇒ **验签被计入 3s 预算 ⇒ 与 Go 相反 ⇒ 真漂移**。<br>③ **共享层槽位（我方独立实测 `service/common/**`，不照抄 G1 的数字）**：+3 Traceparent（起 SERVER span）· +4 MaxConns · +5 Breaker · +6 CpuShedding · **+7 HttpMiddlewareFilter（3s 预算起算）** · +8 MaxBytes ⇒ 合法窗口 **[+3,+6]**（下界 = 验签须在 span **之内**；上界 = 须在预算**之前**）⇒ **四槽全被占满 ⇒ 只能并列 +6**（+6 严格小于 +7 ⇒ 顺序确定 ⇒ 并列无害）。<br>④ **修法（4 步）**：验签搬进本域 `@Order(+6)` Filter（**只验签、只写 request attribute、绝不写响应**）· **401 仍由本域 interceptor 抛**（失败**原样重抛异常对象** ⇒ `@ExceptionHandler` 仍管得到 ⇒ body 形状逐字不变）· 黑名单留在 interceptor（Go 侧黑名单是 route 级中间件 ⇒ 在预算**之内** ⇒ 现状本就一致，**别动**）· 守门断言**容器装配出的链**（`ServletContextInitializerBeans` 的装配结果），**不是**两个 `@Order` 数字。<br>⑤ **本域与 users 域的三处差异（抄配方前必须逐域自己核）**：**401 body = 空 body**（`JwtAuthInterceptor.java:95` 的 `ResponseEntity.status(401).build()`）· **无黑名单**（实测 0 命中）· **受保护路径判据**：本域的判据是 **interceptor 内部的 `PROTECTED_ROUTES`（4 条）**，已登记为 **EQUIVALENT**（见 `docs/DRIFT/article/api.md:361`「public 路径集合（含 upload 公开）」行）⇒ 搬 Filter 时**复用同一份判据**即可，**不要**照抄别域的路径常量（⚠️ `WebMvcConfig` 的 `addPathPatterns("/v1/**")` 只是**拦截器的作用域**，**不是**「哪些路径受保护」的判据 ⇒ **别拿它当判据** —— 我本轮第一稿就是拿它下了「粒度不同」的结论，**是错的**，已撤）。<br>⑥ **验收成本**：本域**没有真 Tomcat 测试** ⇒ 「413 压过 401」这条**响应级**语义**只能新写一条真 Tomcat 用例**来证（MockMvc 测不到响应头层）⇒ **比 favorite 域贵**。⚠️ **真启动须先起 rpc、等 READY 再起 api**（api 侧对 rpc 有 `immediate-connect-timeout: 3s`）。<br>⇒ **状态**：✅ **已落码（2026-10-27 · 本轮）** —— 落点全在本域 ⇒ **不派 G4**（域侧自修）。<br>⇒ **实施记录**：<br>① **新增** `service/article/article-api/src/main/java/com/rag/article/article/api/auth/` **4 个类**（与 users/admin/comment/favorite 域**同款配方**，五域同构便于跨域比对）：`ArticleJwtProtectedRoutes`（**受保护路由表的唯一所在** —— 4 条 method+PathPattern；**Filter 与 interceptor 共用**）· `ArticleJwtVerificationResult`（失败带**异常对象**⇒ 可**原样重抛**⇒ `@ExceptionHandler` 仍管得到 ⇒ **401 空 body 逐字不变**）· `ArticleJwtSignatureVerifier`（**只验签、不写响应、不查 Redis**；兼作本域 **401 的唯一构造点** `unauthorized()`，业务码 1005（`ERROR_UNAUTHORIZED`，**只是让日志可读** ⇒ 不出现在响应里））· `GoStyleJwtAuthFilter`（`@Order(+6)`，**只验签、只写 request attribute、绝不写响应**）。<br>② **改** `JwtAuthInterceptor`（构造器改为注入 verifier，**不新增任何依赖**）：**消费** Filter 的验签结果 → 失败**原样重抛** → **守卫 1**（`handler instanceof HandlerMethod` ⇒ 未注册路径仍 404）与**守卫 2**（「method + path 双命中受保护表」⇒ 3 个 public 端点仍公开）**两条都原样保留**（守卫 2 的判据改为**调用共用类**）。<br>③ ⚠️ **受保护路由表从拦截器搬到共用类**（`ArticleJwtProtectedRoutes`）—— 搬家的原因**不是「好看」**：验签搬进 Servlet Filter 后，**Filter 与 interceptor 必须共用同一份判据** ⇒ 否则 ① **public 端点被误伤**（安全面收窄但行为漂移）② **受保护端点落进兜底分支**（②静默不闭环）。<br>④ **守门** `ArticleApiJwtVerificationScopeTest`（**7 例**，真 Spring 上下文 ＋ 容器装配层 ＋ 行为层）⇒ **7/7 绿**；**变异验证 3/3 精确变红**：<br>　　· 变异 1：`@Order(+6)` → `+8`（跑到 3s 预算之后）⇒ **1 条红** = 「JWT Filter 必须排在 3s 预算 Filter **之前**」⇒ 精确命中（其余 6 例仍绿）；<br>　　· 变异 2：让 Filter 验签失败时**自己写 401 并短路** ⇒ **1 条红** = 「413 压过 401」（实测 401 ≠ 413）⇒ 精确命中；⚠️ **本域与 comment 域的差异**：本域「未注册路径仍 404」那条**不参与作证**（未注册路径压根**不命中受保护表** ⇒ Filter 根本不会验签它 ⇒ 它写不写 401 都还是 404）⇒ **本域判「Filter 不写响应」靠 413-over-401 这一条**（comment 域靠 413 + 404 两条）；<br>　　· 变异 3：摘掉 Filter 的 `@Component` ⇒ **1 failure + 2 errors** = 来源标记由 `filter` 变 **`inline`**（证明兜底分支真会生效）＋ bean 不存在 ⇒ 精确命中。<br>⑤ 🟡 **既有守门 `ArticleApiAssemblyWiringTest` 的「完整链序」断言已同步更新**（本轮**它主动抓到了**新 Filter 进链的事实）—— 该用例断言**真容器装配出的完整 Filter 列表** ⇒ 新增 `GoStyleJwtAuthFilter` 一项（实测落在 Breaker(+5) 与 CpuShedding(+6) 之间）⇒ **期望清单已更新**，并**新增两条承重断言**：「traceparent **之内**」＋「3s 预算 **之外**」（Go 侧 `engine.go:135` 的 TraceHandler 是 native 链第一个 ⇒ `Authorize`(:108) 在其内 ⇒ 验签耗时必须落进 SERVER span；`observability(3s)`(:110-112) 在 `Authorize` 内层 ⇒ 计时器在验签之后起算）。⚠️ **并列 +6 ⇒ 与 CpuShedding 的相对位置由组件扫描顺序决定、**不承重**（本 Filter 绝不写响应 ⇒ 两者互换不改变任何可观察行为）** ⇒ 已在该用例的失败提示里写明，避免后人把「顺序互换」误判成行为回归。<br>⑥ **证据强度**：**L3**（真 Spring 上下文 ＋ **容器装配层**（`ServletContextInitializerBeans` 的排序输出）＋ 行为层）⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」；**未跑双侧活体对拍**（Go 侧 article-api 依赖 etcd + article-rpc + Redis，本环境起不来）。⚠️ **本域无真 Tomcat 门禁** ⇒ 「413 压过 401」的**响应头层**语义在本域**只有 MockMvc 级证据**（favorite 域有真 Tomcat 守门可作对照）⇒ 该残余边界**已如实记在证据强度里**。<br>⑦ **本域三处差异已逐条保留**（**不「顺手统一」**）：**401 body 仍是空 body**（Go `authhandler.go:121` 不写任何字节）· **业务码仍是 1005**（且它**不出现在响应里**）· **3 个 public 端点仍公开**（`GET /v1/article/:id` · `GET /v1/articles` · `POST /v1/upload` —— **特别是 upload 公开**，它是本组件最反直觉的 L1 契约）。 |
| **L-9** | ⛔ **原项撤销（2026-10-27 · 前提不成立）** —— 原写「article-api 的受保护路径是『一条路径模式』，Go 是『逐 route 块』带 `rest.WithJwt` ⇒ 粒度不同」⇒ **实测证伪**：`WebMvcConfig` 的 `addPathPatterns("/v1/**")` 只是**拦截器的作用域**，**不是**「哪些路径受保护」的判据；本域真正的判据是 **interceptor 内部的 `PROTECTED_ROUTES`（4 条）**，且 `docs/DRIFT/article/api.md:361` 已把「public 路径集合（含 upload 公开）」登记为 **EQUIVALENT**（Go 第二/三段 `AddRoutes` 无 `WithJwt` ⇒ RAG 的 public 集合逐条对上）⇒ ⇒ **原项要修的东西已经是对齐的** ⇒ **不落码、不再登记**<br>⇒ ⚠️ **教训**：**「拦截器注册在哪条路径模式上」≠「哪些路径需要鉴权」** ⇒ 这两个是**不同的判据**（前者 = 作用域，后者 = 集合）⇒ 下结论前必须找到**消费点**（谁在决定「这次要不要鉴权」），不能拿注册参数当判据（与「落点必须独立核准」「判据必须能在真源上求值」同源） | — | **撤销，不落码**（受保护路径集合 = EQUIVALENT，见 `api.md:361`） |
| **L-10** | 🔴 **补 article 域自己的 otel 业务 span**（Go 侧 `article-rpc` 有 **6 个**手写 span，RAG 侧 **0 个**）| **单一类** `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleDomainService.java` 的 **6 个方法**（**已定位**，逐条行号）：`createArticle`（:108）· `getArticle`（:186）· `updateArticle`（:304）· `deleteArticle`（:485）· `listArticles`（:526）· `uploadFile`（:592）⇒ ⇒ **6 个 Go span 全部落在这一个类**，比 favorite 域（7 个 api Service 类 ＋ 3 个 rpc 类）简单 ⇒ ⇒ **落点已无歧义，只差 span 名 / 属性 / 判据** | **2026-10-27 逐条取证完成（静态取证，未跑活体探针 ⇒ 证据强度 L1）**：Go 侧本域业务 span 实为 **8 个** —— ① **6 个 logic span**（`service/article/rpc/internal/logic/`，tracer 名 `article-rpc`，⚠️ **span 名无域前缀**，与 favorite 域的 `FavoriteAPI.*`/`FavoriteRPC.*` 不同）：`CreateArticle`（`create_article_logic.go:44-48`）· `UpdateArticle`（`update_article_logic.go:48-51`）· `DeleteArticle`（`delete_article_logic.go:42-45`）· `GetArticle`（`get_article_logic.go:48-51`）· `ListArticles`（`list_articles_logic.go:32-38`）· `UploadFile`（`upload_file_logic.go:42-45`）；② **2 个 consumer span**（`internal/mqs/`，由 `observability.TraceConsumer` 创建，SpanKind=CONSUMER）：`ArticleConsumer.Consume`（`mqs/article_consumer.go:45`）· `ArticleSyncResultConsumer.Consume`（`mqs/article_sync_result_consumer.go:40`）⇒ ⚠️ **这 2 个原先不在域内台账口径内**（原口径只到 `logic/`）⇒ ⇒ **域侧业务 span 实为 8 个，不是 6 个**<br>**属性 15 个（裸名，无 `biz.`/`sea.` 前缀）**：String 9（`author_id`·`title`·`article_id`·`sort_by`·`manual_type_tag`·`secondary_tag`·`file_name`·`content_type`·`object_name`）· Int64 4（`page`·`page_size`·`total_articles`·`file_id`）· Bool 2（`desc`·`article_found`）⇒ ⇒ **属性名口径与 favorite 域的 `biz.*` 完全不同域，不可套用**<br>**`span.SetStatus` 在 `service/article/` 全树 = 0 次** ⇒ ⇒ RAG 侧这 8 个 span **一律不得自行设 ERROR 状态**<br>**🔴 错误判据逐 span 不同（22 处 `RecordError`）**：**业务失败也记**（NotFound@61/55 · PermissionDenied@60）⇒ ⇒ **不是** comment/favorite 域那种「系统失败才记」⇒ ⇒ **必须逐条对齐**；且 Go 侧有**三处系统性缺口****须逐字保留**：`setReviewNonce` 失败@89/108 **不记** · `GetArticle` NotFound@56 **不记**（改用 `article_found=false` 属性表达）· `GetArticle` PermissionDenied@66 **不记**<br>**🔴 两处时机差必须复刻**：① `GetArticle` 的**前置校验在 span 之前** ⇒ 校验失败**根本不存在 span**；② 其余 5 个的起始属性在**方法第一行**（早于入参校验）⇒ ⇒ 非法入参的 span 也带属性<br>**🔴 条件属性**：`ListArticles` 的 `manual_type_tag`/`secondary_tag`/`author_id` **仅在对应 optional 字段非 nil 时才设置**⇒ ⇒ 「**nil ⇒ 属性缺失**」不得写成空串<br>**共享层已覆盖的部分（避免重复实现）**：server span 的**业务/系统错误分档**（`sea.error.kind` ＋ `SetStatus`）在**共享层 `RpcInterceptor`** 里 ⇒ ⇒ 域侧这 8 个 span **只对齐 Go 的 logic 层行为**，**不重复**做 status 分档<br>**落点**：6 个 logic span ⇒ **`ArticleDomainService` 的 6 个方法**（`createArticle`:108 · `getArticle`:186 · `updateArticle`:304 · `deleteArticle`:485 · `listArticles`:526 · `uploadFile`:592）⇒ 2 个 consumer span ⇒ **待核** RAG 侧是否已复用共享层 `MqConsumer`（若已复用 ⇒ 这 2 个可能**已对齐**，只登记不落码）｜🔴 **未修**（**取证已完成**；落码待下一轮）｜2026-10-27 |｜📌 **订正注记（2026-10-28 · G3）—— 上面的「🔴 未修」已过期**：L-10 **已于 commit `7726773` 落码**（`ArticleSpans.java` **321 行** · **8 个 span** = 6 logic + 2 consumer）⇒ 守门 `ArticleSpanWiringTest` **16 条** ＋ **变异 5/5** ⇒ ⇒ **本项已闭合** ⇒ **原「未修」保留不改写**（按「改旧判断 ⇒ 保留原值 + 加订正注记」纪律）⇒ 落码明细 ⇒ [§3.2](#32-✅-l-10-已落码2026-10-28--g3-8-个-span6-logic--2-consumer全部补齐)｜2026-10-28 |｜🔴 **未修**（**缺口已坐实**；落码待逐条取证完成）｜2026-10-27 |
> 🟡 **共享层 U+FFFD 反馈单只登记一次** ⇒ 见 [`comment/vs-common.md`](../../DRIFT/comment/vs-common.md) §4-**G4-4**（`service/common` 两个测试类，各丢 1 个汉字，纯注释）⇒ **本域不重复派单**。


### 3.1 📌 **L-10 的 Go 侧取证已补全**（2026-10-28 · G3）—— 原口径漏了一维：**`span.AddEvent`**

🔴 **原 L-10 口径只记了「span 名 + 属性 + `RecordError`」，漏了 Go 侧还有 `span.AddEvent` 这一维** ⇒ ⇒
本轮实测**全量 12 处** ⇒ ⇒ **落码时必须一并对齐**，否则 RAG 的 trace 里会**少 12 个事件**（不是少几个 span ⇒ 观测面一样缺）。

**① 6 个 logic span 的完整矩阵**（逐条现核 · 2026-10-28 · 行号取自 `service/article/rpc/internal/logic/`）：

| Go logic | tracer | span 名 | 起始属性（`WithAttributes`）| 后续 `SetAttributes` | `AddEvent` | `RecordError` |
|---|---|---|---|---|---|---|
| `create_article_logic.go:42` | `article-rpc` | `CreateArticle` | `author_id`(Str) `:45` · `title`(Str) `:46` | `article_id`(Str) `:57` | 0 | **4**（`:52` `:71` `:110` `:126`）|
| `update_article_logic.go:46` | `article-rpc` | `UpdateArticle` | `article_id`(Str) `:49` | — | 0 | **8**（`:55` `:61` `:68` `:83` `:93` `:98` `:113` `:119`）|
| `delete_article_logic.go:40` | `article-rpc` | `DeleteArticle` | `article_id`(Str) `:43` | — | 0 | **5**（`:49` `:55` `:60` `:70` `:93`）|
| `get_article_logic.go:40` | `article-rpc` | `GetArticle` | `article_id`(Str) `:49` | `article_found`(Bool) `:57` / `:65` | **8** | **4**（`:60` `:73` `:86` `:98`）|
| `list_articles_logic.go:30` | `article-rpc` | `ListArticles` | `page`(I64) `:33` · `page_size`(I64) `:34` · `sort_by`(Str) `:35` · `desc`(Bool) `:36` | `manual_type_tag`(Str) `:51` · `secondary_tag`(Str) `:55` · `author_id`(Str) `:59`（**三个都条件设置**）· `total_articles`(I64) `:71` | 2 | 1（`:66`）|
| `upload_file_logic.go:40` | `article-rpc` | `UploadFile` | `file_name`(Str) `:43` | `file_id`(I64) `:53` · `content_type`(Str) + `object_name`(Str) `:62` | 2 | 2（`:49` `:76`）|

⇒ ⇒ **属性名合计 15 个（裸名，无 `biz.` / `sea.` / `audit.` 前缀）**：
String 9 = `author_id` · `title` · `article_id` · `sort_by` · `manual_type_tag` · `secondary_tag` · `file_name` · `content_type` · `object_name`；
Int64 4 = `page` · `page_size` · `total_articles` · `file_id`；Bool 2 = `desc` · `article_found`。
⚠️ **属性名口径与 favorite 域的 `biz.*` / comment 域的 `audit.*` 完全不同域 ⇒ 不可套用**。

⚠️ **事件名带空格**（不是 `snake_case`）⇒ 逐字保留：
`GetArticle` 8 个 = `"start find article"` `:53` · `"find article success"` `:64` · `"start incr view count"` `:71` ·
`"incr view count success"` `:77` · `"start get minio object"` `:82` · `"get minio object success"` `:93` ·
`"start read minio object"` `:95` · `"read minio object success"` `:102`；
`ListArticles` 2 个 = `"start db list"` `:62` · `"db list success"` `:70`；
`UploadFile` 2 个 = `"start upload to minio"` `:66` · `"upload to minio success"` `:82`。

**② 🔴 三处「Go 不记这个错误」的缺口（本轮把行号现核）**：

| 缺口 | 位置 | Go 的写法 | RAG 侧要对齐成 |
|---|---|---|---|
| `UpdateArticle` 的 `setReviewNonce` 失败 | `update_article_logic.go:89-90`（switch 分支）· `:108-109`（default 分支）| `return nil, err` —— **没有** `span.RecordError` | **不记** |
| `GetArticle` 的 NotFound | `get_article_logic.go:56-59` | `span.SetAttributes(attribute.Bool("article_found", false))` ＋ `return nil, status.Error(codes.NotFound, …)` —— **没有** `RecordError` | **不记**（改用 `article_found=false` 表达）|
| `GetArticle` 的 PermissionDenied | `get_article_logic.go:66-68` | `return nil, status.Error(codes.PermissionDenied, …)` —— **没有** `RecordError` | **不记** |

⇒ ⇒ **「业务失败也记」**（`DeleteArticle` 的 5 处、`CreateArticle` 的 4 处里含业务类）⇒ ⇒
🔴 **不能用「异常类型 = 业务异常 ⇒ 一律不记」这种一刀切判据**（comment 域那套「业务码段」判据**在本域不适用**）
⇒ ⇒ **判据必须是「逐点白/黑名单」**。

**③ 🔴 两处时机差（原口径已记，本轮现核）**：

| 差异 | Go 侧 | RAG 侧要对齐成 |
|---|---|---|
| `GetArticle` 的**前置校验在 span 之前** | `:41-46`（`PublicOnly` → `getPublicArticle`；`RequesterId != "" && IncrView` → InvalidArgument）⇒ **这两条路径根本不存在 span** | **前置校验必须在 span 之外** |
| 其余 5 个的**起始属性在方法第一行** | 早于入参校验 ⇒ **非法入参也会产生一个带属性的 span** | 起始属性**在入参校验之前**设置 |

**④ 🔴 一处「记了但不返回」**（`get_article_logic.go:72-75`）：`IncrViewCount` 失败 ⇒ **记 `RecordError` ＋ 记日志，但 `return` 继续往下走**
⇒ ⇒ **RAG 侧若按「抛异常 ⇒ 整个 span 记一次」处理，就会与 Go 不一致**（Go 是「记了，但方法继续、最终成功返回」）⇒ ⇒ **这条要单独表达**。

**⑤ 2 个 consumer span —— 🔴 本轮实测：RAG 侧**没有**接共享层那座 ⇒ 缺**

| 侧 | 事实 |
|---|---|
| Go | `internal/mqs/article_consumer.go:45` ＝ `observability.TraceConsumer(ctx, "article-rpc", "ArticleConsumer.Consume", articleMessageAttrs("article_review", key), …)`；`internal/mqs/article_sync_result_consumer.go:40` ＝ 同款，span 名 `ArticleSyncResultConsumer.Consume` · topic `article_sync_result` |
| Go 的属性 | `internal/mqs/article_consumer.go:144-150` `articleMessageAttrs(topic, key)` ⇒ **4 个**：`messaging.system`="kafka" · `messaging.destination`=topic · `messaging.operation`="consume" · `messaging.message.key`=key |
| Go 的语义 | `service/common/observability/mq.go:12-27` ⇒ tracer=`article-rpc` · **SpanKind = CONSUMER** · `trace.WithAttributes(attrs...)` · `defer span.End()` |
| **共享层（RAG）** | `service/common/src/main/java/com/rag/common/observability/MqConsumer.java:71-123` `traceConsumer(...)` ⇒ **与 Go 的 `TraceConsumer` 逐项同构**（SpanKind=CONSUMER `:83` · attrs map `:86-93` · 错误分档 `:110-117` · `finally span.end()` `:120-122`）⇒ ⇒ **共享层这一半已经齐了** |
| **域侧（RAG）缺口** | 实测 `service/article/**/src/main/java` 下 `MqConsumer` **0 命中**（只有 `target/` 里的 jar 命中 ⇒ 是共享层那份）⇒ ⇒ article-rpc 用的是**自己的** `@KafkaListener`：`service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleReviewConsumer.java:100` ＋ `ArticleSyncResultConsumer.java:77` ⇒ ⇒ 这两个类里 `Span` / `Tracer` / `otel` **0 命中** ⇒ ⇒ **2 个 consumer span 全缺** |

⇒ ⇒ **⇒ 好消息：consumer span 这一半**不需要动共享层**（共享层那座已与 Go 同构）⇒ ⇒ **纯域侧接线**：
把两个 consumer 的处理体包进 `MqConsumer.traceConsumer(ctx, "article-rpc", "ArticleConsumer.Consume" / "ArticleSyncResultConsumer.Consume", 4 个 `messaging.*` 属性, body)`。
⚠️ **类名对应关系待核**：Go 叫 `ArticleConsumer`，RAG 叫 **`ArticleReviewConsumer`** ⇒ ⇒ **不能默认一一对应** ⇒ 落码前要逐条核 RAG 那个类消费的是不是 Go `ArticleConsumer` 消费的那个 topic。

**⑥ 共享层已覆盖、不要重复做**：server span 的**业务/系统错误分档**（`sea.error.kind` ＋ `SetStatus`）在共享层 `RpcInterceptor` 里
⇒ ⇒ 域侧这 8 个 span **只对齐 Go 的 logic 层行为**；
且 Go 侧 `service/article/` 全树 `span.SetStatus` = **0 次** ⇒ ⇒ **域侧这 8 个 span 一律不得自行设 ERROR 状态**。

**⑦ 🔴 落码方案（本轮定的设计，建议下轮按此落）——「显式逐点记录」，不要「异常逃出就记」**

comment 域的 `CommentSpans` 用的是「异常逃出 ⇒ 按业务码段判一次」⇒ ⇒ **本域用不了**（② 的三处缺口要「不记」，
④ 要「记了但不返回」）⇒ ⇒ **本域应把 live `Span` 交回给业务方法，在 Go 的每个 `RecordError` / `AddEvent` 点显式调用**：

- ⇒ **逐点 1:1 映射**（24 处 `RecordError` + 12 处 `AddEvent` + 15 个属性）⇒ ⇒ **不可能漏、也不会多记**；
- ⇒ 缺口 ② 的三处 ⇒ **不调 `recordException`** 即可（天然「不记」，不需要 sentinel、不需要改异常类型）；
- ⇒ 缺口 ④ ⇒ 在该分支里显式 `recordException` 而**不抛**；
- ⇒ 与共享层无冲突（域侧不碰 `service/common`）⇒ ⇒ **纯域侧改动**。

**⑧ 🔴 本轮结论：取证**已完整**，阻断**不是取证，是落地机制**
- 取证：**已完成**（本节 ①~⑥ 逐条现核，含原口径漏掉的 `AddEvent` 一维 ＋ consumer span 的属性/Kind/共享层同构性）；
- 落点：**已无歧义** ⇒ 6 个 logic span ⇒ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleDomainService.java`
  的 6 个方法（`createArticle` · `getArticle` · `updateArticle` · `deleteArticle` · `listArticles` · `uploadFile`）
  ＋ 2 个 consumer span ⇒ `mq/ArticleReviewConsumer.java` ＋ `mq/ArticleSyncResultConsumer.java`（**接共享层 `MqConsumer` 即可**）；
- 范式：照抄 comment 域的 `CommentSpans.java`（`service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentSpans.java`）
  ⇒ ⇒ 但**要多一维「能打事件」**，且**错误记录改成逐点显式**（不是异常逃出统一判）；
- ⇒ ⇒ **下一轮可直接落码**（本轮**只做取证补全**，**未改任何生产代码** ⇒ ⇒ 本域基线不受影响）。

### 3.2 ✅ **L-10 已落码**（2026-10-28 · G3）—— 8 个 span（6 logic + 2 consumer）全部补齐

**证据强度（分层，不合并）**：**RAG 侧 L4**（真 OTel SDK ＋ 收集型 exporter ⇒ 断言**导出的 span**，非「方法被调用」）
· **Go 侧 L1**（逐行源码取证，§3.1 ①~⑦ 的行号）· 🔴 **双侧同时跑的对拍：未做** ⇒ ⇒ **本条不得读作「两侧已对拍一致」**。

**落码清单（5 个文件 · 纯域侧 · 未碰 `service/common`）**

| 文件 | 改动 | 行数 |
|---|---|---|
| `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleSpans.java` | **新建**（321 行）：8 个 span 名 ＋ 15 个属性名 ＋ 12 个事件名 ＋ `traced` 模板 ＋ `record` / `event` 助手 ＋ `consumerAttrs` | 321 |
| `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleDomainService.java` | 6 个方法各加一层 span 包装 ＋ **逐点** `record` / `event` | 668 → 935 |
| `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleReviewConsumer.java` | 接共享层 `MqConsumer.traceConsumer` ＋ 拆出 `doConsume` | 373 → 419 |
| `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleSyncResultConsumer.java` | 同上 | 209 → 251 |
| `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleSpanWiringTest.java` | **新建**（583 行）：**16 条**守门 | 583 |

**8 个 span 的落点对照（行号取自落码后的 RAG 侧）**

| span 名 | 落点 | 起始属性 | 后续属性 | 事件 | `record` 点 |
|---|---|---|---|---|---|
| `CreateArticle` | `ArticleDomainService.java:118` | `author_id` `title` | `article_id` `:150` 之后 | 0 | 4（`:134` 雪花 · `:150` MinIO · `:187` insert · `:201` kafka）|
| `GetArticle` | `:248`（🔴 前置校验在**外** `:236-247`）| `article_id` | `article_found` `:269`/`:274` | **8**（`:257 :273 :285 :300 :309 :322 :323 :324`）| 4（`:263` 查询 · `:296` IncrView · `:317` MinIO）|
| `UpdateArticle` | `:397` | `article_id` | — | 0 | 8（`:415` 查询 · `:422` NotFound · `:433` applySourceUpdates · `:461` 分支A · `:478`/`:485` 分支B · `:503`/`:511` 分支C）|
| `DeleteArticle` | `:641` | `article_id` | — | 0 | 5（`:653` 查询 · `:660` NotFound · `:669` PermissionDenied · `:678` 事件ID · `:686` 事务）|
| `ListArticles` | `:728`（🔴 **公开分支也有** span）| `page` `page_size` `sort_by` `desc` | `manual_type_tag` `secondary_tag` `author_id`（条件）· `total_articles` | 2（`:766` `:786`）| 1（`:782`）|
| `UploadFile` | `:835` | `file_name` | `file_id` `content_type` `object_name` | 2（`:869` `:882`）| 2（`:847` 雪花 · `:876` MinIO）|
| `ArticleConsumer.Consume` | `ArticleReviewConsumer.java:125` | — | `messaging.*` × 4 | — | 共享层 `markSystemError` |
| `ArticleSyncResultConsumer.Consume` | `ArticleSyncResultConsumer.java:96` | — | `messaging.*` × 4 | — | 同上 |

**「Go 记不记」的逐点白/黑名单（§3.1 ② 的三处缺口 ⇒ 守门逐条钉死）**

| 位置 | Go | 落码 | 守门用例 |
|---|---|---|---|
| `get_article_logic.go:56-58` NotFound | **不记**（改用 `article_found=false`）| 不调 `record` | `getArticleNotFoundIsNotRecorded` |
| `get_article_logic.go:66-68` PermissionDenied | **不记** | 不调 `record` | `getArticlePermissionDeniedIsNotRecorded` |
| `get_article_logic.go:73-75` IncrViewCount | **记了但不返回** | `record` 后**不抛**，继续往下走 | `getArticleIncrViewFailureIsRecordedButNotReturned` |
| `update_article_logic.go:59-63` NotFound | **记** | `record` | `updateArticleNotFoundIsRecorded` |
| `delete_article_logic.go:53-57` / `:58-62` | **都记** | `record` | `deleteArticlePermissionDeniedIsRecorded` |
| `update_article_logic.go:89-90` / `:108-109` `setReviewNonce` | **不记** | 不调 `record` | （无独立用例；由「不自动记」这一机制保证）|

**守门（`ArticleSpanWiringTest` · 16 条 · 2026-10-28）**
- **走装配层**：自装 `OpenTelemetrySdk` ＋ `SimpleSpanProcessor` ＋ 自建 `CollectingExporter` ⇒ 断言**导出的 `SpanData`** ⇒ ⇒ **不是「方法被调用」那种假绿**（AGENTS.md §3.3）。
- **⓪ 第一条是「装置活性」**（`collectorIsAlive`）：先证明收集器**真的**收到了探针 span ⇒ 否则「什么 span 都没导出」会让全部断言**平凡通过**。
- **轻量 Mockito 版**：**不起 Spring 上下文 · 不碰 PG · 不占 gRPC 端口** ⇒ ⇒ 不抢 Testcontainers 端口段（article 域的 `22850–22879` 这轮**没用上**）、不受库表状态影响、可毫秒级跑完。
- **双向断言**：不仅钉「该有的 span 有」，还钉「**不该有的路径不得产生 span**」（`getArticle` 的 2 条前置校验路径）＋「**该有但容易漏的路径必须有**」（`listArticles` 的公开分支**有** span）。

**全模块回归（防「守门绿但既有测试红」）**
`mvn -B -o -pl service/article/article-rpc -am test`
⇒ **172 条 / 0 failures / 0 errors / 0 skipped**（2026-10-28T01:59:33+08:00）⇒ BUILD SUCCESS
⇒ 含既有 `ArticleReviewConsumerTest`（15/0）· `ArticleDomainServiceSemanticsTest`（19/0）· `ArticleSemanticsRegressionTest`（11/0）
⇒ ⇒ **两个 consumer 的处理体拆分（`onMessage` → `doConsume`）与 6 个 logic 的 span 包装没有破坏任何既有断言**。

**变异验证（5 轮 · 每轮只翻一个变量 · 编译通过 + 红在目标断言）**

| 变异 | 结果 | 红在 |
|---|---|---|
| 基线（未变异）| **16 / 0f / 0e / 0s** | — |
| M1 删 `start find article` 事件 | 16 / **3f** / 0e | `getArticleExportsGoSpanWithEightEvents` + 两条「不记」用例 |
| M2 删 `updateArticle` 的 NotFound 记录 | 16 / **1f** / 0e | `updateArticleNotFoundIsRecorded` |
| M3 删 `deleteArticle` 的 PermissionDenied 记录 | 16 / **1f** / 0e | `deleteArticlePermissionDeniedIsRecorded` |
| M4 把 `messaging.destination` 改成 kafka topic | 16 / **1f** / 0e | `reviewConsumerExportsGoSpan` |
| M5 绕开 `traceConsumer` | 16 / **2f** / 0e | 两条 consumer 用例 |
⇒ 每轮还原均以 **sha256 逐字节**校验一致（脚本 `/tmp/g3_l10_mutate.py`，备份 `/tmp/g3_l10_mut_backup/`）。

🔴 **守门当场抓到的真 bug（本轮最有价值的产出）**：落码首版**漏发了** `get_article_logic.go:53` 的 `start find article` 事件
⇒ ⇒ 被 M1 那条「事件集合逐字相等」的断言抓住（红 3 条）⇒ ⇒ **若没有这条断言，RAG 的 trace 会静默少 1 个事件**（与原口径漏掉 `AddEvent` 那一维同类）。

**🔴 三条「结构性差异」（不是漂移，但必须留痕）**
1. **雪花 ID 失败分支**：Go `snowflake.GetID()` 返回 error ⇒ `RecordError`（`create:52` / `upload:49` / `delete:70`）；RAG 的 `Snowflake.nextId()` 抛 `IllegalStateException` ⇒ ⇒ 落码**记了** ＋ **原样上抛**（**不改错误码**）⇒ 错误码语义差异是**既有的**，不是本次引入。
2. **MinIO 两阶段被 RAG 合成一次调用**：Go `GetObject` + `io.ReadAll` 各带一对事件；RAG 的 `storage.readString` = `readAll(get(...))` ⇒ ⇒ **成功路径按 Go 的事件顺序发全 4 个**（保持事件集合一致）；**失败路径无法区分是哪一阶段失败** ⇒ 只发第 1 个（与 Go「get 阶段失败」同形）。
3. **`ListArticles` 的 db list**：Go 一次 `List` 返回 `(articles, total, err)`；RAG 两次调用（`countAuthorView` + `selectAuthorView`）⇒ ⇒ 事件集合一致（2 个），`total_articles` 取自 `count` ⇒ 失败时两条都在 try 内 ⇒ `record` 一次（与 Go 一次 `RecordError` 同形）。

**🔴 本次落码带来 1 项**共享面**变化** ⇒ 已按 §4 **G4-9** 派给 G4：跨域耦合矩阵的 `common_radiation` 快照（`MqConsumer` 多了 `article`）⇒ **只更新快照，不改代码**。

**⚠️ 尚未做（下一步候选）**：**Go 侧 span 导出探针** ⇒ 把「Go 侧 L1」升级为「双侧活体对拍」⇒ 形态可照抄 `docs/DRIFT/hot/rpc.md` §十二.9 的 `redis-disconnect-probe` 规格（Go 腿起一个 in-process OTel exporter，把 span 名/属性/事件 dump 出来 ⇒ 与 RAG 侧守门的期望值对拍）
⇒ ⇒ **本条不写进 §4 派单**（域侧能自修 ⇒ 自修即可），但**需要 `tools/**` 写权** ⇒ ⛔ 等 G4 分配或由 G4 落码。

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> 派单走**实时轨**（`session send` ＋ 反馈单 7 项）＋ **正式轨**（本表）⇒ 两轨内容须一致（AGENTS.md §5.3e）。
> **回执规格（2026-10-27 双方议定 · G4 已写入 `docs/DRIFT/global/vs-common.md` §4.1，commit `6e3e612`）**：回执 **第 4 项 = 「字段本身写全」** ⇒ ① 跑没跑 ② **未跑时把理由写进第 4 项字段本身**（不写在正文里）③ 跑了则给 **模块数 / 用例数 / failures / errors / skipped / 耗时** ④ **三门禁结果**。> ⚠️ 另外三项不变：**① commit 号 ② 逐个 `文件:行号` 改动清单 ③ 触及的 A1~A9 轴 + Go 出处**。

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| ~~**G4-1**~~ | ⛔ **已撤销（2026-10-27）** —— 原写「`DRIFT-ARTICLE-API-006` 的剩余项：共享 helper 的文案/口径基线取自 users 域」⇒ **三条独立理由 ⇒ 不派单**：① **编号撞号** —— 权威面 `article/api.md:676-682` 的 `DRIFT-ARTICLE-API-006` 是「8–10 MiB JSON body 的错误文案（LOW，**2026-10-24 第六轮已确认不修** + characterization test + 变异验证）」，**与本条完全无关**（该文件 `:8-11` 自己写了「两套编号同形不同义」告警，且举的例子就是 006）⇒ **照编号执行会去改一条已签字不修的条目**；② **主题本身不是漂移条目** —— `article/api.md:407/460/826-828` 判 **EQUIVALENT** ⇒ 必填字段校验**两侧已对齐**，**没有编号**；③ **复核后无任何共享层缺口** —— 契约在 `users-common`（`GoStyleRequiredFields` 标记接口 ＋ `GoStyleRequired` 参数化通用文案 `field "X" is not set` / `mustn't be nil`）· article 域 `CreateArticleReq` **已接入**（`:57/:109-119`）· `DeleteArticleReq.java:14` 与 `UpdateArticleReq.java:29-38` **显式不实现并附 Go 侧理由** ⇒ **无缺口**。⚠️ **域侧能力不该被推给共享层**：若日后确有残留，落点在 `service/article/article-api/**` 的 DTO 层 ＝ **域侧写权** ⇒ 按 C3「域侧能自修的自修」 | — | **不派单**（详见 §1 对应行） |
| **G4-2** | 🔴 **MyBatis-Plus 自动收集 `Interceptor[]` Bean 的机制** —— 全仓**无任何测试**证明 `@Import` 的 `DeadlineQueryTimeoutInjector` 真进了 MyBatis-Plus 执行链 | `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java` ＋ MyBatis-Plus 自动配置 | **实测缺口**：`CommentSqlDeadlinePrecisionGateTest.java:130-142` 走的是**手工** `new Configuration()` ＋ `configuration.addInterceptor(new DeadlineQueryTimeoutInjector())` ⇒ **绕开 Spring 与 MyBatis-Plus 自动配置**；`FavoriteSqlTimeoutProductionPathTest.java:161` 也是手工 `addInterceptor(PROBE)`（PROBE 是测试自己的探针）⇒ **两条测试都证明不了「`@Import` 的 Bean 被自动收集」**。⇒ 建议：在**共享层**补一条**真 Spring 上下文 + 真 mapper + 真 PG** 的测试，一次性为 10 个 rpc 域钉死该机制（AGENTS.md §3.3.3：注解存在 ≠ 机制生效） |
| **G4-3** | 🟡 **正式派单（单 3）** `S-6` 的 `SqlTimeoutStatusTranslator` javadoc **「Go 出处」段归因错误** ⇒ 该段以「Go 出处」措辞断言 Go 行为却**零 `文件:行号`**（命中 AGENTS.md §3.3.2） | `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java:31-36`（「Go 出处」段） | **现象**：该段写「Go 侧 ctx 到点后…**经 go-zero 的错误映射**成为「服务繁忙」1015」⇒ 🟡 **结论对、但归因措辞错** —— 实测 Go 侧 **1015 既不是框架映射、也不是 deadline 映射**，而是**各 api logic 显式 `switch st.Code()` 的 `default` 分支**产出（`IsTimeoutError` 只做可观测性判定、**不产码**）。<br>**Go 侧出处（行号已逐条核验真实存在 · 2026-10-27）**：① `service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48`（`switch st.Code()`(44) / `case codes.Internal:`(45)→`ErrorServerCommon`(46) / `default:`(47)→`errmsg.CodeServerBusy`(48)）＋ 同族 **13** 个 admin logic ＋ `service/user/user/api/internal/logic/user/selectavatarlogic.go:60`；② `service/user/common/errmsg/errmsg.go:6`（`CodeServerBusy = 1015`）＋ `:41`（`"服务繁忙"`）—— ⚠️ 路径是 **`errmsg/errmsg.go`（目录）**，不是 `errmsg.go`；③ `service/common/observability/observability.go:146-158`（`IsTimeoutError` 是 Go 侧**唯一** `errors.Is(err, context.DeadlineExceeded)` 的公共处理点（`:150`/`:153`/`:157`），**只做可观测性判定、不产码**）。<br>**RAG 侧（已核）**：`service/users/admin/admin-api/.../advice/GlobalExceptionHandler.java:83`（`mapRPCStatusToCode` 声明）＋ **`:90`**（default→1015）；`service/users/user/user-api/.../advice/GlobalExceptionHandler.java:153` ＋ **`:162`**。<br>**建议修法**：① 「Go 出处」段**逐条补上上述 `文件:行号`**（指向**消费点**而非声明点）；② **改正归因措辞**（1015 来自 **api logic 的 `switch st.Code()` default 分支**，不是「go-zero 的错误映射」）；③ **保留原结论**（`DEADLINE_EXCEEDED` 未被单列 ⇒ 落 default ⇒ 1015 ⇒ **api 层无需改动**）—— 该结论经上述两侧行号**证实成立**。<br>⚠️ **本域侧复核：article 域不成立**（`ArticleServiceGrpcImpl.java:127` 的 `catch (StatusRuntimeException e)` **在前**、`:147` 的 `catch (RuntimeException e)` **在后** ⇒ `DEADLINE_EXCEEDED` 不会被二次抹掉）⇒ **不要**把它算成 article 域的漂移。**纯 javadoc ⇒ 零字节码变化** |
| **G4-4** | 🟡 **A9 轴定义的措辞缺陷**（共享面 `_cross`） | `docs/DRIFT/_cross/domain-common-lanes.md` 的 A9 行 | 轴定义写「span 属性 **`audit.*`**」，实测：**共享层用 `sea.*`**（`ObservabilityConstants.java:57-116`：`sea.error.kind`/`sea.biz_code`/`sea.biz_msg`/`sea.timeout`/`sea.slow`/`sea.duration_ms`）；**`audit.*` 只存在于 users/admin 域**；**favorite 域用 `biz.*`** ⇒ **轴定义应按域区分**（共享层=`sea.*` · admin=`audit.*` · favorite=`biz.*`）⇒ **共享面文件，G3 不写** ⇒ 已请 G4 登记进 `global/vs-common.md` 的建议项段 |
| **G4-5** | 🟡 **AGENTS.md §6.3 问题 4 的记载不完整** | `AGENTS.md` §6.3（问题 4）＋ §3.5 | 该处只点名「**`favorite-rpc` 例外**（缺 `baseline-version: 0`）」⇒ **实测漏了 `article-rpc`**（同样 `baseline-on-migrate: true` ＋ 无 `baseline-version`，`article-rpc/.../application.yml:53-57`）⇒ 共享面文件 ⇒ **合并收口执行者订正**<br>**全仓 9 个 rpc 组件普查（核对日期 2026-10-27 · G4 扫的 9 个，我独立复扫逐条吻合）**：**已钉 `baseline-version: 0`（5）** = `comment-rpc` · `follow-rpc` · `like-rpc` · `message-rpc` · `task-rpc`｜**有 flyway 段但缺（2）** = **`article-rpc`** · **`favorite-rpc`** ← **真正的缺陷面**｜**N/A · 无 flyway 段（3）** = `hot-rpc` · `admin-rpc` · `user-rpc`（后者配置文件名是 **`application.yaml`** 不是 `.yml`）⇒ **我先前那张表只列了 7 个 ⇒ 漏了 `admin-rpc` 与 `user-rpc`；但两者都是 N/A ⇒ 不影响结论** |
| **G4-6** | 🔴 **`GoStyleGrpcClientBreakerInterceptor` 在 rpc 侧无装配路径** ⇒ `article-rpc` 出向调用无熔断（**本轮补记**） | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:103-106` ＋ `GoStyleProtectionConfiguration.java:51-53` | **现象**：该拦截器是 `@Component` ＋ `@GrpcGlobalClientInterceptor` ⇒ **只能组件扫描**；`article-rpc` **刻意不扫 `com.rag.common`**（`ArticleRpcApplication.java` 的 `@SpringBootApplication` **无** `scanBasePackages`；javadoc 里出现该词只是**在说明为什么不能用它**）⇒ 该 client 侧能力**不在容器里** ⇒ **出向调用无熔断**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）。⚠️ **本域确实有出向调用**：`article-rpc` 实测 **`@GrpcClient` = 5**（`client/SecurityRpcClient.java` / `MessageRpcClient.java` / `ContentSecurityPort.java` / `ImageSecurityRpcClient.java` 等）⇒ 缺口**可观测**。**建议**：共享层提供 **rpc 进程可用的装配入口** + 一条「rpc 侧出向调用确实经过 breaker」的**装配层**测试（§3.3.3：注解存在 ≠ 机制生效）。⚠️ **修法可行性已独立核准**：选项「改为可被 `@Import` 引入」要改 **10 个域侧 Application 类** ⇒ **越 G4 写权** ⇒ 只有「由 `RpcServerInterceptorFactory` 一并产出 client 侧拦截器」**落点 100% 在 `service/common`**（实测 **10/10 rpc 组件已 `@Import` `RpcServerInterceptorFactory`**）⇒ **本条已按全域共性正式派单给 G4（单 2）**，article 域**不单独重复排期**。**影响面**：G3 域内 **3/4**（article/comment/favorite 真实命中；hot-rpc 无出向 ⇒ N/A）。**是否阻塞本波**：否 |

| **G4-7** | 🟡 **`flyway-concurrent-verify.py` 的 `KNOWN_BLOCKED` 只登记了 `favorite`，同形的 `article-rpc` 没登记** | `tools/difftest/flyway-concurrent-verify.py:48-51`（`KNOWN_BLOCKED` 表）＋ `:93-100`（`baseline_version` 缺省 `"1"`） | **现象**：该脚本对**所有**模块都算得出 `baseline_version`（缺省 `"1"`）⇒ **它看得见 `article-rpc` 的 `baseline=1`**，只是**已知缺陷账里没这条** ⇒ ⇒ **只补账、不改代码**（两个 yaml 的修法都在**域侧**：`article-rpc` / `favorite-rpc` 各加一行 `baseline-version: 0`）⇒ ⚠️ **与 G4-5（`AGENTS.md` §6.3 漏记 `article-rpc`）需同步订正**（两处说的是同一件事）<br>⚠️ **落点是 `tools/difftest/**` = 共享面 ⇒ 不在 G3 写权面 ⇒ G3 只登记** ⇒ 由 G4 / 合并收口执行者落 |
> ⚠️ **单 1 的含义订正（2026-10-27 · 复核 G4 落码时查出，重要）** ——
> 本条说的「**无任何测试证明**」**仍然成立**；但**机制本身在生产是通的**，别读成「生产上已失效」：
> - **生产早有修法**：MP 3.5.17 的 `MybatisPlusAutoConfiguration` 声明 `@AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)` —— 那是 **Boot 3** 的包名；**Boot 4.0.8** 已把该类搬到 **`org.springframework.boot.jdbc.autoconfigure`**（在 `spring-boot-jdbc-4.0.8.jar`）⇒ **排序引用静默悬空** ⇒ MP 被排到 DataSource 之前求值 ⇒ `@ConditionalOnSingleCandidate(DataSource.class)` 找不到 DataSource ⇒ **MP 退避、不产 `SqlSessionFactory`**。
> - **域侧已用排序桥补上**：**9/9 rpc 组件**都有 `MybatisPlusOrderingBridgeAutoConfiguration`（空类，**只为排序而存在**：`@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = MybatisPlusAutoConfiguration.class)`）＋ 每个组件自己的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里注册一行 ⇒ 权威条目 **`docs/DRIFT/like/rpc.md:703` `DRIFT-LIKE-014`**（2026-10-23 已登记并已修）。<br>📌 **订正（2026-10-27 · G3）**：**「9/9」这个数已变** —— G3 本轮删了**自己三域**的域侧桥（article-rpc / comment-rpc / favorite-rpc）⇒ ⇒ **全仓现存 0 座域侧桥**（**2026-10-31 · G3 第十九轮实测**：`git ls-tree -r do-my-own-gh --name-only` ＋ `grep -i orderingbridge` ⇒ **只剩共享层那 1 座**；全仓 `.imports` 里排序桥注册也**只剩共享层那 1 行** ⇒ ⇒ 先前记的 6 座已由各自域 lane 删净）⇒ ⇒ **排序桥的唯一来源 = 共享层那座**（`service/common/.../persistence/MybatisPlusOrderingBridgeAutoConfiguration`）⇒ ⇒ 「域侧重复」这个**附带发现**已**完全消解**
> - ⇒ **所以缺的只是「把这条链钉死的运行证据」**：现有域侧测试只断言「桥**已注册**」（`ArticleSpringContextWiringTest` / `FollowRpcWiringTest` / `LikeRpcSpringContextWiringTest`），**没有一条断言「`@Import` 的 interceptor 真进了 MP 执行链」** ⇒ **单 1 仍成立、仍是阻塞项**。
> - ⚠️ **⇒ 守门测试必须复刻那座桥**（否则测的是「没有桥的装配」⇒ **假阴性**：实测 G4 的探针在缺桥时 3/3 error，根因原文＝ `MybatisPlusAutoConfiguration: Did not match: @ConditionalOnSingleCandidate (types: javax.sql.DataSource) did not find any beans`）⇒ 修法 = 测试作用域放一座**等价**的空桥 **＋ 在测试资源的 `AutoConfiguration.imports` 里注册它**（**光有类不注册 ⇒ 排序边不建立**）。
> - ✅ **附带发现已消解（2026-10-31 · G3 第十九轮实测）**：桥**已在共享层**（`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4` ＋ 本体 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java`）⇒ ⇒ **「9 个域各写一份」的域侧重复已归零**（全仓 `OrderingBridge` 文件只剩共享层那 1 个；全仓 `.imports` 里的注册也只剩共享层那 1 行）⇒ ⇒ 原写的「需 Jason 排期」**已不需要**（排序桥下沉已实际完成）。

| **G4-8** | ✅ **单 4（排序桥下沉共享层）—— 第 1 步 ＋ 第 2 步**（Jason 2026-10-27 拍板「排序桥要下沉共享层（跨 9 域写权）」）| **第 1 步（共享层 · G4 写权）** = `service/common/src/main/java/com/rag/common/persistence/`（新类）＋ `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（加一行注册）｜**第 2 步（本域侧 · G3 写权）** = 删本域 rpc 组件的 `MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ 删它自己 `imports` 里的注册行 | **① 技术方案（四个前提已实测）**：桥用 **`@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")`** ⇒ **必须用 Name 形式**：① Boot 4.0.8 的 `@AutoConfiguration` **仍有 `beforeName`/`afterName` 属性**（javap 实测）② `service/common` 编译期**只有 mybatis 本体**（3b 段明令禁 MP）⇒ 用类字面量就得加 MP 依赖 ⇒ **违反 3b 段**；用 Name 形式 ⇒ **零新依赖**③ **20 个模块**（10 api + 10 rpc）都依赖 `service/common` ⇒ 其 `imports` 会被 Spring 合并加载 ⇒ **共享层一处 ⇒ 全域生效**④ 共享层已有 `@AutoConfiguration` 先例（`GoStyleOtelAutoConfiguration`）⇒ 组件扫描重复注册的疑虑有仓内实证兜底<br>**② 规避 AGENTS.md §5.3c 的「同一次提交」约束（关键）**：**第 1 步与第 2 步可分离** —— 9 座域侧桥与共享层桥**同向**（都是 `after=DataSource, before=MP`）⇒ **并存时排序图多一个同向节点、无环、功能等价** ⇒ ⇒ **第 1 步单独合入即见效且可独立验证**（零域侧改动、零风险）⇒ **第 2 步各域纯删减、可各自独立提交** ⇒ **不需要跨树同一次提交** ⇒ **合并顺序：先 G4 共享层 ⇒ 再各域删**（任一中间态都可用）<br>**③ 影响面（2026-10-27 订正）**：**实际只有 9 个组件会真正带上这条排序边** —— 精确数：**全仓有 `mybatis-plus` 依赖的组件恰好就是那 9 个有域侧桥的 rpc**（article · comment · favorite · follow · like · message · task · users-user · users-admin）⇒ **其余 11 个（10 个 api ＋ `hot-rpc`）classpath 上没有 MP** ⇒ `beforeName` 的目标类不在 classpath ⇒ **排序边自动被忽略 ⇒ 完全无影响**（实测：5 个 api 模块的 `mybatis-plus` 依赖数 = **0**）⇒ ⇒ **那 9 个 rpc 各自已有同向的域侧桥 ⇒ 共存时排序图多一个同向节点、无环、功能等价 ⇒ 放主 jar ⇒ 无行为变更**（⇒ **G4 在回执里担心的「主 jar 会波及 16 个消费者的 MP 自动配置启用状态」不成立**：既不是 16 个（是 9 个），也不会改变启用状态（那 9 个已各自有桥））⇒ 9 座域侧桥随之成为冗余<br>**③-bis ⚠️ 两条桥不是同一件事（别合并错）**：**单 1 的探针桥**在 `service/common` 的 **test scope**（探针自用：它在 service/common 里，**看不到**域侧那些桥）｜**单 4 的共享层桥**在 **主 jar**（给 9 个 rpc 用）⇒ **两条都要做、不冲突**；且**单 4 先落** ⇒ 单 1 的探针**可直接复用主 jar 那座**（只需在测试资源注册一行）<br>**④ 是否阻塞本波**：**是**（它与单 1 同源 —— 单 1 的守门要证明「链真通」，而链的排序前提就是这座桥）<br>⚠️ **验收口径（G1 补的一条，G3 采纳）**：共享层桥在 `service/common` ⇒ **20 个依赖它的模块都会带上它**（10 api + 10 rpc），而域侧桥只有 **9 个 rpc** 有 ⇒ **下沉后 10 个 api 模块也会多出这条排序边** ⇒ 按「`beforeName`/`afterName` 目标不在 classpath 时排序边自动忽略」**应无影响**，但**这正是 G4 必须实测的点** ⇒ **回执里必须明确给出「api 侧带上这条边后仍能正常启动」的实测**，不能只验 rpc<br>✅ **2026-10-31 · G3 第十九轮实测：两步都已完成** —— **第 1 步（共享层）**：`service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` **在主干** ＋ 已注册 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4`｜**第 2 步（本域侧 · G3 写权）**：本域 `article-rpc` 的域侧桥 **2026-10-27 G3 已落码**（见本文件 `L-6` 行）｜**全仓收口已完成**：`git ls-tree -r do-my-own-gh --name-only` ＋ `grep -i orderingbridge` ⇒ **只剩共享层那 1 座**；全仓 `.imports` 里的排序桥注册也**只剩共享层那 1 行** ⇒ ⇒ **9 域写权的收口已完成**｜⚠️ **G1 补的验收口径（本轮实测满足）**：「api 侧带上这条边后仍能正常启动」⇒ `mvn -o -pl service/article/article-api -am test` ⇒ **216 例 / 0F / 0E / 0S**（含真 Spring 上下文）＋ api 侧 classpath **0 处 `mybatis-plus`** ⇒ 排序边因目标类不在 classpath 而**自动被忽略** ⇒ 无影响 |


| **G4-9** | 🔴 **跨域耦合矩阵的 `common_radiation` 快照过期**（本轮 L-10 落码带来的**真实新共享层依赖** ⇒ 只更新快照，不改代码） | `docs/DRIFT/_cross/cross-domain-coupling.md` 的 `common_radiation` 段（**共享面 ⇒ G3 不写**）⇒ 守门 `tools/difftest/check-wave-coupling.py` | **现象**：`article-rpc` 本轮接了共享层 `MqConsumer`（2 个 consumer span ⇒ `MqConsumer.traceConsumer`）⇒ ⇒ `service/common` 里 **`MqConsumer` 这一类**的跨域引用域从 `['comment','hot','task']` 变成 `['article','comment','hot','task']` ⇒ ⇒ 守门红（`❌ common_radiation 漂移 · MqConsumer`）⇒ ⇒ **这不是缺陷，是依赖拓扑的真变化**（且方向是**向 Go 靠**：Go 侧 article-rpc 的两个 consumer 本来就用共享层那座 `TraceConsumer`）⇒ ⇒ **只更新文档快照**（跑 `python3 tools/difftest/check-wave-coupling.py --print` 取新值）⇒ **不改代码、不改守门**。<br>**Go 出处**：`service/common/observability/mq.go:12-27` 的 `TraceConsumer` ⇒ Go 侧 `internal/mqs/article_consumer.go:45` ＋ `internal/mqs/article_sync_result_consumer.go:40` **本来就调它** ⇒ ⇒ 本次落码 ＝ **把 RAG 补到与 Go 一致**，不是新增架构耦合。<br>**RAG 侧现状**：`service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleReviewConsumer.java:9` ＋ `ArticleSyncResultConsumer.java:8`（两处 `import com.rag.common.observability.MqConsumer;`）<br>**证据强度**：**L4**（守门实测红：`python3 tools/difftest/check-wave-coupling.py` ⇒ 恰好 **1** 项漂移）<br>**🔴 影响面比本条标题更大（2026-10-28 补正）**：**不止我这一条** —— G1 lane 在其树（已并入 G4 尖端）实测到**另一条**同类漂移：`GoStyleRedisClientAutoConfiguration` 的辐射度快照过期 ⇒ ⇒ ⇒ **两棵树各红 1 条、但红在不同类上** ⇒ ⇒ **只补我这一条不够** ⇒ ⇒ **修法必须是「跑 `--print` 取**全量**快照 ⇒ 一次性补齐」**（合并后的树会把两条一起暴露出来）⇒ ⇒ **波末收口前必须做，否则收口门禁红**。<br>**本域侧复核**：另一条（`GoStyleRedisClientAutoConfiguration`）**与本轮落码无关** ⇒ 它是**先前就存在**的快照过期 ⇒ ⇒ 两条**互不覆盖**、都要补。<br>**附带发现（守门口径）**：该守门按 **import 语句**判定（`check-wave-coupling.py:168` / `:190`）⇒ ⇒ **全限定名（FQN）的用法它看不见** —— 例如既有的 `ArticleDomainServiceSemanticsTest` 用 FQN 调 `SnowflakeProperties` ⇒ **不计** ⇒ ⇒ **矩阵的辐射度是「import 边」不是「真实引用边」** ⇒ ⇒ 本轮我因此把守门测试里的 `SnowflakeProperties` import 改成 **mock `SnowflakeBean`**（顺带让 `article_id` / `file_id` 的断言变成**精确值**而不是「> 0」）⇒ 那条边就没了 ⇒ ⇒ **本域只引入 1 条（`MqConsumer`）** |

| **G4-10** | 🔴 **共享脚本 `check-surface-parity.py` 在工作树里指错 Go 真源 ⇒ 假红 / 假绿**（**跨 G1~G4 全域** · 2026-10-29 · G3 实测） | **现象**：在**工作树**（`../RAG.worktrees/<lane>`）里跑 `python3 tools/difftest/check-surface-parity.py --domain hot` ⇒ 报 `[hot] 🔴 Go routes.go 不存在: …/RAG.worktrees/Sea-RideTheWind_golang/service/hot/api/internal/handler/routes.go` ⇒ **EXIT=1**（**假红**）；且脚本继续跑出「**合计：HTTP 端点 Go=0 / RAG=0 · gRPC 方法 Go=0 / RAG=0**」⇒ ⇒ **它把「找不到 Go 仓」当成了「Go 侧 0 个端点」** ⇒ ⇒ 对**能读到 routes.go 的域**就会变成 **0 vs 0 的「逐条相等」⇒ 假绿**。<br>**Go 侧 `文件:行号`**：**不适用**（这是 RAG 侧**共享脚本**的缺陷，不是 Go 行为差异）<br>**RAG 侧现状**：`tools/difftest/check-surface-parity.py:69-70` —— `GO_REPO = os.environ.get("GO_REPO", os.path.join(os.path.dirname(REPO), "Sea-RideTheWind_golang"))` ⇒ `REPO` = 工作树 ⇒ `dirname(REPO)` = `…/java&golang/RAG.worktrees` ⇒ ⇒ 默认路径恒指错（**主工作区**才对）<br>**证据强度**：**L4**（G3 工作树内实测：`hot` EXIT=1 + 报「routes.go 不存在」；**显式带 `GO_REPO=/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` 后 EXIT=0**，且 **HTTP Go=1 ≡ RAG=1**（Go 侧 JWT 保护 **0** 条 ⇒ hot-api 两侧都匿名）· **gRPC 方法 Go=5 ≡ RAG=5**）⇒ ⇒ **同一份代码，两种跑法，两个结论**<br>**建议修法**：① **找不到 Go 仓时显式报错退出**（不得当成「Go 侧 0 个端点」继续比）；② 默认路径改为**向上搜索** `Sea-RideTheWind_golang`（主工作区 = `../Sea-RideTheWind_golang`，工作树 = `../../Sea-RideTheWind_golang` ⇒ 两种形态都能命中）；③ `docs/PhaseGoal/g{1..4}.md §5` 的收口命令要注明带 `GO_REPO`（**否则收口跑法本身产出假结论**）<br>**影响面**：**G1~G4 全域**的 X-8（HTTP 契约面）/ X-9（gRPC 方法集）判定命令<br>**是否阻塞本波**：**否**（G3 已用显式 `GO_REPO` 取得四域真结论：article HTTP 7≡7 + gRPC 6≡6 · comment 7≡7 + 7≡7 · favorite 7≡7 + 7≡7 · hot 1≡1 + 5≡5，**全 EXIT=0**）⇒ ⇒ **阻塞的是「不带 `GO_REPO` 的收口跑法」**，不是本域结论 |
| **G4-11** | 🟡 **article 域「双侧活体 0 条」的**受阻前提**已过期**（基线 §九 **G-3**）⇒ 从「环境受阻」降为「**可做但本轮未做**」 | **现象**：基线 §九 G-3 写「Go 侧需 `etcd:2379`（容器在跑但**宿主端口未映射**）⇒ 0 条」⇒ **2026-10-29 实测该前提已不成立**：`docker ps` ⇒ `Sea-etcd \| 0.0.0.0:49319->2379/tcp` ⇒ **宿主端口已映射** ⇒ ⇒ 「可解除」的条件**已经具备**<br>**Go 侧 `文件:行号`**：`service/article/api/etc/article-api.yaml:9-11`（`ArticleRpcConf.Etcd.Hosts: [etcd:2379]`）+ `:14-24`（`SecurityRpcConf` / `UserRpcConf` 同样指向 `etcd:2379`）⇒ ⇒ Go 侧 api 启动**依赖 etcd 可达**（⚠️ **2026-10-29 订正**：原写「`zrpc.MustNewClient` ⇒ 不可达即启动 panic」**与源码矛盾** —— go-zero v1.10.2 `zrpc/config.go:30` `NonBlock bool json:",default=true"` ⇒ `zrpc/client.go:65-69` `WithNonBlock()` ⇒ `zrpc/internal/client.go:78-80` **不加** `grpc.WithBlock()` ⇒ **客户端 etcd 不可达只在「调用时」失败，不会在启动时 panic**；**会 panic 的是服务端**：`zrpc/server.go:42` `NewRpcPubServer` → `zrpc/internal/rpcpubserver.go:32-33` `discov.NewPublisher(...).KeepAlive()`（**先注册 etcd 再 bind 端口**）⇒ 注册失败 ⇒ `zrpc/server.go:89-92` `panic(err)`）⇒ ⇒ 起 Go 侧仍**必须先解决主机名**（`etcd:2379` 宿主解析不到 ⇒ 调用全失败）：用**副本配置**把 `etcd:2379` 改成 `127.0.0.1:49319`（**冻结的 Go 仓一个字都不能改**）<br>**RAG 侧现状**：本域 A1~A9 全轴结论**最高只到 L3/L4-装配面**，**没有一条双侧活体证据**；基线 §一 A-2 的 `article-api` = **R3**、`article-rpc` = **R2**（E3）⇒ ⇒ 差的就是这一块<br>**证据强度**：**L3**（环境侧实测 `docker ps` 端口映射在）＋ **L1**（Go 侧配置逐行）⇒ ⇒ **未取证**（不是「已对齐」，是「还没做」）<br>🔴 **2026-10-29 订正（探针实跑后）**：上一句「**没有一条双侧活体证据**」**已过期** ⇒ 本轮**已取到 3 条 L4 双侧活体证据**（`GET /v1/articles` 200 · `GET /v1/article/9007199254740993` 5001 · `GET /v1/article/does-not-exist` 1002 ⇒ **两侧逐字节一致**）⇒ ⇒ **「部分闭合」，不是「全闭合」**（`judge` §九 G-3 原文要求「article 域双侧活体 **0 条**」⇒ 现 3 条 ⇒ 未达全量；且**成功路径只覆盖公开列表端点** —— 宿主上 `user.rpc` **两侧都不可达** ⇒ 单条详情端点两侧**同样降级成 5001** ⇒ 该用例钉的是「**降级路径两侧一致**」，不是「取到文章」）<br>**建议修法**：起 Go 侧 article-api（副本配置 + `127.0.0.1:49319`）＋ 起 RAG article-api ⇒ 跑**失败路径矩阵**（article 域失败路径最密：下载/上传/审核/`S-5`）⇒ **落点两可**：域内 JUnit（G3 可写；先例 `service/favorite/favorite-rpc/src/test/java/…/proto/FavoriteProtoWireParityTest.java`＝「JUnit 版双侧活体」· 基线 §六 6.1 已把它列为真·双侧活体证据）**或** `tools/**`（**共享面 ⇒ G3 只登记**）⇒ ⇒ **本轮 G3 已做（2026-10-29）** ⇒ 落点 = `tools/api-full-parity-probe/article/`（**G3 真私有目录** ⇒ 按 AGENTS.md §5.3f「共享判据 = 依赖度数，不是目录位置」⇒ G3 可写）⇒ **部分闭合（3 条）**：见本文件 §6 的 A-1 行 ＋ `docs/DRIFT/article/api.md` §8.3 第 1 项<br>**影响面**：article 域 R3→R4 的最后一块 ＋ P0 #1 / P0 #4<br>**是否阻塞本波**：**否**（基线 G-3 自己标「不属『环境不可得』」） |
| **G4-12** | 🟡 **基线 `docs/PhaseGoal-judge.md` 有三处归类/口径需订正**（**共享面**：它是 4 条 lane 矩阵的**唯一数据源** ⇒ G3 不写 ⇒ 交合并收口执行者） | **现象**：① **§十一 D-07** 把 `comment` 域的 `report_record` 归进「🔴 **真实实现缺陷**（必须修）」并标「**未签字**」⇒ ⇒ **与既有签字偏离矛盾**（`docs/Intentional_Deviations/comment/rpc.md` 的 `SD-2026-10-24-CAND-02`，2026-10-24 用户 Jason 签字）⇒ ⇒ **决策不是缺陷**；且 D-07 写的「**2 条**（`user_id` / `target_type`）」**计数也有误** —— 机器实测是**整张表 8 列**（见 `docs/DRIFT/comment/rpc.md` 的 2026-10-29 补记）。② **§九 G-13 + §七 P0 #4** 写「`article` 域**没有失败路径探针**」⇒ ⇒ **口径漏统计**：article 的失败路径覆盖在**域内 JUnit**（`ArticleApiMvcPipelineTest` ~40 例 + `ArticleApiFormParamLimitTest` 8 例 + `ArticleApiRouteAuthBoundaryTest` ~20 例 + `ArticleApiAssemblyWiringTest` ~14 例 + `ArticleApiJsonBodyLimitCaseTest` 4 例 + `ArticleApiJwtVerificationScopeTest` ~8 例，**真 Tomcat · 400 text/plain 逐字断言**）⇒ ⇒ 而 §六 6.1 的「真·双侧活体」表**只统计 `tools/**` 脚本** ⇒ ⇒ **域内 JUnit 被漏掉** ⇒ ⇒ 「域覆盖不全」的**真正缺口是「双侧活体」，不是「失败路径用例」**。③ **§九 G-18**（`hot-proto` 判定记录位置不统一）**已由 G3 本轮在 `docs/DRIFT/hot/proto.md` 落位**（见该文件 §4）⇒ ⇒ 只需把 §九 G-18 行改成「已闭合 + 落点」<br>**Go 侧 `文件:行号`**：**不适用**（这是**基线文件**的归类问题，非 Go 行为差异）<br>**RAG 侧现状**：`docs/PhaseGoal-judge.md:351`（P0 #3 行提到「comment 2 条仅 RAG 有（未签字）」）· `:352`（P0 #4 行）· `:443`（G-13）· `:447`（G-18）· `:499`（D-07）<br>**证据强度**：**L4**（机器：`python3 tools/difftest/check-ddl-static.py --domain comment --indexes` ⇒ EXIT=2 且逐条列出 8 列；域内 JUnit 计数见上）<br>**建议修法**：改这 3 处 ⇒ **重跑** `python3 tools/difftest/check-phasegoal-lanes.py --write`（4 条 lane 矩阵是机器生成的 ⇒ 改基线必须重跑生成器）＋ `python3 tools/difftest/check-phasegoal-consistency.py`（基线自检）<br>**影响面**：4 条 lane 的**分摊矩阵**（P0 汇总 + 缺口清单）<br>**是否阻塞本波**：**否**（G3 本域结论已用**自取证据**取得，不依赖这三处的措辞） |

### 4.1 §4 派单闭合状态（2026-10-27 · G4 第七次回执 4 项规格）

| 本域派单 | G4 回执对应 | 状态 | 证据 |
|---|---|---|---|
| **G4-2**（MyBatis-Plus 自动收集 `Interceptor[]` Bean） | **单 1** | ✅ **已闭合**（2026-10-27） | G4 树 commit `e3f2046`「test(common): 钉死『MyBatis-Plus 自动收集 Interceptor[] Bean』这条机制（**G3 单 1**）」⇒ 新增 `service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java`（**3 条 · 三层加码**）：① **收集层**（`MybatisPlusAutoConfiguration.interceptors` 里确有本拦截器）② **装载层**（`SqlSessionFactory.getConfiguration().getInterceptors()` 里确有本拦截器）③ **行为层（真 PG ＋ 真 mapper）**：绑一个已过期 deadline ⇒ 查询以 `57014` / `DEADLINE_EXCEEDED` 失败（前置：无 deadline 时同一查询**必须成功** ⇒ 失败才可归因于拦截器）⇒ **3/3 绿（实测）**。⚠️ **我域原登记的「全仓无任何测试证明」在登记时成立、现已由该守门闭合** ⇒ **保留原登记不改写**，此处加注记。⚠️ **该 commit 未进本树**（`git merge-base --is-ancestor` = 否）⇒ 本域要自己复跑该守门时**需先合入共享层**。 |
| **G4-3**（`SqlTimeoutStatusTranslator` javadoc「Go 出处」段归因错误） | **单 3** | ✅ **已闭合**（2026-10-27） | G4 树 commit `2e2a84f`「docs(common): 订正 …归因 ＋ 补齐 `文件:行号`（**G3 单 3**）」⇒ **纯 javadoc ⇒ 零字节码变化** ⇒ ① 「Go 出处」段逐条补上 `文件:行号`（`getuserlistlogic.go:44-48` 等）② 归因措辞改正为「1015 来自 **各 api logic 显式 `switch st.Code()` 的 `default` 分支**」（`IsTimeoutError` 只做可观测性判定、**不产码**）③ 保留原结论。⚠️ **G4 补的边界与本域直接相关**：「`DEADLINE_EXCEEDED` 未被单列 ⇒ 落 default ⇒ 1015」**只对 users 域成立** ⇒ **Go 侧 article 域单列了** `codes.DeadlineExceeded`（`service/article/api/internal/logic/article/create_article_logic.go:66` ⇒ `errmsg.ErrorArticlePublishing` = **1003**，**不走 1015**）⇒ ⇒ **不适用于 article 域** ⇒ ⇒ 与本域两条**独立**结论一致（本文件 **S-6 行** ＋ **G4-3 行「本域侧复核：article 域不成立」**）⇒ **两侧不冲突**。 |
| **G4-6**（client breaker 在 rpc 侧无装配路径） | **单 2** | ✅ **已闭合**（2026-10-27） | G4 回执：「**client breaker 进共享层**」⇒ **本域零改动**（落点全在 `service/common`）。⚠️ **影响面口径（不得外推）**：G4 逐个 rpc 自核（`@GrpcClient` 计数 ＋ **六种出向方式**交叉核）后给出**全局**口径 = **6/10 可观测 ＋ 4/10 N/A**（N/A = `hot-rpc` · `task-rpc` · `user-rpc` · `admin-rpc`）⇒ ⇒ **本域面 = **3/4**（`article-rpc` `@GrpcClient` = 5 · `comment-rpc` = 2 · `favorite-rpc` = 2 可观测；`hot-rpc` = 0 ⇒ N/A）** ⇒ **「3/4」只覆盖本域四个 rpc 组件** ⇒ **不得被读成全局口径**。 |


> ℹ️ **口径说明**：上表的 ✅ 指 **「G4 的回执给了该单的落码 commit ＋ 验证口径」** ⇒ **不等于「本域已复验」** ⇒ 本域复验的前置 = **G4 的共享层 commit 进本树**（本树 HEAD 暂无 `2e2a84f` / `e3f2046` / `a1a2e05` ⇒ `git merge-base --is-ancestor` 全部为否）。
> ⚠️ **G4 回执给的是第七次全量**（`5564c27` / 2457 条）⇒ 本域台账已按**第八次**（`23d1c2a` / 2461 条）回填（**加注记、不覆写原值**）。
## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）—— ✅ **2026-10-27 阶段 2 已完成**：`ArticleRpcRuntimeBootGateTest`（**真 fat jar 启动**，`ArticleRpcRuntimeBootGateTest` 1/1 绿，耗时 10.81s）＋ `mvn -o -pl service/article/article-rpc,... -am test` ⇒ **article-rpc 155 条 / 0 failures / 0 errors / 0 skipped**（**带 `-am`**，共享层从 reactor 构建 ⇒ 见下方「本轮踩坑」）。
  - ⚠️ **别把 DDL 对照当启动证据**：`tools/difftest/check-ddl-static.py --domain article` 是**静态**逐列比对 SQL 文件 ⇒ 它给 A8 的 **DDL 列级一致性**提供 L4 级**双侧**证据，但**不启任何进程** ⇒ **它不能替代启动证据**（本项靠 `ArticleRpcRuntimeBootGateTest` 闭合）。
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（**2026-10-27**）
- [x] **两侧同时跑**的探针（AGENTS.md §3.3）—— ✅ `tools/errmsg-probe/run-parity.sh` **EXIT=0**（A4 错误响应 ＋ A6 错误码契约 ⇒ **L4**）：Go 侧 sha256 == Java 侧 sha256 == `cad5cb4f…d1134`，**49 行**（CONST 15 ＋ TABLE 15 ＋ FALLBACK 19），真源 sha256 校验通过 ⇒ 只跑 RAG 的探针**不算数**这一条**已满足**
- [x] 🆕 **A-1 双侧活体对拍探针**（2026-10-29 · G3）—— ✅ **落码并跑通**：`tools/api-full-parity-probe/article/`（`run.sh` + `mutate-parity.sh` + `expected.txt` + `README.md`）⇒ **Go 真源栈与 RAG 栈同时起服务** ⇒ 同一请求 ⇒ **逐字节**比对 ⇒ **3/3 一致**（`GET /v1/articles` 200 · `GET /v1/article/9007199254740993` 5001 · `GET /v1/article/does-not-exist` 1002）⇒ **L4 双侧活体证据**（**不是**只跑 RAG 的探针）
  - ⚠️ **真源全程只读**：Go 侧用真源**二进制** + **配置副本**（把 yaml 里写死的 `etcd:2379` / `postgres` / `kafka:9092` / `minio:9000` / `jaeger:4317` 改成宿主可达地址）⇒ 脚本第 3 步**断言** `git status --porcelain -- service/article` 干净
  - ⚠️ **两侧各自独立 Postgres**：Go 侧 = 真源 `admin/first_db` + 真源 `article.sql` + **必须先跑** `002_community_revision.sql`（否则真源 rpc 启动即 fatal）；RAG 侧 = 它自己的 Flyway 建表 ⇒ 不互相污染
  - ⚠️ **不是全闭合**：成功路径只覆盖公开列表端点（`user.rpc` **两侧都不可达** ⇒ 单条详情端点两侧**同样降级 5001**）⇒ 见 §6 的 A-1 行
  - 🔴 **探针副产品**：实跑才暴露 **2 个新缺陷并已修** ⇒ `DRIFT-ARTICLE-API-011`（yaml 少 `auth:` ⇒ **组件起不来**）· `DRIFT-ARTICLE-API-012`（JSON 字段序漂移）
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— ✅ 订正 3 条：**必填字段校验**（**编号撤下**：原挂 `DRIFT-ARTICLE-API-006` ＝ 权威面另一条「8–10 MiB body 文案·已签字不修」⇒ **撞号**；**落点** `service/common` → `service/users/users-common`；**状态**「🔴 未修」→「两侧 **EQUIVALENT**·无缺口·**不派单**」）· `S-6`（**本域不成立**：`catch (StatusRuntimeException)` 在前）· `baseline-version`（**实测确实缺**）
- [x] §3 的域侧修法**落码 + 守门**（含变异验证）—— ✅ **2026-10-27 阶段 2 已落码并跑绿**（口径：`mvn -o -pl service/article/article-common,service/article/article-api,service/article/article-rpc -am test` ⇒ **article-common 15 / article-rpc 155 / article-api 99** ⇒ **本域三模块合计 269 条 / 0 failures / 0 errors / 0 skipped** · 核对日期 **2026-10-27**）：
  - **L-1** ✅ 补 `baseline-version: 0`（`article-rpc/src/main/resources/application.yml` flyway 段 ＋ 三段论证注释）
  - **L-2** ⛔ **撤销**（前提不成立：`ArticleRpcApplication` 的 `@Import` 块里**确实有** `DeadlineQueryTimeoutInjector` ＋ `SqlTimeoutStatusTranslator` ⇒ 缺口不存在；「补真装配测试」那半由 **G4 的单 1**（共享层 `MybatisPlusInterceptorChainGateTest`）一次性覆盖 ⇒ 域侧不重复做）
  - **L-3** ✅ proto 契约回归网（`ArticleProtoContractTest` ＋ 域内 `generate.py` ＋ 冻结 `expected.txt`，**真源 sha256 `21596ee267ba9052…`** ⇒ 1/1 绿）
  - ✅ **L-6**（删域侧排序桥）**已落码**（2026-10-27 G3 ⇒ 删 `article-rpc` 的域侧桥 ＋ 自己 `imports` 的第 1 行 ⇒ **clean** 后重跑 **20 条 / 0f 0e 0s** ⇒ 详见 §3 的 **L-6** 行）· ⏳ **L-7**（删 article-api 路径归一副本）＝ **本轮不删**（前置 = G4 的 `9690b04` 先合进本树 ⇒ 属**合并后**的步骤）
  - **L-8** ✅ **JWT 验签搬出 HTTP 预算（本域 = 70s）**（新增 `article-api/.../auth/` 4 个类：`ArticleJwtProtectedRoutes`（**受保护路由表唯一所在** · Filter 与 interceptor 共用）· `ArticleJwtVerificationResult` · `ArticleJwtSignatureVerifier` · `GoStyleJwtAuthFilter`（`@Order(+6)`）＋ 改 `JwtAuthInterceptor` 消费结果并原样重抛 ⇒ **401 空 body 不变**、**业务码仍 1005**、**3 个 public 端点仍公开**）⇒ 守门 `ArticleApiJwtVerificationScopeTest` **7/7 绿 ＋ 变异 3/3 精确变红**（`+8` 顺序 ⇒ 1 条红 / Filter 自写 401 ⇒ 1 条红 / 摘 `@Component` ⇒ 来源标记 `filter`→`inline`）⇒ ⚠️ **本域无真 Tomcat 门禁** ⇒ 「413 压过 401」的响应头层语义**只有 MockMvc 级证据**（已写进证据强度）⚠️ **本域预算是 70s 不是 3s**（全仓唯一例外 ⇒ 数值必须逐域核）
- [x] §4 的派单**已全部走完**（实时轨 `session send` → `G4Common` ＋ 本表正式轨，两轨一致）：
  - **单 1** ＝ 🔴 G4-2（MyBatis-Plus 自动收集 `Interceptor[]` Bean 无运行证据，**阻塞本波**）—— 7 项反馈单齐全
  - **单 2** ＝ 🔴 G4-6（client breaker 在 rpc 侧无装配路径）—— 7 项反馈单齐全（全域共性 ⇒ 请 G4 收口去重）
  - **单 3** ＝ 🟡 G4-3（`S-6` 的 `SqlTimeoutStatusTranslator` javadoc「Go 出处」段**归因错误**）—— 7 项反馈单齐全（Go 4 处 + RAG 3 处行号**已逐条核实真实存在**）
  - ⛔ **G4-1 已撤销**（原「必填字段校验的共享层剩余项」）⇒ **不派**（编号撞 `DRIFT-ARTICLE-API-006` ＝ 已签字不修条目；且该主题 `api.md:407/460` 判 **EQUIVALENT** ⇒ 无编号、无缺口）
  - 🟡 G4-4（A9 轴措辞）/ G4-5（`AGENTS.md` §6.3 漏 `article-rpc`）＝ **登记/转合并收口执行者项**，已随单 1 消息同批发给 G4（**非 7 项反馈单**）
  - ⚠️ **等 G4 回执时逐条核账**（回执规格见 §4 表头）
  - ⚠️ `docs/DRIFT/global/vs-common.md` ＋ `docs/DRIFT/global/_README.md` §七之二属 **SHARED 面 ⇒ G3 不写**（AGENTS.md §5.3e）⇒ 由 G4 / 合并收口执行者落
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由合并收口执行者统一更新）—— ✅ 本轮实测：台账结构守门 `check-ledger-tables` / `check-ledger-selfcheck` / `check-ledger-count` **三项 EXIT=0**；`_MANIFEST.json` **不收录** `vs-common.md`（`grep -c` = 0）⇒ 无需改共享面

> ⚠️🔴 **本轮踩坑（2026-10-27 阶段 2 · 四域共用 · 已在 G4 同步）——「单模块跑法」会拿 `~/.m2` 里的旧共享层做测试**
> - **现象**：先用 `mvn -o -pl service/comment/comment-rpc test`（**不带 `-am`**）⇒ comment-rpc 3 条红、favorite-rpc 1 条红，症状像「SQL deadline 漂移」（驱动收到 2000 而非原值毫秒 / 在途查询 1044ms / **过期后仍执行 SQL**）。
> - **根因（实测到字节级）**：`~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-0.0.1-SNAPSHOT.jar`（安装于 **2026-10-25 21:24**）里的 `DeadlineQueryTimeoutInjector` **不含 `unwrapDriverStatement`** ⇒ 是 **E62（第四十五轮「先 unwrap 到驱动实现类」）之前**的版本 ⇒ ① 毫秒探测必然失败 ⇒ 退回秒级 `ceil`（2000ms）；② 「已过期」分支还是旧行为「**设 1 秒**」而不是抛 57014 ⇒ 过期 SQL 照跑 ⇒ 与三条红**逐条吻合**。
> - **判定**：在**基线 commit `9f034e8`** 建临时 worktree 跑 `mvn -o -pl <两模块> -am test` ⇒ **comment-rpc 3/3 绿 ＋ favorite-rpc 5/5 绿** ⇒ ⇒ **那 4 条红与本轮落码无关，也不是漂移** ⇒ **不派 G4**。
> - **⇒ 纪律**：**凡单模块跑域内测试，一律加 `-am`**（或先 `mvn install` 共享层）⇒ 否则会拿旧共享层做测试、看到一堆**假红**。⚠️ 这也是「**跑绿 ≠ 对**」的另一个形态（反过来：**跑红也 ≠ 坏**）。
> - ⚠️ 共享层 `service/common/**` **不是 G3 的写权**（只有 G4 能写）⇒ 本条只作**记录**，修复动作（若有）在 G4 侧。

> ℹ️ **G4 回执核账（2026-10-27）**：单 1 / 单 2 / 单 4 **全部已落码、零域侧改动**；G4 已确认我方「波及 16 个消费者」的顾虑**不成立**（那是 `grep pom.xml` 命中文本注释的**假阳性**，权威口径是 `mvn dependency:list` ⇒ 有效 classpath 上有 MP 的**只有那 9 个 rpc**）⇒ 我方「删域侧 9 份桥」时**直接删即可、不需改写**（共享层那份用字符串形式 `afterName`/`beforeName`）。⚠️ **单 4 的全量回归数字（基线 `a1a2e05`）待 G4 补** ⇒ 本表 §4 的收口口径暂记「待补」。

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
| **A-1** | **双侧活体对拍**（article-api / article-rpc **两侧同时起**） | 🟡 **部分闭合（2026-10-29 · G3）** —— 已取到 **3 条 L4 双侧活体证据**，但**未达全量** | 🟢 **G3（本域）** | **否**（基线已按 🟡 计入） | [`api.md` §8.3 第 1 项](api.md)（已回填：探针 `tools/api-full-parity-probe/article/`）· [`rpc.md` §「判定不变的理由」](rpc.md)<br>**已做**：`tools/api-full-parity-probe/article/`（**G3 真私有目录** · AGENTS.md §5.3f）⇒ Go 真源栈（真源二进制 + **配置副本**〔真源仓全程 `git status` 干净〕+ 真源 `article.sql` + `002_community_revision.sql`）与 RAG 栈（`article-rpc` → Nacos + `article-api`）**同时起**，两侧**各自独立** Postgres ⇒ 同一请求 ⇒ **逐字节**比对 ⇒ **3 条全一致**：`GET /v1/articles`（200，含 `author_name` 降级路径）/ `GET /v1/article/9007199254740993`（5001）/ `GET /v1/article/does-not-exist`（1002）<br>⚠️ **仍未闭合的部分（不得写成「全闭合」）**：① `judge` §九 G-3 要求「article 域双侧活体 **0 条**」⇒ 现 3 条；② **成功路径只覆盖公开列表端点** —— 宿主上 `user.rpc` **两侧都不可达** ⇒ 单条详情端点两侧**同样降级成 5001** ⇒ **成功路径的完整字段语义**未双侧验证 ⇒ **需 `user.rpc` 两侧可用后补**<br>🔴 **探针副产品（本轮实跑才暴露，已修）**：`DRIFT-ARTICLE-API-011`（yaml 少 `auth:` ⇒ **组件起不来**）· `DRIFT-ARTICLE-API-012`（JSON 字段序漂移）⇒ 见 [`api.md` §7.5](api.md) |
| **A-2** | **`UNRESOLVED-002` 跨栈活体对拍**（依赖 article-rpc 的响应语义） | 🟡 **部分关闭**（rpc 侧已核 `public_only` / `status` 的消费方式；仍缺跨栈活体） | 🟢 G3 | 否 | [`api.md` §9 `UNRESOLVED-002`](api.md) |
| **A-3** | **`DRIFT-HOTAPI-030` 后续格**（**query 段数轴**：总「段」数超过 Go 的 10000 段上限） | ✅ **已闭合（2026-10-30 · G3 · **L4 真 Tomcat 活体**）** —— 🔴 **原「仍 OPEN」是过报，已销账**<br>📌 **销账依据（三条）**：<br>① **Go 真源逐行**：go1.25.11 `src/net/url/url.go:1019-1021`（`parseQuery` 里 `strings.Count(query, "&") + 1` > 10000 ⇒ 拒）+ `:1001`（`defaultMaxParams = 10000`）+ `:1003-1005`（`urlParamsWithinMax`）⇒ **Go 翻转点 = 第 10001 段**；<br>② **本域闸门在装且判据同形** —— ⚠️ **原写「本域 = `OFF` ⇒ 闸门不装」是错的**：本域 `service/article/article-api/src/main/resources/application.yml:185` 实为 **`mode: raw`**（不是 `off`）⇒ 共享层 `GoStyleFormValueLimitInterceptor:83-99`（`mode != OFF` ⇒ 生效；`raw` ⇒ 走 `GoStyleFormValues.writeGoStyleBadRequest` ⇒ **Go 的裸 400 形状**）⇒ 判据仍是 `GoStyleFormValues:262`（`> 10000` 段 ⇒ 拒，与 Go 逐字同形）⇒ **RAG 翻转点 = 第 10001 段 = 与 Go 同**；<br>③ **本域 L4 活体**：`service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiFormParamLimitTest.java` —— **2026-10-30 实跑 9/9 绿**（含 `tooManyQuerySegmentsIsRejectedLikeGo`：**第 10001 段 ⇒ 400 + body 逐字 = `number of URL query parameters exceeded limit\n`**）⇒ **与 Go 的拒绝形状与翻转点逐条同形**<br>⇒ ⛔ **原「修法 = 共享层 `MAX_PARAMETER_COUNT` `10001 → 10000`」已作废**（`favorite/api.md` §21.3 · 2026-10-28 · G3 · L1：改成 10000 ⇒ Tomcat 在第 10000 段就早于 Go 拒 ⇒ 400+HTML ⇒ **反而制造新漂移**）⇒ ⇒ **不得派这张单给 G4**<br>⇒ ⚠️ **本条只闭合「query 段数轴」** ⇒ **body form 参数轴是另一条、真实残留** ⇒ 见 `docs/DRIFT/favorite/vs-common.md` 的 **`F-8`**（C 类 · 跨域同源 · RAG 比 Go **宽一格**） | ⛔ **不再需要共享层改动**（域侧闸门在装 · 共享层常量**不许动**） | 否 | [`favorite/vs-common.md` §「仍未闭合的下一格」](../favorite/vs-common.md)（**已销账**） |
| **A-4** | **P0 #8 的共享面半边**（根级 compose 的 `:?` ＋ 根 README 的 `.env.example`） | 🔴 **仍开**（**域侧那半已修**：`application.yml` 去明文默认值 ＋ `@PostConstruct verifyAccessSecret()` ＋ test scope `application.properties` ＋ **3 例真 `ApplicationContextRunner`** 守门） | 🔴 **合并收口执行者**（根级文件**无人拥有** ⇒ 根因**并入 G1 的 `S1-USERS-001`** ⇒ **不另开编号**） | **否**（域侧已修 ⇒ P0 #8 的**域侧部分**闭合；⚠️ **P0 #8 整体仍不能判 ✅**） | [`favorite/api.md` §24](../favorite/api.md) 第 6/8/11 项 · [`comment/api.md` §「根级半边」](../comment/api.md) |
| **A-5** | **rpc 域的其余明文默认值**（`${POSTGRES_PASSWORD:password}` / `${MINIO_SECRET_KEY:minioadmin}` / `${*_AUTHORITY_TOKEN:}`） | 🟡 **已登记，本轮未动** | 🔴 **G4**（共享层 `service/common` 单写权 ⇒ 需**单独单据**） | 否（实际不可达：compose 已用 `:?` 强制 `POSTGRES_PASSWORD`） | [`favorite/api.md` §24.2 第 8 项第 ③ 条](../favorite/api.md) |
| **A-6** | **跨域待办**：其它域 rpc 的 `*SchemaGate` 走**同一套** `@EventListener(ApplicationReadyEvent.class)` 写法 ⇒ 校验发生在「server 已起来之后」 | 🟡 **已登记，不修** | ⬜ **各域自行判断**（本工作树只写自己域的台账 ⇒ AGENTS.md §5.3） | 否 | [`rpc.md` §「跨域待办」](rpc.md) |

⇒ **article 域的「域内可动」未闭合项 = A-1 / A-2 两条**（其余为跨域 / 根级 / 归属 G4）⇒ ⇒ **本域阶段一判定不变**。

---

## 7. 🔴 Go 出处引用漂移自查（2026-10-29 · G3 域内收敛自查 B）

> **为什么这是缺陷而不是笔误**：以「Go: …」措辞写在 javadoc / `@DisplayName` / 断言消息里的断言
> **改一行不会让任何测试变红** ⇒ ⇒ **绿测试可能守着一个错的事实**（AGENTS.md §3.3.2）。
> ⇒ ⇒ 本轮**机器扫 + 逐条核 + 落一个守门**，判据 = **把 `文件:行号` 拿回真源读那一行**。

| # | 落点 | 原引用 | 订正为 | 缺陷类型 |
|---|---|---|---|---|
| 1 | `article-common/.../errmsg/ErrorCode.java:46` | `get_article_handler.go:35-40` | `get_article_handler.go:28-32` | **行号越界**（该文件**仅 34 行**） |
| 2 | `article-rpc/.../domain/ArticleEntity.java:79` | `list_articles_logic.go:184` | `update_article_logic.go:184` | **文件指错**（行号对、文件错 ⇒ **同目录兄弟文件**，最毒的一类） |
| 3 | `article-rpc/.../config/ArticleKafkaChannelValidator.java:155` ＋ `ArticleKafkaChannelValidatorTest.java:21` | `internal/article.go:58,60` | `service/article/rpc/article.go:58,60` | **域段错** |
| 4 | `article-rpc/.../config/ArticleMetaObjectHandler.java:21` ＋ `ArticleTransactionWiringTest.java:849` | `gorm@v1.31.2/callbacks/update.go:243-259` ＋ `:279-281` | `gorm@**v1.31.1**/callbacks/update.go:**238-241**` ＋ `:276-281` | **版本错**（真源 `go.mod` pin 的是 **v1.31.1** ⇒ 原引用指到**另一份内容不同的副本**）＋ 行号错 |
| 5 | `article-api/.../ArticleApiJwtVerificationScopeTest.java:33` | `application.yml:234` | `application.yml:238` | **行号错**（`http-timeout: 70s` 实际在 `:238`） |
| 6 | `article-api/.../ArticleApiJwtVerificationScopeTest.java:34` | `service/article/api/etc/article.yaml:4` | `service/article/api/etc/**article-api.yaml**:4` | **文件指错** |

⇒ **本域 6 处已订正（零行为变更：全是注释 / `@DisplayName`）** ⇒ 守门见 §7.1。

### 7.1 守门（**四域共用**，G3 域侧落码）

| 项 | 内容 |
|---|---|
| **落点** | `tools/g3-anchor-check/check-anchors.py` ＋ `tools/g3-anchor-check/mutate-anchors.sh`（**新目录 ⇒ 按 AGENTS.md §5.3f「共享判据 = 依赖度数」＝ 真私有 ⇒ G3 域侧落码合法**） |
| **判据** | 抽出 RAG 注释里的 `*.go / *.sql / *.yaml / *.yml:行号` ⇒ **回真源读那一行** ⇒ **只要求「该行存在且非空」** ⇒ ⇒ **零内容匹配 ⇒ 零误报** |
| **覆盖的真源根** | Go 真源仓 · Go **标准库**（`$GOROOT/src`）· **module cache**（按真源 `go.mod` pin 的版本）· **RAG 自己的** 文件（`application.yml` / `db/migration/*.sql`）⇒ ⚠️ 少一个就会把「**我解析不了**」伪装成「**引用是错的**」 |
| **分类** | `OK` / `AMBIGUOUS`（同名多文件，机器判不了）· `LINE_OOB`（行号越界）· `FILE_MISSING`（文件找不到 / 出处缺文件名）· `EMPTY_LINE` · **`VERSION_MISMATCH`**（引的不是真源 pin 的那一份 ⇒ 指到**另一份内容不同的副本** ⇒ 行号照样「在范围内」⇒ 别的判据抓不到） |
| **当前实测**（2026-10-29） | **EXIT=0** —— 440 份 java（main+test）· **2951 条**引用 · 真源索引 11369 个文件 ⇒ `OK=1849` · `AMBIGUOUS=1102` · **`LINE_OOB=0` · `FILE_MISSING=0` · `EMPTY_LINE=0` · `VERSION_MISMATCH=0`** |
| **变异验证**（**非假绿**） | `bash tools/g3-anchor-check/mutate-anchors.sh` ⇒ **MUT_EXIT=0**：**M1 行号越界 ✅ 精确转红（LINE_OOB）** · **M2 文件指错 ✅ 精确转红（LINE_OOB）** · **M3 版本写错 ✅ 精确转红（VERSION_MISMATCH）** · **M4 出处缺文件名 ✅ 精确转红（FILE_MISSING）** · **还原后重新转绿 ✅** |
| **🔴 效力边界（必须随结论一起读）** | 判据是「**那一行存在且非空**」⇒ ⇒ **查不出「行号存在、但内容不是它声称的那件事」** ⇒ ⇒ **内容级错配不在本守门覆盖内**（如「引用了 `sdk/trace/span.go:631-655` 但那一段讲的不是本件事」）⇒ ⇒ **这类必须人读**，不得因为守门绿了就认为出处全对 |

---

## 8. 🔴 台账内部矛盾 / 过期结论订正（2026-10-29 · G3 域内收敛自查 C）

> **扫描口径**：把本域台账里**同一个 ID 在不同行**同时出现「已闭」与「仍开」措辞的**候选**全部取出
> ⇒ 再逐条**人读**核实 ⇒ 只保留**前提真的失效**的。
> ⚠️ **本轮踩到的第一版误报（记下来）**：扫描器最初**没有排除「同一行里『修复前 → 修复后』两列」**
> ⇒ ⇒ 把 18 条**表格内的正常对照**误判成矛盾 ⇒ ⇒ 改成「**只比不同行**」后，候选降到 3 条 ⇒ ⇒
> **2 条是「未闭合项」字样误命中** ⇒ **真矛盾 = 下面这一条**。

| # | 落点 | 矛盾 | 订正 |
|---|---|---|---|
| 1 | [`api.md` §8.3](api.md) | 「**建议补的探针（本轮未做，列为待办）**」的 **4 项**里，**3 项早已被后续轮次消解**，状态列却没回填 ⇒ ⇒ 读者会以为它们还开着。<br>⚠️ 其中第 2 项「真实 Tomcat 探针 ⇒ **用于关闭 §9 `UNRESOLVED-001`**」—— **`UNRESOLVED-001` 本身已在第二轮关闭** ⇒ ⇒ **前提直接失效**（不是「还没做」，是「要关的那条早就关了」） | ✅ **改写为「活状态」表**（逐条补时间坐标）：<br>① **双侧活体对拍 —— 仍开**，但「**修 `DRIFT-001` 的前置**」这个理由已失效（`DRIFT-ARTICLE-API-001` **已修**：F-1 ＋ `ArticleApiMvcPipelineTest` × 3 ＋ 变异 3/3 转红）⇒ 现在它是**证据缺口**（归属 G-3，**不阻塞阶段 1**）<br>② **真 Tomcat 探针 —— 已消解**（`UNRESOLVED-001` 已关闭 ＋ 本域**已有 2 个 `RANDOM_PORT` 真容器测试**：`ArticleApiAssemblyWiringTest` / `ArticleApiFormParamLimitTest`）<br>③ **user-rpc 慢响应探针 —— 已消解**（`DRIFT-ARTICLE-API-002` **已修**：F-2 ＋ 守门 `UserRpcClientDeadlineTest` × 5 ＋ 变异 2/2 转红）<br>④ **重复 query key 探针 —— 已消解**（`DRIFT-ARTICLE-API-003` **已修**：F-3 ＋ 守门 `ArticleApiMvcPipelineTest` × 3 ＋ 变异 2/2 转红） |

⇒ **article 域真矛盾 = 1 条，已订正** ⇒ ⇒ **本域台账的「现状面」从此只有一处权威**（§6 未闭合清单 ＋ §7 出处订正 ＋ 本节）。

---

## 9. 🟢 新立 `T-2.7`：**article 域独立失败路径矩阵**（**7 端点 × 8 类失败维度** × **真 Tomcat**）⇒ 基线 §九 `G-13` 在本域**闭合**（2026-10-29 · R2 lane · `align/r2-article-hot`）

### 9.1 起因（基线原文）

`docs/PhaseGoal-judge.md` P0 **#2** 的判据：
> 「存在失败路径处理」**推不出**「参数校验 / 业务规则 / 认证 / 权限四项都有效」；且失败路径**域覆盖不全**

＋ §九 **`G-13`**：「**4 域无独立失败路径矩阵**（`article` / `follow` / `task` / `hot`）」⇒ 🟡 **否**（P0 #2/#4 因此为 🟡）。
⇒ **article 是这 4 域里失败路径最密的一域**（下载 / 上传 / 审核 / `S-5`）⇒ **本轮补齐，且不许只做 4 条代表**。

### 9.2 落点（3 个文件 · **生产代码零改动**）

| 文件 | 动作 | 内容 |
|---|---|---|
| `service/article/article-api/src/test/java/com/rag/article/article/api/support/RealTomcatPort.java` | **新增** | 真 Tomcat 的端口选取 ＋ **双地址** bind 自检（`0.0.0.0` **与** `127.0.0.1` **两个都要探**）· 段 **18141–18145** |
| `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiFailurePathMatrixTest.java` | **新增** | **104 条**：7 端点 × 8 类失败维度 ＋ 边界值 ＋ 路由层 |
| `service/article/article-api/src/test/mutate/mutate-failure-matrix.sh` | **新增** | **6 条变异**逐条精确转红 ＋ 还原后转绿（§9.6） |

⇒ ⚠️ **article 域生产代码零改动** ⇒ ⇒ **不构成 Go 对照项**（与本档 §3 同款口径）。

### 9.3 为什么**必须真 Tomcat**（不是 MockMvc）

本域原先 **8 个测试类**（`ArticleApiAssemblyWiringTest` / `ArticleApiFormParamLimitTest` 用 `RANDOM_PORT`，其余 6 个用 `MOCK`）⇒ **真容器覆盖率为零**。MockMvc 有三处**系统性**测不到：

1. **Content-Type 的容器归一化** —— 真 Tomcat 把 `text/plain; charset=utf-8` 归一化成 `text/plain;charset=utf-8`（分号后无空格）⇒ **MockMvc 里断言「带空格」的写法会绿、真容器下会红** ⇒ 这正是 **DRIFT-024**（`text/plain 400 丢 Content-Type`）当初漏网的机制；
2. **`Content-Length`** —— 由容器依据实际写入字节数计算，MockMvc 不参与；
3. **body 是否被容器补写** —— 404 / 405 这类「body 为 null」的响应，容器会不会自作主张补空 body 或补 Content-Type，**只有真容器说了算**。

⚠️ **端口段为什么不取 global 段表的 `22400–22499`**：该段已被 **article-rpc 整段吃满**（`article-rpc/.../support/RealGrpcPort.java:94-97` 占 `22400–22479` ＋ `RealGrpcPortTest.java:74-77` 占 `22480–22499` 作守门脚手架）⇒ **段内无切片** ⇒ 本轮取 lane 分配的 `18141–18145`。📌 global 段表（`docs/DRIFT/global/vs-common.md` §4.10.19.3 · **G4 写权**）里 article 域那一行仍是「预分配 22400–22499」⇒ **需 G4 在收口时补记本段** ⇒ **本域只在自己的台账登记，不改共享文件**。

### 9.4 矩阵（**7 端点逐个** × 8 类维度 · 期望值逐条带 Go `文件:行号`）

| 维度 | Go 出处 | 形态（**逐端点不同**） | 条数 |
|---|---|---|---|
| **认证·无 token** | `rest/handler/authhandler.go:106`（`unauthorized()` 只 WriteHeader） | 受保护 4 端点 ⇒ **401 + 空 body + 无 Content-Type**；**公开 3 端点** ⇒ 进业务（**不得 401**） | 4 + 3 |
| **认证·签名无效** | 同上（Go **不区分**失败原因） | **401 + 空 body + 无 Content-Type** | 4 |
| **认证·claim 缺失** | `logic/article/helper.go:17-39`（`extractCurrentUserID`） | **HTTP 200 + `{code:1005,msg:"请先登录"}`**（**不是 401**）＋ **RPC 完全未被调用** | 4 |
| **权限·归属不符** | `update_article_logic.go:65-67` · `delete_article_logic.go:64-66` · `get_author_article_logic.go:60-62` | **200 + `{code:1006}`** ＋ **写路径 RPC 未被调用**（create / public get / list / upload **没有**这条规则 ⇒ 不参与） | 3 + 4 |
| **权限·rpc PermissionDenied** | `create:59-74` · `update:54-55` · `delete:55-56` · `get-author:45-46` · `get:44-51` · `list:51-56` | **同一码 7 端点 3 种结果**：update/delete/get-author ⇒ **1006**；create/get/list ⇒ **1015**（落 Go 的 `default`）；upload ⇒ **403 + text/plain**（`errcode/grpc.go:21-22`） | 7 |
| **参数校验** | `types/types.go:26-33`（title/content **必填**）vs `:74-83`（update 七字段**全 optional**）· `core/mapping/utils.go:104-113` | create 缺席 ⇒ **400 + `field "title" is not set`**；显式 null ⇒ **400 + `mustn't be nil`**；`incr_view=maybe` ⇒ **400 + `type mismatch`**；`page=abc` ⇒ **400**；upload 字段名不是 `image` ⇒ **400**；update 空 body ⇒ **合法 200** | 7 + 1 + 4 |
| **业务规则** | `create_article_logic.go:59-74`（AlreadyExists ⇒ 1001；FailedPrecondition ⇒ **1004 带 data**；DeadlineExceeded ⇒ **1003 带 data**）· `get_article_logic.go:54-56` | NotFound ⇒ update/delete/get-author/get **1002**、create/list **1015**、upload **404 + text/plain**；未发布 ⇒ **1002**（**安全闸门**，不泄露 id 存在） | 7 + 1 + 1 |
| **下游不可用 / 内部错** | 各 logic 的 `default` / `codes.Internal` 分支 · `errcode/grpc.go:31-38` | UNAVAILABLE ⇒ 6 端点 **1015**、upload **503 + text/plain**；INTERNAL ⇒ 6 端点 **5001**、upload **500 + text/plain** | 7 + 7 |
| **边界值** | `types/types.go:61-64`（page/page_size 有 `default` ⇒ **api 层不夹取**）· `core/mapping/utils.go:106-113` | create 的 `title:""` ⇒ **合法 200**（Go 只判「是否缺席」）；list 的 `page=0` / `page=-1` ⇒ **原样透传**（**不夹取**）；`page=`（空值）⇒ **取默认 1 / 20**；`incr_view=1/0` ⇒ true/false | 1 + 1 + 1 + 1 |
| **路由层** | `rest/router/patrouter.go:71-82` · `rest/internal/cors/handlers.go:44-48` | 未注册路径 ⇒ **404 + `404 page not found\n` + text/plain + nosniff**；**方法**不匹配 ⇒ **404 空 body + 无 Content-Type**（**不是 405**！）；404/405 **都不经过鉴权** | 3 |

### 9.5 🔴 三处**决定性不对称**（最容易「顺手统一」的地方）

| # | 不对称 | Go 出处 | 后果 |
|---|---|---|---|
| ① | **article-api 没有注册 `httpx.SetErrorHandler`**（`service/article/api/article.go` 全文 42 行，一个字都没有）⇒ **解析层失败 = 400 + text/plain + nosniff** | vs message / like / hot 三域都是「**200 + `{code:5001}`**」 | 照抄 hot 的判据 ⇒ 本域 400 变 200 ＋ 形状错 |
| ② | **同一个 gRPC 码在 7 端点映出 3 种结果**（`1006` / `1015` / `403 text/plain`） | `create:59-74` **没有** PermissionDenied 分支 ⇒ 落 `default`；upload 走 `errcode.CodeFromGrpcError` | 统一成一个码 ⇒ 6 端点回归 |
| ③ | **article-api 是四个 api 域里唯一用 `rest.WithCors()` 的** ⇒ **方法不匹配 = 404 空 body**，而 hot / follow / message / like 都是 **405 + `Allow`** | `service/article/api/article.go:33` `rest.MustNewServer(c.RestConf, rest.WithCors())` | 照抄 hot 的 405 ⇒ 本域形状错 |

### 9.6 变异验证（**6/6 精确转红** ＋ 还原后转绿 ⇒ 网有牙）

`service/article/article-api/src/test/mutate/mutate-failure-matrix.sh`（**还原用「备份 + 复制回来」，绝不用 `git checkout --`；**`rm -rf` 在本机会被回收站守卫整体拒绝**）。

**判定口径比「跑一遍红了」严格**：**红的方法集合必须与预期**完全相等**（多红一条 = 判据之间有意外耦合；少红一条 = 这条断言是摆设）**。

| # | 变异（改**生产代码**） | 预期红的方法集合 | 实测 |
|---|---|---|---|
| **M1** | `ArticleCreateService` 的 `AlreadyExists` 分支 **1001 ⇒ 5001** | `createConflictMatrix` | ✅ 一致 |
| **M2** | `ArticleGetService` 的 `NOT_FOUND` 分支 **1002 ⇒ 1015** | `rpcNotFoundMatrix` | ✅ 一致 |
| **M3** | `ArticleGetService` 的「未发布 ⇒ 1002」闸门 **1002 ⇒ 1015** | `nonPublishedArticleIsAlso1002` | ✅ 一致 |
| **M4** | `GoStyleGrpcStatusMapper` 的 `UNAVAILABLE` **503 ⇒ 400** | `rpcUnavailableMatrix` | ✅ 一致 |
| **M5** | `ArticleApiSupport.userId` 的 **1005 ⇒ 1006** | `tokenWithoutUserIdClaimIs200With1005` | ✅ 一致 |
| **M6** | `GlobalExceptionHandler` 的鉴权失败 **401 ⇒ 403** | `missingTokenIs401WithEmptyBody` ＋ `invalidSignatureIs401WithEmptyBody` ＋ `routerErrorsBypassAuth` | ✅ 一致 |

⇒ **实跑**：`bash service/article/article-api/src/test/mutate/mutate-failure-matrix.sh` ⇒ **exit 0**（6/6 精确转红 ＋ 还原后 `exit 0`）。

### 9.7 效力边界（如实登记，不夸大）

| 边界 | 内容 |
|---|---|
| **证据强度** | **L3 / R3**（期望值逐条抄自 Go 源码 ＋ **真 Tomcat** 自动化断言 ＋ **变异 6/6 精确转红**）⇒ 满足基线 §1.1 **R3** 的 ④⑤⑥ |
| ⛔ **不是 R4** | R4 要求**双侧活体对拍 ＋ 变异** ⇒ 本类**只有**变异，**没有**双侧活体 ⇒ ⇒ **不得**据此宣称「与 Go 逐字节一致」 |
| ⚠️ **P0 #2 仍不是 ✅** | `task` 域**仍无**独立失败路径矩阵（`T-2.8` · **G2**）⇒ 本条只闭合 **article 域**（+ 本轮 hot 域）⇒ **不因本条把 P0 #2 判成 ✅** |
| ⚠️ **双侧对拍缺口不受影响** | 基线 §九 `G-1` / `G-3`（探针不可复算 / 环境受阻）**本轮未动** ⇒ 本域仍按 **R3** 计 |
| ⚠️ **全局端口段表待 G4 补记** | 见 §9.3 ⇒ **共享文件 ⇒ 本域不擅自改** |

### 9.8 🟡 **本轮基线上撞到的 1 条**（**开轮前就红，与本轮无关**，但必须登记）

| 项 | 内容 |
|---|---|
| **现象** | 开轮基线（`73e53cd`，**本轮尚未写任何代码**）跑 `mvn -o -B -pl service/article/article-api -am -DargLine=-Xmx2g test` ⇒ **106 条 / 1 红**：`ArticleApiFormParamLimitTest.rawNonAsciiInQueryIsRejectedAtConnectorLayer:323` ⇒ **`expected: <200> but was: <103>`** |
| **红灯的那一行** | `assertEquals(200, ok.status, "合规的百分号编码必须正常通过（Tomcat 与 Go 都接受）")` —— 该测试用**裸 socket** 读**第一个**响应行 |
| **根因（读码判定）** | **`103` 是 Tomcat 的 `Early Hints`（1xx informational）响应** ⇒ 该测试的裸 socket 读取器**不跳过 1xx** ⇒ 偶发把 103 当成最终响应读走 ⇒ **不是**业务回归、**不是**本轮引入（该测试与本轮新增文件零耦合，且红灯出现在**写代码之前**） |
| **实测复现率** | 本轮落码后再跑 **4 次**（1 次全量 ＋ 3 次定向 `-Dtest=ArticleApiFormParamLimitTest`）⇒ **4/4 全绿**（`Tests run: 9, Failures: 0`）⇒ ⇒ **非稳定复现 ⇒ 判为环境/时序偶发** |
| **本轮处置** | ⛔ **不修** —— ① 属**另一个组件的既有测试**（`ArticleApiFormParamLimitTest`），与 `T-2.7` 的失败路径矩阵**无耦合**；② 修它要改裸 socket 读取器的 1xx 处理 ⇒ **超出本任务边界**（顺手改会掩盖「本轮只动了 3 个新文件 + 2 份台账」这个可审计事实） |
| **解锁条件（谁来做）** | **域侧**（G3 lane 下一轮）把该测试的裸 socket 读取器改成「**循环读到非 1xx 为止**」⇒ 即对齐 RFC 9110 §15.2（1xx 可出现多次且可被忽略）⇒ 之后该条**恒绿** ⇒ **无需改动任何产品代码** |

### 9.9 实跑记录（**真实输出** · 2026-10-29）

| 命令 | 结果 |
|---|---|
| `mvn -o -B -pl service/article/article-api -DargLine=-Xmx2g test` | **`Tests run: 210, Failures: 0, Errors: 0, Skipped: 0`** · **EXIT=0**（其中本轮新增 `ArticleApiFailurePathMatrixTest` = **104 条**，`Tests run: 104, Failures: 0`）|
| `bash service/article/article-api/src/test/mutate/mutate-failure-matrix.sh` | **EXIT=0**（**6/6 精确转红** ＋ 还原后 `exit 0`）|
| `python3 tools/difftest/check-wave-lane-ownership.py` | g3 lane **零违规**（剩余 3 项违规属 **g1 / g2** 的探针目录缺失 ⇒ **不是本 lane**）|

⚠️ **本轮**生产代码**零改动** ⇒ ⇒ **不构成 Go 对照项**（与本档 §3 同款口径）。

### 9.10 ⚠️ **本轮的栈形态 ＋ 串扰排查口径**（2026-10-29 主线同步后补记）

| 项 | 结论 |
|---|---|
| **是否起双侧栈** | **否** —— 本轮只起 **RAG 单侧**：`article-api` 的**真 Tomcat**（`DEFINED_PORT` ＋ 段 `18141–18145`）＋ **打桩的 gRPC 客户端** ⇒ ⇒ **Go 真源进程一次都没起** ⇒ ⇒ 本轮**不是**双侧活体对拍 ⇒ 证据强度按 **R3**（§9.8）计，**不得**升 R4 |
| **是否共享 Nacos** | **否** —— ① 三个 gRPC 客户端地址全部写死 `static://127.0.0.1:65535`（**不走服务发现**，无 `discovery://`）；② `spring.cloud.service-registry.auto-registration.enabled=false`（**不注册**）；③ `spring.cloud.nacos.discovery.server-addr=127.0.0.1:1`（**死口**，不是宿主口 `49848`）⇒ ⇒ **本轮完全不接触共享 Nacos** ⇒ ⇒ **不受「共享 Nacos 里存在同 serviceName 的陈旧/他栈实例」那种串扰影响** |
| **是否观测到 `INTERNAL` / `code=5001` / 熔断 failure** | **观测到的全部是测试自己打的桩**，逐条核对：① `rpcInternalMatrix` 刻意让 rpc 返 `INTERNAL` ⇒ 期望 5001；② `createConflictMatrix` 里的 5001 是 M1 变异后的**预期红**；③ hot 侧 `HotServiceGrpcImpl -- [Hot] unhandled error: Redis ZRevRangeWithScores failed: timeout` 是 `redisFailureMapsToUnknownNotInternal` 那条用例的**输入**，**输出**是 `UNKNOWN` ⇒ ⇒ **没有一条是环境串扰**。三个模块全量套件 **210 / 116 / 129 条全绿 · EXIT=0**；两轮变异脚本**可重复**（article **6/6**、hot **5/5**，两次跑出**同一组**红集合）⇒ ⇒ **无并发串扰迹象** |
| ⚠️ **反向纪律（AGENTS.md §3.3）** | **跑红 ≠ 是缺陷** ⇒ 本轮基线那条 103（§9.8）也是**先按环境/时序偶发排查**（重跑 4 次全绿 ＋ 读码定位到 1xx informational），**再**判「不是回归」⇒ ⇒ 与主线 2026-10-29 记录的那次 `INTERNAL` 偶发**同一纪律** |

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
- `docs/DRIFT/_cross/component-coverage-matrix.md` ⇒ **「当前态」节（`:23-78`）才是现状**，历史节（2026-10-23 快照）只作追溯（`:5-14`）；⚠️ 其「当前态」节是 **2026-10-25** ⇒ `article-api` 残留 `-007`（`:40`）已被 `article/api.md:696` 推翻、计数 86 已被 **102** 推翻 ⇒ **共享面落后于域台账**；仍有效的是「**rpc 必须先于 api**」的排序约束（`:13-14`）。
- `docs/DRIFT/_cross/proto-verification.md` ⇒ 三档回归网：**user/admin 有 `check-proto-static.sh`**；其余 8 个「只有一次性实测」⇒ 但 `:45` 的「脚本一个都没覆盖」**已过期**（实际已覆盖 5 个）⇒ 共享面待收口订正；`comment`/`like` 的 `package` 归一**已签字**。
- `docs/DRIFT/_cross/domain-common-lanes.md` ⇒ **A1~A9 九条机制面轴的定义**（`:77-85`）＋ 写权边界（§3）＋ 跨 2+ 域未闭合项（§4）⇒ 我域的逐轴结论写在各域 `vs-common.md` §2。
- `docs/Intentional_Deviations/article/rpc.md` ⇒ 已签 **2 条**：`SD-2026-10-24-01`（`:16` 全局锁序统一 `article → publication`）· `SD-2026-10-24-02`（`:147` Kafka 四通道不能逐通道切 broker）⇒ 倾向 = **全局一致性优先于逐字复刻**（两处都写了「为什么**不修回去**」）；`article/{api,common}.md` = **0 字节空档**（非漏写）。
- `docs/Intentional_Deviations/comment/` ⇒ 已签 5 条：`SD-2026-10-23-04`（proto `package` 归一，`proto.md:11`）· `CAND-02`（`report_record` 表 Go 侧不存在，`rpc.md:67`）· `CAND-06`（Go 的 `manage` 清位 SQL **必然报错**，`rpc.md:202`）· `CAND-07`（HS384/HS512 Go 接受 RAG 拒，`api.md:79`）· `CAND-08`（保护层覆盖范围 RAG 更严，`api.md:127`）；**未签字候选 3 条**：`CAND-01`（9 处 `NOT NULL DEFAULT`，`rpc.md:22`）· `CAND-03`（authority/dispatch 默认关闭，`rpc.md:169`）· `CAND-09`（DDL 更严，`rpc.md:121`）⇒ **登记类，不推拍板**；`CAND-04` ⛔ **前提已被实测推翻 ⇒ 作废**（`api.md:18`）。
- `docs/Intentional_Deviations/favorite/` ⇒ 已签 **9 条**（`DEV-FAV-API-001/002` · `DEV-FAV-COMMON-001/002` · `DEV-FAV-RPC-001/003/004/006/007`）⇒ 倾向 = **表达层/部署面为主**，仅 1 条是「RAG 更严」（`DEV-FAV-RPC-007` 的 CHECK）；**已撤销 2 条**（`DEV-FAV-RPC-002` / `-005` ⇒ 前提被逐行核对证伪）⇒ **登记 ≠ 签字的前提必须先复核**；`favorite/proto.md:11`「本域暂无条目」。
- `docs/Intentional_Deviations/hot/` ⇒ 已签 **17 条**（`SD-HOT-RPC-001~004` + `SD-1/2/3` + `SD-HOT-API-001~005/007~010`）⇒ 倾向 = **「RAG 更稳 / 更保守 / 单向增强」占多数**；`SD-HOT-API-006` ⛔ 2026-10-25 已撤销（`api.md:22`）；**未签字候选 1 条** = `SD-2026-10-25-CAND-10`（`api.md:332`）⇒ 登记类；`hot/common.md:7`「本组件无单独登记的签字偏离」＋ `:22-34` 两条「看起来像偏离、实际不是」。
- `docs/Intentional_Deviations/_INDEX.md` ⇒ **CAND 族共 14 条未签字** ⇒ 按 §5.3c **不升级为拍板**；⚠️ `SD-` 编号**存在跨域撞号** ⇒ 引用必须给「**文件 + 域**」，不能只写编号。
- `docs/PhaseGoal/g3.md` ⇒ **lane 判定 🟡**（16 组件：✅15 · 🟡1 · 🔴0）；**差距 = `article-rpc` 一个组件**；**§2.1「本 lane 自修」只有 `article-rpc` 一行**（`S-5` + `U-3/U-4/U-9/U-4′`）；**§2.2 转派 G4** = comment-rpc `D8/D9/U13` · favorite-api `U9/405/B2` · favorite-rpc `U7/U9/E65`；**§3 P0 主责 = #4**，协作 #1/#2/#3/#6/#8，不涉及 #5/#7/#9；§5 收口验收命令全可复算。
- `docs/PhaseGoal-judge.md` ⇒ **§1.3** ✅ 判据 = `R ≥ R3` **且** 归属本组件的必相对齐项 = 0 **且** 有**可复算**判定命令；**§1.5 判定权威层模型**（`:93-108`）= **L0 台账「条目级最新状态」是唯一权威** ⇒ **不得引用台账头的「组件判定」行**；**§8.4 阶段一准入 = ❌ 未通过**，三类硬阻断 = **X-11 密钥链（P0 #8）** · **X-12 项目说明（P0 #5）** · like-rpc `DRIFT-LIKE-003` + global `E60` + 共享层 **28 项（A 类 0）**（🔴 **2026-10-29 主线订正**：原写「27 条」⇒ 共享层基数已订正为「**28 项未修 · A 类 0**」⇒ 本树 `docs/DRIFT/global/vs-common.md` 尚未合入该订正 ⇒ **合并后自动一致**）；**§九 证据缺口**：`G-3`（article 双侧活体 0 条，⚫ **可解除**）· `G-5`（hot-common 探针在 `/tmp` ⇒ **本轮已闭合，可撤**）· `G-6` / `G-7` / `G-13`（4 域无失败路径矩阵，含 **article + hot**）· `G-15` / `G-18`（**本轮已订正**）· **`G-19`**（proto 消费方式未取证，10 个 proto 全部）· `G-20`（共享面 `:45` 已过期）。
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

- `docs/DRIFT/article/api.md` ⇒ §7 状态表 + §9 UNRESOLVED 为权威现状面；**`DRIFT-ARTICLE-API-006`（8~10 MiB body 文案）已确认 LOW 不修**（characterization test 锁住现状 ⇒ 改它会撞红）；`DRIFT-ARTICLE-API-012` 只钉了 2 个 DTO 的字段序 ⇒ 其余 DTO 尚未逐个核对；`DRIFT-ARTICLE-API-009`（原始非 ASCII 请求行）已判 LOW 不修。
- `docs/DRIFT/article/rpc.md` ⇒ §7 + §9 为权威面；`U-3`/`U-4`（真 Kafka）已闭、**`U-4` 的承重项订正为 `FixedBackOff(0L,0L)`**（不是 `setSeekAfterError`）· `U-9b`（Go 1 拉取者 + 8 handler 抢同一 channel vs RAG 8 consumer 各占分区 ⇒ **RAG 更严**）仍 🟡 取证缺口 · `S-5` 经 judge §1.1 **R0** 改判「**范围外**」（Go 侧 `knowledge/points/security` 不迁移）⇒ **实为永不闭环、不是待办**。
- `docs/DRIFT/article/common.md` ⇒ Go↔Java **sha256 逐字节一致** + 契约测试 15 条 + 变异 8/8 全红 ⇒ 无编号未决项；⚠️ `:22`/`:74-77` 的「消费侧仍是空骨架」描述**已被现状推翻**（article-api 41 文件 5759 LOC）⇒ **过期描述，待订正**。
- `docs/DRIFT/article/proto.md` ⇒ 行为层一致、唯一差异是 option 块；§3.4 三项效力边界（presence / 字段号硬编码 / Jackson）逐条已验；**`P-U-1` 跨栈 gRPC 活体仍 OPEN**。
- `docs/DRIFT/article/vs-common.md` ⇒ §2 九轴 A1~A9（**A4/A6/A8 = L4**，A1/A2/A3/A9 = L3）· §6 域内未闭合清单 · §4 派单面 · **文末「阶段归属定级附录（2026-10-30 · Go 对照）」= 本域定级权威面**（`A-3` 判 **C/P2**、`A-5` 判 **B/P1 · 域侧写权**、`A-4`/`G4-2`/`G4-6`/`G4-9`/`G4-10` 判 **D 差异已消失** ⇒ **与正文 🔴 口径冲突，派单前须以附录为准**）。
- `docs/Intentional_Deviations/article/` ⇒ 本域**只有 2 条**已签字（`SD-2026-10-24-01` 锁序统一 `article → publication`（Go 自身锁序反转 ⇒ Go 侧探针实测 `40P01 deadlock detected` ⇒ **改回去 = 主动复现死锁**）· `SD-2026-10-24-02` Kafka 四通道不能逐通道切 broker）⇒ `article/{api,common}.md` = 0 字节空档（**已核，非漏写**）。⚠️ 另有 3 条 api 漂移（`-001`/`-006`/`-009`）**不是**签字偏离 ⇒ 不进签字档。
- `docs/Pitfalls/article/` ⇒ api 22 条 + rpc 16 条 + common 0 条；最关键：`P-4`「注解存在 ≠ 机制生效」（50 条测试**恰好把要验证的那一段替换掉了**）· `P-15` 变异模块没重编 ⇒ 假的仍全绿 · `P-21`「仍全绿」的第一嫌疑是「**变异没打中**」· `P-22`「javadoc 写 Go 出处是**待核实断言**」· `PF-ARTICLE-RPC-003`「Go 用一段 catch 统一映射的分支**漏了**」。
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

- **本域落码点**：`service/article/article-rpc` 补 Actuator servlet 载体（Go 观测端口 **9092**）
  - RAG 侧现状：`pom.xml:171`（actuator）＋ `:175`（micrometer-prometheus）**无** `spring-boot-starter-web`；`application.yml:232-247` 的 `management.endpoints.web.exposure.include` 是**死配置**
  - 本域附带订正：`application.yml:242` 的注释写「对齐 Go prometheus 的 Subsystem: article_rpc（metrics/metrics.go）」⇒ **Go 侧 `service/article/rpc/internal/` 下根本没有 metrics 包**（`article.go` 全文无 metrics import）⇒ ⇒ **该出处不存在** ⇒ 本轮据实改写（Go 侧只有 go-zero 默认 registry；`article_service_*` 是 RAG 自有 meter ⇒ **比 Go 宽**的一面 ⇒ 登记）
  - 本域新守门：`ArticleRpcPrometheusCarrierGateTest`（**真 fat jar** ＋ **真 PG 容器** ⇒ 真启动后打 `/actuator/prometheus`）⇒ 容器宿主口按本域登记册新增一条（`22861`）

## 10. 🟢 **DDL 逐域机器核**（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G3 lane · `article` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并逐域实跑，结论只落在 `docs/PhaseGoal-judge.md` §七 **G-8 / G-9** ⇒ **域级证据链断了一节** ⇒ 本节把 `article` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不改任何差异**（RoundPlan T-3.1：冒出的差异按域登记，不当场修）。
> ⚠️ **`article` 域不是 N/A** —— `hot` 域才是 N/A（两侧无数据库）；**本域两侧都有真库**（Go 侧 5 张表 / RAG 侧 5 张表）⇒ 本节是**实跑**结论，不是「本维度不适用」。
> ⚠️ **本域触发器维度两侧各 1 个 ⇒ 可比对且一致**（**不是**「无对象可比」）—— 这是本域与 `follow` / `hot` 域的关键区别。

### 10.1 执行口径（命令原文 · EXIT · 日期 · 环境）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（14:2x–14:5x 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b` · `align/r3-ddl-g3-2`<br>基点 = `do-my-own-gh` @ **`d10227c5`** |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本只**复制**副本，从不写入 |
| 容器 / 库 | `ddl-article-pg`（`postgres:16-alpine` · 端口 **55560**）· Go 库 `go_article_db` · RAG 库 `rag_article_db` |
| Go 侧 schema 来源 | **真源自己的 `AutoMigrate`** + **真源自己的手写 `.sql`**：`service/article/rpc/internal/model/db.go:29`（`func InitDB(c config.Config) (*gorm.DB, error)`）→ `:55-58`（`db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})`）⇒ 之后由 `service/article/rpc/internal/model/002_community_revision.sql` 建 `article_revision`（`:4`）· `article_publication`（`:21`）· `article_domain_outbox`（`:29`）· 索引（`:41`）· **触发器 `article_revision_immutable`（`:51-53`）** |
| ⚠️ Go 侧的**启动契约** | `db.go:61-71`：`InitDB` 在 AutoMigrate 之后**必须**查到 `article_revision_immutable` 触发器，缺则 `return nil, fmt.Errorf("community revision migration 002_community_revision.sql is required before article RPC startup")` ⇒ 探针**第一次跑必然报 `INITDB_ERR`** —— 这是**真源的启动契约**，不是 bug（补放 `002` 后即通过） |
| RAG 侧 schema 来源 | 仓内 `service/article/article-rpc/src/main/resources/db/migration/`：`V8__create_article.sql` · `V9__create_article_sync_outbox.sql` · `V10__create_article_community_revision.sql`（**Flyway**；生产 `locations: classpath:db/migration` ⇒ `application.yml:77` · `baseline-version: 0` ⇒ `application.yml:76`） |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 差异数是**原始值** |
| 两侧表集合 | Go `go_article_db` = {`article`, `article_sync_outbox`, `article_revision`, `article_publication`, `article_domain_outbox`} · RAG `rag_article_db` = **同名 5 张** ⇒ **表名两侧完全相同**（Go 的 `NamingStrategy{SingularTable:true}` + `TableName()`）⇒ `table_map` 为空（写一个映射进去 = **凭空制造列集合不一致**） |

**命令 ① —— 建库 + 双侧活体对拍（索引 / 约束 / 触发器 + 源码↔活库 一次跑全）**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b"
bash tools/difftest/ddl-parity.sh article
```

⇒ **EXIT=0** —— 末行 `== 实质差异合计 0 处 ⇒ 退出码 0 ==`；runner 行 `=== 对拍退出码: 0 ===`
⇒ Go 探针第一次打印 `INITDB_ERR: community revision migration 002_community_revision.sql is required before article RPC startup`（**真源启动契约**，见上），补放 `002` 后建表成功。

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`）**

```bash
python3 tools/difftest/check-ddl-static.py --domain article --indexes --check-source
```

⇒ **EXIT=0**（0 处）—— 与 ① 同数同因 ⇒ 两条路径互为复算

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain article --print-rag-sql
```

⇒ **EXIT=0**，输出 **3 行**（V8 / V9 / V10），与 ① 实际执行的 3 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**（清单由**目录扫描**产生 ⇒ 单一产生点）

### 10.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（G-9 `--check-source`） | 5 张表 | ✅ **0 差异** —— 仓里的 3 份 `.sql` 在**干净临时库** `rag_article_db_srccheck` 上原样重放后，与活库**逐项一致**（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器） | 0 |
| 2 | **列类型 / 长度** | **56 列 ↔ 56 列**（键集合一致 ✓） | ✅ **0 差异** —— 脚本原话「**逐字相同 56 列**」 | 0 |
| 3 | **可空性（NOT NULL）** | 56 列 | ✅ **0 差异** —— 脚本原话「（两侧完全一致 ✓）」 | 0 |
| 4 | **DEFAULT** | 56 列 | ✅ **0 差异** —— 脚本原话「（两侧完全一致 ✓）」 | 0 |
| 5 | **索引**（`pg_indexes`） | Go **16** / RAG **16** | ✅ **0 差异** —— 脚本原话「（两侧索引定义逐条一致 ✓）」⇒ 连**索引名**都逐字相同 | 0 |
| 6 | **唯一约束 / 主键**（`pg_constraint`） | Go **12** / RAG **12** | ✅ **0 差异** —— 脚本原话「逐条一致 12 行 ✓」（两侧约束集合完全一致 ✓） | 0 |
| 7 | **触发器**（`pg_trigger`） | Go **1** / RAG **1** | ✅ **0 差异** —— **同名同表 1 个 ✓**（两侧触发器集合完全一致 ✓）⇒ ⚠️ **本维度有对象可比，不是「无对象可比」** | 0 |
| 8 | **触发器形式**（`pg_get_triggerdef` → 结构化属性） | 两侧各 1，**可比对 1** | ✅ **0 差异** —— 「1 个可比对的触发器形式逐条一致 ✓」 | 0 |
| 8′ | **触发器函数体**（`pg_proc` → 结构化语义属性，**不比 SQL 文本**） | 两侧各 1，**可比对 1** | ✅ **0 差异** —— 「1 个可比对的函数属性逐条一致 ✓」 | 0 |
| | **合计** | | | **0** |

⇒ **本域 DDL 在 8 个被核维度（触发器拆成集合 / 形式 / 函数体三条 ⇒ 实为 **9** 条判据）上 0 处残留。**

### 10.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 56 列逐字相同**

- 脚本原话：`DDL 列级对比  go_article_db=56 列  rag_article_db=56 列  （键集合一致 ✓）` + `逐字相同 56 列。`
- ⇒ **本域没有 `users` 域那种「Go `text` vs RAG `varchar(255)`」的位宽问题**：Go 侧 `secondary_tags` / `ext_info` 是 `jsonb`（自定义类型 tag `gorm:"type:jsonb"`）⇒ RAG 侧 V8 也写 `JSONB` ⇒ **两侧逐字相同**。

**② 可空性 / DEFAULT —— 两侧完全一致（0 处）**

- `article` 表：Go 侧除 `id` / `title` 外**都可空**（Go 的 DDL 不加 `NOT NULL`）⇒ RAG 侧 `V8` **照抄**，未「顺手收紧」（收紧会让 Go 侧原本能写入的行在 Java 侧被拒 ⇒ 新增行为）。
- ⚠️ **本域两侧都没有 `DEFAULT CURRENT_TIMESTAMP` 残留** —— 与 `users` / `comment` 域不同：`article` 的三个时间列 `created_at` / `updated_at` / `deleted_at` 两侧都是 **`TIMESTAMPTZ` 且可空、无 DB 默认**（Go 的 `autoCreateTime` 由 GORM 在 Go 代码里赋值 ⇒ 不建 DB 默认；RAG 侧照抄了这一形态）。

**③ 索引 —— 16 / 16 逐条一致（连名字都逐字相同）**

- 脚本原话：「（两侧索引定义逐条一致 ✓）」⇒ **本域 16 条索引的 `pg_indexes.indexdef` 逐字相同**（含主键索引与各列索引）。

**④ 唯一约束 / 主键 —— 12 / 12 逐条一致**

- 脚本原话：「逐条一致 12 行 ✓」+「（两侧约束集合完全一致 ✓）」⇒ **同表同名同定义**。

**⑤ 触发器 —— 1 / 1 同名同表（**本域最硬的那条约束**）**

| 侧 | 触发器 | 事件 | 目标表 | 函数 |
|---|---|---|---|---|
| Go | `article_revision_immutable` | `BEFORE UPDATE OR DELETE` | `article_revision` | `reject_article_revision_mutation()` |
| RAG | `article_revision_immutable` | `BEFORE UPDATE OR DELETE` | `article_revision` | `reject_article_revision_mutation()` |

⇒ 脚本的结构化属性逐项一致：`fn=reject_article_revision_mutation  rettype=trigger  args=<无>  volatility=VOLATILE  is_trigger=trigger  lang=plpgsql`（**go 与 rag 两行逐字相同**）。
⇒ Go 出处：`service/article/rpc/internal/model/002_community_revision.sql:51-53`（`CREATE TRIGGER article_revision_immutable BEFORE UPDATE OR DELETE ON article_revision FOR EACH ROW EXECUTE FUNCTION reject_article_revision_mutation();`）· RAG 出处：`service/article/article-rpc/src/main/resources/db/migration/V10__create_article_community_revision.sql:60`（函数）+ `:68-70`（触发器）。
⇒ ⚠️ **这条触发器是「article 修订不可变」语义的唯一载体** ⇒ 触发器维度**不是「无对象可比」**（对比 `follow` / `hot` 域两侧各 0 ⇒ 只能写「无对象可比」）⇒ 本域这一维度是**真比对过且一致**。

**⑥ 源码 ↔ 活库 —— 0 差异（G-9 维度，本轮新增）**

- 目录扫描得到 **3 份**迁移脚本 ⇒ **清单由目录扫描产生**，不会落后于仓。
- 在**干净临时库** `rag_article_db_srccheck` 上**原样重放**（交给 PG 自己解析）⇒ 与活库逐项对比：**表集合 5 ↔ 5** · **列集合 0 差异** · **列属性 0 差异** · **可空性 0 差异** · **DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器）`
- ⇒ **含义**：本域仓里的 `.sql` 与**当前活库**之间**没有分叉** ⇒ 「迁移改了没人重跑」这一类漂移**在 `article` 域为零**。

### 10.4 残留

**本域残留 = 0 处。** ⇒ **无一条需要按 `docs/twoStage.md` §十 定级**。

⚠️ **「0 残留」不等于「全维度已核」** —— 下列维度**无对象可比**或**脚本不查**，其结论是「**无对象可比**」/「**人工补查**」，**不是「已对齐」**（§10.6）：

| 维度 | 状态 | 说明 |
|---|---|---|
| CHECK / 外键 / 分区 / 存储过程 | ⚪ **人工补查 ⇒ 两侧各 0** | 见 §10.6 的效力边界表（**人工闭合，非脚本原生结论**） |
| 触发器**函数体的语义**（它到底在拒绝什么） | ⚪ **人工对照已核** | Go `002_community_revision.sql:44-48` 的函数体 `RAISE EXCEPTION 'article revision is immutable';` ⇒ RAG `V10:60-66` 同 ⇒ **两侧同一条拒绝**；⚠️ **函数体的语义机器判不了**（脚本只比结构化属性）⇒ 本条为**人工核对** |
| 索引是否被真实查询计划选中 | ⚪ **未核** | 需 `EXPLAIN` + 真实数据量 ⇒ 本轮**未核**；本域两侧索引**定义全同** ⇒ 查询计划**无索引层面的差异来源**，实际代价未量化 |

**签字状态**：`docs/Intentional_Deviations/article/` 下 `api.md` / `common.md` 是 **0 字节空占位**，`rpc.md` 两份（`SD-2026-10-24-01` 全局锁序 · `SD-2026-10-24-02` Kafka 通道切 broker）**均与 DDL 无关** ⇒ ⇒ **本域零 DDL 类签字偏离** ⇒ 本节**没有**「已签字 ⇒ 不要重复质疑」的条目需要回避，也**没有**「已登记待签字候选」条目。
⚠️ **空占位 ≠「已签字」**（`docs/Pitfalls/users/admin/rpc.md` **P-9**）—— 这里只是说「**本域没有需要按签字档回避的 DDL 差异**」。

### 10.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：`service/article/**` 的**代码与台账由本波次的另一条 lane 在改**（AGENTS.md §5.3d 并发纪律）⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**（任务书允许的「**临时副本**」路线）：把 `check-ddl-static.py` 与该域的 `db/migration/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ `REPO_ROOT` 与仓内布局一致），**逐字节校验 sha256 相同**，再在**副本**上做变异。
> ⇒ 守门读到的**仍是仓里那份 `.sql` 的内容**（同一份字节），只是不在仓里写。

**变异 ①：`article.status` 的列类型（`SMALLINT` → `INTEGER`）**

| 步骤 | 命令 | 结果 |
|---|---|---|
| 副本 sha256（变异前） | `shasum -a 256` 副本 vs 仓内 3 份 `.sql` | **3/3 逐字节相同**（`V8` = `7eede910c871653fa975f5a672798d361b4c42aa94a76db0f4fa454d55eb6613` · `V9` = `e211dfb80225d23119879a112ea40fd1e914fa00d4d7bde4a75a89352b2fe721` · `V10` = `538c956ec91671dd0064bca942313e83849116bb650d1869870dab42404d795e`） |
| 基线 | `python3 tools/difftest/check-ddl-static.py --domain article --indexes --check-source`（临时树） | **EXIT=0**（0 处）· 源码↔活库 = `✅ 一致` |
| 变异 | `V8__create_article.sql`：`    status          SMALLINT      DEFAULT 0,` → `    status          INTEGER       DEFAULT 0,`（改**一个列的类型**，命中目标行 **1** 处） | — |
| 变异后重跑 | 同上命令 | **EXIT=1**（**1** 处）· 源码↔活库 **转红**：<br>`🔴 列属性不同：article.status  源码=('integer', '')  活库=('smallint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 改回 `SMALLINT` | — |
| 还原后重跑 | 同上命令 | **EXIT=0**（**0** 处 · 回到基线）· 源码↔活库 回到 `✅ 一致` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **3/3 仍逐字节相同**（`V8` 仍是 `7eede910…eb6613`）⇒ 逐字节回到变异前 |
| 副本清理 | `rm -rf` 临时树 | **已删除**（复查：目录不存在）⇒ **工作树里不留临时副本** |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`article.status`），**没有多红也没有漏红** ⇒ 守门**不是假绿**；还原后**精确回到 EXIT=0** ⇒ 无残留。
⚠️ 本域基线就是 **EXIT=0** ⇒ 「变异必红 ⇒ 还原必绿」的**两端都是干净的 0/1 往返**（对比 `favorite` 域：基线 EXIT=1 ⇒ 变异后 3 ⇒ 还原后 1）。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b"
git status --short -- service/     # ⇒ 空
git diff --stat -- service/        # ⇒ 0 行
```

⇒ `service/article/**` 与 `service/favorite/**`（同波次另一 lane 在改）**零改动**。

### 10.6 证据强度 · 效力边界 · 未能核到的部分

**证据强度 = E4（`docs/twoStage.md` §8.1「运行证据」）＋ L4（域台账分层：运行面 · 双侧活体）** —— 两侧各建一个**真 PG 16** 库（Go 侧跑真源自己的 `AutoMigrate` + 真源自己的 `002_community_revision.sql`、RAG 侧跑仓里的真迁移脚本）⇒ 逐列 / 逐索引 / 逐约束 / 逐触发器 dump 后机器比对。
⚠️ 本节所有 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。

**效力边界（越界即失效）**

| # | 边界 | 对本域是否成立 |
|---|---|---|
| 1 | **`--check-source` 的重放引擎 ≠ 生产引擎** —— 重放用 **psql**；本域生产用 **Flyway 引擎**（`application.yml:77` `locations: classpath:db/migration`）⇒ 两个切句器不同 | ✅ **对本域不成立** —— 已核：3 份 `.sql` 的 `$$` 命中 **0** ⇒ 无美元引号块 ⇒ 两个引擎看到的语句序列相同；且三份脚本只含 `CREATE TABLE IF NOT EXISTS` / `CREATE OR REPLACE FUNCTION` / `CREATE TRIGGER` / `DROP TRIGGER IF EXISTS` 形态 |
| 2 | **迁移清单 = 生产清单** —— `discover_rag_sql` 目录扫描的 3 份，与生产 Flyway `locations` 指向的目录内容**逐条一致**（命令 ③ 与 ① 的执行日志互证） | ✅ **成立** |
| 3 | **Flyway 账本表** —— 生产库会有账本表（`application.yml:53` `flyway:` 段；`article-rpc` 用**默认表名** `flyway_schema_history`，见 `application.yml:56-59` 的注释），但**对拍环境直接用 psql 放 `.sql`** ⇒ 两个库里**都没有**账本表 ⇒ 本轮表集合就是 {5 张业务表} | ✅ **本轮无干扰**；⚠️ 换用真 Flyway 起环境时，账本表会出现在 RAG 侧 ⇒ 工具靠 `exclude_tables=("flyway_schema_history",)` 前缀匹配排除（**已配置**） |
| 4 | **`baseline-version: 0`**（`application.yml:76`）—— 生产拓扑（共享非空 schema）下 V1 **必须**执行，否则表建不出来 | ⚠️ **本轮未核** —— 对拍用的是**全新空库** ⇒ baseline 不触发 ⇒ V1 正常执行。⚠️ **这正是该配置注释里点名的陷阱**（`application.yml:56-75`）⇒ **该维度的守门在域侧真 PG + 真 Flyway 引擎的用例里**，不在本节范围 ⇒ **登记为「未核」** |
| 5 | **触发器函数体的语义**机器判不了 ⇒ 走人工逐条 | ✅ **已人工核对**（§10.3 ⑤）—— 两侧函数体都是 `RAISE EXCEPTION 'article revision is immutable'` ⇒ **同一条拒绝** |
| 6 | **`pg_constraint` 只比 `contype` + 定义文本，不解析外键的引用目标**（脚本文件头第 44–45 行） | ✅ **不适用**（本域两侧 `contype='f'` 各 **0 行**，见 §10.6 的人工补查表） |
| 7 | **分区 / 存储过程 / 触发器函数** —— 脚本不查 | ✅ **不适用**（3 份 `.sql` 的**可执行部分**无 `PARTITION` / `PROCEDURE` 关键字 ⇒ 已人工核；两侧 `pg_partitioned_table` / `prokind='p'` 均 **0**） |
| 8 | **Go 侧 schema 与生产的完全等价性** | ⚠️ **有一处未核** —— 本域 Go 侧**有**手写 `.sql`（`002`）且 `ddl-parity.sh` 的 `GO_SQL` 清单**确实包含**它 ⇒ 跑的是「AutoMigrate → 补放 002」的**app-first** 顺序。⚠️ **本域的 `002` 用的是 `CREATE TABLE IF NOT EXISTS`**（`002:4` · `:21` · `:29`）⇒ 在 AutoMigrate 已建的表上会**整表跳过** —— 但本域 `article_revision` / `article_publication` / `article_domain_outbox` **都不在 AutoMigrate 列表里**（`db.go:55-58` 只 AutoMigrate `Article` + `ArticleSyncOutboxEvent`）⇒ ⇒ **`002` 的三张表都是新表 ⇒ 整条执行成功** ⇒ ⇒ **本域不存在 `favorite` 域那种「顺序敏感性」**（见 [`favorite/vs-common.md` §9.4](../favorite/vs-common.md)）⇒ ⇒ **本域 app-first ≡ sql-first**。⚠️ **但「Go 生产上 `002` 是否真被执行」本身未核**（真源无任何 Go 代码执行它 ⇒ 属运维手工步骤）⇒ **登记为「未核」** |

**人工补查（脚本**不查**的维度 —— 结论强度 = **L4 + 人工补查**，**非脚本原生结论**）**

| 维度 | Go 侧 `go_article_db` | RAG 侧 `rag_article_db` | 判定 |
|---|---|---|---|
| 外键（`pg_constraint` `contype='f'`） | **0 行** | **0 行** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 分区表（`relkind='p'` / `pg_partitioned_table`） | **0** / **0** | **0** / **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 存储过程（`prokind='p'`） | **0** | **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 函数（`prokind='f'`，**非触发器**） | **0** | **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 触发器函数（被 `pg_trigger` 引用） | **1**：`reject_article_revision_mutation` | **1**：`reject_article_revision_mutation` | ✅ **同名 ⇒ 人工闭合**（语义另由 §10.3 ⑤ 人工核对） |

**未能核到的部分（明确写「未核」+ 原因，不含糊）**

| # | 未核项 | 原因 | 影响 |
|---|---|---|---|
| 1 | **共享库拓扑下的 Flyway `baseline-version: 0` 行为** | 对拍用**全新空库** ⇒ baseline 不触发 ⇒ 该配置**未被本节验证** | ⚠️ 该维度的守门在域侧真 PG + 真 Flyway 引擎的用例里（`application.yml:56-75` 明写这个陷阱）⇒ 本节**不重复核**，只登记边界 |
| 2 | **Go 生产上 `002_community_revision.sql` 由谁执行** | 真源**没有任何 Go 代码**执行它 ⇒ 属运维手工步骤 ⇒ 本节只核了「AutoMigrate + 补放 002」这一种组合 | ⚠️ 若生产上**从未**执行 `002` ⇒ Go 生产库**缺** `article_revision` / `article_publication` / `article_domain_outbox` 三张表 ⇒ 那是**部署面**问题（不是 DDL 漂移）⇒ 本节**无法从 schema 对拍看出** |
| 3 | **索引是否被真实查询计划选中** | 需 `EXPLAIN` + 真实数据量 ⇒ 本轮**未核** | 本域两侧索引**定义全同** ⇒ 查询计划**无索引层面的差异来源**；实际代价未量化 |
| 4 | **触发器在真实 UPDATE/DELETE 下的拒绝行为** | 本节只比**结构化属性**，**没有**真的发一次 UPDATE 去撞触发器 | 两侧触发器定义**逐字相同** ⇒ 拒绝行为**无差异来源**；但「RAG 侧 `article_revision` 的 UPDATE 确实被拒」这一**运行期**事实**不在本节证据内** |

### 10.7 与其它台账的关系（指针，不改别的文件）

| 台账 | 本节与它的关系 |
|---|---|
| [`docs/DRIFT/article/rpc.md`](rpc.md) | 本域**组件级**台账；本节是 **DDL 这一维度**的域级落账（域内 DDL 残留 **0**）⇒ 与那一份台账的其它维度结论**互不冲突** |
| 本文件 **§2 的 A8 轴** | A8 行已记 `L4`（`check-ddl-static.py --domain article` ⇒ EXIT=0 · 两侧各 56 列逐字相同）⇒ ✅ **本节是 A8 那一格的**扩展**（补上 `--indexes`（索引 / 约束 / 触发器）与 `--check-source`（源码↔活库）两层）⇒ **A8 原结论不变**（仍读作「✅ DDL 列级一致」），只是**证据维度变宽** |
| 本文件 **§2 的 A8 轴「`baseline-version: 0` 缺」那条** | 🔴 **已被后续事实推翻** —— `application.yml:76` **现在就有** `baseline-version: 0`（2026-10-27 G3 域侧自修已落码）⇒ A8 那行「🔴 但 `baseline-version: 0` 缺（`application.yml:53-57`）⇒ 域侧自修（见 §3）」**是陈旧的** ⇒ ⚠️ **本 lane 不改 §2**（该行属本文件但改动需与另一 lane 在改的 article 台账协调）⇒ **只在此登记，报给收口执行者** |
| [`docs/DRIFT/_cross/ddl-type-divergences.md`](../../DRIFT/_cross/ddl-type-divergences.md) | 🔴 **该文件属 `_cross` ⇒ 本 lane 无写权** ⇒ 只在此登记：主线对 `article` 域记「类型/长度 **0 差异**」⇒ **与本节独立复跑结论一致**（本节另补了索引 / 约束 / 触发器 / 源码↔活库四层 + 变异验证） |
| [`docs/PhaseGoal-judge.md` §七 **G-8 / G-9`](../../PhaseGoal-judge.md) | 主线结论（G-8 / G-9 已闭合 · 9 域逐域跑）—— 本节是 `article` 域的**域级落账**；⚠️ 该文件**主线写** ⇒ 本 lane 只读 |

---

## 📌 追加 · 🟡 **共享层轴后果：rpc 域首次成为 servlet web app（2026-10-30 · G3 第三轮）**

> 本节只记**共享层轴**的后果（域内落地的细节在同域的 `rpc.md`）。🟡 = **待共享层裁定**，**不是**缺陷。

**🔴 轴事实一：共享层那批 `@ConditionalOnWebApplication(SERVLET)` 的自动配置，在 rpc 域**首次生效**

- 触发条件：本轮给 `article-rpc` 补了 `spring-boot-starter-web`（Actuator 的 servlet 载体）⇒ ⇒ **rpc 进程首次成为 servlet web app** ⇒ ⇒ 共享层里带 servlet 条件的**自动配置**（`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 列的那几个）**第一次**会在 rpc 容器里装载。
- 实测命中（`article` 域）：`com.rag.common.http.GoStyleJsonContentTypeAdvice`（`@AutoConfiguration` ＋ `@ConditionalOnWebApplication(SERVLET)` ＋ `@RestControllerAdvice`）⇒ 它**只改 JSON 响应的 `Content-Type`**（`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:187-206`）⇒ ⇒ `/actuator/prometheus` 是 **text/plain** ⇒ **不受影响** ⇒ 实测抓取 **HTTP 200** ＋ 指标族正常；⇒ 但 `/actuator/health`（JSON）会拿到 `application/json;charset=utf-8`。
- ⇒ ⇒ 这条**正是**共享层正在裁的那个议题（「rpc 域观测端口要不要挂 Go 的 rest 中间件」）的**落点** ⇒ ⇒ **归 G4 裁定**，域侧**只登记、不自决**。

**🔴 轴事实二：`rag.side` 这个属性共享层**当前还没有消费点**

- 域侧本轮已在三份 rpc 的生产 yml 里写 `rag.side: rpc`（`article` 域的落点是 `service/article/article-rpc/src/main/resources/application.yml`）⇒ 那是**为 G4 正在扩的那道门预留的对侧**（门若写成 `matchIfMissing=true` ⇒ **不写 = 默认 api 侧** ⇒ rpc 域会挂上 api 侧的 web Bean）。
- ⚠️ **如实标注**：**2026-10-30 实测** `grep -rn "rag.side" service/common/src/main/java/` **零命中** ⇒ ⇒ 共享层**当前还没有**消费这个属性的条件 ⇒ ⇒ 域侧这三行**现在还没有消费点** ⇒ ⇒ 门落地前**不生效**、落地后**必须已在位**。
- ⇒ ⇒ 域侧**运行期**断言已就位（`articleRpcSharedLayerWebBeanGateTest`，3 例：属性在位 ＋ **正向对照**（证明 servlet 前提成立 ⇒ 断言非空转）＋ api 侧那 5 个 web Bean **不在**容器里）⇒ ⇒ 它现在证明的是「那 5 个 `@Component` 中间件因为 rpc **不扫** `com.rag.common` 而不在容器里」⇒ ⇒ 等 G4 把门扩到全部 api 侧中间件后，**自动升级**为「`rag.side` 真的挡住了门」的运行期证据。

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


### lane W25

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| G4-4 | C（阶段 2 最终工程建设项 · 共享面文档订正） | P3 | 否 | Go `service/common/observability/observability.go:24-29`（共享层六个 attribute key = `sea.error.kind` / `sea.biz_code` / `sea.biz_msg` / `sea.timeout` / `sea.slow` / `sea.duration_ms` ⇒ **共享层是 `sea.*`，不是 `audit.*`**）＋ `service/comment/rpc/internal/logic/update_subject_state_logic.go:53-56`（`audit.operator_id` / `audit.target_type` / `audit.target_id` / `audit.target_state` ⇒ `audit.*` 是 **comment 域**的形状 ⇒ **article 台账原文「`audit.*` 只存在于 users/admin 域」漏了 comment 域**）＋ `service/favorite/api/internal/logic/favorite/delete_favorite_logic.go:43-44`（`biz.user_id` / `biz.favorite_id` ⇒ favorite = `biz.*`）＋ `service/article/rpc/internal/logic/create_article_logic.go:45-46`（`author_id` / `title` ⇒ **article 域业务 span 是裸名，无任何前缀**）vs RAG `docs/DRIFT/_cross/domain-common-lanes.md:85`（A9 行**至今仍写**「span 属性（`audit.*`）」⇒ 措辞缺陷**未被订正**）＋ `service/common/src/main/java/com/rag/common/observability/ObservabilityConstants.java:67`（`sea.error.kind` ⇒ RAG 共享层与 Go 同名同值 ⇒ **错的只有轴定义那一行**）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleSpans.java:175`（`ATTR_AUTHOR_ID = "author_id"` ⇒ RAG article 侧裸名，与 Go `create_article_logic.go:45` 逐字一致）＋ `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleSpanWiringTest.java:103`（「没有 `audit.` 前缀（那是 comment 域的形状）」⇒ RAG 侧已按裸名落码并钉死）＋ `docs/DRIFT/article/vs-common.md:48`（本域 A9 行已自订正「属性名是 `sea.*` … 不是 `audit.*`」⇒ **对 article = N/A，article 域自身无漂移**）⇒ **差异仍成立**（缺陷只在共享面 `_cross` 文档的**措辞层**，运行时零影响 ⇒ 落点 = `docs/DRIFT/_cross/domain-common-lanes.md` = 共享面 ⇒ 合并收口执行者订正；G4 已登记 `docs/DRIFT/global/vs-common.md:259`（建议项 2）与 `:431`（处置表）⇒ 本域不重复派单）｜不阻断阶段一（零运行时影响 · 只影响 A9 轴的**判定口径**，10 份域台账据此判「A9 已对齐」）｜修法已由 G4 写成可执行的分层表述（共享层 `sea.*` 两侧一致 ＋ 域特有按域各异：comment=`audit.*` · favorite=`biz.*` · admin=`audit.*` · article=裸名）⇒ 属阶段二最终工程建设项 | 2026-10-30 |

### lane W4

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| A-3 | C | P2 | 否 | Go 1.25.11 `src/net/url/url.go:1001`（`const defaultMaxParams = 10000`）＋ `:1019-1021`（`func parseQuery(m Values, query string) (err error) { if !urlParamsWithinMax(strings.Count(query, "&") + 1) { return errors.New("number of URL query parameters exceeded limit") }` ⇒ **段数 = `Count("&")+1`** ⇒ 放行 10000 段、第 **10001** 段拒）vs RAG `service/common/src/main/java/com/rag/common/http/GoStyleFormValues.java:215`（`public static final int MAX_URL_QUERY_PARAMS = 10000;`）＋ `:262`（`if (countQuerySegments(query) > MAX_URL_QUERY_PARAMS) { return "number of URL query parameters exceeded limit"; }`）＋ `:307`（`private static int countQuerySegments(String query) { int count = 1; … count++ per '&' }` ⇒ **与 Go 的 `Count("&")+1` 逐字同形**）＋ `service/common/src/main/java/com/rag/common/http/GoStyleTomcatParameterLimitCustomizer.java:170`（`public static final int MAX_PARAMETER_COUNT = 10001;`）⇒ **差异仍成立（差一格）**：应用层判据两侧**已同形**（10000 放行 / 10001 拒），但**容器层上限 = 10001** ⇒ RAG 的实际翻转点在 **10002 段**、Go 在 **10001 段** ⇒ 修法 = 共享层 `MAX_PARAMETER_COUNT` **10001 → 10000**（`GoStyleTomcatParameterLimitCustomizer.java:170`）⇒ 落点 `service/common`（共享层 ⇒ 非本域写权）⇒ 归 **C**（阶段二由 G4 收口，不阻断阶段一准入；本域 `favorite` 的 `mode: off` ⇒ 共享层那道闸门不装 ⇒ 该域闸门在域内 `FavoriteGoQueryLimitInterceptor`） | 2026-10-30 |
| A-4 | D | P3 | 否 | Go `service/article/api/etc/article-api.yaml:1`（Go 真源侧 **不含** 任何根级 compose / `.env.example` 治理面 ⇒ Go 侧**无对应物** ⇒ 不构成 Go 对照项）＋ `service/article/api/article.go:21`（`var configFile = flag.String("f", "etc/article-api.yaml", "the config file")`）＋ `:27`（`conf.MustLoad(*configFile, &c)` ⇒ Go 侧配置/凭据面**只在 `etc/*.yaml`**，无 env / compose 治理面）vs RAG `docker-compose.yaml:15`（`POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?set POSTGRES_PASSWORD in .env}` ⇒ **`:?` 已在位**，缺变量 ⇒ compose 拒绝渲染）＋ `docker-compose.yaml:182`（`USER_JWT_SECRET: ${USER_JWT_SECRET:?set USER_JWT_SECRET in .env (>=32 bytes; must match the issuer user-api)}`）＋ `service/article/article-api/src/main/java/com/rag/article/article/api/config/ArticleApiProperties.java:135`（`requireSecret(secret, "article-api.auth.access-secret", "USER_JWT_SECRET");` ⇒ 凭据缺失 ⇒ **启动期 fail-loud** ⇒ 与 compose 的 `:?` 构成双保险）＋ `README.md:89`（表格行三列 —— 变量名 / 适用范围 / 缺值行为 —— 其中「compose 用 `:?` ⇒ 缺值**拒绝渲染**」）＋ `.env.example:1`（**根 README 的 `.env.example` 已存在** ⇒ 台账 `DRIFT/article/vs-common.md:363` 记的「根 README 的 `.env.example`」这半边已闭合）⇒ **差异已消失**（P0 #8 的**根级半边**已落：`compose :?` + 根 README + `.env.example` 三件齐 ⇒ 域侧那半更早已修）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| G4-10 | D | P3 | 否 | Go `service/article/api/internal/handler/routes.go:1`（Go 真源仓的**领域无关**结构性标志文件之一 —— 共享实现的判定标志必须是「与域无关」的文件，如 `go.mod` / `proto/` / `service/`，否则「只跑某域的脚本」与「只跑另一域的脚本」判据不同 ⇒ 同一个仓、两个结论）vs RAG `tools/difftest/go_repo.py:1`（**共享实现**：环境变量优先**且校验** → 否则**逐级向上搜索** → 都找不到 ⇒ 抛错退出码 2）＋ `tools/difftest/check-surface-parity.py:93`（`⇒ ⇒ 现在：**共用共享实现** \`tools/difftest/go_repo.py\``）＋ `tools/difftest/check-surface-parity.py:107`（`sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))` ⇒ 共享实现真的被接上）＋ RAG 侧被计数的**契约面原件** `service/article/article-api/src/main/java/com/rag/article/article/api/controller/ArticleController.java:80`（`@RequestMapping("/v1")` ⇒ 脚本 glob `**/*Controller.java` 抓到的类级前缀）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/impl/ArticleServiceGrpcImpl.java:75`（`@Override` + `createArticle` ⇒ 脚本 glob `**/*Impl*.java` 抓到的**实际覆写**方法）⇒ **差异已消失**（台账 `DRIFT/article/vs-common.md:295` 记的「默认路径在 worktree 里恒指错 ⇒ 把『找不到 Go 仓』当成『Go 侧 0 个端点』⇒ 假红 + 假绿」已修 ⇒ **实测**：`python3 tools/difftest/check-surface-parity.py --domain article` ⇒ `EXIT=0`，且报出「✅ HTTP 契约面逐条相等（7 端点，Go 侧 JWT 保护 4 条）」＋「✅ gRPC 方法集合逐条相等（Go ArticleService=6）」⇒ 合计 `HTTP 端点 Go=7 / RAG=7 · gRPC 方法 Go=6 / RAG=6` ⇒ **不再是 0 vs 0 的假绿**）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| G4-2 | D | P3 | 否 | Go `service/common/observability/rpc.go:38`（`timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ Go 侧 ctx deadline ⇒ GORM 的 `QueryContext`/`ExecContext` ⇒ PG 取消 SQL，**是框架级保证**）vs RAG `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java:1`（`@Import` 的拦截器本体）＋ `service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java:1`（**真 Spring 上下文 + 真 mapper + 真 PG** 的三层守门：① 收集层 `MybatisPlusAutoConfiguration.interceptors` ② 装载层 `SqlSessionFactory.getConfiguration().getInterceptors()` ③ 行为层绑过期 deadline ⇒ 查询以 `57014`/`DEADLINE_EXCEEDED` 失败）⇒ **差异已消失**（台账 `DRIFT/article/vs-common.md:275` 记的「全仓**无任何测试**证明 `@Import` 的 `DeadlineQueryTimeoutInjector` 真进了 MyBatis-Plus 执行链」已由该守门闭合 —— 共享层 `MybatisPlusInterceptorChainGateTest` **3 条 · 三层加码**）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| G4-6 | D | P3 | 否 | Go `go-zero@v1.10.2/zrpc/internal/config.go:14`（`Breaker bool \`json:",default=true"\`` ⇒ client breaker **默认开**）＋ `go-zero@v1.10.2/zrpc/internal/client.go:112-114`（`if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }` ⇒ **消费点**）vs RAG `service/common/src/main/java/com/rag/common/protection/GoStyleProtectionConfiguration.java:80`（`@Import(GoStyleGrpcClientBreakerConfiguration.class)` ⇒ 共享层**已提供 rpc 进程可用的装配入口**）＋ `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:104`（`@GrpcGlobalClientInterceptor`）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/ArticleRpcApplication.java:94`（`com.rag.common.protection.GoStyleProtectionConfiguration.class` 在 `@Import({…})` 清单里 ⇒ **10/10 rpc 组件都装配**）⇒ **差异已消失**（台账 `DRIFT/article/vs-common.md:279` 记的「`GoStyleGrpcClientBreakerInterceptor` 是 `@Component` 只能靠扫描 ⇒ rpc 侧无装配路径」已被 `GoStyleProtectionConfiguration:80` 的 `@Import` 取代 ⇒ **出向熔断已在位**）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| G4-8 | D（差异已消失 ⇒ 无待实现内容） | P2 | 否 | Go `service/article/rpc/internal/model/db.go:55`（`db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` ⇒ Go 侧**没有「排序桥」这个概念** —— 建表/初始化顺序由 GORM 承担）vs RAG `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4`（`com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration` ⇒ **共享层桥已落码**）＋ `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java:98`（`public class MybatisPlusOrderingBridgeAutoConfiguration {` ⇒ **共享层桥本体**；`:95` = `@AutoConfiguration(`）＋ `service/article/article-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（本域 `imports` **只剩** `ContentSecurityAutoConfiguration` ⇒ **域侧桥已删**，与台账 `DRIFT/article/vs-common.md:76` 记的「2026-10-27 G3 已落码（L-6）」一致）⇒ **差异已消失（2026-10-31 · G3 第十九轮实测）**：本域侧**已完成**（`service/article/article-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1` **只剩** `ContentSecurityAutoConfiguration`）＋ **全仓域侧桥已归零**（`git ls-tree -r do-my-own-gh --name-only` ＋ `grep -i orderingbridge` ⇒ **只剩共享层那 1 座**：`service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ `service/common/.../AutoConfiguration.imports:4`）⇒ ⇒ **9 域写权的收口已完成** ⇒ ⇒ **本条无剩余待实现项**<br>⚠️ **G1 补的验收口径（本轮实测满足）**：「api 侧带上这条边后仍能正常启动」⇒ `mvn -o -pl service/article/article-api -am test` ⇒ **216 例 / 0F / 0E / 0S**（含真 Spring 上下文）＋ api 侧 classpath **0 处 `mybatis-plus`** ⇒ 排序边因目标类不在 classpath 而**自动被忽略** ⇒ 无影响 | 2026-10-31 |
| G4-9 | D | P3 | 否 | Go `service/common/observability/mq.go:12`（`func TraceConsumer(ctx context.Context, tracerName, spanName string, …)` ⇒ Go 侧 article-rpc 的两个 consumer 本来就调共享层那座 `TraceConsumer`）vs RAG `docs/DRIFT/_cross/cross-domain-coupling.md:532`（`"MqConsumer": [` ⇒ 快照已含 `"article"`）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleReviewConsumer.java:11`（`import com.rag.common.observability.MqConsumer;`）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/mq/ArticleSyncResultConsumer.java:11`（同款 import）⇒ **差异已消失**（台账 `DRIFT/article/vs-common.md:293` 记的「`MqConsumer` 辐射度快照过期 ⇒ 守门红（`❌ common_radiation 漂移 · MqConsumer`）」已由快照更新闭合 ⇒ **实测守门绿**：`python3 tools/difftest/check-wave-coupling.py` ⇒ `✅ PASS` · `service/common 逐类辐射度: 33 类（被外部**生产代码**引用）〔**2026-10-31 口径升级后**；旧口径 52 类〕` ⇒ 5 项断言全绿；台账同格提到的另一条 `GoStyleRedisClientAutoConfiguration` **已于 2026-10-31 口径升级后不在辐射列表**（其外部引用全在测试类 ⇒ 生产面零外部引用 ⇒ 旧指针 `:582` **已失效**））⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| L-10 | D | P3 | 否 | Go `service/article/rpc/internal/logic/create_article_logic.go:42`（`tracer := otel.Tracer("article-rpc")` + span `CreateArticle`）＋ `service/article/rpc/internal/mqs/article_consumer.go:45`（`observability.TraceConsumer(ctx, "article-rpc", "ArticleConsumer.Consume", …)` ⇒ **CONSUMER** kind span）＋ `service/common/observability/mq.go:12`（`func TraceConsumer(ctx context.Context, tracerName, spanName string, attrs []attribute.KeyValue, fn func(context.Context) error) error {` ⇒ `otel.Tracer(tracerName).Start(ctx, spanName, trace.WithSpanKind(trace.SpanKindConsumer), …)`）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/service/ArticleSpans.java:131`（`public static final String TRACER_NAME = "article-rpc";`）＋ `:136`（`CREATE_ARTICLE = "CreateArticle"`）＋ `:151`（`CONSUMER_REVIEW = "ArticleConsumer.Consume"`）＋ `:151`/`:154`（两个 consumer span 名）⇒ **差异已消失**（RAG 侧 `ArticleSpans` **8 个 span（6 logic + 2 consumer）全部落位**、tracer 名 `article-rpc` 与 Go 逐条一致 ⇒ 台账 `DRIFT/article/vs-common.md:100` 那条「Go 有 6 个、RAG 0 个」已被 `docs/DRIFT/article/vs-common.md:48` 的 2026-10-28 落码订正覆盖）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| L-6 | D | P3 | 否 | Go `service/article/rpc/internal/model/db.go:55`（Go 侧 **AutoMigrate** ⇒ 排序图里**没有** MP 自动配置这一环 ⇒ **无「排序桥」对应物** ⇒ 不构成 Go 对照项）vs RAG `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java:95`（`@AutoConfiguration(` ⇒ **共享层桥本体接管排序**）＋ `:98`（`public class MybatisPlusOrderingBridgeAutoConfiguration {`）＋ `service/article/article-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（本域 `imports` **只剩** `com.rag.article.article.rpc.client.ContentSecurityAutoConfiguration` ⇒ 域侧桥那行**已删**）＋ `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/config/`（目录现存 `ArticleKafkaChannelValidator.java` / `ArticleMetaObjectHandler.java` / `ArticleRpcProperties.java` / `Jackson2ObjectMapperConfiguration.java` ⇒ **`MybatisPlusOrderingBridgeAutoConfiguration.java` 已不在**）⇒ **差异已消失**（台账 `DRIFT/article/vs-common.md:76` 的「✅ 2026-10-27 G3 已落码（L-6）」经回源复核**成立** —— 域侧桥已删、共享层桥接管排序）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| L-7 | D | P3 | 否 | Go `go-zero@v1.10.2/rest/router/patrouter.go:60`（`reqPath := path.Clean(r.URL.Path)` ⇒ Go 侧路径归一由 **router 层**承担 ⇒ 无「filter 副本」概念）vs RAG `service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingFilter.java:1`（**共享层一份**）＋ `service/article/article-api/src/main/java/com/rag/article/article/api/config/`（目录现存 `ArticleApiProperties.java` / `ArticleGoStyleCoercion.java` / `ArticleGoStyleCorsFilter.java` / `GoStyleJsonBodyLimitFilter.java` / `WebMvcConfig.java` ⇒ **域内副本 `GoStylePathNormalizingFilter.java` 已不在**）⇒ **差异已消失**（台账 `DRIFT/article/vs-common.md:97` 的「✅ 2026-10-27 已删（`git rm`）」经回源复核**成立** ⇒ 全仓只应有一份，现只有一份）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |
| L-8 | D | P3 | 否 | Go `go-zero@v1.10.2/rest/engine.go:74`（`chn.Append(handler.Authorize(...))` ⇒ **JWT 验签在 Authorize 内侧**）＋ `go-zero@v1.10.2/rest/engine.go:110-111`（`chn = chn.Append(ng.middlewares...)` ⇒ `server.Use` 注册的 observability 3s/70s 预算**在验签之后**才 append ⇒ 验签**在预算之外**）＋ Go 真源 `service/article/api/article.go:34`（`server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))`）vs RAG `service/article/article-api/src/main/java/com/rag/article/article/api/auth/GoStyleJwtAuthFilter.java:101`（`@Order(Ordered.HIGHEST_PRECEDENCE + 6)` ⇒ 验签 Filter）＋ `service/article/article-api/src/main/resources/application.yml:260`（`http-timeout: 70s` ⇒ 本域预算 70s）⇒ **差异已消失**（台账 `DRIFT/article/vs-common.md:98` 的「🔴 article-api 的 JWT 验签被计入 70s 预算」已被 2026-10-27 落码覆盖：验签搬进域内 `@Order(+6)` Filter ⇒ 落在 `+7 HttpMiddlewareFilter`（预算起算）**之外** ⇒ 与 Go 的执行序同形）⇒ **经分析确认不需要再实现** ⇒ 无落码动作 | 2026-10-30 |

### lane W9

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| A-5 | B（阶段一竞争力补强 · P0 #8「关键配置和敏感信息没有明显的不安全处理方式」的域侧半边） | P1 | 否 | Go `service/article/rpc/etc/article.yaml:13`（`Password: "${POSTGRES_PASSWORD}"` ⇒ **无 `:默认值` 形态**）＋ `go-zero@v1.10.2/core/conf/config.go:63`（`loader([]byte(os.ExpandEnv(string(content))), v)` ⇒ yaml 先整体做环境变量展开 ⇒ 未定义变量替换成**空串** ⇒ Go 侧缺 env 时口令为空、**没有明文兜底**）vs RAG `service/article/article-rpc/src/main/resources/application.yml:34`（`password: ${POSTGRES_PASSWORD:password}` ⇒ **明文兜底口令 `password`**）＋ `:171`（第二个数据源同款）⇒ **差异仍成立**（缺 `POSTGRES_PASSWORD` 时 Go 侧拿到空口令、连库失败；RAG 侧静默用 `password`）⇒ ⚠️ 同条目的另两项**不构成差异**：`${MINIO_SECRET_KEY:minioadmin}` 与 Go `service/article/rpc/etc/article.yaml:37-38`（`AccessKeyID: "minioadmin"` / `SecretAccessKey: "minioadmin"`）**两侧同为明文兜底**；`*_AUTHORITY_TOKEN` 在 article 域**不存在**（实测只有 like/comment/favorite 三域有）⇒ 落点 = article-rpc 自己的 `application.yml`（**域侧写权**）＋ 根级 `docker-compose.yaml:15` 已用 `:?` 强制 `POSTGRES_PASSWORD`（⇒ compose 路径下不可达）⇒ 归 B、不阻断阶段一 | 2026-10-30 |
| A-6 | C（阶段 2 工程建设项 · 跨域待办 · 落点在**别的域**的写权面） | P2 | 否 | Go `service/article/rpc/article.go:41-42`（`u := model.NewArticleRepo(c)` ⇒ `service/article/rpc/internal/model/db.go:55-60` 的 `db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` 在 `:44` 的 `zrpc.MustNewServer` **之前**执行 ⇒ Go 侧建表/就绪早于对外监听）vs RAG `service/article/article-rpc/src/main/java/com/rag/article/article/rpc/bootstrap/ArticleSchemaGate.java:82`（`implements SmartInitializingSingleton` ⇒ **article 域已改为「server 起之前」校验**）＋ `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/bootstrap/LikeSchemaGate.java:55` ／ `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/bootstrap/CommentSchemaGate.java:61` ／ `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/bootstrap/FavoriteSchemaGate.java:71` ／ `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/bootstrap/TaskSchemaGate.java:51`（四处皆 `@EventListener(ApplicationReadyEvent.class)` ⇒ 校验发生在 server 已起来之后）⇒ **差异仍成立**（**残留面 = like/comment/favorite/task 四域**；article 域自身已闭合）⇒ 落点 = 他域写权面（AGENTS.md §5.3c）⇒ 归 C、不阻断阶段一 | 2026-10-30 |
| G4-11 | D（差异已消失 ⇒ 无待实现内容） | P2 | 否 | Go `service/article/api/internal/svc/service_context.go:24`（`articleservice.NewArticleService(zrpc.MustNewClient(c.ArticleRpcConf))` ⇒ Go 侧 api 的出向 gRPC 客户端由 `ArticleRpcConf` 解析 ⇒ 依赖 `service/article/api/etc/article-api.yaml:9-11` 的 `Etcd.Hosts: [etcd:2379]` **可达** ⇒ 当初「宿主端口未映射 ⇒ 双侧活体 0 条」的受阻前提**只挂在这条链上**）＋ `service/article/api/etc/article-api.yaml:14-24`（`SecurityRpcConf` / `UserRpcConf` 同样指向 `etcd:2379`）vs RAG `tools/api-full-parity-probe/article/run.sh`（探针**已落仓** ⇒ Go 真源栈 + RAG 栈**同时起**，同一请求**逐字节**比对）＋ `tools/api-full-parity-probe/article/expected.txt`（3 个场景的冻结期望值：`list_articles` / `get_article_exists` / `get_article_missing`）＋ `docs/PhaseGoal-judge.md:478`（§九 **G-3** 现为「✅ **已闭合**（2026-10-29）… ③ **主线独立复跑 EXIT=0** … ④ 变异 M2/M3 **精确转红**」）＋ `:372`（§七 P0 #1 行已改写为「`article` 域从「双侧活体 **0** 条」⇒ **3 场景 + 两侧播种同一行**」）⇒ **差异已消失**（台账记的「受阻前提已过期 ⇒ 降为可做但本轮未做」这半**已落地**；其 2026-10-29 订正段里「成功路径只覆盖公开列表端点 / `user.rpc` 两侧都不可达 ⇒ 降级成 5001」的那段**残留也已消解** —— 现探针**两侧播种同一行**（`status=2 PUBLISHED` · id `9007199254740993`）⇒ `get_article_exists` 命中真实成功路径）⇒ ⚠️ **附带残留（不在本条主体）**：`docs/PhaseGoal/g1.md:54` ／ `g2.md:57` ／ `g3.md:63` ／ `g4.md:46` 四份**机器生成**的 lane 矩阵的 P0 #1 行**仍写**「`article` 域双侧活体 **0 条**（G-3，Go 侧需 `etcd:2379` 宿主端口映射）」⇒ 属生成器输入未同步的**共享面文档订正**（落点 = `docs/PhaseGoal/g*.md` ＋ 生成器）⇒ 交合并收口执行者 ⇒ 本条主体已无待实现内容 ⇒ 归 D | 2026-10-30 |
| G4-12 | D（差异已消失 ⇒ 三处归类/口径已全部订正） | P3 | 否 | Go `service/comment/rpc/internal/model/data_model.go:55-64`（`type ReportRecord struct` ⇒ **8 列**：`id` / `user_id` / `comment_id` / `target_type` / `target_id` / `reason` / `detail` / `created_at`）＋ `:66-68`（`TableName()` ⇒ `"report_record"`）＋ `service/comment/rpc/internal/model/db.go:15`（`db.AutoMigrate(&Subject{}, &CommentContent{}, …)` ⇒ **不含** `&ReportRecord{}` ⇒ Go 侧**从不建**这张表 ⇒ 「整张表仅 RAG 有」是事实）vs RAG `docs/PhaseGoal-judge.md:583`（§十一 **D-07** 现写「comment 域（**已签字** · `SD-2026-10-24-CAND-02` ⇒ **不是缺陷，不得再「修回去」**）」⇒ 订正①已落地）＋ `:488`（§九 **G-13** 现写「~~3 域无独立失败路径矩阵~~ ⇒ **全部闭合**（2026-10-29）」· article **104** 条）＋ `:375`（§七 P0 #4 现写「失败路径矩阵 **0 域无缺口**（**227 条**）… article **104**」）⇒ 订正②已落地 ＋ `:493`（§九 **G-18** 现写「✅ **已闭合**（G3 `50bb5f3` 回填 ⇒ **裁定结果 = 以 `hot/vs-common.md` 为准**）」⇒ 订正③已落地）⇒ **差异已消失**（三处全部已订正）＋ 复跑 `python3 tools/difftest/check-phasegoal-lanes.py` ⇒ 「✅ per-lane 阶段一验收矩阵自检：全部通过」EXIT=0 ⇒ 无待实现内容 ⇒ 归 D | 2026-10-30 |
| G4-3 | D（差异已消失 ⇒ javadoc 已逐条补上 `文件:行号` 并改正归因） | P2 | 否 | Go `service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48`（`switch st.Code()` 44 · `case codes.Internal:` 45 → `errmsg.ErrorServerCommon` 46 · `default:` 47 → `errmsg.CodeServerBusy` 48 ⇒ **1015 产自 api logic 的 `default` 分支**）＋ `service/user/common/errmsg/errmsg.go:6`（`CodeServerBusy = 1015`）＋ `:41`（`"服务繁忙"`）＋ `service/common/observability/observability.go:146-158`（`IsTimeoutError`：`errors.Is(err, context.DeadlineExceeded)` 在 `:150` · `codes.DeadlineExceeded` 在 `:153` · 网络超时在 `:157` ⇒ **只做可观测性判定、不产码**）vs RAG `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java:31`（「Go 出处（消费点，不是声明点）—— 1015 是谁产出的」段已建立）＋ `:32`（「**各 api logic 显式 `switch st.Code()` 的 `default` 分支**」）＋ `:33`（「**不是** go-zero 的错误映射，**也不是** deadline 映射」）＋ `:35` / `:46` / `:53`（逐条补上 `getuserlistlogic.go:44-48` / `errmsg.go:6` / `observability.go:146-158` 等行号）⇒ **差异已消失**（原登记的两处 —— 「以『Go 出处』措辞断言却零 `文件:行号`」＋「归因错误」—— 均已修 ⇒ AGENTS.md §3.3.2 的高危项已消解；**纯 javadoc ⇒ 零字节码变化**）⇒ 无待实现内容 ⇒ 归 D | 2026-10-30 |
| G4-5 | C（阶段 2 工程建设项 · 共享面文档订正 · 落点 = `AGENTS.md` 共享文件） | P3 | 否 | Go `service/article/rpc/internal/model/db.go:55-60`（`db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` ⇒ Go 侧**无版本化迁移账本 / 无 baseline 概念** ⇒ 「账本撞号」是 RAG 侧 Flyway 独有的机制，该机制讨论的**事实底座**只在 RAG 侧）vs RAG `AGENTS.md:139`（§3.5 表「共享 Flyway 账本」行**至今仍写**「⚠️ **`favorite-rpc` 例外**（缺 `baseline-version: 0`）⇒ 只记账」）＋ `:216`（§6.3 问题 4 的「残留风险」段只写通用风险、**未提** `article-rpc`）＋ `service/article/article-rpc/src/main/resources/application.yml:90`（**已补** `baseline-version: 0` ⇒ RAG 侧缺陷已修）＋ `service/favorite/favorite-rpc/src/main/resources/application.yml:126`（**也已补** `baseline-version: 0` ⇒ AGENTS.md 点名的那个「例外」**本身也不成立了**）⇒ **差异仍成立**（AGENTS.md 的记载**双重过期**：既漏 `article-rpc`，又仍把已修的 `favorite-rpc` 写成例外）⇒ 落点 = `AGENTS.md` 共享文件（**无单一 owner** ⇒ 合并收口执行者）⇒ 归 C、不阻断阶段一 | 2026-10-30 |
| G4-7 | D（差异已消失 ⇒ 该模块已不是阻塞面，「补账」动作不应做） | P3 | 否 | Go `service/article/rpc/internal/model/db.go:55-60`（Go 侧 `db.AutoMigrate` ⇒ **无版本账本 / 无 baseline** ⇒ 账本机制是 RAG 侧独有 ⇒ 该脚本的 `KNOWN_BLOCKED` 只可能指向 RAG 侧组件）vs RAG `tools/difftest/flyway-concurrent-verify.py:48-51`（`KNOWN_BLOCKED` 只登记 `favorite`）＋ `:93-95`（`baseline_version = fw.get("baseline-version")`，缺省 `"1"` ⇒ 脚本**看得见**每个模块的实际值）＋ `:228`（`blocked = m in KNOWN_BLOCKED` ⇒ 该表是「已知阻塞」的**豁免表**）＋ `service/article/article-rpc/src/main/resources/application.yml:90`（`baseline-version: 0` ⇒ 脚本对 article 已算得 `baseline_version="0"`）⇒ **差异已消失**（台账记的「同形的 `article-rpc` 没登记 ⇒ **只补账、不改代码**」**已失效**：`article-rpc` 已于 2026-10-27 补 `baseline-version: 0` ⇒ 不再是阻塞面 ⇒ 把它登记进 `KNOWN_BLOCKED` 反而会把**已修**的面继续标成「已知阻塞」⇒ 补账动作不应做）⇒ ⚠️ **附带发现（不在本条范围，留给共享面 owner）**：`KNOWN_BLOCKED` 里 `favorite` 那条**自己也已过期**（`service/favorite/favorite-rpc/src/main/resources/application.yml:126` 已补 `baseline-version: 0`）⇒ 「已知阻塞账」现在两边都不准 ⇒ 归 D（本条主体无待实现内容） | 2026-10-30 |

> 🟢 **补充说明（测试面引用共享层类的写法归一 · 2026-10-31 · G3 lane）**
> ① **判定规则（与 G2 lane 在 message 域的处置定案同形 · 别当「统一风格」去动别的域）** —— **测试面若用 `import` 引用某个共享层类，而本域生产面对该类的引用不是 `import` 形态（是全限定名，或生产面根本没有该依赖）⇒ ⇒ 旧口径（`tools/difftest/check-wave-coupling.py:246` 的 `scan_common_radiation()` 旧版：只认 `^import` 行，且把 `src/test` 也扫进来）下测试面会「伪装成生产依赖面」⇒ ⇒ 真依赖被删时该门不红 ⇒ ⇒ 假绿 ⇒ ⇒ 须改成全限定名**。**反之，若本域生产面本来就 `import` 该类 ⇒ ⇒ 旧口径下该域本来就该在辐射域列表里 ⇒ ⇒ 测试面 import 不产生假阳性 ⇒ ⇒ 不改**。⇒ 规则出处 = `docs/DRIFT/message/vs-common.md:1793`（G2 lane 的同形定案）。
> ② **本域这次改了什么（零行为变更：同一 class 对象）** —— 删 **17** 行 `import` ＋ 把 **28** 处代码用点改成全限定名，涉及 **5** 个测试文件：`service/article/article-api/src/test/java/com/rag/article/article/api/ArticleApiAssemblyWiringTest.java`（7 类：`GoStyleJsonBodyConverterConfig` / `GoStyleBreakerFilter` / `GoStyleCpuSheddingFilter` / `GoStyleGrpcClientBreakerInterceptor` / `GoStyleMaxBytesFilter` / `GoStyleMaxConnsFilter` / `HttpMiddlewareFilter`）· `service/article/article-api/src/test/java/com/rag/article/article/api/ArticleGoStrconvQuoteParityTest.java`（`GoStyleFormValues`）· `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleProtectionChainWiringTest.java`（`GoStyleProtectionProperties`）· `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/ArticleRpcSharedLayerWebBeanGateTest.java`（`GoStyleTomcatParameterLimitCustomizer` / `GoStyleBreakerFilter` / `GoStyleCpuSheddingFilter` / `GoStyleMaxBytesFilter` / `GoStyleMaxConnsFilter`）· `service/article/article-rpc/src/test/java/com/rag/article/article/rpc/client/ArticleOutboundDeadlineCarrierGateTest.java`（`RpcInterceptor` / `GoStyleOutboundDeadlineInterceptor` / `GoStyleProtectionProperties`）。
> ③ **本域生产面的 import 现状（判定依据，逐条可回源）** —— ① `GoStyleProtectionProperties` 的**生产面引用是全限定名**：`service/article/article-rpc/src/main/java/com/rag/article/article/rpc/ArticleRpcApplication.java:125`（`com.rag.common.protection.GoStyleProtectionProperties protectionProperties = …`）⇒ ⇒ 生产面**没有** import 行 ⇒ ⇒ 旧口径看不见它 ⇒ ⇒ 测试面那处 import 会「替生产面代劳」⇒ ⇒ 必改；② 其余 **12** 个类本域生产面**根本没有引用** ⇒ ⇒ 旧口径下测试面 import 凭空把本域列进辐射域 ⇒ ⇒ 必改；③ **保留不动**的（本域生产面确有 import ⇒ 旧口径下本来就该在列表里）：`GoStyleRequestDeadline`（`service/article/article-api/src/main/java/com/rag/article/article/api/client/ArticleRpcClient.java:4` · `service/article/article-api/src/main/java/com/rag/article/article/api/client/UserRpcClient.java:3`）· `GoStyleJsonBodyConverter`（`service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStyleJsonBodyLimitFilter.java:3`）· `GoStyleCircuitOpenException`（`service/article/article-api/src/main/java/com/rag/article/article/api/service/ArticleUploadService.java:10`）· `SnowflakeBean`（`service/article/article-rpc/src/main/java/com/rag/article/article/rpc/bootstrap/SnowflakeBootstrap.java:4`）· `LoggerFactoryBean`（`service/article/article-api/src/main/java/com/rag/article/article/api/service/ArticleAuthorResolver.java:6`）。
> ④ **实测读数（自己跑出来的，不采信台账自述）** —— ① 旧口径 vs 新口径的**辐射域差分**（按守门现口径 `scan_common_radiation()` 复算，改前取 `HEAD` 版文件、改后取工作树版文件）：**article 域被旧口径多报 11 类**（`GoStyleBreakerFilter` / `GoStyleCpuSheddingFilter` / `GoStyleFormValues` / `GoStyleGrpcClientBreakerInterceptor` / `GoStyleJsonBodyConverterConfig` / `GoStyleMaxBytesFilter` / `GoStyleMaxConnsFilter` / `GoStyleOutboundDeadlineInterceptor` / `GoStyleTomcatParameterLimitCustomizer` / `HttpMiddlewareFilter` / `RpcInterceptor`）⇒ ⇒ 改后 **= 0**；② **新口径的辐射列表一字未变**（守门现口径下测试面本来就不可见）⇒ ⇒ **共享面快照无需刷新** ⇒ ⇒ `docs/DRIFT/_cross/cross-domain-coupling.md` 不属本域写权面、本域不碰；③ 守门 `tools/difftest/check-wave-coupling.py` EXIT=0 ＋ 本 lane **11** 道门禁全部 EXIT=0；④ 本域两模块回归（真跑，非只编译）：**article-api 19 条** ＋ **article-rpc 14 条**，**0 failures / 0 errors / 0 skipped**。
> ⑤ **效力边界（三条，越界即失效）** —— ① 本条只管**测试面写法**，**零行为变更**（同一 class 对象）⇒ 改的是 import 形态，不是引用到的那个类；② 本条**不宣称**新口径下门会红 —— 守门现口径（`tools/difftest/check-wave-coupling.py:272` 的 `glob` 只扫 `src/main` ＋ `:279-284` 的代码态 FQN 判定）下测试面**本来就不可见**⇒ ⇒ 本次改动是**口径回退时的防护**（防假绿），**不是**当前门红 ⇒ ⇒ 不得据此声称「修好了一个红门」；③ 本条**只覆盖本域**⇒ ⇒ 跨域的同一形态在别的域仍是开口（见 ⑥）⇒ ⇒ 不外推。
> ⑥ **本域之外的残留（不属本域写权面 ⇒ 本域只登记、不落码）** —— 改后旧口径多报集里 `follow` / `users` 仍在（G1 面）· `like` / `message` / `task` 仍在（G2 面）⇒ ⇒ 已连同本文一起报给合并收口执行者 ⇒ 请按域派回各自主人。
