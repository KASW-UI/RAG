# 文件阅读顺序与理由

阅读顺序：
`UserRpcApplication.java` → `bootstrap/NacosRegistration.java` 
→ `bootstrap/SnowflakeBootstrap.java` → `config/UserRpcProperties.java` 
→ `domain/UserEntity.java` → `domain/UserAvatarHistoryEntity.java` 
→ `exception/UserExceptions.java` → `mapper/UserMapper.java` 
→ `mapper/UserAvatarHistoryMapper.java` → `metrics/UserRpcMetrics.java` 
→ `service/UserDomainService.java` → `service/AvatarDomainService.java` 
→ `service/impl/UserServiceGrpcImpl.java`

文件职责：
`UserRpcApplication.java`：负责启动 user-rpc gRPC server 的 Spring Boot 入口，`scanBasePackages` 扫 `com.rag.users.user.rpc` 与 `com.rag.common`，加 `@ConfigurationPropertiesScan` 让 4 个 Properties 类生效。
`bootstrap/NacosRegistration.java`：负责向 Nacos 注册 user-rpc 服务的 `@Component` Bean，端口 49210，让 admin-rpc / user-api 通过 `discovery://user-rpc` 自动发现。
`bootstrap/SnowflakeBootstrap.java`：负责初始化全局雪花 ID 生成器的 `@Bean`（Snowflake 来自 common 模块），`@PostConstruct` 注入 workerId/datacenterId，让 `@TableId(type=IdType.ASSIGN_ID)` 走统一 ID 源。
`config/UserRpcProperties.java`：负责绑定 application.yml 中 user-rpc.* 配置段的 `@ConfigurationProperties(prefix="user-rpc")` 类，聚合 server 端口 + grpc server starter + redis + nacos + observability。
`domain/UserEntity.java`：负责定义对应 users_user 表的 MyBatis-Plus 实体（`@TableName("users_user")` + `@TableId(type=IdType.ASSIGN_ID)`），字段含 uid / username / password / email / status / score / extra_info。
`domain/UserAvatarHistoryEntity.java`：负责定义对应 user_avatar_history 表的实体，`extra_info` 字段配 JacksonTypeHandler 实现 JSONB ↔ Map<String,String> 互转。
`exception/UserExceptions.java`：负责聚合 user 域业务异常（NotFoundException / UsernameTakenException / WrongPasswordException / UserBannedException 等），全部继承 RuntimeException。
`mapper/UserMapper.java`：负责 users_user 表的 MyBatis-Plus 数据访问接口（`extends BaseMapper<UserEntity>`），注入到 UserDomainService。
`mapper/UserAvatarHistoryMapper.java`：负责 user_avatar_history 表的数据访问接口。
`metrics/UserRpcMetrics.java`：负责 user-rpc gRPC 层业务指标收集（8 Counter：login 4 + register 3 + getAvatarHistory 1），标签 path + result 复刻 Go 端 user_rpc_intercept_total。
`service/UserDomainService.java`：负责承载 9 个业务用例方法（register / login / getUser / updateUser / deleteUser / selectAvatar / uploadAvatar / getAvatarHistory / logout）的 DomainService，事务方法走 `@Transactional default` 接口默认方法（让 Spring 代理生效）。
`service/AvatarDomainService.java`：负责 avatar 字段归一化与转换工具（解析 + 写库 + 切历史）。
`service/impl/UserServiceGrpcImpl.java`：负责 gRPC 服务端实现，把 9 个 RPC 方法委派给 UserDomainService / AvatarDomainService。

# Java 理论文件树
``
user-rpc/
├── pom.xml                                                              # 依赖 user-proto / users-common / common / grpc-server-spring-boot-starter
│                                                                          #   + mybatis-plus-boot-starter + postgresql + nacos-discovery
└── src/
    └── main/
        ├── java/
        │   └── com/rag/users/user/rpc/
        │       ├── UserRpcApplication.java                              # Spring Boot 入口 + @EnableDiscoveryClient
        │       │                                                          #   （scanBasePackages = {"com.rag.users.user.rpc","com.rag.common"}）
        │       │
        │       ├── bootstrap/
        │       │   ├── NacosRegistration.java                           # @Component，向 Nacos 注册 user-rpc 服务
        │       │   │                                                      #   （端口 49210，spring.cloud.nacos.discovery 自动装配）
        │       │   └── SnowflakeBootstrap.java                          # @Component，初始化 Snowflake @Bean
        │       │                                                          #   （workerId/datacenterId 来自 user-rpc.snowflake.* 配置）
        │       │
        │       ├── config/
        │       │   └── UserRpcProperties.java                           # @ConfigurationProperties(prefix = "user-rpc")
        │       │                                                          #   聚合：server 端口 + grpc server starter + redis + nacos + observability
        │       │
        │       ├── domain/
        │       │   ├── UserEntity.java                                  # @TableName("users_user") MyBatis-Plus 实体
        │       │   │                                                      #   字段：uid (雪花) / username / password / email / status / score / extra_info (JSONB)
        │       │   └── UserAvatarHistoryEntity.java                     # @TableName("user_avatar_history") 实体
        │       │                                                              #   extra_info 配 JacksonTypeHandler JSONB ↔ Map
        │       │
        │       ├── exception/
        │       │   └── UserExceptions.java                              # 业务异常聚合：NotFound / UsernameTaken / WrongPassword /
        │       │                                                              #   UserBanned 等，全部 extends RuntimeException
        │       │
        │       ├── mapper/
        │       │   ├── UserMapper.java                                  # extends BaseMapper<UserEntity>
        │       │   │                                                      #   提供 selectById / insert / updateById / deleteById + LambdaQueryWrapper
        │       │   └── UserAvatarHistoryMapper.java                     # extends BaseMapper<UserAvatarHistoryEntity>
        │       │
        │       ├── metrics/
        │       │   └── UserRpcMetrics.java                              # @Component Micrometer
        │       │                                                              #   8 个 Counter：login / register / getAvatarHistory
        │       │                                                              #   指标名 user_rpc_intercept_total{path=..., result=...}
        │       │
        │       ├── service/
        │       │   ├── UserDomainService.java                           # @Service，9 个业务用例方法（register/login/getUser/updateUser/deleteUser/
        │       │   │                                                      #   selectAvatar/uploadAvatar/getAvatarHistory/logout）
        │       │   │                                                      #   事务走 @Transactional default 方法（Spring 代理在公共入口）
        │       │   ├── AvatarDomainService.java                          # avatar helper：解析 / 写库 / 切历史
        │       │   │
        │       │   └── impl/
        │       │       └── UserServiceGrpcImpl.java                     # @Component，gRPC 服务端实现
        │       │                                                              #   委派 9 个 RPC 方法到 UserDomainService / AvatarDomainService
        │
        └── resources/
            ├── application.yml                                           # user-rpc.* + grpc.server.port=49210 + nacos.discovery +
            │                                                              #   spring.datasource + mybatis-plus + flyway + redis
            ├── db/                                                          # ⚠️ 不在本模块，见下
            │                                                              #   2026-10-21 起 users 域 DDL 全部收归
            │                                                              #   users-common/src/main/resources/db/：
            │                                                              #     01_create_user.sql                  建 users_user
            │                                                              #     02_create_user_avatar_history.sql   建 users_user_avatar_history
            │                                                              #     03_create_admin.sql                 建 admin_admin
            │                                                              #     04_create_admin_invite.sql          建 admin_invite
            │                                                              #   由 bootstrap/UserSchemaBootstrap 在 @PostConstruct 里
            │                                                              #   调 users-common 的 SchemaInitializer 原样重放
            │                                                              #   （全部 CREATE ... IF NOT EXISTS，无账本无版本号）
            │                                                              #   Go 出处：user/rpc/internal/model/db.go:33 AutoMigrate
            └── logback-spring.xml                                        # ServiceName=user-rpc, Mode=file, Path=.../log/user-rpc
``