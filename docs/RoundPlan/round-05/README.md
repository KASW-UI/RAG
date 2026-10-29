# Round 5 · 取证与台账对齐（让台账可被审计）

> **本轮目标**：把**台账本身**修到「可复算、可审计、不自相矛盾」的状态 —— 取证缺口补齐 · 明细面与现状面对账 · 编号体系去冲突 · 硬编码数字清单化。
> **本轮不执行整改**（规范 §一.6 / §一.7）—— 本文件是**计划**，不是执行记录。

---

## ⚠️ 本轮的核心发现（这是本轮存在的理由）

抽取全仓台账时发现：**各域台账的「明细面」与「现状面」大面积不同步**。

| 台账 | 明细面写的 | 现状面写的 | 事实 |
|---|---|---|---|
| `docs/DRIFT/like/api.md:468` | `DRIFT-LIKEAPI-015` 🔴 **仍未修** | `like/vs-common.md:35` ✅ **已修**（注「旧快照『未修』过期」） | **矛盾** |
| `docs/DRIFT/message/api.md:912` | `DRIFT-MSGAPI-010` 🔴 **仍 OPEN** | `message/vs-common.md:38` ✅ **已修** | **矛盾** |
| `docs/DRIFT/message/api.md:1528` | `DRIFT-MSGAPI-013` 🟡 需用户拍板 | `message/vs-common.md:40` ✅ **已修** | **矛盾** |
| `docs/DRIFT/users/stage1-admission.md:91` | `S1-USERS-001` 🔴 **OPEN · A 类阻断阶段 1**（「README 7 字节」） | —— | README 现为 **9,815 B**（R1 已修）⇒ **陈旧快照** |
| `docs/DRIFT/users/stage1-admission.md:123` | `S1-USERS-003` 🔴 **OPEN**（「`.env` 不在 `.gitignore`」） | —— | `.gitignore:46` **有** `.env` ⇒ **陈旧快照** |

⇒ **后果**：下一轮复判会拿着**过期结论当现状** ⇒ 这不是「一条差异」，是**判定输入质量问题**。
⇒ 本轮把它变成**可核对的逐条任务**。

---

## §5.1 本轮目标与结束状态

| 项 | 内容 |
|---|---|
| **处理哪些已知问题** | ① `G-19`（proto 消费方式取证 · **取证缺口**）② 各域「明细面 vs 现状面」的**逐条矛盾** ③ **两套 `X-` 编号语义冲突**（`global/cross-domain.md` X-1~X-10 vs `PhaseGoal-judge.md` §五 X-1~X-12 ⇒ **X-2 / X-5 / X-7 全错位** ⇒ 编号不可寻址）④ `DRIFT-HOTAPI-030` **三处三态** ⑤ Pitfalls 台账的**状态字段普遍缺失**（大量条目「文件未标状态」⇒ 无法区分「仍生效」与「已闭环」）⑥ 台账里**几十处写死数字**（漂移温床） |
| **产生哪些可检查的结果** | ① 10 个 proto 的消费方式**逐个有结论（无「未知」）** ② 每个矛盾对**收敛到单一状态**且带「核对日期 + 依据」 ③ 跨域 `X-n` **重编号为 `CD-n`**（保留原号对照列） ④ `DRIFT-HOTAPI-030` 三处状态**一致** ⑤ 每条 Pitfall 有「状态 + 核对日期 + 是否影响当前轮次」 ⑥ 一份**「写死数字清单」**（文件:行号 + 数字 + 它统计什么 + 谁维护） |
| **为什么是下一轮的必要前提** | ① Round 6 的收口回归**要跑得干净** ⇒ 守门不能因台账自相矛盾而红 ② Round 7 的最终验收**逐条核判据** ⇒ 台账必须先自洽，否则「证据」本身不可靠 ③ `P5-T06` 的数字清单是**后续所有轮次**的防漂移清单 |
| **本轮不做** | 不改判据（Round 3）· 不改产品代码（取证任务可能**新增测试**，但不改行为）· **不代用户给 18 条未签字候选签字** · 不改 `tools/difftest/` 的守门清单计数口径 · 不做阶段二 |

---

## §5.2 开始条件

| 条件 | 状态 | 证据 |
|---|---|---|
| **Round 3 已完成**（基线已复判） | ⚠️ **必须** | 本轮的「对账」必须知道**哪个状态是当前判据认可的** ⇒ 否则对账会反向污染基线 |
| **Round 4 已完成**（残留清零） | ⚠️ **建议** | Round 4 会改一批台账行 ⇒ **先改完再对账**，否则要对两遍 |
| **各域 lane 可写本域台账** | ✅ | `AGENTS.md` §5.3b / §5.3f |
| **G4 可写 `docs/DRIFT/global/**`** | ✅ | `_PROTOCOL.md` C1 |
| **计数守门当前可用** | ✅ | `python3 tools/check-ledger-count.py`（守 `docs/Pitfalls/_INDEX.md` 的条目计数） |
| **无前置任务** | ✅ | — |

---

## §5.3 逐项任务清单

### P5-T01 · G-19：proto「消费方式」逐 proto 取证

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P5-T01` |
| **原始编号** | `R5-F4` · `G-19` · `X-10` 的取证缺口 |
| **来源** | `docs/PhaseGoal-judge.md:483`（G-19）· `docs/DRIFT/_cross/proto-verification.md:40-46`（「三档回归网」只覆盖文件层）· `AGENTS.md` §3.4（效力边界） |
| **当前状态** | 🟡 **取证缺口 · 不降 R** |
| **执行目的** | 把「`.proto` 文件层一致」升级到「**消费方式已验**」⇒ 关闭 `AGENTS.md` §3.4 的效力边界（**它在 10 个 proto 上全部成立**） |
| **具体操作** | 逐 proto 核三问，每条给「**是/否 + 证据（`文件:行号`）**」⇒ **无「未知」**：① **`optional` presence** 有无语义差（proto3 的 presence 在 Java 侧怎么表达、有没有丢）② 代码里有没有**字段号硬编码**（`setField(3, …)` 这类）③ **Jackson 配置**与 Go 侧是否同形 ④ **示范**：`ArticleProtoContractTest` / `HotProtoContractTest`（基准由 `generate.py` 从 Go 真源生成并冻结 ⇒ **基准在版本控制内**）⇒ 逐域照做 ⑤ 结论落各域台账 + `docs/DRIFT/_cross/proto-verification.md` |
| **具体落点** | 各域 `service/<域>/<域>-proto/src/test/**`（契约测试）· 各域台账 · `docs/DRIFT/_cross/proto-verification.md` |
| **执行主体** | **各域 lane**（按 proto 归属）＋ **主线**（`_cross/**` 汇总） |
| **前置条件** | Round 3 完成 |
| **并行关系** | 各域**可并行**；`_cross` 汇总**在最后**（要吃各域结论） |
| **写权限** | `service/<域>/<域>-proto/**` = 各域 lane · `docs/DRIFT/_cross/**` = 主线 |
| **验收命令** | ```bashmvn -o -B -DargLine=-Xmx2g -pl service/<域>/<域>-proto -am test\nbash tools/difftest/check-proto-static.sh ; echo "EXIT=$? （10 域逐字节）"\ngit ls-files service/*/*-proto/src/test | wc -l   # 契约测试必须在版本控制内``` |
| **验收证据** | ① 每个 proto 三问**各一条带 `文件:行号` 的结论**（**无「未知」**）② 每个 proto 至少一条断言到「presence / 字段号 / Jackson 形态」的用例 ③ **变异必红**（改字段号 ⇒ 断言红） |
| **完成条件** | **10 个 proto 逐个有结论** ⇒ G-19 可销号 |
| **失败处理** | 某 proto 确实无法证明（框架层不暴露）⇒ 记「**框架限制**」并归入 Intentional_Deviations 的「框架限制」类（**不是待签字候选**）⇒ 写明限制来源与 `文件:行号` |
| **状态更新** | 各域台账 · `docs/DRIFT/_cross/proto-verification.md`（G-19 行）· `docs/PhaseGoal-judge.md` §九 G-19（回填销号） |
| **后续任务** | Round 6 · Round 7 |

### P5-T02 · 各域「明细面 vs 现状面」逐条对账

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P5-T02` |
| **原始编号** | 原文无独立编号（各域台账冲突对）；已点名的：`DRIFT-LIKEAPI-015` · `DRIFT-MSGAPI-010` · `DRIFT-MSGAPI-013` · `S1-USERS-001` · `S1-USERS-003` |
| **来源** | `docs/DRIFT/like/api.md:468` vs `like/vs-common.md:35` · `message/api.md:912` vs `message/vs-common.md:38` · `message/api.md:1528` vs `message/vs-common.md:40` · `users/stage1-admission.md:91`/`:123` · `task/rpc.md:29` vs `:1633` |
| **当前状态** | 🔴 **台账内部矛盾**（不是「一条差异」） |
| **执行目的** | **判定输入的质量问题** ⇒ 下一轮复判不能拿着过期结论当现状 |
| **具体操作** | ① 逐域把「**明细面**」（`api.md` / `rpc.md` / `common.md` / `proto.md`）与「**现状面**」（`vs-common.md` / `stage1-admission.md`）的**每一条编号**拉成一张对照表 ② **以实测为准**判定每个编号的当前状态（**不是以任何一面的快照为准**）③ 把结论**同时**回填两面（**收敛到单一状态**）④ 每条改写标注「**核对日期 + 依据**」⑤ ⚠️ **保留原值 + 加时间线指针**（**历史不抹掉**）⑥ 优先处理**已点名的 5 处**（上面表格里的），再做全域扫描 |
| **具体落点** | 各域 `docs/DRIFT/<域>/**`（明细面 + 现状面） |
| **执行主体** | **各域 lane**（各自域） |
| **前置条件** | Round 3 ∧ Round 4 完成 |
| **并行关系** | 各域**可并行**（文件不重叠） |
| **写权限** | `docs/DRIFT/<域>/**` = **各域 lane** ⇒ ⚠️ **主线不越界改** |
| **验收命令** | ```bashpython3 tools/check-ledger-selfcheck.py ; echo "EXIT=$?"   # 机器可判的那部分\npython3 tools/check-ledger-tables.py    ; echo "EXIT=$?"   # 表结构\n# 对照表逐条人工核：每条能说出「依据是什么」``` |
| **验收证据** | ① 每个冲突对**收敛到单一状态** ② 每条有「核对日期 + 依据」 ③ 两个台账门 EXIT=0 ④ **原值保留**（历史可追溯） |
| **完成条件** | **同一编号在同一个域内不再有两种状态** |
| **失败处理** | 判定不了（无法确定哪个是当前）⇒ **标「待核实」并写清缺什么证据** ⇒ **不许选一个看起来对的**（规范 §3.2） |
| **状态更新** | 即本任务 |
| **后续任务** | Round 6 · Round 7 |

### P5-T03 · 跨域 `X-` 编号重编号（消除两套语义冲突）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P5-T03` |
| **原始编号** | 原文无独立编号（编号体系冲突） |
| **来源** | `docs/DRIFT/global/cross-domain.md:21-29`（X-1~X-9 + X-10）vs `docs/PhaseGoal-judge.md` §五（X-1~X-12）· `docs/DRIFT/global/_README.md:129-135`（第三套措辞，7 条无编号）· `AGENTS.md` §3.5（第四套，6 条无编号） |
| **当前状态** | 🔴 **编号不可寻址**：X-2 / X-5 / X-7 在两套里指向**完全不同的不变量** ⇒ 「查 X-5」查不到唯一的东西 |
| **执行目的** | 跨域台账的 `X-n` 只能被解析成**一个**东西，否则任何引用都可能指错 |
| **具体操作** | ① **定权威源 = `PhaseGoal-judge.md` §五**（它是 12 条的唯一出处且带判定命令）② 把 `global/cross-domain.md` 的 X-1~X-10 **重编号为 `CD-1`~`CD-10`**，并在同表**保留一列「原 X-n」**做对照 ③ `global/_README.md:129-135` 的 7 条**无编号**表述 → 补 `CD-n` 引用 ④ 全仓搜「`X-n`」的引用点 ⇒ **只保留指向 judge §五 的**；指向 `cross-domain.md` 的**全部改成 `CD-n`** ⑤ `AGENTS.md` §3.5 那 6 条（无编号）⇒ 补 `CD-n` 引用 |
| **具体落点** | `docs/DRIFT/global/cross-domain.md` · `docs/DRIFT/global/_README.md` · `AGENTS.md`（⚠️ **必须字节中性或更小** ⇒ 余量 34 B）· 各引用点 |
| **执行主体** | **G4**（`docs/DRIFT/global/**`）＋ **主线**（`AGENTS.md` / `_cross/**`） |
| **前置条件** | Round 3 完成 |
| **并行关系** | 与 `P5-T02`/`P5-T04` 有文件交叠（都在 `global/`）⇒ **串行** |
| **写权限** | `docs/DRIFT/global/**` = G4 · `AGENTS.md` = 主线（**字节中性**） |
| **验收命令** | ```bashpython3 tools/check-doc-links.py ; echo "EXIT=$?"\npython3 tools/check-agents-budget.py ; echo "EXIT=$? （余量不得变负）"\n# 全仓不得再有指向 cross-domain.md 的裸 X-n 引用\nrg -n 'X-[0-9]+' docs/ --glob '!docs/总/**' | grep -i 'cross-domain' | head``` |
| **验收证据** | ① 引用点逐条改完 ② 链接门 + 预算门 EXIT=0 ③ **AGENTS.md 字节数不增** ④ 重编号表可查（原号 ↔ 新号） |
| **完成条件** | **`X-n` 在全仓只有一个语义**（judge §五）⇒ 跨域的用 `CD-n` |
| **失败处理** | `AGENTS.md` 放不下 ⇒ **同次删掉等量重复句**（预算门会红 ⇒ 强制字节中性） |
| **状态更新** | `docs/DRIFT/global/cross-domain.md` 的表头说明 + `docs/DRIFT/_cross/` 的相关指针 |
| **后续任务** | Round 6 · Round 7 |

### P5-T04 · `DRIFT-HOTAPI-030` 三处状态对齐

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P5-T04` |
| **原始编号** | `DRIFT-HOTAPI-030` · `X-6`（跨域）· `A-3`（article）· `F-3`（favorite） |
| **来源** | `docs/DRIFT/_cross/open-items.md:15`（🔴 未修·待拍板）· `docs/DRIFT/global/_README.md:134`（🟡 未修·可修）· `docs/DRIFT/global/cross-domain.md:26`（✅ 第十四轮已落地）· `AGENTS.md:142`（✅ 已落地 · 共享层修法 · favorite 零触碰）· `docs/DRIFT/global/_README.md:251`（A1 ✅ 已落地） |
| **当前状态** | 🔴 **同一格三处三态**（🔴 / 🟡 / ✅） |
| **执行目的** | 权威态 = **✅ 已落地**（共享层 6 个新件 + 9 个域各一行 yaml + 真容器 + 真 MVC + 真 advice + 变异测试）⇒ 另两处是 **10-25 的陈旧快照** ⇒ 不对齐会让人以为还有一条跨域硬阻断 |
| **具体操作** | ① 把 `open-items.md:15` 与 `_README.md:134` 对齐到权威态 ✅ ② **保留订正时间线指针**（不删历史）③ 同步 `article/vs-common.md` 的 `A-3` 与 `favorite/vs-common.md` 的 `F-3` ④ 核对 `hot/api.md:2170` 的 `DRIFT-HOTAPI-030` 行与 `:2183` 的「上表**已过期**」注记 ⇒ 收敛 |
| **具体落点** | `docs/DRIFT/_cross/open-items.md` · `docs/DRIFT/global/_README.md` · `docs/DRIFT/global/cross-domain.md` · `docs/DRIFT/article/vs-common.md` · `docs/DRIFT/favorite/vs-common.md` · `docs/DRIFT/hot/api.md` |
| **执行主体** | **主线**（`_cross/**`）＋ **G4**（`global/**`）＋ **各域 lane**（`article` / `favorite` / `hot`） |
| **前置条件** | Round 3 完成 |
| **并行关系** | 与 `P5-T03` 有文件交叠 ⇒ **串行**；域侧部分与 `P5-T02` 同域 ⇒ **同批提交** |
| **写权限** | `_cross/**` = 主线 · `global/**` = G4 · `docs/DRIFT/<域>/**` = 各域 lane |
| **验收命令** | ```bashpython3 tools/check-ledger-selfcheck.py ; echo "EXIT=$?"\npython3 tools/check-ledger-tables.py    ; echo "EXIT=$?"\n# 全仓搜 HOTAPI-030，状态词必须唯一\nrg -n 'DRIFT-HOTAPI-030' docs/ --glob '!docs/总/**' | head -20``` |
| **验收证据** | ① 所有出现处状态**一致**（或明确写「历史快照 + 已订正」）② 两个台账门 EXIT=0 |
| **完成条件** | **三处三态 → 一态**（历史指针保留） |
| **失败处理** | 若实测与「已落地」不符 ⇒ **按实测改**（权威源是实测，不是任何一面）⇒ 并在变更记录登记 |
| **状态更新** | 即本任务 |
| **后续任务** | Round 6 · Round 7 |

### P5-T05 · Pitfalls 状态字段补齐

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P5-T05` |
| **原始编号** | `PIT-*`（global 56+ 条 + 各域） |
| **来源** | `docs/Pitfalls/_INDEX.md` + `docs/Pitfalls/<域>/**` + `docs/Pitfalls/_cross/**` |
| **当前状态** | 🔴 **状态字段普遍缺失** —— 抽取显示**大量条目「文件未标状态」** ⇒ 无法区分「仍生效」与「已闭环」⇒ 台账**不可审计** |
| **当前事实** | 例：`PIT-GLOBAL-005` ~ `PIT-GLOBAL-067` 中多数条目状态列为空或写「文件未标状态」；少数有明确状态的（如 `PIT-LANE-06` / `PIT-LANE-07` 标「已闭环」并附守门指针）⇒ **说明字段是能填的，只是没填** |
| **执行目的** | 规范 §2.3：「凡是某条 Pitfall 揭示了某项任务的错误判断方式…就必须将其转换成相关任务的**执行约束、验证动作或禁止事项**」⇒ **要先知道它是否仍生效**，才能正确转换 |
| **具体操作** | ① 给**每条** Pitfall 补三个字段：**状态**（仍生效 / 已闭环 / 已撤销）· **核对日期** · **是否影响当前轮次** ② 「已闭环」必须附**证据指针**（哪个守门 / 哪次变异）③ 「仍生效」的**必须**在对应轮次的「防错约束」小节里出现（**双向可查**）④ **「仍生效」但当前轮次不适用的** ⇒ 必须在「是否影响当前轮次」列写明**不适用的具体原因**（规范 §12.3 的第四种落点：「或说明当前不适用的具体原因」）—— **不许留空** ⑤ ⚠️ **不要**把已闭环的历史问题重新变成整改任务（规范 §2.3 末句）⑥ ⚠️ **不要**顺手改 Pitfall 的**内容**（只补状态字段） |
| **具体落点** | `docs/Pitfalls/_INDEX.md` · `docs/Pitfalls/<域>/**` · `docs/Pitfalls/_cross/**` |
| **执行主体** | **各域 lane**（各域 Pitfalls）＋ **G4**（`global/**`）＋ **主线**（`_INDEX.md` + `_cross/**`） |
| **前置条件** | Round 3 完成 |
| **并行关系** | 各域**可并行** |
| **写权限** | `docs/Pitfalls/<域>/**` = 各域 lane · `docs/Pitfalls/global/**` = G4 · `docs/Pitfalls/_INDEX.md` + `_cross/**` = 主线 |
| **验收命令** | ```bashpython3 tools/check-ledger-count.py ; echo "EXIT=$? （守 _INDEX.md 的条目计数）"\npython3 tools/check-ledger-tables.py ; echo "EXIT=$?"\n# 无状态字段的条目数必须归零\nrg -c '文件未标状态' docs/Pitfalls/ | head``` |
| **验收证据** | ① **0 条**「文件未标状态」② 计数守门 EXIT=0 ③ 「仍生效」的条目**在各轮防错约束里可查到**（双向对账）④ **「当前轮次不适用」的条目有具体原因**（规范 §12.3 第四种落点）—— **0 条留空** |
| **完成条件** | **每条 Pitfall 都有状态** ⇒ 台账可审计 |
| **失败处理** | 判定不了某条是否仍生效 ⇒ 标「**待核实**」+ 写清缺什么 ⇒ **不许默认「仍生效」也不许默认「已闭环」** |
| **状态更新** | 即本任务 |
| **后续任务** | Round 6 · Round 7 |

### P5-T06 · 产出「写死数字清单」

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P5-T06` |
| **原始编号** | 原文无独立编号（写死数字清单） |
| **来源** | `docs/DRIFT/_cross/cross-domain-coupling.md:139-147`（2411）· `:223-224`（44 / 315 vs 335）· `:232-233`（2860 / common 337）· `docs/DRIFT/global/_README.md:318/338/347/348/383/886-888`（2263/2301/2305/2303/2305/2310/2314）· `:216`（2860）· `docs/DRIFT/_cross/component-coverage-matrix.md:78`（28）· `docs/DRIFT/_INDEX.md:29`（23+5=28）· `:45-46`（2235 / 2310）· `docs/DRIFT/_cross/ddl-type-divergences.md:178`（51）· `docs/DRIFT/_cross/fix-round-2026-10-28.md:22`（27 · **旧快照**）· `docs/DRIFT/global/vs-common.md:33`/`:46`（28 / 21 stub / 30 处）· `docs/DRIFT/global/lanes/_PROTOCOL.md:12-17`（4 文件字节 221,405） |
| **当前状态** | 🟡 **漂移温床** —— 任何一轮改动都会让这些数字漂移（已是 B14 类问题的来源） |
| **执行目的** | 规范 §0.4：「不得因轮次重组而遗漏原始任务」+ AGENTS.md §六 6.3 的**计数口径写死**纪律 ⇒ 把「哪些数字会漂、谁负责」**显式化** |
| **具体操作** | ① 逐条列出：**文件:行号** · **当前数字** · **它统计的是什么** · **谁维护**（主线 / G4 / 某域 lane） · **哪一轮会改动它** ② 对**本计划会改动**的那些（回归条数 · 未修条数 · DDL 残留数 · 组件数 · 守门数）标注「**改动时必须同次订正**」③ 把清单落成**一份文件**（可被后续轮次引用）④ ⚠️ **不删任何原数字**（历史保留） |
| **具体落点** | 新建 `docs/RoundPlan/_hardcoded-numbers.md` |
| **执行主体** | **合并收口执行者（主线）** |
| **前置条件** | Round 3 完成 |
| **并行关系** | 与 `P5-T02`/`P5-T04` 可并行 |
| **写权限** | `docs/RoundPlan/**` = 主线 |
| **验收命令** | ```bash# 清单里每条都能被定位（脚本逐条核）\npython3 tools/check-doc-links.py ; echo "EXIT=$?"\n# 交叉核：清单条数 vs 实际命中数\nrg -c '' docs/RoundPlan/_hardcoded-numbers.md``` |
| **验收证据** | ① 每条有 `文件:行号` + 数字 + 统计对象 + 维护者 ② 抽 5 条**逐条回真源核实** ③ 本计划改动过的数字**已在清单里标出** |
| **完成条件** | 清单成文且**每条可定位** |
| **失败处理** | 某处数字找不到对应统计对象 ⇒ 标「**含义不明**」并登记为待核实（**不许猜**） |
| **状态更新** | 即本任务 |
| **后续任务** | Round 6（收口回归要按清单核对数字）· Round 7 |

---

## §5.4 具体验收命令（本轮整体）

```bash
cd "/Users/pr/Documents/java&golang/RAG"

# ── ① 台账四门 ──────────────────────────────────────────
python3 tools/check-ledger-count.py      ; echo "EXIT=$?"
python3 tools/check-ledger-selfcheck.py  ; echo "EXIT=$?"
python3 tools/check-ledger-tables.py     ; echo "EXIT=$?"
python3 tools/check-doc-links.py         ; echo "EXIT=$?"

# ── ② proto 契约（10 域）────────────────────────────────
bash tools/difftest/check-proto-static.sh ; echo "EXIT=$?"

# ── ③ 预算（若动了 AGENTS.md）───────────────────────────
python3 tools/check-agents-budget.py      ; echo "EXIT=$?"

# ── ④ 编号唯一性（X-n 只应指向 judge §五）──────────────
rg -n 'X-[0-9]+' docs/ --glob '!docs/总/**' --glob '!docs/RoundPlan/_legacy-index.md' | head -30

# ── ⑤ 无「文件未标状态」残留 ────────────────────────────
rg -c '文件未标状态' docs/Pitfalls/ || echo "✅ 0 条"
```

---

## §5.5 可客观判定的完成条件

| # | 判据 | 怎么判 |
|---|---|---|
| 1 | 10 个 proto 三问无「未知」 | 每 proto 三条结论各带 `文件:行号` |
| 2 | 明细面与现状面收敛 | 同一编号在同域内**只有一种状态**（或明确标「待核实」） |
| 3 | `X-n` 只有一个语义 | 全仓裸 `X-n` 引用**只指向 judge §五**；跨域的用 `CD-n` |
| 4 | `DRIFT-HOTAPI-030` 一态 | 所有出现处状态一致（历史指针保留） |
| 5 | Pitfalls 每条有状态 | **0 条**「文件未标状态」；计数门 EXIT=0 |
| 6 | 写死数字清单成文 | 每条可定位；抽 5 条回真源核实通过 |
| 7 | 门全绿 | 台账四门 + proto 门 + 预算门 **全 EXIT=0** |
| 8 | **不能算完成的情况** | 把「已闭环」的历史 Pitfall 重新变成整改任务 · 判定不了却选了「看起来对」的一侧 · 改了 Pitfall 的内容 · `AGENTS.md` 字节数变增 · 把 `X-n` 重编号但**旧引用没改完** |

---

## §5.6 本轮结束检查

- [ ] **P5-T01** 10 个 proto 三问逐个有结论（无「未知」）；G-19 可销号
- [ ] **P5-T02** 每个冲突对收敛到单一状态 + 核对日期 + 依据；**原值保留**
- [ ] **P5-T03** 跨域 `X-n` → `CD-n` 重编号完成；引用点全改；AGENTS.md 字节中性
- [ ] **P5-T04** `DRIFT-HOTAPI-030` 三处一致
- [ ] **P5-T05** 每条 Pitfall 有状态（0 条「文件未标状态」）
- [ ] **P5-T06** 写死数字清单成文
- [ ] 证据路径：各门禁真实退出码 + `docs/RoundPlan/_hardcoded-numbers.md`
- [ ] 原始编号 → 最新状态：`G-19` · 各域冲突编号 · `X-n`/`CD-n` · `DRIFT-HOTAPI-030` · `PIT-*` 全族
- [ ] 未完成任务及原因：（逐条写）
- [ ] 被阻塞的后续任务：Round 6 · Round 7
- [ ] 本轮新增问题及阶段一分类：（按 §十 定级 ⇒ `CT-02`）
- [ ] **是否满足进入 Round 6 的条件**：**是** —— 前提是台账四门全绿且台账自洽
- [ ] ⚠️ **本轮禁止**：改判据 · 改产品行为 · **代用户签字** · 改 `tools/difftest/` 的守门清单计数口径 · 把已闭环的 Pitfall 重新变成整改任务 · 越域改别人的台账

---

## 防错约束

| PIT 编号 | 出处（规范位置 · 逐条可核） | 规则（摘自该 PIT 标题原文） | 约束绑定的任务 ID |
|---|---|---|---|
| `PIT-GLOBAL-055` | `docs/Pitfalls/global/cross-domain.md:2099` | 🔴 **往台账里插一行 ⇒ 可能把「表格」插塌 ⇒ 而台账的内容一个字都没丢 ⇒ 所有内容层守门都查不出来** | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-061` | `docs/Pitfalls/global/cross-domain.md:2310` | 🔴 **「去噪」是那条判据的「计账规则」，不是「放弃检查」** ⇒ 让**另一条判据**失去覆盖 ⇒ 变异样本被相邻行吃掉 ⇒ **假绿** | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-022` | `docs/Pitfalls/global/cross-domain.md:660` | 🔴 **抄来的「同形态先例」编号也要复核** —— 指针不是证据，它本身是一条**待核对的断言** | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-033` | `docs/Pitfalls/global/cross-domain.md:1149` | 守门的**期望值**可能就落在**已登记的偏离范围**里 ⇒ 修一半 = 把「表述差异」混进「行为漂移」 | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-031` | `docs/Pitfalls/global/cross-domain.md:1042` | 🔴 **守门断言的字符串可能同时出现在被测文件的注释里 ⇒ 断言恒真 ⇒ 变异测不出来** | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-LANE-07` | `docs/Pitfalls/_cross/domain-common-lanes.md:196` | · 守门用**全文 grep** 判措辞 ⇒ 改掉**锚点那一行**后**守门恒绿**（2026-10-27 02:23 实测） | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-036` | `docs/Pitfalls/global/cross-domain.md:1220` | Spring 的 `@DynamicPropertySource` **不进** context cache key ⇒ 靠「properties 恰好不同」隔离上下文是**侥幸** | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-029` | `docs/Pitfalls/global/cross-domain.md:952` | 🔴 **静态扫描不排除 `target/` ⇒ 构建产物的旧副本被当源码判 ⇒ 门禁恒红** | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-040` | `docs/Pitfalls/global/cross-domain.md:1425` | 🔴 **变异无效的五种形态**（规则被掩护 / 对照 flaky / 变异打错层 / 阳性对照依赖分支 / 台账引述错路径） | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
| `PIT-GLOBAL-014` | `docs/Pitfalls/global/cross-domain.md:288` | 🔴 **变异测试报「没转红」时，先排除「测试压根没跑」** —— 否则会误删一个好守门 | 约束本轮任务：`P5-T01` · `P5-T02` · `P5-T03` · `P5-T04` · `P5-T05` · `P5-T06` |
