# ⚠️ 历史计划索引（旧 R0~R5）—— 已归档，不是当前权威入口

> **当前权威入口 = [`_INDEX.md`](_INDEX.md)**（新计划 · 7 个正常轮次 · Round 1~7）。
> **本文件保留的是 2026-10-29 那版旧计划（R0~R5）的原始总索引** —— 一字未改，作为历史依据与审计留痕。
> ⚠️ **本文件里的结论已被 Round 3 复判推翻**：旧 §一 的「S1-1~S1-6 全部 ✅ 满足」与旧 §五 的「R4 = 阶段一通过」**不再成立** ⇒ 当前阶段一结论 = **❌ 未通过**（两个 A 类阻断）。
> ⚠️ **不要按本文件执行任何任务** —— 旧 40 条编号任务的去向见 [`_migration.md`](_migration.md)。

---

# 阶段一轮次规划（RoundPlan）

> **本目录 = 阶段一完成的唯一权威清单。**
> **四条 lane 把 R0~R4 全部做完 ⇒ 阶段一完成（判定结论 = 「通过」）。**
> R5 = 预留兜底轮，条件触发，空转则不做。

- **写权**：本目录归**合并收口执行者**（主线）写；四条 lane **只读**。
  ⇒ 与 `docs/PhaseGoal/**`、`docs/PhaseGoal-judge.md` 同属「机器/共享面」，lane 不写、避免并发撞车。
- ✅ **本目录已登记进 `AGENTS.md` §7.2**（与 `docs/PhaseGoal-judge.md` 同一行 · commit `568aa1e5`）⇒ **R4·T-4.8 已满足** ⇒ 无需再动 `AGENTS.md`（**余量仅 34 B** ⇒ 任何改动必须字节中性或更小）。
- **上游依据**：`docs/CLOSEOUT-2026-10-29.md`（收口清单）· `docs/PhaseGoal-judge.md`（判定基线 v3）· `docs/PhasedGoal.md`（阶段一准入线）· `docs/twoStage.md`（两阶段规范）· 各 lane `docs/DRIFT/<域>/vs-common.md` §开工前置回执。
- ⚠️ **台账优先于本文**：任一「已修/已闭合」断言以对应域台账与 `PhaseGoal-judge.md` 为准；本文只管**顺序与验收**。

---

## 一、阶段一完成判据（**6 条同时成立**才算完成，缺一不算）

| # | 判据 | 判定依据 | 落地轮 |
|---|---|---|---|
| **S1-1** | **P0 九条 = ✅ 9/9** ⇒ ✅ **已满足**（**2026-10-29 · v7 汇总**：✅ 9 / 🟡 0 / 🔴 0 / ⚫ 0）—— 上一版唯一的 🟡（**#5「按 README 真起一次栈」**）**已实跑闭合**（`./manage.sh up` **EXIT=0** ＋ `register`/`login` 真打穿 ＋ 带/不带 token 的 200/401 ＋ `psql` 直查落库 ⇒ commit `0b707b39`） | `PhaseGoal-judge.md` §七 | R4·T-4.7 |
| **S1-2** | **C-1 核心 6 域全 ✅** ⇒ ✅ **已满足**（**6/6** · 2026-10-29 · `article-rpc` R2 → R3 ⇒ `article` 转 ✅）⇒ 唯一曾经的阻塞组件 `article-rpc` 已收口 | 同上 §8.2 | R2·T-2.1 |
| **S1-3** | **C-2 零 🔴 组件** ⇒ ✅ **已满足**（2026-10-29 · 共享层 `service/common` 由 🔴 上调 **🟡**（R2→R3）+ `DRIFT-CF-018` 共享层半边已修 + **28 项逐条定级：A 类 0**） | 同上 §8.2 | R1（25 条）+ R2（证据）|
| **S1-4** | **C-3 跨组件不变量 12 条（X-1 ~ X-12）无 🔴** ⇒ ✅ **已满足**（2026-10-29 · **12 条逐条无 🔴**；X-11 的硬阻断「**空签名密钥可起栈**」**已消解**（P0 #8 ✅ · 残留 = B 类 `S1-USERS-002` ⇒ 不计入）· **X-12 已实跑闭合**（P0 #5 ✅）⇒ ⇒ **P0 九条全绿 ⇒ C-3 满足**） | 同上 §8.2 | R1·T-1.1 / T-1.3 |
| **S1-5** | **twoStage §5.7 冻结基线 + §11 交付物 1~6 成文** | `twoStage.md` §5.7 / §十一 | R4·T-4.5 / T-4.6 |
| **S1-6** | **判定结论 = 「通过」**（当前 ❌ 未通过）+ 守门全绿 | 同上 §8.4 | R4·T-4.7 |

### 复算命令（S1-6 的判据，逐条可跑）

```bash
# ── 全量回归（基线推进后必须带 clean）──────────────────────────────
mvn -o -B -DargLine=-Xmx2g clean test

# ── 守门全跑（退出码先落变量再打印）──────────────────────────────
for g in tools/difftest/check-*.py tools/check-ledger-*.py tools/check-agents-budget.py; do
  out=$(python3 "$g" >&1); rc=$?
  printf "%-52s EXIT=%s\n" "$(basename "$g")" "$rc"
done

# ── 对外契约面（10 域）────────────────────────────────────────────
python3 tools/difftest/check-surface-parity.py

# ── 基线自洽 + per-lane 矩阵（生成 → 校验）────────────────────────
python3 tools/difftest/check-phasegoal-consistency.py
python3 tools/difftest/check-phasegoal-lanes.py --write
python3 tools/difftest/check-phasegoal-lanes.py

# ── DDL 逐域（⚠️ 裸跑只查 users 域！索引/约束/触发器需 --indexes）───
python3 tools/difftest/check-ddl-static.py --domain <域> --indexes

# ── 写权门（阶段一期间必须一直绿）──────────────────────────────
python3 tools/difftest/check-wave-lane-ownership.py

# ── 链接门 / 预算门 ─────────────────────────────────────────────
python3 tools/check-doc-links.py
python3 tools/check-agents-budget.py

# ── 出处守门（判据：EXIT=0 可信；EXIT=1 不可信，见 §8.7 方向性结论）──
python3 tools/g3-anchor-check/check-anchors.py --all
```

⚠️ **口径提醒**：`check-anchors.py` 的 **EXIT=0 ⇒ 无漂移**可信；**EXIT=1 ⇒ 有漂移不可信**（实测假阳性率 59%）⇒ 派工前必须先补 `PINNED` 再重跑。

---

## 二、轮次总表

| 轮 | 名称 | 主目标 | 执行主体 | 依赖 | 预计 |
|---|---|---|---|---|---|
| **R0** | 解锁轮 | 把「不拍板就不能开工」的门槛一次清掉 | **Jason 拍板** + 主线执行 | — | 0.5 轮 |
| **R1** | 硬阻断清零 | P0 #5 / #8 两条 🔴 清零 + 出处真欠账 25 条 | 主线 + G1/G2 并行 | R0 | 1 轮 |
| **R2** | 活体证据补齐 | P0 #1 / #2 / #4 三条 🟡 → ✅（**最重**） | G1/G2/G3 并行 | R0（可与 R1 重叠） | 1.5~2 轮 |
| **R3** | 数据层 + 守门扩面 | P0 #3 → ✅ + 无机器核过的层补上 | G3/G4 + 主线 | R0 | 1 轮 |
| **R4** | 基线订正 + 交付物 + 冻结 + 复判 | **阶段一通过** | 主线主导，四 lane 供料 | R1 ∧ R2 ∧ R3 | 1~1.5 轮 |
| **R5** | 预留兜底轮 | 前三轮跑出的新欠账按域派回 + 复判 | 四 lane | 条件触发 | 0~1 轮 |

**并行结构**（R0 之后三轨并行，R4 汇合）：

```
        ┌──────── R1（硬阻断） ────────┐
R0 ─────┼──────── R2（活体证据）───────┼────► R4（复判 = 阶段一通过） ────► (R5 预留)
        └──────── R3（数据层/守门）─────┘
```

⚠️ **R4 不能提前**：R4 要重算四条 lane 矩阵（`check-phasegoal-lanes.py --write`），**矩阵是 R1/R2/R3 的产物** ⇒ 三轨没落地就重算 ⇒ 白算。

---

## 三、写权速查（**动手前先对这张表**）

| 面 | 拥有者 | 依据 |
|---|---|---|
| `service/{users/user,users/admin,follow,message,like,task,article,comment,favorite,hot}/**` | **各自域 lane**（域侧能自修的域侧修） | AGENTS.md §5.3f |
| `service/common/**` + `service/users/users-common/**` | **G4**（唯一） | AGENTS.md §5.3e/§5.3f |
| `tools/**` | 🔴 **无 lane 拥有**（机器口径）⇒ **T-0.5 裁定后执行** | `check-wave-lane-ownership.py` 白名单不含 `tools/**`（CLOSEOUT §8.7·§9.8-P2） |
| `docs/DRIFT/<域>/**` | **各自域 lane**（只写自己域） | AGENTS.md §5.3b |
| `docs/DRIFT/_cross/**` · `AGENTS.md` · 三个 `_INDEX.md` · `_MANIFEST.json` · `docs/Worktree.md` | **合并收口执行者**（主线） | CLOSEOUT §二·C1 |
| `docs/PhaseGoal-judge.md` + `docs/PhaseGoal/**`（机器生成物） | **合并收口执行者**（主线） | AGENTS.md §3b + 生成器归属 |
| 根 `README.md` · `docker-compose.yml` · `.env.example` | **合并收口执行者**（无人拥有） | CLOSEOUT §二·C1 |
| `docs/RoundPlan/**` | **合并收口执行者**（本目录） | 本文件 |

⚠️ **域侧能自修的域侧修；共享层缺陷一律转派 G4**（反馈单 7 项格式）⇒ **不要为了「一次性改完」越权**。
⚠️ **`AGENTS.md` 改动必须字节中性或更小**（余量 32 B）⇒ 任何新增规则都要同次删掉等量字节。

---

## 四、四条硬约束（每轮都适用）

1. **不预支阶段二**：`twoStage.md` §1.3 / §2.2 / §6 / §7（业务语义完整性 · 十二维度生产级审查）· §9 故障矩阵 · 生产流量 / SLO ⇒ **收口执行者的事，lane 不做**。
2. **登记类不升级为拍板**：`docs/Intentional_Deviations/` 的 `CAND` 族（18 条未签字 · **该时点快照** ⇒ 现值 12）**登记完就躺着** ⇒ 不重复质疑、不推拍板。
3. **一条差异 ≠ 一个缺陷**：先按 `twoStage.md` §10 定级（P0–P3 × A/B/C/D）⇒ 绝大多数可观测性差异是 **C 类**（阶段二）⇒ **不得当作「阶段一未通过」**。
4. **每轮开工前置四步**（AGENTS.md §五·3b-0）：① 先定自己的任务 ② 读完四档台账 ③ 回执「读完了」 ④ **经用户批准才开工**（**调度员不得代批准**）⇒ 本规划的 R0 就是这四步的落点。

---

## 五、轮次状态（**机器可核**，每轮结束时更新）

| 轮 | 状态 | 开始 | 完成 | 备注 |
|---|---|---|---|---|
| R0 | ✅ **已完成** | 2026-10-29 | 2026-10-29 | 7 条停止点逐条有书面结论：① **T-0.1** Jason 选 **A**（judge §1.1 ⑥ 增**第 4 条**排除「C 类阶段二暂缓项」· 一致性门 EXIT=0）② **T-0.2** 四 lane 候选任务已批 ③ **T-0.3** `admin-api` 的 createAdmin 出处已裁定 ⇒ **本轮复核**：`AdminCreateService.java:20` 现指向**真实存在**的 `service/user/admin/api/internal/logic/admin/createlogic.go:34`（该行 = `func (l *CreateLogic) Create(...)`）④ **T-0.4** P0 #4 覆盖清单已按实跑改写 ⑤ **T-0.5** `tools/**` 写权已裁定 ⇒ `check-wave-lane-ownership.py` / `check-global-lane-ownership.py` **EXIT=0** ⑥ **T-0.6** 探针落点口径统一为 `tools/<kind>/<域>/` ⑦ **T-0.7** **10 个域台账**的「开工前置回执」＋ 批准来源（Jason 明示「继续」的消息 id）全部在位 |
| R1 | ✅ **已完成** | 2026-10-29 | 2026-10-29 | ⚠️ **起栈那一条推迟到 R4 执行** ⇒ 现已通过（commit `0b707b39`）：**`./manage.sh up` EXIT=0** ＋ `register`/`login` HTTP 200 ＋ 鉴权 200/401 ＋ `psql` 直查落库 ⇒ **P0 #5 ✅**；**P0 #8 ✅**（10 个 api/rpc 模块 fail-closed 守卫 · 4×4 变异精确转红 · `check-no-prod-secrets.py` EXIT=0）；**出处真欠账 → 0**：锚点门 `FILE_MISSING` / `LINE_OOB` / `EMPTY_LINE` / `VERSION_MISMATCH` **全 0**（`AMBIGUOUS=1993` 属「同名多文件机器判不了」⇒ 工具口径内**不算待查**）；⚠️ **「零行为变更」口径**：R1 只改文档/compose ⇒ 不增删测试 ⇒ 以 **2026-10-28 的 2,860 条**为 R1 当时基线（**后续 R2/R3 新增测试 ⇒ 总数 3,294 ⇒ 增的是测试，不是行为变更**）；`check-doc-links.py` / `check-wave-lane-ownership.py` / `check-agents-budget.py` **EXIT=0** |
| R2 | ✅ **已完成** | 2026-10-29 | 2026-10-29 | **P0 #1 / #2 / #4 ✅**（like 事实投递活体复算 · article 双侧活体 3 场景**且两侧播种同一行** · 失败路径矩阵 **227 条**且变异 **13/13 精确转红** · 错误码 **9/9 域**）⇒ **`article-rpc` R2 → R3** ⇒ **C-1 核心 6 域 6/6（S1-2 成立）**；§九 **G-1 / G-2 / G-3 / G-4 销号**（各带证据强度 + 日期）；探针**全部在仓内**（`tools/api-full-parity-probe/{article,like,follow,users-common,favorite-rpc}/` · `tools/difftest/comment/` …）且**变异必红**；`check-phasegoal-consistency.py` **EXIT=0** |
| R3 | ✅ **已完成** | 2026-10-29 | 2026-10-29 | **P0 #3 ✅**（9 域 `--indexes` + `--check-source` 机器结论齐全 · **变异必红**）· **G-6**（proto 静态守门 10 域）· **G-7**（错误码 9 域 + 白名单可审 + 变异精确转红）· **G-10**（jar 守门不再「未构建即绿」）全闭合；**G-14 / G-16** 台账内部矛盾订正完毕；`check-phasegoal-consistency.py` / `check-phasegoal-lanes.py` **EXIT=0**；**本轮冒出的新差异已按域登记进 R5**（8 条欠账 + 10 张派工单）⇒ **不阻塞** |
| R4 | ✅ **已完成** | 2026-10-29 | 2026-10-29 | **B1~B13 基线订正完毕**（一致性门 EXIT=0）· **G-11 / G-12 / G-18 / G-20 订正完毕** · **生成器重跑**（4 条 lane 矩阵与基线一致）· **D1 / D3 主干卫生**（链接门 EXIT=0）· **交付物 1~6 全部成文**（交付物 5 = **51 条 / 11 字段**）· **§5.7 冻结基线完成**（judge **§十·二** · 9 项逐项 + 日期 + 判定结论 + 基线版本号）· **§8.4 结论 = 「✅ 阶段一通过」** · `AGENTS.md` 登记完成且预算门 EXIT=0（**32,734 B / 余量 34 B**） |
| R5 | 🔵 **已触发 · 执行中** | 2026-10-29 | — | 触发条件 ① 与 ③ 命中（见 [`Round-5.md`](Round-5.md) 头部）⇒ 收录 **8 条欠账**（**A 类 0** ⇒ **无一条阻阶段一**）＋ **10 张派工单**（`R5-F1` ~ `R5-F10`）；**其中 6 条 C/D 类 ⇒ 登记不修**；⚠️ **`check-ledger-selfcheck.py` 仍红 1 处**（task 域台账测试数 134 vs 实测 137）⇒ 属 task 域写权面 ⇒ **派单 R5-F9 ⇒ 不越界改** ⇒ **如实记账** |

⚠️ **状态列的判据**：一栏写「已完成」⇒ 该轮「停止点」里的**每条验收命令都实跑过并全绿** ⇒ **不是**「代码写完了」。

> ⚠️ **R1 的补记（口径诚实）**：R1 的停止点里有一条「第三方照 README 起栈实跑通过」**当时被推迟到 R4** ⇒ ⇒ 按判据，R1 在 R4 完成前**不能记「已完成」**；**现在该条已实跑且全绿** ⇒ ⇒ 2026-10-29 起记「已完成」并保留本行说明（**历史不抹掉，只补时间线指针**）。
> ⚠️ **全仓仍有 1 个守门是红的**（`check-ledger-selfcheck.py`）⇒ **不是本轮偷懒**：根因是 **task 域台账的数字过期** ⇒ 落点在 **task 域写权面** ⇒ 按 §5.3f **不越界改** ⇒ **派单 R5-F9** ⇒ **如实记账，不粉饰成全绿**。

---

## 六、任务编号规范

`R<轮>.<lane>.<序号>`，lane 取值：`G1` `G2` `G3` `G4` `MAIN`（合并收口执行者 = 主线）。
- `R1.G1.1` = 第 1 轮 · G1 lane 的第 1 件
- `R0.MAIN.3` = 第 0 轮 · 主线的第 3 件

每个任务在轮次文件里给全 5 项：**做什么 / 落点（`文件:行号`）/ 执行者 / 写权依据 / 验收命令**。

---

## 七、本规划**不做**什么（防止顺手扩范围）

- ❌ 不做阶段二的十二维度生产级审查（§1.3 / §2.2 / §6 / §7）
- ❌ 不做 §9 故障矩阵 / 生产流量 / SLO / 灾备演练
- ❌ 不改 `tools/difftest/` 守门**清单计数口径**（动了必须同改 `PhaseGoal-judge.md` §6.3 ⇒ 否则立刻制造 B14）
- ❌ 不把 `tools/g3-anchor-check/` 搬进 `tools/difftest/`（搬移必须同改 §6.3）
- ❌ 不重排已签字偏离（48 条）
- ❌ 不动 Go 真源仓（冻结、只读）
