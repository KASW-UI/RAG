# follow-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/follow/api`
> RAG 模块：`service/follow/follow-api`
> 目标根包：`com.rag.follow.follow.api`
> 推导日期：2026-10-23
> 文档性质：**理论推导**。RAG 侧 `follow-api` 当前**只有 `pom.xml`、0 行 Java**，
> 本文件是待实施的目标形态（先例：`FileTree/like/api.md` 于 2026-10-21 在 `like-api` 仍是空壳时写就，
> 其推导已被 2026-10-22 的实施结果反向验证）。
>
> **本文件覆盖 Go 侧全部 25 个文件（api 目录），已逐个读完。**

---

## 12.1 第一部分：目标 Java 项目组织依据

### RAG 当前文件树（事实快照，2026-10-23 实测）

```
service/follow/follow-api/
└── pom.xml                        # 19 行，无 <dependencies>、无 <build>
```

⚠️ **比 `like-api` 2026-10-21 时更空一层**：`like-api` 当时也缺依赖，但 `follow-api` **还多一项 Go 侧必须的能力**——
**Redis**（`BizRedis`，`followcenter.yaml:14-17`，被 `servicecontext.go:23` 真实消费）。
⇒ 实施时 `pom.xml` 至少要补 5 项依赖（见 §12.7 不通过项 #1）。

### 现有包（实际检查到）

`follow-api` 自身零 Java 文件 ⇒ 包结构由已落地兄弟模块推导。下表全部为**实际打开过的路径**：

| 参照模块 | RAG 位置（实测） | 提供的一致性依据 |
|---|---|---|
| **`like-api`（最贴近）** | `like/like-api/src/main/java/com/rag/like/like/api/` | 34 个文件：`advice/{GlobalResponseAdvice,GlobalExceptionHandler}`、`client/LikeRpcClient`、`config/{LikeApiProperties,LikeGoStyleCoercion,WebMvcConfig}`、`controller/LikeController`、13 个 `dto/*`、`interceptor/LikeJwtAuthInterceptor`、`metrics/LikeApiMetrics`、7 个 `service/*` |
| **`users/user/user-api`（黑名单先例）** | `users/user/user-api/src/main/java/com/rag/users/user/api/` | `interceptor/JwtAuthInterceptor.java:59-209` —— **唯一一份已落地、且真正实现了 Go `CheckBlacklistMiddleware` 的 RAG 代码** |
| `message/message-api` | `message/message-api/src/main/java/com/rag/message/message/api/` | 43 个文件；`metrics/MessageApiMetrics.java` 是 **request_total + reject_total + request_duration_seconds 三件套**的先例 |
| `hot/hot-api` | `hot/hot-api/src/main/java/com/rag/hot/hot/api/` | 无 `interceptor/`（Go 侧无 `WithJwt`）；含 `form/GoFormValues.java` —— 说明 api 侧允许在集合外新增包，但 follow 无此需求 |
| `service/common` | `service/common/src/main/java/com/rag/common/` | 38 个类：`response/Response`、`exception/{BusinessException,AuthenticationException}`、`http/{GoStyleRouterErrorResponses,GoStyleJsonBodyConverter,GoStyleJsonBodyConverterConfig}`、`observability/*`、`logger/*`、`protection/*`（10 个） |
| `users/users-common` | `users/users-common/src/main/java/com/rag/users/common/` | `jwt/{JwtUtil,BearerTokenExtractor}`、`http/{GoStyleCoercion,GoStyleRequired,GoStyleRequiredFields,GoStyleStringInt64,GoRequiredFieldException}`、`ddl/SchemaInitializer`、`errmsg/ErrorCode` |

**⇒ 包名集合已定型**：`advice/ client/ config/ controller/ dto/ interceptor/ metrics/ service/`。
本文件**不提出集合外的任何包**。

### 已有组件（跨模块可复用）

| 组件 | RAG 位置 | 判定 |
|---|---|---|
| `BearerTokenExtractor.tokenForSignatureVerification` | `users-common/…/jwt/BearerTokenExtractor.java:74-81` | **REUSE**：逐字等价 golang-jwt 的 `stripBearerPrefixFromTokenString`（**大小写不敏感**） |
| `BearerTokenExtractor.trimBearerPrefix` | `users-common/…/jwt/BearerTokenExtractor.java:95-99` | **REUSE**：逐字等价 `strings.TrimPrefix(authHeader, "Bearer ")`（**大小写敏感**）。⚠️ 这**正是** follow 黑名单中间件 `:33` 需要的那个取法 |
| `JwtUtil.padToHmacKeySize` | `users-common/…/jwt/JwtUtil.java` | **REUSE**：jjwt 要求 ≥32 字节 |
| `GoStyleStringInt64.parse(Object, String)` | `users-common/…/http/GoStyleStringInt64.java:83-` | **REUSE**：Go `,string` int64 的逐条等价解析器（**接受 JSON 字符串与 JSON 数字两者**） |
| `GoStyleRequired.field` / `GoStyleRequiredFields` | `…/http/GoStyleRequired.java:32`、`:67`；`GoStyleRequiredFields.java:46` | **REUSE**：实现 go-zero 的 `optional` 语义 |
| `GoStyleCoercion.applyTo(JsonMapper.Builder)` | `…/http/GoStyleCoercion.java:118` | **REUSE**：Jackson 3 共享强转规则（2 条） |
| `AuthenticationException` | `service/common/…/exception/AuthenticationException.java:47` | **REUSE**：401 出口的异常载体 |
| `GoStyleRouterErrorResponses` | `service/common/…/http/GoStyleRouterErrorResponses.java` | **REUSE**：404 / 405 的逐字节裸响应 |
| `GoStyleRequestDeadline.applyTo(stub)` | `service/common/…/observability/GoStyleRequestDeadline.java` | **REUSE**：gRPC 调用统一挂 deadline（like-api 已用，`LikeRpcClient.java:79`） |
| `LoggerFactoryBean` / `Logger` | `service/common/…/spring/LoggerFactoryBean.java`、`logger/Logger.java` | **REUSE**：替代 Go 的 `logger.Init(c.Name)` + `logger.LogBusinessErr` |
| `HttpMiddlewareFilter` | `service/common/…/spring/HttpMiddlewareFilter.java` | **REUSE**：替代 Go 的 `observability.NewHTTPMiddleware`（`:42`） |
| `GoStyleProtectionConfiguration` | `service/common/…/protection/GoStyleProtectionConfiguration.java` | **REUSE**：go-zero `rest.RestConf` 的 9 个中间件开关 + MaxConns/MaxBytes/CpuThreshold |

### 已确认的框架机制

- `@RestControllerAdvice` + `ResponseBodyAdvice`（`Response` 信封）
- `HandlerInterceptor` + `WebMvcConfigurer`（`config/WebMvcConfig.java:58-61`）
- `@ConfigurationProperties` + `@NestedConfigurationProperty`（`LikeApiProperties.java:37-41`）
- Jackson 3（`tools.jackson.*`）+ `JsonMapperBuilderCustomizer`（`LikeApiProperties` 同款 `WebMvcConfig.java:52-55`）
- `net.devh:grpc-client-spring-boot-starter` 的 `@GrpcClient`（`LikeRpcClient.java:60-61`）
- Micrometer `MeterRegistry`（`Counter` / `Timer`）
- Spring Data Redis（Lettuce）—— `StringRedisTemplate`（`users/user/user-api/…/JwtAuthInterceptor.java:17,73`）
- Nacos 服务发现（`application.yml` 的 `grpc.client.*.address`）
- **无 Kafka**（follow-api 无消息生产/消费）；**无数据库**（follow-api 零 SQL，`:23` 只有 Redis + gRPC client）

### 当前 Component 可以直接复用的能力

| 能力 | 依据 | 本模块用在哪 |
|---|---|---|
| `Response` 信封 | `Response.java:14-17` | 8 个端点的成功信封 |
| `BusinessException` / `FollowCodeError` | 见 `FileTree/follow/common.md` | 业务失败信封 |
| HTTP span / 慢调用 | `HttpMiddlewareFilter`（`@Component`，扫描即注册） | 8 个端点，零新增代码 |
| 请求保护层 4 道 | `GoStyleProtectionConfiguration`（扫描即注册） | 8 个端点，零新增代码 |
| `@GrpcClient` + Nacos | `LikeRpcClient.java:60` | `follow-rpc` 唯一下游 |
| Redis blacklist | `JwtAuthInterceptor.java:199-209`（`hasKey` + fail-open） | **本模块必须实现**（Go 侧真实存在，见下） |
| `GoStyleStringInt64` / `GoStyleRequired` / `GoStyleCoercion` | 上表 | 3 个 `,string` 字段 + 1 个必填字段 + Jackson 3 规则 |

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **路由前缀** | ✅ **无需偏离**。Go 为 `rest.WithPrefix("/follow/v1")`（`routes.go:63`），与 RAG users `/user/v1`、like `/like/v1`、hot 的惯例一致；8 个端点 method **全部已是 POST**（`routes.go:21,26,31,36,41,46,51,56`）⇒ **wire 契约天然 1:1**，不涉及任何偏离留档 |
| **public 路径集** | ✅ **空集**。`routes.go:62` 对 8 条路由统一挂 `rest.WithJwt`；`routes.go:17-18` 的 `rest.WithMiddlewares` 同样覆盖全部 8 条 ⇒ **无任何 public 端点**，`excludePathPatterns` 不需要 |
| 🔴 **401 有两种 body 形态** | ✅ **已核实**：① `rest.WithJwt` 失败 ⇒ `rest/handler/authhandler.go` 只 `WriteHeader(401)`、**空 body**（已由 like-api 的 `GlobalExceptionHandler.handleAuthentication:86-90` 实现并锁死）；② `CheckBlacklistMiddleware` 命中 ⇒ **HTTP 401 + `Content-Type: application/json;charset=utf-8` + body `{"code":5004,"msg":"unauthorized"}`**（`checkblacklistmiddleware.go:42-48`）。**这是 follow 域独有的第二种 401**，like / users 都没有（它们的 blacklist 复用了 401 空 body）。必须单独实现，不能让第 ② 种落到第 ① 种的 handler 上 |
| 🔴 **黑名单中间件的两个 early-pass 分支不可达** | `checkblacklistmiddleware.go:28-31`（header 为空 → 放行）与 `:34-37`（token 为空 → 放行）在 follow **永远不执行**：黑名单被 `rest.WithMiddlewares` 包在 `route.Handler` 内层（`routes.go:17-18`），而 `rest.WithJwt`（`routes.go:62`）是 chain 最外层（依据：`users/user/user-api/…/JwtAuthInterceptor.java:170-174` 对 go-zero `rest/engine.go:101-113` + `rest/chain/chain.go` 的逐层还原）。⇒ 验签失败时黑名单**从不被查**，Go 也不为畸形 token 产生任何 Redis 读。⇒ Java 侧**不必**实现这两个分支（但实现也无害，见 §12.7 不通过项 #4） |
| 🔴 **黑名单 token 的取法与验签 token 的取法不同** | `checkblacklistmiddleware.go:33` 用 `strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))` —— **大小写敏感**；而验签侧 golang-jwt 的 `stripBearerPrefixFromTokenString` 是**大小写不敏感**。⇒ header 为 `"bearer eyJ..."` 时：**验签用 `eyJ...`、黑名单查整串 `"bearer eyJ..."`** ⇒ 以小写 `bearer` 发送的**已拉黑 token 可以绕过黑名单**。这是 Go 自身的缺陷（`BearerTokenExtractor.java:88-93` 已把它记录为「Go 的真实行为，勿"修正"」）。Java 侧照抄 |
| **`DevServer` 段** | `followcenter.yaml:25-32` 有 `DevServer{Enabled:true, Host:0.0.0.0, Port:16064, MetricsPath:/metrics, HealthPath:/healthz}`，而 `internal/config/config.go:12-20` 的 `Config` 结构体里**没有对应字段**。go-zero `conf.MustLoad` 对未知键的严格程度取决于 go-zero 自身实现（不在本仓库）⇒ **`UNKNOWN`**。RAG 侧后果明确：16064 的 `/metrics` + `/healthz` 无对应物，只能由 Actuator 端点覆盖（like-api 的先例做法，`like-api/src/main/resources/application.yml:107-115`） |
| **`RestConf` 的 9 个中间件开关** | `followcenter.yaml` 里**一个字都没写**，但 go-zero `rest/config.go:12-22` 九个开关**全部 `json:",default=true"`** ⇒ **是开的**（AGENTS.md §3.3.1）。like-api 的 `application.yml:80-95` 已把该段显式写出，本模块照抄 |
| **`RestConf.Timeout`** | yaml 未写 ⇒ 走 `rest/config.go:55` 的 `default=3000` ⇒ **3000ms 生效**。这是 like 域踩过的坑（DRIFT-LIKEAPI-001，理由见 `like-api/src/main/resources/application.yml:7-29`）⇒ follow 必须配 `observability.http-timeout: 3s` |

---

## 12.2 第二部分：Go 原始文件树

```
service/follow/api/                                    # 共 25 个文件
├── followcenter.go                        # 入口：metrics.InitMetrics + 禁原生超时 + HTTP span + 双 httpx handler
├── etc/
│   └── followcenter.yaml                  # Name/Host/Port 8891/Log/UserAuth/BizRedis/Telemetry/DevServer 16064/FollowRpc
└── internal/
    ├── config/config.go                   # RestConf + UserAuth + FollowRpc + BizRedis（4 段）
    ├── handler/
    │   ├── routes.go                      # 8 条 POST + WithMiddlewares(黑名单) + WithJwt + WithPrefix("/follow/v1")
    │   └── follow/                        # 8 个逐字同质的薄 handler
    │       ├── followhandler.go
    │       ├── unfollowhandler.go
    │       ├── blockhandler.go
    │       ├── unblockhandler.go
    │       ├── getfollowlisthandler.go
    │       ├── getfollowerlisthandler.go
    │       ├── getblocklisthandler.go
    │       └── getrecommendationshandler.go
    ├── logic/follow/                      # 8 个 logic + 1 个 helper
    │   ├── helper.go                      # extractUserID(5 类型) / userLogOption / codeFromRPCError / resolveUserID
    │   ├── followlogic.go                 # route=/follow/v1/follow；无 resolveUserID、无 scope 检查
    │   ├── unfollowlogic.go
    │   ├── blocklogic.go
    │   ├── unblocklogic.go
    │   ├── getfollowlistlogic.go          # resolveUserID，**无 scope 检查** ⇒ 可查任意人的关注列表
    │   ├── getfollowerlistlogic.go        # 同上
    │   ├── getblocklistlogic.go           # resolveUserID + **forbidden_scope** 检查
    │   └── getrecommendationslogic.go     # 同上 + 嵌套 message 投影（跳过 nil）
    ├── metrics/metrics.go                 # 3 个指标 + InitMetrics（**本次真的被调用了**）
    ├── middleware/
    │   └── checkblacklistmiddleware.go    # Redis 黑名单 ⇒ **本域特色**（comment/favorite 无、like 无）
    ├── svc/servicecontext.go              # Config + FollowRpc client + CheckBlacklistMiddleware（+ Redis 连接）
    └── types/types.go                     # 5 组 DTO（3 个 ,string 字段）

shared:
service/follow/common/errmsg.go                         # 见 FileTree/follow/common.md
service/follow/rpc/followservice/followservice.go       # 8 个方法的客户端接口（被 api 消费）
```

**排除**：`service/follow/rpc/pb/*` 是 protoc 生成物（Go 侧 `pb` 目录），RAG 侧对应 `follow-proto`，**不作为推导输入**。

---

## 12.3 第三部分：Go 文件职责分析

### `followcenter.go`
进程装配，7 件事：
- `metrics.InitMetrics()`（`:33`）—— 🔴 **与 like 域不同**：like 的 `likecenter.go` **没有**这一行（`FileTree/like/api.md:166` 记为「零调用点」），
  而 **follow 真的调了** ⇒ follow 的 3 个指标**确实注册并被 Prometheus 抓取**。
  ⇒ Java 侧 Micrometer 自动注册**不是**「新增可见性」，而是**与 Go 对齐**（与 like 域的结论相反）
- `conf.MustLoad` + `logx.MustSetup` + `logger.Init(c.Name)`（`:36-38`）
- `observability.DisableNativeRestTimeout(&c.RestConf)`（`:40`）+ `server.Use(NewHTTPMiddleware(...))`（`:42`）
- `httpx.SetOkHandler`（`:45-51`）：`Response{Code: Success(200), Msg: GetErrMsg(200)="OK", Data: v}`
- `httpx.SetErrorHandler`（`:53-66`）：默认 `code=ErrorServerCommon(5001)` / `msg=GetErrMsg(5001)`；
  `errors.As(err, &codeErr)` 成功 ⇒ `code=codeErr.Code` / `msg=codeErr.Msg`；
  **两个分支都返回 `http.StatusOK`**
- `svc.NewServiceContext(c)`（`:68`）+ `handler.RegisterHandlers`（`:69`）

- **L1 冻结**：除 401 与框架级错误外，HTTP 状态码**恒 200**，成败只体现在 body 的 `code`。
- **L4**：`GlobalResponseAdvice`（成功包信封）+ `GlobalExceptionHandler`（失败直接产 `Response`）。

### `etc/followcenter.yaml`
`Port: 8891`（`:3`）；`Name: followcenter`（`:1`）；`UserAuth.AccessSecret: ae0536f9-…-5a19ed505da0`（`:11`，**与 user / like / message 同一把钥匙**）、`AccessExpire: 31536000`（`:12`）；
`BizRedis{Host: redis:6379, Type: node, Pass: ""}`（`:14-17`）；`Telemetry{Name: follow-api, Endpoint: jaeger:4317, Sampler: 1.0}`（`:19-23`）；
`DevServer{Port: 16064}`（`:25-32`）；`FollowRpc.Etcd.Key: follow.rpc`（`:34-38`）。
⚠️ yaml 里的 `Log`（`:5-8`）与 `Telemetry`（`:19-23`）在 `internal/config/config.go:12-20` 里**没有对应字段**（靠 go-zero `RestConf` 内嵌的 `service.ServiceConf` 承载或被忽略）⇒ 同 §12.1 的 `UNKNOWN`。

### `internal/config/config.go`
`Config = rest.RestConf + UserAuth + FollowRpc zrpc.RpcClientConf + BizRedis redis.RedisConf`（`:12-20`）。**4 段，全部有真实消费者**。

### `internal/svc/servicecontext.go`
三件事，**全部是真职责**：
- `redis.MustNewRedis(c.BizRedis)`（`:23`）—— 建立 Redis 连接，仅供黑名单用
- `followservice.NewFollowService(zrpc.MustNewClient(c.FollowRpc))`（`:27`）
- `middleware.NewCheckBlacklistMiddleware(redisDB).Handle`（`:28`）—— 构造路由级中间件

⇒ **follow-api 不是 like-api 那种「零基础设施的纯转发网关」**，它有 1 个 Redis 客户端 + 1 个 gRPC 客户端。

### `internal/handler/routes.go`
一个 `AddRoutes`（`:16-64`），**8 条全 POST**：
`/block`（`:22`）、`/block/list`（`:27`）、`/follow`（`:32`）、`/follow/list`（`:37`）、
`/follower/list`（`:42`）、`/recommendations`（`:47`）、`/unblock`（`:52`）、`/unfollow`（`:57`）。

三层装饰器，**顺序即语义**：
1. `rest.WithMiddlewares([]rest.Middleware{serverCtx.CheckBlacklistMiddleware}, routes...)`（`:17-18`）—— 包在**每条路由的 handler 内层**
2. `rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret)`（`:62`）—— **chain 最外层**（先执行）
3. `rest.WithPrefix("/follow/v1")`（`:63`）

⚠️ **无 `rest.WithTimeout`**（不像 message 域有），**无 CORS**（不像 article 域有），**无 maxBytes 装饰器**（但 `RestConf` 默认 `MaxBytes=1048576` 仍生效，见 §12.1）。

### `internal/handler/follow/*.go`（8 个）
**8 个文件逐字同质，零差异**（每个都是 31 行，结构完全一致）：

| 文件 | 请求 DTO | 调用的 logic 方法 | 关键行 |
|---|---|---|---|
| `handler/follow/followhandler.go` | `RelationActionReq` | `followlogic.Follow` | `:17` parse、`:23-24` 委派、`:25-29` 信封 |
| `handler/follow/unfollowhandler.go` | `RelationActionReq` | `unfollowlogic.Unfollow` | 同构 |
| `handler/follow/blockhandler.go` | `RelationActionReq` | `blocklogic.Block` | 同构 |
| `handler/follow/unblockhandler.go` | `RelationActionReq` | `unblocklogic.Unblock` | 同构 |
| `handler/follow/getfollowlisthandler.go` | `FollowListReq` | `getfollowlistlogic.GetFollowList` | 同构 |
| `handler/follow/getfollowerlisthandler.go` | `FollowListReq` | `getfollowerlistlogic.GetFollowerList` | 同构 |
| `handler/follow/getblocklisthandler.go` | `FollowListReq` | `getblocklistlogic.GetBlockList` | 同构 |
| `handler/follow/getrecommendationshandler.go` | `FollowListReq` | `getrecommendationslogic.GetRecommendations` | 同构 |

统一骨架：`httpx.Parse(r, &req)` → 失败即 `httpx.ErrorCtx` 并 `return` → `New*Logic(r.Context(), svcCtx)` → `err != nil ? ErrorCtx : OkJsonCtx`。
handler **不做任何校验**（`RelationActionReq` / `FollowListReq` 的必填与 `,string` 语义全由 `httpx.Parse` 的 go-zero mapping 层承担）。
⚠️ `httpx.Parse` 失败时返回的 error **不是** `*followcommon.CodeError` ⇒ 落到 `followcenter.go:54` 的 5001 兜底 ⇒ **HTTP 200 + `{code:5001,msg:"internal server error"}`**。

### `internal/logic/follow/helper.go`（4 个共享函数）
- **`extractUserID(ctx)`（`:13-33`）—— 本域最宽松的身份解析，5 种类型全收**：
  `nil` → error；`json.Number` → `Int64()`；`string` → `ParseInt(s,10,64)`；
  `int64` / `int` / `float64` → **直接转换**；其它 → error。
  ⚠️ **与 like 域截然不同**：like 只接受 `json.Number`（`FileTree/like/api.md:71`）。
  follow **接受字符串 claim**（`"userId":"123"`）与**浮点 claim**（`"userId":1.9` → `1`）。
  ⇒ Java 侧的实现必须复刻这 5 条，**不能照抄 like 的 `LikeApiSupport.userId`**。
- `userLogOption(userID)`（`:35-37`）
- `codeFromRPCError(err)`（`:39-41`）= `followcommon.BizCodeFromError(err)` ⚠️ 与 like 的同名函数**行为不同**（见下）
- **`resolveUserID(current, requested)`（`:43-48`）：`requested > 0 ? requested : current`** —— 「不传就查自己」

### 4 个 action logic（`followlogic.go` / `unfollowlogic.go` / `blocklogic.go` / `unblocklogic.go`）
**逐字同构，只有 3 处不同**：`const route` 字面量、调用的 rpc 方法名、`ObserveRelation` 的 action 名。

每个方法的完整骨架（以 `followlogic.go:31-63` 为准，4 份完全一致）：
```
route 常量（:32）→ started := time.Now()（:33）→ defer ObserveRequest(route, started, err)（:34-36）
  → extractUserID 失败：ObserveReject(route,"user_id_missing") + NewErrCode(ErrorUnauthorized=5004)（:38-43）
  → rpc 调用 RelationReq{UserId: uid, TargetId: req.TargetId}（:45-48）
  → rpcErr != nil：code = codeFromRPCError(rpcErr) → NewErrCode(code)（:49-53）
  → rpcResp == nil：NewErrCode(ErrorServerCommon)（:54-57）
  → rpcResp.Code != 200：NewErrCodeMsg(int(rpcResp.Code), rpcResp.Msg)（:58-60）
  → return RelationActionResp{Success: true}（:62）
```

**4 条必须逐字保留的语义**：
1. **`Success: true` 硬编码**（`:62`）。响应里的 `success` **永远为 true**；失败路径由 `err != nil` 分支走信封，根本到不了这里。
   ⚠️ 与 like 的 `LikeActionResp.success` 语义**同款**（`like/…/LikeActionResp.java:16-38` 已把「绝不能改成业务结果标志」写成类注释）。
   follow 更彻底：连 `rpcResp.Code` 都只在失败分支被读（`:58`），成功分支**不读 rpc 的任何字段**。
2. **rpc 错误的 msg 来自码表，不是 status description**：`NewErrCode(code)`（`:52`）会走 `GetErrMsg(code)`。
   ⚠️ **与 like 相反**：like 用 `NewErrCodeMsg(int(st.Code()), st.Message())` 保留 status 原文
   （`FileTree/like/api.md:203`、`LikeApiSupport.rpcError` 的类注释）。
   follow 因为 `follow-rpc` 的 `GRPCError(grpcCode, bizCode)` 写入的 description **就是 `GetErrMsg(bizCode)`**（`common/errmsg.go:98`），
   ⇒ 两条路径**按构造即相等**，用码表查表不会丢信息。这是 follow 与 like 的**关键结构差异**。
3. **无 uid 有效性校验**：`uid <= 0` / `target_id <= 0` / `user_id == target_id` **全部不在 api 层拒绝**，
   原样透传给 rpc（rpc 侧 `validateRelationReq` 才有 3 条判定，见 `FileTree/follow/rpc.md`）。
4. **`,string` 校验完全不在 api 层**：`target_id` 的 JSON 形态由 `httpx.Parse` 决定（见下）。

### `getfollowlistlogic.go` / `getfollowerlistlogic.go`（**形状 B**）
`route` 常量（各 `:32`）→ `ObserveRequest`（各 `:34-36`）→ uid 失败 5004（各 `:38-43`）→
**`queryUserID := resolveUserID(currentUserID, req.UserId)`（各 `:45`）→ 直接 rpc（各 `:46-50`）**。
🔴 **没有 `forbidden_scope` 检查** ⇒ **`/follow/list` 与 `/follower/list` 可以传任意 `user_id` 查别人的关注/粉丝列表**，
且**不会记 reject 指标**（与另两个端点不对称）。
⇒ 返回 `FollowUserListResp{UserIds: rpcResp.UserIds}`（各 `:64`），**1:1 直传，无投影、无过滤**。

### `getblocklistlogic.go` / `getrecommendationslogic.go`（**形状 C**）
前两步同形状 B，但在 `:45` 之后**多一条 scope 检查**：
```
queryUserID := resolveUserID(currentUserID, req.UserId)      (:45)
if queryUserID != currentUserID {                              (:46)
    ObserveReject(route, "forbidden_scope")                   (:47)
    LogBusinessErr(ctx, ErrorForbidden, NewErrCode(5005), ...) (:48)
    return nil, NewErrCode(ErrorForbidden)                     (:49)
}
```
- `getblocklistlogic.go:52-70` → `rpcResp.UserIds` 直传
- `getrecommendationslogic.go:70-81` → **嵌套 message 投影**：
  ```go
  users := make([]types.RecommendUser, 0, len(rpcResp.Users))
  for _, user := range rpcResp.Users {
      if user == nil { continue }        // :72-73 跳过 nil 元素
      users = append(users, types.RecommendUser{TargetId: user.TargetId, MutualScore: user.MutualScore})
  }
  return &types.RecommendListResp{Users: users}   // :81
  ```
  ⚠️ **空列表返回 `[]` 而非 `null`**：`make([]T, 0, n)` 保证非 nil ⇒ JSON 序列化为 `"users":[]`。

### `internal/middleware/checkblacklistmiddleware.go` —— **本域特色，8 条路由的必经关卡**
逐分支（**这是本文档最需要逐字保真的一段**）：
```
:27  authHeader := r.Header.Get("Authorization")
:28-31  authHeader == ""          → next(w, r) 并 return          ← 🔴 在 follow 不可达（见 §12.1）
:33  token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))   ← 大小写敏感 + TrimSpace
:34-37  token == ""               → next(w, r) 并 return（且不写 jwt_token） ← 🔴 不可达
:39  blacklistKey := fmt.Sprintf("user:jwt_blacklist:%s", token)
:40  exists, err := m.Redis.ExistsCtx(r.Context(), blacklistKey)
:41  if err == nil && exists {                       ← fail-open：Redis 故障 ⇒ 放行
:42      w.Header().Set("Content-Type", "application/json;charset=utf-8")
:43      w.WriteHeader(http.StatusUnauthorized)     ← HTTP 401
:44-48   w.Write([]byte(fmt.Sprintf(`{"code":%d,"msg":"%s"}`, 5004, "unauthorized")))   ← 只有 2 个字段，无 data
:49      return
:50  }
:52  ctx := context.WithValue(r.Context(), "jwt_token", token)
:53  next(w, r.WithContext(ctx))
```
**5 条 L1/L2 语义**：
1. **401 + JSON body `{"code":5004,"msg":"unauthorized"}`**，含显式 `Content-Type: application/json;charset=utf-8`。
   ⚠️ body **没有 `data` 字段**（不是 `{"code":5004,"msg":"unauthorized","data":null}`）。
2. **fail-open**：`err != nil` 时放行（`:41` 的 `err == nil &&`）。Redis 挂掉不会挡住业务。
3. **key 前缀 `user:jwt_blacklist:`**（`:39`）—— **与 users / admin 域完全同前缀**（跨服务共享的黑名单池），不是 `follow:...`。
4. **写 `jwt_token` 到 ctx**（`:52`）。⚠️ **follow 域零消费者**：实测 `service/follow/api/` 全树无 `ctx.Value("jwt_token")` 读取点
   ⇒ 该 ctx 写入是**纯副作用，无 L2 下游**，但仍属可观察（若日后新增端点）。
5. **`TrimSpace` 是 follow 独有的**（users 域的 `checkblacklistmiddleware.go` 无 `TrimSpace`，
   对应 RAG 的 `BearerTokenExtractor.trimBearerPrefix` 也不含 TrimSpace）⇒ Java 侧需 `+ .trim()`。

### `internal/metrics/metrics.go`
3 个指标（`Subsystem: "follow_api"`）：

| Go 变量 | 类型 | 导出名 | labels | 行号 |
|---|---|---|---|---|
| `APIRequestCounter` | CounterVec | `follow_api_request_total` | `[route, result]` | `:13-17` |
| `APIRejectCounter` | CounterVec | `follow_api_reject_total` | `[route, reason]` | `:19-23` |
| `APIRequestDuration` | HistogramVec（`DefBuckets`） | `follow_api_request_duration_seconds` | `[route]` | `:25-30` |

- `InitMetrics()`（`:33-39`）`sync.Once` + `MustRegister` ×3 —— 🔴 **本次真的被调用**（`followcenter.go:33`）
- `ObserveRequest(route, started, err)`（`:41-49`）：`result` ∈ {`success`,`fail`}，**判据是 logic 返回的 err 是否为 nil**
  ⇒ **返回了业务失败码也是 `fail`**
- `ObserveReject(route, reason)`（`:51-53`）

**8 个 `route` 字面量（全部正确，与 like 域的 3 处错误形成对照）**：

| Go 文件:行 | `route` 值 |
|---|---|
| `followlogic.go:32` | `/follow/v1/follow` |
| `unfollowlogic.go:32` | `/follow/v1/unfollow` |
| `blocklogic.go:32` | `/follow/v1/block` |
| `unblocklogic.go:32` | `/follow/v1/unblock` |
| `getfollowlistlogic.go:32` | `/follow/v1/follow/list` |
| `getfollowerlistlogic.go:32` | `/follow/v1/follower/list` |
| `getblocklistlogic.go:32` | `/follow/v1/block/list` |
| `getrecommendationslogic.go:32` | `/follow/v1/recommendations` |

✅ **8/8 与 `rest.WithPrefix("/follow/v1")` + 路径字面量完全一致** ⇒ 与 like 域的「4 处全写成 `/like/likeaction`」（`FileTree/like/api.md:140`）不同，
**follow 无指标标签缺陷**。

**2 个 `reason` 字面量**：`"user_id_missing"`（8 个 logic 各 1 处，各 `:40`）、`"forbidden_scope"`（`getblocklistlogic.go:47`、`getrecommendationslogic.go:47`）。

### `internal/types/types.go` —— **3 个 `,string` 字段，L1 出参形态不一致**

| 类型 | 字段 | tag | 行号 |
|---|---|---|---|
| `FollowListReq` | `UserId int64` | `json:"user_id,string,optional"` | `:7` |
| | `Offset int32` | `json:"offset,optional"` | `:8` |
| | `Limit int32` | `json:"limit,optional"` | `:9` |
| `FollowUserListResp` | `UserIds []int64` | `json:"user_ids"` | `:13` |
| `RecommendListResp` | `Users []RecommendUser` | `json:"users"` | `:17` |
| `RecommendUser` | `TargetId int64` | `json:"target_id,string"` | `:21` |
| | `MutualScore int32` | `json:"mutual_score"` | `:22` |
| `RelationActionReq` | `TargetId int64` | `json:"target_id,string"` | `:26` |
| `RelationActionResp` | `Success bool` | `json:"success"` | `:30` |

🔴 **4 条 go-zero tag 语义必须逐条落地**：
1. **`RelationActionReq.target_id` 没有 `optional`** ⇒ **必填**。缺失或显式 `null` ⇒ `httpx.Parse` 失败 ⇒ **HTTP 200 + 5001**。
2. **`FollowListReq` 三个字段全是 `optional`** ⇒ 缺失时为 Go 零值（`user_id=0` / `offset=0` / `limit=0`），
   **不报错**。其中 `user_id=0` 会被 `resolveUserID` 归一成当前用户（`helper.go:44-46`）。
3. **`offset` / `limit` 是 `int32` 且无 `,string`** ⇒ JSON 形态必须是**数字**。
   ⚠️ Jackson 默认会把 `{"offset":"10"}` 安静转成 10 ⇒ **Go 拒、RAG 收**（方向与 `FileTree/like/api.md` 记录的 like 补强 1 完全相同）⇒ 必须配域内 coercion 规则。
4. 🔴 **出参形态不一致（本域最隐蔽的 L1 差异）**：
   `FollowUserListResp.UserIds` **无** `,string` ⇒ JSON 是**数字数组** `[1234,5678]`；
   `RecommendUser.TargetId` **有** `,string` ⇒ JSON 是**字符串** `"1234"`。
   **Go 1.25.11 真源探针实测**（`json.Marshal(A{N int64 \`json:"n,string"\`})` ⇒ `{"n":"1234"}`）：
   `,string` 在 **marshal 方向对整型同样生效**，产出 JSON 字符串。
   ⇒ Java 侧 `RecommendUser.targetId` **必须**序列化为字符串，否则前端拿到 `1234` 而非 `"1234"`，类型不一致。

### `followservice/followservice.go`（api 消费的下游接口）
`FollowService` 接口 8 个方法（`:24-33`），`NewFollowService(cli)` + `defaultFollowService` 8 个转发方法（`:40-84`），
类型别名 6 个（`:16-22`，含 `RecommendResp_RecommendUser`）。
⇒ **纯机械转发层，零业务** ⇒ Java 侧由 `follow-proto` 生成的 `FollowServiceGrpc.FollowServiceBlockingStub` **ABSORB**。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `followcenter.go:33` 进程装配 | `FollowApiApplication` | ABSORB | Spring Boot 启动 |
| `followcenter.go:33` `InitMetrics()` | Micrometer 声明即注册 | ABSORB | 🔴 **follow 真的调了 InitMetrics** ⇒ Micrometer 自动注册是**对齐**而非增强（与 like 域结论相反） |
| `followcenter.go:36-38` 配置加载 / 日志初始化 | `@ConfigurationPropertiesScan` + `LoggerFactoryBean` | ABSORB | `LoggerFactoryBean` 承担 `logger.Init(c.Name)` |
| `followcenter.go:40` 禁用原生 REST 超时 | 不做（Spring MVC 无等价默认超时） | ABSORB | Go 是「关掉 go-zero 默认超时」；语义自动满足 |
| `followcenter.go:42` HTTP span | `common/spring/HttpMiddlewareFilter` | REUSE | `@Component`，`scanBasePackages` 含 `com.rag.common` 即注册 |
| `followcenter.go:45-51` SetOkHandler | `advice/GlobalResponseAdvice` | ADAPT | `beforeBodyWrite` 包 `new Response(200,"OK",body)`；**必须对 `body instanceof Response` 与 `null` 放行** |
| `followcenter.go:53-66` SetErrorHandler | `advice/GlobalExceptionHandler` | ADAPT | `FollowCodeError` → `Response(code,msg,null)`，HTTP **恒 200** |
| `followcenter.go:54` 5001 兜底 | `GlobalExceptionHandler.handleAny` | ADAPT | 非 `CodeError` 异常 → 5001 + `GetErrMsg(5001)` |
| `config/config.go:12-20` `UserAuth` | `config/FollowApiProperties.UserAuth` | ADAPT | `@ConfigurationProperties("follow-api")` + `@NestedConfigurationProperty` |
| `config/config.go:19` `BizRedis` | `application.yml` 的 `spring.data.redis.*` | ADAPT | 零业务字段，纯连接参数 ⇒ 不必占 Properties 的字段（`user-api` 同款做法） |
| `config/config.go:18` `FollowRpc` | `client/FollowRpcClient` 的 `@GrpcClient("follow-rpc")` | ADAPT | 寻址写进 `grpc.client.follow-rpc.address=discovery:///follow-rpc`；再放一份 yaml 字段即死配置（`LikeApiProperties.java:30-35` 已论证） |
| `svc/servicecontext.go:23` Redis 连接 | `StringRedisTemplate` | REUSE | `JwtAuthInterceptor.java:73` 已注入同一 Bean |
| `svc/servicecontext.go:27` FollowRpc client | `client/FollowRpcClient` | ADAPT | `@GrpcClient` 取代 `zrpc.MustNewClient` + `followservice.NewFollowService` 两层 |
| `svc/servicecontext.go:28` 构造黑名单中间件 | `interceptor/FollowBlacklistInterceptor`（`@Component`） | ADAPT | Spring 容器承担构造 |
| `routes.go:17-18` `WithMiddlewares(黑名单)` | `WebMvcConfig.addInterceptors` 的**第二条** | ADAPT | ⚠️ 注册顺序即执行顺序，JWT 必须在前 |
| `routes.go:62` `WithJwt(AccessSecret)` | `interceptor/FollowJwtAuthInterceptor` 的**第一条** | ADAPT | 独立 Bean（密钥与 public 集与其它域不同） |
| `routes.go:63` `WithPrefix("/follow/v1")` | `@RequestMapping("/follow/v1")` | ADAPT | **Go 契约本就与 RAG 惯例一致，无偏离** |
| `routes.go` 无 public 路径 | `WebMvcConfig` **不加** `excludePathPatterns` | ABSORB | 8/8 全量鉴权 |
| `routes.go` 无 `WithTimeout` / 无 CORS | 不配 | ABSORB | Go 无此装饰器 |
| `handler/follow/*.go`（8 个） | `controller/FollowController`（单一） | MERGE | 8 个 handler 逐字同质（`Parse → logic → 信封`），无独立依赖边界 |
| `types.go:6-10` `FollowListReq` | `dto/FollowListReq` | SPLIT | 3 字段全 `optional` + `user_id` 带 `,string` |
| `types.go:25-27` `RelationActionReq` | `dto/RelationActionReq` | SPLIT | `target_id` **必填** + `,string` |
| `types.go:29-31` `RelationActionResp` | `dto/RelationActionResp` | SPLIT | `success` 恒 true，响应契约独立 |
| `types.go:12-14` `FollowUserListResp` | `dto/FollowUserListResp` | SPLIT | 出参形态（数字数组）与 `RecommendListResp` 不同 |
| `types.go:16-18` `RecommendListResp` | `dto/RecommendListResp` | SPLIT | 空列表必须序列化为 `[]` |
| `types.go:20-23` `RecommendUser` | `dto/RecommendUser` | SPLIT | 🔴 `target_id` **必须序列化为字符串**（见 §12.3 第 4 条） |
| `types.go:7` `user_id,string,optional` | `GoStyleStringInt64.parse` + `GoStyleRequired` 豁免 | REUSE | 已有实现逐条等价 |
| `types.go:26` `target_id,string`（必填） | `GoStyleStringInt64.parse` + `GoStyleRequired.field` | REUSE | 同上 |
| `types.go:8-9` `offset` / `limit`（无 `,string`） | `config/FollowGoStyleCoercion` 补强 1 | ADAPT | `LogicalType.Integer` + `CoercionInputShape.String → Fail`（同 `LikeGoStyleCoercion`） |
| `logic/follow/helper.go:13-33` `extractUserID` | `service/FollowApiSupport.extractUserId` | ADAPT | 🔴 **5 种类型全收**，与 like 的实现**不可共用** |
| `logic/follow/helper.go:35-37` `userLogOption` | `com.rag.common.logger.WithUserID` | REUSE | 已有 `logger/WithUserID.java` |
| `logic/follow/helper.go:39-41` `codeFromRPCError` | `service/FollowApiSupport.rpcError` | ADAPT | 调 `FollowGrpcError.bizCodeFromError`；⚠️ **与 like 的同名函数 msg 来源不同** |
| `logic/follow/helper.go:43-48` `resolveUserID` | `service/FollowApiSupport.resolveUserId` | ADAPT | `requested > 0 ? requested : current` |
| `logic/follow/helper.go:41` 的失败码 | `FollowErrorCode.ERROR_UNAUTHORIZED`（5004） | ADAPT | 8 个 logic 的 `:42` |
| 4 个 action logic | `service/{Follow,Unfollow,Block,Unblock}Service` | MERGE | 同一形状（3 处差异），但**每个端点有独立 `route` 指标标签** ⇒ 见 §12.5「为什么不是 1 个 Service」 |
| action logic 的 `Success: true` | 各 Service 的返回 `new RelationActionResp(true)` | ADAPT | 硬编码语义保留 |
| action logic 的 rpc 错误路径 | `FollowApiSupport.rpcError` → `NewErrCode(code)` | ADAPT | **msg 查表**，不是 status 原文 |
| `getfollowlistlogic.go` / `getfollowerlistlogic.go` | `service/GetFollowListService` / `GetFollowerListService` | MERGE | 形状 B：**无 scope 检查** |
| `getblocklistlogic.go` / `getrecommendationslogic.go` | `service/GetBlockListService` / `GetRecommendationsService` | MERGE | 形状 C：**有 `forbidden_scope` 检查** |
| 形状 B/C 的差异 | 两条独立的 Service 组，**不合并** | — | 这是**可观察的鉴权不对称**，合并会掩盖它 |
| `getrecommendationslogic.go:70-79` 嵌套投影 | `GetRecommendationsService` 内联 + `dto/RecommendUser` | MERGE | 与主流程同生命周期；无第三方复用 |
| `getrecommendationslogic.go:72-73` 跳过 nil | Java protobuf **不产生 null 元素** | ADAPT | ⚠️ 详见 §12.7 不通过项 #5 |
| 8 个 logic 的 `ObserveRequest` 计时 | 各 Service 的 `try/finally` | ADAPT | 同 `MessageApiMetrics` 的时序（`MessageApiMetrics.java:37-47`） |
| 8 个 logic 的 `ObserveReject` | `FollowApiMetrics.observeReject` | ADAPT | 2 个 `reason` 字面量 |
| `metrics/metrics.go:13-30` 3 个指标 | `metrics/FollowApiMetrics` | ADAPT | 指标名与 tags 1:1 |
| `middleware/checkblacklistmiddleware.go` 全文 | `interceptor/FollowBlacklistInterceptor` | ADAPT | 🔴 **本域必须新建**，无任何 RAG 现成等价物（like 域刻意没实现） |
| `…:39` 黑名单 key | `BearerTokenExtractor.trimBearerPrefix` + 前缀常量 | REUSE | `BearerTokenExtractor.java:95-99` 逐字等价 Go 的 `TrimPrefix` |
| `…:33` 的 `TrimSpace` | 该方法结果上再 `.trim()` | ADAPT | ⚠️ `trimBearerPrefix` **不含** TrimSpace ⇒ follow 需多这一步 |
| `…:40-41` fail-open | `try { hasKey } catch { null }` | REUSE | `JwtAuthInterceptor.java:199-209` 逐行同款 |
| `…:42-48` 401 + JSON body | `FollowBlacklistInterceptor` 内**直接写 response** | ADAPT | 🔴 与 JWT 的 401 空 body **必须分两条路** |
| `…:52` 写 `jwt_token` | `request.setAttribute("jwt_token", token)` | ADAPT | 无消费者，但仍属可观察 |
| `followservice/followservice.go` 8 方法转发 | `FollowServiceGrpc.FollowServiceBlockingStub` | ABSORB | 生成物 |
| `RecommendResp.RecommendUser` 嵌套 message | `com.rag.follow.follow.proto.RecommendResp.RecommendUser`（**静态嵌套类**） | ABSORB | 已实测：`RecommendResp.java:63` 是 `public static final class RecommendUser`；`java_multiple_files=true` **不把嵌套 message 提升为顶层文件** |
| `common/errmsg`（api 侧所需子集） | `follow-common/errmsg/*` | REUSE | 见 `FileTree/follow/common.md` |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/follow/follow/api/
├── FollowApiApplication.java
├── advice/
│   ├── GlobalResponseAdvice.java
│   └── GlobalExceptionHandler.java
├── client/
│   └── FollowRpcClient.java
├── config/
│   ├── FollowApiProperties.java
│   ├── FollowGoStyleCoercion.java
│   └── WebMvcConfig.java
├── controller/
│   └── FollowController.java
├── dto/
│   ├── FollowListReq.java
│   ├── FollowUserListResp.java
│   ├── RelationActionReq.java
│   ├── RelationActionResp.java
│   ├── RecommendListResp.java
│   └── RecommendUser.java
├── interceptor/
│   ├── FollowJwtAuthInterceptor.java
│   └── FollowBlacklistInterceptor.java
├── metrics/
│   └── FollowApiMetrics.java
└── service/
    ├── FollowApiSupport.java
    ├── FollowService.java
    ├── UnfollowService.java
    ├── BlockService.java
    ├── UnblockService.java
    ├── GetFollowListService.java
    ├── GetFollowerListService.java
    ├── GetBlockListService.java
    └── GetRecommendationsService.java

resources/application.yml       # server.port=8891、follow-api.user-auth.*、spring.data.redis.*、
                                # grpc.client.follow-rpc、observability.http-timeout=3s、rag.protection.*
```

**31 个文件**（1 入口 + 2 advice + 1 client + 3 config + 1 controller + 6 dto + 2 interceptor + 1 metrics + 9 service）。
Go 侧 25 个文件 → Java 侧 31 个：**新增 6 个有明确职责依据的文件，压缩 10 个无独立边界的文件**。

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `FollowApiApplication` | 入口；`scanBasePackages={com.rag.follow.follow.api, com.rag.common}`；`@ConfigurationPropertiesScan`；`@EnableDiscoveryClient` | `followcenter.go` 进程装配 | 框架约定 |
| `advice/GlobalResponseAdvice` | `beforeBodyWrite` 包 `Response(200,"OK",body)`；**必须对 `body instanceof Response` 与 `null` 放行** | `followcenter.go:45-51` | 与 like / message 同款；二次包裹是已实证缺陷 |
| `advice/GlobalExceptionHandler` | 🔴 **两条独立的 401 路径**：`AuthenticationException` → 401 **空 body**；黑名单 401 在拦截器内直写（不经此处）。其余：`FollowCodeError` → 200 + 信封；`StatusRuntimeException` → 5001 + 200；`HttpMessageNotReadable` / `GoRequiredFieldException` / `IllegalArgumentException` → 5001 + 200；404 / 405 / 415 走 `GoStyleRouterErrorResponses` 或框架语义 | `followcenter.go:53-66` + 8 个 logic 的错误分支 | 同 like |
| `client/FollowRpcClient` | `@GrpcClient("follow-rpc")` 阻塞 stub；**统一出口套 `GoStyleRequestDeadline`** | `svc/servicecontext.go:27` | 外部服务客户端，`client/` 有先例 |
| `config/FollowApiProperties` | `user-auth.access-secret` / `access-expire` | `config/config.go:14-17` + `followcenter.yaml:10-12` | 配置独立关注点。⚠️ **不含** `BizRedis` / `FollowRpc`（前者是连接参数，后者是寻址，见 §12.4） |
| `config/FollowGoStyleCoercion` | 共享 `GoStyleCoercion` + **1 条补强**：`LogicalType.Integer` + `CoercionInputShape.String → Fail` | `types.go:8-9`（`offset` / `limit` 无 `,string`） | 🔴 **必须有**：`{"offset":"10"}` Go 拒 / Jackson 默认收。**只有 1 条补强**（like 有 2 条，因为 like 还有 float 字段） |
| `config/WebMvcConfig` | `addInterceptor(JWT).addInterceptor(Blacklist).addPathPatterns("/follow/v1/**")` + 注册 `JsonMapperBuilderCustomizer` | `routes.go:17-18,62-63` | 与 like 同款；⚠️ **注册顺序是语义** |
| `controller/FollowController` | 8 个 `@PostMapping`：`/follow` `/unfollow` `/block` `/unblock` `/follow/list` `/follower/list` `/block/list` `/recommendations`，类级 `@RequestMapping("/follow/v1")` | 8 个 handler | **8 个 Go handler 不拆 8 个 Controller**：逐字同质（`Parse → logic → 信封`），拆开只制造 8 个单方法类 |
| `dto/FollowListReq` | 3 字段全 `optional`；`userId` 用 `GoStyleStringInt64.parse` | `types.go:6-10` | `optional` 与 `,string` 语义独立于响应 |
| `dto/RelationActionReq` | 🔴 `targetId` **必填**（`GoStyleRequired`）+ `,string` | `types.go:25-27` | 必填性是与其它 DTO 的核心差异 |
| `dto/RelationActionResp` | `success` 恒 `true` | `types.go:29-31` | 响应契约独立 |
| `dto/FollowUserListResp` | `user_ids` 序列化为**数字数组** | `types.go:12-14` | 🔴 出参形态与 `RecommendListResp` 不同，不可合并 |
| `dto/RecommendListResp` | `users` 序列化为**数组**（空列表 ⇒ `[]` 而非 `null`） | `types.go:16-18` | 同上 |
| `dto/RecommendUser` | 🔴 `target_id` 序列化为 **JSON 字符串**；`mutual_score` 为数字 | `types.go:20-23` | 同上 |
| `interceptor/FollowJwtAuthInterceptor` | 401 **空 body**；验签后把 claim **原始对象**写进 `userId` attribute（**不转 long**） | `routes.go:62` 的 `rest.WithJwt` | **必须独立 Bean**：密钥 + public 集与其它域不同。⚠️ 存原始对象是因为 follow 的 `extractUserID` **接受 5 种类型**（含 `String` / `Double`）⇒ 若提前转 long，Go 判 5004 的输入会在鉴权层变成 401 |
| `interceptor/FollowBlacklistInterceptor` | Redis `hasKey` + **fail-open**；命中 ⇒ **401 + `application/json;charset=utf-8` + `{"code":5004,"msg":"unauthorized"}`**；未命中 ⇒ `setAttribute("jwt_token", …)` | `middleware/checkblacklistmiddleware.go` **全文** | 🔴 **本域必须新建**：like 域刻意未实现（`FileTree/like/api.md:351` 记为「Java 侧新增行为」），**follow 相反 —— Go 侧真实存在，是 Go 的行为不是新增** |
| `metrics/FollowApiMetrics` | `follow_api_request_total{route,result}` + `follow_api_reject_total{route,reason}` + `follow_api_request_duration_seconds{route}` | `metrics/metrics.go:13-30,41-53` | 独立变化原因（指标名 / tags 契约） |
| `service/FollowApiSupport` | `extractUserId`（**5 类型**）+ `rpcError` + `resolveUserId` + `userId(request)` | `logic/follow/helper.go` 全文 | 跨 8 个 Service 的重复片段（与 `LikeApiSupport` 同定位） |
| `service/FollowService` | uid → rpc.Follow → `RelationActionResp(true)` | `followlogic.go` | 单端点用例 |
| `service/UnfollowService` | 同上，调 `rpc.unfollow` | `unfollowlogic.go` | 单端点用例 |
| `service/BlockService` | 同上，调 `rpc.block` | `blocklogic.go` | 单端点用例 |
| `service/UnblockService` | 同上，调 `rpc.unblock` | `unblocklogic.go` | 单端点用例 |
| `service/GetFollowListService` | uid → `resolveUserId`（**无 scope 检查**）→ rpc → 直传 `userIds` | `getfollowlistlogic.go` | 单端点用例 |
| `service/GetFollowerListService` | 同上，调 `rpc.getFollowerList` | `getfollowerlistlogic.go` | 单端点用例 |
| `service/GetBlockListService` | uid → `resolveUserId` → **`forbidden_scope` 检查** → rpc → 直传 `userIds` | `getblocklistlogic.go` | 单端点用例；**与上面 2 个的鉴权不对称是显式行为** |
| `service/GetRecommendationsService` | uid → `resolveUserId` → **`forbidden_scope` 检查** → rpc → 嵌套投影 | `getrecommendationslogic.go` | 单端点用例 |

### 为什么是 8 个 Service 而不是按 3 种形状合并成 3 个

**这是本文件最需要论证的一处组织决策**（§7.2 的合并判定规则）。两个候选：

| 方案 | 文件数 | 评估 |
|---|---|---|
| **(A) 按 3 种形状合并**：1 个 `FollowActionService`（4 方法）+ 1 个 `FollowRelationListService`（2 方法）+ 1 个 `FollowPrivateListService`（2 方法） | 3 | 符合 §7.2 的「同一变化原因」直觉；但**与 RAG 已落地的 19 个 api service 全部是单端点**这一事实不符 |
| **(B) 每端点 1 个 Service**（本文件采纳） | 8 | 与 `message-api`（13 个，`MessageSendUserChatService` / `MessageGetMessagesService` / …）+ `like-api`（6 个，`LikeCountService` / `LikeStateService` / …）**逐条一致** |

**采纳 (B) 的三条依据**（都不是「文件数对齐」）：
1. **独立变化原因**：8 个端点各有**独立的 Prometheus `route` 标签**（§12.3 表），指标契约改动会逐端点发生。
2. **独立接口契约**：8 个端点的请求 DTO 只有 2 种、响应 DTO 有 3 种，且形状 B / C 的鉴权规则**不同**。
   合并会让「哪些端点允许跨用户查询」这条规则变得不可定位 —— 这正是 §7.2 第 6 条要避免的。
3. **目标项目实际组织方式**：RAG 全仓 19 个已落地 api service **零例外**都是单端点（实测两域目录）。

⚠️ **但「形状 B 无 scope 检查」这个不对称必须以显式方式保留**，否则 8 个平铺的 Service 会让人误以为它们同构。
⇒ 做法：在 `GetFollowListService` / `GetFollowerListService` 的**类 javadoc** 里逐条引用 Go 的 `:45` 行号 + 说明「此处**故意**没有 scope 检查」，
在 `GetBlockListService` / `GetRecommendationsService` 的 javadoc 里引用 `:46-50`。**由文档而非由类型来承载这条不对称。**

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `followcenter.go` | `FollowApiApplication` | ABSORB | 进程装配由框架承担 |
| `followcenter.go:33` `InitMetrics()` | Micrometer 声明即注册 | ABSORB | 🔴 **follow 真的调了它** ⇒ 自动注册是对齐 |
| `followcenter.go:36-38` conf/log/logger | `@ConfigurationPropertiesScan` + `LoggerFactoryBean` | ABSORB | 框架承担 |
| `followcenter.go:40` 禁原生超时 | — | — | Spring MVC 无等价默认超时，无代码可落 |
| `followcenter.go:42` HTTP span | `common/spring/HttpMiddlewareFilter` | REUSE | `@Component` 扫描即注册 |
| `followcenter.go:45-51` SetOkHandler | `advice/GlobalResponseAdvice` | ADAPT | Spring advice 等价 |
| `followcenter.go:53-66` SetErrorHandler | `advice/GlobalExceptionHandler` | ADAPT | 异常处理机制换 Spring；HTTP 恒 200 |
| `etc/followcenter.yaml:3` Port 8891 | `application.yml` `server.port` | ADAPT | 配置替代 |
| `etc/followcenter.yaml:10-12` UserAuth | `config/FollowApiProperties.UserAuth` | ADAPT | 同上 |
| `etc/followcenter.yaml:14-17` BizRedis | `application.yml` `spring.data.redis.*` | ADAPT | 纯连接参数，无业务字段 |
| `etc/followcenter.yaml:19-23` Telemetry | `otel.*` in `application.yml` | ADAPT | `Telemetry.Name: follow-api` → `otel.service.name` |
| `etc/followcenter.yaml:25-32` DevServer 16064 | — | — | Go `Config` 无对应字段 ⇒ 无 Spring 对应物（**UNKNOWN**，见 §12.7） |
| `etc/followcenter.yaml:5-8` Log | `logging.*` in `application.yml` | ADAPT | `Path` 目录语义由日志配置承担 |
| `etc/followcenter.yaml:34-38` FollowRpc.Etcd | `grpc.client.follow-rpc.address` | ADAPT | etcd key `follow.rpc` → Nacos service name `follow-rpc` |
| `internal/config/config.go:12-20` | `config/FollowApiProperties` | ADAPT | 只迁有消费者的段 |
| `svc/servicecontext.go:23` Redis | `StringRedisTemplate` | REUSE | 已有先例（`JwtAuthInterceptor.java:73`） |
| `svc/servicecontext.go:27` FollowRpc | `client/FollowRpcClient` | ADAPT | `zrpc.MustNewClient` → `@GrpcClient` |
| `svc/servicecontext.go:28` 黑名单中间件 | `interceptor/FollowBlacklistInterceptor` | ADAPT | Spring 容器承担构造 |
| `routes.go:62` `WithJwt` | `interceptor/FollowJwtAuthInterceptor`（第 1 条） | ADAPT | 独立 Bean |
| `routes.go:17-18` `WithMiddlewares` | `interceptor/FollowBlacklistInterceptor`（第 2 条） | ADAPT | 🔴 本域特色；注册顺序即执行顺序 |
| `routes.go:63` `WithPrefix` | `@RequestMapping("/follow/v1")` | ADAPT | 契约本就一致 |
| `routes.go` 无 public 路径 | 不加 `excludePathPatterns` | ABSORB | 8/8 全量鉴权 |
| `routes.go` 无 `WithTimeout` / 无 CORS | 不配 | ABSORB | Go 无此装饰器 |
| `handler/follow/*.go`（8 个） | `controller/FollowController`（单一） | MERGE | 逐字同质的薄适配层 |
| `types.go:6-10` `FollowListReq` | `dto/FollowListReq` | SPLIT | `optional` × 3 + `,string` × 1 |
| `types.go:25-27` `RelationActionReq` | `dto/RelationActionReq` | SPLIT | 必填 + `,string` |
| `types.go:29-31` `RelationActionResp` | `dto/RelationActionResp` | SPLIT | `success` 恒 true |
| `types.go:12-14` `FollowUserListResp` | `dto/FollowUserListResp` | SPLIT | 🔴 出参为数字数组 |
| `types.go:16-18` `RecommendListResp` | `dto/RecommendListResp` | SPLIT | 空列表 ⇒ `[]` |
| `types.go:20-23` `RecommendUser` | `dto/RecommendUser` | SPLIT | 🔴 出参 `target_id` 为字符串 |
| `types.go:8-9` `offset`/`limit` 形态 | `config/FollowGoStyleCoercion` | NEW | 域内 Jackson 3 补强（`GoStyleCoercion` 无此条） |
| `logic/follow/helper.go:13-33` `extractUserID` | `service/FollowApiSupport.extractUserId` | ADAPT | 🔴 **5 类型**，与 like 实现不可共用 |
| `logic/follow/helper.go:35-37` `userLogOption` | `common/logger/WithUserID` | REUSE | 已有类 |
| `logic/follow/helper.go:39-41` `codeFromRPCError` | `service/FollowApiSupport.rpcError` | ADAPT | ⚠️ **msg 查表**，与 like 相反 |
| `logic/follow/helper.go:43-48` `resolveUserID` | `service/FollowApiSupport.resolveUserId` | ADAPT | `> 0 ? requested : current` |
| `logic/followlogic.go` | `service/FollowService` | MERGE | 与其余 7 个同款单端点组织 |
| `logic/unfollowlogic.go` | `service/UnfollowService` | MERGE | 同上 |
| `logic/blocklogic.go` | `service/BlockService` | MERGE | 同上 |
| `logic/unblocklogic.go` | `service/UnblockService` | MERGE | 同上 |
| `logic/getfollowlistlogic.go` | `service/GetFollowListService` | MERGE | 同上 |
| `logic/getfollowerlistlogic.go` | `service/GetFollowerListService` | MERGE | 同上 |
| `logic/getblocklistlogic.go` | `service/GetBlockListService` | MERGE | 同上 |
| `logic/getrecommendationslogic.go` | `service/GetRecommendationsService` | MERGE | 同上 |
| 4 个 action logic 的 `Success:true` | 各 Service 的 `new RelationActionResp(true)` | ADAPT | 硬编码语义 |
| `getrecommendationslogic.go:70-79` 嵌套投影 | `GetRecommendationsService` 内联 | MERGE | 与主流程同生命周期 |
| `getrecommendationslogic.go:72-73` 跳过 nil 元素 | — | — | Java protobuf 的 `getUsersList()` **不产生 null 元素**，该分支不可达（见 §12.7 #5） |
| `metrics/metrics.go:13-30` 3 个指标 | `metrics/FollowApiMetrics` | ADAPT | 指标名与 tags 1:1 |
| `metrics/metrics.go:41-53` 2 个 Observe | `FollowApiMetrics.observeRequest/observeReject` | ADAPT | `result` 判据 = err 是否非 nil |
| `metrics/metrics.go:33-39` `InitMetrics` | Micrometer 自动注册 | ABSORB | Go 侧**确实调用**了它（`followcenter.go:33`） |
| `middleware/checkblacklistmiddleware.go:27-37` | 不迁移（两个 early-pass 分支） | — | 🔴 **不可达**：`WithJwt` 在外层先执行（依据 `JwtAuthInterceptor.java:170-174`） |
| `middleware/checkblacklistmiddleware.go:33` token 取法 | `BearerTokenExtractor.trimBearerPrefix(...).trim()` | REUSE + ADAPT | 前缀剥离 REUSE；`TrimSpace` 是 follow 独有，需补 |
| `middleware/checkblacklistmiddleware.go:39` key | `"user:jwt_blacklist:" + token` | ADAPT | 前缀跨域共享，不是 `follow:` |
| `middleware/checkblacklistmiddleware.go:40-41` fail-open | `hasKey` + catch 吞异常 | REUSE | `JwtAuthInterceptor.java:199-209` 逐行同款 |
| `middleware/checkblacklistmiddleware.go:42-48` 401 + JSON | `FollowBlacklistInterceptor` 直写 response | ADAPT | 🔴 **与 JWT 的 401 空 body 分两条路** |
| `middleware/checkblacklistmiddleware.go:52` 写 `jwt_token` | `setAttribute("jwt_token", token)` | ADAPT | 无消费者但可观察 |
| `followservice/followservice.go` 8 方法 | `FollowServiceGrpc.FollowServiceBlockingStub` | ABSORB | 生成物 |
| `common/errmsg`（api 所需子集） | `follow-common/errmsg/*` | REUSE | 见 `FileTree/follow/common.md` |
| —— | `com.rag.common.exception.AuthenticationException` | REUSE | 401 空 body 的异常载体 |
| —— | `com.rag.common.http.GoStyleRouterErrorResponses` | REUSE | 404 / 405 裸响应 |
| —— | `com.rag.common.observability.GoStyleRequestDeadline` | REUSE | gRPC deadline 统一出口 |
| —— | `users-common/http/GoStyleStringInt64` / `GoStyleRequired` / `GoStyleCoercion` | REUSE | 3 个 go-zero 解析原语 |
| —— | `users-common/jwt/{JwtUtil,BearerTokenExtractor}` | REUSE | 2 个鉴权原语 |
| —— | `pom.xml`（现为 19 行空壳） | ADAPT | 至少需补 5 项依赖，见 §12.7 #1 |
| —— | 必配测试：`FollowApiMvcPipelineTest`（真实 MVC 管线，断言 8 条路由 + 两种 401 body 形态）、`FollowApiBlacklistInterceptorTest`（真 Redis 或 Testcontainers，断言 fail-open 与 key 拼法）、`FollowDtoSerializationTest`（断言 `target_id` 为字符串、`user_ids` 为数字数组、空列表为 `[]`）、`FollowMetricsLabelsTest`（断言 8 个 `route` 字面量逐条一致） | NEW | AGENTS.md §3.3：纯 Mockito 单测不算 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 8 条 `POST /follow/v1/*`（路径逐条）；8/8 全量 `WithJwt`、**空 public 集**；HTTP 状态码恒 200；成功信封 `{code:200,msg:"OK",data:...}`；**两种 401 body 形态**；`relation_action` 4 端点的 `success` 恒 true；`FollowListReq` 3 字段全 optional、`RelationActionReq.target_id` 必填；3 个 `,string` 字段；**出参 `user_ids` 数字数组 vs `target_id` 字符串**；`success` 恒 true |
| L2 业务语义有承载位置 | ✅ `extractUserID` 的 **5 种类型分支**；`resolveUserID` 的 `>0` 判定；**形状 B 无 scope 检查 vs 形状 C 有 `forbidden_scope`** 的鉴权不对称；`rpcResp.Code != 200` 与 `rpcResp == nil` 两条独立分支；`users` 空列表 ⇒ `[]`；黑名单 fail-open；黑名单 key 前缀跨域共享 |
| L3 技术语义有承载方案 | ✅ `DisableNativeRestTimeout` 无对应物已声明；HTTP span 复用全局 Filter；`Response` 单层包裹；401 两条路径分离；Redis 故障不阻塞业务；gRPC deadline 由 `GoStyleRequestDeadline` 统一挂 |
| 业务分支与异常路径已纳入 | ✅ `httpx.Parse` 失败 → 5001 + 200；uid 三态失败（nil / 类型不符 / ParseInt 失败）；`rpcErr` / `rpcResp==nil` / `code!=200` 三分支；`forbidden_scope`；黑名单命中；黑名单 Redis 故障；JWT 三种验签失败 |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；CORS 未配置 → 不配置；无超时装饰器 → 不加；无 multipart → 不配 limit；`followservice` 转发层 → 生成的 stub |
| 复用组件已明确 | ✅ `Response`、`AuthenticationException`、`GoStyleRouterErrorResponses`、`GoStyleRequestDeadline`、`HttpMiddlewareFilter`、`GoStyleProtectionConfiguration`、`StringRedisTemplate`、`BearerTokenExtractor.{tokenForSignatureVerification,trimBearerPrefix}`、`JwtUtil.padToHmacKeySize`、`GoStyleStringInt64`、`GoStyleRequired`、`GoStyleCoercion`、`WithUserID`、`LoggerFactoryBean` |
| 新增文件有职责依据 | ✅ `FollowBlacklistInterceptor`（Go 真实存在的路由级中间件，RAG 零等价物）、`FollowGoStyleCoercion`（`offset`/`limit` 的 Go-vs-Jackson 形态差）、`FollowApiMetrics`（3 个 Go 指标 + 8 个 route 字面量）、`FollowApiSupport`（跨 8 service 的 5 类型身份解析模板）—— 均非「Go 有同名文件」 |
| 合并有组织依据 | ✅ 8 handler → 1 Controller（逐字同质）；4 helper 函数 → 1 Support（跨 8 service 的重复片段）；未把形状 B/C 合并（鉴权不对称） |
| 拆分可解释 | ✅ 请求 / 响应 DTO 分离（校验规则与出参形态不同）；JWT 与黑名单两个拦截器分离（**401 body 形态不同**）；8 个单端点 Service 分离（依据见 §12.5 三条） |
| 无「为对应 Go 文件而建」的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `Types.java` / `ServiceContext.java` / `Middleware.java`；黑名单中间件落在 `interceptor/` 且命名为 `FollowBlacklistInterceptor`（不是 `CheckBlacklistMiddleware.java`） |
| 未把未实现能力说成现有机制 | ✅ `follow-api` 空壳 + 空依赖已在 §12.1 声明；§12.5 全部为推导建议 |
| 未把 Go 缺陷误当需求照抄而不标注 | ✅ `TrimPrefix` 大小写敏感（可绕过黑名单）、`TrimSpace` 独有、两个 early-pass 分支不可达、`jwt_token` 无消费者 —— 均已显式标注 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | 🔴 **`follow-api/pom.xml` 零依赖** | 缺 `follow-proto`、`spring-boot-starter-web`、`common`、`grpc-client-spring-boot-starter`、**`spring-boot-starter-data-redis`**、`users-common`（`GoStyleStringInt64` / `BearerTokenExtractor`）、`follow-common`、`lombok`、测试依赖。**其中 Redis 是 follow 相对 like 的增量需求**（`servicecontext.go:23`），漏掉它则黑名单静默失效 ⇒ 表现为「被拉黑的 token 仍可用」 |
| 2 | 🔴 **401 有两种形态，Spring 的异常处理链只能自然产出一种** | `AuthenticationException` → 401 空 body（已由 like 锁死）。若黑名单的 401 也抛 `AuthenticationException`，就会变成**空 body** ⇒ 丢掉了 `{"code":5004,"msg":"unauthorized"}` 与 `Content-Type`。⇒ 必须让 `FollowBlacklistInterceptor` **直接写 response 并 `return false`**，不得走异常链。必须有一条**真实 MVC 管线**的测试同时断言两种形态（否则这类回归不会被发现） |
| 3 | 🔴 **`offset` / `limit` 的 Jackson 强转** | `{"offset":"10"}` / `{"limit":"abc"}`：Go 拒（type mismatch ⇒ 5001 + 200），Jackson 3 默认 `TryConvert` 会**放行合法数字串**。方向是「Go 拒、RAG 收」，**多放行不会报错**，最难发现。⇒ `FollowGoStyleCoercion` 补强 1 不可省 |
| 4 | ⚠️ **黑名单两个 early-pass 分支是否要实现** | `:28-31` / `:34-37` 在 follow **不可达**（`WithJwt` 在外层）。本文件选择**不实现**（省 4 行）。若日后有人调整拦截器注册顺序让黑名单跑到最外层，这两个分支就会变成可达 ⇒ **建议在类 javadoc 里写明「本分支缺失依赖『JWT 在外层』这一前提」**，而不是留空 |
| 5 | ⚠️ **`getrecommendationslogic.go:72-73` 的 `if user == nil { continue }` 在 Java 侧不可达** | Java protobuf 的 `getUsersList()` 返回 `ProtocolStringList`/`List<RecommendUser>`，**永不产出 null 元素**。⇒ 该防御分支无对应物。这**不是**遗漏，是语言差异；需在 javadoc 注明「Go 的 nil 元素防御在 Java protobuf 下不存在」 |
| 6 | ⚠️ **`DevServer` 段在 go-zero `conf.MustLoad` 下的行为** | `followcenter.yaml:25-32` 有 5 个字段，`config.go:12-20` 无对应。未知键是报错还是忽略取决于 go-zero 库实现（不在本仓库）⇒ **UNKNOWN**。RAG 侧后果：16064 的 `/metrics` + `/healthz` 无对应物，只能由 Actuator 覆盖 |
| 7 | ⚠️ **Go `RestConf` 的 9 个中间件开关在 yaml 里一个字都没写** | `rest/config.go:12-22` 全部 `json:",default=true"` ⇒ **是开的**。RAG 侧由 `GoStyleProtectionConfiguration` 扫到即生效，但 like 的教训（DRIFT-LIKEAPI-001：`RestConf.Timeout` 的 3000ms 被漏配）说明**必须显式写出**，不能依赖默认值 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `DevServer` 段在 `conf.MustLoad` 下是报错还是忽略 | 取决于 go-zero 库实现（不在本仓库）；follow 是**四个新域中唯一**在 yaml 有 DevServer 而 `Config` 无对应字段的 |
| `Log.Path: ../../../../log/follow-api` 的语义 | 相对路径基准（相对于 CWD 还是配置文件）取决于 go-zero 的 `logx.MustSetup` 实现；RAG 侧的 `logging.*` 配置如何对应，**需实施时确认** |
| `BizRedis.Type: node` 的含义 | go-zero `redis.RedisConf.Type` 取 `node`/`cluster`，`followcenter.yaml:16` 写的是 `node`（单机）。RAG 侧 Lettuce 默认单机，语义等价；但若日后改成 `cluster`，`redis.MustNewRedis` 与 Lettuce 的集群客户端行为**不完全等价**（含 `Pass` 的空串处理），未验证 |
| `float64` claim 的精确转换语义 | `helper.go:28-29` 的 `case float64: return int64(value)` 是**直接截断**。Java protobuf/JJWT 给出的浮点 claim 形态（如 `1.9`）会走这条路径吗？jjwt 对 JSON 数字的解码类型**未实测** ⇒ 若真出现 `1.9`，Go 给 `1`、Java 给什么**需探针确认**。⚠️ 潜在越权面：截断后的 uid 可能指向另一个真实用户 |
| `AccessExpire: 31536000` 在 api 侧是否被读 | Go 侧 `config.go:16` 声明了它，但 8 个 logic 与黑名单中间件**都未读取**（实测无 `AccessExpire` 引用）⇒ 与 like 一致，保留字段仅为 yaml 1:1 对照 |
| `follow-rpc` 侧 `ErrorFollowNotFound`(5203) / `ErrorBlockNotFound`(5204) 是否会被启用 | 见 `FileTree/follow/common.md` UNKNOWN 清单；同一问题在 api 侧无影响（api 不直接构造这两个码） |