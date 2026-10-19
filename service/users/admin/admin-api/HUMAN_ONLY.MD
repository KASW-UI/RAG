# 文件阅读顺序与理由

阅读顺序：
`AdminApiApplication.java` → `config/AdminApiProperties.java` → `config/WebMvcConfig.java` → `client/AdminRpcClient.java` → `interceptor/AdminJwtAuthInterceptor.java` → `advice/GlobalResponseAdvice.java` → `advice/GlobalExceptionHandler.java` → `service/AdminApiSupport.java` → `service/IdentityResolver.java` → `metrics/AdminApiMetrics.java` → `dto/AdminInfo.java` → `dto/UserInfo.java` → `dto/LoginReq.java` → `dto/LoginResp.java` → `dto/LogoutReq.java` → `dto/LogoutResp.java` → `dto/GetSelfReq.java` → `dto/GetSelfResp.java` → `dto/UpdateSelfReq.java` → `dto/UpdateSelfResp.java` → `dto/CreateAdminReq.java` → `dto/CreateAdminResp.java` → `dto/CreateInviteCodeReq.java` → `dto/CreateInviteCodeResp.java` → `dto/BanUserReq.java` → `dto/BanUserResp.java` → `dto/UnBanUserReq.java` → `dto/UnBanUserResp.java` → `dto/GetUserReq.java` → `dto/GetUserResp.java` → `dto/GetUserListReq.java` → `dto/GetUserListResp.java` → `dto/UpdateUserReq.java` → `dto/UpdateUserResp.java` → `dto/DeleteUserReq.java` → `dto/DeleteUserResp.java` → `dto/ResetUserPasswordReq.java` → `dto/ResetUserPasswordResp.java` → `service/AdminLoginService.java` → `service/AdminLogoutService.java` → `service/AdminGetSelfService.java` → `service/AdminUpdateSelfService.java` → `service/AdminCreateService.java` → `service/AdminCreateInviteCodeService.java` → `service/AdminBanUserService.java` → `service/AdminUnBanUserService.java` → `service/AdminGetUserService.java` → `service/AdminListUserService.java` → `service/AdminUpdateUserService.java` → `service/AdminDeleteUserService.java` → `service/AdminResetUserPasswordService.java` → `controller/AdminController.java`

文件职责：
`AdminApiApplication.java`：负责启动 admin-api HTTP 网关（端口 8889）的 Spring Boot 入口，`scanBasePackages` 扫 `com.rag.users.admin.api` 与 `com.rag.common`，加 `@ConfigurationPropertiesScan`。
`config/AdminApiProperties.java`：负责绑定 application.yml 中 admin-api.* 配置段的 `@ConfigurationProperties(prefix="admin-api")` 类，聚合 server name + admin-auth JWT 签名密钥（与 admin-rpc 共享）+ redis + grpc client + nacos + observability。
`config/WebMvcConfig.java`：负责注册 AdminJwtAuthInterceptor 到 `/admin/v1/**` 路径，配置 `/admin/v1/login` 与 `/admin/v1/create` 为 public path 不走 JWT 校验。
`client/AdminRpcClient.java`：负责持有 `AdminServiceBlockingStub`（devh `@GrpcClient("admin-rpc")`），Nacos discovery 自动发现 admin-rpc。
`interceptor/AdminJwtAuthInterceptor.java`：负责 HandlerInterceptor 三件事——解析 Authorization Bearer + jjwt 验签 + Redis blacklist 校验（key prefix `admin:jwt_blacklist:`），通过后把 adminId 注入请求 attribute。
`advice/GlobalResponseAdvice.java`：负责 `@RestControllerAdvice + ResponseBodyAdvice`，把任意 Controller 返回值包成统一 envelope `{code, msg, data}`。
`advice/GlobalExceptionHandler.java`：负责 `@RestControllerAdvice + @ExceptionHandler`，把 BusinessException / StatusRuntimeException / 通用异常还原成 envelope 与 HTTP 状态码。
`service/AdminApiSupport.java`：负责 helper 聚合（extractAdminId / codeFromRPCError / fromProto(AdminInfo) / fromProto(UserInfo)）。
`service/IdentityResolver.java`：负责按 adminId 校验身份契约（3 个 sentinel exception：InvalidClaim / AdminNotFound / IdentityMismatch），调 admin-rpc getSelf 验证存在性；admin 域 AdminInfo 无 status 字段所以没有 inactive 异常（与 user-api 的 5 exception 不同）。
`metrics/AdminApiMetrics.java`：负责 admin-api HTTP 层业务指标收集（17 Counter：login 3 + ban 2 + unban 2 + delete 2 + reset 2 + update 2 + invite 3 含 `token_invalid`），标签 path + result 复刻 Go 端 admin_api_intercept_total。
`dto/AdminInfo.java`：负责定义嵌套 AdminInfo DTO（uid / username / email / extra_info），uid 序列化为字符串。
`dto/UserInfo.java`：负责定义嵌套 UserInfo DTO（uid / status / username / email / extra_info），与 admin-rpc admin.proto.UserInfo 同名用全限定名区分。
`dto/LoginReq.java`：负责 login HTTP 请求 DTO（username / password）。
`dto/LoginResp.java`：负责 login 响应 DTO（token 字段）。
`dto/LogoutReq.java`：负责 logout 请求 DTO（空 struct，adminId 从 JWT claim 解析）。
`dto/LogoutResp.java`：负责 logout 响应 DTO（success）。
`dto/GetSelfReq.java`：负责 getSelf 请求 DTO（空 struct）。
`dto/GetSelfResp.java`：负责 getSelf 响应 DTO（admin 嵌套）。
`dto/UpdateSelfReq.java`：负责 updateSelf 请求 DTO（4 optional 字段）。
`dto/UpdateSelfResp.java`：负责 updateSelf 响应 DTO（admin 嵌套）。
`dto/CreateAdminReq.java`：负责 createAdmin 请求 DTO（username / password / email / invite_code / extra_info）。
`dto/CreateAdminResp.java`：负责 createAdmin 响应 DTO（uid 字符串）。
`dto/CreateInviteCodeReq.java`：负责 createInviteCode 请求 DTO（空 struct）。
`dto/CreateInviteCodeResp.java`：负责 createInviteCode 响应 DTO（invite_code / expires_at）。
`dto/BanUserReq.java`：负责 banUser 请求 DTO（uid 字符串）。
`dto/BanUserResp.java`：负责 banUser 响应 DTO（success）。
`dto/UnBanUserReq.java`：负责 unBanUser 请求 DTO（uid 字符串）。
`dto/UnBanUserResp.java`：负责 unBanUser 响应 DTO（success）。
`dto/GetUserReq.java`：负责 getUser 请求 DTO（uid 字符串）。
`dto/GetUserResp.java`：负责 getUser 响应 DTO（user 嵌套）。
`dto/GetUserListReq.java`：负责 getUserList 请求 DTO（page / page_size / keyword）。
`dto/GetUserListResp.java`：负责 getUserList 响应 DTO（list 数组 / total）。
`dto/UpdateUserReq.java`：负责 updateUser 请求 DTO（uid + 4 optional 字段）。
`dto/UpdateUserResp.java`：负责 updateUser 响应 DTO（success / user）。
`dto/DeleteUserReq.java`：负责 deleteUser 请求 DTO（uid 字符串）。
`dto/DeleteUserResp.java`：负责 deleteUser 响应 DTO（success）。
`dto/ResetUserPasswordReq.java`：负责 resetUserPassword 请求 DTO（uid 字符串）。
`dto/ResetUserPasswordResp.java`：负责 resetUserPassword 响应 DTO（success）。
`service/AdminLoginService.java`：负责 login 用例（4 状态机：Internal → 5001 / NotFound → 1003 / 其它 → 1015 / JWT 签发 → 1006）。
`service/AdminLogoutService.java`：负责 logout 用例。
`service/AdminGetSelfService.java`：负责 getSelf 用例（IdentityResolver.resolveAdmin）。
`service/AdminUpdateSelfService.java`：负责 updateSelf 用例。
`service/AdminCreateService.java`：负责 createAdmin 用例（4 状态机 + invite 验证）。
`service/AdminCreateInviteCodeService.java`：负责 createInviteCode 用例（含 token_invalid metric）。
`service/AdminBanUserService.java`：负责 banUser 用例（3 状态机）。
`service/AdminUnBanUserService.java`：负责 unBanUser 用例（3 状态机）。
`service/AdminGetUserService.java`：负责 getUser 用例。
`service/AdminListUserService.java`：负责 getUserList 用例（循环 fromProto admin.proto.UserInfo）。
`service/AdminUpdateUserService.java`：负责 updateUser 用例（含 DRIFT-NEW-005 updateDbError metric）。
`service/AdminDeleteUserService.java`：负责 deleteUser 用例。
`service/AdminResetUserPasswordService.java`：负责 resetUserPassword 用例。
`controller/AdminController.java`：负责承载 13 个 REST 端点的 `@RequestMapping("/admin/v1") + @PostMapping("/{op}")` Controller，委托给 service/*Service。

# Java 理论文件树
``
admin-api/
├── pom.xml                                                              # 依赖 admin-proto / user-proto / users-common / common / grpc-client-spring-boot-starter
│                                                                          #   + spring-boot-starter-web + redis + nacos-discovery + jjwt + prometheus
└── src/
    └── main/
        ├── java/
        │   └── com/rag/users/admin/api/
        │       ├── AdminApiApplication.java                            # Spring Boot 入口 + @EnableDiscoveryClient
        │       │                                                          #   （scanBasePackages = {"com.rag.users.admin.api","com.rag.common"}）
        │       │
        │       ├── config/
        │       │   ├── AdminApiProperties.java                         # @ConfigurationProperties(prefix = "admin-api")
        │       │   │                                                      #   聚合：name + admin-auth JWT 签名密钥（与 admin-rpc 共享）+
        │       │   │                                                      #   redis + grpc client + nacos + observability
        │       │   └── WebMvcConfig.java                                # 注册 AdminJwtAuthInterceptor 到 /admin/v1/**
        │       │                                                          #   配置 /admin/v1/login + /admin/v1/create 为 public path
        │       │
        │       ├── controller/
        │       │   └── AdminController.java                            # @RequestMapping("/admin/v1")，13 个 @PostMapping 方法
        │       │                                                          #   （合并 13 个用例：login/logout/getSelf/updateSelf/createAdmin/
        │       │                                                          #    createInviteCode/banUser/unBanUser/getUser/getUserList/
        │       │                                                          #    updateUser/deleteUser/resetUserPassword）
        │       │                                                          #   委托给 service/*Service；Response(code, msg, data) 统一响应
        │       │
        │       ├── service/
        │       │   ├── AdminLoginService.java                          # @Service，login 用例（4 状态机）
        │       │   ├── AdminLogoutService.java                         # @Service，logout 用例
        │       │   ├── AdminGetSelfService.java                        # @Service，getSelf 用例
        │       │   ├── AdminUpdateSelfService.java                     # @Service，updateSelf 用例
        │       │   ├── AdminCreateService.java                         # @Service，createAdmin 用例
        │       │   ├── AdminCreateInviteCodeService.java               # @Service，createInviteCode 用例（含 token_invalid）
        │       │   ├── AdminBanUserService.java                        # @Service，banUser 用例（3 状态机）
        │       │   ├── AdminUnBanUserService.java                      # @Service，unBanUser 用例（3 状态机）
        │       │   ├── AdminGetUserService.java                        # @Service，getUser 用例
        │       │   ├── AdminListUserService.java                       # @Service，getUserList 用例
        │       │   ├── AdminUpdateUserService.java                     # @Service，updateUser 用例（含 updateDbError）
        │       │   ├── AdminDeleteUserService.java                     # @Service，deleteUser 用例
        │       │   ├── AdminResetUserPasswordService.java              # @Service，resetUserPassword 用例
        │       │   ├── AdminApiSupport.java                            # helper：extractAdminId / codeFromRPCError /
        │       │   │                                                      #   fromProto(AdminInfo) / fromProto(UserInfo)
        │       │   └── IdentityResolver.java                            # @Component，按 adminId 校验身份契约
        │       │                                                              #   3 个 sentinel exception：InvalidClaim / AdminNotFound /
        │       │                                                              #   IdentityMismatch（admin 域无 status 字段，无 inactive）
        │       │
        │       ├── interceptor/
        │       │   └── AdminJwtAuthInterceptor.java                     # HandlerInterceptor，JWT 解析 + 签名校验 +
        │       │                                                          #   blacklist 业务校验（key prefix admin:jwt_blacklist:），
        │       │                                                          #   把 adminId 注入请求 attribute
        │       │
        │       ├── advice/
        │       │   ├── GlobalResponseAdvice.java                        # @RestControllerAdvice + ResponseBodyAdvice
        │       │   │                                                      #   把任意 Controller 返回值包成 {code, msg, data}
        │       │   └── GlobalExceptionHandler.java                      # @RestControllerAdvice + @ExceptionHandler
        │       │                                                              #   从业务异常还原 {code, msg} 与 HTTP 状态码
        │       │
        │       ├── client/
        │       │   └── AdminRpcClient.java                              # @Component 持有 AdminServiceBlockingStub
        │       │                                                          #   （devh @GrpcClient("admin-rpc")，Nacos discovery）
        │       │
        │       ├── dto/
        │       │   ├── AdminInfo.java                                    # 嵌套 AdminInfo（uid/username/email/extra_info）
        │       │   ├── UserInfo.java                                     # 嵌套 UserInfo（uid/status/username/email/extra_info）
        │       │   ├── LoginReq.java
        │       │   ├── LoginResp.java
        │       │   ├── LogoutReq.java
        │       │   ├── LogoutResp.java
        │       │   ├── GetSelfReq.java
        │       │   ├── GetSelfResp.java
        │       │   ├── UpdateSelfReq.java
        │       │   ├── UpdateSelfResp.java
        │       │   ├── CreateAdminReq.java
        │       │   ├── CreateAdminResp.java
        │       │   ├── CreateInviteCodeReq.java
        │       │   ├── CreateInviteCodeResp.java
        │       │   ├── BanUserReq.java
        │       │   ├── BanUserResp.java
        │       │   ├── UnBanUserReq.java
        │       │   ├── UnBanUserResp.java
        │       │   ├── GetUserReq.java
        │       │   ├── GetUserResp.java
        │       │   ├── GetUserListReq.java
        │       │   ├── GetUserListResp.java
        │       │   ├── UpdateUserReq.java
        │       │   ├── UpdateUserResp.java
        │       │   ├── DeleteUserReq.java
        │       │   ├── DeleteUserResp.java
        │       │   ├── ResetUserPasswordReq.java
        │       │   └── ResetUserPasswordResp.java
        │       │
        │       └── metrics/
        │           └── AdminApiMetrics.java                             # @Component Micrometer
        │                                                                      #   17 Counter：login/ban/unban/delete/reset/update/invite
        │                                                                      #   指标名 admin_api_intercept_total{path=..., result=...}
        │
        └── resources/
            ├── application.yml                                           # admin-api.* 业务段 + grpc.client.admin-rpc + grpc.client.user-rpc +
            │                                                              #   spring.data.redis + spring.cloud.nacos.discovery +
            │                                                              #   service.common.observability.*
            └── logback-spring.xml                                        # ServiceName=admin-api, Mode=file, Path=.../log/admin-api
``