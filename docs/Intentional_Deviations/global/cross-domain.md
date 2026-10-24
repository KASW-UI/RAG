<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit c6ab92f -->
<!-- 本档 2026-10-25 首版：收 `global` 收口域的**跨域不变量**签字记录 -->

# global · 跨域不变量签字档（cross-domain）

> **归属**：`global` 收口域的「cross-domain」议题档（§5.3b）。
> **对象**：**跨 3+ 个域**的不变量 —— 修法落在某个域的代码里，但**决策是跨域的**。
> ⚠️ **与 `docs/DRIFT/global/cross-domain.md` 的边界**：
> DRIFT 侧记「**还没对齐、要修**」；本档记「**明知不一致、已认账**」（§5.3b 硬边界 2：一条问题只在一个档落笔）。

---

## ❌ SD-2026-10-25-03 —— **已撤销（重复登记）** ⇒ 真实记录处 = message 域的 `SD-2026-10-23-06`

| 项 | 值 |
|---|---|
| 状态 | ❌ **已撤销（2026-10-25，本轮自查发现）** —— **不是**一条新的签字偏离，**不产生第二条签字** |
| 撤销理由 | **同一条偏离早已被签字过一次**：message 域的 [`SD-2026-10-23-06`](../../Intentional_Deviations/message/rpc.md)（**2026-10-23 用户拍板**）**内容完全同条** —— 同一个索引 `idx_notification_recipient_read`、同一处根因（Go 侧 gorm tag 的 `priority:1` 写到了另一个索引名）、同一条保留理由（真实过滤是 `WHERE recipient_id = ? AND is_read = false`）⇒ 本条属**重复登记** ⇒ 按 §5.3b 硬边界 2「一条问题只在一个档落笔」撤销 |
| ⚠️ **编号不可寻址** | `SD-2026-10-23-06` 在 **3 个域各有含义**（`like/rpc.md` / `message/rpc.md` / `users/admin/rpc.md`）⇒ **本条指的是 `message/rpc.md` 那一条**（索引列数）⇒ 引用时**必须带域限定**，别按编号盲查（见 [`docs/Intentional_Deviations/_INDEX.md` §编号不可寻址](../../../Intentional_Deviations/_INDEX.md)） |
| 真实记录处 | [`docs/Intentional_Deviations/message/rpc.md` §SD-2026-10-23-06](../../Intentional_Deviations/message/rpc.md) |
| 对应的 DRIFT 条目 | `docs/DRIFT/message/rpc.md` §7 `DRIFT-MSG-V-022`（**2026-10-23 已签字销账**，按该域惯例**保留原始差异记录**）⇒ global 侧的 X-10 同理**只留指针** |
| 行为层 | **索引定义**（列集合）⇒ **无 wire 差异**（索引不改变返回行） |
| 证据强度 | ✅ **真 PG 活体双侧对读**（`msg_ddl_pg/go_db` vs `rag_db` 的 `pg_indexes`）—— 本轮 B3 复核，与 2026-10-23 结论**一致** |

> **用户 2026-10-25 的原话「X-10 保留 RAG 的复合索引并签字」依然有效** ——
> 只是**签字早已存在**（`SD-2026-10-23-06`，2026-10-23）⇒ 本轮**无需再签**，只需把 global 侧的指针指对。

### 本轮（B3）补充取证：真实查询形状逐条核实 —— 支撑 SD-06 的保留理由

Go 侧 `service/message/rpc/internal/model/message.go` 三个函数的过滤形状**逐条核实**：

| 函数 | 行号 | 过滤条件 | 是否被 `(recipient_id, is_read)` 复合索引覆盖 |
|---|---|---|---|
| `CountNotifications`（`unreadOnly=true`） | `:30` + `:32` | `WHERE recipient_id = ?` **AND** `is_read = false` | ✅ 正好是复合索引的**前缀** |
| `ListNotifications`（`unreadOnly=true`） | `:40` + `:42` | `WHERE recipient_id = ?` **AND** `is_read = false` | ✅ 同上 |
| `ListNotifications` 内的未读计数 | `:52` | `WHERE recipient_id = ? AND is_read = false` | ✅ 同上 |
| `MarkAllNotificationsRead` | `:89` | `WHERE recipient_id = ? AND is_read = false` | ✅ 同上 |

⇒ **4 处查询全部落在复合索引的前缀上** ⇒ 改回 Go 的单列 `(is_read)` 会让这 4 处**全部退化成「先按 is_read 定位再回表过滤 recipient_id」**
⇒ **签字保留 RAG 的复合索引是有实测依据的，不是「将就」**（与 SD-06 的判据一致，此处只是把查询点从 1 个补全到 4 个）。

### 签字后已做的落地（本轮仍然保留 —— 这部分是 global 的实际动作）

| # | 动作 | 落点 |
|---|---|---|
| ① | 迁移脚本里那条「对应 Go 端 Notification 上的 `@gorm:index:idx_notification_recipient_read,priority:2`」的注释**订正**（它会让人误以为 1:1）⇒ 改成**说明 Go 侧笔误 + 指向真实签字记录** | `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:27-38` |
| ② | 从 `docs/DRIFT/global/cross-domain.md` 侧**移除**该条（只留指针） | `docs/DRIFT/global/cross-domain.md` X-10 |

### 守门

⚠️ **本条没有行为守门网**（与 `SD-2026-10-25-02` 同款；⚠️ 后者 2026-10-25 05:46 已退回**待签字**）：
索引定义**不改变任何可观察行为** ⇒ 没有「行为断言」可写。
⇒ 能守的只有**「别把它悄悄改回单列」**：
① 迁移脚本里已留指针 + ② **签字档是唯一记录处**（DRIFT 侧已移除 ⇒ 改回去时无处可查 ⇒ 必被 review 逮到）。

### 留痕位置

- `docs/Intentional_Deviations/message/rpc.md` §`SD-2026-10-23-06`（**真实签字记录**）；
- `docs/DRIFT/message/rpc.md` §7 `DRIFT-MSG-V-022`（**已销账**，保留原始记录）；
- `docs/DRIFT/global/cross-domain.md` X-10（**已按 §5.3b 只留指针**）；
- `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:27-38`（代码侧指针）。

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **首版**。`align/global` @ `c6ab92f` 产出。收录 `SD-2026-10-25-03`（`message` 域索引 `idx_notification_recipient_read`）—— 用户 2026-10-25 明确「**保留 RAG 的复合索引并签字**」⇒ 签字决定 = 保留 `(recipient_id, is_read)`，不改回 Go 的单列 `(is_read)`；根因是 **Go 侧 gorm tag 的 `priority:1` 写到了另一个索引名**上。配套：迁移脚本注释订正 + DRIFT 侧移除该条（只留指针）<br>⚠️ **该条已于同日撤销**（见下行）⇒ 读本行时**必须**接着读下一行 |
| 2026-10-25 | ⚠️ **撤销 `SD-2026-10-25-03`（重复登记）** —— 复核发现**同一条偏离早已被签字**：message 域的 `SD-2026-10-23-06`（2026-10-23 用户拍板）**内容完全同条**（同一索引 / 同一根因 / 同一保留理由）⇒ 按 §5.3b「一条问题只在一个档落笔」**撤销本条** ⇒ **真实记录处 = message 域的 `SD-2026-10-23-06`**。⚠️ 顺带记录该编号的**不可寻址性**：`SD-2026-10-23-06` 在 3 个域各有含义 ⇒ 引用必须带域限定。本条保留**本轮 B3 的补充取证**（Go 侧 4 处查询形状逐条核实 ⇒ 全部落在复合索引前缀上），其余内容随撤销一并移除 |
