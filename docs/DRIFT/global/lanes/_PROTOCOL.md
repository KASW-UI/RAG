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
3. **lane 不写共享文件**
   —— ⛔ `AGENTS.md` · 三个 `_INDEX.md` · `docs/DRIFT/_MANIFEST.json` ·
   ⛔ `docs/DRIFT/global/{_README,common-tails,cross-domain,cycle-deps}.md`
   ⇒ 这些由**整合者**在所有 lane 合并后统一更新（§5.3 同款纪律）。
   ⇒ lane 把自己的论证写进 **`docs/DRIFT/global/lanes/<lane>.md`**（自足片段，不依赖上面 4 个文件）。

> ⚠️ **共享文件（含 `AGENTS.md`）的改动要尽早提交** —— 实测已被并发会话 `git checkout` 整体回滚过一次。
> ⚠️ **`git stash` 不作隔离手段** —— 它是工作区级状态，会被并发 agent 互偷。

## 三、lane 清单（2026-10-25 建）

| lane | 分支 | 工作树 | 议题 | 独占写权 |
|---|---|---|---|---|
| **L1** | `align/global-delivery` | `../RAG.worktrees/global-delivery` | **X-4** like 半边（社区事实投递链路） | `service/like/like-rpc/**` + `service/comment/comment-rpc/**` |
| **L2** | `align/global-rpcgate` | `../RAG.worktrees/global-rpcgate` | **X-5** 残留（3 个 rpc 域守门复查 + slow threshold） | `service/{users/user/user-rpc,message/message-rpc,hot/hot-rpc}/src/test/**` |
| **L3** | `align/global-ddl` | `../RAG.worktrees/global-ddl` | **X-7** 残留（5 域 DDL 双侧活体逐列对照） | `tools/difftest/check-ddl-static.py` + 新脚本 |
| **L4** | `align/global-flyway` | `../RAG.worktrees/global-flyway` | **X-1** 残留（三进程同库共存真 PG 验证） | `tools/difftest/check-flyway-concurrent.sh`（新） |
| **L5** | `align/global-signoff` | `../RAG.worktrees/global-signoff` | **C 类登记** + T-7/T-11/T-12 定性 + A6 订正 | `docs/DRIFT/global/lanes/**` + `docs/Intentional_Deviations/global/**` + `docs/Pitfalls/global/**` |

⇒ **L1~L5 文件级零重叠** ⇒ 五路可同时开工。

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

## 六、⛔ 全域禁区（所有 lane 无例外）

- **`service/favorite/**`** 与 **`docs/*/favorite/`** —— `favorite` 域对 `global` **零写权**。
  ⇒ 共享层改动会影响 favorite 的可观察行为 ⇒ 每一次共享层改动**必须记账并告知 favorite 域 owner**。
- **`Sea-RideTheWind_golang`** —— 冻结只读，真源。
- **非 Spring Boot 的 Java 实现** —— 不在范围内。

## 七、⚠️ 已知的坑（别重新踩一遍）

| 坑 | 现象 | 处置 |
|---|---|---|
| **PIT-GLOBAL-019** | 构建**进行中**改源码 ⇒ 绿的是**旧字节码** | 改完源码**重跑一遍**才算数（`stat` 比对 class/source mtime） |
| **PIT-GLOBAL-018** | 变异脚本首版写成**编译不过**的表达式 lambda ⇒ maven 压根没跑测试 ⇒ surefire 里留着上一轮 XML ⇒ **报成假绿** | 「跑前删旧 XML + 看 maven 退出码 + 查 COMPILATION ERROR」三道闸 |
| **B10 变异坑** | 改**上游模块的资源**（`users-common` 的 DDL）只跑下游 ⇒ Maven 从 `~/.m2` 解析旧 jar ⇒ 改动到不了运行代码 | 把上游拉进**同一 reactor**（`-am`）+ 加 `-Dsurefire.failIfNoSpecifiedTests=false` |
| **§3.3** | 「注解存在 / Bean 存在 / 编译通过」≠「机制生效」 | 纯 Mockito 单测**不算** ⇒ 必须真容器 / 真 PG / 真启动 |
| **§3.3** | 「只跑 RAG 的探针」不算数 | 要「双侧同时跑」，Go 半边必须**真调真源** |
