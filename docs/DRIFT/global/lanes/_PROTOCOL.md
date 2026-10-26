# `global` 多 lane 并行协议（2026-10-25）

> 📌 本文件是 **`global` 域专属**的并发操作手册（通用部分见 [`docs/Worktree.md`](../../../Worktree.md)）。
> **冲突时以本文件为准**（它只收窄，不放宽）。

## 一、为什么需要拆 lane

`global` 收口域的待办横跨 **6 个微服务域 + `service/common` 共享层 + `tools/` + 三档台账**，
19 轮单会话串行推进（24 个提交）。实测瓶颈**不是写码速度，是上下文成本**：

| 台账文件 | 字节 | 每轮都要重读 |
|---|---|---|
| `docs/DRIFT/global/_README.md` | 49,886 | ✅ |
| `docs/DRIFT/global/common-tails.md` | 68,534 | ✅ |
| `docs/DRIFT/global/cross-domain.md` | 93,286 | ✅ |
| `docs/DRIFT/global/cycle-deps.md` | 9,699 | ✅ |
| **合计** | **221,405** | ⇒ 单轮 ≈ 6~7 万 token 的纯台账 |

⇒ **拆法必须是「按叶子文件切」，不是「按议题切」** ——
议题会跨文件（`X-4` 同时碰 like/comment/common），**文件才是不冲突的单位**。

## 二、三条硬规则

1. **lane 基线 = `align/global` 的 HEAD**（⛔ 不是 `do-my-own-gh`）
   —— 否则看不到 19 轮的代码产出与台账，等于重做已修项。
2. **每条 lane 只写自己任务书里的白名单文件** ⇒ 文件级零重叠 ⇒ 永不冲突。
   ⇒ 🟢 **机器守门**：`python3 tools/difftest/check-global-lane-ownership.py`
   （断言**在跑的 6 条** lane 白名单**两两不重叠**（已合回的 L1~L5 单独归档、不参与重叠判定）· 10 个共享文件**无人拥有** · **favorite 零写权**；`EXIT=0` 才算合规；
   已做 4 条变异**全部转红且红在目标断言上**）
3. **lane 不写共享文件**
   —— ⛔ `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json` ·
   ⛔ `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md`
   ⇒ 这些由**整合者**在所有 lane 合并后统一更新（§5.3 同款纪律）。
   ⇒ lane 把自己的论证写进 **`docs/DRIFT/global/lanes/<lane>.md`**（自足片段，不依赖上面 4 个文件）。

> ⚠️ **共享文件（含 `AGENTS.md`）的改动要尽早提交** —— 实测已被并发会话 `git checkout` 整体回滚过一次。
> ⚠️ **`git stash` 不作隔离手段** —— 它是工作区级状态，会被并发 agent 互偷。

## 三、lane 清单（2026-10-25 建 · 2026-10-26 扩到 15 条）

### 3.1 在跑（**当前无**）

> ✅ 第二十七轮（L6~L11）与第二十八轮（L12~L15）**全部已合回** ⇒ 写权已并入 `align/global`。
> ⇒ 下一轮开 lane 时：**① 在本节加「在跑」表**（含分支 / 工作树 / 议题 / 独占写权）
> **② 同步在 `tools/difftest/check-global-lane-ownership.py` 的 `ACTIVE` 档登记**（否则守门看不到它）
> **③ 机器守门 `EXIT=0` 才算合规**。

### 3.2 已合回（第二十八轮 · 4 条）

> ⚠️ **本轮修的是「生产启动阻塞」**（不是守门）⇒ 白名单含 `src/main/resources` 与 `pom.xml`。
> ⚠️ **背景**：第 27 轮后盘点发现，各域台账里有一批「域外 ⇒ 交主会话派单」的条目从未被 global 收口
> （2026-10-24 follow 域 `rpc.md:65-97` 已写明并点名 5 个模块）⇒ 本轮把它们真正修掉。

| lane | 分支 | 议题 | 状态 |
|---|---|---|---|
| **L12** | `align/global-rpcboot-a` | **生产启动阻塞**（article-rpc / comment-rpc / like-rpc）：`discovery://` 两斜杠 ×5 + `grpc-inprocess` test scope ×2 | ✅ 已合回 |
| **L13** | `align/global-rpcboot-b` | **生产启动阻塞**（message-rpc / task-rpc）：两斜杠 ×2 + `grpc-inprocess` test scope ×1 + **task-rpc 无 `Main-Class`** | ✅ 已合回 |
| **L14** | `align/global-rpcboot-hot` | **hot-rpc 无 `Main-Class`**（L11 漏报的那一条） | ✅ 已合回 |
| **L15** | `align/global-rpcboot-tools` | **守门缺口**：`check-jar-executable.sh` 纳入 rpc + `check-grpc-runtime-classpath.py` 排除 `target/`（存量恒红缺陷） | ✅ 已合回 |

⇒ **4 条 lane 按 maven 模块零重叠**（L12: article/comment/like · L13: message/task · L14: hot · L15: 只碰 tools）

⚠️ **favorite 域全程零触碰**：`favorite-rpc` 的 2 处两斜杠 + 1 处 `grpc-inprocess` test scope
**保留在 baseline 里**（L15 的 M3 变异专门验证这一点）⇒ **不修、不记账成「已修」**。

### 3.3 已合回（第二十七轮 · 6 条）

> ✅ **6 条全部已合回** ⇒ 不再是并发方 ⇒ 其白名单与在跑的 L12~L15 的重叠**不算违规**。

| lane | 分支 | 议题 | 状态 |
|---|---|---|---|
| **L6** | `align/global-x5gate` | **X-5 轴**真 server 守门（admin-rpc / article-rpc）+ article slow 轴 + 缺口④ 复评 | ✅ 已合回 |
| **L7** | `align/global-slowgate` | **X-5 轴** + **slow 轴**（comment-rpc / like-rpc） | ✅ 已合回 |
| **L8** | `align/global-slowgate2` | **slow 轴**推广（user-rpc / message-rpc / hot-rpc） | ✅ 已合回 |
| **L9** | `align/global-interrupt` | **slow 轴**（task-rpc / follow-rpc）+ **缺口③ 的定性**（打断维度） | ✅ 已合回 |
| **L10** | `align/global-ddldeep` | **DDL 盲区**：触发器函数体逐条核对 + task/message DDL runner 重建 | ✅ 已合回 |
| **L11** | `align/global-flywaydeep` | **Flyway 三条边界**：真 rpc 形态 / 迁移失败与回滚 / 压力与随机延迟 | ✅ 已合回 |

### 3.4 已合回（历史 lane，写权已并入 `align/global`）

| lane | 分支 | 议题 | 状态 |
|---|---|---|---|
| **L1** | `align/global`（**本体**） | **X-4** like 半边 + 共享层 `Dispatcher.runAsync` / OTel 全局 SDK / 超时响应单写者 | ✅ 已合回（= 集成目标分支） |
| **L2** | `align/global-rpcgate` | **X-5** 残留（3 个 rpc 域守门复查 + slow threshold） | ✅ 已合回 `d7a126a` |
| **L3** | `align/global-ddl` | **X-7** 残留（5 域 DDL 双侧活体逐列对照） | ✅ 已合回 `eee3bbb` |
| **L4** | `align/global-flyway` | **X-1** 残留（三进程同库共存真 PG 验证） | ✅ 已合回 `8e6b7b2` |
| **L5** | `align/global-signoff` | **C 类登记** + T-7/T-11/T-12 定性 + A6 订正 | ✅ 已合回 `2076d42` |

> ⚠️ **L2~L5 与在跑的 lane 有白名单重叠**（例如 L2 曾拥有 `user-rpc/src/test/**`，L8 现在也拥有它）⇒
> **这不是违规** —— 它们**已合回**，不再是并发方 ⇒ 机器守门把「在跑」与「已合回」**分开断言**：
> **重叠只在「在跑」的 lane 之间为违规**。

### 3.5 ⚠️ 第二十七轮新增的两条拆分纪律

1. **按模块零重叠**（不是纯按文件）—— L2 的教训：同模块两个 lane 会互踩 `target/`（同一模块并发 `mvn` ⇒ `target/` 竞争）
   ⇒ 6 条 lane 分配到 **9 个互不相交的 maven 模块**（admin/article/comment/like/user/message/hot/task/follow）
   + 2 条 `tools/difftest/` 脚本 lane（`ddl*` / `flyway*` 前缀互不撞）。
2. **每条 lane 一个工作树** ⇒ 变异在自己的工作树里跑（PIT-GLOBAL-020）⇒ 提交前 `git diff` 逐个确认变异已还原。


> ⚠️ **2026-10-25 两处白名单订正**（机器守门同步改过 ⇒ `EXIT=0`）：
> ① **L1 补登记 `service/common`** —— T-11 / T-13 两轮把写权扩到了 `service/common`
>    （observability / http / pom / resources / test）⇒ 当时**只记账没登记**
>    ⇒ 守门给的是**假保证**（它查的是「声明」，不是「实际」）⇒ 现补齐，使声明 = 实际。
> ② **L2 补登记 `service/users/admin/admin-rpc/src/test/**`** —— slow-threshold 守门
>    选域时选到 admin-rpc ⇒ 原白名单不含它 ⇒ 属**越权**⇒ 现补进白名单
>    （⚠️ **只加 test 目录** ⇒ admin-rpc 的**生产代码**仍不归 L2）。

> ⚠️ **L1 不是「新开」的** —— 它就是 `align/global` 本体（**已有一个会话在上面跑**）
> ⇒ ① **不要另开 `align/global-delivery`**（会与在跑的会话重复劳动）；
> ② L1 同时是**集成目标分支** ⇒ L2~L5 干完直接合回 `align/global`。
> ⇒ **代价**：L1 在 `align/global` 上提交时，**必须确认自己在改的文件**（见 §七 的并发坑）。

> ⚠️ **L3 与 L4 同写 `tools/difftest/` 目录** ⇒ git 只跟踪**文件**不跟踪目录 ⇒
> 只要**文件名**不撞就零冲突 ⇒ 约定：**L3 的新脚本一律 `ddl*` 前缀，L4 的一律 `flyway*` 前缀**。
> ⇒ 且 **`tools/difftest/README.md` 两条 lane 都不许写**（共享说明文件 ⇒ 整合者统一更新）。

## 四、每条 lane 的开工检查（缺一不可）

1. `git fetch upstream && git log --oneline -1` ⇒ 确认基线没被别的会话推走。
2. 读**自己那份 lane 任务书**（`docs/DRIFT/global/lanes/<lane>.md`）⇒ 里面有「必读」精确指针。
3. 按任务书的指针**定向读**台账（`grep` 出自己那几条的段落）——
   ⛔ **不要通读 4 个规范台账文件**（那是 221 KB 的上下文陷阱，见 §一）。
4. 读 `docs/Pitfalls/global/**` 里与本 lane 相关的条目（§5.3b 必读）。
5. **先让它真的启动一次**（§3.3 纪律）⇒ 再谈修。

## 五、合并纪律

1. 所有 lane 合回 `align/global`（顺序无所谓，文件不重叠 ⇒ 通常无冲突）。
2. 整合者把各 lane 的 `lanes/<lane>.md` 片段 **fold 进** 对应的规范台账文件
   （`common-tails.md` / `cross-domain.md` / `cycle-deps.md` / `_README.md`）。
3. 整合者统一更新 `AGENTS.md` + 三个 `_INDEX.md` + `_MANIFEST.json`。
4. 整合者跑**全量** `mvn -o clean test`（⚠️ 绿测试可能锁死错误答案 ⇒ 必须配变异，见 §3.3）。
5. 🔴 **凡改过台账文本 ⇒ 必须重跑第 1 层并与「改前」对比**（`python3 tools/check-ledger-selfcheck.py --domain <域>` ⇒ 看「台账引用路径 N 个 ⇒ 缺失 M 个」的 **M**）⇒
   **理由（第三十七轮实测踩了三次）**：台账里**引述一个「已知是错的路径」** ⇒
   ① 写成**活路径形态** ⇒ 工具把它**报出来**（噪声 · E42 已记）·
   ② 写成**与叙述词同行** ⇒ 工具把它**吃掉**（**假阴性** · 更危险）⇒
   ⇒ **两个方向都会让「真实缺陷」从台账的复核网里消失** ⇒ ⇒ **改台账后的 MISSING 集合必须与改前一致**，
   **多一条少一条都要查清**（本轮就是靠这个 diff 当场抓到自己写的 E61 条目把 like 域真缺陷 E43 静默了）。
   ⚠️ **写台账时的正确姿势**：引述「曾写错的路径」⇒ **只写文件名（不带 `tools/` `docs/` `service/` 前缀的短形式）**。

## 六、⛔ 全域禁区（所有 lane 无例外）

- **`service/favorite/**`** 与 **`docs/*/favorite/`** —— `favorite` 域对 `global` **零写权**。
  ⇒ 共享层改动会影响 favorite 的可观察行为 ⇒ 每一次共享层改动**必须记账并告知 favorite 域 owner**。
- **`Sea-RideTheWind_golang`** —— 冻结只读，真源。
- **非 Spring Boot 的 Java 实现** —— 不在范围内。

## 七、⚠️ 已知的坑（别重新踩一遍）

| 坑 | 现象 | 处置 |
|---|---|---|
| **PIT-GLOBAL-019** | 构建**进行中**改源码 ⇒ 绿的是**旧字节码** | 改完源码**重跑一遍**才算数（`stat` 比对 class/source mtime） |
| **PIT-GLOBAL-020** | 变异测试在**共享工作树**里跑 ⇒ 变异态被**提交进 HEAD**（实测：`Dispatcher.runAsync` 被误提交成阻塞式 `Dispatcher.run` ⇒ 开关一开 like-rpc 起不来） | ① **变异只在 lane 自己的工作树里跑**；② **提交前 `git diff` 逐个确认变异已还原**（比对备份文件，别凭印象）；③ **不要在别人也在用的工作树里用 `git add -A`** —— 会把别人的**在途编辑**一并扫进提交 |
| **PIT-GLOBAL-018** | 变异脚本首版写成**编译不过**的表达式 lambda ⇒ maven 压根没跑测试 ⇒ surefire 里留着上一轮 XML ⇒ **报成假绿** | 「跑前删旧 XML + 看 maven 退出码 + 查 COMPILATION ERROR」三道闸 |
| **B10 变异坑** | 改**上游模块的资源**（`users-common` 的 DDL）只跑下游 ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ 改动到不了运行代码 | 把上游拉进**同一 reactor**（`-am`）+ 加 `-Dsurefire.failIfNoSpecifiedTests=false` |
| **§3.3** | 「注解存在 / Bean 存在 / 编译通过」≠「机制生效」 | 纯 Mockito 单测**不算** ⇒ 必须真容器 / 真 PG / 真启动 |
| **§3.3** | 「只跑 RAG 的探针」不算数 | 要「双侧同时跑」，Go 半边必须**真调真源** |
