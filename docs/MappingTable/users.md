# MappingTable.md — Sea-RideTheWind_java ↔ RAG 对照表

> **本表目的**：把 `Sea-RideTheWind_java`（B 列，1:1 Go 镜像）映射到 `RAG`（C 列，Spring idiom 目标架构），方便边改 RAG 边对照、面试前复述。
>
> **A 列**：Go 真理之源 `Sea-RideTheWind_golang/service/user/...`
> **B 列**：`Sea-RideTheWind_java/service/users/...` （镜像）
> **C 列**：`RAG/service/users/...` （目标）
>
> 字段映射：A 文件/符号 → B 文件/符号 → C 文件/符号 + 映射类型（1:1 / 1:N / N:1）

---

## 0. 顶层模块对照

| A (Go) | B (Sea-Java) | C (RAG) | 映射 | 备注 |
|---|---|---|---|---|
| `service/user/user/rpc/` | `service/users/user/user-rpc/` | 同左 | 1:1 | 同名沿用 |
| `service/user/user/api/` | `service/users/user/user-api/` | 同左 | 1:1 | 同名沿用 |
| `service/user/admin/rpc/` | `service/users/admin/admin-rpc/` | 同左 | 1:1 | 同名沿用 |
| `service/user/admin/api/` | `service/users/admin/admin-api/` | 同左 | 1:1 | 同名沿用 |

---

## 1. RPC 层（user-rpc / admin-rpc）

### 1.1 user-rpc 包结构对照

| A Go 文件 | B Sea-Java 文件 | C RAG 文件 | 映射 | 备注 |
|---|---|---|---|---|
| `rpc/internal/config/config.go` | `internal/config/Config.java` | `config/UserRpcProperties.java` | 1:1 | B 用 static final；C 改 `@ConfigurationProperties` |
| `rpc/internal/svc/servicecontext.go` | `internal/svc/ServiceContext.java` | `bootstrap/SnowflakeBootstrap.java` + `bootstrap/NacosRegistration.java` + `config/UserRpcProperties.java` | 1:N | B 单文件聚合；C 拆 N 个 `@Component`/`@Bean` |
| `rpc/internal/model/db.go` + `data_model.go` | `internal/model/DataModel.java` | `mapper/UserMapper.java` + `mapper/UserAvatarHistoryMapper.java` + `domain/UserEntity.java` + `domain/UserAvatarHistoryEntity.java` + `bootstrap/SnowflakeBootstrap.java` | 1:N | B 单 DataModel 静态方法；C 拆 Mapper 接口 + Entity POJO + Snowflake Bean |
| `rpc/internal/model/user.go` | `internal/model/User.java` | `domain/UserEntity.java` | 1:1 | 用户表 ORM |
| `rpc/internal/model/uservatarhistory.go`（Go 内联在 user.go） | `internal/model/UserAvatarHistory.java` | `domain/UserAvatarHistoryEntity.java` | 1:1 | 头像历史表 |
| `rpc/internal/model/vars.go` | `internal/model/UserExceptions.java` | `exception/UserExceptions.java` | 1:1 | 自定义业务异常 |
| `rpc/internal/logic/<casename>.go` ×9 | `internal/logic/<Casename>Logic.java` ×9 | `service/UserDomainService.java`（9 方法合 1）+ `service/AvatarDomainService.java` | N:1 | C 合并 13 个用例到 1-2 个 DomainService |
| `rpc/internal/logic/avatarhelpers.go` | `internal/logic/AvatarHelpers.java` | `service/AvatarDomainService.java`（静态方法） | 1:1 | avatar helper |
| `rpc/internal/server/userserviceserver.go` | `internal/server/UserServiceServer.java` | `service/impl/UserServiceGrpcImpl.java` | 1:1 | gRPC 实现 |
| `rpc/internal/metrics/metrics.go` | `internal/metrics/Metrics.java` | `metrics/UserRpcMetrics.java`（8 Counter） | 1:1 | Counter 包装 |
| - | - | `bootstrap/SnowflakeBootstrap.java` | C 新增 | 雪花 ID Bean，B 用 static |
| - | - | `bootstrap/NacosRegistration.java` | C 新增 | Nacos 注册 Bean，B 写 ServiceContext 启动钩子 |
| `rpc/user.go` 入口 | `UserRpcApplication.java` + `main()` + ServiceContext 钩子 | `UserRpcApplication.java` + `@SpringBootApplication` | 1:1 | 启动入口；C 改注解式 |

### 1.2 admin-rpc 包结构对照

| A Go 文件 | B Sea-Java 文件 | C RAG 文件 | 映射 | 备注 |
|---|---|---|---|---|
| `rpc/internal/config/config.go` | `internal/config/Config.java` | `config/AdminRpcProperties.java` | 1:1 | 同 user-rpc |
| `rpc/internal/svc/servicecontext.go` | `internal/svc/ServiceContext.java` | `bootstrap/AdminSeederBootstrap.java` + `bootstrap/NacosRegistration.java` + `bootstrap/SnowflakeBootstrap.java` | 1:N | C 新增 AdminSeederBootstrap（默认 root admin 种子） |
| `rpc/internal/model/admin.go` | `internal/model/Admin.java` | `domain/AdminEntity.java`（`@TableName("admin_admin")`） | 1:1 | 管理员表，表名 Sea-RideTheWind_java 用 `admins`，RAG 改名 `admin_admin` 区分 |
| `rpc/internal/model/admininvite.go` | `internal/model/AdminInvite.java` | `domain/AdminInviteEntity.java`（`@TableName("admin_invite")`） | 1:1 | 邀请码表 |
| `rpc/internal/model/user.go` | `internal/model/User.java` | `domain/UserEntity.java` | 1:1 | admin-rpc 借用 User 表 |
| `rpc/internal/model/vars.go` | `internal/model/AdminExceptions.java` | `exception/AdminExceptions.java` | 1:1 | 业务异常 |
| `rpc/internal/model/data_model.go` + `db.go` | `internal/model/DataModel.java` | `mapper/AdminMapper.java` + `mapper/AdminInviteMapper.java` + `mapper/UserMapper.java` | 1:N | C 拆 3 个 Mapper |
| `rpc/internal/logic/<casename>.go` ×13 | `internal/logic/<Casename>Logic.java` ×13 | `service/AdminDomainService.java`（13 方法合 1 + createAdminAtomic 4 步事务 default 方法） | N:1 | C 合 13 用例到 1 个 DomainService |
| `rpc/internal/logic/helper.go` | `internal/logic/AdminHelpers.java` | `service/AdminHelpers.java` | 1:1 | helper |
| `rpc/internal/server/adminserviceserver.go` | `internal/server/AdminServiceServer.java` | `service/AdminServiceGrpcImpl.java` | 1:1 | gRPC 实现 |
| `rpc/internal/metrics/metrics.go` | `internal/metrics/Metrics.java` | `metrics/AdminRpcMetrics.java`（25 Counter） | 1:1 | Counter |
| - | - | `bootstrap/AdminSeederBootstrap.java` | C 新增 | 默认 root admin 种子，B 无 |

### 1.3 RPC 层持久层差异

| 维度 | B Sea-Java（JPA） | C RAG（MyBatis-Plus） | 备注 |
|---|---|---|---|
| Entity 注解 | `@Entity @Table @Id @Column` | `@TableName("xxx") @TableId(type=IdType.ASSIGN_ID)` | 表名显式声明 |
| 数据访问 | `Repository extends JpaRepository<E,T>` + `@Query` | `Mapper extends BaseMapper<E>` + `LambdaQueryWrapper` | Spring Data 方法名派生 vs MyBatis-Plus 内置 CRUD |
| Schema 演进 | 无 Flyway | Flyway `V1__init.sql` / `V2__add_field.sql` | C 显式管 schema 历史 |
| 雪花 ID | `IdGenerator.nextId()` static | `SnowflakeBootstrap.@Bean Snowflake` 注入 | B 全局 static；C Spring Bean |
| 事务 | `@Transactional` 在 Logic 类 | `@Transactional` 在 DomainService **default 方法** | C 走接口 default 让事务起作用（Spring 代理要求 public 方法在外部调用） |

### 1.4 RPC 层决策锁

**换持久层（JPA → MyBatis-Plus）4 处同时改**：
1. pom 删 `spring-boot-starter-data-jpa` + 加 `mybatis-plus-bom 3.5.14` + `mybatis-plus-boot-starter` + `flyway-core`
2. Entity 换注解：`@Entity @Id @GeneratedValue` → `@TableName("xxx") @TableId(type=IdType.ASSIGN_ID)`，字段名不变
3. Repository 接口删，新建 `Mapper extends BaseMapper<Entity>`；调用点 `repo.findById(...)` → `mapper.selectById(...)`
4. Schema 来源：B 靠 JPA `ddl-auto=update` 隐式建表；C 改成 Flyway V1/V2/V3 显式迁移

**为什么换**：JPA 隐式 DDL 不适合多人多环境；MyBatis-Plus `LambdaQueryWrapper` 比 Spring Data 方法名派生更直观；Flyway 显式管 schema 历史。

---

## 2. API 层（user-api / admin-api）

### 2.1 user-api 包结构对照

| A Go 文件 | B Sea-Java 文件 | C RAG 文件 | 映射 | 备注 |
|---|---|---|---|---|
| `api/internal/handler/routes.go` + `handler/user/<x>handler.go` ×9 | `internal/handler/UserHandler.java`（1 文件 9 case） | `controller/UserController.java`（9 `@PostMapping`） | 1:N | B 1 个 Handler 装 9 case；C 注解式拆 9 个 |
| `api/internal/logic/<casename>.go` ×9 | `internal/logic/<Casename>Logic.java` ×9 | `service/<Casename>Service.java` ×9（命名不带 Logic 后缀） | 1:1 | 业务方法体；C 去 `Logic` 后缀 |
| `api/internal/logic/LogicResult.go` | `internal/logic/LogicResult.java` | `advice/GlobalResponseAdvice.java` + `advice/GlobalExceptionHandler.java` | 1:1 | B 用 LogicResult 双返回；C 用 throw + Advice |
| `api/internal/logic/avatarhelpers.go` | `internal/logic/AvatarHelpers.java` | `service/AvatarDomainService.java` 静态方法（被各 Service 调） | 1:1 | helper |
| `api/internal/types/types.go`（单文件 500+ 行） | `internal/types/ApiTypes.java`（单文件） | `dto/<Case>Req.java` + `<Case>Resp.java` + 嵌套 `UserInfo.java` / `AvatarHistoryItem.java` 等 | 1:N | B 1 文件装所有 type；C 每 case 一文件 |
| `api/internal/middleware/checkblacklistmiddleware.go` | `internal/filter/CheckBlacklistFilter.java` | `interceptor/JwtAuthInterceptor.java`（合并 JWT 验签 + blacklist） | 1:1 | C 把 2 个合并成 1 个 Interceptor |
| `api/internal/middleware/jwtmiddleware.go` | `internal/filter/JwtAuthenticationFilter.java` | 同上（合并） | 1:1 | 同上 |
| `api/internal/identity/resolve.go` | `internal/identity/IdentityResolver.java` | `service/IdentityResolver.java` | 1:1 | claim 解析 + 5 个 sentinel exception |
| `api/internal/svc/servicecontext.go` | `internal/svc/ServiceContext.java` | `client/UserRpcClient.java` + `config/UserApiProperties.java` + `config/WebMvcConfig.java` | 1:N | B 单 ServiceContext 装所有；C 拆 RPC client + Config + WebMvcConfig 三个 Bean |
| `api/internal/metrics/metrics.go` | `internal/metrics/Metrics.java` | `metrics/UserApiMetrics.java`（多 Counter） | 1:1 | Counter |
| `api/usercenter.go` 入口 | `UserApiApplication.java` | 同 + `@SpringBootApplication` + `@ConfigurationPropertiesScan` | 1:1 | 启动入口 |

### 2.2 admin-api 包结构对照

| A Go 文件 | B Sea-Java 文件 | C RAG 文件 | 映射 | 备注 |
|---|---|---|---|---|
| `api/internal/handler/routes.go` + `handler/admin/<x>handler.go` ×13 | `internal/handler/AdminHandler.java`（1 文件 13 case） | `controller/AdminController.java`（13 `@PostMapping /admin/v1/{op}`） | 1:N | 同 user-api |
| `api/internal/logic/<casename>.go` ×13 | `internal/logic/<Casename>Logic.java` ×13 | `service/Admin<Casename>Service.java` ×13 | 1:1 | 业务方法体 |
| `api/internal/logic/LogicResult.go` | `internal/logic/LogicResult.java` | `advice/GlobalResponseAdvice.java` + `advice/GlobalExceptionHandler.java` | 1:1 | 同 user-api |
| `api/internal/types/types.go` | `internal/types/ApiTypes.java` | `dto/<Case>Req.java` + `<Case>Resp.java` × 28 文件（含嵌套 `AdminInfo.java` / `UserInfo.java`） | 1:N | admin-api DTO 比 user-api 多（13 用例 vs 9） |
| `api/internal/middleware/checkblacklistmiddleware.go` | `internal/filter/CheckBlacklistFilter.java` | `interceptor/AdminJwtAuthInterceptor.java`（合并） | 1:1 | Redis blacklist key prefix `admin:jwt_blacklist:` 与 user-api 的 `user:jwt_blacklist:` 区分 |
| `api/internal/middleware/jwtmiddleware.go` | `internal/filter/JwtAuthenticationFilter.java` | 同上（合并） | 1:1 | |
| `api/internal/identity/resolve.go` | `internal/identity/IdentityResolver.java` | `service/IdentityResolver.java` | 1:1 | admin 域 `AdminInfo` 无 status 字段 → 只有 3 个 sentinel exception（InvalidClaim / AdminNotFound / IdentityMismatch），比 user-api 的 5 个少 2 个 |
| `api/internal/svc/servicecontext.go` | `internal/svc/ServiceContext.java` | `client/AdminRpcClient.java` + `config/AdminApiProperties.java` + `config/WebMvcConfig.java` | 1:N | |
| `api/internal/metrics/metrics.go` | `internal/metrics/Metrics.java` | `metrics/AdminApiMetrics.java`（17 Counter） | 1:1 | |
| `api/admincenter.go` 入口 | `AdminApiApplication.java` | 同 + `@SpringBootApplication` + `@ConfigurationPropertiesScan` | 1:1 | |

### 2.3 API 层 HTTP / DI 差异

| 维度 | B Sea-Java | C RAG | 备注 |
|---|---|---|---|
| 路由注册 | go-zero `rest.WithJwt(serverCtx)` + `server.AddRoute(...)` | `@RequestMapping("/admin/v1")` + `@PostMapping("/{op}")` + `WebMvcConfig.addInterceptors(...)` | go-zero 代码注册 vs Spring 注解 |
| Handler / Controller | 1 个 `UserHandler.java` 装 9 case + Logic 转发 | `UserController.java` 直接调 Service（无 Logic 中间层） | RAG 删 Logic 中间层 |
| Logic 中间层 | 1 个 Handler → N 个 Logic → Repository | N 个 Service 直接被 N 个 Controller 调 | 简化 |
| 双返回 | `LogicResult.ok(data)` / `LogicResult.fail(code, msg)` | `throw new BusinessException(code, msg)` + `return resp`（成功）+ `advice` 统一 | 异常流代替显式 result |
| DTO 风格 | ApiTypes 单文件 500+ 行 | 按 case 拆 20+ 文件 | 按职责拆 |
| JWT 校验 | 2 个 Filter（JwtAuth + CheckBlacklist）chain | 1 个 Interceptor 合并 | Filter 是 Servlet 层；Interceptor 是 Spring MVC Handler 层，能访问 `HandlerMethod` |
| 配置 | `Config.java` static field 读 yml | `@ConfigurationProperties(prefix="admin-api")` 类 + nested `@NestedConfigurationProperty` | 类型安全 + IDE 提示 |
| ServiceContext | `public class ServiceContext { public static UserRpc userRpc; ... }` + `init()` 钩子 | 不再有 ServiceContext；N 个 `@Component`/`@Bean` 由 Spring 注入 | 删 ServiceContext |

### 2.4 API 层决策锁

**删 ServiceContext（static → Spring Bean）4 处同时改**：
1. 删 `ServiceContext.java`（整文件）
2. 每个 static 字段对应一个新 `@Component` 类（`@ConfigurationProperties` 类 / `@Bean` 方法 / `@GrpcClient` stub）
3. 调用点 `ServiceContext.userRpc.login(...)` → `@Autowired UserRpcClient client; client.userRpc().login(...)`
4. main 入口从 `ServiceContext.init()` 改成 `@SpringBootApplication` 注解，自动扫描装配

**为什么换**：static 全局变量在测试和多进程时难处理；Spring Bean 单例 + 依赖注入更标准。

**删 Logic 中间层 3 处同时改**：
1. 删 `internal/logic/<Case>Logic.java`（每个 case 一个文件）
2. Controller 直接 `@Autowired <Case>Service` 调用
3. LogicResult 改用 `BusinessException` + `advice/GlobalResponseAdvice/ExceptionHandler` 统一处理

**为什么换**：Logic 中间层只做"调 gRPC + 错误码映射"，可合并到 Service；throw 异常流比 LogicResult 双返回更 Java idiom。

**ApiTypes 单文件 → 每 case 一文件 N 处同时改**：
1. 删 `internal/types/ApiTypes.java`
2. 每个 type struct 一个新文件 `dto/<Case>Req.java` / `dto/<Case>Resp.java`，嵌套 type 单独建文件（`dto/AdminInfo.java` / `dto/UserInfo.java`）
3. Snake-case JSON 注解从 `// +infra:` 注释改成 `@JsonProperty("xxx_yyy")`
4. uid 序列化为字符串：`getUid()` 返回 `Long.toString()` 而不是 `long` 值

**为什么换**：单文件 500+ 行不可读；按 case 拆利于定位 + 编译按文件粒度增量。

**Handler → Controller 2 处同时改**：
1. `internal/handler/UserHandler.java`（一个文件 9 个 case）→ `controller/UserController.java`（每个 `@PostMapping` 一个 case）
2. 路由：`go-zero server.AddRoute(...)` → `@RequestMapping("/user/v1") @PostMapping("/{op}")`

**为什么换**：go-zero 代码注册路由 vs Spring 注解声明式路由；注解式更直观且与 Swagger 自动集成。

**Filter → Interceptor（2 合一）3 处同时改**：
1. 删 `internal/filter/JwtAuthenticationFilter.java` + `CheckBlacklistFilter.java`
2. 新建 `interceptor/AdminJwtAuthInterceptor.java`（合并验签 + blacklist）
3. `WebMvcConfig.java` 调 `addInterceptors(...)` 注册到 `/admin/v1/**`

**为什么换**：Filter 是 Servlet 层；Interceptor 是 Spring MVC Handler 层，能访问 `HandlerMethod`。2 个合并后单元测试和异常处理都简单。

**配置（static → @ConfigurationProperties）3 处同时改**：
1. `Config.java` 删
2. 新建 `config/<X>Properties.java` 加 `@ConfigurationProperties(prefix="xxx")` + nested `@NestedConfigurationProperty` 类
3. main 入口加 `@ConfigurationPropertiesScan`

**为什么换**：类型安全的配置类 + IDE 自动补全；相比 static field 不会丢失 key。

---

## 3. 公共模块对照

| A Go (utils / errmsg / jwt) | B Sea-Java | C RAG | 备注 |
|---|---|---|---|
| `common/errmsg/` 包 | `service/common/src/main/java/.../errmsg/ErrorCode.java` | `service/users/users-common/src/main/java/.../errmsg/ErrorCode.java`（含 instance `getMsg()`） | C 把 ErrorCode 提到 `users-common`（admin-api 也要用） |
| `common/xcode/responses.go` | `service/common/.../response/Response.java` | 同 | 包成功 / 失败 envelope |
| `common/jwt/...` | （无 B 同款） | `users-common/.../jwt/JwtUtil.java` | C 拆出 |
| - | `service/common/.../exception/BusinessException.java` | 同（公共） | Service 层显式抛 BusinessException |

### 3.1 公共模块决策锁

**ErrorCode 提模块 2 处同时改**：
1. 从 `service/common/.../errmsg/ErrorCode.java` 移到 `service/users/users-common/.../errmsg/ErrorCode.java`
2. 加 instance method `getMsg()` 让 `ErrorCode.X.getCode()` + `ErrorCode.X.getMsg()` 无参可用

**为什么换**：admin-api 也要用 ErrorCode；放 common 模块会反向依赖 users-common（循环依赖）。instance `getMsg()` 避免每次传 code 调静态方法。

---

## 4. 跨模块全局约定（Sea-RideTheWind_java 与 RAG 共享）

| 项 | 值 | 来源 |
|---|---|---|
| proto 字段号 | 用户锁定与 Go 端同款 | `service/users/*/proto/src/main/proto/*.proto` |
| 错误码 | 与 Go 端 `errmsg.ErrorCode` 同款（5001 / 5002 / 1001-1018 / 3001-3003） | `users-common/.../errmsg/ErrorCode.java` |
| JWT claims | `userId` (Long) | `users-common/.../jwt/JwtUtil.java` |
| 表名 | user 表 `users_user`、admin 表 `admin_admin`、admin 邀请 `admin_invite` | `@TableName("xxx")` |
| HTTP path | `/admin/v1/{op}` POST 风格 | `@RequestMapping` + `@PostMapping` |
| Kafka topic | 待补 | - |
| gRPC 接口 | 与 Go 端 13 RPC 同名同参 | `service/users/admin/admin-proto/src/main/proto/admin.proto` |

---

## 5. Phase 1-4 完成度速查

| Phase | 模块 | 增量文件 | 测试 | jar |
|---|---|---|---|---|
| 1 | user-rpc | 13 Java + 2 SQL | 21 用例 | 81MB |
| 2 | user-api | 17 Java + 20 DTO + 3 测试 | 28 用例 | - |
| 3 | admin-rpc | 11 Java + 3 SQL | 18 用例 | - |
| 4 | admin-api | 33 Java + 28 DTO + 3 测试 | 36 用例 | 78MB |

---

## 6. 一句话总结

`Sea-RideTheWind_java` = Go 镜像 + JPA + static ServiceContext + Logic 中间层 + ApiTypes 单文件 + 2 个 Filter；
`RAG` = 同名 Java 包 + MyBatis-Plus + Flyway + 全 Spring Bean + Controller 直接调 Service + 每 case 一 DTO + 1 个 Interceptor。
proto 字段号 / 错误码 / JWT claims / 表名 / HTTP path 全部 1:1 复刻 Go，不偏离。