
---

## DDL 实际差异清单（复核后远超 12 处）

> 从 AGENTS.md §〇 搬出。

⚠️ **2026-10-23 复核后实际差异数远超 12 处**：admin-rpc 7 处、user-rpc 4 个时间列
   `timestamptz↔timestamp`、like-rpc `like_domain_fact_outbox` 列类型不一致**且丢失 Go 侧 CHECK / 触发器 / 部分索引**、
   message-rpc 的 `V2` 迁移 `ALTER TABLE ADD CONSTRAINT` **无 `IF NOT EXISTS`**（在 GORM 建过表的库上会失败）。
