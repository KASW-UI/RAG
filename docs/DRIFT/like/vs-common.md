<!-- @new 2026-10-27 · `like-common` lane 台账 · **G2 波次工作树产出**（`align/g2-message-like-task`）· 基线 = `do-my-own-gh` @ `a282012` · **阶段 1 只读对账完成（2026-10-27）** -->

# `like-common` lane 台账 —— 「like ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（`service/like/like-common`；⚠️ **本域没有 `common.md` 台账** ⇒ 本域的 common 相关结论就落在本文件）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)
> ⇒ **本文件由 `G2` 波次工作树独占**；**共享面一律不写**（合并后由整合者统一更新）

> 📌 **术语订正（2026-10-28 · G2 自修 · 依 G1 通报）**：「**整合者**」是**已废称**
> —— `docs/Worktree.md` §1e 明写「『整合者』不是一个存在的角色」⇒ ⇒ 它会诱导 lane 去找一个**不存在的收件人**。
> ⇒ **现行角色词 = 「合并收口执行者」**（`docs/Worktree.md` §1e 表：它是一个**步骤**，不是一个常驻会话 ⇒ 没有任何会话长期占着这个位置）。
> ⇒ ⇒ **本文件正文的「整合者」一律按「合并收口执行者」理解**；下方**历史条目里的原值保留不改写**（留档纪律）。

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
| ✅ `DRIFT-LIKEAPI-015` | `Content-Type` **大小写**判据 | `service/common`（`GoStyleJsonBodyConverter`） | ✅ **已修**（**旧快照「未修」过期**）—— 共享层 T-1（2026-10-25）已落码 | `GoStyleJsonBodyConverter.java:331`（`getFirst(CONTENT_TYPE)` = **原始头值**）→ `:334` → `:241-245`（`contentType.contains("application/json")` = **大小写敏感**）；Go `go-zero@v1.10.2/rest/httpx/requests.go:153-154`（`strings.Contains(r.Header.Get(...))`，**同样大小写敏感**） | [`api.md`](api.md) |
| `DRIFT-LIKEAPI-017` | 单次 RPC 超时：Go 固定 2000ms | ⚠️ **本域** `service/like/like-api/**`（**旧列写 `service/common` = 错**） | ✅ **已修**（**旧快照「未修」过期**；第五轮已改判为域内可修） | `LikeRpcClient.java:135-146`（`min(capMillis, remainingMillis)`）+ `LikeApiProperties.java:125`（默认 2000ms）+ 守门 `LikeRpcTimeoutCapTest.java:82`；Go `go-zero@v1.10.2/zrpc/config.go:31`<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**不改判**；但**本行的证据行号已全部偏移，本轮已订正**）。依据：RAG `service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:151`（`capMillis = properties.getLikeRpc().getTimeout().toMillis()` ⇒ 固定上限取自配置）· RAG `service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:157`（`effectiveMillis = (remainingMillis < 0L) ? capMillis : Math.min(capMillis, remainingMillis)`）· RAG `service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:160`（`Math.max(1L, effectiveMillis * 1_000_000L)` ⇒ 1ns 下限）· RAG `service/like/like-api/src/main/java/com/rag/like/like/api/config/LikeApiProperties.java:214`（`private Duration timeout = Duration.ofMillis(2000);`）· 守门 RAG `service/like/like-api/src/test/java/com/rag/like/like/api/LikeRpcTimeoutCapTest.java:76`（类）+ `:181`/`:204`/`:222`/`:246`/`:266`/`:286`（6 条 `@Test`）· Go `go-zero@v1.10.2/zrpc/config.go:31`（`Timeout int64 json:",default=2000"` ⇒ Go 固定 2000ms）· 核对日期 2026-10-30。<br>🔴 **本行原证据的行号全部过期**：`LikeRpcClient.java:135-146`（真实逻辑在 `:151-160`）· `LikeApiProperties.java:125`（真实在 `:214`）· `LikeRpcTimeoutCapTest.java:82`（类在 `:76`，6 条 `@Test` 在 `:181`-`:286`）。 | [`api.md`](api.md) |
| `DRIFT-LIKE-006` | 模块从未做 Spring 上下文加载测试 | 域侧测试 | ✅ **已闭合**（**旧快照「部分过期」应更新为已闭合**） | `LikeRpcSpringContextWiringTest.java:64-84`（`@SpringBootTest(classes=LikeRpcApplication.class)` + 真 PG Testcontainers `:87-91` + `@DynamicPropertySource` `:93-98`）⇒ **真能触发上下文加载** | [`rpc.md`](rpc.md) <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/ddl-parity.sh` ⇒ **EXIT=2**（2026-10-30 03:34:15 · 2s）—— 🟡 **探针判定为「环境问题」**（结论不可用）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| `DRIFT-LIKE-013`~`016` | 「服务起不来」族 | 域侧（各条落点各异，均在本域） | ✅ **全部已修**（**旧快照过期**） | 013 `application.yml:183` `discovery:///message-rpc`（三斜杠）+ 守门 `LikeRpcProductionAddressTest.java:104-114`；014 `config/MybatisPlusOrderingBridgeAutoConfiguration.java` + `AutoConfiguration.imports`；015 `like-rpc/pom.xml:156-159` `spring-boot-kafka`；016 `config/Jackson2ObjectMapperConfiguration.java` + `LikeRpcApplication.java:98`<br>⚠️ **2026-10-30 真实性复核（message+like lane）· 清单外顺手订正**：本行 **013 的 `application.yml:183` 行号已失效**（该 yml 在 2026-10-27 后有 3 次提交 ⇒ `:183` 现在落在一行 Kafka 注释上）⇒ 真实位置为 RAG `service/like/like-rpc/src/main/resources/application.yml:227`（`address: discovery:///message-rpc`），形态说明注释在 `:213-225`；守门 RAG `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcProductionAddressTest.java:104-114` + `:94` 仍在位。⇒ **013 的结论仍成立（✅ 已修），失效的只是行号指针** —— 详见 [`rpc.md`](rpc.md) 的 `DRIFT-LIKE-013` 段。014/015/016 三条本轮亦逐条复核，结论**均不变**（落点已迁共享层 / `like-rpc/pom.xml:158` / `Jackson2ObjectMapperConfiguration.java:88,91` + `LikeRpcApplication.java:98`）· 核对日期 2026-10-30 | [`rpc.md`](rpc.md) |
| ✅ `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（`CD-6`〔旧号 `X-6`〕已落码 6 类） | ✅ **已落地**（本域只确认 `mode` 与 Go 同向） | 共享层 6 类在位；本域消费点 `application.yml:125` `mode: envelope`；Go `service/like/api/likecenter.go:48` 注册 `httpx.SetErrorHandler` ⇒ **同向** ✅ | [`api.md`](api.md) |

⇒ **§1 五条 ⇒ 新派 G4 反馈单 = 0 条**（4 条已修/已闭合 + 1 条已落地）。
⇒ **跨域待办关闭**：`DRIFT-MSG-V-029`（`like-rpc` 缺 `baseline-version: 0`）⇒ **已修**（`like-rpc/application.yml:78` `baseline-version: 0` + `:81` 独立账本表名 + 守门 `LikeFlywaySharedSchemaGateTest.java:159-163`）⇒ message 域台账里「🟡 留给 like 域」的挂账 **可以关掉**。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**2026-10-27 核对**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | api：`LikeRpcClient.java:133-147`（`min(2000ms, 剩余)`）+ `LikeApiProperties.java:125`（默认 2000ms）· 消费点 `HttpMiddleware.java:304`（`begin()`）/`:336`（`clear()`）· 🔴 **2026-10-27 新增**：`LikeJwtAuthFilter`（+6）—— **3s 预算的作用域**（验签在预算之外，= `DRIFT-LIKEAPI-023`）<br>rpc：`LikeRpcApplication.java:90`（`@Import`）+ `:137`（`server.intercept(...)`）+ `application.yml:299` `rpc-timeout: 2s` · **Redis 超时**：`like-rpc/application.yml` `spring.data.redis.timeout: 3s` ＋ `connect-timeout: 5s` → 消费点 `RedisURI.timeout`（`CommandExpiryWriter:160`）＋ `ClientOptions.socketOptions.connectTimeout`（Boot `LettuceConnectionConfiguration:212-215`）· **Redis 连接池**：`like-rpc/application.yml` `spring.data.redis.lettuce.pool.{enabled,max-active,max-idle,min-idle}` ＋ `like-rpc/pom.xml` 的 `commons-pool2` → 消费点 Boot 4 `LettuceConnectionConfiguration:267-271`（`getPoolConfig` ⇒ `:269 setMaxTotal` / `:270 setMaxIdle` / `:271 setMinIdle`）＋ `DataRedisConnectionConfiguration:195-198`（`isPoolEnabled`） | ✅ 一致（Go `go-zero@v1.10.2/zrpc/config.go:31` 客户端 2000 / `:46` 服务端 2000 / `go-zero@v1.10.2/rest/config.go:55` HTTP 3000 ⇒ 有效值 = `min(2s, 3s−已耗)`，RAG 同式）<br>🔴 2026-10-27：**3s 预算的作用域也已对齐**（Go 的预算**不含**验签 —— 真源 `observability/http.go:30-34` 关掉原生 TimeoutHandler、改由 `server.Use` 的自建中间件计时 ⇒ 计时器在 `Authorize` **之后**起算；RAG 侧原把验签放在 `HandlerInterceptor`（预算内）⇒ 现已搬到 `LikeJwtAuthFilter`(+6) ⇒ **验签在预算外**，两侧一致）🆕 **2026-10-27（下一轮 · 池轴）**：**Redis 连接池数值轴已修** —— `min-idle: 4` ⇒ **`8`**（= Go 的 `MinIdleConns`：真源走 **go-zero 的 redis** ⇒ `redisclientmanager.go:15` `idleConns = 8` ⇒ **精确对齐**）＋ 新增守门 **⑤⑥**（`LikeRpcRedisTimeoutsGateTest` 4 ⇒ **6 条**）⇒ ⇒ 详见 [`like/rpc.md` 的 `DRIFT-LIKE-RPC-REDIS-POOL`](rpc.md)（残留 = `max-active` 性能面） | L4（Go 腿活体探针：观测点 A `deadline_set=false` / B `deadline_set=true remaining=3s`；**非法 token ⇒ 401 且三个观测点一个都没打印** ⇒ Authorize 在所有 user middleware 之前；Redis 超时轴 = **双侧活体对拍** L4）<br>🔴 2026-10-27（G2 第二轮）：**Redis 连接超时原先未配** ⇒ 落 Lettuce 默认 **10s**（Go 是 go-redis v9.20.0 库默认 **5s**）⇒ **已修**（补 `connect-timeout: 5s`；命令超时 `3s` 本就对齐 ✓）⇒ 详见 [DRIFT-LIKE-RPC-REDIS-TIMEOUT](rpc.md#🔴-drift-like-rpc-redis-timeout--redis-连接超时未配--落-lettuce-默认-10sgo-是-5s) | 2026-10-27 |
| **A2** | gRPC 拦截器链 | 服务端：`LikeRpcApplication.java:137` → `GoStyleRpcProtectionChain.java:61-78`（recover→breaker→shedding→business）<br>客户端熔断：`GoStyleGrpcClientBreakerInterceptor.java:103-105`（`@Component` + `@GrpcGlobalClientInterceptor`）⇒ 被 `LikeApiApplication.java:75-78`（扫 `com.rag.common`）注册<br>🟡 **域侧 javadoc 假断言**：`LikeRpcClient.java:43-54` 仍写「Java 侧尚未接入」 | 🟡 **OBS**（链序 ✅；🟡 **域侧 javadoc 与事实相反** ⇒ 域侧自修 D-1；🟡 **like-api 无客户端熔断 E2E 守门** ⇒ 其余域都有 ⇒ 域侧补 D-1）<br>🆕 **2026-10-27 第四轮：A2 轴由 L1 升为 L2（运行期 · 守门 + 变异验证）** —— 合并 `align/g4-service-common`（merge commit `37816ca` · G4 要求的整支合并协议）后，共享层的 `compose(props)` / `layerNames(chain)` 已在共享层 ⇒ ⇒ **`LikeProtectionChainWiringTest` 新增 2 条原生判据**（① 层名列表 == `[recover, breaker, shedding]` ＋ **实际链 == 配置推出的构成** ② 关掉降载 ⇒ 层名列表变成 `[recover, breaker]`）⇒ ⇒ **旧的「包装层归属」那条保留不删**（互补而非替代：层名证「有哪些层 + 层序」，包装归属证「真的包住了调用」）⇒ ⇒ **本域客户端熔断那一面**（`GoStyleGrpcClientBreakerInterceptor` · `LikeApiApplication.java:75-77` 扫 `com.rag.common` 注册）**已由阶段 2 的 `LikeApiGrpcClientBreakerE2ETest` 覆盖**（真 in-process gRPC server ⇒ 熔断打开后调用不再触达下游）⇒ ⇒ **本轴两侧均有运行期证据** | **L2（运行期）** | 2026-10-27 |
| **A3** | HTTP 保护链 | 四个 Filter：`GoStyleProtectionConfiguration.java:55-57` ⇒ like-api 扫 `com.rag.common` ⇒ 扫到即生效（`LikeApiProtectionMvcTest.java:96-103` 断言 4 个 Filter Bean 存在）· 域侧配置 `application.yml:126-140` · form-limit `application.yml:122-125` `mode: envelope` + 消费点 `GoStyleFormValueLimitInterceptor.java:90-93` + `GoStyleTomcatParameterLimitCustomizer.java:105-118` | 🟡 **OBS**（接线齐备、`mode=envelope` 与 Go `likecenter.go:48` **同向** ✅；🟡 **路径归一** ⇒ 🟢 **共享层已落码**（FB-01 · G4 `182fc32`）**已合入本树** ⇒ ✅ **本域已跑真 Tomcat 探针**（第十轮 · A/B 对照组 + 本域独立变异验证）⇒ ⇒ **「路径归一」这一格 = RAG 腿 L4**；🟢 **本域真实请求证据已补**（第十轮））<br>🟢 **2026-10-27 G4 FB-05 共享层落码（`parse` 精确接受 `"false"` 作为 OFF 的**唯一**别名 + 5 条守门）⇒ 对本域零影响**：本域的值是 **`envelope`**、**不是 `off`** ⇒ 走的是**原分支** ⇒ **零行为变化** ⇒ 合并后复跑本域 form 相关守门即可（预计全绿 ⇒ **「预计」不算数 ⇒ 合并后实测**）<br>> 📌 **共享层落点（按符号记，不钉行号 —— G4 树 `182fc32` 的行号合并后会位移）**：FB-01 = `GoStylePathNormalizingFilter` ＋ `GoStylePathNormalizingAutoConfiguration`（唯一注册点）；FB-02 = `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` / `REGISTERED_DEVIATIONS`；FB-03 = `GoStyleRpcProtectionChain.Layered` / `Composition` / `compose(props)` / `layerNames(chain)` ⇒ 共享层侧回归（G4 自测）：**52 模块 / 2442 条 / 0 failures / 0 errors / 4 skipped** / 21:59 min<br>🆕 **2026-10-27 订正（G4 第七次全量 · `5564c27`）**：**2442** 是**第六次**全量的数字 ⇒ **第七次 = 2457 条**（52 模块 / 0 failures / 0 errors / **4 skipped** / **20:28 min** / `MVN_EXIT=0`）⇒ ⚠️ **2457 是 G4 树（52 模块）的口径**，**不是本域数字** ⇒ 本域三域六模块 = **594 条**（`mvn test` 口径）
⇒ ⚠️ **合并后**本域需跑真 Tomcat 探针复核（路径归一）⇒ 那是**收口回归**窗口的事（§5.3e）

🟢 **共享层的 form 语义表已落码**（FB-02 · G4 `182fc32`）⇒ **like 行 = `Mode.ENVELOPE`** ⇒ 与本域 `mode: envelope` **一致** ⇒ 本域**不在** `REGISTERED_DEVIATIONS` 里 ⇒ **真对齐**） | L1 | 2026-10-27 |
| **A4** | 错误码 / envelope | `GlobalResponseAdvice.java:59-72`（包一层 + `body==null \|\| instanceof Response` 双守卫）· `GlobalExceptionHandler.java:106-110`（CodeError→200）`:229-236`（兜底 5001）`:86-90`（401 空 body）· `GoStyleRouterErrorResponses.java:121-140`（404 nosniff / 405 Allow）· rpc `LikeServiceGrpcImpl.java:108`（`onError` 不抛）<br>🔴 **阶段 2 新发现**：**「下游 gRPC status → 业务码」这一层两侧不同**（详见 §3 **D-7**）| 🟡 **OBS**（envelope 形状 + handler 接线 ✅ 与 Go `likecenter.go:41-47` SetOkHandler + `:48-61` SetErrorHandler 同形；**但 status → 业务码这层不同** ⇒ 见 D-7）| L1（源码逐条） | 2026-10-27 |
| **A5** | 端口治理 | 生产 `application.yml:33` `18887` · rpc `application.yml:164` `8082`（= Go `etc/likecenter.yaml:2-3` / `etc/like.yaml:2`）<br>✅ **阶段 2 已治理**：测试 **2 处** `RANDOM_PORT`（`LikeApiRouterErrorRealTomcatTest.java:54` + 本波新增的 `LikeApiGrpcClientBreakerE2ETest.java:78`）⇒ **`DEFINED_PORT`** + 本域专用段 **21600–21699**（`service/like/like-api/src/test/java/com/rag/like/like/api/support/RealTomcatPort.java` · favorite 同款复制）<br>每个测试类另加 **1 条端口段守门** `bindsToDedicatedPortSegment`（断言 `@LocalServerPort` 落在 `FIRST`–`LAST`） | ✅ **一致**（生产端口一致；测试端口已脱离 OS 临时段 49152–65535 ⇒ 不再与 Docker 发布端口同段）<br>🟡 **选段表归 `global` 协调**（本段只在本域登记） | L3（端口段断言 = 运行时证据） | 2026-10-27 |
| **A6** | errmsg 契约 | `LikeErrorCode.java:18-97`（29 码）· `LikeErrorMessages.java:37-88`（29 条文案 + 未知码回退 "FAIL"）· `LikeGrpcError.java:110-115` | ✅ 一致（Go `service/like/common/errmsg/errmsg.go:37-67` / `:69-99` / `:101-107` / `:33-35` 逐条同）<br>🟡 trailer 迁移 = **已签字偏离** `SD-2026-10-23-06`（框架限制）⇒ **勿当回归** | L1 | 2026-10-27 |
| **A7** | proto 契约 | `service/like/like-proto/src/main/proto/like.proto` · 消费点 `LikeServiceGrpcImpl.java:48` | ✅ 一致（47 条 message 行 / 16 条 enum 行两侧同；`package` 归一 = **已签字偏离** `SD-2026-10-23-05`） | L1 | 2026-10-27 |
| **A8** | DDL / 账本 | `application.yml:78` **`baseline-version: 0`** · `:81` `table: flyway_schema_history_like` · `:56-57` `enabled`/`baseline-on-migrate` · DDL `V1__create_like_record.sql:22-24`（TIMESTAMPTZ）· `V4__create_like_domain_fact_outbox.sql:32`（`DEFAULT CURRENT_TIMESTAMP`） | ✅ 一致（账本表名独立 ✅、baseline 钉 0 ✅、时间列全 TIMESTAMPTZ ✅；`DEFAULT CURRENT_TIMESTAMP` 1 处与 Go tag 同源 ⇒ **不是**残留）<br>✅ 账本隔离：`check-flyway-ledger.py` **EXIT=0** | L1 / L3 | 2026-10-27 |
| **A9** | trace / otel | `Traceparent`：`GoStyleTraceparentFilter.java:188-191` ⇒ like-api 扫到即生效 · 全局 provider `AutoConfiguration.imports:1` → `GoStyleOtelAutoConfiguration.java:58-61` → `GoStyleOtelAgent.java:187-193`（endpoint 非空才装 batcher）· `application.yml:278-286`（`otel.exporter.otlp.endpoint=jaeger:4317`）<br>🟢 **域侧 span 属性：阶段 2 已补齐**（新增 `LikeSpans.java` + `LikeDomainService.java` 6 个方法 + `LikeUpdateKafkaListener.java` MQ 路径）| 🟡 **OBS**（**域侧写法已与 Go 逐条对齐**：Go **8 处落点 → RAG 8 处**，**13 个属性名**全对；守门 `LikeSpansAttributeGateTest.java` **11/11 绿** + **变异验证红在目标断言**）<br>✅ **gRPC 路径端到端已闭环**（2026-10-27 第七轮）：FB-04（`853767b`）已随合并 `37816ca` 入本树 ⇒ ⇒ 新增端到端探针 `LikeA9GrpcSpanEndToEndTest`（3 条）—— **真 in-process gRPC 链路** ⇒ 域侧属性落在**导出的 server span** 上；**对照组**（同一份域服务、同一通真实调用，只把拦截器换成「复刻 FB-04 之前行为」的那个）⇒ 属性**被丢弃** ⇒ ⇒ **A/B 坐实「机制是承重的」** ＋ 变异验证**红在目标断言** ⇒ ⇒ **原写「🔴 但 gRPC 路径的端到端还差共享层一步 … 已派单 FB-04」—— 该条已销号**（原值保留备查） | **L4（RAG 侧探针活体）+ L3（Go 侧源码逐条）**<br>🆕 **第七轮追加一条 L4：真 gRPC 链路端到端（含对照组 A/B + 变异验证）** ⇒ ⇒ ⚠️ **Go 侧仍是源码级（L3）⇒ 本轴【不】写「双侧活体闭环」** | 2026-10-27 |

> 🟡 **A9 轴定义提醒**：`audit.*` 是 **admin/users 域专用**（`AdminSpans.java:91-93`）⇒ like 域的 Go 侧实际用 `query.*` / `cache.*` / `like.*` / `messaging.*` ⇒ **不要照抄 `audit.*` 前缀**。轴定义本身的问题记在共享面（**本 lane 不写**）⇒ 见 message 域台账 §6 的同款登记。

## 3. 域侧能自修的（**本域直接修**，不甩给 G4）

| # | 条目 | 现状（`文件:行号`） | 建议修法 |
|---|---|---|---|
| D-1 | **like-api 客户端熔断 javadoc 假断言** + **缺 E2E 守门** | `LikeRpcClient.java:43-54` 写「Java 侧尚未接入（已登记的已知偏离）」；实际 `GoStyleGrpcClientBreakerInterceptor.java:103-105` 已被扫到注册 | ✅ **阶段 2 已落码**（**两件都做了**）：① **javadoc 已订正**（`LikeRpcClient.java:43-59`）—— 如实写「Java 侧<b>已接入</b>（共享层 `@Component` + `@GrpcGlobalClientInterceptor`，随 `scanBasePackages` 含 `com.rag.common` ⇒ 扫到即生效）」，并删掉「本模块下游 like-rpc 尚未实现 / 等 message 域一起收敛」那段<b>已过期</b>的理由；② **E2E 守门已补** `LikeApiGrpcClientBreakerE2ETest.java`（真 in-process gRPC server ＋ 真 channel ＋ 真拦截器）⇒ **断言「下游连续返 INTERNAL ⇒ 熔断打开 ⇒ 调用不再触达下游」**（= 证明机制真在链上，不是「Bean 存在」）<br>⚠️ **判别信号刻意不用业务码**：本域 `INTERNAL` 与「熔断打开」**都**映到 1015 ⇒ 码无判别力 ⇒ 改用「下游有没有真被调用」（短路发生在 `next.newCall` **之前** ⇒ 根本不该建 ClientCall）⇒ **不把码值锁死成守门**（码的差异是 A4 的账，见 D-7）<br>✅ **变异验证**：临时置 `rag.protection.breaker.enabled=false` ⇒ **红在目标断言**（「熔断必须打开并让调用不再触达下游；本次下游累计被调用 163 次」）⇒ 非假绿 |
| D-2 | ✅ **已销号（2026-10-30 · G2 复核）**：~~🔴 **A9：like 域 span 属性全缺**（阶段 1 新发现）~~ | RAG `service/like` 各子模块 `src/main` 内 `AttributeKey`/`setAttribute`/`Span.fromContext` **零命中**<br>Go 侧落点：`getlikestatelogic.go:38-41`/`:69-71`、`getlikecountlogic.go:38-40`/`:90-92`、`gettargetlikerlistlogic.go:37-41`/`:86`/`:90`、`getuserlikelistlogic.go:35-39`/`:80`/`:84`、`getusertotallikelogic.go:35-37`/`:52`/`:64`、`likeactionlogic.go:162-166`、`likemq.go:137-143` | ✅ **阶段 2 已落码**（域侧 8 处落点 + 13 个属性名）⇒ 新增 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeSpans.java`（域内 helper，**绝不建子 span** ⇒ 对齐 Go 的 `trace.SpanFromContext` 语义；与 admin 域 `AdminSpans` 的差别见该类 javadoc）<br>· **6 个 logic**：`LikeDomainService.java` 的 `getLikeCount` / `getLikeState` / `getUserTotalLike` / `getUserLikeList` / `getTargetLikerList` / `likeAction` ⇒ **属性位置逐条对齐 Go**：入参组在**参数校验之前**、`cache.*` 在 **DB 回源之前**（⇒ 非法请求 / 回源失败的 span 上同样有这些属性，Go 就是这样）<br>· **MQ 路径**：`LikeUpdateKafkaListener.java` 的 `onMessage` 改走共享层 `MqConsumer.traceConsumer`（= Go `observability.TraceConsumer`）⇒ 产生 **CONSUMER** span（名字 `LikeUpdateService.Consume`）+ `messaging.*` 4 属性；⚠️ `messaging.destination` **照抄 Go 的字面量 `"like-topic"`**（不是配置里的 `like_promotion_topic`）<br>· **守门** `LikeSpansAttributeGateTest.java`（真 OTel SDK + 真 exporter，非 mock）**11/11 绿**；**变异验证**：① 入参组挪到校验之后 ② `query.page_size` 取归一值 ⇒ **各自红在目标断言**<br>🔴 **残留（端到端）**：真链上 handler 执行期 `Span.current()` = **invalid**（探针实测）⇒ 域侧写法在真链上暂时落不了地 ⇒ **已派单 FB-04**（共享层 `RpcInterceptor` 需让 server span 在 handler 期 current）<br>⚠️ **前缀按 Go 侧实际取值**（不是 `audit.*`）<br>🟢 **2026-10-30 · G2 复核销号**：**域侧落码 ＋ 共享层 FB-04 落码**两侧闭合 ⇒ `service/common/.../observability/RpcInterceptor.java:467-480`（`onHalfClose` 用 `otelCtx.makeCurrent()` 包住 handler 调用 ⇒ handler 执行期 `Span.current()` **就是** server span）⇒ 端到端守门 `LikeA9GrpcSpanEndToEndTest.java`（**对照组**复刻 FB-04 之前形态 ⇒ 属性**必须丢**）⇒ ⇒ **差异已消失 ⇒ 本条无待实现内容**（判定层：归 **D 类** · P2 · **不阻断**阶段一）<br>⚠️ **残留（非行为漂移 · 不在本条范围）**：`LikeSpans.java:47-51` 的 javadoc 仍写「handler 执行期 span 尚未成为 current ⇒ 本类的方法在真链上暂时是空操作」⇒ 该措辞**已被 FB-04 落码作废** ⇒ **阶段二订正**（不改行为） |
| D-3 | **路径归一缺失** | 本域无 `GoStylePathNormalizingFilter`（全仓唯一实现在 article-api）⇒ 见 §4 **FB-01** | **本域不单独修** ⇒ 走 FB-01（共享层下沉）⇒ 本域只需在回归时复核 |
| D-4 | **like-api 仍用 `RANDOM_PORT`** | `LikeApiRouterErrorRealTomcatTest.java:36`（javadoc）+ `:54`（`webEnvironment = RANDOM_PORT`）⇒ 落在 OS 临时端口段 49152–65535（**Docker 随机发布端口同段**） | 本域自修：引入 `RealTomcatPort` 同款（favorite 域 2026-10-25 已改 `DEFINED_PORT` + 专用段 21300–21399 + 双绑定自检）⇒ like 选段须避开 favorite / 探针 / OS 临时段（建议 21600–21699，**选段表归 global 协调**）<br>✅ **阶段 2 已落码**：新增 `service/like/like-api/src/test/java/com/rag/like/like/api/support/RealTomcatPort.java`（**21600–21699**）+ **2 个**测试类改 `DEFINED_PORT` + `@DynamicPropertySource registry.add("server.port", RealTomcatPort::pick)` + 每类 1 条端口段守门<br>⚠️ **实测多出 1 处**：本波我新增的 `LikeApiGrpcClientBreakerE2ETest` 也是 `RANDOM_PORT` ⇒ 一并治理（**它若不改，本域的治理就只做了一半**）<br>⚠️ **守门到底拦什么**：**改回 `RANDOM_PORT` 不会红**（`@DynamicPropertySource` 的 `server.port` 优先级更高 ⇒ 端口仍在段内）⇒ 守门真正拦的是「**端口注入被拿掉**」（⇒ 落回 `like-api/application.yml` 的 **18887** ＝ 生产端口）与「`pick()` 返回段外端口」 |<br>🟢 **2026-10-27（G2 独立复核 · G4 端口段表落地后）**：**实测复核通过** —— ① `service/like/like-api/src/test/java/com/rag/like/like/api/support/RealTomcatPort.java:69` `FIRST = 21600` / `:72` `LAST = 21699`（与 G4 的测量**逐条一致**；G4 测量基线 = `9ca4b2c`）② **本域真 `RANDOM_PORT` 注解 = 0**（`grep webEnvironment | grep RANDOM_PORT` 在 like 下命中 **4 条**，**逐条看全是 javadoc / 断言文本里的「有人把 webEnvironment 改回 RANDOM_PORT」** ⇒ **不是注解**）③ 🟢 **端口段表已闭合**（G4 `46f67bf` · 台账 §4.10.19）⇒ ⇒ **本条 D-4 = 已销号**<br>⚠️ **统计口径（写给下一个复核者）**：数「真 RANDOM_PORT」**必须只数 `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` 真注解**，**不数 javadoc / 字符串里的提及**（本域就是被提及命中的典型 ⇒ **文件数 ≠ 注解数**）⇒ ⇒ **「提到」与「用着」是两件事** |
| D-5 | **台账快照过期（4 条）** | 本文件 §1（已就地订正）· `like/api.md` 状态表（015「只剩 015 未修」）· `like/rpc.md` DRIFT-LIKE-013（「仍未修」）· `docs/Intentional_Deviations/like/api.md:20` 的 `SD-2026-10-23-08`（charset 残余）| ✅ **阶段 2 已就地订正 3 条**（**同域写权内**）：① `like/api.md` 状态表（三值判定 + 未修项）—— **015 已由共享层 T-1 于 2026-10-25 落码** ⇒ **首轮 6 条 SEMANTIC MISMATCH 全部修完**；**2026-10-27 独立复核**（不照抄台账）：`service/common/.../GoStyleJsonBodyConverter.java:331` = `getFirst(HttpHeaders.CONTENT_TYPE)` = **原始头值** ⇒ 大小写敏感 ⇒ 与 Go `go-zero@v1.10.2/rest/httpx/requests.go:153-154` 同形；域侧守门 `LikeApiGoStyleBodyContentTypeTest.java` ② `like/rpc.md` DRIFT-LIKE-013 —— **已修**：`service/like/like-rpc/src/main/resources/application.yml:183` = `discovery:///message-rpc`（**三斜杠**，`:170-176` 有形态注释）+ 守门 `LikeRpcProductionAddressTest.java:104-114` ③ 本文件 §1（阶段 1 已就地订正）<br>🔴 **第 4 条不在本 lane 写权内 ⇒ 不派 G4（那是共享面）、交整合者**：`docs/Intentional_Deviations/like/api.md:20` 的 `SD-2026-10-23-08`（候选：响应 `Content-Type` 不带 `charset=utf-8`）⇒ 已被共享层 T-6/E1 吸收 ⇒ 应更新为「语义等价」。⚠️ **`docs/Intentional_Deviations/**` 不在 `check-wave-lane-ownership.py` 的 G2 白名单内**（白名单 = `service/{message,like,task}/**` + `docs/DRIFT/{message,like,task}/**`）⇒ **本 lane 不得写** ⇒ **只登记** |
| D-7 | 🔴 **阶段 2 新发现：like-api 的「下游 gRPC status → 业务码」映射与 Go 不同**（A4 轴）| **Go 侧**（`service/like/api/internal/logic/like/*.go`，6 个 logic 同形）：`st, ok := status.FromError(err)` ⇒ `ok==true` ⇒ `errmsg.NewErrCodeMsg(int(st.Code()), st.Message())` ⇒ **业务码 = 原始 gRPC 码**（`INTERNAL` ⇒ **13**）；`ok==false`（非 status 错误，含 go-zero 熔断打开的 `breaker.ErrServiceUnavailable`）⇒ `errmsg.NewErrCode(errmsg.ErrorServerCommon)` ⇒ **5001**（`common/errmsg/errmsg.go:41`）<br>**RAG 侧**：`service/like/like-common/.../errmsg/LikeGrpcError.java:233-247` 的 `mapGrpcCodeToBizCode` 有映射表 ⇒ `INTERNAL` 落 `default` ⇒ **1015**（`CODE_SERVER_BUSY`）；熔断打开抛的 `GoStyleCircuitOpenException` 不是 status ⇒ `bizCodeFromError` 末尾兜底（`:199`）⇒ 也是 **1015**<br>⇒ **两条路径都不同**：INTERNAL：Go **13** vs RAG **1015**；熔断打开：Go **5001** vs RAG **1015** | ⚠️ **本轮只登记，不擅自扩大落码范围**（修它会改 like-api 的整条错误面 ⇒ 需单独一轮）。**影响面**：仅 like-api 的「下游不是本域业务错误」的路径（即 `LikeErrorMessages` 码表之外的 status）<br>⚠️ **不要照抄 message 域的做法**：Go 的 **message** api **有**域内映射 `messagecommon.BizCodeFromError`（`api/internal/logic/shared/helper.go:31`）⇒ Go 侧 INTERNAL ⇒ 5001；**Go 的 like api 没有这个函数** ⇒ 直接用原始 gRPC 码 ⇒ 两侧形态本就不同 ⇒ 「把 RAG 的 like 改成 message 那套」= **既不对齐 Go，也不是对齐本域真源**<br>⇒ **两条候选修法（需拍板，不在本轮范围）**：① **照 Go**：like-api 的 status 兜底改用原始 gRPC 码（INTERNAL ⇒ 13）、非 status ⇒ 5001；② **保留现状并签字**：把「RAG 统一收敛到 1015」登记为**有意偏离**（理由：码表自洽、不泄漏 gRPC 码） |
| D-6 | ✅ **已销号（2026-10-30 · G2 复核）**：~~🆕 **G3 预告（2026-10-27）· 删除本域的 MyBatis-Plus 排序桥** —— ⛔ **带闸门：必须等 G4 的共享层桥落码后才删**~~ ⇒ 🟢 **闸门已开、域侧桥已收编** | 本域持有 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`（**全仓 9 份域侧桥之一**，本轮 `find` 实测；共享层 `service/common` 命中数 = **0**）＋ 注册行 `service/like/like-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（类注释 `:41` 自带「**什么时候可以删**」条件） | ⛔ **闸门（硬）**：**必须 G4 的共享层桥落码之后才删** ⇒ 否则「域侧删了、共享层还没加」⇒ MP 自动配置退避 ⇒ **不产 `SqlSessionFactory` ⇒ 启动失败**<br>⇒ 删后跑 **`LikeRpcSpringContextWiringTest`**（真 Spring 上下文）⇒ 判据 = 删掉域侧桥后 MP 仍生效（`SqlSessionFactory` 在）<br>⇒ 两座桥同向 ⇒ 并存期功能等价、**不需要跨树同一次提交**<br>⚠️ 本条是 **G3 的预告（非派单）** ⇒ 等 G3 正式派单再动手；🔴 **G4 台账目前查不到「排序桥」条目** ⇒ 共享层那一半的落码状态**待证**<br>🆕 **G3 口径订正后 · G2 独立核实（2026-10-27 03:3x）**：① **派单窗口 = 「G4 共享层桥已落码」这个事件**（不是「各域阶段 2」）⇒ 本域阶段 2 到了也**不删** ② 🔴 **本域删除爆炸半径 = 3 件**（① 删桥类 ② 删 `AutoConfiguration.imports` 注册行 ③ **改 `LikeRpcSpringContextWiringTest` 的 DisplayName 与失败消息** —— `:38` / `:181` / `:184` **仍点名被删的类** ⇒ 删完会留下**失效的断言文本**）—— 本域是三域里**唯一有测试引用**的 ③ **共享层桥对 api 侧的影响 = 零** ⇒ 实测 **10 个 api 模块的 classpath 上全部没有 MP**（⚠️ `task-api/pom.xml` 的那一处命中是注释里的「**刻意不引入**」清单 ⇒ **假阳性**）⇒ 共享层桥在 api 上下文里 = **零 bean** ＋ 悬空排序名 ⇒ 无副作用 ④ 共享层桥的 javadoc **别照抄各域的部分列举**（实测「同款」列举：message 列 **1** 个 / like 列 **4** 个 / task 列 **5** 个 ⇒ **实际 9** 个）⑤ 本域台账 `like/rpc.md:704` / `:1437`（把桥登记为 `DRIFT-LIKE-014` 的修法）在删除后**一并订正**（**同域写权内**） | <br>🟢 **2026-10-27 第十七轮：闸门已满足 ⇒ 本域排序桥已收编（详见本文件「第十七轮 · 域侧排序桥收编到共享层」段）** ⇒ ⇒ **本条原写的「共享层 `service/common` 命中数 = 0」已过期**（那是 03:3x 时的状态；共享层那份 `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration` ＋ 其 `AutoConfiguration.imports` **第 4 行** 已在我树里）⇒ ⇒ **闸门解除** ⇒ ⇒ ⚠️ **本条 ⑤ 里的行号已漂移**（`like/rpc.md:704`/`:1437` ⇒ **实为 `:789`/`:1522`**）⇒ ⇒ **那两处已按「保留原值 + 加订正注记」订正**（行号属事实性指针 ⇒ 纠正指针 + 留注记）⇒ ⇒ 🔴 **⚠️ 本域是全仓第一个同时具备「进程级 fat jar 守门」＋「域内桥收编」的域 ⇒ ⇒ 收编当场揭出一条**跨 lane **的构建路径缺陷**（单模块构建的 fat jar 会起不来）⇒ ⇒ **该缺陷的根因在共享层那份桥从未进过 fat jar ⇒ ⇒ 凡有进程级 fat jar 守门的域（G3 的 comment-rpc / article-rpc / favorite-rpc）收编时都会踩** ⇒ ⇒ 详见该段 ②③④ |

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
| **② Go 侧出处** | `go-zero@v1.10.2/zrpc/internal/serverinterceptors/tracinginterceptor.go:95-107`（`startSpan` ⇒ `tr.Start(ctx, …)` ⇒ **span 进 ctx**）· `:18-24`（`ctx, span := startSpan(ctx, …)` ⇒ `handler(ctx, req)` 用的是**带 span 的 ctx**）—— 这就是 Go 侧「业务代码取得到当前 span」的**机制来源**。<br>范围内 10 个域里**只有 like/rpc** 依赖「当前 span」语义（6 处 `SpanFromContext`：`getlikestatelogic.go:37` · `getlikecountlogic.go:37` · `gettargetlikerlistlogic.go:35` · `getuserlikelistlogic.go:34` · `getusertotallikelogic.go:34` · `likeactionlogic.go:161`）；另：共享层自己 4 处（`service/common/observability/observability.go` 的 `Mark*`）。 |
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
| **Go 出处** | `go-zero v1.10.2 go-zero@v1.10.2/zrpc/internal/serverinterceptors/tracinginterceptor.go:18-24` ＋ `:95-107`；like 域 6 处 `SpanFromContext` | ✅ 我域**独立 grep** 得**同样 6 处**（文件与行号逐条一致） |
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
3. ✅ **A3 升 ✅ 的前置已满足**：共享层的 `GoStylePathNormalizingFilter` **已合入本树** ⇒ ⇒ **真 Tomcat 探针第十轮已跑**（A/B 对照组 ＋ 本域独立变异验证）⇒ ⇒ **「路径归一」这一格 = RAG 腿 L4**
   ⇒ 本域 A3 才能整体升 ✅（Go 侧已 L4 活体坐实：5 种路径变体全 200）
4. 🆕 **E66 落码后：本域 Redis 守门必须仍绿**（task-rpc 的 ① `commandTimeoutMechanismIsEnabled` ＋ like-rpc 的 Redis 超时守门）
   ⇒ E66 的落点会**整体替换** `ClientOptions.timeoutOptions` 那份 default source ⇒ 若新实现不含「命令超时」语义 ⇒ ①（`getTimeoutOptions().isTimeoutCommands()` 必须为 **true**）会红
   ⇒ ⚠️ ① 红 = **新回归**（不是「E66 故意关掉了超时」）⇒ **直接找 G4**
   ⇒ 📌 **订正（2026-10-28 · G4 告知 + 我域独立复核）—— 这条信号【不存在】**
      ⇒ **`isTimeoutCommands()` 恒为 true**：Lettuce 的 `TimeoutOptions.enabled()` = `builder().timeoutCommands().connectionTimeout().build()` ⇒ ⇒ **两个机制位被同一次调用同时打开** ⇒ ⇒ **单独不构成「时长会被施加」的判据** ⇒ ⇒ **它永远不会红**（我域守门 `LikeRpcRedisTimeoutsGateTest` 的注释 `:102-115` 早已写明这一点）。
      ⇒ ⇒ **E66 落码后若有红测试，必须红在真网 = ②（`RedisURI.timeout == 3s`）**（消费点 = `LettuceConnectionFactory` 取**连接级 timeout**）⇒ ⇒ **A/B（机制位）只能当形态断言**。
      ⇒ ⇒ ⇒ **别把「① 没红」当成「E66 没关超时」** ⇒ ⇒ **原措辞保留供追溯**。
   ⇒ 📌 **变异方法约束（G4 2026-10-28 告知 · E66 落码时的「assertion-direction 变异」必读）**
      ⇒ ⇒ **A/B（机制位）在 Boot 默认装配下【恒真】** ⇒ ⇒ **变异点必须选在「装配面」（共享层 customizer 那一侧 —— 它一旦调 `timeoutOptions(...)` 就会整体替换 `enabled()` 那份 default source），【不能】选在「断言面」**。
      ⇒ ⇒ ⚠️ **但共享层另有一条守门 ③**（`GoStyleRedisClientOptionsGateTest`「命令超时必须**未被**本层改动」）⇒ ⇒ **若变异把整个 `TimeoutOptions` 换掉 ⇒ ③ 也会红** ⇒ ⇒ ⇒ **「恰好 1 条红」的判据会被破坏**（会被误读成「两条网一起塌」）⇒ ⇒ ⇒ **必须用「只翻那一位」的最小翻转**（如让 `enabled()` 少一位），**不要**用「另一个 `TimeoutOptions` 整体替换」。
      ⇒ ⇒ ⇒ **本条是「变异怎么翻」的硬约束，不是结论** ⇒ ⇒ **E66 未落码 ⇒ 本轮不执行**（等 Jason 拍板）。
   ⇒ 配套：新 source 在**本请求没有 deadline** 时必须回落到 **`getClientConfiguration().getCommandTimeout()`**（本域 = 3s）⇒ 否则命令失去 3s 上限（断连从「3s 快速失败」退化为「无限等待」）⇒ ⇒ **这条已写进本域台账的 E66 硬约束段（并已独立核实 + 订正其机制表述）**
5. 🆕 **两个「合并后必红」的共享门禁**（2026-10-27 第四轮**实测** ⇒ **写权在 G4 ⇒ 已派单** · 编号 `FB-S1`/`FB-S2`）
   ⚠️ **编号说明**：已发给 G4 的消息里把这两条写成 **FB-06 / FB-07** ⇒ 与本域 §4 的 FB-0x 系列**不同域内编号**，但与 **task 域台账**的 FB-06 撞名 ⇒ ⇒ **以各域台账的 `FB-S*` 为准**（`S` = 跨域共享门禁）⇒ G4 侧按**内容**识别。
   - 🟡 **FB-S1 · `tools/difftest/check-tomcat-limits.py`**：**对 like 域零影响**（期望表里 `like-api` = `envelope`，本域 yml 未动 ⇒ 该门禁的红**只来自 `task-api` 那一条**）⇒ ⚠️ 但**合并后整仓复跑仍会红** ⇒ **仍须 G4 修**（修法与影响面见 [task/vs-common.md](vs-common.md) 第 5 条的 `FB-S1`）
   - 🔴 **FB-S2 · `tools/difftest/check-wave-coupling.py`**：我树 **EXIT=1**（6 条 `common_radiation` 漂移）；主干与 G4 树 **EXIT=0**
     ⇒ **其中 2 条与本域相关**：`GoStyleGrpcClientBreakerInterceptor` 增 `like`（**生产代码** `LikeRpcClient`）· `MqConsumer` 增 `like`（**生产代码** `LikeUpdateKafkaListener`）
     ⇒ ⇒ 另 4 条是 **task 域**的（`GoStyleFormLimitMode` / `GoStyleFormValueLimitInterceptor` / `GoStyleRpcBreakerInterceptor` / `GoStyleRpcSheddingInterceptor` ⇒ **全部来自 `src/test/` 的 import**，**本域与 task 生产代码对这些类的 import = 0**）
     ⇒ ⇒ **根因 = 快照过期，不是代码回退**：`docs/DRIFT/_cross/cross-domain-coupling.md`（**744 行 · 三棵树逐字相同**）的 `<!-- wave-coupling-snapshot -->` 块**未含 `CD-6`〔旧号 `X-6`〕(task) 与 A9(like) 的接入** ⇒ ⇒ **文档三棵树一致 + 主干/G4 树绿 ⇒ 差异全部来自域侧接线代码**
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
| **② like-rpc 同款启动 flake** | ✅ **成立且已在本域落码修法**（`StringBuffer` ＋ 等日志行 ＋ `drain.join(10s)`）⇒ 并**独立核过全我域同款范围**：全域 `grep -rn "destroyForcibly" service/{like,task,message} --include=*.java` = **1 命中**（就是这处）⇒ `TaskRpcFatJarArtifactGateTest` 是**产物层**（不起进程）⇒ **不是**同款；`message-rpc` **无**此类 |
| **③ Redis 超时口径**（Boot 4 两属性不再共用；不配 `connect-timeout` ⇒ Lettuce 默认 **10s** ⇒ **漂移 2 倍**） | 🟢 **与我域独立复核结论一致** ⇒ **我域已修，无需再动**：`like-rpc` 补 `connect-timeout: 5s`；`task-rpc` 补 `timeout: 3s` ＋ `connect-timeout: 5s`（双侧活体对拍：Go **1694ms** vs RAG 修前 **60021ms** vs RAG 修后 **3ms**）⇒ 守门 **12 条**（`LikeRpcRedisTimeoutsGateTest` **6** ＋ `TaskRedisCommandTimeoutGateTest` **6**；后者 2026-10-27 本轮 +2 = Redis **连接池** ⑤⑥，前者同日 +2 = 同样两条池断言 ⇒ **两域的池都有守门**）<br>🆕 **2026-10-27（本轮 · 下一轮候选已执行）**：**like 域的池数值轴已修** —— `min-idle: 4` ⇒ **`8`**（= Go 的 `MinIdleConns`，go-zero 的 `idleConns = 8` ⇒ **精确对齐**）＋ 守门 ⑤⑥ ⇒ 详见 [`like/rpc.md` 的 `DRIFT-LIKE-RPC-REDIS-POOL`](rpc.md#🟡-drift-like-rpc-redis-pool新登记--2026-10-27redis-连接池数值未对齐形态已对齐仅数值不同🆕-数值轴已修min-idle-4--8--go-确切值-残留--max-active性能面) |
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
- ⇒ **Go 出处**：`service/like/rpc/internal/mq/internal/mqs/likemq.go:83-84` 的 `observability.TraceConsumer(ctx, "like-mq", "LikeUpdateService.Consume", likeMessageAttrs("like-topic", key), func(ctx) error { … })`
  ⚠️ **2026-10-29 真实性复核 · 出处路径订正**：原写 `service/like/rpc/internal/mq/likemq.go` ⇒ **该路径不存在**（Go 真源里它在 **`internal/mq/internal/mqs/`**）⇒ **结论不变，仅出处路径订正** ⇒ `:83` = `func (l *LikeUpdateService) Consume(ctx context.Context, key, val string) error {`、`:84` = 那行 `observability.TraceConsumer(...)`（2026-10-29 当场核对）。
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
- [x] **共享面不回写**（合并后由**合并收口执行者**统一更新）

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
7. ✅ **启动守门 flake 的修复**（`LikeRpcRuntimeBootGateTest` · G3 `8b2c6e9` 同款）—— **已落 + 已回归 + 已变异验证**（2026-10-27）：<br>· **落码**：`StringBuilder` → **`StringBuffer`** ＋ **收摊前等日志标志行**（30s / 200ms）＋ **`drain.join(10s)`** ＋ 标志串提为常量 `GRPC_STARTED_MARKER`（消除「等待与断言两处字面量漂移」）<br>· **回归**：**like-rpc 111 / task-rpc 115**（`0 failures / 0 errors / 0 skipped`）· `BUILD SUCCESS`；目标类 **Tests run: 1, Skipped: 0**（4.641 s）⇒ **真跑没被 skip** ✔<br>· **变异**：标志串改假串（只改一处）⇒ **红在目标断言** `:155`（36.11 s ≈ 等满 30s 后失败，且失败消息带真实日志尾部 ⇒ 证明进程确实起来了）⇒ 改回后复跑 **1 条绿**（5.784 s）✔<br>· **我域同款排查**：全域 `grep -rn "destroyForcibly" service/{like,task,message}` = **1 命中** ⇒ 同款**仅此一处**（`TaskRpcFatJarArtifactGateTest` 是产物层、message-rpc 无此类）<br>⚠️ **仍剩**：**重复跑 N 次全绿**的统计样本偏小（本轮该类共跑 2 次）⇒ 收口回归时再补几轮 ⇒ 详见 [`like/rpc.md`](rpc.md) A 节。
8. 🆕 **两条「无人拥有」的 U+FFFD 落在 `tools/**`**（G3 扫出并建议修，我域**不越权改**）—— `tools/errmsg-probe/README.md:59`（`未登记码的回U+FFFD×3结果`）+ `tools/sql-statement-probe/favorite/compare.py:561`（`两侧都U+FFFD×3 / 两侧都没有`）⇒ **`tools/**` 不在我 G2 写权面** ⇒ **转派 G4**（若 `tools/**` 归 G4 写权面则请修；否则请指定 owner）⇒ **修复前先字节级复核**（`read` 的输出不可当判据，见上）。<br>9. 🔴 **E65（= global U15）：本域有 1 处「跨域出向 gRPC」⇒ 出向不继承请求 deadline ⇒ 漂移成立**（**2026-10-27 独立核实 · G3 知会 + Jason 拍板方向**）<br>· **本域跨域出向清单（唯一一处）**：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/client/MessageRpcClient.java`（**like-rpc → message-rpc**）⇒ ⚠️ **与 `like-api/LikeRpcClient` 区分**：那是**本域内** api→rpc（阶段 2 已落了 `min(cap, remaining)` 传递）⇒ **不属于 E65**<br>· **Go 侧事实（带 ctx ⇒ 继承 deadline）**：`service/like/rpc/internal/logic/likeactionlogic.go:380` ⇒ `l.svcCtx.MessageRpc.SendNotification(l.ctx, &messagepb.SendNotificationReq{…})` ⇒ ⇒ **Go 侧出向传了 ctx** ⇒ ⇒ **继承请求 deadline**<br>· **RAG 侧现状（零处传）**：`MessageRpcClient.java` 全文 **零** `withDeadline` / `applyTo` / `DeadlineState` / `GoStyleRequestDeadline` 命中 ⇒ ⇒ **出向调用不带任何 deadline** ⇒ ⇒ **E65 在本域成立**<br>· **🔴 Jason 拍板的方向（2026-10-27，G3 转达）= 走 global 统一载体 `GoStyleRequestDeadline.applyTo(stub)` ⇒ 本轮不改** ⇒ ⇒ 共享层那半落地后**本域照做**（已进本文件「合并后我域必做」）<br>· 🔴 **2026-10-27 第十四轮订正（本域源码级独立复核 · 上一行「共享层那半落地后本域照做」**不足** ⇒ ⇒ 域侧单补 `applyTo` 仍是**无用功**（no-op）⇒ ⇒ 两端必须**同一次提交**一起改）**：<br>　① **出向载体在 rpc 进程里恒为 null**：`service/common/.../observability/GoStyleRequestDeadline.java:110-120` 的 `applyTo` 读 `DEADLINE_NANOS`（ThreadLocal），**null 即原样返回 stub** ⇒ 而全仓 `GoStyleRequestDeadline.begin(` 的**主代码调用点只有一处** = `HttpMiddleware.java:324`（`:356` 对应 `clear()`）⇒ ⇒ **gRPC 服务端进程无 HTTP 入口 ⇒ `DEADLINE_NANOS` 恒 null ⇒ rpc→rpc 出向 `applyTo` 恒 no-op**（🔴 **共享层自带书面证明（最硬的证据）**：`service/common/.../spring/ObservabilityLifecycle.java:57-58` 明写「纯 gRPC 进程里 `HttpMiddleware` **从不加载**（没有 servlet filter 要注册）」· `:69-70`「rpc 侧的 HTTP 池**从未被创建过**（`HTTP_EXECUTOR` 是 `HttpMiddleware` 的 static 字段，而该类在 rpc 进程里从不加载）」⇒ ⇒ **这不是我的推断，是共享层源码写死的**） ⇒ ⇒ **即便本域把 `applyTo` 加上，也套不出任何 deadline**（旁证：`service/common/.../observability/RpcInterceptor.java` 内 `GoStyleRequestDeadline` **零命中** ⇒ 服务端拦截器只绑 `DeadlineState`）<br>　② **Go 侧是单载体 ⇒ 两环天然相连**：服务端 `zrpc/internal/serverinterceptors/timeoutinterceptor.go` 的 `ctx, cancel := context.WithTimeout(ctx, t)`（**叠加不覆盖** ⇒ 入站 ctx 自带的客户端 deadline 保留 ⇒ 有效 = min(客户端 deadline, now+t)）→ 同一个 ctx 交给 handler → 出向 `zrpc/internal/clientinterceptors/timeoutinterceptor.go` 的 `context.WithTimeout(ctx, t)`（= min(父剩余, 配置 2s)；由 `zrpc/internal/client.go:115-117` `if c.middlewares.Timeout { … TimeoutInterceptor(timeout) }` 装上）⇒ ⇒ **Go 侧只有 `ctx` 一个载体 ⇒ 服务端绑过的 deadline 出向自动继承**<br>　③ **RAG 侧不是「缺计算」，是「缺载体交接」**：`RpcInterceptor.java:293` 的 `long effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`（`:291` 服务端预算 · `:707-719` `clientDeadlineRemainingMs()` 取 `io.grpc.Context.current().getDeadline()`）⇒ ⇒ **这一行的语义已与 Go 的 `WithTimeout(ctx, t)` 叠加等价**（= min(入站剩余, 服务端配置)）⇒ ⇒ 但它被绑到 `DeadlineState`（`:478` / `:570` `DeadlineState.bindToCurrent(deadlineEpochMs)`，**毫秒 epoch**）⇒ ⇒ **与 `applyTo` 读的那个载体（**纳秒** ThreadLocal）不是同一个** ⇒ ⇒ **值算对了、传不过去**<br>　④ ⇒ ⇒ **收口判据**：本条要闭合，必须**共享层在 gRPC 服务端把 `effectiveTimeoutMs`（或等价值）写进 `applyTo` 读的那个载体**，取值 = **min(入站剩余, 服务端配置)** ⇒ ⇒ 落点 = `service/common/**` ⇒ ⇒ **属 G4 写权面 ⇒ 本域不可单方面落码** ⇒ ⇒ 域侧那半（`applyTo` 调用点）**只有在共享层那半落地后才有意义** ⇒ ⇒ **两半必须同一次提交**（否则留下「域侧已改、实际仍 no-op」的半截 ⇒ 又是一个假绿）<br>　⑤ ⚠️ **与下一条被否决备选的关系**：那条否决「用 `DeadlineState.remainingMillis()` 构造出向 stub deadline」的三条理由**依然成立且更硬** —— 因为 `DeadlineState` 与 `applyTo` 的载体**本来就不通**（这正是 ① 的根因）⇒ ⇒ **正确修法不是让域侧去读另一个载体，而是把共享层那一个载体在服务端也接上**（对齐 Go 的单载体）<br>　⑥ ⚠️ **与 global 台账的关系**：G4 已在 `docs/DRIFT/global/vs-common.md:46` 就 E65 附注做过一次订正（「`applyTo` **不是** no-op；真正的不继承发生在 rpc 进程：预算 ThreadLocal 只由 `HttpMiddleware` 写入 ⇒ rpc 进程无 HTTP 入口 ⇒ `DEADLINE_NANOS` 恒 null」）⇒ ⇒ **与本条结论一致**（本条是**按本域源码 + Go 源码独立复核得出**，**不是转述 G4**）⇒ ⇒ **不重复登记、不重复派单**<br>· **被否决的备选**（**本域不得选边**）：用 `DeadlineState.remainingMillis()` 直接构造出向 stub deadline ⇒ 三条理由：① 引入**第二种载体**（仓内 `GoStyleRequestDeadline` 与 `DeadlineState` 互不相通 ⇒ 这正是 global 认定的病根）② 偏离 Go 的**单载体**语义（Go 侧只有 `ctx`）③ 要改 global 门禁期望值（`outgoing_carrier` 当前钉 `absent`）<br>· ⚠️ **G3 订正的一个「既有先例」不成立（我域已核 ⇒ 本域台账零处引用它 ⇒ 无需订正）**：原引「`users/admin/AdminDomainService.java:140` · `users/user/UserDomainService.java:145` 已用 `DeadlineState.isExpired()` ⇒ 仓内已有第二种载体的出向用法」⇒ ⇒ **实测不成立**：那两处是**本地超时检查**（`if (Thread.currentThread().isInterrupted() || DeadlineState.isExpired())`）⇒ **不是「用 `DeadlineState` 构造出向 gRPC deadline」**；另**行号已漂移**（实测命中 `AdminDomainService.java:178`）⇒ ⇒ **不得据它论证「仓内已有第二种载体的出向用法」**，引用前须重新定位<br>10. 🔴 **命令超时的守门判据偏弱（G1 探针发现 · G2 独立字节码核实 ⇒ 比 G1 再精化一层）**<br>· **G1 的探针读数（转述）**：`isTimeoutCommands()`=true · `isApplyConnectionTimeout()`=true · `getSource()`=`DefaultTimeoutSource` · `LettuceConnectionFactory.getTimeout()`=3000ms ⇒ ⇒ G1 判「`isTimeoutCommands()` 不是有效判据、应改断言 `isApplyConnectionTimeout()`」<br>· 🟢 **G2 独立核实（字节码级）**：`TimeoutOptions.enabled()` = `builder().timeoutCommands().connectionTimeout().build()` ⇒ ⇒ **两个位同时被打开** ⇒ ⇒ **在 Boot 4 默认装配下这两个断言单独都是恒真** ⇒ ⇒ ⚠️ **「改断言 `isApplyConnectionTimeout()`」并不能把弱判据变成强判据** ⇒ ⇒ **两条合起来才等于 `enabled()` 的全貌**；且 `DefaultTimeoutSource.getTimeout(...)` = **恒 -1** ⇒ ⇒ **`source` 给不出时长** ⇒ ⇒ 时长只能走 `applyConnectionTimeout` 分支从连接级 timeout 取（与 G1 读数一致）<br>· ⇒ ⇒ **完整判据链 = 四条合起来**：**A** `isTimeoutCommands()` ＋ **B** `isApplyConnectionTimeout()` ＋ **C** `getSource()` 恒 -1 ＋ **D** `RedisURI.timeout` = 3s（**本域守门 ③ 已断言**）⇒ ⇒ **任何单条都不充分**<br>· ✅ **域侧补强动作已落码（2026-10-27 12:3x · **已编译 + 已变异验证** · 证据见本节末）**：like-rpc 守门 ① 原先只有 **A** ⇒ ⇒ **已补 B** ⇒ ⇒ 注释必须写明「A+B 才等于 enabled() 全貌、单独恒真」⇒ ⇒ **不得**把它写成「时长会被施加」的判据<br>· ⚠️ **边界**：被测机制不在域侧写权面 ⇒ ⇒ **无法做「机制变异」** ⇒ ⇒ 这条是**回归网**，不是「已证伪该机制」；`LettuceConnectionFactory.getTimeout()` 这个出口 **G2 未独立复核**（本域已复核的是 `RedisURI.timeout`）<br>7. 🔴 **我的一条「转移单」是假转移（G4 独立核实并拦截 · G2 采信 · 2026-10-27 12:4x）** ⇒ **自我订正**：我曾据「Go 侧该域有 `rest.WithJwt` ⇒ 适用」就推断「article-api / comment-api / favorite-api 也欠修 009 式的『JWT 验签落在 HTTP 预算内』」，并准备请 G4 转发 ⇒ ⇒ **G4 已独立核实：那三域的 RAG 侧早已修好**（`GoStyleJwtAuthFilter` 的 `@Order(HIGHEST_PRECEDENCE + 6)` ⇒ 验签在预算起点**之前**）⇒ ⇒ **「Go 侧适用」≠「RAG 侧还欠修」** ⇒ ⇒ **我的推断把『适用』悄悄换成了『欠修』** ⇒ ⇒ **该转移单作废**（**我未发出**，已在 G3 队列侧核过 ⇒ 无需撤回；G4 的预防性拦截件已入 G3 队列）<br>⇒ ⇒ **教训（本域留档）**：**真源侧有该行为 ⇒ 只说明「适用」，不说明「RAG 侧还没修」** ⇒ ⇒ 判定「谁欠修」**必须同时读 RAG 侧的当前状态**（`@Order` / 现有实现）⇒ ⇒ **「适用」与「欠修」是两个独立判断，合并成一个就是假转移** ⇒ ⇒ **转派前必须先读被转域的现状**<br>⇒ ⇒ ⚠️ **另一处不精确（G4 指出）**：那三域**不是**都在 3s 预算上 —— article-api 是 **70s**、comment-api / favorite-api 是 3s ⇒ ⇒ **槽位相同（+6 在预算起点 +7 之前）≠ 预算时长相同** ⇒ ⇒ 引用时**槽位与时长要分两轴写**<br>⇒ ⇒ 🟡 **仍属我域的残留（G4 指出 · 由整合者处理）**：那三个拦截器都保留「Filter attribute 缺失时就地验签」的 inline fallback ⇒ ⇒ 那条路径把验签放回预算内 ⇒ **我域无同类实现**（我域 task-api 无鉴权 ⇒ `DRIFT-USERAPI-009②` 对我域**不适用**）⇒ ⇒ **不属我域待办**<br>8. 🟢 **U+FFFD 全清单口径订正（G4 独立实测 · 两份清单交叉核对通过 · 2026-10-27 12:4x）** ⇒ **全仓 = 18 个文件 / 47 处**（G4 于 12:16 的字节级实测）⇒ ⇒ **我上轮给的清单已 stale**（其中「G4 写权面的 2 条」已由 G4 在 `0c72969` 修掉 ⇒ 不要重复登记）⇒ ⇒ **两份独立清单数字吻合** ⇒ ⇒ **「无人拥有」实为 4 条**：`tools/errmsg-probe/README.md`(3) · `tools/sql-statement-probe/favorite/compare.py`(3) · `docs/Pitfalls/message/rpc.md`(2) · `docs/Pitfalls/users/user/rpc.md`(1)<br>⇒ ⇒ **其中 `docs/Pitfalls/message/rpc.md` 的 2 处内容属我域（message 域）** ⇒ ⇒ **我认领其内容归属**，但 **`docs/Pitfalls/**` 不在我 G2 写权白名单** ⇒ ⇒ **我域不能写** ⇒ ⇒ **已请整合者/该文件 owner 处理**（G4 已把 4 条统一登记在其台账 4.10.7 item 6）⇒ ⇒ **这是「路径归属 ≠ 内容归属」的典型**：内容是我的、路径不是 ⇒ ⇒ **别把「不在我写权面」读成「不是我的问题」**<br>🟢 **「机制位②」断言已落码 + 已编译 + 已变异验证（2026-10-27 12:5x）** ⇒ **上一行「等待编译与变异验证」的状态已销账**：<br>· **落码**：`ab0d782` ⇒ task-rpc 与 like-rpc 的 Redis 超时守门 ① 各补一条 `isApplyConnectionTimeout()` 断言（断言消息写明「只覆盖第二位 · 与 ① 合起来才等于 `enabled()` 全貌 · **单条恒真** · **不是**『时长会被施加』的判据 · 时长由 ② 断言」）<br>· **编译**：✅ 通过（`mvn -o -pl … -am -DskipTests install`）<br>· **守门跑绿**：task-rpc **6 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS** · like-rpc **6 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS**<br>· **变异验证（断言方向变异）**：把新断言的 `isTrue()` 改成 `isFalse()` ⇒ ⇒ **两域各红 1 条**，且**红在目标断言** `commandTimeoutMechanismIsEnabled`（task-rpc `TaskRedisCommandTimeoutGateTest` · like-rpc `LikeRpcRedisTimeoutsGateTest`）⇒ ⇒ **恰好 1 条红**（另一条阴性对照仍绿）⇒ ⇒ **非假绿** ⇒ ⇒ 还原后复跑**两域 6/0/0/0 绿**⇒ ⇒ **sha256 与提交版逐字节一致**（还原无残留）<br>· ⚠️ **诚实的边界（不变）**：被测机制（Boot 的 `TimeoutOptions.enabled()` 接线）**不在域侧写权面** ⇒ ⇒ **无法做「机制变异」** ⇒ ⇒ 本条是**回归网**，**不是**「我已证伪过该机制」⇒ ⇒ 上面那次变异证明的是「断言在读运行期值」<br>· ⚠️ **判据自身的一条踩坑**（记下来免得下次重踩）：第一次变异我用 `grep -E "…|expected|but was"` 抓输出 ⇒ ⇒ **被 Kafka 的 `sasl.oauthbearer.expected.audience` 配置行占满 `head` 配额** ⇒ ⇒ **「红没红」根本没被看到**（既不能算红、也不能算绿）⇒ ⇒ 改成只抓 `Tests run:` / `<<< FAILURE` / `BUILD` 后才拿到真相 ⇒ ⇒ **抓取模式本身也会骗人 ⇒ 变异验证的判据必须精确到「失败计数」与「失败用例名」**<br>🟢 **G4 两条断言 · G2 独立复核通过（2026-10-27 12:5x）**：① **我域三域 production 代码对 Redis 客户端零覆盖** —— `ClientOptions` / `LettuceClientOptions` / `disconnectedBehavior` / `DisconnectedBehavior` 在 `service/{like,message,task}` 的 **`src/main` 全部零命中**（排除 `src/test`）⇒ ⇒ **共享层的 `REJECT_COMMANDS` 与breaker 键改法在我域无任何生产侧覆盖 ⇒ 零影响**（G4 的 [1] 登记成立）② **三处 mode 值**：`like-api/application.yml:125` = `envelope` · `message-api/application.yml:152` = `envelope` · `task-api/application.yml:225` = `"off"`（**带引号**）⇒ ⇒ 逐条与共享层语义表一致（`GoStyleFormLimitMode` like=ENVELOPE / message=ENVELOPE / task=OFF）⇒ ⇒ **task 的引号必须保留**（未加引号的 `off` ≠ 省略该属性；favorite 才是「省略整块 ⇒ OFF」） |

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
  ⇒ ⇒ 一次同时钉住「有 breaker」「有 shedding」「层序 = recover→breaker→shedding」（Go `go-zero@v1.10.2/zrpc/server.go:144-167`）
  ⇒ ⇒ 「实际链 == 配置推出的构成」抓住「**配置说三层、实际只装两层**」这类装配与配置脱节 ⇒ ⇒ **旧判据完全覆盖不到**
- **新增 ②**「关掉降载 ⇒ 层名列表变成 `[recover, breaker]`」⇒ ⇒ 证明**降载层是条件装的**（Go `go-zero@v1.10.2/zrpc/server.go:160-163`）⇒ ⇒ **不必再「关掉降载来换取确定性」**
- ⇒ ⇒ **旧的「包装层归属」那条保留不删** ⇒ ⇒ **互补而非替代**（层名证「有哪些层 + 层序」；包装归属证「真的包住了调用」）
- ⇒ ⇒ **客户端熔断那一面**（`GoStyleGrpcClientBreakerInterceptor` · `LikeApiApplication.java:75-77` 扫 `com.rag.common` 注册）**已由阶段 2 的 `LikeApiGrpcClientBreakerE2ETest` 覆盖**（真 in-process gRPC server ⇒ 熔断打开后调用不再触达下游）⇒ ⇒ **本轴两侧（服务端链 + 客户端熔断）均有运行期证据**
- ⇒ ⇒ **本域 A2 轴：L1（源码）⇒ L2（运行期 · 守门 + 变异验证）**

#### ③ 变异验证（判据非假绿）

- 变异方式 = **断言方向变异**（期望层名改成 `["recover","breaker"]`）⇒ ⚠️ **被测机制在共享层、不在本 lane 写权** ⇒ ⇒ 只能做方向变异（证明**断言读的是运行期真值**）⇒ ⇒ **这是「回归网」证据，不是「已证伪该机制」**
- 结果：**本域红且只红 1 条**，红点**精确**在 `chainLayerNamesMatchComposedChain`（`LikeProtectionChainWiringTest.java:132`）⇒ ⇒ **其余 3 条阴性对照仍绿** ⇒ ⇒ **非假绿**
- ⇒ 还原后复跑：**4 条绿** · 0 failures / 0 errors / 0 skipped · BUILD SUCCESS
- ⚠️ **还原脚本的判据坑（我自己踩到）**：变异后文件里有 **2 处** `containsExactly("recover", "breaker")` ⇒ ⇒ 全局字面量替换的还原脚本会**断言失败并中止**（文件停在变异态）⇒ ⇒ **还原必须按位置精确替换**

#### ④ 数字订正

- **like-rpc**：`mvn test` 口径 **111 ⇒ 113 ⇒ 115**（**两段都有归因，不是推算**）：
  · **111 ⇒ 113** = 提交 `cd12ce3`「Redis 连接池 min-idle 4 ⇒ 8 ＋ **新增池守门 ⑤⑥**（like-rpc **首次**有池守门）」⇒ +2 ⇒ ⇒ **13:00 合并前实测 113 ✔ 吻合**
  · **113 ⇒ 115** = 本轮 FB-03 升级（`LikeProtectionChainWiringTest` 2 ⇒ 4）⇒ +2 ⇒ ⇒ **13:39 合并后实测 115 ✔ 吻合**
  · ⚠️ **第三轮记录的 111 保留在下方订正行**（原口径 = `mvn test`）
  · **115 ⇒ 118** = 本轮（第七轮）新增 `LikeA9GrpcSpanEndToEndTest` **3 条**（A9 真链路 + 对照组 + 护栏）⇒ +3 ⇒ ⇒ **14:1x 实测 118 ✔ 吻合**（`mvn -o -B -DargLine=-Xmx2g -pl service/common,service/like/like-rpc test`⇒ **118 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS · 2:36 min**；surefire **mtime 切分口径 = 20 份报告 / 118 条** ⇒ **与 maven 汇总行相等** ✔）
  · ⚠️ **`service/common` 必须在 reactor 里**：`~/.m2` 的 `common` jar 里 `makeCurrent` **只有 1 处**（**不含 FB-04**）⇒ ⇒ 只跑 like-rpc 会编译到**旧**共享层 ⇒ **真链路那条必然红** ⇒ ⇒ 这是「`~/.m2` 的 jar ≠ 源码」的老坑 ⇒ ⇒ **不 install、只在 reactor 内解决**
  · ⚠️ **我一度把 113 写进台账**（只按「第三轮 111 + 本轮 +2」推算）⇒ **被合并后实测 115 证伪** ⇒ ⇒ **推算值必须等实测打脸才认** ⇒ 这是「实测 vs 推算」的同一条纪律
- ⇒ 🟢 **同类 stale 会被 `check-ledger-selfcheck.py` 层 3 抓到**（默认只查 task 域 ⇒ **like / message 域的数字要自己盯** ⇒ 这是「守门覆盖面 ≠ 全域」的已知缺口）

#### ⑤ 🟢 **合并后回归（实测 · 2026-10-27 13:31:32 → 13:39:12 · 7:39 min · `MVN_EXIT=0` · BUILD SUCCESS）**

reactor = **`service/common` + G2 三域 8 模块**（共 **9** 个）⇒ ⇒ **为什么必须把 `service/common` 放进 reactor**：`layerNames` / `compose` 只存在于**合并后的共享层源码**里，而 `~/.m2` 里还是旧 jar ⇒ ⇒ 若只跑 8 个域模块 ⇒ 它们会编译到**旧**共享层 ⇒ **编译不过** ⇒ ⇒ **替代方案是 `install` 共享层 jar 到 `~/.m2`** ⇒ ⇒ **我拒绝这么做**（那会改变别的 lane 正在解析的依赖 ⇒ 尤其 G4 当时正在跑第 12 次全量）⇒ ⇒ **只在 reactor 内解决**

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**合并进来的共享层**） | 324 | 0 | 0 | 0 |
| `service/message/message-api` | 98 | 0 | 0 | 0 |
| `service/message/message-rpc` | **89** | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 89 | 0 | 0 | 0 |
| `service/like/like-rpc` | **115** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 97 | 0 | 0 | 0 |
| `service/task/task-rpc` | **119** | 0 | 0 | 0 |
| **合计** | **970** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 646**（合并前同口径 **637** ⇒ **+9** = task +2 ＋ like +2 ＋ message +5 ⇒ ⇒ **逐条对得上本轮新增的用例**）⇒ ⇒ **共享层自身 324 条全绿** ⇒ ⇒ **合并进来的共享层在我树里是绿的**（不是「合进来就假定它对」）
⇒ ⇒ ⚠️ **口径纪律**：① 数字取自**本轮 mtime 切的 surefire XML**（起跑标记 epoch `1793079092`）⇒ **0 stale 报告**（like-rpc 那 1 份孤儿 `LikeRpcServerSpanCurrentProbeTest` 被正确剔除 ⇒ 见上文口径段）② **并发事故披露**：起跑瞬间 **G3 与 G4 也同时起了 maven** ⇒ **三路并发** ⇒ 我的占用者检查**确实打印出了**那两个树，但被我紧跟的一句写死 echo「(空 = 可起跑)」**盖住** ⇒ ⇒ **我没有看到自己打的字就起了跑** ⇒ ⇒ 幸而机器 24 GB、`memory_pressure` 42% free、0 throttled page ⇒ **三路都跑完、无 OOM**（G4 的第 12 次全量正常完成、其台账已落「第十二次全量口径」）⇒ ⇒ **但这是运气不是纪律** ⇒ ⇒ **已落 memory：占用者检查的输出不许被任何写死的结论句盖住**

### 🔴 **C7 的硬前提：收口回归必须在【所有 lane 合入主干之后】跑，不能用任何 lane 的 lane 级全量代替**（2026-10-27 13:4x · 采信 G1 的实测并**独立复核**）

⇒ **G1 的实测依据**（G1 树 `b7ff946` · `docs/DRIFT/users/vs-common.md` §13.3）：G4 第八次全量里 **G1 域 6 个组件 = 605** 条，而 **G1 树 `fa608f3` 实测 = 667** 条（差 **62**）⇒ ⇒ 根因 = **G4 的树里没有 G1 的任何提交**（7 笔全 `NOT_IN_G4`）⇒ ⇒ **那份全量的工作区 ≠ 任何单域的当前态** ⇒ ⇒ **这不是「谁多跑了测试」，是「代码态不同」**

⇒ ⇒ **三条硬前提**（C7 执行时逐条对照；**我域本轮实测值已填**）：

| # | 前提 | 判据（可机械核） | 本域当前实测（2026-10-27 13:4x） |
|---|---|---|---|
| 1 | 收口回归的代码态必须包含**所有 lane** 的提交 | 回归前 `git rev-list --left-right --count do-my-own-gh...HEAD` ⇒ **落后数 = 0**；并逐 lane 核「该 lane 的 tip 已是祖先」 | ✅ **本树 = `0 104`**（左 0 落后 / 右 104 领先）<br>🔴 **但四个 lane 的 tip 全部尚未入主干**：G1 `b7ff946` / G2 `2e6e027` / G3 `a1e9271` / G4 `dcd0706` ⇒「已入主干 = **NO**」⇒ ⇒ **C7 现在还跑不了**（前置未满足） |
| 2 | **不得**引用任何 lane 的 lane 级全量数字当作**本域 / 波次**证据 | 每个 lane 的全量都在**自己的树**上跑 ⇒ 别的 lane 的提交不在里面 | 本域的 **646**（域侧 8 模块）/ **970**（含 `service/common`）**只能当「G2 树 × (G2 三域 ＋ 合并进来的共享层)」的证据** |
| 3 | 本域数字只有两个来源可采信 | ① **本域树上的本域回归** ② **所有 lane 合入后的收口回归**（C7，尚未跑） | ① = **646** / 970（口径见上）；② = **未跑** |

⇒ ⇒ ⚠️ **对「646 / 970」的诚实定位**：它是 **G2 树 × (G2 三域 ＋ 合并进来的共享层)** 的数字，**不是**波次最终数字 ⇒ ⇒ 它能证明「**我域那半在我树上是绿的**」，**不能**证明「**波次是绿的**」⇒ ⇒ **别把「谁的数字大」当判据**（605 vs 667 的差是**代码态不同**）

⇒ ⇒ 🔴 **一条对我域尤其要紧的补充**（G1 13:2x 实测 + 我本轮独立核实）：**我树已合并 G4 分支（`37816ca`）⇒ 我树里有共享层的 Redis 收编 / 池自愈 / span current；而 G1 树里一个都没有**（G1 树 `service/common` = 主干基线）⇒ ⇒ **同一波次里，不同 lane 的树的内容差异会越来越大** ⇒ ⇒ **汇合点只能是 `do-my-own-gh`（主干）** ⇒ ⇒ **任何 lane 的「全量」都不能代表其他 lane 的树** ⇒ ⇒ **我域的合并后收口回归必须在「所有 lane 入主干之后」跑**

⇒ ⇒ 📌 **本域台账里所有「本轮实测 N 条」的定位统一改述为**：**「G2 树 × (G2 三域 ＋ 共享层) 的 lane 级数字」** ⇒ ⇒ **它们不升级任何轴的波次级证据强度**（跑绿 ≠ 对 ⇒ 同一条纪律的第二个层级）

⇒ ⇒ 📌 **顺带确认 U+FFFD 的合并归属**（G1 的全表 20 份 / 51 处 与我域结论**逐条一致**）：我域那 **3 份**（`LikeOutboxEventEntity.java:17` · `GoStyleGrpcClientBreakerE2ETest.java:43` · `MessageRpcProductionAddressTest.java:96`）⇒ **合并时取 G2 树版本**（我树字节级 0 命中）⇒ ⇒ **与「共享层那 2 份取 G4 树 / users 那 3 份取 G1 树」同规则** ⇒ ⇒ **合并后请再跑一次全仓字节级扫描**（不是 grep）
⇒ ⇒ 📌 **补一条「计数单位」纪律（G1 的表头不闭合 ⇒ 我按表体逐类加回去）**：G1 全表给的「**处**」= **字节命中数**（**1 个 U+FFFD 字符 = 3 字节**）⇒ ⇒ 我域那 3 份 = **3 字符 = 9 字节**（与我树实测 9 个字节偏移完全一致）⇒ ⇒ **闭合账（按文件）**：**13 份真损坏**（G4 2 + **G2 3** + G3 5 + G1 3）＋ **4 份故意**（`UserApiGoStyleBodyContentTypeTest` / `ArticleApiMvcPipelineTest` / `docs/Pitfalls/users/user/rpc.md` / `docs/Pitfalls/message/rpc.md`）＋ **3 份无人拥有**（`tools/errmsg-probe/README.md` / `tools/sql-statement-probe/favorite/compare.py` / `component-semantic-verification/springboot-semantic-model.md`）= **20 份文件** ✔（与 G1 的「20 份」吻合）⇒ ⇒ **按字节**：34 + 6 + 11 = **51 字节 = 17 个 U+FFFD 字符** ✔ ⇒ ⇒ ⚠️ **G1 表头写的「9 份真损坏」与表体 13 行不闭合 ⇒ 正确值是 13** ⇒ ⇒ **分类计数必须能加回去**（加不回去 ⇒ 说明分类边界有漏）⇒ ⇒ **建议表头写「20 份文件 / 51 字节 = 17 个 U+FFFD 字符」并标明单位**（否则「份」与「处」两个单位混用）
⇒ ⇒ 📌 **「无人拥有」那 3 份的处置模式（我的建议，供整合者拍板）**：**owner 域出修法 + 整合者执行** ⇒ ⇒ 理由：① **内容归属** = `tools/sql-statement-probe/favorite/` 整个目录只服务 **favorite 域**（其 README 写明「favorite 域」· 补上 favorite 域四层验证的最后一层）⇒ ⇒ **内容属 G3**；② **路径归属** = `tools/**` **不在任何 lane 的写权白名单**（实测 `check-wave-lane-ownership.py` 的白名单里没有它）⇒ ⇒ **路径属整合者** ⇒ ⇒ **两者不冲突、也不是二选一** ⇒ ⇒ **别让 G3 去改一个它改不了的文件**（那会变成「有 owner、无写权」的悬空项）⇒ ⇒ **同理**：`tools/errmsg-probe/README.md` 与根目录 `component-semantic-verification/springboot-semantic-model.md` ⇒ ⇒ **三份统一交整合者 / global 拍板**
⇒ ⇒ 🆕 **订正注记（2026-10-27 18:4x · 第十二轮）**：**这 3 份已全部修复** —— G4 的 `cc6e38e`（`tools/**` 那 2 份）＋ `f68a9cd`（`component-semantic-verification/springboot-semantic-model.md` 那 1 份）随合并进入本树 ⇒ ⇒ **我树复扫：源级 15 份 / 38 字节 ⇒ 12 份 / 27 字节（−3 份 / −11 字节）** ⇒ ⇒ **本清单自此清空**（**上方原值保留不改写**，作为处置过程的留档）⇒ ⇒ **「owner 域出修法 + 整合者执行」这个处置模式本身成立且已用掉** ⇒ ⇒ **剩下的 12 份 = 别的域的真损坏（各域自认领）＋ 4 份故意保留**，本域写权面 = **0**
⇒ ⇒ 📌 **「各 lane 认领自己那几份」的原则（不代发）**：G1 那 3 份 = G1 树、G2 那 3 份 = G2 树、G3 那 5 份 = G3 树、G4 那 2 份 = G4 树 ⇒ ⇒ **owner 自己认领自己的那几份**（**代发者不是 owner** ⇒ 与「假转移单」同族）⇒ ⇒ **只有「无人拥有」那 3 份 + 全仓总表需要整合者持有** ⇒ ⇒ **一份权威表 + 合并后逐文件复扫**（不是逐份数命中数 ⇒ 「份数相同 ≠ 同一批文件」）



**⑦ 🟢 口径澄清（第十七轮收口实测）**
- 本域的产物层守门 `LikeRpcArtifactClasspathGateTest` 是**普通 `*Test`**（它**自己构建** fat jar）⇒ ⇒ **它在 `mvn test` 阶段就跑** ⇒ ⇒ **已在第十六轮那个 995 口径内**（不像 `*IT` 那样要等 `verify`）。
- 但我域 9 模块里**唯一的 `*IT`** 是 `task-rpc` 的 `TaskRpcFatJarArtifactGateTest`（**5 条**，第十七轮 +1）⇒ ⇒ 它**只在 `verify` 阶段跑** ⇒ ⇒ **同一 9 模块的 `test` + `verify` 口径 = 995 + 5 = 1000** ⇒ ⇒ **口径必须写清，否则会把两个数字混着用**。

**⑧ ✅ task IT 那条守门的变异验证（产物 zip 手术）—— 供本域复用同款手法**
- **为什么 task 不能用本域那条「去掉 `-am`」的变异路径**：task-rpc 的测试类引用了共享层**后来新增的 API**（`GoStyleRpcProtectionChain.layerNames(...)` / `.compose(...)`）⇒ ⇒ 拿 `~/.m2` 的陈旧 `common` jar 构建 ⇒ **测试编译就挂** ⇒ ⇒ **这反过来独立印证了「单模块构建吃陈旧 common jar」**。
- ⇒ ⇒ **改用产物 zip 手术**：把 fat jar 里 `BOOT-INF/lib/common-0.0.1-SNAPSHOT.jar` 换成 `~/.m2` 那份陈旧包（实测两边 sha256 逐字节相同 = `45f41836d945347e5bed288e9573c06d` ⇒ 它的 `imports` 只有 2 行、排序桥 0 行）⇒ ⇒ 再**单独**跑那条 IT（`mvn surefire:test -Dtest='...'` —— **不跑生命周期** ⇒ 手术结果不会被 `package` 覆盖）⇒ ⇒ **5 条恰红 1 条**，红在**目标断言** `:205` ⇒ ⇒ **非假绿** ⇒ ⇒ 还原后 **5 条全绿**。

**⑨ 🔴 残留登记（两条负载敏感型时序 flake，**都不在我域代码里**，但会卡住我域的回归链）**
- **① `task-rpc` 的 `TaskRedisDisconnectFastRejectGateTest` ③ B 组**：一次全量跑实测红（`actual 0L / expected ≥ 2000L`）⇒ **隔离复跑 3 次全绿** ⇒ 判为瞬态；机制 = B 组依赖「客户端还没处理到 FIN」的**竞态窗口** ⇒ ⇒ **该对照组断言不是确定性的** ⇒ ⇒ **只登记、不改断言**。
- **② 共享层 `RpcInterceptorClientDeadlineGateTest`**：实测红（`handler 必须真的被调用过`）⇒ **隔离复跑仍红** ⇒ 机制 = 客户端 deadline 只有 **100 ms** ⇒ ⇒ **共享层的「预算过紧」守门** ⇒ ⇒ 落点 `service/common/**` ⇒ ⇒ **非我写权面 ⇒ 只报 G4**（本轮已报）。
- ⇒ ⇒ **代价**：`service/common` 在我域每次 `-am` 回归的 reactor 里 ⇒ ⇒ **共享层一红 ⇒ 我域 `clean test` 整条链断在我域之前** ⇒ ⇒ 「我域全绿」的前置条件包含「共享层在当前机器负载下能绿」。
---

#### 🆕 第六轮（2026-10-27 13:46–13:58）—— **go-zero 库内路径统一加前缀**（G1 提出的同一件事，我域自查后落码）

> **触发**：G1 在它域把裸库内路径统一成 `go-zero@v1.10.2/<path>`（142 处 / 11 份文件）⇒ ⇒ **我域自查发现同类**（本域 **52 处裸库内路径 / 3 份（api.md 43 ＋ rpc.md 1 ＋ vs-common.md 8） 份台账**）⇒ ⇒ **本轮按同一约定落码**（纯表达层、零行为影响）。

**① 改写结果（实测 · `git` 前后对比，不是推算）**
- 改写前：本域台账 **裸库内路径 52 处** ＋ **已带前缀 14 处**
- 改写后：**裸库内路径 = 0** ＋ **已带前缀 66 处** ⇒ ⇒ **新增前缀 52 处**（52 处裸路径 ＋ 1 处新增全路径引用）
- **纯替换**：**路径改写那部分** = **140 insertions = 140 deletions** ⇒ ⇒ 零行增删（整笔提交还含本段台账文字 ⇒ 那是新增行、不属改写）
- ⚠️ **计数口径**：**整文件正则**（不是逐行 `grep`）⇒ 跨行的路径引用只有整文件口径数得到

**② 判据（实测，不是转述 G1 的说法）**
- 🔴 **订正 G1 的一处表述**：G1 写「真源仓顶层 = **只有 `service/`**」⇒ ⇒ **实测不准确** —— 真源顶层实测 **16 项**（`service` · `api` · `proto` · `doc` · `grafana` · `prometheus` · `go.mod` · `docker-compose.yaml` …）⇒ ⇒ 但 **G1 的核心判据成立**：真源仓里 **`core` / `rest` / `zrpc` / `gateway` / `mcp` 五个首段全部零命中**（`find -maxdepth 2 -type d` 实测）⇒ ⇒ **「首段 ∈ 五者 ⇒ 必是库内路径」成立** ⇒ ⇒ **我按【判据】办，不按【表述】办**
- ✅ **版本锁定**：真源 `go.mod:17` = `github.com/zeromicro/go-zero v1.10.2` ⇒ ⇒ 前缀写 `v1.10.2` 不是自选
- ✅ **不是只按约定改，是逐条验证过**：go-zero v1.10.2 的**模块缓存就在本机**（`~/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2`）⇒ ⇒ 本域 52 条**逐条核「文件存在 + 行号在范围内」** ⇒ ⇒ **52 条通过**、**1 条越界** ⇒ ⇒ 越界那条已订正（见 ④）
- ✅ **内容抽验（3 条，逐行读过，不是只看行号在范围内）**：`go-zero@v1.10.2/zrpc/server.go:144-167` = `setupUnaryInterceptors`（recover:149 / breaker:158 / shedding:160-163 ⇒ **与我台账的链序结论逐字吻合**）· `go-zero@v1.10.2/rest/httpx/util.go:15` = `maxFormParamCount = 2048`（⇒ 与我域「两侧都不可达」的判据吻合）· `go-zero@v1.10.2/core/breaker/googlebreaker.go:52-79` = `accept()`

**③ `internal/` 那一类：逐处判归属 ⇒ 42 处全部是真源仓组件内路径 ⇒ 一处不加前缀**
- `internal/` 是**唯一两个仓都有的顶层目录** ⇒ 裸写无法机械区分 ⇒ ⇒ 本域按三问判：① `<libdir>/internal/<rest>` 在模块缓存里存在吗？② 否则真源仓里有同后缀文件吗？③ 否则未决 ⇒ ⇒ 实测 **库内 0 / 真源 42 / 未决 0** ⇒ ⇒ **全部是真源仓**（如 `internal/logic/helper.go` · `internal/errcode/grpc.go` · `internal/model/message.go`）⇒ ⇒ **语境已含组件，不构成误读 ⇒ 不动**
- ⚠️ **附带辨析**：像 `go-zero@v1.10.2/zrpc/internal/codes/accept.go` 这种**已带父目录段**的 ⇒ 它本来就命中「首段 ∈ 五者」⇒ **已在前缀规则内** ⇒ **不是「裸 `internal/`」那一类**

**④ 顺带订正 3 处引用缺陷（结论不变、指针订正；保留原值 + 订正注记）**
- 原写 **`go-zero@v1.10.2/rest/handler/breakerhandler.go` 的区间 19–44** ⇒ ⇒ **该文件只有 42 行** ⇒ 上界 **越界** ⇒ ⇒ 订正为 **19–41**（函数体 18–41）
- `breakerhandler.go:39` ⇒ ⇒ `:39` 实为 `next.ServeHTTP(cw, r)`；「`cw.Code < 500 ⇒ Accept()`」的真身在 **`:33`**（`cw.Code < http.StatusInternalServerError`）＋ **`:34`**（`promise.Accept()`），`:36` 是 `promise.Reject(...)` ⇒ ⇒ 订正为 **`go-zero@v1.10.2/rest/handler/breakerhandler.go:33-34`** ⇒ ⇒ **结论本身成立，只是指错了行**
- 另 1 处**既有双引用**（外层散文已写 `go-zero@v1.10.2`、内层代码块又是裸路径）⇒ 改写后会变双前缀 ⇒ ⇒ **收敛成一处**

**⑤ 门禁前后逐项相同 ⇒ 零行为影响（实测）**
- `tools/check-ledger-selfcheck.py`：**EXIT=0** · 台账 file:line 引用 **134** 条 · 引用不悬空 **114** · 已去噪 **20** · 跨库歧义 **29** ⇒ ⇒ **改写前后这四个数字完全一致** ⇒ ⇒ 改写没有动到门禁的任何判定
- ⚠️ **口径边界（别把门禁的覆盖当成本域的证据）**：门禁第 4 层的文件集 = `ledger_texts()`（`:243`）→ `LEDGER_FILES.get(domain)` ⇒ ⇒ **默认运行只扫 `LEDGER_FILES['task']` 的 6 份台账** ⇒ ⇒ 覆盖我域的 **`task/rpc.md` ＋ `task/api.md`** ⇒ ⇒ **message / like 两域的台账【不在】该层扫描集内** ⇒ ⇒ 那两域「引用可解析」的证据是**我自己逐条对模块缓存验证**（{BARE} 条逐条核过），**不是门禁给的** ⇒ ⇒ 别把「门禁绿」当成「三域都被门禁核过」
- 其余 3 个标准门禁（`check-ledger-count` · `check-wave-lane-ownership` · `check-flyway-ledger`）**EXIT=0**；写权面 **340 文件字节级 U+FFFD = 0 命中**
- ⚠️ **两个共享门禁仍红**（`check-tomcat-limits.py` · `check-wave-coupling.py`）⇒ **与本轮无关**（`tools/**` 不在我写权）⇒ 仍等 G4

**④-2 🔴 顺带发现【同源缺陷在别的域也存在】⇒ 派单（owner 域自修，我不代改）**
- 我域修掉的那处越界引用，**G3 的 hot 域有一份同源的**：`docs/DRIFT/hot/api.md:1697` ＋ `:1746` 同样写 **`go-zero@v1.10.2/rest/handler/breakerhandler.go` 的区间 19–44** ⇒ ⇒ **global 已在 `docs/DRIFT/global/_README.md:569` 把它登记为 hot 域漂移**（“🔴 **行号越界** —— go-zero `go-zero@v1.10.2/rest/handler/breakerhandler.go` 只有 42 行”）⇒ ⇒ **⇒ 说明这条是跨域同源缺陷，不是本域独有**
- ⇒ ⇒ **我域那份此前【未登记】**（本轮自修：`:19-44` → `:19-41`，另加订正注记）⇒ ⇒ **hot 域那份按 global 的登记走**（owner = G3）⇒ ⇒ **我不代改**（代发者不是 owner ⇒ 同「假转移单」）
- ⇒ ⇒ **顺带**：global 的登记里用的是 **裸 basename**（**原引用** `breakerhandler.go:19-44`）⇒ ⇒ 加上前缀后**越界判定会更准**（裸 basename 靠 basename 索引，可能匹配到别的库同名文件）

**⑥ 🔴 遗留一整类：裸 basename 引用（本域 300 处）⇒ ⇒ 【不机械改】+ 登记**
- 本域裸 basename（形如 `errmsg.go:117-132`）共 **300 处**，分三类（实测）：**库内唯一**（basename 在 go-zero 里唯一）· **库内同名多候选**（如 `config.go` 在库里有 **14** 个同名 · `unmarshaler.go` **3** 个 · `types.go` **2** 个）· **真源仓 / 标准库**（如 `errmsg.go` · `sendchatmessagelogic.go` ⇒ 真源仓里同名）
- ⇒ ⇒ **不能机械改**：机械改会把「真源仓的 `message.go`」误标成「库内的 `go-zero@v1.10.2/core/trace/message.go`」⇒ ⇒ **正确处置 = 登记 + 派单**，不是无脑替换
- ⇒ ⇒ **复开触发条件**：当 G4 把门禁的「精确定位通道」修通（见 ⑦）⇒ ⇒ 那时才有**机器可判**的消歧依据 ⇒ ⇒ 到那时本条才可批量收敛

**⑦ 🔴 工具侧发现（`tools/**` = G4 写权）：门禁第 4 层的「精确定位通道 ① `module@version/...`」从台账文本【永远不可达】**
- 门禁 `:1442-1444` 的注释明写「`module@version/...` ⇒ 直接在 module cache 里找（basename 索引解析不到完整模块路径 ⇒ 假 FAIL；直接定位顺带消除同名歧义）」⇒ ⇒ **设计意图与本轮改写完全一致**
- 🔴 **但它从台账文本进不去**（探针实测 · `/tmp/g2-probe-precise-channel.py`）：`REF_RE`（`:208`）的字符类 `[A-Za-z0-9_./-]` **不含 `@`** ⇒ ⇒ `go-zero@v1.10.2/rest/httpx/util.go:15` 被截断成 **`v1.10.2/rest/httpx/util.go`** ⇒ ⇒ `_direct_module_candidate`（`:1034`）返回 **None** ⇒ ⇒ **短式与全式前缀都触发不到**
- ⇒ ⇒ **根因 = 提取器与解析器的契约不一致**（解析器要 `module@version/path`，提取器却在 `@` 处断掉）⇒ ⇒ **该通道实为死代码**（工具自测 `:1882` 是**直调函数、绕过 `REF_RE`** ⇒ 所以自测过、真跑不通）
- ⇒ ⇒ **修法在 G4**：`REF_RE` 字符类加 `@` ⇒ ⇒ 但**加了之后能触发的必须是全式** `github.com/zeromicro/go-zero@v1.10.2/<path>`（`_direct_module_candidate` 的正则要求模块段**含 `.`** ⇒ 短式 `go-zero` 不满足）⇒ ⇒ **两种写法并存 ⇒ 口径归 G4 / global 拍板**
- ⚠️ **我域的选择**：本轮用**短式** `go-zero@v1.10.2/<path>` ⇒ 理由 = 与 global 台账**既有写法同形**（`docs/DRIFT/global/common-tails.md:51` 等实测）⇒ ⇒ **跨域一致性优先** ⇒ ⇒ **不擅自改跨域约定**

**⑧ 本域当前状态**：落码前 HEAD = `align/g2-message-like-task` @ `05ac555` · **落后 0 / 领先 106** ⇒ ⇒ 本轮提交后 = **领先 107** · 4 个标准门禁 EXIT=0 · 写权面 340 文件 U+FFFD = 0

---

#### 🆕 第七轮（2026-10-27 14:1x）—— **A9 端到端闭环（FB-04 的那「另一半」）** ＋ like-rpc **115 ⇒ 118**

**① 为什么需要这一条（已有守门覆盖不到的那一半）**
- `LikeSpansAttributeGateTest`（11 条）用 `runInSpan(...)` **自造一个 span** 再把域服务塞进去跑 ⇒ ⇒ 它验的是「**域侧写法**」（属性会被打到「当前 span」上），**不是**「**真链路上 handler 执行期的当前 span 就是 server span**」
- ⇒ ⇒ **FB-04（`853767b`）落地之前**：handler 执行期 `Context.current()` 是**根 context** ⇒ `Span.current()` = **invalid（no-op）span** ⇒ 属性**静默丢弃** ⇒ ⇒ **域侧那条守门照样全绿**（它自己造了 span）⇒ ⇒ 「**跑绿但零效果**」
- ⇒ ⇒ FB-04 已随合并入我树（`37816ca`）⇒ ⇒ **这「另一半」现在可验了** ⇒ ⇒ 本轮补上

**② 新守门 `LikeA9GrpcSpanEndToEndTest`（3 条）· 真链路 ＋ 对照组 A/B**
- **真链路**：真 **in-process gRPC**（`InProcessServerBuilder` ＋ `LikeServiceGrpc.bindService(new LikeServiceGrpcImpl(真域服务))`）＋ **共享层真的** `RpcInterceptor.newUnaryServerInterceptor(2s, 1s)` ⇒ ⇒ 真 OTel SDK ＋ 真 exporter ⇒ ⇒ 断言**导出的 server span** 上有 `query.target_type=article` / `query.batch_size=2` / `cache.hits=0` / `cache.misses=2`
- **对照组（本类内联）**：**同一份域服务**、**同一通真实 gRPC 调用**，只把 server 拦截器换成「**复刻 FB-04 之前行为**」的那个（起 SERVER span、但**只在 `close()` 里** `makeCurrent()`，**handler 执行期不包 scope** ⇒ 与共享层 javadoc 记的旧形态一致）⇒ ⇒ **属性必须被丢弃**
- ⇒ ⇒ **A/B 之间唯一差别就是共享层那个机制** ⇒ ⇒ **这才是「机制是承重的」的证据**，而不是「代码写成了这样」
- **护栏（第 3 条）**：锁住「共享层那个静态工厂必须仍能产出 server 拦截器」⇒ ⇒ 防止将来共享层改了实现、而本文件还在测「域侧写法」

**③ 变异验证（红在目标断言）**
- 变异 = **把对照组的 scope 包回去**（让它不再丢弃属性）⇒ ⇒ 这比「改断言值」更强：它证明**对照组真的会区分**
- 结果：**3 条里只红 1 条**，红点**精确**在 `attributesAreDroppedWithoutTheSharedLayerMechanism:327` ⇒ 报错 `expected: <null> but was: <article>` ⇒ ⇒ **断言读的是运行期真值**
- ⇒ ⇒ **另 2 条（真链路 ＋ 护栏）仍绿** ⇒ ⇒ **不是整体崩、不是恒绿**
- ⇒ ⇒ 还原**逐字节一致**（md5 `f56a6b28…` 前后相同）⇒ ⇒ 复跑 **3 条绿**

**④ 数字：like-rpc 115 ⇒ 118**
- **115 ⇒ 118** = 本轮新增 `LikeA9GrpcSpanEndToEndTest` **3 条** ⇒ +3
- **实测**：`mvn -o -B -DargLine=-Xmx2g -pl service/common,service/like/like-rpc test` ⇒ **118 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS · 2:36 min**（14:1x）⇒ ⇒ surefire **mtime 切分口径 = 20 份报告 / 118 条** ⇒ **与 maven 汇总行相等** ✔
- ⚠️ **`service/common` 必须在 reactor 里** —— `~/.m2` 的 `common` jar 里 `makeCurrent` **只有 1 处**（**不含 FB-04**）⇒ ⇒ 只跑 like-rpc 会编译到**旧**共享层 ⇒ **真链路那条必然红** ⇒ ⇒ 「`~/.m2` 的 jar ≠ 源码」的老坑 ⇒ ⇒ **不 install、只在 reactor 内解决**（会改变别的 lane 正在解析的依赖）

**⑤ §5.3e 项⑥ 销号 ＋ A9 轴的证据升级**
- ⇒ ⇒ **A9 轴由「域侧写法已验（自造 span）」升级为「真链路端到端已验（真 gRPC ＋ 对照组 A/B ＋ 变异验证）」** ⇒ ⇒ **FB-04 的收益第一次被本域实测坐实**
- ⇒ ⇒ ⚠️ **仍不写「双侧活体闭环」**：Go 侧 span 属性是**源码级逐条对照（L3）** ⇒ ⇒ 跨侧逐属性活体比对**未做** ⇒ ⇒ 本轴 = **RAG 侧 L4（真链路端到端）＋ Go 侧 L3（源码）**

---

#### 🆕 第八轮（2026-10-27 14:3x）—— **§5.3e 项② 销号：共享层那份 `REJECT_COMMANDS` 在本域【真的生效】**（活体断连 A/B）

**① 这条补的是哪一个缺口（别当成「新做一遍」）**
- 我域上轮已做过一次**真 Redis 容器**的断连实测（记在 `LikeRpcRedisTimeoutsGateTest` 的 javadoc 对拍表里）：
  Go 侧 go-redis **拒连快速失败 = 1694 ms** · RAG 侧未配 timeout **排队等重连 = 60021 ms** · RAG 侧配 3s **快速失败 = 3 ms**
- ⇒ ⇒ **但那次的条件是「手动配 `REJECT_COMMANDS`」** ⇒ ⇒ 它证明的是「**该配置项有效**」，
  **不是**「**共享层那份已在本域生效**」⇒ ⇒ **本条补的就是这一格**

**② 新守门 `LikeRedisDisconnectFastRejectGateTest`（2 条）**
- **① 共享层那份 customizer 在场且【没被域内副本遮蔽】**：
  `getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` 的 **size == 1** ＋ bean 名含
  **`goStyleRedisClientOptionsCustomizer`** ⇒ ⇒ 共享层那份带 `@ConditionalOnMissingBean`
  ⇒ ⇒ **size==1 ＋ 名字对得上**两条合起来才证明「用的就是共享层那份、**没有域内副本遮蔽**」
- **② 断连行为真被设成 `REJECT_COMMANDS`**：**消费点** =
  `connectionFactory.getNativeClient().getOptions().getDisconnectedBehavior()`
  ⇒ ⇒ **不是读配置项**（那是「声明」），也不是读 `ClientConfiguration`
- **③ 断连活体 A/B**（真 docker redis 容器）：连上 → PING → **静默等待（期间不发命令，否则 watchdog 重连会掩盖排队窗口）**
  → `docker stop`（优雅 FIN，不是 kill 的 RST）→ 量**第一条命令**的耗时
  - **A 组 = 共享层产出的那套 ClientOptions** ⇒ **实测 0–1 ms**
  - **B 组 = 同一套、只把断连行为换成 Lettuce 默认的 `DEFAULT`** ⇒ **实测 3004–3016 ms**（排队到 3s 命令超时）
  - ⇒ ⇒ **A/B 唯一差别就是那条机制** ⇒ ⇒ **这才是「机制是承重的」的证据**，而不是「反正都快」

**③ 变异验证（红在目标断言）**
- 变异 = **把 A 组也换成 `DEFAULT`**（模拟「共享层那份没生效」）⇒ ⇒ 比「改断言值」更强
- 结果：**2 条里只红 1 条**，红点**精确**在 `disconnectIsFastRejectedWithSharedLayerOptions:201`；
  且探针打印出 **A=3005 ms / B=3003 ms** ⇒ ⇒ **测量确实跟着机制走**（不是碰巧）
- ⇒ ⇒ 还原**逐字节一致**（md5 `2fa201c2…` 前后相同）⇒ ⇒ 复跑 **2 条绿**（A=0 ms / B=3009 ms）

**④ 数字：Like-rpc 118 ⇒ 120（+2 = 本条 2 条）**
- 实测：`mvn -o -B -DargLine=-Xmx2g -pl service/common,service/like/rpc test`
  ⇒ **120 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS**（mtime 切分口径 **21 份报告 / 120 条**
  ⇒ **与 maven 汇总行相等** ✔）
- ⚠️ **`service/common` 必须在 reactor 里** —— `~/.m2` 的 `common` jar 是**旧**的（不含 FB-04 / 不含共享层的最新装配）⇒ ⇒ 只能**在 reactor 内**解决，**不 `install`**（那会改变别的 lane 正在解析的依赖）

**⑤ ⚠️ 一条「不外推」的纪律（本轮两个域各做了一遍）**
- like 域与 task 域**各跑了自己的活体 A/B**，**没有**拿一个域的结论去覆盖另一个域
  ⇒ ⇒ 理由：**域上下文不同 ⇒ customizer 数量可能不同 ⇒ 行为可能不同** ⇒ ⇒ 「共享层那个 bean 一样」**推不出**「两个域的行为一样」
- ⇒ ⇒ **全仓实测**：`LettuceClientOptionsBuilderCustomizer` 的**域内副本只有 1 处** —— 在 **users 域**
  （`service/users/user/user-api/.../RedisDisconnectedBehaviorConfig.java:103`）⇒ ⇒ **我三域零遮蔽副本** ✔
  ⇒ ⇒ （users 域那份**会遮蔽**共享层 ⇒ 那是 G1 域的事 ⇒ 本域不代改）

---

## 4·九、🔵 **第九轮（2026-10-27 17:1x）· G1 来的「波次级通用判据」⇒ 本域侧逐条处置**（G2 全部**独立取证**，不采信转述）

### ① 🟡 **命令超时判据联动**（**同意 G1 的口径；但「联动」的真实机制与 G1 写的不一样** —— 已从 Boot 4.0.8 **源码**读出）

- **两域守门现状**（实测）：like 域 `LikeRpcRedisTimeoutsGateTest` **6 条**（①②③④ 超时相关 ＋ ⑤⑥ 池）· task 域 `TaskRedisCommandTimeoutGateTest` **6 条**（同结构）⇒ ⇒ **超时相关 = 各 4 条，合计 8 条**（与 G1 口径一致）· **两域合计 12 条**
- **判据链两域均已落地**（**不是「没做」**）：
  - like ① `isTimeoutCommands()` ＋ `isApplyConnectionTimeout()`（两个机制位合起来 = `TimeoutOptions.enabled()` 的全貌）· ② `getClientConfiguration().getCommandTimeout() == 3s`（消费点）· ④ **阳性对照** `RedisURI.create(...)` 默认 `getTimeout() == 60s`（证明断言有识别能力）
  - task 同构：① 两机制位 · ② `socketOptions.connectTimeout == 5s` · ③ `RedisURI.timeout == 3s` · ④ 60s 阳性对照
- ✅ **同意 G1 的口径**：`isTimeoutCommands()` **单独**不构成「时长会被施加」的判据 ⇒ 本域两份守门已把它定位成「机制位①」· **时长另由「消费点」那条覆盖** ⇒ ⇒ **不是「恒真 ⇒ 无效」，而是「单独不足」** ⇒ 两腿合起来 = 完整链
- 🔴 **但「联动」的真实风险面不是 G1 写的那条** —— G2 **从 Boot 4.0.8 源码**独立读出装配顺序（`~/.m2/.../spring-boot-data-redis-4.0.8-sources.jar` ⇒ `LettuceConnectionConfiguration`）：
  - `:208-210` `createClientOptions(ObjectProvider<LettuceClientOptionsBuilderCustomizer> clientConfigurationBuilderCustomizers, …)`
  - `:211` `ClientOptions.Builder builder = initializeClientOptionsBuilder();`
  - `:212-215` `connectTimeout` ⇒ `builder.socketOptions(SocketOptions.builder().connectTimeout(connectTimeout).build())`
  - `:229` `builder.timeoutOptions(TimeoutOptions.enabled());`
  - **`:230` `clientConfigurationBuilderCustomizers.orderedStream().forEach(c -> c.customize(builder));` ⇒ customizer 在最后执行**
  - ⇒ ⇒ `ClientOptions.Builder` 的 `timeoutOptions(...)` / `socketOptions(...)` 是**替换**语义 ⇒ 共享层那份 customizer 一旦调它们 ⇒ **整体覆盖** Boot 刚设的值
  - ⇒ ⇒ **可达性分级**（本域判据的真实风险面）：

    | 本域判据 | 落点 | 共享层 customizer 能否覆盖 |
    |---|---|---|
    | 命令超时 3s | `LettuceClientConfiguration.Builder.commandTimeout`（`:174`）⇒ `getCommandTimeout()` ⇒ `RedisURI.timeout` | **不能**（不是同一个 builder） |
    | 连接超时 5s | `ClientOptions.Builder.socketOptions`（`:213-214`） | **能**（`:230` 在其后） |
    | 机制位 `timeoutOptions.enabled()` | `ClientOptions.Builder.timeoutOptions`（`:229`） | **能**（`:230` 在其后 ⇒ 覆盖 ⇒ `applyConnectionTimeout` 位丢失） |

  - ⇒ ⇒ **联动条款（登记 · 跨域）**：**共享层 `GoStyleRedisClientAutoConfiguration` 若未来要碰 `timeoutOptions(...)` / `socketOptions(...)`（E66「Redis 命令感知 deadline」正是这条路）⇒ 必须与受影响域的守门同一次提交一起改** ⇒ ⇒ 否则 like-rpc / task-rpc 的 ①②③ 与连接超时 5s 会**同时**变红 ⇒ ⇒ **触发条件 = 共享层 customizer 新增上述调用**

### ② ✅ **`-pl` 不带 `-am` ⇒ classpath 上是 `~/.m2` 的旧 `com.rag:common`**（G1 实测踩到）—— **本域已规避**

- G1 的实测现象：`-pl <域内模块>`（**不带 `-am`**）⇒ reactor 里**没有** `service/common` ⇒ `com.rag:common` **从本地仓库解析** ⇒ 那个 jar 是**主干代码态** ⇒ ⇒ **共享层改动根本没进过测试 classpath** ⇒ ⇒ 「跑绿」是**假绿**
- ✅ **本域全部回归命令都显式把 `service/common` 放进 `-pl`** ⇒ 等效于带 `-am`（用**本树源码**）⇒ ⇒ **本域不受该坑影响**
  - 实测命令形如：`mvn -o -B -DargLine=-Xmx2g -pl service/common,service/like/like-rpc test`
  - ⚠️ **不 `install`** 共享层 jar 到 `~/.m2`（那会改变别的 lane 正在解析的依赖）
- ⚠️ **口径纪律**：`service/common` 在 reactor 里 ⇒ **reactor 总数 ≠ 本域数**（共享层自己的 **324** 条会进汇总）⇒ ⇒ **本域数字按本域模块的 surefire 目录**统计（`service/{message,like,task}/**/target/surefire-reports/TEST-*.xml`）⇒ ⇒ **不把 reactor 总数当本域数**

### ③ 🟢 **G1 [3]「连带删 `@Import` 不够」—— 本域实测「不适用」**

- G1 的坑：守门用 `@SpringBootConfiguration`（**不含** `@EnableAutoConfiguration`）⇒ **自动配置清单不会被读** ⇒ 单纯删 `@Import` ⇒ 容器里 customizer = 0 ⇒ ⇒ **守门静默测不到任何断连策略**（零报错）
- ✅ **本域实测不适用**：`service/{message,like,task}/**` 内 **`@SpringBootConfiguration` = 0 命中** ⇒ ⇒ 本域**没有**「最小上下文」守门 ⇒ ⇒ 该坑不适用
- ✅ **本域两个新 Redis 守门用的是真应用类**：`@SpringBootTest(classes = LikeRpcApplication.class)` / `TaskRpcApplication.class`（**真 `@SpringBootApplication`** ⇒ 含 `@EnableAutoConfiguration` ⇒ **清单会被读**）⇒ ⇒ 且已用 **`size()==1` ＋ bean 名含 `goStyleRedisClientOptionsCustomizer`** 双重断言坐实「共享层那份确实在容器里」⇒ ⇒ **即使清单没被读，断言也会红** ⇒ ⇒ **不会静默测不到**

### ④ ⚠️ **U+FFFD 跨树数字不可直接比**（G1 合并后全仓 = **23 份 / 64 处**）

- G1 报：合并后全仓 **23 份 / 64 处**（原 20 份 / 51 处）⇒ 增量来自 G1 台账**引述了坏字节**（把「真损坏」现场当证据写进台账）⇒ ⇒ G1 域真损坏 = 0
- **G2 本树口径**：全仓 **4103 份**扫描 ⇒ 含 `target/` **18 份 / 60 字节** · 剔 `target/` **15 份 / 38 字节**（源级）
- ⇒ ⇒ **两个口径（份数 / 处数）与构成都不同** ⇒ ⇒ **复扫要比「文件清单」而不是比数字** ⇒ ⇒ **本域写权面 340 文件 = 0 命中**

### ⑤ ✅ **`application.yaml` 不改名（G1 (b)）—— 本域同意，走「台账登记 ＋ 复开触发条件」**

- **同意 G1 的三条理由**：收益仅消除 **1 个**命名误判点 / 成本 ≈ **42 处**引用且其中 3 处 ＋ `docs/Pitfalls/users/_shared.md` 2 处**不在任何 lane 白名单** / 扩展名是**纯表达层、零语义收益**
- **本域现状**：`service/{message,like,task}/**/src/main/resources/application.yml`（`.yml`）⇒ ⇒ 本轮**零改动**
- **复开触发条件**（本域侧登记）：当出现「**按扩展名分派的机器判定**」成为某个门禁 / 脚本的**正确性前提**时（例如某个守门开始区分 `application.yml` 与 `application.yaml`）⇒ ⇒ 届时重开该议题

---

---

## 4·十、🟢 **第十轮（2026-10-27 17:3x）· §5.3e 项⑤ 销号 —— A3「路径归一」的**真 Tomcat** 探针**（共享层那层已在本树 ⇒ 本域自己补上探针）

### ① 交付物（**本域自己的**，不外推）
- `LikeApiPathNormalizationRealTomcatTest`（**3 条**）· `LikeApiPathNormalizationControlTest`（**2 条**）⇒ ⇒ **A/B 两类**
- ⚠️ **不外推**：like / message 两域**各做了一遍自己的 A/B** ⇒ ⇒ **三域各 5 条 = 15 条**（不是「一个域跑一遍、三域通用」）

### ② 判据（三段，每段都落到消费点）
① **共享层那份在、且没被域内同名 Bean 遮蔽** ⇒ `context.containsBean("goStylePathNormalizingFilter")` ＋ 按名取到的**类型**必须是 `com.rag.common.http.GoStylePathNormalizingFilter` ＋ `@Order` = `HIGHEST_PRECEDENCE + 2`
② **真 Tomcat ＋ 真请求** ⇒ `DEFINED_PORT` ＋ 本域专用端口段 `21600-21699`（`RealTomcatPort`）＋ **`HttpURLConnection`**
   - ⚠️ **为什么不能用 `java.net.http`**：它的 `URI` 构造器**自己就规范化路径**（`URI.create("…/a/../b")` ⇒ `…/b`）⇒ ⇒ 变体在**发出去之前**就被抹平 ⇒ ⇒ 测到的会是「规范路径」⇒ ⇒ **探针失效**
   - ⚠️ **`java.net.URL` 不校验也不规范化** ⇒ 请求行里原样带上变体（与 Go 的 `net/http` 同形：`r.URL.Path` 拿到的是客户端发的**原样**路径）
③ **变体 ⇒ 与规范路径同状态、且 ≠ 404**（`404` = 压根没匹配到路由）

### ③ 实测矩阵（**三域一致**）
| 域 | 规范路径 | 4 种变体（**A 组**：归一层在） | 4 种变体（**B 组**：归一层缺席） |
|---|---|---|---|
| task-api | 200 | **200 ×4** | **404 ×4** |
| like-api | 401 | **401 ×4** | **404 ×4** |
| message-api | 401 | **401 ×4** | **404 ×4** |

- 4 种变体 = **尾斜杠** · **重复斜杠** · **`.` 段** · **`..` 段**
- ⇒ ⇒ **Tomcat / Spring 自己不会归一这四种形态** ⇒ ⇒ **共享层那层是全部四种变体的承重件**（不是「Tomcat 顺手干了」）
- ⇒ ⇒ **B 组 = 同一份域服务 ＋ 同一通真实请求，唯一差别是「那层机制在不在」**（`spring.autoconfigure.exclude=…GoStylePathNormalizingAutoConfiguration`）⇒ ⇒ **A 组的绿不是空断言**

### ④ 变异验证（**三域各做一遍**，红在目标断言）
- 变异 = 在域内放一个**同名但什么都不做**的 Filter Bean（`@Component("goStylePathNormalizingFilter")`）⇒ ⇒ 共享层那份被 `@ConditionalOnMissingBean(name=…)` **跳过**（= **遮蔽**，不是叠加）
- ⇒ ⇒ **这不是人造的失效形态**：users 域的「域内副本遮蔽共享层那份」就是同款形态 ⇒ ⇒ 变异复现的是**真实隐患**
- 结果（三域**完全一致**）：**3 条里红 2 条**、**`Errors = 0`** ⇒ ⇒ **上下文正常启动**（不是启动期炸）⇒ 红点精确：
  - `sharedLayerNormalizingFilterIsPresent`（实测类型 = 域内影子 Bean）
  - `pathVariantsReachTheSameRoute`（变体 404）
  - **仍绿 1 条** = 规范路径照通 ⇒ ⇒ 坏的只有归一，不是路由本身
- ⇒ ⇒ 还原**逐字节一致**（三个 md5 与变异前完全相同）⇒ ⇒ 复跑全绿

### ⑤ 🔴 变异暴露的一条**设计缺陷**（已修 · 值得留档）
- 第一版用 `@Autowired GoStylePathNormalizingFilter`（**具体类型**）⇒ ⇒ 遮蔽时**上下文启动期就炸**（`NoSuchBeanDefinitionException`）⇒ ⇒ 守门会「**红在启动**」而不是「红在断言」⇒ ⇒ 看不出是哪条判据挂了 ⇒ ⇒ 无法区分「压根没这层」与「这层不对」
- ⇒ ⇒ **改成按 Bean 名 ＋ 断言类型**（`containsBean` ＋ `getBean` ＋ `instanceof`）⇒ ⇒ 遮蔽现在**红在断言上**（实测三域一致）
- ⇒ ⇒ **可复用的判据**：**守门的前置断言不要用「注入具体类型」**，否则一旦机制缺席，失败点会前移到容器启动期 ⇒ ⇒ **看不出是哪条判据挂了**

### ⑥ 与 Go 的对照口径（**不外推 · 不整体写 L4**）
- **Go 出处**：`rest/router/patrouter.go:48`（注册时 Clean）· `:60`（匹配时 Clean）⇒ ⇒ 变体命中同一条路由
- ⚠️ **Go 侧的 L4 活体证据是共享层那一份**（真 go-zero `rest` server ＋ curl：5 种变体全 200 · 2026-10-27）⇒ ⇒ **那不是本域独立跑的 Go 腿**
- ⇒ ⇒ **本轮补的是「RAG 腿」**：本域上下文里**那层真的承重**（真容器 ＋ A/B ＋ 变异验证）⇒ ⇒ **A3 轴的「路径归一」这一格 = RAG 腿 L4**；**Go 腿 = 共享层那一次的活体（不是本域独立跑的）** ⇒ ⇒ 台账按此口径记，**不整体升 L4**

### ⑦ §5.3e 清单更新
| 项 | 状态 |
|---|---|
| ⑤ A3 升 ✅ 的真 Tomcat 探针 | ✅ **本域侧销号**（本轮：真 Tomcat ＋ A/B 对照组 ＋ 三域各自变异验证） |

---

### 🟢 **第十一轮 · 合并后 9 模块回归（刷新 · 2026-10-27 17:55 → 18:0x）**（**上一张 970 的表是 13:31–13:39 那次的历史记录 ⇒ 原值保留不改写**）

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**合并进来的共享层**） | 324 | 0 | 0 | 0 |
| `service/message/message-api` | **103** | 0 | 0 | 0 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | **94** | 0 | 0 | 0 |
| `service/like/like-rpc` | **120** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | **102** | 0 | 0 | 0 |
| `service/task/task-rpc` | **121** | 0 | 0 | 0 |
| **合计** | **992** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 668**（相对 970 那次的 646 ⇒ **+22**）⇒ ⇒ **逐条对得上本轮新增的用例**：
**like-rpc +5**（115→120 · A9 端到端 3 条 ＋ Redis 断连 2 条）· **task-rpc +2**（119→121 · Redis 断连 2 条）· **三域 api 各 +5**（第十轮 A3 探针：message-api 98→103 · like-api 89→94 · task-api 97→102）⇒ ⇒ **22 = 5 + 2 + 5 + 5 + 5** ✔
⇒ ⇒ **口径**：surefire XML **mtime 切**求和（**156 份报告**）**== maven 9 模块汇总行**（**逐字相等**）⇒ ⇒ **0 stale 报告**
⇒ ⇒ ⚠️ **本轮踩到的一个「stale 产物污染计数」的实例（值得留档）**：第十轮做**变异验证**时跑出了 **task-api failures = 2** 的报告 ⇒ ⇒ `check-ledger-selfcheck.py` 第 3 层当场报「❌ task-api failures/errors/skipped 全 0」⇒ ⇒ **根因不是代码坏了，是变异跑的失败报告还留在 `target/surefire-reports/`** ⇒ ⇒ 本轮全量回归把 task-api 重跑一遍 ⇒ ⇒ **failures 归 0、门禁恢复绿** ⇒ ⇒ ⇒ **判据**：门禁读的是 `target/` 里的报告 ⇒ ⇒ **任何一次带 `-Dtest=` 的定向跑都会改写/污染它** ⇒ ⇒ **跑门禁前必须有一次覆盖该模块的全量跑**（否则门禁报的是「上一次跑剩的产物」，不是当前代码态）

### 🟢 **第十六轮 · C7 波末 9 模块回归（实测 · 2026-10-27 18:4x → 19:0x）** —— **把「995 是推算」升级为实测**

| 模块 | tests | failures | errors | skipped | 报告数 |
|---|---|---|---|---|---|
| `service/common`（**共享层**） | **327** | 0 | 0 | 0 | 62 |
| `service/message/message-api` | 103 | 0 | 0 | 0 | 15 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 | 20 |
| `service/message/message-common` | 21 | 0 | 0 | 0 | 2 |
| `service/like/like-api` | **94** | 0 | 0 | 0 | 11 |
| `service/like/like-rpc` | **120** | 0 | 0 | 0 | 21 |
| `service/like/like-common` | **18** | 0 | 0 | 0 | 2 |
| `service/task/task-api` | 102 | 0 | 0 | 0 | 9 |
| `service/task/task-rpc` | 121 | 0 | 0 | 0 | 15 |
| **合计** | **995** | **0** | **0** | **0** | **157** |

⇒ ⇒ **域侧 8 模块 = 668**（与第十一轮**逐条相同**）＋ **共享层 324 ⇒ 327**（+3 = G4 的新机器门禁 `GoStyleRedisClientShadowingGateTest` 3 条）⇒ ⇒ **9 模块总计 995** ⇒ ⇒ ✅ **第十一轮那句「995 是推算，不是实测」** ⇒ ⇒ **本轮实测证实该推算值** ⇒ ⇒ **995 从此是实测值**（**上一张 992 的表原值保留不改写**）
⇒ ⇒ **口径（比上一轮更严，三条）**：① **命令带 `-am` + `clean`** ⇒ `mvn -o -B -DargLine=-Xmx2g -am -pl <9 模块> clean test` ⇒ ⇒ **reactor 实跑 23 个模块**（`-am` 自动带入 `users-common` / `user` / `user-proto` / `message` / `message-proto` / `like` / `like-proto` / `task` / `task-proto`）⇒ ⇒ ⚠️ **这是必须的**：`service/message/message-api/pom.xml:55` 依赖 **`users-common`**（**不在我 9 模块表里**）⇒ ⇒ **不带 `-am` ⇒ 它从 `~/.m2` 解析旧 jar ⇒ 测的不是本树代码**（`mvn -pl` 不带 `-am` 的经典陷阱）② **权威口径 = 按本域模块的 surefire 目录逐文件求和**（`TEST-*.xml` 的 `testsuite` 属性）⇒ **逐模块 == maven 汇总行**（**9/9 逐字相等**）③ **`clean` 不可省** ⇒ 删过源/资源的模块会留 stale `target/classes` ⇒ 门禁与计数都会读到残留
⇒ ⇒ **157 份报告全部本轮生成**（`clean` 已清空旧目录 ⇒ **0 stale 报告**）
⇒ ⇒ ⚠️ **两个共享门禁仍红（非本轮引入 · 非我写权面）**：`check-tomcat-limits.py`（期望表把 task-api 写死 `raw`，而权威语义表 `GoStyleFormLimitMode.java:237` = `Mode.OFF` ⇒ **期望表与权威表自相矛盾**）· `check-wave-coupling.py`（**9 项漂移** ⇒ 快照须在四 lane 全合后由整合者重生成）⇒ ⇒ **两条都不是我域能修的**

### 🔴 第十七轮 · 域侧排序桥收编到共享层 ＋ **它揭出来的构建路径缺陷（单模块构建的 fat jar 起不来）**（2026-10-27）

**① 闸门已满足 ⇒ 收编落码（删 2 件）**
- 共享层那份**已在我的树里**：`service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ 注册行 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` **第 4 行** ⇒ ⇒ 本文件 D-6 原写的「共享层 `service/common` 命中数 = 0」**已过期**（那是 03:3x 时的状态）⇒ ⇒ **闸门解除**。
- 删掉：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ `service/like/like-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（**该文件只含排序桥这一行 ⇒ 整份随类一起删**）。
- ⚠️ **合并清单条目（写给整合者）**：我域这 3 个 rpc 模块的 `imports` 都是**「只含一行」**形态 ⇒ ⇒ **整份文件随类一起删**；而 G3 的 `article-rpc` 是**「2 行」**形态 ⇒ ⇒ **只删第 1 行、文件保留** ⇒ ⇒ **两种形态不能一刀切**。
- 🟢 **删前我域自己做的核验（不采信转述）**：① 两份桥**都不注册任何 bean**（类体为空）⇒ ⇒ **无 bean 名遮蔽**；且声明的是**同向**排序边（DataSource 之后、MybatisPlus 之前）⇒ ⇒ 排序图里**多一个同向节点、无环、功能等价** ⇒ ⇒ **删除 = 去重，不是修 bug** ⇒ ⇒ **A1/A9 等轴的证据强度不因本次删除而降级**（⚠️ global 台账 `:1159` 用「被遮蔽」措辞 ⇒ 我域实测的语义是「**同向并存、功能等价**」⇒ 措辞差异已报 G4）② **主代码零 import**（`grep "import .*MybatisPlusOrderingBridgeAutoConfiguration"` = **0 命中**）⇒ ⇒ 删类不留悬空 import ③ **测试里只有 javadoc / 断言文本**（`LikeRpcSpringContextWiringTest.java:38` javadoc · `:181` `@DisplayName` · `:184` 断言消息）⇒ ⇒ **断言的是「效果」（`SqlSessionFactory` 存在）而不是「类在不在」**。

**② 🔴🔴 收编当场揭出来的真缺陷：单模块构建的 fat jar 起不来（跨 lane 的地雷）**
- **现象**：收编后跑 like-rpc ⇒ `LikeRpcRuntimeBootGateTest.realFatJarProcessBootsAndListens` **红** ⇒ 真 fat jar 的 gRPC 端口没监听 ⇒ 进程日志尾部 = `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` ⇒ ⇒ **⇒ 不是 flake、不是端口撞车，是产物层的真缺陷**（判据 = 失败消息里那条 MP 异常，不是「没监听」这个症状）。
- **根因（三层，逐层实测）**：
  ① **域内桥以前在 `BOOT-INF/classes` 里** ⇒ ⇒ **不管内嵌的 `common` jar 多旧，排序边都成立** ⇒ ⇒ **fat jar 一直是绿的** ⇒ ⇒ **它长期掩盖着下面这条缺陷**；
  ② **构建 fat jar 的那条命令不带 `-am`**（`LikeRpcArtifactClasspathGateTest.buildFatJar()`）⇒ ⇒ `service/common` **不在 reactor 里** ⇒ ⇒ 从 **`~/.m2`** 取 jar ⇒ ⇒ 实测 `~/.m2/.../common-0.0.1-SNAPSHOT.jar`（**12:00 的旧包**）的 `AutoConfiguration.imports` 里**排序桥行数 = 0** ⇒ ⇒ fat jar 里嵌的就是它（实测 `BOOT-INF/lib/common-*.jar` = **215837 B / 03:51** = 同一个旧包）⇒ ⇒ **排序桥不在 fat jar 里**；
  ③ ⇒ ⇒ MP 的 `@AutoConfigureAfter`（**Boot 3 包名**）在 Boot 4 下**悬空** ⇒ ⇒ MP 自动配置在 DataSource 之前求值 ⇒ ⇒ `@ConditionalOnSingleCandidate(DataSource)` 失败 ⇒ ⇒ **不产 `SqlSessionFactory`** ⇒ ⇒ **进程启动即挂**。
- **⇒⇒⇒ 结论（比「收编」本身更重要）**：**「域内桥一删 ⇒ 单模块构建的 fat jar 起不来」** ⇒ ⇒ ⇒ **凡有进程级 fat jar 守门的域，收编排序桥都会红** ⇒ ⇒ ⇒ **G3 的 comment-rpc / article-rpc / favorite-rpc 都带 `*RuntimeBootGateTest` ⇒ ⇒ 收编时必踩** ⇒ ⇒ ⇒ **已回信点名**。
- ⚠️ **「跑绿不是对」的样本**：同一轮里 `LikeRpcSpringContextWiringTest`（断言 `SqlSessionFactory` 存在）**是绿的**（测试 classpath 用的是 reactor 的 `service/common/target/classes`，带桥）⇒ ⇒ **只有进程级 fat jar 守门才抓得到** ⇒ ⇒ ⇒ **「测试绿 + 产物坏」只能靠产物层守门兜住**。

**③ 修法（域侧 · 测试代码）**
- `buildFatJar()` 的嵌套命令 **加 `-am`** ⇒ ⇒ reactor 先产出当轮的 `service/common/target/common-*.jar` ⇒ ⇒ fat jar 内嵌的就是它（**实测：内嵌 jar 的 `imports` 第 4 行 = 共享层排序桥**）⇒ ⇒ **真 fat jar 随即能起**。
- 🆕 **加一条产物层守门** `LikeRpcArtifactClasspathGateTest.fatJarCarriesSharedLayerOrderingBridge` ⇒ ⇒ 断言**内嵌的 common jar 的 `AutoConfiguration.imports` 里含共享层排序桥** ⇒ ⇒ **把这条潜伏地雷变成可捕获的失败**（否则下次有人去掉 `-am`，又只能靠「进程起不来」才发现）⇒ ⇒ **期望值用独立字面量**（共享层那个全限定名，**不从被测代码取常量**）⇒ ⇒ 共享层改包名时如实转红。
- ⚠️ **外层回归命令同样必须带 `-am`**（否则**单测的 classpath** 也吃旧 `common` jar ⇒ ⇒ `SqlSessionFactory` 断言会红）⇒ ⇒ **内外两层都要带**。

**④ 验证（可复算）**
- 修后：`mvn -o -B -DargLine=-Xmx2g -am -pl service/like/like-rpc test -Dtest='LikeRpcArtifactClasspathGateTest,LikeRpcRuntimeBootGateTest,LikeRpcSpringContextWiringTest'` ⇒ ⇒ **13 条 / 0f 0e 0s · BUILD SUCCESS**（`LikeRpcRuntimeBootGateTest` **1 条绿** = 真 fat jar 真起来了；`LikeRpcArtifactClasspathGateTest` **3** 条绿；`LikeRpcSpringContextWiringTest` **9** 条绿）⇒ ⇒ **实测内嵌 jar 的 `imports` 第 4 行 = `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration`** ✔
- **变异验证**：把嵌套命令的 `-am` 去掉 ＋ 删掉旧 fat jar 强制重建 ⇒ ⇒ **3 条里红 1 条**，**红在目标断言** `fatJarCarriesSharedLayerOrderingBridge:147`（失败消息就是「内嵌的 `BOOT-INF/lib/common-0.0.1-SNAPSHOT.jar` 的 imports 里【没有】共享层排序桥」）⇒ ⇒ **恰好 1 条红**（另 2 条仍绿）⇒ ⇒ **非假绿** ⇒ ⇒ 还原后 **sha256 与变异前逐字节一致**（`c3d547e7d5d8706c`）⇒ ⇒ **还原无残留**。

**⑤ 待办（不在我写权面）**
- **「构建 fat jar 一律带 `-am` / 或统一走根 reactor」这条口径需要共享层与整合者定** ⇒ ⇒ 因为**任何域**只要用单模块命令构建 fat jar，就会嵌旧 `~/.m2` 的 `common` jar ⇒ ⇒ **这是全仓性的构建路径约定，不是我域一家的事** ⇒ ⇒ **已报 G4 + G1 + G3**。
- ⚠️ **一条更深的教训**：**「域侧副本」不只是冗余 —— 它可能在无意中充当「共享层落码前」的承重件** ⇒ ⇒ ⇒ **这正是本次缺陷的根因**

**⑥ 🟢 池形态判据的「依赖面」：G3 提出「classpath 探测 ⇒ 共享层加传递依赖 ⇒ 形态静默翻转」⇒ ⇒ 我域复核结论**不同**（别照抄）**
- G3 的判据本身**成立**（共享层 `service/common` **不带** `commons-pool2`；`DataRedisConnectionConfiguration#isPoolEnabled` 在 `enabled == null` 时**回落到 classpath 探测**）⇒ ⇒ **对「没显式配 `enabled`」的域**成立。
- **我域显式配了** ⇒ ⇒ **classpath 回落那条对本域不成立**：`service/like/like-rpc/src/main/resources/application.yml:123-127`（`enabled: true` / `max-active: 64` / `max-idle: 16` / `min-idle: 8`）⇒ ⇒ **「共享层加一个传递依赖 ⇒ 本域池形态静默翻转」对本域**不成立**。
- ⚠️ **本域的风险是「反过来」的，而且更硬**：`enabled: true` ⇒ Boot 必走 `getPoolConfig` ⇒ ⇒ **`commons-pool2` 是启动期硬前提**（缺它 ⇒ `NoClassDefFoundError: org/apache/commons/pool2/impl/GenericObjectPoolConfig`）⇒ ⇒ **不是「静默翻转」，是「起不来」** ⇒ ⇒ **对域侧更安全（fail-fast）** ⇒ ⇒ 本域同款事故实证（like-rpc 2026-10-23 缺它时启动报该 `NoClassDefFoundError`）⇒ ⇒ 已写在 `service/task/task-rpc/pom.xml:123-138` 的注释里（同一形态，两域同源）。
- ⇒ ⇒ **但「依赖面」这条纪律本域照样标，且要分两轴写**：**轴 ①「池开关」** ⇒ 本域**不依赖**共享层 dependency 状态（`enabled` 显式）⇒ ⇒ **轴 ②「参数值」** ⇒ 本域**依赖**共享层（E66 那条联动条款：共享层 customizer 若碰 `timeoutOptions(...)` / `socketOptions(...)` ⇒ 覆盖本域的值）⇒ ⇒ ⇒ **两轴各自盯住：轴 ① 盯共享层的 dependency 声明，轴 ② 盯共享层 customizer 的取值**


### 🔴 第十八轮 · Redis 池 `max-wait` 对齐（Go 的 `PoolTimeout`）—— **池耗尽时无限等 ⇒ 比 Go 更糟**（2026-10-27）

**① 缺陷本体（真缺陷）**
- **现象**：池耗尽时**借连接没有任何等待上限** ⇒ ⇒ **无限挂起**（不是「等 3s」）。
- **Go 侧（本域自己推，不照抄）**：本域走 **go-zero 的 redis** ⇒ `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40` 的 `red.Options` 只传 `Addr/Username/Password/DB/MaxRetries/MinIdleConns/TLSConfig` ⇒ ⇒ **同样不设 `ReadTimeout`/`PoolTimeout`** ⇒ ⇒ 落 go-redis v9.20.0 库默认（`ReadTimeout = 3s`）⇒ ⇒ **`PoolTimeout = 4s`** ⇒ ⇒ 消费点 `internal/pool/pool.go:1113` ⇒ ⇒ **Go 侧池耗尽 ⇒ 4s 后失败**。
- **RAG 侧为何更糟（字节级实测）**：commons-pool2 2.12.1 **`DEFAULT_MAX_WAIT_MILLIS = -1l`** ⇒ 消费点 `GenericObjectPool.borrowObject` 的**负值分支走 `takeFirst()`（无参 = 永久阻塞）** ⇒ ⇒ **无限等**；⚠️ **Lettuce 的 3s 命令超时救不了场**（计时器在 `CommandExpiryWriter.write` 才起算，而**借连接在写命令之前**）。

**② 落码（1 个文件）**
- `service/like/like-rpc/src/main/resources/application.yml` 的 `spring.data.redis.lettuce.pool` ⇒ 加 **`max-wait: 4s`**。
- ⚠️ **不动 `min-idle`**：本域早就是 **8**（= go-zero 的 `idleConns = 8` ⇒ **精确对齐**）⇒ ⇒ ⚠️ **G3 消息里的「like `min-idle: 4`」与我树不符** ⇒ ⇒ **未改**。

**③ 守门 + 变异验证（恰好 1 条红，红在目标断言）**
- 🆕 `LikeRpcRedisTimeoutsGateTest.redisPoolMaxWaitMatchesGoPoolTimeout`（新增断言 ⑦）⇒ 本类 **6 ⇒ 7 条**。
- 变异 = 抽掉 `max-wait: 4s` ⇒ **7 条恰红 1 条**（红在 `:258`）⇒ 另 6 条仍绿 ⇒ ⇒ **非假绿** ⇒ ⇒ 还原后 **sha256 与变异前逐字节一致**（`9b406dca745a5906ab40a522`）⇒ ⇒ 复跑 **7 / 0f 0e 0s**。

**④ 🟢 顺带订正两处「我域自己写错的引用」**
- **`MaxIdleConns` 不是「无对应项」**（`options.go:224-228` `default: 0` ＋ 消费点 `internal/pool/pool.go:1257` ⇒ **0 = 无上限**）⇒ ⇒ **`max-idle: 16` 重新归类 = 「比 Go 更严」的可登记残留**（不影响命令结果）⇒ ⇒ 原写「无对应项 ⇒ 纯表达层取值」**不准确**，已订正。
- **`getPoolConfig` 的引用路径**：Boot 4.0.8 外层类**没有** `getPoolConfig`（实测 `javap`）⇒ ⇒ 真实消费点是内部类 **`LettuceConnectionConfiguration$PoolBuilderFactory.getPoolConfig`** ⇒ ⇒ 原引「`:267-271`」是 **Boot 2/3 旧路径**，已改按**类名 + 方法名**。

**⑤ ⚠️ 口径影响（数字要跟着改 · 第十八轮**实测**）**
- 本域 **+1 条**守门 ⇒ ⇒ **like-rpc 120 ⇒ 121**（`mvn test` 口径）。
- 🆕 **第十八轮实测（9 模块全量 `clean test` · 带 `-am` · BUILD SUCCESS · 9:52 min）**：

| 模块 | tests | failures | errors | skipped | 报告数 |
|---|---|---|---|---|---|
| `service/common`（**共享层**） | 327 | 0 | 0 | 0 | 62 |
| `service/message/message-api` | 103 | 0 | 0 | 0 | 15 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 | 20 |
| `service/message/message-common` | 21 | 0 | 0 | 0 | 2 |
| `service/like/like-api` | 94 | 0 | 0 | 0 | 11 |
| `service/like/like-rpc` | **122** | 0 | 0 | 0 | 21 |
| `service/like/like-common` | 18 | 0 | 0 | 0 | 2 |
| `service/task/task-api` | 102 | 0 | 0 | 0 | 9 |
| `service/task/task-rpc` | **122** | 0 | 0 | 0 | 15 |
| **合计** | **998** | **0** | **0** | **0** | **157** |

  ⇒ ⇒ **域侧 8 模块 = 671** ＋ 共享层 **327** ⇒ ⇒ **9 模块 `mvn test` 口径 = 998** ⇒ ⇒ **`test`+`verify` 口径 = 998 + 5（task 的产物层 IT）= 1003**。
- 🔴 **订正我本轮自己写错的推算**：上面原写「9 模块 `mvn test` 口径 995 ⇒ 997」⇒ ⇒ **实测是 998（差 +1）** ⇒ ⇒ **那个 997 是推算、且推算错了** ⇒ ⇒ **根因（已实测定位）**：**第十七轮**新增的 `LikeRpcArtifactClasspathGateTest.fatJarCarriesSharedLayerOrderingBridge` 落在 **`test` 阶段**（类名 `*Test` ⇒ surefire 默认 includes 命中）⇒ ⇒ **like-rpc +1**（实测复核：`git show 016e95a:…/LikeRpcArtifactClasspathGateTest.java` 的 `@Test` 数 = **2** ⇒ HEAD = **3** ⇒ +1 ✔）⇒ ⇒ **第十七轮结束时真实值已是 996**，而第十七轮**只重跑了 `task-rpc`**、**沿用了第十六轮的 9 模块总数 995** ⇒ ⇒ **基线在第十七轮就悄悄过期了**（未被发现）⇒ ⇒ 第十八轮 +2（两域池断言 ⑦）⇒ ⇒ **996 + 2 = 998** ✔
  ⇒ ⇒ **教训（写死）**：**「只重跑受影响模块 + 沿用上轮总数」会让总数悄悄过期** ⇒ ⇒ 跨轮改动落在 **`test` 阶段的类**上时，要么**重跑 9 模块全量**，要么把总数标成「**已过期**」⇒ ⇒ 本轮能抓出来，靠的是**逐模块 XML 求和 == maven 汇总行**的**双口径交叉验算**。
  ⇒ ⇒ **原值保留不改写**：第十六/十七轮台账里的 **995 / 1000 原值保留**，以本条订正为准。

### 🟢 第十九轮 · 并入 G4 终版 `d546f45`（共享层 1xx 探针「每请求上界」）⇒ **9 模块全量实测 1001**（2026-10-27）

**① 合并（4 笔 · 零冲突）**
- 提交 **`0cd1e3b`**（`merge(g4)`，父提交 = `900e2c4` + `d546f45`）⇒ ⇒ `git merge-tree --write-tree` 预演**零冲突**。
- 并入 4 笔：`15cf93a`（**fix(common)**：1xx 探针行数设「每请求上界」—— `GoStyleClientDisconnectProbe` 的 `MAX_PROBE_LINES_PROPERTY` / `DEFAULT_MAX_PROBE_LINES = 4` / `probe()` 预算闸门 / 新增 `State.BUDGET_EXHAUSTED` ＋ `HttpMiddleware` 的 `BUDGET_EXHAUSTED` 分支，**等待语义不变**）· `8379748` / `12942e2`（global 台账 §4.10.30~§4.10.34）· `d546f45`（**纯 javadoc**：`GoStyleTomcatParameterLimitCustomizer` 过期 javadoc 订正 —— **我域第十七轮报的那条，G4 已在共享层自修**）。
- ⚠️ **`15cf93a` 改了生产代码** ⇒ ⇒ **合并后必须重跑全量**（G4 原话：收口回归窗口在合并前）⇒ ⇒ 本轮紧接一次 9 模块 `clean test`。
- ⚠️ **写权零越界**：并入的 6 个文件全在 `service/common/**` ＋ `docs/DRIFT/global/vs-common.md` ⇒ **均为 G4 的写权面**（本 lane 只读不改）。

**② 实测（9 模块全量 `clean test` · 带 `-am` · BUILD SUCCESS · 13:02 min）**

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**共享层**） | **330** | 0 | 0 | 0 |
| `service/message/message-api` | 103 | 0 | 0 | 0 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 94 | 0 | 0 | 0 |
| `service/like/like-rpc` | 122 | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 102 | 0 | 0 | 0 |
| `service/task/task-rpc` | 122 | 0 | 0 | 0 |
| **合计** | **1001** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 671**（与第十八轮**一字不差**）＋ 共享层 **327 ⇒ 330**（**+3** = G4 的 `GoStyleClientDisconnectProbeBudgetGateTest` 3 条）⇒ ⇒ **9 模块 `mvn test` 口径 998 ⇒ 1001** ⇒ ⇒ **`test`+`verify` 口径 = 1001 + 5（task 的产物层 IT）= 1006**。
⇒ ⇒ **口径**：逐模块 surefire `TEST-*.xml` 的 `testsuite` 属性求和 **== maven 模块汇总行**（**9/9 逐字相等**）⇒ ⇒ **0 stale 报告**（`clean` 已清空旧目录）。
⇒ ⇒ ⚠️ **共享层 330 与 G4 终版回执里的数字逐字相同** ⇒ ⇒ **两侧对同一份共享层的条数认知一致** ✔

**③ G4 点名要我做的三项 —— 逐条复跑，全部绿**

| 项 | G4 的原话 | 我域实测 |
|---|---|---|
| **FB-01 / FB-03 升级到 `layerNames` 断言** | 「升级那些守门到 `layerNames` 断言 ＋ 按 §5.3e 重跑收口回归」 | ✅ **我域三域早就在用 `layerNames` / `compose`**（`LikeProtectionChainWiringTest.java:127/:134/:138/:151` · `MessageProtectionChainWiringTest.java:97/:104/:108/:121` · `TaskProtectionChainWiringTest.java:182/:189/:193/:206`）⇒ ⇒ **无需改任何断言、无需发失败断言** ⇒ 本轮实测 like **4 条** / message **5 条** / task **5 条** 全绿 |
| **我域 3 处 `DEADLINE_EXCEEDED` 断言合并后复跑** | 「须在合并 `7488b3d` 后复跑」 | ✅ **合并条件早已满足**：`7488b3d` / `3b573fb` **早就在我树**（`git merge-base --is-ancestor` 两笔都 ✔）⇒ ⇒ 本轮实测：`MessageRpcTimeoutGateTest` **2 条 / 0f 0e 0s**（20.1s）· `LikeRpcGrpcInterceptorWiringTest` **3 条 / 0f 0e 0s**（12.0s）· `TaskGrpcInterceptorWiringTest` **2 条 / 0f 0e 0s**（19.5s） |
| **池守门 ⑤⑥ 合并后实测复跑** | 「安排在收口回归之后」 | ✅ 本轮实测：`TaskRedisCommandTimeoutGateTest` **7 条 / 0f 0e 0s**（含第十八轮新增的断言 ⑦ `max-wait`）· `LikeRpcRedisTimeoutsGateTest` **7 条 / 0f 0e 0s** ⇒ ⇒ **G4 ⑥ 的预判「customizer 只调 `disconnectedBehavior(REJECT_COMMANDS)`、不碰池 ⇒ 池守门不受影响」被实测证实** |

**④ like 域 A9 端到端 E2E —— G4 说「必须在合并后补」，我域实测是「**已存在 ＋ 合并后复跑通过**」**
- G4 §4.10.30 的判断是「合并后 like 域 `LikeSpans.java:242/:246`（两个 `attr(...)` 重载）落在受益面上 ⇒ 由 no-op 变真生效 ⇒ **预期行为变更，非回归**」⇒ ⇒ **与我的结论一致**。
- ⚠️ **但「补」这个动作在我域是「复跑」，不是「新建」** —— `LikeA9GrpcSpanEndToEndTest`（**3 条**）在**第七轮**就随 FB-04（`853767b`，合并 `37816ca`）入本树了 ⇒ ⇒ **本轮合并后复跑 = 3 条 / 0f 0e 0s（2.16s）** ✔
  · ① 真 gRPC 链路 ⇒ 域侧 span 属性落在**服务端 span** 上（`query.target_type` = `article` · `query.batch_size` = 2L）
  · ② **对照组**（复刻 FB-04 之前的行为、同一份域服务、同一通真实调用）⇒ 属性**必须丢**（`assertNull`）
  · ③ **护栏** ⇒ 共享层那个拦截器真的在链路上（否则前两条测的是别的拦截器）
- ⇒ ⇒ 配套的 `LikeSpansAttributeGateTest`（契约级 · 真 OTel SDK + 真 exporter）**11 条 / 0f 0e 0s**。
- ⇒ ⇒ **本域未被这次合并改动 ⇒ 域侧不需要新增任何代码**（FB-04 的落码在共享层，域侧只是**受益**）。

**⑤ 1xx 上界这条对我域的影响面 = 零**
- 实测我域 `1xx` / `interim` / `Too many interim` **命中数 = 0**（排除注释后）⇒ ⇒ 改动落在共享层探针的**行数预算**上、**不改 HTTP 语义**（G4 给的 Go 出处 `Sea-RideTheWind_golang/service/common/observability/http.go:80-88` ⇒ **零 Go 语义变更**）⇒ ⇒ **我域零直接影响**。
- 共享层那条新守门 `GoStyleClientDisconnectProbeBudgetGateTest` 在我树本次产物里 **3 条 / 0f 0e 0s**（2.9s）✔

**⑥ 本域受影响面（逐条）**
- **like 域**：本域**零代码改动**，但**是这次合并的受益面** ⇒ ⇒ G4 §4.10.30 说的「`LikeSpans.java:242/:246` 由 no-op 变真生效」**在我域可复现验证**。
- 复跑实证：`LikeA9GrpcSpanEndToEndTest` **3 条 / 0f 0e 0s**（2.16s）—— ① 真 gRPC 链路属性落在**服务端 span** 上（`query.target_type` = `article` · `query.batch_size` = 2L）② **对照组**属性**被丢弃** ③ **护栏**共享层拦截器真的在链路上 ⇒ ⇒ **A9 端到端 E2E 合并后仍绿 = 预期行为变更、非回归** ✔ · `LikeSpansAttributeGateTest` **11 条 / 0f 0e 0s**。
- 另：`LikeRpcRedisTimeoutsGateTest` **7 条 / 0f 0e 0s**（含断言 ⑦）· `LikeRpcGrpcInterceptorWiringTest` **3 条 / 0f 0e 0s**（含 `DEADLINE_EXCEEDED`）· `LikeProtectionChainWiringTest` **4 条 / 0f 0e 0s**（`layerNames` / `compose`）。
- ⇒ ⇒ **like-rpc 本轮条数 = 122（未变）**；共享层 327 ⇒ 330 ⇒ 9 模块 998 ⇒ **1001**。

**⑦ 原值保留不改写**
第十八轮的 **998 / 671 原值保留**，以本条订正为准（**当前权威值 = 1001 / 671**）⇒ ⇒ **域侧 8 模块的 671 在两轮之间一字未变** ⇒ ⇒ 变的只有共享层（+3）。

---

### 🔴 第二十轮 · Redis 池维度的**双侧活体对拍**（真 Redis ＋ 真 go-redis 客户端）⇒ **池轴 L3 ⇒ L4** ＋ 🟡 **本域定性降级**（2026-10-27）

**① 为什么本轮要补这一层（缺口在哪）**
- 第十八轮（池 `max-wait: 4s`）证明的是「**配置层**对」（守门 ⑦ 读 Boot 装配出来的那份 `poolConfig`）⇒ ⇒ 它**不能**证明「**运行期**真按这个上限在跑」⇒ ⇒ 本轮补后半段：**真的把池占满，量第 N+1 条等了多久**。
- ⇒ ⇒ **本条取代第十八轮台账里「⚠️ 未做双侧并发活体对拍 ⇒ 不写 L4」的效力边界**。

**② 🟡 本域定性降级（本轮的核心结论）**
- **降级依据（域内源码级实测 · 不是外推）**：本域主代码（`service/like/like-rpc/src/main/java/**`）里 `openPipeline` / `executePipelined` / `SessionCallback` / `.multi()` **命中数 = 0** ⇒ ⇒ 本域 Redis 只有**普通命令**路径（`LikeDomainService.java:104` 的 `StringRedisTemplate` · `LikeActionScript.java` 的 Lua `execute(RedisScript, …)`）⇒ ⇒ 而**普通命令走共享 native 连接、池不参与**（本域活体实测：50 条普通命令 ⇒ 服务端 `connected_clients` **delta = 0**）⇒ ⇒ ⇒ **修前那个「池耗尽 ⇒ 无限挂起」的漂移，在本域生产路径上几乎不可达** ⇒ ⇒ 原写的「🔴 真缺陷（比 Go 更糟）」**降级为「形态上已对齐的防御性配置」**（**不是可达缺陷**）⇒ ⇒ **原值保留不改写**，以本条为准。
- ⚠️ **task 域同款漂移仍成立**（那边**有** pipeline 生产路径）⇒ ⇒ **不外推**：一条轴的定性**按域不同源**。

**③ 落码（1 个新测试类 · 3 条 · 零生产代码改动）**
- `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRedisPoolExhaustionP99GateTest.java`
- 构造 = **真 Redis 容器**（`redis:7-alpine`）＋ **真 Spring 上下文**（真应用类 ⇒ 加载本模块**真** `application.yml`）＋ **真装配**的 `LettuceConnectionFactory`；判据全部是**运行期读数**。
- ⚠️ **本域单独建、不抄 task 域那条的读数**：两个模块的 `application.yml` 各自生效 ⇒ ⇒ 「task 域量到 X」**推不出**「like 域也是 X」。

**④ 三条活体读数（like 域实测）**

| 断言 | 实测 |
|---|---|
| ① 普通命令走不走池 | `connected_clients` **before=8 / after=8 / delta=0** |
| ② pipeline 走不走池 | 占位 63 条 ⇒ **before=8 / after=64 / delta=56** |
| ③ 池耗尽 ⇒ 等连接上限 | **`wait_elapsed_ms = 4003**`，异常链 `RedisConnectionFailureException → PoolException → NoSuchElementException: Timeout waiting for idle object, borrowMaxWaitDuration=PT3.999992S` |

⇒ ⇒ **单次池等待上限 = 4s（真行为读数，不是配置值）**。

**⑤ 🆕 顺带把本域 `min-idle: 8` 从「配置层」升级到「活体」（L4）**
- 探针里**借任何连接之前**，服务端就已经有 **8 条**连接（task 域同款探针 = **1** 条 ⇒ 差值 = 本域的 `min-idle`）⇒ ⇒ **受控实验归因**（把本域 yml 的 `min-idle: 8` 翻成 `0` 再跑 ⇒ 借前基线 **8 ⇒ 1**、净增量 **56 ⇒ 63** ⇒ 回滚后 sha256 = `9b406dca745a5906ab40a522` 与变异前**逐字节一致**）⇒ ⇒ **「`min-idle: 8` = Go 的 `MinIdleConns = 8`」这条对齐结论，现在有 L4 活体证据**（此前只是 L1 配置层 + 装配层读数）⇒ ⇒ 也顺带**证实 G3 那条消息里「like `min-idle: 4`」的前提过期**（本域早就是 8）。
- ⚠️ **归因是受控实验得出的，不是推测**：8 这个数还可能来自本域 `@PostConstruct` 的调度器（`LikeFactDispatchScheduler`）⇒ ⇒ **不能靠「数值相符」下结论** ⇒ ⇒ 必须翻转变量实测。

**⑥ Go 腿活体读数**（探针用**真源的 `go.mod`/`go.sum` 离线构建** · `CGO_ENABLED=0`）：`PoolTimeout = 4s` ⇒ 并发 80 ⇒ **16 个等待者全部 `ErrPoolTimeout`** ⇒ **p50=16086ms / p99=16135ms** ⇒ ⇒ ⚠️ **Go 的 4s 是「单次池等待」**（go-redis v9.20.0 `error.go` 的 `shouldRetry` 对 `ErrPoolTimeout` **无条件重试** ⇒ `MaxRetries=3` ⇒ 共 4 次）⇒ ⇒ **命令整体 ≈16.1s** ⇒ ⇒ 原「Go 4s / RAG ∞」的**参照量级要改**（**漂移方向不变**）。

**⑦ 变异验证**：抽掉本域 yml 的 `max-wait: 4s` ⇒ 同一探针 ⇒ **`wait_elapsed_ms = 8004` / `timedOut = true`** ⇒ ⇒ **3 条恰红 1 条**（红在 `timedOut` 断言，失败信息「Expecting value to be false but was true」）⇒ ⇒ 另 2 条仍绿 ⇒ ⇒ **非假绿** ⇒ ⇒ 回滚后 sha256 与变异前**逐字节一致** ⇒ `git status` 对该 yml **为空**。

**⑧ 修后定性**：单次池等待 Go **4s** / RAG **4.003s** ⇒ **对齐**；命令整体 RAG 更短 ⇒ ⇒ **「比 Go 更严」** ⇒ ⇒ 本域这条**可登记为「有界残留 · 刻意不改」**（**不是缺陷**）。

**⑨ 🟡 探针自身的 3 个缺陷（自曝 · 已修 · 留档）**：① **漏了 `grpc.server.port=0`** ⇒ gRPC server 走固定端口 8082 ⇒ 本机 8082 被桌面运行时的 Python 进程占着 ⇒ **上下文起不来**、整类红、且**不是**被测行为红（**本域这条先暴露**）⇒ ⇒ 已补；② **② 的断言写错了量**（原写「净增量 ≥ 占位数」⇒ 但池里**已有**的空闲连接会被复用 ⇒ like 实测净增 **56 < 63**）⇒ ⇒ 改成「服务端连接数必须**长到池上限**」＋ 前置「借前基线 < 占位数」；③ **② 失败时不释放占位** ⇒ 池被占满 ⇒ **③ 在准备阶段就 4s 超时** ⇒ 「红在别处」⇒ ⇒ 释放动作移到 `@AfterEach`。

**⑩ 回归实测（9 模块 `clean test` · 带 `-am` · BUILD SUCCESS · 12:01 min）**

| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（**共享层**） | **330** | 0 | 0 | 0 |
| `service/message/message-api` | 103 | 0 | 0 | 0 |
| `service/message/message-rpc` | 89 | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/like/like-api` | 94 | 0 | 0 | 0 |
| `service/like/like-rpc` | **125** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/task/task-api` | 102 | 0 | 0 | 0 |
| `service/task/task-rpc` | **125** | 0 | 0 | 0 |
| **合计** | **1007** | **0** | **0** | **0** |

⇒ ⇒ **域侧 8 模块 = 677**（上轮 671 ⇒ **+6** = 两条探针各 3 条）＋ 共享层 **330（未变）** ⇒ ⇒ **9 模块 `mvn test` 口径 1001 ⇒ 1007**；**逐模块 surefire XML 求和 == maven 汇总行（9/9 逐字相等）** ✔
⇒ ⇒ ⚠️ **口径坑（写死）**：**定向跑（`-Dtest=`）会改写该模块的 surefire 报告** ⇒ ⇒ 探针跑完后**必须**补一次覆盖该模块的**全量**跑。

**⑪ 原值保留不改写**
第十九轮的 **1001 / 671 原值保留**，以本条为准（**当前权威值 = 1007 / 677**）⇒ ⇒ **共享层 330 两轮之间一字未变** ⇒ ⇒ 变的只有域侧（+6 = 两条探针）。

---

### 🟢 第二十一轮 · Testcontainers 宿主口（**层 2**）落码 —— **回答 G4 那个未实测项**（2026-10-27）

**① 起因（G4 §4.10.39 ② 分配段号 ＋ 明确要求「先探针、不能照抄」）**
- G4 把 `22700–22999` 划为「**Testcontainers 宿主口段**」，**按域分子段**：**like 域 = `22790–22819`**。
- 根因：Testcontainers 的**默认随机宿主口**落在 **OS 临时段 `49152–65535`**（= Docker published 段）⇒ ⇒ 与 `tools/difftest/ddl-parity.sh` 的固定口 `55560–55569` **同属一个池子** ⇒ ⇒ 全局段表把该段标为**禁占** ⇒ ⇒ **真容器测试不得继续用随机口**。
- ⚠️ G4 同一格里**明写了两层「未实测」**：① `PostgreSQLContainer` **不能直接换成** `FixedHostPortGenericContainer`（后者不带 PG 的初始化/JDBC 支持）⇒ ⇒ 正确形态 = **原容器 ＋ `withCreateContainerCmdModifier(...)` 显式绑口**；② 「**显式绑口后 `getMappedPort()` 是否返回那个固定口**」未实测 ⇒ ⇒ **域侧落地必须先写一条探针验证，不能照抄**。

**② 🔴 「两层口径」在我域的真实含义（本轮第一次把两层说清）**

| 层 | 是什么 | 载体 | 本域段 | 本轮之前的状态 |
|---|---|---|---|---|
| **层 1** | **api 侧 Tomcat 监听端口**（`@SpringBootTest(webEnvironment = DEFINED_PORT)`） | `RealTomcatPort` | like 域另有一段（见 §2/A5 行） | ✅ **早已治理**（阶段 2 · D-7 已销号） |
| **层 2** | **rpc 侧 Testcontainers 容器宿主口**（`PostgreSQLContainer` / `GenericContainer`） | 🆕 `RealContainerPort` | **`22790–22819`** | 🔴 **整个域没人管** —— **9 个容器类**全部用随机口 ⇒ ⇒ **都落在 OS 临时段** |

⇒ ⇒ **本轮补的是层 2 的缺口**（不是重复治理层 1）⇒ ⇒ **两层都要守**：层 1 已有 `bindsToDedicatedPortSegment`，层 2 本轮新增。

**③ 落码（9 个容器类 ＋ 3 份支撑类 ＋ 6 个守门类 · **零生产代码改动**）**

| 项 | 内容 |
|---|---|
| **被绑的容器类** | like 域 **9 个**：PG **8** 个 ＋ Redis **1** 个 ⇒ 全部钉在本域段内的**独占**固定宿主口 |
| **支撑类** | `service/like-rpc/src/test/java/com/rag/like/like/rpc/support/RealContainerPort.java` ⇒ 段边界常量 ＋ `withFixedHostPort(container, hostPort, containerPort)` ＋ **端口越段直接抛异常**（拒绝落码） |
| **机制层守门**（真容器） | `likePostgresFixedHostPortGateTest`（3 条）⇒ 钉「**映射口 == 固定口**」＋「固定口真在监听」＋「固定口上确实是 PG（`SELECT 1` 成功）」 |
| **覆盖层守门**（源扫 · 无容器 · 秒级） | `likeContainerFixedHostPortSegmentGateTest`（1 条）⇒ ① 凡声明容器的类**必须**有 `FIXED_HOST_PORT` ② **必须**在本域段内 ③ **全域不重号** ④ 容器数 **≠ 0**（防空过） |
| **端口分配方式** | **逐类写死**（不是轮询）⇒ surefire **每个 fork 一个独立 JVM** ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一端口 ⇒ ⇒ 撞车 |

**④ 🔴 直接回答 G4 的未实测项（**实测，不是推断**）**

> G4 §4.10.39 ② 的原问句：「**显式绑口后 `getMappedPort()` 是否返回那个固定口**」

### **实测结论：会。**

- `likePostgresFixedHostPortGateTest`（**真容器**）实测：① `getMappedPort(5432)` **== 22799**（就是那个固定口，**不是**随机口、**不是** 0）② 那个固定宿主口**真在监听**（从宿主机 TCP 连得上）③ 那个口上**确实是 PG**（真 JDBC 连接 ＋ `SELECT 1` 成功）。
⇒ ⇒ **形态两层都验完了**：形态 = 原容器 ＋ `withCreateContainerCmdModifier`（`FixedHostPortGenericContainer` **确实不能混用** —— 实测 `PostgreSQLContainer` 的父链是 `JdbcDatabaseContainer` → `GenericContainer`，与它**无继承关系**）；行为 = **映射口就是那个固定口**。

**⑤ 🔴 实现里踩到的三个坑（写死，给下一个域）**
1. **必须「先摘随机绑定、再加固定绑定」** —— 只加不摘 ⇒ Docker 会**同时看到两条同容器端口的绑定** ⇒ 行为不确定。做法：`HostConfig.getPortBindings()` 取出全部 ⇒ **`removeIf` 掉「容器端口 == 5432/6379」的那条**（Testcontainers 默认给的是随机宿主口）⇒ 再 `add` 一条 `new PortBinding(Ports.Binding.bindPort(hostPort), ExposedPort.tcp(containerPort))` ⇒ 最后 `hc.withPortBindings(bindings)`。
2. **泛型写法会编译不过** —— `public static <T extends GenericContainer<T>> T withFixedHostPort(T container, …)` ⇒ 传进去的是 `PostgreSQLContainer<?>`（**通配符捕获**）⇒ 编译器报「推论变量 T 具有不兼容的上限」⇒ ⇒ **改成非泛型签名**：`public static void withFixedHostPort(GenericContainer<?> container, int hostPort, int containerPort)`。
3. 🔴 **javadoc 里写 `src/test/java/**/*.java` 会提前闭合注释块** —— `*/` 出现在注释内部 ⇒ ⇒ 注释提前结束 ⇒ ⇒ 后面的**全角括号等中文被当代码解析** ⇒ ⇒ javac 报「**非法字符**」⇒ ⇒ 而**报错位置在几十行之后**（不是出问题的那一行）⇒ ⇒ **看起来像「编码坏了」，实际是注释块被截断**。修法：写成 `{@code src/test/java} 递归下的全部 {@code .java} 文件`（避开 `*/` 序列）。

**⑥ 回归实测（本轮）**
| 模块 | tests | failures | errors | skipped |
|---|---|---|---|---|
| `service/common`（共享层） | **330** | 0 | 0 | 0 |
| `service/message/message-common` | 21 | 0 | 0 | 0 |
| `service/message/message-rpc` | **93** | 0 | 0 | 0 |
| `service/like/like-common` | 18 | 0 | 0 | 0 |
| `service/like/like-rpc` | **129** | 0 | 0 | 0 |
| `service/task/task-rpc` | **129** | 0 | 0 | 0 |

⇒ ⇒ 三模块 `clean test`（带 `-am`）⇒ **BUILD SUCCESS · 7:41 min** ⇒ ⇒ 本域 `like-rpc` = **129**（上轮 125 ⇒ **+4** = 机制层 3 条 ＋ 覆盖层 1 条）⇒ ⇒ 共享层 `service/common` = **330（未变）** ⇒ ⇒ ⚠️ **口径**：本轮只跑了 3 个 rpc 模块 ＋ 它们的依赖（`-am`）⇒ ⇒ **9 模块口径尚未重测** ⇒ ⇒ **本域之外的模块条数一律沿用上一轮实测值，不做推算**。

**⑦ ⚠️ 口径提醒（写给下一个复核者）**
- **数「真容器类」要数 `new PostgreSQLContainer<>` / `new GenericContainer<>` 的**声明**，**不要**把 import / javadoc / 泛型里的类型名算进去（与「数真 `RANDOM_PORT` 注解、不数提及」同款纪律）。
- **端口守门是源扫型**：它核的是「**源码上有没有绑**」；机制层才是「**运行期绑没绑成**」⇒ ⇒ **两层不重复、缺一不可**。

**⑧ 原值保留不改写**
本轮**未改动**任何生产代码 ⇒ ⇒ 前面各轮的行为结论与条数口径**一律不变**；本轮新增的只有**测试面**（9 个容器类的绑定 ＋ 6 个守门类 ＋ 3 份支撑类）⇒ ⇒ **域侧条数的变化**见 §⑥ 的实测。

---

### ⏸️ 第二十一轮 · 附 · **C7 收口复跑（并入 G4 尖端 `a38ad75` 之后）** —— **本域模块本轮未重跑**

- 本轮 C7 义务 = 复跑 `service/common` ＋ **耦合最紧的 1 个域模块**（本 lane 选的是 `task-rpc`）⇒ ⇒ **本域模块本轮未重跑** ⇒ ⇒ **本域条数仍以第二十一轮实测为准**（`like-rpc` = **129**）⇒ ⇒ 共享层实测 **331**（上轮 330 ⇒ **+1** ⇒ G4 说的「条数不变仍 330」**被实测推翻**）。
- ⇒ ⇒ **9 模块口径仍不可推算**（域不同 ⇒ 数字不可相加）⇒ ⇒ 权威值只能来自「**四 lane 全合后的一次回归**」。
- ⇒ ⇒ 完整回执见 [`docs/DRIFT/task/vs-common.md`](../task/vs-common.md) 的「第二十一轮 · 附 · C7 收口复跑回执」段。

---

## 4·十二、🟢 **第十二轮（2026-10-27 18:4x）· 合并 G4 的 13 笔（零冲突）⇒ 新机器门禁在本树为绿 ＋ 全仓 U+FFFD 复扫下降 ＋ 两个红门禁的根因定位到行**

### ① 合并（`5636164`）

- **冲突面 0**（`git merge-tree --write-tree` 预演确认）⇒ ⇒ **未由本 lane 在 `tools/**` 或 `docs/DRIFT/global/**` 里写任何内容**（写权零越界）
- 带入 13 笔，关键三件：
  - `22dc67e` `test(common)`：**`GoStyleRedisClientShadowingGateTest`** —— 把「不得新建域内 Redis 副本」从**台账禁令**落成**机器门禁**（3 条断言，各带专属变异）
  - `cc6e38e` / `f68a9cd`：**修掉本轮全仓扫描里那 3 份「无人拥有」的 U+FFFD 真损坏**（`tools/errmsg-probe/README.md` · `tools/sql-statement-probe/favorite/compare.py` · `component-semantic-verification/springboot-semantic-model.md`）
  - `6cb0a60`：订正 FB-04 javadoc 的计数口径（我方反馈单 FB-04）· `69769ca`：§4.10.27 Redis 超时跨域全表（我方「命令超时联动」发现已推广）
- **共享层生产代码**：仅 `service/common/.../RpcInterceptor.java` 的 **javadoc 订正**（+19/−2，**零行为变更**）⇒ ⇒ **本域 like 域 A9 端到端探针的前提不受影响**

### ② 🟢 新机器门禁**在本树真跑过且为绿**（不是「读源码推断」）

- 实测：`mvn -o -B -DargLine=-Xmx2g -pl service/common test` ⇒ **327 / 0f 0e 0s**（324 + **3** = G4 那 3 条）· **BUILD SUCCESS**
- surefire 报告里三条用例**都在**：`scannerSeesTheRealRepository` · `noDomainSideShadowingCopyExists` · `scannerActuallyReportsACopy`
- **我三域 `src/main/java` 的 `LettuceClientOptionsBuilderCustomizer` 命中数 = 0** ⇒ ⇒ 我域那两处引用在 `src/test/java`（`LikeRedisDisconnectFastRejectGateTest` · `TaskRedisDisconnectFastRejectGateTest`）⇒ ⇒ **门禁的扫描范围（只扫 `src/main/java`）天然排除** ✔
- 白名单那份（users 域 `RedisDisconnectedBehaviorConfig.java`）在本树**仍存在** ⇒ ⇒ 门禁的 ③(b) 通过 ⇒ ⇒ 与「自适配」设计一致（G1 删掉后那半条自动为空）

### ③ 🟢 全仓字节级 U+FFFD 复扫（**合并后**）：**15 份 / 38 字节 ⇒ 12 份 / 27 字节**（−3 份 / −11 字节）

- 消失的正是我此前登记为「**无人拥有**」的那 3 份（见本文件「无人拥有」条目的订正注记）⇒ ⇒ **那张清单可以清空**
- ⇒ ⇒ 剩下 12 份里**本域写权面 = 0**（`docs/Pitfalls/message/rpc.md` 的 2 字节是**故意保留**的故障样本）

### ④ 🔴 两个红门禁的根因（**定位到行**，修法在 G4 侧）

- **① `check-tomcat-limits.py`**：它把 task-api 期望写死 `raw`，而**共享层自己的权威语义表**写的是 OFF ——
  `service/common/src/main/java/com/rag/common/http/GoStyleFormLimitMode.java:237`
  ⇒ `new DomainReachability("service/task/task-api", false, false, Mode.OFF, …)`
  ⇒ ⇒ **门禁的期望表与权威表自相矛盾** ⇒ ⇒ 修法：**①** 期望表改 `off`；**②**（更耐漂移）门禁改成**读** `GoStyleFormLimitMode`
- **② `check-wave-coupling.py`**：漂移 6 项 ⇒ **9 项**，且 **9 项全部可归因到本域三域的组件引用**（**不是代码错，是文档快照落后于本域合并**）：
  - `GoStyleFormLimitMode` / `GoStyleFormValueLimitInterceptor` ⇒ **+task**
  - `GoStyleGrpcClientBreakerInterceptor` / `MqConsumer` ⇒ **+like**
  - `GoStylePathNormalizingFilter` ⇒ **+like +message +task**（共享层落码 ＋ 本域第十轮探针引用）
  - `GoStyleRpcBreakerInterceptor` / `GoStyleRpcSheddingInterceptor` ⇒ **+message +task**
  - `GoStyleRpcProtectionChain` ⇒ **+message** · `RpcInterceptor` ⇒ **+like**
  - ⚠️ **但这条不能在任何单棵 lane 树上重生成快照**（每棵树都缺别家的提交）⇒ ⇒ **必须在四 lane 全合入后（= C7 窗口）重生成** ⇒ ⇒ 提前改只会改出**第二份过期快照**
  - 📌 **口径提示**：门禁的 `scan_common_radiation` 扫 `service/**/src/**/*.java` ⇒ ⇒ **含 `src/test/java`** ⇒ ⇒ 本域第四轮加的三域链层守门测试也进了辐射集 ⇒ ⇒ 快照条目天然比「只看生产源码」多（**判据口径如此，不是多算**）

### ⑤ ⚠️ 数字口径（**推算 ≠ 实测**）

- 合并后共享层 **324 ⇒ 327**（+3 = G4 的新机器门禁）⇒ ⇒ **9 模块合计预计 992 ⇒ 995** ⇒ ⇒ **本轮未重跑 9 模块** ⇒ ⇒ **995 是推算，不是实测** ⇒ ⇒ 本树 9 模块全量的下一个权威口径 = **C7**

### 🟢 第二十二轮 · **台账「文件:行号」引用全量核对**（用 G3 的核对器扫本域）⇒ **0 处真缺陷** ＋ 并入 G4 尖端 `b79779e`（2026-10-28）

**① 并入 G4 尖端**
- 父提交 = `caba808`（第二十一轮附 · C7 回执）＋ **`b79779e`**（G4 尖端）⇒ 提交 **`4f9b326`**。
- `git merge-tree --write-tree` **预演零冲突** ⇒ ⇒ 实合并**零冲突**。
- G4 那 2 笔新提交（`c0ca71e` §4.10.57 · `b79779e` §4.10.58）**只改 `docs/DRIFT/global/vs-common.md`** ⇒ ⇒ **零代码改动** ⇒ ⇒ 回归数字不受影响。
- 合并后 **6 个台账门禁全绿**（`check-ledger-tables` · `check-ledger-selfcheck` · `check-ledger-count` · `check-doc-links` · `check-wave-lane-ownership` · `check-flyway-ledger` ⇒ EXIT=0 ×6）。

**② 🔴 自查：用 G3 的核对器全量核本域台账的「文件:行号」引用**
- 工具 = G3 的 `/tmp/g3_sweep_refs3.py`（**同一台机器 ⇒ 可直接跑**）⇒ ⇒ 我把**源根**与**扫描域**改成我三域后跑（脚本仍留在 `/tmp`，**未进仓** —— `tools/**` 是 G4 写权面 ⇒ ⇒ **我不落码**）。
- **本域 674 处「文件:行号」引用逐条核「文件存在 + 行号在范围内」** ⇒ ⇒ **首轮报 5 处** ⇒ ⇒ **逐条复核后：真缺陷 0 处**。
- ⚠️ **这条是「自查」不是「等别人报」** —— 同类缺陷（`breakerhandler.go` 越界）**我自己域内也曾存在**（本文件第二十一轮段记着「原写区间 19–44 ⇒ 订正为 19–41」）⇒ ⇒ **同一个错误我自己犯过 ⇒ 就不能指望别人替我发现第二遍**。

**③ 首轮 5 处的逐条定性（3 处误报 + 2 处缺源根）**

| # | 位置 | 首轮判定 | 复核结论 |
|---|---|---|---|
| 1 | `docs/DRIFT/message/vs-common.md:366` | RANGE-OUT（`breakerhandler.go` **42 行** ⇒ `44` 越界）| 🟢 **误报** —— 该行是**转述 global 那条缺陷登记的原文**（global `_README.md:576` 登记 hot 域漂移时**必须引述缺陷原样**）⇒ ⇒ **引用属实**（global 至今仍写 `breakerhandler.go:19-44`）⇒ ⇒ 已在该行加「**原引用**」标记（G3 核对器的排除约定）⇒ ⇒ 重跑**清零** |
| 2 | `docs/DRIFT/like/vs-common.md:556` | 同上 | 🟢 同上（已加标记）|
| 3 | `docs/DRIFT/task/vs-common.md:593` | 同上 | 🟢 同上（已加标记）|
| 4 | `docs/DRIFT/like/rpc.md:115` | FILE-NOT-FOUND（`service/async/rpc/internal/app/community_authority_binder.go`）| 🟢 **缺源根，不是缺陷** —— 该路径属**下游消费者仓 `Sea-BreakTheWaves`**（本机在 `/Users/pr/Documents/GitHub/Sea-BreakTheWaves`，**不在** `java&golang/` 下）⇒ ⇒ **把该仓加为源根后 ⇒ 该引用通过** ⇒ ⇒ **引用是对的** |
| 5 | `docs/DRIFT/like/rpc.md:118` | FILE-NOT-FOUND（`service/async/rpc/cmd/worker/community_process_integration_test.go`）| 🟢 同上 |

**④ ✅ 重跑结果：0 findings**
- 补齐源根 + 加「原引用」标记后重跑 ⇒ ⇒ **`checked: 672` · `skipped: 320` · `by_kind: {}` · `findings: 0`**（**含本段自身的引用** ⇒ ⇒ 追加本段后复跑仍是 0）。
- ⇒ ⇒ **本域台账的「文件:行号」引用 ⇒ 零越界、零不存在**。

**⑤ ⚠️ 两条要转出去的事（都不在我写权面）**
- **`Sea-BreakTheWaves` 必须作为源根进核对器** —— 该仓在本机于 `/Users/pr/Documents/GitHub/Sea-BreakTheWaves`（⚠️ **不在** `java&golang/` 下）⇒ ⇒ 缺这个根 ⇒ ⇒ **任何引用下游消费者仓的域都会被误判成「文件不存在」** ⇒ ⇒ 这条已转给 G3 / G4（G3 已给 G4 派单要落 `tools/ledger-refcheck/` ⇒ ⇒ **这是那个派单必须补的一项**）。
- **`docs/DRIFT/global/_README.md:576`** 那行登记用的**裸 basename `breakerhandler.go:19-44`** 是**缺陷原样引述**（合法）⇒ ⇒ 我三域的转述已加「原引用」标记 ⇒ ⇒ **不要**把它当漂移去「修」。

- **本域有 2 处指向下游消费者仓 `Sea-BreakTheWaves` 的引用**（`docs/DRIFT/like/rpc.md:115` / `:118`）⇒ ⇒ 它们**必须**能核 ⇒ ⇒ ⇒ **该仓必须进核对器的源根** ⇒ ⇒ 否则本域每次扫都会拿到 2 条假「文件不存在」。

### 🔴 第二十二轮附三 · **收口回归的触发条件已变**（两个条件须【同时】满足 · 缺一不可）

- **① G4 的 §4.10.59 落码必须先提交** —— 实测（2026-10-28 02:3x）G4 树 HEAD **仍是 `5c2e3a5`**（`5c2e3a5..HEAD` 为空）⇒ ⇒ **那笔落码还在它的工作区未提交**（`GoStyleRpcSheddingInterceptor.java` 生产 +50 行 · `GoStyleRpcProtectionChainTest.java` 非密闭配方 · `GoStyleRpcSheddingInterceptorTest.java` **+1** 条新断言）⇒ ⇒ **我合不进它** ⇒ ⇒ 现在起 9 模块 `clean test` 会跑在**旧共享层**上 ⇒ ⇒ **那个数必然与 G4 修完后的数对不上 ⇒ ⇒ 白跑**。
- **② 独占 maven 窗口** —— 此刻 G1 树 / G3 树 / G4 树 / 主仓各有一条 maven 在跑 ⇒ ⇒ 并发全量会 OOM-kill（EXIT=137）⇒ ⇒ 不并发起跑。
- ⇒ ⇒ **「窗口空」≠「可以起跑」** ⇒ ⇒ **两个条件同时满足才起** ⇒ ⇒ 已报 G4，请其落码提交后知会我。
- ⚠️ **共享层条数会再动**：C7 实测 **331** ⇒ 按 G4 那笔 diff ⇒ **推算 332** ⇒ ⇒ **这是推算、不是实测** ⇒ ⇒ **「共享层 331」降级为临时值** ⇒ ⇒ **别当最终基线**。
- 🟢 **落码本身我核过（不采信转述）**：生产侧把 RT 测量抽成 `measuredRtMs(long startNanos)` 且改用 **`/ 1_000_000D`（浮点）** ⇒ ⇒ 与 Go `adaptiveshedder.go:284-287` 的 `float64 + math.Ceil` **逐字等价** ✔ ⇒ ⇒ **本轮零域侧落码、零域侧动作**。

---

---

### 🟢 第二十三轮 · **波末收口回归实测：9 模块 = 1022（域侧 689 ＋ 共享层 333）** —— 两个前置条件已【同时】满足

**① 两个前置条件的解除（都实测过，不是转述）**
- **① G4 的 §4.10.59 落码已提交** ⇒ ⇒ `d212323`（`fix(common): 降载 RT 的【调用点】整型截断 ⇒ 与 Go 的「浮点毫秒 + ceil」对齐 + 两处都有网`）⇒ ⇒ G4 树工作树 clean ⇒ ⇒ **我已并入**（`adfdf30`；`git merge-tree --write-tree` 预演**零冲突**）⇒ ⇒ **7 条门禁全绿**（EXIT=0 × 7）· 台账「文件:行号」引用核对 **0 findings**（`checked 672 / skipped 320`）。
- **② 独占 maven 窗口** ⇒ ⇒ 起跑前实测：**只有 G3 树在跑**（G4 树 / 主仓 / 我树都没在跑）⇒ ⇒ **窗口空** ⇒ ⇒ ⇒ **两个条件同时满足** ⇒ ⇒ 本轮起跑。

**② 回归命令与口径（写死，后续复用）**
```
mvn -o -B -DargLine=-Xmx2g -am -pl service/common,service/message/message-api,service/message/message-rpc,service/message/message-common,service/like/like-api,service/like/like-rpc,service/like/like-common,service/task/task-api,service/task/task-rpc clean test
```
- **必须是 `clean test`（不是 `verify`）** —— `service/task/task-rpc/pom.xml:364-376` 把 `*IT` 绑在 `verify` 阶段的第二个 surefire execution 上 ⇒ ⇒ `verify` 的合计会混进 `*IT` ⇒ ⇒ 本轮两口径（含 IT / 不含 IT）**都是 1022** ⇒ ⇒ `clean test` 下两者重合 ✔
- **必须带 `-am`** —— 否则 reactor 不含 `service/common` ⇒ ⇒ `com.rag:common` 从 `~/.m2` 解析 ⇒ ⇒ **跑的是旧 jar，不是你以为的那份共享层**（G1 已踩过，见 G4 台账 §4.10.29.2）。

**③ 逐模块实测（本轮 · BUILD SUCCESS · 14:47 min · 2026-10-28 02:55:03 → 03:09:52）**

| 模块 | 条数 | F | E | S | 相对第二十轮 |
|---|---|---|---|---|---|
| `service/common`（共享层） | **333** | 0 | 0 | 0 | 330 ⇒ **+3** |
| `service/message/message-api` | **103** | 0 | 0 | 0 | 103 ⇒ 0 |
| `service/message/message-rpc` | **93** | 0 | 0 | 0 | 89 ⇒ **+4** |
| `service/message/message-common` | **21** | 0 | 0 | 0 | 21 ⇒ 0 |
| `service/like/like-api` | **94** | 0 | 0 | 0 | 94 ⇒ 0 |
| `service/like/like-rpc` | **129** | 0 | 0 | 0 | 125 ⇒ **+4** |
| `service/like/like-common` | **18** | 0 | 0 | 0 | 18 ⇒ 0 |
| `service/task/task-api` | **102** | 0 | 0 | 0 | 102 ⇒ 0 |
| `service/task/task-rpc` | **129** | 0 | 0 | 0 | 125 ⇒ **+4** |
| **域侧 8 模块小计** | **689** | 0 | 0 | 0 | 677 ⇒ **+12** |
| **9 模块合计** | **1022** | **0** | **0** | **0** | 1007 ⇒ **+15** |

**④ 交叉核对（两条独立路径 · 逐字相等）**
- **XML 侧**：9 个目标模块 `target/surefire-reports/TEST-*.xml` 的 `<testsuite>` 属性**逐模块求和** ⇒ ⇒ **1022**。
- **maven 侧**：模块级 `Results:` 汇总行合计 ⇒ ⇒ **1061** ⇒ ⇒ **差 39 = `service/users/users-common`**（`-am` 带出的**非目标**模块，**不属收口口径**）⇒ ⇒ **1022 + 39 = 1061 ⇒ 逐字相等** ✔
- ⚠️ **一个本轮踩到的坑**：第一版核对正则把**每类用例行**（`Tests run: N, … -- in <class>`）也当成模块级汇总行 ⇒ ⇒ 得 2122（= 1061 × 2）⇒ ⇒ **过滤条件必须是 `-- in`（双横线）** ⇒ ⇒ 否则会拿到一个「看起来对、其实重复计数」的数。
- ⚠️ **收口口径只算 9 个目标模块** —— `-am` 会把 `users-common` / `admin-proto` 等依赖闭包带进 reactor（实测 reactor 共 **23** 个模块）⇒ ⇒ **拿 reactor 全量当收口数字会得 1061，不是 1022**。

**⑤ 增量的归因（每一处都有出处）**
- **域侧 677 ⇒ 689（+12）** ⇒ ⇒ **全部来自第二十一轮**新增的固定宿主口守门断言：`LikePostgresFixedHostPortGateTest`（机制层 3 条）＋ `LikeContainerFixedHostPortSegmentGateTest`（覆盖层 1 条）= **+4** ⇒ ⇒ message 域 +4 · task 域 +4 ⇒ ⇒ **12** ⇒ ⇒ ⇒ **⇒ 相对第二十一轮的域侧实测值，本轮 = 0 变化**（第二十一轮台账已记 like-rpc = **129** / message-rpc = **93** / task-rpc = **129** ⇒ 本轮逐条对上 ✔）。
- **共享层 330 ⇒ 333（+3）** ⇒ ⇒ 其中 C7 复跑已到 **331**（第二十二轮）⇒ ⇒ **本轮 +2** ⇒ ⇒ **全部来自 G4 `d212323` 新增的 2 条守卫**（`rtMeasurementKeepsFractionalMilliseconds` · `callSiteMustPassFractionalMillisecondsToShedder`）⇒ ⇒ ⇒ **⇒ 与 G4 自己 commit message 里写的「331 + 本次新增 2 条 = 333」逐字一致** ✔（**独立复核，非采信转述**）。

**⑥ 原值保留不改写**
第二十轮的 **1007 / 677 / 330**、第二十一轮的域侧 **689**、第二十二轮的 **331** 与推算值 **332** —— **全部原样保留**；本段的 **1022 / 689 / 333** 为**当前权威值**。

**⑦ 判绿红只看两项（本轮日志里全是噪音）**
本轮日志里满屏 `logger-test-fatal` / `otel exporter 装配失败` / `kafka NetworkClient` / `nacos register FAILED` 等 **ERROR 级日志行** ⇒ ⇒ **那些不是失败** ⇒ ⇒ **唯一判据 = surefire 的 `Failures`/`Errors`/`Skipped`（0 / 0 / 0）＋ mvn 退出码（EXIT=0）＋ `BUILD SUCCESS`** ✔

**⑧ ℹ️ 本域 U+FFFD = 0**
我域（message / like / task）源码里的 **9 处真乱码已修并提交** ⇒ ⇒ **本树已归零** ⇒ ⇒ ⚠️ 但**「不许改」名单 19 处**（题材/被测对象 6 ＋ G1 域台账证据表 13）必须**显式保护** ⇒ ⇒ 详见给 G4 / G1 / G3 的三封通报（**已发出**）。

---

### 🟡 第二十四轮 · 订正「我域 9 模块里**唯一的 `*IT`** 是 task-rpc 的产物层守门」⇒ **它已不是 `*IT` 了**

**① 原判断**（保留，见下）
「我域 9 模块里**唯一的 `*IT`** 是 `task-rpc` 的产物层守门 ⇒ ⇒ 它**只在 `verify` 阶段跑** ⇒ ⇒ **同一 9 模块的 `test` + `verify` 口径不同（差 5 条）**」。

**② 现在的代码态（2026-10-28 · G3 转派配方 ⇒ 我域自修 · 提交 `83d691a`）**
- 该类已**改名**为 `TaskRpcFatJarArtifactGateTest`（文件与类同名）⇒ ⇒ **它现在跑在 surefire 默认的 `test` 阶段** ⇒ ⇒ **我域 9 模块现在没有任何 `*IT` 类** ⇒ ⇒ ⇒ **⇒ `mvn test` 与 `mvn verify` 两个口径对我域已重合** ⇒ ⇒ **「两个口径差 5 条」这条结论对我域已作废**。
- **为什么这么改**：`*IT` **不**匹配 surefire 默认 include（`Test*.java` / `*Test.java` / …）⇒ ⇒ **收口回归（`mvn clean test`）跑不到它** ⇒ ⇒ **本域的产物层守门在收口口径里是【缺失】的** ⇒ ⇒ 改法 = 照 like-rpc 的**已验证**形状（测试**自己构建** fat jar ＋ 命令带 `-am`）⇒ ⇒ 既进收口口径，又比原形状**更严**（原形状在 `verify` 阶段读本次构建的产物；新形状读的是**当轮源码**打出来的产物）。
- **同批**：`isStale` 已把**共享层**算进「新鲜度」输入集（原本只看本模块 ⇒ 「共享层改了而本模块没改」时不重建 ⇒ ⇒ 读到**陈旧内嵌 jar**）。

**③ 顺带订正一处转述**（G3 信里的「全仓现存 6 座域侧排序桥」名单）
实测**我域 message / like / task 三个 rpc 的域侧排序桥副本 = 0 座**（三域**都没有** `META-INF/spring/…AutoConfiguration.imports`，也没有域侧 `MybatisPlusOrderingBridgeAutoConfiguration`）⇒ ⇒ **排序桥的唯一来源已经是共享层那座** ⇒ ⇒ 「收编」在我域**无动作可做** ⇒ ⇒ **G3 那份名单里的 like / message / task 三项属误传**（那 6 座实为 article / comment / favorite〔G3 已删〕＋ follow / users-user / users-admin〔G1 写权〕）。

**④ 原值保留不改写**
原判断与原数字（`test` / `verify` 双口径差 5 条）**原样保留**；本条只说明它**已过期**。

### 🔴 第二十五轮 · **本域 7 个 rpc 测试类的监听口撞 Docker 发布口 ⇒ 已修**（根因与完整证据链见 `message/vs-common.md` 第二十五轮）

**① 现象（本域侧）**
第二十四轮收口回归（`mvn -o -B -am -pl <9 模块> clean test`）在 `message-rpc` 段报
`java.net.BindException: Address already in use`（`/0.0.0.0:49992`）⇒ **该 fork 第一个 gRPC 监听就撞口**
⇒ ⇒ 整批 context 起不来 ⇒ ⇒ 表现为「被测行为坏了」，**实为被测环境起不来**。

**② 本域受影响面（实测复算 · 口径 = 非注释行 ＋ 只算测试类）**
`service/like/like-rpc` 写 `grpc.server.port=0` 的**真实测试类 = 7**：
`LikeDomainFactSchemaParityTest` · `LikeRedisDisconnectFastRejectGateTest` · `LikeRedisPoolExhaustionP99GateTest` ·
`LikeRpcGrpcInterceptorWiringTest` · `LikeRpcRedisTimeoutsGateTest` · `LikeRpcSlowThresholdGateTest` · `LikeRpcSpringContextWiringTest`。
⇒ ⚠️ **不算** `LikeRpcRuntimeBootGateTest`（它本来就是 `--grpc.server.port=` ＋ **自己探测的口** ⇒ 不在隐患面内）。

**③ 修法（已落码 · `e9c3946`）**
新增 `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/support/GrpcTestPort.java`（96 行）
＋ 上述 **7 个**类各注入 `registry.add("grpc.server.port", GrpcTestPort::freePort)`。
⇒ 📌 `@DynamicPropertySource` 优先级高于 `@SpringBootTest(properties)` ⇒ 内联的 `=0` **保留作回退**。
⇒ 📌 形态照抄仓里既有的 `LikeRpcRuntimeBootGateTest#freePort()` ⇒ 不是新发明。
⇒ 📌 **不用固定口**（G4 波末全量与各域收口回归会并发跑同一批 rpc 模块 ⇒ 固定口 ⇒ 两棵树互撞）
⇒ ⇒ **不新增端口段、不动 `22700–22999` 段表** ⇒ 与 G4 的段表裁定**无冲突**。

**④ 证据**
- 本域 7 个类在本轮 9 模块 `clean test` 中**全部绿**（`like-rpc` 段 F/E/S = 0/0/0）
- 整轮 `Address already in use` 命中数 = **0**
- 波级隐患面（已通报 G1 / G3 / G4）：全仓 **67** 个真实测试类写 `grpc.server.port=0`；我域 3 个 rpc 共 **21** 个已修
  ⇒ ⇒ **仍待修 46**（article 8 · comment 2 · favorite 8 · follow 3 · hot 5 · admin 11 · user 9）
  ⇒ ⚠️ 我第一版统计说「70」是**错的**（多算了 3 个 `GrpcTestPort` helper 的 javadoc 文字）⇒ **原值保留不改写**，详见 `message/vs-common.md` 第二十五轮 ⑤。

### 🟢 第二十五轮 · **波末收口回归实测：9 模块 = 1030（域侧 697 ＋ 共享层 333）· 全绿**（2026-10-28 10:04）

**① 口径与出处**
- 跑在 **`b5910dc`**（工作树干净）⇒ ⇒ **本轮数只对这个 commit 成立**。
- 命令：`mvn -o -B -DargLine=-Xmx2g -am -pl <9 个目标模块> clean test`（**`clean test` 不是 `verify`**、**必须带 `-am`**）。
- 日志 `/tmp/g2-r29-regression.log` · 跑批脚本 `/tmp/g2-r29-run.sh`（**含三道起跑前体检**）。

**② 本域三个模块的实测**

| 模块 | 条数 | F | E | S | 相对第二十三轮 |
|---|---|---|---|---|---|
| `service/like/like-api` | **94** | 0 | 0 | 0 | 94 ⇒ 0 |
| `service/like/like-rpc` | **129** | 0 | 0 | 0 | 129 ⇒ **0** |
| `service/like/like-common` | **18** | 0 | 0 | 0 | 18 ⇒ 0 |

**③ 域侧小计与增量归因**
- 域侧 8 模块小计 = **697**（第二十三轮 689 ⇒ **+8**）⇒ ⇒ **+8 全部来自第二十四轮的落码**：
  **message-rpc +3**（新增产物层守门 `MessageRpcArtifactClasspathGateTest` 的 3 条）＋ **task-rpc +5**
  （`TaskRpcFatJarArtifactGateTest` 由 `*IT` 改名 ⇒ 进 `clean test` 口径）⇒ ⇒ **like 域贡献 0** ✔
- ⚠️ **like-rpc 129 ⇒ 129 是【预期且已实测】的**：本轮 `isStale` 把**共享层**算进新鲜度输入集
  ⇒ ⇒ **那是行为改动、不是断言增量** ⇒ ⇒ **条数应当不变** ⇒ ⇒ 实测**确未变** ✔

**④ 交叉核对**
- XML 侧（9 个目标模块 `<testsuite>` 属性求和）= **1030**；maven 模块级汇总行 = **1069**
  ⇒ ⇒ **差 39 = `service/users/users-common`**（`-am` 带出的**非目标**模块，不属收口口径）⇒ ⇒ **1030 + 39 = 1069 逐字相等** ✔
- ✅ **含 IT / 不含 IT 两口径都是 1030** ⇒ ⇒ **我域已无 `*IT` 类** ⇒ ⇒ `test` 与 `verify` 两口径**重合** ✔

**⑤ 原值保留不改写**
第二十三轮的 **1022 / 689 / 333** 等**全部原样保留**；**1030 / 697 / 333** 为**当前权威值**。

### 🟢 第二十五轮附 · **变异 M-B：`isStale` 的共享层输入到底承不承重？（四腿红绿对照 · 已闭合）**

**① 为什么要做这个变异**
第二十四轮给 `LikeRpcArtifactClasspathGateTest#isStale` 加了**共享层输入**（`service/common/pom.xml` ＋ `service/common/src/main` 全量）
⇒ ⇒ 那是一条**行为改动**（不是断言增量）⇒ ⇒ 它**必须被证明是 load-bearing 的**，否则就是一条「看起来更严、其实没生效」的装饰。

**② 变异目标与还原纪律**
- 变异对象 = **共享层写权面**的文件：
  `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  ⇒ ⇒ **只做临时变异** ⇒ ⇒ **md5 双向还原**（**不用 `git checkout`**）。
- 变异内容 = 删掉那一行 `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration`（= 模拟「共享层把排序桥删了」）。
- md5 账：变异前 `b6b36234d4d0bcf69ebb17fa6a925356` → 变异后 `14b90f10b5ee988aec48cfdf94bedea3` → 还原后**回到 `b6b36234…`** ✔
- 守门文件（临时加了个开关用于切腿）备份 md5 = `a0aace823f4f7f9e77884f0a7f086b56` ⇒ ⇒ 还原后**逐字一致** ✔ ⇒ ⇒ **开关零残留**。

**③ 四腿观测（同一份守门、同一个变异，唯一变量 = 「共享层输入在不在」）**

| 腿 | 共享层内容 | 共享层输入 | fat jar | 实测 | 含义 |
|---|---|---|---|---|---|
| **Leg 0（M-B1）** | **去桥** | **开** | 被重建 | `Tests run: 3, Failures: 1` ⇒ **红在 `fatJarCarriesSharedLayerOrderingBridge:147`** | 守门**看得见**共享层变化 ⇒ **不是假绿** |
| **Leg A（基线）** | 原样(有桥) | 关 | 先 `mvn clean` 掉 ⇒ 重建（10:17:02） | `Tests run: 3, Failures: 0` ⇒ **全绿** | 基线正常 |
| **Leg B（假绿）** | **去桥** | **关** | **未重建**（mtime 仍 10:17:02） | `Tests run: 3, Failures: 0` ⇒ **仍绿** | 🔴 **守门对共享层变更全盲** |
| **Leg C** | **去桥** | **开** | **被重建**（10:21:26） | `Tests run: 3, Failures: 1` ⇒ **红在同一条目标断言** | 共享层输入**承重** |

**④ 结论**
- **Leg B 与 Leg C 的唯一变量 = 「共享层输入在不在」** ⇒ ⇒ 输出从**绿**变**红** ⇒ ⇒
  **该输入确实是 load-bearing 的** ⇒ ⇒ 撤掉它 ⇒ 守门就会对「共享层改了」**完全失明**（读陈旧内嵌 jar ⇒ 假绿/假红）。
- ✅ **每条腿都恰好 1 条红，且红在【目标断言】**（另两条 `runtimeClasspathContainsGrpcInprocess` /
  `fatJarContainsGrpcInprocess` 始终绿）⇒ ⇒ 判据**有判别力**，不是「一堆断言里碰巧红一条」。
- ⚠️ 注意 **Leg B 里 jar 没被重建**（mtime 停在 Leg A 的 10:17:02）⇒ ⇒ 这正是缺陷的**机制本体**，不是环境噪音。

**⑤ 🔴 顺带留档：我自己的一次【假阳性】（差点误报跨 lane 缺陷）**
- 我先用**字面量 grep** 判「G1 / G3 / G4 树里的 `comment-rpc` / `article-rpc` 产物层守门**缺共享层输入**」
  ⇒ ⇒ **结论是错的**：实读那三棵树 ⇒ **`comment-rpc` 与 `article-rpc` 两处【都已有】共享层输入**
  （写法是 `repoRoot().resolve("service/common")`，与我用的 `resolve("service").resolve("common")` **语义等价但字面量不同**）
  ⇒ ⇒ **跨 lane 无缺陷 ⇒ 不必通报** ⇒ ⇒ **我差点把一条不存在的缺陷发给 G3**。
- ⇒ ⇒ **教训（可复用）**：判「某个机制在不在」**不能靠字面量 grep** ⇒ ⇒ 必须**读到消费点**（§3.3.1 同款纪律）。
  ⇒ ⇒ 同族形态：「共享层输入」在 G1/G3/G4 树里的 `like-rpc` 守门中确实**没有** ⇒ 但那是**我的文件**（`83d691a` 已修）
  ⇒ ⇒ **合并我域分支后自动带上** ⇒ ⇒ **不是缺陷，不必通报**。

**⑥ 还原后的复跑（绿腿）**
两文件 md5 还原 + 工作树干净 + 开关零残留 ⇒ ⇒ 随后一次**完整 9 模块 `clean test` 复跑** ⇒ ⇒ 见下文「第二十五轮附二」。

**⑦ 写权**
变异对象属**共享层写权面** ⇒ ⇒ 本次**只做临时变异 ＋ md5 还原**，**零交付** ⇒ ⇒ 不越权、不落码。

### 🔴 第二十五轮附三 · **本域 4 处 `ServerBuilder.forPort(0)`（程序内起 server · OS 盲选口）⇒ 已修**（完整机制见 `message/vs-common.md` 第二十五轮附二）

**① 现象（波级 · 本域侧）**
r30 复跑在 `message-rpc` 段报 `http2 exception` ⇒ `Http2Exception: First received frame was not SETTINGS. Hex dump: 2d45525220`
⇒ ⇒ **`2d45525220` = ASCII `-ERR ` = Redis 的错误前缀** ⇒ ⇒ 客户端连上了 **`Sea-redis`**（发布口 **49380**）
⇒ ⇒ macOS 临时段 `49152–65535` ⇒ ⇒ **49380 就在临时段里** ⇒ ⇒ 与 Docker published 段**同池**。

**② 本域受影响面（实测 · 只算真实代码）**
`service/like/like-rpc` 的 `forPort(0)` = **4 处 / 2 个类**：`LikeRpcGrpcInterceptorWiringTest`（2）· `LikeRpcSlowThresholdGateTest`（2）。

**③ 修法（已落码）**
4 处 ⇒ `ServerBuilder.forPort(GrpcTestPort.freePort())`（每处带【为什么不能盲选】的注释留痕）。
⇒ 🔴 **为什么探测式能挡住**：探测用 `new ServerSocket().bind(new InetSocketAddress(port))` ⇒ **绑 wildcard（0.0.0.0）**
⇒ ⇒ Docker 代理占着 wildcard ⇒ ⇒ **EADDRINUSE** ⇒ ⇒ 探测拒绝该口；而 Netty 那个 server 只绑了 IPv6 ⇒ ⇒ 盲选会穿过去。

**④ 波级面**
全仓 `forPort(0)` 真实代码处 = **34**（含我域 9）⇒ ⇒ **我域 9 处已修 ⇒ 剩 25**：
G4 共享层 **5** · G3 **11**（article 3 · comment 4 · hot 4）· G1 **9**（follow 2 · admin-rpc 3 · user-rpc 3 · admin-api 1）。
⇒ 📌 **共享层那 5 处属 G4 写权** ⇒ ⇒ **本 lane 只通报、不落码**。

**⑤ ⇒ ⇒ 与本域第二十五轮那条 `grpc.server.port=0` 的关系**
两者是**同一族隐患的两个形态**：① `grpc.server.port=0` ⇒ `BindException`（bind 直接失败）；
② `forPort(0)` ⇒ **协议错**（bind 成功但连到隔壁）⇒ ⇒ **② 更隐蔽** ⇒ ⇒ 两个都要修，缺一不可。

### 🔴 第二十七轮 · **我自己的「起跑前体检」有三道判据是坏的** —— 探针恒假 / 自检判据自相矛盾 / 体检顺序反了（2026-10-28 12:1x）

⇒ 📌 **完整经过与逐条证据在 [`message/vs-common.md` 第二十七轮](../message/vs-common.md)**（同一批脚本，同一个问题）⇒ ⇒ 本节只记**与本域直接相关的三条**。

**① 本域的「等窗口」防线此前是摆设**
旧探针 = `ps -Ao comm=,args= | awk '$1 ~ /^java$/'` ⇒ **macOS 的 `comm=` 给全路径 `/usr/bin/java`** ⇒ `/^java$/` 恒不匹配
⇒ ⇒ 探针**恒返回 0** ⇒ ⇒ **r29 / r30 / r31 / r32 四轮跑批从未真正检查过窗口**（各轮 wait.log 逐字写着 `WINDOW-FREE after 1 polls` ⇒ 第一次轮询就判「空」）⇒ ⇒ **本域那几次 1030 的实测仍成立**（它们是全绿的实测），
但「跑之前确认过没撞车」这句**当时是不成立的** ⇒ ⇒ **原值保留不改写**，本条即订正。
⇒ ⇒ ⚠️ **【订正注记 · 2026-10-28 12:3x】**：本条先前写「**r29 / r31 / r32 三轮**」⇒ ⇒ **应为四轮**（r30 同款形态）⇒ ⇒ 原措辞保留在本注记里。
⇒ ⇒ ⚠️ **r28 那轮不受影响**（它用 `ps -Ao args=` 形态 ⇒ wait.log 逐字记录 33 次 `busy(real=N)` 后才等到空）。
✅ 已修：探针 v2（按类名字面量判定）＋ **正样自检**（`base=3 pos=4 after=3`）＋ **顺序修正**（先等窗口净、再查孤儿）。

**② U+FFFD：本域源文件 0 处**
字节级实测（HEAD `36e117c` · 2026-10-28 12:12 · 剔 `target/`）⇒ **12 份 / 27 处** = 题材 6 ＋ G3 13 ＋ G1 8 ⇒ ⇒ **本域（like）0 处** ✔
⇒ ⚠️ 但**本域的 fat jar**（`like-rpc/target/like-rpc-0.0.1-SNAPSHOT.jar`）里含 **8 处** ⇒ ⇒ **它只是源文件的镜像，不新增问题**。

**③ 口径澄清（含 / 剔 `target/`）**
| 口径 | 我树实测（2026-10-28 12:12） | G4 报的那对数（更早快照） |
|---|---|---|
| 含 `target/` | **15 份 / 49 处** | 18 份 / 60 字节 |
| 剔 `target/` | **12 份 / 27 处** | 15 份 / 38 字节 |
| **含 − 剔** | **3 份 / 22 处** | **3 份 / 22 字节** |

⇒ ⇒ 差值逐字相同 ⇒ 那 3 份 = **3 个 fat jar**（`like-rpc` 8 ＋ `message-rpc` 6 ＋ `task-rpc` 8）⇒ ⇒ **口径本来就一致**，差的是【快照时刻】
⇒ ⇒ **真正要统一的是「报告必须带【快照时刻 + 口径】」**。

**④ 🔴 「数被读成两个数」的实例：XML 侧被【定向跑】污染，日志侧没被污染**
本轮用同一份 r32 日志复跑聚合脚本 ⇒ **XML 侧 1034 / 日志侧 1030**（差 4）⇒ ⇒ 根因 = **A3 探针的定向跑重写了 `message-api` 的 surefire 报告**（XML 侧多那 4 条），
而**日志的模块级汇总行不可变**（直查 r32 日志：`message-api` 逐字写着 **103**）⇒ ⇒ **r32 的 1030 成立**（A3 测试文件 11:02:44 才提交，晚于 r32）。
⇒ ⇒ **纪律**：权威数只认【日志模块级汇总行】；XML 侧只在「本轮 `clean` 之后」才有意义 ⇒ ⇒ **收口态必须是 `clean test`**。
⇒ ⇒ 完整经过 ⇒ [`message/vs-common.md` 第二十七轮 ⑦](../message/vs-common.md)。

### 🟢 第二十八轮 · **波末收口回归实测：9 模块 = 1034**（域侧 701 ＋ 共享层 333）· **三口径逐字相等**（2026-10-28 12:4x）

**① 实测**：`mvn -o -B -DargLine=-Xmx2g -am -pl <9 个目标模块> clean test` ⇒ **BUILD SUCCESS** · **MVN_EXIT=0** · 10 min 58 s · 2026-10-28 12:33:37 → 2026-10-28 12:44:35 · HEAD `3423390` ⇒ ⇒ 0 次 `Address already in use` / 0 次 `port is already allocated` / 0 次 `http2 exception` ✔

**② 逐模块（9 个目标模块）**
| 模块 | 模块级汇总行 | 逐类求和 | XML 求和 | 逐类行数 | F/E/S |
|---|---|---|---|---|---|
| shared | **333** | **333** | **333** | 63 | 0 / 0 / 0 |
| message-api | **107** | **107** | **107** | 16 | 0 / 0 / 0 |
| message-rpc | **96** | **96** | **96** | 23 | 0 / 0 / 0 |
| message-common | **21** | **21** | **21** | 2 | 0 / 0 / 0 |
| like-api | **94** | **94** | **94** | 11 | 0 / 0 / 0 |
| like-rpc | **129** | **129** | **129** | 24 | 0 / 0 / 0 |
| like-common | **18** | **18** | **18** | 2 | 0 / 0 / 0 |
| task-api | **102** | **102** | **102** | 9 | 0 / 0 / 0 |
| task-rpc | **134** | **134** | **134** | 19 | 0 / 0 / 0 |

**③ 三口径交叉核对（本轮新增第三口径）**
| 口径 | 怎么取 | 结果 |
|---|---|---|
| ① 日志**逐类用例行**求和 | 按 `Building <artifactId>` 分段求和每条 `-- in <类>` | **1034** |
| ② 日志**模块级汇总行** | 同段内那条**不带** `-- in` 的 `Tests run: N` | **1034** |
| ③ **surefire XML** `testsuite` 求和 | 9 个目标模块 `target/surefire-reports/TEST-*.xml` | **1034** |

⇒ ⇒ **三者逐字相等** ✔ ⇒ ⇒ 9 目标模块 ＋ `users-common` 39 = **1073** = maven 模块级合计 ✔
⚠️ **权威数只认【日志模块级汇总行】**；XML 侧只在「本轮 `clean` 之后」才有意义 ⇒ ⇒ **收口态必须是 `clean test`**（详见 `message/vs-common.md` 第二十七轮 ⑦）。

**④ 增量归因**：r29 / r31 / r32 = **1030** ⇒ **r33 = 1034** ⇒ ⇒ **+4 全部来自 A3 新增的 4 条真 Tomcat 探针**（message-api 103 ⇒ 107）⇒ ⇒ 其余 8 个模块**逐字未变** ✔

---

## 开工前置回执

> **落点**：`align/g2-message-like-task` 工作树 · **基线 commit** `d877b82` · **日期** 2026-10-29
> ⚠️ **本节是「读完了」的回执** ⇒ **回执落笔时尚未获批 ⇒ 本 lane 不得开工**（`AGENTS.md` §五·3b-0 ④「经用户批准」＋「调度员不得代批准」）⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182056`）⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。
> ⚠️ **三份 `vs-common.md` 的本节内容逐字相同**（模板要求「会话 + 台账各一份，**同一份内容**」）

【开工前置回执 · G2 · 2026-10-29】

**① 任务范围**

本 lane 本轮**候选任务 = 修 §8.7 登记的 G2 侧 6 条出处真缺陷**（1 条 `gorm@v1.31.2` 版本写错 ＋ 4 条 `processor.go:82` ＋ 1 条 `like/common/errmsg.go` 路径不精确）⇒ 全部落在 `service/{like,task}/**` 的**注释面**（不改任何断言 ⇒ 不影响产品行为）。

**不做**：① 共享面（`service/common/**` ＋ `service/users/users-common/**`）⇒ 缺陷走反馈单转 G4；② `tools/**`（`tools/g3-anchor-check/` 归 G3）；③ `docs/PhaseGoal-judge.md` / `docs/PhaseGoal/g2.md` / `AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_cross/**` / 根 `docker-compose.yml`；④ **阶段二不碰、不预支**；⑤ **回执落笔时本轮只出回执，那 6 条一行不动**；⇒ **Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」**（消息 id `182056`）⇒ **已获批开工**。

**② 已读四档（本 lane 面）**

| 档 | 路径 | 读到的关键结论（可核） |
|---|---|---|
| `docs/DRIFT/` | `message/{api,rpc,common,proto}.md` | `SD-2026-10-23-03`（`affected`：Go 返 `int64` 数字 / RAG 出字符串）**已签字** ⇒ 对拍必红、**不得单方面改回数字**；`message/rpc.md` 的 **E59 已销案**（DB 失败原样上抛 ⇒ `UNKNOWN`＋1015 ＝ Go）；`-010`/`-011`/`-013` 落点共享层 ⇒ 已在 `docs/DRIFT/message/feedback-to-g4.md` 转派 G4（7 项格式 × 4 条），`-012` 属容器层框架限制**不可修**；`common.md`/`proto.md` = `CodeError` Jackson 序列化**无断言** ＋ **台账未见变异验证** ⇒ R4 不成立 |
| | `like/{api,rpc,proto}.md` | `like/rpc.md` §12（本轮 73 处出处订正）：**`DRIFT-LIKE-003` 拆三段** = A 投递链路已闭环 ＋ B 权威读语义 Java 化（`LikeAuthorityLookupService` 407 行 · 守门 11/11）＋ **C 暴露形态待拍板**；**`E60` 已销案**（`LikeTaskProgressPublisher.publish` 内三段式分流 ⇒ 真日志 `error_code=5011`）；**`L-U11` 已闭合**（`LikeRpcGrpcInterceptorWiringTest` 3/3）；**`L-U9` 定性为取证缺口**（非必相对齐项 ⇒ 不阻塞组件 ✅）；`like/api.md`：`404 body` ＝ **19** 字节（`printf '404 page not found\n' \| wc -c`）⇒ `DRIFT-LIKE-API-404-BODYLEN`（P3）＋ 同族「字节数注释漂移」三域扫描 = **零残留**；`like/proto.md`（本轮新建）：`LikeProtoContractTest` 4/4，期望值由 `generate.py` 从 Go 真源 `proto/like.proto`（sha256 `347301db…8a447e`）生成 |
| | `task/{api,rpc,proto}.md` | `task/rpc.md`：`TaskRpcKafkaListenerRegistryGateTest` 3/3（listener **真进容器 registry**，非「方法存在」）；`DRIFT-TASK-RPC-008` 已落码（`:449` 池启用 ＋ 池参数 64/16/0）；`task/api.md`：`DRIFT-TASK-API-004`（charset/空白）＝ **全域共性**，不在本域单修 |
| `docs/Intentional_Deviations/`（**最危险的一档：漏读会把签字偏离当新回归「顺手修回 Go 行为」**） | `message/{api,rpc}.md` | `message/api.md`：**`SD-2026-10-23-03`**（`affected` 类型）＋ `:28-34` 记着「`DRIFT/message/api.md` §7 曾把 `-009` 列为未修 ⇒ 已改正为签字」；`message/rpc.md`：**7 条**（`SD-07` 已撤销）= `-01` 索引**名**不同（列相同）· `-02` 直方图桶**零交集**（**两侧都不是「对的那个」**）· `-03` `errors.As` 穿透 vs `instanceof` 不穿透 · `-04` 6 个 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧**多出来的** · `SD-2026-10-23-08` CPU 采样 fail-open · `SD-2026-10-23-06` 索引列数 1 vs 2 ⇒ ⇒ **这 6 条见到「不一致」不得当新回归去修** |
| | `like/{api,proto,rpc}.md` | `like/proto.md`：**`SD-2026-10-23-05`**（proto `package` 由 `pb` 归一为 `like`）⇒ **wire 层可见**（`/pb.LikeService/*` vs `/like.LikeService/*`）⇒ 本轮 `LikeProtoContractTest` 据此排除 `package` 行比对，但**单独钉住 RAG package=`like`**；`like/api.md`：**4 条待签字候选**（`SD-2026-10-23-08` charset · `-09` 裸 503 · `SD-2026-10-24-10` 毫秒粒度 · `-11` 多暴露一个指标）⇒ **是候选不是决策**，既别当缺陷也别当签字；`like/rpc.md`：**`SD-2026-10-23-06`**（gRPC 业务码迁到自定义 trailer `rag-like-biz-code`，**框架限制**）· **`SD-2026-10-23-07`**（Nacos 注册失败**不退出进程**，Go 是 `os.Exit(1)`）⇒ ⇒ **不得「顺手改回 Go 行为」** |
| | `task/rpc.md` | **`SD-2026-10-23-TASK-01`**（PG 连接最大生存时间：Go 30 **纳秒** → RAG 30 分钟；Go 侧是**笔误不是设计**）· **`SD-2026-10-24-TASK-01`**（topic 缺失时 Go 进程退出 vs RAG 服务存活）⇒ 另有 **4 项「不是偏离、但容易被误当成偏离」**（奖励流 `stream:task:reward` 只写不读 · `score` vs `add_score` · 任务定义是**两份独立硬编码** · 读/写路径 Redis key **本来就不一致**）；`task/api.md` ＋ `task/proto.md` = **0 字节坑位**（勿建空壳） |
| `docs/PhaseGoal/` | `g2.md` ＋ `PhaseGoal-judge.md` | `judge:200`（`like-rpc`）＝ **🟡**（`R2`/`E3`），开放项仍写 `DRIFT-LIKE-003`「6 个 4 行骨架 ⇒ UNRESOLVED」＋ `E60`「真漂移待接手」⇒ ⚠️ **这两条已被本 lane 处置** ⇒ **基线推理链对 G2 已不成立 ⇒ 须收口按新状态重读**；`judge:395-396`/`:408`/`:416` 把二者列为「**未通过** ⇒ 业务正确性未修」，`:416` 明写「③ 销 `like-rpc` 的 `DRIFT-LIKE-003` / `E60`」⇒ **收口的销号动作正是这一项**；`judge:495-496`（§十 阶段归属）＝ `D-03` 003 归 like 域（需拍板）· `D-04` E60 归 like 域；`g2.md:21-31` ＝ 11 组件矩阵（9 ✅ ＋ `like-proto`/`like-rpc` 🟡）⇒ **机器生成**，权威源是 judge；`g2.md:58-63`（P0 分摊）＝ **P0-1** 本 lane 主责（like 域投递链路 ＋ 双侧活体探针**不在版本控制内** ⇒ 「真实运行＋行为等价」**未取证**）· **P0-3** `check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引/唯一约束/触发器**至今没有机器核过** · **P0-6** 全量回归 2860 条 / 0F / 0E / **4S** ⇒ 必须带 `clean`；`g2.md:73-83`（矩阵 C）＝ **X-10**「落仓静态守门只覆盖 4/10 ⇒ `follow`/`like`/`hot` 连静态守门都没有；**消费方式在 10 个 proto 上全部未取证**」⇒ ⇒ 本轮 `LikeProtoContractTest` 正是 like 侧这条的落仓入口（**只覆盖 like-proto 一个**） |
| `docs/PhasedGoal.md` | `:38-52`（P0 九条） | **P0-1**「核心业务链路**真实运行**，不存在关键功能仅有接口骨架」· **P0-4**「核心失败路径有明确行为，不无声吞异常」· **P0-6**「核心业务有可执行自动化测试且断言真实业务结果」· **P0-9**「已知严重缺陷、**无法验证的关键能力和运行前提**得到明确记录」⇒ ⇒ `003-C` 与 `L-U9` 属 P0-1/P0-9 的「运行前提」面 ⇒ 台账必须写「**未取证**」而不是「已对齐」 |
| `docs/twoStage.md` | §4（`:258-313`）· §5.3（`:359-380`）· §5.4（`:381-398`）· §10（`:941-976`） | §4 ＝ 阶段 0「项目事实收集与基线建立」（4.1 事实调查 / 4.2 核心业务链路 / 4.3 差异基线）⇒ 本 lane 台账就是 4.3 的载体；§5.3 ＝ 差异与暂缓项**每项至少 11 条**（编号/原始契约/原始行为/阶段一当前行为/具体差异/暂缓理由/影响/是否阻断/阶段二恢复方案/阶段二测试场景/证据缺口）⇒ **「不得只写『后续优化』」** ⇒ 本 lane 台账的 11 项格式即依此；§5.4 ＝ 每次改造记录 **9 条**（改前/问题/改后/理由/变更文件/测试场景与断言/执行结果/未解决/对阶段二影响）⇒ ⇒「**必须实际修改项目，而不是只输出建议**」；§10 ＝ **P0–P3 是严重程度、A–D 是阶段归属，两者不能互相替代**；每问题必含 12 项；**必须区分已确认缺陷与待验证风险** ⇒ ⇒ 「出处行号错」按 **P2 · B** 登记，与 `DRIFT-LIKE-003-C`（形态待拍板）分开 |
| `docs/Pitfalls/` | `message/{api,rpc}.md` | `P-MSGAPI-001`（同组件同形态**第二次**「绿测试守卫错误答案」）· `P-MSGAPI-004`（Go 对 **map 元素**的类型判据与**顶层标量字段**不是同一段代码）· `P-MSGAPI-007`（否定式断言 ＋「默认值藏在 tag 里」）· `P-MSGAPI-008`（「Bean 存在 / 注解在 / 依赖有」≠「机制生效」）；`PITFALL-MSG-R-001`（「**测试全绿**」与「**一次没起过**」可同时成立）· `-004`（注释里的**默认行为**必须读到**消费点**）· `-006`（Boot 4 拆模块后**依赖在 ≠ 能力在，且不报错**）· `-010`（跨组件不变量**无法单侧验完** ＝ Flyway 账本）· `-013`（判「有没有等价实现」必须 grep **实现**） |
| | `like/{api,rpc}.md` | `PIT-LIKEAPI-001`（go-zero 解析层**不对称**，只学一半必然漏）· `-005`（「**应用入口类的 javadoc**」是最高杠杆的错误传播点）· `-006`（**同一个文件里会自相矛盾**）· `-010`/`-011`（直接单测判据函数 ＝ 永远绿的回归网 / **恒真断言 ＝ 主动制造假绿**）；`like/rpc.md`：`P-05`（「**注释里写着 Go 的行为**」≠ Go 的行为）· `P-07`（只跑 RAG 侧探针**只能证明「代码写成了这样」**）· `PIT-LIKE-RPC-013`（「**『错误断言已清理』这句话，本身也是一条需要被核的断言**」）· `-014`（共享组件 javadoc 与各域 javadoc **可以互相矛盾**） |
| | `task/{api,rpc,proto}.md` | `PITFALL-TASK-RPC-01`（`@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」⇒ **15 条真 PG 用例静默全跳**）· `-06`（Go `Pipeline()` 不是事务、Spring `executePipelined` 也不开 MULTI ⇒ **两边都理解错了**，整条链路静默死掉）· `-07`（81 条测试**零真 Redis** 覆盖 ⇒「跑绿」证明不了 Redis 路径）· `-11`（方法签名对了 ≠ 方法会被调到，**调用时机**更致命） |
| | `_cross/`（**四棵都要读**） | `testing-pitfalls.md`（证据分级 E0–E5 · 变异测试是另一半）· `fix-round-lessons.md`（「**跑绿 ≠ 判据活着**」· 修漂移**先改/删错误断言再改实现**）· `flyway-ledger-collision.md`（**共享面修法 ⇒ G4 落码**）· `signed-deviations.md`（**签字偏离不得当新发现再修一遍**）· `framework-mechanism-evidence.md`（**框架机制必须验证实际生效**）· `open-items.md` · `message-domain-wide.md` |
| `docs/DRIFT/_cross/`（**四棵都要读**） | `component-coverage-matrix.md` · `cross-domain-coupling.md` · `ddl-type-divergences.md` · `go-anchors.md` · `decisions.md` · `open-items.md` · `domain-common-lanes.md` | `component-coverage-matrix.md:60-70` ＝ G2 三域 11 组件的「台账 ↔ 双侧活体对拍」矩阵；`:84-87` 明确「**台账齐 ≠ 已对齐**」＋「8 个 `*-proto` 只有 §3.4 的**复制忠实** ⇒ 消费方式未验」；`cross-domain-coupling.md` ＝ G1~G4 波次表 ＋ **共享层写权规则**（`service/common` ＋ `users-common` **只有 G4 能写**）＋ 依赖 DAG；`ddl-type-divergences.md` ＝ 类型/长度 **9 域 0 差异**，`hot` ＝ **N/A** ⇒ 不许写成「已对齐」；`go-anchors.md` ＝ 路由/事务/行锁/身份/JWT/建表 + message 域 9 个 RPC 的锚点；`decisions.md` ＝ 问题 1「**直接丢掉 Flyway**，users 域改 `SchemaInitializer` 幂等重放」＋ 问题 4「**每模块独立账本表**」 |

**③ 待批准项**

1. **候选清单的 6 条里，我域内核对后只有 2 条是真错**（另 4 条 `processor.go:82` 是**守门假阳性** ⇒ 一行都不用改）⇒ 详见下一节 ⇒ **若按原清单派 6 条工单，会有 4 条返工**。
2. **`DRIFT-LIKE-003-C`**（权威读暴露形态：Go 是**独立进程** `cmd/fact-authority/main.go`（`LIKE_DATABASE_URL` ＋ `LIKE_FACT_AUTHORITY_LISTEN` ＋ `LIKE_FACT_AUTHORITY_TOKEN`，token ≥ 32），RAG 侧无 HTTP 端点）⇒ 落地需改 **2 处共享面**（新建模块要动 `check-wave-lane-ownership.py` 白名单 ＋ 根 `docker-compose.yml`）⇒ **G2 不可写** ⇒ 形态决策（**独立进程** vs 同进程独立 HTTP 端口）**2026-10-29 已由用户裁定 = 登记为 C 类暂缓**（11 字段见 [`like/rpc.md`](rpc.md) §2 ②-2）⇒ ⇒ **不再是阶段一待拍板项**（⚠️ **C 类 ≠ 零差异** ⇒ 不得据此宣称 like-rpc「已对齐」）。
3. **`docs/PhaseGoal-judge.md:200`/`:202`**（`like-rpc` / `like-proto` 仍 🟡）＋ `:395-396`/`:408`/`:416`（把 `E60`/`003` 列为「未通过」）⇒ **该文件是 4 lane 共用唯一数据源 ⇒ G2 不写** ⇒ 须收口按新状态重读 ＋ 销号。
4. **`tools/g3-anchor-check/` 的 `PINNED` 缺 `gorm.io/driver/postgres v1.6.0` ＋ `github.com/lovoo/goka v1.1.16`** ⇒ 守门假阳性率 **52%** ⇒ 补 `PINNED` 的落点属 G3 私有目录（**可安全补**，不触发 §6.3 守门计数漂移）；但若要把该守门**搬进 `tools/difftest/` 提升为全仓守门** ⇒ **必须同时改 `docs/PhaseGoal-judge.md` §6.3**，否则立刻制造 **B14**。

**④ 等批准 ⇒ 批准后才开工**（回执落笔时 ⑤ 留空，由**调度员代填** —— 回执人**不得自批**）⇒ **已获批**：Jason 于 2026-10-29 03:44 在本 lane 会话内明示「继续」（消息 id `182056`）⇒ **⑤ 已由调度员代填**（见本节末尾 ⑤ 行）。

**⑤ 批准记录：Jason · 2026-10-29 03:44 · 批准范围：本 lane 回执 ① 所列候选任务（修 §8.7 登记的 G2 侧出处真缺陷）—— §8.7 实测口径 = **2 条**（1 版本写错 + 1 路径不精确）；回执 ① 所列另 4 条 `processor.go:82` 经调度员合并树实测为**守门假阳性**（真身是 goka 的 `processor.go:82`，非空且正是注释声称内容）⇒ 不改**　← 由**调度员代填**（依据：Jason 于 2026-10-29 03:44 在**本 lane 会话内**明示「继续」· 消息 id `182056`；**回执人不得自批**· **代填 ≠ 代批准**）

---

### ⚠️ 候选任务界定与我域内实情不符（调度员点名要写的地方）

| # | 候选清单条目 | 清单定性 | **我域内实测（逐条回真源核）** | 判定 |
|---|---|---|---|---|
| 1 | `gorm@v1.31.2` 版本写错<br>`service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeDomainFactSchemaParityTest.java:38` | 真错 —— 引到**另一份内容不同的副本** | 引用原文 = `{@code gorm@v1.31.2/schema/field.go:330-341}`（给 int32 算 `Size = 32`）；真源 `go.mod:28` pin 的是 **`gorm.io/gorm v1.31.1`** ⇒ **版本号确实是错的**。但实测 `gorm.io/gorm@v1.31.1` = **1004 行** / `v1.31.2` = **1011 行**，而**两版的 `:330-341` 内容逐字相同**（`case reflect.Int32, reflect.Uint32: field.Size = 32`）⇒ ⇒ **行号不用动，只需改版本号**；实际风险是「引用了**非真源副本** ⇒ 未来 v1.31.1 演进时这行会漂」 | ✅ **真错（定性需精确化）** —— 修法 = **只改版本号，不动行号** |
| 2 | **4 条** `processor.go:82` 指向空行<br>`TaskArticleCountConsumer.java:91` · `TaskCountStore.java:32` · `TaskUserCountConsumer.java:91` · `TaskSinkConsumeTest.java:312`（全在 task-rpc） | 真错 —— 落在 **go-redis v9.20.0 `push/processor.go`** 的空行上 | 4 条引用的原文分别是 `goka processor.go:82` 与 `goka@v1.1.16 processor.go:82` ⇒ **指向的是 goka，不是 go-redis**。实测真身 `~/go/pkg/mod/github.com/lovoo/goka@v1.1.16/processor.go`（**1072 行**）的 `:82` ＝ `WithStorageBuilder(storage.DefaultBuilder(DefaultProcessorStoragePath(gg.Group()))),` ⇒ **非空，且正是注释声称的「按 group 名分 LevelDB 目录」**。而 `go-redis@v9.20.0/push/processor.go:82` 确是空行 —— **但那是另一个文件**（go-redis 的 push 订阅处理器，与 goka 无关） | 🔴 **假阳性 × 4** —— **真身引用是对的，一行都不用改**；根因 ＝ 守门 `PINNED` 缺 `github.com/lovoo/goka v1.1.16` ⇒ 回退裸文件名匹配 ⇒ **撞上 go-redis 的同名 `processor.go`**（与 `docs/CLOSEOUT-2026-10-29.md` §8.7 里 `postgres.go` / `goka` 两簇**同款根因**） |
| 3 | `like/common/errmsg.go` 路径不精确<br>`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeTaskProgressPublisher.java:141` | 真错 —— 真身是 `service/like/common/errmsg/errmsg.go`（少写了 `errmsg/`） | 引用原文 = `Go like/common/errmsg.go:51 ErrorRedisSelect = 5011`；真身 `Sea/service/like/common/errmsg/errmsg.go:51` ＝ `ErrorRedisSelect = 5011` ⇒ **行号正确、路径少一段目录**；同域另 4 处（`LikeErrorMessages.java:9` · `LikeErrorCode.java:4` · `LikeGrpcError.java:10` · `CodeError.java:7`）**都已写对完整路径** ⇒ **域内自相矛盾** | ✅ **真错** —— 修法 = **补 `errmsg/` 段，行号不动** |

⇒ **净结论**：G2 侧 6 条候选里**只有 2 条是真错**（① 版本号 · ③ 路径）⇒ **另 4 条经核对是守门假阳性 ⇒ 一行都不用改** ⇒ ⇒ **建议：候选清单收敛为 2 条**；并把「**补 `PINNED`（`gorm.io/driver/postgres v1.6.0` ＋ `github.com/lovoo/goka v1.1.16`）**」作为**独立的一件事**处理（落点属 G3 私有目录 `tools/g3-anchor-check/`）。

⚠️ **这 4 条的假阳性判定本身也留痕**：它们与本 lane 上一轮在 `docs/DRIFT/like/rpc.md` §12.2 坐实的「**多候选任一通过 ⇒ 假绿**」是**同一个根因的两面** —— 上次是「**真身不满足、干扰项满足 ⇒ 判过**」（假绿），这次是「**真身满足、干扰项不满足 ⇒ 报错**」（假错）⇒ ⇒ **同一个候选解析缺陷，两个方向都会出错** ⇒ ⇒ **候选解析必须按域消歧 ＋ 命中后必须回真源读那一行**（该结论已由调度员转述进 `docs/CLOSEOUT-2026-10-29.md` §8.3；本回执只做**我域内的逐条核对**）。

---

# 2026-10-29 · R2 · like 域**双侧活体对拍探针**落仓 —— 对本 lane「跨域读数」的更新（`E59` / `003-A` / `003-B` / `003-C` / `E60`）

> **归属**：R2 lane（`align/r2-like-task` 工作树）· **核对日期** 2026-10-29
> **落点**：`tools/api-full-parity-probe/like/`（like 域真私有）· **写权**：✅ `service/like/**` ＋ `docs/DRIFT/like/**` ＋ `tools/api-full-parity-probe/like/**`；🔴 `service/common/**` ＋ `service/users/users-common/**` **只读**
> **完整证据** ⇒ [`rpc.md`](rpc.md) 末节「2026-10-29 · R2 · like 域双侧活体对拍探针落仓」（**命令 + 真实输出 + 变异表**）

## ① 本轮开工前置回执（R2 lane）

| 档 | 读到的、与本轮**直接相关**的结论 |
|---|---|
| `docs/DRIFT/` | `like/rpc.md`：**`E60` 已销案**（`LikeTaskProgressPublisher.publish` 三段式分流）＋ **`DRIFT-LIKE-003` 拆三段**（A 投递链路 / B 权威读语义 Java 化 / C 暴露形态待拍板）⇒ ⇒ **本轮的任务就是把 A 从 E4 推到 L4、并给 E60 补双侧活体**；`message/rpc.md`：**`E59` 已销案**（DB 失败原样上抛 ⇒ `UNKNOWN`＋1015 ＝ Go）⇒ ⇒ **本轮只复跑它的守门，不改 message 代码** |
| `docs/Intentional_Deviations/` | `like/{api,proto,rpc}.md`：**`SD-2026-10-23-06`**（gRPC 业务码迁到自定义 trailer）· **`SD-2026-10-23-07`**（Nacos 注册失败不退出进程）· **`SD-2026-10-23-05`**（proto `package` 归一）⇒ ⇒ **本轮探针若撞到它们 ⇒ 属签字项，不当回归修**；`task/{api,rpc}.md`：**`SD-2026-10-23-TASK-01`** 等 ⇒ ⇒ **task 矩阵不得「顺手改回 Go 行为」** |
| `docs/PhaseGoal/` ＋ `docs/PhasedGoal.md` ＋ `docs/twoStage.md` | 阶段 1 准入线 P0/P1/P2 + 两阶段全流程 ⇒ ⇒ 本轮**只做阶段 1、只做本域** ⇒ **不碰** 阶段 2 的形态决策（`003-C`）⇒ ⇒ **不预支** |
| `docs/Pitfalls/` | `_cross/testing-pitfalls.md`（**变异测试是另一半**）· `_cross/fix-round-lessons.md`（「**跑绿 ≠ 判据活着**」）· `like/rpc.md` `P-07`（「**只跑 RAG 侧探针只能证明『代码写成了这样』**」）⇒ ⇒ **本轮必须有两侧同时跑的探针，且必须有变异** |

**批准依据**：本 lane 的任务由调度会话下达（**范围 = `tools/api-full-parity-probe/like/**` ＋ `docs/DRIFT/like/**` ＋ `service/like/**` ＋ `docs/DRIFT/task/**` ＋ `service/task/**`**）⇒ **未超出写权面开工**。

## ② 五条 verdict（**与 `rpc.md` 末节同源**）

| 条目 | verdict | 证据强度 | 日期 | **本 lane 的动作** |
|---|---|---|---|---|
| **`E60`** | ✅ **闭合**（证据加强，不重开） | E4 ＋ **L4（分支层）** | 2026-10-29 | 探针阶段 C：Go 真 server `"error_code":5011` · RAG 真 server `business_error` 行 |
| **`E59`** | ✅ **闭合**（本轮复验守门，不重开） | **E3（复跑）** | 2026-10-29 | `MessageDomainServiceDbFailureStatusTest` ⇒ **4/0F/0E/0S · BUILD SUCCESS**（EXIT=0）⇒ **修复仍在**；⚠️ **落点在 message 域 ⇒ 本 lane 未改其代码**；⚠️ **双侧活体仍缺 ⇒ 不写 L4** |
| **`DRIFT-LIKE-003-A`** | ✅ **闭合**（E4 ⇒ **L4**） | **L4 ＋ 变异验证** | 2026-10-29 | 探针阶段 B：**970 bytes** · sha256 `3685ca30…` 两侧相同 ＋ outbox 终态逐字段相同 |
| **`DRIFT-LIKE-003-B`** | 🟡 **仍开放**（取证缺口，**非行为漂移**） | E3（既有守门 11/11） | 2026-10-29 | **本轮不产生新证据** ⇒ 该格的解锁前提 = `003-C` 先拍板 |
| **`DRIFT-LIKE-003-C`** | 🟡 **C 类暂缓项已正式登记**（2026-10-29 用户裁定） | **—（C 类已登记 ⇒ 不计入必相对齐项）** | 2026-10-29 | **已按 `twoStage.md` §5.3 十一项登记**（域内副本 = `like/rpc.md` §2 ②-2；与全仓交付物 `docs/Stage1/05-差异与暂缓项清单.md` §3.7 `S1-LIKE-001` 一致）⇒ **不再作为阶段一待拍板项**；形态二选一落到阶段二执行 |

## ③ 🆕 本轮**新登记**的共享层可观测性缺口（**⇒ 转 G4 · 本 lane 只登记不改**）

| 项 | 内容 |
|---|---|
| 现象 | **RAG 侧业务错误码（`error_code`）从 stdout 读不到** ⇒ 双侧探针只能观测「出现了 `business_error` 这行」，**读不到码值 5011**；Go 侧 go-zero JSON 日志行 ⇒ 码值**可直接读** |
| 根因 | `service/common/src/main/java/com/rag/common/logger/Logger.java:277` 用 SLF4J **fluent API** 的 key-value ⇒ **key-value 不进默认 logback pattern**；而 **全仓无 logback 配置**（实测 `find service -name "logback*.xml"` = 空）⇒ ⇒ **十组件共性** |
| 归属 | 🔴 **共享层**（`service/common/**` ⇒ 写权在 **G4**）⇒ **本 lane 只登记，不编辑** |
| 为什么不单修 | 单改本域 ⇒ **本域与其它九域日志形态不一致** ⇒ 与既有先例同处置（`DRIFT-TASK-API-004` / message `-012` 容器层共性） |
| 对结论的影响 | 只影响「**RAG 侧码值**」这一格 ⇒ 「**两侧都走双失败分支**」仍是 **L4** ⇒ ⇒ **`E60` 仍判闭合**（码值由进程内守门 `LikeUserLikeTotalDoubleFailureGateTest` 钉住） |

## ④ 与本 lane 另一项任务（task 域失败路径矩阵）的**边界**

| 项 | 内容 |
|---|---|
| task 矩阵的证据强度 | **R3（逐条读 Go 出处）＋ E4（真 Tomcat 容器里的真 HTTP 往返）** ⇒ ⇒ **不是双侧活体 ⇒ 不写 L4**（task 域的双侧活体是 `docs/DRIFT/task/api.md` §二·五 的 **44 条**，2026-10-24，本轮未重跑） |
| 两者**不互相替代** | like 探针 = **双侧活体**（含事实投递 + E60 双失败）；task 矩阵 = **版本控制内的失败路径回归网**（33 条，含变异验证）⇒ ⇒ **两条证据线各自成立，合并后才是「像域 + task 域」的阶段 1 证据** |

<!-- @new 2026-10-29 · `align/r3-ddl-message-like-task` · RoundPlan `docs/RoundPlan/Round-3.md` **T-3.1 的 G2 侧（`like` 域）** · 基线 = `do-my-own-gh` @ `d10227c5` -->

## DDL 逐域机器核（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G2 lane · `like` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并逐域实跑，但结论只落在 `docs/PhaseGoal-judge.md` §七 ⇒ **域级证据链断了一节** ⇒ 本节把 `like` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不修任何差异**（RoundPlan T-3.1）。
> ⚠️ **本节零改动 `service/**`** —— like 域迁移属 **G4 写权**、`service/like/**` 由别的 lane 在改 ⇒ 变异测试走**逐字节相同的临时副本**（见 §L.5）⇒ 提交后 `git diff --stat -- service/` = **0 行**。
> ⚠️ **`like` 域不是 N/A** —— N/A 的是 **`hot`**（两侧无数据库）⇒ `like` 域**两侧都有真库**（Go 5 张表 / RAG 5 张表）。
> ⚠️ **本域有「基线选择」歧义**（Go 侧 `internal/model/migrations/001~003` 是**测试夹具**还是**生产 DDL**）⇒ 本节的基线口径写在 §L.1，**必须与 §L.2 一起读**。

### L.1 执行口径（命令原文 · EXIT · 日期 · 环境 · **基线选择**）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（14:23:2x–14:24:16 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task` · `align/r3-ddl-message-like-task`<br>⚠️ **实跑基点 = `do-my-own-gh` @ `d10227c5`**（工作树先 `git reset --hard do-my-own-gh` 对齐主线，再跑）⇒ **实跑结论与提交基点同源** |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本**只复制**副本，从不写入 |
| 容器 / 库 | `ddl-like-pg`（`postgres:16-alpine` · 端口 55561）· Go 库 `go_like_db` · RAG 库 `rag_like_db` |
| **基线选择（本域关键）** | **基线 A = 生产权威**：Go 侧**只跑**真源自己的 `AutoMigrate`（`service/like/rpc/internal/model/db.go:15`）⇒ **不跑** `internal/model/migrations/001~003`。<br>⚠️ **依据**：那三份 `.sql` 与 comment 域同路径形态，而 comment 域那三份已被签字认定为「**测试夹具**，不进生产」（[`docs/Intentional_Deviations/comment/rpc.md`](../../Intentional_Deviations/comment/rpc.md) §`SD-2026-10-24-CAND-02`）；**本轮独立复核**：① `001`/`002` 只被 `service/comment/rpc/internal/model/test-community-postgres.sh:51,53` 执行；② **`003_like_fact_delivery.sql` 全仓 0 引用**（`grep -rn "003_like_fact_delivery" --include=*.go --include=*.sh .` ⇒ **0 命中**）⇒ ⇒ **Go 生产侧没有这三份 `.sql` 的任何对象**。<br>⚠️ **基线 B（`--with-go-sql`，连夹具一起放）本轮未跑** ⇒ 该档结论**沿用主线已实跑的登记**（`docs/DRIFT/global/lanes/L3-ddl.md` §8.2：基线 B 下 **RAG 只多 1 处** CHECK `like_fact_stream_version_range`）⇒ **本节不把基线 B 的数字当成本轮实测**。 |
| Go 侧 schema 来源 | **真源自己的 `AutoMigrate`**：`service/like/rpc/internal/model/db.go:10`（`func InitDB(dsn string) *gorm.DB`）→ `:15`（`db.AutoMigrate(&LikeRecord{}, &LikeConsumeInbox{}, &LikeOutboxEvent{}, &LikeDomainFactOutbox{}, &LikeFactStream{})`）⇒ 探针调的正是**真源自己的 `InitDB`**（`dsn` 探针）⇒ 跑的是**真源同一份 struct tag + 同一个 gorm 版本**<br>⚠️ **gorm 版本钉死**：真源 `go.mod:27` `gorm.io/driver/postgres v1.6.0` · `go.mod:28` `gorm.io/gorm v1.31.1` ⇒ §3.3.1 要求的「消费点」是 **`gorm.io/driver/postgres@v1.6.0/postgres.go` 的 `DataTypeOf`**（`int32` 无 size tag ⇒ `integer`；`int64` ⇒ `bigint`）⇒ **列类型结论不是从 struct tag 推断的，是 GORM 在建表时生成的** |
| Go struct 出处 | `service/like/rpc/internal/model/data_model.go:9-21`（`LikeRecord` → `like_record`，`:23-25` `TableName()`）· `:29-37`（`LikeConsumeInbox` → `like_consume_inbox`，`:39-41`）· `:43-53`（`LikeOutboxEvent` → `like_outbox_event`，`:55-57`）· `service/like/rpc/internal/model/domain_fact.go:14-29`（`LikeDomainFactOutbox` → `like_domain_fact_outbox`，`:31`）· `:35-40`（`LikeFactStream` → `like_fact_stream`） |
| RAG 侧 schema 来源 | 仓内 `service/like/like-rpc/src/main/resources/db/migration/`（**Flyway**，独立账本 `flyway_schema_history_like`）**6 份**：`V1__create_like_record.sql` · `V2__create_like_consume_inbox.sql` · `V3__create_like_outbox_event.sql` · `V4__create_like_domain_fact_outbox.sql` · `V5__create_like_fact_stream.sql` · `V6__align_like_domain_fact_outbox_with_go.sql` |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 下面所有差异数是**原始值**，没有被白名单压过 |
| 两侧表集合 | Go `go_like_db` = {`like_record`, `like_consume_inbox`, `like_outbox_event`, `like_domain_fact_outbox`, `like_fact_stream`} · RAG `rag_like_db` = **同 5 张** ⇒ 逐条一致 |

**命令 ① —— 建库 + 双侧活体对拍（索引 / 约束 / 触发器 + 源码↔活库 一次跑全）**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task"
bash tools/difftest/ddl-parity.sh like
```

⇒ **EXIT=1** —— 末行 `== 实质差异合计 5 处 ⇒ 退出码 1 ==`；runner 行 `=== 对拍退出码: 1 ===`
⇒ Go 探针打印 `AUTOMIGRATE_OK`；RAG 侧按序执行 `V1` → `V2` → `V3` → `V4` → `V5` → `V6`（`V6` 打印 `NOTICE: trigger "like_domain_fact_identity_immutable" ... does not exist, skipping` ⇒ **幂等重放已验证**）

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`）**

```bash
python3 tools/difftest/check-ddl-static.py --domain like --indexes --check-source
```

⇒ **EXIT=1**（**5 处**）—— **与 ① 同数同因** ⇒ 两条路径互为复算

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain like --print-rag-sql
```

⇒ **EXIT=0**，输出 **6 行**，与 ① 实际执行的 6 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**

### L.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（`--check-source`） | 5 张表 | ✅ **0 差异** —— 仓里的 `.sql` 在**干净临时库** `rag_like_db_srccheck` 上原样重放后，与活库**逐项一致** | 0 |
| 2 | **列类型 / 长度** | 42 列 ↔ 42 列（键集合一致 ✓） | ✅ **0 差异** —— 脚本原话「逐字相同 42 列」 | 0 |
| 3 | **可空性（NOT NULL）** | 42 列 | ✅ **0 差异** —— 脚本原话「两侧完全一致 ✓」 | 0 |
| 4 | **DEFAULT** | 42 列 | ✅ **0 差异** —— 脚本原话「两侧完全一致 ✓」 | 0 |
| 5 | **索引**（`pg_indexes`） | Go 17 / RAG 19 | 🔴 **2 处残留**（另 1 处 = 命名形态差 ⇒ 语义等价，不计失败） | 2 |
| 6 | **约束**（`pg_constraint`） | Go 5 / RAG 7 | 🔴 **2 处残留**（2 处 = **额外 CHECK**；另 5 行逐条一致） | 2 |
| 7 | **触发器**（`pg_trigger`） | Go 0 / RAG 1 | 🔴 **1 处残留**（RAG 独有 1 个触发器） | 1 |
| 8 | **触发器形式 / 触发器函数体** | Go 0 / RAG 1 | ⚪ **无可成对比对的触发器 ⇒ 本维度「什么都没比」** —— ⚠️ **这不是「触发器已对齐」**（该单边触发器的函数属性已由维度 7 计过，**不在此重复计失败**） | 0 |
| | **合计** | | | **5** |

> ⚠️ **读法**：本域 DDL 的**行为层主干**（类型 / 长度 / 可空性 / DEFAULT / 唯一约束）在 **8 个维度里 5 个为 0**；剩下的 **5 处全部是「RAG 侧多出来的对象」**（**方向单一**）⇒ 逐条见 §L.4。
> ⚠️ **维度 8 不是「通过」而是「无对象」** —— Go 侧**没有**触发器可与 RAG 的比 ⇒ 本域的触发器**语义等价性未核**。

### L.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 42 列逐字相同（0 处差异）**

| 表 | 抽样列 | Go 侧类型 | RAG 侧类型 | 判定 |
|---|---|---|---|---|
| `like_record` | `id` / `user_id` / `author_id` / `last_operation_id` | `bigint` | `bigint` | ✅ 逐字相同（Go tag 显式 `type:bigint`，见 `data_model.go:11,14,16`） |
| `like_record` | `target_type` / `target_id` | `character varying(32)` / `character varying(64)` | 同左 | ✅ 逐字相同 |
| `like_record` | `state` | `smallint` | `smallint` | ✅ 逐字相同（Go tag `type:smallint`，`data_model.go:15`） |
| `like_record` | `created_at` / `updated_at` / `delete_at` | `timestamp with time zone` | 同左 | ✅ 逐字相同（`DeleteAt gorm.DeletedAt` ⇒ 可空） |
| `like_domain_fact_outbox` | `delivery_status` / `retry_count` | `integer` | `integer` | ✅ 逐字相同 —— ⚠️ 这是 `V6` 那轮**专门修**的项（Go `DeliveryStatus int32` 无 `type` tag ⇒ GORM 取 `integer`；此前 RAG 是 `BIGINT`）⇒ **已闭合** |
| `like_domain_fact_outbox` | `technical_offset` / `fact_version` | `bigint` | `bigint` | ✅ 逐字相同（`int64 ⇒ Size=64`）⇒ ⚠️ 说明 `V6` **不是无差别降级** |

⇒ 脚本汇总行：`类型/长度差异 0 处：` + `逐字相同 42 列。`

**② 可空性（NOT NULL）—— 0 处差异** ⇒ 脚本原话 `（两侧完全一致 ✓）`
⇒ 抽样：`like_record.user_id` / `target_type` / `target_id` / `author_id` / `state` 两侧均 `NOT NULL`（Go tag 有 `not null`）· `like_record.delete_at` 两侧均可空（`gorm.DeletedAt`）。

**③ DEFAULT —— 0 处差异** ⇒ 脚本原话 `（两侧完全一致 ✓）`
⇒ 抽样：`like_record.state` 两侧均 `0` · `like_record.last_operation_id` 两侧均 `0` · `like_domain_fact_outbox.delivery_status` 两侧均 `0` · `retry_count` 两侧均 `0`。
⇒ ⚠️ 与 `message` 域 6 处 / `users` 域 8 处的「RAG 多 DEFAULT」**不同族** ⇒ **本域这个维度是真 0 差异**，不是被豁免名单压过的。

**④ 源码 ↔ 活库 —— 0 差异（本轮新增维度）**

- 目录扫描得到 **6 份**迁移脚本 ⇒ **清单由目录扫描产生**。
- 在**干净临时库** `rag_like_db_srccheck` 上**原样重放** ⇒ 与活库逐项对比：**表集合 5 ↔ 5** · **列集合 0 差异** · **列属性 0 差异** · **可空性 / DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致`
- ⇒ **含义**：本域仓里的 `.sql` 与**当前活库**之间**没有分叉**。

**⑤ 约束 —— 5 行逐条一致 + 2 行 RAG 独有**

| 逐条一致的 5 行 | contype | 定义 |
|---|---|---|
| `like_record_pkey` | p | `PRIMARY KEY (id)` |
| `like_consume_inbox_pkey` | p | `PRIMARY KEY (id)` |
| `like_outbox_event_pkey` | p | `PRIMARY KEY (id)` |
| `like_domain_fact_outbox_pkey` | p | `PRIMARY KEY (id)` |
| `uk_like_fact_stream` | u | `UNIQUE (aggregate_id, fact_version)` |

⇒ 脚本原话 `逐条一致 5 行 ✓` + `（两侧约束集合完全一致 ✓）`（脚本指的是**共有部分**；2 行 RAG 独有的 CHECK 单列为「实质差异」，见 §L.4 残留 ②）。

**⑥ 索引命名形态差 —— 1 处语义等价（不计失败）**

| 语义等价配对 | 唯一性 | 列 |
|---|---|---|
| `idx_like_record_delete_at` ≡ `idx_like_record_deleted_at` | non-unique | `delete_at` |

⇒ Go 列名是 `delete_at`（`data_model.go:20` `DeleteAt gorm.DeletedAt`）⇒ GORM 索引名 `idx_like_record_delete_at`；RAG 迁移脚本写成 `deleted_at`（`V1__create_like_record.sql:49-50`）⇒ **仅命名形态差** ⇒ ✅ **已登记**为语义等价（`docs/DRIFT/global/lanes/L3-ddl.md` §8.3 第 12 行）⇒ **不是漂移**。

### L.4 残留 5 处（逐条列出 + 定级 + 签字状态）

> **定级口径** = `docs/twoStage.md` **§十**（`twoStage.md:941-973`）：**A** = 阶段 1 准入阻断 · **B** = 阶段 1 竞争力补强 · **C** = 阶段 2 最终工程建设 · **D** = 经分析确认可不实现或不适用（**须有明确理由**）。
> ⚠️ **一条差异 ≠ 一个缺陷**：本节 5 处 ⇒ 归成 **3 条**（额外索引 2 · 额外 CHECK 2 · 额外触发器 1）。
> ⚠️ **方向单一**：5 处**全部**是「**RAG 侧多出来的对象**」⇒ 没有一处是「Go 有、RAG 缺」⇒ **不是 A 类**（不阻断阶段一准入）。

#### 残留 ① 🟢 额外索引 2 条（RAG 独有）—— **性能非行为**

| 索引名 | 表 | 列 | Go 出处 | RAG 出处 |
|---|---|---|---|---|
| `idx_like_fact_delivery` | `like_domain_fact_outbox` | `(delivery_status, created_at, event_id)` non-unique | `service/like/rpc/internal/model/migrations/003_like_fact_delivery.sql:12-13`（**只在夹具里**；`003` 全仓 0 引用 ⇒ Go 生产没有） | `service/like/like-rpc/src/main/resources/db/migration/V6__align_like_domain_fact_outbox_with_go.sql:103-104` |
| `idx_like_legacy_fact_subject_target` | `like_domain_fact_outbox` | **部分索引** `((payload->>'subject_ref'), (payload->>'target_type'), (payload->>'target_id')) WHERE delivery_envelope IS NULL` non-unique | `service/like/rpc/internal/model/migrations/002_like_dc_wire.sql:15-17`（**只在夹具里**） | `V6__align_like_domain_fact_outbox_with_go.sql:93-95` |

| 项 | 内容 |
|---|---|
| 签字状态 | ⏳ **未签字候选**（**已登记在案**）—— `docs/DRIFT/global/lanes/L3-ddl.md` §8.3 第 3、4 行分类为「🟢 **只记不修（性能非行为）**」· 本域 DRIFT 侧见 [`docs/DRIFT/like/rpc.md`](rpc.md)（`V6` 那轮的「②③④⑤⑥ 台账没列全的 5 项」表，第 ③④ 项）<br>⚠️ **同族的待签字候选** = `SD-2026-10-24-CAND-02`（**comment 域那条**，本域**无独立编号** ⇒ 引用时必须给「文件 + 域」，不能只写编号，AGENTS.md §0） |
| **定级** | **D 类**（经分析确认不实现 —— **有明确理由**） |
| 定级理由 | ① 两条都是**非唯一**普通索引 ⇒ PG **只用唯一索引 / 约束判唯一** ⇒ 增删它们**不改变任何写入语义、冲突行为、唯一性判定、查询结果** ⇒ **纯查询计划（性能）差异** ⇒ **不是 A 类**、**不是 C 类**；② 方向是「**RAG 更全**」（多了索引 ⇒ 不可能让任何原本能写的行被拒）⇒ **无拒绝路径**；③ 有明确理由的「不实现」⇒ **D 类**；④ ⚠️ **但要知情**：Go **生产**侧确实没有这两条索引（只在夹具 `.sql` 里）⇒ 严格说这是一处**生产 schema 构成差**，只是它**不产生行为差** ⇒ 登记即可，**不建议动**（删索引在生产大表上是 DDL 风险，收益只是省一点写入开销） |

#### 残留 ② 🟡 额外 CHECK 2 条（RAG 独有）—— **写路径上可拒绝**

| 约束名 | 表 | 定义 | Go 出处 | RAG 出处 |
|---|---|---|---|---|
| `like_new_fact_has_dc_wire` | `like_domain_fact_outbox` | `CHECK ((aggregate_id IS NOT NULL) AND (fact_version IS NOT NULL) AND (delivery_envelope IS NOT NULL)) **NOT VALID**` | `service/like/rpc/internal/model/migrations/002_like_dc_wire.sql:21-28`（`DO $$ … ADD CONSTRAINT … NOT VALID`）—— **只在夹具里** | `V6__align_like_domain_fact_outbox_with_go.sql:83-87` |
| `like_fact_stream_version_range` | `like_fact_stream` | `CHECK ((version >= 1) AND (version <= '9007199254740991'::bigint))` | `service/like/rpc/internal/model/migrations/002_like_dc_wire.sql:8-11` —— ⚠️ **内联在 `CREATE TABLE IF NOT EXISTS like_fact_stream (…)` 里** ⇒ AutoMigrate（`db.go:15`）已建过这张表 ⇒ **整条 CREATE 是空操作** ⇒ ⇒ **Go 生产侧永远没有这条 CHECK**（**连夹具基线 B 下也没有**） | `V6__align_like_domain_fact_outbox_with_go.sql:141-145`（抄成独立 `ALTER` ⇒ 与 comment 域 `comment_fact_stream_version_range` **完全同型**） |

| 项 | 内容 |
|---|---|
| 签字状态 | ⏳ **未签字候选**（**已登记在案**）—— `docs/DRIFT/global/lanes/L3-ddl.md` §8.3 第 1、2 行分类为「🟡 **RAG 更严 ⇒ 已签字族候选**」· 本域 DRIFT 侧见 [`docs/DRIFT/like/rpc.md`](rpc.md)（同表的第 ②⑥ 项）<br>⚠️ **同族的待签字候选** = `SD-2026-10-24-CAND-02`（**comment 域那条**，本域**无独立编号**） |
| **定级** | **C 类**（阶段 2 最终工程建设项） |
| 定级理由 | ① **CHECK 是写路径上的拒绝路径** ⇒ 与「多余索引」**不同类**：它**可能拒掉 Go 侧本来会接受的行** ⇒ 原则上**可观察**（比 DEFAULT 更强）⇒ **不能判 D**（说「不实现」得给出「Go 会接受但 RAG 会拒的行不会发生」的理由，而该理由**依赖调用方保证**，不是 schema 自证）；② 方向是「**RAG 更严**」⇒ **不阻断阶段 1 准入**（正常写路径下两侧行为相同）⇒ **不是 A 类**；③ 但「**是否保留这条拒绝路径**」= 业务语义决策（是否允许「缺信封 / 超 2^53 的 version」被写进来）⇒ 属「**业务语义恢复**」的最终形态 ⇒ 阶段 2 ⇒ **C 类**；④ **不是 B 类** —— 阶段 1 竞争力补强指的是「形成可信的技术亮点」，本条是**减法**（要不要多一道防线），不是亮点建设 |
| ⚠️ **当前不可观察（有实测依据）** | 本域 DRIFT 台账已实测两条 CHECK **在当前写路径上不会误伤**：`LikeDomainFactService:157-158` 调 `insertFact` 时 `streamId` / `factVersion` / `envelopeJson` **三者都非 null** ⇒ `like_new_fact_has_dc_wire` 不会触发；`version` 由业务保证 ≥ 1 且 ≤ 2^53-1 ⇒ `like_fact_stream_version_range` 不会触发。⇒ **「当前不触发」≠「永远不会触发」** ⇒ 与签字档的同类口径一致：**登记 + 留痕，不在本轮改回** |

#### 残留 ③ 🟡 额外触发器 1 个（RAG 独有）—— **唯一「原则上真能观察到」的一类**

| 触发器 | 表 | 定义 | Go 出处 | RAG 出处 |
|---|---|---|---|---|
| `like_domain_fact_identity_immutable` | `like_domain_fact_outbox` | `BEFORE UPDATE … FOR EACH ROW EXECUTE FUNCTION reject_like_domain_fact_identity_change()` —— 函数体：`NEW.event_id / event_type / payload / aggregate_id / fact_version / delivery_envelope` 任一 `IS DISTINCT FROM OLD.…` ⇒ `RAISE EXCEPTION 'like fact envelope is immutable'` | `service/like/rpc/internal/model/migrations/003_like_fact_delivery.sql:15-32`（函数 `:15-28` + 触发器 `:29-32`）—— **只在夹具里**；⚠️ **`003` 全仓 0 引用** ⇒ Go 生产没有这个触发器 | `V6__align_like_domain_fact_outbox_with_go.sql:115-133`（函数 `:115-128` + 触发器 `:130-133`） |

| 项 | 内容 |
|---|---|
| 签字状态 | ⏳ **未签字候选**（**已登记在案**）—— `docs/DRIFT/global/lanes/L3-ddl.md` §8.3 第 5 行（「🟡 **RAG 更严 ⇒ 已签字族候选**（**唯一原则上可观察的一类**」）· 触发器形态的逐条对照见 `docs/DRIFT/global/lanes/L10-ddldeep.md`（like 域第 1 行）· 本域 DRIFT 侧见 [`docs/DRIFT/like/rpc.md`](rpc.md)（同表的第 ⑤ 项） |
| 函数体一致性 | ✅ **两侧函数体逐字节相同**（`reject_like_domain_fact_identity_change` 的 plpgsql 体逐字照抄）⇒ 差异只在「**Go 生产有没有建这个触发器**」，不在「**触发器长什么样**」 |
| **定级** | **C 类**（阶段 2 最终工程建设项） |
| 定级理由 | ① **触发器是写路径上真正可观察的拒绝**：RAG 侧 `UPDATE` 改到那 6 个不可变列之一 ⇒ `RAISE EXCEPTION` ⇒ **请求失败**；Go 侧**没有这个触发器** ⇒ 同样的 `UPDATE` **会成功** ⇒ ⇒ **两侧行为真的不同**（不是「性能」也不是「当前恰好不触发」）⇒ **不能判 D**；② 方向是「**RAG 更严**」⇒ **不阻断阶段 1 准入**（正常投递元数据写路径不碰这 6 列）⇒ **不是 A 类**；③ 「**要不要保留这道防线**」= 业务语义决策（信封不可变是不是本域的硬约束）⇒ 属阶段 2 的最终形态 ⇒ **C 类**；④ ⚠️ **当前写路径不误伤**（本域 DRIFT 台账已实测：`LikeDomainFactOutboxMapper` 三处 `UPDATE` 只写 `delivery_status` / `retry_count` / `technical_*` / `delivered_at`，**没有一列**落在不可变清单里）⇒ ⇒ **登记 + 留痕，不在本轮改回** |
| ⚠️ **Go 侧 `bootstrap/LikeSchemaGate` 的口径** | Go 侧 `service/like/rpc/internal/model/db.go:15` 之外的 `bootstrap/LikeSchemaGate` 会 **fail-fast 校验** `like_domain_fact_identity_immutable` 触发器与 5 张表存在 ⇒ 即「**Go 自己也认为这个触发器该在**」⇒ 这条差异更像是「**Go 生产漏执行了夹具**」而非「**RAG 多加了一道不该加的防线**」⇒ **该结论支持保留 RAG 侧**（但仍属**未签字候选**，本轮不替用户拍板） |

### L.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：like 域迁移属 **G4 写权**、`service/like/**` 由别的 lane 在改 ⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**路线：把 `check-ddl-static.py` 与该域的 `db/migration/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ 脚本内的 `REPO_ROOT` 与仓内布局一致 ⇒ 它读到的**仍是仓里那份 `.sql` 的同一份字节**，只是不在仓里写），**逐字节校验 sha256 相同**，再在**副本**上做变异。

**变异：`like_record.last_operation_id` 的列类型（`BIGINT` → `INTEGER`）**

| 步骤 | 命令 / 动作 | 结果 |
|---|---|---|
| 建临时树 | `cp` 脚本 + `cp` 该域 **6 份** `.sql` ⇒ `/tmp/g2-mut/like/{tools/difftest,service/like/like-rpc/src/main/resources/db/migration}` | sha256 **6/6 逐字节 SAME**（见下表） |
| 基线 | `python3 /tmp/g2-mut/like/tools/difftest/check-ddl-static.py --domain like --indexes --check-source` | **EXIT=1**（**5** 处）· 源码↔活库 = `✅ 重放结果与活库逐项一致` |
| 变异 | 副本 `V1__create_like_record.sql:21`：`last_operation_id  BIGINT       NOT NULL DEFAULT 0,` → `last_operation_id  INTEGER       NOT NULL DEFAULT 0,`（改**一个列的类型**，命中目标行 **1** 处） | 变异前后该行已回显核对（`before`/`after` 两行均打印在运行日志里） |
| 变异后重跑 | 同上命令 | **EXIT=1**（**6** 处 = 5 + **1**）· 源码↔活库 **转红**：<br>`🔴 列属性不同：like_record.last_operation_id  源码=('integer', '')  活库=('bigint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 删掉临时树里的 `service/` 后**重新拷贝**原件 | sha256 **6/6 逐字节 SAME** |
| 还原后重跑 | 同上命令 | **EXIT=1**（**5** 处 · **回到基线**）· 源码↔活库 回到 `✅ 一致` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **6 份全部 SAME**（sha256 原值见下）⇒ 逐字节回到变异前 |
| **收尾** | `rm -rf` 整棵临时树 | `exists=no` ⇒ **工作树与 `/tmp` 均无临时副本残留** |

**sha256 对照（变异前 = 还原后，两次完全相同）**

| 文件 | sha256（副本 = 原件，两次一致） |
|---|---|
| `V1__create_like_record.sql` | `0a760802a54f395acd9d68c116246c7f6c27b9e69c50f2b0c4bf1c6844a178dc` |
| `V2__create_like_consume_inbox.sql` | `f6e3538fc5ac1c53e0b11bf4b624c60060ff622016a3462c0e9814bfa020337b` |
| `V3__create_like_outbox_event.sql` | `5af3552a49076c46177b5df7a9986d982818db7961ee71f582818e96ab04e832` |
| `V4__create_like_domain_fact_outbox.sql` | `5d6d598c5786777f14ae7720b28716cbeaa9c1b660fd0952720bb19585f5a9d0` |
| `V5__create_like_fact_stream.sql` | `4f56386596ee349b4dd8f2c4157669a95183c310cd978154acd91aa7c78bf75e` |
| `V6__align_like_domain_fact_outbox_with_go.sql` | `1ea34f3383a8648c4b7d8dc5cc5f5ea3652d3c539d89435a94ea1837296400b8` |
| `check-ddl-static.py`（脚本副本） | `cc87da8dfde81eb11dd160150299369e6012d3eb8d815e44ede02a5c517a6ebb` |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`like_record.last_operation_id`），其余 5 处**不变** ⇒ 守门**不是假绿**；还原后**精确回到基线** ⇒ 无残留。
⚠️ **本域的「期望 EXIT」口径**：本域 Go↔活库**本就有 5 处已登记差异** ⇒ 还原后的正确期望值是 **EXIT=1（5 处）**，**不是** EXIT=0 ⇒ 变异判据用的是「**源码↔活库这一维度的差异数 0 → 1 → 0**」＋「总差异数 5 → 6 → 5」，不是总退出码。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task"
git status --short          # ⇒ 空
git diff --stat -- service/ # ⇒ 0 行
```

### L.6 人工补查（脚本**不查**的维度 · **人工闭合，非脚本原生结论**）

> ⚠️ `tools/difftest/check-ddl-static.py` 的「约束」维度**会把 CHECK（`contype='c'`）也列进「实质差异」**（本域 §L.2 维度 6 的 2 行即 CHECK）⇒ **CHECK 已被脚本原生覆盖**；但**外键（`contype='f'`）、分区（`relkind='p'`）、存储过程 / 自定义函数（`pg_proc`）** 仍需**另查** ⇒ 下表是**本轮人工补查**的结果，**明确标注为人工闭合，不冒充脚本原生结论**。

| 维度 | Go 侧 `go_like_db` | RAG 侧 `rag_like_db` | 人工闭合结论 |
|---|---|---|---|
| **CHECK**（`pg_constraint` where `contype='c'`） | 2 行，**但 `conrelid` 为 0** ⇒ PG **内建域**约束（`cardinal_number_domain_check` / `yes_or_no_check`），**不是用户表约束**；**用户表 CHECK = 0** | 2 行内建域（同左）**＋ 2 行用户表 CHECK**（`like_new_fact_has_dc_wire` / `like_fact_stream_version_range`） | 🔴 **RAG 多 2 条** ⇒ **已在 §L.4 残留 ② 计过**（脚本原生也报了同样 2 处 ⇒ **双路一致**） |
| **外键**（`contype='f'`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无外键** |
| **分区**（`pg_class.relkind='p'` / `relispartition`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无分区表** |
| **存储过程 / 自定义函数**（`pg_proc`，排除扩展与 `pg_catalog`） | 0 行 | **1 行**：`public.reject_like_domain_fact_identity_change`（`prokind=f` · `rettype=trigger` · `VOLATILE` · `plpgsql`） | 🔴 **RAG 多 1 个函数** ⇒ ⚠️ **它就是残留 ③ 那个触发器的函数体** ⇒ **不是独立对象**，**不在此重复计失败**（脚本原话：「单边触发器的函数属性（**不在此计失败** —— 已由上面的『触发器集合』维度计过）」）⇒ **已在 §L.4 残留 ③ 计过** |
| **触发器**（`pg_trigger`，排除 internal） | 0 行 | 1 行：`like_domain_fact_outbox.like_domain_fact_identity_immutable` | 🔴 **RAG 多 1 个** ⇒ **已在 §L.4 残留 ③ 计过**（双路一致） |

⇒ **「无对象可比」≠「已对齐」**：外键 / 分区两个维度**两侧都没有可比对象** ⇒ 本轮**未核**（无对象可核），**不写成「已对齐」**。

### L.7 证据强度 · 效力边界 · 未能核到的部分

| 项 | 结论 |
|---|---|
| 证据强度 | **E4（真 PG 双侧活体）** —— 两侧各建一个真库（Go 侧跑**真源自己的** `AutoMigrate`，RAG 侧按序执行 `V1`~`V6`），逐条 dump `pg_indexes` / `pg_constraint` / `pg_trigger` / `information_schema.columns`；**外加 `--check-source` 的「源码重放 ↔ 活库」真库对照** |
| 变异验证 | ✅ **已做**（§L.5）⇒ 守门有牙齿，**不是假绿** |
| **效力边界 ①** | 本节结论只覆盖 **`like` 域 5 张表**；`service/like/like-common/**` 与 `service/common/**` 的**共享层**不在本节范围 |
| **效力边界 ②** | 结论覆盖的是**当前迁移脚本 + 当前 Go struct tag** ⇒ 改了任一侧即**失效** ⇒ 需重跑 `ddl-parity.sh like` |
| **效力边界 ③** | **`--check-source` 的「源码 ↔ 活库」维度与 Go 侧无关** ⇒ 它只保证「仓里的 `.sql` 与活库没分叉」，**不代表**活库与 Go 一致 |
| **效力边界 ④** | ⚠️ **基线选择敏感**：本节基线 = **A（Go 生产权威，只跑 AutoMigrate）**。**基线 B（连夹具 `001~003` 一起放）下差异会从 5 处降到 1 处**（只余 `like_fact_stream_version_range` 一条 CHECK，因为该 CHECK **内联在 `CREATE TABLE IF NOT EXISTS` 里** ⇒ AutoMigrate 先建过表 ⇒ 整条 CREATE 是空操作 ⇒ Go 侧**永远**没有）⇒ ⇒ **5 处中有 4 处的「Go 侧出处」只在夹具里** ⇒ **引用时必须带上「夹具 / 生产」这个限定**，否则会误读成「Go 生产缺了这 4 个对象」 |
| **未能核到 ①** | **触发器形式 / 函数体维度（维度 8）**：Go 侧 0 个触发器 ⇒ **无可成对比对的触发器 ⇒ 本维度未核**（不是「已对齐」） |
| **未能核到 ②** | **`--check-source` 不检查外键 / 分区 / 存储过程** ⇒ §L.6 是**人工补查**（`contype='f'` / `relkind='p'` / `pg_proc` 三条手工 SQL）⇒ **非脚本原生结论**，但**已人工闭合** |
| **未能核到 ③** | **基线 B（`--with-go-sql`）本轮未实跑** ⇒ 该档数字**沿用主线已实跑的登记**（`docs/DRIFT/global/lanes/L3-ddl.md` §8.2）⇒ **本节不把基线 B 的数字当成本轮实测** |
| **未能核到 ④** | **未做「同一次写入两侧落库后读回」的活体数据对拍**（本节是 **schema 层**对照）⇒ 数据层的等价性另由 [`docs/DRIFT/like/rpc.md`](rpc.md) 的各轮双侧活体承担 ⇒ **本节不重复声称数据层已对齐** |
| **未能核到 ⑤** | ⚠️ **本节 5 处残留**在本域 `docs/Intentional_Deviations/like/` 下**没有独立编号的签字条目**（`like/rpc.md` 现存 2 条 `SD-` 编号都与 DDL 无关：`SD-2026-10-23-06` 业务码载体 / `SD-2026-10-23-07` Nacos 注册）⇒ ⇒ **本节的「签字状态」一律写「未签字候选（已登记在案）」**，**不冒充已签字** ⇒ 也**不替用户拍板**（AGENTS.md 用户偏好：登记类事项不升级为拍板） |

### L.8 本节边界（本节**不**声称什么）

1. **不修任何差异** —— 本轮只**登记**（RoundPlan T-3.1）。
2. **不重复质疑已登记项** —— 本节 5 处**均已在案**（`docs/DRIFT/like/rpc.md` + `docs/DRIFT/global/lanes/L3-ddl.md` §8.3）⇒ 只做**留痕 + 定级**，**不改回**。
3. **不声称触发器已对齐** —— Go 侧 0 ⇒ 「无对象可比」；RAG 多的那 1 个**已单列**为残留 ③。
4. **不声称数据层已对齐** —— 本节是 **schema 层**。
5. **不写共享面** —— `docs/DRIFT/_cross/**` · `docs/DRIFT/global/**` · `docs/PhaseGoal-judge.md` · `docs/RoundPlan/**` **本 lane 一律不写**（合并后由**合并收口执行者**统一更新）⇒ 例如 §L.4 的 3 条在 `docs/DRIFT/global/lanes/L3-ddl.md` 与 `L10-ddldeep.md` 有**共享面**登记，本节**只复述、不修改**那两个文件。

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
>
> 🆕 **2026-10-30 · G2 本轮新增一行的交接说明（`SD-2026-10-23-09`）**：
> 本表的**权威来源 = `docs/DRIFT/_cross/grading/*.md`（U5-O 叠加层）**，
> 而 `docs/DRIFT/_cross/**` 属**合并收口执行者**的共享面 ⇒ **G2 不可写** ⇒
> ⇒ 本轮由 G2 完成的是**逐条 Go 对照 + 结论**（已写入本表与
> [`docs/Intentional_Deviations/like/api.md`](../Intentional_Deviations/like/api.md) 的 `lane W7` 小节），
> **落 overlay 那一步需由收口执行者补** ⇒
> ⚠️ **在 overlay 落地前，派生层 `stage1-derivation.tsv` 里 `SD-2026-10-23-09@like` 仍显示 `stage=—`（未定级）**
> ⇒ ⇒ **这是「定级已结论、但未落到权威源」的已知状态，不是「没人定级」**
> ⇒ ⇒ **判读派工面时勿把它当成「G2 漏排」**（它的结论 = C 类 · 不阻断阶段一 ⇒ 本来就不进阶段一范围）。


### lane W30

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| `D-2` | D（差异已消失 ⇒ 无待实现内容） | P2 | 否 | Go `service/like/rpc/internal/logic/getlikecountlogic.go:37`（`span := trace.SpanFromContext(l.ctx)`）＋ `:38-41`（`span.SetAttributes(attribute.String("query.target_type", …), attribute.Int("query.batch_size", …))` ⇒ 属性打在 **ctx 里**的 span 上）＋ `service/like/rpc/internal/logic/getlikestatelogic.go:37-42`（`query.user_id`／`query.target_type`／`query.batch_size`）＋ `service/like/rpc/internal/logic/likeactionlogic.go:161-166`（`like.*` 4 个）＋ `service/like/rpc/internal/mq/internal/mqs/likemq.go:84`（`TraceConsumer(ctx, "like-mq", "LikeUpdateService.Consume", likeMessageAttrs("like-topic", key), …)`）＋ `:137-143`（`likeMessageAttrs` ⇒ `messaging.*` 4 个）vs RAG `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeSpans.java:62-108`（**17 个属性常量**：`query.*` 6／`cache.*` 3／`like.*` 4／`messaging.*` 4，属性名与 Go 侧逐条一致）＋ `:120`（`GO_MESSAGING_DESTINATION_LITERAL = "like-topic"` ⇒ 与 Go 字面量同形）＋ `:241-246`（`Span.current().setAttribute(...)` ⇒ **不新建 span**、与 Go 的 `SpanFromContext` 同形）＋ 落点 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeDomainService.java:148-149`（`query.*` 2 个，在「空 ID」校验**之前** ⇒ 与 Go `:42` 同序）＋ `:263-265`（`query.*` 3 个）＋ `:299-300`（`cache.hits`／`cache.misses`，在 DB 回源**之前**）＋ `:681-684`（`like.*` 4 个，在「参数异常」校验**之前**）＋ `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/mq/LikeUpdateKafkaListener.java:153-157`（`MqConsumer.traceConsumer(..., LikeSpans.messagingAttributes(key), ...)`）＋ 共享层前提 FB-04 已落：RAG `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:467-480`（`onHalfClose` 用 `otelCtx.makeCurrent()` 包住 `:472`／`:477` 的 `super.onHalfClose()` ⇒ handler 执行期 `Span.current()` **就是** server span）＋ 守门 RAG `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/service/LikeSpansAttributeGateTest.java`（11 tests／0F）＋ `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/service/LikeA9GrpcSpanEndToEndTest.java:314-331`（**对照组**：复刻 FB-04 之前形态 ⇒ 属性**必须丢**）⇒ ⇒ **差异已消失**：D-2 记的「like 域 span 属性全缺」已由**域侧落码 ＋ 共享层 FB-04 落码**两侧闭合 —— Go 侧打属性用 ctx 里的 span、RAG 侧打属性用 current span，落在**同一个** server span 上，属性名与写入位置逐条一致 ⇒ 两侧同形 ⇒ ⇒ 本条**无待实现内容** ⇒ 归 D；P2（属性缺失只在 trace 数据上可见、不改业务返回，但属 A9 可观测性验收面）· **不阻断阶段一**　⚠️ 残留（非行为漂移 · 不在本条范围）：`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeSpans.java:47-51` 的 javadoc 仍写「handler 执行期 span 尚未成为 current ⇒ 本类的方法在真链上暂时是空操作」⇒ 该措辞已被 FB-04 落码作废 | 2026-10-30 |
| `D-6` | D（差异已消失 ⇒ 无待实现内容） | P3 | 否 | Go `service/like/rpc/internal/model/db.go:11`（`db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{})` ⇒ Go 侧 DB **显式构造 ＋ 显式注入** ⇒ **不存在**「自动配置求值顺序」这个概念 ⇒ 排序桥在 Go 侧**无对应物**，是纯表达层 workaround）＋ Go `service/like/rpc/internal/model/like.go:128`（`query.Order("id DESC")`）＋ `:141`（`query.Order("id DESC")`）⇒ 排序语义由**查询自身**承担（对应 RAG 侧 mapper 的 `orderByDesc`）vs RAG `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java:95-97`（`@AutoConfiguration(afterName="org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration", beforeName="com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")`）＋ `:98-99`（**类体刻意为空、不注册任何 bean** ⇒ 零行为面）＋ 注册行 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4` ＋ 依赖面 `service/like/like-rpc/pom.xml:63`（`com.rag:common` ⇒ 共享层那份在 like-rpc 的 classpath 上）⇒ ⇒ **域侧那份已不在**：`git log --diff-filter=D` 实测 commit `2016fa6d`（G2 第十七轮）删掉的正是 D-6 点名的域侧桥类；`service/like/like-rpc/src/main/java/com/rag/like/like/rpc/config/` 现仅剩 `Jackson2ObjectMapperConfiguration.java`／`LikeKafkaTopics.java`／`LikeRpcProperties.java`；like-rpc **无**自己的 `AutoConfiguration.imports`；全仓 `find` `MybatisPlusOrdering*`（排除 target）**只剩共享层那 1 份** ⇒ ⇒ 产物层守门在位：RAG `service/like/like-rpc/src/test/java/com/rag/like/like/rpc/LikeRpcArtifactClasspathGateTest.java:117`（`fatJarCarriesSharedLayerOrderingBridge`）＋ `:147`（断言内嵌 common jar 的 `AutoConfiguration.imports` **含**共享层排序桥全限定名 ⇒ 期望值不与被测代码同源）＋ `:290`（`mvn … -pl <模块> -am` ⇒ reactor 产当轮 `service/common` jar）⇒ ⇒ **差异已消失**（域侧冗余桥已删、共享层桥接管、产物层证据已在；Go 侧本就无此概念 ⇒ 两侧同形）⇒ ⇒ 本条**无待实现内容** ⇒ 归 D；P3（该桥**不注册任何 bean** ⇒ 零行为面，且已被收编消解）· **不阻断阶段一** | 2026-10-30 |
| `D-7` | C（阶段二工程建设项 · 剩余实现面 = 一次双侧活体定值 ＋ 据此决定 like 域是否改成与 Go 同形的数值码透传） | P1 | 否 | Go `service/like/api/internal/logic/like/getlikecountlogic.go:41-46`（`st, ok := status.FromError(err)`；`ok` 为真 ⇒ `errmsg.NewErrCodeMsg(int(st.Code()), st.Message())` ⇒ **业务码 = gRPC 数值码**；`ok` 为假 ⇒ `errmsg.NewErrCode(errmsg.ErrorServerCommon)`）＋ `service/like/common/errmsg/errmsg.go:26-30`（`NewErrCodeMsg` ⇒ `&CodeError{Code: code, Msg: msg}`）＋ `:40-41`（`CodeServerBusy = 1015`／`ErrorServerCommon = 5001`）＋ `grpc-go@v1.81.1/codes/codes.go:174`（`Internal Code = 13`）＋ `grpc-go@v1.81.1/status/status.go:96-110`（`FromError` ⇒ 实现了 `GRPCStatus()` 才 `ok == true`）vs RAG `service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeGrpcError.java:233-247`（`mapGrpcCodeToBizCode` ⇒ INVALID_ARGUMENT→2001／UNAUTHENTICATED→1009／PERMISSION_DENIED→1008／**default→1015**）＋ `service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeErrorCode.java:26`（`CODE_SERVER_BUSY(1015)`）＋ `:30`（`ERROR_SERVER_COMMON(5001)`）＋ `service/like/like-api/src/main/java/com/rag/like/like/api/service/LikeApiSupport.java:177-186`（`rpcError`：**先** `extractStatus` ⇒ 非 status ⇒ `:180-181` 直接给 **5001**（注释明写「Go: status.FromError 的 ok == false 分支 —— 注意是 5001，不是 1015」）；是 status ⇒ `:183` 调 `bizCodeFromError` 得 bizCode ＋ description）＋ `:198-206`（`extractStatus` 只认 `StatusException`／`StatusRuntimeException`）＋ 消费点 `service/like/like-api/src/main/java/com/rag/like/like/api/service/LikeCountService.java:63`（`throw support.rpcError(e)`）＋ 业务码载体 `service/like/like-common/src/main/java/com/rag/like/like/common/errmsg/LikeGrpcError.java:140`（trailer `rag-like-biz-code` ⇒ 载体差异已签字 `docs/Intentional_Deviations/like/rpc.md:13`）⇒ ⇒ **差异仍成立**：**status 错误路径**两侧业务码**不同类** —— Go 侧必然是 `int(st.Code())`（gRPC 数值码 0~16，如 INTERNAL = 13）⇒ **1015 从这条路径出不来**；RAG 侧 `mapGrpcCodeToBizCode` 的 default 分支恒给 1015 ⇒ 客户端拿到的错误码不同类。⚠️ **台账描述需按实况订正**：台账 D-7 原文称「RAG 侧 INTERNAL 落 default ⇒ 1015，且熔断打开经 `bizCodeFromError:199` ⇒ 1015」；**实况**是 like-api 真实消费路径先过 `LikeApiSupport.rpcError` —— 非 status 异常与熔断打开异常（`service/common/src/main/java/com/rag/common/protection/GoStyleCircuitOpenException.java:30` 是**普通 RuntimeException** ⇒ `extractStatus` 返回 null）**两侧同形落到 5001**（Go 的 `ok == false` 分支也是 5001）⇒ ⇒ 残余差异面**只在**「status 错误 ⇒ 业务码」这一条 ⇒ 不影响本条结论。⇒ ⇒ 剩余实现面 = ① 一次「下游不可达」的**双侧活体定值**（Go 侧确切数值取决于运行期失败形态：etcd 无实例／连接被拒／调用超时 ⇒ 分别落到 `Unknown` = 2／`Unavailable` = 14／`DeadlineExceeded` = 4）② 据此决定 RAG 继续用 1015 还是改成与 Go 同形的**数值码透传**（落点在 like 域内 `LikeGrpcError`，**不牵动共享层**）⇒ ⇒ 阶段二工程建设项 ⇒ 归 C；P1（下游不可达时的错误码语义对客户端可见，且 Go 侧 1015 从这条路径出不来 ⇒ 属对外契约面未闭合）· **不阻断阶段一** | 2026-10-30 |
| `FB-05` | C（阶段二最终工程建设项 · 落点 100% 在共享层，本域无法自修） | P1 | 否 | Go `service/like/rpc/internal/logic/getlikecountlogic.go:50`（`l.svcCtx.BizRedis.HmgetCtx(l.ctx, likeCountKey, in.TargetIds...)`）＋ `:56`（`l.svcCtx.BizRedis.HmgetCtx(l.ctx, dislikeCountKey, in.TargetIds...)`）＋ `service/like/rpc/internal/logic/likeactionlogic.go:193`（`l.svcCtx.BizRedis.EvalCtx(l.ctx, LikeActionLua, …)`）⇒ like rpc 的 Redis 调用**全部**是 `*Ctx` 变体（`HmgetCtx`／`HsetCtx`／`HgetCtx`／`ExpireCtx`／`ZaddCtx`／`ZrevrangebyscoreWithScoresAndLimitCtx`／`GetCtx`／`SetexCtx`／`EvalCtx`／`ZcardCtx`）⇒ **19 处全带 ctx、0 处不带** ⇒ ctx 取消／deadline 到点 ⇒ Redis 调用随之中断 vs RAG `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/service/LikeDomainService.java:706`（`redis.execute(LikeActionScript.INSTANCE, …)`）＋ `:878`（`redis.opsForHash().multiGet(...)`）＋ `:934`（`redis.opsForHash().put(...)`）＋ `:955`（`redis.opsForValue().set(...)`）＋ `:964`（`redis.expire(key, ttl)`）＋ `:995`（`redis.opsForZSet().add(...)`）⇒ 本域 **12 处** Redis 调用**全部无 ctx／deadline 载体**（like-rpc main 内 grep `DeadlineState`／`remainingMillis` 仅命中 `service/like/like-rpc/src/main/java/com/rag/like/like/rpc/LikeRpcApplication.java:94` 的 `DeadlineQueryTimeoutInjector` ⇒ **只作用于 SQL**）＋ 共享层 `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java:87-94`（明确**不碰** `timeoutOptions` ⇒ 共享层本身也不绑 deadline；`:93-94` 自述「Redis 命令感知请求 deadline = global 台账 **E66**，属另一条」）⇒ ⇒ **差异仍成立**：deadline 到点后 Go 侧 Redis 读写随 ctx 取消而中断、RAG 侧照跑不误 ⇒ 请求预算对缓存读写**无约束** ⇒ ⇒ 落点 **100% 在共享层** `service/common`（Lettuce `TimeoutOptions.TimeoutSource` 逐命令动态返回剩余 deadline ⇒ 零域侧改动）⇒ global 台账 `docs/DRIFT/global/vs-common.md:31`（**E66 = 🔴 OPEN** · 2026-10-29 复核状态不变）＋ `:47`（E66 本轮**转立项**、处方与回归口径已齐 ⇒ **待 Jason 拍板**）⇒ ⇒ 归阶段二（与同批 `docs/DRIFT/_cross/grading/W6.md:14` 的 E66-comment 同口径）⇒ P1（可靠性／容量风险：deadline 到点后缓存读写照跑不误）· **不阻断阶段一** | 2026-10-30 |

---

## 开工前置回执（2026-10-30 轮 · 派生层 `wave` 列落地后的第一轮）

> **落点**：`align/g2-message-like-task` 工作树 · **基线 commit** `bdac3607`（= `do-my-own-gh` @ `bdac3607` · 施工单加 `wave` 列那次）· **日期** 2026-10-30
> ⚠️ **三份 `vs-common.md` 的本节内容逐字相同**（模板要求「会话 + 台账各一份，**同一份内容**」）
> ⚠️ **本节是「读完了」的回执** ⇒ **回执落笔时尚未获批 ⇒ 本 lane 不得开工**（`AGENTS.md` §五·3b-0 ④「经用户批准」＋「调度员不得代批准」）⇒ **Jason 于 2026-10-30 在本 lane 会话内明示「开始工作」** ⇒ **已获批开工**（⑤ 由调度员代填，见本节末尾）。

【开工前置回执 · G2 · 2026-10-30】

**① 任务范围**

本 lane 本轮任务 = **按派生层施工单 `wave=G2` 派工面（281 条）取本 lane 可动的部分**，逐条**回真源复核**后落码/销号：

| # | 条目 | 派生层判定 | 本轮处置 |
|---|---|---|---|
| 1 | `DRIFT-MSG-V-032` | **B 类 · 🟡 部分闭合**（**G2 唯一一条 A/B 类状态开放项**）· 缺口 =「无可复算指针」 | **落码加固**（`service/message/message-rpc/src/test/**` ⇒ G2 写权面内） |
| 2 | `SD-04` / `DRIFT-MSG-V-027` / `DRIFT-MSG-V-020` | **D 类 · 差异已消失**（台账未销账） | **回源复核 ⇒ 回写台账销号**（原值保留） |
| 3 | `D-2`（like）/ `D-6`（like）/ `D-4`（message） | **D 类 · 差异已消失** | **回源复核 ⇒ 回写台账销号** |
| 4 | `SD-2026-10-23-09`（like） | **⏳ 未签字 · 未定级**（派生层 `stage=—`） | **复核前提成立 ⇒ 按 §10 定级为 C 类**（阶段 2 承接）⇒ **不推拍板、不改代码** |

**不做**：① 共享面（`service/common/**` ＋ `service/users/users-common/**`）⇒ **只读**；缺陷一律**登记反馈单转 G4**（§5.3e 7 项）② `tools/**`（`tools/difftest/**` 与派生层产物**不属任何 lane** ⇒ 归合并收口执行者）③ **共享索引类文件**（`AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md`）④ **阶段 2 不碰、不预支**（`twoStage.md` §1.3 / §2.2 / §6 / §7）⑤ **本轮只做「阶段一范围内 + 本域能自修」的活** ⇒ C/D 类只登记不定实现。

**② 已读四档（本 lane 面）**

| 档 | 路径 | 读到的关键结论（可核） |
|---|---|---|
| `docs/DRIFT/` | `message/{api,rpc,common,proto}.md` | `SD-2026-10-23-03`（`affected`：Go 返 `int64` 数字 / RAG 出字符串）**已签字** ⇒ 对拍必红、**不得单方面改回数字**；`message/rpc.md` 的 **E59 已销案**（DB 失败原样上抛 ⇒ `UNKNOWN`＋1015 ＝ Go）；`-010`/`-011`/`-013` 落点共享层 ⇒ 已在 `docs/DRIFT/message/feedback-to-g4.md` 转派 G4；`-012` 属容器层框架限制**不可修**；**`DRIFT-MSG-V-032` = 本轮唯一 A/B 类开放项**（`:2801`，登记形态「🟡 登记（不改）」） |
| | `like/{api,rpc,proto}.md` | `like/rpc.md` §12：`DRIFT-LIKE-003` 拆三段 = A 投递链路已闭环 ＋ B 权威读语义 Java 化 ＋ **C 暴露形态待拍板**；**E60 已销案**；`L-U11` 已闭合；`L-U9` = 取证缺口（非必相对齐项）；`like/api.md`：`404 body` ＝ **19** 字节；`like/proto.md`：`LikeProtoContractTest` 4/4 |
| | `task/{api,rpc,proto}.md` | `task/rpc.md`：`TaskRpcKafkaListenerRegistryGateTest` 3/3（listener **真进容器 registry**）；`DRIFT-TASK-RPC-008` 已落码；`task/api.md`：`DRIFT-TASK-API-004`（charset/空白）＝ **全域共性**，不在本域单修 |
| `docs/Intentional_Deviations/`（**最危险的一档：漏读会把签字偏离当新回归「顺手修回 Go 行为」**） | `message/{api,rpc}.md` | `message/api.md`：**`SD-2026-10-23-03`**（`affected` 类型）＋「`-009` 已改正为签字」；`message/rpc.md`：**7 条**（`SD-07` 已撤销）= `-01` 索引名不同 · `-02` 直方图桶零交集 · `-03` `errors.As` vs `instanceof` · `-04` 6 个 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧多出来的 · `SD-2026-10-23-08` CPU 采样 fail-open · `SD-2026-10-23-06` 索引列数 1 vs 2 ⇒ ⇒ **这 6 条见到「不一致」不得当新回归去修** |
| | `like/{api,proto,rpc}.md` | `like/proto.md`：**`SD-2026-10-23-05`**（proto `package` 由 `pb` 归一为 `like`，**wire 层可见**）；`like/api.md`：**`SD-2026-10-23-08` 已失效·已撤销**（共享层 T-6 已补 charset）＋ **`SD-2026-10-23-09` 仍成立·⏳ 未签字**（裸 503）＋ `SD-2026-10-24-10`（毫秒粒度）＋ `SD-2026-10-24-11`；`like/rpc.md`：**`SD-2026-10-23-06`**（业务码迁到 trailer `rag-like-biz-code`，**框架限制**）· **`SD-2026-10-23-07`**（Nacos 注册失败**不退出**）⇒ ⇒ **不得「顺手改回 Go 行为」** |
| | `task/rpc.md` | **`SD-2026-10-23-TASK-01`**（Go 30 **纳秒** → RAG 30 分钟；Go 侧是**笔误不是设计**）· **`SD-2026-10-24-TASK-01`**（topic 缺失时 Go 进程退出 vs RAG 存活）＋ 4 项「不是偏离、但容易被误当成偏离」；`task/api.md` ＋ `task/proto.md` = **0 字节坑位**（勿建空壳） |
| `docs/PhaseGoal/` | `g2.md` ＋ `PhaseGoal-judge.md` | `g2.md:21-31` = 11 组件矩阵（10 ✅ ＋ `like-rpc` 🟡）⇒ **机器生成**，权威源是 judge；`g2.md:24` = `like-rpc` 的 `DRIFT-LIKE-003` **仅剩 C 暴露形态待拍板**；`g2.md:41`（§2.1 自修清单）＝ **本 lane 自修项只有 `like-rpc` 一条**；`g2.md:47`（§2.2 转派 G4）＝ message-api `-010`/`-011`/`-013`；`g2.md:57-62`（P0 分摊）＝ **P0-1 本 lane 主责**（like 域事实投递链路 ＋ 双侧活体探针**不在版本控制内** ⇒ 未取证）· **P0-3** `check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引/唯一约束/触发器**至今没有机器核过** · **P0-6** 全量回归 2860 条 / 0F / 0E / 4S ⇒ **必须带 `clean`**；`g2.md:73-83`（矩阵 C）＝ **X-10**「落仓静态守门只覆盖 4/10」 |
| `docs/PhasedGoal.md` | `:38-52`（P0 九条） | **P0-1**「核心业务链路**真实运行**」· **P0-4**「核心失败路径有明确行为」· **P0-6**「测试断言真实业务结果」· **P0-9**「已知缺陷、**无法验证的关键能力和运行前提**得到明确记录」⇒ ⇒ `003-C` 与 `L-U9` 属 P0-1/P0-9 的「运行前提」面 ⇒ 台账必须写「**未取证**」而不是「已对齐」 |
| `docs/twoStage.md` | §4（`:258-313`）· §5.3（`:359-380`）· §5.4（`:381-398`）· §10（`:941-976`） | §5.3 = 差异与暂缓项**每项至少 11 条**（编号/原始契约/原始行为/阶段一当前行为/具体差异/暂缓理由/影响/是否阻断/阶段二恢复方案/阶段二测试场景/证据缺口）⇒ **「不得只写『后续优化』」**；§5.4 = 每次改造记录 **9 条**（改前/问题/改后/理由/变更文件/测试场景与断言/执行结果/未解决/对阶段二影响）⇒ ⇒「**必须实际修改项目，而不是只输出建议**」；§10 = **P0–P3 是严重程度、A–D 是阶段归属，两者不能互相替代**；每问题必含 12 项；**必须区分已确认缺陷与待验证风险** |
| `docs/Pitfalls/` | `message/{api,rpc}.md` | `PIT-MESSAGE-01`（同组件同形态**第二次**「绿测试守卫错误答案」）· `-02`（Jackson `FAIL_ON_NULL_FOR_PRIMITIVES` 默认 true）· `PIT-MESSAGE-09`（「**测试全绿**」与「**一次没起过**」可同时成立）· `-10`（「断言 Bean 存在」≠「断言机制生效」）· `-12`（注释里的**默认行为**必须读到**消费点**） |
| | `like/{api,rpc}.md` | `PIT-LIKE-01`（go-zero 解析层**不对称**，只学一半必然漏）· `PIT-LIKE-05`（「**应用入口类的 javadoc**」是最高杠杆的错误传播点）· `PIT-LIKE-06`（**同一个文件里会自相矛盾**）· `PIT-LIKE-RPC-013`（「**『错误断言已清理』这句话，本身也是一条需要被核的断言**」）· `-14`（共享组件 javadoc 与各域 javadoc **可以互相矛盾**） |
| | `task/{api,rpc,proto}.md` | `PIT-TASK-01`（`@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」⇒ **15 条真 PG 用例静默全跳**）· `-06`（Go `Pipeline()` 不是事务、Spring `executePipelined` 也不开 MULTI ⇒ **两边都理解错了**）· `-07`（81 条测试**零真 Redis** 覆盖 ⇒「跑绿」证明不了 Redis 路径）· `-11`（方法签名对了 ≠ 方法会被调到，**调用时机**更致命） |
| | `_cross/`（**四棵都要读**） | `testing-pitfalls.md`（证据分级 E0–E5 · **变异测试是另一半**）· `fix-round-lessons.md`（「**跑绿 ≠ 判据活着**」· 修漂移**先改/删错误断言再改实现**）· `flyway-ledger-collision.md`（**共享面修法 ⇒ G4 落码**）· `signed-deviations.md`（**签字偏离不得当新发现再修一遍**）· `framework-mechanism-evidence.md`（**框架机制必须验证实际生效**） |
| `docs/DRIFT/_cross/`（**四棵都要读**） | `stage1-derivation.md` ＋ `.tsv` · `component-coverage-matrix.md` · `cross-domain-coupling.md` · `domain-common-lanes.md` · `ddl-type-divergences.md` · `go-anchors.md` · `decisions.md` | **施工单加 `wave` 列**（`bdac3607`）⇒ **本 lane 的派工面 = `wave=G2` 共 281 条**；§4.0 波次分布 = G2 **状态开放 77 条 / 其中 A/B 类 0 条**（⇒ **无漏排**）；§4.1 A/B 类真缺口 = **空表** ⇒ ⇒ **本轮 G2 的真活 = ① 唯一一条 A/B 类开放项（`DRIFT-MSG-V-032`）② §4.4 ④ 档的台账销账 ③ 1 条未定级的 ⏳ 未签字候选**；`cross-domain-coupling.md` = G1~G4 波次表 ＋ **共享层写权规则**（`service/common` ＋ `users-common` **只有 G4 能写**）＋ 依赖 DAG；`ddl-type-divergences.md` = 类型/长度 **9 域 0 差异**，`hot` = **N/A** ⇒ 不许写成「已对齐」；`decisions.md` = 问题 1「**直接丢掉 Flyway**」＋ 问题 4「**每模块独立账本表**」 |

**③ 待批准项**

1. **无**（本轮范围已由 Jason 在本 lane 会话内明示「开始工作」批准 ⇒ 无需额外拍板）。
   ⚠️ **本轮刻意不做、留给阶段二或收口的**：① `DRIFT-LIKE-003-C`（like 域权威读**暴露形态**：Go 是**独立进程** `cmd/fact-authority/main.go`；RAG 侧无 HTTP 端点 ⇒ 落地需动**共享面** ⇒ **G2 不可写** ⇒ **形态决策须拍板**）② `D-7`（like 域 status 错误 ⇒ 业务码映射与 Go 不同类 ⇒ 阶段二做一次**双侧活体定值**再决定）③ `SD-2026-10-23-09` 的**签字**（本轮只定级为 C，**不推拍板**）④ 其它模块的同款「发完就 `shutdownNow()`」写法（**阶段二**同族项）。

**④ 等批准 ⇒ 批准后才开工**

**⑤ 批准记录：Jason · 2026-10-30 10:31 · 批准范围：本回执 ① 所列 G2 任务（`wave=G2` 派工面内的阶段一范围条目：`DRIFT-MSG-V-032` 落码 ＋ 5 条已闭合项销号 ＋ `SD-2026-10-23-09` 定级为 C）—— 依据：Jason 于 2026-10-30 在**本 lane 会话内**明示「开始工作」**　← 由**调度员代填**（**代填 ≠ 代批准**；回执人不得自批）**

**⑥ 本轮 §3.3 回源复核的实测记录（**派工前按 `basis` 抽查，不照抄标题**）**

| 条目 | 回源实测 | 结论 |
|---|---|---|
| `SD-04`（§⑥-A 复核行） | Go `proto/message.proto:3` = `package message;` ＋ `:5` = `option go_package = "./pb";` ＋ `service/message/rpc/pb/message_grpc.pb.go:383` = `ServiceName: "message.MessageService",` vs RAG `message-proto/src/main/proto/message.proto:3` = `package message;` | ✅ **两侧 proto package 本来都是 `message`** ⇒ 本域**从未引入**该偏离 ⇒ 派生层把标题里的**交叉引用**当成了**登记** ⇒ **差异已消失 ⇒ 销号** |
| `DRIFT-MSG-V-027`（§⑥-B 复核行） | Go `service/message/rpc/internal/model/db.go:42` = `db.AutoMigrate(...)` ⇒ **Go 侧无版本化迁移账本** vs RAG `message-rpc/src/main/resources/application.yml:66` `baseline-on-migrate: true` ＋ **`:94` `baseline-version: 0`** ＋ `:105` `table: flyway_schema_history_message` ＋ 守门 `MessageFlywaySharedSchemaWiringTest.java:164`/`:179`/`:187` | ✅ **V1 不会被 baseline 跳过**（baseline 打在 version 0）⇒ **差异已消失 ⇒ 销号** |
| `DRIFT-MSG-V-020`（§⑥-C 复核行） | Go `db.go:22` DSN 钉 `TimeZone=Asia/Shanghai` ＋ `:42` `AutoMigrate` vs RAG `MessageRpcSupport.java:107`/`:110` `toEpochSecond(OffsetDateTime)`（**与时区无关**）＋ `V5__message_time_columns_timestamptz.sql:2` | ✅ **两侧 0 差异** ⇒ **销号** |
| `D-2`（like） | RAG `LikeSpans.java` **存在**（12298 B）＋ `service/like/*/src/main` 内 `setAttribute` **11 处命中** ＋ 守门 `LikeSpansAttributeGateTest.java` ＋ `LikeA9GrpcSpanEndToEndTest.java` **均在仓** ＋ 共享层 `RpcInterceptor.java:467-480`（`makeCurrent()` 包住 handler ⇒ 执行期 `Span.current()` **就是** server span） | ✅ **域侧落码 ＋ 共享层 FB-04 落码**两侧闭合 ⇒ **差异已消失 ⇒ 销号** |
| `D-6`（like）/ `D-4`（message） | 域侧 `config/MybatisPlusOrderingBridgeAutoConfiguration.java` **两个域都已不在**（`find` 命中 = 0）＋ 共享层 `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` **在** ⇒ ⇒ **闸门已开、域侧桥已收编** | ✅ **差异已消失 ⇒ 销号** |
| `SD-2026-10-23-09`（§⑥-D 复核行） | Go `service/like/api/**` 内 `maxConns`/`Semaphore`/`worker` 命中 = **0** ⇒ Go 侧**无 worker 池概念** vs RAG `HttpMiddleware.java:154` `MAX_HTTP_WORKERS = 512` ＋ `:369` `catch (RejectedExecutionException saturated)` | ✅ **前提成立**（行为仍在）⇒ 但两侧「过载 ⇒ 拒绝」**大方向同向** ⇒ RAG 多的是**载体与形状** ⇒ **不改回去 ⇒ 不属 DRIFT** ⇒ 按 §10 **按问题本身定级 = C**（未签字候选**不得自动归 D**）⇒ **不推拍板** |
| `DRIFT-MSG-V-032`（§⑥-E 复核行） | Go `service/common/observability/observability.go:41` = `func SlowThreshold() time.Duration`（阈值真源）＋ `service/message/rpc/message.go:44` = `s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))`（打标唯一装配点）⇒ **Go 侧无任何对应测试** ⇒ **本条不是 Go↔RAG 行为差异** vs RAG 守门 `MessageRpcSlowThresholdGateTest.java:280`（用例仍在）＋ `:324`（`warmUp()` —— ⚠️ **台账记的 `:318` 已漂移**）＋ `:349`（`callAndGetSpan` —— 仍准） | ✅ **缺口真实存在**（`:340-346` warm-up「发一次就 `shutdownNow()`」＋ **无重试**）⇒ **落码加固**（域内可自修）⇒ **落点 = `service/message/message-rpc/src/test/**`** |
| `SD-2026-10-23-09` | C（阶段 2 最终工程建设项 · 未签字候选 ⇒ **不得自动归 D**） | P2 | 否 | Go `service/like/api/**` 树内 `maxConns`／`MaxConns`／`Semaphore`／`worker` 命中数 = **0**（2026-10-30 G2 实测 `grep -rn`）⇒ Go 侧每请求一个 goroutine（初始栈 2KB）、**无上限** ⇒ **不存在**「worker 池饱和」这个事件；⚠️ 对照：Go 侧**确实有**「过载 ⇒ 拒绝」这条语义，只是载体不同（go-zero `rest.WithMiddlewares` 的限流/熔断中间件拒绝，**不是**线程池饱和）。RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:154`（`static final int MAX_HTTP_WORKERS = 512`）＋ `:181`（池按 512 建）＋ `:193`（注释「饱和 ⇒ 抛 `RejectedExecutionException`，由调用点转成**裸 503**」）＋ `:369`（`catch (RejectedExecutionException saturated)` ⇒ 容量拒绝路径）⇒ ⇒ **差异仍成立**（RAG 侧存在 Go 侧**结构上不存在**的「worker 池饱和 ⇒ 裸 503（无 body、无 Content-Type、无业务码、无 envelope）」这个可观察面）；⚠️ **但两侧的「过载 ⇒ 拒绝」大方向同向** ⇒ RAG 多出来的是**拒绝的载体与形状**，不是「Go 会接受而 RAG 会拒绝」⇒ ⇒ **不改回去 ⇒ 不属 DRIFT**（§5.3b 判据）⇒ 按 §10 **按问题本身定级**：本条在 `docs/Intentional_Deviations/like/api.md` 里仍是 **⏳ 未签字候选**（核对日期 2026-10-29）⇒ **尚未成为决策** ⇒ 按纪律 5「已签字偏离 ⇒ 是决策不是缺陷」的**反面** ⇒ **不得自动归 D** ⇒ 归 **C**；**P2**（只在「高并发 ＋ 下游卡死」这个窄窗口可观察；触发窗口窄 —— 512 远大于 Tomcat 默认 `max-threads`（200）⇒ 正常流量不该触发；且**不影响任何业务判定**、不涉及数据一致性或安全边界 ⇒ **不命中阶段一 P0 九条** ⇒ **不阻断阶段一**）；**处置 = 阶段二走签字登记**（把它从「候选」升为「决策」）⇒ **本轮不推拍板、不改代码**（§5.3b「登记的**登记**本身就是交付物」⇒ 不把已登记条目打包推给用户逐条拍板） | 2026-10-30 |

---

## 开工前置回执（2026-10-30 轮 · rpc 域指标抓取载体）

> ⚠️ **本轮性质**：**派工指定的载体补齐**（Jason 2026-10-30 明示批准「这波就补载体」）⇒ **④ 已由调度方转达满足**；但 **①②③ 仍须本 lane 自己做完**（AGENTS.md §5.3b-0）⇒ **回执人不得自批**。

### ① 本轮任务界定（定任务）

| 项 | 内容 |
|---|---|
| 派工来源 | 调度会话 `mvs_d218b18e499340d9b6f31e2097e0e420` 转达 · Jason 2026-10-30 明示批准「这波就补载体」 |
| 任务本体 | 给 **rpc 域补「指标抓取载体」**：Go 侧 `DevServer` 起**独立端口**出 Prometheus 文本；RAG 侧对应的载体能力（`spring-boot-starter-web` ＋ Actuator ＋ `server.port`）在 message-rpc / like-rpc **缺席** ⇒ **指标注册了但抓不到** |
| 端口口径 | `message-rpc` = **16067**（Go `service/message/rpc/etc/message.yaml:22`）· `like-rpc` = **9098**（Go `service/like/rpc/etc/like.yaml:22`）· `task-rpc` = **不补**（Go 侧 `service/task/rpc/etc/task.yaml` **无 `DevServer` 段** ⇒ 两侧同形 ⇒ 补了反而是**偏离**） |
| 模板 | `service/follow/follow-rpc/pom.xml:165-217` ＋ `service/follow/follow-rpc/src/main/resources/application.yml`（**照抄，不自创**）⇒ 该域 2026-10-29 已落地同款载体（台账 `docs/DRIFT/follow/rpc.md:351-488`） |
| 本波不做 | ① **共享层 `service/common/**` 一行不碰**（G4 写权面）② **不加任何 opt-out 属性**（共享层正在裁「rpc 域观测口要不要挂 Go 的 rest 中间件」⇒ 合并窗口由收口执行者统一加）③ 不碰其它域 ④ 不 push / 不开 PR ⑤ `like-api` / `message-api` / `task-api` **不在本波**（派工只点了两个 rpc 组件） |

### ② 已读四档（本 lane 面）

| 档 | 路径 | 读到的关键结论（可核） |
|---|---|---|
| `docs/DRIFT/` | `message/{api,rpc,common,proto}.md` | `message/rpc.md`：**`DRIFT-MSG-V-032` 已于上一轮落码销号**；`-010`/`-011`/`-013` 落点共享层 ⇒ 已在 `docs/DRIFT/message/feedback-to-g4.md` 转派 G4；`-012` 属容器层框架限制**不可修**；**本轮新增关注面 = 指标抓取出口**（`message/rpc.md:873`「与 Go 的 `prometheus` 客户端**不是同一套** ⇒ 属产品能力」） |
| | `like/{api,rpc,proto}.md` | `like/rpc.md` §12：`DRIFT-LIKE-003` 拆三段 = A 投递链路已闭环 ＋ B 权威读语义 Java 化 ＋ **C 暴露形态待拍板**（**注意：C 段讲的是「事实权威读的 HTTP 暴露形态」，与本轮「Actuator 观测口」是**不同轴**，不冲突**）；**`U-06`**（Go `DevServer` 端点形态：端口 / 路径 / pprof 三层都不同）**已在 `like/api.md:1744` 定级为 C / P2**，其「全仓统一方案」结论指向 G4 ⇒ **本波只做 rpc 两个组件** |
| | `task/{api,rpc,proto}.md` | `task/rpc.md`：`DRIFT-TASK-RPC-008` 已落码；**Go 侧 `service/task/rpc/etc/task.yaml` 无 `DevServer` 段** ⇒ **本波明确不补**（同形 ⇒ 补了才是偏离） |
| `docs/Intentional_Deviations/`（**最危险的一档：漏读会把签字偏离当新回归「顺手修回 Go 行为」**） | `message/{api,rpc}.md` | `message/rpc.md`：**7 条**（`SD-07` 已撤销）= `-01` 索引名不同 · `-02` 直方图桶零交集 · `-03` `errors.As` vs `instanceof` · `-04` 6 个 `DEFAULT CURRENT_TIMESTAMP` 是 RAG 侧多出来的 · `SD-2026-10-23-08` CPU 采样 fail-open · `SD-2026-10-23-06` 索引列数 1 vs 2 ⇒ ⇒ **这 6 条见到「不一致」不得当新回归去修** |
| | `like/{api,proto,rpc}.md` | `like/proto.md`：**`SD-2026-10-23-05`**（proto `package` 由 `pb` 归一为 `like`，**wire 层可见**）；`like/api.md`：**`SD-2026-10-23-08` 已失效·已撤销** ＋ **`SD-2026-10-23-09` 仍成立·⏳ 未签字**（裸 503，**上一轮已定级 C**）＋ `SD-2026-10-24-10` ＋ `SD-2026-10-24-11`；`like/rpc.md`：**`SD-2026-10-23-06`**（业务码迁到 trailer，**框架限制**）· **`SD-2026-10-23-07`**（Nacos 注册失败**不退出**）⇒ ⇒ **不得「顺手改回 Go 行为」** |
| | `task/rpc.md` | **`SD-2026-10-23-TASK-01`**（Go 30 **纳秒** → RAG 30 分钟；Go 侧是**笔误不是设计**）· **`SD-2026-10-24-TASK-01`**（topic 缺失时 Go 进程退出 vs RAG 存活）＋ 4 项「不是偏离、但容易被误当成偏离」 |
| `docs/PhaseGoal/` | `g2.md` ＋ `PhaseGoal-judge.md` | `g2.md:21-31` = 11 组件矩阵（10 ✅ ＋ `like-rpc` 🟡）⇒ **机器生成**，权威源是 judge；`g2.md:41`（§2.1 自修清单）＝ **本 lane 自修项只有 `like-rpc` 一条**；`g2.md:47`（§2.2 转派 G4）＝ message-api `-010`/`-011`/`-013`；`g2.md:57-62`（P0 分摊）＝ **P0-1 本 lane 主责** · **P0-6** 全量回归 ⇒ **必须带 `clean`**；`g2.md:73-83`（矩阵 C）＝ **X-10**「落仓静态守门只覆盖 4/10」 |
| `docs/twoStage.md` | §4（`:258-313`）· §5.3（`:359-380`）· §5.4（`:381-398`）· **§十**（`:941-976`）· 维度 8（`:754-768`） | §5.3 = 差异与暂缓项**每项至少 11 条**（**不得只写「后续优化」**）；§5.4 = 每次改造记录 **9 条**（**必须实际修改项目，而不是只输出建议**）；**§十 = P0–P3 是严重程度、A–D 是阶段归属，两者不能互相替代**；每问题必含 12 项；**必须区分已确认缺陷与待验证风险**；**维度 8**（日志/指标/追踪）明写「**仅发现依赖或监控面板模板不能证明可观测性完整**」⇒ ⇒ **本轮「补载体」正落这一条** |
| `docs/Pitfalls/` | `message/{api,rpc}.md` | `PIT-MESSAGE-01`（同组件同形态**第二次**「绿测试守卫错误答案」）· `-02`（Jackson `FAIL_ON_NULL_FOR_PRIMITIVES` 默认 true）· `-09`（「**测试全绿**」与「**一次没起过**」可同时成立）· `-10`（「断言 Bean 存在」≠「断言机制生效」）· `-12`（注释里的**默认行为**必须读到**消费点**）· **`-37`（上一轮新登记：回执小节被派生层当成条目 ⇒ 凭空多出条目）** |
| | `like/{api,rpc}.md` | `PIT-LIKE-01`（go-zero 解析层**不对称**，只学一半必然漏）· `-05`（「**应用入口类的 javadoc**」是最高杠杆的错误传播点）· `-06`（**同一个文件里会自相矛盾**）· `-13`（「**『错误断言已清理』这句话，本身也是一条需要被核的断言**」）· `-14`（共享组件 javadoc 与各域 javadoc **可以互相矛盾**） |
| | `task/{api,rpc,proto}.md` | `PIT-TASK-01`（`@EnabledIfDockerAvailable` 把「拉不到镜像」压成「Docker 不可用」⇒ **静默全跳**）· `-06`（Go `Pipeline()` 不是事务、Spring `executePipelined` 也不开 MULTI ⇒ **两边都理解错了**）· `-07`（**零真 Redis** 覆盖 ⇒「跑绿」证明不了 Redis 路径）· `-11`（方法签名对了 ≠ 方法会被调到，**调用时机**更致命） |
| | `_cross/`（**四棵都要读**） | `testing-pitfalls.md`（证据分级 E0–E5 · **变异测试是另一半**）· `fix-round-lessons.md`（「**跑绿 ≠ 判据活着**」· 修漂移**先改/删错误断言再改实现**）· `flyway-ledger-collision.md`（**共享层修法 ⇒ G4 落码**）· `signed-deviations.md`（**签字偏离不得当新发现再修一遍**）· `framework-mechanism-evidence.md`（**框架机制必须验证实际生效**） |
| `docs/DRIFT/_cross/`（**四棵都要读**） | `stage1-derivation.md` ＋ `.tsv` · `component-coverage-matrix.md` · `cross-domain-coupling.md` · `domain-common-lanes.md` · `grading/W*.md` | 派生层产物 = **派工面**（`wave` 列 G1~G4）⇒ 本波条目落 **`wave=G2`**；**共享层写权规则**（`service/common` ＋ `users-common` **只有 G4 能写**）⇒ ⇒ **本波一律只读**；`grading/W28.md`（follow 域 `E2` 定级 **D** ⇒「**同款 5 处（favorite-rpc / message-rpc / like-rpc / user-rpc / admin-rpc）属别的域，各域各判**」）＋ `grading/W1.md`（follow 域 `E31` 定级 **D**）⇒ ⇒ **本波按「各域各判」办，只判 message-rpc / like-rpc** |
| `docs/DRIFT/follow/`（**模板参照域**） | `follow/rpc.md:351-488` ＋ `follow/rpc.md:20` | **落码清单 4 行**（starter-web ＋ `server.port` ＋ `register-enabled: false` ＋ 桶边界）＋ **残余 5 条全部 P3 / D 类**（暴露路径不同 · 载体不同 · `le` 文本 · `_max` 增项 · **Go 有 `/debug/pprof/*` 而 RAG 无**）⇒ ⇒ **本波沿用同一口径登记残余项，不新造偏离** |

### ③ 待批准项

1. **无**（本轮范围已由 Jason 明示批准「这波就补载体」⇒ 无需额外拍板）。
   ⚠️ **本轮刻意不做、留给收口执行者或阶段二的**：① **共享层「rpc 域观测口要不要挂 Go 的 rest 中间件」的取舍**（共享层 3 个 `@ConditionalOnWebApplication(SERVLET)` 自动配置会因本波补载体而**首次在 rpc 进程激活** ⇒ 属共享层 ⇒ **G4 / 收口执行者**；**本波不加任何 opt-out 属性**）② `DRIFT-LIKE-003-C`（like 域事实权威读的**暴露形态**，**不同轴**）③ `D-7`（like 域 status 错误 ⇒ 业务码映射与 Go 不同类）④ `SD-2026-10-23-09` 的**签字**（只定级为 C，**不推拍板**）⑤ **Go 侧 `/debug/pprof/*` 的对应物**（RAG Actuator 无 pprof endpoint ⇒ 按 follow 域口径列 **P3 / D** ⇒ 不修）。

### ④ 等批准 ⇒ 批准后才开工

### ⑤ 批准记录：Jason · 2026-10-30 · 批准范围：**给 `message-rpc`（16067）与 `like-rpc`（9098）补「指标抓取载体」**（`task-rpc` 不补）—— 依据：调度会话转达 Jason 2026-10-30 明示批准「这波就补载体」**　← 由**调度员代填**（**代填 ≠ 代批准**；回执人不得自批）**

### ⑥ 本轮 §3.3 回源复核的实测记录（**派工前按 `basis` 抽查，不照抄标题**）

| 回源对象 | 本轮自读实测 | 结论 |
|---|---|---|
| Go 侧 yaml（message） | `service/message/rpc/etc/message.yaml:19-26` = `DevServer: {Enabled: true, Host: 0.0.0.0, Port: 16067, MetricsPath: /metrics, HealthPath: /healthz, EnableMetrics: true, EnablePprof: true}` | 端口 **16067** 确认（**一字不差**） |
| Go 侧 yaml（like） | `service/like/rpc/etc/like.yaml:19-26` = 同款，`Port: 9098` | 端口 **9098** 确认（**一字不差**） |
| Go 侧 yaml（task） | `service/task/rpc/etc/task.yaml`（实测 1-40 行）= `Name` / `ListenOn` / `Etcd` / `Telemetry` / `Kafka` / `LikeRedis` … ⇒ **无 `DevServer` 段** | ⇒ **`task-rpc` 不补载体**（两侧同形；补了才是**偏离**） |
| Go 侧**消费点**（不是声明点） | `go-zero@v1.10.2/zrpc/config.go:39` = `RpcServerConf struct { service.ServiceConf ... }` ⇒ rpc 侧内嵌 `ServiceConf` ⇒ `go-zero@v1.10.2/core/service/serviceconf.go:40` = `DevServer DevServerConfig \`json:",optional"\`` ⇒ ⇒ `go-zero@v1.10.2/zrpc/server.go:63` = `if err = c.SetUp(); err != nil {` ⇒ ⇒ `go-zero@v1.10.2/core/service/serviceconf.go:77` = `devserver.StartAgent(sc.DevServer)` | **rpc 侧确实会起 DevServer** ⇒ **不是死配置**（消费点在框架内、仓内 grep 不到读取点） |
| Go 侧起法细节 | `go-zero@v1.10.2/internal/devserver/server.go:79-81` = `StartAgent` → `if !c.Enabled { return }`；`:82-86` = `once.Do(...)`（**进程内只起一次**）；`:44` = `HealthPath`；`:47-50` = `EnableMetrics` ⇒ `prometheus.Enable()` ＋ `handleFunc(MetricsPath, promhttp.Handler().ServeHTTP)`；`:53-59` = pprof 五条路由；`:67-76` = `StartAsync` ⇒ **`:73` = `http.ListenAndServe(addr, s.server)`** ⇒ **独立端口、不是主端口**；`internal/devserver/config.go:5-13` = `Port` 默认 6060 / `MetricsPath` 默认 `/metrics` / `HealthPath` 默认 `/healthz` | 端口与路径**默认值**与真源 yaml 一致 ⇒ RAG 侧取同端口、只改路径 |
| RAG 侧现状（message-rpc） | `service/message/message-rpc/pom.xml:171-179` = 第 5 段**已有** `spring-boot-starter-actuator` ＋ `micrometer-registry-prometheus`，**无** `spring-boot-starter-web`；`service/message/message-rpc/src/main/resources/application.yml:185-205` **已有** `management.endpoints.web.exposure.include: health,info,prometheus,metrics` ＋ `endpoint.prometheus.enabled: true` ＋ `prometheus.metrics.export.enabled: true`；**全文件无 `server.port`** | ⇒ **`management.*` 是死配置**（classpath 无 `jakarta.servlet.Servlet` ⇒ `WebApplicationType=NONE` ⇒ 那一族自动配置**根本不加载**，**不报错**）⇒ 与 follow 域**同款缺陷、同款修法** |
| RAG 侧现状（like-rpc） | `service/like/like-rpc/pom.xml:229-237` = 第 7 段**已有** actuator ＋ micrometer-prometheus，**无** starter-web；`service/like/like-rpc/src/main/resources/application.yml:302-320` **已有** 同款 `management.*`；**全文件无 `server.port`** | 同上 |
| 🔴 **补 web starter 的连带风险（本轮自读发现 · follow 域已踩过）** | `service/message/message-rpc/src/main/resources/application.yml:107-114` ＋ `service/like/like-rpc/src/main/resources/application.yml:197-201` = `spring.cloud.nacos.discovery` 段**均无** `register-enabled: false`（而 `service/follow/follow-rpc/src/main/resources/application.yml:112` **有**）⇒ 补上 servlet 容器后，`NacosAutoServiceRegistration` 会注册**观测口**（16067 / 9098）⇒ 同一服务名下出现**两个端口的实例** ⇒ 调用方按 Nacos 给的地址连到一个只提供 `/actuator/` 的 HTTP 口 ⇒ **gRPC 链路直接断**（业务码塌成 1015） | ⇒ **必须同时补 `register-enabled: false`**（本域 yml 改动 ⇒ **在 G2 写权内**）⇒ 本模块注册是**手写**的（`bootstrap/NacosRegistration.java` 用 `GrpcServerProperties.getPort()`）⇒ 与 Go `zrpc.MustNewServer` 注册 `ListenOn` 的语义一致 |
| 共享层激活面（**只读 · G4 写权面**） | `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` = 6 行 ⇒ 其中 **3 行是 `@ConditionalOnWebApplication(SERVLET)`**：`GoStyleJsonContentTypeAdvice` · `GoStylePathNormalizingAutoConfiguration` · `GoStyleBreakerRouteTemplateAutoConfiguration` ⇒ ⇒ 补 servlet 容器后它们**在 rpc 进程首次激活** | ⇒ **Go 的 rpc 侧 `zrpc.MustNewServer` 一个 rest 中间件都没挂** ⇒ **「rpc 域观测口要不要挂 Go 的 rest 中间件」是共享层决策** ⇒ ⇒ **本波只登记、不加任何 opt-out 属性**（合并窗口由收口执行者统一加） |
| 共享层激活面的**实际影响**（本轮自读） | `service/common/src/main/java/com/rag/common/http/GoStylePathNormalizingFilter.java` 的 `doFilter`：**已 Clean 的路径直接 `chain.doFilter(request, resp)` 原样下传**（`/actuator/prometheus` 本身已 Clean）⇒ **不影响**该端点；`GoStyleJsonContentTypeAdvice` 只在「响应体是 JSON」时改 Content-Type ⇒ Prometheus 文本（`text/plain; version=0.0.4`）**不受影响** | ⇒ 本波补载体**不会**因这两个自动配置而抓不到指标 ⇒ **实测以守门为准**（§3.3：推演不算证据） |

**⇒ 本轮结论（④ 之外的部分）**：**① 已定任务** · **② 四档已读** · **③ 无待批准项** · **⑥ 回源已逐条自读**（Go 侧 yaml + go-zero 框架消费点 + RAG 侧现状 + 共享层激活面）⇒ ⇒ **可以落码**。
