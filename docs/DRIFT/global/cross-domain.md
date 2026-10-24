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
| X-3 | 事实投递链路 | `like`/`comment`/`favorite` 的 rpc ↔ **仓外 DC 平台** | ✅ **已闭合**（2026-10-25 B2）—— 末端不是「第三个仓库」，是仓外 `POST /v1/events`；仓内两侧 4↔4 对齐 | ✅ **已闭合** |
| X-4 | 社区消费者差异 | Go 用 `like`，RAG 不用 | ⏸ 仍未决（交接 #9，与 `DRIFT-LIKE-003` 同一条） | 未展开 |
| X-5 | 裸 `ServerCallHandler` 不被 dispatch | **task + follow**（2026-10-25 实测扩面） | 🔴 **已确证**（第七轮探针）—— grpc-java 不向裸 handler 派发 `onHalfClose` ⇒ 两个域的「造慢服务」测试**空转** ⇒ 见 §二·八 | ✅ **已确证**（机制 + 波及面） |
| X-6 | `DRIFT-HOTAPI-030`（Tomcat 数「总参数个数」vs Go 数「非空值个数」） | hot + **全部 10 个 api** | 🔴 未修 · 跨域 · 待拍板 | 未展开 |
| X-7 | DDL 双侧活体逐列对照 | `like` 域 + 其余 5 域 | 🟡 **部分闭合**（2026-10-25 B3）—— 4 个域已能活体逐列对照；其余 5 域**缺双侧库环境** | ✅ **已跑**（产出真结论 + 1 条新差异 X-10） |
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

## 二·五、X-7 🟡 **部分闭合** —— DDL 双侧活体逐列对照（2026-10-25 B3）

### ① 先修一个更严重的问题：**已有的域预设本身已经失效**

| 域 | 原预设 | 实测 | 结论 |
|---|---|---|---|
| `users` | 容器 `difftest-postgres` / 库 `go_db`,`rag_db` | 容器**已不存在**（现为 `uudifftest-postgres`），库名带 `uu_` 前缀 | 🔴 **原预设跑不起来** |
| `message` | 容器 `msgddl-pg` / 库 `go_msg_db`,`rag_msg_db` / 用户 `admin` | 容器是 `msg_ddl_pg`（**下划线**）、库是 `go_db`,`rag_db`、**该容器没有 `admin` 角色**（只有 `postgres`） | 🔴 **三重失效**（容器 / 库 / 用户） |
| `task` | 容器 `taskprobe-postgres` / 库 `task_go_db`,`task_rag_db` | ✅ 仍有效 | ✅ |

⇒ ⇒ **AGENTS.md §0/§7.2 说的「3 个域预设」里，只有 1 个真能跑**
⇒ 且脚本的 `load()` 把 psql 用户**写死成 `admin`** ⇒ 换个容器就连不上
⇒ **本轮给脚本加了按域预设的 `pg_user` 字段**（默认 `admin`）。

### ② 修好后 + 扩域 ⇒ **4 个域可跑**

| 域 | 容器 / 库 | 结论 |
|---|---|---|
| `task` | `taskprobe-postgres` | ✅ **11 列逐字相同**；类型/长度 0 差异、可空性 0 差异、DEFAULT 0 差异；索引 2≡2；约束逐条一致（**仅约束名不同**：`task_progress_pkey` vs `pk_task_progress` ⇒ 语义等价，属命名差异） |
| `message` | `msg_ddl_pg` | ✅ **37 列逐字相同**；类型/长度 0、可空性 0；🔴 **DEFAULT 6 处差异**（Go 无 / RAG `CURRENT_TIMESTAMP`）⇒ **已知且刻意保留**（两侧 INSERT 都显式绑定 ⇒ AGENTS.md §0 已登记）；🔴 **索引列不同 1 处** ⇒ **新发现 X-10**；约束 3/3 一致 |
| `users` | `uudifftest-postgres` | 🟡 **仅覆盖 `users` + `user_avatar_history`**：Go 侧**没有** admin 的表（`admins` / `admin_invites`，见 Go `service/user/admin/rpc/internal/model/data_model.go:17,32`）⇒ 那套库里 Go 侧 admin 表**未被 AutoMigrate** ⇒ **是环境缺口，不是漂移** |
| `comment` | `cdifftest-postgres` | 🟡 两侧表名一致（Go 的 `TableName()` 与 RAG 迁移脚本同名）；⚠️ Go 侧 `report_record`（`service/comment/rpc/internal/model/data_model.go:66`）**未被 migrate** ⇒ 环境缺口 ⇒ 结论覆盖其余 6 张表 |

⇒ **「未知项」的定性变了**：X-7 不再是「不知道差在哪」，
而是「**工具与 4 个域的环境已就绪，剩下 5 个域（article/like/hot/follow/favorite）缺双侧库**」
⇒ 缺口是**环境供给**，不是**审计方法**。

### ③ 仍缺的 5 个域（article / like / hot / follow / favorite）

⇒ 机器上**没有**这 5 个域的「Go 侧 AutoMigrate 库 + RAG 侧 Flyway 库」成对容器
⇒ 需要各域各自的 runner（起 Go 侧服务跑 AutoMigrate + 起 RAG 侧跑迁移）
⇒ `tools/difftest/` 现成的 runner 只有 `run.sh` / `run-hot.sh` / `run-message-ddl-parity.sh` /
`run-task-http-parity.sh` ⇒ **article / like / follow / favorite 缺 runner**
⇒ **登记为待办**（不是「已覆盖」）。

### ④ 工具改动（可重跑）

- `tools/difftest/check-ddl-static.py`
  ⇒ ① `load` / `load_indexes` / `load_constraints` 加 `user` 参数（默认 `admin`）；
  ⇒ ② 域预设加 `pg_user` 字段；
  ⇒ ③ **修好 `users` / `message` 两个失效预设**；
  ⇒ ④ **新增 `comment` 域预设** ⇒ 覆盖域数 **3 → 4**（其中可跑的 **1 → 4**）；
  ⇒ 复现：`python3 tools/difftest/check-ddl-static.py --domain <users|task|message|comment> [--indexes]`

---

## 二·六、X-10 ✅ **已签字认账** —— `message` 域索引 `idx_notification_recipient_read` 两侧列不同

⚠️ **本条已按 §5.3b 硬边界 2 移出 DRIFT 档**（决定不对齐的差异只在签字档落笔）。

| 项 | 值 |
|---|---|
| 签字决定 | **保留 RAG 的 `(recipient_id, is_read)` 复合索引**，**不改回** Go 的单列 `(is_read)` |
| 根因 | **Go 侧 gorm tag 的 `priority:1` 写到了另一个索引名**上 ⇒ GORM 只建出单列索引 ⇒ 是**声明笔误的产物** |
| 决策记录 | [`docs/Intentional_Deviations/message/rpc.md` §`SD-2026-10-23-06`](../../../Intentional_Deviations/message/rpc.md)（**message 域那一条** —— 该编号在 3 个域各有含义，引用必须带域限定）<br>⚠️ **global 侧的 `SD-2026-10-25-03` 已撤销（重复登记）** ⇒ 见 [`docs/Intentional_Deviations/global/cross-domain.md`](../../../Intentional_Deviations/global/cross-domain.md) |
| 代码侧指针 | `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:27-38` |

⚠️ **别把它悄悄改回单列** ⇒ 那里已留指针 + 签字档是唯一记录处。
⚠️ **本条的 DRIFT 侧销账日期是 2026-10-23**（`docs/DRIFT/message/rpc.md` §7 `DRIFT-MSG-V-022`），**不是** 2026-10-25 —— 2026-10-25 只是 global 侧重新发现并把指针指对。

---

## 二·八、X-5 🔴 **已确证（2026-10-25 第七轮实测）** —— 裸 `ServerCallHandler` 不被 dispatch ⇒「造慢服务」的测试**是空转的**

> ⚠️ **本条由「仍未决 · 未展开」升级为「已确证」** —— 机制已用**一次性探针**实测清楚，
> **波及面从「task 域 1 处」扩到「task + follow 两个 rpc 域各 1 处」**。
> ⚠️ **本轮只取证、未改代码**（改法涉及 task / follow 两个域的测试 ⇒ 下一轮落地）。

### ① 机制（探针实测，非推断）

用 grpc-java 1.63 起一个**真 server**，把一个**裸** `ServerCallHandler` 注册成唯一方法：

```java
// /tmp/probe-handler/Probe.java（一次性探针，不入仓）
ServerServiceDefinition.builder("probe.Service")
    .addMethod(MD, new ServerCallHandler<String,String>() {
        public ServerCall.Listener<String> startCall(ServerCall<String,String> call, Metadata h) {
            START_CALL_FIRED.set(true);          // ← 确实被调用
            return new ServerCall.Listener<String>() {
                @Override public void onHalfClose() {   // ← 【从不触发】
                    HALF_CLOSE_FIRED.set(true);
                    Thread.sleep(2000);
                    call.sendMessage("pong"); call.close(Status.OK, new Metadata());
                }
            };
        }
    }).build();
```

**实测输出**：

```
client got status=DEADLINE_EXCEEDED
RESULT startCall=true  onHalfClose=false  responded=false
```

⇒ **grpc-java 会调用裸 handler 的 `startCall()`，但**不会**把 `onHalfClose` 派发给它返回的 `ServerCall.Listener`**
⇒ ⇒ **注册进去的业务处理函数从未执行过**（`responded=false`）。

### ② 为什么这会让测试**空转**（而不是「少测一点」）

两个域的同名测试（`serverSideTimeoutIsActuallyEnforced` 形态）都是：
「注册一个**处理函数睡 10 秒**的服务 ⇒ 断言服务端超时在 ~300ms 打断它」。

| 事实 | 后果 |
|---|---|
| handler 的 `onHalfClose` **从不触发** | 「睡 10 秒」**从未发生** ⇒ 服务端**从一开始就不打算响应** |
| 客户端还挂着 `withDeadlineAfter(5, SECONDS)` | 客户端最终拿到 `DEADLINE_EXCEEDED` ⇒ `assertThrows` **照样通过** |
| `elapsedMillis < 3_000` 断言 | 由**服务端超时拦截器**取消一个**从未开始执行的调用**而满足 |

⇒ ⚠️ **测试是绿的，但它验证的是「服务端能取消一个空调用」，
而不是它 DisplayName 声称的「服务端超时打断一个正在执行的处理」。**
⇒ 这正是 AGENTS.md §3.3 点名的形态：**「绿测试可能是错误答案的守卫」**。

⚠️ **别把它读成「服务端超时机制没生效」** —— 那条断言（`elapsed < 3000`）
**确实**抓得住「`server.intercept(...)` 没挂上」⇒ **该轴的守门力是真的**。
缺的只是**「正在执行的处理被打断」这一语义从未被验证过**。

### ③ 波及面（2026-10-25 全仓扫描）

| 文件 | 形态 | 判定 |
|---|---|---|
| `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskGrpcInterceptorWiringTest.java:173` | 裸 handler → `ServerServiceDefinition.builder(...).addMethod(m, slowHandler)` → **挂真 server** | 🔴 **空转** |
| `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowGrpcInterceptorWiringTest.java:149` | 同款（同文件、同注释、同结构） | 🔴 **空转** |
| `service/common/src/test/java/com/rag/common/protection/{GoStyleRpcProtectionChainTest, GoStyleRpcSheddingInterceptorTest, GoStyleSheddingStatTest, GoStyleRpcBreakerInterceptorTest}.java` | 裸 handler 作**拦截器链的 `next`** ⇒ 直接调 `startCall()` | ✅ **不是本形态**（拦截器单测的标准写法，无需改） |
| `service/common/src/main/java/com/rag/common/protection/GoStyleRpcProtectionChain.java:98,134,161` | **生产代码**里的 `next` 包装 | ✅ **不是本形态**（拦截器链包装，不注册进 server） |

⇒ 🔴 **实际波及 = 2 个 rpc 域各 1 条测试**（task + follow），
**不是**先前台账写的「task 域内部 ⇒ 会反噬所有 rpc 域」—— **反噬面比预估的窄，但确实是跨域的**。

### ④ 修法（下一轮落地）

| # | 动作 |
|---|---|
| ① | 把裸 `ServerCallHandler` 换成 grpc-java 的**标准派发入口** `io.grpc.stub.ServerCalls.asyncUnaryCall(...)` ⇒ `onHalfClose` 才会被派发 ⇒ 处理函数**真的执行** |
| ② | 补一条断言：「**handler 确实被调用过**」（计数器 / latch）+「服务端在 handler **执行中途**取消它」⇒ 补上现在缺失的那一维语义 |
| ③ | **变异测试**：去掉 `server.intercept(...)` ⇒ 客户端 5s deadline 生效 ⇒ `elapsed ≈ 5000ms` ⇒ **转红**（证明守门非空转） |

---

## 三、其余 7 条的状态说明（2026-10-25）

本轮**未展开**这 7 条的逐条对照（它们的落点分散在多个域的代码里，
且多数需要**真 PG / 真启动**才能判定 ⇒ 不适合与 T-10 同批做）。

| # | 为什么本轮没动 |
|---|---|
| X-1 | 需要**三个 rpc 模块同库共存**的运行时验证 ⇒ 需真 PG + 三进程并发 ⇒ 独立一轮 |
| X-2 | `user-rpc`↔`admin-rpc` 的建表责任与 client 契约 ⇒ 跨 users 域内部两个子域 ⇒ 需先读 users 域台账 |
| ~~X-3~~ | ✅ **已闭合**（2026-10-25 B2）⇒ 见 §二·七（末端 = 仓外 DC 平台；仓内两侧 4↔4 对齐） |
| X-4 | 与 `DRIFT-LIKE-003` 同一条 ⇒ 归 like 域，但需 global 协调（涉及 common 侧的 communityfact） |
| ~~X-5~~ | ✅ **已确证**（2026-10-25 第七轮，探针实测）⇒ 见 §二·八。⚠️ 机制清楚了，但**修法要同时动 task + follow 两个域的测试** ⇒ 下一轮落地 |
| X-6 | 待**用户拍板**（改 Tomcat 三旋钮 vs 改 10 个 api 的参数解析）⇒ 拍板前不动 |
| ~~X-7~~ | ✅ **已做**（2026-10-25 B3）⇒ 见 §二·五（4 个域可跑 + 产出 X-10） |

⇒ **排期建议（2026-10-25 更新）**：~~X-7~~ **已做**（§二·五）→ **X-1**（真 PG 三进程）
→ **X-2** → ~~X-3~~（**已闭合**）→ ~~X-5~~（**已确证**，§二·八）→ **X-5 修法落地**（task + follow）
→ X-4；**X-6 等用户拍板**；
~~X-10~~（**已签字认账**，真实记录 = message 域 `SD-2026-10-23-06`）。

---

## 四、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | **第七轮（X-5 确证 + X-10 去重）**：① 🔴 **X-5 由「未展开」升级为「已确证」** —— 一次性探针实测：grpc-java 1.63 **会**调裸 handler 的 `startCall()`，但**不会**把 `onHalfClose` 派发给它返回的 `ServerCall.Listener` ⇒ 「处理函数睡 10 秒」**从未发生** ⇒ task + follow 两个域的同名测试**空转**（绿，但验证的是「取消一个空调用」而非「打断在飞处理」）⇒ 波及面由「task 域」扩到「task + follow 两个 rpc 域」⇒ 见 §二·八 ② ⚠️ **X-10 去重** —— 复查发现 X-10 早在 2026-10-23 已签为 message 域 `SD-2026-10-23-06` ⇒ **撤销本域重复登记的 `SD-2026-10-25-03`**（改为指针） |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 7 条跨域不变量状态；新增 **X-8 真源分层（L1 项目层 / L2 go-zero 框架层）** —— 本轮实测发现，影响全部域的对照方法 |
| 2026-10-25 | **第四轮（B2 / X-3 闭合）**：**「第三个仓库」已定位** —— 链路末端**不是仓库**，是**仓外 DC 平台**的 `POST /v1/events`（`DC_PLATFORM_EVENT_URL` + `DC_PLATFORM_SERVICE_TOKEN`，路径必须以 `/v1/events` 结尾、token ≥ 32，见 Go `service/common/communityfact/wire.go:83-91`）。仓内两侧**都有**：`service/common/communityfact` **4 文件 ↔ 4 类**；authority 端点路径 1:1；调度形状 **1s / 16 / 10s / 单条失败即 break** 四项逐条对齐；`like` / `comment` / `favorite` 三域**都接了线**。唯一差异 = **部署形态**（Go 独立 `cmd/fact-dispatch` vs RAG 进程内 `@Scheduled`）⇒ 表达层差异，不修。⇒ **X-3 关闭**（不是漂移，是架构事实 + 仓外边界） |
| 2026-10-25 | **第三轮（B3 / X-7 部分闭合）**：① 查出**已有的域预设本身已失效**（users 容器改名、message 容器+库+用户三重失效）⇒ 修好并给脚本加按域的 `pg_user` ② **扩到 4 个域**（新增 `comment`）⇒ **可跑的域 1 → 4** ③ 跑出**真结论**：task ✅ 11 列逐字相同、message ✅ 37 列逐字相同（+ DEFAULT 6 处=已知刻意保留）、users/comment 各有**环境缺口**（Go 侧对应表未被 AutoMigrate）④ 🔴 **新增 X-10**：`message` 域索引 `idx_notification_recipient_read` **两侧列不同**（Go 单列 `is_read` / RAG 复合 `recipient_id,is_read`）⇒ 根因是 Go 侧 `priority:1` 写到了另一个索引名 ⇒ **落在 message 域的迁移脚本 ⇒ global 只记账不改别域代码，等用户拍板** ⑤ 仍缺 5 个域（article/like/hot/follow/favorite）的**双侧库环境** ⇒ 缺 runner ⇒ 登记为待办 |
| 2026-10-25 | **第二轮**：新增 **X-9** —— 「Go 是否读 body」判据存在 **3 份生产复制品**（favorite / hot / article）。T-1 修复前 RAG **内部自相矛盾**（闸门说不读 / 解析器说要读）⇒ T-1 修复后已一致。⚠️ **根治需各域自己收敛**（调用唯一真源），global 只记账不改别域代码 |
