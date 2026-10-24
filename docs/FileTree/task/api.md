# task-api — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/task/api`
> 目标根包：`com.rag.task.task.api`
> 推导日期：2026-10-23
> 配套文档：`docs/FileTree/task/rpc.md`（**同域，无 `common.md`**，依据见 §12.1 末）

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/task/task-api` 是**真·空壳**：

| 检查项 | 实测结果 |
|---|---|
| `service/task/task-api/pom.xml` | 19 行，**无 `<dependencies>` 块**（`task-api/pom.xml:12-18` 只有 artifactId 与 compiler properties） |
| `src/main/java`、`src/main/resources`、`src/test/java` | 三个目录**存在但为空**（`find task-api -type f` 只命中 `pom.xml` 与 `target/**`） |
| Java 文件数 | **0** |
| `target/task-api-0.0.1-SNAPSHOT.jar` | 已产出（空 jar） |

⇒ 包结构完全由同仓库已落地兄弟模块推导，**本文件所有"复用"结论都给出实际打开过的路径**。

| 参照模块 | 提供的一致性依据 |
|---|---|
| `service/users/user/user-api`（30 个 Java 文件） | `@RestController` + `@RequestMapping("/user/v1")`（`controller/UserController.java:99-101`）；`@PostMapping` + `@RequestBody(required=false)`（`:135-142`）；`advice/{GlobalResponseAdvice,GlobalExceptionHandler}`；`client/UserRpcClient`（`client/UserRpcClient.java:23-27` 的 `@GrpcClient` 字段注入）；`config/{UserApiProperties,WebMvcConfig}` |
| `service/like/like-api`（2026-10-23 已落地） | `LikeApiApplication.java:63-68` 的 `scanBasePackages` + `@EnableDiscoveryClient`；`controller/LikeController.java:63-64` |
| `service/article/article-api` | 🔴 **本域的决定性参照**：它同样是**没有注册 error handler** 的 api，其 `http/GoStylePlainTextErrors.java:13-48` 的 javadoc 已把「有 error handler」与「无 error handler」两种 wire 形状的分野写成明文 |
| `service/common` | `response/Response.java:14-18`；`http/GoStyleRouterErrorResponses.java:95,107`（404/405 字节形状）；`protection/GoStyleProtectionProperties.java:104`；`spring/HttpMiddlewareFilter.java:36-55`；`spring/ObservabilityProperties.java:13,66` |

**包名集合**：本文件只使用 `advice/ client/ controller/ dto/ service/` 五个已定型包 + 根包下的 `*Application`。
**刻意不建**：`config/`、`interceptor/`、`metrics/` —— 理由逐条见 §12.4 与 §12.7。

### 已有组件（跨模块可复用）

- **JWT 鉴权**：`users/user/user-api/interceptor/JwtAuthInterceptor.java` —— **本域不使用**（Go 侧零鉴权，见下）。
- **`Response` 信封 + advice**：`common/response/Response.java:14-18` + `users/.../advice/GlobalResponseAdvice.java:41-74` —— **本域不使用信封**（Go 侧无 okHandler，见下）。
- **异常 → 业务码**：`users/.../advice/GlobalExceptionHandler.java:49-55`（`BusinessException` → `ResponseEntity<Response>`）—— **本域不使用信封**，理由见下。
- **gRPC 下游客户端**：`users/user/user-api/client/UserRpcClient.java:23-27`。
- **text/plain 400 形状**：`users/.../advice/GlobalExceptionHandler.java:214-220`（`goStylePlainTextBadRequest` 私有静态方法）；同款公开工具类见 `article/article-api/.../http/GoStylePlainTextErrors.java:64-75`。
- **404 / 405 形状**：`common/http/GoStyleRouterErrorResponses.java:95,107`（`like-api` 的用法见 `advice/GlobalExceptionHandler.java:212,221`）。
- **请求体上限**：`common/protection/GoStyleMaxBytesFilter.java:82-112` + `GoStyleProtectionProperties.java:104`（默认 `1048576L` = 1 MiB）。
- **HTTP span / 超时**：`common/spring/HttpMiddlewareFilter.java:36-55`，超时值由 `observability.http-timeout` 提供（`ObservabilityProperties.java:13,66,86-91`）。
- **`,string` int64 解析原语**：`users/users-common/.../http/GoStyleStringInt64.java:83-125` —— **本域不复用该类**（在 users-common 模块里，见 §12.4 的"不复用"行），改为在 DTO 内自持一份 15 行等价逻辑。

### 已确认的框架机制

- Spring Boot **4.0.8** / Java **21**（`service/task/pom.xml:20-23`）。
- `@RestController` + `@RequestBody` + `@RestControllerAdvice`（Jackson **3**，`tools.jackson.*`；依据 `users/users-common/.../http/GoStyleCoercion.java:62-66`）。
- `net.devh:grpc-client-spring-boot-starter` 的 `@GrpcClient` + Spring Cloud Alibaba Nacos 服务发现（`users/.../client/UserRpcClient.java:13,23`）。
- `@ConfigurationProperties`（`users/.../config/UserApiProperties.java:18,23-24`）。
- `HandlerInterceptor` + `WebMvcConfigurer`（`users/.../config/WebMvcConfig.java:55-59`）—— **本域不需要**。

### 当前 Component 可以直接复用的能力

| 能力 | RAG 侧依据 | 本模块用在哪 |
|---|---|---|
| gRPC stub 注入 | `users/user/user-api/client/UserRpcClient.java:23-27` | 唯一下游 `task-rpc` |
| 404 / 405 字节形状 | `common/http/GoStyleRouterErrorResponses.java:95,107` | 全部路由层错误 |
| 1 MiB 请求体上限 | `common/protection/GoStyleMaxBytesFilter.java:109-112` | 唯一端点 |
| HTTP span + 3s 超时 | `common/spring/HttpMiddlewareFilter.java:52-55` + `observability.http-timeout: 3s` | 唯一端点 |
| gRPC status → HTTP 状态码映射 | `errcode.CodeFromGrpcError`（Go 侧，见 §12.3）的 Java 等价 | `GlobalExceptionHandler.handleRPC` |

### 需要进一步确认的组织约定

| 项 | 结论 |
|---|---|
| 🔴 **路由前缀 `/taskcenter/v1`** | Go 为 `rest.WithPrefix("/taskcenter/v1")`（`internal/handler/routes.go:24`）。**与 RAG 全部 6 个已落地 api 的 `/user/v1`、`/admin/v1`、`/message/v1`、`/like/v1`、`/article/v1`、`/hot/v1` 全部不同**（`taskcenter` 是英文复合词，其余是单域名）。这是 L1 wire 契约，**必须照抄**，不得"统一成 task/v1"。是否要签字留档需拍板 —— **UNKNOWN（需拍板）** |
| 🔴 **成功响应无信封** | `api/task.go` 全文 37 行，**没有 `httpx.SetOkHandler`**（对比 `like/api/likecenter.go:41-47`）。go-zero v1.10.2 `rest/httpx/responses.go:56-64` 的 `OkJsonCtx` 在 `okHandler == nil` 时**直接写原始对象** ⇒ 成功响应体就是 `{"tasks":[...]}`，**没有 `{code,msg,data}`**。⇒ task-api **不得引入 `GlobalResponseAdvice`** |
| 🔴 **失败响应是 text/plain，不是信封** | 同上，`task.go` 也**没有 `httpx.SetErrorHandler`** ⇒ `responses.go:127-141` 的 `buildErrorHandler` 返回 nil ⇒ `:143-160` 的 `doHandleError` 落 `handler == nil` 分支 ⇒ `http.Error(w, err.Error(), code)`。与 article-api 同类（`article/.../GoStylePlainTextErrors.java:22-27`） |
| 🔴 **端点零鉴权** | `routes.go:16-25` 的 `server.AddRoutes(..., rest.WithPrefix("/taskcenter/v1"))` **只有 WithPrefix，没有 `rest.WithJwt`**（全文件 26 行，实测）。`config.Auth{AccessSecret, AccessExpire}`（`internal/config/config.go:14-17`）+ `etc/task.yaml:9-11` 的 `"replace-with-a-long-random-secret"` 是**死配置**：`grep -rn "Auth\|AccessSecret\|AccessExpire" api/` 只命中 config 结构体与 yaml 四行，**零消费点**。⇒ **不建 `interceptor/`** |
| 🔴 **用户身份来自 body 而非 JWT** | `internal/types/types.go:52-54` `GetTaskReq{Userid UserIDParam json:"id"}`，`internal/logic/task/gettasklogic.go:31` `req.Userid.Int64()`。这是**水平越权面**（任何调用方都能查任意 uid 的任务进度），但它是 Go 的既定行为，本文件按原样保留 |
| **下游寻址** | `etc/task.yaml:6` `Target: 127.0.0.1:9005` —— **硬编码 localhost，无 Etcd**（对比 `task/rpc/etc/task.yaml:3-6` 有 `Etcd.Key: task.rpc`）。RAG 全部 rpc 模块都用 `discovery://`（`message/message-rpc/src/main/resources/application.yml:83-91`）。`127.0.0.1:9005` 是**部署形态**而非 wire 契约，但改成 `discovery:///task-rpc` 需要 task-rpc 已注册 Nacos ⇒ **UNKNOWN（需拍板：先直连还是直接上服务发现）** |
| **`Telemetry` 段** | `etc/task.yaml:13-17`（`Name: task-api` / `Endpoint: jaeger:4317` / `Sampler: 1.0`）对应 RAG 的 `otel.*`（`message-rpc/application.yml:126-134` 有同款）。`Name` 直接落 `otel.service.name` |
| **`config.Config` 无 `Telemetry` 字段** | `internal/config/config.go:11-18` 只声明 `RestConf` + `TaskRpc` + `Auth`。yaml 有 `Telemetry` 段但结构体无对应字段 ⇒ go-zero `conf.MustLoad` 对未知键的严格性不在本仓库 ⇒ **UNKNOWN**（go-zero `core/conf` 在模块缓存里，但 `RestConf` 内嵌的 `service.ServiceConf` 是否吞掉该段需实跑才能定论） |

---

## 12.2 第二部分：Go 原始文件树

```
service/task/api/
├── task.go                                          # 入口：conf.MustLoad + 关原生超时 + HTTP 中间件 + 注册路由
├── etc/task.yaml                                    # Name/Host/Port 18886/TaskRpc/Auth/Telemetry
└── internal/
    ├── config/config.go                             # RestConf + TaskRpc(zrpc.RpcClientConf) + Auth
    ├── handler/routes.go                             # 1 条 POST /task/get + WithPrefix("/taskcenter/v1")
    ├── handler/task/gettaskhandler.go               # json.Decoder(UseNumber) → logic → OkJsonCtx/ErrorCtx
    ├── logic/task/gettasklogic.go                    # uid 解析 → gRPC GetTask → proto→DTO 投影
    ├── svc/servicecontext.go                         # 唯一 1 个下游 gRPC client：TaskCli
    └── types/types.go                                # UserIDParam(自定义 UnmarshalJSON/Int64) + Req/Resp/TaskInfo

shared:
service/common/observability/http.go                   # DisableNativeRestTimeout + NewHTTPMiddleware（api 侧唯一依赖）
```

共 **8 个文件**（7 个 `.go` + 1 个 `.yaml`），与"api 8 个"的清单一致。

---

## 12.3 第三部分：Go 文件职责分析

> 凡带 `go-zero v1.10.2` 前缀的行，行号指向本机模块缓存 `$(go env GOMODCACHE)/github.com/zeromicro/go-zero@v1.10.2/...`，
> 与 Go 侧仓库代码同版本（`service/like/rpc/etc/...` 等模块亦锁定 1.10.2，见 `docs/DRIFT/` 各台账）。

### `api/task.go`（37 行）

进程装配，**全部职责只有 5 行**：

| 行 | 代码 | 语义 |
|---|---|---|
| `:24-25` | `var c config.Config; conf.MustLoad(*configFile, &c)` | 加载 yaml |
| `:27` | `httpTimeout := observability.DisableNativeRestTimeout(&c.RestConf)` | `observability/http.go:30-34`：`TimeoutFromMillis(conf.Timeout)` 取值后把 `conf.Middlewares.Timeout` 置 false（**关掉 go-zero 原生超时**，改由 observability 统一计时） |
| `:28` | `server := rest.MustNewServer(c.RestConf)` | go-zero 内建中间件链在此挂载 |
| `:29` | `server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, observability.SlowThreshold()))` | HTTP span + 慢调用 + 超时 |
| `:32-33` | `svc.NewServiceContext(c)` + `handler.RegisterHandlers(server, ctx)` | |
| `:35-36` | `fmt.Printf` + `server.Start()` | |

🔴 **L1/L3：从 yaml 推不出来的两条 go-zero 默认值**（`rest.RestConf` 内嵌 `service.ServiceConf` 的 tag 默认）：

- **请求超时 = 3000ms**。go-zero `rest/config.go:55` 是 `Timeout int64 \`json:",default=3000"\``，`task.yaml` **没有** `Timeout` 键 ⇒ 默认值生效（默认值由 `core/mapping/unmarshaler.go:918-929` 的 `processNamedFieldWithoutValue` 在键缺失时应用）。⇒ `httpTimeout = 3s` > 0 ⇒ `observability/http.go:53` 走 `context.WithTimeout(r.Context(), timeout)` 分支；超时响应是 `http.go:21-22` 的 `499` + `"Request Timeout"`。
- **请求体上限 = 1 MiB**。`rest/config.go:53` `MaxBytes int64 \`json:",default=1048576"\``、`rest/config.go:21` `Middlewares.MaxBytes bool \`json:",default=true"\``（`DisableNativeRestTimeout` 只关了 Timeout，**没碰 MaxBytes**）⇒ go-zero `rest/handler/maxbyteshandler.go:19-22` 生效：`r.ContentLength > 1048576` ⇒ `w.WriteHeader(413)`，**裸状态码、无 body**。

- **L1 冻结**：**没有** `httpx.SetOkHandler` / `SetErrorHandler` ⇒ 响应形状由 go-zero 默认决定（见下）。
- **L4**：进程装配 → Spring Boot 启动；关原生超时 → Spring 无等价默认超时（语义自动满足）；HTTP span/超时 → `common/spring/HttpMiddlewareFilter`（需配 `observability.http-timeout: 3s`）；1 MiB 上限 → `common/protection/GoStyleMaxBytesFilter`（默认已是 1 MiB，零新增代码）。

### `api/etc/task.yaml`（17 行）

`Name: task-api`（`:1`）、`Host: 0.0.0.0`（`:2`）、`Port: 18886`（`:3`）、
`TaskRpc{Target: 127.0.0.1:9005, NonBlock: true}`（`:5-7`）、
`Auth{AccessSecret: "replace-with-a-long-random-secret", AccessExpire: 1800}`（`:9-11`，**死配置**）、
`Telemetry{Name: task-api, Endpoint: jaeger:4317, Sampler: 1.0, Batcher: otlpgrpc}`（`:13-17`）。
`NonBlock` 是 go-zero 客户端连接选项，Java 侧无对应概念（L4 丢弃，不影响 wire）。

### `api/internal/config/config.go`（18 行）

`Config = rest.RestConf + TaskRpc zrpc.RpcClientConf + Auth{AccessSecret string; AccessExpire int64}`（`:11-18`）。
**没有** `Telemetry` 字段（见 §12.1 UNKNOWN）；`Auth` 零消费点。

### `api/internal/handler/routes.go`（26 行）

`:15-25` 一个 `server.AddRoutes` 块，**1 条路由**：
`Method: http.MethodPost`（`:19`）、`Path: "/task/get"`（`:20`）、`Handler: task.GetTaskHandler(serverCtx)`（`:21`）、
装饰器**只有** `rest.WithPrefix("/taskcenter/v1")`（`:24`）。

- **L1**：完整路径 = `POST /taskcenter/v1/task/get`；**无 `rest.WithJwt`**；**无 `WithTimeout`**；**无 maxBytes 装饰器**（1 MiB 来自 `RestConf` 内建）；**无 CORS**。
- 与 like-api（6 条全 `WithJwt`）、user-api（public/protected 分档）都不同。

### `api/internal/handler/task/gettaskhandler.go`（34 行）

🔴 **本文件是本域最容易被"照抄别的域"带偏的地方。** 它**不用 `httpx.Parse`**，而是裸 `encoding/json`：

| 行 | 代码 | 与其它域的差异 |
|---|---|---|
| `:18` | `var req types.GetTaskReq` | |
| `:19-20` | `decoder := json.NewDecoder(r.Body); decoder.UseNumber()` | ⭐ **不检查 `Content-Type`**、**不套 `io.LimitReader`** |
| `:21-24` | `if err := decoder.Decode(&req); err != nil { httpx.ErrorCtx(...); return }` | 空 body ⇒ `Decode` 返回 `io.EOF` ⇒ 直接进错误路径 |
| `:26-32` | `l := NewGetTaskLogic(...)` → `err != nil ? httpx.ErrorCtx : httpx.OkJsonCtx` | |

- **L3 事实**：`Content-Type: text/plain` + 合法 JSON body ⇒ **Go 会成功解析**。而 RAG 的 `common/http/GoStyleJsonBodyConverter.java:19-30` 复刻的是 go-zero `httpx.Parse` 的 `withJsonBody` 判据（`ContentLength > 0 && ContentType 含 application/json`，否则**整段丢弃 body**）⇒ **task-api 绝不能注册 `GoStyleJsonBodyConverter`**，否则这是新增的 415/丢弃行为。
- **L3 事实**：未知 JSON 字段被 `encoding/json` **静默忽略**（无 `DisallowUnknownFields`）⇒ `{"id":"1","zzz":2}` 在 Go 侧成功。
- **L3 事实**：`decoder.UseNumber()`（`:20`）对唯一字段**无作用** —— `UserIDParam.UnmarshalJSON(data []byte)`（`types.go:16`）拿到的是**原始 token 字节**，`json.Unmarshaler` 契约不经过 `any`/`interface{}` 解码路径。`types.go` 自己已经处理了字符串与数字两种形态（`:24` string 分支、`:30` json.Number 分支）。这是一处**死设置**，不迁移不报错。
- **零校验**：handler 不做任何字段校验，畸形 JSON 由 `Decode` 的 error 直接进错误路径。

### `api/internal/logic/task/gettasklogic.go`（56 行）

1. `:31-34` `uid, err := req.Userid.Int64(); if err != nil { return nil, err }` —— ⭐ **uid 唯一来源是 body**，失败返回**裸 `error`（非 gRPC status）**。
2. `:36-41` `rpcResp, err := l.svcCtx.TaskCli.GetTask(l.ctx, &taskpb.GetTaskReq{UserId: uid})`，**错误原样透传**（不做 status → int 的转换，与 like-api 的 `errmsg.NewErrCodeMsg(int(st.Code()), ...)` 不同）。
3. `:43-52` proto→DTO 投影，5 个字段逐个赋值；`out := make([]types.TaskInfo, 0, len(rpcResp.Task))` ⇒ **空列表也输出 `[]`，绝不输出 `null`**。
4. `:53-55` `return &types.GetTaskResp{Tasks: out}`。

- **L1**：**零指标**（本文件无任何 metric 调用，对比 like-api 的 `ApiRejectCount`）。
- **L2**：无业务分支、无状态转换 —— 本 logic 是**纯转发 + 字段投影**。

### `api/internal/svc/servicecontext.go`（33 行）

`:12-20` 是 goctl 最初生成的注释版（`Config` only）；`:22-25` 实际版本加了 `TaskCli taskpb.TaskServiceClient`；
`:27-32` `NewServiceContext` 只做两件事：`zrpc.MustNewClient(c.TaskRpc)` + `taskpb.NewTaskServiceClient(conn.Conn())`。

- **无 Redis、无 DB、无 Kafka** —— task-api 是**纯 HTTP→gRPC 网关**。
- **L4**：`zrpc.MustNewClient` + 生成的 client ⇒ `@GrpcClient("task-rpc")` 字段注入（`users/user/user-api/client/UserRpcClient.java:23-27`）。

### `api/internal/types/types.go`（66 行）

| 行 | 内容 | 语义 |
|---|---|---|
| `:14` | `type UserIDParam string` | 自定义标量 |
| `:16-36` | `UnmarshalJSON`：`:18` 空/`null` → `""`；`:24` 先试 `string` 分支并 `TrimSpace`；`:30` 再试 `json.Number`；`:35` 都不成 → `errors.New("id must be a string or integer")` | **接受 string 或 number 两种形态** |
| `:38-50` | `Int64()`：`:39` `TrimSpace`；`:40-42` 空 → `"user_id invalid"`；`:44` `strconv.ParseInt(v, 10, 64)`；`:45-47` `err != nil \|\| uid <= 0` → 同一个 `"user_id invalid"` | **正整数校验**；浮点字面量（`1.0`）被 `ParseInt` 拒 |
| `:52-54` | `GetTaskReq{ Userid UserIDParam \`json:"id"\` }` | ⭐ **请求只有一个字段，JSON key 是 `id`** |
| `:56-58` | `GetTaskResp{ Tasks []TaskInfo \`json:"tasks"\` }` | |
| `:60-66` | `TaskInfo{ Name \`json:"name"\`, Desc \`json:"desc,optional"\`, Task_id \`json:"taskId"\`, Completion_progress \`json:"completionProgress"\`, Required_progress \`json:"requiredProgress"\` }` | ⭐ **字段名是 Go 风格 snake（`Task_id`）而 JSON key 是 camel（`taskId`）**；`desc` 的 `,optional` 是 goctl 标记，但本域走裸 `encoding/json`，**`optional` 被忽略** ⇒ 空 desc 仍输出 `"desc":""` |

### `service/common/observability/http.go`（api 侧唯一共享依赖）

- `:20-23` `httpClientClosedRequest = 499` / `httpTimeoutBody = "Request Timeout"`。
- `:25-28` `httpEnvelope{Code int json:"code"; Msg string json:"msg"}` —— **observability 自己的错误信封**，与业务响应无关（task-api 不会用到，因为 `httpx.ErrorCtx` 先于它执行？——**UNKNOWN**：中间件在 handler 之外，handler 已经写过响应头后中间件不再改写；但 `http.go` 的超时分支与 panic 分支会自行写响应。**需实跑确认超时分支的实际输出**，标 UNKNOWN）。
- `:30-34` `DisableNativeRestTimeout`。
- `:36-51` `NewHTTPMiddleware` 的 `timeout <= 0` 快路径；`:53` `timeout > 0` 的 `context.WithTimeout` 分支。

### 🔴 go-zero 默认响应路径（`httpx.OkJsonCtx` / `httpx.ErrorCtx`，本域**唯一**生效的响应契约）

`okHandler` 全局为 nil（`task.go` 未调 `SetOkHandler`）：

```
rest/httpx/responses.go:56-64  OkJsonCtx(ctx,w,v)
    → okHandler == nil  ⇒ 跳过包装
    → responses.go:63  WriteJsonCtx(ctx, w, http.StatusOK, v)
    ⇒ ✅ 成功响应：HTTP 200 + application/json + 【原始 v，无 {code,msg,data}】
```

`errorHandler` 全局为 nil（`task.go` 未调 `SetErrorHandler`）：

```
rest/httpx/responses.go:31-37  ErrorCtx(ctx,w,err,fns...)
    → :36  doHandleError(w, err, buildErrorHandler(ctx), writeJson)   // fns 为空
responses.go:127-141           buildErrorHandler → handler == nil
responses.go:143-160           doHandleError:
      :144  if handler == nil {
      :146      if len(fns) > 0 { ... }              // task 侧 fns 为空
      :147      else if errcode.IsGrpcError(err) {   // errcode/grpc.go:45-55
      :149          http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))
      :151      } else {
      :152          http.Error(w, err.Error(), http.StatusBadRequest)
      :153      }
```

⇒ **L1 冻结（三条错误路径，全部是 `text/plain` + `X-Content-Type-Options: nosniff` + 尾随 `\n`）**：

| 触发条件 | Go 事实 | HTTP | body |
|---|---|---|---|
| `id` 缺失/非正整数/非 string-or-number | `types.go:35` 或 `:41`/`:46` 的裸 error → `IsGrpcError` 为 false → `responses.go:152` | **400** | `"user_id invalid\n"` 或 `"id must be a string or integer\n"` |
| rpc 返 `codes.InvalidArgument` | `get_task_logic.go:40-41` 透传 → `errcode/grpc.go:17` 映射 | **400** | gRPC status 文本 |
| rpc 返 `codes.Unknown`（DB 错等，Go gRPC 框架自动包装） | `errcode/grpc.go:31-32` | **500** | gRPC status 文本 |
| body 缺失（`Decode` → `io.EOF`） | `gettaskhandler.go:21-22` | **400** | `"EOF\n"` |
| body 不是合法 JSON | 同上 | **400** | json 语法错误文本 |
| 路由 404 / 405 | go-zero router 自己写 | **404**（body 有）/ **405**（裸） | `common/http/GoStyleRouterErrorResponses.java:95,107` 已复刻 |
| body > 1 MiB | `rest/handler/maxbyteshandler.go:19-22` | **413** | 裸状态码、无 body |

🔴 **与 like-api 的决定性差异**：`like/api/likecenter.go:48-61` 注册了 error handler，两分支都返回 `http.StatusOK` + 信封。
**照抄 like 的 `GlobalExceptionHandler` 到 task-api 就是漂移。** 这正是 `article/.../GoStylePlainTextErrors.java:39-42` 写下的「不要拿另一个域的实测结论当对照基准」。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `task.go:21-36` 进程装配 | `TaskApiApplication` | ABSORB | Spring Boot 启动（`LikeApiApplication.java:63-68` 同款） |
| `task.go:27` `DisableNativeRestTimeout` | 不做（框架无等价默认超时） | ABSORB | Spring MVC 无内建请求超时；语义由 `observability.http-timeout` 单独承担 |
| `task.go:27` + `rest/config.go:55` 默认 `Timeout=3000` | `application.yml` 的 `observability.http-timeout: 3s` | ADAPT | `HttpMiddlewareFilter.java:52-55` 读该键；Go 侧值来自 go-zero tag 默认而非 yaml |
| `task.go:29` `NewHTTPMiddleware` | `common/spring/HttpMiddlewareFilter` | REUSE | `@Component`（`:36-38`）；需 `scanBasePackages` 含 `com.rag.common` |
| `task.go:29` `observability.SlowThreshold()` | `Observability.slowThreshold()`（env fallback） | REUSE | `HttpMiddlewareFilter.java:53-54` 已在 props 为空时回落 |
| `rest/config.go:53,21` 默认 `MaxBytes=1MiB` | `common/protection/GoStyleMaxBytesFilter` | REUSE | `GoStyleProtectionProperties.java:104` 默认已是 `1048576L`；`:109-112` 复刻 413 裸响应 |
| `responses.go:56-64` 成功响应（**无信封**） | `controller/TaskController` 直接返回 `GetTaskResp` | ADAPT | ⭐ **不建 `GlobalResponseAdvice`** —— 加了就多一层 `{code,msg,data}` |
| `responses.go:152` 非 grpc 错误 → 400 text/plain | `advice/GlobalExceptionHandler.goStylePlainTextBadRequest`（私有静态） | ADAPT | 形状照抄 `users/.../GlobalExceptionHandler.java:214-220`；**刻意不建 `http/` 包**，见 §12.7 #5 |
| `responses.go:147-149` grpc 错误 → `CodeFromGrpcError` | `advice/GlobalExceptionHandler.handleRPC` | ADAPT | 必须复刻 `errcode/grpc.go:12-42` 的 15 分支映射（**不是** `users/.../mapRPCStatusToCode` 的 5 分支业务码映射） |
| `routes.go:24` `WithPrefix("/taskcenter/v1")` | `@RequestMapping("/taskcenter/v1")` | ADAPT | 契约本就与 Go 一致；**不"统一"成 `/task/v1`** |
| `routes.go:19` `POST /task/get` | `@PostMapping("/get")` | ADAPT | method + path 1:1 |
| `routes.go` **无 `rest.WithJwt`** | **不建 `interceptor/`** | ABSORB | Go 侧零鉴权；`Auth` 配置（`config.go:14-17` + `task.yaml:9-11`）零消费点，不迁移 |
| `routes.go` **无 CORS / 无 `WithTimeout` / 无 per-route maxBytes** | 不配 CORS / 不配 per-route 超时 | ABSORB | Go 无此装饰器；1 MiB 上限来自 `RestConf` 内建（见上） |
| `gettaskhandler.go` 1 个 handler | `controller/TaskController`（单一） | MERGE | 只有 1 个端点，无合并判断空间 |
| `gettaskhandler.go:19-20` **裸 `json.Decoder`、无 Content-Type 门** | `@RequestBody`（**`required = true`**，用默认） | ADAPT | ⭐ **不注册 `common/http/GoStyleJsonBodyConverter`**（`GoStyleJsonBodyConverter.java:19-30` 会丢弃非 JSON body 的内容） |
| `gettaskhandler.go:21-24` 空 body → `io.EOF` → 400 | `HttpMessageNotReadableException` → text/plain 400 | ADAPT | 形状对齐、文本不对齐（`users/.../GlobalExceptionHandler.java:163-167` 已确立该边界） |
| `gettaskhandler.go:19` 未知字段静默忽略 | 默认 Jackson 行为（不配 `FAIL_ON_UNKNOWN_PROPERTIES`） | ADAPT | Go 侧 `encoding/json` 默认即忽略 |
| `types.go:52-54` `GetTaskReq{id}` | `dto/GetTaskReq` | ADAPT | 单字段 DTO；`id` 声明为 `Object` + 自持解析（见下） |
| `types.go:16-36` `UnmarshalJSON`（string 或 number） | `dto/GetTaskReq.setId(Object)` | ADAPT | 与 `users-common` 的 `GoStyleStringInt64.parse`（`:83-125`）同判据，但**不复用该类**（跨域模块依赖，见 §12.7 #6） |
| `types.go:38-50` `Int64()`（TrimSpace + 正整数） | `dto/GetTaskReq.userIdOrThrow()` | ADAPT | 空/非正/浮点字面量一律 `"user_id invalid"` |
| `types.go:56-58` `GetTaskResp{tasks}` | `dto/GetTaskResp` | ADAPT | 列表**永不为 null**（对齐 `gettasklogic.go:43` 的 `make(..., 0, n)`） |
| `types.go:60-66` `TaskInfo` 5 字段 | `dto/TaskInfo` | ADAPT | JSON key 必须是 `taskId`/`completionProgress`/`requiredProgress`（Go 字段名是 snake、tag 是 camel）；`desc` **不加** `NON_EMPTY` |
| `gettasklogic.go:31-34` uid 解析与失败 | `service/TaskGetService` | ADAPT | 失败 → `IllegalArgumentException` → text/plain 400（与 `types.go:41,46` 的裸 error 等价） |
| `gettasklogic.go:36-41` gRPC 调用 + 错误透传 | `service/TaskGetService` → `client/TaskRpcClient` | ADAPT | **不做 status→int 转换**；错误以 `StatusRuntimeException` 冒泡给 advice |
| `gettasklogic.go:43-52` proto→DTO 投影 | `service/TaskGetService`（私有方法） | MERGE | 单一消费者，不建 `TaskApiSupport` |
| `svc/servicecontext.go:27-32` 唯一 gRPC client | `client/TaskRpcClient`（`@GrpcClient`） | ADAPT | `users/.../UserRpcClient.java:23-27` 同款 |
| `config/config.go` 的 `TaskRpc` / `Telemetry` | `application.yml` 的 `grpc.client.task-rpc.address` + `otel.service.name` | ADAPT | 标准 Spring 绑定；**不建 `config/TaskApiProperties`**（无自有配置段） |
| `config/config.go:14-17` 的 `Auth` | **不迁移** | — | Go 侧零消费点（死配置），不是 ABSORB |
| `svc/servicecontext.go` 的 `Config` 字段持有 | Spring 容器（构造注入） | ABSORB | `zrpc`/`rest` 的依赖持有由 DI 承担 |
| 本域**零指标** | **不建 `metrics/`** | — | Go 侧 `api/` 全树无 metric 调用；§14.4 禁止为填满目录建空类 |
| 本域**零 `WebMvcConfigurer`** | **不建 `config/WebMvcConfig`** | — | 无拦截器要注册；也不注册 `GoStyleCoercion` customizer（Go 侧裸 `encoding/json` 无标量强转规则） |

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/task/task/api/
├── TaskApiApplication.java                 # @SpringBootApplication(scanBasePackages={..., com.rag.common}) + @EnableDiscoveryClient
├── advice/
│   └── GlobalExceptionHandler.java          # 唯一的响应形状来源：gRPC→HTTP 映射 + text/plain 400 + 404/405
├── client/
│   └── TaskRpcClient.java                   # @GrpcClient("task-rpc") → TaskServiceGrpc.TaskServiceBlockingStub
├── controller/
│   └── TaskController.java                  # @RequestMapping("/taskcenter/v1") + @PostMapping("/get")
├── dto/
│   ├── GetTaskReq.java                      # 单字段 id（Object + 自持 string|number 解析 + 正整数校验）
│   ├── GetTaskResp.java                     # tasks（List，永不为 null）
│   └── TaskInfo.java                        # name/desc/taskId/completionProgress/requiredProgress
└── service/
    └── TaskGetService.java                  # uid → gRPC → proto→DTO 投影（本域全部业务）

resources/application.yml                     # server.port=18886 / observability.http-timeout=3s / grpc.client.task-rpc / otel.*
```

**共 8 个 Java 文件 + 1 个资源文件。**

### 刻意不建的目录（逐条给依据）

| 不建的包 | 依据 |
|---|---|
| `config/` | Go 侧 `Config` 的三段里，`TaskRpc` → `grpc.client.*`、`Telemetry` → `otel.*`、`Auth` 是死配置。**零自有配置段 ⇒ 零 `@ConfigurationProperties` 类**（对比 `users/.../config/UserApiProperties.java:18-39` 确有 `user-auth` 段）。`WebMvcConfig` 无拦截器可注册 |
| `interceptor/` | `routes.go:16-25` 无 `rest.WithJwt`。Go 侧该端点**完全公开** |
| `metrics/` | Go 侧 `api/` 全树零 metric 调用 |
| `http/` | 本域的 text/plain 构造只需要 4 行（`HttpHeaders` + Content-Type + nosniff + 尾 `\n`），照 `users/.../GlobalExceptionHandler.java:214-220` 作为**私有静态方法**放进已有的 `advice/GlobalExceptionHandler` 即可，不值得为它开第 9 个包（§14.4） |

### 逐文件说明

| 文件 | 主要职责 | 承载 Go 职责 | 为什么需要独立文件 | 依赖或调用哪些现有组件 |
|---|---|---|---|---|
| `TaskApiApplication` | 入口；`scanBasePackages = {"com.rag.task.task.api", "com.rag.common"}`（后者是 `HttpMiddlewareFilter` / `GoStyleMaxBytesFilter` / `GoStyleRouterErrorResponses` 能被扫到的前提）；`@EnableDiscoveryClient` | `task.go:21-36` | 框架约定 | — |
| `advice/GlobalExceptionHandler` | ① `StatusRuntimeException` → **`errcode/grpc.go:12-42` 的 15 分支状态码映射** + `http.Error` 的 text/plain 形状（**不是** 200 信封）；② `HttpMessageNotReadableException` → 400 text/plain；③ `NoHandlerFoundException` → `GoStyleRouterErrorResponses.notFound`；④ `HttpRequestMethodNotSupportedException` → `.methodNotAllowed(List.of("POST"))`；⑤ 兜底 `Exception` → **400** text/plain（不是 500！Go 侧任何非 grpc 错误都是 400） | `responses.go:143-160`、`errcode/grpc.go`、`gettaskhandler.go:21-24` | 与 users/article 域形状不同（无信封），**照抄别的域的 advice 就是漂移** | `common/http/GoStyleRouterErrorResponses`（REUSE） |
| `client/TaskRpcClient` | 持有 `TaskServiceGrpc.TaskServiceBlockingStub` | `svc/servicecontext.go:27-32` | 外部服务客户端，`client/` 有 5 处先例 | `task-proto` 生成物（已就位） |
| `controller/TaskController` | 1 个 `@PostMapping("/get")`，`@RequestBody` **required=true**，直接返回 `GetTaskResp`（**不包信封**） | `routes.go:16-25`、`gettaskhandler.go:16-33` | HTTP 入口边界 | `service/TaskGetService` |
| `dto/GetTaskReq` | `private Object id` + `setId(Object)`（String→trim；Number→`BigInteger`/`BigDecimal` 分支；Boolean/Array/Object/浮点一律拒）+ `long userIdOrThrow()`（`TrimSpace` → `ParseInt` 语义 → `> 0`） | `types.go:14-54` | 请求契约与校验独立于响应 | 无（纯 Jackson） |
| `dto/GetTaskResp` | `List<TaskInfo> tasks`，构造即置空 list | `types.go:56-58` + `gettasklogic.go:43` | 响应契约 | `dto/TaskInfo` |
| `dto/TaskInfo` | 5 个 `long`/`String`，JSON 名 `name/desc/taskId/completionProgress/requiredProgress` | `types.go:60-66` | 被 `GetTaskResp` 复用 | — |
| `service/TaskGetService` | `getTask(req)`：`userIdOrThrow` → `TaskRpcClient.getTask(GetTaskReq)` → 逐条投影 | `gettasklogic.go:30-56` | 本域全部业务用例 | `client/TaskRpcClient`、`dto/*` |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `api/task.go` | `TaskApiApplication` | ABSORB | 进程装配由框架承担 |
| `api/task.go:27` `DisableNativeRestTimeout` | — | — | Spring MVC 无等价默认超时，无代码可落（不是 ABSORB，是"无对应物"） |
| `api/task.go:27` + `rest/config.go:55` 的 `Timeout=3000` 默认 | `application.yml` `observability.http-timeout: 3s` | ADAPT | 值来自 go-zero tag 默认，Java 侧必须显式写出来 |
| `api/task.go:29` `NewHTTPMiddleware` | `common/spring/HttpMiddlewareFilter` | REUSE | 需 `scanBasePackages` 含 `com.rag.common` |
| `api/task.go:29` `SlowThreshold()` | `Observability.slowThreshold()` | REUSE | `HttpMiddlewareFilter.java:53-54` 已内建回落 |
| `rest/config.go:53,21` 的 `MaxBytes=1MiB` | `common/protection/GoStyleMaxBytesFilter` | REUSE | 默认值已一致（`GoStyleProtectionProperties.java:104`） |
| `api/etc/task.yaml:3` `Port: 18886` | `application.yml` `server.port` | ADAPT | 配置替代 |
| `api/etc/task.yaml:5-7` `TaskRpc.Target` | `application.yml` `grpc.client.task-rpc.address` | ADAPT | 寻址方式待拍板（见 §12.1） |
| `api/etc/task.yaml:13-17` `Telemetry` | `application.yml` `otel.service.name` | ADAPT | 与 `message-rpc/application.yml:126-134` 同款 |
| `api/etc/task.yaml:9-11` `Auth` | — | — | **死配置，零消费点，不迁移** |
| `api/internal/config/config.go:11-18` | `application.yml` 三处标准键 | ADAPT | `TaskRpc`/`Telemetry` 走标准绑定；`Auth` 不迁 |
| `api/internal/svc/servicecontext.go:27-32` | `client/TaskRpcClient` | ADAPT | `zrpc.MustNewClient` + 生成 client → `@GrpcClient` |
| `api/internal/svc/servicecontext.go:22-25` 的 `Config` 字段 | Spring 容器 | ABSORB | 依赖持有由 DI 承担 |
| `api/internal/handler/routes.go:24` `WithPrefix` | `@RequestMapping("/taskcenter/v1")` | ADAPT | 契约本就一致（与 RAG 其它域前缀不同，但那是 Go 的事实） |
| `api/internal/handler/routes.go:19` `POST /task/get` | `@PostMapping("/get")` | ADAPT | method + path 1:1 |
| `api/internal/handler/routes.go` 无 `WithJwt` | — （不建 `interceptor/`） | ABSORB | Go 侧零鉴权 |
| `api/internal/handler/task/gettaskhandler.go:16-33` | `controller/TaskController` | KEEP | 1 handler → 1 controller，不需要合并也没有拆分理由 |
| `gettaskhandler.go:19-20` 裸 `json.Decoder` | `@RequestBody`（required=true）+ **不注册** `GoStyleJsonBodyConverter` | ADAPT | Go 不看 `Content-Type`；共享组件会丢弃 body |
| `gettaskhandler.go:21-24` `Decode` 失败 | `GlobalExceptionHandler.handleUnreadableBody` | ADAPT | 400 text/plain，形状对齐文本不对齐 |
| `gettaskhandler.go:27-32` ok/error 分派 | Controller 直接返回 / advice 接管 | ADAPT | ⭐ **无 okHandler ⇒ 成功路径没有信封** |
| `gettasklogic.go:31-34` uid 解析 | `dto/GetTaskReq.userIdOrThrow()` | SPLIT | 解析是契约的一部分（string\|number + 正整数），与用例分离便于独立测试 |
| `gettasklogic.go:36-41` gRPC 调用 | `service/TaskGetService` + `client/TaskRpcClient` | ADAPT | 错误原样透传，不做业务码转换 |
| `gettasklogic.go:43-52` proto→DTO | `service/TaskGetService`（私有） | MERGE | 单一消费者，不建 Support 类 |
| `gettasklogic.go:53-55` 包装 resp | `dto/GetTaskResp` | ADAPT | — |
| `types/types.go:16-36` `UnmarshalJSON` | `dto/GetTaskReq.setId(Object)` | ADAPT | 自持一份 15 行，不跨域依赖 `users-common` |
| `types/types.go:38-50` `Int64()` | `dto/GetTaskReq.userIdOrThrow()` | ADAPT | — |
| `types/types.go:52-54` `GetTaskReq` | `dto/GetTaskReq` | ADAPT | — |
| `types/types.go:56-58` `GetTaskResp` | `dto/GetTaskResp` | ADAPT | — |
| `types/types.go:60-66` `TaskInfo` | `dto/TaskInfo` | ADAPT | camelCase JSON 名 |
| `responses.go:56-64` `OkJsonCtx` | Controller 直接返回（**不建 `GlobalResponseAdvice`**） | ABSORB | Spring 无内建信封；**刻意不加** |
| `responses.go:143-160` `doHandleError` nil 分支 | `GlobalExceptionHandler`（私有 text/plain 构造） | ADAPT | 形状复刻，**刻意不用 200 信封** |
| `errcode/grpc.go:12-42` `CodeFromGrpcError` | `GlobalExceptionHandler.handleRPC` | ADAPT | 15 分支映射（**不是** users 域的 5 分支业务码映射） |
| `errcode/grpc.go:45-55` `IsGrpcError` | Java 侧由"是否 `StatusRuntimeException`"决定 | ADAPT | 异常类型即判据（`article/.../GoStyleBadRequestException.java:7-35` 的同款纪律） |
| go-zero router 的 404 / 405 | `common/http/GoStyleRouterErrorResponses` | REUSE | 共享实现，跨域形状一致 |
| `service/common/observability/http.go:30-34,36-51` | `HttpMiddlewareFilter` | REUSE | — |
| `service/common/observability/observability.go:41-55` | `Observability.slowThreshold()` | REUSE | — |
| 本域零指标 | — （不建 `metrics/`） | — | Go 侧无此职责，不是 ABSORB |
| 本域零 exception | — （不建 `exception/`） | — | api 层统一抛框架异常 |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ `POST /taskcenter/v1/task/get`；请求体 `{"id": <string\|number>}`；响应 `{"tasks":[{name,desc,taskId,completionProgress,requiredProgress}]}`；**成功无信封**；失败 text/plain + nosniff + 尾 `\n`；状态码 400/413/404/405/500 五种各自来源已逐条钉死 |
| L2 业务语义有承载位置 | ✅ uid 来自 body（无 JWT）、正整数校验、空列表输出 `[]`、`desc` 空值仍输出、`required_progress` 由 rpc 侧定义给出（api 不参与计算） |
| L3 技术语义有承载方案 | ✅ 3s 请求超时（tag 默认推出的 3s 已显式落 yml）、1 MiB body 上限、未知字段忽略、无 `Content-Type` 门、关原生超时 |
| 业务分支与异常路径已纳入 | ✅ `Decode` 失败 / 空 body(`io.EOF`) / `id` 三态非法 / gRPC 四类状态码 / DB 错（→`Unknown`→500）/ 超限 413 / 404 / 405 |
| 框架吸收有机制依据 | ✅ 进程装配→Spring Boot；关原生超时→无对应物；请求拦截→Spring MVC 无内建 |
| 复用组件已明确 | ✅ `TaskRpcClient` 模式、`GoStyleRouterErrorResponses`、`GoStyleMaxBytesFilter`、`HttpMiddlewareFilter`、`task-proto` 生成物 |
| 新增文件有职责依据 | ✅ 8 个文件全部有 Go 出处；**没有一个是"Go 有同名文件所以建"** |
| 合并有组织依据 | ✅ proto→DTO 投影合入 `TaskGetService`（单一消费者，不建 `TaskApiSupport`）；`UserIDParam` 的两段逻辑合入 `GetTaskReq`（它就是该 DTO 的 setter/getter） |
| 拆分可解释 | ✅ uid 解析从 logic 拆到 DTO（契约的一部分，独立可测） |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Handler.java` / `Logic.java` / `Types.java` / `ServiceContext.java` / `Metrics.java` |
| 未把未实现能力说成现有机制 | ✅ `task-api` 现为空 pom + 空 src 目录已在 §12.1 声明；每条"复用"均给出打开过的路径与行号 |
| 无 `common.md` 的依据已写明 | ✅ 见下 |

### 不通过 / 待处理项

| # | 问题 | 详情 |
|---|---|---|
| 1 | 🔴 **路由前缀 `/taskcenter/v1` 与 RAG 全部 6 个已落地 api 不同** | Go 出处 `internal/handler/routes.go:24`。wire 契约必须照抄，但"要不要签字留档"是决策 —— **UNKNOWN，需拍板** |
| 2 | 🔴 **端点零鉴权 + uid 来自 body = 水平越权面** | Go 出处 `routes.go:16-25`（无 `WithJwt`）+ `types.go:52-54` + `gettasklogic.go:31`。任何人可查任意 uid 的任务进度。这是 **Go 的既定行为**，本文件按原样保留；若要补鉴权 = **新增行为**，需拍板 |
| 3 | 🔴 **`127.0.0.1:9005` 与 Nacos 发现** | `etc/task.yaml:6` 硬编码 localhost。RAG 全部 rpc/api 模块走 `discovery://`（`message-rpc/application.yml:83-91`）。改成发现需 task-rpc 已注册 Nacos ⇒ **UNKNOWN，需拍板先直连还是直接上发现** |
| 4 | 🔴 **不得注册 `common/http/GoStyleJsonBodyConverter`** | 它复刻 `httpx.Parse` 的 `withJsonBody`（`GoStyleJsonBodyConverter.java:19-30`），会**丢弃**非 `application/json` body。Go 侧 `gettaskhandler.go:19` 是裸 `json.Decoder`，任何 `Content-Type` 都照读。注册它 = 新增 415/丢弃行为 |
| 5 | ⚠️ **text/plain 构造刻意放在 `advice/GlobalExceptionHandler` 私有方法** | 备选是照 article-api 开 `http/GoStylePlainTextErrors`（`:50-75`）。选前者因为：本域的 text/plain 只有一个来源（error handler nil 分支），4 行足够，且不新增 §3.1 包名集合外的包。若后续 task 域长出第 2 个 text/plain 来源，再抽到 `http/` |
| 6 | ⚠️ **不复用 `users-common` 的 `GoStyleStringInt64`** | 它在 `com.rag.users.common.http`（`GoStyleStringInt64.java:69`），属 users 域 maven 模块。`LikeApiApplication` 的注释已明确「scanBasePackages 刻意不含 `com.rag.users`」的理由是避免拖进 users 的 `SchemaInitializer`；task-api 同理。在 `GetTaskReq` 内自持一份 15 行等价逻辑，判据逐条对齐（string→`parseLong`、整型→`longValue`、`BigInteger.bitLength()>=64` 越界、浮点一律拒） |
| 7 | ⚠️ **`task-api/pom.xml` 无任何依赖** | 缺 `task-proto`、`common`、`spring-boot-starter-web`、`grpc-client-spring-boot-starter`、`spring-boot-starter-data-redis`（protection filter 需要）、`spring-cloud-starter-alibaba-nacos-discovery`。**实现前必须先补 pom**，否则 §12.5 的树一行都编译不过 |
| 8 | ⚠️ **超时分支的实际响应未实测** | `observability/http.go:53` 的超时分支会自行写 `499` + `"Request Timeout"`（`:21-22`），但它与 handler 已写出的响应头如何交互未实跑验证 ⇒ 标 UNKNOWN |
| 9 | ⚠️ **`config.Config` 无 `Telemetry` 字段而 yaml 有该段** | go-zero `conf.MustLoad` 对未知键的严格性未实测 ⇒ UNKNOWN（对 `otel.service.name` 的落点无影响，但会影响"是否还有别的段被静默吞掉"的判断） |

### 关于「本域没有 `common`」的依据（§12.1 末的兑现）

**结论：不创建 `docs/FileTree/task/common.md`，不新建 `task-common` maven 模块。** 四条可验证依据：

1. **Go 侧 `service/task/` 全树没有任何 `common/` 目录**。实测 `find task -type d` 共 18 个目录，**零个** 叫 `common`/`errmsg`；而同级 9 个域里 `article/`、`comment/`、`favorite/`、`follow/`、`hot/`、`like/`、`message/`、`user/` **全部有** `common`（`find . -maxdepth 4 -type d -name common`）。
2. **Go 侧 task 零业务错误码**。`grep -rn "errmsg" service/task/` **零命中**；唯一的错误表达是 `get_task_logic.go:44` 的 `status.Error(codes.InvalidArgument, "user_id invalid")`（**gRPC 标准码 3**）与 `types.go:35,41,46` 的三个 `errors.New` 裸文本。**没有 code→msg 表、没有 `CodeError` 类型、没有业务码空间** ⇒ 没有需要跨模块共享的错误码载体。
3. **错误码的承载位置（本文件的答案）**：**错误表达由类型承载，落在两个地方** ——
   - **api 侧**：`advice/GlobalExceptionHandler`。gRPC 状态码 → HTTP 状态码的映射表（`errcode/grpc.go:12-42`）与 text/plain 形状都在这一个类里。**不需要业务码**，因为 Go 侧根本没有。
   - **rpc 侧**：`service/impl/TaskServiceGrpcImpl` 直接用 `io.grpc.Status.INVALID_ARGUMENT.withDescription("user_id invalid").asRuntimeException()`（`service/task/rpc.md` §12.5 已落此结论）。**不建 `exception/` 包**。
   - 若将来 task 域要引入业务码（当前没有任何需求依据），参照先例新建 `task-common`（`like-common` 的 `errmsg/` 四件套是现成形态）；**在此之前不建**。
4. **RAG 侧已经按此结论落地**：`service/task/pom.xml:14-18` 的 `<modules>` 只有 `task-api` / `task-rpc` / `task-proto` **三项**；而 `service/comment/pom.xml:14-19` 有**四项**（多一个 `comment-common`），`service/comment/comment-common/` 目录实测存在。⇒ 两侧一致，**不是遗漏**。

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `/taskcenter/v1` 前缀是否要签字留档 | Go 事实明确（`routes.go:24`），但与 RAG 其它 6 个 api 前缀全不同，属决策项 |
| api→rpc 寻址：`127.0.0.1:9005` 还是 `discovery:///task-rpc` | 取决于 task-rpc 是否已注册 Nacos；两份文件需一致决策 |
| `observability` 超时分支的实际响应体 | `http.go:53` 分支与 handler 已写响应头的交互未实跑 |
| `config.Config` 无 `Telemetry` 字段时 go-zero 的行为 | 未知键报错 or 忽略，未实测 |
| 是否补端点鉴权 | 补=新增行为（Go 无 `WithJwt`） |