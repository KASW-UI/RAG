# user-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/user/user/rpc`
> 目标根包：`com.rag.users.user.rpc`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

`user-rpc` 在 RAG 中**已有完整 Java 实现**（非空骨架），实际包结构如下：

| 包 | 实际文件 | 职责 |
|---|---|---|
| （根） | `UserRpcApplication` | `@SpringBootApplication` 入口 |
| `bootstrap/` | `NacosRegistration`、`SnowflakeBootstrap` | 启动期注册与 ID 生成器初始化 |
| `config/` | `UserRpcProperties`、`MybatisPlusOrderingBridgeAutoConfiguration` | `@ConfigurationProperties` 绑定；MyBatis-Plus 自动配置桥接 |
| `domain/` | `UserEntity`、`UserAvatarHistoryEntity` | `@TableName` 数据库实体 |
| `exception/` | `UserExceptions` | 业务异常聚合类（内部多个 static 嵌套类） |
| `mapper/` | `UserMapper`、`UserAvatarHistoryMapper`、`JsonbTypeHandler` | MyBatis-Plus `BaseMapper` 子接口 + jsonb TypeHandler |
| `metrics/` | `UserRpcMetrics` | `@Component`，注入 `MeterRegistry` |
| `service/` | `UserDomainService`、`AvatarDomainService` | 领域服务（`@Service`） |
| `service/impl/` | `UserServiceGrpcImpl` | `@GrpcService extends UserServiceGrpc.UserServiceImplBase` |

### 已有组件（跨模块可复用）

| 组件 | 位置 | 承担 |
|---|---|---|
| `com.rag.common.response.Response` | `service/common` | HTTP/gRPC 统一响应信封（`code`/`msg`/`data`） |
| `com.rag.common.exception.BusinessException` | `service/common` | 业务异常基类（`int code` + `String msg`） |
| `com.rag.common.snowflake.Snowflake` + `SnowflakeBean` | `service/common` | 64 位雪花 ID |
| `com.rag.common.observability.*` | `service/common` | `HttpMiddleware` / `MqConsumer` / `RpcInterceptor` / `Observability` |
| `com.rag.common.spring.RpcServerInterceptorFactory` | `service/common` | 把 gRPC `ServerInterceptor` 暴露为 Bean，**但 user-rpc 当前并未导入它** —— 见下方警告 |
| `com.rag.users.common.jwt.JwtUtil` | `users-common` | JWT 签发/验签 + `padToHmacKeySize` |
| `com.rag.users.common.errmsg.ErrorCode` | `users-common` | 业务错误码枚举 |

### 已确认的框架机制

- `net.devh:grpc-server-spring-boot-starter`：`@GrpcService` 类自动注册为 gRPC server handler；`@GrpcClient("name")` 自动注入 BlockingStub
- `grpc-spring-boot-starter` 自动收集容器中所有 `ServerInterceptor` Bean —— **前提是该 Bean 真的在容器里**。`RpcServerInterceptorFactory` 是 `com.rag.common.spring` 下的 `@Component`，而 `UserRpcApplication.java:39` 只 `@Import({SnowflakeBean, SnowflakeProperties, LoggerFactoryBean})` 三个 bean、且 `scanBasePackages` 刻意不含 `com.rag.common`（注释说明是为了避免把 servlet 类拖进纯 gRPC 进程）→ **`RpcServerInterceptorFactory` 在 user-rpc 中并未注册**。
  > ⚠️ **2026-10-20 更正**：本文件早前版本写「已全局注册」，经 `FileTree/common.md` 交叉核对**证伪**。实测 user-rpc 当前**没有 gRPC span、没有 deadline 传播、也没有 gRPC 超时**（`RpcServerInterceptorFactory` 还把 timeout 写死 `Duration.ZERO`）。修法见 `FileTree/common.md` §12.7 #1。
- MyBatis-Plus `@Mapper` + `BaseMapper<Entity>` + `@TableName`；`LambdaQueryWrapper` / `LambdaUpdateWrapper`
- Spring `@ConfigurationProperties` 配置绑定
- `spring-boot-starter-actuator` / Micrometer `MeterRegistry`
- Spring Cloud Alibaba Nacos Discovery

### 当前 Component 可以直接复用的能力

- **ID 生成**：复用 `SnowflakeBean`，不新建 id 生成器
- **指标底座**：复用 `Observability`（OTEL span 属性标记）+ `MeterRegistry`；`UserRpcMetrics` 只承载 users 特有指标
- **gRPC 可观测性拦截器**：`RpcServerInterceptorFactory` **当前未注册**（`@Import` 未含它、`scanBasePackages` 不含 `com.rag.common`）。落地修法：`@Import` 追加该 bean，或按 `common.md` 的建议先拆 `common/spring/grpc/` 再精确导入。**不是**"不需自建"，而是**需要补一次装配**
- **异常体系**：复用 `BusinessException` + `UserExceptions`
- **JWT**：`JwtUtil` 在 `users-common`，user-rpc 侧仅在登录时签发

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| Flyway 迁移归属 | `UNKNOWN` —— user-rpc 与 admin-rpc 抢同一 schema 的 `flyway_schema_history` 版本号（AGENTS.md §6.3 待拍板） |
| user-rpc 是否应允许直写 admin 表 | 按 Go 边界：`user/identity/resolve.go` 走 gRPC，故 user-rpc 不碰 admin 表；RAG 已符合 |

---

## 12.2 第二部分：Go 原始文件树

```
service/user/user/rpc/
├── user.go                                    # main 入口：flag、config、svc、server.Run
├── userservice/userservice.go                 # go-zero 生成的 gRPC service 接口
├── internal/
│   ├── config/config.go                       # Config 结构体：MySQL / Redis / Auth / Log
│   ├── svc/service_context.go                 # ServiceContext：持有 model / cache / identity client
│   ├── server/userserviceserver.go            # gRPC server 装配：UserServiceServer{}
│   ├── logic/
│   │   ├── loginlogic.go                      # Login
│   │   ├── registerlogic.go                   # Register（事务：查重 + 插 user）
│   │   ├── logoutlogic.go                     # Logout（JWT blacklist）
│   │   ├── getuserlogic.go                    # GetUser
│   │   ├── updateuserlogic.go                 # UpdateUser（jsonb extra_info）
│   │   ├── deleteuserlogic.go                 # DeleteUser
│   │   ├── selectavatarlogic.go               # SelectAvatar（多步：history + user）
│   │   ├── uploadavatarlogic.go               # UploadAvatar
│   │   ├── getavatarhistorylogic.go           # GetAvatarHistory
│   │   └── avatarhelpers.go                   # avatar URL / contentType / historyId 归一化
│   ├── model/
│   │   ├── data_model.go                      # 事务闭包：Transaction() + ModelContext(tx)
│   │   ├── db.go                              # GORM AutoMigrate 建表（无状态）
│   │   ├── user.go                            # User / UserAvatarHistory GORM 模型 + 查询方法
│   │   └── vars.go                            # 枚举常量（status / role 等）
│   └── metrics/metrics.go                     # users 专属指标
└── pb/                                        # 生成物（不改）
```

---

## 12.3 第三部分：Go 文件职责分析

### `user/rpc/user.go`
- **主要职责**：进程入口。解析 flag、加载 config、构造 `ServiceContext`、启动 gRPC server。
- **关键调用关系**：`config.Load()` → `svc.NewServiceContext(cfg)` → `userservice.NewUserServer(...)` → `server.Run()`
- **业务语义**：无（纯装配）。
- **实现机制**：go-zero flag + `server.Run()`（含优雅退出）。
- **需保留**：启动顺序（config → context → server）。
- **可替换**：**整个文件由 Spring Boot 装配吸收** —— `@SpringBootApplication` + `@ConfigurationProperties` + `@GrpcService` 自动注册。

### `internal/config/config.go`
- **主要职责**：配置结构体 —— MySQL DSN、Redis、Auth（SecretKey/ExpiresIn）、Log、Name。
- **业务语义**：无。**实现机制**：yaml 绑定。
- **可替换**：Spring `@ConfigurationProperties(prefix="user")`。**注意语义点**：`Auth.SecretKey` 是原始字符串，需保留"短密钥补齐到 32 字节"这条 L3 语义——Go 的 `golang-jwt` 不校验长度，Java `jjwt` 强制 ≥32，故 Java 侧必须显式 `JwtUtil.padToHmacKeySize`（AGENTS.md §6.1 #7）。

### `internal/svc/service_context.go`
- **主要职责**：依赖持有 —— `Config`、`Model`（含 `UserModel`/`UserAvatarHistoryModel`）、`Identity`（走 gRPC 的 admin/user 身份解析客户端）、cache。
- **业务语义**：`Identity` 是**跨服务边界**，不能内联到本 model。
- **可替换**：**ABSORB**。Spring DI 覆盖依赖持有与生命周期。但"需要一个 identity gRPC 客户端"这件事是业务语义，需保留承载位置。

### `internal/server/userserviceserver.go`
- **主要职责**：gRPC server 结构体与方法分发（把 9 个 RPC 转发到对应 logic）。
- **业务语义**：无（纯分发）。
- **可替换**：`@GrpcService` 类继承生成的 `UserServiceImplBase`，方法分发由 starter 完成 → 但**方法入口本身需要显式写**（要注入 domain service、做参数转换、记录指标）。

### `internal/logic/*.go`（9 个 + `avatarhelpers.go`）
- **主要职责**：每个文件一个 RPC 的业务规则。共同模式：`校验输入 → 调 model → 组装 resp → 记指标`。
- **逐个语义**：
  - `loginlogic.go`：按 username 查 → 比对密码 → 签发 JWT；失败分支区分"用户不存在/密码错/已封禁"
  - `registerlogic.go`：查重 → 生成 snowflake uid → 插 user；**事务边界**
  - `logoutlogic.go`：把 token 写入 Redis blacklist（带 TTL）
  - `getuserlogic.go`：按 uid 查；**未找到**与**调用方无权限**是不同错误码
  - `updateuserlogic.go`：部分字段更新；`extra_info` 为 jsonb，**空字符串不能清空该列**
  - `deleteuserlogic.go**：软删/硬删 + 关联处理
  - `selectavatarlogic.go`：**多步原子操作** —— 更新 user.avatar_url + 更新/插 history 记录 + 置旧 history 的 `is_current=false`
  - `uploadavatarlogic.go`：生成 URL + 插 history
  - `getavatarhistorylogic.go`：按 uid 分页查 history
  - `avatarhelpers.go`：`TrimSpace` 归一化 URL；空 contentType → 默认 `"image/png"`；historyId `TrimSpace`
- **可替换的机制**：文件划分可合并（Go 一文件一 RPC ≠ Java 必须一文件一 RPC）；但**业务分支与错误码不可省略**。

### `internal/model/data_model.go`
- **主要职责**：事务闭包。`Transaction()` 开事务 → 构造带 `tx` 的 `ModelContext` → 调闭包 → commit/rollback。
- **L3 语义**：**这是整个 user-rpc 唯一的事务边界载体**。`selectAvatar` 的多步写必须在此闭包内。
- **可替换**：Spring `@Transactional`。**陷阱（AGENTS.md §3.3）**：self-invocation 不经代理 → 注解静默失效。多步原子方法必须抽到**独立 `@Service` Bean**，由 GrpcImpl 或另一个 service 跨 Bean 调用。

### `internal/model/db.go`
- **主要职责**：`AutoMigrate(&User{}, &UserAvatarHistory{})` 建表。
- **可替换**：Flyway `V*__create_*.sql`。**这是有状态账本 vs Go 无状态**的差异点 → 见 §12.7 待确认项。

### `internal/model/user.go`
- **主要职责**：GORM 模型定义（struct tag 决定列名/类型/索引）+ 查询方法（按 uid、按 username、分页）。
- **L1/L2 语义**：列名、可空性、唯一约束、索引必须与 Go 一致。
- **可替换**：`@TableName` 实体 + Mapper `BaseMapper` + `@Select`/`LambdaQueryWrapper`。**陷阱**：`LambdaUpdateWrapper.set(Map)` 会丢掉 typeHandler（jsonb 被绑成 varchar）→ 必须 `setSql("CAST(... AS jsonb)")` 或走实体更新（AGENTS.md §6.1 #4）。

### `internal/model/vars.go`
- **主要职责**：status / role 等枚举常量。
- **可替换**：常量类或 `users-common` 的枚举。字段值属 **L1**，不可改。

### `internal/metrics/metrics.go`
- **主要职责**：users RPC 专属指标（登录成功/失败、注册、头像操作等）的记录函数。
- **可替换**：`@Component UserRpcMetrics` + `MeterRegistry`。**触发条件与统计含义属 L2，不可删**。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `user.go` 进程装配 | `UserRpcApplication` + Spring 容器 | ABSORB | `@SpringBootApplication` 覆盖 flag/config/server 组装；starter 自动注册 `@GrpcService` |
| `config.go` 配置绑定 | `config/UserRpcProperties` | ADAPT | Spring `@ConfigurationProperties` 替代 yaml struct；**字段语义 1:1** |
| `config.go` Auth.SecretKey 短密钥 | `users-common/jwt/JwtUtil.padToHmacKeySize` | REUSE | Go 无长度校验、jjwt 强制 ≥32，Java 必须显式补齐；两模块共用一份 |
| `service_context.go` 依赖持有 | Spring DI（构造器注入） | ABSORB | 无额外业务职责，纯 wiring |
| `service_context.go` identity gRPC 客户端 | `service/impl/UserServiceGrpcImpl` 的构造依赖 | ADAPT | Go 的 `Identity` 客户端在 user-rpc 内**未被 user-rpc 自身 RPC 使用**（identity 解析发生在 api 侧），故无需独立 Bean |
| `userserviceserver.go` 9 个方法分发 | `service/impl/UserServiceGrpcImpl` 9 个 override | ADAPT | 分发由 starter 完成，但方法体（参数转换 + 指标 + 调 domain）需显式写 |
| `loginlogic.go` 登录规则 | `service/UserDomainService.login` | MERGE | 9 个 logic 合并进单一 `UserDomainService`（RAG 既有决策，与 HUMAN_ONLY.MD 一致） |
| `registerlogic.go` 注册事务 | `service/UserDomainService.register` + `@Transactional` | ADAPT | 事务边界保留，注解替代闭包 |
| `logoutlogic.go` blacklist | `service/UserDomainService.logout` | MERGE | 同上 |
| `getuserlogic.go` | `UserDomainService.getUser` | MERGE | 同上 |
| `updateuserlogic.go` jsonb 写入 | `mapper/UserMapper` + `setSql("CAST(... AS jsonb)")` | ADAPT | MyBatis-Plus wrapper 丢 typeHandler，需显式 CAST |
| `deleteuserlogic.go` | `UserDomainService.deleteUser` | MERGE | 同上 |
| `selectavatarlogic.go` 多步原子写 | `UserDomainService.selectAvatar` + **独立事务 Bean** | SPLIT | self-invocation 风险：多步原子方法必须抽独立 `@Service` 供跨 Bean 调用（AGENTS.md §3.3） |
| `selectavatarlogic.go` `is_current` 回写 | `UserDomainService.selectAvatar` | ADAPT | 写库后必须回写内存实体，否则响应 `is_current` 陈旧（AGENTS.md §6.1 #5） |
| `uploadavatarlogic.go` | `UserDomainService.uploadAvatar` | MERGE | 同上 |
| `getavatarhistorylogic.go` | `UserDomainService.getAvatarHistory` | MERGE | 同上 |
| `avatarhelpers.go` 三个归一化 | `service/AvatarDomainService`（rpc 侧权威） | SPLIT | 与 api 侧 `UserApiSupport` 的同名方法**同名同义但分层不同**：rpc 侧是权威实现，api 侧是镜像；归一化属业务语义需独立承载类 |
| `data_model.go` 事务闭包 | Spring `@Transactional` | ADAPT | 框架机制；**代价**：失去显式 tx 传播，需独立 Bean |
| `db.go` AutoMigrate | Flyway `V*__create_*.sql` | ADAPT | 有状态账本 vs 无状态迁移；归属待拍板 |
| `model/user.go` User 结构 | `domain/UserEntity` | ADAPT | `@TableName("users_user")`；列/约束 1:1 |
| `model/user.go` UserAvatarHistory | `domain/UserAvatarHistoryEntity` | ADAPT | 同上 |
| `model/user.go` 查询方法 | `mapper/UserMapper`、`UserAvatarHistoryMapper` | SPLIT | 按实体拆 Mapper（HUMAN_ONLY.MD 决策：user-rpc 2 个 Mapper） |
| `model/user.go` jsonb 列 | `mapper/JsonbTypeHandler` | NEW | `@TableField(typeHandler=…)`；仅对 MP 生成的 SQL 生效，wrapper 需另行 CAST |
| `model/vars.go` 枚举 | `users-common/errmsg/ErrorCode` + 领域常量 | REUSE | 字段值属 L1，不重新定义 |
| `model/*` 异常 | `exception/UserExceptions` + `common/BusinessException` | MERGE | 不为每个错误分支建类；按业务含义聚合 |
| `metrics/metrics.go` | `metrics/UserRpcMetrics` | ADAPT | `@Component` + `MeterRegistry`；触发条件保留 |
| RPC 可观测性 span | `common/observability/RpcInterceptor`（经 `RpcServerInterceptorFactory` 暴露） | **UNKNOWN** | ⚠️ **当前未注册** —— 见 §12.1 更正。`user-rpc` 需在 `@Import` 中补该 bean 才谈得上 REUSE；在此之前该职责**没有承载位置**，属 §12.7 不通过项 |
| 服务注册 | `bootstrap/NacosRegistration` | ADAPT | `@EventListener(ApplicationReadyEvent)` + `@PreDestroy` |
| snowflake 初始化 | `bootstrap/SnowflakeBootstrap` | ADAPT | 复用 `SnowflakeBean` |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/users/user/rpc/
├── UserRpcApplication.java
├── bootstrap/
│   ├── NacosRegistration.java
│   └── SnowflakeBootstrap.java
├── config/
│   ├── UserRpcProperties.java
│   └── MybatisPlusOrderingBridgeAutoConfiguration.java
├── domain/
│   ├── UserEntity.java
│   └── UserAvatarHistoryEntity.java
├── exception/
│   └── UserExceptions.java
├── mapper/
│   ├── UserMapper.java
│   ├── UserAvatarHistoryMapper.java
│   └── JsonbTypeHandler.java
├── metrics/
│   └── UserRpcMetrics.java
└── service/
    ├── UserDomainService.java
    ├── AvatarDomainService.java
    └── impl/
        └── UserServiceGrpcImpl.java
```

### 逐文件说明

**`UserRpcApplication.java`**
- 所属包：`com.rag.users.user.rpc`
- 主要职责：应用入口，`@SpringBootApplication` + `@MapperScan`
- 承载 Go 职责：`user.go` 的进程装配
- 为什么独立：Spring Boot 入口约定（其余 3 个 rpc 模块同样式）
- 依赖：`config/UserRpcProperties`、`mapper/*`

**`bootstrap/NacosRegistration.java`**
- 所属包：`com.rag.users.user.rpc.bootstrap`
- 主要职责：`ApplicationReadyEvent` 时向 Nacos 注册；`@PreDestroy` 注销
- 承载 Go 职责：`user.go` 中 server 注册与优雅退出
- 为什么独立：生命周期职责与业务无关；RAG 三个 rpc 模块统一此式（一致性依据）
- 依赖：`NacosServiceManager`

**`bootstrap/SnowflakeBootstrap.java`**
- 所属包：`com.rag.users.user.rpc.bootstrap`
- 主要职责：启动期校验/初始化 snowflake ID 生成器
- 承载 Go 职责：`service_context.go` 里 snowflake 依赖的初始化
- 为什么独立：启动期 fail-fast 职责；ID 生成本身在 `common/SnowflakeBean`
- 依赖：`common/spring/SnowflakeBean`

**`config/UserRpcProperties.java`**
- 所属包：`com.rag.users.user.rpc.config`
- 主要职责：`@ConfigurationProperties(prefix="user")` 绑定 DSN / Redis / Auth / Log
- 承载 Go 职责：`config/config.go` 全部字段
- 为什么独立：配置绑定是独立关注点；避免散落到各 service
- 依赖：无（纯 POJO）

**`config/MybatisPlusOrderingBridgeAutoConfiguration.java`**
- 所属包：`com.rag.users.user.rpc.config`
- 主要职责：适配 MyBatis-Plus 与目标 Spring Boot 版本的 Bean 装配差异
- 承载 Go 职责：无直接 Go 对应（**L4 推导**，纯框架桥接）
- 为什么独立：框架版本适配不应污染业务包
- 依赖：MyBatis-Plus

**`domain/UserEntity.java`**
- 所属包：`com.rag.users.user.rpc.domain`
- 主要职责：`@TableName("users_user")` 实体；`@TableId(type=ASSIGN_ID)`；`@TableField(typeHandler=JsonbTypeHandler)` 标 jsonb 列
- 承载 Go 职责：`model/user.go` 的 `User` struct + struct tag
- 为什么独立：HUMAN_ONLY.MD 决策「domain/Entity」
- 依赖：`mapper/JsonbTypeHandler`

**`domain/UserAvatarHistoryEntity.java`**
- 所属包：`com.rag.users.user.rpc.domain`
- 主要职责：`@TableName("users_user_avatar_history")` 实体
- 承载 Go 职责：`model/user.go` 的 `UserAvatarHistory` struct
- 为什么独立：同上；且与 User 是不同持久化用途（历史 vs 当前态）
- 依赖：无

**`exception/UserExceptions.java`**
- 所属包：`com.rag.users.user.rpc.exception`
- 主要职责：按**业务含义**聚合的异常嵌套类集合（如 `UserNotFound`、`UserInactive`、`UserExists`、`PasswordMismatch`、`AvatarHistoryNotFound`）
- 承载 Go 职责：`model/vars.go` + 各 logic 的错误返回路径
- 为什么独立：RAG 既有约定（`MessageExceptions` / `AdminExceptions` 同式）
- 依赖：`common/exception/BusinessException`

**`mapper/UserMapper.java`**
- 所属包：`com.rag.users.user.rpc.mapper`
- 主要职责：`@Mapper interface UserMapper extends BaseMapper<UserEntity>`；自定义查询（按 uid、按 username、分页）与 jsonb 更新
- 承载 Go 职责：`model/user.go` 的全部查询方法 + `updateuserlogic.go` 的 jsonb 写
- 为什么独立：HUMAN_ONLY.MD 决策「按实体拆 Mapper」
- 依赖：`domain/UserEntity`、`mapper/JsonbTypeHandler`

**`mapper/UserAvatarHistoryMapper.java`**
- 所属包：`com.rag.users.user.rpc.mapper`
- 主要职责：`BaseMapper<UserAvatarHistoryEntity>` + 按 uid 分页 + `is_current` 批量置位
- 承载 Go 职责：`model/user.go` 中 history 相关查询 + `selectavatarlogic.go` 的 history 更新
- 为什么独立：HUMAN_ONLY.MD 明确 user-rpc 为 2 个 Mapper
- 依赖：`domain/UserAvatarHistoryEntity`

**`mapper/JsonbTypeHandler.java`**
- 所属包：`com.rag.users.user.rpc.mapper`
- 主要职责：jsonb 列的 TypeHandler（String ↔ PGobject/jsonb）
- 承载 Go 职责：`model/user.go` 中 `extra_info` 的 jsonb 映射
- 为什么独立：RAG 既有约定（admin-rpc 同名文件）
- 依赖：MyBatis

**`metrics/UserRpcMetrics.java`**
- 所属包：`com.rag.users.user.rpc.metrics`
- 主要职责：`@Component`，注入 `MeterRegistry`；`observeRpc(method, startedAt, err)` 等
- 承载 Go 职责：`metrics/metrics.go`
- 为什么独立：指标有独立的变化原因与调用方
- 依赖：Micrometer

**`service/UserDomainService.java`**
- 所属包：`com.rag.users.user.rpc.service`
- 主要职责：9 个用户业务用例（`login` / `register` / `logout` / `getUser` / `updateUser` / `deleteUser` / `selectAvatar` / `uploadAvatar` / `getAvatarHistory` / `logout`）
- 承载 Go 职责：`logic/*.go` 全部 9 个文件
- 为什么独立：HUMAN_ONLY.MD 决策「9 合 1」；合并依据是同一变化原因（用户域）、同一依赖边界（UserMapper + HistoryMapper）、同一调用方集合
- 依赖：`mapper/UserMapper`、`mapper/UserAvatarHistoryMapper`、`users-common/jwt/JwtUtil`、`metrics/UserRpcMetrics`、`StringRedisTemplate`

**`service/AvatarDomainService.java`**
- 所属包：`com.rag.users.user.rpc.service`
- 主要职责：头像归一化权威实现（`normalizeAvatarUrl` / `normalizeContentType` / `normalizeHistoryId`）+ `fromModel` 投影
- 承载 Go 职责：`logic/avatarhelpers.go` + `logic/getavatarhistorylogic.go` 的投影部分
- 为什么独立：与 `UserDomainService` **变化原因不同** —— 归一化规则同时被 api 侧镜像复用，是跨层共享的领域规则；且未来 upload（对象存储）等会独立演进
- 依赖：`domain/UserAvatarHistoryEntity`

**`service/impl/UserServiceGrpcImpl.java`**
- 所属包：`com.rag.users.user.rpc.service.impl`
- 主要职责：`@GrpcService extends UserServiceGrpc.UserServiceImplBase`；9 个 override 做 proto ↔ domain DTO 转换、调 `UserDomainService`、记指标
- 承载 Go 职责：`server/userserviceserver.go`
- 为什么独立：gRPC 接口实现是独立契约边界（AGENTS.md §4.1「严格」层）
- 依赖：`service/UserDomainService`、`metrics/UserRpcMetrics`

> **⚠️ 独立事务 Bean（推导建议，尚未落地）**：`selectAvatar` 的 3 步写（user.avatar_url / history insert / 旧 history 置 `is_current=false`）需要事务。若 `selectAvatar` 与其他方法同在 `UserDomainService` 内且被同 Bean 内部调用，`@Transactional` 因 self-invocation **静默失效**（AGENTS.md §3.3）。修法与 `admin-rpc/AdminCreateAtomicService` 同款：抽 `service/UserAvatarAtomicService.java` 独立 `@Service`。当前 RAG **未落地**，标 `UNKNOWN`。

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `user.go` | `UserRpcApplication` + Spring 容器 | ABSORB | 进程装配全部由框架承担 |
| `user.go`（服务注册） | `bootstrap/NacosRegistration` | ADAPT | 注册语义保留，API 换 Spring 事件 |
| `svc/service_context.go` | Spring DI | ABSORB | 纯依赖持有 |
| `svc/service_context.go`（snowflake） | `bootstrap/SnowflakeBootstrap` + `common/SnowflakeBean` | REUSE | ID 生成已有实现 |
| `config/config.go` | `config/UserRpcProperties` | ADAPT | 绑定机制换 Spring，字段 1:1 |
| `config/config.go`（Auth key 长度） | `users-common/JwtUtil.padToHmacKeySize` | REUSE | 补齐逻辑收口一处 |
| `server/userserviceserver.go` | `service/impl/UserServiceGrpcImpl` | ADAPT | 分发由 starter 做，方法体需写 |
| `logic/loginlogic.go` | `service/UserDomainService.login` | MERGE | 并入统一 domain service |
| `logic/registerlogic.go` | `UserDomainService.register` | MERGE | 同上 |
| `logic/logoutlogic.go` | `UserDomainService.logout` | MERGE | 同上 |
| `logic/getuserlogic.go` | `UserDomainService.getUser` | MERGE | 同上 |
| `logic/updateuserlogic.go` | `UserDomainService.updateUser` + `UserMapper` jsonb CAST | SPLIT | 业务归 service，jsonb 绑定细节归 mapper |
| `logic/deleteuserlogic.go` | `UserDomainService.deleteUser` | MERGE | 同上 |
| `logic/selectavatarlogic.go` | `UserDomainService.selectAvatar` | MERGE | 同上 |
| `logic/uploadavatarlogic.go` | `UserDomainService.uploadAvatar` | MERGE | 同上 |
| `logic/getavatarhistorylogic.go` | `UserDomainService.getAvatarHistory` + `AvatarDomainService.fromModel` | SPLIT | 用例与投影分层 |
| `logic/avatarhelpers.go` | `service/AvatarDomainService` | SPLIT | 归一化规则独立承载 |
| `model/data_model.go` | `@Transactional` | ADAPT | 框架替代闭包；需独立 Bean 规避 self-invocation |
| `model/db.go` | Flyway `V1__create_user.sql` | ADAPT | 迁移机制换 Flyway |
| `model/user.go`（User） | `domain/UserEntity` | ADAPT | JPA/ORM 换 MyBatis-Plus |
| `model/user.go`（History） | `domain/UserAvatarHistoryEntity` | ADAPT | 同上 |
| `model/user.go`（查询） | `mapper/UserMapper` + `UserAvatarHistoryMapper` | SPLIT | 按实体拆 |
| `model/user.go`（jsonb） | `mapper/JsonbTypeHandler` | NEW | Go struct tag 无对应 Java 载体 |
| `model/vars.go` | `users-common/errmsg/ErrorCode` | REUSE | 枚举值属 L1，不重定义 |
| `model/*`（错误路径） | `exception/UserExceptions` | MERGE | 按业务含义聚合，不逐错误建类 |
| `metrics/metrics.go` | `metrics/UserRpcMetrics` | ADAPT | Micrometer 替代 Go metrics |
| —（无 Go 对应） | `config/MybatisPlusOrderingBridgeAutoConfiguration` | NEW | 纯 L4 框架桥接 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 9 个 gRPC 方法（`login`/`register`/`logout`/`getUser`/`updateUser`/`deleteUser`/`selectAvatar`/`uploadAvatar`/`getAvatarHistory`）、表名 `users_user`、枚举值、JWT claims |
| L2 业务语义有承载位置 | ✅ 每个 RPC 用例 → `UserDomainService` 具体方法；归一化规则 → `AvatarDomainService` |
| L3 技术语义有承载方案 | ✅ 事务 → `@Transactional`（+ 独立 Bean）；jsonb 绑定 → TypeHandler + CAST；outbox/idempotency → Go 侧 user-rpc **不存在**，无需迁移 |
| 业务分支与异常路径已纳入 | ✅ 已逐个 logic 核对错误分支（用户不存在/密码错/封禁/权限） |
| 框架吸收有机制依据 | ✅ `user.go` → Spring Boot 启动；`service_context.go` → Spring DI；均指出实际承担机制 |
| 复用组件已明确 | ✅ `SnowflakeBean`、`JwtUtil`、`BusinessException`、`ErrorCode`、`RpcInterceptor` |
| 新增文件有职责依据 | ✅ `JsonbTypeHandler`（jsonb 映射）、`MybatisPlusOrderingBridgeAutoConfiguration`（框架版本适配）——均非"Go 有同名文件故建" |
| 合并有组织依据 | ✅ 9 logic → `UserDomainService`：同一变化原因 + 同一依赖边界 + HUMAN_ONLY.MD 决策 |
| 拆分可解释 | ✅ `updateUser` 业务/绑定分离、`avatarhelpers` 归一化独立 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Logic.java` / `Repository.java` / `DataModel.java` / `ServiceContext.java` |
| 未把未实现能力说成现有机制 | ✅ Flyway 归属标 `UNKNOWN`，未声称已解决 |

### 不通过项（必须显式指出）

| # | 问题 | 详情 |
|---|---|---|
| 1 | **Flyway 迁移归属未定** | `user-rpc` 的 `V1__create_user` 与 `admin-rpc` 的 `V1__create_admin` 抢同一 `flyway_schema_history` 版本号，后启动方校验和失败。Go 用 `AutoMigrate` 无状态故无此问题。**候选**：① 单一迁移归属 ② 每服务独立 schema。**需用户拍板**（AGENTS.md §6.3 问题 1） |
| 2 | **`selectAvatar` 事务边界未验证** | mapper 的 `default` 方法 / 同 Bean 内部调用上的 `@Transactional` 是否真生效，**当前无测试可证**。按 AGENTS.md §3.3，纯 Mockito 单测测不到这类静默失效 |
| 3 | **`service/` 下缺独立事务 Bean** | 头像 4/5 步原子性依赖事务，当前 `UserDomainService` 内部调用形态存在 self-invocation 风险（AGENTS.md §6.2 P1） |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| Flyway schema 归属 | 见上不通过项 #1 |
| `UserAvatarAtomicService` 是否需要新增 | 取决于 #2 的验证结论 |
| article/like 等模块对 user-rpc 的调用契约 | 本文件只推导 user-rpc 自身；跨模块调用面待对应模块推导后回填 |
