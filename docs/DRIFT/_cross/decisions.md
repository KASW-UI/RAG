<!-- @move AGENTS.md §6.3 问题 1~3 · 架构决策详述（证据与取舍） -->

# 架构决策详述（问题 1~3）

> 从 AGENTS.md §6.3 搬出：**结论留在 AGENTS.md，证据与取舍过程在这里。**

---

**✅ 问题 1：建表机制 —— 2026-10-21 用户拍板「直接把 Flyway 丢掉」。**（原为待定，2026-10-20 曾定案为「保留 Flyway + 全局版本号」，该结论已被推翻）

| 表 | 谁建 |
|---|---|
| `users_user` / `users_user_avatar_history` / `admin_admin` / `admin_invite` | **user-rpc 与 admin-rpc 都会建**（幂等，谁先启动都一样） |
| message 三张表 | **message-rpc 仍用 Flyway**（本次未动，见下方范围说明） |

执行要点：

1. **4 份 DDL 收归 `users-common/src/main/resources/db/*.sql`**，编号前缀（`01_`…`04_`）
   只保证确定性执行顺序，**不表示版本**。
2. `users-common` 的 `com.rag.users.common.ddl.SchemaInitializer` 用 `ScriptUtils` 原样重放；
   user-rpc / admin-rpc 各自的 `bootstrap/UserSchemaBootstrap` / `AdminSchemaBootstrap`
   在 `@PostConstruct` 里调用，任一 DDL 失败即启动失败（对齐 Go 的 `log.Fatalln`）。
3. `AdminSeederBootstrap` 显式 `@DependsOn("adminSchemaBootstrap")`。
4. 两个 rpc 的 `spring-boot-flyway` / `flyway-core` / `flyway-database-postgresql`
   **三件套全部从 pom 移除** —— 只删 `flyway-core` 是不够的，`spring-boot-flyway`
   在 classpath 上就会让 `FlywayAutoConfiguration` 按默认值自动 migrate。

Go 侧证据：`user/rpc/internal/model/db.go:33` = `AutoMigrate(&User{}, &UserAvatarHistory{})`，`admin/rpc/internal/model/db.go:15` = `AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` —— **Go 两个服务都建 `users_user`**，
Go 之所以无冲突，正因为 `AutoMigrate` 无状态且幂等。丢掉 Flyway 之后 Java 侧回到同一条路上。

**⚠️ 已知代价（第一次 `ALTER` 之前可以无视）**：脚本要同时承担「建表」与「升级」两件事，
只能到处加 `ADD COLUMN IF NOT EXISTS`；也没有任何地方记录「这张表现在该长什么样」。
**第一次 `ALTER` 之前必须重新引入版本化机制。**

**⚠️ 本问题的实际范围比原记录更宽**：`message-rpc` **仍在用 Flyway**，本次未动。
它与 users 域共用同一个 `first_db`，一旦 users 域也留下 Flyway 账本就会撞号 ——
现在 users 域已经没有账本了，所以 message-rpc 暂时安全。
若日后 message 域要接入 users 域的数据、或想让 message-rpc 也去掉 Flyway，
按同一套 `SchemaInitializer` 模式处理即可。

**✅ 问题 2：`admin-rpc` 边界 —— 不转发，保持直连；RAG 现状即与 Go 一致，不是缺陷。**（原为待定）

实测证据：

- `admin/rpc` 全仓 `UserRpc` / `userpb` / `UserService` **零命中**；`ServiceContext` 只有 `Config` / `AdminModel` / `BizRedis` —— **零出向 gRPC 依赖**
- 7 个用户管控 RPC（`BanUser` / `UnBanUser` / `UpdateUser` / `DeleteUser` / `ResetUserPassword` / `GetUser` / `GetUserList`）全是 `internal/model/admin.go` 的直接 SQL
- Go 两份 `User` struct（`user/rpc` 与 `admin/rpc` 的 `internal/model/data_model.go`）**逐字一致** —— Go 自己就有两份同样的表定义

**⚠️ 原记录的前提有误，已更正**：原文称「Go 把『读身份』边界建好了，只漏了『管理用户数据』」。实测 Go 的 gRPC 边界是 **api→rpc**：`identity.UserReader`（`user/identity/resolve.go:60-62`）签名带 `grpc.CallOption`（只能被 gRPC stub 满足），但调用方只有 `user/api`（`svcCtx.UserRpc = userservice.NewUserService(zrpc.MustNewClient(...))`）与 `knowledge/api` —— **都是 api**。Go 里**不存在 rpc→rpc 的用户数据边界**，「读身份」与「管用户数据」不是同一个轴。

**⚠️ 一条常见反对理由已被推翻**：`CreateAdmin` **不建 User**（只做锁邀请码 / 查重 / 插 `Admin` / 标记已用，见 `createadminlogic.go`），7 个管控 RPC 也都是单表独立操作，**无跨边界事务** —— 所以「转发会破坏 `CreateAdmin` 原子性」**不成立**。

若日后仍决定转发，属 §_cross/signed-deviations.md 留档的有意偏离，须单独立 spec；代价是：admin-rpc **新增一个可用性依赖**（user-rpc 挂则管控挂，Go 无此耦合）+ 错误分类跨两层映射 + 7 个 RPC 多一跳延迟。

**📌 问题 3：`RpcServerInterceptorFactory` 注册 —— 显式 `@Import`，不拆包。**（2026-10-21 新增）

Go 侧每个 rpc 服务都在入口显式注册（`user/rpc/user.go:32,43`、`admin/rpc/admin.go:33,43`、`hot/rpc/hot.go:32,42` 三处一致）：

```go
rpcTimeout := observability.DisableNativeRpcTimeout(&c.RpcServerConf)
s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))
```

**Go 里不存在「全局自动注册」**。RAG 把 `@Component` 当自动注册，是一次**无出处 idiomatization**（§3.2）—— `UserRpcApplication` 的 `@Import` 只含 3 个 bean、`AdminRpcApplication` 连 `@Import` 都没有，两者**当时**均无 gRPC span / 无 deadline 传播 / 无 gRPC 超时。

> **2026-10-21 已实施并验证**：本问题已随 `row/common-fix-semantic-drift` 合并落地（见 §docs/DRIFT/common/audit.md）。`ObservabilityWiringTest` 用 `@SpringBootTest` 断言 `ServerInterceptor` 确实是容器里的 Bean —— 正是这条「自动注册」假设此前无任何测试覆盖，才让缺陷长期静默。`message-rpc` 归一到显式 `@Import` 仍**未做**，见 §common/_signed-deviations.md 效力边界。

定案（**2026-10-21 已实施**，见 §docs/DRIFT/common/audit.md）：`@Import` 数组补 `RpcServerInterceptorFactory.class`（`@Import` 本就接受普通 `@Component` 类，**零搬迁**）。连带结果：**不再有任何模块扫 `com.rag.common`** ⇒ servlet 类不会被拖进纯 gRPC 进程 ⇒ §12.5 曾提的 `spring/web/` + `spring/grpc/` 拆包**动机自我消解，已撤回**。`message-rpc` 现在能跑属**撞运气**（恰好有 `spring-boot-starter-web-server` 兜住），须一并归一到显式 `@Import`。
