<!-- @new 2026-10-27 · `message-common` lane 台账 · **G2 波次工作树产出**（`align/g2-message-like-task`）· 基线 = `do-my-own-gh` @ `a282012` · **阶段 1 只读对账完成（2026-10-27）** -->

# `message-common` lane 台账 —— 「message ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/message/message-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（G1~G4 波次映射 · 9 条机制面轴 · 写权边界）
> ⇒ **本文件由 `G2` 波次工作树独占**；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/DRIFT/global/**`）本 lane 一律不写** ⇒ 合并后由整合者统一更新（AGENTS.md §5.3d）

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
| `DRIFT-MSG-V-016` | go-zero 三个 unary 拦截器（recover / breaker / CPU shedding） | `service/common` | ✅ **已全处置**（旧快照准确） | 链定义 `GoStyleRpcProtectionChain.java:61-78`；**消费点** `MessageRpcApplication.java:134-135`（`server.intercept(chain.build(...))`） | [`rpc.md`](rpc.md) |
| `DRIFT-MSG-V-025` | 熔断 / 降载三计数 | `service/common` | ✅ **已修**（旧快照准确） | `GoStyleSheddingStat.java:75-77`（三计数）+ `:84-96`（三个 increment） | [`rpc.md`](rpc.md) |
| `DRIFT-MSG-V-024` | DB / 未知异常保留 `UNKNOWN` | ⚠️ **本域** `service/message/message-rpc`（**旧列写 `service/common` = 错**） | ✅ **已修**（旧快照的「落点」列错误） | **消费点** `MessageRpcServiceImpl.java:166`（`Status.UNKNOWN.withCause(e)`） | [`rpc.md`](rpc.md) |
| `DRIFT-MSGAPI-010` | 成功 / 错误响应 `Content-Type` 缺 `charset` | `service/common` | ✅ **已修**（**旧快照「OPEN」过期**）—— 共享层 T-6 第二十五轮已落码 | `GoStyleJsonContentTypeAdvice.java:127`（常量）+ **消费点** `:151`（`response.getHeaders().set(CONTENT_TYPE, ...)`）；生效链 `AutoConfiguration.imports:2` → `message-api/pom.xml:62-66` | [`api.md`](api.md) |
| `DRIFT-MSGAPI-011` | 状态行缺 reason phrase | 🔴 **容器层**（Tomcat `Http11OutputBuffer`）—— **既非共享层也非本域**（旧列写 `service/common` = 错） | 🔴 **框架限制，不可修**；已登记 `SD-2026-10-25-CAND-14` | Go 标准库 `net/http/server.go:1602-1606`（`if text := StatusText(code); text != ""` → 写 code + 空格 + text）+ `net/http/status.go:81`（`func StatusText`）；RAG 侧 `HttpMiddleware.java:907-921`（`resp.setStatus(sc)`，**无 reason**）；⚠️ 实测 **Tomcat 11.0.24 的 `Response` 已移除 `setStatus(int,String)`**（`javap`：9.0.63 有、10.1.52 与 11.0.24 **只剩 `setStatus(int)`**）⇒ 应用层无路可走 | [`api.md`](api.md) · [`global/common-tails.md`](../global/common-tails.md) |
| `DRIFT-MSGAPI-013` | 缺 `Traceparent` | `service/common` | ✅ **已修**（**旧快照「OPEN」过期**）—— 共享层 T-8 第十九轮已落码 | `GoStyleTraceparentFilter.java:188-191`（`@Component` + `@Order(+3)`）+ **消费点** `:270-283`（Extract→Start→Inject）；本域接线 `MessageApiApplication.java:63-66`（扫 `com.rag.common`）；Go 出处 `go-zero@v1.10.2/rest/handler/tracehandler.go:61` | [`api.md`](api.md) |
| `DRIFT-MSG-V-029` | `like-rpc` 的 Flyway baseline 缺陷 | `service/like/like-rpc`（**别的域**） | ✅ **已修**（2026-10-25 global X-1）⇒ **本跨域待办可关闭** | `like-rpc/application.yml:78` `baseline-version: 0` + `:81` 独立账本表名 | [`rpc.md`](rpc.md) |

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

> **A3 已预填锚点订正（2026-10-27 核）** —— 共享层 6 个 form 类已落码（X-6 第十四轮）；本域 `mode=envelope` 与 Go `messagecenter.go:53`（注册 `httpx.SetErrorHandler` ⇒ 200 + 业务信封）**同向** ✅。
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

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 本轮**无 L4**，§6 列了 3 条待跑
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **7 条全部重核，5 条过期快照已订正**
- [x] §3 的域侧修法**落码 + 守门**（阶段 2；D-1 / D-3 = `2049a08`；D-2 = 端口段治理，**7 类全绿**）
- [x] §4 的派单**登记**（本文件 §4 FB-01）＋ **实时轨已发** `session send` 给 `G4Common`（`mvs_e8063ac67c654150ab7797b8116ef0a1`）
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/DRIFT/global/**` ⇒ 合并后由整合者统一更新）

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
- 但我域 9 模块里**唯一的 `*IT`** 是 `task-rpc` 的 `TaskRpcFatJarArtifactGateIT`（**5 条**，第十七轮 +1）⇒ ⇒ 它**只在 `verify` 阶段跑** ⇒ ⇒ **同一 9 模块的 `test` + `verify` 口径 = 995 + 5 = 1000** ⇒ ⇒ **口径必须写清**（本域自身数字不受影响，但全域合计会差 5）。

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
- ⇒ ⇒ **顺带**：global 的登记里用的是 **裸 basename**（`breakerhandler.go:19-44`）⇒ ⇒ 加上前缀后**越界判定会更准**（裸 basename 靠 basename 索引，可能匹配到别的库同名文件）

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
- 🆕 **加一条产物层守门** `fatJarCarriesSharedLayerOrderingBridge`（like 域）＋ **task 域同款** `TaskRpcFatJarArtifactGateIT.fatJarMustCarrySharedLayerOrderingBridge` ⇒ ⇒ **把这条潜伏地雷变成可捕获的失败** ⇒ ⇒ **期望值用独立字面量**（共享层那个全限定名，**不从被测代码取常量**）⇒ ⇒ 共享层改包名时如实转红。
- **验证（可复算）**：修后 `mvn -o -B -DargLine=-Xmx2g -am -pl service/like/like-rpc test -Dtest='LikeRpcArtifactClasspathGateTest,LikeRpcRuntimeBootGateTest,LikeRpcSpringContextWiringTest'` ⇒ ⇒ **13 条 / 0f 0e 0s · BUILD SUCCESS** ⇒ ⇒ **变异验证**：去掉 `-am` ＋ 删旧 fat jar 强制重建 ⇒ ⇒ **3 条里红 1 条**，**红在目标断言** `fatJarCarriesSharedLayerOrderingBridge:147` ⇒ ⇒ **恰好 1 条红**、另 2 条仍绿 ⇒ ⇒ **非假绿** ⇒ ⇒ 还原后 **sha256 与变异前逐字节一致**（`c3d547e7d5d8706c`）。

**⑤ 一条更深的教训**
- **「域侧副本」不只是冗余 —— 它可能在无意中充当「共享层落码前」的承重件** ⇒ ⇒ ⇒ **这正是本次缺陷的根因**
- ⇒ ⇒ **判据式** ⇒ ⇒ **收编共享层能力前，先问「这份副本在 fat jar / 独立构建路径上是不是唯一的承重件」** ⇒ ⇒ ⇒⇒ **本次就是：域内副本在 `BOOT-INF/classes` 里 ⇒ 它才是那条构建路径上唯一的承重件 ⇒ 删掉它之后，共享层那份在「测试 classpath」（reactor 的 `classes`）里带着，但在「产物」（fat jar 内嵌的 jar）里未必带着 ⇒ ⇒ 两个路径必须分别验**

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

