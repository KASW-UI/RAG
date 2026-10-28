# 阶段一轮次规划（RoundPlan）

> **本目录 = 阶段一完成的唯一权威清单。**
> **四条 lane 把 R0~R4 全部做完 ⇒ 阶段一完成（判定结论 = 「通过」）。**
> R5 = 预留兜底轮，条件触发，空转则不做。

- **写权**：本目录归**合并收口执行者**（主线）写；四条 lane **只读**。
  ⇒ 与 `docs/PhaseGoal/**`、`docs/PhaseGoal-judge.md` 同属「机器/共享面」，lane 不写、避免并发撞车。
- ⚠️ **本目录尚未登记进 `AGENTS.md` §7.2**（`AGENTS.md` 实测余量仅 **32 B** ⇒ 登记必须字节中性或更小）⇒ 登记项留在 **R4·T-4.8** 与 AGENTS.md 瘦身同一次编辑。
- **上游依据**：`docs/CLOSEOUT-2026-10-29.md`（收口清单）· `docs/PhaseGoal-judge.md`（判定基线 v3）· `docs/PhasedGoal.md`（阶段一准入线）· `docs/twoStage.md`（两阶段规范）· 各 lane `docs/DRIFT/<域>/vs-common.md` §开工前置回执。
- ⚠️ **台账优先于本文**：任一「已修/已闭合」断言以对应域台账与 `PhaseGoal-judge.md` 为准；本文只管**顺序与验收**。

---

## 一、阶段一完成判据（**6 条同时成立**才算完成，缺一不算）

| # | 判据 | 判定依据 | 落地轮 |
|---|---|---|---|
| **S1-1** | **P0 九条 = ✅ 9/9**（当前 ✅3 🟡4 🔴2） | `PhaseGoal-judge.md` §七 | R1(R4? 见 R1/R2/R3) → R4·T-4.7 |
| **S1-2** | **C-1 核心 6 域全 ✅**（当前 5/6，`article` 🟡） | 同上 §8.2 | R2·T-2.1 |
| **S1-3** | **C-2 零 🔴 组件**（当前唯一 🔴 = 共享层 `service/common`） | 同上 §8.2 | R1（25 条）+ R2（证据）|
| **S1-4** | **C-3 跨组件不变量 X-1~X-10 无 🔴**（当前 X-11 安全 / X-12 构建 两条 🔴） | 同上 §8.2 | R1·T-1.1 / T-1.3 |
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
2. **登记类不升级为拍板**：`docs/Intentional_Deviations/` 的 `CAND` 族（18 条未签字）**登记完就躺着** ⇒ 不重复质疑、不推拍板。
3. **一条差异 ≠ 一个缺陷**：先按 `twoStage.md` §10 定级（P0–P3 × A/B/C/D）⇒ 绝大多数可观测性差异是 **C 类**（阶段二）⇒ **不得当作「阶段一未通过」**。
4. **每轮开工前置四步**（AGENTS.md §五·3b-0）：① 先定自己的任务 ② 读完四档台账 ③ 回执「读完了」 ④ **经用户批准才开工**（**调度员不得代批准**）⇒ 本规划的 R0 就是这四步的落点。

---

## 五、轮次状态（**机器可核**，每轮结束时更新）

| 轮 | 状态 | 开始 | 完成 | 备注 |
|---|---|---|---|---|
| R0 | ⬜ 未开工 | — | — | 等 Jason 拍板 T-0.1 |
| R1 | ⬜ 未开工 | — | — | 依赖 R0 |
| R2 | ⬜ 未开工 | — | — | 依赖 R0 |
| R3 | ⬜ 未开工 | — | — | 依赖 R0 |
| R4 | ⬜ 未开工 | — | — | 依赖 R1∧R2∧R3 |
| R5 | ⬜ 预留 | — | — | 条件触发 |

⚠️ **状态列的判据**：一栏写「已完成」⇒ 该轮「停止点」里的**每条验收命令都实跑过并全绿** ⇒ **不是**「代码写完了」。

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
