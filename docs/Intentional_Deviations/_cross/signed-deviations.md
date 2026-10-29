<!-- @move AGENTS.md L1864-L1888 · §3.4 已知偏离主表（N-1..N-5） -->

> **本文件属于 `docs/Intentional_Deviations/` 档案** ——「已知偏离（有意为之，需留档）」独立成档的原因：
> 这类条目**是决策不是缺陷**，审查时不必重复质疑，但**也不得「顺手修正回旧行为」**；
> 与 DRIFT（差异台账）分开，才不会被「未修项」的心智误读为待办。

---

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


---

## 2026-10-23 组件级审查（12 个 api/rpc）新增的签字偏离

> 这 3 条由 **2026-10-23 用户逐条拍板**（「保留 RAG 现状，登记为签字偏离」），
> 不是 agent 自行决定的。每条都写明 **Go 行为 / RAG 行为 / 为什么接受不一致**，
> 并指向对应的 DRIFT 台账（**台账里仍保留原始差异记录，不因签字而删除**）。

### SD-2026-10-23-01 · admin-api `OperatorId`：Go 恒 0 且不校验 claim，RAG 严格校验 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> ⚠️ **2026-10-29 真实性复核（主线 · 逐字对照两侧）⇒ 原判「✅ 已签字」⇒ 现行态 = ✅ 仍成立** —— **被签字描述的行为今天两侧都还在**：Go 侧 `service/user/admin/api/internal/logic/admin/resetuserpasswordlogic.go:38-39` `adminUid, _ = uidNumber.Int64()`（**忽略解析错误 ⇒ claim 缺失时恒为 0**）＋ `:43` `OperatorId: adminUid`；RAG 侧 `service/users/admin/admin-api/src/main/java/com/rag/users/admin/admin-api/service/AdminResetUserPasswordService.java:37` `operatorId = IdentityResolver.claimedUID(httpRequest)`（**严格取 claim**）（同族 5 处：`AdminDeleteUserService.java:37` · `AdminBanUserService.java:37` · `AdminUnBanUserService.java:37` · `AdminUpdateUserService.java:53` · `AdminCreateInviteCodeService.java:44`）⇒ 差异未消除（**RAG 更严**）⇒ **仍成立、不计入「失效」** · 核对日期 2026-10-29

| 项 | 内容 |
|---|---|
| **组件** | `admin-api`（台账：[`DRIFT/users/admin/api.md`](../../DRIFT/users/admin/api.md)） |
| **Go 行为** | 操作者 id 恒为 `0`，且**不校验** JWT 里有没有对应 claim |
| **RAG 行为** | 从 JWT claim 取操作者并**严格校验**，缺失即拒绝 |
| **类别** | `AUTHENTICATION / AUTHORIZATION SEMANTIC MISMATCH` |
| **签字结论** | **保留 RAG 的严格校验**。Go 的「恒 0 + 不校验」是安全缺口而不是业务语义 —— 复刻它等于把一个已知弱点也搬过来 |
| **⚠️ 代价（必须知情）** | 两侧**可观察行为确实不同**：Go 接受而 RAG 拒绝的请求存在。任何跨实现对拍在这条上都会红，**属预期差异，不是回归** |
| **⚠️ 不得做的事** | 不要为了让对拍变绿而给 RAG 放宽校验。若日后 Go 侧补上校验，则应反向收敛 RAG，而不是保持差异 |

### SD-2026-10-23-02 · user-api `uid`：Go 有两套解析语义，RAG 统一拒绝 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> ⚠️ **2026-10-29 真实性复核（主线 · 逐字对照两侧）⇒ 原判「✅ 已签字」⇒ 现行态 = ✅ 仍成立** —— RAG 侧 `service/users/user/user-api/src/main/java/com/rag/users/user/api/service/IdentityResolver.java:129`（**统一走严格拒绝**，注释直接引 Go `identity/resolve.go:75-77` `if err != nil || uid <= 0 { return 0, ErrInvalidClaim }`）＋ `auth/JwtSignatureVerifier.java:114` 同款引用⇒ **两侧语义未收敛** ⇒ **仍成立**（RAG 取更严那一侧）· 核对日期 2026-10-29

| 项 | 内容 |
|---|---|
| **组件** | `user-api`（台账：[`DRIFT/users/user/api.md`](../../DRIFT/users/user/api.md)） |
| **Go 行为** | 两条路径的 `uid` 解析**不一致**：一条静默转 0，另一条严格拒绝 |
| **RAG 行为** | 统一走严格拒绝 |
| **类别** | `ERROR SEMANTICS / STRING PARSING MISMATCH` |
| **签字结论** | **保留 RAG 的统一拒绝**。Go 两条路径不一致是它自己的缺陷；RAG 选更严的那一侧是合理收敛 |
| **⚠️ 代价** | 与 Go「静默转 0」那条路径的输入，Go 会继续执行（通常随后失败或误判），RAG 直接拒绝。可观察行为不同，**属预期** |
| **⚠️ 不得做的事** | 不要为对齐而引入「静默转 0」——那会把 Go 的缺陷固化成 RAG 的行为 |

### SD-2026-10-23-03 · message-api `AdminSendNotification` 的 `affected`：Go 返 `int64`，RAG 输出字符串 ｜ **状态（核对日期 2026-10-29）：✅ 仍成立**

> ⚠️ **2026-10-29 真实性复核（主线 · 逐字对照两侧）⇒ 原判「✅ 已签字」⇒ 现行态 = ✅ 仍成立** —— RAG 侧 `service/message/message-api/src/main/java/com/rag/message/message/api/dto/AdminSendNotificationResp.java:43-44` `@JsonFormat(shape = JsonFormat.Shape.STRING)` + `private long affected;` ⇒ 输出 `{"affected":"3"}`（**字符串**）；Go 侧 `service/message/api/internal/types/types.go:22` `Affected int64 \`json:"affected"\`` ⇒ 输出 `{"affected":3}`（**数字**）⇒ 差异未消除 ⇒ **仍成立**（**不得「以 proto 里写着 int64 为由」单方面改回数字** ⇒ 改须重新签字）。⚠️ **别被 proto 误导**：两侧 proto 的该字段**都是** `int64 affected = 3;`（`proto/message.proto:98`）⇒ **JSON 形态由 Jackson 注解决定，不由 proto 决定** · 核对日期 2026-10-29

| 项 | 内容 |
|---|---|
| **组件** | `message-api`（台账：[`DRIFT/message/api.md`](../../DRIFT/message/api.md)） |
| **Go 行为** | proto 字段为 `int64`，**没有** `,string` 修饰 ⇒ JSON 里是**数字** |
| **RAG 行为** | 加了 `,string` ⇒ JSON 里是**字符串** |
| **类别** | `WIRE CONTRACT / SERIALIZATION MISMATCH` |
| **签字结论** | **保留 RAG 的字符串形态，不改 wire 契约** |
| **🔴 附带处置** | 绿测试 `MessageApiMvcPipelineTest#adminSendNotificationAffectedIsString`（`:786-800`）用 `jsonPath("$.data.affected").value("3")` 断言了**字符串**形态。**该 DisplayName 实测已是中性措辞**（`admin 发通知：affected 是字符串；定向发送的 recipient_ids 只有 1 个`，不含「Go: …」），故**无需改措辞**；断言值**保留** —— 它正是本条签字结论的一部分 |
| **⚠️ 不得做的事** | 不要因为 proto 里写着 `int64` 就单方面改回数字 —— 那会让已签字的偏离变成未受控的漂移。若要改，须重新签字 |
| **⚠️ 对拍时的读法** | 这条会让 `message-api` 的 wire 层对拍**必然为红**。看到 `affected` 是 `"3"` 而非 `3` 时，先确认是不是本条，再下结论 |

> **三条的共性**：它们都不是「RAG 忘了做」，而是「RAG 做了更严/更安全的那一侧」。
> 审查与对拍时**统一按「预期差异」处理**：看到这三条变红，先确认是不是这三条之一，
> 再决定是否处理 —— **不要**把它们当成新回归去「修」。
