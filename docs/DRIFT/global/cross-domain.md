<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25 · 本轮只做状态更新 + 一条新发现，未改动任何跨域代码 -->

# global · 跨域不变量台账（cross-domain）

> **归属**：`global` 收口域的「cross-domain」议题档（§5.3b）。
> **对象**：**涉及 3+ 个域、无法归给任何单一域**的不变量。
> ⚠️ **边界**：单域内部的漂移记在该域自己的台账；**跨域不变量**才落这里。

---

## 〇、状态总表（2026-10-25 实测）

| # | 不变量 | 涉及组件 | 状态（2026-10-25） | 本轮 |
|---|---|---|---|---|
| X-1 | 共享 Flyway 账本 | `message-rpc`↔`like-rpc`↔`article-rpc` | 🟡 配置层 + 运行时已闭环；**三模块同库共存**仍 `unresolved` | 未展开 |
| X-2 | 建表责任交叉 · client 契约 | `user-rpc`↔`admin-rpc` · api↔rpc proto | 🔴 均未审 | 未展开 |
| X-3 | 事实投递链路 | `like` 的 api ↔ rpc ↔ 第三个仓库 | ⏸ 链路本身不是任何组件的属性 | 未展开 |
| X-4 | 社区消费者差异 | Go 用 `like`，RAG 不用 | ⏸ 仍未决（交接 #9，与 `DRIFT-LIKE-003` 同一条） | 未展开 |
| X-5 | 裸 `ServerCallHandler` 不被 dispatch | task 域 | ⏸ 仍未决（交接 #10） | 未展开 |
| X-6 | `DRIFT-HOTAPI-030`（Tomcat 数「总参数个数」vs Go 数「非空值个数」） | hot + **全部 10 个 api** | 🔴 未修 · 跨域 · 待拍板 | 未展开 |
| X-7 | DDL 双侧活体逐列对照 | `like` 域 + 其余 5 域 | 🔴 未修 · 跨域 | 未展开 |
| **X-8** | 🆕 **真源分层（Go 出处一半在框架里）** | **全部域** | ✅ **本轮已查明并记账** | ✅ **本轮新增** |

---

## 一、X-8 🆕 **真源分两层**（2026-10-25 新发现，影响全部域）

### 1.1 事实

AGENTS.md §3.1 规定「任何行为一致性审查必须**以 Go 为唯一基准**核对 RAG」。
本轮实测发现：**「Go」这个词必须再分两层，否则一半的对照会落空。**

| 层 | 位置 | 覆盖什么 | 本轮证据 |
|---|---|---|---|
| **L1 项目层** | `/Users/pr/Documents/java&golang/Sea-RideTheWind_golang` | 业务代码：`service/<域>/{api,rpc,common}`、proto、yaml | `service/common` 只有 `logger` / `response` / `snowflake` / `observability` / `communityfact` **五个包** |
| **L2 框架层** | `$GOMODCACHE/github.com/zeromicro/go-zero@v1.10.2` | **请求保护层四层**（MaxBytes / MaxConns / Breaker / Shedding）· HTTP body 解析 · gRPC 超时/熔断/降载 | `service/common` 在 Go 侧**根本没有** `http/` 与 `protection/` 子包（`git log --all --diff-filter=A` 实证：从未有过） |

⇒ **RAG 的 `service/common/http/` + `service/common/protection/` 整体对应的是 go-zero 的对应件**，
不是 Go 项目的 `service/common`。
⇒ 项目层 `service/common` 的对应物是 RAG 的 `service/common/{snowflake,logger,response,observability/...}`。

### 1.2 为什么这是**跨域**问题

它不是某一个域的事：**凡涉及「请求保护层 / body 解析 / RPC 中间件」的漂移，
它的 Go 出处都在 L2** ⇒ 而这些横跨 10 个 api + 10 个 rpc ⇒ **一个域查不清全局**。

### 1.3 本轮的实际后果

台账前几版把 Go 出处写成「Go 侧 `service/common/...`」⇒ 实测**全部落空**
（`withJsonBody` / `MaxConnsHandler` / `path.Join(cc.Target(), method)` 在项目树里**都不存在**）。
⇒ 已在本域 [`common-tails.md`](common-tails.md) §〇 纠正，并给出两侧逐行出处。
⇒ 已登记为踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../../Pitfalls/global/common-tails.md)。

### 1.4 ⚠️ **给其它域的提醒（不属 global 的写权范围，只记账）**

各域台账里的 Go 出处若写成「Go 侧 `service/common/xxx`」，**需要各自复核**是否其实是 L2（go-zero）。
⇒ **本轮不代其它域改**（§5.3b 规则：只写自己域）⇒ 建议下一轮开「全仓 Go 出处 L1/L2 归属普查」。

---

## 二、其余 7 条的状态说明（2026-10-25）

本轮**未展开**这 7 条的逐条对照（它们的落点分散在多个域的代码里，
且多数需要**真 PG / 真启动**才能判定 ⇒ 不适合与 T-10 同批做）。

| # | 为什么本轮没动 |
|---|---|
| X-1 | 需要**三个 rpc 模块同库共存**的运行时验证 ⇒ 需真 PG + 三进程并发 ⇒ 独立一轮 |
| X-2 | `user-rpc`↔`admin-rpc` 的建表责任与 client 契约 ⇒ 跨 users 域内部两个子域 ⇒ 需先读 users 域台账 |
| X-3 | 链路末端在**第三个仓库** ⇒ 本轮未定位那个仓库 |
| X-4 | 与 `DRIFT-LIKE-003` 同一条 ⇒ 归 like 域，但需 global 协调（涉及 common 侧的 communityfact） |
| X-5 | task 域内部 ⇒ 但「裸 `ServerCallHandler` 不被 dispatch」会**反噬所有 rpc 域** ⇒ 需先在 task 域定论 |
| X-6 | 待**用户拍板**（改 Tomcat 三旋钮 vs 改 10 个 api 的参数解析）⇒ 拍板前不动 |
| X-7 | 需要**真 PG 活体**逐列对照 ⇒ `check-ddl-static.py` 的域预设要扩 ⇒ 工具改动，可与本轮并行 |

⇒ **排期建议**：X-7（工具扩预设，成本最低）→ X-1（真 PG 三进程）→ X-2 → X-3 → X-4/X-5；
X-6 **等用户拍板**。

---

## 三、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 7 条跨域不变量状态；新增 **X-8 真源分层（L1 项目层 / L2 go-zero 框架层）** —— 本轮实测发现，影响全部域的对照方法 |
