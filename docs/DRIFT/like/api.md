# like-api 语义对照台账

> **核对日期**：2026-10-23（本轮复核 · 组件级 · Go → Spring Boot）
> **Go 侧**：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/like/api`
> **RAG 侧**：`/Users/pr/Documents/java&golang/RAG/service/like/like-api`
> **方法**：`.agents/specs/semantic-verification-prompt.md`（Go 重建基准 → RAG 对照）

## 证据强度声明（本轮）

| 编号 | 证据类型 | 具体手段 | 可证明什么 / 不能证明什么 |
|---|---|---|---|
| **E-Go** | **Go 活体探针** | `/tmp/likeprobe/main.go`，独立小程序，直接调 `go-zero v1.10.2` 的 `httpx.Parse` / `OkJsonCtx` / `SetErrorHandler`，struct 定义逐字复制自 `internal/types/types.go`。56 条输入矩阵 + 6 条 envelope 字节 + 3 条错误路径字节 | Go 的**输入解释与出参字节**。不含业务逻辑（无 like-rpc），不含 HTTP 层（路由/JWT/超时） |
| **E-Rag** | **RAG 活体探针（真实 MVC 管线）** | `/tmp/likeprobe/RagProbe.java` + `NullElem.java`。用生产 `JsonMapper`（`LikeGoStyleCoercion.applyTo` 原样装配）、生产 DTO、`GoStyleRequest.normalize`、生产 `GoStyleJsonBodyConverter`、生产 `GlobalExceptionHandler` / `GlobalResponseAdvice`，以 `MockMvcBuilders.standaloneSetup` 驱动；proto 用生产 `com.rag.like.like.proto` 生成类 | RAG 的**输入解释 / 必填判定 / 响应形状**。⚠️ `MockMvc` 不等价于真 Tomcat（不参与 `Content-Length` 真实计算、不驱动 converter 排序以外的容器行为） |
| **E-Tomcat** | RAG 真容器测试 | `LikeApiRouterErrorRealTomcatTest`（`RANDOM_PORT` + JDK HttpClient 读原始响应），本轮 `mvn -o -pl service/like/like-api test` 实跑 **62 tests / 0 failures / 0 errors** | 404/405 的字节级形状、鉴权遮蔽边界 |
| **E-Src** | 静态源码 | 两侧源码逐行对照，Go 侧引用到**消费点**行号 | 只证明「代码写成了这样」，**不证明运行期真的这样** |
| **E-Cfg** | 配置 + 装配源码 | yaml ↔ struct tag ↔ 过滤器注册链 | 「配置与装配代码都在」，**不等于运行期生效**（AGENTS.md §3.3） |

> ⚠️ **本组件绿测试 62 条全绿** —— 但绿测试**不是**「无漂移」的证据（AGENTS.md §0）。
> 本轮 6 条 SEMANTIC MISMATCH **全部落在现有 62 条测试的覆盖之外**。

---

## 1. Component Contract

| | 内容 | 出处 |
|---|---|---|
| **Purpose** | HTTP → gRPC 网关。**零业务逻辑**：解析、鉴权、转发、映射错误 | `internal/handler/routes.go`、`internal/logic/like/*.go`（全部逻辑 = 取 uid + 调 rpc + 搬字段） |
| **Public Operations** | **6 个 POST**，全部前缀 `/like/v1`、全部要 JWT、**零 public 路径** | `internal/handler/routes.go:16-51` |
| **Inputs** | JSON body（`Content-Type` 含 `application/json` 且 `ContentLength > 0` 时才读）+ `Authorization: Bearer <jwt>` | `rest/httpx/requests.go:117-124,153-155` |
| **Outputs** | 恒 `200` + `{"code":200,"msg":"OK","data":{…}}`；错误恒 `200` + `{"code":<业务码>,"msg":…,"data":null}` | `likecenter.go:41-61`、`rest/httpx/responses.go:161-172` |
| **Dependencies** | ① `like-rpc`（gRPC，etcd `like.rpc`）② JWT 验签密钥 ③ go-zero 自带的 Trace/Log/Prometheus/MaxConns/Breaker/Shedding/Recover/Metrics/MaxBytes/Gunzip | `internal/svc/servicecontext.go:18-23`、`etc/likecenter.yaml:31-36`、`rest/config.go:11-23` |
| **State** | **无**。本组件不碰数据库、不碰缓存 | 全仓 grep 无 DB/Redis 引用 |
| **Side Effects** | 只发出 gRPC 调用（含 `likeaction` 这条写路径）。无本地写、无 MQ | `internal/logic/like/*.go` |
| **Failure Model** | 解析失败/必填缺失 → `200 + 5001`；身份解析失败 → `200 + 1006`；鉴权失败 → `401 + 空 body`；路由错误 → `404` / `405` 裸；超时 → `503 + "Request Timeout"` | 见 §5 |

---

## 2. GO-SEMANTIC-MODEL

### 请求链（按执行顺序）

```
1. engine.start → bindRoutes → bindRoute                rest/engine.go:101-116
   ├─ chain = 原生中间件 (Trace/Log/Prom/MaxConns/Breaker/Shedding/Recover/Metrics/MaxBytes/Gunzip)
   ├─ chain += handler.Authorize(secret)                rest/handler/authhandler.go:45-84
   ├─ chain += observability.NewHTTPMiddleware(3s)     likecenter.go:36-38
   └─ router.Handle(POST, path, chain.ThenFunc(handler))
   ⚠️ chain 只挂在**匹配上**的路由上 ⇒ 404/405 分支进不了 chain
      （rest/router/patrouter.go:73,79）

2. Authorize: token 从 Authorization 头取（BearerExtractor 要求 "bearer " 前缀，大小写不敏感）
   jwt.NewParser(jwt.WithJSONNumber())                  rest/token/tokenparser.go:122-124
   ⇒ 数字 claim 解出来是 json.Number（其 Kind == String）
   失败 ⇒ unauthorized()：**只 WriteHeader(401)，不写一个字节**  authhandler.go:106-122

3. 超时中间件（3s，独立 goroutine）                     service/common/observability/http.go:41-92
   ctx, cancel := context.WithTimeout(r.Context(), 3s)   :53
   超时 ⇒ http.Error(w, "Request Timeout", 503)          :88

4. httpx.Parse                                          rest/httpx/requests.go:50-77
   ├─ ParsePath / ParseForm / ParseHeaders（本 DTO 无对应 tag ⇒ no-op）
   └─ ParseJsonBody                                      :117-124
      withJsonBody(r) = r.ContentLength > 0
                     && strings.Contains(Header.Get("Content-Type"), "application/json")   :153-155
      假 ⇒ mapping.UnmarshalJsonMap(nil, v)   ← **完全不看 body**
      真 ⇒ mapping.UnmarshalJsonReader(LimitReader(body, 8MB), v)

5. 逐字段（mapping.Unmarshaler，key = "json"）
   标量缺席        ⇒ newInitError         unmarshaler.go:944-970 default 分支 :967-969
   标量/数组显式 null ⇒ mustn't be nil     unmarshaler.go:856-862
   Slice/Map/Array 缺席且非 optional ⇒ processFieldNotFromString(emptyMap)
                    → default: → processFieldPrimitive
                    → typeKind(Slice) ≠ valueKind(Map) ⇒ newTypeMismatchError  :944-950 → :638-641 → :643-662
   ,string 字段     ⇒ processNamedFieldWithValueFromString → strconv.ParseInt    :889-916
   options=1|2|3|4 ⇒ validateValueInOptions(Repr(v), opts)                       :673-675
   Slice 元素        ⇒ fillSliceValue：json.Number 命中 fmt.Stringer 分支
                    ⇒ setValueFromString(String, …) ⇒ **数字被字符串化接受**      :227-231

6. logic：ctx.Value("userId").(json.Number) → .Int64()   getlikestatelogic.go:37-49 等 4 处
   失败 ⇒ NewErrCode(ErrorTokenRuntime) ⇒ 1006 + "TOKEN已过期"

7. gRPC：zrpc.MustNewClient(c.LikeRpc)
   RpcClientConf.Timeout 默认 **2000ms**（zrpc/config.go:31）
   → WithTimeout(2s)                                    zrpc/client.go:70-72
   → TimeoutInterceptor：每次调用 context.WithTimeout(ctx, 2s)
                                                          clientinterceptors/timeoutinterceptor.go:17-30
   无 retry（RpcClientConf 无 Retries 字段）；client breaker 默认开（default=true）

8. 出参：httpx.OkJsonCtx → jsonx.Marshal（**SetEscapeHTML(false)**）core/jsonx/json.go:12-30
   Content-Type: application/json; charset=utf-8
```

### 逐 OP 语义（§6 的 18 字段）

| OP | Input | Precondition | Processing | External Call | Branch | Result | State | Error | Side Effect | Timeout | Retry | Concurrency | Ordering |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **getlikecount** | `target_type`(必填 str) `target_ids`(必填 []str) | JWT 有效；不解析 uid | 直传两个字段 | `GetLikeCount` | 无业务分支 | `counts` = `make(map,…)` ⇒ **恒 `{}`，永不为 null** | 无 | status 错→`NewErrCodeMsg(code, st.Message())`；否则 5001 | 只发 RPC | HTTP 3s / RPC 2s | 无 | 每请求一 goroutine | 无 |
| **getlikestate** | 同上 + uid | 同上 | uid 从 ctx 取 | `GetLikeState` | uid 非 json.Number / 非整数 → 1006 | `states` 直传（rpc 返回 nil map 时会是 `null`） | 无 | 同上 | 只发 RPC | 同上 | 无 | 同上 | 无 |
| **gettargetlikerlist** | 4 个**全必填**标量 | 同上 | 直传 | `GetTargetLikerList` | 无 | `list` = `make([]…,0,…)` ⇒ **恒 `[]`** | 无 | 同上 | 只发 RPC | 同上 | 无 | 同上 | 无 |
| **getuserlikelist** | 3 个全必填标量 + uid | 同上 | 同上 | `GetUserLikeList` | 同 getlikestate | `list` 恒 `[]` | 无 | 同上 | 只发 RPC | 同上 | 无 | 同上 | 无 |
| **getusertotallike** | **零字段** | 同上 | 只取 uid | `GetUserTotalLike` | 同 getlikestate | 直传 | 无 | 同上 | 只发 RPC | 同上 | 无 | 同上 | 无 |
| **likeaction** | `target_type` `target_id` `action_type`(必填,options) `author_id`(必填,string) + 3 个 optional | 同上 | `weight` **float64→float32 窄化**；`success` **硬编码 true**（不读 proto） | `LikeAction` | options 越界 → 解析期失败 | 同上 | 无 | 同上 | **写路径** RPC | 同上 | 无 | 同上 | 无 |

---

## 3. SPRINGBOOT-SEMANTIC-MODEL

### 请求链（对应上表逐行）

```
1. @Component HttpMiddlewareFilter @Order(HIGHEST_PRECEDENCE+5)
   ← 由 scanBasePackages 含 "com.rag.common" 自动注册        LikeApiApplication.java:63-66
   → HttpMiddleware.newHttpMiddleware(...)                  HttpMiddlewareFilter.java:52-56
2. WebMvcConfig.addInterceptors：/like/v1/** → LikeJwtAuthInterceptor
   ⚠️ 无 excludePathPatterns（Go 侧没有 public 端点）
   ⚠️ preHandle 首行 `handler instanceof HandlerMethod` 守卫（DRIFT-011 修复）
3. JwtUtil/BearerTokenExtractor + jjwt parseSignedClaims
   ⇒ claim 存**原始对象**（不转 long），解析身份在业务层 LikeApiSupport#userId
4. 3s 超时（observability.http-timeout: 3s）→ worker 线程 + CountDownLatch.await(3s)
   ⚠️ 与 Go 的关键差异：Go 用 ctx 传播 + 独立 goroutine；Java 用 Future.cancel(true)
   ⇒ 取消产生的 status 是 CANCELLED，Go 是 DEADLINE_EXCEEDED
5. @RequestBody(required=false) + GoStyleJsonBodyConverter
   goReadsBody(ct, len) = len > 0 && ct != null && ct.contains("application/json")
   ⚠️ ct 取自 `HttpHeaders.getContentType().toString()`（**已归一化为小写**）
      Go 取自 `r.Header.Get("Content-Type")`（**原始头，大小写敏感**）
   假 ⇒ 返回 null → GoStyleRequest.normalize 造零值 DTO → 必填校验
6. Jackson 3（tools.jackson 3.1.5）+ LikeGoStyleCoercion
   规则1 LogicalType.Textual ← Integer/Float/Boolean = Fail
   规则2 LogicalType.Integer ← String = Fail；LogicalType.Float ← String = Fail
   规则3 ACCEPT_FLOAT_AS_INT = 关闭
   ⚠️ **Jackson 3 默认开启 FAIL_ON_TRAILING_TOKENS**（Jackson 2 默认关闭）
7. LikeApiSupport#userId：只接受整型（Byte/Short/Integer/Long/BigInteger，bitLength<64）
8. LikeRpcClient.likeRpc() → GoStyleRequestDeadline.applyTo(stub)
   ⇒ deadline = **HTTP 超时的剩余预算**（≤3s），不是 Go 的固定 2000ms
```

---

## 4. Equivalence Matrix

> Result 只用 5 个枚举值。Evidence 见本文件头部 E-Go / E-Rag / E-Src / E-Cfg。

| # | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| 1 | all 6 | 路由表（6 POST / 前缀 / 全要 JWT / 零 public） | `routes.go:16-51` | `LikeController` + `WebMvcConfig:44` | EQUIVALENT | E-Src + E-Tomcat |
| 2 | all 6 | 鉴权 401 + **零字节 body** | `authhandler.go:106-122` | `GlobalExceptionHandler:86-90` | EQUIVALENT | E-Tomcat |
| 3 | all 6 | 未知路径恒 404（**不经鉴权**） | `engine.go:101-116` + `patrouter.go:73` | `preHandle` 的 `HandlerMethod` 守卫 | EQUIVALENT | E-Tomcat |
| 4 | all 6 | 405 裸状态码 + `Allow` 头，不排序 | `patrouter.go:79-82,101-117` | `GoStyleRouterErrorResponses` | EQUIVALENT | E-Tomcat |
| 5 | all 6 | 成功 envelope 恒 `200 + {code:200,msg:"OK",data}` | `likecenter.go:41-47` | `GlobalResponseAdvice:59-72` | EQUIVALENT | E-Go(F01/F05) + E-Rag(H) |
| 6 | all 6 | envelope **只包一层**（`@ResponseBodyAdvice` 二次包裹） | n/a | `body==null \|\| body instanceof Response` 双守卫 | EQUIVALENT | E-Rag(A01..A13) |
| 7 | all 6 | HTML 字符**不转义** | `jsonx.Marshal` `SetEscapeHTML(false)` | Jackson 3 默认 escapes 不含 `<>&` | EQUIVALENT | E-Go(F03) + E-Rag(H) |
| 8 | all 6 | 解析失败 → `200 + 5001` | `responses.go:161-172` | `handleUnreadableBody` / `handleGoRequiredField` | EQUIVALENT | E-Go(G02) + E-Rag |
| 9 | all 6 | 非 JSON Content-Type ⇒ **丢弃 body**（不是 415） | `requests.go:153-155` | `GoStyleJsonBodyConverter.read` 返回 null | EQUIVALENT | **E-Go(A01/A02/A09) + E-Rag(A01/A02/A09)** |
| 10 | getusertotallike | 零字段 DTO + 非 JSON CT ⇒ **成功并调下游** | E-Go(A09/E03) | E-Rag(A10/A11) | EQUIVALENT | **双侧活体对拍** |
| 11 | all 6 | **Content-Type 大小写** | 用**原始头** `strings.Contains`（大小写敏感） | 用**解析后的 MediaType.toString()**（已小写化） | **SEMANTIC MISMATCH** | **双侧活体对拍（A04/A08b）** ⇒ **DRIFT-015** |
| 12 | all 6 | chunked（`ContentLength==-1`）⇒ 丢弃 body | E-Go(A07/A08) | `goReadsBody("application/json",-1)=false` | EQUIVALENT | E-Go + E-Rag（转换器级；⚠️ 未在真 Tomcat 上跑 chunked，见 §9） |
| 13 | getlikecount/getlikestate | **Slice 元素是 JSON 数字** | **接受**并字符串化 `["1","2"]` | **拒绝**（Textual←Integer=Fail） | **SEMANTIC MISMATCH** | **双侧活体对拍（B05/B06/B28）** ⇒ **DRIFT-012** |
| 14 | getlikecount/getlikestate | **Slice 元素是 `null`** | `[null]`→nil；`["x",null,"y"]`→`["x","","y"]` | 保留 null → proto **NPE** → 5001 且不调下游 | **SEMANTIC MISMATCH** | **双侧活体对拍（B08/B09 + NullElem）** ⇒ **DRIFT-013** |
| 15 | all 6 | **JSON 尾随 token** `{…} xx` / `{…}{…}` | **接受**（只解第一个值） | **拒绝**（Jackson 3 默认 `FAIL_ON_TRAILING_TOKENS`） | **SEMANTIC MISMATCH** | **双侧活体对拍（B21/B22）** ⇒ **DRIFT-014** |
| 16 | likeaction | **`weight: 1e400`** | **拒**（`ParseFloat` out of range） | **接受成 `Infinity`** → 发出带 `+Inf` 的 RPC | **SEMANTIC MISMATCH** | **双侧活体对拍（D07）** ⇒ **DRIFT-016** |
| 17 | all 6 | JSON 键大小写敏感 / 重复键后者胜 / 未知键忽略 | `TARGET_IDS` 拒；重复键后者胜；`zzz` 忽略 | 同 | EQUIVALENT | E-Go(B11/B23/B27) + E-Rag 同 |
| 18 | all 6 | 顶层非对象（`[1,2]` / `"s"` / `123`） | 解析失败 → 5001 | 解析失败 → 5001 | EQUIVALENT | E-Go(B17-B20) + E-Rag 同 |
| 19 | 4 个标量 DTO | `cursor`/`page_size` **必填**；`0` 合法 | `:967-969` | `GoStyleRequired.field` | EQUIVALENT | E-Go(C01-C11) + E-Rag 同 |
| 20 | likeaction | `action_type` `options=1\|2\|3\|4`，`0/5/1.0/"1"` 全拒 | `:673-675` | `ALLOWED_ACTION_TYPES` | EQUIVALENT | E-Go(D11-D15) + E-Rag 同 |
| 21 | likeaction | `author_id` 字符串与数字**都收**；浮点/布尔/空串/越界拒 | `:889-916` | `GoStyleStringInt64` | EQUIVALENT | E-Go(D16-D26) + E-Rag 同 |
| 22 | likeaction | 标量 String 字段拒 JSON 数字 | `processFieldPrimitive:648-661` | `Textual←Integer/Float/Boolean=Fail` | EQUIVALENT | E-Go(B12/D10) + E-Rag 同 |
| 23 | likeaction | 无 `,string` 的整数字段拒 JSON 字符串 | `:643-662` | `Integer←String=Fail` | EQUIVALENT | E-Go(C04/D14) + E-Rag 同 |
| 24 | likeaction | `,optional` 缺席与显式 null 都合法 | `:856-860` | 无必填校验 | EQUIVALENT | E-Go(D03/D09) + E-Rag 同 |
| 25 | likeaction | `weight` float64→float32 窄化 | `likeactionlogic.go:56` | `LikeActionService:58` | EQUIVALENT | E-Src + E-Rag |
| 26 | likeaction | `success` **硬编码 true** | `likeactionlogic.go:71` | `LikeActionService:66` | EQUIVALENT | E-Src |
| 27 | 4 个端点 | 身份失败 ⇒ `200 + 1006`（不是 401/5001） | `:37-49` | `LikeApiSupport:95-126` | EQUIVALENT | E-Src + 现有绿测 |
| 28 | 2 个端点 | **鉴权不对称**：`getlikecount`/`gettargetlikerlist` 不读 uid | 全文无 `ctx.Value("userId")` | 不注入 `HttpServletRequest` | EQUIVALENT | E-Src + 现有绿测 |
| 29 | all 6 | gRPC 错误二分：status→业务码+`st.Message()` 原文；非 status→**5001**（不是 1015） | `:41-47` ×6 | `LikeApiSupport:177-186` | EQUIVALENT | E-Src |
| 30 | all 6 | **HTTP 超时 3s → 503 + `Request Timeout`** | `rest/config.go:55` + `http.go:88` | `application.yml:27-29` + `HttpMiddleware:208+` | EQUIVALENT（配置+装配层） | E-Go 源码链 + E-Cfg。⚠️ **运行期 503 出口本轮未在真 Tomcat 上复现** ⇒ 见 §9 |
| 31 | all 6 | 超时**作用域** = 整个操作（含鉴权+解析+RPC） | `NewHTTPMiddleware` 包住 chain 全段 | `HttpMiddleware` 包住 `chain.doFilter` | EQUIVALENT | E-Src |
| 32 | all 6 | 超时后的**取消语义** | ctx 到期 ⇒ `DEADLINE_EXCEEDED`（在 `codes.Acceptable` 失败名单内） | `Future.cancel(true)` ⇒ `CANCELLED`（**不在名单内**） | OBSERVABLE DIFFERENCE | E-Src（`GoStyleRequestDeadline` javadoc 已自陈） |
| 33 | all 6 | **单次 RPC 超时** | **固定 2000ms**（`zrpc/config.go:31` → `zrpc/client.go:70-72`） | **HTTP 剩余预算**（≤3000ms），无独立 2s 上限 | **SEMANTIC MISMATCH** | E-Go(E-Go 源码链) + E-Src ⇒ **DRIFT-017** |
| 34 | all 6 | gRPC **无 retry** | `RpcClientConf` 无 `Retries` | 无 retry | EQUIVALENT | E-Src |
| 35 | all 6 | 响应 `Content-Type` | `application/json; charset=utf-8`（分号后**有空格**） | `application/json`（**无 charset 参数**） | OBSERVABLE DIFFERENCE | E-Go(F01) + E-Rag(A03)。按 RFC 9110 语义等价（JSON 恒 UTF-8） ⇒ **DRIFT-021** |
| 36 | all 6 | 容量饱和时的拒绝出口 | 无上限 goroutine，**无此出口** | worker 池 512 上界，饱和 ⇒ **裸 503 无 body** | OBSERVABLE DIFFERENCE | E-Src（`HttpMiddleware:296-317`） ⇒ **DRIFT-022** |
| 37 | all 6 | 事务边界 | n/a（无 DB） | n/a | EQUIVALENT | E-Src |
| 38 | all 6 | 状态机 | n/a（无本地状态） | n/a | EQUIVALENT | E-Src |
| 39 | all 6 | 缓存语义 | n/a（无缓存） | n/a | EQUIVALENT | E-Src |
| 40 | all 6 | 并发幂等 / 重复调用 | 无客户端可见副作用（重复 likeaction 由 rpc 侧决定） | 同 | UNKNOWN | ⚠️ 下游 like-rpc 未审（并行会话），见 §9 |

---

## 5. External Interaction & Error Flow

### 5.1 External System：like-rpc（gRPC）

```
External System : like-rpc（Go: etcd Key "like.rpc"；RAG: Nacos discovery:///like-rpc）
Operation       : 6 个 unary 调用，全部同步阻塞
Input           : proto message（两侧 wire 契约一致 —— 见 AGENTS.md §3.4，like-proto 2026-10-23 实测 6/6 一致）
Parameters      : 见 §2 逐 OP 表
Expected Result : 成功 / status error
Failure Result  : 见下表
Does Failure Block Operation : 是（同步阻塞，失败即返回 envelope）
Side Effect     : likeaction 会改 like-rpc 侧的数据（本组件无本地写）
```

| 失败形态 | Go | RAG | Result |
|---|---|---|---|
| gRPC status error（有 code + description） | `NewErrCodeMsg(int(st.Code()), st.Message())` → 业务码 + **description 原文** | `CodeError.newCodeMsg(bizCode, status.getDescription())` | EQUIVALENT |
| gRPC status error，description 为空 | `NewErrCodeMsg(code, "")` | `description == null ? "" : description` | EQUIVALENT |
| 非 status 错误（连接失败 / 序列化失败） | `NewErrCode(5001)` → `5001 + "系统内部错误"` | `extractStatus()==null` → 5001 | EQUIVALENT |
| 超时被取消 | `DEADLINE_EXCEEDED` | `CANCELLED`（补 deadline 后为 `DEADLINE_EXCEEDED`） | 见矩阵 #32 |

### 5.2 ERROR FLOW 汇总

| Failure | 触发条件 | Go 输出 | Spring Boot 输出 | Semantic Equivalent |
|---|---|---|---|---|
| 鉴权失败 | 无头 / 非 Bearer / 签名错 / 过期 | `401` + **0 字节** | `401` + **0 字节** | YES |
| 身份解析失败 | `userId` 非 json.Number / 非整数 / 越界 | `200` + `{1006,"TOKEN已过期"}` | 同 | YES |
| body 不可读 | 畸形 JSON / 类型不匹配 / 尾随 token / 顶层非对象 | `200` + `{5001}` | `200` + `{5001}` | YES（文本不进 body） |
| 必填缺失 | 标量缺席 / Slice 缺席 | `200` + `{5001}` | `200` + `{5001}` | YES |
| **Content-Type 大小写** | `APPLICATION/JSON` | `200` + `{5001}`，**不发 RPC** | `200` + 成功，**发 RPC** | **NO** ⇒ DRIFT-015 |
| **Slice 内是数字** | `target_ids:[1,2]` | `200`，**发 RPC**（`["1","2"]`） | `200` + `{5001}`，不发 RPC | **NO** ⇒ DRIFT-012 |
| **Slice 内有 null** | `target_ids:["x",null,"y"]` | `200`，**发 RPC**（`["x","","y"]`） | `200` + `{5001}`，不发 RPC | **NO** ⇒ DRIFT-013 |
| options 越界 | `action_type:0/5` | `200` + `{5001}` | 同 | YES |
| RPC 业务错误 | rpc 返回 status | `200` + 业务码 | 同 | YES |
| 请求超时 | >3s | `503` + `Request Timeout\n`（text/plain） | `503` + `Request Timeout`（text/plain） | YES（配置层），运行期未复现 |
| 路径未知 | `/like/v1/nope` | `404` + `404 page not found\n` | 同（真 Tomcat 实测） | YES |
| 方法不支持 | `GET /like/v1/getlikecount` | `405` 裸 + `Allow` | 同 | YES |

---

## 6. 逐维度结论

### 6.1 State Semantics（§12）
本组件**无状态**（不碰 DB / 缓存）。唯一「状态」是请求内的 uid 解析结果。
⇒ **EQUIVALENT**。

### 6.2 Cache Semantic Equivalence（§13）
两侧都**没有缓存**。Go `ServiceContext` 只有 `Config` + `LikeRpc`。
⇒ **EQUIVALENT**（无此维度）。

### 6.3 Authentication / Authorization（§14）
| 维度 | Go | RAG | Result |
|---|---|---|---|
| Identity Source | `Authorization: Bearer <jwt>` | 同 | EQUIVALENT |
| Token Parsing | `BearerExtractor`，要求 `bearer ` 前缀（大小写不敏感），否则 `ErrNoTokenInRequest` | `BearerTokenExtractor`（同源实现） | EQUIVALENT |
| Signature | HMAC，`[]byte(AccessSecret)` 原样作 key（36 字节） | `Keys.hmacShaKeyFor(padToHmacKeySize(secret))`（jjwt 要求 ≥32 字节，36 已满足 ⇒ pad 是 no-op） | EQUIVALENT |
| Claim Extraction | 非标准 claim 全部塞 ctx；`userId` 是 `json.Number`（因 `WithJSONNumber`） | 只落 `userId`（其余 claim 无消费者） | EQUIVALENT（本组件只消费 `userId`） |
| Claim Default | 类型断言失败 ⇒ `!ok` ⇒ 1006 | `instanceof` 链不命中 ⇒ 1006 | EQUIVALENT |
| JSON 字符串 claim `"userId":"123"` | 解出来是 Go `string` ⇒ 断言失败 ⇒ **1006** | jjwt 给 `String` ⇒ 不命中整型链 ⇒ **1006** | EQUIVALENT |
| Exp / nbf | `MapClaims.Valid()` 校验 `exp`/`nbf`/`iat` | jjwt 校验 | EQUIVALENT |
| Failure Mapping | 三种失败都只到 `unauthorized()` ⇒ `401` 零字节 | 同（`catch (Throwable)`） | EQUIVALENT |
| public 路径集合 | **空集** | **空集**（无 exclude） | EQUIVALENT |
| 黑名单中间件 | ⚠️ **like 域根本不存在**（全仓 grep `blacklist` 在 `service/like/` 与 `service/common/` 均**零命中**） | 相应地也没有 | EQUIVALENT（该维度不适用 —— **不是「两侧都做了」，是两侧都没有**） |

⇒ **EQUIVALENT**。

### 6.4 Transaction Semantics（§15）
本组件**无事务边界**（无 DB 写）。`likeaction` 的写发生在 like-rpc 侧。
⇒ **EQUIVALENT**（无此维度）。

### 6.5 Concurrency Semantics（§16）
| 维度 | Go | RAG | Result |
|---|---|---|---|
| 并发模型 | 每请求一 goroutine，无上限 | 容器线程 + worker 池（`SynchronousQueue`，上限 512） | OBSERVABLE DIFFERENCE ⇒ DRIFT-022 |
| 重复执行 | 每次请求独立 | 同 | EQUIVALENT |
| 幂等 | likeaction 的幂等性由 rpc 侧决定 | 同 | UNKNOWN（下游未审） |
| 超时时的资源释放 | ctx 传播 ⇒ I/O 立即中止，goroutine 迅速退出 | `Future.cancel(true)` ⇒ 阻塞 stub 可中断，但 status 是 `CANCELLED` | OBSERVABLE DIFFERENCE（矩阵 #32） |

### 6.6 Timeout Semantics（§17）
| | Go | RAG | Result |
|---|---|---|---|
| **HTTP 超时来源** | `rest/config.go:55` `Timeout int64 json:",default=3000"`（yaml 未写 ⇒ 3000） | `observability.http-timeout: 3s`（显式写） | EQUIVALENT（值一致） |
| **HTTP 超时作用域** | 整个请求（`NewHTTPMiddleware` 包 chain 全段） | 整个请求（`chain.doFilter`） | EQUIVALENT |
| **HTTP 超时结果** | `503` + `http.Error` ⇒ text/plain + nosniff + 尾随换行 | `503` + `Request Timeout` | EQUIVALENT（字节级待真 Tomcat 复核） |
| **单次 RPC 超时来源** | `RpcClientConf.Timeout` 默认 **2000**（yaml 未写） | 无独立项；取 HTTP 剩余预算 | **SEMANTIC MISMATCH** ⇒ DRIFT-017 |
| **单次 RPC 超时作用域** | 单次调用 | 单次调用 | EQUIVALENT |
| 超时传播 | `r.WithContext(ctx)` 传进业务 | `GoStyleRequestDeadline` ThreadLocal + `withDeadlineAfter` | EQUIVALENT（机制不同，效果近似） |

### 6.7 Retry Semantics（§18）
Go `RpcClientConf` **没有** `Retries` 字段；RAG 也未配任何重试。
⚠️ 注意别与 `zrpc.ClientConf`（有 `Retries`）混淆 —— like api 用的是前者。
⇒ **EQUIVALENT**（两侧都不重试）。

### 6.8 Serialization Semantics（§19）
| 维度 | Go | RAG | Result |
|---|---|---|---|
| JSON 库 | `core/jsonx`（`encoding/json` + `UseNumber` + `SetEscapeHTML(false)`） | Jackson 3（`tools.jackson` 3.1.5） | IMPLEMENTATION DIFFERENCE |
| 数字解码形态 | `json.Number`（Kind == String） | 默认按目标类型解 | 见 DRIFT-012/016 |
| Field Mapping | `json` tag | `@JsonProperty` | EQUIVALENT（6/6 字段名逐条一致，见 §4 #17） |
| Default / Null / Empty | 必填看「键在不在」，空串合法 | `GoStyleRequired.field` 只判 `null` | EQUIVALENT |
| 未知字段 | **忽略** | **忽略**（实测 B11 通过） | EQUIVALENT |
| 重复键 | 后者胜 | 后者胜（B23） | EQUIVALENT |
| 尾随 token | **接受** | **拒绝** | **SEMANTIC MISMATCH** ⇒ DRIFT-014 |
| `null` vs 缺失 | 两者都失败，文案不同 | 两者都失败，文案不同 | EQUIVALENT |
| Enum | `pb.ActionType(int64)` 强转 | `ActionType.forNumber` | EQUIVALENT（options 已先挡） |
| Timestamp | int64 透传 | int64 透传 | EQUIVALENT |
| HTML 转义 | 关闭 | 默认关闭 | EQUIVALENT |

### 6.9 Configuration Semantic Equivalence（§20）
| Go yaml | 值 | RAG | 值 | Result |
|---|---|---|---|---|
| `Host/Port` | `0.0.0.0 / 18887` | `server.port` | `18887` | EQUIVALENT |
| `Name` | `likecenter` | `spring.application.name` | `likecenter` | EQUIVALENT |
| `UserAuth.AccessSecret` | `ae05…5da0` | `like-api.user-auth.access-secret` | 同 | EQUIVALENT |
| `UserAuth.AccessExpire` | `31536000`（**本模块不读**） | 同值保留 | EQUIVALENT |
| `Timeout`（**yaml 没写**，tag 默认 3000） | 3000ms | `observability.http-timeout: 3s` | 3000ms | EQUIVALENT |
| `MaxConns`（tag 默认 10000） | 10000 | `rag.protection.max-conns` | 10000 | EQUIVALENT |
| `MaxBytes`（tag 默认 1048576） | 1 MiB | `rag.protection.max-bytes` | 1 MiB | EQUIVALENT |
| `CpuThreshold`（tag 默认 900） | 900 | `rag.protection.cpu-shedding.cpu-threshold` | 900 | EQUIVALENT |
| `LikeRpc.Etcd.Key` | `like.rpc` | `grpc.client.like-rpc.address=discovery:///like-rpc` | 寻址方式不同，语义等价 | IMPLEMENTATION DIFFERENCE |
| `LikeRpc.Timeout`（**yaml 没写**，tag 默认 **2000**） | **2000ms** | ❌ 无对应配置 | — | **SEMANTIC MISMATCH** ⇒ DRIFT-017 |
| `DevServer`（端口 9099 + `/metrics` + `/healthz`） | **⚠️ 见 §9 U-06：Go 侧无人消费** | actuator `health,info,prometheus`（18887） | — | UNKNOWN |
| `Log` / `Telemetry` | 有 | 有 | — | UNKNOWN（不在行为面） |

### 6.10 Lifecycle（§21）
启动顺序：Go = 读配置 → 建 rpc client → 注册路由 → Start；RAG = Spring 上下文 → Filter/Interceptor/Controller 注册 → 就绪。
⚠️ RAG 的 `HttpMiddlewareFilter` 是 `@Order(HIGHEST_PRECEDENCE+5)`，**包在 DispatcherServlet 外**，因此它在 Controller Bean 就绪前就已注册 —— 但它只在请求到达时用链上的 bean，**不存在**「worker 先于依赖就绪」的窗口。
⇒ **EQUIVALENT**。

---

## 7. Semantic Drifts

> 编号连续：历史占用 `DRIFT-LIKEAPI-001` ~ `-011`；本轮新增从 **-012** 起。

### 7.1 历史条目状态（2026-10-23 复核，**原文保留**）

| ID | 项 | 分类 | 状态 |
|---|---|---|---|
| **DRIFT-LIKEAPI-001** | HTTP 请求超时 | SEMANTIC MISMATCH | ✅ **2026-10-23 复核：已修**。`application.yml:27-29` 已配 `observability.http-timeout: 3s`；装配链完整：`HttpMiddlewareFilter`（`@Component` + `@Order(HIGHEST_PRECEDENCE+5)`）← `LikeApiApplication:63-66` 的 `scanBasePackages` 含 `com.rag.common` ⇒ 自动注册。⚠️ **按 §3.3 记「配置+装配已核对，运行期 503 出口未复现」**（本轮探针未起真 Tomcat 打超时路径） |
| **DRIFT-LIKEAPI-002** | `target_ids` 必填性 | SEMANTIC MISMATCH | ✅ **2026-10-23 复核：已修且结论成立**。Go 活体探针 B02 复现：`type mismatch for field "target_ids"`；B03 `mustn't be nil`。RAG 侧 `GetLikeCountReq#assertGoRequiredFields` 抛 `GoRequiredFieldException` ⇒ 200+5001。⚠️ **但遗留 3 处未清理的旧断言**：`-018`（本文件外）与 `LikeReqGoStyleValidationTest` 类注释 ⇒ 见 **DRIFT-LIKEAPI-019** |
| ~~DRIFT-LIKEAPI-003~~ | 请求保护层四层全缺 | — | ✅ 已关（2026-10-22）。本轮未复审 |
| ~~DRIFT-LIKEAPI-004~~ | 超限请求优先级（413 而非 401） | — | ✅ 已关（2026-10-22）。本轮未复审 |
| ~~DRIFT-LIKEAPI-005~~ | gRPC client 熔断缺失 | — | ✅ 已关（2026-10-22）。本轮未复审 |
| **DRIFT-LIKEAPI-010** | `LikeApiSupport#userId` 的 javadoc 说反了 | 文档致高危 | ✅ **2026-10-23 复核：已修**。`LikeApiSupport.java:79-90` 现在写的是「JSON 字符串 claim（含 `"123"`）一律拒绝 ⇒ 1006」，与 `golang-jwt v4.5.2 parser.go:154-156` 的 `UseNumber` 只影响 JSON 数字一致。代码本来是对的，注释也已对齐 |
| ~~DRIFT-LIKEAPI-006~~ | 非 JSON Content-Type | — | ✅ **2026-10-23 复核：确认真关闭**。双侧活体对拍 A01/A02/A09/A10/A11 全部一致（Go 丢弃 body → 200+5001；零字段 DTO 两侧都成功并调下游）。原签字理由「两侧都是拒绝」确实被推翻过，现在靠 `GoStyleJsonBodyConverter` 真正对齐了 |
| ~~DRIFT-LIKEAPI-007~~ | 404 空 body | — | ✅ 已关。E-Tomcat 本轮实跑 `LikeApiRouterErrorRealTomcatTest` 4 条全绿 |
| ~~DRIFT-LIKEAPI-008~~ | 405 缺 `Allow` 头 | — | ✅ 已关（同上，同一测试类） |
| **DRIFT-LIKEAPI-011** | 未知路径被鉴权遮蔽 | SEMANTIC | ✅ 已修。E-Tomcat `routerErrorsBypassAuth` 实跑通过 |
| ~~DRIFT-LIKEAPI-009~~ | `author_id = -2^63` 被误拒 | — | ❌ 复核为误报，已撤销。⚠️ **本轮探针再次印证**：`GoStyleStringInt64` 的 `bitLength() >= 64` 判据正确（`Long.MIN_VALUE` 的 bitLength 是 63），见矩阵 #21（D 系列全绿） |

### 7.2 本轮新增

---

#### **DRIFT-LIKEAPI-012** — `target_ids` 里放 JSON **数字**：Go 接受，RAG 拒

| 字段 | 内容 |
|---|---|
| **Component** | like-api（`GetLikeCountReq` / `GetLikeStateReq`） |
| **Operation** | `POST /like/v1/getlikecount`、`POST /like/v1/getlikestate` |
| **Category** | Serialization / Input Interpretation |
| **Go Behavior** | `{"target_type":"article","target_ids":[1,2]}` ⇒ **解析成功**，`TargetIds == ["1","2"]`，**真的发出 RPC**。`[1.5]` ⇒ `["1.5"]`。`[99999999999999999999]` ⇒ `["99999999999999999999"]`（连溢出都不管，就是字面量） |
| **Java Behavior** | 同样三个 body **全部解析失败** ⇒ `200 + {"code":5001}`，**不发出 RPC** |
| **Difference** | Go 的 `fillSliceValue`（`core/mapping/unmarshaler.go:227-231`）的 type switch 里有 `case fmt.Stringer:`，而 `json.Number` **实现了** `Stringer`（`type Number string`）⇒ 数字被字符串化接受。RAG 用 `LikeGoStyleCoercion` 的 `LogicalType.Textual ← Integer/Float = Fail` 把它挡掉 |
| **Why It Matters** | 这是 **go-zero 自身的不对称**：同一个 JSON 数字，进**标量** String 字段被拒（`unmarshaler.go:648-661` 的 `processFieldPrimitive`）、进 **Slice 元素**却被收（`fillSliceValue` 没走那条路）。只看「String 字段拒数字」这条规则去实现整个解析层，必然漏掉 Slice 这一半 |
| **Observable Impact** | Go 返回真实点赞数；RAG 返回 5001。**方向是「RAG 更严」**，属拒绝而非错误数据 |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | **HIGH**（§26 反事实检验：**YES** —— 一个没读过 `fillSliceValue` 的 Spring 开发者，几乎必然会用 `List<String>` + 「数字不许进 String」的全局 coercion，**正是现在 RAG 的样子**。这是「不看控制流就会写成的样子」的教科书案例） |
| **Must Preserve** | `target_ids` 的**元素**允许 JSON 数字，且按 `json.Number.String()` 原样字符串化 |
| **Recommended Verification** | ① 在 `GetLikeCountReq` / `GetLikeStateReq` 的 `setTargetIds` 改用 `Object` 入参 + 一个仿 `GoStyleStringInt64` 的元素级解析器（只对 Slice 元素放开 Integer/Float，标量规则不动）；② 或给 DTO 加显式元素级 coercion 豁免。**⚠️ 注意 Jackson 3 的 coercion 配置只有 LogicalType 级生效，类级无效**（`GoStyleCoercion:74-83` 已实测记录此坑），所以**不要指望 `withCoercionConfig(SomeListClass, …)`** |
| **Evidence** | **双侧活体对拍**：Go `/tmp/likeprobe` B05 `OK ids=[1 2]` / B06 `OK ids=[1.5]` / B28 `OK ids=[99999999999999999999]`；RAG 同输入 `InvalidFormatException: Cannot coerce Integer value (1) to java.lang.String`。消费点：`core/mapping/unmarshaler.go:227-231`（`fillSliceValue` 的 `case fmt.Stringer`） |

---

#### **DRIFT-LIKEAPI-013** — `target_ids` 里的 **`null` 元素**：Go 填空串，RAG 崩在 protobuf

| 字段 | 内容 |
|---|---|
| **Component** | like-api |
| **Operation** | `getlikecount` / `getlikestate` |
| **Category** | Serialization / External Interaction（下游调用是否发生） |
| **Go Behavior** | `{"target_ids":[null]}` ⇒ **解析成功**，slice **保持 nil**（`fillSlice` 的 `valid` 标志未被置位 ⇒ `value.Set(conv)` 不执行，`unmarshaler.go:156-183`）⇒ 发出**空**的 target_ids 的 RPC。`{"target_ids":["x",null,"y"]}` ⇒ **`["x","","y"]`**（`conv` 长度仍是 3，null 位留成零值空串，`unmarshaler.go:158-160` 的 `continue`） |
| **Java Behavior** | 两者都解析成功，但保留 null 元素 ⇒ `LikeCountService#idList` 原样透传 ⇒ `addAllTargetIds(...)` 抛 **`NullPointerException: Element at index N is null.`** ⇒ `GlobalExceptionHandler#handleAny` 兜成 `200 + {"code":5001}`，**下游 RPC 没被调用** |
| **Difference** | 两层叠加：① 解析层对 null 元素的处置不同；② protobuf 的 `addAll` 对 null 元素是硬失败 |
| **Observable Impact** | Go 返回 like-rpc 的 2001（`getlikecountlogic.go:44` 的「输入需要查询的ID为空」）；RAG 返回 5001 |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | **MEDIUM**（触发需要客户端在数组里塞 null；但**失败方向是「本该成功的读变成 5001」**，且错误被 NPE 兜底掩盖，排查成本高） |
| **Must Preserve** | Go 对 null 元素的实际语义：全 null ⇒ nil slice；部分 null ⇒ 该位变空串且**长度不变** |
| **Recommended Verification** | 在 `LikeCountService` / `LikeStateService` 的 `idList` 里把 null 元素替换成 `""`；并单独钉死「全 null ⇒ 空列表」与「部分 null ⇒ 长度不变、null 位为空串」两条（后者**不是**过滤，Go 不会缩短数组） |
| **Evidence** | **双侧活体对拍**：Go B08 `OK ids=[] nil=true` / B09 `OK ids=[]string{"x", "", "y"}`；RAG `ids=[null]` / `ids=[x, null, y]`，proto 侧 `/tmp/likeprobe/NullElem.java` 实测 `NullPointerException: Element at index 0 is null.`。消费点：`core/mapping/unmarshaler.go:156-160,181-183` |

---

#### **DRIFT-LIKEAPI-014** — JSON **尾随 token**：Go 接受第一个值，Jackson 3 拒绝

| 字段 | 内容 |
|---|---|
| **Component** | like-api（全部 6 个端点） |
| **Operation** | 全部 |
| **Category** | Serialization |
| **Go Behavior** | `json.Decoder.Decode` 只解**第一个** JSON 值，其后的内容既不报错也不检查（`core/jsonx/json.go:74-77`）。`{…} xx` ⇒ **解析成功**；`{…}{…}` ⇒ **解析成功**，用的是第一个对象 |
| **Java Behavior** | 两者都**解析失败**。尾随 token 的报错文案直接点名了机制：`not allowed as per DeserializationFeature.FAIL_ON_TRAILING_TOKENS` |
| **Difference** | ⚠️ **Jackson 3 的默认值与 Jackson 2 相反**：Jackson 2 的 `FAIL_ON_TRAILING_TOKENS` 默认 **false**，Jackson 3（`tools.jackson` 3.1.5）默认 **true**。这是「升级 Jackson 大版本时静默改变默认行为」的又一例 |
| **Observable Impact** | Go 接受并按第一个对象执行（**会发出 RPC**）；RAG 返回 5001。含 `likeaction` ⇒ **写路径**也受影响 |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | **LOW-MEDIUM**（正常客户端不会发尾随 token；恶意/畸形客户端的行为两侧不同但都「不该成功」——⚠️ 严格说 Go 侧是**会成功**的，所以这是 Go 侧的一个宽松点，RAG 更严） |
| **Must Preserve** | 二选一必须**显式决策**：要么在 RAG 侧关掉 `FAIL_ON_TRAILING_TOKENS` 以对齐 Go，要么把这条登记为**签字的有意偏离**。⚠️ **当前的沉默状态最坏**：既没关，也没有任何台账记录它 |
| **Recommended Verification** | 在 `LikeGoStyleCoercion.applyTo` 里 `builder.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)`（或显式 `enable` 并签字），并加一条锁死该判定的测试 |
| **Evidence** | **双侧活体对拍**：Go B21 `OK tt="a"` / B22 `OK tt="a"`（取第一个对象）；RAG B21 `StreamReadException: Unrecognized token 'xx'` / B22 `MismatchedInputException: Trailing token … not allowed as per DeserializationFeature.FAIL_ON_TRAILING_TOKENS`。消费点：`core/jsonx/json.go:74-77` |

---

#### **DRIFT-LIKEAPI-015** — `Content-Type` **大小写**：Go 用原始头，RAG 用已归一化的 MediaType

| 字段 | 内容 |
|---|---|
| **Component** | like-api（全部 6 个端点） |
| **Operation** | 全部 |
| **Category** | Configuration / Input Interpretation |
| **Go Behavior** | `withJsonBody` 用 `strings.Contains(r.Header.Get("Content-Type"), "application/json")` —— `Header.Get` 返回**原始头字符串**，`strings.Contains` **大小写敏感**。`Content-Type: APPLICATION/JSON` ⇒ 不命中 ⇒ **body 被整个丢弃** ⇒ `200 + 5001`，**不发 RPC** |
| **Java Behavior** | `GoStyleJsonBodyConverter.read` 取 `inputMessage.getHeaders().getContentType().toString()` —— 这是**解析后的 `MediaType`**，媒体类型部分已被规范化为**小写** ⇒ `application/json` ⇒ 命中 ⇒ **正常解析并发出 RPC** ⇒ `200 + 成功` |
| **Difference** | 判据的**输入不同**：一个用未解析的原始头，一个用解析后的规范形式。`GoStyleJsonBodyConverter.goReadsBody(ct, len)` 这个 public static 方法**直接单测是测不出问题的**（传 `"APPLICATION/JSON"` 进去确实返回 `false`），因为**规范化发生在它之前** —— 又一个「测试直接调判据函数 ≠ 端点行为」的形态 |
| **Observable Impact** | 同一请求：Go 5001 不发 RPC；RAG 200 成功并发 RPC。`likeaction` ⇒ **写路径**受影响 |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | **MEDIUM**（HTTP 规范说媒体类型大小写不敏感 ⇒ 客户端写大写是合法请求；Go 因为用了字面串比较而丢弃，两侧行为必须分出胜负） |
| **Must Preserve** | 判据必须作用在**未经解析的原始头**上（`request.getHeader("Content-Type")`），而不是 `getContentType().toString()` |
| **Recommended Verification** | 改 `GoStyleJsonBodyConverter.read` 传入 `((HttpServletRequest) …).getHeader("Content-Type")`（或 `inputMessage.getHeaders().getFirst("Content-Type")`）；加一条**走完整管线**（不能只测 `goReadsBody`）的用例钉死 `Content-Type: APPLICATION/JSON` |
| **Evidence** | **双侧活体对拍**：Go A04 `Content-Type: APPLICATION/JSON` ⇒ `ERR field "target_type" is not set`；RAG A04/A08b ⇒ `200 {"code":200,…,"echo_ids":"[1]"}`。消费点：`rest/httpx/requests.go:153-155`（`r.Header.Get` 原始头）vs `GoStyleJsonBodyConverter.java:115-118`（`getContentType().toString()`） |

---

#### **DRIFT-LIKEAPI-016** — `weight: 1e400`：Go 拒，RAG 收成 `Infinity`

| 字段 | 内容 |
|---|---|
| **Component** | like-api |
| **Operation** | `POST /like/v1/likeaction` |
| **Category** | Serialization（写路径） |
| **Go Behavior** | `strconv.ParseFloat: parsing "1e400": value out of range` ⇒ 解析失败 ⇒ `200 + 5001`，**不发 RPC** |
| **Java Behavior** | Jackson 3 接受，字段值 = `Double.POSITIVE_INFINITY` ⇒ `LikeActionService:58` 的 `(float)` 窄化仍是 `+Inf` ⇒ **发出一个 weight=+Inf 的 proto 请求** |
| **Difference** | go-zero 走 `strconv.ParseFloat` 并检查 `ErrRange`；Jackson 3 对溢出字面量**不报错**，直接给 IEEE 无穷 |
| **Observable Impact** | Go 不产生副作用；RAG 产生**一次带 `+Inf` 的写调用**（下游拿到的是无限权重） |
| **Classification** | **SEMANTIC MISMATCH** |
| **Risk** | **MEDIUM**（`weight` 直接进结算链路；`+Inf` 会污染下游任何按权重加权的计算。⚠️ 注意 `1e39`（f64 合法、f32 溢出）两侧**都接受**且都窄化成 `+Inf`，那条是等价的 —— 差异**只在字面量本身越出 f64** 时出现） |
| **Must Preserve** | `weight` 超出 float64 值域 ⇒ 解析失败，不得产生 `Infinity` |
| **Recommended Verification** | 给 `LikeActionReq#setWeight` 改成 `Object` 入参 + 显式 `isFinite()` 校验；或在 DTO 层加一道 `Double.isFinite` 断言（抛 `IllegalArgumentException` ⇒ 已被 `handleIllegalArgument` 折叠成 5001） |
| **Evidence** | **双侧活体对拍**：Go D07 `ERR strconv.ParseFloat: parsing "1e400": value out of range`；RAG D07 `OK w=Infinity`。消费点：`core/mapping/unmarshaler.go:686-693`（`v.Float64()` 后的 `target.SetFloat`，**未检查 ErrRange** 之外的上层 `setValueFromString` 路径）→ 实测报错来自 `setValueFromString` 的 `ParseFloat` 返回值检查 |

---

#### **DRIFT-LIKEAPI-017** — 单次 RPC 超时：Go **固定 2000ms**，RAG **取 HTTP 剩余预算**

| 字段 | 内容 |
|---|---|
| **Component** | like-api |
| **Operation** | 全部 6 个端点的下游调用 |
| **Category** | Timeout（§17 / §20） |
| **Go Behavior** | `zrpc.RpcClientConf.Timeout int64 json:",default=2000"`（`zrpc/config.go:31`；`etc/likecenter.yaml` **没写** ⇒ 2000）→ `zrpc/client.go:70-72` `WithTimeout(2000ms)` → `clientinterceptors/timeoutinterceptor.go:25` `context.WithTimeout(ctx, 2s)`。⇒ **每次 RPC 调用独立封顶 2s**，再叠加外层 HTTP 3s ⇒ 实际 = `min(2s, 3s − 已耗时)` |
| **Java Behavior** | `LikeRpcClient#likeRpc()` → `GoStyleRequestDeadline.applyTo(stub)` ⇒ `withDeadlineAfter(剩余预算)`，剩余预算是 **HTTP 3s 的剩余量** ⇒ 实际 = `3s − 已耗时`，**没有 2s 这个独立上界** |
| **Difference** | 两个语义相同的旋钮（单次下游调用超时），Go 是**固定值 2000**，RAG 是**父预算派生**（≤3000）。**没有任何一个 yaml 字段能对应 Go 的 2000** |
| **Observable Impact** | 下游耗时落在 **2s ~ 3s** 这一段时：Go 返 `DEADLINE_EXCEEDED` ⇒ `200 + 5001`；RAG **成功返回**。2s 以下 / 3s 以上两侧一致 |
| **Classification** | **SEMANTIC MISMATCH**（§20：同一语义旋钮取值不同 ⇒ 语义错误，不是配置风格差异） |
| **Risk** | **MEDIUM**（只在 2–3s 窗口可观测；但下游变慢时「RAG 会把本该超时的调用放过去」正是线上最难查的一类问题） |
| **Must Preserve** | 单次 RPC 调用封顶 **2000ms**，与 HTTP 总预算 **3000ms** 是**两个独立旋钮** |
| **Recommended Verification** | 给 `LikeRpcClient` 增加一个显式的 `like-rpc` 超时配置（默认 `2000ms`），在 `likeRpc()` 里取 `min(配置值, HTTP 剩余预算)`；⚠️ 改完后 `GoStyleRequestDeadline` 的「无预算就原样返回」分支也要重新审 —— 单测里没有 HTTP 超时上下文时，两侧语义会再次分叉 |
| **Evidence** | E-Go 源码链：`zrpc/config.go:31` → `zrpc/client.go:70-72` → `clientinterceptors/timeoutinterceptor.go:17-30`；E-Src：`GoStyleRequestDeadline.java:110-120` + `LikeRpcClient.java:78-80`。⚠️ 「3000 − 已耗时 > 2000 时 RAG 真的放行」这一条**未做双端活体对拍**（需要真的让 like-rpc 慢 2.5s），见 §9 U-04 |

---

#### **DRIFT-LIKEAPI-018** — 🔴 `LikeApiApplication` 的类 javadoc 仍在传播**已被推翻的 DRIFT-001 结论**

| 字段 | 内容 |
|---|---|
| **Component** | like-api（入口类文档） |
| **Operation** | n/a（文档） |
| **Category** | **文档致高危**（AGENTS.md §3.3.2 同款形态） |
| **断言原文** | `LikeApiApplication.java:25-29`：<br>「⚠️ **本服务没有配置超时**：Go 侧 `etc/likecenter.yaml` 的 RestConf **没有 `Timeout` 字段** ⇒ `TimeoutFromMillis(0) == 0` ⇒ `observability.NewHTTPMiddleware` 走 `timeout <= 0` 的『不设超时』分支。Java 侧因此**刻意不配** `observability.http-timeout`，配了就等于凭空加了一条 Go 没有的 503 超时出口。」 |
| **事实** | ① Go 的 `Timeout` 由 **struct tag** 提供：`rest/config.go:55` `Timeout int64 json:",default=3000"` ⇒ yaml 不写也等于 **3000ms**；② Java 侧**已经配了** —— `application.yml:27-29` `observability.http-timeout: 3s`；③ 因此这条注释描述的「刻意不配」**早已不存在**，且它描述的 Go 行为是 **DRIFT-LIKEAPI-001 的成因本身** |
| **Why It Matters** | 这是 §3.3.2 点名的**最危险形态**：一段「引用真实文件、行文自信」的 Go 断言，指向已被证伪的推理，而它就在**应用入口类**上 —— 任何人排查超时或整理配置时**第一眼就会读到它**，并据此把 `http-timeout: 3s` 当成「凭空多出来的出口」删掉 ⇒ **DRIFT-LIKEAPI-001 原地复活** |
| **Observable Impact** | 无运行时影响；影响的是下一次改动 |
| **Classification** | **SEMANTIC MISMATCH（文档层）** |
| **Risk** | **HIGH**（复活成本 = 一次「清理无用配置」的提交） |
| **Must Preserve** | — |
| **Recommended Verification** | 改写为：「Go 的 3s 来自 `rest/config.go:55` 的 struct tag 默认值（yaml 未写），本模块在 `application.yml` **显式写出同值**」 |
| **Evidence** | E-Go：`rest/config.go:55`（tag 声明）+ `observability/http.go:30-34,88`（消费点）；E-Src/E-Cfg：`application.yml:27-29` 已配 3s。**本条已由本轮实测交叉确认**：若 Go 真无超时，则 `A01`（无 Content-Type）那条路径不会有 3s 窗口 —— 无关；决定性证据是 tag 默认值本身 |

---

#### **DRIFT-LIKEAPI-019** — 🔴 `LikeReqGoStyleValidationTest` 类注释仍在写「Slice 永不计必填」

| 字段 | 内容 |
|---|---|
| **Component** | like-api（测试类文档） |
| **Category** | **文档致高危**（AGENTS.md §3.3.2 同款形态，且正是 **DRIFT-LIKEAPI-002 的案发现场**） |
| **断言原文** | `LikeReqGoStyleValidationTest.java:31`：「保住 Go 的语义（**标量必填 / Slice 永不计必填** / options 取值域**）」<br>`LikeReqGoStyleValidationTest.java:39-40`：<br>`case reflect.Array, reflect.Map, reflect.Slice:`<br>`    if !opts.optional() { return u.processFieldNotFromString(... emptyMap ...) }  ← 不报错` |
| **事实** | ① 「Slice 永不计必填」是 **2026-10-22 已被活体探针推翻**的错误结论；② 那段 Go 代码摘录里的 `← 不报错` 标注**也是错的** —— 该分支不是 `return nil`，而是把 `emptyMap` 交给 `processFieldNotFromString` → `default:` → `processFieldPrimitive` → `typeKind(Slice) ≠ valueKind(Map)` → `newTypeMismatchError`；③ **同一个文件的 59-73 行已经写对了**（`target_ids 也是必填 —— Slice 不豁免`）⇒ **文件内部自相矛盾** |
| **Why It Matters** | 与 DRIFT-002 的元教训完全同款：测试名/类注释里引用了一个被推翻的 Go 结论 ⇒ 修复时会先撞上红测试，且注释会让人**误判「修复」才是漂移方向**。这次的额外风险是**同一个文件里两处结论打架**，读者无法判断哪处是当前有效的 |
| **Observable Impact** | 无运行时影响；影响的是下一次改动（有人照 `-31` 那行去「简化」Slice 校验 ⇒ 立刻把 DRIFT-002 改回来） |
| **Classification** | **SEMANTIC MISMATCH（文档层）** |
| **Risk** | **HIGH** |
| **Must Preserve** | 类级注释里的 Go 结论必须与 §59-73 行一致：**无 tag 的 Slice 与标量同样必填**，只是错误文案不同 |
| **Recommended Verification** | 删除 `-31` 的「Slice 永不计必填」字样与 `-40` 的 `← 不报错` 标注，改写为指向同文件 59-73 行的结论 + `unmarshaler.go:944-950 → processFieldNotFromString(emptyMap) → processFieldPrimitive → newTypeMismatchError` 的完整调用链 |
| **Evidence** | E-Go 探针 **B02 = `ERR type mismatch for field "target_ids"`**（`{"target_type":"a"}`），**B04 = `OK ids=[]`**（`target_ids:[]` 合法）。消费点：`core/mapping/unmarshaler.go:944-950` |

---

#### **DRIFT-LIKEAPI-020** — `application/*+json` 的 Go 结论**过宽**

| 字段 | 内容 |
|---|---|
| **Component** | like-api（测试文档） |
| **Category** | 文档精度（AGENTS.md §3.3.2：Go 断言必须精确到可复现） |
| **断言原文** | `LikeApiGoStyleBodyContentTypeTest.java:148`：<br>`@DisplayName("application/*+json 在 Go 侧**同样不算** JSON（字面子串匹配，不是媒体类型兼容判定）")` |
| **事实** | Go 的判据是 `strings.Contains(ct, "application/json")` —— **子串包含**。于是：<br>• `application/vnd.api+json` → 不含子串 ⇒ Go **不读** ✅（该测试断言的正是这一条，**断言本身正确**）<br>• `application/json-patch+json` → **含**子串 ⇒ Go **会读 body** ❌（**与 DisplayName 的措辞相反**） |
| **Observable Impact** | 无（DisplayName 是描述性文本，断言的是具体值） |
| **Classification** | **SEMANTIC MISMATCH（文档层，精度问题）** |
| **Risk** | **LOW-MEDIUM**（有人据此以为「Go 拒绝一切 `+json`」⇒ 可能给 `application/json-patch+json` 加上 Go 没有的拒绝逻辑） |
| **Recommended Verification** | 把 DisplayName 收窄为「`application/vnd.api+json` 不含 `application/json` 子串 ⇒ Go 不读；⚠️ `application/json-patch+json` **含**该子串 ⇒ Go 会读，RAG 也读」；并在 `goReadsBody` 的边界测试里补一条 `application/json-patch+json → true`（本轮实测确为 `true`） |
| **Evidence** | E-Go A05 `Content-Type: application/json-patch+json` ⇒ **`OK tt="a" ids=[1]`**（读）；A-E-Rag `goReadsBody("application/json-patch+json",10) = true`。消费点：`rest/httpx/requests.go:154` 的 `strings.Contains` |

---

#### **DRIFT-LIKEAPI-021** — 响应 `Content-Type`：Go 带 `charset=utf-8`，RAG 不带（OBSERVABLE）

| 字段 | 内容 |
|---|---|
| **Component** | like-api |
| **Operation** | 全部 6 个端点的成功与业务错误响应 |
| **Category** | Serialization（响应头） |
| **Go Behavior** | `application/json; charset=utf-8` —— 分号后**有空格** |
| **Java Behavior** | `application/json` —— **无 charset 参数** |
| **Difference** | 按 RFC 9110，`application/json` 的 charset 参数**默认即 UTF-8**，省略与显式声明 `utf-8` **语义等价**；且省略 `application/json; charset=utf-8` 里的 OWS 按规范不显著 ⇒ 判定为语义等价 |
| **Observable Impact** | 客户端若做**字节级**的 Content-Type 断言会红（⚠️ 本仓库的测试用 `contentTypeCompatibleWith` / `MediaType.parse` 比对，**不受影响**） |
| **Classification** | **OBSERVABLE DIFFERENCE** |
| **Risk** | **LOW** |
| **Must Preserve** | 不必强行对齐（会与容器对抗）；⚠️ 若将来有客户端按原始字符串比对，需登记为签字偏离 |
| **Recommended Verification** | 保持现状；在 `docs/Intentional_Deviations/_cross/signed-deviations.md` 追加一行留档 |
| **Evidence** | E-Go F01/F04 `ct="application/json; charset=utf-8"`；E-Rag A03 `ct=application/json`。消费点：`core/jsonx` + `rest/httpx/responses.go:182` `w.Header().Set(ContentType, header.ContentTypeJson)` |

---

#### **DRIFT-LIKEAPI-022** — worker 池饱和的**裸 503**：RAG 独有的拒绝出口（OBSERVABLE）

| 字段 | 内容 |
|---|---|
| **Component** | like-api |
| **Operation** | 全部 6 个端点 |
| **Category** | Concurrency / Load Shedding |
| **Go Behavior** | 每请求一个 goroutine（初始栈 2KB），**无上限** ⇒ 不存在「worker 池饱和」这个事件。Go 侧唯一的容量拒绝是 `MaxConnsHandler`（`rest/config.go:52` 默认 10000）与 `SheddingHandler`（CPU 90%），两者都由 `rag.protection` 对齐（历史条目 -003/-004 已关） |
| **Java Behavior** | `HttpMiddleware` 的 worker 池**上限 512**（`MAX_HTTP_WORKERS`）；饱和 ⇒ `RejectedExecutionException` ⇒ **裸 503，无 body、无 Content-Type**（`HttpMiddleware:296-317`） |
| **Difference** | RAG 多了一条 Go 不存在的拒绝出口，且**形状刻意与超时 503 不同**（超时那条有 body） |
| **Observable Impact** | 高并发 + 下游卡死时，RAG 会出现 Go 不会出现的裸 503。⚠️ 512 远大于 Tomcat 默认 `max-threads`（200），正常流量不该触发 |
| **Classification** | **OBSERVABLE DIFFERENCE** |
| **Risk** | **LOW**（触发窗口窄；且这是**有意的兜底**，`HttpMiddleware:116-128` 的注释已自陈「是『拿不到 ctx 传播』这件事的兜底，不是 Go 语义的一部分」） |
| **Must Preserve** | 无（这是有意接受的实现差异，不属 Go 语义） |
| **Recommended Verification** | 保持现状；建议在 `docs/Intentional_Deviations/_cross/signed-deviations.md` 留档，避免下次复核被当成新漂移 |
| **Evidence** | E-Src：`HttpMiddleware.java:130`（`MAX_HTTP_WORKERS = 512`）+ `:296-317`（饱和分支）；Go 侧无对应物（`rest/engine.go` 全文无 worker 池） |

---

## 8. Test Cases

> **CASE-xxx** 按 §29/§30。本轮**只跑探针、不新增测试**（任务限定只改两个台账文件）。
> 「Go」与「RAG」两列的取值全部来自本轮活体探针输出。

### 输入解释矩阵（56 条，两侧同矩阵）

| CASE | Input | Go | RAG | Equivalent | §30 |
|---|---|---|---|---|---|
| CASE-01 | `{"target_type":"a","target_ids":["1","2"]}` | OK | OK | **YES** | 正常成功 |
| CASE-02 | `{"target_type":"a"}`（缺 Slice） | `type mismatch for field "target_ids"` | `field "target_ids" is not set` | **YES**（同 5001） | 空输入 |
| CASE-03 | `{"target_type":"a","target_ids":null}` | `mustn't be nil` | `is not set` | **YES** | null |
| CASE-04 | `{"target_type":"a","target_ids":[]}` | OK `[]` | OK `[]` | **YES** | 空输入 |
| CASE-05 | `{"target_type":"a","target_ids":[1,2]}` | **OK `["1","2"]`** | **ERR** | **NO** | ⇒ DRIFT-012 |
| CASE-06 | `{"target_type":"a","target_ids":[1.5]}` | **OK `["1.5"]`** | **ERR** | **NO** | ⇒ DRIFT-012 |
| CASE-07 | `{"target_type":"a","target_ids":[true]}` | ERR | ERR | YES | — |
| CASE-08 | `{"target_type":"a","target_ids":[null]}` | **OK nil slice** | **OK `[null]` → proto NPE** | **NO** | ⇒ DRIFT-013 |
| CASE-09 | `{"target_type":"a","target_ids":["x",null,"y"]}` | **OK `["x","","y"]`** | **OK `[x,null,y]` → proto NPE** | **NO** | ⇒ DRIFT-013 |
| CASE-10 | `{"target_type":"a","target_ids":"abc"}` | ERR | ERR | YES | — |
| CASE-11 | `{"target_type":"a","target_ids":{}}` | ERR | ERR | YES | — |
| CASE-12 | `…,"zzz":9`（未知键） | OK 忽略 | OK 忽略 | YES | — |
| CASE-13 | `{"target_type":1,…}` | `type mismatch … expect "string", actual "number"` | `Cannot coerce Integer …` | YES | — |
| CASE-14 | `{"target_type":null,…}` | `mustn't be nil` | `is not set` | YES | — |
| CASE-15 | `{"target_type":"","target_ids":[""]}` | **OK**（空串合法） | **OK** | YES | 边界 |
| CASE-16 | `{}` | `target_type is not set` | 同 | YES | 空输入 |
| CASE-17 | `[1,2]` / `"abc"` / `123` / `null`（顶层非对象） | ERR | ERR | YES | 序列化 |
| CASE-18 | `{…} xx`（尾随垃圾） | **OK** | **ERR** | **NO** | ⇒ DRIFT-014 |
| CASE-19 | `{…}{…}`（两对象） | **OK（取第一个）** | **ERR** | **NO** | ⇒ DRIFT-014 |
| CASE-20 | 重复键 `target_type` ×2 | 后者胜 | 后者胜 | YES | 重复 |
| CASE-21 | 截断 JSON | ERR `unexpected EOF` | ERR `UnexpectedEndOfInput` | YES | 序列化 |
| CASE-22 | 大写键 `TARGET_IDS` | ERR（键名大小写敏感） | ERR | YES | — |
| CASE-23 | `Content-Type: text/plain` + 完整合法 body | **200 + 5001，不发 RPC** | 同 | YES | ⇒ -006 确认真关闭 |
| CASE-24 | 无 Content-Type + 完整合法 body | **200 + 5001，不发 RPC** | 同 | YES | 同上 |
| CASE-25 | 零字段 DTO + `text/plain` | **200 成功，发 RPC** | 同 | YES | 同上 |
| CASE-26 | **`Content-Type: APPLICATION/JSON`** | **200 + 5001，不发 RPC** | **200 成功，发 RPC** | **NO** | ⇒ DRIFT-015 |
| CASE-27 | `Content-Type: application/json-patch+json` | OK（读） | OK（读） | YES | ⇒ DRIFT-020 |
| CASE-28 | `Content-Type: application/json` + 空 body | 200 + 5001 | 同 | YES | 空输入 |
| CASE-29 | chunked（`ContentLength=-1`） | 丢弃 body | `goReadsBody(…,-1)=false` | YES | ⚠️ 见 §9 U-05 |
| CASE-30 | `cursor`/`page_size` 缺席 | `is not set` | 同 | YES | 边界 |
| CASE-31 | `cursor:"0"`（字符串） | `type mismatch` | `Cannot coerce String` | YES | — |
| CASE-32 | `cursor:1.0` / `1e2` | `ParseInt: invalid syntax` | `Cannot coerce Floating-point` | YES | 边界 |
| CASE-33 | `page_size:2147483648` / `cursor:2^63` | `value out of range` | `out of range of int/long` | YES | 边界 |
| CASE-34 | `cursor:-1, page_size:-5` | **OK**（不做非负校验） | **OK** | YES | 边界 |
| CASE-35 | `action_type` ∈ {1,2,3,4} | OK | OK | YES | 正常成功 |
| CASE-36 | `action_type` ∈ {0,5,1.0,"1"} | ERR | ERR | YES | 边界 |
| CASE-37 | `author_id` = `"7"` / `7` / `"+7"` | **OK**（三种都收） | **OK** | YES | — |
| CASE-38 | `author_id` = `7.0` / `7.5` / `"7.5"` / `true` / `""` / `2^63` / `null` / 缺席 | ERR（8 种） | ERR（8 种） | YES | 权限失败 |
| CASE-39 | `weight` 缺席 / `null` | OK（零值 0） | OK（null→0f） | YES | — |
| CASE-40 | `weight:1.5` | OK | OK | YES | — |
| CASE-41 | **`weight:1e400`** | **ERR（out of range）** | **OK `Infinity`** | **NO** | ⇒ DRIFT-016 |
| CASE-42 | `weight:"1.5"` / `"x"` | `type mismatch` | `Cannot coerce String` | YES | — |
| CASE-43 | `weight:1e39`（f64 合法、f32 溢出） | OK → 窄化 `+Inf` | OK → 窄化 `+Inf` | YES | 边界 |
| CASE-44 | `client_ip:null` / 缺席 | OK（零值 `""`） | OK（null→`""`） | YES | — |
| CASE-45 | `client_ip:123` | `type mismatch` | `Cannot coerce Integer` | YES | — |
| CASE-46 | 零字段 DTO + 畸形 JSON `{` | ERR → 5001 | ERR → 5001 | YES | 依赖失败 |
| CASE-47 | 401（无 token / 坏签名 / 过期） | `401` + 0 字节 | `401` + 0 字节 | YES | 权限失败 |
| CASE-48 | 未知路径无 token | `404` | `404`（真 Tomcat） | YES | — |
| CASE-49 | `GET /like/v1/getlikecount` | `405` 裸 + `Allow` | `405` 裸 + `Allow`（真 Tomcat） | YES | — |
| CASE-50 | envelope：`counts:{}` / `list:[]` / `data:null` | 逐字节一致 | 一致 | YES | 部分失败 |
| CASE-51 | envelope：`<a> & 'b' ü 中文` | 不转义 | 不转义 | YES | 编码 |
| CASE-52 | RPC status error（含 description） | 业务码 + 原文 | 同 | YES | 依赖失败 |
| CASE-53 | RPC 非 status 错误 | `5001 + "系统内部错误"` | 同 | YES | 依赖失败 |
| CASE-54 | RPC 调用次数（1 次，不重试） | 1 | 1 | YES | CASE-07/12 |
| CASE-55 | 超时（下游 >3s） | `503 + Request Timeout` | 配置已对齐，⚠️ 运行期未复现 | **UNKNOWN** | ⇒ §9 U-03 |
| CASE-56 | 单次 RPC 超时（下游 2.5s） | `DEADLINE_EXCEEDED` | **成功** | **NO** | ⇒ DRIFT-017 |

### 未覆盖 / 不适用
- CASE-08~11（Transaction Rollback）：**不适用** —— 本组件无事务。
- CASE-08（Cache Hit/Miss）：**不适用** —— 本组件无缓存。
- CASE-11（并发调用）：**部分覆盖** —— 只覆盖了「单请求内」；并发下的幂等性归 like-rpc，未审。

---

## 9. Unresolved

| ID | Question | 已查到的证据 | 为什么仍然 Unknown | 影响 | 需要谁确认 |
|---|---|---|---|---|---|
| **U-01** | **事实投递链路端到端是否等价？** | `AGENTS.md` §0 记「like 域的事实投递链路**跨三个仓库**」。本组件只覆盖到「发出 `LikeAction` RPC」这一端 | **第三个仓库本轮完全不在范围内**；且 `like-rpc` 由**另一并行会话**审查，本轮**不假设对端正确**（`like-rpc` 的行为、事务、幂等都没验） | 「点赞是否真的记上、消息是否真的发出」这条最关键的链路，本轮**单侧验不完**。任何关于 likeaction 正确性的结论都只到 api 边界为止 | like-rpc 会话 + 第三个仓库的审查 |
| **U-02** | `weight` / `author_id` 真正落库时的口径 | Go api 侧已确认 `float32` 窄化 + `success` 硬编码 true；proto 字段号两侧一致（`AGENTS.md` §3.4） | 下游如何用 `+Inf` 权重（DRIFT-016 会产生 `+Inf`）取决于 like-rpc | DRIFT-016 的**业务影响面**无法在本组件定量 | like-rpc 会话 |
| **U-03** | **3s 超时的 503 出口在真 Tomcat 上真的跑出来吗？** | 配置层（`application.yml:27-29`）+ 装配层（`HttpMiddlewareFilter` 是 `@Component`、`scanBasePackages` 含 `com.rag.common`）**都已核对**；源码链完整 | ⚠️ 按 `AGENTS.md` §3.3，**「配置写了 + 装配在」不等于运行期生效**。本轮探针没起真 Tomcat 打超时路径 | DRIFT-001 的修复目前只能记「配置+装配已核对」，**不能记「已验证闭环」** | 需要一条真 Tomcat + 慢下游的超时用例 |
| **U-04** | DRIFT-017 的可观测窗口到底多宽？ | Go 源码链（2s 固定）与 RAG 源码链（≤3s 派生）都已定位到消费点 | 「下游耗时 2.5s 时 RAG 真的放行」需要**真的让 like-rpc 慢 2.5s**，本轮没做双端活体对拍 | DRIFT-017 的判定目前是「源码级」而非「活体对拍」 | 需要一条跨两侧的慢下游探针 |
| **U-05** | chunked 请求在**真 Tomcat** 上是否真被丢弃？ | Go 侧活体探针 A07/A08 已复现（`ContentLength=-1` ⇒ 丢弃）。RAG 侧只验到 `goReadsBody("application/json",-1)=false` 这个转换器级判据 | `MockMvc` 的 `.content(byte[])` 会填上内容长度，**无法模拟 chunked** | 判据本身两侧一致，但缺少端到端证据 | 需要一条真 Tomcat 的 chunked 请求 |
| **U-06** | Go 的 `DevServer`（`likecenter.yaml:20-27`，端口 9099 + `/metrics` + `/healthz`）**是否真的被消费**？ | yaml 里配了；RAG 侧对应的是 18887 上的 actuator（`health,info,prometheus`） | **本轮未 grep 到 Go 侧任何读 `DevServer` 的代码** —— 疑似死配置，但未穷尽全仓 | 若 Go 真的在 9099 起了一套运维端点而 RAG 没有 ⇒ 端口与路径层面有可观察差异 | 需要全仓 grep `DevServer` |
| **U-07** | `GetLikeStateResp.states` 在 rpc 返回 nil map 时两侧是否一致？ | Go 是 `States: rpcResp.States` **直传**（`getlikestatelogic.go:65-67`）⇒ nil map 会序列化成 `{"states":null}` | 本轮**未审** `LikeStateService` 的对应实现，也未探针覆盖（`getlikestate` 的 rpc 返回不在本组件可构造范围内） | `counts`/`list` 已确认恒 `{}`/`[]`，但 **`states` 是唯一没有 `make` 保护的可空容器** | 需 like-rpc 会话提供「返回空 states」的活体响应 |
| **U-08** | `@GrpcClient("like-rpc")` 的实际寻址在无 Nacos 时的行为 | `application.yml:97-105` 配了 `address=discovery:///like-rpc` + `negotiation-type=plaintext` | 本轮所有探针都**绕开了** gRPC（`NullElem` 只测 proto builder，没发网络请求） | 寻址失败时的错误形态未验（会被 `rpcError` 折叠成 5001 还是别的） | 需一个真 like-rpc 进程 |

---

## 10. 最终判定

### 10.1 §39 的 18 项 checklist（逐条）

| # | 检查项 | 结论 | 依据 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致？ | ❌ **否** | 6 条 SEMANTIC MISMATCH（012~017） |
| 2 | 正常路径是否一致？ | ✅ 是 | CASE-01/35/37/44/50 全绿 |
| 3 | 边界输入是否一致？ | ❌ **否** | CASE-05/06/08/09/41（012/013/016） |
| 4 | NotFound / Empty / Null 是否一致？ | ⚠️ **部分** | 空/缺席一致（CASE-02/04/15/16/28）；**数组内 null 元素不一致**（CASE-08/09） |
| 5 | Error meaning 是否一致？ | ✅ 是 | 码表一致；错误文本不可观测（两侧都不把文本放进 body） |
| 6 | Error propagation 是否一致？ | ✅ 是 | `5001` 兜底 / `1006` 身份失败 / `401` 鉴权 三条出口逐条一致 |
| 7 | External dependency failure 是否一致？ | ⚠️ **部分** | status / 非 status 两条分支一致；**超时取消语义不一致**（矩阵 #32）、**单次 RPC 超时值不一致**（017） |
| 8 | Cache hit / miss 是否一致？ | ✅ 不适用 | 两侧都无缓存 |
| 9 | Transaction boundary 是否一致？ | ✅ 不适用 | 两侧都无事务 |
| 10 | State transition 是否一致？ | ✅ 不适用 | 两侧都无本地状态 |
| 11 | Side effects 是否一致？ | ❌ **否** | 012/013/016/017/022 **都会改变「是否发出 / 发出什么」下游调用** |
| 12 | Ordering 是否一致？ | ✅ 是 | 单次同步调用，无排序 |
| 13 | Retry 是否一致？ | ✅ 是 | 两侧都不重试 |
| 14 | Timeout scope 是否一致？ | ⚠️ **部分** | HTTP 作用域一致；**单次 RPC 的来源与取值不一致**（017） |
| 15 | Concurrency semantics 是否一致？ | ⚠️ **部分** | 饱和拒绝出口 RAG 独有（022）；取消语义不同（#32） |
| 16 | Authentication / Authorization 是否一致？ | ✅ 是 | §6.3 逐条对齐；⚠️ **黑名单维度不适用（Go 侧零实现）** |
| 17 | Serialization semantics 是否一致？ | ❌ **否** | 012（Slice 元素）/ 013（null 元素）/ 014（尾随 token）/ 016（浮点溢出） |
| 18 | Lifecycle 是否一致？ | ✅ 是 | §6.10 |

### 10.2 三值结论

```text
SEMANTICALLY NOT EQUIVALENT
```

**理由**：存在 **6 条会改变行为的 SEMANTIC MISMATCH** + **4 条 OBSERVABLE DIFFERENCE**，
其中 3 条（012/013/015/016）**直接改变「是否向下游发出请求」或「发出什么内容」**，
1 条（017）改变超时语义，1 条（014）改变解析成败。

⚠️ **本判定不是「上轮结论的延续」**：上一轮（2026-10-22）的 2 条 HIGH 漂移
（001 超时、002 `target_ids` 必填）**本轮复核确认均已修复**，且 002 的修复结论**成立**。
本轮的 6 条是**首次被组件级对照发现**的新漂移 —— 它们**全部落在现有 62 条绿测试的覆盖之外**，
再次印证 `AGENTS.md` §0：**测试数不是质量指标**。

### 10.3 证据强度总表

| 结论类别 | 条数 | 证据强度 |
|---|---|---|
| SEMANTIC MISMATCH（012/013/014/015/016） | 5 | **双侧活体对拍**（同矩阵、同输入、两侧各跑一次真实解析路径） |
| SEMANTIC MISMATCH（017） | 1 | Go 源码链到消费点 + RAG 源码链；**活体对拍未做** ⇒ §9 U-04 |
| SEMANTIC MISMATCH（文档层 018/019/020） | 3 | 源码直读 + Go 活体探针反证 |
| OBSERVABLE DIFFERENCE（021/022/#32） | 3 | 源码 + 探针（#32 为源码级，已由 `GoStyleRequestDeadline` 自陈） |
| EQUIVALENT（矩阵 40 项中 31 项） | 31 | 多数为**双侧活体对拍**；404/405/401 为**真 Tomcat 实跑** |
| UNKNOWN | 1（矩阵 #40）+ 8（§9） | — |

### 10.4 口径边界（必须随结论一起读）

1. **本组件的结论只到 api 边界**。「发出什么 RPC」已验，**「对端收到什么、怎么落库、有没有幂等」完全未验**
   —— `like-rpc` 由并行会话审查，且 `AGENTS.md` §0 记 like 域的**事实投递链路跨三个仓库**。
   ⇒ 任何「likeaction 正确」的结论都不成立。
2. **DRIFT-LIKEAPI-018/019/020 是文档缺陷，不是运行时缺陷**。它们不影响当前行为，
   但**每一条都会在下一次改动时变成运行时缺陷** —— 这正是 §3.3.2 立该硬要求的原因。
3. **绿测试 62 条全绿不构成本判定的反证**。本轮 6 条 MISMATCH 无一被现有测试覆盖。
---
---

# like-api 修复轮 · 部分完成（2026-10-23）

> **分支**：`align/like` · **基线**：改前 `like-api` 62 tests 全绿。
> ⚠️ 本轮**只修完 2 条**，其余留档 —— 见 §4。**判定不变**：`SEMANTICALLY NOT EQUIVALENT`。

## 0. 本轮摘要

| | 内容 |
|---|---|
| **已修** | **DRIFT-LIKEAPI-013**（null 元素崩在 protobuf）、**DRIFT-LIKEAPI-014**（尾随 token） |
| **新增守门测试** | `LikeApiGoAlignmentTest` **5 条**（基线 62 → **67**） |
| **证据强度** | 4 条走**真实 MVC 管线** + `ArgumentCaptor` 捕获真实下游 proto；1 条纯函数 |
| **⚠️ 零覆盖已证伪两次** | 这 2 条修复改回原状时，既有 62 条<b>依然全绿</b> |

## 1. DRIFT-LIKEAPI-013 ✅ 已修

**问题**：`{"target_ids":[null]}` / `["x",null,"y"]` 时，null 元素原样透传到 protobuf 的
`addAllTargetIds(...)` ⇒ `NullPointerException: Element at index 0 is null.`
⇒ 被 `GlobalExceptionHandler#handleAny` 兜成 `200 + 5001`，**下游 RPC 根本没发出**；
Go 侧则**正常发出**。

**Go 真实语义**（`core/mapping/unmarshaler.go:156-183`）：

| 输入 | Go 结果 | 后果 |
|---|---|---|
| 缺席 / `[]` | nil / 空 slice | 空 target_ids |
| `[null]` | **slice 保持 nil**（`valid` 标志从未置位 ⇒ `value.Set` 不执行） | 空 target_ids ⇒ like-rpc 回 2001 |
| `["x",null,"y"]` | **`["x","","y"]`** —— `conv` 长度仍是 3，null 位留成零值空串（`:158-160` 的 `continue`） | ⚠️ **不是过滤** |

**修法**：新增 `com.rag.like.like.api.service.GoStyleTargetIds`（两个读服务共用，避免各写一份），
`LikeCountService` / `LikeStateService` 的 `addAllTargetIds(...)` 均改走 `normalize(...)`。

⚠️ **最容易做错的是「长度不变」**：把 null 过滤掉会把长度 3 缩成 2，
会让**下一页的 limit 边界与 offset 全部错位** —— 这是分页语义问题，不是长度洁癖。

⚠️ **刻意不在此处实现 012**：012 是「`target_ids` 里放 **JSON 数字**时 Go 接受、RAG 拒」，
属**解析层**（DTO setter）的责任；数字若已被解析层拒掉，`normalize` 根本看不到。

## 2. DRIFT-LIKEAPI-014 ✅ 已修（**主动放宽解析器**）

**Go 语义**：`core/jsonx/json.go:74-77` 只解**第一个** JSON 值，其后内容既不报错也不检查。
`{…} xx` ⇒ **解析成功**（用第一个对象，**会真的发出 RPC**）；`{…}{…}` ⇒ 同理。

**RAG 原状**：Jackson 3（`tools.jackson` 3.1.5）把 `FAIL_ON_TRAILING_TOKENS` 的**默认值翻转成 `true`**
（Jackson 2 是 `false`）⇒ 两者都解析失败 ⇒ `200 + 5001`。含 `likeaction` 这个**写路径**在内，6 个端点全受影响。

**修法**：`LikeGoStyleCoercion.applyTo` 里显式
`builder.disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)`。
⚠️ 只改 **like 域自有**的 `LikeGoStyleCoercion`，**不动 `service/common` 的 `GoStyleCoercion`**（见 §4 015）。

⚠️ **这是有代价的放宽**：接受「一个 JSON 值后跟任意垃圾」是攻击面。
之所以仍按此修，是 AGENTS.md §5「出现行为差异时默认判 RAG 错」——
真源就是这个行为，不对齐则灰度期两侧对畸形请求的分叉无法解释。
⚠️ **若将来决定改严，必须先在 `Intentional_Deviations` 签字，不能单方面「顺手收紧」。**

## 3. 本轮新增守门测试

`service/like/like-api/src/test/java/com/rag/like/like/api/LikeApiGoAlignmentTest.java` —— **5 条**：

`trailingContentAfterFirstJsonValueIsAccepted` · `secondJsonObjectIsIgnored` ·
`allNullTargetIdsBecomeEmptyList` · `partialNullTargetIdsKeepLengthAndFillEmptyString` ·
`normalizeCoversAllFourShapes`

⚠️ **前 4 条必须走真实 MVC 管线**（`@SpringBootTest` + `@AutoConfigureMockMvc`）并用
`ArgumentCaptor` 捕获**真实发给 like-rpc 的 proto** ——
尾随 token 与 coercion 都取决于 Spring MVC **实际使用的那个** Jackson 3 mapper，
自己 `new` 一个 mapper 造断言是**假绿**（本轮在 LikeApiGoAlignmentTest 的类 javadoc 里显式写明了这点）。

## 4. ⚠️ 本轮**未做**的（留档，含原因）

| ID | 内容 | 为什么没做 |
|---|---|---|
| **DRIFT-LIKEAPI-015** | `Content-Type` 大小写：Go 用**原始头**（`strings.Contains` 大小写敏感），RAG 用**已解析的 MediaType**（媒体类型已小写化） | ⚠️ **修法落在 `service/common` 的 `GoStyleJsonBodyConverter`**，该类被 **6 个域**共用（like / follow / message / users×2 / task / article），各域都有 `*GoStyleBodyContentTypeTest`。在**多域并发 worktree**（§5.3）下改共享组件会产生跨域冲突 ⇒ **须单域串行处理**，不在本 worktree 内做 |
| **DRIFT-LIKEAPI-012** | `target_ids` 里放 JSON 数字：Go **接受**（`fillSliceValue` 的 `case fmt.Stringer` 命中 `json.Number`），RAG **拒**（`LogicalType.Textual ← Integer = Fail`） | 修法需**元素级解析器**（setter 改 `Object` 入参）。⚠️ 台账已实测记录「Jackson 3 的 coercion **只有 LogicalType 级生效、类级无效**」，所以 `withCoercionConfig(SomeListClass, …)` 这条路走不通 —— 需新增一个显式的元素级解析组件，范围大于本轮 |
| **DRIFT-LIKEAPI-016** | `weight: 1e400` ⇒ Go **拒**（`strconv.ParseFloat` 检查 `ErrRange`），RAG 收成 `Double.POSITIVE_INFINITY` ⇒ **真的发出一次 `weight=+Inf` 的写调用** | 修法明确（`setWeight` 改 `Object` 入参 + `isFinite()` 校验），但需与 012 共用同一套「元素级/字段级解析器」组件 ⇒ **与 012 合并做**，避免两次改动同一批 DTO |
| **DRIFT-LIKEAPI-017** | 单次 RPC 超时：Go **固定 2000ms**（`zrpc/config.go:31` 的 struct tag 默认），RAG 取 **HTTP 剩余预算**（≤3000） | 需新增独立配置项 + 改 `GoStyleRequestDeadline` 的「无预算就原样返回」分支（单测里没有 HTTP 超时上下文时两侧语义会再次分叉）⇒ 需要连带审 `common` 的 deadline 组件，同样受 §4 的并发约束 |
| **DRIFT-LIKEAPI-018/019/020** | 3 条**文档层**高危断言 | ⚠️ **纯注释修正**，无需运行期测试。本轮**未做**，留待下一轮（改动零风险，但需要逐条核对 Go 出处，不能凭印象改） |
| **DRIFT-LIKEAPI-021/022** | 响应 `Content-Type` 无 `charset`（判语义等价）/ 裸 503（RAG 独有） | ✅ **已建档**为待签字候选：`Intentional_Deviations/like/api.md` 的 SD-…-08 / -09。⚠️ 标注为**待签字**而非已签字 —— 保留在案是为了让下一轮复核**不会把它们当新发现重提**，不是代替用户拍板 |

## 5. ⚠️ 关于「018 不加测试」的一条自我约束

`DRIFT-LIKEAPI-018` 是纯文档缺陷（类 javadoc 曾宣称「刻意不配 `http-timeout`」，
而 `application.yml:27-29` 早就配了 3s）⇒ 修复方式是改注释，**不需要**新测试。

⚠️ 本轮写测试时**差点**顺手加一条 `assertEquals("3s", …)` 之类的恒真断言 ——
那等于**主动制造一条假绿测试**，给未来的人「已经验过了」的错觉，
而台账 §9 **U-03 明确记着「3s 的 503 出口未在运行期复现」**。
⇒ 该条**刻意不加测试**，并把理由写进 `LikeApiGoAlignmentTest` 的注释里
（这本身是 §3.3.3「绿测试可能是错误答案的守卫」的一个**更隐蔽的变种**：
不是绿测试锁死了错答案，而是**用一条无意义的断言伪装成"验过了"**）。
