# hot-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/hot/api`
> 目标根包：`com.rag.hot.hot.api`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/hot/hot-api/pom.xml` 只有 26 行，**除 parent 与 artifactId 外零依赖**（连 `spring-boot-starter-web` 都没有），`src/` 目录不存在 → 零 Java 文件。`service/hot/hot-proto/src/main/proto/hot.proto` 已存在，与 Go 的 `proto/hot.proto` 逐字相同，只把 `option go_package = "./pb"` 换成 `option java_package = "com.rag.hot.hot.proto"` + `java_multiple_files = true`（已 `diff` 验证，仅此一处差异）。包结构依据来自同仓库已落地的 `users/{user,admin}-api` 与 `message/message-rpc`：

| 参照模块（已打开的文件） | 提供的一致性依据 |
|---|---|
| `users/user/user-api/advice/GlobalResponseAdvice.java` | `@RestControllerAdvice` + `ResponseBodyAdvice` 包装 `com.rag.common.response.Response`；**并在 `beforeBodyWrite` 里对 `body == null` / `body instanceof Response` 放行**——注释明写这是修 AGENTS.md §6.1 #1「二次包裹」回归的锁 |
| `users/user/user-api/advice/GlobalExceptionHandler.java` | `@ExceptionHandler(BusinessException/StatusRuntimeException/...)` → `ResponseEntity<Response>`；`decideStatus()` 当前**一律返回 200** |
| `users/user/user-api/controller/UserController.java` | `@RestController` + `@RequestMapping("/user/v1")` + `@PostMapping`；Controller 只做「接 DTO → 委托 Service → 返回」 |
| `users/user/user-api/dto/*.java` | 显式 `@JsonProperty("snake_case")`；`ApiTypesSnakeCaseTest` 用裸 `new ObjectMapper()` 锁 wire 契约 |
| `users/user/user-api/interceptor/JwtAuthInterceptor.java` | `HandlerInterceptor` + `StringRedisTemplate` 黑名单（**本模块不适用**，见下） |
| `message/message-rpc/client/UserRpcClient.java` | `@Component` + `@GrpcClient("user-rpc") UserServiceBlockingStub` facade；**失败吞掉并回退** |
| `message/message-rpc/service/impl/MessageRpcServiceImpl.java` | `@GrpcService` + 继承生成的 `…ImplBase` + `invoke(Supplier)` 统一兜底 |
| `common/response/Response.java` | `{int code; String msg; Object data;}`，与 Go `service/common/response/response.go` 的 `json:"code"/"msg"/"data"` 逐字一致 |

### 已确认的框架机制

- `@RestControllerAdvice` + `ResponseBodyAdvice`、`@ExceptionHandler` + `ResponseEntity`
- `@ConfigurationProperties`（`users/user/user-api/config/UserApiProperties.java`）
- `net.devh:grpc-client-spring-boot-starter` 的 `@GrpcClient`
- Micrometer `MeterRegistry` + Actuator Prometheus（`users/user/user-api/metrics/UserApiMetrics.java`）
- `common/observability/HttpMiddleware.java` + `common/spring/HttpMiddlewareFilter.java`：`@Component @Order(HIGHEST_PRECEDENCE+5)` 的 Servlet `Filter`。**前提**：hot-api 的 `scanBasePackages` 必须含 `com.rag.common`（`user-api` / `admin-api` 确实如此，`UserApiApplication` / `AdminApiApplication` 已验证）—— `@Component` 本身不等于自动注册
- `common/logger/Logger.java`（`init/logBusinessErr/logInfo/logFatal` 静态入口）与 `common/logger/WithArticleID.java`（Go `logger.WithArticleID` 的等价物）
- **Go 侧 `rest.RestConf` 的内建 middleware 语义见 §12.3 `hotcenter.go`——这是本模块最大的一处框架差异**

### 当前 Component 可以直接复用的能力

- **`common/response/Response` + advice 组合**：字段名与 Go envelope 完全一致，`REUSE`
- **HTTP span**：`common/spring/HttpMiddlewareFilter`（已 `@Component`），`REUSE`，不自建
- **业务异常**：`common/exception/BusinessException(int code, String msg)`，`REUSE`
- **业务错误日志**：`common/logger/Logger.logBusinessErr(ctx, 5001, err, WithArticleID.of(id))`，`REUSE`
- **下游 gRPC 客户端**：`@GrpcClient` + BlockingStub，`REUSE`（message-rpc 先例）
- **不需要**：`common/snowflake`（hot 全链路零 ID 生成）、`common/communityfact`（无信封事件）、`common/observability/MqConsumer`（api 侧无 MQ）、**`heavykeeper` sketch**（grep 验证 Go 侧只有 `rpc/internal/config` 与 `rpc/internal/mqs` 引用它，api 侧的热度只经 gRPC 从 hot-rpc 拿现成的 Top-K —— 本模块**不得**持有 sketch 副本，详见 `FileTree/hot/rpc.md`）
- **Redis**：本模块不直接用 Redis（Go 侧亦无），无需引入

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **端口** | Go `hotcenter.yaml` 为 `8893`；`Infrastructure.md` §3 给 `hot` 预占 **18897（HTTP）**，且只给了**一个**端口。hot-rpc 的 gRPC 端口**未分配**。本文件只写 `server.port` 来自配置，端口值**留空待编排确认** |
| **HTTP method** | Go 为 `GET /hot/v1/articles`；RAG 的 users 已留档偏离为统一 POST（AGENTS.md §3.4）。本文件按 **Go 的 GET** 推导，是否跟随 RAG 惯例**需拍板** |
| **路由前缀** | Go `/hot/v1` 与 RAG 已留档的 `/user/v1`、`/admin/v1` **同形**，无 wire 偏离 |
| **鉴权** | Go `routes.go` **没有任何中间件**（无 JWT、无 CORS、无签名）。RAG 的 `JwtAuthInterceptor` **不得引入**——加了就是行为变更 |
| **`hot-common` Maven 模块** | Go 有 `service/hot/common/errmsg.go`，grep 验证：**只有 api 引用，rpc 零引用**。该组件的推导已独立成文 → **`FileTree/hot/common.md`**（11 个码值逐字留档 + 落点决策 + 码表三方冲突）。⚠️ **本行原先援引的先例已失效**：原文写「按 `FileTree/article/api.md` 已批准的先例（`service/ArticleErrorCodes`），**不建 `-common` 模块**」——实测 `ArticleErrorCodes.java` 现已改写为「**骨架占位 · 已废止，请勿在此实现常量**」，真正落点是 `article-common/errmsg/ErrorCode`（15 个码）。⇒ 「不建 `-common` 模块」的原有依据已不存在；且 `service/hot/hot-common/pom.xml` **早已存在于磁盘**，与本行描述矛盾。✅ **落点已定（2026-10-22 实施）：落在 `hot-common`**，该模块已落地 `HotErrorCode` / `HotErrorMessages` 两个类 + 14 条契约测试（Go ↔ Java 双侧对拍 sha256 一致）。**详见 `FileTree/hot/common.md` §12.5 与 §12.7 #1** |
| **`hot-api/pom.xml` 零依赖** | 实现前必须补依赖，否则理论树无法编译。见 §12.7 不通过项 #1 |

---

## 12.2 第二部分：Go 原始文件树

```
service/hot/api/
├── hotcenter.go                                    # 进程入口：metrics 注册 + Ok/Error 信封 + observability 中间件
├── etc/hotcenter.yaml                              # Name/Host/Port/Log/Telemetry/DevServer/HotRpcConf/ArticleRpcConf
└── internal/
    ├── config/config.go                            # Config = RestConf + HotRpcConf + ArticleRpcConf
    ├── svc/serviceContext.go                       # 2 个 gRPC client（窄接口 HotRPC / ArticleRPC + adapter）
    ├── handler/
    │   ├── routes.go                               # 单路由 GET /hot/v1/articles，前缀 /hot/v1，无中间件
    │   └── hot/getHotArticlesHandler.go            # 薄 handler：Parse → Logic → OkJson
    ├── logic/hot/
    │   ├── getHotArticlesLogic.go                  # 唯一用例：TopK=100 → 100 路并发补全 → 过滤 → 重排名 → 分页
    │   └── getHotArticlesLogic_test.go             # 4 个用例锁住公开投影/默认值/过滤顺序/skip 容错
    ├── metrics/metrics.go                          # 5 个 Prometheus 指标 + 4 个 Observe 辅助
    └── types/types.go                              # HotArticleItem(13 字段) / HotArticlesReq / HotArticlesResp

共享：service/hot/common/errmsg.go                  # 本组件唯一使用的共享文件 → 推导见 FileTree/hot/common.md
```

---

## 12.3 第三部分：Go 文件职责分析

### `hotcenter.go`
进程装配，**四段职责都是行为契约，不是样板**：
1. `metrics.InitMetrics()`（第 33 行）注册 5 个 counter/histogram。
2. `httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)`（第 40 行）。该函数读 `conf.Timeout` 后把 `conf.Middlewares.Timeout = false`（Go `service/common/observability/http.go:30-34`）。**go-zero v1.10.2 `rest.RestConf.Timeout` 的 `json` 默认值是 `3000`（ms）**，`hotcenter.yaml` 未覆盖 → **hot-api 有 3 秒服务端超时，由 observability 中间件施加**。
3. `server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, SlowThreshold()))`。注意 `MiddlewaresConf` 的其余开关（Trace/Log/Prometheus/MaxConns/Breaker/Shedding/Recover/Metrics/MaxBytes/Gunzip）**默认全为 true**，只有 Timeout 被关掉。
4. `httpx.SetOkHandler` / `httpx.SetErrorHandler`（第 45-66 行）：**错误处理器的返回值恒为 `http.StatusOK`**，code 默认 `ErrorServerCommon(5001)`，只有 `*hotcommon.CodeError` 会被 `errors.As` 取出自己的 code，`Data` 恒为 `nil`。
- **L2**：`ErrorServerCommon` 的 msg 来自 `GetErrMsg`，即英文 `"internal server error"`（**不是** users 的中文「系统内部错误」）。
- **L2**：`logger.Init(c.Name)` 用的是 `Name: hotcenter`，而 `Log.ServiceName: hotcenter`、`Telemetry.Name: hot-api` —— 同一个进程三个名字。
- **L2**：`etc/hotcenter.yaml` 的 `DevServer` 块（`Enabled/Port:16067/MetricsPath/HealthPath`）**在 go-zero v1.10.2 的 `rest.RestConf` 里根本没有对应字段**，被 conf loader 静默忽略；`Prometheus` 块位于 `ServiceConf`，yaml 未给 `Host` → `StartAgent` 因 `len(c.Host)==0` 提前返回。**净结果：5 个指标注册进了默认 registry，但没有任何端点暴露它们。**

### `etc/hotcenter.yaml`
`Port: 8893`；`Log{Mode:file, Path:../../../../log/hot-api}`；`Telemetry{Name:hot-api, Endpoint:jaeger:4317, Sampler:1.0}`；`HotRpcConf{Etcd.Key: hot.rpc}`；`ArticleRpcConf{Etcd.Key: article.rpc}`。
- **L2 隐含语义**：`zrpc.RpcClientConf.Timeout` 的 json 默认值是 **2000ms**，yaml 未覆盖 → **两次下游 gRPC 调用各自带 2 秒客户端 deadline**（`zrpc/internal/client.go:115-116` 的 `TimeoutInterceptor`）。

### `internal/config/config.go`
`Config = rest.RestConf + HotRpcConf + ArticleRpcConf`。**除两个 `RpcClientConf` 外全部字段在 Spring Boot 里有原生归属**（`server.*` / `spring.application.name` / `grpc.client.*` / `management.*` / `otel.*` / `logging.*`），且两个 `RpcClientConf` 也被 `grpc.client.<name>.*` 接管 → **本模块不需要任何 `@ConfigurationProperties` 类**（见 §12.5 逐文件说明）。

### `internal/svc/serviceContext.go`
- 构造 2 个 gRPC client，并各自套一层**窄接口**（`HotRPC` 只有 `GetHotArticles`，`ArticleRPC` 只有 `GetArticle`）+ adapter struct（第 16-23、41-58 行）。
- `zrpc.MustNewClient` 两处：任一失败即 panic（进程起不来）。
- **L2**：`ArticleRPC` 暴露的是完整请求结构 `GetArticleRequest`，「`PublicOnly=true` + `IncrView=false`」的约束在 logic 里逐次赋值，不是类型级约束。

### `internal/handler/routes.go`
唯一 `AddRoutes` 块：`GET /hot/v1/articles`，`rest.WithPrefix("/hot/v1")`，**无 `WithJwt`、无 `WithCors`、无 `WithTimeout`、无 `WithMaxBytes`**。全组件只有 1 条路由、1 个 handler。

### `internal/handler/hot/getHotArticlesHandler.go`
`httpx.Parse(r, &req)` → `NewGetHotArticlesLogic` → `httpx.OkJsonCtx`；两条错误路径都走 `httpx.ErrorCtx`（→ 200 + envelope）。handler **不做任何校验**（`page=-1` 会原样进 logic）。

### `internal/logic/hot/getHotArticlesLogic.go`（唯一业务用例）
1. `defer metrics.ObserveRequest(route, started, err)`（第 56-58 行）——`err` 是**命名返回值**，成功时记 `result="success"`；`route` 是字面量 `"/hot/v1/articles"`。
2. `normalizePagination`（第 172-190 行）：`page<=0 → 1`、`pageSize<=0 → 20`、`pageSize>50 → 50`。与 `types.go` 的 form tag `default=1/20` **重复**（两层默认值）。
3. `HotRpc.GetHotArticles(TopK: 100)`——**TopK 硬编码 100**，请求里的任何取值都影响不到它。
4. 两条**致命**分支：`rpcErr != nil` → `ObserveRPCError("hot_rpc_get_hot_articles")` + `LogBusinessErr(5001)` + 返回 `NewErrCode(5001)`；`hotResp == nil` → `ObserveArticleSkip("hot_rpc_nil_response")`（**复用了 article skip 指标**）+ 5001。
5. `hydrateArticles`（第 91-124 行）：为 100 个 item **各起 1 个 goroutine**，用容量 8 的 channel semaphore 限流；结果按**下标**写入 `hydrated[idx]`，最后**按下标顺序**过滤 nil 并**重算 `Rank = len(result)+1`**。
   - **L2 关键**：`Rank` 是**过滤后的稠密序号，且跨页连续**（第 2 页首条是 `rank=2`，见 `_test.go:135-147`）。它**不是**页内序号。
6. `buildHotArticleItem`（第 126-170 行）4 条 skip 分支，各有独立 reason：
   - `hotItem == nil || GetArticleId()==""` → `empty_article_id`
   - `ArticleRpc.GetArticle` 报错 → `ObserveRPCError("article_rpc_get_article")` + `article_rpc_error` + `LogBusinessErr(5001, …, WithArticleID)` → **best-effort，整包继续**
   - `articleResp==nil || GetArticle()==nil` → `article_missing` + `LogInfo`
   - `status != PUBLISHED` → `article_not_published` + `LogInfo`
   - 成功时：`HotScore` 取自 **hot-rpc**，其余 11 个字段取自 **article-rpc 的公开投影**；`ArticleId` 用 `article.GetId()`（不是 `hotItem.GetArticleId()`）。
   - **L2**：这里的 `status` 是 **article-rpc 公开投影（frozen revision）的状态**，不是可变源行。源行处于 `REVIEWING` 时公开投影仍是 `PUBLISHED`，文章不会被误过滤（`_test.go:47-60` 专门锁住这条）。
7. `total = len(items)`（第 78 行）——**是过滤后的窗口大小（≤100），不是真实总数**；`Scope` 恒为 `"rolling"`（编译期常量）。
8. `paginate`（第 192-208 行）：`total<=0` 或 `start<0` 或 `start>=total` → `(0,0)` 空页；`start=(page-1)*pageSize` 是 **int32 乘法**，大 page 会溢出成负数 → 同样返回空页而不是报错。
- **L2**：整条链路把 `l.ctx`（请求 ctx）传进每一次下游 gRPC 调用，**客户端断开会连带取消 100 次补全**。
- **L2**：补全失败是 best-effort（skip），hot-rpc 失败是 fatal——**同一个「失败」概念在两层是相反的**，必须保留这个不对称。

### `internal/logic/hot/getHotArticlesLogic_test.go`
4 个用例，用 `fakeHotRPC` / `fakeArticleRPC` 替换 `svc.ServiceContext` 两个字段。`fakeArticleRPC.GetArticle` 第 35-37 行**主动断言** `in.PublicOnly && !in.IncrView`，否则直接返回错误——即「公开只读、不计浏览量」是 hot 域的硬不变量，被测试钉死。

### `internal/metrics/metrics.go`
5 个指标：`hot_api_request_total{route,result}`、`hot_api_reject_total{route,reason}`、`hot_api_request_duration_seconds{route}`、`hot_api_rpc_error_total{callee}`、`hot_api_article_skip_total{reason}`。4 个 `Observe*` 辅助。
- **L2**：`ObserveReject` **全仓零调用点**（grep 验证）→ `reject_total` 是死指标。
- **L2**：`observe` 的 4 个 reason 与 2 个 callee 名都是 L1 标签值（Prometheus 维度，改名即断看板）。

### `internal/types/types.go`
`HotArticleItem` 13 字段（wire 层 snake_case）；`HotArticlesReq{page,page_size}` 带 form 默认值；`HotArticlesResp{items,total,page,page_size,scope}`。
- **L2**：proto 的 `HotArticleItem` 只有 2 个字段，HTTP 层 13 个 → 11 个字段是 api 层补全的，**api 的 DTO 不是 proto 的投影**。

### `service/hot/common/errmsg.go`（api 唯一使用的共享文件）
**→ 推导已独立成文：[`FileTree/hot/common.md`](common.md)。** 本文件不重复其内容，只保留 api 侧用得到的结论：
- **L2（grep 验证的可达性）**：hot 组件内**只有 `Success`、`GetErrMsg`、`ErrorServerCommon`、`NewErrCode`、`CodeError` 被调用**（调用点逐行见 common.md §12.3 表）。其余 6 个常量 + `NewErrCodeMsg`/`GRPCError`/`BizCodeFromError` **全部零调用点**。
- **L2 码表冲突**：hot 的 `5002/5003/5004` 与 **users-common 与 article-common 双方**均语义冲突且文案语言不同（英文 vs 中文）。hot **必须**自持码表，禁止复用任何既有 `ErrorCode`。三方对照见 common.md §12.3。
- ⚠️ 「零调用点」**不构成删除依据**：`codeMsg` 必须整表保留，否则 `GetErrMsg` 的未知码回退失效（理由见 common.md §12.3）。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `hotcenter.go` 进程装配 | `HotApiApplication` | ABSORB | Spring Boot 启动 |
| `hotcenter.go` `InitMetrics()` | `metrics/HotApiMetrics` 构造器 | ADAPT | Micrometer 自动注册，5 个指标名/标签 1:1 |
| `hotcenter.go` `DisableNativeRestTimeout`（3s） | `common/spring/HttpMiddlewareFilter` 现有实现**不覆盖** | UNKNOWN | `ObservabilityProperties` 只有 `slowThreshold`，无 http timeout 字段。需先扩公共模块，见 §12.7 #2 |
| `hotcenter.go` `NewHTTPMiddleware` | `common/spring/HttpMiddlewareFilter`（`@Component`） | ADAPT | 同一中间件，但需 `scanBasePackages` 含 `com.rag.common` 才注册 |
| `hotcenter.go` `SetOkHandler` 信封 | `advice/GlobalResponseAdvice` | ADAPT | 产出 `{200,"OK",body}`，与 Go 逐字一致 |
| `hotcenter.go` `SetErrorHandler`（**恒 200**） | `advice/GlobalExceptionHandler` | ADAPT | **不可照抄 user-api 版**：Go 的 `decideStatus` 恒 200 且 body 恒 `{code,msg,null}` |
| `hotcenter.go` `logger.Init(c.Name)` | `common/logger/Logger.init("hotcenter")` | REUSE | 静态入口已存在 |
| `etc/hotcenter.yaml` 端口/日志/OTEL | `application.yml` 的 `server.*` / `logging.*` / `otel.*` | ABSORB | Spring Boot 原生属性 |
| `etc/hotcenter.yaml` `DevServer` 块 | — | — | go-zero v1.10.2 `RestConf` 无该字段，yaml 侧静默忽略；**Go 里本就不生效**，不迁移 |
| `etc/hotcenter.yaml` `HotRpcConf/ArticleRpcConf` Timeout=2000ms | `client/*` 内 stub `withDeadlineAfter(2s)` | ADAPT | devh starter 无默认 deadline，**必须显式补**，否则退化为无限等待 |
| `config/config.go` | 无独立类 | ABSORB | 全部字段被 Spring 属性接管，建空 Properties 类只会镜像 Go 文件名 |
| `svc` `HotRpc` 窄接口 + adapter | `client/HotRpcClient` | ADAPT | BlockingStub 本身就是 1 方法窄接口，无需 adapter |
| `svc` `ArticleRPC` 窄接口 + adapter | `client/ArticleRpcClient`（facade 只暴露 `getPublicArticle(articleId)`） | ADAPT | 见 §12.3 L2；facade 硬编码 `PublicOnly=true/IncrView=false`，把 Go 的「每次调用都要记得传对」升级为类型级约束 |
| `routes.go` 前缀 `/hot/v1` + `GET /articles` | `controller/HotArticleController` `@RequestMapping("/hot/v1")` + `@GetMapping("/articles")` | ADAPT | 与 RAG `/user/v1` 同形，无 wire 偏离 |
| `routes.go` 无中间件 | 不引入 `interceptor/`、不引入 `WebMvcConfig` | ABSORB | 加 JWT/CORS 都是行为变更 |
| handler `httpx.Parse` | `@RequestParam(name="page_size", defaultValue="20")` | ADAPT | Go form tag → query 参数名 `page_size` |
| logic `ObserveRequest` | `metrics/HotApiMetrics.observeRequest`（try/finally） | ADAPT | `route` 标签用字面量字符串，不可用 Spring mapping 对象 |
| logic `normalizePagination` / `paginate` / Rank 重算 | `service/HotRankPaginator` | SPLIT | 纯数值契约（1/20/50/100、稠密跨页 rank、int32 溢出→空页），零依赖，可脱离 HTTP 单测 |
| logic `hydrateArticles` + `buildHotArticleItem`（4 条 skip + 限流 8 + 顺序 + best-effort） | `service/HotArticleHydrator` | SPLIT | 拥有并发上限与「skip vs fatal」不对称策略，需能脱离 HTTP 回归 |
| logic `GetHotArticles` 主干 | `service/HotArticleQueryService` | MERGE | 唯一用例，负责编排与致命错误映射 |
| logic 的 4 条 skip reason / 2 个 callee 名 | `metrics/HotApiMetrics` 常量 | REUSE | L1 标签值 |
| `metrics` 5 个指标 | `metrics/HotApiMetrics` | ADAPT | Micrometer 1:1 |
| `metrics.ObserveReject` | `metrics/HotApiMetrics` 注册但零触发点 | REUSE | 保留指标名以免断看板；**不补触发点**（补即新增行为） |
| `types.go` `HotArticlesReq` | `dto/HotArticlesReq` | ADAPT | 归一化输入载体，让分页规则可脱离 Web 层单测 |
| `types.go` `HotArticlesResp` | `dto/HotArticlesResp` | ADAPT | `@JsonProperty` 显式 snake_case |
| `types.go` `HotArticleItem`（13 字段） | `dto/HotArticleCard` | ADAPT | 命名避开 proto 的 `HotArticleItem`，表明二者不是同一形状 |
| `errmsg.go` 全部职责（码表 / `GetErrMsg` / `bizCodeByMsg` / `CodeError` / 3 个不可达函数） | ➡️ **[`FileTree/hot/common.md`](common.md) §12.4** | — | 该组件已独立成文，本表不再逐行拆解。api 侧只依赖其中两条：`HotErrorCode` + `HotErrorMessages` 的码值（**禁止复用 users-common / article-common 的 `ErrorCode`**，三方 5002-5004 冲突）与 `BusinessException` 作为异常载体 |
| `common/observability/HttpMiddleware` 的信封解析 | `common/observability/HttpMiddleware` | REUSE | 依赖响应体是 `{code,msg,...}`，本模块 envelope 已对齐 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/hot/hot/api/
├── HotApiApplication.java
├── advice/
│   ├── GlobalResponseAdvice.java
│   └── GlobalExceptionHandler.java
├── client/
│   ├── HotRpcClient.java
│   └── ArticleRpcClient.java
├── controller/
│   └── HotArticleController.java
├── dto/
│   ├── HotArticlesReq.java
│   ├── HotArticlesResp.java
│   └── HotArticleCard.java
├── metrics/
│   └── HotApiMetrics.java
└── service/
    ├── HotRankPaginator.java
    ├── HotArticleHydrator.java
    └── HotArticleQueryService.java

⚠️ **本树不含错误码类**：`HotErrorCode` / `HotErrorMessages` 已随该组件独立成文 → [`FileTree/hot/common.md`](common.md)。
✅ **落点已定（2026-10-22 实施）：落在 `hot-common`**（`service/hot/hot-common/.../errmsg/`），本模块 `service` 包**不**放这两个类。两者仍是**码与文案分离的 2 个类**。
```

**本模块不新增顶层包**（只用 `advice/client/config/controller/dto/metrics/service`，全部是 RAG 已落地模块在用的包），故无「新增包说明」小节。

**刻意不建的三个文件及理由**（AGENTS.md 要求不得为镜像 Go 文件名而建文件）：

| 未建 | 理由 |
|---|---|
| `config/HotApiProperties.java` | Go `Config` 的 12 个字段在 Spring Boot 里全部有原生归属（`server.*` / `spring.application.name` / `grpc.client.*` / `management.*` / `otel.*` / `logging.*`），hot 无任何专属阈值——Go 侧的 `defaultPage/maxPageSize/maxHotArticles/hydrationConcurrency` 是**编译期常量**不是配置。建一个空 Properties 类只剩镜像价值 |
| `config/WebMvcConfig.java` | Go `routes.go` 无中间件、无 CORS。`WebMvcConfig` 唯一的用途是注册这两样 |
| `interceptor/JwtAuthInterceptor.java` | Go 侧零鉴权。引入即行为变更（公开热门列表突然要 token） |

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `HotApiApplication` | 入口；`scanBasePackages` 含 `com.rag.common`；`@ConfigurationPropertiesScan` 指向本模块 `config`（**当前为空，可不写**） | `hotcenter.go` main | 框架约定 |
| `advice/GlobalResponseAdvice` | 包装 `{code:200,msg:"OK",data}`；**必须对 `body == null` 与 `body instanceof Response` 放行** | `httpx.SetOkHandler`（`hotcenter.go:45-51`） | 同 user-api，但注释里必须写明这是 §6.1 #1 的回归锁 |
| `advice/GlobalExceptionHandler` | `BusinessException` / gRPC 异常 / 框架异常 → **HTTP 200** + `{code,msg,null}`；**不接管 404/405** | `httpx.SetErrorHandler`（`hotcenter.go:53-66`） | 与 user-api 的关键差异：Go 侧 handler 内的一切错误都是 200+envelope，而 404/405 由 go-zero router 直出裸响应；Spring 默认 404/405 已是裸响应，**照抄 user-api 的 400/405/415 空体处理反而制造偏离** |
| `client/HotRpcClient` | `@GrpcClient("hot-rpc")` + `getHotArticles(topK)`，**每次调用 `withDeadlineAfter(2s)`** | `svc.HotRPC` | 外部服务客户端；deadline 是 Go `RpcClientConf.Timeout` 默认值，devh 必须显式补 |
| `client/ArticleRpcClient` | `@GrpcClient("article-rpc")` + `getPublicArticle(articleId)`；**内部固定 `PublicOnly=true` / `IncrView=false` / 不设 `RequesterId`**，同样 2s deadline | `svc.ArticleRPC` + `buildHotArticleItem` 的两次赋值 | §3.2 留痕：这比 Go **多做**了一件事——把「调用点必须记得传对」变成「调用点无法传错」。等价性论证：Go 的 4 处调用点（唯一调用点在 `getHotArticlesLogic.go:132-136`）全部传同一组值；等价。**必须配一条断言这两个 flag 的测试**，对齐 `_test.go:35-37` |
| `controller/HotArticleController` | 单端点 `GET /hot/v1/articles`，2 个 `@RequestParam` | `routes.go` + `getHotArticlesHandler.go` | 只有一个方法；不拆 Controller 也不需要其它层 |
| `dto/HotArticlesReq` | 归一化前的 `page/pageSize`（原始 int） | `types.HotArticlesReq` | 让分页规则可脱离 Web 层单测 |
| `dto/HotArticlesResp` | `items/total/page/page_size/scope` | `types.HotArticlesResp` | wire 契约，`@JsonProperty` 逐字段 |
| `dto/HotArticleCard` | 13 字段，`rank` 为过滤后稠密序号 | `types.HotArticleItem` | 刻意不叫 `HotArticleItem`：proto 侧同名 message 只有 2 字段，同名会诱导下游误当成 proto 投影 |
| `metrics/HotApiMetrics` | 5 个 Micrometer meter + 4 个 Observe 辅助 | `metrics/metrics.go` | 独立变化原因（Prometheus 看板） |
| `hot-common/errmsg/HotErrorCode` + `hot-common/errmsg/HotErrorMessages` ✅ **落点已定** | **2 个类**：前者 enum 只管 11 个码值，后者管 `codeMsg` 表 + `getMsg(code)`（未知回退 `"FAIL"`）。**码与文案分离**，切分判据见 `FileTree/hot/common.md` §12.5 | `service/hot/common/errmsg.go` | ⚠️ 原树在此只画了**一个** `HotErrorCode`（码+文案+msg→code 反查全塞一起），职责超载，已按同仓三个 `-common` 的实测先例重切。原结论「**不建 `hot-common` 模块**」的援引先例亦已被推翻（`ArticleErrorCodes` 已废止），且 `hot-common/pom.xml` 早已存在于磁盘。✅ **已落 `hot-common`（2026-10-22 实施）**；类数（2）已定 |
| `service/HotRankPaginator` | `normalize()` + `paginate()` + `assignRanks()` | `getHotArticlesLogic.go:172-208` + `hydrateArticles` 的重排名段 | 纯函数、无依赖、L1 数值契约集中地；int32 溢出与跨页稠密 rank 这两条最容易被「顺手优化」掉 |
| `service/HotArticleHydrator` | 限流 8 的并发补全；4 条 skip 分支 + 各自 metric；按下标保序；失败 best-effort | `getHotArticlesLogic.go:91-170` | 拥有并发策略与「skip vs fatal」不对称，必须能脱离 HTTP 回归 |
| `service/HotArticleQueryService` | 计时 → 调 hot-rpc → 两条致命分支 → 补全 → 过滤/排名/分页 → 组装 resp | `getHotArticlesLogic.go:54-89` | 唯一用例的编排层 |

### 必配测试（§3.3）

| 测试 | 触发什么 |
|---|---|
| `HotApiWireContractTest`（`new ObjectMapper()`） | 锁 15 个 snake_case 字段名与类型（对齐 `ApiTypesSnakeCaseTest`） |
| `HotApiEnvelopeTest`（`@WebMvcTest` + MockMvc） | 真正的 `ResponseBodyAdvice` 装配路径：错误响应顶层 `code` **不能**恒为 200（§6.1 #1 的同款回归） |
| `HotApiErrorStatusTest`（MockMvc） | 业务异常 / 非法 query → HTTP 200 + `{5001,...}`；404/405 → 非 200 且无 envelope |
| `HotArticleHydratorTest` | 4 条 skip 分支各打一次；并发峰值 ≤8；输出顺序 == 输入顺序 |
| `HotRankPaginatorTest` | 默认 1/20、封顶 50、`rank` 跨页稠密、超大 `page` 返回空页而非报错 |
| `ArticleRpcClientTest` | 断言 `PublicOnly=true` 且 `IncrView=false`（对齐 `_test.go:35-37`） |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `hotcenter.go` | `HotApiApplication` | ABSORB | 进程装配由框架承担 |
| `hotcenter.go`（`SetOkHandler`） | `advice/GlobalResponseAdvice` | ADAPT | 信封换 Spring advice，`{200,"OK",data}` 逐字一致 |
| `hotcenter.go`（`SetErrorHandler`） | `advice/GlobalExceptionHandler` | ADAPT | 保留「恒 200 + data:null」 |
| `hotcenter.go`（`DisableNativeRestTimeout`→3s） | `HttpMiddlewareFilter` 现有实现不覆盖 | UNKNOWN | `ObservabilityProperties` 缺 timeout 字段 |
| `hotcenter.go`（`NewHTTPMiddleware`） | `common/spring/HttpMiddlewareFilter` | ADAPT | 需 `scanBasePackages` 含 `com.rag.common` |
| `hotcenter.go`（`InitMetrics`） | `metrics/HotApiMetrics` | ADAPT | Micrometer |
| `hotcenter.go`（`logger.Init`） | `common/logger/Logger.init` | REUSE | 已有静态入口 |
| `etc/hotcenter.yaml`（Name/Host/Port/Log/Telemetry） | `application.yml` 对应段 | ABSORB | Spring Boot 原生属性 |
| `etc/hotcenter.yaml`（`DevServer` 块） | — | — | go-zero v1.10.2 无此字段，Go 侧本就不生效，不迁移（非 ABSORB） |
| `etc/hotcenter.yaml`（2 个 `RpcClientConf`） | `grpc.client.hot-rpc` / `grpc.client.article-rpc` + stub deadline | ADAPT | 寻址换 Nacos discovery；2s 超时须显式补 |
| `config/config.go` | `application.yml` 各属性段 | ABSORB | 无专属字段，不建 Properties 类 |
| `svc`（`HotRpc`） | `client/HotRpcClient` | ADAPT | BlockingStub 即窄接口 |
| `svc`（`ArticleRpc`） | `client/ArticleRpcClient` | ADAPT | facade 固定公开只读参数 |
| `routes.go` | `controller/HotArticleController` | ADAPT | 单路由 |
| `routes.go`（无中间件） | 不引入拦截器/CORS | ABSORB | 加了就是行为变更 |
| `handler/hot/getHotArticlesHandler.go` | `controller/HotArticleController` 方法体 | MERGE | 薄适配层，与路由同文件 |
| `logic`（`normalizePagination`/`paginate`/rank） | `service/HotRankPaginator` | SPLIT | 纯数值契约独立可测 |
| `logic`（`hydrateArticles`/`buildHotArticleItem`） | `service/HotArticleHydrator` | SPLIT | 并发与 skip 策略独立 |
| `logic`（主干） | `service/HotArticleQueryService` | MERGE | 唯一用例 |
| `metrics/metrics.go` | `metrics/HotApiMetrics` | ADAPT | 5 指标 1:1 |
| `metrics` 的 `ObserveReject` | `HotApiMetrics` 注册零触发 | REUSE | Go 侧同样零调用，不补触发点 |
| `types.go`（Req） | `dto/HotArticlesReq` | ADAPT | — |
| `types.go`（Resp） | `dto/HotArticlesResp` | ADAPT | — |
| `types.go`（Item 13 字段） | `dto/HotArticleCard` | ADAPT | 与 proto 同名 message 形状不同，刻意改名 |
| `errmsg.go`（全部职责） | ➡️ **[`FileTree/hot/common.md`](common.md) §12.6** | — | 已独立成文并已实施。api 侧净变化仅两条：码值来源（`hot-common` 落点已定）与 `BusinessException` 复用；3 个函数的不迁移结论同见 common.md |
| `logic/hot/getHotArticlesLogic_test.go` | 6 条测试（§12.5 表） | ADAPT | Mockito 单测 + MockMvc，覆盖面比 Go 4 条更全 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ `GET /hot/v1/articles`；query `page`/`page_size`（默认 1/20、封顶 50）；响应 15 个 snake_case 字段；`scope="rolling"`；`top_k` 硬编码 100；`rank` 跨页稠密；`total` 为过滤后窗口大小 |
| L2 业务语义有承载位置 | ✅ 4 条 skip reason；`HotScore` 来自 hot-rpc 而其余字段来自 article-rpc 公开投影；`ArticleId` 取 article 侧；best-effort vs fatal 的不对称；过滤→重排名→分页的顺序；`total` 的真实含义 |
| L3 技术语义有承载方案 | ✅ 并发上限 8（`HotArticleHydrator` 固定池）；按 idx 保序；2s 客户端 deadline；int32 溢出→空页；客户端断开的 ctx 取消 |
| 业务分支与异常路径已纳入 | ✅ hot-rpc 报错 / `hotResp==nil` / 4 条 skip / 空列表 / 越界页；`httpx.Parse` 失败 → 200+5001 |
| 框架吸收有机制依据 | ✅ 进程装配→Spring Boot；文件日志→logback；OTEL/Trace 中间件→`HttpMiddlewareFilter`；服务发现→Nacos `discovery://` |
| 复用组件已明确 | ✅ `Response`、`BusinessException`、`Logger`+`WithArticleID`、`HttpMiddlewareFilter`、`@GrpcClient` |
| 新增文件有职责依据 | ✅ `HotRankPaginator`（纯数值契约）、`HotArticleHydrator`（并发+容错策略）、`HotArticleCard`（与 proto 形状不同故改名）—— 均非「Go 有同名文件」。错误码类（`HotErrorCode` + `HotErrorMessages`）已移出本树，职责依据见 `FileTree/hot/common.md` §12.5 |
| 无「为对应 Go 文件而建」的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `ServiceContext.java` / `Types.java` / `Metrics.java`；`HotApiProperties`、`WebMvcConfig`、`interceptor/*` 三个看似该有的文件被**显式论证为不需要** |
| 未把未实现能力说成现有机制 | ✅ `hot-api` 现状是「零 Java + 零依赖」；3s 超时缺口、`DevServer` 死配置、2s deadline 缺口均标为问题而非既成事实 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **`hot-api/pom.xml` 零依赖** | 只有 parent + artifactId，连 `spring-boot-starter-web` 都没有；`service/article/article-api/pom.xml` 同样为空骨架。理论树无法编译，实现前必须先补 `spring-boot-starter-web`、`grpc-client-spring-boot-starter`、`hot-proto`、`article-proto`、`common`、`actuator` + `micrometer-registry-prometheus` |
| 2 | **3 秒服务端超时无处安放** | Go 侧 `RestConf.Timeout` json 默认 3000ms，`DisableNativeRestTimeout` 关掉 go-zero 内建超时后把 3000ms 交给 observability 中间件 → **hot-api 实际有 3s 超时**。RAG 的 `common/spring/ObservabilityProperties` 只有 `slowThreshold`，`HttpMiddlewareFilter` 硬编码 `Duration.ZERO`。**需在 `service/common` 增补 http/rpc timeout 属性**（属他人拥有的公共模块，需协调）。否则超时静默丢失 |
| 3 | **路由 method 待拍板** | Go 为 `GET`；RAG 的 users 已留档偏离为统一 POST。本文件按 Go 推导，改则属 §3.4 类别的 wire 变更 |
| 4 | **2s 客户端 deadline 需显式补** | devh starter 没有默认 deadline。不补则 `article-rpc` 挂死时，100 次补全会串行堆满 8 并发槽，整请求可能挂到 Tomcat 线程超时 |
| 5 | **客户端断开的取消不再传播** | Go 把请求 ctx 传进每次补全；Spring 的 BlockingStub 不会因 Servlet 请求断开而取消。这是 Java **少做**的一件事（§3.2 需留痕），不构成漂移但会改变超时下的可观测行为 |
| 6 | **`hot_api_reject_total` 是死指标** | `ObserveReject` 零调用点。已按「保留注册、不补触发点」处理；若后续补触发点即为新增行为 |
| 7 | **指标在 Go 侧未被暴露** | yaml 未给 `Prometheus.Host` → go-zero 的 `StartAgent` 提前返回；`DevServer` 块在 go-zero v1.10.2 无对应字段。RAG 若接 Actuator 就是**行为改善**，需明确标注为有意偏离而非「对齐」 |
| 8 | ⚠️ **原「不建 `hot-common` 模块」的结论已失效** | 本条原文的推理链是「按 `article/api.md` 先例把常量放进业务模块」。**该先例已被推翻**：`service/article/article-api/.../ArticleErrorCodes.java` 现为「骨架占位 · 已废止，请勿在此实现常量」，真正落点是 `article-common/errmsg/ErrorCode`。⇒ 本条不再是一条「两种做法并存」的口径问题，而是一个**已拍板项**：现状四域中 `users`/`message`/`article` 都有独立 `-common` 模块，仅 hot 的文档说不建，**三对一**。且 `service/hot/hot-common/pom.xml` **已存在于磁盘**，与本条的表述直接矛盾。✅ **已落 `hot-common`（2026-10-22 实施）**，四域口径统一。**完整推导与证据见 [`FileTree/hot/common.md`](common.md) §12.5 / §12.7 #1–#3** |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| **端口分配** | `Infrastructure.md` §3 只给 `hot` 18897（HTTP）一个端口；hot-rpc 的 gRPC 端口未分配；Go 的 3 个 rpc 端口（8083/8084/8085）在 Java 侧应变成「同一部署 ×3 副本」，端口由环境变量给 |
| **HTTP method** | GET（Go）vs POST（RAG users 惯例） |
| **是否需要鉴权** | Go 侧公开无鉴权；若产品侧要求热门列表也带 JWT，那是**新增**行为而非对齐 |
| **`codeMsg` 中 5002-5008 是否保留** | ➡️ 已迁至 [`FileTree/hot/common.md`](common.md) §12.7 UNKNOWN。在 hot 内不可达；**必须整表保留**（否则 `GetErrMsg` 未知码回退失效），是否整表保留需拍板 |
| **`BizCodeFromError` 的 msg→code 反查** | ➡️ 已迁至 [`FileTree/hot/common.md`](common.md) §12.3（含判定顺序留档）与 §12.7。hot 零调用，**不建类**，但判定顺序已留档以备日后复用 |
| `Telemetry.Name: hot-api` 与 `Name: hotcenter` 不一致 | Java 侧统一用 `spring.application.name`，两个名字合并成一个属于表达层收敛，无歧义；但 OTEL `service.name` 与 Nacos 服务名取哪个需与运维对齐 |