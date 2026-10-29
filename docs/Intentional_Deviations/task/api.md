# `task-api` 有意偏离（已签字）

> **本域暂无已登记的签字偏离**（核对日期 2026-10-29 · 已核 `grep -cE 'SD-2026-10-[0-9]{2}-TASK-[0-9]{2}' docs/Intentional_Deviations/task/api.md` ⇒ **0**；
> 且 `grep -rn 'SD-2026-10-[0-9]{2}-TASK' docs/Intentional_Deviations/task/` ⇒ **全部命中都在 `rpc.md`** ⇒ 本文件无条目）。
>
> **核对依据**：
> - `docs/DRIFT/task/api.md:25-27` 的「已修 / 对拍新发现 / 未修」三行里**没有任何「已签字偏离」条目**
>   （未修项只有 **1 条全域共性** `DRIFT-TASK-API-004`「`Content-Type` 的 charset 与空白格式」⇒ 按 §三·三 **不在本域单独修** ⇒ **它不是签字偏离**）。
> - `docs/DRIFT/task/rpc.md:25` 记的 **2 条**已签字偏离（`SD-2026-10-23-TASK-01` · `SD-2026-10-24-TASK-01`）**都在 `rpc.md`**。
> - `docs/DRIFT/task/proto.md:41` 明写「与 `comment` 域不同，**task 的 `package` 两侧都是 `task`，未归一** ⇒ 不存在 `comment` 那样的签字偏离」⇒ **proto 侧也没有**。
>
> ⚠️ **若将来 task-api 出现「明知与 Go 不同、经用户签字批准」的差异** ⇒ **在本文件登记一条 `SD-2026-10-29-TASK-NN`**，
> 并同步更新 [`docs/DRIFT/task/api.md`](../../DRIFT/task/api.md) ＋ [`docs/Intentional_Deviations/_INDEX.md`](../_INDEX.md)（**共享索引由主线更新**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/task/api.md`](../../DRIFT/task/api.md)
