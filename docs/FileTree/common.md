# common — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/common`
> 目标根包：`com.rag.common`
> 推导日期：2026-10-21

> **本文件覆盖的 14 个 Go 文件 + RAG 侧 24 个 main + 9 个 test 文件，均已逐行读完。**
> 本模块是 12 份 sibling 文件里唯一的**非 api/rpc 双分**组件——它没有 `internal/{config,logic,server,svc}`，
> 是一个被所有业务模块依赖的**单一共享库**，因此 §12.1 的包职责约定**不逐字适用**，详见 §12.1。

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG `service/common` 实测 7 个包、24 个 main + 9 个 test，**无 `src/main/resources`**，`pom.xml` 无 `config/` 约定载体：

| RAG 包 | 文件数 | 对应 Go 包 |
|---|---|---|
| `communityfact/` | 4 | `communityfact/` |
| `observability/` | 5 | `observability/` |
| `logger/` | 5 | `logger/` |
| `snowflake/` | 1 | `snowflake/` |
| `response/` | 1 | `response/` |
| `spring/` | 7 | **Go 侧无对应物**（框架装配层） |
| `exception/` | 1 | **Go 侧无对应物**（Go 用 `(resp, error)` 双返回） |

**本任务的包职责约定（api/rpc 用的 `advice/config/dto/domain/mapper/service/bootstrap/client/metrics/exception/mq`）对本模块不逐字适用**，理由不是"图省事"，而是本模块**结构性地缺一半职责**：

| 包职责 | 是否适用于 common | 依据 |
|---|---|---|
| `advice` | ❌ 无 | Go 侧 `response.Response` 只有一个 struct，无统一包装器；RAG 的 `@ResponseBodyAdvice` 在 `users/{user,admin}-api/advice/`，属业务侧 |
| `config` | ⚠️ 半适用 | Go 侧 `common` **无 config 段**；RAG 已有 2 个 `@ConfigurationProperties` 藏在 `spring/`（见下方不一致） |
| `dto` | ❌ 无 | 本模块无对外 RPC DTO；`Wire.*` 是**跨服务 wire 契约**，不是本服务 DTO |
| `domain` / `mapper` | ❌ 无 | Go 侧 `common` 零 GORM 模型、零 SQL。`response.Response` 是序列化载体不是表实体 |
| `service` / `impl` | ❌ 无 | Go 侧 `common` 无用例、无 stub。`Dispatcher`/`AuthorityHandler` 是**库**（Go 侧由 `like/cmd/*` 宿主进程调用） |
| `client` | ❌ 无 | Go 侧唯一的出网调用 `DCEventSender` 已内嵌在 `wire.go`，且属于 wire 契约的一部分 |
| `bootstrap` | ⚠️ 半适用 | Go 侧 `InstallLocalTracing` / `InstallFrameworkLogger` 是启动期副作用，但**无 Nacos/Flyway/MinIO**，无 `bootstrap/` 必要 |
| `metrics` | ❌ 无 | Go 侧 `common` 零 Prometheus 指标（指标全在各业务 `internal/metrics/`） |
| `exception` | ✅ 适用 | 见 §12.7 不通过项 #6：`BusinessException` 是合法 idiomatization |
| `mq` | ❌ 无 | Go 侧 `TraceConsumer` 只是 span 包装，**不含 listener 容器**（见 §12.3 `mq.go`） |

实际推导出的包集合 = `{communityfact, observability, logger, snowflake, response}`（对应 Go 5 包）+ `{exception, spring}`（Go 无对应物的新增层）。

### 已有组件（跨模块可复用）

**外部消费面实测**（`grep -rn "com.rag.common.<pkg>"` 排除 `service/common` 自身）：

| 包 | 外部 import 点位 | 结论 |
|---|---|---|
| `snowflake` | message-rpc ×1、admin-rpc ×4、user-rpc ×2 | ✅ 真实复用 |
| `response` | users/{admin,user}-api ×4（advice 层） | ✅ 真实复用 |
| `exception` | users/{admin,user}-api ×8 | ✅ 真实复用 |
| `spring` | message-rpc ×1（`LoggerFactoryBean` 注入）、user-rpc ×1（`@Import` 3 个）、admin-rpc ×0 | ⚠️ 部分复用，**见 §12.7 不通过项 #2** |
| `observability` | user-rpc ×1（仅 `RpcInterceptor.DeadlineState`） | ⚠️ 仅 1 个内部类被用，整个包的可观测价值未落地 |
| `logger` | **0** | ❌ 全仓零 import |
| `communityfact` | **0** | ❌ 全仓零 import |

### 已确认的框架机制

| 机制 | 证据 |
|---|---|
| `@Component` / `@ConfigurationProperties` | `spring/` 7 个类全部标注；`pom.xml:51-54` 引 `spring-boot-starter` |
| `@Value` + 组件扫描 | `HttpMiddlewareFilter.java:41`、`LoggerFactoryBean.java:72` |
| gRPC `ServerInterceptor` Bean 自动收集 | `RpcServerInterceptorFactory.java:31-37`（依赖 `net.devh:grpc-server-spring-boot-starter`） |
| Servlet Filter 自动注册 | `HttpMiddlewareFilter.java:34-35`（`@Component` + `@Order(HIGHEST_PRECEDENCE+5)`） |
| `ServletRegistrationBean` | `AuthorityHandlerFactory.java:45-47`（Spring Boot 4 API `addUrlMappings`） |
| Jackson + JCS + OTel API/SDK/context | `pom.xml:76-88` / `:116-137` |

> **§3.3 必须点名**：上表第 3、4 行的两个机制**在本仓库里已被实测证伪为"部分不生效"**，详见 §12.7 不通过项 #1。不得凭 `@Component` 名称断定已启用。

### 当前 Component 可以直接复用的能力

`snowflake/Snowflake`（位宽与 Go bwmarrin 一致）、`response/Response`（`code/msg/data` 三字段与 Go `response.go` 逐字一致）、`exception/BusinessException`、`observability/{Observability,ObservabilityConstants}`（attribute key 与 `Mark*` 四分支逐条对齐）、`communityfact/Wire`（见 §12.3 `wire.go`）。

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| `spring/` 与 `config/` 的包职责归属 | 本文件**已决**，见 §12.5「新增包说明」。执行仍需 review |
| `RpcServerInterceptorFactory` 在 rpc 侧未注册 | 已定位为不通过项 #1，修复形状已给出 |
| 超时值丢失（hot-api 3s / hot-rpc 2s） | 已定位为不通过项 #3，修复形状已给出 |
| `communityfact` / `logger` 的未来归属 | 它们是给 `like/comment/article` 的库；RAG 侧这些模块**尚无 Java 文件**，无法验证。**UNKNOWN** |

---

## 12.2 第二部分：Go 原始文件树

```
service/common/
├── communityfact/
│   ├── wire.go                        (165)  信封 DTO + DC sender + JCS hash + receipt 校验 + subject 解析 + 2 个 sentinel
│   ├── process.go                     ( 69)  Dispatcher 接口 + RunDispatcher 循环（批次16/1s/单次10s/once）
│   ├── authority_http.go              ( 80)  GET /internal/v1/community/facts servlet（Bearer + query 校验 + 4 分支）
│   ├── logging.go                     (145)  ProcessMetadata + OBS-r3 slog envelope + trace handler + 本地 OTel + go-zero logx 接管
│   └── authority_http_test.go         (106)  4 个测试：framework logger 走 envelope / authority 用注入 logger / subject v2 wire / 响应中断
├── logger/
│   ├── logger.go                      (356)  Logger + LogOptions + LogOption + 3 个 log 方法 + 栈采集 3 函数
│   └── logger_test.go                 (201)  7 个测试（TestFatal 被 skip）
├── observability/
│   ├── observability.go               (162)  常量 + SlowThreshold + Mark{Business,System,Timeout,DurationAndSlow} + IsTimeoutError
│   ├── http.go                        (209)  DisableNativeRestTimeout + NewHTTPMiddleware + response writer buffer + envelope 解析
│   ├── rpc.go                         (122)  DisableNativeRpcTimeout + NewUnaryServerInterceptor + markRPCResult + 业务 code 集
│   ├── mq.go                          ( 43)  TraceConsumer span 包装
│   └── observability_test.go          (184)  6 个测试，全部基于 sdk tracetest.SpanRecorder 断言 attribute
├── response/response.go               (  7)  Response{code,msg,data}
└── snowflake/snowflake.go             (102)  Init + GetID + generateNodeId（env > SERVICE_NAME+PID sha256）
```

---

## 12.3 第三部分：Go 文件职责分析

### `communityfact/wire.go`

- **主要职责**：社区事实的**技术线（technical wire）**。Go 注释明写"Domain packages still own their payload semantics and outboxes"——本文件**不含任何 domain payload**。
- **关键调用关系**：`Dispatcher` → `Sender` → `DCEventSender.Send` → HTTP → `StrictDecode` → `TechnicalReceipt` → `ValidateReceipt`；`AuthorityHandler` → `AuthorityLookup` → `AuthorityFact`（内含 `Event`）。
- **涉及的业务语义**：`Event` 9 字段（`aggregate_version` 为 `int64` 非指针）；`DeliveryStatus` 0/1/2/3；`ValidateReceipt` 要求 `technical_status=="accepted"` ∧ `offset>=1` ∧ `input_hash` 匹配 `^[a-f0-9]{64}$` ∧ `received_at` 可按 RFC3339Nano 解析；`ParseRTWSubject` 要求 `uid>0` **且** `strconv.FormatInt(uid,10)==id`（即拒绝 `+1001`/`01001` 这类非规范字面量）。
- **涉及的实现机制**：**canonical JSON 哈希**（`jsoncanonicalizer.Transform` = JCS/RFC 8785）后 SHA-256 hex；`StrictDecode` = `DisallowUnknownFields` + 拒绝尾随 JSON；DC sender 强制 `endpoint.Path == "/v1/events"` ∧ `token>=32` ∧ 无 userinfo/query/fragment。
- **需要保留的行为**：① 哈希必须**先规范化再哈希**——否则 `{"b":2,"a":1}` 与 `{"a":1,"b":2}` 摘要不同，跨语言不可比；② `Idempotency-Key: event.EventID`；③ 非 200/201 时**丢弃最多 64KB body 后返回错误**（防连接泄漏），且**返回零值 receipt 而非伪 receipt**。
- **可由 Java/Spring 替换的机制**：`http.Client` → JDK `java.net.http.HttpClient`；`encoding/json` → Jackson；`errors.New` sentinel → 异常类型。**不可替换**：JCS 规范化本身。

### `communityfact/process.go`

- **主要职责**：`Dispatcher` 接口（域 store 实现）+ `RunDispatcher` 排空循环。
- **涉及的业务语义**：单次 dispatch 起 span `community.fact_delivery.dispatch`，**每次 10s deadline**；外层 **1s ticker**；内层**最多 16 次**，遇 err 或 `sent==false` 即 `break`；`once=true` 时只跑一次并返回 err。`deliveryErrorCode` 用 `errors.Is` 把 frozen-envelope 阻塞映射为 `INVALID_FROZEN_ENVELOPE`，其余为 `DELIVERY_ATTEMPT_FAILED`。
- **涉及的实现机制**：`context.WithTimeout` + `select` 三路（panic / done / ctx.Done）。
- **需要保留的行为**：**失败用同一个 frozen event 重试**（不是取新事件）；单次失败**不中断批次语义下的当前 tick**（break 内层，外层继续下一 tick）。
- **可由 Java/Spring 替换的机制**：`ticker` → `@Scheduled` 或定时等待；`select` → `Future.get(timeout)` + `cancel(true)`。`slog` → SLF4J fluent API。

### `communityfact/authority_http.go`

- **主要职责**：把一个 domain 自有的源查询暴露成内部 HTTP 端点。
- **涉及的业务语义**：`GET /internal/v1/community/facts?producer=X&event_id=Y`；`Cache-Control: no-store`；Bearer 用 `subtle.ConstantTimeCompare` **且要求 `len(Header.Values("Authorization"))==1`**（多 header 直接 401）；query 必须**恰好一个** `producer` **且等于**注册 producer、`event_id` 非空，否则 **404**；lookup 抛 `ErrAuthorityUnavailable` → **404**（不是 503），其它错 → **503 + `SOURCE_READ_FAILED`**；JSON 编码失败 → `RESPONSE_WRITE_FAILED` + `outcome=failed`。每条路径都在 `defer` 里打 `community.fact_authority.request_finished`。
- **涉及的实现机制**：`http.ServeMux` + `otel` propagator 提取 + `SpanKindServer`。
- **需要保留的行为**：**404 与 503 的区分是 fail-closed 设计**——authority 不可用时**不能**伪装成"事实不存在"，因为前者可重试、后者会让上游永久丢弃事件。恒定时间比较防时序侧信道。
- **可由 Java/Spring 替换的机制**：`ServeMux` → `HttpServlet` + `ServletRegistrationBean`。

### `communityfact/logging.go`

- **主要职责**：独立进程的 OBS-r3 JSON 日志 envelope + 本地 OTel 装配 + **go-zero 框架日志接管**。
- **涉及的业务语义**：`ProcessMetadata.Validate` 要求 `version` 匹配 `^[0-9a-f]{40}$`、`environment ∈ {local,test,staging,production}`；空字段统一写成 `"unresolved"`；`traceLogHandler.Handle` 在 span 有效时追加 `trace_id/span_id/trace_sampled`；`InstallFrameworkLogger` 把 **go-zero 的生命周期输出（含信号停机）** 路由进同一 envelope，并做字段改名 `trace→trace_id` / `span→span_id` / `caller→code_location` / 丢弃 10 个保留键。
- **涉及的实现机制**：`slog.NewJSONHandler` + `ReplaceAttr` + `Handler.WithAttrs/WithGroup` 链式包装。
- **需要保留的行为**：**不使用全局 MDC**——`WithAttrs` 绑在 logger 实例上，因此同一 JVM 内可建多份互不串扰的进程 logger（这是 like 的 4 进程合并成 1 JVM 的前提）。
- **可由 Java/Spring 替换的机制**：`slog` → logback 自定义 appender；`InstallLocalTracing` → `SdkTracerProvider.builder().setSampler(alwaysOn())` + W3C trace context + baggage composite。

### `communityfact/authority_http_test.go`

4 个测试锁定的行为：① go-zero 日志**必须**走进程 envelope（且**不得**残留 `"@timestamp":`、**必须**产出 `code_location`）；② authority 的日志**必须**落到注入的 logger 上（不是全局）；③ subject wire 是 v2（`{"issuer":"rtw.identity","subject_id":"1001"}`，**无 realm / tenant**）；④ 响应写入中断**必须**经注入 logger 记 `response_interrupted`。

### `logger/logger.go`

- **主要职责**：业务日志门面。3 个入口 `LogBusinessErr` / `LogInfo` / `LogFatal`，每次生成一个 snowflake `event_id`、采集 `file_line` / `call_path` / `call_chain`、提取 `trace_id`、可选 `user_id` / `article_id`。
- **涉及的业务语义**：`Init` 空服务名 → `panic`；三个 log 方法在未 `Init` 时 → `panic`；`image_base64:` 内容**必须截断**（防日志爆炸）；`user_id`/`article_id` **空字符串则整字段不出现**；`LogFatal` 输出后 `os.Exit(1)`。
- **涉及的实现机制**：`runtime.Caller(skip)` + `runtime.Callers(32)` 栈采集 + `getFuncName` + `isFiltered` 前缀过滤 + 反转链。
- **需要保留的行为**：`call_chain` 要**过滤掉 `runtime.` / `testing.` / `github.com/zeromicro/go-zero/` 框架帧**并**反转成"入口→下游"顺序**——过滤失效会让 `call_chain` 变成不可读的栈转储。
- **可由 Java/Spring 替换的机制**：`logx` → SLF4J 2.x fluent API（`atError().addKeyValue(...).log()`）；`runtime.Caller` → `StackWalker`；`z_trace.TraceIDFromContext` → `Span.fromContext(ctx).getSpanContext()`。

### `logger/logger_test.go`

7 个测试锁定的行为：`Init("")` panic；重复 `Init` 只生效一次；`LogInfo` / `LogBusinessErr` 在空 ctx / 空 err / 部分 opts 下不崩；`LogFatal` **被 `t.Skip`**（因为 `os.Exit`）；`getFuncName(0)=="unknown"` 且能解析出 `package.Type.Method`；`getCallChain` 非 `"unknown"` 且含调用者；`isFiltered` 5 个用例（`runtime.` ✓、`testing.` ✓、`github.com/zeromicro/go-zero/...` ✓、业务函数 ✗、空串 ✗）。

### `observability/observability.go`

- **主要职责**：span attribute 的**唯一**写入点。
- **涉及的业务语义**：`SuccessBizCode=200`；`DefaultSlowThreshold=500ms`；`SlowThreshold()` 依次试 `SEA_OBSERVABILITY_SLOW_THRESHOLD`（Go duration 格式）与 `..._MS`（纯毫秒）；`MarkBusinessError` 在 `code==200` 时**降级为** `MarkDurationAndSlow`；三个 error 分支都 `SetStatus(Error, msg)`，`System`/`Timeout` 额外 `RecordError`；`MarkDurationAndSlow` 仅在 `slowThreshold>0 && duration>slowThreshold` 时打 `sea.slow=true`。
- **需要保留的行为**：`MarkBusinessError` 内部的 `SlowThreshold()` 读的是 **env**，不是调用方传入的阈值——即 yml 配的阈值在这条路径上**不生效**，这与 Go 一致，不能"顺手修正"。
- **可由 Java/Spring 替换的机制**：`attribute.KeyValue` varargs → `Map<String,Object>`；`float64(microsec)/1000` → `duration.toNanos()/1e6`（保亚毫秒）。

### `observability/http.go`

- **主要职责**：go-zero `rest.Middleware`，替代原生超时中间件并对结果打 span。
- **涉及的业务语义**：`DisableNativeRestTimeout` 关掉 `conf.Middlewares.Timeout` 并**返回** timeout 交回调用方；`shouldSkipHTTPTimeout`（`Upgrade: websocket` 或 `Accept: text/event-stream`）**不设超时**；超时时 **503**（DeadlineExceeded）、**客户端取消时 499**、两者都写 `"Request Timeout"` body；`markHTTPResult` 的判定顺序是 **envelope 业务码 → status>=400 → 慢调用**（业务码优先于 HTTP 码）。
- **涉及的实现机制**：goroutine + `select`；`observabilityResponseWriter` **全 buffer**（非 tee）以捕获 body 做 envelope 解析。
- **需要保留的行为**：超时后 `Write` 返回 `ErrHandlerTimeout` 且**不 flush 已捕获的 body**——否则 client 会先收到半截业务响应再收到超时 body。
- **可由 Java/Spring 替换的机制**：`rest.Middleware` → `jakarta.servlet.Filter`；buffer writer → `HttpServletResponseWrapper`。

### `observability/rpc.go`

- **主要职责**：`grpc.UnaryServerInterceptor`，与 http.go **对称**的一套超时/分类逻辑。
- **涉及的业务语义**：`DisableNativeRpcTimeout` 把 `conf.Timeout` 清零并返回原值；超时 → `codes.DeadlineExceeded`；**业务 gRPC code 集合恰为 7 个**：`InvalidArgument/NotFound/AlreadyExists/PermissionDenied/Unauthenticated/FailedPrecondition/OutOfRange` → `MarkBusinessError`；其余 → `MarkSystemError`。attrs 恒为 `rpc.system/rpc.method/rpc.grpc.status_code`。
- **可由 Java/Spring 替换的机制**：`grpc.UnaryServerInterceptor` → `io.grpc.ServerInterceptor`（包装 `ServerCall.close`）。

### `observability/mq.go`

- **主要职责**：**仅** span 包装（`SpanKindConsumer` + attrs + 三分支标记）。
- **需要保留的行为**：默认 `tracerName="mq-consumer"` / `spanName="message.consume"`；`ctx==nil` 退化到 root。
- **明确无职责**：**本文件不含 ack / drop / 重试 / offset 提交**。Go 侧这些由调用方（各业务 `internal/mq` 的 `kq` consumer）决定。RAG 侧同样不能加——加了就是新增行为。

### `observability/observability_test.go`

6 个测试全部基于 `tracetest.SpanRecorder` **真装配 `sdktrace.TracerProvider`** 后断言 span attribute：`sea.error.kind=business/timeout`、`sea.biz_code=40101`、`sea.timeout=true`、`sea.slow=true`。超时用例断言 HTTP 返回 `503`。

> **§3.3 对照**：这批测试是 Go 侧**唯一真正触发 OTel 运行期装配**的测试。反观 RAG 同名测试——见 §12.7 不通过项 #5。

### `response/response.go`

3 字段 JSON 信封。RAG `Response.java` 逐字一致（Lombok 生成访问器，`data` 无 `@JsonInclude` → 始终输出 `data:null`，与 Go 无 `omitempty` 一致）。

### `snowflake/snowflake.go`

- **主要职责**：分布式 ID。位布局 **41 时间 + 10 worker + 12 seq**，epoch `1288834974657`。
- **涉及的业务语义**：`generateNodeId` 优先 `SNOWFLAKE_NODE_ID`（**parse 失败则继续走 hash，不报错**），否则 `sha256("SERVICE_NAME-PID")` 取**前 2 字节大端** 作 uint16；`Init()` 统一 `%1024`。
- **需要保留的行为**：时钟回拨时**等待追上**再生成；同毫秒 seq 耗尽时自旋到下一毫秒；`GetID` 在 `sfErr != nil` 时返回 `(0, err)`。
- **可由 Java/Spring 替换的机制**：`bwmarrin/snowflake` → JDK `MessageDigest` + 位运算自实现；`sync.Once` → 静态 Holder。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `wire.go` 4 个 DTO + `DeliveryStatus` | `communityfact/Wire`（`Wire.java:37-132`） | REUSE | 9 字段逐字段核对一致，含 `aggregateVersion` 为 `long` |
| `wire.go` `StrictDecode` | `Wire.strictDecode`（`:259-278`） | REUSE | Jackson `FAIL_ON_UNKNOWN_PROPERTIES` + `nextToken()!=null` 拒尾随 |
| `wire.go` `CanonicalHash` | `Wire.canonicalHash`（`:284-297`） | REUSE | JCS → SHA-256 → 小写 hex，**规范化必须保留** |
| `wire.go` `ValidateReceipt` / `ValidHash` | `Wire.validateReceipt` / `validHash`（`:303-326`） | REUSE | 6 项校验 + RFC3339 解析，逐条一致 |
| `wire.go` `ParseRTWSubject` | `Wire.parseRTWSubject`（`:332-348`） | REUSE | 含 `uid>0` 与**字面量规范化**双重校验 |
| `wire.go` `DCEventSender` | `Wire.DCEventSender`（`:142-253`） | REUSE | 6 条 endpoint/token 校验 + 64KB 限流 + 丢弃 body |
| `wire.go` `ErrFrozenEnvelopeBlocked` / `ErrAuthorityUnavailable` | `Wire.ERR_*`（`:66-72`）+ `AuthorityHandler.AuthorityUnavailableException`（`:55-63`） | SPLIT | **RAG 侧两套机制并存且不一致**，见 §12.7 #7 |
| `process.go` `Dispatcher` 接口 | `Dispatcher.DispatcherImpl`（`Dispatcher.java:35-44`） | ADAPT | `@FunctionalInterface` 表达 Go 接口 |
| `process.go` `RunDispatcher` 循环 | `Dispatcher.run`（`:86-208`） | REUSE | 批次16/1s/10s/`once`/`stopSignal` 逐条对齐 |
| `process.go` ctx 取消 | `DispatcherHandle.stop()`（`:217-255`） | ADAPT | Go `cancel()` → 显式 handle，**需 `@PreDestroy` 配对**，见 #8 |
| `process.go` `deliveryErrorCode` | `Dispatcher.deliveryErrorCode`（`:261-274`） | ADAPT | 走 cause 链 + 身份/消息双判，见 #7 |
| `authority_http.go` handler 工厂 | `AuthorityHandler.create`（`:95-102`） | ADAPT | 返回 `HttpServlet` 而非 `http.Handler` |
| `authority_http.go` 4 分支状态码 | `AuthorityServlet.doGet`（`:141-213`） | REUSE | 401/404/404/503 分支与日志 event 名逐条一致 |
| `authority_http.go` 恒定时间比较 | `AuthorityHandler.java:159-160` | ADAPT | `MessageDigest.isEqual` + **先比长度** |
| `authority_http.go` Servlet 注册 | `spring/web/AuthorityHandlerFactory`（`:39-49`） | ADAPT | Go 由 `cmd/fact-authority` 显式 `ListenAndServe`，RAG 交容器 |
| `logging.go` `ProcessMetadata` + `Validate` | `ProcessLogger.ProcessMetadata.validate`（`:83-94`） | REUSE | 40-hex + 4 个 environment 白名单 |
| `logging.go` OBS-r3 envelope | `ProcessLogger.newProcessLogger` + `ProcessLogAppender`（`:109-207`） | REUSE | 字段名/顺序/`unresolved`/不依赖 MDC 全部保真 |
| `logging.go` `traceLogHandler` | `ProcessLogger.traceContextAttrs`（`:152-162`） | REUSE | 仅在 span 有效时注入三字段 |
| `logging.go` `WithGroup` | — | — | **RAG 缺失**（无对应能力），低风险，Go 侧无调用点 |
| `logging.go` `InstallLocalTracing` | `ProcessLogger.installLocalTracing`（`:133-144`） | REUSE | AlwaysSample + W3C trace context + baggage |
| `logging.go` `InstallFrameworkLogger` | **无** | — | **行为缺失 + 无 Java 测试**，见 §12.7 #4 |
| `logger.go` 3 个日志入口 | `logger/Logger`（`Logger.java:130-385`） | REUSE | 字段集/守卫/`image_base64` 截断/`fatal` 抛异常化 |
| `logger.go` `getCallChain` + `isFiltered` | `Logger.getCallChain` / `isFiltered`（`:429-476`） | ADAPT | **过滤已失效**，见 §12.7 #9 |
| `logger.go` `LogFatal` → `os.Exit(1)` | `Logger.doLogFatal` 抛 `IllegalStateException`（`:381-384`） | ADAPT | Spring 化合法：绕过 graceful shutdown 是不对的；**但退出码由调用方承担** |
| `logger.go` `Init` 全局单例 | `Logger.init`（`:112-122`）+ `LoggerFactoryBean` | SPLIT | 静态轨 + Bean 轨并存，见 §12.7 #2 |
| `logger.go` `filterPrefixes` | `Logger.FILTER_PREFIXES`（`:84-91`） | ADAPT | 已补 Java 侧前缀，但**因 #9 而无效** |
| `observability.go` 常量 | `ObservabilityConstants`（全 155 行） | REUSE | 7 个 key + 3 个 ErrorKind + 2 个 env 名逐条一致 |
| `observability.go` `SlowThreshold` | `Observability.slowThreshold`（`:45-58`） | ADAPT | 自实现 Go duration 子集解析（`:193-223`） |
| `observability.go` 4 个 `Mark*` | `Observability.mark*`（`:68-143`） | REUSE | `code==200` 降级 / `RecordError` / slow 判定逐条一致 |
| `observability.go` `IsTimeoutError` | `Observability.isTimeoutError`（`:149-162`） | REUSE | **覆盖不全**，见 §12.7 #10 |
| `http.go` `DisableNativeRestTimeout` | `HttpMiddleware.disableNativeRestTimeout`（`:116-118`） | ABSORB | Spring Boot 无原生超时中间件，取值即全部语义 |
| `http.go` `NewHTTPMiddleware` | `HttpMiddleware.newHttpMiddleware`（`:142-245`） | REUSE | skip 分支/503/499 三分支结构保真 |
| `http.go` buffer response writer | `ObservabilityResponseWriter`（`:368-524`） | REUSE | 全 buffer + `timedOut`/`clientCancelled` 双标志 |
| `http.go` `markHTTPResult` | `HttpMiddleware.markHttpResult`（`:286-305`） | REUSE | 判定顺序 envelope → 400 → slow，与 Go 一致 |
| `http.go` 超时写 499 给 client | **未实现**（`:214-220` 只记 span 后抛 `ServletException`） | — | **wire 行为缺失**，见 §12.7 #11 |
| `rpc.go` `NewUnaryServerInterceptor` | `RpcInterceptor.newUnaryServerInterceptor`（`:147-261`） | REUSE | 包装 `ServerCall.close` + `Listener.onCancel` |
| `rpc.go` 超时"真取消" | `DeadlineState`（`:507-563`） | ADAPT | Go `ctx.Done()` → ThreadLocal，**语义弱化已在 javadoc 声明** |
| `rpc.go` 7 个业务 code | `RpcInterceptor.isBusinessGrpcCode`（`:403-416`） | REUSE | 7 个 code 逐条一致 |
| `rpc.go` `DisableNativeRpcTimeout` | `RpcInterceptor.disableNativeRpcTimeout`（`:109-111`） | ABSORB | 同 http 版 |
| `mq.go` `TraceConsumer` | `MqConsumer.traceConsumer`（`:71-119`） | REUSE | 默认名/SpanKind/三分支一致；**多包了一层 `catch(Throwable)`** |
| `mq.go` ack/drop 语义 | — | — | **两侧都没有**；由未来 `mq/` 消费者承担，`UNKNOWN` |
| `response.go` `Response` | `response/Response`（17 行） | REUSE | 三字段一致，`data:null` 保留 |
| `snowflake.go` 位布局 + 自旋 | `snowflake/Snowflake.nextIdInternal`（`:214-250`） | REUSE | 41/10/12 + 回拨等待 + seq 耗尽自旋 |
| `snowflake.go` `generateNodeId` | `Snowflake.generateNodeId`（`:275-306`） | REUSE | env > hash > `%1024`；**已验证 uint16 回绕等价** |
| `snowflake.go` `Init` / `GetID` | `Snowflake.init` / `nextId` | ADAPT | Holder + `bindBean` 双路径，见 §12.7 #12 |
| `_test.go` × 4 | `common/src/test/java/**` 9 个测试类 | REUSE | 覆盖面见 §12.7 #5 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/common/
├── communityfact/
│   ├── Wire.java                       # KEEP   392  信封 + 校验 + JCS + DC sender
│   ├── Dispatcher.java                  # KEEP   288  投递循环
│   ├── AuthorityHandler.java           # KEEP   285  内部 HTTP 事实查询端点
│   └── ProcessLogger.java              # CHANGE 210  + framework logger 缺口
├── config/                             # ★ 新增包（§3.3 规则 8，见下）
│   ├── ObservabilityProperties.java     # SPLIT   43  ← spring/   （并补 rpc/http timeout 字段）
│   └── SnowflakeProperties.java        # SPLIT   37  ← spring/
├── exception/
│   └── BusinessException.java          # KEEP    38  业务异常基类
├── logger/
│   ├── Logger.java                     # CHANGE 516  + 修 isFiltered 失效
│   ├── LogOptions.java                 # KEEP    56
│   ├── LogOption.java                  # KEEP    26
│   ├── WithUserID.java                 # KEEP    42
│   └── WithArticleID.java              # KEEP    49
├── observability/
│   ├── Observability.java              # CHANGE 223  + isTimeoutError 覆盖
│   ├── ObservabilityConstants.java     # KEEP   155
│   ├── HttpMiddleware.java             # CHANGE 531  + 499 wire
│   ├── RpcInterceptor.java             # KEEP   564
│   └── MqConsumer.java                 # CHANGE 141  − 去掉 catch(Throwable)
├── response/
│   └── Response.java                   # KEEP    17
├── snowflake/
│   └── Snowflake.java                  # CHANGE 306  + bindBean 时序
└── spring/
    ├── HttpMiddlewareFilter.java          # KEEP 61   [需 jakarta.servlet] —— 由 api 模块显式 @Import
    ├── AuthorityHandlerFactory.java       # KEEP 49   [需 jakarta.servlet] —— 同上
    ├── RpcServerInterceptorFactory.java   # KEEP 37   [需 grpc-stub] —— 由 rpc 模块显式 @Import
    ├── LoggerFactoryBean.java             # MERGE 369 ← 合并进 logger/（见下）
    └── SnowflakeBean.java                 # KEEP 71
```

### 新增包说明（§3.3 规则 8）

**结论先行：不建议把两个 properties 类单纯"改名搬到 `config/`"就算修好；真正的问题是 `spring/` 这个包按"是不是 Spring 装配"划分，而正确的划分轴是"运行时 classpath 需要什么"。**

**1）`config/`（新增）——职责**：承载 `@ConfigurationProperties` 绑定，运行期只需 `spring-boot`，**不需要 servlet 也不需要 grpc**。
**当前 `spring/` 为何承载不了**：`spring/` 下 7 个类的运行时依赖是**三种互斥的**——`HttpMiddlewareFilter` / `AuthorityHandlerFactory` 需要 `jakarta.servlet`，`RpcServerInterceptorFactory` 需要 `grpc-stub`，`LoggerFactoryBean` / `SnowflakeBean` 两者都不需要。把 properties 混在中间，既让它跟着 servlet 走，也让"只想绑 `snowflake.worker-id` 的纯 gRPC 进程"被迫拉进 servlet 类。
**实际证据**：RAG 全仓每个 api/rpc 模块都把 properties 放在 `config/`（`UserRpcProperties` / `AdminRpcProperties` / `MessageProperties` / `HotRpcProperties` / `ArticleRpcProperties`），**common 是唯一违反项**。且 `pom.xml:105-109` 的 servlet 依赖是 `provided`——编译期在、运行期不在。

**2）~~`spring/web/` 与 `spring/grpc/`（新增）——已撤回~~**

> **2026-10-21 撤回**：本文件早前提出按运行时依赖拆 `spring/web/` + `spring/grpc/`。经 Go 侧核对，**拆包的原始动机已被自己的修法消解**，故撤回。
> **撤回理由**：`AdminRpcApplication.java:31-33` 与 `UserRpcApplication.java:31-34` 拒绝扫 `com.rag.common` 的理由是"全包扫描会把 `HttpMiddlewareFilter` 拖进纯 gRPC 进程"。但 §12.7 #1 的定案是**恢复 Go 的显式注册**——rpc 模块一律 `@Import` 精确导入所需 bean，**不再有任何模块扫 `com.rag.common`**。没人扫描 ⇒ servlet 类永远不会被拖进纯 gRPC 进程 ⇒ **classpath 冲突根本不存在** ⇒ 拆包失去必要性。
> **同时符合 §14.5 最小范围**：`@Import` 本就接受普通 `@Component` 类（`UserRpcApplication.java:39` 已在用 `SnowflakeBean.class` 等 3 个），加第 4 个是同类操作、**零搬迁**。为一个可由注解解决的装配问题新增 2 个包，是把简单问题复杂化。
> **Go 侧佐证**：Go 每个 rpc 服务都在入口显式 `s.AddUnaryInterceptors(...)`，**不存在"按 classpath 自动分组"这回事**——按运行时依赖分包在 Go 里同样不存在，不属于需要迁移的语义。

**3）`logger/` ← 合并 `LoggerFactoryBean`**——**职责**：服务名来源从"进程内静态初始化"改为"Spring 注入"。
**为什么不独立成文件**：`LoggerFactoryBean.java:87-282` 的 `logBusinessErr` / `logInfo` / `logFatal` 与 `Logger.java:162-385` 的三个方法**逐行同构**，连 `IMAGE_BASE64_TRUNCATE` 正则（`LoggerFactoryBean.java:64` vs `Logger.java:481`）、`getFuncName` / `getCallChain` / `isFiltered` / `fileBaseName` 5 个 helper 都是复制粘贴，**唯一差异只有 `serviceName` 的来源和第 7 个 filter 前缀**。合并形态：`Logger` 增加一个 `instance` 构造路径，`serviceName` 由构造器传入，删掉 `LoggerFactoryBean`。
> 注意：这与"`Logger.java` 不可接受，因为 Go 有 `logger.go`"是两条不同判断。`Logger` **保留**的理由是它已承载 8 个字段的稳定契约 + 3 个测试类 + `users` 侧注释锚点；**`LoggerFactoryBean` 删掉**的理由是它与 `Logger` 零行为差异——两者都独立于 Go 的文件名。

**4）不新建的包**：`advice` / `dto` / `domain` / `mapper` / `service` / `client` / `bootstrap` / `metrics` / `mq` —— 理由见 §12.1 表格，本模块无对应职责。

### 逐文件说明

| 文件 | 处置 | 关键说明 |
|---|---|---|
| `Wire` | KEEP | 唯一"必须逐字"的文件；`Event.payload` 用 `Object` 而非 `JsonNode`，跨语言序列化顺序由 JCS 兜底 |
| `Dispatcher` | KEEP | 唯一需注意 `duration_ms` 从 Go 的小数毫秒退化为整数毫秒（`:159`），低风险但应记档 |
| `AuthorityHandler` | KEEP | `LOOKUP_HINT` 字段（`:283-284`）是死代码，应删 |
| `ProcessLogger` | CHANGE | 补 framework logger 通道（#4） |
| `Observability` | CHANGE | 补 `HttpTimeoutException` 等（#10） |
| `HttpMiddleware` | CHANGE | 补 499 wire（#11） |
| `RpcInterceptor` / `MqConsumer` / `ObservabilityConstants` | KEEP/CHANGE | 见 §12.4 |
| `Snowflake` | CHANGE | `bindBean` 静态委托存在 ID 双计数器风险（#12） |
| `config/*` | SPLIT | 新增 2 个字段：`rpcTimeout` / `httpTimeout`（#3） |
| `spring/grpc/*`、`spring/web/*` | SPLIT | 见上 |
| `LoggerFactoryBean` | MERGE | 并入 `Logger`（见上） |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `wire.go`（DTO + 校验 + sender） | `communityfact/Wire` | KEEP | 逐字段已核对 |
| `wire.go`（2 个 sentinel） | `Wire.ERR_*` + `AuthorityHandler.AuthorityUnavailableException` | SPLIT | 需统一为类型判定（#7） |
| `process.go` | `communityfact/Dispatcher` | KEEP | 循环语义逐条对齐 |
| `process.go`（ctx 取消） | `DispatcherHandle.stop()` | ADAPT | 需调用方配 `@PreDestroy` |
| `authority_http.go` | `communityfact/AuthorityHandler` | KEEP | 4 分支保真 |
| `authority_http.go`（注册） | `spring/web/AuthorityHandlerFactory` | ADAPT | 容器托管 |
| `logging.go`（metadata + envelope + tracing） | `communityfact/ProcessLogger` | KEEP | 保真 |
| `logging.go`（`WithGroup`） | — | — | 无调用点，不迁 |
| `logging.go`（`InstallFrameworkLogger`） | `ProcessLogAppender` 的 framework channel | NEW | **行为缺口，需补**（#4） |
| `logger.go`（3 入口 + options） | `logger/Logger` | KEEP | 契约稳定 |
| `logger.go`（`getFuncName` / `isFiltered`） | `Logger.getCallChain` | ADAPT | **过滤失效，需修**（#9） |
| `logger.go`（`LogFatal` 退出） | `Logger.doLogFatal` 抛异常 | ADAPT | Spring 化；退出码责任转移给调用方 |
| `logger.go`（`Init` 单例） | `Logger` 实例轨 | MERGE | 与 Bean 轨合并，删 `LoggerFactoryBean` |
| `observability.go` | `observability/{Observability,ObservabilityConstants}` | SPLIT | 常量与逻辑分文件（Go 单文件，RAG 两文件） |
| `observability.go`（`IsTimeoutError`） | `Observability.isTimeoutError` | ADAPT | 覆盖不全，需补（#10） |
| `http.go` | `observability/HttpMiddleware` | KEEP | buffer writer 保真 |
| `http.go`（499 写回 client） | — | — | **缺失，需补**（#11） |
| `rpc.go` | `observability/RpcInterceptor` | KEEP | 7 业务 code 一致 |
| `rpc.go`（真取消） | `RpcInterceptor.DeadlineState` | ADAPT | 语义弱化已声明 |
| `rpc.go`（`DisableNative*`） | 同名静态方法 | ABSORB | 取值即语义 |
| `mq.go` | `observability/MqConsumer` | KEEP | ack/drop 两侧都无 |
| `response.go` | `response/Response` | KEEP | 逐字一致 |
| `snowflake.go` | `snowflake/Snowflake` + `spring/SnowflakeBean` + `config/SnowflakeProperties` | SPLIT | 生成算法 / workerId owner / 配置来源，三者变化原因不同 |
| `authority_http_test.go` | `WireTest` + `AuthorityHandlerTest`（2/4 有对应） | SPLIT | framework logger 与 subject-v2 两例**无 Java 对应**（#4/#5） |
| `logger_test.go` | `LoggerTest` + `LoggerFactoryBeanTest` | MERGE | 随实现合并 |
| `observability_test.go` | `ObservabilityTest` + `HttpMiddlewareTest` + `MqConsumerTest` | SPLIT | 按被测类拆 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| Go 14 个文件全部落位 | ✅ §12.2 全列，§12.3 逐个分析（含 4 个 `_test.go`） |
| L1 wire 契约逐字对齐 | ✅ `Wire.{Event,TechnicalReceipt,SubjectRef,AuthorityFact}` 4 组 tag 级一致；`DeliveryStatus` 0-3 一致 |
| canonical-JSON 哈希保真 | ✅ `Wire.java:288` 先 JCS 再 SHA-256（`WireTest` 覆盖）——若改成 raw-JSON 哈希会跨语言不可比，现实现正确 |
| 事务/并发语义无 Go 依赖 | ✅ 本模块零 DB 事务，无 `@Transactional` 风险（AGENTS.md §6.1 #2 类缺陷不适用） |
| 幂等键保真 | ✅ `Wire.java:188` `Idempotency-Key = event.eventId` |
| 响应体不泄漏语义保真 | ✅ `HttpMiddleware` 超时分支不 flush 已捕获 body，`HttpMiddlewareTest:86,119-121` 有断言 |
| 包职责已推导而非照抄 | ✅ §12.1 逐条说明 9 个约定包为何不适用，理由是"Go 侧无此职责"而非"不需要" |
| 拆分有证据支撑 | ✅ `spring/{web,grpc}` 的拆分依据是 `AdminRpcApplication.java:31-33` 的实测启动失败，不是风格偏好 |
| 未创建镜像 Go 文件名的类 | ✅ 全树无 `Middleware.java` / `ServiceContext.java` / `Types.java`；`Logger` 保留理由是契约而非文件名 |

### 不通过项

| # | 问题 | 详情与证据 |
|---|---|---|
| **1** | **P0：`RpcServerInterceptorFactory` 在两个 rpc 模块里都没生效** —— 这是 §3.3 的教科书案例 **【已定案 2026-10-21】** | `UserRpcApplication.java:31-34` 拒绝扫 `com.rag.common`，`:39` 的 `@Import` 只有 `SnowflakeBean/SnowflakeProperties/LoggerFactoryBean`；`AdminRpcApplication.java:30` 连 `@Import` 都没有。**后果：Go 侧 `observability.NewUnaryServerInterceptor` 的超时、7 业务码分类、`sea.slow` 标记在 RAG 的 user-rpc / admin-rpc 中全部丢失。**<br>**✅ 定案：显式 `@Import`，不拆包。** 依据：Go 侧每个 rpc 服务都在入口显式 `s.AddUnaryInterceptors(...)`（`user/rpc/user.go:43`、`admin/rpc/admin.go:43`、`hot/rpc/hot.go:42` 三处一致），**Go 里不存在"全局自动注册"** —— RAG 把 `@Component` 当自动注册是一次**无出处 idiomatization**（§3.2）。修法：`UserRpcApplication:39` 的数组加 `RpcServerInterceptorFactory.class`；`AdminRpcApplication` 补 `@Import(RpcServerInterceptorFactory.class)`。`@Import` 本就接受普通 `@Component` 类，**零搬迁**。<br>**⚠️ 连带撤回**：§12.5 原提的 `spring/web/` + `spring/grpc/` 拆包**不再需要**——没人再扫 `com.rag.common` ⇒ servlet 类不会进纯 gRPC 进程 ⇒ classpath 冲突不存在 ⇒ 拆包动机自我消解。`<br>**⚠️ 顺带**：`message-rpc` 现在能跑是**撞运气**（它 `scanBasePackages` 含 `com.rag.common`，却恰好有 `spring-boot-starter-web-server` 兜住 servlet 类），应一并归一到显式 `@Import`。 |
| **2** | **`logger` 689 行 + `LoggerFactoryBean` 369 行 ≈ 1058 行日志基础设施，实际消费者为零** | 实测：`com.rag.common.logger` 全仓**零外部 import**；`Logger` 是纯静态类、无 `@Component`、无门面，**不存在任何非 import 的装配机制**。`LoggerFactoryBean` 虽有 1 处注入（`MessageDomainService.java:93,103,111`），但 `grep "logger\.log\|logger\.getServiceName" message-rpc` **零命中**——注入后从未被调用。结论：**不成立，判为过度建设**。处置见 §12.5 第 3 条：合并成一个 `Logger`，净减约 370 行。 |
| **3** | **超时值彻底丢失**（本文件是该缺陷的归属地） | `ObservabilityProperties.java:35` **只有** `slowThreshold` 一个字段；`HttpMiddlewareFilter.java:52` 与 `RpcServerInterceptorFactory.java:36` 都把 `Duration.ZERO` 硬编码传下去。Go 侧 `DisableNativeRestTimeout`（`http.go:30-34` / `rpc.go:19-23`）的**全部语义就是把 `conf.Timeout` 取出来交回给观测中间件**——RAG 取出来之后直接扔了。**后果**：hot-api 的 `RestConf.Timeout=3000`、hot-rpc 的 `RpcServerConf.Timeout=2000` 在 RAG 侧等于**无超时**，慢请求会一直挂到下游超时。<br>**修复形状**：① `config/ObservabilityProperties` 增 `Duration httpTimeout` / `Duration rpcTimeout`；② `HttpMiddlewareFilter` 传 `props.getHttpTimeout()`（null → `ZERO`）、`RpcServerInterceptorFactory` 传 `props.getRpcTimeout()`；③ 各消费模块在自己的 `application.yml` 写 `observability.http-timeout: 3s` / `rpc-timeout: 2s`，值**逐服务**对应 Go 的 yaml。<br>**测试要求（§3.3）**：必须有一条 `@SpringBootTest` 装配 `ObservabilityProperties` 并断言传给 `HttpMiddleware`/`RpcInterceptor` 的 timeout 非 `ZERO`——纯 `new HttpMiddlewareFilter("svc", new ObservabilityProperties())` 的单测**不算**，因为它绕过了 `@ConfigurationProperties` 绑定这条真正会失效的链路。 |
| **4** | `InstallFrameworkLogger` 行为缺失 | Go `logging.go:104-145` 把 go-zero 全部生命周期输出（含信号停机）路由进 OBS-r3 envelope，并做 `trace→trace_id`/`span→span_id`/`caller→code_location` 字段改名与 10 个保留键丢弃。`ProcessLogger.java:44-45` 的 javadoc 写"Java 端没有 go-zero logx 等价物；删除对应方法，避免为了模仿而再造一层"。**这是拿"框架不同"当理由删掉了 Go 的一个行为保证**——Go 的保证是"框架日志与业务日志同一 envelope"，Java 侧 Logback 的框架日志（启动 banner、`Undertow started` 等）目前**不走** `ProcessLogAppender`。对应 Go 测试 `TestFrameworkLoggerUsesProcessEnvelope`（`authority_http_test.go:24-41`）**无 Java 对应**。需补：在 `ProcessLogAppender` 上开一条 framework logger 通道 + 一个等价测试。 |
| **5** | 测试强度不对等 | Go `observability_test.go` 用 `tracetest.SpanRecorder` **真装配 `sdktrace.TracerProvider`** 后断言 span attribute（6 个用例）。RAG 侧 `pom.xml:150-154` 已引 `opentelemetry-sdk-testing`，但需逐个确认 4 个 observability 测试类是否都走了真实 provider；凡只用 mock `Span` 的，**等于没测**（AGENTS.md §3.3）。`authority_http_test.go` 的 4 个用例中，framework logger 与 subject-v2 两个在 RAG 侧无对应测试。 |
| **6** | `exception/` 是合法新增，但要写明理由 | `BusinessException.java:6-10` 已正确标注 Go 双返回 → Java 异常。**无需修改**，仅登记：Go 侧无此包是因为 Go 用 `(resp, error)`；这是表达层差异不是行为差异，判**通过**。 |
| **7** | 两套 sentinel 识别机制并存 | `Wire.java:66-72` 的两个 `public static`（**非 final**）单例 `RuntimeException` 与 `AuthorityHandler.AuthorityUnavailableException`（`:55-63`，可子类化、`instanceof` 判定）并存。`Dispatcher.deliveryErrorCode`（`:266-272`）沿 cause 链做**身份 + message 相等**双判——任何被 wrap 且 message 恰好相同的业务异常都会被误判成 `INVALID_FROZEN_ENVELOPE`。Go 侧 `errors.Is` 没有这个问题。应统一到类型判定，并把两个字段改 `final`。 |
| **8** | `DispatcherHandle.stop()` 无自动配对 | Go 的 `ctx.Done()` 由宿主进程保证触发；RAG 的 `stopSignal` **必须**由业务显式在 `@PreDestroy` 里调。`Dispatcher.java:55-62` 的 javadoc 已写明用法，但**当前无任何调用方**。属未来 like/article 接入时的强制约定，需在那些文件里配对。 |
| **9** | **`call_chain` 过滤完全失效** | `Logger.getFuncName`（`:396-417`）返回的是 `短类名 + "." + 方法名`（`shortClass` = `declaringClass` 最后一段，**不含包名**），而 `FILTER_PREFIXES`（`:84-91`）里 6 项**全部是包名前缀**。⇒ `isFiltered` **永远返回 false**，Go 侧能滤掉的 `runtime.` / `github.com/zeromicro/go-zero/` 一帧都滤不掉，`call_chain` 会变成含框架噪声的栈转储。附带：`getFuncName` 的 `methodName.contains("$")` 分支与 fallthrough **返回值完全相同**，是死代码。`LoggerFactoryBean.java:286-301/335-342` 有同样问题。修法：`isFiltered` 改用 `frame.getDeclaringClass().getName()` 做前缀判定。 |
| **10** | `isTimeoutError` 漏判 HTTP 超时 | Go 侧 `errors.As(err, &netErr) && netErr.Timeout()` 覆盖所有 `net.Error`；RAG `Observability.isTimeoutError`（`:149-162`）只认 `TimeoutException` / `SocketTimeoutException` / gRPC `DEADLINE_EXCEEDED`。`java.net.http.HttpTimeoutException` 继承 `IOException`（**不是** `SocketTimeoutException`）⇒ `DCEventSender` 超时会被归类成 **system error 而非 timeout**，`sea.error.kind` 与 `sea.timeout` 都会错。需补 `HttpTimeoutException` / `SocketException` 分支。 |
| **11** | 499 没有到达 client | Go `http.go:83-88`：ctx 取消时 `http.Error(w, "Request Timeout", 499)` **写给客户端**。RAG `HttpMiddleware.java:214-220`：客户端取消分支只把 499 记进 span 属性，随后**无条件** `throw new ServletException(panic)` ⇒ client 收到容器的 500。`HttpMiddlewareTest` 的 javadoc 声称"覆盖 DRIFT-004（499 vs 503 区分）"，但**全部断言都是 503**（`:85,117`），499 分支**零测试**。 |
| **12** | `Snowflake.bindBean` 时序风险 | `Snowflake.nextId()`（`:191-200`）优先走 `boundBean`，否则走静态 `HOLDER`。若在 `SnowflakeBean` 构造完成前有任何一次 `nextId()`（例如更早的 Bean 初始化路径），HOLDER 与 Bean 各自持有**独立的 `State` 与 workerId**，同一 JVM 内出现**两个序列计数器**。Go 侧无此问题（`sync.Once` 只有一个 `sf`）。当前各模块的 `@Import` 顺序看起来安全，但**无测试锁定**。建议加一条断言"容器 refresh 完成后 `boundBean != null` 且首次 `nextId()` 走 Bean 路径"的测试。 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `communityfact` / `logger` 的真实消费形态 | 二者在 RAG 侧**零消费**，其设计是为 `like/comment/article` 准备的，而这些模块**尚无 Java 文件**。本文件只能判定"当前无消费方"，无法判定"未来接入方式是否与现签名兼容" |
| 跨文档 `REUSE` 声明的最终有效性 | 见下方冲突清单；判定依赖兄弟模块落地后才能验证 |
| `AuthorityLookup` 的 HTTP 端口归属 | Go 侧 `like` 的 authority 在独立进程独立端口；RAG 侧若并入 like-rpc 需新增端口（`like/rpc.md:595` 已列为待拍板）。common 只提供 Servlet，不决定端口 |
| `DCEventSender` 的 HTTP 客户端超时 | Go `like/cmd/fact-dispatch` 显式给 8s；common 的 `Sender` 接口不携带超时，由调用方构造 `HttpClient` 时决定。common 侧无默认值可核对 |
| `Dispatcher` 的 span 导出 | Go `InstallLocalTracing` **明确不导出**（`logging.go:92-93` 注释），只有 AlwaysSample + 本地 span。RAG 侧若接入 OTel exporter 会产生行为差异，需各消费模块显式确认 |
| 死代码清理是否属于本轮范围 | `AuthorityHandler.java:283-284` 的 `LOOKUP_HINT`、`HttpMiddleware.java:520-523` 的 `emptyHeaders`、`Observability` 中未使用的 `ATTR_TIMEOUT` / `ATTR_BIZ_CODE_LONG` / `ATTR_BIZ_MSG_STR` import。清理会动公共 API，需拍板 |

### 跨文档冲突（本文件必须点名，不得默认追认）

**实测结果：`service/common` 之外没有任何模块引用 `communityfact` 或 `logger`。** 因此兄弟文档中的下列 `REUSE` 断言，**按 §13.2 / §15.5 属"对未来采纳的投影"，不是既成事实**：

| 兄弟文档 | 位置 | 断言 | 实测状态 |
|---|---|---|---|
| `article/rpc.md` | `:197` | community 信封 `Wire.Event` = REUSE | article-rpc **零 Java 文件**，无法验证 |
| `article/rpc.md` | `:200`、`:202`、`:355` | `MqConsumer` / `RpcInterceptor` REUSE，"已全局注册" | ❌ **已证伪**：rpc 侧未注册（#1） |
| `like/rpc.md` | `:22,26,30-42,61-65,355,361-362,393,583` | `Wire` / `Dispatcher` / `AuthorityHandler` / `ProcessLogger` / `MqConsumer` / `AuthorityHandlerFactory` / `logger.Logger` 全部 REUSE | like-rpc **零 Java 文件**，全部属投影 |
| `like/rpc.md` | `:42,512` | `RpcServerInterceptorFactory` "已 `@Component` 全局注册" | ❌ **已证伪**（#1） |
| `like/rpc.md` | `:165` | "`RpcServerInterceptorFactory` 已用 `Duration.ZERO`，语义天然对齐" | ❌ **已证伪**：Go 的 `Duration.ZERO` 意思是"把 3s/2s 移交给观测中间件"，RAG 的 `Duration.ZERO` 意思是"根本没有超时"（#3） |
| `users/user/rpc.md` | `:34-35,42` | starter 自动收集 `ServerInterceptor` Bean ⇒ 生效 | ❌ **已证伪**：user-rpc 不扫 `com.rag.common`，Bean 不存在（#1） |
| `users/admin/rpc.md` | `:34,52,109,267,504` | "已全局注册" | ❌ **已证伪**（#1） |
| `message/rpc.md` | `:39,59` | 同上 | ✅ **成立**：`MessageRpcApplication.java:19-22` 确实扫了 `com.rag.common` |
| `users/user/rpc.md` | `:34` | 列 `HttpMiddleware` 为 user-rpc 可复用能力 | ❌ user-rpc 是纯 gRPC 进程、**刻意不引 servlet**，该 Bean 不可能注册 |

**处置建议**：不静默追认。① 按 #1 修完注册问题后，rpc 侧的"已全局注册"表述才成立；② article/like 的 `REUSE` 表述应统一降级为"待接入后验证"，并在其 §12.7 的 UNKNOWN 清单中登记本条。③ `like/rpc.md:165` 的"语义天然对齐"必须撤回——它把一个行为回归写成了设计对齐。