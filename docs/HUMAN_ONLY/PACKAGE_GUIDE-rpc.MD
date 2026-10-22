下面 7 段按你给的 4 个维度逐个说明，每条都基于实际源码。最后一段给出"我准备写的类该放哪"的速查表 + user-rpc 语境下最容易混的边界。

> 重要前提：user-rpc 是 gRPC 服务 + DB 持有方，**没有** `advice` / `interceptor` / `client` / `dto` 这 4 个文件夹。你列的"4 对容易混淆的区别"在 user-rpc 语境下要重新映射——我会在最后单独说。

---

### 1. `bootstrap/`

**负责什么**：应用启动期的一次性装配。两件事——
- `NacosRegistration` 在 `ApplicationReadyEvent` 触发时，把自己（user-rpc 实例 + 端口 49210）注册到 Nacos；在 `@PreDestroy` 时反注册
- `SnowflakeBootstrap` 同样在 `ApplicationReadyEvent` 做 `workerId ∈ [0, 1023]` 范围校验 + `Snowflake.init()` 触发，越界直接抛 `IllegalStateException` 让进程 fail-fast

之所以独立于 `config/`：bootstrap 是**带副作用的运行期动作**（写 Nacos、调 Snowflake.init），而 config 只是**声明式绑定**。两件事都是"启动时干一次"，但语义不同。

**应该放什么**：
- `@Component` + `@EventListener(ApplicationReadyEvent.class)` 的启动期校验/注册类
- `@PreDestroy` 反注册类
- 任何"必须 fail-fast、不能等到第一个请求才报错"的初始化

**不应该放什么**：
- 单纯读 yaml 转对象（→ `config`）
- 业务用例（→ `service`）

**容易混的边界**：bootstrap vs config —— config 是"我应该是什么配置"（声明）；bootstrap 是"我用这个配置去做什么事"（命令）。

---

### 2. `config/`

**负责什么**：把 `application.yml` 里 `user-rpc.*` 段绑成 Java 对象。当前只有 `UserRpcProperties`（`@ConfigurationProperties(prefix = "user-rpc")`），聚合 `name` + `userAuth.accessSecret`（JWT 签名密钥）+ `userAuth.accessExpire`（token 有效期秒，LogoutLogic 写 Redis TTL 用这个，不能写死 24h）。

**应该放什么**：
- `@ConfigurationProperties` 类 —— 任何 `application.yml` 里 `user-rpc.*` 段都要有一个对应的 Properties 类
- 未来要加的 `@Configuration` 工厂类（如 `@Bean` 创建 Snowflake / Tracer）

**不应该放什么**：
- 启动期动作（→ `bootstrap`）
- 业务用例（→ `service`）
- MyBatis-Plus mapper 路径配置（项目用 `@MapperScan` 自动发现，不在这里）

---

### 3. `domain/`

**负责什么**：MyBatis-Plus 实体类，一类一张表。当前两个：
- `UserEntity` — `@TableName("users_user")` 映射 `users_user` 表，10 字段（uid / username / password / email / status / score / extra_info / create_time / update_time / id），`@TableId(type=IdType.ASSIGN_ID)` 走 Snowflake，`extra_info` 用 `JacksonTypeHandler` 做 JSONB ↔ `Map<String,String>` 互转
- `UserAvatarHistoryEntity` — `@TableName("users_user_avatar_history")` 同样套路

**应该放什么**：
- `@TableName` + `@TableId` + `@TableField` 注解的 POJO，每张表一个
- 只有字段 + getter/setter；不放业务方法、不放 SQL
- `LocalDateTime` / `Long` / `String` / `Map` 这些 MyBatis-Plus 能自动处理的类型

**不应该放什么**：
- 任何带 `@Select` / `@Insert` / `@Update` / `@Delete` 注解的方法（→ `mapper`）
- 业务编排（→ `service`）
- 业务异常（→ `exception`）

**容易混的边界**：domain vs mapper —— domain 是**数据形态**（"这张表长什么样"），mapper 是**数据访问**（"怎么读写这张表"）。一个 entity 对应一个 mapper；entity 自己不查 DB。

---

### 4. `exception/`

**负责什么**：业务异常的"工厂类"。`UserExceptions` 是一个**不能实例化**的容器（private 构造器），里面挂 5 个 `public static class extends RuntimeException`：
- `NotFoundException`（对应 Go `ErrorNotFound`，mapper 查不到 user 抛）
- `AvatarHistoryNotFoundException`（对应 Go `ErrorAvatarHistoryNotFound`）
- `PasswordMismatchException`（密码错）
- `UsernameExistsException`（注册时用户名已存在 → 映射 `ALREADY_EXISTS`）
- `TokenBlacklistedException`（logout 时 token 已在 blacklist → 映射 `PERMISSION_DENIED`）

**应该放什么**：
- 业务异常子类（`extends RuntimeException`）
- 工厂模式：把"同类异常"聚合到一个容器类里，private 构造器禁止 new，调用方只能 `throw new UserExceptions.NotFoundException(...)`
- 未来新增的 user 域业务异常

**不应该放什么**：
- 框架异常（Spring/MyBatis 自己的，不用自己定义）
- 业务逻辑（→ `service`）
- gRPC `Status` 异常转换（这是 service 里的事）

**容易混的边界**：exception vs service —— exception 是**异常类型定义**（"出了什么错"），service 是**异常使用点**（"什么时候抛、怎么捕获映射成 gRPC Status"）。mapper 也允许直接抛这里的异常（D-001），但翻译成 `io.grpc.StatusRuntimeException` 只在 service 里做。

---

### 5. `mapper/`

**负责什么**：MyBatis-Plus 数据访问层。两类方法：
1. **继承自 `BaseMapper<Entity>` 的 CRUD**（`selectById` / `insert` / `updateById` / `deleteById` / `selectList` 等）—— 免费拿
2. **自定义 SQL**：`@Select` / `@Insert` / `@Update` / `@Delete` 注解的方法，1:1 对齐原 Go 端 DataModel 的 JPQL
3. **`default` 方法封装**：把 `selectByXxxRaw` + `null check + throw NotFoundException` 包成 `findOneByXxx(...)`，原子事务 `createAvatarHistory` / `selectAvatarHistory` 也用 `default` 方法 + `@Transactional` 保证

当前两个 mapper：
- `UserMapper` — `users_user` 表的 CRUD + 4 步/5 步原子事务的入口
- `UserAvatarHistoryMapper` — `users_user_avatar_history` 表的 CRUD + 头像历史的 4 步/5 步原子事务

**应该放什么**：
- `@Mapper` 注解的接口（`org.apache.ibatis.annotations.Mapper`），`extends BaseMapper<Entity>`
- 单行 SQL 用 `@Select` / `@Insert` / `@Update` / `@Delete` 注解方法
- `default` 方法做 null 抛异常封装、动态 SQL（`LambdaUpdateWrapper` / `QueryWrapper`）
- `@Transactional` 写在 `default` 方法上（Spring 走 JDK 动态代理，default 同样被拦截）

**不应该放什么**：
- 业务规则（→ `service`，比如 login 三态判定、password 校验）
- 实体类（→ `domain`）
- 任何带 `@Service` 注解的类（mapper 是接口不是类）
- 跨 mapper 编排（业务用例放在 service，不是放在另一个 mapper 里；`UserAvatarHistoryMapper.createAvatarHistory` 注入 `UserMapper` 是特例，因为 Go 端就是在 `UserAvatarHistoryModel` 里跨表操作）

**容易混的边界**：mapper vs service —— mapper 是**单表 / 固定 SQL 的封装**（"增删改查 + 原子事务"），service 是**跨 mapper 的业务编排**（"login 三态 / register 5 步 / deadline 检查"）。一个 service 调多个 mapper，一个 mapper 不调 service。

---

### 6. `metrics/`

**负责什么**：gRPC 层业务指标。`UserRpcMetrics`（`@Component` + Micrometer Counter），8 个 Counter（4 个 register 分支 + 4 个 login 分支），指标名 `user_rpc_register_total{result=...}` / `user_rpc_login_total{result=...}` 1:1 对齐 Go 端。`UserDomainService.login` / `register` 在 success/error 路径上自增。

**应该放什么**：
- `@Component` 类，构造器注入 `MeterRegistry`，每个 Counter / Gauge / Timer 在构造时一次性注册好
- 命名严格按 Go 端 `user_rpc_*_total`，tag 也是 `result`（注意是 `result`，**不是** `path`——user-rpc 只有 service 名维度）

**不应该放什么**：
- actuator 暴露端点配置（→ `application.yml`）
- 业务用例（→ `service`）

**与 user-api 的 metrics 对比**：结构一样（都是 `@Component` + Counter 容器），但**指标名 + tag 维度不同**——user-api 是 `user_api_intercept_total{path, result}`，user-rpc 是 `user_rpc_register_total{result}` / `user_rpc_login_total{result}`。原因：user-api 在 HTTP 入口按 path 区分；user-rpc 没有 path 维度，按 method+result 区分。

---

### 7. `service/`

**负责什么**：业务用例层。注意这个目录里其实有两类东西要区分：

**`service/` 直接住的**（领域服务，`@Service`）：
- `UserDomainService` —— **9 合 1** 的 DomainService，对应 Go 端 9 个 `*Logic.go`，本类直接注入 `UserMapper` / `UserAvatarHistoryMapper` / `UserRpcMetrics` / `StringRedisTemplate` / `Tracer` / `UserRpcProperties` / `AvatarDomainService` 等所有依赖。9 个方法：login / register / getUser / updateUser / deleteUser / selectAvatar / uploadAvatar / getAvatarHistory / logout
- `AvatarDomainService` —— 头像域的 helper：proto 转换（`fromModel`）+ 3 个 normalize（avatarUrl / contentType / historyId），3 个头像用例共用

**`service/impl/` 子目录**（gRPC 适配，`@GrpcService`）：
- `UserServiceGrpcImpl` —— `extends UserServiceGrpc.UserServiceImplBase`（protoc 生成的基类），9 个 RPC 方法都是 **thin wrapper**：构造 proto → 调 `UserDomainService.xxx` → `responseObserver.onNext + onCompleted`（或 `onError` 透传 RuntimeException）

**应该放什么**：
- `service/` 里放**领域服务**：`@Service` + 业务用例方法（一个域一个 DomainService，9 个用例合 1）+ 跨用例的 `@Service` helper
- `service/impl/` 里放**外部接口适配**：`@GrpcService` 实现 protoc 生成的基类，**只做转发**（不写业务规则）
- 异常翻译成 `io.grpc.StatusRuntimeException` 也在 `service/`（如 `throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException()`）

**不应该放什么**：
- 实体类（→ `domain`）
- 数据访问（→ `mapper`）
- 业务异常类型定义（→ `exception`）
- gRPC 接口生成代码（来自 `com.rag.users.user.proto.*`，由 protoc 生成，不属于 `service`）
- 启动期动作（→ `bootstrap`）

**容易混的边界 1 — service vs service/impl**：
- `service/UserDomainService` 是**业务大脑**——9 个用例的实际逻辑、metrics 埋点、deadline 检查、异常翻译都在这里
- `service/impl/UserServiceGrpcImpl` 是**协议适配层**——只做 proto 转换 + 转发；硬约束是 grpc-java 一个 service 一个实现类（这是 grpc-java 的硬性规定）
- 协作：`GrpcServiceImpl` 注入 `DomainService`，把 gRPC 调用路由到对应的 DomainService 方法

**容易混的边界 2 — service vs mapper**：
- service 是**业务规则 + 编排**（login 三态、register 5 步、deadline、metrics、trace span、异常翻译）
- mapper 是**数据访问**（单表 SQL、原子事务）
- service 调 mapper；mapper 不调 service

---

### 你提到的 4 对区别，在 user-rpc 语境下的映射

| 你问的对比 | user-rpc 的答案 |
|---|---|
| **advice vs interceptor** | user-rpc 都没有。`GlobalExceptionHandler` / `JwtAuthInterceptor` 的功能**分散到 service 层**：`UserDomainService.login` 自己 try/catch 并翻译成 `io.grpc.StatusRuntimeException`；鉴权在 user-api 的 interceptor 跑，user-rpc 是被调方，**不再做鉴权**。 |
| **config vs interceptor** | config 在（`UserRpcProperties`），interceptor 没有。config 在 user-rpc 是**配置绑定**的单一职责，user-api 还会用 `WebMvcConfig` 注册 interceptor。 |
| **client vs service** | client 没有，service 是**入站**（被 user-api 调）。user-api 的 client 是出站（调下游），user-rpc 的 service 是入站（被调）；两者互为镜像但方向相反。 |
| **dto vs 其他业务代码** | dto 没有。user-rpc 在边界上**只有 proto**（`com.rag.users.user.proto.*`）；内部流转用 entity（`UserEntity`）和 mapper；不引入独立 DTO 层。 |

**结论**：user-rpc 的"横切"（鉴权、异常翻译、响应包装）全在 service 自己完成；user-api 的横切被拆成 advice/interceptor 4 个文件夹。user-rpc 的"数据"层有 3 个文件夹（domain / mapper / exception），user-api 一个都没有。

---

### 速查表：我准备写的类应该放哪？

| 我要写的类长这样 | 放哪 | 关键注解 / 形态 |
|---|---|---|
| MyBatis-Plus 实体（一张表一个） | `domain/` | `@TableName` + `@TableId` + `@TableField` 注解的 POJO |
| 一张表的数据访问接口 | `mapper/` | `@Mapper` 接口 + `extends BaseMapper<Entity>` |
| 业务用例方法（一个域合一个 DomainService） | `service/` | `@Service`（如 `UserDomainService`） |
| 跨用例 helper（proto 转换 / 字段归一化） | `service/` | `@Service`（如 `AvatarDomainService`） |
| gRPC 接口实现（protoc 生成基类的子类） | `service/impl/` | `@GrpcService` + `extends XxxServiceGrpc.XxxImplBase` |
| 业务异常类型 | `exception/` | `public static class extends RuntimeException` 挂在容器类下 |
| 启动期动作（Nacos 注册 / Snowflake 校验） | `bootstrap/` | `@Component` + `@EventListener(ApplicationReadyEvent)` |
| 绑定 yaml 配置 | `config/` | `@ConfigurationProperties(prefix="user-rpc")` |
| 注册 Micrometer 指标 | `metrics/` | `@Component` + `MeterRegistry` |

判断口诀：**横切看层次（proto 边界→service/impl，业务→service，数据→mapper，形态→domain，启动→bootstrap，配置→config，异常→exception，指标→metrics）**。

如果还想看 `service/impl/UserServiceGrpcImpl` 怎么把 9 个 RPC 全部 thin wrapper 化的代码模式，或 `UserDomainService` 9 个用例方法的内部编排套路，告诉我。