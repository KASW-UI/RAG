# message-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/message/api`
> 目标根包：`com.rag.message.message.api`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/message/message-api` **只有 `pom.xml`，无 Java 代码**。下面 §12.5 的理论文件树是基于同仓库已落地的一致性依据推导：

| 参照 | 提供的一致性依据 |
|---|---|
| `service/users/user/user-api` | `@RestController` + `@RequestMapping("/user/v1")`；`advice/{GlobalResponseAdvice,GlobalExceptionHandler}`；`interceptor/JwtAuthInterceptor`；`config/{UserApiProperties,WebMvcConfig}`；`client/UserRpcClient`；`service/UserApiSupport`（归一化 + proto→DTO 投影）；`service/IdentityResolver` |
| `service/users/admin/admin-api` | admin-secret 短密钥下 Bean 创建的 `padToHmacKeySize` 修复；`interceptor/AdminJwtAuthInterceptor`（独立 key 前缀 `admin:jwt_blacklist:`）；`config/AdminApiProperties` |
| `service/message/message-rpc` | rpc 端 9 个方法名（`SendChatMessage` / `ListConversations` 等）+ `ConversationItem` / `ChatMessageItem` / `NotificationItem` proto；`MessageErrorCode` 21 个业务码常量 |

### 已有组件（跨模块可复用）

| 组件 | 位置 | 承担 |
|---|---|---|
| `com.rag.common.response.Response` | `service/common` | HTTP 统一响应信封 `{code, msg, data}` |
| `com.rag.common.exception.BusinessException` | `service/common` | 业务异常基类 |
| `com.rag.common.observability.HttpMiddlewareFilter` | `service/common` | HTTP 可观测性 filter（已 `@Component` 全局注册，APM span） |
| `com.rag.common.snowflake.Snowflake` + `SnowflakeBean` | `service/common` | 当前 api 模块无 ID 生成需求 |
| `com.rag.message.message.common.errmsg.*` | `message-common` | `MessageErrorCode` / `MessageErrorMessages` / `MessageGrpcError`（含 `bizCodeFromError` 反查） |
| `com.rag.message.message.proto.MessageServiceGrpc` | `message-proto`（生成物） | `@GrpcClient("message-rpc") MessageServiceBlockingStub` |
| `com.rag.users.common.jwt.JwtUtil` | `users-common` | `getToken` + `padToHmacKeySize`（⚠️ 短密钥补齐——见 §12.7 #1） |

### 已确认的框架机制

- `@RestControllerAdvice` + `ResponseBodyAdvice<Object>`（**注意 §3.3 idempotency**：`beforeBodyWrite` 必须判 `body instanceof Response` 不二次包裹 `@ExceptionHandler` 的 `ResponseEntity`，**否则所有错误响应顶层 `code=200`** — 与 user-api 锁定行为一致，参见 `users/user-api/GlobalResponseAdvice.java:51-61`）
- `HandlerInterceptor` + `WebMvcConfigurer`（**双拦截器模式**：本组件有 user + admin 两套 JWT 密钥，必须独立 Bean、独立 path pattern）
- `@ConfigurationProperties` + `@NestedConfigurationProperty`（**双 Auth 段**：`UserAuth` + `AdminAuth` 都要绑定）
- `@GrpcClient("message-rpc")` 自动注入 BlockingStub（devh grpc-client-spring-boot-starter + Nacos discovery）
- `spring-boot-starter-data-redis`（`StringRedisTemplate` 做 JWT blacklist）
- `spring-boot-starter-actuator` + Micrometer（api 侧 3 个 metric：`message_api_request_total` / `reject_total` / `request_duration_seconds`）

### 当前 Component 可以直接复用的能力

- **HTTP 信封 + advice**：`common/response/Response` + `advice/GlobalResponseAdvice` + `advice/GlobalExceptionHandler` 三件套已定型，**直接复刻 user-api/admin-api 同款两份**
- **JWT 拦截器模式**：借鉴 `users/user-api/JwtAuthInterceptor` + `users/admin-api/AdminJwtAuthInterceptor`——**双拦截器**，因 message-api 路由表同时挂 user JWT 与 admin JWT
- **gRPC 客户端**：`@GrpcClient("message-rpc")` 自动注入，无需手写 stub
- **ErrorCode 反查**：rpc 端用 biz code 嵌 gRPC status description，`message-common/MessageGrpcError.bizCodeFromError(Throwable)` 反查

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| **HTTP path 前缀** | Go 端两组：`/message/v1`（user group，JWT 用 `UserAuth.AccessSecret`）+ `/message/v1/admin`（admin group，JWT 用 `AdminAuth.AccessSecret`）。RAG 端 message-api 尚未决策，**按 Go 契约一字不差推导**：保留 `/message/v1` 与 `/message/v1/admin` 双前缀；若跟随 RAG users 惯例改为 `/message/v1` + `/admin/v1` 属 wire 变更，**需用户拍板**（AGENTS.md §3.4 已记录偏离惯例） |
| **HTTP method** | Go 为 POST/GET 混合（`/unread` 是 GET，其余 POST）；RAG 的 users 统一 POST（AGENTS.md §3.4 留档）。本文件**统一为 POST**（`/unread` 也改为 POST）—— 这是与已落地的 RAG 一致性 |
| **双 Auth secret 是同一进程共享还是分进程** | Go 端 `Config{UserAuth, AdminAuth, MessageRpc}` 三个配置段在**同一 yaml** 里 (`etc/messagecenter.yaml`)，意味着 message-api **单进程** 同时托管 user 与 admin 两套 JWT。RAG 端按 Go 等价推导为**单进程双拦截器**（vs user-api / admin-api 是各一进程单拦截器） |
| **JWT claim `userId`** | Go 端 `helper.go:UserIDFromContext` 从 ctx 取 `"userId"`（注意：小写 u、驼峰式）。**RAG 端必须保留此 exact name**，否则 `UserIDFromContext` 等价代码取不到。users/user-api 已用 `"userId"`（参见 `users/user-api/JwtAuthInterceptor.java:132`） |
| **`AdminAuth.AccessSecret="yU-hCm)2*?NQmWG"`（15 字节）** | 与 admin-api 共一份密钥。**RAG 端必须调 `JwtUtil.padToHmacKeySize` 补齐到 32 字节**——jjwt 强制 ≥256 bits（HS256 / RFC 7518）。Go 侧 golang-jwt 不校验长度故无问题；Java 侧 Bean 创建即抛 `WeakKeyException` |
| **`MessageAdminSender` vs `MessageSender` role 取值** | Go 端：user group 永远 `SenderRole.USER`、admin group 永远 `SenderRole.ADMIN`（写死在 api logic）。**RAG 端一样要在对应 Service 层硬编码**；不能从 JWT 推导 |

---

## 12.2 第二部分：Go 原始文件树

```
service/message/api/
├── messagecenter.go                                 # main 入口：flag、config、metrics、cors、httpx.SetOkHandler/ErrorHandler、handler.RegisterHandlers
├── etc/messagecenter.yaml                           # 配置：RestConf + UserAuth + AdminAuth + MessageRpc
├── internal/
│   ├── config/config.go                             # Config：rest.RestConf + UserAuth + AdminAuth + MessageRpc zrpc.RpcClientConf
│   ├── svc/servicecontext.go                        # ServiceContext：Config + MessageRpc (zrpc client)
│   ├── handler/
│   │   ├── routes.go                                # 2 个 AddRoutes 块：user group（/message/v1 + UserAuth）+ admin group（/message/v1/admin + AdminAuth）
│   │   ├── message/                                 # user group：8 个 handler（每个 ~30 行 httpx.OkJson 包装）
│   │   │   ├── getconversationmessageshandler.go
│   │   │   ├── getunreadsummaryhandler.go
│   │   │   ├── listconversationshandler.go
│   │   │   ├── listnotificationshandler.go
│   │   │   ├── markallnotificationsreadhandler.go
│   │   │   ├── markconversationreadhandler.go
│   │   │   ├── marknotificationreadhandler.go
│   │   │   └── senduserchatmessagehandler.go
│   │   └── messageadmin/                            # admin group：5 个 handler
│   │       ├── getadminconversationmessageshandler.go
│   │       ├── listadminconversationshandler.go
│   │       ├── markadminconversationreadhandler.go
│   │       ├── sendadminchatmessagehandler.go
│   │       └── sendadminnotificationhandler.go
│   ├── logic/
│   │   ├── message/                                 # user group：8 个 logic
│   │   │   ├── getconversationmessageslogic.go
│   │   │   ├── getunreadsummarylogic.go
│   │   │   ├── listconversationslogic.go
│   │   │   ├── listnotificationslogic.go
│   │   │   ├── markallnotificationsreadlogic.go
│   │   │   ├── markconversationreadlogic.go
│   │   │   ├── marknotificationreadlogic.go
│   │   │   └── senduserchatmessagelogic.go
│   │   ├── messageadmin/                            # admin group：5 个 logic
│   │   │   ├── getadminconversationmessageslogic.go
│   │   │   ├── listadminconversationslogic.go
│   │   │   ├── markadminconversationreadlogic.go
│   │   │   ├── sendadminchatmessagelogic.go
│   │   │   └── sendadminnotificationlogic.go
│   │   └── shared/helper.go                         # UserIDFromContext + RPCError + To*Resp 投影 + AdminNotificationKind
│   ├── metrics/metrics.go                           # APIRequestCounter / APIRejectCounter / APIRequestDuration
│   └── types/types.go                               # 14 个 Req/Resp DTO + ChatMessageItem / ConversationItem / NotificationItem + EmptyReq + PageReq
```

---

## 12.3 第三部分：Go 文件职责分析

### `messagecenter.go`
- **主要职责**：进程入口。`flag.Parse()` + `metrics.InitMetrics()` + `conf.MustLoad(*configFile, &c)` + `logx.MustSetup(c.Log)` + `logger.Init(c.Name)` + `observability.DisableNativeRestTimeout(&c.RestConf)` + `rest.MustNewServer(c.RestConf)` + `server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))` + `httpx.SetOkHandler(...)` + `httpx.SetErrorHandler(...)` + `handler.RegisterHandlers(server, ctx)` + `server.Start()`。
- **关键调用关系**：`observability.DisableNativeRestTimeout(&c.RestConf)` → `server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))` —— Go 端把 HTTP 中间件装配到 rest server。
- **业务语义**：**3 处关键 envelope 与 error 处理约定**：
  1. **`httpx.SetOkHandler(ctx, v)`**：所有 `httpx.OkJson` 走这个工厂，把 `v` 包成 `&response.Response{Code: messagecommon.Success (200), Msg: GetErrMsg(200), Data: v}`——**L1 外部契约**：HTTP body 永远是 `{code:200, msg:"OK", data:<返回值>}`。
  2. **`httpx.SetErrorHandler(err)`**：
     - `errors.As(err, &codeErr)` 匹配 → 用 `codeErr.Code` + `codeErr.Msg`（透传业务码）
     - 否则 `code := messagecommon.ErrorServerCommon (5001)` 兜底
     - HTTP 状态码恒为 `200`（**注意**：Go 端业务失败也回 HTTP 200，业务码在 body 里 —— **与 RAG 的 user-api/admin-api GlobalExceptionHandler.decideStatus 决策一致**）
  3. **JSON 选项绑定**：从 Go 的 `rest.RestConf` 拿到 host/port + cors 设置。
- **可替换**：Spring Boot 启动 + `HttpMiddlewareFilter` 全局注册 envelope wrapper + error handler。

### `internal/config/config.go`
- **主要职责**：
  ```go
  type Config struct {
      rest.RestConf                                                  // host/port/cors 等
      UserAuth     struct{ AccessSecret string; AccessExpire int64 } // user-side JWT
      AdminAuth    struct{ AccessSecret string; AccessExpire int64 } // admin-side JWT
      MessageRpc   zrpc.RpcClientConf                                // → message-rpc gRPC client
  }
  ```
- **L1 关键值（来自 `etc/messagecenter.yaml`）**：
  - `Name: messagecenter`
  - `Host: 0.0.0.0` `Port: 8892`（RAG 暂定走对应端口；可能与 docker-compose 实际不同）
  - `UserAuth.AccessSecret: "ae0536f9-6450-4606-8e13-5a19ed505da0"`（UUID 36 字节 ≥ 32 字节，**无需 padding**）
  - `UserAuth.AccessExpire: 31536000`（365 天）
  - `AdminAuth.AccessSecret: "yU-hCm)2*?NQmWG"`（**15 字节 —— 必需 padding 到 32**）
  - `AdminAuth.AccessExpire: 2592000`（30 天）
  - `MessageRpc.Etcd: {Hosts: [etcd:2379], Key: "message.rpc"}`（RAG 端换 Nacos discovery）
- **业务语义**：**两份密钥与 admin-rpc / user-rpc 共享**：admin-rpc 与 admin-api 共一份（用于签发 & 验签）；user-rpc 与 user-api 共一份。本模块 message-api **只验签不签发**，用 AdminAuth 验 admin JWT、用 UserAuth 验 user JWT。

### `internal/svc/servicecontext.go`
- **主要职责**：依赖持有——`Config` + `MessageRpc messageservice.MessageService`（zrpc client facade）。
- **业务语义**：`MessageRpc` 是 message-api **唯一**外部依赖（无 Redis、admin-rpc、user-rpc 等）。
- **可替换**：Spring `@GrpcClient("message-rpc") MessageServiceBlockingStub`，自动注入。

### `internal/handler/routes.go`（**双 AddRoutes 块，**核心结构**）
- **关键**：go-zero 端通过两次 `server.AddRoutes(...)` 实现两套 JWT 配置（不同密钥 + 不同 prefix）：

| 块 | 路由 | method | 中间件 | 前缀 |
|---|---|---|---|---|
| 块 1（user group） | `/conversations/list`、`/conversations/messages`、`/conversations/read`、`/conversations/send`、`/notifications/list`、`/notifications/read`、`/notifications/readall`（POST）<br>`/unread`（GET） | 8 个 | `rest.WithJwt(UserAuth.AccessSecret)` | `/message/v1` |
| 块 2（admin group） | `/conversations/list`、`/conversations/messages`、`/conversations/read`、`/conversations/send`、`/notifications/send` | 5 个（POST） | `rest.WithJwt(AdminAuth.AccessSecret)` | `/message/v1/admin` |

- **L1 完整路由清单**（共 **13 个**端点）：

**user group（`/message/v1/*`，JWT 用 user secret）：**
| path | method | handler |
|---|---|---|
| `/conversations/list` | POST | `ListConversationsHandler` |
| `/conversations/messages` | POST | `GetConversationMessagesHandler` |
| `/conversations/read` | POST | `MarkConversationReadHandler` |
| `/conversations/send` | POST | `SendUserChatMessageHandler` |
| `/notifications/list` | POST | `ListNotificationsHandler` |
| `/notifications/read` | POST | `MarkNotificationReadHandler` |
| `/notifications/readall` | POST | `MarkAllNotificationsReadHandler` |
| `/unread` | POST（**Go 是 GET**，RAG 建议改 POST） | `GetUnreadSummaryHandler` |

**admin group（`/message/v1/admin/*`，JWT 用 admin secret）：**
| path | method | handler |
|---|---|---|
| `/conversations/list` | POST | `ListAdminConversationsHandler` |
| `/conversations/messages` | POST | `GetAdminConversationMessagesHandler` |
| `/conversations/read` | POST | `MarkAdminConversationReadHandler` |
| `/conversations/send` | POST | `SendAdminChatMessageHandler` |
| `/notifications/send` | POST | `SendAdminNotificationHandler` |

- **L4 实现差异**（关键）：`rest.WithJwt(secret)` = go-zero 中间件，做三件事——(1) 从 `Authorization: Bearer <token>` 取 token，(2) 验签 HS256 + parse claims 到 ctx，(3) 写 userId 到 ctx 的 jwt_token。Go 端**不显式做 Redis blacklist**（admin/group 都有，因为 `middleware/CheckBlacklistMiddleware` 在别的中间件路径，没接入 api 路径）：**RAG 端必须补 Redis blacklist**（参见 user-api/admin-api 同款 redis 拦截），否则安全性回归。
- **L4 关键决策**：RAG 端的`UserApiSupport` / `AdminApiSupport`（helper 聚合类）模式各自处理**单 secret** 的事——message-api 需要**双 secret** 的处理，所以必须拆成 **2 个拦截器** + **2 个 Support 类** 或 **1 个 interceptor 用 path 前缀分发**。**本文件选 2 个 interceptor（清晰边界），与 user-api/admin-api 已落地的两份 interceptor 一致**。

### `internal/handler/message/*.go`（8 个 user handler）
- **统一模式**（每个 ~30 行）：
  ```go
  func XxxHandler(svcCtx *svc.ServiceContext) http.HandlerFunc {
      return func(w http.ResponseWriter, r *http.Request) {
          var req types.XxxReq
          if err := httpx.Parse(r, &req); err != nil {
              httpx.ErrorCtx(r.Context(), w, err)
              return
          }
          l := message.NewXxxLogic(r.Context(), svcCtx)
          resp, err := l.Xxx(&req)
          if err != nil {
              httpx.ErrorCtx(r.Context(), w, err)
          } else {
              httpx.OkJsonCtx(r.Context(), w, resp)
          }
      }
  }
  ```
- **业务语义**：handler 层**不做业务逻辑**（logic 已做完），只做 parse + envelope。
- **可替换**：RAG 走 `MessageController` 单一 Controller（8 个 `@PostMapping`）+ 委托给 8 个 `*Service` + `advice/GlobalResponseAdvice` 自动包信封。**理由**：8 个 handler 同质、无独立生命周期、无独立依赖边界；拆开只会制造 8 个单方法类

### `internal/logic/message/*.go`（8 个 user logic）
- **统一模式**（每个 ~40-60 行）：
  ```go
  func (l *XxxLogic) Xxx(req *types.XxxReq) (resp *types.XxxResp, err error) {
      started := time.Now()
      const route = "/message/v1/<path>"
      defer func() {
          metrics.ObserveRequest(route, started, err)
      }()

      uid, err := shared.UserIDFromContext(l.ctx)
      if err != nil {
          metrics.ObserveReject(route, "user_id_missing")
          return nil, err
      }

      rpcResp, err := l.svcCtx.MessageRpc.Xxx(l.ctx, &pb.XxxReq{
          // 把 uid 填到 proto 字段，role 硬编码
          OperatorId/RecipientId/etc.: uid,
          OperatorRole: pb.SenderRole_USER, // ★ user 端硬编码
          ...req fields
      })
      if err != nil {
          return nil, shared.RPCError(err)
      }
      return shared.ToXxxResp(rpcResp), nil
  }
  ```
- **业务语义（关键）**：
  - **每个 user logic 强制硬编码 `pb.SenderRole_USER`**——这是"user 调用 → 角色 = USER"的契约。RAG 端的对应 `*Service` 必须在构造 gRPC 请求时硬编码 USER
  - **`UserIDFromContext`** 从 ctx 拿 `userId`（json.Number 类型）失败 → `NewErrCode(messagecommon.ErrorUnauthorized)` → 1006
  - **`shared.RPCError(err)`** 把 gRPC `StatusRuntimeException` 翻译成 `*messagecommon.CodeError`，用 `bizCodeByMsg` 反查或按 gRPC code class 兜底
  - **DTO 转换** 通过 `shared/To*Resp(rpcResp)` —— `toNotificationItem` / `toMessageItem` / `toConversationListResp` / `toConversationMessagesResp` / `toSendChatResp` / `toUnreadSummaryResp`
- **逐个 logic 关键点**：
  - `listnotificationslogic.go`：调 `pb.NotificationListReq{UserId:uid, Offset, Limit, UnreadOnly}` → `ListNotifications` RPC
  - `marknotificationreadlogic.go`：`NotificationId` from body → `MarkNotificationReq{UserId:uid, NotificationId:req.NotificationId}`
  - `markallnotificationsreadlogic.go`：**`EmptyReq{}`** —— **空 body**，handler 解析空 struct 后仍走 `MarkAllNotificationsReq{UserId:uid}`（**见 §12.7 #2 关于 required=false**）
  - `markconversationreadlogic.go`：`ConversationId` from body → `MarkConversationReadReq{OperatorId:uid, OperatorRole:USER, ConversationId}`
  - `listconversationslogic.go`：`PageReq{Offset, Limit}` → `ConversationListReq{OperatorId:uid, OperatorRole:USER, Offset, Limit}`
  - `getconversationmessageslogic.go`：`ConversationMessagesReq{ConversationId, Offset, Limit}` → `ConversationMessageListReq{OperatorId:uid, OperatorRole:USER, ...}`
  - `senduserchatmessagelogic.go`：`UserSendChatReq{RecipientId?, ConversationId?, Content}` → `SendChatMessageReq{SenderId:uid, SenderRole:USER, RecipientId:req.RecipientId, RecipientRole:USER, ConversationId, Content}`
  - `getunreadsummarylogic.go`：**`EmptyReq{}`** —— 空 body（**见 §12.7 #2**）→ `UnreadSummaryReq{UserId:uid}`

### `internal/handler/messageadmin/*.go`（5 个 admin handler）
- 与 user handler **完全同款**模式，但解析 `types.XxxReq` 不同（如 `AdminSendChatReq` / `AdminSendNotificationReq`）。
- **L1 关键**：`SendAdminNotificationHandler` 的 `types.AdminSendNotificationReq` 含 `Broadcast` + `TargetId` + `Kind`，见 §12.3 下文。

### `internal/logic/messageadmin/*.go`（5 个 admin logic）
- **业务语义关键**：
  - **`SenderRole.ADMIN` 硬编码** —— admin 端调用的 `listconversationslogic` / `getconversationmessageslogic` / `markconversationreadlogic` 强制 `pb.SenderRole.ADMIN`
  - **`sendadminchatmessagelogic.go`**：`RecipientRole: pb.SenderRole_USER`（admin 只能给 user 发）—— **rpc 侧会校验**：admin→user 直接 OPEN，admin→admin/admin→SYSTEM → `INVALID_ARGUMENT + ErrorUnsupportedRole (5207)`
  - **`sendadminnotificationlogic.go`**（**关键逻辑**）：
    ```go
    uid, err := shared.UserIDFromContext(l.ctx)
    if !req.Broadcast && req.TargetId <= 0 {
        metrics.ObserveReject(route, "target_missing")
        return nil, shared.RPCError(messagecommon.NewErrCode(messagecommon.ErrorInvalidParam))
    }
    rpcResp, err := l.svcCtx.MessageRpc.SendNotification(l.ctx, &pb.SendNotificationReq{
        RecipientIds: recipientIDs(req),
        Broadcast:    req.Broadcast,
        SenderId:     uid,
        SenderRole:   pb.SenderRole_ADMIN,
        Kind:         shared.AdminNotificationKind(req.Broadcast, req.Kind),
        Title:        req.Title,
        Content:      req.Content,
        Extra:        req.Extra,
    })
    ```
    - `recipientIDs(req)`：如果 `Broadcast=true` → `[]int64{nil}`（rpc 端忽略 RecipientIds，由 broadcast 路径覆盖）；否则 `[]int64{req.TargetId}` —— **单点发送模式**
    - `AdminNotificationKind(broadcast, raw)`：
      ```go
      if raw > 0 { return pb.NotificationKind(raw) }    // caller 显式指定 kind（覆盖）
      if broadcast { return pb.NotificationKind_ADMIN_BROADCAST }
      return pb.NotificationKind_ADMIN_NOTICE          // 默认单点发：ADMIN_NOTICE
      ```

### `internal/logic/shared/helper.go`
- **主要职责**（**9 个函数**，**全部 user + admin 共用**）：
  - **`UserIDFromContext(ctx) (int64, error)`**：从 ctx 取 `"userId"`（json.Number 类型）→ `int64`；失败 → `messagecommon.NewErrCode(messagecommon.ErrorUnauthorized)` → 1006
  - **`RPCError(err) error`**：把 gRPC error 翻译成 `*CodeError`：
    - `nil` → nil
    - 有 status + description 在反查表中 → 用反查出的 biz code + 原 description
    - 否则按 gRPC code class 兜底（INVALID_ARGUMENT → 5003、UNAUTHENTICATED → 5004、PERMISSION_DENIED → 5005、ALREADY_EXISTS → 5006、NOT_FOUND → 5002、INTERNAL → 5001、default → 1015）
  - **`ToNotificationListResp` / `ToUnreadSummaryResp` / `ToConversationListResp` / `ToConversationMessagesResp` / `ToSendChatResp`**：proto → http DTO（**部分**做 enum int32 转换、UnreadSummary 整体透传）
  - **`AdminNotificationKind(broadcast, raw) pb.NotificationKind`**：见上
  - **`InvalidContextError(label) error`**：`fmt.Errorf("%s not found in context", label)` —— **未实际使用**，死代码
- **业务语义关键**：
  - **`UserIDFromContext` 仅读 `"userId"` claim**：**双 group 都走同一个函数**——因为 Go 端 `rest.WithJwt(...)` 默认把 payload 全 deserialize 到 ctx，所以不管 user 还是 admin JWT，claim key 都是 `"userId"`（go-zero 默认存 claims `key=fieldName`，不区分 user/admin）
  - **proto → DTO enum int32 转换**：`ConversationItem.PeerRole` proto 是 `SenderRole` enum，DTO 是 `int32`；**所有枚举类型都走 int32 输出**——RAG 端对应 DTO 字段也应是 `int32`
  - **`ToSendChatResp`**：proto `SendChatMessageResp.Code` + `Msg` + `ConversationId` + `Message` + `Status` + `CanSend`；http `SendChatResp` 只有 `ConversationId` + `Message` + `Status` + `CanSend`，**丢弃 code/msg**（envelope 自动包 200/OK）

### `internal/types/types.go`（**24 个类型**）
- **主要职责**：HTTP wire DTO + shared items。
- **L1 关键**：
  - **`PageReq{Offset int32 optional, Limit int32 optional, UnreadOnly bool optional}`**：page + 过滤参数
  - **`EmptyReq{}`**：空 body（MarkAllNotificationsRead、GetUnreadSummary 用），**Go 端 `httpx.Parse` 仍然成功**（handler 解析空 struct）
  - **`EmptyResp{}`**（推断；Go 端 logic 返回 `&EmptyResp{}`）：Go 端实际**裸用 `&EmptyResp{}`** —— RAG 端 DTO 必须存在该空类
  - **`UserSendChatReq{RecipientId int64 string optional, ConversationId int64 string optional, Content string}`**
  - **`AdminSendChatReq{RecipientId int64 string, ConversationId int64 string optional, Content string}`**（**注意**：RecipientId 不是 optional——admin chat 必须发给某个 user）
  - **`AdminSendNotificationReq{Broadcast bool optional, TargetId int64 string optional, Kind int32 optional, Title string, Content string, Extra map[string]string optional}`**
  - **`ConversationMessagesReq{ConversationId int64 string, Offset int32 optional, Limit int32 optional}`**
  - **`MarkConversationReadReq{ConversationId int64 string}`**
  - **`MarkNotificationReq{NotificationId int64 string}`**
  - **`SendChatResp{ConversationId int64 string, Message ChatMessageItem, Status int32, CanSend bool}`**
  - **`ConversationListResp{Items []ConversationItem, Total int64, UnreadCount int64}`**
  - **`ConversationMessagesResp{Items []ChatMessageItem, Status int32, CanSend bool, PendingSenderId int64 string, PendingSenderRole int32}`**
  - **`NotificationListResp{Items []NotificationItem, Total int64, UnreadCount int64}`**
  - **`UnreadSummaryResp{NotificationUnread int64, ConversationUnread int64, TotalUnread int64}`**
  - **`AdminSendNotificationResp{Affected int64}`**
  - **`ChatMessageItem{Id int64 string, ConversationId int64 string, SenderId int64 string, SenderRole int32, RecipientId int64 string, RecipientRole int32, Content string, IsRead bool, CreatedAt int64}`**
  - **`ConversationItem{ConversationId int64 string, PeerId int64 string, PeerRole int32, PeerName string, PeerEmail string, Status int32, UnreadCount int64, LatestMessage string, LatestMessageAt int64, UpdatedAt int64, CanSend bool}`**
  - **`NotificationItem{Id int64 string, RecipientId int64 string, SenderId int64 string, SenderRole int32, Kind int32, Title string, Content string, IsRead bool, CreatedAt int64, Extra map[string]string}`**
- **`int64 ...string` 标签**：Go 端用 `json:"id,string"` 把 int64 输出为字符串以避免 JS 精度丢失。**RAG 端用 Jackson `@JsonFormat(shape=STRING)` 或字符串类型即可**——`ApiTypesSnakeCaseTest` 已验证

### `internal/metrics/metrics.go`
- **主要职责**：3 个 prometheus collector：
  - `APIRequestCounter{Subsystem:"message_api", Name:"request_total", Labels:[route, result]}`
  - `APIRejectCounter{Subsystem:"message_api", Name:"reject_total", Labels:[route, reason]}`
  - `APIRequestDuration{Subsystem:"message_api", Name:"request_duration_seconds", Labels:[route]}`
- **触发点**：每个 logic 入口 `defer metrics.ObserveRequest(route, started, err)`；拒绝分支 `metrics.ObserveReject(route, "<reason>")`（如 `"user_id_missing"` / `"admin_id_missing"` / `"target_missing"`）
- **L1 命名 + labels**：metric 名称属 L1，**Prometheus 抓取端配置不改**。RAG 端用 Micrometer 替换 prometheus client 时命名 1:1。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `messagecenter.go` 进程装配 | `MessageApiApplication` + Spring 容器 | ABSORB | `@SpringBootApplication` 覆盖 flag/config/server 组装 |
| `messagecenter.go` CORS / observability HTTP middleware | `common/observability/HttpMiddlewareFilter`（已 `@Component` 全局注册） | REUSE | 自动拦截 |
| `messagecenter.go` `httpx.SetOkHandler` 信封包装 | `advice/GlobalResponseAdvice`（参照 user-api 同款） | NEW | RAG 三件套定型 |
| `messagecenter.go` `httpx.SetErrorHandler` 错误包装 | `advice/GlobalExceptionHandler`（参照 user-api 同款，**gRPC code → biz code 反查必须用 `MessageGrpcError.bizCodeFromError`**） | NEW | 同上 |
| `config/config.go` Config 绑定 | `config/MessageApiProperties`（`@ConfigurationProperties(prefix="message-api")`：`UserAuth` + `AdminAuth` 嵌套） | ADAPT | 与 admin-api `AdminApiProperties` 同款双 Auth 结构 |
| `config/config.go` `MessageRpc` 客户端 | `client/MessageRpcClient`（`@GrpcClient("message-rpc")`） | NEW | 与 message-rpc 同款模式 |
| `svc/servicecontext.go` 依赖持有 | Spring DI | ABSORB | 纯 wiring |
| `routes.go` 块 1（user group + UserAuth JWT） | `interceptor/MessageJwtAuthInterceptor` + `config/WebMvcConfig.addInterceptors(path="/message/v1/*")` | NEW | **必须独立 Bean**（user secret + path 不同） |
| `routes.go` 块 2（admin group + AdminAuth JWT） | `interceptor/MessageAdminJwtAuthInterceptor` + `config/WebMvcConfig.addInterceptors(path="/message/v1/admin/*")` | NEW | **必须独立 Bean**（admin secret + path 不同 + admin:jwt_blacklist: prefix） |
| `routes.go` 块 1 user 8 个端点 | `controller/MessageController`（8 个 `@PostMapping` + `@RequestMapping("/message/v1")`） | MERGE | 同质薄适配层 |
| `routes.go` 块 2 admin 5 个端点 | `controller/MessageAdminController`（5 个 `@PostMapping` + `@RequestMapping("/message/v1/admin")`） | MERGE | 同质薄适配层，**且 user 与 admin 是不同 Controller（因不同 Auth 上下文）** |
| `routes.go` 前缀 `/message/v1` + `/message/v1/admin` | 拆 Controller + `@RequestMapping` | ADAPT | 路径前缀 1:1 |
| `routes.go` method POST/GET 混合 → RAG 统一 POST | 全部 `@PostMapping` | ADAPT | **已记录偏离**（AGENTS.md §3.4） |
| 8 个 user handler | `MessageController`（8 个 `@PostMapping`） | MERGE | 同质薄适配层 |
| 5 个 admin handler | `MessageAdminController`（5 个 `@PostMapping`） | MERGE | 同质薄适配层，**且不同 Controller**（同 user/admin Auth 边界） |
| `logic/message/getconversationmessageslogic.go` | `service/MessageGetMessagesService` + `service/MessageApiSupport.toConversationMessagesResp` | SPLIT | 业务 service + proto → DTO 投影聚合 |
| `logic/message/getunreadsummarylogic.go` | `service/MessageGetUnreadSummaryService` + `service/MessageApiSupport.toUnreadSummaryResp` | SPLIT | 同上 |
| `logic/message/listconversationslogic.go` | `service/MessageListConversationsService` + `MessageApiSupport.toConversationListResp` | SPLIT | 同上 |
| `logic/message/listnotificationslogic.go` | `service/MessageListNotificationsService` + `MessageApiSupport.toNotificationListResp` | SPLIT | 同上 |
| `logic/message/markallnotificationsreadlogic.go` | `service/MessageMarkAllNotificationsReadService` | MERGE | 单用例 + EmptyReq |
| `logic/message/markconversationreadlogic.go` | `service/MessageMarkConversationReadService` + `MessageApiSupport.successBaseResp` | SPLIT | 业务 + 空响应构造 |
| `logic/message/marknotificationreadlogic.go` | `service/MessageMarkNotificationReadService` + `MessageApiSupport.successBaseResp` | SPLIT | 同上 |
| `logic/message/senduserchatmessagelogic.go` | `service/MessageSendUserChatService` + `MessageApiSupport.toSendChatResp` | SPLIT | — |
| `logic/messageadmin/getadminconversationmessageslogic.go` | `service/MessageAdminGetMessagesService` + `MessageApiSupport.toConversationMessagesResp` | SPLIT | **admin 独立 Service 命名**（admin 与 user 业务类不合并） |
| `logic/messageadmin/listadminconversationslogic.go` | `service/MessageAdminListConversationsService` + `MessageApiSupport.toConversationListResp` | SPLIT | 同上 |
| `logic/messageadmin/markadminconversationreadlogic.go` | `service/MessageAdminMarkConversationReadService` | MERGE | — |
| `logic/messageadmin/sendadminchatmessagelogic.go` | `service/MessageAdminSendChatService` + `MessageApiSupport.toSendChatResp` | SPLIT | — |
| `logic/messageadmin/sendadminnotificationlogic.go` | `service/MessageAdminSendNotificationService` + `MessageApiSupport.adminNotificationKind` | SPLIT | — |
| `logic/shared/helper.go` `UserIDFromContext` | `service/MessageApiSupport.extractUserId(HttpServletRequest)`（参照 `users/user-api/IdentityResolver.claimedUID`） | SPLIT | **关键**：必须从 `request.getAttribute("userId")` 读，**不可从 JWT 自己 parse**（JwtAuthInterceptor 已验签 + 写 attr；重复 parse 会再次触发 Bean 创建密钥 check） |
| `logic/shared/helper.go` `RPCError` 双向转换 | `message-common/MessageGrpcError.bizCodeFromError`（已落地） + `advice/GlobalExceptionHandler` 处理 `StatusRuntimeException` | REUSE | 全部跨 rpc/consumer 行为有公共契约 |
| `logic/shared/helper.go` `To*Resp` 5 个投影 | `MessageApiSupport` 5 个静态方法 | MERGE | proto → DTO 投影 + int32 enum 转换 |
| `logic/shared/helper.go` `AdminNotificationKind` | `MessageApiSupport.adminNotificationKind(broadcast, raw)` 静态方法 | MERGE | 单函数集中 |
| `logic/shared/helper.go` `InvalidContextError` | （死代码） | — | Go 端未使用 |
| `internal/metrics/metrics.go` | `metrics/MessageApiMetrics`（3 metric：`message_api_*`） | ADAPT | Micrometer 替换 prometheus client |
| `internal/types/types.go` 14 个 Req/Resp + 3 个 shared item | `dto/*` | SPLIT | HTTP wire 契约 |
| `internal/handler/routes.go` user 8 路由 | `MessageController` 8 `@PostMapping` | MERGE | 同质 |
| `internal/handler/routes.go` admin 5 路由 | `MessageAdminController` 5 `@PostMapping` | MERGE | 同质，**且不同 Controller（Auth 边界）** |
| `EmptyReq{}` 空 body handler | `MessageController.markAllNotificationsRead` 用 `@RequestBody(required=false) EmptyReq req` | ADAPT | **Spring 约束**：`@RequestBody` 默认 `required=true` —— 见 §12.7 #2 |
| `EmptyReq{}` 空 body logic | `MessageApiSupport` 无（logic 直接透传 uid） | MERGE | — |
| HTTP body 类（int64 → string 输出） | `dto/*` 用 `String` 类型 + Jackson 自动序列化 | ADAPT | 与 user-api 同款；`ApiTypesSnakeCaseTest` 验证 |
| HTTP 信封 | `common/response/Response` + `advice/GlobalResponseAdvice` | REUSE | 全局定型 |
| HTTP 错误处理 | `advice/GlobalExceptionHandler` | REUSE | 全局定型，**gRPC 反查走 `MessageGrpcError`** |
| HTTP 可观测性（span/timeout） | `common/observability/HttpMiddlewareFilter` + `common/spring/HttpMiddlewareFilter` | REUSE | 全局注册 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/message/message/api/
├── MessageApiApplication.java                       # 入口；scanBasePackages=本模块 + com.rag.common
├── advice/
│   ├── GlobalResponseAdvice.java                    # @RestControllerAdvice + ResponseBodyAdvice：包信封（已 Response 不二次包裹）
│   └── GlobalExceptionHandler.java                  # @ExceptionHandler：BusinessException / StatusRuntimeException（用 MessageGrpcError.bizCodeFromError）/ HttpMessageNotReadable (400) / MethodNotSupported (405) / MediaTypeNotSupported (415) / Exception (500)
├── client/
│   └── MessageRpcClient.java                        # @GrpcClient("message-rpc") MessageServiceBlockingStub facade
├── config/
│   ├── MessageApiProperties.java                    # @ConfigurationProperties(prefix="message-api")：UserAuth + AdminAuth 嵌套
│   └── WebMvcConfig.java                            # @Configuration：注册双 interceptor（path=/message/v1/* + path=/message/v1/admin/*）
├── controller/
│   ├── MessageController.java                       # @RequestMapping("/message/v1") 8 个 @PostMapping（user group）
│   └── MessageAdminController.java                  # @RequestMapping("/message/v1/admin") 5 个 @PostMapping（admin group）
├── dto/
│   ├── PageReq.java                                 # {offset, limit, unreadOnly} optional
│   ├── EmptyReq.java                                # 空 body
│   ├── UserSendChatReq.java                         # {recipientId? string, conversationId? string, content}
│   ├── AdminSendChatReq.java                        # {recipientId string, conversationId? string, content}
│   ├── AdminSendNotificationReq.java                # {broadcast, targetId? string, kind, title, content, extra}
│   ├── AdminSendNotificationResp.java               # {affected}
│   ├── ConversationMessagesReq.java                 # {conversationId string, offset?, limit?}
│   ├── ConversationMessagesResp.java                # {items, status, canSend, pendingSenderId, pendingSenderRole}
│   ├── MarkConversationReadReq.java                 # {conversationId string}
│   ├── MarkNotificationReq.java                     # {notificationId string}
│   ├── SendChatResp.java                            # {conversationId, message, status, canSend}
│   ├── ConversationListResp.java                    # {items, total, unreadCount}
│   ├── NotificationListResp.java                    # {items, total, unreadCount}
│   ├── UnreadSummaryResp.java                       # {notificationUnread, conversationUnread, totalUnread}
│   ├── ChatMessageItem.java                         # {id, conversationId, senderId, senderRole, recipientId, recipientRole, content, isRead, createdAt}
│   ├── ConversationItem.java                        # {conversationId, peerId, peerRole, peerName, peerEmail, status, unreadCount, latestMessage, latestMessageAt, updatedAt, canSend}
│   └── NotificationItem.java                        # {id, recipientId, senderId, senderRole, kind, title, content, isRead, createdAt, extra}
├── interceptor/
│   ├── MessageJwtAuthInterceptor.java               # user secret + path=/message/v1/*（不含 /admin）
│   └── MessageAdminJwtAuthInterceptor.java          # admin secret + path=/message/v1/admin/* (前缀独立)
├── metrics/
│   └── MessageApiMetrics.java                       # @Component + MeterRegistry：message_api_request_total / reject_total / request_duration_seconds
└── service/
    ├── MessageApiSupport.java                       # @Component：extractUserId(request) + 5 个 To*Resp 投影 + adminNotificationKind(broadcast, raw) + successBaseResp
    # ↑ 业务码常量**不再新建本地 enum**：直接 REUSE `message-common/errmsg/MessageErrorCode`
    #   （见 §12.7 #6）。Go 的 `message/common/errmsg.go` 在 Java 侧已有等价载体，无需镜像一份。
    ├── MessageGetMessagesService.java               # GetConversationMessages（user group）
    ├── MessageListConversationsService.java         # ListConversations（user group）
    ├── MessageSendUserChatService.java              # SendUserChatMessage（user group）
    ├── MessageMarkConversationReadService.java      # MarkConversationRead（user group）
    ├── MessageListNotificationsService.java         # ListNotifications（user group）
    ├── MessageMarkNotificationReadService.java      # MarkNotificationRead（user group）
    ├── MessageMarkAllNotificationsReadService.java  # MarkAllNotificationsRead（user group）
    ├── MessageGetUnreadSummaryService.java          # GetUnreadSummary（user group）
    ├── MessageAdminListConversationsService.java    # ListAdminConversations（admin group）
    ├── MessageAdminGetMessagesService.java          # GetAdminConversationMessages（admin group）
    ├── MessageAdminSendChatService.java             # SendAdminChatMessage（admin group）
    ├── MessageAdminMarkConversationReadService.java # MarkAdminConversationRead（admin group）
    └── MessageAdminSendNotificationService.java     # SendAdminNotification（admin group，broadcast=true 特化分支）
```

### 逐文件说明

**`MessageApiApplication.java`**
- 所属包：`com.rag.message.message.api`
- 主要职责：入口；`@SpringBootApplication(scanBasePackages={"com.rag.message.message.api", "com.rag.common"})` + `@EnableConfigurationProperties(MessageApiProperties.class)` + `@EnableDiscoveryClient`
- 承载 Go 职责：`messagecenter.go` 的进程装配（**ABSORB**）
- 依赖：`config/MessageApiProperties`、`client/MessageRpcClient`、`interceptor/*`

**`advice/GlobalResponseAdvice.java`**
- 同款 `users/user-api/GlobalResponseAdvice.java:42-62`：实现 `ResponseBodyAdvice<Object>`，`beforeBodyWrite` 判 `body == null || body instanceof Response` 则放行（避免 `@ExceptionHandler` 的 `ResponseEntity<Response>` 二次包裹）
- **WHY exact**：已被 `ResponseEnvelopeAdviceTest`（user-api/admin-api）锁定为回归用例，message-api 复用即直接受益

**`advice/GlobalExceptionHandler.java`**
- 与 user-api 同款结构：分业务异常、`StatusRuntimeException`（**反查走 `MessageGrpcError.bizCodeFromError(Throwable)`**）、框架级 400/405/415、兜底 Exception
- **关键**：gRPC 反查**必须用 message-common 的 `bizCodeFromError`**——users-common 的 `JwtUtil` / `users.common.ErrorCode` 是 user 域的，**不能跨域复用**
- 业务码 → HTTP status 一律 200（与 Go 端 `httpx.SetErrorHandler` 写 `http.StatusOK` 1:1）

**`client/MessageRpcClient.java`**
- `@GrpcClient("message-rpc") MessageServiceBlockingStub` 注入 + 13 个方法 facade（与 message-rpc 9 个 RPC + DTO 投影）
- 命名冲突点：rpc 端 `MessageServiceGrpc.MessageServiceBlockingStub` 类型与 message-rpc 的 service 同名——但本类属 api 模块，**类型同名不冲突**（不同包路径）

**`config/MessageApiProperties.java`**
- 嵌套双 Auth 段：
  ```java
  @ConfigurationProperties(prefix = "message-api")
  public class MessageApiProperties {
      @NestedConfigurationProperty private UserAuth userAuth = new UserAuth();
      @NestedConfigurationProperty private AdminAuth adminAuth = new AdminAuth();
      public static class UserAuth { private String accessSecret; private long accessExpire; }
      public static class AdminAuth { private String accessSecret; private long accessExpire; }
  }
  ```
- **L1 关键值**：`userAuth.accessSecret=ae0536f9-...`（UUID 36B）、`adminAuth.accessSecret=yU-hCm)2*?NQmWG`（**15B 必走 `padToHmacKeySize`**）

**`config/WebMvcConfig.java`**
- 注册 2 个 interceptor：
  ```java
  registry.addInterceptor(messageJwtAuthInterceptor)
      .addPathPatterns("/message/v1/*");
  registry.addInterceptor(messageAdminJwtAuthInterceptor)
      .addPathPatterns("/message/v1/admin/*");
  ```
- 与 user-api/admin-api 的单 interceptor 注册对比——message-api **必须**双注册

**`controller/MessageController.java`**
- `@RequestMapping("/message/v1")` + 8 个 `@PostMapping`：conversations/list, conversations/messages, conversations/read, conversations/send, notifications/list, notifications/read, notifications/readall, unread
- 委托给 8 个 `Service`
- public 路径：**本组无 public 路径**——所有 8 个都要 JWT（对比 user-api/admin-api 有 login/register/create 等 public 端点）

**`controller/MessageAdminController.java`**
- `@RequestMapping("/message/v1/admin")` + 5 个 `@PostMapping`：conversations/list, conversations/messages, conversations/read, conversations/send, notifications/send
- **public 路径：本组无 public**
- 与 `MessageController` 物理分离因 Auth 边界不同

**`dto/EmptyReq.java`**
- 空类（Lombok @Data 或显式 getter/setter），handler 用 `@RequestBody(required=false) EmptyReq req`
- **必须显式存在**，否则 Spring 反序列化报错

**`dto/*Req/Resp`**
- 全部用 `Long`（不 String）；输出 JSON 时通过 Jackson `@JsonFormat(shape=STRING)` 或字段类型用 String 输出——**已在 user-api 确立模式**（参见 `ApiTypesSnakeCaseTest`）

**`interceptor/MessageJwtAuthInterceptor.java`**
- 与 `users/user-api/JwtAuthInterceptor.java` 结构同款：`@Component`、`implements HandlerInterceptor`、构造器调 `Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(messageApiProperties.getUserAuth().getAccessSecret()))`、`preHandle` 做 JWT 解析 + Redis blacklist（key 前缀 `message:jwt_blacklist:`）+ 写 `userId` attribute
- **L4 idiomatization**：
  - Go 出处：`service/message/api/etc/messagecenter.yaml` `UserAuth.AccessSecret = ae0536f9-...`（UUID 36B，已 ≥ 32 字节）+ `rest.WithJwt(secret)` 隐式做 HS256 验签
  - Java 表达差异：jjwt 强制 ≥32 + 显式 Redis blacklist + 写 attribute
  - 语义等价：36B secret padding 后字节一致；blacklist 检查安全增强（Go 端未做）
  - **⚠️ Redis blacklist 是 RAG 端"更安全"的偏离**（Go 端 `routes.go` 不挂 blacklist middleware）——见 §12.7 #4

**`interceptor/MessageAdminJwtAuthInterceptor.java`**
- 与 `users/admin-api/AdminJwtAuthInterceptor.java` 结构同款；但 secret = `adminAuth.accessSecret="yU-hCm)2*?NQmWG"`（15B → 补齐 32B），prefix = `message:admin:jwt_blacklist:`（命名按 message-api 命名空间）
- **P0 修复点**：拦截器构造器**必须**调 `padToHmacKeySize`（参见 admin-api `AdminJwtAuthInterceptor.java:67-68` 的 L4 javadoc 已锁定）
- **§3.3 强制留痕**：admin-api 是"更安全但打破 Go 行为"的 wire 偏离；message-api 拦截器同理

**`metrics/MessageApiMetrics.java`**
- 3 个 metric：`message_api_request_total{route, result}` / `message_api_reject_total{route, reason}` / `message_api_request_duration_seconds{route}`
- Micrometer Counter + Timer，命名 1:1 对齐 prom
- `observeRequest(route, startedAtNanos, err)` + `observeReject(route, reason)` 公开方法

**`service/MessageApiSupport.java`**
- `@Component`，聚合以下静态与实例方法：
  - `extractUserId(HttpServletRequest) → long`：从 `request.getAttribute("userId")` 读（JwtAuthInterceptor 已写入），没有则抛 `BusinessException(ERROR_UNAUTHORIZED)` —— **与 `users/user-api/IdentityResolver.claimedUID` 同款**
  - `successBaseResp() → SendChatResp`：构造 `conversation_id=0, message={}, status=OPEN, canSend=false` —— 实际由 rpc 端填
  - `toNotificationListResp(rpc) → dto` / `toUnreadSummaryResp(rpc) → dto` / `toConversationListResp(rpc) → dto` / `toConversationMessagesResp(rpc) → dto` / `toSendChatResp(rpc) → dto`
  - `adminNotificationKind(broadcast: boolean, raw: int) → NotificationKind`：raw>0 → 取 raw；broadcast → `ADMIN_BROADCAST`；否则 `ADMIN_NOTICE`
- **注意**：admin group 与 user group 共用 `MessageApiSupport` —— `extractUserId` 对 admin JWT 也读 `"userId"` claim（go-zero 默认存 claims 不区分 user/admin；RAG 端 admin group 路径下 jwt 拦截器同样写 `"userId"` attribute）

**业务码常量 —— REUSE `message-common/errmsg/MessageErrorCode`，不新建文件**
- Go 的 `service/message/common/errmsg.go` 在 Java 侧已有等价载体：`message-common` 的 `MessageErrorCode`（enum，**L1 冻结值**）、`MessageErrorMessages`（文案）、`MessageGrpcError`（含 `bizCodeFromError` 反查），三者均已在 RAG 落地。
- 因此本文件**不新建** `service/MessageErrorCodes.java`。§12.5 的树中已相应移除该条目；`advice/GlobalExceptionHandler` 与各 Service 直接 `import message-common` 的 enum 即可。
- 理由（§10 规则 4/6）：目标项目**已有组件可以承担**该职责，无需重复创建。`users-common/errmsg/ErrorCode` 是 user 域的，**不能跨域复用**；message 域只能依赖 `message-common`。
- 若未来 message 需要扩展 Go 侧尚未定义的子段，再引入 alias enum —— 那是届时的独立职责，不在本文件范围。

**`service/MessageListConversationsService.java`**（user group）
- 业务逻辑：
  1. `support.extractUserId(httpRequest)` → uid
  2. `metrics.observeRequest(route="/message/v1/conversations/list", started, err)`
  3. `rpc.listConversations(ConversationListReq{OperatorId:uid, OperatorRole:USER, Offset:req.offset, Limit:req.limit})`
  4. RPC 失败 → 抛 `StatusRuntimeException` 给 GlobalExceptionHandler
  5. RPC 成功 → `support.toConversationListResp(rpcResp)`
- 与 `logic/message/listconversationslogic.go` 1:1

**`service/MessageAdminListConversationsService.java`**（admin group）
- 与上面**业务同款**，**唯一差异**：`OperatorRole: ADMIN` 硬编码
- 与 Go `listadminconversationslogic.go` 1:1

（其它 12 个 Service 同结构，差异只在硬编码 USER / ADMIN + RPC 方法名 + DTO 投影）

**`service/MessageAdminSendNotificationService.java`**（最特殊）
- 业务逻辑（与 Go `sendadminnotificationlogic.go:34-66` 1:1）：
  1. `support.extractUserId(httpRequest)` → uid
  2. `metrics.observeRequest(route="/message/v1/admin/notifications/send")`
  3. `if (!req.broadcast && req.targetId <= 0)` → 抛 `StatusRuntimeException(INVALID_ARGUMENT + ErrorInvalidParam)`
  4. `rpc.sendNotification(SendNotificationReq{RecipientIds: List.of(req.targetId) if !broadcast else List.of(), Broadcast:broadcast, SenderId:uid, SenderRole:ADMIN, Kind: support.adminNotificationKind(req.broadcast, req.kind), Title: req.title, Content: req.content, Extra: req.extra})`
  5. 成功 → `AdminSendNotificationResp{affected: rpc.affected}`

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `messagecenter.go` | `MessageApiApplication` | ABSORB | Spring Boot 启动 |
| `messagecenter.go`（`observability.NewHTTPMiddleware`） | `common/observability/HttpMiddlewareFilter`（已 `@Component` 全局注册） | REUSE | 自动拦截 |
| `messagecenter.go`（`httpx.SetOkHandler`） | `advice/GlobalResponseAdvice` | NEW | RAG 三件套定型 |
| `messagecenter.go`（`httpx.SetErrorHandler`） | `advice/GlobalExceptionHandler` | NEW | 同上；gRPC 反查走 `MessageGrpcError.bizCodeFromError` |
| `internal/config/config.go` | `config/MessageApiProperties` | ADAPT | RestConf + 双 Auth + RpcClientConf 绑定 |
| `internal/svc/servicecontext.go` MessageRpc 客户端 | `client/MessageRpcClient`（`@GrpcClient("message-rpc")`） | NEW | devh starter 自动注入 |
| `internal/handler/routes.go` user group（8 路由） | `controller/MessageController`（8 `@PostMapping`） | MERGE | 同质薄适配层 |
| `internal/handler/routes.go` admin group（5 路由） | `controller/MessageAdminController`（5 `@PostMapping`） | MERGE | 同质，**且独立 Controller**（Auth 边界） |
| `internal/handler/routes.go` 块 1 user JWT | `interceptor/MessageJwtAuthInterceptor`（独立 Bean，独立 Redis blacklist prefix） | NEW | 双 Auth 上下文 |
| `internal/handler/routes.go` 块 2 admin JWT | `interceptor/MessageAdminJwtAuthInterceptor`（独立 Bean，独立 prefix `message:admin:jwt_blacklist:`） | NEW | 同上 |
| `internal/handler/routes.go` 前缀 `/message/v1` + `/message/v1/admin` | 双 Controller + `@RequestMapping` | ADAPT | 路径 1:1 |
| `internal/handler/routes.go` method 混合 | 全部 `@PostMapping`（`/unread` 也改 POST） | ADAPT | 已记录偏离（AGENTS.md §3.4） |
| `internal/handler/message/getconversationmessageshandler.go` | `MessageController.conversationsMessages` + `MessageGetMessagesService.getConversationMessages` | SPLIT | handler 拆 Controller method，logic 拆 Service |
| `internal/handler/message/getunreadsummaryhandler.go` | `MessageController.unread` + `MessageGetUnreadSummaryService.getUnreadSummary` | SPLIT | 同上 |
| `internal/handler/message/listconversationshandler.go` | `MessageController.conversationsList` + `MessageListConversationsService.listConversations` | SPLIT | 同上 |
| `internal/handler/message/listnotificationshandler.go` | `MessageController.notificationsList` + `MessageListNotificationsService.listNotifications` | SPLIT | 同上 |
| `internal/handler/message/markallnotificationsreadhandler.go` | `MessageController.notificationsReadAll` + `MessageMarkAllNotificationsReadService.markAllNotificationsRead` | SPLIT | 同上；**+ `@RequestBody(required=false)`**（§12.7 #2） |
| `internal/handler/message/markconversationreadhandler.go` | `MessageController.conversationsRead` + `MessageMarkConversationReadService.markConversationRead` | SPLIT | — |
| `internal/handler/message/marknotificationreadhandler.go` | `MessageController.notificationsRead` + `MessageMarkNotificationReadService.markNotificationRead` | SPLIT | — |
| `internal/handler/message/senduserchatmessagehandler.go` | `MessageController.conversationsSend` + `MessageSendUserChatService.sendUserChatMessage` | SPLIT | — |
| `internal/handler/messageadmin/getadminconversationmessageshandler.go` | `MessageAdminController.conversationsMessages` + `MessageAdminGetMessagesService.getAdminConversationMessages` | SPLIT | 同上；admin role |
| `internal/handler/messageadmin/listadminconversationshandler.go` | `MessageAdminController.conversationsList` + `MessageAdminListConversationsService.listAdminConversations` | SPLIT | — |
| `internal/handler/messageadmin/markadminconversationreadhandler.go` | `MessageAdminController.conversationsRead` + `MessageAdminMarkConversationReadService.markAdminConversationRead` | SPLIT | — |
| `internal/handler/messageadmin/sendadminchatmessagehandler.go` | `MessageAdminController.conversationsSend` + `MessageAdminSendChatService.sendAdminChatMessage` | SPLIT | — |
| `internal/handler/messageadmin/sendadminnotificationhandler.go` | `MessageAdminController.notificationsSend` + `MessageAdminSendNotificationService.sendAdminNotification` | SPLIT | — |
| `internal/logic/message/*logic.go`（8 个） | 8 个 `service.Message*Service` | MERGE | 业务用例 + DTO 投影 |
| `internal/logic/messageadmin/*logic.go`（5 个） | 5 个 `service.MessageAdmin*Service` | MERGE | 业务用例 + DTO 投影，**且独立 Service 类（admin 与 user 不合并）** |
| `internal/logic/shared/helper.go` `UserIDFromContext` | `service/MessageApiSupport.extractUserId(HttpServletRequest)`（从 `userId` attribute 读） | SPLIT | 不能从 JWT 自己 parse（JwtAuthInterceptor 已验签写 attr）；改用 Spring attribute 模式 |
| `internal/logic/shared/helper.go` `RPCError` | `message-common/errmsg/MessageGrpcError.bizCodeFromError(Throwable)`（已落地）+ `advice/GlobalExceptionHandler` 接 `StatusRuntimeException` | REUSE | 业务码反查不复写 |
| `internal/logic/shared/helper.go` `ToNotificationListResp` / `ToUnreadSummaryResp` / `ToConversationListResp` / `ToConversationMessagesResp` / `ToSendChatResp` | `service/MessageApiSupport` 静态方法 | MERGE | proto → DTO 投影 + int32 enum 转换 |
| `internal/logic/shared/helper.go` `AdminNotificationKind` | `service/MessageApiSupport.adminNotificationKind(broadcast, raw)` | MERGE | 单函数集中 |
| `internal/logic/shared/helper.go` `InvalidContextError` | — | — | Go 死代码 |
| `internal/types/types.go` 14 个 Req/Resp + 3 个 shared item | 17+ 个 `dto/*` | SPLIT | HTTP wire 契约 |
| `internal/metrics/metrics.go` | `metrics/MessageApiMetrics` | ADAPT | Micrometer 替换 prometheus client |
| `EmptyReq{}` 空 body | `@RequestBody(required=false) EmptyReq req` | ADAPT | Spring 必显式 `required=false`；见 §12.7 #2 |
| 双 Auth secret 共享 | `MessageApiProperties.UserAuth` + `MessageApiProperties.AdminAuth` 双嵌套 | ADAPT | 同 admin-api `AdminApiProperties` 双段 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 13 个端点的 path/method、JWT claim `userId`、两份 secret 字面值（user 36B / admin 15B）、token TTL（365d vs 30d）、DTO 字段名（含 `int64 ...string` 输出） |
| L2 业务语义有承载位置 | ✅ user group 8 端 `OperatorRole=USER` / admin group 5 端 `OperatorRole=ADMIN` 硬编码；`SendAdminNotification` 的 `broadcast=true` 覆盖 + `target_id<=0` 校验 + `AdminNotificationKind` 三分支决策；`sendChatMessage` 端由 rpc 层处理 `ConversationId>0` vs 缺省两路；Reject reason 列表（`user_id_missing` / `admin_id_missing` / `target_missing`）逐个对照 |
| L3 技术语义有承载方案 | ✅ JWT 验签 + Redis blacklist → 双 interceptor；HTTP envelope → `advice/GlobalResponseAdvice`；gRPC code ↔ biz code 双向 → `message-common/MessageGrpcError.bizCodeFromError`；HTTP 可观测 → `common/HttpMiddlewareFilter` |
| 业务分支与异常路径已纳入 | ✅ gRPC 码 7 类（INVALID_ARGUMENT → 5003 / UNAUTHENTICATED → 5004 / PERMISSION_DENIED → 5005 / ALREADY_EXISTS → 5006 / NOT_FOUND → 5002 / INTERNAL → 5001 / default → 1015）；非 200 路径走 envelope |
| 框架吸收有机制依据 | ✅ `messagecenter.go` → Spring Boot 启动；`servicecontext.go` → Spring DI；`routes.go` 双块 → 双 interceptor + 双 Controller；`httpx.SetOkHandler/ErrorHandler` → `@RestControllerAdvice` |
| 复用组件已明确 | ✅ `common/Response` / `common/BusinessException` / `common/observability` / `users-common/JwtUtil.padToHmacKeySize` / `message-common/MessageErrorCode/MessageErrorMessages/MessageGrpcError` |
| 新增文件有职责依据 | ✅ `MessageJwtAuthInterceptor`（独立 user secret）/`MessageAdminJwtAuthInterceptor`（独立 admin secret + 短密钥 padding）/`MessageApiSupport`（聚合 helper）/ 双 Controller（user 与 admin 边界差异）/13 个 Service（按 Go 1:1 拆分） — 均非"Go 有同名文件故建" |
| 合并有组织依据 | ✅ 8 user handler → 1 `MessageController`；5 admin handler → 1 `MessageAdminController`；Logic → Service 1:1（每 Service 单例化 1 用例） |
| 拆分可解释 | ✅ 双 Controller（user / admin 因 Auth 边界）；双 Interceptor（user secret / admin secret 不同）；admin Service 独立命名空间（`MessageAdmin*Service`）；Helper 收口（`MessageApiSupport`）；投影与业务分离 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Logic.java` / `Handler.java` / `Types.java` / `ServiceContext.java` / `Metrics.java` 文件名 |
| 未把未实现能力说成现有机制 | ✅ Flyway 归属标 `UNKNOWN`（rpc 端 §12.7）；`MessageErrorCodes` 不强制建（直接用 `message-common` enum） |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **admin secret 15 字节 → `Keys.hmacShaKeyFor` 抛 `WeakKeyException`** | `AdminAuth.AccessSecret="yU-hCm)2*?NQmWG"`（**15B**）；jjwt 0.12 强制 HS256 key ≥256 bits = 32B。Go 端 golang-jwt 不校验长度故无问题；**RAG 端 `MessageAdminJwtAuthInterceptor` 必须调 `JwtUtil.padToHmacKeySize`**，否则**进程起不来**。**修法**：与 `users/admin-api/AdminJwtAuthInterceptor.java:67` 同款：`Keys.hmacShaKeyFor(JwtUtil.padToHmacKeySize(props.getAdminAuth().getAccessSecret()))`。**注意**：admin-rpc 与 message-api 必须共用同一份 key bytes，否则 admin JWT 在 message-api 验不过 |
| 2 | **`@RequestBody` 默认 `required=true`，`EmptyReq{}` 空 body handler 会 400** | Go 端 `httpx.Parse(r, &req)` 对 `EmptyReq{}`（无字段）总是成功（handler 解析空 struct）；Spring `@RequestBody` 默认抛 `HttpMessageNotReadableException` → 400。**RAG 端必须显式 `@RequestBody(required=false) EmptyReq req`**。涉及 `markAllNotificationsRead` (user) + `getUnreadSummary` (user) + `markConversationRead`（admin 与 user 两端都 `EmptyResp`，但 Go 端 request body 不是 EmptyReq，无此问题；只有 user group 的 2 个 endpoint 受影响）。**修法**：在对应 Controller method 加 `required=false` |
| 3 | **admin 与 user 共用 `MessageApiSupport.extractUserId` 时 admin JWT 校验放行 user group 端点（潜在越权）** | `MessageJwtAuthInterceptor` 拦截 `/message/v1/*`（非 admin），path 不重叠（`/message/v1/admin/*` 是另一前缀）；但 `extractUserId` 通用读 `"userId"` attribute —— **理论上 user group 只能被 user JWT 验签**，因为 admin JWT 没有发到 user group（`/message/v1/*` 不匹配 `/message/v1/admin/*`）。**但**：若用户**误用** admin token 调用 `/message/v1/conversations/list`，admin token 通过 `MessageJwtAuthInterceptor`（user secret 验签失败 → 401）—— 这部分 RAG 自动保护。**唯一风险**：若将来想支持「admin 查看自己的 user 视角会话」，**必须新增独立 endpoint**。当前设计**无漏洞**，列为已注意 |
| 4 | **RAG 端 Redis blacklist 是「更安全但偏离 Go」** | Go 端 `routes.go` 不挂 blacklist middleware（admin-api 同款）—— message-api 的 user 与 admin group 也都不挂。RAG 端按 user-api / admin-api 惯例**显式**做 blacklist（`Message:jwt_blacklist:<token>` for user / `message:admin:jwt_blacklist:<token>` for admin）。**这是偏离 Go 的"安全增强"**：Go 端的 logout 行为是只把 token 写到 Redis blacklist，然后**所有路径都不查**，相当于 blacklist 形同虚设。**RAG 端必须补**——不属于"顺手修正"，是补齐 Go 安全缺口 |
| 5 | **`int64 → string` 输出 DTO 必须用 Jackson 配置** | Go 端 `json:"id,string"` 把 int64 输出为字符串。RAG 端用 `@JsonFormat(shape=STRING)` 或字段类型用 `String` 即可。**已有 `ApiTypesSnakeCaseTest`（user-api）锁定用例**，message-api 必须复用同款，否则 JS 客户端精度丢失。**L1 冻结** |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `MessageApiProperties` 完整字段 | Go 端 `UserAuth{accessSecret, accessExpire}` 与 `AdminAuth{accessSecret, accessExpire}` 完整字段。**是否需要 TTL 字段（accessExpire）对外暴露**——当前 api 不签发 token，accessExpire 只读不写；保留字段供未来发 token 用 |
| 双 Controller 是否合并为 1 个 with path dispatch | 当前按 user / admin Auth 边界拆双 Controller；如未来统一为单一 token 体系，可合并。**当前 RAG 落地建议保持双 Controller** |
| `MessageAdminSendNotificationService` 的 `kind` 字段语义 | Go 端 `AdminNotificationKind(raw > 0 ? raw : broadcast ? BROADCAST : NOTICE)` —— raw 显式时覆盖。`AdminSendNotificationReq.Kind` 文档需说明覆盖优先级。**未拍板** |
| `/unread` 路径是否要单独走 GET（与 Go 一致） | AGENTS.md §3.4 留档偏离惯例是"统一 POST"；但 message-api 的 `/unread` 在 Go 端是 GET（路径缓存友好）。**RAG 用户决策**：本文件按统一 POST 推导 |
| `MessageApiSupport` 是否独立成 `ExtractService` 与 `Projector` 两个类 | 当前聚合所有 helper（uid 提取 + 投影 + 通知 kind 决策）；与 user-api `UserApiSupport` 同款模式。**未拍板** |
| Redis blacklist key prefix 命名 | 本文件推 `message:jwt_blacklist:` / `message:admin:jwt_blacklist:`（与 user-api / admin-api 同款 prefix）。是否对齐用户/管理员命名空间（`message:user:` / `message:admin:`）—— **未拍板** |
| 13 个 Service 是否合并为 user / admin 两组聚合类 | 当前每个 endpoint 1 个 Service（与 Go 一对一）；如按 user-api 模式合 1 个 `MessageService` + 1 个 `MessageAdminService` 聚合类，**会破坏 Go 1:1 映射**。**保持现状** |
| ~~`MessageErrorCodes` 是否真要建~~ | **已定：不建。** 与 `message-common/MessageErrorCode` 完全重复，按 §10 规则 4/6「已有组件能承担就复用」判 REUSE。§12.5 树中该条目已移除。若未来需扩展 Go 侧尚未定义的子段，再单独立项 |
