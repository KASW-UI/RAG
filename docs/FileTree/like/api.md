# like-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/like/api`
> 目标根包：`com.rag.like.like.api`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/like/like-api` **只有 `pom.xml`，零 Java 文件**（实测 `find service/like -type f` 只有 4 个 pom + 1 个 proto）。更关键的是 `service/like/like-api/pom.xml` **没有声明任何 dependency**——既无 `like-proto`，也无 `spring-boot-starter-web`、`common`。因此本模块是"空壳 + 空依赖"两层空白，包结构完全由已落地兄弟模块推导。

| 参照 | 提供的一致性依据 |
|---|---|
| `users/user/user-api` | `@RestController` + `@RequestMapping("/user/v1")`（`controller/UserController.java:69-70`）；`advice/GlobalResponseAdvice`；`advice/GlobalExceptionHandler`；`interceptor/JwtAuthInterceptor`；`config/{UserApiProperties,WebMvcConfig}`；`client/UserRpcClient`；`service/UserApiSupport`（uid 提取 + proto→DTO）；per-endpoint `service/User*Service` |
| `users/admin/admin-api` | 同款结构的平行证明（`interceptor/AdminJwtAuthInterceptor.java:57`） |
| `message/message-api` | 第三份平行证明，确认 api 层包名集合已稳定 |
| `service/common` | `response/Response.java:14-18`；`exception/BusinessException.java:26-38`；`observability/HttpMiddleware`（已 `@Component` 全局注册） |

**包名集合已定型**：`controller/`、`dto/`、`service/`、`client/`、`config/`、`advice/`、`interceptor/`、`metrics/`。本文件不提出集合外的任何包。

### 已有组件（跨模块可复用）

- **JWT 鉴权模式**：`users/user/user-api/interceptor/JwtAuthInterceptor.java` 已实现"JWT 解析 → 写 request attribute → Redis blacklist → fail-open"。like-api 需要**同款但独立 Bean**（密钥、public 路径集都不同）。
- **`Response` 信封 + advice**：`common/response/Response.java` + `advice/GlobalResponseAdvice.java:57-60`（含 `body instanceof Response` 放行，正是 AGENTS.md §6.1 #1 修复的落点）。
- **异常 → 业务码**：`advice/GlobalExceptionHandler.java:47-52` 已有 `BusinessException` → `ResponseEntity<Response>` 通路。
- **gRPC 下游客户端**：`client/UserRpcClient.java:22-28` 的 `@GrpcClient` 字段注入模式。
- **HTTP span**：`common/observability/HttpMiddleware` + `spring/HttpMiddlewareFilter`（全局），本模块无需自建。

### 已确认的框架机制

- `@RestControllerAdvice` + `ResponseBodyAdvice`（`common/response/Response` 信封）
- `HandlerInterceptor` + `WebMvcConfigurer`（`config/WebMvcConfig.java:28-31`）
- `@ConfigurationProperties`（`config/UserApiProperties.java:18`）
- `net.devh:grpc-client-spring-boot-starter` 的 `@GrpcClient`（`client/UserRpcClient.java:23`）
- Micrometer `MeterRegistry`；Nacos 服务发现（`application.yml` 的 `grpc.client.*.address: discovery:///user-rpc`）
- **无 Kafka 先例**、**无 Lua/Redis 先例**——但 api 侧不需要，二者都在 rpc 侧（见 `like/rpc.md`）

### 当前 Component 可以直接复用的能力

| 能力 | 依据 | 本模块用在哪 |
|---|---|---|
| `Response` 信封 | `common/response/Response.java` | 6 个端点的统一响应 |
| `BusinessException(int code, String msg)` | `common/exception/BusinessException.java:30-34` | uid 解析失败、业务码透出 |
| HTTP span / 慢调用标记 | `common/observability/HttpMiddleware`（全局注册） | 全部端点，零新增代码 |
| `@GrpcClient` 发现 | `users/user/user-api/client/UserRpcClient.java` | `like-rpc` 唯一下游 |

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **路由前缀** | ✅ **无需偏离**。Go 为 `rest.WithPrefix("/like/v1")`（`handler/routes.go:50`），与 RAG users 的 `/user/v1`、`/admin/v1` 惯例**已经一致**；6 个端点 method 也**全部已是 POST**（`routes.go:19,25,31,37,43,45`）。unlike article-api（Go `/v1/article` 需拍板），like-api 的 wire 契约**天然 1:1**，不涉及 AGENTS.md §3.4 任何一条留档偏离 |
| **public 路径集** | ✅ Go 侧**空集**——`routes.go:49` 对全部 6 条路由统一 `rest.WithJwt`。因此 Java 侧 `JwtAuthInterceptor` 的 `PUBLIC_PATHS` 应为空集，而不是照抄 user-api 的两元素集合 |
| **errmsg 归属** | Go 的 `service/like/common/errmsg` 被 api（6 个 logic）、rpc（5 个 logic + model 包）、mq（`likemq.go`）**三方共用**，不能像 article 那样只放进 api。需新建 maven 模块 `like-common`（先例：`message-common`、`users-common`） |
| **`DevServer` 段** | `etc/likecenter.yaml:20-27` 有 `DevServer{Enabled,Host:9099,MetricsPath:/metrics,...}`，但 `internal/config/config.go:11-18` 的 `Config` 结构体里**没有对应字段**。go-zero `conf.MustLoad` 对未知键的严格程度取决于 go-zero 自身定义（不在本仓库）→ `UNKNOWN`。RAG 侧后果明确：9099 的 `/metrics` + `/healthz` 无对应物，只能由 Actuator 端点覆盖 |
| **Redis blacklist** | like-api 的 Go 侧**只有 `rest.WithJwt`，没有 `CheckBlacklistMiddleware`**。Java 侧若沿用 `JwtAuthInterceptor` 的 blacklist 逻辑 = **新增行为**（`UNKNOWN`：Go like 是否也应加 blacklist，需拍板） |

---

## 12.2 第二部分：Go 原始文件树

```
service/like/api/
├── likecenter.go                              # 入口：REST server + 超时禁用 + HTTP span + 双 httpx handler
├── etc/likecenter.yaml                        # Name/Host/Port 18887/UserAuth/Telemetry/DevServer 9099/LikeRpc
└── internal/
    ├── config/config.go                       # RestConf + UserAuth{AccessSecret,AccessExpire} + LikeRpc
    ├── svc/servicecontext.go                  # 唯一 1 个下游 gRPC client：LikeRpc
    ├── handler/
    │   ├── routes.go                          # 6 条 POST + WithPrefix("/like/v1") + 全部 WithJwt
    │   └── like/*.go                          # 6 个同质薄 handler（parse → logic → OkJson）
    ├── logic/like/
    │   ├── getlikecountlogic.go               # 无 uid 提取；gRPC code → int 直传
    │   ├── getlikestatelogic.go               # uid(json.Number) + reject 指标 + gRPC 转发
    │   ├── gettargetlikerlistlogic.go         # **无 uid**（点赞人列表是公开读）；游标直传
    │   ├── getuserlikelistlogic.go            # uid + reject 指标 + 转发
    │   ├── getusertotallikelogic.go           # uid + reject 指标 + 转发
    │   └── likeactionlogic.go                 # uid + reject 指标 + float64→float32 窄化 + Success 恒 true
    ├── types/types.go                         # 6 组 HTTP DTO（含 action_type options=1|2|3|4、author_id,string）
    └── metrics/metrics.go                     # like_api_reject_total{route,reason}；InitMetrics 零调用点

shared:
service/like/common/errmsg/errmsg.go           # 30 个错误码 + codeMsg 表 + CodeError + NewGrpcErr（api/rpc/mq 三方共用）
```

---

## 12.3 第三部分：Go 文件职责分析

### `likecenter.go`
进程装配：
- `observability.DisableNativeRestTimeout(&c.RestConf)`（`:36`）——**主动关掉 go-zero 原生 REST 超时**，改由 observability 中间件统一计时
- `server.Use(observability.NewHTTPMiddleware(...))`（`:38`）——HTTP span + 慢调用阈值
- `httpx.SetOkHandler`（`:41-47`）：成功一律 `code=errmsg.Success(200)` / `msg="OK"` / `data=v`
- `httpx.SetErrorHandler`（`:48-61`）：`errors.As(err, &CodeError)` 成功 → 用 `e.Code/e.Msg`；失败 → `ErrorServerCommon(5001)`；**两分支都返回 `http.StatusOK`**
- `svc.NewServiceContext(c)` + `handler.RegisterHandlers`（`:63-64`）

- **L1 冻结**：所有响应 HTTP 状态码恒 200，业务失败只体现在 body 的 `code`。
- **L4**：Spring 侧 = `GlobalResponseAdvice`（成功包信封）+ `GlobalExceptionHandler`（失败直接产出 `Response`，且必须**只包一层**——`GlobalResponseAdvice.java:57` 的 `body instanceof Response` 放行是 §6.1 #1 的回归锁）。

### `etc/likecenter.yaml`
`Port: 18887`；`UserAuth.AccessSecret: ae0536f9-…-5a19ed505da0`（36 字节 UUID）、`AccessExpire: 31536000`；`LikeRpc.Etcd.Key: like.rpc`。**Secret 与 usercenter.yaml 完全同值**（同一 JWT 域），所以 like-api 验签用的就是 user-api 签发的那把钥匙。

### `internal/config/config.go`
`Config = rest.RestConf + UserAuth + LikeRpc zrpc.RpcClientConf`（`:11-18`）。**没有** Telemetry / DevServer / Log 字段——它们靠 `rest.RestConf` 内嵌的 `service.ServiceConf` 承载（或被忽略，见 §12.1 UNKNOWN）。

### `internal/svc/servicecontext.go`
`NewServiceContext` 只有两件事：持有 `Config`、构造 `likeservice.NewLikeService(zrpc.MustNewClient(c.LikeRpc))`（`:21`）。**无 Redis、无 DB、无 Kafka**——like-api 是纯转发网关。

### `internal/handler/routes.go`
一个 `AddRoutes` 块，6 条 `POST`：
`/getlikecount`、`/getlikestate`、`/gettargetlikerlist`、`/getuserlikelist`、`/getusertotallike`、`/likeaction`
装饰器：`rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)` + `rest.WithPrefix("/like/v1")`（`:49-50`）。
- **L1**：无 public 路由；无 `WithTimeout`；无 maxBytes；无 CORS 配置（与 article-api 不同，like-api 入口没挂 CORS）。

### `internal/handler/like/*.go`（6 个）
**零差异同质**：`httpx.Parse` → `New*Logic` → `err != nil ? httpx.ErrorCtx : httpx.OkJsonCtx`。
- handler **不做任何校验**：畸形 JSON 由 `httpx.Parse` 报错并直接进入错误信封（`data=null`，code 走 `ErrorServerCommon` 兜底还是解析错误原文，取决于 `httpx.Parse` 返回的 error 类型——它不是 `*errmsg.CodeError`，所以实际落到 `:49` 的 5001 兜底）。

### `internal/logic/like/getlikecountlogic.go`
无 uid 提取（`target_ids` 是 Feed 流批量刚需，公开读）。转发 `GetLikeCount`；错误处理：
```go
st, ok := status.FromError(err)
if ok { return nil, errmsg.NewErrCodeMsg(int(st.Code()), st.Message()) }
logger.LogBusinessErr(…); return nil, errmsg.NewErrCode(errmsg.ErrorServerCommon)
```
- **L2 关键语义**：rpc 侧抛的是 `errmsg.NewGrpcErr(code, msg)` = `status.Error(codes.Code(code), msg)`（`errmsg.go:33-35`），而 rpc 的 code 取值就是 **like 的 errmsg 数值空间**（2001/5001/5003/1006…）。`int(st.Code())` 是把这个数值原样取回。**即：like 的"错误码"不是 gRPC 标准码，是被塞进 gRPC status code 字段的 like 业务码**。这条往返链路是 api 侧错误的唯一来源，Java 侧必须原样保留，不能"规范化"成 `Status.Code.INVALID_ARGUMENT` 之类。
- `status.FromError` 只在**非 status 错误**时返回 `ok=false`；普通 Go error 会落到 5001 兜底。

### `internal/logic/like/getlikestatelogic.go`
uid 提取 + 拒绝指标 + 转发。uid 提取是 4 个 logic 共享的模板（`getuserlikelist`/`getusertotallike`/`likeaction` 完全一致）：
```go
userId, ok := l.ctx.Value("userId").(json.Number)   // :37
if !ok { ApiRejectCount{route:"/like/likeaction", reason:"token_invalid"}.Inc(); return 1006 }
uid, err := userId.Int64()
if err != nil { ApiRejectCount{route:"/like/likeaction", reason:"token_parse_error"}.Inc(); return 1006 }
```
- **L2 陷阱 1**：4 处 `route` 标签都硬编码为 **`"/like/likeaction"`**，包括 `getlikestate` / `getuserlikelist` / `getusertotallike` 三个根本不是该路径的端点。这是 Go 侧复制粘贴缺陷。`route` 是 Prometheus label，**改它属于指标契约变更**；本文件按 Go 原样保留并标注。
- **L2 陷阱 2**：这个 `route` 标签与真实路径 `/like/v1/getlikestate` 也对不上（缺 `/v1`、路径大小写不同）。
- **L2 陷阱 3**：`getlikecount` 与 `gettargetlikerlist` **不做 uid 提取**，所以同样一个坏 token，在这两个端点上会被 `rest.WithJwt` 放行到 logic 层再由 rpc 报错——与另外 4 个端点"api 层直接 1006"的行为**不对称**。

### `internal/logic/like/gettargetlikerlistlogic.go`
**无 uid**（点赞人列表不需要调用者身份）。`TargetType/TargetId/Cursor/PageSize` 四字段 1:1 直传，错误映射与 `getlikecount` 完全一致。

### `internal/logic/like/getuserlikelistlogic.go`
uid + reject 指标 + `UserId=uid` 注入 + 转发；响应 `list` 做 proto→DTO 投影（`TargetType/TargetId/Timestamp`）。

### `internal/logic/like/getusertotallikelogic.go`
uid + reject 指标 + 转发；响应只有 `TotalLikeCount`。

### `internal/logic/like/likeactionlogic.go`
uid + reject 指标 + 转发，三处**必须 1:1 保留的窄化/恒值**：
1. `ActionType: pb.ActionType(req.ActionType)`（`:54`）——`int64 → enum` **无校验**。api 侧不校验 `action_type ∈ {1,2,3,4}`；校验只发生在 rpc 侧（`likeactionlogic.go:169` 的三字段判空 + `likemq.go:93` 的 `State ∈ [1,4]`）。proto3 的 `ACTION_UNSPECIFIED=0` 会原样穿过 api。
2. `Weight: float32(req.Weight)`（`:56`）——`float64 → float32` **精度窄化**。Java 侧若用 `double` 接 `LikeActionReq.weight`（proto3 `float` 字段本身就映射成 Java `float`），须确认生成代码已做同样窄化，不要中途升成 `double` 再传给 rpc stub。
3. `Success: true` 硬编码（`:71`）——响应里的 `success` **永远是 true**，失败路径由 `err != nil` 分支走信封，压根不会到这里。rpc 侧同样硬编码（`rpc/internal/logic/likeactionlogic.go:304`）。这正是 AGENTS.md §1.1 列的"users 侧 `success` 语义反转"回归的镜像：**Java 侧绝不能把 `success` 改成"业务结果标志"**。

### `internal/types/types.go`
6 组 Req/Resp + 3 个共享 item（`LikeCountItem`/`LikeItem`/`LikeRecordItem`）。两个 goctl tag 值得记录：
- `ActionType int64 \`json:"action_type,options=1|2|3|4"\``（`:59`）——go-zero 的 `options` 校验在 `httpx.Parse` 阶段生效，非法值直接 400 类错误进错误信封。**Java 侧若不加 `@Min/@Max` 或自定义校验 = 校验时机后移**，属行为差异，需在 §3.2 留痕
- `AuthorId int64 \`json:"author_id,string"\``（`:60`）——**JSON 里是字符串形式的 int64**。Java DTO 必须用 `@JsonProperty` + 保持 string 序列化语义

### `internal/metrics/metrics.go`
`ApiRejectCount = CounterVec{Subsystem:"like_api", Name:"reject_total"}` labels `[route, reason]`。
- **L3 陷阱**：`InitMetrics()`（`:17-21`）负责 `prometheus.Register`，但**全仓零调用点**（实测 `grep -rn InitMetrics service/like` 只命中 3 处定义、0 处调用）。后果：Counter 在默认 registry 上**未注册**，`WithLabelValues().Inc()` 仍能累加（值存在 collector 里），但 Prometheus **抓不到**。Java 侧用 Micrometer 会**自动注册**，等于把 Go 侧"不生效"的指标变成生效——这是**指标面新增**，需在 §3.2 声明或按 Go 现状保持未暴露。

### `service/like/common/errmsg/errmsg.go`（api/rpc/mq 共用）
- 30 个常量（`:37-67`）：`Success=200`、`Error=500`、`CodeServerBusy=1015`、`5001-5012` 通用错误、`1001-1011` 用户/鉴权、`2001/2002` 输入、`3001` 构建 outbox 失败
- `codeMsg` 表（`:69-99`）——`GetErrMsg` 查不到时回退 `codeMsg[Error]`（"FAIL"），**不是空串**
- `NewGrpcErr(code, msg)`（`:33-35`）= `status.Error(codes.Code(code), msg)`——把 like 业务码塞进 gRPC status code
- api 侧实际会用到的：`Success(200)`、`ErrorServerCommon(5001)`、`ErrorTokenRuntime(1006)`、`ErrorInputWrong(2001)`、`ErrorRedisSelect(5011)`、`ErrorJsonUnmarshal(5008)`、`ErrorKafkaPush(5009)`、`ErrorSnowflakeID(5006)`

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `likecenter.go` 进程装配 | `LikeApiApplication` | ABSORB | Spring Boot 启动（`MessageRpcApplication.java:19-24` 同款） |
| `likecenter.go:36` 禁用原生超时 | 不做（框架无对应默认值） | ABSORB | Go 侧是"关掉 go-zero 默认超时"；Spring MVC 无等价默认超时，语义自动满足 |
| `likecenter.go:38` HTTP span | `common/observability/HttpMiddlewareFilter`（`spring/HttpMiddlewareFilter.java`） | REUSE | 已 `@Component` 全局注册，api 零新增 |
| `likecenter.go:41-47` SetOkHandler | `advice/GlobalResponseAdvice` | ADAPT | `beforeBodyWrite` 包 `new Response(200,"OK",body)`，语义等价 |
| `likecenter.go:48-61` SetErrorHandler | `advice/GlobalExceptionHandler` + `common/exception/BusinessException` | ADAPT | `CodeError` → `BusinessException(int, String)`；两分支 HTTP 状态码必须保持 **200** |
| `SetErrorHandler` 的 5001 兜底 | `GlobalExceptionHandler` 的兜底 `@ExceptionHandler(RuntimeException)` | ADAPT | 非 `CodeError` 异常 → 5001 |
| `config/config.go` + `UserAuth` | `config/LikeApiProperties` | ADAPT | `@ConfigurationProperties("like-api")`，`@NestedConfigurationProperty` 嵌套 `UserAuth`（`UserApiProperties.java:23-38`） |
| `svc/servicecontext.go` LikeRpc | `client/LikeRpcClient` | ADAPT | `@GrpcClient("like-rpc")` 字段注入（`UserRpcClient.java:22-28`） |
| `routes.go:50` `WithPrefix("/like/v1")` | `@RequestMapping("/like/v1")` | ADAPT | **Go 契约本就与 RAG 惯例一致，无偏离** |
| `routes.go:49` `WithJwt`（6/6 全覆盖） | `interceptor/LikeJwtAuthInterceptor` + `config/WebMvcConfig` | ADAPT | public 集为空集；`addPathPatterns("/like/v1/**")` |
| `routes.go` 无 `WithTimeout` | 不加超时 | ABSORB | Go 无此装饰器 |
| `routes.go` 无 maxBytes | 不加 multipart 限制 | ABSORB | like-api 无文件上传端点 |
| 6 个 handler | `controller/LikeController`（单一） | MERGE | 6 个 handler 逐字同质（parse→logic→envelope），无独立依赖边界、无独立生命周期 |
| `types.go` 6 组 Req | `dto/*Req` | SPLIT | 请求契约与校验要求独立；保留 `action_type` options 校验与 `author_id` string 语义 |
| `types.go` 6 组 Resp + 3 item | `dto/*Resp` + `dto/{LikeCountItem,LikeItem,LikeRecordItem}` | SPLIT | 响应契约独立；3 个 item 被多个 Resp 复用，单列 |
| `getlikecountlogic` 无 uid | `service/LikeCountService` | MERGE | 与其余 5 个 logic 同为"转发 + 错误映射"的单端点用例，合为一组 service 承载（理由同下 5 行） |
| 4 个 logic 的 uid 提取模板 | `service/LikeApiSupport.extractUserId` | MERGE | 与 `users/user/user-api/service/UserApiSupport.java:54` `extractUserId` 同款定位（request attribute → long） |
| 4 个 logic 的 reject 指标 | `metrics/LikeApiMetrics.reject(route, reason)` | ADAPT | Micrometer Counter，`route`/`reason` 两个 tag 值**按 Go 硬编码值原样** |
| `getlikestatelogic` uid + 转发 | `service/LikeStateService` | MERGE | 单端点用例 |
| `gettargetlikerlistlogic` 转发 | `service/TargetLikerListService` | MERGE | **无 uid**，与有 uid 的 4 个分开 |
| `getuserlikelistlogic` | `service/UserLikeListService` | MERGE | — |
| `getusertotallikelogic` | `service/UserTotalLikeService` | MERGE | — |
| `likeactionlogic` 转发 + 窄化 + `Success:true` | `service/LikeActionService` | MERGE | `Success=true` 硬编码语义保留 |
| 6 个 logic 的 gRPC 错误映射 | `GlobalExceptionHandler.handleRPC(StatusRuntimeException)` | ADAPT | `int(status.getCode())` → `Response(code, msg, null)`，与 `getlikecountlogic.go:41-43` 同款 |
| `httpx.Parse` 解析失败 | `GlobalExceptionHandler` 的 `HttpMessageNotReadableException` | ADAPT | 走框架级 400 或业务兜底，取决于实现；**必须在 §3.2 声明与 Go `httpx.Parse` 的差异** |
| `common/errmsg`（api 侧所需子集） | `like-common` 的 `errmsg/LikeErrorCode` + `LikeErrorMessages` | ADAPT | 模块划分先例：`message-common`（`MessageErrorCode.java:25` enum + `MessageGrpcError`） |
| metrics 指标名 | `metrics/LikeApiMetrics` | ADAPT | `like_api_reject_total`（Go `Subsystem:"like_api"` + `Name:"reject_total"` 的拼接结果） |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/like/like/api/
├── LikeApiApplication.java
├── advice/
│   ├── GlobalResponseAdvice.java
│   └── GlobalExceptionHandler.java
├── client/
│   └── LikeRpcClient.java
├── config/
│   ├── LikeApiProperties.java
│   └── WebMvcConfig.java
├── controller/
│   └── LikeController.java
├── dto/
│   ├── GetLikeCountReq.java
│   ├── GetLikeCountResp.java
│   ├── GetLikeStateReq.java
│   ├── GetLikeStateResp.java
│   ├── GetTargetLikerListReq.java
│   ├── GetTargetLikerListResp.java
│   ├── GetUserLikeListReq.java
│   ├── GetUserLikeListResp.java
│   ├── GetUserTotalLikeReq.java
│   ├── GetUserTotalLikeResp.java
│   ├── LikeActionReq.java
│   ├── LikeActionResp.java
│   ├── LikeCountItem.java
│   ├── LikeItem.java
│   └── LikeRecordItem.java
├── interceptor/
│   └── LikeJwtAuthInterceptor.java
├── metrics/
│   └── LikeApiMetrics.java
└── service/
    ├── LikeApiSupport.java
    ├── LikeCountService.java
    ├── LikeStateService.java
    ├── TargetLikerListService.java
    ├── UserLikeListService.java
    ├── UserTotalLikeService.java
    └── LikeActionService.java

com/rag/like/like/common/errmsg/            # ← 新增 maven 模块 like-common（先例：message-common / users-common）
├── LikeErrorCode.java
├── LikeErrorMessages.java
├── CodeError.java
└── LikeGrpcError.java

resources/application.yml                    # server.port=18887、like-api.user-auth.*、grpc.client.like-rpc
```

> **不提出集合外的新包**：本文件使用的 `advice/ client/ config/ controller/ dto/ interceptor/ metrics/ service/` 全部落在 user-api / admin-api / message-api 已定型的包名集合内；`errmsg` 落在 maven **模块**层（先例已存在），不是新包形态。

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `LikeApiApplication` | 入口，`scanBasePackages = {com.rag.like.like.api, com.rag.common}`，`@ConfigurationPropertiesScan` | `likecenter.go` | 框架约定 |
| `advice/GlobalResponseAdvice` | `beforeBodyWrite` 包 `Response(200,"OK",body)`；**必须对 `body instanceof Response` 与 `null` 放行** | `likecenter.go:41-47` | 与 users 同款；二次包裹是已实证缺陷（§6.1 #1） |
| `advice/GlobalExceptionHandler` | `BusinessException` → `Response(code,msg,null)` HTTP 200；`StatusRuntimeException` → `int(status.getCode())` 原样透传；`HttpMessageNotReadable` / 405 / 415 走框架级 | `likecenter.go:48-61` + 6 个 logic 的错误分支 | 同上 |
| `client/LikeRpcClient` | `@GrpcClient("like-rpc")` 阻塞 stub | `svc/servicecontext.go:21` | 外部服务客户端，`client/` 有先例 |
| `config/LikeApiProperties` | `user-auth.access-secret` / `access-expire` | `internal/config/config.go:13-16` + `likecenter.yaml:5-7` | 配置独立关注点 |
| `config/WebMvcConfig` | `addInterceptor(LikeJwtAuthInterceptor).addPathPatterns("/like/v1/**")` | `routes.go:49-50` | 与 users 同款 |
| `controller/LikeController` | 6 个 `@PostMapping`：`/getlikecount` `/getlikestate` `/gettargetlikerlist` `/getuserlikelist` `/getusertotallike` `/likeaction` | 6 个 handler | **6 个 Go handler 不拆 6 个 Controller**：逐字同质，拆开只制造 6 个单方法类 |
| `dto/*Req` | 请求契约；`LikeActionReq` 需保留 `action_type ∈ {1,2,3,4}` 校验与 `author_id` 的 string 序列化 | `types.go:6-64` | 请求与响应的可选性/校验规则不同 |
| `dto/*Resp` + 3 item | 响应契约；`LikeActionResp.success` 恒 `true` | `types.go:11-87` | 3 个 item 跨 Resp 复用，单列便于共享 |
| `interceptor/LikeJwtAuthInterceptor` | JWT 解析 → `request.setAttribute("userId", Long)` → blacklist（**见 §12.7 待拍板**）→ 写 `jwt_token` | `rest.WithJwt(AccessSecret)` | **必须独立 Bean**：密钥与 public 集都与 user-api 不同；且 Go 侧 public 集为空 |
| `metrics/LikeApiMetrics` | `like_api_reject_total{route,reason}` | `metrics/metrics.go:9-15` | 独立变化原因（Prometheus 名/tags 契约） |
| `service/LikeApiSupport` | 从 request attribute 取 uid；proto→DTO 投影工具 | 4 个 logic 的重复模板 | 与 `users/.../UserApiSupport` 同一形态 |
| `service/LikeCountService` | 无 uid，纯转发 + proto→DTO | `getlikecountlogic.go` | 单端点用例 |
| `service/LikeStateService` | uid 提取 + 转发 | `getlikestatelogic.go` | 同上 |
| `service/TargetLikerListService` | **无 uid**，纯转发 | `gettargetlikerlistlogic.go` | 与有 uid 的 4 个分开，因为 Go 侧的鉴权不对称是显式行为 |
| `service/UserLikeListService` | uid + 转发 + list 投影 | `getuserlikelistlogic.go` | 同上 |
| `service/UserTotalLikeService` | uid + 转发 | `getusertotallikelogic.go` | 同上 |
| `service/LikeActionService` | uid + 转发 + `float64→float32` 窄化 + `Success:true` | `likeactionlogic.go` | 同上 |
| `common/errmsg/LikeErrorCode` | 30 个 int 常量（enum） | `errmsg.go:37-67` | 与 `MessageErrorCode` 同款；数值是跨服务契约 |
| `common/errmsg/LikeErrorMessages` | `code → msg` 表 + 未知码回退 `"FAIL"` | `errmsg.go:69-107` | 与 `MessageErrorMessages` 同款 |
| `common/errmsg/CodeError` | 业务错误 unchecked 异常，`getMessage()` 输出 `ErrCode:X, Errmsg:Y` | `errmsg.go:10-17` | 与 `MessageGrpcError.CodeError` 同款 |
| `common/errmsg/LikeGrpcError` | `grpcError(int bizCode, String msg)` + `bizCodeFromError(Throwable)` | `errmsg.go:26-35` | 与 `MessageGrpcError` 同款；rpc 侧也要用，故不能放 api |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `likecenter.go` | `LikeApiApplication` | ABSORB | 进程装配由框架承担 |
| `likecenter.go:36` 禁用原生超时 | — | — | Spring MVC 无等价默认超时，无代码可落（不是 ABSORB，是"无对应物"） |
| `likecenter.go:38` HTTP span | `common/spring/HttpMiddlewareFilter` | ADAPT | `@Component` 需 `scanBasePackages` 含 `com.rag.common` 才注册（`user-api` 已如此） |
| `likecenter.go:41-47` SetOkHandler | `advice/GlobalResponseAdvice` | ADAPT | Spring advice 等价 |
| `likecenter.go:48-61` SetErrorHandler | `advice/GlobalExceptionHandler` | ADAPT | 异常处理机制换 Spring |
| `internal/config/config.go` | `config/LikeApiProperties` | ADAPT | 绑定机制换 Spring |
| `etc/likecenter.yaml:5-7` UserAuth | `config/LikeApiProperties.UserAuth` + `application.yml` | ADAPT | 同上 |
| `etc/likecenter.yaml:3` Port 18887 | `application.yml` `server.port` | ADAPT | 配置替代 |
| `etc/likecenter.yaml:20-27` DevServer | — | — | Go 侧 `Config` 无对应字段，9099 无 Spring 对应物（**UNKNOWN**，见 §12.7） |
| `svc/servicecontext.go` | `client/LikeRpcClient` | ADAPT | `zrpc.MustNewClient` → `@GrpcClient` |
| `routes.go:50` WithPrefix | `@RequestMapping("/like/v1")` | ADAPT | 契约本就一致 |
| `routes.go:49` WithJwt | `interceptor/LikeJwtAuthInterceptor` | ADAPT | 独立 Bean（密钥/路径不同） |
| `handler/like/*.go`（6 个） | `controller/LikeController`（单一） | MERGE | 同质薄适配层 |
| `types/types.go`（Req） | `dto/*Req` | SPLIT | 契约与校验独立 |
| `types/types.go`（Resp + item） | `dto/*Resp` + `dto/*Item` | SPLIT | 3 个 item 跨 Resp 复用 |
| `logic/getlikecountlogic.go` | `service/LikeCountService` | MERGE | 6 logic 对应 6 单端点 service（同一变化原因） |
| `logic/getlikestatelogic.go` | `service/LikeStateService` | MERGE | 同上 |
| `logic/gettargetlikerlistlogic.go` | `service/TargetLikerListService` | MERGE | 同上 |
| `logic/getuserlikelistlogic.go` | `service/UserLikeListService` | MERGE | 同上 |
| `logic/getusertotallikelogic.go` | `service/UserTotalLikeService` | MERGE | 同上 |
| `logic/likeactionlogic.go` | `service/LikeActionService` | MERGE | 同上 |
| 4 个 logic 的 uid 提取模板 | `service/LikeApiSupport.extractUserId` | MERGE | 跨 4 个 service 的重复片段 |
| 6 个 logic 的 gRPC 码映射 | `GlobalExceptionHandler.handleRPC` | ADAPT | 映射表落点统一到异常处理器 |
| `metrics/metrics.go` | `metrics/LikeApiMetrics` | ADAPT | Micrometer 1:1 复刻指标名/tags |
| `common/errmsg/errmsg.go` | `like-common` 的 4 个类 | ADAPT | 被 api+rpc+mq 三方共用 → 必须独立模块；数值 1:1 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 6 条 `POST /like/v1/*`；全量 `WithJwt`、空 public 集；`action_type options=1|2|3|4`；`author_id,string`；HTTP 状态码恒 200；成功信封 `{code:200,msg:"OK",data:...}` |
| L2 业务语义有承载位置 | ✅ uid 提取/类型断言/解析失败三分支；reject 指标的 `route`+`reason`；gRPC status code ↔ like 业务码的往返；`Success:true` 硬编码；`float64→float32` 窄化；`GetTargetLikerList`/`GetLikeCount` **无 uid** 的鉴权不对称 |
| L3 技术语义有承载方案 | ✅ `DisableNativeRestTimeout` 无对应物已声明；HTTP span 复用全局 Filter；`Response` 单层包裹（§6.1 #1 回归锁）；5xx 兜底 5001 |
| 业务分支与异常路径已纳入 | ✅ `status.FromError` ok 分支 / 非 status 错误兜底 5001；`httpx.Parse` 失败路径；uid 三态失败 |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；CORS 未配置 → 不配置；无超时装饰器 → 不加超时；无 multipart → 不配 limit |
| 复用组件已明确 | ✅ `Response`、`BusinessException`、`HttpMiddlewareFilter`、`@GrpcClient`、全局 `ServerInterceptor`（rpc 侧另述） |
| 新增文件有职责依据 | ✅ `LikeJwtAuthInterceptor`（独立密钥/空 public 集）、`LikeApiSupport`（跨 4 service 重复片段）、`like-common/errmsg`（三方共用）—— 均非"Go 有同名文件" |
| 合并有组织依据 | ✅ 6 handler → 1 Controller（同质薄层）；6 logic → 6 个单端点 service（与 article-api 的 6-service 形态一致）；uid 模板合入 `LikeApiSupport` |
| 拆分可解释 | ✅ Req/Resp 分离；3 个共享 item 单列；errmsg 提到 maven 模块层 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `Types.java` / `ServiceContext.java` / `Metrics.java` |
| 未把未实现能力说成现有机制 | ✅ `like-api` 现为空骨架 + 空依赖已在 §12.1 声明；本文件所有"复用"均给出实际打开过的路径 |
| 两文件拓扑结论一致 | ✅ 见 `FileTree/like/rpc.md` §12.1「多二进制 → 单 JVM」的同一结论：api 不参与合并争议，只新增 1 个 maven 模块 `like-common`（rpc 侧同一模块） |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **`route` 标签在 Go 侧是错的** | `getlikestatelogic.go:39,46`、`getuserlikelistlogic.go:39,46`、`getusertotallikelogic.go:39,46`、`likeactionlogic.go:39,46` 全部硬编码 `"/like/likeaction"`。Prometheus label 变更属指标契约变更。本文件按 Go 原样保留；**是否修正需拍板** |
| 2 | **`InitMetrics()` 零调用 → 指标实际未暴露** | `metrics/metrics.go:17-21` 的 `prometheus.Register` 从未被调用（实测全仓零调用点）。Micrometer 会自动注册，等于**新增一个可见指标**。保留 Go 现状还是接受增强，需拍板 |
| 3 | **Redis blacklist 是 Java 侧新增行为** | Go like-api 只有 `rest.WithJwt`，无 `CheckBlacklistMiddleware`。若 `LikeJwtAuthInterceptor` 照抄 user-api 的 blacklist 分支，被封禁 token 在 like 侧仍可用 |
| 4 | **`httpx.Parse` 的 `options` 校验时机** | `types.go:59` 的 `options=1|2|3|4` 在 go-zero 解析阶段生效并产生框架级错误；Java 侧若改用 `@Valid`，错误会变成 `BusinessException`/框架异常，信封里的 `code`/`msg` 会与 Go 不同 |
| 5 | **Go `config.Config` 无 `DevServer`/`Log`/`Telemetry` 字段** | `config.go:11-18` 只声明 3 段，yaml 却有 5 段。go-zero `conf.MustLoad` 对未知键的严格性不在本仓库内 → `UNKNOWN`。RAG 侧后果：`Telemetry.Name=like-api` → `otel.service.name`，`DevServer` 的 9099 无对应物 |
| 6 | **`like-api/pom.xml` 无任何依赖** | 缺 `like-proto`、`spring-boot-starter-web`、`common`、`grpc-client-spring-boot-starter`、`lombok`。**实现前必须先补 pom**，否则上述包结构一行都编译不过 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `DevServer` 段在 go-zero `conf.MustLoad` 下的行为 | 未知键是报错还是忽略，取决于 go-zero 库实现（不在本仓库） |
| `route` 标签的正确值 | 是修成 `/like/v1/getlikestate` 等，还是保持 Go 的错误值 |
| like-api 是否应补 blacklist 中间件 | Go 无此能力；补=新增行为 |
| `promotion_id` / `client_ip` / `weight` 的下游消费方 | api 侧直传 rpc，但 rpc 侧 `weight`/`client_ip`/`promotion_id` 在 `LikeAction` 主路径里**只写 Kafka 消息、DB 落库时不用**（见 rpc 文件）；防刷 `AntiBrush` 配置在 Go 侧零消费点 |
| Go 侧 `httpx.Parse` 对 `target_ids: []` 与 `target_ids: null` 的差异 | 影响 `@RequestBody(required=false)` 判定（与 users 的 P2 遗留同类问题） |