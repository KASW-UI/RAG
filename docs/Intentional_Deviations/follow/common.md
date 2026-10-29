# `follow-common` 有意偏离（已签字）

> **本域暂无独立登记的签字偏离**（核对日期 2026-10-29 · 已核 `grep -cE 'SD-[0-9-]+' docs/Intentional_Deviations/follow/common.md` ⇒ **0**；
> 且 `grep -rn 'follow' docs/Intentional_Deviations/ --include='*.md' | grep -i 'SD-'` ⇒ **follow 域只命中 `hot/api.md` 里的一条旁注**，不是 follow 域的条目）。
>
> ## ⚠️ **但 follow 域确实涉及一条签字偏离** —— 它的签字档**不在本目录**
>
> | 项 | 值 |
> |---|---|
> | 签字编号 | **`SD-2026-10-25-01`**（**唯一别名 `SD-2026-10-25-01@global`** —— 全仓**无撞号**） |
> | 签字档位置 | [`docs/Intentional_Deviations/global/common-tails.md`](../global/common-tails.md) §`SD-2026-10-25-01`（**`global` 收口域 · G4 写权** ⇒ 本 lane **只读、不改**） |
> | 对应的 DRIFT 条目 | **`DRIFT-FOLLOW-008`**（`MaxConns` 闸门作用域） |
> | 与本组件的关系 | ⚠️ **间接** —— 该闸门落点在共享层 `service/common/protection/GoStyleMaxConnsFilter.java`，**不是** `follow-common`（`follow-common` = Go `service/follow/common/errmsg.go` 的错误码契约）⇒ ⇒ **`follow-common` 本身零偏离** |
>
> ## ✅ `follow-common` 自身的核对结论（核对日期 2026-10-29）
>
> `docs/DRIFT/follow/common.md` 判定 **`SEMANTICALLY EQUIVALENT` —— 零漂移**（16 个业务码 + 16 条文案 + 三条错误转换路径逐条一致 ＋ **37 条契约测试**钉死）
> ⇒ ⇒ **没有任何「明知与 Go 不同」的差异需要签字** ⇒ **本档为空是正确状态，不是漏登记**。
>
> ⇒ **引用签字偏离时必须同时给出「文件 + 域」**（`SD-` 编号**跨文件复用** ⇒ 编号本身**不可寻址**）。
>
> ⚠️ **2026-10-29 真实性复核（W-B lane）**：本档的两条断言**均仍成立** ——
> **①「本域暂无独立登记的签字偏离」**：本轮实测 `grep -rn 'SD-[0-9]' docs/Intentional_Deviations/follow/` ⇒ **无 follow 自有签字条目** ⇒ **本档为空是正确状态，不是漏登记**。
> **②「follow 域确实涉及一条签字偏离、其签字档不在本目录」**：签字档 `docs/Intentional_Deviations/global/common-tails.md` §`SD-2026-10-25-01` **存在**（「对应的 DRIFT 条目」栏 = **T-2 = `DRIFT-FOLLOW-008`**）⇒ **指向一致**；⚠️ 该闸门落点是共享层 `service/common/protection/GoStyleMaxConnsFilter.java` ⇒ **与 `follow-common`（= Go `service/follow/common/errmsg.go` 的错误码契约）无关** ⇒ **`follow-common` 本身零偏离**这条结论**仍成立**。
> **零漂移结论的证据本轮已独立复核**：`docs/DRIFT/follow/common.md` 判定 `SEMANTICALLY EQUIVALENT`（16 码 + 16 文案 + 三条错误转换路径）· **本轮实测 `service/follow/follow-common` 的 surefire XML 求和 = `tests=37 failures=0 errors=0 skipped=0`**（3 个契约测试类：`FollowErrorCodeContractTest` / `FollowErrorMessagesContractTest` / `FollowGrpcErrorContractTest`）⇒ ⇒ **「37 条契约测试」这个数字属实且全绿** ⇒ **无需登记偏离**。
> ⚠️ 共享索引 `docs/Intentional_Deviations/_INDEX.md` 的计数**由主线统一更新**，本 lane 不写。
>
> ⚠️ **若将来 follow-common 出现「明知与 Go 不同、经用户签字批准」的差异** ⇒ **在本文件登记一条 `SD-2026-10-29-FOLLOW-NN`**，
> 并同步更新 [`docs/DRIFT/follow/common.md`](../../DRIFT/follow/common.md) ＋ [`docs/Intentional_Deviations/_INDEX.md`](../_INDEX.md)（**共享索引由主线更新**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/follow/common.md`](../../DRIFT/follow/common.md)
