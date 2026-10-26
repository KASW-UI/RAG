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
| **A1** | 超时 / deadline | api：`LikeRpcClient.java:133-147`（`min(2000ms, 剩余)`）+ `LikeApiProperties.java:125`（默认 2000ms）· 消费点 `HttpMiddleware.java:304`（`begin()`）/`:336`（`clear()`）<br>rpc：`LikeRpcApplication.java:90`（`@Import`）+ `:137`（`server.intercept(...)`）+ `application.yml:299` `rpc-timeout: 2s` | ✅ 一致（Go `zrpc/config.go:31` 客户端 2000 / `:46` 服务端 2000 / `rest/config.go:55` HTTP 3000 ⇒ 有效值 = `min(2s, 3s−已耗)`，RAG 同式） | L1 | 2026-10-27 |
| **A2** | gRPC 拦截器链 | 服务端：`LikeRpcApplication.java:137` → `GoStyleRpcProtectionChain.java:61-78`（recover→breaker→shedding→business）<br>客户端熔断：`GoStyleGrpcClientBreakerInterceptor.java:103-105`（`@Component` + `@GrpcGlobalClientInterceptor`）⇒ 被 `LikeApiApplication.java:75-78`（扫 `com.rag.common`）注册<br>🟡 **域侧 javadoc 假断言**：`LikeRpcClient.java:43-54` 仍写「Java 侧尚未接入」 | 🟡 **OBS**（链序 ✅；🟡 **域侧 javadoc 与事实相反** ⇒ 域侧自修 D-1；🟡 **like-api 无客户端熔断 E2E 守门** ⇒ 其余域都有 ⇒ 域侧补 D-1） | L1（源码） | 2026-10-27 |
| **A3** | HTTP 保护链 | 四个 Filter：`GoStyleProtectionConfiguration.java:55-57` ⇒ like-api 扫 `com.rag.common` ⇒ 扫到即生效（`LikeApiProtectionMvcTest.java:96-103` 断言 4 个 Filter Bean 存在）· 域侧配置 `application.yml:126-140` · form-limit `application.yml:122-125` `mode: envelope` + 消费点 `GoStyleFormValueLimitInterceptor.java:90-93` + `GoStyleTomcatParameterLimitCustomizer.java:105-118` | 🟡 **OBS**（接线齐备、`mode=envelope` 与 Go `likecenter.go:48` **同向** ✅；🟡 **路径归一缺失** ⇒ 派单 **FB-01**；🟡 **缺本域真实请求证据**） | L1 | 2026-10-27 |
| **A4** | 错误码 / envelope | `GlobalResponseAdvice.java:59-72`（包一层 + `body==null \|\| instanceof Response` 双守卫）· `GlobalExceptionHandler.java:106-110`（CodeError→200）`:229-236`（兜底 5001）`:86-90`（401 空 body）· `GoStyleRouterErrorResponses.java:121-140`（404 nosniff / 405 Allow）· rpc `LikeServiceGrpcImpl.java:108`（`onError` 不抛）<br>🔴 **阶段 2 新发现**：**「下游 gRPC status → 业务码」这一层两侧不同**（详见 §3 **D-7**）| 🟡 **OBS**（envelope 形状 + handler 接线 ✅ 与 Go `likecenter.go:41-47` SetOkHandler + `:48-61` SetErrorHandler 同形；**但 status → 业务码这层不同** ⇒ 见 D-7）| L1（源码逐条） | 2026-10-27 |
| **A5** | 端口治理 | 生产 `application.yml:33` `18887` · rpc `application.yml:164` `8082`（= Go `etc/likecenter.yaml:2-3` / `etc/like.yaml:2`）<br>🟡 测试 `LikeApiRouterErrorRealTomcatTest.java:54` 仍 `RANDOM_PORT` | 🟡 **OBS**（生产端口 ✅；🟡 测试端口段 ⇒ 域侧自修 D-4；⚠️ 选段须避开 favorite 21300–21399 / 探针 21400–21599 / OS 临时段 49152–65535） | L1 | 2026-10-27 |
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
| D-4 | **like-api 仍用 `RANDOM_PORT`** | `LikeApiRouterErrorRealTomcatTest.java:36`（javadoc）+ `:54`（`webEnvironment = RANDOM_PORT`）⇒ 落在 OS 临时端口段 49152–65535（**Docker 随机发布端口同段**） | 本域自修：引入 `RealTomcatPort` 同款（favorite 域 2026-10-25 已改 `DEFINED_PORT` + 专用段 21300–21399 + 双绑定自检）⇒ like 选段须避开 favorite / 探针 / OS 临时段（建议 21600–21699，**选段表归 global 协调**） |
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

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [x] **两侧同时跑**的探针（AGENTS.md §3.3）—— 🟡 **RAG 侧已有 L4**（A9 探针：真 server + 真链 + 真 OTel SDK ⇒ 坐实 `Span.current()` 在 handler 期 = invalid）；**Go 侧仍 L3**（未跑 Go 活体）⇒ §6 列了剩余缺口
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **5 条全部重核，4 条过期快照已订正**
- [x] §3 的域侧修法**落码 + 守门**（阶段 2）—— ✅ **D-2 已落**（like A9 span 属性 + 守门 11/11 + 变异验证）；🟡 **D-1 / D-4 / D-5 进行中**
- [x] §4 的派单**登记**（本文件 §4 **FB-01** ＋ **FB-04**）＋ **实时轨已发** `session send` 给 `G4Common`（FB-04 位 14）
- [x] **共享面不回写**（合并后由整合者统一更新）

## 6. 未验证项（本轮明确留下的缺口，**不写成已闭环**）

1. **A3 缺本域真实请求证据**：超 2048 参数的 query ⇒ 拒绝形状应是 `200 + {code:5001,msg:"系统内部错误"}`（`GoStyleFormLimitExceededException` → `GlobalExceptionHandler.java:229-236` 兜底 → `LikeErrorMessages.java:44`）⇒ **必须与 Go 侧同请求对拍**才算 L4。
2. **FB-01 的「RAG 返 404」** —— 🟢 **Go 侧已由 G4Common 活体坐实**（go-zero 真 server，5 种路径变体全 200，2026-10-27）⇒ 剩下的是 **RAG 侧**的对应证据，**正在 G4 手里**（共享层归一器 + 真 Tomcat 守门）⇒ 本域只需在收口回归时复核。
3. **A2 客户端熔断缺双侧活体**：like-api 真实 channel 上拦截器是否真在链中，未跑 ⇒ **「Bean 存在」≠「机制生效」**（§3.3.3）。
4. 🆕 **A9 的 gRPC 路径端到端缺 RAG 侧真链证据**：域侧属性写法已落码且守门绿（域内契约级），但**真链上**「server span 携带 `query.*`/`cache.*`/`like.*`」这条**端到端证据要等 FB-04 落码后才拿得到**（探针已证：handler 期 `Span.current()` = invalid）⇒ **收口窗口**补一条 E2E（真 server + 真链 ⇒ 断言导出的 server span 带属性）。
5. 🆕 **A9 的 Go 侧活体未跑**：Go 侧属性清单目前是 **L3**（源码逐条对照 + go-zero `tracinginterceptor.go` 取证）⇒ 未与 RAG 侧做「同一属性名 + 同一取值」的**双侧活体对拍** ⇒ A9 轴的最终结案仍需该对拍（AGENTS.md §3.3.3：无双侧活体不得写 L4）。
4. **A9 缺双侧活体**：Go 侧 7 处 span 属性 vs RAG 侧 0 处 —— 差异已由**源码逐条对照**坐实（L1），但**未跑双侧探针** ⇒ 落码后须补一条两侧 span 属性对拍探针。
5. **`like_promotion_topic` 生产与消费同 topic**（`LikeKafkaTopics.java:36` + `application.yml:217`）是 Go 现状而非笔误（`LikeKafkaTopics.java:8-16` 已注释）⇒ 灰度期两侧同 topic 消费的字段一致性靠 `LikeEventContracts.java:57`（`@JsonPropertyOrder`）保障，**未经双侧活体对拍**。
