<!-- @new 2026-10-25 · global 收口域 · 由 `align/global` 工作树产出 + 基线 commit 58e8314 -->
<!-- 核对日期：2026-10-25（第十八轮）· 本轮修了 **users 域真漂移 B10**：`users_user.extra_info`
     写路径 jsonb → text（真 PG 坐实「Go 成功 / RAG 5001」）⇒ 落点跨 `users-common` + `admin-rpc`
     ⇒ **同一次提交** · 详见 §二·十四 -->
<!-- ⚠️ 第十八轮起，**SQLSTATE 22P05（不是 22P02）** 是本档的必读订正（§二·十四 ②） -->
<!-- ⚠️ 第十六轮起，本档的「端到端 vs 解析层」区分是必读项（见 §二·十三 ③ 与 PIT-GLOBAL-015） -->
<!-- ⚠️ 第十七轮起，**变异脚本的「假绿」判据**是必读项（见 PIT-GLOBAL-018）：编译不过 ⇒ 测试没跑 ⇒ 旧 XML ⇒ 假绿 -->

# global · 跨域不变量台账（cross-domain）

> **归属**：`global` 收口域的「cross-domain」议题档（§5.3b）。
> **对象**：**涉及 3+ 个域、无法归给任何单一域**的不变量。
> ⚠️ **边界**：单域内部的漂移记在该域自己的台账；**跨域不变量**才落这里。

---

## 〇、状态总表（2026-10-25 实测）

| # | 不变量 | 涉及组件 | 状态（2026-10-25） | 本轮 |
|---|---|---|---|---|
| X-1 | 共享 Flyway 账本 | `message-rpc`↔`like-rpc`↔`article-rpc` | ✅ **第十五轮**：🔴 **7 个 Flyway rpc 模块全连同一个库 ⇒ 同库共存是常态** ⇒ 查出 **comment/follow/like 三个域缺 `spring.flyway.baseline-version: 0`** ⇒ 非空 schema 下 **V1 被整条跳过 ⇒ 首张表永远建不出来** ⇒ ✅ **三域已修 + 各配真 PG 守门**；⛔ **`favorite-rpc` 同款缺陷零触碰 ⇒ 记账 + 告知该域 owner**；✅ **「三进程并发」运行时形态已验（第二十四轮 L4 · 真 PG 16 三进程并发）⇒ 六域可安全并发同库** ⇒ 仅 `favorite-rpc` 例外（**起得来、首次查库才炸**） | ✅ **已闭环**（配置层 + 运行时；`favorite-rpc` 例外只记账） |
| X-2 | 建表责任交叉 · client 契约 | `user-rpc`↔`admin-rpc` · api↔rpc proto | ✅ **已审完（第九轮）** —— 交叉但无冲突；client 契约 10 域零漂移；🟡 **产出 1 条新真漂移**（`users_user.extra_info` jsonb→text 漏改）⇒ 见 §二·九 | ✅ **已审完** |
| X-3 | 事实投递链路 | `like`/`comment`/`favorite` 的 rpc ↔ **仓外 DC 平台** | ✅ **已闭合**（2026-10-25 B2）—— 末端不是「第三个仓库」，是仓外 `POST /v1/events`；仓内两侧 4↔4 对齐 | ✅ **已闭合** |
| X-4 | 社区消费者差异 | Go 用 `like`，RAG 不用 | ✅ **已确证（第九轮实测）** —— like 侧 RAG **另起炉灶**（0 引用共享组件）· favorite/article **RAG 多出** ⇒ 一半可修、一半被 favorite 阻塞 ⇒ 见 §二·十 | ✅ **已确证** |
| X-5 | 裸 `ServerCallHandler` 不被 dispatch | **task + follow**（2026-10-25 实测扩面） | ✅ **第十七轮已落地** —— grpc-java 不向裸 handler 派发 `onHalfClose` ⇒ 两个域的「造慢服务」测试**曾是空转的** ⇒ ✅ **两域已换 `ServerCalls.asyncUnaryCall` + 补「handler 真被调用过」与「取消时仍在跑」两条断言** ⇒ **M1/M2/M3 三条变异全部转红** ⇒ 🟢 **生产代码零改动**（超时机制本来就是真的，缺的是「在飞处理被打断」这一维从未被验证）⇒ 见 §二·八 | ✅ **已修**（见 §二·八 ⑤） |
| X-6 | `DRIFT-HOTAPI-030`（Tomcat 数「总参数个数」vs Go 数「非空值个数」） | hot + **全部 10 个 api** | ✅ **第十四轮已落地**（共享层 6 个类 + 9 个域一行 yaml）⇒ 🔴 **Go 的拒绝形状按域而异**（5 域注册 `SetErrorHandler` ⇒ 200+信封；4 域未注册 ⇒ 400+原文）⇒ 共享层按 `mode` 分派、**文案由各域自己的兜底 handler 渲染** ⇒ **favorite 缺省 = off ⇒ 零触碰** ⇒ 🟢 **真容器 + 真 MVC + 变异测试** | ✅ **已修**（见 §二·十一 ⑤） |
| X-7 | DDL 双侧活体逐列对照 | `like` 域 + 其余 5 域 | 🟡 **部分闭合**（2026-10-25 B3）—— 4 个域已能活体逐列对照；其余 5 域**缺双侧库环境** | ✅ **已跑**（产出真结论 + 1 条新差异 X-10） |
| **X-8** | 🆕 **真源分层（Go 出处一半在框架里）** | **全部域** | ✅ **本轮已查明并记账** | ✅ **本轮新增** |
| **X-9** | 🆕 **「Go 是否读 body」判据有 3 份生产复制品** | favorite / hot / article | ✅ **第十六轮已收敛到只剩 1 份**（favorite）—— hot / article 两份已改为**调用**共享层唯一真源（判据 + 取输入一起收口）⇒ 🔴 **探针实测发现解析层还缺 Go 的第二道闸 `maxBodyLen = 8 MiB`**（RAG 整个读进堆里）⇒ ✅ 已在共享层 + hot 域补上 ⇒ ⚠️ **端到端今天不可达**（Go 的 MaxBytes 默认 1 MiB 全局生效 ⇒ 先拒成 413）⇒ ⛔ **favorite 那份永久残留**（零权限） | ✅ **已修**（见 §二·十三） |

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
⇒ 已登记为踩坑 ⇒ [`docs/Pitfalls/global/common-tails.md`](../../Pitfalls/global/common-tails.md)。

### 1.4 ⚠️ **给其它域的提醒（不属 global 的写权范围，只记账）**

各域台账里的 Go 出处若写成「Go 侧 `service/common/xxx`」，**需要各自复核**是否其实是 L2（go-zero）。
⇒ **本轮不代其它域改**（§5.3b 规则：只写自己域）⇒ 建议下一轮开「全仓 Go 出处 L1/L2 归属普查」。

---

## 二、X-9 ✅ **第十六轮已收敛到只剩 1 份** —— 「Go 是否读 body」判据的生产复制品（2026-10-25 新发现 → 同日收敛）

### 2.1 事实

`service/common/http/GoStyleJsonBodyConverter#goReadsBody` 的 javadoc 自称是
「Go 何时读 body」这条规则的**唯一真源**，并明确警告「复制品必然与实现漂移」。
⇒ **但仓库里有 3 份生产代码各自抄了一份判据**：

| # | 位置 | 抄法 | 取的头值 | 第十六轮后 |
|---|---|---|---|---|
| 1 | `service/favorite/favorite-api/.../controller/FavoriteController.java:146` | `contentType.contains("application/json")` | `httpRequest.getHeader("Content-Type")` ⇒ **原始** ✔ | ⛔ **永久残留**（favorite 零权限） |
| 2 | `service/hot/hot-api/.../form/GoFormValues.java` | 同上 | `request.getContentType()` ⇒ **原始** ✔ | ✅ **已改为调用唯一真源** |
| 3 | `service/article/article-api/.../config/GoStyleJsonBodyLimitFilter.java` | 同上 | `request.getContentType()` ⇒ **原始** ✔ | ✅ **已改为调用唯一真源** |

⇒ **三份当前都取原始头值 ⇒ 判定与 Go 一致 ⇒ 行为是对的**
（`FavoriteController.java:241` 的注释甚至已经写明「Go 用 strings.Contains（大小写敏感），不是 MIME 解析 —— 别用 MediaType.parseMediaType」）。

### 2.2 为什么它仍是**跨域**问题（而不是三个域各自的小事）

T-1 修复**前**，RAG 内部是**自相矛盾**的：

```
Content-Type: Application/JSON
  ├─ favorite / hot / article 的三份复制品 ⇒ 「不是 JSON body」⇒ 不读   ← 与 Go 一致
  └─ GoStyleJsonBodyConverter（唯一真源）   ⇒ 「是 JSON body」⇒ 读     ← 与 Go 相反 ❌
```

⇒ 同一个请求，**闸门说不读、解析器说要读** ⇒ 这不是「某域的 bug」，是**共享判据被复制**造成的一致性风险。
⇒ **T-1 已于 2026-10-25 修复**（唯一真源改取原始头值）⇒ 现在两侧一致。

### 2.3 ✅ 第十六轮的收敛（**3 份 → 1 份**）

**收敛方式不是「把三份都改成调真源」**（favorite 改不了），而是**把真源变厚**：

| 改动 | 内容 | 为什么这样改 |
|---|---|---|
| 共享层 | 新增 `goReadsBody(HttpServletRequest)` | 连「**怎么从请求里取头值与长度**」一起收进真源 ⇒ 以后没人能在取输入这一步取错（取 `MediaType.parseMediaType` 就会大小写不敏感 ⇒ 与 Go 相反） |
| `hot-api` | `GoFormValues.requireParsableJsonBody` 改调该重载 | 顺带修掉 `request.getContentLength()`（**int**）⇒ > 2 GiB 时 Tomcat 返 -1 ⇒ 与 Go 的 `int64` 判成相反 |
| `article-api` | `GoStyleJsonBodyLimitFilter` 删掉本地判据，改调该重载 | 该 Filter **仍在链上**（它守的是 Go 的 `maxBodyLen`，与判据收敛是两件事） |

**🔴 收敛时顺带查出的第二道闸** ⇒ 见本档 **§二·十三**（解析层还缺 Go 的 `maxBodyLen`；端到端今天不可达）。

### 2.4 残留风险（**favorite 那份永久无法收敛**）

| 风险 | 说明 |
|---|---|
| ⛔ **favorite 那份永久无法收敛** | 2026-10-25 用户重申 favorite 零权限 ⇒ **hot + article 两份**可改，**favorite 那份不能** ⇒ 收敛后仍是「共享真源 + favorite 一份复制品」⇒ **「3 份复制品」的风险在 favorite 上永久存在** |
| ✅ **当前行为无偏差** | favorite 那份取的仍是**原始头值** ⇒ 判定仍与 Go 一致 ⇒ **今天没有任何可观察差异** ⇒ 风险只在于「未来有人把它改成 MediaType 解析」 |
| ✅ **已做的缓解** | ① 唯一真源的 javadoc 点名了残留位置；② 新增 `tools/difftest/check-go-json-body-predicate.py` —— **复制品白名单只留 favorite 一条**，多一份即 `EXIT 1` |

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
| 决策记录 | [`docs/Intentional_Deviations/message/rpc.md` §`SD-2026-10-23-06`](../../Intentional_Deviations/message/rpc.md)（**message 域那一条** —— 该编号在 3 个域各有含义，引用必须带域限定）<br>⚠️ **global 侧的 `SD-2026-10-25-03` 已撤销（重复登记）** ⇒ 见 [`docs/Intentional_Deviations/global/cross-domain.md`](../../Intentional_Deviations/global/cross-domain.md) |
| 代码侧指针 | `service/message/message-rpc/src/main/resources/db/migration/V1__create_message_notification.sql:27-38` |

⚠️ **别把它悄悄改回单列** ⇒ 那里已留指针 + 签字档是唯一记录处。
⚠️ **本条的 DRIFT 侧销账日期是 2026-10-23**（`docs/DRIFT/message/rpc.md` §7 `DRIFT-MSG-V-022`），**不是** 2026-10-25 —— 2026-10-25 只是 global 侧重新发现并把指针指对。

---

## 二·八、X-5 ✅ **已落地（2026-10-25 第十七轮）** —— 裸 `ServerCallHandler` 不被 dispatch ⇒「造慢服务」的测试**曾是空转的**

> ⚠️ **本条由「仍未决 · 未展开」升级为「已确证」（第七轮）⇒ 再升级为「已落地」（第十七轮）**
> —— 机制已用**一次性探针**实测清楚，**波及面从「task 域 1 处」扩到「task + follow 两个 rpc 域各 1 处」**，
> 且**两个域的测试已改完、变异转红**。
> ⚠️ **改的是测试，不是生产代码** —— 超时机制**本来就是真的**（见 ⑤·三），
> 缺的只是「正在执行的处理被打断」这一维**从未被验证过**。

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

### ④ 修法（**第十七轮已落地**）

| # | 动作 | 状态 |
|---|---|---|
| ① | 把裸 `ServerCallHandler` 换成 grpc-java 的**标准派发入口** `io.grpc.stub.ServerCalls.asyncUnaryCall(...)` ⇒ `onHalfClose` 才会被派发 ⇒ 处理函数**真的执行** | ✅ **已做**（task + follow） |
| ② | 补一条断言：「**handler 确实被调用过**」（计数器 / latch）+「服务端在 handler **执行中途**取消它」⇒ 补上现在缺失的那一维语义 | ✅ **已做**（两条断言） |
| ③ | **变异测试**：去掉 `server.intercept(...)` ⇒ 客户端 5s deadline 生效 ⇒ `elapsed ≈ 5000ms` ⇒ **转红**（证明守门非空转） | ✅ **已做**（M1 转红） |

---

### ⑤ 第十七轮落地（2026-10-25）

#### ⑤·一 改了什么（**两个域同构** ⇒ 逐条相同）

| 文件 | 改动 |
|---|---|
| `service/task/task-rpc/src/test/java/com/rag/task/task/rpc/TaskGrpcInterceptorWiringTest.java` | 裸 `ServerCallHandler`（手写 `startCall` + `onHalfClose`）⇒ **`ServerCalls.asyncUnaryCall((req, observer) -> {...})`**；`Thread.sleep(10_000)` ⇒ **`probe.release.await(30, SECONDS)`**（latch）；**新增两条断言**（`entered` / `completed`）；`finally` 里加 `probe.release.countDown()` |
| `service/follow/follow-rpc/src/test/java/com/rag/follow/follow/rpc/FollowGrpcInterceptorWiringTest.java` | 同款 |

⚠️ **生产代码一行没动**（`TaskRpcApplication` / `FollowRpcApplication` 的
`server -> server.intercept(GoStyleRpcProtectionChain.build(...))` 原样保留）
⇒ **本轮不是行为修复，是「守门修复」**。

#### ⑤·二 为什么用 latch 而不是 `sleep`

| | `Thread.sleep(10_000)` | `probe.release.await(30, SECONDS)` |
|---|---|---|
| 测试收尾 | 必须等满 10 秒 | `finally` 里**立刻**放行 |
| 「取消时它还在跑」可观测 | ❌ 等到断言时它早跑完了 | ✅ 精确 |

⇒ ② 的两条断言**在 `sleep` 方案下根本写不出来**。

⚠️ **补一条签名事实**（改造时会踩）：grpc-java 1.63 的 `ServerCalls.asyncUnaryCall` 有
**单参重载** `asyncUnaryCall(UnaryMethod<ReqT,RespT>)`（**不带** `MethodDescriptor`）
⇒ 照抄网上常见的双参写法会编译不过。

#### ⑤·三 变异证据（三条全部转红）

| # | 变异 | 结果 |
|---|---|---|
| **M1** | 去掉 `server.intercept(...)`（两域的 `rpcInterceptorConfigurer` ⇒ 块 lambda 空实现） | 🔴 **RED** —— task `5010ms` / follow `5009ms`（红在「必须在服务端超时（300ms）附近返回」） |
| **M2** | 把 `asyncUnaryCall` 换回**裸** `ServerCallHandler` | 🔴 **RED** —— 红在**新增**的 `entered` 断言：「handler 必须**真的被调用过**」 |
| **M3** | handler 在超时前**完成但不回包**（`await(0)` + 去掉 `observer.onNext/onCompleted`） | 🔴 **RED** —— 红在**新增**的 `completed` 断言：「取消发生时 handler 必须**仍在跑**」 |

⚠️ **M3 的第一版写错了**：只做 `await(0)` 不去回包 ⇒ handler 秒完成**并正常回包** ⇒
客户端拿到 **OK** ⇒ 红在**更早**的 `assertThrows` 上 ⇒ **新增的 `completed` 断言压根没被执行到**
⇒ 等于没验。改成「完成但**不回包**」⇒ 超时照常触发 ⇒ 才红在**目标断言**上。
⇒ **教训**：变异要红在**目标断言**上，红在更早的断言上**不算数**（见 `docs/Pitfalls/global/cross-domain.md` PIT-GLOBAL-018）。

#### ⑤·四 证据边界（**不声称已证明的**）

1. ⚠️ 本轮**只验了「服务端超时打断在飞处理」这一条语义**；
   同一条测试**没有**覆盖 Go 的 **slow threshold** 分支（Go `observability.SlowThreshold()`）——
   两个域的 `server.intercept(...)` 链里那条阈值**仍无独立守门**。
2. ⚠️ **没有**做「Go 侧同一场景的活体对拍」—— 本轮是**RAG 侧自洽性**修复（测试没在验它 ⇒ 改成在验它），
   **不是** Go↔RAG 的 wire 层差异 ⇒ **不涉及** proto / DDL / 错误码。
3. ⚠️ `message-rpc` / `like-rpc` / `users-user-rpc` / `admin-rpc` / `comment-rpc` / `hot-rpc` /
   `article-rpc` / `favorite-rpc` 的同名 `*GrpcInterceptorWiringTest` **未逐一复查**
   ⇒ 「还有没有别的域用裸 handler 造慢服务」**本轮未穷尽**（第七轮的扫描结论是 task + follow 两个域，
   但那次扫描的是**「裸 handler 注册进 server」**这一形态；**若别的域用了别的空转形态，本轮查不出来**）。

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
| **可观察差异** | ① jsonb 会**规范化 JSON**（键序/空格）⇒ 落库字节与 Go 不同；② 含 `\u0000` 时 PG 报 **`22P05`** ⇒ **Go 成功 / RAG 5001**（与已修的 `DRIFT-ARPC-004①` 同型 —— `admin_admin` 已改、`users_user` **漏改**；⚠️ **SQLSTATE 是 22P05 不是 22P02**，第十八轮真 PG 实测订正）；③ 与 RAG 自身 user-rpc 写同一列的 `JsonTextTypeHandler`（`user-rpc/.../domain/UserEntity.java:114`）**自相矛盾** |
| **登记状态** | `docs/DRIFT/users/admin/api.md:1660` 只列了「仍开放」，**归因缺失**（没落到「建表责任交叉 ⇒ DDL 归属」这条因果链上） |
| **建议修法** | 删掉 `AdminDomainService.java:962` 的 `CAST(... AS jsonb)`，改用与 `updateSelf` 同款的 `setSql("extra_info = {0}", …)`（`:357`）；`UserEntity.java:129` 换成 `JsonTextTypeHandler` |
| **可做性** | ✅ **global 现在有权限**（users 域，非 favorite）⇒ **无需拍板** |
| **✅ 落地状态** | ✅ **2026-10-25 第十八轮已修** ⇒ 见 **§二·十四**（真 PG 坐实 + 真 PG 守门 + 3 条变异全红） |

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

⇒ **待判定（第九轮留的）**：RAG 这套「自己实现的 outbox+stream+scheduler」与 Go 的 `communityfact` 链路**行为是否等价**？
（B2 已钉住共享组件的契约：`POST /v1/events` + 调度形状 **1s / 每轮 16 / 单次 10s / 单条失败即 break** ⇒ 应拿这四条逐条对 RAG 的 `LikeFactDispatchScheduler`。）

#### 🔴 2026-10-25 第十九轮末**实测重判**（这条「待判定」已被证伪 ⇒ 定性变了）

| 事实 | 出处 | 推论 |
|---|---|---|
| ① **共享层的 `Dispatcher` 已经按 Go 的形状实现** | `service/common/.../communityfact/Dispatcher.java:176-203`（ticker 1s + 内层 `for (i<16)` + 单条失败/未发送即 `break` + 单次 10s 超时，对 Go `communityfact/process.go:41-53`） | ⇒ 🔴 **不存在「共享组件 ticker 语义漂移」**（此前 todo 里那条描述**作废**）⇒ **B4 的等价性判定不必单独做**（形状已对齐，且 comment 域已在用） |
| ② `Dispatcher.run(ctx, DispatcherImpl, Sender, …)` 的 `DispatcherImpl` 是 **`@FunctionalInterface`** | `Dispatcher.java:35-36`（+ `:64/:86` 签名） | ⇒ 共享层**不绑死任何域的表** ⇒ like 域**可直接复用**（自己实现取件/结算回调即可） |
| ③ **Go 侧 like 域本来就有自己的 outbox/stream 表** | Go `service/like/rpc/internal/model/domain_fact.go:31,40`（`like_domain_fact_outbox` / `like_fact_stream`）+ `migrations/001,002` | ⇒ 🔴 「RAG 另起炉灶」的说法**只对了一半** ⇒ **表名两侧同名**（RAG V4/V5 ✔）⇒ **数据层不是问题** |
| ④ 真正缺的只有**投递那一段** | `LikeFactDispatchService.java`（**4 行 · 骨架占位 · 未实现**）+ `LikeFactDispatchScheduler.java`（**4 行 · 骨架占位**） | ⇒ 🔴 这是**功能缺口**，**不是语义漂移** ⇒ ⇒ **不能签字认账**（签了等于认下「少一个功能」） |
| ⑤ 数据层已齐 | `LikeDomainFactOutboxEntity`(340 行) + `LikeFactStreamEntity` + 2 mapper + `LikeDomainFactService`(175 行) + V4/V5/V6 迁移 + `LikeDomainFactSchemaParityTest` | ⇒ 只差「把件送出去」那一段 |
| ⑥ **样板现成** | comment 域：`CommentFactDispatchScheduler`(118 行) + `CommentAuthorityEndpointConfig`(90 行) | ⇒ 修法 = **照 comment 的接法**补齐 like 的两个骨架 ⇒ **共享层零改动** ⇒ ⛔ **不需拍板** |

### ③ 🔴 我自己第四轮（B2 / X-3）的结论**有误，须订正**

⇒ B2 写的是「`like` / `comment` / `favorite` 三域**都接了线**」⇒ **实测 `like` 域是 0 引用** ⇒ **该句作废**。
⇒ 准确表述：**`comment` 与 `favorite` 接了共享组件；`like` 域没有（它自己实现了一套）；`article` 多了一处 Go 侧没有的用法。**

### ④ 处置

| 半边 | 判定 | 处置 |
|---|---|---|
| **like 缺共享组件接线** | 🟢 **可修，且定性已改**（第十九轮末实测：① 共享层形状已对齐 Go ② `DispatcherImpl` 是函数式接口 ⇒ 通用 ③ Go 侧 like 本来就有自己的表 ⇒ 数据层无问题 ④ 真正缺的只是**投递那段 4 行骨架**） | ✅ **照 comment 域的接法补齐 `LikeFactDispatchService` + `LikeFactDispatchScheduler`**（共享层零改动）⇒ **不需拍板**；⚠️ 这是**功能缺口** ⇒ **不能签字认账** |
| **favorite / article 多出来的用法** | 🔴 **不是漂移**（Go 侧本来就没有 ⇒ RAG 多做了功能）⇒ 属「有意偏离」还是「多余实现」**待判** | ⛔ **favorite 一律不碰** ⇒ 这半边**被 favorite 永久阻塞** |

⚠️ ⇒ **X-4 无法整体关闭**：**可修的一半（like）+ 永久阻塞的一半（favorite）** ⇒ 台账应拆成两条。

---

## 二·十一、X-6 🔴 **第十一轮已改判 ⇒ 不是 favorite 边界下的永久阻塞**（`DRIFT-HOTAPI-030`）

> ⚠️ **第九轮结论已被本轮推翻**：第九轮写「修法必然要动 `favorite-api` ⇒ 永久阻塞」——
> **那个结论只覆盖了「逐模块改 `application.yml`」这一条修法**，而**不是全部修法**。
> 本轮实测发现**共享层修法**（落在 `service/common`）⇒ **10 个 api 模块零改动 ⇒ favorite 零触碰**。
> ⇒ **教训已登记** ⇒ [`docs/Pitfalls/global/cross-domain.md`](../../Pitfalls/global/cross-domain.md)。

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

## 二·十二、X-1 ✅ **共享 Flyway 账本：配置层 + 运行时闭环 ⇒ 修掉 3 个域的真缺陷**（2026-10-25 第十五轮）

> **本条的原状态**：「配置层 + 运行时已闭环；**三模块同库共存**仍 `unresolved`」（需真 PG + 三进程并发）。
> ⇒ 本轮把它**拆成两半**：**配置层有一个此前没人登记的真缺陷**（本轮修掉），**运行时并发**留下一轮。

### ① 🔴 取证：7 个 Flyway rpc 模块**全部连同一个库** ⇒ 同库共存是**常态**，不是边缘场景

| rpc 模块 | DSN（默认） | 账本表 |
|---|---|---|
| `message-rpc` | `first_db` | `flyway_schema_history_message` |
| `like-rpc` | `first_db` | `flyway_schema_history_like` |
| `comment-rpc` | `first_db` | `flyway_schema_history_comment` |
| `follow-rpc` | `first_db` | `flyway_schema_history_follow` |
| `task-rpc` | `first_db` | `flyway_schema_history_task` |
| `favorite-rpc` | `first_db` | `flyway_schema_history_favorite` |
| `article-rpc` | `first_db` | **默认表名** `flyway_schema_history`（由它独占，见 AGENTS.md §6.3 问题 4） |

⇒ ⇒ **「先启动的模块已经把 public schema 弄非空」是必然**，不是「万一」⇒ ⇒ **每个 V1 起始的模块都必须能扛住非空 schema**。

### ② 🔴 缺陷形态（Flyway 的 baseline 语义）

```
spring.flyway.baseline-on-migrate: true      ← 7/7 都配了
spring.flyway.baseline-version                ← 🔴 部分模块【没配】⇒ 取默认值 1
```

⇒ **非空 schema + 没有自己的账本** ⇒ Flyway 在 **version 1** 处打一条 BASELINE 记录
⇒ ⇒ **只执行版本 > 1 的迁移** ⇒ **V1 的 SQL 从未执行** ⇒ 首张表永远建不出来
⇒ ⇒ 后续依赖它的迁移报 `relation "…" does not exist` ⇒ **服务起不来**。

⚠️ **同一缺陷在 `message-rpc` 上已于 2026-10-24 被抓到并修掉**（`MessageFlywaySharedSchemaWiringTest`）
⇒ ⇒ **同一个坑，在另外 3 个域上原封不动地留着** ⇒ 这正是「已知模式没被推广」的典型。

### ③ 逐模块判定（**不是所有模块都需要改**）

| rpc 模块 | 最低迁移版本 | 是否受 baseline 默认值影响 | 处置 |
|---|---|---|---|
| `comment-rpc` | **V1**（`subject`） | 🔴 **受影响** | ✅ **本轮修** |
| `follow-rpc` | **V1**（`follow_relation`） | 🔴 **受影响** | ✅ **本轮修** |
| `like-rpc` | **V1**（`like_record`） | 🔴 **受影响** | ✅ **本轮修** |
| `favorite-rpc` | **V1**（`favorite_folder`） | 🔴 **受影响** | ⛔ **零触碰** ⇒ 见 ⑤ |
| `message-rpc` | V1 | ✅ 已配 `baseline-version: 0` | 不动 |
| `task-rpc` | V1 | ✅ 已配 `baseline-version: 0` | 不动 |
| `article-rpc` | **V8**（`article`） | 🟢 **不受影响** —— 最低版本 8 > baseline 默认值 1 ⇒ V8/V9/V10 照常执行 | **不动**（差点误修 ⇒ 逐模块判定后才排除） |

### ④ 修法 + 守门（真 PG 16 + 真 Flyway 引擎）

- **修法**：3 个模块的 `application.yml` 补 `spring.flyway.baseline-version: 0`（**一行**）
  ⇒ baseline 标记打在 version 0 ⇒ **V1 之后全部迁移照常执行** ⇒ 既能在共享库里安全启动，又不丢任何迁移。
  ⇒ ⚠️ **不能改成 `baseline-on-migrate: false`** —— 那会让 Flyway 在非空 schema 上**直接抛异常退出**
  （"Found non-empty schema(s) … but no schema history table"）⇒ 更糟（起不来且无修复路径）。
- **守门**：每个域一个 `*FlywaySharedSchemaGateTest`（`comment` / `follow` / like）⇒
  - **真 PG 16**（Testcontainers）+ **与生产同版本的 Flyway 引擎** + 裸 JDBC
    ⇒ ⚠️ 沿用本仓既有先例（`CommentSchemaPgGateTest` 明确写过「不起 Spring 上下文，避免把 DDL 守门变成全栈启动测试」）；
  - 🔴 **配置取自本模块自己的 `application.yml`**（不是硬编码）⇒ 测的就是生产真正用的那几个值；
  - 🔴 **先把 public schema 弄成非空**（预建一张无关表）⇒ 让 baseline 路径**真的被走到**
    ⇒ 这是「空库测试永远测不出来」的那一层（§3.3 的经典形态）；
  - 断言：① 首张表**真的存在** ② 账本的 BASELINE 行**版本必须是 0** ③ V1..VN **一条不缺**。
- **变异测试（证明守门非空转）**：把 3 个模块 yaml 里的 `baseline-version: 0` **全部删掉**重跑
  ⇒ **6 条守门全部转红**（3 域 × 2 条：首表存在 + BASELINE 行版本）
  ⇒ ⇒ **每个域的守门都对该配置有判别力** ✓
  ⇒ ⚠️ 第一次跑时 Maven 的 **fail-fast** 让 reactor 在第一个失败模块就停了
  ⇒ ⇒ 必须加 `-fae` 才能看到三个域的全貌（**这是本轮踩到的一个小坑**：`-fae` 之前会漏看后两个域）。

### ⑤ 🔴 `favorite-rpc` 同款缺陷：**global 不能改** ⇒ 记账 + 告知该域 owner

| 项 | 内容 |
|---|---|
| 缺陷 | `service/favorite/favorite-rpc/src/main/resources/application.yml` 的 `spring.flyway` 块**缺 `baseline-version`** ⇒ 与 comment/follow/like **完全同款**（V1 = `favorite_folder`） |
| 为什么 global 不能改 | favorite 域**零写权**（AGENTS.md §5.3c，用户重申「你没有 favorite 的所有权限」）⇒ **代码 / 配置 / 测试 / 台账一律不碰** |
| 为什么共享层也代劳不了 | 这次**不是共享层的问题** —— 缺的是**该域自己 yaml 里的一行** ⇒ 共享层无法代劳（与第十四轮 X-6 不同：那条可以靠「共享层 + 缺省 off」规避） |
| 后果（若不修） | 🔴 **2026-10-25 · L4 实测订正故障形态**：**不是「服务起不来」**，而是「**服务起得来、首次查库才炸**」<br>（L4-01：`baseline-version` 缺失 ⇒ V1 被跳过 ⇒ `favorite_folder` 建不出来；但**上下文本身能起来** ⇒ 启动探针/存活探针**全绿** ⇒ 故障推迟到第一次真实查库才暴露 ⇒ **比「起不来」更难被发现**）<br>⇒ 属**生产可用性**问题（不是形状漂移） |
| **交给 favorite 域 owner 的修法** | 在 `spring.flyway:` 块下加 `baseline-version: 0`（与 comment/follow/like 完全同款）⇒ 建议照抄 `CommentFlywaySharedSchemaGateTest` 的守门思路 |
| 记账位置 | 本档（global 台账）+ 本轮 spec §七「不做」⇒ **不静默** |

### ⑥ 残留（本条**未**闭环的部分）

1. ~~**三进程并发的运行时验证**仍未做~~ ⇒ ✅ **已完成（2026-10-25 · 第二十四轮 L4 lane）** ——
   **真 PG 16 三进程并发同库实测**：**六个域可安全并发同库**
   （独立账本表名 + advisory lock **按账本表名**隔离）
   ⇒ 工具 `tools/difftest/check-flyway-concurrent.sh` + `flyway-concurrent-boot.java` + `flyway-concurrent-verify.py`
   ⇒ 逐条证据 ⇒ [`lanes/L4-flyway.md`](lanes/L4-flyway.md) §8.4（结论矩阵）
   ⇒ ⚠️ **唯一例外 = `favorite-rpc`**（缺 `baseline-version: 0` ⇒ 见 ⑤ ⇒ **只记账不修**）。
2. ⚠️ `article-rpc` 用**默认账本表名** ⇒ 任何**新增**的 Flyway 模块若忘了配 `table` ⇒ 直接撞上它
   （AGENTS.md §6.3 问题 4 已记为残留风险）⇒ 本轮**未动**（动它需要改 `article-rpc` 的表名
   ⇒ 属跨域迁移，代价大于收益）。
   ⚠️ **L4 补的第二条残留**：新增模块若忘了配 **`baseline-version: 0`** ⇒ 故障形态**不是「服务起不来」**
   （上下文能起来）⇒ 是「**起得来、首次查库才炸**」⇒ **更难被发现**。

---

## 二·十三、X-9 续 —— **解析层还缺 Go 的 `maxBodyLen`；端到端今天不可达**（2026-10-25 第十六轮）

### ① 怎么发现的：先建探针，再动代码

AGENTS.md §3.3 要求「凡写『与 Go 一致』，必须有**两侧同时跑的**探针」。
本轮新建 **`tools/json-body-probe/`**（Go 半边 + Java 半边 + `run-parity.sh`）：
Go 半边**直接调** go-zero v1.10.2 的 `httpx.Parse`，Java 半边走 **hot 域真实实现**
（`GoFormValues.requireParsableJsonBody`）⇒ **期望值不是人写的，是这份输出**。

> ⚠️ 与 `tools/semantic-probe/` 的差别：本探针**没有 known-dev 白名单** ⇒
> 任一条不一致即 `EXIT 1` ⇒ 因为这 20 条全是「Go 的字面语义」，两侧必须逐条一致。

### ② 🔴 首跑：19/20 一致，**J20 不一致**

```
J20 9MiB 单个 JSON 对象 + JSON 头 | Go REJECTED | RAG ACCEPTED | XX 判定不一致
```

Go 侧错误详情长度 = **8388643 = 8 MiB + 3** ⇒ **截断点精确在 8 MiB** ⇒ 坐实：

| | Go | RAG（修前） |
|---|---|---|
| > 8 MiB 的 JSON body | `io.LimitReader(r.Body, 8MiB)` **静默截断** ⇒ 半个文档 ⇒ **报错** | `readAllBytes()` / Jackson 无上限 ⇒ **整个读进堆里并解析成功** |

Go 出处：`rest/httpx/requests.go:21`（`maxBodyLen = 8 << 20`）+ `:119`（`io.LimitReader`）。

⇒ 而 `maxBodyLen` 在 go-zero 的 `httpx` 包里 ⇒ **所有**走 `httpx.ParseJsonBody` 的域都有
⇒ ⇒ **必须修在共享层**（一次覆盖 like / follow / message / task / user / admin / article）⇒ 属 global 收口域。

### ③ 🔴 **但端到端今天不可达**（本轮最要紧的一条澄清）

修完跑端到端时发现：**hot 的 9 MiB body 端到端是 413，不是 5001** ⇒ 回头查 Go 的 MaxBytes：

```
go-zero v1.10.2 rest/config.go:53   MaxBytes int64 `json:",default=1048576"`   ← 1 MiB 默认值
go-zero v1.10.2 rest/config.go:21   Middlewares.MaxBytes bool `json:",default=true"`
go-zero v1.10.2 rest/engine.go:163-164
    if ng.conf.Middlewares.MaxBytes {
        chn = chn.Append(handler.MaxBytesHandler(ng.checkedMaxBytes(fr.maxBytes)))
    }                                                  ← ← 挂在**引擎**上 ⇒ 对所有路由生效
```

⇒ Go 的 MaxBytes **默认 1 MiB 且全局生效** ⇒ 9 MiB 的 body **先被拦成裸 413**
（net/http 的 `requestTooLarge()`）⇒ **根本走不到** `ParseJsonBody`。

⇒ **逐域算「8–10 MiB 段端到端可不可达」**（RAG 各 api 的 `max-bytes`）：

| api 模块 | `max-bytes` | 8–10 MiB 段端到端可达？ |
|---|---|---|
| `article-api` | **10485760**（10 MiB） | 🟢 可达 ⇒ 但已被 `GoStyleJsonBodyLimitFilter` 覆盖（显式 400，已签字 DRIFT-006） |
| `hot` / `comment` / `follow` / `like` / `message` / `task` | 1 MiB（Go 默认） | ⛔ 不可达（MaxBytes 先拒） |
| `user-api` / `admin-api` / `favorite-api` | 未配 ⇒ 走共享 `GoStyleMaxBytesFilter` 默认值 | ⛔ 不可达 |

⇒ **诚实结论**：
- 本次修的是**解析层**与 Go 的对齐（探针直接验的就是这一层）；
- **端到端今天没有任何域会因它而改变行为**（唯一可达的 article 段已被它自己的 filter 覆盖）；
- ⇒ 价值 = **解析层忠实** + **去掉共享层的无上限读** + **防「哪天 MaxBytes 被调大」**；风险 = **零**。

⚠️ **这个层次区分本身是一条产出**：差点把「解析层漂移」写成「端到端漂移」，
而端到端那条**根本不存在**（MaxBytes 先拒）⇒ 这是「跑绿 ≠ 对」的另一个形态：
**探针测的层 ≠ 端到端跑的层**。

### ④ 改动

| 层 | 改动 |
|---|---|
| 共享层 `service/common` | `GoStyleJsonBodyConverter`：新增 `GO_MAX_BODY_LEN`（8 MiB，**不可配置** —— Go 侧是框架常量）+ `goLimitedBody(InputStream)`（Go `io.LimitReader` 的等价物）+ servlet 版判据重载；`SanitizedJsonContentTypeInputMessage#getBody` 改走限流 |
| `hot-api` | `requireParsableJsonBody`：判据改调唯一真源；读 body 改走 `goLimitedBody`；解析从 `readTree(byte[])` 改 `readTree(InputStream)`（**流式** ⇒ 不会为一个小 JSON 等满 8 MiB） |
| `article-api` | `GoStyleJsonBodyLimitFilter`：删本地判据，改调唯一真源（该 Filter **仍在链上**） |

### ⑤ 守门（每条都做过变异）

| # | 守门 | 位置 |
|---|---|---|
| G1 | **双侧活体探针** 20 例（**无白名单**） | `tools/json-body-probe/run-parity.sh` |
| G2 | **真 Tomcat + 裸 socket**：原始 Content-Type 不被归一 | `service/common` `GoStyleJsonBodyRawHeaderTomcatTest` |
| G3 | 共享转换器只读前 8 MiB（边界卡到 **±1 字节** + 「底层流最多被读走 8 MiB」） | `service/common` `GoStyleJsonBodyMaxBodyLenTest` |
| G4 | hot 域：9 MiB 端到端 ⇒ **413**（MaxBytes，Go 同形）；**解析层**直接调用 ⇒ 5001；大小写 ⇒ 不读 | `service/hot/hot-api` `HotApiJsonBodyGateTest` |
| G5 | article 域闸门走共享判据（大小写 ⇒ 闸门**不触发**，判别式：body 含 `is not set` 而非 `request entity too large`） | `service/article/article-api` `ArticleApiJsonBodyLimitCaseTest` |
| G6 | 判据只有一份实现（复制品白名单只留 favorite 一条） | `tools/difftest/check-go-json-body-predicate.py` |

### ⑤·二 🔴 变异测试抓到的**我自己守门**的同义反复（本轮最重要的一条）

| 变异 | 结果 |
|---|---|
| M1 共享真源判据改成**大小写不敏感** | ✅ common **5** 条 · hot **2** 条 · article **1** 条 · 探针 **3** 条不一致 ⇒ 全中 |
| M2 `GO_MAX_BODY_LEN` 改成 1 MiB | 🔴 **首跑 0 红** ⇒ 抓到 G3 的同义反复（见下）⇒ 修正后**复验转红**：common 228 条里 `maxBodyLenConstantMatchesGo` 失败 + `exactlyMaxBodyLenIsAccepted` 报错 |
| M3 某域重新抄一份判据 | ✅ `tools` 守卫 `EXIT 1` |
| M4 去掉共享转换器的限流 | ✅ common **3** 条转红（`oneByteOverMaxBodyLenIsRejected` / `nineMiBJsonObjectIsRejected` / `underlyingStreamIsCappedAtEightMiB`） |

🔴 **M2 抓到的是**：G3 的边界 body **是用 `GO_MAX_BODY_LEN` 这个常量构造的**
⇒ 变异把常量改成 1 MiB 后，**测试构造的 body 也跟着缩到 1 MiB** ⇒ **恒绿**
⇒ 即：**那条守门当时根本没在守那个上限值**。
⇒ 修正：边界一律用**字面值** `8 * 1024 * 1024`（Go 出处 `rest/httpx/requests.go:21` 的 `maxBodyLen = 8 << 20`）
+ 另配一条断言把**常量本身**钉住 ⇒ 它成为 M2 的唯一拦截点。

⚠️ **探针抓不到 M2**（实测：探针 20/20 仍绿）⇒ 因为探针比的是**判定**（读 / 不读、拒绝 / 接受），
上限从 8 MiB 改成 1 MiB 时**两侧都判「拒绝」** ⇒ 判定一致 ⇒ 探针无感
⇒ ⇒ **探针守「判定」、边界测试守「阈值」⇒ 两者缺一不可**（已写进 `tools/json-body-probe/README.md`）。

### ⑥ favorite 域的待办（**本轮零触碰**）

| 项 | 说明 |
|---|---|
| `FavoriteController.java:146` 那份复制品 | 用户重申 favorite 零权限 ⇒ **永久残留**。当前取原始头值 ⇒ 判定仍与 Go 一致 ⇒ **当前行为无偏差**；风险仅在「未来被改成 MediaType 解析」⇒ 已进 G6 白名单 |
| 修法 | 改成 `GoStyleJsonBodyConverter.goReadsBody(httpRequest)`（**前提**：`favorite-api` 的 classpath 上有 Jackson 3 —— `spring-boot-starter-web` 已带，实测 `hot-api` 同款可用） |

---

## 二·十四、B10 ✅ **已落地（2026-10-25 第十八轮）** —— `users_user.extra_info` 写路径仍按 jsonb ⇒ Go 成功 / RAG 5001

> ⚠️ 本条由**第九轮 §二·九 ③** 的只读审计产出 ⇒ **第十八轮修完**。
> 🔴 **它不是「X-2（建表责任交叉）」的一部分** —— 属 users 域单域漂移，
> 之所以落 global：① 修法落点跨 `users-common`（DDL 资源）+ `admin-rpc`（写路径）⇒ 同一次提交
> ② §5.3b 规定 users 域台账不归 global 写 ⇒ 本档是**唯一**能记它的地方。

### ① 漂移本体（2026-10-25 第十八轮真 PG 坐实）

| | 内容 |
|---|---|
| **Go** | GORM `serializer:json` ⇒ 产出**字符串** ⇒ 落进 **text** 列（`service/user/admin/rpc/internal/model/data_model.go:43` 的 `ExtraInfo map[string]string` + `gorm:"column:extra_info;serializer:json"`）⇒ **没有 jsonb 那层校验** |
| **RAG（修前）** | `AdminDomainService.java:962` 写死 `extra_info = CAST({0} AS jsonb)`；实体挂 `JsonbTypeHandler`（`UserEntity.java:129`） |
| **🔴 前提失效** | 原注释写「该列**仍是 jsonb**，CAST 必须保留」—— **假**：`users-common/.../db/01_create_user.sql:41` 建表就是 `extra_info  TEXT`，`:103` 每次启动幂等 `ALTER … TYPE text USING extra_info::text` ⇒ 这一列**不可能**是 jsonb |
| **可观察差异①** | 含 `\u0000` 的 payload ⇒ **PG 拒收**（真 PG 16 实测）⇒ **Go 成功 / RAG 5001**（与已修的 `DRIFT-ARPC-004①` 同型：`admin_admin` 已改、`users_user` **漏改**） |
| **可观察差异②** | jsonb 会**规范化 JSON**（键重排 / 冒号后补空格）⇒ **落库字节与 Go 不同**（实测 `{"k":"v"}` → `{"k": "v"}`） |
| **可观察差异③** | 与 RAG 自身 user-rpc 写**同一张表同一列**的 `JsonTextTypeHandler` **自相矛盾** |

### ② 🔴 顺手订正一个**到处写错的 SQLSTATE**

台账与旧注释都写「PG 报 **22P02**」⇒ **错**。真 PG 16 实测：

```
ERROR:  22P05: unsupported Unicode escape sequence
DETAIL:  \u0000 cannot be converted to text.
CONTEXT:  JSON data, line 1: {"k":"\u0000...
LOCATION:  json_errsave_error, jsonfuncs.c:634
```

⇒ **22P05**（`json_bad_unicode_escape`），**不是** 22P02（`invalid_text_representation`）。

⚠️ **本轮已改**（5 处，本域/共享层内）：`AdminDomainService.java`（2 处）·
`AdminRpcColumnTypeParityTest.java`（2 处）· `users-common/.../03_create_admin.sql`（1 处）。
⛔ **未改（不在 global 写权内）**：`docs/DRIFT/users/admin/rpc.md:483,732` ·
`docs/Pitfalls/users/admin/api.md:386` ⇒ **记账 + 交该域 owner**。

### ③ 改了什么（`service/users/admin/admin-rpc`）

| # | 动作 |
|---|---|
| ① | `AdminDomainService.updateUser`：`extra_info = CAST({0} AS jsonb)` ⇒ **`extra_info = {0}`**（与 `updateSelf` 同款） |
| ② | `admin-rpc/domain/UserEntity.java:129`：`JsonbTypeHandler` ⇒ **`JsonTextTypeHandler`** |
| ③ | **删除** `admin-rpc/mapper/JsonbTypeHandler.java` —— 它的**全部存在理由**（「jsonb 列拒收 varchar 绑参」）在该列变成 text 后**不复存在** ⇒ 与 user-rpc 2026-10-23 的改名同型（类名继续叫 `Jsonb…` 就是在骗下一个读代码的人） |
| ④ | 订正 5 处失效前提的注释（`UserEntity` 的 DDL 块 / `AdminEntity` / `JsonTextTypeHandler` / `admin-api WebMvcConfig` / `admin-rpc pom.xml` / `AdminRpcContainerWiringTest`） |

⚠️ **② 为什么安全**：admin-rpc 写 `users_user` 的**每一条**语句都是
`userMapper.update(null, wrapper)`（实体为 **null**）⇒ 实体的 typeHandler **在写路径上从未被用到**
⇒ 真正的分歧**只有**那句 CAST。② 是「让实体与真实列型一致」的收尾，不是行为改动。

### ④ 守门（真 PG 16 + 真业务链 + 变异）

| 用例 | 断言 |
|---|---|
| `AdminRpcColumnTypeParityTest#userExtraInfoColumnIsTextAndAcceptsJsonbHostilePayload` | ① `users_user.extra_info` 的**库内真实列型** = `text` ② payload 对 jsonb **确实敌意**（自证：PG 报 `unsupported Unicode escape sequence`）③ `updateUser` 收下含 `\u0000` 的 payload ⇒ 行还在、库里存着 6 个 ASCII 字符的 `\u0000` |
| `AdminRpcColumnTypeParityTest#userExtraInfoLandsVerbatimNotJsonbNormalized` | 落库**逐字节** = `{"k":"v"}`（若变成 `{"k": "v"}` ⇒ jsonb 的规范化还在链路上） |

⚠️ **为什么不碰「键序」那条轴**：Go 的 `json.Marshal` 按字典序排键、Jackson 保持插入序
⇒ 那是**既有**差异，user-rpc 的 `JsonTextTypeHandler` 类注释已写明**不改** ⇒ 本轮不碰。

### ⑤ 变异（三条全部转红，且**红在目标断言上**）

| # | 变异 | 结果 |
|---|---|---|
| **M1** | 把 `updateUser` 的 CAST 加回去 | 🔴 **RED** —— 两条新用例各红在自己的目标断言上（`INTERNAL: DB更新失败` / `{"k":"v"}` ≠ `{"k": "v"}`） |
| **M2** | DDL 把 `users_user.extra_info` 改回 jsonb（`:41` + `:103` 同时改） | 🔴 **RED** —— 红在列类型断言：`expected: <text> but was: <jsonb>` |
| **M3** | 把 `updateSelf` 的 CAST 也加回去（**旧守门对照**） | 🔴 **RED** —— 红在**旧**用例 `extraInfoAcceptsPayloadJsonbWouldReject` ⇒ 证明旧守门仍有效、且本轮改动**没顺手动** `updateSelf` |

### ⑥ 🔴 变异脚本踩的**第三个**坑：**改上游模块的资源，测试根本读不到**

M2 首跑 **GREEN**（假阴性）—— 根因：`01_create_user.sql` 是 **`users-common` 的资源**，
而只跑 `admin-rpc` 时 Maven 从 `~/.m2` 解析**旧的** `users-common` jar
⇒ **改动压根到不了运行代码**。
⇒ 修法：把 `users-common` **拉进同一个 reactor**（`mvn -o test -pl service/users/users-common,service/users/admin/admin-rpc`）
⇒ 顺带发现 surefire 的参数名换代：`-DfailIfNoTests=false` **管不住** `-Dtest` 模式，
要加 **`-Dsurefire.failIfNoSpecifiedTests=false`**，否则上游模块会因「没匹配到测试」直接 BUILD FAILURE。
⇒ 同族：§PIT-GLOBAL-016。

### ⑦ 证据边界（**不声称已证明的**）

1. ⚠️ 本轮**只**验了 `updateUser` 一条写路径 ⇒ admin-rpc 里**其它**写 `users_user.extra_info` 的语句
   （`updateUserStatusByUid` / `updateUserPasswordByUid` 等）**不写 extra_info** ⇒ 未逐条核对。
2. ⚠️ **Go 侧未做活体对拍** —— Go 的「落库成功」由**真 PG 16 的 text 列实测** +
   `encoding/json` 的转义规则（U+0000 → 6 个 ASCII 字符）推得，**不是**真跑 Go 进程写库。
3. ⚠️ **键序差异仍在**（Go 字典序 / Jackson 插入序）⇒ **不在本轮范围** ⇒ 仍记为既有差异。

---

## 二·十五、✅ **第二十七轮：X-5 轴 / slow 轴守门补齐（6 条 lane）** —— 逐域证据从「1 个样板」到「8 个域」

> 本轮把 L2 留下的 4 条缺口 + L3 的 2 条盲区 + L4 的 3 条边界**一次性打完**
> ⇒ 6 条 lane（L6~L11）并行 ⇒ **全部生产代码零改动**（只有守门与脚本）。

### ① L2 缺口①（X-5 轴真 server 守门）—— ✅ **4 个组件补齐**

| 组件 | 守门类 | 用例 / 断言 |
|---|---|---|
| `users/admin/admin-rpc` | `AdminRpcGrpcInterceptorWiringTest`（新增） | 3 / 14 |
| `article/article-rpc` | `ArticleGrpcInterceptorWiringTest`（新增） | 3 / 14 |
| `comment/comment-rpc` | `CommentRpcGrpcInterceptorWiringTest`（新增） | 3 / 14 |
| `like/like-rpc` | `LikeRpcGrpcInterceptorWiringTest`（新增） | 3 / 14 |

⇒ **9 个 rpc 组件**里，X-5 轴真 server 守门现已覆盖 **8 个**（user / message / hot / task / follow / admin / article / comment / like）
⇒ ⛔ **只剩 `favorite-rpc`**（域零写权 ⇒ 只能记账）。

⚠️ **一条不可外推的结论**：这 8 份守门证明的是「**RAG 侧的机制生效**」
⇒ **不能**据此宣称 Go ↔ RAG 已对齐（Go 侧活体对拍**本轮仍未做**）。

### ② L2 缺口②（slow threshold 逐域证据）—— ✅ **8 个域从 1 个样板补齐**

| 域 | 阈值 / 占用时长 | 守门类 |
|---|---|---|
| admin | 100ms / 400ms | `AdminRpcSlowThresholdGateTest`（L2 原件，本轮**加固**） |
| article | 100ms / 400ms | `ArticleRpcSlowThresholdGateTest`（新增） |
| comment | 100ms / 400ms | `CommentRpcSlowThresholdGateTest`（新增） |
| like | 100ms / 400ms | `LikeRpcSlowThresholdGateTest`（新增） |
| user / message / hot | 200ms / 300ms | `UserRpcSlowThresholdGateTest` / `MessageRpcSlowThresholdGateTest` / `HotRpcSlowThresholdGateTest`（新增） |
| task / follow | 100ms / 400ms | `TaskRpcSlowThresholdGateTest` / `FollowRpcSlowThresholdGateTest`（新增） |

⇒ ⛔ **`favorite-rpc` 仍缺**（零写权 ⇒ 记账）。

### ③ 🔴 整合者修掉的**跨 lane 守门缺陷**（本轮最重要的**代码**产出）

两处**跨 6 条 lane 的共性缺陷**，由整合者统一修（生产代码零改动）：

| # | 缺陷 | 现象 | 修法 | 证据 |
|---|---|---|---|---|
| **③a** | **slow 轴守门缺「预热」** | 7 份里有 **5 份**（admin/article/user/message/hot）的「快调用」用例**首调就红**（实测 follow-rpc 首调 duration = **138.333ms** > 阈值 100ms）⇒ 它们之所以绿，**只因 JUnit 按声明序** ⇒ **顺序依赖的假稳定** | 每个守门在被测调用前先做一次**预热调用**，再 `EXPORTER.clear()` ⇒ 预热必须用**独立 probe / 独立 server**（否则「handler 真被调用过」那条判别式断言会**永远为真** ⇒ 守门空转） | L9 §七·五 N2 · **PIT-GLOBAL-027** |
| **③b** | **slow 轴守门不断言「前提成立」** | 前提「`GlobalOpenTelemetry` 在 refresh 前被换成本测试的 SDK」是**侥幸成立**（`GoStyleOtelAgent` 有「已 set 就放弃」分支）⇒ 将来有人先装了别的 SDK ⇒ 用例会以**「找不到 span」**这种看不懂的形态变红 | 把前提**显式断言** ⇒ 前提不成立时直接说「前提不成立」 | L6 §7.3 缺口④ · **PIT-GLOBAL-026** |

⚠️ **③a 的连带修正**：整合者第一版断言写成 `assertSame(testSdk, GlobalOpenTelemetry.get(), …)`
⇒ **两条用例全红** ⇒ **自曝错账 #4** —— OTel Java 1.55.0 的 `set()` 存的是
`new ObfuscatedOpenTelemetry(sdk)` **包装器** ⇒ `get()` **永远不等于** sdk 本身
⇒ 正确判据是比 **`getTracerProvider()`**（包装器原样委托 ⇒ 同一实例）。
⇒ **教训**：断言「全局单例是不是我装的那个」之前，**先读那个单例的 `set()` 实现**。

### ④ 缺口③ 的定性：「在飞处理被打断」这一维 —— ✅ **框架限制成立，不是新发现**

L9 只读分析（**未改 `service/common`**）：

| 问 | 答 |
|---|---|
| ① `RpcInterceptor` 实际有没有打断在飞 handler 线程？ | **没有** —— `:196-210` 超时只 `close`；`:287-314` `onCancel` 不打断；`:395/:406` 唯一的 interrupt 在 `handle(...)`（**非 gRPC 入口**）⇒ grpc-java 1.63.0 的 `ServerCallImpl` 只取消 grpc-java 的 `Context` |
| ② Go 侧对应行为？ | `rpc.go:38-39` `context.WithTimeout`；`:47-57` handler goroutine 拿 `timeoutCtx`；`:68-71` 超时分支**不等** goroutine ⇒ **Go 也不杀 goroutine**；`rpc.go:21` `conf.Timeout=0` ⇒ go-zero 自带超时（`zrpc/server.go:164-168`）未挂上 |
| ③ 是否行为不一致？ | 仅在「**取消自动传播到在途操作**」这一维（Go ctx 自动 / RAG `DeadlineState` ThreadLocal 需显式轮询）⇒ **框架限制**，非可修局部缺陷（已签字 B-1） |
| ④ L2 的 javadoc 自陈「DRIFT-018 框架限制」是否成立？ | **成立** ⇒ **不是新发现** |

⇒ ⇒ **L2 缺口清单第 5 条**（「在飞处理被打断」这一维）**销账为「框架限制」**，**不补守门**（没有「应当被打断」的可观察行为可守）。

### ⑤ 🔴 L11：Flyway 三条边界 —— **全部做到**，且用 **advisory-lock 屏障**证明是**真并发**

> L4 留下的边界是「起的是**最小 Spring 上下文**（只 import DataSource + Flyway）」⇒ 那不是「三个真 rpc 服务同时起」。
> ⇒ L11 改用**真 `*RpcApplication` 进程**（真 gRPC server + 真 Kafka/Redis + 真 Flyway + 真连同一个 PG 16 库）。

| 边界 | 结论 | 证据 |
|---|---|---|
| **① 真 rpc 进程并发同库** | ✅ **做到** | `follow ‖ task ‖ message` 三个**真进程**并发连同一个库 ⇒ 🔴 **用 advisory-lock 屏障证明「同时」**：预先占住 Flyway 的 advisory lock（`locknum = 77431708279161 + javaHash("public"."flyway_schema_history_<模块>")`，**实测捕获**）⇒ 三个进程**同时**卡在 `pg_try_advisory_xact_lock`（`pg_stat_activity` 可见、此时 `Migrating schema` 计数 = 0）⇒ 才放锁 ⇒ **并发不是墙钟凑巧** ⇒ 三者 `ok=true` · `gRPC 监听=true`（TCP 探测 + `gRPC Server started` 双证据） |
| **② 迁移中途失败 / 回滚 + 同模块两副本** | ✅ **做到** | ① 用 `filesystem:` **额外 Flyway location** 注入必然失败的 V900（**不动生产代码**）⇒ follow 真进程起不来（`FlywayMigrateException` / SQLSTATE 42883）⇒ **失败迁移建的表零残留**（PG 事务性 DDL）⇒ **同库的 task 进程完全不受影响** ② 同模块两副本 ⇒ 账本 **2 行（不是 4）** · `follow_relation` 表数 = 1 |
| **③ N 轮 + 随机启动延迟** | ✅ **做到** | 每轮**新库** + 每域 **0~4000ms 随机启动偏移** ⇒ 全绿 |

**反向注入（判定器非空转）**：把 `message` + `task` 的 `spring.flyway.table` 指向**同一本** `flyway_schema_history_shared`
⇒ **EXIT=1**，红在 **A3-表建出**（`task_progress` / `user_like_count` 没建出来）+ 汇总的「**迁移记录互相污染**」
⇒ 查库确认：共享账本里只剩 message 的 5 行、task 的表**根本没建出来**
⇒ 正常跑同一脚本 ⇒ **EXIT=0**（`--rounds=2` ⇒ 13 个进程全 SUCCESS）。

⚠️ **边界（不许读成「Flyway 已对齐」）**：
- 只覆盖 **3/7 域**（另 4 域用**当前生产 yaml 起不来** ⇒ 需跨 lane 的行为修复）
- 依赖全是**不可达地址**（Nacos / Kafka / Redis）⇒ 只验**迁移面**，不验业务面
- **没测**：迁移中途 SIGKILL · PG 重启 / 网络抖动 / 锁超时
- 随机延迟只覆盖**启动偏移**，没覆盖「极慢机器」
- **本 lane 一句「与 Go 一致」都没说**（Go 侧是 GORM `AutoMigrate` ⇒ **无账本无锁**）
- 判定器是**脚本**，**不进 `mvn test`** ⇒ 全仓回归不覆盖它

### ⑤ 🟡 本轮新发现（**登记，不在 lane 内修**）

| # | 发现 | 落点 | 处置 |
|---|---|---|---|
| **N1** | `RpcInterceptor.java:134-135` 的 javadoc 把「两层补救」**并列**陈述，但第 ② 层**只在 `handle()` 路径** ⇒ 可能写出**永不触发**的 `InterruptedException` 处理 | `service/common`（共享层） | 交整合者 ⇒ 归 `common-tails` |
| **N2** | 「首调 span duration 被类加载撑大」⇒ slow 轴守门**必须预热** | 全部 slow 轴守门 | ✅ **整合者已统一修**（见 ③a） |
| **N3** | 「slow 阈值的**配置被忽略**」是可静默发生的退化（回退 Go 兜底 500ms）⇒ 守门的占用时长必须 **< 500ms** | 全部 slow 轴守门 | ✅ **取值已核**（见 ② 表）⇒ 教训 **PIT-GLOBAL-028** |
| **N4** | `LikeRpcApplication.java:117-119` 的注释说「目前没有」锁死用例，并提到一个**全仓不存在**的类名 | `service/like/like-rpc`（生产代码注释） | ✅ **整合者已订正**（L7 发现，本轮修） |
| **N5** | `CommentFactDispatchScheduler` **不是** `@Scheduled` 驱动 ⇒ 定时方法在本进程不跑 | `service/comment` | **只登记、不定性**（超本轮范围） |
| **N6** | 🔴 **`discovery://<name>`（**两斜杠**）在 5/7 个 rpc 模块的 yaml 里** ⇒ devh 的 `DiscoveryClientResolverFactory` 要求 `targetUri.getPath()` **长度 > 1** ⇒ **真 fat jar 起不来**（**连 Flyway 都跑不到** —— grpc client bean post-processor 先失败）⇒ 只有 `follow-rpc` 是三斜杠（正确）⇒ ⚠️ **与 Nacos 在不在线无关**（检查发生在任何网络调用之前） | 5 个 rpc 模块的 `application.yml` | ✅ **第二十八轮已修**（L12 修 article/comment/like 5 处 + L13 修 message 2 处 = **7 处**）⇒ 🟢 **3 个 `XxxRpcProductionAddressTest`**（读**生产** yaml · 遍历**全部** client · **真调 devh 的 `newNameResolver`**：生产不抛 + 两斜杠**必须**抛）⇒ ⛔ **favorite 那 2 处零触碰**（保留在 baseline） |
| **N7** | `like-rpc` / `article-rpc` 的 `grpc-inprocess` 是 **`test` scope** ⇒ 不在 fat jar 里 ⇒ 实测 `NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder` | 2 个模块的 `pom.xml` | ✅ **第二十八轮已修**（L12 修 article/like + L13 修 task = **3 处**）⇒ 🟢 **产物层守门**（解包真 fat jar 查 `BOOT-INF/lib/` 有没有 `grpc-inprocess`）⇒ 🟢 **独立实测**：修后 `BOOT-INF/lib/grpc-inprocess-1.63.0.jar` **在**，修前不在 ⇒ ⛔ **favorite 那 1 处零触碰** |
| **N8** | `task-rpc` 的 jar **没有 `Main-Class`**（没接 `spring-boot-maven-plugin`）⇒ `java -jar` 起不了 | `service/task/task-rpc/pom.xml` | ✅ **第二十八轮已修**（L13 修 task-rpc + **L14 修 hot-rpc**）⇒ 🔴 **本条 L11 漏报了一个模块**：逐个解包 10 个 rpc 模块的产物 ⇒ 实际是 **2 个**（`hot-rpc` 58,873 B + `task-rpc` 83,827 B）⇒ **漏报根因 = 守门缺口**：`check-jar-executable.sh` 只扫 `*-api` ⇒ **10 个 rpc 模块的产物可执行性从来没被任何守门查过** ⇒ L11 只能手工盘点 ⇒ **手工必漏** ⇒ ✅ **L15 已把 rpc 纳入枚举**（20 个模块）⇒ 教训 **PIT-GLOBAL-030** |
| **N9** | 共享库并发启动时，**后启动者**账本会多一条 `BASELINE(0)`（**不跳迁移**，但会让「行数 == 迁移条数」这条断言**误红**） | 判定器口径 | ✅ **已记进台账**（判定器已按此调整口径） |
| **N10** | postgres 镜像**初始化期**的临时 server 会**吞掉刚建的库**（⇒ 容器刚起时连不上，是镜像行为不是缺陷） | 测试基建 | ✅ **已记进台账** |

## 三、其余 7 条的状态说明（2026-10-25）

本轮**未展开**这 7 条的逐条对照（它们的落点分散在多个域的代码里，
且多数需要**真 PG / 真启动**才能判定 ⇒ 不适合与 T-10 同批做）。

| # | 为什么本轮没动 |
|---|---|
| X-1 | 🔴 **第十五轮已部分消解** —— 取证发现 **7 个 Flyway rpc 模块全部连同一个库** ⇒ 同库共存是**常态** ⇒ 查出 **comment/follow/like 三域缺 `spring.flyway.baseline-version: 0`** ⇒ 非空 schema 下 **V1 被整条跳过 ⇒ 首张表建不出来 ⇒ 服务起不来** ⇒ ✅ **三域已修 + 各配真 PG 守门**（变异 6 条全红）；⛔ **`favorite-rpc` 同款缺陷零触碰 ⇒ 记账 + 告知 owner**；🟡 **「三进程并发」那个形态仍未验证** ⇒ 见 §二·十二 |
| ~~X-2~~ | ✅ **已审完**（第九轮）⇒ 见 §二·九 |
| ~~X-3~~ | ✅ **已闭合**（2026-10-25 B2）⇒ 见 §二·七（末端 = 仓外 DC 平台；仓内两侧 4↔4 对齐） |
| ~~X-4~~ | ✅ **已确证**（第九轮）⇒ 见 §二·十（**可修的一半** = like 侧接线；**阻塞的一半** = favorite） |
| ~~X-5~~ | ✅ **已落地**（2026-10-25 第十七轮）⇒ 见 §二·八 ⑤。机制第七轮已确证（探针实测）⇒ **task + follow 两个域的测试已改完**（换 `ServerCalls.asyncUnaryCall` + 补两条断言）⇒ **M1/M2/M3 三条变异全部转红** ⇒ 🟡 **slow threshold 分支仍无独立守门**；🟡 **其余 8 个 rpc 域的同名测试未逐一复查** |
| ~~X-6~~ | ✅ **已展开 + 已改判 + 已落地**（第九轮展开 / 第十一轮改判 / **第十四轮落地**）⇒ 见 §二·十一（**不是 favorite 边界下的永久阻塞** —— 共享层修法存在 ⇒ **可修**、**不需签字**；🔴 第十四轮补：**Go 的拒绝形状按域而异** ⇒ 共享层按 `mode` 分派 ⇒ **favorite 缺省 off ⇒ 零触碰**） |
| ~~X-7~~ | ✅ **已做**（2026-10-25 B3）⇒ 见 §二·五（4 个域可跑 + 产出 X-10） |

⇒ **排期建议（2026-10-25 更新）**：~~X-7~~ **已做**（§二·五）→ **X-1**（真 PG 三进程）
→ **X-2** → ~~X-3~~（**已闭合**）→ ~~X-5~~（**已落地**，§二·八 ⑤）
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
| 2026-10-25 | ✅ **第十八轮（B10 落地）** —— ① 🔴 **真 PG 16 坐实漂移** —— 独立探针实测：`text` 列收下 Go 侧 `encoding/json` 产出的 14 字节 `{"k":"\u0000"}`（成功）；`CAST('{"k":"\u0000"}' AS jsonb)` **被拒** ⇒ **Go 成功 / RAG 5001** 坐实 ② 🔴 **顺手订正一个到处写错的 SQLSTATE** —— 台账与旧注释都写「PG 报 **22P02**」⇒ **错** ⇒ 实测是 **22P05**（`json_bad_unicode_escape`，`jsonfuncs.c:634`）⇒ 本域/共享层内 **5 处已改**；⛔ `docs/DRIFT/users/admin/rpc.md:483,732` 与 `docs/Pitfalls/users/admin/api.md:386` **不在 global 写权内** ⇒ 记账 + 交该域 owner ③ ✅ **三处改完** —— `AdminDomainService.updateUser` 去掉 `CAST({0} AS jsonb)` + `admin-rpc/domain/UserEntity` 的 typeHandler 换 `JsonTextTypeHandler` + **删除** `admin-rpc/mapper/JsonbTypeHandler.java`（它的全部存在理由「jsonb 列拒收 varchar 绑参」在该列变 text 后**不复存在** ⇒ 与 user-rpc 2026-10-23 的改名同型）④ ✅ **真 PG 守门 2 条**（列型查**库内真实 schema** + `\u0000` payload 自证敌意 + **逐字节落库**）⇒ **M1/M2/M3 三条变异全红且红在目标断言上** ⑤ 🔴 **变异脚本踩第三个坑**：M2 首跑 **GREEN 假阴性** —— `01_create_user.sql` 是 `users-common` 的**资源**，只跑 `admin-rpc` 时 Maven 从 `~/.m2` 解析**旧 jar** ⇒ 改动到不了运行代码 ⇒ 修法 = 把 `users-common` 拉进**同一 reactor**；顺带发现 surefire 参数换代：`-DfailIfNoTests=false` **管不住** `-Dtest` 模式，要加 **`-Dsurefire.failIfNoSpecifiedTests=false`** ⇒ 同族 §PIT-GLOBAL-016 ⑥ ⚠️ **边界**：Go 侧**未做活体对拍**；「键序」差异（Go 字典序 / Jackson 插入序）**不在本轮范围** |
| 2026-10-25 | ✅ **第十七轮（X-5 落地）** —— ① 🔴 **第七轮只取证、这轮改完** —— `service/task/task-rpc` + `service/follow/follow-rpc` 两个域的 `*GrpcInterceptorWiringTest` 把**裸** `ServerCallHandler`（手写 `startCall` + `onHalfClose`）换成 grpc-java 的**标准派发入口** `ServerCalls.asyncUnaryCall(...)` ⇒ 处理函数**真的执行** ② 🔴 **补上原来缺的那一维** —— 两条新断言：「**handler 确实被调用过**」+「**取消发生时它仍在跑**」⇒ 并把 `Thread.sleep(10_000)` 换成 `probe.release.await(30, SECONDS)`（latch）⇒ 两条断言在 `sleep` 方案下**根本写不出来** ③ ✅ **生产代码一行没动**（`server.intercept(...)` 原样）⇒ **这不是行为修复，是守门修复**（超时机制本来就是真的，缺的只是「在飞处理被打断」这一维从未被验证）④ ✅ **三条变异全部转红** —— M1 去 `server.intercept` ⇒ `elapsed 5010/5009ms`；M2 换回裸 handler ⇒ 红在**新增**的 `entered` 断言；M3「完成但**不回包**」⇒ 红在**新增**的 `completed` 断言 ⑤ 🔴 **变异脚本踩了两个坑**（详见 ⑤·三 与 `docs/Pitfalls/global/cross-domain.md` PIT-GLOBAL-018）—— ① 首版 M1 写成**编译不过**的表达式 lambda ⇒ maven **压根没跑测试** ⇒ surefire 目录里留着**上一轮**的 XML ⇒ 脚本读旧结果 ⇒ **报成假绿**（靠「跑前删旧 XML + 看 maven 退出码 + 查 COMPILATION ERROR」三道闸才拦住）② 首版 M3 只做 `await(0)` 不去回包 ⇒ handler 秒完成**并正常回包** ⇒ 客户端拿到 OK ⇒ 红在**更早**的 `assertThrows` 上 ⇒ **目标断言压根没被执行到** ⇒ 变异要红在**目标断言**上 ⑥ ⚠️ **边界**：slow threshold 分支**仍无独立守门**；其余 8 个 rpc 域的同名测试**未逐一复查** ⇒ 「还有没有别的空转形态」**未穷尽** |
| 2026-10-25 | ✅ **第十六轮（X-9 收敛 + 解析层补 `maxBodyLen`）** —— ① ✅ **新建双侧活体探针 `tools/json-body-probe/`**（Go 半边**真调** go-zero 的 `httpx.Parse` + Java 半边走 hot 域**真实实现** ⇒ **期望值不是人写的**；**无 known-dev 白名单** ⇒ 任一条不一致即 exit 1）② ✅ **首跑 19/20 一致，J20 不一致** ⇒ 坐实「RAG 整个读 body、Go 只读前 8 MiB」⇒ Go 侧错误详情长度 **8388643 = 8 MiB + 3** ⇒ 截断点精确 ⇒ ✅ **共享层补 `GO_MAX_BODY_LEN` + `goLimitedBody`（Go `io.LimitReader` 等价物）+ servlet 版判据重载**；✅ **hot 域补上并改用唯一真源**（改**流式**读 ⇒ 不会为一个小 JSON 等满 8 MiB）；✅ **article 域删本地判据改调唯一真源** ⇒ **3 份复制品 → 1 份** ③ 🔴 **澄清一条差点写错的话**：Go 的 `MaxBytes` **默认 1 MiB 且挂在引擎上全局生效**（`rest/config.go:53` + `rest/engine.go:163-164`）⇒ 9 MiB body **先被拦成裸 413** ⇒ **端到端那条漂移根本不存在** ⇒ 本次修的是**解析层**，端到端今天**零行为变化**（唯一可达的 article 段已被它自己的 filter 覆盖）⇒ 逐域可达性已列表 ④ ✅ **6 条守门**（真 Tomcat 裸 socket 验原始头值 / 8 MiB 边界卡 ±1 字节 / hot 端到端 413 + 解析层 5001 / article 判别式断言 / **新 tools 守卫 `check-go-json-body-predicate.py`**）+ **全部做过变异** ⑤ ⛔ **favorite 那份复制品永久残留**（零权限）⇒ 记账 + 写明修法与前提 ⑥ 📌 **新增 PIT-GLOBAL-015**（「探针测的层 ≠ 端到端跑的层」） |
| 2026-10-25 | ✅ **第十五轮（X-1 取证与修复）** —— ① 🔴 **取证推翻「同库共存是边缘场景」** —— **7 个 Flyway rpc 模块的 DSN 默认值全部是同一个 `first_db`** ⇒ 「先启动者把 public schema 弄非空」是**必然** ⇒ 每个 V1 起始的模块都必须扛得住 ② 🔴 **查出 3 个域缺 `spring.flyway.baseline-version: 0`** —— `baseline-on-migrate=true` + 非空 schema + 无账本 ⇒ Flyway 在 **version 1** 处打 BASELINE ⇒ **V1 的 SQL 从未执行** ⇒ 首张表永远建不出来 ⇒ **服务起不来** ⇒ 同款缺陷 `message-rpc` **2026-10-24 已修并留了守门** ⇒ **同一个坑在另外 3 个域原封不动地留着** ③ ✅ **三域已修**（各一行 yaml）+ **各配一个真 PG 守门**（真 PG 16 + 真 Flyway 引擎 + **配置取自模块自己的 yaml** + **先把 schema 弄成非空**）⇒ **变异（删掉那行）⇒ 6 条全红** ④ ⚠️ **`article-rpc` 逐模块判定后排除**（最低迁移版本 **V8** > baseline 默认值 1 ⇒ 不受影响 ⇒ **差点误修一个不存在的缺陷**）⑤ ⛔ **`favorite-rpc` 同款缺陷零触碰** ⇒ 记账 + **明确告知该域 owner**（含修法与守门思路）⇒ **不静默** ⑥ 🟡 **「三进程并发」的运行时形态仍未验证** ⇒ 留一轮 |
| 2026-10-25 | ✅ **第十四轮（X-6 落地）** —— ① 🔴 **推翻第十三轮修法草案的一个隐含前提**：草案假设「共享 Filter 统一写 Go 的 400 原文」⇒ **错** —— **Go 的拒绝形状按域而异**（5 域注册 `httpx.SetErrorHandler` ⇒ **200 + 业务信封**；article/user/task/favorite 未注册 ⇒ **400 + Go 原文**）⇒ 且各域 5001 **文案不同**（like/comment =「系统内部错误」，hot/follow/message = `"internal server error"`）⇒ ⇒ **共享层写不出信封** ⇒ 拒绝形状必须**由域自己渲染** ② 落地结构 = `service/common` **6 个类**（`GoStyleFormValues` 判据 / `GoStyleFormLimitMode` 模式 / `GoStyleFormLimitExceededException` / `GoStyleFormValueLimitInterceptor` / `GoStyleFormLimitWebMvcConfig` / `GoStyleTomcatParameterLimitCustomizer`）+ **9 个域各一行 yaml**（`rag.http.go-form-limit.mode: raw\|envelope`）⇒ **favorite 缺省 = off ⇒ 容器上限与闸门都不生效 ⇒ 零触碰、行为逐字节不变** ③ 🔴 **闸门是 `HandlerInterceptor` 不是 servlet `Filter`** —— Filter 在 DispatcherServlet **之外** ⇒ 抛的异常到不了 `@RestControllerAdvice` ⇒ envelope 模式无法渲染 ④ 顺带订正 `service/common/pom.xml` 的 **spring-web / spring-test 硬编码 7.0.0**（与 starter-web 的 7.0.9 错配 ⇒ `NoClassDefFoundError: ApiVersionHolder`）⇒ 改为跟随 BOM ⑤ 🟢 证据强度首次达 **真容器 + 真 Spring MVC + 真 advice + 变异测试**（raw 5/5 · envelope 4/4 · 模式 8/8 · 变异 M1/M2 均转红）· `check-tomcat-limits.py` 新增第四旋钮 + **favorite 零触碰守卫** ⇒ EXIT=0 ⑥ 残留（有界，修前已存在）：**鉴权与闸门的先后**（Go 先鉴权后解析 / RAG 先解析后鉴权）⇒ 记录不改 |
| 2026-10-25 | ✅ **第十一轮（favorite 零权限重申 ⇒ 推翻第九轮的「永久阻塞」结论）** —— 用户重申：「**你没有 favorite 的所有权限**」⇒ ① 🔴 **推翻自己第九轮的结论** —— 那个结论只覆盖「逐模块改 `application.yml`」这一条修法；本轮实测**共享层修法存在**：① `service/common` 里一个 `WebServerFactoryCustomizer`（`@Order` > Boot 的 `TomcatWebServerFactoryCustomizer.getOrder()==0`）⇒ `connector.setMaxParameterCount(-1)`（`Connector.java:538`；`-1` = 不限 ⇒ `Parameters.java:79/247`）② 一个共享 Filter 复刻 go-zero `GetFormValues`（`net/url` 解析层 + **2048 个非空值**计数，可从 `article-api` 的 `GoStyleGetFormValuesFilter` 移植）⇒ **10 个 api 模块零代码改动 ⇒ favorite 零触碰**（前提已逐条实测：10/10 依赖 `service/common` + 10/10 扫 `com.rag.common`）⇒ ⇒ **X-6 不是永久阻塞，也不需签字认账** ⇒ 落 **出路 D** ② ✅ **连带影响面复核**（§二·十一 ④ 之二）—— X-4（favorite 半边）**仍阻塞**（必须删 favorite 自己的 10 处调用）· X-9（hot/article 两份可收敛、**favorite 那份永久留作复制品**）· X-7（**只读库不改代码 ⇒ 不受影响，仍可推进**）③ 本表**已无「等用户拍板」的技术项**；唯一真决策项 = T-6（需先撕 hot 域 `SD-HOT-API-006`） |
| 2026-10-25 | ✅ **第九轮补（X-6 global 侧展开）** —— 两层限制语义精确化：Go **数非空值**（`httpx/util.go:15` `maxFormParamCount=2048` + `:44-45` 跳过空值 + `:48` `n < 2048` ⇒ 最多 2048 个非空值）vs Tomcat **数参数总数** ⇒ **残余窗口 = 总数 ≥ 2048 且 非空值 ≤ 2048**。🔴 **修法必然要动 `favorite-api`**（`max-parameter-count` 是 **per-app** 连接器设置，**无法在 `service/common` 统一改**；实测 **10 个 api 模块全部已配 2048、零遗漏**）⇒ ⇒ **X-6 不是「待拍板的技术项」，而是「favorite 写权边界」问题** ⇒ 登记等用户定（出路 A 放开 favorite / B 制造分叉 / C 签字认账）<br>⚠️ **2026-10-25 第十一轮已改判** —— 出路 A 撤下、出路 B 放弃、**出路 C（签字认账）也放弃** ⇒ 改判为「**存在共享层修法 ⇒ 可修，favorite 零触碰**」（见上一行）⇒ ⇒ **第九轮那句「修法必然要动 `favorite-api` ⇒ 永久阻塞」作废**
| 2026-10-25 | ✅ **第九轮（X-2 审完 + X-4 确证）** —— ① **X-2**（建表责任交叉 + client 契约）**从「均未审」变成「已审完」**：A 组交叉但无冲突（Go 两侧 `User` struct 逐字段相同）；B 组 **10 域全覆盖零漂移**；🟡 **产出 1 条此前无人登记的真漂移** —— `users_user.extra_info` 写路径仍按 jsonb（`AdminDomainService.java:962` + `UserEntity.java:129`），但该列**早已是 TEXT**（`01_create_user.sql:41` + `:103`，且 admin-rpc 自己就在重放这份 DDL）⇒ **代码注释的前提已失效** ⇒ 含 `\u0000` 时 Go 成功 / RAG 5001 ② **X-4 实测确证**：`like` 域 Go 侧 6 个文件引用 `communityfact`、RAG 侧 **0**（**另起炉灶**）；`favorite` Go 0 / RAG 10（⛔ 不碰）③ ⚠️ **订正我自己第四轮 B2 的错误结论**（「like/comment/favorite 三域都接了线」**作废**）
| 2026-10-25 | **第七轮（X-5 确证 + X-10 去重）**：① 🔴 **X-5 由「未展开」升级为「已确证」** —— 一次性探针实测：grpc-java 1.63 **会**调裸 handler 的 `startCall()`，但**不会**把 `onHalfClose` 派发给它返回的 `ServerCall.Listener` ⇒ 「处理函数睡 10 秒」**从未发生** ⇒ task + follow 两个域的同名测试**空转**（绿，但验证的是「取消一个空调用」而非「打断在飞处理」）⇒ 波及面由「task 域」扩到「task + follow 两个 rpc 域」⇒ 见 §二·八 ② ⚠️ **X-10 去重** —— 复查发现 X-10 早在 2026-10-23 已签为 message 域 `SD-2026-10-23-06` ⇒ **撤销本域重复登记的 `SD-2026-10-25-03`**（改为指针） |
| 2026-10-25 | **首版**。`align/global` @ `58e8314` 产出。收录 7 条跨域不变量状态；新增 **X-8 真源分层（L1 项目层 / L2 go-zero 框架层）** —— 本轮实测发现，影响全部域的对照方法 |
| 2026-10-25 | **第四轮（B2 / X-3 闭合）**：**「第三个仓库」已定位** —— 链路末端**不是仓库**，是**仓外 DC 平台**的 `POST /v1/events`（`DC_PLATFORM_EVENT_URL` + `DC_PLATFORM_SERVICE_TOKEN`，路径必须以 `/v1/events` 结尾、token ≥ 32，见 Go `service/common/communityfact/wire.go:83-91`）。仓内两侧**都有**：`service/common/communityfact` **4 文件 ↔ 4 类**；authority 端点路径 1:1；调度形状 **1s / 16 / 10s / 单条失败即 break** 四项逐条对齐；`like` / `comment` / `favorite` 三域**都接了线**。⚠️ **2026-10-25 第九轮订正**：本条末句「`like` / `comment` / `favorite` 三域**都接了线**」⇒ **实测不成立** —— `like` 域 RAG 侧对 `com.rag.common.communityfact` 是 **0 引用**（它**另起炉灶**实现了自己的 outbox+stream+scheduler）⇒ 准确表述：**`comment` 与 `favorite` 接了共享组件；`like` 域没有；`article` 多了一处 Go 侧没有的用法** ⇒ 详见 §二·十（X-4）。⇒ ⚠️ **X-3 本身（末端 = 仓外 DC 平台 / 共享组件契约）仍成立** —— 订正的只是「哪些域接了线」这一句。 唯一差异 = **部署形态**（Go 独立 `cmd/fact-dispatch` vs RAG 进程内 `@Scheduled`）⇒ 表达层差异，不修。⇒ **X-3 关闭**（不是漂移，是架构事实 + 仓外边界） |
| 2026-10-25 | **第三轮（B3 / X-7 部分闭合）**：① 查出**已有的域预设本身已失效**（users 容器改名、message 容器+库+用户三重失效）⇒ 修好并给脚本加按域的 `pg_user` ② **扩到 4 个域**（新增 `comment`）⇒ **可跑的域 1 → 4** ③ 跑出**真结论**：task ✅ 11 列逐字相同、message ✅ 37 列逐字相同（+ DEFAULT 6 处=已知刻意保留）、users/comment 各有**环境缺口**（Go 侧对应表未被 AutoMigrate）④ 🔴 **新增 X-10**：`message` 域索引 `idx_notification_recipient_read` **两侧列不同**（Go 单列 `is_read` / RAG 复合 `recipient_id,is_read`）⇒ 根因是 Go 侧 `priority:1` 写到了另一个索引名 ⇒ **落在 message 域的迁移脚本 ⇒ global 只记账不改别域代码，等用户拍板** ⑤ 仍缺 5 个域（article/like/hot/follow/favorite）的**双侧库环境** ⇒ 缺 runner ⇒ 登记为待办 |
| 2026-10-25 | **第二轮**：新增 **X-9** —— 「Go 是否读 body」判据存在 **3 份生产复制品**（favorite / hot / article）。T-1 修复前 RAG **内部自相矛盾**（闸门说不读 / 解析器说要读）⇒ T-1 修复后已一致。⚠️ **根治需各域自己收敛**（调用唯一真源），global 只记账不改别域代码 |
