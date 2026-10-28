# R4 · 基线订正 + 交付物 + 冻结 + 复判（**阶段一通过在这一轮**）

> **目标**：把判定基线推到**与现状一致**，产出阶段一交付物，得出 **「通过」** 结论。
> **执行主体**：**主线（合并收口执行者）主导**，四条 lane 供料。
> **前置**：**R1 ∧ R2 ∧ R3 全部完成**（矩阵是这三轮的产物 ⇒ 没落地就重算 = 白算）。
> ⚠️ 本轮是**唯一**能改共享面（`PhaseGoal-judge.md` / `docs/PhaseGoal/**` / `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / 根级文件）的一轮。

---

## T-4.1【主线】基线订正 **B1~B13**（13 处）

**清单不在本文复制** ⇒ 逐条见 `docs/CLOSEOUT-2026-10-29.md` §一（B1~B14 表，B14 已完成）。

**顺序有依赖**：
1. **先**按 T-0.1 的结论改 **B8**（`PhaseGoal-judge.md:217`）⇒ 口径变了才谈得上重算
2. **再**改 B1~B7 / B9~B13（各条自带「现文 → 应改为 → 依据」三列 ⇒ **逐条照抄 + 核对依据行仍有效**）

- **落点**：`docs/PhaseGoal-judge.md`（**主线写**）
- **写权**：**主线**
- **验收**：`python3 tools/difftest/check-phasegoal-consistency.py` ⇒ **EXIT=0**（它会抓「矩阵 ↔ 聚合 ↔ 计数 ↔ 路径存在性」四类不一致）

---

## T-4.2【主线】共享索引与跨域记录订正（G-11 / G-12 / G-18 / G-20）

| 编号 | 订正什么 | 落点 |
|---|---|---|
| **G-11** | `docs/DRIFT/_INDEX.md` + `component-coverage-matrix.md` 对 `favorite` 域仍写「零台账 / 从未对照」⇒ **已被实测推翻** | `docs/DRIFT/_INDEX.md` · `docs/DRIFT/_cross/component-coverage-matrix.md` |
| **G-12** | 5 份台账的「文件头核对日期 / 组件判定」已被正文推翻（`like/api.md` `like/rpc.md` `message/api.md` `message/rpc.md` `users/*`）⇒ 台账侧加「当前态指针」 | 各域台账文件头（⚠️ 域侧文件 ⇒ **各自 lane 写**，主线只加口径） |
| **G-18** | `hot-proto` 判定记录位置统一（`:59` 与 `:67` 自相矛盾）⇒ 裁定 + 回填 | `docs/DRIFT/hot/vs-common.md`（**G3 写**） |
| **G-20** | `docs/DRIFT/_cross/proto-verification.md:45` 已过期（写「脚本一个都没覆盖」，实际已覆盖 5 个） | `docs/DRIFT/_cross/proto-verification.md`（**主线写**） |

- **验收**：四处**逐条**改完 + `python3 tools/check-ledger-tables.py` ⇒ EXIT=0

---

## T-4.3【主线】重跑生成器 → 校验 → 自洽

```bash
python3 tools/difftest/check-phasegoal-lanes.py --write     # 重新生成 docs/PhaseGoal/g{1..4}.md
python3 tools/difftest/check-phasegoal-lanes.py              # 只校验 ⇒ EXIT=0
python3 tools/difftest/check-phasegoal-consistency.py        # 基线自洽 ⇒ EXIT=0
```

⚠️ **`--write` 必须用当前基线** ⇒ **不能**用任何一棵 lane 树的旧生成器（否则会把 G4 的 `--domain users` 错名写回去 —— 那正是 **假绿**来源）。

- **验收**：三条命令全 EXIT=0 + **四条 lane 矩阵的判定与基线一致**（人工抽 4 个组件对一眼）

---

## T-4.4【主线】主干卫生（D1 / D3）

- **D1**：`TRY/alignment-standard-a.md` 在主干工作树里是「已删除未提交」⇒ `git add -u` 提交该删除（内容已迁入 `docs/Worktree.md`，全仓零引用）
- **D3**：`PhaseGoal-judge.md` 的**失效锚点** ⇒ `python3 tools/check-doc-links.py` ⇒ **EXIT=0**
- **D2**：`AGENTS_副本.md`（untracked 旧稿）**调度员已清** ⇒ 确认干净即可

---

## T-4.5【主线】阶段一交付物 **1~6 成文**（`twoStage.md` §十一）

⚠️ **这是最容易被当成「收尾顺手」而漏掉的一块** —— 仓内目前只有 **per-domain** 的 `stage1-admission.md`（users / follow 两份）+ `PhaseGoal-judge.md` + 各域 `vs-common.md` ⇒ **全仓级 6 份交付物尚未成文**。

| # | 交付物 | 素材从哪来 | 谁写 |
|---|---|---|---|
| **1** | 原始项目工程基线报告 | `PhaseGoal-judge.md` §二 矩阵 A-1（39 行定位与规模）+ §三 A-2 + `docs/DRIFT/_cross/*` | 主线 |
| **2** | 阶段 1 简历项目验收总览 | judge.md §八 聚合（C-1~C-4）+ §七 P0 九条 | 主线 |
| **3** | 阶段 1 核心业务链路与技术机制分析 | `twoStage.md` §4.2 的**三条**核心链路（Go↔RAG 双侧） | 主线（lane 供料）|
| **4** | **阶段 1 十二维度验收表** | `twoStage.md` §七 的 12 个维度 | 主线 |
| **5** | 阶段 1 差异与暂缓项清单 | 各域 `vs-common.md` §5.3 的 **11 字段**条目 + G4 已定级的 25 条（`6c48959`）+ §十 的 A/B/C/D 归属 | 主线汇总 |
| **6** | 阶段 1 最小可行实施计划及验收结果 | `twoStage.md` §5.2 计划 + §5.4 改造记录 + §5.5 验证记录 | 主线 |

🔴 **交付物 4 的口径警告**：
**十二维度 ≠ judge.md 的「12 条跨组件不变量」**（后者是矩阵 C）⇒ 交付物 4 要逐维度按 **`twoStage.md` §2.1（阶段一等级）**评，**不是** §2.2（阶段二等级）⇒ 否则会把「生产级能力」算成「阶段一未过」⇒ **这正是 T-0.1 那个坑在交付物层的翻版**。

🔴 **交付物 5 的 11 字段**：§5.3 要求每项含 11 个字段（唯一编号 / Go 侧文件方法契约 / 原始行为 / 阶段一当前行为 / 具体差异 / 暂缓理由 / 影响 / 是否阻断阶段一 / 阶段二恢复方案 / 阶段二测试与通过标准 / 证据与缺口）⇒ **「不得只写『后续优化』」**。

- **落点**：`docs/`（建议 `docs/Stage1/` 目录，主线写）
- **验收**：6 份文件**都在**且**逐项字段齐全** ⇒ 抽查 3 项暂缓项，11 字段无缺

---

## T-4.6【主线】冻结基线（`twoStage.md` §5.7）

**动作**：阶段一完成后**冻结**当前基线（判定矩阵 + 台账 + 交付物）⇒ 后续变更走「阶段二 / 变更申请」流程。

- **落点**：`docs/PhaseGoal-judge.md` §十 变更记录（追加冻结条目）+ 交付物 2
- **验收**：冻结条目带**日期 + 判定结论 + 基线版本号**

---

## T-4.7【主线】阶段一结论复判 ⇒ **「通过」**

**改 `PhaseGoal-judge.md` §8.4** 的结论（当前 ❌「阶段一未通过」⇒ 目标 ✅「阶段一通过」）。

**复判前逐条核对 S1-1 ~ S1-6**（见 `_INDEX.md` §一）⇒ **每条要有证据强度**。

```bash
mvn -o -B -DargLine=-Xmx2g clean test          # 全量回归（必须带 clean）
python3 tools/difftest/check-surface-parity.py  # 对外契约面
python3 tools/difftest/check-phasegoal-consistency.py
python3 tools/difftest/check-phasegoal-lanes.py
python3 tools/difftest/check-wave-lane-ownership.py
python3 tools/check-doc-links.py
python3 tools/check-agents-budget.py
```

- **验收**：① 上述命令**全 EXIT=0** ② §8.4 结论 = 「**通过**」 ③ 复判依据**逐条**对应 S1-1~S1-6
- ⚠️ **「跑绿」≠「判对」** —— 守门全绿是**必要**条件；结论仍要逐条对判据核（§3.3 的纪律）

---

## T-4.8【主线】`AGENTS.md` §7.2 登记 `docs/RoundPlan/`

⚠️ **`AGENTS.md` 余量只有 32 B** ⇒ 登记**必须字节中性或更小**（同一次编辑里删掉等量重复句）⇒ 否则 `check-agents-budget.py` 直接红。

- **落点**：`AGENTS.md` §7.2 文档档表
- **验收**：`python3 tools/check-agents-budget.py` ⇒ EXIT=0 **且** 余量未变负

---

## R4 停止点 = **阶段一完成**

- ☐ B1~B13 基线订正完毕，一致性自检 EXIT=0
- ☐ G-11 / G-12 / G-18 / G-20 订正完毕
- ☐ 生成器重跑 + 四条 lane 矩阵与基线一致
- ☐ D1 / D3 主干卫生完成，链接门 EXIT=0
- ☐ **交付物 1~6 全部成文**（交付物 5 的 11 字段齐全）
- ☐ §5.7 冻结基线完成（带日期 + 版本号）
- ☐ **§8.4 结论 = 「通过」**
- ☐ `AGENTS.md` 登记完成且预算门 EXIT=0
- ☐ **`_INDEX.md` §五 状态表：R0~R4 全部 = 已完成**（每轮停止点的验收命令都实跑过且全绿）

⇒ **以上全绿 ⇒ 阶段一完成。**
