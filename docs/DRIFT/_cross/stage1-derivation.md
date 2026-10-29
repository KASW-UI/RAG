# 阶段一施工单派生层 · 三目录台账 → 施工单（2026-10-30）

> **本文件是「派生层」** —— 每个值都**从** `docs/DRIFT` / `docs/Intentional_Deviations` /
> `docs/Pitfalls` **投影**而来，**不重新裁定任何结论**（每个派生值都带 `rules` 规则号）。
> **机器层** = [`stage1-derivation.tsv`](stage1-derivation.tsv)（全量问题项 × 15 列）
> **生成器 + 守门** = [`tools/ledger-derive/derive-stage1.py`](../../../tools/ledger-derive/derive-stage1.py)
> （`--check` 模式：源台账改了而本层没重生成 ⇒ **EXIT=1**）
> **规范来源** = [`docs/twoStage.md`](../../twoStage.md) §10（11 字段 · P0-P3 × A-D）＋ §5.3（暂缓项 11 字段）

---

## 一、口径（机器可复算）

### 1.1 一句话结论

> **三目录**有 **1174** 个带状态的问题项，
> 其中只有 **149** 个（**12%**）带阶段归属，
> **129** 条是**开放状态**（🔴 未闭合 / 仍生效 / ⏳ 未签字）却**没有阶段归属** ——
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

---

## 三、阶段归属分布

| 归属 | 条目数 | 含义 |
|---|---|---|
| **A** | 10 | 阶段 1 准入阻断项（不处理就无法通过简历项目验收） |
| **B** | 20 | 阶段 1 竞争力补强项（技术亮点与验证闭环） |
| **C** | 46 | 阶段 2 最终工程建设项（业务语义恢复 / 生产工程机制） |
| **D** | 73 | 经分析确认可不实现或不适用（须有明确理由） |
| **—** | 605 | **未派生 —— 待人工定级**（不是「不重要」，是「还没定级」） |

| 优先级 | 条目数 |
|---|---|
| P0 | 9 |
| P1 | 485 |
| P2 | 86 |
| P3 | 594 |

---

## 四、真缺口（**可开工清单**）

> 判据 = **状态开放**（🔴 未闭合 / 仍生效 / ⏳ 未签字）**且** `排期指针 = 未排期`
> ⇒ **不是「没发现」，是「发现了但没进施工单」** —— 旧口径下它们**不可见**。

**合计 467 条** —— ① **A/B 类**（阶段 1 范围内）**0** 条
· ② C/D 类（**阶段 2 承接**，§5.3 暂缓项清单已登记）**27** 条
· ③ 未定级 **122** 条

### 4.1 ① A/B 类 · 开放且未排期（**真缺口 —— 阶段 1 范围内的漏排**）

> **A 类 = 阶段 1 准入阻断项**（不处理就无法通过简历项目验收）·
> **B 类 = 阶段 1 竞争力补强项**。
> 两条都**不在** `_task-universe.md` 的 37 条任务里 ⇒ **施工单漏了它们**。

| 归属 | 优先级 | ID | 域 | 状态 | 证据 | 证据缺口 | 源位置 |
|---|---|---|---|---|---|---|---|

> C/D 类 **27** 条另有去向（阶段 2 恢复方案已在各条台账的 §5.3 十一项表里）
> ⇒ **不计入阶段一判定**（`PhaseGoal-judge.md` §1.1 ⑥ 第 4 条）⇒ 不列。

### 4.2 ③ 未定级（**需人工定级才能进施工单**）

> 派生层**不猜**归属 ⇒ 这一档必须人工定级。
> 排序 = **优先级 × 证据强度**（P1 且证据 C = 既危险又不可复算 ⇒ 排最前）。

| 优先级 | 证据 | ID | 域 | 状态 | 源位置 |
|---|---|---|---|---|---|
| P1 | A 活体 · 绿(0) | `DRIFT-INFRA-001` | _cross | 🔴 未闭合 | `DRIFT/_cross/open-items.md:59` |
| P1 | C 无指针 | `A-3` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:362` |
| P1 | C 无指针 | `A-4` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:363` |
| P1 | C 无指针 | `DRIFT-001` | article | 🔴 未闭合 | `DRIFT/article/api.md:877;DRIFT/article/api.md:894;DRIFT/common/deadline.md:2` |
| P1 | B 静态 | `F-3` | article | 🔴 未闭合 | `DRIFT/article/api.md:1291;DRIFT/favorite/vs-common.md:225` |
| P1 | C 无指针 | `FIX-06` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:1161;DRIFT/article/rpc.md:1207` |
| P1 | C 无指针 | `FIX-08` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:1345;DRIFT/article/rpc.md:1394` |
| P1 | B 静态 | `FIX-10` | article | 🔴 未闭合 | `DRIFT/article/api.md:1839;DRIFT/article/api.md:1914` |
| P1 | C 无指针 | `FIX-11` | article | 🔴 未闭合 | `DRIFT/article/api.md:1966;DRIFT/article/api.md:1980` |
| P1 | C 无指针 | `FIX-13` | article | 🔴 未闭合 | `DRIFT/article/api.md:2011;DRIFT/article/api.md:2080;DRIFT/article/rpc.md:1423` |
| P1 | C 无指针 | `FIX-17` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:1875;DRIFT/article/rpc.md:1950` |
| P1 | B 静态 | `G4-10` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:295` |
| P1 | B 静态 | `G4-2` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:275;DRIFT/comment/vs-common.md:78;DRIFT/favorite/vs-common.md:109` |
| P1 | C 无指针 | `G4-6` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:279;DRIFT/favorite/vs-common.md:112` |
| P1 | C 无指针 | `G4-8` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:290` |
| P1 | B 静态 | `G4-9` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:293` |
| P1 | B 静态 | `L-10` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:100;DRIFT/article/vs-common.md:104;DRIFT/article/vs-common.md:191` |
| P1 | C 无指针 | `L-6` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:76;DRIFT/favorite/vs-common.md:89` |
| P1 | C 无指针 | `L-7` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:97;DRIFT/favorite/vs-common.md:91` |
| P1 | B 静态 | `L-8` | article | 🔴 未闭合 | `DRIFT/article/vs-common.md:98;DRIFT/favorite/vs-common.md:94` |
| P1 | C 无指针 | `Latin-1` | article | 🔴 未闭合 | `DRIFT/article/api.md:2302` |
| P1 | B 静态 | `O-5` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:129` |
| P1 | B 静态 | `S-3` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:332` |
| P1 | A 活体 · 绿(0) | `S-5` | article | 🔴 未闭合 | `DRIFT/article/rpc.md:2400;DRIFT/article/rpc.md:2606` |
| P1 | B 静态 | `C-6` | comment | 🔴 未闭合 | `DRIFT/comment/vs-common.md:62;DRIFT/comment/vs-common.md:223` |
| P1 | B 静态 | `G4-16` | comment | 🔴 未闭合 | `DRIFT/comment/vs-common.md:96` |
| P1 | B 静态 | `MISMATCH-API-01` | comment | 🔴 未闭合 | `DRIFT/comment/api.md:52;DRIFT/comment/api.md:88;DRIFT/comment/api.md:293` |
| P1 | B 静态 | `SD-2026-10-24-CAND-01` | comment | ⏳ 未签字 | `Intentional_Deviations/comment/rpc.md:34` |
| P1 | B 静态 | `SD-2026-10-24-CAND-03` | comment | ⏳ 未签字 | `Intentional_Deviations/comment/rpc.md:229` |
| P1 | B 静态 | `SD-2026-10-24-CAND-05` | comment | ⏳ 未签字 | `Intentional_Deviations/comment/api.md:73` |
| P1 | B 静态 | `SD-2026-10-24-CAND-09` | comment | ⏳ 未签字 | `Intentional_Deviations/comment/rpc.md:162` |
| P1 | B 静态 | `CF-018` | common | 🔴 未闭合 | `DRIFT/common/alignment-2026-10-23.md:848;DRIFT/common/alignment-2026-10-23.md:958` |
| P1 | B 静态 | `DRIFT-COMMON-LOG-001` | common | 🔴 未闭合 | `DRIFT/common/alignment-2026-10-23.md:296` |
| P1 | B 静态 | `T-10` | common | 🔴 未闭合 | `DRIFT/common/alignment-2026-10-23.md:697;DRIFT/common/alignment-2026-10-23.md:718;DRIFT/global/_README.md:97` |
| P1 | B 静态 | `T-6` | common | 🔴 未闭合 | `DRIFT/common/alignment-2026-10-23.md:693;DRIFT/common/alignment-2026-10-23.md:714;DRIFT/global/_README.md:92` |
| P1 | B 静态 | `E68` | favorite | 🔴 未闭合 | `DRIFT/favorite/vs-common.md:30;DRIFT/global/_README.md:433;DRIFT/global/_README.md:541` |
| P1 | C 无指针 | `F-5` | favorite | 🔴 未闭合 | `DRIFT/favorite/vs-common.md:227` |
| P1 | B 静态 | `G4-13` | favorite | 🔴 未闭合 | `DRIFT/favorite/vs-common.md:120` |
| P1 | C 无指针 | `PITFALL-FOLLOW-005` | follow | 🔴 未闭合 | `DRIFT/follow/vs-common.md:300;DRIFT/follow/vs-common.md:424;DRIFT/follow/vs-common.md:1215` |
| P1 | B 静态 | `B-05` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3841` |
| P1 | C 无指针 | `B-10` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3846` |
| P1 | C 无指针 | `B-11` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3847` |
| P1 | C 无指针 | `DRIFT-018` | global | 🔴 未闭合 | `DRIFT/global/lanes/L9-interrupt.md:260;DRIFT/users/_shared.md:67` |
| P1 | C 无指针 | `E10` | global | 🔴 未闭合 | `DRIFT/global/_README.md:341` |
| P1 | C 无指针 | `E18` | global | 🔴 未闭合 | `DRIFT/global/_README.md:358` |
| P1 | C 无指针 | `E20` | global | 🔴 未闭合 | `DRIFT/global/_README.md:368` |
| P1 | C 无指针 | `E22` | global | 🔴 未闭合 | `DRIFT/global/_README.md:370` |
| P1 | A 活体 · 绿(0) | `E23` | global | 🔴 未闭合 | `DRIFT/global/_README.md:371` |
| P1 | B 静态 | `E24` | global | 🔴 未闭合 | `DRIFT/global/_README.md:372` |
| P1 | C 无指针 | `E25` | global | 🔴 未闭合 | `DRIFT/global/_README.md:373` |
| P1 | C 无指针 | `E26` | global | 🔴 未闭合 | `DRIFT/global/_README.md:374` |
| P1 | B 静态 | `E30` | global | 🔴 未闭合 | `DRIFT/global/_README.md:378` |
| P1 | B 静态 | `E32` | global | 🔴 未闭合 | `DRIFT/global/_README.md:380` |
| P1 | B 静态 | `E36` | global | 🔴 未闭合 | `DRIFT/global/_README.md:385` |
| P1 | B 静态 | `E40` | global | 🔴 未闭合 | `DRIFT/global/_README.md:418` |
| P1 | C 无指针 | `E41` | global | 🔴 未闭合 | `DRIFT/global/_README.md:419` |
| P1 | B 静态 | `E44` | global | 🔴 未闭合 | `DRIFT/global/_README.md:424` |
| P1 | B 静态 | `E46` | global | 🔴 未闭合 | `DRIFT/global/_README.md:421;DRIFT/global/_README.md:452` |
| P1 | A 活体 · 绿(0) | `E47` | global | 🔴 未闭合 | `DRIFT/global/_README.md:426;DRIFT/global/_README.md:858;DRIFT/users/admin/rpc.md:3335` |
| P1 | B 静态 | `E50` | global | 🔴 未闭合 | `DRIFT/global/_README.md:438` |
| P1 | C 无指针 | `E51` | global | 🔴 未闭合 | `DRIFT/global/_README.md:439` |
| P1 | B 静态 | `E52` | global | 🔴 未闭合 | `DRIFT/global/_README.md:440` |
| P1 | B 静态 | `E53` | global | 🔴 未闭合 | `DRIFT/global/_README.md:441` |
| P1 | B 静态 | `E55` | global | 🔴 未闭合 | `DRIFT/global/_README.md:443` |
| P1 | B 静态 | `E56` | global | 🔴 未闭合 | `DRIFT/global/_README.md:444` |
| P1 | B 静态 | `E58` | global | 🔴 未闭合 | `DRIFT/global/_README.md:446;DRIFT/users/user/rpc.md:2257;DRIFT/users/user/rpc.md:2328` |
| P1 | C 无指针 | `E61` | global | 🔴 未闭合 | `DRIFT/global/_README.md:449;DRIFT/global/_README.md:839` |
| P1 | B 静态 | `E63` | global | 🔴 未闭合 | `DRIFT/global/_README.md:450` |
| P1 | A 活体 · 绿(0) | `E64` | global | 🔴 未闭合 | `DRIFT/global/_README.md:429` |
| P1 | B 静态 | `E69` | global | 🔴 未闭合 | `DRIFT/global/_README.md:434;DRIFT/global/_README.md:563` |
| P1 | B 静态 | `E70` | global | 🔴 未闭合 | `DRIFT/global/_README.md:435` |
| P1 | C 无指针 | `E9` | global | 🔴 未闭合 | `DRIFT/global/_README.md:340` |
| P1 | A 活体 · 环境(2) | `FB-03` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:402;DRIFT/like/vs-common.md:439;DRIFT/like/vs-common.md:448` |
| P1 | A 活体 · 绿(0) | `G4-A4-01` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3751;DRIFT/global/vs-common.md:3761` |
| P1 | C 无指针 | `G4-A4-02` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3752;DRIFT/global/vs-common.md:3774` |
| P1 | C 无指针 | `G4-A4-05` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3755;DRIFT/global/vs-common.md:3812` |
| P1 | C 无指针 | `G4-A4-06` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3756;DRIFT/global/vs-common.md:3867` |
| P1 | C 无指针 | `G4-A4-07` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:3757;DRIFT/global/vs-common.md:3878` |
| P1 | C 无指针 | `L4-01` | global | 🔴 未闭合 | `DRIFT/global/lanes/L4-flyway.md:172` |
| P1 | B 静态 | `PIT-GLOBAL-019` | global | 🔴 未闭合 | `DRIFT/global/lanes/_PROTOCOL.md:180;Pitfalls/global/cross-domain.md:618` |
| P1 | B 静态 | `PIT-GLOBAL-020` | global | 🔴 未闭合 | `DRIFT/global/lanes/_PROTOCOL.md:181;Pitfalls/global/cross-domain.md:785` |
| P1 | B 静态 | `PIT-GLOBAL-022` | global | 🔴 未闭合 | `DRIFT/global/lanes/L5-signoff.md:309;Pitfalls/global/cross-domain.md:706` |
| P1 | B 静态 | `PIT-GLOBAL-023` | global | 🔴 未闭合 | `DRIFT/global/lanes/L5-signoff.md:310;Pitfalls/global/common-tails.md:288` |
| P1 | B 静态 | `SD-2026-10-23-09` | global | ⏳ 未签字 | `DRIFT/global/lanes/L5-signoff.md:190;Intentional_Deviations/like/api.md:54` |
| P1 | B 静态 | `SD-2026-10-24-10` | global | ⏳ 未签字 | `DRIFT/global/common-tails.md:547;Intentional_Deviations/like/api.md:87` |
| P1 | B 静态 | `SD-2026-10-25-CAND-10` | global | ⏳ 未签字 | `DRIFT/global/lanes/L5-signoff.md:245;Intentional_Deviations/hot/api.md:33;Intentional_Deviations/hot/api.md:421` |
| P1 | B 静态 | `SD-2026-10-25-CAND-14` | global | ⏳ 未签字 | `Intentional_Deviations/global/common-tails.md:436` |
| P1 | B 静态 | `SD-2026-10-25-CAND-15` | global | ⏳ 未签字 | `Intentional_Deviations/global/common-tails.md:510` |
| P1 | B 静态 | `T-12` | global | 🔴 未闭合 | `DRIFT/global/common-tails.md:519;DRIFT/global/common-tails.md:642;DRIFT/global/lanes/L5-signoff.md:118` |
| P1 | C 无指针 | `T-13` | global | 🔴 未闭合 | `DRIFT/global/common-tails.md:561;DRIFT/global/lanes/L1-delivery.md:296` |
| P1 | B 静态 | `UTF-8` | global | 🔴 未闭合 | `DRIFT/global/vs-common.md:949` |
| P1 | B 静态 | `DRIFT-HOTAPI-027` | hot | 🔴 未闭合 | `DRIFT/hot/api.md:1720` |
| P1 | B 静态 | `G-HOT-001` | hot | 🔴 未闭合 | `DRIFT/hot/rpc.md:167` |
| P1 | B 静态 | `OP-09` | like | 🔴 未闭合 | `DRIFT/like/rpc.md:504;DRIFT/message/rpc.md:505` |
| P1 | B 静态 | `SD-2026-10-24-11` | like | ⏳ 未签字 | `Intentional_Deviations/like/api.md:133` |
| P1 | B 静态 | `U-06` | like | 🔴 未闭合 | `DRIFT/like/api.md:829;DRIFT/like/api.md:842` |
| P1 | A 活体 · 绿(0) | `DRIFT-MSG-012` | message | 🔴 未闭合 | `DRIFT/message/rpc.md:262` |
| P1 | B 静态 | `DRIFT-MSG-V-020` | message | 🔴 未闭合 | `DRIFT/message/rpc.md:1671` |
| P1 | C 无指针 | `DRIFT-MSG-V-027` | message | 🔴 未闭合 | `DRIFT/message/rpc.md:2110` |
| P1 | B 静态 | `DRIFT-MSGAPI-009` | message | 🔴 未闭合 | `DRIFT/message/api.md:858` |
| P1 | B 静态 | `DRIFT-MSGAPI-011` | message | 🔴 未闭合 | `DRIFT/message/api.md:1499;DRIFT/message/vs-common.md:39` |
| P1 | B 静态 | `FB-G2-03` | message | 🔴 未闭合 | `DRIFT/message/feedback-to-g4.md:52` |
| P1 | A 活体 · 绿(0) | `SD-04` | message | 🔴 未闭合 | `DRIFT/message/proto.md:34` |
| P1 | B 静态 | `ADJ-01` | users | 🔴 未闭合 | `DRIFT/users/_shared.md:965` |
| P1 | C 无指针 | `D-015` | users | 🔴 未闭合 | `DRIFT/users/user/api.md:1973` |
| P1 | B 静态 | `DRIFT-ADMINAPI-013` | users | 🔴 未闭合 | `DRIFT/users/admin/api.md:2448` |
| P1 | A 活体 · 绿(0) | `DRIFT-ARPC-007` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:1203` |
| P1 | B 静态 | `DRIFT-ARPC-008` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:1302` |
| P1 | B 静态 | `DRIFT-ARPC-009` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:2947` |
| P1 | B 静态 | `DRIFT-ARPC-011` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:3118` |
| P1 | B 静态 | `DRIFT-ARPC-012` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:3195;DRIFT/users/admin/rpc.md:4100` |
| P1 | B 静态 | `DRIFT-ARPC-014` | users | 🔴 未闭合 | `DRIFT/users/admin/rpc.md:4435` |
| P1 | B 静态 | `DRIFT-USERAPI-017` | users | 🔴 未闭合 | `DRIFT/users/user/api.md:3635` |
| P1 | A 活体 · 混(0/1) | `DRIFT-USERRPC-V-001` | users | 🔴 未闭合 | `DRIFT/users/user/rpc.md:473;DRIFT/users/user/rpc.md:1360` |
| P1 | A 活体 · 混(0/1) | `DRIFT-USERRPC-V-016` | users | 🔴 未闭合 | `DRIFT/users/user/rpc.md:2714` |
| P1 | B 静态 | `DRIFT-USERSCOMMON-006` | users | 🔴 未闭合 | `DRIFT/users/common.md:788;DRIFT/users/common.md:869` |
| P1 | A 活体 · 绿(0) | `Round-2` | users | 🔴 未闭合 | `DRIFT/users/vs-common.md:5072` |
| P1 | B 静态 | `SD-2026-10-25-CAND-16` | users | ⏳ 未签字 | `Intentional_Deviations/users/_shared.md:18` |
| P1 | B 静态 | `SD-2026-10-25-CAND-17` | users | ⏳ 未签字 | `Intentional_Deviations/users/_shared.md:110` |
| P1 | B 静态 | `V-002` | users | 🔴 未闭合 | `DRIFT/users/user/rpc.md:995;DRIFT/users/vs-common.md:1578` |
| P1 | A 活体 · 绿(0) | `V-004` | users | 🔴 未闭合 | `DRIFT/users/_shared.md:777` |
| P1 | C 无指针 | `V-013` | users | 🔴 未闭合 | `DRIFT/users/user/rpc.md:2353` |

### 4.3 同编号多态（**同一编号在多处登记、状态不一致**）

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

## 六、全量派生表（1174 条问题项）

> 机读请用 [`stage1-derivation.tsv`](stage1-derivation.tsv)（同一次生成，15 列）。

| ID | 目录 | 域 | 状态 | 证据 | 优先级 | 归属 | 阻断 | 最终目标 | 排期 | 缺口 | 源位置 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `G-3` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/article/api.md:2692` |
| `L-4` | DRIF | article | 🟡 部分闭合 | A 活体 · 绿(0) | P0 | A | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/article/vs-common.md:73;DRIFT/favorite/api.md:909;DRIFT/favorite/rpc.md:3783` |
| `T-2` | DRIF | article | 🟡 部分闭合 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/article/vs-common.md:418;DRIFT/common/alignment-2026-10-23.md:689;DRIFT/common/alignment-2026-10-23.md:710` |
| `DRIFT-SF-001` | DRIF | common | ✅ 已一致 | A 活体 · 绿(0) | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:420;DRIFT/common/alignment-2026-10-23.md:870;DRIFT/common/alignment-2026-10-23.md:991` |
| `SF-001` | DRIF | common | ✅ 已一致 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:866` |
| `G-13` | DRIF | follow | ✅ 已一致 | B 静态 | P0 | A | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/api.md:1098;DRIFT/follow/stage1-admission.md:214;DRIFT/follow/vs-common.md:770` |
| `H-1` | DRIF | hot | 🟡 部分闭合 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/hot/vs-common.md:67;DRIFT/hot/vs-common.md:267;DRIFT/users/admin/rpc.md:962` |
| `S1-FOLLOW-007` | DRIF | users | 🔴 未闭合 | B 静态 | P0 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | BLK-03 | 静态取证 · 缺活体对拍 | `DRIFT/users/stage1-admission.md:524` |
| `S1-USERS-001` | DRIF | users | ✅ 已一致 | B 静态 | P0 | A | 否 | 台账已写 · 阶段 1 先补**最小可复现说明书**（README：架构一句话 / 模块清单 / 环境变量表 / 启动顺序 / 验证命 | P5-T02 | — | `DRIFT/users/stage1-admission.md:91;DRIFT/users/stage1-admission.md:491;DRIFT/users/stage1-admission.md:515` |
| `E65` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | A | 是 | 间接 · 阶段 1 闭合后才有阶段 2 | P1-T05,P2-T01,P2-T02,P2-T03,P2-T04,P2-T05,P2-T06,P3-T01 ｜ BLK-02 | 静态取证 · 缺活体对拍 | `DRIFT/comment/rpc.md:257;DRIFT/comment/rpc.md:930;DRIFT/favorite/rpc.md:3376` |
| `E59` | DRIF | global | ✅ 已一致 | B 静态 | P1 | B | 否 | 台账已写 · ✅ **已修**：三处 catch 改 `throw e;`（原样上抛）⇒ **不做**「换成另一个业务码」 ｜ ① 三 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:447;DRIFT/like/vs-common.md:1498;DRIFT/message/rpc.md:2770` |
| `E60` | DRIF | global | ✅ 已一致 | B 静态 | P1 | B | 否 | 台账已写 · ✅ **已修**（**补业务错误记录 + 错误分流落在 `publish` 内部**） ｜ ① 双失败 ⇒ 真实日志出现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:448;DRIFT/like/rpc.md:1597;DRIFT/like/rpc.md:1742` |
| `S1-FOLLOW-008` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-05 | 静态取证 · 缺活体对拍 | `DRIFT/users/stage1-admission.md:525` |
| `S1-FOLLOW-009` | DRIF | users | 🔴 未闭合 | C 无指针 | P1 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | GAP-06 | 无可复算指针 · 结论不可复算 | `DRIFT/users/stage1-admission.md:526` |
| `E2` | DRIF | follow | 🟡 部分闭合 | C 无指针 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:377;DRIFT/global/_README.md:333` |
| `E7` | DRIF | follow | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:382;DRIFT/global/_README.md:338` |
| `S1-FOLLOW-004` | DRIF | follow | 🟡 部分闭合 | B 静态 | P2 | B | 否 | 台账已写 · ① `docker-compose.yaml` 补 `follow-rpc` / `follow-api` / `adm | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:145;DRIFT/follow/stage1-admission.md:394;DRIFT/follow/stage1-admission.md:537` |
| `E1` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:332;DRIFT/users/stage1-admission.md:503` |
| `S1-USERS-002` | DRIF | users | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | B | 否 | 台账已写 · ① 全仓 9 处统一为**一个**来源（`.env` ⇒ env 注入，**取消 fallback**，与 Go 的 f | P4-T01,P4-T02 | — | `DRIFT/users/stage1-admission.md:108;DRIFT/users/stage1-admission.md:493;DRIFT/users/stage1-admission.md:516` |
| `S1-USERS-004` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | B | 否 | 台账已写 · ① 事务边界上移到 `UserDomainService` 的 public 入口（与 Go 的「数据层显式闭包」同形） | 未排期 | 已定性 · 未排期 | `DRIFT/users/stage1-admission.md:158;DRIFT/users/stage1-admission.md:518` |
| `E4` | DRIF | follow | ✅ 已一致 | C 无指针 | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:379;DRIFT/global/_README.md:335;DRIFT/users/stage1-admission.md:506` |
| `E5` | DRIF | follow | ✅ 已一致 | C 无指针 | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:380;DRIFT/global/_README.md:336;DRIFT/users/stage1-admission.md:507` |
| `S1-FOLLOW-001` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | B | 否 | 台账已写 · ① 统一方案：给所有 rpc 组件开**独立 management 端口**（与管理 HTTP 端口分离，避开 `man | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:74;DRIFT/follow/stage1-admission.md:391;DRIFT/follow/stage1-admission.md:540` |
| `S1-FOLLOW-003` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | B | 否 | 台账已写 · ① 把 `ObservabilityLifecycle` 的关闭路径与 **web 类型**解耦（延迟类加载 / 反射隔 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:123;DRIFT/follow/stage1-admission.md:393;DRIFT/follow/stage1-admission.md:541` |
| `S1-FOLLOW-010` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:548;DRIFT/follow/vs-common.md:821;DRIFT/follow/vs-common.md:1248` |
| `S1-FOLLOW-011` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:549;DRIFT/follow/vs-common.md:822;DRIFT/follow/vs-common.md:1249` |
| `E6` | DRIF | global | ✅ 已一致 | A 活体 · 环境(2) | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | 未排期 | 已定性 · 未排期 | `DRIFT/global/_README.md:337` |
| `D-08` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | B | 否 | 间接 · 阶段 1 闭合后才有阶段 2 | P4-T09,P7-T01 | — | `DRIFT/like/rpc.md:1984` |
| `S1-USERS-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | B | 否 | 台账已写 · ① `.gitignore` 加 `.env`（+ `.env.*` 但放行 `.env.example`）；② 提交  | P5-T02 | — | `DRIFT/users/stage1-admission.md:141` |
| `S1-USERS-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | B | 否 | 台账已写 · 域内 1 行改完（`${FOLLOW_RPC_PORT:8082}`）⇒ 本轮即做（T3）；阶段 2 可把 6 个组件的 | 未排期 | 已定性 · 未排期 | `DRIFT/users/stage1-admission.md:175;DRIFT/users/stage1-admission.md:519` |
| `T-3` | DRIF | article | 🔴 未闭合 | A 活体 · 环境(2) | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/article/vs-common.md:608;DRIFT/comment/vs-common.md:339;DRIFT/common/alignment-2026-10-23.md:690` |
| `DRIFT-CF-001` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:375` |
| `DRIFT-CF-002` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:376` |
| `DRIFT-CF-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:385` |
| `DRIFT-CF-004` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:398` |
| `DRIFT-CF-005` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:377` |
| `DRIFT-CF-006` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:378` |
| `DRIFT-CF-008` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:379` |
| `DRIFT-CF-012` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:388` |
| `DRIFT-CF-013` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:389` |
| `DRIFT-CF-014` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:390` |
| `DRIFT-CF-015` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:391` |
| `DRIFT-CF-017` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:392` |
| `DRIFT-LOG-001` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:413` |
| `DRIFT-LOG-002` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:414` |
| `DRIFT-LOG-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:415` |
| `DRIFT-LOG-005` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:417` |
| `DRIFT-LOG-006` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:418` |
| `DRIFT-LOG-007` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:419` |
| `DRIFT-SF-002` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:421` |
| `DRIFT-SF-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:422` |
| `LOG-003` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:856` |
| `E3` | DRIF | follow | 🔴 未闭合 | C 无指针 | P1 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/stage1-admission.md:378;DRIFT/users/stage1-admission.md:505` |
| `CF-001` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:834` |
| `CF-002` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:835` |
| `CF-005` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:838` |
| `CF-006` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:839` |
| `CF-008` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:840` |
| `CF-012` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:843` |
| `CF-013` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:844` |
| `CF-014` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:845` |
| `CF-015` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:846` |
| `CF-017` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:847` |
| `LOG-001` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:854` |
| `LOG-002` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:855` |
| `LOG-005` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:858` |
| `LOG-006` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:859` |
| `LOG-007` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:860` |
| `SF-002` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:867` |
| `SF-003` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:868` |
| `T-17` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/global/common-tails.md:612;DRIFT/global/common-tails.md:643` |
| `G-19` | DRIF | hot | 🟡 部分闭合 | C 无指针 | P2 | C | 否 | 影响 · 阶段 2 必须恢复 | P5-T01 ｜ GAP-01 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/proto.md:67` |
| `P-U-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/proto.md:176;DRIFT/article/proto.md:178` |
| `U-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | C | 否 | 影响 · 阶段 2 必须恢复 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:2442;DRIFT/users/admin/_shared.md:213;DRIFT/users/admin/rpc.md:1477` |
| `CF-004` | DRIF | common | ✅ 已一致 | B 静态 | P3 | C | 是 | 影响 · 阶段 2 必须恢复 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:837` |
| `DRIFT-LIKE-003` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | C | 否 | 台账已写 · ❌ **否**（**C 类暂缓 ⇒ 不计入「真漂移 = 0」的口径**）<br>**依据（逐条可复算）**：<br>·  | DEC-01 | — | `DRIFT/like/rpc.md:151;DRIFT/like/rpc.md:1395;DRIFT/like/rpc.md:1829` |
| `DRIFT-CF-009` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:386` |
| `DRIFT-CF-011` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:387` |
| `DRIFT-LOG-004` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:416` |
| `U-1` | DRIF | users | 🔴 未闭合 | C 无指针 | P1 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/admin/_shared.md:211;DRIFT/users/admin/rpc.md:1475` |
| `A-1` | DRIF | article | 🟡 部分闭合 | C 无指针 | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:360;DRIFT/users/admin/_shared.md:217;DRIFT/users/admin/api.md:1329` |
| `S1-FOLLOW-013` | DRIF | follow | 🟡 部分闭合 | A 活体 · 混(0/2) | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/vs-common.md:823` |
| `T-0` | DRIF | users | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/users/_shared.md:1335` |
| `SD-2026-10-23-05` | Inte | _INDEX.md | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/_INDEX.md:233;Intentional_Deviations/like/proto.md:11` |
| `SD-2026-10-23-06` | Inte | _INDEX.md | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/_INDEX.md:102;Intentional_Deviations/like/rpc.md:13;Intentional_Deviations/message/rpc.md:174` |
| `SD-2026-10-23-07` | Inte | _INDEX.md | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/_INDEX.md:103;Intentional_Deviations/_INDEX.md:118;Intentional_Deviations/like/rpc.md:82` |
| `SD-2026-10-23-02` | Inte | _cross | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/_cross/signed-deviations.md:57` |
| `SD-2026-10-23-03` | Inte | _cross | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/_cross/signed-deviations.md:71` |
| `DRIFT-ARTICLE-API-001` | DRIF | article | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/article/api.md:576;Intentional_Deviations/article/api.md:25` |
| `DRIFT-ARTICLE-API-009` | DRIF | article | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:750;Intentional_Deviations/article/api.md:27` |
| `U-4` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/article/rpc.md:2423;DRIFT/article/rpc.md:2455;DRIFT/users/admin/_shared.md:214` |
| `C-1` | DRIF | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | P7-T01 | — | `DRIFT/comment/vs-common.md:57;DRIFT/comment/vs-common.md:218;Intentional_Deviations/common/_signed-deviations.md:156` |
| `SD-2026-10-23-04` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/proto.md:61;Intentional_Deviations/_INDEX.md:222;Intentional_Deviations/_INDEX.md:248` |
| `SD-2026-10-24-CAND-02` | Inte | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/rpc.md:95` |
| `SD-2026-10-24-CAND-04` | Inte | comment | ⛔ 已失效 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/api.md:28` |
| `SD-2026-10-24-CAND-06` | Inte | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/rpc.md:274` |
| `SD-2026-10-24-CAND-07` | Inte | comment | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/comment/api.md:113` |
| `CF-009` | DRIF | common | ✅ 已一致 | B 静态 | P3 | D | 是 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/common/alignment-2026-10-23.md:841` |
| `DEV-FAV-API-001` | DRIF | favorite | ✅ 已一致 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/api.md:275;Intentional_Deviations/favorite/api.md:31;Intentional_Deviations/favorite/api.md:40` |
| `DEV-FAV-API-002` | DRIF | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/favorite/api.md:446;Intentional_Deviations/favorite/api.md:32;Intentional_Deviations/favorite/api.md:56` |
| `DEV-FAV-COMMON-001` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/common.md:23;Intentional_Deviations/favorite/common.md:38` |
| `DEV-FAV-COMMON-002` | Inte | favorite | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/favorite/common.md:24;Intentional_Deviations/favorite/common.md:52` |
| `DEV-FAV-RPC-001` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:37;Intentional_Deviations/favorite/rpc.md:87` |
| `DEV-FAV-RPC-002` | DRIF | favorite | ⛔ 已撤销 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:472;Intentional_Deviations/favorite/rpc.md:101` |
| `DEV-FAV-RPC-003` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:38;Intentional_Deviations/favorite/rpc.md:112` |
| `DEV-FAV-RPC-004` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:39;Intentional_Deviations/favorite/rpc.md:126` |
| `DEV-FAV-RPC-005` | DRIF | favorite | ⛔ 已撤销 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:324;Intentional_Deviations/favorite/rpc.md:167` |
| `DEV-FAV-RPC-006` | Inte | favorite | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:40;Intentional_Deviations/favorite/rpc.md:139` |
| `DEV-FAV-RPC-007` | Inte | favorite | ✅ 仍成立 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/favorite/rpc.md:41;Intentional_Deviations/favorite/rpc.md:153` |
| `S1-FOLLOW-002` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | D | 否 | 台账已写 · —— **阶段 2 也不做**（签字决定保留全局闸门）<br>⚠️ **若将来要重开**：必须**先撤销签字**（用户拍 | 未排期 | 已定性 · 未排期 | `DRIFT/follow/stage1-admission.md:97;DRIFT/follow/stage1-admission.md:536;DRIFT/follow/vs-common.md:1208` |
| `CYCLE-001` | DRIF | global | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/cycle-deps.md:11;Intentional_Deviations/global/cycle-deps.md:20` |
| `N-1` | DRIF | global | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L10-ddldeep.md:332;DRIFT/users/user/api.md:2311;Intentional_Deviations/users/admin/_shared.md:65` |
| `N-2` | DRIF | global | ✅ 仍成立 | A 活体 · 红(1) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L10-ddldeep.md:333;DRIFT/users/user/rpc.md:1047;Intentional_Deviations/users/user/rpc.md:18` |
| `N-3` | DRIF | global | ⛔ 已失效 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L10-ddldeep.md:334;Intentional_Deviations/users/admin/_shared.md:111;Intentional_Deviations/users/admin/_shared.md:115` |
| `N-4` | DRIF | global | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L10-ddldeep.md:335;DRIFT/users/admin/rpc.md:2255;Intentional_Deviations/users/admin/_shared.md:84` |
| `SD-2026-10-24-04` | DRIF | global | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/lanes/L5-signoff.md:134;DRIFT/message/rpc.md:2717;Intentional_Deviations/message/rpc.md:96` |
| `SD-2026-10-25-01` | Inte | global | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/common-tails.md:11` |
| `SD-2026-10-25-02` | Inte | global | ⛔ 已撤销 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/common-tails.md:125;Intentional_Deviations/global/common-tails.md:226;Intentional_Deviations/global/common-tails.md:355` |
| `SD-2026-10-25-03` | Inte | global | ⛔ 已撤销 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/global/cross-domain.md:13` |
| `SD-2026-10-25-04` | Inte | global | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/global/common-tails.md:205` |
| `SD-HOT-API-006` | DRIF | global | ⛔ 已撤销 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/global/lanes/L1-delivery.md:744;Intentional_Deviations/hot/api.md:28;Intentional_Deviations/hot/api.md:45` |
| `SD-1` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:798;Intentional_Deviations/hot/rpc.md:44;Intentional_Deviations/hot/rpc.md:179` |
| `SD-2` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:45;Intentional_Deviations/hot/rpc.md:197` |
| `SD-3` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:46;Intentional_Deviations/hot/rpc.md:211` |
| `SD-HOT-API-001` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:23;Intentional_Deviations/hot/api.md:53` |
| `SD-HOT-API-002` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:24;Intentional_Deviations/hot/api.md:85` |
| `SD-HOT-API-003` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:25;Intentional_Deviations/hot/api.md:99` |
| `SD-HOT-API-004` | Inte | hot | ⛔ 已失效 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:26;Intentional_Deviations/hot/api.md:43;Intentional_Deviations/hot/api.md:111` |
| `SD-HOT-API-005` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:27;Intentional_Deviations/hot/api.md:158` |
| `SD-HOT-API-007` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:29;Intentional_Deviations/hot/api.md:280` |
| `SD-HOT-API-008` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:30;Intentional_Deviations/hot/api.md:327` |
| `SD-HOT-API-009` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:31;Intentional_Deviations/hot/api.md:360` |
| `SD-HOT-API-010` | Inte | hot | ✅ 仍成立 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/api.md:32;Intentional_Deviations/hot/api.md:377` |
| `SD-HOT-RPC-001` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:40;Intentional_Deviations/hot/rpc.md:52` |
| `SD-HOT-RPC-002` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:41;Intentional_Deviations/hot/rpc.md:76` |
| `SD-HOT-RPC-003` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:42;Intentional_Deviations/hot/rpc.md:111` |
| `SD-HOT-RPC-004` | Inte | hot | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/hot/rpc.md:43;Intentional_Deviations/hot/rpc.md:137` |
| `D-1` | DRIF | like | ⛔ 已失效 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/like/vs-common.md:67;DRIFT/message/vs-common.md:69;DRIFT/task/vs-common.md:61` |
| `SD-2026-10-23-08` | DRIF | message | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | DEC-02 | — | `DRIFT/message/rpc.md:2068;Intentional_Deviations/_INDEX.md:104;Intentional_Deviations/_INDEX.md:119` |
| `SD-2026-10-24-01` | DRIF | message | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2714;Intentional_Deviations/_INDEX.md:105;Intentional_Deviations/article/rpc.md:23` |
| `SD-2026-10-24-02` | DRIF | message | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2715;Intentional_Deviations/_INDEX.md:106;Intentional_Deviations/article/rpc.md:164` |
| `N-5` | Inte | users | ✅ 仍成立 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/users/admin/_shared.md:99` |
| `N-6` | Inte | users | ⛔ 已失效 | C 无指针 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 无可复算指针 · 结论不可复算 | `Intentional_Deviations/users/admin/_shared.md:116` |
| `SD-2026-10-23-01` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:1415;Intentional_Deviations/_INDEX.md:101;Intentional_Deviations/_INDEX.md:210` |
| `SD-USERAPI-001` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/user/api.md:12` |
| `SD-USERAPI-002` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/user/api.md:106;Intentional_Deviations/users/user/api.md:348` |
| `SD-USERAPI-003` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/user/api.md:265` |
| `SD-USERSCOMMON-001` | Inte | users | ✅ 仍成立 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `Intentional_Deviations/users/common.md:12` |
| `U-5` | DRIF | users | ✅ 已一致 | B 静态 | P3 | D | 否 | 不影响 · 经分析确认不实现 | 未排期 | 已定性 · 未排期 | `DRIFT/users/admin/_shared.md:215` |
| `DRIFT-INFRA-001` | DRIF | _cross | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/_cross/open-items.md:59` |
| `A-3` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:362` |
| `A-4` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:363` |
| `DRIFT-001` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:877;DRIFT/article/api.md:894;DRIFT/common/deadline.md:2` |
| `F-3` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/api.md:1291;DRIFT/favorite/vs-common.md:225` |
| `FIX-06` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1161;DRIFT/article/rpc.md:1207` |
| `FIX-08` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1345;DRIFT/article/rpc.md:1394` |
| `FIX-10` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/api.md:1839;DRIFT/article/api.md:1914` |
| `FIX-11` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1966;DRIFT/article/api.md:1980` |
| `FIX-13` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2011;DRIFT/article/api.md:2080;DRIFT/article/rpc.md:1423` |
| `FIX-17` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1875;DRIFT/article/rpc.md:1950` |
| `G4-10` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/vs-common.md:295` |
| `G4-2` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/vs-common.md:275;DRIFT/comment/vs-common.md:78;DRIFT/favorite/vs-common.md:109` |
| `G4-6` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:279;DRIFT/favorite/vs-common.md:112` |
| `G4-8` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:290` |
| `G4-9` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/vs-common.md:293` |
| `L-10` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/vs-common.md:100;DRIFT/article/vs-common.md:104;DRIFT/article/vs-common.md:191` |
| `L-6` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:76;DRIFT/favorite/vs-common.md:89` |
| `L-7` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:97;DRIFT/favorite/vs-common.md:91` |
| `L-8` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/vs-common.md:98;DRIFT/favorite/vs-common.md:94` |
| `Latin-1` | DRIF | article | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2302` |
| `O-5` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/rpc.md:129` |
| `S-3` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/rpc.md:332` |
| `S-5` | DRIF | article | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:2400;DRIFT/article/rpc.md:2606` |
| `C-3` | DRIF | comment | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | P2-T01,P3-T04 ｜ BLK-02,DEC-05 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:59;DRIFT/comment/vs-common.md:112;DRIFT/comment/vs-common.md:220` |
| `C-6` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/comment/vs-common.md:62;DRIFT/comment/vs-common.md:223` |
| `E66` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | P2-T01,P2-T02,P2-T06 ｜ DEC-05 | 静态取证 · 缺活体对拍 | `DRIFT/comment/rpc.md:290;DRIFT/global/_README.md:431;DRIFT/global/vs-common.md:31` |
| `G4-16` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/comment/vs-common.md:96` |
| `MISMATCH-API-01` | DRIF | comment | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/comment/api.md:52;DRIFT/comment/api.md:88;DRIFT/comment/api.md:293` |
| `SD-2026-10-24-CAND-01` | Inte | comment | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/comment/rpc.md:34` |
| `SD-2026-10-24-CAND-03` | Inte | comment | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/comment/rpc.md:229` |
| `SD-2026-10-24-CAND-05` | Inte | comment | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/comment/api.md:73` |
| `SD-2026-10-24-CAND-09` | Inte | comment | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/comment/rpc.md:162` |
| `CF-018` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:848;DRIFT/common/alignment-2026-10-23.md:958` |
| `DRIFT-COMMON-LOG-001` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:296` |
| `T-10` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:697;DRIFT/common/alignment-2026-10-23.md:718;DRIFT/global/_README.md:97` |
| `T-6` | DRIF | common | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/common/alignment-2026-10-23.md:693;DRIFT/common/alignment-2026-10-23.md:714;DRIFT/global/_README.md:92` |
| `E62` | DRIF | favorite | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | P2-T05 | 静态取证 · 缺活体对拍 | `DRIFT/favorite/rpc.md:3111;DRIFT/favorite/vs-common.md:28;DRIFT/global/_README.md:428` |
| `E68` | DRIF | favorite | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/favorite/vs-common.md:30;DRIFT/global/_README.md:433;DRIFT/global/_README.md:541` |
| `F-5` | DRIF | favorite | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/vs-common.md:227` |
| `G4-13` | DRIF | favorite | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/favorite/vs-common.md:120` |
| `PITFALL-FOLLOW-005` | DRIF | follow | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/follow/vs-common.md:300;DRIFT/follow/vs-common.md:424;DRIFT/follow/vs-common.md:1215` |
| `B-05` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/vs-common.md:3841` |
| `B-10` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3846` |
| `B-11` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3847` |
| `DRIFT-018` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L9-interrupt.md:260;DRIFT/users/_shared.md:67` |
| `E10` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:341` |
| `E18` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:358` |
| `E20` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:368` |
| `E22` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:370` |
| `E23` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:371` |
| `E24` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:372` |
| `E25` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:373` |
| `E26` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:374` |
| `E30` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:378` |
| `E31` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | DEC-03 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:379` |
| `E32` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:380` |
| `E36` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:385` |
| `E40` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:418` |
| `E41` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:419` |
| `E44` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:424` |
| `E46` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:421;DRIFT/global/_README.md:452` |
| `E47` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:426;DRIFT/global/_README.md:858;DRIFT/users/admin/rpc.md:3335` |
| `E48` | DRIF | global | 🔴 未闭合 | A 活体 · 混(0/4) | P1 | — | 否 | 未判定 | P2-T05 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:427;DRIFT/global/_README.md:750;DRIFT/global/_README.md:776` |
| `E50` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:438` |
| `E51` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:439` |
| `E52` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:440` |
| `E53` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:441` |
| `E55` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:443` |
| `E56` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:444` |
| `E58` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:446;DRIFT/users/user/rpc.md:2257;DRIFT/users/user/rpc.md:2328` |
| `E61` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:449;DRIFT/global/_README.md:839` |
| `E63` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:450` |
| `E64` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:429` |
| `E69` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:434;DRIFT/global/_README.md:563` |
| `E70` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/_README.md:435` |
| `E9` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:340` |
| `FB-03` | DRIF | global | 🔴 未闭合 | A 活体 · 环境(2) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:402;DRIFT/like/vs-common.md:439;DRIFT/like/vs-common.md:448` |
| `G4-A4-01` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:3751;DRIFT/global/vs-common.md:3761` |
| `G4-A4-02` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3752;DRIFT/global/vs-common.md:3774` |
| `G4-A4-05` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3755;DRIFT/global/vs-common.md:3812` |
| `G4-A4-06` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3756;DRIFT/global/vs-common.md:3867` |
| `G4-A4-07` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3757;DRIFT/global/vs-common.md:3878` |
| `L4-01` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L4-flyway.md:172` |
| `PIT-GLOBAL-018` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | P2-T05 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/L12-rpcboot-article-comment-like.md:247;DRIFT/global/lanes/L2-rpcgate.md:81;DRIFT/global/lanes/_PROTOCOL.md:182` |
| `PIT-GLOBAL-019` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/_PROTOCOL.md:180;Pitfalls/global/cross-domain.md:618` |
| `PIT-GLOBAL-020` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/_PROTOCOL.md:181;Pitfalls/global/cross-domain.md:785` |
| `PIT-GLOBAL-022` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/L5-signoff.md:309;Pitfalls/global/cross-domain.md:706` |
| `PIT-GLOBAL-023` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/lanes/L5-signoff.md:310;Pitfalls/global/common-tails.md:288` |
| `SD-2026-10-23-09` | DRIF | global | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/lanes/L5-signoff.md:190;Intentional_Deviations/like/api.md:54` |
| `SD-2026-10-24-10` | DRIF | global | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/common-tails.md:547;Intentional_Deviations/like/api.md:87` |
| `SD-2026-10-25-CAND-10` | DRIF | global | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/lanes/L5-signoff.md:245;Intentional_Deviations/hot/api.md:33;Intentional_Deviations/hot/api.md:421` |
| `SD-2026-10-25-CAND-14` | Inte | global | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/global/common-tails.md:436` |
| `SD-2026-10-25-CAND-15` | Inte | global | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/global/common-tails.md:510` |
| `T-12` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/common-tails.md:519;DRIFT/global/common-tails.md:642;DRIFT/global/lanes/L5-signoff.md:118` |
| `T-13` | DRIF | global | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/common-tails.md:561;DRIFT/global/lanes/L1-delivery.md:296` |
| `UTF-8` | DRIF | global | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/global/vs-common.md:949` |
| `DRIFT-HOTAPI-027` | DRIF | hot | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/hot/api.md:1720` |
| `G-HOT-001` | DRIF | hot | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/hot/rpc.md:167` |
| `OP-09` | DRIF | like | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/like/rpc.md:504;DRIFT/message/rpc.md:505` |
| `SD-2026-10-24-11` | Inte | like | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/like/api.md:133` |
| `U-06` | DRIF | like | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/like/api.md:829;DRIFT/like/api.md:842` |
| `DRIFT-MSG-012` | DRIF | message | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:262` |
| `DRIFT-MSG-V-020` | DRIF | message | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/message/rpc.md:1671` |
| `DRIFT-MSG-V-027` | DRIF | message | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/rpc.md:2110` |
| `DRIFT-MSGAPI-009` | DRIF | message | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/message/api.md:858` |
| `DRIFT-MSGAPI-011` | DRIF | message | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/message/api.md:1499;DRIFT/message/vs-common.md:39` |
| `FB-G2-03` | DRIF | message | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/message/feedback-to-g4.md:52` |
| `SD-04` | DRIF | message | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/proto.md:34` |
| `ADJ-01` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/_shared.md:965` |
| `D-015` | DRIF | users | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/api.md:1973` |
| `DRIFT-ADMINAPI-013` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/api.md:2448` |
| `DRIFT-ARPC-007` | DRIF | users | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:1203` |
| `DRIFT-ARPC-008` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:1302` |
| `DRIFT-ARPC-009` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:2947` |
| `DRIFT-ARPC-010` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | P2-T02,P2-T06 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:3063;DRIFT/users/admin/rpc.md:3169` |
| `DRIFT-ARPC-011` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:3118` |
| `DRIFT-ARPC-012` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:3195;DRIFT/users/admin/rpc.md:4100` |
| `DRIFT-ARPC-014` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/admin/rpc.md:4435` |
| `DRIFT-USERAPI-017` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/user/api.md:3635` |
| `DRIFT-USERRPC-V-001` | DRIF | users | 🔴 未闭合 | A 活体 · 混(0/1) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:473;DRIFT/users/user/rpc.md:1360` |
| `DRIFT-USERRPC-V-016` | DRIF | users | 🔴 未闭合 | A 活体 · 混(0/1) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2714` |
| `DRIFT-USERSCOMMON-006` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/common.md:788;DRIFT/users/common.md:869` |
| `Round-2` | DRIF | users | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/vs-common.md:5072` |
| `SD-2026-10-25-CAND-16` | Inte | users | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/users/_shared.md:18` |
| `SD-2026-10-25-CAND-17` | Inte | users | ⏳ 未签字 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `Intentional_Deviations/users/_shared.md:110` |
| `V-002` | DRIF | users | 🔴 未闭合 | B 静态 | P1 | — | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/users/user/rpc.md:995;DRIFT/users/vs-common.md:1578` |
| `V-004` | DRIF | users | 🔴 未闭合 | A 活体 · 绿(0) | P1 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:777` |
| `V-013` | DRIF | users | 🔴 未闭合 | C 无指针 | P1 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/rpc.md:2353` |
| `UNRESOLVED-MSG-V-001` | DRIF | _cross | 🟡 部分闭合 | A 活体 · 红(1) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/_cross/message-domain-wide.md:215;DRIFT/_cross/message-domain-wide.md:494` |
| `A-5` | DRIF | article | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:364` |
| `A-6` | DRIF | article | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:365` |
| `DRIFT-ARTICLE-API-006` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:703;DRIFT/global/vs-common.md:225;Intentional_Deviations/article/api.md:26` |
| `DRIFT-ARTICLE-RPC-007` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:518` |
| `G4-11` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:296` |
| `G4-12` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:297` |
| `G4-3` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:276;DRIFT/comment/vs-common.md:79;DRIFT/favorite/vs-common.md:110` |
| `G4-5` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:278;DRIFT/favorite/vs-common.md:113` |
| `G4-7` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:281` |
| `U-9` | DRIF | article | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:2510` |
| `U-9b` | DRIF | article | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:2523` |
| `C-4` | DRIF | comment | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | P7-T01 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:60;DRIFT/comment/vs-common.md:126;DRIFT/comment/vs-common.md:221` |
| `C-5` | DRIF | comment | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/comment/vs-common.md:61;DRIFT/comment/vs-common.md:222` |
| `G4-17` | DRIF | comment | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:97` |
| `PIT-GLOBAL-051` | DRIF | comment | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:29;Pitfalls/global/cross-domain.md:2014` |
| `DRIFT-003` | DRIF | common | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/protection.md:89;DRIFT/message/api.md:79;DRIFT/users/user/api.md:35` |
| `DRIFT-CF-018` | DRIF | common | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | P4-T03 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:661;DRIFT/common/alignment-2026-10-23.md:1050;DRIFT/global/vs-common.md:4646` |
| `T-7` | DRIF | common | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:694;DRIFT/common/alignment-2026-10-23.md:715;DRIFT/global/_README.md:93` |
| `F-6` | DRIF | favorite | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/vs-common.md:228` |
| `G4-14` | DRIF | favorite | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/vs-common.md:121` |
| `G4-15` | DRIF | favorite | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/vs-common.md:122` |
| `DRIFT-FOLLOW-007` | DRIF | follow | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:34;DRIFT/follow/api.md:589;DRIFT/follow/api.md:1182` |
| `CD-7` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/cross-domain.md:47;DRIFT/global/cross-domain.md:141` |
| `E34` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:383` |
| `E43` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:423;DRIFT/users/vs-common.md:1228` |
| `E67` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:432` |
| `FB-01` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:352;DRIFT/global/vs-common.md:1527;DRIFT/like/vs-common.md:80` |
| `FB-02` | DRIF | global | 🟡 部分闭合 | A 活体 · 混(0/1/2) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:373;DRIFT/global/vs-common.md:862;DRIFT/global/vs-common.md:1580` |
| `G4-A4-03` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3753;DRIFT/global/vs-common.md:3789` |
| `G4-A4-04` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/vs-common.md:3754;DRIFT/global/vs-common.md:3801` |
| `L4-02` | DRIF | global | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L4-flyway.md:173` |
| `L4-03` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/lanes/L4-flyway.md:174` |
| `T-16` | DRIF | global | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/common-tails.md:598` |
| `DRIFT-HOTAPI-025` | DRIF | hot | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1590` |
| `DRIFT-HOTAPI-026` | DRIF | hot | 🟡 部分闭合 | A 活体 · 红(1) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1672` |
| `DRIFT-HOTAPI-031` | DRIF | hot | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:2398` |
| `DRIFT-HOTRPC-005` | DRIF | hot | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:723;DRIFT/hot/rpc.md:990` |
| `G4-19` | DRIF | hot | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:92` |
| `H-4` | DRIF | hot | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:70` |
| `D-2` | DRIF | like | 待核实 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/like/vs-common.md:68;DRIFT/message/vs-common.md:70;DRIFT/task/vs-common.md:62` |
| `DRIFT-LIKE-018` | DRIF | like | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:898;DRIFT/like/rpc.md:1289` |
| `U-01` | DRIF | like | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | GAP-03 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:824;DRIFT/like/api.md:837` |
| `U-03` | DRIF | like | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:826;DRIFT/like/api.md:839` |
| `U-05` | DRIF | like | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:828;DRIFT/like/api.md:841` |
| `U-08` | DRIF | like | 🟡 部分闭合 | A 活体 · 绿(0) | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:831;DRIFT/like/api.md:844` |
| `DRIFT-MSG-V-032` | DRIF | message | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2801` |
| `DRIFT-MSGAPI-012` | DRIF | message | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/api.md:1532` |
| `FB-G2-04` | DRIF | message | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/feedback-to-g4.md:66` |
| `DRIFT-TASK-API-004` | DRIF | task | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:315;DRIFT/task/_README.md:355;DRIFT/task/api.md:510` |
| `DRIFT-ADMINAPI-012` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:2236` |
| `DRIFT-USERAPI-013` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | P2-T02,P2-T06 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:624;DRIFT/users/user/api.md:3283;DRIFT/users/user/api.md:3590` |
| `DRIFT-USERRPC-V-002` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:531;DRIFT/users/user/rpc.md:1706` |
| `U-2` | DRIF | users | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | GAP-04 | 无可复算指针 · 结论不可复算 | `DRIFT/users/admin/_shared.md:212;DRIFT/users/admin/rpc.md:1476` |
| `UNRESOLVED-ADMINAPI-005` | DRIF | users | 🟡 部分闭合 | C 无指针 | P2 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/admin/api.md:1005;DRIFT/users/admin/api.md:1588` |
| `UNRESOLVED-USERSCOMMON-002` | DRIF | users | 待核实 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:543` |
| `UNRESOLVED-USERSCOMMON-004` | DRIF | users | 🟡 部分闭合 | B 静态 | P2 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:545;DRIFT/users/common.md:871` |
| `DRIFT-MSG-V-001` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:26;DRIFT/_cross/message-domain-wide.md:148;DRIFT/_cross/message-domain-wide.md:161` |
| `DRIFT-MSG-V-002` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:27;DRIFT/_cross/message-domain-wide.md:149;DRIFT/_cross/message-domain-wide.md:184` |
| `DRIFT-MSG-V-003` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:28;DRIFT/_cross/message-domain-wide.md:150;DRIFT/_cross/message-domain-wide.md:397` |
| `DRIFT-MSG-V-004` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:29;DRIFT/_cross/message-domain-wide.md:151;DRIFT/_cross/message-domain-wide.md:267` |
| `DRIFT-MSG-V-005` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:30;DRIFT/_cross/message-domain-wide.md:152;DRIFT/_cross/message-domain-wide.md:268` |
| `DRIFT-MSG-V-006` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:31;DRIFT/_cross/message-domain-wide.md:153;DRIFT/_cross/message-domain-wide.md:206` |
| `DRIFT-MSG-V-007` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:32;DRIFT/_cross/message-domain-wide.md:154;DRIFT/_cross/message-domain-wide.md:316` |
| `DRIFT-MSG-V-008` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:33;DRIFT/_cross/message-domain-wide.md:155;DRIFT/_cross/message-domain-wide.md:270` |
| `DRIFT-MSG-V-009` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:34;DRIFT/_cross/message-domain-wide.md:156;DRIFT/_cross/message-domain-wide.md:271` |
| `DRIFT-MSG-V-010` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:35;DRIFT/_cross/message-domain-wide.md:157;DRIFT/_cross/message-domain-wide.md:272` |
| `DRIFT-MSG-V-011` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:49;DRIFT/_cross/message-domain-wide.md:282;DRIFT/_cross/message-domain-wide.md:491` |
| `DRIFT-MSG-V-012` | DRIF | _cross | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/_cross/message-domain-wide.md:50;DRIFT/_cross/message-domain-wide.md:283;DRIFT/_cross/message-domain-wide.md:492` |
| `DRIFT-MSG-V-013` | DRIF | _cross | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/_cross/message-domain-wide.md:51;DRIFT/_cross/message-domain-wide.md:284;DRIFT/_cross/message-domain-wide.md:493` |
| `UNRESOLVED-MSG-V-002` | DRIF | _cross | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/_cross/message-domain-wide.md:450` |
| `A-2` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | BLK-03 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:2551;DRIFT/article/vs-common.md:361;DRIFT/favorite/rpc.md:1662` |
| `DRIFT-002` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:895` |
| `DRIFT-004` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:897;DRIFT/users/_shared.md:143` |
| `DRIFT-006` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:899;DRIFT/common/http.md:2;DRIFT/follow/rpc.md:265` |
| `DRIFT-007` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:900` |
| `DRIFT-ARTICLE-API-002` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:601` |
| `DRIFT-ARTICLE-API-003` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:626` |
| `DRIFT-ARTICLE-API-004` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:646` |
| `DRIFT-ARTICLE-API-005` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:666;DRIFT/article/api.md:1815` |
| `DRIFT-ARTICLE-API-007` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:723` |
| `DRIFT-ARTICLE-API-008` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:738` |
| `DRIFT-ARTICLE-API-010` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:782` |
| `DRIFT-ARTICLE-API-011` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:813` |
| `DRIFT-ARTICLE-API-012` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:850` |
| `DRIFT-ARTICLE-RPC-001` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:363` |
| `DRIFT-ARTICLE-RPC-002` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:392` |
| `DRIFT-ARTICLE-RPC-003` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:415` |
| `DRIFT-ARTICLE-RPC-004` | DRIF | article | ⚫ 已签字偏离 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:440;DRIFT/article/rpc.md:903` |
| `DRIFT-ARTICLE-RPC-005` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:464` |
| `DRIFT-ARTICLE-RPC-006` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:496` |
| `DRIFT-ARTICLE-RPC-008` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:556` |
| `DRIFT-ARTICLE-RPC-009` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:575` |
| `DRIFT-ARTICLE-RPC-010` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:637` |
| `DRIFT-HOTAPI-030` | DRIF | article | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | P1-T01,P5-T03,P5-T04 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:26;DRIFT/comment/vs-common.md:31;DRIFT/follow/vs-common.md:28` |
| `DRIFT-MSG-V-030` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:2590;DRIFT/comment/api.md:366;DRIFT/follow/vs-common.md:1201` |
| `F-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1288;DRIFT/article/api.md:1299;DRIFT/favorite/vs-common.md:223` |
| `F-1b` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1289` |
| `F-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1290;DRIFT/article/api.md:1367;DRIFT/favorite/vs-common.md:224` |
| `F-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1292;DRIFT/article/api.md:1434;DRIFT/favorite/vs-common.md:226` |
| `FIX-01` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:766` |
| `FIX-02` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:789` |
| `FIX-03` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:842` |
| `FIX-04` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:1000` |
| `FIX-05` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1079;DRIFT/article/rpc.md:1139` |
| `FIX-07` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1239;DRIFT/article/rpc.md:1314` |
| `FIX-09` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:1726;DRIFT/article/api.md:1785` |
| `FIX-12` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1971` |
| `FIX-14` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:2141;DRIFT/article/api.md:2273;DRIFT/article/rpc.md:1546` |
| `FIX-15` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:1709;DRIFT/article/rpc.md:1830` |
| `FIX-16` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:1736` |
| `FIX-18` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:2321;DRIFT/article/api.md:2556` |
| `G4-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/vs-common.md:277;DRIFT/comment/vs-common.md:89;DRIFT/favorite/vs-common.md:111` |
| `L-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:70;DRIFT/favorite/vs-common.md:84` |
| `L-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:72` |
| `L-5` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/vs-common.md:74;DRIFT/favorite/vs-common.md:88` |
| `MUT-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2462;DRIFT/hot/rpc.md:1706` |
| `MUT-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2463;DRIFT/hot/rpc.md:1707` |
| `MUT-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2464` |
| `MUT-4` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/api.md:2465` |
| `O-1` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:338` |
| `O-2` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:339` |
| `O-3` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:340` |
| `O-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:341` |
| `O-6` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:343` |
| `OP-07` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:327;DRIFT/message/rpc.md:503` |
| `S-1` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:330` |
| `S-2` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:331` |
| `S-4` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:333` |
| `S-5b` | DRIF | article | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/article/rpc.md:335` |
| `S-6` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:336;DRIFT/article/vs-common.md:28;DRIFT/global/vs-common.md:235` |
| `S-7` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:337` |
| `U-9a` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/rpc.md:2512` |
| `UNRESOLVED-001` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:984;DRIFT/article/api.md:1620;DRIFT/message/api.md:1035` |
| `UNRESOLVED-002` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1001;DRIFT/message/api.md:1055;DRIFT/message/api.md:1344` |
| `UNRESOLVED-003` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1031;DRIFT/article/api.md:1564;DRIFT/message/api.md:1067` |
| `UNRESOLVED-004` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1050;DRIFT/article/api.md:1536;DRIFT/message/api.md:1078` |
| `UNRESOLVED-005` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1068;DRIFT/message/api.md:1097;DRIFT/users/user/api.md:2104` |
| `UNRESOLVED-006` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1081;DRIFT/article/api.md:1550` |
| `UNRESOLVED-007` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1098` |
| `UNRESOLVED-008` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1127` |
| `UNRESOLVED-009` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1162;DRIFT/article/api.md:1691` |
| `UNRESOLVED-010` | DRIF | article | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/article/api.md:1177` |
| `C-2` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | P2-T01,P3-T04 ｜ BLK-02 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:58;DRIFT/comment/vs-common.md:219` |
| `C-7` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:63` |
| `C-8` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:64` |
| `C-9` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:65` |
| `G4-1` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:77;DRIFT/favorite/vs-common.md:108;DRIFT/hot/vs-common.md:88` |
| `MISMATCH-01` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:80` |
| `MISMATCH-02` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:93` |
| `MISMATCH-03` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:106` |
| `MISMATCH-04` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:118` |
| `MISMATCH-05` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:130` |
| `MISMATCH-06` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:141` |
| `MISMATCH-07` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/rpc.md:154` |
| `MISMATCH-API-02` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/api.md:53;DRIFT/comment/api.md:107` |
| `MISMATCH-API-03` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/api.md:54;DRIFT/comment/api.md:120` |
| `SD-2026-10-24-CAND-08` | DRIF | comment | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/comment/vs-common.md:30;Intentional_Deviations/comment/api.md:172` |
| `DRIFT-COMMON-001` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:71;DRIFT/common/alignment-2026-10-23.md:1043` |
| `DRIFT-COMMON-002` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:104;DRIFT/common/alignment-2026-10-23.md:1044` |
| `DRIFT-COMMON-003` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:117;DRIFT/common/alignment-2026-10-23.md:1045` |
| `DRIFT-COMMON-004` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:134;DRIFT/common/alignment-2026-10-23.md:1046` |
| `DRIFT-COMMON-005` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:169;DRIFT/common/alignment-2026-10-23.md:589;DRIFT/common/alignment-2026-10-23.md:1047` |
| `DRIFT-COMMON-006` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:200;DRIFT/common/alignment-2026-10-23.md:1048` |
| `T-1` | DRIF | common | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:688;DRIFT/common/alignment-2026-10-23.md:709;DRIFT/global/_README.md:87` |
| `T-4` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:691;DRIFT/common/alignment-2026-10-23.md:712;DRIFT/global/_README.md:90` |
| `T-5` | DRIF | common | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:692;DRIFT/common/alignment-2026-10-23.md:713;DRIFT/global/_README.md:91` |
| `T-8` | DRIF | common | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/common/alignment-2026-10-23.md:695;DRIFT/common/alignment-2026-10-23.md:716;DRIFT/global/_README.md:94` |
| `T-9` | DRIF | common | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/common/alignment-2026-10-23.md:696;DRIFT/common/alignment-2026-10-23.md:717;DRIFT/global/_README.md:95` |
| `B-1` | DRIF | favorite | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/rpc.md:1497;DRIFT/favorite/rpc.md:1544;DRIFT/users/user/rpc.md:2133` |
| `E21` | DRIF | favorite | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/vs-common.md:27;DRIFT/global/_README.md:369;DRIFT/global/vs-common.md:27` |
| `L-2` | DRIF | favorite | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/vs-common.md:85` |
| `L-9` | DRIF | favorite | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/favorite/vs-common.md:95` |
| `DRIFT-FOLLOW-001` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:32;DRIFT/follow/api.md:51;DRIFT/follow/api.md:1178` |
| `DRIFT-FOLLOW-002` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:17;DRIFT/follow/rpc.md:25;DRIFT/follow/rpc.md:693` |
| `DRIFT-FOLLOW-003` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:18;DRIFT/follow/rpc.md:79;DRIFT/follow/rpc.md:694` |
| `DRIFT-FOLLOW-004` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:19;DRIFT/follow/rpc.md:143;DRIFT/follow/rpc.md:696` |
| `DRIFT-FOLLOW-005` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:33;DRIFT/follow/api.md:157;DRIFT/follow/api.md:1180` |
| `DRIFT-FOLLOW-006` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/rpc.md:20;DRIFT/follow/rpc.md:213;DRIFT/follow/rpc.md:351` |
| `DRIFT-FOLLOW-008` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | BLK-03 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:35;DRIFT/follow/api.md:528;DRIFT/follow/api.md:1183` |
| `DRIFT-FOLLOW-009` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:36;DRIFT/follow/api.md:239;DRIFT/follow/api.md:1185` |
| `DRIFT-FOLLOW-010` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:37;DRIFT/follow/api.md:287;DRIFT/follow/api.md:875` |
| `DRIFT-FOLLOW-011` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:38;DRIFT/follow/api.md:338;DRIFT/follow/api.md:1187` |
| `DRIFT-FOLLOW-012` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:39;DRIFT/follow/api.md:410;DRIFT/follow/api.md:1003` |
| `DRIFT-FOLLOW-013` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:1074;DRIFT/follow/rpc.md:659;DRIFT/follow/vs-common.md:726` |
| `DRIFT-G1-001` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/vs-common.md:174;DRIFT/follow/vs-common.md:1213;DRIFT/follow/vs-common.md:1270` |
| `DRIFT-G1-002` | DRIF | follow | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/vs-common.md:200;DRIFT/follow/vs-common.md:1214;DRIFT/follow/vs-common.md:1271` |
| `OPEN-001` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/api.md:199;DRIFT/follow/vs-common.md:1202` |
| `S1-FOLLOW-005` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 是 | 台账已写 · ① 阶段 1 已修（`${FOLLOW_RPC_PORT:8082}`）；② 阶段 2 把 6 个组件端口**统一**为 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/stage1-admission.md:162;DRIFT/follow/stage1-admission.md:538;DRIFT/follow/vs-common.md:1211` |
| `S1-FOLLOW-006` | DRIF | follow | ✅ 已一致 | B 静态 | P3 | — | 是 | 台账已写 · ✅ **无**；⚠️ **反向使用**：本条是 users 域 `S1-USERS-004`（头像链路事务依赖跨 Bea | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/follow/stage1-admission.md:179` |
| `CD-1` | DRIF | global | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | P5-T03 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:41;DRIFT/global/cross-domain.md:629` |
| `CD-10` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | P5-T03 | 无可复算指针 · 结论不可复算 | `DRIFT/global/cross-domain.md:50;DRIFT/global/cross-domain.md:187` |
| `CD-2` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:42;DRIFT/global/cross-domain.md:336` |
| `CD-3` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/cross-domain.md:43;DRIFT/global/cross-domain.md:419` |
| `CD-4` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:44;DRIFT/global/cross-domain.md:386` |
| `CD-5` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:45;DRIFT/global/cross-domain.md:203;DRIFT/global/cross-domain.md:916` |
| `CD-6` | DRIF | global | ✅ 已一致 | A 活体 · 环境(2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:137;DRIFT/global/cross-domain.md:46;DRIFT/global/cross-domain.md:435` |
| `CD-8` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/cross-domain.md:48` |
| `CD-9` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cross-domain.md:49;DRIFT/global/cross-domain.md:89;DRIFT/global/cross-domain.md:720` |
| `CYCLE-002` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/cycle-deps.md:96` |
| `E11` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:350` |
| `E12` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:351` |
| `E13` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:352` |
| `E14` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:353` |
| `E15` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:354` |
| `E16` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:355` |
| `E17` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:356` |
| `E19` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:357` |
| `E27` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:375` |
| `E28` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:376` |
| `E29` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:377` |
| `E33` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:382` |
| `E35` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:384` |
| `E37` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:387` |
| `E38` | DRIF | global | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:388;DRIFT/users/user/rpc.md:832` |
| `E39` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:386` |
| `E42` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:420` |
| `E45` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/_README.md:422` |
| `E49` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:436` |
| `E54` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:442` |
| `E57` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:445` |
| `E8` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/_README.md:339` |
| `FB-04` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:910;DRIFT/like/vs-common.md:179;DRIFT/like/vs-common.md:196` |
| `FB-05` | DRIF | global | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | P2-T02,P2-T06 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:1014;DRIFT/like/vs-common.md:109` |
| `T-11` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/common-tails.md:498;DRIFT/global/common-tails.md:641;DRIFT/global/lanes/L1-delivery.md:214` |
| `T-14` | DRIF | global | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/common-tails.md:565` |
| `T-15` | DRIF | global | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/common-tails.md:576;DRIFT/global/common-tails.md:587` |
| `DRIFT-HOTAPI-011` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:535;DRIFT/hot/api.md:912;DRIFT/hot/api.md:920` |
| `DRIFT-HOTAPI-012` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:575` |
| `DRIFT-HOTAPI-013` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:611;DRIFT/hot/api.md:914;DRIFT/hot/api.md:977` |
| `DRIFT-HOTAPI-014` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:641;DRIFT/hot/api.md:915;DRIFT/hot/api.md:1003` |
| `DRIFT-HOTAPI-015` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:675;DRIFT/hot/api.md:916;DRIFT/hot/api.md:952` |
| `DRIFT-HOTAPI-016` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1073` |
| `DRIFT-HOTAPI-017` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1136` |
| `DRIFT-HOTAPI-018` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1167` |
| `DRIFT-HOTAPI-019` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1212` |
| `DRIFT-HOTAPI-020` | DRIF | hot | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1231` |
| `DRIFT-HOTAPI-021` | DRIF | hot | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1342` |
| `DRIFT-HOTAPI-022` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1391` |
| `DRIFT-HOTAPI-023` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1437` |
| `DRIFT-HOTAPI-024` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1481;DRIFT/hot/vs-common.md:27` |
| `DRIFT-HOTAPI-028` | DRIF | hot | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1930` |
| `DRIFT-HOTAPI-029` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:2092` |
| `DRIFT-HOTRPC-001` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:582;DRIFT/hot/rpc.md:986;DRIFT/hot/rpc.md:996` |
| `DRIFT-HOTRPC-002` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:621;DRIFT/hot/rpc.md:987` |
| `DRIFT-HOTRPC-003` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:663;DRIFT/hot/rpc.md:988;DRIFT/hot/rpc.md:1030` |
| `DRIFT-HOTRPC-004` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:690;DRIFT/hot/rpc.md:1049` |
| `DRIFT-HOTRPC-006` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:744;DRIFT/hot/rpc.md:1089` |
| `DRIFT-HOTRPC-007` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:772;DRIFT/hot/rpc.md:992` |
| `DRIFT-HOTRPC-008` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:1176` |
| `DRIFT-HOTRPC-009` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:1198` |
| `DRIFT-HOTRPC-010` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:1222` |
| `G-5` | DRIF | hot | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/common.md:16` |
| `G4-18` | DRIF | hot | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:91` |
| `H-2` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:68;DRIFT/users/admin/rpc.md:963` |
| `H-3` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:69;DRIFT/users/admin/rpc.md:964` |
| `H-5` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:71` |
| `H-6` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:73` |
| `H-7` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/vs-common.md:74` |
| `H-8` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:75` |
| `H-9` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/vs-common.md:76` |
| `M-redis-1` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:1168` |
| `M-redis-2` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:1169` |
| `U5-OPEN-1` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/api.md:1780;DRIFT/hot/api.md:2104;DRIFT/hot/api.md:2125` |
| `UNR-1` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:850;DRIFT/hot/rpc.md:1413` |
| `UNR-2` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:851` |
| `UNR-3` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:852` |
| `UNR-4` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:853` |
| `UNR-5` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:854` |
| `UNR-6` | DRIF | hot | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/hot/rpc.md:855` |
| `UNR-7` | DRIF | hot | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/hot/rpc.md:856` |
| `D-4` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:70;DRIFT/message/vs-common.md:72` |
| `D-5` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:71` |
| `D-6` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:73;DRIFT/task/vs-common.md:66` |
| `D-7` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/vs-common.md:72;DRIFT/task/vs-common.md:68` |
| `DRIFT-LIKE-001` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:82` |
| `DRIFT-LIKE-002` | DRIF | like | ⚫ 已签字偏离 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:234` |
| `DRIFT-LIKE-004` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:235;DRIFT/like/rpc.md:1169` |
| `DRIFT-LIKE-005` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:236;DRIFT/like/rpc.md:1205` |
| `DRIFT-LIKE-006` | DRIF | like | ✅ 已一致 | A 活体 · 环境(2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:237;DRIFT/like/vs-common.md:37` |
| `DRIFT-LIKE-007` | DRIF | like | ⚫ 已签字偏离 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:238` |
| `DRIFT-LIKE-008` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:239;DRIFT/like/rpc.md:1239` |
| `DRIFT-LIKE-009` | DRIF | like | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/like/rpc.md:240;DRIFT/like/rpc.md:1251` |
| `DRIFT-LIKE-010` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:1264` |
| `DRIFT-LIKE-011` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:1271` |
| `DRIFT-LIKE-012` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:1279` |
| `DRIFT-LIKE-013` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:802` |
| `DRIFT-LIKE-014` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:823` |
| `DRIFT-LIKE-015` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:841` |
| `DRIFT-LIKE-016` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:861` |
| `DRIFT-LIKE-017` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/rpc.md:879;DRIFT/like/rpc.md:1058` |
| `DRIFT-LIKEAPI-001` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:382` |
| `DRIFT-LIKEAPI-002` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:383` |
| `DRIFT-LIKEAPI-010` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:387` |
| `DRIFT-LIKEAPI-011` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:391` |
| `DRIFT-LIKEAPI-012` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:403;DRIFT/like/api.md:1013` |
| `DRIFT-LIKEAPI-013` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:424;DRIFT/like/api.md:954` |
| `DRIFT-LIKEAPI-014` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:444;DRIFT/like/api.md:978` |
| `DRIFT-LIKEAPI-015` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:464;DRIFT/like/api.md:1012` |
| `DRIFT-LIKEAPI-016` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:484;DRIFT/like/api.md:1014` |
| `DRIFT-LIKEAPI-017` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:504;DRIFT/like/api.md:1015;DRIFT/like/vs-common.md:36` |
| `DRIFT-LIKEAPI-018` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:524` |
| `DRIFT-LIKEAPI-019` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:544` |
| `DRIFT-LIKEAPI-020` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:563` |
| `DRIFT-LIKEAPI-023` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:630` |
| `G-14` | DRIF | like | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:800` |
| `U-02` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:825;DRIFT/like/api.md:838` |
| `U-04` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:827;DRIFT/like/api.md:840` |
| `U-07` | DRIF | like | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/like/api.md:830;DRIFT/like/api.md:843` |
| `D-3` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/vs-common.md:71` |
| `DRIFT-MSG-001` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:35;DRIFT/message/rpc.md:132` |
| `DRIFT-MSG-002` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:36;DRIFT/message/rpc.md:133;DRIFT/message/rpc.md:191` |
| `DRIFT-MSG-003` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:37;DRIFT/message/rpc.md:134;DRIFT/message/rpc.md:218` |
| `DRIFT-MSG-004` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:143;DRIFT/message/rpc.md:254` |
| `DRIFT-MSG-005` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:144;DRIFT/message/rpc.md:255` |
| `DRIFT-MSG-006` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:256` |
| `DRIFT-MSG-007` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:257` |
| `DRIFT-MSG-008` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:258` |
| `DRIFT-MSG-009` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:259` |
| `DRIFT-MSG-010` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:260` |
| `DRIFT-MSG-011` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:261` |
| `DRIFT-MSG-V-014` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:642` |
| `DRIFT-MSG-V-015` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:689` |
| `DRIFT-MSG-V-016` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:721;DRIFT/message/rpc.md:1745;DRIFT/message/rpc.md:1762` |
| `DRIFT-MSG-V-017` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1059` |
| `DRIFT-MSG-V-018` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1103;DRIFT/message/rpc.md:2316` |
| `DRIFT-MSG-V-019` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1138` |
| `DRIFT-MSG-V-021` | DRIF | message | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1509` |
| `DRIFT-MSG-V-022` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/rpc.md:1533` |
| `DRIFT-MSG-V-024` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:805;DRIFT/message/rpc.md:2054;DRIFT/message/rpc.md:2187` |
| `DRIFT-MSG-V-025` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:854;DRIFT/message/rpc.md:2055;DRIFT/message/rpc.md:2360` |
| `DRIFT-MSG-V-026` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1008;DRIFT/message/rpc.md:2056;DRIFT/message/rpc.md:2407` |
| `DRIFT-MSG-V-028` | DRIF | message | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:1713;DRIFT/message/rpc.md:2163` |
| `DRIFT-MSG-V-029` | DRIF | message | ✅ 已一致 | A 活体 · 环境(2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:882;DRIFT/message/vs-common.md:41` |
| `DRIFT-MSG-V-031` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2663` |
| `DRIFT-MSGAPI-001` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:48;DRIFT/message/api.md:692` |
| `DRIFT-MSGAPI-002` | DRIF | message | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:49` |
| `DRIFT-MSGAPI-004` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:240` |
| `DRIFT-MSGAPI-005` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:712` |
| `DRIFT-MSGAPI-006` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:757` |
| `DRIFT-MSGAPI-007` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:790` |
| `DRIFT-MSGAPI-008` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:818` |
| `DRIFT-MSGAPI-010` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:912;DRIFT/message/api.md:1664;DRIFT/message/vs-common.md:38` |
| `DRIFT-MSGAPI-013` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:1543;DRIFT/message/vs-common.md:40` |
| `DRIFT-MSGAPI-014` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/api.md:1590` |
| `FB-G2-01` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/feedback-to-g4.md:22` |
| `FB-G2-02` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/feedback-to-g4.md:38` |
| `FIX-MSG-014` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1320` |
| `FIX-MSG-017` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1359` |
| `FIX-MSG-COMMON-001` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/common.md:63` |
| `FIX-MSG-COMMON-002` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/common.md:118` |
| `FIX-MSG-COMMON-003` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/common.md:134` |
| `FIX-MSG-COMMON-004` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/common.md:144` |
| `FIX-MSG-V-015` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1431` |
| `FIX-MSG-V-019` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1452` |
| `FIX-MSG-V-023` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1556` |
| `OP-01` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:497` |
| `OP-02` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:498` |
| `OP-03` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:499` |
| `OP-04` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:500` |
| `OP-05` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:501` |
| `OP-06` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:502` |
| `OP-08` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:504` |
| `SD-2026-10-24-03` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2716;Intentional_Deviations/message/rpc.md:73` |
| `UNRESOLVED-MSG-003` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/rpc.md:2209` |
| `UNRESOLVED-MSG-004` | DRIF | message | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2507` |
| `UNRESOLVED-MSG-006` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1945` |
| `UNRESOLVED-MSG-009` | DRIF | message | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/message/rpc.md:2698` |
| `UNRESOLVED-MSG-V-003` | DRIF | message | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:2584;DRIFT/message/rpc.md:2689` |
| `V-020` | DRIF | message | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/message/rpc.md:1474` |
| `DRIFT-TASK-API-001` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:312;DRIFT/task/_README.md:352;DRIFT/task/api.md:64` |
| `DRIFT-TASK-API-002` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:313;DRIFT/task/_README.md:353;DRIFT/task/api.md:100` |
| `DRIFT-TASK-API-003` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:43;DRIFT/task/_README.md:314;DRIFT/task/_README.md:354` |
| `DRIFT-TASK-RPC-001` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:317;DRIFT/task/_README.md:357;DRIFT/task/rpc.md:98` |
| `DRIFT-TASK-RPC-002` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:318;DRIFT/task/_README.md:358;DRIFT/task/rpc.md:154` |
| `DRIFT-TASK-RPC-003` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:319;DRIFT/task/_README.md:359;DRIFT/task/rpc.md:204` |
| `DRIFT-TASK-RPC-004` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:320;DRIFT/task/_README.md:360;DRIFT/task/rpc.md:224` |
| `DRIFT-TASK-RPC-005` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:321;DRIFT/task/_README.md:361;DRIFT/task/rpc.md:322` |
| `DRIFT-TASK-RPC-006` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:322;DRIFT/task/_README.md:362;DRIFT/task/rpc.md:349` |
| `DRIFT-TASK-RPC-007` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:323;DRIFT/task/_README.md:363;DRIFT/task/rpc.md:408` |
| `DRIFT-TASK-RPC-008` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:324;DRIFT/task/_README.md:364;DRIFT/task/rpc.md:1545` |
| `FB-06` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | P2-T02,P2-T06 | 未定级 ⇒ 未排期 | `DRIFT/task/vs-common.md:148` |
| `SD-2026-10-23-TASK-01` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:325;DRIFT/task/_README.md:365;Intentional_Deviations/task/rpc.md:13` |
| `SD-2026-10-24-TASK-01` | DRIF | task | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/task/_README.md:326;DRIFT/task/_README.md:366;Intentional_Deviations/task/rpc.md:115` |
| `ADJ-03` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/_shared.md:1005` |
| `ADJ-04` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:1023` |
| `ARPC-002` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:2114;DRIFT/users/admin/rpc.md:2148;DRIFT/users/admin/rpc.md:2455` |
| `D-007` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/api.md:1966` |
| `D-009` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/api.md:1968` |
| `D-010` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/api.md:1969` |
| `DRIFT-ADMINAPI-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:439;DRIFT/users/admin/api.md:1401` |
| `DRIFT-ADMINAPI-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:486` |
| `DRIFT-ADMINAPI-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:520;DRIFT/users/admin/api.md:1728` |
| `DRIFT-ADMINAPI-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:575;DRIFT/users/admin/api.md:1274;DRIFT/users/admin/api.md:1376` |
| `DRIFT-ADMINAPI-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:604` |
| `DRIFT-ADMINAPI-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:646;DRIFT/users/admin/api.md:2007` |
| `DRIFT-ADMINAPI-007` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:710;DRIFT/users/vs-common.md:50` |
| `DRIFT-ADMINAPI-008` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:806` |
| `DRIFT-ADMINAPI-009` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:854;DRIFT/users/admin/api.md:1012;DRIFT/users/admin/api.md:1639` |
| `DRIFT-ADMINAPI-010` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:874` |
| `DRIFT-ADMINAPI-011` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:1148` |
| `DRIFT-ARPC-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:336` |
| `DRIFT-ARPC-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:425;DRIFT/users/admin/rpc.md:1567` |
| `DRIFT-ARPC-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:473` |
| `DRIFT-ARPC-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:504;DRIFT/users/admin/rpc.md:1500` |
| `DRIFT-ARPC-005` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:566;DRIFT/users/admin/rpc.md:1608` |
| `DRIFT-ARPC-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:598;DRIFT/users/admin/rpc.md:2213;DRIFT/users/vs-common.md:35` |
| `DRIFT-ARPC-013` | DRIF | users | ✅ 已一致 | A 活体 · 混(0/1/2) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:4274;DRIFT/users/vs-common.md:3197` |
| `DRIFT-FRAMEWORK-001` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/_shared.md:76` |
| `DRIFT-NEW-014` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:295` |
| `DRIFT-NEW-015` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:297;DRIFT/users/_shared.md:441` |
| `DRIFT-NEW-016` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:299;DRIFT/users/_shared.md:360` |
| `DRIFT-USERAPI-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:951;DRIFT/users/user/api.md:612;DRIFT/users/user/api.md:632` |
| `DRIFT-USERAPI-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:952;DRIFT/users/user/api.md:613;DRIFT/users/user/api.md:700` |
| `DRIFT-USERAPI-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:614;DRIFT/users/user/api.md:742;DRIFT/users/user/api.md:2586` |
| `DRIFT-USERAPI-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:787` |
| `DRIFT-USERAPI-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:616;DRIFT/users/user/api.md:906;DRIFT/users/user/api.md:2689` |
| `DRIFT-USERAPI-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:617;DRIFT/users/user/api.md:939;DRIFT/users/user/api.md:2948` |
| `DRIFT-USERAPI-007` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:618;DRIFT/users/user/api.md:997;DRIFT/users/user/api.md:2784` |
| `DRIFT-USERAPI-008` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:1040;DRIFT/users/user/api.md:3154` |
| `DRIFT-USERAPI-009` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:620;DRIFT/users/user/api.md:1132;DRIFT/users/user/api.md:1235` |
| `DRIFT-USERAPI-010` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:621;DRIFT/users/user/api.md:1533` |
| `DRIFT-USERAPI-011` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:622;DRIFT/users/user/api.md:2986` |
| `DRIFT-USERAPI-012` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:623` |
| `DRIFT-USERAPI-014` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:625;DRIFT/users/user/api.md:1574;DRIFT/users/user/api.md:3466` |
| `DRIFT-USERAPI-015` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:626;DRIFT/users/user/api.md:1638;DRIFT/users/user/api.md:2193` |
| `DRIFT-USERAPI-016` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/api.md:1791` |
| `DRIFT-USERRPC-V-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:575;DRIFT/users/user/rpc.md:1707` |
| `DRIFT-USERRPC-V-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:665;DRIFT/users/user/rpc.md:1480` |
| `DRIFT-USERRPC-V-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:915;DRIFT/users/_shared.md:953;DRIFT/users/user/rpc.md:720` |
| `DRIFT-USERRPC-V-006` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:744;DRIFT/users/user/rpc.md:1709;DRIFT/users/user/rpc.md:1789` |
| `DRIFT-USERRPC-V-007` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:775;DRIFT/users/user/rpc.md:1710;DRIFT/users/vs-common.md:37` |
| `DRIFT-USERRPC-V-008` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:889;DRIFT/users/user/rpc.md:1711;DRIFT/users/user/rpc.md:1834` |
| `DRIFT-USERRPC-V-009` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1515` |
| `DRIFT-USERRPC-V-010` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1541` |
| `DRIFT-USERRPC-V-012` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2018;DRIFT/users/user/rpc.md:2085;DRIFT/users/vs-common.md:792` |
| `DRIFT-USERRPC-V-013` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2222` |
| `DRIFT-USERRPC-V-014` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2365;DRIFT/users/vs-common.md:3195;DRIFT/users/vs-common.md:3196` |
| `DRIFT-USERRPC-V-015` | DRIF | users | ✅ 已一致 | A 活体 · 混(0/1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:2658;DRIFT/users/vs-common.md:4123` |
| `DRIFT-USERSCOMMON-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:77;DRIFT/users/common.md:864` |
| `DRIFT-USERSCOMMON-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:179;DRIFT/users/common.md:449;DRIFT/users/common.md:865` |
| `DRIFT-USERSCOMMON-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:210;DRIFT/users/common.md:866` |
| `DRIFT-USERSCOMMON-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:234;DRIFT/users/common.md:434;DRIFT/users/common.md:867` |
| `DRIFT-USERSCOMMON-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:298;DRIFT/users/common.md:868;DRIFT/users/user/api.md:2951` |
| `E-1` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:590` |
| `E-2` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:591` |
| `E-3` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:159;DRIFT/users/_shared.md:592` |
| `E-4` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:593;DRIFT/users/_shared.md:597` |
| `E-5` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:594` |
| `E-6` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:595` |
| `ENC-01` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:389` |
| `FIX-ARPC-001` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:1079` |
| `G-4` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:688` |
| `OP-10` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:188` |
| `PAD-01` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:352;DRIFT/users/common.md:460` |
| `U-7` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/_shared.md:221` |
| `U-8` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/admin/_shared.md:222` |
| `UNRESOLVED-ADMINAPI-001` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:945;DRIFT/users/admin/api.md:1237` |
| `UNRESOLVED-ADMINAPI-002` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:959;DRIFT/users/admin/api.md:1489` |
| `UNRESOLVED-ADMINAPI-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:981;DRIFT/users/admin/api.md:1465` |
| `UNRESOLVED-ADMINAPI-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:992;DRIFT/users/admin/api.md:1518` |
| `UNRESOLVED-ADMINAPI-006` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/api.md:1017;DRIFT/users/admin/api.md:1610` |
| `UNRESOLVED-ARPC-001` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:671;DRIFT/users/admin/rpc.md:1909` |
| `UNRESOLVED-ARPC-002` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:711;DRIFT/users/admin/rpc.md:1953;DRIFT/users/admin/rpc.md:2674` |
| `UNRESOLVED-ARPC-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:794;DRIFT/users/admin/rpc.md:2063;DRIFT/users/admin/rpc.md:2174` |
| `UNRESOLVED-ARPC-004` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:835;DRIFT/users/admin/rpc.md:1379` |
| `UNRESOLVED-ARPC-005` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:852;DRIFT/users/admin/rpc.md:2320` |
| `UNRESOLVED-USERRPC-004` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1223` |
| `UNRESOLVED-USERRPC-005` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1138;DRIFT/users/user/rpc.md:1715;DRIFT/users/user/rpc.md:1907` |
| `UNRESOLVED-USERRPC-006` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/_shared.md:855;DRIFT/users/_shared.md:954;DRIFT/users/user/rpc.md:1651` |
| `UNRESOLVED-USERRPC-007` | DRIF | users | ✅ 已一致 | A 活体 · 红(1) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1908` |
| `UNRESOLVED-USERSCOMMON-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/vs-common.md:46;DRIFT/users/vs-common.md:611` |
| `UNRESOLVED-USERSCOMMON-005` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/common.md:635;DRIFT/users/common.md:870` |
| `V-003` | DRIF | users | ✅ 已一致 | B 静态 | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/user/rpc.md:1717` |
| `V-007` | DRIF | users | ✅ 已一致 | C 无指针 | P3 | — | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/users/user/rpc.md:1000` |
| `V-012` | DRIF | users | ✅ 已一致 | A 活体 · 绿(0) | P3 | — | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/users/admin/rpc.md:3182;DRIFT/users/user/rpc.md:1003` |
| `PIT-LANE-01` | Pitf | _cross | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/_cross/domain-common-lanes.md:17` |
| `PIT-LANE-02` | Pitf | _cross | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:63` |
| `PIT-LANE-03` | Pitf | _cross | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/_cross/domain-common-lanes.md:97` |
| `PIT-LANE-04` | Pitf | _cross | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:134` |
| `P-1` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:11` |
| `P-10` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:386;Pitfalls/like/rpc.md:218` |
| `P-11` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:416;Pitfalls/like/rpc.md:257` |
| `P-12` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:449` |
| `P-13` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:483` |
| `P-14` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:540` |
| `P-15` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:586` |
| `P-16` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:622` |
| `P-17` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:673` |
| `P-19` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:796` |
| `P-20` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:833` |
| `P-21` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:889` |
| `P-3` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:112` |
| `P-6` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:226` |
| `P-7` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:257` |
| `P-8` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:301` |
| `P-9` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/api.md:342` |
| `PF-ARTICLE-RPC-005` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:203` |
| `PF-ARTICLE-RPC-006` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:241` |
| `PF-ARTICLE-RPC-007` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:274` |
| `PF-ARTICLE-RPC-008` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:325` |
| `PF-ARTICLE-RPC-009` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:385` |
| `PF-ARTICLE-RPC-012` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/rpc.md:620` |
| `PF-ARTICLE-RPC-013` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:700` |
| `PF-ARTICLE-RPC-014` | Pitf | article | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/article/rpc.md:801` |
| `PF-ARTICLE-RPC-015` | Pitf | article | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:882` |
| `PF-ARTICLE-RPC-016` | DRIF | article | 🔴 未闭合 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/article/rpc.md:2072;Pitfalls/article/rpc.md:950` |
| `PIT-COMMENT-API-01` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/api.md:11` |
| `PIT-COMMENT-API-02` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/api.md:71` |
| `PIT-COMMENT-API-03` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/api.md:111` |
| `PIT-COMMENT-RPC-01` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:14` |
| `PIT-COMMENT-RPC-02` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:76` |
| `PIT-COMMENT-RPC-04` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:179` |
| `PIT-COMMENT-RPC-05` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:216` |
| `PIT-COMMENT-RPC-06` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:285` |
| `PIT-COMMENT-RPC-09` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:426` |
| `PIT-COMMENT-RPC-10` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:463` |
| `PIT-COMMENT-RPC-13` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:635` |
| `PIT-COMMENT-RPC-14` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:694` |
| `PIT-COMMENT-RPC-15` | Pitf | comment | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:760` |
| `PIT-COMMENT-RPC-16` | Pitf | comment | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/comment/rpc.md:802` |
| `PIT-COMMON-01` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/audit.md:23` |
| `PIT-COMMON-03` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/audit.md:99` |
| `PIT-COMMON-04` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/deadline.md:11` |
| `PIT-COMMON-05` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/deadline.md:77` |
| `PIT-COMMON-06` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/deadline.md:99` |
| `PIT-COMMON-08` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/http.md:2` |
| `PIT-COMMON-09` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/observability.md:11` |
| `PIT-COMMON-10` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/observability.md:68` |
| `PIT-COMMON-11` | Pitf | common | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/observability.md:114` |
| `PIT-COMMON-12` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/observability.md:153` |
| `PIT-COMMON-13` | Pitf | common | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/protection.md:10` |
| `PIT-FAV-API-001` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:11` |
| `PIT-FAV-API-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:57` |
| `PIT-FAV-API-003` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:95` |
| `PIT-FAV-API-004` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:129` |
| `PIT-FAV-API-005` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:162` |
| `PIT-FAV-API-006` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:188` |
| `PIT-FAV-API-007` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:245` |
| `PIT-FAV-API-010` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:460` |
| `PIT-FAV-API-011` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:520` |
| `PIT-FAV-API-012` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:578` |
| `PIT-FAV-API-013` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:633` |
| `PIT-FAV-API-014` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:685` |
| `PIT-FAV-API-015` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/api.md:759` |
| `PIT-FAV-API-016` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:838` |
| `PIT-FAV-API-017` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:884` |
| `PIT-FAV-COMMON-001` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/common.md:11` |
| `PIT-FAV-COMMON-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/common.md:51` |
| `PIT-FAV-PROTO-001` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/proto.md:10` |
| `PIT-FAV-PROTO-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/proto.md:54` |
| `PIT-FAV-RPC-001` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:10` |
| `PIT-FAV-RPC-002` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:70` |
| `PIT-FAV-RPC-003` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:103` |
| `PIT-FAV-RPC-004` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:128` |
| `PIT-FAV-RPC-005` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:174` |
| `PIT-FAV-RPC-006` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:222` |
| `PIT-FAV-RPC-007` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:267` |
| `PIT-FAV-RPC-008` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:307` |
| `PIT-FAV-RPC-009` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:353` |
| `PIT-FAV-RPC-010` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:413` |
| `PIT-FAV-RPC-011` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:470` |
| `PIT-FAV-RPC-012` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:518` |
| `PIT-FAV-RPC-014` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:628` |
| `PIT-FAV-RPC-015` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:670` |
| `PIT-FAV-RPC-016` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:709` |
| `PIT-FAV-RPC-017` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:745` |
| `PIT-FAV-RPC-018` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:801` |
| `PIT-FAV-RPC-019` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:855` |
| `PIT-FAV-RPC-020` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:894` |
| `PIT-FAV-RPC-021` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:947` |
| `PIT-FAV-RPC-022` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:996` |
| `PIT-FAV-RPC-023` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1039` |
| `PIT-FAV-RPC-024` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1085` |
| `PIT-FAV-RPC-025` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1129` |
| `PIT-FAV-RPC-026` | Pitf | favorite | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1181` |
| `PIT-FAV-RPC-027` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1259` |
| `PIT-FAV-RPC-028` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1324` |
| `PIT-FAV-RPC-029` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1390` |
| `PIT-FAV-RPC-030` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1435` |
| `PIT-FAV-RPC-031` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1494` |
| `PIT-FAV-RPC-032` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1556` |
| `PIT-FAV-RPC-033` | DRIF | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:3095;Pitfalls/favorite/rpc.md:1594` |
| `PIT-FAV-RPC-035` | DRIF | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/favorite/rpc.md:3277;Pitfalls/favorite/rpc.md:1718` |
| `PIT-FAV-RPC-036` | DRIF | favorite | 🔴 未闭合 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 静态取证 · 缺活体对拍 | `DRIFT/favorite/rpc.md:3365;DRIFT/favorite/rpc.md:3459;Pitfalls/favorite/rpc.md:1778` |
| `PIT-FAV-RPC-037` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1832` |
| `PIT-FAV-RPC-039` | Pitf | favorite | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1960` |
| `PIT-FAV-RPC-040` | Pitf | favorite | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/favorite/rpc.md:1996` |
| `PIT-FOLLOW-011` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:261;Pitfalls/follow/api.md:481;Pitfalls/follow/api.md:509` |
| `PIT-FOLLOW-012` | Pitf | follow | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:307;Pitfalls/follow/api.md:482;Pitfalls/follow/api.md:510` |
| `PIT-FOLLOW-013` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:190;Pitfalls/follow/rpc.md:291;Pitfalls/follow/rpc.md:316` |
| `PIT-FOLLOW-014` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:237;Pitfalls/follow/rpc.md:292;Pitfalls/follow/rpc.md:317` |
| `PIT-FOLLOW-015` | Pitf | follow | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:358;Pitfalls/follow/api.md:483;Pitfalls/follow/api.md:511` |
| `PIT-FOLLOW-016` | Pitf | follow | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:412;Pitfalls/follow/api.md:484;Pitfalls/follow/api.md:512` |
| `PIT-FAV-RPC-038` | DRIF | global | 🔴 未闭合 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 未定级 ⇒ 未排期 | `DRIFT/global/vs-common.md:2709;Pitfalls/favorite/rpc.md:1908` |
| `PIT-GLOBAL-001` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:8` |
| `PIT-GLOBAL-002` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:70` |
| `PIT-GLOBAL-003` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cycle-deps.md:8` |
| `PIT-GLOBAL-004` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cycle-deps.md:43` |
| `PIT-GLOBAL-005` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:12` |
| `PIT-GLOBAL-006` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:87` |
| `PIT-GLOBAL-007` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/common-tails.md:110` |
| `PIT-GLOBAL-008` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:185` |
| `PIT-GLOBAL-009` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:242` |
| `PIT-GLOBAL-010` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cycle-deps.md:85` |
| `PIT-GLOBAL-011` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:99` |
| `PIT-GLOBAL-012` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:175` |
| `PIT-GLOBAL-013` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:238` |
| `PIT-GLOBAL-014` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:308` |
| `PIT-GLOBAL-015` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:376` |
| `PIT-GLOBAL-016` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:442` |
| `PIT-GLOBAL-017` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:500` |
| `PIT-GLOBAL-021` | DRIF | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `DRIFT/global/lanes/L1-delivery.md:833;Pitfalls/global/cross-domain.md:825` |
| `PIT-GLOBAL-024` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/common-tails.md:362` |
| `PIT-GLOBAL-025` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/common-tails.md:449` |
| `PIT-GLOBAL-026` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:895` |
| `PIT-GLOBAL-027` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:956` |
| `PIT-GLOBAL-028` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1014` |
| `PIT-GLOBAL-029` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1058` |
| `PIT-GLOBAL-030` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1105` |
| `PIT-GLOBAL-031` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1158` |
| `PIT-GLOBAL-032` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P1-T01,P1-T05 ｜ DEC-04 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1228` |
| `PIT-GLOBAL-033` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1273` |
| `PIT-GLOBAL-034` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1308` |
| `PIT-GLOBAL-035` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1335` |
| `PIT-GLOBAL-036` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1356` |
| `PIT-GLOBAL-037` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1388` |
| `PIT-GLOBAL-038` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | P1-T01,P1-T02,P1-T03,P1-T05 ｜ ENV-04 | — | `Pitfalls/global/cross-domain.md:1451` |
| `PIT-GLOBAL-039` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1510` |
| `PIT-GLOBAL-040` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P2-T01,P2-T03 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1577` |
| `PIT-GLOBAL-041` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1681` |
| `PIT-GLOBAL-042` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1711` |
| `PIT-GLOBAL-043` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1742` |
| `PIT-GLOBAL-044` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1772` |
| `PIT-GLOBAL-045` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1804` |
| `PIT-GLOBAL-046` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1836` |
| `PIT-GLOBAL-047` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1868` |
| `PIT-GLOBAL-048` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P2-T01,P2-T05 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:1903` |
| `PIT-GLOBAL-049` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1940` |
| `PIT-GLOBAL-050` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:1980` |
| `PIT-GLOBAL-052` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2055` |
| `PIT-GLOBAL-053` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2132` |
| `PIT-GLOBAL-054` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | P2-T01 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2174` |
| `PIT-GLOBAL-055` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2315` |
| `PIT-GLOBAL-056` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2372` |
| `PIT-GLOBAL-057` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2415` |
| `PIT-GLOBAL-058` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2452` |
| `PIT-GLOBAL-059` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2482` |
| `PIT-GLOBAL-060` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2515` |
| `PIT-GLOBAL-061` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2552` |
| `PIT-GLOBAL-062` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2583` |
| `PIT-GLOBAL-063` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2616` |
| `PIT-GLOBAL-064` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2657` |
| `PIT-GLOBAL-065` | Pitf | global | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/global/cross-domain.md:2682` |
| `PIT-GLOBAL-066` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2705` |
| `PIT-GLOBAL-067` | Pitf | global | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/global/cross-domain.md:2728` |
| `PIT-GLOBAL-068` | Pitf | global | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | P4-T08 ｜ ENV-05 | — | `Pitfalls/global/cross-domain.md:2773` |
| `P-HOTAPI-24` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/api.md:993` |
| `P-HOTAPI-25` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/api.md:1037` |
| `P-HOTRPC-04` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:130` |
| `P-HOTRPC-05` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:153` |
| `P-HOTRPC-06` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:172` |
| `P-HOTRPC-07` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:216` |
| `P-HOTRPC-10` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:319` |
| `P-HOTRPC-11` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:351` |
| `P-HOTRPC-12` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:400` |
| `P-HOTRPC-13` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:447` |
| `P-HOTRPC-14` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:484` |
| `P-HOTRPC-15` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:522` |
| `P-HOTRPC-16` | Pitf | hot | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:564` |
| `P-HOTRPC-17` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:608` |
| `P-HOTRPC-18` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:646` |
| `P-HOTRPC-19` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:677` |
| `P-HOTRPC-22` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:787` |
| `P-HOTRPC-23` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:813` |
| `P-HOTRPC-24` | Pitf | hot | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:834` |
| `P-06` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/rpc.md:101` |
| `P-07` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:115` |
| `P-08` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:137` |
| `PIT-LIKE-RPC-012` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:297` |
| `PIT-LIKE-RPC-013` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/rpc.md:348` |
| `PIT-LIKE-RPC-014` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:389` |
| `PIT-LIKEAPI-001` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:12` |
| `PIT-LIKEAPI-006` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:196` |
| `PIT-LIKEAPI-009` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:300` |
| `PIT-LIKEAPI-010` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:328` |
| `PIT-LIKEAPI-011` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:355` |
| `PIT-LIKEAPI-012` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:399` |
| `PIT-LIKEAPI-013` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:431` |
| `PIT-LIKEAPI-014` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:465` |
| `PIT-LIKEAPI-015` | Pitf | like | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:515` |
| `PIT-LIKEAPI-016` | Pitf | like | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/api.md:561` |
| `PIT-MESSAGE-01` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:11` |
| `PIT-MESSAGE-05` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:193` |
| `PIT-MESSAGE-06` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:231` |
| `PIT-MESSAGE-07` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:263` |
| `PIT-MESSAGE-08` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:296` |
| `PIT-MESSAGE-09` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:10` |
| `PIT-MESSAGE-10` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:47` |
| `PIT-MESSAGE-12` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:130` |
| `PIT-MESSAGE-13` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:161` |
| `PIT-MESSAGE-16` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:257` |
| `PIT-MESSAGE-20` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:441` |
| `PIT-MESSAGE-21` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:513` |
| `PIT-MESSAGE-22` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:564` |
| `PIT-MESSAGE-23` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:608` |
| `PIT-MESSAGE-24` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:641` |
| `PIT-MESSAGE-29` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:794` |
| `PIT-MESSAGE-30` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:849` |
| `PIT-MESSAGE-31` | Pitf | message | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:885` |
| `PIT-MESSAGE-36` | Pitf | message | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:1107` |
| `PIT-TASK-04` | Pitf | task | 仍生效 | A 活体 · 红(1) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:193;Pitfalls/task/rpc.md:1564;Pitfalls/task/rpc.md:1620` |
| `PIT-TASK-05` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:243;Pitfalls/task/rpc.md:1565;Pitfalls/task/rpc.md:1621` |
| `PIT-TASK-08` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:365;Pitfalls/task/rpc.md:1568;Pitfalls/task/rpc.md:1624` |
| `PIT-TASK-09` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:400;Pitfalls/task/rpc.md:1569;Pitfalls/task/rpc.md:1625` |
| `PIT-TASK-10` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:452;Pitfalls/task/rpc.md:1570;Pitfalls/task/rpc.md:1626` |
| `PIT-TASK-11` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:496;Pitfalls/task/rpc.md:1571;Pitfalls/task/rpc.md:1627` |
| `PIT-TASK-12` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:543;Pitfalls/task/rpc.md:1572;Pitfalls/task/rpc.md:1628` |
| `PIT-TASK-13` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:586;Pitfalls/task/rpc.md:1573;Pitfalls/task/rpc.md:1629` |
| `PIT-TASK-15` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:673;Pitfalls/task/rpc.md:1575;Pitfalls/task/rpc.md:1631` |
| `PIT-TASK-17` | Pitf | task | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:806;Pitfalls/task/rpc.md:1577;Pitfalls/task/rpc.md:1633` |
| `PIT-TASK-19` | Pitf | task | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:933;Pitfalls/task/rpc.md:1579;Pitfalls/task/rpc.md:1635` |
| `PIT-TASK-21` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1048;Pitfalls/task/rpc.md:1581;Pitfalls/task/rpc.md:1637` |
| `PIT-TASK-26` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1246;Pitfalls/task/rpc.md:1586;Pitfalls/task/rpc.md:1642` |
| `PIT-TASK-27` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1293;Pitfalls/task/rpc.md:1587;Pitfalls/task/rpc.md:1643` |
| `PIT-TASK-28` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1318;Pitfalls/task/rpc.md:1588;Pitfalls/task/rpc.md:1644` |
| `PIT-TASK-29` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1348;Pitfalls/task/rpc.md:1589;Pitfalls/task/rpc.md:1645` |
| `PIT-TASK-34` | Pitf | task | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:763;Pitfalls/task/rpc.md:1594;Pitfalls/task/rpc.md:1650` |
| `P-18` | DRIF | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `DRIFT/users/admin/rpc.md:3976;DRIFT/users/admin/rpc.md:4246;Pitfalls/article/api.md:732` |
| `P-2` | DRIF | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `DRIFT/users/stage1-admission.md:767;Pitfalls/article/api.md:62` |
| `PIT-USERS-01` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:55` |
| `PIT-USERS-02` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:104` |
| `PIT-USERS-03` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:151` |
| `PIT-USERS-04` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:216` |
| `PIT-USERS-05` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:267` |
| `PIT-USERS-06` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:329` |
| `PIT-USERS-07` | Pitf | users | 仍生效 | A 活体 · 红(1) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:384` |
| `PIT-USERS-08` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:439` |
| `PIT-USERS-09` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:488` |
| `PIT-USERS-10` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:530` |
| `PIT-USERS-11` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:585` |
| `PIT-USERS-12` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:650` |
| `PIT-USERS-13` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:702` |
| `PIT-USERS-14` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:729` |
| `PIT-USERS-15` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:749` |
| `PIT-USERS-16` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:776` |
| `PIT-USERS-18` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:848` |
| `PIT-USERS-19` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:934` |
| `PIT-USERS-20` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:1101` |
| `PIT-USERS-21` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:1160` |
| `PIT-USERS-22` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:1254` |
| `PIT-USERS-23` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:1323` |
| `PIT-USERS-24` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/_shared.md:1375` |
| `PIT-USERS-25` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:26` |
| `PIT-USERS-27` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:173` |
| `PIT-USERS-28` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:227` |
| `PIT-USERS-29` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:287` |
| `PIT-USERS-30` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/api.md:343` |
| `PIT-USERS-31` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/api.md:386` |
| `PIT-USERS-32` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:22` |
| `PIT-USERS-33` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:60` |
| `PIT-USERS-35` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:133` |
| `PIT-USERS-36` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:159` |
| `PIT-USERS-37` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/user/rpc.md:193` |
| `PIT-USERS-38` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/rpc.md:232` |
| `PIT-USERSADM-01` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:23` |
| `PIT-USERSADM-02` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:62` |
| `PIT-USERSADM-03` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:117` |
| `PIT-USERSADM-04` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:145` |
| `PIT-USERSADM-05` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:179` |
| `PIT-USERSADM-06` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:208` |
| `PIT-USERSADM-07` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:239` |
| `PIT-USERSADM-08` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:265` |
| `PIT-USERSADM-09` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/api.md:294` |
| `PIT-USERSADM-10` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:343` |
| `PIT-USERSADM-11` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:377` |
| `PIT-USERSADM-12` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:416` |
| `PIT-USERSADM-13` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:444` |
| `PIT-USERSADM-14` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/api.md:480` |
| `PIT-USERSADM-16` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:570` |
| `PIT-USERSADM-17` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:621` |
| `PIT-USERSADM-18` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:22` |
| `PIT-USERSADM-21` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:158` |
| `PIT-USERSADM-22` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:229` |
| `PIT-USERSADM-23` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:272` |
| `PIT-USERSADM-25` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:333` |
| `PIT-USERSADM-27` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:444` |
| `PIT-USERSADM-28` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:518` |
| `PIT-USERSADM-29` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:585` |
| `PIT-USERSADM-30` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:646` |
| `PIT-USERSADM-31` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:712` |
| `PIT-USERSADM-32` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:781` |
| `PIT-USERSADM-33` | Pitf | users | 仍生效 | A 活体 · 绿(0) | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:857` |
| `PIT-USERSADM-34` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:937` |
| `PIT-USERSADM-35` | Pitf | users | 仍生效 | B 静态 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:1025` |
| `PIT-USERSADM-36` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:1143` |
| `PIT-USERSCOMMON-01` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:8` |
| `PIT-USERSCOMMON-02` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:43` |
| `PIT-USERSCOMMON-03` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:70` |
| `PIT-USERSCOMMON-04` | Pitf | users | 仍生效 | C 无指针 | P1 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/common.md:88` |
| `P-22` | Pitf | article | 待核实 | B 静态 | P2 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:935` |
| `PIT-LANE-05` | Pitf | _cross | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:154` |
| `PIT-LANE-06` | Pitf | _cross | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:188` |
| `PIT-LANE-07` | Pitf | _cross | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/_cross/domain-common-lanes.md:220` |
| `P-4` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:156` |
| `P-5` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/api.md:191` |
| `PF-ARTICLE-RPC-001` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:13` |
| `PF-ARTICLE-RPC-002` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:74` |
| `PF-ARTICLE-RPC-003` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:118` |
| `PF-ARTICLE-RPC-004` | Pitf | article | 已撤销 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:166` |
| `PF-ARTICLE-RPC-010` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:479` |
| `PF-ARTICLE-RPC-011` | Pitf | article | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/article/rpc.md:550` |
| `PIT-COMMENT-RPC-03` | Pitf | comment | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:136` |
| `PIT-COMMENT-RPC-07` | Pitf | comment | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:331` |
| `PIT-COMMENT-RPC-08` | Pitf | comment | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:375` |
| `PIT-COMMENT-RPC-11` | Pitf | comment | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:522` |
| `PIT-COMMENT-RPC-12` | Pitf | comment | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/comment/rpc.md:589` |
| `PIT-COMMON-02` | Pitf | common | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/common/audit.md:72` |
| `PIT-COMMON-07` | Pitf | common | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/common/deadline.md:122` |
| `PIT-FAV-API-008` | Pitf | favorite | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:292` |
| `PIT-FAV-API-009` | Pitf | favorite | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/api.md:390` |
| `PIT-FAV-RPC-013` | Pitf | favorite | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:566` |
| `PIT-FAV-RPC-034` | Pitf | favorite | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/favorite/rpc.md:1643` |
| `PIT-FOLLOW-001` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:19;Pitfalls/follow/rpc.md:286;Pitfalls/follow/rpc.md:311` |
| `PIT-FOLLOW-002` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:59;Pitfalls/follow/rpc.md:287;Pitfalls/follow/rpc.md:312` |
| `PIT-FOLLOW-003` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:100;Pitfalls/follow/rpc.md:288;Pitfalls/follow/rpc.md:313` |
| `PIT-FOLLOW-004` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:130;Pitfalls/follow/rpc.md:289;Pitfalls/follow/rpc.md:314` |
| `PIT-FOLLOW-005` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/rpc.md:160;Pitfalls/follow/rpc.md:290;Pitfalls/follow/rpc.md:315` |
| `PIT-FOLLOW-006` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:19;Pitfalls/follow/api.md:476;Pitfalls/follow/api.md:504` |
| `PIT-FOLLOW-007` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:71;Pitfalls/follow/api.md:477;Pitfalls/follow/api.md:505` |
| `PIT-FOLLOW-008` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:156;Pitfalls/follow/api.md:479;Pitfalls/follow/api.md:507` |
| `PIT-FOLLOW-009` | Pitf | follow | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:107;Pitfalls/follow/api.md:478;Pitfalls/follow/api.md:506` |
| `PIT-FOLLOW-010` | Pitf | follow | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/follow/api.md:178;Pitfalls/follow/api.md:480;Pitfalls/follow/api.md:508` |
| `P-HOTAPI-23` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/api.md:944` |
| `P-HOTRPC-01` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:15` |
| `P-HOTRPC-02` | Pitf | hot | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:64` |
| `P-HOTRPC-03` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:107` |
| `P-HOTRPC-08` | Pitf | hot | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/hot/rpc.md:251` |
| `P-HOTRPC-09` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:282` |
| `P-HOTRPC-20` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:712` |
| `P-HOTRPC-21` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:748` |
| `P-HOTRPC-25` | Pitf | hot | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/hot/rpc.md:885` |
| `P-01` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:31` |
| `P-02` | Pitf | like | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/like/rpc.md:45` |
| `P-03` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:59` |
| `P-04` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:73` |
| `P-05` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:87` |
| `P-09` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/rpc.md:185` |
| `PIT-LIKEAPI-002` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:51` |
| `PIT-LIKEAPI-003` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:89` |
| `PIT-LIKEAPI-004` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:121` |
| `PIT-LIKEAPI-005` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:160` |
| `PIT-LIKEAPI-007` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:233` |
| `PIT-LIKEAPI-008` | Pitf | like | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/like/api.md:266` |
| `PIT-MESSAGE-02` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:67` |
| `PIT-MESSAGE-03` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:113` |
| `PIT-MESSAGE-04` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/api.md:151` |
| `PIT-MESSAGE-11` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:85` |
| `PIT-MESSAGE-14` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:193` |
| `PIT-MESSAGE-15` | Pitf | message | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/message/rpc.md:231` |
| `PIT-MESSAGE-17` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:293` |
| `PIT-MESSAGE-18` | Pitf | message | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:319` |
| `PIT-MESSAGE-19` | Pitf | message | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:347` |
| `PIT-MESSAGE-25` | Pitf | message | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:672` |
| `PIT-MESSAGE-26` | Pitf | message | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:700` |
| `PIT-MESSAGE-27` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:731` |
| `PIT-MESSAGE-28` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:770` |
| `PIT-MESSAGE-32` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:919` |
| `PIT-MESSAGE-33` | Pitf | message | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:958` |
| `PIT-MESSAGE-34` | Pitf | message | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:1008` |
| `PIT-MESSAGE-35` | Pitf | message | 已闭环 | A 活体 · 红(1) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/message/rpc.md:1064` |
| `PIT-TASK-01` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:22;Pitfalls/task/rpc.md:1561;Pitfalls/task/rpc.md:1617` |
| `PIT-TASK-02` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:109;Pitfalls/task/rpc.md:1562;Pitfalls/task/rpc.md:1618` |
| `PIT-TASK-03` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:161;Pitfalls/task/rpc.md:1563;Pitfalls/task/rpc.md:1619` |
| `PIT-TASK-06` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:272;Pitfalls/task/rpc.md:1566;Pitfalls/task/rpc.md:1622` |
| `PIT-TASK-07` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:330;Pitfalls/task/rpc.md:1567;Pitfalls/task/rpc.md:1623` |
| `PIT-TASK-14` | Pitf | task | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:628;Pitfalls/task/rpc.md:1574;Pitfalls/task/rpc.md:1630` |
| `PIT-TASK-16` | Pitf | task | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:711;Pitfalls/task/rpc.md:1576;Pitfalls/task/rpc.md:1632` |
| `PIT-TASK-18` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:859;Pitfalls/task/rpc.md:1578;Pitfalls/task/rpc.md:1634` |
| `PIT-TASK-20` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:987;Pitfalls/task/rpc.md:1580;Pitfalls/task/rpc.md:1636` |
| `PIT-TASK-22` | Pitf | task | 已闭环 | A 活体 · 绿(0) | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1099;Pitfalls/task/rpc.md:1582;Pitfalls/task/rpc.md:1638` |
| `PIT-TASK-23` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1137;Pitfalls/task/rpc.md:1583;Pitfalls/task/rpc.md:1639` |
| `PIT-TASK-24` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1178;Pitfalls/task/rpc.md:1584;Pitfalls/task/rpc.md:1640` |
| `PIT-TASK-25` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1220;Pitfalls/task/rpc.md:1585;Pitfalls/task/rpc.md:1641` |
| `PIT-TASK-30` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1376;Pitfalls/task/rpc.md:1590;Pitfalls/task/rpc.md:1646` |
| `PIT-TASK-31` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1411;Pitfalls/task/rpc.md:1591;Pitfalls/task/rpc.md:1647` |
| `PIT-TASK-32` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1458;Pitfalls/task/rpc.md:1592;Pitfalls/task/rpc.md:1648` |
| `PIT-TASK-33` | Pitf | task | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/task/rpc.md:1501;Pitfalls/task/rpc.md:1593;Pitfalls/task/rpc.md:1649` |
| `PIT-USERS-17` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/_shared.md:800` |
| `PIT-USERS-26` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/api.md:110` |
| `PIT-USERS-34` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/user/rpc.md:105` |
| `PIT-USERSADM-15` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/api.md:531` |
| `PIT-USERSADM-19` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:70` |
| `PIT-USERSADM-20` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:119` |
| `PIT-USERSADM-24` | Pitf | users | 已闭环 | B 静态 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 已定性 · 未排期 | `Pitfalls/users/admin/rpc.md:307` |
| `PIT-USERSADM-26` | Pitf | users | 已闭环 | C 无指针 | P3 | N/A（不适用） | 否 | 未判定 | 未排期 | 无可复算指针 · 结论不可复算 | `Pitfalls/users/admin/rpc.md:386` |

---

## 七、已知盲区（**这一层的效力边界**）

1. **派生层不重新裁定任何结论** —— 台账说「已修」就是「已修」（在该证据强度下）。
   **「跑绿 ≠ 对」** 在这一层同样成立：U3 的 A 档只证明**那条探针的断言**成立，
   **不证明本条目结论的全量成立**。
2. **U5 拿不到定级 ⇒ 写 `—`** —— 不是「不重要」，是**还没定级**。
   本轮有 **605** 条属这一档 ⇒ 它们**不进施工单**，除非先定级。
3. **U8 的强链只认「指针落在条目段内」或「任务块里直接写了编号」** ——
   **既没写编号也没给指针**的语义关联 ⇒ **不可见**（这是本层的固有上限）。
4. **`CASE-*` 被排除** ⇒ 它们是**测试场景矩阵行**（证据层），不是差异条目；
   差异条目会引用 `CASE-*` 作为自己的证据。
5. **「阶段归属」与「优先级」不可互推** —— `twoStage.md` §10 明写「两者不能互相替代」。
   本层用 U4/U5 两条**独立**规则算，交叉不符时**两个都保留**，不强行对齐。
6. **「未排期」≠「不重要」** —— 它只说明**当前没有任何任务/未解决项指向它**。
7. **本层**不读**产品代码** ⇒ 它是**台账的投影**，不是代码的事实；
   「指针能解析」也不等于「结论为真」（同 AGENTS.md §3.3.3 第 ② 条）。

---

## 八、复现

```bash
cd "$(git rev-parse --show-toplevel)"
python3 tools/ledger-derive/derive-stage1.py            # 重新生成
python3 tools/ledger-derive/derive-stage1.py --check    # 守门：产物过期 ⇒ EXIT=1
python3 tools/ledger-derive/derive-stage1.py --report   # 只打印统计
```

