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
| 语义等价性 | 🔴 **SEMANTICALLY NOT EQUIVALENT**（1 条端口漂移，已修） |
| 已修 | **2 条**（DRIFT-TASK-API-001 端口 / DRIFT-TASK-API-002 `Go:` 行号引用失准） |
| 对拍新发现 | **1 条 OBSERVABLE DIFFERENCE**（DRIFT-TASK-API-003，**未修**，见 §六） |
| 未修 | 0 条语义漂移 |
| 证据强度 | ✅✅ **双侧活体对拍**（2026-10-23 升级）· ✅ 真实 MVC 管线（MockMvc，20 条）· ⚠️ gRPC 边界在单测里是 mock |
| 对拍结果 | **43 用例：body 43/43 逐字节一致**；状态码 41/43 一致；Content-Type header 格式 40 条不同 |
| 测试 | **78 tests / 0 failures / 0 errors / 0 skipped** |

---

## 一、已修（2 条）

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

### 结果：43 条用例

| 维度 | 结果 |
|---|---|
| **响应体逐字节一致** | ✅ **43 / 43** |
| 状态码一致 | ✅ 41 / 43（差的 2 条见 DRIFT-TASK-API-003） |
| `X-Content-Type-Options: nosniff` | ✅ 43 / 43 一致 |
| `Content-Type` header | ⚠️ **40 条不同**（格式差异，见 DRIFT-TASK-API-004） |

覆盖：`id` 参数解析 18 类（null/缺失/字符串/数字/布尔/数组/对象/浮点/超长/带符号…）、
DB 回源 5 组（含无行、status/progress 为 NULL）、**Redis 缓存命中 10 组手工 crafted 值**
（2 段 / 4 段 / 空串 / 非数字 / **负数** / 超 target / status 含 `|`）、路由层 3 组、畸形 JSON、空 body。

### 🔴 静态审查完全看不到、只有对拍才抓到的一条

**DRIFT-TASK-API-003**：Go 的 handler（`gettaskhandler.go:19` 的 `json.NewDecoder(r.Body)`，
**没有** `httpx.Parse`）对 `Content-Type` **完全无感**；
RAG 侧 Tomcat 会对 `form-urlencoded` / `multipart` 的 POST body 抢先解析并消费掉输入流 ⇒ 返回 400。
详见 §六。

### ⚠️ 对拍方法论上的一条教训（已写进 `../../Pitfalls/task/rpc.md`）

第一版探针用 Python `urllib`，它在「没显式给 Content-Type」时会**自动补** `application/x-www-form-urlencoded`
⇒ 把「不带 Content-Type」测成了「带 form-urlencoded」，一度得出「两侧行为一致」的假结论。
用 `curl -H 'Content-Type:'` 复验才发现真相。**探针自己也会撒谎。**

## 三、证据边界

1. ✅ **双侧活体对拍已做**（2026-10-23 22:5x，见 §二·五）：Go `task-api:18886` + `task-rpc:19005`
   与 RAG `task-api:58886` + `task-rpc:59005` 同时起，43 条请求矩阵，
   **body 43/43 逐字节一致**，状态码 41/43。
   ⚠️ 但覆盖面**只到 HTTP wire 层**：经由 `GetTask` 这条链路验证了
   **Redis HGETALL → PG 回源 → packProgress/unpackProgress → 任务定义合并 → gRPC 序列化 → HTTP 响应**。
   **Kafka 消息链路不在 HTTP 对拍面内**（那条链路由
   [`rpc.md` §四·三](./rpc.md#四三kafka-端到端对拍两侧灌同一批-raw-日志比最终态2026-10-23-234x-实测) 单独对拍）。
2. ⚠️ **gRPC 边界在单测里是 mock**：`TaskGetServiceTest` / `GrpcStatusHttpMappingTest` 用 stub，
   验证的是「拿到某个 gRPC code 后映射成什么 HTTP 响应」，**不是**「对着真的 task-rpc 调」。
   ⚠️ 但 **gRPC 单组件对拍已单独做**（`rpc.md` §四·三 之前的 gRPC runner，7/7 一致，
   含 3 组 HTTP 面永远走不到的 `INVALID_ARGUMENT`）。
3. ⚠️ **MockMvc 不是真 Tomcat**。400 响应的 `Content-Type`/`nosniff` 在 MockMvc 下可验，
   但**真实 Tomcat 下 400 的 header 行为**属另一域已登记的坑（`tools/difftest/check-plaintext400-headers.sh`），
   本域**未跑该脚本** ⇒ 登记为待办。
4. ⚠️ 本轮**未重审** `service/common`（`HttpMiddlewareFilter` / breaker / maxConns 等），
   其结论沿用 `docs/DRIFT/common/audit.md`。

---

## 四、待办

1. 跑 `tools/difftest/check-plaintext400-headers.sh` 覆盖 task-api（真 Tomcat 下的 400 header）。
2. `task-api` 的 `NacosDiscovery` 在真注册中心下的注册/注销行为未验（当前测试指向不可达地址绕过）。
3. **DRIFT-TASK-API-003 的彻底修法**（Servlet 容器层拦截 form/multipart 的参数化）——
   见 §六 的「要彻底修，需要」两条候选方案，**均未评估**。
4. **跨域闭环对拍**：「Kafka 写入 → `task-api` 读到」——
   注意两侧的读写 Redis key **本来就不一致**（见 `TaskRedisKeys` 类注释），
   所以这条闭环在 Go 侧也是「读不到自己写的缓存」的，**不要当成 RAG 的 bug 修**。

---

## 五、相关文件

| 关系 | 文件 |
|---|---|
| 本域 rpc 台账（**必须先读**） | [`rpc.md`](./rpc.md) |
| 本域 proto 台账 | [`proto.md`](./proto.md) |
| 踩坑 | [`../../Pitfalls/task/api.md`](../../Pitfalls/task/api.md) |
| 有意偏离 | [`../../Intentional_Deviations/task/api.md`](../../Intentional_Deviations/task/api.md) |

---

## 六、对拍新发现（未修）

### 🔴 DRIFT-TASK-API-003 · JSON body 配 `form-urlencoded` / `multipart` 时 Go 收、RAG 拒

**性质**：SEMANTIC MISMATCH（状态码层）。**对拍抓出，静态审查不可见。**

| Content-Type（body 均为 `{"id":1001}`） | Go | RAG |
|---|---|---|
| `application/json` | 200 | 200 ✅ |
| 不带 Content-Type | 200 | 200 ✅ |
| `text/plain` | 200 | 200 ✅ |
| `application/xml` | 200 | 200 ✅ |
| **`application/x-www-form-urlencoded`** | **200** | 🔴 **400** `JSON parse error: Unexpected character ('%' (code 37))` |
| **`multipart/form-data`** | **200** | 🔴 **400** `Failed to parse multipart servlet request` |

**Go 为什么收**：`gettaskhandler.go:18-24` 直接 `json.NewDecoder(r.Body).Decode(&req)`，
**不调 `httpx.Parse`**（那才是 go-zero 做 Content-Type 校验的入口），handler 对 Content-Type 零判断。

**RAG 为什么拒（根因已定位到消费点，未完全收口）**：

1. 已实测**排除** Spring 侧：加 `--spring.mvc.formcontent.filter.enabled=false` 与
   `--spring.mvc.servlet.multipart.enabled=false` 后行为**完全不变**；
2. 实测**排除** Tomcat 的 body 参数解析：`Connector#setParseBodyMethods(null)`（字节码确认
   `isParseBodyMethod` 就是一次 `Set.contains`，空集 ⇒ 完全不解析）之后，
   form-urlencoded 的报错从「URL 解码损坏」变成 **「Required request body is missing」**
   ⇒ 输入流仍被消费掉，但已不是 Tomcat 参数解析干的；
3. ⇒ **剩余消费者在 Tomcat 11 连接器层，Spring 的配置开关够不着**（filter 在解析之后才跑）。

**为什么没有修**（两条缓解路径都实测过，都**没有达成 parity**）：

| 尝试 | 结果 |
|---|---|
| `setParseBodyMethods(null)`（新增 `TomcatConnectorCustomizer`） | 错误**文案**变了（`%` 解码损坏 → body 缺失），**状态码仍是 400**。且全局关掉 body 解析会让 `getParameter*()` / `getParts()` 静默失效 —— 对「下一个端点要读表单参数」是雷 |
| 再叠加关掉 formcontent filter + multipart | 无变化 |

⇒ **该改动已回滚**（不进仓）。理由：它没有改变对外可观察的**状态码**，
却引入了「容器级关掉 body 解析」这个副作用面宽的改动；
半吊子修复比不修更危险 —— 它会让人以为问题已解决。

**影响面判断**：只影响「用非 JSON 的 Content-Type 头发送 JSON body」的客户端。
这类客户端本身用法有问题（Go 能收是因为它根本不校验），真实网关/前端 SDK 基本不会这么发。
⇒ 归 **OBSERVABLE DIFFERENCE 而非阻塞缺陷**，但**按 AGENTS.md §5 仍默认判 RAG 错**，未签字。

**要彻底修，需要**（均未做，需先评估）：
- 在 Servlet 容器之前拦住 form/multipart 的请求参数化（例如自定义 `HttpServletRequest` 包装 +
  强制 `setContentType("application/json")`），或
- 让本域不接受任何 `consumes` 判定并自建 `HandlerMethodArgumentResolver` 直接读 raw stream。

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

⚠️ 但它让本域对拍的 43 条里有 40 条「红了」。读对拍报告时必须先按 header 归一再看，
否则会淹没真正的语义差异 —— 这正是 `tools/difftest/diff.py` 里 `Normalizer` 的职责。

---

## 七、对拍产物与复现

| 文件 | 作用 |
|---|---|
| [`tools/difftest/conf/go/task-rpc.yaml`](../../../tools/difftest/conf/go/task-rpc.yaml) | Go rpc 配置副本（冻结真源零写入） |
| [`tools/difftest/conf/go/task-api.yaml`](../../../tools/difftest/conf/go/task-api.yaml) | Go api 配置副本 |
| [`tools/difftest/parity-task.py`](../../../tools/difftest/parity-task.py) | 43 条请求矩阵（已进仓） |
| `/tmp/task-difftest/parity-result.txt` | 本轮结果原始输出 |

⚠️ **`parity.py` 尚未进仓**。它本该落在 `tools/difftest/` 下（像 `diff.py` / `run.sh` 那样可复现），
本轮已补进仓（`tools/difftest/parity-task.py`）。⚠️ 它目前**硬编码了本轮的端口**（Go 18886 / RAG 58886）与库名，与 `run.sh` 一样属对拍专用配置。
