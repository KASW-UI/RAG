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

---

## 〇、结论

| 项 | 结论 |
|---|---|
| 语义等价性 | 🔴 **SEMANTICALLY NOT EQUIVALENT**（1 条端口漂移，已修） |
| 已修 | **2 条**（DRIFT-TASK-API-001 端口 / DRIFT-TASK-API-002 `Go:` 行号引用失准） |
| 未修 | 0 条语义漂移 |
| 证据强度 | ✅ **真实 MVC 管线**（`@SpringBootTest` + MockMvc，20 条）· ⚠️ **gRPC 边界是 mock** · 🔴 无双侧活体对拍 |
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

## 三、证据边界

1. 🔴 **无双侧活体对拍**：Go 侧 `task-api` 未起过（依赖 `task-rpc` + etcd）。
2. ⚠️ **gRPC 边界是 mock**：`TaskGetServiceTest` / `GrpcStatusHttpMappingTest` 用 stub，
   验证的是「拿到某个 gRPC code 后映射成什么 HTTP 响应」，**不是**「对着真的 task-rpc 调」。
3. ⚠️ **MockMvc 不是真 Tomcat**。400 响应的 `Content-Type`/`nosniff` 在 MockMvc 下可验，
   但**真实 Tomcat 下 400 的 header 行为**属另一域已登记的坑（`tools/difftest/check-plaintext400-headers.sh`），
   本域**未跑该脚本** ⇒ 登记为待办。
4. ⚠️ 本轮**未重审** `service/common`（`HttpMiddlewareFilter` / breaker / maxConns 等），
   其结论沿用 `docs/DRIFT/common/audit.md`。

---

## 四、待办

1. 跑 `tools/difftest/check-plaintext400-headers.sh` 覆盖 task-api（真 Tomcat 下的 400 header）。
2. Go 侧起 `task-rpc` + `task-api`，对本域做双侧活体对拍（本域可达的最高证据档）。
3. `task-api` 的 `NacosDiscovery` 在真注册中心下的注册/注销行为未验（当前测试指向不可达地址绕过）。

---

## 五、相关文件

| 关系 | 文件 |
|---|---|
| 本域 rpc 台账（**必须先读**） | [`rpc.md`](./rpc.md) |
| 本域 proto 台账 | [`proto.md`](./proto.md) |
| 踩坑 | [`../../Pitfalls/task/api.md`](../../Pitfalls/task/api.md) |
| 有意偏离 | [`../../Intentional_Deviations/task/api.md`](../../Intentional_Deviations/task/api.md) |