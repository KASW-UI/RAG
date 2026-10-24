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
| **X-9** | 🆕 **「Go 是否读 body」判据有 3 份生产复制品** | favorite / hot / article | 🟡 **当前行为正确，但是定时炸弹** | ✅ **本轮新增** |

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

## 二、X-9 🆕 **「Go 是否读 body」判据有 3 份生产复制品**（2026-10-25 新发现）

### 2.1 事实

`service/common/http/GoStyleJsonBodyConverter#goReadsBody` 的 javadoc 自称是
「Go 何时读 body」这条规则的**唯一真源**，并明确警告「复制品必然与实现漂移」。
⇒ **但仓库里有 3 份生产代码各自抄了一份判据**：

| # | 位置 | 抄法 | 取的头值 |
|---|---|---|---|
| 1 | `service/favorite/favorite-api/.../controller/FavoriteController.java:242` | `contentType.contains("application/json")` | `httpRequest.getHeader("Content-Type")` ⇒ **原始** ✔ |
| 2 | `service/hot/hot-api/.../form/GoFormValues.java:272` | `contentType.contains("application/json")` | `request.getContentType()` ⇒ **原始** ✔ |
| 3 | `service/article/article-api/.../config/GoStyleJsonBodyLimitFilter.java:123` | `contentType.contains("application/json")` | `request.getContentType()` ⇒ **原始** ✔ |

⇒ **三份当前都取原始头值 ⇒ 判定与 Go 一致 ⇒ 当前行为是「对的」**。
（`FavoriteController.java:241` 的注释甚至已经写明「Go 用 strings.Contains（大小写敏感），不是 MIME 解析 —— 别用 MediaType.parseMediaType」）

### 2.2 为什么它仍是**跨域**问题（而不是三个域各自的小事）

T-1 修复**前**，RAG 内部是**自相矛盾**的：

```
Content-Type: Application/JSON
  ├─ favorite / hot / article 的三份复制品 ⇒ 「不是 JSON body」⇒ 不读   ← 与 Go 一致
  └─ GoStyleJsonBodyConverter（唯一真源）   ⇒ 「是 JSON body」⇒ 读     ← 与 Go 相反 ❌
```

⇒ 同一个请求，**闸门说不读、解析器说要读** ⇒ 这不是「某域的 bug」，是**共享判据被复制**造成的一致性风险。
⇒ **T-1 已于 2026-10-25 修复**（唯一真源改取原始头值）⇒ 现在两侧一致。

### 2.3 残留风险（**本轮未修，已登记**）

| 风险 | 说明 |
|---|---|
| 🔴 **复制品可能漂移** | 哪天有人把其中一份改成「用 `MediaType` 解析」（看起来更「地道」）⇒ RAG 立刻**内部自相矛盾**（闸门 vs 解析器），且**没有任何测试会红** |
| 🔴 **修法在别的域** | 要根治 ⇒ 三份复制品都应改为**调用** `GoStyleJsonBodyConverter.goReadsBody(...)`（唯一真源）⇒ **但那三个文件分别在 favorite / hot / article 域** ⇒ **不是 global 的写权范围**（§5.3b 规则 1：`service/common/**` 只有 `align/global` 能写；**反向不成立** —— global 也不该去改别的域的业务代码） |
| ✅ **已做的缓解** | 已在唯一真源的 javadoc 里**显式列出这 3 份复制品的位置**，并写明「哪天有人改成 MediaType 解析 ⇒ RAG 会内部自相矛盾」⇒ 下一个改它的人会看到 |

⇒ **归属**：这 3 份文件的修改**属于 favorite / hot / article 三域** ⇒ 应由各域自行收敛
（各域台账可链到本条）⇒ **global 只做记账与协调**。
⚠️ **本轮不去改它们**（§5.3b：别的域的代码，别的域自己改；global 只写自己的台账）。

---

## 三、其余 7 条的状态说明（2026-10-25）

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

## 四、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 7 条跨域不变量状态；新增 **X-8 真源分层（L1 项目层 / L2 go-zero 框架层）** —— 本轮实测发现，影响全部域的对照方法 |
| 2026-10-25 | **第二轮**：新增 **X-9** —— 「Go 是否读 body」判据存在 **3 份生产复制品**（favorite / hot / article）。T-1 修复前 RAG **内部自相矛盾**（闸门说不读 / 解析器说要读）⇒ T-1 修复后已一致。⚠️ **根治需各域自己收敛**（调用唯一真源），global 只记账不改别域代码 |
