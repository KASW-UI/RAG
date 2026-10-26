<!-- @new 2026-10-23 21:4x · worktree 并发工作流（配套改写 AGENTS.md §5.3） -->

# worktree 并发工作流（2026-10-23）

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

建完之后：

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

⚠️ `service/common` 被 **16 个组件**依赖（article/comment/favorite/follow/hot/like/message/task 各自的 api+rpc），
它是请求保护层。它不依赖任何其他域 ⇒ 并发自由度最高，值得先跑。
但反过来：**若 common 挖出新漂移，那 16 个组件的 api 全要复查。**

---

## 三、纪律（硬约束）

### 1. 台账写入边界 —— 不碰共享索引

| 文件 | 谁写 |
|---|---|
| `docs/DRIFT/<域>/<组件>.md` | ✅ 该域工作树自己写 |
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
docs/DRIFT/_cross/                    ← 跨模块档案（跨域不变量：共享 Flyway 账本 / 建表责任交叉 / 事实投递链路）
```

⚠️ `_cross/` 必看的原因：跨组件不变量不属于任何单个组件，**塞进组件任务里就会被漏掉**。

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

### 1b. 🆕 `global` 收口域的**单写权**（2026-10-25 用户定）

> **`global` 不是第 11 个微服务域，是「收口域」**：单微服务域已各自对齐完毕，
> 但**修法落在共享层、或影响多个域**的漂移被搁置 ⇒ 全部归 `global`。

| 规则 | 内容 |
|---|---|
| **单写权** | **`service/common/**` 只有 `align/global` 能写**，别的域只读 ⇒ 别人发现 common 的 bug，**记台账交给 global**，不自己动手 |
| **同一次提交** | 修法同时涉及 `service/common` 与别的域代码（`service/users/**` 等）⇒ **必须同一次提交**。否则会留下「一半改完」的半截状态 |
| **落点** | `docs/DRIFT/global/`（+ `Pitfalls/global/` + `Intentional_Deviations/global/`），按**议题**分档，不是按 api/rpc/proto |
| **与 `common` 域的边界** | `common` 域问「common 自己对不对」；`global` 问「**别人**因 common 而对不对」 |

⚠️ **为什么需要单写权**：`service/common` 被 **16 个组件**依赖（article/comment/favorite/follow/hot/like/message/task 各自的 api+rpc）
⇒ 别的域同时改它 ⇒ 合并必冲突 + 语义打架。
⇒ **这正是「10 域并发对齐吞吐被卡住的那一层」** —— 不是 common 的漂移条目多，
而是**凡修法落在 common 的条目，都自动从并发任务降级为串行任务**。

### 1c. 🆕 `global` 域的**多 lane 并行**（2026-10-25）

> ⚠️ **单写权 ≠ 单会话** —— `global` 的待办横跨 6 个微服务域 + `service/common` + `tools/` + 三档台账，
> 一个会话串行扛不住 ⇒ **一个域可以开多棵 lane 树并行**。
> **完整协议 ⇒ [`docs/DRIFT/global/lanes/_PROTOCOL.md`](DRIFT/global/lanes/_PROTOCOL.md)**（以它为准）。

| 规则 | 内容 |
|---|---|
| **切法** | **按「叶子文件」切**，⛔ 不是按议题切 —— 议题会跨文件（`X-4` 同时碰 like/comment/common），**文件才是不冲突的单位** |
| **基线** | lane **从 `align/global` 的 HEAD 切**（⛔ 不是 `do-my-own-gh`）⇒ 否则看不到该域已完成的轮次，等于重做 |
| **写权** | 每条 lane 只写自己任务书里的**白名单文件** ⇒ **文件级零重叠** ⇒ 永不冲突 |
| **共享文件** | lane ⛔ 不写 `AGENTS.md` / 三个 `_INDEX.md` / `_MANIFEST.json` / 4 个规范台账文件 ⇒ **整合者在合并后统一更新** |
| **lane 台账** | lane 的论证写进 **`docs/DRIFT/global/lanes/<lane>.md`**（自足片段）⇒ 避免 5 个人抢 4 个大文件 |
| **禁区** | **`favorite` 域对 `global` 零写权**（代码 + 配置 + 测试 + 台账，一律零改动） |

📌 **为什么要拆**（实测数据）：`global` 的 4 个规范台账合计 **221,405 B**
（`_README.md` 49,886 + `common-tails.md` 68,534 + `cross-domain.md` 93,286 + `cycle-deps.md` 9,699）
⇒ **单会话每轮都要重读一遍** ⇒ 单轮约 6~7 万 token 的纯台账
⇒ **瓶颈是上下文，不是写码速度** ⇒ 拆 lane 必须配「定向读」（任务书里给精确到段的指针）。

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

---

## 五、拆（干完撤掉）

```bash
git worktree remove ../RAG.worktrees/task
git branch -d align/task
```

`remove` 前必须确认该目录无未提交改动，否则 Git 会拦（这是好事）。

⚠️ 历史上的 4 个 `row/*` 工作树已确认**真合并**（`git merge-base --is-ancestor` 逐个验过），
`RAG.worktrees/` 下的目录可清理，与新建工作树不冲突（名字不撞）。

---

## 六、命令速查

| 目的 | 命令 |
|---|---|
| 看挂了哪几个 | `git worktree list` |
| 建（分支+目录一起） | `git worktree add "<路径>" -b "<分支>" do-my-own-gh` |
| 推 feature 分支 | `git push -u upstream align/<域>` |
| 合回主干 | `git merge --no-ff align/<域>` |
| 同步基线 | `git merge do-my-own-gh`（在工作树内） |
| 撤 | `git worktree remove <路径>` + `git branch -d <分支>` |
| 建 `global` 收口域 | `git worktree add "../RAG.worktrees/global" -b align/global do-my-own-gh` |

> ⚠️ IDE 里也能做（Git 工具窗口 ⌘9 → 右键分支 → `Git > 新建工作树…`），
> 但多建时终端循环更快、不易点错。**不要在主目录上点「签出」**——那会带走另一个会话的未提交改动。
>
> ⚠️ **同名分支已存在时**（如旧的 `align/favorite` 仍被某个工作树占用）：
> `git worktree add` 会报「branch already checked out」⇒ 先 `git worktree remove` + `git branch -D`
> （**确认无独有提交、无未提交改动后再删**）。