<!-- @new 2026-10-27 · `comment-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G3 波次工作树（`align/g3-article-comment-favorite-hot`）**  · **2026-10-27 03:0x G3 阶段 1 复核轮：§1 状态复核 ＋ §2 九轴逐轴填实 ＋ §3 自修项 ＋ §4 派单项（全部带核对日期与证据强度）** -->

# `comment-common` lane 台账 —— 「comment ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/comment/comment-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G3` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `comment-common`（**原第 2/10 条**） |
| **现分支 / 工作树** | **`align/g3-article-comment-favorite-hot`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot` |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/comment/{comment-api, comment-rpc, comment-common, comment-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) · [`proto.md`](proto.md) |
| **写权** | ✅ 可写**本域** `service/comment/**` ＋ `docs/DRIFT/comment/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |
| **本轮复核口径** | **核对日期 2026-10-27** · 阶段 1（**全只读对账**，不落任何代码）· 真源 = `Sea-RideTheWind_golang`（`go.mod:17` 锁 go-zero **v1.10.2**）· **证据强度上限 L3**（本域**无**域级双侧活体探针 —— 仓内 `tools/*-probe/` 的域级实例**只覆盖 favorite**） |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取 + 同日复核**）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `D8` / `D9` | = global 台账 **E65**（出向 gRPC 不继承请求 deadline）/ **E66**（Redis 写不绑 deadline） | `service/common`（`RpcInterceptor` / Redis 拦截器） | 🔴 **OPEN**（两侧同编号 ⇒ **销账别当两条**）<br>**E65 本轮复核确认仍 OPEN 且 comment 域受害**：`comment-rpc/.../client/ArticleRpcClient.java:65-72` 与 `client/MessageRpcClient.java:63-83` **裸调**（无 `withDeadlineAfter`）⇒ 出向零期限（核对日期 **2026-10-27**） | [`rpc.md`](rpc.md) |
| `E30` / `E38` / `E46` / `E47` | **只读指针（归属 global）**：超时未继承客户端 deadline / 业务层吞掉「SQL 被 deadline 取消」等 | `service/common` | 🟡 **只读**（按**编号**指，不按行号） | [`rpc.md`](rpc.md) |
| `PIT-GLOBAL-051` | 与「SQL 被 deadline 取消」同族的踩坑记录 | `service/common`<br>📍 **可复算指针（本次补）**：`docs/Pitfalls/global/cross-domain.md:2010`（`## PIT-GLOBAL-051 …`）· 同文件 `:2013`（旧号 `PIT-GLOBAL-051` ⇒ 新号 `PIT-GLOBAL-51`，**同一条**）<br>⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **🟡 只读指针** ⇒ **现行态 = 🟡 只读指针（判定不变）** · 依据：`docs/Pitfalls/global/cross-domain.md:2010`（该坑状态 **🔴 仍生效** · 核对日期 **2026-10-29**）· 核对日期 2026-10-30 ⇒ ⇒ **「与『SQL 被 deadline 取消』同族」这一定性复核成立**（该坑正文 = 「登记『某域把异常改写了』之前 ⇒ 必须逐条核 Go 侧对应行号」）⇒ ⇒ **落点在 `service/common` ⇒ 归 global 单写权 ⇒ 本域只读不写** ⇒ **不改判**；⚠️ **本条原为 PTRLESS（段内无任何 `文件:行号` ⇒ 结论不可复算）⇒ 本次补上指针** | 🟡 只读指针 | [`rpc.md`](rpc.md) |
| `SD-2026-10-24-CAND-08` | 保护层覆盖范围：**未注册路径**上的 MaxBytes（Go 的 chain 只在 `bindRoute` 内拼装 ⇒ 服务端从不返回 413） | —<br>📍 **可复算指针（本次补）**：`docs/Intentional_Deviations/comment/api.md:172`（`## SD-2026-10-24-CAND-08：…` · 状态 **✅ 仍成立** · 核对日期 **2026-10-29**）· 域侧处置 `docs/DRIFT/comment/api.md:186`（✅ **已签字** ⇒ 代码零改动）<br>⚠️ **2026-10-30 真实性复核（W-D lane）**：原判 **✅ 已签字偏离** ⇒ **现行态 = ✅ 已签字偏离（判定不变）** · 依据：Go `go-zero@v1.10.2/rest/engine.go:101-106`（`bindRoute` 内才 `buildChainWithNativeMiddlewares` ⇒ **未注册路径不经 MaxBytes** ⇒ 服务端**从不返回 413**）· RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxBytesFilter.java:83`（`@Order(Ordered.HIGHEST_PRECEDENCE + 8)` ⇒ **全局 servlet Filter** ⇒ 未注册路径也过 ⇒ **413**）· 核对日期 2026-10-30 ⇒ ⇒ **两侧差异仍在（Go 从不 413 / RAG 413）⇒ 偏离对象未消失 ⇒ 签字仍有效** ⇒ ⇒ **RAG 是更严/更安全的一侧 ⇒ 不重复质疑、代码零改动** ⇒ **不改判**；⚠️ **本条原为 PTRLESS ⇒ 本次补上指针** | ✅ **已签字偏离**（2026-10-24 用户 Jason）⇒ **不重复质疑** | [`api.md`](api.md) |
| `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（CD-6） | ✅ **已落地**<br>✅ **本域侧已验**（核对日期 **2026-10-27**）：`comment-api/.../application.yml:125` = `mode: envelope` ⇒ **与 Go 同向**（Go 侧 `service/comment/api/commentservice.go:47` **注册了** `httpx.SetErrorHandler` ⇒ 解析失败走业务信封 ⇒ **200 + `{code:5001}`**；实测全仓**仅** comment 与 hot 两个 api 注册） | [`api.md`](api.md) <br>⚠️ **2026-10-30 活体对拍（live-parity 轮）**：原判**保留不改**；本轮对该条目点名的活体探针实测 ⇒ `tools/difftest/ddl-parity.sh` ⇒ **EXIT=2**（2026-10-30 03:34:15 · 2s）—— 🟡 **探针判定为「环境问题」**（结论不可用）；覆盖断言：（未抓到结论行）。⚠️ **退出码只证「该探针自己的断言」成立，不等于本条结论的全量成立**⇒ 本条其余断言仍以上列逐条依据为准。⇒ 证据明细见 [`docs/DRIFT/_cross/live-parity-2026-10-30.md`](../_cross/live-parity-2026-10-30.md)。|
| （域侧 · 非编号） | `comment-rpc` **缺 `baseline-version: 0`** ⇒ 起得来、首次查库才炸 | **域侧** `service/comment/comment-rpc/src/main/resources/application.yml` | ✅ **不缺**（核对日期 **2026-10-27** 实测）：`:109 baseline-version: 0` ＋ `:112 table: flyway_schema_history_comment` ⇒ **本域无此缺陷**（⚠️ 与 `article-rpc` / `favorite-rpc` 不同）⇒ 本条**销账** | — |
| （域侧 · 非编号） | `comment-rpc` 缺 `DeadlineQueryTimeoutInjector` 的 MyBatis-Plus 自动装配证明 | **域侧测试** `CommentSqlDeadlinePrecisionGateTest.java:130-142` | 🔴 **证明缺口**（见 §2 的 A1 与 §4 的 G4-1）：该测试走**手工** `new Configuration()` ＋ `addInterceptor(new DeadlineQueryTimeoutInjector())` ⇒ **绕开 Spring 与 MyBatis-Plus 自动配置** ⇒ **证明不了「`@Import` 的 Bean 被自动收集」** | [`rpc.md`](rpc.md) |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ ✅ **2026-10-27 复核轮已就地订正/确认 7 条**（`D8`/`D9` 的 E65 受害面 ＋ `DRIFT-HOTAPI-030` 本域侧已验 ＋ `baseline-version` **销账** ＋ 新增 MyBatis-Plus 装配证明缺口）。
> ⇒ **教训**：抽取快照的「落点」与「状态」两列**都要独立复核**，落点尤其不能照抄。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体** · 核对日期 **2026-10-27**）

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | 服务端：`comment-rpc/.../application.yml:303-304` `rpc-timeout: 2s` ＋ `CommentRpcApplication.java:91-100`（`server.intercept(...)`）；消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:75-82`；超时执行 `service/common/.../observability/RpcInterceptor.java:232-237`（`min(服务端预算, 客户端剩余)`）`:258-288`（到点 `close(DEADLINE_EXCEEDED)`）；SQL 侧 `service/common/.../persistence/DeadlineQueryTimeoutInjector.java:164-176`（三路径）`:207-229`（毫秒探测）；装配 `CommentRpcApplication.java:67-68`（`@Import`）；api→rpc 出向 `comment-api/.../client/CommentRpcClient.java:96-102`；**HTTP 侧 3s 预算作用域**（2026-10-27 新增）：`comment-api/.../auth/GoStyleJwtAuthFilter.java:93`（`@Order(+6)`，**只验签**）＋ `comment-api/.../auth/CommentJwtSignatureVerifier.java`（**只验签、不写响应**）＋ `comment-api/.../interceptor/CommentJwtAuthInterceptor.java`（**消费结果**）| 🔴 **漂移（限 rpc 出向）** —— ✅ **服务端一致**：Go `service/comment/rpc/etc/comment.yaml` **未配 `Timeout`** ⇒ 走 go-zero default **2000ms**（`zrpc/config.go:46`）⇒ 两侧同为 2s；Go 侧 `comment.go:33` `DisableNativeRpcTimeout` ＋ `:54` `NewUnaryServerInterceptor` ⇒ 唯一超时执行者，RAG 同构；`RpcInterceptor.java:232-237` 的「取小」语义 ≡ Go `context.WithTimeout` 的父子取早。<br>✅ **api→rpc 出向一致**：`CommentRpcClient.java:96-102` 取 `min(2000ms, 剩余预算)`。<br>🔴 **rpc→article-rpc / message-rpc 出向零期限**：`comment-rpc/.../client/ArticleRpcClient.java:65-72`（`stub.getArticle(...)` 裸调）与 `client/MessageRpcClient.java:63-83`（`stub.sendNotification(...)` 裸调）**都没有 `withDeadlineAfter`**（全域 grep 只命中 `comment-api` 的 `CommentRpcClient`）⇒ Go 侧 `zrpc/internal/client.go:115-117` **无条件**套 2s ⇒ **行为分叉**（Go 到点即失败不写；RAG 照跑会写）。<br>🔴 **附带缺口**：SQL deadline 的 MyBatis-Plus 装配链**无任何运行证据**（见 §4-G4-1）<br>✅ **HTTP 侧 3s 预算作用域已对齐（2026-10-27 · 本轮落码 · §3-C-6）**：Go 的计时器在 Authorize **之后**起算 ⇒ **验签在预算之外**；RAG 原先把验签内联在 MVC interceptor（预算**之内**）⇒ **真漂移** ⇒ ⇒ 本轮把验签搬进域内 `auth/GoStyleJwtAuthFilter`（`@Order(+6)` ⇒ 落在 `+3 Traceparent` **之内**、`+7 HttpMiddlewareFilter` **之外**）⇒ **验签不再计入预算**；守门 `CommentApiJwtVerificationScopeTest` **7 例 ＋ 变异验证 3/3 精确变红**<br>🆕 **Redis 连接池轴 ＋ 断连行为轴（2026-10-27 G3）** —— **池轴**见 §3-**C-9**（`min-idle 4→0` ＋ **`max-wait: 4s`**）⇒ 🔴 **池耗尽等待上限**（Go `PoolTimeout = ReadTimeout+1s` = 4s；不配 `max-wait` ⇒ commons-pool2 `DEFAULT_MAX_WAIT = -1ms` ⇒ **无限等** ⇒ 且 Lettuce 的 3s 命令超时**此时尚未起算**（计时器在 `CommandExpiryWriter.write()` 才起算，而借连接在写命令之前）⇒ ⇒ 光加池**比 Go 更糟**）⇒ **断连轴**：落点 **共享层** `service/common/.../redis/GoStyleRedisClientAutoConfiguration` 的 `REJECT_COMMANDS`（域内**零副本** ⇒ 共享层那份带 `@ConditionalOnMissingBean` ⇒ **域内一旦自己声明一个，共享层就退让** ⇒ ⇒ **这条的写权全在共享层**）⇒ **判定 = 🔴→✅ 断连快速失败**：Go 侧 go-redis `MaxRetries` 默认 3 ＋ 重试退避 ⇒ **断连后第一条命令 1708–1776 ms 失败**（4 轮实测）⇒ RAG 侧若用 Lettuce 默认 `DEFAULT` ⇒ **排队到 3s 命令超时**（实测 **3005 ms**）⇒ ⇒ **恰好撞 2s 服务端超时 ⇒ RPC 先超时 ⇒ 客户端看到 1015「服务繁忙」**；配了 `REJECT_COMMANDS` ⇒ ⇒ **1 ms 拒单**（实测）⇒ ⇒ **不撞服务端超时** ⇒ ⇒ **与 Go 的「快速失败」语义等价**（Go 的重试面另属他档，Lettuce 无重试 ⇒ **不混**）⇒ 守门 `CommentRedisDisconnectFastRejectGateTest` **2 条**（① customizer 在场且**没被遮蔽** ② 消费点读到 `REJECT_COMMANDS` ③ 活体 A/B） | **L1**（两侧逐行；「没有 `withDeadlineAfter`」是可静态判定的否定事实）；**HTTP 预算作用域侧 = L3**（真 Spring 上下文装配层 ＋ 行为层）<br>**🆕 池轴 = L3**（Go 侧 L1 逐行对照 ＋ RAG 侧真装配运行证据 ＋ **4 条变异全精确转红**；Go 侧无同时跑探针 ⇒ **不是 L4**）<br>**🆕 断连轴 = L4（RAG 腿，仓内可复现）＋ L4-一次性（Go 腿，仓外探针）** ⇒ ⇒ ⚠️ **不是「仓内可复现的双侧活体对拍」** ⇒ ⇒ Go 探针**未入仓**（`tools/**` 属 G4 写权面 ⇒ 已派单）⇒ ⚠️ **仍待验：断连时 gRPC 响应层客户端到底看到什么**（要真发一次 gRPC 调用）⇒ ⚠️ **断连轴测的是「池里有空闲连接」形态**，「池耗尽」形态由池轴的 `max-wait` 覆盖 ⇒ **两条不混** | 2026-10-27（本轮订正 **HTTP 预算作用域** ＋ 🆕 **Redis 池轴 / 断连轴**） |
| **A2** | gRPC 拦截器链 | 链定义 `service/common/.../protection/GoStyleRpcProtectionChain.java:61-78`（recover → breaker → shedding → 业务）；装配 `CommentRpcApplication.java:91-100`；client breaker `service/common/.../protection/GoStyleGrpcClientBreakerInterceptor.java:103-106`（`@Component` ＋ **`@GrpcGlobalClientInterceptor`** ⇒ **只能靠组件扫描生效**） | 🔴 **漂移（限 rpc 出向 client breaker）** —— ✅ **服务端链一致**：Go `zrpc/server.go:144-167`（Trace→Recover→Stat→Prometheus→Breaker→Shedding→Timeout 逐条按 `c.Middlewares.*` 装配）⇒ RAG recover→breaker→shedding→业务 ＋ 阈值（`cpuThreshold=900`／`cpuSource=SYSTEM`，`GoStyleProtectionProperties.java:151-168`）≡ `zrpc/config.go:47`；Go 侧 native timeout 被 `DisableNativeRpcTimeout` 置 0 ⇒ RAG 不挂 native timeout 也一致。<br>🔴 **comment-rpc 出向 client breaker 缺失**：`CommentRpcApplication.java:53` 的 `@SpringBootApplication` **无 `scanBasePackages`**（实测；`:54` 只有 `@ConfigurationPropertiesScan`）＋ `:56-76` 的 `@Import` 显式清单**不含** `GoStyleGrpcClientBreakerInterceptor` ⇒ 该 `@GrpcGlobalClientInterceptor` **不在容器里** ⇒ comment-rpc→article-rpc/message-rpc **无熔断**；Go 侧两个 client 的 breaker 默认开（`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）。<br>✅ **comment-api 侧在位**：扫 `com.rag.common`（`CommentApiApplication.java:68-71`）⇒ client breaker 生效 | **L1**（链定义点 ＋ 装配点 ＋ `@Component` 生效条件三处逐行读）；🟡 域内 `CommentRpcGrpcInterceptorWiringTest.java:149-155` 会起**真 gRPC server** 验服务端链，但**本轮未跑** ⇒ 不算 L4 | 2026-10-27 |
| **A3** | HTTP 保护链 | 共享层 `service/common/.../protection/{GoStyleMaxConnsFilter,GoStyleBreakerFilter,GoStyleCpuSheddingFilter,GoStyleMaxBytesFilter}.java`（靠 `comment-api` 扫 `com.rag.common` 生效）＋ `service/common/.../http/{GoStyleFormValueLimitInterceptor,GoStyleFormLimitWebMvcConfig,GoStyleTomcatParameterLimitCustomizer}.java`；域侧 `comment-api/.../application.yml:122-125`（`mode: envelope`）`:126-134`（四层阈值）`:44-47`（容器三旋钮） | ✅ **一致** —— 四层阈值与 Go tag 默认**逐条同值**（`rest/config.go:52-56`：`MaxConns=10000` / `MaxBytes=1048576` / `CpuThreshold=900`）且 comment-api **显式写出同样数值**；**拒绝形状**：RAG 四层为**裸状态码 + 空 body**（`GoStyleProtectionProperties.java:53-57`）≡ Go 四层同为裸 `w.WriteHeader(code)`（`breakerhandler.go:18-42` 裸 503 · `maxconnshandler.go:12,23,34` 裸 503 · `maxbyteshandler.go:10,19,22` 裸 413 · `sheddinghandler.go:22,34-42` 裸 503）；**相对顺序** MaxConns<Breaker<Shedding<MaxBytes ≡ `rest/engine.go:134-168`；**form 计数口径**：RAG `GoStyleFormValues` 数**非空值**（空值跳过）≡ Go `rest/httpx/util.go:44-45`（空值 `continue` 不计数）；form 上限 **2048** ≡ `rest/httpx/util.go:15` `maxFormParamCount = 2048`；容器旋钮 `max-parameter-count: 2048` / `relaxed-query-chars: [ ]` / `max-http-request-header-size: 1028KB`。<br>ℹ️ Go 另有 Trace/Log/Prometheus/Recover/Metrics 五层，RAG 无同构实现（Spring 无对应物）⇒ **不改变可观察契约** | **L1** | 2026-10-27 |
| **A4** | 错误码 / envelope | 共享层 `service/common/.../response/Response.java:14-17`（三字段、**无** `@JsonInclude`）＋ `service/common/.../http/GoStyleRouterErrorResponses.java:121-140`；域侧 `comment-api/.../advice/CommentGlobalExceptionHandler.java:79-83`（401 **空 body**）`:100-104`（业务异常 → 200 + envelope）`:170-193`（解析失败 → 200 + 5001）`:236-249`（404/405）`:273-280`（兜底 → 200 + 5001）；gRPC 载体 `comment-common/.../errmsg/CommentGrpcError.java:116-121,139-145,194-226` | ✅ **一致** —— Go `service/common/response/response.go:3-7`（三字段**无 omitempty** ⇒ `data:null` 一定出现）≡ RAG；业务错误恒 **HTTP 200 + `{code,msg,data}`** ≡ Go `commentservice.go:47-60`（ErrorHandler **恒 `http.StatusOK`**）；401/404/405 三种例外形状逐条对上（Go `rest/handler/authhandler.go` 裸状态码无 body ＋ `rest/router/patrouter.go:73,79-82`）。<br>ℹ️ **净差异仅 `grpc-status` 数值载体**：Go 把业务码直接写进 `grpc-status`（`codes.Code(5003)`）⇒ `grpc-status: 5003`；grpc-java 只能发 17 个标准码 ⇒ RAG 把业务码移到**自定义 trailer**（`rag-comment-biz-code`）、文案仍在 description ⇒ **业务码与文案对客户端仍无损**，但 `grpc-status` 字段的数值不可复原（`CommentGrpcError.java:32-57` 自记为平台强制偏离） | **L1** | 2026-10-27 |
| **A5** | 端口治理 | `comment-api/.../application.yml:30-31`（`server.port: 8888`）`:32`（`address: 0.0.0.0`）＋ `comment-rpc/.../application.yml:175-178`（`grpc.server.port: 9001`）`:38-39`（`web-application-type: none`）`:255-259`（management 暴露面） | 🟡 **OBS** —— ✅ **业务端口逐字一致**：Go `service/comment/api/etc/commentservice.yaml:2-3`（`Host: 0.0.0.0` / `Port: 8888`）＋ `service/comment/rpc/etc/comment.yaml:2`（`ListenOn: 0.0.0.0:9001`）；且 `comment-rpc/.../application.yml:183-194` 记录了 devh `discovery:///` **三斜杠**要求（`CommentRpcProductionAddressTest` 守门）。<br>🔴 **Go 的 `:9091` promhttp 暴露面 RAG 没有**：Go `comment/rpc/comment.go:38-41` 起独立 goroutine 暴露 `/metrics`（5 个 `comment_*` 向量，`internal/metrics/metrics.go:42-49`）；RAG comment-rpc 是 `web-application-type: none` ⇒ **无任何 HTTP connector**，且**未配** `management.server.port` ⇒ `management.endpoints.web.exposure.include` 在无 web server 时不可达。<br>**N/A（`RealTomcatPort` 子项）**：该工具类**只存在于** `service/favorite/favorite-api/src/test/.../support/RealTomcatPort.java` ⇒ comment 域测试全用 MockMvc、**未使用 RANDOM_PORT** ⇒ 该子项对本域不适用<br>🔴 **2026-10-27 G3 落码（本域 gRPC 测试端口段治理 · global §4.10.47 转派 ②③）**：本域**原先没有**段管理类 ⇒ ⇒ `CommentSpanWiringTest` 写死 `grpc.server.port=21500`（**落在 favorite 域的「探针段」21400–21599 内** ⇒ ⇒ 两边都以为对方不在自己段里）＋ 另外 3 个 `@SpringBootTest` 写 `grpc.server.port=0`（**OS 临时段 49152–65535 = Docker 发布口同段** ⇒ 环境性抖动）⇒ ⇒ **裁法（域内设计，G3 自裁）= 补一个 `RealGrpcPort` 同款**（不是「只迁这一个口」）—— 因为「4 处 OS 分配口改专用段」这条**本来就必须有段管理类** ⇒ ⇒ 新增 `service/comment/comment-rpc/src/test/.../support/RealGrpcPort.java`（**专用段 22600–22699** = global 段表**预分配给 comment 域**的段，2026-10-27 实测整段可绑 100/100）＋ 配套守门 `RealGrpcPortTest`（**4 例**，含「被占 ⇒ 判为不可用」的两组判别用例）⇒ ⇒ **5 处全部改走本段**（3 个 `@SpringBootTest` 的 `port=0` + `CommentSpanWiringTest` 的 `21500` + `CommentRpcRuntimeBootGateTest` 的 `freePort()`）⇒ ⇒ **第 5 处（global 点的 4 处之外）= `CommentRedisDisconnectFastRejectGateTest` 里的容器发布口**（`docker run -p 127.0.0.1:<port>:6379` 用的那个 `freePort()`）—— 它是**容器宿主口**不是 gRPC 口，但同属「落 OS 临时段」这一类 ⇒ ⇒ 一并迁 ⇒ ⚠️ **脚手架段用 `22900–22909`**（= comment 域自有的 Testcontainers 子段 `22880–22909` 的上 10 口；下 20 口 `22880–22899` 留给 Testcontainers 宿主口）⇒ ⇒ **不碰 global 的未分配预留段** ⇒ ⇒ **零跨域协调** ⇒ ⇒ **验证**：`mvn -o -pl service/comment/comment-rpc -am test -Dtest='CommentRpcGrpcInterceptorWiringTest,CommentRpcSlowThresholdGateTest,CommentRedisDisconnectFastRejectGateTest,CommentSpanWiringTest,CommentRpcRuntimeBootGateTest,RealGrpcPortTest'` ⇒ **19 条 / 0 failures / 0 errors / 0 skipped**（2026-10-27T23:43:18+08:00） | **L1**（yaml 静态）＋ **L3**（端口段治理部分：测试支撑类源码 ＋ 守门用例 ＋ 段可绑实测） | 2026-10-27 |
| **A6** | errmsg 契约 | `comment-common/.../errmsg/CommentErrorCode.java:29-116`（27 个枚举，声明序＝Go const 块序）· `CommentErrorMessages.java:40-75`（27 条文案 `LinkedHashMap`，序同 Go）`:83-89`（`getMsg` 未知码回退 `"FAIL"`）· `CommentGrpcError.java:116-121,139-145,194-226` · `CommentCodeError.java` | ✅ **一致** —— 27 个码值与文案与 Go `service/comment/common/errmsg/errmsg.go:37-65`（const）`:67-95`（`codeMsg`）**逐条同值同字**（含「系统内部错误」「评论区不存在」「评论不存在」「输入有误」）；未知码回退 `"FAIL"` ≡ Go `:97-103`（`codeMsg[Error]`）；`CodeError.Error()` 格式 `"ErrCode:%d, Errmsg:%s"` ≡ Go `:15-17`；gRPC 编码方式 ≡ Go `:33-35`（`NewGrpcErr`） | **L1**（两侧逐条对照）；📌 本域**无** errmsg 探针实例（`tools/errmsg-probe/` 只有顶层 article ＋ `favorite/`）⇒ **可复制 article 的骨架到 `comment/` 升 L4** | 2026-10-27 |
| **A7** | proto 契约 | `service/comment/comment-proto/src/main/proto/comment.proto` | 🟡 **OBS（表达层归一）** —— 逐条比对 **4 个 enum（值 0/1/2/3、0/1/2、0~8+99、0~4）＋ 13 个 message ＋ 全部字段名/字段号/类型 ＋ 7 个 rpc 方法** ⇒ **行为层 100% 一致**；差异仅 option/包名块：Go `proto/comment.proto:4` `package pb` → RAG `:3` `package comment`；`option go_package` → `java_package` ＋ `java_multiple_files`。<br>ℹ️ **`package` 归一会改 gRPC 方法全名**（`/pb.CommentService/CreateComment` → `/comment.CommentService/CreateComment`）⇒ wire 上方法路径不同 ⇒ 属 AGENTS.md §0 第 1 条说的「**proto `package` 归一（表达层）**」家族 ⇒ **对拍看到它变红先确认是不是签字项，不要当新回归去「修」**。⚠️ **但 comment 的 proto 也无机器回归网**（`check-proto-static.sh` 只覆盖 user/admin/task） | **L1** | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | 迁移 `service/comment/comment-rpc/src/main/resources/db/migration/V1..V8`；Flyway `comment-rpc/.../application.yml:87-112`（`:109 baseline-version: 0` ＋ `:112 table: flyway_schema_history_comment`）；jsonb `comment-rpc/.../mapper/JsonbTypeHandler.java:39-69`（`PGobject` ＋ `type=jsonb`）；守门 `CommentFlywaySharedSchemaGateTest.java:78-97`（断言 V1~V7 七张表真实存在）`:124-125`（断言 BASELINE 行的 version 必须是 `"0"`）`:165-169`（断言 `spring.flyway.baseline-version == "0"`） | ✅ **一致** —— 时间列**全部 `TIMESTAMPTZ`**（V1:27,28 / V2:22,23 / V3:18,19 / V4:24,25 / V5:34 / V6:24）✅；**jsonb 未降级为 text**：三处保持 `JSONB`（V3:17 `comment_content.meta`、V6:19 `payload`、V6:23 `delivery_envelope`）＋ `JsonbTypeHandler` ⇒ 与 Go `type:jsonb` 一致（⚠️ 轴提示写的「jsonb⇒text」**在本域不成立**）；长度/约束（`VARCHAR(32/64/128)`、`CHAR(64)`、唯一索引 `idx_target`/`idx_user_comment_like`/`idx_user_comment_report`/`uk_comment_fact_stream`、普通索引 `idx_target_root_time`）与 Go struct tag 逐条对上。<br>⚠️ **`DEFAULT CURRENT_TIMESTAMP` 残留**属 AGENTS.md §0 第 2 条列明的「**刻意保留**」项（不是漂移）。<br>✅ **`baseline-version: 0` 已在位** ⇒ 非空 schema 时 BASELINE 标记打在 version 0 ⇒ **V1 照常执行** ⇒ **无「V1 被跳过 ⇒ `subject` 表建不出来」的缺陷**（本条与 `article-rpc`/`favorite-rpc` 不同 ⇒ **销账**）。<br>ℹ️ **本域 `report_record` 表 RAG 有、Go 无** ⇒ 属**已签字偏离 `SD-2026-10-24-CAND-02`**（Go 侧 `AutoMigrate` 列表不含 `&ReportRecord{}` ⇒ 表从不被建出 ⇒ Go 侧举报功能实际报 `relation "report_record" does not exist`）⇒ **不是新回归，不要去「修」** | **L3**（迁移文件 ＋ yaml 静态实测）＋ L1（列级对照）<br>⚠️ **DDL 列级双侧活体对拍在本域未做**（`check-ddl-static.py --domain comment` ⇒ **EXIT=2「列集合不一致」= 那张签字表**，不是缺陷）<br>⚠️⚠️ **2026-10-30 真实性复核（W-D lane · 上句已过期，原句保留 + 订正）**：**默认口径**确实 `EXIT=2`（在已签字的 `report_record` 列集合差异处提前退出）⇒ 但**本域的 DDL 列级双侧活体对拍后来**用**补跑口径**做完了（`--rag-db` 指向一份「RAG 侧去掉 `report_record`」的同源副本库 ⇒ **工具与仓内文件零改动**）⇒ **`EXIT=1` / 23 处**（可空性 8 · DEFAULT 10 · 索引 2 · 约束 2 · 触发器 1 ⇒ 实质 21）⇒ ⇒ **本轴的 DDL 证据强度已从「L1（列级对照）」升到「L4（机器双侧活体对照，补跑口径）」** ⇒ ⇒ **逐条结论见本文件 §9.2 / §9.4**（逐维度 + 逐条 + 两侧 `文件:行号`）⇒ ⇒ ⚠️ **但 `report_record` 本身仍不可比**（Go 侧整表不存在 ⇒ **无样本** ⇒ 只能登记「已签字的 RAG 多一张表」，**不能**给「列级一致」结论）—— 这一点**不变**。核对日期 2026-10-30。 | 2026-10-27 |
| **A9** | trace / otel | 全局 SDK `service/common/.../observability/GoStyleOtelAutoConfiguration.java:51-83`（`BeanFactoryPostProcessor` ＋ `AutoConfiguration.imports:1` ⇒ **不经组件扫描** ⇒ comment-rpc / comment-api 都生效）；Kafka 消费 span `comment-rpc/.../service/CommentAuditListener.java:94-101`（`MqConsumer.traceConsumer(Context.current(), "comment-rpc", "AuditConsumer.Consume", attrs, …)`）；共享实现 `service/common/.../observability/MqConsumer.java:71-123`；Traceparent `service/common/.../http/GoStyleTraceparentFilter.java:188-191` | ✅ **两处漂移均已修（2026-10-27 阶段 2）** —— ① ✅ **`messaging.destination` 已改回 Go 的字面量 `"comment_audit"`**（修前是**未解析的占位符字面量** `attrs.put("messaging.destination", "${comment.kafka.audit-topic}")` ⇒ **Spring 的 `${}` 只在 `@Value`/`@ConfigurationProperties`/yaml 里被解析**，Java 字面量里的 `${}` **永远不会被替换** ⇒ span 上落的是这串占位文本，既不是 Go 的 `"comment_audit"` 也不是真 topic 名；⚠️ **同文件 `@KafkaListener(topics = "${comment.kafka.audit-topic}")` 却是正确用法** ⇒ 同一文件里一处解析、一处不解析的疏漏）：`CommentAuditListener.java` 已新增 `static final String AUDIT_SPAN_DESTINATION = "comment_audit"` ＋ Go 出处注释块（Go `rpc/internal/mqs/audit_consumer.go:34` 传字面量、`:70-77` 把它写进 `messaging.destination`；⚠️ **Go 的真 topic 是 `comment-audit-topic`，但 span 上写的是逻辑名** ⇒ 逐字对齐，**不「顺手改成更合理的真 topic 名」**）。<br>② ✅ **6 个业务 span 已补齐（2026-10-27 阶段 2 落码）** —— Go 在 `create/delete/manage/report/update_subject_state` 五个 logic 各起 span（`Action-Comment-Create` / `-Delete` / `-Manage` / `-Report` / `-UpdateSubjectState`，`service/comment/rpc/internal/logic/*.go:37-38` 起）并写 `audit.operator_id`/`comment_id`/`target_type`/`target_id`/`root_id`/`parent_id`/`reason`/`action_type`/`target_state`（**9 个不同属性名 / 全域 23 处写入**，`create_comment_logic.go:41-43,55-61` 等）**＋ 第 6 个 `GetComment` span**（`get_comment_logic.go:52-53`）其属性**无前缀**（`target_id`/`root_id`/`page`/`page_size`/`target_type`/`sort_type`，`:70-75`）⇒ RAG 侧此前生产代码 grep `Tracer|spanBuilder|startSpan|SpanBuilder|SetAttributes` **0 命中** ⇒ **连 span 名都没有**。<br>✅ **已落码（域内文件，不动共享层）**：新增 **`comment-rpc/.../service/CommentSpans.java`**（域内私有常量类 ＋ `traced` / `tracedWithErrorStatus` 两个入口 ＋ `enumName` 枚举语义助手）＋ `CommentDomainService` 的 **6 个方法**改为「开 span → 设属性 → 跑业务体」（业务体拆成 `doXxx` 私有方法，**span 生命周期覆盖整个 logic** ⇔ Go 的 `defer span.End()`）。<br>🔴 **三处「Go 侧的不一致」逐字保留（不「顺手统一」）**：① **tracer 名不统一** —— 5 个 `Action-*` 用 `"comment-rpc"`、而 `GetComment` 用 **`"comment.rpc"`（带点）**；② **属性前缀不统一** —— 5 个用 `audit.*`、`GetComment` 的 6 个**无前缀**（⇒ 常量分成两组，不可复用）；③ **状态语义不统一** —— Go 的 `span.RecordError`（otel-go v1.x）**只加异常事件、不改状态**，而 `get_comment_logic.go:85-86/101-102/110-111/117-118` **额外显式** `SetStatus(codes.Error, …)` ⇒ ⇒ 5 个 `Action-*` 失败时状态保持 `UNSET`、`GetComment` 失败时状态是 `ERROR`。<br>🔴 **「Go 记不记这个错误」的判据 = 业务码落在哪一段**（**不是**「有异常就记」）：Go 的 `RecordError` 精确落在 `result = "sys_fail"` 的 15 处分支，`biz_fail`（输入错/实体不存在/无权限）**一律不记** ⇒ 判据取自 Go `service/comment/common/errmsg/errmsg.go:35-48` 的**码段事实**（**5xxx = 系统类** · **1xxx/2xxx = 业务类**）⇒ **业务码 ≥ 5000 ⇒ 系统失败 ⇒ Go 会记**。<br>🔴 **`likeComment` 刻意不加 span**：Go 的 `like_comment_logic.go` **没有任何 otel 代码**（实测 `tracer.Start` 命中数 = 0）⇒ 加了会让 span 数从 6 变 7 ⇒ **那是对齐，不是遗漏**。<br>🟡 **一处已知表达层差异（已登记）**：Go 记的是**原始错误**（如 PG 的 err 文本），RAG 的业务异常在多数分支**没串联 cause** ⇒ **事件存在性一致、消息文本不同**（RAG 记的是 `ErrCode:5003, Errmsg:DB查询失败`）。<br>✅ **G4 交叉确认（2026-10-27）**：G4 独立复核后把这条登记为「交 comment 域侧自查的线索」⇒ **与我方 §3-C-3 是同一条**（已在域侧自修项里）⇒ **不派 G4**（域侧能自修的自修 · C3）<br>✅ **Traceparent 传播在位**（Go `rest/handler/tracehandler.go:61` `propagator.Inject(spanCtx, HeaderCarrier(w.Header()))` 确实写响应头） | **L3**（span 名/属性名/属性值/状态语义 = **L1 逐行对照 Go**；**运行证据 = L4**：`CommentSpanWiringTest` 自装 SDK ＋ 收集型 exporter ⇒ 断言**导出的 span**（不是「代码写成了这样」）⇒ **7/7 绿**，含「业务失败无异常事件」「`GetComment` 有异常事件 + ERROR 状态」「span 里不出现任何 `audit.*`」「`likeComment` 不产生 span」四条判别式断言）<br>（**C-2 的 `messaging.destination`** ⇒ L1 源码对照 ＋ 共享层 Kafka span 的既有路径） | 2026-10-27 |<br>🟢 **2026-10-28 · G3 裁定：G4 §4.10.64 ⑤ 的「域内 status 漂移」警告【对 comment 域不成立】，且【方向搞反】**（G4 原文保留，不改写）—— G4 写「域内 span 若只调 `recordException` ⇒ status 恒 UNSET ⇒ **与 Go 的 `RecordError` 漂移**」⇒ ⇒ **中间那一步是错的**：Go otel-go **v1.44.0** 的 `RecordError` **【不改】status**（接口契约 `go.opentelemetry.io/otel/trace@v1.44.0/span.go:47-51`「**as this method does not change the Span status**」＋ SDK 实现 `go.opentelemetry.io/otel/sdk@v1.44.0/trace/span.go:626-630` javadoc ＋ `:631-655` 实现**全程无 `SetStatus` 调用**）⇒ ⇒ **Go 侧 favorite/comment/article 的业务 span 若只调 `RecordError` ⇒ status 本就恒 UNSET** ⇒ ⇒ **「只调 `recordException`」在 Go 侧同样是 UNSET ⇒ 两侧一致 ⇒ 不漂移** ⇒ ⇒ **域侧零改动**（**不补 `setStatus`** —— 补了反而是引入 Go 侧不存在的 ERROR）。<br>⚠️ **唯一需要显式 `SetStatus` 的是 `GetComment`，而本域【已经逐字复刻了】**：Go `service/comment/rpc/internal/logic/get_comment_logic.go:85-86` / `:101-102` / `:110-111` / `:117-118` 四个错误分支**各自** `RecordError` ＋ `SetStatus(codes.Error, err.Error())` ⇒ ⇒ RAG `CommentSpans.tracedWithErrorStatus(...)`（**只给 `GetComment` 用**，其余走 `traced` ⇒ 无 `setStatus`）⇒ 逐条对应 ✅ ⇒ 且 `CommentSpanWiringTest.java:237-238`（业务失败 ⇒ **断言 `StatusCode.UNSET`**）＋ `:390-392`（GetComment 系统失败 ⇒ **断言 `StatusCode.ERROR`**）**两条判别式断言把两侧 status 语义钉死了** ⇒ ⇒ **网已张、语义已对齐** ⇒ 逐条证据 ⇒ [`api.md` §16.4.1](../favorite/api.md#1641-🟢-补证2026-10-28-go-侧-recorderror-也不改-status--两侧-status-语义同构--status-漂移不成立) | 2026-10-28 |

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

| # | 事项 | 落点 | 依据 / 修法 |
|---|---|---|---|
| **C-1** | ✅ **comment-rpc → article-rpc / message-rpc 出向加 deadline（2026-10-27 阶段 2 已落码）** | `comment-rpc/.../client/ArticleRpcClient.java`（新增 `articleRpc()`）· `client/MessageRpcClient.java`（新增 `messageRpc()`）＋ **新增** `comment-rpc/.../client/GoStyleClientCallTimeout.java`（域内 package-private 工具类） | **已落码**：两个出向出口都改成经 `GoStyleClientCallTimeout.withDeadline(stub)` 取 stub ⇒ **min(2000ms, 请求剩余预算)**，**无请求预算时硬套 2000ms**（`remainingMillis()` 返回 −1 ⇒ 取默认 2000）⇒ 与 Go 的「客户端超时是**拦截器级别**、与有没有 HTTP 超时中间件无关」一致（`zrpc/internal/client.go:113-117` ＋ `zrpc/internal/config.go:15` 的 `default=true` ＋ `zrpc/config.go:31` 的 `default=2000`；`service/comment/rpc/etc/comment.yaml` 未配 `Timeout` ⇒ 走默认值）。<br>⚠️ **落码前的前提已独立复核**：`GoStyleRequestDeadline.begin` 的**唯一主代码调用点**是 `service/common/.../observability/HttpMiddleware.java:304`（HTTP worker）⇒ **gRPC 服务端路径永不绑定** ⇒ `applyTo` 恒返回原 stub ⇒ 落码前**出向调用确实零期限**（真阳性）。<br>⚠️ **范式照抄同域现成实现**（`comment-api/.../CommentRpcClient.java:96-102`）⇒ **不发明第三种写法**；本域只是把它提到 rpc 侧的 package-private 工具类，供两个出口共用一处。<br>⚠️ **只解决「2s 封顶」这一半**；「继承服务端 deadline」那一半落点在共享层 ⇒ §4-G4-3（global `E65`，**不重复派**） |
| **C-2** | ✅ **`messaging.destination` 占位符未解析（2026-10-27 阶段 2 已落码）** | `comment-rpc/.../service/CommentAuditListener.java`（新增常量 `AUDIT_SPAN_DESTINATION` ＋ `attrs.put(...)`） | **已落码**：取值改成 Go 的字面量 **`"comment_audit"`**（新增 `static final String AUDIT_SPAN_DESTINATION` ＋ 注释块给出 Go 出处）。<br>**取值口径（本轮定案并已登记）**：Go `service/comment/rpc/internal/mqs/audit_consumer.go:34` 传的是 `commentMessageAttrs("comment_audit", key)` ⇒ `:70-77` 的定义体把它写进 `messaging.destination` ⇒ **Go 的 span 属性值是 `"comment_audit"`，而 Go 的真 topic 是 `comment-audit-topic`（`etc/comment.yaml:26,32`）** ⇒ ⇒ **span 属性的取值属于可观察行为**（Jaeger 里的 span 结构）⇒ **逐字对齐 Go，不能「顺手改成更合理的真 topic 名」**（那会与 Go 分叉）。⚠️ **仓内已有同款先例**：task-rpc 的 `TaskTraceAttributes`（同样是「逻辑名 ≠ topic 名」）。<br>⚠️ **修前的形态（真漂移）**：原写法 `attrs.put("messaging.destination", "${comment.kafka.audit-topic}")` 是**普通 Java 字符串** ⇒ `${...}` 只在 `@Value` / yaml 绑定时才会被求值 ⇒ **放在这里永远不会被求值** ⇒ span 上真的带着这串占位符文本 ⇒ 既不是 Go 的 `"comment_audit"`、也不是真 topic 名 ⇒ **两侧遥测不可比**。⚠️ **注意与 `@KafkaListener(topics = "${comment.kafka.audit-topic}")` 的区别**：那处的占位符**由 Spring 解析**，是对的 ⇒ 不要把两处混为一谈 |
| **C-3** | ✅ **comment 域 6 个业务 span 已补齐（2026-10-27 阶段 2 已落码）** | **新增** `comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentSpans.java` ＋ `CommentDomainService.java` 的 6 个方法 | **已落码**（域内文件，**不动共享层**、**不改 pom**）：<br>① **新增 `CommentSpans`**（照仓内先例 `service/users/admin/admin-rpc/.../AdminSpans.java:91` 的「域内私有 `ATTR_*` 常量类」范式）：2 个 tracer 名常量（`"comment-rpc"` / `"comment.rpc"`）· 6 个 span 名常量 · **9 个 `audit.*` 属性名** + **6 个无前缀属性名**（两组**分开**，因为 Go 侧 `target_id`/`target_type` 同时以带前缀与不带前缀两种形态出现）· `traced` / `tracedWithErrorStatus` 两个入口 · `isSystemFailure`（错误记录判据）· `enumName`（Go `EnumStringOf` 语义）。<br>② **6 个方法改为「开 span → 设属性 → 跑业务体」**：业务体拆成 `doXxx` 私有方法 ⇒ **span 生命周期覆盖整个 logic** ⇔ Go 的 `defer span.End()`（拆方法的理由写进了 javadoc）。<br>③ **`likeComment` 刻意不加**（Go 侧 `like_comment_logic.go` 实测 `tracer.Start` 命中数 = 0）⇒ javadoc 里写明「这是对齐不是遗漏」，防止后人补齐。<br>④ **判据 = 业务码落在哪一段**（AGENTS.md §3.3.1 的「判据必须能在真源上求值」）：Go 的 `RecordError` 精确落在 `result = "sys_fail"` 的 15 处分支，`biz_fail` 一律不记 ⇒ 判据取自 Go `errmsg.go` 的**码段事实**（5xxx 系统 / 1xxx·2xxx 业务）⇒ **码 ≥ 5000 ⇒ 记**。<br>⑤ **三处 Go 侧的不一致逐字保留**：tracer 名（带点 vs 不带点）· 属性前缀（`audit.*` vs 无前缀）· 状态语义（`RecordError` 不改状态 ⇒ 5 个 `Action-*` 失败时状态 `UNSET`；`GetComment` 显式 `SetStatus(Error)` ⇒ 状态 `ERROR`）。<br>⑥ **枚举取值语义逐字对齐**：Go 的 `in.ActionType.String()` / `in.Reason.String()` 走 `protoimpl.X.EnumStringOf` ⇒ **已知值 = proto 值名、未知值 = 数字字符串** ⇒ `CommentSpans.enumName` 专门对齐（Java 的 `name()` 会写成 `UNRECOGNIZED`）；而 `audit.target_state` 记的是**编号**（`int64(in.State)`）⇒ 用 `getStateValue()`。<br>⑦ **`GetComment` 的 `page`/`page_size` 记原始入参**（Go `:72-73` 用 `in.Page`/`in.PageSize`，**不是**归一后的值）。<br>✅ **守门**：`CommentSpanWiringTest`（**真 Spring 上下文 + 真 PG + 自装 SDK + 收集型 exporter**）⇒ 断言**导出的 span**（AGENTS.md §3.3.3：注解存在 ≠ 机制生效）⇒ **7/7 绿**，含 4 条判别式断言（业务失败**无**异常事件 · `GetComment` **有**异常事件且状态 `ERROR` · `GetComment` 的 span 里**不出现任何 `audit.*`** · `likeComment` **不产生** span）。<br>🟡 **一处已知表达层差异（已登记）**：Go 记原始错误文本、RAG 的业务异常多数分支**没串联 cause** ⇒ 事件存在性一致、**消息文本不同** ⇒ 不在本项修（改 cause 串联会动 `CommentExceptions` 的公共面，超出 C-3 范围）。<br>✅ **G4 已独立复核并交叉确认**（2026-10-27）⇒ 与 G4 台账 §4.4 的「跨域线索」是**同一条** ⇒ **不派 G4**，本域自修 |
| **C-4** | 🟡 **`:9091` metrics 暴露面** | `comment-rpc` 的部署形态 | Go `comment.go:38-41` 的独立 goroutine ⇒ RAG `web-application-type: none` ⇒ 无对应面 ⇒ 属**部署形态类**，与本域已登记的 U9 同款 ⇒ 需拍板（**不是代码缺陷**） |
| **C-5** | 🟡 **A6/A7 升 L4 的探针**（**2026-10-30 · G3：A6 半边 ✅ 已闭合 · A7 半边仍开**） | `tools/errmsg-probe/{go,java}` → 复制出 `comment/` | ✅ **A6 半边已完成（2026-10-30 · G3 · 实测 EXIT=0）** —— `tools/errmsg-probe/comment/`（`run-parity.sh` + `mutate-parity.sh` + `go/{errmsg_probe.go,main.go}` + `java/Probe.java` + `README.md`）**已在版本控制内**（`git ls-files` 命中）⇒ 本轮实跑 `GO_REPO=… bash tools/errmsg-probe/comment/run-parity.sh` ⇒ **EXIT=0**：真源 sha256 `9820df5a…` 与复制件一致 · Go 侧与 Java 侧输出 **137 行 sha256 同值 `514e3a71…`**（`CONST + TABLE + FALLBACK + CODEERR + CODEERRMSG + WIRE` 逐字节一致）· **OBS 段 3 条**（`obs_wire_grpcstatus_1012/2001/5003`）差异仍在登记状态<br>⇒ ⇒ **A6（错误码契约 / errmsg）= L4**（双侧同时跑）⇒ ⇒ **本项的 A6 半边销账**<br>🟡 **A7 半边仍开**：proto **消息体** wire 字节的域级双侧探针**本域仍缺**（`tools/proto-wire-probe/` 只有 `favorite/` ＋ `hot/` ⇒ **无 `comment/`**）⇒ 本域 A7 仍只有 `tools/difftest/comment/check-proto-parity.py` 的**静态**对照（未升 L4）<br>⚠️ **归属订正（2026-10-29 · G3 域内收敛自查 C）**：按 AGENTS.md §5.3f「**共享判据 = 依赖度数，不是目录位置**」⇒ `tools/errmsg-probe/comment/` **只被 comment 域引用** ⇒ **真私有** ⇒ ⇒ **G3 域侧落码合法**（原判「`tools/**` 是共享面 ⇒ 不在 G3 写权面」**已作废**，见 hot 域同款订正）⇒ ⇒ **A7 半边也是域侧自修项**，不是「登记给收口执行者」项 |
| **C-6** | 🔴 **comment-api 的 JWT 验签被计入 3s 预算**（Go 的计时器在 Authorize **之后**起算 ⇒ 验签在预算**之外**） | `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/interceptor/CommentJwtAuthInterceptor.java`（把验签拆出去）＋ **新增**同域 `@Order(+6)` Servlet Filter（**只验签、只写 request attribute、绝不写响应**） | **G1 同步（2026-10-27）· 前提已由我方独立复核成立**：<br>① **Go 侧执行序（真源证据 · go-zero v1.10.2 `rest/engine.go:bindRoute`）**：原生中间件 → **`chn.Append(handler.Authorize(...))`（JWT 验签，`engine.go:74`）** → **再 append `ng.middlewares`（`server.Use` 注册的 observability 3s，`engine.go:110-111`）** ⇒ `chain.Append` = **更靠内** ⇒ ⇒ **Go 执行序：原生 → JWT 验签 → 3s 预算 → route 级黑名单 → handler** ⇒ ⇒ **验签在 3s 预算之外**。⚠️ `service/comment/api/commentservice.go:36` 的 `server.Use(NewHTTPMiddleware(...))` 虽是**全局**中间件，但 go-zero 把它 append 在 Authorize **内侧** ⇒ **「全局」≠「最外层」**（⚠️ 我第一轮按「全局包住 route 级」的直觉判断，差点把这条判成假阳性 ⇒ **必须读 go-zero 的 `bindRoute`，不能凭注册方式推链序**）。<br>② **RAG 现状**：验签内联在 `CommentJwtAuthInterceptor.preHandle`（`HandlerInterceptor` ⇒ DispatcherServlet **内**）⇒ 排在 `HttpMiddlewareFilter`（`@Order(+7)`，`service/common/.../spring/HttpMiddlewareFilter.java:37`）**之后** ⇒ ⇒ **验签被计入 3s 预算 ⇒ 与 Go 相反 ⇒ 真漂移**。<br>③ **共享层槽位（我方独立实测 `service/common/**`，不照抄 G1 的数字）**：+3 Traceparent（起 SERVER span）· +4 MaxConns · +5 Breaker · +6 CpuShedding · **+7 HttpMiddlewareFilter（3s 预算起算）** · +8 MaxBytes ⇒ 合法窗口 **[+3,+6]** ⇒ **四槽全被占满 ⇒ 只能并列 +6**（+6 严格小于 +7 ⇒ 顺序确定 ⇒ 并列无害）。<br>④ **修法（4 步）**：验签搬进本域 `@Order(+6)` Filter（**只验签、只写 request attribute、绝不写响应**）· **401 仍由本域 interceptor 抛**（失败**原样重抛异常对象** ⇒ `@ExceptionHandler` 仍管得到 ⇒ body 形状逐字不变）· 黑名单留在 interceptor（**本域无黑名单** ⇒ 无此步）· 守门断言**容器装配出的链**（`ServletContextInitializerBeans` 的装配结果），**不是**两个 `@Order` 数字。<br>⑤ **本域与 users 域的差异（抄配方前必须逐域自己核）**：**401 body = 空 body**（Go `authhandler.go:121` 的 `writer.WriteHeader(401)` **不写任何 body 字节** ⇒ 本域 javadoc 亦明写「与 users 域『401 + 完整 envelope』不同」）· **无黑名单** · **受保护路径 = 全部**（实测 Go `comment/api/internal/handler/routes.go` **只有一个** `AddRoutes` 块且带 `rest.WithJwt` ⇒ RAG 的 `COMMENT_V1_PATTERN` 全保护**与 Go 一致** ⇒ **无伴随的路径集合漂移**，本项落地面是干净的）。<br>⑥ **验收成本**：本域**没有真 Tomcat 测试** ⇒ 「413 压过 401」这条**响应级**语义**只能新写一条真 Tomcat 用例**来证（MockMvc 测不到响应头层）⇒ **比 favorite 域贵**。⚠️ **真启动须先起 rpc、等 READY 再起 api**（api 侧对 rpc 有 `immediate-connect-timeout: 3s`）。<br>⇒ **状态**：✅ **已落码（2026-10-27 · 本轮）** —— 落点全在本域 ⇒ **不派 G4**（域侧自修）。<br>⇒ **实施记录**：<br>① **新增** `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/auth/` **4 个类**（与 users/admin/favorite 域**同款配方**，四域同构便于跨域比对）：`CommentJwtAuthPaths`（**路径判据单源** —— 本域**无 public 路径**，受保护前缀 `/comment/v1`）· `CommentJwtVerificationResult`（失败带**异常对象**而非标志位 ⇒ 可**原样重抛** ⇒ `@ExceptionHandler` 仍管得到 ⇒ **401 空 body 逐字不变**）· `CommentJwtSignatureVerifier`（**只验签、不写响应、不查 Redis**；兼作本域 **401 的唯一构造点** `unauthorized()`，业务码 **1006**（`ERROR_TOKEN_RUNTIME`，**不是** 5004 —— 与 message 域不同，**不统一**））· `GoStyleJwtAuthFilter`（`@Order(+6)`，**只验签、只写 request attribute、绝不写响应**）。<br>② **改** `CommentJwtAuthInterceptor`（构造器改为注入 verifier，**不新增任何依赖**）：**消费** Filter 的验签结果 → 失败**原样重抛** → ⚠️ **「未注册路径 404」的 `handler instanceof HandlerMethod` 判据原样保留**（它是「要不要拦」的判据，与 Filter 的「要不要验签」判据**刻意不同源**，已写进代码注释）⇒ **本域无黑名单 ⇒ 无此步**（Go 侧 `routes.go:16-56` **没有** `rest.WithMiddlewares` ⇒ 两侧一致 ⇒ **没新增**）。<br>③ ⚠️ **`iat` 校验随验签一起搬进验签器**（Go `golang-jwt/jwt/v4@v4.5.2` 的 `MapClaims.Valid()` **校验** iat，jjwt **不校验** ⇒ RAG 此前比 Go 松）⇒ 既有守门 `CommentJwtTokenMatrixGateTest`（**11 例**）的**构造器调用**已同步改（`new CommentJwtAuthInterceptor(new CommentJwtSignatureVerifier(props))`）⇒ ⚠️ 该类**不经过 Filter** ⇒ 它现在走的是拦截器的**兜底（inline）分支** ⇒ **顺带把兜底路径纳入覆盖**（额外收益，已在测试 javadoc 写明）。<br>④ **守门** `CommentApiJwtVerificationScopeTest`（**7 例**，真 Spring 上下文 ＋ 容器装配层）⇒ **7/7 绿**；**变异验证 3/3 精确变红**：<br>　　· 变异 1：`@Order(+6)` → `+8`（跑到 3s 预算之后）⇒ **1 条红** = 「JWT Filter 必须排在 3s 预算 Filter **之前**」⇒ 精确命中（其余 6 例仍绿）；<br>　　· 变异 2：让 Filter 验签失败时**自己写 401 并短路** ⇒ **2 条红** = 「413 压过 401」（实测 401 ≠ 413）+「未注册路径仍 404」（实测 401 ≠ 404）⇒ 精确命中；⚠️ **本域与 favorite 域的守门差异**：comment 的 401 是**空 body** ⇒ Filter 裸写 401 **恰好也是空 body** ⇒ 「401 空 body」那条**不区分**（它只能证明「形状没变」，不能证明「谁写的」）⇒ ⇒ **本域判「Filter 不写响应」靠的是 413-over-401 与 404 两条**，favorite 域那条「401 信封形状」也能作证（它的 401 是三字段信封）；<br>　　· 变异 3：摘掉 Filter 的 `@Component` ⇒ **1 failure + 2 errors** = 来源标记由 `filter` 变 **`inline`**（证明兜底分支真会生效）＋ bean 不存在 ⇒ 精确命中。<br>⑤ **证据强度**：**L3**（真 Spring 上下文 ＋ **容器装配层**（`ServletContextInitializerBeans` 的排序输出）＋ 行为层）⇒ 按 AGENTS.md §0 读作「**在该证据强度下一致**」；**未跑双侧活体对拍**（Go 侧 comment-api 依赖 etcd + comment-rpc + Redis，本环境起不来）。⚠️ **本域无真 Tomcat 门禁** ⇒ 「413 压过 401」的**响应头层**语义在本域**只有 MockMvc 级证据**（favorite 域有真 Tomcat 守门可作对照）⇒ 该残余边界**已如实记在证据强度里**。<br>⑥ **本域两处差异已逐条保留**（**不「顺手统一」**）：**401 body 仍是空 body**（Go `authhandler.go:121` 不写任何字节）· **业务码仍是 1006**（不是 5004）· **未注册路径仍 404**（不被鉴权遮蔽）。 |
| **C-7** | ✅ **comment-rpc 的 Redis TCP 连接超时**（Go `DialTimeout` 5s vs Lettuce 默认 **10s** ⇒ 漂移 2 倍） | `service/comment/comment-rpc/src/main/resources/application.yml:149` ＋ **新增** `src/test/java/com/rag/comment/comment/rpc/config/CommentRpcRedisTimeoutContractTest.java` | **已落码（域侧自修，2026-10-27 · commit `0c6295c`）**：yml 加 `connect-timeout: 5s`。<br>**Go 出处（消费点）**：`service/comment/rpc/internal/cache/redis.go:17-19` `redis.NewClient(&redis.Options{Addr: Host})` ⇒ **只设了 Addr** ⇒ DialTimeout 未设 → go-redis v9.20.0 `options.go:338-340` ⇒ **5s**。<br>**RAG 侧**：Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` 字节码 offset 9→31 ⇒ **只有** `spring.data.redis.connect-timeout` 非空时才设 `SocketOptions.connectTimeout` ⇒ 不配 ⇒ Lettuce `DEFAULT_CONNECT_TIMEOUT = 10s`；且 `spring.data.redis.timeout` **只**喂命令超时（Boot 2/3 才是共用）。<br>**为什么是域侧、不派 G4**：共享层 `GoStyleRedisClientAutoConfiguration` **不碰** `socketOptions` ⇒ 连接超时由域内 yml 决定。<br>**守门**：**真容器**（真实 yml → 真 `LettuceConnectionFactory` → 断言**消费点**）**1/1 绿 ＋ 变异 3/3 精确变红**。⚠️ 本域 yml 开了 `lettuce.pool.enabled: true` ⇒ pom 必引 `commons-pool2`（已显式声明）⇒ 守门起真容器不会踩 `NoClassDefFoundError: GenericObjectPoolConfig`。<br>🔴 **可观察性最强**：comment-rpc 是**纯 gRPC（无 HTTP 预算兜底）** ⇒ 连接超时 10s vs 5s **直接**体现在 gRPC 调用耗时上。<br>📌 **G4 派单回执（2026-10-28 · G3）**：G4 第七封信把本条列为 **R5**（「你域的 yml 没有 `connect-timeout` ⇒ 连接超时 10s ⇒ 比 Go 的 5s 松 2 倍」）⇒ ⇒ **该派单在本域已过期**：本条落码（`0c6295c`，2026-10-27）**早于**派单 ⇒ ⇒ **不重做、不重派**。⇒ ⇒ **复验（2026-10-28）**：守门 `CommentRpcRedisTimeoutContractTest`（**真容器** ⇒ 真 yml ⇒ 真 `LettuceConnectionFactory` ⇒ 断言**消费点** `getSocketOptions().getConnectTimeout()` == 5s ＋ `getCommandTimeout()` == 3s）**绿** ＋ **变异 3/3 精确变红**（`expected: 5S but was: 10S`）⇒ ⇒ **判据活着 ⇒ 差异不存在**。⚠️ **版本差**：G4 测的是 **Boot 4.0.3 / lettuce-core 6.1.8** ⇒ ⇒ **本域树上是 Boot 4.0.8 / lettuce-core 6.8.2.RELEASE** ⇒ ⇒ **两个版本都验过**（lettuce `SocketOptions` 静态块 `ldc2_w long 10l → Duration.ofSeconds` ⇒ 默认 10s ✔；Boot 4.0.8 `LettuceConnectionConfiguration.createClientOptions` offset 9→31 = `getConnectTimeout()` → `SocketOptions.builder().connectTimeout(...)` ⇒ **只在属性非空时才设** ✔）⇒ ⇒ **是基线过期，不是版本差异**。 |
| **C-8** | ✅ **comment 域 fact 投递链路的 span 属性 = RAG 侧独有的无效代码（已删 · 2026-10-27）** | `comment-rpc/.../service/CommentFactDeliveryService.java`（删改动前 `:148` 取 span ＋ `:192-194` 写属性 ＋ `:6` import）＋ 守门加进 `comment-rpc/src/test/.../CommentRpcGoSemanticsGateTest.java`（**新增 3 条**） | **来源**：G4 的 **FB-04** 通报（「gRPC 业务 handler 执行期能拿到当前 server span」落码后，"有 span 的线程"变多 ⇒ 差异显形）⇒ **报给 G3**（G3 写权，G4 不代改）。<br>**定性**：**不是语义漂移** —— 两侧**都没有** `comment.fact.event_id` / `comment.fact.aggregate_version` ⇒ 行为层一致 ⇒ 是「看起来在工作、实际永远不生效」的代码（AGENTS.md §3.3.3 那一族）。<br>⚠️ **依据经一次订正（我自己写错过）**：初判写「Go 侧 fact 投递**根本不用 span**」⇒ **不准确** ⇒ Go 侧**有**这个 span，只是建在**共享层**：`service/common/communityfact/process.go:26` `otel.Tracer("sea.rtw.communityfact").Start(ctx, "community.fact_delivery.dispatch")`（全文 69 行、**零** `SetAttributes`）⇒ 且域内 `service/comment/rpc/internal/model/` 整包（**10** 个非测试文件）**零** span 命中 ⇒ Go 的 `DispatchFactOnce` 从不写属性。⇒ 教训见坑档 [`PIT-COMMENT-RPC-14`](../../Pitfalls/comment/rpc.md)。<br>**RAG 侧为什么也写不进去**：等价 span 在**共享层** `service/common/.../communityfact/Dispatcher.java:186`（**同名同 tracer** ⇒ **span 拓扑两侧已对齐**）；但共享层把 span 放进 **Context**（`:189` `ctx.with(span)`）而**从不 `makeCurrent`** ＋ 投递跑在**另一个线程**（`:172-176` `communityfact-dispatch` 单线程池、`:190` `supplyAsync`）⇒ OTel 默认 `ThreadLocalContextStorage` 是**普通** `ThreadLocal`（**不继承**）⇒ 投递线程上 `Span.current()` 恒为 **invalid span** ⇒ `if (span != null)` 恒真 ⇒ `setAttribute` **静默丢弃**。<br>**验证（已跑绿）**：门禁 **14** 条全绿（既有 11 ＋ 新增 3）· **变异精确变红**（把那两行**原样**加回 ⇒ **14 条里恰好 1 条红**，另两条仍绿 ⇒ 失败断言原文点名那两个属性）· 本域回归 **198** 条全绿（common **27** / api **52** / rpc **119** = 旧 **116** ＋ 新增 **3**）⇒ ⇒ **零行为变化**（删掉的本来就写不进去 ⇒ 生产行为不变）⇒ ⇒ 共享层**不需要**任何改动 |
| **C-9** | ✅ **comment-rpc 的 Redis 连接池参数口径**（`min-idle` **4 → 0** ＋ **新增 `max-wait: 4s`**） | `service/comment/comment-rpc/src/main/resources/application.yml:173-179` ＋ **新增** `src/test/java/com/rag/comment/comment/rpc/config/CommentRpcRedisPoolContractTest.java` | **已落码（域侧自修 · 2026-10-27 · G3）** —— 修 [`comment/rpc.md` §10](../comment/rpc.md) 登记的「形态已对齐、仅数值不对齐」。<br>**改动**：yml `min-idle: 4` → **`0`**（**精确对齐 Go**）＋ **新增** `max-wait: 4s`（`enabled` / `max-active: 64` / `max-idle: 16` 未动）；**pom 无需改**（`commons-pool2` 早已显式声明 ⇒ 本域**不存在** `hot-rpc` 那条「缺依赖 ⇒ 启动期 `NoClassDefFoundError`」的风险）。<br>**Go 出处（消费点）**：`service/comment/rpc/internal/cache/redis.go:17-19` `redis.NewClient(&redis.Options{Addr: Host})` ⇒ **只设 `Addr`** ⇒ `MinIdleConns` 未设 ⇒ `go-redis@v9.20.0/options.go:218-222` **无 default** ⇒ **0** ⇒ ⇒ ⚠️ **本域走 go-redis 直连，不是 go-zero** ⇒ **不是 8**（那是 like / favorite 域的口径）⇒ ⇒ 原先的 `4` **是对 Go 的偏离**。<br>🔴 **`max-wait` 是「必配」而不是「优化」**（全仓级共性缺陷）：Go `go-redis@v9.20.0/internal/pool/pool.go:1113` `p.semaphore.Acquire(ctx, p.cfg.PoolTimeout, ErrPoolTimeout)` ⇒ `PoolTimeout` 未设 ⇒ `options.go:380-386` `= ReadTimeout + 1s` = **4s** ⇒ 池耗尽 ⇒ **4s 后失败**；RAG 侧不配 ⇒ commons-pool2 2.12.1 `BaseObjectPoolConfig:59,68` `DEFAULT_MAX_WAIT = -1ms` ⇒ `GenericObjectPool:306` `negativeDuration ? idleObjects.takeFirst()` ⇒ **无限等**；🔴 且 **Lettuce 的 3s 命令超时救不了场** —— `CommandExpiryWriter.write(command)` 才起算计时器（实测 lettuce-core 6.8.2 字节码），而**借连接在写命令之前** ⇒ ⇒ 池耗尽时 3s 超时**根本没开始** ⇒ ⇒ **无限挂起**（Go 4s 失败）⇒ ⇒ **光有池反而比 Go 更糟**。<br>**守门**：**4 条**（`CommentRpcRedisPoolContractTest`）—— 起**真** `ApplicationContextRunner` ＋ Boot **真** `DataRedisAutoConfiguration` ⇒ 断言**真装配**的 `LettucePoolingClientConfiguration.getPoolConfig()`（**不读 yml 文本**）⇒ ④ 含**阳性对照**（commons-pool2 默认 `maxWait` = -1ms）。<br>**变异 4 条全部精确转红**：`min-idle: 0→4`（**回到本轮修之前的值**）⇒ 红在 ② 的 `minIdle` · 删 `max-wait` ⇒ 红在 ③ · `max-active: 64→65` ⇒ 红在 ② 的 `maxTotal` · `enabled: true→false` ⇒ 红在 ①。<br>⚠️ **证据强度 L3**（Go 侧 L1 逐行 ＋ RAG 侧真装配运行证据）⇒ **不是 L4**（Go 侧无同时跑探针；⚠️ **并发活体对拍**原记的阻塞理由「机器级 Docker 端口转发故障」**已于 2026-10-27 19:2x 实测不成立** ⇒ 现在只差「Go 侧同时跑的探针」）。<br>⚠️ **顺带订正一处措辞**（见 [`comment/rpc.md` §10.4](../comment/rpc.md)）：原稿「⛔ go-redis 无对应概念（只存 `PoolSize` + `MinIdleConns`）」**不准确** —— go-redis v9.20.0 **有 `MaxIdleConns`**（`options.go:224-228`，无 default ⇒ 0 ⇒ 消费点 `internal/pool/pool.go:1257` ⇒ **0 = 无上限**）⇒ 正确表述是「**有对应项，Go 取值 = 0（无上限）**」⇒ ⇒ `max-idle: 16` 属「**比 Go 更严**」的**可登记残留**，不是「语义无对应」。⚠️ **同款措辞错误也在 G2 的 `task/vs-common.md` A1 ⑦** ⇒ 已一并通报。 |

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> 🟡 **共享层 U+FFFD 反馈单只登记一次** ⇒ 见 [`comment/vs-common.md`](../../DRIFT/comment/vs-common.md) §4-**G4-4**（`service/common` 两个测试类，各丢 1 个汉字，纯注释）⇒ **本域不重复派单**。

> 派单走**实时轨**（`session send` ＋ 反馈单 7 项）＋ **正式轨**（本表）⇒ 两轨内容须一致（AGENTS.md §5.3e）。
> **回执规格（2026-10-27 双方议定 · G4 已写入 `docs/DRIFT/global/vs-common.md` §4.1，commit `6e3e612`）**：回执 **第 4 项 = 「字段本身写全」** ⇒ ① 跑没跑 ② **未跑时把理由写进第 4 项字段本身**（不写在正文里）③ 跑了则给 **模块数 / 用例数 / failures / errors / skipped / 耗时** ④ **三门禁结果**。> ⚠️ 另外三项不变：**① commit 号 ② 逐个 `文件:行号` 改动清单 ③ 触及的 A1~A9 轴 + Go 出处**。
> ⚠️ **落点必须独立核准**（不要照抄台账快照）。

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| **G4-1** | 🔴 **MyBatis-Plus 自动收集 `Interceptor[]` Bean 的机制** —— 全仓**无任何测试**证明 `@Import` 的 `DeadlineQueryTimeoutInjector` 真进了 MyBatis-Plus 执行链 | `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java` ＋ MyBatis-Plus 自动配置 | **实测缺口**：`CommentSqlDeadlinePrecisionGateTest.java:130-142` 走**手工** `new Configuration()` ＋ `configuration.addInterceptor(new DeadlineQueryTimeoutInjector())` ⇒ **绕开 Spring 与 MyBatis-Plus 自动配置**；`FavoriteSqlTimeoutProductionPathTest.java:161` 也是手工 `addInterceptor(PROBE)`（PROBE 是测试自己的探针）⇒ **两条测试都证明不了「`@Import` 的 Bean 被自动收集」**（AGENTS.md §3.3.3：注解存在 ≠ 机制生效）。⇒ 建议：在**共享层**补一条**真 Spring 上下文 + 真 mapper + 真 PG** 的测试，一次性为 10 个 rpc 域钉死该机制。**影响面**：全部 rpc 域（同一机制）。**是否阻塞本波**：**是**（它直接决定 A1 在 SQL 侧是否真生效） |
| **G4-2** | 🔴 **`GoStyleGrpcClientBreakerInterceptor` 在 rpc 侧无装配路径** | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:103-106` ＋ `GoStyleProtectionConfiguration.java:51-53` | 它是 `@Component @GrpcGlobalClientInterceptor` ⇒ **只能组件扫描**；而 rpc 进程**刻意不扫 `com.rag.common`**（`CommentRpcApplication.java:53` 无 `scanBasePackages`，`:56-76` 的 `@Import` 清单不含它）⇒ **comment-rpc 这类进程永远拿不到 client breaker**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）⇒ **出向调用无熔断**。⇒ 建议：共享层提供**一个 rpc 进程可用的装配入口**（例如把该拦截器改为可被 `@Import` 引入，或提供 `@Configuration` 供 rpc 侧显式导入），并补一条「rpc 侧出向调用确实经过 breaker」的**装配层**测试（AGENTS.md §3.3.3）。**影响面**：全部 rpc 域 ＋ 它们的出向调用。**是否阻塞本波**：否 |
| **G4-3** | 🔴 **`E65`**：出向 gRPC **不继承请求 deadline**（服务端路径恒 no-op）＋ 缺**共享的客户端 2s 封顶** | `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:110-120` ＋ `service/common/.../observability/HttpMiddleware.java:304`（`begin` 的唯一主代码调用点，只在 HTTP worker） | **两侧都应是 Y**：Go 侧 zrpc client **无条件**套 2s（`zrpc/internal/client.go:115-117`）且父 deadline 经 `depCtx` 传播 ⇒ 共享层应让 **gRPC 服务端路径也把 deadline 写进 `GoStyleRequestDeadline`**（或让 client 直接读 `DeadlineState`），并提供**一个共享的客户端 2s 封顶**（目前只有 favorite-api 有域内私有实现 ⇒ 10 域各写一遍 = 10 个可能写错的地方）。**影响面**：全部 rpc → 下游的出向调用（10 域）。**是否阻塞本波**：否 |

> ⚠️ **单 1 的含义订正（2026-10-27 · 复核 G4 落码时查出，重要）** ——
> 本条说的「**无任何测试证明**」**仍然成立**；但**机制本身在生产是通的**，别读成「生产上已失效」：
> - **生产早有修法**：MP 3.5.17 的 `MybatisPlusAutoConfiguration` 声明 `@AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)` —— 那是 **Boot 3** 的包名；**Boot 4.0.8** 已把该类搬到 **`org.springframework.boot.jdbc.autoconfigure`**（在 `spring-boot-jdbc-4.0.8.jar`）⇒ **排序引用静默悬空** ⇒ MP 被排到 DataSource 之前求值 ⇒ `@ConditionalOnSingleCandidate(DataSource.class)` 找不到 DataSource ⇒ **MP 退避、不产 `SqlSessionFactory`**。
> - **域侧已用排序桥补上**：**9/9 rpc 组件**都有 `MybatisPlusOrderingBridgeAutoConfiguration`（空类，**只为排序而存在**：`@AutoConfiguration(after = DataSourceAutoConfiguration.class, before = MybatisPlusAutoConfiguration.class)`）＋ 每个组件自己的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里注册一行 ⇒ 权威条目 **`docs/DRIFT/like/rpc.md:703` `DRIFT-LIKE-014`**（2026-10-23 已登记并已修）。<br>📌 **订正（2026-10-27 · G3）**：**「9/9」这个数已变** —— G3 本轮删了**自己三域**的域侧桥（article-rpc / comment-rpc / favorite-rpc）⇒ ⇒ **全仓现存 6 座域侧桥**（follow / like / message / task / users-user / users-admin）⇒ ⇒ **排序桥的唯一来源 = 共享层那座**（`service/common/.../persistence/MybatisPlusOrderingBridgeAutoConfiguration`）⇒ ⇒ 「域侧重复」这个**附带发现**已**部分消解**（余下 6 座归各自域写权）
> - ⇒ **所以缺的只是「把这条链钉死的运行证据」**：现有域侧测试只断言「桥**已注册**」（`ArticleSpringContextWiringTest` / `FollowRpcWiringTest` / `LikeRpcSpringContextWiringTest`），**没有一条断言「`@Import` 的 interceptor 真进了 MP 执行链」** ⇒ **单 1 仍成立、仍是阻塞项**。
> - ⚠️ **⇒ 守门测试必须复刻那座桥**（否则测的是「没有桥的装配」⇒ **假阴性**：实测 G4 的探针在缺桥时 3/3 error，根因原文＝ `MybatisPlusAutoConfiguration: Did not match: @ConditionalOnSingleCandidate (types: javax.sql.DataSource) did not find any beans`）⇒ 修法 = 测试作用域放一座**等价**的空桥 **＋ 在测试资源的 `AutoConfiguration.imports` 里注册它**（**光有类不注册 ⇒ 排序边不建立**）。
> - 🟡 **附带发现（已登记，未派单）**：桥在 **9 个域各写一份**（域侧重复），**共享层 `service/common` 没有**（其 `imports` 只有 `GoStyleOtelAutoConfiguration` ＋ `GoStyleJsonContentTypeAdvice`）⇒ 下沉共享层可消除重复，但**要改 9 个域的 `imports` 文件 ⇒ 域侧写权** ⇒ 跨写权变更（AGENTS.md §5.3c 须同一次提交）⇒ **需 Jason 排期，本轮不动**。

| **G4-4** | ✅ **共享层 2 处 U+FFFD 注释损坏**（各丢 1 个汉字；**纯注释、零行为影响**） | `service/common/src/test/java/com/rag/common/observability/GoStyleOtelSamplerProtocolGateTest.java:24`（`证的␦␦「全局 provider…（2×U+FFFD）` ⇒ 疑为「明」）· `service/common/src/test/java/com/rag/common/protection/FakeServerCall.java:13`（`测试用␦␦ {@link ServerCall}（2×U+FFFD）` ⇒ 疑为「的」） | **现象**：两处各丢 1 个汉字（3 字节 UTF-8 ⇒ 2×U+FFFD = 丢末字节）。<br>**建议修法**：按语义补回（只改注释、不改行为）；⚠️ **先判断是不是「故意的」** —— 本轮全仓扫出 **4 处故意**的 U+FFFD（如 `UserApiGoStyleBodyContentTypeTest:376` 的 `assertFalse(body.contains("\uFFFD"))` 是**断言的被测对象**；`docs/Pitfalls/message/rpc.md:911` 是**故障现象的样本**）⇒ **「全仓 U+FFFD 归零」不是合法目标**。<br>**影响面**：`service/common` 的两个测试类。**是否阻塞本波**：**否**。<br>🟡 **全域共性 ⇒ 只派一次**：本条**只在 comment 域登记**，其余域不重复派（G3 域内已自查完毕）。<br>✅ **闭合（2026-10-27 · G4 回执）**：G4 commit **`0c72969`** 已修（**字节级替换** `\uXXXX` 转义 ⇒ 不用 bash heredoc 写中文 ⇒ 写入的是正确 UTF-8 字节 ⇒ 严格 UTF-8 解码通过）⇒ 核验链条：字节级扫描（各 `FFFD_total=2`）→ 码点 dump（`0xfffd 0xfffd` **连续** ⇒ 「2×FFFD = 3 字节汉字丢末字节」成立）→ `git log --all` 逐提交追（**首个提交即已损坏** ⇒ 原字不可考 ⇒ 只能按语义重建）→ **形态反向验证**（「明」`E6 98 8E` 丢末字节 → `E6 98` = 正好 2×FFFD；「的」`E7 9A 84` → `E7 9A` = 正好 2×FFFD ⇒ 推断与字节形态吻合 + 语义通顺）⇒ ⇒ **影响面 = 纯 javadoc ⇒ 零行为变化**；⚠️ **该 commit 未进本树** ⇒ 本域要复验须先合入共享层（§4.3 选项 D）。<br>🟡 **另 2 条 `tools/**` 的 U+FFFD（G4 本轮回执判定 = 无人拥有）**：`tools/errmsg-probe/README.md:59` · `tools/sql-statement-probe/favorite/compare.py:561` ⇒ 既非合并收口执行者专属（≠ `tools/difftest/**`）· 也非任何 lane 的写权面 ⇒ ⇒ **本域不认领、不越权改** ⇒ G4 已登记进 `docs/DRIFT/global/vs-common.md` §4.10.7 ⑥ ⇒ **交合并收口执行者** ⇒ ⚠️ **合并影响**：这 2 个文件合并时**取 G4 树版本**，否则坏字符会被带回去 |


### 4.4 🆕 2026-10-29 · G3 · 本轮新增派单/回执（阶段 1）

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| **G4-16** | 🔴 **comment-rpc 的 `D8` / `D9` 两条仍在共享层**（= global `E65` / `E66`）—— 本轮**状态复核：未修，且本域无法自修** | **现象**：**D8**＝出向 gRPC 调用**不继承请求 deadline** ⇒ `ArticleRpcClient` 是**裸 stub**（服务端路径恒 no-op）；**D9**＝Redis 调用**不感知 deadline** ⇒ 缓存读写**照写不误**。⇒ ⇒ 两条的**落点都在 `service/common`**（共享层）⇒ ⇒ **G1~G3 只读** ⇒ G3 本轮**只登记不落码**。<br>**Go 侧 `文件:行号`**：`zrpc/internal/client.go:115-117`（zrpc client **无条件**套 2s timeout）＋ `rpc/internal/logic/helper.go:98,133`（父 deadline 经 `depCtx` 传播）⇒ ⇒ **Go 侧的 deadline 是真继承的**<br>**RAG 侧现状**：`service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:110-120`（`applyTo` —— **只在 HTTP worker 上生效**）＋ `service/common/.../observability/HttpMiddleware.java:304`（`begin` 的唯一主代码调用点）⇒ ⇒ **服务端（gRPC）路径恒 no-op**<br>**证据强度**：**L1**（Go 侧逐行）＋ **L3**（共享层源码路径）⇒ ⇒ **未在真双侧活体上对拍**<br>**建议修法**：共享层让 **gRPC 服务端路径**也把 deadline 写进 `GoStyleRequestDeadline`（或让 client 直接读 `DeadlineState`）＋ 提供**一个共享的客户端 2s 封顶**（目前只有 favorite-api 有域内私有实现 `FavoriteRpcClient.capByClientTimeout` ⇒ **10 域各写一遍 = 10 个可能写错的地方**）⇒ ⇒ **落点 100% 在 `service/common` ⇒ G4 落码**<br>**影响面**：10 域的 rpc → 下游出向调用<br>**是否阻塞本波**：**否** |
| **G4-17** | 🟡 **comment 域的 DDL 差异已被机器重测** ⇒ **基线 §十一 D-07 的归类与计数都要订正**（详见 `docs/DRIFT/comment/rpc.md` 的 2026-10-29 补记 ＋ article 域派单 **G4-12**） | **现象**：`python3 tools/difftest/check-ddl-static.py --domain comment --indexes` ⇒ **EXIT=2**，差异单位是**整张 `report_record` 表 8 列**（不是基线写的「2 条」）⇒ ⇒ 且它**已签字**（`SD-2026-10-24-CAND-02`，2026-10-24 用户 Jason）⇒ ⇒ **是决策不是缺陷** ⇒ ⇒ 基线把它列进「🔴 真实实现缺陷（必须修）」是**归类错误**。<br>**Go 侧 `文件:行号`**：`service/comment/rpc/internal/model/db.go:15`（`AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{}, &CommentDomainFactOutbox{}, &CommentFactStream{})` ⇒ **不含 `ReportRecord`**）＋ `service/comment/rpc/internal/model/data_model.go:55-68`（`ReportRecord` struct **存在**，`TableName() = "report_record"`）<br>**RAG 侧现状**：`service/comment/comment-rpc/src/main/resources/db/migration/V5__create_comment_report_record.sql`（**建表**）⇒ ⇒ 全新部署上 Go 的 `ReportComment` **必然 5004**、RAG 侧可用 ⇒ ⇒ RAG 侧是「**更全**」的一侧<br>**证据强度**：**L4**（机器 DDL 对照：EXIT=2 + 逐列清单；其余 6 张共享表 类型/长度/可空性/DEFAULT **0 差异**、索引 **16≡16**、约束逐条一致）<br>⚠️⚠️ **2026-10-30 真实性复核（W-D lane · 上句括号内的四个数字**已证伪**，原句保留 + 订正）**：那四个数字**不是那次 `EXIT=2` 运行的产出** —— 脚本在「列集合不一致」分支就 `return 2` ⇒ **七项对照一个都跑不到**；且**即便用补跑口径重跑，结论也相反** ⇒ **2026-10-30 本机实跑 `--rag-db` 补跑口径 ⇒ `EXIT=1` / 23 处**：**可空性 8 处**（`subject`/`comment_index`/`comment_content`/`comment_like` × `created_at`/`updated_at`）· **DEFAULT 10 处**（8 处同族时间列 + 2 处 IDENTITY 假阳性）· 类型/长度 **52 列逐字相同** · 索引 **Go 11 / RAG 13**（**2 处仅 RAG 有**）· 约束 **6/9 行**（**2 处实质差异** + 1 处仅命名形态差）· 触发器 **0/1**（**1 处仅 RAG 有**）⇒ ⇒ **「索引 16≡16」在本域任何口径下都不成立**（疑似串了 `article` 域的 `Go 16 / RAG 16`）⇒ ⇒ **本行的「其余 6 张共享表 0 差异」整体作废**，**逐条以 §9.2 / §9.4 为准**（2026-10-29 + 2026-10-30 两次独立复跑一致）。⇒ ⚠️ **本行「是决策不是缺陷」的归类**（`report_record` 整张表）**不变** —— 那部分确实成立。核对日期 2026-10-30。<br>**建议修法**：**不改代码**（保留 V5 ⇒ 不制造 Go 侧才有的部署缺口）⇒ **只订正基线的归类与计数** ⇒ 属**共享面**（`docs/PhaseGoal-judge.md` 是 4 条 lane 矩阵的唯一数据源）⇒ ⇒ **合并收口执行者**改 ⇒ 改后必须重跑 `python3 tools/difftest/check-phasegoal-lanes.py --write` ＋ `check-phasegoal-consistency.py`<br>**影响面**：4 条 lane 的分摊矩阵（P0 #3 行的「comment 2 条仅 RAG 有（未签字）」措辞）<br>**是否阻塞本波**：**否** |

### 4.1 §4 派单闭合状态（2026-10-27 · G4 第七次回执 4 项规格）

| 本域派单 | G4 回执对应 | 状态 | 证据 |
|---|---|---|---|
| **G4-1**（MyBatis-Plus 自动收集 `Interceptor[]` Bean） | **单 1** | ✅ **已闭合**（2026-10-27） | G4 树 commit `e3f2046`「test(common): 钉死『MyBatis-Plus 自动收集 Interceptor[] Bean』这条机制（**G3 单 1**）」⇒ 新增 `service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java`（**3 条 · 三层加码**）：① **收集层**（`MybatisPlusAutoConfiguration.interceptors` 里确有本拦截器）② **装载层**（`SqlSessionFactory.getConfiguration().getInterceptors()` 里确有本拦截器）③ **行为层（真 PG ＋ 真 mapper）**：绑一个已过期 deadline ⇒ 查询以 `57014` / `DEADLINE_EXCEEDED` 失败（前置：无 deadline 时同一查询**必须成功** ⇒ 失败才可归因于拦截器）⇒ **3/3 绿（实测）**。⚠️ **我域原登记的「全仓无任何测试证明」在登记时成立、现已由该守门闭合** ⇒ **保留原登记不改写**，此处加注记。⚠️ **该 commit 未进本树**（`git merge-base --is-ancestor` = 否）⇒ 本域要自己复跑该守门时**需先合入共享层**。 |
| **G4-2**（client breaker 在 rpc 侧无装配路径） | **单 2** | ✅ **已闭合**（2026-10-27） | G4 回执：「**client breaker 进共享层**」⇒ **本域零改动**（落点全在 `service/common`）。⚠️ **影响面口径（不得外推）**：G4 逐个 rpc 自核（`@GrpcClient` 计数 ＋ **六种出向方式**交叉核）后给出**全局**口径 = **6/10 可观测 ＋ 4/10 N/A**（N/A = `hot-rpc` · `task-rpc` · `user-rpc` · `admin-rpc`）⇒ ⇒ **本域面 = **3/4**（`article-rpc` 5 · `comment-rpc` 2 · `favorite-rpc` 2 可观测；`hot-rpc` = 0 ⇒ N/A）** ⇒ **「3/4」只覆盖本域四个 rpc 组件** ⇒ **不得被读成全局口径**。 |


> ℹ️ **口径说明**：上表的 ✅ 指 **「G4 的回执给了该单的落码 commit ＋ 验证口径」** ⇒ **不等于「本域已复验」** ⇒ 本域复验的前置 = **G4 的共享层 commit 进本树**（本树 HEAD 暂无 `2e2a84f` / `e3f2046` / `a1a2e05` ⇒ `git merge-base --is-ancestor` 全部为否）。
> ⚠️ **G4 回执给的是第七次全量**（`5564c27` / 2457 条）⇒ 本域台账已按**第八次**（`23d1c2a` / 2461 条）回填（**加注记、不覆写原值**）。

### 4.2 ✅ 拍板结论（2026-10-27 · Jason）—— 本域的**唯一**真决策项已定

#### ① 🔴 **C-3 / U15（= global E65）出向 gRPC 不继承请求 deadline ⇒ 方向已定：等 global 统一载体** ｜ 📌 **2026-10-30 订正 · 标题口径已过期**：共享层 `53bc05ec` 已把统一载体绑进 `RpcInterceptor.onHalfClose` ⇒ 「等 global」这一步**已完成** ⇒ ⇒ **「继承」那一半本域已闭合**（逐出口证据见 `docs/DRIFT/comment/rpc.md` 本轮新增段）⇒ ⇒ 现行开放面收窄为 **精度对齐（毫秒 → 纳秒，差 ≤1ms）** ＋ **逐出口线程差异**（一条继承 / 一条不继承，**两侧同形 ⇒ 非漂移**）

| 项 | 内容 |
|---|---|
| **拍板** | **走 global 的统一载体**（`GoStyleRequestDeadline.applyTo(stub)`）⇒ **本轮不改** ⇒ 共享层那半落地后本域照做 ｜ 📌 **2026-10-30 前置已满足**：共享层 `53bc05ec` 已把 `GoStyleRequestDeadline` 绑进 `RpcInterceptor.onHalfClose` ⇒ 「共享层那半」**已落地** ⇒ 本域那半（裸 stub 改 `applyTo`）**现在可执行** ⇒ 本域**尚未落码** ｜ 📌 **2026-10-30 第五轮订正 · 本域那半已落码**（Jason 拍板「本轮做」）：`service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/GoStyleClientCallTimeout.java` 改为**纳秒两段式**（`capByClientTimeout`）＋ `client/ArticleRpcClient.java:71` ＋ `client/MessageRpcClient.java:66` 两处调用点改走 `capByClientTimeout(GoStyleRequestDeadline.applyTo(stub))` ⇒ ⇒ **精度差 ≤1ms 已消除** ⇒ ⇒ 配套守门 `CommentOutboundDeadlineCarrierGateTest`（5 条 · **L4 活体** ＋ **三变异矩阵精确转红**）｜ ⚠️ **2026-10-30 订正 · 本域那半的「真实内容」已收窄**：**「继承」本身已闭合**（本域出口 `withDeadline` 读 `remainingMillis()` ⇒ 载体在位即继承）⇒ ⇒ 本域那半的真实内容 = **精度对齐（毫秒 → 纳秒）**，**不是**「从零继承」 |
| **被否决的备选** | 用 `DeadlineState.remainingMillis()` 直接构造出向 stub deadline |
| **否决理由（三条）** | ① 会引入**第二种载体** —— 仓内已有 `GoStyleRequestDeadline` 与 `DeadlineState` 两种互不相通的载体，**这正是 global 认定的病根**；② **偏离 Go 的单载体语义** —— Go 侧只有 `ctx` 一个载体（靠 ctx 传播）⇒ **「统一载体」才对应它**；③ 需 global 改探针期望值（`outgoing_carrier` 探针键当前钉的是 `absent` ⇒ 改实现即变红）⇒ **代价明确 ⇒ 不选** ｜ 📌 **2026-10-30 订正**：理由③**已失效** —— 共享层已把 `outgoing_carrier` 由 drift 键**升为 parity 键**（`tools/difftest/check-deadline-downstream.py:53`）⇒ 「改实现即变红」的前提**消失**；理由①②（统一载体 / 不引第二种载体）**仍成立** ⇒ ⇒ 本域那半**不再受阻**（且其内容已收窄为**精度对齐**，见「拍板」格） |
| **影响面** | 本域 `comment-rpc` 的 2 个出向出口（`client/ArticleRpcClient.java` · `client/MessageRpcClient.java`）＋ 跨域的 like / message（**G2 域**）⇒ ⇒ **本域那半的前置 = 共享层统一载体落地** ｜ 📌 **2026-10-30 订正 · 两个出口的载体可见性不同**（逐出口证据见 `docs/DRIFT/comment/rpc.md` 本轮新增段）：`ArticleRpcClient` 在 **gRPC handler 线程** ⇒ 载体 ThreadLocal **可见** ⇒ 继承入向 deadline；`MessageRpcClient` 在 **Kafka consumer 线程**（`service/CommentAuditListener.java:99` 的 `@KafkaListener`）⇒ 载体 **不可见** ⇒ 只有 2s 客户端封顶 ⇒ ⇒ 而 Go 侧该出口的 ctx 是 **Kafka 消费 ctx（无 deadline）** ⇒ **两侧同形 ⇒ 该出口不是漂移** ⇒ ⇒ **「继承」那一半只对 handler 线程那条出口成立** |

⚠️ **本轮核出的新事实（防下一个人误引先例）**：本域台账原引「同仓既有先例：`users/admin/AdminDomainService.java:140` · `users/user/UserDomainService.java:145` 已用 `DeadlineState.isExpired()`」⇒
**实测该先例不成立** —— 那两处是**本地超时检查**（`if (Thread.currentThread().isInterrupted() || DeadlineState.isExpired())` ⇒ 判到期后走本地降级）⇒
**不是「用 `DeadlineState` 构造出向 gRPC deadline」** ⇒ ⇒ **不得据它论证「仓内已有第二种载体的出向用法」**；
⚠️ 另：该判断的**行号已漂移**（实测命中在 `AdminDomainService.java:178`）⇒ **引用前须重新定位**。

#### ② 🟡 **C-4（`:9091` metrics 暴露面）⇒ 不升级为拍板（登记类）**

- **依据**：① **不是代码缺陷**（部署形态类，与本域已登记的 U9 同款）⇒ **不阻塞任何落码**；
  ② **指标内容两侧已对齐** —— Go `service/comment/rpc/internal/metrics/metrics.go:42-43` **主动注销** `collectors.NewGoCollector()` 与 `NewProcessCollector()`
  ⇒ `:9091` **只暴露 5 个 `comment_*` 向量**（`request_total` · `request_seconds_counter` · `list_size` · `postgres_error_total` · `redis_error_total`）；
  RAG 侧 `comment-rpc/.../application.yml:277-283` **显式关掉** `management.metrics.enable.jvm` / `.process` ⇒ **同样只暴露 comment_*** ⇒
  ⇒ ⇒ **差异只在「有没有对外抓取面」，不在指标内容**。
- **三个选项（留档，将来要改时直接用）**：
  ① **维持现状**（纯 gRPC 进程无 HTTP 面 ⇒ 指标只在进程内，抓取侧拿不到）
  ② **给 rpc 进程开一个 HTTP 暴露面**（`management.server.port`）⇒ 代价：**部署形态变更**（纯 gRPC 变「有 HTTP 端」）＋ 抓取路径与 Go 不同（`/actuator/prometheus` vs Go 的 `/metrics`）⇒ 抓取侧要改 URL
  ③ **照 Go 起一个独立 promhttp `:9091`** ⇒ 代价：**引入第二套指标暴露路径**（与 Actuator 并存 ⇒ 两处维护）
  ⇒ ⇒ **三条都要拍板才能定** ⇒ **本轮按「登记类不占用拍板额度」处理**（AGENTS.md 协作规则）⇒ **本条仍挂在 §4 的待办清单里**。

### 4.3 🔴 共享层同步请求（**4 件事同源同前置** · 本轮给出**新选项 D**）

| 项 | 内容 |
|---|---|
| **被卡住的 4 件事** | ① Redis 断连快速失败的**活体对拍**（favorite-api 侧）—— ⚠️ **本域现状已核清（2026-10-27 · 独立实测，不是转述）**：**我四域的域侧副本 = 0**（全仓生产代码里 `RedisDisconnectedBehaviorConfig` 只命中 G1 域的 `users/user/user-api` 等 **5** 份 ⇒ 我域**零命中**）⇒ ⇒ **G4 收编时的删除动作在我域 = 0**（与 G1 的 5 份不同）；且**我树共享层根本没有 redis 自动配置**（`find service/common/src/main/java -iname "*redis*"` ⇒ 零命中；`2f12236` 不在 HEAD 历史）🔴 **订正（2026-10-27 第十八轮 · 合并后）**：**这一条已不成立** —— merge `e18fd52` 把 `align/g4-service-common` @ `cd893d1` 合入 ⇒ ⇒ `service/common/src/main/java/com/rag/common/redis/GoStyleRedisClientAutoConfiguration.java` **已在本树** ⇒ ⇒ 「共享层那份**必被创建**」现在**可在树内验**（我四域仍**零域侧副本** ⇒ **无遮蔽** ⇒ 收编成立）⇒ ⇒ **「G4 收编时的删除动作在我域 = 0」这条结论不变**（域内副本数仍为 0）⇒ ⇒ **我域不是「被域内副本遮蔽」，而是「两边都没有」** ⇒ ⇒ 断连快速失败在我域**尚未成立** ⇒ ⇒ **活体对拍必须等共享层落地**（这正是它卡在选项 D 的原因）<br>② 删域侧排序桥副本（🔴 **2026-10-27 订正：原写「本域 article-rpc / favorite-rpc 各 1 份」是**漏了本域** ⇒ 本域自己也有 1 份（`service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ 自己的 `imports:1`）⇒ **全仓 9 座域侧桥**（article / comment / favorite / follow / like / message / task / users-user / users-admin）⇒ 本域那份此前**没进过台账**）<br>✅ **2026-10-27 G3 已落码**：本域那份**已删**（删 `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java` ＋ 本域自己的 `imports` 文件**整份删**—— 它**只有那 1 行** ⇒ ⇒ 与 article-rpc 的「2 行、只删第 1 行」形态**不同**）⇒ ⇒ **验证（先 `clean` 再跑）**：`mvn -o -pl service/comment/comment-rpc -am test -Dtest='CommentRpcGoSemanticsGateTest,CommentRpcArtifactClasspathGateTest,CommentRpcRuntimeBootGateTest'` ⇒ **18 条 / 0 failures / 0 errors / 0 skipped**（2026-10-27T23:17:11+08:00）⇒ `CommentRpcGoSemanticsGateTest` **14 条**（**真上下文** ＋ 注入 `SqlSessionFactory`）＋ `CommentRpcArtifactClasspathGateTest` **3 条**（含**新增**第 ③ 条：fat jar 内嵌的 common jar 带共享层排序桥）＋ `CommentRpcRuntimeBootGateTest` **1 条**（**真 fat jar 真进程起来**）⇒ ⇒ **全仓现存 6 座域侧桥**（follow / like / message / task / users-user / users-admin）⇒ ⇒ **排序桥的唯一来源 = 共享层那座** ③ client breaker **装配复验**（= §4-G4-2）④ **U15 / C-3 统一载体**落地（= global E65） |
| **共同前置** | 共享层的落码 commit **不在我树 HEAD 历史**（`2e2a84f` · `a1a2e05` · `e3f2046` ⇒ `git merge-base --is-ancestor` 对我树 HEAD **全为否**，仅 object 库里存在）⇒ ⇒ **共享层标准件没进我树 ⇒ 我不能先删域侧副本**（先删 ⇒ MP 排序边消失 ⇒ 上下文起不来） |
| **选项 D（本轮新增 · 我推荐）** | 我直接 `git merge align/g4-service-common` 到我树 ⇒ **同一仓库 ⇒ 提交对象共享 ⇒ 不产生「内容同、hash 异」的副本** ⇒ 合主干时**不会** add/add 撞 ⇒ ⇒ **只需你给一个稳定点**（从哪个 commit 起） |
| **其他选项** | **A**（你合主干后我合主干）· **B**（我 cherry-pick ⇒ **会**造出 hash 不同的副本 ⇒ 合主干时大概率撞）· **C**（你替我合） |
| **要你回什么** | **一个字**：`D` 还是 `A`；若 `D` ⇒ 再给**起始 commit**。⇒ 我拿到即按序推进那 4 件事（**不重复催**，这是同一件事的第 3 次问，前两次都附了 A/B/C） |

> 🔴 **2026-10-27 · G3 独立核结论（4 项全过 ⇒ 删除动作本身结构上安全，但**等真 PG 窗口**才落刀）**
> 📌 **2026-10-27 G3 已落刀（原文保留不改写）**：**「等真 PG 窗口」这个条件本轮**没等**（也不需要）——
>   删除是**纯删减**（删一个空类 ＋ 删掉只含那一行的 `imports` 文件）⇒ ⇒ 且三处验证**本身就要真环境**：
>   `CommentRpcGoSemanticsGateTest`（真上下文，注入 `SqlSessionFactory` ＝ 排序边真接管了）＋
>   `CommentRpcRuntimeBootGateTest`（**真 fat jar 真进程起来**）⇒ ⇒ **clean 后重跑 18 条 / 0f 0e 0s**
>   （2026-10-27T23:17:11+08:00）⇒ ⇒ 详见上方「被卡住的 4 件事」的 **②** 条。
> ① **共享层那份是自动配置**：`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4` 登记了 `com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration`
>    ⇒ **每个消费者 classpath 上都会加载** ⇒ **前置已满足**（`e18fd52` 已把它合进本树）；
> ② **两份排序边同向同目标**：共享层**字符串版**（`afterName`/`beforeName`）＝ 域内**类字面量版**（`after = …class` / `before = …class`）
>    ⇒ **同一条边、同一个方向** ⇒ 排序图里多一个同向节点、**无环**、功能等价 ⇒ 共存**不改变任何行为**；
> ③ **两份都不注册任何 bean** ⇒ ⇒ **无同名 bean 冲突** ⇒ 共存**结构上安全**（不是「侥幸没炸」）；
> ④ **域内那份外部引用 = 0 命中**（`grep -rn "config.MybatisPlusOrderingBridgeAutoConfiguration" --include=*.java service/comment` ⇒ 只命中文件自身）
>    ⇒ ⇒ **删除不断编译**。
> ⇒ **删除动作 = 纯删减**，但**运行期验证**需要真 PG ⇒ ⇒ **当前被机器级 Docker 端口转发故障阻塞** ⇒ ⇒ 本轮**只落核结论，不落删除**。
> 📌 **订正注记（2026-10-27 · G3）**：上面这句的**阻塞理由已不成立** —— 「机器级 Docker 宿主→容器端口转发故障」
> **实测不复现**（四个探针全通：新建容器 `PING` ⇒ `+PONG`；**42 小时前**的 `ddl-comment-pg` ⇒ PG `SSLRequest` 有应答；
> **23 小时前**的 `difftest5k-redis` / **3 天前**的 `uudifftest-redis` ⇒ `+PONG`）
> ⇒ ⇒ **「只落核结论、不落删除」的真实理由**变成 **「共享层的落码 commit 不在我树 HEAD 历史」**（见下面「共同前置」行）
> ⇒ ⇒ **不是环境问题** ⇒ ⇒ **合并共享层后即可执行删除 ＋ 真 PG 验证**（该删除动作属 G3 写权面 ⇒ 本域自修）
> ⇒ 证据明细 ⇒ [`hot/rpc.md` §十一.8](../hot/rpc.md) 的四个探针表。
> ✅ **订正注记（2026-10-27 · G3 · 20:15）：上面「被卡住的 4 件事」① 已解除** —— **断连活体对拍本轮真的跑通了**
> （真 Redis 容器 → 连上 → PING → **静默 1s（期间不发命令）** → 外部 `docker stop`（优雅 FIN）→ 量第一条命令）：
> **RAG 腿 A(REJECT_COMMANDS) = 1 ms** vs **B(Lettuce 默认 DEFAULT) = 3005 ms** ⇒ 差 **3004 ms**；**Go 腿 1708–1776 ms**（4 轮）
> ⇒ ⇒ **共享层那份在本域真的生效**（`getBeansOfType(LettuceClientOptionsBuilderCustomizer.class)` **size == 1** ＋ bean 名含 `goStyleRedisClientOptionsCustomizer` ⇒ ⇒ **无域内副本遮蔽** ⇒ ⇒ **「断连快速失败在我域尚未成立」这句已被本轮实测推翻**）
> ⇒ ⇒ 守门 `CommentRedisDisconnectFastRejectGateTest` **2 条**已入库（本轮全量 **comment-rpc 125 / 0 / 0 / 0**）
> ⇒ ⇒ 证据 ⇒ [`comment/rpc.md` §十二](../comment/rpc.md#十二✅-redis-断连活体对拍共享层的-reject_commands-在本域真的生效-2026-10-27--g3)
> ⇒ ⇒ ⚠️ **仍剩两格未验**：① **gRPC 响应层**（断连时客户端到底看到什么 ⇒ **要真发一次 gRPC 调用**才测得到）
> ② **Go 探针未入仓**（`tools/**` 属 G4 写权面 ⇒ 已派单 ⇒ ⇒ 仓内**不可复现** ⇒ ⇒ 证据强度只能写「L4-一次性」）
> 〔📌 **2026-10-28 G3 更新**：**规格已入仓** ⇒ [`hot/rpc.md` §十二.9](../hot/rpc.md#十二9-🔴-toolsredis-disconnect-probe-的完整落码规格2026-10-28--g3--自包含照此即可落码)（**含可直接复制的 Go 探针源码** ＋ 真源 Options 逐条出处 ＋ 时序 ＋ 编排 ＋ 实测基线 ＋ 输出格式 ＋ 退出码）⇒ ⇒ **G4 不必再等聊天里的信，直接照仓内规格落码** ⇒ ⇒ 待 `tools/redis-disconnect-probe/` 落地后 ⇒ **仓内即可复现** ⇒ ⇒ 证据强度可从「L4-一次性」升为「**L4（仓内可复现）**」⇒ **原句保留不改写**〕
> ⚠️ **附带（删 imports 的具体形态）**：`comment-rpc` 的 `imports` 文件**只有这一行**（= 桥类）
> ⇒ ⇒ 删桥后**文件变空** ⇒ 决定**连文件一起删**（空 imports 文件无意义）。

## 5. 交付物清单

- [x] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）—— ✅ **2026-10-27 阶段 2 已完成**：`mvn -o -pl service/comment/comment-rpc -am test` ⇒ **comment-rpc 115 条 / 0 failures / 0 errors / 0 skipped**（含 `CommentRpcGrpcInterceptorWiringTest`（**真 gRPC server**）＋ `CommentFlywaySharedSchemaGateTest`（**真 PG ＋ 真 Flyway**）＋ `CommentSqlDeadlinePrecisionGateTest`（**真 PG ＋ 真 Hikari ＋ 真 MyBatis 插件链**）3/3 绿 ＋ **C-3 新增的 `CommentSpanWiringTest`（真 Spring 上下文 ＋ 真 PG ＋ 自装 SDK 收集型 exporter）7/7 绿**）。⚠️ **必须带 `-am`**（否则拿旧共享层跑，见文末「本轮踩坑」）。
  - ⚠️ **数字订正（2026-10-27 · C-3 轮自查）**：上一版此处写的是「**comment-rpc 27 条**」⇒ **错**：那 **27** 是 **`comment-common`** 模块的条数（`CommentErrorCodeContractTest` 5 ＋ `CommentGrpcErrorTest` 12 ＋ `CommentErrorMessagesContractTest` 10）⇒ **comment-rpc 上一轮实际是 108 条**，本轮加 7 条守门 = **115** ⇒ ⇒ **上一轮 commit `6d4eb1d` 的 message 里写的「四域合计 392 条」也是错的**（真实为 155＋96＋114＋108 = **473**）⇒ **根因**：我当时只 `tail` 了 reactor 汇总的**最后 7 行**，漏了第 8 行（`comment-rpc 108`）⇒ **教训**：**模块条数要按「模块名 ↔ 汇总行」对应取，不可按行序数**（与「口径必须写死 + 可复算」同族）。
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（**2026-10-27**）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— ⬜ **本域无现成域级探针**（2026-10-29 订正：`tools/*-probe/` 的域级实例 = `favorite/` ＋ **`hot/`**（后者为 G3 本轮落码），**不含 comment**）⇒ 结论最高 **L3** ⇒ 已在 §3-C-5 登记「复制探针骨架」的**域侧自修项**（⚠️ 归属已按 §5.3f 订正为 G3 可写）
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）—— 🟡 **阶段 2 落码部分已完成**（口径：`mvn -o -pl service/comment/comment-common,service/comment/comment-api,service/comment/comment-rpc -am test` ⇒ **comment-common 27 / comment-api 52（= 原 45 ＋ 本轮新守门 7）/ comment-rpc 115** ⇒ **本域三模块合计 194 条 / 0 failures / 0 errors / 0 skipped** · 核对日期 **2026-10-27**）：
  - **C-1** ✅ 两个出向 client 补 2s 封顶（新增域内 package-private `client/GoStyleClientCallTimeout.withDeadline` ＋ `ArticleRpcClient.articleRpc()` / `MessageRpcClient.messageRpc()`）
  - **C-2** ✅ `messaging.destination` 改成 Go 字面量 `"comment_audit"`（新增 `CommentAuditListener.AUDIT_SPAN_DESTINATION` ＋ Go 出处注释块）
  - **C-3** ✅ **6 个业务 span 已补齐**（新增域内 `CommentSpans` ＋ `CommentDomainService` 6 个方法接上 ＋ 新增守门 `CommentSpanWiringTest` **7/7 绿**）—— 三处 Go 侧不一致（tracer 名带点 / 属性前缀 / 状态语义）逐字保留；「记不记错误」的判据 = **业务码段**（5xxx 系统 / 1xxx·2xxx 业务）；`likeComment` **刻意不加**；枚举取值对齐 Go 的 `EnumStringOf`
  - ⏳ **C-4**（`:9091` metrics 暴露面 · 部署形态类 · **需拍板**）· **C-5**（A6/A7 升 L4 的探针 · 骨架已有）＝ **本轮不落码**（C-5 属「复制探针骨架」的自修项；C-4 不是代码缺陷）
  - **C-6** ✅ **JWT 验签搬出 3s 预算**（新增 `comment-api/.../auth/` 4 个类：`CommentJwtAuthPaths` / `CommentJwtVerificationResult` / `CommentJwtSignatureVerifier` / `GoStyleJwtAuthFilter`（`@Order(+6)`）＋ 改 `CommentJwtAuthInterceptor` 消费结果并原样重抛 ⇒ **401 空 body 不变**、**业务码仍 1006**、**未注册路径仍 404**、**本域无黑名单 ⇒ 无新增**）⇒ 守门 `CommentApiJwtVerificationScopeTest` **7/7 绿 ＋ 变异 3/3 精确变红**（`+8` 顺序 ⇒ 1 条红 / Filter 自写 401 ⇒ 2 条红 / 摘 `@Component` ⇒ 来源标记 `filter`→`inline`）⇒ ⚠️ **本域无真 Tomcat 门禁** ⇒ 「413 压过 401」的响应头层语义**只有 MockMvc 级证据**（已如实写进证据强度）
- [x] §4 的派单**已走实时轨**（`session send` → `G4Common` ＋ 本表正式轨，两轨一致）：**单 1** ＝ 🔴 G4-1（MyBatis-Plus 自动收集 `Interceptor[]` Bean 无运行证据，**阻塞本波**）· **单 2** ＝ 🔴 G4-2（client breaker 在 rpc 侧无装配路径）—— 两条 7 项反馈单齐全（全域共性 ⇒ 请 G4 收口去重）
  - 🔴 G4-3（`E65`）＝ **不重复派**（已是 global 台账的 **OPEN** 项）⇒ G3 只在域台账**引用**，不另开单
  - 🔴 `docs/DRIFT/global/vs-common.md` ＋ `global/_README.md` §七之二属 **SHARED 面 ⇒ G3 不写**（AGENTS.md §5.3e）⇒ 由 G4 / 合并收口执行者落
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）—— ✅ **本轮实测（2026-10-28 · G3，域侧自修 · §1e）**：`check-ledger-tables` **EXIT=0** · `check-ledger-selfcheck` **EXIT=0** · `check-ledger-count` **EXIT=1**（索引 **315** vs 本树实测 **332**，差 **+17** ⇒ **合并收口执行者**统一 `--fix` ⇒ ⛔ **各 lane 不各自 `--fix`**）· `_MANIFEST.json` **不收录** `vs-common.md`（`grep -c` = **0**）⇒ ⇒ **本域无需改共享面** ⇒ ⇒ ⚠️ **措辞订正**：「合并收口执行者」⇒ 改称「**合并收口执行者**」

> ⚠️🔴 **本轮踩坑（四域共用 · 已在 G4 同步）——「单模块跑法」会拿 `~/.m2` 里的旧共享层做测试**
> 先用 `mvn -o -pl service/comment/comment-rpc test`（**不带 `-am`**）⇒ **本域 3 条红**（驱动收到 2000 而非原值毫秒 / 在途查询 1044ms / **过期后仍执行 SQL**）⇒ 症状像「SQL deadline 漂移」。
> ⇒ **根因**：`~/.m2/repository/com/rag/common/0.0.1-SNAPSHOT/common-0.0.1-SNAPSHOT.jar`（安装于 **2026-10-25 21:24**）的 `DeadlineQueryTimeoutInjector` **不含 `unwrapDriverStatement`** ⇒ 是 **E62 修前**的版本 ⇒ 毫秒探测必然失败（退回秒级 `ceil`）＋「已过期」分支还是旧行为「设 1 秒」而非抛 57014 ⇒ 与三条红**逐条吻合**。
> ⇒ **判定**：基线 commit `9f034e8` 的临时 worktree 跑 `-am` ⇒ **comment-rpc 3/3 绿** ⇒ **不是漂移、不派 G4**。⇒ **纪律：单模块跑域内测试一律加 `-am`**（或先 `mvn install` 共享层）。⚠️ **跑红也 ≠ 坏**（与「跑绿 ≠ 对」同族）。
> ⇒ **全文与跨域影响面见** [`docs/DRIFT/article/vs-common.md`](../article/vs-common.md) §5 的「本轮踩坑」条（同一轮同一事实，四域共用）。

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
| **C-1** | **`U15`（= global `E65`）出向 gRPC 调用不继承请求 deadline** —— 本域**独立核实成立**（`ArticleRpcClient.java:65-72` / `MessageRpcClient.java:63-83` 是裸 stub） | 🔴 **仍开** | 🔴 **global / G4**（共享层那半：`GoStyleRequestDeadline.begin()` 只在 HTTP 路径绑定 ⇒ gRPC 服务端路径上 `applyTo` 是 **no-op**）⇒ **本域那半待修法拍板** ⇒ ⇒ **本域不擅自选边**（两条路各有代价） | 否 | [`rpc.md` §5 `U15`](rpc.md) · §3.5 |
| **C-2** | **`U16`（= global `E66`）Redis 调用不感知 deadline** —— `CommentCacheService.java` 的写路径（`:279` / `:331` / `:433` / `:528` 的 `set`、`:619` 的 `delete`）**零 deadline 检查** | 🔴 **仍开**（证据强度 **🟡 源码级**，未做活体验证） | 🔴 **global / G4**（`service/common` **无任何 Redis 期限载体** ⇒ **本域单方面无法闭环** ⇒ 不擅自加临时机制 —— 那会引入**第三种载体**，与统一方向相反） | 否 | [`rpc.md` §5 `U16`](rpc.md) · §3.6 |
| **C-3** | **`U10` RPC 超时的 status `description` 两侧不同**（Go `context deadline exceeded` vs RAG `rpc timeout after 2000ms`） | 🔧 **修复已落码，销账待 global 合回** | 🔴 **G4 / 合并收口执行者**（已提交并推双远端 ⇒ 不会丢；**合回与否已拍板「暂不合回」** ⇒ 本域**不代销**） | 否 | [`rpc.md` §5 `U10`](rpc.md) |
| **C-4** | **`U13` 跨栈 gRPC 错误码必然退化成 `UNKNOWN(2)`** —— `io.grpc.Status` 只有 17 个常量 ⇒ grpc-java **发不出** `grpc-status: 1012` ⇒ 业务码只能在**自定义 trailer** 里 ⇒ Go 侧读不到 | 🔴 **仍开 · 协议层限制 · 非本域可修** | ⬜ **协议层**（混跑必须同栈配对；别名只是兜底） | 否 | [`rpc.md` §5 `U13`](rpc.md) |
| **C-5** | **A6/A7 升 L4 的域级探针**（错误码契约 ＋ proto wire） | 🟡 **部分闭合（2026-10-30 · G3）** —— **A6 半边 ✅ 已闭合（L4）** · **A7 半边仍开** | ✅ **A6 = L4**：`tools/errmsg-probe/comment/` 已落仓（`git ls-files` 命中）＋ **2026-10-30 实跑 `EXIT=0`**（Go ↔ Java 双侧逐字节，sha256 同值 `514e3a71…` · 137 行 · OBS 段 3 条仍登记）⇒ ⇒ **A6 半边销账**<br>🟡 **A7 仍开**：proto **消息体** wire 字节探针**本域仍缺**（`tools/proto-wire-probe/` 只有 `favorite/` ＋ `hot/`）⇒ 本域 A7 仍只有静态对照<br>🟢 **G3（本域 · 归属已订正）** —— 按 §5.3f，`tools/errmsg-probe/comment/` 与 `tools/proto-wire-probe/comment/` 都只被本域引用 ⇒ **真私有 ⇒ 域侧落码合法** | 否 | [`comment/vs-common.md` §3 `C-5`](.) · §5 交付物清单 |
| **C-6** | **共享台账的过期结论**：`docs/DRIFT/_cross/new-domains-2026-10-23.md:43-45` 记「gRPC client 熔断缺失」⇒ **实测 RAG 侧熔断已存在** ⇒ 该结论**作废** | 🔴 **需更正共享台账** | 🔴 **合并收口执行者**（`_cross/**` 是共享文件 ⇒ 本工作树不碰） | 否 | [`api.md` §5 `U4`](api.md) |
| **C-7** | **`U10`（api 侧）机制待查**：Spring 7 + Tomcat 11 下非 JSON Content-Type 时 servlet input stream 读不出字节 | ⚠️ **机制待查**（**可观察行为已定** ⇒ 不影响结论） | 🟢 G3（低优先） | 否 | [`api.md` §5 `U10`](api.md) |
| **C-8** | **真源侧缺口**：Go 侧三个 `_test.go`（`domain_fact_test.go` / `fact_process_fixture_test.go` / `fact_delivery_logging_test.go`）**默认 `t.Skip`** ⇒ Go 侧投递链路的期望行为**在真源仓里本身也没有活体证据** | ⚠️ **真源侧缺口**（已冻结 ⇒ 只记录） | ⬜ 真源 | 否 | [`rpc.md` §5 `U4`](rpc.md) |
| **C-9** | **Go 侧潜伏缺陷**：`comment.go:571`（点赞的 `attribute & ~?`）⇒ Go 侧「作者取消自己的赞」也会 5002 ⇒ **RAG 侧已正确** | ⚠️ **真源侧缺陷**（记录，不修 RAG） | ⬜ 真源 | 否 | [`rpc.md` §5 `U11`](rpc.md) |

⇒ **comment 域的「域内可动」未闭合项 = C-5 / C-7 两条**（C-1~C-4 属 global/协议层，C-6 属共享台账，C-8/C-9 属真源侧）⇒ ⇒ **本域阶段一判定不变**。

---

## 7. 🔴 Go 出处引用漂移自查（2026-10-29 · G3 域内收敛自查 B）

> **为什么这是缺陷而不是笔误**：以「Go: …」措辞写在 javadoc / `@DisplayName` / 断言消息里的断言
> **改一行不会让任何测试变红** ⇒ ⇒ **绿测试可能守着一个错的事实**（AGENTS.md §3.3.2）。
> ⚠️ **守门本体见 [`article/vs-common.md` §7.1](../article/vs-common.md)（四域共用，G3 域侧落码）**
> ⇒ 本节只列**本域**的订正项。

| # | 落点 | 原引用 | 订正为 | 缺陷类型 |
|---|---|---|---|---|
| 1 | `comment-api/.../interceptor/CommentJwtTokenMatrixGateTest.java:46` | `rest/token/tokenparser.go:127-129` | `rest/token/tokenparser.go:122-124` | **行号越界**（go-zero **v1.10.2** 的该文件**仅 124 行** ⇒ `:127-129` 根本不存在） |

⇒ **本域 1 处已订正（零行为变更：注释 / `@DisplayName`）** ⇒ 本域守门实测 **EXIT=0**（`LINE_OOB` / `FILE_MISSING` / `EMPTY_LINE` / `VERSION_MISMATCH` **全 0**）。

---

## 8. 🔴 台账内部矛盾 / 过期结论订正（2026-10-29 · G3 域内收敛自查 C）

> **扫描口径**：把本域台账里**同一个 ID 在不同行**同时出现「已闭」与「仍开」措辞的**候选**全部取出
> ⇒ 再逐条**人读**核实 ⇒ 只保留**前提真的失效**的。

| # | 落点 | 矛盾 | 订正 |
|---|---|---|---|
| 1 | 本文件 §3 `C-5` 行 ＋ §5 交付物清单 | ① `C-5` 写「骨架已有（顶层 article ＋ `favorite/`）」⇒ **漏了 `hot/`**（2026-10-29 G3 刚落码）⇒ ② §5 交付物清单写「`tools/*-probe/` 的域级实例**只覆盖 favorite**」⇒ ⇒ **同款过期** | ✅ 两处一并订正（补 `hot/`）＋ 顺带把 `C-5` 的**归属**按 AGENTS.md §5.3f 订正为「**G3 域侧可写**」（原判「`tools/**` 是共享面 ⇒ 不在 G3 写权面」已作废） |

⇒ **comment 域真矛盾 = 1 条，已订正**。

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

- `docs/DRIFT/article/api.md` ⇒ 权威现状面 = **§7 状态表（`:556`）+ §9 UNRESOLVED（`:982`）**（文件头 `:17-24` 有读取指针表；⚠️ 组件判定行 `:26` 是**首轮**判定，**不可直接引用** —— 见 `PhaseGoal-judge.md:104-108`「5 份台账头已被正文推翻」）；§8.3（`:961`）四条「待补探针」经核对 **3 条早已消解**（`DRIFT-001/002/003` 已修 + 守门在位）⇒ 本轮已改写为「活状态」表；`UNRESOLVED-002`（`:1001`）**已于 2026-10-30 关闭**（W-D lane 跨组件复核 · **L1 静态双侧源码对照**）⇒ ⚠️ 但**跨栈活体对拍仍未做**（关闭依据 = 两侧 `文件:行号` 逐条对照，**不是**活体）。
  ⚠️ **2026-10-30 真实性复核（W-D lane · 指针订正）**：本行原写的 `§9 UNRESOLVED（`:878`）` / `§8.3（`:858`）` / `UNRESOLVED-002`（`:886`）` **三处都指错行**（当时分别指向 `UNRESOLVED-008` 的正文 / `UNRESOLVED-010` 的正文 / `UNRESOLVED-001` 的正文）⇒ ⇒ 本次订正为**各节/各条的实际标题行**（`:982` / `:961` / `:1001`）；⚠️ `§7 状态表（`:556`）` **复核正确**，未动。
- `docs/DRIFT/article/rpc.md` ⇒ 权威面 = §7（`:343`）+ §9（`:654`）；**`S-5`（`:95` / `:318`）content security 被 security 域阻塞 ⇒ 外部客户端恒抛、审核链路不可用 ⇒ ⛔ 本项目内不可闭环**；`U-3`（`:658` 真 Kafka 失败位点）· `U-4`（`:659`）· `U-9`（`:664`）· `U-4'`（`:666`）**四项取证缺口仍 OPEN**；「跨域待办」（`:1271` / `:1789`）= 其它域 `*SchemaGate` 同款写法 ⇒ **只登记不修**。
- `docs/DRIFT/article/common.md` ⇒ ✅ Go↔Java **sha256 逐字节一致**（`cad5cb4f…`，49 行，`:50`）+ 契约测试 15 条（`:51`）+ 变异 **8/8 全红**（`:52`）⇒ **无编号未决项**；⚠️ `:22` / `:74-77` 的「消费侧仍是空骨架 / 零生产调用方」描述**已被现状推翻**（覆盖矩阵 `:40-41` 记 `article-api` 41 文件 5759 LOC）⇒ **一条未被本文件自身订正的过期描述**（见 ③ 待批准项 #5）。
- `docs/DRIFT/article/proto.md` ⇒ ✅ 行为层一致、**唯一差异是 option 块**（`:51-62`）；§3.4 三项效力边界（presence / 字段号硬编码 / Jackson）**逐条已验**（`:23-27`）；`P-U-1` 跨栈 gRPC 活体**仍 OPEN**（`:175`）。
- `docs/DRIFT/article/vs-common.md` ⇒ §2 **9 条机制面轴 A1~A9 逐轴结论**（`:36-49`，核对日期 2026-10-27）；§6 **域内未闭合项清单**（`:345-363`，本轮新增）⇒ 域内可动 = **A-1 / A-2 两条**，均不阻塞阶段一；§4 派单仍挂 `G4-2`（MP 自动收集无运行证据，**阻塞本波** `:276`）· `G4-10`（`check-surface-parity.py` 路径指错 ⇒ **假红/假绿** `:296`）· `G4-11`（双侧活体受阻前提已过期 `:297`）。
- `docs/DRIFT/comment/api.md` ⇒ §5 未决 `U1~U11`（`:330-345`）：**`U4`（`:337`）共享台账 `_cross/new-domains-2026-10-23.md:43-45` 的「熔断缺失」结论已被实测推翻 ⇒ 🔴 需收口执行者更正共享台账**；`U10`（`:343`）机制待查（**可观察行为已定**）；判定行 `:24` 注明 3 条 MISMATCH 全修 ⇒ 当前无「必须对齐」分歧项。
- `docs/DRIFT/comment/rpc.md` ⇒ §5 `U1~U16`（`:566-585`）：**`U15`（`:584`）= global `E65` 出向 gRPC 不继承 deadline ⇒ 待修法拍板 + 待共享层** · **`U16`（`:585`）= `E66` Redis 不感知 deadline ⇒ 待共享层**（域侧不擅自引入第三种载体）· `U13`（`:582`）跨栈 `grpc-status:1012` **协议层不可修** · `U10`（`:579`）**销账待 global 合回** · `U4`（`:573`）Go 侧 `t.Skip` 真源缺口 · `U11`（`:580`）Go 侧潜伏缺陷；⚠️ `:1146` 诚实边界：**本域新守门只到「编译通过」、测试未重跑 ⇒ 不得宣布「comment 守门已绿」**。
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
- `docs/DRIFT/_cross/component-coverage-matrix.md` ⇒ **「当前态」节（`:23-78`）才是现状**，历史节（2026-10-23 快照）只作追溯（`:5-14`）；⚠️ 其「当前态」节是 **2026-10-25** ⇒ `article-api` 残留 `-007`（`:40`）已被 `article/api.md:723` 推翻、计数 86 已被 **102** 推翻 ⇒ **共享面落后于域台账**；仍有效的是「**rpc 必须先于 api**」的排序约束（`:13-14`）。
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

- `docs/DRIFT/comment/api.md` ⇒ §5 未决 `U1~U11`；`U4`（共享台账 `_cross/new-domains-2026-10-23.md:43-45` 的「熔断缺失」结论**已被实测推翻** ⇒ 需收口执行者更正共享台账）· `U10`（非 JSON Content-Type 下 servlet 读不出字节 ⇒ 机制静态未查实，**可观察行为已定**）；判定行 `:32` 的 🔴 标记**已过期**（3 条 MISMATCH 全修）。
- `docs/DRIFT/comment/rpc.md` ⇒ §5 `U1~U16`：**`U15` = global `E65` 出向 gRPC 不继承 deadline ⇒ A 类 · 唯一阻断阶段一条目（落点 100% 在共享层）** · `U16` = `E66` Redis 不感知 deadline（**源码级**，未经双侧活体）· `U13` 跨栈 `grpc-status:1012` **协议层不可修** · `U10` 修复已落码**销账待合回** · `U4` Go 侧 `t.Skip` 真源缺口 · `U11` Go 侧点赞清位潜伏缺陷（RAG 侧已正确）；⚠️ `:1146` 诚实边界：**本域新守门只到「编译通过」、测试未重跑 ⇒ 不得宣布「comment 守门已绿」**。
- `docs/DRIFT/comment/common.md` ⇒ ✅ **SEMANTICALLY EQUIVALENT**（错误码契约层）· MISMATCH 0 条 · OBS 1 条；⚠️ 同表 `:110`（🔴）与 `:112`（🟢 已修复并活体验证）**两行口径相反 ⇒ 读 `:110` 会误判为仍断**。
- `docs/DRIFT/comment/proto.md` ⇒ 消费方式层 EQUIVALENT；`U4` 跨栈业务码不可传输 = **协议层**；`package` 归一**已签字**（`SD-2026-10-23-04`）⇒ gRPC 方法全名 `/comment.CommentService/*` vs Go `/pb.CommentService/*`（wire 层可见）。
- `docs/DRIFT/comment/vs-common.md` ⇒ §2 九轴（**A8 = L4**、A6 半边已 L4 · A1/A2/A3/A4/A5/A7 = L1~L3）· §6 域内未闭合清单（`C-1~C-9`）· §4.2 拍板结论；**文末定级附录（2026-10-30）= 权威面**：`C-3` 判 **A/P1 · 阻断阶段一**（现行开放面 = `:112` 的 global E65）· `G4-16` 判 **C/P1**（派单本体 · 落点 100% 在共享层）· `C-5` 判 **B/P3 · 域侧可自修**。
- `docs/Intentional_Deviations/comment/` ⇒ 共 10 条：✅ 已签 **5**（`SD-2026-10-23-04` package 归一 · `CAND-02` `report_record` 表 Go 侧漏建 · `CAND-06` Go 的 `attribute & ~?` **必然报错** · `CAND-07` HS384/512 Go 接受 RAG 拒 · `CAND-08` 保护层覆盖范围 RAG 更严）· ⏳ 未签字候选 **4**（`CAND-01` 时间列 NOT NULL DEFAULT · `CAND-03` authority/dispatch 默认关闭 · `CAND-05` Bearer 剥离长度边界（落点 `users-common` ⇒ G4）· `CAND-09` DDL 更严）· ⛔ **已作废 1**（`CAND-04` 前提被活体推翻）⇒ **登记类，不推拍板**。
- `docs/Pitfalls/comment/` ⇒ api 3 条 + rpc 16 条 + common 0 + proto 0；最关键：`PIT-COMMENT-RPC-02`「javadoc 里『对应 Go xxx.go:行号』的断言**本身可能与真源相反**」· `PIT-COMMENT-API-03`「**自己的 javadoc 会成为『证据』**」· `PIT-COMMENT-RPC-04`「Go 故意留零/不填是**契约** ⇒ 顺手补上 = 新增可观察行为（`owner_id` 还会泄露 up 主 ID）」· `PIT-COMMENT-RPC-05`「因错误原因而绿的假守卫 ⇒ **变异测试是唯一检验手段**」· `PIT-COMMENT-RPC-11`「**已应用的 Flyway 迁移连注释都不能改**」（改注释 = checksum 失败 = 组件起不来）。
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

- **本域不落码**，只写台账依据：
  - Go 侧实测：`service/comment/rpc/etc/comment.yaml` **无** `Prometheus` / `DevServer` 段（探针：`Prometheus{Host:""}` ＋ `DevServer{Enabled:false}`）⇒ **go-zero 层不起**
  - 但 Go 侧**自建** ⇒ `service/comment/rpc/comment.go:38-41` ⇒ **`0.0.0.0:9091` 的 `/metrics`**（`promhttp.Handler()` 默认 registry ＋ `metrics.InitMetrics(&c)` 注册的 `comment_*` counter，见 `service/comment/rpc/internal/metrics/metrics.go:41-50`）
  - ⇒ **派工给的前提（「Go 侧无该面」）被实测证伪** ⇒ RAG 侧 comment-rpc 缺该面 = **一条真实漂移** ⇒ 已按 C 类登记 ⇒ **下一轮补**
  - ⚠️ **下一轮补载体时要一并处理的两处**（本轮只登记，不动）：① `service/comment/comment-rpc/src/main/resources/application.yml:39` 的 `spring.main.web-application-type: none`（**与 favorite-rpc 同款**：不反转则补了 web starter 也不起容器）；② `service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/CommentRpcWebApplicationTypeGateTest.java:64` 那条守门（它断言 `web-application-type: none` **必须存在** ⇒ 载体落地后该断言要改成「必须有载体 ＋ 端口钉死」）

## 9. 🔴 **DDL 逐域机器核**（`--indexes` + `--check-source`）· 2026-10-29 · R3·T-3.1（G3 lane · `comment` 域）

> **本节为什么存在**：`tools/difftest/check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**从未被机器核过**；**默认不开 `--check-source`** ⇒ 只比活库、不比仓里的 `.sql` ⇒ 「迁移改了但没人重跑」**不可见**。
> 主线已把这两层补上并逐域实跑，结论只落在 `docs/PhaseGoal-judge.md` §七 **G-8 / G-9** ⇒ **域级证据链断了一节** ⇒ 本节把 `comment` 域的结论**落到本域台账**。
> ⚠️ **本节只落结论与证据，不改任何差异**（RoundPlan T-3.1：冒出的差异按域登记，不当场修）。
> ⚠️ **`comment` 域不是 N/A** —— `hot` 域才是 N/A；**本域两侧都有真库**（Go 侧 6 张表 / RAG 侧 7 张表）⇒ 本节是**实跑**结论。

### 9.0 🔴 **本域有一个「机器核跑不到」的坑：默认口径在列集合不一致处提前退出 ⇒ 7 张表里只核了 1 张**

> ⚠️ **2026-10-30 真实性复核（W-D lane · 本标题的措辞已过期，原标题保留 + 订正）**：标题里的「**机器核跑不到**」**只对默认口径成立** ——
> **默认口径**（`check-ddl-static.py --domain comment --indexes`）确实 `EXIT=2`（在已签字的 `report_record` 列集合差异处提前退出）⇒ **七项对照一个都跑不到**；
> **但**本节下方记录的**补跑口径**（`--rag-db` 指向一份「RAG 侧去掉 `report_record`」的同源副本库 ⇒ **工具与仓内文件零改动**）**能跑完** ⇒ **EXIT=1 / 23 处** ⇒ ⇒
> ⇒ **准确口径 = 「默认口径核不到 · 补跑口径核得到」** ⇒ ⇒ **本节 §9.1~§9.4 的 23 处结论全部是机器可复算的**（复算命令见 §9.1「命令 ④」· 2026-10-30 独立复跑一次逐条对上）。
> ⇒ ⚠️ **「需人工核对」这四个字只对那 2 处 IDENTITY 假阳性成立**（`subject.id` / `comment_like.id`）—— 机器的 DEFAULT 维度**看不见 IDENTITY 列** ⇒ 需人工闭合；**其余 21 处不需要人工核对**。
> ⇒ ⇒ 本节与 `docs/DRIFT/comment/rpc.md` §4.5.1 **现已口径一致**（原 §4.5 段落的「可空性 0 差异 · DEFAULT 0 差异 · 索引 16≡16」已订正）。核对日期 2026-10-30。

> ⚠️ **这一节是本域最重要的发现**，先说清楚，后面的表才有意义。

**现象**：`check-ddl-static.py` 的「列集合不一致」分支（脚本第 ~1050 行）在**列类型 / 可空性 / DEFAULT / 索引 / 约束 / 触发器**七项对照**之前**就 `return 2` ⇒ ⇒ **只要两侧表集合不一致，其余七个维度一个都跑不到**。

**本域实测（默认口径）**：

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b"
bash tools/difftest/ddl-parity.sh comment
python3 tools/difftest/check-ddl-static.py --domain comment --indexes --check-source
```

⇒ **两条命令都 EXIT=2**；输出末段是 8 行 `仅 RAG 有: report_record.*` ⇒ **Go↔RAG 的七项对照全部没跑到**。

⇒ **后果**：`docs/PhaseGoal-judge.md` §七 **G-8 / G-9** 那行对 `comment` 域记的残留是「**comment 域整张 `report_record` 表（8 列）仅 RAG 有**（已签字保留）」⇒ ⇒ **那一行只记下了「导致提前退出的那一条差异」，把另外 6 张表的对照结果整个漏掉了**（⇒ 详见 §9.7 的跨台账登记）。

**补跑口径（不改守门、不改仓内任何文件）**：用守门**自带的 `--rag-db` 覆盖参数**，把 RAG 侧指向一份**只删掉 `report_record` 表的同源副本** ⇒ 表集合对齐 ⇒ 七个维度就能真跑。

```bash
# ① 在对拍容器内造一份「RAG 侧去掉 report_record」的同源副本（TEMPLATE ⇒ 逐对象同源）
docker exec ddl-comment-pg psql -U admin -d postgres \
  -c "CREATE DATABASE rag_comment_db_cmp6 TEMPLATE rag_comment_db;"
docker exec ddl-comment-pg psql -U admin -d rag_comment_db_cmp6 -c "DROP TABLE report_record;"

# ② 用守门自带的 --rag-db 覆盖跑七项对照（工具与仓内文件零改动）
python3 tools/difftest/check-ddl-static.py --domain comment --indexes --rag-db rag_comment_db_cmp6
```

⇒ **EXIT=1**（**23 处**）⇒ 结论见 §9.3 / §9.4。
⇒ ⚠️ **本节的「23 处」结论来自这条补跑口径**，**不是**默认口径的产出；默认口径的产出就是 **EXIT=2**（本节两处都如实记）。

### 9.1 执行口径（命令原文 · EXIT · 日期 · 环境）

| 项 | 值 |
|---|---|
| 实跑日期 | **2026-10-29**（14:3x–15:0x 本机时间） |
| 工作树 / 分支 | `/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b` · `align/r3-ddl-g3-2`<br>基点 = `do-my-own-gh` @ **`d10227c5`** |
| 真源 | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` —— **冻结 · 只读**；脚本只**复制**副本，从不写入 |
| 容器 / 库 | `ddl-comment-pg`（`postgres:16-alpine` · 端口 **55566**）· Go 库 `go_comment_db` · RAG 库 `rag_comment_db`（补跑副本 `rag_comment_db_cmp6`） |
| Go 侧 schema 来源 | **真源自己的 `AutoMigrate`**：`service/comment/rpc/internal/model/db.go:15`（`db.AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{}, &CommentDomainFactOutbox{}, &CommentFactStream{})`）⇒ **6 张表** ⇒ 探针调的正是**真源自己的 `InitDB`**（`dsn` 探针）⇒ 跑的是**真源同一份 struct tag + 同一个 gorm 版本** |
| ⚠️ **Go 侧没有手写 `.sql`** | 本域的 `001/002/003` 在 `service/comment/rpc/internal/model/migrations/` 下，且**只被测试脚本执行**（`service/comment/rpc/internal/model/test-community-postgres.sh:47,49`）⇒ ⇒ **`ddl-parity.sh` 的 `GO_SQL` 清单对本域为空** ⇒ **Go 侧生产 DDL = `AutoMigrate` 产物**（这一点是 comment 域所有约束差异的根因，见 §9.4） |
| Go struct 出处 | `service/comment/rpc/internal/model/data_model.go`（`Subject` / `CommentContent` / `CommentIndex` / `CommentLike` / `CommentDomainFactOutbox` / `CommentFactStream`）· ⚠️ **`ReportRecord` 的 struct 存在**（`data_model.go:55-68`，`TableName()` → `report_record`）**但不在 `db.go:15` 的 AutoMigrate 列表里** ⇒ **表不会被创建** |
| RAG 侧 schema 来源 | 仓内 `service/comment/comment-rpc/src/main/resources/db/migration/`：`V1__create_comment_subject.sql` · `V2__create_comment_index.sql` · `V3__create_comment_content.sql` · `V4__create_comment_like.sql` · `V5__create_comment_report_record.sql` · `V6__create_comment_domain_fact_outbox.sql` · `V7__create_comment_fact_stream.sql` · `V8__align_comment_fact_delivery_with_go.sql`（**Flyway**；生产 `locations: classpath:db/migration` ⇒ `application.yml:110` · `baseline-version: 0` ⇒ `application.yml:109`） |
| 豁免名单 | **一个都没用**（`--allow-idx` / `--allow-default` **均未传**）⇒ 差异数是**原始值** |
| 两侧表集合 | Go `go_comment_db` = {`subject`, `comment_index`, `comment_content`, `comment_like`, `comment_domain_fact_outbox`, `comment_fact_stream`} = **6 张**<br>RAG `rag_comment_db` = 上述 **6 张 ＋ `report_record`** = **7 张** ⇒ ⇒ **表名两侧完全相同** ⇒ `table_map` 为空 |

**命令 ① —— 建库 + 双侧活体对拍（默认口径）**

```bash
bash tools/difftest/ddl-parity.sh comment
```

⇒ **EXIT=2**（runner 行 `=== 对拍退出码: 2 ===`）—— Go 探针打印 `AUTOMIGRATE_OK`；RAG 侧按序执行 `V1`→`V8`（`V8` 有一条 `NOTICE: trigger "comment_fact_identity_immutable" ... does not exist, skipping` ⇒ **RAG 侧建出了该触发器**）
⇒ 退出码 2 的含义 = **「列集合不一致」**（脚本自身的闸门），**不是**「有实质差异」也不是「环境不满足」。

**命令 ② —— 只读静态对拍（显式带 `--indexes --check-source`，默认口径）**

```bash
python3 tools/difftest/check-ddl-static.py --domain comment --indexes --check-source
```

⇒ **EXIT=2**，输出与 ① 相同（8 行 `仅 RAG 有: report_record.*`）。
⇒ ⚠️ **但「源码 ↔ 活库」那一维**在退出前**已经跑完**（脚本把 G-9 放在所有环境闸门**之前**执行）⇒ 该维结论 = **0 差异**（详见 §9.2 第 1 行）。

**命令 ③ —— 打印 RAG 侧 SQL 清单**

```bash
python3 tools/difftest/check-ddl-static.py --domain comment --print-rag-sql
```

⇒ **EXIT=0**，输出 **8 行**（V1~V8），与 ① 实际执行的 8 份 `.sql` **逐条一致** ⇒ **清单没有落后于仓**。

**命令 ④ —— 补跑口径（覆盖 `--rag-db` ⇒ 让七项对照真跑）**

```bash
docker exec ddl-comment-pg psql -U admin -d postgres \
  -c "CREATE DATABASE rag_comment_db_cmp6 TEMPLATE rag_comment_db;"
docker exec ddl-comment-pg psql -U admin -d rag_comment_db_cmp6 -c "DROP TABLE report_record;"
python3 tools/difftest/check-ddl-static.py --domain comment --indexes --rag-db rag_comment_db_cmp6
```

⇒ **EXIT=1**（**23 处**）—— 逐条见 §9.3 / §9.4。
⇒ ⚠️ **本命令不传 `--check-source`** —— 副本库少了 `report_record`，而仓里 `V5` 会把它建出来 ⇒ 源码重放库是 **7 张**、副本活库是 **6 张** ⇒ 该维度必然报 1 处**表集合**差异 ⇒ ⇒ **该维度以命令 ② 的默认口径为准（0 差异）**。

### 9.2 八个被核维度的逐项结论

| # | 维度 | 两侧规模 | 结论 | 计入失败数 |
|---|---|---|---|---|
| 1 | **源码 ↔ 活库**（G-9 `--check-source`，**默认口径**） | 7 张表 ↔ 7 张表 | ✅ **0 差异** —— 仓里的 8 份 `.sql` 在**干净临时库** `rag_comment_db_srccheck` 上原样重放后，与活库**逐项一致**（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器） | 0 |
| 2 | **列类型 / 长度**（**补跑口径**） | **52 列 ↔ 52 列**（键集合一致 ✓） | ✅ **0 差异** —— 脚本原话「**逐字相同 52 列**」⇒ **8 张表的其余 6 张，类型/长度 0 差异** | 0 |
| 3 | **可空性（NOT NULL）**（补跑口径） | 52 列 | 🔴 **8 处差异** —— 逐条见 §9.4① | **8** |
| 4 | **DEFAULT**（补跑口径） | 52 列 | 🔴 **10 处差异** —— 逐条见 §9.4②（**其中 2 处经人工闭合为工具方法假阳性**） | **10**（**其中 2 处为假阳性** ⇒ 实质 **8**） |
| 5 | **索引**（`pg_indexes`，补跑口径） | Go **11** / RAG **13** | 🔴 **2 处差异**（**均仅 RAG 有**）—— 逐条见 §9.4③ | **2** |
| 6 | **唯一约束 / 主键**（`pg_constraint`，补跑口径） | Go **6** / RAG **9** | 🔴 **2 处差异**（**均仅 RAG 有**，`contype=c` 的 CHECK）+ **1 处语义等价**（`comment_like.idx_user_comment_like` ≡ `idx_user_comment_like`，**唯一约束 ↔ 唯一索引，载体不同** ⇒ **不计失败**） | **2** |
| 7 | **触发器**（`pg_trigger`，补跑口径） | Go **0** / RAG **1** | 🔴 **1 处差异**（**仅 RAG 有**：`comment_domain_fact_outbox.comment_fact_identity_immutable`） | **1** |
| 8 | **触发器形式**（`pg_get_triggerdef` → 结构化属性，补跑口径） | 两侧各 0/1，**可比对 0** | ⚪ **无对象可比** —— 脚本原话「两侧**没有可成对比对的触发器** ⇒ 本维度**什么都没比**（不是「已对齐」）」⇒ **单边触发器的形式未被比对**（其函数属性单列，见 §9.2 8′） | 0 |
| 8′ | **触发器函数体**（`pg_proc` → 结构化语义属性，补跑口径） | 两侧各 0/1，**可比对 0** | ⚪ **同上 ⇒ 无对象可比** —— 脚本原话「两侧**没有可成对比对的触发器函数** ⇒ 本维度**什么都没比**」⇒ 但**单边**触发器的函数属性被脚本单独打印了：`fn=reject_comment_fact_identity_change  rettype=trigger  args=<无>  volatility=VOLATILE  is_trigger=trigger  lang=plpgsql` | 0 |
| | **合计** | | | **23**（**8 + 10 + 2 + 2 + 1**）⇒ **实质 21**（扣掉 §9.4② 的 2 处工具方法假阳性） |

⇒ **本域 DDL 在 8 个被核维度上共 23 处机器报出的差异 ⇒ 其中 21 处是实质差异、2 处是工具方法的假阳性（人工闭合）。**

### 9.3 「0 差异」项（逐条列全，不写「其余一致」）

**① 列类型 / 长度 —— 52 列逐字相同（补跑口径）**

- 脚本原话：`DDL 列级对比  go_comment_db=52 列  rag_comment_db_cmp6=52 列  （键集合一致 ✓）` + `逐字相同 52 列。`
- ⇒ **`report_record` 之外的 6 张表，列类型 / 长度 0 差异** ⇒ 符合 AGENTS.md §0 的总体结论「类型/长度 0 差异」。

**② 源码 ↔ 活库 —— 0 差异（G-9 维度，默认口径）**

- 目录扫描得到 **8 份**迁移脚本 ⇒ **清单由目录扫描产生**，不会落后于仓。
- 在**干净临时库** `rag_comment_db_srccheck` 上**原样重放**（交给 PG 自己解析）⇒ 与活库逐项对比：**表集合 7 ↔ 7** · **列集合 0 差异** · **列属性 0 差异** · **可空性 0 差异** · **DEFAULT 0 差异** · **索引 0 差异** · **约束 0 差异** · **触发器 0 差异**
- ⇒ 脚本原文：`✅ 仓里的迁移脚本**重放结果**与活库逐项一致（表 / 列 / 列属性 / 可空性 / DEFAULT / 索引 / 约束 / 触发器）`
- ⇒ **含义**：本域仓里的 `.sql` 与**当前活库**之间**没有分叉** ⇒ 「迁移改了没人重跑」这一类漂移**在 `comment` 域为零**。
- ⚠️ **本维度含 `report_record`**（7 张表全在内）⇒ **它是本域唯一覆盖了 `report_record` 这张表的机器证据**。

**③ 唯一约束的「载体不同」形态差（1 处，不计失败）**

| 表 | Go 侧 | RAG 侧 | 判定 |
|---|---|---|---|
| `comment_like` | `idx_user_comment_like`（**唯一约束** `contype=u`） | `idx_user_comment_like`（**唯一索引**，载体不同） | ✅ **语义等价** —— 都保证 `(user_id, comment_id)` 唯一 ⇒ PG 层面的唯一性判定与冲突行为**完全相同** ⇒ 脚本按「语义等价（仅命名形态差）」归类，**不计失败** |

### 9.4 残留（逐条 · 定级 · 签字状态）

> ⚠️ **一条差异 ≠ 一个缺陷** —— 下面 23 处机器报出的差异，按**根因**归成 **6 条**。
> ⚠️ **签字状态先查 `docs/Intentional_Deviations/comment/`**（AGENTS.md §0 第 1 条）：**已签字的是决策不是缺陷 ⇒ 不重复质疑、也不「修回去」**。

**① 可空性 8 处（4 张表 × `created_at` / `updated_at`）**

| 表 · 列 | Go 侧 | RAG 侧 |
|---|---|---|
| `subject.created_at` / `subject.updated_at` | `nullable=YES` | `nullable=NO` |
| `comment_index.created_at` / `comment_index.updated_at` | `nullable=YES` | `nullable=NO` |
| `comment_content.created_at` / `comment_content.updated_at` | `nullable=YES` | `nullable=NO` |
| `comment_like.created_at` / `comment_like.updated_at` | `nullable=YES` | `nullable=NO` |

- Go 侧成因：gorm tag 只有 `autoCreateTime` / `autoUpdateTime` ⇒ **不产生 NOT NULL、也不产生 DB 层 DEFAULT**（Go 出处 `service/comment/rpc/internal/model/data_model.go:14-15,34-35,47-48,95-96`）⇒ ⇒ **Go 侧落 NULL**，RAG 侧落「入库时刻」。
- RAG 侧出处：`V1:27-28` · `V2:22-23` · `V3:18-19` · `V4:24-25`。
- **签字状态**：🟡 **`SD-2026-10-24-CAND-01`（待签字候选 · 未签字）** —— 该条列了 **9 处**同族时间列（含 `report_record.created_at`）⇒ ⇒ **本轮实测到其中 8 处**（第 9 处在 `report_record` 上 ⇒ 因该表 Go 侧**整表不存在** ⇒ **不可比**）。
- **定级**：**C 类（阶段 2 最终工程建设项）** —— 理由：① **加强型差异**（RAG 更严）⇒ **不产生数据丢失 / 重复处理 / 状态错误**（RAG 侧所有写路径都显式传值 ⇒ 当前行为等价）⇒ 按 `docs/twoStage.md` §十 不构成**阶段 1 准入阻断**（**不是 A 类**）；② 改回去要删 9 处 `NOT NULL` ⇒ **会降低完整性**、且与同仓 `comment_domain_fact_outbox.created_at` 的 Go tag（`not null;default:CURRENT_TIMESTAMP`）**不自洽** ⇒ ⇒ 属「阶段 2 再决策」而非「阶段 1 必须修」；③ 归 **C 而非 D**，是因为签字档自己给的第三条路（去掉 `DEFAULT`、保留 `NOT NULL`）**改变了 DDL** ⇒ 需要单独决策 ⇒ **不能按「可实现或不适用」销号**。

**② DEFAULT 10 处（其中 8 处属 CAND-01 同族，2 处是工具方法假阳性）**

> ⚠️ **2026-10-29 真实性复核（W-D lane · 独立复跑确认）**：本节的 **23 处 / 可空性 8 处 / DEFAULT 10 处** 由 W-D lane **用 §9.0 的补跑口径独立复跑一次并逐条对上**（新建 `rag_comment_db_wd` 同源副本 ⇒ `DROP TABLE report_record` ⇒ `check-ddl-static.py --domain comment --indexes --rag-db rag_comment_db_wd` ⇒ **`EXIT=1` / 23 处**）⇒ **本节数字可复算（须按 §9.0 的补跑口径）**。
> ⚠️ **但默认口径仍跑不到**（`check-ddl-static.py --domain comment --indexes` ⇒ **`EXIT=2`** ⇒ 在列集合不一致处提前退出 ⇒ **七项对照一个都跑不到**）⇒ ⇒ **凡引用本节数字，一律须写「需人工核对（补跑口径）」，不得写成默认口径「可复算」**。
> ⚠️ **本条同时订正了同域另一份台账的相反表述** ⇒ `docs/DRIFT/comment/rpc.md` 的「补机器证据」块曾写「同一次运行里其余 6 张共享表 … 可空性 0 差异 · DEFAULT 0 差异 · 索引 16 ≡ 16 ⇒ 除 `report_record` 外两侧一致」⇒ **该结论已证伪**（实测：可空性 8 / DEFAULT 10 / 索引 11 vs 13 / 约束 2 处实质差 / 触发器 RAG 多 1）⇒ 已在该文件追加订正段。核对日期 2026-10-29。

| # | 表 · 列 | Go 侧 | RAG 侧 | 判定 |
|---|---|---|---|---|
| ②-1 ~ ②-8 | `subject` / `comment_index` / `comment_content` / `comment_like` 的 `created_at` / `updated_at` | `<NULL>` | `CURRENT_TIMESTAMP` | 🔴 **实质差异** ⇒ 与 ① 同根因 ⇒ 归 **CAND-01** ⇒ **C 类**（同 ① 的理由） |
| ②-9 | `subject.id` | `nextval('subject_id_seq'::regclass)` | `<NULL>` | ⚪ **工具方法假阳性（人工闭合）** ⇒ 见下方说明 |
| ②-10 | `comment_like.id` | `nextval('comment_like_id_seq'::regclass)` | `<NULL>` | ⚪ **工具方法假阳性（人工闭合）** ⇒ 见下方说明 |

**⚪ ②-9 / ②-10 为什么是假阳性（人工闭合，**非脚本原生结论**）**

- 守门的 DEFAULT 维度读的是 `information_schema.columns.column_default` ⇒ **对 IDENTITY 列恒为 NULL**（IDENTITY 的默认值存在 `pg_attribute.attidentity` 里，不进 `column_default`）。
- 实测（`pg_attribute` 直查）：

| 侧 | `attidentity` | `pg_get_expr(adbin)` | `information_schema.is_identity` |
|---|---|---|---|
| Go `subject.id` | **空**（非 IDENTITY） | `nextval('subject_id_seq'::regclass)` | `NO` |
| RAG `subject.id` | **`d`**（= `BY DEFAULT`） | **空** | `YES`（`identity_generation = BY DEFAULT`） |

- ⇒ **RAG 侧写的是 `BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`**（`V1:19` · `V4:17`，脚本头自带出处注释：`-- Go: Id int64 \`primaryKey;autoIncrement\``）⇒ **PG 会自动供值**；Go 侧写的是 `bigserial` ⇒ **PG 也会自动供值**。
- ⇒ **两侧的「主键自动生成」语义完全一致** ⇒ ⇒ **这不是 DDL 漂移，是「identity 列 vs serial 列」的写法差** ⇒ **工具的 DEFAULT 维度看不见 IDENTITY 列 ⇒ 报成差异** ⇒ ⇒ **人工闭合为「非真差」**。
- ⚠️ **与 `users` 域那条不同**：`docs/DRIFT/users/vs-common.md` 记的 `nextval('users_id_seq')` ≡ `nextval('users_user_id_seq')` 是**序列名不同但语义等价**（脚本自己判为非真差）⇒ **本域这两处是同一个工具盲点** ⇒ **登记给收口执行者**（脚本层改进项，**本 lane 不改 `tools/difftest/check-*.py`**）。

**③ 索引 2 处（均仅 RAG 有）**

| 索引 | 唯一性 | 列 | 出处 |
|---|---|---|---|
| `idx_comment_fact_delivery` | non-unique | `delivery_status`, `created_at`, `event_id` | Go 的 `002_comment_dc_wire.sql`（**测试夹具**，不进生产）⇒ RAG `V8` |
| `idx_comment_legacy_fact_aggregate` | non-unique | 部分索引 `((payload...` | Go 的 `001/003`（**测试夹具**）⇒ RAG `V6/V8` |

- **签字状态**：🟡 **`SD-2026-10-24-CAND-09`（待签字候选 · 未签字）** —— 该条逐条列了**同样这 5 个对象**（本 2 个索引 ＋ 2 个 CHECK ＋ 1 个触发器）⇒ ⇒ **本轮独立复跑，逐条命中**。
- **定级**：**D 类（经分析确认可不实现或不适用的事项）** —— 理由：① **纯性能**（无行为差异）⇒ **不产生数据丢失 / 重复处理 / 状态错误** ⇒ **不是 A 类**；② **Go 侧的生产路径根本没有这些对象**（`001/002/003` 只被 `service/comment/rpc/internal/model/test-community-postgres.sh:47,49` 执行 ⇒ **是测试夹具，不进生产**）⇒ ⇒ 「RAG 比 Go 多」这件事**没有 Go 生产 DDL 作为对照基准** ⇒ ⇒ 按 §十「D 类 = 经分析确认可不实现或不适用」⇒ **可接受 RAG 更严 / 更全** ⇒ **不修、不改回**。

**④ CHECK 2 处（均仅 RAG 有，`contype=c`）**

| 约束 | 定义 | 备注 |
|---|---|---|
| `comment_domain_fact_outbox.comment_new_fact_has_dc_wire` | `CHECK ((aggregate_id IS NOT NULL) AND (fact_version IS NOT NULL) AND (delivery_envelope IS NOT NULL)) NOT VALID` | `NOT VALID` ⇒ 只对**新写入**生效，不校验存量行 |
| `comment_fact_stream.comment_fact_stream_version_range` | `CHECK ((version >= 1) AND (version <= '9007199254740991'::bigint))` | `version` 从 1 递增 ⇒ 要到 2^53-1 才触发 |

- **签字状态**：🟡 **`SD-2026-10-24-CAND-09`（待签字候选 · 未签字）** ⇒ 同 ③。
- **定级**：**D 类** —— 理由同 ③（① 不可观察：现有写入 / 投递路径**永远填齐**这三个字段、`version` 从 1 递增 ⇒ 正常路径不可能触发 ⇒ **不产生数据丢失 / 重复处理 / 状态错误**；② Go 生产 DDL 无对应物（`001/002/003` 是测试夹具）⇒ ⇒ 可接受 RAG 更严 ⇒ 不修、不改回）。

**⑤ 触发器 1 处（仅 RAG 有）**

| 触发器 | 事件 | 表 | 函数 |
|---|---|---|---|
| `comment_fact_identity_immutable` | `BEFORE UPDATE` | `comment_domain_fact_outbox` | `reject_comment_fact_identity_change()` |

- 脚本打印的函数属性：`fn=reject_comment_fact_identity_change  rettype=trigger  args=<无>  volatility=VOLATILE  is_trigger=trigger  lang=plpgsql`（**单边** ⇒ 不与 Go 比）。
- **签字状态**：🟡 **`SD-2026-10-24-CAND-09`（待签字候选 · 未签字）** —— 该条把本触发器列为 5 个对象里**「唯一原则上可观察的」**，并写明「**但现有写入 / 投递路径都不碰受保护列** ⇒ **当前不可观察**」。
- **定级**：**D 类** —— 理由：① Go 生产 DDL 无对应物（同 ③/④ 的根因）⇒ ⇒ 无对照基准 ⇒ 可接受 RAG 更严；② ⚠️ **但它是本域唯一的「可观察面」** ⇒ ⇒ 若将来出现**第二个写入方**去 UPDATE 受保护列 ⇒ RAG 抛错、Go 不抛 ⇒ ⇒ **不按 C 类销号，保留 D 类并把「可观察面」写进登记**（⇒ 与 `favorite` 域 `DEV-FAV-RPC-007` 的同款「什么时候会变成真差异」条件一致）。

**⑥ `report_record` 整张表（8 列 ＋ 复合唯一索引 `idx_user_comment_report`）—— ✅ **已签字****

| 项 | 值 |
|---|---|
| 机器报出的形态 | **仅 RAG 有 8 列**（`report_record.id` / `.user_id` / `.target_type` / `.target_id` / `.comment_id` / `.reason` / `.detail` / `.created_at`）⇒ **默认口径在此提前退出（EXIT=2）** |
| Go 侧事实 | `db.go:15` 的 AutoMigrate 列表**不含** `&ReportRecord{}` ⇒ **表不会被创建**；`data_model.go:55-68` 的 struct 存在且 `TableName() = "report_record"` ⇒ 代码路径完整；`comment.go:242-250` 的 `InsertReport` 用 `ON CONFLICT DO NOTHING` ⇒ 表不存在时**插入报错** ⇒ logic 发 `errmsg.ErrorDbInsert`(5004) |
| RAG 侧事实 | `V5__create_comment_report_record.sql` 建表（8 列 ＋ 复合唯一索引 `idx_user_comment_report`） |
| **签字状态** | ✅ **已签字**（2026-10-24，用户 Jason）⇒ **`SD-2026-10-24-CAND-02`** ⇒ **是决策不是缺陷 ⇒ 不重复质疑、也不「修回去」** |
| 签字结论原文 | 「**保留** `V5__create_comment_report_record.sql` ⇒ RAG 侧 `report_record` 表**继续存在**，`ReportComment` 在 RAG 侧**继续可用**（Go 侧仍会 5004，这是**已知的真源侧缺陷**）」 |
| **定级** | **D 类（经分析确认可不实现或不适用的事项）** —— 理由：① **已签字** ⇒ 按 §十 属「经分析确认」⇒ 不修；② **不是 A 类**：Go 侧 `ReportComment` 必然 5004 ⇒ 是**真源侧缺陷**，不是 RAG 的问题 ⇒ **RAG 更全**，不产生数据丢失 / 重复处理 / 状态错误；③ ⚠️ **撤回方式备查**：删 `V5` 迁移 ⇒ RAG 侧退化成与 Go 一致的「全新部署必 5004」；但**已应用的迁移不可回滚**（Flyway 只增不改）⇒ 真要撤需**新写一个 V9 DROP TABLE** |

**残留汇总（按根因归条 ⇒ 6 条）**

| # | 根因 | 机器报出 | 实质 | 签字 | 定级（§十） |
|---|---|---|---|---|---|
| ① | 时间列 `NOT NULL` | 8 | **8** | 🟡 `CAND-01`（待签字） | **C** |
| ② | 时间列 `DEFAULT CURRENT_TIMESTAMP` | 8 | **8** | 🟡 `CAND-01`（待签字） | **C** |
| ③ | 额外索引 2 | 2 | **2** | 🟡 `CAND-09`（待签字） | **D** |
| ④ | 额外 `CHECK` 2 | 2 | **2** | 🟡 `CAND-09`（待签字） | **D** |
| ⑤ | 额外不可变触发器 1 | 1 | **1** | 🟡 `CAND-09`（待签字） | **D** |
| ⑥ | 整张 `report_record` 表 | （8 列） | **1（表级）** | ✅ **`CAND-02`（已签字）** | **D** |
| — | **`id` 的 `nextval` vs IDENTITY** | 2 | **0** | — | ⚪ **工具方法假阳性 · 人工闭合** |
| | **合计** | **23** | **21** | | |

### 9.5 变异验证（证明守门有牙齿，不是假绿）

> ⚠️ **为什么不直接改仓内的 `.sql`**：`service/comment/**` 的代码由本波次另一条 lane 在改 ⇒ 本轮**零改动仓内迁移脚本** ⇒ 变异走**临时副本**（任务书允许的路线）：把 `check-ddl-static.py` 与该域的 `db/migration/` 目录**原样拷到一棵临时树**（保持相对路径 ⇒ `REPO_ROOT` 与仓内布局一致），**逐字节校验 sha256 相同**，再在**副本**上做变异。

**变异 ①：`subject.total_count` 的列类型（`BIGINT` → `INTEGER`）**

| 步骤 | 命令 | 结果 |
|---|---|---|
| 副本 sha256（变异前） | `shasum -a 256` 副本 vs 仓内 8 份 `.sql` | **8/8 逐字节相同**（`V1` = `cd96a9f16ee95be65f34368d8fb7e8bff1c06137409d6146bca8faf978e4e7e3` · `V2` = `f9d2daef0599f7d4dce557e924b08ad9fe234e8a76711d63cc62a8d419511c79` · `V3` = `f4ce58b84c79cdd9df17a1f675967f63d5f4cf1e997f49db6f8ede512d157a3e` · `V4` = `5f46ed268aeec35889b4a1a96b4bf1cdcdea5b7dcaa7caa69631964b7c97f8ca` · `V5` = `7cdf003cb27ce49950bd7000c81631d1c4441b6d1da7d5cd9951f74c9fd12617` · `V6` = `0d4966bef568ce07bbbf9edf16a65a398bfdb51e38fc9c4ae41eb794e05a72dc` · `V7` = `59c3a29ad808fb63310aed77ad5040ba523f5ec8c21d154f9ad6965ddf8bba5b` · `V8` = `1d71a9c9ba0bdfc37d84fab0a886b6198629e1e690e2abc67ab3c7f81316d30e`） |
| 基线 | `python3 tools/difftest/check-ddl-static.py --domain comment --indexes --check-source`（临时树） | **EXIT=2**（列集合不一致：`report_record.*` 8 列仅 RAG 有） |
| 变异 | `V1__create_comment_subject.sql`：`    total_count  BIGINT       NOT NULL DEFAULT 0,` → `    total_count  INTEGER      NOT NULL DEFAULT 0,`（改**一个列的类型**，命中目标行 **1** 处） | — |
| 变异后重跑 | 同上命令 | **EXIT=1**（脚本规定「源码↔活库有实质差异 ⇒ 退出码 **1** 优先于 2」）· 源码↔活库 **转红**：<br>`🔴 列属性不同：subject.total_count  源码=('integer', '')  活库=('bigint', '')`<br>`❌ 源码 ↔ 活库**分叉 1 处** ⇒ 活库已过期于仓里的迁移脚本` |
| 还原 | 改回 `BIGINT` | — |
| 还原后重跑 | 同上命令 | **EXIT=2**（**回到基线**）· 输出回到 8 行 `仅 RAG 有: report_record.*` |
| **还原校验** | `shasum -a 256` 临时副本 vs 仓内文件 | **8/8 仍逐字节相同**（`V1` 仍是 `cd96a9f1…4e7e3`）⇒ 逐字节回到变异前 |
| 副本清理 | `rm -rf` 临时树 | **已删除**（复查：目录不存在）⇒ **工作树里不留临时副本** |

⇒ **结论**：变异**精确报出 1 处**、**指向被改的那一列**（`subject.total_count`），**没有多红也没有漏红** ⇒ 守门**不是假绿**；还原后**精确回到 EXIT=2**（基线）⇒ 无残留。
⚠️ **本域基线是 EXIT=2 而非 0/1** ⇒ 「变异必红 ⇒ 还原必绿」的**两端是 2 → 1 → 2** ⇒ ⇒ **且验证了脚本自己的一条规则**：「源码↔活库有实质差异时退出码 1 **优先于** 2」（脚本第 ~1055 行）⇒ **该规则本轮被实测命中**。

**仓内零改动核验**

```bash
cd "/Users/pr/Documents/java&golang/RAG.worktrees/r3-ddl-g3b"
git status --short -- service/     # ⇒ 空
git diff --stat -- service/        # ⇒ 0 行
```

⇒ `service/comment/**` **零改动**（含 `service/article/**` 与 `service/favorite/**`）。

### 9.6 证据强度 · 效力边界 · 未能核到的部分

**证据强度 = E4（`docs/twoStage.md` §8.1「运行证据」）＋ L4（域台账分层：运行面 · 双侧活体）** —— 两侧各建一个**真 PG 16** 库（Go 侧跑真源自己的 `AutoMigrate`、RAG 侧跑仓里的真迁移脚本）⇒ 逐列 / 逐索引 / 逐约束 / 逐触发器 dump 后机器比对。
⚠️ 本节所有 ✅ 一律读作「**在该证据强度下一致**」，不是已闭环（AGENTS.md §0 证据强度分层）。

**效力边界（越界即失效）**

| # | 边界 | 对本域是否成立 |
|---|---|---|
| 1 | **默认口径的七项对照在本域「根本没跑」**（EXIT=2 提前退出）⇒ ⇒ **「0 差异」不能从默认口径读出** | 🔴 **成立且是本域最重要的边界** ⇒ 本节的七项结论**全部来自 §9.1 命令 ④ 的补跑口径**（`--rag-db` 覆盖 + 同源副本库）⇒ **复现必须用那条命令**，不能用默认命令 |
| 2 | **补跑副本与活库的同源性** | ✅ **成立** —— `CREATE DATABASE ... TEMPLATE rag_comment_db` ⇒ **逐对象同源**（表 / 列 / 索引 / 约束 / 触发器 / 函数全带过来）⇒ 只 `DROP TABLE report_record` ⇒ ⇒ **除那张表外，其余 6 张表与活库**逐对象相同** ⇒ ⇒ 结论**可外推到活库**。⚠️ 副本是**一次性**的（补跑后已 `DROP`）⇒ 复现需重建（命令见 §9.0 / §9.1） |
| 3 | **`--check-source` 的重放引擎 ≠ 生产引擎** —— 重放用 **psql**；本域生产用 **Flyway 引擎**（`application.yml:110`） | ✅ **对本域不成立** —— 已核：8 份 `.sql` 的 `$$` 命中 **0** ⇒ 无美元引号块 ⇒ 两个引擎看到的语句序列相同 |
| 4 | **迁移清单 = 生产清单** —— `discover_rag_sql` 目录扫描的 8 份，与生产 Flyway `locations` 指向的目录内容**逐条一致**（命令 ③ 与 ① 的执行日志互证） | ✅ **成立** |
| 5 | **Flyway 账本表** —— 生产库会有 `flyway_schema_history_comment`（AGENTS.md §6.3 问题 4 的定案），但**对拍环境直接用 psql 放 `.sql`** ⇒ 两个库里**都没有**账本表 | ✅ **本轮无干扰**；工具靠 `exclude_tables=("flyway_schema_history",)` 前缀匹配排除（**已配置**） |
| 6 | **`baseline-version: 0`**（`application.yml:109`） | ⚠️ **本轮未核** —— 对拍用**全新空库** ⇒ baseline 不触发 ⇒ V1 正常执行。⚠️ **这正是该配置注释里点名的陷阱**（`application.yml:90-108`）⇒ **登记为「未核」** |
| 7 | **触发器函数体的语义**机器判不了 ⇒ 走人工逐条 | ⚪ **本域有单边触发器**（RAG 有、Go 无）⇒ **无 Go 侧可比对象** ⇒ ⇒ **函数体语义「未核」**（只能确认 RAG 侧那条**存在**且形态正常） |
| 8 | **`pg_constraint` 只比 `contype` + 定义文本，不解析外键的引用目标** | ✅ **不适用**（本域两侧 `contype='f'` 各 **0 行**，见 §9.6 的人工补查表） |
| 9 | **分区 / 存储过程 / 触发器函数** —— 脚本不查 | ✅ **不适用**（8 份 `.sql` 的**可执行部分**无 `PARTITION` / `PROCEDURE` 关键字 ⇒ 已人工核；两侧 `pg_partitioned_table` / `prokind='p'` 均 **0**） |

**人工补查（脚本**不查**的维度 —— 结论强度 = **L4 + 人工补查**，**非脚本原生结论**）**

| 维度 | Go 侧 `go_comment_db` | RAG 侧 `rag_comment_db` | 判定 |
|---|---|---|---|
| 外键（`pg_constraint` `contype='f'`） | **0 行** | **0 行** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 分区表（`relkind='p'` / `pg_partitioned_table`） | **0** / **0** | **0** / **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 存储过程（`prokind='p'`） | **0** | **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 函数（`prokind='f'`，**非触发器**） | **0** | **0** | ⚪ 两侧各 0 ⇒ **无对象可比** |
| 触发器函数（被 `pg_trigger` 引用） | **0** | **1**：`reject_comment_fact_identity_change` | 🔴 **单边**（RAG 独有）⇒ 已归入 §9.4 ⑤ |

**未能核到的部分（明确写「未核」+ 原因，不含糊）**

| # | 未核项 | 原因 | 影响 |
|---|---|---|---|
| 1 | **共享库拓扑下的 Flyway `baseline-version: 0` 行为** | 对拍用**全新空库** ⇒ baseline 不触发 ⇒ 该配置**未被本节验证** | ⚠️ 该维度的守门在域侧真 PG + 真 Flyway 引擎的用例里（`application.yml:90-108` 明写这个陷阱）⇒ 本节**不重复核**，只登记边界 |
| 2 | **RAG 侧单边触发器的函数体语义** | 机器只比结构化属性；且 Go 侧**无对应触发器** ⇒ **无对象可比** | ⇒ 本域「事实信封不可变」这条语义**只有 RAG 侧有载体** ⇒ ⇒ 该语义**无法做双侧对照**（已在 §9.4 ⑤ 登记） |
| 3 | **`report_record` 表自身的列级 / 索引级双侧对照** | Go 侧**整表不存在** ⇒ 无样本 | ⇒ 该表的 8 列 ＋ `idx_user_comment_report` **只有 RAG 侧样本** ⇒ ⇒ 本节对它**只能登记「已签字的 RAG 多一张表」**，**不能**给「列级一致」的结论 |
| 4 | **索引是否被真实查询计划选中** | 需 `EXPLAIN` + 真实数据量 ⇒ 本轮**未核** | 本域 2 个 RAG 独有索引是**纯性能** ⇒ 实际代价未量化 |
| 5 | **DEFAULT 维度的 IDENTITY 列盲点**（`id` 的 `nextval` vs IDENTITY） | **工具方法的盲点** ⇒ 不是本域能自行闭合的 ⇒ 人工闭合已做（§9.4 ②-9/②-10） | ⇒ **守门本身对该形态仍会报差异** ⇒ ⇒ **登记给收口执行者**（脚本层改进项；**本 lane 不改 `tools/difftest/check-*.py`**） |

### 9.7 与其它台账的关系（指针，不改别的文件）

| 台账 | 本节与它的关系 |
|---|---|
| [`docs/Intentional_Deviations/comment/rpc.md`](../../Intentional_Deviations/comment/rpc.md) | **本节全部 21 处实质差异在此有归属**：`CAND-01`（时间列 8+8，🟡 待签字）· `CAND-09`（约束 / 索引 / 触发器 5 处，🟡 待签字）· `CAND-02`（`report_record` 表，✅ **已签字** ⇒ **不重复质疑、不改回**）⇒ ⇒ **本节不新增签字条目**，只把机器结论落到域台账 |
| [`docs/DRIFT/comment/rpc.md`](rpc.md) · [`api.md`](api.md) · [`common.md`](common.md) | 本域**组件级**台账；本节是 **DDL 这一维度**的域级落账 ⇒ 与那几份台账的其它维度结论**互不冲突** |
| [`docs/PhaseGoal-judge.md` §七 **G-8 / G-9**](../../PhaseGoal-judge.md) | 🔴 **该文件对 `comment` 域的那一行不完整** —— 它记的残留是「comment 域整张 `report_record` 表（8 列）仅 RAG 有（**已签字保留**）」⇒ ⇒ **那正是导致默认口径提前退出（EXIT=2）的那一条** ⇒ ⇒ **另外 6 张表的 21 处实质差异没有进那一行**（其中 16 处属 `CAND-01`、5 处属 `CAND-09` ⇒ **都已在签字档里**，只是没进主线的残留汇总）。⚠️ **该文件主线写 ⇒ 本 lane 只读 ⇒ 只在此登记，报给收口执行者** |
| [`docs/DRIFT/_cross/ddl-type-divergences.md`](../../DRIFT/_cross/ddl-type-divergences.md) | 🔴 **属 `_cross` ⇒ 本 lane 无写权** ⇒ 只在此登记：该文件对 `comment` 域「类型/长度 0 差异」的记录**与本节一致**（52 列逐字相同）⇒ ⇒ **无需更正**；但**它若也只记了 `report_record` 一条 ⇒ 同样不完整** ⇒ 报给收口执行者 |
| [`docs/DRIFT/_cross/open-items.md`](../../DRIFT/_cross/open-items.md) | 🔴 **属 `_cross` ⇒ 本 lane 无写权** ⇒ 只在此登记：本节若与该文件 P2 行的 `comment` 记数**不一致**（它可能只记了 `report_record`）⇒ ⇒ 报给收口执行者 |
| 本文件的 **§2 A8 轴** | ⚠️ 本节**不改** A8 的结论（A8 记的是 DDL 列级一致面）⇒ ⇒ **A8 原结论不变**；本节是**在其之上补了约束 / 索引 / 触发器 / 源码↔活库四层**，且**把 21 处加强型差异显式登记**（A8 原行**未提**这些）⇒ ⇒ **A8 那格的「✅ DDL 列级一致」应读作「列级（类型/长度）一致」**，**不含**约束 / 索引 / 触发器面 |

---

## 📌 追加 · 🟡 **共享层轴后果：rpc 域首次成为 servlet web app（2026-10-30 · G3 第三轮）**

> 本节只记**共享层轴**的后果（域内落地的细节在同域的 `rpc.md`）。🟡 = **待共享层裁定**，**不是**缺陷。

**🔴 轴事实一：共享层那批 `@ConditionalOnWebApplication(SERVLET)` 的自动配置，在 rpc 域**首次生效**

- 触发条件：本轮给 `comment-rpc` 补了 `spring-boot-starter-web`（Actuator 的 servlet 载体）⇒ ⇒ **rpc 进程首次成为 servlet web app** ⇒ ⇒ 共享层里带 servlet 条件的**自动配置**（`service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 列的那几个）**第一次**会在 rpc 容器里装载。
- 实测命中（`comment` 域）：`com.rag.common.http.GoStyleJsonContentTypeAdvice`（`@AutoConfiguration` ＋ `@ConditionalOnWebApplication(SERVLET)` ＋ `@RestControllerAdvice`）⇒ 它**只改 JSON 响应的 `Content-Type`**（`service/common/src/main/java/com/rag/common/http/GoStyleJsonContentTypeAdvice.java:187-206`）⇒ ⇒ `/actuator/prometheus` 是 **text/plain** ⇒ **不受影响** ⇒ 实测抓取 **HTTP 200** ＋ 指标族正常；⇒ 但 `/actuator/health`（JSON）会拿到 `application/json;charset=utf-8`。
- ⇒ ⇒ 这条**正是**共享层正在裁的那个议题（「rpc 域观测端口要不要挂 Go 的 rest 中间件」）的**落点** ⇒ ⇒ **归 G4 裁定**，域侧**只登记、不自决**。

**🔴 轴事实二：`rag.side` 这个属性共享层**当前还没有消费点**

- 域侧本轮已在三份 rpc 的生产 yml 里写 `rag.side: rpc`（`comment` 域的落点是 `service/comment/comment-rpc/src/main/resources/application.yml`）⇒ 那是**为 G4 正在扩的那道门预留的对侧**（门若写成 `matchIfMissing=true` ⇒ **不写 = 默认 api 侧** ⇒ rpc 域会挂上 api 侧的 web Bean）。
- ⚠️ **如实标注**：**2026-10-30 实测** `grep -rn "rag.side" service/common/src/main/java/` **零命中** ⇒ ⇒ 共享层**当前还没有**消费这个属性的条件 ⇒ ⇒ 域侧这三行**现在还没有消费点** ⇒ ⇒ 门落地前**不生效**、落地后**必须已在位**。
- ⇒ ⇒ 域侧**运行期**断言已就位（`commentRpcSharedLayerWebBeanGateTest`，3 例：属性在位 ＋ **正向对照**（证明 servlet 前提成立 ⇒ 断言非空转）＋ api 侧那 5 个 web Bean **不在**容器里）⇒ ⇒ 它现在证明的是「那 5 个 `@Component` 中间件因为 rpc **不扫** `com.rag.common` 而不在容器里」⇒ ⇒ 等 G4 把门扩到全部 api 侧中间件后，**自动升级**为「`rag.side` 真的挡住了门」的运行期证据。

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


### lane W10

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| C-4 | C（阶段 2 最终工程建设项 · 部署形态／生产工程机制） | P2 | 否 | Go `service/comment/rpc/comment.go:38-41`（`go func() { http.Handle("/metrics", promhttp.Handler()); _ = http.ListenAndServe(":9091", nil) }()` ⇒ **独立 goroutine 起一个对外 HTTP 抓取面**）＋ `service/comment/rpc/internal/metrics/metrics.go:41-43`（`InitMetrics` 内 `prometheus.Unregister(collectors.NewGoCollector())` ＋ `prometheus.Unregister(collectors.NewProcessCollector(...))` ⇒ `:9091` **只暴露 5 个 `comment_*` 向量**）vs RAG `service/comment/comment-rpc/src/main/resources/application.yml:39`（`web-application-type: none` ⇒ **进程内无任何 HTTP connector**）＋ `:287`（`management:`）＋ `:291`（`endpoints.web.exposure.include: health,info,prometheus,metrics` ⇒ 暴露清单已配好但**无 web server 时不可达**）＋ `:302-304`（`management.metrics.enable.jvm: false` ／ `.process: false` ⇒ **同样只暴露 comment_***，指标内容两侧逐条对齐）⇒ ⇒ **差异仍成立**（Go 有 `:9091` 对外抓取面 ／ RAG 进程内无对外 HTTP 面 ⇒ **Prometheus 抓取侧拿不到指标**）⇒ 但**指标内容零差异** ⇒ 属**部署形态类**（不是代码缺陷）⇒ 三个选项（维持现状 ／ 开 `management.server.port` ／ 照 Go 起独立 promhttp）**都需拍板**（留档见 `docs/DRIFT/comment/vs-common.md:133-137`）⇒ ⇒ 剩余那部分 = **对外暴露面选型**（生产工程机制）⇒ **阶段二**收敛 ⇒ 归 C · **不阻断阶段一** | 2026-10-30 |
| C-5 | B（阶段 1 竞争力补强项 · 验证闭环） | P3 | 否 | Go `service/comment/common/errmsg/errmsg.go:37-65`（27 个码值 const 块）＋ `:67-95`（`codeMsg` 27 条中文文案）＋ `:97-103`（`GetErrMsg` 未知码回退 `codeMsg[Error]`＝`"FAIL"`）＋ `:15-17`（`Error()` ＝ `fmt.Sprintf("ErrCode:%d, Errmsg:%s", …)`）＋ `:33-35`（`NewGrpcErr` ＝ `status.Error(codes.Code(code), msg)`）vs RAG `service/comment/comment-common/src/main/java/com/rag/comment/comment/common/errmsg/CommentErrorCode.java:33-104`（27 码枚举）＋ `CommentErrorMessages.java:43-74`（27 条文案 `LinkedHashMap`）＋ `:83-89`（`getMsg` 未知码回退 `"FAIL"`）＋ `CommentGrpcError.java:72`（`BIZ_CODE_TRAILER_KEY`）＋ `:116`（`mapToGrpcCode`）＋ `:139`（`grpcError`）＋ `:194`（`bizCodeFromError`）⇒ ⇒ **A6 半边已闭**：`tools/errmsg-probe/comment/run-parity.sh` ＋ `mutate-parity.sh` ＋ `go/` ＋ `java/` 已在仓（commit `34bc2e6f` · 2026-10-29 实跑 run-parity EXIT=0 · 两侧 dump sha256 同为 `514e3a71…3e3e` · mutate EXIT=0）⇒ **A7 半边仍差一格**：proto **静态**守门已覆盖本域（`tools/difftest/check-proto-static.sh:119` 的 `DOMAINS=(` 数组里 `:123` 是 comment 域那一项 ＋ `tools/difftest/comment/check-proto-parity.py`）⇒ 但 **wire 字节级**双侧探针**本域仍缺**（`tools/proto-wire-probe/` 只有 `favorite/` ＋ `hot/` 两个目录）⇒ ⇒ **差异仍成立**（剩余那部分 = 「comment 域 proto wire 字节级双侧探针未落仓」；只影响**证据强度**，不改变行为判定 —— `docs/PhaseGoal-judge.md:264` 的 X-10 已判 ✅，其效力边界自记「静态文本 diff 不覆盖运行时语义」）⇒ ⇒ 归 **B**（阶段 1 竞争力补强 · 验证闭环）· **不阻断阶段一** | 2026-10-30 |
| G4-17 | C（阶段 2 最终工程建设项 · 共享基线归类订正） | P2 | 否 | Go `service/comment/rpc/internal/model/db.go:15`（`err = db.AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{}, &CommentDomainFactOutbox{}, &CommentFactStream{})` ⇒ **AutoMigrate 列表不含 `ReportRecord`** ⇒ Go 侧 `report_record` **从不被建出**）＋ `service/comment/rpc/internal/model/data_model.go:55`（`type ReportRecord struct {`）＋ `:66`（`func (ReportRecord) TableName() string {` ⇒ `"report_record"`）vs RAG `service/comment/comment-rpc/src/main/resources/db/migration/V5__create_comment_report_record.sql:26`（`CREATE TABLE IF NOT EXISTS report_record (` ⇒ **RAG 侧多一整张表**）＋ `docs/PhaseGoal-judge.md:583`（`**D-07**` 行**仍留在 §11.1「🔴 真实实现缺陷（必须修）」** —— 小节标题在 `:573`）＋ `docs/PhaseGoal-judge.md:604`（§11.4「⬜ 已签字偏离」**未收录** D-07）⇒ ⇒ **差异仍成立**（「**是决策不是缺陷**」这半边成立 —— 两侧确实不对齐且已签字 `SD-2026-10-24-CAND-02`；**但基线的归类与计数仍未订正** —— D-07 行的「归属」列自己写着「不是缺陷，不得再『修回去』」，却仍被摆在「必须修」的小节里 ⇒ **归类自相矛盾**）⇒ ⇒ 剩余那部分 = **把 D-07 从 §11.1 移到 §11.4 ＋ 订正 §11.1 的条数**，落点 = `docs/PhaseGoal-judge.md`（**共享面 · 机器生成 `docs/PhaseGoal/g*.md` 的唯一数据源**）⇒ ⇒ 归 C · **不阻断阶段一** | 2026-10-30 |
| PIT-GLOBAL-051 | D（经分析确认：本域面无待落码内容 · 落点 `service/common` 属 global 单写权，本域只读） | P3 | 否 | Go `service/comment/rpc/internal/logic/report_comment_logic.go:88-92`（`result = "sys_fail"` → `span.RecordError(err)` → `err = errmsg.NewGrpcErr(errmsg.ErrorDbInsert, "DB插入失败")` ⇒ **Go 侧同样改写异常**）＋ `service/user/user/rpc/internal/logic/getuserlogic.go:41-44`（Go 改写成 `status.Error(codes.Internal, "DB查询失败")` ⇒ 这一侧 RAG 更准）＋ `service/message/rpc/internal/logic/markconversationreadlogic.go:59-62`（Go **原样** `return nil, err`）vs RAG `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentDomainService.java:674-677`（`catch (RuntimeException e)` → `throw CommentExceptions.dbInsertOnReport()` ⇒ **RAG 侧也改写** ⇒ 与 Go 同形）＋ `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java:110`（`private static final String PG_QUERY_CANCELED = "57014"` ⇒ 「SQL 被 deadline 取消」在 RAG 侧的产出点）⇒ ⇒ 就 **comment 域面**而言，本条登记的「RAG 把异常改写得比 Go 更糊」**差异已消失**（两侧都改写 ⇒ 销案；台账 `docs/Pitfalls/global/cross-domain.md:2024` 已记「21 处销案」）⇒ ⇒ 剩下的是**一条踩坑纪律**（`:2038`「登记『某域把异常改写了』之前必须逐条核 Go 侧对应行号」· 状态 🔴 仍生效，见 `:2014`）＋ 落点 `service/common`（**global 单写权** ⇒ comment 域只读不写，见 `docs/DRIFT/comment/vs-common.md:29`）⇒ ⇒ **本域无待落码内容** ⇒ 归 D · **不阻断阶段一** | 2026-10-30 |

### lane W26

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| G4-1 | D（证明缺口已闭合 ⇒ 无待实现内容） | P3 | 否 | Go `service/common/observability/rpc.go:38`（`timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ Go 侧 ctx deadline 的唯一产生点）＋ `service/comment/rpc/internal/logic/get_comment_logic.go:49`（`ctx, cancel := context.WithTimeout(l.ctx, time.Second)` ⇒ comment 域业务侧 ctx deadline）＋ `service/comment/rpc/internal/model/comment.go:36`（`m.conn.WithContext(ctx).Transaction(...)` ⇒ GORM 把 ctx deadline 传进 SQL ⇒ PG 到点发 CancelRequest ⇒ **框架级保证**）＋ `service/comment/rpc/internal/model/comment.go:166`（`m.conn.WithContext(ctx).Model(&CommentIndex{})...` ⇒ 读路径同样带 ctx）vs RAG `service/common/src/main/java/com/rag/common/persistence/DeadlineQueryTimeoutInjector.java:94-98`（`@Intercepts(@Signature(type = StatementHandler.class, method = "prepare", ...))` ＋ `implements Interceptor` ⇒ 靠 **MP 自动配置收集 `Interceptor[]` Bean** 才生效 ⇒ 这正是本条所指机制）＋ `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/CommentRpcApplication.java:56,67`（`@Import({...})` 内含 `DeadlineQueryTimeoutInjector.class` ⇒ 生产装配面）＋ `service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java:119-133`（① 收集层：`MybatisPlusAutoConfiguration.interceptors` 字段内确有本拦截器）＋ `:135-142`（② 装载层：`SqlSessionFactory.getConfiguration().getInterceptors()` 内确有本拦截器）＋ `:144-166`（③ 行为层：真 PG ＋ 真 mapper，已过期 deadline ⇒ 查询以 57014／DEADLINE_EXCEEDED 失败，前置「无 deadline ⇒ 必须成功」⇒ 干净因果证据）＋ `service/common/src/test/java/com/rag/common/persistence/MybatisPlusProbeApplication.java:28-31`（`@Import({DeadlineQueryTimeoutInjector.class, SqlTimeoutStatusTranslator.class})` ⇒ **与生产同一种装配方式**，守门不测另一条路）＋ `service/common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports:4`（排序桥 `MybatisPlusOrderingBridgeAutoConfiguration` 注册在**共享层主 jar** ⇒ 探针自动吃到同一座桥 ⇒ Boot 4 下 MP 自动配置才被求值）＋ `service/common/src/main/java/com/rag/common/persistence/MybatisPlusOrderingBridgeAutoConfiguration.java:95-98`（排序桥本体）＋ `service/comment/comment-rpc/pom.xml:71`（`artifactId=common` ⇒ comment-rpc 经共享 jar 继承该桥，且本模块**无**自己的 `META-INF/spring/` 注册文件 ⇒ 继承链唯一）⇒ **差异已消失**（台账 `DRIFT/comment/vs-common.md:77` 记的「**全仓无任何测试**证明 `@Import` 的 `DeadlineQueryTimeoutInjector` 真进了 MyBatis-Plus 执行链」——该守门**已在共享层落地**：`service/common/src/test/java/com/rag/common/persistence/MybatisPlusInterceptorChainGateTest.java`，commit `e3f2046`「test(common): 钉死『MyBatis-Plus 自动收集 Interceptor[] Bean』这条机制（G3 单 1）」⇒ 实测 `git merge-base --is-ancestor e3f2046 HEAD` = **是** ⇒ **已进本树** ⇒ 台账 `DRIFT/comment/vs-common.md:103` 与 `:107` 那句「该 commit 未进本树／三个 commit 全部为否」**已过期**（实测 `a1a2e05`、`2e2a84f` 亦均为 HEAD 祖先）⇒ 2026-10-30 本机实跑 `mvn -o -pl service/common test -Dtest=MybatisPlusInterceptorChainGateTest`（真 testcontainers PG 16-alpine ＋ 真 mapper）⇒ surefire 报告 `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` ⇒ **3 层全绿** ⇒ 本条登记的缺口已闭合 ⇒ **经分析确认不需要再实现** ⇒ 无落码动作；⚠️ 残留仅在**文档面**：`DRIFT/comment/vs-common.md:103`／`:107` 的「未进本树」表述与 `:81-87` 的「单 1 仍成立、仍是阻塞项」补记已过期 ⇒ 属主线收口时的台账订正动作，**不构成阶段一准入阻断**，本轮不改台账） | 2026-10-30 |

### lane W6

| 编号 | 归属 | 优先级 | 阻断阶段一 | 依据 | 核对日期 |
|---|---|---|---|---|---|
| C-3 | A（阶段一准入阻断项 · 现行开放面 = `:112` 的 global E65） | P1 | 是（阻断阶段一） | 同一编号在本文件有三处登记，**现行开放面 = `:112` 的「C-3 / U15（= global E65）出向 gRPC 不继承请求 deadline」**：Go `service/comment/rpc/internal/logic/subject_fallback.go:12`（`resolveSubjectFallback(ctx context.Context, …)`）＋ `:17`（`articleRPC.GetArticle(ctx, …)` ⇒ 父 deadline 经 ctx 传播 ⇒ 到点即止）＋ `go-zero@v1.10.2/zrpc/internal/client.go:115`（`if c.middlewares.Timeout { interceptors = append(interceptors, clientinterceptors.TimeoutInterceptor(timeout)) }` ⇒ 客户端无条件 2s）vs RAG `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/client/ArticleRpcClient.java:71`（`GoStyleClientCallTimeout.withDeadline(stub)` ⇒ **只做到 2s 封顶**，不继承服务端剩余预算）＋ `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:192`（`applyTo`）＋ `:193`（`Long d = DEADLINE_NANOS.get()` ⇒ 为 null 时原样返回 stub）＋ `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:324`（`GoStyleRequestDeadline.begin(deadlineNanos)` ⇒ HTTP 路径的绑定点）＋ `service/common/src/main/java/com/rag/common/observability/RpcInterceptor.java:519`（**2026-10-30 共享层 `53bc05ec` 起**：`onHalfClose` 也绑 ⇒ **两个载体已连通**（同一绝对时刻，`:311-312` 算一次））⇒ ⇒ **差异已收窄到「精度」**：comment 域的出向出口 `GoStyleClientCallTimeout.withDeadline(stub)`（`client/ArticleRpcClient.java:71` ⇒ 实现 `:90-95`）**读 `GoStyleRequestDeadline.remainingMillis()`** ⇒ ⇒ **载体在位时它已经继承入向剩余预算**（= `min(2s, 剩余)`）⇒ ⇒ **「继承」这一半已闭合**（2026-10-30 源码级复核）⇒ ⇒ **残留 = 精度差**：`remainingMillis()` 把亚毫秒部分截掉（`GoStyleRequestDeadline.java:90-96`）⇒ 与 Go 的纳秒预算（`go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25`）**差 ≤1ms** ⇒ 已由 `docs/PhaseGoal-judge.md` v5 §五 X-6 判为 🔴 A 类（双侧活体 **2026-10-30 更新**：Go 与 RAG **均** `outgoing_carrier=present_expired` —— 该键已由 drift 键升为 **parity 键**（`tools/difftest/check-deadline-downstream.py:53`）⇒ ⇒ **仍漂移的是 `failed_fast`/`code`** —— ⚠️ **但这两个键测的是探针自身的 RAG 半边**：探针脚本 `tools/semantic-probe/deadline-downstream/run-deadline-downstream-parity.sh` 的期望表自述「**本探针的 RAG 半边手搓 Netty channel ⇒ 不走 devh 全局拦截器路径 ⇒ 测不到已落地的 GoStyleOutboundDeadlineInterceptor**」⇒ ⇒ **不能当作「域内 client 没消费载体」的证据** ⇒ ⇒ 域内是否继承**已于 2026-10-30 第五轮由守门在活体上判**（`service/comment/comment-rpc/src/test/java/com/rag/comment/comment/rpc/client/CommentOutboundDeadlineCarrierGateTest`：真 wire ＋ 真共享层 `RpcInterceptor` ⇒ 载体在 gRPC handler 线程**真的可见**；纳秒精度用例经**变异锚点**实测精确转红）⇒ ⇒ 本行的「跨组件不变量 C-3 破」**待 global 侧复核后销账**（落点在 `service/common` ⇒ 判定权不在本域））⇒ 跨组件不变量 C-3 破 ⇒ 阶段一判「❌ 未通过」⇒ 归 A 且阻断阶段一；另两处登记（`:59` 的「6 个业务 span 已补齐」＝ 2026-10-27 已落码闭合，`:220` 的「U10 RPC 超时 status description 两侧不同」＝ 同 global E30，已在另一 lane 判为闭合）不改变本行结论 | 2026-10-30 |
| C-6 | C（阶段 2 最终工程建设项 · 共享台账订正） | P2 | 否（不阻断阶段一） | 同一编号两处登记。`:62` 面（comment-api 的 JWT 验签被计入 3s 预算）已闭合：Go `service/common/observability/http.go:32`（`conf.Middlewares.Timeout = false` ⇒ 关掉 go-zero 原生 TimeoutHandler）＋ `:53`（`ctx, cancel := context.WithTimeout(r.Context(), timeout)` ⇒ 3s 计时器在**用户中间件**里起算）＋ `service/comment/api/commentservice.go:36`（`server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, …))` ⇒ 用户中间件在链上排在 Authorize **之后**：`go-zero@v1.10.2/rest/engine.go:108` 先 `appendAuthHandler`、`:110` 再 `chn.Append(middleware)` ⇒ 后 Append = 更靠内 = 更晚执行）＋ `go-zero@v1.10.2/rest/handler/authhandler.go:54`（`parser.ParseToken(r, secret, …)` ⇒ 验签在 Authorize 内 ⇒ 在 3s 预算**之外**）vs RAG `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/auth/GoStyleJwtAuthFilter.java:93`（`@Order(Ordered.HIGHEST_PRECEDENCE + 6)` ⇒ 验签 Filter）＋ `service/common/src/main/java/com/rag/common/spring/HttpMiddlewareFilter.java:40`（`@Order(Ordered.HIGHEST_PRECEDENCE + 7)` ⇒ 3s 计时器）⇒ 2026-10-27 已落码对齐（验签在计时器之前 ⇒ 预算外）。`:223` 面（共享台账的过期结论）**仍开着**：`docs/DRIFT/_cross/new-domains-2026-10-23.md:43` 至今仍写「已登记**未修**：gRPC client 熔断缺失」⇒ 而 RAG 侧熔断已存在（`service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:109`（`@GrpcGlobalClientInterceptor`）＋ `service/comment/comment-api/src/main/java/com/rag/comment/comment/api/CommentApiApplication.java:68`（`scanBasePackages` 含 `com.rag.common`）⇒ 该链已在 comment-api 装配）⇒ ⇒ **差异仍成立**：过期结论仍在共享台账里未订正 ⇒ 落点 = `docs/DRIFT/_cross/**`（共享文件）⇒ 属阶段二收口订正 ⇒ 归 C、不阻断阶段一 | 2026-10-30 |
| G4-16 | C（阶段 2 最终工程建设项 · 派单本体；落点 100% 在共享层） | P1 | 否（不阻断阶段一） | Go `service/comment/rpc/internal/cache/comment_subject_cache.go:84`（`c.rdb.Get(ctx, …)` ⇒ Go 侧缓存读写吃 ctx ⇒ deadline 到点即止）＋ `service/comment/rpc/internal/logic/subject_fallback.go:17`（`articleRPC.GetArticle(ctx, …)` ⇒ 出向 gRPC 吃 ctx ⇒ 到点即止）＋ `go-zero@v1.10.2/zrpc/internal/client.go:115`（客户端无条件 2s 封顶）vs RAG `service/comment/comment-rpc/src/main/java/com/rag/comment/comment/rpc/service/CommentCacheService.java:250`（`redis.opsForValue().get(...)` ⇒ 零 deadline 检查）＋ `service/common/src/main/java/com/rag/common/observability/GoStyleRequestDeadline.java:112`（`DEADLINE_NANOS.get()` 为 null ⇒ `applyTo` 原样返回 stub ⇒ gRPC 服务端路径恒 no-op）＋ `service/common/src/main/java/com/rag/common/observability/HttpMiddleware.java:324`（`GoStyleRequestDeadline.begin(...)` 是主代码唯一绑定点 ⇒ 只覆盖 HTTP worker）⇒ ⇒ **差异仍成立**：本单两半（`D8`＝出向 gRPC 不继承请求 deadline、`D9`＝Redis 调用不感知 deadline）都仍成立，落点 100% 在 `service/common` ⇒ 本域无法自修 ⇒ 阶段二由 G4 落码 ⇒ 归 C；⚠️ 其 `D8` 半边（= global `E65`）已由 `docs/PhaseGoal-judge.md` v5 判为 🔴 A 类、已在本表 `C-3` 行按 A 类登记（阻断阶段一），本行只承载派单本体 ⇒ 不重复计第二个阻断项 ⇒ 阻断阶段一 = 否 | 2026-10-30 |
