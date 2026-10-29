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
> ⚠️ **2026-10-29 真实性复核（W-B lane）**：本档的两条断言**均仍成立** ——
> **①「本域暂无独立登记的签字偏离」**：本轮实测 `grep -rn 'SD-[0-9]' docs/Intentional_Deviations/follow/` ⇒ **无 follow 自有签字条目** ⇒ **本档为空是正确状态，不是漏登记**。
> **②「follow 域确实涉及一条签字偏离、其签字档不在本目录」**：签字档 `docs/Intentional_Deviations/global/common-tails.md` §`SD-2026-10-25-01` **存在**（「对应的 DRIFT 条目」栏 = **T-2 = `DRIFT-FOLLOW-008`**）⇒ **指向一致**。
> ⚠️ **「`follow-rpc` 是纯 gRPC 进程 ⇒ 该闸门不适用」这条前提本轮已复核**：RAG `service/follow/follow-rpc/pom.xml:216` = `spring-boot-starter-web`（2026-10-29 补，起因 = 指标出口）⇒ ⇒ **follow-rpc 现在**有** servlet 容器 ⇒ **该闸门在 follow-rpc 上其实已具备适用面**。
> ⇒ **但这不改变签字的有效性**：签字的对象是「**RAG 的全局闸门 vs Go 的 per-route 闸门**」这一**差异本身**——
> RAG 侧 `service/common/src/main/java/com/rag/common/protection/GoStyleMaxConnsFilter.java:102,106` = 全进程**一个** `Semaphore`（**仍在**）·
> Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go:20` = `latch := syncx.NewLimit(n)`（**每路由一个**）·
> Go `go-zero@v1.10.2/rest/engine.go:145-146` = 在 `bindRoute` 的每条路由链里各建一次 ⇒ ⇒ **两侧差异仍在 ⇒ 签字仍成立（决策不是缺陷）**。
> ⇒ **不重签、不派单**（共享层写权归 G4，本 lane 只读）。⚠️ 共享索引 `docs/Intentional_Deviations/_INDEX.md` 的计数**由主线统一更新**，本 lane 不写。
>
> ⚠️ **若将来 follow-rpc 出现「明知与 Go 不同、经用户签字批准」的差异** ⇒ **在本文件登记一条 `SD-2026-10-29-FOLLOW-NN`**，
> 并同步更新 [`docs/DRIFT/follow/rpc.md`](../../DRIFT/follow/rpc.md) ＋ [`docs/Intentional_Deviations/_INDEX.md`](../_INDEX.md)（**共享索引由主线更新**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/follow/rpc.md`](../../DRIFT/follow/rpc.md)
