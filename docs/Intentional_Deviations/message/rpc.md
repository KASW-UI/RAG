<!-- @new 2026-10-23 23:1x · 由 `align/message` 工作树产出 · 基线 commit 8422116 -->

# `message-rpc` 签字偏离（域内索引）

> **正本在本文件**；总索引见 [`../_INDEX.md`](../_INDEX.md)（⚠️ 该文件是**共享面**，
> 按 `docs/Worktree.md` §三，所有工作树都不许碰 ⇒ 合并后由主会话统一登记本条的编号）。

## 本域签字偏离：1 条

### SD-2026-10-23-06 · `idx_notification_recipient_read` 的列数：Go 1 列 / RAG 2 列

| 项 | 内容 |
|---|---|
| **组件** | `message-rpc`（DDL 台账：[`DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) §7 的 `DRIFT-MSG-V-022`） |
| **Go 行为** | `CREATE UNIQUE … ON (is_read)` —— 普通索引，**只有 1 列** |
| **RAG 行为** | `ON (recipient_id, is_read)` —— **2 列** |
| **类别** | `DDL / STRUCTURE`（非 wire 契约；数据语义与唯一性保证**完全相同**） |
| **签字结论** | **2026-10-23 用户拍板：保留 RAG 现状，不回改成 Go 的单列索引** |
| **根因** | Go 的 struct tag 里 `recipient_id` 挂的是**另一个**索引（`index:idx_notification_recipient_time,priority:1`），而 `is_read` 用 `index:idx_notification_recipient_read,priority:2` —— 该索引**没有 priority:1 成员**，GORM 就只建一列。RAG 侧按索引名的字面意图（"recipient + read"）建了两列 |
| **⚠️ 为什么保留是对的（不是「将就」）** | `ListNotifications` 的过滤条件是 `WHERE recipient_id = ? AND is_read = false`。RAG 的两列索引**正好**是这个前缀，能同时过滤两列；Go 的单列索引只能定位 `is_read=false` 的全部行再回表过滤 `recipient_id` —— **在这个服务的真实查询形态下，RAG 的版本是更优的**，回改会造成计划回退 |
| **⚠️ 代价（必须知情）** | 两侧 `pg_indexes` / `\d` 的输出不同；任何**按索引定义做逐字节比对**的 DDL 对拍工具（含 `tools/difftest/check-ddl-static.py`）在这一条上**必然为红**，属预期差异，不是回归 |
| **⚠️ 不得做的事** | 不要为了让 DDL 对拍变绿而把它改回单列。若日后 `ListNotifications` 的查询形态变化（`recipient_id` 不再总是等值过滤），本条应**重新评估**，而不是当作既成决策继承下去 |

## 本域**没有**的签字偏离（避免后人误补）

| 曾被怀疑的点 | 实测结论 |
|---|---|
| proto `package` 归一（`comment` / `like` 的 `SD-04` / `-05` 同款） | ❌ **不存在**。两侧 proto package 都是 `message`；Go 生成物的语言包名 `pb` 来自 `option go_package`，不是 proto package。见 [`DRIFT/message/proto.md`](../../DRIFT/message/proto.md) §2 |
| `Content-Type` 缺 `charset`（`DRIFT-MSGAPI-010`） | ❌ 未签字 —— 属 **DRIFT**（要修），且是 `OBSERVABLE DIFFERENCE` / LOW。见 `DRIFT/message/api.md` |
| 时间列 `timestamptz` → `timestamp`（`DRIFT-MSG-V-020`） | ❌ 未签字 —— 属 **DRIFT**，且**后果是双向的**（见 `DRIFT/message/rpc.md` 该节 ⚠️ 段：Go 的 AutoMigrate 会反向 `ALTER` 回来）。不要当偏离放行 |