<!-- @new 2026-10-27 · `message-common` lane 台账 · **G2 波次工作树产出**（`align/g2-message-like-task`）· 基线 = `do-my-own-gh` @ `a282012` · **阶段 1 只读对账完成（2026-10-27）** -->

# `message-common` lane 台账 —— 「message ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/message/message-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（G1~G4 波次映射 · 9 条机制面轴 · 写权边界）
> ⇒ **本文件由 `G2` 波次工作树独占**；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/DRIFT/global/**`）本 lane 一律不写** ⇒ 合并后由**合并收口执行者**统一更新（AGENTS.md §5.3d）

> 📌 **术语订正（2026-10-28 · G2 自修 · 依 G1 通报）**：「**整合者**」是**已废称**
> —— `docs/Worktree.md` §1e 明写「『整合者』不是一个存在的角色」⇒ ⇒ 它会诱导 lane 去找一个**不存在的收件人**。
> ⇒ **现行角色词 = 「合并收口执行者」**（`docs/Worktree.md` §1e 表：它是一个**步骤**，不是一个常驻会话 ⇒ 没有任何会话长期占着这个位置）。
> ⇒ ⇒ **本文件正文的「整合者」一律按「合并收口执行者」理解**；下方**历史条目里的原值保留不改写**（留档纪律）。

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `message-common`（原第 7/10 条） |
| **现分支 / 工作树** | **`align/g2-message-like-task`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g2-message-like-task` |
| 基点 | **`do-my-own-gh` @ `a282012`**（= lane 脚手架落地那次提交） |
| 本域模块 | `service/message/{message-api, message-rpc, message-common, message-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/message/**` ＋ `docs/DRIFT/message/**`；🔴 **`service/common/**` + `service/users/users-common/**` 只读**（唯一写者 = `align/g4-service-common`） |
| **本轮核对日期** | **2026-10-27**（§1 全表 + §2 全表） |
| **证据强度上限** | **L1**（两侧源码逐字对照）+ **L3**（静态实测）；**本轮未同时跑两侧进程 ⇒ 全篇无 L4** |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 逐条重核**）

> ⚠️ **本表上一版是 2026-10-24 的抽取快照，已被本轮逐条订正** —— 7 条里 **5 条状态过期**。
> **落点一律「读消费点」判定，不照抄旧列**（AGENTS.md §3.3.1）。

| 编号 | 摘要 | 落点（**读消费点后判定**） | 状态（**2026-10-27 核对**） | 证据 | 域台账 |
|---|---|---|---|---|---|
| `DRIFT-MSG-V-016` | go-zero 三个 unary 拦截器（recover / breaker / CPU shedding） | `service/common` | ✅ **已全处置**（旧快照准确） | 链定义 `GoStyleRpcProtectionChain.java:61-78`；**消费点** `MessageRpcApplication.java:134-135`（`server.intercept(chain.build(...))`）<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已全处置** ⇒ **现行态 = ✅ 已全处置**（**不改判**；短确认 + 补全路径指针）。依据：RAG `service/common/src/main/java/com/rag/common/protection/GoStyleRpcProtectionChain.java:68`（类本体）· `:126`（`compose` —— 构成的**唯一**计算点）· `:158-160`（`build`）· `:187`（`recoverThen`）· `:221`（`breakerThen`）· `:267`（`sheddingThen`）· 消费点 RAG `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/MessageRpcApplication.java:134-135` · Go `go-zero@v1.10.2/zrpc/server.go:144-163`（`setupUnaryInterceptors`：Trace→Recover→Stat→Prometheus→Breaker→Shedding）· 核对日期 2026-10-30。 | [`rpc.md`](rpc.md) |
| `DRIFT-MSG-V-025` | 熔断 / 降载三计数 | `service/common` | ✅ **已修**（旧快照准确） | `GoStyleSheddingStat.java:75-77`（三计数）+ `:84-96`（三个 increment）<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**不改判**；但**本行的证据行号已偏移，本轮已订正**）。依据：RAG `service/common/src/main/java/com/rag/common/protection/GoStyleSheddingStat.java:80-82`（三个 `AtomicLong`：`total` / `pass` / `drop`）· RAG `service/common/src/main/java/com/rag/common/protection/GoStyleSheddingStat.java:89-100`（`incrementTotal` / `incrementPass` / `incrementDrop`）· RAG `service/common/src/main/java/com/rag/common/protection/GoStyleSheddingStat.java:104-105`（`snapshot()` 一次返回三计数）· Go `go-zero@v1.10.2/core/load/sheddingstat.go:13-18`（`SheddingStat` 的 `total` / `pass` / `drop` 三字段）· Go `go-zero@v1.10.2/core/load/sheddingstat.go:37-48`（`IncrementTotal` / `IncrementPass` / `IncrementDrop`）· 核对日期 2026-10-30。<br>🔴 **指针偏移订正**：本行原写的 `:75-77` + `:84-96` **两处都偏移**（真实位置为 `:80-82` + `:89-100`）⇒ **结论不变、指针已改**。 | [`rpc.md`](rpc.md) |
| `DRIFT-MSG-V-024` | DB / 未知异常保留 `UNKNOWN` | ⚠️ **本域** `service/message/message-rpc`（**旧列写 `service/common` = 错**） | ✅ **已修**（旧快照的「落点」列错误） | **消费点** `MessageRpcServiceImpl.java:166`（`Status.UNKNOWN.withCause(e)`）<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**不改判**；短确认 + 补全路径指针）。依据：RAG `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/impl/MessageRpcServiceImpl.java:166`（`observer.onError(new StatusRuntimeException(Status.UNKNOWN.withCause(e)))` ⇒ **兜底码 = UNKNOWN**）· RAG `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/service/impl/MessageRpcServiceImpl.java:132`（`catch (RuntimeException e)`）· Go `go-zero@v1.10.2/zrpc/internal/codes/accept.go:9-17`（`Acceptable` 的 6 码失败名单**不含 UNKNOWN** ⇒ 两侧熔断开路决策不分岔）· 核对日期 2026-10-30。 | [`rpc.md`](rpc.md) |
| `DRIFT-MSGAPI-010` | 成功 / 错误响应 `Content-Type` 缺 `charset` | `service/common` | ✅ **已修**（**旧快照「OPEN」过期**）—— 共享层 T-6 第二十五轮已落码 | `GoStyleJsonContentTypeAdvice.java:127`（常量）+ **消费点** `:151`（`response.getHeaders().set(CONTENT_TYPE, ...)`）；生效链 `AutoConfiguration.imports:2` → `message-api/pom.xml:62-66`<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**不改判**；短确认 + 补全路径指针）。依据：RAG `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:127`（`GO_JSON_CONTENT_TYPE = "application/json;charset=utf-8"`）· RAG `service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:151`（`response.getHeaders().set(HttpHeaders.CONTENT_TYPE, GO_JSON_CONTENT_TYPE)`）· 生效链 RAG `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:2`（`com.rag.common.http.GoStyleJsonContentTypeAdvice`）→ 依赖 RAG `service/message/message-api/pom.xml:62-66`（`com.rag:common`）⇒ **自动配置确实被容器加载**（不只是「类存在」）· Go `go-zero@v1.10.2/rest/httpx/requests.go:153-155`（`withJsonBody` ⇒ `strings.Contains(r.Header.Get(ContentType), ApplicationJson)`）· 核对日期 2026-10-30。 | [`api.md`](api.md) |
| `DRIFT-MSGAPI-011` | 状态行缺 reason phrase | 🔴 **容器层**（Tomcat `Http11OutputBuffer`）—— **既非共享层也非本域**（旧列写 `service/common` = 错） | 🔴 **框架限制，不可修**；已登记 `SD-2026-10-25-CAND-14` | Go 标准库 `net/http/server.go:1602-1606`（`if text := StatusText(code); text != ""` → 写 code + 空格 + text）+ `net/http/status.go:81`（`func StatusText`）；RAG 侧 `HttpMiddleware.java:907-921`（`resp.setStatus(sc)`，**无 reason**）；⚠️ 实测 **Tomcat 11.0.24 的 `Response` 已移除 `setStatus(int,String)`**（`javap`：9.0.63 有、10.1.52 与 11.0.24 **只剩 `setStatus(int)`**）⇒ 应用层无路可走<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **🔴 框架限制，不可修** ⇒ **现行态 = 🔴 框架限制，不可修**（**不改判** —— 按 §4.6「C 类属阶段二暂缓」**不升级也不降级**）。依据：RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:943`（`public void setStatus(int sc)` —— **只有 int 重载，签名层就没有 reason phrase 的位置**）· RAG `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:956`（`resp.setStatus(sc)` ⇒ 交给 Tomcat 的 `HttpServletResponse`，**应用层无路可走**）· Go 侧 `net/http/server.go:1602-1606` + `net/http/status.go:81`（⚠️ **Go 标准库不在本机 ⇒ 这两条本机不可复算**，只作口径说明、不作证据）· 核对日期 2026-10-30。<br>🔴 **指针偏移订正**：本行原写的 `HttpMiddleware.java:907-921` **已失效**（该段现在是 `underlyingLock` 字段 + `writeToOriginal` 的 javadoc）⇒ 真实位置为 `:943-963`（`setStatus` 覆写）+ `:956`（`resp.setStatus(sc)`）。 | [`api.md`](api.md) · [`global/common-tails.md`](../global/common-tails.md) |
| `DRIFT-MSGAPI-013` | 缺 `Traceparent` | `service/common` | ✅ **已修**（**旧快照「OPEN」过期**）—— 共享层 T-8 第十九轮已落码 | `GoStyleTraceparentFilter.java:188-191`（`@Component` + `@Order(+3)`）+ **消费点** `:270-283`（Extract→Start→Inject）；本域接线 `MessageApiApplication.java:63-66`（扫 `com.rag.common`）；Go 出处 `go-zero@v1.10.2/rest/handler/tracehandler.go:61`<br>⚠️ **2026-10-30 真实性复核（message+like lane）**：原判 **✅ 已修** ⇒ **现行态 = ✅ 已修**（**不改判**；短确认 + 补全路径指针）。依据：RAG `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java:188-190`（`@Component` + `@ConditionalOnWebApplication(SERVLET)` + `@Order(Ordered.HIGHEST_PRECEDENCE + 3)`）· RAG `service/common/src/main/java/com/rag/common/http/GoStyleTraceparentFilter.java:270-283`（① Extract → ② Start → ③ Inject，**Inject 在 `chain.doFilter` 之前** ⇒ 与 Go 的 `propagator.Inject` 时序一致）· 本域接线 RAG `service/message/message-api/src/main/java/com/rag/message/message/api/MessageApiApplication.java:63-66`（`scanBasePackages` 含 `com.rag.common`）· Go `go-zero@v1.10.2/rest/handler/tracehandler.go:61`（`propagator.Inject(spanCtx, propagation.HeaderCarrier(w.Header()))`）· 核对日期 2026-10-30。 | [`api.md`](api.md) |
| `DRIFT-MSG-V-029` | `like-rpc` 的 Flyway baseline 缺陷 | `service/like/like-rpc`（**别的域**） | ✅ **已修**（2026-10-25 global `CD-1`〔旧号 `X-1`〕）⇒ **本跨域待办可关闭** | `like-rpc/application.yml:78` `baseline-version: 0` + `:81` 独立账本表名 | [`rpc.md`](rpc.md) |

⇒ **§1 七条 ⇒ 新派 G4 反馈单 = 0 条**（3 条已修 / 2 条「已修但旧落点列写错」/ 2 条落点在容器层或他域）。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**2026-10-27 核对**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | rpc：`MessageRpcApplication.java:85-86`（`@Import DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator`）、`:75-77`、`:134-135`（链装配）；`message-rpc/application.yml:225-226` `rpc-timeout: 2s`<br>api：`MessageRpcClient.java:87`（`GoStyleRequestDeadline.applyTo`）；`message-api/application.yml:226-227` `http-timeout: 3s`· 🔴 **2026-10-27 新增**：`MessageJwtAuthFilter`（+6）—— **3s 预算的作用域**（验签在预算之外，= `DRIFT-MSGAPI-014`）<br>SQL 取消：`DeadlineQueryTimeoutInjector.java:139-145` | ✅ 一致（2s=2000ms / 3s=3000ms 毫秒精度一致；`DEADLINE_EXCEEDED` 文案对齐 `RpcInterceptor.java:109`）<br>🔴 2026-10-27：**3s 预算的作用域也已对齐**（Go 的预算**不含**验签 —— 真源 `observability/http.go:30-34` 关掉原生 TimeoutHandler、改由 `server.Use` 的自建中间件计时 ⇒ 计时器在 `Authorize` **之后**起算；RAG 侧原把验签放在 `HandlerInterceptor`（预算内）⇒ 现已搬到 `MessageJwtAuthFilter`(+6) ⇒ **验签在预算外**，两侧一致）<br>🟢 **2026-10-27（G2 第三/四轮 · 补记）：本组件的 Redis 轴 = **N/A**（**不碰 Redis**）** —— ① RAG 侧：`service/message/message-rpc/pom.xml` **无** `spring-boot-starter-data-redis`（⇒ 无 `commons-pool2` 需求）＋ `src/main/java` 里 `RedisTemplate` / `StringRedisTemplate` / `LettuceConnectionFactory` **命中数 = 0**；② Go 侧：`$GO/service/message/**` 全目录 `redis` **命中数 = 0**（`redis.NewClient` / `MustNewRedis` 均 0）⇒ ⇒ **两侧都不碰 Redis** ⇒ ⇒ 故 **Redis 连接池轴（2026-10-27 第四轮 task/like 两域新立的那条）在本组件 = N/A**（**不是「漏审」** ⇒ 也不是「没配池」⇒ 是**本组件没有这个面**）⇒ 📌 **供整合者/其他 lane 免再猜**：G2 域三组件的 Redis 池状况已查清 —— **task-rpc** 原**无池** ⇒ **已修**（+守门 ⑤⑥）· **like-rpc** 形态已对齐但 `min-idle` 偏离 ⇒ **已修**（4⇒8，+守门 ⑤⑥）· **message-rpc 不碰 Redis** ⇒ **N/A** | L4（Go 腿活体探针：观测点 A `deadline_set=false` / B `deadline_set=true remaining=3s`；**非法 token ⇒ 401 且三个观测点一个都没打印** ⇒ Authorize 在所有 user middleware 之前） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | 服务端：`MessageRpcApplication.java:134-135` → `GoStyleRpcProtectionChain.java:65`(shedding)/`:72`(breaker)/`:77`(recover) **消费点**<br>客户端熔断：`GoStyleGrpcClientBreakerInterceptor.java:104-106`（`@Component` + `@GrpcGlobalClientInterceptor`）⇒ 被 `MessageApiApplication.java:63-66` 注册 | ✅ 一致（链序 recover→breaker→shedding→业务；Go `go-zero@v1.10.2/zrpc/server.go:144-167`）<br>🆕 **2026-10-27 第四轮：A2 轴由 L1 升为 L2（运行期 · 组件级守门 + 变异验证）** —— **本组件此前完全没有链守门**（兄弟组件 task-rpc / like-rpc 都有 ⇒ 本域 A2 只有 L1 源码级证据、**无运行期证据**）⇒ ⇒ 合并 `align/g4-service-common`（merge commit `37816ca` · G4 要求的整支合并协议）后，共享层的 `compose(props)` / `layerNames(chain)` 到位 ⇒ ⇒ **补 `MessageProtectionChainWiringTest`（新类 · 5 条）**：① `@Import` 必须含 `GoStyleProtectionConfiguration` ② 层名列表 == `[recover, breaker, shedding]` ＋ **实际链 == 配置推出的构成** ③ 关掉降载 ⇒ 层名列表变成 `[recover, breaker]` ④ 熔断层真的包住业务 call ⑤ 降载层真的包住业务 call ⇒ ⇒ **组件级、运行期、确定性**（层名是共享层唯一计算点的只读观测 ⇒ 零行为影响）<br>⇒ ⇒ **与客户端那一面合并看**：客户端熔断（`GoStyleGrpcClientBreakerInterceptor` · `MessageApiApplication.java:63-66` 扫 `com.rag.common` 注册）由阶段 2 的 `GoStyleGrpcClientBreakerE2ETest` 覆盖 ⇒ ⇒ **本轴两侧均有运行期证据** | **L2（运行期）** | 2026-10-27 |
| **A3** | HTTP 保护链 | 配置 `message-api/application.yml:121-169`（四道闸门 + `mode: envelope`）<br>共享层消费点：`GoStyleMaxConnsFilter.java:96`(+4) · `GoStyleBreakerFilter.java:77`(+5) · `GoStyleCpuSheddingFilter.java:63`(+6) · `GoStyleMaxBytesFilter.java:83`(+8) · `GoStyleTraceparentFilter.java:190`(+3) · `GoStyleFormValueLimitInterceptor.java:81-97` + `GoStyleFormLimitWebMvcConfig.java:42` · `GoStyleTomcatParameterLimitCustomizer.java:134` | 🟡 **OBS**（三条有界残留：① **form 闸门与鉴权先后** —— **已登记**为「有界残留·刻意不改」（[`global/cross-domain.md`](../global/cross-domain.md) 第十四轮 §⑤之五-1）⇒ **不重复派单** ② **路径归一** ⇒ 🟢 **共享层已落码**（FB-01 · G4 `182fc32`：`GoStylePathNormalizingFilter` 字节级移植 Go `path.Clean` + 自动配置注册点；🟢 Go 侧已 L4 活体坐实）但**已合入本树** ⇒ ✅ **本域已跑真 Tomcat 探针**（第十轮 · A/B 对照组 + 本域独立变异验证）⇒ ⇒ **「路径归一」这一格 = RAG 腿 L4** ③ **缺本域真实请求证据**（envelope 拒绝形状 / 2049 参数 —— `..` 路径**已由第十轮补上**））<br>> 📌 **共享层落点（按符号记，不钉行号 —— G4 树 `182fc32` 的行号合并后会位移）**：FB-01 = `GoStylePathNormalizingFilter` ＋ `GoStylePathNormalizingAutoConfiguration`（唯一注册点）；FB-02 = `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` / `REGISTERED_DEVIATIONS`；FB-03 = `GoStyleRpcProtectionChain.Layered` / `Composition` / `compose(props)` / `layerNames(chain)` ⇒ 共享层侧回归（G4 自测）：**52 模块 / 2442 条 / 0 failures / 0 errors / 4 skipped** / 21:59 min<br>🆕 **2026-10-27 订正（G4 第七次全量 · `5564c27`）**：**2442** 是**第六次**全量的数字 ⇒ **第七次 = 2457 条**（52 模块 / 0 failures / 0 errors / **4 skipped** / **20:28 min** / `MVN_EXIT=0`）⇒ ⚠️ **2457 是 G4 树（52 模块）的口径**，**不是本域数字** ⇒ 本域三域六模块 = **594 条**（`mvn test` 口径）
⇒ ⚠️ **合并后**本域需跑真 Tomcat 探针复核（路径归一）⇒ 那是**收口回归**窗口的事（§5.3e）

🟢 **共享层的 form 语义表已落码**（FB-02 · G4 `182fc32`：`SEMANTIC_REACHABILITY` 逐域取证表）⇒ **message 行 = `Mode.ENVELOPE`** ⇒ 与本域 `mode: envelope` **一致** ⇒ 本域**不在** `REGISTERED_DEVIATIONS` 里 ⇒ **不是**「已登记的例外」，是**真对齐**） | L1（静态） | 2026-10-27 |
| **A4** | 错误码 / envelope | `GlobalResponseAdvice.java:50-71` · `GlobalExceptionHandler.java:90-94`(401 空 body)`:213-218`(404)`:221-229`(405)`:281-288`(兜底 200+5001) · `GoStyleRouterErrorResponses.java:121-126` / `:133-136` | ✅ 一致（Go `service/message/api/messagecenter.go:45-51` SetOkHandler + `:53` SetErrorHandler ⇒ 恒 200）<br>🟡 唯一残留 = reason phrase（容器层，见 §1） | L1 | 2026-10-27 |
| **A5** | 端口治理 | 生产 `message-api/application.yml:36` `8892` · `message-rpc/application.yml:120-121` `8082`（与 Go `etc/messagecenter.yaml:3` / `etc/message.yaml:2` 一致）<br>✅ **阶段 2 已治理**：测试 3 处 `RANDOM_PORT` ⇒ **`DEFINED_PORT`** + 本域专用段 **21700–21799**（`service/message/message-api/src/test/java/com/rag/message/message/api/support/RealTomcatPort.java` · favorite 同款复制）<br>每个测试类另加 **1 条端口段守门** `bindsToDedicatedPortSegment`（断言 `@LocalServerPort` 落在 `FIRST`–`LAST`）<br>⚠️ **守门到底拦什么（本轮实测厘清，勿照抄错误说法）**：**改回 `RANDOM_PORT` 并不会红** —— 因为 `@DynamicPropertySource` 的 `server.port` **优先级高于** `webEnvironment` ⇒ 端口**仍然**落在本域段内 ⇒ 那种改法**其实无害**（只是白写了 `RANDOM_PORT`）。<br>⇒ 守门真正拦的是 **① 端口注入被拿掉**（实测：⇒ 落回 `message-api/application.yml` 的 **8892** ＝ <b>生产端口</b> ⇒ 与真实部署撞端口；实测红在 `bindsToDedicatedPortSegment`，报「实际=8892」）**② `pick()` 返回段外端口**（⇒ 落进 OS 临时段 49152–65535 ⇒ 与 Docker 发布端口同段）| ✅ **一致**（生产端口一致；测试端口已脱离 OS 临时段 49152–65535 ⇒ 不再与 Docker 发布端口同段）<br>🟡 **选段表归 `global` 协调**（本段只在本域登记） | L3（端口段断言 = 运行时证据） | 2026-10-27 |
| **A6** | errmsg 契约 | `MessageErrorCode.java:28-90`（22 码）· `MessageErrorMessages.java:53-74`（`codeMsg` 逐条）+ `:86-92`（未知码回落）· `MessageGrpcError.java:217-248`（`BizCodeFromError`，`:230-232` 先 CodeError 再 status） | ✅ 一致（Go `service/message/common/errmsg.go:27-49` / `:51-73` / `:88-93` / `:99-132` 逐条同） | L1 | 2026-10-27 |
| **A7** | proto 契约 | `service/message/message-proto/src/main/proto/message.proto:1-195` | ✅ 一致（**仅 option 块不同**：Go `option go_package` ↔ RAG `java_package` + `java_multiple_files`；3 enum / 20 message / 字段名·号·类型 / 9 rpc 全等） | L1 | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | `message-rpc/application.yml:66` `baseline-on-migrate` · `:102` **`baseline-version: 0`** · `:105` **`table: flyway_schema_history_message`** · `:103` `locations`<br>`V1__create_message_notification.sql:15-19`（`is_read BOOLEAN` / `extra JSONB` / `created_at TIMESTAMP`）· `V5__message_time_columns_timestamptz.sql:69-92`（8 列 → TIMESTAMPTZ + 5 列 DROP NOT NULL） | 🟡 **OBS**（时间列类型与可空性**已对齐**；🟡 `DEFAULT CURRENT_TIMESTAMP` 残留（Go 侧无默认）⇒ **已登记不修** —— 两侧 INSERT 均显式绑定该列 ⇒ 不可观测；本域 `extra` 是 jsonb ⇒ **jsonb⇒text 不适用**）<br>✅ 账本隔离：`check-flyway-ledger.py` **EXIT=0**（7 模块账本表名两两不同） | L1 / L3 | 2026-10-27 |
| **A9** | trace / otel | `Traceparent`：`GoStyleTraceparentFilter.java:188-191`（装配）+ `:270-283`（消费点）· 全局 provider：`AutoConfiguration.imports:1-2` → `GoStyleOtelAutoConfiguration.java:58-61` → `GoStyleOtelAgent.java:146`（`GlobalOpenTelemetry.set`）<br>`audit.*` 属性：**本域 Go 侧无自定义 span ⇒ N/A** | ✅ 一致（Go `go-zero@v1.10.2/rest/handler/tracehandler.go:61` `propagator.Inject`）<br>🟡 残留 **T-12**：「405 方法不匹配」出口 RAG 多带一个 `Traceparent` ⇒ **出口层级不同**（Go 在 router 层 `patrouter.go:66-72`）⇒ 共享层改不动 ⇒ 已登记 `SD-2026-10-25-CAND-15`<br>⚠️ `GoStyleOtelAgent.java:133-137`（装配失败只记 ERROR 并继续启动）⇒ 「有 Traceparent 头」**不是无条件成立** | L1 / L3 | 2026-10-27 |

> **A3 已预填锚点订正（2026-10-27 核）** —— 共享层 6 个 form 类已落码（`CD-6`〔旧号 `X-6`〕第十四轮）；本域 `mode=envelope` 与 Go `messagecenter.go:53`（注册 `httpx.SetErrorHandler` ⇒ 200 + 业务信封）**同向** ✅。
> ⚠️ **共享层已验 ≠ 本域已验** ⇒ 本域仍缺**真实请求**证据（envelope 拒绝形状 / 2049 参数 / `..` 路径）⇒ 已列 §5 待办。

## 3. 域侧能自修的（**本域直接修**，不甩给 G4）

| # | 条目 | 现状（`文件:行号`） | 建议修法 |
|---|---|---|---|
| D-1 | **过期 javadoc（断言与事实相反）** —— `MessageRpcClient` 仍写「本类没有任何 client 拦截器」「`DRIFT-MSGAPI-004` 已登记未修」 | `service/message/message-api/src/main/java/com/rag/message/message/api/client/MessageRpcClient.java:60-68` | 事实：客户端熔断由共享层 `GoStyleGrpcClientBreakerInterceptor.java:104-106`（`@Component` + `@GrpcGlobalClientInterceptor`）承担 ⇒ 被 `MessageApiApplication.java:63-66` 扫 `com.rag.common` 自动注册 ⇒ **`DRIFT-MSGAPI-004` 已修** ⇒ 改写 javadoc（**只改注释，不改行为**），Go 出处照抄 `go-zero@v1.10.2/zrpc/internal/client.go:112-113`；⚠️ §3.3.2：注释里的「Go: …」断言必须带真实行号<br>✅ **阶段 2 已落码**（`2049a08`）：javadoc 订正为「客户端熔断**已接入**」+ 落点（共享层 `GoStyleGrpcClientBreakerInterceptor.java:103-105` + `MessageApiApplication.java:63-66`）+ 判据（共享层 `:336-345` ⇒ Go `go-zero@v1.10.2/zrpc/internal/codes/accept.go:9-17` 那六个 code）+ 隔离单位（共享层 `:286` ⇒ Go `breakerinterceptor.go:14`）+ 熔断打开的码（`GlobalExceptionHandler.java:275-279` ⇒ **1015 / "service busy"**）<br>⚠️ Go 侧行号本轮**逐条实测复核**（go-zero@v1.10.2：config.go:14 / client.go:112-113 / breakerinterceptor.go:13-18 / accept.go:9-17）<br>⚠️ **本轮不新增守门**：本域已有 `GoStyleGrpcClientBreakerE2ETest`（真 in-process server + 真 channel）⇒ 熔断**在链上**已有**运行证据**（like 域当时缺这条证据才补的 E2E —— 两域处置不同，理由就是这个） |
| D-2 | **A5 测试端口段** —— 3 个真容器测试类用 `RANDOM_PORT` | `MessageApiProtectionRealTomcatTest.java:47` · `MessageApiTimeoutRealTomcatTest.java:55` · `GoStyleGrpcClientBreakerE2ETest.java:56` | 域侧自修：把 `service/favorite/favorite-api/src/test/.../support/RealTomcatPort.java` **复制**到 `message-api/src/test/.../support/`（**不改 favorite**）⇒ `DEFINED_PORT` + 专用端口段（避开 favorite 的 21300–21399、探针的 21400–21599、OS 临时段 49152–65535）+ 双绑定自检<br>✅ **阶段 2 已落码**：新增 `service/message/message-api/src/test/java/com/rag/message/message/api/support/RealTomcatPort.java`（**21700–21799**）+ 3 个测试类改 `DEFINED_PORT` + `@DynamicPropertySource registry.add("server.port", RealTomcatPort::pick)` + 每类 1 条端口段守门<br>⚠️ **选段依据**：避开 favorite 的 **21300–21399**、favorite 探针的 **21400–21599**、favorite 脚手架的 **21950–21999**、OS 临时段 **49152–65535** ⇒ 取 **21700–21799**（like **21600–21699** / task **21800–21899**）<br>⚠️ **为什么各域各复制一份**：`service/common` **不发布 test-jar**（实测 `service/common/pom.xml` 无 test-jar 目标）⇒ 业务模块的 test scope **拿不到**共享层测试类 ⇒ 只能复制（与 favorite 域既定做法一致） |
| D-3 | **台账落点订正** —— §1 把 `DRIFT-MSG-V-024` 落点写成 `service/common`、把 `DRIFT-MSGAPI-011` 落点写成 `service/common` | 本文件 §1（已就地订正）· 另需订正 [`api.md`](api.md) 的同款表述 | 本轮已改本文件；`api.md` 的订正在阶段 2 落码时一并做（**同域写权内**）<br>✅ **阶段 2 已落码**（`2049a08`）：`api.md` 的 MSGAPI-011 归因由「共享层 · 归因待查」订正为「**容器层**（Tomcat）· 框架限制 · 不可修」—— 决定性事实本轮**实测 `javap`**：`tomcat-embed-core` **9.0.63** 有 `setStatus(int)` + `setStatus(int,String)`；**10.1.52 与 11.0.24 只剩 `setStatus(int)`** ⇒ 本项目（Boot 4.0.8 ⇒ `tomcat.version=11.0.24`）**应用层无路可走**<br>⇒ 已引用 `SD-2026-10-25-CAND-14`（🏷 候选，在 `docs/Intentional_Deviations/global/common-tails.md`）—— ⚠️ **该目录不在本 lane 写权内** ⇒ 本轮**只引用、不改** |
| D-4 | 🆕 **G3 预告（2026-10-27）· 删除本域的 MyBatis-Plus 排序桥** —— ⛔ **带闸门：必须等 G4 的共享层桥落码后才删** | 本域持有 `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`（**全仓 9 份域侧桥之一**，本轮 `find` 实测；共享层 `service/common` 命中数 = **0**）＋ 注册行 `service/message/message-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（类注释 `:46` 自带「**什么时候可以删**」条件） | ⛔ **闸门（硬）**：**必须 G4 的共享层桥落码之后才删** ⇒ 否则「域侧删了、共享层还没加」⇒ MP 自动配置退避（`@ConditionalOnSingleCandidate(DataSource)` 找不到 DataSource）⇒ **不产 `SqlSessionFactory` ⇒ 启动失败**<br>⇒ 删后跑本域真上下文守门 ⇒ 判据 = `SqlSessionFactory` 在 + `@Import` 的 `DeadlineQueryTimeoutInjector` **真在执行链上**<br>⇒ 两座桥同向（都是 `after=DataSource, before=MP`）⇒ 并存期功能等价、**不需要跨树同一次提交**<br>⚠️ 本条是 **G3 的预告（非派单）** ⇒ 等 G3 正式派单再动手；🔴 **G4 台账目前查不到「排序桥」条目** ⇒ 共享层那一半的落码状态**待证**<br>🆕 **G3 口径订正后 · G2 独立核实（2026-10-27 03:3x）**：① **派单窗口 = 「G4 共享层桥已落码」这个事件**（不是「各域阶段 2」）⇒ 本域阶段 2 到了也**不删** ② **本域删除爆炸半径 = 2 件**（① 删桥类 ② 删 `AutoConfiguration.imports` 注册行）—— 本域**无任何测试引用该类名**（`grep` 实测：message/task 的桥类名只出现在自身与 imports）⇒ **「连带处理 javadoc」在本域为零**（类自己的 javadoc 随文件消失）③ **共享层桥对 api 侧的影响 = 零** ⇒ 实测 **10 个 api 模块的 classpath 上全部没有 MP**（⚠️ `task-api/pom.xml` 的那一处命中是注释里的「**刻意不引入**」清单 ⇒ **假阳性**）⇒ 共享层桥在 api 上下文里 = **零 bean** ＋ 悬空排序名 ⇒ 无副作用 ④ 共享层桥的 javadoc **别照抄各域的部分列举**（实测「同款」列举：message 列 **1** 个 / like 列 **4** 个 / task 列 **5** 个 ⇒ **实际 9** 个）⑤ 本域台账 `message/rpc.md:508` / `:1134`（把桥登记为「已确认的基础设施事实」）在删除后**一并订正**（**同域写权内**） | <br>🟢 **2026-10-27 第十七轮：闸门已满足 ⇒ 本域排序桥已收编（详见本文件「第十七轮 · 域侧排序桥收编到共享层」段）** ⇒ ⇒ **本条原写的「共享层 `service/common` 命中数 = 0」已过期**（那是 03:3x 时的状态；共享层那份 `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration` ＋ 其 `AutoConfiguration.imports` **第 4 行** 已在我树里）⇒ ⇒ **闸门解除** ⇒ ⇒ ✅ **本条 ⑤ 的两个行号仍准确**（`message/rpc.md:508` / `:1134`）⇒ ⇒ **那两处已按「保留原值 + 加订正注记」订正**（注记写明「类已移出本组件、承载者已换」）⇒ ⇒ ⚠️ **但收编揭出一条**跨 lane **的构建路径缺陷**（单模块构建的 fat jar 会起不来）⇒ ⇒ **本域尤其要注意：本域没有进程级 fat jar 守门 ⇒ 收编后测试全绿 ≠ 产物没问题** ⇒ ⇒ 详见该段 ②③④ |

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> ✅ **本域 §1 的 7 条 ⇒ 0 条需要新派单**（全部已修 / 落点在容器层 / 落点在他域）。
> 🆕 **本轮新发现 1 条共享层落点缺陷** ⇒ **FB-01**（与 `like` / `task` 两域同批 ⇒ 同落点、同批落码）。

### 🆕 FB-01 · A3「路径归一」在共享层缺位（**9 个 api 缺 Go 的 `path.Clean` 归一**）

| 项 | 内容 |
|---|---|
| **① 现象** | Go 侧 router 对请求路径做 `path.Clean` ⇒ 尾斜杠 / 双斜杠 / `.` / `..` 变体**命中同一路由**；RAG 侧**只有 `article-api` 有**归一器（域内类）⇒ **其余 9 个 api（含 message / like / task）缺这一层** ⇒ 变体路径两侧行为可能分岔（Go 200 vs RAG 404）。⚠️ **「RAG 返 404」目前是推断**（Spring 默认 `useTrailingSlashMatch=false`）⇒ **需真容器探针确认**（本轮未跑） |
| **② Go 侧出处** | `go-zero@v1.10.2/rest/router/patrouter.go:48`（`cleanPath := path.Clean(reqPath)`，注册侧）+ `:60`（`reqPath := path.Clean(r.URL.Path)`，匹配侧）⇒ **已实测两行真实存在**<br>⇒ 语义证据：article 域双侧对拍 `docs/DRIFT/article/api.md:849`（CASE-25 尾斜杠 `/v1/articles/` ⇒ **Go 200**）⇒ Go 侧确实有归一语义 |
| **③ RAG 侧现状** | `service/common/src/main/**` 下 **`GoStylePathNormalizingFilter` 命中数 = 0**（本轮 `find` 实测）⇒ 全仓唯一实现 = `service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStylePathNormalizingFilter.java`（**域内类**）⇒ 本域 `MessageApiApplication.java:63-66` 扫 `com.rag.common` 时**扫不到它** |
| **④ 证据强度** | **L1**（Go 源码行号 + RAG 文件缺失，二者均可复算）<br>🟢 **Go 侧已升级为 L4 活体**（2026-10-27 · G4Common 实测）：go-zero **真 server** + curl 探针 ⇒ `/v1/x` · `/v1/x/` · `/v1//x` · `/v1/x/../x` · `/v1/./x` **五种变体全部 200** ⇒ **Go 的 `path.Clean` 归一已活体坐实**<br>⚠️ **RAG 侧后果**仍待 G4 落码后的真 Tomcat 守门 ⇒ 本条整体**暂不标 L4** |
| **⑤ 建议修法（两侧都应是 Y）** | **Y = 「所有 api 的请求路径都按 Go 的 `path.Clean` 归一后再匹配」** ⇒ 归一器应落在**共享层 `service/common/http/`**（A3 轴定义的落点即 `protection/* + http/*`）⇒ **不得**继续按域各写一份（article-api 那份是历史遗留的域内副本）<br>⇒ 归一语义 = Go `path/clean.go`（`//`→`/`、`.`/`..` 消解、去尾斜杠），且必须排在**读路径的 Filter 之前**（article 现取 `HIGHEST_PRECEDENCE+2`，须在 `GoStyleTraceparentFilter(+3)` 之前）<br>⇒ 连带：共享层 `GoStyleTraceparentFilter.java:173-176` 的注释把 `GoStylePathNormalizingFilter` 当**共享层**类讨论 ⇒ 归一器进共享层后该引用自动成立；否则须把注释改成「article-api 私有、共享层没有」 |
| **⑥ 影响面** | 共享层 `service/common/http/*` ⇒ 被 **10 个 api 全依赖**；**实际缺口 9 个**（article 已有等价实现）⇒ 跨域引用域数 = **9** |
| **⑦ 是否阻塞本波** | **否**（不阻塞本波台账；阻塞 A3 结案 —— A3 在归一层落位前只能记 🟡） |

> ⚠️ **本单与 `like` 域 `D-3` / `task` 域 `C-2` 是同一条**（三域独立发现）⇒ **同批落码**（一个共享层实现 ⇒ 9 域受益）。

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
| **E66**（Redis 写不绑 deadline） | 🔴 **仍 OPEN · 本轮转立项** | — |

⇒ **G4 未越界**（按本域给的边界）：共享层**没有**为 task 写特例 · **没有**碰 favorite 的任何东西 ·
本域建议项 3 的对拍脚本落点 = `tools/difftest/**`（无 lane 拥有的共享面）⇒ **只登记，不擅自改** ✅

### 📊 **G4 第八次全量口径**（2026-10-27 · 09:57:42 → 10:18:03）· G2 **用 surefire XML 独立复核**

| 项 | 值 |
|---|---|
| 结果 | **BUILD SUCCESS / `MVN_EXIT=0`** · **52 模块** · **2461 条** · **0 失败 / 0 errors / 4 skipped** · **20:21 min** |
| 本域在 **G4 树**里的口径 | `message-api` **88** · `message-rpc` **84** · `message-common` **21** ⇒ **本域 3 模块 = 193**（全树逐项可复算：193 + task 域 204 + like 域 195 = **592**） |

⚠️ **口径隔离**：**193 = G4 树口径** ⇒ **不含**本域尚未合入的守门 ⇒ **本域自己的口径另计** ⇒ 两个数不可混用。
⚠️ **取数字只用 surefire 的 `TEST-*.xml`**（**不要**按日志 `[INFO] Building` 分段聚合 ⇒ 本轮据此把 `task-rpc` 算错成 110，真实 114）。
⇒ 完整明细与判据见 [`like/vs-common.md` §4·七](../like/vs-common.md)（本轮唯一出处）。

### 📥 **FB-04 对本域 = N/A**（`853767b` · 2026-10-27 · G2 **独立取证**，未采信转述）

| 项 | 独立取证 |
|---|---|
| **Go 侧** | `grep -rn "SpanFromContext" service/message --include=*.go`（排除 `_test.go`）= **0 命中** ⇒ 本域 Go 侧**不依赖**「业务代码取当前 span」语义 |
| **RAG 侧** | `grep -rn "Span.current()" service/message`（排除 `target/` 与测试）= **0 命中** ⇒ ⇒ **两侧都是 0** ⇒ RAG **没有凭空多出** span 用法 ⇒ **无漂移** |
| **结论** | FB-04（`onHalfClose` 里把 `otelCtx.makeCurrent()` 的 scope 包住 handler 执行期）**只影响会读「当前 span」的域** ⇒ ⇒ 本域 **N/A** ⇒ 合并后**零行为变更** · **域侧零改动** · **无待办** |
| **HTTP 侧也不涉及** | G4 回执第 ⑤ 条第 3 点说「HTTP 侧 `HttpMiddleware.java:332` **已有** `makeCurrent()` ⇒ 本次修法**只落 gRPC 侧**」⇒ ⚠️ 但**本域 api 侧 `Span.current()` = 0 命中** ⇒ ⇒ 那条 HTTP 侧能力**对本域同样无作用** ⇒ **仍是 N/A** |

### 🔴 合并后我域**必做**动作（收口回归 · §5.3e）

⚠️ **本域当前仍用「等价判据」顶着**（因为 G4 的落码**未合入本树**）⇒ 合并后必须：

1. **复跑本域 3 处 `DEADLINE_EXCEEDED` 断言**（`LikeRpcGrpcInterceptorWiringTest:178` ·
   `LikeRpcSlowThresholdGateTest` · `LikeRpcTimeoutCapTest` × 4）
   ⇒ 原因 = G4 `7488b3d` 修的「**进程级静态池**终止即永久」缺陷**本域同构**
   （`ObservabilityLifecycle` 被本域三个 rpc 组件全部 `@Import` ⇒ 每个 Spring 上下文关闭都毒化池）
   ⇒ 合并后池**自愈**（`AtomicReference` + CAS 重建）⇒ 这三处应从「**侥幸绿**」变「**真绿**」
   ⇒ ⚠️ **若仍红 ⇒ 不是新回归，是毒化未被覆盖 ⇒ 直接找 G4**<br>   🔴 **合并顺序硬约束（G1 预告 · G2 独立核实，2026-10-27）**：`3b573fb`（修「关闭路径触发 web 类加载」）**不能单独合入** ⇒ 它让 `ObservabilityLifecycle.shutdown()` **真的执行** ⇒ 若 `7488b3d`（池自愈）**未同时在位** ⇒ 池「终止即永久」⇒ 同模块**后续** Spring 上下文被毒化 ⇒ 拦截器返回 **`INTERNAL`** 而非 `DEADLINE_EXCEEDED` ⇒ **由绿转红**<br>   ⇒ ⇒ **我域实测暴露面**：message-rpc **10** 个 `@SpringBootTest` 上下文（**三域里最多**；task-rpc 4 / like-rpc 9） ⇒ ⇒ **本域三个 rpc 组件全部同构**<br>   ⇒ ⇒ ⚠️ **G4 树自身的落地顺序与 G1 给的「分批顺序」相反**（G2 实测 `git log` 倒序）：`3b573fb` 在**第 21 位（更早）**、`7488b3d` 在**第 17 位（更晚）**⇒ ⇒ **整批合并 G4 分支 ⇒ 最终态两条都在 ⇒ 约束天然满足** ⇒ ⇒ **但「只挑部分 commit」⇒ 必须带上 `7488b3d`**，否则本条会红 ⇒ ⇒ **别把红判成「域侧回归」**<br>   ⇒ ⇒ ⚠️ **另有一层「绿是因为没走到」**：修 `3b573fb` 之前，纯 gRPC 进程的 `@PreDestroy` 先抛 `NoClassDefFoundError` ⇒ `RpcInterceptor.shutdown()` **根本没机会执行** ⇒ 静态池**侥幸存活** ⇒ ⇒ **前几轮全量一直绿不是「本来就对」，是缺陷把保护罩挡住了**（G1 提出 · G2 采信）<br>   ⇒ ⇒ ⚠️ **订正本条的类名**：本条原写「3 处 `LikeRpcGrpcInterceptorWiringTest:178` · `LikeRpcSlowThresholdGateTest` · `LikeRpcTimeoutCapTest` × 4」⇒ **那是 like 域的类名，被误抄进本域** ⇒ ⇒ **message 域的对应断言在 `MessageRpcTimeoutGateTest`**（`serverSideTimeoutIsActuallyEnforced` 同义断言）⇒ ⇒ **类名须按域重新定位，引用前先核**<br>   🟡 **2026-10-27 12:5x 实测撞过一次红 · 根因已定位 · 不是域侧回归**：本轮 8 模块域级回归跑到 message-rpc 时 `MessageRpcTimeoutGateTest` 报 **`Tests run: 2, Failures: 0, Errors: 2`** ⇒ 最内层根因 = `java.net.BindException: Address already in use` on **端口 64118** ⇒ ⇒ 64118 落在 **OS 临时段 49152–65535**（**不是**本域专用段 21700–21799）⇒ ⇒ **根因 = 两个 lane 同时跑 maven ⇒ 撞端口**（`ps` 当时显示 **G1 树**在跑）⇒ ⇒ **独占重跑 message-rpc = 84 条 / 0 failures / 0 errors / 0 skipped · BUILD SUCCESS** ⇒ ⇒ **确认：不是代码缺陷、不是域侧回归、也不是「静态池毒化」那条路径**<br>   ⇒ ⇒ **教训（本域留档）**：**「起跑前查一次 ps」是不够的** ⇒ ⇒ **别的 lane 可以在你跑的过程中起 maven** ⇒ ⇒ ⇒ **必须双向错峰**：① 起跑前查真实占用者（已做）② **我也要给别人发错峰知会**（⚠️ **本轮之前我只给 G4 发过，没给 G1 发 ⇒ 这次撞车就是那个漏洞的代价**）③ 撞车后**先看最内层 `Caused by`**（端口 / 绑定 / 超时 ⇒ 环境；断言 / 状态码 ⇒ 代码）⇒ ⇒ **别看到「由绿转红」就判回归** |
2. **复核共享层的 `REJECT_COMMANDS` 在本域生效**（`GoStyleRedisClientAutoConfiguration`）
   ⇒ 本域 like-rpc / task-rpc **自带** `spring-boot-starter-data-redis` ⇒ `@ConditionalOnClass` 命中
   ⇒ 且本域**零域内副本** ⇒ **共享层那份生效** ⇒ 断连即拒单（实测 1ms）⇒ 不再撞 `rpc-timeout: 2s`
   ⇒ ⚠️ **顺带确认「遮蔽契约」**：本域**不得**新建 `RedisDisconnectedBehaviorConfig` 之类的域内副本
   （那会遮蔽共享层 ⇒ E66 等后续修正在本域**静默失效**）
3. **A3 升 ✅ 的前置**：共享层的 `GoStylePathNormalizingFilter` 合并后 ⇒ 跑**真 Tomcat 探针**复核
   ⇒ 本域 A3 才能整体升 ✅（Go 侧已 L4 活体坐实：5 种路径变体全 200）

⚠️ **E66 的硬约束（G4 已探明，落地前必须解决）**：
Boot 4 `LettuceConnectionConfiguration.createClientOptions` 的字节码顺序 =
**offset 146 先写 `timeoutOptions`（来自 `spring.data.redis.timeout`）→ offset 151 才应用 customizer**
⇒ ⇒ customizer 再调 `timeoutOptions(...)` 会**覆盖运维配置的命令超时**
⇒ ⇒ 落码前必须先解决「**保留既有命令超时**」
⇒ 🆕 **本域提供的实测数据**：命令超时**压根不挂在 `ClientOptions` 上**
（`javap` 实测 `io.lettuce.core.ClientOptions` **没有** `getCommandTimeout()`，命中数 = **0**）
⇒ ⇒ customizer 里要读回的是 **`getClientConfiguration().getCommandTimeout()`**（**不是** yml、**不是** `getTimeoutOptions()`）
⇒ ⇒ 且本域的**域侧调用点清单**已备好（like-rpc **11 处** / task-rpc **3 处** · 见本文件 §4 的 FB-05 / FB-06）

- 📌 **变异方法约束（G4 2026-10-28 告知 · E66 落码时的「assertion-direction 变异」必读）**
  - ⇒ ⇒ **A/B（机制位）在 Boot 默认装配下【恒真】** ⇒ ⇒ **变异点必须选在「装配面」（共享层 customizer 那一侧 —— 它一旦调 `timeoutOptions(...)` 就会整体替换 `enabled()` 那份 default source），【不能】选在「断言面」**。
  - ⇒ ⇒ ⚠️ **但共享层另有一条守门 ③**（`GoStyleRedisClientOptionsGateTest`「命令超时必须**未被**本层改动」）⇒ ⇒ **若变异把整个 `TimeoutOptions` 换掉 ⇒ ③ 也会红** ⇒ ⇒ ⇒ **「恰好 1 条红」的判据会被破坏**（会被误读成「两条网一起塌」）⇒ ⇒ ⇒ **必须用「只翻那一位」的最小翻转**（如让 `enabled()` 少一位），**不要**用「另一个 `TimeoutOptions` 整体替换」。
  - ⇒ ⇒ ⇒ **本条是「变异怎么翻」的硬约束，不是结论** ⇒ ⇒ **E66 未落码 ⇒ 本轮不执行**（等 Jason 拍板）。

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 本轮**无 L4**，§6 列了 3 条待跑
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **7 条全部重核，5 条过期快照已订正**
- [x] §3 的域侧修法**落码 + 守门**（阶段 2；D-1 / D-3 = `2049a08`；D-2 = 端口段治理，**7 类全绿**）
- [x] §4 的派单**登记**（本文件 §4 FB-01）＋ **实时轨已发** `session send` 给 `G4Common`（`mvs_e8063ac67c654150ab7797b8116ef0a1`）
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/DRIFT/global/**` ⇒ 合并后由**合并收口执行者**统一更新）

## 6. 未验证项（本轮明确留下的缺口，**不写成已闭环**）

1. **A3 缺本域真实请求证据**：① envelope 拒绝形状（>2048 参数 ⇒ 是否 `200 + {code:5001,msg:"internal server error"}`）② `..` 路径归一 ③ 保护层 413/503 裸响应形状 ⇒ 需真 Tomcat 探针。
2. **FB-01 的「RAG 返 404」** —— 🟢 **Go 侧已由 G4Common 活体坐实**（go-zero 真 server，5 种路径变体全 200，2026-10-27）⇒ 剩下的是 **RAG 侧**的对应证据，**正在 G4 手里**（共享层归一器 + 真 Tomcat 守门）⇒ 本域只需在收口回归时复核。
3. **A1 / A2 缺双侧活体**：Go 侧 2000ms 超时 + `codes.Acceptable` 六码判据（`go-zero@v1.10.2/zrpc/internal/codes/accept.go:9-17`）与 RAG 熔断器的对齐只做了源码对照。
4. **A9 的前提**：`GoStyleOtelAgent.java:133-137` 装配失败只记 ERROR 并继续启动 ⇒ 「响应带 `Traceparent`」不是无条件成立，需运行期确认。
5. **A8 的 Flyway 运行时形态**：`baseline-version: 0` 在**非空 schema** 下是否真的执行 V1–V5，只有真 PG 双侧起进程能定论。
6. **全局端口段表** —— 本域段 **21700–21799** 已登记在本文件 §2/A5；🟢 **2026-10-27：G4 已把三域段号汇总登记进 `docs/DRIFT/global/vs-common.md`**（落点 = G4 写权面**唯一**的那个文件 ⇒ 不新建）⇒ **like 21600–21699 / message 21700–21799 / task 21800–21899** 三段同批登记（另含 favorite **21300–21399** / 探针 **21400–21599** / favorite 脚手架 **21950–21999** / OS 临时段 **49152–65535**（**禁占** ⇒ 各域真容器测试必须自选专用段））⇒ ⇒ **本域零待办**<br>⚠️ **G4 明确未核 users(G1) 与 article/comment/hot(G3) 的段号 ⇒ 「不外推」** ⇒ 待各域台账补齐后由 G4 统一登记 ⇒ ⇒ **本域不替别的域填段号**<br>🟢 **2026-10-27（G2 独立复核 · G4 端口段表落地后）**：**表已闭合**（G4 `46f67bf` · 台账 §4.10.19）⇒ **8 段已落 + 5 段预分配 + 2 域 N/A + OS 临时段 49152–65535 禁占 ⇒ 全局无重叠**；预分配的 5 段 = favorite-rpc **22100–22199** / users **22200–22299** / follow **22300–22399** / article **22400–22499** / hot **22500–22599**<br>⇒ **本域段号实测复核通过**：`service/message/message-api/src/test/java/com/rag/message/message/api/support/RealTomcatPort.java:69` `FIRST = 21700` / `:72` `LAST = 21799`（与 G4 测量**逐条一致**；G4 测量基线 = `9ca4b2c`）⇒ ⇒ **本域真 `RANDOM_PORT` 注解 = 0**（message 下 `webEnvironment`+`RANDOM_PORT` 命中 **6 条** ⇒ **逐条全是 javadoc / 断言文本** ⇒ 不是注解）<br>⚠️ **两条口径纪律（写给下一个复核者）**：① **数「真 RANDOM_PORT」只数真注解，不数 javadoc / 字符串里的提及**（**文件数 ≠ 注解数**）⇒ 「提到」与「用着」是两件事；② 🔴 **端口段表是「协调产出」，不是 Go 行为断言** ⇒ ⇒ **它不计入 A5 的行为级证据**（A5 的 Go 锚点仍在各域 A5 行）⇒ ⇒ **别因为段表闭合就把 A5 的证据强度升级** |<br>7. 🔴 **E65（= global U15）：本域有 2 处「跨域出向 gRPC」⇒ 出向不继承请求 deadline ⇒ 漂移成立**（**2026-10-27 独立核实 · G3 知会 + Jason 拍板方向**）<br>· **本域跨域出向清单（2 处，都在 message-rpc）**：`service/message/message-rpc/src/main/java/com/rag/message/message/rpc/client/UserRpcClient.java`（**message-rpc → user-rpc**）· `…/client/AdminRpcClient.java`（**message-rpc → admin-rpc**）⇒ ⚠️ **与 `message-api/MessageRpcClient` 区分**：那是**本域内** api→rpc ⇒ **不属于 E65**<br>· **Go 侧事实（带 ctx ⇒ 继承 deadline）**：`service/message/rpc/internal/logic/helper.go:144` `svcCtx.UserRpc.GetUser(ctx, &userpb.GetUserReq{Uid: id})` · `:150` `svcCtx.AdminRpc.GetSelf(ctx, …)` · `:198` `svcCtx.AdminRpc.GetUserList(ctx, …)` ⇒ ⇒ **Go 侧出向传了 ctx** ⇒ ⇒ **继承请求 deadline**<br>· **RAG 侧现状（两处均零传）**：`UserRpcClient.java` 与 `AdminRpcClient.java` 全文 **零** `withDeadline` / `applyTo` / `DeadlineState` / `GoStyleRequestDeadline` 命中 ⇒ ⇒ **出向调用不带任何 deadline** ⇒ ⇒ **E65 在本域成立**<br>· **🔴 Jason 拍板的方向（2026-10-27，G3 转达）= 走 global 统一载体 `GoStyleRequestDeadline.applyTo(stub)` ⇒ 本轮不改** ⇒ ⇒ 共享层那半落地后**本域照做**（已进本文件「合并后我域必做」）<br>· 🔴 **2026-10-27 第十四轮订正（本域源码级独立复核 · 上一行「共享层那半落地后本域照做」**不足** ⇒ ⇒ 域侧单补 `applyTo` 仍是**无用功**（no-op）⇒ ⇒ 两端必须**同一次提交**一起改）**：<br>　① **出向载体在 rpc 进程里恒为 null**：`service/common/.../observability/GoStyleRequestDeadline.java:110-120` 的 `applyTo` 读 `DEADLINE_NANOS`（ThreadLocal），**null 即原样返回 stub** ⇒ 而全仓 `GoStyleRequestDeadline.begin(` 的**主代码调用点只有一处** = `HttpMiddleware.java:324`（`:356` 对应 `clear()`）⇒ ⇒ **gRPC 服务端进程无 HTTP 入口 ⇒ `DEADLINE_NANOS` 恒 null ⇒ rpc→rpc 出向 `applyTo` 恒 no-op**<br>　　⇒ 🔴 **共享层自带书面证明（最硬的证据）**：`service/common/.../spring/ObservabilityLifecycle.java:57-58` 明写「纯 gRPC 进程里 `HttpMiddleware` **从不加载**（没有 servlet filter 要注册）」· `:69-70`「rpc 侧的 HTTP 池**从未被创建过**（`HTTP_EXECUTOR` 是 `HttpMiddleware` 的 static 字段，而该类在 rpc 进程里从不加载）」⇒ ⇒ **不是我的推断，是共享层源码写死的**<br>　　⇒ 🟢 **本域自带的 A/B 对照（同一个 `applyTo`，两种命运）**：`service/message/message-api/src/main/java/com/rag/message/message/api/client/MessageRpcClient.java:112` 的 `applyTo` 落在 **HTTP 进程** ⇒ `HttpMiddleware.begin()` 已写 ThreadLocal ⇒ **有效**；而本条要修的 `message-rpc` 侧两处若照抄同一个 `applyTo` ⇒ 落在 **gRPC 进程** ⇒ **恒 no-op** ⇒ ⇒ **「照着 message-api 抄一行」这条修法在本域跨域出向上是错的**<br>　② **Go 侧是单载体 ⇒ 两环天然相连**：服务端 `zrpc/internal/serverinterceptors/timeoutinterceptor.go` 的 `ctx, cancel := context.WithTimeout(ctx, t)`（**叠加不覆盖** ⇒ 入站 ctx 自带的客户端 deadline 保留 ⇒ 有效 = min(客户端 deadline, now+t)）→ 同一个 ctx 交给 handler → 出向 `zrpc/internal/clientinterceptors/timeoutinterceptor.go` 的 `context.WithTimeout(ctx, t)`（= min(父剩余, 配置 2s)；由 `zrpc/internal/client.go:115-117` `if c.middlewares.Timeout { … TimeoutInterceptor(timeout) }` 装上）⇒ ⇒ **Go 侧只有 `ctx` 一个载体 ⇒ 服务端绑过的 deadline 出向自动继承**<br>　③ **RAG 侧不是「缺计算」，是「缺载体交接」**：`service/common/.../observability/RpcInterceptor.java:293` 的 `long effectiveTimeoutMs = Math.min(serverBudgetMs, clientDeadlineRemainingMs())`（`:291` 服务端预算 · `:707-719` `clientDeadlineRemainingMs()` 取 `io.grpc.Context.current().getDeadline()`）⇒ ⇒ **这一行的语义已与 Go 的 `WithTimeout(ctx, t)` 叠加等价**（= min(入站剩余, 服务端配置)）⇒ ⇒ 但它被绑到 `DeadlineState`（`:478` / `:570` `DeadlineState.bindToCurrent(deadlineEpochMs)`，**毫秒 epoch**）⇒ ⇒ **与 `applyTo` 读的那个载体（**纳秒** ThreadLocal）不是同一个** ⇒ ⇒ **值算对了、传不过去**<br>　④ ⇒ ⇒ **收口判据**：本条要闭合，必须**共享层在 gRPC 服务端把 `effectiveTimeoutMs`（或等价值）写进 `applyTo` 读的那个载体**，取值 = **min(入站剩余, 服务端配置)** ⇒ ⇒ 落点 = `service/common/**` ⇒ ⇒ **属 G4 写权面 ⇒ 本域不可单方面落码** ⇒ ⇒ 域侧那半（`applyTo` 调用点）**只有在共享层那半落地后才有意义** ⇒ ⇒ **两半必须同一次提交**（否则留下「域侧已改、实际仍 no-op」的半截 ⇒ 又是一个假绿）<br>　⑤ ⚠️ **与下一条被否决备选的关系**：那条否决「用 `DeadlineState.remainingMillis()` 构造出向 stub deadline」的三条理由**依然成立且更硬** —— 因为 `DeadlineState` 与 `applyTo` 的载体**本来就不通**（这正是 ① 的根因）⇒ ⇒ **正确修法不是让域侧去读另一个载体，而是把共享层那一个载体在服务端也接上**（对齐 Go 的单载体）<br>　⑥ ⚠️ **与 global 台账的关系**：G4 已在 `docs/DRIFT/global/vs-common.md:46` 就 E65 附注做过一次订正（「`applyTo` **不是** no-op；真正的不继承发生在 rpc 进程：预算 ThreadLocal 只由 `HttpMiddleware` 写入 ⇒ rpc 进程无 HTTP 入口 ⇒ `DEADLINE_NANOS` 恒 null」）⇒ ⇒ **与本条结论一致**（本条是**按本域源码 + Go 源码独立复核得出**，**不是转述 G4**）⇒ ⇒ **不重复登记、不重复派单**<br>· **被否决的备选**（**本域不得选边**）：用 `DeadlineState.remainingMillis()` 直接构造出向 stub deadline ⇒ ① 引入**第二种载体** ② 偏离 Go 的**单载体**语义 ③ 要改 global 门禁期望值（`outgoing_carrier` 当前钉 `absent`）<br>· ⚠️ **G3 订正的「users 域既有先例」不成立**：那两处 `DeadlineState.isExpired()` 是**本地超时检查**、不是出向 gRPC deadline（且行号已漂移到 `AdminDomainService.java:178`）⇒ ⇒ **不得据它论证「仓内已有第二种载体的出向用法」** ⇒ **我域台账零处引用它 ⇒ 无需订正** |

> 🟡 **共享面观察（本 lane 不写，登记待整合者处理）**：`docs/DRIFT/_cross/domain-common-lanes.md:85` 把 A9 定义为「span 属性（`audit.*`）」，但 `audit.*` 是 **admin/users 域专用**（`AdminSpans.java:91-93`）；like 域 Go 侧实际用 `query.*` / `cache.*` / `like.*` / `messaging.*` ⇒ **轴定义建议改为「按域取 Go 侧实际前缀」**，否则 like / message / comment 等域会被误判为 N/A。<br>8. 🟢 **U+FFFD 全清单口径订正（G4 独立实测 · 两份清单交叉核对通过 · 2026-10-27 12:4x）** ⇒ **全仓 = 18 个文件 / 47 处**（G4 于 12:16 的字节级实测）⇒ ⇒ **我上轮给的清单已 stale**（其中「G4 写权面的 2 条」已由 G4 在 `0c72969` 修掉 ⇒ 不要重复登记）⇒ ⇒ **两份独立清单数字吻合** ⇒ ⇒ **「无人拥有」实为 4 条**：`tools/errmsg-probe/README.md`(3) · `tools/sql-statement-probe/favorite/compare.py`(3) · `docs/Pitfalls/message/rpc.md`(2) · `docs/Pitfalls/users/user/rpc.md`(1)<br>⇒ ⇒ **其中 `docs/Pitfalls/message/rpc.md` 的 2 处内容属我域（message 域）** ⇒ ⇒ **我认领其内容归属**，但 **`docs/Pitfalls/**` 不在我 G2 写权白名单** ⇒ ⇒ **我域不能写** ⇒ ⇒ **已请整合者/该文件 owner 处理**（G4 已把 4 条统一登记在其台账 4.10.7 item 6）⇒ ⇒ **这是「路径归属 ≠ 内容归属」的典型**：内容是我的、路径不是 ⇒ ⇒ **别把「不在我写权面」读成「不是我的问题」**<br>7. 🔴 **我的一条「转移单」是假转移（G4 独立核实并拦截 · G2 采信 · 2026-10-27 12:4x）** ⇒ **自我订正**：我曾据「Go 侧该域有 `rest.WithJwt` ⇒ 适用」就推断「article-api / comment-api / favorite-api 也欠修 009 式的『JWT 验签落在 HTTP 预算内』」，并准备请 G4 转发 ⇒ ⇒ **G4 已独立核实：那三域的 RAG 侧早已修好**（`GoStyleJwtAuthFilter` 的 `@Order(HIGHEST_PRECEDENCE + 6)` ⇒ 验签在预算起点**之前**）⇒ ⇒ **「Go 侧适用」≠「RAG 侧还欠修」** ⇒ ⇒ **我的推断把『适用』悄悄换成了『欠修』** ⇒ ⇒ **该转移单作废**（**我未发出**，已在 G3 队列侧核过 ⇒ 无需撤回；G4 的预防性拦截件已入 G3 队列）<br>⇒ ⇒ **教训（本域留档）**：**真源侧有该行为 ⇒ 只说明「适用」，不说明「RAG 侧还没修」** ⇒ ⇒ 判定「谁欠修」**必须同时读 RAG 侧的当前状态**（`@Order` / 现有实现）⇒ ⇒ **「适用」与「欠修」是两个独立判断，合并成一个就是假转移** ⇒ ⇒ **转派前必须先读被转域的现状**<br>⇒ ⇒ ⚠️ **另一处不精确（G4 指出）**：那三域**不是**都在 3s 预算上 —— article-api 是 **70s**、comment-api / favorite-api 是 3s ⇒ ⇒ **槽位相同（+6 在预算起点 +7 之前）≠ 预算时长相同** ⇒ ⇒ 引用时**槽位与时长要分两轴写**<br>⇒ ⇒ 🟡 **仍属我域的残留（G4 指出 · 由整合者处理）**：那三个拦截器都保留「Filter attribute 缺失时就地验签」的 inline fallback ⇒ ⇒ 那条路径把验签放回预算内 ⇒ **我域无同类实现**（我域 task-api 无鉴权 ⇒ `DRIFT-USERAPI-009②` 对我域**不适用**）⇒ ⇒ **不属我域待办**<br>🟢 **G4 两条断言 · G2 独立复核通过（2026-10-27 12:5x）**：① **我域三域 production 代码对 Redis 客户端零覆盖** —— `ClientOptions` / `LettuceClientOptions` / `disconnectedBehavior` / `DisconnectedBehavior` 在 `service/{like,message,task}` 的 **`src/main` 全部零命中**（排除 `src/test`）⇒ ⇒ **共享层的 `REJECT_COMMANDS` 与breaker 键改法在我域无任何生产侧覆盖 ⇒ 零影响**（G4 的 [1] 登记成立）② **三处 mode 值**：`like-api/application.yml:125` = `envelope` · `message-api/application.yml:152` = `envelope` · `task-api/application.yml:225` = `"off"`（**带引号**）⇒ ⇒ 逐条与共享层语义表一致（`GoStyleFormLimitMode` like=ENVELOPE / message=ENVELOPE / task=OFF）⇒ ⇒ **task 的引号必须保留**（未加引号的 `off` ≠ 省略该属性；favorite 才是「省略整块 ⇒ OFF」） |

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
2. 🔴 **排掉 1 份孤儿报告**：`service/like/like-rpc/target/surefire-reports/TEST-com.rag.like.like.rpc.LikeRpcServerSpanCurrentProbeTest.xml`（mtime 早于起跑标记）⇒ **源文件已不在树里**（`find service/like/like-rpc/src/test -name '*Probe*.java'` **零命中** ⇒ 该探针类**从未进过 git** ⇒ 是「建在模块里的本地探针、源删了报告没删」）⇒ ⇒ **like-rpc 报告 20 份 / 测试类 19 个 ⇒ 差 1 = 这份孤儿** ⇒ ⇒ **不会回退任何测试来消它** ⇒ ⇒ **教训（复用 G1 同款）**：**探针要么放 `/tmp`，要么连报告一起删** ⇒ ⇒ **「两口径交叉验」遇到差值先查「哪个口径读的是残留」**
3. ⚠️ **独占跑**：起跑前 `pgrep -f 'plexus.classworlds.launcher.Launcher'` = **0**（G1 的 6 模块回归 12:52:27 起、约 13:0x 收工 ⇒ 我 13:00:44 才起）⇒ ⇒ **零端口撞车** ⇒ ⇒ **本轮 637 全绿不含任何环境噪声**（对比：上一轮同口径因撞车红 2 条）

⇒ ⇒ **本轮数字的用途边界**：这是**我域当前代码态**的回归网 ⇒ ⇒ **不是**「合并 G4 共享层后」的结论 ⇒ ⇒ **合并后必须重跑**（§「合并后我域必做」第 1/2/4 条）⇒ ⇒ **本轮数字不升级任何轴的证据强度**（跑绿 ≠ 对）

⇒ ⇒ 🟡 **本域相关的两条残留**：① **E65 两处跨域出向**（`UserRpcClient` / `AdminRpcClient`）⇒ 🔴 **第十四轮订正**：旧表述「共享层 `GoStyleRequestDeadline.applyTo(stub)` 落地后**本域照做**」**不足** —— 在 **gRPC 进程**里 `DEADLINE_NANOS` 恒 null ⇒ `applyTo` **恒 no-op** ⇒ ⇒ **域侧单补 = 无用功** ⇒ ⇒ **必须与共享层那半（服务端把 `effectiveTimeoutMs` 写进出向载体）同一次提交**（落点 = `service/common/**` ⇒ G4 写权面）⇒ ⇒ 详见本文件「E65」条；⇒ **合并后本域这两处必须重跑**；② **U+FFFD 3 处真损坏在主树、**不在本树**（本树字节级 0 命中）⇒ ⇒ **合并时这 3 个文件一律取 G2 树版本**（`LikeOutboxEventEntity.java` · `MessageRpcProductionAddressTest.java` · `GoStyleGrpcClientBreakerE2ETest.java`）⇒ ⇒ **合并后请再跑一次字节级扫描**（不是 grep）

### 🔴 第四轮（2026-10-27 13:2x~13:4x）· 合并 G4 共享层 + **补上 message-rpc 缺失的组件级链守门** ⇒ 本域 A2 轴 **L1 ⇒ L2**

#### ① 合并（G4 要求的协议 · 整支合并、**不 cherry-pick**）

- `git merge align/g4-service-common` ⇒ **merge commit `37816ca`** ⇒ **零冲突**（合并前已用 `git merge-tree --write-tree` 预演：`main←G2` / `main←G4` / **`G4←G2`** 三者 EXIT=0）
- ⇒ ⇒ **天然满足 §5.3e 的合并顺序硬约束**：`3b573fb` 与 `7488b3d` **同批到位**（`git merge-base --is-ancestor` 逐个核对 = YES）⇒ ⇒ **不会**出现「池终止即永久 ⇒ 由绿转红」
- ⇒ 带入共享层 API `compose(props)` / `layerNames(chain)`（G4 `182fc32`）⇒ ⇒ **域侧守门升级条件满足**
- ⚠️ **口径纪律（合并后必须改口径）**：合并后**本分支相对主干的 diff 里含 `service/common/**`** ⇒ 但那些 commit 的**作者是 G4** ⇒ ⇒ **本 lane 没有写共享层**（写权仍归 G4）⇒ ⇒ 「共享面零触碰」改述为「**本 lane 未新增、未修改任何共享层 commit**」

#### ② 🟢 **补域内缺口**：本组件此前**完全没有**链守门 ⇒ 新增 `MessageProtectionChainWiringTest`（**新类 · 5 条**）

⇒ ⇒ **为什么这是缺口而不是「锦上添花」**：AGENTS.md §3.5 要求**组件级** —— 熔断 / 降载在 Go 侧是 **zrpc 框架级**能力，每个 rpc server 都自带；本仓把链的定义点放在共享模块，但**每个组件仍要自己把链交给 ServerBuilder** ⇒ 漏掉任何个组件，那个组件就**既没有 recover、也没有熔断、也没有降载**，而**不报任何错**（§3.3 的经典形态）
⇒ ⇒ **合并前的域内事实**：兄弟组件 `task-rpc` / `like-rpc` **都有**链守门，而**本组件没有** ⇒ ⇒ **本域 A2 轴此前只有 L1（源码级）证据、零运行期证据** ⇒ ⇒ 现补齐：

| # | 断言 | 证什么 |
|---|---|---|
| ① | `@Import` 必须含 `GoStyleProtectionConfiguration` | properties Bean 真的存在（否则上下文起不来） |
| ② | 层名列表 == `[recover, breaker, shedding]` **且** `layerNames(chain)` == `compose(props).layerNames()` | 一次钉住「两层都在 + 层序」，**并**抓住「配置说三层、实际只装两层」 |
| ③ | 关掉降载 ⇒ 层名列表变成 `[recover, breaker]` | 降载层是**条件装的**（Go `go-zero@v1.10.2/zrpc/server.go:160-163`），不是恒在 |
| ④ | 熔断层**真的包住**业务 call（包装层归属） | 证「不是只在名单上」 |
| ⑤ | 降载层**真的包住**业务 call（包装层归属） | 同上，且它是**最内层**（Go 链序 Breaker → Shedding → handler） |

⇒ ⇒ **② 与 ④⑤ 互补而非替代**（层名证「有哪些层 + 层序」；包装归属证「真的包住了调用」）⇒ ⇒ **五条合起来才是「A2 链序对齐」的完整判据**（任何单条都不充分）
⇒ ⇒ **客户端熔断那一面**（`GoStyleGrpcClientBreakerInterceptor` · `MessageApiApplication.java:63-66` 扫 `com.rag.common` 注册）**已由阶段 2 的 `GoStyleGrpcClientBreakerE2ETest` 覆盖** ⇒ ⇒ **本轴两侧（服务端链 + 客户端熔断）均有运行期证据**
⇒ ⇒ **本域 A2 轴：L1（源码）⇒ L2（运行期 · 组件级守门 + 变异验证）**

#### ③ 变异验证（判据非假绿）

- 变异方式 = **断言方向变异**（期望层名改成 `["recover","breaker"]`）⇒ ⚠️ **被测机制在共享层、不在本 lane 写权** ⇒ ⇒ 只能做方向变异（证明**断言读的是运行期真值**）⇒ ⇒ **这是「回归网」证据，不是「已证伪该机制」**
- 结果：**本域红且只红 1 条**，红点**精确**在 `chainLayerNamesMatchComposedChain`（`MessageProtectionChainWiringTest.java:102`）⇒ ⇒ **其余 4 条阴性对照仍绿** ⇒ ⇒ **非假绿**
- ⇒ 还原后复跑：**5 条绿** · 0 failures / 0 errors / 0 skipped · BUILD SUCCESS
- ⚠️ **还原脚本的判据坑（我自己踩到）**：变异后文件里有 **2 处** `containsExactly("recover", "breaker")` ⇒ ⇒ 全局字面量替换的还原脚本会**断言失败并中止**（文件停在变异态）⇒ ⇒ **还原必须按位置精确替换**

#### ④ 数字订正

- **message-rpc**：`mvn test` 口径 **84 ⇒ 89**（新守门类 **5 条**）⇒ ⚠️ 本文件里另有更早的历史数字（`49` / `68` 等）⇒ 那些是**旧轮次**的记述、**不是现状** ⇒ 引用时以本行与本节为准
- ⇒ ⚠️ **守门覆盖缺口**：`check-ledger-selfcheck.py` 层 3（数字断言）**默认只查 task 域** ⇒ ⇒ **like / message 域的测试数 stale 它不会报** ⇒ ⇒ **这两域的数字要自己盯**（本轮是我自己发现并订正的）

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



**⑥ 🟢 口径澄清（第十七轮收口实测）**
- 本域**没有任何 `*IT` 类**（实测：`find service/message/**/src/test -name "*IT.java"` = 0）⇒ ⇒ 本域在 `mvn test` 与 `mvn verify` 两个口径下**数字相同**。
- 但我域 9 模块里**唯一的 `*IT`** 是 `task-rpc` 的 `TaskRpcFatJarArtifactGateTest`（**5 条**，第十七轮 +1）⇒ ⇒ 它**只在 `verify` 阶段跑** ⇒ ⇒ **同一 9 模块的 `test` + `verify` 口径 = 995 + 5 = 1000** ⇒ ⇒ **口径必须写清**（本域自身数字不受影响，但全域合计会差 5）。

**⑦ 🔴 残留登记（两条负载敏感型时序 flake，**都不在本域代码里**，但会卡住我域的回归链）**
- **① `task-rpc` 的 `TaskRedisDisconnectFastRejectGateTest` ③ B 组**：一次全量跑实测红（`actual 0L / expected ≥ 2000L`）⇒ **隔离复跑 3 次全绿** ⇒ 判为瞬态；机制 = B 组依赖「客户端还没处理到 FIN」的**竞态窗口** ⇒ ⇒ **该对照组断言不是确定性的** ⇒ ⇒ **只登记、不改断言**。
- **② 共享层 `RpcInterceptorClientDeadlineGateTest`**：实测红（`handler 必须真的被调用过`）⇒ **隔离复跑仍红** ⇒ 机制 = 客户端 deadline 只有 **100 ms** ⇒ ⇒ **共享层的「预算过紧」守门** ⇒ ⇒ 落点 `service/common/**` ⇒ ⇒ **非我写权面 ⇒ 只报 G4**（本轮已报）。
- ⇒ ⇒ **代价**：`service/common` 在我域每次 `-am` 回归的 reactor 里 ⇒ ⇒ **共享层一红 ⇒ 我域 `clean test` 整条链断在我域之前** ⇒ ⇒ 「我域全绿」的前置条件包含「共享层在当前机器负载下能绿」。

**⑧ 🟢 本域状态**：第十七轮落码 = **删 2 件**（域侧桥类 ＋ 整份单行 `imports`）＋ 台账订正；**零新增守门**（本域仍无产物层守门 ⇒ 见 ③ 的待办，**本轮未做** ⇒ ⇒ 「共享层那份真生效」在本域仍**只有测试 classpath 意义上的证据**）。
---

#### 🆕 第六轮（2026-10-27 13:46–13:58）—— **go-zero 库内路径统一加前缀**（G1 提出的同一件事，我域自查后落码）

> **触发**：G1 在它域把裸库内路径统一成 `go-zero@v1.10.2/<path>`（142 处 / 11 份文件）⇒ ⇒ **我域自查发现同类**（本域 **88 处裸库内路径 / 3 份（api.md 40 ＋ rpc.md 42 ＋ vs-common.md 6） 份台账**）⇒ ⇒ **本轮按同一约定落码**（纯表达层、零行为影响）。

**① 改写结果（实测 · `git` 前后对比，不是推算）**
- 改写前：本域台账 **裸库内路径 88 处** ＋ **已带前缀 7 处**
- 改写后：**裸库内路径 = 0** ＋ **已带前缀 96 处** ⇒ ⇒ **新增前缀 89 处**（88 处裸路径 ＋ 1 处新增全路径引用）
- **纯替换**：**路径改写那部分** = **140 insertions = 140 deletions** ⇒ ⇒ 零行增删（整笔提交还含本段台账文字 ⇒ 那是新增行、不属改写）
- ⚠️ **计数口径**：**整文件正则**（不是逐行 `grep`）⇒ 跨行的路径引用只有整文件口径数得到

**② 判据（实测，不是转述 G1 的说法）**
- 🔴 **订正 G1 的一处表述**：G1 写「真源仓顶层 = **只有 `service/`**」⇒ ⇒ **实测不准确** —— 真源顶层实测 **16 项**（`service` · `api` · `proto` · `doc` · `grafana` · `prometheus` · `go.mod` · `docker-compose.yaml` …）⇒ ⇒ 但 **G1 的核心判据成立**：真源仓里 **`core` / `rest` / `zrpc` / `gateway` / `mcp` 五个首段全部零命中**（`find -maxdepth 2 -type d` 实测）⇒ ⇒ **「首段 ∈ 五者 ⇒ 必是库内路径」成立** ⇒ ⇒ **我按【判据】办，不按【表述】办**
- ✅ **版本锁定**：真源 `go.mod:17` = `github.com/zeromicro/go-zero v1.10.2` ⇒ ⇒ 前缀写 `v1.10.2` 不是自选
- ✅ **不是只按约定改，是逐条验证过**：go-zero v1.10.2 的**模块缓存就在本机**（`~/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2`）⇒ ⇒ 本域 88 条**逐条核「文件存在 + 行号在范围内」** ⇒ ⇒ **87 条通过**、**1 条越界** ⇒ ⇒ 越界那条已订正（见 ④）
- ✅ **内容抽验（3 条，逐行读过，不是只看行号在范围内）**：`go-zero@v1.10.2/zrpc/server.go:144-167` = `setupUnaryInterceptors`（recover:149 / breaker:158 / shedding:160-163 ⇒ **与我台账的链序结论逐字吻合**）· `go-zero@v1.10.2/rest/httpx/util.go:15` = `maxFormParamCount = 2048`（⇒ 与我域「两侧都不可达」的判据吻合）· `go-zero@v1.10.2/core/breaker/googlebreaker.go:52-79` = `accept()`

**③ `internal/` 那一类：逐处判归属 ⇒ 10 处全部是真源仓组件内路径 ⇒ 一处不加前缀**
- `internal/` 是**唯一两个仓都有的顶层目录** ⇒ 裸写无法机械区分 ⇒ ⇒ 本域按三问判：① `<libdir>/internal/<rest>` 在模块缓存里存在吗？② 否则真源仓里有同后缀文件吗？③ 否则未决 ⇒ ⇒ 实测 **库内 0 / 真源 10 / 未决 0** ⇒ ⇒ **全部是真源仓**（如 `internal/logic/helper.go` · `internal/errcode/grpc.go` · `internal/model/message.go`）⇒ ⇒ **语境已含组件，不构成误读 ⇒ 不动**
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

**⑥ 🔴 遗留一整类：裸 basename 引用（本域 231 处）⇒ ⇒ 【不机械改】+ 登记**
- 本域裸 basename（形如 `errmsg.go:117-132`）共 **231 处**，分三类（实测）：**库内唯一**（basename 在 go-zero 里唯一）· **库内同名多候选**（如 `config.go` 在库里有 **14** 个同名 · `unmarshaler.go` **3** 个 · `types.go` **2** 个）· **真源仓 / 标准库**（如 `errmsg.go` · `sendchatmessagelogic.go` ⇒ 真源仓里同名）
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

#### 🆕 第七轮（2026-10-27 14:1x）—— **§5.3e「合并后必做」清单的销号**（本域 （message 域））

**① 销号：项①（3 处 `DEADLINE_EXCEEDED` 断言）与 项③（Redis 池守门 ⑤⑥）**
- ⇒ ⇒ **这两项已被 13:31–13:39 的合并后 9 模块回归覆盖并通过**（`MVN_EXIT=0` · **970 条 / 0 failures / 0 errors / 0 skipped**）⇒ ⇒ 覆盖本域的：**task-rpc 119**（含 `TaskGrpcInterceptorWiringTest` 的 `DEADLINE_EXCEEDED` 断言 ＋ 池守门 ⑤⑥ 6 条）· **like-rpc 115**（含 `LikeRpcGrpcInterceptorWiringTest` ＋ 池守门 4 条）· **message-rpc 89**（含 `MessageRpcTimeoutGateTest`）
- ⇒ ⇒ ⚠️ **口径**：那是**我域树 × (三域 ＋ 共享层)** 的 **lane 级**回归 ⇒ ⇒ **不等于 C7 波末收口回归**（C7 的三条硬前提仍未满足：四个 lane tip 全部未入主干）⇒ ⇒ **这两项在本域侧销号，波次级仍待 C7**

**② 销号：项⑧（合并后全仓字节级 U+FFFD 复扫）**
- **本轮全仓复扫（源码 + target/ 全扫）**：**4103 份文件** ⇒ **18 份含 U+FFFD / 60 字节**；**剔除 `target/` 构建产物** ⇒ **15 份 / 38 字节**
- ⇒ ⇒ **差额已逐条解释**（不是「漏了」）：
  - **3 份 / 22 字节** = `target/` 里的 **3 个 jar**（like-rpc 8 ＋ task-api 6 ＋ task-rpc 8）⇒ ⇒ 它们的构建时间是 **12:00 / 12:48**，**早于**我域那 3 个源文件的修复 ⇒ ⇒ **是旧构建产物，不是当前源码缺陷**（那 3 个源文件现测 **FFFD = 0**）
  - **2 字节** = `docs/Pitfalls/message/rpc.md:911` ＋ `:921` ⇒ ⇒ **故意保留的故障样本**（`:911` 是真实报错文本，其中变量名后跟了一个 **U+FFFD 替换符**（本行**不复制**那个字符，只描述）；`:921` 是解释「那个乱码替换符」）⇒ ⇒ **不是真损坏**
- ⇒ ⇒ **本域真损坏 = 0**（我域写权面 **340 文件字节级 = 0 命中**）
- ⚠️ **跨树口径纪律**：G1 全表给的「20 份 / 51 字节」是**G1 树**的数字 ⇒ ⇒ **跨树不可直接比**（同一批文件在四棵树里各不相同 ⇒ 「份数相同 ≠ 同一批文件」）⇒ ⇒ **本树口径 = 15 份 / 38 字节（源级）**

**③ 仍未销号（与本轮无关，等 G4）**
- **项②**（共享层 `REJECT_COMMANDS` 断连活体对拍）：🟢 **本域 N/A** —— **message 域零 Redis**（`service/message/*` 的 pom 里 `spring-boot-starter-data-redis` **零命中**；本域只在`message-rpc` 用 gRPC 调下游）⇒ ⇒ **没有 Redis 连接 ⇒ 谈不上「断连行为」** ⇒ ⇒ **不是「没做」，是「不适用」** ⇒ ⇒ 同一机制在 **like 域 ＋ task 域**各做了一遍活体 A/B（第八轮）⇒ ⇒ 本域只需在**合并后**确认「共享层那份 customizer 在本域上下文里在不在」（本域无 Redis ⇒ 连装配都不会发生 ⇒ ⇒ 预期零影响）
- **项④**（E66 落码后守门 ① 仍绿）：**E66 未落码** ⇒ ⇒ N/A（等落码）
- **项⑤**（A3 升 ✅ 的真 Tomcat 探针）：✅ **已做**（第十轮 · 真 Tomcat ＋ A/B 对照组 ＋ 本域独立变异验证）
- **两个共享门禁**（`check-tomcat-limits.py` · `check-wave-coupling.py`）仍红 ⇒ ⇒ **`tools/**` 非本 lane 写权 ⇒ 仍等 G4**（本轮新增证据：那三个门禁依据文件在**我树 vs G4 最新 tip `c814689`** 之间**逐字节相同** ⇒ ⇒ G4 树里它们绿**是因为不含我域改动**，不是「已修」）

---

## 4·九、🔵 **第九轮（2026-10-27 17:1x）· G1 来的「波次级通用判据」⇒ 本域侧逐条处置**（G2 全部**独立取证**，不采信转述）

### ① ⏸ **命令超时判据联动 —— 本域 N/A**（零 Redis ⇒ 无 Redis 判据 ⇒ 无联动面）

- **G1 的判据**：`getCommandTimeout()`（值）＋ `getTimeout()==3000` ＋ `isApplyConnectionTimeout()`（机制），**不是** `isTimeoutCommands()`（source 恒 `DefaultTimeoutSource` = −1 ⇒ 恒真 ⇒ 非有效判据）
- ✅ **同意 G1 的口径**（在 **like / task** 两域成立）—— 那两域的判据链已落地（机制位①＋机制位②＋「source 恒 −1」＋时长出口，缺一不可）
- ⏸ **本域 N/A 的依据**：`service/message/*` 的 pom 里 `spring-boot-starter-data-redis` **零命中** ⇒ ⇒ **本域零 Redis** ⇒ ⇒ 既无「命令超时」判据 ⇒ 也无「共享层 customizer 覆盖域内超时」的联动面 ⇒ ⇒ **不是「没做」，是「不适用」**
- 📌 **同一机制的受益域证据在 like / task 两域**（第八轮活体 A/B + 变异验证）⇒ ⇒ **本域不重复做、不外推**

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
- `MessageApiPathNormalizationRealTomcatTest`（**3 条**）· `MessageApiPathNormalizationControlTest`（**2 条**）⇒ ⇒ **A/B 两类**
- ⚠️ **不外推**：like / message 两域**各做了一遍自己的 A/B** ⇒ ⇒ **三域各 5 条 = 15 条**（不是「一个域跑一遍、三域通用」）

### ② 判据（三段，每段都落到消费点）
① **共享层那份在、且没被域内同名 Bean 遮蔽** ⇒ `context.containsBean("goStylePathNormalizingFilter")` ＋ 按名取到的**类型**必须是 `com.rag.common.http.GoStylePathNormalizingFilter` ＋ `@Order` = `HIGHEST_PRECEDENCE + 2`
② **真 Tomcat ＋ 真请求** ⇒ `DEFINED_PORT` ＋ 本域专用端口段 `21700-21799`（`RealTomcatPort`）＋ **`HttpURLConnection`**
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
| `service/message/message-api` | **103** | 0 | 0 | 0 | 15 |
| `service/message/message-rpc` | **89** | 0 | 0 | 0 | 20 |
| `service/message/message-common` | **21** | 0 | 0 | 0 | 2 |
| `service/like/like-api` | 94 | 0 | 0 | 0 | 11 |
| `service/like/like-rpc` | 120 | 0 | 0 | 0 | 21 |
| `service/like/like-common` | 18 | 0 | 0 | 0 | 2 |
| `service/task/task-api` | 102 | 0 | 0 | 0 | 9 |
| `service/task/task-rpc` | 121 | 0 | 0 | 0 | 15 |
| **合计** | **995** | **0** | **0** | **0** | **157** |

⇒ ⇒ **域侧 8 模块 = 668**（与第十一轮**逐条相同**）＋ **共享层 324 ⇒ 327**（+3 = G4 的新机器门禁 `GoStyleRedisClientShadowingGateTest` 3 条）⇒ ⇒ **9 模块总计 995** ⇒ ⇒ ✅ **第十一轮那句「995 是推算，不是实测」** ⇒ ⇒ **本轮实测证实该推算值** ⇒ ⇒ **995 从此是实测值**（**上一张 992 的表原值保留不改写**）
⇒ ⇒ **口径（比上一轮更严，三条）**：① **命令带 `-am` + `clean`** ⇒ `mvn -o -B -DargLine=-Xmx2g -am -pl <9 模块> clean test` ⇒ ⇒ **reactor 实跑 23 个模块**（`-am` 自动带入 `users-common` / `user` / `user-proto` / `message` / `message-proto` / `like` / `like-proto` / `task` / `task-proto`）⇒ ⇒ ⚠️ **这是必须的**：`service/message/message-api/pom.xml:55` 依赖 **`users-common`**（**不在我 9 模块表里**）⇒ ⇒ **不带 `-am` ⇒ 它从 `~/.m2` 解析旧 jar ⇒ 测的不是本树代码**（`mvn -pl` 不带 `-am` 的经典陷阱）② **权威口径 = 按本域模块的 surefire 目录逐文件求和**（`TEST-*.xml` 的 `testsuite` 属性）⇒ **逐模块 == maven 汇总行**（**9/9 逐字相等**）③ **`clean` 不可省** ⇒ 删过源/资源的模块会留 stale `target/classes` ⇒ 门禁与计数都会读到残留
⇒ ⇒ **157 份报告全部本轮生成**（`clean` 已清空旧目录 ⇒ **0 stale 报告**）
⇒ ⇒ ⚠️ **两个共享门禁仍红（非本轮引入 · 非我写权面）**：`check-tomcat-limits.py`（期望表把 task-api 写死 `raw`，而权威语义表 `GoStyleFormLimitMode.java:237` = `Mode.OFF` ⇒ **期望表与权威表自相矛盾**）· `check-wave-coupling.py`（**9 项漂移** ⇒ 快照须在四 lane 全合后由整合者重生成）⇒ ⇒ **两条都不是我域能修的**

### 🔴 第十七轮 · 域侧排序桥收编到共享层 ＋ **它揭出来的构建路径缺陷（单模块构建的 fat jar 起不来）**（2026-10-27）

**① 闸门已满足 ⇒ 收编落码（删 2 件）**
- 共享层那份**已在我的树里**：`service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ 注册行 `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` **第 4 行** ⇒ ⇒ 本文件 D-4 原写的「共享层 `service/common` 命中数 = 0」**已过期**（那是 03:3x 时的状态）⇒ ⇒ **闸门解除**。
- 删掉：`service/message/message-rpc/src/main/java/com/rag/message/message/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ `service/message/message-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（**该文件只含排序桥这一行 ⇒ 整份随类一起删**）。
- ⚠️ **合并清单条目（写给整合者）**：我域这 3 个 rpc 模块的 `imports` 都是**「只含一行」**形态 ⇒ ⇒ **整份文件随类一起删**；而 G3 的 `article-rpc` 是**「2 行」**形态 ⇒ ⇒ **只删第 1 行、文件保留** ⇒ ⇒ **两种形态不能一刀切**。
- 🟢 **删前我域自己做的核验（不采信转述）**：① 两份桥**都不注册任何 bean**（类体为空）⇒ ⇒ **无 bean 名遮蔽**；且声明的是**同向**排序边 ⇒ ⇒ 排序图里**多一个同向节点、无环、功能等价** ⇒ ⇒ **删除 = 去重，不是修 bug** ⇒ ⇒ **A1/A9 等轴的证据强度不因本次删除而降级**（⚠️ global 台账 `:1159` 用「被遮蔽」措辞 ⇒ 我域实测语义是「**同向并存、功能等价**」⇒ 措辞差异已报 G4）② **主代码零 import**（`grep "import .*MybatisPlusOrderingBridgeAutoConfiguration"` = **0 命中**）③ **测试里只有 javadoc / 断言文本** ⇒ ⇒ **断言的是「效果」（`SqlSessionFactory` 存在）而不是「类在不在」**。

**② 🔴🔴 收编当场揭出来的真缺陷：单模块构建的 fat jar 起不来（跨 lane 的地雷）—— 本域是「首撞」的那个域的同门兄弟**
- **现象（首撞在 like 域）**：收编后跑 like-rpc ⇒ `LikeRpcRuntimeBootGateTest.realFatJarProcessBootsAndListens` **红** ⇒ 真 fat jar 的 gRPC 端口没监听 ⇒ 进程日志尾部 = `Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required` ⇒ ⇒ **不是 flake、不是端口撞车**（判据 = 那条 MP 异常）。
- **根因（三层，逐层实测）**：① **域内桥以前在 `BOOT-INF/classes` 里** ⇒ ⇒ **不管内嵌的 `common` jar 多旧，排序边都成立** ⇒ ⇒ **域内副本长期掩盖着下面这条**；② **构建 fat jar 的命令不带 `-am`** ⇒ ⇒ `service/common` 不在 reactor 里 ⇒ ⇒ 从 **`~/.m2`** 取 jar ⇒ ⇒ 实测 `~/.m2/.../common-0.0.1-SNAPSHOT.jar`（**12:00 的旧包**）的 `AutoConfiguration.imports` 里**排序桥行数 = 0** ⇒ ⇒ fat jar 里嵌的就是它 ⇒ ⇒ **排序桥不在 fat jar 里**；③ ⇒ ⇒ MP 的 `@AutoConfigureAfter`（**Boot 3 包名**）在 Boot 4 下**悬空** ⇒ ⇒ MP 自动配置在 DataSource 之前求值 ⇒ ⇒ `@ConditionalOnSingleCandidate(DataSource)` 失败 ⇒ ⇒ **不产 `SqlSessionFactory`** ⇒ ⇒ **进程启动即挂**。
- **⇒⇒⇒ 通用结论**：**「域内桥一删 ⇒ 单模块构建的 fat jar 起不来」** ⇒ ⇒ ⇒ **凡有进程级 fat jar 守门的域，收编都会红**（G3 的 comment-rpc / article-rpc / favorite-rpc 都带 `*RuntimeBootGateTest`）⇒ ⇒ ⇒ **已回信点名**。

**③ ⚠️⚠️ 本域尤其要注意：本域「收编后测试全绿」**毫无产品意义**（本域**没有**进程级 fat jar 守门，也没有产物层 fat jar 守门）**
- ⇒ ⇒ **本域收编后所有测试都会绿**（测试 classpath 用的是 reactor 的 `service/common/target/classes`，带桥）⇒ ⇒ ⇒ **但那是「测试 classpath」意义上的绿，不是「产物」意义上的绿**
- ⇒ ⇒ **判据式** **⇒ 本域的「绿」≠ 产物没问题** ⇒ ⇒ ⇒⇒ **「测试 classpath」用的是 reactor 的 `service/common/target/classes`（带桥）⇒ ⇒ 「产物」用的是 fat jar 内嵌的 jar（可能不带桥）⇒ ⇒ 两个路径必须分别验**
- ⇒ ⇒ **⇒ 本域的「产物层」目前是无人守的** ⇒ ⇒ ⇒⇒ **「产物层无人守」这条本身就是本域的一条待办**
- ⇒ ⇒ **本域现状** ⇒ ⇒ **建议（域侧可自修 · 本轮未做）**：本域补一条**同款产物层守门**（断言 fat jar 内嵌的 common jar 带共享层排序桥）⇒ ⇒ 与 `task-rpc` / `like-rpc` 保持同款 ⇒ ⇒ **补上之后，本轮的「共享层那份真生效」才有产物级证据**
- ⇒ ⇒ **本域的唯一护栏 = 统一构建口径**（构建 fat jar 一律带 `-am` / 或统一走根 reactor）⇒ ⇒ **该口径不在我写权面 ⇒ 已报 G4 + G1 + G3 + 整合者**。

**④ 修法与验证（在 like 域做，因为只有它有进程级守门能证）**
- `LikeRpcArtifactClasspathGateTest.buildFatJar()` 的嵌套命令 **加 `-am`** ⇒ ⇒ reactor 先产出当轮的 `service/common/target/common-*.jar` ⇒ ⇒ fat jar 内嵌的就是它（**实测：内嵌 jar 的 `imports` 第 4 行 = 共享层排序桥**）⇒ ⇒ **真 fat jar 随即能起**。
- 🆕 **加一条产物层守门** `fatJarCarriesSharedLayerOrderingBridge`（like 域）＋ **task 域同款** `TaskRpcFatJarArtifactGateTest.fatJarMustCarrySharedLayerOrderingBridge` ⇒ ⇒ **把这条潜伏地雷变成可捕获的失败** ⇒ ⇒ **期望值用独立字面量**（共享层那个全限定名，**不从被测代码取常量**）⇒ ⇒ 共享层改包名时如实转红。
- **验证（可复算）**：修后 `mvn -o -B -DargLine=-Xmx2g -am -pl service/like/like-rpc test -Dtest='LikeRpcArtifactClasspathGateTest,LikeRpcRuntimeBootGateTest,LikeRpcSpringContextWiringTest'` ⇒ ⇒ **13 条 / 0f 0e 0s · BUILD SUCCESS** ⇒ ⇒ **变异验证**：去掉 `-am` ＋ 删旧 fat jar 强制重建 ⇒ ⇒ **3 条里红 1 条**，**红在目标断言** `fatJarCarriesSharedLayerOrderingBridge:147` ⇒ ⇒ **恰好 1 条红**、另 2 条仍绿 ⇒ ⇒ **非假绿** ⇒ ⇒ 还原后 **sha256 与变异前逐字节一致**（`c3d547e7d5d8706c`）。

**⑤ 一条更深的教训**
- **「域侧副本」不只是冗余 —— 它可能在无意中充当「共享层落码前」的承重件** ⇒ ⇒ ⇒ **这正是本次缺陷的根因**
- ⇒ ⇒ **判据式** ⇒ ⇒ **收编共享层能力前，先问「这份副本在 fat jar / 独立构建路径上是不是唯一的承重件」** ⇒ ⇒ ⇒⇒ **本次就是：域内副本在 `BOOT-INF/classes` 里 ⇒ 它才是那条构建路径上唯一的承重件 ⇒ 删掉它之后，共享层那份在「测试 classpath」（reactor 的 `classes`）里带着，但在「产物」（fat jar 内嵌的 jar）里未必带着 ⇒ ⇒ 两个路径必须分别验**

---

### 🟢 第二十一轮 · Testcontainers 宿主口（**层 2**）落码 —— **回答 G4 那个未实测项**（2026-10-27）

**① 起因（G4 §4.10.39 ② 分配段号 ＋ 明确要求「先探针、不能照抄」）**
- G4 把 `22700–22999` 划为「**Testcontainers 宿主口段**」，**按域分子段**：**message 域 = `22760–22789`**。
- 根因：Testcontainers 的**默认随机宿主口**落在 **OS 临时段 `49152–65535`**（= Docker published 段）⇒ ⇒ 与 `tools/difftest/ddl-parity.sh` 的固定口 `55560–55569` **同属一个池子** ⇒ ⇒ 全局段表把该段标为**禁占** ⇒ ⇒ **真容器测试不得继续用随机口**。
- ⚠️ G4 同一格里**明写了两层「未实测」**：① `PostgreSQLContainer` **不能直接换成** `FixedHostPortGenericContainer`（后者不带 PG 的初始化/JDBC 支持）⇒ ⇒ 正确形态 = **原容器 ＋ `withCreateContainerCmdModifier(...)` 显式绑口**；② 「**显式绑口后 `getMappedPort()` 是否返回那个固定口**」未实测 ⇒ ⇒ **域侧落地必须先写一条探针验证，不能照抄**。

**② 🔴 「两层口径」在我域的真实含义（本轮第一次把两层说清）**

| 层 | 是什么 | 载体 | 本域段 | 本轮之前的状态 |
|---|---|---|---|---|
| **层 1** | **api 侧 Tomcat 监听端口**（`@SpringBootTest(webEnvironment = DEFINED_PORT)`） | `RealTomcatPort` | message 域另有一段（见 §2/A5 行） | ✅ **早已治理**（阶段 2 · D-7 已销号） |
| **层 2** | **rpc 侧 Testcontainers 容器宿主口**（`PostgreSQLContainer` / `GenericContainer`） | 🆕 `RealContainerPort` | **`22760–22789`** | 🔴 **整个域没人管** —— **9 个容器类**全部用随机口 ⇒ ⇒ **都落在 OS 临时段** |

⇒ ⇒ **本轮补的是层 2 的缺口**（不是重复治理层 1）⇒ ⇒ **两层都要守**：层 1 已有 `bindsToDedicatedPortSegment`，层 2 本轮新增。

**③ 落码（9 个容器类 ＋ 3 份支撑类 ＋ 6 个守门类 · **零生产代码改动**）**

| 项 | 内容 |
|---|---|
| **被绑的容器类** | message 域 **9 个**：PG **9** 个 ＋ Redis **0** 个 ⇒ 全部钉在本域段内的**独占**固定宿主口 |
| **支撑类** | `service/message-rpc/src/test/java/com/rag/message/message/rpc/support/RealContainerPort.java` ⇒ 段边界常量 ＋ `withFixedHostPort(container, hostPort, containerPort)` ＋ **端口越段直接抛异常**（拒绝落码） |
| **机制层守门**（真容器） | `messagePostgresFixedHostPortGateTest`（3 条）⇒ 钉「**映射口 == 固定口**」＋「固定口真在监听」＋「固定口上确实是 PG（`SELECT 1` 成功）」 |
| **覆盖层守门**（源扫 · 无容器 · 秒级） | `messageContainerFixedHostPortSegmentGateTest`（1 条）⇒ ① 凡声明容器的类**必须**有 `FIXED_HOST_PORT` ② **必须**在本域段内 ③ **全域不重号** ④ 容器数 **≠ 0**（防空过） |
| **端口分配方式** | **逐类写死**（不是轮询）⇒ surefire **每个 fork 一个独立 JVM** ⇒ ⇒ 静态计数器在每个 fork 里都从头开始 ⇒ ⇒ 两个并发 fork 会拿到同一端口 ⇒ ⇒ 撞车 |

**④ 🔴 直接回答 G4 的未实测项（**实测，不是推断**）**

> G4 §4.10.39 ② 的原问句：「**显式绑口后 `getMappedPort()` 是否返回那个固定口**」

### **实测结论：会。**

- `messagePostgresFixedHostPortGateTest`（**真容器**）实测：① `getMappedPort(5432)` **== 22769**（就是那个固定口，**不是**随机口、**不是** 0）② 那个固定宿主口**真在监听**（从宿主机 TCP 连得上）③ 那个口上**确实是 PG**（真 JDBC 连接 ＋ `SELECT 1` 成功）。
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

⇒ ⇒ 三模块 `clean test`（带 `-am`）⇒ **BUILD SUCCESS · 7:41 min** ⇒ ⇒ 本域 `message-rpc` = **93**（上轮 89 ⇒ **+4** = 机制层 3 条 ＋ 覆盖层 1 条）⇒ ⇒ 共享层 `service/common` = **330（未变）** ⇒ ⇒ ⚠️ **口径**：本轮只跑了 3 个 rpc 模块 ＋ 它们的依赖（`-am`）⇒ ⇒ **9 模块口径尚未重测** ⇒ ⇒ **本域之外的模块条数一律沿用上一轮实测值，不做推算**。

**⑦ ⚠️ 口径提醒（写给下一个复核者）**
- **数「真容器类」要数 `new PostgreSQLContainer<>` / `new GenericContainer<>` 的**声明**，**不要**把 import / javadoc / 泛型里的类型名算进去（与「数真 `RANDOM_PORT` 注解、不数提及」同款纪律）。
- **端口守门是源扫型**：它核的是「**源码上有没有绑**」；机制层才是「**运行期绑没绑成**」⇒ ⇒ **两层不重复、缺一不可**。

**⑧ 原值保留不改写**
本轮**未改动**任何生产代码 ⇒ ⇒ 前面各轮的行为结论与条数口径**一律不变**；本轮新增的只有**测试面**（9 个容器类的绑定 ＋ 6 个守门类 ＋ 3 份支撑类）⇒ ⇒ **域侧条数的变化**见 §⑥ 的实测。

---

### ⏸️ 第二十一轮 · 附 · **C7 收口复跑（并入 G4 尖端 `a38ad75` 之后）** —— **本域模块本轮未重跑**

- 本轮 C7 义务 = 复跑 `service/common` ＋ **耦合最紧的 1 个域模块**（本 lane 选的是 `task-rpc`）⇒ ⇒ **本域模块本轮未重跑** ⇒ ⇒ **本域条数仍以第二十一轮实测为准**（`message-rpc` = **93**）⇒ ⇒ 共享层实测 **331**（上轮 330 ⇒ **+1** ⇒ G4 说的「条数不变仍 330」**被实测推翻**）。
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

### 🔴 第十八轮 · 9 模块回归**实测 998** ⇒ 🔴 **订正第十六轮的 995 基线 —— 那条基线在第十七轮就悄悄过期了**（2026-10-27）

**① 本域本轮零代码改动，但 9 模块总数变了**
- 本域（message）本轮**零代码改动**：第十八轮只动了 **task-rpc / like-rpc** 的 Redis 池 `max-wait`（⇒ 各 +1 条守门）⇒ ⇒ **message 三个模块的条数一字未变**（message-api **103** / message-rpc **89** / message-common **21**）。

**② 实测（9 模块全量 `clean test` · 带 `-am` · BUILD SUCCESS · 9:52 min · 157 份报告全部本轮生成）**

| 模块 | tests | failures | errors | skipped | 报告数 |
|---|---|---|---|---|---|
| `service/common`（**共享层**） | 327 | 0 | 0 | 0 | 62 |
| `service/message/message-api` | **103** | 0 | 0 | 0 | 15 |
| `service/message/message-rpc` | **89** | 0 | 0 | 0 | 20 |
| `service/message/message-common` | **21** | 0 | 0 | 0 | 2 |
| `service/like/like-api` | 94 | 0 | 0 | 0 | 11 |
| `service/like/like-rpc` | **122** | 0 | 0 | 0 | 21 |
| `service/like/like-common` | 18 | 0 | 0 | 0 | 2 |
| `service/task/task-api` | 102 | 0 | 0 | 0 | 9 |
| `service/task/task-rpc` | **122** | 0 | 0 | 0 | 15 |
| **合计** | **998** | **0** | **0** | **0** | **157** |

⇒ ⇒ **域侧 8 模块 = 671** ＋ 共享层 **327** ⇒ ⇒ **9 模块 `mvn test` 口径 = 998** ⇒ ⇒ **`test`+`verify` 口径 = 998 + 5（task 的产物层 IT）= 1003**。

**③ 🔴 订正：第十六轮那张「995」的表，在第十七轮就过期了**
- 第十六轮实测 = **995**（like-rpc **120** / task-rpc **121**）；第十八轮实测 = **998**（like-rpc **122** / task-rpc **122**）⇒ ⇒ **差 +3**，而第十八轮只加了 **2** 条守门 ⇒ ⇒ **多出来的 +1 来自第十七轮**。
- **根因（已实测定位）**：第十七轮新增的 `LikeRpcArtifactClasspathGateTest.fatJarCarriesSharedLayerOrderingBridge` 落在 **`test` 阶段**（类名 `*Test` ⇒ surefire 默认 includes 命中）⇒ ⇒ **like-rpc +1**（实测复核：`git show 016e95a:…/LikeRpcArtifactClasspathGateTest.java` 的 `@Test` 数 = **2** ⇒ HEAD = **3** ⇒ +1 ✔）⇒ ⇒ **第十七轮结束时真实值已是 996**，而第十七轮**只重跑了 `task-rpc`**、**沿用了第十六轮的 9 模块总数 995** ⇒ ⇒ **基线过期，且没有任何门禁会报**（`check-ledger-selfcheck.py` 第 3 层只比对**本域** task-rpc 的条数，不比对 9 模块总数 ⇒ ⇒ **总数这一格没有机器守门**）。
- ⇒ ⇒ **教训（写死）**：**「只重跑受影响模块 + 沿用上轮总数」会让总数悄悄过期** ⇒ ⇒ 跨轮改动落在 **`test` 阶段的类**上时，要么**重跑 9 模块全量**，要么把总数标成「**已过期**」⇒ ⇒ 本轮能抓出来，靠的是**逐模块 XML 求和 == maven 汇总行**的**双口径交叉验算**（单靠「我加了 2 条 ⇒ 997」的推算**推不出来**）。
- ⇒ ⇒ **原值保留不改写**：第十六轮那张 **995 / 668 的表原值保留**，以本条订正为准（**当前权威值 = 998 / 671**）。

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
- **message 域**：本域**零代码改动** ⇒ ⇒ 本次合并对我域**零行为变更**。
- 复跑实证：`MessageRpcTimeoutGateTest` **2 条 / 0f 0e 0s**（20.1s，含 `DEADLINE_EXCEEDED` 断言）· `MessageProtectionChainWiringTest` **5 条 / 0f 0e 0s**（`layerNames` / `compose` 断言已就位）· `MessageGrpcInterceptorWiringTest`（span 链路）绿。
- ⇒ ⇒ **message 三模块条数 103 / 89 / 21（逐条未变）**；共享层 327 ⇒ 330 ⇒ 9 模块 998 ⇒ **1001**。

**⑦ 原值保留不改写**
第十八轮的 **998 / 671 原值保留**，以本条订正为准（**当前权威值 = 1001 / 671**）⇒ ⇒ **域侧 8 模块的 671 在两轮之间一字未变** ⇒ ⇒ 变的只有共享层（+3）。

---

### ⏸️ 第二十轮 · Redis 池维度的双侧活体对拍 —— **本轮不涉及本域**（2026-10-27）

**① 本域为什么「不涉及」（不是「漏做」）**
- 本轮做的是 **Redis 连接池**维度的双侧活体对拍（真 Redis 容器 ＋ 真 go-redis 客户端 ⇒ 量「池耗尽时等连接的最长等待」）。
- **实测**：本域主代码（`service/message/**/src/main/java/**`）里 **Redis 引用数 = 0**（`RedisTemplate` / `StringRedisTemplate` / `RedisConnectionFactory` / `LettuceConnectionFactory` / `openPipeline` / `executePipelined` / `SessionCallback` 全部零命中）⇒ ⇒ **本域根本没有 Redis 客户端** ⇒ ⇒ **连接池这一维在本域不存在** ⇒ ⇒ 本轮**不建探针、不落码、不改任何本域代码**。
- ⇒ ⇒ **「本轮不涉及」≠「该做而没做」**：本轴对 message 域是 **N/A**，不是缺口。

**② 本域受影响面 = 零**
- 本轮两条新探针（`TaskRedisPoolExhaustionP99GateTest` / `LikeRedisPoolExhaustionP99GateTest`）**都不在本域** ⇒ ⇒ 本域生产代码与测试代码**零改动**。

**③ 计数（本轮 9 模块 `clean test` 实测 · BUILD SUCCESS · 12:01 min）**
- `service/message/message-api` **103**（未变）· `service/message/message-rpc` **89**（未变）· `service/message/message-common` **21**（未变）⇒ ⇒ **本域三模块合计 213（一字未变）**。
- 共享层 **330（未变）** ⇒ ⇒ **9 模块 = 1007**（域侧 8 模块 **677** ＋ 共享层 **330**）⇒ ⇒ **逐模块 surefire XML 求和 == maven 汇总行（9/9 逐字相等）** ✔
- ⇒ ⇒ **域侧 8 模块 671 ⇒ 677（+6）** —— 那 +6 **全部来自 like / task 两域的探针**，**本域 +0**。

**④ ⚠️ 一条「不外推」的纪律（本轮再次坐实）**
- Redis 池这条轴上，**task 域 = 真缺陷（可达）** · **like 域 = 定性降级（几乎不可达）** · **message 域 = N/A（不存在该轴）** ⇒ ⇒ **同一轴、三种定性** ⇒ ⇒ **一条轴的结论不能外推到另一个域**。
- 详细逐条见 [`docs/DRIFT/task/vs-common.md`](../task/vs-common.md) 与 [`docs/DRIFT/like/vs-common.md`](../like/vs-common.md) 的第二十轮段。

**⑤ 原值保留不改写**
第十九轮的 **1001 / 671 原值保留**，以本条为准（**当前权威值 = 1007 / 677**）⇒ ⇒ **本域三模块 103 / 89 / 21 在两轮之间一字未变**。


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

**③ 首轮 3 处的逐条定性（全部是「转述 global 缺陷登记的原文」这一类误报）**

| # | 位置 | 首轮判定 | 复核结论 |
|---|---|---|---|
| 1 | `docs/DRIFT/message/vs-common.md:366` | RANGE-OUT（`breakerhandler.go` **42 行** ⇒ `44` 越界）| 🟢 **误报** —— 该行是**转述 global 那条缺陷登记的原文**（global `_README.md:576` 登记 hot 域漂移时**必须引述缺陷原样**）⇒ ⇒ **引用属实**（global 至今仍写 `breakerhandler.go:19-44`）⇒ ⇒ 已在该行加「**原引用**」标记（G3 核对器的排除约定）⇒ ⇒ 重跑**清零** |
| 2 | `docs/DRIFT/like/vs-common.md:556` | 同上 | 🟢 同上（已加标记）|
| 3 | `docs/DRIFT/task/vs-common.md:593` | 同上 | 🟢 同上（已加标记）|

**④ ✅ 重跑结果：0 findings**
- 补齐源根 + 加「原引用」标记后重跑 ⇒ ⇒ **`checked: 672` · `skipped: 320` · `by_kind: {}` · `findings: 0`**（**含本段自身的引用** ⇒ ⇒ 追加本段后复跑仍是 0）。
- ⇒ ⇒ **本域台账的「文件:行号」引用 ⇒ 零越界、零不存在**。

**⑤ ⚠️ 两条要转出去的事（都不在我写权面）**
- **`Sea-BreakTheWaves` 必须作为源根进核对器** —— 该仓在本机于 `/Users/pr/Documents/GitHub/Sea-BreakTheWaves`（⚠️ **不在** `java&golang/` 下）⇒ ⇒ 缺这个根 ⇒ ⇒ **任何引用下游消费者仓的域都会被误判成「文件不存在」** ⇒ ⇒ 这条已转给 G3 / G4（G3 已给 G4 派单要落 `tools/ledger-refcheck/` ⇒ ⇒ **这是那个派单必须补的一项**）。
- **`docs/DRIFT/global/_README.md:576`** 那行登记用的**裸 basename `breakerhandler.go:19-44`** 是**缺陷原样引述**（合法）⇒ ⇒ 我三域的转述已加「原引用」标记 ⇒ ⇒ **不要**把它当漂移去「修」。

- **本域无「指向下游消费者仓」的引用** ⇒ ⇒ 本域首轮 **3 处**全部是「转述 global 缺陷登记的原文」这一类误报 ⇒ ⇒ 但**缺 `Sea-BreakTheWaves` 源根这件事仍要转出去**（本域现在没有依赖它的引用，**不代表别的域没有** ⇒ ⇒ like 域就有 2 处）。

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
- **域侧 677 ⇒ 689（+12）** ⇒ ⇒ **全部来自第二十一轮**新增的固定宿主口守门断言：`MessagePostgresFixedHostPortGateTest`（机制层 3 条）＋ `MessageContainerFixedHostPortSegmentGateTest`（覆盖层 1 条）= **+4** ⇒ ⇒ like 域 +4 · task 域 +4 ⇒ ⇒ **12** ⇒ ⇒ ⇒ **⇒ 相对第二十一轮的域侧实测值，本轮 = 0 变化**（第二十一轮台账已记 message-rpc = **93** / like-rpc = **129** / task-rpc = **129** ⇒ 本轮逐条对上 ✔）。
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

### 🔴 第二十五轮 · **收口回归那 3 个 error 不是「被测行为坏了」，是「被测环境起不来」** —— 根因 = `grpc.server.port=0` 撞 Docker 发布口（2026-10-28）

**① 现象（第二十四轮收口回归实测 · BUILD FAILURE · `MVN_EXIT=1`）**
`mvn -o -B -DargLine=-Xmx2g -am -pl <9 模块> clean test` ⇒ `service/message/message-rpc` 的
`MessageIsReadLambdaWiringTest` **3 条用例全 error**：
```
Failed to load ApplicationContext
  → Failed to start bean 'shadedNettyGrpcServerLifecycle'
  → java.net.BindException: Address already in use   （/0.0.0.0:49992）
```
⇒ **该 fork 里的第一个 gRPC server 监听就撞上了** ⇒ ⇒ 整批 context 起不来
⇒ ⇒ 表现像「3 条用例 error」，**实为被测环境起不来**（⚠️ 极易被误读成新回归 —— 我第一反应也差点这么读）。

**② 根因（实测链 · 非推测 · 双重取证）**
- `49992` 被 `com.docker.backend` 占着：`lsof -nP -iTCP:49992 -sTCP:LISTEN` ⇒ `com.docke … IPv6 … TCP *:49992 (LISTEN)`；
  `docker ps` ⇒ `Sea-kafka  0.0.0.0:49992->9093/tcp, [::]:49992->9093/tcp`（**Up 7 天** ⇒ 长期在池子里）。
- 所有 rpc 测试写 `grpc.server.port=0` ⇒ 监听口由 **OS 临时段**挑（macOS 实测 `net.inet.ip.portrange` = `49152–65535`）
  ⇒ ⇒ **与 Docker published 段同池**（本机 `com.docker.backend` 在该段占着 30+ 个口）。
- Netty 的 gRPC server 用**双栈 IPv6 socket**（实测 `bound=/[0:0:0:0:0:0:0:0]:53428`）⇒ 该族在 IPv6 空间选口、却在 bind 阶段与 IPv4 持有者冲突。
- ⚠️ **20 万次 `bind(0)` 压测（同一份 shaded Netty）0 失败** ⇒ ⇒ 这是**低概率环境态**，不是「每次必现」
  ⇒ ⇒ **收口口径必须可复跑** ⇒ ⇒ 不能靠「重跑一次算过」。

**③ 修法（已落码 · `e9c3946` · 24 个代码文件）**
新增各域 `support/GrpcTestPort`（message / like / task 各 1 份 · 各 96 行）：
`ServerSocket(0)` 取候选 → **显式再绑一次做确认**（绑不上 = 此刻被占 ⇒ 换下一个）⇒ 返回**此刻真绑得上**的端口；
21 个 rpc 测试类注入 `registry.add("grpc.server.port", GrpcTestPort::freePort)`。
- 📌 `@DynamicPropertySource` 优先级高于 `@SpringBootTest(properties)` ⇒ ⇒ 内联的 `grpc.server.port=0` **保留作回退**。
- 📌 形态**照抄仓里既有的** `LikeRpcRuntimeBootGateTest#freePort()`（同款 `ServerSocket(0)` 探测）⇒ ⇒ 不是新发明。
- 📌 **不用固定口**：G4 的 52 模块波末全量与各域收口回归**会并发**跑同一批 rpc 模块 ⇒ 固定口 ⇒ 两棵树互撞
  ⇒ ⇒ 本修法**动态探测 · 不新增端口段 · 不动 `22700–22999` 段表**（与 G4 正在裁定的段表口径**无冲突**）。

**④ 证据**
- `MessageIsReadLambdaWiringTest` ⇒ `Tests run: 3, Failures: 0, Errors: 0`（修复前 = 3 errors）✔
- 整轮 `Address already in use` 命中数 = **0** ✔

**⑤ 隐患面（波级 · 已通报 G1 / G3 / G4）**

| 口径 | 数 |
|---|---|
| 全仓 rpc 域写 `grpc.server.port=0` 的**真实测试类** | **67** |
| 其中我域 3 个 rpc（**已修**） | 21（like 7 · message 9 · task 5）|
| ⇒ **仍待修**（7 个域）| **46**（article 8 · comment 2 · favorite 8 · follow 3 · hot 5 · admin 11 · user 9）|

⚠️ **我第一版统计说「70」是错的**（**原值保留，不改写**）：多算的 3 个是**我这轮新增的 `GrpcTestPort` helper 文件**
—— 它们的 **javadoc 里写了 `"grpc.server.port=0"` 这句解释文字** ⇒ 被「按文件里出现过该字符串」的口径计入
⇒ ⇒ **正确口径 = 只算非注释行 ＋ 只算测试类（`*Test.java` / `*Tests.java` / `*IT.java`）** ⇒ ⇒ **67**。
⇒ 📌 **可复跑产物**：`/tmp/g2-port-hazard-count.json`（逐模块明细）。

**⑥ 同类文件、不同风险（复算时最容易混进去）**
- `*RuntimeBootGateTest`（article / comment / like 各 1 个）**不是** `=0` 形态 ⇒ 是 `--grpc.server.port=` ＋ **自己探测的口** ⇒ 本来就不在隐患面内。
- favorite 的 3 个类是 `registry.add("grpc.server.port", () -> env("PROBE_RAG_PORT"))` ⇒ **环境变量驱动** ⇒ 也不是 `=0` 形态。

**⑦ 共享层同款隐患 ⇒ 属 G4 写权（我域只登记 ＋ 通报，零落码）**
`service/common/src/test/java` 里有 **6 个**类用 `tomcat.setPort(0)`（实测复核：`GoStyleTraceparentFilterTest` ·
`GoStyleTraceparentFilterTraceSourceGateTest` · `GoStyleClientDisconnectProbeGateTest` ·
`GoStyleClientDisconnectProbeBudgetGateTest` · `GoStyleTimeoutResponseRaceGateTest` · `HttpClientDisconnectRealTomcatTest`）
⇒ 同样落在 OS 临时段 ⇒ **同样会撞 Docker 发布口** ⇒ ⇒ 已于第二十四轮通报 G4（请其落码）。
⇒ ⚠️ **口径提醒**：另有 **29 个** api 侧测试类用 `RANDOM_PORT`（Tomcat 侧等价形态 = `r.add("server.port", 探测口)`）⇒ 属同一族隐患。

**⑧ 🔴 r28 那次失败 =【自伤】，不是缺陷、不是跨 lane 撞车**（留档 · 教训）
r28（`/tmp/g2-r28-regression.log`）在 `MessageRpcTimeColumnParityTest` 上报
`Bind for 0.0.0.0:22768 failed: port is already allocated` ⇒ ⇒ **BUILD FAILURE · `MVN_EXIT=1`**。

- **根因**：**我自己 kill 掉的一个 stale run 泄漏了 Testcontainers 容器** ⇒ 该容器占住了
  **我域 message 段的固定宿主口 22768** ⇒ ⇒ r28 起跑时绑不上。
- **证据链（为什么断定是「自伤」而不是「跨 lane 撞车」）**：
  ① 泄漏容器 `festive_nobel`（`postgres:16-alpine`，`0.0.0.0:22768->5432`）的 `Created` =
     **09:42:21** ⇒ ⇒ **比 r28 自己起跑（09:42:23）还早 2 秒** ⇒ ⇒ **不可能是 r28 自己建的**；
 ② 09:42:21 那时正在跑的是**我 kill 掉的 stale `rerun.sh`**（它的 maven 09:40:20 起跑 ⇒ +2 min 到建容器 ⇒ 时序吻合）；
 ③ **全局段表零重叠**（message `22760–22789` · like `22790–22819` · task `22820–22849` ·
     article `22850–22879` · comment `22880–22909` · favorite `22910–22939` · hot `22940–22969`）
     ⇒ ⇒ 同段另有 G3 的 `agitated_lighterman`（**22858** = G3 `RealContainerHostPort.TRANSACTION_WIRING`）
     ⇒ ⇒ **跨 lane 的那个我没动**（只删了 22768 那个我自己的）；
 ④ `22768` 在**我域 message 段**内 ⇒ ⇒ 按段表归属，它**只可能是我域的**容器。
- **处置**：只 `docker rm -f festive_nobel`（**我自己的孤儿**）⇒ ⇒ **未碰任何长期容器**
  （`Sea-*` / `ddl-*` / `difftest5k-*` / `g2-pool-redis` 全部保持原样）。
- **教训（可复用到任何 lane）**：**kill 一个 maven 会泄漏它的 Testcontainers 容器**
  （Ryuk 随 JVM 一起死）⇒ ⇒ **下一个 run 在同域会撞固定宿主口** ⇒ ⇒
  **表现像「被测行为坏了」，实为「上一轮被我 kill 的 run 的残骸挡住了本轮」** ⇒ ⇒
  ⚠️ 与 ① 的 `BindException` 是**同一种误读形态**（环境起不来 ≠ 被测行为坏）。
- **⇒ 已加的机械防护**：r29 的跑批脚本加**两道起跑前体检** ——
  ① 我域三段（`22760–22849`）内**若还有容器 ⇒ 直接判失败、不硬跑**；
  ② 等待窗口结束后**再查一次**（防止等待期间别的 run 又漏容器）。
  ⇒ 📌 这道体检属**波级通用**形态 ⇒ ⇒ **已通报 G4 / G1 / G3，建议各 lane 的跑批脚本都加**。
- ⚠️ **写权说明**：「kill maven ⇒ 泄漏容器 ⇒ 下一轮撞固定宿主口」这条**踩坑**按规矩该进
  `docs/Pitfalls/**` ⇒ ⇒ 但**该目录不在 G2 写权面** ⇒ ⇒ **本 lane 只登记 + 通报，不越权写**。

### 🟢 第二十五轮 · **波末收口回归实测：9 模块 = 1030（域侧 697 ＋ 共享层 333）· 全绿**（2026-10-28 10:04）

**① 回归命令与口径（与第二十三轮同款，**只换了日志**）**
```
mvn -o -B -DargLine=-Xmx2g -am -pl service/common,service/message/message-api,service/message/message-rpc,service/message/message-common,service/like/like-api,service/like/like-rpc,service/like/like-common,service/task/task-api,service/task/task-rpc clean test
```
- **必须是 `clean test`（不是 `verify`）**、**必须带 `-am`**（理由同第二十三轮，不重复论证）。
- 日志：`/tmp/g2-r29-regression.log` · 跑批脚本：`/tmp/g2-r29-run.sh`（含**三道起跑前体检**，见 ⑧）。
- **跑在哪个 HEAD 上**：`b5910dc`（工作树干净）⇒ ⇒ **本轮数只对这个 commit 成立**。

**② 实测（`BUILD SUCCESS` · `MVN_EXIT=0` · 10:37 min · 09:53:24 → 10:04:03）**

| 模块 | 条数 | F | E | S | 相对第二十三轮（1022） |
|---|---|---|---|---|---|
| `service/common`（共享层） | **333** | 0 | 0 | 0 | 333 ⇒ **0** |
| `service/message/message-api` | **103** | 0 | 0 | 0 | 103 ⇒ 0 |
| `service/message/message-rpc` | **96** | 0 | 0 | 0 | 93 ⇒ **+3** |
| `service/message/message-common` | **21** | 0 | 0 | 0 | 21 ⇒ 0 |
| `service/like/like-api` | **94** | 0 | 0 | 0 | 94 ⇒ 0 |
| `service/like/like-rpc` | **129** | 0 | 0 | 0 | 129 ⇒ **0** |
| `service/like/like-common` | **18** | 0 | 0 | 0 | 18 ⇒ 0 |
| `service/task/task-api` | **102** | 0 | 0 | 0 | 102 ⇒ 0 |
| `service/task/task-rpc` | **134** | 0 | 0 | 0 | 129 ⇒ **+5** |
| **域侧 8 模块小计** | **697** | 0 | 0 | 0 | 689 ⇒ **+8** |
| **9 模块合计** | **1030** | **0** | **0** | **0** | 1022 ⇒ **+8** |

**③ 交叉核对（两条独立路径 · 差值逐字归因）**
- **XML 侧**：9 个目标模块 `target/surefire-reports/TEST-*.xml` 的 `<testsuite>` 属性**逐模块求和** ⇒ ⇒ **1030**（F/E/S = 0/0/0）。
- **maven 侧**：模块级 `Results:` 汇总行合计 ⇒ ⇒ **1069**
  ⇒ ⇒ **差 39 = `service/users/users-common`**（`-am` 带出的**非目标**模块，**不属收口口径**）
  ⇒ ⇒ **1030 + 39 = 1069 ⇒ 逐字相等** ✔
- ⚠️ 收口口径**只算 9 个目标模块** —— reactor 实测 **23** 个模块 ⇒ ⇒ 拿 reactor 全量当收口数字会得 **1069**，不是 1030。
- ⚠️ maven 的 `Tests run:` 有**两种行**（模块级汇总行 / 每类用例行 `-- in <class>`）⇒ ⇒ 过滤必须是 `-- in `（**双横线**）
  ⇒ ⇒ 本轮排除 **172** 条每类用例行，取 **10** 条模块级汇总行。
- ✅ **含 IT / 不含 IT 两口径都是 1030** ⇒ ⇒ **我域已无任何 `*IT` 类** ⇒ ⇒ `test` 与 `verify` 两口径**重合** ✔
  （全仓 `TEST-*IT.xml` 实测 = **0 个**）。

**④ 增量归因（+8 的每一处都有出处，逐条可核）**
- **message-rpc 93 ⇒ 96（+3）** ⇒ ⇒ **第二十四轮**新增的产物层守门 `MessageRpcArtifactClasspathGateTest` 的 **3 条**断言
  ⇒ ⇒ **与本轮端口修法无关**（端口修法**不改条数**，只把原来 error 的那 3 条变成真跑起来的绿）。
- **task-rpc 129 ⇒ 134（+5）** ⇒ ⇒ `TaskRpcFatJarArtifactGateTest` 由 `*IT` **改名为 `*Test`**（第二十四轮）
  ⇒ ⇒ 它从「只在 `verify` 阶段跑、不计入 `clean test`」变成「**进 `clean test` 口径**」⇒ ⇒ **正好 +5**。
- **like-rpc 129 ⇒ 129（0）** ⇒ ⇒ `isStale` 把**共享层**算进新鲜度输入集（第二十四轮）⇒ ⇒ **是行为改动，不是断言增量** ⇒ ⇒ 条数**应当不变**，实测**也确不变** ✔
- ⇒ ⇒ **域侧 +8 全部来自第二十四轮的落码，端口修法贡献 0 条** ⇒ ⇒ ⇒ **⇒ 相对第二十三轮：+8 全部有出处**。

**⑤ 原值保留不改写**
第二十三轮的 **1022 / 689 / 333**、第二十轮 **1007 / 677 / 330**、第二十一轮 **689**、第二十二轮 **331 / 332** ——
**全部原样保留**；本段的 **1030 / 697 / 333** 为**当前权威值**。

**⑥ 端口修法的效果（与 ⑧ 配套）**
- 本轮 **0 次** `Address already in use` / `0 次** `port is already allocated`
  ⇒ ⇒ `GrpcTestPort` 的**探测式**分配（21 个类注入）**在收口口径里真生效** ✔
- ⚠️ r28 那轮的失败**不是**端口修法的锅 ⇒ 是**自伤**（我 kill 掉的 stale run 泄漏的容器挡住了固定宿主口 22768）⇒ ⇒ 详见 ⑧。

**⑦ 判绿红只看两项**
本轮日志里同样满屏 `nacos register FAILED` / `kafka NetworkClient` / `otel exporter 装配失败` / `logger-test-fatal` 等 **ERROR 级日志行**
⇒ ⇒ **那些不是失败** ⇒ ⇒ **唯一判据 = surefire 的 `Failures`/`Errors`/`Skipped`（0/0/0）＋ `MVN_EXIT=0` ＋ `BUILD SUCCESS`** ✔

### 🔴 第二十五轮附二 · **同一族隐患的【第二形态】：`ServerBuilder.forPort(0)`（程序内起 server · OS 盲选口）** —— r30 实测复现

**① 现象（r30 实测 · BUILD FAILURE · `MVN_EXIT=1`）**
r30（= M-B 还原后的复跑）在 `message-rpc` 段报：
`MessageRpcSlowThresholdGateTest.slowCallMustBeMarkedSlow` → 1 error
→ `io.grpc.StatusRuntimeException: INTERNAL: http2 exception`
→ `Caused by: Http2Exception: First received frame was not SETTINGS. Hex dump for first 5 bytes: 2d45525220`

**② 🔴 破案关键：那 5 个字节是 ASCII「`-ERR `」⇒ ⇒ 客户端连到了【不是 gRPC server 的东西】**
- `2d 45 52 52 20` = `-` `E` `R` `R` `空格` ⇒ ⇒ **`-ERR ` 是 Redis 的错误前缀** ⇒ ⇒
  客户端连上的是一个 **Redis**（⇒ 拿到的是「协议对不上」⇒ ⇒ http2 首帧不是 SETTINGS）。
- 那个 Redis 是谁？⇒ **`Sea-redis` 发布 `0.0.0.0:49380->6379`**（`docker ps` 实测，Up 7 天）。
- 而 macOS 的临时段是 **`49152–65535`** ⇒ ⇒ **49380 就在临时段里** ⇒ ⇒ 与 Docker published 段**同池**（同 §① 的池）。

**③ 机制（为什么「bind 没报错」却「连错了服务」）**
- 该测试的 `warmUp()` 用的是 **`ServerBuilder.forPort(0)`** ⇒ ⇒ **OS 盲选口** ⇒ ⇒ 这次选到了 **49380**。
- Netty 的 gRPC server 在 **IPv6 空间**绑上了这个口（§① 已实测这族用双栈 IPv6 socket），
  而 Docker 代理占着**同一个口的 IPv4 那一半** ⇒ ⇒ **两个 socket 可以共存** ⇒ ⇒ **bind 不报错**。
- 客户端按 `dns:///127.0.0.1:<port>` 走 **IPv4** ⇒ ⇒ 命中 **Docker 代理** ⇒ ⇒ 收到 Redis 的 `-ERR `。
- ⇒ ⇒ ⚠️ **与 §① 的 `BindException` 是同一族，但形态不同**：
  `grpc.server.port=0` 那次是 **bind 直接失败**（连不上）；
  这次是 **bind 成功但连到了隔壁**（协议错）⇒ ⇒ **更隐蔽**。

**④ ⇒ ⇒ 为什么 §① 的修法没挡住它**
`GrpcTestPort` 只覆盖了 **Spring 上下文那个 `grpc.server.port`**（21 个类的 `@DynamicPropertySource` 注入），
**没覆盖「程序内用 `ServerBuilder.forPort(0)` 起的 server」** ⇒ ⇒ 这就是 r30 把它逼出来的原因。

**⑤ 修法（已落码 · 我域 9 处 / 6 个类）**
`forPort(0)` ⇒ `forPort(GrpcTestPort.freePort())`（每个站点都带【为什么不能盲选】的注释留痕）。
- 🔴 **为什么探测式能挡住而盲选挡不住**：探测用的是
  `new ServerSocket().bind(new InetSocketAddress(port))` ⇒ **绑 wildcard（0.0.0.0）**
  ⇒ ⇒ Docker 代理占着 wildcard ⇒ ⇒ **EADDRINUSE** ⇒ ⇒ 探测**拒绝**该口、换下一个
  ⇒ ⇒ 而 Netty 那个 server 只绑了 IPv6 ⇒ ⇒ 盲选就穿过去了。
- 我域落码清单：`MessageRpcTimeoutGateTest`（1）· `MessageRpcSlowThresholdGateTest`（2）·
  `LikeRpcGrpcInterceptorWiringTest`（2）· `LikeRpcSlowThresholdGateTest`（2）·
  `TaskRpcSlowThresholdGateTest`（1）· `TaskGrpcInterceptorWiringTest`（1）= **9 处**。
- ✅ 修完后代码里 **0 处** `ServerBuilder.forPort(0)`（剩下 9 个 grep 命中全是**注释里引用的反面写法**）。

**⑥ 波级面（全仓实测 · 只算真实代码、不含注释）**

| 归属 | 模块 | 处数 |
|---|---|---|
| **G2（我域）** | message / like / task 三 rpc | **9 ⇒ 已修** |
| **G4（共享层）** | `service/common` | **5** ⇒ **属 G4 写权** |
| **G3** | article 3 · comment 4 · hot 4 | **11** |
| **G1** | follow 2 · admin-rpc 3 · user-rpc 3 · admin-api 1 | **9** |
| **合计（修后）** | | **25** |

⇒ 📌 共享层那 5 处（`RpcInterceptorTimeoutDescriptionGateTest` · `RpcInterceptorClientDeadlineGateTest`×2 ·
`RpcServerSpanCurrentGateTest` · `FakeOtlpCollector`）**属 G4 写权** ⇒ ⇒ **本 lane 只通报、不落码**。

**⑦ ⇒ ⇒ 三条形态归到一张表（同一族 · 同一个池）**

| 形态 | 写法 | 撞口时的表现 | 我域处置 |
|---|---|---|---|
| ① Spring 上下文 | `grpc.server.port=0` | `BindException: Address already in use` ⇒ **context 起不来** | ✅ 已修（21 个类） |
| ② 程序内 server | `ServerBuilder.forPort(0)` | **`http2 exception` / 首帧不是 SETTINGS** ⇒ **协议错** | ✅ 已修（9 处） |
| ③ Testcontainers 宿主口 | 固定端口（`22760–22849` 段） | `port is already allocated` | ✅ 段表**零重叠**（各域不重号） |

⚠️ **三者的误读形态都是同一句**：「**看起来像被测行为坏了，实为被测环境撞口/撞了隔壁服务**」。

**⑧ ⚠️ 与 §⑧（r28 自伤）的关系**
r28 的失败 = **自伤**（kill maven ⇒ 泄漏容器 ⇒ 占住 22768）；
r30 的失败 = **本节这一形态**（OS 盲选口 ⇒ 选到 Sea-redis 的 49380）。
⇒ ⇒ **两个是不同的失败，别混记** ⇒ ⇒ 二者都已在台账留档。

### 🟢 第二十五轮附三 · **修完 `forPort(0)` 后的复跑：9 模块 = 1030（第二次独立实测 · 与 r29 逐字相等）**（2026-10-28 10:39）

**① 实测**
`mvn -o -B -DargLine=-Xmx2g -am -pl <9 个目标模块> clean test`
⇒ **`BUILD SUCCESS` · `MVN_EXIT=0` · 9:09 min · 10:30:18 → 10:39:29** · 日志 `/tmp/g2-r31-regression.log`
⇒ **0 次** `Address already in use` / **0 次** `port is already allocated` / **0 次** `http2 exception` ✔

**② 逐模块（与 r29 逐条相同）**
common **333** · message-api **103** · message-rpc **96** · message-common **21** ·
like-api **94** · like-rpc **129** · like-common **18** · task-api **102** · task-rpc **134**
⇒ ⇒ **域侧 697 · 9 模块合计 1030 · F/E/S 全 0**

**③ 交叉核对（同 r29）**
XML 侧 9 个目标模块求和 = **1030**；maven 侧模块级汇总行 = **1069**（排除 172 条每类用例行，取 10 条模块级汇总行）
⇒ ⇒ **差 39 = `service/users/users-common`** ⇒ ⇒ **1030 + 39 = 1069 逐字相等** ✔

**④ ⇒ ⇒ 这个数现在是【可复跑】的（两次独立实测）**
| 轮次 | 时刻 | HEAD | 结果 |
|---|---|---|---|
| r29 | 09:53:24 → 10:04:03 | `b5910dc` | **1030** · 全绿 |
| r31 | 10:30:18 → 10:39:29 | `forPort(0)` 修好后的树 | **1030** · 全绿 |

⇒ ⇒ **两次独立 `clean test`、逐模块逐条相同** ⇒ ⇒ 这个数**不是一次侥幸** ✔
⇒ ⇒ 且 **两轮的端口修法/改动都没有动过任何断言** ⇒ ⇒ **条数不变是【预期且实测】的** ✔

**⑤ 三轮失败的性质对照（留档 · 三个不同的东西，别混记）**

| 轮次 | 失败现象 | 根因 | 性质 | 处置 |
|---|---|---|---|---|
| **r28** | `port is already allocated`（22768） | **自伤**：kill maven ⇒ 泄漏容器 ⇒ 占住固定宿主口 | 环境 + 自伤 | 删自己的孤儿容器 + 加起跑前体检 |
| **r30** | `http2 exception` / 首帧非 SETTINGS | **隐患第二形态**：`forPort(0)` OS 盲选 ⇒ 选到 Sea-redis 的 49380 | **真实缺陷**（我域） | ✅ 已修 9 处（探测式） |
| **r31** | — | — | — | ✅ **全绿 · 1030** |

⚠️ **共同点**：三个里有两个长得像「被测行为坏了」，**实为环境** ⇒ ⇒ 这就是为什么台账要求「判绿红只看 surefire 三项 + 退出码」。

### 🟢 第二十六轮 · **A3 轴本域最后一项销号：form 值超限的【业务信封】形状 ⇒ 真 Tomcat 活体 + 变异验证**（2026-10-28）

**① 销号前 A3 轴在本域的确切状态（逐条核对，不是抄上一轮的结论）**
| A3 子项 | 状态 | 证据 |
|---|---|---|
| 路径归一（`..`） | ✅ **已闭合** | 第十轮：`MessageApiPathNormalizationRealTomcatTest`（真 Tomcat）＋ A/B 对照组 `MessageApiPathNormalizationControlTest` ＋ 本域独立变异验证 |
| 保护层 413/503 裸响应形状 | ✅ **已闭合** | `MessageApiProtectionRealTomcatTest`（真 Tomcat）：413 + 空 body + 无 Content-Type；413 压过鉴权；未超限 ⇒ 401 |
| form 闸门与鉴权的**先后** | 🟡 **有界残留 · 刻意不改**（已登记） | RAG = 闸门先（`Ordered.HIGHEST_PRECEDENCE`）· Go = auth 先 ⇒ 差别已登记，不改判据 |
| 🔴 **envelope 拒绝形状 / 2049 参数** | ❌ **本轮销号的这一格** | 此前**只有 MockMvc** 覆盖（`MessageApiMvcPipelineTest`）⇒ ⇒ **缺一次真 Tomcat 活体** |

⇒ ⇒ **本轮补的就是最后一格**（G4 第二封回信第 ③ 条点名的「你域内唯一未销号项」· 域侧做）。

**② 新增探针**（`service/message/message-api/src/test/java/com/rag/message/message/api/MessageApiFormLimitEnvelopeRealTomcatTest.java` · **4 条**）
① **端口段前提**：真容器必须落在本域专用端口段（`21700–21799`）⇒ 防止 `RANDOM_PORT` 落进 OS 临时段、请求打到别人的 Docker 服务。
② **模式前提**：`GoStyleFormValueLimitInterceptor.mode()` 必须 = `ENVELOPE`（**直接断言**，不靠「返回 200」区分「关掉」与「根本没装」）。
③ 🔴 **目标断言**：>2048 个**非空 query 值**（实测用 2100）⇒ **HTTP 200** ＋ body 含 `"code":5001` ＋ **body 不含 Go 裸 400 文案** `too many form values`。
④ **阳性对照**：未超限 + 无 token ⇒ **401**（证明 ③ 不是「无条件短路」，形状断言有判别力）。

**③ 期望值的 Go 出处（逐字）**
Go `service/message/api/messagecenter.go:53` ⇒ `httpx.SetErrorHandler` ⇒ `code = messagecommon.ErrorServerCommon`（**5001**）· `msg = GetErrMsg(code)`（**"internal server error"**）· 返回 **`http.StatusOK`** ＋ `{Code, Msg, Data:nil}`。
⇒ ⇒ 本域契约 = 「**除鉴权失败外，HTTP 状态码恒为 200，成败全在 body 的 code 里**」。
⇒ ⇒ ⚠️ 共享层在 ENVELOPE 模式下是**抛异常**（`GoStyleFormValueLimitInterceptor:90-93`）而不是写响应 ⇒ ⇒ **必须是 Interceptor**（Filter 抛的异常到不了 `@RestControllerAdvice`）⇒ ⇒ **这一段只有真容器能判，MockMvc 不够** ⇒ ⇒ 这就是本探针存在的理由。

**④ 变异 M-D1（证明判据不是假绿）**
- 变异对象 = 本域 `message-api/src/main/resources/application.yml` 的 `mode: envelope` ⇒ `mode: raw`（**域侧写权面**）。
- md5 账：变异前 `199873219f92ca913b79c7fd04fe7cf5` → 变异后 `558d05b0e64e009ad82f56682b2caad2` → 还原后**回到 `19987321…`** ✔
- 实测：`Tests run: 4, Failures: 2` ⇒ 红的是
  ① `formLimitModeMustBeEnvelope`（**模式前提**，预期会红 —— 它就是盯着这个开关的）＋
  ② **`overLimitQueryValuesYieldBusinessEnvelope`（目标断言）** ⇒ ⇒ **目标断言确实在目标断言上红** ✔
  另两条（端口段、阳性对照）**仍绿** ✔
- 🔴 **变异把 RAG 的另一侧形状也实测出来了**：`mode: raw` 时 ⇒ **HTTP 400** ＋ body =
  **`too many form values, error: p0=1&p1=1&…`** ⇒ ⇒ **Go 的裸 400 形状逐字一致**
  ⇒ ⇒ 由此可判定：**RAG 侧的 `envelope` 模式正是与 Go `SetErrorHandler` 对齐的那一侧**，
  而 `raw` 对应的是 Go 侧 user/admin api（**未注册** `SetErrorHandler`）的行为 ⇒ ⇒ **两边形状都实测到了，不是推测**。
- 还原后复跑：`Tests run: 4, Failures: 0` ⇒ ✅ **绿腿闭合**。

**⑤ ⇒ ⇒ 销号结论**
🟢 **A3 轴「envelope 拒绝形状 / 2049 参数」这一格 = RAG 腿 L4**（真 Tomcat 活体 ＋ 变异验证 ＋ Go 出处逐字）。
⇒ ⇒ 本域 A3 轴仅剩**已登记的「有界残留 · 刻意不改」**（form 闸门与鉴权先后）⇒ ⇒ **无未销号项**。

**⑥ 写权与边界**
- 探针类落在 **message-api 的 test 目录**（本域写权面）· 变异对象是**本域** yml ⇒ ⇒ **零越权**。
- ⚠️ **共享层那半仍是 G4 的**：本探针只钉住「**本域**观测到的形状」，
  共享层 `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` 的行为未改 ⇒ ⇒ **不越权、不落码**。
- ⚠️ **本类不进 Go 腿**：Go 侧期望值取自 `messagecenter.go:53` 源码（**未做 Go 活体对拍**）⇒ ⇒ 证据边界显式标注。

**⑦ 三域现状（一句话对照）**
| 域 | A3「真实请求证据」 | 本轮 |
|---|---|---|
| **message** | 🟡 → ✅ **L4** | **本轮补上 envelope 形状**（真 Tomcat ＋ M-D1 变异） |
| **like** | 🟢 **已补**（第十轮） | 本轮**未动**（已复核，不重复劳动） |
| **task** | 🟢 **已补**（第十轮）＋ form 闸门 **OFF** ⇒ envelope 形状 **N/A** | 本轮**未动** |

⚠️ **教训（留给全波）**：G4 那封回信说「A3 真 Tomcat 探针 = 你域内唯一未销号项」⇒ ⇒ **按字面看像是「我什么都没做」**
⇒ ⇒ **实读本域三份台账才发现**：路径归一（第十轮）与 413 形状（`MessageApiProtectionRealTomcatTest`）**早已闭合**，
真正缺的只有 **envelope 拒绝形状**那一格 ⇒ ⇒ **别人的「未销号」描述可能停在旧 tip**
⇒ ⇒ **先核自己的台账，再决定要不要补**（否则会重复劳动，或漏掉真正的那一格）。

### 🔴 第二十七轮 · **我自己的「起跑前体检」有三道判据是坏的** —— 探针恒假 / 自检判据自相矛盾 / 体检顺序反了（2026-10-28 12:1x）

**⓪ 为什么要写这一轮**：本轮开 r33 时，三道体检**连续三次自己把我拦下来**（其中**两次是假拦**）
⇒ ⇒ ⇒ 一个判据在报错之前，**先问「判据本身对不对」**（§3.3 同一条纪律，这次落在**我自己的脚本**上）。

**① 第一处：窗口探针恒返回 0 ⇒ r29~r32 四轮的「等窗口」防线全是摆设**
- 旧写法：`ps -Ao comm=,args= | awk '$1 ~ /^java$/ && /plexus[.]classworlds[.]launcher[.]Launcher/ {n++}'`
- 🔴 **macOS 的 `ps -o comm=` 给的是全路径 `/usr/bin/java`**（不是 `java`）⇒ ⇒ `/^java$/` **恒不匹配**
  ⇒ ⇒ 探针**恒返回 0** ⇒ ⇒ **永远判「窗口空」** ⇒ ⇒ **r29 / r30 / r31 / r32 四次跑批其实从未检查过窗口**（各轮 wait.log 逐字写着 `WINDOW-FREE after 1 polls` ⇒ **第一次轮询就判「空」**）。
  ⇒ ⇒ ⚠️ **【订正注记 · 2026-10-28 12:3x】**：本条先前写的是「**r29 / r31 / r32 三次**」⇒ ⇒ **应为四次**（**r30 也用的是 `ps -Ao comm=` 形态**）⇒ ⇒ **原措辞保留在本注记里，不删改**。
  ⇒ ⇒ ⚠️ **r28 那轮不受此坑影响**：它用的是 `ps -Ao args=` 形态 ⇒ 其 wait.log 逐字记录了 **33 次 `busy(real=N)`** 才等到 `WINDOW-FREE after 34 polls`（09:31:20 → 09:42:23）⇒ ⇒ **r28 的失败与本条无关**（根因是端口/孤儿容器那条，已单独留档）。
- ⚠️ 与 memory 里那条「判据自匹配 ⇒ 恒 busy」是**镜像同族**：**一个恒假、一个恒真**
  ⇒ ⇒ 「以为在等窗口」与「以为在躲窗口」**同样是幻觉**。
- ✅ 修正（**探针 v2**）：改按**类名字面量**判定，不再依赖 `argv[0]`：
  `ps -Ao args= | awk '/plexus[.]classworlds[.]launcher[.]Launcher/ && /classworlds[.]conf=/ {n++}'`
  ⇒ ⇒ 自我排除仍是**结构性**的：本探针自己命令行里写的是**括号化**写法 ⇒ ps 输出里我那行**匹配不上** ⇒ 结构上不可能自计数。

**② 第二处：探针自检的「负样」判据自相矛盾 ⇒ 脚本连续两次自杀（假坏）**
- 自检原意：造一个「maven 形状」的假进程 ⇒ 证明探针**能**看见它（正样）；假进程退出后 ⇒ 探针必须看不见（负样）。
- 🔴 **负样被写成「假进程退出后计数必须 = 0」** ⇒ ⇒ 而**此刻场上本来就有别人的 maven**（实测 2~3 个）⇒ ⇒ 负样必然 >0
  ⇒ ⇒ 脚本判「探针坏了」并退出 ⇒ 连着两版都栽在这里（**幸未起跑，两次都是零副作用**）。
- ✅ 修正：**相对量**判据 —— `BASE`（造之前）→ `POS`（造之中）⇒ 必须 `POS ≥ BASE+1`（=「能看见」）；
  `AFTER`（造之后）⇒ 必须 `AFTER ≤ POS`（=「不残留」）⇒ ⇒ 不再依赖「全场无 maven」这个前提。
- ⚠️ **假样本本身也踩了三个坑**（每一个都会造成「假样本没被看见 ⇒ 误判探针坏」）：
  ① `sleep 4 org.codehaus...` ⇒ sleep 把多余参数当时间间隔 ⇒ **立即退出** ⇒ 探针看不见；
  ② 改成 shell 脚本 ⇒ 内核 exec 成 `/bin/sh <脚本>` ⇒ **argv[0] 变成 /bin/sh** ⇒ 靠「首 token 是 java」的判据落空；
  ③ 路径是**已存在的符号链接** ⇒ `cat >` 跟到 `/bin/sleep` ⇒ **写不进去**（`Operation not permitted`）。
  ⇒ ⇒ 终版：脚本**忽略所有参数**只 `sleep 30`，探针 v2 按**类名字面量**判定 ⇒ 与 argv[0] 无关。
- ✅ 终版自检实测：**`base=3 pos=4 after=3`** ⇒ 「能看见」✔ 且「不自计数」✔ ⇒ ⇒ 这道判据**先被证明可用，才被拿来用**。

**③ 第三处：体检顺序反了 ⇒ 活容器被误判成「孤儿」**
- 旧顺序：**先查**我域三段（22760–22849）内有没有容器 ⇒ 有 ⇒ 判失败不硬跑；**再**等窗口。
- 🔴 实测**假阳性**：窗口正被别人占着时，别人**活着的** run 的 Testcontainers 容器**也会占住我域端口段**
  （实测 `competent_herschel:22792` 落在 like 段）⇒ ⇒ 体检判「孤儿」⇒ 脚本退出
  ⇒ ⇒ 而那个容器 30 秒后自己消失 ⇒ ⇒ 它是**活容器**，不是孤儿。
- ✅ 修正顺序：**先等窗口净 → 再查我域三段** ⇒ 此时还占着我域段的容器才是**真孤儿**
  （r28 的教训仍成立：kill 掉 maven ⇒ Ryuk 随 JVM 死 ⇒ 容器泄漏 ⇒ 端口被占 ⇒ 下一轮撞固定宿主口）。
- ⇒ ⇒ **判据本身也要判「真假阳性」**：一个只会「拦下来」的体检，可能一直在拦**活的东西**。

**④ U+FFFD 口径澄清（G4 提示的那对数）—— 结论：口径本来就一致，差的是【快照时刻】**
| 口径 | 我树实测（HEAD `36e117c` · 2026-10-28 12:12） | G4 报的那对数 |
|---|---|---|
| **含 `target/`** | **15 份 / 49 处** | 18 份 / 60 字节 |
| **剔 `target/`** | **12 份 / 27 处** | 15 份 / 38 字节 |
| **含 − 剔** | **3 份 / 22 处** | **3 份 / 22 字节** |

⇒ ⇒ **差值逐字相同（3 份 / 22）** ⇒ ⇒ 那 3 份正是 **3 个 fat jar**（`like-rpc` 8 + `message-rpc` 6 + `task-rpc` 8 = 22 处）
⇒ ⇒ ⇒ **「含 / 剔 `target/`」这个口径本身没有分歧**；G4 报的那对数是**更早一个快照**的，
而「15 份 / 38 字节」正是我三份台账第十二轮 ③ 里已记的那个数。
⇒ ⇒ 且两份快照的算术**自洽**：18−3=15 · 60−11=49 · 15−3=12 · 38−11=27（中间那步 −3 份 / −11 处 = 当时的清理）
⇒ ⇒ **真正要统一的不是「含不含 target/」，而是「报告时必须带【快照时刻 + 口径】」** ⇒ 本条即按此格式写。

**⑤ 本域（message / like / task）U+FFFD 现状（字节级实测 · 剔 `target/`）**
| 分类 | 份数 | 处数 | 处置 |
|---|---|---|---|
| **G2 域源（message / like / task）** | **0** | **0** | ✅ 已修并提交（第十二轮 ③） |
| **题材·被测对象（不许改）** | 4 | 6 | `docs/Pitfalls/message/rpc.md`(2) · `docs/Pitfalls/users/user/rpc.md`(1) · `UserApiGoStyleBodyContentTypeTest.java`(1) · `ArticleApiMvcPipelineTest.java`(2) |
| **G3 域源** | 5 | 13 | comment 1 份 3 处 ＋ favorite 4 份 10 处 ⇒ **请 G3 自修**（我无写权） |
| **G1 域源** | 3 | 8 | `AvatarHistoryService.java`(2) · `AvatarHistoryIgnoresBodyTest.java`(3) · `UserRpcLoginNullStatusReadsAsZeroTest.java`(3) ⇒ **请 G1 自修**（我无写权） |

⇒ ⇒ **12 份 / 27 处 = 题材 6 ＋ G3 13 ＋ G1 8** ⇒ ⇒ **守恒逐字相等** ✔（⇒ ⇒ 没有第四类漏网的）

**⑥ ⇒ ⇒ 本轮教训（跨项目可复用）**
① **一个探针可以恒假而不报错** —— 「等待 / 判定」类代码必须**先被证明可用**（正样 ＋ 自排除论证），再拿去用；
② **「等窗口」与「躲窗口」都可能是幻觉** —— 判据恒真与判据恒假同样致命，**两个方向都要查**；
③ **体检要能区分「活的」与「死的」** —— 顺序错了，好体检也会变成误杀；
④ **报告数字必须带【快照时刻 + 口径】** —— 否则同一个数会被读成两个数。

**⑦ 🔴 复盘时才发现的一个「数被读成两个数」的实例 —— XML 侧被【定向跑】污染，而日志侧没被污染**
- 触发：本轮用同一份 **r32 日志**复跑聚合脚本 ⇒ **XML 侧报 1034，日志侧报 1030** ⇒ ⇒ 差 **4**。
- 根因（逐条核过，不是推测）：聚合脚本的**逐模块表列来自 `target/surefire-reports/TEST-*.xml`**，而 **XML 会被定向跑改写**
  ⇒ ⇒ 本轮 **A3 探针的定向跑**（`-Dtest=…`）在 11:0x 重写了 `message-api` 的报告 ⇒ ⇒ **XML 侧多出那 4 条**。
  ⇒ ⇒ 而**日志的模块级汇总行是那一轮跑出来的、不可变** ⇒ ⇒ r32 日志里 `message-api` 逐字写着 **103**（直查日志确认）。
- ⇒ ⇒ **r32 的权威数 1030 成立**（以**日志模块级汇总行**为准）⇒ ⇒ **A3 那份测试文件是 11:02:44 才提交的，晚于 r32（10:46–10:56）** ⇒ ⇒ r32 **不可能**包含它。
- ⇒ ⇒ ⚠️ **这也是台账里早就记过的坑的又一次复现**：「**定向跑会改写报告 ⇒ 孤儿/混合报告污染计数**」⇒ ⇒ **这次它污染的是【事后复跑旧日志】的场景**。
- ⇒ ⇒ **纪律（固化）**：① **权威数只认【日志模块级汇总行】**；② **XML 侧只在「本轮 `clean` 之后」才有意义**
  （`clean` 会清掉 `target/` ⇒ 报告重建 ⇒ 两侧才可比 ⇒ ⇒ **这正是收口态必须是 `clean test` 的原因**）；
  ③ **事后复跑聚合脚本时，必须先看两列是否一致** ⇒ 不一致 ⇒ 先查「是不是被后续定向跑改写过」，**不要**直接改台账上的数。

### 🟢 第二十八轮 · **波末收口回归实测：9 模块 = 1034**（域侧 701 ＋ 共享层 333）· **三口径逐字相等**（2026-10-28 12:4x）

**① 实测（收口态 = `clean test`）**
`mvn -o -B -DargLine=-Xmx2g -am -pl <9 个目标模块> clean test`
⇒ ⇒ **BUILD SUCCESS** · **MVN_EXIT=0** · 10 min 58 s · 2026-10-28 12:33:37 → 2026-10-28 12:44:35 · 日志 `/tmp/g2-r33-regression.log` · HEAD `3423390`
⇒ ⇒ **0 次** `Address already in use` / **0 次** `port is already allocated` / **0 次** `http2 exception` ✔

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

**③ 🔴 三口径交叉核对（本轮新增第三口径 ⇒ 比前几轮更硬）**
| 口径 | 怎么取 | 结果 |
|---|---|---|
| ① **日志逐类用例行求和** | 按 `Building <artifactId>` 分段，把该段内每条 `[INFO] Tests run: N … -- in <类>` 求和 | **1034** |
| ② **日志模块级汇总行** | 同段内那条**不带** `-- in` 的 `Tests run: N` | **1034** |
| ③ **surefire XML `testsuite` 求和** | 9 个目标模块 `target/surefire-reports/TEST-*.xml` | **1034** |

⇒ ⇒ **三者逐字相等** ✔ ⇒ ⇒ 且 **9 目标模块 + reactor 带出的 `users-common` 39 = 1073 = maven 模块级合计** ✔
⚠️ **口径纪律（第七轮 ⑦ 已固化）**：**权威数只认【日志模块级汇总行】**；XML 侧只在「本轮 `clean` 之后」才有意义 ⇒ ⇒ **收口态必须是 `clean test`**。

**④ ⇒ ⇒ 与前几轮的增量归因（逐条可核）**
| 轮次 | 时刻 | HEAD | 9 模块 | 变化 |
|---|---|---|---|---|
| r29 | 09:53:24 → 10:04:03 | `b5910dc` | 1030 | 基线 |
| r31 | 10:30:18 → 10:39:29 | `bebf30d` | 1030 | `forPort(0)` 修好 ⇒ **条数不变（预期且实测）** ✔ |
| r32 | 10:46:45 → 10:56:02 | `04f9e79` | 1030 | 并入 G4 尖端后 ⇒ **条数不变** ✔ |
| **r33** | 2026-10-28 12:33:37 → 2026-10-28 12:44:35 | `3423390` | **1034** | **+4** = **A3 新增的 4 条真 Tomcat 探针**（`MessageApiFormLimitEnvelopeRealTomcatTest` · message-api 103 ⇒ 107） |

⇒ ⇒ **1030 ⇒ 1034 的全部增量 = 那 4 条**（其余 8 个模块**逐字未变**）⇒ ⇒ 与「A3 销号只加探针、不动既有断言」**逐字相符** ✔

**⑤ ⇒ ⇒ 本轮的额外价值：探针本身被证明可用**
- 本轮窗口探针**实等了 72 轮**才等到窗口净（12:15:43 → 12:33:36）⇒ ⇒ **修正后的探针会「判忙」**（不再是恒 0）；
- **正样自检**：`base=3 pos=4 after=3` ⇒ 「能看见 maven 形状的进程」✔ 且「不自计数」✔；
- **体检顺序**：先等窗口净 → 再查我域三段容器 ⇒ 本轮 `PREFLIGHT-OK no-orphan-in-my-segments` ✔（r28 那次「孤儿容器」的真教训仍然成立）。

**⑥ 顺带落档的两条实缺陷（本域写权面，已修/已订正）**
- `docs/DRIFT/task/rpc.md` 的标题测试数 **129 ⇒ 134**（实测 134，三口径相等）⇒ ⇒ `check-ledger-selfcheck.py --domain task` 由红转绿。
- `check-ledger-selfcheck.py` 的 `LEDGER_FILES` **只注册了 `task` / `users` / `users-admin`** ⇒ `--domain message` / `--domain like` 报「找不到台账文件」⇒ ⇒ **这是工具注册表缺项**（`tools/**` **非本 lane 写权**）⇒ ⇒ 只登记 + 通报，不越权落码。

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
2. **`DRIFT-LIKE-003-C`**（权威读暴露形态：Go 是**独立进程** `cmd/fact-authority/main.go`（`LIKE_DATABASE_URL` ＋ `LIKE_FACT_AUTHORITY_LISTEN` ＋ `LIKE_FACT_AUTHORITY_TOKEN`，token ≥ 32），RAG 侧无 HTTP 端点）⇒ 落地需改 **2 处共享面**（新建模块要动 `check-wave-lane-ownership.py` 白名单 ＋ 根 `docker-compose.yml`）⇒ **G2 不可写** ⇒ 形态决策（**独立进程** vs 同进程独立 HTTP 端口）**须拍板**。
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

<!-- @new 2026-10-29 · `align/r3-ddl-message-like-task` · RoundPlan `docs/RoundPlan/Round-3.md` **T-3.1 的 G2 侧（`message` 域）** · 基线 = `do-my-own-gh` @ `d10227c5` -->

## DDL 逐域机器核（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G2 lane · `message` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**（AGENTS.md §3.1 对照清单的最后几项）；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并逐域实跑，但结论只落在 `docs/PhaseGoal-judge.md` §七 ⇒ **域级证据链断了一节** ⇒ 本节把 `message` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不修任何差异**（RoundPlan T-3.1：「冒出的新差异**按域登记**，**不在本轮当场修**」）。
> ⚠️ **本节零改动 `service/**`** —— 迁移脚本在 `service/message/message-rpc/**`（**别的 lane 在改**）⇒ 变异测试走**逐字节相同的临时副本**（见 §D.5）⇒ 提交后 `git diff --stat -- service/` = **0 行**。
> ⚠️ **`message` 域不是 N/A** —— N/A 的是 **`hot`**（两侧无数据库）⇒ `message` 域**两侧都有真库**（Go 3 张表 / RAG 3 张表）⇒ 本节是**实跑**结论。

### D.1 执行口径（命令原文 · EXIT · 日期 · 环境）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（14:22:5x–14:23:23 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task` · `align/r3-ddl-message-like-task`<br>⚠️ **实跑基点 = `do-my-own-gh` @ `d10227c5`**（工作树先 `git reset --hard do-my-own-gh` 对齐主线，再跑）⇒ **实跑结论与提交基点同源** |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本**只复制**副本，从不写入 |
| 容器 / 库 | `ddl-message-pg`（`postgres:16-alpine` · 端口 55569）· Go 库 `go_message_db` · RAG 库 `rag_message_db` |
| Go 侧 schema 来源 | **真源自己的 `AutoMigrate`**：`Sea-RideTheWind_golang/service/message/rpc/internal/model/db.go:20`（`func InitDB(conf DBConf) *gorm.DB`）→ `:42`（`db.AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})`）⇒ 探针调的正是**真源自己的 `InitDB`**（`dbconf` 探针）⇒ 跑的是**真源同一份 struct tag + 同一个 gorm 版本**<br>⚠️ **gorm 版本钉死**：真源 `go.mod:27` `gorm.io/driver/postgres v1.6.0` · `go.mod:28` `gorm.io/gorm v1.31.1` ⇒ §3.3.1 要求的「消费点」是 **`gorm.io/driver/postgres@v1.6.0/postgres.go` 的 `DataTypeOf`**（`time.Time` → `timestamptz` · 无 size 的 `int64` → `bigint`）⇒ **列类型结论不是从 struct tag 推断的，是 GORM 在建表时生成的** |
| Go struct 出处 | `service/message/rpc/internal/model/data_model.go:30-43`（`Notification` → `message_notification`）· `:49-65`（`Conversation` → `message_conversation`）· `:71-82`（`ConversationMessage` → `message_conversation_message`） |
| RAG 侧 schema 来源 | 仓内 `service/message/message-rpc/src/main/resources/db/migration/`（**Flyway**）**5 份**：`V1__create_message_notification.sql` · `V2__create_message_conversation.sql` · `V3__create_message_conversation_message.sql` · `V4__conversation_pair_unique_index.sql` · `V5__message_time_columns_timestamptz.sql` |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 下面所有差异数是**原始值**，没有被白名单压过 |
| 两侧表集合 | Go `go_message_db` = {`message_notification`, `message_conversation`, `message_conversation_message`} · RAG `rag_message_db` = **同 3 张** ⇒ 逐条一致 |

**命令 ① —— 建库 + 双侧活体对拍（索引 / 约束 / 触发器 + 源码↔活库 一次跑全）**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task"
bash tools/difftest/ddl-parity.sh message
```

⇒ **EXIT=1** —— 末行 `== 实质差异合计 7 处 ⇒ 退出码 1 ==`；runner 行 `=== 对拍退出码: 1 ===`
⇒ Go 探针打印 `AUTOMIGRATE_OK`；RAG 侧按序执行 `V1` → `V2` → `V3` → `V4` → `V5`

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`）**

```bash
python3 tools/difftest/check-ddl-static.py --domain message --indexes --check-source
```

⇒ **EXIT=1**（**7 处**）—— **与 ① 同数同因** ⇒ 两条路径互为复算（① 是「建库 + 对拍」，② 是「只对拍已建好的库」）

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain message --print-rag-sql
```

⇒ **EXIT=0**，输出 **5 行**，与 ① 实际执行的 5 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**（「清单只有一个产生点」在本域成立）

### D.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（`--check-source`） | 3 张表 | ✅ **0 差异** —— 仓里的 `.sql` 在**干净临时库** `rag_message_db_srccheck` 上原样重放后，与活库**逐项一致**（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器） | 0 |
| 2 | **列类型 / 长度** | 37 列 ↔ 37 列（键集合一致 ✓） | ✅ **0 差异** —— 脚本原话「逐字相同 37 列」 | 0 |
| 3 | **可空性（NOT NULL）** | 37 列 | ✅ **0 差异** —— 脚本原话「两侧完全一致 ✓」 | 0 |
| 4 | **DEFAULT** | 37 列 | 🔴 **6 处残留** | 6 |
| 5 | **索引**（`pg_indexes`） | Go 12 / RAG 12 | 🔴 **1 处残留**（另 2 处 = 命名形态差 ⇒ 语义等价，不计失败） | 1 |
| 6 | **唯一约束 / 主键**（`pg_constraint`） | Go 3 / RAG 3 | ✅ **0 处差异** —— 脚本原话「逐条一致 3 行 ✓」 | 0 |
| 7 | **触发器**（`pg_trigger`） | 两侧各 0 | ⚪ **两侧都没有触发器 ⇒ 本维度「无对象可比」** —— ⚠️ **这不是「触发器已对齐」** | 0 |
| 8 | **触发器形式 / 触发器函数体** | 两侧各 0 | ⚪ **同上 ⇒ 无对象可比** —— 脚本原话「两侧**没有可成对比对的触发器** ⇒ 本维度**什么都没比**（不是「已对齐」）」 | 0 |
| | **合计** | | | **7** |

> ⚠️ **读法**：本域 DDL 的**行为层**（类型 / 长度 / 可空性 / 唯一约束）在 **8 个维度里 4 个为 0**；剩下的 **7 处全部是「已登记并已签字的已知差异」**，**不是新回归**（逐条见 §D.4）。
> ⚠️ **维度 7/8 不是「通过」而是「无对象」** —— Go 侧 `message` 域**根本没有触发器**（真源 `service/message/**` 全域无 `CREATE TRIGGER`）⇒ 本域的触发器语义**没有可核对象**。

### D.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 37 列逐字相同（0 处差异）**

| 表 | 抽样列 | Go 侧类型 | RAG 侧类型 | 判定 |
|---|---|---|---|---|
| `message_notification` | `id` / `recipient_id` / `sender_id` / `sender_role` / `kind` | `bigint` / `bigint` / `bigint` / `integer` / `integer` | 同左 | ✅ 逐字相同 |
| `message_notification` | `title` / `content` / `extra` | `character varying(255)` / `text` / `jsonb` | 同左 | ✅ 逐字相同（`extra` 的 `jsonb` 来自 Go tag `type:jsonb` ⇒ `data_model.go:40`） |
| `message_notification` | `is_read` / `read_at` | `boolean` / `timestamp with time zone` | 同左 | ✅ 逐字相同 |
| `message_conversation` | `participant1_role` … `last_message_preview` | `integer` / `bigint` / `character varying(255)` | 同左 | ✅ 逐字相同 |
| `message_conversation_message` | `conversation_id` / `content` / `is_read` / `read_at` | `bigint` / `text` / `boolean` / `timestamp with time zone` | 同左 | ✅ 逐字相同 |

⇒ 脚本汇总行：`类型/长度差异 0 处：` + `逐字相同 37 列。`
⇒ ⚠️ **时间列已是 `timestamp with time zone`**（不是 `timestamp without time zone`）⇒ `DRIFT-MSG-V-020` 那轮 `V5` 迁移的**类型面已闭合**（**只余 DEFAULT 残差**，见 §D.4 残留 ①）。

**② 可空性（NOT NULL）—— 0 处差异**

- 脚本原话：`补充对照 · NOT NULL（可空性）：差异 0 处` + `（两侧完全一致 ✓）`
- 抽样：`message_notification.read_at` / `extra`（Go tag **无** `not null` ⇒ 两侧都可空）· `message_notification.title` / `content`（Go tag **有** `not null` ⇒ 两侧都 `NOT NULL`）· `message_conversation_message.status` 等同理。
- ⇒ `V5__message_time_columns_timestamptz.sql:74-75,84-85,92` 那 5 条 `DROP NOT NULL` **与 Go 侧逐条对齐** ⇒ 本维度**已闭合**。

**③ 源码 ↔ 活库 —— 0 差异（本轮新增维度）**

- 目录扫描得到 **5 份**迁移脚本 ⇒ **清单由目录扫描产生**，不会落后于仓。
- 在**干净临时库** `rag_message_db_srccheck` 上**原样重放**（交给 PG 自己解析 ⇒ 与生产同一条路径）⇒ 与活库逐项对比：**表集合 3 ↔ 3** · **列集合 0 差异** · **列属性 0 差异** · **可空性 / DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致`
- ⇒ **含义**：本域仓里的 `.sql` 与**当前活库**之间**没有分叉** ⇒ 「迁移改了没人重跑」这一类漂移**在 `message` 域为零**。

**④ 唯一约束 / 主键 —— 0 处差异（3 行逐条一致）**

| 约束 | contype | 定义 |
|---|---|---|
| `message_notification_pkey` | p | `PRIMARY KEY (id)` |
| `message_conversation_pkey` | p | `PRIMARY KEY (id)` |
| `uk_conversation_pair` | u | `UNIQUE (participant1_role, participant1_id, participant2_role, participant2_id)` |

⇒ `uk_conversation_pair` 在两侧都是**唯一索引载体**（Go 的 `uniqueIndex` tag ⇒ `pg_indexes` 有它、`pg_constraint` 无它；RAG 的 `V4` 已把 `V2` 的 `ADD CONSTRAINT` 改成 `CREATE UNIQUE INDEX` ⇒ 两侧**载体相同**）⇒ 唯一性判定 / `23505` / `ON CONFLICT` 仲裁完全一致。

**⑤ 索引命名形态差 —— 2 处语义等价（不计失败）**

| 语义等价配对 | 唯一性 | 列 |
|---|---|---|
| `idx_message_conversation_last_message_at` ≡ `idx_conversation_last_message_at` | non-unique | `last_message_at` |
| `idx_message_conversation_updated_at` ≡ `idx_conversation_updated_at` | non-unique | `updated_at` |

⇒ ✅ **已签字** `SD-2026-10-24-01`（[`docs/Intentional_Deviations/message/rpc.md:30`](../../Intentional_Deviations/message/rpc.md)）⇒ **不重复质疑、不改回**。

### D.4 残留 7 处（逐条列出 + 定级 + 签字状态）

> **定级口径** = `docs/twoStage.md` **§十**（`twoStage.md:941-973`）：**A** = 阶段 1 准入阻断 · **B** = 阶段 1 竞争力补强 · **C** = 阶段 2 最终工程建设 · **D** = 经分析确认可不实现或不适用（**须有明确理由**）。
> ⚠️ **一条差异 ≠ 一个缺陷**：本节 7 处 ⇒ 归成 **2 条**（DEFAULT 6 · 索引 1）。
> ✅ **本节 2 条**（DEFAULT 6 + 索引 1）**全部已签字** ⇒ 按 AGENTS.md §0：**不重复质疑、不得「顺手改回 Go 的旧写法」**。

#### 残留 ① ✅ DEFAULT 6 处 —— 6 个时间列 `DEFAULT CURRENT_TIMESTAMP`（Go 侧**无默认**）· **已签字**

| 表（RAG） | 列 | Go 侧 DEFAULT | RAG 侧 DEFAULT | Go 出处（tag 里**没有** `default`） | RAG 出处 |
|---|---|---|---|---|---|
| `message_conversation` | `created_at` | `<NULL>` | `CURRENT_TIMESTAMP` | `service/message/rpc/internal/model/data_model.go:63`（`autoCreateTime`） | `V2__create_message_conversation.sql:21` |
| `message_conversation` | `last_message_at` | `<NULL>` | `CURRENT_TIMESTAMP` | `data_model.go:62`（`not null;index`，**无** `default`） | `V2__create_message_conversation.sql:20` |
| `message_conversation` | `updated_at` | `<NULL>` | `CURRENT_TIMESTAMP` | `data_model.go:64`（`autoUpdateTime;index`） | `V2__create_message_conversation.sql:22` |
| `message_conversation_message` | `created_at` | `<NULL>` | `CURRENT_TIMESTAMP` | `data_model.go:81`（`autoCreateTime;index:idx_message_conversation_time,priority:2`） | `V3__create_message_conversation_message.sql:17` |
| `message_notification` | `created_at` | `<NULL>` | `CURRENT_TIMESTAMP` | `data_model.go:41`（`autoCreateTime;index:idx_notification_recipient_time,priority:2`） | `V1__create_message_notification.sql:18` |
| `message_notification` | `updated_at` | `<NULL>` | `CURRENT_TIMESTAMP` | `data_model.go:42`（`autoUpdateTime`） | `V1__create_message_notification.sql:19` |

| 项 | 内容 |
|---|---|
| 成因 | Go 的 `autoCreateTime` / `autoUpdateTime` 由 **GORM 在 Go 代码里赋值** ⇒ **不建 DB 默认**；RAG 侧多一条 DB 级默认 |
| 签字状态 | ✅ **已签字 `SD-2026-10-24-04`** —— **2026-10-24 用户拍板：保留签字**（保留 RAG 侧的 DEFAULT，**不加** `V6` 迁移去 `DROP DEFAULT`）⇒ [`docs/Intentional_Deviations/message/rpc.md:80`](../../Intentional_Deviations/message/rpc.md)（**同条亦复述于该档头部第 17-18 行**）· DRIFT 侧 `DRIFT-MSG-V-020`（[`docs/DRIFT/message/rpc.md`](rpc.md)） |
| **定级** | **D 类**（经分析确认可不实现 —— **有明确理由**） |
| 定级理由 | ① DEFAULT 只在「**INSERT 省略该列**」时发作 ⇒ 两侧**所有** INSERT 都**显式绑定**该列（签字档原文引 `ConversationMapper.java:143-155` / `NotificationMapper.java:205-219` / `ConversationMessageMapper.java:159-166`）⇒ **当前不可观察**；② 删掉它是为「**不可观测的维度**」冒险 ⇒ 收益低于风险（签字档原文：「为一个不可观测的维度冒『有人在某条裸 SQL 路径上依赖了默认』的风险不划算」）；③ 方向是「**RAG 更宽松**」（多一个默认值）⇒ **不构成阶段 1 准入阻断** ⇒ **不是 A 类**；④ **不是 C 类** —— 已签字的就是「保留」这个决策 ⇒ 再谈「阶段 2 恢复 Go 语义」等于**推翻一次已拍板的决策** ⇒ 按签字档的「**代价（必须知情）**」留痕即可 ⇒ 有明确理由的「不实现」⇒ **D 类** |
| ⚠️ **必须知情的是「将来会分岔」** | 签字档原文：「这不是『永远等价』，是『当前无路径』」—— 将来若有人写一条**省略该列**的 INSERT：Go 写 `NULL`（`created_at`/`updated_at`）或 `NOT NULL` 违规（`last_message_at`），RAG 写 `now()` ⇒ **两侧分岔，方向是「RAG 悄悄填了个时间」** ⇒ 排查成本高 |
| 处置 | **本轮不修、不重复质疑**（已签字 ⇒ AGENTS.md §0：「对拍看到它们变红，先确认是不是签字项，不要当新回归去『修』」） |

#### 残留 ② ✅ 索引 1 处 —— `idx_notification_recipient_read` 的**列数**：Go 1 列 / RAG 2 列 · **已签字**

| 项 | 内容 |
|---|---|
| 差异 | 索引**同名** `idx_notification_recipient_read`，但列不同：Go = `cols=['is_read']`（**单列**）· RAG = `cols=['recipient_id', 'is_read']`（**复合 2 列**）⇒ 两侧都 `unique=False` |
| Go 出处 | `service/message/rpc/internal/model/data_model.go:38` —— `IsRead bool \`gorm:"column:is_read;not null;default:false;index:idx_notification_recipient_read,priority:2"\``<br>⇒ ⚠️ 该索引**只有 `priority:2`、没有 `priority:1` 成员**（`recipient_id` 的 `priority:1` 被写到了**另一个**索引名 `idx_notification_recipient_time` 上，见 `data_model.go:32`）⇒ GORM 于是只建出**单列**索引 `(is_read)` ⇒ 那是**声明笔误的产物**，不是设计意图 |
| RAG 出处 | `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:43-44` —— `CREATE INDEX IF NOT EXISTS idx_notification_recipient_read ON message_notification (recipient_id, is_read);`（该文件 `:32-41` 已就地写下「**与 Go 侧不一致是已签字的有意偏离，别顺手改回单列**」） |
| 签字状态 | ✅ **已签字 `SD-2026-10-23-06`** —— **2026-10-23 用户拍板：保留 RAG 现状，不回改成 Go 的单列索引** ⇒ [`docs/Intentional_Deviations/message/rpc.md:141`](../../Intentional_Deviations/message/rpc.md) |
| **定级** | **D 类**（经分析确认不实现 —— **有明确理由**） |
| 定级理由 | ① **查询结果完全相同** ⇒ 不改变任何写入语义、冲突行为、唯一性判定 ⇒ **纯查询计划差异（性能）** ⇒ **不是 A 类**；② 方向是「**RAG 更优**」—— `ListNotifications` 的真实过滤形状是 `WHERE recipient_id = ? AND is_read = false`（签字档原文引 `service/message/rpc/internal/model/message.go`）⇒ RAG 的复合索引**正好命中这个前缀**，Go 的单列索引只能先定位 `is_read=false` 的全部行再回表过滤 `recipient_id` ⇒ **回改会造成计划回退**；③ 有明确理由的「不实现」⇒ **D 类**；④ **不是 C 类** —— 没有「待恢复的 Go 语义」要恢复，签字档已给出「RAG 更优」的结论 |
| ⚠️ **必须知情的代价** | 两侧 `pg_indexes` / `\d` 输出不同 ⇒ **任何按索引定义做逐字节比对的 DDL 工具在这一条上必然为红**（本轮实测正是如此）⇒ 属**预期差异，不是回归** |
| 处置 | **本轮不修、不重复质疑** |

### D.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：`service/message/**` 由**别的 lane 在改**（`service/**` 本 lane **零写权**）⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**路线：把 `check-ddl-static.py` 与该域的 `db/migration/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ 脚本内的 `REPO_ROOT` 与仓内布局一致 ⇒ 它读到的**仍是仓里那份 `.sql` 的同一份字节**，只是不在仓里写），**逐字节校验 sha256 相同**，再在**副本**上做变异。

**变异：`message_notification.sender_id` 的列类型（`BIGINT` → `INTEGER`）**

| 步骤 | 命令 / 动作 | 结果 |
|---|---|---|
| 建临时树 | `cp` 脚本 + `cp` 该域 5 份 `.sql` ⇒ `/tmp/g2-mut/message/{tools/difftest,service/message/message-rpc/src/main/resources/db/migration}` | sha256 **5/5 逐字节 SAME**（见下表） |
| 基线 | `python3 /tmp/g2-mut/message/tools/difftest/check-ddl-static.py --domain message --indexes --check-source` | **EXIT=1**（**7** 处）· 源码↔活库 = `✅ 重放结果与活库逐项一致` |
| 变异 | 副本 `V1__create_message_notification.sql:10`：`sender_id       BIGINT       NOT NULL,` → `sender_id       INTEGER       NOT NULL,`（改**一个列的类型**，命中目标行 **1** 处） | 变异前后该行已回显核对（`before`/`after` 两行均打印在运行日志里） |
| 变异后重跑 | 同上命令 | **EXIT=1**（**8** 处 = 7 + **1**）· 源码↔活库 **转红**：<br>`🔴 列属性不同：message_notification.sender_id  源码=('integer', '')  活库=('bigint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 删掉临时树里的 `service/` 后**重新拷贝**原件 | sha256 **5/5 逐字节 SAME** |
| 还原后重跑 | 同上命令 | **EXIT=1**（**7** 处 · **回到基线**）· 源码↔活库 回到 `✅ 一致` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **5 份全部 SAME**（sha256 原值见下）⇒ 逐字节回到变异前 |
| **收尾** | `rm -rf` 整棵临时树 | `exists=no` ⇒ **工作树与 `/tmp` 均无临时副本残留** |

**sha256 对照（变异前 = 还原后，两次完全相同）**

| 文件 | sha256（副本 = 原件，两次一致） |
|---|---|
| `V1__create_message_notification.sql` | `17f16b79011b25c733c30bdf39389d0c020e35e95f843547fdaeb1b251c61f13` |
| `V2__create_message_conversation.sql` | `97a65f86ecc77eb8a664ecc0d5e774059a05466161f90b3a1c2d0053f2a062da` |
| `V3__create_message_conversation_message.sql` | `d9a13539a2abb41d6ca34626db45a5dcd43a1797c05381b4ba4797f9a436aed3` |
| `V4__conversation_pair_unique_index.sql` | `30fad63c1281dc40622ed7ca65364fb8f9de402bea7092bc32bfa760178286c9` |
| `V5__message_time_columns_timestamptz.sql` | `c73f3c8d146bc2e5e5e6a8c1cb0b536fb942869d6f6d614c34b20de00af1eaba` |
| `check-ddl-static.py`（脚本副本） | `cc87da8dfde81eb11dd160150299369e6012d3eb8d815e44ede02a5c517a6ebb` |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`message_notification.sender_id`），其余 7 处**不变** ⇒ 守门**不是假绿**；还原后**精确回到基线** ⇒ 无残留。
⚠️ **本域的「期望 EXIT」口径**：本域 Go↔活库**本就有 7 处已签字差异** ⇒ 还原后的正确期望值是 **EXIT=1（7 处）**，**不是** EXIT=0 ⇒ 变异判据用的是「**源码↔活库这一维度的差异数 0 → 1 → 0**」＋「总差异数 7 → 8 → 7」，不是总退出码。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-message-like-task"
git status --short          # ⇒ 空
git diff --stat -- service/ # ⇒ 0 行
```

### D.6 人工补查（脚本**不查**的维度 · **人工闭合，非脚本原生结论**）

> ⚠️ `tools/difftest/check-ddl-static.py` 的「约束」维度**只覆盖 `pg_constraint` 的主键 / 唯一类**；**CHECK（`contype='c'`）、外键（`contype='f'`）、分区（`relkind='p'`）、存储过程 / 自定义函数（`pg_proc`）** 都需要**另查** ⇒ 下表是**本轮人工补查**的结果，**明确标注为人工闭合，不冒充脚本原生结论**。

| 维度 | Go 侧 `go_message_db` | RAG 侧 `rag_message_db` | 人工闭合结论 |
|---|---|---|---|
| **CHECK**（`pg_constraint` where `contype='c'`） | 2 行，**但 `conrelid` 为 0** ⇒ 是 PG **内建域**的约束（`cardinal_number_domain_check` / `yes_or_no_check`），**不是用户表约束** | **同样 2 行、逐字相同** | ✅ **用户表 CHECK：两侧各 0 ⇒ 无对象可比**（内建域约束两侧**逐字一致**，不构成差异） |
| **外键**（`contype='f'`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无外键** |
| **分区**（`pg_class.relkind='p'` / `relispartition`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无分区表** |
| **存储过程 / 自定义函数**（`pg_proc`，排除扩展与 `pg_catalog`） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无用户自定义例程** |
| **触发器**（`pg_trigger`，排除 internal） | 0 行 | 0 行 | ✅ **两侧各 0 ⇒ 无对象可比**（与 §D.2 维度 7/8 同源） |

⇒ **「无对象可比」≠「已对齐」**：以上 5 个维度**两侧都没有可比对象**，本轮**未核**（无对象可核），**不写成「已对齐」**。

### D.7 证据强度 · 效力边界 · 未能核到的部分

| 项 | 结论 |
|---|---|
| 证据强度 | **E4（真 PG 双侧活体）** —— 两侧各建一个真库（Go 侧跑**真源自己的** `AutoMigrate`，RAG 侧按序执行 `V1`~`V5`），逐条 dump `pg_indexes` / `pg_constraint` / `pg_trigger` / `information_schema.columns`；**外加 `--check-source` 的「源码重放 ↔ 活库」真库对照** |
| 变异验证 | ✅ **已做**（§D.5）⇒ 守门有牙齿，**不是假绿** |
| **效力边界 ①** | 本节结论只覆盖 **`message` 域 3 张表**；`service/message/message-common/**` 与 `service/common/**` 的**共享层**不在本节范围 |
| **效力边界 ②** | 结论覆盖的是**当前迁移脚本 + 当前 Go struct tag** ⇒ 任何人改了 `data_model.go` 的 tag 或改了 `db/migration/` 下的 `.sql`，本节结论**即失效** ⇒ 需重跑 `ddl-parity.sh message` |
| **效力边界 ③** | `--check-source` 的「源码 ↔ 活库」维度**与 Go 侧无关** ⇒ 它只保证「仓里的 `.sql` 与活库没分叉」，**不代表**活库与 Go 一致（那是前 6 个维度的事） |
| **未能核到 ①** | **触发器维度（维度 7/8）**：两侧各 0 个触发器 ⇒ **无对象可比 ⇒ 本维度未核**（不是「已对齐」） |
| **未能核到 ②** | **`--check-source` 不检查 CHECK / 外键 / 分区 / 存储过程** ⇒ §D.6 是**人工补查**（`contype in ('c','f')` / `relkind='p'` / `pg_proc` 三条手工 SQL）⇒ **非脚本原生结论**，但**已人工闭合** |
| **未能核到 ③** | **未做「同一次写入两侧落库后读回」的活体数据对拍**（本节是 **schema 层**对照，**不是数据层**）⇒ 数据层的等价性另由 `docs/DRIFT/message/rpc.md` 的各轮双侧活体承担 ⇒ **本节不重复声称数据层已对齐** |
| **未能核到 ④** | **Go 侧手写 `.sql` 夹具未纳入本节基线** —— `message` 域真源**没有** `internal/model/migrations/` 目录 ⇒ 本节基线**无基线选择歧义**（与 `like` 域不同，见 [`docs/DRIFT/like/vs-common.md`](../like/vs-common.md) 同名节） |

### D.8 本节边界（本节**不**声称什么）

1. **不修任何差异** —— 本轮只**登记**（RoundPlan T-3.1）。
2. **不重复质疑已签字项** —— 残留 ① ② **均已签字** ⇒ 本节只做**留痕 + 定级**，**不改回**。
3. **不声称触发器已对齐** —— 两侧各 0 ⇒ 「无对象可比」。
4. **不声称数据层已对齐** —— 本节是 **schema 层**。
5. **不写共享面** —— `docs/DRIFT/_cross/**` · `docs/DRIFT/global/**` · `docs/PhaseGoal-judge.md` · `docs/RoundPlan/**` **本 lane 一律不写**（合并后由**合并收口执行者**统一更新）⇒ 例如 §D.4 残留 ② 的索引差异在 `docs/DRIFT/global/lanes/L3-ddl.md` 有**共享面**的登记，本节**只复述、不修改**该文件。
