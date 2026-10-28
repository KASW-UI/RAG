# R3 · 数据层与守门扩面

> **目标**：**P0 #3 → ✅**；把「**从来没有机器核过的那几层**」补上（索引 / 唯一约束 / 触发器 / proto 域覆盖 / 错误码域覆盖）。
> **前置**：R0（T-0.5 tools 写权）。**后继**：R4。
> ⚠️ **本轮可能「冒出新差异」** —— 这是**预期内**的（此前那几层根本没核过）⇒ 冒出的**按域派回**，**不在本轮当场修**。

---

## T-3.1 🔴【主线 + G4】9 域 DDL `--indexes` 逐域跑（**首次机器核**）

**为什么**：`check-ddl-static.py` **默认不开 `--indexes`** ⇒ 索引 / 唯一约束 / 触发器**全部未查** ⇒ §3.1 对照清单的**最后两项至今没有机器核过** ⇒ P0 #3 因此只能判 🟡。
⚠️ **裸跑只查 `users` 域** ⇒ **必须逐域显式传 `--domain`**。

- **落点**：`docs/DRIFT/<域>/vs-common.md`（结论入各自域台账）
- **写权**：**主线**跑 + **G4** 协助（`service/common` 那一域的 DDL 归 G4）
- **验收**：
  ```bash
  for d in users article hot like message comment favorite follow task; do
    python3 tools/difftest/check-ddl-static.py --domain "$d" --indexes
  done
  ```
  ⇒ **9/9 域都有机器结论**（`--indexes` 打开）⇒ 差异按域登记
- ⚠️ **预期会冒差异**（首次核这一层）⇒ **登记到 R5**，不阻塞本轮

---

## T-3.2【G4】G-9 —— DDL 守门比的是「活库」，不是「仓库里的 `.sql`」

**问题**：`check-ddl-static.py` 比的是**活库里实际存在的表结构** ⇒ **迁移文件改了但未重跑时，脚本仍显示「一致」** ⇒ 迁移脚本本身漂了它发现不了。

**要做的**：加一条「源码 `.sql` ↔ 活库」的**双向**校验（或在命令里**显式声明比对基准**）。

- **落点**：`tools/difftest/check-ddl-static.py`（**G4 写权**？⇒ ⚠️ `tools/**` 无 lane 拥有 ⇒ 按 T-0.5 裁定；若裁定给 lane，则落 G4）
- **验收**：① 故意改一个 `.sql`（不改活库）⇒ **守门必须红**（变异必红）② 不改任何东西 ⇒ EXIT=0
- ⚠️ **不许**顺手把守门搬进 `tools/difftest/` 之外的目录（计数口径只在 `tools/difftest/check-*` + `tools/check-ledger-*` + `tools/check-agents-budget.py`）

---

## T-3.3【G3】G-6 —— proto 落仓静态守门从 4 域扩到 **10 域**

**现状**：只覆盖 `user` · `admin` · `task` · `comment` ⇒ **`follow` / `like` / `hot` 连静态守门都没有** ⇒ 「proto 漂了**不会红**」。

- **落点**：`tools/difftest/check-proto-static.sh`（按 T-0.5 写权）· 结论入各域台账
- **写权**：**G3**
- **验收**：① 10 域全跑 ⇒ EXIT=0 ② **变异必红**：改一个 proto 字段号 ⇒ 守门必红 ③ `check-phasegoal-consistency.py` 仍 EXIT=0（守门**数量**没变 ⇒ §6.3 无需改）
- ⚠️ **改 proto 源 ⇒ 会改动生成代码 ⇒ 会动断言** ⇒ 这是本轮**唯一**可能动行为的地方 ⇒ 变异测试用**临时副本**，不留在仓内

---

## T-3.4【G3 + G4】G-7 —— 错误码双侧逐字节对拍从 2 域扩到 **9 域**

**现状**：`tools/errmsg-probe/` 只有 `favorite` + `hot` ⇒ **9 域里只覆盖 2 域**。

- **落点**：`tools/errmsg-probe/<域>/run-parity.sh`（照 favorite 骨架）
- **写权**：**G3**（article / comment / hot / favorite / follow 侧）+ **G4**（共享层那一份）· ⚠️ 具体按域分工，须在开工前写进各自台账
- **验收**：① 9 域各有一份 `run-parity.sh` 且跑通 ② **变异必红**（改一条错误码文案 ⇒ 对拍必红）③ 已签字偏离（`SD-` 族）**按白名单豁免**且**白名单逐条有理由**（⚠️ 白名单是假绿源 ⇒ 白名单本身要能审）

---

## T-3.5【G4】G-10 —— `check-jar-executable.sh` 的「未构建 ⇒ 跳过且不计失败」缺口

**现状**：实跑**只检查 5/20 模块**却返回绿。

**要做的**：改成「未构建即失败」或输出「**已检查 N/M**」并把 M 纳入退出码。

- **落点**：`tools/difftest/check-jar-executable.sh`
- **写权**：**G4**（按 T-0.5 裁定）
- **验收**：① 只构建 5 个模块 ⇒ **必须红**（或明确报「已检查 5/20」）② 全量构建 ⇒ EXIT=0

---

## T-3.6【G3 · 可延 R5】G-19 —— proto 的「消费方式」取证

**范围**：`optional` presence · 代码里的**字段号硬编码** · Jackson 配置 ⇒ `docs/DRIFT/_cross/proto-verification.md:40-46` 的「三档回归网」**只覆盖文件层** ⇒ AGENTS.md §3.4 的效力边界在 **10 个 proto 上全部成立**。

⚠️ **本条不阻断阶段一**（judge.md §九 明写「不改变任何组件的行为判定」）⇒ **允许延到 R5** ⇒ 但 §十一 交付物 4「十二维度验收表」的**维度 3（服务发现与 RPC 通信）**会因此留一个「未取证」格子 ⇒ 若要交付物 4 完整，本轮做掉更好。

- **落点**：`service/<域>/<域>-proto/**` 的契约测试（`ArticleProtoContractTest` 已示范）+ 各域台账
- **写权**：**G3**（proto 域侧）· 按域分工
- **验收**：每个 proto 组件至少一条断言到「presence / 字段号 / Jackson 形态」的用例 + **变异必红**

---

## T-3.7【G4】G-14 / G-16 —— 台账内部一致性两条

- **G-14**：`like` 域 **8 条 Unresolved（U-01~U-08）**台账未标关闭（U-04 已注明闭环）⇒ 逐条销号或明确列为「取证缺口」
  - ⚠️ 落点在 **like 域** ⇒ **G2 写、G4 不写** ⇒ 本条改派 **G2**（G4 只需提供销案依据）
- **G-16**：`service/common` 台账自相矛盾（`:36` 写「3 个 NOT EQUIVALENT」，`:645` 枚举列了 **4 个包**）⇒ 基数订正
  - **落点**：`docs/DRIFT/global/vs-common.md` ⇒ **G4 写**
- **验收**：两处**逐字一致**（用 `diff` 或 sha256 比对，不肉眼）

---

## R3 停止点

- ☐ **P0 #3 ✅**（9 域 `--indexes` 机器结论齐全 + G-9 源码↔活库校验在位 + **变异必红**）
- ☐ **G-6** 闭合（proto 静态守门 10 域 + 变异必红）
- ☐ **G-7** 闭合（错误码对拍 9 域 + 白名单可审 + 变异必红）
- ☐ **G-10** 闭合（jar 守门不再「未构建即绿」）
- ☐ G-14 / G-16 台账内部矛盾订正完毕（逐字比对过）
- ☐ `check-phasegoal-consistency.py` / `check-phasegoal-lanes.py` ⇒ EXIT=0
- ☐ **本轮冒出的新差异已按域登记**（进 R5 清单，**不阻塞**）

⚠️ **本轮禁止**：改 proto **源文件**（除变异测试的临时副本）· 把守门搬进 `tools/difftest/`（会触发 §6.3 计数漂移 = B14）· 顺手修冒出的新差异。
