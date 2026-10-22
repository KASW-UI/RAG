# user-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/user/user/api`
> 目标根包：`com.rag.users.user.api`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

`user-api` 在 RAG 中**已有完整 Java 实现**（非空骨架），实际包结构如下：

| 包 | 实际文件 | 职责 |
|---|---|---|
| （根） | `UserApiApplication` | `@SpringBootApplication` 入口 |
| `advice/` | `GlobalResponseAdvice`、`GlobalExceptionHandler` | `@RestControllerAdvice` 信封包装 + `@ExceptionHandler` 业务码映射 |
| `client/` | `UserRpcClient` | `@GrpcClient("user-rpc")` 阻塞 stub |
| `config/` | `UserApiProperties`、`WebMvcConfig` | `@ConfigurationProperties` 绑定 + 拦截器注册 |
| `controller/` | `UserController` | `@RequestMapping("/user/v1")` 9 个 `@PostMapping` 端点 |
| `interceptor/` | `JwtAuthInterceptor` | `HandlerInterceptor`：JWT 验签 + Redis blacklist（`@Component`） |
| `metrics/` | `UserApiMetrics` | `@Component` Micrometer 7 Counter（仅 login/register） |
| `service/` | `UserApiSupport`（helper 聚合）+ `IdentityResolver`（5 sentinel）+ 9 个用例 `*Service` | 9 用例 + 共用 helper + 身份契约 |
| `dto/` | 21 个 DTO 类（UserInfo / AvatarHistoryItem + 9 对 Req/Resp + 嵌套项） | HTTP wire 契约 |

### 已有组件（跨模块可复用）

| 组件 | 位置 | 承担 |
|---|---|---|
| `com.rag.common.response.Response` | `service/common` | HTTP 统一响应信封 |
| `com.rag.common.exception.BusinessException` | `service/common` | 业务异常基类 |
| `com.rag.common.observability.HttpMiddlewareFilter` | `service/common` | HTTP span Filter（已 `@Component` 全局注册） |
| `com.rag.users.common.jwt.JwtUtil` + `padToHmacKeySize` | `users-common` | JWT 签发 |
| `com.rag.users.common.errmsg.ErrorCode` | `users-common` | 业务错误码枚举 |
| `com.rag.users.user.proto.*` (生成物) | `service/users/user/user-proto` | gRPC proto client stub（`@GrpcClient("user-rpc")` 自动注入） |
| **跨模块引用**：`com.rag.users.admin.proto.*` | `service/users/admin/admin-proto` | **未引用** —— user-api 不调 admin-rpc |

### 已确认的框架机制

- `spring-boot-starter-web`：`@RestController` / `@RequestMapping` / `@PostMapping`
- `org.springframework.web.servlet.HandlerInterceptor` + `WebMvcConfigurer` 拦截器注册
- `@RestControllerAdvice` + `ResponseBodyAdvice<Object>`（**注意 §3.3 idempotency**：判 `body instanceof Response` 才不二次包裹）
- `@GrpcClient("user-rpc")` 自动注入 BlockingStub（devh grpc-client-spring-boot-starter，Nacos discovery）
- `StringRedisTemplate` 做 JWT blacklist
- `spring-boot-starter-actuator` / Micrometer
- `org.springframework.cloud.discovery.EnableDiscoveryClient` + Nacos

### 当前 Component 可以直接复用的能力

- **JWT 签发**：复用 `JwtUtil.getToken(...)`（user-api 唯一签发方，user-rpc 端不签发）
- **HTTP 信封 + 异常映射**：`common/response/Response` + `advice/GlobalResponseAdvice` + `advice/GlobalExceptionHandler` 三件套
- **JWT 拦截器模式**：与 admin-api 同款（`JwtAuthInterceptor`），但密钥/路径不同 → **必须独立 Bean**
- **gRPC 客户端**：通过 `@GrpcClient` 自动注入
- **身份契约**：`service/user/identity/resolve.go` 的 5 sentinel exception 已映射为 Java 端 `IdentityResolver` 5 个 nested static class

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| HTTP path | Go 为 `/usercenter/v1/user/*`；RAG 已偏离为 `/user/v1/*`（AGENTS.md §3.4 留档） |
| HTTP method | Go 为 POST/GET/DELETE 混合（login=POST/getuser=GET/delete=DELETE）；RAG 统一 POST（AGENTS.md §3.4 留档） |
| `user-api` 是否应继续持有 `IdentityResolver` | RAG 保留 admin-api 的 `IdentityResolver`（3 sentinel）与 user-api 的 `IdentityResolver`（5 sentinel）**分开** —— admin 域无 status 字段（AGENTS.md §6.1 已确认） |
| 5 sentinel 的 proto3 status presence 语义 | `UserInfo.status` 是 `optional int64`（proto3 presence），user-rpc 的 `UserDomainService.getUser` 必须**显式 setStatus**（不能让它走 proto3 默认 0 路径），否则 api 侧 `IdentityResolver.resolveUser` 抛 `ErrStatusUnavailable` —— 这是 user-api **独有的 L1 协议约束** |

---

## 12.2 第二部分：Go 原始文件树

```
service/user/user/api/
├── usercenter.go                                       # main 入口：flag、config、metrics、svc、handler.RegisterHandlers
├── identity_contract.md                                # L1 协议：AuthorityID/PlatformTenantID/5 sentinel 约束
├── internal/
│   ├── config/config.go                                # Config：RestConf + UserAuth + UserRpc + BizRedis
│   ├── svc/servicecontext.go                           # ServiceContext：Config + UserRpc + CheckBlacklistMiddleware
│   ├── handler/
│   │   ├── routes.go                                   # 2 个 AddRoutes 块（public 2 + JWT 7）
│   │   └── user/                                       # 9 个 handler
│   │       ├── deleteuserhandler.go
│   │       ├── getavatarhistoryhandler.go
│   │       ├── getuserhandler.go
│   │       ├── loginhandler.go
│   │       ├── logouthandler.go
│   │       ├── registerhandler.go
│   │       ├── selectavatarhandler.go
│   │       ├── updateuserhandler.go
│   │       └── uploadavatarhandler.go
│   ├── identity/resolve.go                             # api 侧 identity resolver（5 sentinel），转发到 user/user/identity/resolve.go
│   ├── logic/user/                                     # 9 个 logic
│   │   ├── avatarhelpers.go                            # normalizeAvatarUrl / normalizeContentType / normalizeHistoryId + avatarHistoryItemFromPB
│   │   ├── deleteuserlogic.go
│   │   ├── getavatarhistorylogic.go
│   │   ├── getuserlogic.go                             # 调 identity.ResolveUser（5 sentinel）
│   │   ├── loginlogic.go
│   │   ├── logoutlogic.go
│   │   ├── registerlogic.go
│   │   ├── selectavatarlogic.go
│   │   ├── updateuserlogic.go
│   │   └── uploadavatarlogic.go
│   ├── metrics/metrics.go                              # UserApiInterceptCount CounterVec
│   ├── middleware/checkblacklistmiddleware.go          # user:jwt_blacklist: 前缀
│   └── types/types.go                                  # 9 个 Req/Resp + UserInfo + AvatarHistoryItem + CreateUserReq
└── etc/usercenter.yaml
```

### 跨模块引用

```
service/user/user/identity/resolve.go                    # L1 权威 identity resolver，api 侧 identity/resolve.go 转发到本文件
```

---

## 12.3 第三部分：Go 文件职责分析

### `usercenter.go`
- **主要职责**：进程入口。`metrics.InitMetrics()`、flag、conf.MustLoad、logx.MustSetup、logger.Init、`observability.NewHTTPMiddleware`、`rest.MustNewServer`、`handler.RegisterHandlers`。
- **业务语义**：无（纯装配）。
- **可替换**：Spring `@SpringBootApplication` + `@EnableDiscoveryClient` + `HttpMiddlewareFilter`（已注册）。

### `identity_contract.md`
- **主要职责**：L1 协议文档冻结
  - `AuthorityID = "rtw.identity"`（issuer 串）
  - `PlatformTenantID = "platform"`（v1 唯一用户空间固定命名空间）
  - `SubjectRef.subject_id = UID 十进制字符串`
  - 5 sentinel exception：`ErrInvalidClaim` / `ErrUserNotFound` / `ErrIdentityMismatch` / `ErrUserInactive` / `ErrStatusUnavailable`
  - proto3 presence：status=0 且字段存在才表示活跃
- **承载位置**：RAG `IdentityResolver.java` 的 L1 冻结常量 + 5 sentinel nested static class + `resolveUser` 完整复刻。

### `internal/config/config.go`
- **主要职责**：`Config{rest.RestConf, UserAuth{AccessSecret, AccessExpire}, UserRpc zrpc.RpcClientConf, BizRedis redis.RedisConf}`。
- **L1 外部契约冻结**：`AccessSecret = 36 字节 UUID`（≥32 字节，无需 padding 但统一走 `padToHmacKeySize` 收口）；`AccessExpire = 2592000`（30 天）。
- **可替换**：Spring `@ConfigurationProperties(prefix="user-api")` 1:1 字段对应。

### `internal/svc/servicecontext.go`
- **主要职责**：依赖持有（`Config` / `UserRpc userservice.UserService` / `CheckBlacklistMiddleware rest.Middleware`）。
- **可替换**：Spring DI + `@GrpcClient("user-rpc")`。

### `internal/handler/routes.go`
- **主要职责**：2 个 `AddRoutes` 块：
  - **块 1**（public）：`POST /user/login` + `POST /user/register`，无中间件
  - **块 2**（JWT + blacklist）：7 个路由 + `WithJwt(AccessSecret)`
- **L1 路由清单**（9 个端点）：
  | path | method | 中间件 |
  |---|---|---|
  | `/user/login` | POST | — |
  | `/user/register` | POST | — |
  | `/user/delete` | DELETE | JWT + blacklist |
  | `/user/get` | GET | JWT + blacklist |
  | `/user/avatar/history` | GET | JWT + blacklist |
  | `/user/logout` | POST | JWT + blacklist |
  | `/user/avatar` | POST | JWT + blacklist |
  | `/user/avatar/select` | POST | JWT + blacklist |
  | `/user/update` | POST | JWT + blacklist |
- **已记录偏离（AGENTS.md §3.4）**：前缀 `/usercenter/v1` → `/user/v1`；method 混合 → 统一 POST。
- **可替换**：RAG `UserController` 9 个 `@PostMapping` + `JwtAuthInterceptor`（合并 JWT + blacklist）。

### `internal/handler/user/*handler.go`（9 个）
- **统一模式**：`httpx.Parse(r, &req)` → `logic.NewXxxLogic(ctx, svcCtx).Xxx(&req)` → `httpx.OkJson(w, &response.Response{...})`。
- **L2 关键**：handler 层不做业务逻辑。
- **可替换**：RAG `UserController`（9 个 `@PostMapping`）+ 委托给 9 个 `*Service`。

### `internal/logic/user/loginlogic.go`
- **主要职责**：6 状态机
  1. `UserRpc.Login` 失败：`codes.Internal` → 5001；其它 → 1015；**`login_rpc_error` metric**
  2. `rpcResp.Status == 1`（密码错或用户名不存在）→ 1002 `ErrorLoginWrong` + `login_wrong_password` metric
  3. `rpcResp.Status == 2`（用户封禁）→ 1011 `ErrorUserBanned` + `login_user_banned` metric
  4. `jwt.GetToken` 失败 → 1006 `ErrorTokenRuntime` + `login_token_error` metric
  5. 成功 → `login_success` metric + 返 `LoginResp{Token}`
- **L2 关键**：Go 端 `rpcResp.Status == 1/2` 字段语义见 `user.proto:42` —— proto3 `uint64 status` 是三态码（0=success / 1=login failure / 2=banned）。
- **可替换**：RAG `UserLoginService.java:74-133` —— BusinessException 单异常路径，按 gRPC code + rpcResp.Status 双重判别（**注意**：user.proto 的 `LoginResp.status` 是 status 字段，与 admin.proto 不同；RAG 端必须读 `rpcResp.getStatus()`）。
- **重要细节**：rpcResp.Status=1 时 RAG 端**判读字段**（不是 gRPC code）→ 抛 1002；rpcResp.Status=2 → 抛 1011。这是 user 域独有的语义。

### `internal/logic/user/registerlogic.go`
- **主要职责**：3 状态机 + 2 metric
  1. `UserRpc.Register` 失败：`codes.Internal` → 5001；`codes.AlreadyExists` → 1001；其它 → 1015 + `register_rpc_error` metric
  2. 成功 → `register_success` metric + `CreateUserResp{Uid}`
- **L2 关键**：proto 方法名是 `Register`（不是 `CreateUser`），因为 protoc 对 `rpc Register(CreateUserReq)` 取 service 名 → Java 端生成 `register` 方法。
- **可替换**：RAG `UserRegisterService.java:47-70` 1:1 跟随（含 proto 方法名 `register`）。

### `internal/logic/user/logoutlogic.go`
- **主要职责**：3 状态机
  1. 从 ctx 取 `jwt_token` → 1004 `ErrorTokenNotExist`
  2. `UserRpc.Logout` 失败 → `codes.Internal` → 5001；其它 → 1015
  3. 成功 → `LogoutResp{Success: true}`
- **L2 关键**：**无 metric**（Go 端 logout 不埋点）—— RAG 1:1 不补。
- **可替换**：RAG `UserLogoutService.java:47-68` 1:1。

### `internal/logic/user/getuserlogic.go`
- **主要职责**：6 状态机
  1. `identity.ResolveUser(ctx, UserRpc)` → 5 sentinel exception 映射到 5 个业务码
  2. gRPC 异常 fallback：`codes.NotFound` → 1003；`codes.Internal` → 5001；其它 → 1015
  3. 成功 → `GetUserResp{User: UserInfo{uid, username, email, extra_info}, Found: true}`（**只填 4 字段，不填 score**）
- **L2 关键**：**这是唯一走 IdentityResolver 的 api 端点** —— 其它 8 个端点都用 `IdentityResolver.claimedUID` 取 uid，不做 status 校验。getUser 验证"已认证 UID 仍然活跃"。
- **可替换**：RAG `UserGetService.java:51-90` 1:1 跟随（**注意**：RAG 端 `UserInfo.setUid/setUsername/setEmail/setExtraInfo` 4 字段，不 setScore，与 Go 端 UserInfo 同款透传语义）。

### `internal/logic/user/updateuserlogic.go`
- **主要职责**：4 状态机
  1. `currentUserID(ctx)` (即 `identity.ClaimedUID`) → 失败 → 1006
  2. `UserRpc.UpdateUser` 失败：`codes.Internal` → 5001；`codes.AlreadyExists` → 1001；`codes.NotFound` → 1003；其它 → 1015
  3. 成功 → `UpdateUserResp{User: UserInfo{uid, username, email, extra_info}}`（**只填 4 字段**）
- **L2 关键**：**无 metric**（Go 端 updateuser 不埋点）—— RAG 1:1 不补。
- **可替换**：RAG `UserUpdateService.java:48-81` 1:1。

### `internal/logic/user/deleteuserlogic.go`
- **主要职责**：4 状态机
  1. `currentUserID(ctx)` → 失败 → 1006
  2. `UserRpc.DeleteUser(Uid)` → `codes.NotFound` → 1003；`codes.Internal` → 5001；其它 → 1015
  3. `rpcResp.Success == false`（**无 gRPC error 但 success=false**）→ 1015
  4. 成功 → `DeleteUserResp{Success: true}`
- **L2 关键**：与 admin-api delete 不同，user-api delete **不调用 admin-rpc**，直接 user-rpc（user 自己删自己）。**也无 metric**。
- **可替换**：RAG `UserDeleteService.java:47-75` 1:1（含 `!rpcResp.getSuccess()` 兜底）。

### `internal/logic/user/selectavatarlogic.go`
- **主要职责**：5 状态机
  1. `currentUserID(ctx)` → 失败 → 1006
  2. `strings.TrimSpace(req.HistoryId) == ""` → 500 `Error`（**注意**：500 不是 5001）
  3. `UserRpc.SelectAvatar(Uid, HistoryId)` → `codes.InvalidArgument` → 500；`codes.NotFound` → 500；`codes.Internal` → 5001；其它 → 1015
  4. 成功 → `SelectAvatarResp{AvatarUrl, History: avatarHistoryItemFromPB(rpcResp.History)}`
- **L2 关键**：
  - `codes.NotFound` 映射**不是 1003**，而是 500（与 uploadAvatar 不同，**RAG 端需正确区分**）
  - **无 metric**（Go 端 selectAvatar 不埋点）
- **可替换**：RAG `AvatarSelectService.java:53-86` —— **注意 RAG 端通过 `IdentityResolver.claimedUID` 校验 uid，但用 `throw e` 把 gRPC 异常透传给 GlobalExceptionHandler**。当前**实际是把 gRPC 异常透传给 GlobalExceptionHandler**，由 handler 的 `mapRPCStatusToCode` 兜底（INVALID_ARGUMENT→500 / NOT_FOUND→500）—— **行为 1:1 等价**，但 RAG 端未在 Service 内显式 catch，需要 review 时确认。

### `internal/logic/user/uploadavatarlogic.go`
- **主要职责**：5 状态机
  1. `currentUserID(ctx)` → 失败 → 1006
  2. `strings.TrimSpace(req.AvatarUrl) == ""` → 500 `Error`
  3. `UserRpc.UploadAvatar(Uid, AvatarUrl, ContentType, SizeBytes)` → `codes.InvalidArgument` → 500；`codes.NotFound` → 1003 `ErrorUserNotExist`（**与 selectAvatar 不同！**）；`codes.Internal` → 5001；其它 → 1015
  4. 成功 → `UploadAvatarResp{AvatarUrl, History}`
- **L2 关键**：
  - uploadAvatar 的 `codes.NotFound` → 1003，selectAvatar 的 `codes.NotFound` → 500 —— 看似不合理但 1:1 跟随 Go 端
  - **无 metric**
- **可替换**：RAG `AvatarUploadService.java:55-89` 1:1。

### `internal/logic/user/getavatarhistorylogic.go`
- **主要职责**：4 状态机
  1. `currentUserID(ctx)` → 失败 → 1006
  2. `UserRpc.GetAvatarHistory(Uid, Limit=30)` → `codes.NotFound` → 1003；`codes.Internal` → 5001；其它 → 1015
  3. 成功 → `AvatarHistoryResp{List: [...]}`（**注**：Limit=30 硬编码，DRIFT-016 决策 1:1 不配置化）
- **L2 关键**：Limit=30 硬编码（**RAG `AvatarHistoryService.LIMIT = 30` 1:1 保留**）。
- **可替换**：RAG `AvatarHistoryService.java:58-88` 1:1。

### `internal/logic/user/avatarhelpers.go`
- **主要职责**：
  - `currentUserID(ctx)` → `identity.ClaimedUID(ctx)`
  - `avatarHistoryItemFromPB(item *pb.AvatarHistoryItem)` → `types.AvatarHistoryItem{Id, AvatarUrl, ContentType, SizeBytes, IsCurrent, CreateTime}`
- **L2 关键**：api 端不归一化 URL/contentType（**Go 端 upload/select avatar 的 `strings.TrimSpace` 归一化在 api 端做，user-rpc 端不再做** —— RAG 把归一化放在 `UserApiSupport.normalizeAvatarUrl/normalizeContentType/normalizeHistoryId`，与 `AvatarSelectService` / `AvatarUploadService` 1:1）。
- **可替换**：RAG `UserApiSupport.normalizeAvatarUrl/normalizeContentType/normalizeHistoryId`（**L4 推导**：Go 端 `avatarhelpers.go` 内联了 `currentUserID`，RAG 端改由 `IdentityResolver.claimedUID` 提供）；`UserApiSupport.fromProto(AvatarHistoryItem)` 替代 `avatarHistoryItemFromPB`。

### `internal/identity/resolve.go`
- **主要职责**：api 侧身份契约的"转发层"，把 sentinel exception 和 `SubjectRef` 类型从 `service/user/user/identity/resolve.go` 重导出到 api 包，**保留 api 包旧路径**（注释里说明）。
- **可替换**：RAG 端**不需要**这一层 —— 直接在 `service/IdentityResolver.java` 实现 5 sentinel 和 `SubjectRef`，复用 `proto.UserServiceBlockingStub`。**L4 推导**：api 侧 identity 与共享 identity resolver 合并。

### `internal/middleware/checkblacklistmiddleware.go`
- **主要职责**：从 Authorization 取 token → Redis `ExistsCtx("user:jwt_blacklist:<token>")`；命中 → **写 401 + `{"code":401,"msg":"登录已失效,请重新登陆"}`**（不通过 envelope）；未命中 → 把 token 写到 `context.WithValue("jwt_token", token)`。
- **可替换**：RAG `JwtAuthInterceptor.preHandle` 合并到 JWT 验签之后。
- **已记录偏离**：401 → 200/1006（AGENTS.md §3.4 + §6.1 #10）。

### `internal/metrics/metrics.go`
- **主要职责**：`UserApiInterceptCount{Subsystem="user_api", Name="intercept_total", Labels=["path", "result"]}` + `InitMetrics()`。
- **L2 关键**：**仅 7 个 Counter**，覆盖 login (5: rpc_error / wrong_password / user_banned / token_error / success) + register (2: rpc_error / success)。**其余 7 个 RPC 不埋点**。
- **可替换**：RAG `UserApiMetrics.java` 完整保留。

### `internal/types/types.go`
- **主要职责**：9 个 Req/Resp + UserInfo + AvatarHistoryItem + CreateUserReq。
- **L1 关键**：
  - `CreateUserReq{Username, Password, Email, ExtraInfo}`（与 admin 域 `CreateAdminReq` 字段数相同）
  - `LoginReq{Username, Password}` / `LoginResp{Token}`
  - `LogoutReq{}`（空）/ `LogoutResp{Success}`
  - `GetUserReq{}`（空，uid 走 jwt）/ `GetUserResp{User, Found}`
  - `UpdateUserReq{Username optional, Password optional, Email optional, ExtraInfo optional}` / `UpdateUserResp{User}`（**注意**：无 Success 字段，与 admin 域不同）
  - `DeleteUserReq{}`（空）/ `DeleteUserResp{Success}`
  - `UploadAvatarReq{AvatarUrl, ContentType optional, SizeBytes optional}` / `UploadAvatarResp{AvatarUrl, History}`
  - `SelectAvatarReq{HistoryId}` / `SelectAvatarResp{AvatarUrl, History}`
  - `GetAvatarHistoryReq{}`（空）/ `AvatarHistoryResp{List}`
  - `AvatarHistoryItem{Id, AvatarUrl, ContentType, SizeBytes, IsCurrent, CreateTime}`
  - `UserInfo{Uid string, Score uint32, Username, Email, ExtraInfo}`（**注意**：含 `Score`，但 user-api 端 UserInfo 不暴露 score 字段）

### 跨模块：`service/user/user/identity/resolve.go`
- **主要职责**：**L1 权威 identity resolver**
  - 5 sentinel error：`ErrInvalidClaim` / `ErrUserNotFound` / `ErrIdentityMismatch` / `ErrUserInactive` / `ErrStatusUnavailable`
  - `ClaimedUID(ctx)` → 从 ctx.Value("userId") 取 json.Number，非法/缺失 → `ErrInvalidClaim`
  - `ResolveUser(ctx, users UserReader)` → 调 `users.GetUser(Uid)` → 校验 `response.Found=true` / `User.Uid` 一致 / **`User.Status` presence + == 0**（status nil → `ErrStatusUnavailable`；status != 0 → `ErrUserInactive`）
  - `ResolveSubjectRef(ctx, users)` → `SubjectRef{AuthorityID="rtw.identity", TenantID="platform", SubjectID=Long.toString(Uid)}`
- **L2 关键**：**`Status == nil` 是 user 域独有语义**，admin 域无此分支（admin.proto AdminInfo 无 status 字段）。
- **承载位置**：RAG `IdentityResolver.java` 完整 1:1 复刻 + `resolveUser` 用 `user.hasStatus()` 判 presence。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `usercenter.go` 进程装配 | `UserApiApplication` + Spring 容器 | ABSORB | Spring Boot 启动 |
| `usercenter.go` CORS / HTTP span | `common/observability/HttpMiddlewareFilter` | REUSE | 全局 Filter |
| `identity_contract.md` L1 协议 | `service/IdentityResolver` L1 常量 + 5 sentinel | ADAPT | Java 端集中实现 |
| `config/config.go` | `config/UserApiProperties` | ADAPT | RestConf + UserAuth + UserRpc + BizRedis 绑定 |
| `svc/servicecontext.go` UserRpc | `client/UserRpcClient`（`@GrpcClient("user-rpc")`） | ADAPT | devh starter 自动注入 |
| `svc/servicecontext.go` Redis | `StringRedisTemplate` | ABSORB | spring-data-redis |
| `routes.go` 块 1（public） | `JwtAuthInterceptor.PUBLIC_PATHS` | ADAPT | 拦截器按 path 匹配 |
| `routes.go` 块 2（JWT + blacklist） | `interceptor/JwtAuthInterceptor` | MERGE | Go 两个 middleware 合一个 Java interceptor |
| `routes.go` 前缀 `/usercenter/v1` | `@RequestMapping("/user/v1")` | ADAPT | **已记录偏离**（§3.4） |
| `routes.go` method 混合 | 全部 `@PostMapping` | ADAPT | **已记录偏离**（§3.4） |
| 9 个 handler | `controller/UserController`（单一，9 个 `@PostMapping`） | MERGE | 9 handler 同质 |
| `types/types.go` UserInfo | `dto/UserInfo`（uid/username/email/extraInfo 4 字段，**不暴露 score**） | SPLIT | 按 HTTP 契约拆 |
| `types/types.go` AvatarHistoryItem | `dto/AvatarHistoryItem`（6 字段） | SPLIT | 同上 |
| `types/types.go`（9 对 Req/Resp） | `dto/*Req` / `dto/*Resp`（17 文件） | SPLIT | 同上 |
| `httpx.OkJson` 信封 | `advice/GlobalResponseAdvice` | NEW | Spring advice |
| 各 logic 错误返回 | `advice/GlobalExceptionHandler`（5 个 IdentityResolver sentinel + 3 框架异常 + 1 兜底） | ADAPT | gRPC code → 业务码 + 5 sentinel 兜底 |
| `middleware/checkblacklistmiddleware.go` | `JwtAuthInterceptor.preHandle` blacklist 分支 | MERGE | 合并 |
| 401 短路 | `BusinessException(ERROR_TOKEN_RUNTIME)` | ADAPT | **已记录偏离**（§6.1 #10 + §3.4） |
| `logic/user/loginlogic.go` | `service/UserLoginService` | MERGE | 6 状态机 + 5 metric |
| `logic/user/registerlogic.go` | `service/UserRegisterService` | MERGE | 3 状态机 + 2 metric；proto 方法名 `register` 1:1 |
| `logic/user/logoutlogic.go` | `service/UserLogoutService` | MERGE | 3 状态机，**无 metric** |
| `logic/user/getuserlogic.go` | `service/UserGetService` | MERGE | 6 状态机 + IdentityResolver 5 sentinel + UserInfo 4 字段 |
| `logic/user/updateuserlogic.go` | `service/UserUpdateService` | MERGE | 4 状态机，**无 metric** |
| `logic/user/deleteuserlogic.go` | `service/UserDeleteService` | MERGE | 4 状态机 + `!rpcResp.getSuccess()` 兜底 |
| `logic/user/selectavatarlogic.go` | `service/AvatarSelectService` | MERGE | 5 状态机；`codes.NotFound` → 500（**非 1003**） |
| `logic/user/uploadavatarlogic.go` | `service/AvatarUploadService` | MERGE | 5 状态机；`codes.NotFound` → 1003 |
| `logic/user/getavatarhistorylogic.go` | `service/AvatarHistoryService` | MERGE | 4 状态机；`LIMIT=30` 硬编码 |
| `logic/user/avatarhelpers.go`（normalize + avatarHistoryItemFromPB） | `service/UserApiSupport.normalize*` + `fromProto(AvatarHistoryItem)` | MERGE | helper 聚合 |
| `logic/user/avatarhelpers.go`（currentUserID） | `service/IdentityResolver.claimedUID` | MERGE | 收口在 IdentityResolver |
| `internal/identity/resolve.go`（api 转发层） | （无需对应层，直接 `service/IdentityResolver`） | ABSORB | api 与共享 identity 合并 |
| `service/user/user/identity/resolve.go`（L1 权威） | `service/IdentityResolver`（5 sentinel + `AuthorityID`/`PlatformTenantID` + `SubjectRef`） | ADAPT | 1:1 复刻 |
| `middleware/checkblacklistmiddleware.go` 401 | `BusinessException(ERROR_TOKEN_RUNTIME)` | ADAPT | **已记录偏离** |
| `metrics/metrics.go` | `metrics/UserApiMetrics`（7 Counter：login 5 + register 2） | ADAPT | Micrometer |
| —（无 Go 对应） | `advice/GlobalResponseAdvice` | NEW | Spring advice 模式 |
| —（无 Go 对应） | `advice/GlobalExceptionHandler` | NEW | 统一异常映射 |
| —（无 Go 对应） | `config/WebMvcConfig` | NEW | 拦截器注册 |
| —（无 Go 对应） | `interceptor/JwtAuthInterceptor` | NEW | 合并 JWT + blacklist |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/users/user/api/
├── UserApiApplication.java
├── advice/
│   ├── GlobalResponseAdvice.java
│   └── GlobalExceptionHandler.java
├── client/
│   └── UserRpcClient.java
├── config/
│   ├── UserApiProperties.java
│   └── WebMvcConfig.java
├── controller/
│   └── UserController.java
├── interceptor/
│   └── JwtAuthInterceptor.java
├── dto/
│   ├── UserInfo.java
│   ├── AvatarHistoryItem.java
│   ├── RegisterReq.java
│   ├── RegisterResp.java
│   ├── LoginReq.java
│   ├── LoginResp.java
│   ├── LogoutReq.java
│   ├── LogoutResp.java
│   ├── GetUserReq.java
│   ├── GetUserResp.java
│   ├── UpdateUserReq.java
│   ├── UpdateUserResp.java
│   ├── DeleteUserReq.java
│   ├── DeleteUserResp.java
│   ├── SelectAvatarReq.java
│   ├── SelectAvatarResp.java
│   ├── UploadAvatarReq.java
│   ├── UploadAvatarResp.java
│   ├── GetAvatarHistoryReq.java
│   └── GetAvatarHistoryResp.java
├── metrics/
│   └── UserApiMetrics.java
└── service/
    ├── UserApiSupport.java
    ├── IdentityResolver.java
    ├── UserRegisterService.java
    ├── UserLoginService.java
    ├── UserLogoutService.java
    ├── UserGetService.java
    ├── UserUpdateService.java
    ├── UserDeleteService.java
    ├── AvatarSelectService.java
    ├── AvatarUploadService.java
    └── AvatarHistoryService.java
```

### 逐文件说明

**`UserApiApplication.java`**
- 所属包：`com.rag.users.user.api`
- 主要职责：Spring Boot 入口，`@SpringBootApplication(scanBasePackages={"com.rag.users.user.api","com.rag.common"})` + `@EnableConfigurationProperties(UserApiProperties)` + `@EnableDiscoveryClient`
- 承载 Go 职责：`usercenter.go` 进程装配
- 为什么独立：4 个 api/rpc 模块统一入口风格
- 依赖：`config/UserApiProperties`

**`advice/GlobalResponseAdvice.java`**
- 所属包：`com.rag.users.user.api.advice`
- 主要职责：`@RestControllerAdvice implements ResponseBodyAdvice<Object>`；`beforeBodyWrite` 判 `body instanceof Response` 直接放行（**关键**：避免二次包裹 §6.1 #1 已修复）
- 承载 Go 职责：每个 handler 末尾 `httpx.OkJson`
- 为什么独立：Spring advice 是 envelope 拦截的标准位
- 依赖：`common/response/Response`、`users-common/errmsg/ErrorCode`

**`advice/GlobalExceptionHandler.java`**
- 所属包：`com.rag.users.user.api.advice`
- 主要职责：`@RestControllerAdvice` + 多 `@ExceptionHandler`：
  - `BusinessException` → 业务码 + HTTP 200
  - `StatusRuntimeException` → gRPC code → 业务码（INTERNAL→5001/NOT_FOUND→1003/ALREADY_EXISTS→1001/INVALID_ARGUMENT→500/default→1015）+ HTTP 200
  - 5 个 `IdentityResolver.Err*Exception` → 业务码 + HTTP 200
  - 框架级 3 个 → 400/405/415
  - 兜底 `Exception` → 500 + 5001
- 承载 Go 职责：各 logic 的错误返回
- 为什么独立：与 admin-api 同款
- 依赖：`common/response/Response`、`users-common/errmsg/ErrorCode`、`service/IdentityResolver`

**`client/UserRpcClient.java`**
- 所属包：`com.rag.users.user.api.client`
- 主要职责：`@Component`；`@GrpcClient("user-rpc") private UserServiceBlockingStub` + getter
- 承载 Go 职责：`svc/servicecontext.go` 的 `UserRpc userservice.UserService`
- 为什么独立：客户端 stub 独立 Bean；9 Service 共享
- 依赖：`com.rag.users.user.proto.UserServiceGrpc`（生成物）

**`config/UserApiProperties.java`**
- 所属包：`com.rag.users.user.api.config`
- 主要职责：`@ConfigurationProperties(prefix="user-api")` + `name` + `UserAuth{AccessSecret, AccessExpire}`
- 承载 Go 职责：`config/config.go` 的 `UserAuth`
- 为什么独立：配置绑定独立关注点
- 依赖：无（纯 POJO）

**`config/WebMvcConfig.java`**
- 所属包：`com.rag.users.user.api.config`
- 主要职责：`WebMvcConfigurer.addInterceptors` → 注册 `JwtAuthInterceptor` 到 `/user/v1/**`
- 承载 Go 职责：`routes.go` 块 2 的 `WithJwt(secret)` + blacklist 中间件
- 为什么独立：拦截器注册独立
- 依赖：`interceptor/JwtAuthInterceptor`

**`controller/UserController.java`**
- 所属包：`com.rag.users.user.api.controller`
- 主要职责：`@RequestMapping("/user/v1")` + 9 个 `@PostMapping` 方法：register / login / logout / get / update / delete / avatar/select / avatar / avatar/history
- 承载 Go 职责：`routes.go` 9 条路由 + `handler/user/*handler.go`（9 个）合并
- 为什么独立：9 handler 同质
- 依赖：9 个 `service/*Service`

**`interceptor/JwtAuthInterceptor.java`**
- 所属包：`com.rag.users.user.api.interceptor`
- 主要职责：`HandlerInterceptor.preHandle`
  - public paths（`/user/v1/register` + `/user/v1/login`）→ 直接放行
  - 缺失/空 Bearer → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - JWT 验签失败 → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - claim `userId` 缺失/≤0 → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - Redis blacklist `user:jwt_blacklist:<token>` 存在 → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - 通过 → 写 attribute `userId` (Long) + `jwt_token` (String)
- 承载 Go 职责：`routes.go` 块 2 + `middleware/checkblacklistmiddleware.go`
- 为什么独立：密钥/路径与 admin-api 不同
- 依赖：`config/UserApiProperties`、`StringRedisTemplate`、`users-common/jwt/JwtUtil`（含 `padToHmacKeySize`）

**`dto/UserInfo.java`**
- 所属包：`com.rag.users.user.api.dto`
- 主要职责：嵌套 DTO `uid (string) / status / username / email / extra_info`（**注意**：api DTO 含 status，proto3 `optional int64` 透传；user.proto UserInfo 含 score，**api DTO 不暴露 score** —— 与 Go 端 `UserInfo{Uid, Score, Username, Email, ExtraInfo}` 的 proto wire 不同）
- 承载 Go 职责：`types/types.go` 的 `UserInfo`（Go 端含 Score 字段；RAG api DTO 显式 drop）
- 为什么独立：与 dto 包内其他类型区分；用全限定名 `com.rag.users.user.proto.UserInfo` 区分 proto
- 依赖：Jackson

**`dto/AvatarHistoryItem.java`**
- 所属包：`com.rag.users.user.api.dto`
- 主要职责：嵌套 DTO `id / avatar_url / content_type / size_bytes / is_current / create_time`（snake_case）
- 承载 Go 职责：`types/types.go` 的 `AvatarHistoryItem`
- 为什么独立：与 dto 包内其他类型区分
- 依赖：Jackson

**`dto/*Req` / `dto/*Resp`**（17 个）
- 所属包：`com.rag.users.user.api.dto`
- 主要职责：9 对 HTTP 请求/响应 DTO（`uid` 字段统一 `@JsonSerialize` 为字符串）
- 承载 Go 职责：`types/types.go` 全部
- 为什么独立：按 HTTP 契约与字段校验要求一一拆分
- 依赖：Jackson

**`metrics/UserApiMetrics.java`**
- 所属包：`com.rag.users.user.api.metrics`
- 主要职责：`@Component` 注入 `MeterRegistry`；7 个 Counter：
  - login (5): rpc_error / wrong_password / user_banned / token_error / success
  - register (2): rpc_error / success
- 承载 Go 职责：`metrics/metrics.go` 的 `UserApiInterceptCount`（**仅 login/register 埋点**，其余 7 个 RPC 不埋点）
- 为什么独立：指标独立变化原因
- 依赖：Micrometer

**`service/UserApiSupport.java`**
- 所属包：`com.rag.users.user.api.service`
- 主要职责：`@Component`
  - `normalizeAvatarUrl(value)` → trim；空 → ""（**注意**：返回 "" 而非 null）
  - `normalizeContentType(value)` → trim；空 → `"image/png"`（D-012：默认值）
  - `normalizeHistoryId(value)` → trim
  - `extractUserId(HttpServletRequest)` → 委派 `IdentityResolver.claimedUID`
  - `fromProto(AvatarHistoryItem proto)` → DTO 转换
  - `fromProto(UserInfo proto)` → DTO 转换（**4 字段，不含 score**）
- 承载 Go 职责：`logic/user/avatarhelpers.go` 的归一化 + proto 投影
- 为什么独立：跨 9 Service 共享
- 依赖：`service/IdentityResolver`

**`service/IdentityResolver.java`**
- 所属包：`com.rag.users.user.api.service`
- 主要职责：`@Component`
  - L1 常量 `AuthorityID = "rtw.identity"` / `PlatformTenantID = "platform"`
  - `claimedUID(HttpServletRequest)` → 抛 `ErrInvalidClaimException`
  - `resolveUser(HttpServletRequest, UserServiceBlockingStub)` → 调 user-rpc getUser → 5 sentinel exception：
    - `ErrInvalidClaimException` → 1006
    - `ErrUserNotFoundException` → 1003
    - `ErrUserInactiveException` → 1011（**proto3 presence + status != 0**）
    - `ErrStatusUnavailableException` → 1015（**status 字段未设置**）
    - `ErrIdentityMismatchException` → 5001（**uid 不一致**）
  - `resolveSubjectRef(...)` → `SubjectRef{authorityId, tenantId, subjectId=Long.toString(uid)}`
- 承载 Go 职责：`service/user/user/identity/resolve.go`（**L1 权威**）
- 为什么独立：身份契约独立可测
- 依赖：`com.rag.users.user.proto.*`

**`service/UserRegisterService.java` / `UserLoginService.java` / `UserLogoutService.java` / `UserGetService.java` / `UserUpdateService.java` / `UserDeleteService.java` / `AvatarSelectService.java` / `AvatarUploadService.java` / `AvatarHistoryService.java`**
- 9 个独立 Service，每个对应一个 `@PostMapping` 端点
- 模式：调 `UserRpc.<Op>` → gRPC code / rpcResp.Status 映射 → 返 DTO
- 单端点用例，各自独立变化原因
- 依赖：`client/UserRpcClient`、`service/UserApiSupport`、`service/IdentityResolver`

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `usercenter.go` | `UserApiApplication` | ABSORB | 进程装配 |
| `usercenter.go`（CORS） | `common/observability/HttpMiddlewareFilter` | REUSE | 全局 Filter |
| `identity_contract.md` | `service/IdentityResolver` | ADAPT | L1 协议 → Java |
| `config/config.go` | `config/UserApiProperties` | ADAPT | Spring `@ConfigurationProperties` |
| `svc/servicecontext.go`（UserRpc） | `client/UserRpcClient` | ADAPT | devh `@GrpcClient` |
| `svc/servicecontext.go`（Redis） | `StringRedisTemplate` | ABSORB | spring-data-redis |
| `routes.go` 块 1（public） | `JwtAuthInterceptor.PUBLIC_PATHS` | ADAPT | 拦截器按 path 匹配 |
| `routes.go` 块 2（JWT + blacklist） | `interceptor/JwtAuthInterceptor` | MERGE | 合一个 interceptor |
| `routes.go` 前缀 `/usercenter/v1` | `@RequestMapping("/user/v1")` | ADAPT | **已记录偏离**（§3.4） |
| `routes.go` method 混合 | 全部 `@PostMapping` | ADAPT | **已记录偏离**（§3.4） |
| `handler/user/*`（9 个） | `controller/UserController`（单一） | MERGE | 同质薄适配层 |
| `types/types.go`（UserInfo） | `dto/UserInfo`（4 字段，不含 score） | SPLIT | 按契约拆分；drop score |
| `types/types.go`（AvatarHistoryItem） | `dto/AvatarHistoryItem` | SPLIT | 同上 |
| `types/types.go`（9 对 Req/Resp） | `dto/*Req` / `dto/*Resp` | SPLIT | 按契约拆分 |
| `httpx.OkJson` 信封 | `advice/GlobalResponseAdvice` | ADAPT | Spring advice |
| 各 logic 错误返回 | `advice/GlobalExceptionHandler` | ADAPT | gRPC code + 5 sentinel 映射 |
| `middleware/checkblacklistmiddleware.go` | `JwtAuthInterceptor.preHandle` blacklist 分支 | MERGE | 合并 |
| 401 短路 | `BusinessException(ERROR_TOKEN_RUNTIME)` | ADAPT | **已记录偏离**（§6.1 #10 + §3.4） |
| `logic/user/loginlogic.go` | `service/UserLoginService` | MERGE | 6 状态机 + 5 metric |
| `logic/user/registerlogic.go` | `service/UserRegisterService` | MERGE | 3 状态机 + 2 metric；proto 方法名 `register` |
| `logic/user/logoutlogic.go` | `service/UserLogoutService` | MERGE | 3 状态机，**无 metric** |
| `logic/user/getuserlogic.go` | `service/UserGetService` | MERGE | 6 状态机 + 5 sentinel |
| `logic/user/updateuserlogic.go` | `service/UserUpdateService` | MERGE | 4 状态机，**无 metric** |
| `logic/user/deleteuserlogic.go` | `service/UserDeleteService` | MERGE | 4 状态机 + `!rpcResp.getSuccess()` 兜底 |
| `logic/user/selectavatarlogic.go` | `service/AvatarSelectService` | MERGE | 5 状态机；`codes.NotFound` → 500 |
| `logic/user/uploadavatarlogic.go` | `service/AvatarUploadService` | MERGE | 5 状态机；`codes.NotFound` → 1003 |
| `logic/user/getavatarhistorylogic.go` | `service/AvatarHistoryService` | MERGE | 4 状态机；`LIMIT=30` 硬编码 |
| `logic/user/avatarhelpers.go`（normalize + avatarHistoryItemFromPB） | `service/UserApiSupport.normalize*` + `fromProto(AvatarHistoryItem)` | MERGE | helper 聚合 |
| `logic/user/avatarhelpers.go`（currentUserID） | `service/IdentityResolver.claimedUID` | MERGE | 收口在 IdentityResolver |
| `internal/identity/resolve.go`（api 转发层） | — | ABSORB | 无需对应层，直接 `service/IdentityResolver` |
| `service/user/user/identity/resolve.go`（L1 权威） | `service/IdentityResolver` | ADAPT | 1:1 复刻 5 sentinel + `AuthorityID`/`PlatformTenantID` + `SubjectRef` |
| `metrics/metrics.go` | `metrics/UserApiMetrics`（7 Counter） | ADAPT | Micrometer |
| —（user-api 端无 Go 对应） | `advice/GlobalResponseAdvice` | NEW | Spring advice |
| —（user-api 端无 Go 对应） | `advice/GlobalExceptionHandler` | NEW | 统一异常映射 |
| —（user-api 端无 Go 对应） | `config/WebMvcConfig` | NEW | 拦截器注册 |
| —（user-api 端无 Go 对应） | `interceptor/JwtAuthInterceptor` | NEW | 合并 JWT + blacklist |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 9 条路由的 method/path/中间件分组（除已记录偏离外）；全部业务码 500/1001/1002/1003/1004/1006/1009/1011/1015/5001；JWT claim `userId`；Redis blacklist key 前缀 `user:jwt_blacklist:`；AuthorityID/PlatformTenantID；Limit=30 硬编码 |
| L2 业务语义有承载位置 | ✅ 5 sentinel 全部在 `IdentityResolver` 内抛出；selectAvatar `codes.NotFound → 500` 与 uploadAvatar `codes.NotFound → 1003` 的差异忠实记录；`rpcResp.Status == 1/2` 三态码字段判读；proto3 presence（`user.hasStatus()`）→ `ErrStatusUnavailable` |
| L3 技术语义有承载方案 | ✅ JWT secret ≥32B 走 `padToHmacKeySize` 收口；user-api 端不补齐也无影响（36B UUID 已合规）；Redis blacklist `fail-open`；Limit=30 硬编码保留（DRIFT-016 决策） |
| 业务分支与异常路径已纳入 | ✅ 9 个 RPC 全部含 gRPC code + rpcResp.Status 分支；`!rpcResp.getSuccess()` 兜底（user delete）；5 sentinel × 5 业务码映射 |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；CORS → `common/observability`；`@GrpcClient` 注入 stub |
| 复用组件已明确 | ✅ `common/response/Response`、`common/exception/BusinessException`、`common/observability.HttpMiddlewareFilter`、`users-common/jwt/JwtUtil`、`users-common/errmsg/ErrorCode` |
| 新增文件有职责依据 | ✅ `JwtAuthInterceptor`（合并 Go 两个 middleware）、`GlobalResponseAdvice`（Spring advice 模式）、`GlobalExceptionHandler`（统一异常映射）、`IdentityResolver`（user 域 5 sentinel + proto3 status presence）、`UserApiSupport.normalize*`（归一化工具）—— 均非"Go 有同名文件故建" |
| 合并有组织依据 | ✅ 9 logic → 9 Service（一一对应，单端点用例独立）；handler/user 9 个 → `UserController` 单一（同质薄适配层）；JWT + blacklist 两个 middleware → `JwtAuthInterceptor` 单一；api 侧 identity 与共享 identity 合并 |
| 拆分可解释 | ✅ DTO 按契约独立（21 个文件）；advice / interceptor / client / config 各自独立变化原因 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `ServiceContext.java` / `Types.java` / `Server.java` / `Middleware.java` |
| 已记录偏离保留 | ✅ `/user/v1` 前缀（Go `/usercenter/v1`）；统一 POST（Go 混合）；HTTP 200 + body code 表达成败（Go 401 + body code）；user-api DTO UserInfo drop score（proto 含 score，api 不暴露）—— 不"顺手修正回旧行为" |
| **AGENTS.md §6.1 全部已修问题在本文件覆盖** | ✅ #1 ResponseAdvice 二次包裹（`GlobalResponseAdvice` 判 `instanceof Response` 放行）；#10 HTTP 状态码语义（已记录偏离） |
| **跨用户文档一致性** | ✅ 与 `users/user/rpc.md`、`users/admin/rpc.md`、`users/admin/api.md` 命名一致：`UserRpcClient` / `AdminRpcClient`、`JwtAuthInterceptor` / `AdminJwtAuthInterceptor`、`UserApiSupport` / `AdminApiSupport`、`IdentityResolver`（5 sentinel / 3 sentinel 区分）、`GlobalResponseAdvice` / `GlobalExceptionHandler`、`UserApiMetrics` / `AdminApiMetrics` |
| **proto3 status presence 语义** | ✅ `IdentityResolver.resolveUser` 用 `user.hasStatus()` 判 presence，1:1 跟随 Go 端 `User.Status == nil → ErrStatusUnavailable` |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **路由前缀与方法已偏离 Go 端（已留档）** | Go 为 `/usercenter/v1/user/*`（POST/GET/DELETE 混合）；RAG 为 `/user/v1/*`（统一 POST）。属 wire 契约变更，AGENTS.md §3.4 已留档；**不可"修正回旧行为"** |
| 2 | **`AvatarSelectService` / `AvatarUploadService` 异常路径走 `throw e`** | 这两个 Service 当前未显式 catch `StatusRuntimeException`，把 gRPC 异常透传给 `GlobalExceptionHandler.mapRPCStatusToCode` 做兜底（INVALID_ARGUMENT→500 / NOT_FOUND→500）。**行为 1:1 等价**，但需 review 时确认**Go 端 selectAvatar 的 `codes.NotFound → 500` 和 uploadAvatar 的 `codes.NotFound → 1003` 差异**正确传达：`GlobalExceptionHandler` 的 default 映射把 `NOT_FOUND` → 1003，与 selectAvatar 期望不符**。当前事实是这两个 Service 实际**没有 `NOT_FOUND` 异常会被全局捕获**，因为 user-rpc 端 `SelectAvatar` 不会抛 NOT_FOUND（avatar history 不存在会走 INVALID_ARGUMENT 或隐式 success），所以**这条风险在 RAG 端不可触发**，但需要在 review 时书面确认 |
| 3 | **`UserController` 4 个端点 `@RequestBody` 默认 required=true** | Go 端 `LogoutReq` / `GetUserReq` / `DeleteUserReq` / `GetAvatarHistoryReq` 是空 struct，handler 不要求 body；RAG 当前 4 个端点为 `@RequestBody(required=false)`（**UserController.java:118/126/137/153**），其余 5 个是 `@RequestBody`（默认 required=true）。**已部分修复**：4 个空 struct 端点已加 `required=false`。**待修复**：5 个非空 struct 端点 Go 端无 body 校验（Login/Register 等）但 RAG 强制要求 body —— 这是符合 REST 习惯的偏离，**不在 Go 1:1 范畴**，不视为缺陷 |
| 4 | **`UserApiSupport.normalizeAvatarUrl` 返回 `""` 而非 null** | Go 端 `strings.TrimSpace` 返回 `""`；RAG 同样返回 `""`（1:1）。**但**下游 `selectAvatar` / `uploadAvatar` 检查 `if (avatarUrl.isEmpty())` 抛 500 —— 与 Go 端 `TrimSpace == ""` 检查后抛 500 1:1。**无问题**，只是明示"空字符串 = 不通过校验"的语义 |
| 5 | **`UserLoginService` 重新定义 5 个 metric label 但 admin-api 只 3 个** | user-api 端 login 有 wrong_password / user_banned / token_error 三分支（来自 rpcResp.Status 字段判读）；admin-api 端 login 只有 UNAUTHENTICATED → 1002 一分支（admin.proto 无 Status 字段）。这是 user 与 admin 域行为差异的忠实记录，**不是缺陷**，但需要在 review 时确认两模块 metric label 名不互相错位 |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| Flyway schema 归属 | 与 admin-rpc / user-rpc 共享 `flyway_schema_history` 版本号（AGENTS.md §6.3 问题 1）—— user-api 不直接涉及 |
| admin-rpc 是否应改为转发 user-rpc | admin-rpc 直写 `users_user` 表（AGENTS.md §6.3 问题 2）—— user-api 端无影响 |
| `AvatarSelectService` / `AvatarUploadService` 的 `NOT_FOUND` → 500/1003 差异是否会被触发 | 见不通过项 #2；当前不可触发，但**待 user-rpc 端进一步审计后确认** |
| `UserController` 5 个非空 struct 端点是否要改 `required=false` | 见不通过项 #3；REST 风格偏离，不在 Go 1:1 范畴 |
| `UserApiSupport` 是否要把 `parseUid` / `codeFromRPCError` 也加进来（与 admin-api `AdminApiSupport` 对齐） | 当前 RAG user-api 端 `parseUid` 用得不多（只有 register/login 不带 uid），**业务上有需要再加**；admin-api 端有 6 个 Service 共用 `parseUid` 故已经独立。当前两模块 `*Support` 工具集不完全对称，**待 user-api 端业务增长时再决定是否扩** |
| `IdentityResolver.resolveUser` 内部 `users` 参数若为 null 是否需要在调用方显式防御 | 当前 RAG `UserGetService` 显式检查 `users != null` 后调用 `resolveUser`，1:1 跟随 Go 端逻辑（Go 端 `if users == nil → ErrIdentityMismatch`） |
