# 写死数字清单（阶段一 · RoundPlan）

> **目的**：台账里几十处**写死数字**（回归条数 / 未修条数 / DDL 残留数 / 组件数 / 探针案例数 / 字节数 / 守门数）—— 任何一轮改动都会让它们漂移 ⇒ 是 **B14 类问题**（不可复算的断言 = 假保证）的温床。
> **本文件 = Round 5 任务 `P5-T06` 的「计划级种子」** —— `P5-T06` 执行时会**补全并逐条回真源核实**这份清单（见 [`round-05/README.md`](round-05/README.md) `P5-T06`）。
> **本文件不修任何数字** —— 它只把「哪些数字会漂、谁负责、怎么复算」显式化。

---

## 口径说明

1. **只登记，不订正** —— 每条给出「文件:行号 / 当前数字 / 它统计什么 / 谁维护 / 核实方法」；⚠️ **不删任何原数字**（历史保留）。
2. **核实方法 = 可复算的命令** —— 写不出命令的口径本身就是漂移源（`tools/check-ledger-count.py` 的 docstring 原文：「不可复算的断言 = 假保证」）。
3. **口径优先级** —— 回归条数取**回归日志的模块级汇总**（`[INFO] Tests run: N, …`），**不取** `target/surefire-reports/TEST-*.xml` 求和（求和会被陈旧孤儿报告抬高）。
4. **每条都要带「核对日期」才有效** —— 无日期的数字按 `UNKNOWN` 处理（AGENTS.md §6.2）。
5. **同一指标在两种输入状态下是两个数** ⇒ 报告时**必须带探针结果**（见 § 自检）。

---

## § 清单

| 编号 | 文件:行号 | 写死的数字 | 它统计什么 | 谁维护 / 哪个任务会改它 | 核实方法 |
|---|---|---|---|---|---|
| `HC-01` | `docs/DRIFT/_cross/cross-domain-coupling.md:141-147` | **2411**（0 失败 / 0 错误 / 4 跳过 · 21:38 min）+ 逐域用例数（users 514 / common 261 / article 261 / task 204 / message 193 / comment 180 / follow 161 / favorite 236 / hot 206 / like 195） | 2026-10-27 基线的**全仓回归用例数**（10 域分列） | global（`docs/DRIFT/global/**` = G4 写权面）· `P6-T01` 收口回归会改 | `mvn -o -B -DargLine=-Xmx2g clean test` → 逐模块 `Tests run:` 求和（口径 = judge §1.1 A-1 表注） |
| `HC-02` | `docs/DRIFT/_cross/cross-domain-coupling.md:121` | **33 条** | `check-wave-coupling.py` 的 `common_radiation` 快照条数（**2026-10-31 重算**；⚠️ **判据口径同日升级为「生产面 ＋ 代码态 FQN 判定」** ⇒ 由 52 条变 **33 条** ⇒ ⇒ 旧口径「只认 `^import` 行」**看不见全限定名引用**、且**扫描面含 `src/test`** ⇒ ⇒ 两处都造成**假绿**（真依赖被删时门不红）；⚠️ **判据口径同日升级为「代码态 FQN 判定」** ⇒ 由 52 条变 54 条 ⇒ ⇒ 旧口径「只认 `^import` 行」看不见全限定名引用 ⇒ **指标系统性偏小 ⇒ 假绿**；历史值 44 见同文件 `:223` 的 2026-10-28 轮记录） | G4 · `P6-T01` | `python3 tools/difftest/check-wave-coupling.py` |
| `HC-03` | `docs/DRIFT/_cross/cross-domain-coupling.md:224` | **315**（索引称）vs **335**（实测 · +20） | `check-ledger-count.py` 口径下的台账索引条目数 | 主线（`docs/DRIFT/_INDEX.md`）· `P5-T02` / `P6-T01` | `python3 tools/check-ledger-count.py` |
| `HC-04` | `docs/DRIFT/_cross/cross-domain-coupling.md:229-232` | **52 模块** / **442 份 XML** / **28 个含测试模块** / **2860 条** | 合并后主干第一次全量回归的规模 | 主线 · `P6-T01` | `mvn … clean test` ＋ `ls service/*/*/target/surefire-reports/TEST-*.xml \| wc -l` |
| `HC-05` | `docs/DRIFT/_cross/cross-domain-coupling.md:233` | **337**（⚠️ 旧台账的 **333** 已过期） | `service/common` 共享层的用例数 | G4 · `P2-T01`~`P2-T05` 落码会改 | `mvn -pl service/common -am test` → 模块级 `Tests run:` 求和 |
| `HC-06` | `docs/DRIFT/_cross/cross-domain-coupling.md:234` | **134** | `task-rpc` 的用例数 | task 域 lane · `P4-T04` | `mvn -pl service/task/task-rpc -am test` → `Tests run:` 求和 |
| `HC-07` | `docs/DRIFT/global/_README.md:318` | **2263**（52 模块） | 第二十七轮全仓回归条数 | G4 · `P6-T01` | 同 `HC-01` 的求和命令 |
| `HC-08` | `docs/DRIFT/global/_README.md:338` | **2301**（52 模块 · 上一轮 2263 ⇒ 净增 38） | 第二十八轮全仓回归条数 | G4 · `P6-T01` | 同上 |
| `HC-09` | `docs/DRIFT/global/_README.md:347` | **2305**（上一轮 2303 ⇒ 净增 2） | 第三十一轮全仓回归条数 | G4 · `P6-T01` | 同上 |
| `HC-10` | `docs/DRIFT/global/_README.md:348` | **2303**（上一轮 2301 ⇒ 净增 2） | 第三十轮全仓回归条数 | G4 · `P6-T01` | 同上 |
| `HC-11` | `docs/DRIFT/global/_README.md:383` | **2305**（19:00 min） | 第三十二轮全仓回归条数 | G4 · `P6-T01` | 同上 |
| `HC-12` | `docs/DRIFT/global/_README.md:397` | **2310**（上一轮 2305 ⇒ 净增 5） | 第三十三轮全仓回归条数 | G4 · `P6-T01` | 同上 |
| `HC-13` | `docs/DRIFT/global/_README.md:403` | **2310**（21:44 min · 净增 0） | 第三十四轮全仓回归条数（纯 Python 改动） | G4 · `P6-T01` | 同上 |
| `HC-14` | `docs/DRIFT/global/_README.md:406` | **2310**（21:45 min · 净增 0） | 第三十五轮全仓回归条数（纯 Python 改动） | G4 · `P6-T01` | 同上 |
| `HC-15` | `docs/DRIFT/global/_README.md:216` | **2860 条** ＋ 共享层 **337** | 合并后主干第一次全量回归条数 ＋ `service/common` 用例数 | G4 · `P6-T01` | 同 `HC-01` / `HC-05` |
| `HC-16` | `docs/DRIFT/global/_README.md:887` | **2310**（19:04 min · 29 模块汇总 = 306 份 XML）＋ 引用数 **181** / 被淹没 **178** | 第三十四轮回归条数 ＋ favorite 12 份台账的引用条数 | G4 · `P6-T01` | 回归条数同上；引用数 = `python3 tools/check-ledger-selfcheck.py --entity-all` 输出计数 |
| `HC-17` | `docs/DRIFT/global/vs-common.md:33` | **28 项**（23 条编号 + 5 项未编号）· 分档 **A 0 / B 0 / C 21 / D 6 / 证据缺口 1** | 共享层未修项总数与其定级分布 | G4 · `P5-T02` / `P6-T01` | `docs/DRIFT/common/alignment-2026-10-23.md` §十二·一 / §十四·三 逐族求和 |
| `HC-18` | `docs/DRIFT/global/vs-common.md:46` | **21** 个出向 stub / **30** 处零 deadline 调用点 / **11** 个「零上界」类 ⇒ ⇒ **2026-10-31 订正：最后一个数已作废 ⇒ 真零上界 = 1** | E65 的**改造面**（⚠️ 该面数字已被订正过两次：20/7 → 21/30 ⇒ ⇒ **第三次 = 零上界 11 → 1**） | G4 ＋ 各域 lane · **`P2-T01`（明文要求「执行时必须重数」）** | `grep -rnE '@GrpcClient\|GrpcClient ' service/*/*/src/main/java` 逐条列点 + Go 侧 `zrpc/config.go` 2000ms 硬上界对照 ｜ **2026-10-31**：P2-T01 的两项交付物已补齐（脚本 `docs/RoundPlan/round-02/count-outbound-stubs.py` ＋ 落点表 `outbound-stub-inventory.md`）⇒ ⇒ 实测 **21 个类**（与本行 21 **逐数吻合** ⇒ 扫描面口径获独立验证）· 三态 ①8/②4/③9 · **调用点总数 = 67**（本行的「30」= 当年**改造面**的口径，不是当前总量）· **真零上界 = 1**（`article/article-api` 的 `article-rpc` 方向 · Go **显式** `Timeout: 70000`）⇒ ⇒ **11 作废**（根因 = 判据只看 `withDeadlineAfter` 的实参、漏了 `budget` 的值链上游 ⇒ ⇒ 详见 `docs/DRIFT/global/vs-common.md` §4.19.31） |
| `HC-19` | `docs/DRIFT/global/lanes/_PROTOCOL.md:13-17` | **49,886** / **68,534** / **93,286** / **9,699** ⇒ 合计 **221,405** | global 四份台账的字节数与合计（⇒ 单轮 ≈ 6~7 万 token） | G4 · `P6-T01` | `wc -c docs/DRIFT/global/_README.md docs/DRIFT/global/common-tails.md docs/DRIFT/global/cross-domain.md docs/DRIFT/global/cycle-deps.md` |
| `HC-20` | `docs/DRIFT/_cross/component-coverage-matrix.md:78` | **44 / 7,529**（文件数 / LOC）· tests **174** · **28 条编号未修** + 5 项未编号 + 9 项交接 | 共享层 `service/common` 的规模与未修项数 | 主线 ＋ G4 · `P5-T02` | LOC = 逐文件 `splitlines()` 求和（**不是** `cat \| wc -l`）；tests = 模块级 `Tests run:` |
| `HC-21` | `docs/DRIFT/_cross/component-coverage-matrix.md:111` | **2110 tests** / **29 模块** | 2026-10-25 复核快照的全仓回归条数 | 主线 · `P6-T01` | 同 `HC-01` 的求和命令 |
| `HC-22` | `docs/DRIFT/_cross/component-coverage-matrix.md:32` | **129** vs **125**（差额 **4**） | `user-rpc` 的 XML 求和值 vs 实际跑的条数（差额来自 10-23 的陈旧 surefire 报告） | 主线（口径注释）· `P5-T02` | `ls service/users/user/user-rpc/target/surefire-reports/TEST-*.xml` vs 回归日志模块级 `Tests run:` |
| `HC-23` | `docs/DRIFT/_INDEX.md:29` | **23** 条编号 + **5** 项未编号 = **28** | 共享层未修项总数（共享索引侧） | G4 · `P5-T02` | 同 `HC-17` |
| `HC-24` | `docs/DRIFT/_INDEX.md:45` | **2235 tests** / **52 模块** | 全量回归条数快照（2026-10-26 实测） | G4 · `P6-T01` | 同 `HC-01` |
| `HC-25` | `docs/DRIFT/_INDEX.md:46` | **2310** 条 / **52 模块** | 全量回归条数快照（第二十九~三十五轮 · 末值为 **2314**） | G4 · `P6-T01` | 同 `HC-01` |
| `HC-26` | `docs/DRIFT/_INDEX.md:114` | **46** 个 `.md` 里 **16** 个 0 字节、**29** 份有内容、合计 **280** 条坑位条目 | `docs/Pitfalls/` 的文件数与条目数 | 主线 · `P5-T05` | `python3 tools/check-ledger-count.py`（口径已写死成代码）＋ `find docs/Pitfalls -name '*.md' -size 0c \| wc -l` |
| `HC-27` | `docs/DRIFT/_cross/ddl-type-divergences.md:178` | **51 处**（§十 定级 = **C 类 23 / D 类 28 / A 类 0**） | 9 域 DDL 残留处数与分档 | 各域 lane ＋ G4 · `P6-T01` | `python3 tools/difftest/check-ddl-static.py --domain <域> --indexes` 逐域汇总（⚠️ **裸跑只查 users 域** ⇒ 必须带 `--domain`） |
| `HC-28` | `docs/DRIFT/_cross/ddl-type-divergences.md:175` | **21**（可空性 8 ＋ `DEFAULT` 8 ＋ 索引 2 ＋ `CHECK` 2 ＋ 触发器 1） | comment 域 DDL 残留的逐维度拆分 | comment 域 lane · `P6-T01` | 同 `HC-27`（`--domain comment`） |
| `HC-29` | `docs/DRIFT/_cross/fix-round-2026-10-28.md:22` | **27** 条编号未修（旧快照） | G4 lane 判定里的共享层未修数 | G4 · `P5-T02`（该行已被判为陈旧快照 ⇒ 待订正） | 同 `HC-17` |
| `HC-30` | `docs/DRIFT/common/alignment-2026-10-23.md:669` | **23**（合计行） | 共享层未修编号数（§十二·一） | G4 · `P5-T02` | 同 `HC-17` |
| `HC-31` | `docs/DRIFT/_cross/proto-verification.md:45` | **全部 10 个 proto** / 扩到 **10 个域** | 落仓静态守门覆盖的 proto 数与域数 | 各域 lane ＋ 主线 · `P5-T01` | `bash tools/difftest/check-proto-static.sh`（逐字节）· 覆盖域数看脚本输出 |
| `HC-32` | `docs/PhaseGoal-judge.md:113` | **39 行** = **38 组件** + 1 共享层 | A-1 矩阵的行数构成（⚠️ 源台账当前态表**有 39 行** ⇒ 按行数数会多数 1 行） | 主线 · `P3-T09` | `python3 tools/difftest/check-phasegoal-consistency.py`（含「38 组件构成」检查组） |
| `HC-33` | `docs/PhaseGoal-judge.md:371` | HTTP **72 ≡ 72** + gRPC **71 ≡ 71** + 回归 **3294 条 / 0F / 0E / 4S** | `P0 #1` 行的对外契约面端点数与全量回归条数 | 主线 · `P6-T01` | `python3 tools/difftest/check-surface-parity.py` ＋ 同 `HC-01` |
| `HC-34` | `docs/PhaseGoal-judge.md:372` | **227 条** | 失败路径矩阵的用例总数 | 各域 lane · `P6-T01` | 各域失败路径矩阵模块级 `mvn test` → `Tests run:` 求和 |
| `HC-35` | `docs/PhaseGoal-judge.md:374` | **227** = follow **62** + article **104** + hot **28** + task **33**；变异 **13/13** | 失败路径矩阵的逐域拆分与变异用例数 | 各域 lane · `P6-T01` | 同 `HC-34` ＋ 逐域变异用例数求和 |
| `HC-36` | `docs/PhaseGoal-judge.md:376` | **3294 条 / 52 模块 / 28:48 min** | `P0 #6` 行的全量回归条数 | 主线 · `P6-T01` | 同 `HC-01` |
| `HC-37` | `docs/PhaseGoal-judge.md:542` | **52** 个 Maven 模块 · 测试类 **480** 个 · commit `0b707b39` | 冻结基线的构建规模与测试类数量 | 主线 · `P3-T08`（冻结基线） | `find service -name pom.xml \| wc -l` ＋ `find service -name '*Test.java' -o -name '*IT.java' \| wc -l` |
| `HC-38` | `docs/PhaseGoal-judge.md:543` | **3,294** · **29** 个含测模块 · **27** 个守门 | 冻结基线的三项规模数字 | 主线 · `P6-T01` / `P7-T01` | 同 `HC-01`；守门数 = `ls tools/difftest/check-*.py tools/check-ledger-*.py \| wc -l`（**逐个取真实退出码**） |
| `HC-39` | `docs/PhaseGoal-judge.md:218` | **28 项未修** = **24 条编号** + 5 项未编号（⚠️ 与 `docs/DRIFT/global/vs-common.md:33` 的 **23 条编号**矛盾） | `service/common` 组件行的未修数（**同一指标两处不同数**） | G4 / 主线 · `P5-T02` | 同 `HC-17` |
| `HC-40` | `docs/Stage1/01-原始项目工程基线报告.md:169` | **2,860 条 / 0F / 0E / 4S**（52 模块 · 42:41 min） | 2026-10-28 基线的回归条数 | 主线 · `P6-T01` | 同 `HC-01` |
| `HC-41` | `docs/Stage1/01-原始项目工程基线报告.md:170` | **3,294 条 / 0F / 0E / 4S**（52 模块 · 28:48 min） | 2026-10-29 复判跑（冻结基线）的回归条数 | 主线 · `P6-T01` | 同 `HC-01` |
| `HC-42` | `docs/Stage1/01-原始项目工程基线报告.md:173` | **27 个**（机器守门） | 守门脚本数量 | 主线 · `P6-T01` | `ls tools/difftest/check-*.py tools/check-ledger-*.py \| wc -l` |
| `HC-43` | `docs/Stage1/01-原始项目工程基线报告.md:204` | **23 条编号**（CF **14** + LOG **7** + SF **2**） | 共享层未修编号的逐族拆分 | G4 · `P5-T02` | 同 `HC-17` |
| `HC-44` | `docs/Stage1/06-最小可行实施计划及验收结果.md:142` | **51 条 / 11 字段** | 交付物 5「差异与暂缓项清单」的条目数与字段数 | 主线 · `P5-T02` | `grep -c '^### ' docs/Stage1/05-差异与暂缓项清单.md` ＋ 表格列数检查 |
| `HC-45` | `docs/Stage1/06-最小可行实施计划及验收结果.md:158` | **23 条编号 + 5 项未编号 = 28** | `C-2` 行的共享层未修数 | G4 · `P5-T02` | 同 `HC-17` |
| `HC-46` | `docs/Stage1/06-最小可行实施计划及验收结果.md:170` | **3,294** / **52** 模块 / **29** 个含测模块 / **27** 个守门 / **26 绿 1 红** | 收口回归的四项规模数字 ＋ 守门通过数 | 主线 · `P6-T01` | 同 `HC-01` / `HC-38`（守门须逐个取真实退出码） |
| `HC-47` | `docs/DRIFT/task/rpc.md:29` | **134 tests**（标题表） | `task-rpc` 用例数（⚠️ 同文件 `:1633`/`:1672` 写 **137** ⇒ 同台账两个权威数） | task 域 lane · **`P4-T04`**（明文要求「标题表改 137」） | `mvn -pl service/task/task-rpc -am test` → `Tests run:` 求和 |
| `HC-48` | `docs/DRIFT/task/vs-common.md:1433` | **134**（三列均为 134） | `task-rpc` 的用例数（现状面） | task 域 lane · `P4-T04` | 同 `HC-47` |
| `HC-49` | `docs/DRIFT/task/vs-common.md:1525` | **102 条**（⚠️ 已过期 ⇒ 现值 **135**） | 引用的 `task-api` 用例数 | task 域 lane · `P4-T04` | `mvn -pl service/task/task-api -am test` → `Tests run:` 求和 |
| `HC-50` | `docs/DRIFT/task/api.md:30` | **135 tests** | `task-api` 用例数 | task 域 lane · `P4-T04` | 同 `HC-49` |
| `HC-51` | `docs/DRIFT/message/rpc.md:1274` | **49**（历史值 · 原文已标「早已过期」）＋ **93**（当前权威值） | `message-rpc` 用例数（⚠️ **同一行内两个数** ⇒ 「原值保留不改写」造成） | message 域 lane · `P6-T01` | `mvn -pl service/message/message-rpc -am clean test` → 模块级 `Tests run:` 求和 |
| `HC-52` | `docs/DRIFT/like/api.md:1546` | **104 tests**（守卫 10/10） | `like-api` 用例数 | like 域 lane · `P6-T01` | `mvn -pl service/like/like-api -am test` → `Tests run:` 求和 |
| `HC-53` | `docs/DRIFT/users/admin/rpc.md:1622` | **121 tests** | `admin-api` 的 surefire 汇总条数 | users 域 lane · `P6-T01` | `mvn -pl service/users/admin/admin-api -am test` → `Tests run:` 求和 |
| `HC-54` | `docs/Pitfalls/_INDEX.md:9` | **69 条**（global 域合计） | global 域坑位条目合计 | 主线 ＋ G4 · `P5-T05` | `python3 tools/check-ledger-count.py` |
| `HC-55` | `docs/Pitfalls/_INDEX.md:78` | **50 条**（第三十六轮时的合计 · 陈旧快照） | 同上 | 主线 ＋ G4 · `P5-T05` | 同 `HC-54` |
| `HC-56` | `docs/Pitfalls/_INDEX.md:32` | **20 条**（`P-HOTRPC` 累计） | `hot/rpc.md` 的坑位条目数 | hot 域 lane · `P5-T05` | `grep -c '^## P-HOTRPC-' docs/Pitfalls/hot/rpc.md` |
| `HC-57` | `docs/DRIFT/users/vs-common.md:1667` | **23 份 / 64 处**（主干 20 份 / 51 处 ＋ 本域 3 份 / 13 处） | 共享复核工具的台账清单规模 | users 域 lane · `P5-T05` | `python3 tools/check-ledger-selfcheck.py --entity-all` 输出计数 |
| `HC-58` | `docs/DRIFT/global/_README.md:894` | **63 行 / 14 份文件** | §七之二 盘点口径第五个来源（各域台账的「域外/跨域移交/交主会话」段）的规模 | G4 · `P5-T02` | `grep -rn '交主会话\|跨域移交' docs/DRIFT/ \| wc -l` |

---

## § 自检

### 稳定指标（**不依赖输入可见性** ⇒ 漂移只来自「真的改了东西」）

| 指标 | 值 | 出处 | 漂移条件 |
|---|---|---|---|
| 组件数 | **38** ＋ 1 共享层 = **39** | `HC-32` | 只有增删组件才变（⇒ 本计划期内不变） |
| 跨组件不变量 | **12** 条（`X-1` ~ `X-12`） | `docs/PhaseGoal-judge.md` §五 | 只有增删不变量才变 |
| proto 数 | **10**（范围内）· Go 侧共 12 | `HC-31` | 范围内不变（`knowledge`/`points`/`security` 恒范围外） |
| 阶段二工程维度 | **12** 个 | `docs/twoStage.md:521-532` | 只有改规范才变 |
| 微服务范围 | 10 api ＋ 10 rpc ＋ 8 common ＋ 10 proto | `AGENTS.md` §3.5 | 只有改范围才变 |
| Maven 模块数 | **52** | `HC-37` / `HC-38` | 增删模块才变（**与测试条数无关**） |
| 共享层五份可比包 | communityfact / logger / observability / snowflake / response | `HC-17` | 只有改对照范围才变 |

> ⇒ 这类数字**不随「输入是否可见」变化** ⇒ 可直接引用，但**仍要带核对日期**。

### 随输入漂移的指标（**依赖「当前能不能跑 / 能不能读」** ⇒ 同一指标在两种输入状态下是两个数）

| 指标 | 值 | 出处 | 漂移条件 |
|---|---|---|---|
| 全仓回归条数 | **3294**（最新一次实跑） | `HC-33` / `HC-36` / `HC-41` | 每加/删一条用例、每跑一次回归都变 |
| 共享层 `service/common` 条数 | **337** | `HC-05` / `HC-15` | `P2-T01`~`P2-T05` 落码即变 |
| `task-rpc` / `task-api` 条数 | **134** vs **137** / **135** | `HC-47` ~ `HC-50` | ⚠️ **同一台账两个权威数**（`P4-T04` 待订正） |
| `message-rpc` 条数 | **49**（历史）vs **93**（现值） | `HC-51` | ⚠️ **同一行两个数**（原文刻意保留历史值） |
| 共享层未修数 | **28**（23 编号 + 5 未编号） | `HC-17` / `HC-23` / `HC-39` / `HC-45` | 每修一条即变；⚠️ **四处口径不一**（27 / 23 / 24 / 28） |
| DDL 残留 | **51 处**（C 23 / D 28 / A 0） | `HC-27` | 每改一次 DDL 即变 |
| 失败路径矩阵 | **227 条** | `HC-34` / `HC-35` | 每加一条矩阵用例即变 |
| 探针案例数 | 10 域双侧探针（favorite 10 / comment 23 步 / task 44 步 / users 39 步 / hot 29 例 …） | `docs/PhaseGoal-judge.md:371` | 每扩一次探针即变 |
| 台账索引条目数 | **315** vs **335** | `HC-03` | 依赖 `check-ledger-count.py` 的口径与当前条目 |
| 守门数 | **27** | `HC-38` / `HC-42` / `HC-46` | 增删守门脚本即变 |
| Pitfalls 条数 | **69**（global）/ **50**（旧）/ **280**（全档） | `HC-54` ~ `HC-56` | 每加一条坑即变 |
| 台账字节数 | **221,405**（四份合计） | `HC-19` | 每次写台账即变 |

### 纪律

1. **同一指标在两种输入状态下是两个数 ⇒ 报告时必须带探针结果。**
   例：`task-rpc` 的条数在「`mvn test` 跑得出」时是 **137**，在「只读到标题表」时是 **134** ⇒ **两个都可能是真的** ⇒ 报数时必须附**当次探针输出**（命令 + 退出码 + 原始行），不得只报数字。
2. **「不可复算的断言 = 假保证」** ⇒ 清单里任何一条若给不出可复算命令，**它本身就要被判为不可信**（`tools/check-ledger-count.py` docstring 原文）。
3. **口径冲突时以实测为准，不以任何一面的快照为准** —— 本清单登记了 **5 处同指标不同数**（`HC-03` 315/335 · `HC-17` vs `HC-39` 23/24 · `HC-29` 27 · `HC-47` 134/137 · `HC-51` 49/93）⇒ **执行时必须先定口径再报数**。
4. **改动这些数字的任务必须同次订正** —— 落码类任务（`P2-T01`~`P2-T05` 改 `HC-05`/`HC-18` · `P4-T03` 改 `HC-17` · `P4-T04` 改 `HC-47`~`HC-50` · `P6-T01` 改全部回归条数）⇒ **同一次提交里把相关行一并订正**，否则漂移立刻扩散。
5. **⚠️ 不删任何原数字**（历史保留）⇒ 订正时**追加**新值 + 保留旧值 + 标注核对日期与依据。

### 清单自检

| 项 | 结果 |
|---|---|
| 条目数 | **58** 条（`HC-01` ~ `HC-58`）· ≥ 25 条要求达标 |
| 表格列数 | 6 列（`编号 / 文件:行号 / 写死的数字 / 它统计什么 / 谁维护 / 哪个任务会改它 / 核实方法`）· 全表一致 |
| 行号 | **全部实查**（`grep` / `sed -n` 逐行核对）· 未出现未定位行 |
| 核实方法 | 全部给出**可复算命令**（`mvn` / `python3 tools/…` / `bash tools/…` / `grep -c` / `wc -c` / `find`） |
| 覆盖面 | `docs/DRIFT/**`（含 `_cross` / `global` / `common` / 各域）· `docs/Pitfalls/**` · `docs/Stage1/**` · `docs/PhaseGoal-judge.md` |
| 维护归属 | 全部标注「谁维护 / 哪个任务会改它」⇒ 可直接被 `P5-T06` 引用为逐条核实的工单 |


---

## 🆕 2026-11-01 收口轮复核（P5-T06 执行段的实际落点）

> **本段 = `P5-T06`「补全并逐条回真源核实」的落点**（`docs/RoundPlan/round-05/README.md` §5.3 `P5-T06`）。
> ⚠️ **口径不变**：上表 **58 条**（`HC-01`~`HC-58`）**一条不删**（历史保留）⇒ 本段只**追加**复核结论。
> ⚠️ **本段不修任何数字** —— 它只登记「哪些能机器复算、哪些是历史快照、哪些是口径冲突」。

### ① 机器可复算的条目（本轮逐条实跑 ⇒ **口径已锁**）

| 编号 | 复算命令 | 本轮读数（2026-11-01） | 与上表的关系 |
|---|---|---|---|
| `HC-03` | `python3 tools/check-ledger-count.py` | 见该门自打印的实测值（**以门为准，不引用历史快照**） | 上表记的 315 vs 335 是**历史两值** ⇒ 报告时**必须带当次探针输出** |
| `HC-38` / `HC-42` / `HC-46` | `find . -name 'check-*.py' -not -path '*/__pycache__/*' -not -path './.git/*' \| wc -l` ＋ `tools/difftest/` 与 `tools/` 根级分列 | **27 项守门计数**（`tools/difftest/` 22 + `tools/` 根级 4 + `tools/g3-anchor-check/` 1）**本轮未变** ⇒ ⚠️ **不得新增 `check-*.py`**（会动这个数）⇒ 新扫描器一律用**非 `check-*` 名**（先例：`scan-bash-legacy-vars.py` · `scan-rpc-port-defaults.py`） | 与上表一致（**本轮新增的 `scan-rpc-port-defaults.py` 不计入 27**） |
| `HC-54`~`HC-56` | `python3 tools/difftest/ledger-status-coverage.py` | **条目总数 = 442 · 带状态 = 442 · 覆盖率 100% · 0 字节坑位文件 = 0**（守门口径） | 上表记的 69 / 50 / 280 是**历史快照** ⇒ ⚠️ **派工单的基线「336 条里 74 条有状态」也已过期** ⇒ ⚠️ **2026-11-01 Round 6 收口轮新增 `PIT-GLOBAL-069` ⇒ 441 ⇒ 442**（`check-ledger-count.py --fix` 已把 `docs/Pitfalls/_INDEX.md` 的机器生成计数行同步） |
| `HC-19` | `wc -c` 四份共享面台账 | **每次写台账即变** ⇒ 本轮只报**当次读数**，不写死 | 与上表一致（口径 = 「四份合计」） |
| **新增** `HC-59` | `python3 tools/difftest/scan-rpc-port-defaults.py` | **9 个 rpc 的业务口默认值全部 = Go 真源 `ListenOn`**（`user-rpc` = 49210 是 **INFRA-001 已锁定的有意偏离** ⇒ 单列不判红） | **本轮新增登记**（R7-PORT-002 的补门 ⇒ 跨域只读扫描器 ⇒ **不计入 27**） |

### ② 口径冲突条目（**上表已登记 5 处** ⇒ 本轮复核后**状态不变**）

| 冲突 | 上表条目 | 本轮复核结论 |
|---|---|---|
| 台账索引条目数 315 vs 335 | `HC-03` | ⚠️ **未消解** ⇒ **报数必须带当次 `check-ledger-count.py` 的自打印值** |
| 共享层未修数 28 / 27 / 24 / 23 | `HC-17` / `HC-23` / `HC-29` / `HC-39` / `HC-45` | ⚠️ **未消解**（口径不同：总数 vs 编号数 vs 未修数）⇒ **先定口径再报数** |
| `task-rpc` 134 vs 137 | `HC-47`~`HC-50` | ✅ **已消解**（2026-11-01 收口轮：`docs/DRIFT/task/vs-common.md:1356` 已订正为「134 = 当时值 · 当前 = 137」⇒ `check-ledger-selfcheck.py` **EXIT=0**） |
| `message-rpc` 49 vs 93 | `HC-51` | ⚠️ **未消解**（原文刻意保留历史值）⇒ 同上纪律 |
| DDL 残留 51 处 | `HC-27` | ⚠️ **逐条定级已完成**（C 23 / D 28 / A 0）⇒ **数字随迁移改动漂** ⇒ 报数带当次 `check-ddl-static.py` 输出 |

### ③ 本轮新增的「写死数字」来源（**必须登记，否则下一轮又会漏**）

| 编号 | 位置 | 数字 | 它统计什么 | 谁维护 | 核实方法 |
|---|---|---|---|---|---|
| `HC-60` | `docs/RoundPlan/_INDEX.md` 的「轮次执行状态」表 | **Round 1~7 的 7 个状态格** | 每轮的执行结论（✅ / 🟡 / 🔴） | 合并收口执行者（主线） | 逐行读本表 + 对 `_task-universe.md` 的任务状态列 |
| `HC-61` | `docs/RoundPlan/round-06/regression-report.md`（Round 6 唯一产出物） | **全量回归条数 / 0F / 0E / 4S / 模块数 / 墙钟** ＋ **27 门逐个真实退出码** | Round 6 收口回归的规模与门禁读数 | 合并收口执行者（主线） | `mvn -o -B -DargLine=-Xmx2g clean verify` ＋ **27 门逐个取退出码（先落变量，不用管道末段）** |
