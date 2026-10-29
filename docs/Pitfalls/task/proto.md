# `task-proto` 踩坑档（为什么会踩）

> **本域暂无已登记坑位**（核对日期 2026-10-29 · 已核 `grep -cE 'PITFALL-TASK|PIT-TASK' docs/DRIFT/task/proto.md` ⇒ **0**；
> 且 `grep -cE '^## PIT-' docs/Pitfalls/task/rpc.md` ⇒ **34** 条全部落在 `rpc.md` 一个组件上 ⇒ **本文件无独立条目**）。
>
> ⚠️ **2026-10-29 真实性复核（W-B lane）**：本档的断言**仍成立** ——
> **①「本域无独立条目、34 条全在 `rpc.md`」**：本轮实测 `grep -cE '^## PIT-' docs/Pitfalls/task/rpc.md` ⇒ **34** ⇒ 一致。
> **②「`.proto` 文件本身两侧逐行一致（行为层零差异）＋生成代码真产出」**：本轮核 `docs/DRIFT/task/proto.md` 的结论与其依据（option 块差异属 AGENTS.md §3.4 允许的**表达层**）**未发现被推翻的证据**；⚠️ **「复制忠实 ≠ 消费方式已验」这条效力边界本轮维持**（本 lane 未对 task 的 gRPC 消费方式做**新的**字节级活体对拍 ⇒ 该维度**沿用**原结论，不重复主张）。
> ⇒ **本档为空是正确状态，不是漏登记**。

> ⚠️ **口径提醒（AGENTS.md §3.4 的效力边界）** —— `docs/DRIFT/task/proto.md` 记录的结论是
> 「`.proto` **文件本身**两侧逐行一致（行为层零差异）」＋「生成代码真产出（7 个 java + `TaskServiceGrpc`）」＋
> 「**消费方式**本轮首次核对、未发现差异」⇒ **⚠️ 「复制忠实」≠「消费方式已验」**。
> ⇒ 若将来出现「生成代码用错了字段号 / `optional` presence 语义不同 / 字段号硬编码」这类问题，**它才是本档的坑位** ⇒ **在本文件登记一条 `PIT-TASK-NN`**，
> 并同步更新 [`docs/DRIFT/task/proto.md`](../../DRIFT/task/proto.md)。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/task/proto.md`](../../DRIFT/task/proto.md)
