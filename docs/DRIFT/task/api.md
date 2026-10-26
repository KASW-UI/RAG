<!-- @new 2026-10-23 · task-api 组件级语义对照 · 由 `align/task` 工作树产出 -->

# `task-api` 组件级语义对照台账

> **基线 commit**：`8422116`（`do-my-own-gh`）· **工作树**：`RAG.worktrees/task`，分支 `align/task`
> **排序**：本组件在 `task-rpc` **之后**对照（`docs/Worktree.md` §2.2「rpc → common → api → proto 消费」硬约束）。
> ⚠️ **`task-rpc` 未验对之前，api 侧的 ✅ 只是条件性的** —— 见 `rpc.md` §五的证据边界。
>
> **Go 出处基准**：`Sea-RideTheWind_golang/service/task/api/`（全部 8 个文件，215 行）+ **go-zero `v1.10.2` 真实源码**
> （`rest/httpx/responses.go`、`rest/internal/errcode/grpc.go`）。
> ⚠️ **本台账没有沿用任何既有结论**：go-zero 的错误响应形状是本轮**直接读模块缓存源码**核出来的，
> 不是从别的域的台账抄的（AGENTS.md §3.1：不要拿现有 RAG 代码或注释当对照基准）。
>
> 🔴 **2026-10-23 22:5x 已追加双侧活体对拍**（Go 栈 + RAG 栈同时起、同一串 43 条请求）。
> 对拍**抓出一条静态审查完全看不到的差异**（DRIFT-TASK-API-003），详见 **§六**。
> 对拍同时把本组件的证据强度从「真实 MVC 管线」升到 **「双侧活体对拍」**。

---

## 〇、结论

| 项 | 结论 |
|---|---|
| 语义等价性 | ✅ **EQUIVALENT**（1 条端口漂移已修；1 条 Content-Type 无关性漂移已修） |
| 已修 | **3 条**（DRIFT-TASK-API-001 端口 / -002 `Go:` 行号引用失准 / **-003 Content-Type 无关性**） |
| 对拍新发现 | **1 条 OBSERVABLE DIFFERENCE**（DRIFT-TASK-API-003）⇒ **2026-10-24 已修**，见 §六 |
| 未修 | 0 条语义漂移（余 1 条**全域共性** DRIFT-TASK-API-004，按 §三·三 不在本域单独修） |
| 证据强度 | ✅✅ **双侧活体对拍**（2026-10-24 重跑，44 条 MATCH 44/DIFF 0）· ✅✅ **真 Tomcat 守门**（`TaskApiContentTypeTest` 12 条 + 变异测试）· ✅✅ **Nacos 真注册中心**（2026-10-24，注册/注销实测，见 §四·六）· ✅ 真实 MVC 管线（MockMvc，20 条）· ⚠️ gRPC 边界在单测里是 mock |
| 对拍结果 | **44 用例：MATCH 44 / DIFF 0**（判定口径：状态码 + **归一后**的 Content-Type 媒体类型 + nosniff + 响应体逐字节） |
| 测试 | **97 tests / 0 failures / 0 errors / 0 skipped**（task-api；原 78 + 新增 12 ⇒ **2026-10-27 阶段 2**：新增 `TaskApiFormLimitReachabilityTest` **6** 条（form 闸门语义可达性 + 端口段）+ `TaskApiContentTypeTest` 的端口段守门 **1** 条 ⇒ 90 + 7 = **97**） |

### 〇·一、本组件的守门测试清单（2026-10-24 复核补登）

> ⚠️ 此前台账**只点名了部分测试类**，其余在册但没登记 ⇒ 复核时判定为「覆盖缺口」。
> 本节把 task-api 的**全部**测试类登记在此，每个都写清它**锁住哪一条契约**。

| 测试类 | 条数 | 锁住的契约 | 类型 |
|---|---|---|---|
| `TaskApiContentTypeTest` | **13** | **DRIFT-TASK-API-003**：任何 Content-Type（含 `form-urlencoded` / `multipart`）的 JSON body 都应被收下（Go `gettaskhandler.go:19` 裸 decoder）＋ `spring.servlet.multipart.enabled` **真的**被解析成 false<br>🆕 **+1 条端口段守门** `bindsToDedicatedPortSegment`（2026-10-27 阶段 2 加） | 变异测试 |
| `TaskApiMvcPipelineTest` | **20** | **真实 MVC 管线**（非 `standaloneSetup`）：请求体转换器插在 Boot 的 Jackson 转换器**之前** ＋ 共享的 `GoStyleJsonBodyConverterConfig` **真的被排除** ＋ 404 裸 `text/plain` / 405 裸状态码 | 装配 + 端点级 |
| `TaskApiWiringTest` | **5** | **装配层**：HTTP 3s 超时中间件 + 四道请求保护闸门**真的在容器里**（Go `rest/config.go:12-23` tag 默认全开）＋ 端点映射在 `/taskcenter/v1/task/get` | 装配层 |
| `GetTaskReqParsingTest` | **23** | **解析层**（用 Jackson 3 = Boot 4 MVC 边界同一个 mapper）：`{"id":"123"}` 接受 + `TrimSpace`（Go `types.go:24-25`） | 契约层 |
| `GrpcStatusHttpMappingTest` | **23** | gRPC code → HTTP 状态码的映射（Go `responses.go:143-160`） | 契约层 |
| `TaskGetServiceTest` | **7** | `GetTask` 的领域逻辑（合并任务定义、pack/unpack 进度） | 领域层 |
| `TaskApiRawBodyConfig` / `GoStyleRawBodyArgumentProcessor` | — | （生产代码）请求体转换器本体 | — |
| `TaskApiFormLimitReachabilityTest` | **6** | **form 值上限闸门在本域的语义可达性**（2026-10-27 阶段 2 新增）：Go 侧 handler 用**裸** `json.NewDecoder(r.Body)` ⇒ `r.Form` 从不被求值 ⇒ go-zero 的 `maxFormParamCount=2048`（`rest/httpx/util.go:15`）**在本域不可达** ⇒ 本域闸门必须 `mode: "off"`（`task-api/application.yml:213`）—— 断言 ① 生效模式必须是 OFF（**直接**断言 `GoStyleFormValueLimitInterceptor#mode()`）② >2048 个非空 query 值 ⇒ 200 ③ `?a=%zz`（畸形转义）⇒ 200 ④ `?a=1;b=2`（分号）⇒ 200 ⑤ 阳性对照（无 query ⇒ 200 + 正常 task 载荷）<br>🆕 **+1 条端口段守门** `bindsToDedicatedPortSegment`（2026-10-27 阶段 2 加） | 真 Tomcat + 变异测试（改回 `mode: raw` ⇒ 4 条红） |
| `RealTomcatPort`（`…/task/api/support/RealTomcatPort.java`） | — | （**测试支撑类**，非用例）真 Tomcat 的**端口选取 + 双绑定自检**：本域专用段 **21800–21899** ⇒ 避开 favorite 的 21300–21399 / 探针的 21400–21599 / favorite 脚手架的 21950–21999 / **OS 临时段 49152–65535**（Docker 随机发布端口就在那一段）<br>⚠️ 复制自 favorite 域同款（`service/common` **不发布 test-jar** ⇒ 业务模块 test scope 拿不到共享层测试类 ⇒ 只能复制） | 支撑类 |

> ⚠️ **本节新增的 `RealTomcatPort` 会被 `check-ledger-selfcheck.py` 第 5 层报成「台账未点名的测试类」—— 这是**工具口径**缺口，不是台账缺陷**：
> 该层的 `CLASS_RE` 只认 `` `…Test` `` / `` `…ContractTest` `` / `` `…GateTest` `` / `` `…IT` ``（工具自己的注释见 `tools/check-ledger-selfcheck.py:204-207`：
> 「**永远无法被判定为「已点名」** ⇒ 第 5 层把「台账已登记」误报成「覆盖缺口」」）⇒ **支撑类**（本仓三个域各一份的端口选取器）**永远**会被列进未点名清单。
> ⇒ 本条只是**提示级**（层 5·6 **不算失败**、门禁 `EXIT=0`）⇒ **登记在此**，工具侧的判据修改归**共享工具**（不在本 lane 写权内 ⇒ 只登记、不改）。

⚠️ **「装配层」与「装配生效」是两条不同的断言**（`TaskApiWiringTest` 答「装上了吗」、
`TaskApiMvcPipelineTest` 答「生效了吗」）⇒ 两条缺一不可，别只留一条。

---

## 一、逐条对照（4 条：001 / 002 / 003 已修 · **004 未修**）

### 🔴 DRIFT-TASK-API-001 · 监听端口 18809 ≠ Go 的 18886

| | 值 | 出处 |
|---|---|---|
| Go yaml | **18886** | `service/task/api/etc/task.yaml:3` `Port: 18886` |
| Go 编排 | **18886** | `manage.sh:842` `-p 18886:18886 -p 19005:9005` |
| RAG（修复前） | 18809 | `application.yml` `port: ${TASK_API_PORT:18809}` |

**18809 的出处**：`Infrastructure.md:89` 的「Java 版业务进程端口分配」表。
该表**自己写明**是预占、待校验：

> `Infrastructure.md:91`：「端口分配从 18889 / 18891 / 18893… 这种增量是 Go 版 manage.sh 1179 行的 1:1 复刻；
> **Java 版只是先按这个号段预占，等具体域实装时再校验**。」

🔴 **该表与 Go 的真实值对不上**（同一份文档 §3 给 hot 的 18897 vs Go 的 8893 亦然）——
它是照等差序列**推算**出来的，不是从 Go 抄的。`task` 域现已实装，正是它自己说的那个校验点。

**本仓既有先例**（已实装的域一律用 Go 原值）：

| 域 | Infrastructure.md 预占 | 实际采用 | Go 真实值 |
|---|---|---|---|
| article | 18889 | **8889** | 8889 ✅ |
| comment | 18891 | **8888** | 8888 ✅ |
| message | 18803 | **8892** | 8892 ✅ |
| like | 18801 | **18887** | 18887 ✅（尤其关键：预占值被否掉了） |
| **task** | 18809 | ~~18809~~ → **18886** | 18886 ✅ |

**修法**：`port: ${TASK_API_PORT:18886}`，原值仍可用环境变量传回。
**修法性质**：这是**部署形态**而非 wire 契约（客户端按 URL 调用，不把端口写进协议），
但它决定服务发现/编排/网关的连通性，属可观察行为，故按 DRIFT 记而非签字偏离。

---

### 🔴 DRIFT-TASK-API-002 · 4 处 `Go:` 行号引用与 go-zero 真实源码对不上

AGENTS.md §3.3.2 硬要求：**任何以「Go: …」措辞出现的断言，必须指向真实消费点**，否则不得用该措辞。
本类的 javadoc 与日志文案引用了 `responses.go` 的行号，但**行号与文件路径都不准确**：

| 位置 | 原引用 | go-zero v1.10.2 实际 | 修后 |
|---|---|---|---|
| 类注释 | `rest/httpx/responses.go:143-160` | `doHandleError` 整体 = **142-173** | `142-173` |
| gRPC 分支 | `rest/httpx/responses.go:147-149` | `else if errcode.IsGrpcError(err)` = **150-153** | `150-153` |
| 兜底分支注释 | `responses.go:151-153` | `} else {` + `http.Error(..., StatusBadRequest)` = **154-155** | `154-155` |
| 运行日志 | `Go responses.go:152` | **155** | `go-zero v1.10.2 rest/httpx/responses.go:155` |

⚠️ 原值 152 恰好落在注释行 `// it hides the rpc error headers.` 上 ——
**照着它去读，读到的是一句英文注释而不是行为**。

**性质**：不改变运行时行为，但违反 §3.3.2，且会让后续复核者**按错误的行号复核**。
已全部校正为带版本号的完整路径。

---

## 二、逐条对照结论（已核为一致的部分）

### 路由与鉴权

| 行为点 | Go 出处 | RAG 出处 | 结论 |
|---|---|---|---|
| `POST /taskcenter/v1/task/get` | `handler/routes.go:16-25`（prefix `/taskcenter/v1` + path `/task/get`） | `TaskController` `@RequestMapping("/taskcenter/v1")` + `@PostMapping("/task/get")` | ✅ |
| **零鉴权** | `routes.go` 只有 `rest.WithPrefix`，**无 `rest.WithJwt`/`WithJwtTransition`** | 无任何 interceptor/SecurityFilter，类注释显式说明「引入 JwtAuthInterceptor 是 L2 级行为变更」 | ✅ |
| uid 来自请求体而非 token | `types.GetTaskReq{Userid UserIDParam \`json:"id"\`}` | `GetTaskReq` `@JsonProperty("id")` | ✅ |

### `id` 参数解析（`types.go` ↔ `GetTaskReq`）

> 这是本域最容易写错的一段，逐条核。

| 输入 | Go `UnmarshalJSON`+`Int64()` | RAG | 结论 |
|---|---|---|---|
| `null` | `""` → `Int64()` 报 `user_id invalid` | `normalize(null)` → `""` → 同 | ✅ |
| `"  12  "` | TrimSpace → `"12"` → 12 | `s.trim()` → 同 | ✅ |
| `123` | 字符串失败 → `json.Number` → `"123"` | Jackson `Integer` → `String.valueOf(longValue)` | ✅ |
| `true` | 两个 Unmarshal 都失败 → `id must be a string or integer` | `Boolean` → `BadIdTypeException` → handler 用 `findGoText` 取出**原文案** | ✅ |
| `[1,2]` / `{...}` | 同上报错 | Jackson 传 List/Map → 抛 `BadIdTypeException` | ✅ |
| `1.5` | `json.Number("1.5")` → `ParseInt` 失败 → `user_id invalid` | Jackson `Double 1.5` → `"1.5"` → `parseLong` 失败 → 同 | ✅ |
| `1.0` | `json.Number("1.0")` → `ParseInt` 失败 → `user_id invalid` | `Double 1.0` → `"1.0"` → 失败 → 同 | ✅（两侧都拒绝，**不是**「1.0 应当等价于 1」） |
| `"0"` / `"-5"` | `uid <= 0` → `user_id invalid` | `uid <= 0` → 同 | ✅ |
| `"abc"` | `ParseInt` 失败 → `user_id invalid` | 同 | ✅ |
| 字段缺失 | `""` → `user_id invalid` | 默认 `""` → 同 | ✅ |

### 错误响应形状（**本轮直接读 go-zero 源码核出**）

| 行为点 | go-zero v1.10.2 出处 | RAG | 结论 |
|---|---|---|---|
| 未注册 errorHandler 时的默认分支 | `rest/httpx/responses.go:145-159` | `GlobalExceptionHandler` | ✅ |
| 非 gRPC 错误 ⇒ **400** `http.Error(msg, 400)` | `:154-155` | `handleAny` → `plainText(BAD_REQUEST, msg)` | ✅ **是 400 不是 500** |
| gRPC 错误 ⇒ `errcode.CodeFromGrpcError` 的 **15 分支**映射 | `rest/internal/errcode/grpc.go:12-42` | `httpStatusOf` 逐条对应 | ✅ 17 个 `Status.Code` + default 500 全覆盖 |
| 响应体 `err.Error()`（grpc-go 文本） | `:153` `http.Error(w, err.Error(), ...)` | `goStatusText` 复刻 `rpc error: code = X desc = Y` | ✅ |
| `Content-Type: text/plain; charset=utf-8` + `X-Content-Type-Options: nosniff` + 末尾换行 | `http.Error` 的标准行为 | `plainText(...)` | ✅ |
| 本域**未注册** errorHandler（决定上面形状的前提） | `api/task.go:28-33` 只 `Use(NewHTTPMiddleware)`，无 `httpx.SetErrorHandler` | 未注册 | ✅ |

⚠️ **错误文案本身**：Go `encoding/json` 与 Jackson 的解析错误措辞不同（如 `invalid character 'x' ...` vs
`Unrecognized token 'x'`）⇒ **状态码与 Content-Type 一致，**文案**逐字不同**。
这是全仓 api 域的共性差异，**不在本域单独修**（单独修会与其它域不一致）。

### gRPC client 调用

| 行为点 | Go 出处 | RAG | 结论 |
|---|---|---|---|
| client 超时 **2000ms**（yaml 未配 ⇒ zrpc 默认） | `zrpc/config.go:31` `Timeout int64 \`json:",default=2000"\`` | `TaskRpcClient.DEFAULT_CALL_TIMEOUT_MILLIS = 2000` | ✅ |
| 取较小值（下游 deadline 与自身预算取 min） | — | `withDeadline` `Math.min(...)` | ✅ 更严一侧，不放宽 Go 的语义 |
| 熔断开启 | go-zero `zrpc` client 内建 breaker | `rag.protection.breaker.enabled: true` + `GoStyleGrpcClientBreakerInterceptor` | ✅ |
| 零下游 gRPC 依赖 | `svc/servicecontext.go:24-33` 只有 `TaskCli`（Points/User 客户端被注释） | rpc 只引 server starter，**刻意不引** client starter | ✅ |

### 请求体 Content-Type

- Go 的 handler **不调 `httpx.Parse`**，直接 `json.NewDecoder(r.Body)` ⇒ **完全不校验 Content-Type**。
- RAG 用自己的 `TaskApiJsonBodyConfig`（`canRead` 恒 true，认领 `MediaType.ALL`），
  并在 `TaskApiApplication` 的 `excludeFilters` 里**显式排除**共享的 `GoStyleJsonBodyConverterConfig`
  （后者要求字面量 `application/json`，那是 like/message 等**调了 `httpx.Parse`** 的域才需要的形状）。
- ⇒ ✅ 一致，且这个排除是**必需的**（漏了会把 task 域变成「必须带 Content-Type」）。

---

## 二·五、双侧活体对拍（2026-10-23 22:5x）

**性质**：AGENTS.md §3.3「凡写『与 Go 一致』，必须有两侧同时跑的探针」的落地。

### 搭法（照 `tools/difftest/run.sh` 的既有方法，未重新设计）

| | Go | RAG |
|---|---|---|
| rpc | `:19005`（`CGO_ENABLED=0 go build` 真二进制） | `:59005`（`java -cp`，真 PG + 真 Redis + 真 Kafka） |
| api | `:18886` | `:58886` |
| PG | 容器 `taskprobe-postgres:55433` / 库 `task_go_db`（**GORM AutoMigrate 建表**） | 同一容器 / 库 `task_rag_db`（**Flyway 建表**） |
| Redis | `:56380`（两侧共用，矩阵前 `FLUSHALL`） | 同左 |
| Kafka | `:59992` KRaft，5 个 topic 预建（**goka topic manager 连不上会 `log.Fatal`**） | 同左 |
| 服务发现 | 两侧都摘掉（Go `Endpoints` 短路 / RAG `static://` + 关注册） | 同左 |

⚠️ Go 配置是**副本**（[`tools/difftest/conf/go/task-api.yaml`](../../../tools/difftest/conf/go/task-api.yaml)），
**冻结真源仓库零写入**；`Log.Mode: console` 是必需的（真源默认落文件）。

### 结果：**44 条用例**（2026-10-24 重跑，含本轮新增的「不带 Content-Type」用例）

| 维度 | 结果 |
|---|---|
| **判定口径** | 状态码 + **归一后**的 Content-Type 媒体类型 + `X-Content-Type-Options` + **响应体逐字节** |
| **MATCH / DIFF** | ✅ **44 / 0**（脚本 `EXIT=0`，可直接当门禁） |
| **响应体逐字节一致** | ✅ **44 / 44** |
| 状态码一致 | ✅ **44 / 44**（DRIFT-TASK-API-003 修掉后，那 2 条不再红） |
| `X-Content-Type-Options: nosniff` | ✅ 44 / 44 一致 |
| `Content-Type` header **原值** | ⚠️ **43 条不同**（**格式**差异，见 DRIFT-TASK-API-004）⇒ 归一后一致，**单列不藏** |

覆盖：`id` 参数解析 18 类（null/缺失/字符串/数字/布尔/数组/对象/浮点/超长/带符号…）、
DB 回源 5 组（含无行、status/progress 为 NULL）、**Redis 缓存命中 10 组手工 crafted 值**
（2 段 / 4 段 / 空串 / 非数字 / **负数** / 超 target / status 含 `|`）、路由层 3 组、
**Content-Type 矩阵 5 组**、畸形 JSON、空 body、**完全不带 Content-Type 1 组**。

### ⚠️ 本轮把对拍脚本从「只能人读」改成「能当门禁」（三处改造）

1. **补上 §2.7 那个一直没实现的用例**。原脚本 §2.7 只有一段注释：
   「⚠️ 不能用 urllib：它会在没有显式 Content-Type 时**自动补** `application/x-www-form-urlencoded`…」
   ⇒ **用例本身根本没写**，但上一版台账的 API-003 表里却有一行「不带 Content-Type | 200 | 200 ✅」
   ⇒ **那一行当时没有脚本证据**。本轮用 `http.client` 实现（它只自动补 `Host`/`Content-Length`，
   **不补** `Content-Type`），用例数 43 → **44**。
2. **有 DIFF 必须返回非零**。原脚本结尾恒 `sys.exit(0)` ⇒ **43 条全红也返回 0**，不能当门禁。
   现在对齐 `parity-task-kafka.py` 的 `return 1` 约定。
3. **按媒体类型归一后再判定**。原先 Content-Type 的**格式**差异（`application/json; charset=utf-8`
   vs `application/json`）直接把 43/44 条染红 ⇒ 真差异会被淹没。
   现在：媒体类型本身不同 ⇒ 仍算 DIFF；只是 charset/空白不同 ⇒ 归入
   **「已知全域共性」单列**（DRIFT-TASK-API-004），不计入 DIFF、但**必须打印出来**。

### 🔴 静态审查完全看不到、只有对拍才抓到的一条（**已修**）

**DRIFT-TASK-API-003**：Go 的 handler（`gettaskhandler.go:19` 的 `json.NewDecoder(r.Body)`，
**没有** `httpx.Parse`）对 `Content-Type` **完全无感**；
RAG 侧 Spring 会对 `form-urlencoded` 的 POST body **参数化重建**，对 `multipart` 的 POST body
**被 resolver 抢先消费** ⇒ 两条都返回 400。
⇒ **2026-10-24 已修**，根因（含上一版台账的**误判**）与修法见 §六。

### ⚠️ 对拍方法论上的一条教训（已写进 `../../Pitfalls/task/rpc.md`）

第一版探针用 Python `urllib`，它在「没显式给 Content-Type」时会**自动补** `application/x-www-form-urlencoded`
⇒ 把「不带 Content-Type」测成了「带 form-urlencoded」，一度得出「两侧行为一致」的假结论。
用 `curl -H 'Content-Type:'` 复验才发现真相。**探针自己也会撒谎。**

## 三、证据边界

1. ✅ **双侧活体对拍已做**（2026-10-23 首做 43 条，**2026-10-24 重跑 44 条**，见 §二·五）：
   Go `task-api:18886` + `task-rpc:19005`
   与 RAG `task-api:58886` + `task-rpc:59005` 同时起，请求矩阵 **MATCH 44 / DIFF 0**。
   ⚠️ 但覆盖面**只到 HTTP wire 层**：经由 `GetTask` 这条链路验证了
   **Redis HGETALL → PG 回源 → packProgress/unpackProgress → 任务定义合并 → gRPC 序列化 → HTTP 响应**。
   **Kafka 消息链路不在 HTTP 对拍面内**（那条链路由
   [`rpc.md` §四·三](rpc.md#四三kafka-端到端对拍两侧灌同一批-raw-日志比最终态2026-10-23-234x-实测) 单独对拍）。
   ⚠️ **本轮新增了一键编排** [`tools/difftest/run-task-http-parity.sh`](../../../tools/difftest/run-task-http-parity.sh)
   （原先只有手工步骤，Kafka 侧有一键脚本而 HTTP 侧没有）。
2. ⚠️ **gRPC 边界在单测里是 mock**：`TaskGetServiceTest` / `GrpcStatusHttpMappingTest` 用 stub，
   验证的是「拿到某个 gRPC code 后映射成什么 HTTP 响应」，**不是**「对着真的 task-rpc 调」。
   ⚠️ 但 **gRPC 单组件对拍已单独做**（`rpc.md` §四·三 之前的 gRPC runner，7/7 一致，
   含 3 组 HTTP 面永远走不到的 `INVALID_ARGUMENT`）。
3. ✅ **真 Tomcat 的 400 响应头已守**（2026-10-24）：`tools/difftest/check-plaintext400-headers.sh`
   已扩到 **task-api**（畸形 JSON / 空 body 两条）⇒ 状态码 400、`Content-Type: text/plain;charset=utf-8`、
   `X-Content-Type-Options: nosniff` **三条断言全过**，并与 Go 真源同请求的响应头做了对照打印。
   ⚠️ 该脚本里 task 段与 user 段**互不影响**：某域栈没起时判 **SKIP** 而不是 FAIL。
4. ✅ **Content-Type 无关性有真 Tomcat 守门测试**（2026-10-24 新增
   [`TaskApiContentTypeTest`](../../../service/task/task-api/src/test/java/com/rag/task/task/api/TaskApiContentTypeTest.java)）：
   6 种 Content-Type + 不带 Content-Type 全要 200；**带阳性对照**（畸形 JSON / 空 body 仍须 400）。
   ⚠️ **变异测试已做**：撤掉处理器替换 ⇒ 1 条红；把 multipart 开关改回 `true` ⇒ 2 条红 ⇒ **不是假绿**。
5. ⚠️ 本轮**未重审** `service/common`（`HttpMiddlewareFilter` / breaker / maxConns 等），
   其结论沿用 `docs/DRIFT/common/audit.md`。

---

## 四、待办

1. ~~跑 `tools/difftest/check-plaintext400-headers.sh` 覆盖 task-api~~ ⇒ **2026-10-24 已做**（见 §三·3）。
2. ~~`task-api` 的 `NacosDiscovery` 在真注册中心下的注册/注销行为未验~~ ⇒ **2026-10-24 已验**（见 §四·六）。
3. ~~**DRIFT-TASK-API-003 的彻底修法**~~ ⇒ **2026-10-24 已修**（见 §六）。
4. **跨域闭环对拍**：「Kafka 写入 → `task-api` 读到」——
   注意两侧的读写 Redis key **本来就不一致**（见 `TaskRedisKeys` 类注释），
   所以这条闭环在 Go 侧也是「读不到自己写的缓存」的，**不要当成 RAG 的 bug 修**。
5. ⚠️ **DRIFT-TASK-API-004（`Content-Type` header 的 charset/空白格式）**——
   **全域共性**（article/hot/like/message/task 五域同样记着），
   **不在本域单独修**（单独改会造成「本域与其它域不一致」）。
   本轮已把对拍脚本的判定口径改成「归一后比媒体类型」，让这条共性**不再淹没真差异**。

---

## 四·六、NacosDiscovery 真注册中心行为（2026-10-24 实测）

**性质**：AGENTS.md §3.3「框架机制要运行证据」——
此前 `task-api` 的注册/注销路径**从没跑过**：所有测试都是
`spring.cloud.nacos.discovery.register-enabled=false` 或指向不可达地址
⇒ **装配层被整体关掉**。

**做法**：起一个**独立**的 Nacos 容器（`taskprobe-nacos`，HTTP `:49849` / gRPC `:50849`），
把 `task-api` 真注册上去，再用 Nacos open API 查注册表。

⚠️ **为什么自己起一个**：本机已有 `Sea-nacos`（Sea 栈的，**不许动**）
与 `nacos-standalone`（用户自己的开发实例）⇒ 往用户的实例里注册测试实例是**对用户环境的副作用**。

**结果（`EXIT=0`）**：

| 步骤 | 期望 | 实测 |
|---|---|---|
| 起 `task-api`（`register-enabled=true`） | 注册表出现自己的实例 | ✅ **第 3s** 查到：`ip=192.168.43.239 port=58887 healthy=true enabled=true ephemeral=true clusterName=DEFAULT`，`metadata` 里带 `preserved.register.source=SPRING_CLOUD` |
| 停进程 | 实例从注册表**消失**（不留幽灵） | ✅ **第 1s** 就消失了（`hosts: []`） |

⇒ **注册与注销都符合预期**，且注销是**即时的**（不靠心跳超时）。

**复现**：
```bash
./tools/difftest/run-task-nacos-discovery.sh
```

#### 🔴 本轮实测踩到的两个「症状离病根很远」的坑

1. **Nacos 2.x 的客户端按「HTTP 端口 + 1000」推算 gRPC 端口**
   （nacos-client 3.x 的 `NamingGrpcClientProxy` 连的就是这个端口）。
   ⇒ 宿主机映射必须用 `49849+1000 = 50849`；我第一次映射成 `50949` ⇒
   客户端连不上 ⇒ 注册报 **`ErrCode:-401 Client not connected, current status:STARTING`**
   ⇒ 而且 `failFast=true` ⇒ **整个应用启动失败**。
   ⇒ 症状是「app 起不来」，病根却在**端口映射**上。
2. **Nacos 的 readiness 端点返回的是 `OK`，不是 `UP`**
   ⇒ 探针里写 `grep -q UP` 会永远等不到（本轮实测空转 120 秒）。

⇒ 这两条已写进
[`tools/difftest/run-task-nacos-discovery.sh`](../../../tools/difftest/run-task-nacos-discovery.sh) 的注释里。

---

## 五、相关文件

| 关系 | 文件 |
|---|---|
| 本域 rpc 台账（**必须先读**） | [`rpc.md`](./rpc.md) |
| 本域 proto 台账 | [`proto.md`](./proto.md) |
| 踩坑 | [`../../Pitfalls/task/api.md`](../../Pitfalls/task/api.md) |
| 有意偏离 | [`../../Intentional_Deviations/task/api.md`](../../Intentional_Deviations/task/api.md) |

---

## 六、对拍新发现（**已修**）

### ✅ DRIFT-TASK-API-003 · JSON body 配 `form-urlencoded` / `multipart` 时 Go 收、RAG 拒

**性质**：SEMANTIC MISMATCH（状态码层）。**对拍抓出，静态审查不可见。**
**状态**：🔴 → ✅ **2026-10-24 已修**（双侧活体对拍 **MATCH 44 / DIFF 0** 复验）。

| Content-Type（body 均为 `{"id":1001}`） | Go | RAG（修前） | RAG（**修后**） |
|---|---|---|---|
| `application/json` | 200 | 200 ✅ | 200 ✅ |
| **不带 Content-Type** | 200 | 200 ✅ | 200 ✅（本轮补上了**真实**的这条用例） |
| `text/plain` | 200 | 200 ✅ | 200 ✅ |
| `application/xml` | 200 | 200 ✅ | 200 ✅ |
| **`application/x-www-form-urlencoded`** | **200** | 🔴 **400** `JSON parse error: Unexpected character ('%' (code 37))` | ✅ **200**，body 与 Go **逐字节一致** |
| **`multipart/form-data`** | **200** | 🔴 **400** `Failed to parse multipart servlet request` | ✅ **200**，body 与 Go **逐字节一致** |

**Go 为什么收**：`gettaskhandler.go:18-24` 直接 `json.NewDecoder(r.Body).Decode(&req)`，
**不调 `httpx.Parse`**（那才是 go-zero 做 Content-Type 校验的入口），handler 对 Content-Type 零判断。

---

#### 🔴🔴 本轮推翻上一版台账的根因结论（**上一版是误判**）

上一版写的是：

> 3. ⇒ **剩余消费者在 Tomcat 11 连接器层，Spring 的配置开关够不着**（filter 在解析之后才跑）。

**这是错的。** 真源在 **Spring 的 `HttpInputMessage` 制造环节**，不在 Tomcat 连接器层。逐条证据：

**（1）消费点是 Spring 的 `ServletServerHttpRequest.getBody()`**，spring-web **7.0.9**
`org/springframework/http/server/ServletServerHttpRequest.java:227-234`：
```java
public InputStream getBody() throws IOException {
    if (isFormPost(this.servletRequest) && this.servletRequest.getQueryString() == null) {
        return getBodyFromServletRequestParameters(this.servletRequest);  // ← 不读原始 body
    }
    else {
        return this.servletRequest.getInputStream();
    }
}
```
`isFormPost`（同文件 `:249-253`）判的是
`contentType.contains("application/x-www-form-urlencoded") && POST`；
`getBodyFromServletRequestParameters`（同文件 `:261` 起）做的是
`request.getParameterMap()`（触发 Tomcat 表单解析，body 被消费）→ 再把参数
`URLEncoder.encode` 后拼成 `key=value` ⇒ **`%` 的来源**。

**（2）上一版的「已实测排除 Spring 侧」是**假阴性**。**
上一版加的开关是 `--spring.mvc.formcontent.filter.enabled=false` 与
**`--spring.mvc.servlet.multipart.enabled=false`** ——
⚠️ **后者的属性名错了**（多了 `mvc.`）。Boot 4 的
`MultipartAutoConfiguration` 上是
`@ConditionalOnBooleanProperty(name = "spring.servlet.multipart.enabled", matchIfMissing = true)`
（在 `spring-boot-servlet-4.0.8.jar` 里）⇒ 写 `spring.mvc.servlet.multipart.enabled`
**静默失效**（没人读那个属性）⇒ 当时「行为完全不变」是必然的，
**不能作为「已排除 Spring 侧」的证据**。

**（3）更关键：body 在转换器被调用**之前**就已经被换掉了** ⇒ 转换器层来不及。**
spring-webmvc 7.0.9
`org/springframework/web/servlet/mvc/method/annotation/AbstractMessageConverterMethodArgumentResolver.java:355-380`：
```java
private static class EmptyBodyCheckingHttpInputMessage implements HttpInputMessage {
    public EmptyBodyCheckingHttpInputMessage(HttpInputMessage inputMessage) throws IOException {
        this.headers = inputMessage.getHeaders();
        InputStream inputStream = inputMessage.getBody();   // ← 构造期就消费一次
        ...
    }
}
```
⇒ 转换器拿到的 `HttpInputMessage` 是 `EmptyBodyCheckingHttpInputMessage`，
**不是** `ServletServerHttpRequest` ⇒ **转换器层根本拿不到原始 request，更拿不到原始 body**。

⚠️ **本轮第一版修法就栽在这里**：先按「转换器层修」写了
`ContentTypeAgnosticJsonBodyConverter.read(ResolvableType, HttpInputMessage, Map)` 的绕开逻辑，
靠 `inputMessage instanceof ServletServerHttpRequest` 拿原始 request ——
**永远为 false** ⇒ 修法一装上就没效果（实测 form-urlencoded 仍 400）。
⇒ 这正是 AGENTS.md §3.3 说的「**注解存在 ≠ 机制生效**」的另一种形态：
**方法签名对了 ≠ 方法会被调到**。

---

#### ✅ 修法（两处，分工不同）

**（A）multipart ⇒ 配置层「本服务不支持 multipart」**

`service/task/task-api/src/main/resources/application.yml`：
```yaml
spring:
  servlet:
    multipart:
      enabled: false
```
**为什么**：Go 侧 task-api **没有 multipart 概念** ——
`routes.go:15-25` 只有一条路由，`gettaskhandler.go:19-21` 是裸 decoder，**不读任何 form part**。
⇒ Spring 侧也不该让 multipart resolver 先把 body 吃掉
（`DispatcherServlet` 的 multipart resolver 调 `getParts()` 消费 body），
否则 body 到 handler 时已被消费 ⇒ 报「Required request body is missing」。
关掉之后 multipart body 原样流给 Jackson ⇒ 与 Go 侧同样按 JSON 解析 ⇒ **同样的 200**。

**（B）form-urlencoded ⇒ 消息输入层「body 一律按 JSON 读原始字节」**

新增两个类：
- [`GoStyleRawBodyArgumentProcessor`](../../../service/task/task-api/src/main/java/com/rag/task/task/api/config/GoStyleRawBodyArgumentProcessor.java)
  —— 继承 `RequestResponseBodyMethodProcessor`，**只覆盖 `createInputMessage(NativeWebRequest)`**
  （父类 `AbstractMessageConverterMethodArgumentResolver:269-273`，protected），
  返回一个 `getBody()` 直读 `HttpServletRequest.getInputStream()` 的 `ServletServerHttpRequest` 子类。
- [`TaskApiRawBodyConfig`](../../../service/task/task-api/src/main/java/com/rag/task/task/api/config/TaskApiRawBodyConfig.java)
  —— 用 `BeanPostProcessor` 把 `RequestMappingHandlerAdapter` 里的默认
  `RequestResponseBodyMethodProcessor` **就地替换**成本类。

**为什么不能改转换器**（这正是上一版台账「要彻底修」的第二个候选，**方向对、位置错**）：
body 在转换器被调用前就被换掉了（见上面 (3)）⇒ 转换器层是**事后诸葛**。

**为什么用 `BeanPostProcessor` 替换处理器、而不是自建 `HandlerMethodArgumentResolver`**：
`RequestMappingHandlerAdapter#getDefaultArgumentResolvers()`（spring-webmvc 7.0.9）
把**自定义** resolver 放在**所有**内置 resolver **之后**，
而 `RequestResponseBodyMethodProcessor.supportsParameter()` 对任何带 `@RequestBody` 的参数都返回 `true`
⇒ 自定义 resolver **永远轮不到**。⇒ 只能**就地替换**默认实例。

**⚠️ 副作用面（必须知道再改）**：替换实例会丢掉 Spring 内部持有的 `RequestBodyAdvice` 链
（`RequestMappingHandlerAdapter#requestResponseBodyAdvice` 是 private 且**没有 getter**）。
本仓全量 `service/` 主代码里**没有任何** `RequestBodyAdvice` 实现 ⇒ 传 `null` 与 Spring 自己的装配**完全等价**。
⇒ 装配处加了**启动即失败**的断言：一旦将来有人加了 `RequestBodyAdvice`，直接抛异常，
**不静默丢掉**。

**不用「filter 改写 Content-Type」那种「在容器层撒谎」的方案** ——
那会让服务端看到与客户端不同的 Content-Type，排查时更迷惑。

---

#### ✅ 证据

| 证据 | 结果 |
|---|---|
| **双侧活体对拍**（`tools/difftest/parity-task.py`，44 条矩阵） | ✅ **MATCH 44 / DIFF 0**，脚本 `EXIT=0` |
| **真 Tomcat 守门测试**（`TaskApiContentTypeTest`，12 条） | ✅ 6 种 Content-Type + 不带 Content-Type 全 200；**阳性对照**（畸形 JSON / 空 body）仍 400 |
| **变异测试 ①**：撤掉处理器替换 | 🔴 1 条红，报错正是 `Unexpected character ('%' (code 37))`（与修前历史错误形态一致） |
| **变异测试 ②**：multipart 开关改回 `true` | 🔴 2 条红（行为 + 配置项断言各 1） |
| **全量回归** | ✅ task-api **90** + task-rpc **90** + common **146** = **326 条全绿 0 skipped** |

⇒ 守门测试**不是假绿**：两个变异都能把它打红。

**修法来源**（本轮唯一一次「先读源码再动手」）：
- Go：`service/task/api/internal/handler/task/gettaskhandler.go:18-24`
- Spring：spring-web 7.0.9 `ServletServerHttpRequest.java:227-234` / `:249-253` / `:261`
- Spring：spring-webmvc 7.0.9 `AbstractMessageConverterMethodArgumentResolver.java:269-273` / `:355-380`
- Spring：`AbstractJacksonHttpMessageConverter.java:313`（`read(ResolvableType, HttpInputMessage, Map)`，
  **Smart 分支**，是本仓转换器 `read` 覆盖的真实入口）、
  `AbstractJacksonHttpMessageConverter.java:88`（`extends AbstractSmartHttpMessageConverter`）

---

### ⚠️ DRIFT-TASK-API-004 · `Content-Type` header 的 charset 与空白格式

**性质**：OBSERVABLE DIFFERENCE（低危，全域系统性）。

| | 值 |
|---|---|
| Go | `application/json; charset=utf-8` / `text/plain; charset=utf-8`（**分号后有空格**） |
| RAG | `application/json`（**无 charset**） / `text/plain;charset=utf-8`（**无空格**） |

**出处**：Go 的成功响应走 `httpx.WriteJsonCtx` → `w.Header().Set(ContentType, header.ContentTypeJson)`
（go-zero `rest/httpx/responses.go:182`），常量是 `"application/json; charset=utf-8"`；
错误响应走 `http.Error`（Go 标准库）⇒ `"text/plain; charset=utf-8"`。
RAG 侧由 Spring 的转换器与本域的 `plainText()` 产生。

**为什么不改**：按 RFC 9110，`;` 后的 OWS（optional whitespace）本就允许省略；
`application/json` 按规范默认就是 UTF-8，省略 charset 合法且更常见。
改它需要覆盖 Spring 的消息转换器输出，代价大于收益。
⇒ 归入**全域共性差异**（其它 api 域同样如此），**不在本域单独修**。

⚠️ **它曾让本域对拍的 43 条里有 40 条「红了」** —— 真差异会被淹没。
⇒ **2026-10-24 已处理**：对拍脚本改成「**按媒体类型归一后判定**，
原值差异归入『已知全域共性』单列（打印但不计 DIFF）」，
现在 44 条里 43 条的这个共性差异**单列可见**，且**不再掩盖任何真差异**。
（⚠️ `tools/difftest/diff.py` 里的 `Normalizer` 管的是 **JSON 值**的归一，
**不是** HTTP 头 —— 别指望它兜这件事。）

---

## 七、对拍产物与复现

### 一条命令复跑（2026-10-24 新增）

```bash
./tools/difftest/run-task-http-parity.sh          # 完整跑一遍
KEEP_UP=1 ./tools/difftest/run-task-http-parity.sh # 跑完不拆服务与容器
```

⚠️ 它会**重建** `taskprobe-postgres` / `taskprobe-redis` / `taskprobe-kafka`
（`docker compose down -v` + 删同名遗留容器）⇒ **会破坏该容器上的既有数据**，
且与其它并发会话可能抢容器名/端口（已用 `taskprobe-*` 前缀 + 独立端口避开）。

### 产物

| 文件 | 作用 |
|---|---|
| [`tools/difftest/run-task-http-parity.sh`](../../../tools/difftest/run-task-http-parity.sh) | **一键编排**（编 Go/RAG、起基建、起两侧四进程、等就绪、跑矩阵、收摊） |
| [`tools/difftest/parity-task.py`](../../../tools/difftest/parity-task.py) | **44 条请求矩阵**（已进仓）；`EXIT=0/1` 可当门禁 |
| [`tools/difftest/docker-compose.task-kafka.yml`](../../../tools/difftest/docker-compose.task-kafka.yml) | 基建（pg/redis/kafka，容器名 `taskprobe-*`） |
| [`tools/difftest/conf/go/task-rpc.yaml`](../../../tools/difftest/conf/go/task-rpc.yaml) | Go rpc 配置副本（冻结真源零写入） |
| [`tools/difftest/conf/go/task-api.yaml`](../../../tools/difftest/conf/go/task-api.yaml) | Go api 配置副本 |
| `$TMPDIR/task-http-parity/logs/parity-task-http.txt` | 对拍报告落盘（编排脚本第 9 步 tee 出来） |

⚠️ `parity-task.py` **硬编码了本轮的端口**（Go 18886 / RAG 58886）与库名，与 `run.sh` 一样属对拍专用配置。

---

## 2026-10-25 补记：`DRIFT-MSG-V-030` 的**域内部分**已修 —— 本模块产物现在是可执行 jar

> **登记方在 message 域**（[`docs/DRIFT/message/rpc.md`](../../DRIFT/message/rpc.md) 的 `DRIFT-MSG-V-030`）——
> 它是 V-028 的同款缺陷，但**修法落在本模块自己的 `pom.xml`** ⇒ 本域已修。

| 项 | 修前 | 修后 |
|---|---|---|
| 产物 | **24 KB** library jar，**无 `Main-Class`** | **63 MB** Spring Boot fat jar，`Main-Class: org.springframework.boot.loader.launch.JarLauncher` |
| `java -jar` | 「中没有主清单属性」 | 可启动（外部依赖齐备的前提下） |
| pom | **无 `<build>` 段** | 补 `<build>` 段 + `spring-boot-maven-plugin`（版本由根 pom 的 `spring-boot-starter-parent` 继承，不写 `<version>`） |

**修法**：照 `service/message/message-api/pom.xml` 的 V-028 同款写法，在本模块 pom 补
`<build><plugins><plugin>spring-boot-maven-plugin</plugin></plugins></build>`。
启动类 `TaskApiApplication` 唯一且带 `main` 方法 ⇒ **不显式写 `<mainClass>`**（少一处可能写错的配置）。

⚠️ **本次只证明「产物带 `Main-Class` 且是可执行 fat jar」**，**没有**真起进程验证启动成功
（会撞 Nacos / Redis / Kafka / gRPC 等外部依赖 ⇒ 起不来不等于产物有问题）。

⚠️ **「`mvn package` 成功」不算数** —— 本项目至今的每一项验证都跑在容器 / 测试环境里，
**从来没有碰过 jar 本身**（这正是 §3.3「编译通过 ≠ 产物可运行」的形态）。
⇒ **判据是守门脚本**：`tools/difftest/check-jar-executable.sh`
（逐个 api 模块解包 `META-INF/MANIFEST.MF` 看 `Main-Class`）
⇒ **2026-10-25 实测 `EXIT=0`**（修前 `EXIT=1`）。
