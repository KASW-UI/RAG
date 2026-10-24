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
| X-2 | 建表责任交叉 · client 契约 | `user-rpc`↔`admin-rpc` · api↔rpc proto | ✅ **已审完（第九轮）** —— 交叉但无冲突；client 契约 10 域零漂移；🟡 **产出 1 条新真漂移**（`users_user.extra_info` jsonb→text 漏改）⇒ 见 §二·九 | ✅ **已审完** |
| X-3 | 事实投递链路 | `like`/`comment`/`favorite` 的 rpc ↔ **仓外 DC 平台** | ✅ **已闭合**（2026-10-25 B2）—— 末端不是「第三个仓库」，是仓外 `POST /v1/events`；仓内两侧 4↔4 对齐 | ✅ **已闭合** |
| X-4 | 社区消费者差异 | Go 用 `like`，RAG 不用 | ✅ **已确证（第九轮实测）** —— like 侧 RAG **另起炉灶**（0 引用共享组件）· favorite/article **RAG 多出** ⇒ 一半可修、一半被 favorite 阻塞 ⇒ 见 §二·十 | ✅ **已确证** |
| X-5 | 裸 `ServerCallHandler` 不被 dispatch | **task + follow**（2026-10-25 实测扩面） | 🔴 **已确证**（第七轮探针）—— grpc-java 不向裸 handler 派发 `onHalfClose` ⇒ 两个域的「造慢服务」测试**空转** ⇒ 见 §二·八 | ✅ **已确证**（机制 + 波及面） |
| X-6 | `DRIFT-HOTAPI-030`（Tomcat 数「总参数个数」vs Go 数「非空值个数」） | hot + **全部 10 个 api** | ✅ **第十四轮已落地**（共享层 6 个类 + 9 个域一行 yaml）⇒ 🔴 **Go 的拒绝形状按域而异**（5 域注册 `SetErrorHandler` ⇒ 200+信封；4 域未注册 ⇒ 400+原文）⇒ 共享层按 `mode` 分派、**文案由各域自己的兜底 handler 渲染** ⇒ **favorite 缺省 = off ⇒ 零触碰** ⇒ 🟢 **真容器 + 真 MVC + 变异测试** | ✅ **已修**（见 §二·十一 ⑤） |
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
| 🔴 **favorite 那份永久无法收敛** | 2026-10-25 用户重申 favorite 零权限 ⇒ **hot + article 两份**可改为调用唯一真源，**favorite 那份不能** ⇒ 收敛后仍是「共享真源 + favorite 一份复制品」⇒ **「3 份复制品」的风险在 favorite 上永久存在** ⇒ 缓解手段只剩唯一真源 javadoc 的显式点名 |
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

## 二·九、X-2 ✅ **已审完（第九轮，只读审计）** —— 建表责任交叉 **确实交叉但无冲突**；client 契约 **10 域全覆盖、零漂移**；🟡 **产出 1 条此前无人登记的真漂移**

| 组 | 结论 |
|---|---|
| **A · 建表责任交叉** | 🟡 **确实交叉，但无冲突** |
| **B · api ↔ rpc client 契约** | ✅ **10 个域全覆盖、逐方法核对、零漂移** |

### ① A 组：谁建哪些表（Go ↔ RAG）

| 表 | Go 侧谁建 | RAG 侧谁建 | 重叠 | 冲突 |
|---|---|---|---|---|
| `users_user` | **两个都建**：`user/rpc/internal/model/db.go:33` + `admin/rpc/internal/model/db.go:15` | **两个都建**：`user-rpc/.../bootstrap/UserSchemaBootstrap.java:51` + `admin-rpc/.../bootstrap/AdminSchemaBootstrap.java:55` → `users-common/.../ddl/SchemaInitializer.java:63-68` | 是 | **无** —— Go 两侧的 `User` struct 逐字段相同（`user/rpc/.../data_model.go:5-20` vs `admin/rpc/.../data_model.go:35-50`）；RAG 两侧同源同一文件 |
| `users_user_avatar_history` | 仅 user-rpc（`db.go:33`） | 两个 rpc 都建 | RAG 侧重叠 | 无（覆盖面差异 ⇒ 已登记 `DRIFT-ARPC-005`） |
| `admin_admin` / `admin_invite` | 仅 admin-rpc（`db.go:15`） | 两个 rpc 都建 | RAG 侧重叠 | 无（同上） |

- **同库事实**：RAG 两侧 `application.yml:10` 同为 `first_db`；Go 两侧同为 `${POSTGRES_DB}` ⇒ **建表责任确实交叉**。
- **并发保护**：RAG 有 `pg_advisory_lock`（`SchemaInitializer.java:91,115`）；Go 侧 GORM 裸跑 ⇒ **表达层差异（RAG 更强）**。

### ② B 组：api ↔ rpc 方法对（10 域全覆盖）

article-api 6/6 · hot-api 2 · like-api 6/6 · message-api 9/9 · comment-api 7/7 · favorite-api 7/7 ·
follow-api 8/8 · task-api 1 · user-api 9 · admin-api 13/13 ⇒ **全部有实现、全部与 Go 一致**。

- ✅ **未发现「api 调了但 rpc 没有」**，也未发现「rpc 有而 api 新增调用」的漂移。
- ℹ️ 唯一「rpc 有、api 从不调」= hot-rpc 的 `LikeArticle/CommentArticle/CoinArticle/ShareArticle`
  （`HotServiceGrpcImpl.java:70-98`）—— **Go 侧同样无调用方**（`hot_grpc.pb.go:36` 标注「预留」）⇒ **无漂移**。
- ✅ **proto 字段号无硬编码覆盖**（全仓 `setField(` / `UnknownFieldSet` 零命中）。

### ③ 🟡 本轮产出的真漂移：**`users_user.extra_info` 写路径仍按 jsonb 处理**

| | 内容 |
|---|---|
| **Go** | GORM `serializer:json` ⇒ 落库是**原始 JSON 文本**（`admin/rpc/internal/logic/updateuserlogic.go:84-87` + `admin/rpc/.../data_model.go:43`） |
| **RAG** | 写 `users_user` 时显式 `CAST(? AS jsonb)`（`admin-rpc/.../service/AdminDomainService.java:962`），实体挂 `JsonbTypeHandler`（`admin-rpc/.../domain/UserEntity.java:129`） |
| **🔴 根因（代码注释的前提已失效）** | 该列**早已是 TEXT**：`users-common/.../db/01_create_user.sql:41`（`extra_info TEXT`）+ `:103`（每次启动 `ALTER … TYPE text`），而 **admin-rpc 自己启动时就在重放这份 DDL** ⇒ 代码里「该列仍是 jsonb」的前提**不成立** |
| **可观察差异** | ① jsonb 会**规范化 JSON**（键序/空格）⇒ 落库字节与 Go 不同；② 含 `\u0000` 时 PG 报 **`22P02`** ⇒ **Go 成功 / RAG 5001**（与已修的 `DRIFT-ARPC-004①` 同型 —— `admin_admin` 已改、`users_user` **漏改**）；③ 与 RAG 自身 user-rpc 写同一列的 `JsonTextTypeHandler`（`user-rpc/.../domain/UserEntity.java:114`）**自相矛盾** |
| **登记状态** | `docs/DRIFT/users/admin/api.md:1660` 只列了「仍开放」，**归因缺失**（没落到「建表责任交叉 ⇒ DDL 归属」这条因果链上） |
| **建议修法** | 删掉 `AdminDomainService.java:962` 的 `CAST(... AS jsonb)`，改用与 `updateSelf` 同款的 `setSql("extra_info = {0}", …)`（`:357`）；`UserEntity.java:129` 换成 `JsonTextTypeHandler` |
| **可做性** | ✅ **global 现在有权限**（users 域，非 favorite）⇒ **无需拍板** |

### ④ 证据边界（**未查 / 不声称已证明**）

1. ⚠️ **未跑真 PG、未做双栈并发复跑** ⇒ `ADJ-03`（`docs/DRIFT/users/_shared.md:922-929`）要求的「user-rpc + admin-rpc 同时对空库建表」**未复现** ⇒ 并发结论**只来自静态读码**（advisory lock 存在），**不是实测**。
2. ⚠️ **jsonb→text 的实际行为本轮未实测** ⇒ 差异①②来自仓内已记载的活体证据（`03_create_admin.sql:85-93`）+ PG 语义推断 ⇒ 建议按 `UNRESOLVED-ARPC-004`（`docs/DRIFT/users/admin/rpc.md:724-736`）实跑一次 `updateUser` + `extra_info={"k":"\u0000"}`。
3. ⚠️ **字段级只抽查了 3 域**（like 7/7、hot 2 项、comment 7/7 + users/admin 的 `extra_info`）⇒ favorite / follow / task / message / article 的**请求字段未逐字段与 Go 对照**。
4. ⚠️ Go rpc **内部逻辑**（如 `UpdateOneUserByUid` 的 SQL 细节）未逐行对；`proto` 未逐字节重比（沿用 AGENTS.md §3.4 的 10/10 结论）。

---

## 二·十、X-4 ✅ **已确证（第九轮实测复核）** —— `communityfact` **消费者集合两侧不一致**；一半可修，一半被 favorite 永久阻塞

### ① 实测（`grep -rl`，两侧同口径）

| 域 | Go 侧引用 `communityfact` | RAG 侧引用 `com.rag.common.communityfact` | 差 |
|---|---|---|---|
| `like` | ✅ **6** | 🔴 **0** | 🔴 **RAG 缺** |
| `comment` | ✅ **6** | ✅ **5** | ≈ |
| `favorite` | 🔴 0 | 🔴 **10** | 🔴 **RAG 多**（⛔ favorite 不碰） |
| `article` | 🔴 0 | 🔴 1 | 🔴 **RAG 多** |
| 其余（follow/task/hot/message/users） | 0 | 0 | — |

### ② 🔴 RAG 的 `like` 域**另起炉灶**，没有复用共享组件

RAG 的 like 域**有自己的**一套：`LikeFactDispatchScheduler` / `LikeFactDispatchService` /
`LikeDomainFactOutboxMapper` / `LikeFactStreamMapper` / `LikeDomainFactOutboxEntity` / `LikeFactStreamEntity`
⇒ **零处引用 `service/common/communityfact`**；
而 Go 的 like 域用的是**共享组件** `service/common/communityfact` + `cmd/fact-dispatch` / `cmd/fact-authority` 两个进程 + `internal/model/fact_delivery.go`。

⇒ **待判定（本轮未做完）**：RAG 这套「自己实现的 outbox+stream+scheduler」与 Go 的 `communityfact` 链路**行为是否等价**？
（B2 已钉住共享组件的契约：`POST /v1/events` + 调度形状 **1s / 每轮 16 / 单次 10s / 单条失败即 break** ⇒ 应拿这四条逐条对 RAG 的 `LikeFactDispatchScheduler`。）

### ③ 🔴 我自己第四轮（B2 / X-3）的结论**有误，须订正**

⇒ B2 写的是「`like` / `comment` / `favorite` 三域**都接了线**」⇒ **实测 `like` 域是 0 引用** ⇒ **该句作废**。
⇒ 准确表述：**`comment` 与 `favorite` 接了共享组件；`like` 域没有（它自己实现了一套）；`article` 多了一处 Go 侧没有的用法。**

### ④ 处置

| 半边 | 判定 | 处置 |
|---|---|---|
| **like 缺共享组件接线** | 🟡 **可能可修**（需先做上面 ② 的等价性判定） | 下一步逐条对四条调度契约 |
| **favorite / article 多出来的用法** | 🔴 **不是漂移**（Go 侧本来就没有 ⇒ RAG 多做了功能）⇒ 属「有意偏离」还是「多余实现」**待判** | ⛔ **favorite 一律不碰** ⇒ 这半边**被 favorite 永久阻塞** |

⚠️ ⇒ **X-4 无法整体关闭**：**可修的一半（like）+ 永久阻塞的一半（favorite）** ⇒ 台账应拆成两条。

---

## 二·十一、X-6 🔴 **第十一轮已改判 ⇒ 不是 favorite 边界下的永久阻塞**（`DRIFT-HOTAPI-030`）

> ⚠️ **第九轮结论已被本轮推翻**：第九轮写「修法必然要动 `favorite-api` ⇒ 永久阻塞」——
> **那个结论只覆盖了「逐模块改 `application.yml`」这一条修法**，而**不是全部修法**。
> 本轮实测发现**共享层修法**（落在 `service/common`）⇒ **10 个 api 模块零改动 ⇒ favorite 零触碰**。
> ⇒ **教训已登记** ⇒ [`docs/Pitfalls/global/cross-domain.md`](../../../Pitfalls/global/cross-domain.md)。

### ① 两层限制的**精确语义**（第九轮实测取证）

| | Go | RAG（Tomcat） |
|---|---|---|
| **判据位置** | **应用层**：`go-zero v1.10.2/rest/httpx/util.go:15` `maxFormParamCount = 2048` | **容器层**：`server.tomcat.max-parameter-count` |
| **数什么** | 🔴 **数「非空值」**：`util.go:44-45` `if len(v) == 0 { continue }`（空值直接跳过） | 🔴 **数「参数总数」**（含空值） |
| **上限语义** | `util.go:48` `if n < maxFormParamCount` ⇒ **最多接受 2048 个非空值**（第 2049 个非空值报错） | `parameterCount >= limit` ⇒ **总数 ≥ 2048 即拦** |

### ② 残余窗口（精确化）

> **总数 ≥ 2048 且 非空值 ≤ 2048** ⇒ **Go 200 / RAG 5001**
> （需要至少「总数 − 2048」个空值才能落进窗口；例：2048 个参数全空 ⇒ Go 放行、RAG 5001）

### ③ 🔴 **「逐模块改 yaml」这条修法必然要动 `favorite-api`** —— 但**它不是唯一修法**

彻底修 = ① `max-parameter-count: -1`（关掉 Tomcat 上限）＋ ② **每个** api 模块补 Go 的应用层判据（数**非空值**、上限 2048、以及 `net/url` 的段数上限）。

⚠️ **实测确认「每个 api 模块都有自己的 Tomcat 连接器」** ⇒ 该配置是 **per-app** 的 ⇒ **无法在 `service/common` 用 yaml 统一改**：

| 域 | api 模块 | 现有配置 |
|---|---|---|
| article / comment / **favorite** / follow / hot / like / message / task | 8 个 | `max-parameter-count: 2048` |
| users（user-api + admin-api） | 2 个 | `max-parameter-count: 2048` |

⇒ **10 个 api 模块全部已配 2048，零遗漏** ⇒ **「逐模块改 yaml」这条修法必然动 `favorite-api`** ⇒ ⛔ **与「favorite 一律不碰」直接冲突**。

⚠️ **只改 9 个、留着 favorite-api 不改** ⇒ **等于把差异留在 favorite 域** ⇒ 9 个模块与 Go 一致、favorite 域不一致 ⇒ **制造新的分叉**（与 T-1「3 份生产复制品」同类问题）。

#### ③ 之二 🟢 **但存在共享层修法 ⇒ favorite 零触碰**（第十一轮新发现，本轮唯一的技术结论）

🔴 **关键前提（本轮实测，10/10 成立）**：

| 前提 | 实测证据 | 结论 |
|---|---|---|
| **10 个 api 模块都依赖 `service/common`** | 逐模块查 `pom.xml` ⇒ **10/10 ✅** | 共享层的类**在每个 api 模块的 classpath 上** |
| **10 个 api 模块都扫 `com.rag.common`** | 逐模块查 `@SpringBootApplication(scanBasePackages = …)`：9 个 = `{<域包>, "com.rag.common"}`；`task-api` = `@ComponentScan(basePackages = {"com.rag.task.task.api", "com.rag.common"})` ⇒ **10/10 ✅** | ⇒ **`service/common` 里加的 Bean / Filter 会进所有 10 个 api 上下文**（本仓**不用** `AutoConfiguration.imports`，靠**组件扫描** ⇒ 见 `GoStyleProtectionConfiguration.java:45-49`） |
| **Tomcat 允许在运行期改上限** | `Connector.setMaxParameterCount(int)`（`catalina/connector/Connector.java:538`，默认 `1000` 在 `:251`）⇒ `Request.java:148/480` 每请求从 connector 取值 | ⇒ **连接器上限可在容器启动期改掉** |
| **`-1` = 不限** | `org/apache/tomcat/util/http/Parameters.java:79` javadoc「**or -1 for no limit**」+ `:247` `if (limit > -1 && parameterCount >= limit)` | ⇒ `setMaxParameterCount(-1)` ⇒ **Tomcat 完全不限制参数个数** ⇒ 与 Go 的 `net/http`（不限制）+ go-zero 应用层 2048 判据**同构** |
| **Boot 的接线顺序可被超越** | Boot 4.0.8 `org.springframework.boot.tomcat.autoconfigure.TomcatWebServerFactoryCustomizer.getOrder()` **返回 0**（javap 实测）⇒ 它用 `ConnectorCustomizer` lambda 设 `maxParameterCount` | ⇒ 共享层用 **`WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>` + `@Order > 0`** ⇒ **在 Boot 之后执行 ⇒ 覆盖生效** |

⇒ **共享层修法（两处，全在 `service/common`）**：

1. **一个 `WebServerFactoryCustomizer`**（`@Order` > 0）⇒ `factory.addConnectorCustomizers(c -> c.setMaxParameterCount(-1))`
   ⇒ **10 个 api 模块的 Tomcat 上限统一解除**，**不动任何 `application.yml`**（含 `favorite-api`）。
2. **一个共享 Filter** 复刻 go-zero `GetFormValues` 的两层（`net/url` 解析错误 + **2048 个非空值**计数）
   ⇒ **可从 `article-api` 的 `GoStyleGetFormValuesFilter` 移植**（它已把 `net/url` 段数上限 10000 / URL escape 校验 / 2048 非空值计数三件事做全了）
   ⇒ 放在 `service/common` ⇒ **10 个 api 模块自动生效**。

⚠️ **已有实现分布（实测，影响工作量估算）**：

| api 模块 | 是否已有 Go 等价的应用层判据 | 位置 |
|---|---|---|
| `article-api` | ✅ 有（**含 `net/url` 解析层**） | `article-api/.../config/GoStyleGetFormValuesFilter.java` |
| `hot-api` | ✅ 有 | `hot-api/.../form/GoFormValues.java` |
| `favorite-api` | ✅ 有（**只含 2048 计数**，不含 `net/url` 解析层） | `favorite-api/.../service/FavoriteApiSupport.java:327-358` |
| 其余 7 个 | ❌ **没有** | like / message / comment / follow / task / user-api / admin-api |

⇒ ⇒ **X-6 的修法 = 共享层 2 处 ⇒ 10 个 api 模块零代码改动 ⇒ favorite 零触碰 ⇒ 不制造分叉**
⇒ **证据强度**：🟡 **源码级 + 配置级已逐条取证**（Tomcat 源码 / Boot 字节码 / 10 个模块的 pom 与扫描配置 / 3 个已有实现的分布）
⇒ ⚠️ **尚未做真容器实测**（§3.3：「配置/源码说得通」≠「框架真的采用了它」）⇒ **落地时必须配真 Tomcat 守门 + 变异测试**。

### ④ ⇒ 结论：**可修（共享层）⇒ 不必动 favorite ⇒ 也不是「待拍板的技术项」**

> 🔴 **2026-10-25 用户重申写权边界**：「**你没有 favorite 的所有权限**」⇒ global 对 `favorite` 域**零改动**（代码 / 配置 / 台账一律不碰）。
> ⇒ **出路 A 随之撤下** —— 它不再是「等你点头」的选项，而是**边界之外**。
> ⇒ **但边界本身不构成阻塞** —— 因为存在**共享层修法**（③ 之二）⇒ **不需要用户拍板**。

| 出路 | 内容 | 状态 |
|---|---|---|
| ~~A~~ | 放开 `favorite-api` 的写权 ⇒ 10 个模块一起改 yaml | ❌ **已撤下**（用户重申 favorite 零权限 ⇒ 边界之外，不作为选项） |
| ~~B~~ | 9 个模块改、favorite 不改 | ❌ **已放弃** —— 等于把差异永久留在 favorite 域 ⇒ **制造新的分叉**（与 T-1「3 份生产复制品」同类问题） |
| ~~C~~ | 保持现状 + 登记待签字候选 | ❌ **已放弃** —— **本轮找到共享层修法 ⇒ 问题可修 ⇒ 不该「认账不改」** ⇒ 候选 `SD-2026-10-25-CAND-13` **未登记**（编号已回收，勿引用） |
| **D** | ✅ **共享层修法**：`service/common` 里 2 处（① Tomcat 上限解除 ② 共享 Filter 复刻 Go 判据）⇒ **10 个 api 模块零代码改动 ⇒ favorite 零触碰** | ✅ **本轮定案** |

⇒ ⇒ **`DRIFT-HOTAPI-030` 不是 favorite 边界下的永久阻塞**，**也不需要签字认账** ⇒ 它是一条**待修的真实漂移**，修法落在 `service/common`（global 单写权范围内）⇒ **下一轮落地**。

⚠️ **落地前必须先答的 3 个问题**（本轮已能预判，但留给落地轮实测）：

| # | 问题 | 预判 | 落地时的验收门槛（§3.3） |
|---|---|---|---|
| 1 | **yaml 声明值 ≠ 运行时值**：10 个 api 模块的 `application.yml` 里仍是 `max-parameter-count: 2048`（**favorite-api 不能碰 ⇒ 改不了**），而共享层在运行期把连接器上限设成 `-1` | 🟡 **这是边界内唯一可行的形态**（另一条路 = 改 10 份 yaml ⇒ 必动 favorite ⇒ 被边界排除）<br>⇒ 代价：**yaml 变成「名义值」** ⇒ 必须在共享层 javadoc + `check-tomcat-limits.py` 里写清楚 | ⚠️ **`tools/difftest/check-tomcat-limits.py` 的期望要跟着改**（它现在断言 yaml == 2048 ⇒ 共享层接管后这条断言的**语义**变了）⇒ **该脚本属 global 可写**（`tools/`）⇒ **同一次提交** |
| 2 | **共享 Filter 与 3 个模块已有的本地实现重复**：article / hot / favorite 本地已有 Go 等价判据 | 🟡 共享 Filter **先执行** ⇒ 「>2048 非空值」这条路径上**本地实现变不可达** ⇒ 响应须**逐字节一致**（都是 Go 的 `too many form values, error: <encoded>` + 400） | 真容器对照：**共享 Filter 的 400 body 与 article-api 现有实现的 400 body 逐字节相同** |
| 3 | **共享 Filter 会改变 favorite 的可观察行为**：favorite 本地实现抛 `IllegalArgumentException` ⇒ 落 **5003**；共享层会把它变成 Go 的 **400** | 🟡 **这是向 Go 靠拢的修正**（Go = 400）⇒ **不是回归** | ⚠️ **必须记进台账并告知 favorite 域 owner**（global 不碰它的代码，但共享层会改它的行为 ⇒ 不能静默） |

### ④ 之二 🔴 **favorite 边界的连带影响面**（本轮一并复核，避免留下「半修」）

| 条目 | 修法是否要动 favorite | 处置 |
|---|---|---|
| **X-4**（社区消费者差异） | 🔴 **要** —— favorite 侧 RAG 多出 10 处 Go 没有的 `communityfact` 用法，删掉它们 = 改 favorite | ✅ 已在 §二·十 记为「**可修的一半（like）+ 永久阻塞的一半（favorite）**」⇒ 边界确认后**维持原判** |
| **X-6**（本条 HOTAPI-030） | 🟢 **不用** —— `max-parameter-count` 虽是 per-app，但**上限可在运行期由共享层改**（`Connector.setMaxParameterCount`，`Connector.java:538`）⇒ 共享层 2 处 ⇒ **favorite 零触碰** | ✅ 本轮已改判为「**可修（共享层）**」⇒ 落 D 出路 ⇒ 下一轮落地（**非永久阻塞、非签字项**） |
| **X-9**（3 份「Go 是否读 body」复制品） | 🟡 **部分要** —— 3 份分别在 favorite / hot / article；**hot + article 两份可收敛到唯一真源**，favorite 那份不能 | ✅ **本轮不动**（现状三份都取原始头值 ⇒ 行为正确）⇒ 但「3 份复制品」的风险**在 favorite 那份上永久存在** ⇒ 已在本节 §2.3 补记该限制 |
| **X-7**（DDL 双侧活体对照） | ❌ **不要** —— 只读库、不改 favorite 代码 | ✅ **不受影响** —— favorite 域缺的是**双侧库环境**（runner），不是写权 ⇒ **仍可推进**（见 §二·五 ③） |

### ⑤ ✅ **第十四轮已落地**（2026-10-25）—— 🔴 且**推翻了第十一轮修法草案的一个前提**

> 🔴 **第十四轮最重要的发现**：第十三轮的修法草案假设「共享 Filter 统一写 Go 的 **400 原文**」。
> **那个假设是错的** —— **Go 的拒绝形状是「按域而异」的**。

#### ⑤ 之一 🔴 **Go 的拒绝形状按域而异**（逐域读 Go 源码取证）

Go 的 `httpx.Parse` 失败后经 `httpx.ErrorCtx` → `httpx.SetErrorHandler` 出口（go-zero `rest/httpx/responses.go:141-172`）：

| Go 侧是否注册 `SetErrorHandler` | 域（Go 侧出处） | wire 形状 |
|---|---|---|
| ✅ **注册了** | `hot/api/hotcenter.go:53` · `follow/api/followcenter.go:53` · `like/api/likecenter.go:48` · `message/api/messagecenter.go:53` · `comment/api/commentservice.go:47` | **HTTP 200** + `{"code":5001,"msg":"<该域自己的文案>","data":null}` |
| ❌ **没注册** | `article/api` · `user/api` · `task/api` · `favorite/api` | **HTTP 400** + `text/plain; charset=utf-8` + `nosniff` + Go 原文 |

⚠️ **各域 5001 的文案还不一样**（Go 侧逐域取证）：

| 域 | `GetErrMsg(5001)` |
|---|---|
| hot / follow / message | `"internal server error"` |
| like / comment | `"系统内部错误"` |

⇒ ⇒ **共享层写不出信封** ⇒ 拒绝形状**必须由域自己渲染** ⇒ ⇒ **共享层不能有单一形状**。

#### ⑤ 之二 落地的结构（`service/common` 6 个类 + **9 个域各一行 yaml**）

| 件 | 作用 |
|---|---|
| `GoStyleFormValues` | **Go 判据**：`net/url` 三条错误（段数上限 / 分号 / 转义）+ **2048 个非空值**计数 + Go 原文。**只出判据，不出响应** |
| `GoStyleFormLimitMode` | 模式：`off` / `raw` / `envelope`；**非法值在构造期就炸**（不静默兜底） |
| `GoStyleFormLimitExceededException` | envelope 模式下抛出；**刻意不继承 `BusinessException`**（否则各域的 `@ExceptionHandler(BusinessException.class)` 会拿 `getMessage()` 当信封 msg ⇒ 把 Go 原文塞进 wire） |
| `GoStyleFormValueLimitInterceptor` | 闸门本体。**是 `HandlerInterceptor` 不是 servlet `Filter`** —— Filter 在 DispatcherServlet **之外** ⇒ 抛的异常到不了 `@RestControllerAdvice` |
| `GoStyleFormLimitWebMvcConfig` | 注册拦截器（`order = HIGHEST_PRECEDENCE`） |
| `GoStyleTomcatParameterLimitCustomizer` | 容器上限 → **10001**（= Go `net/url` 段数上限 + 1）；**仅非 `off` 模式生效** |

⇒ **每个域只需一行 yaml**：`rag.http.go-form-limit.mode: raw | envelope`

| 模式 | 域 | 形状 |
|---|---|---|
| `raw` | article / user-api / admin-api / task | 400 + Go 原文（`http.Error` 形状） |
| `envelope` | hot / like / follow / message / comment | 200 + 该域自己的信封（**共享层只抛异常，由域的兜底 handler 查自己的表渲染**） |
| **缺省（off）** | **favorite** | **零触碰** —— 容器上限也不动、闸门不装 ⇒ 行为逐字节不变 |

⚠️ **为什么 favorite 连容器上限都不动**：第十三轮担心「抬高上限会改 favorite 的行为」⇒ ⇒ **把「抬上限」也挂在同一个模式开关下** ⇒ `off` ⇒ `GoStyleTomcatParameterLimitCustomizer` 直接 `return` ⇒ **favorite 的 Tomcat 上限仍是 yaml 里的 2048** ⇒ **favorite 零改动、零行为变化、无需通知其 owner**。

#### ⑤ 之三 实测依据（§3.3 真容器 / 真 Spring MVC）

| 守门 | 覆盖 | 结果 |
|---|---|---|
| `GoStyleParamLimitGateTest`（**真 Tomcat + 真 Spring MVC 拦截器链**，raw 模式） | 运行期容器上限 = 10001 · 2048 放行 · 2049 ⇒ 400 + Go 原文逐字 · **3000 个空值 ⇒ 200（X-6 的窗口）** · 10001 段 ⇒ 400 + 段数文案 | ✅ **5/5** |
| `GoStyleFormLimitEnvelopeGateTest`（**真 Tomcat + 真 MVC + 真 advice**，envelope 模式） | 2049 ⇒ **200 + 业务信封**（`code=5001` / msg 由**测试替身的域**提供 / `data:null`）· 2048 放行 · 3000 空值放行 · 容器上限 10001 | ✅ **4/4** |
| `GoStyleFormLimitModeTest`（模式分派单测） | `off` 放行且不写响应 · `raw` 写 400 + Go 原文 · `envelope` 抛异常且**不写**响应 · 非法值构造期炸 | ✅ **8/8** |
| **变异测试** | M1 `MAX_PARAMETER_COUNT` 10001→2048 ⇒ **3000 空值那条转红**；M2 非空值计数恒 0 ⇒ **2049 那条转红** | ✅ **两条机制都有判别力** |
| `tools/difftest/check-tomcat-limits.py` | 新增第四旋钮「form-gate 模式」+ **favorite 无 mode 的零触碰守卫** | ✅ **EXIT=0** |

⇒ **证据强度**：🟢 **真容器 + 真 MVC + 真 advice + 变异测试**（本条首次达到该强度；此前 X-6 只有源码级/配置级取证）。

#### ⑤ 之四 连带影响（各域可观察行为）

| 域 | 2049 个非空值：修前 | 修后 | 性质 |
|---|---|---|---|
| article | 400 + Go 原文（本地 filter） | **400 + Go 原文**（共享层 raw） | ✅ **不变** |
| hot | 200 + `{code:5001}`（本地判据） | **200 + `{code:5001}`**（共享层 envelope ⇒ hot 自己的兜底 handler 渲染 ⇒ 文案取 hot 的表） | ✅ **不变** |
| like / follow / message / comment / task / user / admin | 400 + **HTML 错误页**（Tomcat 先拒） | **Go 的形状**（200+信封 或 400+原文） | ✅ **向 Go 靠拢**（修漂移） |
| **favorite** | 400 + HTML（Tomcat 在 2048 先拒） | **400 + HTML（完全不变）** | ✅ **零变化**（`off` 模式） |

#### ⑤ 之五 ⚠️ 残留（有界，本轮刻意不改）

1. **鉴权与闸门的先后**：Go 是「**先鉴权**（`rest.WithJwt` 是中间件）**后解析**（`httpx.Parse` 在 handler 内）」；
   RAG 的拦截器 `order = HIGHEST_PRECEDENCE` ⇒ 「**先解析后鉴权**」
   ⇒ 「既无合法 token、又带 2049 个参数」的请求：**Go 401 / RAG 400 或 5001**。
   ⚠️ 这是**修之前就存在**的差异（`article-api` 的同名 filter 一直跑在鉴权之前）⇒ 本轮**刻意不改**（避免扩大 scope）⇒ **记为有界残留**，不是新引入。
2. **未匹配到路由的路径不触发闸门**（Interceptor 只对匹配到的 handler 生效）⇒ **与 Go 一致**（Go 的 404 分支在 `patrouter.go:73` 直接返回，**不解析 form**）⇒ 这条是**修正**，不是残留。

#### ⑤ 之六 对第十三轮「落地前必须先答的 3 个问题」的最终回答

| # | 第十三轮的预判 | 第十四轮实测结论 |
|---|---|---|
| 1 | yaml 声明值 ≠ 运行时值 ⇒ yaml 成「名义值」 | 🔴 **预判已过时**：最终形态是 **yaml 新增一行 `mode`**（10 份 yaml 里的 `max-parameter-count: 2048` **一个字没动**）⇒ **yaml 仍是名义值**（运行期 10001，由 `check-tomcat-limits.py` 断言「yaml 统一 2048 + mode 齐备」）；favorite 那份 yaml **完全没动** |
| 2 | 共享 Filter 与 3 个模块的本地实现重复 | ✅ **已消解**（不是「重复」而是「**分工**」）：共享层出**判据**与**容器上限**，本地实现出**域特有的形状**。实测 article / hot 的可观察行为**逐字节不变** |
| 3 | 共享 Filter 会改 favorite 的可观察行为 ⇒ 须记账并告知 owner | ✅ **已消解**：模式开关把 favorite 置于 `off` ⇒ **容器上限与闸门都不生效** ⇒ **favorite 零变化、无需通知** |

---

## 三、其余 7 条的状态说明（2026-10-25）

本轮**未展开**这 7 条的逐条对照（它们的落点分散在多个域的代码里，
且多数需要**真 PG / 真启动**才能判定 ⇒ 不适合与 T-10 同批做）。

| # | 为什么本轮没动 |
|---|---|
| X-1 | 需要**三个 rpc 模块同库共存**的运行时验证 ⇒ 需真 PG + 三进程并发 ⇒ 独立一轮 |
| ~~X-2~~ | ✅ **已审完**（第九轮）⇒ 见 §二·九 |
| ~~X-3~~ | ✅ **已闭合**（2026-10-25 B2）⇒ 见 §二·七（末端 = 仓外 DC 平台；仓内两侧 4↔4 对齐） |
| ~~X-4~~ | ✅ **已确证**（第九轮）⇒ 见 §二·十（**可修的一半** = like 侧接线；**阻塞的一半** = favorite） |
| ~~X-5~~ | ✅ **已确证**（2026-10-25 第七轮，探针实测）⇒ 见 §二·八。⚠️ 机制清楚了，但**修法要同时动 task + follow 两个域的测试** ⇒ 下一轮落地 |
| ~~X-6~~ | ✅ **已展开 + 已改判 + 已落地**（第九轮展开 / 第十一轮改判 / **第十四轮落地**）⇒ 见 §二·十一（**不是 favorite 边界下的永久阻塞** —— 共享层修法存在 ⇒ **可修**、**不需签字**；🔴 第十四轮补：**Go 的拒绝形状按域而异** ⇒ 共享层按 `mode` 分派 ⇒ **favorite 缺省 off ⇒ 零触碰**） |
| ~~X-7~~ | ✅ **已做**（2026-10-25 B3）⇒ 见 §二·五（4 个域可跑 + 产出 X-10） |

⇒ **排期建议（2026-10-25 更新）**：~~X-7~~ **已做**（§二·五）→ **X-1**（真 PG 三进程）
→ **X-2** → ~~X-3~~（**已闭合**）→ ~~X-5~~（**已确证**，§二·八）→ **X-5 修法落地**（task + follow）
→ **X-4**（like 半边可修 / favorite 半边**仍阻塞** —— 必须删 favorite 自己的 10 处调用）；
→ **X-6 修法落地**（**共享层 2 处**：`service/common` 的 Tomcat 上限解除 + 共享 Filter 复刻 Go 判据 ⇒ **favorite 零触碰**）；
~~X-10~~（**已签字认账**，真实记录 = message 域 `SD-2026-10-23-06`）。

⚠️ **本表已无「等用户拍板」的技术项**（2026-10-25 第十一轮复核）——
剩余待决项全部是**登记类**（候选签字等签），按用户既定偏好**不升级为拍板**：
`SD-2026-10-25-CAND-10`（HOTAPI-031）· users 域 8 处 DEFAULT 补签字 ·
`DRIFT-COMMON-005`（可拆到只拍「毫秒 vs 纳秒」差异②）
⇒ 唯一真决策项 = **T-6**（**需先撕 hot 域 `SD-HOT-API-006`**）。

---

## 四、变更记录

| 日期 | 动作 |
|---|---|
| 2026-10-25 | ✅ **第十四轮（X-6 落地）** —— ① 🔴 **推翻第十三轮修法草案的一个隐含前提**：草案假设「共享 Filter 统一写 Go 的 400 原文」⇒ **错** —— **Go 的拒绝形状按域而异**（5 域注册 `httpx.SetErrorHandler` ⇒ **200 + 业务信封**；article/user/task/favorite 未注册 ⇒ **400 + Go 原文**）⇒ 且各域 5001 **文案不同**（like/comment =「系统内部错误」，hot/follow/message = `"internal server error"`）⇒ ⇒ **共享层写不出信封** ⇒ 拒绝形状必须**由域自己渲染** ② 落地结构 = `service/common` **6 个类**（`GoStyleFormValues` 判据 / `GoStyleFormLimitMode` 模式 / `GoStyleFormLimitExceededException` / `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）+ **9 个域各一行 yaml**（`rag.http.go-form-limit.mode: raw\|envelope`）⇒ **favorite 缺省 = off ⇒ 容器上限与闸门都不生效 ⇒ 零触碰、行为逐字节不变** ③ 🔴 **闸门是 `HandlerInterceptor` 不是 servlet `Filter`** —— Filter 在 DispatcherServlet **之外** ⇒ 抛的异常到不了 `@RestControllerAdvice` ⇒ envelope 模式无法渲染 ④ 顺带订正 `service/common/pom.xml` 的 **spring-web / spring-test 硬编码 7.0.0**（与 starter-web 的 7.0.9 错配 ⇒ `NoClassDefFoundError: ApiVersionHolder`）⇒ 改为跟随 BOM ⑤ 🟢 证据强度首次达 **真容器 + 真 Spring MVC + 真 advice + 变异测试**（raw 5/5 · envelope 4/4 · 模式 8/8 · 变异 M1/M2 均转红）· `check-tomcat-limits.py` 新增第四旋钮 + **favorite 零触碰守卫** ⇒ EXIT=0 ⑥ 残留（有界，修前已存在）：**鉴权与闸门的先后**（Go 先鉴权后解析 / RAG 先解析后鉴权）⇒ 记录不改 |
| 2026-10-25 | ✅ **第十一轮（favorite 零权限重申 ⇒ 推翻第九轮的「永久阻塞」结论）** —— 用户重申：「**你没有 favorite 的所有权限**」⇒ ① 🔴 **推翻自己第九轮的结论** —— 那个结论只覆盖「逐模块改 `application.yml`」这一条修法；本轮实测**共享层修法存在**：① `service/common` 里一个 `WebServerFactoryCustomizer`（`@Order` > Boot 的 `TomcatWebServerFactoryCustomizer.getOrder()==0`）⇒ `connector.setMaxParameterCount(-1)`（`Connector.java:538`；`-1` = 不限 ⇒ `Parameters.java:79/247`）② 一个共享 Filter 复刻 go-zero `GetFormValues`（`net/url` 解析层 + **2048 个非空值**计数，可从 `article-api` 的 `GoStyleGetFormValuesFilter` 移植）⇒ **10 个 api 模块零代码改动 ⇒ favorite 零触碰**（前提已逐条实测：10/10 依赖 `service/common` + 10/10 扫 `com.rag.common`）⇒ ⇒ **X-6 不是永久阻塞，也不需签字认账** ⇒ 落 **出路 D** ② ✅ **连带影响面复核**（§二·十一 ④ 之二）—— X-4（favorite 半边）**仍阻塞**（必须删 favorite 自己的 10 处调用）· X-9（hot/article 两份可收敛、**favorite 那份永久留作复制品**）· X-7（**只读库不改代码 ⇒ 不受影响，仍可推进**）③ 本表**已无「等用户拍板」的技术项**；唯一真决策项 = T-6（需先撕 hot 域 `SD-HOT-API-006`） |
| 2026-10-25 | ✅ **第九轮补（X-6 global 侧展开）** —— 两层限制语义精确化：Go **数非空值**（`httpx/util.go:15` `maxFormParamCount=2048` + `:44-45` 跳过空值 + `:48` `n < 2048` ⇒ 最多 2048 个非空值）vs Tomcat **数参数总数** ⇒ **残余窗口 = 总数 ≥ 2048 且 非空值 ≤ 2048**。🔴 **修法必然要动 `favorite-api`**（`max-parameter-count` 是 **per-app** 连接器设置，**无法在 `service/common` 统一改**；实测 **10 个 api 模块全部已配 2048、零遗漏**）⇒ ⇒ **X-6 不是「待拍板的技术项」，而是「favorite 写权边界」问题** ⇒ 登记等用户定（出路 A 放开 favorite / B 制造分叉 / C 签字认账）<br>⚠️ **2026-10-25 第十一轮已改判** —— 出路 A 撤下、出路 B 放弃、**出路 C（签字认账）也放弃** ⇒ 改判为「**存在共享层修法 ⇒ 可修，favorite 零触碰**」（见上一行）⇒ ⇒ **第九轮那句「修法必然要动 `favorite-api` ⇒ 永久阻塞」作废**
| 2026-10-25 | ✅ **第九轮（X-2 审完 + X-4 确证）** —— ① **X-2**（建表责任交叉 + client 契约）**从「均未审」变成「已审完」**：A 组交叉但无冲突（Go 两侧 `User` struct 逐字段相同）；B 组 **10 域全覆盖零漂移**；🟡 **产出 1 条此前无人登记的真漂移** —— `users_user.extra_info` 写路径仍按 jsonb（`AdminDomainService.java:962` + `UserEntity.java:129`），但该列**早已是 TEXT**（`01_create_user.sql:41` + `:103`，且 admin-rpc 自己就在重放这份 DDL）⇒ **代码注释的前提已失效** ⇒ 含 `\u0000` 时 Go 成功 / RAG 5001 ② **X-4 实测确证**：`like` 域 Go 侧 6 个文件引用 `communityfact`、RAG 侧 **0**（**另起炉灶**）；`favorite` Go 0 / RAG 10（⛔ 不碰）③ ⚠️ **订正我自己第四轮 B2 的错误结论**（「like/comment/favorite 三域都接了线」**作废**）
| 2026-10-25 | **第七轮（X-5 确证 + X-10 去重）**：① 🔴 **X-5 由「未展开」升级为「已确证」** —— 一次性探针实测：grpc-java 1.63 **会**调裸 handler 的 `startCall()`，但**不会**把 `onHalfClose` 派发给它返回的 `ServerCall.Listener` ⇒ 「处理函数睡 10 秒」**从未发生** ⇒ task + follow 两个域的同名测试**空转**（绿，但验证的是「取消一个空调用」而非「打断在飞处理」）⇒ 波及面由「task 域」扩到「task + follow 两个 rpc 域」⇒ 见 §二·八 ② ⚠️ **X-10 去重** —— 复查发现 X-10 早在 2026-10-23 已签为 message 域 `SD-2026-10-23-06` ⇒ **撤销本域重复登记的 `SD-2026-10-25-03`**（改为指针） |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 7 条跨域不变量状态；新增 **X-8 真源分层（L1 项目层 / L2 go-zero 框架层）** —— 本轮实测发现，影响全部域的对照方法 |
| 2026-10-25 | **第四轮（B2 / X-3 闭合）**：**「第三个仓库」已定位** —— 链路末端**不是仓库**，是**仓外 DC 平台**的 `POST /v1/events`（`DC_PLATFORM_EVENT_URL` + `DC_PLATFORM_SERVICE_TOKEN`，路径必须以 `/v1/events` 结尾、token ≥ 32，见 Go `service/common/communityfact/wire.go:83-91`）。仓内两侧**都有**：`service/common/communityfact` **4 文件 ↔ 4 类**；authority 端点路径 1:1；调度形状 **1s / 16 / 10s / 单条失败即 break** 四项逐条对齐；`like` / `comment` / `favorite` 三域**都接了线**。⚠️ **2026-10-25 第九轮订正**：本条末句「`like` / `comment` / `favorite` 三域**都接了线**」⇒ **实测不成立** —— `like` 域 RAG 侧对 `com.rag.common.communityfact` 是 **0 引用**（它**另起炉灶**实现了自己的 outbox+stream+scheduler）⇒ 准确表述：**`comment` 与 `favorite` 接了共享组件；`like` 域没有；`article` 多了一处 Go 侧没有的用法** ⇒ 详见 §二·十（X-4）。⇒ ⚠️ **X-3 本身（末端 = 仓外 DC 平台 / 共享组件契约）仍成立** —— 订正的只是「哪些域接了线」这一句。 唯一差异 = **部署形态**（Go 独立 `cmd/fact-dispatch` vs RAG 进程内 `@Scheduled`）⇒ 表达层差异，不修。⇒ **X-3 关闭**（不是漂移，是架构事实 + 仓外边界） |
| 2026-10-25 | **第三轮（B3 / X-7 部分闭合）**：① 查出**已有的域预设本身已失效**（users 容器改名、message 容器+库+用户三重失效）⇒ 修好并给脚本加按域的 `pg_user` ② **扩到 4 个域**（新增 `comment`）⇒ **可跑的域 1 → 4** ③ 跑出**真结论**：task ✅ 11 列逐字相同、message ✅ 37 列逐字相同（+ DEFAULT 6 处=已知刻意保留）、users/comment 各有**环境缺口**（Go 侧对应表未被 AutoMigrate）④ 🔴 **新增 X-10**：`message` 域索引 `idx_notification_recipient_read` **两侧列不同**（Go 单列 `is_read` / RAG 复合 `recipient_id,is_read`）⇒ 根因是 Go 侧 `priority:1` 写到了另一个索引名 ⇒ **落在 message 域的迁移脚本 ⇒ global 只记账不改别域代码，等用户拍板** ⑤ 仍缺 5 个域（article/like/hot/follow/favorite）的**双侧库环境** ⇒ 缺 runner ⇒ 登记为待办 |
| 2026-10-25 | **第二轮**：新增 **X-9** —— 「Go 是否读 body」判据存在 **3 份生产复制品**（favorite / hot / article）。T-1 修复前 RAG **内部自相矛盾**（闸门说不读 / 解析器说要读）⇒ T-1 修复后已一致。⚠️ **根治需各域自己收敛**（调用唯一真源），global 只记账不改别域代码 |
