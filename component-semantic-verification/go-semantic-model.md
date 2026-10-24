# Go Semantic Model — `article-api`

> 本文件**只描述 Go 侧**。分析时未查看 Java 源码。
> 所有「实测」数据来自本次自建的探针 `go-probe/`：真实 `go-zero v1.10.2` 引擎 + 与 `article-api` 完全相同的接线 + 真实 HTTP 监听。

---

## 1. Initialization

**顺序（`article.go:23-41`）**

1. 解析 `-f` flag（默认 `etc/article-api.yaml`）
2. `conf.MustLoad(configFile, &c)` —— 用 go-zero 的 tag 默认值填充未写字段
3. `logx.MustSetup(c.Log)` + `logger.Init(c.Name)`
4. `observability.DisableNativeRestTimeout(&c.RestConf)`
   - 读 `conf.Timeout`（= yaml 的 `70000`）→ `TimeoutFromMillis` → **70s**
   - **副作用**：`conf.Middlewares.Timeout = false` ⇒ go-zero 原生 `TimeoutHandler` **不被安装**
5. `rest.MustNewServer(c.RestConf, rest.WithCors())`
   - `WithNotFoundHandler(nil)` 先执行（`server.go:58` 把 `WithNotFoundHandler(nil)` 放在 opts 首位）
   - `WithCors()`（无参）⇒ `server.router.SetNotAllowedHandler(cors.NotAllowedHandler(nil))` + 把 router 包进 `corsRouter`
6. `server.Use(observability.NewHTTPMiddleware(c.Name, 70s, 500ms))`
7. `defer server.Stop()`
8. `svc.NewServiceContext(c)` —— **建 3 个 gRPC client，其中 `SecurityRpc` 零消费者**
9. `handler.RegisterHandlers(server, ctx)` —— 3 段 `AddRoutes`
10. `server.Start()`

**实测**：`MustNewServer` 不报错；`WithCors` 使 notAllowed handler 非 nil（这是 DRIFT-001 的根因）。

---

## 2. Processing（按语义顺序，API 仅作 Evidence）

### 2.1 请求流（`engine.go:104-113` + `chain.go:38-91`）

`chain.Then(h)` 的语义是「`middlewares[0]` 最外层、请求先经过它」。
`appendAuthHandler` 与 `server.Use` 都是 `Append`，即**追加到末尾 = 最内层**… 

⚠️ 更正：`Then` 里 `h = middlewares[len-1-i](h)`，所以**最后一个 append 的 middleware 是最外层**。
实际请求流（外 → 内）：

```
corsRouter.ServeHTTP                    ← server.go:355，router 之外最外层
  observability.NewHTTPMiddleware       ← server.Use 追加，engine 链最后一项 = 最外层
    Trace → Log → Prometheus → MaxConns → Breaker → Shedding
      → [Timeout：已被 DisableNativeRestTimeout 关掉]
      → Recover → Metrics → MaxBytes → Gunzip
        → Authorize (JWT)               ← appendAuthHandler 追加
          → route.Handler
```

**实测确认**：`POST /v1/article` 带 20 MiB Content-Length 且**不带 token** ⇒ 返回 **413**（不是 401），
证明 MaxBytes 确实在 Authorize 之外。

### 2.2 路径匹配（`router/patrouter.go:59-83`）

- `reqPath := path.Clean(r.URL.Path)` —— **先规范化**
- 按 method 找 tree，命中即执行（`pathvar.WithVars` 注入路径变量）
- 未命中 ⇒ `methodsAllowed` 收集其他 method 树里能匹配的
  - **有**匹配 ⇒ `pr.notAllowed.ServeHTTP` ⇒ **`cors.NotAllowedHandler`**（因为 `WithCors` 覆盖了它）
    ⇒ 对非 OPTIONS 写 **404**（`handlers.go:47`），**没有 `Allow` 头，没有 body，没有 Content-Type**
  - **无**匹配 ⇒ `engine.notFoundHandler(nil)` ⇒ `http.NotFoundHandler()` ⇒ `http.Error(w, "404 page not found", 404)`
    ⇒ **有** body（含尾 `\n`）+ `text/plain; charset=utf-8` + `nosniff`

**实测**：
```
MISMATCH POST /v1/article/1 -> 404 ct="" nosniff="" allow="" body=""
UNREGISTERED /v1/nope      -> 404 ct="text/plain; charset=utf-8" nosniff="nosniff" allow="" body="404 page not found\n"
trailing slash  /v1/articles/ -> 200
```

### 2.3 CORS（`internal/cors/handlers.go:52-68`）

`corsRouter` 在 **router 之外**，因此 **404 / 405 / 预检全都会带 CORS 头**。
`WithCors()` 无参 ⇒ `checkAndSetHeaders` 走 `len(origins)==0` 分支 ⇒ `setHeader(w, "*")` ⇒
`Access-Control-Allow-Credentials` **不写**（`handlers.go:111` 的 `if origin != allOrigins`）。

`OPTIONS` ⇒ `w.WriteHeader(204)`，**handler 完全不执行**（无论路径是否注册）。

**实测**：5 个头全部命中、无 Allow-Credentials、已注册与未注册路径的 OPTIONS 都是 204 空 body。

### 2.4 保护层（全部来自 `rest/config.go:12-22` 的 `json:",default=true"`，yaml 里一个字没写）

| 层 | 生效值 | 判据 | 输出 |
|---|---|---|---|
| MaxConns | 10000（`config.go:52` tag 默认） | `latch.TryBorrow()` 失败 | 裸 **503** |
| Breaker | 开 | `brk.Allow()` 失败 | 裸 **503** |
| Shedding | 开，`CpuThreshold=900`（千分比=90%） | `shedder.Allow()` 失败 | 裸 **503** |
| MaxBytes | **10485760**（yaml 覆盖 tag 默认 1048576；`engine.checkedMaxBytes` 使 route 级与 conf 级同值） | **`r.ContentLength > n`**（chunked 时 `-1`，放行） | 裸 **413** |
| Recover | 开 | `recover()` | 裸 **500** |
| Trace / Log / Prometheus / Metrics / Gunzip | 开 | — | 不改响应 |

⚠️ **Breaker 的判据是 `cw.Code < 500 ⇒ Accept()`**（`breakerhandler.go:33`）。
本组件的业务错误恒 **HTTP 200**，所以**全部被记为「成功」**。
能触发熔断的只有 **超时（503）** 与 **panic（500）**。熔断的真实职责不是保护下游。

### 2.5 超时（`common/observability/http.go:36-92`）

- 跳过条件：`Upgrade: websocket` 或 `Accept: text/event-stream`（`EqualFold` 比较）
- 否则 `context.WithTimeout(r.Context(), 70s)`，在**独立 goroutine** 里跑 handler
- 响应写入一个**缓冲的** `observabilityResponseWriter`，handler 返回后才 flush
- 超时 ⇒ **`http.Error(w, "Request Timeout", 503)`**，**丢弃缓冲**；handler goroutine **不 kill**，
  但它手上的 ctx 已到期 ⇒ 所有 ctx-aware 的下游 I/O（gRPC）立即中止
- 因为 `Middlewares.Timeout=false`，`http.Server` 的 `ReadTimeout`/`WriteTimeout` **也不会被设置**
  （`engine.withNetworkTimeout` 的前置是 `hasTimeout()` = `conf.Middlewares.Timeout && timeout > 0`）

**副作用（两侧都必须知道）**：响应是**缓冲后一次性发出**的 ⇒ 没有流式输出能力；`Content-Length` 由 Go 在 flush 时计算。

### 2.6 请求解析（`httpx/requests.go:49-77`）

`httpx.Parse` 固定顺序：**path → form → header → JSON body**。
本组件的 6 个信封端点没有 header 字段，`encoding.ParseHeaders` 对无匹配字段是 no-op。

**JSON body 的判据（`requests.go:153-155`）**：
```go
return r.ContentLength > 0 && strings.Contains(r.Header.Get("Content-Type"), "application/json")
```
- 不满足 ⇒ `mapping.UnmarshalJsonMap(nil, v)` ⇒ **完全不看 body**，当空对象继续 ⇒ 必填字段报 `is not set`
- 满足 ⇒ `io.LimitReader(r.Body, maxBodyLen)`，**`maxBodyLen = 8 << 20` = 8388608**（`requests.go:21`）

**实测**：
```
7MiB JSON body  -> err=<nil>（解析成功）
9MiB JSON body  -> err=string: `…` （截断到 8388741 字节后解析失败）
11MiB JSON body -> 同上
```
⇒ **8 MiB ~ 10 MiB 这条带里，Go 必定返回 400 text/plain**。

**必填 / optional / null 的三条实测**（`core/mapping/unmarshaler.go`）：
```
{"title":"t","content":"x"}                -> nil
{"content":"x"}                             -> field "title" is not set          (newInitError)
{"title":null,"content":"x"}                -> field "title" mustn't be nil     (unmarshaler.go:862，optional=false)
{"title":"t","content":"x","brief":null}    -> nil                                (unmarshaler.go:858-860，optional=true ⇒ 接受 null)
{"title":"t","content":"x","status":null}   -> nil                                (同上)
{"title":123,"content":"x"}                 -> type mismatch for field "title", expect "string", actual "number"
{"title":"t","content":"x","status":"2"}     -> type mismatch for field "status"
{"title":"","content":"x"}                   -> nil                                （空串是合法值）
{"title":"t","content":"x","zzz":1}          -> nil                                （未知字段忽略）
{"title":"t","content":"x","tags":"a"}       -> error（类型不符）
```

**query 解析（`httpx/util.go:22-65` + `core/mapping/utils.go:105-125`）**：
- 空值（`len(v)==0`）**整体丢弃** ⇒ `?page=`、`?page`、不传三者等价 ⇒ 走 `,default=`
- 默认值只在 key **缺席**时填（`unmarshaler.go:919`）；key 存在但解析失败 ⇒ 400
- bool：`1` / `true`(EqualFold) / `0` / `false`(EqualFold)，其余 `errTypeMismatch`
- int32：`strconv.ParseInt(str, 10, 32)`
- String：原样透传，不 trim

---

## 3. External Interaction

| # | Operation | Input | Expected | Failure | 结果影响分支 | 失败阻断 | 副作用 |
|---|---|---|---|---|---|---|---|
| E-1 | `GetArticle(public_only=true)` | `{article_id, incr_view, public_only}` | 找到已发布文章 | `NOT_FOUND`→1002 / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 若 `incr_view` 则 +1 |
| E-2 | `GetArticle(requester_id=me)` | `{article_id, requester_id}` | 文章 + 作者 | `NOT_FOUND`→1002 / `PERMISSION_DENIED`→1006 / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 无 |
| E-3 | `ListArticles(public_only=true)` | 4 筛选(optional) + page/page_size/sort_by/desc | 文章页 | `INTERNAL`→5001 / 其它→**1015（连 NOT_FOUND 也是 1015）** | 是 | 是 | 无 |
| E-4 | `CreateArticle` | 7 字段 | 新文章 id | `ALREADY_EXISTS`→1001(data=null) / `FAILED_PRECONDITION`→**1004(data 带 id)** / `DEADLINE_EXCEEDED`→**1003(data 带 id)** / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 写库 + 发布流水线 |
| E-5 | `UpdateArticle` | 仅非空字段 | success | `NOT_FOUND`→1002 / `INTERNAL`→5001 / 其它→**1015（无 PERMISSION_DENIED 分支）** | 是 | 是 | 写库 |
| E-6 | `DeleteArticle` | `{article_id, operator_id}` | 无 | `NOT_FOUND`→1002 / `PERMISSION_DENIED`→1006 / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 软删 |
| E-7 | `UploadFile` | `{content, file_name}` | file_url | **裸 err 向上抛**，不进业务码 | 是 | 是 | 写对象存储 |
| E-8 | `GetUser` | `{uid int64}` | 用户 | **4 条分支全部回落昵称，不阻断** | 否（只影响昵称） | **否** | 无 |

**E-3 的不对称（实测自 Go 自带测试 `public_read_handler_test.go:140`）**：列表里出现非 PUBLISHED 行 ⇒ 整条 5001。

**E-1 的安全闸门**：`res.Article.Status != PUBLISHED` ⇒ 1002（不是 1015、不是 1006），防老版本下游忽略 `public_only` 导致草稿泄露。

---

## 4. Result Semantics（严格区分）

| 概念 | Go 的表达 | 落点 |
|---|---|---|
| Success | `code=200` | HTTP 200 + 信封 |
| Not Found（文章） | `code=1002` | HTTP **200** + 信封，`data:null` |
| Not Found（用户） | `resp.Found == false` | **不是错误** → 回落 `用户 <id>` |
| Empty（列表空） | `articles == nil` | HTTP 200 + 信封，`data.articles: null`（**不是** `[]`） |
| Null vs Missing | `ext_info` 空 map → **nil**；`author_name` 空串 → **key 消失** | 见 `component-contract.md` §4 |
| Default | `page=1` / `page_size=20` / `sort_by=create_time` / `desc=true` | 仅在缺席时 |
| Unauthorized（无 token） | HTTP **401 空 body** | 不在信封里 |
| Unauthorized（有 token 无 userId） | `code=1005` | HTTP **200** |
| Dependency Failure | `code=1015` | HTTP 200 |
| Timeout | HTTP **503 + `Request Timeout\n`** | 裸 |
| Invalid Input | HTTP **400 + text/plain** | 裸 |

⚠️ **`res == nil` 的两处映射不同**（Go 侧自己就写得不一致）：
`get_article_logic.go:54` ⇒ **1002**；`list_articles_logic.go:58` ⇒ **5001**。Java 侧照抄了这两处不对称。

---

## 5. Error Semantics

### 5.1 身份解析（`helper.go:17-39`）

```
uid := ctx.Value("userId")     ← key 是裸字符串 "userId"
if uid == nil            -> 1005
json.Number  -> TrimSpace(字面量) 非空则接受
string       -> TrimSpace(整串)  非空则接受
default      -> TrimSpace(fmt.Sprintf("%v", uid)) 非空且 != "<nil>"
否则          -> 1005
```
claim 里的 `userId` 是 **JSON 数字** ⇒ go-zero 的 `token.NewTokenParser` 用 `jwt.WithJSONNumber()`
（`tokenparser.go:123`）⇒ claims 值是 `json.Number` ⇒ 走第一条分支。

**实测**：`{"userId":1001}` ⇒ `ACCEPTED userId=1001 (type json.Number)`。

### 5.2 JWT 提取（`jwt/v4 request.AuthorizationHeaderExtractor`）

**实测**：
```
Bearer <tok>          -> ACCEPTED
<tok>（无 Bearer 前缀） -> ACCEPTED      ← 前缀可选！
bearer <tok>          -> ACCEPTED      ← 大小写不敏感
Basic <tok>           -> REJECTED      ← "Basic " 前缀被当作 scheme，剩下的是 "<tok>" 被当 base64 的一部分 → 解不开
Bearer（只有这个词）    -> REJECTED
```

### 5.3 文章 id 从 status 文案抠取（`create_article_logic.go:82-92`）

```
message == ""                -> ""
无 ":"                       -> ""
":" 在末尾 (idx+1 >= len)      -> ""
其它                         -> TrimSpace(LastIndex(":") 之后的部分)
```
⚠️ 这是约定式协议，**脆弱**，但它是当前真源行为。

### 5.4 作者昵称（`helper.go:71-103`）—— 四条失败分支全部不冒泡

```
authorID TrimSpace 后为空            -> "未知作者"
strconv.ParseInt(10,64) 失败 或 uid<=0 -> "用户 " + authorID
GetUser RPC 失败                       -> "用户 " + authorID（记日志）
resp==nil || !Found || User==nil        -> "用户 " + authorID
Username TrimSpace 后为空              -> "用户 " + authorID
```
批量场景用**请求内 map** 去重（`helper.go:53`），空 authorId **不发 RPC**。

---

## 6. State Semantics

| 状态 | 初始 | 转换 | 终止 |
|---|---|---|---|
| 请求 ctx | `Background` + Trace span | 加 70s deadline（observability） | 请求结束 / 超时 |
| HTTP 响应缓冲 | 空（status=200） | handler 写入 | handler 返回 ⇒ flush；或超时 ⇒ 丢弃 |
| 熔断器计数 | 0 | 每次请求 +1；`code<500` ⇒ Accept | 常驻，无过期 |
| 并发 latch | 10000 可借 | 借/还 | 常驻 |
| gRPC 连接 | 启动期建 | 每请求调用 | 进程退出 |
| **业务数据** | — | — | **组件内不持有任何业务状态** |

无过期、无持久化、无事务边界（gateway 层）。

---

## 7. Concurrency

| 维度 | Go 侧事实 |
|---|---|
| Concurrent Calls | 无限制；由 MaxConns latch（10000/路由）限制 |
| Mutual Exclusion | MaxConns 的 `syncx.NewLimit`；CPU shedding 的 promise |
| Duplicate Execution | **无幂等保护**。OP-2/OP-3 是「先读后写」两次调用，读与写之间**存在 TOCTOU 窗口** |
| Atomicity | OP-2/OP-3 的读+写**不是原子的**（两个独立 RPC） |
| Lost Update | OP-2 无版本号 ⇒ 后写覆盖先写 |
| Visibility | 全部经 gRPC，天然可见 |
| Idempotency | OP-1 非幂等（重复提交 ⇒ 1001）；OP-2/OP-3 非幂等；OP-5 读操作但 `incr_view` 有副作用 |
| Ordering | 单请求内严格顺序（goroutine 顺序执行）；跨请求无序 |

---

## 8. Timeout / Retry

**Timeout**
- HTTP 层：70s，`context.WithTimeout`，**scope = 整个 operation**（不是单次网络读）
- 传播：`r.WithContext(ctx)` ⇒ 全部 gRPC 调用继承同一个 deadline
- `CreateArticle` 额外显式 `zrpc.WithCallTimeout(70*time.Second)`（`create_article_logic.go:53`）
  —— 与 HTTP 预算**同值**，所以本部署下两者无差别
- 其余调用靠 ctx deadline，无独立超时
- ⚠️ 原生 `TimeoutHandler` 被关掉 ⇒ `http.Server.ReadTimeout`/`WriteTimeout` **不生效**
- 触发后：goroutine 不 kill，但 ctx 到期 ⇒ gRPC 立即返回 `DeadlineExceeded` ⇒ 落到业务码 1015（若超时先发生）或 503（若 middleware 先 select 到）

**Retry**
- **无**。`zrpc.RpcClientConf`（`zrpc/config.go`）**没有 `Retries` 字段**
- ⚠️ 注意别与 `zrpc.ClientConf` 混淆（后者有 `Retries`，api 用的是前者）
- 但 **client breaker 有**且默认开：`zrpc/internal/config.go:14` `Breaker bool json:",default=true"`，
  `zrpc/internal/client.go:112-113` 追加 `BreakerInterceptor`，
  `clientinterceptors/breakerinterceptor.go:13-18` **每个 RPC 方法一个 breaker**
- ⚠️ `zrpc/internal/codes/accept.go` 的失败名单只有 6 个 code
  （`DeadlineExceeded`/`Internal`/`Unavailable`/`DataLoss`/`Unimplemented`/`ResourceExhausted`）
  ⇒ **业务错误（`NotFound`/`PermissionDenied`/`InvalidArgument`）在 Go 侧被记为 Accept，不参与熔断**

---

## 9. Serialization

**出参**
- `jsonx.Marshal`（`core/jsonx/json.go:12-30`）：`json.Encoder` + **`SetEscapeHTML(false)`** ⇒ `<` `>` `&` **不转义**
- **`encoding/json` 对 map key 排序** ⇒ `ext_info` 输出是**字典序**
- 无 `omitempty` 的字段恒出现（`null` 也出现）
- `author_name` 有 `,omitempty` ⇒ 空串时 key 消失
- 信封三字段无 `omitempty` ⇒ `data` 恒出现

**入参**
- 未知字段忽略
- `,optional` 接受显式 null；无 `,optional` 的字段显式 null ⇒ `mustn't be nil`
- `,omitempty` **不设置任何 unmarshal 选项**（`core/mapping/utils.go:372-385` 的 `parseOption` 只认
  `inherit` / `string` / `optional` 前缀）⇒ 等价于必填

---

## 10. Lifecycle

| 阶段 | Go 行为 |
|---|---|
| Startup | 读 yaml → 建 logx → 关原生 timeout → 建 server+router+CORS → 挂 observability → **建 3 个 gRPC client** → 注册 3 段路由 → Start |
| Ready | `server.Start()` 阻塞 |
| Shutdown | `defer server.Stop()`（只 `logx.Close()`）+ go-zero 默认优雅关闭 |
| ⚠️ 启动期硬依赖 | `zrpc.MustNewClient` 无 etcd ⇒ **直接 panic**。本组件**无法在无 etcd 环境启动** |

---

## 11. Go 侧实测记录（探针输出，原文）

见 `go-facts.txt` 与本文档 §2.2 / §2.6 / §5.2 的引用。
