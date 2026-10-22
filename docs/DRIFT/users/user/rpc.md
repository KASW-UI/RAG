# DRIFT 台账 · `service/users/user/user-rpc`

> **本文件是导航页，不是内容页。**
> AGENTS.md 里与 `user-rpc` 相关的漂移/修复记录**没有独立成节**——它们散布在几段
> 以 `service/users` / `tools/difftest` 为标题的叙述里，那几段同时覆盖 4 个子模块 + `users-common`。
> 为了**不割裂叙事、不改一个字**，这些段落整体搬到了 [`../_shared.md`](../_shared.md)。
> 本页给出精确行号索引。

## 搬移到 `users/_shared.md` 的相关段落

| 在 `_shared.md` 的行号 | 来源 | 与 `user-rpc` 的关系 |
|---|---|---|
| **L479 起** | AGENTS.md §6.2.5 | **Flyway 迁移并发** —— 把 Go 的 `user-rpc` 与 `admin-rpc` **同时**对着全新空库启动的实测，暴露 DDL 竞态；`SchemaInitializer.applyAll` 取 PG 会话级 advisory lock。**这是 `user-rpc` 至今最重的一条运行时缺陷记录。** |
| **L561 起** | AGENTS.md §6.2.6 | 证据边界台账 E-1~E-6。其中 **E-2 / E-4 / E-5** 的 `user-rpc` 项、`E-5` 的 admin-rpc 并发与回滚证据。 |

## 也需一并读的相邻台账

- [`../../message/rpc.md`](../../message/rpc.md) —— **§6.2.4② `NotificationEntity.isRead` 运行期致命缺陷**原挂在 `service/users` 的章节下，
  但 `trimPreview` / `markNotificationRead` 实为 **message-rpc**。搬移时已按真归属改判。
- [`../../common/deadline.md`](../../common/deadline.md) —— deadline 透传的接入点含 `LikeRpcClient` / `MessageRpcClient`，**不含** user-rpc。

## 本模块自身**从未**单独建立过的记录（缺口）

- `service/users/user/user/rpc` ↔ Go `service/user/user/rpc` 的**逐字段 proto 对照**：无独立留档，
  证据散落在 `users/_shared.md` 的 §6.2.5 与 §6.2.6。
- ⚠️ `AvatarTransactionWiringTest`（5 条，含 2 条真触发 rollback）证明 `@Transactional` 真生效，
  但 §6.2.6 自己声明 **「无真实 PG，故不证明 jsonb `CAST` 等 PG 行为」**——该缺口后由
  `UserRpcContainerWiringTest`（真 Testcontainers PG，12 条）关闭，详见 `users/_shared.md`。
- 本模块的 `HUMAN_ONLY.MD` **不存在**（全仓 4 个 HUMAN_ONLY.MD 全在 users 域，但不含 user-rpc 之外的域）。