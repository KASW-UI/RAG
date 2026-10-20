# admin-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/user/admin/rpc`
> 目标根包：`com.rag.users.admin.rpc`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

`admin-rpc` 在 RAG 中**已有完整 Java 实现**（非空骨架），实际包结构如下：

| 包 | 实际文件 | 职责 |
|---|---|---|
| （根） | `AdminRpcApplication` | `@SpringBootApplication` 入口 |
| `bootstrap/` | `NacosRegistration`、`SnowflakeBootstrap`、`AdminSeederBootstrap` | 启动期：Nacos 注册 / snowflake ID 初始化 / 默认 root admin 种子化 |
| `config/` | `AdminRpcProperties`、`MybatisPlusOrderingBridgeAutoConfiguration` | `@ConfigurationProperties` 绑定；MyBatis-Plus 自动配置桥接 |
| `domain/` | `AdminEntity`、`AdminInviteEntity`、`UserEntity` | `@TableName` 数据库实体（**注意**：admin-rpc 同时持 admin_admin / admin_invite / users_user 三表实体） |
| `exception/` | `AdminExceptions` | 业务异常聚合类（内部多个 static 嵌套类 + `isUniqueViolation` 工具） |
| `mapper/` | `AdminMapper`、`AdminInviteMapper`、`UserMapper`、`JsonbTypeHandler` | MyBatis-Plus `BaseMapper` 子接口 + jsonb TypeHandler |
| `metrics/` | `AdminRpcMetrics` | `@Component`，注入 `MeterRegistry`（25 个 Counter） |
| `service/` | `AdminDomainService`、`AdminCreateAtomicService`、`AdminHelpers` | 13 用例合并域服务 + CreateAdmin 4 步原子事务独立 Bean + 字符串归一化工具 |

### 已有组件（跨模块可复用）

| 组件 | 位置 | 承担 |
|---|---|---|
| `com.rag.common.response.Response` | `service/common` | HTTP/gRPC 统一响应信封 |
| `com.rag.common.exception.BusinessException` | `service/common` | 业务异常基类 |
| `com.rag.common.snowflake.Snowflake` + `SnowflakeBean` | `service/common` | 64 位雪花 ID |
| `com.rag.common.observability.*` | `service/common` | `RpcInterceptor` 等 |
| `com.rag.common.spring.RpcServerInterceptorFactory` | `service/common` | gRPC `ServerInterceptor` 暴露为 Bean，**但 admin-rpc 当前未导入它** —— 见下方警告 |
| `com.rag.users.common.jwt.JwtUtil` | `users-common` | JWT 签发（**admin-rpc 不验签**：验签归 admin-api 拦截器；admin-rpc 只在登录后签发） |
| `com.rag.users.common.errmsg.ErrorCode` | `users-common` | 业务错误码枚举 |
| `com.rag.users.common.cryptx.PasswordUtil` | `users-common` | bcrypt 加解密（admin-rpc 与 user-rpc 共享同一 `BCryptPasswordEncoder(STRENGTH=10)`） |

### 已确认的框架机制

- `net.devh:grpc-server-spring-boot-starter`：`@GrpcService` 类自动注册为 gRPC server handler
- MyBatis-Plus `@Mapper` + `BaseMapper<Entity>` + `@TableName`；`LambdaQueryWrapper` / `LambdaUpdateWrapper`
- Spring `@Transactional`（必须配独立 Bean，**详见下文 self-invocation 陷阱**）
- `org.flywaydb:flyway-core`：`flyway_schema_history` 有状态账本
- `spring-boot-starter-actuator` / Micrometer `MeterRegistry`
- Spring Cloud Alibaba Nacos Discovery

### 当前 Component 可以直接复用的能力

- **ID 生成**：复用 `SnowflakeBean` + `com.rag.common.snowflake.Snowflake.nextId()`
- **指标底座**：复用 `Observability` + `MeterRegistry`；`AdminRpcMetrics` 只承载 admin 域特有指标
- **gRPC 可观测性拦截器**：`RpcServerInterceptorFactory` **当前未注册**。`AdminRpcApplication.java:30` 只有 `@SpringBootApplication`（注释明写「只扫本模块」，且拒绝把 `com.rag.common` 放进 `scanBasePackages`），**没有任何 `@Import`** → common 里的 Bean 一个都进不来。
  > ⚠️ **2026-10-20 更正**：本文件早前版本多处写「已全局注册 / 已自动注册」，经 `FileTree/common.md` 交叉核对**证伪**。实测 admin-rpc **无 gRPC span、无 deadline 传播、无 gRPC 超时**。修法见 `FileTree/common.md` §12.7 #1。 **【已定案 2026-10-21】**
> **✅ 修法定案：`AdminRpcApplication` 补一行 `@Import(RpcServerInterceptorFactory.class)`，不拆包。** 依据 Go `admin/rpc/admin.go:33,43` 显式 `AddUnaryInterceptors(...)` —— **Go 里不存在全局自动注册**（§3.2）。
- **异常体系**：复用 `BusinessException` + `AdminExceptions`（业务异常聚合）
- **密码哈希**：复用 `PasswordUtil.encrypt` / `PasswordUtil.check`

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| Flyway 迁移归属 | ✅ **已定案 2026-10-21**：**单一归属 + 全局唯一版本号**。`users_user` / `users_user_avatar_history` 归 **user-rpc**；`admin_admin` / `admin_invite` 归 **admin-rpc**。admin-rpc **删除 `V3__create_user.sql`**，改由 `bootstrap/AdminSchemaGate` 校验存在性（缺失 fail-fast）。<br>**证据**：Go `user/rpc/.../db.go:33` = `AutoMigrate(&User{}, &UserAvatarHistory{})`、`admin/rpc/.../db.go:15` = `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` —— **Go 两个服务都建 `users_user`**，无冲突只因 `AutoMigrate` 无状态幂等。RAG 冲突面比原记录更宽：Go 全部服务连同一 `first_db`，共用一张 `flyway_schema_history`，**message-rpc 的 V1/V2/V3 同样与两个 users 模块撞号**，`IF NOT EXISTS` 只解决建表、解决不了版本号与 checksum。<br>**「每服务独立 schema」已被证据排除**：admin-rpc 直接查 `users_user`，拆 schema 必须跨 schema 查询或改转发，而 Go 两边都直连同一库。 |
| admin-rpc 是否应继续直写 `users_user` | ✅ **已定案 2026-10-21：不转发，保持直连。** 与 Go 一致，**不是缺陷**。<br>**证据**：`admin/rpc` 全仓 `UserRpc\|userpb\|UserService` **零命中**；`ServiceContext` 只有 `Config`/`AdminModel`/`BizRedis` —— **零出向 gRPC 依赖**；7 个管控操作全是 `internal/model/admin.go` 的直接 SQL；Go 的两份 `User` struct **逐字一致**（diff 空）。<br>**⚠️ 更正 AGENTS.md §6.3 的错误前提**：原文称"Go 把『读身份』边界建好了，只漏了『管理用户数据』"。实测 Go 的 gRPC 边界是 **api→rpc**——`identity.UserReader`（`user/identity/resolve.go:60-62`）签名带 `grpc.CallOption`（只能被 stub 满足），但两个调用方分别是 `user/api`（`svcCtx.UserRpc = userservice.NewUserService(zrpc.MustNewClient(...))`）与 `knowledge/api`，**都是 api**。Go 里**不存在 rpc→rpc 的用户数据边界**。<br>**推翻一条常见反对理由**：`CreateAdmin` **不建 User**（`createadminlogic.go` 只做锁邀请码 / 查重 / 插 Admin / 标记已用），7 个管控 RPC 也都是单表独立操作，**无跨边界事务**——所以"转发会破坏原子性"**不成立**。<br>**若仍要转发**：那是 §3.4 留档的有意偏离，需单独立 spec；代价是 admin-rpc **新增一个可用性依赖**（user-rpc 挂则管控挂，Go 无此耦合）+ 错误分类跨两层映射 + 7 个 RPC 多一跳。 |
| CreateAdmin 4 步原子事务 Bean 边界 | ✅ 已落地 `AdminCreateAtomicService` 独立 `@Service`（AGENTS.md §6.1 #2+#3 已修复）；`AdminDomainService.createAdmin` 跨 Bean 调用，`@Transactional` 代理生效；`SELECT ... FOR UPDATE` 已显式写在 `AdminInviteMapper`（实际写在 `AdminCreateAtomicService` 内 `LambdaQueryWrapper.last("FOR UPDATE")`） |

---

## 12.2 第二部分：Go 原始文件树

```
service/user/admin/rpc/
├── admin.go                                            # main 入口：flag、config、metrics、svc、server.Run
├── adminservice/adminservice.go                        # go-zero 生成的 gRPC service 接口
├── internal/
│   ├── config/config.go                                # Config 结构体：DataSource / BizRedis / AdminAuth / System / RpcServerConf
│   ├── svc/servicecontext.go                           # ServiceContext：AdminModel + BizRedis；ensureDefaultAdmin 启动期种子化
│   ├── server/adminserviceserver.go                    # gRPC server 装配：13 个 RPC 转发到对应 logic
│   ├── logic/
│   │   ├── loginlogic.go                               # Login
│   │   ├── logoutlogic.go                              # Logout（Redis blacklist）
│   │   ├── getselflogic.go                             # GetSelf（构造 AdminInfo，email → stringValue）
│   │   ├── updateselflogic.go                          # UpdateSelf（username 冲突 + optionalString email + 重读）
│   │   ├── createadminlogic.go                         # CreateAdmin（4 步事务 + SELECT FOR UPDATE + bcrypt + Snowflake uid）
│   │   ├── createinvitecodelogic.go                    # CreateInviteCode（SecureRandom hex + 最多 3 次重试）
│   │   ├── banuserlogic.go                             # BanUser（update status=1）
│   │   ├── unbanuserlogic.go                           # UnBanUser（update status=0）
│   │   ├── getuserlogic.go                             # GetUser（UserInfo 不设 score 字段）
│   │   ├── getuserlistlogic.go                         # GetUserList（keyword LIKE + 分页）
│   │   ├── updateuserlogic.go                          # UpdateUser（username 冲突 + email 非 null）
│   │   ├── deleteuserlogic.go                          # DeleteUser
│   │   ├── resetuserpasswordlogic.go                   # ResetUserPassword（不检查 RowsAffected，1:1 保留）
│   │   └── helper.go                                   # optionalString / stringValue
│   ├── model/
│   │   ├── admin.go                                    # Admin / AdminInvite / User 三个 GORM struct
│   │   ├── data_model.go                               # AdminModel 全部查询 / 更新方法 + Transaction 闭包
│   │   ├── db.go                                       # InitDB + AutoMigrate
│   │   ├── helper.go                                   # IsUniqueViolation（pgErr.Code == "23505"）
│   │   └── vars.go                                     # ErrorNotFound sentinel
│   └── metrics/metrics.go                              # AdminLoginCount + AdminActionCount（注意 "logal" typo 1:1 保留）
└── pb/                                                 # 生成物（不改）
```

---

## 12.3 第三部分：Go 文件职责分析

### `admin.go`
- **主要职责**：进程入口。解析 flag、加载 config、`metrics.InitMetrics()`、构造 `ServiceContext`、`zrpc.MustNewServer`、注册 `AdminServiceServer`、加 `observability.NewUnaryServerInterceptor`。
- **关键调用关系**：`config.MustLoad` → `svc.NewServiceContext(c)` → `server.NewAdminServiceServer(ctx)` → `s.Start()`。
- **业务语义**：无（纯装配）。
- **可替换**：Spring `@SpringBootApplication` + `@EnableDiscoveryClient` + `devh grpc-spring-boot-starter` 自动收集 `@GrpcService` Bean。⚠️ 但 `RpcServerInterceptorFactory` **不在自动收集范围内** —— starter 只收集**容器里已有**的 `ServerInterceptor` Bean，而 admin-rpc 既不扫 `com.rag.common` 也无 `@Import`，该 Bean 不在容器中。

### `internal/config/config.go`
- **主要职责**：配置结构体 —— `DataSource`、`BizRedis`、`AdminAuth{AccessSecret, AccessExpire}`、`System{DefaultPassword}`、`RpcServerConf`。
- **L1 外部契约**：admin JWT secret = `"yU-hCm)2*?NQmWG"`（**15 字节**，与 admin-api 共享）；`AccessExpire=2592000`（30 天）；`DefaultPassword="123456"`。
- **可替换**：Spring `@ConfigurationProperties(prefix="admin-rpc")`。**L4 关键**：admin secret 15 字节远 < jjwt 0.12 要求的 32 字节（HS256 / RFC 7518），Java 侧必须显式 `JwtUtil.padToHmacKeySize` 补齐；admin-rpc 侧签发 token 时已复用 `JwtUtil.getToken` 走同一份 padding，但 admin-rpc 端**不直接验签**（验签在 admin-api 拦截器），故本文件不涉及拦截器 Bean 创建时的 `WeakKeyException`（**P0 在 admin-api 侧**，本文件不背锅）。

### `internal/svc/servicecontext.go`
- **主要职责**：依赖持有（`Config` / `AdminModel` / `BizRedis`）+ 启动期 `ensureDefaultAdmin` 种子化（默认用户名 `admin`、默认密码 `admin` fallback，bcrypt + Snowflake uid + `InsertOneAdmin` + unique violation 重读兜底）。
- **L3 行为**：多实例并发启动时靠 `IsUniqueViolation` 识别另一实例已创建。
- **可替换**：Spring DI 覆盖依赖持有；启动期种子化拆为独立 `AdminSeederBootstrap`（`@EventListener(ApplicationReadyEvent)`）。`admin/admin` 默认密码已硬编码为 `AdminDomainService.DEFAULT_ADMIN_PASSWORD_FALLBACK`；`123456` 从 `admin-rpc.system.default-password` 配置注入（**1:1 保留 Go 端 fallback 链路**：配置空 → `"admin"` → 兜底）。

### `internal/server/adminserviceserver.go`
- **主要职责**：gRPC server 结构体与方法分发（13 个 RPC 转发到对应 logic）。
- **可替换**：`@GrpcService` 类继承 `AdminServiceGrpc.AdminServiceImplBase`，13 个 override 做 proto ↔ entity 转换、调 `AdminDomainService`、记指标、统一错误兜底（`AdminServiceGrpcImpl.invoke`）。

### `internal/logic/loginlogic.go`
- **主要职责**：按 username 查 → 比对密码 → 返 uid；4 个 metric 触发点（`user_not_found` / `db_error` / `wrong_answer` / `success`）。
- **关键错误码映射**：
  - `model.ErrorNotFound` → `Status.NOT_FOUND`（api 侧 → 1003 `ErrorUserNotExist`）
  - 密码错 → `Status.UNAUTHENTICATED`（api 侧 → 1002 `ErrorLoginWrong`，**AGENTS.md §6.1 #8 已修复**：缺这条映射会落到 1015）
  - DB 错 → `Status.INTERNAL`（→ 5001）
- **L2 强制保留**：username/password 错与"用户不存在"合并（**不区分**，1:1 跟随 Go 端 — Go 端也只返 NOT_FOUND，不区分两种 case）。

### `internal/logic/logoutlogic.go`
- **主要职责**：校验 token 非空 → `SetexCtx("admin:jwt_blacklist:%s", "banned", AccessExpire)` → 返回 `LogoutResp{}`（`Success` 字段保持 proto3 默认 false）。
- **L2 关键**：TTL 必须等于 JWT 过期秒数（`l.svcCtx.Config.AdminAuth.AccessExpire`），不能写死 24h（**AGENTS.md §6.1 #7 关联**：admin-rpc 与 user-rpc 同款语义）。

### `internal/logic/getselflogic.go`
- **主要职责**：`FindOneAdminByUid` → 构造 `AdminInfo{uid, username, email=stringValue(admin.Email), extra_info}`。
- **L2 关键**：`email` 是 `*string` nullable，Go 端用 `stringValue(admin.Email)` 兜底成 `""`（不是 nil）；Java 侧由 `AdminEntity.email` 用包装类型 `String`（`null` 表示 SQL NULL）+ `AdminServiceGrpcImpl.toProtoAdmin` 用 `AdminHelpers.stringValue` 兜底。

### `internal/logic/updateselflogic.go`
- **主要职责**：5 状态机。
  1. `FindOneAdminByUid` → NotFound/Internal
  2. `len(in.Username) > 0` → `FindOneAdminByUsername`；命中且 `existAdmin.Uid != in.Uid` → ALREADY_EXISTS
  3. 构造 `toUpdate *Admin`（跳零值）；`Email = optionalString(in.Email)`；`ExtraInfo` 仅 `in.ExtraInfo != nil` 时赋值
  4. `UpdateOneAdminByUid`
  5. 重读 → `UpdateSelfResp{Success=true, Admin=...}`
- **L2 关键**：Go 端 `Email = optionalString(in.Email)` 把 trim 后空串 → nil（写 SQL NULL），Java 侧对应 `AdminDomainService.updateSelf` 的 `if (email != null && !email.trim().isEmpty()) wrapper.set(...)` 跳过零值（**AGENTS.md §6.1 #6 已修复**：RAG 早期会把空 email 写成 NULL 静默丢邮箱）。

### `internal/logic/createadminlogic.go`
- **主要职责**：CreateAdmin 4 步原子事务（**核心**）。
  - W0 事务外：`optionalString` trim username/inviteCode、bcrypt password、Snowflake uid
  - 事务内（`AdminModel.Transaction`）：
    - W1 `FindAdminInviteByCodeForUpdate(tx, code)` — **`SELECT ... FOR UPDATE`** 悲观锁
    - W2 校验 invite 未用未过期（`UsedAt == nil && ExpiresAt.After(now)`）
    - W3 `FindOneAdminByUsernameTx(tx, username)` 冲突检查
    - W4 `InsertOneAdminTx(tx, admin)`
    - W5 `UpdateAdminInviteUsedTx(tx, invite.Id, uid, usedAt)`
  - 错误映射：`PermissionDenied` / `AlreadyExists` 原样透传；其他 → `Status.INTERNAL` "DB添加失败"
- **L3 强制保留**：
  - 4 步原子性 → Java 侧 `AdminCreateAtomicService` 独立 `@Service` + `@Transactional`（避免 self-invocation 静默失效，**AGENTS.md §6.1 #2+#3 已修复**）
  - `SELECT ... FOR UPDATE` 显式悲观锁（Go 端用 `clause.Locking{Strength: "UPDATE"}`，Java 端用 `LambdaQueryWrapper.last("FOR UPDATE")`）—— **RAG 实际位于 `AdminCreateAtomicService:67`**，而非 mapper default 方法

### `internal/logic/createinvitecodelogic.go`
- **主要职责**：
  - W1 `FindOneAdminByUid(operatorId)` 校验操作人（user_not_found → 404）
  - W2 循环 3 次（`inviteCodeMaxRetry=3`）：
    - `generateInviteCode()`：`SecureRandom.nextBytes(8)` → `"ADM-" + hex.ToUpper`
    - `InsertOneAdminInvite`；`IsUniqueViolation` → continue；其他 DB 错 → INTERNAL
  - 成功 → `CreateInviteCodeResp{InviteCode, ExpiresAt=unix}`（TTL = 10 分钟，`inviteCodeTTL = 10 * time.Minute`）
- **L2 关键**：重试上限 3 次；3 次仍冲突 → 抛 INTERNAL "管理员邀请码生成失败"（**不是 1006**，是 5001 经 api 侧映射）。

### `internal/logic/banuserlogic.go` / `unbanuserlogic.go`
- **主要职责**：`UpdateUserStatusByUid(uid, 1)` / `(uid, 0)`。
- **错误映射**：`RowsAffected=0` → `ErrorNotFound` → `Status.NOT_FOUND` "用户不存在"。
- **L2 关键**：`status` 列含义：0=正常 1=封禁。**注意**：admin-rpc 端的 user 表 `status` 与 user-rpc 端 user 表是**同一物理列**（`users_user.status`）。

### `internal/logic/getuserlogic.go`
- **主要职责**：`FindOneUserByUid` → 构造 `GetUserResp{User: UserInfo{...}}`；**`Found` 字段不显式设**（proto3 默认 false，1:1 跟随 Go 端）。
- **L2 关键**：`UserInfo` 的 `score` 字段**不设**（proto3 默认 0），与 Go 端保持一致。
- **陷阱**：admin-rpc 端 `UserInfo` 比 user-rpc 端 `UserInfo` **多一个 `score` 字段**（`admin.proto:16`）；但 admin-rpc **1:1 不写 score**。

### `internal/logic/getuserlistlogic.go`
- **主要职责**：`FindUserListByKeyword(page, pageSize, keyword)` → `(users, total)` → 逐行转 `UserInfo`（同样不写 score）。
- **L2 关键**：keyword 非空时 `username LIKE ? OR email LIKE ?`；空 keyword 全量 + `Order("id desc")`。

### `internal/logic/updateuserlogic.go`
- **主要职责**：5 状态机，与 UpdateSelf 对称但作用于 User 表：
  - W2 `FindOneUserByUid` 校验存在性（**注意**：user_not_found 抛 1003 + `update_user_not_found` metric）
  - W3 校验 username 冲突（**不排除自己** —— 与 updateSelf 不同）
  - W4 bcrypt password
  - W5 `UpdateOneUserByUid` + 重读
- **L2 关键**：
  - `Email = in.Email`（**非 optionalString**，user 表 email 非 null，写空串）
  - **6 个 metric 触发点**：user_not_found / db_error(×3) / internal_error(1) / success(1)
  - **AGENTS.md §6.1 #4 已修复**：Go 端写 `extra_info` 走 GORM `serializer:json` 自动序列化为 jsonb；RAG 早期用 `wrapper.set(Map)` 会被当成 varchar，**现在用 `setSql("extra_info = CAST({0} AS jsonb)", toJson(...))` 强制 CAST**

### `internal/logic/deleteuserlogic.go`
- **主要职责**：`DeleteOneUserByUid` → `RowsAffected=0` → `NOT_FOUND` "用户%d不存在"。
- **L2 关键**：description 模板带 uid（`fmt.Sprintf("用户%d不存在", in.Uid)`），便于定位；1:1 保留。

### `internal/logic/resetuserpasswordlogic.go`
- **主要职责**：`FindOneUserByUid` 校验存在 → bcrypt `System.DefaultPassword` → `UpdateUserPasswordByUid`。
- **L3 强制保留**：Go 端 `UpdateUserPasswordByUid` **不检查 RowsAffected**——并发删场景下仍返回 success。Java 侧 `AdminDomainService.resetUserPassword` 1:1 保留该语义（注释已写明）。

### `internal/logic/helper.go`
- **主要职责**：
  - `optionalString(value string) *string`：trim 后空 → nil；否则返回 &trimmed
  - `stringValue(value *string) string`：nil → ""；否则 *value
- **可替换**：Java 侧封装到 `AdminHelpers.optionalString` / `AdminHelpers.stringValue`（admin-rpc `service/AdminHelpers.java`）。**注意**：`helpers.go` 在 logic 包内，但调用方是 logic 各文件；Java 侧放 service/ 包下（**只放**，admin-api 端 `AdminApiSupport` 不放，避免与 user-api 端的 `UserApiSupport` 跨界混淆）。

### `internal/model/admin.go`
- **主要职责**：3 个 GORM struct：
  - `Admin{Uid, Username, Password, Email *string, ExtraInfo map[string]string}` → `TableName()="admins"`（Go 默认名）
  - `AdminInvite{Code, InviterUid, ExpiresAt, UsedByUid *int64, UsedAt *time.Time}` → `"admin_invites"`
  - `User{Uid, Username, Password, Email, Status int64 default 0, Score int32, ExtraInfo}` → `"users"`
- **L1 列结构 1:1**：列名、可空性、uniqueIndex、serializer 全部对应。
- **可替换**：MyBatis-Plus `@TableName` 实体。**注意已记录的表名偏离**：RAG 把 `"admins"` / `"admin_invites"` / `"users"` 改成 `"admin_admin"` / `"admin_invite"` / `"users_user"`（AGENTS.md §3.4 留档）。
- **关键陷阱**：**`Admin.Email` 是 `*string`（nullable）；`User.Email` 是 `string`（非 null）**。Java 侧 `AdminEntity.email` 用包装类型 `String`（null = SQL NULL）；`UserEntity.email` 也用 `String`（业务上不写 NULL，但保留 nullable 是为了与 GORM 行为一致）。

### `internal/model/data_model.go`
- **主要职责**：`AdminModel` 全部查询/更新方法 + `Transaction(ctx, fn(tx))` 闭包。
- **逐个方法映射**：
  - `Transaction` → Java `@Transactional`（+ 独立 Bean 规避 self-invocation）
  - `FindOneUserByUid/Username` → `UserMapper.selectOne` + `LambdaQueryWrapper`
  - `DeleteOneUserByUid` → `userMapper.delete`（RowsAffected=0 抛 NotFound）
  - `UpdateOneUserByUid` → `userMapper.update(null, LambdaUpdateWrapper)`
  - `FindOneAdminByUid/Username` → `adminMapper.selectOne`
  - `FindOneAdminByUsernameTx` → 事务内同名查询
  - `UpdateOneAdminByUid` → `adminMapper.update`
  - `UpdateUserStatusByUid` → 专用于 BanUser/UnBanUser，`RowsAffected=0` 抛 NotFound
  - `UpdateUserPasswordByUid` → ResetUserPassword 用
  - `InsertOneAdmin` / `InsertOneAdminTx` → `adminMapper.insert`
  - `InsertOneAdminInvite` → `adminInviteMapper.insert`
  - `FindAdminInviteByCodeForUpdate` → **悲观锁 SELECT FOR UPDATE**（Java 端放在 `AdminCreateAtomicService.createAdminAtomic` 内联 `LambdaQueryWrapper.last("FOR UPDATE")`，见该文件 65-69 行；并非 mapper default 方法）
  - `UpdateAdminInviteUsedTx` → `LambdaUpdateWrapper.set(usedByUid, usedAt)`
  - `FindUserListByKeyword` → `AdminDomainService.getUserList` 内的 `selectCount + selectList` 组合

### `internal/model/db.go`
- **主要职责**：`InitDB(dsn)` → `gorm.Open` + `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})`。
- **可替换**：Flyway `V*__create_*.sql`。**有状态账本 vs 无状态**：Go 端 AutoMigrate 无版本号；Flyway 是 schema ledger —— 双 rpc 服务抢同一 `flyway_schema_history` 触雷。
- **当前 RAG 状态**：admin-rpc 实际包含 V1+V2+V3 三份 DDL（`V3__create_user.sql` 用 `IF NOT EXISTS` 兼容 user-rpc 已建表）—— **临时方案，未拍板归属**。

### `internal/model/helper.go`
- **主要职责**：`IsUniqueViolation(err)` — 检查 `pgErr.Code == "23505"`。
- **可替换**：Java 侧 `AdminExceptions.isUniqueViolation(Throwable)`（位于 `exception/AdminExceptions.java`，SQLState 字符串匹配；遍历 cause 链，匹配到 `java.sql.SQLException`）。

### `internal/model/vars.go`
- **主要职责**：`ErrorNotFound = errors.New("record not found")` sentinel。
- **可替换**：Java `AdminExceptions.NotFoundException extends RuntimeException`（`exception/AdminExceptions.java` 嵌套类）。注意 Go 用 sentinel + `==`，Java 用 `instanceof`。

### `internal/metrics/metrics.go`
- **主要职责**：定义 2 个 Prometheus CounterVec + `InitMetrics()`（unregister Go/Process collectors + register 业务 Counter）。
- **L2 关键**：
  - `AdminLoginCount{Subsystem="admin_rpc", Name="login_total", Labels=["result"]}` —— 4 个 label：success / user_not_found / db_error / wrong_answer
  - `AdminActionCount{Subsystem="admin_rpc", Name="high_risk_action_logal", Labels=["action", "result"]}` —— **注意**：源码 `Name` 是 typo `logal`（应为 `total`），**1:1 保留** —— RAG `AdminRpcMetrics.java` 也用 `"admin_rpc_high_risk_action_logal"`，否则 Prometheus 看板看板名分裂
  - **覆盖 Go 端埋点的 7 个 Logic**：Login / CreateInviteCode / UpdateUser / DeleteUser / ResetUserPassword / BanUser / UnBanUser（GetSelf / UpdateSelf / GetUser / GetUserList / Logout / CreateAdmin 不埋点 → 1:1 不建 Counter）

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `admin.go` 进程装配 | `AdminRpcApplication` + Spring 容器 | ABSORB | `@SpringBootApplication` 覆盖 flag/config/server 组装 |
| `admin.go` 服务注册 | `bootstrap/NacosRegistration` | ADAPT | `@EventListener(ApplicationReadyEvent)` + `@PreDestroy` |
| `admin.go` UnaryServerInterceptor | `common/spring/RpcServerInterceptorFactory` | **ADAPT** | ⚠️ 当前未注册 —— 见 §12.1 更正。**已定案**：`AdminRpcApplication` 补 `@Import(RpcServerInterceptorFactory.class)`（§12.7 #1） |
| `config.go` 配置绑定 | `config/AdminRpcProperties` | ADAPT | Spring `@ConfigurationProperties(prefix="admin-rpc")` 替代 yaml struct |
| `config.go` AdminAuth.AccessSecret | **admin-rpc 不验签**，仅签发时复用 | REUSE | 走 `users-common/JwtUtil.getToken` 已含 padding |
| `servicecontext.go` 依赖持有 | Spring DI（构造器注入） | ABSORB | 无额外业务职责 |
| `servicecontext.go` ensureDefaultAdmin | `bootstrap/AdminSeederBootstrap` | SPLIT | 启动期生命周期职责独立成 Bean |
| `adminserviceserver.go` 13 个方法分发 | `service/AdminServiceGrpcImpl` | ADAPT | 分发由 starter 完成，方法体（参数转换 + 指标 + 调 domain + 错误兜底）需显式写 |
| `loginlogic.go` 登录规则 | `service/AdminDomainService.login` | MERGE | 13 个 logic 合并进单一 `AdminDomainService` |
| `loginlogic.go` 4 个 Counter | `metrics/AdminRpcMetrics`（loginUserNotFound/loginDbError/loginWrongAnswer/loginSuccess） | ADAPT | 25 个 Counter 全部归 `AdminRpcMetrics` |
| `logoutlogic.go` blacklist | `service/AdminDomainService.logout` | MERGE | 同上 |
| `getselflogic.go` | `AdminDomainService.getSelf` | MERGE | 同上 |
| `updateselflogic.go` 5 状态机 | `AdminDomainService.updateSelf` | MERGE | 同上 |
| `updateselflogic.go` 空 email 跳过 | `AdminDomainService.updateSelf` 的 `email != null && !email.trim().isEmpty()` 条件 | ADAPT | **AGENTS.md §6.1 #6 修复**：空 email 不写 NULL |
| `updateselflogic.go` extra_info 序列化 | `wrapper.setSql("extra_info = CAST({0} AS jsonb)", toJson(...))` | ADAPT | **AGENTS.md §6.1 #4 修复**：MP wrapper 丢 typeHandler |
| `createadminlogic.go` 4 步事务 | `service/AdminCreateAtomicService.createAdminAtomic` + `@Transactional` | SPLIT | **独立 Bean 规避 self-invocation**（AGENTS.md §6.1 #2+#3 已修复） |
| `createadminlogic.go` SELECT FOR UPDATE | `AdminCreateAtomicService.createAdminAtomic` 内联 `LambdaQueryWrapper.last("FOR UPDATE")` | ADAPT | 悲观锁必须显式 |
| `createadminlogic.go` bcrypt + Snowflake uid | `AdminDomainService.createAdmin` 事务外预计算 | ADAPT | 1:1 保留事务外 bcrypt/uid |
| `createinvitecodelogic.go` | `AdminDomainService.createInviteCode` | MERGE | 13 合 1 |
| `createinvitecodelogic.go` 3 次重试 + SecureRandom + ADM- 前缀 | `AdminDomainService` 内部常量 | ADAPT | TTL=10min / maxRetry=3 / randomBytes=8 / prefix="ADM-" |
| `createinvitecodelogic.go` 4 个 Counter | `AdminRpcMetrics`（createInviteUserNotFound/DbError/InternalError/Success） | ADAPT | 25 Counter 同上 |
| `banuserlogic.go` | `AdminDomainService.banUser` + `UserMapper.update` (set status=1) | MERGE | 跨表（`users_user`）走 `UserMapper` |
| `unbanuserlogic.go` | `AdminDomainService.unBanUser` | MERGE | 同上 |
| `getuserlogic.go` UserInfo 不写 score | `AdminServiceGrpcImpl.toProtoUser` 注释已写明不 setScore | ADAPT | proto3 默认 0，1:1 跟随 |
| `getuserlistlogic.go` 分页 + keyword | `AdminDomainService.getUserList` | MERGE | 内联 `selectCount + selectList` 替代 mapper default 方法 |
| `updateuserlogic.go` 5 状态机 + 6 Counter | `AdminDomainService.updateUser` + `AdminRpcMetrics`（4 Counter：userNotFound/DbError/InternalError/Success） | MERGE | 同上 |
| `updateuserlogic.go` jsonb 写入 | `wrapper.setSql("extra_info = CAST({0} AS jsonb)", toJson(...))` | ADAPT | 同上 |
| `updateuserlogic.go` User.Email 非 null | `email != null && !email.isEmpty()` 不进 trim | ADAPT | 1:1 保留 Go 端非 optionalString |
| `deleteuserlogic.go` | `AdminDomainService.deleteUser` + `UserMapper.delete` | MERGE | 同上 |
| `deleteuserlogic.go` description 带 uid | `Status.NOT_FOUND.withDescription(String.format("用户%d不存在", uid))` | ADAPT | 1:1 保留 description 模板 |
| `resetuserpasswordlogic.go` | `AdminDomainService.resetUserPassword` | MERGE | 同上 |
| `resetuserpasswordlogic.go` 不检查 RowsAffected | `userMapper.update` 不看返回值 | ADAPT | 1:1 保留 |
| `resetuserpasswordlogic.go` System.DefaultPassword | `properties.getSystem().getDefaultPassword()` + 兜底 `"admin"` | ADAPT | 1:1 保留 Go 端 fallback 链路 |
| `helper.go` optionalString / stringValue | `service/AdminHelpers.optionalString` / `stringValue` | REUSE | 字符串归一化工具类 |
| `model/admin.go` Admin struct | `domain/AdminEntity` (`@TableName("admin_admin")`) | ADAPT | 列/约束 1:1；表名按 AGENTS.md §3.4 偏离 |
| `model/admin.go` AdminInvite struct | `domain/AdminInviteEntity` (`@TableName("admin_invite")`) | ADAPT | 同上 |
| `model/admin.go` User struct | `domain/UserEntity` (`@TableName("users_user")`) | ADAPT | 同上 |
| `model/data_model.go` 事务闭包 | Spring `@Transactional`（**必须独立 Bean**） | ADAPT | 框架替代闭包 |
| `model/data_model.go` AdminModel 方法 | 3 个 Mapper (`AdminMapper` + `AdminInviteMapper` + `UserMapper`) | SPLIT | 按实体拆（admin-rpc 有 3 Mapper —— 用户表单独拆，便于 user-rpc 端零依赖共享 User 表） |
| `model/data_model.go` SELECT FOR UPDATE | `AdminCreateAtomicService` 内联（**不放在 mapper default 方法**） | ADAPT | 4 步事务在同一 Bean，mapper 留 default 注释指向调用方 |
| `model/db.go` AutoMigrate | Flyway `V3__create_admin.sql` + `V4__create_admin_invite.sql`（版本号全局唯一） | ADAPT | 有状态账本；**归属已定**：admin 表归 admin-rpc，users 表归 user-rpc，本模块**不再建 `users_user`** |
| `model/helper.go` IsUniqueViolation | `exception/AdminExceptions.isUniqueViolation` | ADAPT | 遍历 cause 链 + SQLState 字符串匹配 |
| `model/vars.go` ErrorNotFound sentinel | `exception/AdminExceptions.NotFoundException` (RuntimeException) | ADAPT | Java 用 exception subtype + `instanceof` |
| `model/*` 异常 | `exception/AdminExceptions` + `common/BusinessException` | MERGE | 按业务含义聚合（NotFoundException + InviteUnavailableException + UsernameTakenException） |
| `metrics/metrics.go` Prometheus Counter | `metrics/AdminRpcMetrics` | ADAPT | Micrometer 替代 Go prometheus client；25 Counter |
| `metrics/metrics.go` "logal" typo | 同名 `"admin_rpc_high_risk_action_logal"` | KEEP | **1:1 保留**：RAG Prometheus name 必须与 Go 对齐 |
| RPC 可观测性 span | `common/observability/RpcInterceptor` | REUSE | 无需自建 |
| Snowflake 初始化 | `bootstrap/SnowflakeBootstrap` | ADAPT | 复用 `common/SnowflakeBean` |
| 默认 admin 种子化 | `bootstrap/AdminSeederBootstrap`（`@EventListener(ApplicationReadyEvent)`） | ADAPT | 与 `servicecontext.ensureDefaultAdmin` 同语义，独立成 Bean |
| Nacos discovery | `bootstrap/NacosRegistration` + `@EnableDiscoveryClient` | ADAPT | 自动注册 admin-rpc 端口 49220 |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/users/admin/rpc/
├── AdminRpcApplication.java
├── bootstrap/
│   ├── NacosRegistration.java
│   ├── SnowflakeBootstrap.java
│   ├── AdminSeederBootstrap.java
│   └── AdminSchemaGate.java               # ★ 校验 users_user 存在，缺失 fail-fast（不创建）
├── config/
│   ├── AdminRpcProperties.java
│   └── MybatisPlusOrderingBridgeAutoConfiguration.java
├── domain/
│   ├── AdminEntity.java
│   ├── AdminInviteEntity.java
│   └── UserEntity.java
├── exception/
│   └── AdminExceptions.java
├── mapper/
│   ├── AdminMapper.java
│   ├── AdminInviteMapper.java
│   ├── UserMapper.java
│   └── JsonbTypeHandler.java
├── metrics/
│   └── AdminRpcMetrics.java
└── service/
    ├── AdminDomainService.java
    ├── AdminCreateAtomicService.java
    ├── AdminHelpers.java
    └── AdminServiceGrpcImpl.java
```

### 逐文件说明

**`AdminRpcApplication.java`**
- 所属包：`com.rag.users.admin.rpc`
- 主要职责：Spring Boot 入口，`@SpringBootApplication` + `@MapperScan("com.rag.users.admin.rpc.mapper")`
- 承载 Go 职责：`admin.go` 进程装配
- 为什么独立：4 个 rpc 模块统一入口风格
- 依赖：`config/AdminRpcProperties`

**`bootstrap/NacosRegistration.java`**
- 所属包：`com.rag.users.admin.rpc.bootstrap`
- 主要职责：`ApplicationReadyEvent` 时向 Nacos 注册 admin-rpc 服务（端口 49220）；`@PreDestroy` 注销
- 承载 Go 职责：`admin.go` 中 `zrpc.MustNewServer` + 进程注册
- 为什么独立：生命周期职责与业务无关
- 依赖：`NacosServiceManager`

**`bootstrap/SnowflakeBootstrap.java`**
- 所属包：`com.rag.users.admin.rpc.bootstrap`
- 主要职责：启动期初始化雪花 ID 生成器（workerId/datacenterId 来自 `admin-rpc.snowflake.*`）
- 承载 Go 职责：`servicecontext.go` 里 snowflake 依赖的初始化
- 为什么独立：启动期 fail-fast 职责
- 依赖：`common/spring/SnowflakeBean`

**`bootstrap/AdminSeederBootstrap.java`**
- 所属包：`com.rag.users.admin.rpc.bootstrap`
- 主要职责：`@EventListener(ApplicationReadyEvent)` 时种子化 root 管理员（`admin`/`<System.DefaultPassword>`）
- 承载 Go 职责：`servicecontext.go` 的 `ensureDefaultAdmin`（**L4 推导**：Go 端是 ServiceContext 方法，Java 端拆为独立 Bean 以纳入 Spring 启动期生命周期）
- 为什么独立：与 `SnowflakeBootstrap` 同属启动期职责，但 ensureDefaultAdmin 涉及业务（admin 表插入）→ 单独成类
- 依赖：`AdminMapper`、`PasswordUtil`、`SnowflakeBean`、`AdminRpcProperties`

**`bootstrap/AdminSchemaGate.java`**（✅ 已定案 2026-10-21 新增）
- 所属包：`com.rag.users.admin.rpc.bootstrap`
- 主要职责：启动期校验 `users_user` 表存在，**缺失则 fail-fast 退出**；**只校验、不创建**
- 承载 Go 职责：`internal/model/db.go:15` 的 `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` —— Go 侧 admin-rpc **自己也建 `users_user`**；失败走 `log.Fatalln("数据表迁移失败")`。RAG 侧迁移归属 user-rpc，admin-rpc 无法再建，但**必须保留 fail-fast 语义**：缺表时若无条件启动，所有 7 个用户管控 RPC 会在运行时炸成难懂的 SQL 错误
- 为什么独立：**L3 语义载体**，不是启动期副作用的补充。Go 的 `AutoMigrate` 兼具"建表 + 缺表即死"两重语义；Flyway 是单一归属的有状态账本，只保得住后半截。若不建这个门禁，Go 的 fail-fast 保证在 RAG 侧静默丢失
- 依赖：`DataSource` / `JdbcTemplate`（仅查 `information_schema.tables`），不依赖任何 Mapper
- **测试要求（§3.3）**：需一条真正连库的测试——mock 掉 DataSource 的单测不执行这条路径，等于没测

**`config/AdminRpcProperties.java`**
- 所属包：`com.rag.users.admin.rpc.config`
- 主要职责：`@ConfigurationProperties(prefix="admin-rpc")` 绑定 DSN / Redis / AdminAuth / System
- 承载 Go 职责：`config/config.go` 全部字段（DataSource / BizRedis / AdminAuth / System）
- 为什么独立：配置绑定是独立关注点
- 依赖：无（纯 POJO）

**`config/MybatisPlusOrderingBridgeAutoConfiguration.java`**
- 所属包：`com.rag.users.admin.rpc.config`
- 主要职责：适配 MyBatis-Plus 与目标 Spring Boot 版本的 Bean 装配差异
- 承载 Go 职责：无直接 Go 对应（**L4 推导**，纯框架桥接）
- 为什么独立：框架版本适配不应污染业务包
- 依赖：MyBatis-Plus

**`domain/AdminEntity.java`**
- 所属包：`com.rag.users.admin.rpc.domain`
- 主要职责：`@TableName(value="admin_admin", autoResultMap=true)` 实体；`@TableId(type=IdType.AUTO)`；`@TableField(typeHandler=JsonbTypeHandler.class)` 标 jsonb 列
- 承载 Go 职责：`model/admin.go` 的 `Admin` struct + struct tag（**L1 字段对应**：uid / username / password / email (*string) / extra_info）
- 为什么独立：HUMAN_ONLY.MD 决策「domain/Entity」；AdminEmail 用包装类型 `String`（null = SQL NULL）替代 Go `*string`
- 依赖：`mapper/JsonbTypeHandler`

**`domain/AdminInviteEntity.java`**
- 所属包：`com.rag.users.admin.rpc.domain`
- 主要职责：`@TableName("admin_invite")` 实体
- 承载 Go 职责：`model/admin.go` 的 `AdminInvite` struct（code / inviter_uid / expires_at / used_by_uid / used_at）
- 为什么独立：与 Admin 是不同持久化用途（**业务含义**：邀请码是 admin 准入凭证，与 admin 实体有独立生命周期）
- 依赖：无

**`domain/UserEntity.java`**
- 所属包：`com.rag.users.admin.rpc.domain`
- 主要职责：`@TableName(value="users_user", autoResultMap=true)` 实体
- 承载 Go 职责：`model/admin.go` 的 `User` struct（**注意**：Go 端 admin-rpc 通过 `internal/model/admin.go` 同时持 `User` struct，RAG 完全照搬 —— admin-rpc 直写 `users_user`）
- 为什么独立：admin-rpc 端独立持有（不依赖 user-rpc）；字段定义与 user-rpc 端 `UserEntity` 完全一致（同一物理表两侧共同读写）
- 依赖：`mapper/JsonbTypeHandler`

**`exception/AdminExceptions.java`**
- 所属包：`com.rag.users.admin.rpc.exception`
- 主要职责：业务异常聚合：
  - `NotFoundException`(record not found)
  - `InviteUnavailableException`(邀请码无效)
  - `UsernameTakenException`(用户名已被占用)
  - `isUniqueViolation(Throwable)`(SQLState 23505 识别)
- 承载 Go 职责：`model/vars.go`（ErrorNotFound）+ `model/helper.go`（IsUniqueViolation）
- 为什么独立：RAG 既有约定（`UserExceptions` / `MessageExceptions` 同款：nested static class + 工具方法）
- 依赖：`common/exception/BusinessException`、`org.springframework.dao.DataIntegrityViolationException`

**`mapper/AdminMapper.java`**
- 所属包：`com.rag.users.admin.rpc.mapper`
- 主要职责：`@Mapper interface AdminMapper extends BaseMapper<AdminEntity>`；标准 CRUD 由 `BaseMapper` 提供
- 承载 Go 职责：`model/data_model.go` 中 `AdminModel` 的 Admin 相关方法
- 为什么独立：HUMAN_ONLY.MD 决策「按实体拆 Mapper」—— admin-rpc 有 3 个 Mapper（admin / adminInvite / user）是因为 admin-rpc 跨 3 张表
- 依赖：`domain/AdminEntity`

**`mapper/AdminInviteMapper.java`**
- 所属包：`com.rag.users.admin.rpc.mapper`
- 主要职责：`@Mapper interface AdminInviteMapper extends BaseMapper<AdminInviteEntity>`
- 承载 Go 职责：`model/data_model.go` 中 AdminModel 的 AdminInvite 相关方法（`InsertOneAdminInvite`）
- 为什么独立：独立生命周期（邀请码插入与查询、悲观锁、used 标记）
- 依赖：`domain/AdminInviteEntity`

**`mapper/UserMapper.java`**
- 所属包：`com.rag.users.admin.rpc.mapper`
- 主要职责：`@Mapper interface UserMapper extends BaseMapper<UserEntity>`
- 承载 Go 职责：`model/data_model.go` 中 AdminModel 的 User 相关方法（`FindOneUserByUid/Username` / `DeleteOneUserByUid` / `UpdateOneUserByUid` / `UpdateUserStatusByUid` / `UpdateUserPasswordByUid` / `FindUserListByKeyword`）
- 为什么独立：与 user-rpc 端 `UserMapper` 共享同一物理表但**写入路径不同**（admin-rpc 写 `status` / `password`，user-rpc 写 `avatar_url` / `extra_info`）—— 两侧 Mapper 是**职责分工而非重复实现**
- 依赖：`domain/UserEntity`

**`mapper/JsonbTypeHandler.java`**
- 所属包：`com.rag.users.admin.rpc.mapper`
- 主要职责：PG `jsonb` 列 ↔ `Map<String, String>` 的 TypeHandler（**关键**：用 `ps.setObject(i, json, Types.OTHER)` 让 PG 按 jsonb/unknown 解释，不被当成 varchar）
- 承载 Go 职责：`model/admin.go` 的 `ExtraInfo map[string]string` GORM tag `serializer:json`
- 为什么独立：RAG 既有约定（admin-rpc / user-rpc / user-api 各自一份同名文件；详见 user-api.md）
- 依赖：MyBatis + Jackson

**`metrics/AdminRpcMetrics.java`**
- 所属包：`com.rag.users.admin.rpc.metrics`
- 主要职责：`@Component`，注入 `MeterRegistry`；25 个 Counter：
  - 4 login（userNotFound/DbError/WrongAnswer/Success）
  - 4 createInviteCode（userNotFound/DbError/InternalError/Success）
  - 4 updateUser（userNotFound/DbError/InternalError/Success）
  - 3 deleteUser（userNotFound/DbError/Success）
  - 4 resetPassword（userNotFound/DbError/InternalError/Success）
  - 3 ban（userNotFound/DbError/Success）
  - 3 unban（userNotFound/DbError/Success）
- 承载 Go 职责：`metrics/metrics.go` 全部 Counter（**`high_risk_action_logal` typo 1:1 保留**）
- 为什么独立：指标有独立的变化原因
- 依赖：Micrometer

**`service/AdminDomainService.java`**
- 所属包：`com.rag.users.admin.rpc.service`
- 主要职责：13 个业务用例方法合 1：`login` / `logout` / `getSelf` / `updateSelf` / `createAdmin` / `createInviteCode` / `banUser` / `unBanUser` / `getUser` / `getUserList` / `updateUser` / `deleteUser` / `resetUserPassword` + 内部 helper `findOneAdminByUid/Username` / `findOneUserByUid/Username`
- 承载 Go 职责：`logic/*.go` 全部 13 个文件（不含 `helper.go`）
- 为什么独立：HUMAN_ONLY.MD 决策「13 合 1」；合并依据是同一变化原因（admin 域）+ 同一依赖边界（AdminMapper + AdminInviteMapper + UserMapper + StringRedisTemplate + PasswordUtil + AdminRpcMetrics）
- 依赖：`mapper/AdminMapper`、`mapper/AdminInviteMapper`、`mapper/UserMapper`、`users-common/cryptx/PasswordUtil`、`metrics/AdminRpcMetrics`、`StringRedisTemplate`、`AdminRpcProperties`、`service/AdminCreateAtomicService`、`service/AdminHelpers`

**`service/AdminCreateAtomicService.java`**
- 所属包：`com.rag.users.admin.rpc.service`
- 主要职责：CreateAdmin 4 步原子事务（独立 Bean）：`createAdminAtomic` 含 W1 悲观锁查 invite / W2 校验 invite 未用未过期 / W3 查 username 冲突 / W4 插 admin / W5 标 invite used
- 承载 Go 职责：`logic/createadminlogic.go` 4 步事务闭包
- 为什么独立：**必须独立成 Bean** —— Spring `@Transactional` 靠 AOP 代理；self-invocation 时注解静默失效（AGENTS.md §3.3 + §6.1 #2+#3 已修复）。原同类直调方案退化成 auto-commit，4 步独立提交，邀请码并发消费可成功两次
- 依赖：`mapper/AdminMapper`、`mapper/AdminInviteMapper`

**`service/AdminHelpers.java`**
- 所属包：`com.rag.users.admin.rpc.service`
- 主要职责：`optionalString(value: String) → String`（trim 后空 → null；否则 trim 返回）+ `stringValue(value: String) → String`（null → ""；否则原值）
- 承载 Go 职责：`logic/helper.go` 的两个函数
- 为什么独立：字符串归一化规则在 13 用例中**多处复用**（updateSelf / updateUser / toProtoAdmin / createAdmin），独立成静态工具类避免散落
- 依赖：无

**`service/AdminServiceGrpcImpl.java`**
- 所属包：`com.rag.users.admin.rpc.service`
- 主要职责：`@GrpcService extends AdminServiceGrpc.AdminServiceImplBase`；13 个 override 做 proto ↔ entity 转换、调 `AdminDomainService`、记指标；`invoke` 统一错误兜底
- 承载 Go 职责：`server/adminserviceserver.go`（13 个 RPC 分发）
- 为什么独立：gRPC 接口实现是独立契约边界
- 依赖：`service/AdminDomainService`、`service/AdminHelpers`

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `admin.go` | `AdminRpcApplication` + Spring 容器 | ABSORB | 进程装配全部由框架承担 |
| `admin.go`（Nacos 注册） | `bootstrap/NacosRegistration` | ADAPT | 注册语义保留，API 换 Spring 事件 |
| `admin.go`（UnaryServerInterceptor） | `common/spring/RpcServerInterceptorFactory` | **ADAPT** | ⚠️ 当前未注册 —— 见 §12.1 更正；补 `@Import` 即可 |
| `config/config.go` | `config/AdminRpcProperties` | ADAPT | 绑定机制换 Spring，字段 1:1 |
| `config/config.go`（AdminAuth） | `users-common/JwtUtil.getToken`（admin-rpc 仅签发） | REUSE | 签发走同一份 padding |
| `svc/servicecontext.go` | Spring DI | ABSORB | 纯依赖持有 |
| `svc/servicecontext.go`（snowflake 初始化） | `bootstrap/SnowflakeBootstrap` + `common/SnowflakeBean` | REUSE | ID 生成已有实现 |
| `svc/servicecontext.go`（ensureDefaultAdmin） | `bootstrap/AdminSeederBootstrap` | SPLIT | 启动期生命周期职责独立成 Bean |
| `server/adminserviceserver.go` | `service/AdminServiceGrpcImpl` | ADAPT | 分发由 starter 做，方法体需写 |
| `logic/loginlogic.go` | `service/AdminDomainService.login` | MERGE | 13 合 1 |
| `logic/logoutlogic.go` | `AdminDomainService.logout` | MERGE | 同上 |
| `logic/getselflogic.go` | `AdminDomainService.getSelf` | MERGE | 同上 |
| `logic/updateselflogic.go` | `AdminDomainService.updateSelf` + jsonb `setSql` CAST | SPLIT | 业务归 service，jsonb 绑定细节归 mapper TypeHandler + `setSql` |
| `logic/createadminlogic.go`（4 步事务 + SELECT FOR UPDATE） | `service/AdminCreateAtomicService.createAdminAtomic` | SPLIT | 独立 Bean 规避 self-invocation |
| `logic/createadminlogic.go`（bcrypt / Snowflake uid 事务外） | `AdminDomainService.createAdmin` | MERGE | 事务外预计算保留 |
| `logic/createinvitecodelogic.go` | `AdminDomainService.createInviteCode` | MERGE | 13 合 1 |
| `logic/banuserlogic.go` | `AdminDomainService.banUser` | MERGE | 同上 |
| `logic/unbanuserlogic.go` | `AdminDomainService.unBanUser` | MERGE | 同上 |
| `logic/getuserlogic.go` | `AdminDomainService.getUser` | MERGE | 同上 |
| `logic/getuserlistlogic.go` | `AdminDomainService.getUserList` | MERGE | 同上 |
| `logic/updateuserlogic.go` | `AdminDomainService.updateUser` + jsonb `setSql` CAST | SPLIT | 业务归 service，jsonb 归 TypeHandler |
| `logic/deleteuserlogic.go` | `AdminDomainService.deleteUser` | MERGE | 同上 |
| `logic/resetuserpasswordlogic.go` | `AdminDomainService.resetUserPassword` | MERGE | 同上 |
| `logic/helper.go` | `service/AdminHelpers` | SPLIT | 字符串归一化独立工具类（13 用例共享） |
| `model/admin.go`（Admin） | `domain/AdminEntity` | ADAPT | 表名按 AGENTS.md §3.4 改为 `admin_admin` |
| `model/admin.go`（AdminInvite） | `domain/AdminInviteEntity` | ADAPT | 表名 `admin_invite` |
| `model/admin.go`（User） | `domain/UserEntity` | ADAPT | 表名 `users_user`；与 user-rpc 端 UserEntity 共享物理表 |
| `model/data_model.go`（事务闭包） | `@Transactional`（+ 独立 Bean） | ADAPT | 框架替代闭包 |
| `model/data_model.go`（AdminModel 方法） | `AdminMapper` + `AdminInviteMapper` + `UserMapper` | SPLIT | 按实体拆 3 Mapper |
| `model/data_model.go`（SELECT FOR UPDATE） | `AdminCreateAtomicService.createAdminAtomic` 内联 | ADAPT | 锁与事务同 Bean，mapper 留注释指向调用方 |
| `model/db.go` | Flyway `V3__create_admin.sql` + `V4__create_admin_invite.sql` | ADAPT | **归属已定**：删除 `V3__create_user.sql`；`users_user` 的校验归 `bootstrap/AdminSchemaGate`（Go 的 `log.Fatalln` fail-fast 语义 → 只校验不创建） |
| `model/helper.go` | `exception/AdminExceptions.isUniqueViolation` | ADAPT | SQLState 23505 |
| `model/vars.go` | `exception/AdminExceptions.NotFoundException` | ADAPT | Java 用 RuntimeException 子类 + `instanceof` |
| `model/*`（错误路径） | `exception/AdminExceptions` | MERGE | 按业务含义聚合 |
| `metrics/metrics.go` | `metrics/AdminRpcMetrics` | ADAPT | Micrometer 替代；25 Counter |
| `metrics/metrics.go`（"logal" typo） | `admin_rpc_high_risk_action_logal`（同名） | KEEP | **1:1 保留**：RAG Prometheus name 必须与 Go 对齐 |
| —（无 Go 对应） | `config/MybatisPlusOrderingBridgeAutoConfiguration` | NEW | 纯 L4 框架桥接 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 13 个 gRPC 方法、表名 `admin_admin`/`admin_invite`/`users_user`、AdminAuth 15 字节 secret / 30 天 expire、邀请码 TTL 10 分钟 / 重试 3 次 / ADM- 前缀、DefaultPassword="123456" 兜底 admin |
| L2 业务语义有承载位置 | ✅ 每个 RPC 用例 → `AdminDomainService` 具体方法；4 步事务 → `AdminCreateAtomicService`；邀请码悲观锁 → `LambdaQueryWrapper.last("FOR UPDATE")` |
| L3 技术语义有承载方案 | ✅ 事务 → `@Transactional`（独立 Bean 规避 self-invocation）；jsonb 绑定 → `JsonbTypeHandler` + `setSql CAST`；Snowflake 复用 → `common/SnowflakeBean` |
| 业务分支与异常路径已纳入 | ✅ 逐个 logic 核对：用户不存在/密码错/封禁/invite 失效/username 冲突/uid 不存在 |
| 框架吸收有机制依据 | ✅ `admin.go` → Spring Boot 启动；`servicecontext.go` → Spring DI + 独立 Bootstrap；`adminserviceserver.go` → `@GrpcService` |
| 复用组件已明确 | ✅ `SnowflakeBean`、`JwtUtil`、`BusinessException`、`ErrorCode`、`PasswordUtil` 确为复用；`RpcServerInterceptorFactory` **已定案**为补 `@Import` 后复用（§12.1 更正 / `common.md` §12.7 #1） |
| 新增文件有职责依据 | ✅ `JsonbTypeHandler`（jsonb 映射）、`MybatisPlusOrderingBridgeAutoConfiguration`（框架版本适配）、`AdminCreateAtomicService`（独立事务 Bean）、`AdminSeederBootstrap`（启动期种子化）、`AdminHelpers`（归一化工具）—— 均非"Go 有同名文件故建" |
| 合并有组织依据 | ✅ 13 logic → `AdminDomainService`：同一变化原因 + 同一依赖边界 + HUMAN_ONLY.MD 决策 |
| 拆分可解释 | ✅ `updateSelf`/`updateUser` 业务/jsonb 分离；`createAdminAtomic` 独立 Bean；`AdminHelpers` 归一化独立；3 个 Mapper 按实体拆（admin 跨 3 表） |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Logic.java` / `Repository.java` / `DataModel.java` / `ServiceContext.java` / `Types.java` / `Server.java` |
| 未把未实现能力说成现有机制 | ✅ Flyway 归属与 admin-rpc 边界均已按 AGENTS.md §6.3（2026-10-21）定案，**未自造结论**——两条都附 Go 侧实测证据 |
| **`AdminRpcMetrics` 25 Counter 与 Go 端 1:1** | ✅ 含 `"high_risk_action_logal"` typo 保留；4 login + 4 createInvite + 4 update + 3 delete + 4 resetPassword + 3 ban + 3 unban = 25 |
| **`AdminServiceGrpcImpl` 13 override 与 Go 端 1:1** | ✅ proto3 默认值（Found=false / Success=false）保留；`Success=true` 在响应构造时**显式 set**（非透传 rpcResp.getSuccess()）以避免 proto3 默认 false 误报 |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **Flyway 迁移归属未定** | admin-rpc 的 V1/V2/V3 与 user-rpc 的 V1/V2 抢同一 `flyway_schema_history`。**当前 RAG 临时**靠 `V3__create_user.sql` 用 `IF NOT EXISTS` 兼容 user-rpc 已建过表，但**版本号双服务启动顺序敏感的根问题未解**。Go 用 `AutoMigrate` 无状态故无此问题。**候选**：① 单一迁移归属 ② 每服务独立 schema。**需用户拍板**（AGENTS.md §6.3 问题 1） |
| 2 | **admin-rpc 直写 `users_user` 表** | 边界缺失：admin-rpc 通过 `UserMapper` 直接读写用户表（BanUser / UnBanUser / UpdateUser / DeleteUser / ResetUserPassword / GetUser / GetUserList），而 `grep UserRpc admin/rpc` 零命中：admin-rpc **永不走 user-rpc gRPC**。RAG 全仓零 `role`/`permission` 概念，"是否 admin" 完全由"这行存在哪张表"决定。**方向**：沿**数据所有权**切线 —— admin-rpc 的 7 个用户管控 RPC 改为转发 user-rpc。**需用户拍板**（AGENTS.md §6.3 问题 2） |
| 3 | **`AdminRpcMetrics` 缺 Counter 的 7 个 Logic** | GetSelf / UpdateSelf / GetUser / GetUserList / Logout / CreateAdmin 在 Go 端**不埋点**（D-016），RAG 1:1 不补。**这是有意保留**，不是缺陷 —— 但需要在 §12.7 通过项里明示，避免后续 review 误以为"漏建 Counter" |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|---|
| Flyway schema 归属 | 见不通过项 #1 |
| admin-rpc 是否应改为转发 user-rpc | 见不通过项 #2 |
| `AdminRpcMetrics` 25 个 Counter 是否需要补 7 个未埋点 Logic 的指标 | Go 端 1:1 不埋是设计决策，但 RAG 是否要补全未知；AGENTS.md §6.2 P3 阶段无此项说明 |
| `model/data_model.go` 的 `FindUserListByKeyword` 是否应改为 Mapper default 方法 | 当前 RAG 选择在 `AdminDomainService.getUserList` 内联 `selectCount + selectList` + `LambdaQueryWrapper.last("LIMIT ...")`；若未来要拆为 mapper default 方法（与 Go 端对称），需评估 MyBatis-Plus 对 `default` + 自定义 SQL 的兼容性 |
