# `task-proto` 有意偏离（已签字）

> **本域暂无已登记的签字偏离**（核对日期 2026-10-29 · 已核 `grep -cE 'SD-2026-10-[0-9]{2}-TASK-[0-9]{2}' docs/Intentional_Deviations/task/proto.md` ⇒ **0**；
> 且 `grep -rn 'SD-2026-10-[0-9]{2}-TASK' docs/Intentional_Deviations/task/` ⇒ **全部命中都在 `rpc.md`** ⇒ 本文件无条目）。
>
> **核对依据**：
> - 🔴 **本域最容易误判成偏离的一条已被明确排除**：其它域（`comment` / `like`）的 proto `package` 被**归一**（Go 遗留名 `pb` → 域名）⇒ 那才需要签字
>   （`SD-2026-10-23-04` / `SD-2026-10-23-05`）；
>   **task 域两侧 `package` 都是 `task`（未归一）⇒ 不存在这类偏离** —— 证据：`docs/DRIFT/task/proto.md:41`
>   （「与 `comment` 域不同，**task 的 `package` 两侧都是 `task`，未归一** ⇒ 不存在 `comment` 那样的签字偏离」）
>   ＋ `:39-40`（唯一差异**只在 option 块** ⇒ Go `option go_package = "."` vs RAG `option java_package` + `java_multiple_files = true` ⇒ 属 AGENTS.md §3.4 允许的**表达层**）。
> - `docs/DRIFT/task/proto.md:55` 同时留了前车之鉴：「`package` 一旦被归一（像 `comment` 域那样），两端就再也调不通了，且**编译期无任何提示**」
>   ⇒ ⇒ **若将来有人把 task 的 `package task` 改成 `task.proto`**，那**才会**产生一条需要签字的偏离 ⇒ **在本文件登记**。
>
> ⚠️ **若将来 task-proto 出现「明知与 Go 不同、经用户签字批准」的差异** ⇒ **在本文件登记一条 `SD-2026-10-29-TASK-NN`**，
> 并同步更新 [`docs/DRIFT/task/proto.md`](../../DRIFT/task/proto.md) ＋ [`docs/Intentional_Deviations/_INDEX.md`](../_INDEX.md)（**共享索引由主线更新**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/task/proto.md`](../../DRIFT/task/proto.md)
