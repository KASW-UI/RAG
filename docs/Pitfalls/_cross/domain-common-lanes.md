# 🔴 Pitfall：`<域>-common` lane 的四个坑（域 ↔ `service/common` 跨层对账）

> **归档时间**：2026-10-27 · **性质**：并发工作流坑（影响全部 10 条 lane）
> 🆕 **2026-10-27 01:44 补充**：10 条 `<域>-common` lane **工作树已全部撤掉** ⇒ 执行形态改为 **G1~G4 四棵波次工作树**（`align/g1-users-follow` / `align/g2-message-like-task` / `align/g3-article-comment-favorite-hot` / `align/g4-service-common`）⇒ **本档的坑仍然成立**（写权唯一 / 基点 / 阶段划分都没变），只是**载体换了**；新增 **PIT-LANE-05/06**。
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

## PIT-LANE-05 · 「统计类/模块被谁用」的两个假阳性陷阱（2026-10-27 实测）

**现象**：为 `users-common` / `service/common` 做「跨域引用度数」统计时，得到了一份**看起来合理、实则偏高**的表。

### 陷阱 A：全文正则匹配 ⇒ 把 **javadoc 里的 `{@code …}` 提及**当成真引用

- 实测：`service/common/src/main/java/com/rag/common/exception/BusinessException.java:14` 的注释里写了
  `{@code com.rag.users.common.errmsg.ErrorCode}` ⇒ 全文正则判定「`common` 域用了 `ErrorCode`」。
- 但 `service/common/pom.xml` **并不依赖** `users-common` ⇒ 那处提及**不是引用**。
- ⇒ 误判后果：`ErrorCode` 被判成「共享层 + users 共用」⇒ **users 专用类被错误提级到共享层**（写权面平白扩大）。
- ✅ **修法**：**只认 `import` 语句**（`^import\s+(?:static\s+)?([\w.]+)\s*;`），不看正文全文。

### 陷阱 B：裸类名匹配 ⇒ 撞各域同名类

- `ErrorCode` / `SchemaInitializer` / `Response` 在多个域**各有同名类** ⇒ 裸类名匹配产生大量假阳性。
- ✅ **修法**：**全限定名**匹配（如 `com.rag.users.common.jwt.JwtUtil`）。

### 订正

本仓据此订正过一次：`users-common` 的分类从「**9 个真共享 + `ErrorCode` 半共享 + 2 个 users 专用**」
改为「**8 个跨域类 + 3 个 users 专用类**」⇒ 提级清单从 10 个类缩到 8 个类。

### 机器守门

`tools/difftest/check-wave-coupling.py` 的 `scan_users_common_classes()` / `scan_common_radiation()`
**一律用 import 判定** ⇒ 同一口径重算，与文档快照比对 ⇒ 漂移即 `EXIT=1`。
⇒ **守门本身也要能被变异测**：该守门已做两次变异（注入新 gRPC 边 / 改跨域 topic 名）⇒ 均 `EXIT=1` 且精确点名。

---

## PIT-LANE-06 · 「守门登记了一条**已不存在**的 lane」＝ 恒绿假绿（2026-10-27 01:44 实测）

**现象**：2026-10-27 工作树重组，`align/global-common` 那条 lane **连分支一起被删**，
但写权守门 `check-global-lane-ownership.py` 的 `ACTIVE` 档**仍然登记着它** ⇒ 守门**照样 EXIT=0**。

**为什么是假绿**：`ACTIVE` 里的 lane 名只被当成「一组 pattern」，
**从不去查这个 lane 的分支是否真的存在** ⇒ 守门盯着一棵**不存在的树**，
断言 1（两两不重叠）只在**它自己的白名单内部**求交集 ⇒ 永远无冲突 ⇒ 永远绿。

**修法**（已落地 `tools/difftest/check-wave-lane-ownership.py`）：

1. **断言「每条 ACTIVE lane 的分支必须真实存在」** —— 查 `refs/heads/` + 两个远端 ref。
2. **断言「每条白名单 pattern 必须命中真实文件」** —— 同一次实测抓出的**第二个假绿**：
   `service/users/user/src/**` 这个 pattern **命中 0 个真实文件**
   （`service/users/user/` 底下根本没有 `src/`，它是 `user-api`/`user-rpc`/`user-proto` 三个模块）
   ⇒ 守门对 G1 的用户域**完全失明**（展开数从 376 跳到 1542 才暴露）。
   ⚠️ **写路径时必须先 `ls` 确认目录形态** —— Maven 聚合目录（`service/<域>/<域>-<组件>/`）
   与单模块目录（`service/<域>/<组件>/`）**层级不同** ⇒ 凭印象写必错。

**教训**：**任何「登记 X ⇒ 断言 Y」的守门，都必须有一条「X 本身还存在吗」的断言。**
⇒ 否则「X 被删」会静默地把守门降级成**永远绿**的摆设 —— 而摆设比没有守门更危险
（它给的是**虚假的安全感**）。

**配套**：`check-wave-lane-ownership.py` 已做 6 条变异验证
（抢 favorite / 抢共享层 / 抢 user-api / 抢 users-common / 路径写错 / lane 不存在）⇒ 全部转红且红在目标断言上。

---

## PIT-LANE-07 · 守门用**全文 grep** 判措辞 ⇒ 改掉**锚点那一行**后**守门恒绿**（2026-10-27 02:23 实测）

**现象**：新建 `tools/difftest/check-wave-role-model.py`（守「G4 = 常设执行者」这个角色模型），
第一版把断言写成「**这个文件里含 `常设执行者` 就算过**」⇒ 变异把**规范定义那一行**改回旧模型后，
**断言照样绿** ⇒ 首版变异测试直接报 `2/5 条变异未被捕获 ⇒ 守门有假绿`。

**为什么是假绿**：同一个概念在**一份文档里出现多次** ——
`AGENTS.md` 的 §3.5 指针句、§5.3d 顺序行、§5.3e 定义行**都含**「常设执行者」三个字。
⇒ 变异只改**定义那一行** ⇒ 另两处**原封不动** ⇒ `_has(file_lines, "常设执行者")` 仍为真 ⇒ 断言不红。
⇒ **守门守的是「一个散落的词」，不是一个「契约」** ⇒ 词还在，契约已经破了。

**修法**（已落地，三条一起，缺一条都会复发）：

1. **断言必须锚定到唯一的规范位置** ⇒ A2 改为**块级**：只读 `AGENTS.md` 的
   **§5.3e 块**（`3e.` 起 → `3f.`/`4.` 前）与 **§5.3d 块**（`3d.` 起 → `3e.` 前）。
2. **引入规范锚点** ⇒ 4 份域台账各含**唯一一处** `G4 = 共享层常设执行者` ⇒ A7 查这个锚点
   ⇒ **锚点被改 ⇒ 该文档的角色描述失效 ⇒ 转红**。
3. **变异必须全量替换同锚点** ⇒ 只替换**首个命中**时，同锚点的其它处仍在 ⇒ 仍绿
   （**M7 就这么漏的**）⇒ 变异器改为 `replace(old, new)`（全量）。

**教训**：**「某文件包含 X」不等于「X 在该断言的位置」。**
⇒ 守门断言必须**锚定到唯一的规范位置**（块级 / 锚点级），否则它守的是一个**散落的词**而不是一个**契约**。

**元规则**：**守门写完必须做变异验证，且变异要打在被断言的那一处** ——
打在**同义复述**上（例如只改 §3.5 的指针句、不改 §5.3e 的定义句）⇒ 守门不红 ⇒ 变异测试**自己也是假绿**。

---

## 效力边界（2026-10-27 02:23 更新）

- PIT-LANE-01 的「建完确认脚手架」仍是**人工检查**（一条 `ls`）⇒ **仍无机器守门**。
  ⇒ 缺口**依然存在**（可加一条「波次分支的基点必须含 `vs-common.md`」的静态检查）。
- ✅ **PIT-LANE-02 的「写权唯一」已机器化**（2026-10-27）：`tools/difftest/check-wave-lane-ownership.py`
  断言 **G1~G4 白名单两两不重叠** ＋ **`favorite` 只归 G3** ＋ **共享层只归 G4**
  ＋ **私有库无跨域引用** ⇒ **原来的「尚未覆盖」缺口已闭合**。
- 🆕 **PIT-LANE-06 的两条新断言**（同一守门）：**白名单路径必须命中真实文件** ＋ **分支必须真实存在**
  ⇒ 「守门登记了已不存在的 lane」「路径写错导致对某一面失明」这两类假绿**已被堵死**。
- ✅ **PIT-LANE-07 已机器化**（2026-10-27 02:23）：`tools/difftest/check-wave-role-model.py`
  ⇒ 角色模型守门改为**块级 / 规范锚点级**判定（A2 读 §5.3e·§5.3d 两块 · A7 查规范锚点）
  ＋ **变异全量替换** ⇒ **7 条变异全部转红且红在目标断言上**（`--mutation-test`）。
- PIT-LANE-03（共享层已验 ≠ 本域已验）与 PIT-LANE-04（同域两套 lane 语义打架）
  靠**台账与 AGENTS.md**承载 ⇒ **仍无机器守门** ⇒ 登记为已知缺口。
  ⚠️ PIT-LANE-04 在新形态下**部分消解**：粒度已是「一波次一棵」，同域两套 lane 并存的空间小了
  ⇒ 但 `align/<域>`（组件级对齐）与波次工作树**仍可能同域并存** ⇒ 坑位**仍然成立**。
