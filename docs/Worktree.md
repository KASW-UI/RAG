<!-- @new 2026-10-23 21:4x · worktree 并发工作流（配套改写 AGENTS.md §5.3） -->

# worktree 并发工作流（2026-10-27）

> **本文件是操作手册**，AGENTS.md §5.3 只留一行指针。
> 起因：原 §5.3 写「不建独立 worktree、需要隔离时用 `git stash`」——那条在**串行**方式下成立，
> 但 2026-10-23 起对齐批次改为**多域并发**，`git stash` 在共享工作区下不可用（stash 是工作区级状态，
> 多个 agent 会互相偷），故本文件取代原规则。

---

## 一、为什么需要

`git worktree` = **同一个 Git 仓库，多开出几个独立目录，各装一份代码，互不干扰**。

与「手动 clone 第二份」的对比：

| | 手动 `git clone` | worktree |
|---|---|---|
| `.git` | 完整的第二份 | **文件**，内容是一行指回主仓库的路径 |
| 磁盘占用 | 历史全复制一份 | 几乎为零（共享对象库） |
| 对方 commit 你看得到吗 | ❌ 手动 `fetch` | ✅ 立刻可见（同一 reflog） |
| push 冲突 | 两边各自 fetch，易打架 | Git 自己挡 |

**「只建分支」是不够的**：分支名有了，目录还是一个，切分支会带着工作区未提交改动走或被 Git 拦下。并发靠的是**目录隔离**。

---

## 二、建（一条命令同时建分支 + 建目录）

```bash
cd "/Users/pr/Documents/java&golang/RAG"
git fetch upstream

for d in article comment common favorite follow hot like message task users-user users-admin; do
  git worktree add "../RAG.worktrees/$d" -b "align/$d" do-my-own-gh
done

git worktree list
```

- `-b align/<域>` —— 顺手把**本地分支**也建了，**不必先去 GitHub 网页建分支**，push 时才出现在 `upstream`。
- `do-my-own-gh` —— 基线。⚠️ 建之前它必须干净；主目录有未提交改动时切分支会被 Git 拦。
- 目录名不能用斜杠（`users/user` 会被当路径分隔）⇒ 写作 `users-user`。

建完之后（**以下是 2026-10-23 那一轮的「一域一工作树」历史形态**）：

```
RAG/                    ← 主工作区，停在 do-my-own-gh
RAG.worktrees/          ← 11 个工作树（在 RAG 外面，不进版本库）
├── article/            ← align/article
├── comment/            ← align/comment
├── common/             ← align/common
├── favorite/  follow/  hot/  like/  message/  task/
├── users-user/         ← align/users-user
└── users-admin/        ← align/users-admin
```

> ⚠️ **这段是历史示例**：2026-10-27 起该形态**已全部拆掉**（含 `common/`、`users-user/`、`users-admin/`），
> **现状 = 下方「波次版」的 G1~G4 四棵工作树**（2026-10-27 01:44 重整）。
> ⇒ 其中旧的 **`global` 全域工作树（`align/global`，覆盖除 favorite 外的所有域）已弃用** ——
> **理由（Jason 2026-10-27 定）**：**一个工作树跨多个域 ⇒ 粒度与操作内容不可控** ⇒ 不再作为干活的地方。
> ⇒ `global` 域的活现在**拆成两处**：**各域自己的 `<域>-common` 对账**（域内待办归各域，在 G1~G3 里做）
> ＋ **`global-common` lane**（只管 `service/common/**` 这**一个**共享层，粒度是窄的）。

⚠️ `service/common` 被 **16 个组件**依赖（article/comment/favorite/follow/hot/like/message/task 各自的 api+rpc），
它是请求保护层。它不依赖任何其他域 ⇒ 并发自由度最高，值得先跑。
但反过来：**若 common 挖出新漂移，那 16 个组件的 api 全要复查。**

### ✅ **波次版：G1~G4（2026-10-27 01:44 用户定 · 2026-10-28 已全部合入主干）**

```bash
cd "/Users/pr/Documents/java&golang/RAG"
git fetch upstream

git worktree add "../RAG.worktrees/g1-users-follow"                  -b "align/g1-users-follow"                  do-my-own-gh
git worktree add "../RAG.worktrees/g2-message-like-task"             -b "align/g2-message-like-task"             do-my-own-gh
git worktree add "../RAG.worktrees/g3-article-comment-favorite-hot"  -b "align/g3-article-comment-favorite-hot"  do-my-own-gh
git worktree add "../RAG.worktrees/g4-service-common"                -b "align/g4-service-common"                do-my-own-gh

git worktree list
```

⇒ **4 棵** = **一个波次一棵**（不再是「一个域一棵」）。语义与写权：

| 波 | 分支 / 工作树 | 覆盖 | 独占写权 |
|---|---|---|---|
| **G1** | `align/g1-users-follow` | `users`（**不含 `users-common`**）+ `follow` | `service/users/{user,admin}/**` ＋ `service/follow/**` ＋ `docs/DRIFT/{users,follow}/**` |
| **G2** | `align/g2-message-like-task` | `message` + `like` + `task` | `service/{message,like,task}/**` ＋ `docs/DRIFT/{message,like,task}/**` |
| **G3** | `align/g3-article-comment-favorite-hot` | `article` + `comment` + `favorite` + `hot` | `service/{article,comment,favorite,hot}/**` ＋ `docs/DRIFT/{article,comment,favorite,hot}/**` |
| **G4** | `align/g4-service-common` | **共享层**：`service/common` + `service/users/users-common` | `service/common/**` ＋ `service/users/users-common/**` ＋ `docs/DRIFT/global/vs-common.md` |

当前布局：

```
RAG/                          ← 主工作区，停在 do-my-own-gh
RAG.worktrees/                ← 4 个波次工作树
├── g1-users-follow/                 ← align/g1-users-follow
├── g2-message-like-task/            ← align/g2-message-like-task
├── g3-article-comment-favorite-hot/ ← align/g3-article-comment-favorite-hot
└── g4-service-common/               ← align/g4-service-common
```

⚠️ **域波顺序**：**G1 → G2 → G3**（契约 DAG 定序 = **合并收口序**）—— ⚠️ **G1/G2/G3 三棵并行跑，不是串行**。
⚠️ **G4 不在域波顺序里** —— 它是**常设的共享层执行者**（**2026-10-27 02:23 Jason 定**）：
**先于 G1 就位 standby**，**随时接收** G1/G2/G3 的反馈单 ⇒ **执行落码** ⇒ 跑**全量回归** ⇒ 回执反馈方
⇒ **落码窗口常开**；G1~G3 期间 `service/common` + `users-common` **只读、只登记反馈单**。
⇒ 🔴 **三棵都没反馈 ⇒ G4 就没活干**（standby 等单）；⚡ **G4 可开子会话并行加速，上限 10 个**（落码**按文件切** ⇒ 零重叠）。
⇒ ✅ **撤销「基线不可变 / 结论作废」**：**收口回归在波次末尾跑** ⇒ 天然含 G4 至今的改动
⇒ 唯一窗口 = **收口回归跑完 ～ 合并进主干之前** ⇒ 该窗口内共享层变了 ⇒ **重跑一次收口回归**（AGENTS.md §5.3e）。
⇒ 波次表 / 依赖 DAG ⇒ [`docs/DRIFT/_cross/cross-domain-coupling.md`](DRIFT/_cross/cross-domain-coupling.md) §二·§三。

⚠️ **写权机器守门**：`tools/difftest/check-wave-lane-ownership.py`
⇒ 断言 **G1~G4 白名单两两不重叠** + **共享文件无人拥有** + **`favorite` 只归 G3** + **共享层只归 G4**
+ **白名单路径必须命中真实文件** + **分支必须真实存在** ⇒ `EXIT=0` 才算合规。

⚠️ **建完立刻确认写权面**（否则回并会撞 **add/add 冲突** 或**半盲**）：

```bash
ls ../RAG.worktrees/g4-service-common/docs/DRIFT/global/vs-common.md
python3 tools/difftest/check-wave-lane-ownership.py     # ⇒ EXIT=0
```

⇒ 缺文件 = 基线早于脚手架落地那次提交 ⇒ 先 `git merge do-my-own-gh` 再开工。

⚠️ **旧形态（已撤）**：~~10 条 `align/<域>-common` lane~~ —— 2026-10-27 01:44 **工作树全部撤掉**。
其**方法与逐域台账**（9 条机制面轴 A1~A9 + `docs/DRIFT/<域>/vs-common.md`）**仍然有效**，
但**改在 G1~G3 四棵工作树里做**（阶段 1 只读对账 ⇒ 不落码）。
⇒ 总纲 ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](DRIFT/_cross/domain-common-lanes.md)。

---

## 三、纪律（硬约束）

### 0. 🆕 本轮目标 = 两份标准文件（2026-10-28 用户定）

**四棵工作树（G1~G4）的唯一目标**由两份标准文件给定（政策出处 ⇒ AGENTS.md §5.3g）：

| 文件 | 是什么 | lane 怎么用 |
|---|---|---|
| [`PhasedGoal.md`](PhasedGoal.md) | **阶段 1 准入线** = P0 九条 / P1 机制 / P2 不阻塞（与 `twoStage.md` §2.1 **逐字相同**） | **必读全文** —— 判「这个域够不够格进简历」 |
| [`twoStage.md`](twoStage.md) | **两阶段全流程** = 阶段 0/1/2 + §5.1~§5.7 流程义务 + §9 故障矩阵 + §10 阶段归属 A/B/C/D | **只读 §5.3 / §5.4 / §10** |

⚠️ **lane 只做阶段 1、只做本域**：
- **要改代码** ⇒ 照 `twoStage.md` §5.4（记录修改前的行为 / 具体问题 / 修改后的实现 / 变更文件 / 测试断言 / 实际结果）；
- **每条差异或暂缓项** ⇒ 照 `twoStage.md` §5.3 记满 **11 项**（含「阶段 2 的恢复或替换方案」「阶段 2 的测试场景和通过标准」「当前证据及剩余证据缺口」）；
- **每条问题** ⇒ 标 `twoStage.md` §10 的**阶段归属**（**A**=阶段 1 准入阻断 / **B**=竞争力补强 / **C**=阶段 2 建设项 / **D**=可不实现）⇒ 别把 C 类当 A 类做，也别把 A 类挂着不做。
- 🔴 **不碰** `twoStage.md` 的 §1.3 / §4 / §6 / §7（阶段 2 的业务语义逐项核对 + 12 维度生产级审查）⇒ **那是收口执行者的事**。

### 0-1. 🆕 派工清单 = **派生层施工单**（2026-10-30）

**两份标准文件给的是「标准」，派工清单给的是「这一轮具体做什么」** —— 后者 = 三目录台账的**派生层**：

| 位置 | 是什么 | lane 怎么用 |
|---|---|---|
| [`DRIFT/_cross/stage1-derivation.md`](DRIFT/_cross/stage1-derivation.md) | **人读层** —— **§四 = 可开工清单**（**§4.1** = A/B 类 · 阶段一范围内 · §4.2 = C/D 类 · 阶段二承接） | **先看 §4.1** ⇒ 只取**自己域** + **阶段归属 A/B** 的条目 |
| [`DRIFT/_cross/stage1-derivation.tsv`](DRIFT/_cross/stage1-derivation.tsv) | **机器层** —— 全量问题项 × **16 列**（`id / 域 / 状态 / 证据强度 / 优先级 / 阶段归属 / 是否阻断阶段一 / 排期指针 / 证据缺口 / rules / basis`） | 按 `domain` + `stage` + `blocking` 过滤 ⇒ 直接生成自己的待办 |
| `tools/ledger-derive/derive-stage1.py` | **生成器 + 守门**（`--check`：源台账改了而派生层没重生成 ⇒ `EXIT=1`） | 改完台账**必须**重跑，否则守门红 |

⚠️ **三条效力边界（越界即失效）**：
① **只读台账、不读产品代码** ⇒ 它是台账的**投影**、不是代码的事实 ⇒ 派工时台账写「✅ 已闭合」的**仍须**按 AGENTS.md §3.3 确认（**注解存在 ≠ 机制生效 · 跑绿 ≠ 对**）；
② **每行都带 `rules` 规则号 + `basis` 双侧 `文件:行号`** ⇒ **可回源**；`basis` 缺 Go 侧或 RAG 侧一侧 ⇒ 派生层**整行丢弃**（**不猜**）；
③ **台账结论会过期**（实测：`D-7` 的「熔断打开 ⇒ 1015」半句已死 · `G-3` 的 🔴 标题是历史快照 · `SF-001` 的 MISMATCH 早已修好）⇒ 派工前**按 `basis` 抽查**，别照抄标题。

⚠️ **派生层指标自己被修过 3 次（§七 ⑦⑧⑨）** ⇒ **「真缺口 A/B 未排期 = 0」只有在修完后才成立**；
派工前若发现「某条该有指针却没有」，先查 [`DRIFT/_cross/grading/_README.md`](DRIFT/_cross/grading/_README.md) §七，别直接当成漏排。

### 1. 台账写入边界 —— 不碰共享索引

| 文件 | 谁写 |
|---|---|
| `docs/DRIFT/<域>/<组件>.md` | ✅ 该域工作树自己写 |
| `docs/DRIFT/<域>/vs-common.md` | ✅ 该域自己写（**按域各一份**）⇒ G1~G3 各自只写自己那几个域的 |
| `docs/DRIFT/_INDEX.md` | ❌ **所有工作树都不许碰**，合并后由主会话统一更新 |
| `AGENTS.md` | ❌ 同上 |
| `docs/DRIFT/_MANIFEST.json` | ❌ 同上 |

理由：多路并发合并必撞，且事后分不清哪段台账是谁写的。
⇒ 各工作树的台账要能自证归属：**每份台账首行标注「由 `align/<域>` 工作树产出 + 基线 commit」**。

---

## 二·五、三档台账：每域必读 + 必写（2026-10-23 用户定）

`docs/` 下的三个目录**同构**，每个域下都是组件级命名（`api.md` / `rpc.md` / `common.md` / `proto.md`）。
**它们不是三个可选的档案柜，是三个职能不同的档案柜**：

| 目录 | 记什么 | 读它的后果（不读会怎样） |
|---|---|---|
| `docs/DRIFT/` | 「RAG 与 Go 不一致」——**需要对齐的差异** | 重复劳动：把已修项当新漂移重查一遍 |
| `docs/Intentional_Deviations/` | 「明知与 Go 不同、**经签字批准**」——**决定不对齐的差异** | 🔴 **最危险**：把签字偏离当新回归「顺手修回 Go 行为」，**制造新漂移** |
| `docs/Pitfalls/` | 「为什么会踩」——框架机制静默失效、绿测试锁死错误答案、基础设施误用 | 重复踩同一个坑（尤其「跑绿 ≠ 对」） |

### 开工前必读（本域 + 跨域）

```
docs/DRIFT/<本域>/                    ← 本域已有台账：哪些已修、哪些还 OPEN
docs/Intentional_Deviations/<本域>/   ← 本域签字偏离：哪些「看着像 bug」其实是决策
docs/Pitfalls/<本域>/                 ← 本域踩过的坑：这个域特有的陷阱
docs/PhaseGoal/                       ← 本轮判定项：g<N>.md（per-lane 投影）+ PhaseGoal-judge.md（权威）
docs/DRIFT/_cross/                    ← 跨模块档案（跨域不变量：共享 Flyway 账本 / 建表责任交叉 / 事实投递链路）
docs/DRIFT/_cross/stage1-derivation.md ← 🆕 派生层施工单（§三·0-1）：§四 = 可开工清单（先看 §4.1）
```

⚠️ `_cross/` 必看的原因：跨组件不变量不属于任何单个组件，**塞进组件任务里就会被漏掉**。

### 🔴 开工前置四步（2026-10-29 用户定 · 四棵 lane 强制 · **未经批准不得开工**）

> AGENTS.md §五·3b-0 的一句话规则在这里展开。**本节是「开工前」的硬门槛，不是建议。**

**为什么要有这一节**：四棵 lane 并发时，最贵的错误不是写错代码，而是**没读就写** ——
① 不知道自己的任务边界 ⇒ 越界改别域 / 抢共享层写权；
② 没读 `docs/Intentional_Deviations/` ⇒ **把签字偏离当新回归「顺手修回 Go 行为」⇒ 制造新漂移**（本仓最危险的错）；
③ 没读 `docs/Pitfalls/` ⇒ 重复踩同一个坑；
④ 没读 `docs/PhaseGoal/` ⇒ 不知道本轮判定项与阶段边界 ⇒ 做多了（预支阶段二）或做少了（漏 P0）。

| 步 | 动作 | 产出 |
|---|---|---|
| **① 定任务** | 写清**本 lane 本轮做什么 / 明确不做什么**（哪些域、哪些文件面、哪个阶段） | 「任务范围」一句话 ＋ **不做清单** |
| **② 读四档** | 读完**本 lane 面**的四档（见下表） | 「已读清单」＝ **文件路径 + 读到的关键结论** |
| **③ 回执「读完了」** | 在**会话里**发开工前置回执（模板见下）**＋** 写进本 lane 的**每个域台账的固定小节 `## 开工前置回执`**（落点见下） | 回执（会话 ＋ 台账各一份，**同一份内容**） |
| **④ 等批准** | 🔴 **未经用户（Jason）明确批准 ⇒ 不得写任何代码或台账**（**③ 的回执小节除外**；只允许读） | 批准 |

**② 的「本 lane 面」怎么读（四档 × 四棵）**

| lane | 本 lane 域 | `docs/DRIFT/` | `docs/Intentional_Deviations/` | `docs/PhaseGoal/` | `docs/Pitfalls/` |
|---|---|---|---|---|---|
| **G1** | `users`（**不含 `users-common`**）+ `follow` | `users/` · `follow/` | `users/` · `follow/` | **`g1.md`** ＋ `PhaseGoal-judge.md` 的 users/follow 行 | `users/` · `follow/` |
| **G2** | `message` + `like` + `task` | 同名三目录 | 同名三目录 | **`g2.md`** ＋ judge 的三域行 | 同名三目录 |
| **G3** | `article` + `comment` + `favorite` + `hot` | 同名四目录 | 同名四目录 | **`g3.md`** ＋ judge 的四域行 | 同名四目录 |
| **G4** | **共享层** `service/common` + `users-common` | `common/` · `global/` | `global/`（含 `common-tails.md`） | **`g4.md`** ＋ judge 的 `service/common` 行 | `common/` · `global/` |
| **全部** | —— | `docs/DRIFT/_cross/`（**四棵都要读**） | —— | `docs/PhasedGoal.md` ＋ `docs/twoStage.md` §4/§5.3/§5.4/§10 | `docs/Pitfalls/_cross/` |

⚠️ `docs/DRIFT/_cross/` **四棵都要读** —— 跨组件不变量不属于任何单个组件，塞进组件任务里就会被漏掉。
⚠️ `docs/PhaseGoal/` 是**机器生成**的（`check-phasegoal-lanes.py --write`）⇒ **`docs/PhaseGoal-judge.md` 是权威源，`docs/PhaseGoal/g<N>.md` 是它的 per-lane 投影** ⇒ 两者矛盾 ⇒ 以 judge 为准并登记（不要直接改生成物）。

**③ 开工前置回执模板**（**会话 + 台账各一份**）

```
【开工前置回执 · <lane> · <日期>】
① 任务范围：<本 lane 本轮做什么>
   不做：<明确不碰的域 / 文件面 / 阶段>
② 已读四档：
   · docs/DRIFT/<...>                → 关键结论：<...>
   · docs/Intentional_Deviations/<...> → 关键结论：<...>
   · docs/PhaseGoal/g<N>.md + PhaseGoal-judge.md → 关键结论：<...>
   · docs/Pitfalls/<...>             → 关键结论：<...>
   · docs/DRIFT/_cross/              → 关键结论：<...>
③ 待批准项：<需要用户/调度员拍板的点；若无则写「无」>
④ 等批准 ⇒ 批准后才开工
⑤ 批准记录：<批准人> · <批准时间> · <批准范围>   ← 由**调度员代填**，回执人**不得自批**
```

⚠️ **⑤ 的标题里不许出现「Jason 批准」这四个字** —— 核验命令按 `批准记录：Jason` 匹配，标题若自带该串 ⇒ 任何写了回执的 lane 都会被误判成「已获批准」（**G1 与 G4 于 2026-10-29 03:13 各自独立发现此缺陷**）⇒ 标题保持 `批准记录：<批准人>` 的**占位形态**，**只有调度员真的填了 `Jason` 才算批准**。

**④ 批准人**：**用户（Jason）** 为批准人；**调度员**负责收齐四条 lane 的回执、汇总待批准项、转达「已批准开工」。
⇒ 调度员**不得**代用户批准。

**③ 的回执落点（固定小节 ⇒ 可一条命令核验）**

| lane | 回执落点（固定小节标题 `## 开工前置回执`） |
|---|---|
| **G1** | `docs/DRIFT/users/vs-common.md` ＋ `docs/DRIFT/follow/vs-common.md` |
| **G2** | `docs/DRIFT/message/vs-common.md` ＋ `docs/DRIFT/like/vs-common.md` ＋ `docs/DRIFT/task/vs-common.md` |
| **G3** | `docs/DRIFT/article/vs-common.md` ＋ `docs/DRIFT/comment/vs-common.md` ＋ `docs/DRIFT/favorite/vs-common.md` ＋ `docs/DRIFT/hot/vs-common.md` |
| **G4** | `docs/DRIFT/global/vs-common.md` |

⇒ **本 lane 的每个域台账都要有这一节**（不是「挑一个写」）⇒ 收口时才能一眼看出「这个域开工前到底读没读」。

**核验命令（收口 / 调度用）**：

```bash
cd "/Users/pr/Documents/java&golang/RAG"
for f in docs/DRIFT/{users,follow,message,like,task,article,comment,favorite,hot,global}/vs-common.md; do
  printf "%-46s 回执=%s  批准=%s\n" "$f" \
    "$(grep -c '^## 开工前置回执' "$f")" \
    "$(grep -cE '⑤ 批准记录：Jason · [0-9]{4}-' "$f")"
done
```

⚠️ **批准列的判据是 `⑤ 批准记录：Jason · <日期>`**（**要求完整填充形态**）—— 前三版都有假阳性：
- **第一版** `Jason 批准` ⇒ 命中**回执模板自带的 ⑤ 标题** ⇒ 回执一写就 ≥1 ⇒ **假绿**（**G1 与 G4 于 2026-10-29 03:13 各自独立发现**）；
- **第二版** `批准记录：Jason` ⇒ 仍会命中**旧模板行** `⑤ Jason 批准记录：<批准人> …` ⇒ **假绿**（**G4 当场指出**）；
- **第三版** `批准记录：[^<]` ⇒ 仍会命中**散文里讨论这个判据的行**（G1 回执里写着「匹配 `批准记录：` 后有非空字符的行」⇒ 反引号命中 `[^<]`）⇒ **假绿**（**调度员 03:2x 实测发现**）。

⇒ **正确判据** = **锚定 ⑤ 标记 ＋ 要求批准人 ＋ 要求日期** ⇒ 模板行、空栏、散文提及**都不匹配**；**只有调度员真的填了 `⑤ 批准记录：Jason · 2026-…` 才算批准**。
⚠️ **两列都要 ≥1 才算「该域已开工」** ⇒ **缺回执或缺批准 ⇒ 收口那一步不得认定该 lane 已开工**，也不得把它的产出计入阶段一判定。

⚠️ **以下形式都算「没读」，回执不成立**：
- 只列文件路径、不写读到的结论 ⇒ 「读了但没读懂」；
- 只读了 `docs/DRIFT/`，漏了 `docs/Intentional_Deviations/` ⇒ **最危险的一种**；
- 用「已按 §3.1 对照」代替具体 `文件:行号` ⇒ 无法核；
- **台账里写了回执、但会话里没说** ⇒ **不算**（调度方只看得到会话）；
- **回执里自己写「已批准 / 已获批」** ⇒ **不算批准**（批准只能来自 Jason 的明示；调度员只能代填**已存在的**批准）；
- 只写「已读四档」不写**本 lane 面的具体路径** ⇒ 退回（无法核是不是只读了自己域）；
- 把回执写成「已读全部文档」这种空话 ⇒ 无可核内容 ⇒ 退回重做。

### 产出必写回（只写自己域的子目录）

| 产出类型 | 落点 | 例 |
|---|---|---|
| 发现的不一致（待对齐） | `docs/DRIFT/<域>/<组件>.md` | `docs/DRIFT/comment/rpc.md` |
| 签字批准的偏离 | `docs/Intentional_Deviations/<域>/<组件>.md` | `docs/Intentional_Deviations/comment/proto.md` |
| 踩坑（为什么会这样） | `docs/Pitfalls/<域>/<组件>.md` | `docs/Pitfalls/comment/rpc.md` |

- `<组件>` = `api` / `rpc` / `common` / `proto`，**与被审组件同名**。
- `<域>` = 微服务名；`users` 域有两套 ⇒ `users/user/` 与 `users/admin/`（见现有结构）。
- **三档之间不要互相搬运**：一条问题只在**一个**档里落笔，在另一档里**链接**过去。

### 🔴 三条硬边界

1. **三个 `_INDEX.md` 一律合并后统一更新** —— 工作树内谁都不许碰（同上节的共享面规则）。
2. **不要在 `docs/DRIFT/` 里记「决定不对齐」的偏离** —— 那是 `Intentional_Deviations` 的职能。
   判据：**这条差异要不要「修回去」？** 要 ⇒ DRIFT；不要（有意）⇒ Intentional_Deviations。
3. **空文件即坑位**（`Intentional_Deviations` 现有 26 个 0 字节占位、`Pitfalls` 10 个）——
   填了就是新坑位，不填就别建空壳文件。

### ⚠️ 域目录缺口现状（2026-10-25 更新）

`comment` / `favorite` / `follow` / `task` 四个新域的三档子目录**已建齐**（2026-10-23 43 个坑位）。
`global` 收口域的三档子目录也已建（`common-tails` / `cross-domain` / `cycle-deps` 三个议题档 + `DRIFT/global/_README.md`）。

⇒ **开工前先确认自己域的目录存在**；缺了就建，别把台账塞进别的域或 `_cross/`。

🆕 **2026-10-27**：`docs/DRIFT/<域>/vs-common.md` × **10** 已建齐
（article / comment / favorite / follow / hot / like / message / task / users / global）
⇒ 每域的 `<域>-common` 对账台账坑位**已就位** ⇒ 工作树 agent **不用再建文件**，直接开工填 §2 的 9 轴表。

### 1f. 🆕 **域内探针的落点口径 + 写权**（2026-10-29 R0·T-0.5 / T-0.6 裁定）

> **背景**：`tools/**` 在机器守门 `check-wave-lane-ownership.py` 的**任何 lane 白名单里都不存在**
> ⇒ 按机器口径 `tools/**` **无 lane 拥有**（归合并收口执行者）⇒
> ⇒ 而 R2 要求「域内探针必须落仓」⇒ 若不给 lane 授权 ⇒ 所有探针都得**串行**落在主线 ⇒ R2 直接变慢。

| 裁定 | 内容 | 落点 |
|---|---|---|
| **T-0.5 选项 A** | **域内探针目录 `tools/<kind>/<域>/` 归对应域 lane** ⇒ 按**域**授权，不按目录授权 | `tools/difftest/check-wave-lane-ownership.py` 的 `PROBE_OWNERS` 表 |
| **T-0.6** | **新探针统一落 `tools/<kind>/<域>/`**（按探针种类分层）⇒ 与已有的 `tools/proto-wire-probe/` `tools/errmsg-probe/` 一致 ⇒ 域内探针**不混进** `tools/difftest/` ⇒ **不触发** `PhaseGoal-judge.md` §6.3 的守门计数漂移 | 本节 |

⚠️ **为什么必须「按域」而不是「按目录」**：同一个域的探针天然散在**多个 `<kind>/` 下**
（例：`favorite` 域同时有 `tools/api-envelope-probe/favorite/` + `tools/errmsg-probe/favorite/` + `tools/proto-wire-probe/favorite/`）
⇒ 按目录授权 ⇒ 每加一个 `<kind>/` 就要再改一次白名单 ⇒ **迟早漏** ⇒ ⇒ 按域。

⚠️ **`tools/difftest/` 下已有的域探针**（`users` / `comment` / `task`）**不搬移** ——
搬移会动 `tools/difftest/check-*` 的计数口径 ⇒ 必须同改 `PhaseGoal-judge.md` §6.3 ⇒ **制造 B14** ⇒ **禁止**。
⇒ **新探针**按 `tools/<kind>/<域>/` 落 ⇒ **存量**留在原处 ⇒ 两种并存是**已知且被接受的**状态。

🔴 **机器守门现在会拦的**（`check-wave-lane-ownership.py` 断言 8）：
① 同一个域被两个 lane 同时授权 ⇒ 红；② 仓里现存的 `tools/<kind>/<域>/` 目录**没有主** ⇒ 红；
③ 本波（R2）**要求落地**的域级探针目录缺失 ⇒ 红（`PROBE_REQUIRED` 表 ⇒ R2 收口时逐条核销）。

```bash
python3 tools/difftest/check-wave-lane-ownership.py     # ⇒ EXIT=0 是收口判据之一
```

### 1b. 🆕 `global` 收口域的**单写权**（2026-10-25 用户定）

> **`global` 不是第 11 个微服务域，是「收口域」**：单微服务域已各自对齐完毕，
> 但**修法落在共享层、或影响多个域**的漂移被搁置 ⇒ 全部归 `global`。

| 规则 | 内容 |
|---|---|
| **单写权** | **`service/common/**` + `service/users/users-common/**` 只有 `align/g4-service-common` 能写**（旧的 `align/global` 全域 lane **已弃用** ⇒ 见 §二），G1~G3 只读 ⇒ 别人发现共享层的 bug，**记反馈单交给 G4**，不自己动手 |
| **同一次提交** | 修法同时涉及 `service/common` 与别的域代码（`service/users/**` 等）⇒ **必须同一次提交**。否则会留下「一半改完」的半截状态 |
| **落点** | `docs/DRIFT/global/`（+ `Pitfalls/global/` + `Intentional_Deviations/global/`），按**议题**分档，不是按 api/rpc/proto |
| **与 `common` 域的边界** | `common` 域问「common 自己对不对」；`global` 问「**别人**因 common 而对不对」 |

⚠️ **为什么需要单写权**：`service/common` 被 **16 个组件**依赖（article/comment/favorite/follow/hot/like/message/task 各自的 api+rpc）
⇒ 别的域同时改它 ⇒ 合并必冲突 + 语义打架。
⇒ **这正是「10 域并发对齐吞吐被卡住的那一层」** —— 不是 common 的漂移条目多，
而是**凡修法落在 common 的条目，都自动从并发任务降级为串行任务**。

### 1c. 🆕 `global` 域的**多 lane 并行**（2026-10-25）

> ⚠️ **本节记录的是 2026-10-25~26 那一轮的做法，已结束**（那 15 条 L1~L15 lane **全部已合回**），
> 且**载体 `align/global`（全域 mega 工作树）已于 2026-10-27 弃用**（Jason：粒度与操作内容不可控）⇒
> **本节仅作历史留档**；**当前唯一在跑的 global 侧 lane = `global-common`**（见 §三·1d）。
> ⚠️ **单写权 ≠ 单会话** —— `global` 的待办横跨 6 个微服务域 + `service/common` + `tools/` + 三档台账，
> 一个会话串行扛不住 ⇒ **一个域可以开多棵 lane 树并行**。
> **完整协议 ⇒ [`docs/DRIFT/global/lanes/_PROTOCOL.md`](DRIFT/global/lanes/_PROTOCOL.md)**（以它为准）。

| 规则 | 内容 |
|---|---|
| **切法** | **按「叶子文件」切**，⛔ 不是按议题切 —— 议题会跨文件（`CD-4` 同时碰 like/comment/common），**文件才是不冲突的单位** |
| **基线** | lane **从 `align/global` 的 HEAD 切**（⛔ 不是 `do-my-own-gh`）⇒ 否则看不到该域已完成的轮次，等于重做 |
| **写权** | 每条 lane 只写自己任务书里的**白名单文件** ⇒ **文件级零重叠** ⇒ 永不冲突 |
| **共享文件** | lane ⛔ 不写 `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / 4 个规范台账文件 ⇒ **合并收口执行者在合并后统一更新** |
| **lane 台账** | lane 的论证写进 **`docs/DRIFT/global/lanes/<lane>.md`**（自足片段）⇒ 避免 5 个人抢 4 个大文件 |
| **禁区** | **`favorite` 域对 `global` 零写权**（代码 + 配置 + 测试 + 台账，一律零改动） |

📌 **为什么要拆**（实测数据）：`global` 的 4 个规范台账合计 **221,405 B**
（`_README.md` 49,886 + `common-tails.md` 68,534 + `cross-domain.md` 93,286 + `cycle-deps.md` 9,699）
⇒ **单会话每轮都要重读一遍** ⇒ 单轮约 6~7 万 token 的纯台账
⇒ **瓶颈是上下文，不是写码速度** ⇒ 拆 lane 必须配「定向读」（任务书里给精确到段的指针）。

### 1d. 🆕 **波次工作树 G1~G4**：一波次一棵（2026-10-27 01:44 用户定）

> **语义**：工作树 = **一个波次**（不是「一个域」）⇒ 粒度从「域」升到「波次」。
> **总纲 ⇒ [`docs/DRIFT/_cross/domain-common-lanes.md`](DRIFT/_cross/domain-common-lanes.md)**
> · **波次表 ⇒ [`docs/DRIFT/_cross/cross-domain-coupling.md`](DRIFT/_cross/cross-domain-coupling.md) §二**

| 规则 | 内容 |
|---|---|
| **切法** | **4 棵** = G1（`users` 不含 `users-common` + `follow`）· G2（`message`+`like`+`task`）· G3（`article`+`comment`+`favorite`+`hot`）· G4（**共享层**）；分支 `align/g<N>-<slug>` ＋ 目录 `../RAG.worktrees/g<N>-<slug>` |
| **基线** | `do-my-own-gh`（主干再前进就 `merge do-my-own-gh` 一次，**文件级不重叠 ⇒ 零冲突**）⇒ 📌 **2026-10-28：四波已全部合入主干**（`02259ee` / `503a978` / `a2f2c2a` ＋ 收口修正 `061bfff`）⇒ **四条分支已 `--ff-only` 推进到 `061bfff`**（只推进指针、不重写历史）⇒ **基线 = 合并后主干** ⇒ 收口记录 ⇒ [`docs/DRIFT/_cross/cross-domain-coupling.md` §2.2.1](DRIFT/_cross/cross-domain-coupling.md) |
| **写权** | ✅ 各自表里那个面；🔴 **`service/common/**` + `service/users/users-common/**` 只读** —— 只有 `align/g4-service-common` 能写 |
| **顺序** | **域波 G1 → G2 → G3**（契约 DAG 定序 = **合并收口序**）—— ⚠️ 但 **G1/G2/G3 三棵是并行跑的，不是串行**；**G4 不在域波顺序里** —— 它是**常设的共享层执行者**，**随时**接收反馈单并落码，**并行、不阻塞** |
| **lane 台账** | **`docs/DRIFT/<域>/vs-common.md`**（**按域各一份** ⇒ 跨树**文件级零重叠**，不必抢同一个大文件） |
| **共享面** | 工作树 ⛔ 不写 `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` ⇒ **合并收口执行者在合并后统一更新** |
| **交付物** | **9 条机制面轴（A1~A9）**的**本域侧**逐轴结论（✅/🔴/🟡 ＋ **证据强度** ＋ **核对日期**）＋ **两侧同时跑**的探针（§3.3：只跑 RAG 的探针**不算数**） |
| **派单** | 发现共享层缺陷 ⇒ 本域**只登记**（**反馈单 7 项**，§5.3e）⇒ **转派 G4**；**域侧能自修的域侧修** ⇒ 不甩单、不留半截 |
| **G4 反馈链** | G1/G2/G3 遇到 **`users-common` / `service/common` 引起的问题** ⇒ **发消息给 G4**（**反馈单 7 项**）⇒ G4 **执行落码** + **回执**；🔴 **G1/G2/G3 三棵都没反馈 ⇒ G4 就没有活干**（standby 等单）⇒ ⚡ **G4 可开子会话并行加速，上限 10 个**（共享层落码**按文件切** ⇒ 零重叠；**先于 G1 就位 standby**、**落码窗口常开**） |
| **开工前置** | 🔴 **四棵 lane 开工前必做四步**（2026-10-29 用户定）—— ① **先定自己的任务**（含「不做什么」）② **读完本 lane 面的四档**（`docs/DRIFT/`·`docs/Intentional_Deviations/`·`docs/PhaseGoal/`·`docs/Pitfalls/` ＋ `_cross/`）③ **在会话里回执「读完了」** ④ 🔴 **经用户/调度员批准才开工**（未批准 ⇒ 不得写任何代码或台账）⇒ **细则 ＋ 回执模板 ⇒ §二·五** |
| **机器守门** | `tools/difftest/check-wave-lane-ownership.py` ⇒ **EXIT=0** 才算合规（写权不重叠 + 排他归属 + 路径落空 + 分支存在） |

📌 **为什么从 10 条 lane 收敛到 4 棵工作树**：10 条 `<域>-common` lane 是「**一个域一棵**」，
粒度太细 ⇒ 合并协调成本高，且**域与域之间有契约依赖**（`message`→`like`→`task`、`article`→`comment`/`favorite`/`hot`）
⇒ 细粒度并发反而互相踩。改成「**一波次一棵**」⇒ 每棵内部顺序执行、跨波按拓扑序推进。

⚠️ **建工作树前必看**：基线必须**已经包含** `docs/DRIFT/<域>/vs-common.md` 脚手架
⇒ 否则提交后回并会撞 **add/add 冲突**。
⇒ **建完立刻确认**：`ls ../RAG.worktrees/g4-service-common/docs/DRIFT/global/vs-common.md`

### 1e. 🆕 **角色边界：谁管什么 · 问题该写在哪**（2026-10-27 用户定）

> **为什么加这一节**：2026-10-27 实测踩到一次**误升级** ——
> 写权守门 `tools/difftest/check-wave-lane-ownership.py` 的 SHARED 清单注释写着
> 「**归整合者**」⇒ lane 读到后去找「整合者」⇒
> **「整合者」不是一个存在的角色**（四条 lane 分支与主干都**没有**登记过任何整合者会话）
> ⇒ lane 把一条**共享面**改动报给了一个**只管 runtime 故障**的会话 ⇒ 跨 lane 协调被误触发。
> ⇒ **教训**：**角色名会诱导升级路径** ⇒ 共享面归谁，必须写成**可指向的角色**（下表）。
>
> ⚠️ **这个词在 lane 侧还在流通**：2026-10-27 23:53 G1 的消息里仍写着「需**整合者**」⇒
> ⇒ 光把守门里的措辞改掉**不够**（lane 的既有记忆里还有这个词）⇒ ⇒ **本节就是那个词的去处**：
> 读到「整合者」的 lane ⇒ 它的诉求按**下表**分派（共享层 → G4 · 共享索引 → 合并收口 · 本域 → 域侧自修），
> **不需要**再去找任何一个会话。

| 角色 | 负责 | ⛔ 不负责 |
|---|---|---|
| **合并收口执行者** | 在**合并收口**这一步统一更新共享面：`AGENTS.md` / 三个 `_INDEX.md` / `docs/DRIFT/_MANIFEST.json` / `docs/DRIFT/_cross/**` / `docs/Worktree.md` / `tools/difftest/README.md` | ——（**它是一个步骤，不是一个常驻会话** ⇒ **没有**任何一个会话长期占着这个位置） |
| **G4**（`align/g4-service-common`） | **常设的共享层执行者** ⇒ `service/common/**` + `service/users/users-common/**` 的**代码**落码 + 接反馈单执行 | 共享索引类文件（那是合并收口执行者的） |
| **域波 lane**（G1 / G2 / G3） | 自己域的 9 轴结论 + **域侧能自修的域侧修** | 共享层代码（**只登记反馈单转派 G4**）· 共享索引类文件 |
| **运行时故障处置**（**临时角色**） | 只在 lane 出现 runtime 故障时解封：`agent configuration missing` / 队列暂停 / 会话卡 409 | **lane 的任务 · 落码 · 台账 · 跨 lane 协调 · 共享面内容 · 合并收口** |

⚠️ **三条硬边界**（照这三条做，就**不需要**去找任何特定会话）：

1. **共享面有疑问 ⇒ 不要找「整合者」**
   ⇒ 这个词已从两个写权守门的 SHARED 注释里删除（2026-10-27），换成可指向的「**合并收口执行者**」。
   ⇒ 域侧能自修 ⇒ **域侧修**；
   ⇒ 落点在 `service/common/**` 或 `service/users/users-common/**` ⇒ **登记反馈单转派 G4**（§1d「派单」行）；
   ⇒ 落点是共享索引类文件 ⇒ **记进本域台账**，**留给合并收口**统一更新。
2. **lane 的问题仍然必须落在文件里**（台账 / 反馈单）—— 但**理由不是「没有出口」**
   ⇒ **跨会话 `session send` 的方向是通的**：实测 lane → 故障处置会话在 2026-10-27 23:53 / 23:54 **两次送达**，
   出现在目标会话的上下文里 ⇒ ⇒ **「反向没有出口」是错的**（本节初稿写错，已订正）。
   ⇒ ⚠️ **但送达不是保证**：同一天 22:33 那次已被 `Session send was admitted`（`mode: "started"`），
   而**目标会话的上下文里查不到那条落地记录** ⇒ ⇒ **「我发了」≠「对方看到了」**。
   ⇒ 🔴 **判据不能用队列行数**：`local_runtime_queue_items` 的行数是**目标会话忙闲的函数** ——
   目标**忙** ⇒ `mode: "queued"`（落队列，带 `queuePosition`）⇒ 行数 +1；
   目标**闲** ⇒ `mode: "started"`（直接起一个 turn，**不落队列**）⇒ 行数 **+0**。
   ⇒ ⇒ **「队列 0 行」不能读成「没送到」**（初稿正是用它得出「反向没有出口」这个**错误结论**）。
   ⇒ ⇒ **正确判据 = 目标会话的上下文/回合里有没有那条**（transcript 里出现 `cross-session-message` 块）。
   ⇒ ⇒ **所以结论仍要落在文件里** —— 理由是「**送达是尽力而为、丢失是静默的**」，不是「没有出口」。
3. **临时角色不承接 lane 的活**
   ⇒ 故障处置会话的职责**只有**解 runtime 故障（`agent configuration missing` / 队列暂停 / 会话卡 409）
   ⇒ 它**不落码、不写台账、不做跨 lane 协调、不碰共享面、不做合并收口**
   ⇒ ⇒ **请把结论写进文件**，而不是交给某个会话。

### 2. 域内顺序：`rpc → common → api → proto 消费`

§3.5 的硬约束，在并发语境下**依然生效**——只是从「域与域之间串行」变成「工作树内部串行」。
Go 侧边界是 api→rpc，rpc 没验对就先验 api，产出的是「RAG api 行为正确」的假结论。

### 3. `do-my-own-gh` 只在最后一步接受合并

- 中间产物全走 `align/<域>` 分支
- 每个域跑完先 `git push -u upstream align/<域>`，再 `git merge --no-ff align/<域>`
- ⇒ `do-my-own-gh` 始终「随时可启动」（§3.3 那条纪律的实践形态），合出问题 revert 一个 merge commit 即可

### 4. 基线漂移要重新对齐

工作树从某个 commit 切下后，那个 commit 不变。若期间主工作区合了别的域：

```bash
cd ../RAG.worktrees/<域>
git merge do-my-own-gh        # 文件不重叠时是干净的
```

⚠️ **审计结论要带基线 commit**（§6.2：无核对日期的断言按 `UNKNOWN` 处理）。
基线变了，之前那轮结论的适用范围就变了。

---

## 四、⚠️ 已知冲突：基线与并发占用（2026-10-23 实测）

### 建树时的基线

本文件初版写于 HEAD `d27791e`。**该基线已过期** —— users 域的 25 个未提交改动已提交，
HEAD 前进到 `61e6d7a`（`5977c64` / `0a5aa0e` / `61e6d7a` 三个 commit）。

⇒ **每次建工作树前必须 `git fetch upstream` 并确认 HEAD**，不要照抄本文件里的 commit 号。
台账里引用的基线 commit 必须与实际对齐（§6.2：无核对日期的断言按 `UNKNOWN` 处理）。

### 主工作区被占用时的通用规则

并发会话各自有工作树时，**共享面只有两类，任何工作树都不得触碰**：

| 共享面 | 为什么 |
|---|---|
| `docs/DRIFT/_INDEX.md` · `AGENTS.md` · `docs/DRIFT/_MANIFEST.json` | 多路合并必撞，且事后无法区分台账归属 |
| 主工作区 `RAG/`（停在 `do-my-own-gh`） | 归主会话与其他域所有；在其上「签出」会带走别人的未提交改动 |

⚠️ **实测教训（2026-10-23 21:4x）**：另一个并发会话曾用 `git checkout` 把本会话对 `AGENTS.md` 的编辑整体回滚。
⇒ **在共享文件上的改动要尽早提交**，不要长期挂在工作区里等合并。

### `service/common` 的特殊地位

`service/common` 被 **16 个组件**依赖（article/comment/favorite/follow/hot/like/message/task 各自的 api+rpc），
是请求保护层。它不依赖任何其他域 ⇒ 并发自由度最高，值得先跑。
但反过来：**若 common 挖出新漂移，那 16 个组件的 api 全要复查。**

🆕 **2026-10-27：这条「共享层」现在有了一个专门的并发协议** ——
`align/g4-service-common` 单写 `service/common/**` **+ `service/users/users-common/**`**（**唯一**），
G1~G3 **只读**共享层侧、各自负责对账自己那一域看到的现象（§三·1d）⇒
**「谁都能发现、只有一个人能改」** 这个约束从口头约定变成了台账里的硬规则 **+ 机器守门**。

⚠️ **「共享层」不止 `service/common` 一个**：`service/users/users-common` 也被 **6 个外域 api** 依赖
⇒ 它是**第二个共享层** ⇒ 写权同样归 G4（AGENTS.md §5.3f）⇒ **物理位置不搬**（避免大范围 import 改动）。

---

## 五、拆（干完撤掉）

```bash
git worktree remove ../RAG.worktrees/task
git branch -d align/task
```

`remove` 前必须确认该目录无未提交改动，否则 Git 会拦（这是好事）。

🆕 **拆波次工作树（G1~G4）**：

```bash
git worktree remove ../RAG.worktrees/g4-service-common
git branch -d align/g4-service-common
```

⚠️ **先确认已合回**（与「删之前逐个确认未提交=0 且已合回」同款纪律）：

```bash
git merge-base --is-ancestor align/g4-service-common do-my-own-gh && echo "已合回"
```

⚠️ **删之前先确认那棵树的写权面已清空**：若它曾声明过 `service/common/**`，
⇒ 把它从 `tools/difftest/check-wave-lane-ownership.py` 的 `ACTIVE` 档**同步移走**
⇒ 否则守门会**盯一个不存在的分支** ⇒ **恒绿假绿**（2026-10-27 实测踩到）。

⚠️ 历史上的 4 个 `row/*` 工作树已确认**真合并**（`git merge-base --is-ancestor` 逐个验过），
`RAG.worktrees/` 下的目录可清理，与新建工作树不冲突（名字不撞）。

---

## 六、命令速查

| 目的 | 命令 |
|---|---|
| 看挂了哪几个 | `git worktree list` |
| 建（分支+目录一起） | `git worktree add "<路径>" -b "<分支>" do-my-own-gh` |
| 建波次工作树 | `git worktree add "../RAG.worktrees/g<N>-<slug>" -b "align/g<N>-<slug>" do-my-own-gh` |
| 确认脚手架在位 | `ls ../RAG.worktrees/g4-service-common/docs/DRIFT/global/vs-common.md` |
| **验写权（机器）** | `python3 tools/difftest/check-wave-lane-ownership.py` |
| **取自己的派工清单（施工单）** | `python3 tools/ledger-derive/derive-stage1.py --report`（只打印统计不写盘）· 人读版 = `docs/DRIFT/_cross/stage1-derivation.md` §四 |
| **重生成施工单**（改完台账必跑） | `python3 tools/ledger-derive/derive-stage1.py` |
| **验施工单没漂移** | `python3 tools/ledger-derive/derive-stage1.py --check`（源台账改了而派生层没重生成 ⇒ `EXIT=1`） |
| 推 feature 分支 | `git push -u upstream align/<域>` |
| 合回主干 | `git merge --no-ff align/<域>` |
| 同步基线 | `git merge do-my-own-gh`（在工作树内） |
| 撤 | `git worktree remove <路径>` + `git branch -d <分支>` |

> ⚠️ IDE 里也能做（Git 工具窗口 ⌘9 → 右键分支 → `Git > 新建工作树…`），
> 但多建时终端循环更快、不易点错。**不要在主目录上点「签出」**——那会带走另一个会话的未提交改动。
>
> ⚠️ **同名分支已存在时**（如旧的 `align/favorite` 仍被某个工作树占用）：
> `git worktree add` 会报「branch already checked out」⇒ 先 `git worktree remove` + `git branch -D`
> （**确认无独有提交、无未提交改动后再删**）。