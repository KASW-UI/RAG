# `follow-api` 有意偏离（已签字）

> **本域暂无独立登记的签字偏离**（核对日期 2026-10-29 · 已核 `grep -cE 'SD-[0-9-]+' docs/Intentional_Deviations/follow/api.md` ⇒ **0**；
> 且 `grep -rn 'follow' docs/Intentional_Deviations/ --include='*.md' | grep -i 'SD-'` ⇒ **follow 域只命中 `hot/api.md` 里的一条**「`follow-api` **未配** `management.metrics.tags.application`」的**旁注**（那条是 hot 域自己的偏离，不是 follow 域的））。
>
> ## ⚠️ **但 follow 域确实涉及一条签字偏离** —— 它的签字档**不在本目录**
>
> | 项 | 值 |
> |---|---|
> | 签字编号 | **`SD-2026-10-25-01`**（**唯一别名 `SD-2026-10-25-01@global`** —— 全仓**无撞号**） |
> | 签字档位置 | [`docs/Intentional_Deviations/global/common-tails.md`](../global/common-tails.md) §`SD-2026-10-25-01`（**`global` 收口域 · G4 写权** ⇒ 本 lane **只读、不改**） |
> | 对应的 DRIFT 条目 | **`DRIFT-FOLLOW-008`**（`MaxConns` 闸门：Go **每路由一个** vs RAG **全局一个**） |
> | 签字决定 | **保留 RAG 的全局闸门**（不改成 per-route）· 2026-10-25 用户拍板 |
> | follow 域侧状态 | ✅ **已移出 DRIFT 侧**（决策见签字档 ⇒ 不修）—— 见 [`docs/DRIFT/follow/api.md:503`](../../DRIFT/follow/api.md) §`DRIFT-FOLLOW-008` |
>
> ⇒ **引用签字偏离时必须同时给出「文件 + 域」**（AGENTS.md 的编号纪律：`SD-` 编号**跨文件复用** ⇒ 编号本身**不可寻址**）。
>
> ⚠️ **2026-10-29 真实性复核（W-B lane）**：本档的两条断言**均仍成立** ——
> **①「本域暂无独立登记的签字偏离」**：本轮实测 `grep -rn 'SD-[0-9]' docs/Intentional_Deviations/follow/` ⇒ 命中**全部**是**指向 `SD-2026-10-25-01@global` 的引用**与**「将来登记用哪个号」的说明 ⇒ **无 follow 自有签字条目** ⇒ **本档为空是正确状态，不是漏登记**。
> **②「follow 域确实涉及一条签字偏离、其签字档不在本目录」**：签字档 `docs/Intentional_Deviations/global/common-tails.md` §`SD-2026-10-25-01` **存在**，其「对应的 DRIFT 条目」栏明写 = **T-2 = `DRIFT-FOLLOW-008`** ⇒ **指向一致**。
> **被签字描述的那个行为还在两侧吗？** ✅ **仍在** ⇒ **签字仍成立（决策不是缺陷）**：
> RAG `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:102,106` = 全进程**一个** `Semaphore`（`maxConns` 默认 10000）·
> Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go:20` = `latch := syncx.NewLimit(n)`（**每路由一个**）·
> Go `go-zero@v1.10.2/rest/engine.go:145-146` = `if ng.conf.Middlewares.MaxConns { chn = chn.Append(handler.MaxConnsHandler(ng.conf.MaxConns)) }`（**在 `bindRoute` 的每条路由链里** ⇒ per-route）
> ⇒ ⇒ **两侧差异未消失 ⇒ 决策仍然有效** ⇒ **不重签、不派单**（共享层写权归 G4，本 lane 只读）。
> ⚠️ 共享索引 `docs/Intentional_Deviations/_INDEX.md` 的计数**由主线统一更新**，本 lane 不写。
>
> ⚠️ **若将来 follow-api 出现「明知与 Go 不同、经用户签字批准」的差异** ⇒ **在本文件登记一条 `SD-2026-10-29-FOLLOW-NN`**，
> 并同步更新 [`docs/DRIFT/follow/api.md`](../../DRIFT/follow/api.md) ＋ [`docs/Intentional_Deviations/_INDEX.md`](../_INDEX.md)（**共享索引由主线更新**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/follow/api.md`](../../DRIFT/follow/api.md)
