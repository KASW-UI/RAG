# R0 · 解锁轮

> **目标**：把「不拍板就不能开工」的门槛一次清掉，让 R1 / R2 / R3 三轨能无阻塞并行。
> **本轮不写产品代码**（唯一例外：T-0.6 若裁定探针落点，可先落 1 个目录骨架）。
> **前置**：无。**后继**：R1 / R2 / R3 全部依赖本轮。

---

## T-0.1 🔴【Jason 必拍】`R3 ⑥` 判定口径

**问题**：`PhaseGoal-judge.md` §1.1 的 **R3 ⑥** 只排除三类（已签字偏离 / 跨组件残留 / 取证缺口），
**没给「C 类阶段二暂缓项」留出口** ⇒ 而 25 条共享层差异按 `twoStage.md` §10 定级后**绝大多数是 OBSERVABLE**
⇒ 天然落 **C 类** ⇒ ⇒ **C 类没有出口 = 阶段一永远判不通过**。

| 选项 | 做法 | 代价 | 风险 |
|---|---|---|---|
| **A（推荐）** | 在 R3 ⑥ 增加**第四类排除**：「按 §10 定级为 **C 类**、且已按 §5.3 **登记完整（11 项齐全）**者」 | 改动面最小（只加一句排除）⇒ **25 条不必现在修** | 需确认 G4 已把 25 条的 §5.3 登记补齐（`6c48959` 已做 25 条 CF/LOG/SF 定级 ⇒ **待复核完整性**） |
| **B** | 不改 R3 ⑥，把 25 条逐条修掉或转签字 | 阶段一最「干净」 | 工作量落 G1/G2/G4 ⇒ **+2~3 轮**；OBSERVABLE 类改动收益低 |
| **C** | 把阶段一准入改为「只要求 **A/B 类**闭环，C 类仅登记不判失败」 | 语义最正 | 与 `twoStage.md` §10 的 C 类定义**直接冲突** ⇒ 要改 `twoStage.md`（共享面）⇒ 影响面最大 |

⚠️ **A 与 C 的区别**：A = **基线判定口径**承认「已完整登记的 C 类」；C = **改规范本身**的准入规则。两者都会牵动四条 lane 矩阵重算。

**必须一并订正（无论选哪个）**：`PhaseGoal-judge.md:217` 两处陈旧 ——
「共享层 **27 条**编号未修」→ 真欠账 **25 条**（机械 23 + 无行号盲区 2）；
「唯一硬阻塞」表述需按定级结果改写（**33 条是守门假阳性**，不是缺陷）。

- **落点**：`docs/PhaseGoal-judge.md` §1.1（**主线写**，lane 不写）
- **验收**：改动后 `python3 tools/difftest/check-phasegoal-consistency.py` ⇒ **EXIT=0**
- **阻塞**：**T-0.1 未决 ⇒ R4 不得开始**（R4 要重算矩阵，口径没定 = 白算）

---

## T-0.2 🔴【Jason 批】四条 lane 候选任务批量批准

> 形态 = 「批 / 不批 / 批哪几条」，**不是**「怎么批」⇒ 一句回执即可。

| lane | 候选任务 | 条数 | 落点 | 备注 |
|---|---|---|---|---|
| **G1** | 25 条真欠账中的 **G1 侧 23 条**（15 版本号 + 5 行号越界 + 3 路径不精确） | 23 | `service/users/**` | 全部**注释面** ⇒ 零行为变更 |
| **G2** | 25 条真欠账中的 **G2 侧 2 条** | 2 | `service/{like,task}/**` | ⚠️ 原清单 6 条里**另 4 条 `processor.go:82` 是守门假阳性** ⇒ **候选收敛为 2 条** |
| **G3** | **H-A1** hot proto wire 探针 · **A-1** article 双侧活体 · **F-1** favorite-rpc 整域双侧活体 | 3 | `tools/**` + 域内 | H-A1 落点 `tools/**` ⇒ 受 T-0.5 裁定约束 |
| **主线** | 补 `PINNED` 两行 · G2 的 `/tmp` 抽验脚本落仓（二选一） | 2 | `tools/g3-anchor-check/` | `tools/g3-anchor-check/` 已随 G3 合入主干 ⇒ 主线补，**G4 零落码** |

⚠️ **不建议**把 G2 的 `/tmp` 脚本与 G3 的 `check-anchors.py` 合并 —— 合并 = 搬进 `tools/difftest/` ⇒ **必须同改 `PhaseGoal-judge.md` §6.3** ⇒ 制造 B14。**本轮只做二选一，不做搬移。**

---

## T-0.3【裁定 · 不用 Jason】`admin-api` 的 createAdmin 出处指向不存在的文件

**现象**：`service/users/user/admin-api/…/AdminCreateService.java:20` 引 `service/user/admin/api/internal/logic/admin/createadminlogic.go`
⇒ **该文件在 Go 仓根本不存在**（真身只在 rpc 侧 `service/user/admin/rpc/internal/logic/createadminlogic.go`，126 行，`:44-46` = 两行 `strings.TrimSpace`）。

**要裁的**：admin-api 的 `createAdmin` 对应 Go 的**哪个入口**（api↔rpc 边界问题）⇒ 裁定前**不许盲修**（盲修 = 把引用改到一个仍然不对的文件）。

- **落点**：`service/users/user/admin-api/**`（域侧自修）
- **验收**：裁定结论写进 `docs/DRIFT/users/vs-common.md` ⇒ 该文件引用改为**真实存在**的 Go 入口
- **执行者**：**G1**

---

## T-0.4【范围钉死 · 不用 Jason】P0 #4 的判据自相矛盾

**现象**：`PhaseGoal-judge.md` §七 P0 #4 一边写「探针只覆盖 5 个域（article / message / hot / shared-layer / favorite）」，
一边又写「`article` 域（失败路径最密集的域）**没有失败路径探针**」⇒ **两句话互斥** ⇒
⇒ **「P0 #4 还差多少」的范围当前不可确定** ⇒ **R2 的工作量在钉死前无法估准**。

**要做的**：实跑一遍探针，列出**每个探针实际覆盖哪些域**，据此改写 P0 #4 的覆盖清单。

```bash
bash tools/semantic-probe/run-parity.sh      # message 解析语义
bash tools/json-body-probe/run-parity.sh      # hot JSON body
bash tools/breaker-probe/run-parity.sh        # shared-layer 熔断
bash tools/errmsg-probe/run-parity.sh         # article 错误码
bash tools/difftest/run.sh                    # users 双侧
bash tools/difftest/comment/run_diff.sh       # comment 23 步矩阵
bash tools/difftest/run-task-http-parity.sh   # task HTTP 44 步
```

- **落点**：`docs/PhaseGoal-judge.md` §七 P0 #4 行（**主线写**）
- **验收**：覆盖清单与实跑结果**逐条对齐**；article 域「有没有失败路径探针」有**明确一句结论**
- **执行者**：**主线**（R2 的工作量依赖它 ⇒ 尽早做）

---

## T-0.5【写权裁定】`tools/**` 归谁

**事实**：机器守门 `check-wave-lane-ownership.py` 的**任何 lane 白名单都不含 `tools/**`**
⇒ 按机器口径 **tools 无 lane 拥有**（CLOSEOUT §8.7 / §9.8-P2）⇒
⚠️ 而 §8.2 原文写的「tools 写权归 G4」**与机器口径不符** ⇒ 以机器口径为准。

| 方案 | 做法 | 代价 |
|---|---|---|
| **A（推荐）** | **主线**先在 `check-wave-lane-ownership.py` 补一条机器白名单：「域内探针目录 `tools/<kind>/<域>/` 归对应域 lane」 | 一次性成本小 ⇒ 之后 **lane 自己落探针**（并行） |
| **B** | `tools/**` 全部由**主线**落码，lane 只交内容 | **串行** ⇒ R2 直接变慢 |

- **落点**：`tools/difftest/check-wave-lane-ownership.py`（**主线写**）
- **验收**：`python3 tools/difftest/check-wave-lane-ownership.py` ⇒ **EXIT=0**
- **阻塞**：**T-0.5 未决 ⇒ R2 的探针落码任务全部停摆** ⇒ 优先做

---

## T-0.6【口径统一】探针落仓位置

**事实**：仓内现有**两种**落点并存 ——
`tools/difftest/<域>/run_diff.sh`（users / comment / task）· `tools/<probe-kind>/<域>/run-parity.sh`（favorite / errmsg / proto-wire）。

**要定的**：新落点统一用哪一种（建议：**按探针种类分层** `tools/<kind>/<域>/`，与已有 proto-wire / errmsg 一致 ⇒ 域内探针不混进 `tools/difftest/` ⇒ **不触发 §6.3 守门计数漂移**）。

- **落点**：写进 `docs/Worktree.md`（共享面 ⇒ **主线写**）或本目录 `_INDEX.md`
- **验收**：R2 起所有新探针按同一口径落仓

---

## T-0.7【开工前置】四 lane 的「⑤ 批准记录」

**动作**：Jason 在四个 lane 会话内明示「继续 / 批」⇒ 收口执行者按 §10.4 的**代填规则**落码并留痕
（授权来源 = 批准人 + 时间 + 消息 id ⇒ 写进字段本身 ⇒ 后人可独立复核）。

- **落点**：各 lane `docs/DRIFT/<域>/vs-common.md` 的 `## 开工前置回执` 小节（**各自域 lane 写自己的**）
- **验收**（**终版判据，注意不是 `grep 'Jason 批准'`** —— 那个字面量会自匹配模板 ⇒ 恒真）：
  ```bash
  grep -cE '⑤ 批准记录：Jason · [0-9]{4}-' docs/DRIFT/<域>/vs-common.md   # 每 lane 自己的那份
  ```

---

## R0 停止点

**以下全部有书面结论 ⇒ R1 / R2 / R3 同时开工：**

- ☐ T-0.1 R3 ⑥ 口径已拍（写进 `PhaseGoal-judge.md` §1.1 + 一致性自检 EXIT=0）
- ☐ T-0.2 四 lane 候选任务已批（批准记录已落，判据见 T-0.7）
- ☐ T-0.3 admin-api createAdmin 出处已裁定
- ☐ T-0.4 P0 #4 覆盖清单已按实跑改写
- ☐ T-0.5 `tools/**` 写权已裁定 + 白名单已落 + 写权门 EXIT=0
- ☐ T-0.6 探针落点口径已统一
- ☐ T-0.7 四 lane 批准记录 4/4 已落

⚠️ **R0 未完成前，允许「只读勘察 + 范围钉死」，不允许改产品代码 / 台账结论。**
