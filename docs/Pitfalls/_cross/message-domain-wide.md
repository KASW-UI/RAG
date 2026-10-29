# `_cross/message-domain-wide.md` —— `message` 域**全域**（跨组件）踩坑（跨域档）

> **本文件此前是 0 字节占位**（2026-10-29 台账对齐轮补上）。**不编造坑位**。

## 用途

登记**跨越 message 域多个组件**（`message-rpc` + `message-api` + `message-common` + `message-proto`）
才能看出来的踩坑 —— 即**单组件视角看不到**的那一类。

## 已核范围（核对日期 2026-10-29）

- `docs/Pitfalls/message/**` 的条目已按域台账登记（**逐条带状态 + 核对日期**）
- **其中属于「跨组件全域」性质的**：已随域台账登记（落点 `docs/Pitfalls/message/rpc.md` 等）
- ⇒ **本文件没有需要另行登记的独立条目**（避免与域台账**重复登记** ⇒ 那会让同一坑位出现两个状态源）

## 内容在哪（指针）

| 内容 | 位置 |
|---|---|
| **message 域的逐条坑位** | [`../message/`](../../Pitfalls/message/) |
| **message 域的全域语义验证留档**（10 项漂移 + 3 条新挖出的 P0 · 2026-10-22） | [`../../DRIFT/_cross/message-domain-wide.md`](../../DRIFT/_cross/message-domain-wide.md) |
| **message 域的漂移台账本体** | [`../../DRIFT/message/`](../../DRIFT/message/) |

## 状态

- **状态**：⚪ **无独立登记条目**（内容归 `docs/Pitfalls/message/`）
- **核对日期**：**2026-10-29**
- **是否影响当前轮次**：**否** —— 跨组件纪律由 `docs/Pitfalls/message/` 承担（**不重复登记**）
