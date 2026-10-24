<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 · 基线 commit c6ab92f -->
<!-- 本档 2026-10-25 首版：收 `global` 收口域的**跨域不变量**签字记录 -->

# global · 跨域不变量签字档（cross-domain）

> **归属**：`global` 收口域的「cross-domain」议题档（§5.3b）。
> **对象**：**跨 3+ 个域**的不变量 —— 修法落在某个域的代码里，但**决策是跨域的**。
> ⚠️ **与 `docs/DRIFT/global/cross-domain.md` 的边界**：
> DRIFT 侧记「**还没对齐、要修**」；本档记「**明知不一致、已认账**」（§5.3b 硬边界 2：一条问题只在一个档落笔）。

---

## SD-2026-10-25-03 ✅ **已签字（2026-10-25，用户拍板）** —— `message` 域索引 `idx_notification_recipient_read` **保留 RAG 的复合索引**

| 项 | 值 |
|---|---|
| 状态 | ✅ **已签字（2026-10-25，用户明确「保留 RAG 的复合索引并签字」）** |
| 签字决定 | **保留 RAG 的 `(recipient_id, is_read)` 复合索引**，**不改回** Go 的单列 `(is_read)` |
| 组件 | `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:29-30` |
| 对应的 DRIFT 条目 | **X-10**（`docs/DRIFT/global/cross-domain.md`）⇒ 已按 §5.3b 硬边界 2 **移除该条，只留指针** |
| 行为层 | **索引定义**（列集合）⇒ **无 wire 差异**（索引不改变返回行） |
| 证据强度 | ✅ **真 PG 活体双侧对读**（`msg_ddl_pg/go_db` vs `rag_db` 的 `pg_indexes`） |

### 差异的实际面

| 侧 | 索引定义（**真 PG 活体**） |
|---|---|
| **Go** | `CREATE INDEX idx_notification_recipient_read ON public.message_notification USING btree (is_read)` |
| **RAG** | `CREATE INDEX idx_notification_recipient_read ON public.message_notification USING btree (recipient_id, is_read)` |

⇒ **同名索引，列集合不同** ⇒ 属 §3.1 明确要求逐条对照的「索引」面。

### 根因：**Go 侧的一处声明笔误**（不是 RAG 的误解）

```go
// Sea-RideTheWind_golang/service/message/rpc/internal/model/data_model.go:32,38,41
RecipientID int64    `gorm:"column:recipient_id;not null;index:idx_notification_recipient_time,priority:1"`
IsRead      bool     `gorm:"column:is_read;not null;default:false;index:idx_notification_recipient_read,priority:2"`
CreatedAt   time.Time `gorm:"column:created_at;autoCreateTime;index:idx_notification_recipient_time,priority:2"`
```

⇒ `is_read` 声明的复合索引 `idx_notification_recipient_read` **只有 `priority:2`、没有 `priority:1`**；
而 `recipient_id` 的 `priority:1` 被写到了**另一个索引名**（`idx_notification_recipient_time`）上
⇒ GORM 于是只建出**单列索引** `(is_read)`
⇒ ⇒ **Go 的单列索引是「priority 写错了索引名」的产物**，不是设计意图；
⇒ RAG 的复合索引 `(recipient_id, is_read)` 才是**本意**
（它覆盖 `CountNotifications(unreadOnly=true)` + `MarkAllNotificationsRead` 的实际查询形状）。

### 为什么签字（而不是改回 Go）

1. **改回 Go = 把 RAG 改差**：单列 `(is_read)` 覆盖不了「按 recipient 过滤未读」的查询形状
   ⇒ 两侧的**查询返回行不变**（索引不影响语义），但 RAG 会**白白丢掉**一个能用的索引；
2. **根因在 Go 侧**：RAG 若照抄单列，等于**把真源的笔误复制成规范**
   —— 这正是 §3.1 反对的「拿现有实现当基准」的对立面：**笔误不该被对齐**；
3. **影响面极窄**：仅 `message` 域 1 张表的 1 个索引；**无 wire 差异**（客户端可见响应逐字节相同）。

### 签字后已做的落地

| # | 动作 | 落点 |
|---|---|---|
| ① | 迁移脚本里那条「对应 Go 端 Notification 上的 `@gorm:index:idx_notification_recipient_read,priority:2`」的注释**订正**（它会让人误以为 1:1）⇒ 改成**说明 Go 侧笔误 + 指向本档的指针** | `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:27-28` |
| ② | 从 `docs/DRIFT/global/cross-domain.md` 侧**移除**该条（只留指针） | `docs/DRIFT/global/cross-domain.md` X-10 |

### 守门

⚠️ **本条没有行为守门网**（与 `SD-2026-10-25-02` 同款）：
索引定义**不改变任何可观察行为** ⇒ 没有「行为断言」可写。
⇒ 能守的只有**「别把它悄悄改回单列」**：
① 迁移脚本里已留指针 + ② 本档是**唯一**记录处（DRIFT 侧已移除 ⇒ 改回去时无处可查 ⇒ 必被 review 逮到）。

### 留痕位置

- `docs/DRIFT/global/cross-domain.md` X-10（**已按 §5.3b 只留指针**）；
- `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:27-28`（代码侧指针）。

---

## 变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **首版**。`align/global` @ `c6ab92f` 产出。收录 `SD-2026-10-25-03`（`message` 域索引 `idx_notification_recipient_read`）—— 用户 2026-10-25 明确「**保留 RAG 的复合索引并签字**」⇒ 签字决定 = 保留 `(recipient_id, is_read)`，不改回 Go 的单列 `(is_read)`；根因是 **Go 侧 gorm tag 的 `priority:1` 写到了另一个索引名**上。配套：迁移脚本注释订正 + DRIFT 侧移除该条（只留指针） |
