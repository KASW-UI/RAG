<!-- @move AGENTS.md §7.1 · Go 真源锚点表（参考资料，非政策） -->

# Go 真源锚点表

> 从 AGENTS.md §7.1 搬出：判定行为时读 Go 哪个文件。**这是参考资料，不是政策。**


**`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang`** —— 冻结的唯一真源

| RAG 子模块 | Go 对应路径 |
|---|---|
| `service/users` | `<go>/service/user` |
| `service/message` | `<go>/service/message` |

常用锚点（审计 `service/users` 时已验证）：

| 主题 | Go 路径 |
|---|---|
| 路由 / 鉴权中间件装配 | `service/user/user/api/internal/handler/routes.go` |
| 响应封装（显式 `httpx.OkJson`） | `service/user/admin/api/internal/handler/admin/*.go` |
| 建表（`AutoMigrate`，无状态） | `service/user/{user,admin}/rpc/internal/model/db.go` |
| 事务边界（闭包传 `tx`） | `service/user/admin/rpc/internal/logic/createadminlogic.go` |
| 邀请码行锁（`clause.Locking`） | `service/user/admin/rpc/internal/model/admin.go` |
| 身份解析（**走 gRPC**，边界正确的范例） | `service/user/user/identity/resolve.go` |
| JWT 签发（无长度校验） | `service/user/common/jwt/jwt.go` |
| 字段与 ORM 映射（struct tag） | `service/user/admin/rpc/internal/model/*.go` |

常用锚点（审计 `service/message/message-rpc` 时已验证，2026-10-22）：

| 主题 | Go 路径 |
|---|---|
| 9 个 RPC 的业务分支（`SendChatMessage` 最复杂） | `service/message/rpc/internal/logic/*.go` |
| 跨 RPC 共享语义工具（`canSend` / `peerOf` / `canonicalParticipants` / `TrimPreview`） | `service/message/rpc/internal/logic/helper.go` |
| **事务边界（显式 `conn.Transaction`）** | `service/message/rpc/internal/model/message.go:186`、`:196` |
| 建表（`AutoMigrate`，`TimeZone=Asia/Shanghai` 写在 DSN 里） | `service/message/rpc/internal/model/db.go:20` |
| 业务码 → gRPC status 的双向转换（`bizCodeByMsg` 反查表） | `service/message/common/errmsg.go` |
| 服务发现与超时（yaml 未配 `Timeout` ⇒ 走 go-zero 默认 2000ms） | `service/message/rpc/etc/message.yaml` |
