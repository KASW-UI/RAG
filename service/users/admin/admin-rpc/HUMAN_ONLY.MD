# 文件阅读顺序与理由

阅读顺序：
`AdminRpcApplication.java` → `bootstrap/NacosRegistration.java` 
→ `bootstrap/SnowflakeBootstrap.java` → `bootstrap/AdminSeederBootstrap.java` 
→ `config/AdminRpcProperties.java` → `domain/AdminEntity.java` 
→ `domain/AdminInviteEntity.java` → `domain/UserEntity.java`
→ `exception/AdminExceptions.java` → `mapper/AdminMapper.java` 
→ `mapper/AdminInviteMapper.java` → `mapper/UserMapper.java` 
→ `metrics/AdminRpcMetrics.java` → `service/AdminHelpers.java` 
→ `service/AdminDomainService.java` → `service/AdminServiceGrpcImpl.java`

文件职责：
`AdminRpcApplication.java`：负责启动 admin-rpc gRPC server 的 Spring Boot 入口，`scanBasePackages` 扫 `com.rag.users.admin.rpc` 与 `com.rag.common`，加 `@ConfigurationPropertiesScan`。
`bootstrap/NacosRegistration.java`：负责向 Nacos 注册 admin-rpc 服务（端口 49220）的 `@Component` Bean，让 admin-api 通过 `discovery://admin-rpc` 自动发现。
`bootstrap/SnowflakeBootstrap.java`：负责初始化全局雪花 ID 生成器的 `@Bean`，workerId/datacenterId 来自 admin-rpc.snowflake.* 配置。
`bootstrap/AdminSeederBootstrap.java`：负责应用启动时种子化 root 管理员账号（`ApplicationRunner`），如果 admin_admin 表为空则插入默认 root。
`config/AdminRpcProperties.java`：负责绑定 application.yml 中 admin-rpc.* 配置段的 `@ConfigurationProperties(prefix="admin-rpc")` 类，聚合 server 端口 + JWT 校验密钥 + redis + nacos + observability。
`domain/AdminEntity.java`：负责定义对应 admin_admin 表的 MyBatis-Plus 实体（`@TableName("admin_admin")` + `@TableId(type=IdType.ASSIGN_ID)`），字段含 uid / username / password / email / extra_info。
`domain/AdminInviteEntity.java`：负责定义 admin_invite 表的实体（invite_code / expires_at / used_by_uid / created_at）。
`domain/UserEntity.java`：负责借用 user 表实体（admin-rpc 在 getUser / getUserList / updateUser / deleteUser / banUser / unBanUser / resetUserPassword 时操作 user 表，与 user-rpc 共享 user 表）。
`exception/AdminExceptions.java`：负责聚合 admin 域业务异常（NotFoundException / InviteUnavailableException / UsernameTakenException / isUniqueViolation 等）。
`mapper/AdminMapper.java`：负责 admin_admin 表的 MyBatis-Plus 数据访问接口（`extends BaseMapper<AdminEntity>`）。
`mapper/AdminInviteMapper.java`：负责 admin_invite 表的数据访问接口。
`mapper/UserMapper.java`：负责 users_user 表的访问接口（admin-rpc 借用）。
`metrics/AdminRpcMetrics.java`：负责 admin-rpc gRPC 层业务指标收集（25 Counter：login 4 + createInvite 4 + update 4 + delete 3 + resetPassword 4 + ban 3 + unban 3 + createAdmin 4），标签 path + result 复刻 Go 端 admin_rpc_intercept_total。
`service/AdminHelpers.java`：负责 admin 域 helper 聚合（密码哈希 / 邀请码生成 / 时间工具）。
`service/AdminDomainService.java`：负责承载 13 个业务用例方法（login/logout/getSelf/updateSelf/createAdmin/createInviteCode/banUser/unBanUser/getUser/getUserList/updateUser/deleteUser/resetUserPassword）的 DomainService，13 用例合 1；createAdminAtomic 走 `@Transactional default` 接口默认方法（4 步原子事务：插 invite / 插 admin / 标 invite used / 写密码）。
`service/AdminServiceGrpcImpl.java`：负责 gRPC 服务端实现，把 13 个 RPC 方法委派给 AdminDomainService。

# Java 理论文件树
``
admin-rpc/
├── pom.xml                                                              # 依赖 admin-proto / users-common / common / grpc-server-spring-boot-starter
│                                                                          #   + mybatis-plus-boot-starter + flyway-core + mysql-connector-j + nacos-discovery
└── src/
    └── main/
        ├── java/
        │   └── com/rag/users/admin/rpc/
        │       ├── AdminRpcApplication.java                             # Spring Boot 入口 + @EnableDiscoveryClient
        │       │                                                          #   （scanBasePackages = {"com.rag.users.admin.rpc","com.rag.common"}）
        │       │
        │       ├── bootstrap/
        │       │   ├── NacosRegistration.java                           # @Component，向 Nacos 注册 admin-rpc 服务
        │       │   │                                                      #   （端口 49220，spring.cloud.nacos.discovery 自动装配）
        │       │   ├── SnowflakeBootstrap.java                          # @Component，初始化 Snowflake @Bean
        │       │   │                                                      #   （workerId/datacenterId 来自 admin-rpc.snowflake.* 配置）
        │       │   └── AdminSeederBootstrap.java                       # @Component，ApplicationRunner
        │       │                                                              #   启动时种子化 root admin 账号（如表空则插入）
        │       │
        │       ├── config/
        │       │   └── AdminRpcProperties.java                          # @ConfigurationProperties(prefix = "admin-rpc")
        │       │                                                              #   聚合：server 端口 + JWT 校验密钥 + redis + nacos + observability
        │       │
        │       ├── domain/
        │       │   ├── AdminEntity.java                                 # @TableName("admin_admin") MyBatis-Plus 实体
        │       │   │                                                      #   字段：uid (雪花) / password / email / extra_info (JSONB)
        │       │   ├── AdminInviteEntity.java                           # @TableName("admin_invite") 实体
        │       │   │                                                      #   字段：invite_code / expires_at / used_by_uid / created_at
        │       │   └── UserEntity.java                                   # @TableName("users_user") 复用 user-rpc 同款
        │       │                                                              #   admin-rpc 在 ban/unBan/updateUser 等操作 user 表
        │       │
        │       ├── exception/
        │       │   └── AdminExceptions.java                              # 业务异常聚合：NotFound / InviteUnavailable /
        │       │                                                              #   UsernameTaken / isUniqueViolation
        │       │
        │       ├── mapper/
        │       │   ├── AdminMapper.java                                 # extends BaseMapper<AdminEntity>
        │       │   ├── AdminInviteMapper.java                           # extends BaseMapper<AdminInviteEntity>
        │       │   └── UserMapper.java                                   # extends BaseMapper<UserEntity>（复用）
        │       │
        │       ├── metrics/
        │       │   └── AdminRpcMetrics.java                              # @Component Micrometer
        │       │                                                              #   25 Counter：login / createInvite / update / delete / resetPassword /
        │       │                                                              #   ban / unban / createAdmin
        │       │                                                              #   指标名 admin_rpc_intercept_total{path=..., result=...}
        │       │
        │       └── service/
        │           ├── AdminHelpers.java                                 # helper：密码哈希 / 邀请码生成 / 时间工具
        │           ├── AdminDomainService.java                          # @Service，13 个业务用例方法合 1
        │           │                                                      #   （login/logout/getSelf/updateSelf/createAdmin/createInviteCode/
        │           │                                                      #    banUser/unBanUser/getUser/getUserList/updateUser/deleteUser/resetUserPassword）
        │           │                                                      #   createAdminAtomic 走 @Transactional default 方法（4 步事务）
        │           │
        │           └── AdminServiceGrpcImpl.java                         # @Component，gRPC 服务端实现
        │                                                                          #   委派 13 个 RPC 方法到 AdminDomainService
        │
        └── resources/
            ├── application.yml                                           # admin-rpc.* + grpc.server.port=49220 + nacos.discovery +
            │                                                              #   spring.datasource + mybatis-plus + flyway + redis
            ├── db/migration/
            │   ├── V1__init_admin_admin.sql                              # 建 admin_admin 表
            │   ├── V2__init_admin_invite.sql                            # 建 admin_invite 表
            │   └── V3__add_admin_indexes.sql                            # 加索引
            └── logback-spring.xml                                        # ServiceName=admin-rpc, Mode=file, Path=.../log/admin-rpc
``