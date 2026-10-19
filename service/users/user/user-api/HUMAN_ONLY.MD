# 文件阅读顺序与理由

阅读顺序：
`UserApiApplication.java` → `config/UserApiProperties.java` → `config/WebMvcConfig.java` → `client/UserRpcClient.java` → `interceptor/JwtAuthInterceptor.java` → `advice/GlobalResponseAdvice.java` → `advice/GlobalExceptionHandler.java` → `service/UserApiSupport.java` → `service/IdentityResolver.java` → `metrics/UserApiMetrics.java` → `dto/UserInfo.java` → `dto/RegisterReq.java` → `dto/RegisterResp.java` → `dto/LoginReq.java` → `dto/LoginResp.java` → `dto/GetUserReq.java` → `dto/GetUserResp.java` → `dto/UpdateUserReq.java` → `dto/UpdateUserResp.java` → `dto/DeleteUserReq.java` → `dto/DeleteUserResp.java` → `dto/SelectAvatarReq.java` → `dto/SelectAvatarResp.java` → `dto/UploadAvatarReq.java` → `dto/UploadAvatarResp.java` → `dto/GetAvatarHistoryReq.java` → `dto/GetAvatarHistoryResp.java` → `dto/AvatarHistoryItem.java` → `service/UserRegisterService.java` → `service/UserLoginService.java` → `service/UserLogoutService.java` → `service/UserGetService.java` → `service/UserUpdateService.java` → `service/UserDeleteService.java` → `service/AvatarSelectService.java` → `service/AvatarUploadService.java` → `service/AvatarHistoryService.java` → `controller/UserController.java`

文件职责：
`UserApiApplication.java`：负责启动 user-api HTTP 网关（端口 49900）的 Spring Boot 入口，`scanBasePackages` 扫 `com.rag.users.user.api` 与 `com.rag.common`，加 `@ConfigurationPropertiesScan`。
`config/UserApiProperties.java`：负责绑定 application.yml 中 user-api.* 配置段的 `@ConfigurationProperties(prefix="user-api")` 类，聚合 server name + user-auth JWT 签名密钥 + redis + grpc client + nacos + observability。
`config/WebMvcConfig.java`：负责注册 JwtAuthInterceptor 到 `/user/v1/**` 路径，配置 `/user/v1/login` 与 `/user/v1/register` 为 public path 不走 JWT 校验。
`client/UserRpcClient.java`：负责持有 `UserServiceBlockingStub`（devh `@GrpcClient("user-rpc")`），Nacos discovery 自动发现 user-rpc 多副本。
`interceptor/JwtAuthInterceptor.java`：负责 HandlerInterceptor 三件事——解析 Authorization Bearer + jjwt 验签 + Redis blacklist 校验，通过后把 userId 注入请求 attribute。
`advice/GlobalResponseAdvice.java`：负责 `@RestControllerAdvice + ResponseBodyAdvice`，把任意 Controller 返回值包成统一 envelope `{code, msg, data}`。
`advice/GlobalExceptionHandler.java`：负责 `@RestControllerAdvice + @ExceptionHandler`，把 BusinessException / StatusRuntimeException / 通用异常还原成 envelope 与 HTTP 状态码。
`service/UserApiSupport.java`：负责 helper 聚合（extractUserId / codeFromRPCError / fromProto 转换 / resolveUserId 委派给 IdentityResolver）。
`service/IdentityResolver.java`：负责按 userId 校验身份契约（5 个 sentinel exception：InvalidClaim / UserNotFound / IdentityMismatch / StatusUnavailable / UserInactive），调 user-rpc getUser 验证 status=0。
`metrics/UserApiMetrics.java`：负责 user-api HTTP 层业务指标收集（多 Counter：login / register / get / update / delete / selectAvatar / uploadAvatar / getAvatarHistory / logout），标签 path + result 复刻 Go 端 user_api_intercept_total。
`dto/UserInfo.java`：负责定义嵌套 UserInfo DTO（uid / status / username / email / extra_info），uid 序列化为字符串。
`dto/RegisterReq.java`：负责 register HTTP 请求 DTO（username / password / email / extra_info）。
`dto/RegisterResp.java`：负责 register 响应 DTO（uid 字符串）。
`dto/LoginReq.java`：负责 login HTTP 请求 DTO（username / password）。
`dto/LoginResp.java`：负责 login 响应 DTO（token 字段）。
`dto/GetUserReq.java`：负责 getUser HTTP 请求 DTO（uid 字符串）。
`dto/GetUserResp.java`：负责 getUser 响应 DTO（user / found）。
`dto/UpdateUserReq.java`：负责 updateUser 请求 DTO（uid + 4 optional 字段，@JsonInclude(NON_NULL)）。
`dto/UpdateUserResp.java`：负责 updateUser 响应 DTO（success）。
`dto/DeleteUserReq.java`：负责 deleteUser 请求 DTO（uid 字符串）。
`dto/DeleteUserResp.java`：负责 deleteUser 响应 DTO（success）。
`dto/SelectAvatarReq.java`：负责 selectAvatar 请求 DTO（avatar_id / history_id）。
`dto/SelectAvatarResp.java`：负责 selectAvatar 响应 DTO（success）。
`dto/UploadAvatarReq.java`：负责 uploadAvatar 请求 DTO（avatar_url / content_type / size_bytes）。
`dto/UploadAvatarResp.java`：负责 uploadAvatar 响应 DTO（history_id / is_current / create_time）。
`dto/GetAvatarHistoryReq.java`：负责 getAvatarHistory 请求 DTO（uid 字符串）。
`dto/GetAvatarHistoryResp.java`：负责 getAvatarHistory 响应 DTO（list 数组）。
`dto/AvatarHistoryItem.java`：负责嵌套头像历史项 DTO（id / avatar_url / content_type / size_bytes / is_current / create_time），snake_case 字段。
`service/UserRegisterService.java`：负责 register 用例（参数校验 → 调 user-rpc 创建用户 → 自动登录签 token）。
`service/UserLoginService.java`：负责 login 用例（参数校验 → 调 user-rpc 校验账密 → JwtUtil.getToken() → 返回 token）。
`service/UserLogoutService.java`：负责 logout 用例（撤销 token 完成登出）。
`service/UserGetService.java`：负责 getUser 用例（调 user-rpc 读取用户信息）。
`service/UserUpdateService.java`：负责 updateUser 用例（调 user-rpc 更新用户资料）。
`service/UserDeleteService.java`：负责 deleteUser 用例（调 user-rpc 删除用户）。
`service/AvatarSelectService.java`：负责 selectAvatar 用例（调 user-rpc 选择预置头像）。
`service/AvatarUploadService.java`：负责 uploadAvatar 用例（调 user-rpc 上传头像）。
`service/AvatarHistoryService.java`：负责 getAvatarHistory 用例（调 user-rpc 查询头像历史）。
`controller/UserController.java`：负责承载 9 个 REST 端点的 `@RequestMapping("/user/v1") + @PostMapping("/{op}")` Controller，委托给 service/*Service。

# Java 理论文件树
``
user-api/
├── pom.xml                                                              # 依赖 user-proto / users-common / common / grpc-client-spring-boot-starter
│                                                                          #   + spring-boot-starter-web + redis + nacos-discovery + jjwt + prometheus
└── src/
    └── main/
        ├── java/
        │   └── com/rag/users/user/api/
        │       ├── UserApiApplication.java                              # Spring Boot 入口 + @EnableDiscoveryClient
        │       │                                                          #   （scanBasePackages = {"com.rag.users.user.api","com.rag.common"}）
        │       │
        │       ├── config/
        │       │   ├── UserApiProperties.java                           # @ConfigurationProperties(prefix = "user-api")
        │       │   │                                                      #   聚合：name + 业务 redis + 下游 user-rpc grpc client + JWT 签名密钥
        │       │   └── WebMvcConfig.java                                  # 注册 JwtAuthInterceptor 到 /user/v1/**
        │       │                                                          #   配置 /user/v1/login + /user/v1/register 为 public path
        │       │
        │       ├── controller/
        │       │   └── UserController.java                              # @RequestMapping("/user/v1")，9 个 @PostMapping 方法
        │       │                                                          #   （合并 9 个用例：register / login / logout / getUser / updateUser /
        │       │                                                          #    deleteUser / selectAvatar / uploadAvatar / getAvatarHistory）
        │       │                                                          #   委托给 service/*Service；Response(code, msg, data) 统一响应
        │       │
        │       ├── service/
        │       │   ├── UserRegisterService.java                         # @Service，register 用例
        │       │   │                                                      #   （参数校验 → 调 user-rpc 创建用户 → 自动登录签 token）
        │       │   ├── UserLoginService.java                            # @Service，login 用例
        │       │   │                                                      #   （参数校验 → 调 user-rpc 校验账密 → JwtUtil.getToken(...) → 返回 token）
        │       │   ├── UserLogoutService.java                           # @Service，logout 用例
        │       │   ├── UserGetService.java                              # @Service，getUser 用例
        │       │   ├── UserUpdateService.java                           # @Service，updateUser 用例
        │       │   ├── UserDeleteService.java                           # @Service，deleteUser 用例
        │       │   ├── AvatarSelectService.java                         # @Service，selectAvatar 用例
        │       │   ├── AvatarUploadService.java                         # @Service，uploadAvatar 用例
        │       │   ├── AvatarHistoryService.java                       # @Service，getAvatarHistory 用例
        │       │   ├── UserApiSupport.java                              # helper：extractUserId / userLogOption /
        │       │   │                                                      #   codeFromRPCError / resolveUserId / fromProto(UserInfo)
        │       │   └── IdentityResolver.java                             # @Component，按 userId 校验身份契约
        │       │                                                              #   5 个 sentinel exception：InvalidClaim / UserNotFound /
        │       │                                                              #   IdentityMismatch / StatusUnavailable / UserInactive
        │       │
        │       ├── interceptor/
        │       │   └── JwtAuthInterceptor.java                          # HandlerInterceptor，JWT 解析 + 签名校验 +
        │       │                                                          #   blacklist 业务校验，把 userId 注入请求 attribute
        │       │
        │       ├── advice/
        │       │   ├── GlobalResponseAdvice.java                        # @RestControllerAdvice + ResponseBodyAdvice
        │       │   │                                                      #   把任意 Controller 返回值包成 {code, msg, data}
        │       │   └── GlobalExceptionHandler.java                      # @RestControllerAdvice + @ExceptionHandler
        │       │                                                              #   从业务异常还原 {code, msg} 与 HTTP 状态码
        │       │
        │       ├── client/
        │       │   └── UserRpcClient.java                               # @Component 持有 UserServiceBlockingStub
        │       │                                                          #   （devh @GrpcClient("user-rpc")，Nacos discovery）
        │       │
        │       ├── dto/
        │       │   ├── RegisterReq.java
        │       │   ├── RegisterResp.java
        │       │   ├── LoginReq.java
        │       │   ├── LoginResp.java
        │       │   ├── GetUserReq.java
        │       │   ├── GetUserResp.java
        │       │   ├── UpdateUserReq.java
        │       │   ├── UpdateUserResp.java
        │       │   ├── DeleteUserReq.java
        │       │   ├── DeleteUserResp.java
        │       │   ├── SelectAvatarReq.java
        │       │   ├── SelectAvatarResp.java
        │       │   ├── UploadAvatarReq.java
        │       │   ├── UploadAvatarResp.java
        │       │   ├── GetAvatarHistoryReq.java
        │       │   ├── GetAvatarHistoryResp.java
        │       │   ├── AvatarHistoryItem.java                            # 嵌套头像历史项（id/avatar_url/content_type/size_bytes/is_current/create_time）
        │       │   └── UserInfo.java                                      # 嵌套用户信息（id/username/email/extra_info/score/status）
        │       │
        │       └── metrics/
        │           └── UserApiMetrics.java                              # @Component Micrometer
        │                                                                      #   多 Counter：login/register/get/update/delete/selectAvatar/
        │                                                                      #   uploadAvatar/getAvatarHistory/logout
        │                                                                      #   指标名 user_api_intercept_total{path=..., result=...}
        │
        └── resources/
            ├── application.yml                                           # user-api.* 业务段 + grpc.client.user-rpc +
            │                                                              #   spring.data.redis + spring.cloud.nacos.discovery +
            │                                                              #   service.common.observability.*
            └── logback-spring.xml                                        # ServiceName=user-api, Mode=file, Path=.../log/user-api
``