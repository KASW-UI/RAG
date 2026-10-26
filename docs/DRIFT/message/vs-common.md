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
| **A1** | 超时 / deadline | rpc：`MessageRpcApplication.java:85-86`（`@Import DeadlineQueryTimeoutInjector` + `SqlTimeoutStatusTranslator`）、`:75-77`、`:134-135`（链装配）；`message-rpc/application.yml:225-226` `rpc-timeout: 2s`<br>api：`MessageRpcClient.java:87`（`GoStyleRequestDeadline.applyTo`）；`message-api/application.yml:226-227` `http-timeout: 3s`<br>SQL 取消：`DeadlineQueryTimeoutInjector.java:139-145` | ✅ 一致（2s=2000ms / 3s=3000ms 毫秒精度一致；`DEADLINE_EXCEEDED` 文案对齐 `RpcInterceptor.java:109`） | L1 | 2026-10-27 |
| **A2** | gRPC 拦截器链 | 服务端：`MessageRpcApplication.java:134-135` → `GoStyleRpcProtectionChain.java:65`(shedding)/`:72`(breaker)/`:77`(recover) **消费点**<br>客户端熔断：`GoStyleGrpcClientBreakerInterceptor.java:104-106`（`@Component` + `@GrpcGlobalClientInterceptor`）⇒ 被 `MessageApiApplication.java:63-66` 注册 | ✅ 一致（链序 recover→breaker→shedding→业务；Go `zrpc/server.go:144-167`） | L1 | 2026-10-27 |
| **A3** | HTTP 保护链 | 配置 `message-api/application.yml:121-169`（四道闸门 + `mode: envelope`）<br>共享层消费点：`GoStyleMaxConnsFilter.java:96`(+4) · `GoStyleBreakerFilter.java:77`(+5) · `GoStyleCpuSheddingFilter.java:63`(+6) · `GoStyleMaxBytesFilter.java:83`(+8) · `GoStyleTraceparentFilter.java:190`(+3) · `GoStyleFormValueLimitInterceptor.java:81-97` + `GoStyleFormLimitWebMvcConfig.java:42` · `GoStyleTomcatParameterLimitCustomizer.java:134` | 🟡 **OBS**（三条有界残留：① **form 闸门与鉴权先后** —— **已登记**为「有界残留·刻意不改」（[`global/cross-domain.md`](../global/cross-domain.md) 第十四轮 §⑤之五-1）⇒ **不重复派单** ② **路径归一缺失** ⇒ **派单 FB-01** ③ **缺本域真实请求证据**（envelope 拒绝形状 / 2049 参数 / `..` 路径）） | L1（静态） | 2026-10-27 |
| **A4** | 错误码 / envelope | `GlobalResponseAdvice.java:50-71` · `GlobalExceptionHandler.java:90-94`(401 空 body)`:213-218`(404)`:221-229`(405)`:281-288`(兜底 200+5001) · `GoStyleRouterErrorResponses.java:121-126` / `:133-136` | ✅ 一致（Go `service/message/api/messagecenter.go:45-51` SetOkHandler + `:53` SetErrorHandler ⇒ 恒 200）<br>🟡 唯一残留 = reason phrase（容器层，见 §1） | L1 | 2026-10-27 |
| **A5** | 端口治理 | 生产 `message-api/application.yml:36` `8892` · `message-rpc/application.yml:120-121` `8082`（与 Go `etc/messagecenter.yaml:3` / `etc/message.yaml:2` 一致）<br>🟡 测试仍用 `RANDOM_PORT` 3 处：`MessageApiProtectionRealTomcatTest.java:47` · `MessageApiTimeoutRealTomcatTest.java:55` · `GoStyleGrpcClientBreakerE2ETest.java:56` | 🟡 **OBS**（生产端口一致；测试端口段未治理 ⇒ **域侧自修**，见 §3） | L3 | 2026-10-27 |
| **A6** | errmsg 契约 | `MessageErrorCode.java:28-90`（22 码）· `MessageErrorMessages.java:53-74`（`codeMsg` 逐条）+ `:86-92`（未知码回落）· `MessageGrpcError.java:217-248`（`BizCodeFromError`，`:230-232` 先 CodeError 再 status） | ✅ 一致（Go `service/message/common/errmsg.go:27-49` / `:51-73` / `:88-93` / `:99-132` 逐条同） | L1 | 2026-10-27 |
| **A7** | proto 契约 | `service/message/message-proto/src/main/proto/message.proto:1-195` | ✅ 一致（**仅 option 块不同**：Go `option go_package` ↔ RAG `java_package` + `java_multiple_files`；3 enum / 20 message / 字段名·号·类型 / 9 rpc 全等） | L1 | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | `message-rpc/application.yml:66` `baseline-on-migrate` · `:102` **`baseline-version: 0`** · `:105` **`table: flyway_schema_history_message`** · `:103` `locations`<br>`V1__create_message_notification.sql:15-19`（`is_read BOOLEAN` / `extra JSONB` / `created_at TIMESTAMP`）· `V5__message_time_columns_timestamptz.sql:69-92`（8 列 → TIMESTAMPTZ + 5 列 DROP NOT NULL） | 🟡 **OBS**（时间列类型与可空性**已对齐**；🟡 `DEFAULT CURRENT_TIMESTAMP` 残留（Go 侧无默认）⇒ **已登记不修** —— 两侧 INSERT 均显式绑定该列 ⇒ 不可观测；本域 `extra` 是 jsonb ⇒ **jsonb⇒text 不适用**）<br>✅ 账本隔离：`check-flyway-ledger.py` **EXIT=0**（7 模块账本表名两两不同） | L1 / L3 | 2026-10-27 |
| **A9** | trace / otel | `Traceparent`：`GoStyleTraceparentFilter.java:188-191`（装配）+ `:270-283`（消费点）· 全局 provider：`AutoConfiguration.imports:1-2` → `GoStyleOtelAutoConfiguration.java:58-61` → `GoStyleOtelAgent.java:146`（`GlobalOpenTelemetry.set`）<br>`audit.*` 属性：**本域 Go 侧无自定义 span ⇒ N/A** | ✅ 一致（Go `rest/handler/tracehandler.go:61` `propagator.Inject`）<br>🟡 残留 **T-12**：「405 方法不匹配」出口 RAG 多带一个 `Traceparent` ⇒ **出口层级不同**（Go 在 router 层 `patrouter.go:66-72`）⇒ 共享层改不动 ⇒ 已登记 `SD-2026-10-25-CAND-15`<br>⚠️ `GoStyleOtelAgent.java:133-137`（装配失败只记 ERROR 并继续启动）⇒ 「有 Traceparent 头」**不是无条件成立** | L1 / L3 | 2026-10-27 |

> **A3 已预填锚点订正（2026-10-27 核）** —— 共享层 6 个 form 类已落码（X-6 第十四轮）；本域 `mode=envelope` 与 Go `messagecenter.go:53`（注册 `httpx.SetErrorHandler` ⇒ 200 + 业务信封）**同向** ✅。
> ⚠️ **共享层已验 ≠ 本域已验** ⇒ 本域仍缺**真实请求**证据（envelope 拒绝形状 / 2049 参数 / `..` 路径）⇒ 已列 §5 待办。

## 3. 域侧能自修的（**本域直接修**，不甩给 G4）

| # | 条目 | 现状（`文件:行号`） | 建议修法 |
|---|---|---|---|
| D-1 | **过期 javadoc（断言与事实相反）** —— `MessageRpcClient` 仍写「本类没有任何 client 拦截器」「`DRIFT-MSGAPI-004` 已登记未修」 | `service/message/message-api/src/main/java/com/rag/message/message/api/client/MessageRpcClient.java:60-68` | 事实：客户端熔断由共享层 `GoStyleGrpcClientBreakerInterceptor.java:104-106`（`@Component` + `@GrpcGlobalClientInterceptor`）承担 ⇒ 被 `MessageApiApplication.java:63-66` 扫 `com.rag.common` 自动注册 ⇒ **`DRIFT-MSGAPI-004` 已修** ⇒ 改写 javadoc（**只改注释，不改行为**），Go 出处照抄 `zrpc/internal/client.go:112-113`；⚠️ §3.3.2：注释里的「Go: …」断言必须带真实行号 |
| D-2 | **A5 测试端口段** —— 3 个真容器测试类用 `RANDOM_PORT` | `MessageApiProtectionRealTomcatTest.java:47` · `MessageApiTimeoutRealTomcatTest.java:55` · `GoStyleGrpcClientBreakerE2ETest.java:56` | 域侧自修：把 `service/favorite/favorite-api/src/test/.../support/RealTomcatPort.java` **复制**到 `message-api/src/test/.../support/`（**不改 favorite**）⇒ `DEFINED_PORT` + 专用端口段（避开 favorite 的 21300–21399、探针的 21400–21599、OS 临时段 49152–65535）+ 双绑定自检 |
| D-3 | **台账落点订正** —— §1 把 `DRIFT-MSG-V-024` 落点写成 `service/common`、把 `DRIFT-MSGAPI-011` 落点写成 `service/common` | 本文件 §1（已就地订正）· 另需订正 [`api.md`](api.md) 的同款表述 | 本轮已改本文件；`api.md` 的订正在阶段 2 落码时一并做（**同域写权内**） |
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

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次** —— 🟡 **本轮只做静态前置检查，未启动进程**（见 §6）⇒ 仍为待办
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（2026-10-27）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— 本轮**无 L4**，§6 列了 3 条待跑
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）—— **7 条全部重核，5 条过期快照已订正**
- [ ] §3 的域侧修法**落码 + 守门**（阶段 2；D-1 / D-2 / D-3）
- [x] §4 的派单**登记**（本文件 §4 FB-01）＋ **实时轨已发** `session send` 给 `G4Common`（`mvs_e8063ac67c654150ab7797b8116ef0a1`）
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/DRIFT/global/**` ⇒ 合并后由整合者统一更新）

## 6. 未验证项（本轮明确留下的缺口，**不写成已闭环**）

1. **A3 缺本域真实请求证据**：① envelope 拒绝形状（>2048 参数 ⇒ 是否 `200 + {code:5001,msg:"internal server error"}`）② `..` 路径归一 ③ 保护层 413/503 裸响应形状 ⇒ 需真 Tomcat 探针。
2. **FB-01 的「RAG 返 404」** —— 🟢 **Go 侧已由 G4Common 活体坐实**（go-zero 真 server，5 种路径变体全 200，2026-10-27）⇒ 剩下的是 **RAG 侧**的对应证据，**正在 G4 手里**（共享层归一器 + 真 Tomcat 守门）⇒ 本域只需在收口回归时复核。
3. **A1 / A2 缺双侧活体**：Go 侧 2000ms 超时 + `codes.Acceptable` 六码判据（`zrpc/internal/codes/accept.go:9-17`）与 RAG 熔断器的对齐只做了源码对照。
4. **A9 的前提**：`GoStyleOtelAgent.java:133-137` 装配失败只记 ERROR 并继续启动 ⇒ 「响应带 `Traceparent`」不是无条件成立，需运行期确认。
5. **A8 的 Flyway 运行时形态**：`baseline-version: 0` 在**非空 schema** 下是否真的执行 V1–V5，只有真 PG 双侧起进程能定论。

> 🟡 **共享面观察（本 lane 不写，登记待整合者处理）**：`docs/DRIFT/_cross/domain-common-lanes.md:85` 把 A9 定义为「span 属性（`audit.*`）」，但 `audit.*` 是 **admin/users 域专用**（`AdminSpans.java:91-93`）；like 域 Go 侧实际用 `query.*` / `cache.*` / `like.*` / `messaging.*` ⇒ **轴定义建议改为「按域取 Go 侧实际前缀」**，否则 like / message / comment 等域会被误判为 N/A。
