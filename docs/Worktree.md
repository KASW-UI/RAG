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

理由：11 路并发合并必撞，且事后分不清哪段台账是谁写的。
⇒ 各工作树的台账要能自证归属：**每份台账首行标注「由 `align/<域>` 工作树产出 + 基线 commit」**。

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

> ⚠️ IDE 里也能做（Git 工具窗口 ⌘9 → 右键分支 → `Git > 新建工作树…`），
> 但多建时终端循环更快、不易点错。**不要在主目录上点「签出」**——那会带走另一个会话的未提交改动。