# `follow-rpc` 有意偏离（已签字）

> **本域暂无独立登记的签字偏离**（核对日期 2026-10-29 · 已核 `grep -cE 'SD-[0-9-]+' docs/Intentional_Deviations/follow/rpc.md` ⇒ **0**；
> 且 `grep -rn 'follow' docs/Intentional_Deviations/ --include='*.md' | grep -i 'SD-'` ⇒ **follow 域只命中 `hot/api.md` 里的一条旁注**，不是 follow 域的条目）。
>
> ## ⚠️ **但 follow 域确实涉及一条签字偏离** —— 它的签字档**不在本目录**
>
> | 项 | 值 |
> |---|---|
> | 签字编号 | **`SD-2026-10-25-01`**（**唯一别名 `SD-2026-10-25-01@global`** —— 全仓**无撞号**） |
> | 签字档位置 | [`docs/Intentional_Deviations/global/common-tails.md`](../global/common-tails.md) §`SD-2026-10-25-01`（**`global` 收口域 · G4 写权** ⇒ 本 lane **只读、不改**） |
> | 对应的 DRIFT 条目 | **`DRIFT-FOLLOW-008`**（`MaxConns` 闸门：Go **每路由一个** vs RAG **全局一个**）—— 落点在共享层 `service/common/protection/GoStyleMaxConnsFilter.java` ⇒ **10 个 api 模块全部适用**（`follow-api` 是其中之一；**`follow-rpc` 是纯 gRPC 进程 ⇒ 该闸门不适用**） |
> | 签字决定 | **保留 RAG 的全局闸门**（不改成 per-route）· 2026-10-25 用户拍板 |
>
> ⇒ **引用签字偏离时必须同时给出「文件 + 域」**（`SD-` 编号**跨文件复用** ⇒ 编号本身**不可寻址**）。
>
> ⚠️ **若将来 follow-rpc 出现「明知与 Go 不同、经用户签字批准」的差异** ⇒ **在本文件登记一条 `SD-2026-10-29-FOLLOW-NN`**，
> 并同步更新 [`docs/DRIFT/follow/rpc.md`](../../DRIFT/follow/rpc.md) ＋ [`docs/Intentional_Deviations/_INDEX.md`](../_INDEX.md)（**共享索引由主线更新**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/follow/rpc.md`](../../DRIFT/follow/rpc.md)
