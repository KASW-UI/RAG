# Round 6 · 收口回归（把 Round 1~5 的改动一次性验干净）

> **本轮目标**：在**合并后的树**上做一次全量验证 —— 证明 Round 1~5 的改动**没有引入回归**，且**台账自洽**。
> **本轮不执行整改**（规范 §一.6 / §一.7）—— 本文件是**计划**，不是执行记录。
> **本轮是验收的前置**：`P7-T01`（Round 7）以本轮全绿为唯一入口条件。

---

## ⚠️ 本轮的三条铁律

| # | 铁律 | 为什么（都是踩过的坑） |
|---|---|---|
| **1** | **收口回归必须在「合并后的树」上跑**，不得用任一棵 lane 树，也不得用「差集推断」 | ① 每棵树只含自己域的修复 ⇒ **缺域**；② 某 lane 改了文件 ⇒ **同行号整体平移** ⇒ 同一条问题在四棵树里是**四个不同的 key** ⇒ **差集法会把一条算成两条** ⇒ 正解是**物化一棵合并树**再跑（`check-merge-tree-content.py` 已能逐步 merge-tree） |
| **2** | **取退出码必须先落变量**：`out=$(cmd 2>&1); rc=$?` ⇒ **不许**用 `cmd \| tail` 的退出码（那是 `tail` 的） | judge §六 6.3 表头已把这条写成纪律；用管道末段会把「门红了」记成「门绿了」 |
| **3** | **守门的两个方向可信度不同** ⇒ **「EXIT=0 ⇒ 没问题」可信；「EXIT=1 ⇒ 有问题」不可信** | 已实证：G3 锚点守门报「待查 56 条」⇒ **逐簇反测后 29 条（52%）是假阳性**（解析器漏一个模块 ⇒ 带版本前缀的路径解析不到 ⇒ 回退裸文件名匹配 ⇒ 撞上同名但行数完全不同的另一个文件）⇒ **红项必须回真源逐条核实**，不能直接派工 |

---

## §5.1 本轮目标与结束状态

| 项 | 内容 |
|---|---|
| **处理哪些已知问题** | ① Round 1~5 的**回归面**（全量 `mvn clean verify` + 27 守门 + 10 域双侧探针 + 生成器）② 上轮遗留的 **1 个红门**（`check-ledger-selfcheck.py` ⇒ task 台账 134 vs 137 ⇒ 归 `P4-T04`）③ **台账自洽**（`X-n` 唯一 · `DRIFT-HOTAPI-030` 一态 · Pitfalls 状态字段齐）④ **计划层面**的规范 §十二 6 组交叉检查（把「静默丢任务」的可能性在**计划层**堵掉） |
| **产生哪些可检查的结果** | ① 一份**回归报告**（`docs/RoundPlan/round-06/regression-report.md`）：全量条数 + 27 门逐个真实退出码 + 10 域探针退出码 + 生成器 diff ② 红项**逐条定性**（真红 / 假红 / 已知签字偏离 / N/A / 刻意保留）并给出**销号依据** ③ 一份**计划层交叉检查表**（§十二 6 组 ⇒ 全部有结论） |
| **为什么是下一轮的必要前提** | Round 7 要**逐条核对判据并宣布结论** ⇒ 若回归是红的，Round 7 的结论只能是「不通过」⇒ **白跑一轮** ⇒ 且 Round 7 的判定依赖**台账自洽**（否则拿过期结论当现状） |
| **本轮不做** | 不改判据（Round 3）· 不改产品行为（发现回归 ⇒ **登记 + 定级**，不当场改）· 不代用户签字 · 不改 `tools/difftest/` 的守门清单计数口径（**不得新增 `check-*.py`**）· **不做阶段二** |

---

## §5.2 开始条件

| 条件 | 状态 | 证据 |
|---|---|---|
| **Round 1~5 全部完成** | ⚠️ **必须** | 本轮是**收口**回归 ⇒ 前面 5 轮不完成就没有「收口」可言 |
| **各域 lane 改动已并入主干**（或已物化合并树） | ⚠️ **必须** | 铁律 1 |
| **RAG 栈在跑**（PG / Redis / Nacos / Kafka / MinIO / Jaeger） | ⚠️ **必须** | 双侧探针与 `check-flyway-concurrent.sh` 依赖真 PG / 真 Redis |
| **Go 真源仓可读**（只读） | ⚠️ **必须** | 双侧探针的 Go 侧 |
| ⚠️ **模块缓存可见性** | 🟡 **需先探针** | 已知本机存在**间歇性不可见**（同一路径 5 分钟内「可读 → 不可读 → 可读」）⇒ **探针不过就硬失败退出码 2**，**绝不静默降级成「这条不可核验」**（⇒ 同一份代码两次跑出两个结论） |
| **无前置任务** | ✅ | — |

---

## §5.3 逐项任务清单

### P6-T01 · 收口回归（全量 + 27 门 + 10 域探针 + 生成器 + 计划层交叉检查）

| 字段 | 内容 |
|---|---|
| **任务 ID** | `P6-T01` |
| **原始编号** | 原文无独立编号（`PhaseGoal-judge.md` §六 6.3「27 项命令」· §十二「命令清单」· 旧 `_legacy-index.md` §一「复算命令」） |
| **来源** | `docs/PhaseGoal-judge.md` §六 6.3（27 项 · 含 4 次 `check-ddl-static.py`）· §十·二 冻结基线 9 项 · `docs/DRIFT/_cross/cross-domain-coupling.md:139-147`（回归面 2411）· `docs/DRIFT/global/lanes/_PROTOCOL.md`（合并纪律） |
| **当前状态** | 🟡 **待执行**。上轮基线：全量 **3,294 条 / 0F / 0E / 4S / 52 模块 / 28:40 min**；27 守门 **26 绿 1 红**（红 = `check-ledger-selfcheck.py` ⇒ task 台账 134 vs 137 ⇒ 归 `P4-T04`） |
| **执行目的** | 把「Round 1~5 的改动没有引入回归」从**信念**变成**可复算的证据**；并把**计划层**的「静默丢任务」可能性堵掉 |
| **具体操作** | **见下方 §5.3.1 的 7 个步骤（S1~S7）** —— 每个步骤都有**独立产出物 + 独立失败处置** |
| **具体落点** | `docs/RoundPlan/round-06/regression-report.md`（**新建** · 本轮唯一的产出文件）· 报告落 `docs/RoundPlan/_final-acceptance.md` 的「回归证据」列 |
| **执行主体** | **合并收口执行者（主线）** —— 全量回归与守门是**仓级**动作，跨域写权不适用 |
| **前置条件** | Round 1~5 全部完成 ∧ 栈在跑 ∧ Go 真源可读 |
| **并行关系** | **S1（全量回归，~30 min）与 S3（双侧探针）可并行**（一个用 CPU、一个用栈）⇒ 但 **S2（27 门）与 S1 不并发**（都吃 `target/`）⇒ **S4（生成器 `--write`）必须在 S2 之后**（生成器会改 `docs/PhaseGoal/**` ⇒ 会改守门期望 ⇒ 顺序不能反） |
| **写权限** | `docs/RoundPlan/round-06/**`（主线）· `docs/PhaseGoal/**` + `docs/PhaseGoal-judge.md`（**主线** · 生成器 `--write` 会改 ⇒ **只允许 `--write` 产生的内容**，**不得手改**） |
| **验收命令** | 见 §5.4（**7 组，逐组取真实退出码**） |
| **验收证据** | ① `regression-report.md` 完整（含**每门的真实退出码**，不是「全绿」三个字）② 红项**逐条定性表** ③ §十二 6 组交叉检查**逐组结论** ④ 生成器 `--write` 前后 **diff 为空**（⇒ 机器生成物与判据一致） |
| **完成条件** | **S1~S7 全部完成** ＋ **27 门全部 EXIT=0**（或红项已按「定性表」销号并给出依据） ＋ **10 域探针全部 EXIT=0** ＋ **无「未定性红项」** ＋ 计划层 6 组交叉检查无「无去向任务」 |
| **失败处理** | ① **真红** ⇒ 按 `CT-02` 定级 + 记录，**不当场改产品代码**（规范 §一.6）⇒ 落到 `docs/RoundPlan/_unresolved.md` 第 2 类 ② **假红** ⇒ 按 `PIT-GLOBAL-032` 三步定性法（单跑复跑 → 降并行度重跑 → **不要跳过全量**）⇒ 记录定性依据 ③ **门坏了**（守门自身假绿/假红）⇒ **改门**（`tools/difftest/**` = 主线）⇒ 但**不得新增 `check-*.py`**（会动 27 的计数 ⇒ 制造 B14）⇒ 新扫描器用非 `check-*` 名（先例：`scan-bash-legacy-vars.py`） |
| **状态更新** | `docs/PhaseGoal-judge.md` §六 6.3（本轮实跑结果行）· §十 变更记录追加 v6 条目 · `docs/DRIFT/global/_README.md`（收口结论） |
| **后续任务** | `P7-T01`（Round 7 · 阶段一最终验收） |

---

#### §5.3.1 P6-T01 的 7 个步骤

| 步骤 | 做什么 | 产出物 | 失败处置 |
|---|---|---|---|
| **S1** | **全量回归**：`mvn -o -B -DargLine=-Xmx2g clean verify` ⇒ 记 **条数 / 0F / 0E / 4S / 模块数 / 墙钟** | 报告 §1 | 任何 `F`/`E` ⇒ 真红 ⇒ 定级 ⇒ **不当场改** |
| **S2** | **27 门逐个取真实退出码**（4 个非 `tools/difftest/` 的门 + `g3-anchor-check` + 4 次 `check-ddl-static.py`） | 报告 §2 的 **27 行表** | 每个非 0 ⇒ 进「红项定性表」 |
| **S2′** | **⚠️ 本轮计划期实测发现的命令形式缺陷（必须在本轮回填时修掉）** | 报告 §2′ | 见下方说明 |
| **S3** | **10 域双侧活体探针**（`tools/api-full-parity-probe/**` · `tools/errmsg-probe/<域>/run-parity.sh` · `tools/semantic-probe/run-parity.sh` · `tools/json-body-probe/run-parity.sh`）⇒ **两侧同时跑** ⇒ 逐字节比对 | 报告 §3 | 探针环境受阻 ⇒ 登记 + 写解除条件 ⇒ **绝不写「已对齐」** |
| **S4** | **生成器 `--write` + 校验**：`python3 tools/difftest/check-phasegoal-lanes.py --write` ⇒ 再跑 `check-phasegoal-consistency.py` ⇒ **`git diff` 必须为空** | 报告 §4 | diff 非空 ⇒ 说明机器生成物与判据不一致 ⇒ **先查判据**，不是先查生成器 |
| **S5** | **合并树物化 + 合并内容守门**：`check-merge-tree-content.py <4 棵 lane ref>` ⇒ 逐步 merge-tree ⇒ 每一步「那侧的改动没丢」 | 报告 §5 | 「改动丢了」⇒ **合并纪律问题** ⇒ 回 `_PROTOCOL.md` 合并纪律 |
| **S6** | **台账自洽检查**：`X-n` 唯一 · `DRIFT-HOTAPI-030` 一态 · Pitfalls 状态字段齐 · 台账三门（count / tables / selfcheck） | 报告 §6 | 任一红 ⇒ **回 Round 5**（不是在本轮硬修台账）⇒ 走 `CT-01` |
| **S7** | **计划层交叉检查**（规范 §十二 6 组）⇒ 逐组结论 | 报告 §7 + [`_final-acceptance.md`](../_final-acceptance.md) | 发现「无去向任务」⇒ **改计划**（不是只记在报告里） |

#### §5.3.1.1 S2′ · 一条**命令形式缺陷**（计划期实测抓出 · 必须本轮回填时修掉）

| 项 | 内容 |
|---|---|
| **现象** | `docs/PhaseGoal-judge.md` §六 6.3 的表格里写的是 `check-ddl-static.py --domain {follow,task,article} --indexes --check-source` ⇒ **该命令跑不通**：`--domain` 的 argparse 用 `choices=sorted(DOMAINS)`（**只接受单值**）⇒ 逗号形式被拒 ⇒ **退出码 2**（argparse 的用法错误，**不是**「已签字偏离」的 2） |
| **实测对照** | 逐域分 3 次跑：`--domain follow` ⇒ **EXIT=0** · `--domain task` ⇒ **EXIT=0** · `--domain article` ⇒ **EXIT=0** ⇒ ⇒ **结论不变**（3 域 8 维度逐项一致），**只是命令写法不可执行** |
| **危险点** | ⚠️ **退出码 2 有二义性** —— 「argparse 用法错误」与「已签字偏离」**同码** ⇒ 若不逐条看输出文本，**会把「命令写错」误记成「已签字偏离」** ⇒ 这正是规范 §12.6 第 4 项「命令不存在、路径不正确或参数未经确认」要抓的东西 |
| **本轮动作** | ① 跑命令时**逐域分 3 次**（本文件 §5.4 已按此写）② **回填 judge §六 6.3 时把那 1 行拆成 3 行**（follow / task / article 各一行，EXIT=0）—— ⚠️ 这是**修正一条不可执行的命令形式**，**不是改判定阈值**（`X-2` 的口径「9 域逐域实跑」一字不动）③ 在 §十 变更记录里写明这次订正 |
| **验收** | ① judge §六 6.3 里**每条命令都真的可执行**（逐条实跑取真实退出码）② **退出码 2 的每一行都要能说清是「用法错误」还是「已签字偏离」**（看输出文本，不看码） |

---

#### §5.3.2 S2 的 27 门清单（**以 judge §六 6.3 为准**，不凭印象）

> ⚠️ **口径澄清（防呆）**：`tools/difftest/` 下共 **23** 个 `check-*` 脚本，但 **27** 指的是**命令条数** ⇒
> ① `check-surface-parity.py` **不计入 27**（它是 P0 #1 的判定命令，单独跑）⇒ 剩 **22**；
> ② `tools/` 根级 **4** 个（`check-ledger-count.py` / `check-ledger-tables.py` / `check-ledger-selfcheck.py` / `check-agents-budget.py`）；
> ③ `tools/g3-anchor-check/check-anchors.py --all` **1** 个；
> ④ 22 + 4 + 1 = **27** 个**脚本**；**命令行条数** = 27 + 3 = **30**（`check-ddl-static.py` 按 **4 种参数**各算一条：裸跑 / `--domain comment` / `--domain hot` / `--domain follow,task,article`）。
> ⇒ **本轮不得增删 `check-*` 脚本** ⇒ 27 这个数一变，judge §六 6.3 与 `check-phasegoal-consistency.py` 的期望就错 ⇒ **制造 B14**。

| 组 | 命令 | 上轮 EXIT | 备注 |
|---|---|---|---|
| **契约 / 产物** | `bash tools/difftest/check-proto-static.sh` | 0 | 10 域逐字节 |
| | `bash tools/difftest/check-jar-executable.sh` | 0 | 20/20 fat jar 带 `Main-Class` |
| | `bash tools/difftest/check-plaintext400-headers.sh` | 0 | **真 Tomcat** 400 头（MockMvc 测不到） |
| | `bash tools/difftest/check-flyway-concurrent.sh` | 0 | **L4 真 PG 三进程并发同库** |
| | `python3 tools/difftest/check-merge-tree-content.py <g1> <g2> <g3> <g4>` | 0 | 逐步 merge-tree（铁律 1 的机器形态） |
| **DDL（4 次）** | `python3 tools/difftest/check-ddl-static.py`（裸跑 = users） | **1** | 残留全是 `DEFAULT CURRENT_TIMESTAMP`（**刻意保留**）⇒ **A 类 0** ⇒ **不是门坏了** |
| | `python3 tools/difftest/check-ddl-static.py --domain comment --indexes --check-source` | **2** | **已签字偏离** `SD-2026-10-24-CAND-02` ⇒ **不是新漂移** |
| | `python3 tools/difftest/check-ddl-static.py --domain hot --indexes --check-source` | **3** | **N/A**（两侧都无库）⇒ **不许写成「已对齐」** |
| | `python3 tools/difftest/check-ddl-static.py --domain follow --indexes --check-source`（**逐域 3 次**） | 0 | 3 域 8 维度逐项一致 |
| | `python3 tools/difftest/check-ddl-static.py --domain task --indexes --check-source`（**逐域 3 次**） | 0 | 同上 |
| | `python3 tools/difftest/check-ddl-static.py --domain article --indexes --check-source`（**逐域 3 次**） | 0 | 同上 |
| **装配 / 写权** | `python3 tools/difftest/check-tomcat-limits.py` | 0 | 10/10 api 三旋钮一致 |
| | `python3 tools/difftest/check-grpc-interceptor-wiring.py` | 0 | 10/10 接线完整 |
| | `python3 tools/difftest/check-flyway-ledger.py` | 0 | 7 模块独立账本表 |
| | `python3 tools/difftest/check-go-json-body-predicate.py` | 0 | 静态 |
| | `python3 tools/difftest/check-grpc-runtime-classpath.py` | 0 | 静态 + baseline |
| | `python3 tools/difftest/check-no-nul-bytes.py` | 0 | |
| | `python3 tools/difftest/check-cross-domain-holds.py` | 0 | |
| **deadline 族** | `python3 tools/difftest/check-deadline-verdict.py` | 0 | 门禁**自身**有效性（fixture） |
| | `python3 tools/difftest/check-deadline-downstream.py` | 0 | ⚠️ **本轮起语义已变**（`P2-T05`）⇒ 由「漂移形态门」改为「**等值门**」⇒ 修好 E65 后它**应当绿** ⇒ 若**变红** ⇒ **`P2-T05` 没做完**，不是回归 |
| **波次 / 写权** | `python3 tools/difftest/check-global-lane-ownership.py` | 0 | `global` 历史 lane 写权 |
| | `python3 tools/difftest/check-wave-coupling.py` | 0 | 跨域耦合漂移 |
| | `python3 tools/difftest/check-wave-lane-ownership.py` | 0 | G1~G4 波次写权 |
| | `python3 tools/difftest/check-wave-role-model.py` | 0 | G4 角色措辞 |
| **台账 / 预算** | `python3 tools/check-ledger-count.py` | 0 | 守 `docs/Pitfalls/_INDEX.md` 计数 |
| | `python3 tools/check-ledger-tables.py` | 0 | 表格结构（内建 3 条变异 M1/M2/M3） |
| | `python3 tools/check-agents-budget.py` | 0 | AGENTS.md ≤ 32,768 B |
| | `python3 tools/check-ledger-selfcheck.py` | **1** | 🔴 task 台账 **134 vs 137** ⇒ 归 `P4-T04` ⇒ **本轮应已销号** ⇒ 若仍红 ⇒ **`P4-T04` 未完成** |
| **基线自洽** | `python3 tools/difftest/check-phasegoal-consistency.py` | 0 | 本文件自身 15 组自检 |
| | `python3 tools/difftest/check-phasegoal-lanes.py` | 0 | per-lane 分摊矩阵校验（**机器生成** ⇒ 不得手改） |
| **安全 / 锚点** | `python3 tools/difftest/check-no-prod-secrets.py` | 0 | 未豁免命中 0（已豁免 101 处逐条有理由/owner/删法） |
| | `python3 tools/g3-anchor-check/check-anchors.py --all` | 0 | 上轮 `OK=3208 · LINE_OOB=0 · FILE_MISSING=0 · VERSION_MISMATCH=0`；`AMBIGUOUS=1993` 是「同名多文件、机器判不了」⇒ **不计入失败** |
| **（不计入 27）** | `python3 tools/difftest/check-surface-parity.py` | — | P0 #1 的判定命令（契约面 HTTP 72 ≡ 72 + gRPC 71 ≡ 71） |
| | `python3 tools/difftest/scan-bash-legacy-vars.py` | 1 | **新增扫描器 · 不计入 27** ⇒ 1 处属 hot 域写权面 ⇒ 归 `P4-T05` |

---

## §5.4 具体验收命令（本轮整体）

```bash
cd "/Users/pr/Documents/java&golang/RAG"
# ═══ S0 前置探针：环境不可见 ⇒ 硬失败，绝不静默降级 ═══
# ⚠️ Go 真源 / 模块缓存可能间歇性不可见 ⇒ 先探针，不过就 exit 2
for p in "/Users/pr/Documents/java&golang/Sea-RideTheWind_golang" \
         "$HOME/go/pkg/mod"; do
  [ -d "$p" ] || { echo "INPUT_NOT_VISIBLE: $p"; exit 2; }
done
# ⚠️ 本机是 macOS bash 3.2 ⇒ 没有 timeout / declare -A

# ═══ S1 全量回归（~30 min）══════════════════════════════════
out=$(mvn -o -B -DargLine=-Xmx2g clean verify 2>&1); rc=$?
printf "mvn clean verify EXIT=%s\n" "$rc"
# 条数/0F/0E/4S/模块数 从 surefire XML 求和（口径见 judge §十二）

# ═══ S2 27 门逐个取真实退出码 ═══════════════════════════════
# ⚠️ 取退出码必须先落变量，不能用 cmd | tail 的退出码
for g in tools/difftest/check-proto-static.sh ; do :; done
for g in tools/difftest/check-*.py tools/difftest/check-*.sh \
         tools/check-ledger-*.py tools/check-agents-budget.py ; do
  out=$(bash -c "python3 '$g'" 2>&1); rc=$?
  case "$g" in *.sh) out=$(bash "$g" 2>&1); rc=$? ;; esac
  printf "%-52s EXIT=%s\n" "$(basename "$g")" "$rc"
done
python3 tools/difftest/check-ddl-static.py ; echo "ddl(users) EXIT=$?"
python3 tools/difftest/check-ddl-static.py --domain comment --indexes --check-source ; echo "ddl(comment) EXIT=$?"
python3 tools/difftest/check-ddl-static.py --domain hot --indexes --check-source ; echo "ddl(hot) EXIT=$?"
# ⚠️ 必须逐域分 3 次跑：--domain 的 choices 不接受逗号形式
for d in follow task article; do
  python3 tools/difftest/check-ddl-static.py --domain "$d" --indexes --check-source ; echo "ddl($d) EXIT=$?"
done
python3 tools/g3-anchor-check/check-anchors.py --all ; echo "anchors EXIT=$?"
python3 tools/difftest/check-merge-tree-content.py \
  align/g1-users-follow align/g2-message-like-task \
  align/g3-article-comment-favorite-hot align/g4-service-common ; echo "merge-tree EXIT=$?"

# ═══ S3 10 域双侧活体探针（两侧同时跑）══════════════════════
# ⚠️ 探针必须落仓 tools/<kind>/<域>/ ⇒ /tmp 不算证据
bash tools/semantic-probe/run-parity.sh      ; echo "semantic EXIT=$?"
bash tools/json-body-probe/run-parity.sh     ; echo "json-body EXIT=$?"
for d in article comment favorite follow hot like message task users; do
  out=$(bash "tools/errmsg-probe/$d/run-parity.sh" 2>&1); rc=$?
  printf "errmsg(%-9s) EXIT=%s\n" "$d" "$rc"
done

# ═══ S4 生成器 --write + 校验（diff 必须为空）═══════════════
python3 tools/difftest/check-phasegoal-lanes.py --write
python3 tools/difftest/check-phasegoal-consistency.py ; echo "consistency EXIT=$?"
git diff --stat docs/PhaseGoal/     # ⚠️ 必须为空 ⇒ 非空说明判据与生成物不一致

# ═══ S5 合并内容守门（已在 S2 跑）→ 结论写进报告 ═══════════

# ═══ S6 台账自洽 + 计划层检查 ═══════════════════════════════
python3 tools/check-ledger-count.py ; echo "ledger-count EXIT=$?"
python3 tools/check-ledger-tables.py ; echo "ledger-tables EXIT=$?"
python3 tools/check-ledger-selfcheck.py ; echo "ledger-selfcheck EXIT=$?"
python3 tools/check-agents-budget.py ; echo "agents-budget EXIT=$?"
python3 tools/difftest/scan-bash-legacy-vars.py ; echo "bash-vars EXIT=$? （非 check-* ⇒ 不计入 27）"

# ═══ S7 计划层交叉检查（规范 §十二 6 组）════════════════════
# 逐组结论写进 regression-report.md §7
#  12.1 旧 RoundPlan 覆盖 → _migration.md 逐条有去向
#  12.2 DRIFT 覆盖        → _task-universe.md + _unresolved.md
#  12.3 Pitfalls 覆盖     → 各轮「防错约束」小节
#  12.4 Intentional_Deviations 覆盖 → _unresolved.md 第 4 类
#  12.5 PhaseGoal 覆盖    → _final-acceptance.md
#  12.6 依赖与验收完整性  → _dependencies.md + _final-acceptance.md
```

---

## §5.5 可客观判定的完成条件

| # | 判据 | 怎么判 |
|---|---|---|
| 1 | 全量回归绿 | `mvn clean verify` EXIT=0，**0F / 0E**，条数写进报告（**不许只写「绿」**） |
| 2 | **27 门全部 EXIT=0** | 报告里有 **27 行**「命令 · 真实退出码」 |
| 3 | 红项零「未定性」 | 每个非 0 都有「真红 / 假红 / 已签字偏离 / N/A / 刻意保留」之一 + 依据 |
| 4 | 10 域探针全绿 | **两侧同时跑**的退出码，不是单侧 |
| 5 | 生成器 diff 为空 | `git diff --stat docs/PhaseGoal/` 无输出 |
| 6 | 台账自洽 | `X-n` 唯一 · `HOTAPI-030` 一态 · Pitfalls 0 条「文件未标状态」 |
| 7 | 计划层 6 组无「无去向任务」 | `_migration.md` 每行都有去向（迁移 / 已完成 / 已关闭 / 不属于阶段一 / 待裁定） |
| 8 | **不能算完成的情况** | ① 用「全绿」代替逐门退出码 ② 把 `check-ddl-static.py` 的 **1/2/3** 当成「门坏了」③ `check-deadline-downstream.py` 转红时**当成回归去「修回去」** ④ 在 lane 树上跑完就宣布收口 ⑤ 探针环境受阻却写「已对齐」 ⑥ **为了让门变绿而改判据** |

---

## §5.6 本轮结束检查

- [ ] **S1** 全量回归：条数 / 0F / 0E / 4S / 模块数 / 墙钟已写入报告
- [ ] **S2** **27 行**「命令 · 真实退出码」表齐全；**每个非 0 有定性**
- [ ] **S3** 10 域双侧探针退出码齐全（含**两侧同时跑**的证据）
- [ ] **S4** 生成器 `--write` 后 `git diff --stat docs/PhaseGoal/` **为空**
- [ ] **S5** 合并树内容守门：4 棵 lane ref 逐步 merge-tree ⇒ 「那侧的改动没丢」
- [ ] **S6** 台账自洽：`X-n` 唯一 · `HOTAPI-030` 一态 · Pitfalls 状态齐
- [ ] **S7** 计划层 6 组交叉检查逐组有结论
- [ ] 证据路径：`docs/RoundPlan/round-06/regression-report.md`
- [ ] 原始编号 → 最新状态：27 门逐条 · 10 域探针逐条
- [ ] 未完成任务及原因：（逐条写）
- [ ] 被阻塞的后续任务：Round 7
- [ ] 本轮新增问题及阶段一分类：（按 §十 定级 ⇒ `CT-02`）
- [ ] **是否满足进入 Round 7 的条件**：**是** —— 前提是 **S1~S7 全完成** 且 **无未定性红项**
- [ ] ⚠️ **本轮禁止**：改判据 · 改产品行为 · **代用户签字** · **新增 `check-*.py`** · 在 lane 树上宣布收口 · 把「drift 修好后门变红」当回归

---

## 防错约束

| PIT 编号 | 出处（规范位置 · 逐条可核） | 规则（摘自该 PIT 标题原文） | 约束绑定的任务 ID |
|---|---|---|---|
| `PIT-GLOBAL-032` | `docs/Pitfalls/global/cross-domain.md:1108` | 🔴 **「跑红」也可能是假红**：两类非代码原因（构建竞态 / 环境时序）—— 必须单独复跑定性 | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-038` | `docs/Pitfalls/global/cross-domain.md:1307` | 🔴 **端口占用型假红**：`BindException` ⇒ 先查「谁占着那个端口」，再查「是不是随机端口」 | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-054` | `docs/Pitfalls/global/cross-domain.md:1962` | 🔴 **「接进编排」≠「有了闸门」** —— 六种假绿（判定只写在注释里 / 退出码是意外得来的 / 守门分支短路 / 端口与就绪不跟随约定 / **施加侧空转** / **阴性观测误诊**） | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-036` | `docs/Pitfalls/global/cross-domain.md:1220` | Spring 的 `@DynamicPropertySource` **不进** context cache key ⇒ 靠「properties 恰好不同」隔离上下文是**侥幸** | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-048` | `docs/Pitfalls/global/cross-domain.md:1719` | 🔴 **变异判据本身也会假阴性 ⇒ 用「集合差分」，不要用「关键词匹配」** | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-018` | `docs/Pitfalls/global/cross-domain.md:512` | 🔴 **变异脚本的两种「假绿」：① 编译不过 ⇒ 测试没跑 ⇒ 读到旧 XML；② 红在更早的断言上 ⇒ 目标断言没被执行到** | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-029` | `docs/Pitfalls/global/cross-domain.md:952` | 🔴 **静态扫描不排除 `target/` ⇒ 构建产物的旧副本被当源码判 ⇒ 门禁恒红** | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-040` | `docs/Pitfalls/global/cross-domain.md:1425` | 🔴 **变异无效的五种形态**（规则被掩护 / 对照 flaky / 变异打错层 / 阳性对照依赖分支 / 台账引述错路径） | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-014` | `docs/Pitfalls/global/cross-domain.md:288` | 🔴 **变异测试报「没转红」时，先排除「测试压根没跑」** —— 否则会误删一个好守门 | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-031` | `docs/Pitfalls/global/cross-domain.md:1042` | 🔴 **守门断言的字符串可能同时出现在被测文件的注释里 ⇒ 断言恒真 ⇒ 变异测不出来** | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-061` | `docs/Pitfalls/global/cross-domain.md:2310` | 🔴 **「去噪」是那条判据的「计账规则」，不是「放弃检查」** ⇒ 让**另一条判据**失去覆盖 ⇒ 变异样本被相邻行吃掉 ⇒ **假绿** | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-LANE-07` | `docs/Pitfalls/_cross/domain-common-lanes.md:196` | · 守门用**全文 grep** 判措辞 ⇒ 改掉**锚点那一行**后**守门恒绿**（2026-10-27 02:23 实测） | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-055` | `docs/Pitfalls/global/cross-domain.md:2099` | 🔴 **往台账里插一行 ⇒ 可能把「表格」插塌 ⇒ 而台账的内容一个字都没丢 ⇒ 所有内容层守门都查不出来** | 约束本轮任务：`P6-T01`（S1~S7） |
| `PIT-GLOBAL-068` | `docs/Pitfalls/global/cross-domain.md:2503` | 🔴 **「跑红」也可能是环境串扰 ⇒ 共享服务发现面会把并发探针互相污染** ⇒ **跑红 ≠ 是缺陷** | 约束本轮任务：`P6-T01`（S1~S7） |
