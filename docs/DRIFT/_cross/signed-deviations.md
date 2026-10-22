<!-- @move AGENTS.md L1864-L1888 · §3.4 已知偏离主表（N-1..N-5） -->
### 3.4 已知偏离（有意为之，需留档）

以下偏离经过确认并记录，审查时不必重复质疑，但也不得"顺手修正回旧行为"：

| 偏离 | 内容 | 理由 |
|---|---|---|
| HTTP path | `/user/v1/*`、`/admin/v1/*`（Go 为 `/usercenter/v1/user/*`、`/admincenter/v1/admin/*`） | HUMAN_ONLY.MD 决策；路由不属于行为语义，但属 wire 契约变更 |
| HTTP method | 统一 POST（Go 为 GET/DELETE 混合） | 同上 |
| 表名 | `users_user` / `admin_admin` | HUMAN_ONLY.MD 决策。**⚠️ 2026-10-21 补全 Go 侧真实表名**（原行只写「Go 为 GORM 默认名」，太笼统，写脚本时容易照抄成同名 —— 见下） |

**表名全映射（2026-10-21 取证自 Go 真源 `service/user/admin/rpc/internal/model/data_model.go`）**：

| 实体 | Go `TableName()` | RAG |
|---|---|---|
| User | `users` | `users_user` |
| Admin | **`admins`** | `admin_admin` |
| AdminInvite | **`admin_invites`** | `admin_invite` |
| UserAvatarHistory | `user_avatar_history` | `users_user_avatar_history` |

⚠️ **admin 侧是两端都改名，不是一端改**。扩展超时对拍时我按「admin_admin 两侧同名」写锁脚本，
`go_db` 立刻报 `relation "admins" does not exist` —— 而 `LOCK` 报错会让**整个事务 abort**，
连同一进程里已经拿到的 `users` 表锁也一起丢掉，于是 Go/user 在 363ms 就登录成功、
**看着像"RAG 超时更慢"的假偏差**。最终修法：每个 (库, 表) 用独立 psql 进程 + 加锁前
`to_regclass()` 校验存在，否则直接 `exit 1`。**教训：造阻塞的脚本必须先证明"锁真的加上了"。**

