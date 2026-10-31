# 🆕 合并记录 · 2026-11-01 收官轮（G1~G4 四棵 lane 全部收官）

> **性质** = **合并收口执行者**的收官台账（`docs/DRIFT/_cross/**` = 合并收口执行者写权面）。
> **口径纪律**（本文件全部数字）—— ① 一律取**守门自己打印的实测值**，不取 lane 报来的数；
> ② **不采信转述** —— lane 的结论、回执里的状态快照都要自己重测（回执拆「内容」与「状态快照」分别采信）；
> ③ 凡「零命中」的读数 ⇒ 先证明命令本身成功；④ 判「某笔已落主干」唯一可靠判据 = `git merge-base --is-ancestor`；
> ⑤ **两点 diff ≠ 三点 diff**（两个不同的量）⇒ 判「某 lane 改了什么」一律用三点。

---

## §一 本轮合并清单（逐 lane · 实际带进来的 commit 集合）

> **合并信息一律按实际集合写**（含空合并笔）⇒ 合并后用 `git log <合并前 tip>..HEAD` 复核。

| # | lane | 合并 commit | 实际集合 | 内容面 |
|---|---|---|---|---|
| 1 | **CI** | `a937468b` | **1 笔**：`dd47da44` | `ci/DELIVERY.md`（docs-only · B17「推后」时点标注） |
| 2 | **G2** | `2daa6884` | **3 笔**：`1228ec12` · `15baabc2` · `53b23292` | like/message/task 三域：测试面 `import com.rag.common.*` → 全限定名（**37 条 import** · 108 处代码用点 · 6 处 `{@link}`）＋ 三份台账文末追加 |
| 3 | **G4** | `41a59b5e` / `fa6283ce` / `6ab4dcf1` / `f5ef5d88` | 逐次 **1~2 笔**（§4.19.63~§4.19.68 续记轮） | `docs/DRIFT/global/vs-common.md`（**纯台账**） |
| 4 | **G1** | `6c44f965` | **1 笔**：`90ad008a` | `docs/DRIFT/users/vs-common.md`（出向 stub 旧工具读数订正 + 三条纪律） |
| 5 | **G2** | `db24290b` | **4 笔**：`884af0e5` · `0a0ee9a8` + 2 笔同步合并 | 三份域台账口径订正（+42/−3） |
| 6 | **G4** | `8f288312` | **2 笔**：`82c9b6b1` + 1 笔同步合并 | `docs/DRIFT/global/vs-common.md`（§4.19.68） |
| 7 | **G1** | `719dbe93` | **4 笔**：`4ab7415e` · `267bfe1d` + 2 笔同步合并 | `docs/DRIFT/users/user/api.md` + `users/vs-common.md`（**净差 2 文件**） |
| 8 | **G4** | `983036f5` / `8c1dd6bb` | 各 **1 笔**（并发会话落的 §4.19.69 / 第九轮） | `docs/DRIFT/global/vs-common.md` |

⇒ **四棵 lane + CI 的全部内容笔均已落主干** ⇒ **主干相对 `upstream/do-my-own-gh` 领先 63 笔**（`c950e3ee` → 本轮 tip）。

### §1.1 ⚠️ 一棵分支**刻意不合并**：`align/g4-service-common`（ahead = **22**）

· **判定 = 作废分支 ⇒ 不合并**（派工单裁定 + G4 lane 自己的收官回执「零待落、可关闭」）。
· **内容级复核（本层实测）**：主干比它**多出 `service/common/**` 共 4,772 行 / −10**
  （`GoStyleOutboundDeadlineInterceptor.java` 的 **per-service cap** 支持 ＋ **4 个新守门测试类**）
  ⇒ ⇒ 合并 = **回退 per-service cap 支持 + 删掉守门测试类** ⇒ ⇒ **合即倒退**。
· **祖先级 vs 内容级是两个不同的量**（G4 lane 自己的登记亦已写明）：
  那 22 笔逐笔 `merge-base --is-ancestor` = **全否** ⇒ 但内容**已被以更新形态取代** ⇒ ⇒
  ⇒ **判「零待落」必须用内容级判据**；用祖先级会误判成「还有待办」。

---

## §二 lane 终态与核销

| lane | 分支 | 相对主干 | 终态 | 核销依据 |
|---|---|---|---|---|
| **G1** | `align/g1-users-follow` | **ahead 0** | ✅ **收官** | 2 批共 5 笔内容全部落主干（`6c44f965` · `719dbe93`）；`git log do-my-own-gh..分支` = 空 |
| **G2** | `align/g2-message-like-task` | **ahead 0** | ✅ **收官** | 3 批共 9 笔内容全部落主干（`2daa6884` · `db24290b` · `7f7f84ed`） |
| **G3** | `align/g3-article-comment-favorite-hot` | **ahead 0** | ✅ **收官** | 本轮零新增（此前 4 域全限定名重构 + U+FFFD 修复已在 `ddf98082`） |
| **G4** | `align/g4-ledger-close` | **ahead 0** | ✅ **收官** | 5 批全部落主干（§一 #3 #6 #8） |
| **G4** | `align/g4-service-common` | **ahead 22** | ⚫ **作废 · 不合并** | §1.1 |
| **CI** | `align/ci-concourse` | **ahead 0** | ✅ **收官** | 1 笔 docs-only 已合（`a937468b`） |

⇒ ⇒ **四棵 lane + CI 的内容面全部排空** ⇒ ⇒ **零待合**。

### §2.1 三份 lane 回执的核销（按派工单第 ⑧ 步要求）

| 回执 | 声称 | 本层独立核验 | 结论 |
|---|---|---|---|
| **G1** | 「我域本轮全部落地」＋ **否证本层两处行号订正** | 逐条回源：被否证的两处属 **U+FFFD 普查/分派快照**（记**历史原址**）⇒ 实测该文件**当前 U+FFFD = 0**（坏字节早已被 G2 修掉）⇒ ⇒ **「按行号平移」的前提不成立** | ✅ **采纳 G1 的否证** ⇒ 本层**撤回**那两处（见 §九①） |
| **G2** | 报 **9 处**行号位移指针待订正 | 逐条**内容级映射**（同名文件有多份拷贝 ⇒ 先定拷贝身份）⇒ **5 处该改**、**2 处不该改**（G2 误报）⇒ ⇒ G2 报的 9 处里，**它自己也没分清「当前内容」与「历史原址」** | 🟢 **部分采纳** ⇒ 净订正 **5 处** |
| **G4** | 「零待落、可关闭」＋ 指出 `docs/PhaseGoal/**` **无主面** | 独立复核：四条 lane 白名单里含 `PhaseGoal` 的条目 = **0 条** ⇒ 且 `docs/Worktree.md` §二的**政策文本**里也没有 ⇒ ⇒ **政策与机器守门两处同时漏** | ✅ **采纳** ⇒ 本层落闭合（见 §九②） |

---

## §三 本层（合并收口执行者）落码清单

| commit | 内容 |
|---|---|
| `e22ea9b9` | **X-6 改判 ✅**（判据原文与三条判定命令**一字未改**，只改「当前状态」列）＋ `count-outbound-stubs.py` **判据盲区修复** ＋ §六 6.3 守门计数订正 ＋ per-lane 矩阵重生成 |
| `959c1999` | 收官轮：行号位移指针订正（**初版 9 处 · 后订正为 5 处**）＋ **指标表订正**（`_INDEX.md` + `grading/_README.md` 各加一列） |
| `99a6d66f` | 🟢 **撤回本层两处错误订正**（G1 独立否证）＋ M-21 记录据实订正 ＋ 修本层自己引入的行号位移 ＋ 修**编号撞车**（M-16 → M-21） |
| `50f49853` | **闭合「无主面」写权洞**：机器守门新增**断言 9「无主面检测」** ＋ `SHARED` 补登记两族 ＋ 🐛 修断言 2 的 glob 假绿 ＋ 派生层重算 |

---

## §四 守门读数（枚举全仓 · 逐道实跑）

> **口径**：**枚举全仓所有 `check-*.py`**（`find . -name "check-*.py" -not -path "*/__pycache__/*"`）
> ⇒ **46 个门** ⇒ ⇒ **不手写清单**（M-15 教训：手写清单会漏门）。
> ⚠️ **本轮自己踩了第二次**：第一遍只扫了 5 组 glob（`tools/difftest/` · `tools/check-ledger-*` ·
> `tools/check-agents-budget.py` · `tools/ledger-derive/` · `ci/scripts/`）⇒ **只覆盖 36 个**
> ⇒ **漏了 10 个** ⇒ ⇒ 其中 8 个可跑 ⇒ ⇒ **其中 1 个红是本轮自己写坏的链接**（已修）·
> ⇒ **4 个红是主干自带**（见下）⇒ ⇒ **最终口径 = 40 绿 / 4 红 / 2 跳过**。

| 项 | 读数 |
|---|---|
| **门禁全扫（全仓 46 个）** | **40 绿 / 4 红 / 2 跳过** |
| 跳过的 2 个 | `check-ddl-static.py`（需 PG 容器）· `check-merge-tree-content.py`（**用法门**：需给 `<ref1> <ref2> …`） |
| **4 个红（全部主干自带 · 本层零引入）** | ① `tools/assertion-audit/favorite/check-assertion-sources.py` ⇒ **门禁级 20 条 / 提示级 55 条**<br>② `tools/g3-anchor-check/check-anchors.py` ⇒ **FILE_MISSING 1 条**（`comment-api/…/CommentRpcClient.java:10` → `service/comment/api/internal/svc/service_context.go:18-23` **NO-SUCH-PATH**）<br>③ `tools/ledger-lint/favorite/check-ledger-selfcheck.py` ⇒ **省略写法 20 条 + `entity-layer-inert` 1 条**<br>④ `tools/ledger-lint/favorite/check-ledger-vs-reality.py` ⇒ **门禁级不一致 3 条**<br>⇒ ⇒ **归因实测**（`git archive HEAD` 出**主干纯净副本** + 软链让 Go 真源可达 ⇒ 同条件对照）⇒ ⇒ **4 个红的读数与主干**逐条一致** ⇒ ⇒ **本层零引入** ⇒ ⇒ 且**全部落在 favorite / comment 域面** ⇒ ⇒ **它们的根因 = 这 4 个门从来不在任何人的门禁清单里 ⇒ 红灯长期无人看见**（M-15 同族）⇒ ⇒ **登记 + 建议纳入常跑清单**（本层不代改域面内容） |
| **本层自己写坏并已修** | `tools/check-doc-links.py` ⇒ 🔴 `docs/Pitfalls/like/api.md` → `../Worktree.md` **链接失效**（应为 `../../Worktree.md`）⇒ 已修 ⇒ 该门 **EXIT=0 / PASS** ✅ |
| `check-merge-tree-content.py` **按正确用法实跑** | `align/g1-users-follow → align/g2-message-like-task → align/g3-article-comment-favorite-hot` ⇒ **EXIT=0 · PASS**（每步「问题 0 · 不可信 0」）⇒ ⇒ **合并内容零丢失**<br>⚠️ 加进 `align/g4-service-common` ⇒ **该步有冲突 ⇒ 本门对该分支不适用**（与「作废不合并」裁定一致） |
| **派生层** | `derive-stage1.py --check` ⇒ **EXIT=0 / PASS**（产物与源台账同步） |
| **身份组** | `gen-identity-groups.py --check` ⇒ **EXIT=0**（**107** 条关系） |
| **指针棘轮** | `check-pointer-consistency.py` ⇒ **EXIT=0**（存量例外 **237** == 当前 **237** ⇒ **新增 0 条** · 已消失 0 条） |
| **judge 聚合** | `check-phasegoal-consistency.py` ⇒ **EXIT=0** |
| **per-lane 矩阵** | `check-phasegoal-lanes.py` ⇒ **EXIT=0** |
| **写权守门** | `check-wave-lane-ownership.py` ⇒ **EXIT=0**（**19** 个共享面 · **无主面检测**已判定 3 个 lane 实际改过的 `docs/**` 文件 ⇒ 全有归属） |
| **活指针守门** | `check-ledger-count-pointers.py`（`tools/ledger-derive/`）⇒ **EXIT=0** |
| **源声明测试数** | `check-source-test-decl-count.py` ⇒ **EXIT=0** |
| **出向 stub 判据** | `count-outbound-stubs.py` ⇒ **EXIT=0**（**零上界类数 = 0**）· `--check-inventory` ⇒ **EXIT=0** |
| **下游 deadline** | `check-deadline-downstream.py` ⇒ **EXIT=0**（Go=RAG 四键逐键相等） |

---

## §五 全量回归读数

> ⚠️ **口径**：读数取 **surefire XML 的 `tests=/failures=/errors=/skipped=`**（不是 stdout 的文字行）⇒ ⇒
> 载体守门以 XML 为准；定向 `-Dtest=` 会改写报告，故本轮为**全量**跑。

**（本节读数待全量跑终态补入）**

---

## §六 派生层读数（本轮重算后）

| 指标 | 读数 | 独立复算 |
|---|---|---|
| 派生表编号数 | **1174** | ✅ TSV 复算一致 |
| 已定级 | **754 / 1174 (64%)** | ✅ |
| 未定级 | **420 / 1174** | ✅ |
| 未定级且开放 | **3** | ✅ |
| **真缺口 A/B 类未排期** | **0** | ✅ ⇒ **阶段一范围内零漏排** |
| **真缺口 C/D 类未排期** | **86** | ✅ ⇒ 阶段 2 承接 |
| 已闭合（编号级） | **105** | ✅ |
| 未排期（行） | **1203** | ✅ |
| 阶段归属（行） | A 7 · B 26 · C 68 · D 227 · N/A 436 · — 520 | ✅ |
| 状态冲突 | **153**（同源组 **49**） | —— |

⚠️ **「已闭合」有两个口径 · 不得混用** —— stdout 的 **105** = **全表** `_closed` 标记的**编号级**计数（**不限**是否落在「开放 ∧ 未排期」集合内）；派生层正文 §四 的「④ 已闭合」= **先取 `g_open` 再筛 `_closed`** ⇒ 实测 **56** ⇒ ⇒ **105 与 56 不是同一个量**。

---

## §七 阶段一结论（四选项逐字对上 `docs/twoStage.md` §5.6）

| §5.6 的四个选项（原文） | 本轮结论 |
|---|---|
| 「通过。」 | ❌ **不适用** —— 必要条件「P0 九条全满足」**不成立**（P0 #5 = 🟡） |
| 「有条件通过，但必须明确有限例外及其影响。」 | ❌ **不适用** —— ⚠️ **理由已换**：原排除理由 = X-6（已闭合）⇒ 新理由 = **「P0 九条全满足」是准入条件，不是「可枚举的有限例外」** ⇒ ⇒ 本选项**结构性不适用** |
| 「未通过。」 | ✅ **本轮结论** —— **必要条件只剩 1 条不成立**（P0 #5 = 🟡 证据覆盖缺口）⇒ ⇒ **构成收窄为 1 条** |
| 「受阻，缺少必要的环境或证据。」 | ❌ **不适用** —— 按 §8.4 既定口径：**证据覆盖不足 ⇒ 归「未通过」并注明缺哪份证据** ⇒ 不归「受阻」 |

⇒ ⇒ **阶段一 = 未通过**；**构成 = 1 条**（P0 #5）；**补齐 9 域起栈形态证据即可闭合 ⇒ 不需要改产品代码**。

### §7.1 P0 #5 的现状（本层**自己复测**，不采信上一轮）

| 事实 | 本层实测 | 与 judge 记载是否一致 |
|---|---|---|
| `docs/RoundPlan/round-01/service-startup-matrix.md` | **仍不存在** | ✅ 一致 |
| `manage.sh:18` 的 `APP_SVCS` | **仍 = `"user-rpc user-api"`** ⇒ **只覆盖 2/20 服务** | ✅ 一致 |
| 「证据广度只覆盖 1/10 域」 | **不变** | ✅ 一致 |

⇒ ⇒ **P0 #5 维持 🟡 部分满足 · 不改判**（性质 = **证据覆盖缺口**，不是产品缺陷）。

### §7.2 其余 P0 九条（本层复测）

**✅ 8 条**（#1 #2 #3 #4 #6 #7 #8 #9）· **🟡 1 条**（#5）· **🔴 0 条** · **⚫ 0 条** ⇒ 合计 **9 条** ⇒
⇒ ⇒ **「九条全满足」不成立** ⇒ ⇒ 「通过」必要条件破 1 条。

---

## §八 剩余未闭合项分档（派生层实测 · 编号级聚合）

| 档 | 读数 | 说明 |
|---|---|---|
| **A 类（阶段一准入阻断）** | **2** | `G4-A4-05`（→ `GAP-07`）· `G4-A4-07`（→ `GAP-08`）⇒ **两条都【已排期】** ⇒ ⇒ **不是「漏排」**<br>· `GAP-07` = 「已被 `_task-universe.md` 点名 ⇒ 不是新漏排」<br>· `GAP-08` = **`G4-A4-07` 归 E65 统一载体（Jason 已拍板，本轮不改）** ⇒ ⇒ **均不构成阶段一阻断** |
| **B 类（阶段一竞争力补强）** | **14** | 全部 `blocking=否` ⇒ 不阻阶段一 |
| **C 类（阶段 2 承接）** | **63** | 其中 **56** 条 `schedule=未排期`（阶段 2 的工作面） |
| **D 类** | **227 行** | 差异已消失 · 无待实现内容 |
| **未定级且开放** | **3** | `SD-2026-10-30-CAND-22`（P1 · 未签字）· `G4-15b`（P2）· `G4-15c`（P2）⇒ **全部在 `global` 域 = G4 派工面** ⇒ ⇒ **无阶段归属 ⇒ 不进任何施工单 ⇒ 需先定级才能排期** |
| **已闭合** | **105**（编号级） | 另有 §六 的「56」口径 |
| **状态冲突** | **153**（同源组 **49**） | 同源组 = 台账两边没同步 = 真缺陷 |

⚠️ **两个口径不可混用**（本轮自己差点犯）：
① **「仍开放且在阶段一范围内」= 79**（A 2 · B 14 · C 63）—— **不论是否已排期**；
② **「真缺口 A/B 类未排期」= 0** —— **只算没排期的** ⇒ ⇒ **A 类阻断是 2 条，不是 0 条**；
⇒ ⇒ 加了 `schedule=未排期` 过滤就得到「52 全 C」，那是**另一个量** ⇒ ⇒ 引用时必须标明口径。

---

## §九 本轮的三处自我订正（据实留档）

### ① 🔴 撤回本层两处**错误订正**（G1 lane 独立否证 ⇒ 本层采纳）

· 本层先把两处 **U+FFFD 普查/分派表**的行号按「删 import ⇒ 行号前移」改了（`:43` → `:42`）。
· **G1 独立否证** ⇒ 本层回源复核**成立**：
  ① 实测该文件**当前 U+FFFD = 0**（坏字节**早已被 G2 修掉**；连删除 import 之前那版也为 0）
  ② ⇒ 普查表/分派表记的是**扫描当时的历史原址** ⇒ ⇒ **不随代码平移**
  ③ ⇒ 改后的 `:42` 落在 `* </ul>` —— 一个**完全无关的行**
· ⇒ ⇒ **根因（比「副本认错」更深一层）**：**「指针指向当前内容」这个前提本身要先验** ——
  本层只验了「是不是同一份拷贝」，**没验「它指的是当前内容还是历史事实」**。
· ⇒ ⇒ **净订正由 9 处改为 5 处**（记录已改：`docs/Worktree.md` **M-21** ＋ `docs/Pitfalls/like/api.md`）。

### ② ✅ 闭合「无主面」写权洞（**G1 与 G4 两棵 lane 各自独立发现同一个洞**）

· **两个洞叠加**：① **机器守门**（`check-wave-lane-ownership.py`）没有 `docs/PhaseGoal-judge.md` ＋ `docs/PhaseGoal/g{1..4}.md`；
  ② **政策文本**（`docs/Worktree.md` §二的合并收口执行者共享面清单）**也没有**它们 ⇒ ⇒ **不是「机器漏跟上政策」，而是两处同时漏**。
· **不能归给某个 lane**：那 4 份 `g<N>.md` 文件头自述「**机器生成 —— 不要手改**」＋「唯一数据源 = `docs/PhaseGoal-judge.md`」
  ⇒ ⇒ 它们是 **judge 的派生物**、不是各 lane 的面 ⇒ ⇒ 归 lane 就会有人手改 ⇒ 立刻与基线分叉。
· **落法**：① 机器守门**新增断言 9「无主面检测」**（逐 lane 判「它实际改过的 `docs/**` 文件」的归属：
  自己白名单 / 别域白名单 / `SHARED` / **谁都不命中**）② `SHARED` 补登记 **5 个** PhaseGoal 文件 ＋ `docs/DRIFT/_cross/**`
  ③ 🐛 修断言 2 的**假绿**（原式拿 `SHARED` 的原始字符串撞展开后的文件集合 ⇒ **glob 形式恒撞不上**）
  ④ **政策文本同批补登记**（`docs/Worktree.md` §二 三处）。
· **变异自证（三条子判据逐条验）**：① 造「lane 写了谁都不拥有的文件」⇒ **精确转红**并点名 ✅
  ② 造「lane 写了别域白名单里的文件」⇒ 报「**越权写别域的面**」并点名归属 lane ✅
  ③ 造「lane 写了 `SHARED` 里的文件」⇒ 报「**越权写共享面**」✅ ④ 复原后**转绿** ⇒ **变异可逆**。
· ⚠️ **当场抓到真事**：断言 9 首次运行即报出 **G1 lane 的 `4ab7415e` 写了共享面 `docs/DRIFT/_cross/stage1-derivation.tsv`**
  ⇒ ⇒ 该共享面被 G1 自己的下一笔 `267bfe1d` **恢复与主干逐字节一致** ⇒ ⇒ **净差 = 0** ⇒ 未被污染（但越权写这件事是真的）。

### ③ 🐛 修本层自己引入的两类问题

· **行号位移**：§九② 的 M-20 登记插入在 `docs/Worktree.md:563` ⇒ ⇒ 使该文件两处行号指针下移
  ⇒ 已连带订正（`:1076`→**`:1077`**（5 处引用）· `:587`→**`:588`**（2 处引用））⇒ 全部为**单行→单行替换** ⇒ 不再级联。
· **编号撞车**：`docs/Worktree.md` 的 M 表里**已有 M-16**（2026-10-31 那轮）⇒ 本层新加的「M-16」**撞号** ⇒ ⇒ 改为 **M-21**
  ⇒ ⇒ 判定式：**新登记项落笔前先查既有编号**（与派生层 `C-3` 编号撞车**同族**）。

---

## §十 未决项（等 Jason 拍板 · 本层不代决）

| # | 事项 | 现状 | 影响面 |
|---|---|---|---|
| 1 | **per-client 上界** | 「per-client 上界仍未授权 ⇒ 本面不落码」 | 共享层能力缺口（`service/common/**`）⇒ 需授权才能落 |
| 2 | **`D-015`** | 台账明写「🔴 未修 · 待拍板（仅 api 侧）」⇒ G1 建议归「阶段 2 证据面」 | 派生层待办面的一项真实待拍板项 |
| 3 | **lane 提交派生产物的流程口子** | lane 改了源台账 ⇒ 派生产物由谁负责？（允许合入后一个 commit 的 `--check` 红窗口 ／ 改「lane 只交源台账 + 产物由收口执行者全量重算」） | 本轮已用**后者**执行（收口执行者全量重算）⇒ 需把口径写死 |
| 4 | **推 `origin`（CodeArts 镜像）** | `origin` 落后约 **1050 笔** ⇒ 本轮**未推**（派工单明示「不要 push、不要开 PR」） | 与 CI 的 pin 目标有关 |
| 5 | **CI 流水线「真跑绿」** | 13 条 job 曾处于 paused；canonical 的 git resource **从未成功 check 过**；流水线 pin 的是 **branch** ⇒ ⇒ 报绿时必须带 **build 号 + 当时 pin 的 commit** | CI lane 的最终证据 |
| 6 | **两套同题出向 stub 普查并存** | `docs/DRIFT/common/outbound-stub-census.*`（21 类 / 80 调用点 / 零载体 37）vs `docs/RoundPlan/round-02/*`（21 个类 / 三态 7·1·13 / 零上界 0）⇒ ⇒ 「21 个类」与「零上界 = 0」**互证通过** ✅ ⇒ 但「零载体 37」是**调用点**口径、「裸 stub 13」是**类**口径 ⇒ **不得混用** ⇒ **谁收敛为唯一真源未决** | 派生层口径 |
| 7 | **`docs/PhaseGoal/**` 的长期归属** | 本轮已归合并收口执行者（政策 + 机器守门两处同批闭合）⇒ 但 `docs/Worktree.md` §二的「域波 lane」行仍写着「`docs/PhaseGoal/` ⇒ 各 lane 读自己的 `g<N>.md`」 | 需在下一轮把 §二·五的读面表述与「专写」口径对齐 |

---

## §十一 复现入口

```bash
# 合并清单（实际带进来的集合）
git log --oneline --merges c950e3ee..HEAD

# lane 待合（真口径 = 相对主干差集 + 内容级核对；禁用 git cherry 的假 +）
for b in align/g1-users-follow align/g2-message-like-task \
         align/g3-article-comment-favorite-hot align/g4-ledger-close \
         align/ci-concourse align/g4-service-common; do
  echo "$b ahead=$(git rev-list --count do-my-own-gh..$b) behind=$(git rev-list --count $b..do-my-own-gh)"
done

# 派生层（取证 → 生成 → 确认）
python3 tools/ledger-derive/derive-stage1.py --check     # 期望 EXIT=0
python3 tools/ledger-derive/derive-stage1.py             # 生成
python3 tools/ledger-derive/derive-stage1.py --check     # 期望 EXIT=0

# 守门
python3 tools/difftest/check-wave-lane-ownership.py       # 含断言 9「无主面检测」
python3 tools/ledger-derive/check-pointer-consistency.py  # 期望 237 == 237 · 新增 0
python3 docs/RoundPlan/round-02/count-outbound-stubs.py   # 期望 零上界类数 = 0
python3 tools/difftest/check-merge-tree-content.py \
  align/g1-users-follow align/g2-message-like-task align/g3-article-comment-favorite-hot

# 全量回归（读数以 surefire XML 的 tests/failures/errors/skipped 为准）
mvn -o -B -DargLine=-Xmx2g --fail-at-end clean test
```
