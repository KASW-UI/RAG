<!-- @new 2026-10-27 · `hot-common` lane 台账 · 主会话（Jason 指定）建 lane 时预填 · 基线 = `do-my-own-gh` @ 2026-10-27  · **2026-10-27 02:0x 改：归属 G3 波次工作树（`align/g3-article-comment-favorite-hot`）**  · **2026-10-27 03:0x G3 阶段 1 复核轮：§1 状态复核 ＋ §2 九轴逐轴填实 ＋ §3 自修项 ＋ §4 派单项（全部带核对日期与证据强度）** -->

# `hot-common` lane 台账 —— 「hot ↔ `service/common`」跨层对账

> **lane 语义**：本域与**共享机制层 `service/common`** 之间的漂移对账
> —— ⚠️ **不是**「本域自己的 `-common` 库」（那是 `service/hot/hot-common`，台账见 [`common.md`](common.md)）
> **总纲** ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](../_cross/domain-common-lanes.md)（**G1~G4 波次映射** · 9 条机制面轴 · 写权边界 · 跨域未闭合项）
> ⇒ **本文件由 `G3` 波次工作树独占**（**按域各一份** ⇒ 跨树文件级零重叠）；**共享面（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**`）合并后统一更新**（AGENTS.md §5.3d）

## 0. lane 身份

| 项 | 值 |
|---|---|
| lane（历史名） | `hot-common`（**原第 5/10 条**） |
| **现分支 / 工作树** | **`align/g3-article-comment-favorite-hot`** / `/Users/pr/Documents/java&golang/RAG.worktrees/g3-article-comment-favorite-hot` |
| 基点 | **`do-my-own-gh` @ 2026-10-27**（= lane 脚手架落地那次提交）⇒ 主干再前进时 lane `merge` 一次即可（**文件级不重叠 ⇒ 零冲突**） |
| 本域模块 | `service/hot/{hot-api, hot-rpc, hot-common, hot-proto}` |
| 域侧台账 | [`api.md`](api.md) · [`rpc.md`](rpc.md) · [`common.md`](common.md) |
| **写权** | ✅ 可写**本域** `service/hot/**` ＋ `docs/DRIFT/hot/**`；🔴 **`service/common/**` 只读**（只有 `align/g4-service-common` 能写）；⚠️ 旧的 `align/global` 全域 lane **已弃用** |
| common 侧机制面台账 | [`docs/DRIFT/common/`](../common/)：`deadline.md` · `http.md` · `protection.md` · `audit.md` · `alignment-2026-10-23.md` |
| **本轮复核口径** | **核对日期 2026-10-27** · 阶段 1（**全只读对账**，不落任何代码）· 真源 = `Sea-RideTheWind_golang`（`go.mod:17` 锁 go-zero **v1.10.2**）· **证据强度上限 L3**（本域**无**域级双侧活体探针 —— 仓内 `tools/*-probe/` 的域级实例**只覆盖 favorite**） |

## 1. 本域侧已知的 common 相关条目（**2026-10-27 抽取 + 同日复核**）

| 编号 | 摘要 | 落点 | 状态 | 域台账 |
|---|---|---|---|---|
| `DRIFT-HOTAPI-024` | Content-Type charset（共享层 `GoStyleJsonContentTypeAdvice`） | `service/common` | ✅ **已修**（global T-6）⇒ 10 个 api 全生效 | [`api.md`](api.md) |
| `DRIFT-HOTAPI-014` | 跨域移交 `service/common` 的条目 | `service/common` | 🟡 **跨域移交**（nosniff 已实现） | [`api.md`](api.md) |
| `DRIFT-HOTRPC-004` | client breaker 是否真在链上并会因超时打开（`zrpc/internal/client.go:112-113` ＋ `breakerinterceptor.go:13-18`） | `service/common`（装配层） | ✅ **已闭合**（第十轮）—— ⚠️ **本轮复核：该闭合指的是 `hot-api` 侧**（域内有 `GoStyleGrpcClientBreakerWiringTest` ＋ `GoStyleGrpcClientBreakerE2ETest`，均在 `service/hot/hot-api/src/test/**`）⇒ **`hot-rpc`（纯 gRPC 进程）侧仍缺** ⇒ **不是同一件事**，见 §2 的 A2 与 §4-G4-2 | [`api.md`](api.md) · [`rpc.md`](rpc.md) |
| `D14` / `D25` / `D27` | 三条**移交 `service/common`** 的条目 | `service/common` | ✅ **已全部闭合**（2026-10-26 域内未闭合项已清空） | [`api.md`](api.md) |
| `DRIFT-HOTAPI-030` | Tomcat 参数个数口径 | `service/common`（X-6） | ✅ **已落地**（global 第十四轮）<br>✅ **本域侧已验**（核对日期 **2026-10-27**）：`hot-api/.../application.yml:141` = `mode: envelope` ⇒ **与 Go 同向**（Go 侧 `service/hot/api/hotcenter.go:53` **注册了** `httpx.SetErrorHandler` ⇒ 解析失败走业务信封 ⇒ **200 + `{code:5001}`**） | [`api.md`](api.md) |
| （域侧 · 非编号） | `hot-rpc` 缺 `baseline-version: 0` | — | ✅ **N/A（不适用）**（核对日期 **2026-10-27** 实测）：`hot-rpc/.../application.yml` **无 `flyway:` 段** ⇒ hot 域**两侧都没有数据库**（Go 侧全域无 gorm，只有 Redis ＋ Kafka）⇒ **本维度无对象可比**，不是缺陷 | — |

> ⚠️ **写死数字/状态的纪律**（AGENTS.md §6）：本表是**抽取快照**；每条**逐条证据**以「域台账」列指向的正文为准，
> **状态必须带核对日期** ⇒ ✅ **2026-10-27 复核轮已就地订正/确认 6 条**（`DRIFT-HOTRPC-004` 的**范围澄清** ＋ `DRIFT-HOTAPI-030` 本域侧已验 ＋ `baseline-version` **判为 N/A**）。
> ⇒ **教训**：同一编号在 **api 侧与 rpc 侧是两条不同的东西** ⇒ 「已闭合」必须带**范围**，否则会掩盖另一侧的缺口。

## 2. 9 条机制面轴 · 本域侧逐轴结论（**lane 的交付主体** · 核对日期 **2026-10-27**）

> 📌 **本轴的取证方式（与另三域不同，留痕备查）** —— hot 域的取证子任务**两次都因 TPM 限流失败**
> （首次 `rate limit exceeded(TPM) (1039)`；重派后长时间零输出 ⇒ 已取消）⇒ **本表的 hot 结论全部由主会话逐条独立复核**，未采信任何子任务输出。
> 复核命令与结果（核对日期 **2026-10-27**）：
> | 断言 | 复核方式 | 结果 |
> |---|---|---|
> | **A7** proto 行为层零差异 | `diff <(Go 侧 hot.proto 过滤 option/package/注释/空行) <(RAG 侧同名过滤)` | ✅ **零差异**（diff 无输出） |
> | **A9** Go 侧 hot 域无业务 span | `grep -rn "otel.Tracer" / "spanBuilder" $GO_REPO/service/hot` | ✅ **各 0 命中**（只有 `server.Start()` / `serviceGroup.Start()` 误匹配） |
> | **A8** hot-rpc 无库 | `grep -n flyway hot-rpc/.../application.yml` ＋ `ls -d hot-rpc/src/main/resources/db/migration` | ✅ **flyway 行数 0** ＋ **迁移目录不存在** |
> | **A2** hot-rpc 不扫 `com.rag.common` | `grep -n "@SpringBootApplication" HotRpcApplication.java` | ✅ **第 61 行是裸 `@SpringBootApplication`**（无 `scanBasePackages`） |

| 轴 | 机制 | 本域侧的落点 / 调用 | 结论 | 证据强度 | 核对日期 |
|---|---|---|---|---|---|
| **A1** | 超时 / deadline | 服务端：`hot-rpc/.../application.yml:138` `observability.rpc-timeout: 2s` ＋ `HotRpcApplication.java:105-108`（`server.intercept(GoStyleRpcProtectionChain.build(...))`）；消费点 `service/common/.../spring/RpcServerInterceptorFactory.java:75-82`；api→rpc 出向：`hot-api/.../client/GoStyleGrpcCallDeadline.java:60-63`（`min(2000ms, 剩余预算)`）＋ `client/ArticleRpcClient.java` | ✅ **一致** —— Go `service/hot/rpc/etc/hot.yaml` **未配 `Timeout`** ⇒ 走 go-zero default **2000ms**（`zrpc/config.go:46`）⇒ 两侧同为 **2s**；Go 侧 `hot/rpc/hot.go:32` `DisableNativeRpcTimeout` ＋ `:42` `AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))` ⇒ **超时唯一执行者**，RAG 侧同构（`rpcInterceptorConfigurer` → `server.intercept(...)`）。<br>ℹ️ **出向 gRPC 口径**：Go `context.WithTimeout` 的「取父子较早者」语义 ≡ RAG 的 `min(2000ms, 剩余预算)`（`GoStyleGrpcCallDeadline.java:60-63`；⚠️ 预算耗尽时给 **1ms** 而非原样返回，对应 Go ctx 到期即失败）。<br>ℹ️ **`hot-rpc` 无出向 gRPC 调用**（import 面实测：`service/hot/hot-rpc/**` **无 client 类** ⇒ 该域只消费 Kafka ＋ 写 Redis）⇒ rpc 侧出向维度 **N/A** | **L3**（Go 侧 L1 逐行对照 ＋ RAG 侧装配面扫描；未起进程） | 2026-10-27 |
| **A2** | gRPC 拦截器链 | `HotRpcApplication.java:61`（`@SpringBootApplication`）`:63-68`（`@Import` 4 类：`RpcServerInterceptorFactory` / `ObservabilityProperties` / `Jackson2ObjectMapperConfiguration` / `GoStyleProtectionConfiguration`）`:105-108`（`@Bean GrpcServerConfigurer` ⇒ `server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps))`）；链序 `service/common/.../protection/GoStyleRpcProtectionChain.java:61-78` | ✅ **服务端链一致** —— Go `zrpc/server.go:144-163`（Trace→Recover→Stat→Prometheus→Breaker→Shedding→Timeout 逐条按 `c.Middlewares.*` 装配；注册序「append 到尾部 ⇒ 先注册的最外层」，`zrpc/internal/server.go:58`）⇒ RAG recover→breaker→shedding→业务 ＋ 阈值（`cpuThreshold=900`／`cpuSource=SYSTEM`）≡ `zrpc/config.go:47`；Go 侧 native timeout 被 `DisableNativeRpcTimeout` 置 0 ⇒ RAG 不挂 native timeout 也一致。<br>🔴 **但 `hot-rpc`（纯 gRPC 进程）拿不到 client breaker**：`GoStyleGrpcClientBreakerInterceptor.java:103-106` 是 `@Component` ＋ **`@GrpcGlobalClientInterceptor`** ⇒ **只能靠组件扫描**；而 `hot-rpc` **刻意不扫 `com.rag.common`**（`HotRpcApplication.java:61` 无 `scanBasePackages`；⚠️ 同文件 javadoc 明写「**不能**用 `scanBasePackages={"com.rag.common"}`：那里有 `HttpMiddlewareFilter`（servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来」）⇒ 该 client 侧能力**不在容器里** ⇒ **出向调用无熔断**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）⇒ **落点在共享层 ⇒ §4-G4-2**（⚠️ **不是** §1 的 `DRIFT-HOTRPC-004` —— 那条闭的是 `hot-api` 侧） | **L3**（链序 ＋ `@Import`/`@Bean` 装配面）<br>（client breaker 缺失侧为 **L1**：否定事实可静态判定） | 2026-10-27 |
| **A3** | HTTP 保护链 | 共享层 `service/common/.../protection/{GoStyleMaxConnsFilter,GoStyleBreakerFilter,GoStyleCpuSheddingFilter,GoStyleMaxBytesFilter}.java`（靠 `hot-api` 的 `@SpringBootApplication(scanBasePackages={…})` 含 `com.rag.common` ⇒ 注册）＋ 共享层 `service/common/.../http/{GoStyleFormValueLimitInterceptor,GoStyleFormLimitWebMvcConfig,GoStyleTomcatParameterLimitCustomizer}.java`；域侧 `hot-api/.../application.yml:138-141`（`mode: envelope`）＋ `rag.protection.*` | ✅ **一致** —— `mode: envelope` **同向**：Go 侧 `service/hot/api/hotcenter.go:53` **注册了** `httpx.SetErrorHandler`（⇒ 解析失败 ⇒ **200 + 业务信封**）⇒ RAG `envelope` 档同形；hot 的 5001 文案 = `"internal server error"`（Go `service/hot/common/errmsg.go:50` `ErrorServerCommon: "internal server error"`）⇒ 两侧文案也同。<br>✅ 四层阈值与 Go tag 默认逐条同值（`rest/config.go:52-56`：`MaxConns=10000` / `MaxBytes=1048576` / `CpuThreshold=900`）＋ **拒绝形状**同为**裸状态码 + 空 body**（Go：`breakerhandler.go:18-42` 裸 503 · `maxconnshandler.go:12` 裸 503 · `maxbyteshandler.go:10` 裸 413 · `sheddinghandler.go:22` 裸 503） | **L3**（yaml ＋ 装配面；Filter 实现体未逐行读） | 2026-10-27 |
| **A4** | 错误码 / envelope | 共享层 `service/common/.../response/Response.java:14-17`（三字段、**无** `@JsonInclude`）＋ `service/common/.../http/GoStyleRouterErrorResponses.java:121-140`（404 = `text/plain; charset=utf-8` ＋ `nosniff` ＋ `"404 page not found\n"`；405 裸状态码 ＋ `Allow`）；域侧 `hot-api/.../advice/GlobalResponseAdvice.java` ＋ `GlobalExceptionHandler.java` | ✅ **一致** —— Go `service/common/response/response.go:3-7`（`Code/Msg/Data` 三字段**无 omitempty**）⇒ `data:null` 一定出现；Go `hot/api/hotcenter.go:45` `httpx.SetOkHandler` ＋ `:53` `httpx.SetErrorHandler` ⇒ **成功与失败都走业务信封、恒 HTTP 200** ≡ RAG（`GlobalResponseAdvice` 包成 `{"code":200,"msg":"OK","data":…}`）；Go handler `getHotArticlesHandler.go:18-19`（`httpx.Parse` 失败 ⇒ `httpx.ErrorCtx`）**被 SetErrorHandler 接管** ⇒ **200 + `{code:5001}`** ≡ RAG `envelope` 档。404/405 裸响应形状 ≡ Go `rest/router/patrouter.go:73,79-82` | **L1**（两侧逐行对照） | 2026-10-27 |
| **A5** | 端口治理 | `hot-api/.../application.yml:34` `server.port: ${HOT_API_PORT:18897}` ＋ `hot-rpc/.../application.yml:85` `grpc.server.port: ${HOT_RPC_GRPC_PORT:8083}` | 🟡 **OBS（部署形态迁移）** —— ✅ **gRPC 8083 与 Go 一致**（Go `service/hot/rpc/etc/hot.yaml:2` `ListenOn: 0.0.0.0:8083`）；⚠️ **Go 是三副本**（`hot.yaml`/`hot2.yaml`/`hot3.yaml` = **8083/8084/8085**）⇒ **RAG 收敛为「同一部署 × 副本」，端口走环境变量**（`HotRpcApplication` 的 yaml 注释明写「Go 三副本是 8083/8084/8085；Java 侧收敛为同一部署 × 副本，端口走环境变量」）⇒ **属部署形态差异，不是行为漂移**。<br>🟡 **HTTP 18897 ≠ Go 8893**（Go `service/hot/api/etc/hotcenter.yaml` `Port: 8893`）⇒ 属 `Infrastructure.md §3` 给 hot 域预占的号段（**已记录的部署口径差异**）；yaml 注释明写「原值 8893 可用 `HOT_API_PORT` 传回」。<br>⚠️ **`hot-rpc` 的 gRPC 端口未在 `Infrastructure.md §3` 预占**（该文件目前只给 hot 预占了 18897/HTTP）⇒ 部署侧待办 | **L3**（yaml 静态；未起进程验绑定） | 2026-10-27 |
| **A6** | errmsg 契约 | `hot-common/.../errmsg/HotErrorCode.java:36`（`public enum HotErrorCode`）＋ `HotErrorMessages.java`；**域内契约测试** `HotErrorCodeContractTest#declarationOrderMatchesGoSourceOrder`（锁死**声明顺序**＝Go const 块顺序）＋ `HotErrorMessagesContractTest#hotCodesKeepTheirOwnMeaningAgainstOtherDomains`（钉死 hot 的 5002/5003/5004 **不与他域同名码混义**） | ✅ **一致** —— Go `service/hot/common/errmsg.go:33-46`（const：`Success=200` / `Error=500` / `CodeServerBusy=1015` / `ErrorServerCommon=5001` / `ErrorNotFound=5002` / `ErrorInvalidParam=5003` / `ErrorUnauthorized=5004` / `ErrorForbidden=5005` / `ErrorAlreadyExist=5006` / `ErrorDbSelect=5007` / `ErrorDbUpdate=5008`）＋ `:48-62`（`codeMsg`：`OK` / `FAIL` / `service busy` / `internal server error` / `record not found` / `invalid parameter` / `unauthorized` / `forbidden` / `already exists` / `database query failed` / `database update failed`）⇒ RAG 侧 `HotErrorCode` 的枚举项（`SUCCESS(200)` / `ERROR(500)` / `CODE_SERVER_BUSY(1015)` / `ERROR_SERVER_COMMON(5001)` / `ERROR_NOT_FOUND(5002)` / `ERROR_INVALID_PARAM(5003)` / `ERROR_UNAUTHORIZED(5004)` …）**逐条对应**，且**声明顺序被测试锁死** ⇒ 比裸顺序对照更强 | **L3**（枚举 ＋ 契约测试源码；未跑双侧探针）⇒ 📌 本域**有契约测试但无双侧探针** ⇒ 可复制 `tools/errmsg-probe/` 骨架升 L4 | 2026-10-27 |
| **A7** | proto 契约 | `service/hot/hot-proto/src/main/proto/hot.proto` | ✅ **一致** —— 与 Go `proto/hot.proto` 做**行为层 diff**（排除 `option` / `package` / 注释 / 空行）⇒ **零差异**（diff 无输出）⇒ message 名 / 字段名 / 字段号 / 类型 / 枚举值 / rpc 签名全部一致；唯一差异在 option/包名块（表达层：删 `option go_package`、加 `java_package` ＋ `java_multiple_files`）⇒ 属 AGENTS.md §3.4 说的「复制粘贴不是翻译、但改动限定在 option 块与空白层面」的可接受范围。<br>🔴 **但无机器回归网**：`tools/difftest/check-proto-static.sh` **只覆盖 `user`/`admin`/`task`** ⇒ **hot 的 proto 契约无自动守门**（AGENTS.md §3.4：10 个 proto 中 8 个无回归网）⇒ **域侧可自修** | **L1**（行为层 diff 为零差异） | 2026-10-27 |
| **A8** | DDL 时间列 / 类型 | — | ✅ **N/A（本维度无对象可比）** —— Go 侧 `service/hot` **全域无 gorm**（只有 Redis ＋ Kafka）；RAG 侧 `hot-rpc` **无 `db/migration` 目录**、`hot-rpc/.../application.yml` **无 `flyway:` 段** ⇒ **两侧都是 0 张表**。<br>⚠️ **「两侧都是 0 张表」不等于「DDL 已对齐」** —— 结论是「**本维度无对象可比**」（与 `check-ddl-static.py --domain hot` 的 `EXIT=3` 口径一致：「⚠️ 本域两侧都没有业务表 ⇒ DDL 维度不适用（N/A），不是『已对齐』」）⇒ **本轴不得写成「已对齐」** | **L3**（目录 ＋ yaml 静态实测） | 2026-10-27 |
| **A9** | trace / otel | 共享层 `service/common/.../observability/ObservabilityConstants.java:57-116`（`sea.*` 属性）＋ `RpcInterceptor.java:207-336`（SERVER span ＋ `rpc.system`/`rpc.method`/`rpc.grpc.status_code`）＋ `service/common/.../http/GoStyleTraceparentFilter.java:188-191`（`@Component` ＋ `@Order(HIGHEST_PRECEDENCE+3)`）；Kafka 消费侧 `hot-rpc/.../mq/HotEventConsumer.java:98-99`（`@KafkaListener`）＋ 共享层 `MqConsumer.java:71-123`（SpanKind.CONSUMER ＋ attrs ＋ `markTimeout/markSystemError/markDurationAndSlow`） | ✅ **一致** —— 共享层的 span 属性与 `Traceparent` 传播**已就位**；⚠️ **关键差异点已核清**：**Go 侧 hot 域「无业务 span」**（实测：`service/hot/**` 全域 grep `otel.Tracer` / `spanBuilder` / `Start(` **零命中**，只有 `server.Start()` / `serviceGroup.Start()` 的误匹配）⇒ **两侧都无业务 span ⇒ 不是漂移**（与 comment/favorite 域不同 —— 那两域 Go 侧有 `audit.*`/`biz.*` 业务 span ⇒ 属真实缺口，见那两域台账）。<br>✅ `Traceparent` 传播 ≡ Go `rest/handler/tracehandler.go:47-63`（Extract→Start→Inject 到响应头→next）＋ `rest/engine.go:245-251`（404 也挂 TraceHandler）。<br>ℹ️ **属性名是 `sea.*`（共享层通用前缀），不是 `audit.*`** —— `audit.*` 只存在于 users/admin 域 ⇒ **本轴的「`audit.*`」措辞对 hot = N/A**（见 §4 的共享面订正项） | **L3**（Go 侧 L1 ＋ RAG 侧装配面；否定事实「Go 侧无业务 span」可静态判定） | 2026-10-27 |

## 3. 域侧能自修的（**本域直接修**，不要甩给 G4）

| # | 事项 | 落点 | 依据 / 修法 |
|---|---|---|---|
| **H-1** | 🔴 **补 proto 契约回归网**（A7 无守门） | `service/hot/hot-proto` ＋ `hot-rpc/src/test/**` | `check-proto-static.sh` 只覆盖 user/admin/task ⇒ hot 的 proto 无自动守门 ⇒ 域内加一条「逐字段/字段号/枚举值」契约测试（**域内文件，不改共享脚本**） |
| **H-2** | 🟡 **A6 升 L4：复制 errmsg 探针骨架到 `hot/`** | `tools/errmsg-probe/{go,java}` → 复制出 `hot/` | 骨架已有（顶层 article ＋ `favorite/`）⇒ 复制一份到 `hot/` 即可拿错误码值/文案/未知码回落的**双侧逐字节**证据 |
| **H-3** | 🟡 **A7 升 L4：proto wire 探针** | `tools/proto-wire-probe/` → 复制出 `hot/` | 同款骨架（favorite 已有）⇒ 拿 wire 字节级证据 |
| **H-4** | 🟡 **部署侧**：`hot-rpc` 的 gRPC 端口未在 `Infrastructure.md §3` 预占 | 部署配置（**非本仓写权面**） | `hot-rpc/.../application.yml:83-85` 的注释自记「Infrastructure.md §3 目前只给 hot 预占了 18897(HTTP)，**gRPC 端口未分配**」⇒ **记录 + 上报**，非代码缺陷 |
| **H-5** | ✅ A3/A4/A5/A9 —— **无需改** | — | 四轴实测一致（端口差异属已记录的部署口径；Go 侧 hot 无业务 span ⇒ 无需补埋点 ⇒ **补了反而是新增 Go 没有的行为**） |

## 4. 需转派 **G4**（`align/g4-service-common`）的（**本域只登记：现象 + 证据 + 建议修法**）

> 派单走**实时轨**（`session send` ＋ 反馈单 7 项）＋ **正式轨**（本表）⇒ 两轨内容须一致（AGENTS.md §5.3e）。
> ⚠️ **落点必须独立核准**（不要照抄台账快照）。

| # | 事项 | 落点 | 建议修法（**两侧都应是 Y**） |
|---|---|---|---|
| **G4-1** | 🔴 **`GoStyleGrpcClientBreakerInterceptor` 在 rpc 侧无装配路径** ⇒ `hot-rpc` 出向调用无熔断 | `service/common/src/main/java/com/rag/common/protection/GoStyleGrpcClientBreakerInterceptor.java:103-106` ＋ `GoStyleProtectionConfiguration.java:51-53` | **现象**：该拦截器是 `@Component` ＋ `@GrpcGlobalClientInterceptor` ⇒ **只能组件扫描**；而 **rpc 进程刻意不扫 `com.rag.common`**（`HotRpcApplication.java:61` 无 `scanBasePackages`；同文件 javadoc 明写「不能扫，HttpMiddlewareFilter 是 servlet Filter，纯 gRPC 进程全包扫描会把 web 类拖进来」）⇒ rpc 侧**永远拿不到 client breaker** ⇒ **出向调用无熔断**（Go 侧 client breaker 默认开：`zrpc/internal/config.go:14` ＋ `zrpc/internal/client.go:113`）。**实测影响面（2026-10-27）**：**G3 四域的 rpc 组件全部如此** —— `article-rpc` / `comment-rpc` / `favorite-rpc` / `hot-rpc` 的 `@SpringBootApplication` 均**无** `scanBasePackages`（javadoc 里出现 `scanBasePackages` 只是**在说明为什么不能用它**）且 `@Import` 清单**均不含**该类；⇒ **建议**：共享层提供一个 **rpc 进程可用的装配入口**（例如改为可被 `@Import` 引入，或由 `RpcServerInterceptorFactory` 一并产出 client 侧拦截器），并补一条「rpc 侧出向调用确实经过 breaker」的**装配层**测试（AGENTS.md §3.3.3：注解存在 ≠ 机制生效）。**影响面**：全部 rpc 组件的出向调用（≥4 个 rpc 组件，G3 域内 4/4 命中）。**是否阻塞本波**：否 |
| **G4-2** | 🟡 **A9 轴定义的措辞缺陷**（共享面 `_cross`） | `docs/DRIFT/_cross/domain-common-lanes.md` 的 A9 行 | 轴定义写「span 属性 **`audit.*`**」⇒ 实测：**共享层 = `sea.*`**（`ObservabilityConstants.java:57-116`）· **admin = `audit.*`** · **favorite = `biz.*`** · **hot 域两侧都无业务 span** ⇒ 轴定义应按域区分 ⇒ **共享面文件，G3 不写** ⇒ 已请 G4 登记进 `global/vs-common.md` 的建议项段 |
| **G4-3** | ℹ️ **A8 = N/A 的登记口径** | `docs/DRIFT/_cross/ddl-type-divergences.md` | 本域「两侧 0 张表 ⇒ DDL 维度 N/A」⇒ 建议在跨域 DDL 汇总里**把 hot 显式标为 N/A**（而非「已对齐」）⇒ 共享面 ⇒ 整合者/G4 处理 |

## 5. 交付物清单

- [ ] **第 0 步**：本域组件**真的启动一次**（AGENTS.md §0 第 3 条：「跑绿」≠「对」）—— ⚠️ **本轮为阶段 1（只读），未起进程**；域内已有 `HotGrpcInterceptorWiringTest`（真 gRPC server）、`HotRpcJarExecutableWiringTest`（产物可执行性）、`HotRpcSpringContextWiringTest` 等资产，**待阶段 2 收口回归统一跑**
- [x] **9 条轴**逐轴填表（§2）：结论 ✅/🔴/🟡/N/A ＋ **证据强度** ＋ **核对日期**（**2026-10-27**）
- [ ] **两侧同时跑**的探针（AGENTS.md §3.3）—— ⬜ **本域无现成域级探针**（`tools/*-probe/` 的域级实例只覆盖 favorite）⇒ 结论最高 **L3** ⇒ 已在 §3-H-2 / H-3 登记「复制探针骨架」的自修项
- [x] §1 的每一条**重新核对状态**并写回（带核对日期）
- [ ] §3 的域侧修法**落码 + 守门**（含变异验证）—— **阶段 2 才做**
- [ ] §4 的派单**登记到 G4**（`docs/DRIFT/global/vs-common.md`）—— 🔴 `global/_README.md` §七之二属 SHARED 面 ⇒ G3 不写 ⇒ 派单走**实时轨**（`session send`）
- [x] **共享面不回写**（`AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` ⇒ 合并后由整合者统一更新）
