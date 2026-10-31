# 未解决事项清单（阶段一 · RoundPlan）

> **本清单只登记，不修。** 每条都指向一个执行者或一个轮次；本文件本身不含任何整改动作。
> **上位索引** = [`_task-universe.md`](_task-universe.md)（任务全集）· [`round-01/README.md`](round-01/README.md) ~ [`round-07/README.md`](round-07/README.md)（逐轮计划）· 判据基线 = [`docs/PhaseGoal-judge.md`](../PhaseGoal-judge.md)。

## 口径说明

1. 本清单**只登记不修** —— 「去向 / 处置」列指向执行者或轮次，本文件不执行任何整改。
2. **6 类互不重叠** —— 一条事项只归一类；归类依据是「它对阶段一判据的作用」，不是它属于哪个域、哪个文件。
3. **只有 A 类（第 1 类）阻阶段一** —— 第 5 / 6 类的事项**不计入**阶段一判定。
4. **台账登记类事项不升级为「请你拍板」** —— 已登记进 `docs/Intentional_Deviations/` 的候选，登记完就躺着（措辞「🏷 候选，建议签」）。
5. **C 类阶段二暂缓项不计入** —— 依据 `docs/PhaseGoal-judge.md:53-57`（§1.1 ⑥ 精确边界 · **第 4 条** = `:57` 那条 C 类 bullet）⇒ C 类定级口径见 `docs/twoStage.md:954`。
6. **证据强度以各域台账为准** —— 「已改」≠「已验证」；台账里的 ✅ 一律读作「在该证据强度下一致」。
7. **本清单不含结论句** —— 不写任何「已通过 / 已对齐」类判定；判定归 `docs/PhaseGoal-judge.md` §8.4（Round 3 的面）。
8. 🆕 **排期指针有时效（2026-10-30 第 4 轮）** —— 指向**已了结**的未解决项**不算排期**（派生层规则 `U8d`）。
   **关闭判定只认本表的「当前状态」列**（第 3 列）里的「已关闭」/「已处置」⇒ **不是**整行叙述里的任何一个词。
   ⇒ **为什么是列作用域**：整行子串会让**新增行自触发**（新登记行里写一句「`GAP-10` 已处置 ⇒ 指针失效」⇒ 本行被判已了结 ⇒ 刚补上的指针立刻失效 ⇒ **自我抵消**）。
   ⇒ **当前已了结项** = `GAP-10`（已处置）· `GAP-12`（已关闭）⇒ 指向它们的指针**一律失效** ⇒ 由 `GAP-13` / `GAP-14` 重新落地。

---

## 1. 阶段一阻断项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `BLK-01` | **P0 #5 覆盖缺口** —— 判据主语是「**项目**」不是「某个域」 | 旧结论只覆盖 users 域 **1/10 域**、**2/20 服务** ⇒ 实测照 `README.md:127-147` §三 原文起 `article-api` ⇒ `nacos registry … serverAddr='nacos:8848'` → `ErrCode:-401 Client not connected` → `Application run failed`（进程退出）；只用 shell 覆盖地址 + 错开端口 ⇒ article-api 正常启动（`Tomcat started on port 8899` ＋ Nacos 注册成功 ＋ `/actuator/health` 200 `{"status":"UP"}`）⇒ **缺陷面 = 地址 + 端口两类，不是代码问题** | `P0 #5`（`docs/PhasedGoal.md:46`）· `X-12`（judge §五）· `P0 #8`（`:49`，fail-closed 不得被本次改动弄坏）· `S1-1` / `S1-4` / `S1-6` | `docs/PhasedGoal.md:46` · `docs/PhaseGoal-judge.md:436` · `README.md:127-147` · `docs/RoundPlan/round-01/README.md:12` | **Round 1**（`P1-T01`~`P1-T05`） |
| `BLK-02` | **E65 出向 gRPC 不继承请求 deadline** —— Go 到点立刻失败，RAG 出向调用可无限挂住 | 🟡 **【2026-10-30 第三次订正 · 原读数已被推翻】** —— 原写「RAG 出向调用**无 deadline 上界 ⇒ 可无限挂住**（实测 `failed_fast=false, code=0`）⇒ **根因 = RAG 没有 ctx 传播**」**已被双侧活体证伪**：那两格是**探针产物**（RAG 半边自搓裸 channel ⇒ 绕开 devh 装配 ＋ 换线程 ⇒ 看不到载体）⇒ ⇒ 探针改走 **devh 生产装配路径**后实测 **Go = RAG = `failed_fast=true\|code=4\|outgoing_carrier=present_expired\|sql_carrier=present_expired`**（四键逐键相等）⇒ ⇒ **继承面已闭合**（台账 `E65` 已闭合）⇒ ⇒ ⇒ **本项残余 = 「客户端硬上界 2000ms 未启用」**（= **另一条轴** · `P2-T04` · 域侧 yml）⇒ ⇒ ⇒ **X-6 仍 🔴 · C-3 仍破 · 阶段一仍「❌ 未通过」**（破的理由已换） | `P0 #4`（`docs/PhasedGoal.md:45`）· `X-6`（judge §五）· `C-2` / `C-3` · `S1-1` / `S1-4` / `S1-6` | `docs/DRIFT/global/vs-common.md:30` · `docs/DRIFT/global/vs-common.md:46` · `docs/DRIFT/global/_README.md:391` · `docs/PhaseGoal-judge.md:259` | **Round 2**（`P2-T01`~`P2-T05`） |
| `BLK-03` | **`S1-FOLLOW-007` —— 基线仍把「已签字偏离」记成开放缺陷** ⇒ 机器生成的派单指令会让 G4 去改掉用户已决定保留的行为 | ✅ **已关闭（2026-10-31 · 合并收口执行者）** —— **原状态「🔴 维持仍开着」逐字留存于本格末尾**（原值保留，不删改）⇒ ⇒ **关闭依据（本面独立复核 · 不采信转述）**：① **事实层已闭合** —— `S1-FOLLOW-007` 在派生层已是 **✅ 已一致**（G1 的销号 `12fbad33` **已在主干**，带入者 `2ab06d95`）⇒ ⇒ **派生链已连上**；② **机器派单面的危害已消解** —— 派工面 `g1.md` 的 `follow-api` 行「开放项归属」列实测 = `**→ 非开放（已签字 · 不是缺陷）**` ⇒ ⇒ 判据 `tools/difftest/check-phasegoal-lanes.py` 的 `route_open()` 分支（认「不计入本行的开放残余差异 / 有意偏离…不是缺陷」）把它判为**非开放** ⇒ ⇒ **不再产出「→ G4」派单指令** ⇒ ⇒ 本条描述的危害**不成立**；③ ⚠️ **本条只关「未解决项」这半** —— `S1-FOLLOW-007` 的 `blocking` 读作 **是**（`stage` = **A**）来自**定级层**（U5-O 显式定级叠加层）⇒ ⇒ **事实变了 ≠ 定级变了** ⇒ ⇒ **`blocking` 本面不擅自翻**（并入 Jason 的定级复判批次）。**原状态：🔴 维持仍开着**（2026-10-30 users lane 重核：基线里那条「已签字偏离」**仍被记成开放缺陷**）· 阶段归属 **A**（派单错误 ⇒ 共享层被改坏）· 优先级 **P1** | `S1-1` / `S1-4`（阶段 1 准入面）· 判据基线 = `docs/PhaseGoal-judge.md`（§1.5：**四层状态只认一个来源**） | `docs/DRIFT/users/stage1-admission.md:524` · `docs/PhaseGoal-judge.md:192`（`follow-api` 的 A-2 行「残留」列**字面原值仍写** `DRIFT-FOLLOW-008` ⇒ ⚠️ **那是刻意保留** —— 该行已带「不计入本行的开放残余差异」措辞 ⇒ 派单判据 `route_open()` 正认它 ⇒ ⇒ **不要把它改掉** ⇒ 改掉反而会让判据失配 ⇒ **这半已闭合**） | **Round 3**（基线复判订正面 · `P3-T01`~`P3-T09`）—— ✅ **已接入 Round 3 的条目清单**（`P3-T02` / `P3-T07` 的行）⇒ ⇒ **动作已完成**（2026-10-31 · 合并收口执行者）⇒ ⇒ 剩余动作 = **`blocking` 定级复判**（属 Jason 拍板面 · 本面不擅自翻） |

> ⚠️ **【2026-10-30 第三次订正】** 原写「`BLK-02` 的守门 `check-deadline-downstream.py` 的 **PASS 语义是「漂移还在」** ⇒ **不能拿它的 PASS 证明 X-6 成立**」**已过期**：该守门**现在**是**纯等值门**（漂移名单已清空 ⇒ 四键全部 parity）⇒ ⇒ **PASS 的含义是「两侧真的相等」** ⇒ ⇒ ⇒ **但 X-6 仍 🔴** —— 破的是**判据的另一项**（客户端硬上界 2000ms 未启用 · `P2-T04`）⇒ ⇒ ⇒ **仍然不能拿 PASS 证明 X-6 成立**（理由换了：不再是「口径反」，而是「判据未全满足」）。

> 🆕 **2026-10-30 · 派生层真缺口轮（`docs/DRIFT/_cross/stage1-derivation.md` §4.1）**
> ⇒ 本类新增 `BLK-03`（**1 条**）。判定口径 = **状态开放**（🔴 / 仍生效 / ⏳ 未签字）
> **且** 阶段归属 ∈ {A, B} **且** `_task-universe.md` 的 37 条任务**都没有指向它**。
> ⇒ ⚠️ 派生层口径下 **A/B 类未排期共 3 条**（本类 1 条 ＋ 第 2 类 2 条）⇒ **已全部登记**。

---

## 2. 证据缺口

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `GAP-01` | **G-19 proto「消费方式」取证** | 🟡 取证缺口 · 不降 R —— `.proto` 文件层逐字节一致**推不出**消费方式一致（`optional` presence / 代码里的字段号硬编码 / Jackson 配置）⇒ AGENTS.md §3.4 的效力边界在 **10 个 proto 上全部成立** | `X-10`（judge §五）· proto 组件 ③⑤ · `R3 ⑥ 第 3 条`（取证缺口不计入） | `docs/PhaseGoal-judge.md:483` · `docs/PhaseGoal-judge.md:263` · `docs/DRIFT/_cross/proto-verification.md:40-46` | **Round 5** `P5-T01` |
| `GAP-02` | **G-17 `like-common` 专属台账缺失** | 🟡 **记录缺口**（非行为缺口）—— 「从未与 Go 对照」的判定已被仓内证据推翻（它有 Go 派生的契约测试 `LikeErrorContractTest`：29 个码逐条 1:1 ＋ 文案表 ＋ 未知码回落 ＋ `CodeError` 序列化）⇒ 缺的只是**逐条台账记录** | `like-common` 组件的**可审计性** · 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 3 条） | `docs/PhaseGoal-judge.md:481` | **阶段二**（见本清单第 6 类）· 本阶段只登记 |
| `GAP-03` | **`U-01` 的「DC → 第三仓」一段**（`Sea-BreakTheWaves`） | 🟡 取证缺口（部分闭合）—— like → DC 那一跳已 L4 闭合（两侧 970 B sha256 相同 ＋ outbox 终态逐字段相同）；**DC → 第三仓消费端那一跳仍未取证**（需 pinned checkout ＋ 40-hex SHA）⇒ **仓外、本机不可核验** ⇒ **不是缺陷** | 不计入 `X-1` 判定 · 不阻阶段一 | `docs/DRIFT/like/api.md:801` · `docs/DRIFT/like/rpc.md:9` | **阶段一之外**（见本清单第 6 类） |
| `GAP-04` | **共享层 28 项里的「证据缺口 1」** —— `LoggerFactoryBean` 各模块注册方式不一致 | 🟡 **U-2** —— 各模块的注册方式不一致（有的显式 `@Import`，有的只靠 `scanBasePackages`）⇒ **是否有模块漏注册「未验」**（跨模块，未验）⇒ 属**取证缺口**而非行为差异 | 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 3 条）· 须登记进 judge §九 证据缺口清单 | `docs/DRIFT/common/alignment-2026-10-23.md:957` · `docs/DRIFT/common/alignment-2026-10-23.md:525` | **Round 5** `P5-T02`（台账对账面）· 跨模块复核归各域 lane |
| `GAP-05` | **`S1-FOLLOW-008` —— `check-surface-parity.py` 的两个静默假绿口子** | 🔴 **维持仍开着**（2026-10-30 users lane 重核：**工具在位**（`tools/difftest/check-surface-parity.py` · 23,215 B · 2026-10-29 03:27），但**「两个口子」的具体位置本轮未逐条复现**）⇒ 属**取证缺口**（不是已证实的缺陷）· 阶段归属 **B**（阶段 1 竞争力补强）· 优先级 **P2** | 阶段 1 契约面判据的**守门可信度** · 不阻阶段 1 | `docs/DRIFT/users/stage1-admission.md:525` · `tools/difftest/check-surface-parity.py` | **Round 5** `P5-T02` 口径（取证与台账对齐）—— **共享工具面** ⇒ 落点 `tools/difftest/`（**G4 写权面**）⇒ 按 §5.3e 走共享层反馈单 |
| `GAP-06` | **`S1-FOLLOW-009` —— 5 个 api 模块缺 `TESTCONTAINERS_RYUK_DISABLED`** | 🔴 **维持仍开着**（2026-10-30 users lane 重核：`grep -rln "TESTCONTAINERS_RYUK_DISABLED" service/ --include='*.yml' --include='*.yaml'`（排除 target）⇒ **0 命中**；逐模块实测仍缺）· 阶段归属 **B** · 优先级 **P2** | 阶段 1 的**验证手段可用性**（Testcontainers 清理容器会拖慢/挂住本地跑测）· 不阻阶段 1 | `docs/DRIFT/users/stage1-admission.md:526` | **Round 4 / Round 5** —— **各域域侧**（5 个 api 模块）⇒ 归各域 lane，**不是**共享层 |

| `GAP-07` | **派生层 A/B 类「U8 指针口径歧义」3 条** —— `B-10`（A/P0/阻断是）· `B-11`（A/P0/阻断是）· `G4-A4-05`（A/P0/阻断是） | 🟡 **已被任务覆盖，派生层认不出**（2026-10-30 U5-O 逐条 Go 对照轮实测）：三条的**定级依据里明确点名** `_task-universe.md` 的 `P2-T01` / `P2-T03` / `P2-T04`（E65 族「出向 gRPC 不继承 deadline / 零硬上界」）⇒ **不是新漏排**；但它们在台账里是 `global/vs-common.md` 的**表行编号**（`B-xx` / `G4-A4-xx`），而任务的「原始编号」列写的是 `E65` / `X-6` / `P0 #4` ⇒ **两套编号不是一一对应** ⇒ 派生层 `U8` 只认「任务块里直接写了编号」⇒ 判「未排期」 | 阶段 1 的**准入阻断面**（A 类 ⇒ 不处理无法通过阶段一验收）· **与 `PhaseGoal-judge.md` v5 的「❌ 阶段一未通过」一致** | `docs/DRIFT/global/vs-common.md:3846`（`B-10`）· `:3847`（`B-11`）· `:3755`/`:3812`（`G4-A4-05`）· 派生层 `docs/DRIFT/_cross/stage1-derivation.md` §4.1 | **Round 5** —— 处置 = **要么**在 `_task-universe.md` 的「原始编号」列补上这三个表行编号（**指针口径对齐**）**要么**在派生层 `U8` 增加「改造面内条目」语义连接 ⇒ **本层不猜**（§七 盲区第 8 条）⇒ 落点 `docs/RoundPlan/`（主线写权） |
| `GAP-08` | **派生层 A/B 类「依据里无任务指针」4 条** —— `G4-A4-07`（A/P0/阻断是 · article-rpc 裸 stub）· `G4-A4-01`（B/P2 · A6 错误码契约在共享层无对口探针）· `DRIFT-ARPC-009`（B/P2 · admin-rpc 缺 Stat + Prometheus 两层）· `DRIFT-USERSCOMMON-006`（B/P2 · JWT Bearer 前缀剥离长度边界 `> 6` vs `> 7`） | 🔴 **待人工确认排期归属**（2026-10-30 U5-O 逐条 Go 对照轮）：四条的定级依据里**未点名任何任务编号** ⇒ 派生层**无法自动判定**它是否已被别的任务覆盖 ⇒ **本层不猜**。⚠️ 已知线索：`G4-A4-07` 与 `GAP-07` 的三条**同族**（E65 出向 stub）⇒ 很可能同属 `P2-T03`/`P2-T04` 的改造面；`G4-A4-01` 在 `PhaseGoal-judge.md` 中 A6 已判 **🟡 不阻断阶段一** | 阶段 1 的**准入阻断面**（`G4-A4-07` = A ⇒ 不处理无法通过阶段一验收）· 其余三条 = 竞争力补强 | `docs/DRIFT/global/vs-common.md:3757`/`:3878`（`G4-A4-07`）· `:3751`/`:3761`（`G4-A4-01`）· `docs/DRIFT/users/admin/rpc.md:2947`（`DRIFT-ARPC-009`）· `docs/DRIFT/users/common.md:788`/`:869`（`DRIFT-USERSCOMMON-006`） | **Round 5** —— 人工确认后归入既有任务或新开任务 ⇒ 落点按各条域写权（`G4-A4-07` 落 `service/article` · `DRIFT-ARPC-009` 落 `service/users/admin` · `DRIFT-USERSCOMMON-006` 落 `service/users/users-common` = **G4 写权面** ⇒ 走共享层反馈单） |
| `GAP-09` | **派生层 A/B 类开放且未排期 8 条中的「真缺口」4 条 → 现 3 条** —— `E2`（B/P2 · `follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口）**（第 4 轮已判 D / 差异已消失 ⇒ 已剔除）**· `E7`（B/P2 · `follow` 域双侧活体对拍本轮未复验）· `S1-FOLLOW-004`（B/P2 · `follow` 域无编排入口，**域侧台账已写明 B 类**）· `S1-USERS-004`（B/P2 · 头像链路事务正确性依赖调用方永远跨 Bean 调用，**域侧台账已写明 B 类**） | 🔴 **真缺口**（2026-10-30 U5-O 轮后按**「🟡 部分闭合 = 仍开放」**修正后的口径重算）：四条都是「**修了一部分、还剩一部分**」⇒ 仍有待实现项 ⇒ 属阶段一范围<br>⚠️ **2026-10-30 第 4 轮订正**：原列 4 条中的 `E2` 已在第 4 轮（`W28.md`）判 **D / 差异已消失** ⇒ **本行现为 3 条**（`E7` · `S1-FOLLOW-004` · `S1-USERS-004`）⇒ `E2` **不再属本行** ⇒ 派生层 `E2`@follow 现为「已闭合 · 无待实现项」 | 阶段 1 的**竞争力补强面**（B 类）· `S1-FOLLOW-004` 影响「能按实际项目说明完成构建/配置/启动」这条**域级判据**（compose 只含 users 两个组件 ⇒ follow 域无入口） | `docs/DRIFT/follow/stage1-admission.md:145`（`S1-FOLLOW-004`）· `:377`/`:382`（`E2`/`E7`）· `docs/DRIFT/users/stage1-admission.md:158`（`S1-USERS-004`）· 派生层 §4.1 | **Round 5** —— `E7` 落 `follow` 域（域侧自修）· `S1-FOLLOW-004` 落**根级 compose**（`docker-compose.yaml`）⇒ **跨域写权** ⇒ 走 §5.3e 共享层反馈单 · `S1-USERS-004` 落 `service/users`（域侧） |
| `GAP-10` | **派生层 A/B 类「假缺口」4 条** —— `L-4`（A/P0）· `T-2`（A/P0）· `H-1`（A/P0）· `E1`（B/P2） | ⚠️ **不是缺口，是「同编号跨条目污染」** —— 逐条核验：① `L-4` / `T-2` / `H-1` 的**台账原文写着「✅ 无需改」「🟢 …已闭合」「✅ 已落码」**（⇒ 状态标记 🟡 是残留），但派生层给它们的 **A/P0 是从**同一编号的**另一处登记**继承来的（`L-4` 在 `article/vs-common.md` 至少 4 处登记，分别指「阈值无需改」「favorite 16 个 span 已落码」「9 个 span 已落码」「补 favorite 域自己的 span」四件不同的事）⇒ ⇒ **这是「164 条同编号冲突」的危害实例**：拿编号排期会**踩到别的条目的结论**；② `E1` 与 `T-6` 是**同一件事**（`Content-Type` 缺 charset），而 `T-6` 已在 U5-O 轮判「**差异已消失**」⇒ `E1` 的 🟡 也是残留 **· ✅ 已处置（2026-10-30 · 派生层口径侧）** | **台账编号卫生**（不是行为缺陷）· **但会直接导致派单错误** ⇒ 与 `BLK-03`（基线把已签字偏离记成开放缺陷 ⇒ 派单会改坏共享层）**同族** · 阶段一的**派单正确性**（不改错一条 = 省一轮返工）· 不阻阶段 1 | `docs/DRIFT/article/vs-common.md:73`/`:418`（`L-4`/`T-2`）· `docs/DRIFT/hot/vs-common.md:67`（`H-1`）· `docs/DRIFT/global/_README.md:332`（`E1`）· 派生层 §4.5 同编号多态清单 | **Round 5** —— ✅ **2026-10-30 已处置（派生层口径侧）**：用户拍板「**只改派生层口径，条目身份 = 编号 + 所属域目录**」⇒ 派生层已按域拆开 + 用**人工同源判定**（138 行）把「同一件事」的节点**仍合并** ⇒ **跨域串味已消除**（实测：4 条假缺口的成因消失；`L-4`/`T-2`/`H-1`/`E1` 各自回到本域）⇒ ⚠️ **台账侧仍未拆号**（同编号在不同域仍指不同的事）⇒ 「台账编号卫生」这一层**未做** ⇒ **留作台账侧的可选清理**（不影响派生层口径） |

| `GAP-11` | **派生层 B 类待人工判别 6 条 ⇒ 本轮闭合 2 条 ⇒ 仍开放 4 条**（2026-10-30 U5-O 第 2 轮后出现；**全部「阻断阶段一 = 否」⇒ 不阻塞阶段一**；⚠️ 原题头写「开放且未排期 6 条」是 2026-10-30 的**状态快照** —— 派生层 §4.1「A/B 类 · 开放且未排期」当前为**空表**）—— `A-5`（article 域 · 明文默认值）· `F-6`（favorite 域 · 明文默认值）· `G4-A4-03`（global · 1015 归因「只对 users 域成立」⇒ 漏记 4 个域）· `G4-A4-04`（global · 404/405 结论缺前置条件（CORS 会替换 405 分支）＋ 405 的 `Content-Type` 有两份互斥的 L3）· `DRIFT-MSG-V-032`（message · 慢阈值门禁在全量并发回归里偶发 `UNAVAILABLE`）· `C-5`（comment · 错误码探针缺 `comment/` 份） | 🟡 **待人工判别（本层不猜）** —— ① `A-5` 与 `F-6` 是**同一件事的两个域面**（明文默认值）⇒ 已被 `DEP-03` / `S1-USERS-002` 登记（8 个 rpc 模块 / 10 处）⇒ **不是新缺口**，是**派生层指针口径没认出 `DEP-03`**（与 `GAP-07` 同族）；② `G4-A4-04` 与 U5-O 第 2 轮查出的 **`G4-15`「已修」是假绿**（守门只断言 `ResponseEntity` 对象上的 `Content-Type` ⇒ 证不到容器实际写出的头）**同源** ⇒ 需人工判别两份互斥的 L3 哪份成立；③ **2026-10-31 · 本层订正 —— 6 条里 2 条已闭合 ⇒ 仍开放 4 条**：`C-5` ✅（`tools/proto-wire-probe/comment/` ＋ `tools/errmsg-probe/comment/` **两半边均已落仓** · 变异精确转红 ⇒ 定级 `docs/DRIFT/_cross/grading/W10.md:10` 已改判 **D**）· `DRIFT-MSG-V-032` **本体** ✅（2026-10-30 · G2 `c6c0d7d8` 落码：优雅拆除 ＋ 预热重试 ＋ 守门 `MessageRpcSlowThresholdTeardownGateTest` · 变异红在目标断言 · 域级回归 781/0F/0E/0S）⇒ ⇒ **仍开放 4 条** = `A-5` · `F-6` · `G4-A4-03` · `G4-A4-04` | 阶段 1 的**竞争力补强面**（B 类）· **不阻阶段 1** | `docs/DRIFT/article/vs-common.md:364`（`A-5`）· `docs/DRIFT/favorite/vs-common.md:228`（`F-6`）· `docs/DRIFT/global/vs-common.md:3753`/`:3754`（`G4-A4-03`/`G4-A4-04`）· `docs/DRIFT/message/rpc.md:2841`（`DRIFT-MSG-V-032`）· `docs/DRIFT/comment/vs-common.md:61`/`:222`（`C-5`） | **Round 5** —— ① `A-5`/`F-6` ⇒ 归并到 `DEP-03` / `S1-USERS-002`（**不新开任务**）② `G4-A4-03`/`G4-A4-04` ⇒ 落 `docs/DRIFT/global/` ＋ 共享层探针（**G4 写权面**）③ `DRIFT-MSG-V-032` ⇒ **本体已闭合**（G2 域侧落码）⇒ **残余 = 其它模块同款「发完就 `shutdownNow()`」硬拆写法**（2026-10-31 本层实测 `src/test` 命中 **142 处**）⇒ **阶段二**同族项 ⇒ 落点 = 各域自己的 `src/test` ⇒ 跨域写权 ⇒ 走 §5.3e 共享层反馈单（**G2 领衔**）④ `C-5` ⇒ **已闭合**（`tools/errmsg-probe/comment/` ＋ `tools/proto-wire-probe/comment/`，**两条探针均只被本域引用 ⇒ 真私有 ⇒ G3 域侧落码合法** ⇒ **原写「G4 写权面」归属错误已订正**）⇒ 本行不再需要为它新开任务 |

| `GAP-12` | **归一后「编号级未定级且状态开放」16 条**（2026-10-30 同源归一后出现）—— `G4-4`@article · `G4-1`@comment · `F-1`/`F-2`/`F-4`/`L-4`@favorite · `E2`/`S1-FOLLOW-013`@follow · `FB-04`@global · `D-2`/`D-6`/`D-7`/`FB-05`@like · `D-4`/`UNRESOLVED-005`@message · `U-1`@users | ✅ **已关闭（2026-10-30 第 4 轮收口）** —— **成因不是新增缺陷，而是口径修正**：前两轮 184 条定级是**编号级**的 ⇒ 归一后**按域拆开** ⇒ 这 16 条原本**靠跨域串味「继承」到别的域的定级** ⇒ 串味一断 ⇒ 重新变成未定级。⚠️ 这 16 条全部是第 3 轮人工判定为「**不同的事**」或「撞号」的节点 ⇒ ⇒ 它们**本来就该各判各的** ⇒ 第 4 轮（`W25`~`W32`）逐条与 Go 真源对照后各归本域：**17 行 / 16 个唯一编号**（`G4-4` 在 article + favorite 两域各一条 ⇒ **各判各的**）⇒ 归属 **B 1 · C 4 · D 12**（A **0**）· 三态 **差异已消失 13 · 差异仍成立 4 · 无法证实 0** · **阻断阶段一 = 是 的 0 条** ⇒ ⇒ 派生层「**未定级且状态开放**」**16 → 0** ⇒ **阶段一范围内仍无漏排** | 阶段 1 的**派单正确性**（把「继承来的定级」换成**本域自己的定级** ⇒ 派单才不会踩到别人的结论）· ✅ **不预判是否阻断阶段一** ⇒ 定级后已知：**全部不阻断** | 派生层 `docs/DRIFT/_cross/stage1-derivation.tsv`（`stage == —` 且状态开放且该编号无任何已定级登记）· 同源判定见 `docs/DRIFT/_cross/grading/_samesrc-W*.md` · 第 4 轮结论见 `docs/DRIFT/_cross/grading/W25.md`…`W32.md` · 归一说明见 [`docs/DRIFT/_cross/grading/_README.md`](../DRIFT/_cross/grading/_README.md) §1.2/§1.3 | ✅ **已关闭** —— 8 个 lane 全部落盘（`W25`~`W32`）⇒ **回写 49 个台账文件 / 201 节点**（逐字一致，节点级判据）⇒ **派生层重生成 + 15 守门全绿**。残留 4 条「差异仍成立」（`D-7`@like · `FB-05`@like · `G4-4`@article · `UNRESOLVED-005`@message）**全部是 C 类 ⇒ 阶段 2 承接**（`FB-05` 落点 100% 在共享层 ⇒ 走 §5.3e 反馈单）。⚠️ **台账侧订正遗留**：`D-7` 的「熔断打开 ⇒ 1015」半句**已过期**（like-api 真实路径落 5001 ⇒ 与 Go 同形）⇒ 落点收窄到「status 错误 ⇒ 业务码」⇒ **该域属主订正**（`W30` 无台账写权） |

| `GAP-13` | **派生层 A/B 类「排期指针指向已了结项 ⇒ 指针失效」1 条** —— `E1`@users（B/P2 · 项目说明残留 = `Infrastructure.md` 的 `api-gateway/` 与 `sea_user` 两处） | 🔴 **真缺口（阶段一范围内）** —— 原两条（`E1`@users ＋ `F-1`@favorite）原本挂在 `GAP-10` / `GAP-12` 名下 ⇒ 该项销账时**排期指针一并失效** ⇒ 派生层「真缺口 A/B 类未排期」由 0 变 2 ⇒ ⇒ **此前报的 0 是假的**。⚠️ **2026-10-31 订正为 1 条**：`F-1`@favorite **已闭合**（2026-10-31 · G3 `716eede8` 补 `CreateFavorite` 对拍 case ⇒ rpc 层整域双侧活体对拍 7/7 方法 · 27 条 FROZEN 逐字节一致 ＋ 变异已验 ⇒ 定级 `docs/DRIFT/_cross/grading/W27.md:3` 已改判 **D**）⇒ **不再有残留待实现内容** ⇒ **本行不再管 `F-1`**。⚠️ **口径订正（派生层 U8d · 指针时效性）**：排期指针**有时效** —— 指向**已了结**的未解决项 ⇒ **不算排期**。⚠️ **编号须域限定**：`E1` 在 `global` 域另有一条（已一致）⇒ 本行只管 **users 域**那一条 | 阶段 1 的**竞争力补强面**（B 类 · 不阻阶段一）· `E1` 影响「能按实际项目说明完成构建/配置/启动」（照 `Infrastructure.md` 操作会走错） | `docs/DRIFT/users/stage1-admission.md:503`（`E1`）· `docs/DRIFT/favorite/vs-common.md:223`（`F-1`）· `docs/DRIFT/_cross/grading/W27.md:3`（`F-1` 定级依据 · 2026-10-31 已改判 **D** ⇒ 本行不再覆盖）· 派生层 §4.1 | **Round 5** —— `E1` 落点 = **根级 `Infrastructure.md`**（`api-gateway/` 与 `sea_user` 两处）⇒ **不在任何域写权面** ⇒ 按 §5.3e 走**共享层反馈单**；⚠️ `F-1` 那半边**已于 2026-10-31 由 G3 闭合**（`tools/api-full-parity-probe/favorite-rpc/run.sh` 补 `CreateFavorite` 对拍 case ⇒ 两条链取并集 = 整域 ⇒ 原落点 `docs/DRIFT/favorite/rpc.md:4136`/`:4139` 已被覆盖）⇒ **本行不再需要给 `F-1` 派活** |

| `GAP-14` | **派生层 C 类「排期指针指向已了结项 ⇒ 指针失效」2 条** —— `G4-4`@article（C/P3 · A9 轴定义措辞写 `audit.*`，共享层实际是 `sea.*`）· `UNRESOLVED-005`@message（C/P3 · `extra` map 的 JSON 键序）　⚠️ **原第 3 条（like 域 · 「下游 gRPC status → 业务码」映射与 Go 不同）已于 2026-10-31 经 Jason 裁定改判为 D/P3**（已签字偏离 ⇒ 无待实现内容 ⇒ **不再需要排期指针**）⇒ ⇒ 已从本条覆盖范围移出 ⇒ ⇒ **本条现覆盖 2 条**（该条的编号与定级落点见 `docs/DRIFT/like/vs-common.md` 与 `docs/DRIFT/_cross/grading/W30.md`） | 🟡 **阶段二承接** —— 余下两条同因：原挂 `GAP-12` 名下 ⇒ 该项销账后**排期指针一并失效**。⚠️ 余下两条**都仍开放**（第 4 轮判「**差异仍成立**」）⇒ **不得判已了结**。⚠️ **三态缺档（派生层 U2-4\*）**：三态只有「差异仍成立 / 差异已消失 / 无法证实」⇒ **缺「行为已对齐但验证闭环未补」这一档** ⇒ 归属 A/B/C 时**不得**因 verdict 判「差异已消失」就判已了结 | **不计入**阶段一判定（C 类 · 依 `docs/PhaseGoal-judge.md:53-57` §1.1 ⑥ 第 4 条）· 但**阶段二必须有落地处** ⇒ 本行即落地处 | `docs/DRIFT/article/vs-common.md:277`（`G4-4`）· `docs/DRIFT/message/api.md:1097`（`UNRESOLVED-005`）· 第 4 轮定级 `docs/DRIFT/_cross/grading/W25.md:3`（`G4-4`）· `W31.md:4`（`UNRESOLVED-005`） | **阶段二** —— `G4-4` 落 `docs/DRIFT/_cross/domain-common-lanes.md`（**共享面文档** ⇒ G4 写权；G4 已在 `docs/DRIFT/global/vs-common.md:259`/`:431` 登记 ⇒ 本行**不重复派单**，只补落地处）· `UNRESOLVED-005` 落 `service/message/message-api`（域侧） |

| `GAP-15` | **派生层 C 类「三态缺档 ⇒ 判据不再认作闭合」3 条** —— `U-03`@like（C/P2 · 3s 超时的 503 出口只核到配置层/装配层，缺真 Tomcat + 慢下游）· `U-05`@like（C/P3 · chunked 请求只验到转换器级判据，缺端到端真容器证据）· `FB-02`@global（C/P2 · A3 form 闸门开关判据的共享面机器校验工具未落） | 🟡 **阶段二承接** —— 三条**同因**：lane 的 verdict 判「差异已消失」，但同一条的**阶段归属**判 C ⇒ ⇒ 三态（差异仍成立 / 差异已消失 / 无法证实）**缺「行为已对齐但验证闭环未补」这一档** ⇒ 本轮派生层规则 `U2-4*` 改为「**只有归属 D 才算闭合**」⇒ 三条**从闭合状态复活成未排期** ⇒ ⇒ 必须有落地处。⚠️ `FB-02` 另有一层矛盾：台账 `docs/DRIFT/global/vs-common.md:373` 写 ✅ 已一致，而 `W8.md:12` 的定级判 C 且明写「剩下那部分 = 机器校验工具 `check-go-form-reachability.py` 未落」⇒ **状态与定级互相打架** ⇒ 须属主订正 | **不计入**阶段一判定（C 类 · 依 `docs/PhaseGoal-judge.md:53-57` §1.1 ⑥ 第 4 条）· 但**阶段二必须有落地处** ⇒ 本行即落地处 | `docs/DRIFT/like/api.md:826`/`:839`（`U-03`）· `:828`/`:841`（`U-05`）· `docs/DRIFT/global/vs-common.md:373`（`FB-02`）· 定级 `docs/DRIFT/_cross/grading/W12.md:23`/`:24`（`U-03`/`U-05`）· `W8.md:12`（`FB-02`） | **阶段二** —— `U-03`/`U-05` 落 `service/like/like-api` 的**真容器测试**（域侧：补「真 Tomcat + 慢下游」与「chunked」两条用例）· `FB-02` 落 `tools/difftest/check-go-form-reachability.py`（**共享面工具** ⇒ G4 写权）⇒ 按 §5.3e 走共享层反馈单；**另** `FB-02` 的状态/定级矛盾 ⇒ **global 域属主订正**（`global/vs-common.md:373` 的 ✅ 与 `W8.md` 的 C 对齐） |

> 🆕 **2026-10-30 · U5-O 显式定级轮（第 1 轮 129 条 + 第 2 轮 55 条 = 184 条逐条 Go 对照）** —— 本类新增 `GAP-07` / `GAP-08`（第 1 轮）/ `GAP-11`（第 2 轮）。
> ⇒ **184 条「状态开放但未定级」已全部逐条与 Go 真源对照并定级**（用户 2026-10-30 拍板口径）
> ⇒ 派生层「**未定级且状态开放**」= 第 1 轮 **129** → 第 2 轮 **55** → **0**。
> ⇒ 第 2 轮专治 **「🟡 部分闭合」被排除在「开放」之外**这个口径漏洞 ⇒ 上一轮报的「= 0」**是假的**。
> ⇒ 新识别的 A/B 类开放条目 = 第 1 轮 **7 条**（4 A + 3 B）＋ 第 2 轮 **6 条**（全 B）⇒ 分三条登记：
> **`GAP-07`** = **3 条已被 `_task-universe.md` 点名**（`U8` 指针口径歧义 ⇒ 不是新漏排）
> · **`GAP-08`** = **4 条依据里无任务指针**（需人工确认）。
> · **`GAP-11`** = **第 2 轮 6 条**（全 B · 不阻阶段一 · 其中 2 条已由 `DEP-03` 覆盖）。
> ⇒ ⚠️ 另有 **95 条「差异已消失」**（第 1 轮 75 + 第 2 轮 20；台账仍写 🔴/🟡 未闭合或部分闭合，但逐条回源后差异早已不存在）
> ⇒ **不是「阶段 2 承接」**，而是**已闭合 · 无待实现项** ⇒ 处置 = **回写台账销账**
> （已随各域台账的「阶段归属定级附录」落文）⇒ 清单见
> [`docs/DRIFT/_cross/stage1-derivation.md`](../DRIFT/_cross/stage1-derivation.md) §4.4。
> ⇒ **证据层**：本次 184 条定级全部为 **B 档静态**（无活体对拍）⇒ 按 **2026-10-30 拍板「声明证据边界、接受静态证据」**
> 执行（见派生层 §5.2 证据边界声明）⇒ **引用这些条目作前提时必须标注「前提未回源」**。
> ⇒ ⚠️ **1 条「无法证实」**（`U-01`）：残余「DC → 第三仓」一跳**两侧都不含 RAG/Go 代码**（对端仓不在本项目范围）⇒ **不是待实现项** ⇒ 归 D。
>
> 🆕 **2026-10-30 · 「同编号冲突」归一（条目身份 = 编号 + 所属域目录）** —— 用户拍板口径。
> ⇒ 前两轮定的 **184 条是「编号级」定级** ⇒ 归一后**按域拆开** ⇒ 那些**原本靠跨域串味「继承」到定级**的条目**重新变成未定级**。
> ⇒ 派生层「**未定级且状态开放**」由 **0** ⇒ **16** ⇒ 登记 **`GAP-12`**（第 4 轮派工范围）。
> ⇒ 同时**修掉 3 个派生层自身的「数字漂亮、内容全错」型 bug**（指针关联恒空 / 表格行反查落到前一个编号 / 假同源边）
> ⇒ 详见 [`docs/DRIFT/_cross/grading/_README.md`](../DRIFT/_cross/grading/_README.md) §七。
> ⇒ **归一效果（可复算）**：① 62 个同源组全部合并为 1 行 ② 判定「不同的事」的 38 条全部按域独立成行。

---

## 3. 环境或权限受阻项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `ENV-01` | **Go 模块缓存 / 参考仓间歇性不可见** | 🔴 同一路径 5 分钟内「可读 → 不可读 → 可读」，且 `ls` 与 `os.path.isdir` 结论互相打脸 ⇒ 判据依赖外部输入时**必须探针先行 + 硬失败退出码 2**，**绝不静默降级成「这条不可核验」** | 任何依赖 Go 真源 / 参考仓的判据（含 `BLK-02` 的双侧活体、DDL 逐列对照） | 本轮计划期实测观察（**仓内无台账出处**）· 退出码口径参照 `tools/difftest/check-ddl-static.py:985-987` | 各轮「防错约束」小节（写入 `round-0*/README.md`）· 受阻时记「未完成任务及原因」 |
| `ENV-02` | **两个仓的 compose 容器名 + 端口 100% 冲突** | 本仓与兄弟仓 `Sea-RideTheWind_java` 都用 `${CONTAINER_PREFIX:-Sea}-*` 与 49848 / 49432 / 49380 / 49090 / 49317 / 49992 / 49100 ⇒ **本来就互斥**；兄弟仓容器已 park 为 `SeaOld-*`（数据卷是匿名卷，未动） | `P0 #5` / `X-12` 的起栈面（Round 1 全部） | `docs/RoundPlan/round-01/README.md:23` | **Round 1** `P1-T02` / `P1-T05`（基础设施前提） |
| `ENV-03` | **本机 Docker 默认镜像源拉大 blob 会 EOF** | 环境面，不是项目缺陷 ⇒ 换 `docker.m.daocloud.io` 后 `docker tag` 回本地名可绕 | Round 1 依赖镜像重建的任何一步 | 本轮计划期实测观察（**仓内无台账出处**） | **Round 1** `P1-T05`（起栈前置）· 受阻时记「未完成任务及原因」 |
| `ENV-04` | **Docker 端口释放延迟** | 容器 `docker rm -f` 后宿主口**仍被 docker-proxy 占住** ⇒ 必须**轮询** `lsof -nP -iTCP:<port> -sTCP:LISTEN` 直到空才起下一个 ⇒ 属**假红来源**之一 | `P0 #5` / Round 1 `P1-T05` 的判读 | `docs/RoundPlan/round-01/README.md:26` · `docs/Pitfalls/global/cross-domain.md:1307`（PIT-GLOBAL-038 端口占用型假红） | **Round 1** `P1-T05`（轮询到空）· 三步定性法 |
| `ENV-05` | **共享 Nacos 服务发现面让并发探针互相污染** | 报 `INTERNAL` / `5001` 是**已知串扰**，**不是缺陷** ⇒ **跑红 ≠ 是缺陷** | 并发探针的结果判读（Round 4 `P4-T08`、Round 6 收口回归） | `docs/Pitfalls/global/cross-domain.md:2503`（PIT-GLOBAL-068） | **Round 4** `P4-T08`（探针 Nacos 隔离） |
| `ENV-07` | **Docker containerd content store I/O 损坏 ⇒ Testcontainers 起不来** | ✅ **已处置（2026-10-31 · Docker 恢复）** —— Docker daemon 已恢复（Server 29.2.1）⇒ ⇒ `docker image inspect redis:7-alpine` 那个此前读不出的 blob（`sha256:6ab0b6e7…`）**现在读得出来了** ⇒ ⇒ 重跑 `GoStyleRedisDeadlineLiveRedisWriteFaceGateTest`：该类真跑 **3 条 / 0F / 0E / 0 skipped** ⇒ ⇒ `service/common` 回归基线回到 **470 / 0F / 0E / 0S**（逐 `TEST-*.xml` 求和，非 stdout 的 BUILD SUCCESS）⇒ ⇒ ⚠️ **本条不可再复现为「环境受阻」**：若 containerd 再损 ⇒ 该类会再次**诚实跳过**⇒ ⇒ 那时的读数**必须写 `skipped`**（不是绿）⇒ ⇒ 载体类守门的判读一律以 surefire XML 为准 | **X-6**（deadline 分层预算与传播）· E66（共享层 Redis 期限那轴）的活体面 · `service/common` 回归基线 | `service/common/target/surefire-reports/TEST-com.rag.common.redis.GoStyleRedisDeadlineLiveRedisWriteFaceGateTest.xml`（skip 原因 = `Assumption failed: live redis 不可用`）· `docker image inspect redis:7-alpine` 的 `input/output error` | **修 Docker Desktop**（containerd 元数据与 snapshotter 已 I/O 损坏 ⇒ 只重启不腾空间会再坏）⇒ ⇒ 修好后重跑该类并把 `skipped` 归 0 ⇒ ⇒ **在此之前 E66 的活体面在本轮无活体读数** |

---

## 4. 等待用户裁定的事项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `DEC-01` | **like 域 `003-C`（暴露形态）** | 🟡 `DRIFT-LIKE-003` 的 **A 段**（事实投递链路）/ **B 段**（权威读语义 Java 化）已闭环，**仅剩 C 段（暴露形态）** ⇒ 已改判 C 类暂缓 `S1-LIKE-001` ⇒ **等部署形态拍板**（原写「scope 过大」的理由**已作废**） | 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 4 条）· ⚠️ **不得据此宣称 like 域「零差异」**（C 类 ≠ 零差异） | `docs/Stage1/05-差异与暂缓项清单.md:490` · `docs/PhaseGoal-judge.md:371` | 等用户拍板 · 登记在 `docs/Stage1/05` 的 11 字段清单 · 本轮不改判 |
| `DEC-02` | **13 条未签字候选**（`CAND` 族 **10** ＋ `like/api.md` 的 **3**）<br>⚠️ **2026-10-29 机器复算订正**：原写 **18 条**（`CAND` 族 14 ＋ `like/api.md` 4）⇒ **现值 13**（2026-10-31 再复算）—— 差额来自：`CAND` 族里 **4 条早已转正为已签字**（`CAND-07`/`CAND-11`/`CAND-12` 等）＋ **1 条已撤销**、`like/api.md` 的 `SD-2026-10-23-08` **已失效**（⇒ 4 → 3）＋ **`CAND` 族新增 1 条未签字候选**（族内 14 → 15 条）⇒ 权威表见 [`docs/Intentional_Deviations/_INDEX.md`](../Intentional_Deviations/_INDEX.md) | ⏳ **未签字** —— `SD-` 而非 `CAND-` 编号的条目原计数漏掉 ⇒ 合计 13 条 ⇒ **不升级拍板**（已登记即为交付物）⇒ 仅登记 | ⚠️ judge §1.1 ⑥ 第 1 条**只豁免「已签字偏离」** ⇒ 这 13 条**尚未签字** ⇒ 不得按已签字豁免；各组件 ⑥ 判定逐条按域台账核 | `docs/Intentional_Deviations/_INDEX.md:28-30` | 仅登记（措辞「🏷 候选，建议签」）· **不进拍板清单** |
| `DEC-03` | **`D-05` `follow-rpc` 的 5 个 `follow_rpc_*` 指标无抓取出口** | 🔴 同款 **5 处**：favorite / message / like / user-user / user-admin（Go 有 DevServer `/metrics`）⇒ 用户 2026-10-24 定「本轮不落地，转全仓 rpc 层统一方案交主会话拍板」⇒ **需重新拍板** | `follow-rpc` **降 R2**（可观察差异）· 跨 **6** 个 rpc 组件 | `docs/PhaseGoal-judge.md:564` · `docs/DRIFT/global/_README.md:340`（E31） | 等用户拍板 · **属阶段二** |
| `DEC-04` | **`P1-T05` 里某个服务真起不来** | 🔴 走 `PIT-GLOBAL-032` 三步定性法判「真失败 / 假红」⇒ **真失败** ⇒ 走 `CT-02`（定级 + 记录来源/证据/影响的判据/所需决策）⇒ **不得当场改产品代码**（规范 §一.6） | 阻塞 `P1-T05` 的「20/20 在服务」判定 ⇒ 连带阻塞 `P3-T02`（P0 #5 行改判）⇒ **可能使 `BLK-01` 持续存在** | `docs/RoundPlan/round-01/README.md` `P1-T05` 失败处理 | 走 `CT-02` ⇒ 落到「暂缓 / 登记 / 转派」三选一 ⇒ **不得**为让矩阵好看而把两个服务塞回同一端口 |
| `DEC-05` | **`P2-T06` 的 E66 定级去向（二选一必须写明）** | 🔴 若定 **A/B** ⇒ 需落地（Lettuce 期限载体 + **规避 Boot 4 `LettuceConnectionConfiguration.createClientOptions` 字节码顺序硬约束** ⇒ 必须自证「`spring.data.redis.timeout` 仍生效」）⇒ 落点在 `service/common/**` ⇒ **G4 写权**；若定 **C** ⇒ 登记进交付物 5 暂缓清单（**11 字段齐全**）⇒ **不计入**阶段一判定 | `X-6` / `P0 #4` / `C-3`（若 E66 被判 A/B） | `docs/DRIFT/global/vs-common.md:31`/`:47` · `docs/DRIFT/global/_README.md:392` | **落地或登记，二选一必须写明**（不能既不落地也不登记）⇒ 落地须与 `P2-T02` **同一次提交** |

---

## 5. 允许暂缓且不阻断阶段一的事项

> ⚠️ 本类全部条目的共同依据 = `docs/PhaseGoal-judge.md:53-57`（§1.1 ⑥ 精确边界 · **第 4 条** = `:57` 那条 C 类 bullet：按 `docs/twoStage.md:954` 定级为 **C 类**的差异与暂缓项 ⇒ **不计入**组件 R3 ⑥）⇒ **不计入阶段一判定**。
> ⚠️ 「不计入」≠「零差异」⇒ 任何复判都**不得**据此宣称某组件「已对齐」。

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `DEP-01` | **共享层 28 项**（23 编号 + 5 未编号） | 🟡 逐条定级 **A 类 0 · B 类 0 · C 类 21 · D 类 6 · 证据缺口 1** ⇒ 域内硬阻塞 = 0 ⇒ 组件 `service/common` R2 → R3 | **依据 §1.1 ⑥ 第 4 条 ⇒ 不计入阶段一判定** | `docs/DRIFT/global/vs-common.md:33` · `docs/DRIFT/common/alignment-2026-10-23.md:652` · `docs/DRIFT/common/alignment-2026-10-23.md:964-972` | 阶段二（`docs/twoStage.md` §5.3 的 11 字段清单）· 本阶段只登记不修 |
| `DEP-02` | **DDL 残留 51 处**（9 域） | 🟡 **C 类 23 · D 类 28 · A 类 0** ⇒ **类型/长度 9 域 0 差异**；`hot` = **N/A** ⇒ **不许写成「已对齐」** | **依据 §1.1 ⑥ 第 4 条 ⇒ 不计入阶段一判定** | `docs/DRIFT/_cross/ddl-type-divergences.md:178` | 阶段二 · 逐域证据在各域 `vs-common.md` §9 |
| `DEP-03` | **`S1-USERS-002`**（B 类部署凭据默认值） | 🟡 **8 个 rpc 模块 / 10 处** `${POSTGRES_PASSWORD:password}` × 9 ＋ `${MINIO_SECRET_KEY:minioadmin}` × 1 ⇒ 落点在**各域自己的 rpc 模块** ⇒ **域侧可自修**（原写「归 G4」是错的） | B 类 ⇒ **不计入** `P0 #8` 判定（残留已登记） | `docs/Stage1/05-差异与暂缓项清单.md:73` · `docs/Stage1/02-阶段1简历项目验收总览.md:51` | **Round 4** `P4-T01` / `P4-T02`（已排期修复 ⇒ 本类只登记「已排期」） |
| `DEP-04` | **`DRIFT-CF-018` favorite 域半边** | 🔴 C 类 —— 共享层半边**已修** ⇒ favorite 域半边**仍成立**：`FavoriteFactDispatchScheduler.java:119` 仍调阻塞版 `Dispatcher.run(...)` ⇒ `once=false` 时句柄永远拿不到 ⇒ `@PreDestroy` 拿到 null ⇒ **进程无法优雅关闭** | C 类 ⇒ **不计入**阶段一判定（`P0 #1`） | `docs/DRIFT/global/vs-common.md:4646`（§4.18.1 共享层修复记录）· `docs/Stage1/06-最小可行实施计划及验收结果.md:158` | **Round 4** `P4-T03`（已排期修复 ⇒ 本类只登记「已排期」） |
| `DEP-05` | **comment 域整张 `report_record` 表（8 列）仅 RAG 有** | ✅ **已签字** `SD-2026-10-24-CAND-02`（2026-10-24 · 用户 Jason）⇒ Go 侧**根本不存在**该表 ⇒ 属**已签字偏离** ⇒ **是决策不是缺陷** | 已签字偏离 ⇒ 不计入 `R3 ⑥`（judge §1.1 ⑥ 第 1 条） | `docs/Intentional_Deviations/comment/rpc.md:67` · `docs/Intentional_Deviations/comment/rpc.md:17` · `service/comment/comment-rpc/src/main/resources/db/migration/V5__create_comment_report_record.sql:26-36` | 阶段二 · **保护项**（各轮「不得顺手修回 Go 行为」约束） |

---

## 6. 阶段一之外的事项

| 编号 | 事项 | 当前状态 | 影响的判据 | 来源（文件:行号） | 去向 / 处置 |
|---|---|---|---|---|---|
| `OUT-01` | **阶段二十二维度生产级审查** | ⬜ 未启动 —— 12 个维度（业务闭环 / 微服务边界 / 服务发现与 RPC / 超时重试熔断 / 数据一致性 / 认证授权 / 性能容量资源 / 日志指标追踪 / 测试覆盖质量 / 构建部署配置发布 / 备份恢复灾难 / 可维护性）**逐维**完整审查，**不得删减或合并** | **不计入** `S1-1` ~ `S1-6`（阶段一判据不含生产级审查） | `docs/twoStage.md:517` · `docs/twoStage.md:521-532` · `docs/twoStage.md:59` | 阶段二 §6.3 · 阶段一**不预装**该清单 |
| `OUT-02` | **§九 故障矩阵**（生产流量 / SLO / 灾备演练） | ⬜ 未启动 | **不计入**阶段一 | `docs/twoStage.md:898` · `docs/twoStage.md:74-78`（`P2` 原文） | 阶段二 |
| `OUT-03` | **§6.1 重新建立最终改造范围** | ⬜ 未启动 —— ⚠️ 原文「**不得假定阶段 1 的暂缓清单已经包含所有缺口**」⇒ 第 5 类清单**不可**被当成阶段二的完整输入 | **不计入**阶段一 | `docs/twoStage.md:460` · `docs/twoStage.md:473` | 阶段二 2A |
| `OUT-04` | **Go 真源范围外三域**（`knowledge` / `points` / `security`） | ⚪ **R0 = 范围外** ⇒ Go 侧无对应件 ⇒ 本仓不实现 ⇒ **是决策不是待办**（⚠️ 不得列为整改任务） | **不计入**任何组件的必相对齐项 · **不计入**阶段一 | `docs/PhaseGoal-judge.md:46` · `docs/DRIFT/_cross/proto-verification.md:46` | **不做** |
| `OUT-05` | **`P1` 维度 3 / 7 / 8** | ⬜ 阶段一未覆盖 —— 维度 3（服务发现与 RPC 通信）里的 **proto 消费方式取证**（= `GAP-01`）；维度 7（**性能、容量与资源管理**）；维度 8（日志、指标、追踪与告警）里的 **rpc 层指标出口**（= `DEC-03`） | `P1` 是「简历竞争力要求」，**不构成阶段一阻塞**（`docs/PhasedGoal.md:54-68`） | `docs/PhasedGoal.md:54` · `docs/twoStage.md:523` · `docs/twoStage.md:527` · `docs/twoStage.md:528` | 阶段二 · 其中 proto 消费方式的取证已提前排进 **Round 5** `P5-T01` |
| `OUT-06` | **`P2` 全部** | ⬜ 「暂不作为简历项目阻塞条件」（完整生产级 SLO 与事故管理体系 / 基于真实生产流量的容量规划 / 定期灾难恢复演练 / 完整灰度发布与自动回滚 / 穷尽式验证） | **不计入**阶段一 | `docs/PhasedGoal.md:70-78` | 阶段二 |

---

## § 自检

| 类 | 条数 | 编号 |
|---|---|---|
| 1. 阶段一阻断项 | **3** | `BLK-01` · `BLK-02` · `BLK-03` |
| 2. 证据缺口 | **15** | `GAP-01` ~ `GAP-15` |
| 3. 环境或权限受阻项 | **6** | `ENV-01` ~ `ENV-05` · `ENV-07` |
| 4. 等待用户裁定的事项 | **5** | `DEC-01` ~ `DEC-05` |
| 5. 允许暂缓且不阻断阶段一的事项 | **5** | `DEP-01` ~ `DEP-05` |
| 6. 阶段一之外的事项 | **6** | `OUT-01` ~ `OUT-06` |
| **合计** | **40** | — |

- **6 类齐全，无遗漏**（类名一字不改、顺序不变 ⇒ 对齐规范 §11.6）。
- 每条 6 列（`编号 / 事项 / 当前状态 / 影响的判据 / 来源（文件:行号） / 去向 / 处置`），「影响的判据」均落到具体判据编号（`P0 #n` / `X-n` / `C-n` / `S1-n` / `R3 ⑥` / `D-05` / `G-n` / `U-n` / `DRIFT-*`）。
  ⇒ **2026-10-30 第 4 轮修**：`GAP-10` 行原本是 **7 列**（多一格 ⇒ 后面各列整体错位）⇒ 已并回 6 列 ⇒ 全表 38 行**列数一致**（机器校验：列数 ≠ 6 的行 = 0）。
- **6 类互不重叠**：A 类 3 条；证据缺口 15 条（含 1 条仓外不可核验 `GAP-03`）；环境受阻 6 条；真正需拍板 **5** 条（其中 `DEC-02` 按口径说明第 4 条**仅登记**；`DEC-04`/`DEC-05` 走 `CT-02` 定级路径）；暂缓 5 条；阶段一之外 6 条。
- **只有第 1 类阻阶段一**；第 5 / 6 类均写明「依据 §1.1 ⑥ 第 4 条 ⇒ 不计入阶段一判定」或等价依据。
- **本清单不含结论句**（无任何「已通过 / 已对齐」类断言）。
- 🆕 **排期指针时效（2026-10-30 第 4 轮）** —— 已了结项 = `GAP-10` · `GAP-12` ⇒ 指向它们的指针**失效** ⇒ 补登 `GAP-13`（2 条 B 类）+ `GAP-14`（3 条 C 类）⇒ **派生层「真缺口 A/B 未排期」回到 0 是有据的**（此前那个 0 是**指针失效造成的假零**）。
  ⇒ 另补 `GAP-15`（3 条 C 类 · 三态缺档导致「从闭合复活成未排期」：`U-03` · `U-05` · `FB-02`）⇒ 派生层「真缺口 C/D 未排期」的**完整路径** = **88 → 93**（`U8d` 指针失效 ⇒ 5 条掉出排期）**→ 90**（`GAP-14` 补回 3 条 C）**→ 88**（`GAP-15` 补回 3 条复活项；另 `FB-02` 不计桶因状态 ✅ 已一致）。
