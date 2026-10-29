# `task-proto` 踩坑档（为什么会踩）

> **本域暂无已登记坑位**（核对日期 2026-10-29 · 已核 `grep -cE 'PITFALL-TASK|PIT-TASK' docs/DRIFT/task/proto.md` ⇒ **0**；
> 且 `grep -cE '^## PIT-' docs/Pitfalls/task/rpc.md` ⇒ **34** 条全部落在 `rpc.md` 一个组件上 ⇒ **本文件无独立条目**）。
>
> ⚠️ **口径提醒（AGENTS.md §3.4 的效力边界）** —— `docs/DRIFT/task/proto.md` 记录的结论是
> 「`.proto` **文件本身**两侧逐行一致（行为层零差异）」＋「生成代码真产出（7 个 java + `TaskServiceGrpc`）」＋
> 「**消费方式**本轮首次核对、未发现差异」⇒ **⚠️ 「复制忠实」≠「消费方式已验」**。
> ⇒ 若将来出现「生成代码用错了字段号 / `optional` presence 语义不同 / 字段号硬编码」这类问题，**它才是本档的坑位** ⇒ **在本文件登记一条 `PIT-TASK-NN`**，
> 并同步更新 [`docs/DRIFT/task/proto.md`](../../DRIFT/task/proto.md)。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/task/proto.md`](../../DRIFT/task/proto.md)
