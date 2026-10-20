# admin-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/user/admin/api`
> 目标根包：`com.rag.users.admin.api`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

`admin-api` 在 RAG 中**已有完整 Java 实现**（非空骨架），实际包结构如下：

| 包 | 实际文件 | 职责 |
|---|---|---|
| （根） | `AdminApiApplication` | `@SpringBootApplication` 入口 |
| `advice/` | `GlobalResponseAdvice`、`GlobalExceptionHandler` | `@RestControllerAdvice` 信封包装 + `@ExceptionHandler` 业务码映射 |
| `client/` | `AdminRpcClient` | `@GrpcClient("admin-rpc")` 阻塞 stub |
| `config/` | `AdminApiProperties`、`WebMvcConfig` | `@ConfigurationProperties` 绑定 + 拦截器注册 |
| `controller/` | `AdminController` | `@RequestMapping("/admin/v1")` 13 个 `@PostMapping` 端点 |
| `interceptor/` | `AdminJwtAuthInterceptor` | `HandlerInterceptor`：JWT 验签 + Redis blacklist（`@Component`） |
| `metrics/` | `AdminApiMetrics` | `@Component` Micrometer 17 Counter |
| `service/` | `AdminApiSupport`（helper 聚合）+ `IdentityResolver`（3 sentinel）+ 13 个用例 `*Service` | 13 用例 + 共用 helper + 身份契约 |
| `dto/` | 24 个 DTO 类（AdminInfo / UserInfo + 13 对 Req/Resp + CreateInviteCodeReq/Resp） | HTTP wire 契约 |

### 已有组件（跨模块可复用）

| 组件 | 位置 | 承担 |
|---|---|---|
| `com.rag.common.response.Response` | `service/common` | HTTP 统一响应信封 |
| `com.rag.common.exception.BusinessException` | `service/common` | 业务异常基类 |
| `com.rag.common.observability.HttpMiddlewareFilter` | `service/common` | HTTP span Filter（已 `@Component` 全局注册） |
| `com.rag.users.common.jwt.JwtUtil` + `padToHmacKeySize` | `users-common` | JWT 签发 |
| `com.rag.users.common.errmsg.ErrorCode` | `users-common` | 业务错误码枚举 |
| `com.rag.users.admin.proto.*` (生成物) | `service/users/admin/admin-proto` | gRPC proto client stub（`@GrpcClient("admin-rpc")` 自动注入） |

### 已确认的框架机制

- `spring-boot-starter-web`：`@RestController` / `@RequestMapping` / `@PostMapping`
- `org.springframework.web.servlet.HandlerInterceptor` + `WebMvcConfigurer` 拦截器注册
- `@RestControllerAdvice` + `ResponseBodyAdvice<Object>`（**注意 §3.3 idempotency**：必须判 `body instanceof Response` 才不二次包裹 `@ExceptionHandler` 的 `ResponseEntity`）
- `@GrpcClient("admin-rpc")` 自动注入 BlockingStub（devh grpc-client-spring-boot-starter，Nacos discovery 自动发现 admin-rpc 多副本）
- `StringRedisTemplate`（`spring-boot-starter-data-redis`）做 JWT blacklist
- `net.devh:grpc-server-spring-boot-starter` 由 admin-rpc 端配置
- `spring-boot-starter-actuator` / Micrometer
- `org.springframework.cloud.discovery.EnableDiscoveryClient` + Nacos

### 当前 Component 可以直接复用的能力

- **JWT 签发**：复用 `JwtUtil.getToken(...)`（admin-api 唯一签发方，admin-rpc 签发可选）
- **HTTP 信封 + 异常映射**：`common/response/Response` + `advice/GlobalResponseAdvice` + `advice/GlobalExceptionHandler` 三件套已定型
- **JWT 拦截器模式**：user-api 的 `JwtAuthInterceptor` 模式可借鉴，但 admin 与 user 的密钥/路径/public 前缀不同 → **必须独立 Bean**
- **gRPC 客户端**：通过 `@GrpcClient` 自动注入，无需手写 stub

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| HTTP path | Go 为 `/admincenter/v1/admin/*`；RAG 已偏离为 `/admin/v1/*`（AGENTS.md §3.4 留档） |
| HTTP method | Go 为 POST/GET/DELETE 混合（logout=POST/getuser=GET/delete=DELETE）；RAG 统一 POST（AGENTS.md §3.4 留档） |
| **P0: `AdminJwtAuthInterceptor` Bean 创建时 `WeakKeyException`** | ⚠️ **admin-api 默认配置下起不来**（AGENTS.md §6.2 P0）—— RAG 当前 `AdminJwtAuthInterceptor.java:67` 已调 `JwtUtil.padToHmacKeySize` 补齐 15B → 32B，**该 fix 已落地**，但属于"协议变更"留档：Go 端 15B admin secret 直接签，Java 侧依赖 jjwt 强制补齐；admin-rpc 签发侧也走同一 `JwtUtil`，两端 key 字节对齐 |

---

## 12.2 第二部分：Go 原始文件树

```
service/user/admin/api/
├── admincenter.go                                       # main 入口：flag、config、metrics、svc、handler.RegisterHandlers
├── internal/
│   ├── config/config.go                                 # Config：RestConf + AdminAuth + AdminRpc + BizRedis
│   ├── svc/servicecontext.go                            # ServiceContext：Config + AdminRpc + CheckBlacklistMiddleware
│   ├── handler/
│   │   ├── routes.go                                    # 3 个 AddRoutes 块（public / with JWT / 嵌套 blacklist）
│   │   └── admin/                                       # 13 个 handler（每个 ~30 行 httpx.OkJson 包装）
│   │       ├── banuserhandler.go
│   │       ├── createhandler.go
│   │       ├── createinvitehandler.go
│   │       ├── deletehandler.go
│   │       ├── getselfhandler.go
│   │       ├── getuserhandler.go
│   │       ├── getuserlisthandler.go
│   │       ├── loginhandler.go
│   │       ├── logouthandler.go
│   │       ├── resetuserpasswordhandler.go
│   │       ├── unbanuserhandler.go
│   │       ├── updatehandler.go
│   │       └── updateuserhandler.go
│   ├── logic/admin/                                     # 13 个 logic（每个 ~40-100 行，含 gRPC code 映射）
│   │   ├── banuserlogic.go
│   │   ├── createlogic.go
│   │   ├── createinvitelogic.go
│   │   ├── deletelogic.go
│   │   ├── getselflogic.go
│   │   ├── getuserlistlogic.go
│   │   ├── getuserlogic.go
│   │   ├── loginlogic.go
│   │   ├── logoutlogic.go
│   │   ├── resetuserpasswordlogic.go
│   │   ├── unbanuserlogic.go
│   │   ├── updatelogic.go
│   │   └── updateuserlogic.go
│   ├── metrics/metrics.go                               # AdminApiInterceptCount CounterVec
│   ├── middleware/checkblacklistmiddleware.go           # Go 端独立 redis blacklist 中间件
│   └── types/types.go                                   # 13 个 Req/Resp + AdminInfo / UserInfo
└── etc/admincenter.yaml                                 # 配置
```

---

## 12.3 第三部分：Go 文件职责分析

### `admincenter.go`
- **主要职责**：进程入口。`metrics.InitMetrics()`、flag、conf.MustLoad、logx.MustSetup、logger.Init、`observability.NewHTTPMiddleware`（cors + slow threshold）、`rest.MustNewServer`、`handler.RegisterHandlers(server, ctx)`。
- **关键调用关系**：`config.MustLoad` → `svc.NewServiceContext(c)` → `handler.RegisterHandlers`。
- **可替换**：Spring `@SpringBootApplication` + `@EnableDiscoveryClient` + `HttpMiddlewareFilter`（已在 `common/observability` `@Component` 全局注册）。

### `internal/config/config.go`
- **主要职责**：`Config{rest.RestConf, AdminAuth{AccessSecret, AccessExpire}, AdminRpc zrpc.RpcClientConf, BizRedis redis.RedisConf}`。
- **L1 外部契约冻结**：`AccessSecret="yU-hCm)2*?NQmWG"`（15 字节，与 admin-rpc 共享）；`AccessExpire=2592000`（30 天）。
- **可替换**：Spring `@ConfigurationProperties(prefix="admin-api")` —— `AdminApiProperties.java` 1:1 字段对应。

### `internal/svc/servicecontext.go`
- **主要职责**：依赖持有（`Config` / `AdminRpc` / `CheckBlacklistMiddleware rest.Middleware`）。
- **关键动作**：`redis.MustNewRedis(c.BizRedis)` → 注入 `CheckBlacklistMiddleware`；`adminservice.NewAdminService(zrpc.MustNewClient(c.AdminRpc))` → 注入 `AdminRpc`。
- **可替换**：Spring DI。`AdminRpc` → `@GrpcClient("admin-rpc") AdminServiceBlockingStub`（devh starter 自动管理 zrpc 客户端）。

### `internal/handler/routes.go`
- **主要职责**：3 个 `AddRoutes` 块（**关键**）：
  - **块 1**（public）：`POST /admin/create` + `POST /admin/login`，无中间件；`WithPrefix("/admincenter/v1")`
  - **块 2**（with JWT + blacklist）：11 个路由 + `WithMiddlewares({CheckBlacklistMiddleware})` + `WithJwt(AccessSecret)` + `WithPrefix("/admincenter/v1")`
- **L1 路由清单**（13 个端点）：
  | path | method | 中间件 |
  |---|---|---|
  | `/admin/create` | POST | — |
  | `/admin/login` | POST | — |
  | `/admin/banuser` | POST | JWT + blacklist |
  | `/admin/delete` | DELETE | JWT + blacklist |
  | `/admin/invite/create` | POST | JWT + blacklist |
  | `/admin/getself` | GET | JWT + blacklist |
  | `/admin/getuser` | GET | JWT + blacklist |
  | `/admin/getuserlist` | GET | JWT + blacklist |
  | `/admin/logout` | POST | JWT + blacklist |
  | `/admin/resetuserpassword` | POST | JWT + blacklist |
  | `/admin/unbanuser` | POST | JWT + blacklist |
  | `/admin/update` | POST | JWT + blacklist |
  | `/admin/updateuser` | POST | JWT + blacklist |
- **已记录偏离（AGENTS.md §3.4）**：
  - 前缀从 `/admincenter/v1` → `/admin/v1`
  - method 不再混合（DELETE/GET 全部统一为 POST）
- **可替换**：RAG 走 Spring `@PostMapping` + `WebMvcConfig.addInterceptors`；public 路径前缀在拦截器内部按 path 匹配（`AdminJwtAuthInterceptor.PUBLIC_PATHS`）。

### `internal/handler/admin/*handler.go`（13 个）
- **统一模式**（每个 ~30 行）：`httpx.Parse(r, &req)` → `logic.NewXxxLogic(ctx, svcCtx).Xxx(&req)` → `httpx.OkJson(w, &response.Response{Code: errmsg.Success, Data: resp, Msg: errmsg.GetErrMsg(errmsg.Success)})`。
- **L2 关键**：handler 层**不做业务逻辑**（logic 已经做完），只做 parse + envelope。
- **可替换**：RAG 走 `AdminController` 单一 Controller（13 个 `@PostMapping`）+ 委托给 13 个 `*Service` + `advice/GlobalResponseAdvice` 自动包信封。**理由**：13 个 handler 同质、无独立生命周期、无独立依赖边界；拆开只会制造 13 个单方法类。

### `internal/logic/admin/loginlogic.go`
- **主要职责**：4 状态机
  1. `AdminRpc.Login` 失败：`codes.Internal` → 5001；其它 → 1015；**`login_rpc_error` metric**
  2. `rpcResp.Status == 1`（密码错或用户名不存在）→ 1002 `ErrorLoginWrong` + `login_wrong_password` metric
  3. `rpcResp.Status == 2`（用户封禁）→ 1011 `ErrorUserBanned` + `login_user_banned` metric
  4. `jwt.GetToken` 失败 → 1006 `ErrorTokenRuntime` + `login_token_error` metric
  5. 成功 → `login_success` metric + 返 `LoginResp{Token}`
- **L2 关键**：Go 端 `rpcResp.Status` 字段语义见 `user.proto:42` —— proto3 中 `Status` 是 0=success / 1=login failure / 2=banned（**注意**：与 `GetUserResp` 的 `status` 语义**不同**，proto3 的 `uint64 status` 字段在 `LoginResp` 是三态码）。
- **可替换**：RAG `AdminLoginService.java:50-101` —— BusinessException 单异常路径，admin-api 端不判 `rpcResp.Status`，而是由 admin-rpc 的 gRPC code 区分：
  - `UNAUTHENTICATED` → 1002（**AGENTS.md §6.1 #8 已修复**：缺这条 case 会落到 default 1015"服务繁忙"）
  - `NOT_FOUND` → 1003
  - `INTERNAL` → 5001
  - default → 1015
- **L4 差异**：RAG 端**不暴露 admin-rpc 的 `Status` 字段**，原因是 admin.proto `LoginResp` 不含 Status（与 user.proto 不同），admin-rpc 用 gRPC code 表达；这是 wire 契约差异，**Go admin 端也无 Status 字段可读**，1:1 兼容。

### `internal/logic/admin/logoutlogic.go`
- **主要职责**：3 状态机
  1. 从 ctx 取 `jwt_token` → 1004 `ErrorTokenNotExist`
  2. `AdminRpc.Logout` 失败 → `codes.Internal` → 5001；其它 → 1015（**无 metric**，Go 端 logout 不埋点）
  3. 成功 → `LogoutResp{Success: true}`
- **可替换**：RAG `AdminLogoutService.java:38-62` 同款逻辑（**`req.LogoutReq{}` 空 struct，adminId/JWT 都从 attribute 取**）。

### `internal/logic/admin/getselflogic.go`
- **主要职责**：直接调 `AdminRpc.GetSelf(Uid)`；失败 → 1003/5001/1015；成功 → `GetSelfResp{Admin: AdminInfo{uid, username, email=stringValue, extra_info}}`。
- **L2 关键**：admin 域 `AdminInfo` 无 `status` 字段（与 user 域 `UserInfo` 有 `status` 不同），所以 RAG `IdentityResolver.resolveAdmin` 不抛 `ErrAdminInactive` / `ErrStatusUnavailable`（user-api 同款 5 sentinel 减为 3 sentinel）。
- **可替换**：RAG `AdminGetSelfService.java:39-60` + `IdentityResolver.resolveAdmin`。

### `internal/logic/admin/updateselflogic.go`
- **主要职责**：3 状态机（注意**与 user-api updateuserlogic 同款**：直接构造 RPC req，**不经过 IdentityResolver.resolveAdmin**；admin 域 updateSelf 走 jwt claim uid）。
- **L2 关键**：与 admin-rpc updateSelfLogic 对称 —— username 非空 → username 冲突；email 非空 → optionalString trim；extra_info 非 null → setMap。
- **可替换**：RAG `AdminUpdateSelfService.java:46-83` 1:1 跟随。

### `internal/logic/admin/createlogic.go`
- **主要职责**：3 状态机（**注意**：createAdmin 是 public 路径，不经过 JWT，**uid 来自 RPC req 自身**）。
  1. `AdminRpc.CreateAdmin` → `codes.PermissionDenied` → 1016 `ErrorAdminInviteCodeWrong`；`codes.AlreadyExists` → 1001；`codes.Internal` → 5001；default → 1015
  2. 成功 → `CreateAdminResp{Uid}`
- **L2 关键**：**public 路径不带 JWT** —— Go 端 `routes.go` 块 1 的 `createHandler` 不挂 `WithJwt`。
- **可替换**：RAG `AdminCreateService.java:43-74` 1:1 跟随。

### `internal/logic/admin/createinvitelogic.go`
- **主要职责**：3 状态机 + **3 metric label（含 `token_invalid`）**
  1. 解析 `OperatorId` from JWT → 失败 → 1006 + `invite_token_invalid` metric（**唯一有 token_invalid label 的端点**）
  2. `AdminRpc.CreateInviteCode` 失败 → 5001/1003/1015 + `invite_rpc_error` metric
  3. 成功 → `invite_success` metric + `CreateInviteCodeResp{InviteCode, ExpiresAt}`
- **可替换**：RAG `AdminCreateInviteCodeService.java:41-78` 1:1 跟随。

### `internal/logic/admin/banuserlogic.go` / `unbanuserlogic.go`
- **主要职责**：claim uid → 调 `AdminRpc.BanUser/UnBanUser(Uid, OperatorId)` → 失败映射 → 1003/5001/1015 + `ban_rpc_error` / `unban_rpc_error` metric；成功 → `ban_success` / `unban_success` + `BanUserResp{Success}` / `UnBanUserResp{Success}`。
- **L2 关键**：Success 字段**透传** admin-rpc 的 `rpcResp.getSuccess()`（不硬编码 true —— **与 updateSelf/updateUser 不同**，后者是 api 侧自己决定 success=true；ban/unban/delete/reset 透传下游）。
- **可替换**：RAG `AdminBanUserService.java:34-72` / `AdminUnBanUserService.java:34-72` 1:1。

### `internal/logic/admin/getuserlogic.go` / `getuserlistlogic.go`
- **主要职责**：`uid` from query string → 调 `AdminRpc.GetUser/GetUserList` → 失败映射 → 1003/5001/1015（**无 metric**，Go 端不埋点）；成功 → 透传 UserInfo。
- **L2 关键**：GetUser 的 `Found` 字段透传（admin-rpc 默认 false 时不设）；GetUserList 透传 `list + total`。
- **可替换**：RAG `AdminGetUserService.java:34-64` / `AdminListUserService.java:39-66` 1:1。

### `internal/logic/admin/updateuserlogic.go`
- **主要职责**：claim uid → 调 `AdminRpc.UpdateUser` → 失败映射 → 1001/1003/5001/1015 + **`update_db_error` metric**（DRIFT-NEW-005：Go 端 updateuser 用 `db_error` 而非 `rpc_error`，**1:1 保留 label 名**）；成功 → `update_success` + `UpdateUserResp{Success=true, User}`。
- **L2 关键**：Success=true **硬编码**（不透传 rpcResp），原因：admin-rpc 的 `UpdateUserResp.Success` proto3 默认 false，透传会把成功报成失败。`User` 字段由 rpcResp 透传。
- **可替换**：RAG `AdminUpdateUserService.java:50-99` 1:1。

### `internal/logic/admin/deletelogic.go` / `resetuserpasswordlogic.go`
- **主要职责**：claim uid → 调 admin-rpc → 失败映射 → 1003/5001/1015 + `delete_rpc_error` / `reset_rpc_error` metric；成功 → `delete_success` / `reset_success` + `DeleteUserResp{Success}` / `ResetUserPasswordResp{Success}`。
- **L2 关键**：Success 字段**透传** admin-rpc（与 ban/unban 同款）。
- **可替换**：RAG `AdminDeleteUserService.java:34-72` / `AdminResetUserPasswordService.java:34-72` 1:1。

### `internal/middleware/checkblacklistmiddleware.go`
- **主要职责**：从 `Authorization` 头取 token（Bearer prefix trim）→ Redis `ExistsCtx("admin:jwt_blacklist:<token>")`；命中 → **写 401 + `{"code":401,"msg":"登录已失效,请重新登陆"}`**（不通过 envelope 写出）；未命中 → 把 token 写到 `context.WithValue("jwt_token", token)`。
- **L1 强制保留**：401 是 HTTP 层短路，**不进 logic**，**不进 GlobalExceptionHandler** —— 它直接 `w.WriteHeader(401) + w.Write([byte]...)`，是 Go 端 middleware 的特性。
- **L4 关键差异**：RAG 端没有同等 401 短路 —— `AdminJwtAuthInterceptor.preHandle` 检测到 blacklist 命中时**抛 `BusinessException(ERROR_TOKEN_RUNTIME)`**，由 `GlobalExceptionHandler` 映射成 HTTP 200 + body `code=1006`。**这就是 AGENTS.md §6.1 #10 修过的"HTTP 状态码语义变更"**：从 401 → 200/1006。
- **注意**：根据 AGENTS.md §3.4 已记录偏离，401 → 200/1006 视为 wire 契约留档，**不可"顺手修正回旧行为"**。

### `internal/metrics/metrics.go`
- **主要职责**：`AdminApiInterceptCount{Subsystem="admin_api", Name="intercept_total", Labels=["path", "result"]}` + `InitMetrics()`。
- **覆盖 Go 端埋点的 7 个 Logic**（与 admin-rpc 端 25 metric 的区别：api 侧埋点更少，仅针对失败率敏感的端点）：
  - login (3): rpc_error / wrong_password / user_banned / token_error / success（**实际 5**，但 token_error 不独立计，混入 loginInternalError——**RAG 拆为 5**，1:1）
  - banuser (2): rpc_error / success
  - unbanuser (2): rpc_error / success
  - delete (2): rpc_error / success
  - resetuserpassword (2): rpc_error / success
  - updateuser (2): db_error / success
  - invite/create (3): token_invalid / rpc_error / success
- **总计**：3+2+2+2+2+2+3 = 16 个具名 Counter，**RAG 实际 17 个**（login 拆为 rpc_error / internal_error / success，token_error 改为 internal_error）—— **RAG 1:1 调整**：Go 端 5 个 label（含 wrong_password / user_banned / token_error），RAG 拆为 wrong_password / user_banned + internal_error = 3 + success。
- **可替换**：RAG `AdminApiMetrics.java` 完整保留所有 Counter 与 label 名。

### `internal/types/types.go`
- **主要职责**：13 个 Req/Resp DTO + AdminInfo / UserInfo（含 `json:"uid,string"` 把 int64 转字符串输出以避免 JS 精度丢失）。
- **L1 关键**：
  - `LoginReq{Username, Password}`、`LoginResp{Token}`
  - `CreateAdminReq{Username, Password, Email, InviteCode, ExtraInfo}`、`CreateAdminResp{Uid}`
  - `CreateInviteReq{}`（空 struct）、`CreateInviteResp{InviteCode, ExpiresAt}`
  - `GetSelfReq{Uid}`、`GetSelfResp{Admin}`、`UpdateSelfReq{...optional}`、`UpdateSelfResp{Success, Admin}`
  - `GetUserReq{Uid form}`、`GetUserResp{User, Found}`、`GetUserListReq{Page form, PageSize form, Keyword form}`、`GetUserListResp{List, Total}`
  - `BanUserReq{Uid}`、`BanUserResp{Success}`、`UnBanUserReq{Uid}`、`UnBanUserResp{Success}`
  - `DeleteUserReq{Uid}`、`DeleteUserResp{Success}`、`ResetUserPasswordReq{Uid}`、`ResetUserPasswordResp{Success}`
  - `UpdateUserReq{Uid, ..., OperatorId}`、`UpdateUserResp{Success, User}`
  - `LogoutReq{}`（空 struct）、`LogoutResp{Success}`
  - `AdminInfo{Uid string, Username, Email, ExtraInfo}`
  - `UserInfo{Uid string, Username, Email, Status, ExtraInfo}`（**注意**：admin.proto UserInfo 有 status，admin-rpc 不显式 setStatus → DTO UserInfo 同款透传）

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `admincenter.go` 进程装配 | `AdminApiApplication` + Spring 容器 | ABSORB | Spring Boot 启动 |
| `admincenter.go` CORS / HTTP span | `common/observability/HttpMiddlewareFilter`（已 `@Component` 全局注册） | REUSE | 自动拦截 |
| `config/config.go` | `config/AdminApiProperties` | ADAPT | RestConf + AdminAuth + AdminRpc 绑定 |
| `svc/servicecontext.go` AdminRpc 客户端 | `client/AdminRpcClient`（`@GrpcClient("admin-rpc")`） | ADAPT | devh starter 自动注入；Nacos discovery |
| `svc/servicecontext.go` Redis | `StringRedisTemplate`（spring-boot-starter-data-redis） | ADAPT | 自动注入 |
| `routes.go` 块 1（public：login + create） | `interceptor/AdminJwtAuthInterceptor.PUBLIC_PATHS` | ADAPT | 拦截器内部按 path 匹配放行 |
| `routes.go` 块 2（JWT + blacklist） | `interceptor/AdminJwtAuthInterceptor`（JWT 解析 + Redis blacklist） | MERGE | Go 端是 `WithJwt(secret)` + `CheckBlacklistMiddleware` 两个独立 middleware；Java 端合一个 `HandlerInterceptor` |
| `routes.go` 块 3（multipart） | （admin-api 不需要，admin.proto 无 upload 端点） | — | — |
| `routes.go` 前缀 `/admincenter/v1/admin/*` | `@RequestMapping("/admin/v1")` + `@PostMapping("/{op}")` | ADAPT | 已记录偏离（AGENTS.md §3.4） |
| `routes.go` method 混合 | 全部 `@PostMapping` | ADAPT | 已记录偏离（AGENTS.md §3.4） |
| 13 个 handler | `controller/AdminController`（单一，13 个 `@PostMapping` 方法） | MERGE | 13 handler 同质薄适配层，无独立边界依据 |
| `types/types.go` Req/Resp | `dto/*Req` / `dto/*Resp` / `dto/AdminInfo` / `dto/UserInfo`（24 文件） | SPLIT | 按 HTTP 契约分；嵌套字段独立 dto |
| envelope 包装 | `advice/GlobalResponseAdvice` | NEW | RAG users 同款模式 |
| 异常 → 错误码 | `advice/GlobalExceptionHandler`（7 个 `@ExceptionHandler`） | NEW | RAG users 同款 |
| 业务码 1004/1006/1009 → HTTP 401 | HTTP 200 + body code | ADAPT | **已记录偏离**（AGENTS.md §6.1 #10 修复 + §3.4 留档）：从 401 → 200/1006 |
| `middleware/checkblacklistmiddleware.go` | `AdminJwtAuthInterceptor.preHandle`（blacklist 检测在 JWT 验签之后） | MERGE | Java 端合一个 interceptor；黑名单用 `StringRedisTemplate.hasKey` |
| `middleware` 401 短路（不通过 envelope） | 抛 `BusinessException(ERROR_TOKEN_RUNTIME)` 走 envelope | ADAPT | **已记录偏离**（§3.4 + §6.1 #10） |
| `logic/admin/loginlogic.go` | `service/AdminLoginService` | MERGE | 同质 thin；13 合 1 决策**不**适用于 api 侧（api 侧每个 Service 是独立 HTTP 端点） |
| `logic/admin/logoutlogic.go` | `service/AdminLogoutService` | MERGE | 同上 |
| `logic/admin/getselflogic.go` | `service/AdminGetSelfService` + `IdentityResolver.resolveAdmin` | MERGE | admin 域无 status 字段，IdentityResolver 不抛 inactive / status_unavailable |
| `logic/admin/updateselflogic.go` | `service/AdminUpdateSelfService` | MERGE | — |
| `logic/admin/createlogic.go` | `service/AdminCreateService` | MERGE | public 路径不走 JWT |
| `logic/admin/createinvitelogic.go` | `service/AdminCreateInviteCodeService`（含 `inviteTokenInvalid` metric） | MERGE | — |
| `logic/admin/banuserlogic.go` | `service/AdminBanUserService` | MERGE | — |
| `logic/admin/unbanuserlogic.go` | `service/AdminUnBanUserService` | MERGE | — |
| `logic/admin/getuserlogic.go` | `service/AdminGetUserService` | MERGE | 透传 `Found` 字段 |
| `logic/admin/getuserlistlogic.go` | `service/AdminListUserService` | MERGE | 透传 `list + total` |
| `logic/admin/updateuserlogic.go` | `service/AdminUpdateUserService`（含 `updateDbError` metric） | MERGE | Success 硬编码 true（防 proto3 默认 false） |
| `logic/admin/deletelogic.go` | `service/AdminDeleteUserService` | MERGE | Success 透传 |
| `logic/admin/resetuserpasswordlogic.go` | `service/AdminResetUserPasswordService` | MERGE | Success 透传 |
| admin proto 转换 helper | `service/AdminApiSupport.fromProto(AdminInfo/UserInfo)` | MERGE | 跨 13 Service 共享 |
| `svc/identity/resolve.go` 身份契约 | `service/IdentityResolver`（3 sentinel：InvalidClaim / AdminNotFound / IdentityMismatch） | ADAPT | admin 域无 status 字段 → 比 user-api 少 2 sentinel |
| `metrics/metrics.go` | `metrics/AdminApiMetrics`（17 Counter） | ADAPT | Micrometer；与 Go 端 1:1 |
| helper uid 解析（admin-api 端无 Go 对应，业务推导） | `service/AdminApiSupport.parseUid`（抛 `BusinessException(1017)`） | NEW | **AGENTS.md §6.1 #9 已修复**：原"静默转 0L"会掩盖坏请求 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/users/admin/api/
├── AdminApiApplication.java
├── advice/
│   ├── GlobalResponseAdvice.java
│   └── GlobalExceptionHandler.java
├── client/
│   └── AdminRpcClient.java
├── config/
│   ├── AdminApiProperties.java
│   └── WebMvcConfig.java
├── controller/
│   └── AdminController.java
├── interceptor/
│   └── AdminJwtAuthInterceptor.java
├── dto/
│   ├── AdminInfo.java
│   ├── UserInfo.java
│   ├── LoginReq.java
│   ├── LoginResp.java
│   ├── LogoutReq.java
│   ├── LogoutResp.java
│   ├── GetSelfReq.java
│   ├── GetSelfResp.java
│   ├── UpdateSelfReq.java
│   ├── UpdateSelfResp.java
│   ├── CreateAdminReq.java
│   ├── CreateAdminResp.java
│   ├── CreateInviteCodeReq.java
│   ├── CreateInviteCodeResp.java
│   ├── BanUserReq.java
│   ├── BanUserResp.java
│   ├── UnBanUserReq.java
│   ├── UnBanUserResp.java
│   ├── GetUserReq.java
│   ├── GetUserResp.java
│   ├── GetUserListReq.java
│   ├── GetUserListResp.java
│   ├── UpdateUserReq.java
│   ├── UpdateUserResp.java
│   ├── DeleteUserReq.java
│   ├── DeleteUserResp.java
│   ├── ResetUserPasswordReq.java
│   └── ResetUserPasswordResp.java
├── metrics/
│   └── AdminApiMetrics.java
└── service/
    ├── AdminApiSupport.java
    ├── IdentityResolver.java
    ├── AdminLoginService.java
    ├── AdminLogoutService.java
    ├── AdminGetSelfService.java
    ├── AdminUpdateSelfService.java
    ├── AdminCreateService.java
    ├── AdminCreateInviteCodeService.java
    ├── AdminBanUserService.java
    ├── AdminUnBanUserService.java
    ├── AdminGetUserService.java
    ├── AdminListUserService.java
    ├── AdminUpdateUserService.java
    ├── AdminDeleteUserService.java
    └── AdminResetUserPasswordService.java
```

### 逐文件说明

**`AdminApiApplication.java`**
- 所属包：`com.rag.users.admin.api`
- 主要职责：Spring Boot 入口，`@SpringBootApplication(scanBasePackages={"com.rag.users.admin.api","com.rag.common"})` + `@EnableDiscoveryClient`
- 承载 Go 职责：`admincenter.go` 进程装配
- 为什么独立：4 个 api/rpc 模块统一入口风格
- 依赖：`config/AdminApiProperties`

**`advice/GlobalResponseAdvice.java`**
- 所属包：`com.rag.users.admin.api.advice`
- 主要职责：`@RestControllerAdvice implements ResponseBodyAdvice<Object>`；`beforeBodyWrite` 中判 `body instanceof Response` 直接放行（**关键**：避免二次包裹 `@ExceptionHandler` 的 `ResponseEntity`，AGENTS.md §6.1 #1 已修复）
- 承载 Go 职责：每个 handler 末尾手动 `httpx.OkJson(w, envelope{...})`
- 为什么独立：与 user-api 同款；advice pattern 是 Spring 标准的 envelope 拦截位
- 依赖：`common/response/Response`、`users-common/errmsg/ErrorCode`

**`advice/GlobalExceptionHandler.java`**
- 所属包：`com.rag.users.admin.api.advice`
- 主要职责：`@RestControllerAdvice` + 7 个 `@ExceptionHandler`：
  - `BusinessException` → 业务码 + HTTP 200（**强制一律 OK**，body code 表达成败）
  - `StatusRuntimeException` → gRPC code → 业务码（INTERNAL→5001/NOT_FOUND→1003/ALREADY_EXISTS→1001/INVALID_ARGUMENT→500/PERMISSION_DENIED→1016/UNAUTHENTICATED→1006/default→1015）+ HTTP 200
  - 3 个 `IdentityResolver.Err*Exception` → 业务码 + HTTP 200
  - 框架级 `HttpMessageNotReadableException` → 400；`HttpRequestMethodNotSupportedException` → 405；`HttpMediaTypeNotSupportedException` → 415
  - 兜底 `Exception` → 500 + 5001
- 承载 Go 职责：每个 logic 的错误返回 + handler 层 catch
- 为什么独立：与 user-api 同款；advice 是 Spring 标准的异常拦截位
- 依赖：`common/response/Response`、`users-common/errmsg/ErrorCode`、`users/common/exception/BusinessException`、`service/IdentityResolver`

**`client/AdminRpcClient.java`**
- 所属包：`com.rag.users.admin.api.client`
- 主要职责：`@Component`；`@GrpcClient("admin-rpc") private AdminServiceBlockingStub` + getter
- 承载 Go 职责：`svc/servicecontext.go` 的 `AdminRpc adminservice.AdminService`
- 为什么独立：客户端 stub 必须独立成 Bean（devh starter 注入约束）；13 个 Service 共享
- 依赖：`com.rag.users.admin.proto.AdminServiceGrpc`（生成物）

**`config/AdminApiProperties.java`**
- 所属包：`com.rag.users.admin.api.config`
- 主要职责：`@ConfigurationProperties(prefix="admin-api")` + `AdminAuth{AccessSecret, AccessExpire}`
- 承载 Go 职责：`config/config.go` 的 `AdminAuth`
- 为什么独立：配置绑定独立关注点
- 依赖：无（纯 POJO）

**`config/WebMvcConfig.java`**
- 所属包：`com.rag.users.admin.api.config`
- 主要职责：`WebMvcConfigurer.addInterceptors` → 注册 `AdminJwtAuthInterceptor` 到 `/admin/v1/**`
- 承载 Go 职责：`routes.go` 块 2 的 `WithMiddlewares({CheckBlacklistMiddleware})` + `WithJwt(secret)`
- 为什么独立：拦截器注册与路径匹配是独立配置关注点
- 依赖：`interceptor/AdminJwtAuthInterceptor`

**`controller/AdminController.java`**
- 所属包：`com.rag.users.admin.api.controller`
- 主要职责：`@RequestMapping("/admin/v1")` + 13 个 `@PostMapping` 方法：login / create / logout / getself / update / updateuser / delete / resetuserpassword / banuser / unbanuser / getuser / getuserlist / invite/create
- 承载 Go 职责：`routes.go` 13 条路由 + `handler/admin/*handler.go`（13 个）合并
- 为什么独立：13 个 handler 同质（parse → Service → 返 DTO），无独立生命周期；拆开只会制造 13 个单方法类
- 依赖：13 个 `service/*Service`

**`interceptor/AdminJwtAuthInterceptor.java`**
- 所属包：`com.rag.users.admin.api.interceptor`
- 主要职责：`HandlerInterceptor.preHandle`
  - public paths（`/admin/v1/login` + `/admin/v1/create`）→ 直接放行
  - 缺失 Authorization 或 Bearer 后 token 空 → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - JWT 验签（`Jwts.parser().verifyWith(key)`）失败 → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - claim `userId` 缺失 / ≤ 0 → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - Redis blacklist `admin:jwt_blacklist:<token>` 存在 → 抛 `BusinessException(ERROR_TOKEN_RUNTIME)`
  - 通过 → 写 attribute `userId` (Long) + `jwt_token` (String)
- 承载 Go 职责：`routes.go` 块 2 的 `WithJwt(secret)` + `middleware/checkblacklistmiddleware.go`
- 为什么独立：必须独立 Bean（密钥/路径/前缀与 user-api 不同）
- 依赖：`config/AdminApiProperties`、`StringRedisTemplate`、`users-common/jwt/JwtUtil`（**含 `padToHmacKeySize` 补齐 15B → 32B**）

**`dto/AdminInfo.java`**
- 所属包：`com.rag.users.admin.api.dto`
- 主要职责：嵌套 DTO `uid (string) / username / email / extra_info`
- 承载 Go 职责：`types/types.go` 的 `AdminInfo`
- 为什么独立：与 UserInfo 区分（同名字段在 admin.proto + user.proto 都存在）
- 依赖：Jackson 注解（snake_case）

**`dto/UserInfo.java`**
- 所属包：`com.rag.users.admin.api.dto`
- 主要职责：嵌套 DTO `uid (string) / status / username / email / extra_info`（admin 域 UserInfo 包含 status，但**admin-api 不暴露 score** —— 与 admin-rpc 同款）
- 承载 Go 职责：`types/types.go` 的 `UserInfo`（admin 域，含 status 字段；score 字段由 admin.proto 定义但 admin-rpc 不显式 set）
- 为什么独立：与 dto 包内 AdminInfo 同名字段差异；用全限定名 `com.rag.users.admin.proto.UserInfo` 区分 proto
- 依赖：Jackson

**`dto/*Req` / `dto/*Resp`**（22 个）
- 所属包：`com.rag.users.admin.api.dto`
- 主要职责：13 对 HTTP 请求/响应 DTO（`uid` 字段统一 `@JsonSerialize` 为字符串避免精度丢失）
- 承载 Go 职责：`types/types.go` 全部（13 对）
- 为什么独立：按 HTTP 契约与字段校验要求一一拆分；空 struct（LogoutReq / GetSelfReq / CreateInviteCodeReq）保留为 `@Data` 空类（Jackson 支持）
- 依赖：Jackson

**`metrics/AdminApiMetrics.java`**
- 所属包：`com.rag.users.admin.api.metrics`
- 主要职责：`@Component` 注入 `MeterRegistry`；17 个 Counter：
  - login (3): rpc_error / internal_error / success（**RAG 拆 5 → 3**，把 wrong_password / user_banned 去掉 —— 但 admin 端 login logic 没有这两个分支；admin 端密码错走 UNAUTHENTICATED → api 侧 1002 单一处理）
  - banuser (2): rpc_error / success
  - unbanuser (2): rpc_error / success
  - delete (2): rpc_error / success
  - resetuserpassword (2): rpc_error / success
  - updateuser (2): db_error / success
  - invite/create (3): token_invalid / rpc_error / success
- 承载 Go 职责：`metrics/metrics.go` 的 `AdminApiInterceptCount` CounterVec（admin-api 17 vs admin-rpc 25 的差异是合理的：api 端只埋关键失败率敏感端点）
- 为什么独立：指标有独立的变化原因
- 依赖：Micrometer

**`service/AdminApiSupport.java`**
- 所属包：`com.rag.users.admin.api.service`
- 主要职责：`@Component`
  - `parseUid(String)` → 抛 `BusinessException(ERROR_REQUEST_PARAM)` 解析失败（**AGENTS.md §6.1 #9 修复**）
  - `extractAdminId(HttpServletRequest)` → 委派 `IdentityResolver.claimedUID`
  - `codeFromRPCError(Status.Code, fallback)` → 业务码 fallback 映射
  - `fromProto(AdminInfo proto)` / `fromProto(UserInfo proto)` → DTO 转换
- 承载 Go 职责：cross-Service helper 聚合（**L4 推导**：Go 端各 logic 内联 helper 无独立文件）
- 为什么独立：跨 13 Service 共享的边界适配工具
- 依赖：`service/IdentityResolver`、`common/exception/BusinessException`

**`service/IdentityResolver.java`**
- 所属包：`com.rag.users.admin.api.service`
- 主要职责：`@Component`
  - L1 常量 `AuthorityID = "rtw.identity"` / `PlatformTenantID = "platform"`
  - `claimedUID(HttpServletRequest)` → 抛 `ErrInvalidClaimException`
  - `resolveAdmin(HttpServletRequest, AdminServiceBlockingStub)` → 调 admin-rpc getSelf → 3 sentinel exception：
    - `ErrInvalidClaimException` → 1006
    - `ErrAdminNotFoundException` → 1003
    - `ErrIdentityMismatchException` → 5001
  - `resolveSubjectRef(...)` → `SubjectRef{authorityId, tenantId, subjectId=Long.toString(uid)}`
- 承载 Go 职责：user-api 端 `identity/resolve.go`（admin 域无 status 字段 → 3 sentinel vs user 域 5 sentinel）
- 为什么独立：身份契约独立可测，与 13 Service 解耦
- 依赖：`com.rag.users.admin.proto.*`

**`service/AdminLoginService.java`**
- 所属包：`com.rag.users.admin.api.service`
- 主要职责：`@Service`
  - 调 `AdminRpc.Login` → 失败 gRPC code 映射（**特别注意 UNAUTHENTICATED → 1002**；NOT_FOUND → 1003；INTERNAL → 5001；default → 1015）+ `loginRpcError` metric
  - `JwtUtil.getToken(...)` 失败 → 5001 + `loginInternalError` metric
  - 成功 → `loginSuccess` + `LoginResp{Token}`
- 承载 Go 职责：`logic/admin/loginlogic.go`
- 为什么独立：单端点用例，独立的 gRPC 调用方 + JWT 签发方
- 依赖：`client/AdminRpcClient`、`config/AdminApiProperties`、`metrics/AdminApiMetrics`、`users-common/jwt/JwtUtil`

**`service/AdminLogoutService.java` / `service/AdminGetSelfService.java` / ...**（其余 11 个 Service）
- 模式同上：每个对应一个 `@PostMapping` 端点；调 `AdminRpc.<Op>` → gRPC code 映射 + metric → 返 DTO
- 13 Service 各自独立原因：每个是不同 HTTP 端点的 thin wrapper，下游 gRPC 方法名一一对应；无独立边界但有明确 1:1 路由绑定

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `admincenter.go` | `AdminApiApplication` | ABSORB | 进程装配 |
| `admincenter.go`（CORS） | `common/observability/HttpMiddlewareFilter` | REUSE | 全局 Filter 已注册 |
| `config/config.go` | `config/AdminApiProperties` | ADAPT | 绑定机制换 Spring |
| `svc/servicecontext.go`（AdminRpc） | `client/AdminRpcClient` | ADAPT | devh `@GrpcClient` 替代 |
| `svc/servicecontext.go`（Redis） | `StringRedisTemplate` | ABSORB | spring-data-redis 自动 |
| `routes.go` 块 1（public） | `AdminJwtAuthInterceptor.PUBLIC_PATHS` | ADAPT | 拦截器按 path 匹配 |
| `routes.go` 块 2（JWT + blacklist） | `interceptor/AdminJwtAuthInterceptor` | MERGE | Go 两个 middleware 合一个 Java interceptor |
| `routes.go` 前缀 `/admincenter/v1` | `@RequestMapping("/admin/v1")` | ADAPT | **已记录偏离**（§3.4） |
| `routes.go` method 混合 | 全部 `@PostMapping` | ADAPT | **已记录偏离**（§3.4） |
| `handler/admin/*`（13 个） | `controller/AdminController`（单一） | MERGE | 同质薄适配层 |
| `types/types.go`（AdminInfo / UserInfo） | `dto/AdminInfo` / `dto/UserInfo` | ADAPT | snake_case 序列化 |
| `types/types.go`（13 对 Req/Resp） | `dto/*Req` / `dto/*Resp` | SPLIT | 按契约独立 |
| `httpx.OkJson` 信封 | `advice/GlobalResponseAdvice` | ADAPT | Spring advice 等价 |
| 各 logic 错误返回 | `advice/GlobalExceptionHandler` | ADAPT | gRPC code → 业务码 |
| `middleware/checkblacklistmiddleware.go` | `AdminJwtAuthInterceptor.preHandle` blacklist 分支 | MERGE | 同上合并 |
| 401 短路 | `BusinessException(ERROR_TOKEN_RUNTIME)` | ADAPT | **已记录偏离**（§6.1 #10 + §3.4） |
| `logic/admin/loginlogic.go` | `service/AdminLoginService` | MERGE | 单端点用例 |
| `logic/admin/logoutlogic.go` | `service/AdminLogoutService` | MERGE | 同上 |
| `logic/admin/getselflogic.go` | `service/AdminGetSelfService` | MERGE | 同上 |
| `logic/admin/updateselflogic.go` | `service/AdminUpdateSelfService` | MERGE | 同上 |
| `logic/admin/createlogic.go` | `service/AdminCreateService` | MERGE | public 端点 |
| `logic/admin/createinvitelogic.go` | `service/AdminCreateInviteCodeService` | MERGE | 含 token_invalid metric |
| `logic/admin/banuserlogic.go` | `service/AdminBanUserService` | MERGE | — |
| `logic/admin/unbanuserlogic.go` | `service/AdminUnBanUserService` | MERGE | — |
| `logic/admin/getuserlogic.go` | `service/AdminGetUserService` | MERGE | — |
| `logic/admin/getuserlistlogic.go` | `service/AdminListUserService` | MERGE | — |
| `logic/admin/updateuserlogic.go` | `service/AdminUpdateUserService` | MERGE | 含 db_error metric |
| `logic/admin/deletelogic.go` | `service/AdminDeleteUserService` | MERGE | — |
| `logic/admin/resetuserpasswordlogic.go` | `service/AdminResetUserPasswordService` | MERGE | — |
| admin proto 转换 | `service/AdminApiSupport.fromProto(...)` | MERGE | helper 聚合 |
| `identity/resolve.go`（admin 域身份契约） | `service/IdentityResolver`（3 sentinel） | ADAPT | admin 无 status → 少 2 sentinel |
| `metrics/metrics.go` | `metrics/AdminApiMetrics` | ADAPT | Micrometer；17 Counter |
| —（admin-api 端无 Go 对应） | `advice/GlobalResponseAdvice` | NEW | Spring advice 模式 |
| —（admin-api 端无 Go 对应） | `advice/GlobalExceptionHandler` | NEW | 统一异常映射 |
| —（admin-api 端无 Go 对应） | `config/WebMvcConfig` | NEW | 拦截器注册 |
| —（admin-api 端无 Go 对应） | `interceptor/AdminJwtAuthInterceptor` | NEW | 合并 JWT + blacklist |
| —（admin-api 端无 Go 对应） | `service/AdminApiSupport.parseUid` | NEW | 业务推导（uid 字符串解析失败抛 1017） |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 13 条路由的 method/path/中间件分组（除已记录偏离外）；全部业务码 500/1001/1002/1003/1004/1005/1006/1009/1011/1015/1016/1017/5001；JWT claim `userId`；Redis blacklist key 前缀 `admin:jwt_blacklist:` |
| L2 业务语义有承载位置 | ✅ gRPC code → 业务码映射表（admin-api 比 user-api 多两条：PERMISSION_DENIED→1016 / UNAUTHENTICATED→1002）；`inviteTokenInvalid` 唯一 token_invalid label；`updateDbError` 唯一 db_error label（DRIFT-NEW-005）；Success 字段透传 vs 硬编码区分（ban/unban/delete/reset 透传，updateSelf/updateUser 硬编码 true） |
| L3 技术语义有承载方案 | ✅ JWT secret 短密钥补齐（`padToHmacKeySize`）→ admin-api 默认配置起得来；Redis blacklist `fail-open` 策略（Redis 故障不阻塞）；public path 不走 JWT |
| 业务分支与异常路径已纳入 | ✅ 13 个 RPC 全部含 gRPC code 分支 + metric 触发点 + 异常映射；`UNAUTHENTICATED → 1002` 修复了密码错报 1015"服务繁忙"的 bug（AGENTS.md §6.1 #8） |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；CORS → `common/observability`；`@GrpcClient` 注入 stub |
| 复用组件已明确 | ✅ `common/response/Response`、`common/exception/BusinessException`、`common/observability.HttpMiddlewareFilter`、`users-common/jwt/JwtUtil`、`users-common/errmsg/ErrorCode` |
| 新增文件有职责依据 | ✅ `AdminJwtAuthInterceptor`（合并 Go 两个 middleware）、`GlobalResponseAdvice`（Spring advice 模式）、`GlobalExceptionHandler`（统一异常映射）、`IdentityResolver`（admin 域 3 sentinel）、`AdminApiSupport.parseUid`（业务推导）—— 均非"Go 有同名文件故建" |
| 合并有组织依据 | ✅ 13 logic → 13 Service（一一对应，单端点用例独立而非堆进大 Service）；handler/admin 13 个 → `AdminController` 单一（同质薄适配层）；JWT + blacklist 两个 middleware → `AdminJwtAuthInterceptor` 单一 |
| 拆分可解释 | ✅ DTO 按契约独立（24 个文件）；advice / interceptor / client / config 各自独立变化原因 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `ServiceContext.java` / `Types.java` / `Server.java` / `Middleware.java` |
| 已记录偏离保留 | ✅ `/admin/v1` 前缀（Go `/admincenter/v1`）；统一 POST（Go 混合）；HTTP 200 + body code 表达成败（Go 401 + body code）—— 不"顺手修正回旧行为" |
| **P0 `AdminJwtAuthInterceptor` WeakKeyException 已修复** | ✅ `JwtUtil.padToHmacKeySize(properties.getAdminAuth().getAccessSecret())` 已落地（`AdminJwtAuthInterceptor.java:67`），admin-api 默认配置可启动；与 `JwtUtil.getToken` 签发共用同一份 padding，签发/验签 key 字节对齐 |
| **AGENTS.md §6.1 全部已修问题在本文件覆盖** | ✅ #1 ResponseAdvice 二次包裹（`GlobalResponseAdvice` 判 `instanceof Response` 放行）；#8 UNAUTHENTICATED → 1002（`AdminLoginService` 显式映射）；#9 uid 解析失败抛 1017（`AdminApiSupport.parseUid`）；#10 HTTP 状态码语义（已记录偏离） |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **路由前缀与方法已偏离 Go 端（已留档）** | Go 为 `/admincenter/v1/admin/*`（POST/GET/DELETE 混合）；RAG 为 `/admin/v1/*`（统一 POST）。属 wire 契约变更，AGENTS.md §3.4 已留档；**不可"修正回旧行为"** |
| 2 | **`AdminApiMetrics` 17 Counter 与 Go 端 1:1 但有差异** | Go 端 `AdminApiInterceptCount{path,result}` 含 16 个 label 组合；RAG 17 个具名 Counter。差异原因：admin 端 login 只有 UNAUTHENTICATED → 1002（无 wrong_password / user_banned / token_error 三分支）—— **这是 admin 与 user 域行为差异的忠实记录**，不是缺陷，但需要在 review 时确认 metric 名 `admin_api_intercept_total` 1:1 跟随 |
| 3 | **`AdminApiSupport.codeFromRPCError` 与 `GlobalExceptionHandler.mapRPCStatusToCode` 重复** | 两处都做 gRPC code → 业务码映射，但**逻辑不全等**：Service 内部用 `BusinessException(精确业务码)` 优先；advice 仅做兜底。**现状**：advice 的映射表只在 Service 未捕获的 StatusRuntimeException 透传时生效（极少数场景）。**风险**：未来新增 Service 时若漏写某个 gRPC code 分支，会 fallback 到 advice 的 default 映射 → 业务码语义与 Go 端可能漂移。**建议**：可合并或明确分工文档化 |
| 4 | **`AdminController` 13 个 `@RequestBody(required=false)` 未改** | Go 端 `LogoutReq` / `GetSelfReq` / `CreateInviteCodeReq` 是空 struct，handler 不要求 body；RAG 当前**全部 `@RequestBody`（默认 required=true）**。**`/admin/v1/logout` 调用方若发空 body → 400** —— 与 Go 端语义漂移（**AGENTS.md §6.2 P2 已记**） |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| Flyway schema 归属 | 与 admin-rpc / user-rpc 共享 `flyway_schema_history` 版本号（AGENTS.md §6.3 问题 1）—— admin-api 不直接涉及，但 admin-rpc 启动失败会牵连 admin-api |
| admin-rpc 是否应改为转发 user-rpc | admin-rpc 直写 `users_user` 表（AGENTS.md §6.3 问题 2）—— admin-api 端无影响，但若 admin-rpc 架构调整，admin-api Service 需重写 |
| `AdminApiSupport.codeFromRPCError` 是否要废弃 | 见不通过项 #3 |
| `AdminController` 13 个端点是否需要拆分 | 当前 13 Service 各自独立原因已说，但若未来业务增长（每个 Service 超过 200 行），可考虑按业务领域拆为多个 Controller（LoginController / UserMgmtController / BanController）。**当前 13 端点同一变化原因（admin HTTP 网关）**，保留单 Controller 是合理的 |
