# 同源盲区判定 · 「不同编号 + 同域 + 同一主题」（2026-10-30 · 第 4 轮收口后）

> **这是同源机制的第二个盲区**：第 3 轮的同源判定只覆盖「**同编号跨域**」
> ⇒ **漏掉「不同编号、同域、同一件事」** ⇒ 后果：同一件事被**判两次**
> ⇒ 结论可能不一致（优先级/归属打架）＋ 一份读到旧快照 ⇒ **虚增缺口**。
>
> **机械初筛（可复算）**：U5-O 叠加层各条**依据里的 `文件:行号` 集合**
> ⇒ 同域内「交集 ≥3 且 Jaccard ≥0.45」⇒ 候选 ⇒ **须人工判定**（机械判据只是初筛）。
> ⇒ 实测命中 **9 对**（脚本见收口工作区）⇒ **本文件逐对判定完毕**。
>
> ⚠️ **判据纪律**：「机械判据命中」**不等于**「是同一件事」——
> 本文件每一条都**打开了台账原文**才判；`PIT-GLOBAL-023` 就是**依据高度重叠但不是同一件事**的反例。

| 编号 | 域 | 判定 | 依据（一句话，说明凭什么） | 本体位置 |
|---|---|---|---|---|
| `V-002` | `users` | 同一件事 | `DRIFT/users/user/rpc.md:995`（销账表 `V-002` 行）讲「`status IS NULL` 的读法：Go 读成 **0**，RAG NOT NULL ⇒ 读不到 NULL ⇒ **也是 0** ⇒ **行为层零差异** ⇒ 维持 NOT NULL ⇒ 本条无落码动作」；`DRIFT/users/_shared.md:965`（`ADJ-01` 标题）讲「`users_user.status` 的 NOT NULL 与 admin-rpc 的 `status = 0L` **必须同时改**」⇒ **同一条 DDL 差异**（`V-002` = user 半边 · `ADJ-01` = 总条）⇒ 现象、结论、落点（`service/users/users-common/src/main/resources/db/01_create_user.sql:39` 的 `status BIGINT NOT NULL DEFAULT 0` vs Go `data_model.go:11` 的 `column:status;default:0` 无 `not null`）**三样全同** ⇒ 不是撞号 | `DRIFT/users/_shared.md:965` |
| `DRIFT-ARPC-008` | `users` | 同一件事 | `DRIFT/users/admin/rpc.md:1302`（§3 标题「🔴 DRIFT-ARPC-008 —— `status IS NULL` 的读法：Go 读成 **0**，不是『读不出来』」）与 `DRIFT/users/user/rpc.md:995`（`V-002`）**讲同一件事的 admin 半边**（Go `service/user/admin/rpc/internal/model/data_model.go:41` 与 user 半边 `:11` 同款 tag ⇒ 同一条 DDL 差异）⇒ 而 `DRIFT/users/_shared.md:965`（`ADJ-01`）正是把**两个半边绑在一起**的总条（「必须同时改」）⇒ 三者是一件事的三个登记面 | `DRIFT/users/_shared.md:965` |
| `SD-2026-10-25-CAND-17` | `users` | 同一件事 | `Intentional_Deviations/users/_shared.md:110` 讲 `users` 域 `status` 列的 **NOT NULL vs Go 可空**（Go `data_model.go:11`／`:41` 同款 tag ⇒ GORM 建出可空列但恒写 0）⇒ 与 `DRIFT/users/_shared.md:965`（`ADJ-01`）**同一条差异**；差异在 DRIFT 侧定级、本条是**待签字候选面**（登记口径互补 ⇒ 不是撞号） | `DRIFT/users/_shared.md:965` |
| `ADJ-01` | `users` | 同一件事 | 本体面（`DRIFT/users/_shared.md:965`）与 user 半边 `V-002`（`DRIFT/users/user/rpc.md:995`）、admin 半边 `DRIFT-ARPC-008`（`DRIFT/users/admin/rpc.md:1302`）、待签字候选 `SD-2026-10-25-CAND-17`（`Intentional_Deviations/users/_shared.md:110`）**同指 `users_user.status` 的 NOT NULL vs Go 可空**这一件事 ⇒ 本条是**总条**（标题自己写明「user 与 admin **必须同时改**」）⇒ 四者合并成一条 | `DRIFT/users/user/rpc.md:995` |
| `SD-2026-10-25-CAND-15` | `global` | 同一件事 | `Intentional_Deviations/global/common-tails.md:510` 讲「Go `rest/router/patrouter.go:77-82` 的 405 出口在 **router 层** ⇒ 不经 TraceHandler ⇒ **无** `Traceparent`；RAG `GoStyleTraceparentFilter.java:282` 在 filter 链注入 ⇒ 405 出口**已带** `Traceparent`」⇒ 与 `DRIFT/global/common-tails.md:519`（`T-12`）**同一条行为差异**（RAG 多带一个 `Traceparent`，**只**影响「方法不匹配」这一个出口）⇒ 一个在 DRIFT 侧登记差异、一个在 IntDev 侧登记待签字候选 ⇒ **天然该合并**（否则同一件事被派两次单） | `DRIFT/global/common-tails.md:519` |
| `T-12` | `global` | 同一件事 | 本体面（`DRIFT/global/common-tails.md:519`）与待签字候选 `SD-2026-10-25-CAND-15`（`Intentional_Deviations/global/common-tails.md:510`）**同指 Go 405 不经 TraceHandler ⇒ 无 `Traceparent`** 这一件事 ⇒ 两条的 U5-O 结论**完全一致**（D · P3 · 阻断否）⇒ 合并**零冲突** | `Intentional_Deviations/global/common-tails.md:510` |
| `PIT-GLOBAL-023` | `global` | **不同的事** | ⚠️ **依据高度重叠但不是同一件事** —— 它讲的是**方法论坑位**：「『这个出口在 XX 层 ⇒ 不经过 X』是**推断**；判定必须落到**注册点**（`rest/engine.go:113-115`）才立得住」⇒ 它的**指向**是 T-12 那条差异，但**它自身不是那条差异**；其**依据原文自己写明**「它指向的行为差异在 **DRIFT 侧**定级（`T-12` ⇒ `SD-2026-10-25-CAND-15`，见本表）⇒ **本条只承担『防』的角色**」⇒ ⇒ **明文自证不是同一件事**。⚠️ 它的 **P2** 是「阶段一验证闭环：凡写『层』必落注册点」这个**坑位**的严重程度，与差异本体的 **P3** **不同口径** ⇒ ⇒ **不是优先级打架，是两件事的两种严重程度** | `DRIFT/global/common-tails.md:519` |
| `G4-7` | `article` | **不同的事** | `DRIFT/article/vs-common.md:281` 讲「`tools/difftest/flyway-concurrent-verify.py:48-51` 的 `KNOWN_BLOCKED` 只登记 `favorite` ⇒ article 该不该补 ⇒ **结论是不该做**（『该模块已不是阻塞面，补账动作不应做』）」⇒ 落点 = **守门脚本**；`DRIFT/article/vs-common.md:278`（`G4-5`）讲「`AGENTS.md:139` §3.5 表的『共享 Flyway 账本』行**至今仍写**『⚠️ `favorite-rpc` 例外（缺 `baseline-version: 0`）』⇒ **共享面文档过期** ⇒ 要改 `AGENTS.md`」⇒ 落点 = **共享文档** ⇒ **落点不同（脚本 vs 文档）· 处置动作不同（改文档 vs 不做）⇒ 不是同一件事** | `DRIFT/article/vs-common.md:278` |
| `L4-03` | `global` | **不同的事** | `DRIFT/global/lanes/L4-flyway.md:174` 讲「Flyway 源碼 `PostgreSQLConnection.java:105` 的 advisory lock 机制 ⇒ **正面记录** ⇒ 无待实现内容」⇒ 落点 = **机制记录**；`DRIFT/global/lanes/L4-flyway.md:173`（`L4-02`）讲「**改表名属跨域迁移 ⇒ 经分析确认不实现**（当前 0 撞号 ＋ 守门在撞上后才报）」⇒ 落点 = **「要不要改表名」的决策** ⇒ **结论动作不同（正面记录 vs 决定不做）⇒ 不是同一件事** | `DRIFT/global/lanes/L4-flyway.md:173` |

⇒ **判定汇总**：**同一件事 6 个节点 / 2 个同源组**（users 组 4 个 · global 组 2 个）· **不同的事 3 个节点**。
⇒ 合并的 2 组**组内 U5-O 结论完全一致**（D · P3 · 阻断否）⇒ **合并零冲突**、不改动任何派单结论。
⇒ 合并后派生层「真缺口 C/D 未排期」会**从 91 降到 87**（同一件事原本被数了 5 次 ⇒ 合并成 2 次）
⇒ ⇒ **这是修正不是退化**（消除重复计数）。
