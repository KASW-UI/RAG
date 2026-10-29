# `follow-proto` 有意偏离（已签字）

> **本域暂无独立登记的签字偏离**（核对日期 2026-10-29 · 已核 `grep -cE 'SD-[0-9-]+' docs/Intentional_Deviations/follow/proto.md` ⇒ **0**；
> 且 `grep -rn 'follow' docs/Intentional_Deviations/ --include='*.md' | grep -i 'SD-'` ⇒ **follow 域只命中 `hot/api.md` 里的一条旁注**，不是 follow 域的条目）。
>
> ## ⚠️ **但 follow 域确实涉及一条签字偏离** —— 它的签字档**不在本目录**
>
> | 项 | 值 |
> |---|---|
> | 签字编号 | **`SD-2026-10-25-01`**（**唯一别名 `SD-2026-10-25-01@global`** —— 全仓**无撞号**） |
> | 签字档位置 | [`docs/Intentional_Deviations/global/common-tails.md`](../global/common-tails.md) §`SD-2026-10-25-01`（**`global` 收口域 · G4 写权** ⇒ 本 lane **只读、不改**） |
> | 对应的 DRIFT 条目 | **`DRIFT-FOLLOW-008`**（`MaxConns` 闸门作用域）—— 与本组件**无关**（proto 层不涉及 HTTP 闸门） |
>
> ## ✅ `follow-proto` 自身的核对结论（核对日期 2026-10-29）
>
> `docs/DRIFT/follow/proto.md` 判定 `.proto` **文件本身行为层零差异**（**唯一差异在 option 块**：Go `option go_package` vs RAG `option java_package` + `java_multiple_files` ⇒ 属 AGENTS.md §3.4 允许的**表达层**）
> ⇒ ⇒ **没有需要签字的差异** ⇒ **本档为空是正确状态，不是漏登记**。
>
> ⚠️ **2026-10-29 真实性复核（W-B lane）**：本档的两条断言**均仍成立** ——
> **①「本域暂无独立登记的签字偏离」**：本轮实测 `grep -rn 'SD-[0-9]' docs/Intentional_Deviations/follow/` ⇒ **无 follow 自有签字条目** ⇒ **本档为空是正确状态，不是漏登记**。
> **②「follow 域确实涉及一条签字偏离、其签字档不在本目录」**：签字档 `docs/Intentional_Deviations/global/common-tails.md` §`SD-2026-10-25-01` **存在**（「对应的 DRIFT 条目」栏 = **T-2 = `DRIFT-FOLLOW-008`**）⇒ **指向一致**；⚠️ 该闸门属 HTTP 层 ⇒ **与 proto 层无关**（本档已如此声明）。
> **「proto 文件本身零差异」这条结论本轮已复核**：`docs/DRIFT/follow/proto.md` 判定差异**只在 option 块**（Go `option go_package` vs RAG `option java_package` + `java_multiple_files` ⇒ 属 AGENTS.md §3.4 允许的**表达层**）⇒ ⇒ **没有需要签字的差异** ⇒ **本档为空是正确状态**。
> ⚠️ 共享索引 `docs/Intentional_Deviations/_INDEX.md` 的计数**由主线统一更新**，本 lane 不写。
>
> ⚠️ **同类前车之鉴**：别的域（`comment` / `like`）的 proto `package` 被**归一**（Go 遗留名 `pb` → 域名）⇒ **那才需要签字**（`SD-2026-10-23-04` / `SD-2026-10-23-05`）。
> ⇒ **若将来 follow 的 `package` 被归一** ⇒ **必须在本文件登记一条签字偏离**（⇒ 同时会打断 Go↔RAG 的 gRPC 方法路径 ⇒ 跨栈互调失效）。
>
> ⇒ **引用签字偏离时必须同时给出「文件 + 域」**（`SD-` 编号**跨文件复用** ⇒ 编号本身**不可寻址**）。

**本域 DRIFT 明细面** ⇒ [`docs/DRIFT/follow/proto.md`](../../DRIFT/follow/proto.md)
