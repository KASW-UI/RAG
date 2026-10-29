# 阶段一施工单派生层 · 三目录台账 → 施工单（2026-10-30）

> **本文件是「派生层」** —— 每个值都**从** `docs/DRIFT` / `docs/Intentional_Deviations` /
> `docs/Pitfalls` **投影**而来，**不重新裁定任何结论**（每个派生值都带 `rules` 规则号）。
> **机器层** = [`stage1-derivation.tsv`](stage1-derivation.tsv)（全量问题项 × 16 列）
> **生成器 + 守门** = [`tools/ledger-derive/derive-stage1.py`](../../../tools/ledger-derive/derive-stage1.py)
> （`--check` 模式：源台账改了而本层没重生成 ⇒ **EXIT=1**）
> **规范来源** = [`docs/twoStage.md`](../../twoStage.md) §10（11 字段 · P0-P3 × A-D）＋ §5.3（暂缓项 11 字段）

---

## 一、口径（机器可复算）

### 1.1 一句话结论

> **三目录**有 **1174** 个带状态的问题项，
> 其中只有 **334** 个（**28%**）带阶段归属，
> **0** 条是**开放状态**（🔴 未闭合 / 仍生效 / ⏳ 未签字）却**没有阶段归属** ——
> **它们既不在施工单里，也没有明确推给阶段 2**。这就是「台账不能当施工单」的量化原因。
> 证据面上：有活体证据的 **130** 条里，**只有 99 条**的活体证据是**绿**（支持当前结论），
> 其余 **31** 条的证据本身是**红 / 环境问题 / 混合** ⇒ **「有活体证据」≠「结论被证实」**。
> 本派生层把这几条数字**变成可复算的断言**，并把它们排成**可开工的清单**（§四）。

### 1.2 覆盖面（旧口径的盲区）

| 事实 | 数字 | 依据 |
|---|---|---|
| 三目录条目（**宽口径**扫描） | **2661** 条 / **1371** 个唯一编号 | U1 |
| 其中**问题项**（自身带状态标记） | **2091** 条 / **1174** 个唯一编号 | U1 + U2 |
| 旧口径（`ledger-claim-audit.py` 的 `ID_RE` 前缀白名单）能认出 | **766** / 1174 | ⚠️ **漏 408 个唯一编号** |
| 施工单任务（`_task-universe.md`） | 37 条 | §〇 口径 |
| 施工单 ↔ 台账条目**强链** | **49** 个条目被回指 | U8 |

> ⚠️ **上一轮报给用户的「1183 条目 / 707 唯一 ID」是**旧口径**（前缀白名单）**的数字**
> ⇒ 本派生层用**宽口径**重扫 ⇒ **2091 条问题项 / 1174 个唯一编号**。
> **差额不是「多算了」** —— 是旧口径**认不出**整族编号（`MISMATCH-API-*` / `OP-*` / `T-*` / `N-*` /
> `CD-*` / `ADJ-*` / `A-*` / `B-*` / `C-*` / **`E60` 这类「字母+数字」形态** …）。
> ⇒ **「落空 0」这类结论的覆盖面小于表面**（同族：弱判据把真缺陷洗成 0）。

### 1.3 U1 · 条目（问题项）识别

```
位置候选 = 二~五级标题（首个 token；**不在首位时扫全文取第一个编号**）· 表格首列
编号形态 = ^[A-Z][A-Za-z0-9]{0,14}(-[A-Za-z0-9]{1,24})*-\d{1,4}[A-Za-z]?$  |  ^E\d{1,3}$
排除     = tools/ledger-derive/excluded-id-prefixes.txt（显式数据文件，逐条带理由）
必须     = 自身带状态标记（取值域按目录限定，见 U2）
分段     = 互不重叠（每个候选的段到下一个候选为止）—— 否则同一个 🔴 会被数两次
```

### 1.4 派生规则表（R1~R8，每条都能复核）

| 规则 | 派生什么 | 判据 | 依据 |
|---|---|---|---|
| **U2 状态** | 处理状态 | ①「现行态 = X」显式声明优先 ②标题行 ③条目段；取值域**按目录**限定 | 三目录各自的取值域约定 + `PhaseGoal-judge.md` §1.5 L0 |
| **U3 证据强度** | A 活体 / B 静态 / C 无指针 | 段内点名**已在活体台账实跑过**的探针 ⇒ A；否则有 `文件:行号` ⇒ B；否则 C | `live-parity-2026-10-30.md` §二 |
| **U4 优先级** | P0-P3 | 台账已写 ⇒ 用台账的；否则 A 类⇒P0 · 🔴/仍生效/⏳未签字⇒P1 · 🟡/待核实⇒P2 · 其余⇒P3 | `twoStage.md` §10 |
| **U5 阶段归属** | A-D | ①台账里的**十一项表** ②共享层定级表 ③`Stage1/05` ④已签字偏离⇒D ⑤条目**自己正文**的归属声明 | `twoStage.md` §10 |
| **U5-O 显式定级叠加层** | A-D | `docs/DRIFT/_cross/grading/*.md` 里**逐条与 Go 源码对照后**的人工定级 ⇒ **优先级最高**（压过 U5-0 的历史表格判定）· **依据列必须含 Go + RAG 双侧 `文件:行号`**，否则整行丢弃 | `docs/DRIFT/_cross/grading/` |
| **U6 是否阻断** | 是/否 | ①权威表的「阻断」列 ②条目正文自己的「不阻断阶段 1」声明 ③A⇒是 | `Stage1/05` 字段 8 |
| **U7 最终目标影响** | 影响/不影响/间接/未判定 | 台账已写 ⇒ 用台账的；否则 C⇒影响 · D⇒不影响 · A/B⇒间接 | `twoStage.md` §10 |
| **U8 排期指针** | 任务 ID ｜ BLK/GAP/ENV/DEC-xx | 任务块里**直接写了本条目编号**，或任务块的**台账指针落在本条目段内** | `_task-universe.md` + `_unresolved.md` |
| **U9 证据缺口** | 缺口描述 | C⇒无可复算指针 · B+🔴⇒静态取证·缺活体 · 未定级⇒未定级⇒未排期 · 未排期⇒未排期 | — |

### 1.5 排除清单（`excluded-id-prefixes.txt`）

| 前缀 | 理由 |
|---|---|
| `CASE-` | 测试场景矩阵行（users/admin/api.md §case 表 · users/user/rpc.md §case 表 · hot/rpc.md §case 表）—— 记录的是「某个场景两侧是否一致」的**测试结果**，属**证据层**；真正的差异条目会引用 CASE 编号作为自己的证据 ⇒ 把它当独立「问题项」会重复计数 |

---

## 二、施工单总表（**能直接开工的部分**）

> 本节把三处已有的施工单**并成一张表**：
> ① `_task-universe.md` 的 **37 条任务**（计划任务 ID）· ② `_unresolved.md` 的 **14 条未解决项**
> ③ 派生层里**已定级但未排期**的台账条目 ⇒ **凡要动手的，这里都能查到去向**。

### 2.1 计划任务（`_task-universe.md`）→ 覆盖的台账条目

| 计划 ID | 标题 | 阶段类 | 阶段一影响 | 覆盖的台账条目（派生链） |
|---|---|---|---|---|
| `P1-T01` | 全服务起栈（P0 #5 闭合） | **A** | **① 阻断阶段一** —— P0 #5 主语是「**项目**」不是「某个域」；`READM | `DRIFT-HOTAPI-030` · `PIT-GLOBAL-032` · `PIT-GLOBAL-038` |
| `P1-T02` | 提供宿主机可解析的中间件地址（容器内值与宿主机值两套） | **A** | ① **阻断阶段一**（A 类 · 与 `P1-T01` 同一条 P0 #5） | `PIT-GLOBAL-038` |
| `P1-T03` | 端口错开 + 硬编码端口 env 化 | **A** | ① **阻断阶段一**（A 类 · 与 `P1-T01` 同一条 P0 #5） | `PIT-GLOBAL-038` |
| `P1-T04` | 订正 README §三 与 `.env.example` 标题 | **A** | ① **阻断阶段一**（A 类 · 与 `P1-T01` 同一条 P0 #5） | （未回指台账条目） |
| `P1-T05` | 真起 20 个服务，逐个确认「在服务」 | **A** | ① **阻断阶段一**（A 类） | `E65` · `PIT-GLOBAL-032` · `PIT-GLOBAL-038` |
| `P2-T01` | E65 —— 出向 gRPC 不继承请求 deadline（Go 立刻失败 / RAG  | **A** | **① 阻断阶段一** —— 命中 `P0 #4`「核心失败路径具有明确行为，不会无声吞掉关 | `C-2` · `C-3` · `E65` · `E66` · `PIT-GLOBAL-040` · `PIT-GLOBAL-048` · `PIT-GLOBAL-054` |
| `P2-T02` | E66 —— Redis 调用不绑 deadline | **B** | **② 缺验收证据**（证据只有 L1）⇒ 按 Jason 2026-10-29 裁定：** | `DRIFT-ARPC-010` · `DRIFT-USERAPI-013` · `E65` · `E66` · `FB-05` · `FB-06` |
| `P2-T03` | 各域裸 stub 调用点改用 `applyTo(stub)` | **A** | ① **阻断阶段一**（A 类 · E65 的域侧半边） | `E65` · `PIT-GLOBAL-040` |
| `P2-T04` | 11 个「零 deadline」类补 2000ms 硬上界 | **A** | ① **阻断阶段一**（A 类） | `E65` |
| `P2-T05` | 守门改造：漂移形态守门 → 等值守门（+ 变异重做） | **A** | ① **阻断阶段一**（A 类 · X-6 的判定口径） | `E48` · `E62` · `E65` · `PIT-GLOBAL-018` · `PIT-GLOBAL-048` |
| `P2-T06` | E66：先补双侧活体证据，再定级，再落地或登记 | **B** | **② 缺验收证据** ⇒ 按 Jason 2026-10-29 裁定：**与 E65 同轮 | `DRIFT-ARPC-010` · `DRIFT-USERAPI-013` · `E65` · `E66` · `FB-05` · `FB-06` |
| `P3-T01` |  | **A** |  | `E65` |
| `P3-T02` |  | **A** |  | （未回指台账条目） |
| `P3-T03` |  | **A** |  | （未回指台账条目） |
| `P3-T04` |  | **A** |  | `C-2` · `C-3` |
| `P3-T05` |  | **A** |  | （未回指台账条目） |
| `P3-T06` |  | **A** |  | （未回指台账条目） |
| `P3-T07` |  | **B** |  | （未回指台账条目） |
| `P3-T08` |  | **A** |  | （未回指台账条目） |
| `P3-T09` |  | **A** |  | （未回指台账条目） |
| `P4-T01` |  | **C** |  | `S1-USERS-002` |
| `P4-T02` |  | **C** |  | `S1-USERS-002` |
| `P4-T03` |  | **C** |  | `DRIFT-CF-018` |
| `P4-T04` |  | **C** |  | （未回指台账条目） |
| `P4-T05` |  | **C** |  | （未回指台账条目） |
| `P4-T06` |  | **C** |  | （未回指台账条目） |
| `P4-T07` |  | **C** |  | （未回指台账条目） |
| `P4-T08` |  | **C** |  | `PIT-GLOBAL-068` |
| `P4-T09` |  | **C** |  | `D-08` |
| `P5-T01` |  | **B** |  | `G-19` |
| `P5-T02` |  | **B** |  | `S1-USERS-001` · `S1-USERS-003` |
| `P5-T03` |  | **B** |  | `CD-1` · `CD-10` · `DRIFT-HOTAPI-030` |
| `P5-T04` |  | **B** |  | `DRIFT-HOTAPI-030` |
| `P5-T05` |  | **B** |  | （未回指台账条目） |
| `P5-T06` |  | **B** |  | （未回指台账条目） |
| `P6-T01` |  | **A** |  | （未回指台账条目） |
| `P7-T01` |  | **A** |  | `C-1` · `C-4` · `D-08` |

### 2.2 未解决项（`_unresolved.md`）

| 编号 | 阶段类 | 影响的台账条目 |
|---|---|---|
| `BLK-01` | A | （未回指台账条目） |
| `BLK-02` | A | `C-2` · `C-3` · `E65` |
| `BLK-03` | A | `A-2` · `DRIFT-FOLLOW-008` · `S1-FOLLOW-007` |
| `DEC-01` | — | `DRIFT-LIKE-003` |
| `DEC-02` | — | `SD-2026-10-23-08` |
| `DEC-03` | — | `E31` |
| `DEC-04` | — | `PIT-GLOBAL-032` |
| `DEC-05` | — | `C-3` · `E66` |
| `ENV-01` | — | （未回指台账条目） |
| `ENV-02` | — | （未回指台账条目） |
| `ENV-03` | — | （未回指台账条目） |
| `ENV-04` | — | `PIT-GLOBAL-038` |
| `ENV-05` | — | `PIT-GLOBAL-068` |
| `GAP-01` | B | `G-19` |
| `GAP-02` | B | （未回指台账条目） |
| `GAP-03` | B | `U-01` |
| `GAP-04` | B | `U-2` |
| `GAP-05` | B | `S1-FOLLOW-008` |
| `GAP-06` | B | `S1-FOLLOW-009` |
| `GAP-07` | B | `B-10` · `B-11` · `E65` · `G4-A4-05` |
| `GAP-08` | B | `DRIFT-ARPC-009` · `DRIFT-USERSCOMMON-006` · `E65` · `G4-A4-01` · `G4-A4-07` |
| `GAP-09` | B | `E2` · `E7` · `S1-FOLLOW-004` · `S1-USERS-004` |
| `GAP-10` | B | `E1` · `H-1` · `L-4` · `T-2` · `T-6` |
| `GAP-11` | B | `A-5` · `C-5` · `DRIFT-MSG-V-032` · `F-6` · `G4-15` · `G4-A4-03` · `G4-A4-04` · `S1-USERS-002` |

---

## 三、阶段归属分布

| 归属 | 条目数 | 含义 |
|---|---|---|
| **A** | 15 | 阶段 1 准入阻断项（不处理就无法通过简历项目验收） |
| **B** | 29 | 阶段 1 竞争力补强项（技术亮点与验证闭环） |
| **C** | 87 | 阶段 2 最终工程建设项（业务语义恢复 / 生产工程机制） |
| **D** | 203 | 经分析确认可不实现或不适用（须有明确理由） |
| **—** | 420 | **未派生 —— 待人工定级**（不是「不重要」，是「还没定级」） |

| 优先级 | 条目数 |
|---|---|
| P0 | 16 |
| P1 | 380 |
| P2 | 100 |
| P3 | 678 |

---

## 四、真缺口（**可开工清单**）

> 判据 = **状态开放**（🔴 未闭合 / 仍生效 / ⏳ 未签字）**且** `排期指针 = 未排期`
> ⇒ **不是「没发现」，是「发现了但没进施工单」** —— 旧口径下它们**不可见**。

**合计 523 条** —— ① **A/B 类**（阶段 1 范围内）**0** 条
· ② C/D 类（**阶段 2 承接**，§5.3 暂缓项清单已登记）**114** 条
· ③ 未定级 **0** 条
· ④ **已闭合**（逐条 Go 对照后「**差异已消失**」⇒ **无待实现项**）**91** 条

> ⚠️ **第 ④ 档必须与 C/D 分开** —— 实测（W1/W5 轮）：台账里大量条目仍写 🔴 未闭合，
> 但逐条回源后**差异早已不存在**（只是台账没销账）⇒ 它们**不是**「阶段 2 承接」，
> 而是**已闭合、无待实现项** ⇒ 混进阶段二工作量清单会把那份清单变成**假待办**。
> ⇒ 本档的处置 = **回写台账销账**（状态订正），**不进任何阶段的工作量**。

### 4.1 ① A/B 类 · 开放且未排期（**真缺口 —— 阶段 1 范围内的漏排**）

> **A 类 = 阶段 1 准入阻断项**（不处理就无法通过简历项目验收）·
> **B 类 = 阶段 1 竞争力补强项**。
> 两条都**不在** `_task-universe.md` 的 37 条任务里 ⇒ **施工单漏了它们**。

| 归属 | 优先级 | ID | 域 | 状态 | 证据 | 证据缺口 | 源位置 |
|---|---|---|---|---|---|---|---|

> C/D 类 **114** 条另有去向（阶段 2 恢复方案已在各条台账的 §5.3 十一项表里）
> ⇒ **不计入阶段一判定**（`PhaseGoal-judge.md` §1.1 ⑥ 第 4 条）⇒ 不列。

### 4.4 ④ 已闭合（**逐条 Go 对照后差异已消失 · 无待实现项**）

> **不是「阶段 2 承接」** —— 是台账**没销账**。处置 = **回写台账订正状态**。

| 归属 | ID | 域 | 台账状态 | 源位置 |
|---|---|---|---|---|
| C | `FB-02` | global | 🟡 部分闭合 | `DRIFT/global/vs-common.md:373;DRIFT/global/vs-common.md:862;DRIFT/global/vs-common.md:1580` |
| C | `U-03` | like | 🟡 部分闭合 | `DRIFT/like/api.md:826;DRIFT/like/api.md:839` |
| C | `U-05` | like | 🟡 部分闭合 | `DRIFT/like/api.md:828;DRIFT/like/api.md:841` |
| D | `E20` | global | 🔴 未闭合 | `DRIFT/global/_README.md:368` |
| D | `E22` | global | 🔴 未闭合 | `DRIFT/global/_README.md:370` |
| D | `DRIFT-MSG-V-027` | message | 🔴 未闭合 | `DRIFT/message/rpc.md:2110` |
| D | `MISMATCH-API-01` | comment | 🔴 未闭合 | `DRIFT/comment/api.md:52;DRIFT/comment/api.md:88;DRIFT/comment/api.md:293` |
| D | `E23` | global | 🔴 未闭合 | `DRIFT/global/_README.md:371` |
| D | `E30` | global | 🔴 未闭合 | `DRIFT/global/_README.md:378` |
| D | `E46` | global | 🔴 未闭合 | `DRIFT/global/_README.md:421;DRIFT/global/_README.md:452` |
| D | `E47` | global | 🔴 未闭合 | `DRIFT/global/_README.md:426;DRIFT/global/_README.md:858;DRIFT/users/admin/rpc.md:3335` |
| D | `E50` | global | 🔴 未闭合 | `DRIFT/global/_README.md:438` |
| D | `T-16` | global | 🟡 部分闭合 | `DRIFT/global/common-tails.md:598` |
| D | `DRIFT-MSG-V-020` | message | 🔴 未闭合 | `DRIFT/message/rpc.md:1671` |
| D | `SD-04` | message | 🔴 未闭合 | `DRIFT/message/proto.md:34` |
| D | `DRIFT-ADMINAPI-012` | users | 🟡 部分闭合 | `DRIFT/users/admin/api.md:2236` |
| D | `G4-11` | article | 🟡 部分闭合 | `DRIFT/article/vs-common.md:296` |
| D | `G4-3` | article | 🟡 部分闭合 | `DRIFT/article/vs-common.md:276;DRIFT/comment/vs-common.md:79;DRIFT/favorite/vs-common.md:110` |
| D | `DRIFT-003` | common | 🟡 部分闭合 | `DRIFT/common/protection.md:89;DRIFT/message/api.md:79;DRIFT/users/user/api.md:35` |
| D | `E10` | global | 🔴 未闭合 | `DRIFT/global/_README.md:341` |
| D | `E18` | global | 🔴 未闭合 | `DRIFT/global/_README.md:358` |
| D | `E24` | global | 🔴 未闭合 | `DRIFT/global/_README.md:372` |
| D | `E25` | global | 🔴 未闭合 | `DRIFT/global/_README.md:373` |
| D | `E26` | global | 🔴 未闭合 | `DRIFT/global/_README.md:374` |
| D | `E32` | global | 🔴 未闭合 | `DRIFT/global/_README.md:380` |
| D | `E40` | global | 🔴 未闭合 | `DRIFT/global/_README.md:418` |
| D | `E41` | global | 🔴 未闭合 | `DRIFT/global/_README.md:419` |
| D | `E44` | global | 🔴 未闭合 | `DRIFT/global/_README.md:424` |
| D | `E51` | global | 🔴 未闭合 | `DRIFT/global/_README.md:439` |
| D | `E52` | global | 🔴 未闭合 | `DRIFT/global/_README.md:440` |
| D | `E53` | global | 🔴 未闭合 | `DRIFT/global/_README.md:441` |
| D | `E55` | global | 🔴 未闭合 | `DRIFT/global/_README.md:443` |
| D | `E56` | global | 🔴 未闭合 | `DRIFT/global/_README.md:444` |
| D | `E61` | global | 🔴 未闭合 | `DRIFT/global/_README.md:449;DRIFT/global/_README.md:839` |
| D | `E63` | global | 🔴 未闭合 | `DRIFT/global/_README.md:450` |
| D | `E64` | global | 🔴 未闭合 | `DRIFT/global/_README.md:429` |
| D | `E69` | global | 🔴 未闭合 | `DRIFT/global/_README.md:434;DRIFT/global/_README.md:563` |
| D | `E70` | global | 🔴 未闭合 | `DRIFT/global/_README.md:435` |
| D | `E9` | global | 🔴 未闭合 | `DRIFT/global/_README.md:340` |
| D | `G4-19` | hot | 🟡 部分闭合 | `DRIFT/hot/vs-common.md:92` |
| D | `DRIFT-LIKE-018` | like | 🟡 部分闭合 | `DRIFT/like/rpc.md:898;DRIFT/like/rpc.md:1289` |
| D | `DRIFT-INFRA-001` | _cross | 🔴 未闭合 | `DRIFT/_cross/open-items.md:59` |
| D | `A-4` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:363` |
| D | `DRIFT-001` | article | 🔴 未闭合 | `DRIFT/article/api.md:877;DRIFT/article/api.md:894;DRIFT/common/deadline.md:2` |
| D | `F-3` | article | 🔴 未闭合 | `DRIFT/article/api.md:1291;DRIFT/favorite/vs-common.md:225` |
| D | `FIX-06` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:1161;DRIFT/article/rpc.md:1207` |
| D | `FIX-08` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:1345;DRIFT/article/rpc.md:1394` |
| D | `FIX-10` | article | 🔴 未闭合 | `DRIFT/article/api.md:1839;DRIFT/article/api.md:1914` |
| D | `FIX-11` | article | 🔴 未闭合 | `DRIFT/article/api.md:1966;DRIFT/article/api.md:1980` |
| D | `FIX-17` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:1875;DRIFT/article/rpc.md:1950` |
| D | `G4-10` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:295` |
| D | `G4-12` | article | 🟡 部分闭合 | `DRIFT/article/vs-common.md:297` |
| D | `G4-2` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:275;DRIFT/comment/vs-common.md:78;DRIFT/favorite/vs-common.md:109` |
| D | `G4-6` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:279;DRIFT/favorite/vs-common.md:112` |
| D | `G4-7` | article | 🟡 部分闭合 | `DRIFT/article/vs-common.md:281` |
| D | `G4-9` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:293` |
| D | `L-10` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:100;DRIFT/article/vs-common.md:104;DRIFT/article/vs-common.md:191` |
| D | `L-6` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:76;DRIFT/favorite/vs-common.md:89` |
| D | `L-7` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:97;DRIFT/favorite/vs-common.md:91` |
| D | `L-8` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:98;DRIFT/favorite/vs-common.md:94` |
| D | `Latin-1` | article | 🔴 未闭合 | `DRIFT/article/api.md:2302` |
| D | `O-5` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:129` |
| D | `S-5` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:2400;DRIFT/article/rpc.md:2606` |
| D | `U-9` | article | 🟡 部分闭合 | `DRIFT/article/rpc.md:2510` |
| D | `PIT-GLOBAL-051` | comment | 🟡 部分闭合 | `DRIFT/comment/vs-common.md:29;Pitfalls/global/cross-domain.md:2014` |
| D | `T-10` | common | 🔴 未闭合 | `DRIFT/common/alignment-2026-10-23.md:697;DRIFT/common/alignment-2026-10-23.md:718;DRIFT/global/_README.md:97` |
| D | `E68` | favorite | 🔴 未闭合 | `DRIFT/favorite/vs-common.md:30;DRIFT/global/_README.md:433;DRIFT/global/_README.md:541` |
| D | `PITFALL-FOLLOW-005` | follow | 🔴 未闭合 | `DRIFT/follow/vs-common.md:300;DRIFT/follow/vs-common.md:424;DRIFT/follow/vs-common.md:1215` |
| D | `B-05` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3841` |
| D | `CD-7` | global | 🟡 部分闭合 | `DRIFT/global/cross-domain.md:47;DRIFT/global/cross-domain.md:141` |
| D | `DRIFT-018` | global | 🔴 未闭合 | `DRIFT/global/lanes/L9-interrupt.md:260;DRIFT/users/_shared.md:67` |
| D | `E34` | global | 🟡 部分闭合 | `DRIFT/global/_README.md:383` |
| D | `FB-01` | global | 🟡 部分闭合 | `DRIFT/global/vs-common.md:352;DRIFT/global/vs-common.md:1527;DRIFT/like/vs-common.md:80` |
| D | `FB-03` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:402;DRIFT/like/vs-common.md:439;DRIFT/like/vs-common.md:448` |
| D | `G4-A4-02` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3752;DRIFT/global/vs-common.md:3774` |
| D | `L4-01` | global | 🔴 未闭合 | `DRIFT/global/lanes/L4-flyway.md:172` |
| D | `L4-03` | global | 🟡 部分闭合 | `DRIFT/global/lanes/L4-flyway.md:174` |
| D | `T-13` | global | 🔴 未闭合 | `DRIFT/global/common-tails.md:561;DRIFT/global/lanes/L1-delivery.md:296` |
| D | `UTF-8` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:949` |
| D | `H-4` | hot | 🟡 部分闭合 | `DRIFT/hot/vs-common.md:70` |
| D | `OP-09` | like | 🔴 未闭合 | `DRIFT/like/rpc.md:504;DRIFT/message/rpc.md:505` |
| D | `D-015` | users | 🔴 未闭合 | `DRIFT/users/user/api.md:1973` |
| D | `DRIFT-ADMINAPI-013` | users | 🔴 未闭合 | `DRIFT/users/admin/api.md:2448` |
| D | `DRIFT-ARPC-008` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:1302` |
| D | `DRIFT-ARPC-014` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:4435` |
| D | `DRIFT-USERAPI-017` | users | 🔴 未闭合 | `DRIFT/users/user/api.md:3635` |
| D | `DRIFT-USERRPC-V-001` | users | 🔴 未闭合 | `DRIFT/users/user/rpc.md:473;DRIFT/users/user/rpc.md:1360` |
| D | `DRIFT-USERRPC-V-016` | users | 🔴 未闭合 | `DRIFT/users/user/rpc.md:2714` |
| D | `Round-2` | users | 🔴 未闭合 | `DRIFT/users/vs-common.md:5072` |
| D | `UNRESOLVED-ADMINAPI-005` | users | 🟡 部分闭合 | `DRIFT/users/admin/api.md:1005;DRIFT/users/admin/api.md:1588` |
| D | `V-004` | users | 🔴 未闭合 | `DRIFT/users/_shared.md:777` |

### 4.2 ③ 未定级（**需人工定级才能进施工单**）

> 派生层**不猜**归属 ⇒ 这一档必须人工定级。
> 排序 = **优先级 × 证据强度**（P1 且证据 C = 既危险又不可复算 ⇒ 排最前）。

| 优先级 | 证据 | ID | 域 | 状态 | 源位置 |
|---|---|---|---|---|---|

### 4.5 同编号多态（**同一编号在多处登记、状态不一致**）

> 这是派生层**刻意不替人做决定**的地方：同一编号在多个文件登记，状态可能不同。
> 合并口径 = **取最新核对日期的那条**（`PhaseGoal-judge.md` §1.5 L0）；
> 冲突条数 = **登记处状态取值不同**的编号数 ⇒ **需人工归一**。

| ID | 登记处数 | 出现过的状态 | 采纳的状态 | 源位置 |
|---|---|---|---|---|
| `D-1` | 7 | ⛔ 已失效 / ✅ 仍成立 / ✅ 已一致 / 🔴 未闭合 | ⛔ 已失效 | `DRIFT/like/vs-common.md:67;DRIFT/message/vs-common.md:69;DRIFT/task/vs-common.md:61` |
| `N-1` | 4 | ⏳ 未签字 / ✅ 仍成立 / ✅ 已一致 / 🔴 未闭合 | ✅ 仍成立 | `DRIFT/global/lanes/L10-ddldeep.md:332;DRIFT/users/user/api.md:2311;Intentional_Deviations/users/admin/_shared.md:65` |
| `A-1` | 4 | ✅ 仍成立 / 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/article/vs-common.md:360;DRIFT/users/admin/_shared.md:217;DRIFT/users/admin/api.md:1329` |
| `A-2` | 5 | ✅ 仍成立 / ✅ 已一致 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/article/rpc.md:2551;DRIFT/article/vs-common.md:361;DRIFT/favorite/rpc.md:1662` |
| `C-1` | 3 | ✅ 仍成立 / ✅ 已一致 / 🔴 未闭合 | ✅ 仍成立 | `DRIFT/comment/vs-common.md:57;DRIFT/comment/vs-common.md:218;Intentional_Deviations/common/_signed-deviations.md:156` |
| `CD-6` | 6 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/global/_README.md:137;DRIFT/global/cross-domain.md:46;DRIFT/global/cross-domain.md:435` |
| `D-2` | 4 | ✅ 已一致 / 待核实 / 🔴 未闭合 | 待核实 | `DRIFT/like/vs-common.md:68;DRIFT/message/vs-common.md:70;DRIFT/task/vs-common.md:62` |
| `DRIFT-CF-018` | 3 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/common/alignment-2026-10-23.md:661;DRIFT/common/alignment-2026-10-23.md:1050;DRIFT/global/vs-common.md:4646` |
| `DRIFT-LIKE-003` | 4 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/like/rpc.md:151;DRIFT/like/rpc.md:1395;DRIFT/like/rpc.md:1829` |
| `DRIFT-MSG-V-030` | 12 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/article/api.md:2590;DRIFT/comment/api.md:366;DRIFT/follow/vs-common.md:1201` |
| `DRIFT-USERAPI-013` | 5 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/users/user/api.md:624;DRIFT/users/user/api.md:3283;DRIFT/users/user/api.md:3590` |
| `E60` | 5 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/global/_README.md:448;DRIFT/like/rpc.md:1597;DRIFT/like/rpc.md:1742` |
| `E62` | 9 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🔴 未闭合 | `DRIFT/favorite/rpc.md:3111;DRIFT/favorite/vs-common.md:28;DRIFT/global/_README.md:428` |
| `FB-01` | 5 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/global/vs-common.md:352;DRIFT/global/vs-common.md:1527;DRIFT/like/vs-common.md:80` |
| `FB-02` | 5 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/global/vs-common.md:373;DRIFT/global/vs-common.md:862;DRIFT/global/vs-common.md:1580` |
| `FB-04` | 6 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/global/vs-common.md:910;DRIFT/like/vs-common.md:179;DRIFT/like/vs-common.md:196` |
| `L-4` | 4 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/article/vs-common.md:73;DRIFT/favorite/api.md:909;DRIFT/favorite/rpc.md:3783` |
| `N-2` | 3 | ✅ 仍成立 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 仍成立 | `DRIFT/global/lanes/L10-ddldeep.md:333;DRIFT/users/user/rpc.md:1047;Intentional_Deviations/users/user/rpc.md:18` |
| `N-3` | 4 | ⛔ 已失效 / ⛔ 已撤销 / 🟡 部分闭合 | ⛔ 已失效 | `DRIFT/global/lanes/L10-ddldeep.md:334;Intentional_Deviations/users/admin/_shared.md:111;Intentional_Deviations/users/admin/_shared.md:115` |
| `N-4` | 3 | ✅ 仍成立 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 仍成立 | `DRIFT/global/lanes/L10-ddldeep.md:335;DRIFT/users/admin/rpc.md:2255;Intentional_Deviations/users/admin/_shared.md:84` |
| `P-18` | 3 | ✅ 已一致 / 仍生效 / 🔴 未闭合 | 仍生效 | `DRIFT/users/admin/rpc.md:3976;DRIFT/users/admin/rpc.md:4246;Pitfalls/article/api.md:732` |
| `PIT-GLOBAL-018` | 4 | ✅ 已一致 / 仍生效 / 🔴 未闭合 | 🔴 未闭合 | `DRIFT/global/lanes/L12-rpcboot-article-comment-like.md:247;DRIFT/global/lanes/L2-rpcgate.md:81;DRIFT/global/lanes/_PROTOCOL.md:182` |
| `S1-FOLLOW-003` | 6 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/follow/stage1-admission.md:123;DRIFT/follow/stage1-admission.md:393;DRIFT/follow/stage1-admission.md:541` |
| `SD-2026-10-23-08` | 9 | ⛔ 已失效 / ✅ 仍成立 / 🔴 未闭合 | ✅ 仍成立 | `DRIFT/message/rpc.md:2068;Intentional_Deviations/_INDEX.md:104;Intentional_Deviations/_INDEX.md:119` |
| `SD-2026-10-25-02` | 3 | ⛔ 已失效 / ⛔ 已撤销 / 待核实 | ⛔ 已撤销 | `Intentional_Deviations/global/common-tails.md:125;Intentional_Deviations/global/common-tails.md:226;Intentional_Deviations/global/common-tails.md:355` |
| `SD-HOT-API-006` | 4 | ⛔ 已失效 / ⛔ 已撤销 / 🔴 未闭合 | ⛔ 已撤销 | `DRIFT/global/lanes/L1-delivery.md:744;Intentional_Deviations/hot/api.md:28;Intentional_Deviations/hot/api.md:45` |
| `T-2` | 7 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/article/vs-common.md:418;DRIFT/common/alignment-2026-10-23.md:689;DRIFT/common/alignment-2026-10-23.md:710` |
| `T-6` | 8 | ✅ 已一致 / 🔴 未闭合 / 🟡 部分闭合 | 🔴 未闭合 | `DRIFT/common/alignment-2026-10-23.md:693;DRIFT/common/alignment-2026-10-23.md:714;DRIFT/global/_README.md:92` |
| `ARPC-002` | 3 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/users/admin/rpc.md:2114;DRIFT/users/admin/rpc.md:2148;DRIFT/users/admin/rpc.md:2455` |
| `B-1` | 4 | ✅ 仍成立 / ✅ 已一致 | ✅ 已一致 | `DRIFT/favorite/rpc.md:1497;DRIFT/favorite/rpc.md:1544;DRIFT/users/user/rpc.md:2133` |
| `C-2` | 2 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/comment/vs-common.md:58;DRIFT/comment/vs-common.md:219` |
| `C-3` | 4 | ✅ 已一致 / 🔴 未闭合 | 🔴 未闭合 | `DRIFT/comment/vs-common.md:59;DRIFT/comment/vs-common.md:112;DRIFT/comment/vs-common.md:220` |
| `C-4` | 3 | 🔴 未闭合 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/comment/vs-common.md:60;DRIFT/comment/vs-common.md:126;DRIFT/comment/vs-common.md:221` |
| `CD-3` | 2 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/global/cross-domain.md:43;DRIFT/global/cross-domain.md:419` |
| `CD-9` | 3 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/global/cross-domain.md:49;DRIFT/global/cross-domain.md:89;DRIFT/global/cross-domain.md:720` |
| `CF-018` | 2 | ✅ 已一致 / 🔴 未闭合 | 🔴 未闭合 | `DRIFT/common/alignment-2026-10-23.md:848;DRIFT/common/alignment-2026-10-23.md:958` |
| `CYCLE-001` | 2 | ✅ 仍成立 / ✅ 已一致 | ✅ 仍成立 | `DRIFT/global/cycle-deps.md:11;Intentional_Deviations/global/cycle-deps.md:20` |
| `D-4` | 2 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/like/vs-common.md:70;DRIFT/message/vs-common.md:72` |
| `D-6` | 2 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/like/vs-common.md:73;DRIFT/task/vs-common.md:66` |
| `D-7` | 2 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/like/vs-common.md:72;DRIFT/task/vs-common.md:68` |
| `DEV-FAV-API-001` | 3 | ✅ 仍成立 / ✅ 已一致 | ✅ 已一致 | `DRIFT/favorite/api.md:275;Intentional_Deviations/favorite/api.md:31;Intentional_Deviations/favorite/api.md:40` |
| `DEV-FAV-API-002` | 3 | ✅ 仍成立 / ✅ 已一致 | ✅ 仍成立 | `DRIFT/favorite/api.md:446;Intentional_Deviations/favorite/api.md:32;Intentional_Deviations/favorite/api.md:56` |
| `DEV-FAV-RPC-002` | 2 | ⛔ 已撤销 / 🔴 未闭合 | ⛔ 已撤销 | `DRIFT/favorite/rpc.md:472;Intentional_Deviations/favorite/rpc.md:101` |
| `DEV-FAV-RPC-005` | 2 | ⛔ 已撤销 / 🔴 未闭合 | ⛔ 已撤销 | `DRIFT/favorite/rpc.md:324;Intentional_Deviations/favorite/rpc.md:167` |
| `DRIFT-001` | 3 | ✅ 已一致 / 🔴 未闭合 | 🔴 未闭合 | `DRIFT/article/api.md:877;DRIFT/article/api.md:894;DRIFT/common/deadline.md:2` |
| `DRIFT-003` | 3 | ✅ 已一致 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/common/protection.md:89;DRIFT/message/api.md:79;DRIFT/users/user/api.md:35` |
| `DRIFT-ARTICLE-API-001` | 2 | ✅ 仍成立 / ✅ 已一致 | ✅ 仍成立 | `DRIFT/article/api.md:576;Intentional_Deviations/article/api.md:25` |
| `DRIFT-ARTICLE-API-005` | 2 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/article/api.md:666;DRIFT/article/api.md:1815` |
| `DRIFT-ARTICLE-API-006` | 3 | ✅ 已一致 / 🟡 部分闭合 | 🟡 部分闭合 | `DRIFT/article/api.md:703;DRIFT/global/vs-common.md:225;Intentional_Deviations/article/api.md:26` |
| `DRIFT-ARTICLE-API-009` | 2 | ✅ 仍成立 / ✅ 已一致 | ✅ 已一致 | `DRIFT/article/api.md:750;Intentional_Deviations/article/api.md:27` |
| `DRIFT-ARTICLE-RPC-004` | 2 | ⚫ 已签字偏离 / ✅ 已一致 | ⚫ 已签字偏离 | `DRIFT/article/rpc.md:440;DRIFT/article/rpc.md:903` |
| `DRIFT-FOLLOW-006` | 6 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/follow/rpc.md:20;DRIFT/follow/rpc.md:213;DRIFT/follow/rpc.md:351` |
| `DRIFT-FOLLOW-008` | 8 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/follow/api.md:35;DRIFT/follow/api.md:528;DRIFT/follow/api.md:1183` |
| `DRIFT-FOLLOW-012` | 6 | ✅ 已一致 / 🟡 部分闭合 | ✅ 已一致 | `DRIFT/follow/api.md:39;DRIFT/follow/api.md:410;DRIFT/follow/api.md:1003` |
| `DRIFT-FOLLOW-013` | 5 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/follow/api.md:1074;DRIFT/follow/rpc.md:659;DRIFT/follow/vs-common.md:726` |
| `DRIFT-G1-001` | 5 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/follow/vs-common.md:174;DRIFT/follow/vs-common.md:1213;DRIFT/follow/vs-common.md:1270` |
| `DRIFT-G1-002` | 6 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/follow/vs-common.md:200;DRIFT/follow/vs-common.md:1214;DRIFT/follow/vs-common.md:1271` |
| `DRIFT-HOTAPI-015` | 4 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/hot/api.md:675;DRIFT/hot/api.md:916;DRIFT/hot/api.md:952` |
| `DRIFT-HOTAPI-030` | 7 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/article/vs-common.md:26;DRIFT/comment/vs-common.md:31;DRIFT/follow/vs-common.md:28` |
| `DRIFT-LIKE-017` | 2 | ✅ 已一致 / 🔴 未闭合 | ✅ 已一致 | `DRIFT/like/rpc.md:879;DRIFT/like/rpc.md:1058` |

> 共 **164** 个编号存在状态冲突（此处列前 60）。

---

## 五、证据强度分层（**「已改」≠「已验证」**）

| 档 | 含义 | 条目数 |
|---|---|---|
| **A 活体** | 段内点名了**已在活体台账实跑过**的探针（**带探针的真实退出码**） | 0 |
| **B 静态** | 有可复算的 `文件:行号` 指针，但无活体探针 | 757 |
| **C 无指针** | 两样都没有 ⇒ 结论今天不可复算 | 287 |

### 5.1 A 档内部再分（**「有活体证据」≠「结论被证实」**）

> ⚠️ **A 档带的是探针的真实退出码** —— 因为实测发现：**A 档引用的探针里**
> 有指向 **EXIT=2（环境问题 ⇒ 结论不可用）**的，也有指向 **EXIT=1（不一致）**的。
> ⇒ **只写「A 活体」会让人以为这条已被证实** ⇒ 必须把退出码带出来。
> ⚠️ 且「**红**」**≠ 有缺陷** —— 红分三种（已签字偏离 / 已登记残留 / 真问题），
> 逐条定性见 [`live-parity-2026-10-30.md`](live-parity-2026-10-30.md) §0 与 §四。

| A 档细分 | 含义 | 条目数 |
|---|---|---|
| **A 活体 · 混(0/1)** | 多个探针退出码**不一致**（有的绿有的红）⇒ 需逐条看 | 6 |
| **A 活体 · 混(0/1/2)** | 同上（还有退出码 2）⇒ 需逐条看 | 2 |
| **A 活体 · 混(0/2)** | 同上（绿 + 环境问题）⇒ 需逐条看 | 1 |
| **A 活体 · 混(0/4)** | 同上（绿 + 退出码 4）⇒ 需逐条看 | 1 |
| **A 活体 · 环境(2)** | 探针判定「**环境问题**」⇒ 结论**不可用** | 6 |
| **A 活体 · 红(1)** | 探针判定**不一致** ⇒ 当前结论**未被证实**（红 ≠ 有缺陷） | 15 |
| **A 活体 · 绿(0)** | 探针断言**全部成立** ⇒ 这条的活体证据**支持**当前结论 | 99 |

> ⇒ **真正「活体坐实」（绿）的只有 99 条**（8%）；
> 其余 **31** 条 A 档的证据本身是**红 / 环境问题 / 混合** ⇒ **不可当成「已坐实」**。

---

### 5.2 证据边界声明（**C 档 = 今天不可复算**）

> **本节是「效力边界」的正式声明** —— 用户 2026-10-30 拍板：**C 档条目（287 条）本轮不做活体取证，接受其静态证据**。声明如下：
>
> 1. **C 档条目的状态 = 台账自述**，不是本轮可复算的结论。
>    ⇒ 任何引用 C 档条目作为**前提**的结论，必须标注「前提未回源」。
> 2. **C 档条目不得单独构成「已证实」** —— 它只是「被登记过」，不是「被核对过」。
>    ⇒ 与 AGENTS.md §3.3.3 第 ② 条同源：**指针能解析 ≠ 结论为真**。
> 3. **C 档条目的阶段归属（本轮 U5-O 定级）只解决「排不排期」，不解决「对不对」。**
>    ⇒ 定为 A/B 只表示「阶段一要处理」，**不表示已证明它成立**。
> 4. **本声明不等于「C 档条目已被否定」** —— 它们仍是**待回源**状态，
>    且**回源是逐条与 Go 源码对照**（Q1 拍板口径），不是抽样。
> 5. **已签字偏离（`Intentional_Deviations`）不受本声明影响** —— 它们的效力来自**签字**，
>    不来自「今天能不能复算」（AGENTS.md §0 第 1 条）。
>
> ⇒ **可复算性分级见 §五 表**；本声明只界定**效力**，不改任何条目的状态。

---

## 六、全量派生表（1174 条问题项）

> 机读请用 [`stage1-derivation.tsv`](stage1-derivation.tsv)（同一次生成，16 列）。

| ID | 目录 | 域 | 状态 | 证据 | 优先级 | 归属 | 阻断 | 最终目标 | 排期 | 缺口 | 源位置 | 依据（Go ↔ RAG） |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `G-3` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/article/api.md:2692` | — |
| `L-4` | DRIF | article | 🟡 部分闭合 | A 活体 · 绿(0) | P0 | A | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-10 | — | `DRIFT/article/vs-common.md:73;DRIFT/favorite/api.md:909;DRIFT/favorite/rpc.md:3783` | — |
| `T-2` | DRIF | article | 🟡 部分闭合 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-10 | — | `DRIFT/article/vs-common.md:418;DRIFT/common/alignment-2026-10-23.md:689;DRIFT/common/alignment-2026-10-23.md:710` | — |
| `DRIFT-SF-001` | DRIF | common | ✅ 已一致 | A 活体 · 绿(0) | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:420;DRIFT/common/alignment-2026-10-23.md:870;DRIFT/common/alignment-2026-10-23.md:991` | — |
| `SF-001` | DRIF | common | ✅ 已一致 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:866` | — |
| `G-13` | DRIF | follow | ✅ 已一致 | B 静态 | P0 | A | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/api.md:1098;DRIFT/follow/stage1-admission.md:214;DRIFT/follow/vs-common.md:770` | — |
| `B-10` | DRIF | global | 🔴 未闭合 | C 无指针 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-07 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3846` | Go `go-zero@v1.10.2/zrpc/client.go:70-71`（Go 侧每次调用**无条件**装 2000ms 上界）＋ `go-zero@v1.10.2/zrpc/internal/client.go:115-116`（`clientinterceptors.TimeoutIn… |
| `B-11` | DRIF | global | 🔴 未闭合 | C 无指针 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-07 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3847` | Go `go-zero@v1.10.2/zrpc/client.go:70-71`（Go 侧每次调用**无条件**装 2000ms 上界）＋ `go-zero@v1.10.2/zrpc/internal/client.go:115-116`（`clientinterceptors.TimeoutIn… |
| `G4-A4-05` | DRIF | global | 🔴 未闭合 | C 无指针 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-07 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3755;DRIFT/global/vs-common.md:3812` | Go `go-zero@v1.10.2/zrpc/client.go:70-71`（`if c.Timeout > 0 { opts = append(opts, WithTimeout(time.Duration(c.Timeout)*time.Millisecond)) }` ⇒ **每次调用都… |
| `G4-A4-07` | DRIF | global | 🔴 未闭合 | C 无指针 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-08 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3757;DRIFT/global/vs-common.md:3878` | Go `go-zero@v1.10.2/zrpc/config.go:31`（`Timeout int64 json:",default=2000"` ⇒ Go 侧上界真源**存在**）vs RAG `service/article/article-rpc/src/main/java/com/rag… |
| `H-1` | DRIF | hot | 🟡 部分闭合 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-10 | — | `DRIFT/hot/vs-common.md:67;DRIFT/hot/vs-common.md:267;DRIFT/users/admin/rpc.md:962` | — |
| `S1-FOLLOW-007` | DRIF | users | 🔴 未闭合 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | BLK-03 | 静态取证 · 缺活体对拍 | `DRIFT/users/stage1-admission.md:524` | — |
| `S1-USERS-001` | DRIF | users | ✅ 已一致 | B 静态 | P0 | A | 否 | 台账已写 · 阶段 1 先补**最小可复现说明书**（README：架构一句话 / 模块清单 / 环境变量表 / 启动顺序 / 验证命 | P5-T02 | — | `DRIFT/users/stage1-admission.md:91;DRIFT/users/stage1-admission.md:491;DRIFT/users/stage1-admission.md:515` | — |
| `C-3` | DRIF | comment | 🔴 未闭合 | A 活体 · 绿(0) | P1 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | P2-T01,P3-T04 ｜ BLK-02,DEC-05 | — | `DRIFT/comment/vs-common.md:59;DRIFT/comment/vs-common.md:112;DRIFT/comment/vs-common.md:220` | 同一编号在本文件有三处登记，**现行开放面 = `:112` 的「C-3 / U15（= global E65）出向 gRPC 不继承请求 deadline」**：Go `service/comment/rpc/internal/logic/subject_fallback.go:12`（`reso… |
| `E65` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | P1-T05,P2-T01,P2-T02,P2-T03,P2-T04,P2-T05,P2-T06,P3-T01 ｜ BLK-02,GAP-07,GAP-08 | 静态取证 · 缺活体对拍 | `DRIFT/comment/rpc.md:257;DRIFT/comment/rpc.md:930;DRIFT/favorite/rpc.md:3376` | — |
| `A-5` | DRIF | article | 🟡 部分闭合 | C 无指针 | P1 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-11 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:364` | Go `service/article/rpc/etc/article.yaml:13`（`Password: "${POSTGRES_PASSWORD}"` ⇒ **无 `:默认值` 形态**）＋ `go-zero@v1.10.2/core/conf/config.go:63`（`loader([… |
| `F-6` | DRIF | favorite | 🟡 部分闭合 | C 无指针 | P1 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-11 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/vs-common.md:228` | Go `service/favorite/rpc/etc/favorite.yaml:13`（`Password: "${POSTGRES_PASSWORD}"` ⇒ **yaml 里不带兜底值**）＋ `go-zero@v1.10.2/core/conf/config.go:63`（`os.Exp… |
| `E59` | DRIF | global | ✅ 已一致 | B 静态 | P1 | B | 否 | 台账已写 · ✅ **已修**：三处 catch 改 `throw e;`（原样上抛）⇒ **不做**「换成另一个业务码」 ｜ ① 三 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:447;DRIFT/like/vs-common.md:1498;DRIFT/message/rpc.md:2770` | — |
| `E60` | DRIF | global | ✅ 已一致 | B 静态 | P1 | B | 否 | 台账已写 · ✅ **已修**（**补业务错误记录 + 错误分流落在 `publish` 内部**） ｜ ① 双失败 ⇒ 真实日志出现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:448;DRIFT/like/rpc.md:1597;DRIFT/like/rpc.md:1742` | — |
| `S1-FOLLOW-008` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-05 | 静态取证 · 缺活体对拍 | `DRIFT/users/stage1-admission.md:525` | — |
| `S1-FOLLOW-009` | DRIF | users | 🔴 未闭合 | C 无指针 | P1 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-06 | 无可复算指针 · 结论不可复算 | `DRIFT/users/stage1-admission.md:526` | — |
| `E2` | DRIF | follow | 🟡 部分闭合 | C 无指针 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-09 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:377;DRIFT/global/_README.md:333` | — |
| `E7` | DRIF | follow | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-09 | — | `DRIFT/follow/stage1-admission.md:382;DRIFT/global/_README.md:338` | — |
| `S1-FOLLOW-004` | DRIF | follow | 🟡 部分闭合 | B 静态 | P2 | B | 否 | 台账已写 · ① `docker-compose.yaml` 补 `follow-rpc` / `follow-api` / `adm | GAP-09 | — | `DRIFT/follow/stage1-admission.md:145;DRIFT/follow/stage1-admission.md:394;DRIFT/follow/stage1-admission.md:537` | — |
| `E1` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-10 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:332;DRIFT/users/stage1-admission.md:503` | — |
| `G4-A4-01` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-08 | — | `DRIFT/global/vs-common.md:3751;DRIFT/global/vs-common.md:3761` | Go `service/message/common/errmsg.go:108-111`（`status.FromError` 失败 ⇒ `CodeServerBusy`）＋ `:117-132`（`switch st.Code()` 的 `default` ⇒ `CodeServerBusy` … |
| `G4-A4-03` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-11 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3753;DRIFT/global/vs-common.md:3789` | Go `service/favorite/common/errmsg.go:113-116`（`st, ok := status.FromError(err)` ＋ `if !ok { return CodeServerBusy }`）＋ `service/favorite/common/errms… |
| `G4-A4-04` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-11 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3754;DRIFT/global/vs-common.md:3801` | Go `go-zero@v1.10.2/rest/router/patrouter.go:79-82`（裸 405 = `Set("Allow")` ＋ `WriteHeader(405)` ⇒ **无 body、无 Content-Type**）＋ `go-zero@v1.10.2/rest/ro… |
| `DRIFT-MSG-V-032` | DRIF | message | 🟡 部分闭合 | B 静态 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-11 | — | `DRIFT/message/rpc.md:2801` | Go `service/common/observability/observability.go:41`（`func SlowThreshold() time.Duration` ⇒ 慢调用阈值的**真源**）＋ `service/message/rpc/message.go:44`（`s.Add… |
| `DRIFT-ARPC-009` | DRIF | users | 🔴 未闭合 | B 静态 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-08 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:2947` | Go `go-zero@v1.10.2/zrpc/server.go:144`（`setupUnaryInterceptors` ⇒ Trace/Recover/Stat/Prometheus/Breaker/Shedding 六层默认全装）＋ `service/user/admin/rpc/adm… |
| `DRIFT-USERSCOMMON-006` | DRIF | users | 🔴 未闭合 | B 静态 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-08 | 静态取证 · 缺活体对拍 | `DRIFT/users/common.md:788;DRIFT/users/common.md:869` | Go `golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:10`（`if len(tok) > 6 && strings.ToUpper(tok[0:7]) == "BEARER "` ⇒ 判据是 `> 6` ⇒ `"Bearer "`（7 字符）**被剥成空串*… |
| `S1-USERS-002` | DRIF | users | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | B | 否 | 台账已写 · ① 全仓 9 处统一为**一个**来源（`.env` ⇒ env 注入，**取消 fallback**，与 Go 的 f | P4-T01,P4-T02 ｜ GAP-11 | — | `DRIFT/users/stage1-admission.md:108;DRIFT/users/stage1-admission.md:493;DRIFT/users/stage1-admission.md:516` | — |
| `S1-USERS-004` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | B | 否 | 台账已写 · ① 事务边界上移到 `UserDomainService` 的 public 入口（与 Go 的「数据层显式闭包」同形） | GAP-09 | — | `DRIFT/users/stage1-admission.md:158;DRIFT/users/stage1-admission.md:518` | — |
| `C-5` | DRIF | comment | 🟡 部分闭合 | C 无指针 | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-11 | 无可复算指针 · 结论不可复算 | `DRIFT/comment/vs-common.md:61;DRIFT/comment/vs-common.md:222` | Go `service/comment/common/errmsg/errmsg.go:37-65`（27 个码值 const 块）＋ `:67-95`（`codeMsg` 27 条中文文案）＋ `:97-103`（`GetErrMsg` 未知码回退 `codeMsg[Error]`＝`"FAIL"… |
| `E4` | DRIF | follow | ✅ 已一致 | C 无指针 | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:379;DRIFT/global/_README.md:335;DRIFT/users/stage1-admission.md:506` | — |
| `E5` | DRIF | follow | ✅ 已一致 | C 无指针 | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:380;DRIFT/global/_README.md:336;DRIFT/users/stage1-admission.md:507` | — |
| `S1-FOLLOW-001` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | B | 否 | 台账已写 · ① 统一方案：给所有 rpc 组件开**独立 management 端口**（与管理 HTTP 端口分离，避开 `man | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:74;DRIFT/follow/stage1-admission.md:391;DRIFT/follow/stage1-admission.md:540` | — |
| `S1-FOLLOW-003` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | B | 否 | 台账已写 · ① 把 `ObservabilityLifecycle` 的关闭路径与 **web 类型**解耦（延迟类加载 / 反射隔 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:123;DRIFT/follow/stage1-admission.md:393;DRIFT/follow/stage1-admission.md:541` | — |
| `S1-FOLLOW-010` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:548;DRIFT/follow/vs-common.md:821;DRIFT/follow/vs-common.md:1248` | — |
| `S1-FOLLOW-011` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:549;DRIFT/follow/vs-common.md:822;DRIFT/follow/vs-common.md:1249` | — |
| `E6` | DRIF | global | ✅ 已一致 | A 活体 · 环境(2) | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:337` | — |
| `D-08` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | P4-T09,P7-T01 | — | `DRIFT/like/rpc.md:1984` | — |
| `S1-USERS-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | B | 否 | 台账已写 · ① `.gitignore` 加 `.env`（+ `.env.*` 但放行 `.env.example`）；② 提交  | P5-T02 | — | `DRIFT/users/stage1-admission.md:141` | — |
| `S1-USERS-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | B | 否 | 台账已写 · 域内 1 行改完（`${FOLLOW_RPC_PORT:8082}`）⇒ 本轮即做（T3）；阶段 2 可把 6 个组件的 | 未排期 | 已定性 · 未排期 | `DRIFT/users/stage1-admission.md:175;DRIFT/users/stage1-admission.md:519` | — |
| `T-3` | DRIF | article | 🔴 未闭合 | A 活体 · 环境(2) | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/article/vs-common.md:608;DRIFT/comment/vs-common.md:339;DRIFT/common/alignment-2026-10-23.md:690` | — |
| `E66` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | P2-T01,P2-T02,P2-T06 ｜ DEC-05 | 静态取证 · 缺活体对拍 | `DRIFT/comment/rpc.md:290;DRIFT/global/_README.md:431;DRIFT/global/vs-common.md:31` | Go `service/comment/rpc/internal/cache/comment_subject_cache.go:76`（`GetSubjectCache(ctx context.Context, subjectID string)` ⇒ 带 ctx）＋ `:84`（`c.rdb.Ge… |
| `G4-16` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/comment/vs-common.md:96` | Go `service/comment/rpc/internal/cache/comment_subject_cache.go:84`（`c.rdb.Get(ctx, …)` ⇒ Go 侧缓存读写吃 ctx ⇒ deadline 到点即止）＋ `service/comment/rpc/interna… |
| `SD-2026-10-24-CAND-03` | Inte | comment | ⏳ 未签字 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/rpc.md:229` | Go `service/comment/rpc/cmd/fact-authority/main.go:23`（`func main() { os.Exit(execute()) }` ⇒ **独立进程**）＋ `:52`（`listen := os.Getenv("COMMENT_FACT_AUTH… |
| `DRIFT-CF-001` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:375` | — |
| `DRIFT-CF-002` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:376` | — |
| `DRIFT-CF-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:385` | — |
| `DRIFT-CF-004` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:398` | — |
| `DRIFT-CF-005` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:377` | — |
| `DRIFT-CF-006` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:378` | — |
| `DRIFT-CF-008` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:379` | — |
| `DRIFT-CF-012` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:388` | — |
| `DRIFT-CF-013` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:389` | — |
| `DRIFT-CF-014` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:390` | — |
| `DRIFT-CF-015` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:391` | — |
| `DRIFT-CF-017` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:392` | — |
| `DRIFT-COMMON-LOG-001` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:296` | Go `service/common/logger/logger.go:148-157`（8 个 `logx.LogField`：service/event_id/trace_id/error_code/file_line/call_path/call_chain/error_reason）＋ `:… |
| `DRIFT-LOG-001` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:413` | — |
| `DRIFT-LOG-002` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:414` | — |
| `DRIFT-LOG-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:415` | — |
| `DRIFT-LOG-005` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:417` | — |
| `DRIFT-LOG-006` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:418` | — |
| `DRIFT-LOG-007` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:419` | — |
| `DRIFT-SF-002` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:421` | — |
| `DRIFT-SF-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:422` | — |
| `LOG-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:856` | — |
| `E3` | DRIF | follow | 🔴 未闭合 | C 无指针 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:378;DRIFT/users/stage1-admission.md:505` | — |
| `U-08` | DRIF | like | 🟡 部分闭合 | A 活体 · 绿(0) | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/like/api.md:831;DRIFT/like/api.md:844` | Go `service/like/rpc/pb/like_grpc.pb.go:53`（`err := c.cc.Invoke(...)` ⇒ 把**原始 gRPC status 错误**原样返回）＋ `service/like/api/internal/logic/like/likeactionl… |
| `UNRESOLVED-MSG-V-001` | DRIF | _cross | 🟡 部分闭合 | A 活体 · 红(1) | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/_cross/message-domain-wide.md:215;DRIFT/_cross/message-domain-wide.md:494` | Go `service/message/rpc/message.go:37`（`s := zrpc.MustNewServer(c.RpcServerConf, …)` ⇒ 注册到服务发现）＋ `go-zero@v1.10.2/zrpc/server.go:43`（`server, err = in… |
| `A-3` | DRIF | article | 🔴 未闭合 | C 无指针 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:362` | Go 1.25.11 `src/net/url/url.go:1001`（`const defaultMaxParams = 10000`）＋ `:1019-1021`（`func parseQuery(m Values, query string) (err error) { if !urlPar… |
| `A-6` | DRIF | article | 🟡 部分闭合 | C 无指针 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:365` | Go `service/article/rpc/article.go:41-42`（`u := model.NewArticleRepo(c)` ⇒ `service/article/rpc/internal/model/db.go:55-60` 的 `db.AutoMigrate(&Article… |
| `FIX-13` | DRIF | article | 🔴 未闭合 | C 无指针 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2011;DRIFT/article/api.md:2080;DRIFT/article/rpc.md:1423` | Go `go-zero@v1.10.2/rest/httpx/requests.go:21`（`maxBodyLen = 8 << 20` ⇒ `io.LimitReader` **静默截断**）＋ Go 1.25.11 `src/strconv` 无关；解析器拿到半个文档 ⇒ `http.Erro… |
| `G4-8` | DRIF | article | 🔴 未闭合 | C 无指针 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:290` | Go `service/article/rpc/internal/model/db.go:55`（`db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` ⇒ Go 侧**没有「排序桥」这个概念** —— 建表/初始化顺序由 GORM 承担）vs… |
| `S-3` | DRIF | article | 🔴 未闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/rpc.md:332` | Go `go-queue@v1.2.2/kq/config.go:24`（`ForceCommit bool \`json:",default=true"\`` ⇒ **声明点**）＋ `go-queue@v1.2.2/kq/queue.go:230-242`（**消费点**：`if err := … |
| `C-4` | DRIF | comment | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | P7-T01 | — | `DRIFT/comment/vs-common.md:60;DRIFT/comment/vs-common.md:126;DRIFT/comment/vs-common.md:221` | Go `service/comment/rpc/comment.go:38-41`（`go func() { http.Handle("/metrics", promhttp.Handler()); _ = http.ListenAndServe(":9091", nil) }()` ⇒ **独立 … |
| `C-6` | DRIF | comment | 🔴 未闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/comment/vs-common.md:62;DRIFT/comment/vs-common.md:223` | 同一编号两处登记。`:62` 面（comment-api 的 JWT 验签被计入 3s 预算）已闭合：Go `service/common/observability/http.go:32`（`conf.Middlewares.Timeout = false` ⇒ 关掉 go-zero 原生 Tim… |
| `G4-17` | DRIF | comment | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/comment/vs-common.md:97` | Go `service/comment/rpc/internal/model/db.go:15`（`err = db.AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{}, &CommentDomainF… |
| `SD-2026-10-24-CAND-01` | Inte | comment | ⏳ 未签字 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/rpc.md:34` | Go `service/comment/rpc/internal/model/data_model.go:14`（`CreatedAt time.Time \`gorm:"column:created_at;autoCreateTime;…"\`` ⇒ **无 `not null`**、**无 DB… |
| `CF-001` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:834` | — |
| `CF-002` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:835` | — |
| `CF-005` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:838` | — |
| `CF-006` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:839` | — |
| `CF-008` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:840` | — |
| `CF-012` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:843` | — |
| `CF-013` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:844` | — |
| `CF-014` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:845` | — |
| `CF-015` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:846` | — |
| `CF-017` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:847` | — |
| `CF-018` | DRIF | common | 🔴 未闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:848;DRIFT/common/alignment-2026-10-23.md:958` | Go `service/common/communityfact/process.go:20`（`func RunDispatcher(ctx, dispatcher, sender, logger, once bool) error` ⇒ **阻塞式**、**不返回任何句柄**）＋ `:32-33… |
| `DRIFT-CF-018` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | P4-T03 | — | `DRIFT/common/alignment-2026-10-23.md:661;DRIFT/common/alignment-2026-10-23.md:1050;DRIFT/global/vs-common.md:4646` | Go `service/common/communityfact/process.go:20`（`func RunDispatcher(ctx context.Context, dispatcher Dispatcher, sender Sender, logger *slog.Logger, on… |
| `LOG-001` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:854` | — |
| `LOG-002` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:855` | — |
| `LOG-005` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:858` | — |
| `LOG-006` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:859` | — |
| `LOG-007` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:860` | — |
| `SF-002` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:867` | — |
| `SF-003` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:868` | — |
| `F-5` | DRIF | favorite | 🔴 未闭合 | C 无指针 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/vs-common.md:227` | Go `go-zero@v1.10.2/rest/router/patrouter.go:79-82`（`w.Header().Set(allowHeader, allows)` + `w.WriteHeader(http.StatusMethodNotAllowed)` ⇒ **裸状态码**：既不… |
| `G4-13` | DRIF | favorite | 🔴 未闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/favorite/vs-common.md:120` | Go `go-zero@v1.10.2/rest/httpx/util.go:22-65`（`GetFormValues`：**非空值计数** + `r.ParseForm()` 错误传播）＋ `go-zero@v1.10.2/rest/httpx/requests.go:50-68`（`httpx… |
| `G4-14` | DRIF | favorite | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/favorite/vs-common.md:121` | Go `service/favorite/rpc/etc/favorite.yaml:23-29`（`DevServer: {Enabled: true, Port: 16062, MetricsPath: /metrics, HealthPath: /healthz, EnableMetrics:… |
| `G4-15` | DRIF | favorite | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | GAP-11 | — | `DRIFT/favorite/vs-common.md:122` | Go `go-zero@v1.10.2/rest/router/patrouter.go:79-82`（`w.Header().Set(allowHeader, allows)` ＋ `w.WriteHeader(http.StatusMethodNotAllowed)` ⇒ **裸状态码**：既无… |
| `DRIFT-FOLLOW-007` | DRIF | follow | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/api.md:34;DRIFT/follow/api.md:589;DRIFT/follow/api.md:1182` | Go `service/follow/api/internal/metrics/metrics.go:29` = `Buckets: prometheus.DefBuckets`（11 个数值边界 ＋ `+Inf` ⇒ **12 桶**）＋ `:14` = `Subsystem: "follow_a… |
| `E67` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:432` | Go `service/comment/rpc/internal/svc/service_context.go:28`（`kq.NewPusher(c.KqPusherConf.Brokers, c.KqPusherConf.Topic)` ⇒ **没传** `WithSyncPush()`）＋ `… |
| `FB-02` | DRIF | global | 🟡 部分闭合 | A 活体 · 混(0/1/2) | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/vs-common.md:373;DRIFT/global/vs-common.md:862;DRIFT/global/vs-common.md:1580` | Go `go-zero@v1.10.2/rest/httpx/util.go:15`（`maxFormParamCount = 2048`）＋ `go-zero@v1.10.2/rest/httpx/util.go:48-53`（数**非空值**，超限 ⇒ `too many form values… |
| `T-17` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/global/common-tails.md:612;DRIFT/global/common-tails.md:643` | — |
| `DRIFT-HOTAPI-025` | DRIF | hot | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/hot/api.md:1590` | Go `service/common/observability/http.go:80-88`（`case <-ctx.Done():` ⇒ `statusCode := http.StatusServiceUnavailable`，`errors.Is(ctx.Err(), context.Can… |
| `G-19` | DRIF | hot | 🟡 部分闭合 | C 无指针 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | P5-T01 ｜ GAP-01 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/proto.md:67` | — |
| `U-03` | DRIF | like | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/like/api.md:826;DRIFT/like/api.md:839` | Go `go-zero@v1.10.2/rest/config.go:55`（`Timeout int64 json:",default=3000"` ⇒ **yaml 写不写都是 3000ms**）＋ `service/like/api/likecenter.go:36-38`（`httpTime… |
| `U-06` | DRIF | like | 🔴 未闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/like/api.md:829;DRIFT/like/api.md:842` | Go `go-zero@v1.10.2/core/service/serviceconf.go:77`（`devserver.StartAgent(sc.DevServer)`，在 `ServiceConf.SetUp()` 内 ⇒ `rest.RestConf`/`zrpc.RpcServerCo… |
| `DRIFT-MSG-012` | DRIF | message | 🔴 未闭合 | A 活体 · 绿(0) | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/message/rpc.md:262` | Go `service/message/rpc/message.go:25`（`func main()` ⇒ 纯 gRPC 进程）＋ `:37`（`s := zrpc.MustNewServer(c.RpcServerConf, …)` ⇒ **无 servlet 容器**、无 HTTP 监听面）v… |
| `DRIFT-ARPC-010` | DRIF | users | 🔴 未闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | P2-T02,P2-T06 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:3063;DRIFT/users/admin/rpc.md:3169` | Go `service/user/admin/rpc/internal/logic/logoutlogic.go:38`（`l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))` ⇒ Redis 写绑 c… |
| `DRIFT-ARPC-012` | DRIF | users | 🔴 未闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:3195;DRIFT/users/admin/rpc.md:4100` | Go `go-zero@v1.10.2/zrpc/server.go:25`（`MustNewServer` ⇒ `logx.Must(err)` ⇒ 注册失败即**进程退出** fail-fast）＋ `go-zero@v1.10.2/zrpc/internal/rpcpubserver.go:4… |
| `DRIFT-USERAPI-013` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | P2-T02,P2-T06 | — | `DRIFT/users/user/api.md:624;DRIFT/users/user/api.md:3283;DRIFT/users/user/api.md:3590` | Go `go-zero@v1.10.2/rest/engine.go:149`（`chn = chn.Append(handler.BreakerHandler(route.Method, route.Path, metrics))` ⇒ 键 = **注册期路由模板**，每条路由各建一个 break… |
| `UNRESOLVED-USERSCOMMON-004` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/users/common.md:545;DRIFT/users/common.md:871` | Go `service/user/user/rpc/internal/model/db.go:33`（`db.AutoMigrate(&User{}, &UserAvatarHistory{})` ⇒ DDL 由 GORM 生成：**标量字段无 `not null` tag ⇒ 列可空**（`ser… |
| `G4-5` | DRIF | article | 🟡 部分闭合 | B 静态 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/article/vs-common.md:278;DRIFT/favorite/vs-common.md:113` | Go `service/article/rpc/internal/model/db.go:55-60`（`db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` ⇒ Go 侧**无版本化迁移账本 / 无 baseline 概念** ⇒ 「账本撞号… |
| `P-U-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/proto.md:176;DRIFT/article/proto.md:178` | — |
| `U-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:2442;DRIFT/users/admin/_shared.md:213;DRIFT/users/admin/rpc.md:1477` | — |
| `U-9b` | DRIF | article | 🟡 部分闭合 | B 静态 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/article/rpc.md:2523` | Go `go-queue@v1.2.2/kq/queue.go:248-259`（`startProducers`：`q.c.Consumers` 个 goroutine 调 `FetchMessage` ⇒ 拉取者）＋ `:219-246`（`startConsumers`：`q.c.Proces… |
| `SD-2026-10-24-CAND-05` | Inte | comment | ⏳ 未签字 | B 静态 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/api.md:73` | Go `golang-jwt/jwt/v4@v4.5.2/request/oauth2.go:8`（`func stripBearerPrefixFromTokenString(tok string) (string, error)`）＋ `:10`（`if len(tok) > 6 && stri… |
| `CF-004` | DRIF | common | ✅ 已一致 | B 静态 | P3 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:837` | — |
| `E43` | DRIF | global | 🟡 部分闭合 | B 静态 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:423;DRIFT/users/vs-common.md:1228` | Go `service/like/rpc/internal/model/db.go:15`（`db.AutoMigrate(...)` ⇒ 该静态校验要对照的 **Go 侧 DDL 产出点**）＋ `service/like/rpc/internal/model/data_model.go:9`（`… |
| `E8` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:339` | — |
| `G4-A4-06` | DRIF | global | 🔴 未闭合 | C 无指针 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3756;DRIFT/global/vs-common.md:3867` | Go `service/article/rpc/article.go:1`（Go 侧 rpc 进程入口 ⇒ 「真启动」在 Go 侧是逐进程可验的事实，RAG 侧必须逐组件补同等级证据）vs RAG `service/article/article-rpc/src/test/java/com/rag/… |
| `DRIFT-HOTAPI-031` | DRIF | hot | 🟡 部分闭合 | B 静态 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/hot/api.md:2398` | Go `net/http/server.go:2049`（`const publicErr = "431 Request Header Fields Too Large"` ⇒ Go 自己产的 **431 ＋ `text/plain`**）＋ `service/hot/api/hotcenter.g… |
| `DRIFT-LIKE-003` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | C | 否 | 台账已写 · ❌ **否**（**C 类暂缓 ⇒ 不计入「真漂移 = 0」的口径**）<br>**依据（逐条可复算）**：<br>·  | DEC-01 | — | `DRIFT/like/rpc.md:151;DRIFT/like/rpc.md:1395;DRIFT/like/rpc.md:1829` | — |
| `SD-2026-10-24-11` | Inte | like | ⏳ 未签字 | B 静态 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/like/api.md:133` | Go `service/like/api/internal/metrics/metrics.go:20`（`prometheus.Register(ApiRejectCount)`，**在 `InitMetrics()` 内** `:17`）＋ `service/like/api/likecente… |
| `U-05` | DRIF | like | 🟡 部分闭合 | B 静态 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/like/api.md:828;DRIFT/like/api.md:841` | Go `go-zero@v1.10.2/rest/httpx/requests.go:66`（`httpx.Parse` ⇒ `ParseJsonBody(r, v)`）＋ `:117-118`（`func ParseJsonBody` ⇒ `if withJsonBody(r) {`）＋ `:15… |
| `E20` | DRIF | global | 🔴 未闭合 | C 无指针 | P0 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:368` | Go `service/like/rpc/internal/config/config.go:78`（`MessageRpc zrpc.RpcClientConf` ⇒ Go 的客户端目标由 zrpc/etcd 的 `Key` 解析，**根本不存在 `discovery://` 这种带 path 的… |
| `E22` | DRIF | global | 🔴 未闭合 | C 无指针 | P0 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:370` | Go `service/task/rpc/task.go:23`（`func main()` ⇒ Go 侧入口天然可执行）＋ `service/hot/rpc/hot.go:27`（`func main()`）vs RAG `service/task/task-rpc/src/main/java/c… |
| `DRIFT-MSG-V-027` | DRIF | message | 🔴 未闭合 | C 无指针 | P0 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/message/rpc.md:2110` | Go `service/message/rpc/internal/model/db.go:42`（`db.AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})` ⇒ Go 侧**无版本化迁移账本** ⇒ 不存在「非… |
| `MISMATCH-API-01` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/comment/api.md:52;DRIFT/comment/api.md:88;DRIFT/comment/api.md:293` | Go `service/comment/common/errmsg/errmsg.go:33`（`NewGrpcErr` ⇒ `status.Error(codes.Code(code), msg)` ⇒ 业务码原样进 grpc-status）＋ `service/comment/api/inter… |
| `DRIFT-CF-009` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:386` | — |
| `DRIFT-CF-011` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:387` | — |
| `DRIFT-LOG-004` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:416` | — |
| `E23` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:371` | Go `service/hot/rpc/hot.go:27`（`func main()` ⇒ 这类 rpc 入口的**产物可执行性**正是此前从未被任何守门查过的那一类）vs RAG `tools/difftest/check-jar-executable.sh:64`（枚举已扩到 `service… |
| `E30` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:378` | Go `service/common/observability/rpc.go:69`（`err := status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ wire description = `context dead… |
| `E31` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | D | 否 | 不影响 · 经分析确认不实现 | DEC-03 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:379` | Go `service/follow/rpc/etc/follow.yaml:20-27`（`DevServer: {Port: 16065, MetricsPath: /metrics, EnableMetrics: true}` ⇒ Go 侧**有**抓取出口）＋ `service/follow… |
| `E46` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:421;DRIFT/global/_README.md:452` | Go `service/comment/rpc/internal/model/comment.go:36`（`m.conn.WithContext(ctx).Transaction(...)` ⇒ ctx 到点**立即**发 CancelRequest ⇒ 取消窗口 = 传播延迟（毫秒级））vs R… |
| `E47` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:426;DRIFT/global/_README.md:858;DRIFT/users/admin/rpc.md:3335` | Go `service/message/rpc/internal/logic/markconversationreadlogic.go:59`（`metrics.ObserveDBError` + 裸 `return nil, err`）vs RAG `service/message/message… |
| `E48` | DRIF | global | 🔴 未闭合 | A 活体 · 混(0/4) | P1 | D | 否 | 不影响 · 经分析确认不实现 | P2-T05 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:427;DRIFT/global/_README.md:750;DRIFT/global/_README.md:776` | Go `service/common/observability/rpc.go:69`（`status.Error(codes.DeadlineExceeded, timeoutCtx.Err().Error())` ⇒ 探针所测的 Go 侧超时分支）vs RAG `service/common/s… |
| `E50` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:438` | Go `~/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2/core/service/serviceconf.go:83`（该文件**共 89 行** ⇒ 台账写 83-90 越界 ⇒ 这条**真缺陷**正是被文件级去噪吃掉的那一条）vs RAG `s… |
| `PIT-GLOBAL-018` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | P2-T05 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/L12-rpcboot-article-comment-like.md:247;DRIFT/global/lanes/L2-rpcgate.md:81;DRIFT/global/lanes/_PROTOCOL.md:182` | Go `service/common/observability/http.go:53`（`ctx, cancel := context.WithTimeout(r.Context(), timeout)` ⇒ 被对拍/被变异的那条 Go 侧 deadline 语义）＋ `service/commo… |
| `PIT-GLOBAL-019` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/_PROTOCOL.md:180;Pitfalls/global/cross-domain.md:618` | Go `service/user/admin/rpc/internal/model/db.go:15`（`db.AutoMigrate(&Admin{}, &AdminInvite{}, &User{})` ⇒ Go 侧上游建表真源随进程走，无「旧 jar」问题）vs RAG `service/us… |
| `PIT-GLOBAL-020` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/_PROTOCOL.md:181;Pitfalls/global/cross-domain.md:785` | Go `service/common/communityfact/process.go:57`（`case <-ctx.Done():` ⇒ Go 侧社区事实投递循环的终止判据）vs RAG `service/common/src/main/java/com/rag/common/community… |
| `T-16` | DRIF | global | 🟡 部分闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/common-tails.md:598` | Go `service/common/observability/rpc.go:38` = `timeoutCtx, cancel := context.WithTimeout(ctx, timeout)`（⇒ ctx 带客户端 deadline ⇒ **有效期限 = min(客户端 deadlin… |
| `DRIFT-MSG-V-020` | DRIF | message | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/message/rpc.md:1671` | Go `service/message/rpc/internal/model/db.go:22`（DSN 钉 `TimeZone=Asia/Shanghai` ⇒ GORM 迁出的 `timestamptz` 存绝对时刻，`time.Time.Unix()` 读出真实 epoch）＋ `:42`（`… |
| `DRIFT-MSGAPI-009` | DRIF | message | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/message/api.md:858` | Go `service/message/api/internal/types/types.go:22`（`Affected int64 \`json:"affected"\`` ⇒ **无 `,string`** ⇒ `encoding/json` 输出 JSON **数字** `{"affecte… |
| `SD-04` | DRIF | message | 🔴 未闭合 | A 活体 · 绿(0) | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/message/proto.md:34` | Go `proto/message.proto:3`（`package message;` ⇒ proto package 本来就是 `message`）＋ `proto/message.proto:5`（`option go_package = "./pb";` ⇒ Go 生成物的语言包名 `pb… |
| `DRIFT-ADMINAPI-012` | DRIF | users | 🟡 部分闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/admin/api.md:2236` | Go `service/user/admin/api/internal/middleware/checkblacklistmiddleware.go:35` = `exists, err := m.Redis.ExistsCtx(r.Context(), blacklistKey)` ＋ `:36`… |
| `U-1` | DRIF | users | 🔴 未闭合 | C 无指针 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/admin/_shared.md:211;DRIFT/users/admin/rpc.md:1475` | — |
| `A-1` | DRIF | article | 🟡 部分闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:360;DRIFT/users/admin/_shared.md:217;DRIFT/users/admin/api.md:1329` | — |
| `G4-11` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:296` | Go `service/article/api/internal/svc/service_context.go:24`（`articleservice.NewArticleService(zrpc.MustNewClient(c.ArticleRpcConf))` ⇒ Go 侧 api 的出向 gR… |
| `G4-3` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:276;DRIFT/comment/vs-common.md:79;DRIFT/favorite/vs-common.md:110` | Go `service/user/admin/api/internal/logic/admin/getuserlistlogic.go:44-48`（`switch st.Code()` 44 · `case codes.Internal:` 45 → `errmsg.ErrorServerComm… |
| `SD-2026-10-24-CAND-09` | Inte | comment | ⏳ 未签字 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/rpc.md:162` | Go `service/comment/rpc/internal/model/db.go:15`（`db.AutoMigrate(&Subject{}, &CommentContent{}, &CommentIndex{}, &CommentLike{}, &CommentDomainFactOut… |
| `DRIFT-003` | DRIF | common | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/common/protection.md:89;DRIFT/message/api.md:79;DRIFT/users/user/api.md:35` | Go `service/like/api/likecenter.go:37`（`server := rest.MustNewServer(c.RestConf)` ⇒ Go 的四层保护是 go-zero `rest.Server` 的**内建行为**，不是 like 域自己的代码）＋ `go-zer… |
| `S1-FOLLOW-013` | DRIF | follow | 🟡 部分闭合 | A 活体 · 混(0/2) | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/vs-common.md:823` | — |
| `E10` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:341` | Go `service/like/rpc/cmd/fact-dispatch/main.go:22`（`func main() { os.Exit(execute()) }` ⇒ Go 的事实投递是**独立进程**，不占 rpc 服务端的启动线程）vs RAG `service/like/like-… |
| `E18` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:358` | Go `service/common/observability/rpc.go:38`（`timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ Go 侧 slow/超时的度量基准）＋ `:66`（`markRPCResult(timeo… |
| `E24` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:372` | Go `service/like/rpc/likeservice/likeservice.go:47`（`NewLikeService(cli zrpc.Client)` ⇒ Go 的客户端桩，目标由 zrpc 解析，**不存在「客户端要在运行时 classpath 上加载 grpc-inproce… |
| `E25` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:373` | Go `service/follow/rpc/internal/config/config.go:29`（`UserRpc zrpc.RpcClientConf` ⇒ Go 的客户端目标由 etcd `Key` 解析；N6/N7 这两条涉及的正是「rpc 客户端目标 + 运行时 classpath」… |
| `E26` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:374` | Go `service/hot/rpc/hot.go:27`（`func main()` ⇒ RAG 侧 jar 可执行性守门要钉的正是这个入口在 fat jar 里可执行）vs RAG `service/hot/hot-rpc/src/test/java/com/rag/hot/hot/rpc/H… |
| `E32` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:380` | Go `service/user/admin/rpc/internal/model/data_model.go:41`（`Status int64`，gorm tag 为 `column:status;default:0` ⇒ **无 `not null`** ⇒ 列可空 ⇒ GORM 对 `int… |
| `E40` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:418` | Go `service/common/response/response.go:3`（Go 仓内文件 ⇒ RAG 台账常以 **Go 仓路径形态**引用它 ⇒ 修前第 1 层判「引用了不存在的文件」= 误报）vs RAG `service/common/src/main/java/com/rag/c… |
| `E41` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:419` | Go `service/common/observability/rpc.go:69`（第 4 层判的就是「台账里 **Go 侧 `file:line` 出处是否悬空**」⇒ 分类结果必须可复现）vs RAG `service/common/src/main/java/com/rag/common/… |
| `E44` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:424` | Go `service/task/rpc/internal/Init/initGoKa.go:178`（`NewProcessor` ⇒ task 域 Go 侧锚点，本轮 `--mutation-test` 覆盖的域）vs RAG `service/task/task-rpc/src/main/ja… |
| `E51` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:439` | Go `~/go/pkg/mod/gorm.io/gorm@v1.31.1/schema/naming.go:89`（`func (ns NamingStrategy) IndexName(table, column string) string` ⇒ `formatName("idx", tabl… |
| `E52` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:440` | Go `~/go/pkg/mod/gorm.io/gorm@v1.31.1/migrator/migrator.go:124`（`if !queryTx.Migrator().HasTable(value)` ⇒ check-then-act，users 域台账引用的那类锚点）vs RAG `ser… |
| `E53` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:441` | Go `~/sdk/go1.25.11/src/net/url/url.go:1001`（`const defaultMaxParams = 10000` ⇒ 标准库通道要定位的目标）vs RAG `service/common/src/main/java/com/rag/common/http/G… |
| `E55` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:443` | Go `service/task/api/task.go:28`（`server := rest.MustNewServer(c.RestConf)` ⇒ 第 3 层所读 task 域台账对应的 Go 侧入口）vs RAG `service/task/task-rpc/src/main/java/c… |
| `E56` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:444` | Go `service/task/rpc/internal/Init/initGoKa.go:131`（`sarama.OffsetOldest` ⇒ task 域台账引用的 Go 侧锚点）vs RAG `service/task/task-rpc/src/main/java/com/rag/tas… |
| `E61` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:449;DRIFT/global/_README.md:839` | Go `service/common/observability/rpc.go:69`（`status.Error(codes.DeadlineExceeded, ...)` ⇒ 第 1 层判「台账引用的 Go 侧出处是否悬空」时的对照面）vs RAG `service/common/src/tes… |
| `E63` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:450` | Go `service/task/api/task.go:28`（`server := rest.MustNewServer(c.RestConf)` ⇒ 原先被**写死**的那个根 `service/task` 对应的 Go 侧入口）vs RAG `service/users/admin/admi… |
| `E64` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:429` | Go `service/user/admin/rpc/admin.go:24`（`func main()` ⇒ 探针所起的 Go 侧栈进程，就绪判据是「端口在监听」）vs RAG `service/users/admin/admin-rpc/src/main/java/com/rag/users/a… |
| `E69` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:434;DRIFT/global/_README.md:563` | Go `service/common/communityfact/authority_http.go:18`（`type AuthorityLookup func(context.Context, string, string) (AuthorityFact, error)` ⇒ 该符号的唯一声明处… |
| `E70` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:435` | Go `~/sdk/go1.25.11/src/net/http/server.go:2458`（`var htmlReplacer = strings.NewReplacer(` ⇒ 标准库通道要定位的目标）＋ `~/go/pkg/mod/github.com/zeromicro/go-zero@… |
| `E9` | DRIF | global | 🔴 未闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:340` | Go `service/like/api/internal/handler/like/likeactionhandler.go:28`（`httpx.OkJsonCtx(r.Context(), w, resp)` ⇒ Go 的 JSON 响应出口，Content-Type 由 go-zero ht… |
| `PIT-GLOBAL-022` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/L5-signoff.md:309;Pitfalls/global/cross-domain.md:706` | Go `service/message/rpc/internal/model/data_model.go:41`（`CreatedAt time.Time` 仅 `autoCreateTime` ⇒ **Go 侧无 `DEFAULT CURRENT_TIMESTAMP`**）vs RAG `serv… |
| `PIT-GLOBAL-023` | DRIF | global | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/L5-signoff.md:310;Pitfalls/global/common-tails.md:288` | Go `go-zero@v1.10.2/rest/engine.go:113-115`（**注册点**：`handle := chn.ThenFunc(route.Handler)` ⇒ `router.Handle(route.Method, route.Path, handle)`）＋ `go-… |
| `SD-2026-10-23-09` | DRIF | global | ⏳ 未签字 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L5-signoff.md:190;Intentional_Deviations/like/api.md:54` | Go `go-zero@v1.10.2/rest/handler/maxconnshandler.go:12-36`（容量拒绝 ⇒ **裸 503**，触发条件是 `MaxConns`，`go-zero@v1.10.2/rest/config.go:52` 默认 10000；Go **无 worke… |
| `G4-19` | DRIF | hot | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/hot/vs-common.md:92` | Go `service/hot/api/internal/handler/routes.go:16-25`（该脚本要比对的 Go 原件：`AddRoutes` ⇒ `GET /articles` ＋ `rest.WithPrefix("/hot/v1")` ⇒ Go 侧 HTTP 端点 = 1）vs… |
| `DRIFT-LIKE-018` | DRIF | like | 🟡 部分闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/like/rpc.md:898;DRIFT/like/rpc.md:1289` | Go `service/like/rpc/internal/mq/etc/mq.yaml:10`（`Consumers: 8`）＋ `go-queue@v1.2.2/kq/config.go:18`（`Consumers int json:",default=8"` ⇒ 默认值也是 8）＋ `go-… |
| `DRIFT-MSGAPI-011` | DRIF | message | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/message/api.md:1499;DRIFT/message/vs-common.md:39` | Go `go-zero@v1.10.2/rest/httpx/responses.go:183`（`w.WriteHeader(code)` ⇒ reason phrase 由 Go 标准库 `net/http` 补 ⇒ 状态行 = `HTTP/1.1 200 OK` / `401 Unauthor… |
| `DRIFT-MSGAPI-012` | DRIF | message | 🟡 部分闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/api.md:1532` | Go `go-zero@v1.10.2/rest/httpx/responses.go:155`（`http.Error(w, err.Error(), http.StatusBadRequest)`）＋ `:153`（gRPC 错误码路径同样走 `http.Error`）＋ Go 标准库 `net… |
| `FB-G2-03` | DRIF | message | 🔴 未闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/message/feedback-to-g4.md:52` | Go `go-zero@v1.10.2/rest/httpx/responses.go:183`（`w.WriteHeader(code)` ⇒ 状态行带 `StatusText` reason phrase）vs RAG `service/common/src/main/java/com/rag/… |
| `FB-G2-04` | DRIF | message | 🟡 部分闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/feedback-to-g4.md:66` | Go `go-zero@v1.10.2/rest/httpx/responses.go:155`（`http.Error(w, err.Error(), http.StatusBadRequest)` ⇒ 响应头写出侧）＋ Go 标准库 `net/http/server.go:2351`（`h.Se… |
| `DRIFT-TASK-API-004` | DRIF | task | 🟡 部分闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/task/_README.md:315;DRIFT/task/_README.md:355;DRIFT/task/api.md:510` | Go `go-zero@v1.10.2/rest/internal/header/headers.go:17`（`ContentTypeJson = "application/json; charset=utf-8"` ⇒ **分号后有空格**）＋ `go-zero@v1.10.2/rest/htt… |
| `DRIFT-USERRPC-V-002` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/users/user/rpc.md:531;DRIFT/users/user/rpc.md:1706` | Go `service/user/user/rpc/internal/model/data_model.go:11`（`Status     int64             \`gorm:"column:status;default:0"\`` ⇒ **无 `not null` tag** ⇒ … |
| `T-0` | DRIF | users | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/users/_shared.md:1335` | — |
| `U-2` | DRIF | users | 🟡 部分闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | GAP-04 | 无可复算指针 · 结论不可复算 | `DRIFT/users/admin/_shared.md:212;DRIFT/users/admin/rpc.md:1476` | Go `service/user/admin/rpc/internal/model/data_model.go:41`（admin 侧的 `User` 模型同样 `Status int64` + gorm tag `column:status;default:0` ⇒ **无 `not null`*… |
| `SD-2026-10-23-05` | Inte | _INDEX.md | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/_INDEX.md:233;Intentional_Deviations/like/proto.md:11` | — |
| `SD-2026-10-23-06` | Inte | _INDEX.md | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/_INDEX.md:102;Intentional_Deviations/like/rpc.md:13;Intentional_Deviations/message/rpc.md:174` | — |
| `SD-2026-10-23-07` | Inte | _INDEX.md | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/_INDEX.md:103;Intentional_Deviations/_INDEX.md:118;Intentional_Deviations/like/rpc.md:82` | — |
| `DRIFT-INFRA-001` | DRIF | _cross | 🔴 未闭合 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/_cross/open-items.md:59` | Go `service/message/rpc/internal/model/db.go:42`（`db.AutoMigrate(&Notification{}, &Conversation{}, &ConversationMessage{})` ⇒ Go 侧**无版本账本 / 无 baseline… |
| `SD-2026-10-23-02` | Inte | _cross | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/_cross/signed-deviations.md:57` | — |
| `SD-2026-10-23-03` | Inte | _cross | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/_cross/signed-deviations.md:71` | — |
| `A-4` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:363` | Go `service/article/api/etc/article-api.yaml:1`（Go 真源侧 **不含** 任何根级 compose / `.env.example` 治理面 ⇒ Go 侧**无对应物** ⇒ 不构成 Go 对照项）＋ `service/article/api/art… |
| `DRIFT-001` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/api.md:877;DRIFT/article/api.md:894;DRIFT/common/deadline.md:2` | Go `go-zero@v1.10.2/rest/router/patrouter.go:72-82`（`if !ok { pr.handleNotFound(w, r); return }` ⇒ 路径未注册 ⇒ **404**；`if pr.notAllowed != nil { pr.notAl… |
| `DRIFT-ARTICLE-API-001` | DRIF | article | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/article/api.md:576;Intentional_Deviations/article/api.md:25` | — |
| `DRIFT-ARTICLE-API-006` | DRIF | article | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/article/api.md:703;DRIFT/global/vs-common.md:225;Intentional_Deviations/article/api.md:26` | Go `go-zero@v1.10.2/rest/httpx/requests.go:21`（`maxBodyLen = 8 << 20  // 8MB` ⇒ `io.LimitReader` **静默截断** ⇒ 解析器报 `unexpected EOF` ⇒ 400 + body `"unexp… |
| `DRIFT-ARTICLE-API-009` | DRIF | article | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:750;Intentional_Deviations/article/api.md:27` | — |
| `DRIFT-ARTICLE-RPC-007` | DRIF | article | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/article/rpc.md:518` | Go `go-queue@v1.2.2/kq/config.go:12`（`Brokers []string` ⇒ 逐通道独立声明）＋ `go-queue@v1.2.2/kq/pusher.go:44-47`（`NewPusher` ⇒ `kafka.Writer{Addr: kafka.TCP(a… |
| `F-3` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/api.md:1291;DRIFT/favorite/vs-common.md:225` | Go `go-zero@v1.10.2/rest/httpx/util.go:44-53`（同名 key 多值收成 `[]string` 存 map，空值 `continue`）＋ `go-zero@v1.10.2/core/mapping/unmarshaler.go:835-846`（`u.op… |
| `FIX-06` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/rpc.md:1161;DRIFT/article/rpc.md:1207` | Go `service/article/rpc/internal/mqs/article_sync_result_consumer.go:147`（`notifyAuthor, notifyTitle = article.AuthorID, article.Title` ⇒ 通知标题取**当前行**… |
| `FIX-08` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/rpc.md:1345;DRIFT/article/rpc.md:1394` | Go `service/article/rpc/metrics/metrics.go:46`（`Buckets: []float64{0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1}` ⇒ **8 个**桶边界 + 固有 `+Inf` = 9）vs RAG `… |
| `FIX-10` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/api.md:1839;DRIFT/article/api.md:1914` | Go `go-zero@v1.10.2/rest/httpx/util.go:15`（`maxFormParamCount = 2048`）＋ `:48-53`（`if n < maxFormParamCount { filtered = append(...); n++ } else { retu… |
| `FIX-11` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/api.md:1966;DRIFT/article/api.md:1980` | Go `go-zero@v1.10.2/zrpc/config.go:30`（`NonBlock bool \`json:",default=true"\`` ⇒ **声明点**，默认 true）＋ `go-zero@v1.10.2/zrpc/client.go:65-66`（**消费点**：`if… |
| `FIX-17` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/rpc.md:1875;DRIFT/article/rpc.md:1950` | Go `service/article/rpc/internal/logic/create_article_logic.go:94`（`SecondaryTags: model.StringArray(in.SecondaryTags)` ⇒ **直接类型转换**（非 `append`）⇒ 不带二级… |
| `G4-10` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:295` | Go `service/article/api/internal/handler/routes.go:1`（Go 真源仓的**领域无关**结构性标志文件之一 —— 共享实现的判定标志必须是「与域无关」的文件，如 `go.mod` / `proto/` / `service/`，否则「只跑某域的脚本」… |
| `G4-12` | DRIF | article | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:297` | Go `service/comment/rpc/internal/model/data_model.go:55-64`（`type ReportRecord struct` ⇒ **8 列**：`id` / `user_id` / `comment_id` / `target_type` / `ta… |
| `G4-2` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:275;DRIFT/comment/vs-common.md:78;DRIFT/favorite/vs-common.md:109` | Go `service/common/observability/rpc.go:38`（`timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ Go 侧 ctx deadline ⇒ GORM 的 `QueryContext`/`Exe… |
| `G4-6` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:279;DRIFT/favorite/vs-common.md:112` | Go `go-zero@v1.10.2/zrpc/internal/config.go:14`（`Breaker bool \`json:",default=true"\`` ⇒ client breaker **默认开**）＋ `go-zero@v1.10.2/zrpc/internal/clie… |
| `G4-7` | DRIF | article | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:281` | Go `service/article/rpc/internal/model/db.go:55-60`（Go 侧 `db.AutoMigrate` ⇒ **无版本账本 / 无 baseline** ⇒ 账本机制是 RAG 侧独有 ⇒ 该脚本的 `KNOWN_BLOCKED` 只可能指向 RAG 侧组… |
| `G4-9` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:293` | Go `service/common/observability/mq.go:12`（`func TraceConsumer(ctx context.Context, tracerName, spanName string, …)` ⇒ Go 侧 article-rpc 的两个 consumer 本… |
| `L-10` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:100;DRIFT/article/vs-common.md:104;DRIFT/article/vs-common.md:191` | Go `service/article/rpc/internal/logic/create_article_logic.go:42`（`tracer := otel.Tracer("article-rpc")` + span `CreateArticle`）＋ `service/article/rp… |
| `L-6` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:76;DRIFT/favorite/vs-common.md:89` | Go `service/article/rpc/internal/model/db.go:55`（Go 侧 **AutoMigrate** ⇒ 排序图里**没有** MP 自动配置这一环 ⇒ **无「排序桥」对应物** ⇒ 不构成 Go 对照项）vs RAG `service/common/src/… |
| `L-7` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:97;DRIFT/favorite/vs-common.md:91` | Go `go-zero@v1.10.2/rest/router/patrouter.go:60`（`reqPath := path.Clean(r.URL.Path)` ⇒ Go 侧路径归一由 **router 层**承担 ⇒ 无「filter 副本」概念）vs RAG `service/commo… |
| `L-8` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/vs-common.md:98;DRIFT/favorite/vs-common.md:94` | Go `go-zero@v1.10.2/rest/engine.go:74`（`chn.Append(handler.Authorize(...))` ⇒ **JWT 验签在 Authorize 内侧**）＋ `go-zero@v1.10.2/rest/engine.go:110-111`（`chn… |
| `Latin-1` | DRIF | article | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/api.md:2302` | Go 1.25.11 `src/strconv/quote.go:522`（`func IsPrint(r rune) bool`）＋ `:524-533`（Latin-1 快路径：`0x20..0x7E` 可打印；**`0xA1..0xFF` 可打印，唯一例外 `0xAD`**（软连字符）；其余 … |
| `O-5` | DRIF | article | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/rpc.md:129` | Go `service/article/rpc/internal/model/db.go:55`（`if err := db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{}); err != nil {` ⇒ Go 侧**用 GORM AutoMi… |
| `S-5` | DRIF | article | 🔴 未闭合 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/rpc.md:2400;DRIFT/article/rpc.md:2606` | Go `service/article/rpc/internal/mqs/article_consumer.go:154-155`（`if l.svcCtx.SecurityRpc == nil { return fmt.Errorf("content security client not ini… |
| `U-4` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/article/rpc.md:2423;DRIFT/article/rpc.md:2455;DRIFT/users/admin/_shared.md:214` | — |
| `U-9` | DRIF | article | 🟡 部分闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/article/rpc.md:2510` | Go `go-queue@v1.2.2/kq/queue.go:240`（`if err := q.consumer.CommitMessages(ctx, msg); err != nil {` ⇒ 提交的是**这条消息自己的位点**）＋ `kafka-go@v0.4.47/reader.go:1… |
| `C-1` | DRIF | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | P7-T01 | — | `DRIFT/comment/vs-common.md:57;DRIFT/comment/vs-common.md:218;Intentional_Deviations/common/_signed-deviations.md:156` | — |
| `PIT-GLOBAL-051` | DRIF | comment | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/comment/vs-common.md:29;Pitfalls/global/cross-domain.md:2014` | Go `service/comment/rpc/internal/logic/report_comment_logic.go:88-92`（`result = "sys_fail"` → `span.RecordError(err)` → `err = errmsg.NewGrpcErr(errms… |
| `SD-2026-10-23-04` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/proto.md:61;Intentional_Deviations/_INDEX.md:222;Intentional_Deviations/_INDEX.md:248` | — |
| `SD-2026-10-24-CAND-02` | Inte | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/rpc.md:95` | — |
| `SD-2026-10-24-CAND-04` | Inte | comment | ⛔ 已失效 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/api.md:28` | — |
| `SD-2026-10-24-CAND-06` | Inte | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/rpc.md:274` | — |
| `SD-2026-10-24-CAND-07` | Inte | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/api.md:113` | — |
| `CF-009` | DRIF | common | ✅ 已一致 | B 静态 | P3 | D | 是 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:841` | — |
| `T-10` | DRIF | common | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/common/alignment-2026-10-23.md:697;DRIFT/common/alignment-2026-10-23.md:718;DRIFT/global/_README.md:97` | Go `service/common/observability/rpc.go:38`（`timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ deadline **来自 ctx**，Go 侧**没有**「手动设过期时刻」的原语）；超时… |
| `T-6` | DRIF | common | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | GAP-10 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/common/alignment-2026-10-23.md:693;DRIFT/common/alignment-2026-10-23.md:714;DRIFT/global/_README.md:92` | Go `go-zero@v1.10.2/rest/internal/header/headers.go:17`（`ContentTypeJson = "application/json; charset=utf-8"`）＋ `rest/httpx/responses.go:182`（`w.Heade… |
| `T-7` | DRIF | common | 🟡 部分闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:694;DRIFT/common/alignment-2026-10-23.md:715;DRIFT/global/_README.md:93` | Go 标准库 `net/http/server.go:1596-1611`（`writeStatusLine` ⇒ 其中 `:1602-1605` 用 `StatusText(code)` 把 **reason phrase** 写进状态行 ⇒ `HTTP/1.1 200 OK`）＋ `go-zer… |
| `DEV-FAV-API-001` | DRIF | favorite | ✅ 已一致 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/api.md:275;Intentional_Deviations/favorite/api.md:31;Intentional_Deviations/favorite/api.md:40` | — |
| `DEV-FAV-API-002` | DRIF | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/favorite/api.md:446;Intentional_Deviations/favorite/api.md:32;Intentional_Deviations/favorite/api.md:56` | — |
| `DEV-FAV-COMMON-001` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/common.md:23;Intentional_Deviations/favorite/common.md:38` | — |
| `DEV-FAV-COMMON-002` | Inte | favorite | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/favorite/common.md:24;Intentional_Deviations/favorite/common.md:52` | — |
| `DEV-FAV-RPC-001` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:37;Intentional_Deviations/favorite/rpc.md:87` | — |
| `DEV-FAV-RPC-002` | DRIF | favorite | ⛔ 已撤销 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:472;Intentional_Deviations/favorite/rpc.md:101` | — |
| `DEV-FAV-RPC-003` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:38;Intentional_Deviations/favorite/rpc.md:112` | — |
| `DEV-FAV-RPC-004` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:39;Intentional_Deviations/favorite/rpc.md:126` | — |
| `DEV-FAV-RPC-005` | DRIF | favorite | ⛔ 已撤销 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:324;Intentional_Deviations/favorite/rpc.md:167` | — |
| `DEV-FAV-RPC-006` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:40;Intentional_Deviations/favorite/rpc.md:139` | — |
| `DEV-FAV-RPC-007` | Inte | favorite | ✅ 仍成立 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:41;Intentional_Deviations/favorite/rpc.md:153` | — |
| `E62` | DRIF | favorite | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | P2-T05 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/favorite/rpc.md:3111;DRIFT/favorite/vs-common.md:28;DRIFT/global/_README.md:428` | Go `service/common/observability/rpc.go:38`（`timeoutCtx, cancel := context.WithTimeout(ctx, timeout)` ⇒ Go 侧 ctx deadline ⇒ PG 侧**到点立即发 CancelRequest*… |
| `E68` | DRIF | favorite | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/favorite/vs-common.md:30;DRIFT/global/_README.md:433;DRIFT/global/_README.md:541` | Go `service/favorite/rpc/internal/logic/delete_favorite_folder_logic.go:96`（该文件**只有 96 行** ⇒ 台账里写成 `delete_favorite_folder_logic.go:336-378` 的锚点**行号在范… |
| `PITFALL-FOLLOW-005` | DRIF | follow | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/follow/vs-common.md:300;DRIFT/follow/vs-common.md:424;DRIFT/follow/vs-common.md:1215` | Go `service/common/observability/http.go:36`（`NewHTTPMiddleware` —— 只被 **api 侧** `service/follow/api/followcenter.go:42` 等 7 个 api main 引用；**rpc 侧零引用*… |
| `S1-FOLLOW-002` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | D | 否 | 台账已写 · —— **阶段 2 也不做**（签字决定保留全局闸门）<br>⚠️ **若将来要重开**：必须**先撤销签字**（用户拍 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:97;DRIFT/follow/stage1-admission.md:536;DRIFT/follow/vs-common.md:1208` | — |
| `B-05` | DRIF | global | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/vs-common.md:3841` | Go `go-zero@v1.10.2/zrpc/client.go:70-71`（Go 侧每次调用**无条件**装 2000ms 上界）vs RAG `service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc… |
| `CD-7` | DRIF | global | 🟡 部分闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/cross-domain.md:47;DRIFT/global/cross-domain.md:141` | Go `service/like/rpc/internal/model/db.go:15`（`db.AutoMigrate(&LikeRecord{}, &LikeConsumeInbox{}, &LikeOutboxEvent{}, &LikeDomainFactOutbox{}, &LikeFa… |
| `CYCLE-001` | DRIF | global | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/cycle-deps.md:11;Intentional_Deviations/global/cycle-deps.md:20` | — |
| `DRIFT-018` | DRIF | global | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/lanes/L9-interrupt.md:260;DRIFT/users/_shared.md:67` | Go `service/common/observability/http.go:80`（`case <-ctx.Done():` ⇒ Go 的 ctx 取消会传播到在途处理）＋ `service/common/observability/rpc.go:38`／`:68`（`timeoutCtx, … |
| `E34` | DRIF | global | 🟡 部分闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/_README.md:383` | Go `service/comment/rpc/internal/trace/trace.go:45`（`otel.SetTracerProvider(tp)` ⇒ 真源自己的 OTel 装配点，**在进程启动代码里**）＋ `go-zero@v1.10.2/core/service/service… |
| `E36` | DRIF | global | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:385` | Go `service/common/observability/rpc.go:69`（`codes.DeadlineExceeded` ⇒ grpc-go 的 `codes.Code.String()` = 驼峰 `DeadlineExceeded` ⇒ `err.Error()` = `rpc … |
| `E58` | DRIF | global | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:446;DRIFT/users/user/rpc.md:2257;DRIFT/users/user/rpc.md:2328` | Go `service/user/user/rpc/internal/logic/getuserlogic.go:44`（`status.Error(codes.Internal, "DB查询失败")`）＋ `service/user/admin/rpc/internal/logic/getuser… |
| `FB-01` | DRIF | global | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/vs-common.md:352;DRIFT/global/vs-common.md:1527;DRIFT/like/vs-common.md:80` | Go `go-zero@v1.10.2/rest/router/patrouter.go:48`（注册侧 `cleanPath := path.Clean(reqPath)`）＋ `go-zero@v1.10.2/rest/router/patrouter.go:60`（匹配侧 `reqPath :… |
| `FB-03` | DRIF | global | 🔴 未闭合 | A 活体 · 环境(2) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/vs-common.md:402;DRIFT/like/vs-common.md:439;DRIFT/like/vs-common.md:448` | Go `go-zero@v1.10.2/zrpc/server.go:157-159`（`if c.Middlewares.Breaker` ⇒ 无条件装 breaker 拦截器）＋ `:160-163`（`if c.CpuThreshold > 0 { ... UnarySheddingInter… |
| `G4-A4-02` | DRIF | global | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/vs-common.md:3752;DRIFT/global/vs-common.md:3774` | Go `go-zero@v1.10.2/core/stores/redis/redisclientmanager.go:32-40`（`red.NewClient(&red.Options{...})` **未设任何命令超时** ⇒ 「Go 侧无命令超时」是**另一条轴**，不是本条判据）vs RA… |
| `L4-01` | DRIF | global | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/lanes/L4-flyway.md:172` | Go `service/favorite/rpc/internal/model/db.go:33`（`db.AutoMigrate(&FavoriteFolder{}, &FavoriteItem{}, &FavoriteFactOutbox{})` ⇒ Go 侧建表成功即有 `favorite_f… |
| `L4-02` | DRIF | global | 🟡 部分闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L4-flyway.md:173` | Go `service/article/rpc/internal/model/db.go:55`（`db.AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` ⇒ **Go 侧无版本化账本、无 baseline、无 advisory lock** ⇒… |
| `L4-03` | DRIF | global | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/lanes/L4-flyway.md:174` | Go `service/article/rpc/internal/model/db.go:55`（`db.AutoMigrate(...)` ⇒ **Go 侧无版本化账本 ⇒ 无 advisory lock** ⇒ 本条**无 Go 对照物**；`docs/DRIFT/global/lanes/L4… |
| `N-1` | DRIF | global | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L10-ddldeep.md:332;DRIFT/users/user/api.md:2311;Intentional_Deviations/users/admin/_shared.md:65` | — |
| `N-2` | DRIF | global | ✅ 仍成立 | A 活体 · 红(1) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L10-ddldeep.md:333;DRIFT/users/user/rpc.md:1047;Intentional_Deviations/users/user/rpc.md:18` | — |
| `N-3` | DRIF | global | ⛔ 已失效 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L10-ddldeep.md:334;Intentional_Deviations/users/admin/_shared.md:111;Intentional_Deviations/users/admin/_shared.md:115` | — |
| `N-4` | DRIF | global | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L10-ddldeep.md:335;DRIFT/users/admin/rpc.md:2255;Intentional_Deviations/users/admin/_shared.md:84` | — |
| `SD-2026-10-24-04` | DRIF | global | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/lanes/L5-signoff.md:134;DRIFT/message/rpc.md:2717;Intentional_Deviations/message/rpc.md:96` | — |
| `SD-2026-10-24-10` | DRIF | global | ⏳ 未签字 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/common-tails.md:547;Intentional_Deviations/like/api.md:87` | Go `go-zero@v1.10.2/zrpc/config.go:31`（`Timeout int64 json:",default=2000"`）＋ `go-zero@v1.10.2/zrpc/client.go:70-71`（每次调用装 2000ms 上界）＋ `go-zero@v1.10.… |
| `SD-2026-10-25-01` | Inte | global | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/common-tails.md:11` | — |
| `SD-2026-10-25-02` | Inte | global | ⛔ 已撤销 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/common-tails.md:125;Intentional_Deviations/global/common-tails.md:226;Intentional_Deviations/global/common-tails.md:355` | — |
| `SD-2026-10-25-03` | Inte | global | ⛔ 已撤销 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/cross-domain.md:13` | — |
| `SD-2026-10-25-04` | Inte | global | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/global/common-tails.md:205` | — |
| `SD-2026-10-25-CAND-10` | DRIF | global | ⏳ 未签字 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L5-signoff.md:245;Intentional_Deviations/hot/api.md:33;Intentional_Deviations/hot/api.md:421` | Go 标准库 `net/http/server.go:2049-2050`（`const publicErr = "431 Request Header Fields Too Large"` ＋ `fmt.Fprintf(c.rwc, "HTTP/1.1 "+publicErr+errorHeade… |
| `SD-2026-10-25-CAND-14` | Inte | global | ⏳ 未签字 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/common-tails.md:436` | Go 标准库 `net/http/server.go:1596-1611`（`writeStatusLine` ⇒ 其中 `:1602-1605` 用 `StatusText(code)` 把 **reason phrase** 写进状态行）＋ `go-zero@v1.10.2/rest/httpx… |
| `SD-2026-10-25-CAND-15` | Inte | global | ⏳ 未签字 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/common-tails.md:510` | Go `go-zero@v1.10.2/rest/router/patrouter.go:77-82`（405 出口在 router 层 ⇒ 不经 TraceHandler ⇒ **无** `Traceparent`）＋ `go-zero@v1.10.2/rest/engine.go:113-115… |
| `SD-HOT-API-006` | DRIF | global | ⛔ 已撤销 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L1-delivery.md:744;Intentional_Deviations/hot/api.md:28;Intentional_Deviations/hot/api.md:45` | — |
| `T-12` | DRIF | global | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/common-tails.md:519;DRIFT/global/common-tails.md:642;DRIFT/global/lanes/L5-signoff.md:118` | Go `go-zero@v1.10.2/rest/router/patrouter.go:77-82`（405 出口在 **router 层** ⇒ 不经 TraceHandler）＋ `go-zero@v1.10.2/rest/engine.go:113-115`（**注册点**：`handle … |
| `T-13` | DRIF | global | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/common-tails.md:561;DRIFT/global/lanes/L1-delivery.md:296` | Go `service/common/observability/http.go:138-145` ＋ `:158-174`（`observabilityResponseWriter` 是**纯 buffer**：`WriteHeader`/`Write` 从不碰底层 `http.ResponseW… |
| `UTF-8` | DRIF | global | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/global/vs-common.md:949` | Go `go-zero@v1.10.2/core/trace/agent.go:105-127`（`startAgent` 装 `sdktrace.NewTracerProvider(opts...)`（`:126`）＋ `otel.SetTracerProvider(tp)`（`:127`）⇒ 那… |
| `DRIFT-HOTAPI-026` | DRIF | hot | 🟡 部分闭合 | A 活体 · 红(1) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/hot/api.md:1672` | Go `go-zero@v1.10.2/rest/handler/maxbyteshandler.go:19-22`（`if r.ContentLength > n` ⇒ 裸 `w.WriteHeader(http.StatusRequestEntityTooLarge)` ⇒ **413 ＋ 空 … |
| `DRIFT-HOTAPI-027` | DRIF | hot | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/hot/api.md:1720` | Go `go-zero@v1.10.2/rest/engine.go:145-147`（**路由循环内** `chn.Append(handler.MaxConnsHandler(ng.conf.MaxConns))` ⇒ 每条路由各建一个）＋ `rest/handler/maxconnshandl… |
| `DRIFT-HOTRPC-005` | DRIF | hot | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/hot/rpc.md:723;DRIFT/hot/rpc.md:990` | Go `service/hot/rpc/hot.go:55-58`（`saramaConf := sarama.NewConfig()` ⇒ 用**库默认值**）＋ `sarama@v1.50.2/config.go:588-589`（**消费点**：`Consumer.Offsets.AutoCo… |
| `G-HOT-001` | DRIF | hot | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/hot/rpc.md:167` | Go `service/hot/rpc/internal/mqs/hot_handler.go:132`（`OnPartitionsRevoked` 内 `h.hkMu.Lock()`）→ `:136`（`h.syncToRedis(ctx, pid)`）→ `:196`（`syncToRedis`… |
| `H-4` | DRIF | hot | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/hot/vs-common.md:70` | Go `service/hot/rpc/etc/hot.yaml:2`（`ListenOn: 0.0.0.0:8083` ⇒ Go 侧 hot-rpc 的 gRPC 端口 = 8083；`hot2.yaml:2` = 8084、`hot3.yaml:2` = 8085 ⇒ 三副本）＋ `servic… |
| `SD-1` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:798;Intentional_Deviations/hot/rpc.md:44;Intentional_Deviations/hot/rpc.md:179` | — |
| `SD-2` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:45;Intentional_Deviations/hot/rpc.md:197` | — |
| `SD-3` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:46;Intentional_Deviations/hot/rpc.md:211` | — |
| `SD-HOT-API-001` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:23;Intentional_Deviations/hot/api.md:53` | — |
| `SD-HOT-API-002` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:24;Intentional_Deviations/hot/api.md:85` | — |
| `SD-HOT-API-003` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:25;Intentional_Deviations/hot/api.md:99` | — |
| `SD-HOT-API-004` | Inte | hot | ⛔ 已失效 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:26;Intentional_Deviations/hot/api.md:43;Intentional_Deviations/hot/api.md:111` | — |
| `SD-HOT-API-005` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:27;Intentional_Deviations/hot/api.md:158` | — |
| `SD-HOT-API-007` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:29;Intentional_Deviations/hot/api.md:280` | — |
| `SD-HOT-API-008` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:30;Intentional_Deviations/hot/api.md:327` | — |
| `SD-HOT-API-009` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:31;Intentional_Deviations/hot/api.md:360` | — |
| `SD-HOT-API-010` | Inte | hot | ✅ 仍成立 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:32;Intentional_Deviations/hot/api.md:377` | — |
| `SD-HOT-RPC-001` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:40;Intentional_Deviations/hot/rpc.md:52` | — |
| `SD-HOT-RPC-002` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:41;Intentional_Deviations/hot/rpc.md:76` | — |
| `SD-HOT-RPC-003` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:42;Intentional_Deviations/hot/rpc.md:111` | — |
| `SD-HOT-RPC-004` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:43;Intentional_Deviations/hot/rpc.md:137` | — |
| `D-1` | DRIF | like | ⛔ 已失效 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/like/vs-common.md:67;DRIFT/message/vs-common.md:69;DRIFT/task/vs-common.md:61` | — |
| `OP-09` | DRIF | like | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/like/rpc.md:504;DRIFT/message/rpc.md:505` | Go `service/like/rpc/internal/model/domain_fact.go:76-138`（`appendLikeFact`：遗留未版本化事实 ⇒ 报错 `:101-110`；原子取号 `:111-116`；版本须 ∈ [1, 2^53-1] `:117-119`；payl… |
| `U-01` | DRIF | like | 🟡 部分闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | GAP-03 | — | `DRIFT/like/api.md:824;DRIFT/like/api.md:837` | Go `service/like/rpc/cmd/fact-dispatch/main.go:72` = `communityfact.NewDCEventSender(endpoint, token, …)` ＋ `service/common/communityfact/wire.go:97` … |
| `SD-2026-10-23-08` | DRIF | message | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | DEC-02 | — | `DRIFT/message/rpc.md:2068;Intentional_Deviations/_INDEX.md:104;Intentional_Deviations/_INDEX.md:119` | — |
| `SD-2026-10-24-01` | DRIF | message | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2714;Intentional_Deviations/_INDEX.md:105;Intentional_Deviations/article/rpc.md:23` | — |
| `SD-2026-10-24-02` | DRIF | message | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2715;Intentional_Deviations/_INDEX.md:106;Intentional_Deviations/article/rpc.md:164` | — |
| `ADJ-01` | DRIF | users | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/_shared.md:965` | Go `service/user/user/rpc/internal/model/data_model.go:11`（`Status int64 gorm:"column:status;default:0"` ⇒ 值类型 · 无 `not null` ⇒ GORM 恒写 0 ⇒ 该列实际永不出现 N… |
| `D-015` | DRIF | users | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/user/api.md:1973` | Go `service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35`（`exists, err := m.Redis.ExistsCtx(r.Context(), blackListKey)`）＋ `checkbl… |
| `DRIFT-ADMINAPI-013` | DRIF | users | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/admin/api.md:2448` | Go `service/user/admin/api/internal/handler/routes.go:15`（生产侧等价面；Go 全仓 `forPort(0)` ／ `new ServerSocket(0)` ／ `RANDOM_PORT` 三个模式串 **0 命中** ⇒ Go 侧无对应物 … |
| `DRIFT-ARPC-007` | DRIF | users | 🔴 未闭合 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/users/admin/rpc.md:1203` | Go `service/user/admin/rpc/internal/logic/getselflogic.go:37`（`status.Error(codes.NotFound, …)` 的字面量在磁盘上即**转码损坏的乱码字节**；同语义在 `createadminlogic.go:50` 同… |
| `DRIFT-ARPC-008` | DRIF | users | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/admin/rpc.md:1302` | Go `service/user/admin/rpc/internal/model/data_model.go:41`（`Status int64 gorm:"column:status;default:0"` ⇒ 值类型 · 无 not null ⇒ 该列恒写 0、实际永不出现 NULL）＋ `s… |
| `DRIFT-ARPC-011` | DRIF | users | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:3118` | Go `service/user/admin/rpc/internal/logic/getuserlogic.go:39`（DB 出口统一改写 `status.Error(codes.Internal, …)` ⇒ admin 域 26 处 `codes.Internal`）＋ `service/u… |
| `DRIFT-ARPC-014` | DRIF | users | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/admin/rpc.md:4435` | Go `service/user/admin/rpc/admin.go:36`（`zrpc.MustNewServer(c.RpcServerConf, …)` ⇒ 生产侧等价面；Go 全仓 `forPort(0)` ／ `new ServerSocket(0)` ／ `RANDOM_PORT` 三… |
| `DRIFT-USERAPI-017` | DRIF | users | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/user/api.md:3635` | Go `service/user/user/api/internal/handler/routes.go:15`（生产侧等价面；Go 全仓 `forPort(0)` ／ `new ServerSocket(0)` ／ `RANDOM_PORT` 三个模式串 **0 命中** ⇒ Go 侧无对应物 ⇒… |
| `DRIFT-USERRPC-V-001` | DRIF | users | 🔴 未闭合 | A 活体 · 混(0/1) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/user/rpc.md:473;DRIFT/users/user/rpc.md:1360` | Go `service/user/user/rpc/internal/model/data_model.go:11`（`Status int64 gorm:"column:status;default:0"` ⇒ 值类型 ⇒ GORM 恒写 0 ⇒ 该列永不出现 NULL）＋ `service/us… |
| `DRIFT-USERRPC-V-016` | DRIF | users | 🔴 未闭合 | A 活体 · 混(0/1) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/user/rpc.md:2714` | Go `service/user/user/rpc/user.go:36`（`zrpc.MustNewServer(c.RpcServerConf, …)` ⇒ 生产侧等价面；Go 全仓 `forPort(0)` ／ `new ServerSocket(0)` ／ `RANDOM_PORT` 三个模… |
| `N-5` | Inte | users | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/users/admin/_shared.md:99` | — |
| `N-6` | Inte | users | ⛔ 已失效 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/users/admin/_shared.md:116` | — |
| `Round-2` | DRIF | users | 🔴 未闭合 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/vs-common.md:5072` | Go `service/user/common/jwt/jwt.go:12`（`token := jwt.New(jwt.SigningMethodHS256)` ⇒ 签名算法钉死 HS256）vs RAG `service/users/users-common/src/main/java/com/… |
| `SD-2026-10-23-01` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:1415;Intentional_Deviations/_INDEX.md:101;Intentional_Deviations/_INDEX.md:210` | — |
| `SD-2026-10-25-CAND-16` | Inte | users | ⏳ 未签字 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/_shared.md:18` | Go `service/user/user/rpc/internal/model/data_model.go:14`（`CreateTime time.Time gorm:"column:create_time;autoCreateTime"` ⇒ tag 里**无** `default`）＋ `d… |
| `SD-2026-10-25-CAND-17` | Inte | users | ⏳ 未签字 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/_shared.md:110` | Go `service/user/user/rpc/internal/model/data_model.go:11`（`Status int64 gorm:"column:status;default:0"` ⇒ 有 `default:0`、无 `not null` ⇒ GORM 建出可空列）＋ `… |
| `SD-USERAPI-001` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/user/api.md:12` | — |
| `SD-USERAPI-002` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/user/api.md:106;Intentional_Deviations/users/user/api.md:348` | — |
| `SD-USERAPI-003` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/user/api.md:265` | — |
| `SD-USERSCOMMON-001` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/common.md:12` | — |
| `U-5` | DRIF | users | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/users/admin/_shared.md:215` | — |
| `UNRESOLVED-ADMINAPI-005` | DRIF | users | 🟡 部分闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/admin/api.md:1005;DRIFT/users/admin/api.md:1588` | Go `service/user/admin/api/internal/svc/servicecontext.go:27`（`AdminRpc: adminservice.NewAdminService(zrpc.MustNewClient(c.AdminRpc)),` ⇒ 下游不可达即 `logx… |
| `V-002` | DRIF | users | 🔴 未闭合 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/user/rpc.md:995;DRIFT/users/vs-common.md:1578` | Go `service/user/user/rpc/internal/model/data_model.go:11`（`gorm:"column:status;default:0"` ⇒ 值类型 · 无 `not null` ⇒ 建出可空列，但 GORM 恒写 0 ⇒ 实际永不出现 NULL）vs … |
| `V-004` | DRIF | users | 🔴 未闭合 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已闭合 · 差异已消失 ⇒ 无待实现项 | `DRIFT/users/_shared.md:777` | Go `service/user/user/rpc/internal/logic/avatarhelpers.go:18`（`createTime = item.CreateTime.Format(time.RFC3339)` ⇒ 按进程本地时区渲染带偏移）vs RAG `service/users… |
| `V-013` | DRIF | users | 🔴 未闭合 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/rpc.md:2353` | Go `service/user/user/rpc/internal/logic/getuserlogic.go:44`（`status.Error(codes.Internal, "DB查询失败")` ⇒ logic 层根本不返回 1015）vs RAG `service/users/user/u… |
| `D-2` | DRIF | like | 待核实 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/like/vs-common.md:68;DRIFT/message/vs-common.md:70;DRIFT/task/vs-common.md:62` | — |
| `UNRESOLVED-USERSCOMMON-002` | DRIF | users | 待核实 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:543` | — |
| `DRIFT-MSG-V-001` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:26;DRIFT/_cross/message-domain-wide.md:148;DRIFT/_cross/message-domain-wide.md:161` | — |
| `DRIFT-MSG-V-002` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:27;DRIFT/_cross/message-domain-wide.md:149;DRIFT/_cross/message-domain-wide.md:184` | — |
| `DRIFT-MSG-V-003` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:28;DRIFT/_cross/message-domain-wide.md:150;DRIFT/_cross/message-domain-wide.md:397` | — |
| `DRIFT-MSG-V-004` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:29;DRIFT/_cross/message-domain-wide.md:151;DRIFT/_cross/message-domain-wide.md:267` | — |
| `DRIFT-MSG-V-005` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:30;DRIFT/_cross/message-domain-wide.md:152;DRIFT/_cross/message-domain-wide.md:268` | — |
| `DRIFT-MSG-V-006` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:31;DRIFT/_cross/message-domain-wide.md:153;DRIFT/_cross/message-domain-wide.md:206` | — |
| `DRIFT-MSG-V-007` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:32;DRIFT/_cross/message-domain-wide.md:154;DRIFT/_cross/message-domain-wide.md:316` | — |
| `DRIFT-MSG-V-008` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:33;DRIFT/_cross/message-domain-wide.md:155;DRIFT/_cross/message-domain-wide.md:270` | — |
| `DRIFT-MSG-V-009` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:34;DRIFT/_cross/message-domain-wide.md:156;DRIFT/_cross/message-domain-wide.md:271` | — |
| `DRIFT-MSG-V-010` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:35;DRIFT/_cross/message-domain-wide.md:157;DRIFT/_cross/message-domain-wide.md:272` | — |
| `DRIFT-MSG-V-011` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:49;DRIFT/_cross/message-domain-wide.md:282;DRIFT/_cross/message-domain-wide.md:491` | — |
| `DRIFT-MSG-V-012` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:50;DRIFT/_cross/message-domain-wide.md:283;DRIFT/_cross/message-domain-wide.md:492` | — |
| `DRIFT-MSG-V-013` | DRIF | _cross | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/_cross/message-domain-wide.md:51;DRIFT/_cross/message-domain-wide.md:284;DRIFT/_cross/message-domain-wide.md:493` | — |
| `UNRESOLVED-MSG-V-002` | DRIF | _cross | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/_cross/message-domain-wide.md:450` | — |
| `A-2` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | BLK-03 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:2551;DRIFT/article/vs-common.md:361;DRIFT/favorite/rpc.md:1662` | — |
| `DRIFT-002` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:895` | — |
| `DRIFT-004` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:897;DRIFT/users/_shared.md:143` | — |
| `DRIFT-006` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:899;DRIFT/common/http.md:2;DRIFT/follow/rpc.md:265` | — |
| `DRIFT-007` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:900` | — |
| `DRIFT-ARTICLE-API-002` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:601` | — |
| `DRIFT-ARTICLE-API-003` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:626` | — |
| `DRIFT-ARTICLE-API-004` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:646` | — |
| `DRIFT-ARTICLE-API-005` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:666;DRIFT/article/api.md:1815` | — |
| `DRIFT-ARTICLE-API-007` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:723` | — |
| `DRIFT-ARTICLE-API-008` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:738` | — |
| `DRIFT-ARTICLE-API-010` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:782` | — |
| `DRIFT-ARTICLE-API-011` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:813` | — |
| `DRIFT-ARTICLE-API-012` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:850` | — |
| `DRIFT-ARTICLE-RPC-001` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:363` | — |
| `DRIFT-ARTICLE-RPC-002` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:392` | — |
| `DRIFT-ARTICLE-RPC-003` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:415` | — |
| `DRIFT-ARTICLE-RPC-004` | DRIF | article | ⚫ 已签字偏离 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:440;DRIFT/article/rpc.md:903` | — |
| `DRIFT-ARTICLE-RPC-005` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:464` | — |
| `DRIFT-ARTICLE-RPC-006` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:496` | — |
| `DRIFT-ARTICLE-RPC-008` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:556` | — |
| `DRIFT-ARTICLE-RPC-009` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:575` | — |
| `DRIFT-ARTICLE-RPC-010` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:637` | — |
| `DRIFT-HOTAPI-030` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | P1-T01,P5-T03,P5-T04 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:26;DRIFT/comment/vs-common.md:31;DRIFT/follow/vs-common.md:28` | — |
| `DRIFT-MSG-V-030` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:2590;DRIFT/comment/api.md:366;DRIFT/follow/vs-common.md:1201` | — |
| `F-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1288;DRIFT/article/api.md:1299;DRIFT/favorite/vs-common.md:223` | — |
| `F-1b` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1289` | — |
| `F-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1290;DRIFT/article/api.md:1367;DRIFT/favorite/vs-common.md:224` | — |
| `F-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1292;DRIFT/article/api.md:1434;DRIFT/favorite/vs-common.md:226` | — |
| `FIX-01` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:766` | — |
| `FIX-02` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:789` | — |
| `FIX-03` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:842` | — |
| `FIX-04` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:1000` | — |
| `FIX-05` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1079;DRIFT/article/rpc.md:1139` | — |
| `FIX-07` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1239;DRIFT/article/rpc.md:1314` | — |
| `FIX-09` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1726;DRIFT/article/api.md:1785` | — |
| `FIX-12` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1971` | — |
| `FIX-14` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:2141;DRIFT/article/api.md:2273;DRIFT/article/rpc.md:1546` | — |
| `FIX-15` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1709;DRIFT/article/rpc.md:1830` | — |
| `FIX-16` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:1736` | — |
| `FIX-18` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:2321;DRIFT/article/api.md:2556` | — |
| `G4-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:277;DRIFT/comment/vs-common.md:89;DRIFT/favorite/vs-common.md:111` | — |
| `L-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:70;DRIFT/favorite/vs-common.md:84` | — |
| `L-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:72` | — |
| `L-5` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:74;DRIFT/favorite/vs-common.md:88` | — |
| `MUT-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2462;DRIFT/hot/rpc.md:1706` | — |
| `MUT-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2463;DRIFT/hot/rpc.md:1707` | — |
| `MUT-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2464` | — |
| `MUT-4` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2465` | — |
| `O-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:338` | — |
| `O-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:339` | — |
| `O-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:340` | — |
| `O-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:341` | — |
| `O-6` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:343` | — |
| `OP-07` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:327;DRIFT/message/rpc.md:503` | — |
| `S-1` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:330` | — |
| `S-2` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:331` | — |
| `S-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:333` | — |
| `S-5b` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:335` | — |
| `S-6` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:336;DRIFT/article/vs-common.md:28;DRIFT/global/vs-common.md:235` | — |
| `S-7` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:337` | — |
| `U-9a` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:2512` | — |
| `UNRESOLVED-001` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:984;DRIFT/article/api.md:1620;DRIFT/message/api.md:1035` | — |
| `UNRESOLVED-002` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1001;DRIFT/message/api.md:1055;DRIFT/message/api.md:1344` | — |
| `UNRESOLVED-003` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1031;DRIFT/article/api.md:1564;DRIFT/message/api.md:1067` | — |
| `UNRESOLVED-004` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1050;DRIFT/article/api.md:1536;DRIFT/message/api.md:1078` | — |
| `UNRESOLVED-005` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1068;DRIFT/message/api.md:1097;DRIFT/users/user/api.md:2104` | — |
| `UNRESOLVED-006` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1081;DRIFT/article/api.md:1550` | — |
| `UNRESOLVED-007` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1098` | — |
| `UNRESOLVED-008` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1127` | — |
| `UNRESOLVED-009` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1162;DRIFT/article/api.md:1691` | — |
| `UNRESOLVED-010` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1177` | — |
| `C-2` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | P2-T01,P3-T04 ｜ BLK-02 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:58;DRIFT/comment/vs-common.md:219` | — |
| `C-7` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:63` | — |
| `C-8` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:64` | — |
| `C-9` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:65` | — |
| `G4-1` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:77;DRIFT/favorite/vs-common.md:108;DRIFT/hot/vs-common.md:88` | — |
| `MISMATCH-01` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:80` | — |
| `MISMATCH-02` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:93` | — |
| `MISMATCH-03` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:106` | — |
| `MISMATCH-04` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:118` | — |
| `MISMATCH-05` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:130` | — |
| `MISMATCH-06` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:141` | — |
| `MISMATCH-07` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:154` | — |
| `MISMATCH-API-02` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/api.md:53;DRIFT/comment/api.md:107` | — |
| `MISMATCH-API-03` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/api.md:54;DRIFT/comment/api.md:120` | — |
| `SD-2026-10-24-CAND-08` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:30;Intentional_Deviations/comment/api.md:172` | — |
| `DRIFT-COMMON-001` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:71;DRIFT/common/alignment-2026-10-23.md:1043` | — |
| `DRIFT-COMMON-002` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:104;DRIFT/common/alignment-2026-10-23.md:1044` | — |
| `DRIFT-COMMON-003` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:117;DRIFT/common/alignment-2026-10-23.md:1045` | — |
| `DRIFT-COMMON-004` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:134;DRIFT/common/alignment-2026-10-23.md:1046` | — |
| `DRIFT-COMMON-005` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:169;DRIFT/common/alignment-2026-10-23.md:589;DRIFT/common/alignment-2026-10-23.md:1047` | — |
| `DRIFT-COMMON-006` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:200;DRIFT/common/alignment-2026-10-23.md:1048` | — |
| `T-1` | DRIF | common | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:688;DRIFT/common/alignment-2026-10-23.md:709;DRIFT/global/_README.md:87` | — |
| `T-4` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:691;DRIFT/common/alignment-2026-10-23.md:712;DRIFT/global/_README.md:90` | — |
| `T-5` | DRIF | common | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:692;DRIFT/common/alignment-2026-10-23.md:713;DRIFT/global/_README.md:91` | — |
| `T-8` | DRIF | common | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:695;DRIFT/common/alignment-2026-10-23.md:716;DRIFT/global/_README.md:94` | — |
| `T-9` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:696;DRIFT/common/alignment-2026-10-23.md:717;DRIFT/global/_README.md:95` | — |
| `B-1` | DRIF | favorite | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/rpc.md:1497;DRIFT/favorite/rpc.md:1544;DRIFT/users/user/rpc.md:2133` | — |
| `E21` | DRIF | favorite | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/vs-common.md:27;DRIFT/global/_README.md:369;DRIFT/global/vs-common.md:27` | — |
| `L-2` | DRIF | favorite | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/vs-common.md:85` | — |
| `L-9` | DRIF | favorite | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/vs-common.md:95` | — |
| `DRIFT-FOLLOW-001` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:32;DRIFT/follow/api.md:51;DRIFT/follow/api.md:1178` | — |
| `DRIFT-FOLLOW-002` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:17;DRIFT/follow/rpc.md:25;DRIFT/follow/rpc.md:693` | — |
| `DRIFT-FOLLOW-003` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:18;DRIFT/follow/rpc.md:79;DRIFT/follow/rpc.md:694` | — |
| `DRIFT-FOLLOW-004` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:19;DRIFT/follow/rpc.md:143;DRIFT/follow/rpc.md:696` | — |
| `DRIFT-FOLLOW-005` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:33;DRIFT/follow/api.md:157;DRIFT/follow/api.md:1180` | — |
| `DRIFT-FOLLOW-006` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:20;DRIFT/follow/rpc.md:213;DRIFT/follow/rpc.md:351` | — |
| `DRIFT-FOLLOW-008` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | BLK-03 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:35;DRIFT/follow/api.md:528;DRIFT/follow/api.md:1183` | — |
| `DRIFT-FOLLOW-009` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:36;DRIFT/follow/api.md:239;DRIFT/follow/api.md:1185` | — |
| `DRIFT-FOLLOW-010` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:37;DRIFT/follow/api.md:287;DRIFT/follow/api.md:875` | — |
| `DRIFT-FOLLOW-011` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:38;DRIFT/follow/api.md:338;DRIFT/follow/api.md:1187` | — |
| `DRIFT-FOLLOW-012` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:39;DRIFT/follow/api.md:410;DRIFT/follow/api.md:1003` | — |
| `DRIFT-FOLLOW-013` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:1074;DRIFT/follow/rpc.md:659;DRIFT/follow/vs-common.md:726` | — |
| `DRIFT-G1-001` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/vs-common.md:174;DRIFT/follow/vs-common.md:1213;DRIFT/follow/vs-common.md:1270` | — |
| `DRIFT-G1-002` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/vs-common.md:200;DRIFT/follow/vs-common.md:1214;DRIFT/follow/vs-common.md:1271` | — |
| `OPEN-001` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:199;DRIFT/follow/vs-common.md:1202` | — |
| `S1-FOLLOW-005` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 是 | 台账已写 · ① 阶段 1 已修（`${FOLLOW_RPC_PORT:8082}`）；② 阶段 2 把 6 个组件端口**统一**为 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/stage1-admission.md:162;DRIFT/follow/stage1-admission.md:538;DRIFT/follow/vs-common.md:1211` | — |
| `S1-FOLLOW-006` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 是 | 台账已写 · ✅ **无**；⚠️ **反向使用**：本条是 users 域 `S1-USERS-004`（头像链路事务依赖跨 Bea | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/stage1-admission.md:179` | — |
| `CD-1` | DRIF | global | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | P5-T03 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:41;DRIFT/global/cross-domain.md:629` | — |
| `CD-10` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | P5-T03 | 无可复算指针 · 结论不可复算 | `DRIFT/global/cross-domain.md:50;DRIFT/global/cross-domain.md:187` | — |
| `CD-2` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:42;DRIFT/global/cross-domain.md:336` | — |
| `CD-3` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/cross-domain.md:43;DRIFT/global/cross-domain.md:419` | — |
| `CD-4` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:44;DRIFT/global/cross-domain.md:386` | — |
| `CD-5` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:45;DRIFT/global/cross-domain.md:203;DRIFT/global/cross-domain.md:916` | — |
| `CD-6` | DRIF | global | ✅ 已一致 | A 活体 · 环境(2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:137;DRIFT/global/cross-domain.md:46;DRIFT/global/cross-domain.md:435` | — |
| `CD-8` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/cross-domain.md:48` | — |
| `CD-9` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:49;DRIFT/global/cross-domain.md:89;DRIFT/global/cross-domain.md:720` | — |
| `CYCLE-002` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cycle-deps.md:96` | — |
| `E11` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:350` | — |
| `E12` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:351` | — |
| `E13` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:352` | — |
| `E14` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:353` | — |
| `E15` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:354` | — |
| `E16` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:355` | — |
| `E17` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:356` | — |
| `E19` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:357` | — |
| `E27` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:375` | — |
| `E28` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:376` | — |
| `E29` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:377` | — |
| `E33` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:382` | — |
| `E35` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:384` | — |
| `E37` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:387` | — |
| `E38` | DRIF | global | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:388;DRIFT/users/user/rpc.md:832` | — |
| `E39` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:386` | — |
| `E42` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:420` | — |
| `E45` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:422` | — |
| `E49` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:436` | — |
| `E54` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:442` | — |
| `E57` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:445` | — |
| `FB-04` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:910;DRIFT/like/vs-common.md:179;DRIFT/like/vs-common.md:196` | — |
| `FB-05` | DRIF | global | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | P2-T02,P2-T06 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:1014;DRIFT/like/vs-common.md:109` | — |
| `T-11` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/common-tails.md:498;DRIFT/global/common-tails.md:641;DRIFT/global/lanes/L1-delivery.md:214` | — |
| `T-14` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/common-tails.md:565` | — |
| `T-15` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/common-tails.md:576;DRIFT/global/common-tails.md:587` | — |
| `DRIFT-HOTAPI-011` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:535;DRIFT/hot/api.md:912;DRIFT/hot/api.md:920` | — |
| `DRIFT-HOTAPI-012` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:575` | — |
| `DRIFT-HOTAPI-013` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:611;DRIFT/hot/api.md:914;DRIFT/hot/api.md:977` | — |
| `DRIFT-HOTAPI-014` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:641;DRIFT/hot/api.md:915;DRIFT/hot/api.md:1003` | — |
| `DRIFT-HOTAPI-015` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:675;DRIFT/hot/api.md:916;DRIFT/hot/api.md:952` | — |
| `DRIFT-HOTAPI-016` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1073` | — |
| `DRIFT-HOTAPI-017` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1136` | — |
| `DRIFT-HOTAPI-018` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1167` | — |
| `DRIFT-HOTAPI-019` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1212` | — |
| `DRIFT-HOTAPI-020` | DRIF | hot | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1231` | — |
| `DRIFT-HOTAPI-021` | DRIF | hot | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1342` | — |
| `DRIFT-HOTAPI-022` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1391` | — |
| `DRIFT-HOTAPI-023` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1437` | — |
| `DRIFT-HOTAPI-024` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1481;DRIFT/hot/vs-common.md:27` | — |
| `DRIFT-HOTAPI-028` | DRIF | hot | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1930` | — |
| `DRIFT-HOTAPI-029` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:2092` | — |
| `DRIFT-HOTRPC-001` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:582;DRIFT/hot/rpc.md:986;DRIFT/hot/rpc.md:996` | — |
| `DRIFT-HOTRPC-002` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:621;DRIFT/hot/rpc.md:987` | — |
| `DRIFT-HOTRPC-003` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:663;DRIFT/hot/rpc.md:988;DRIFT/hot/rpc.md:1030` | — |
| `DRIFT-HOTRPC-004` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:690;DRIFT/hot/rpc.md:1049` | — |
| `DRIFT-HOTRPC-006` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:744;DRIFT/hot/rpc.md:1089` | — |
| `DRIFT-HOTRPC-007` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:772;DRIFT/hot/rpc.md:992` | — |
| `DRIFT-HOTRPC-008` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:1176` | — |
| `DRIFT-HOTRPC-009` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:1198` | — |
| `DRIFT-HOTRPC-010` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:1222` | — |
| `G-5` | DRIF | hot | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/common.md:16` | — |
| `G4-18` | DRIF | hot | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:91` | — |
| `H-2` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:68;DRIFT/users/admin/rpc.md:963` | — |
| `H-3` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:69;DRIFT/users/admin/rpc.md:964` | — |
| `H-5` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:71` | — |
| `H-6` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:73` | — |
| `H-7` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:74` | — |
| `H-8` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:75` | — |
| `H-9` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:76` | — |
| `M-redis-1` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:1168` | — |
| `M-redis-2` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:1169` | — |
| `U5-OPEN-1` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1780;DRIFT/hot/api.md:2104;DRIFT/hot/api.md:2125` | — |
| `UNR-1` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:850;DRIFT/hot/rpc.md:1413` | — |
| `UNR-2` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:851` | — |
| `UNR-3` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:852` | — |
| `UNR-4` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:853` | — |
| `UNR-5` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:854` | — |
| `UNR-6` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:855` | — |
| `UNR-7` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:856` | — |
| `D-4` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:70;DRIFT/message/vs-common.md:72` | — |
| `D-5` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:71` | — |
| `D-6` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:73;DRIFT/task/vs-common.md:66` | — |
| `D-7` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:72;DRIFT/task/vs-common.md:68` | — |
| `DRIFT-LIKE-001` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:82` | — |
| `DRIFT-LIKE-002` | DRIF | like | ⚫ 已签字偏离 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:234` | — |
| `DRIFT-LIKE-004` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:235;DRIFT/like/rpc.md:1169` | — |
| `DRIFT-LIKE-005` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:236;DRIFT/like/rpc.md:1205` | — |
| `DRIFT-LIKE-006` | DRIF | like | ✅ 已一致 | A 活体 · 环境(2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:237;DRIFT/like/vs-common.md:37` | — |
| `DRIFT-LIKE-007` | DRIF | like | ⚫ 已签字偏离 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:238` | — |
| `DRIFT-LIKE-008` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:239;DRIFT/like/rpc.md:1239` | — |
| `DRIFT-LIKE-009` | DRIF | like | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/like/rpc.md:240;DRIFT/like/rpc.md:1251` | — |
| `DRIFT-LIKE-010` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:1264` | — |
| `DRIFT-LIKE-011` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:1271` | — |
| `DRIFT-LIKE-012` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:1279` | — |
| `DRIFT-LIKE-013` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:802` | — |
| `DRIFT-LIKE-014` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:823` | — |
| `DRIFT-LIKE-015` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:841` | — |
| `DRIFT-LIKE-016` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:861` | — |
| `DRIFT-LIKE-017` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:879;DRIFT/like/rpc.md:1058` | — |
| `DRIFT-LIKEAPI-001` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:382` | — |
| `DRIFT-LIKEAPI-002` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:383` | — |
| `DRIFT-LIKEAPI-010` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:387` | — |
| `DRIFT-LIKEAPI-011` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:391` | — |
| `DRIFT-LIKEAPI-012` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:403;DRIFT/like/api.md:1013` | — |
| `DRIFT-LIKEAPI-013` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:424;DRIFT/like/api.md:954` | — |
| `DRIFT-LIKEAPI-014` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:444;DRIFT/like/api.md:978` | — |
| `DRIFT-LIKEAPI-015` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:464;DRIFT/like/api.md:1012` | — |
| `DRIFT-LIKEAPI-016` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:484;DRIFT/like/api.md:1014` | — |
| `DRIFT-LIKEAPI-017` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:504;DRIFT/like/api.md:1015;DRIFT/like/vs-common.md:36` | — |
| `DRIFT-LIKEAPI-018` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:524` | — |
| `DRIFT-LIKEAPI-019` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:544` | — |
| `DRIFT-LIKEAPI-020` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:563` | — |
| `DRIFT-LIKEAPI-023` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:630` | — |
| `G-14` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:800` | — |
| `U-02` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:825;DRIFT/like/api.md:838` | — |
| `U-04` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:827;DRIFT/like/api.md:840` | — |
| `U-07` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:830;DRIFT/like/api.md:843` | — |
| `D-3` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/vs-common.md:71` | — |
| `DRIFT-MSG-001` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:35;DRIFT/message/rpc.md:132` | — |
| `DRIFT-MSG-002` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:36;DRIFT/message/rpc.md:133;DRIFT/message/rpc.md:191` | — |
| `DRIFT-MSG-003` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:37;DRIFT/message/rpc.md:134;DRIFT/message/rpc.md:218` | — |
| `DRIFT-MSG-004` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:143;DRIFT/message/rpc.md:254` | — |
| `DRIFT-MSG-005` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:144;DRIFT/message/rpc.md:255` | — |
| `DRIFT-MSG-006` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:256` | — |
| `DRIFT-MSG-007` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:257` | — |
| `DRIFT-MSG-008` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:258` | — |
| `DRIFT-MSG-009` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:259` | — |
| `DRIFT-MSG-010` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:260` | — |
| `DRIFT-MSG-011` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:261` | — |
| `DRIFT-MSG-V-014` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:642` | — |
| `DRIFT-MSG-V-015` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:689` | — |
| `DRIFT-MSG-V-016` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:721;DRIFT/message/rpc.md:1745;DRIFT/message/rpc.md:1762` | — |
| `DRIFT-MSG-V-017` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1059` | — |
| `DRIFT-MSG-V-018` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1103;DRIFT/message/rpc.md:2316` | — |
| `DRIFT-MSG-V-019` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1138` | — |
| `DRIFT-MSG-V-021` | DRIF | message | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1509` | — |
| `DRIFT-MSG-V-022` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/rpc.md:1533` | — |
| `DRIFT-MSG-V-024` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:805;DRIFT/message/rpc.md:2054;DRIFT/message/rpc.md:2187` | — |
| `DRIFT-MSG-V-025` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:854;DRIFT/message/rpc.md:2055;DRIFT/message/rpc.md:2360` | — |
| `DRIFT-MSG-V-026` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1008;DRIFT/message/rpc.md:2056;DRIFT/message/rpc.md:2407` | — |
| `DRIFT-MSG-V-028` | DRIF | message | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:1713;DRIFT/message/rpc.md:2163` | — |
| `DRIFT-MSG-V-029` | DRIF | message | ✅ 已一致 | A 活体 · 环境(2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:882;DRIFT/message/vs-common.md:41` | — |
| `DRIFT-MSG-V-031` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2663` | — |
| `DRIFT-MSGAPI-001` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:48;DRIFT/message/api.md:692` | — |
| `DRIFT-MSGAPI-002` | DRIF | message | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:49` | — |
| `DRIFT-MSGAPI-004` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:240` | — |
| `DRIFT-MSGAPI-005` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:712` | — |
| `DRIFT-MSGAPI-006` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:757` | — |
| `DRIFT-MSGAPI-007` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:790` | — |
| `DRIFT-MSGAPI-008` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:818` | — |
| `DRIFT-MSGAPI-010` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:912;DRIFT/message/api.md:1664;DRIFT/message/vs-common.md:38` | — |
| `DRIFT-MSGAPI-013` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:1543;DRIFT/message/vs-common.md:40` | — |
| `DRIFT-MSGAPI-014` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:1590` | — |
| `FB-G2-01` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/feedback-to-g4.md:22` | — |
| `FB-G2-02` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/feedback-to-g4.md:38` | — |
| `FIX-MSG-014` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1320` | — |
| `FIX-MSG-017` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1359` | — |
| `FIX-MSG-COMMON-001` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/common.md:63` | — |
| `FIX-MSG-COMMON-002` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/common.md:118` | — |
| `FIX-MSG-COMMON-003` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/common.md:134` | — |
| `FIX-MSG-COMMON-004` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/common.md:144` | — |
| `FIX-MSG-V-015` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1431` | — |
| `FIX-MSG-V-019` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1452` | — |
| `FIX-MSG-V-023` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1556` | — |
| `OP-01` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:497` | — |
| `OP-02` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:498` | — |
| `OP-03` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:499` | — |
| `OP-04` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:500` | — |
| `OP-05` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:501` | — |
| `OP-06` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:502` | — |
| `OP-08` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:504` | — |
| `SD-2026-10-24-03` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2716;Intentional_Deviations/message/rpc.md:73` | — |
| `UNRESOLVED-MSG-003` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/rpc.md:2209` | — |
| `UNRESOLVED-MSG-004` | DRIF | message | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2507` | — |
| `UNRESOLVED-MSG-006` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1945` | — |
| `UNRESOLVED-MSG-009` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/rpc.md:2698` | — |
| `UNRESOLVED-MSG-V-003` | DRIF | message | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2584;DRIFT/message/rpc.md:2689` | — |
| `V-020` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1474` | — |
| `DRIFT-TASK-API-001` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:312;DRIFT/task/_README.md:352;DRIFT/task/api.md:64` | — |
| `DRIFT-TASK-API-002` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:313;DRIFT/task/_README.md:353;DRIFT/task/api.md:100` | — |
| `DRIFT-TASK-API-003` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:43;DRIFT/task/_README.md:314;DRIFT/task/_README.md:354` | — |
| `DRIFT-TASK-RPC-001` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:317;DRIFT/task/_README.md:357;DRIFT/task/rpc.md:98` | — |
| `DRIFT-TASK-RPC-002` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:318;DRIFT/task/_README.md:358;DRIFT/task/rpc.md:154` | — |
| `DRIFT-TASK-RPC-003` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:319;DRIFT/task/_README.md:359;DRIFT/task/rpc.md:204` | — |
| `DRIFT-TASK-RPC-004` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:320;DRIFT/task/_README.md:360;DRIFT/task/rpc.md:224` | — |
| `DRIFT-TASK-RPC-005` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:321;DRIFT/task/_README.md:361;DRIFT/task/rpc.md:322` | — |
| `DRIFT-TASK-RPC-006` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:322;DRIFT/task/_README.md:362;DRIFT/task/rpc.md:349` | — |
| `DRIFT-TASK-RPC-007` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:323;DRIFT/task/_README.md:363;DRIFT/task/rpc.md:408` | — |
| `DRIFT-TASK-RPC-008` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:324;DRIFT/task/_README.md:364;DRIFT/task/rpc.md:1545` | — |
| `FB-06` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | P2-T02,P2-T06 | 未定级 ⇒ 未排期 | `DRIFT/task/vs-common.md:148` | — |
| `SD-2026-10-23-TASK-01` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:325;DRIFT/task/_README.md:365;Intentional_Deviations/task/rpc.md:13` | — |
| `SD-2026-10-24-TASK-01` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:326;DRIFT/task/_README.md:366;Intentional_Deviations/task/rpc.md:115` | — |
| `ADJ-03` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/_shared.md:1005` | — |
| `ADJ-04` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:1023` | — |
| `ARPC-002` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:2114;DRIFT/users/admin/rpc.md:2148;DRIFT/users/admin/rpc.md:2455` | — |
| `D-007` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/api.md:1966` | — |
| `D-009` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/api.md:1968` | — |
| `D-010` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/api.md:1969` | — |
| `DRIFT-ADMINAPI-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:439;DRIFT/users/admin/api.md:1401` | — |
| `DRIFT-ADMINAPI-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:486` | — |
| `DRIFT-ADMINAPI-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:520;DRIFT/users/admin/api.md:1728` | — |
| `DRIFT-ADMINAPI-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:575;DRIFT/users/admin/api.md:1274;DRIFT/users/admin/api.md:1376` | — |
| `DRIFT-ADMINAPI-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:604` | — |
| `DRIFT-ADMINAPI-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:646;DRIFT/users/admin/api.md:2007` | — |
| `DRIFT-ADMINAPI-007` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:710;DRIFT/users/vs-common.md:50` | — |
| `DRIFT-ADMINAPI-008` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:806` | — |
| `DRIFT-ADMINAPI-009` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:854;DRIFT/users/admin/api.md:1012;DRIFT/users/admin/api.md:1639` | — |
| `DRIFT-ADMINAPI-010` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:874` | — |
| `DRIFT-ADMINAPI-011` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:1148` | — |
| `DRIFT-ARPC-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:336` | — |
| `DRIFT-ARPC-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:425;DRIFT/users/admin/rpc.md:1567` | — |
| `DRIFT-ARPC-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:473` | — |
| `DRIFT-ARPC-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:504;DRIFT/users/admin/rpc.md:1500` | — |
| `DRIFT-ARPC-005` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:566;DRIFT/users/admin/rpc.md:1608` | — |
| `DRIFT-ARPC-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:598;DRIFT/users/admin/rpc.md:2213;DRIFT/users/vs-common.md:35` | — |
| `DRIFT-ARPC-013` | DRIF | users | ✅ 已一致 | A 活体 · 混(0/1/2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:4274;DRIFT/users/vs-common.md:3197` | — |
| `DRIFT-FRAMEWORK-001` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/_shared.md:76` | — |
| `DRIFT-NEW-014` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:295` | — |
| `DRIFT-NEW-015` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:297;DRIFT/users/_shared.md:441` | — |
| `DRIFT-NEW-016` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:299;DRIFT/users/_shared.md:360` | — |
| `DRIFT-USERAPI-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:951;DRIFT/users/user/api.md:612;DRIFT/users/user/api.md:632` | — |
| `DRIFT-USERAPI-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:952;DRIFT/users/user/api.md:613;DRIFT/users/user/api.md:700` | — |
| `DRIFT-USERAPI-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:614;DRIFT/users/user/api.md:742;DRIFT/users/user/api.md:2586` | — |
| `DRIFT-USERAPI-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:787` | — |
| `DRIFT-USERAPI-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:616;DRIFT/users/user/api.md:906;DRIFT/users/user/api.md:2689` | — |
| `DRIFT-USERAPI-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:617;DRIFT/users/user/api.md:939;DRIFT/users/user/api.md:2948` | — |
| `DRIFT-USERAPI-007` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:618;DRIFT/users/user/api.md:997;DRIFT/users/user/api.md:2784` | — |
| `DRIFT-USERAPI-008` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:1040;DRIFT/users/user/api.md:3154` | — |
| `DRIFT-USERAPI-009` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:620;DRIFT/users/user/api.md:1132;DRIFT/users/user/api.md:1235` | — |
| `DRIFT-USERAPI-010` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:621;DRIFT/users/user/api.md:1533` | — |
| `DRIFT-USERAPI-011` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:622;DRIFT/users/user/api.md:2986` | — |
| `DRIFT-USERAPI-012` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:623` | — |
| `DRIFT-USERAPI-014` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:625;DRIFT/users/user/api.md:1574;DRIFT/users/user/api.md:3466` | — |
| `DRIFT-USERAPI-015` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:626;DRIFT/users/user/api.md:1638;DRIFT/users/user/api.md:2193` | — |
| `DRIFT-USERAPI-016` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:1791` | — |
| `DRIFT-USERRPC-V-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:575;DRIFT/users/user/rpc.md:1707` | — |
| `DRIFT-USERRPC-V-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:665;DRIFT/users/user/rpc.md:1480` | — |
| `DRIFT-USERRPC-V-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:915;DRIFT/users/_shared.md:953;DRIFT/users/user/rpc.md:720` | — |
| `DRIFT-USERRPC-V-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:744;DRIFT/users/user/rpc.md:1709;DRIFT/users/user/rpc.md:1789` | — |
| `DRIFT-USERRPC-V-007` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:775;DRIFT/users/user/rpc.md:1710;DRIFT/users/vs-common.md:37` | — |
| `DRIFT-USERRPC-V-008` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:889;DRIFT/users/user/rpc.md:1711;DRIFT/users/user/rpc.md:1834` | — |
| `DRIFT-USERRPC-V-009` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1515` | — |
| `DRIFT-USERRPC-V-010` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1541` | — |
| `DRIFT-USERRPC-V-012` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2018;DRIFT/users/user/rpc.md:2085;DRIFT/users/vs-common.md:792` | — |
| `DRIFT-USERRPC-V-013` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2222` | — |
| `DRIFT-USERRPC-V-014` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2365;DRIFT/users/vs-common.md:3195;DRIFT/users/vs-common.md:3196` | — |
| `DRIFT-USERRPC-V-015` | DRIF | users | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2658;DRIFT/users/vs-common.md:4123` | — |
| `DRIFT-USERSCOMMON-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:77;DRIFT/users/common.md:864` | — |
| `DRIFT-USERSCOMMON-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:179;DRIFT/users/common.md:449;DRIFT/users/common.md:865` | — |
| `DRIFT-USERSCOMMON-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:210;DRIFT/users/common.md:866` | — |
| `DRIFT-USERSCOMMON-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:234;DRIFT/users/common.md:434;DRIFT/users/common.md:867` | — |
| `DRIFT-USERSCOMMON-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:298;DRIFT/users/common.md:868;DRIFT/users/user/api.md:2951` | — |
| `E-1` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:590` | — |
| `E-2` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:591` | — |
| `E-3` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:159;DRIFT/users/_shared.md:592` | — |
| `E-4` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:593;DRIFT/users/_shared.md:597` | — |
| `E-5` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:594` | — |
| `E-6` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:595` | — |
| `ENC-01` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:389` | — |
| `FIX-ARPC-001` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:1079` | — |
| `G-4` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:688` | — |
| `OP-10` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:188` | — |
| `PAD-01` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:352;DRIFT/users/common.md:460` | — |
| `U-7` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/_shared.md:221` | — |
| `U-8` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/admin/_shared.md:222` | — |
| `UNRESOLVED-ADMINAPI-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:945;DRIFT/users/admin/api.md:1237` | — |
| `UNRESOLVED-ADMINAPI-002` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:959;DRIFT/users/admin/api.md:1489` | — |
| `UNRESOLVED-ADMINAPI-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:981;DRIFT/users/admin/api.md:1465` | — |
| `UNRESOLVED-ADMINAPI-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:992;DRIFT/users/admin/api.md:1518` | — |
| `UNRESOLVED-ADMINAPI-006` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:1017;DRIFT/users/admin/api.md:1610` | — |
| `UNRESOLVED-ARPC-001` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:671;DRIFT/users/admin/rpc.md:1909` | — |
| `UNRESOLVED-ARPC-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:711;DRIFT/users/admin/rpc.md:1953;DRIFT/users/admin/rpc.md:2674` | — |
| `UNRESOLVED-ARPC-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:794;DRIFT/users/admin/rpc.md:2063;DRIFT/users/admin/rpc.md:2174` | — |
| `UNRESOLVED-ARPC-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:835;DRIFT/users/admin/rpc.md:1379` | — |
| `UNRESOLVED-ARPC-005` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:852;DRIFT/users/admin/rpc.md:2320` | — |
| `UNRESOLVED-USERRPC-004` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1223` | — |
| `UNRESOLVED-USERRPC-005` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1138;DRIFT/users/user/rpc.md:1715;DRIFT/users/user/rpc.md:1907` | — |
| `UNRESOLVED-USERRPC-006` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:855;DRIFT/users/_shared.md:954;DRIFT/users/user/rpc.md:1651` | — |
| `UNRESOLVED-USERRPC-007` | DRIF | users | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1908` | — |
| `UNRESOLVED-USERSCOMMON-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/vs-common.md:46;DRIFT/users/vs-common.md:611` | — |
| `UNRESOLVED-USERSCOMMON-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:635;DRIFT/users/common.md:870` | — |
| `V-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1717` | — |
| `V-007` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/rpc.md:1000` | — |
| `V-012` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:3182;DRIFT/users/user/rpc.md:1003` | — |
| `PIT-LANE-01` | Pitf | _cross | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/_cross/domain-common-lanes.md:17` | — |
| `PIT-LANE-02` | Pitf | _cross | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:63` | — |
| `PIT-LANE-03` | Pitf | _cross | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/_cross/domain-common-lanes.md:97` | — |
| `PIT-LANE-04` | Pitf | _cross | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:134` | — |
| `P-1` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:11` | — |
| `P-10` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:386;Pitfalls/like/rpc.md:218` | — |
| `P-11` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:416;Pitfalls/like/rpc.md:257` | — |
| `P-12` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:449` | — |
| `P-13` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:483` | — |
| `P-14` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:540` | — |
| `P-15` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:586` | — |
| `P-16` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:622` | — |
| `P-17` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:673` | — |
| `P-19` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:796` | — |
| `P-20` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:833` | — |
| `P-21` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:889` | — |
| `P-3` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:112` | — |
| `P-6` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:226` | — |
| `P-7` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:257` | — |
| `P-8` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:301` | — |
| `P-9` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:342` | — |
| `PF-ARTICLE-RPC-005` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:203` | — |
| `PF-ARTICLE-RPC-006` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:241` | — |
| `PF-ARTICLE-RPC-007` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:274` | — |
| `PF-ARTICLE-RPC-008` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:325` | — |
| `PF-ARTICLE-RPC-009` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:385` | — |
| `PF-ARTICLE-RPC-012` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/rpc.md:620` | — |
| `PF-ARTICLE-RPC-013` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:700` | — |
| `PF-ARTICLE-RPC-014` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/rpc.md:801` | — |
| `PF-ARTICLE-RPC-015` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:882` | — |
| `PF-ARTICLE-RPC-016` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/rpc.md:2072;Pitfalls/article/rpc.md:950` | — |
| `PIT-COMMENT-API-01` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/api.md:11` | — |
| `PIT-COMMENT-API-02` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/api.md:71` | — |
| `PIT-COMMENT-API-03` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/api.md:111` | — |
| `PIT-COMMENT-RPC-01` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:14` | — |
| `PIT-COMMENT-RPC-02` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:76` | — |
| `PIT-COMMENT-RPC-04` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:179` | — |
| `PIT-COMMENT-RPC-05` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:216` | — |
| `PIT-COMMENT-RPC-06` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:285` | — |
| `PIT-COMMENT-RPC-09` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:426` | — |
| `PIT-COMMENT-RPC-10` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:463` | — |
| `PIT-COMMENT-RPC-13` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:635` | — |
| `PIT-COMMENT-RPC-14` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:694` | — |
| `PIT-COMMENT-RPC-15` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:760` | — |
| `PIT-COMMENT-RPC-16` | Pitf | comment | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/comment/rpc.md:802` | — |
| `PIT-COMMON-01` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/audit.md:23` | — |
| `PIT-COMMON-03` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/audit.md:99` | — |
| `PIT-COMMON-04` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/deadline.md:11` | — |
| `PIT-COMMON-05` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/deadline.md:77` | — |
| `PIT-COMMON-06` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/deadline.md:99` | — |
| `PIT-COMMON-08` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/http.md:2` | — |
| `PIT-COMMON-09` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/observability.md:11` | — |
| `PIT-COMMON-10` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/observability.md:68` | — |
| `PIT-COMMON-11` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/observability.md:114` | — |
| `PIT-COMMON-12` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/observability.md:153` | — |
| `PIT-COMMON-13` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/protection.md:10` | — |
| `PIT-FAV-API-001` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:11` | — |
| `PIT-FAV-API-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:57` | — |
| `PIT-FAV-API-003` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:95` | — |
| `PIT-FAV-API-004` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:129` | — |
| `PIT-FAV-API-005` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:162` | — |
| `PIT-FAV-API-006` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:188` | — |
| `PIT-FAV-API-007` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:245` | — |
| `PIT-FAV-API-010` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:460` | — |
| `PIT-FAV-API-011` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:520` | — |
| `PIT-FAV-API-012` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:578` | — |
| `PIT-FAV-API-013` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:633` | — |
| `PIT-FAV-API-014` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:685` | — |
| `PIT-FAV-API-015` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:759` | — |
| `PIT-FAV-API-016` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:838` | — |
| `PIT-FAV-API-017` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:884` | — |
| `PIT-FAV-COMMON-001` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/common.md:11` | — |
| `PIT-FAV-COMMON-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/common.md:51` | — |
| `PIT-FAV-PROTO-001` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/proto.md:10` | — |
| `PIT-FAV-PROTO-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/proto.md:54` | — |
| `PIT-FAV-RPC-001` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:10` | — |
| `PIT-FAV-RPC-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:70` | — |
| `PIT-FAV-RPC-003` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:103` | — |
| `PIT-FAV-RPC-004` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:128` | — |
| `PIT-FAV-RPC-005` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:174` | — |
| `PIT-FAV-RPC-006` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:222` | — |
| `PIT-FAV-RPC-007` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:267` | — |
| `PIT-FAV-RPC-008` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:307` | — |
| `PIT-FAV-RPC-009` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:353` | — |
| `PIT-FAV-RPC-010` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:413` | — |
| `PIT-FAV-RPC-011` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:470` | — |
| `PIT-FAV-RPC-012` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:518` | — |
| `PIT-FAV-RPC-014` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:628` | — |
| `PIT-FAV-RPC-015` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:670` | — |
| `PIT-FAV-RPC-016` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:709` | — |
| `PIT-FAV-RPC-017` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:745` | — |
| `PIT-FAV-RPC-018` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:801` | — |
| `PIT-FAV-RPC-019` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:855` | — |
| `PIT-FAV-RPC-020` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:894` | — |
| `PIT-FAV-RPC-021` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:947` | — |
| `PIT-FAV-RPC-022` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:996` | — |
| `PIT-FAV-RPC-023` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1039` | — |
| `PIT-FAV-RPC-024` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1085` | — |
| `PIT-FAV-RPC-025` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1129` | — |
| `PIT-FAV-RPC-026` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1181` | — |
| `PIT-FAV-RPC-027` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1259` | — |
| `PIT-FAV-RPC-028` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1324` | — |
| `PIT-FAV-RPC-029` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1390` | — |
| `PIT-FAV-RPC-030` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1435` | — |
| `PIT-FAV-RPC-031` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1494` | — |
| `PIT-FAV-RPC-032` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1556` | — |
| `PIT-FAV-RPC-033` | DRIF | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:3095;Pitfalls/favorite/rpc.md:1594` | — |
| `PIT-FAV-RPC-035` | DRIF | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:3277;Pitfalls/favorite/rpc.md:1718` | — |
| `PIT-FAV-RPC-036` | DRIF | favorite | 🔴 未闭合 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/favorite/rpc.md:3365;DRIFT/favorite/rpc.md:3459;Pitfalls/favorite/rpc.md:1778` | — |
| `PIT-FAV-RPC-037` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1832` | — |
| `PIT-FAV-RPC-039` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1960` | — |
| `PIT-FAV-RPC-040` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1996` | — |
| `PIT-FOLLOW-011` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:261;Pitfalls/follow/api.md:481;Pitfalls/follow/api.md:509` | — |
| `PIT-FOLLOW-012` | Pitf | follow | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:307;Pitfalls/follow/api.md:482;Pitfalls/follow/api.md:510` | — |
| `PIT-FOLLOW-013` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:190;Pitfalls/follow/rpc.md:291;Pitfalls/follow/rpc.md:316` | — |
| `PIT-FOLLOW-014` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:237;Pitfalls/follow/rpc.md:292;Pitfalls/follow/rpc.md:317` | — |
| `PIT-FOLLOW-015` | Pitf | follow | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:358;Pitfalls/follow/api.md:483;Pitfalls/follow/api.md:511` | — |
| `PIT-FOLLOW-016` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:412;Pitfalls/follow/api.md:484;Pitfalls/follow/api.md:512` | — |
| `PIT-FAV-RPC-038` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:2709;Pitfalls/favorite/rpc.md:1908` | — |
| `PIT-GLOBAL-001` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:8` | — |
| `PIT-GLOBAL-002` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:70` | — |
| `PIT-GLOBAL-003` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cycle-deps.md:8` | — |
| `PIT-GLOBAL-004` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cycle-deps.md:43` | — |
| `PIT-GLOBAL-005` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:12` | — |
| `PIT-GLOBAL-006` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:87` | — |
| `PIT-GLOBAL-007` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/common-tails.md:110` | — |
| `PIT-GLOBAL-008` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:185` | — |
| `PIT-GLOBAL-009` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:242` | — |
| `PIT-GLOBAL-010` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cycle-deps.md:85` | — |
| `PIT-GLOBAL-011` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:99` | — |
| `PIT-GLOBAL-012` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:175` | — |
| `PIT-GLOBAL-013` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:238` | — |
| `PIT-GLOBAL-014` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:308` | — |
| `PIT-GLOBAL-015` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:376` | — |
| `PIT-GLOBAL-016` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:442` | — |
| `PIT-GLOBAL-017` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:500` | — |
| `PIT-GLOBAL-021` | DRIF | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L1-delivery.md:833;Pitfalls/global/cross-domain.md:825` | — |
| `PIT-GLOBAL-024` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/common-tails.md:362` | — |
| `PIT-GLOBAL-025` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:449` | — |
| `PIT-GLOBAL-026` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:895` | — |
| `PIT-GLOBAL-027` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:956` | — |
| `PIT-GLOBAL-028` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1014` | — |
| `PIT-GLOBAL-029` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1058` | — |
| `PIT-GLOBAL-030` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1105` | — |
| `PIT-GLOBAL-031` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1158` | — |
| `PIT-GLOBAL-032` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P1-T01,P1-T05 ｜ DEC-04 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1228` | — |
| `PIT-GLOBAL-033` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1273` | — |
| `PIT-GLOBAL-034` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1308` | — |
| `PIT-GLOBAL-035` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1335` | — |
| `PIT-GLOBAL-036` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1356` | — |
| `PIT-GLOBAL-037` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1388` | — |
| `PIT-GLOBAL-038` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | P1-T01,P1-T02,P1-T03,P1-T05 ｜ ENV-04 | — | `Pitfalls/global/cross-domain.md:1451` | — |
| `PIT-GLOBAL-039` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1510` | — |
| `PIT-GLOBAL-040` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P2-T01,P2-T03 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1577` | — |
| `PIT-GLOBAL-041` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1681` | — |
| `PIT-GLOBAL-042` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1711` | — |
| `PIT-GLOBAL-043` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1742` | — |
| `PIT-GLOBAL-044` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1772` | — |
| `PIT-GLOBAL-045` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1804` | — |
| `PIT-GLOBAL-046` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1836` | — |
| `PIT-GLOBAL-047` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1868` | — |
| `PIT-GLOBAL-048` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P2-T01,P2-T05 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1903` | — |
| `PIT-GLOBAL-049` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1940` | — |
| `PIT-GLOBAL-050` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1980` | — |
| `PIT-GLOBAL-052` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2055` | — |
| `PIT-GLOBAL-053` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2132` | — |
| `PIT-GLOBAL-054` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P2-T01 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2174` | — |
| `PIT-GLOBAL-055` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2315` | — |
| `PIT-GLOBAL-056` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2372` | — |
| `PIT-GLOBAL-057` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2415` | — |
| `PIT-GLOBAL-058` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2452` | — |
| `PIT-GLOBAL-059` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2482` | — |
| `PIT-GLOBAL-060` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2515` | — |
| `PIT-GLOBAL-061` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2552` | — |
| `PIT-GLOBAL-062` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2583` | — |
| `PIT-GLOBAL-063` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2616` | — |
| `PIT-GLOBAL-064` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2657` | — |
| `PIT-GLOBAL-065` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2682` | — |
| `PIT-GLOBAL-066` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2705` | — |
| `PIT-GLOBAL-067` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2728` | — |
| `PIT-GLOBAL-068` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | P4-T08 ｜ ENV-05 | — | `Pitfalls/global/cross-domain.md:2773` | — |
| `P-HOTAPI-24` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/api.md:993` | — |
| `P-HOTAPI-25` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/api.md:1037` | — |
| `P-HOTRPC-04` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:130` | — |
| `P-HOTRPC-05` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:153` | — |
| `P-HOTRPC-06` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:172` | — |
| `P-HOTRPC-07` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:216` | — |
| `P-HOTRPC-10` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:319` | — |
| `P-HOTRPC-11` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:351` | — |
| `P-HOTRPC-12` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:400` | — |
| `P-HOTRPC-13` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:447` | — |
| `P-HOTRPC-14` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:484` | — |
| `P-HOTRPC-15` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:522` | — |
| `P-HOTRPC-16` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:564` | — |
| `P-HOTRPC-17` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:608` | — |
| `P-HOTRPC-18` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:646` | — |
| `P-HOTRPC-19` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:677` | — |
| `P-HOTRPC-22` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:787` | — |
| `P-HOTRPC-23` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:813` | — |
| `P-HOTRPC-24` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:834` | — |
| `P-06` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/rpc.md:101` | — |
| `P-07` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:115` | — |
| `P-08` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:137` | — |
| `PIT-LIKE-RPC-012` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:297` | — |
| `PIT-LIKE-RPC-013` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/rpc.md:348` | — |
| `PIT-LIKE-RPC-014` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:389` | — |
| `PIT-LIKEAPI-001` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:12` | — |
| `PIT-LIKEAPI-006` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:196` | — |
| `PIT-LIKEAPI-009` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:300` | — |
| `PIT-LIKEAPI-010` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:328` | — |
| `PIT-LIKEAPI-011` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:355` | — |
| `PIT-LIKEAPI-012` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:399` | — |
| `PIT-LIKEAPI-013` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:431` | — |
| `PIT-LIKEAPI-014` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:465` | — |
| `PIT-LIKEAPI-015` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:515` | — |
| `PIT-LIKEAPI-016` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:561` | — |
| `PIT-MESSAGE-01` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:11` | — |
| `PIT-MESSAGE-05` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:193` | — |
| `PIT-MESSAGE-06` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:231` | — |
| `PIT-MESSAGE-07` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:263` | — |
| `PIT-MESSAGE-08` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:296` | — |
| `PIT-MESSAGE-09` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:10` | — |
| `PIT-MESSAGE-10` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:47` | — |
| `PIT-MESSAGE-12` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:130` | — |
| `PIT-MESSAGE-13` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:161` | — |
| `PIT-MESSAGE-16` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:257` | — |
| `PIT-MESSAGE-20` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:441` | — |
| `PIT-MESSAGE-21` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:513` | — |
| `PIT-MESSAGE-22` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:564` | — |
| `PIT-MESSAGE-23` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:608` | — |
| `PIT-MESSAGE-24` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:641` | — |
| `PIT-MESSAGE-29` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:794` | — |
| `PIT-MESSAGE-30` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:849` | — |
| `PIT-MESSAGE-31` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:885` | — |
| `PIT-MESSAGE-36` | Pitf | message | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:1107` | — |
| `PIT-TASK-04` | Pitf | task | 仍生效 | A 活体 · 红(1) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:193;Pitfalls/task/rpc.md:1564;Pitfalls/task/rpc.md:1620` | — |
| `PIT-TASK-05` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:243;Pitfalls/task/rpc.md:1565;Pitfalls/task/rpc.md:1621` | — |
| `PIT-TASK-08` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:365;Pitfalls/task/rpc.md:1568;Pitfalls/task/rpc.md:1624` | — |
| `PIT-TASK-09` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:400;Pitfalls/task/rpc.md:1569;Pitfalls/task/rpc.md:1625` | — |
| `PIT-TASK-10` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:452;Pitfalls/task/rpc.md:1570;Pitfalls/task/rpc.md:1626` | — |
| `PIT-TASK-11` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:496;Pitfalls/task/rpc.md:1571;Pitfalls/task/rpc.md:1627` | — |
| `PIT-TASK-12` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:543;Pitfalls/task/rpc.md:1572;Pitfalls/task/rpc.md:1628` | — |
| `PIT-TASK-13` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:586;Pitfalls/task/rpc.md:1573;Pitfalls/task/rpc.md:1629` | — |
| `PIT-TASK-15` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:673;Pitfalls/task/rpc.md:1575;Pitfalls/task/rpc.md:1631` | — |
| `PIT-TASK-17` | Pitf | task | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:806;Pitfalls/task/rpc.md:1577;Pitfalls/task/rpc.md:1633` | — |
| `PIT-TASK-19` | Pitf | task | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:933;Pitfalls/task/rpc.md:1579;Pitfalls/task/rpc.md:1635` | — |
| `PIT-TASK-21` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1048;Pitfalls/task/rpc.md:1581;Pitfalls/task/rpc.md:1637` | — |
| `PIT-TASK-26` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1246;Pitfalls/task/rpc.md:1586;Pitfalls/task/rpc.md:1642` | — |
| `PIT-TASK-27` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1293;Pitfalls/task/rpc.md:1587;Pitfalls/task/rpc.md:1643` | — |
| `PIT-TASK-28` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1318;Pitfalls/task/rpc.md:1588;Pitfalls/task/rpc.md:1644` | — |
| `PIT-TASK-29` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1348;Pitfalls/task/rpc.md:1589;Pitfalls/task/rpc.md:1645` | — |
| `PIT-TASK-34` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:763;Pitfalls/task/rpc.md:1594;Pitfalls/task/rpc.md:1650` | — |
| `P-18` | DRIF | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `DRIFT/users/admin/rpc.md:3976;DRIFT/users/admin/rpc.md:4246;Pitfalls/article/api.md:732` | — |
| `P-2` | DRIF | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `DRIFT/users/stage1-admission.md:767;Pitfalls/article/api.md:62` | — |
| `PIT-USERS-01` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:55` | — |
| `PIT-USERS-02` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:104` | — |
| `PIT-USERS-03` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:151` | — |
| `PIT-USERS-04` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:216` | — |
| `PIT-USERS-05` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:267` | — |
| `PIT-USERS-06` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:329` | — |
| `PIT-USERS-07` | Pitf | users | 仍生效 | A 活体 · 红(1) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:384` | — |
| `PIT-USERS-08` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:439` | — |
| `PIT-USERS-09` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:488` | — |
| `PIT-USERS-10` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:530` | — |
| `PIT-USERS-11` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:585` | — |
| `PIT-USERS-12` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:650` | — |
| `PIT-USERS-13` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:702` | — |
| `PIT-USERS-14` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:729` | — |
| `PIT-USERS-15` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:749` | — |
| `PIT-USERS-16` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:776` | — |
| `PIT-USERS-18` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:848` | — |
| `PIT-USERS-19` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:934` | — |
| `PIT-USERS-20` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:1101` | — |
| `PIT-USERS-21` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:1160` | — |
| `PIT-USERS-22` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:1254` | — |
| `PIT-USERS-23` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:1323` | — |
| `PIT-USERS-24` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:1375` | — |
| `PIT-USERS-25` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:26` | — |
| `PIT-USERS-27` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:173` | — |
| `PIT-USERS-28` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:227` | — |
| `PIT-USERS-29` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:287` | — |
| `PIT-USERS-30` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/api.md:343` | — |
| `PIT-USERS-31` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/api.md:386` | — |
| `PIT-USERS-32` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:22` | — |
| `PIT-USERS-33` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:60` | — |
| `PIT-USERS-35` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:133` | — |
| `PIT-USERS-36` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:159` | — |
| `PIT-USERS-37` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:193` | — |
| `PIT-USERS-38` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/rpc.md:232` | — |
| `PIT-USERSADM-01` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:23` | — |
| `PIT-USERSADM-02` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:62` | — |
| `PIT-USERSADM-03` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:117` | — |
| `PIT-USERSADM-04` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:145` | — |
| `PIT-USERSADM-05` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:179` | — |
| `PIT-USERSADM-06` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:208` | — |
| `PIT-USERSADM-07` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:239` | — |
| `PIT-USERSADM-08` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:265` | — |
| `PIT-USERSADM-09` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/api.md:294` | — |
| `PIT-USERSADM-10` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:343` | — |
| `PIT-USERSADM-11` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:377` | — |
| `PIT-USERSADM-12` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:416` | — |
| `PIT-USERSADM-13` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:444` | — |
| `PIT-USERSADM-14` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/api.md:480` | — |
| `PIT-USERSADM-16` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:570` | — |
| `PIT-USERSADM-17` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:621` | — |
| `PIT-USERSADM-18` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:22` | — |
| `PIT-USERSADM-21` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:158` | — |
| `PIT-USERSADM-22` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:229` | — |
| `PIT-USERSADM-23` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:272` | — |
| `PIT-USERSADM-25` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:333` | — |
| `PIT-USERSADM-27` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:444` | — |
| `PIT-USERSADM-28` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:518` | — |
| `PIT-USERSADM-29` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:585` | — |
| `PIT-USERSADM-30` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:646` | — |
| `PIT-USERSADM-31` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:712` | — |
| `PIT-USERSADM-32` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:781` | — |
| `PIT-USERSADM-33` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:857` | — |
| `PIT-USERSADM-34` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:937` | — |
| `PIT-USERSADM-35` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:1025` | — |
| `PIT-USERSADM-36` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:1143` | — |
| `PIT-USERSCOMMON-01` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:8` | — |
| `PIT-USERSCOMMON-02` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:43` | — |
| `PIT-USERSCOMMON-03` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:70` | — |
| `PIT-USERSCOMMON-04` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:88` | — |
| `P-22` | Pitf | article | 待核实 | B 静态 | P2 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:935` | — |
| `PIT-LANE-05` | Pitf | _cross | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:154` | — |
| `PIT-LANE-06` | Pitf | _cross | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:188` | — |
| `PIT-LANE-07` | Pitf | _cross | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:220` | — |
| `P-4` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:156` | — |
| `P-5` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:191` | — |
| `PF-ARTICLE-RPC-001` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:13` | — |
| `PF-ARTICLE-RPC-002` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:74` | — |
| `PF-ARTICLE-RPC-003` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:118` | — |
| `PF-ARTICLE-RPC-004` | Pitf | article | 已撤销 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:166` | — |
| `PF-ARTICLE-RPC-010` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:479` | — |
| `PF-ARTICLE-RPC-011` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:550` | — |
| `PIT-COMMENT-RPC-03` | Pitf | comment | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:136` | — |
| `PIT-COMMENT-RPC-07` | Pitf | comment | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:331` | — |
| `PIT-COMMENT-RPC-08` | Pitf | comment | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:375` | — |
| `PIT-COMMENT-RPC-11` | Pitf | comment | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:522` | — |
| `PIT-COMMENT-RPC-12` | Pitf | comment | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:589` | — |
| `PIT-COMMON-02` | Pitf | common | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/audit.md:72` | — |
| `PIT-COMMON-07` | Pitf | common | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/deadline.md:122` | — |
| `PIT-FAV-API-008` | Pitf | favorite | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:292` | — |
| `PIT-FAV-API-009` | Pitf | favorite | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:390` | — |
| `PIT-FAV-RPC-013` | Pitf | favorite | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:566` | — |
| `PIT-FAV-RPC-034` | Pitf | favorite | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1643` | — |
| `PIT-FOLLOW-001` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:19;Pitfalls/follow/rpc.md:286;Pitfalls/follow/rpc.md:311` | — |
| `PIT-FOLLOW-002` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:59;Pitfalls/follow/rpc.md:287;Pitfalls/follow/rpc.md:312` | — |
| `PIT-FOLLOW-003` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:100;Pitfalls/follow/rpc.md:288;Pitfalls/follow/rpc.md:313` | — |
| `PIT-FOLLOW-004` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:130;Pitfalls/follow/rpc.md:289;Pitfalls/follow/rpc.md:314` | — |
| `PIT-FOLLOW-005` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:160;Pitfalls/follow/rpc.md:290;Pitfalls/follow/rpc.md:315` | — |
| `PIT-FOLLOW-006` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:19;Pitfalls/follow/api.md:476;Pitfalls/follow/api.md:504` | — |
| `PIT-FOLLOW-007` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:71;Pitfalls/follow/api.md:477;Pitfalls/follow/api.md:505` | — |
| `PIT-FOLLOW-008` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:156;Pitfalls/follow/api.md:479;Pitfalls/follow/api.md:507` | — |
| `PIT-FOLLOW-009` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:107;Pitfalls/follow/api.md:478;Pitfalls/follow/api.md:506` | — |
| `PIT-FOLLOW-010` | Pitf | follow | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:178;Pitfalls/follow/api.md:480;Pitfalls/follow/api.md:508` | — |
| `P-HOTAPI-23` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/api.md:944` | — |
| `P-HOTRPC-01` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:15` | — |
| `P-HOTRPC-02` | Pitf | hot | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:64` | — |
| `P-HOTRPC-03` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:107` | — |
| `P-HOTRPC-08` | Pitf | hot | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:251` | — |
| `P-HOTRPC-09` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:282` | — |
| `P-HOTRPC-20` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:712` | — |
| `P-HOTRPC-21` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:748` | — |
| `P-HOTRPC-25` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:885` | — |
| `P-01` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:31` | — |
| `P-02` | Pitf | like | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/rpc.md:45` | — |
| `P-03` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:59` | — |
| `P-04` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:73` | — |
| `P-05` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:87` | — |
| `P-09` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:185` | — |
| `PIT-LIKEAPI-002` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:51` | — |
| `PIT-LIKEAPI-003` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:89` | — |
| `PIT-LIKEAPI-004` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:121` | — |
| `PIT-LIKEAPI-005` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:160` | — |
| `PIT-LIKEAPI-007` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:233` | — |
| `PIT-LIKEAPI-008` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:266` | — |
| `PIT-MESSAGE-02` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:67` | — |
| `PIT-MESSAGE-03` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:113` | — |
| `PIT-MESSAGE-04` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:151` | — |
| `PIT-MESSAGE-11` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:85` | — |
| `PIT-MESSAGE-14` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:193` | — |
| `PIT-MESSAGE-15` | Pitf | message | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/message/rpc.md:231` | — |
| `PIT-MESSAGE-17` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:293` | — |
| `PIT-MESSAGE-18` | Pitf | message | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:319` | — |
| `PIT-MESSAGE-19` | Pitf | message | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:347` | — |
| `PIT-MESSAGE-25` | Pitf | message | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:672` | — |
| `PIT-MESSAGE-26` | Pitf | message | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:700` | — |
| `PIT-MESSAGE-27` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:731` | — |
| `PIT-MESSAGE-28` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:770` | — |
| `PIT-MESSAGE-32` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:919` | — |
| `PIT-MESSAGE-33` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:958` | — |
| `PIT-MESSAGE-34` | Pitf | message | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:1008` | — |
| `PIT-MESSAGE-35` | Pitf | message | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:1064` | — |
| `PIT-TASK-01` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:22;Pitfalls/task/rpc.md:1561;Pitfalls/task/rpc.md:1617` | — |
| `PIT-TASK-02` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:109;Pitfalls/task/rpc.md:1562;Pitfalls/task/rpc.md:1618` | — |
| `PIT-TASK-03` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:161;Pitfalls/task/rpc.md:1563;Pitfalls/task/rpc.md:1619` | — |
| `PIT-TASK-06` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:272;Pitfalls/task/rpc.md:1566;Pitfalls/task/rpc.md:1622` | — |
| `PIT-TASK-07` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:330;Pitfalls/task/rpc.md:1567;Pitfalls/task/rpc.md:1623` | — |
| `PIT-TASK-14` | Pitf | task | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:628;Pitfalls/task/rpc.md:1574;Pitfalls/task/rpc.md:1630` | — |
| `PIT-TASK-16` | Pitf | task | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:711;Pitfalls/task/rpc.md:1576;Pitfalls/task/rpc.md:1632` | — |
| `PIT-TASK-18` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:859;Pitfalls/task/rpc.md:1578;Pitfalls/task/rpc.md:1634` | — |
| `PIT-TASK-20` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:987;Pitfalls/task/rpc.md:1580;Pitfalls/task/rpc.md:1636` | — |
| `PIT-TASK-22` | Pitf | task | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1099;Pitfalls/task/rpc.md:1582;Pitfalls/task/rpc.md:1638` | — |
| `PIT-TASK-23` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1137;Pitfalls/task/rpc.md:1583;Pitfalls/task/rpc.md:1639` | — |
| `PIT-TASK-24` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1178;Pitfalls/task/rpc.md:1584;Pitfalls/task/rpc.md:1640` | — |
| `PIT-TASK-25` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1220;Pitfalls/task/rpc.md:1585;Pitfalls/task/rpc.md:1641` | — |
| `PIT-TASK-30` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1376;Pitfalls/task/rpc.md:1590;Pitfalls/task/rpc.md:1646` | — |
| `PIT-TASK-31` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1411;Pitfalls/task/rpc.md:1591;Pitfalls/task/rpc.md:1647` | — |
| `PIT-TASK-32` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1458;Pitfalls/task/rpc.md:1592;Pitfalls/task/rpc.md:1648` | — |
| `PIT-TASK-33` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1501;Pitfalls/task/rpc.md:1593;Pitfalls/task/rpc.md:1649` | — |
| `PIT-USERS-17` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:800` | — |
| `PIT-USERS-26` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:110` | — |
| `PIT-USERS-34` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/rpc.md:105` | — |
| `PIT-USERSADM-15` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:531` | — |
| `PIT-USERSADM-19` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:70` | — |
| `PIT-USERSADM-20` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:119` | — |
| `PIT-USERSADM-24` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:307` | — |
| `PIT-USERSADM-26` | Pitf | users | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:386` | — |

---

## 七、已知盲区（**这一层的效力边界**）

1. **派生层不重新裁定任何结论** —— 台账说「已修」就是「已修」（在该证据强度下）。
   **「跑绿 ≠ 对」** 在这一层同样成立：U3 的 A 档只证明**那条探针的断言**成立，
   **不证明本条目结论的全量成立**。
2. **U5 拿不到定级 ⇒ 写 `—`** —— 不是「不重要」，是**还没定级**。
   本轮有 **420** 条属这一档 ⇒ 它们**不进施工单**，除非先定级。
3. **U8 的强链只认「指针落在条目段内」或「任务块里直接写了编号」** ——
   **既没写编号也没给指针**的语义关联 ⇒ **不可见**（这是本层的固有上限）。
4. **`CASE-*` 被排除** ⇒ 它们是**测试场景矩阵行**（证据层），不是差异条目；
   差异条目会引用 `CASE-*` 作为自己的证据。
5. **「阶段归属」与「优先级」不可互推** —— `twoStage.md` §10 明写「两者不能互相替代」。
   本层用 U4/U5 两条**独立**规则算，交叉不符时**两个都保留**，不强行对齐。
6. **「未排期」≠「不重要」** —— 它只说明**当前没有任何任务/未解决项指向它**。
7. **本层**不读**产品代码** ⇒ 它是**台账的投影**，不是代码的事实；
   「指针能解析」也不等于「结论为真」（同 AGENTS.md §3.3.3 第 ② 条）。
8. 🆕 **U8 认不出「表行编号」** —— 实测（U5-O 轮）：`global/vs-common.md` 里的表行编号
   （`B-10` / `B-11` / `G4-A4-05` / `G4-A4-07`）**与任务的「原始编号」不是一一对应**
   （`_task-universe.md` 的 `P2-T01` 写的是 `E65` / `X-6` / `P0 #4`，不含 `B-10`）
   ⇒ 派生层会判它们「**未排期**」⇒ **报进真缺口清单**。
   ⚠️ **这不等于它们没排期** —— 它们可能是某个任务的**改造面内条目**
   ⇒ **本层不猜**（不按语义关联去连任务）⇒ 处置 = **在 `_unresolved.md` 登记歧义 + 人工确认**。
   ⇒ 与第 3 条同源：**「不可见」≠「不存在」**。
9. 🆕 **「已闭合」档 ≠ 台账已销账** —— §4.4 的条目在台账里**仍写 🔴 未闭合**，
   只是逐条回源后**差异早已不存在**。本层**不改状态**（投影不裁定）
   ⇒ 状态订正由**回写台账**完成（追加「阶段归属定级附录」，保留区机制不产生自噬）。

---

## 八、复现

```bash
cd "$(git rev-parse --show-toplevel)"
python3 tools/ledger-derive/derive-stage1.py            # 重新生成
python3 tools/ledger-derive/derive-stage1.py --check    # 守门：产物过期 ⇒ EXIT=1
python3 tools/ledger-derive/derive-stage1.py --report   # 只打印统计
```

