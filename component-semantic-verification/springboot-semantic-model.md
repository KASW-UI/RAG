# Spring Boot Semantic Model — `article-api`

> 本文件使用与 `go-semantic-model.md` **完全相同**的章节结构（§1~§11），
> 以便逐节对拍、避免「某一侧漏写维度」被误读。
> 所有「实测」数据来自真 Tomcat 启动 `ArticleApiApplication`（`server.port=18898`，
> Nacos 自动注册关闭、gRPC 指向 `127.0.0.1:65535`）+ `curl` 矩阵。

---

## 1. Initialization

**顺序**

1. `ArticleApiApplication`（`@SpringBootApplication(scanBasePackages={"com.rag.article.article.api","com.rag.common"})`，`@EnableDiscoveryClient`）
2. `@EnableConfigurationProperties(ArticleApiProperties.class)` —— 绑定 `article-api.*`
3. `WebMvcConfig`（`@Configuration`）：
   - `addInterceptors` → 把 `JwtAuthInterceptor` 挂在 `/v1/**`
   - `JsonMapperBuilderCustomizer` → `ArticleGoStyleCoercion::applyTo`
4. `com.rag.common` 被扫描 ⇒ 自动注册：
   - `GoStyleMaxConnsFilter`（`HIGHEST_PRECEDENCE+1`）
   - `GoStyleBreakerFilter`、`GoStyleCpuSheddingFilter`
   - `HttpMiddlewareFilter`（`+5`）
   - `GoStyleMaxBytesFilter`（`+6`）
   - `GoStyleJsonBodyConverter`（认领全部媒体类型）
   - `LoggerFactoryBean` / `ObservabilityLifecycle`
5. `ArticleGoStyleCorsFilter`（`Ordered.HIGHEST_PRECEDENCE`）—— CORS
6. devh `grpc-client-spring-boot-starter` 按 `grpc.client.{article-rpc,user-rpc}` 建 channel + stub
7. `spring-cloud-starter-alibaba-nacos-discovery` 自动注册实例
8. Tomcat 监听 `${ARTICLE_API_PORT:8889}`

⚠️ **`@Order` 的真实作用**：`ArticleGoStyleCorsFilter` 用 `HIGHEST_PRECEDENCE`，
使其在**所有** Filter（含 `GoStyleMaxBytesFilter` 的 `+6`）之前运行 ——
这对应 Go 侧 `corsRouter` 包在 router 之外。
⚠️ **Filter 与 Interceptor 的先后不是 `@Order` 决定的**，而是容器结构：所有 Filter 都在
`DispatcherServlet` 之外，`HandlerInterceptor` 在其内。

---

## 2. Processing（按语义顺序，API 仅作 Evidence）

### 2.1 请求流

```
Tomcat connector
  ArticleGoStyleCorsFilter (HIGHEST_PRECEDENCE)          ← 对应 Go corsRouter
    GoStyleMaxConnsFilter (+1)
      GoStyleCpuSheddingFilter (+3)
        HttpMiddlewareFilter (+5)                         ← 对应 Go observability.NewHTTPMiddleware
          GoStyleMaxBytesFilter (+6)
            [OrderedFormCsrfFilter / CharacterEncodingFilter ... Boot 默认]
              DispatcherServlet
                JwtAuthInterceptor.preHandle             ← 对应 Go handler.Authorize
                  ArticleController.<op>
                    <Service>
                GlobalExceptionHandler / GlobalResponseAdvice
```

**实测**：`POST /v1/article` 带 20 MiB Content-Length 且**不带 token** ⇒ **413**（不是 401），
证明 MaxBytes Filter 确实在 JWT 拦截器之外。

### 2.2 路径匹配（`DispatcherServlet` + `RequestMappingHandlerMapping`）

- 默认 `PathPatternParser`，**不做** `path.Clean`
- `useTrailingSlashMatch` 在 Spring Boot 3+ **默认 false** ⇒ `/v1/articles/` **不匹配** ⇒ 404
- 路径存在但方法不匹配 ⇒ 抛 `HttpRequestMethodNotSupportedException`
  ⇒ `GlobalExceptionHandler#handleMethodNotSupported` ⇒ **405 + `Allow` 头**（来自 Spring 的
  `e.getSupportedHttpMethods()`，顺序是 Spring 的排序，非 Go 的 map 随机序）
- 静态资源处理器匹配 `/**` ⇒ 未注册路径也会拿到一个 handler
  ⇒ `JwtAuthInterceptor` 的 `handler instanceof HandlerMethod` 守卫放行 ⇒ 抛 `NoResourceFoundException`
  ⇒ `GoStyleRouterErrorResponses.notFound`

**实测**：
```
MISMATCH POST /v1/article/1 -> 405 ct="" allow="PUT, DELETE, GET" body=""
UNREGISTERED /v1/nope      -> 404 ct="text/plain;charset=utf-8" nosniff="nosniff" body="404 page not found\n"(19B)
trailing slash /v1/articles/ -> 404
重复斜杠 + /v1/articles       -> 404
```

⚠️ `NoResourceFoundException` 能触发的前提是 Boot 的 `spring.web.resources.add-mappings` 仍开着
（默认开）。若被关掉，404 会走 `BasicErrorController`，body 与 Content-Type 都会变。

### 2.3 CORS（`ArticleGoStyleCorsFilter`）

- `@Component` + `@Order(Ordered.HIGHEST_PRECEDENCE)` ⇒ 在所有其它 Filter 之前
- 五个头用 `setHeader`，`Vary` 用 `addHeader`（对应 Go 的 `header.Add`）
- **刻意不写 `Access-Control-Allow-Credentials`**（Go 的 `setHeader` 在 `origin=="*"` 时不写）
- `OPTIONS` ⇒ `setStatus(204)` + `return`（**不调 `chain.doFilter`**）—— 对应 Go 的短路

**实测**：5 个头全部命中、无 Allow-Credentials、已注册与未注册路径的 OPTIONS 都是 204 空 body、404 也带 CORS 头。**与 Go 一致**。

### 2.4 保护层（`com.rag.common.protection`，配置在 `application.yml` 的 `rag.protection.*`）

| 层 | 生效值 | 判据 | 输出 |
|---|---|---|---|
| MaxConns | 10000 | 并发闸门 TryBorrow 失败 | 裸 **503** |
| Breaker | 开 | `Allow()` 失败 | 裸 **503** |
| Shedding | 开，`cpu-threshold: 900`（千分比） | `Allow()` 失败 | 裸 **503** |
| MaxBytes | **10485760**（`application.yml` 显式写） | **`request.getContentLengthLong() > n`**（chunked 时 -1，放行） | 裸 **413**，用 `setStatus` 而非 `sendError` |
| — | — | — | 无 Go `RecoverHandler` 对应物（Spring 无此层，靠 `@ExceptionHandler(Exception)`） |

**实测**：20 MiB body ⇒ **413**、`Content-Length: 0`、**无 Content-Type**、**无 nosniff**。
与 Go 的 `maxbyteshandler.go:22`（裸 `w.WriteHeader(413)`）逐项一致。✅

### 2.5 超时（`HttpMiddlewareFilter` → `com.rag.common.observability.HttpMiddleware`）

- 预算来自 `observability.http-timeout: 70s`
- Spring 无原生第二层超时（对应 Go 的 `DisableNativeRestTimeout` 是**无需动作**，但**结果一致**）
- `GoStyleRequestDeadline.begin/clear` 把 deadline 放进 `ThreadLocal`，由 worker 线程的 finally 清理
- 超时后的客户端可见结果：**503 + `Request Timeout`**（对应 Go 的 `http.Error(w, "Request Timeout", 503)`）

⚠️ **未实测**（需等 70s）：源码层两侧一致，但没跑过。

### 2.6 请求解析

**JSON body 的判据（`GoStyleJsonBodyConverter.goReadsBody`）**：
```java
contentLength > 0 && contentType != null && contentType.contains("application/json")
```
不满足 ⇒ `read` 返回 **`null`**（不消费请求流）⇒ 上层 `GoStyleRequest.normalize(null, factory)`
产出零值 DTO ⇒ 必填校验报 `is not set`。
⇒ 与 Go 的 `mapping.UnmarshalJsonMap(nil, v)` 等价（Go 也不读 body）。

⚠️ **与 Go 的唯一结构差异**：Go 在满足判据后用 `io.LimitReader(r.Body, 8<<20)` **截断**；
Java **没有截断**，完整交给 Jackson。

**必填 / optional / null 的实测**（真 Tomcat）：
```
{"title":"t","content":"x"}               -> 200（进业务）
{"content":"x"}                            -> 400 field "title" is not set
{"title":null,"content":"x"}               -> 400 field "title" mustn't be nil
{"title":123,"content":"x"}                -> 400 type mismatch for field "title"
{"title":"t","content":"x","brief":null}   -> 200（接受）✅ 与 Go 的 ,optional 一致
{"title":"t","content":"x","status":null}  -> 200（接受）✅
{"title":"t","content":"x","status":"2"}    -> 400 Cannot coerce String value ("2") … （Jackson 文案，非 Go 文案）
CT: text/plain + 合法 JSON                  -> 400 field "title" is not set（body 被丢弃）✅
无 Content-Type / 空 body                   -> 400 field "title" is not set ✅
9 MiB 合法 JSON                             -> 200（进业务）❌ Go 是 400
```

`CreateArticleReq` 的 setter 形参刻意声明成 `Object`，配合 `titlePresent`/`contentPresent`
两个布尔位区分「缺席」与「显式 null」——这是**必要**的（Jackson 默认把两者都映射成 `null`）。

**query 解析（`GoFormValues` + `ListArticlesReq` 构造器）**：
- 空值丢弃 ⇒ `raw()` 返回 `null` ⇒ 与 Go 的 `GetFormValues` 一致
- bool：`1` / `true`(ignoreCase) / `0` / `false`(ignoreCase)，否则抛 `GoStyleBadRequestException("type mismatch for field \"x\"")`
- int32：`Integer.parseInt(v, 10)` ⇒ `?page=010` → **10**（不是八进制 8）
- 8 个 query 参数全部声明成 `String`（不用 `@RequestParam(defaultValue=...)`）

**实测**：`?desc=1` → 200；`?desc=yes` → 400 `type mismatch for field "desc"`；
`?page=010` → 200；`?page=abc` → 400 ParseInt 文案；`?page=&sort_by=` → 200。
**与 Go 逐项一致** ✅

---

## 3. External Interaction

| # | Operation | Input | Expected | Failure | 结果影响分支 | 失败阻断 | 副作用 |
|---|---|---|---|---|---|---|---|
| E-1 | `getArticle(public_only=true)` | `{articleId, incrView, publicOnly}` | 已发布文章 | `NOT_FOUND`→1002 / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | `incrView` ⇒ +1 |
| E-2 | `getArticle(requesterId=me)` | `{articleId, requesterId}` | 文章 + 作者 | `NOT_FOUND`→1002 / `PERMISSION_DENIED`→1006 / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 无 |
| E-3 | `listArticles(public_only=true)` | 4 筛选(optional 恒 set) + page/pageSize/sortBy/desc | 文章页 | `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 无 |
| E-4 | `createArticle` | 7 字段 | 新文章 id | `ALREADY_EXISTS`→1001 / `FAILED_PRECONDITION`→**1004 带 payload** / `DEADLINE_EXCEEDED`→**1003 带 payload** / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 写库 + 发布 |
| E-5 | `updateArticle` | 仅非空字段 | success | `NOT_FOUND`→1002 / `INTERNAL`→5001 / 其它→1015（**无 PERMISSION_DENIED**） | 是 | 是 | 写库 |
| E-6 | `deleteArticle` | `{articleId, operatorId}` | 无 | `NOT_FOUND`→1002 / `PERMISSION_DENIED`→1006 / `INTERNAL`→5001 / 其它→1015 | 是 | 是 | 软删 |
| E-7 | `uploadFile` | `{content, fileName}` | fileUrl | **抛 `GoStyleBadRequestException`** ⇒ 400 text/plain | 是 | 是 | 写对象存储 |
| E-8 | `getUser` | `{uid long}` | 用户 | **4 条分支全部回落昵称**；且**日志调用额外包了 try/catch** | 否 | **否** | 无 |

**实测（无下游，全部落到 `default → 1015`）**：
7 个业务端点全部返回 `{"code":1015,"msg":"服务繁忙","data":null}`，
与 Go 的 `default: return nil, errmsg.CodeServerBusy` 一致。
上传：`rpc call UploadFile failed: Status{code=UNAVAILABLE,…}` ⇒ **400 text/plain**（与 Go 一致）。

⚠️ `ArticleRpcClient.articleRpc()` / `UserRpcClient.userRpc()` 每次调用都
`GoStyleRequestDeadline.applyTo(stub)`（不缓存），保证每次都带当前请求的剩余预算。

---

## 4. Result Semantics

| 概念 | Java 的表达 | 落点 |
|---|---|---|
| Success | `Response(200,"OK",body)` | HTTP 200 + 信封（由 `GlobalResponseAdvice` 包） |
| Not Found（文章） | `BusinessException(1002)` | HTTP **200** + `{code:1002,msg,data:null}` |
| Not Found（用户） | `!resp.getFound()` | **不是异常** → 回落昵称 |
| Empty（列表空） | `cloneStringList(空) → null` | `"articles": null`（**不是** `[]`） |
| Null vs Missing | `ext_info`/`secondary_tags` 保持 null；`author_name` 用 `@JsonInclude(NON_EMPTY)` | 与 Go 一致 |
| Default | `GoFormValues.*(raw, default)` | 仅在缺席时 |
| Unauthorized（无 token） | `AuthenticationException` → `ResponseEntity.status(401).build()`（**`Void`**，不选 MediaType） | 401 空 body |
| Unauthorized（有 token 无 userId） | `BusinessException(1005)` | HTTP **200** |
| Dependency Failure | `BusinessException(1015)` | HTTP 200 |
| Timeout | `HttpMiddleware` 503 + `Request Timeout` | 裸 |
| Invalid Input | `GoStyleBadRequestException` / `GoRequiredFieldException` / Spring 解析异常族 → 400 text/plain | 裸 |
| Framework 404 | `GoStyleRouterErrorResponses.notFound` | 400 形状同款 |
| Framework 405 | `GoStyleRouterErrorResponses.methodNotAllowed` | ❌ Go 无此形状 |
| 未预期异常 | `handleAny` → 500 空 body | 对应 Go 的 `RecoverHandler` |

⚠️ **Go 侧自己就不一致的两处，Java 侧照抄了**：
`getArticle` 的 `res==null` ⇒ **1002**；`listArticles` 的 `res==null` ⇒ **5001**。
（实测：Java 侧 stub 返回 null 时，`GET /v1/article/1` ⇒ 1002，`GET /v1/articles` ⇒ 5001。）

---

## 5. Error Semantics

### 5.1 身份解析（`ArticleApiSupport.resolveUserIdClaim`）

```
request.getAttribute("userId")
null                -> null ⇒ BusinessException(1005)
byte[]              -> new String(bytes, UTF-8) 再 trim
其它                 -> String.valueOf(claim)；"<nil>" ⇒ null；再 trim
trim 后为空          -> null ⇒ 1005
```
claim 存的是 **原始对象**（jjwt 的 `claims.get("userId")`），不做类型转换 ——
与 Go 侧 `ctx.Value("userId")` 拿到 `json.Number`/`string` 后再取字面量的形状一致。

**实测**：`{"userId":1001}`（数字）⇒ 进业务；`{"userId":"2002"}`（字符串）⇒ 进业务；
无 `userId` claim ⇒ **200 + `{code:1005,msg:"请先登录"}`**。✅ 与 Go 一致。

### 5.2 JWT 提取（`BearerTokenExtractor.tokenForSignatureVerification`）

**实测**：`Bearer <tok>` ⇒ 进业务；`<tok>`（无前缀）⇒ **进业务** ✅ 与 Go 一致；
`bearer <tok>` ⇒ 进业务 ✅。`Basic <tok>` / `Bearer` 单独未在 Java 侧单独实测（Go 侧为 401）。

### 5.3 文章 id 从 status 文案抠取（`ArticleCreateService.extractArticleIdFromStatusMessage`）

```java
message == null || isEmpty  -> ""
lastIndexOf(':') < 0        -> ""
idx + 1 >= length           -> ""
否则                         -> substring(idx+1).trim()
```
与 Go 逐字一致（含两个边界）。

**实测**：Java 测试日志 `CreateFailure code=1004 msg=文章发布失败 articleId=1792670400123`。

### 5.4 作者昵称（`ArticleAuthorResolver.resolveAuthorName`）

四条失败分支与 Go 一一对应，额外加了一条 **日志调用的 try/catch**（Go 的 logger 不会让请求失败，
Java 的 `LoggerFactoryBean` 若异常会把「回落昵称」变成 500）。
⇒ 这条**比 Go 更防御**，但方向一致（都不冒泡），判为 EQUIVALENT。

---

## 6. State Semantics

| 状态 | 初始 | 转换 | 终止 |
|---|---|---|---|
| 请求 deadline | ThreadLocal 无值 | `HttpMiddleware` 在 worker 线程 `begin()` | worker 的 finally `clear()` |
| HTTP 响应 | Spring 默认 | advice / handler 写入 | 容器提交 |
| 熔断器计数 | 0 | 每请求 +1 | 常驻 |
| 并发闸门 | 10000 | 借/还 | 常驻 |
| gRPC 连接 | devh 建 channel | 每请求调用 | 容器关闭 |
| **业务数据** | — | — | **组件内不持有任何业务状态** |

⚠️ **`ThreadLocal` 的两个前提**（Go 侧无对应物、因为 Go 用 ctx）：
① 必须在**跑业务的 worker 线程**上 set；② 必须在同一线程的 finally 里 clear。
两处都由 `HttpMiddleware` 负责。**未做压测验证**。

---

## 7. Concurrency

| 维度 | Java 侧事实 |
|---|---|
| Concurrent Calls | Tomcat 线程池（默认 max-threads=200）+ `GoStyleMaxConnsFilter`(10000) |
| Mutual Exclusion | 与 Go 同一套 `com.rag.common.protection` 实现 |
| Duplicate Execution | **无幂等保护**。OP-2/OP-3 的「先读后写」两次调用存在同样��� TOCTOU 窗口 |
| Atomicity | OP-2/OP-3 读+写非原子（两个独立 RPC），与 Go 同 |
| Lost Update | OP-2 无版本号 ⇒ 后写覆盖先写 |
| Visibility | 经 gRPC，天然可见 |
| Idempotency | 与 Go 相同（OP-1 非幂等 ⇒ 1001；OP-2/3 非幂等；OP-5 `incr_view` 有副作用） |
| Ordering | 单请求内同步顺序执行；跨请求无序 |

⚠️ **Tomcat 线程池（200）比 Go 的 goroutine 模型更容易成为瓶颈**。
在 Go 侧「Go 立刻 503 拒绝」与 Java 侧「排队」在过载时是**不同的客户端可观察行为**。
⇒ `GoStyleMaxConnsFilter` 保留了 10000 的闸门，但 200 线程会先饱和 ⇒ **排队先于拒绝发生**。

---

## 8. Timeout / Retry

**Timeout**
- HTTP 层：`observability.http-timeout: 70s`，scope = **整个 operation**
- 传播：`GoStyleRequestDeadline` → `withDeadlineAfter(remaining)`，每次调用新建 stub
- 预算耗尽 ⇒ 给 **1 纳秒**（对应 Go 的 ctx 已到期 ⇒ 下游立刻失败）
- ⚠️ `CreateArticle` **没有**像 Go 那样显式 `WithCallTimeout(70s)`；
  本部署下 HTTP 预算也是 70s ⇒ **等值**。若日后调小 HTTP 超时，这条会出现差异
- gRPC 超时 ⇒ `DEADLINE_EXCEEDED` ⇒ 与 Go 同码
- ⚠️ **`Future.cancel(true)` 路径会产生 `CANCELLED` 而不是 `DEADLINE_EXCEEDED`**，
  而 `CANCELLED` **不在** breaker 的失败名单里 ⇒ 可能被记为成功

**Retry**
- **无**（两侧一致）。`application.yml` 未配任何 retry 策略
- **breaker 有**且默认开：`GoStyleGrpcClientBreakerInterceptor`，
  失败名单照抄 Go 的 6 个 code（`DeadlineExceeded`/`Internal`/`Unavailable`/`DataLoss`/`Unimplemented`/`ResourceExhausted`）
- 熔断拒绝时抛 `GoStyleCircuitOpenException`（**普通异常，不是 `StatusRuntimeException`**），
  由 `GlobalExceptionHandler` 单列 handler ⇒ **1015 + msg 查表**，与 Go 的 `ErrServiceUnavailable` 一致

---

## 9. Serialization

**出参**
- Jackson 3（`tools.jackson`），`GlobalResponseAdvice` 统一包信封
- `Response` 的 `code`/`msg`/`data` **无 `@JsonInclude`** ⇒ `data` 恒出现 ✅ 与 Go 一致
- `Article.authorName` 用 `@JsonInclude(NON_EMPTY)` ⇒ 空串时 key 消失 ✅
- `secondaryTags` / `extInfo` 保持 `null`，用 `@JsonProperty` 显式命名（仓库无 snake_case 命名策略）
- `<` `>` `&` **不转义**（Jackson 默认）⇒ 与 Go 的 `SetEscapeHTML(false)` 同向 ✅
- ⚠️ **`ext_info` 键序**：`cloneStringMap` 用 `LinkedHashMap` 承接 `proto.getExtInfoMap()` 的迭代顺序
  ⇒ **不排序**；Go 的 `encoding/json` **排序** ⇒ 字节序不一致（DRIFT-007）
  ⚠️ 源码注释声称 LinkedHashMap「保持 Go 的字典序」——**该断言为假**

**入参**
- 未知字段：实测 `{"zzz":1}` 被忽略 ✅
- `ArticleGoStyleCoercion` = `GoStyleCoercion` + 两条补强：
  `LogicalType.Integer/Float` × `CoercionInputShape.String` ⇒ `Fail`
  理由：article 的 `types.go` **全文没有一个 `,string` option**，所以 users 域那条
  「String → int 豁免」的前提在本文��**不成立**
- ⚠️ 类型不匹配的**错误文案**与 Go 不同（DRIFT-006）

**Content-Type**
- 成功：`application/json`（由 Jackson converter 产出）
- ⚠️ 实测 Java 的 404 是 `text/plain;charset=utf-8`（**分号后无空格**），
  而 `GoStyleRouterErrorResponses` 源码里写的是 `"text/plain; charset=utf-8"`（**有空格**）
  ⇒ **Tomcat 把 MediaType 重新序列化时丢了空格**（DRIFT-005）

---

## 10. Lifecycle

| 阶段 | Java 行为 |
|---|---|
| Startup | 读 `application.yml` → 建 MVC/Jackson3/gRPC/Nacos/Tomcat |
| Ready | Tomcat 接受连接；Nacos 注册实例 |
| Shutdown | Spring context 关闭 → Filter/interceptor → devh channel 关闭 → Nacos 注销 |
| ⚠️ 启动期硬依赖 | Nacos 不可达 ⇒ `NacosAutoServiceRegistration` **抛异常导致 context 启动失败**（本次实测） |

⚠️ **实测到的启动失败模式**：`NacosAutoServiceRegistration.register` 在 `Client not connected, current status:STARTING` 时
抛 `ErrCode:-401`，导致 `WebServerStartStopLifecycle.start` 失败。
Go 侧对应的是 `zrpc.MustNewClient` 无 etcd 直接 panic。
⇒ **两侧都是「依赖不可达 ⇒ 启动失败」，但 Java 侧失败得更晚**（WebServer 生命周期里），
且错误类型不同（panic vs context startup exception）。判为 UNKNOWN（未在 Go 侧实测启动失败形态）。

---

## 11. Spring 侧实测记录

见 `java-probe.sh` 的执行输出与 `mvn-article-api-test.log`（43/43 绿）。
