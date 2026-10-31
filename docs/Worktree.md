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
| [`DRIFT/_cross/stage1-derivation.md`](DRIFT/_cross/stage1-derivation.md) | **人读层** —— **§四 = 可开工清单**（**§4.0 = 波次分布** · **§4.1** = A/B 类 · 阶段一范围内 · §4.2 = C/D 类 · 阶段二承接） | **先看 §4.0**（自己那棵有多少活）⇒ 再看 §4.1 ⇒ 只取**自己域** + **阶段归属 A/B** 的条目 |
| [`DRIFT/_cross/stage1-derivation.tsv`](DRIFT/_cross/stage1-derivation.tsv) | **机器层** —— 全量问题项 × **17 列**（`id / 域 / **`wave`** / 状态 / 证据强度 / 优先级 / 阶段归属 / 是否阻断阶段一 / 排期指针 / 证据缺口 / rules / basis`） | 按 **`wave`** + `domain` + `stage` + `blocking` 过滤 ⇒ 直接生成自己的待办 |
| `tools/ledger-derive/wave-map.txt` | **`wave` 列的映射数据**（域/台账文件 → G1~G4）—— 权威出处 = 本文件 §二「波次版：G1~G4」表 | 改派工面**只改这个文件**，不改代码 |
| `tools/ledger-derive/derive-stage1.py` | **生成器 + 守门**（`--check`：源台账改了而派生层没重生成 ⇒ `EXIT=1`） | 改完台账**必须**重跑，否则守门红 |

⚠️ **三条效力边界（越界即失效）**：
① **只读台账、不读产品代码** ⇒ 它是台账的**投影**、不是代码的事实 ⇒ 派工时台账写「✅ 已闭合」的**仍须**按 AGENTS.md §3.3 确认（**注解存在 ≠ 机制生效 · 跑绿 ≠ 对**）；
② **每行都带 `rules` 规则号 + `basis` 双侧 `文件:行号`** ⇒ **可回源**；`basis` 缺 Go 侧或 RAG 侧一侧 ⇒ 派生层**整行丢弃**（**不猜**）；
③ **台账结论会过期**（实测：`D-7` 的「熔断打开 ⇒ 1015」半句已死 · `G-3` 的 🔴 标题是历史快照 · `SF-001` 的 MISMATCH 早已修好）⇒ 派工前**按 `basis` 抽查**，别照抄标题。

⚠️ **`wave` 列 = 派工面（该条归哪棵工作树做）** —— **本文件的 §二 波次表就是它的权威出处**：
`G1` = users（**不含 users-common**）+ follow · `G2` = message+like+task · `G3` = article+comment+favorite+hot · `G4` = 共享层（`service/common` + `users-common` + 收口域 `global`）。
⇒ **lane 取自己那棵**（`wave` 含自己的波次号即可，**并集** `G1+G4` = 跨派工面 ⇒ 域波先做、共享层部分转 G4 反馈单）。
⇒ ⚠️ **G4 不在域波顺序里**（域波顺序 = G1→G2→G3）—— G4 是**常设执行者**，**先于 G1 就位 standby**、**落码窗口常开**。
⇒ ⚠️ **`wave` 给的是「派工面」不是「类级写权」** —— `users-common` 是**混合体**（AGENTS.md §5.3f **按类判定**）⇒ 动手前按 §5.3f 与 G4 对齐。

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

### 1g. 🆕 **回执的发送纪律：回执只在「带新事实 / 新任务 / 需要对方动作」时才发**（2026-10-30 实测 · 根治「回执乒乓」）

> **为什么加这一节**：2026-10-30 实测到一次**跨 lane 回执乒乓** ——
> G1 把反馈单发给 G4 ⇒ G4 回执 ⇒ G1 再回执 ⇒ G4 再回执 …
> ⇒ **每回一条就在对方队列里再排一条**，而 `session send` 的返回**只保证「已受理」**
> （目标**忙** ⇒ `mode: "queued"` 落队列；目标**闲** ⇒ `mode: "started"` 直接起一个 turn、**不落队列**）
> ⇒ ⇒ 两侧都在 `started` 时，**队列就变成了回执交换机** ⇒ ⇒ **每个回执都会唤起对方一次 turn（白烧 token）**，
> 且**真正的技术结论被埋在回执往复里**（结论本该落在台账里）。
>
> ⚠️ **与 §1e 硬边界 2 的分工**：那条讲「**送达是尽力而为、丢失是静默的 ⇒ 结论必须落文件**」；
> **本条讲「发回执本身要有节制」** —— **落文件 ≠ 要互相回执确认**。

**规则（lane 通用 · 硬约束）**

| 回执里有没有这三样 | 该不该发 |
|---|---|
| **新事实**（新测出的数据 / 新的结论 / 对既有结论的**订正**） | ✅ **发** |
| **新任务 / 新反馈单**（对方需要动作） | ✅ **发** |
| **需要对方动作**（要它改代码 / 回填 / 决定） | ✅ **发** |
| **只有「收到了 / 一致 / 已处理 / 已知悉」** | ⛔ **不发** |

⇒ **纯确认回执一律不发** ⇒ **台账才是事实源，回执只是通知** ⇒ ⇒ **不丢任何技术结论**。

**重复投递的入站消息怎么回**：回一句「**已处理，见 `<commit>`**」即可 ⇒
⛔ **不重跑落码** · ⛔ **不重复写台账**（重复条目会污染派生层 ⇒ 见 `docs/Pitfalls/_cross/derived-layer.md` 的 `PIT-DERIVE-01/02`）
⚠️ **重复投递是运行时的正常行为**（§1e 硬边界 2：目标忙 ⇒ 落队列 ⇒ 空闲时必被投递）⇒ **不是异常，不必上报**。

---

### 2. 域内顺序：`rpc → common → api → proto 消费`

§3.5 的硬约束，在并发语境下**依然生效**——只是从「域与域之间串行」变成「工作树内部串行」。
Go 侧边界是 api→rpc，rpc 没验对就先验 api，产出的是「RAG api 行为正确」的假结论。

### 3. `do-my-own-gh` 只在最后一步接受合并

- 中间产物全走 `align/<域>` 分支
- 每个域跑完先 `git push -u upstream align/<域>`，再 `git merge --no-ff align/<域>`
- ⇒ `do-my-own-gh` 始终「随时可启动」（§3.3 那条纪律的实践形态），合出问题 revert 一个 merge commit 即可

### 3-1. 🆕 合并收口时的「合并后必做清单」（2026-10-30 立）

> **为什么要有这一节**：有些收口项**只能在主干合入后做** —— 在 lane 工作树里做会**撞写权守门**
> ⇒ ⇒ 若不提前登记，合并完**没人记得** ⇒ ⇒ **合并收口执行者**按本清单逐项核销。
>
> ⚠️ **本清单是「收口义务」的登记处，不是差异台账** ⇒ 不进派生层（不写编号 token 首格、不写状态标记 —— 见 `PIT-DERIVE-01/02`）。

| # | 事项 | 为什么只能在合并后做 | 判据 / 核销方式 |
|---|---|---|---|
| **M-1** | **复跑 G1 的 3 个域侧守门类（13 条）**<br>· `service/users/user/user-api/.../client/UserRpcClientDeadlineCapTest.java`（5）<br>· `service/users/admin/admin-api/.../client/AdminRpcClientDeadlineCapTest.java`（5）<br>· `service/follow/follow-rpc/.../client/UserRpcClientDeadlineCapTest.java`（3） | G1 要在**自己的**工作树里 merge G4 的提交 ⇒ 而 `service/common/**` **不在 G1 写权面**（`tools/difftest/check-wave-lane-ownership.py` 的 `ACTIVE["g1-users-follow"]` 白名单）⇒ ⇒ 合并会**让写权守门判红** ⇒ ⇒ 只能在主干合入后跑<br>⚠️ **前置 = G1 先提交它那批未提交文件** | `mvn -o -B -pl <三个模块> -am test` ⇒ **13 条全绿**<br>⚠️ **重点看 follow-rpc 那条「真发一次调用」**（in-process 真 server + 故意不响应 ⇒ 客户端真阻塞 ⇒ 只能靠自己套的 **2s** deadline 收口 ⇒ 断言 `DEADLINE_EXCEEDED`）⇒ **在 G4 的出向拦截器落码后，它必须仍在 2s 收口** ⇒ ⇒ **若它红了 = 有价值的信号（拦截器把调用点自带的 2s 收紧或放宽了）⇒ 别当回归「修回去」** |
| **M-2** | **派生层重生成**（`tools/ledger-derive/derive-stage1.py`） | 四棵 lane 的台账改动**只在各自工作树/分支**里 ⇒ 主干台账在合并前**不含这一波** ⇒ ⇒ 合并前重生成 = **缺一整波** ⇒ 合并后还得再作废一次 | `python3 tools/ledger-derive/derive-stage1.py`（**真跑**）⇒ 产物与源同步 ⇒ `diff` 逐字节核对<br>⚠️ **不是** `--report` 只读模式（只读不写 ⇒ diff 恒 0 行 ⇒ 恒「无漂移」）  ⚠️ **合并后计数的预期值（供对账 · 2026-10-30 G4/G1 各自自测）**：基线 `do-my-own-gh` 宽口径候选 **2609** ⇒ G4 侧增量 **+7**（问题项 **2022 → 2021**，E66 的 🟡 假登记被消；同源组冲突 **52 → 51**；**TSV 逐字节不变 ⇒ 派工判断零影响**）⇒ G1 侧增量 **+18**（消失 0 · 本轮改动 0 个新候选）⇒ ⇒ ⇒ **合并后预期 ≈ 2634** ⇒ ⇒ ⚠️ **但以「合并后真生成的结果」为准**，不要拿它当硬期望值去判红 ⇒ ⇒ ⚠️⚠️ **取证纪律（两 lane 都栽过同一处）**：`derive-stage1.py --report` 是**只读模式 ⇒ 不写盘 ⇒ diff 恒 0 行 ⇒ 「没变」有两种成因：真没变 / **根本没生成**」 ⇒ ⇒ ⇒ **必须**「备份 → **真跑生成** → diff → 逐字节还原」，只读模式的 0 行 diff 是**假信号** |
| **M-3** | ✅ **已闭合**（2026-10-30 收口执行者 · commit `b57564dc`）· **门禁口径**（`tools/difftest/check-deadline-downstream.py` ＋ `mutate-deadline-downstream.py` 的 **M1 锚点**）· ⚠️ **【2026-10-30 第三次订正】口径已由「漂移形态门」升级为「纯等值门」**（见本行末） | ⚠️ **原判「M1 锚点待修」已查实并修完**：M1 的锚点串 `DeadlineState.bindToCurrent(deadlineEpochMs)` **在 `1f62a245`（DRIFT-COMMON-005 结案）里已被改成 `bindRemainingNanos(...)`** ⇒ ⇒ 锚点命中 **0 处** ⇒ ⇒ **M1 变异空转**（变异没发生 ⇒ 守门虽红，红的原因是锚点失配、**不是**判据被证伪 ⇒ ⇒ **该条对 `outgoing_carrier` 键已无判别力**）⇒ ⇒ **两轮定位**（都不是一次改对）：① 改到 `RpcInterceptor.handle` 的 worker 绑定点（`:568`）⇒ 锚点命中但探针仍绿 ⇒ 根因 = **观测点与绑定点不在同一条路径**（探针 handler 走 `onHalfClose`，根本不经 `handle(...)` 的线程池）② 改到 `onHalfClose` 的绑定点（`:466-480`）⇒ **精确命中**<br>⇒ ✅ 台账旁证：`docs/DRIFT/global/_README.md` 的 `E65` 行「候选修法」原文就是「共享层在 **`onHalfClose`** 里把有效期限也绑进 `GoStyleRequestDeadline`」⇒ ⇒ **独立印证锚点选对**<br>⇒ ✅ 漂移形态守门实测 **EXIT=0**（Go `failed_fast=true`/`code=4`/`outgoing_carrier=present_expired`/`sql_carrier=present_expired` ⇔ RAG `false`/`0`/`absent`/`present_expired`）⇒ ⇒ 形态与登记一致 | `python3 tools/difftest/mutate-deadline-downstream.py` ⇒ **EXIT=0（2/2 精确命中）**<br>`python3 tools/difftest/check-deadline-downstream.py` ⇒ **EXIT=0**<br>⇒ ⚠️ **口径**：出向 deadline 载体真落地后 `outgoing_carrier` 会由 `absent` 翻 `present` ⇒ ⇒ **那时本守门必红**（这是设计如此，不是回归）⇒ ⇒ 改口径**必须与 M-2 同批**<br>⇒ 🟢 **【2026-10-30 第三次订正 · 那次「必红」已发生，口径已改（主干 `e796a4ed`）】**：探针 RAG 半边改走 **devh 生产装配路径** ⇒ ⇒ 实测 Go = RAG = `failed_fast=true\|code=4\|outgoing_carrier=present_expired\|sql_carrier=present_expired`（四键逐键相等）⇒ ⇒ **漂移名单清空 ⇒ 守门由「漂移形态门」改为「纯等值门」**（四个键全部 parity）⇒ ⇒ `mutate-deadline-downstream.py` **第 4 轮重定基**：M1 拆载体绑定 ⇒ 红 `failed_fast`+`outgoing_carrier` · M2（**重锚**：旧 M2「加 deadline」在 min 语义下**天然无效**）改为「探针改用裸 channel」⇒ 红 `failed_fast` ⇒ ⇒ **2/2 精确命中** |
| **M-4** | **有意偏离候选登记**（`SD-2026-10-30-CAND-18 / -19 / -20` ＋ 🆕 **`-21`**） | 登记会描述「**RAG 侧的行为**」⇒ 合并前主干上**那个行为还不存在** ⇒ ⇒ 登记即描述一个**不存在的行为**（假偏离） | 🆕 **`-21`** = 「取消口令兜底后的**失败时刻**差异」：Go = **启动成功、连库时**失败；RAG = **启动即失败**（Spring 解析不到占位符 ⇒ DataSource bean 创建失败）⇒ ⇒ **RAG 更严的一侧** ⇒ ⇒ 按 **P0 #8** 同款口径登记（⚠️ **不是**「保留兜底」的理由 —— 保留兜底本身已判为**真漂移**并已取消）<br>| `python3 tools/difftest/ledger-deviation-count.py --strict` ⇒ EXIT=0<br>⚠️ **`G4-15a` 不登记**（前提已被 G4 自身订正为假 ⇒ 登记即造假偏离） |
| **M-5** | ✅ **已闭合**（2026-10-30 收口执行者 · commit `ac6268d4`）· **搬移清单**（`tools/drift-move.py verify`） | ⚠️ **原判「主线既有红」已修完**：旧实现的 4 个红档（`docs/DRIFT/like/rpc.md` ×2 · `docs/DRIFT/_cross/fix-round-2026-10-23.md`）**根因不是清单不符**，而是**三个判据缺陷**：① manifest 的 `label` 是自动抽的，而 `fix-round` 的哨兵是**人手写**的 ⇒ 措辞对不上 ⇒ 找不到（⇒ 加 `sentinel_label` 显式登记）② 目标文件里的搬移块**长度 ≠ 声明行数**（实测 40 段里 0 段落在文件末尾）⇒ ⇒ 块边界不能靠声明行数定（⇒ 三级回退：登记行数 → 声明行数 → 哨兵→文件末尾〔就地编辑口径〕）③ `like/api.md` 两段的哨兵在后续轮次被重写掉 ⇒ ⇒ 锚到 **git 历史 `69e3262a`** ⇒ ⇒ 现 **40/40 绿**（38 段当前文件 ＋ 2 段历史锚） ⚠️ **并补齐了它自己承诺过的变异测试**（`tools/difftest/mutate-drift-move-verify.py`，此前**不存在**）⇒ 5/5 全过 | `python3 tools/drift-move.py verify` ⇒ **EXIT=0（40/40）**<br>`python3 tools/difftest/mutate-drift-move-verify.py` ⇒ **EXIT=0（5/5）** |
| **M-6** | 🔴 **「`logout` 域内预检删除」必须与共享层 Redis 代理同一个 commit**（G1 提 · 收口执行者独立复核通过）<br>✅ **已闭合（2026-10-30 · 原子 commit 已落在主干）** ⇒ 门不是「共享层**已提交**」⇒ 而是「共享层的**效果已被运行证据证明**」 | 共享层代理在 `service/common/**`（G4 写权）⇒ 域侧那一行在 `service/users/admin/admin-rpc/**`（G1 写权）⇒ ⇒ **AGENTS.md §5.3c「必须同一次提交」** ⇒ ⇒ **G1 不能自己合 G4 的 commit**（`tools/difftest/check-wave-lane-ownership.py` 判红）⇒ ⇒ **只能由合并收口执行者在主干上做那一个原子 commit**<br>⚠️ **单独合 G1 = 真回归**（两道闸门都没了 ⇒ RAG 把 token 写进黑名单、Go 不写 ⇒ 数据面倒退）⇒ ⇒ **若因故先合 G4 ⇒ 不得先合 G1**<br>✅ **门已满足（收口执行者逐行核过 `e8cb769a`，非采信转述）**：`GoStyleRedisDeadlineConnectionFactory.getConnection()` 里 `assertNotExhausted("getConnection")` **已在 `delegate.getConnection()` 之前** ⇒ ⇒ 对齐 Go `go-redis/v9@v9.20.0/internal/pool/pool.go:1098-1104` 的 `waitTurn` fast path（先查 ctx → 再取/拨连接 → 再写）⇒ ⇒ 预算耗尽时**连都不拨** ⇒ ⇒ 三层守门已落并转绿（`GoStyleRedisDeadlineRealLettuceGateTest` L1 / `GoStyleRedisDeadlineLiveRedisWriteFaceGateTest` L2+L3）⇒ ⇒ **M-6 的两个前置（检查前移 ＋ 守门转绿）均已满足** ⇒ ⇒ 见 [`PIT-AXIS-04`](Pitfalls/_cross/pointer-axes.md)<br>⇒ ✅ **客户端可见码「集合」双侧独立复现**：G4 用**真源模块**（非复刻）跑三场景各 20000 次 ⇒ ① ctx 已过期 **1015=19995 / 5001=5**（≈0.025%）② deadline 期间过期 1015=20000 ③ 非 deadline 的 5001=20000；收口执行者用主干探针跑 **200000 轮** ⇒ `code_13`(=5001) 命中 **22** 次 ≈ 0.011% ⇒ ⇒ **两侧独立得同一量级** ⇒ ⇒ **Go 侧集合 = {1015, 5001}** ⇒ ⇒ **RAG 保留域侧抢先 `checkDeadline()` = {1015}（真子集 ⇒ RAG 更严的一侧 ⇒ 才需登记）** ／ **RAG 删掉 = {1015, 5001} ⇒ 同形 ⇒ 不需要签字** ⇒ ⇒ **⇒ G1 的删除方向成立且是同形化**<br>⇒ ✅ **已交付并落码（2026-10-30 收口执行者取 G1 工作树 · 路径 (b)）**：域侧 3 个文件（`AdminDomainService.java` + `AdminDomainServiceDeadlineTest.java` + `AdminLogoutDeadlineHandoffTest.java`）**已并入 M-6 原子 commit** ⇒ 依据 = G1 会话因 Token Plan 上限停摆（`status.type=started` + errorCode 42212 · 最后更新 ~4h 前）＋ 那 3 个文件 mtime 停在 16:29~16:30（稳定）＋ **G1 分支 HEAD 相对主干在 `service/users/admin/admin-rpc/**` 上零差异**（⇒ 工作树版与主干版的差 = 纯增量 ⇒ 无夹带）⇒ ⇒ 复核 = 测试用构造器 7 参与调用逐位匹配 ✔ ＋ `AdminAuth.setAccessExpire` 存在 ✔ ＋ 该域相对 merge-base 未被主干改动 ✔<br>⇒ ✅ **两侧同时跑的探针复跑**：Go `{'failed_fast':'true','code':'4','outgoing_carrier':'present_expired','sql_carrier':'present_expired'}` ⇔ RAG `{'failed_fast':'false','code':'0','outgoing_carrier':'present_expired','sql_carrier':'present_expired'}` ⇒ ⇒ **`outgoing_carrier` 两侧已一致**（共享层载体面闭合 ⇒ 该键由 drift 键升为 **parity 键**）⇒ ⇒ 仍漂移的是 `failed_fast`/`code` ⇒ ⚠️ **二次订正（2026-10-30 · 经 G4 纠正面 ＋ 收口执行者独立 javap 复核）：那两格是探针产物**（RAG 半边 `DeadlineDownstreamProbe.java:86` 自建裸 netty channel ⇒ 绕开 devh 装配 ＋ `:198` 显式 `CallOptions.DEFAULT`）⇒ ⇒ **域侧出向 stub 全部走 devh channel**（域侧 main 代码 `ManagedChannelBuilder`/`NettyChannelBuilder` **零命中**）⇒ ⇒ devh `AbstractChannelFactory.createChannel(String,List,boolean)` 在 **`:95` `ClientInterceptors.interceptForward(channel, interceptors)`** ⇒ **全局客户端拦截器接在 channel 上** ⇒ ⇒ **凡建在 devh channel 上的 stub（哪怕不显式调 `applyTo`）都经过拦截器链** ⇒ ⇒ **域侧出向调用本来就继承请求 deadline** ⇒ ⇒ **「域侧改 `applyTo`」不是闭合条件** ⇒ 已向 G1/G2/G3 发纠正面 ⇒ ⇒ **X-6 仍 🔴 · C-3 仍破**（理由 = **双侧活体证据缺口** ⇒ 定级复判挂起） | 两处**同一个 commit** ⇒ ⇒ 复跑 `AdminDomainServiceDeadlineTest`（`logout` 已移出 `GUARDED` 清单）＋ `AdminRpc*Logout*` 两个客户端可见码用例 ⇒ ⇒ **客户端可见码仍须是 1015**（拦截器先 `close`）· **handler 级码须是 5001**<br>⇒ **逐条机制与四种合并态对照 ⇒ §3-2**<br>⇒ ⚠️ **另有一层**：`BeanPostProcessor` 改的是 `RedisTemplate` 的**字段**，**不是 bean** ⇒ ⇒ `@Autowired RedisConnectionFactory` 拿到的仍是裸 factory（域侧 main 代码实测**零**直接注入 ⇒ 当时只是潜在缺口） |

| **M-7** | ✅ **共享层前置已完成**（2026-10-30 · G4 第九次订正 `6a7b1a2b` · 收口执行者独立复核）＋ ✅ **域侧已闭合**（2026-10-30 · follow-rpc 的 `rag.side: rpc` 已叠取到主干 ⇒ ⇒ **形态面守门已零豁免**；⚠️ **其余 9 个 rpc 域经复算不需要这一行** —— 它们**没有 servlet 容器** ⇒ 加了是**死配置**） | ⚠️ **2026-10-30 收口执行者实测订正（把本层上一轮的表述改准）**：上一轮写「其余 12 个 `@ConditionalOnWebApplication(SERVLET)` 的 web Bean 不受门管 ⇒ **补载体后全部在 rpc 域激活**」⇒ ⇒ **这不成立**（G4 与 G3 各自独立得出同一结论）：rpc 域全部用 `@Import` 精确引入、**不扫 `com.rag.common`** ⇒ ⇒ 那 12 个 `@Component` **靠组件扫描** ⇒ ⇒ **在 rpc 域根本不会注册**（实测 10 个 rpc 域的 `@SpringBootApplication` **实参均为空**）⇒ ⇒ **真正会自动活的是 `AutoConfiguration.imports` 里那 3 个**（`GoStylePathNormalizingFilter` · `GoStyleJsonContentTypeAdvice` · **`GoStyleRouteTemplateResolver`** ← 本层上一轮也漏了第 3 个）⇒ ⇒ ⚠️ **但这反而暴露了更严重的一层**：那 12 个**毫无自保** —— 一旦某域把 `com.rag.common` 加进 `scanBasePackages` ⇒ 它们会**全部**活起来 ⇒ ⇒ **【2026-10-30 二次订正】** 原写「而 `rag.side=rpc` **救不了**（它们不看那个属性）」**已过期** ⇒ ⇒ **13 个类已逐个核实全部挂了 `@ConditionalOnApiSide`** ⇒ ⇒ **扫描面是双保险**（域侧不扫 ＋ 门）⇒ ⇒ **形态面加一行 `rag.side: rpc` ⇒ 一次封掉全部 13 个** ⇒ ⇒ **护栏已从「域侧自觉」收回「共享层自己兜住」**<br>⇒ ✅ **G4 已落码**：新元注解 `com.rag.common.web.ConditionalOnApiSide`（**单一真源**）＋ **13 个类全挂**（上一版那 2 个裸 `@ConditionalOnProperty` 已收敛 ⇒ 全仓 `service/common` 再无第二处 `rag.side` 条件）· `matchIfMissing=true` 保留 ⇒ **默认 api ⇒ 10 个 api 域零改动**（实测全仓 yml 无人设 `rag.side`）<br>⇒ ✅ **守门重写为 5 形态**（全部真起进程）：① non-web ⇒ 13 个全不在 ② servlet+未设 side ⇒ 自动配置面 3 个在、扫描面 10 个不在 ③ servlet+**显式 `@Import` 扫描面 10 类**+未设 side ⇒ 13 个全在 ④ servlet+`rag.side=rpc` ⇒ 13 个全不在 ⑤ servlet+显式 `@Import`+`rag.side=rpc` ⇒ 13 个全不在 ⇒ ⇒ **形态③ ⇒ 形态⑤ 只差 `rag.side` 一个变量 ⇒ 差异被隔离** ⇒ ⇒ 既证明 `rag.side=rpc` 真挡住了扫描面、又是元注解真生效的运行证据（§3.3）· 双向变异（M1 拿掉扫描面一类 ⇒ 形态⑤ 红；M2 拿掉自动配置面一类 ⇒ 形态④+⑤ 红 ⇒ 两条不重叠）<br>⇒ ✅ **载体形态已定（G4 实测 + 收口执行者复核）**：**non-web 主应用下 `management.server.port` 不生效**（实测 `local.management.port` 为空 ⇒ management 端口根本没起）⇒ ⇒ 「独立 management 端口承载抓取」这条路在 non-web 下**不成立** ⇒ ⇒ **可行形态 = 主应用变 servlet**（G1 的 follow-rpc 样板正是这条：`server.port` = Go 观测端口 + gRPC 端口另设 + `spring-boot-starter-web`）⇒ ⇒ 与 Go 同形（Go 的 rpc 侧 = `zrpc.MustNewServer` + go-zero 自建 DevServer 观测面，api 侧才挂 rest 中间件）<br>⇒ ⚠️ **新增一条跨域静态检查的必要性**（G4 提 · 归收口执行者）：rpc 域**必须继续不扫** `com.rag.common` ⇒ 否则那 12 个 `@Component` 会一起活 ⇒ ⇒ ✅ **静态检查已提前落地**（2026-10-30 收口执行者）：`tools/difftest/check-rpc-common-scan-exclusion.py`（结构面 + 形态面 + 内置变异 **5/5 · 含 1 条阴性对照**）＋ 白名单 `conf/rpc-common-scan-whitelist.txt` ⇒ ⇒ 原写「合并窗口加一条静态检查」已提前完成 | **共享层前置**：✅ **已完成**（G4 `6a7b1a2b` · 13 个类全挂元注解 + 5 形态守门 + 双向变异）<br>**域侧（第三次订正后的真口径）**：**只有 `follow-rpc` 是活偏离**（真 servlet 形态）⇒ ⇒ **G1 已落并叠取**（follow-rpc / user-rpc / admin-rpc 三处 —— 后两处为**防御性**）；**G2 / G3 那 6 个域经复算不需要这一行**（无 servlet 容器 ⇒ 加了是死配置）⇒ ⇒ ⚠️ G2 分支 `48e48b93` 里的 like/message 两行属**死配置 · 防御性保留**（已派单加注）<br>🔴 **2026-10-30 二次订正 · 收口执行者实测（前提变了）**：原写「各 lane **在载体任务里**加 ⇒ 合入主干后即可」**前提是错的** ⇒ ⇒ **主干实测**：**载体 2026-10-29 就已在主干**（follow-rpc `pom.xml:216` `spring-boot-starter-web` ＋ `yml:130 server:`）⇒ ⇒ 且 **like-rpc / message-rpc / task-rpc / user-rpc** 同样「有 web starter ＋ 无 `web-application-type: none`」⇒ ⇒ **【2026-10-30 第三次订正 · 起因 = 我的守门 bug】** 上一句写「**5 个域**在主干上今天就是 servlet 形态且缺 `rag.side`」**是错的** ⇒ ⇒ 根因 = `check-rpc-common-scan-exclusion.py` 的 `_module_has_web_starter()` 是**纯文本正则、不剥 XML 注释** ⇒ ⇒ 把 pom **注释**里那句「本模块不引 `spring-boot-starter-web`」当成了**真依赖** ⇒ ⇒ 剥注释后逐域复算：**只有 `follow-rpc` 是真依赖**（⇒ servlet 形态 ⇒ 真需要 `rag.side` ⇒ **它是那唯一一个活偏离**：载体 2026-10-29 已在主干 ⇒ 13 个 api 侧类会挂到 16065 观测口 ⇒ 归 G1）⇒ ⇒ `user-rpc` / `like-rpc` / `message-rpc` / `task-rpc` = **仅注释命中** ⇒ **没有 servlet 容器** ⇒ ⇒ **不需要 `rag.side`**（加了是**死配置**）⇒ ⇒ **已撤回给 G2 的 task-rpc 那一笔派工**（基于我自己 bug 派错的）⇒ ⇒ ⇒ **守门已修**（pom 侧剥注释 ＋ **自带自检**：注释里的假依赖若被判成 servlet 形态 ⇒ 守门直接报红）⇒ ⇒ ⇒ **白名单从 5 条剪到 1 条**（那 4 条是**假豁免**）⇒ ⇒ ⇒ **形态面违例 = 1 个（follow-rpc）** ⇒ ⇒ ⇒ ✅ **【2026-10-30 第四次订正 · 已闭合】** G1 的 `rag.side: rpc` 已随其 `d181b3b0` 的**那一笔 diff** 叠取到主干（follow-rpc / user-rpc / admin-rpc 三处）⇒ ⇒ 守门随即报「🟡 follow-rpc 已无命中 ⇒ 可剪枝」⇒ ⇒ **白名单已归零（0 条）** ⇒ ⇒ ⇒ **形态面守门已是零豁免的真门**（⇒ ⇒ 「违例 = 0」是**零豁免下的实测结论**，不是「有豁免所以看起来绿」）⇒ ⇒ ⇒ ⚠️ **user-rpc / admin-rpc 那两处是「防御性」**（当前 `management.server.port: -1` ⇒ 无 web 上下文 ⇒ 本行当前是**死配置** ⇒ 一旦按 `S1-FOLLOW-001` 给它们加观测口 ⇒ 这一行即成必做项）⇒ ⇒ ⇒ ⚠️ **变异集同日重锚**（原 M3 靠的**正是**刚修掉的 pom 注释 bug ⇒ ⇒ 修好后它空转 ⇒ ⇒ 重锚为 M2/M3 并补 **M5 阴性对照** ⇒ ⇒ 5/5）<br>**验收**：7 个 rpc 域各起一次 ⇒ `/actuator/prometheus` 有内容 **且** 抓取请求**不被 CPU 降载拦掉** **且** 运行期断言「那些 web Bean 不在容器里」<br>⚠️ **判据**：**不得**用「注解在」或「yml 写了这一行」当证据（§3.3）⇒ ⇒ 要有**真启动 + 真 curl**<br>⚠️ **G3 的 comment-rpc 例外**：Go 侧 comment 域**自建** `:9091`（`service/comment/rpc/comment.go:38-41` · `promhttp.Handler()` · 非 go-zero agent）⇒ ⇒ 派工前提「Go 侧无该面」被**实测证伪** ⇒ ⇒ RAG 缺该面 = **真实漂移**（C 类登记，下一轮补）⇒ ⇒ **机制与另三域不同**（go-zero prometheus agent / go-zero devserver / 自建 promhttp 三种）⇒ ⇒ **不能照抄** |
| **M-8** | 🔴 **共享面「rpc 模块口令占位符形态」守门**（2026-10-30 收口执行者落码 · commit 见下） | ⚠️ **背景**：G1 lane 撤回了「`S1-USERS-002` 需 Jason 签字」的旧定性 —— 本层独立复核坐实：Go 侧**全部** rpc yaml 的口令占位符都是**裸** `${POSTGRES_PASSWORD}`，且 go-zero 用 `os.ExpandEnv`（**不认 `:default` 语法** · 实测带默认值写法展开成**空串**）⇒ ⇒ Go 侧 fail-fast ⇒ ⇒ **RAG 的 `${POSTGRES_PASSWORD:password}` = 比 Go 松 = 真漂移** ⇒ ⇒ 取消兜底是**同形化**，**不需要签字** ⇒ ⇒ 共享面缺口：原先**没有任何**守门覆盖这个形态（G1 实证 `check-no-prod-secrets.py` 对它**零判别力**） ⇒ ⇒ **新落**：`tools/difftest/check-datasource-password-form.py`（**一次覆盖全仓 10 个 rpc 模块** · 内置变异自测 **6/6**） ＋ `tools/difftest/conf/datasource-password-whitelist.txt`（**9 个已知未修** · 按仓里「显式数据文件」惯例 · 条目已无命中 ⇒ 守门会报「可剪枝」） ⇒ ⇒ ⚠️ **白名单是豁免不是免检**：任何**新出现**的违例一律报红 | **白名单当前 9 条**：G1 面 3（已在其分支落码 ⇒ 合入后可剪）· **G2 面 3**（message-rpc:29 / like-rpc:20 / task-rpc:16）· **G3 面 3**（article-rpc:20+171 / comment-rpc:53 / favorite-rpc:55） ⇒ ⇒ **已派单 G2/G3**（与它们各自的载体 yml 改动**同一个 commit**）<br>**验收**：`python3 tools/difftest/check-datasource-password-form.py` ⇒ 违例 0 ＋ 白名单条目全部「可剪」⇒ ⇒ **合入后我统一剪白名单**<br>⚠️ **本条的判据只管形态，不管明文** ⇒ ⇒ 与 `check-no-prod-secrets.py`（拦明文密钥）**互补不重叠** |
| **M-9** | 🟡 **「行号硬指针」跨域机制面 · 根治三条方向**（G4 转来 · **棘轮守门已落** · 根治待合并窗口） | ⚠️ **背景**：`docs/RoundPlan/_unresolved.md` 与 `docs/DRIFT/_cross/grading/*` 里有大量**硬编码 `文件:行号` 指针**指向各域台账 ⇒ ⇒ 派生层用它们做**反向链接**，而 `resolve_pointer` 是**纯行号算术** ⇒ ⇒ **域台账插一行 ⇒ 指针偏一格 ⇒ 解析到别的条目** ⇒ ⇒ 「排期指针 / 证据缺口组」成员变了 ⇒ ⇒ **派工面被静默改掉** ⇒ ⇒ ⚠️ **两个数字口径不同、不可比**：G4 回执写「1112 处」（枚举方式未写明）· 本层用**派生层自己的正则** `DOC_PTR_RE` 全仓实测 = **3308** ⇒ ⇒ 本层只记本层口径，两个数**不互相引用为佐证** | **✅ 已落（合并收口执行者 · commit `83969be7`）**：① `tools/ledger-derive/check-pointer-consistency.py` —— **棘轮守门**（判据 =「解析出的编号」应当出现在「**指针所在那一行**写的编号」里；两者**独立算出** ⇒ 不漂移时恒等、漂移时不等 ⇒ ⇒ **零基线**） · 基线 = 存量 **237** 条例外（`docs/DRIFT/_cross/pointer-drift-baseline.json`）⇒ ⇒ **只有新增漂移才红** · ② `tools/difftest/mutate-pointer-consistency.py` —— 变异测试 **5/5**（插一行 ⇒ 必红）· ③ 台账 `docs/DRIFT/_cross/pointer-drift.md`（机制 · 口径 · **一个走不通的方案也记了** · 口径边界） ⇒ ⇒ **派生层零影响**：条目集合差 **2619 = 2619 · 新增 0 · 消失 0** ⇒ ⇒ **🟡 根治三条方向（本条待做 · 都在共享面 ⇒ 无 lane 拥有 ⇒ 合并窗口统一做）**：**① 改按标题/锚点定位** · **② 指针升级为「行号 + 锚点」** · **③ 加「指针复位」脚本** ⇒ ⇒ 落点：`tools/ledger-derive/derive-stage1.py` ＋ `docs/RoundPlan/_unresolved.md` ＋ `docs/DRIFT/_cross/grading/*` ⇒ ⇒ ⚠️ **边界**：棘轮只保证「**派工面不会在没人察觉的情况下变**」⇒ ⇒ **存量 237 条尚未逐条定性**（多少是真漂移、多少是「源行另提他号」的语义正常情况） ⇒ ⇒ 且本守门只覆盖「源行写了编号」的指针（2791 / 3308 = 84.4%）⇒ ⇒ **它不是「指针一定对」的证明** | **验收（根治）**：① 三条方向定一条落码 · ② 存量 237 条**逐条定性**（真漂移 ⇒ 复位；语义正常 ⇒ 写明理由留档）· ③ `check-pointer-consistency.py` 的存量基线**清零**<br>⚠️ **不阻阶段一**：派工面的变化由棘轮**显式报出**，不靠人眼发现 |
| **M-10** | ✅ **「变异脚本锚点存活」守门已落**（2026-10-31 收口执行者落码）· 🔴 **并查出 2 条此前是空的变异** | ⚠️ **背景**：`tools/difftest/mutate-*.py` 那一族**变异脚本**改生产码/探针 ⇒ 再跑守门 ⇒ 期望守门**精确转红** ⇒ 用来证明「守门不是假绿」；⚠️ **但这些脚本本身没有任何守门** ⇒ ⇒ 有一条洞：**变异脚本会随生产代码演进而「空转」** —— 旧串在新代码里**命中 0 处** ⇒ ⇒ **变异根本没发生** ⇒ ⇒ 守门当然不会红 ⇒ ⇒ **「变异转红」这个证据静默变空**，而脚本自己仍 EXIT=0（它会判「锚点命中数 ≠ 1 ⇒ 报错」，可**没人跑它** ⇒ ⇒ 「判据失去判别力」**没有任何机器信号**）。⚠️ **实证**：`mutate-deadline-downstream.py` 的注释里已记**三轮**同类事故（`bindToCurrent` → `bindRemainingNanos` · 观测点与绑定点不同路 · `bindRemainingNanos` → `bindAbsoluteNanos`） | **① 新守门已落**：`tools/difftest/check-mutation-anchor-liveness.py`（**只读型** ⇒ 不注入任何代码 ⇒ 可在共享工作树常驻跑）—— 五条断言：① 目标文件可解析且唯一（同名文件多模块 ⇒ 按**模块标签**限定）② 存在相邻串对且恰有一个出现在目标文件里 ③ **那一个命中数必须 = 1**（**这正是变异脚本自己 `count(old)==1` 的前置条件**）④ 期望红键集合不得为空（空集 = 判据真空）⑤ 期望红键必须在行为键词表里（拼错 ⇒ 探针输出永远匹配不到 ⇒ 判据空转）⇒ ⇒ 覆盖 4 脚本 / **50 条变异**（另 2 个 `mutate-*` 形态不同 ⇒ 已如实标注不在覆盖面）。**② 查出 2 条此前是空的变异**（已重定基）：`mutate-deadline-verdict.py` **M19**（锚点 `DEADLINE_VERDICT_THRESHOLD_MS:-150` **命中 2 处** ⇒ 一处是 echo 提示、一处是真正生效的 `--threshold-ms` 实参 ⇒ 变异脚本自身前置条件不满足 ⇒ 该条**跑不起来**）· `mutate-hot.py` **M10**（锚点 `articleResp = articleRpcClient.getPublicArticle(hotItem.getArticleId(), remainingBudgetMs);` **命中 0 处** ⇒ 下游调用已被重构成多行形式且预算参数改成 `remainingBudgetMs(deadlineNanos)`）⇒ ⇒ **这 2 条此前的「变异转红」证据是拿不到的** ⇒ ⇒ 已重定基（M19 收敛到 `--threshold-ms` 实参行 ⇒ **更精确**：只改实参不碰 echo；M10 改成多行调用形式）⇒ ⇒ ⚠️ **边界**：本守门判的是「**变异脚本此刻还是不是活的**」，**不是**「变异会不会精确转红」（那要真跑变异脚本 ⇒ 它会注入改动到生产码 ⇒ 不宜在共享工作树常驻跑 ⇒ 那一步仍靠人工/CI 独立容器） | **验收**：`python3 tools/difftest/check-mutation-anchor-liveness.py` ⇒ **EXIT=0（50/50 锚点恰好命中 1 处）**<br>**已做变异测试**：故意把一条锚点改成不存在的串 ⇒ ⇒ 守门**精确红在那一条**、其余全绿 ⇒ ⇒ **鉴别力成立** ⇒ ⇒ 还原后 **sha256 一致** + 恢复绿<br>⚠️ **新增守门 ⇒ 连带义务**：`docs/PhaseGoal-judge.md` §6.3 的「仓内守门数 − 声明未跑数」口径变了 ⇒ ⇒ 已把本门登记进该节的**未跑清单**（2026-10-31 才落 ⇒ **不属 2026-10-29 那一轮** ⇒ ⇒ §6.3 的「27 项」仍按那轮口径）⇒ ⇒ 连带 `check-phasegoal-consistency.py` 恢复 EXIT=0 |
| **M-11** | ✅ **「gRPC channel target 必须显式写 `dns:///` scheme」跨域静态守门已落**（2026-10-31 收口执行者落码 · commit 见下）· 🔴 **并查出 9 处存量违规点** | ⚠️ **背景**：devh 的 `GrpcClientAutoConfiguration.grpcNameResolverRegistration(...)` 把 Nacos discovery resolver 注册进 **grpc-java 的进程级全局** `NameResolverRegistry`（**javap 实测**：`io.grpc.NameResolverRegistry.getDefaultRegistry()` ⇒ 进程级；`NameResolverRegistration.destroy()` 只在 **Spring 上下文关闭**时反注册 ⇒ surefire 复用 JVM ⇒ **注册一直在**）⇒ ⇒ 任何**无 scheme** 的 channel target 被接管 ⇒ 把 `127.0.0.1:P` 当**服务名**去 Nacos 查 ⇒ ⇒ `UNAVAILABLE` ⇒ ⇒ 表现为「**单跑绿、同跑红**」（跨测试类顺序依赖）。⚠️ **本机制在本仓「早已认知」但「无机器守门」** —— 早前记录 `docs/DRIFT/global/lanes/L6-x5gate.md:60` / `L8-slowgate2.md:62` 已把它列为「写测试样板 ⑥」⇒ ⇒ 但那只是 **markdown 清单** ⇒ ⇒ **人工盘点必漏**：2026-10-31 一次盘点就漏掉 **9 处 / 9 个文件**（跨 users / comment / hot / common 四域）⇒ ⇒ **这才是要落机器守门的理由** | **① 新守门已落**：`tools/difftest/check-grpc-channel-target-scheme.py`（**只读型** ⇒ 不注入任何代码 ⇒ 可在共享工作树常驻跑）—— 判据 = 扫 `service/**/*.java`（**1330** 个文件）里的**代码态** `forAddress(`（**词法级剥离注释与字符串字面量** ⇒ 注释里解释这个 API 不算违规）⇒ ⇒ 三条错误码：`E1` 未登记违规 · `E2` **例外已失效**（某点位修好了 ⇒ 条目必须同步删 ⇒ 否则例外集长成永久豁免）· `E3` 例外定义不合格（无理由 / 路径写错）。⚠️ **例外集是「点位级」不是「文件级」** —— 第一版按文件登记 ⇒ ⇒ **已登记文件里新增的违规抓不到**（文件级豁免 = 永久白地）⇒ ⇒ 变异 M1 精确证伪了这一点 ⇒ ⇒ 现按「文件 + 归一化代码片段」登记。**② 存量 9 处点位已分派**：G4 面 **1**（`service/common/.../GoStyleGrpcClientBreakerTargetKeyTest.java:226` · 共享层）· **G1 面 5**（`admin-rpc` ×2 + `user-rpc` ×2 + `user-api` ×1 · **活体 RPC 测试 ⇒ 真会被接管**）· G3 面 **3**（`comment-rpc` ×1 + `hot-rpc` ×1 **已在其分支自修** ⇒ 待合入后剪枝；`hot-api` ×1 **惰性** —— 同文件 javadoc 自证「channel 从不被真正使用」）⇒ ⇒ **每修一处 ⇒ 剪一条例外 ⇒ 剪到 0 ⇒ 守门零豁免** | **验收**：`python3 tools/difftest/check-grpc-channel-target-scheme.py` ⇒ **EXIT=0**（9 个点位全部登记 · 无未登记违规 · 无失效例外）<br>`python3 tools/difftest/check-grpc-channel-target-scheme.py --mutation-test` ⇒ **EXIT=0（5/5 精确命中）**<br>⚠️ **新增守门 ⇒ 连带义务**：已把本门登记进 `docs/PhaseGoal-judge.md` §6.3 的**未跑清单**（2026-10-31 才落 ⇒ **不属 2026-10-29 那一轮** ⇒ ⇒ §6.3 的「27 项」仍按那轮口径）⇒ ⇒ 连带 `check-phasegoal-consistency.py` 恢复 EXIT=0 |
| **M-12** | ✅ **judge §二 A-1 的 `tests` 列口径已改为环境无关**（2026-10-31 合并收口执行者落码）—— 该列原**陈旧** ＋ **环境相关** ⇒ ⇒ **已改为「源声明测试方法数」** ⇒ ⇒ **不可当覆盖面口径用**的隐患已消除 | ⚠️ **背景**：judge §二 A-1 的 `tests` 列定义原 = 「surefire `TEST-*.xml` 逐 `testsuite` 属性求和」⇒ ⇒ **环境相关**：Docker 停 ⇒ 容器在 `@BeforeAll` 起不来 ⇒ **整类 N 个方法折叠成 1 条 error** ⇒ 读数**偏小**；而 `@ParameterizedTest` 被 surefire **展开成 N 条 case** ⇒ 读数**偏大** ⇒ ⇒ **同一份代码两次跑能出两个数**；且它是 **2026-10-29 那次**的读数 ⇒ 此后各域新增/改动的测试**没进列**（`user-rpc` 列 **168** vs 2026-10-31 实测执行 **184** ⇒ 差 **16**）⇒ ⇒ ⇒ **「3,294」这个数不能当「当前全仓条数」用** ⚠️ **【2026-10-31 再订正 · G2 回执触发】差是**两个方向相反的量** ⇒ ⇒ **不可相减**：① **源声明数 > 列值**（**真增量** = 陈旧）= **13 行 · +150** ② **列值 > 源声明数**（**参数化展开**）= **6 行 · −219** ③ 相等 = **14 行** ⇒ ⇒ ⇒ **⇒ 「陈旧」的真证据是方向 ①**（我第一版拿「两个总数相减」去论证「陈旧」⇒ ⇒ **判据形态错** ⇒ ⇒ 已改成两分法）| ✅ **已做（零位移 · 2026-10-31）**：① **A-1 的 39 行 `tests` 列逐行重算并回填**为源声明数（**19 行变更** = 19 增 19 删 ⇒ **总行数 727 不变 ⇒ 零行位移**）⇒ ⇒ **列求和 3,226 == 实测**（29 个含测模块）⇒ ⇒ ② **列定义注释就地改写**（写清新口径 ＋ 保留两分法的全部事实与数字 ＋ 加「覆盖面口径 ≠ 执行结果口径」的边界）⇒ ⇒ ③ **§七 P0 #6 的声明就地加注**（**3,294 条 / 0F / 0E / 4S** 是**运行期**读数 ⇒ 与 A-1 的 3,226 **两个口径、不可相减**）⇒ ⇒ ④ **新落机器守门 `tools/difftest/check-source-test-decl-count.py`**（逐行对账 + 求和对账 + 行数 39 + 10 个 `proto` 行的 0 值对账 ⇒ ⇒ **A-1 忘了更新就红**）⇒ ⇒ **EXIT=0** ⇒ ⇒ ⑤ **`check-phasegoal-consistency.py` 第 13 组自检改口径**：原判据拿 A-1 求和去对「§七 P0 #6 的全仓 N 条 / 0F」⇒ ⇒ **两个不同口径** ⇒ ⇒ **判据形态错** ⇒ ⇒ 改为**直接重算源声明数 ⇒ 与 A-1 列求和对账**（3,226 == 3,226 ✅）⇒ ⇒ ⑥ **新守门登记进 §6.3 的「本轮未跑」清单**（就地追加到该引用块行尾 ⇒ 零位移 ⇒ ⇒ §6.3 的「**27 项**」按本轮口径不动）⇒ ⇒ **consistency 守门全绿**<br>⚠️ **遗留（不阻塞）**：**执行读数**（surefire 真跑条数 / 0F / 0E / skipped）仍需一次**全仓 `mvn -o -B -DargLine=-Xmx2g clean verify`** 刷新（⇒ ⇒ **不能与其他 lane 的 mvn 并发** ⇒ ⇒ 排在**收口窗口**）⇒ ⇒ **但它与 A-1 的 `tests` 列是两个口径 ⇒ ⇒ 刷了也不会改本列** ⇒ ⇒ **本条已无「等全仓回归」的前置依赖**<br>⚠️ **一处自己踩的坑（已修）**：改列的脚本里 `for i in range(lo, ...)` + `if l.startswith('## '): break` ⇒ ⇒ **在标题行本身就 break** ⇒ ⇒ 替换数恒为 0 ⇒ ⇒ **「脚本跑了 0 行」被当成「不用改」** ⇒ ⇒ **教训见 `docs/Pitfalls/_cross/testing-pitfalls.md`** |
| **M-13** | ✅ **P2-T01「出向 stub 全表」的两项缺失交付物已补齐**（2026-10-31 合并收口执行者落码）—— 派工面 `docs/RoundPlan/round-02/README.md` 的 P2-T01 / P2-T02 **验收命令**引用的 `count-outbound-stubs.py` 与 `outbound-stub-inventory.md` **原本都不存在**（该目录只有 `README.md`）⇒ ⇒ **照抄执行会直接失败** ⇒ ⇒ G2 在 `docs/DRIFT/message/vs-common.md` 登记后转派到本面⇒ ⇒ **执行主体本就是合并收口执行者**（P2-T01「执行主体」行）⇒ ⇒ 本轮补齐 | 🔴 **背景**：P2-T01 的「改造面是跨 5 个模块的 **21 个类 / 30 个调用点**」是整个 round-02 的**工作单底座**（P2-T02~P2-T04 都依赖它）⇒ ⇒ 底座缺失 ⇒ ⇒ 后续三笔虽然各自落了码，**却没有落表可核销**⇒ ⇒ ⚠️ **这不是「文档缺个链接」，是「派工面的验收命令不可执行」** ⇒ ⇒ 属**派工面缺陷**（非域侧缺陷） | ✅ **已落（2026-10-31）**：① **新脚本 `docs/RoundPlan/round-02/count-outbound-stubs.py`**（**只读型** ⇒ 可常驻跑）—— 扫 `service/**/src/main/java/**` 的「出向 stub 提供者」（`@GrpcClient` 注入点 或 public 方法返回 `*Stub` ⇒ 按「保守包含」原则）⇒ ⇒ **判据打在代码态**（词法级剥离注释与字符串字面量）⇒ ⇒ **三态**（① 走统一载体 / ② 手搓 remaining / ③ 裸 stub）＋ **上界轴**（本地 min cap / 载体自带 min cap / 模块级共享 `cap-millis`，三来源互斥判定）＋ **Go 对照**（按 RAG 模块 → Go yaml 目录的**显式映射表**取 `XxxRpcConf:` 段的 `Timeout`；无显式值 ⇒ 走 `go-zero@v1.10.0/zrpc/config.go:31` 默认 2000ms）⇒ ⇒ **四种模式**：只读计数（打印 `Δ_start` / `Δ_end` ＋ 两次扫描指纹一致性 ＋ 三态和 == 总数自检）· `--write-inventory`（生成落点表）· `--check-inventory`（落点表与扫描**逐字节**一致 ⇒ 不一致 exit 1）· `--assert-no-zero-deadline`（零上界类数必须 = 0 ⇒ 否则 exit 1）② **新落点表 `docs/RoundPlan/round-02/outbound-stub-inventory.md`**（由脚本生成 ⇒ 不手改）③ **实测**：**21 个类**（与 P2-T01 规格写的 21 **逐数吻合** ⇒ 扫描面口径获独立验证）· 三态 8/4/9（和 == 21 ✅）· **真零上界 = 1**④ **订正我上一轮 `:46` 的错数**（零上界 2 → **1**；对象由 `comment-api`/`hot-api` 改为 `article/article-api` 的 `article-rpc` 方向）⇒ ⇒ 根因 = **判据打在错误的层**（只看 `withDeadlineAfter(Math.max(1L, budget), …)` 的**实参**，漏了 `budget` 的**值链上游** —— 那两处的 `budget` 都已 `Math.min(2000, remaining)`）⇒ ⇒ 已在 `docs/DRIFT/global/vs-common.md` `:46` + 新增 §4.19.31 就地订正 ⇒ ⇒ **派生层 diff = 0 行**（派工面未被扰动） | **验收**：`python3 docs/RoundPlan/round-02/count-outbound-stubs.py` ⇒ **EXIT=0**（21 类 · 三态和 == 总数 ✅ · 两次扫描指纹一致）<br>`python3 docs/RoundPlan/round-02/count-outbound-stubs.py --check-inventory` ⇒ **EXIT=0**（落点表与扫描逐字节一致）<br>⚠️ `python3 docs/RoundPlan/round-02/count-outbound-stubs.py --assert-no-zero-deadline` ⇒ **EXIT=1（当前红 · 不是脚本坏了）** —— 真零上界 = **1**（`article-api` 的 `article-rpc` 方向；Go 侧**显式** `Timeout: 70000`）⇒ ⇒ **真漂移** ⇒ ⇒ 修法需**共享层支持 per-direction cap**（组件级 cap 表达不了 70000 / 2000 两方向 ⇒ ⇒ **绝不能配组件级 `cap-millis=2000`**，会把 70000 改严 35 倍）⇒ ⇒ 属 **G4 写权面**（`service/common/**`）的**能力缺口** ⇒ ⇒ 本面只登记 + 转派<br>⚠️ **本脚本不在 judge §6.3 的守门清单口径内**（那口径只数 `tools/difftest/check-*.py` ＋ `tools/check-ledger-*.py` ＋ `tools/check-agents-budget.py`）⇒ ⇒ **§6.3 的「27 项」不动**⇒ ⇒ **且不得登记进 §6.3 的「本轮未跑」清单**（该清单的守门会校验「仓内存在」⇒ ⇒ 登记了反而会让 `check-phasegoal-consistency.py` 红）⇒ ⇒ 改登记在此（Worktree 的 M 行 = 新守门的登记位） |

| **M-14** | ✅ **派生层「状态列」判据已落**（2026-10-31 合并收口执行者落码）—— 派生层从**人写的表格**里取状态时，改为**按表头列名定位「状态」列**，不再按整行取最早标记 | ⚠️ **背景**：`docs/DRIFT/global/_README.md` 的 E 表行形态是 `\| **E20** \| 🔴 **N6**：<问题描述> \| <落点> \| ✅ **已修**(...) \|` ⇒ 第 2 格（条目）里的 🔴 是**问题标签**、第 4 格（状态）才是当前态 ⇒ ⇒ 旧口径把 **16 条已修条目**读成 `🔴 未闭合` ⇒ ⇒ 而**同一文件的阶段归属表**写着「D（**差异已消失** ⇒ 无待实现内容）」⇒ ⇒ **同一份文件里状态与阶段自相矛盾** ⇒ ⇒ 派工面「状态开放」清单虚增 | ✅ **已落**：① **判据**（`tools/ledger-derive/derive-stage1.py`）—— 表头含「状态/处置/结论/现状」时**从那一列读**；**护栏** = 该格**以更严重的标记收尾 ⇒ 保守回退**（防假绿；实测 `E34` 的 ✅ 只覆盖子项③）；**回溯** = 向上找分隔行（台账的空行会把一张表切成多段）＋ **表头列数 == 行格数**（防串表）；② **影响面** = 派生 TSV **1284 行里恰好 16 行**变化、**恰好 `status` 一列**变化、**全部在 global 域**、**全部 🔴→✅** ⇒ ⇒ 其余 10 个域**零变化**；③ **人工逐条核验 16 条** = **16/16 正确**、**零假绿**；④ 派工面「状态开放」（**MD 口径**）**517 行 / 504 编号 → 503 行 / 490 编号**；派生表里「开放状态」的**编号**数 **555 → 539（−16，零条进入）**、「已闭合（台账没销账）」**80 → 66**（那 16 条本就不属该档）、「真缺口 C/D 类未排期」**88 不变**、状态冲突 **153 → 154**（新增 **E48** ⇒ **global 域内三处来源不一致**：E 表行 `:427` 已翻成 ✅，而同文件两处**历史复核段** `:750` / `:776` 段内最早标记仍是 🔴 ⇒ **同域多版本**型 ⇒ 归 **global 域面**） | **验收**：`python3 tools/ledger-derive/derive-stage1.py --check` ⇒ **EXIT=0**<br>**新守门 `tools/ledger-derive/mutate-status-column.py`**（**变异测试** ⇒ 证伪「判据只是看着像在读状态列」）—— 四个断言：**C0** 隔离树保真（派生行数 == 主干 1284）· **M1** 状态列改成 🔴 ⇒ 必须翻成 `🔴 未闭合` · **M2** 阴性对照（只动条目格）⇒ 必须**保持** `✅ 已一致` · **M3** 状态列末尾追加 🔴 ⇒ 必须翻成 `🔴 未闭合`（**护栏有牙 · 防假绿**）⇒ ⇒ **EXIT=0（4/4）**<br>⚠️ **隔离树做法**：硬链接整树复制 `docs/` + `tools/` ⇒ ⇒ **硬链接就地写会改到真仓** ⇒ ⇒ 故待变异文件与派生产物**先 `os.remove`（只删链接）再写** ⇒ ⇒ 真仓产物零改动<br>⚠️ **本门不在 judge §6.3 的守门清单口径内**（那口径只数 `tools/difftest/check-*.py` ＋ `tools/check-ledger-*.py` ＋ `tools/check-agents-budget.py`）⇒ ⇒ **§6.3 的「27 项」不动** ⇒ ⇒ **也不登记进 §6.3 的「本轮未跑」清单**（该清单会校验「仓内存在」⇒ ⇒ 登记了反而让 `check-phasegoal-consistency.py` 红）⇒ ⇒ 改登记在此 |

| **M-15** | ✅ **跨域耦合矩阵的 `common_radiation` 快照已重算刷新**（2026-10-31 合并收口执行者落码）—— 守门 `check-wave-coupling.py` 的 **18 项漂移**已清零 · 🟢 **并订正本面上一轮「守门全绿」的漏报** | ⚠️ **背景**：G1/G2/G3 各域把**共享层的保护类 / 拦截器 / 建议器**接进本域（`follow` / `hot` / `message` / `task` / `users` 五域新增引用）⇒ ⇒ `service/common` 逐类**辐射度**快照过期 ⇒ ⇒ 守门 `python3 tools/difftest/check-wave-coupling.py` ⇒ **EXIT=1 · 18 项漂移**（**全部落在 `common_radiation`**）⇒ ⇒ 该文件属**合并收口执行者写权面**（`docs/DRIFT/_cross/**`）⇒ ⇒ 归本面落码（**不是**域侧缺陷 ⇒ **不改任何生产代码**）。⚠️ **⚠️ 顺带订正本面上一轮的漏报**：上一轮报「**24 项守门全绿**」时用的是**手写清单** ⇒ ⇒ **漏了 `check-wave-coupling.py`** ⇒ ⇒ 它**当时就是红的**（⇒ ⇒ **「全绿」那句是错的**）⇒ ⇒ 根因 = **清单是手写的、没枚举全仓** ⇒ ⇒ 修法 = **枚举 `tools/**/check-*.py` 逐个实跑**（只排除两个**用法门**：`check-ddl-static.py` 需 PG 容器、`check-merge-tree-content.py` 需 4 棵 lane ref）⇒ ⇒ ⚠️ **【2026-10-31 · 第三波合并后 · 订正】** 「**本轮 29 个 `check-*.py` 全绿**（27 绿 + 2 用法门）」**这句当时是错的** ⇒ ⇒ 实测：该轮 `check-source-test-decl-count.py` **本来就是红的**（文档声明 **3,300** vs 守门实测 **3,293**）⇒ ⇒ **根因 = 把 lane 报来的总数当成了守门实测值**（与此前 `check-wave-coupling.py` 那次**同族**）⇒ ⇒ 第三波合并后重跑（枚举 `tools/**/check-*.py` = **30** 个）= **25 绿 · 3 红 · 2 用法门** ⇒ ⇒ 3 个红项已在本轮修掉（见 **M-16**） | ✅ **已落**：① **快照整块刷新**（`--print` 的重算值整块替换 §6.1 的 JSON 块）⇒ ⇒ 被外部域引用的类 **44 → 52**；`service/common` 的 `.java` 总数 **63 → 88**（2026-10-27 ⇒ 2026-10-31，随共享层落码增长）⇒ ② **§1.4 标题 + 分桶表就地订正**（**行数不变 ⇒ 零位移**）⇒ ⇒ 顺带清掉一条旧账：**正文原先写「37 个被外部域引用」而它自己下面的快照是 44** ⇒ ⇒ 本轮两个数字都从重算值取（**52**）并在标题里**标注基线值** ⇒ ⇒ **不再有第二个真相源** ③ ⚠️ **块内两个被外部引用的行号漂移**（`:530` → **`:574`** · `:589` → **`:653`**）⇒ ⇒ 连带改指 `docs/DRIFT/_cross/grading/W4.md`（U5-O 叠加层的 G4-9 行）＋ `docs/DRIFT/article/vs-common.md`（G4-9 行）＋ `docs/RoundPlan/_hardcoded-numbers.md` **HC-02**（**44 → 52 条** · 指针 `:223` → `:121`，历史行 `:223` 保留为 2026-10-28 轮的存档）⇒ ⇒ **否则 `check-pointer-consistency.py` 会红** ④ **派生层只变 1 行**（G4-9 的 `basis` 列）⇒ ⇒ 其余 1283 行**逐列未变** ⑤ 刷新记录写进 `docs/DRIFT/_cross/cross-domain-coupling.md` **§七**（**末尾追加** ⇒ 零位移） | **验收**：`python3 tools/difftest/check-wave-coupling.py` ⇒ **EXIT=0 · 5 项断言全绿**（度数 / DAG / topic / 跨域引用 / 辐射度）<br>⚠️ **关键判定（决定这次刷新不影响分波方案）**：18 项漂移**全部落在 `common_radiation`** ⇒ ⇒ 其余四项（`pom_dependents` / `grpc_edges` / `cross_domain_topics` / `users_common_classes`）**逐项未变** ⇒ ⇒ **§二 分波方案的事实底座（DAG + 编译期度数）未过期** ⇒ ⇒ **分波方案本身不必重排**<br>⚠️ **本门不在 judge §6.3 的守门清单口径内** ⇒ ⇒ **§6.3 的「27 项」不动** ⇒ ⇒ 改登记在此 |

| **M-16** | ✅ **第三波合并后的 5 个红项全部清零**（2026-10-31 合并收口执行者落码）· 🟢 **并订正本面上一轮「守门全绿」的漏报（第二次）** | ⚠️ **背景**：第三波四棵 lane 合并后（`55bdd673` / `713896ff` / `22423962` / `fd6c9830`）+ G2 最后一笔（`cff90c26`）⇒ ⇒ 全仓守门由「绿」翻「红」 ⇒ ⇒ **根因 = 事实变了、快照/登记没跟**（域侧把共享层保护类接进本域 ⇒ 辐射度变；域侧自带封顶删除 ⇒ 出向落点表变）⇒ ⇒ **不是行为回归** ⇒ ⇒ 只更新登记物、不改生产代码 ⇒ ⇒ ⚠️ **【自我订正】** 本面上一轮 M-15 里那句「**29 个 `check-*.py` 全绿**」**当时就是错的** ⇒ ⇒ 实测 `3431111f` 的源声明总和 = **3,293**（文档却写 3,300）⇒ ⇒ `check-source-test-decl-count.py` **当时就是红的** ⇒ ⇒ **根因 = 把 lane 报来的总数当成了守门实测值**（与 `check-wave-coupling.py` 那次**同族**） | ✅ **已落**：① **M2 总数落终值 3,314**（`git grep -o` 在该 commit 上重算 ⇒ **实测值**，非 lane 报值）② **「零上界」按组件级口径 = 0**（G4 逐组件实测：16 个有出向组件全部有硬上界；13 个零出向组件不适用） ③ **跨域耦合快照刷新 ×2**（§八 + §九）⇒ 两次都只更新快照 + 连带改指两个块内行号（`:530→:574→:576→:575` · `:589→:653→:655→:654`）⇒ **历史锚点表保持不动**（不改写历史） ④ **变异锚点重定基**（`mutate-hot.py M08`：G3 删掉域侧封顶 ⇒ 原锚点命中 0 ⇒ 改指仍在的传播语句 ⇒ new 串避开已有分支 ⇒ 漂移形态与原意同形） ⑤ **出向落点表重生成**（`count-outbound-stubs.py --write-inventory`） | **验收**：**枚举全仓 = 31 绿 · 0 红 · 2 用法门**（`tools/**/check-*.py` 30 个 + 派生层 `--check` + 变异测试 + 落点表 `--check-inventory`）· 派生层 **1284 行零增零删** · 指针棘轮 **新增 0 条**
| ⚠️ **一笔主动撤回**：曾给派生层 `entry_blocking_decl` 补「不再阻断阶段 1」否定形态 ⇒ ⇒ 实测**影响面 1048 行**（远超预期 1 行）⇒ ⇒ 查证发现该函数对**任意输入**都返回「否」⇒ ⇒ **撤回** ⇒ ⇒ 「派生层否定式匹配漏形态」这条**留作待办**（未查清前不落码）
| ⚠️ **本门不在 judge §6.3 的守门清单口径内** ⇒ ⇒ §6.3 的「27 项」不动 ⇒ ⇒ 登记在此 |
| **M-17** | ✅ **第三波悬空产出核销 + 两处「守门自身缺陷」清零**（2026-10-31 合并收口执行者**代 G4 lane** 落码）—— ① `check-ledger-selfcheck.py` 层 0 阳性对照 ⑤″ 的**位数不一致**修法 ② 共享层**例外集剪到 1 条**（悬空登记清零） | ⚠️ **背景**：① **磁盘满**（`/Users/pr` 仅剩 **170 Mi**）⇒ ⇒ `check-deadline-downstream.py` 的 Go 半边**链接失败**（`no space left on device`）⇒ ⇒ 守门清单一度读数 **30 绿 · 1 红**（**红项是环境性的，不是真漂移**）⇒ ⇒ 清 `~/Library/Caches/go-build`（**7.1 G 纯构建缓存**）⇒ ⇒ 恢复 **7.3 Gi** ⇒ ⇒ 该门 **PASS**（双侧活体探针 4 键全一致）⚠️ **⇒ 「磁盘是回归的前置条件，不是事后细节」** ② 四棵 lane 会话状态全为 `42212` / `50113` ⇒ ⇒ **状态字段不可信** ⇒ ⇒ 按「看产物最后写入时间」复核 ⇒ ⇒ **G4 / G3 两棵工作树留有未提交产出**（G4 15 文件 +1044 −254 @16:36 · G3 2 文件 +58 −2 @16:42 ⇒ **都晚于会话状态自报**） | ✅ **已落**：① **`tools/check-ledger-selfcheck.py`**（本面写权）：`_off` 用 `--abbrev-commit` ⇒ 本仓库实测 **8 位**（20/20）而 `manifest_chain` 内部统一 `h[:7]` ⇒ ⇒ `_n == _x` **恒假** ⇒ ⇒ ⑤″ 在**任何**「HEAD ≠ 主干」的分支上**恒红**（「一个恒红的守门等于没有守门」）⇒ ⇒ 修法 `_x = _off[0][:7]`（**一行 · 零语义变化**）② **`service/common/**`（G4 写权面 ⇒ 代落码）**：`GoStyleClientCallTimeoutSingleSourceGateTest` 的 `KNOWN_INLINE_CAP_CALL_SITES` 里 like-api / task-api 两条**悬空登记**（内联已由 `22423962` 删净 ⇒ 落在判据④ 的 `expired` 集合 ⇒ **该守门本来是红的**）⇒ ⇒ 剪 2 条（**9 增 9 删 ⇒ 行数不变**）③ 台账：`docs/DRIFT/global/vs-common.md` **§4.19.42**（末尾追加 ⇒ 零位移）＋ `docs/DRIFT/common/protection.md`「现行态订正」（就地 4→4 + 末尾追加）＋ 本条 ④ **派生层重生成**：**1284 行零增零删 · 变化行 0**（⇒ 实测标题里的编号 token **未造成劫持**） | **验收**：**静态守门 31 绿 · 0 红 · 2 用法门**（枚举 `tools/**/check-*.py` 30 个 + 派生层 `--check` + 变异测试 + 落点表 `--check-inventory`）· ⑤″ **双场景**（HEAD≠主干 ⇒ ❌→✅ 且 `EXIT 1→0`；HEAD==主干 ⇒ 仍走 `else` 分支**不误伤**）＋ **变异**（回退一位 ⇒ 又红）⇒ **判据有牙** · 共享层判据 **surefire `tests="4" skipped=0 failures=0 errors=0`**（⚠️ **定向跑** `-Dtest=GoStyleClientCallTimeoutSingleSourceGateTest` ⇒ **不是全量读数**）＋ **变异**（加回 2 条 ⇒ **红在 `noNewInlineDomainSideCap:250` 的目标断言** · `Failures: 1, Errors: 0` ⇒ 非裸异常）⚠️ **共享层全量 `mvn -pl service/common test` 本轮未跑完** —— **Docker Desktop 的 daemon 僵死**（进程在、`docker ps` 挂起）⇒ ⇒ 需容器的测试卡在连接超时（surefire CPU 0% · 6 分钟无进展）⇒ ⇒ **全量回归待 Docker 恢复后补跑**（**不擅自重启用户的 Docker**）· 派生层 `--check` PASS · 指针棘轮新增 0 条 |
| ⚠️ **代落码原因**：**G4 lane 会话停摆**（`42212` 配额耗尽 + 最后写入时间 16:36 后未再动）⇒ ⇒ `service/common/**` 的写权无人行使 ⇒ ⇒ 由**合并收口执行者**代为落码（§5.3f「共享层写权唯一」的**例外执行路径**）⇒ ⇒ **方向已定**（G4 自己的方案 + 本层已逐条复核）⇒ ⇒ 非新开工 |
| ⚠️ **核销结论（G4 / G3 两棵工作树的未提交产出）**：**不采纳** G4 的 `docs/PhaseGoal-judge.md` 那份（写 **3,300**，主干已被更晚的 **3,314** 覆盖）与 like/task 域侧代码（**主干已有 = 重复副本**）⇒ ⇒ **只采纳「例外集剪到 1 条」**；**不采纳** G3 那 2 文件（**前提已过期** —— 主干 `check-wave-coupling.py` 已 PASS；且其修法是「改 import 为全限定名」= **绕开判据盲区** ⇒ 工具层根治转派待办） |
| ⚠️ **一条口径修正（比上面两条都重要）**：**「守门清单全绿」≠「测试全绿」** —— 本面的「31 绿」是 **`tools/**/check-*.py` 静态清单**，**不覆盖 surefire 层**（`mvn test` 的 JUnit）⇒ ⇒ 本轮才发现共享层有一个**真红项** ⇒ ⇒ **两套口径必须分别跑**（静态清单：环境无关 · 快  ／ surefire 实跑：要编译 · 要空间）⇒ ⇒ ⚠️ 本轮晚发现的直接原因 = **磁盘满** ⇒ ⇒ 连带订正一处**过期数字**：已配 `cap-millis` 的模块数 = **15**（**代码态**口径：剥离注释后计数）⇒ ⇒ `article-api` **刻意不配**（yml `:201-214` 是说明注释 ⇒ **grep 会命中但不生效**）⇒ ⇒ 合计 **15 真配 + 7 处注释命中**（⚠️ 「grep 命中 = 已配」是**错的**，与本条同族） |
| ⚠️ **本门不在 judge §6.3 的守门清单口径内** ⇒ ⇒ §6.3 的「27 项」不动 ⇒ ⇒ 登记在此 |
| **M-21** | ✅ **`check-wave-coupling.py` 的辐射度判据升级 ⇒ 假绿消除**（2026-10-31 合并收口执行者落码 · `tools/**` 本面写权面）—— 扫描面**只扫生产面** ＋ 判定改为**代码态 FQN** ⇒ ⇒ 快照 **52 → 33 条** | ⚠️ **背景**：**两条独立路径**指向同一处门禁盲区 —— ① **G3 lane 转派**（「辐射度判据的 import 盲区」）；② **G2 lane 独立实测**（「该门按 import 判定 ⇒ 全限定名引用看不见 ⇒ ⇒ **辐射度这个指标系统性偏小**」）⇒ ⇒ 同一处 ⇒ ⇒ **不是某个 lane 的个案** ⇒ ⇒ 而这是**假绿**（真依赖被删时门不会红 ⇒ ⇒ **假绿比假红贵**） | ✅ **已落（`tools/difftest/check-wave-coupling.py`）**：① **扫描面收窄到生产面**（`service/**/src/main/**/*.java`）—— 辐射度的本意是「**生产依赖面**」⇒ ⇒ 旧口径把 `src/test` 也算进来；② **判定改为「代码态 FQN」**（代码态里出现该类的完全限定名即算引用 ⇒ ⇒ `import` 行与 `@Import({...FQN.class...})` 一视同仁）＋ 配套**剥离注释与字符串/字符字面量**（等长替换 ⇒ 行号不位移）；③ 守门 stdout 文案改成「被外部**生产代码**引用」（口径可见） | 🔴 **变异实录（本轮最有价值的一段）**：**第一版修法**（只改 ②，扫描面不动）**在变异下仍然绿（EXIT=0）** ⇒ ⇒ ⚠️ **它没有消除假绿** —— 原因 = `message` 域的**守门测试类**仍 import 着该类 ⇒ ⇒ **测试面把「生产依赖被删」掩盖了** ⇒ ⇒ 必须**同时**收窄扫描面 ⇒ ⇒ **第二版（现行）同一变异 ⇒ EXIT=1（红）** ⇒ ⇒ 还原后 EXIT=0 ⇒ ⇒ **旧口径在同一变异下漂移项数 = 0 ⇒ 绿** ⇒ ⇒ **假绿的直接实证**。**判据自证**：阳性对照 **9/9**（import / `@Import` / 内部类 `.Sub` ⇒ 算；行注释 / 块注释 / javadoc `@link` / 字符串字面量 / URL 里的 `//` / 前缀同族 `XxxHelper` ⇒ 不算）。**连锁订正（全部零位移）**：§1.4 标题 **52 → 33**（并标注口径不同、不可直接比）· 口径说明段 · 分桶表**整块重建**（新值 6/5/3/19 类 · ⚠️ 重建时一度把 4 行挤成 1 行 ⇒ 当场修回）· §三「8/6/8」→「**6/5/3**」· 锚点表（`"MqConsumer": [` ⇒ **`:532`**；`GoStyleRedisClientAutoConfiguration` ⇒ **已不在辐射列表**，其外部引用全在测试类 ⇒ 旧指针 `:582` 已失效）· `W4.md` + `article/vs-common.md` 指针 · **HC-02 52 条 → 33 条**。⚠️ **快照块 492 → 373 行（-119）** ⇒ ⇒ 块后行号全前移 ⇒ ⇒ **已逐处核**：全仓指向该文件的跨文件指针**共 3 处、全部指向块内 `:532`** ⇒ ⇒ **无悬空** |
| ⚠️ **口径变更的实质**：旧口径下被计数的 **19 个类 = 「只有测试面引用」的类** ⇒ ⇒ 生产上本就没有外部依赖 ⇒ ⇒ **旧快照 52 是虚高** ⇒ ⇒ **33 是真值** | ⚠️ **同族但范围外的一条（登记待办）**：`scan_users_common_classes()`（§1.3 `users-common` 逐类跨域引用）**同样只认 `^import` 行** ⇒ ⇒ **同一种全限定名盲区** ⇒ ⇒ 本轮**未动**（改它会动 §1.3 快照与分桶 ⇒ 需单独设计）⇒ ⇒ 详见 `docs/DRIFT/global/vs-common.md` **§4.19.48** |
| ⚠️ **验收**：静态守门 **31 绿 · 0 红 · 2 用法门** · 派生层 **1284 行零增零删 · md 产物逐字节一致 · `--check` PASS** · 指针棘轮 **237 == 237（新增 0）** · U+FFFD=0 · 反斜杠逐文件持平 |
| **M-20** | ✅ **派工面 `P2-T04` ⑤ 的三处订正已落**（2026-10-31 合并收口执行者落码 · **派工面 owner = 本面**）· 🟢 **并核销 G4 lane 第二条跨会话消息（又是零待叠）** | ⚠️ **背景**：G4 lane 发来第二条跨会话消息，内容有三块：① 「per-direction cap 需求面比你写的窄得多」（逐组件实测 ⇒ 只需覆盖 `article-api` 一个配置点）② 「派工面 P2-T04 ⑤ 有两处形态缺口」＋「一个 yaml 读法坑」③ 「本分支相对主干独有 2 笔，等你叠」⇒ ⇒ 其中 ② 明确是**本面**的活（派工面 owner = 合并收口执行者 ⇒ G4 未自行落码是对的 ⇒ 并发写风险） | ✅ **已落（派工面 `docs/RoundPlan/round-02/README.md:130` · 三处全在同一行内 ⇒ 行数 276 不变 ⇒ 1 增 1 删 = 单行替换 ⇒ 零位移）**：① 「必须逐域配」**升级为「按 client 取值」**（组件级 `cap-millis` 不变 ＋ 新增「按 client 名覆盖」一层 ⇒ 只需覆盖 `article-api` 一个配置点 ⇒ 其余 15 个组件零影响）② 「`SecurityConf.Timeout = 10000` ⇒ 那个出向调用点配 2000 = 比 Go 严 5 倍」**订正为「潜在偏差，不是活路径上的可观察漂移」** ③ **新增「yaml 读法坑」**（顶层 `Timeout` = 服务端方法超时 · 段内 `Timeout` = 客户端上界 ⇒ 同名同值 ≠ 同一件事 ⇒ 读 yaml 必须按段判） | **独立复核（本层自跑，非采信转述）**：① Go 侧 `article/rpc/etc/article.yaml:85` `SecurityConf:` 段 · `:90` `Timeout: 10000` ⇒ **坐标成立**；② RAG 侧 `SecurityRpcClient.java:3` / `ImageSecurityRpcClient.java:3` 的 `@GrpcClient("security-rpc")` **整行都在注释里** ⇒ 全仓**零真实注入点** ⇒ 实际生效的是 `UnavailableContentSecurityClient` ⇒ **该方向不可观测** ⇒ G3 的「取 2000 · 可观察行为优先」成立；③ yaml 三处顶层/段内 Timeout 的段属性逐个核过 |
| ⚠️ **G4 报的「独有 2 笔（`99a1505b` / `6b848946`）⇒ 等你叠」⇒ 实测 2/2 已在主干** ⇒ `align/g4-ledger-close` 相对主干真实差集 = **0** ⇒ ⇒ **零待叠** ⇒ ⚠️ **同一模式第三次**（把 G1 的 `50ce795d` 当自己的 · 把旧轮次提交当「新增」）⇒ ⇒ **「分支上有的 ≠ 本层产出的」** |
| ⚠️ **G4 列的三件「仍挂着」⇒ 两件已过期**：① 「等 G2/G3 收敛后剪 6 条例外」⇒ **已剪到 1 条**（§4.19.42）；② 「等 G3 答复 comment/hot/favorite 三域」⇒ **三域都已收敛** ⇒ 终态 = 1 条（不是 5 条）；③ 「per-client cap 可开工」⇒ **仍待 Jason 授权**（落点 `service/common/**` = G4 写权面 ⇒ G4 停摆中）⇒ ⇒ ⚠️ **绝不能配组件级 `cap-millis=2000`**（会把 70000 改严 35 倍）。明细见 `docs/DRIFT/global/vs-common.md` **§4.19.46** |
| **M-19** | ✅ **G1 lane 跨会话消息的核销：零待合**（2026-10-31 合并收口执行者落码）· 🔴 **并把 ⑤″ 阳性对照的判据重构到第三版**（v1 / v2 两版都被实测打回） | ⚠️ **背景**：G1 lane 发来回执，报「本域新增 1 笔待合 = `a28cc7b7`」＋「转派：`tools/check-ledger-selfcheck.py` 层 0 阳性对照会误判红（样本 = merge commit ⇒ span = 4 ≠ 2 ⇒ 假红）」＋「本域回归 148 / 99 / 138 / 160 全 0F / 0E / 0S」 | ✅ **已核（逐条自跑）**：① `a28cc7b7` **已在主干**（由 **`fd6c9830`** 带入）⇒ ⇒ **零待合** ⇒ 它说「这一笔是新的」不成立；② **转派成立**，但**成因与本层上轮的定性不同** ⇒ ⇒ 本层上轮修的是「位数不一致」（8 位 vs 7 位 ⇒ `_n == _x` 恒假）⇒ ⇒ **两个成因独立并存** ⇒ ⇒ 修掉位数后 merge 形态**仍红**（本层实测）⇒ ⇒ **v2**（取样避开 merge 及其 merge 父）**实测不足**（merge 可藏在**两代之前**；且「父是不是 merge」的判据**根本没生效** —— `rev-list --no-merges --max-count=1 <merge>` 会跳过 merge 继续找 ⇒ 输出非空 ⇒ **正确判法 = 数父的个数**）⇒ ⇒ **v3** 把**与 merge 耦合**的附加断言整个换成与形态无关的三条（`_o is not None` · `_n == _x` · `_x in _sp`）⇒ ⇒ **三场景全对**（merge 样本 / 现有 / 主干） | **验收**：**变异 A**（层 3 拓扑序改成只看 `HEAD` ⇒ 变依赖分支）⇒ **必红**（`EXIT=1`）⇒ ⇒ **⑤″ 仍有牙**；**变异 B**（区间起点写错）⇒ ⑤″ 不抓，**但层 3 自己的判据抓到**（`check_manifest_integrity` ④ 报「多登 1 个」⇒ `EXIT=1`）⇒ ⇒ **换掉附加断言没有留下覆盖缺口** ⇒ ⇒ 「⑤″ 不测区间起点」这个取舍**无害**；**复现手法** = `git clone --shared` 零拷贝独立仓（把 `do-my-own-gh` 指到 merge commit `d00d6f5c` + `HEAD` 指到其父 `52e0497`）⇒ ⇒ **不动 RAG 主干的分支指针**（脚本的 `git(root,…)` 用 `cwd=root` ⇒ `--repo-root` 可指向任意仓）⇒ ⇒ 静态守门 **31 绿 · 0 红 · 2 用法门** · 派生层 `--check` PASS · U+FFFD=0 |
| ⚠️ **「每合一批 lane 就会复发」的机制**：`fd6c9830` **本身是 merge commit** ⇒ ⇒ 主干每次因合 lane 前进 ⇒ ⇒ 都会重新制造「off-branch 样本 = merge commit」的形态 ⇒ ⇒ ⑤″ 这个缺陷**必然复发** ⇒ ⇒ 不是一次性的坏运气 ⇒ ⇒ **判据必须对形态鲁棒** |
| ⚠️ **G1 报的「本域回归 148 / 99 / 138 / 160 全 0F / 0E / 0S」** = **G1 自报**，本层**未亲验** ⇒ ⇒ 引用时须标来源（与「不采信转述」同族）⇒ ⇒ 明细见 `docs/DRIFT/global/vs-common.md` **§4.19.45** |
| **M-18** | ✅ **G4 跨会话消息的核销：无需合并**（2026-10-31 合并收口执行者落码）—— 它报的「待叠 6 笔」**实测 7/7 全在主干** ⇒ ⇒ 零待叠 | ⚠️ **背景**：G4 lane 发来一条跨会话消息，报「本层已把主干合了两轮」＋「相对主干的独有 = `bd916837` ＋ `2691f847` ＋ `50ce795d` ＋ 3 个 merge commit ⇒ ⇒ 叠的时候注意 `:46` 那块」⇒ ⇒ **若照此去「补合并」⇒ ⇒ 会重复合并 / 造冲突** ⇒ ⇒ 故必须核销并留痕 | ✅ **已核（逐条自跑，不采信转述）**：① `git merge-base --is-ancestor` 逐个核 ⇒ **7/7 全部已在主干** ⇒ ⇒ **零待叠**；② `git worktree list` 实测 `g4-ledger-close` 的 HEAD = **`7d419ca0`**（16:22）⇒ ⇒ 它消息里的 HEAD（`50ce795d`）**已过期**；③ ⚠️ **`50ce795d` 的 message 自述是「G1 第二波落码」** ⇒ ⇒ **它把分支上 G1 的 commit 当成了自己的** ⇒ ⇒ 「**分支上有的 ≠ 本层产出的**」；④ **16:40 后该工作树无任何文件写入** ⇒ ⇒ **G4 已停摆** | **它消息里的实质内容全部已在主干**（逐条落到行号核过）：§4.19.31→§4.19.34 的重编号处置在 `:7086` · 「零上界」组件级 0 在 `:6868` · 方向级 1 在 `:7086` · 两口径并存标注在 `:7163` · per-direction 需求面收窄在 `:7437` ⇒ ⇒ **不重复登记**（明细见 `docs/DRIFT/global/vs-common.md` **§4.19.43**）⇒ ⇒ ⚠️ **「共享层缺 per-direction cap」仍待授权**（落点 `service/common/**` = G4 写权面 ⇒ 域侧无法自修） |
| ⚠️ **它报的两个数字的口径订正**：① 「**11 项**机器守门全绿」⇒ ⇒ 本面**枚举口径 = 31 项** ⇒ ⇒ **手写小清单不可当「全仓全绿」**（M-15 同族）；② 「共享层全量 **480 条** / 0F / 0E / 0S」⇒ ⇒ 权威源声明测试方法数 = **481**（`check-source-test-decl-count.py` · 全仓 3,314）⇒ ⇒ **480 < 481** 而**执行条数应 ≥ 源声明数** ⇒ ⇒ **480 不可能是当前代码的执行读数** ⇒ ⇒ **口径未明 ⇒ 引用前须以 surefire XML 复核** ⇒ ⇒ 本项仍「待补读数」 |
| ⚠️ **共享层全量卡住的根因已定位到类**：`service/common/src/test/` 里有 **2 个测试类**引用 `Testcontainers` / `GenericContainer` / `DockerCompose` / `@Container` ⇒ ⇒ **Docker daemon 僵死**（进程在、`docker ps` 挂起）⇒ ⇒ 这 2 类卡在容器连接超时 ⇒ ⇒ **Docker 恢复后即可跑通**（**不是代码问题**；**未擅自重启用户的 Docker**） |

---

### 3-2. 🔴 合并原子性硬约束（2026-10-30 落码轮定 · 合并收口执行者核销）

> **为什么要有这一节**：AGENTS.md §5.3c 说「修法同时涉及共享层与别的域代码时**必须同一次提交**」，
> 但**没说哪几处必须成对** ⇒ ⇒ 并发 lane 各自提交后，合并者若按「域先共享后」的直觉顺序合 ⇒ ⇒
> **中间态不是一个「短暂不一致」，而是一个真回归**（RAG 做了 Go 不会做的事）。

**本波唯一一对必须同提交的改动（收口执行者已独立复核）**：

| 侧 | 改动 | 落点 |
|---|---|---|
| 共享层（G4） | Redis **派发前**期限感知（`GoStyleRedisDeadline*` ＋ `AutoConfiguration.imports` 登记 ＋ `BeanPostProcessor` 换掉 `RedisTemplate` 的 connection factory） | `service/common/src/main/java/com/rag/common/redis/GoStyleRedisDeadline*.java`（G4 commit `3d5b4924`） |
| 域侧（G1） | `AdminDomainService.logout` **删掉域内预检 `checkDeadline()`** | `service/users/admin/admin-rpc/.../service/AdminDomainService.java:259` |

**Go 侧的机制事实（收口执行者自读逐行核对 · 不采信任何一方的转述）**：

- **客户端可见层**：真源**自己**的超时拦截器（`Sea-RideTheWind_golang/service/common/observability/rpc.go:25-74`，注册于 `service/user/admin/rpc/admin.go:43`）⇒ `:59-72` 的 `select`：`done` 分支要**先拿锁**（`:64`），而那把锁被 handler goroutine 在**整个 handler 执行期**持有（`:53-54`）⇒ ⇒ 预算超了时 `done` 分支拿不到锁 ⇒ ⇒ **超时分支先就绪** ⇒ ⇒ `:69` `status.Error(codes.DeadlineExceeded, …)` ⇒ ⇒ **Go 客户端可见码 = 1015，且是确定的**。
  - ⚠️ **别引错拦截器**：`go-zero@v1.10.2/zrpc/internal/serverinterceptors/timeoutinterceptor.go` **在这条链路上是死代码** ⇒ 真源 `rpc.go:19-23` 的 `DisableNativeRpcTimeout` **先取出原 timeout、再把 `conf.Timeout` 置 0** ⇒ 置 0 关掉的是 **go-zero 的原生拦截器**（`zrpc/server.go:164` 的 `if c.Timeout > 0` 不成立）⇒ ⇒ **生效的是真源自己那个**（它带着刚取出的原 timeout）。
- **handler 级层**：`Sea-RideTheWind_golang/service/user/admin/rpc/internal/logic/logoutlogic.go:38` ⇒ `BizRedis.SetexCtx(l.ctx, …)`（**写绑请求 ctx**）；失败 ⇒ 同文件 `:39-41` ⇒ `status.Error(codes.Internal, "注销失败,Redis写入异常")` ⇒ 业务码 `service/user/common/errmsg/errmsg.go:7` = **5001**。
- **Redis 派发前**：Go 侧唯一的写闸门 = `go-redis/v9@v9.20.0/internal/pool/pool.go:1098-1104` 的 `waitTurn` fast path（无条件先查 `ctx.Done()`）⇒ `pool.go:837` 的 `getConn` 必调它 ⇒ ⇒ **Go 侧没有任何域内预检** ⇒ ⇒ **RAG 侧要同形，就必须只有一道闸门** ⇒ ⇒ 域内那第二道（`checkDeadline()`）必须让位给共享层那道。

**⇒ ⇒ ⚠️ 两层别混读（本节最容易判反的地方）**：

- **客户端可见层**：Go 1015 = RAG 1015 ⇒ **已同形** ⇒ ⇒ 域内预检删不删，**这一层都不变**（RAG 侧 `service/common/.../observability/RpcInterceptor.java:341-342`（客户端 deadline 已过期 ⇒ `effective=0` ⇒ 调度器**立即** fire）＋ `:358-366`（`close(DEADLINE_EXCEEDED)`）⇒ ⇒ 先 close 的赢）。
- **handler 级层**：Go 5001 ≠ RAG 现状 1015 ⇒ ⇒ **这一层不同形 ⇒ 正是域侧要改的那一层** ⇒ ⇒ **删掉域内预检 = 向 Go 对齐**（域侧不再抢先拦 ⇒ 共享层在派发前拦 ⇒ 落进既有 `catch (Exception e)` ⇒ `INTERNAL`「注销失败,Redis写入异常」）。
- 🔴 **⇒ ⇒ 但「两层分开读」还差第三步：读「集合」，不读单点**（2026-10-30 合并收口执行者实测并落仓）
  ⇒ ⇒ **Go 侧在「请求 deadline 已过期」时，客户端可见码集合 = {1015, 5001}** —— 不是「恒 1015」：
  真源 `service/common/observability/rpc.go:38` ⇒ `timeoutCtx` 一创建就是到期的（父 ctx 已过期）
  ⇒ ⇒ `:59-72` 的 `select` 里 **`done` 与 `timeoutCtx.Done()` 可能同时就绪** ⇒ ⇒ **Go 的 `select` 在多个就绪分支间随机选**
  ⇒ ⇒ 走 `done` 分支（`:63-67`）就**返回 handler 的 err**（= `codes.Internal` = 5001）。
  ⇒ ⇒ **实测**（探针 `tools/semantic-probe/client-visible-code/`，200000 轮 ⇒ 5001 命中 **22** 次）⇒ ⇒ **窄，但可观察**。
  ⇒ ⇒ ⇒ **所以**：**保留**域内守卫 ⇒ RAG 集合 = {1015} = **Go 集合的真子集** ⇒ **仍是一处差异** ⇒ **删掉**守卫 ⇒ RAG 集合 = {1015, 5001} ⇒ ⇒ **同集合 ⇒ 同形**。
  ⇒ ⇒ ⇒ 逐条机制 / 复算数 / 探针纪律 ⇒ [`docs/DRIFT/_cross/client-visible-code-set.md`](DRIFT/_cross/client-visible-code-set.md)。
  ⇒ ⇒ ⚠️ **纪律**：凡断言「某个分支**永远**走不到」，要么给一条**可运行的判据**，要么降级成「实践中如此」 ⇒ ⇒ **「几乎总是」≠「必然」**。

**⇒ 四种合并态的行为对照**（判据 = 「RAG 是否写了 Go 不会写的 Redis」）：

| 合并态 | RAG 的 logout 行为 | Go 的行为 | 判定 |
|---|---|---|---|
| 现状（无代理 · 有域内预检） | 域内预检先拦 ⇒ 客户端可见 1015 · **handler 级 1015** | 客户端可见 1015 · **handler 级 5001** | 客户端可见同形 · **handler 级分叉** |
| **只合 G1**（删预检 · 无代理） | **预算耗尽仍把 token 写进黑名单** | **不写** | **真回归（数据面）** |
| 只合 G4（代理已合 · **但预算检查不在生效路径上** · 预检仍在） | 域内预检先拦 ⇒ 客户端可见 1015 · handler 级 1015 | 客户端可见 1015 · handler 级 5001 | 数据面与现状一致（预检仍拦）⇒ ⚠️ **但共享层此刻是「空转」的 ⇒ 不能据此说「代理已生效」** |
| **G4 + G1 同一次提交** | 代理在派发前抛 `QueryTimeoutException` ⇒ 域侧 `catch (Exception e)` ⇒ **handler 级 5001** · **不写**；客户端可见仍 1015（拦截器先 close） | 不写 · handler 级 5001 · 客户端可见 1015 | **两层同形** |

**⇒ 结论（硬约束，不是偏好）**：

1. **G4 的 `3d5b4924` 与 G1 的 logout 改动必须落在主干上的同一个 commit** ⇒ ⇒ 任何「先合 G1 后合 G4」或「拆成两个 commit」的做法 ⇒ **中间态就是上表第 2 行 = 真回归**。
2. **G1 不能自己合 G4 的提交**（`service/common/**` 不在 G1 写权面 ⇒ `tools/difftest/check-wave-lane-ownership.py` 判红）⇒ ⇒ **那一个原子 commit 由合并收口执行者在主干上做**。
3. G4 的共享层**单独合入在数据面上是中性的**（域内预检仍在 ⇒ 行为不变）⇒ ⇒ 若因故必须先合 G4 ⇒ **不得先合 G1**。
   ⚠️ **但这条中性只讲「数据面行为」** —— **不等于「共享层已生效」**：已提交的代理把预算检查放在**连接获取之后**（`GoStyleRedisDeadlineConnectionFactory.java:39-43` 先连后包）⇒ ⇒ **它在生效路径上拦不住** ⇒ ⇒ **合并门 = 「检查被提到 `delegate.getConnection()` 之前 ＋ 守门转绿」**，不是「commit 在位」。
   ⇒ ⇒ 实证与判定式 ⇒ [`PIT-AXIS-04`](Pitfalls/_cross/pointer-axes.md)。
4. ⚠️ **落码顺序（AGENTS.md §3.3 ③ 先改断言）**：先改 `service/users/admin/admin-rpc/src/test/java/com/rag/users/admin/rpc/service/AdminDomainServiceDeadlineTest.java:41-43` 的 `GUARDED` 清单（**含 `"logout"`** ⇒ `:77` 断言 `DEADLINE_EXCEEDED`）⇒ 再改 `AdminDomainService.java:259` 的实现。

**⇒ ⇒ 另附一条同源的「出口口径分层」（G1 提 · 收口执行者核过成立）**：

指标抓取出口**必须分两层**，单层任一数都会误导拍板：

- **配置层** = actuator endpoint 有没有开（G4 逐域实测：3 个三重关闭 ＋ 三态划分）；
- **载体层** = 进程**有没有 HTTP 容器**（`spring-boot-starter-web` ⇒ `tomcat-embed-core`）⇒ 逐模块 `mvn -o -B dependency:list` 实测（⚠️ **2026-10-31 合并收口执行者复测**）：**10 个 rpc 里 8 个有** `tomcat-embed-core`（article / favorite / follow / hot / like / message / user-user-rpc / user-admin-rpc）⇒ **只有 comment-rpc / task-rpc 两域无载体（2/10 无）** ⇒ ⇒ **原文「只有 follow-rpc 有 ⇒ 9/10 无载体」已被实测推翻** ⇒ ⇒ `exposure.include` 写了但没载体 ⇒ 抓取方连不上 HTTP；
- ⇒ ⇒ **净差 = Go 侧 8/10 有独立观测端口 vs RAG 侧 1/10 有载体 ⇒ 7/10 域「RAG 侧抓不到」**
  （🔴 **订正**：本节原写「Go 侧 6/10 · 净差 5/10」⇒ ⇒ **是枚举漏了嵌套路径**：`ls service/*/rpc/etc/*.yaml` **跨不过两层** ⇒ ⇒ 漏掉 `service/user/user/rpc/…`（**9094**）与 `service/user/admin/rpc/…`（**9097**）⇒ ⇒ 实际 **8/10**；教训 ⇒ [`PIT-SCAN-01`](Pitfalls/_cross/scan-coverage.md)）；
  ⇒ ⇒ **Go 侧观测端口全表（2026-10-30 收口执行者逐个 yaml 核过）**：`article-rpc 9092`（`article.yaml:21-24` 的 `Prometheus:`）· `favorite-rpc 16062`（`favorite.yaml:23-26` 的 `DevServer:`）· `follow-rpc 16065`（`follow.yaml:20-23`）· `hot-rpc 19091`（`hot.yaml:8-11`；另有 hot2=19092 / hot3=19093）· `like-rpc 9098`（`like.yaml:19-22`）· `message-rpc 16067`（`message.yaml:19-22`）· `user-rpc 9094`（`user.yaml:23-26`）· `admin-rpc 9097`（`admin.yaml:31-34`）⇒ ⇒ **`comment-rpc` 与 `task-rpc` 两侧都没有 ⇒ 同形 ⇒ 不补**；
- ⚠️ **判据纪律**：**不要 grep pom，要查依赖树**（`service/common/pom.xml:361` 的 web starter 是 `test` scope ⇒ 不传递；且父子继承下「grep 不到」≠「运行时没有」）⇒ ⇒ 这是 AGENTS.md §3.3.1 的**反面**：「配置里写了」≠「开得了」；
- ⚠️ **JMX 边界**：Prometheus 的抓取模型是 HTTP scrape ⇒ JMX 不能替代 ⇒ 仍记为无出口；
- ⇒ ⇒ **落点是各域 yml（域侧写权）⇒ 不归 G4** ⇒ ⇒ ✅ **已拍板（Jason 2026-10-30：「这波就补载体」）** ⇒ ⇒ **派工面 = 7 个域**（G1：`user-rpc 9094` + `admin-rpc 9097` ＋ 核对 `follow-rpc 16065`；G2：`message-rpc 16067` + `like-rpc 9098`；G3：`article-rpc 9092` + `favorite-rpc 16062` + `hot-rpc 19091`）⇒ ⇒ **模板 = `follow-rpc` 现有形态**（pom 第 5 段加 `spring-boot-starter-web` ＋ yml `server.port` = Go 端口 ＋ actuator exposure）⇒ ⇒ ⚠️ **端口一字不差、路径不同**（Go `/metrics` vs RAG `/actuator/prometheus`）⇒ ⇒ 照 follow-rpc 已登记的口径写，**不新造偏离登记**；
- 🔴 **补载体的共享层副作用（待 G4 裁定 · 合并期动作 ⇒ M-7）**：加了 web starter 之后，`service/common` 的 **12 个 `@ConditionalOnWebApplication(SERVLET)` 的 web Bean**（含 `GoStyleCpuSheddingFilter` / `GoStyleMaxConnsFilter` / `GoStyleBreakerFilter` / `GoStyleTomcatParameterLimitCustomizer` …）＋ **4 个无 servlet 条件的 `@Component`**（`GoStyleFormValues` / `GoStylePathNormalizingFilter` / `AuthorityHandlerFactory` / `AuthorityHandler`）会**在 rpc 域激活** ⇒ ⇒ 而 **Go 的 rpc 侧是 `zrpc.MustNewServer` ⇒ 一个 rest 中间件都没挂**（`rest.With*` 全在 api 侧）⇒ ⇒ **「rpc 域观测端口挂 Go 的 api 侧中间件」= 一处真偏离**（有杀伤力的是 CPU 降载：超阈时它会**拒掉抓取请求**，而 Go 的 DevServer 没有这层）⇒ ⇒ **G4 须在 `service/common` 裁定 + 落码**。

---

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

---

## 七、M-22：跨工作流分支合入 ＋ 写权越界裁决 ＋ 源声明数口径订正（2026-10-31 · 合并收口执行者）

> ⚠️ **为什么追加在文末、而不是插进 §五 的 M-xx 表**：本文件**有硬行号指针** ——
> `docs/DRIFT/users/user/rpc.md:3010` 引 `docs/Worktree.md:587` ·
> `docs/CLOSEOUT-2026-10-29.md:117` 引 `:274` / `:108` ⇒ ⇒ 中段插一行 ⇒ **后面所有行号整体位移**
> ⇒ ⇒ ⇒ 只能**文末追加**（零位移）。

### ① 背景：出现了**第五棵分支**（不在 G1~G4 波次表内）

- 第三波收口后（HEAD `eba740e0`）清点工作树，发现 `align/ci-concourse`（**13 笔未合主干** ·
  工作树 `RAG.worktrees/ci-concourse`）⇒ ⇒ 它来自**另一条协调线程**（Concourse CI 阶段一）⇒ ⇒
  **不在 §二 的 G1~G4 波次表内 ⇒ 无 lane 写权**；同时 `align/g1-users-follow` 新增 1 笔（`f8438ee6`）。
- ⇒ 两笔一并合入：`7e945722`（G1 · 纯末尾追加 84 行 ⇒ 零位移）＋ `4b4bb10e`（ci-concourse · 86 文件）。

### ② 冲突裁决（1 处）：**主干侧是错的**

- 冲突文件 = `service/users/admin/admin-api/pom.xml`：主干侧 `44b8b80b` 写 `<relativePath>../../pom.xml</relativePath>`，
  分支侧写 `../../../pom.xml`。
- **独立核坐标链**（**排除 `<parent>` 块后**取自身 artifactId）：`service/pom.xml`=`service` ·
  `service/users/pom.xml`=`users` · `service/users/admin/pom.xml`=`admin`；而 `admin-api` 声明的父 artifactId=**`service`**
  ⇒ ⇒ `../../pom.xml` 指向 artifactId=**`users`** ❌ · `../../../pom.xml` 指向 artifactId=**`service`** ✅
  ⇒ ⇒ **判定：分支侧正确，主干侧是错层数的真缺陷**（干净 checkout 无制品仓库 ⇒ 父 POM 解析必炸；
  开发机不报错是被 `~/.m2` 里旧的已安装 `service` pom 掩盖 ⇒ 属「配置存在但没生效」形态）⇒ ⇒ **取分支侧**。
- **合并后复扫**：**51 个 `com.rag` 内父 POM 全部解析正确 · 0 错**（外部父 `spring-boot-starter-parent` 不计）
  ⇒ ⇒ 与仓库自带守门 `ci/scripts/check-pom-parent-relativepath.py` **EXIT=0 一致** ⇒ ⇒ **互证**。
- ⚠️ **取证时踩的坑**：第一次核对坐标链时用「取文件里**第一个** `<artifactId>`」⇒ ⇒ 拿到的是
  **`<parent>` 块里的那个**（= 父的 artifactId）⇒ ⇒ 把 `service/pom.xml` 误读成 `Sea-RideTheWind_java`
  ⇒ ⇒ **结论差点反过来** ⇒ ⇒ 判据必须**排除 `<parent>` 块**后取自身 artifactId。

### ③ 写权裁决：越界登记，但**保留其改动**

- `align/ci-concourse` 改了 `tools/difftest/check-wave-lane-ownership.py`（**写权机器口径** ⇒ 归本面）⇒ ⇒ **越界**。
- 但它的改动本身是**改善**：第 7 条判据在「**无 lane 上下文**」（Concourse 的 git resource = 单 commit detached HEAD ⇒
  `branch_exists()` 恒假 ⇒ 该条恒红 ⇒ 门根本进不了 CI）时改为 **NOT_RUN**，且**如实打印 NOT_RUN**
  ⇒ ⇒ 修掉了「**跳过看起来像通过**」的假绿表述 ⇒ ⇒ **予以保留**（登记越界，不回退）。

### ④ 守门清单口径：**第二次补全**（前两次分别是 M-15 / M-16）

- 此前「31 绿」只枚举 `tools/**/check-*.py` ⇒ ⇒ **漏了 `tools/difftest/check-*.sh` 4 个**（`proto-static` /
  `jar-executable` / `plaintext400-headers` / `flyway-concurrent`）⇒ ⇒ 又**新增了 `ci/scripts/check-*.py` 7 个** ⇒ ⇒
  ⇒ 本轮把枚举扩到**全仓**（`.py` + `.sh` + `ci/scripts`）。
- 实测：`ci/scripts` 7 道 = **5 绿 · 2 红**（红项当时是 **Docker 镜像 blob 损坏** ⇒ 环境性；
  且 `check-schema-contract.py` **正确报 `BLOCKED` 而不是「通过」** ⇒ 门本身没假绿）⇒ ⇒ Docker 恢复后补跑：
  **R6 Schema 契约门 8 域 PASS · 0 FAIL · 0 BLOCKED · 1 NOT_RUN（`hot` · DDL 维度不适用 · 门内明写「**不是「已对齐」**」）**
  ＋ **R6 变异对照 9/9 精确转红** ⇒ ⇒ 该门**不是假绿**。

### ⑤ Docker 病根定位：**daemon 是活的，坏的是 content store**

- 此前记的「Docker daemon 僵死」（`docker ps` 挂起）**与本轮实测不符**：`docker version` 返回 **Server 29.2.1** ·
  API 有响应 ⇒ ⇒ 真病根 = **content store 缺 blob**（镜像**元数据在、实体层被 GC**）⇒ ⇒ 建容器 HTTP 500
  ⇒ ⇒ `docker pull` 补 blob 时**镜像代理断流 EOF** ⇒ ⇒ 本机当时无法自动修。
- ⚠️ **本轮 Docker 已自愈**（外部重启）⇒ ⇒ ⇒ **两处被它阻塞的项已补跑**：
  ① **共享层全量回归跑完**（此前 6 分钟无进展的 Testcontainers 类全部恢复）⇒ **surefire XML 权威读数
  `tests=480 · skipped=0 · failures=0 · errors=0`**（90 个 XML · BUILD SUCCESS 1:19）；
  ② **产物门**：全量 `mvn package`（58s · BUILD SUCCESS）后 `check-jar-executable.sh` ⇒ **20/20 个 api+rpc 模块的 jar 均可执行**。

### ⑥ 源声明数口径订正：「480 vs 481」定案（真因是**计数口径**，不是少跑了一个测试）

- 悬案：`service/common` 的**执行读数 480** vs **源声明数 481** ⇒ ⇒ 当时判「**口径未明**」。
- **根因**：`check-source-test-decl-count.py` 用**全文 `findall`** 数测试注解 ⇒ ⇒ **注释 / javadoc 里提到的
  `@Test` 文本也被算成一个测试方法** ⇒ ⇒ 定位到 `HttpShutdownProbeWithoutServlet` 的 javadoc 里
  `{@code @Test}`（那个类**刻意不以 `Test` 结尾** ⇒ surefire 不会跑它）⇒ ⇒ **虚高 1**。
- **修法**：守门加 `code_state_mask()`（注释 / 字符串 / 字符字面量 / 文本块 ⇒ **等长空格** ⇒ 行号不位移）
  ＋ **M4 自证**（阳性 = 真实 `@Test` 一个不少；阴性 = javadoc 那处恰好少 1）⇒ ⇒ **只算代码态**。
- **订正后的值**（**全仓 −7**）：`service/common` **481→480** · `follow-api` **101→99** ·
  `user-api` **140→138** · `admin-api` **162→160** ⇒ ⇒ **全仓 3,314 → 3,307**。
- ⇒ ⇒ **互证成立**：订正后**共享层源声明 = 480 == surefire 执行读数 480** ⇒ ⇒ 「480 vs 481」**已定案**。
- ⚠️ **同一量不许有第二份实现**：`check-phasegoal-consistency.py` 里**抄了第二份**计数（口径不同 ⇒ 改完它立刻变红
  ⇒ ⇒ 但它红的原因**不是「基线坏了」而是「第二份实现没跟着改」）⇒ ⇒ 改成 **import 同族的 `code_state_mask`**
  ⇒ ⇒ 一个量一个实现。
- ⚠️ **两次自踩**：① 我自己的**复扫脚本 glob 深度不够**（`service/*/*/src/test/java` 覆盖不到
  `service/users/user/user-api` 三层深）⇒ ⇒ 算出 **−3 而真值是 −7** ⇒ ⇒ **是守门自己的组件映射表
  （覆盖 39 个组件）抓出了全量** ⇒ ⇒ 「**扫描面决定结论**」；② 我第一次写的**自证谓词反了**
  （把「掩码后还剩 ⇒ 是代码」写成「原始 > 掩码 ⇒ 是代码」）⇒ ⇒ 自证 FAIL ⇒ ⇒ **差点反着把判据改坏**
  ⇒ ⇒ **自证失败时先怀疑自证**（读原始/掩码后的数字，别信自己写的 `if`）。

### ⑦ U+FFFD 归因：**31 处 / 11 文件 ⇒ 28 处是存量**

- 逐文件比对合并前后计数：**9 个文件合并前后计数不变**（存量）⇒ 含 `tools/ledger-derive/derive-stage1.py` 的 3 处
  ⇒ ⇒ **全在注释里**（`derive-stage1.py:426` 那行注释首部的 3 个替换字符）⇒ ⇒ **不是**派生层 `entry_blocking_decl` 未解之谜的根因（谜底仍未查清）。
- **本轮新引入 3 处** = `ci/scripts/check-requirements.py:270` 的**注释行** ⇒ ⇒ **不参与匹配 ⇒ 无功能影响**。

### ⑧ 验收（本轮合并 + 订正后）

- **静态守门**：`tools/**` **31 绿 · 0 红 · 2 用法门**（与订正前持平）＋ `ci/scripts` **7 道全绿**（Docker 恢复后）
  ＋ `.sh` 守门：`check-proto-static.sh` **10/10 域 proto wire 契约逐字节一致** ✅ · `check-jar-executable.sh` **20/20** ✅
  （另 2 个需真 Tomcat / 真 PG ⇒ 仍受环境制约）。
- **派生层**：重生成 **md / tsv 逐字节零差异** · `derive-stage1.py --check` **PASS**。
- **指针棘轮**：**3383 指针 · 存量例外 237 → 当前 237 · 新增 0 · 已消失 0** ✅。
- **pom 父解析**：**51/51 正确 · 0 错**（独立复扫 ＋ 仓库自带门，互证）。
- **零行位移**：`docs/PhaseGoal-judge.md` **727 行不变** · `docs/DRIFT/global/vs-common.md` **7957 行不变**。
- **共享层全量回归**：`tests=480 · skipped=0 · failures=0 · errors=0` ✅。

### ⑨ 遗留（下一轮接手须知）

- ⚠️ **`align/ci-concourse` 工作树仍有未提交改动**（`ci/scripts/run-static-gates.sh` · `ci/verify-triggers.sh`
  · 18:xx 写入）⇒ ⇒ **那条工作流还活着** ⇒ ⇒ 本面合入的是 **17:56 的快照** ⇒ ⇒ 它再提交后**需再合一次**。
- ⚠️ **四棵 lane 配额仍耗尽**（G1/G2/G4 `42212` · G3 `50113`）⇒ ⇒ 本轮的所有「代落码」都是 §5.3f 的**例外路径**。
- ⚠️ **本面未做**：push（**310+ 笔未推**）· per-client cap 落码（`article-api` 缺 70s 上界 · G4 写权面）
  · 4 处域侧私有常量 + 判据②（users/follow 域面）· E66 定级复判。｜ ⚠️ **【2026-10-31 · 合并收口执行者】原同行并列的 `entry_stage_decl` 口径 · `§4.19.6` 重判 两项已销案**（⇒ **不再需要 Jason 拍板**）⇒ 详见本文件**文末 M-31** ＋ `docs/DRIFT/global/vs-common.md` **§4.19.55**。

---

## 八、M-23：G3 跨会话回执的核销 ＋ 「W6 的 C-3 行」订正 ＋ 一个**判据形态脆性**的登记（2026-10-31 · 合并收口执行者）

### ① 背景：G3 第十三轮回执（api 侧硬上界收敛）

- G3 报「本分支比主干多 2 笔」⇒ 实测**分支独有 = 0** ⇒ ⇒ `049ffb66` ＋ `74211082` **已全在主干**
  ⇒ ⇒ **零待合**（这是 lane 回执说「待合」而实际早已合入的**第五次**：G1 / G2 / G4 各一次）。
- G3 的落码**已在主干且已核**：`comment-api` / `favorite-api` / `hot-api` **三个域 yml 已配 `cap-millis`**（代码态）
  · `favorite-api` **新建 `rag:` 段** · `article-api` **刻意不配**（两方向 Go 真值不同 ⇒ per-direction 内联必需）
  · 三处域 client 的**内联 cap 已删**（只剩 javadoc 提及）⇒ ⇒ 与 G3 自述一致。

### ② G3 的硬请求（「棘轮剪例外必须与删内联同一次提交」）⇒ **已满足，零红窗已达成**

- G3 担心：共享层棘轮 `GoStyleClientCallTimeoutSingleSourceGateTest` 报红「例外已失效」⇒ ⇒ 要求 G4 把 G3 分支合进去、
  **在同一个 merge commit 里剪那 4 条例外**。
- **事实**：例外集已由**本面在 G4 停摆时代剪并合入主干**（`50f2d51f` ⇒ 例外集 **7 → 3 → 1**，只剩 `article-api` 那条不可收敛的）
  ⇒ ⇒ **主干上「删内联」与「剪例外」已经是同一批落码** ⇒ ⇒ **零红窗已达成** ⇒ ⇒ **不需要 G4 再动、也不需要「G4 先合 G3」这个排序**。
- 本轮**共享层全量回归实测**：`tests=480 · skipped=0 · failures=0 · errors=0` ⇒ ⇒ 棘轮在主干上是**绿的**。

### ③ G3 的第 ③ 项（W6 的 C-3 行与派工面口径不一致）⇒ **本面已就地订正 W6.md:15**（零行位移）

- **不一致是真的，但方向与直觉相反**：派工面 `C-3` 显示 `✅ 已一致`（由 judge 覆盖规则 `U2-2` 产生），
  而 W6 的 C-3 行写着「现行开放面 = `:112` 的 global E65（未闭合 · A 类 · **阻断阶段一**）」⇒ ⇒ **看着像派工面假绿**。
- **实测三段事实**（本面自己跑，不是采信回执）：
  ① `comment/vs-common.md:112` **自己 2026-10-30 的订正**已写明「**继承那一半本域已闭合**」（双侧活体逐键相等）；
  ② judge §五 `X-6` 行的残余②「**客户端硬上界 2000ms 未启用**」= **P2-T04** ⇒ ⇒ **P2-T04 ⑤ 本轮已落**（G3 落码三个域 ＋ 本面机制面）；
  ③ 本轮实测 `python3 tools/difftest/check-deadline-downstream.py` ⇒ **EXIT=0 · 四键逐键相等**
  （`failed_fast` Go=true/RAG=true · `code` 4/4 · `outgoing_carrier` equal · `sql_carrier` equal）。
- ⇒ ⇒ **事实层已闭合** ⇒ ⇒ ⇒ **派工面的 `✅ 已一致` 是对的** ⇒ ⇒ ⇒ **过期的是 W6 那行**（不是派工面假绿）
  ⇒ ⇒ **订正 W6.md:15**（就地单行替换 ⇒ 28 行不变 ⇒ 零位移；且**新文字刻意不含任何三态 emoji** ⇒ 不扰动基于标记的判据）
  ⇒ ⇒ **派生层重生成后 md/tsv 逐字节零差异 · `--check` PASS** ⇒ ⇒ 派工面那侧**不用动**。
- ⚠️ **「阻断阶段一 = 是」这个定级本层不擅自翻**（事实变了 ⇒ 定级是决策）⇒ ⇒ 并入 Jason 的
  **E66 定级复判 / `entry_stage_decl` 口径**那一批一并议。

### ④ 🔴 顺手查出一个**判据形态脆性**（工具层 · 本轮**只登记不落码**）

- `tools/ledger-derive/derive-stage1.py` 的 `load_judge_closed()`（第 773 行起）判据 =
  「judge 的行文本里出现**已闭合 / 已解除 / 已销案**」⇒ ⇒ **子串命中**。
- **实测影响面**：25 行给「已闭合」集合供数 ⇒ 其中 **5 行的结论是「不满足 / ❌ / 🔴」**（`X-6` · `X-11` · **`C-3`** · `G-14` · `D-02`）
  ⇒ ⇒ 即「**正文提到『已闭合』**」被当成了「**断言整条已闭合**」⇒ ⇒ 子面闭合会顶掉整条未闭合。
- ⚠️ **叠加第二个缺陷：编号空间混淆** —— judge:417 第一格 = 「**C-3 跨组件不变量**」（= **阶段一验收判据编号**），
  而台账 `C-3` 是**台账条目编号** ⇒ ⇒ `all_id_tokens` 把前者当成了后者 ⇒ ⇒ **同名不同物**。
- ⇒ ⇒ **今天 `C-3` 判「已一致」恰好是对的**（因为 P2-T04 刚完成、而 judge 是权威源）⇒ ⇒ ⇒ **侥幸对 ≠ 判据对**
  ⇒ ⇒ 若按「结论列必须断言闭合」把判据改严 ⇒ ⇒ 会把 `X-6` / `C-3` 这类**真闭合**的判成未闭合 ⇒ ⇒ **大面积假红**
  ⇒ ⇒ ⇒ **本轮不动**（改它需要**逐条人工裁定 25 行**）⇒ ⇒ 登记为工具层待办。

### ⑤ 共享层本轮出现的 1 个失败 ⇒ **环境性，不是回归**

- `RpcServerSpanCurrentGateTest.handlerSeesTheSameSpanThatIsExported` 红 ⇒ 症状 = `http2 exception … First received frame was not SETTINGS. Hex dump: 485454502f`（= **「HTTP/」**）
  ⇒ ⇒ **gRPC 端口上收到 HTTP 响应** ⇒ ⇒ 端口被别的进程占（当时机器上有 7 个 Python 服务在监听 8082 / 8092~8097 / 51234）。
- **隔离复跑 = 4/4 绿 · BUILD SUCCESS** ⇒ ⇒ **环境性**；且 18:13 那次全量是 **480/0F**，其间本面合并只碰 `ci/` · `tools/difftest/docker_api.py` · `docs/`
  ⇒ ⇒ **没碰 `service/common`** ⇒ ⇒ 两头都指向环境。
- ⚠️ 顺带订正一处**形态差异**：G3 报的「跨 lane 并发抢**固定端口**」与本例**不同** —— 该测试用 `ServerBuilder.forPort(0)`（**临时端口**）
  ⇒ ⇒ **两者不是同一形态** ⇒ ⇒ 「端口冲突」这句话要分形态说（固定端口 vs 临时端口被抢占）。

## 九、M-24：§4.19.30 第四 / 六节的现行态落账（代 G4）＋ 共享层 Redis deadline 判据的 A/B 双变异实测 ＋ 「死配置转告各域 yml 面」核销（2026-10-31 · 合并收口执行者）

### ① 为什么这轮由本面代 G4 落码

- `docs/DRIFT/global/vs-common.md` ＋ `service/common/**` = **G4 写权面** ⇒ ⇒ G4 lane **停摆中**
  （工作树 `align/g4-ledger-close` 自 16:22 起无新提交、无文件写入 ⇒ 判据 = **产物最后写入时间**，不看会话状态字段）
  ⇒ ⇒ 本面在**其写权面内**代落 ⇒ ⇒ **commit message 注明原因**（§5.3b-0 / §5.3e「代落码须注明原因」）。
- 本轮落的是**台账落账 ＋ 已有产出登记**，**零行为改动**（不改任何生产代码、不改任何判据）⇒ ⇒ 不需要新的行为授权。

### ② §4.19.30 第四 / 六节 = 当时的快照 ⇒ ⇒ 现行态（7 个点位 → 终态 1 条）

| 点位 | 当时的形态 | **本层实测的现行态** | 收敛动作 |
|---|---|---|---|
| `comment-api` `CommentRpcClient` | 域侧私有常量 2000ms | 内联 `withDeadlineAfter` = **0** · yml `:167` 已配 `cap-millis` | `049ffb66` ＋ `3aa9accd` |
| `hot-api` `GoStyleGrpcCallDeadline` | 域侧私有常量 2000ms | ⚠️ 仍有 **2 处** `withDeadlineAfter`（`:23` / `:25`）⇒ **但都不是私有 cap**（`:23` = 预算耗尽给 1ns · `:25` = **传播入向剩余预算**）⇒ 私有 2s 常量已删 · yml `:168` 已配 | 同上 |
| `favorite-api` `FavoriteRpcClient`（**2 个点位**） | 域侧私有常量 2000ms | 内联 = **0** · yml `:197` 已配（两条**同一次提交**剪 ✔） | 同上 |
| `like-api` `LikeRpcClient` | **域侧私有配置**（唯一不是常量的一条） | 内联 = **0** · yml `:174` 已配 | `3c0d9887` ＋ `4b7b3a9c` |
| `task-api` `TaskRpcClient` | 域侧私有常量 2000ms | 内联 = **0** · yml `:262` 已配 | 同上 |
| **`article-api` `UserRpcClient`** | 域侧私有常量 2000ms | **未收敛（刻意保留）** ⇒ 仍在 `.../article/api/client/UserRpcClient.java:26`（`withDeadlineAfter(effective, …)`）⇒ **例外集唯一一条 ⇒ 不许剪** | — |

⇒ ⇒ **终态 = 6 条已收敛 ＋ 1 条保留**（例外集 `7 → 3 → 1`：`3aa9accd` 同批剪 4 条 ＋ `50f2d51f` 补剪 2 条）。
⚠️ **第六节那张表的「组件级 cap 现状」列也已过期** ⇒ ⇒ 本层逐组件实测（19 个有 `application.yml` 的组件）= **15 个已配 `cap-millis`** ·
**4 个未配** = `article-api`（**刻意** · 两方向 Go 真值 70000 / 2000 不同 ⇒ 组件级单值表达不了）· `hot-rpc` · `task-rpc` · `admin-rpc`（这三个**零出向注入点** ⇒ 不配是对的）
⇒ ⇒ ⇒ **「`article-api` 绝不能配 `cap-millis=2000`」的警告至今成立、仍然有效**（配 2000 = 把 article-rpc 方向改严 35 倍）⇒ ⇒ 本面不代改（域面写权 = G3）。

**落账方式** = **就地单行标注 4 处**（第四节标题行 / 第四节 intro / 第六节标题行 / 第六节 intro · **单行 → 单行**）
＋ **文末追加 `§4.19.49`** ⇒ ⇒ **零位移**（`git diff --numstat` = **4 增 4 删 ＋ 60 行新增** · 7957 → 8017 行 · 前面 7957 行**逐行未动**）
⇒ ⇒ 派生层 `--check` **PASS**（1284 数据行）· 指针棘轮 **3384 指针 · 例外 237 → 237 · 新增 0** · U+FFFD = **0** · 反斜杠计数与 HEAD 基线相等。

### ③ 共享层 Redis deadline 判据的 A/B 双变异实测（G3 lane 请求 · 代 G4 落码）

| 变异 | 预期 | **实测** | 阴性对照 |
|---|---|---|---|
| **A** · 拆 BPP 包装（`GoStyleRedisDeadlineAutoConfiguration` 的 `template.setConnectionFactory(...)` 换注释） | 装配面转红 | ✅ **精确转红**：`GoStyleRedisDeadlineGateTest.connectionFactoryIsWrapped`（判据①）· `exhaustedBudgetBlocksTheCommand` · `GoStyleRedisDeadlineRealLettuceGateTest` 的 `bootTemplatesAreWrapped` / `exhaustedBudgetBlocksBeforeAnyConnectionAttempt` / `delegateIsTheRealLettuceFactory`（ClassCastException） | ✅ `LiveRedisWriteFaceGateTest` **3/3 仍绿** |
| **B** · 拆每命令检查（`GoStyleRedisDeadlineConnection` 的 `assertNotExhausted(method.getName())` 换注释） | 行为面转红 | ✅ **精确转红**：`GoStyleRedisDeadlineLiveRedisWriteFaceGateTest.exhaustedBudgetBlocksCommandOnAlreadyEstablishedConnection`（**1 红 / 3**） | ✅ 静态门 **4/4** 与真 Lettuce 门 **5/5** 仍绿 |

⇒ ⇒ **结论（最有价值的一条）**：**形态面判据与行为面判据互补、不能互相替代** —— 拆 BPP ⇒ **只**装配面红（行为面仍绿）· 拆每命令检查 ⇒ **只**行为面红（形态面仍绿）
⇒ ⇒ ⇒ **只测一面对 ⇒ 另一面可无限退化而不被发现** ⇒ ⇒ **两面都必须有**。
⇒ ⇒ **还原** = 逐字节（sha 校验通过 ＋ `git status service/common` **零输出**）⇒ ⇒ 主干净净。
⚠️ **两条纪律**：① 变异测试**必须带 `-am`**（`mvn -pl service/common` 不带 `-am` ⇒ 用旧 common jar ⇒ **测的不是当前代码**）② **绝不与别的 lane 的 mvn 并发**。

### ④ 「`spring.grpc.client.*.deadline` 死配置需转告各域 yml 面」⇒ ⇒ **核销 = 零处需转告**

- **形态**：devh `grpc-client-spring-boot-starter 3.1.0.RELEASE` 的客户端属性**只有** `net.devh.boot.grpc.client.channels.*` ⇒ ⇒ **没有 per-client deadline 这个旋钮** ⇒ ⇒ yml 的 `deadline:` **不生效**
  （AGENTS.md §3.3.1 的**反向**形态：「**配置里写了**」也不等于「生效」）。
- **本层全量实测**（代码态 = 剥注释 ＋ 剥字符串字面量 · 扫全仓 `service/**/src/main/resources` 下 **20 个源 yml/yaml**）= `deadline` 键（排除 `outbound-deadline` / `cap-millis`）**全仓共 2 处**，
  **全在 `service/article/article-rpc/src/main/resources/application.yml`**（`:277` `security-rpc.deadline: 10s` · `:293` `message-rpc.deadline: 2s`）
  ⇒ ⇒ **两处都已就地标注「本键不生效」**（该 yml `:265-270` 有整段实测说明）⇒ ⇒ **其余 18 个组件零死配置** ⇒ ⇒ ⇒ **无需转告**。
- **保留登记**：那 2 行死配置本身仍在 yml 里 ⇒ ⇒ 清理属**域面写权**（G3）且**不是缺陷**（注释已写清「对应 Go 真值、但本键不生效」）⇒ ⇒ **本面不代改** ⇒ ⇒ 若 G3 清理须**同一次提交**更新那段注释。

### ⑤ 🔴 本轮又踩一次「自己跑出来的输出也是转述」—— **扫描器的过滤条件把自己的匹配项吃掉了**

- **现象**：第一次扫「哪些组件配了 `cap-millis`」⇒ 用了 `'cap-millis' not in l` 当「排除非目标键」的条件 ⇒ ⇒ **恰好把要统计的那一行也滤掉了**
  ⇒ ⇒ 读数变成「`article-rpc` / `comment-rpc` / `favorite-rpc` 有 `outbound-deadline:` 段却**没有** `cap-millis`」⇒ ⇒ **结论完全反向**（真值 = **15 个已配 / 4 个未配**）。
- **为什么没当场发现**：那个反过来的读数**看起来很合理**（与台账里「api 侧若干个未配」的旧数也能对上）⇒ ⇒ **不是靠肉眼发现的，是靠「换一条独立路径重扫」发现的**。
- **修法**：正向扫（逐行找 `cap-millis`）与反向扫（找 `deadline` 并排除 `outbound-deadline` / `cap-millis`）**各写一套过滤条件、绝不共用**
  ⇒ ⇒ 两次读数**互证**（正向 = 15 · 反向 = 4 未配 + 2 处死配置，合计自洽）。
- ⇒ ⇒ **教训**：**同一套过滤条件不能同时服务「找目标」与「找非目标」** ⇒ ⇒ 正反两向读数**必须各走一条独立路径**。

## 十、M-25：G1 跨会话回执的核销（**第七次**「回执说待合、实际早已合入」）＋ 三处共享面残留的**两落一不动** ＋ **两条「lane 的预期与实测不符」的订正（2026-10-31 · 合并收口执行者）

### ① G1 报的两笔 ⇒ **两笔都已在主干** ⇒ 零待合

| 它报的 | 本层实测（`git merge-base --is-ancestor` 逐笔核） | 结论 |
|---|---|---|
| 「本轮新增 2 笔：`a28cc7b7` ＋ `12fbad33`」 | **两笔都已在主干** —— `12fbad33` 由 **`2ab06d95`** 带入 · `a28cc7b7` 由 **`12fbad33`** 带入 | ⇒ ⇒ **零待合**；`align/g1-users-follow` 相对主干的**真实差集 = 34/0 ⇒ 独有 0** ⇒ ⇒ ⚠️ **这是同一模式第七次**（G1 / G2 / G3 / G4 各报过「待合」而实际早已合入）⇒ ⇒ **「分支上有的 ≠ 本域产出的」+ 「未提交产出不在 ancestry 判据内」** |
| 「① `S1-FOLLOW-007` 销号已落码」 | **属实** —— 派生层该行现为 `✅ 已一致`（`12fbad33` 已在主干）⇒ ⇒ 本面不重复登记 | ✅ |

⇒ ⇒ **纪律固化**：凡 lane 报「新增 N 笔」⇒ ⇒ **一律用 `git merge-base --is-ancestor` 逐笔核**；凡**已在主干**的 ⇒ ⇒ **零待合回执**；**分支相对主干的差集**才算「待合」的真口径。

### ② 三处共享面残留 ⇒ ⇒ **两落一不动**（本面独立复核后落码）

| # | 落点 | 本层复核结论 | 处置 |
|---|---|---|---|
| a | `docs/PhaseGoal-judge.md:192` 的「残留」列字面原值 `DRIFT-FOLLOW-008` | **已闭合** —— 该行已带「**不计入本行的开放残余差异**」措辞（2026-10-30 本面所加）⇒ ⇒ 派工判据 `tools/difftest/check-phasegoal-lanes.py` 的 `route_open()` **正认这个措辞** ⇒ ⇒ 派工面 `docs/PhaseGoal/g1.md` 的 `follow-api` 行「开放项归属」列实测 = `**→ 非开放（已签字 · 不是缺陷）**` ⇒ ⇒ **危害确已消解** | 🔴 **不动** —— ⚠️ **改掉它反而会让判据失配**（`route_open()` 认的就是这句）⇒ ⇒ 原值**刻意保留**，并在 BLK-03 的来源格里写明「不要改它」 |
| b | `docs/RoundPlan/_unresolved.md:28`（`BLK-03` 槽 · 仍写「🔴 维持仍开着」） | **真陈旧** —— 事实层已闭合（`S1-FOLLOW-007` = ✅ 已一致）⇒ ⇒ 且**危害已消解**（同上，派工面已判非开放） | ✅ **已落**（三格同行替换 ⇒ 零位移）—— 当前状态列加「✅ 已关闭」＋ 关闭依据，**原状态逐字留存于格末**（原值保留，不删改） |
| c | `docs/DRIFT/_cross/grading/_samesrc-W18.md:5`（转述 users 档 `:524`） | **真陈旧** —— users 档 `:524` 已改为「✅ 本轮销号 · 危害已消解 ⇒ 不再阻断阶段 1」⇒ ⇒ 原转述失效 | ✅ **已落**（同行替换 ⇒ 零位移） |

### ③ 🔴 两条「lane 的预期与实测不符」—— 本面**先自己复算再答复**（不照抄它的预期）

| G1 的预期 | 本面**实测** | 差在哪 |
|---|---|---|
| ③b「订正 BLK-03 槽 ⇒ ⇒ **派生层 §2.2「未解决项」表才会去掉 `S1-FOLLOW-007`**」 | ❌ **不成立** —— §2.2 的渲染是 `for uid in sorted(unresolved)` ⇒ ⇒ **遍历全部未解决项、不按 `closed` 过滤** ⇒ ⇒ `BLK-03` **仍出现在 §2.2 表里**（`stage1-derivation.md:134`）⇒ ⇒ 真正变的是**指针时效性**（指向已关闭项 ⇒ 不再算排期） | **「已关闭」只影响 `unres_of` → `unres_closed_of` 的分流**，**不影响 §2.2 的清单成员** ⇒ ⇒ **「关掉一项 ⇒ 它从表里消失」是错的** |
| ③b「阶段一阻断项数才会真正减 1」 | ❌ **不成立** —— `blocking` 取自 **U5-O 显式定级叠加层**（`derive-stage1.py` 的 `ov["blocking"]`）⇒ ⇒ **不随 `gap` 自动翻转** ⇒ ⇒ `S1-FOLLOW-007` 仍是 `stage=A` / `blocking=是` | **事实层（gap）与定级层（blocking）是两路** ⇒ ⇒ ⇒ 与 **C-3** 同一处置：**只关事实、不擅自翻定级**（定级 = Jason 拍板面） |

⇒ ⇒ **本轮实际效果**：① `S1-FOLLOW-007` 的 `gap` 脱钩 `BLK-03` ⇒ `schedule` = 「已定性 · 未排期」；② 另 5 行原挂 `BLK-03` 的同步脱钩；
③ ⚠️ **开放清单 +1 行 / +1 编号 = `A-2`@users（🟡 部分闭合）** ⇒ ⇒ **不是新增缺口**，而是它原先被**已关闭**的指针**伪排期**掉了 ⇒ ⇒ 闭合后正确回到未排期
⇒ ⇒ 与 `derive-stage1.py:1846` 那条纪律同族（「指向已关闭项 ⇒ 不算排期 ⇒ ⇒ **真缺口未排期 = 0 是假的**」）⇒ ⇒ **这一 +1 是修正，不是回归**。

### ④ G1 报的「④ ⑤″ 在 off-branch 最新 commit 是 merge commit 时误判红」⇒ ⇒ **旧读数**（已修）

- **本面实测复现**（用**当前主干**脚本 + 独立克隆：`do-my-own-gh` → `c750a2f7`（merge commit）· `HEAD` → 其父 `56b9966a` ⇒ off-branch 样本 = merge commit）
  ⇒ ⇒ `tools/check-ledger-selfcheck.py` 的层 0 阳性对照 ⑤″ 读数 = **「✅ 层 3 的链判据能处理**不在当前分支上**的 commit（不依赖分支口径）」** ⇒ ⇒ **0 个 ❌ · EXIT=0** ⇒ ⇒ **不红**。
- **成因**：⑤″ 已在 `2c7f047c` 重构到 **v3**（把与样本形态耦合的 `len(_sp) == 2` 换成与形态无关的 `_x[:7] in _sp`）⇒ ⇒ G1 观察到的是 **pre-v3** 的行为（它的分支落后主干 34 笔 ⇒ 跑的是旧脚本）⇒ ⇒ 它自述「推基线到 `HEAD == do-my-own-gh` 后 `_off` 为空 ⇒ 全绿」**正是旧脚本的特征**（v3 下不需要推基线）。
- ⇒ ⇒ **「某 lane 报的工具缺陷」必须先确认它跑的是哪一版脚本** ⇒ ⇒ 否则会把**已修的问题**当成新缺陷再修一遍。

### ⑤ 本轮零行为改动 ⇒ ⇒ 核验

派生层 `--check` **PASS** · 指针棘轮 **237→237 · 新增 0** · 四个文件**行数全不变**（`_unresolved` 162 · `_samesrc-W18` 26 · `derivation.md` 1877 · `derivation.tsv` 1285）⇒ ⇒ **零位移** · U+FFFD = **0** · 反斜杠计数与 HEAD 相等。

## 十一、M-26：G2 回执的核销（**第八次**「说待合、实则早已合入」）＋ 🔴🔴 **拆掉一颗活雷：G4 工作树停摆在一笔未提交的 merge 上**（2026-10-31 · 合并收口执行者）

### ① G2 报「我的 4 笔请落主干」⇒ ⇒ **四笔都已在主干** ⇒ 零待合

| 它报的 | 本层实测 | 结论 |
|---|---|---|
| 「4 笔请落主干：`4b7b3a9c` / `6d4bbad9` / `720f1c37` / `3e73398f`」 | `git merge-base --is-ancestor` **逐笔核 = 4/4 已在主干** | ⇒ ⇒ **零待合** ⇒ ⚠️ **这是同一模式第八次**（G1 / G2 / G3 / G4 各报过）⇒ ⇒ `align/g2-message-like-task` 相对主干的**真实差集 = 47/0 ⇒ 分支独有 0** |
| 「我落后主干 1 笔（主干已到 `a6482356`）」 | 主干现为 **`ffd7c8d6`** ⇒ ⇒ 它的「落后 1 笔」是**旧基线**下的读数 ⇒ ⇒ 实测落后 **47** 笔 | ⇒ ⇒ **它那份回执整段基于旧主干** |
| 「G4 现在正在 merge 主干（工作树 `UU` 冲突态）」 | ✅ **这一条成立**（见第二节）⇒ ⇒ 但 G4 已**停摆约 3 小时无写入**（**最后一次真实写入 = 16:35** · 最后一次 commit = 16:22 · reflog）⇒ ⇒ 冲突虽解、**merge 未提交** | ⇒ ⇒ **见第二节：这是一颗活雷** |

### ② 它报的「合并后主线会红 2 道门」⇒ ⇒ **在主干上已不成立**（且它给的那个修法**会造成红门**）

| 它报的 | 本层实测（主干 `ffd7c8d6`） | 结论 |
|---|---|---|
| 「A-1 的 `tests` 列缺 G3 那 14 个方法 ⇒ `comment-api` 62→**66** · `favorite-api` 155→**158** · `hot-api` 109→**116**」 | **三行现值已经是 66 / 158 / 116**（`judge:125` / `:129` / `:137`）⇒ ⇒ `check-source-test-decl-count` 逐行读数 `comment-api 列=66 源=66 ✅` · `favorite-api 列=158 源=158 ✅` ⇒ ⇒ **已回填** | ✅ **已闭合**（G3 已回填） |
| 「`check-phasegoal-consistency` 会红（A-1 求和 3300 vs 实测 3314）」 | **EXIT=0 ✅** | ✅ **已闭合** |
| 「`check-wave-coupling` 会多 1 项（`GoStyleProtectionConfiguration` 辐射多出 `message`）」 | **EXIT=0 ✅** —— 该辐射差异正是 §4.19.48 的**口径升级**（生产面 ＋ 代码态 FQN）**正确计入** `message` 后**已刷新快照**的结果 | ✅ **已闭合** |
| 「合并收口时把 M2 总数改成 **3,314**」 | 🔴 **不能改** —— 主干 M2 总数现值 = **3,307**（**代码态口径**）⇒ ⇒ **3,314 是「代码态订正前」的旧值** ⇒ ⇒ 照它改 ⇒ ⇒ `check-source-test-decl-count` **立刻红**（列求和 3,314 ≠ 源声明求和 3,307） | ⚠️ **照旧口径改 = 亲手制造红门** ⇒ ⇒ **回执里的数字必须带口径** |

### ③ 🔴🔴 拆雷：G4 工作树停摆在一笔**未提交**的 merge 上 ⇒ ⇒ 提交即把主干回退 **27k 行**

- **现场**（本面实测）：G4 工作树 `git status` = **「All conflicts fixed but you are still merging」** ⇒ ⇒ 冲突已解、**merge 未提交** ⇒ ⇒ 且 **最后一次真实写入 = 16:35**（reflog：最后一次 commit = 16:22）⇒ ⇒ **停摆约 3 小时**。
- **MERGE_HEAD = `3e73398f`**（= G2 的**旧分支尖**）⇒ ⇒ ⚠️ **这个 commit 早已在主干**（`git merge-base --is-ancestor` = ✅）⇒ ⇒ ⇒ **merge 它 = 把分支往回拉**，而不是「同步主干」。
- **后果（实测）**：该 merge 的暂存树相对**当前主干** = **124 文件 / +348 / −26,995 行** ⇒ ⇒ 被删的包括主干新增物
  `tools/difftest/docker_api.py` · `tools/difftest/schema-baseline-deviations.json` · `tools/difftest/extract-schema-baseline.py`
  · `tools/difftest/check-proto-static.sh` · `check-source-test-decl-count.py` · `check-phasegoal-consistency.py` · `check-wave-coupling.py` · `derive-stage1.py` …
  ⇒ ⇒ 且 A-1 三行**回退**（comment-api **66→62** · favorite-api **158→155** · follow-api **99→101**）＋ M2 总数 **3,307→3,300**
  ⇒ ⇒ ⇒ **一旦提交 = 两道门立刻红 + 主干丢掉 27k 行**。
- **处置 = `git merge --abort`**（已执行）⇒ ⇒ G4 回到 `7d419ca0` · `MERGE_HEAD` 已清除 · 工作树干净 · **主干零影响**（`ffd7c8d6` 未动）。
- ⚠️ **代价要说清**：abort **丢弃了那 15 项未提交内容**（它们从未进 git ⇒ 不可恢复）⇒ ⇒ **但按 §4.19.42 的裁定它们本就不该采纳**
  （judge 的旧值 **3,300** + 主干已有的 `like-api`/`task-api` **重复副本**）⇒ ⇒ 唯一该采纳的「**例外集剪到 1 条**」早已由本面代落（`50f2d51f`）⇒ ⇒ **净损失 = 0**。
- **正确的同步方式** = `git merge do-my-own-gh`（**当前主干**）⇒ ⇒ ⚠️ **不是** merge 一个「已在主干的祖先」。

### ④ 三条可复用的教训

1. **「merge 一个已在主干的祖先」= 把分支往回拉** ⇒ ⇒ 合并前必问：**「MERGE_HEAD 在主干上吗」** ⇒ ⇒ 在 ⇒ ⇒ 那不是同步，是回退。
2. **停摆的 merge 是活雷** ⇒ ⇒ 判「某 lane 是否停摆」**不能只看「有没有新提交」** ⇒ ⇒ 还要看**工作树是不是卡在 merge 中**（`git status` 的 "still merging"）⇒ ⇒ 且「冲突已解 ≠ 已提交」。
3. **回执里的数字必须带口径** ⇒ ⇒ G2 的 **3,314** = **代码态订正前**的旧值 ⇒ ⇒ 主干真值 **3,307** ⇒ ⇒ ⇒ **照旧口径改 = 亲手制造红门** ⇒ ⇒ 与 §4.19.42 那条纪律同源：**凡要往文档写一个「总数」，必须取守门自己打印的实测值**。

## 十二、M-27：G3 回执的核销（**第九次**同模式）＋ 它报的「主干红 2 处」逐条实测**都不成立**（2026-10-31 · 合并收口执行者）

| 它报的 | 本面实测（主干 `75de9942`） | 结论 |
|---|---|---|
| 「我最后两笔 `83fe7752` ＋ `6600b4d0`，随 `713896ff` 进主干」 | `git merge-base --is-ancestor` **逐笔核 = 3/3 已在主干** ⇒ ⇒ `align/g3-article-comment-favorite-hot` 相对主干的**真实差集 = 9/0 ⇒ 分支独有 0** | ⇒ ⇒ **零待合** ⇒ ⚠️ **这是同一模式第九次**（G1 / G2 / G3 / G4 各报过） |
| 「🔴 主干当前红 ①：`check-source-test-decl-count` 的 M2 —— 声明 3,300 vs 实测 3,314」 | **EXIT=0 ✅**（M1 / M2 全绿）⇒ ⇒ 主干 M2 总数现值 = **3,307**（**代码态口径**）⇒ ⇒ 它那个「3,300 / 3,314」是**两个不同时代的旧值** | ✅ **不成立** ⇒ ⇒ ⚠️ **它建议 G2 改成 `3,314` 那一步已危险** ⇒ ⇒ 本面已在回执 4 里明确警告 G2「别改」 |
| 「🔴 主干当前红 ②：共享层 `GoStyleClientCallTimeoutSingleSourceGateTest` 的例外失效检测 2 条（like-api 1 · task-api 1）」 | **例外集现 = 1 条** = `article-api` 的 `UserRpcClient`（**不可收敛 ⇒ 不许剪**）⇒ ⇒ **不含 like-api / task-api** ⇒ ⇒ 那两条早在本面 `50f2d51f` 就被剪掉了 | ✅ **不成立** ⇒ ⇒ 且 `service/common` 自上次**全量 480 / 0F / 0E / 0S** 以来**零变更**（`git log 99186457..do-my-own-gh -- service/common` = 空）⇒ ⇒ 那次全量读数**仍然成立** |
| 「M1 已绿（A-1 列求和 3314 == 源声明求和 3314）」 | ✅ **属实**（它自己那笔 `83fe7752` 干的）⇒ ⇒ 主干现值是 **3,307**（不是 3,314） | ✅ 销账，**但数字要换成 3,307** |
| 「派生层基线 1284 → 我的树 1284 · 阻断项 11 → 11 不变」 | ✅ 与主干现值一致（**1284 数据行**） | ✅ |
| 「三棵树已全部落主干」 | ✅ **属实** —— 本轮五棵分支的「分支独有」**全部 = 0** | ✅ |

⇒ ⇒ **它那两条纪律提醒本面早已在册**：① **共享层变异必须带 `-am`**（否则链 `~/.m2` 的旧快照 jar ⇒ 变异进不了被测链路 ⇒ **读数全绿是假信号**）；② **判据要从「放宽方向」取证**（收紧方向下写死副本会把 min 的结果压回旧值 ⇒ 判据**绿**而副本仍在）⇒ ⇒ 本面共享层 A/B 双变异已按这两条执行。

⇒ ⇒ ⚠️ **本轮三次回执（G1 / G2 / G3）都是「基于旧主干的快照」** ⇒ ⇒ **凡 lane 报「主干红 X 处」，先自己跑一遍那两道门再答复** —— 三次里有三次是**旧读数**，其中一次（②）若被照做会**亲手制造红门**。

## 十三、M-28：G2 **第二条**回执的核销（**第十次**同模式）＋ 它要落的 2 处改动**一处会红门、一处无改动可做** ＋ **R5 那 8 条 pending 差异的逐条核对 ⇒ 三类归属**（2026-10-31 · 合并收口执行者）

### ① 它的 5 笔 ⇒ 都已在主干 ⇒ 零待合

`4b7b3a9c` / `6d4bbad9` / `720f1c37` / `3e73398f` / **`e7e51e60`** ⇒ `git merge-base --is-ancestor` **逐笔核 = 5/5 已在主干**
⇒ ⇒ `align/g2-message-like-task` 真实差集 = **51/0 ⇒ 分支独有 0** · 工作树干净 ⇒ ⇒ **无任何待合产出**
⇒ ⇒ ⚠️ **「回执说待合、实际早已合入」同一模式第十次**。它报的「G3 的 A-1 回填 `713896ff` ＋ G4 的例外集批剪 `55bdd673` ＋ G1 第三波 `fd6c9830` 也都已落」⇒ ⇒ **全部属实** ✔

### ② 它报的「合并后只剩 2 项红」⇒ 在主干上**两项都不红**

| 它报的 | 主干实测 |
|---|---|
| 「`check-source-test-decl-count` 的 M2：声明 3,300 vs 实测 3,314」 | **EXIT=0 ✅** ⇒ ⇒ **主干 M2 真值 = 3,307**（代码态口径）⇒ ⇒ 3,314 = **代码态订正前**的值 |
| 「`check-wave-coupling` 差一个 `hot`」 | **EXIT=0 ✅** ⇒ ⇒ 辐射度判据已升到**生产面 ＋ 代码态 FQN**（`eba740e0` ＋ `vs-common.md` §4.19.48）⇒ ⇒ `hot` 已被**正确计入并刷新快照**⇒ ⇒ **既不是漂移，也不是「快照没跟上」** |

### ③ 🔴 它要落的第一处「M2 改成 `**3,314**`」⇒ **会红门** ⇒ ⇒ **已拦下**

3,314 是**代码态订正前**的数 ⇒ ⇒ 改成 3,314 ⇒ ⇒ 列求和 3,314 ≠ 源声明求和 **3,307** ⇒ ⇒ `check-source-test-decl-count` **立刻红**
⇒ ⇒ ⇒ **「3,314 已是终值」这个判断不成立** ⇒ ⇒ 它 3 份台账里那条「M2 归属订正」**别按 3,314 写** ⇒ ⇒ 要写就写 **3,307**，或写成「**真值以守门实测为准**」（避免下轮再被当常量搬）。

### ④ 它要落的第二处「两个 message 守门类 `import` → 全限定名」⇒ **无改动可做**

实测 `MessageRpcOutboundDeadlineCapGateTest.java:427` / `:436` 与 `MessageApiOutboundDeadlineCapGateTest.java:383` **已经是全限定名**
⇒ ⇒ 且**现在也不需要做** —— 辐射度判据**只扫生产面**（`service/**/src/main/java`）⇒ ⇒ **测试面根本不参与读数**
⇒ ⇒ G3 已独立得出同一结论并**放弃了路 A 的代码改动** ⇒ ⇒ ⇒ **「用改写法绕开判据盲区」这条路已不需要**（盲区已根治）。

### ⑤ 它报的「判据形态盲区（只认 import ⇒ 全限定名看不见）」⇒ **已修**

现行实现 = **生产面**（`service/**/src/main/java`）＋ **`code_only()` 剥注释与字面量**＋ **代码态 FQN 判定**
⇒ ⇒ 且**变异实测过**：删掉 `message-rpc` 的全限定名依赖 ⇒ **新口径红 / 旧口径绿** ⇒ ⇒ **假绿已消除** ⇒ ⇒ **不用再改工具**。
⇒ ⇒ 它要的「用 `--print` 重算快照」⇒ ⇒ **已完成**（§4.19.48 第四节：快照块 492 → 373 行 · 分桶表整块重建 · 锚点表刷新）。

### ⑥ 🆕 R5 那 **8 条 pending 差异的逐条核对** ⇒ ⇒ **三类归属**（ci 第 9 笔 `4ae4b96b` · 已合入）

⚠️ 该补记**不改任何一条差异的状态**（仍 `pending`）⇒ ⇒ **裁定权仍在用户**。核对方法 = **两侧同时读消费点**（不是拿 RAG 现状当基准）。

| 类别 | 条目 | 核对结论 | 处置建议 |
|---|---|---|---|
| 🔴 **对外可见的真实差异** | like 域 **1 / 2 / 3 / 5** | Go 侧业务码 = **原始 gRPC 码**（13/3/5/16）· Java 侧 = **域内代表码**（1015/2001/1015/1009）· **文案两侧相同** ⇒ ⇒ **客户端看到的 `code` 字段变了** | ⚠️ **不是「无害的技术差异」** ⇒ ⇒ 签字 = 接受一个**客户端可见**的行为差异 ⇒ ⇒ **属真决策**（本面不代签） |
| 🟡 **疑似「比较点错配」** | like 域 **4**（`foreign_plain`） | 探针比的是 Java 的**库方法** `LikeGrpcError#bizCodeFromError`（非 status ⇒ 末尾兜底 1015），Go 侧比的是**消费点**；而 Java 的**实际消费点** `LikeApiSupport#rpcError` **先判 `status == null`** ⇒ 对外返回 **5001** = **与 Go 一致** | ⇒ ⇒ **可能该修探针，而不是签字** |
| 🟡 **平台限制** | comment 域 **6 / 7 / 8** | **只差 `grpc-status` 字段的数值**；业务码与文案两侧无损（Java 从 trailer `rag-comment-biz-code` 取回）⇒ ⇒ **不可复原项 = 平台限制**（grpc-java `Status.Code` 只有 17 个成员）⇒ ⇒ 与 like 域已签字的 `SD-2026-10-23-06@like` **同一族**，差别只在 **comment 域本域未签** | 🏷 **建议签**（与已签字项同族） |

⇒ ⇒ ⇒ **修正本面上一轮的说法**：上一轮本面报「8 条已登记 ⇒ 🏷 候选，建议签」**太粗** ⇒ ⇒ **逐条核对后只有 comment 域 3 条是同族可签**；
⇒ ⇒ **like 域 4 条是真实可见差异**（不该顺手签）· **like 域第 4 条疑似探针错配**（该查探针）⇒ ⇒ ⇒ **这三条是三类不同性质的事，不能打包成一次「签 8 条」**。

### ⑦ 🆕 顺带核出一条**新事实**（like 域写权 ⇒ 已转派 G2）

`service/like/like-api/src/main/java/com/rag/like/like/api/client/LikeRpcClient.java:70-77` 的 **javadoc 与当前实现不符**：
写「熔断打开 ⇒ 落进 `bizCodeFromError` 兜底 ⇒ 业务码 **1015**；Go 侧此时是 5001」⇒ ⇒ **实测**：`LikeApiSupport#rpcError` 在调用 `bizCodeFromError` **之前**就判了 `status == null`
⇒ ⇒ 熔断打开（`GoStyleCircuitOpenException` 非 status）**对外返回 5001** ⇒ ⇒ **与 Go 一致**（不是 1015）。
实测依据：grpc-java 1.63.0 `io/grpc/ClientInterceptors.java:153-157`（`interceptCall` 在 `newCall()` 时被调）＋ `io/grpc/stub/ClientCalls.java:148-155`（`channel.newCall(...)` 在 `try` **之外**）⇒ ⇒ 熔断异常**原样**抛出、不会被包装成 `StatusRuntimeException`。
⇒ ⇒ 处置 = **订正该 javadoc**（零行位移）⇒ ⇒ **不影响那 8 条的任何一条状态**。

### ⑧ 本轮合并台账

`60c3b709` merge(ci,g3)：ci 第 9 笔（8 条 pending 逐条核对 ＋ like javadoc 新事实）＋ G3 第十七轮（comment 域 §3.5 D8 判定行订正 · **整条仍 🔴** · 销账权在 global）
`55158320` merge(ci)：ci 第 10 笔（Testcontainers 测试文件数 **128 → 126** ＋ 写死**可复算口径** ⇒ ⇒ 与「数字必须带口径」同族）

⇒ ⇒ ⚠️ **本轮四次回执（G1 / G2 ×2 / G3）全部是「基于旧主干的快照」** ⇒ ⇒ **凡 lane 报「主干红 X 处」，先自己跑一遍那两道门再答复** —— 四次里四次是旧读数，其中**两次若被照做会亲手制造红门**（G2 的 M2 → 3,314；G2 的 M2 → 3,300）。

### M-29 · 🆕 本轮收口（2026-10-31 19:49 ~ 21:xx · 合并收口执行者）

#### ① 🔴 **「用户可以绕过调度会话直接给某个 lane 下指令」** ⇒ ⇒ **调度员的「待拍板清单」不是权威来源**

- **事实**：2026-10-31 **19:49:22**，Jason **直接给 CI lane**（会话 `mvs_f6a61ff2e19a40d8a2da6d4ec0afa835`）下了一句裁定：
  「**comment 3 条 + like 4 条签、foreign_plain 降级为closed**」⇒ ⇒ **本面（调度会话）当时仍在等「这 8 条待拍板」**。
- **⇒ 判据固化**：① 凡清单里有「待用户拍板」项 ⇒ **答复前先扫全量会话的 user-role 消息**（不是只看自己会话、也不是只看对方 lane 的回执）
  ⇒ 拿到**用户原话**才算数；② 凡要在文档写「用户签字 / 用户裁定」⇒ 必须能指出**原话出处**（会话 id ＋ 时间戳 ＋ 原话引用）
  ⇒ 指不出 ⇒ 那不是签字，是推断；③ 判「有没有人下过令」⇒ **扫 user-role 消息 ＋ 扫工作树 mtime**（产物是否已被改动）⇒ 两个信号都要。
- **取证手法（可复用）**：会话原文落盘在 `~/.minimax/v2/sessions/YYYY/MM/DD/<dir>/messages.jsonl`
  ⇒ 逐行 `json.loads` ⇒ 取 `message.role == 'user'` ⇒ 文本在 `message.content[].text`
  ⇒ ⚠️ **用户真实输入常跟在 `</system-reminder>` 之后** ⇒ 直接取 `content` 会把整段系统提示当输入 ⇒ 要按 `</system-reminder>` 切分取尾部
  ⇒ ⚠️ `mavis session messages` 返回体含全部工具调用结果 ⇒ 极易被截断 ⇒ **扫盘比翻会话工具可靠**。
- ⚠️ **台账里的「用户签字」是待验对象，不是证据**（同族：「自己跑出来的守门输出也是转述」）。

#### ② ✅ **R5 那 8 条 pending 的签字落地**（ci 第 11 个合并点 `ee231805` ＋ `00c8a343` ＋ `341c1d99`）

- **合入内容**：2 条新签字条目（`SD-2026-10-31-01`@like · `SD-2026-10-31-02`@comment）＋ 机器基准（`ci/contracts/errmsg/{like,comment}.txt` 的 pending 5+3 ⇒ 0）
  ＋ 目标清单（`REQ-ERRMSG-LIKE-001` / `REQ-ERRMSG-COMMENT-001` ⇒ `APPROVED_DIFFERENCE`）
  ＋ 两份 mutation 脚本的「变异落空」守卫与自建靶子 ＋ 4 个 CI 文档。
- **合并收口执行者独立复核**：R5 门 **PASS 9 / FAIL 0 / BLOCKED 0 / PENDING 0 · exit 0**（分列 38/3/13/0 ⇒ 合计 54 守恒）
  · `check-requirements.py` exit 0（PASS 23 · FAIL 0 · 缺口 18）· 指针 `comment/common.md:11` 与 `like/rpc.md:127` 精确落在新条目标题行
  · ⚠️ 首次跑出现 1 次 `BLOCKED(article)` ⇒ 实测为**容器启动竞态**的瞬时值 ⇒ 重跑即 PASS 9 ⇒ 记为环境性、**不记为缺陷**。
- **共享索引回填（SHARED 面 = 收口执行者写权）**：`docs/Intentional_Deviations/_INDEX.md`
  用 `ledger-deviation-count.py --fix` 回填「已签字」一行 ＋ 同步族别表 / 合计行 / 条目总数 / 两处「现值」引用
  ⇒ 已签字 **60 → 62** · 未签字 12 → 13（既存漏抄 1 条）· 条目总数 93 → 96 ⇒ **全为单行替换（10 增 10 删 · 259 行不变 ⇒ 零位移）**
  ⇒ 族别表自洽复核：52+4+2+4=62 · 3+0+0+10=13 · 8+2+0+1=11 · 70+6+5+15=96。
- ⚠️ **本面的一处误判（2026-10-31 20:2x 已订正 · CI lane 的 B12 指出）**：本面曾据 `mavis session get` 的 status 字段
  （`errorCode 42212` · `updatedAt` 19:49:22）判定「CI lane 因配额上限停摆 ⇒ 8 个文件停在未提交状态 ⇒ 由收口执行者代落」
  ⇒ ⇒ **该判定不成立**：会话原始记录显示该 lane 在 19:49:22 之后**持续有 assistant 活动**（连续到 20:03:15，收到回执后续作到 20:19），
  工作树写入持续到 20:00:47，且**它自己也提交了** `4140731f` ⇒ ⇒ **内容零损失**（`615cf933` 的 8 个文件全是它自己的产出）
  ⇒ ⇒ 但 **commit message `615cf933` 里那句「停摆 ⇒ 代落」是假断言** ⇒ 按「台账订正、不改写共享分支历史」处置（与 CI lane 的 `ci/DELIVERY.md` §4.5 B12 同一处置）。
  ⇒ ⇒ **教训**：**会话 status 字段可能是瞬时快照（配额提示），不代表 agent 停摆** ⇒ ⇒ 判「对方是否还在动」必须看**会话原始记录里的 assistant 活动轨迹 ＋ 工作树 mtime**，二者都要（§5.3e 同款纪律）。
- ⚠️ **派生层是生成物 ⇒ 源台账一变就全量重算**（不做增量）⇒ 重算后 `--check` PASS；
  本次 tsv **0 行变化**、md **1 行变化**（三目录条目 2664 → 2667 · 唯一编号 1406 → 1408）⇒ 增量来源逐项核对无误。

#### ③ 🔴 **「签字落账 ⇒ 派生层的『已签字偏离优先于 🔴 未闭合』规则没触发」** ⇒ ⇒ **规则只在同一编号下生效**

- **现象**：`SD-2026-10-31-01`（IntDev，id = `SD-2026-10-31-01`）已在正文回指 DRIFT 的 **`D-7`**
  ⇒ 但派生层里 `D-7` 仍被读成 **🔴 未闭合** ⇒ ⇒ **一条已签字的决策仍进「真缺口」清单** ⇒ 与「签字偏离不是缺陷」冲突。
- **根因**：`derive-stage1.py` 的合并键 = **条目编号** ⇒ DRIFT 的 `D-7` 与 IntDev 的 `SD-2026-10-31-01` **是两个编号** ⇒ 规则不命中。
  ⇒ ⇒ **「同一条事在两处登记」必须让两处用同一个编号，否则「已签字优先」这条护栏形同虚设**。
- **本轮处置**：把 like 台账 `D-7` 的状态位改为 **⚫ 已签字偏离**（沿用本域既有范式 `like/rpc.md:234` 的 `DRIFT-LIKE-002`）
  ⇒ 派生层读成 ⚫ ⇒ **不再计入开放项**（like 域未闭合 **70 → 69**；其余计数全不变）⇒ **只关事实、不擅自翻定级**
  ⇒ ✅ **D-7 的 U5-O 定级层已按 Jason 2026-10-31 裁定改判**（stage=C · blocking=否 · prio=P1 · final_goal=「影响 · 阶段 2 必须恢复」⇒ ⇒ **stage=D · blocking=否 · prio=P3 · final_goal=「不影响 · 经分析确认不实现」**）
  ⇒ ✅ **定级层与事实层现已一致**（两边都指向「⚫ 已签字偏离 ⇒ 无待实现内容」）⇒ ⇒ **原「定级层与事实层不一致 ⇒ 需定级复判 ⇒ 已并入待拍板清单」那一条已销案**⇒ ⇒ 裁定依据 = Jason 2026-10-31 问卷原话（`ask_535efb231602268f7205d515` · 「翻成不阻断且阶段 2 无需恢复」）⇒ ⇒ 定级层落点 = `docs/DRIFT/_cross/grading/W30.md:5`（U5-O 真源）＋ 同步副本 `docs/DRIFT/like/vs-common.md` 的 `D-7` 定级行⇒ ⇒ **E66 定级复判仍维持现状**（见 §5.3 表第 4 项）⇒ ⇒ **不外推**。
- ⚠️ **同批次一并订正的还有一句「熔断打开 ⇒ 业务码 1015」的错误断言**（javadoc ＋ 域台账 ＋ `_samesrc-W20.md` ＋ `_INDEX.md`）
  ⇒ 实测消费点 `LikeApiSupport#rpcError:177-186` **先判 `status == null`** ⇒ 熔断打开对外 **5001** = **与 Go 一致**
  ⇒ 真正的 A4/D-7 差异只有**下游非本域 status 码**（Go 13/3/5/16 ⇒ Java 1015/2001/1015/1009）⇒ 已由 `SD-2026-10-31-01` 签字。

#### ④ ⚠️ **第十一次「lane 报的『主干红』在主干不成立」**

- CI lane 报「`tools/check-ledger-selfcheck.py` 仍 EXIT=1（task 域 Go 侧 import 映射）」⇒ **主干实测 EXIT=0**（层 0~4 全过）
  ⇒ 根因 = 它**自己的分支树落后主干 250 笔** ⇒ ⇒ 与前十次同形态：**凡 lane 报「主干红 X 处」，先自己跑一遍那几道门再答复**。

#### ⑤ ✅ **旧 G4 分支 `align/g4-service-common` 的 22 笔「独有提交」判定**

- `git cherry` = 8 笔 `-` ＋ **14 笔假 `+`**（合并过的 commit 会被标 `+`）⇒ ⇒ **不能按分支差集判「待合」**。
- **逐文件 blob 比对**（22 笔触碰 20 个文件）：**19 个与主干逐字节相同**，唯一不同的 `docs/DRIFT/global/vs-common.md` = **主干是超集**（含本面代 G4 落的 §4.19.49 等追加段）
  ⇒ ⇒ **结论：旧分支无须合入**（内容已在主干）⇒ **分支名会换代，旧分支会冒充「未合的证据」**。

---

## M-31：两项「待 Jason 拍板」由本面**实测定性后销案** ⇒ 待拍板面 7 项 → 4 项（2026-10-31 · 合并收口执行者）

> ⚠️ **本节追加在文件末尾** ⇒ ⇒ **零位移** ⇒ ⇒ 本文件的三处硬指针 `:108` / `:274` / `:587`（全在 1173 行之前）
> **不受影响**；M-23 节「遗留」清单里那一行做的是**单行替换**（1 行 → 1 行）⇒ ⇒ **同样零位移**。
> ⚠️ **本文件里指向本文件行号的指针，全仓实测只有 `:274`（1 处）与 `:587`（2 处）** ⇒ **均在本节之前** ⇒ 不位移。

### ① `§4.19.6` 重判 = **早已做完**（不是待办）

- **事实**：`docs/DRIFT/global/vs-common.md` **§4.19.6 第三节**（「三态」= 配置层口径）
  **已由本面于 2026-10-31 逐域重测并多处订正**：
  **配置面 10/10** · **载体面 8/10**（article / favorite / follow / hot / like / message / users-admin / users-user）
  · **可抓取面 8/10**（= 载体面集合）· **今天无载体的 2 个 = `comment-rpc` / `task-rpc`** ⇒ 逐格见 **§4.19.23**。
- ⇒ ⇒ 原断言「**10 个 rpc 域里只有 `follow-rpc` 有 HTTP 容器**」与「**可被抓取 = 1/10**」
  **已作废**（**不是过期 —— 是判据与数字都错**）⇒ ⇒ **`§4.19.6` 重判 = 已完成** ⇒ ⇒ **不需要 Jason 拍板**。

### ② `entry_stage_decl` 口径 = **影响面 0** ⇒ **不改代码**，只登记口径

- **隐患**：`entry_stage_decl` 取「条目**自己段落**里第一行含『阶段归属 / 定级』且能解析出类」的行
  ⇒ ⇒ 若那行是**口径图例**（形如「定级口径 = …：**A** = 阶段 1 准入阻断 · **B** = … · **C** = …」）
  ⇒ ⇒ 它在**定义各字母的含义**，不是给**本条**定级 ⇒ ⇒ **会被误读成 stage**。
- **候选修法**：取类前跳过「图例行」（判据 = 一行里有 **≥2 个**「`A`/`B`/`C`/`D` = …」定义式）。
- **影响面已量**（**A/B 双跑**：复制两棵工作区各跑原版 / 候选修法 ⇒ **逐字节 diff 两份产物**）
  ⇒ **md 逐字节相同 · tsv 逐字节相同** ⇒ **影响面 = 0**。
  （理由：现存唯一的图例行 `docs/DRIFT/like/vs-common.md:1640` 位于各条目**段落之外** ⇒ 不进任何条目的 `seg`。）
- ⇒ ⇒ 按「**改严前先量影响面**」⇒ ⇒ **不改代码**（零收益、却引入一条新的识别规则 ⇒ 风险 > 收益）。
- ⇒ ⇒ **口径登记**（**不是拍板项**）：**口径图例行不驱动 stage 列**；`entry_stage_decl` 只在**条目自己段落**内生效
  ⇒ ⇒ **留待将来真有条目被图例带偏时再改**（届时再量影响面）。

### ③ 待 Jason 拍板面：**7 项 → 4 项**

| # | 事项 | 状态 |
|---|---|---|
| 1 | **push**（本地 386 笔 · `upstream/do-my-own-gh`） | **待拍板**（此前拍板「等 G1/G2/G3 合批后一起开 PR」⇒ **条件已满足**：G1/G3 本轮已合入 3 个合并点，五棵分支「分支独有」全 0） |
| 2 | **per-client cap 落码** | **待拍板**（机制已定案 = 组件级 `cap-millis` 不变 ＋ 新增「按 client 名覆盖」⇒ 只需覆盖 `article-api` 一个配置点；⚠️ **绝不能配组件级 `cap-millis=2000`**）⚠️ 落点在 `service/common/**` = **G4 写权面** ⇒ **G4 配额 42212 停摆** ⇒ 需 Jason 定「等 G4」还是「授权本层代落」 |
| 3 | ~~**D-7 定级复判**（like 域）~~ | ✅ **已销案**（Jason 2026-10-31 裁定：翻成「不阻断阶段一 ＋ 阶段 2 无需恢复」⇒ ⇒ 定级层已改判为 **stage=D · blocking=否 · prio=P3 · final_goal=「不影响 · 经分析确认不实现」** ⇒ ⇒ 见 `docs/DRIFT/like/vs-common.md` 的 `D-7` 定级行 ＋ `docs/DRIFT/global/vs-common.md` **§4.19.57**） |
| 4 | **E66 定级复判** + **C-3「阻断阶段一 = 是」是否仍成立** | **待拍板**（事实层已闭合，`blocking` 仍是「是」⇒ 本面**不擅自翻**） |

⇒ ⇒ **已销案**：`§4.19.6` 重判（①）· `entry_stage_decl` 口径（②）
⇒ ⇒ **另有一项**（G3 回执 3 报的「域面 yml 死配置」）本面复核后**核销为「已定性 = 保留 ⇒ 不是待办」**
（`docs/DRIFT/article/rpc.md:2919` 明写「保留 + 加注不生效」· 全仓实测确实只有 `article-rpc` 那 2 处）。
