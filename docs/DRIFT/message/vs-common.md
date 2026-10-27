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
| **A1** | 超时 / deadline | rpc：`MessageRpcApplication.java:85-86`（`@Import DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator`）、`:75-77`、`:134-135`（链装配）；`message-rpc/application.yml:225-226` `rpc-timeout: 2s`<br>api：`MessageRpcClient.java:87`（`GoStyleRequestDeadline.applyTo`）；`message-api/application.yml:226-227` `http-timeout: 3s`· 🔴 **2026-10-27 新增**：`MessageJwtAuthFilter`（+6）—— **3s 预算的作用域**（验签在预算之外，= `DRIFT-MSGAPI-014`）<br>SQL 取消：`DeadlineQueryTimeoutInjector.java:139-145` | ✅ 一致（2s=2000ms / 3s=3000ms 毫秒精度一致；`DEADLINE_EXCEEDED` 文案对齐 `RpcInterceptor.java:109`）<br>🔴 2026-10-27：**3s 预算的作用域也已对齐**（Go 的预算**不含**验签 —— 真源 `observability/http.go:30-34` 关掉原生 TimeoutHandler、改由 `server.Use` 的自建中间件计时 ⇒ 计时器在 `Authorize` **之后**起算；RAG 侧原把验签放在 `HandlerInterceptor`（预算内）⇒ 现已搬到 `MessageJwtAuthFilter`(+6) ⇒ **验签在预算外**，两侧一致） | L4（Go 腿活体探针：观测点 A `deadline_set=false` / B `deadline_set=true remaining=3s`；**非法 token ⇒ 401 且三个观测点一个都没打印** ⇒ Authorize 在所有 user middleware 之前） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | 服务端：`MessageRpcApplication.java:134-135` → `GoStyleRpcProtectionChain.java:65`(shedding)/`:72`(breaker)/`:77`(recover) **消费点**<br>客户端熔断：`GoStyleGrpcClientBreakerInterceptor.java:104-106`（`@Component` + `@GrpcGlobalClientInterceptor`）⇒ 被 `MessageApiApplication.java:63-66` 注册 | ✅ 一致（链序 recover→breaker→shedding→业务；Go `zrpc/server.go:144-167`） | L1 | 2026-10-27 |
| **A3** | HTTP 保护链 | 配置 `message-api/application.yml:121-169`（四道闸门 + `mode: envelope`）<br>共享层消费点：`GoStyleMaxConnsFilter.java:96`(+4) · `GoStyleBreakerFilter.java:77`(+5) · `GoStyleCpuSheddingFilter.java:63`(+6) · `GoStyleMaxBytesFilter.java:83`(+8) · `GoStyleTraceparentFilter.java:190`(+3) · `GoStyleFormValueLimitInterceptor.java:81-97` + `GoStyleFormLimitWebMvcConfig.java:42` · `GoStyleTomcatParameterLimitCustomizer.java:134` | 🟡 **OBS**（三条有界残留：① **form 闸门与鉴权先后** —— **已登记**为「有界残留·刻意不改」（[`global/cross-domain.md`](../global/cross-domain.md) 第十四轮 §⑤之五-1）⇒ **不重复派单** ② **路径归一** ⇒ 🟢 **共享层已落码**（FB-01 · G4 `182fc32`：`GoStylePathNormalizingFilter` 字节级移植 Go `path.Clean` + 自动配置注册点；🟢 Go 侧已 L4 活体坐实）但**未合入本树** ⇒ 合并后本域跑**真 Tomcat 探针**即可升 ✅ ③ **缺本域真实请求证据**（envelope 拒绝形状 / 2049 参数 / `..` 路径））<br>> 📌 **共享层落点（按符号记，不钉行号 —— G4 树 `182fc32` 的行号合并后会位移）**：FB-01 = `GoStylePathNormalizingFilter` ＋ `GoStylePathNormalizingAutoConfiguration`（唯一注册点）；FB-02 = `GoStyleFormLimitMode.SEMANTIC_REACHABILITY` / `REGISTERED_DEVIATIONS`；FB-03 = `GoStyleRpcProtectionChain.Layered` / `Composition` / `compose(props)` / `layerNames(chain)` ⇒ 共享层侧回归（G4 自测）：**52 模块 / 2442 条 / 0 failures / 0 errors / 4 skipped** / 21:59 min<br>🆕 **2026-10-27 订正（G4 第七次全量 · `5564c27`）**：**2442** 是**第六次**全量的数字 ⇒ **第七次 = 2457 条**（52 模块 / 0 failures / 0 errors / **4 skipped** / **20:28 min** / `MVN_EXIT=0`）⇒ ⚠️ **2457 是 G4 树（52 模块）的口径**，**不是本域数字** ⇒ 本域三域六模块 = **594 条**（`mvn test` 口径）
⇒ ⚠️ **合并后**本域需跑真 Tomcat 探针复核（路径归一）⇒ 那是**收口回归**窗口的事（§5.3e）

🟢 **共享层的 form 语义表已落码**（FB-02 · G4 `182fc32`：`SEMANTIC_REACHABILITY` 逐域取证表）⇒ **message 行 = `Mode.ENVELOPE`** ⇒ 与本域 `mode: envelope` **一致** ⇒ 本域**不在** `REGISTERED_DEVIATIONS` 里 ⇒ **不是**「已登记的例外」，是**真对齐**） | L1（静态） | 2026-10-27 |
| **A4** | 错误码 / envelope | `GlobalResponseAdvice.java:50-71` · `GlobalExceptionHandler.java:90-94`(401 空 body)`:213-218`(404)`:221-229`(405)`:281-288`(兜底 200+5001) · `GoStyleRouterErrorResponses.java:121-126` / `:133-136` | ✅ 一致（Go `service/message/api/messagecenter.go:45-51` SetOkHandler + `:53` SetErrorHandler ⇒ 恒 200）<br>🟡 唯一残留 = reason phrase（容器层，见 §1） | L1 | 2026-10-27 |
| **A5** | 端口治理 | 生产 `message-api/application.yml:36` `8892` · `message-rpc/application.yml:120-121` `8082`（与 Go `etc/messagecenter.yaml:3` / `etc/message.yaml:2` 一致）<br>✅ **阶段 2 已治理**：测试 3 处 `RANDOM_PORT` ⇒ **`DEFINED_PORT`** + 本域专用段 **21700–21799**（`service/message/message-api/src/test/java/com/rag/message/message/api/support/RealTomcatPort.java` · favorite 同款复制）<br>每个测试类另加 **1 条端口段守门** `bindsToDedicatedPortSegment`（断言 `@LocalServerPort` 落在 `FIRST`–`LAST`）<br>⚠️ **守门到底拦什么（本轮实测厘清，勿照抄错误说法）**：**改回 `RANDOM_PORT` 并不会红** —— 因为 `@DynamicPropertySource` 的 `server.port` **优先级高于** `webEnvironment` ⇒ 端口**仍然**落在本域段内 ⇒ 那种改法**其实无害**（只是白写了 `RANDOM_PORT`）。<br>⇒ 守门真正拦的是 **① 端口注入被拿掉**（实测：⇒ 落回 `message-api/application.yml` 的 **8892** ＝ <b>生产端口</b> ⇒ 与真实部署撞端口；实测红在 `bindsToDedicatedPortSegment`，报「实际=8892」）**② `pick()` 返回段外端口**（⇒ 落进 OS 临时段 49152–65535 ⇒ 与 Docker 发布端口同段）| ✅ **一致**（生产端口一致；测试端口已脱离 OS 临时段 49152–65535 ⇒ 不再与 Docker 发布端口同段）<br>🟡 **选段表归 `global` 协调**（本段只在本域登记） | L3（端口段断言 = 运行时证据） | 2026-10-27 |
| **A6** | errmsg 契约 | `MessageErrorCode.java:28-90`（22 码）· `MessageErrorMessages.java:53-74`（`codeMsg` 逐条）+ `:86-92`（未知码回落）· `MessageGrpcError.java:217-248`（`BizCodeFromError`，`:230-232` 先 CodeError 再 status） | ✅ 一致（Go `service/message/common/errmsg.go:27-49` / `:51-73` / `:88-93` / `:99-132` 逐条同） | L1 | 2026-10-27 |
| **A7** | proto 契约 | `service/message/message-proto/src/main/proto/message.proto:1-195` | ✅ 一致（**仅 option 块不同**：Go `option go_package` ↔ RAG `java_package` + `java_multiple_files`；3 enum / 20 message / 字段名·号·类型 / 9 rpc 全等） | L1 | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | `message-rpc/application.yml:66` `baseline-on-migrate` · `:102` **`baseline-version: 0`** · `:105` **`table: flyway_schema_history_message`** · `:103` `locations`<br>`V1__create_message_notification.sql:15-19`（`is_read BOOLEAN` / `extra JSONB` / `created_at TIMESTAMP`）· `V5__message_time_columns_timestamptz.sql:69-92`（8 列 → TIMESTAMPTZ + 5 列 DROP NOT NULL） | 🟡 **OBS**（时间列类型与可空性**已对齐**；🟡 `DEFAULT CURRENT_TIMESTAMP` 残留（Go 侧无默认）⇒ **已登记不修** —— 两侧 INSERT 均显式绑定该列 ⇒ 不可观测；本域 `extra` 是 jsonb ⇒ **jsonb⇒text 不适用**）<br>✅ 账本隔离：`check-flyway-ledger.py` **EXIT=0**（7 模块账本表名两两不同） | L1 / L3 | 2026-10-27 |
| **A9** | trace / otel | `Traceparent`：`GoStyleTraceparentFilter.java:188-191`（装配）+ `:270-283`（消费点）· 全局 provider：`AutoConfiguration.imports:1-2` → `GoStyleOtelAutoConfiguration.java:58-61` → `GoStyleOtelAgent.java:146`（`GlobalOpenTelemetry.set`）<br>`audit.*` 属性：**本域 Go 侧无自定义 span ⇒ N/A** | ✅ 一致（Go `rest/handler/tracehandler.go:61` `propagator.Inject`）<br>🟡 残留 **T-12**：「405 方法不匹配」出口 RAG 多带一个 `Traceparent` ⇒ **出口层级不同**（Go 在 router 层 `patrouter.go:66-72`）⇒ 共享层改不动 ⇒ 已登记 `SD-2026-10-25-CAND-15`<br>⚠️ `GoStyleOtelAgent.java:133-137`（装配失败只记 ERROR 并继续启动）⇒ 「有 Traceparent 头」**不是无条件成立** | L1 / L3 | 2026-10-27 |

> **A3 已预填锚点订正（2026-10-27 核）** —— 共享层 6 个 form 类已落码（X-6 第十四轮）；本域 `mode=envelope` 与 Go `messagecenter.go:53`（注册 `httpx.SetErrorHandler` ⇒ 200 + 业务信封）**同向** ✅。
> ⚠️ **共享层已验 ≠ 本域已验** ⇒ 本域仍缺**真实请求**证据（envelope 拒绝形状 / 2049 参数 / `..` 路径）⇒ 已列 §5 待办。

## 3. 域侧能自修的（**本域直接修**，不甩给 G4）

| # | 条目 | 现状（`文件:行号`） | 建议修法 |
|---|---|---|---|
| D-1 | **过期 javadoc（断言与事实相反）** —— `MessageRpcClient` 仍写「本类没有任何 client 拦截器」「`DRIFT-MSGAPI-004` 已登记未修」 | `service/message/message-api/src/main/java/com/rag/message/message/api/client/MessageRpcClient.java:60-68` | 事实：客户端熔断由共享层 `GoStyleGrpcClientBreakerInterceptor.java:104-106`（`@Component` + `@GrpcGlobalClientInterceptor`）承担 ⇒ 被 `MessageApiApplication.java:63-66` 扫 `com.rag.common` 自动注册 ⇒ **`DRIFT-MSGAPI-004` 已修** ⇒ 改写 javadoc（**只改注释，不改行为**），Go 出处照抄 `zrpc/internal/client.go:112-113`；⚠️ §3.3.2：注释里的「Go: …」断言必须带真实行号<br>✅ **阶段 2 已落码**（`2049a08`）：javadoc 订正为「客户端熔断**已接入**」+ 落点（共享层 `GoStyleGrpcClientBreakerInterceptor.java:103-105` + `MessageApiApplication.java:63-66`）+ 判据（共享层 `:336-345` ⇒ Go `zrpc/internal/codes/accept.go:9-17` 那六个 code）+ 隔离单位（共享层 `:286` ⇒ Go `breakerinterceptor.go:14`）+ 熔断打开的码（`GlobalExceptionHandler.java:275-279` ⇒ **1015 / "service busy"**）<br>⚠️ Go 侧行号本轮**逐条实测复核**（go-zero@v1.10.2：config.go:14 / client.go:112-113 / breakerinterceptor.go:13-18 / accept.go:9-17）<br>⚠️ **本轮不新增守门**：本域已有 `GoStyleGrpcClientBreakerE2ETest`（真 in-process server + 真 channel）⇒ 熔断**在链上**已有**运行证据**（like 域当时缺这条证据才补的 E2E —— 两域处置不同，理由就是这个） |
| D-2 | **A5 测试端口段** —— 3 个真容器测试类用 `RANDOM_PORT` | `MessageApiProtectionRealTomcatTest.java:47` · `MessageApiTimeoutRealTomcatTest.java:55` · `GoStyleGrpcClientBreakerE2ETest.java:56` | 域侧自修：把 `service/favorite/favorite-api/src/test/.../support/RealTomcatPort.java` **复制**到 `message-api/src/test/.../support/`（**不改 favorite**）⇒ `DEFINED_PORT` + 专用端口段（避开 favorite 的 21300–21399、探针的 21400–21599、OS 临时段 49152–65535）+ 双绑定自检<br>✅ **阶段 2 已落码**：新增 `service/message/message-api/src/test/java/com/rag/message/message/api/support/RealTomcatPort.java`（**21700–21799**）+ 3 个测试类改 `DEFINED_PORT` + `@DynamicPropertySource registry.add("server.port", RealTomcatPort::pick)` + 每类 1 条端口段守门<br>⚠️ **选段依据**：避开 favorite 的 **21300–21399**、favorite 探针的 **21400–21599**、favorite 脚手架的 **21950–21999**、OS 临时段 **49152–65535** ⇒ 取 **21700–21799**（like **21600–21699** / task **21800–21899**）<br>⚠️ **为什么各域各复制一份**：`service/common` **不发布 test-jar**（实测 `service/common/pom.xml` 无 test-jar 目标）⇒ 业务模块的 test scope **拿不到**共享层测试类 ⇒ 只能复制（与 favorite 域既定做法一致） |
| D-3 | **台账落点订正** —— §1 把 `DRIFT-MSG-V-024` 落点写成 `service/common`、把 `DRIFT-MSGAPI-011` 落点写成 `service/common` | 本文件 §1（已就地订正）· 另需订正 [`api.md`](api.md) 的同款表述 | 本轮已改本文件；`api.md` 的订正在阶段 2 落码时一并做（**同域写权内**）<br>✅ **阶段 2 已落码**（`2049a08`）：`api.md` 的 MSGAPI-011 归因由「共享层 · 归因待查」订正为「**容器层**（Tomcat）· 框架限制 · 不可修」—— 决定性事实本轮**实测 `javap`**：`tomcat-embed-core` **9.0.63** 有 `setStatus(int)` + `setStatus(int,String)`；**10.1.52 与 11.0.24 只剩 `setStatus(int)`** ⇒ 本项目（Boot 4.0.8 ⇒ `tomcat.version=11.0.24`）**应用层无路可走**<br>⇒ 已引用 `SD-2026-10-25-CAND-14`（🏷 候选，在 `docs/Intentional_Deviations/global/common-tails.md`）—— ⚠️ **该目录不在本 lane 写权内** ⇒ 本轮**只引用、不改** |
| D-4 | 🆕 **G3 预告（2026-10-27）· 删除本域的 MyBatis-Plus 排序桥** —— ⛔ **带闸门：必须等 G4 的共享层桥落码后才删** | 本域持有 `service/message/message-rpc/src/main/java/com/rag/message/message/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java`（**全仓 9 份域侧桥之一**，本轮 `find` 实测；共享层 `service/common` 命中数 = **0**）＋ 注册行 `service/message/message-rpc/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:1`（类注释 `:46` 自带「**什么时候可以删**」条件） | ⛔ **闸门（硬）**：**必须 G4 的共享层桥落码之后才删** ⇒ 否则「域侧删了、共享层还没加」⇒ MP 自动配置退避（`@ConditionalOnSingleCandidate(DataSource)` 找不到 DataSource）⇒ **不产 `SqlSessionFactory` ⇒ 启动失败**<br>⇒ 删后跑本域真上下文守门 ⇒ 判据 = `SqlSessionFactory` 在 + `@Import` 的 `DeadlineQueryTimeoutInjector` **真在执行链上**<br>⇒ 两座桥同向（都是 `after=DataSource, before=MP`）⇒ 并存期功能等价、**不需要跨树同一次提交**<br>⚠️ 本条是 **G3 的预告（非派单）** ⇒ 等 G3 正式派单再动手；🔴 **G4 台账目前查不到「排序桥」条目** ⇒ 共享层那一半的落码状态**待证**<br>🆕 **G3 口径订正后 · G2 独立核实（2026-10-27 03:3x）**：① **派单窗口 = 「G4 共享层桥已落码」这个事件**（不是「各域阶段 2」）⇒ 本域阶段 2 到了也**不删** ② **本域删除爆炸半径 = 2 件**（① 删桥类 ② 删 `AutoConfiguration.imports` 注册行）—— 本域**无任何测试引用该类名**（`grep` 实测：message/task 的桥类名只出现在自身与 imports）⇒ **「连带处理 javadoc」在本域为零**（类自己的 javadoc 随文件消失）③ **共享层桥对 api 侧的影响 = 零** ⇒ 实测 **10 个 api 模块的 classpath 上全部没有 MP**（⚠️ `task-api/pom.xml` 的那一处命中是注释里的「**刻意不引入**」清单 ⇒ **假阳性**）⇒ 共享层桥在 api 上下文里 = **零 bean** ＋ 悬空排序名 ⇒ 无副作用 ④ 共享层桥的 javadoc **别照抄各域的部分列举**（实测「同款」列举：message 列 **1** 个 / like 列 **4** 个 / task 列 **5** 个 ⇒ **实际 9** 个）⑤ 本域台账 `message/rpc.md:508` / `:1134`（把桥登记为「已确认的基础设施事实」）在删除后**一并订正**（**同域写权内**） |

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
   ⇒ ⚠️ **若仍红 ⇒ 不是新回归，是毒化未被覆盖 ⇒ 直接找 G4**
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
3. **A1 / A2 缺双侧活体**：Go 侧 2000ms 超时 + `codes.Acceptable` 六码判据（`zrpc/internal/codes/accept.go:9-17`）与 RAG 熔断器的对齐只做了源码对照。
4. **A9 的前提**：`GoStyleOtelAgent.java:133-137` 装配失败只记 ERROR 并继续启动 ⇒ 「响应带 `Traceparent`」不是无条件成立，需运行期确认。
5. **A8 的 Flyway 运行时形态**：`baseline-version: 0` 在**非空 schema** 下是否真的执行 V1–V5，只有真 PG 双侧起进程能定论。
6. **全局端口段表** —— 本域段 **21700–21799** 已登记在本文件 §2/A5；🟡 **全局端口段表归 `global` 收口域**（`docs/DRIFT/global/**` **不在本 lane 写权内**）⇒ 合并后由整合者统一登记（like 21600–21699 / task 21800–21899 同期登记）。

> 🟡 **共享面观察（本 lane 不写，登记待整合者处理）**：`docs/DRIFT/_cross/domain-common-lanes.md:85` 把 A9 定义为「span 属性（`audit.*`）」，但 `audit.*` 是 **admin/users 域专用**（`AdminSpans.java:91-93`）；like 域 Go 侧实际用 `query.*` / `cache.*` / `like.*` / `messaging.*` ⇒ **轴定义建议改为「按域取 Go 侧实际前缀」**，否则 like / message / comment 等域会被误判为 N/A。
