<!-- @new 2026-10-27 · `like-common` lane 台账 · **G2 波次工作树产出**（`align/g2-message-like-task`）· 基线 = `do-my-own-gh` @ `a282012` · **阶段 1 只读对账完成（2026-10-27）** -->

# `like-common` lane 台账 —— 「like ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（`service/like/like-common`；⚠️ **本域没有 `common.md` 台账** ⇒ 本域的 common 相关结论就落在本文件）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)
> ⇒ **本文件由 `G2` 波次工作树独占**；**共享面一律不写**（合并后由整合者统一更新）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `like-common`（原第 6/10 条） |
| **现分支 / 工作树** | **`align/g2-message-like-task`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g2-message-like-task` |
| 基点 | **`do-my-own-gh` @ `a282012`** |
| 本域模块 | `service/like/{like-api, like-rpc, like-common, like-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) |
| **写权** | ✅ 可写**本域** `service/like/**` ＋ `docs/DRIFT/like/**`；🔴 **`service/common/**` + `service/users/users-common/**` 只读** |
| **本轮核对日期** | **2026-10-27**（§1 全表 + §2 全表） |
| **证据强度上限** | **L1** + **L3**（Go 侧）；**L4 仅限 RAG 侧活体**（A9 探针：真 gRPC server + 真链 + 真 OTel SDK）⇒ **Go 侧仍未跑活体 ⇒ A9 轴整体不得标 L4**（AGENTS.md §3.3.3：无双侧活体不得写 L4） |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 逐条重核**）

> ⚠️ **本表上一版是 2026-10-24 的抽取快照 —— 5 条里 4 条状态过期**。
> **落点一律「读消费点」判定，不照抄旧列**。

| 编号 | 摘要 | 落点（**读消费点后判定**） | 状态（**2026-10-27 核对**） | 证据 | 域台账 |
|---|---|---|---|---|---|
| 🔴 `DRIFT-LIKEAPI-015` | `Content-Type` **大小写**判据 | `service/common`（`GoStyleJsonBodyConverter`） | ✅ **已修**（**旧快照「未修」过期**）—— 共享层 T-1（2026-10-25）已落码 | `GoStyleJsonBodyConverter.java:331`（`getFirst(CONTENT_TYPE)` = **原始头值**）→ `:334` → `:241-245`（`contentType.contains("application/json")` = **大小写敏感**）；Go `go-zero@v1.10.2/rest/httpx/requests.go:153-154`（`strings.Contains(r.Header.Get(...))`，**同样大小写敏感**） | [`api.md`](api.md) |
| `DRIFT-LIKEAPI-017` | 单次 RPC 超时：Go 固定 2000ms | ⚠️ **本域** `service/like/like-api/**`（**旧列写 `service/common` = 错**） | ✅ **已修**（**旧快照「未修」过期**；第五轮已改判为域内可修） | `LikeRpcClient.java:135-146`（`min(capMillis, remainingMillis)`）+ `LikeApiProperties.java:125`（默认 2000ms）+ 守门 `LikeRpcTimeoutCapTest.java:82`；Go `zrpc/config.go:31` | [`api.md`](api.md) |
| `DRIFT-LIKE-006` | 模块从未做 Spring 上下文加载测试 | 域侧测试 | ✅ **已闭合**（**旧快照「部分过期」应更新为已闭合**） | `LikeRpcSpringContextWiringTest.java:64-84`（`@SpringBootTest(classes=LikeRpcApplication.class)` + 真 PG Testcontainers `:87-91` + `@DynamicPropertySource` `:93-98`）⇒ **真能触发上下文加载** | [`rpc.md`](rpc.md) |
| `DRIFT-LIKE-013`~`016` | 「服务起不来」族 | 域侧（各条落点各异，均在本域） | ✅ **全部已修**（**旧快照过期**） | 013 `application.yml:183` `discovery:///message-rpc`（三斜杠）+ 守门 `LikeRpcProductionAddressTest.java:104-114`；014 `config/MybatisPlusOrderingBridgeAutoConfiguration.java` + `AutoConfiguration.imports`；015 `like-rpc/pom.xml:156-159` `spring-boot-kafka`；016 `config/Jackson2ObjectMapperConfiguration.java` + `LikeRpcApplication.java:98` | [`rpc.md`](rpc.md) |
| ✅ `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（X-6 已落码 6 类） | ✅ **已落地**（本域只确认 `mode` 与 Go 同向） | 共享层 6 类在位；本域消费点 `application.yml:125` `mode: envelope`；Go `service/like/api/likecenter.go:48` 注册 `httpx.SetErrorHandler` ⇒ **同向** ✅ | [`api.md`](api.md) |

⇒ **§1 五条 ⇒ 新派 G4 反馈单 = 0 条**（4 条已修/已闭合 + 1 条已落地）。
⇒ **跨域待办关闭**：`DRIFT-MSG-V-029`（`like-rpc` 缺 `baseline-version: 0`）⇒ **已修**（`like-rpc/application.yml:78` `baseline-version: 0` + `:81` 独立账本表名 + 守门 `LikeFlywaySharedSchemaGateTest.java:159-163`）⇒ message 域台账里「🟡 留给 like 域」的挂账 **可以关掉**。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**2026-10-27 核对**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | api：`LikeRpcClient.java:133-147`（`min(2000ms, 剩余)`）+ `LikeApiProperties.java:125`（默认 2000ms）· 消费点 `HttpMiddleware.java:304`（`begin()`）/`:336`（`clear()`）· 🔴 **2026-10-27 新增**：`LikeJwtAuthFilter`（+6）—— **3s 预算的作用域**（验签在预算之外，= `DRIFT-LIKEAPI-023`）<br>rpc：`LikeRpcApplication.java:90`（`@Import`）+ `:137`（`server.intercept(...)`）+ `application.yml:299` `rpc-timeout: 2s` · **Redis 超时**：`like-rpc/application.yml` `spring.data.redis.timeout: 3s` ＋ `connect-timeout: 5s` → 消费点 `RedisURI.timeout`（`CommandExpiryWriter:160`）＋ `ClientOptions.socketOptions.connectTimeout`（Boot `LettuceConnectionConfiguration:212-215`）· **Redis 连接池**：`like-rpc/application.yml` `spring.data.redis.lettuce.pool.{enabled,max-active,max-idle,min-idle}` ＋ `like-rpc/pom.xml` 的 `commons-pool2` → 消费点 Boot 4 `LettuceConnectionConfiguration:267-271`（`getPoolConfig` ⇒ `:269 setMaxTotal` / `:270 setMaxIdle` / `:271 setMinIdle`）＋ `DataRedisConnectionConfiguration:195-198`（`isPoolEnabled`） | ✅ 一致（Go `zrpc/config.go:31` 客户端 2000 / `:46` 服务端 2000 / `rest/config.go:55` HTTP 3000 ⇒ 有效值 = `min(2s, 3s−已耗)`，RAG 同式）<br>🔴 2026-10-27：**3s 预算的作用域也已对齐**（Go 的预算**不含**验签 —— 真源 `observability/http.go:30-34` 关掉原生 TimeoutHandler、改由 `server.Use` 的自建中间件计时 ⇒ 计时器在 `Authorize` **之后**起算；RAG 侧原把验签放在 `HandlerInterceptor`（预算内）⇒ 现已搬到 `LikeJwtAuthFilter`(+6) ⇒ **验签在预算外**，两侧一致）🆕 **2026-10-27（下一轮 · 池轴）**：**Redis 连接池数值轴已修** —— `min-idle: 4` ⇒ **`8`**（= Go 的 `MinIdleConns`：真源走 **go-zero 的 redis** ⇒ `redisclientmanager.go:15` `idleConns = 8` ⇒ **精确对齐**）＋ 新增守门 **⑤⑥**（`LikeRpcRedisTimeoutsGateTest` 4 ⇒ **6 条**）⇒ ⇒ 详见 [`like/rpc.md` 的 `DRIFT-LIKE-RPC-REDIS-POOL`](rpc.md)（残留 = `max-active` 性能面） | L4（Go 腿活体探针：观测点 A `deadline_set=false` / B `deadline_set=true remaining=3s`；**非法 token ⇒ 401 且三个观测点一个都没打印** ⇒ Authorize 在所有 user middleware 之前；Redis 超时轴 = **双侧活体对拍** L4）<br>🔴 2026-10-27（G2 第二轮）：**Redis 连接超时原先未配** ⇒ 落 Lettuce 默认 **10s**（Go 是 go-redis v9.20.0 库默认 **5s**）⇒ **已修**（补 `connect-timeout: 5s`；命令超时 `3s` 本就对齐 ✓）⇒ 详见 [DRIFT-LIKE-RPC-REDIS-TIMEOUT](rpc.md#🔴-drift-like-rpc-redis-timeout--redis-连接超时未配--落-lettuce-默认-10sgo-是-5s) | 2026-10-27 |
| **A2** | gRPC 拦截器链 | 服务端：`LikeRpcApplication.java:137` → `GoStyleRpcProtectionChain.java:61-78`（recover→breaker→shedding→business）<br>客户端熔断：`GoStyleGrpcClientBreakerInterceptor.java:103-105`（`@Component` + `@GrpcGlobalClientInterceptor`）⇒ 被 `LikeApiApplication.java:75-78`（扫 `com.rag.common`）注册<br>🟡 **域侧 javadoc 假断言**：`LikeRpcClient.java:43-54` 仍写「Java 侧尚未接入」 | 🟡 **OBS**（链序 ✅；🟡 **域侧 javadoc 与事实相反** ⇒ 域侧自修 D-1；🟡 **like-api 无客户端熔断 E2E 守门** ⇒ 其余域都有 ⇒ 域侧补 D-1）<br>🆕 **2026-10-27 第四轮：A2 轴由 L1 升为 L2（运行期 · 守门 + 变异验证）** —— 合并 `align/g4-service-common`（merge commit `37816ca` · G4 要求的整支合并协议）后，共享层的 `compose(props)` / `layerNames(chain)` 已在共享层 ⇒ ⇒ **`LikeProtectionChainWiringTest` 新增 2 条原生判据**（① 层名列表 == `[recover, breaker, shedding]` ＋ **实际链 == 配置推出的构成** ② 关掉降载 ⇒ 层名列表变成 `[recover, breaker]`）⇒ ⇒ **旧的「包装层归属」那条保留不删**（互补而非替代：层名证「有哪些层 + 层序」，包装归属证「真的包住了调用」）⇒ ⇒ **本域客户端熔断那一面**（`GoStyleGrpcClientBreakerInterceptor` · `LikeApiApplication.java:75-77` 扫 `com.rag.common` 注册）**已由阶段 2 的 `LikeApiGrpcClientBreakerE2ETest` 覆盖**（真 in-process gRPC server ⇒ 熔断打开后调用不再触达下游）⇒ ⇒ **本轴两侧均有运行期证据** | **L2（运行期）** | 2026-10-27 |
| **A3** | HTTP 保护链 | 四个 Filter：`GoStyleProtectionConfiguration.java:55-57` ⇒ like-api 扫 `com.rag.common` ⇒ 扫到即生效（`LikeApiProtectionMvcTest.java:96-103` 断言 4 个 Filter Bean 存在）· 域侧配置 `application.yml:126-140` · form-limit `application.yml:122-125` `mode: envelope` + 消费点 `GoStyleFormValueLimitInterceptor.java:90-93` + `GoStyleTomcatParameterLimitCustomizer.java:105-118` | 🟡 **OBS**（接线齐备、`mode=envelope` 与 Go `likecenter.go:48` **同向** ✅；🟡 **路径归一** ⇒ 🟢 **共享层已落码**（FB-01 · G4 `182fc32`）但**未合入本树** ⇒ 合并后本域跑**真 Tomcat 探针**即可升 ✅；🟡 **缺本域真实请求证据**）<br>🟢 **2026-10-27 G4 FB-05 共享层落码（`parse` 精确接受 `"false"` 作为 OFF 的**唯一**别名 + 5 条守门）⇒ 对本域零影响**：本域的值是 **`envelope`**、**不是 `off`** ⇒ 走的是**原分支** ⇒ **零行为变化** ⇒ 合并后复跑本域 form 相关守门即可（预计全绿 ⇒ **「预计」不算数 ⇒ 合并后实测**）<br>> 📌 **共享层落点（按符号记，不钉行号 —— G4 树 `182fc32` 的行号合并后会位移）**：FB-01 = `GoStylePathNormalizingFilter` ＋ `GoStylePathNormalizingAutoConfiguration`（唯一注册点）；FB-02 = `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` / `REGISTERED_DEVIATIONS`；FB-03 = `GoStyleRpcProtectionChain.Layered` / `Composition` / `compose(props)` / `layerNames(chain)` ⇒ 共享层侧回归（G4 自测）：**52 模块 / 2442 条 / 0 failures / 0 errors / 4 skipped** / 21:59 min<br>🆕 **2026-10-27 订正（G4 第七次全量 · `5564c27`）**：**2442** 是**第六次**全量的数字 ⇒ **第七次 = 2457 条**（52 模块 / 0 failures / 0 errors / **4 skipped** / **20:28 min** / `MVN_EXIT=0`）⇒ ⚠️ **2457 是 G4 树（52 模块）的口径**，**不是本域数字** ⇒ 本域三域六模块 = **594 条**（`mvn test` 口径）
⇒ ⚠️ **合并后**本域需跑真 Tomcat 探针复核（路径归一）⇒ 那是**收口回归**窗口的事（§5.3e）

🟢 **共享层的 form 语义表已落码**（FB-02 · G4 `182fc32`）⇒ **like 行 = `Mode.ENVELOPE`** ⇒ 与本域 `mode: envelope` **一致** ⇒ 本域**不在** `REGISTERED_DEVIATIONS` 里 ⇒ **真对齐**） | L1 | 2026-10-27 |
| **A4** | 错误码 / envelope | `GlobalResponseAdvice.java:59-72`（包一层 + `body==null \|\| instanceof Response` 双守卫）· `GlobalExceptionHandler.java:106-110`（CodeError→200）`:229-236`（兜底 5001）`:86-90`（401 空 body）· `GoStyleRouterErrorResponses.java:121-140`（404 nosniff / 405 Allow）· rpc `LikeServiceGrpcImpl.java:108`（`onError` 不抛）<br>🔴 **阶段 2 新发现**：**「下游 gRPC status → 业务码」这一层两侧不同**（详见 §3 **D-7**）| 🟡 **OBS**（envelope 形状 + handler 接线 ✅ 与 Go `likecenter.go:41-47` SetOkHandler + `:48-61` SetErrorHandler 同形；**但 status → 业务码这层不同** ⇒ 见 D-7）| L1（源码逐条） | 2026-10-27 |
| **A5** | 端口治理 | 生产 `application.yml:33` `18887` · rpc `application.yml:164` `8082`（= Go `etc/likecenter.yaml:2-3` / `etc/like.yaml:2`）<br>✅ **阶段 2 已治理**：测试 **2 处** `RANDOM_PORT`（`LikeApiRouterErrorRealTomcatTest.java:54` + 本波新增的 `LikeApiGrpcClientBreakerE2ETest.java:78`）⇒ **`DEFINED_PORT`** + 本域专用段 **21600–21699**（`service/like/like-api/src/test/java/com/rag/like/like/api/support/RealTomcatPort.java` · favorite 同款复制）<br>每个测试类另加 **1 条端口段守门** `bindsToDedicatedPortSegment`（断言 `@LocalServerPort` 落在 `FIRST`–`LAST`） | ✅ **一致**（生产端口一致；测试端口已脱离 OS 临时段 49152–65535 ⇒ 不再与 Docker 发布端口同段）<br>🟡 **选段表归 `global` 协调**（本段只在本域登记） | L3（端口段断言 = 运行时证据） | 2026-10-27 |
| **A6** | errmsg 契约 | `LikeErrorCode.java:18-97`（29 码）· `LikeErrorMessages.java:37-88`（29 条文案 + 未知码回退 "FAIL"）· `LikeGrpcError.java:110-115` | ✅ 一致（Go `service/like/common/errmsg/errmsg.go:37-67` / `:69-99` / `:101-107` / `:33-35` 逐条同）<br>🟡 trailer 迁移 = **已签字偏离** `SD-2026-10-23-06`（框架限制）⇒ **勿当回归** | L1 | 2026-10-27 |
| **A7** | proto 契约 | `service/like/like-proto/src/main/proto/like.proto` · 消费点 `LikeServiceGrpcImpl.java:48` | ✅ 一致（47 条 message 行 / 16 条 enum 行两侧同；`package` 归一 = **已签字偏离** `SD-2026-10-23-05`） | L1 | 2026-10-27 |
| **A8** | DDL / 账本 | `application.yml:78` **`baseline-version: 0`** · `:81` `table: flyway_schema_history_like` · `:56-57` `enabled`/`baseline-on-migrate` · DDL `V1__create_like_record.sql:22-24`（TIMESTAMPTZ）· `V4__create_like_domain_fact_outbox.sql:32`（`DEFAULT CURRENT_TIMESTAMP`） | ✅ 一致（账本表名独立 ✅、baseline 钉 0 ✅、时间列全 TIMESTAMPTZ ✅；`DEFAULT CURRENT_TIMESTAMP` 1 处与 Go tag 同源 ⇒ **不是**残留）<br>✅ 账本隔离：`check-flyway-ledger.py` **EXIT=0** | L1 / L3 | 2026-10-27 |
| **A9** | trace / otel | `Traceparent`：`GoStyleTraceparentFilter.java:188-191` ⇒ like-api 扫到即生效 · 全局 provider `AutoConfiguration.imports:1` → `GoStyleOtelAutoConfiguration.java:58-61` → `GoStyleOtelAgent.java:187-193`（endpoint 非空才装 batcher）· `application.yml:278-286`（`otel.exporter.otlp.endpoint=jaeger:4317`）<br>🟢 **域侧 span 属性：阶段 2 已补齐**（新增 `LikeSpans.java` + `LikeDomainService.java` 6 个方法 + `LikeUpdateKafkaListener.java` MQ 路径）| 🟡 **OBS**（**域侧写法已与 Go 逐条对齐**：Go **8 处落点 → RAG 8 处**，**13 个属性名**全对；守门 `LikeSpansAttributeGateTest.java` **11/11 绿** + **变异验证红在目标断言**）<br>🔴 **但 gRPC 路径的端到端还差共享层一步**：真链上 handler 执行期 `Span.current()` = **invalid（no-op）** ⇒ 域侧写法在真链上**暂时落不了地** ⇒ 已派单 **FB-04** | **L4（RAG 侧探针活体）+ L3（Go 侧源码逐条）** | 2026-10-27 |

> 🟡 **A9 轴定义提醒**：`audit.*` 是 **admin/users 域专用**（`AdminSpans.java:91-93`）⇒ like 域的 Go 侧实际用 `query.*` / `cache.*` / `like.*` / `messaging.*` ⇒ **不要照抄 `audit.*` 前缀**。轴定义本身的问题记在共享面（**本 lane 不写**）⇒ 见 message 域台账 §6 的同款登记。

## 3. 域侧能自修的（**本域直接修**，不甩给 G4）

| # | 条目 | 现状（`文件:行号`） | 建议修法 |
|---|---|---|---|
| D-1 | **like-api 客户端熔断 javadoc 假断言** + **缺 E2E 守门** | `LikeRpcClient.java:43-54` 写「Java 侧尚未接入（已登记的已知偏离）」；实际 `GoStyleGrpcClientBreakerInterceptor.java:103-105` 已被扫到注册 | ✅ **阶段 2 已落码**（**两件都做了**）：① **javadoc 已订正**（`LikeRpcClient.java:43-59`）—— 如实写「Java 侧<b>已接入</b>（共享层 `@Component` + `@GrpcGlobalClientInterceptor`，随 `scanBasePackages` 含 `com.rag.common` ⇒ 扫到即生效）」，并删掉「本模块下游 like-rpc 尚未实现 / 等 message 域一起收敛」那段<b>已过期</b>的理由；② **E2E 守门已补** `LikeApiGrpcClientBreakerE2ETest.java`（真 in-process gRPC server ＋ 真 channel ＋ 真拦截器）⇒ **断言「下游连续返 INTERNAL ⇒ 熔断打开 ⇒ 调用不再触达下游」**（= 证明机制真在链上，不是「Bean 存在」）<br>⚠️ **判别信号刻意不用业务码**：本域 `INTERNAL` 与「熔断打开」**都**映到 1015 ⇒ 码无判别力 ⇒ 改用「下游有没有真被调用」（短路发生在 `next.newCall` **之前** ⇒ 根本不该建 ClientCall）⇒ **不把码值锁死成守门**（码的差异是 A4 的账，见 D-7）<br>✅ **变异验证**：临时置 `rag.protection.breaker.enabled=false` ⇒ **红在目标断言**（「熔断必须打开并让调用不再触达下游；本次下游累计被调用 163 次」）⇒ 非假绿 |
| D-2 | 🔴 **A9：like 域 span 属性全缺**（阶段 1 新发现） | RAG `service/like` 各子模块 `src/main` 内 `AttributeKey`/`setAttribute`/`Span.fromContext` **零命中**<br>Go 侧落点：`getlikestatelogic.go:38-41`/`:69-71`、`getlikecountlogic.go:38-40`/`:90-92`、`gettargetlikerlistlogic.go:37-41`/`:86`/`:90`、`getuserlikelistlogic.go:35-39`/`:80`/`:84`、`getusertotallikelogic.go:35-37`/`:52`/`:64`、`likeactionlogic.go:162-166`、`likemq.go:137-143` | ✅ **阶段 2 已落码**（域侧 8 处落点 + 13 个属性名）⇒ 新增 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeSpans.java`（域内 helper，**绝不建子 span** ⇒ 对齐 Go 的 `trace.SpanFromContext` 语义；与 admin 域 `AdminSpans` 的差别见该类 javadoc）<br>· **6 个 logic**：`LikeDomainService.java` 的 `getLikeCount` / `getLikeState` / `getUserTotalLike` / `getUserLikeList` / `getTargetLikerList` / `likeAction` ⇒ **属性位置逐条对齐 Go**：入参组在**参数校验之前**、`cache.*` 在 **DB 回源之前**（⇒ 非法请求 / 回源失败的 span 上同样有这些属性，Go 就是这样）<br>· **MQ 路径**：`LikeUpdateKafkaListener.java` 的 `onMessage` 改走共享层 `MqConsumer.traceConsumer`（= Go `observability.TraceConsumer`）⇒ 产生 **CONSUMER** span（名字 `LikeUpdateService.Consume`）+ `messaging.*` 4 属性；⚠️ `messaging.destination` **照抄 Go 的字面量 `"like-topic"`**（不是配置里的 `like_promotion_topic`）<br>· **守门** `LikeSpansAttributeGateTest.java`（真 OTel SDK + 真 exporter，非 mock）**11/11 绿**；**变异验证**：① 入参组挪到校验之后 ② `query.page_size` 取归一值 ⇒ **各自红在目标断言**<br>🔴 **残留（端到端）**：真链上 handler 执行期 `Span.current()` = **invalid**（探针实测）⇒ 域侧写法在真链上暂时落不了地 ⇒ **已派单 FB-04**（共享层 `RpcInterceptor` 需让 server span 在 handler 期 current）<br>⚠️ **前缀按 Go 侧实际取值**（不是 `audit.*`） |
| D-3 | **路径归一缺失** | 本域无 `GoStylePathNormalizingFilter`（全仓唯一实现在 article-api）⇒ 见 §4 **FB-01** | **本域不单独修** ⇒ 走 FB-01（共享层下沉）⇒ 本域只需在回归时复核 |
| D-4 | **like-api 仍用 `RANDOM_PORT`** | `LikeApiRouterErrorRealTomcatTest.java:36`（javadoc）+ `:54`（`webEnvironment = RANDOM_PORT`）⇒ 落在 OS 临时端口段 49152–65535（**Docker 随机发布端口同段**） | 本域自修：引入 `RealTomcatPort` 同款（favorite 域 2026-10-25 已改 `DEFINED_PORT` + 专用段 21300–21399 + 双绑定自检）⇒ like 选段须避开 favorite / 探针 / OS 临时段（建议 21600–21699，**选段表归 global 协调**）<br>✅ **阶段 2 已落码**：新增 `service/like/like-api/src/test/java/com/rag/like/like/api/support/RealTomcatPort.java`（**21600–21699**）+ **2 个**测试类改 `DEFINED_PORT` + `@DynamicPropertySource registry.add("server.port", RealTomcatPort::pick)` + 每类 1 条端口段守门<br>⚠️ **实测多出 1 处**：本波我新增的 `LikeApiGrpcClientBreakerE2ETest` 也是 `RANDOM_PORT` ⇒ 一并治理（**它若不改，本域的治理就只做了一半**）<br>⚠️ **守门到底拦什么**：**改回 `RANDOM_PORT` 不会红**（`@DynamicPropertySource` 的 `server.port` 优先级更高 ⇒ 端口仍在段内）⇒ 守门真正拦的是「**端口注入被拿掉**」（⇒ 落回 `like-api/application.yml` 的 **18887** ＝ 生产端口）与「`pick()` 返回段外端口」 |<br>🟢 **2026-10-27（G2 独立复核 · G4 端口段表落地后）**：**实测复核通过** —— ① `service/like/like-api/src/test/java/com/rag/like/like/api/support/RealTomcatPort.java:69` `FIRST = 21600` / `:72` `LAST = 21699`（与 G4 的测量**逐条一致**；G4 测量基线 = `9ca4b2c`）② **本域真 `RANDOM_PORT` 注解 = 0**（`grep webEnvironment | grep RANDOM_PORT` 在 like 下命中 **4 条**，**逐条看全是 javadoc / 断言文本里的「有人把 webEnvironment 改回 RANDOM_PORT」** ⇒ **不是注解**）③ 🟢 **端口段表已闭合**（G4 `46f67bf` · 台账 §4.10.19）⇒ ⇒ **本条 D-4 = 已销号**<br>⚠️ **统计口径（写给下一个复核者）**：数「真 RANDOM_PORT」**必须只数 `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` 真注解**，**不数 javadoc / 字符串里的提及**（本域就是被提及命中的典型 ⇒ **文件数 ≠ 注解数**）⇒ ⇒ **「提到」与「用着」是两件事** |
| D-5 | **台账快照过期（4 条）** | 本文件 §1（已就地订正）· `like/api.md` 状态表（015「只剩 015 未修」）· `like/rpc.md` DRIFT-LIKE-013（「仍未修」）· `docs/Intentional_Deviations/like/api.md:20` 的 `SD-2026-10-23-08`（charset 残余）| ✅ **阶段 2 已就地订正 3 条**（**同域写权内**）：① `like/api.md` 状态表（三值判定 + 未修项）—— **015 已由共享层 T-1 于 2026-10-25 落码** ⇒ **首轮 6 条 SEMANTIC MISMATCH 全部修完**；**2026-10-27 独立复核**（不照抄台账）：`service/common/.../GoStyleJsonBodyConverter.java:331` = `getFirst(HttpHeaders.CONTENT_TYPE)` = **原始头值** ⇒ 大小写敏感 ⇒ 与 Go `rest/httpx/requests.go:153-154` 同形；域侧守门 `LikeApiGoStyleBodyContentTypeTest.java` ② `like/rpc.md` DRIFT-LIKE-013 —— **已修**：`service/like/like-rpc/src/main/resources/application.yml:183` = `discovery:///message-rpc`（**三斜杠**，`:170-176` 有形态注释）+ 守门 `LikeRpcProductionAddressTest.java:104-114` ③ 本文件 §1（阶段 1 已就地订正）<br>🔴 **第 4 条不在本 lane 写权内 ⇒ 不派 G4（那是共享面）、交整合者**：`docs/Intentional_Deviations/like/api.md:20` 的 `SD-2026-10-23-08`（候选：响应 `Content-Type` 不带 `charset=utf-8`）⇒ 已被共享层 T-6/E1 吸收 ⇒ 应更新为「语义等价」。⚠️ **`docs/Intentional_Deviations/**` 不在 `check-wave-lane-ownership.py` 的 G2 白名单内**（白名单 = `service/{message,like,task}/**` + `docs/DRIFT/{message,like,task}/**`）⇒ **本 lane 不得写** ⇒ **只登记** |
| D-7 | 🔴 **阶段 2 新发现：like-api 的「下游 gRPC status → 业务码」映射与 Go 不同**（A4 轴）| **Go 侧**（`service/like/api/internal/logic/like/*.go`，6 个 logic 同形）：`st, ok := status.FromError(err)` ⇒ `ok==true` ⇒ `errmsg.NewErrCodeMsg(int(st.Code()), st.Message())` ⇒ **业务码 = 原始 gRPC 码**（`INTERNAL` ⇒ **13**）；`ok==false`（非 status 错误，含 go-zero 熔断打开的 `breaker.ErrServiceUnavailable`）⇒ `errmsg.NewErrCode(errmsg.ErrorServerCommon)` ⇒ **5001**（`common/errmsg/errmsg.go:41`）<br>**RAG 侧**：`service/like/like-common/.../errmsg/LikeGrpcError.java:233-247` 的 `mapGrpcCodeToBizCode` 有映射表 ⇒ `INTERNAL` 落 `default` ⇒ **1015**（`CODE_SERVER_BUSY`）；熔断打开抛的 `GoStyleCircuitOpenException` 不是 status ⇒ `bizCodeFromError` 末尾兜底（`:199`）⇒ 也是 **1015**<br>⇒ **两条路径都不同**：INTERNAL：Go **13** vs RAG **1015**；熔断打开：Go **5001** vs RAG **1015** | ⚠️ **本轮只登记，不擅自扩大落码范围**（修它会改 like-api 的整条错误面 ⇒ 需单独一轮）。**影响面**：仅 like-api 的「下游不是本域业务错误」的路径（即 `LikeErrorMessages` 码表之外的 status）<br>⚠️ **不要照抄 message 域的做法**：Go 的 **message** api **有**域内映射 `messagecommon.BizCodeFromError`（`api/internal/logic/shared/helper.go:31`）⇒ Go 侧 INTERNAL ⇒ 5001；**Go 的 like api 没有这个函数** ⇒ 直接用原始 gRPC 码 ⇒ 两侧形态本就不同 ⇒ 「把 RAG 的 like 改成 message 那套」= **既不对齐 Go，也不是对齐本域真源**<br>⇒ **两条候选修法（需拍板，不在本轮范围）**：① **照 Go**：like-api 的 status 兜底改用原始 gRPC 码（INTERNAL ⇒ 13）、非 status ⇒ 5001；② **保留现状并签字**：把「RAG 统一收敛到 1015」登记为**有意偏离**（理由：码表自洽、不泄漏 gRPC 码） |
| D-6 | 🆕 **G3 预告（2026-10-27）· 删除本域的 MyBatis-Plus 排序桥** —— ⛔ **带闸门：必须等 G4 的共享层桥落码后才删** | 本域持有 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`（**全仓 9 份域侧桥之一**，本轮 `find` 实测；共享层 `service/common` 命中数 = **0**）＋ 注册行 `service/like/like-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（类注释 `:41` 自带「**什么时候可以删**」条件） | ⛔ **闸门（硬）**：**必须 G4 的共享层桥落码之后才删** ⇒ 否则「域侧删了、共享层还没加」⇒ MP 自动配置退避 ⇒ **不产 `SqlSessionFactory` ⇒ 启动失败**<br>⇒ 删后跑 **`LikeRpcSpringContextWiringTest`**（真 Spring 上下文）⇒ 判据 = 删掉域侧桥后 MP 仍生效（`SqlSessionFactory` 在）<br>⇒ 两座桥同向 ⇒ 并存期功能等价、**不需要跨树同一次提交**<br>⚠️ 本条是 **G3 的预告（非派单）** ⇒ 等 G3 正式派单再动手；🔴 **G4 台账目前查不到「排序桥」条目** ⇒ 共享层那一半的落码状态**待证**<br>🆕 **G3 口径订正后 · G2 独立核实（2026-10-27 03:3x）**：① **派单窗口 = 「G4 共享层桥已落码」这个事件**（不是「各域阶段 2」）⇒ 本域阶段 2 到了也**不删** ② 🔴 **本域删除爆炸半径 = 3 件**（① 删桥类 ② 删 `AutoConfiguration.imports` 注册行 ③ **改 `LikeRpcSpringContextWiringTest` 的 DisplayName 与失败消息** —— `:38` / `:181` / `:184` **仍点名被删的类** ⇒ 删完会留下**失效的断言文本**）—— 本域是三域里**唯一有测试引用**的 ③ **共享层桥对 api 侧的影响 = 零** ⇒ 实测 **10 个 api 模块的 classpath 上全部没有 MP**（⚠️ `task-api/pom.xml` 的那一处命中是注释里的「**刻意不引入**」清单 ⇒ **假阳性**）⇒ 共享层桥在 api 上下文里 = **零 bean** ＋ 悬空排序名 ⇒ 无副作用 ④ 共享层桥的 javadoc **别照抄各域的部分列举**（实测「同款」列举：message 列 **1** 个 / like 列 **4** 个 / task 列 **5** 个 ⇒ **实际 9** 个）⑤ 本域台账 `like/rpc.md:704` / `:1437`（把桥登记为 `DRIFT-LIKE-014` 的修法）在删除后**一并订正**（**同域写权内**） |

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> ✅ **本域 §1 的 5 条 ⇒ 0 条需要新派单**。
> 🆕 **本轮新发现 1 条共享层落点缺口** ⇒ **FB-01**（与 message / task 两域**同一条** ⇒ 同批落码）。

### 🆕 FB-01 · A3「路径归一」在共享层缺位（**9 个 api 缺 Go 的 `path.Clean` 归一**）

| 项 | 内容 |
|---|---|
| **① 现象** | Go 侧 router 对请求路径做 `path.Clean` ⇒ 尾斜杠 / 双斜杠 / `.` / `..` 变体**命中同一路由**；RAG 侧**只有 `article-api` 有**归一器（域内类）⇒ **其余 9 个 api（含 like）缺这一层** ⇒ 变体路径两侧行为可能分岔（Go 200 vs RAG 404）。⚠️ **「RAG 返 404」是推断**（Spring 默认 `useTrailingSlashMatch=false`）⇒ **需真容器探针确认**（本轮未跑） |
| **② Go 侧出处** | `go-zero@v1.10.2/rest/router/patrouter.go:48`（`cleanPath := path.Clean(reqPath)`，注册侧）+ `:60`（`reqPath := path.Clean(r.URL.Path)`，匹配侧）⇒ **两行已实测真实存在**<br>⇒ 语义证据：article 域双侧对拍 `docs/DRIFT/article/api.md:849`（CASE-25 尾斜杠 `/v1/articles/` ⇒ **Go 200**） |
| **③ RAG 侧现状** | `service/common/src/main/**` 下 **`GoStylePathNormalizingFilter` 命中数 = 0**（本轮 `find` 实测）⇒ 全仓唯一实现 = `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStylePathNormalizingFilter.java`（**域内类**）⇒ like-api 的 `scanBasePackages`（`LikeApiApplication.java:75-78`）**扫不到它** |
| **④ 证据强度** | **L1**（Go 源码行号 + RAG 文件缺失，均可复算）<br>🟢 **Go 侧已升级为 L4 活体**（2026-10-27 · G4Common 实测）：go-zero **真 server** + curl 探针 ⇒ `/v1/x` · `/v1/x/` · `/v1//x` · `/v1/x/../x` · `/v1/./x` **五种变体全部 200** ⇒ **Go 的 `path.Clean` 归一已活体坐实**<br>⚠️ **RAG 侧后果**仍待 G4 落码后的真 Tomcat 守门 ⇒ 本条整体**暂不标 L4** |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「所有 api 的请求路径都按 Go 的 `path.Clean` 归一后再匹配」** ⇒ 归一器落在**共享层 `service/common/http/`**（A3 轴定义的落点即 `protection/* + http/*`）⇒ **不得**继续按域各写一份（article-api 那份是历史遗留的域内副本）<br>⇒ 归一语义 = Go `path/clean.go`（`//`→`/`、`.`/`..` 消解、去尾斜杠），且必须排在**读路径的 Filter 之前**（article 现取 `HIGHEST_PRECEDENCE+2`，须在 `GoStyleTraceparentFilter(+3)` 之前） |
| **⑥ 影响面** | 共享层 `service/common/http/*` ⇒ 被 **10 个 api 全依赖**；**实际缺口 9 个**（article 已有等价实现）⇒ 跨域引用域数 = **9** |
| **⑦ 是否阻塞本波** | **否**（不阻塞本波台账；阻塞 A3 结案 —— 归一层落位前 A3 只能记 🟡） |

> ⚠️ **本单与 message 域 `FB-01` / task 域 `C-2` 是同一条**（三域独立发现）⇒ **同批落码**（一个共享层实现 ⇒ 9 域受益）。

### 🆕 FB-04 · A9「gRPC 业务代码拿不到当前 server span」在共享层缺位（**阶段 2 深挖后新增 · 我方阶段 1 结论的自我订正**）

> ⚠️ **本单是我阶段 1 台账 D-2 的结论改判**：D-2 原写「落点在本域 ⇒ 域侧自修 ⇒ **不是共享层缺陷，不派单**」⇒ **判错了**。
> 深挖 + 活体探针后：域侧按 `Span.current().setAttribute(...)` 落码 ⇒ **在真链上静默无效**（no-op span）⇒ 修法有一半在共享层。

| 项 | 内容 |
|---|---|
| **① 现象** | Go 侧 like 域 6 个 logic 用 `span := trace.SpanFromContext(l.ctx)` + `span.SetAttributes(...)` 给**已有的 server span**加属性；Go 那个 server span 是 **go-zero 自带**的 `UnaryTracingInterceptor` 建的，它**把 span 放进了 ctx** ⇒ logic 取得到。<br>RAG 侧对应物是共享层 `RpcInterceptor`：它**创建了** SERVER span，但**只在 `close()` / `onCancel()` 里 `makeCurrent()`** ⇒ **handler 执行期间 span 不是 current** ⇒ 业务代码 `Span.current()` 拿到 **invalid（no-op）span** ⇒ 属性**静默丢弃**。 |
| **② Go 侧出处** | `zrpc/internal/serverinterceptors/tracinginterceptor.go:95-107`（`startSpan` ⇒ `tr.Start(ctx, …)` ⇒ **span 进 ctx**）· `:18-24`（`ctx, span := startSpan(ctx, …)` ⇒ `handler(ctx, req)` 用的是**带 span 的 ctx**）—— 这就是 Go 侧「业务代码取得到当前 span」的**机制来源**。<br>范围内 10 个域里**只有 like/rpc** 依赖「当前 span」语义（6 处 `SpanFromContext`：`getlikestatelogic.go:37` · `getlikecountlogic.go:37` · `gettargetlikerlistlogic.go:35` · `getuserlikelistlogic.go:34` · `getusertotallikelogic.go:34` · `likeactionlogic.go:161`）；另：共享层自己 4 处（`service/common/observability/observability.go` 的 `Mark*`）。 |
| **③ RAG 侧现状** | `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:207-211`（建 span + `otelCtx = Context.current().with(span)`）· `:332` / `:392`（**只有** `close()` / `onCancel()` 里 `otelCtx.makeCurrent()`）· `:359-367`（`onHalfClose` ⇒ `super.onHalfClose()` **没有**打开 scope）⇒ handler 线程上 `Context.current()` 仍是根 context。 |
| **④ 证据强度** | **RAG 侧 = L4（活体）**：探针（真 gRPC server + 真链 `recover→breaker→shedding→业务` + 真 OTel SDK + 收集型 exporter）实测输出：<br>`serverSpan.spanId = 312a14f71d96f701`（server span 确实导出、活着）· `handler current valid = false` · `handler current spanId = 0000000000000000` ⇒ **坐实**：server span 活着，但 handler 里取不到。<br>⚠️ 该探针是**临时产物**，已删除（输出逐字录在本行）。<br>**Go 侧 = L3**（源码逐条对照 + go-zero v1.10.2 源码取证）—— **未跑 Go 侧活体探针 ⇒ 按协议不写 L4**。 |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「业务 handler 里能拿到当前 server span，并往它上面加属性」**。<br>**最小修法（共享层 1 处）**：在 `RpcInterceptor` 的 `onHalfClose` 里，把 `otelCtx.makeCurrent()` 的 scope **包住 `super.onHalfClose()`**（即 handler 执行期）。<br>⚠️ **不要**让业务侧「自建子 span」：Go 侧 like 是**给已有 server span 加属性**、**不新建** ⇒ 自建子 span 会让 trace 里多一个 Go 没有的 span ⇒ 结构漂移。<br>⚠️ **域侧写法（我方已落，不依赖这次改动）**：`Span.current().setAttribute(...)` —— OTel 的 `Span.current()` 在无 span 时返回 **non-recording span**，`setAttribute` 是 no-op ⇒ **与 Go 的 `trace.SpanFromContext(ctx)` 语义一致**（Go 无 span 时也是 noop）。 |
| **⑥ 影响面** | **现有行为变更 = 0**：全仓 `src/main` 里 `Span.current()` **只有 1 处** —— `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentFactDeliveryService.java:148`，且它跑在 **`CommentFactDispatchScheduler` 的调度线程**上（**不走 gRPC handler**）⇒ **改动碰不到它** ⇒ 那两个 `comment.fact.*` 属性今天落在 no-op span 上（等于没打），改完**仍然不生效** ⇒ **零回归**。<br>⚠️ **comment 域的潜在雷（不用 G4 改，是 G3 的写权）**：Go 侧 `service/comment/rpc/internal/model/fact_delivery.go` **零 otel/span 用法**（实测 grep 无命中）⇒ Go **没有** `comment.fact.event_id` / `comment.fact.aggregate_version` ⇒ RAG 是**凭空多出来的**；今天因落在 no-op span 上而「恰好不可见」⇒ **若将来有人让调度线程也带上 span，它就会显形成漂移**。<br>**受益面**：范围内 10 个 rpc 组件的 gRPC handler 线程；其中**只有 like 域**会用（Go 侧 `SpanFromContext` 分布实测：like/rpc 6 处 · common/observability 4 处 · knowledge/api 2 处（**范围外**））。<br>⚠️ **口径提醒**：不要把影响面写成「9 个域全命中」—— **只有 like/rpc 依赖当前 span 语义**。 |
| **⑦ 是否阻塞本波** | **部分阻塞**：like 域 A9 的 **gRPC 路径端到端**（真 server span 上真的带 `query.*`/`cache.*`/`like.*`）**依赖这条落码**。<br>**不阻塞本波落码**：域侧写法 + **MQ 路径**（`MqConsumer.traceConsumer` ⇒ 共享层已有、它自己 `span.makeCurrent()` ⇒ **不依赖 G4**）本轮已落；守门 = **域侧契约级**（真 OTel SDK + `span.makeCurrent()` ⇒ 断言属性落到 current span 上）⇒ 绿且**不依赖 G4** ⇒ 我方可独立提交。<br>**G4 落码后** ⇒ 我方可补一条 **E2E**（真 server + 真链 ⇒ 断言导出的 server span 带 `query.user_id`）—— 属**收口窗口**的活，不阻塞本波。 |

### 🆕 FB-05 · A1「Redis 调用不感知 deadline」在 like 域**成立**（global 台账 **E66**）⇒ 附本域调用点清单

> ⚠️ **本单不是推演**：G4 在共享层知会里点名「E66 需要各域 owner 参与」⇒ G2 逐条核了本域的 Redis 调用点。
> 本单**只登记**（落点在 `service/common` = G4 写权）⇒ 域侧**不落码**。

| 项 | 内容 |
|---|---|
| **① 现象** | 本域 Redis 调用**不感知请求 deadline** ⇒ deadline 到点后缓存读写**照跑不误**（含写）⇒ 与 Go 的 ctx-aware 语义不同形 |
| **② Go 侧出处（消费点）** | Go 侧 like-rpc 请求路径的 **20 处** Redis 调用**全部带 `l.ctx`**（实测 `grep 'BizRedis\.' service/like/rpc/internal/logic/` ⇒ 20 处命中，**0 处**不带 `Ctx(`）⇒ 代表性：<br>① `getlikecountlogic.go:50,56` `BizRedis.HmgetCtx(l.ctx, …)`<br>② `likeactionlogic.go:193` `BizRedis.EvalCtx(l.ctx, LikeActionLua, …)`（**写**）<br>③ `getlikestatelogic.go:95` `BizRedis.HsetCtx(l.ctx, stateKey, …)`（**写**）<br>④ `getuserlikelistlogic.go:120` `BizRedis.ExpireCtx(l.ctx, redisKey, …)` |
| **③ RAG 侧现状（本域）** | `service/like/like-rpc/.../service/LikeDomainService.java` 共 **11 处** Redis 调用，**全部无 ctx / 无 deadline 载体**：<br>① `:855` `redis.opsForHash().multiGet(...)`（← Go HmgetCtx）<br>② `:874` / `:896` `redis.opsForHash().get(...)`（← Go HgetCtx）<br>③ `:911` `redis.opsForHash().put(...)`（← Go HsetCtx，**写**）<br>④ `:920` `redis.opsForValue().get(...)`（← Go GetCtx）<br>⑤ `:932` `redis.opsForValue().set(...)`（← Go SetexCtx，**写**）<br>⑥ `:941` `redis.expire(...)`（← Go ExpireCtx，**写**）<br>⑦ `:960` `redis.opsForZSet().reverseRangeByScoreWithScores(...)`（← Go Zrevrangebyscore…Ctx）<br>⑧ `:972` `redis.opsForZSet().add(...)`（← Go ZaddCtx，**写**）<br>⑨ `:1005` `redis.opsForHash().get(...)`<br>⑩ `:818` `redis.opsForZSet().zCard(...)`（← Go ZcardCtx）<br>⑪ `:706` `redis.execute(LikeActionScript…)`（← Go EvalCtx，**Lua 写**）<br>⇒ 共享层的 `GoStyleRequestDeadline`（**只**在 `HttpMiddleware:304` 绑 ⇒ **api 侧**）与 `RpcInterceptor.DeadlineState`（**rpc 侧**，`RpcInterceptor:364/455` 绑定）**都查不到**（`grep` 实测：本域 **0 处**调用 `remainingMillis()`） |
| **④ 证据强度** | **L1（源码级 · 两侧逐条对照：Go 20 处带 ctx vs RAG 11 处无 ctx）** ⚠️ **未经双侧活体验证**（global 台账 E66 现有强度同此）⇒ 本域**不主张** L4 |
| **⑤ 建议修法（共享层先落 · 域侧再跟）** | 共享层：提供「当前请求剩余 deadline」的统一载体（rpc 侧 = `RpcInterceptor.DeadlineState`，它**已经在** handler 线程绑定 ⇒ **零新增机制**）<br>域侧（本域）：在 Redis 调用点把 `DeadlineState.remainingMillis()` 绑进命令超时<br>⚠️ **落点顺序**：若 G4 走「Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline」（G4 知会里说的路线）⇒ **域侧零改动** ⇒ 本域只需复核；若是「各域调用点自己查载体」⇒ 本域需改 11 处（其中 5 处是**写**） |
| **⑥ 影响面** | 本域 = **like-rpc 的 11 处请求路径调用点**（上表③）<br>⚠️ **不含** like 域的 MQ / outbox 路径（`LikeUpdateKafkaListener` / `LikeHotEventOutboxRelay`）—— Go 侧那些 ctx 是 **Kafka 消费 ctx** ⇒ 本来就没有请求 deadline ⇒ 两侧同形 ⇒ **不修**（避免造出「比 Go 更严」的新漂移） |
| **⑦ 是否阻塞本波** | **否**（G4 已明确「本轮不落码、提请立项」⇒ 本域**只登记**）<br>⚠️ 但**本域已单独修掉一条相邻的、更严重的漂移**（Redis **连接超时**未配 ⇒ 落 Lettuce 默认 10s vs Go 5s）⇒ 见 [DRIFT-LIKE-RPC-REDIS-TIMEOUT](rpc.md#🔴-drift-like-rpc-redis-timeout--redis-连接超时未配--落-lettuce-默认-10sgo-是-5s) |

## 4·五、🟢 **G4 正式回执（4 项规格）· 2026-10-27 · `align/g4-service-common` @ `5564c27`**

> 📌 **本节是 G4 那张「4 项规格」回执的域侧归档**（AGENTS.md §5.3b：产出**必写回**本域 §4）。
> ⚠️ **本节不销任何单** —— 销号须**合并后**按 §5.3e 重跑收口回归才成立（见末「合并后必做动作」）。

### ① HEAD

`align/g4-service-common` @ **`5564c27`**（基线 `a282012` 之上 **30** 提交 · 工作区干净 · **未 push**）

### ② 改动文件清单（逐个 `文件:行号`）

- `service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:92` ＋ 类级 javadoc `:40-90`
- `service/common/src/main/java/com/rag/common/spring/HttpPoolLifecycle.java:50` ＋ `:60`
- `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:88` / `:122` / `:137` / `:181-182`
- `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:163` / `:175` / `:209`
- `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java:70-84` / `:106-127` / `:129`
- `service/common/src/main/java/com/rag/common/persistence/SqlTimeoutStatusTranslator.java:31`
- 守门新增 5 文件：`…/spring/HttpPoolShutdownWithoutServletGateTest.java` · `…/spring/HttpShutdownProbeWithoutServlet.java` ·
  `…/precedence/GoStyleRedisClientOptionsPrecedenceGateTest.java` · `…/precedence/DomainLikeRedisDisconnectedBehaviorConfig.java` ·
  `…/observability/ObservationPoolSelfHealingGateTest.java`
- 台账：`docs/DRIFT/global/vs-common.md`

### ③ 触及的 A 轴 ＋ Go 出处

- **A3**：`HttpMiddleware.java:1089`（Go 出处 `service/common/observability/http.go`）；
  `ObservabilityLifecycle` 的 Go 出处 = `service/follow/rpc/follow.go:45` ＋ `:3-21`（零 web 类型）
- **A1**：`RpcInterceptor.java:623`（Go 出处 `service/common/observability/rpc.go:38`）
- **A4**：`SqlTimeoutStatusTranslator.java:31`（Go 出处 `getuserlistlogic.go:44-48` · `selectavatarlogic.go:59-60` ·
  `errmsg/errmsg.go:6`/`:41` · `observability.go:146-158`）

### ④ 回归口径与结果

`mvn -B clean verify`（全量）⇒ **BUILD SUCCESS / `MVN_EXIT=0`** · **52 模块** · **2457 条** ·
**failures 0 / errors 0 / skipped 4** · **20:28 min**
⇒ 双侧探针同批次复跑：`run-parity.sh` **EXIT=0 · 56 例**（一致 54 / 不一致 0 / 已签字偏离 2）·
`run-rpc-timeout-parity.sh` **EXIT=0 · 3 例**
⇒ 三门禁同批次复跑：**0 / 0 / 0**

⚠️ **口径提醒**：**2457 条是 G4 树（52 模块）的数字** ⇒ **不是**本域的数字
⇒ 本域三域六模块的数字见本文件各自的「测试」行（口径 = `mvn test`）⇒ **两者不可混用**。

### 🟢 我域三张单 + 澄清的逐条状态

| 单 | 状态 | 落码 commit |
|---|---|---|
| **FB-01**（A3 路径归一进共享层） | 🟢 **已落** | `9690b04` |
| **FB-02**（form 闸门判据改「语义可达性」） | 🟢 **已落** | `182fc32` |
| **FB-03**（保护链「层构成」只读入口） | 🟢 **已落** | `182fc32` |
| **澄清第 1 步**（form 判据措辞） | 🟢 **已落且与本域要求逐条一致** | `182fc32` |
| **FB-04**（A9 gRPC handler 拿到当前 server span） | 🟢 **已落**（⚠️ **第八次落码，不在本表 4 项规格内** ⇒ 独立复核见 **§4·六**） | `853767b` |
| **E66**（Redis 写不绑 deadline） | 🔴 **仍 OPEN · 本轮转立项** | — |

⇒ **G4 未越界**（按本域给的边界）：共享层**没有**为 task 写特例 · **没有**碰 favorite 的任何东西 ·
本域建议项 3 的对拍脚本落点 = `tools/difftest/**`（无 lane 拥有的共享面）⇒ **只登记，不擅自改** ✅

## 4·六、🟢 **FB-04 已落码**（`853767b` · 2026-10-27）· G2 **直接读 diff 独立复核**（未采信转述）

| 项 | 独立复核结论 |
|---|---|
| **落码 commit** | `853767b`「fix(common): 让 gRPC 业务 handler 执行期能拿到当前 server span（修 G2 FB-04 · A9）」 |
| **修法** | `RpcInterceptor.onHalfClose()` 里把 `otelCtx.makeCurrent()` 的 scope **包住 `super.onHalfClose()`**（= handler 执行期）⇒ 业务代码 `Span.current()` 就是 server span（`RpcInterceptor.java:456`） |
| **`otelCtx` 来源（我域自核）** | `:270` `Context otelCtx = Context.current().with(span);`（在 `interceptCall` 里创建，**携带 server span**）⇒ `onHalfClose` 闭包捕获 ⇒ **有效** ✔ |
| **scope 不泄漏** | `try (Scope ignored = otelCtx.makeCurrent())` ⇒ try-with-resources ⇒ **不泄漏** ✔（与 G4 守门第 4 条同口径）；`makeCurrent()` 三处 = `:391`（close）· `:456`（onHalfClose · 新增）· `:490`（onCancel）⇒ **非嵌套**（`onHalfClose` → `close` 顺序） |
| **Go 出处（我域侧独立再核）** | Go 侧 `SpanFromContext(l.ctx)` **6 处全在 `internal/logic/*logic.go`**（`getuserlikelistlogic.go:34` · `getusertotallikelogic.go:34` · `gettargetlikerlistlogic.go:35` · `likeactionlogic.go:161` · `getlikecountlogic.go:37` · `getlikestatelogic.go:37`）⇒ **全部 gRPC handler 路径**、**给已有 server span 加属性**、**不新建 span** ⇒ 与 G4「不建子 span」的取舍**一致** ✔ |
| **我域受益面（我域自核）** | 我域对应落点 = `LikeDomainService`（`LikeSpans.queryUserId` / `cacheHits` / `cacheStatusHit` …）⇒ 跑在 **gRPC handler 线程** ⇒ **FB-04 正是我域 A9 的必要前提** |
| 🔴 **合并前我域 A9 属性的实际效果 = 零** | `onHalfClose` 未包 scope ⇒ handler 执行期 `Context.current()` 是**根 context** ⇒ `Span.current()` = **invalid（no-op）span** ⇒ `LikeSpans` 的属性**静默丢弃** ⇒ ⇒ 域侧 A9 落码在 FB-04 合入前是「**跑绿但零效果**」⇒ **A9 端到端结论必须等合并** |
| **我域 A9 守门的层级（勿误当端到端）** | `LikeSpansAttributeGateTest` 用 `runInSpan(...)` = **手工** `tracer.spanBuilder("gate").startSpan()` + `makeCurrent()` ⇒ **契约级**（证明「有 span 时属性会落到那个 span」）⇒ **不覆盖**「真实 handler 链上 handler 期有无 span」⇒ 11/11 绿 **≠** 端到端生效（与 §6 第 4/5 条一致，**无需订正**） |
| **MQ 路径 = N/A（我域自核）** | `LikeUpdateKafkaListener.onMessage` 走 `MqConsumer.traceConsumer(Context.current(), …)` ⇒ **自建** consumer span ⇒ **不依赖** FB-04 ⇒ `messaging.*` 属性**本来就生效**（与 Go 侧 `TraceConsumer` 同形）⇒ **不受本改动影响** |
| ⚠️ **跨树影响面表述的差异（不是 G4 算错）** | G4 写「全仓 `src/main` 里 `Span.current()` **只有 1 处**（comment-rpc）」⇒ 在 **G4 自己树**上成立（**我域 `LikeSpans` 未合入它树**）⇒ **两树不同步** ⇒ 合并后我域 `LikeSpans` 将是**第 2 处** ⇒ 但它是**受益者**（从 no-op 变真生效）⇒ **域侧零改动** ⚠️ 已就此给 G4 提 javadoc 订正项（它的 javadoc 合并后会「过时」） |

⇒ **我域单据状态更新**：**FB-04** 🟢 **已落**（`853767b`）· 域侧**零改动** · **端到端 E2E 仍待合并后补**（§6 第 4 条）。

### 🟢 **G4 FB-04 最终回执**（`23d1c2a` · 2026-10-27 · 基线 `5564c27` 之上 **33 提交** · 未 push）

| 项 | G4 回执内容 | G2 独立复核 |
|---|---|---|
| **落码** | `RpcInterceptor.java:453-465`（`onHalfClose()`）· `:456` **新增** `try (Scope ignored = otelCtx.makeCurrent()) {`（**包住** `super.onHalfClose()` = handler 执行期）· `:457-460` `deadlineEpochMs == Long.MAX_VALUE` 分支**也在 scope 内** · `:461-463` `DeadlineState.bindToCurrent` **顺序不变**（仍在 `super.onHalfClose()` 之前） | ✅ **一致**（我核的是 `853767b` 的同一处改动）⇒ ⚠️ **`DeadlineState` 仍在 scope 内** ⇒ **域侧 E66 的载体不受影响**（`DeadlineState` 与 OTel scope 是两套） |
| **守门** | 新增 `RpcServerSpanCurrentGateTest`（**4 条**）：① handler 里 `Span.current()` 有效且 recording ② 属性真落在**导出的** server span 上 ③ handler 看到的就是导出的那个 span（**不是子 span**）④ scope **不泄漏**到下一次请求 · 辅助 `runCalls(int)`（**每次调用前清空观测与导出** ⇒ 不依赖 JUnit 方法执行顺序） | ✅ 4 条设计与我的影响面判断**吻合**（尤其 ③ 对应 Go「不新建子 span」） |
| **A 轴** | **只**触及 **A9**，未触及其它 8 轴 | ✅ 与我域判断一致 |
| **Go 出处** | `go-zero v1.10.2 zrpc/internal/serverinterceptors/tracinginterceptor.go:18-24` ＋ `:95-107`；like 域 6 处 `SpanFromContext` | ✅ 我域**独立 grep** 得**同样 6 处**（文件与行号逐条一致） |
| **回归口径** | 第八次全量 `mvn -B clean verify` ⇒ **52 模块 / 29 有测试 / 2461 条 / 0 失败 / 0 errors / 4 skipped / 20:21 min** · 双侧探针 `run-parity.sh` **EXIT=0 · 56 例**（一致 54 / 不一致 0 / 已签字偏离 2）＋ `run-rpc-timeout-parity.sh` **EXIT=0 · 3 例** · 三门禁 0/0/0 | ✅ 我域用 **surefire XML 独立复核**了 52 模块里我域 8 个模块的条数 ⇒ **592** ⇒ 与它的明细表**逐条一致**（见 §4·七） |
| **写权自证** | `git diff --name-only 5564c27..HEAD` = **4 个文件，全在 `service/common/**`** ＋ 本台账 ⇒ **零域侧改动** | ✅ 我域**零改动**（本轮我域只改 `like-rpc` 一个**测试类** + 台账 + 一处 yaml 注释缩进） |

### ⚠️ 影响面**精确化**（G4 第 ⑤ 条第 3 点触发 ⇒ G2 独立核过）
- G4 说「**HTTP 侧不用管** —— `HttpMiddleware.java:332` **已有** `makeCurrent()` ⇒ current span 可见性本来就具备 ⇒ 本次修法**只落 gRPC 侧**」。
- ⇒ **G2 独立核我域六组件的 `Span.current()` 用量（主源码，排除测试）**：
  | 组件 | `Span.current()` 命中 | 结论 |
  |---|---|---|
  | **like-rpc** | **2 处**（`LikeSpans.java:242` / `:246`）⇒ 跑在 **gRPC handler 线程** | ✅ **FB-04 的唯一受益者** |
  | like-api | **0** | N/A（HTTP 侧的 `makeCurrent` 对我域**无作用**） |
  | message-api / message-rpc | **0 / 0** | N/A |
  | task-api / task-rpc | **0 / 0** | N/A |
- ⇒ ⇒ **FB-04 对我域 = 只惠及 `like-rpc` 一个组件**，其余五个组件 **N/A** ⇒ **域侧零改动**、**零待办**（除 like-rpc 的合并后 E2E）。

#### 🔴 **间接路径也断了**（G2 独立核 ⇒ 补记 · 2026-10-27 10:35）
G4 回执 ④ 声称「共享层 `Observability.mark*` 是**显式传 ctx** ⇒ 不受影响 ⇒ 零回归」⇒ **G2 不采信转述、独立核过**：
- **Go 侧全仓 `SpanFromContext` = 12 处**（G2 独立 grep 逐处分类）：`service/common/observability/observability.go` **4** · `service/like/rpc/internal/logic/` **6**（6 个 logic 文件各 1）· `service/knowledge/api/internal/telemetry/` **2**（**范围外**）⇒ **与 G4 的分类逐条吻合** ✔
- **RAG 侧对应**：`Observability.java` 的 4 个 `mark*` 方法**签名首参就是 `Context ctx`**（`:68` `markBusinessError` · `:89` `markSystemError` · `:115` `markTimeout` · `:138` `markDurationAndSlow`）⇒ 方法体是 **`Span span = Span.fromContext(ctx);`**（`:73` / `:94` / `:120` / `:139`）⇒ ⇒ **不依赖 current span** ⇒ ⇒ **FB-04 改 `onHalfClose` 的 scope 对这 4 处零影响** ⇒ ⇒ **我域 rpc 侧（含 like-rpc 走 `Observability.mark*` 的路径）零间接影响** ✔
- ⇒ 结论：FB-04 对我域的**全部**影响面 = **只有 `like-rpc` 的 `LikeSpans` 那 2 处**（直接依赖 current span）⇒ 与上文「六个组件逐个实测」的结论**互相独立、结论一致**。
- ⚠️ **合并后必查（like-rpc）**：`LikeSpans` 的属性**从「静默丢弃」变成「真落」** ⇒ 这是**行为变更**（预期）⇒ 收口回归里 **like-rpc 的 span 相关守门必须仍绿** ⇒ 若红 ⇒ 优先怀疑「属性真生效后与既有断言冲突」，而不是「没生效」。

### 📌 G4 对 like 域落码的三条结论（G2 已全部采纳 ⇒ 无异议）
1. **落码形态** = `Span.current().setAttribute(...)`（等价 Go 的 `trace.SpanFromContext(l.ctx).SetAttributes(...)`）⇒ **现在拿到的是真的 server span**（修前是 invalid no-op span ⇒ **跑绿但零效果**）；
2. **不要自建子 span**（Go 是**给已有 server span 加属性**、不新建 ⇒ 自建会让 trace 里多一个 Go 没有的 span ⇒ **结构漂移**）⇒ 我域 `LikeSpans` **已是**「给 current span 加属性」的写法 ✔；
3. **HTTP 侧不用管**（见上表）。

⇒ **本层对我域已闭环**（FB-04 落码 + 守门 + 变异 + 全量 + 回执）⇒ 我域**不需要**请求共享层再跑全量（**域侧零改动**）。

## 4·七、🟢 **G4 第八次全量口径**（2026-10-27 · 09:57:42 → 10:18:03）· G2 **用 surefire XML 独立复核**

| 项 | 值 |
|---|---|
| 结果 | **BUILD SUCCESS / `MVN_EXIT=0`** · **52 模块**（Reactor）· **29 个有测试模块** · **2461 条** · **0 失败 / 0 errors / 4 skipped** · **20:21 min** |
| 我域在 **G4 树**里的口径（**G2 独立复核**） | `message-api` **88** · `message-rpc` **84** · `like-common` **18** · `like-api` **81** · `like-rpc` **96** · `task-api` **90** · `task-rpc` **114** · `message-common` **21** ⇒ **我域 8 模块合计 = 592** |
| 🆕 **G4 第九次全量（2026-10-27 · `c15dfeb` 之上 · `e7d14f3`）** | **转述（G4 自报，未独立复核）**：`mvn -B clean verify` ⇒ **52 模块 / 29 有测试 / 2470 条 / 0 failures / 0 errors / 4 skipped / 20:22 min**（10:34:55 → 10:55:17）⇒ 相对第八次 2461 ⇒ **+9**（槽位守门 4 ＋ **FB-05 守门 5**）⇒ `service/common` **311 → 320**、**其余 28 模块逐条一致** ⇒ ⇒ **G4 树里我域 8 模块仍 = 592** ⇒ ⚠️ 与我域树口径（**635**）**不可混用、不可相加** |
| 🆕 **G4 第十次全量（2026-10-27 · 最新）** | **转述（G4 自报，未独立复核）**：`mvn -B clean verify` ⇒ **52 模块 / 2470 条 / 0 failures / 0 errors / 4 skipped / 20:23 min**（**11:18:58 → 11:39:21**）⇒ 相对第九次 2470 ⇒ **条数持平**（本轮 G4 侧是**台账订正**为主：`2f3d5c2` 订正「task 域副作用」误判 · `ee1b797` 订正 A3 轴行与两处旧前提 ⇒ **零行为变更** ⇒ 条数不变与之一致）⇒ ⇒ **第十次全量窗口已于 11:39:21 收工** ⇒ ⇒ 🟢 **G4 当场让出大回归窗口**（回执 ⑤：「本层现在不起大回归 ⇒ 你可以先跑」）⇒ 我域可先跑 |

⚠️ **口径隔离（关键，别混用）**：**592 = G4 树口径** ⇒ 该树**不含**我域尚未合入的守门（Redis 超时 8 条 · A9 span 11 条 · form 闸门 5 条 等）⇒
⇒ **我域自己的口径另计**（本域台账记的是我域树实测值）⇒ **两个数不可相加、不可互称**。
⇒ **我域树口径（2026-10-27 · 第三轮实测）**：`message-api` **98** · `message-rpc` **84** · `like-api` **89** · `like-rpc` **111** · `task-api` **97** · `task-rpc` **117** ⇒ **六模块合计 = 596**（上一轮同口径 **594** ⇒ **+2** = 本轮新增的 Redis **连接池**断言 ⑤⑥）· **0 failures / 0 errors / 0 skipped**（与上一轮同口径 ⇒ **无回归**）⇒ 🆕 **八模块口径**（加 `like-common` 18 ＋ `message-common` 21）= **635** · BUILD SUCCESS · 6:06 min。<br>🆕 **第四轮（2026-10-27 · like 域池数值轴）· 已复跑确认**：like-rpc 的池守门 ⑤⑥ ⇒ **like-rpc +2 条** ⇒ ⇒ **六模块 = 598** / **八模块 = 637**（**本轮实测**：8 模块 `mvn test` = **BUILD SUCCESS · 6:09 min** · 逐模块 `message-common` 21 · `message-rpc` 84 · `message-api` 98 · `like-common` 18 · `like-api` 89 · **like-rpc 113**（111 + 2）· `task-api` 97 · `task-rpc` 117 ⇒ **maven 汇总行 = surefire XML（按 mtime 切本轮）双口径相等** · **0 failures / 0 errors / 0 skipped** · like-rpc 目录里那 **1 个陈旧 XML**（已删探针类）被正确剔除）⇒ ⇒ **上一行的 596 / 635 是「池断言落地前」的口径 ⇒ 原值保留**（那是第三轮的实测）⇒ ⇒ **本行不覆盖它，只记增量**。<br>🆕 **并发纪律（G4 第十次全量窗口）**：G4 于 11:19 起**第十次**全量并知会「窗口内别跑全量」⇒ ⇒ 我域**只跑域内单类**（like-rpc 守门类）⇒ ⇒ 待其**窗口结束**（等 640s）才起域级复跑 ⇒ ⇒ **两轮不重叠**（实测）⇒ 🟢 其 `/tmp/g4-full10.log` 本域读得 **BUILD SUCCESS · 20:12 min**（**G4 树口径**；⚠️ **本域未独立复核其逐模块条数** ⇒ 只记结果行）⇒ ⚠️ 期间 macOS 有**系统服务**被内存压力挤掉（Siri/textkit 等，**不是 maven**）⇒ ⇒ 印证「同窗并发 maven ⇒ EXIT=137」这条共识。

### ⚠️ 取数字的**方法论**（本轮踩到两条 ⇒ 留档）
- ✅ **权威口径 = surefire 的 `TEST-*.xml`**（读每个 `testsuite` 的 `tests`/`failures`/`errors`/`skipped` 属性再求和）⇒ 本轮据此复核，**8 个模块逐条与 G4 的明细表一致**。
- ❌ **不可用「按 maven 日志的 `[INFO] Building <module>` 分段聚合」**：本轮用它把 `task-rpc` 算成 **110**（真实 **114**）⇒ **错 4 条** ⇒ 根因 = 日志分段与 surefire 输出的归属**不严格对齐**（`-am` 会先构建依赖模块）。
- ❌ 同样**不可**用 `grep -h "^Tests run:" *.txt`（JUnit5 的 `.txt` 是**每类一行**且**都带 `-- in`** ⇒ 没有独立的模块汇总行）。
- ⇒ **判定式**：跨树的数字只能来自**产物目录的测试报告**；日志只用来定位**失败**，不用来**求和**。
- 🔴 **但 surefire 的报告目录【不清空】** ⇒ 跨轮**残留**会污染「目录求和」：本轮 like-rpc 目录里有 **20 个** XML ⇒ 求和得 **112**，而 maven 模块汇总行是 **111** ⇒ 差 1。
  - **元凶已定位** = **`LikeRpcServerSpanCurrentProbeTest`（1 条，mtime 03:52:04）** ⇒ 那是**已删除的探针类**留下的**陈旧产物**（`find` 确认源码已不在该树）⇒ 剔除后 = **111** ⇒ **双口径一致** ✔。
  - ⇒ **判定式（补）**：**求和前必须先确认「目录里没有跨轮残留」** ⇒ 两条做法：① **按文件 mtime 切出本轮** ② **与 maven 模块汇总行对照**；**两条口径必须相等**才采信。
  - ⇒ **附带一条通用形态**：「**源码已删、报告还在**」⇒ 陈旧 XML **不会随源码删除而消失** ⇒ 同机多轮跑的仓，`target/` 里的测试报告**不可直接当历史证据**。
  - ⇒ **第三轮六模块的交叉验算结果**（maven 汇总行 vs surefire XML **按 mtime 切本轮**）：`message-api` 98 · `message-rpc` 84 · `like-api` 89 · `like-rpc` 111 · `task-api` 97 · `task-rpc` **117** ⇒ **逐条相等** ⇒ 合计 **596** ✔（八模块 **635** ✔）⇒ ⚠️ 第三轮 like-rpc 目录里**仍有 1 个陈旧 XML**（`LikeRpcServerSpanCurrentProbeTest` · 源码已删）⇒ 被 mtime 切分正确剔除 ⇒ **未污染求和**（旧坑仍在 ⇒ 判定式继续有效）

### 🔴 合并后我域**必做**动作（收口回归 · §5.3e）

⚠️ **本域当前仍用「等价判据」顶着**（因为 G4 的落码**未合入本树**）⇒ 合并后必须：

1. **复跑本域 3 处 `DEADLINE_EXCEEDED` 断言**（`LikeRpcGrpcInterceptorWiringTest:178` ·
   `LikeRpcSlowThresholdGateTest` · `LikeRpcTimeoutCapTest` × 4）
   ⇒ 原因 = G4 `7488b3d` 修的「**进程级静态池**终止即永久」缺陷**本域同构**
   （`ObservabilityLifecycle` 被本域三个 rpc 组件全部 `@Import` ⇒ 每个 Spring 上下文关闭都毒化池）
   ⇒ 合并后池**自愈**（`AtomicReference` + CAS 重建）⇒ 这三处应从「**侥幸绿**」变「**真绿**」
   ⇒ ⚠️ **若仍红 ⇒ 不是新回归，是毒化未被覆盖 ⇒ 直接找 G4**<br>   🔴 **合并顺序硬约束（G1 预告 · G2 独立核实，2026-10-27）**：`3b573fb`（修「关闭路径触发 web 类加载」）**不能单独合入** ⇒ 它让 `ObservabilityLifecycle.shutdown()` **真的执行** ⇒ 若 `7488b3d`（池自愈）**未同时在位** ⇒ 池「终止即永久」⇒ 同模块**后续** Spring 上下文被毒化 ⇒ 拦截器返回 **`INTERNAL`** 而非 `DEADLINE_EXCEEDED` ⇒ **由绿转红**<br>   ⇒ ⇒ **我域实测暴露面**：like-rpc **9** 个 `@SpringBootTest` 上下文（task-rpc 4 / message-rpc 10） ⇒ ⇒ **本域三个 rpc 组件全部同构**<br>   ⇒ ⇒ ⚠️ **G4 树自身的落地顺序与 G1 给的「分批顺序」相反**（G2 实测 `git log` 倒序）：`3b573fb` 在**第 21 位（更早）**、`7488b3d` 在**第 17 位（更晚）**⇒ ⇒ **整批合并 G4 分支 ⇒ 最终态两条都在 ⇒ 约束天然满足** ⇒ ⇒ **但「只挑部分 commit」⇒ 必须带上 `7488b3d`**，否则本条会红 ⇒ ⇒ **别把红判成「域侧回归」**<br>   ⇒ ⇒ ⚠️ **另有一层「绿是因为没走到」**：修 `3b573fb` 之前，纯 gRPC 进程的 `@PreDestroy` 先抛 `NoClassDefFoundError` ⇒ `RpcInterceptor.shutdown()` **根本没机会执行** ⇒ 静态池**侥幸存活** ⇒ ⇒ **前几轮全量一直绿不是「本来就对」，是缺陷把保护罩挡住了**（G1 提出 · G2 采信）<br>   🟡 **2026-10-27 12:5x 实测撞过一次红 · 根因已定位 · 不是域侧回归**：本轮 8 模块域级回归跑到 message-rpc 时 `MessageRpcTimeoutGateTest` 报 **`Tests run: 2, Failures: 0, Errors: 2`** ⇒ 最内层根因 = `java.net.BindException: Address already in use` on **端口 64118** ⇒ ⇒ 64118 落在 **OS 临时段 49152–65535**（**不是**本域专用段 21700–21799）⇒ ⇒ **根因 = 两个 lane 同时跑 maven ⇒ 撞端口**（`ps` 当时显示 **G1 树**在跑）⇒ ⇒ **独占重跑 message-rpc = 84 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS** ⇒ ⇒ **确认：不是代码缺陷、不是域侧回归、也不是「静态池毒化」那条路径**<br>   ⇒ ⇒ **教训（本域留档）**：**「起跑前查一次 ps」是不够的** ⇒ ⇒ **别的 lane 可以在你跑的过程中起 maven** ⇒ ⇒ ⇒ **必须双向错峰**：① 起跑前查真实占用者（已做）② **我也要给别人发错峰知会**（⚠️ **本轮之前我只给 G4 发过，没给 G1 发 ⇒ 这次撞车就是那个漏洞的代价**）③ 撞车后**先看最内层 `Caused by`**（端口 / 绑定 / 超时 ⇒ 环境；断言 / 状态码 ⇒ 代码）⇒ ⇒ **别看到「由绿转红」就判回归** |
2. **复核共享层的 `REJECT_COMMANDS` 在本域生效**（`GoStyleRedisClientAutoConfiguration`）
   ⇒ 本域 like-rpc / task-rpc **自带** `spring-boot-starter-data-redis` ⇒ `@ConditionalOnClass` 命中
   ⇒ 且本域**零域内副本** ⇒ **共享层那份生效** ⇒ 断连即拒单 ⇒ 不再撞 `rpc-timeout: 2s`
   - ⚠️ **口径订正（2026-10-27 · G2 自查）**：本条原先写「断连即拒单（**实测 1ms**）」⇒ **那个 1ms 是我域上轮探针在「手动配 `REJECT_COMMANDS`」条件下**测得的（[`like/rpc.md`](rpc.md) 对拍表已标注「G4 共享层落码后」）⇒ ⇒ 它证明的是「**该配置项有效**」，**不是**「**共享层那份已在本域生效**」—— **共享层那份尚未合入本树** ⇒ ⇒ 本条的**真实验收** = 合并后在本域**不配任何域内配置**、让共享层那份生效 ⇒ 再跑一次**断连活体** ⇒ 量首条命令耗时。
   - ⚠️ **探针配方已成跨域共识**（G3 2026-10-27 确认会用同款配方补它域的断连活体 ⇒ 勿各自另造）：**先连上 → 静默等待（期间不发命令，否则 watchdog 重连会掩盖排队窗口）→ 外部 `docker stop`（优雅 FIN，不是 `kill` 的 RST）→ 量第一条命令挂多久**。
   ⇒ ⚠️ **顺带确认「遮蔽契约」**：本域**不得**新建 `RedisDisconnectedBehaviorConfig` 之类的域内副本
   （那会遮蔽共享层 ⇒ E66 等后续修正在本域**静默失效**）
   - 🆕 **2026-10-27 第三轮追加（Redis 连接池守门 ⑤⑥）**：G4 那份 `GoStyleRedisClientAutoConfiguration` 的 customizer **只调 `disconnectedBehavior(REJECT_COMMANDS)`**（G4 树实测 `GoStyleRedisClientAutoConfiguration.java:129-131` ⇒ **不调 `timeoutOptions(...)`、不碰池**）⇒ ⇒ 本域的池守门（task-rpc ⑤⑥ / like-rpc）**理论上不受影响** ⇒ ⚠️ **「理论上」不算数** ⇒ 合并后必须**复跑**（task-rpc 6 条 ＋ like-rpc **6 条**）⇒ 若 ⑤/⑥ 红 ⇒ 说明共享层或 Boot 装配路径碰了池 ⇒ 属**新回归** ⇒ 直接找 G4。
3. **A3 升 ✅ 的前置**：共享层的 `GoStylePathNormalizingFilter` 合并后 ⇒ 跑**真 Tomcat 探针**复核
   ⇒ 本域 A3 才能整体升 ✅（Go 侧已 L4 活体坐实：5 种路径变体全 200）
4. 🆕 **E66 落码后：本域 Redis 守门必须仍绿**（task-rpc 的 ① `commandTimeoutMechanismIsEnabled` ＋ like-rpc 的 Redis 超时守门）
   ⇒ E66 的落点会**整体替换** `ClientOptions.timeoutOptions` 那份 default source ⇒ 若新实现不含「命令超时」语义 ⇒ ①（`getTimeoutOptions().isTimeoutCommands()` 必须为 **true**）会红
   ⇒ ⚠️ ① 红 = **新回归**（不是「E66 故意关掉了超时」）⇒ **直接找 G4**
   ⇒ 配套：新 source 在**本请求没有 deadline** 时必须回落到 **`getClientConfiguration().getCommandTimeout()`**（本域 = 3s）⇒ 否则命令失去 3s 上限（断连从「3s 快速失败」退化为「无限等待」）⇒ ⇒ **这条已写进本域台账的 E66 硬约束段（并已独立核实 + 订正其机制表述）**
5. 🆕 **两个「合并后必红」的共享门禁**（2026-10-27 第四轮**实测** ⇒ **写权在 G4 ⇒ 已派单** · 编号 `FB-S1`/`FB-S2`）
   ⚠️ **编号说明**：已发给 G4 的消息里把这两条写成 **FB-06 / FB-07** ⇒ 与本域 §4 的 FB-0x 系列**不同域内编号**，但与 **task 域台账**的 FB-06 撞名 ⇒ ⇒ **以各域台账的 `FB-S*` 为准**（`S` = 跨域共享门禁）⇒ G4 侧按**内容**识别。
   - 🟡 **FB-S1 · `tools/difftest/check-tomcat-limits.py`**：**对 like 域零影响**（期望表里 `like-api` = `envelope`，本域 yml 未动 ⇒ 该门禁的红**只来自 `task-api` 那一条**）⇒ ⚠️ 但**合并后整仓复跑仍会红** ⇒ **仍须 G4 修**（修法与影响面见 [task/vs-common.md](vs-common.md) 第 5 条的 `FB-S1`）
   - 🔴 **FB-S2 · `tools/difftest/check-wave-coupling.py`**：我树 **EXIT=1**（6 条 `common_radiation` 漂移）；主干与 G4 树 **EXIT=0**
     ⇒ **其中 2 条与本域相关**：`GoStyleGrpcClientBreakerInterceptor` 增 `like`（**生产代码** `LikeRpcClient`）· `MqConsumer` 增 `like`（**生产代码** `LikeUpdateKafkaListener`）
     ⇒ ⇒ 另 4 条是 **task 域**的（`GoStyleFormLimitMode` / `GoStyleFormValueLimitInterceptor` / `GoStyleRpcBreakerInterceptor` / `GoStyleRpcSheddingInterceptor` ⇒ **全部来自 `src/test/` 的 import**，**本域与 task 生产代码对这些类的 import = 0**）
     ⇒ ⇒ **根因 = 快照过期，不是代码回退**：`docs/DRIFT/_cross/cross-domain-coupling.md`（**744 行 · 三棵树逐字相同**）的 `<!-- wave-coupling-snapshot -->` 块**未含 X-6(task) 与 A9(like) 的接入** ⇒ ⇒ **文档三棵树一致 + 主干/G4 树绿 ⇒ 差异全部来自域侧接线代码**
     ⇒ ⇒ **修法在 global 写权 ⇒ G4**：用 `python3 tools/difftest/check-wave-coupling.py --print` 的输出更新快照块（目标值已取到 = `/tmp/g2-coupling-target.json`）

⚠️ **E66 的硬约束（G4 已探明，落地前必须解决）**：
Boot 4 `LettuceConnectionConfiguration.createClientOptions` 的字节码顺序 =
**offset 146 先写 `timeoutOptions` → offset 151 才应用 customizer**
⇒ ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖既有那份 `TimeoutOptions`**
⇒ ⇒ 落码前必须先解决「**保留既有命令超时**」
⇒ 🆕 **本域提供的实测数据**：命令超时**压根不挂在 `ClientOptions` 上**
（`javap` 实测 `io.lettuce.core.ClientOptions` **没有** `getCommandTimeout()`，命中数 = **0**）
⇒ ⇒ customizer 里要读回的是 **`getClientConfiguration().getCommandTimeout()`**（**不是** yml、**不是** `getTimeoutOptions()`）
⇒ ⇒ 且本域的**域侧调用点清单**已备好（like-rpc **11 处** / task-rpc **3 处** · 见本文件 §4 的 FB-05 / FB-06）

🟢 **2026-10-27 G2 独立核实（`javap -c` ＋ 源码双证）—— 结论成立，但「机制表述」有一处不准 ⇒ 订正如下**
- ✅ **顺序与偏移全部复现**：Boot 4.0.8 `spring-boot-data-redis` 的 `LettuceConnectionConfiguration.createClientOptions`（`ObjectProvider<LettuceClientOptionsBuilderCustomizer>, SslBundle`）反汇编 ⇒ `142-149` `aload_3` → `invokestatic TimeoutOptions.enabled()` → `146` `invokevirtual ClientOptions$Builder.timeoutOptions` → `150-151` `aload_1` / `ObjectProvider.orderedStream()` → `157` `invokedynamic`（= `lambda$createClientOptions$0` ⇒ 逐个 `customize(builder)`）→ `162` `Stream.forEach` → `168` `build()` ⇒ ⇒ **offset 146 / 151 与 G4 所述逐位吻合** ⇒ ⇒ **「customizer 再调 `timeoutOptions(...)` 会覆盖前一份」这个结论成立** ✔
- 🔴 **订正 1（机制表述不准）**：G4 原句写「offset 146 先写 `timeoutOptions`（**来自 `spring.data.redis.timeout`**）」⇒ **不对** ⇒ 源码 `LettuceConnectionConfiguration.java:229` = `builder.timeoutOptions(TimeoutOptions.enabled());` ⇒ **入参是硬编码的 `TimeoutOptions.enabled()`**（default source）⇒ **`spring.data.redis.timeout` 根本没进 `ClientOptions.timeoutOptions`** ⇒ 它走的是**另一条路**：`:173-174` `if (getProperties().getTimeout() != null) { builder.commandTimeout(getProperties().getTimeout()); }` ⇒ ⇒ 落 `LettuceClientConfiguration.getCommandTimeout()` ⇒ **RedisURI.timeout**（⇒ 也就是本域守门 ③ 读的那个值）
- ⇒ ⇒ **对修法的影响 = 无**（结论与处方都成立）：customizer 仍必须回读 **`getClientConfiguration().getCommandTimeout()`**（**不是** yml、**不是** `getTimeoutOptions()`）
- 🔴 **但对实现有一个新增约束（原文没写）**：customizer 的 `TimeoutSource` 会**整体替换**掉 `TimeoutOptions.enabled()` 那份 default source ⇒ ⇒ **新 source 在「本请求没有 deadline」时必须回落到 `getCommandTimeout()`（本域 = 3s）** ⇒ 否则**命令会失去 3s 上限**（断连时从「3s 快速失败」退化成「无限等待」）⇒ ⇒ **本域守门 ①（`getTimeoutOptions().isTimeoutCommands()` 必须为 true）就是钉这一条的那根网** ⇒ 🔴 **E66 落码后若 ① 变红 ⇒ 属新回归 ⇒ 直接找 G4**

### 📥 G3 跨 lane 回执的**逐条复核**（2026-10-27 · 不采信转述，全部独立取证）

| G3 报的 | 本域复核结论 |
|---|---|
| **① U+FFFD 我域 3 条** | 🟢 **在我树已干净**（**字节级**扫描：FFFD=0）⇒ 但 **主树 / G1 / G3 / G4 树仍各坏 1 处** ⇒ **合并以我树版本为准**（我域写权面）⇒ 见 [`like/rpc.md`](rpc.md) B 节 + [`message/rpc.md`](../message/rpc.md) + [`message/api.md`](../message/api.md) 同款条目 |
| **② like-rpc 同款启动 flake** | ✅ **成立且已在本域落码修法**（`StringBuffer` ＋ 等日志行 ＋ `drain.join(10s)`）⇒ 并**独立核过全我域同款范围**：全域 `grep -rn "destroyForcibly" service/{like,task,message} --include=*.java` = **1 命中**（就是这处）⇒ `TaskRpcFatJarArtifactGateIT` 是**产物层**（不起进程）⇒ **不是**同款；`message-rpc` **无**此类 |
| **③ Redis 超时口径**（Boot 4 两属性不再共用；不配 `connect-timeout` ⇒ Lettuce 默认 **10s** ⇒ **漂移 2 倍**） | 🟢 **与我域独立复核结论一致** ⇒ **我域已修，无需再动**：`like-rpc` 补 `connect-timeout: 5s`；`task-rpc` 补 `timeout: 3s` ＋ `connect-timeout: 5s`（双侧活体对拍：Go **1694ms** vs RAG 修前 **60021ms** vs RAG 修后 **3ms**）⇒ 守门 **12 条**（`LikeRpcRedisTimeoutsGateTest` **6** ＋ `TaskRedisCommandTimeoutGateTest` **6**；后者 2026-10-27 本轮 +2 = Redis **连接池** ⑤⑥，前者同日 +2 = 同样两条池断言 ⇒ **两域的池都有守门**）<br>🆕 **2026-10-27（本轮 · 下一轮候选已执行）**：**like 域的池数值轴已修** —— `min-idle: 4` ⇒ **`8`**（= Go 的 `MinIdleConns`，go-zero 的 `idleConns = 8` ⇒ **精确对齐**）＋ 守门 ⑤⑥ ⇒ 详见 [`like/rpc.md` 的 `DRIFT-LIKE-RPC-REDIS-POOL`](rpc.md#🟡-drift-like-rpc-redis-pool新登记--2026-10-27redis-连接池数值未对齐形态已对齐仅数值不同-🆕-数值轴已修min-idle-4--8--go-确切值·-残留--max-active性能面) |
| **④ G3 扫到的 13 条域外 U+FFFD** | 其中 **2 条标「共享面（无人拥有）」** = `tools/errmsg-probe/README.md:59` ＋ `tools/sql-statement-probe/favorite/compare.py:561` ⇒ **`tools/**` 不在我写权面** ⇒ **只登记、转派 G4**（见 §6 第 7 条） |

⚠️ **判据坑（同款，全域适用）**：**`read` 的输出会把正常的汉字渲染成 `U+FFFD×3`** ⇒ 判 U+FFFD **只认字节级扫描**；
`grep -rn $'\xef\xbf\xbd'` 在本机也会 0 命中 ⇒ 同样不可用。

> 📌 **本文件引述里的坏字符已改写为 ASCII 标记 `U+FFFD×3`**（3 个替换字符 = 1 个 UTF-8 汉字的 3 字节全丢）
> ⇒ **台账自身不再植入坏字节** ⇒ 我域复扫（`docs` + 我域源码，排除 `target/`）**0 命中**
> （余下 2 处在 `docs/Pitfalls/**`：`message/rpc.md` 2 处 + `users/user/rpc.md` 1 处 ⇒ **故意保留**（记录故障现象本身）＋ **不在我写权面** ⇒ 不改）。
> ⇒ 需原样比对时，按「`文件:行号` + `U+FFFD×3`」去主树/G1/G3/G4 树取原件。

### 🔴 第五轮追加（2026-10-27 13:1x · G2 独立取证）· FB-S2 的两条 like 漂移：**机制订正** ＋ **辐射度口径的第三个失真方向**

#### ① 🔴 **订正上一条**（本台账 §4·六 末段 FB-S2 的 like 那 2 条）

⚠️ **原写**（保留不改，供追溯）：「`GoStyleGrpcClientBreakerInterceptor` 增 `like`（**生产代码** `LikeRpcClient`）· `MqConsumer` 增 `like`（**生产代码** `LikeUpdateKafkaListener`）」

⚠️ **订正**：第一条的**归属对、机制错** —— `service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java` 对该类**只在 javadoc 提及**（`:53` `共享层 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor}`）⇒ **零 import** ⇒ ⇒ **本域唯一的 `import` 在测试里**：`service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiGrpcClientBreakerE2ETest.java:3`（import）＋ `:126`（字段）⇒ ⇒ **归因必须按「import 判定」重述**（守门 `scan_common_radiation` 判的就是 import ⇒ 不是「文件里出现过这个名字」）
⇒ ⇒ **真机制 = 扫描注册型**（见 ②）⇒ ⇒ **漂移归属 `like` 成立，但成立的原因不是「生产代码 import 了它」**

⇒ ⇒ **教训（本域留档）**：**「文件里出现了这个名字」≠「import 了它」** ⇒ ⇒ 我原判据把 **javadoc 提及**当成了 import ⇒ ⇒ 与「零命中也要复核判据」同族：**判据必须是守门真正用的那个**（这里是 `import` 语句，不是文本出现）

#### ② **真机制 = 扫描注册型**（`import` 判定**看不见**这一类）

| 环节 | 证据（本树实测） |
|---|---|
| 共享层把客户端熔断注册成 Bean | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:104-105` = `@Component` ＋ `@GrpcGlobalClientInterceptor` |
| 域侧把它扫进容器 | `service/like/like-api/src/main/java/com/rag/like/like/api/LikeApiApplication.java:75-77` = `scanBasePackages = { "com.rag.like.like.api", "com.rag.common" }`（`service/message/message-api/.../MessageApiApplication.java:63-66` 同款） |
| ⇒ **结果** | **like-api / message-api 的生产 gRPC 调用真的过这个熔断拦截器**，而**域侧生产代码对它零 import** ⇒ ⇒ **`import` 判定完全看不见这条依赖** |

⇒ ⇒ 🔴 **对 G4 的口径建议（第三轴）**：`scan_common_radiation` 的 import 判定 **两头都不准**
- **高估**：**测试 import ≡ 生产 import**（我域 6 条漂移里 **5 条**属此类：task 4 条 + like 1 条）
- **漏计**：**扫描注册型依赖**（`@Component` + 域侧 `scanBasePackages` 含 `com.rag.common`）⇒ ⇒ **这恰恰是共享层最危险的一类**（改 `@Component` 的实现 ⇒ 所有扫 `com.rag.common` 的域**一起变** ⇒ **却不出现在辐射度清单里**）⇒ ⇒ 例如 `GoStyleGrpcClientBreakerInterceptor` 就属于这一类
⇒ ⇒ ⇒ **三种修法（G4 择一）**：① **分 MAIN / TEST 两轴**记录（治高估）② 快照里**保留层级标注**（治高估的读法）③ **扫描注册型依赖单列一轴**（治漏计 ⇒ 从各域 `scanBasePackages` ＋ 共享层 `@Component`/`@Order` 注解反推）
⇒ ⇒ ⇒ ⚠️ **无论选哪种，本域都不会回退任何测试** ⇒ **修法只能落在「快照/口径」侧**

#### ③ `MqConsumer` 增 `like` = **真生产依赖**（本轮阶段 2 A9 落码引入）

- `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeUpdateKafkaListener.java` ⇒ **`src/main`** 新增 `import com.rag.common.observability.MqConsumer;` ＋ 直接调用 `MqConsumer.traceConsumer(Context.current(), LikeSpans.MQ_TRACER_NAME, LikeSpans.MQ_SPAN_NAME, LikeSpans.messagingAttributes(record.key()), ctx -> consume(value))`
- ⇒ **Go 出处**：`service/like/rpc/internal/mq/likemq.go:83-84` 的 `observability.TraceConsumer(ctx, "like-mq", "LikeUpdateService.Consume", likeMessageAttrs("like-topic", key), func(ctx) error { … })`
- ⇒ ⇒ 🔴 **影响面（给 G4）**：**`MqConsumer` 现在是 like-rpc 的生产依赖** ⇒ **共享层改 `MqConsumer` ⇒ 会影响 like-rpc 的生产 MQ 消费路径** ⇒ ⇒ **合并后我域需复跑 like-rpc 的 MQ 相关测试**（`LikeSpansAttributeGateTest` ＋ MQ 监听相关用例）⇒ ⇒ 这是**本轮真实新增的共享层耦合**，请计入快照
- ⇒ ⇒ 📌 **对照**：task 域的两个 sink（`UserLikeProgressSink` / `ArticleLikeProgressSink`）也包 `MqConsumer.traceConsumer` ⇒ **`MqConsumer` 的域级引用方 = comment-rpc / hot-rpc / like-rpc / task-rpc（4 个域）**

#### ④ 📌 附：本域 4 条漂移里，**task 那 4 条其实不是「零依赖」**（跨树/跨域读法修正）

⚠️ 本台账此前写「task 生产代码对前 4 个共享类的 import = 0」⇒ **字面为真，但易被读成「task 域对共享层零依赖」** ⇒ ⇒ **订正如下**（本树实测）：

- `service/task/task-rpc/src/main/java/com/rag/task/task/rpc/TaskRpcApplication.java:32-34` ⇒ **import** 了 `GoStyleRpcProtectionChain` / `GoStyleProtectionProperties` / `GoStyleProtectionConfiguration` ⇒ `:138` 调用 `GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps)`
- ⇒ ⇒ **task-rpc 的生产路径真依赖 `GoStyleRpcBreakerInterceptor` 与 `GoStyleRpcSheddingInterceptor`** —— 但依赖是**链级**的（经共享层自己的 facade 组装）⇒ ⇒ **域侧对这 2 个类零直接 import** ⇒ ⇒ **`import` 判定把这条依赖记在 facade 名下（`GoStyleRpcProtectionChain`）⇒ 那一格本来就是对的** ⇒ ⇒ **不是漏计，是「依赖记在 facade 层」的合理口径**
- ⇒ ⇒ **只有 `GoStyleFormLimitMode` / `GoStyleFormValueLimitInterceptor` 是真零依赖**（本域 mode = OFF ⇒ 共享层不装 form 闸门 ⇒ 生产路径不碰这 2 个类）⇒ ⇒ 这 2 条漂移**纯粹是测试引用** ⇒ ⇒ **高估的典型样本**

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [x] **两侧同时跑**的探针（AGENTS.md §3.3）—— 🟡 **RAG 侧已有 L4**（A9 探针：真 server + 真链 + 真 OTel SDK ⇒ 坐实 `Span.current()` 在 handler 期 = invalid）；**Go 侧仍 L3**（未跑 Go 活体）⇒ §6 列了剩余缺口
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **5 条全部重核，4 条过期快照已订正**
- [x] §3 的域侧修法**落码 + 守门**（阶段 2）—— ✅ **D-2 已落**（like A9 span 属性 + 守门 11/11 + 变异验证）；🟡 **D-1 / D-4 / D-5 进行中**
- [x] §4 的派单**登记**（本文件 §4 **FB-01** ＋ **FB-04**）＋ **实时轨已发** `session send` 给 `G4Common`（FB-04 位 14）
- [x] **共享面不回写**（合并后由整合者统一更新）

## 6. 未验证项（本轮明确留下的缺口，**不写成已闭环**）

6. 🆕 **共享层「进程级静态池」缺陷在我域同构 ⇒ 合并后必须复核**（2026-10-27 · G4 `7488b3d` 触发我做静态自查）：
   - **G4 修的缺陷**：共享层的 `HttpMiddleware.HTTP_EXECUTOR` / `RpcInterceptor.TIMEOUT_SCHEDULER` / `RpcInterceptor.HANDLE_EXECUTOR`
     是 **`static final`** ⇒ `shutdown()` **永久终止** ⇒ surefire 一个模块一个 JVM ⇒ 模块内**多个 Spring 上下文**
     ⇒ 前一个上下文关闭后 `schedule()` / `submit()` 被拒 ⇒ 拦截器返回 **`INTERNAL`** 而不是 `DEADLINE_EXCEEDED`。
   - **触发钩子**：`service/common/src/main/java/com/rag/common/spring/ObservabilityLifecycle.java:52-56`
     （`@Component` + `@PreDestroy` ⇒ 调 `HttpMiddleware.shutdown()` + `RpcInterceptor.shutdown()`）
     ⇒ ⚠️ **我域三个 rpc 组件全部 `@Import` 了它**
     （`LikeRpcApplication.java:91` · `TaskRpcApplication.java:89` · `MessageRpcApplication.java:77`）
     ⇒ ⇒ **我域同构，不是「别的域才有」**。
   - **我域的暴露面（实测）**：`LikeRpcGrpcInterceptorWiringTest:178` 断言 `DEADLINE_EXCEEDED`
     （另 `LikeRpcSlowThresholdGateTest` · `LikeRpcTimeoutCapTest` × 4）
     ⇒ 且 like-rpc 有 **9 个** `@SpringBootTest`（properties 互不相同 ⇒ 9 个独立上下文）
     ⇒ 按 surefire 报告写入时间还原，该断言所在类排**第 7 位** ⇒ 其前已有 5 个 Spring 上下文跑过。
   - **G4 的修法**：`AtomicReference` + 取用时 `isShutdown()` 检出即 **CAS 重建**（自愈）⇒ 合并后**毒化自动恢复**。
   - ⇒ **我域动作 = 0**（域侧无自建静态池 ⇒ 只经共享层受影响 ⇒ 合并即受益）
   - ⚠️ **但「合并前我域的绿是真是侥幸」我未实证** —— 需**合并后**复跑本域这三处 `DEADLINE_EXCEEDED` 断言
     ⇒ **收口回归必查项**（这正是 G4 那句「绿可能只是没走到」）。

1. **A3 缺本域真实请求证据**：超 2048 参数的 query ⇒ 拒绝形状应是 `200 + {code:5001,msg:"系统内部错误"}`（`GoStyleFormLimitExceededException` → `GlobalExceptionHandler.java:229-236` 兜底 → `LikeErrorMessages.java:44`）⇒ **必须与 Go 侧同请求对拍**才算 L4。
2. **FB-01 的「RAG 返 404」** —— 🟢 **Go 侧已由 G4Common 活体坐实**（go-zero 真 server，5 种路径变体全 200，2026-10-27）⇒ 剩下的是 **RAG 侧**的对应证据，**正在 G4 手里**（共享层归一器 + 真 Tomcat 守门）⇒ 本域只需在收口回归时复核。
3. **A2 客户端熔断缺双侧活体**：like-api 真实 channel 上拦截器是否真在链中，未跑 ⇒ **「Bean 存在」≠「机制生效」**（§3.3.3）。
4. 🆕 **A9 的 gRPC 路径端到端缺 RAG 侧真链证据**：域侧属性写法已落码且守门绿（域内契约级），但**真链上**「server span 携带 `query.*`/`cache.*`/`like.*`」这条**端到端证据要等 FB-04 落码后才拿得到**（探针已证：handler 期 `Span.current()` = invalid）⇒ **收口窗口**补一条 E2E（真 server + 真链 ⇒ 断言导出的 server span 带属性）。
   - 🟢 **2026-10-27 更新**：FB-04 **已落码**（`853767b` · 我域已独立复核，见 **§4·六**）⇒ 现在**只差「合入本树 + 补 E2E」**两步 ⇒ **E2E 前置已满足** ⇒ 合并后即可跑。
   - 🟢 **2026-10-27 11:59 G4 回执确认**：「你域侧 `29cd55d` 那半**可以接上了**，**收口窗口补 E2E**」⇒ ⇒ **两侧对「E2E 前置已满足」无异议** ⇒ ⇒ **本条只等合并动作**（**合并不在本 lane 写权内** ⇒ 由整合者执行）⇒ ⇒ 合并后第一条要跑的就是它。
   - ⚠️ **合并前我域 A9 属性的实际效果 = 零**（属性落在 invalid span 上 ⇒ 静默丢弃）⇒ **A9 轴在合并前不得判为已闭环**。
5. 🆕 **A9 的 Go 侧活体未跑**：Go 侧属性清单目前是 **L3**（源码逐条对照 + go-zero `tracinginterceptor.go` 取证）⇒ 未与 RAG 侧做「同一属性名 + 同一取值」的**双侧活体对拍** ⇒ A9 轴的最终结案仍需该对拍（AGENTS.md §3.3.3：无双侧活体不得写 L4）。
4. **A9 缺双侧活体**：Go 侧 7 处 span 属性 vs RAG 侧 0 处 —— 差异已由**源码逐条对照**坐实（L1），但**未跑双侧探针** ⇒ 落码后须补一条两侧 span 属性对拍探针。
5. **`like_promotion_topic` 生产与消费同 topic**（`LikeKafkaTopics.java:36` + `application.yml:217`）是 Go 现状而非笔误（`LikeKafkaTopics.java:8-16` 已注释）⇒ 灰度期两侧同 topic 消费的字段一致性靠 `LikeEventContracts.java:57`（`@JsonPropertyOrder`）保障，**未经双侧活体对拍**。
7. ✅ **启动守门 flake 的修复**（`LikeRpcRuntimeBootGateTest` · G3 `8b2c6e9` 同款）—— **已落 + 已回归 + 已变异验证**（2026-10-27）：<br>· **落码**：`StringBuilder` → **`StringBuffer`** ＋ **收摊前等日志标志行**（30s / 200ms）＋ **`drain.join(10s)`** ＋ 标志串提为常量 `GRPC_STARTED_MARKER`（消除「等待与断言两处字面量漂移」）<br>· **回归**：**like-rpc 111 / task-rpc 115**（`0 failures / 0 errors / 0 skipped`）· `BUILD SUCCESS`；目标类 **Tests run: 1, Skipped: 0**（4.641 s）⇒ **真跑没被 skip** ✔<br>· **变异**：标志串改假串（只改一处）⇒ **红在目标断言** `:155`（36.11 s ≈ 等满 30s 后失败，且失败消息带真实日志尾部 ⇒ 证明进程确实起来了）⇒ 改回后复跑 **1 条绿**（5.784 s）✔<br>· **我域同款排查**：全域 `grep -rn "destroyForcibly" service/{like,task,message}` = **1 命中** ⇒ 同款**仅此一处**（`TaskRpcFatJarArtifactGateIT` 是产物层、message-rpc 无此类）<br>⚠️ **仍剩**：**重复跑 N 次全绿**的统计样本偏小（本轮该类共跑 2 次）⇒ 收口回归时再补几轮 ⇒ 详见 [`like/rpc.md`](rpc.md) A 节。
8. 🆕 **两条「无人拥有」的 U+FFFD 落在 `tools/**`**（G3 扫出并建议修，我域**不越权改**）—— `tools/errmsg-probe/README.md:59`（`未登记码的回U+FFFD×3结果`）+ `tools/sql-statement-probe/favorite/compare.py:561`（`两侧都U+FFFD×3 / 两侧都没有`）⇒ **`tools/**` 不在我 G2 写权面** ⇒ **转派 G4**（若 `tools/**` 归 G4 写权面则请修；否则请指定 owner）⇒ **修复前先字节级复核**（`read` 的输出不可当判据，见上）。<br>9. 🔴 **E65（= global U15）：本域有 1 处「跨域出向 gRPC」⇒ 出向不继承请求 deadline ⇒ 漂移成立**（**2026-10-27 独立核实 · G3 知会 + Jason 拍板方向**）<br>· **本域跨域出向清单（唯一一处）**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/client/MessageRpcClient.java`（**like-rpc → message-rpc**）⇒ ⚠️ **与 `like-api/LikeRpcClient` 区分**：那是**本域内** api→rpc（阶段 2 已落了 `min(cap, remaining)` 传递）⇒ **不属于 E65**<br>· **Go 侧事实（带 ctx ⇒ 继承 deadline）**：`service/like/rpc/internal/logic/likeactionlogic.go:380` ⇒ `l.svcCtx.MessageRpc.SendNotification(l.ctx, &messagepb.SendNotificationReq{…})` ⇒ ⇒ **Go 侧出向传了 ctx** ⇒ ⇒ **继承请求 deadline**<br>· **RAG 侧现状（零处传）**：`MessageRpcClient.java` 全文 **零** `withDeadline` / `applyTo` / `DeadlineState` / `GoStyleRequestDeadline` 命中 ⇒ ⇒ **出向调用不带任何 deadline** ⇒ ⇒ **E65 在本域成立**<br>· **🔴 Jason 拍板的方向（2026-10-27，G3 转达）= 走 global 统一载体 `GoStyleRequestDeadline.applyTo(stub)` ⇒ 本轮不改** ⇒ ⇒ 共享层那半落地后**本域照做**（已进本文件「合并后我域必做」）<br>· **被否决的备选**（**本域不得选边**）：用 `DeadlineState.remainingMillis()` 直接构造出向 stub deadline ⇒ 三条理由：① 引入**第二种载体**（仓内 `GoStyleRequestDeadline` 与 `DeadlineState` 互不相通 ⇒ 这正是 global 认定的病根）② 偏离 Go 的**单载体**语义（Go 侧只有 `ctx`）③ 要改 global 门禁期望值（`outgoing_carrier` 当前钉 `absent`）<br>· ⚠️ **G3 订正的一个「既有先例」不成立（我域已核 ⇒ 本域台账零处引用它 ⇒ 无需订正）**：原引「`users/admin/AdminDomainService.java:140` · `users/user/UserDomainService.java:145` 已用 `DeadlineState.isExpired()` ⇒ 仓内已有第二种载体的出向用法」⇒ ⇒ **实测不成立**：那两处是**本地超时检查**（`if (Thread.currentThread().isInterrupted() || DeadlineState.isExpired())`）⇒ **不是「用 `DeadlineState` 构造出向 gRPC deadline」**；另**行号已漂移**（实测命中 `AdminDomainService.java:178`）⇒ ⇒ **不得据它论证「仓内已有第二种载体的出向用法」**，引用前须重新定位<br>10. 🔴 **命令超时的守门判据偏弱（G1 探针发现 · G2 独立字节码核实 ⇒ 比 G1 再精化一层）**<br>· **G1 的探针读数（转述）**：`isTimeoutCommands()`=true · `isApplyConnectionTimeout()`=true · `getSource()`=`DefaultTimeoutSource` · `LettuceConnectionFactory.getTimeout()`=3000ms ⇒ ⇒ G1 判「`isTimeoutCommands()` 不是有效判据、应改断言 `isApplyConnectionTimeout()`」<br>· 🟢 **G2 独立核实（字节码级）**：`TimeoutOptions.enabled()` = `builder().timeoutCommands().connectionTimeout().build()` ⇒ ⇒ **两个位同时被打开** ⇒ ⇒ **在 Boot 4 默认装配下这两个断言单独都是恒真** ⇒ ⇒ ⚠️ **「改断言 `isApplyConnectionTimeout()`」并不能把弱判据变成强判据** ⇒ ⇒ **两条合起来才等于 `enabled()` 的全貌**；且 `DefaultTimeoutSource.getTimeout(...)` = **恒 -1** ⇒ ⇒ **`source` 给不出时长** ⇒ ⇒ 时长只能走 `applyConnectionTimeout` 分支从连接级 timeout 取（与 G1 读数一致）<br>· ⇒ ⇒ **完整判据链 = 四条合起来**：**A** `isTimeoutCommands()` ＋ **B** `isApplyConnectionTimeout()` ＋ **C** `getSource()` 恒 -1 ＋ **D** `RedisURI.timeout` = 3s（**本域守门 ③ 已断言**）⇒ ⇒ **任何单条都不充分**<br>· ✅ **域侧补强动作已落码（2026-10-27 12:3x · **已编译 + 已变异验证** · 证据见本节末）**：like-rpc 守门 ① 原先只有 **A** ⇒ ⇒ **已补 B** ⇒ ⇒ 注释必须写明「A+B 才等于 enabled() 全貌、单独恒真」⇒ ⇒ **不得**把它写成「时长会被施加」的判据<br>· ⚠️ **边界**：被测机制不在域侧写权面 ⇒ ⇒ **无法做「机制变异」** ⇒ ⇒ 这条是**回归网**，不是「已证伪该机制」；`LettuceConnectionFactory.getTimeout()` 这个出口 **G2 未独立复核**（本域已复核的是 `RedisURI.timeout`）<br>7. 🔴 **我的一条「转移单」是假转移（G4 独立核实并拦截 · G2 采信 · 2026-10-27 12:4x）** ⇒ **自我订正**：我曾据「Go 侧该域有 `rest.WithJwt` ⇒ 适用」就推断「article-api / comment-api / favorite-api 也欠修 009 式的『JWT 验签落在 HTTP 预算内』」，并准备请 G4 转发 ⇒ ⇒ **G4 已独立核实：那三域的 RAG 侧早已修好**（`GoStyleJwtAuthFilter` 的 `@Order(HIGHEST_PRECEDENCE + 6)` ⇒ 验签在预算起点**之前**）⇒ ⇒ **「Go 侧适用」≠「RAG 侧还欠修」** ⇒ ⇒ **我的推断把『适用』悄悄换成了『欠修』** ⇒ ⇒ **该转移单作废**（**我未发出**，已在 G3 队列侧核过 ⇒ 无需撤回；G4 的预防性拦截件已入 G3 队列）<br>⇒ ⇒ **教训（本域留档）**：**真源侧有该行为 ⇒ 只说明「适用」，不说明「RAG 侧还没修」** ⇒ ⇒ 判定「谁欠修」**必须同时读 RAG 侧的当前状态**（`@Order` / 现有实现）⇒ ⇒ **「适用」与「欠修」是两个独立判断，合并成一个就是假转移** ⇒ ⇒ **转派前必须先读被转域的现状**<br>⇒ ⇒ ⚠️ **另一处不精确（G4 指出）**：那三域**不是**都在 3s 预算上 —— article-api 是 **70s**、comment-api / favorite-api 是 3s ⇒ ⇒ **槽位相同（+6 在预算起点 +7 之前）≠ 预算时长相同** ⇒ ⇒ 引用时**槽位与时长要分两轴写**<br>⇒ ⇒ 🟡 **仍属我域的残留（G4 指出 · 由整合者处理）**：那三个拦截器都保留「Filter attribute 缺失时就地验签」的 inline fallback ⇒ ⇒ 那条路径把验签放回预算内 ⇒ **我域无同类实现**（我域 task-api 无鉴权 ⇒ `DRIFT-USERAPI-009②` 对我域**不适用**）⇒ ⇒ **不属我域待办**<br>8. 🟢 **U+FFFD 全清单口径订正（G4 独立实测 · 两份清单交叉核对通过 · 2026-10-27 12:4x）** ⇒ **全仓 = 18 个文件 / 47 处**（G4 于 12:16 的字节级实测）⇒ ⇒ **我上轮给的清单已 stale**（其中「G4 写权面的 2 条」已由 G4 在 `0c72969` 修掉 ⇒ 不要重复登记）⇒ ⇒ **两份独立清单数字吻合** ⇒ ⇒ **「无人拥有」实为 4 条**：`tools/errmsg-probe/README.md`(3) · `tools/sql-statement-probe/favorite/compare.py`(3) · `docs/Pitfalls/message/rpc.md`(2) · `docs/Pitfalls/users/user/rpc.md`(1)<br>⇒ ⇒ **其中 `docs/Pitfalls/message/rpc.md` 的 2 处内容属我域（message 域）** ⇒ ⇒ **我认领其内容归属**，但 **`docs/Pitfalls/**` 不在我 G2 写权白名单** ⇒ ⇒ **我域不能写** ⇒ ⇒ **已请整合者/该文件 owner 处理**（G4 已把 4 条统一登记在其台账 4.10.7 item 6）⇒ ⇒ **这是「路径归属 ≠ 内容归属」的典型**：内容是我的、路径不是 ⇒ ⇒ **别把「不在我写权面」读成「不是我的问题」**<br>🟢 **「机制位②」断言已落码 + 已编译 + 已变异验证（2026-10-27 12:5x）** ⇒ **上一行「等待编译与变异验证」的状态已销账**：<br>· **落码**：`ab0d782` ⇒ task-rpc 与 like-rpc 的 Redis 超时守门 ① 各补一条 `isApplyConnectionTimeout()` 断言（断言消息写明「只覆盖第二位 · 与 ① 合起来才等于 `enabled()` 全貌 · **单条恒真** · **不是**『时长会被施加』的判据 · 时长由 ② 断言」）<br>· **编译**：✅ 通过（`mvn -o -pl … -am -DskipTests install`）<br>· **守门跑绿**：task-rpc **6 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS** · like-rpc **6 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS**<br>· **变异验证（断言方向变异）**：把新断言的 `isTrue()` 改成 `isFalse()` ⇒ ⇒ **两域各红 1 条**，且**红在目标断言** `commandTimeoutMechanismIsEnabled`（task-rpc `TaskRedisCommandTimeoutGateTest` · like-rpc `LikeRpcRedisTimeoutsGateTest`）⇒ ⇒ **恰好 1 条红**（另一条阴性对照仍绿）⇒ ⇒ **非假绿** ⇒ ⇒ 还原后复跑**两域 6/0/0/0 绿**⇒ ⇒ **sha256 与提交版逐字节一致**（还原无残留）<br>· ⚠️ **诚实的边界（不变）**：被测机制（Boot 的 `TimeoutOptions.enabled()` 接线）**不在域侧写权面** ⇒ ⇒ **无法做「机制变异」** ⇒ ⇒ 本条是**回归网**，**不是**「我已证伪过该机制」⇒ ⇒ 上面那次变异证明的是「断言在读运行期值」<br>· ⚠️ **判据自身的一条踩坑**（记下来免得下次重踩）：第一次变异我用 `grep -E "…|expected|but was"` 抓输出 ⇒ ⇒ **被 Kafka 的 `sasl.oauthbearer.expected.audience` 配置行占满 `head` 配额** ⇒ ⇒ **「红没红」根本没被看到**（既不能算红、也不能算绿）⇒ ⇒ 改成只抓 `Tests run:` / `<<< FAILURE` / `BUILD` 后才拿到真相 ⇒ ⇒ **抓取模式本身也会骗人 ⇒ 变异验证的判据必须精确到「失败计数」与「失败用例名」**<br>🟢 **G4 两条断言 · G2 独立复核通过（2026-10-27 12:5x）**：① **我域三域 production 代码对 Redis 客户端零覆盖** —— `ClientOptions` / `LettuceClientOptions` / `disconnectedBehavior` / `DisconnectedBehavior` 在 `service/{like,message,task}` 的 **`src/main` 全部零命中**（排除 `src/test`）⇒ ⇒ **共享层的 `REJECT_COMMANDS` 与breaker 键改法在我域无任何生产侧覆盖 ⇒ 零影响**（G4 的 [1] 登记成立）② **三处 mode 值**：`like-api/application.yml:125` = `envelope` · `message-api/application.yml:152` = `envelope` · `task-api/application.yml:225` = `"off"`（**带引号**）⇒ ⇒ 逐条与共享层语义表一致（`GoStyleFormLimitMode` like=ENVELOPE / message=ENVELOPE / task=OFF）⇒ ⇒ **task 的引号必须保留**（未加引号的 `off` ≠ 省略该属性；favorite 才是「省略整块 ⇒ OFF」） |

### 🟢 **8 模块域级回归 · 完整数字**（2026-10-27 13:00:44 → 13:07:38 · 独占跑 · `MVN_EXIT=0` · `BUILD SUCCESS` · 6:47 min）

**本轮首次拿到 G2 三域 8 模块的完整数字**（此前只有 message-rpc 独占 84 条与两个守门各 6 条）：

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/message/message-api` | 98 | 0 | 0 | 0 |
| `service/message/message-rpc` | 84 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 89 | 0 | 0 | 0 |
| `service/like/like-rpc` | 113 | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 97 | 0 | 0 | 0 |
| `service/task/task-rpc` | 117 | 0 | 0 | 0 |
| **合计** | **637** | **0** | **0** | **0** |

⚠️ **口径纪律（三条，随数字一起留档）**：
1. **权威口径 = 本轮 mtime 切的 surefire `TEST-*.xml` 逐文件求和**（起跑标记 epoch `1793077244`）⇒ **不用**「按 `[INFO] Building <module>` 分段聚合」⇒ ⇒ **637 与上一轮「八模块 637」口径一致**（上一轮 message-rpc 有 2 errors ⇒ 本轮同一批用例**由红转绿** ⇒ 归因 = 上一轮是**端口撞车**（64118 落在 OS 临时段）⇒ **不是代码缺陷**）
2. 🔴 **排掉 1 份孤儿报告**：`service/like/like-rpc/target/surefire-reports/TEST-com.rag.like.like.rpc.LikeRpcServerSpanCurrentProbeTest.xml`（mtime 早于起跑标记）⇒ **源文件已不在树里**（`find service/like/like-rpc/src/test -name '*Probe*.java'` **零命中** ⇒ 该探针类**从未进过 git** ⇒ 是「建在模块里的本地探针、源删了报告没删」）⇒ ⇒ **like-rpc 报告 20 份 / 测试类 19 个 ⇒ 差 1 = 这份孤儿** ⇒ ⇒ **本域不会回退任何测试来消它** ⇒ ⇒ **教训（复用 G1 同款）**：**探针要么放 `/tmp`，要么连报告一起删** ⇒ ⇒ **「两口径交叉验」遇到差值先查「哪个口径读的是残留」**
3. ⚠️ **独占跑**：起跑前 `pgrep -f 'plexus.classworlds.launcher.Launcher'` = **0**（G1 的 6 模块回归 12:52:27 起、约 13:0x 收工 ⇒ 我 13:00:44 才起）⇒ ⇒ **零端口撞车** ⇒ ⇒ **本轮 637 全绿不含任何环境噪声**（对比：上一轮同口径因撞车红 2 条）

⇒ ⇒ **本轮数字的用途边界**：这是**我域当前代码态**的回归网 ⇒ ⇒ **不是**「合并 G4 共享层后」的结论 ⇒ ⇒ **合并后必须重跑**（§「合并后我域必做」第 1/2/4 条）⇒ ⇒ **本轮数字不升级任何轴的证据强度**（跑绿 ≠ 对）

⇒ ⇒ 🟡 **本域相关的一条残留**：`MqConsumer` 现在是 like-rpc 的**生产依赖**（见上节 ③）⇒ ⇒ **合并 G4 共享层后，本域 MQ 相关用例必须重跑**（`LikeSpansAttributeGateTest` ＋ `LikeUpdateKafkaListener` 相关用例）⇒ ⇒ **本轮 113 条里已包含这些用例 ⇒ 合并后若它们变红 ⇒ 是共享层 `MqConsumer` 改动 ⇒ 属新回归 ⇒ 直接找 G4**

### 🔴 第四轮（2026-10-27 13:2x~13:4x）· 合并 G4 共享层 + **FB-03 域侧守门升级** ⇒ 本域 A2 轴 **L1 ⇒ L2**

#### ① 合并（G4 要求的协议 · 整支合并、**不 cherry-pick**）

- `git merge align/g4-service-common` ⇒ **merge commit `37816ca`** ⇒ **零冲突**（合并前已用 `git merge-tree --write-tree` 预演：`main←G2` / `main←G4` / **`G4←G2`** 三者 EXIT=0）
- ⇒ ⇒ **天然满足 §5.3e 的合并顺序硬约束**：`3b573fb` 与 `7488b3d` **同批到位**（`git merge-base --is-ancestor` 逐个核对 = YES）⇒ ⇒ **不会**出现「池终止即永久 ⇒ 由绿转红」
- ⇒ 带入共享层 API `compose(props)` / `layerNames(chain)`（G4 `182fc32`）⇒ ⇒ **FB-03 的域侧升级条件满足**
- ⚠️ **口径纪律（合并后必须改口径）**：合并后**本分支相对主干的 diff 里含 `service/common/**`** ⇒ 但那些 commit 的**作者是 G4** ⇒ ⇒ **本 lane 没有写共享层**（写权仍归 G4）⇒ ⇒ 「共享面零触碰」改述为「**本 lane 未新增、未修改任何共享层 commit**」

#### ② 域侧守门升级（FB-03）—— 本域 `LikeProtectionChainWiringTest`：**2 条 ⇒ 4 条**

- **新增 ①**「链的层名列表 == `[recover, breaker, shedding]`」**且**「**实际装上的链 == 配置推出的构成**」（`layerNames(chain)` == `compose(props).layerNames()`）
  ⇒ ⇒ 一次同时钉住「有 breaker」「有 shedding」「层序 = recover→breaker→shedding」（Go `zrpc/server.go:144-167`）
  ⇒ ⇒ 「实际链 == 配置推出的构成」抓住「**配置说三层、实际只装两层**」这类装配与配置脱节 ⇒ ⇒ **旧判据完全覆盖不到**
- **新增 ②**「关掉降载 ⇒ 层名列表变成 `[recover, breaker]`」⇒ ⇒ 证明**降载层是条件装的**（Go `zrpc/server.go:160-163`）⇒ ⇒ **不必再「关掉降载来换取确定性」**
- ⇒ ⇒ **旧的「包装层归属」那条保留不删** ⇒ ⇒ **互补而非替代**（层名证「有哪些层 + 层序」；包装归属证「真的包住了调用」）
- ⇒ ⇒ **客户端熔断那一面**（`GoStyleGrpcClientBreakerInterceptor` · `LikeApiApplication.java:75-77` 扫 `com.rag.common` 注册）**已由阶段 2 的 `LikeApiGrpcClientBreakerE2ETest` 覆盖**（真 in-process gRPC server ⇒ 熔断打开后调用不再触达下游）⇒ ⇒ **本轴两侧（服务端链 + 客户端熔断）均有运行期证据**
- ⇒ ⇒ **本域 A2 轴：L1（源码）⇒ L2（运行期 · 守门 + 变异验证）**

#### ③ 变异验证（判据非假绿）

- 变异方式 = **断言方向变异**（期望层名改成 `["recover","breaker"]`）⇒ ⚠️ **被测机制在共享层、不在本 lane 写权** ⇒ ⇒ 只能做方向变异（证明**断言读的是运行期真值**）⇒ ⇒ **这是「回归网」证据，不是「已证伪该机制」**
- 结果：**本域红且只红 1 条**，红点**精确**在 `chainLayerNamesMatchComposedChain`（`LikeProtectionChainWiringTest.java:132`）⇒ ⇒ **其余 3 条阴性对照仍绿** ⇒ ⇒ **非假绿**
- ⇒ 还原后复跑：**4 条绿** · 0 failures / 0 errors / 0 skipped · BUILD SUCCESS
- ⚠️ **还原脚本的判据坑（我自己踩到）**：变异后文件里有 **2 处** `containsExactly("recover", "breaker")` ⇒ ⇒ 全局字面量替换的还原脚本会**断言失败并中止**（文件停在变异态）⇒ ⇒ **还原必须按位置精确替换**

#### ④ 数字订正

- **like-rpc**：`mvn test` 口径 **111 ⇒ 113**（守门 +2 条）⇒ ⚠️ **第三轮记录的 111 保留在下方订正行**（原口径 = `mvn test`）
- ⇒ 🟢 **同类 stale 会被 `check-ledger-selfcheck.py` 层 3 抓到**（默认只查 task 域 ⇒ **like / message 域的数字要自己盯** ⇒ 这是「守门覆盖面 ≠ 全域」的已知缺口）
