# 🔴 Pitfall：`<域>-common` lane 的四个坑（域 ↔ `service/common` 跨层对账）

> **归档时间**：2026-10-27 · **性质**：并发工作流坑（影响全部 10 条 lane）
> **决策记录**：AGENTS.md §5.3d · **总纲**：[`docs/DRIFT/_cross/domain-common-lanes.md`](../../DRIFT/_cross/domain-common-lanes.md)
> **踩坑档记「为什么会踩」**，漂移条目本身在 [`docs/DRIFT/`](../../DRIFT/_INDEX.md)

## 一句话

10 条 lane 并行对账「本域 ↔ `service/common`」这条跨层漂移时，
**最省事的做法（各域自己动手改共享层）恰好是最错的**：
共享层只有一条写权 ⇒ 9 条域 lane 并行改必撞；
而**先把脚手架落在主干、再建 lane** 的顺序颠倒一次，就会让每条 lane 回并时都撞 add/add。

---

## PIT-LANE-01：lane 的基点早于「脚手架落地那次提交」⇒ 回并撞 **add/add**

### 症状长什么样

lane agent 在自己的分支上提交了 `docs/DRIFT/<域>/vs-common.md`（新建），
回并主干时 Git 报：

```
CONFLICT (add/add): Merge conflict in docs/DRIFT/<域>/vs-common.md
```

**两侧都是「新增了同一个路径」** ⇒ 不是内容冲突，是**两边都声称这个文件是自己加的**。

### 真实原因

worktree 建好后，**主干仍在前进**（AGENTS.md 政策本身就在改）⇒
lane 的基点被留在旧 commit 上；**而主干那次前进，恰好新增了 lane 要写的那个文件**。

⇒ lane 从旧基点看：它是「新建」；主干从新提交看：它也是「新建」⇒ Git 判 add/add。

### 怎么确认（不是看 commit，是看文件）

```bash
ls ../RAG.worktrees/<域>-common/docs/DRIFT/<域>/vs-common.md
```

⇒ **文件不存在 = 基点早于脚手架落地那次提交** ⇒ 必撞。
（`git merge-base --is-ancestor` **查不出**这个 —— 两边确实都是对方的后代。）

### 处置

- **lane 开工前**：`git merge do-my-own-gh`（文件级不重叠 ⇒ 干净）⇒ 脚手架进来了，文件变成「修改」而非「新增」。
- **或**由整合者把 lane 分支 `reset --hard` 到含脚手架的提交。
  ⛔ **不要用 `-s ours` 抹掉 lane 的成果**（本仓已有先例：5 条陈旧分支因此只登记不回退）。

### 留下的守门网

[`docs/Worktree.md`](../../Worktree.md) §二（lane 版）与 §三·1d 都写了
「**建完 lane 立刻确认脚手架在位**」+ 具体命令。

---

## PIT-LANE-02：「改一处受益全域」**不是**「可以并行改」

### 症状

9 条域 lane 同时开工，每条都在共享层的**同一个类**上提改动。

### 真实原因

`DRIFT-LIKEAPI-015`（Content-Type 大小写判据）落点是 `GoStyleJsonBodyConverter`
—— **6 个域共用这一个类**（message / like / favorite / follow / article / users…）。

⇒ 「改一处、全域受益」这个**正确的事实**，会诱发「那我顺手改一下」的错误动作。

### 处置

| 层 | 谁能改 |
|---|---|
| `service/common/**` | 🔴 **只有 `align/global-common`**（AGENTS.md §5.3c·§5.3d） |
| 本域 `service/<域>/**` ＋ `docs/DRIFT/<域>/**` | ✅ 本域 lane |

⇒ 域 lane 发现 common 侧缺陷 ⇒ **只登记**（现象 + 证据 + 建议修法）⇒ 转派 `global-common` ⇒ **串行**落地。
⇒ **域侧能自修的域侧修**（本域的调用方式 / 配置 / 断言 / 装配）—— 不甩单，也不越界。

### 教训

**「修一处受益 N 处」是「修的**动机**」，不是「修的**权限**」。**
共享层的写权是**排他**的，无论动机多正当。

---

## PIT-LANE-03：「共享层已验」≠「本域已验」

### 症状

某域 lane 看到共享层的 6 个类都在、10 个域的 yaml 都在 ⇒ 在台账里写「✅ 已对齐」。

### 真实原因

共享层的**类存在**只证明**机制在**；
**本域的可观察形状**取决于两件事对齐：
① 本域 yaml 的 `rag.http.go-form-limit.mode`；② **Go 侧本域是否注册 `httpx.SetErrorHandler`**。

⇒ Go 侧注册 ⇒ 解析失败返回 **200 + 业务信封**；
Go 侧未注册 ⇒ 返回 **400 + Go 原文**。
⇒ 共享层只能出**判据**，出不了**信封文案**（文案在**各域自己的**错误码表里）。

### 本仓实测（2026-10-27）

| 域 | Go 侧 `httpx.SetErrorHandler` | RAG `go-form-limit.mode` | 两侧同向？ |
|---|---|---|---|
| comment / follow / hot / like / message | **已注册**（5 域） | `envelope` | ✅ |
| article / task / user / admin | **未注册**（4 域） | `raw` | ✅ |
| favorite | 未注册 | `off`（**刻意**） | ✅ 两侧都不过限 ⇒ 零触碰 |

⇒ 与 global 台账记的「5 域注册 / 4 域未注册」**逐条吻合**（交叉验证通过）。

### 教训

这是 AGENTS.md §3.3「**注解存在 ≠ 机制生效**」在 lane 语境下的形态：
**共享层已验 ≠ 本域已验** ⇒ lane 必须在本域**跑一次真实请求**确认拒绝形状。

---

## PIT-LANE-04：同一域同时开 `align/<域>` 与 `align/<域>-common` ⇒ 台账不撞但**代码撞**

### 症状

两套 lane 的台账文件**不重叠**（`docs/DRIFT/<域>/<组件>.md` vs `docs/DRIFT/<域>/vs-common.md`），
但它们**会同时改 `service/<域>/**` 的代码** ⇒ 回并时不是文件冲突，而是**语义打架**：
「同一个类的两处改动谁说了算」—— Git 能合，但合出来的行为可能谁都没审过。

### 处置

**同一域只开一套。**
`<域>-common` lane 做的是**跨层对账**（本域 ↔ 共享层），
`align/<域>` 做的是**域内组件级对齐**（api/rpc/common/proto）⇒ 两者目的不同、时间轴也不同 ⇒ 别同时开。

---

## 效力边界

- PIT-LANE-01 的「建完确认脚手架」是**人工检查**（一条 `ls`）⇒ **没有机器守门**。
  ⇒ 若要自动化，可在 `tools/difftest/` 加一条「10 条 lane 分支的基点必须含 `vs-common.md`」的静态检查 —— **目前未做**（登记为已知缺口）。
- PIT-LANE-02 / 03 / 04 的约束靠**台账与 AGENTS.md**承载 ⇒
  机器侧目前只有 `tools/difftest/check-global-lane-ownership.py`（global 域内 lane 的写权），
  **尚未覆盖这 10 条 `<域>-common` lane** ⇒ 同样登记为已知缺口。
