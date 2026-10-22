# Semantic Drifts — `article-api`

只记录**真正需要关注的**差异。分类：
`A. IMPLEMENTATION DIFFERENCE`（实现不同、语义相同）/ `B. OBSERVABLE DIFFERENCE`（外部可观察）/ `C. SEMANTIC MISMATCH`（改变系统应如何行为）/ `D. UNKNOWN`

---

## DRIFT-001

| 字段 | 内容 |
|---|---|
| **Component** | 路由层（go-zero `patRouter` vs Spring `DispatcherServlet`） |
| **Operation** | 全部 7 个端点的**方法不匹配**请求（如 `POST /v1/article/1`、`PATCH /v1/articles`、`GET /v1/upload`） |
| **Category** | **C. SEMANTIC MISMATCH** |
| **Risk** | 🔴 **CRITICAL · HIGH-RISK DRIFT**（Counterfactual Test = YES） |
| **Go Behavior** | `Input` 路径命中已注册的 `:id` 段，但 method 未注册 → `Condition` `pr.methodsAllowed` 返回非空 → `Action` 调用 `pr.notAllowed` —— 而这个 handler 已被 `rest.WithCors()`（`server.go:150`）覆盖为 `cors.NotAllowedHandler(nil)`，它对非 OPTIONS 请求执行 `gw.WriteHeader(http.StatusNotFound)`（`internal/cors/handlers.go:47`）→ `Result` **HTTP 404 + 空 body + 无 Content-Type + 无 `Allow` 头** |
| **Java Behavior** | `Input` 同上 → `Condition` Spring 抛出 `HttpRequestMethodNotSupportedException` → `Action` `GlobalExceptionHandler#handleMethodNotSupported` 调 `GoStyleRouterErrorResponses.methodNotAllowed(...)` → `Result` **HTTP 405 + 空 body + 无 Content-Type + `Allow: PUT, DELETE, GET`** |
| **Difference** | 状态码 404 → 405；多一个 `Allow` 响应头；**响应头的值集合与顺序也不同**（Go 的 `methodsAllowed` 遍历 `map[string]*search.Tree`，**顺序随机**；Java 给出确定顺序） |
| **Why It Matters** | ① 客户端若按状态码分流（`404` 常被前端当作「资源不存在」而**永久不重试**，`405` 常被当作「可换 method 重试」），行为直接反转。② 监控/告警按状态码聚合时，这批请求在两侧落在**不同的错误桶**里。③ **契约不对称**：Go 侧「方法不对」与「路径不存在」**故意用同一个 404**（因为 `WithCors` 的副作用），Java 侧把它们拆成 404/405 —— 意味着 Java 侧**多暴露了一个 Go 侧刻意隐藏的信息**（该路径支持哪些 method）。 |
| **Observable Impact** | `curl -X POST http://host/v1/article/1` ：Go `404`，Java `405 Allow: PUT, DELETE, GET` |
| **Business-Technical Impact** | Business：前端把「方法写错了」显示成「文章不存在」在 Go 侧是**现状**；迁移到 Java 后会变成「方法不允许」，已有前端代码的分支逻辑会走到另一支。Technical：错误率看板、SLO 统计、5xx 告警不受影响（两者都不是 5xx），但 4xx 分项会变。 |
| **Likely Cause** | **没意识到 `rest.WithCors()` 会覆盖 `notAllowed` handler**。go-zero 的默认行为（`patrouter.go:79-82`）确实是 `Allow` + 405；`WithCors` 把它换成了返回 404 的 CORS handler。实现方读了 `patrouter.go:79` 的 405 分支代码，**但没读到 `WithCors` 对该 handler 的替换**。 |
| **Must Preserve** | Go 侧方法不匹配的**完整可观察形状**：404、空 body、无 Content-Type、无 `Allow`。**并且要区分「路径未注册」（404 + body）与「路径命中但方法不对」（404 + 无 body）** —— 这两支在 Go 侧是不同代码路径产出的不同形状。 |
| **Recommended Verification** | ① 真 Go 侧起进程后 `curl -X POST /v1/article/1` 断言 **404 且 body 为空且无 `Allow`**；② Java 侧加一条 MockMvc 用例断言 `status().isNotFound()` + `content` 为空 + `header().doesNotExist("Allow")`；③ **改测试前先改 `ArticleApiRouteAuthBoundaryTest` 的两条 405 断言**（它们锁的是错误答案）。⚠️ 改共享层 `GoStyleRouterErrorResponses` 前需先确认 message/like/hot 三域是否也有同样问题。 |
| **Evidence** | [G] `go-probe`: `MISMATCH POST /v1/article/1 -> 404 ct="" nosniff="" allow="" acao="*" body=""`；`MISMATCH PATCH /v1/articles -> 404`；`MISMATCH DELETE /v1/articles -> 404`；`MISMATCH POST /v1/me/article/1 -> 404`；`MISMATCH GET /v1/upload -> 404`<br>[J] `java-probe.sh`: `MISMATCH POST /v1/article/1 -> 405 ... allow="PUT, DELETE, GET"`；`PATCH/DELETE /v1/articles -> 405 allow="GET"`；`POST /v1/me/article/1 -> 405 allow="GET"`<br>[S] `rest/server.go:148-153` + `rest/router/patrouter.go:71-82` + `rest/internal/cors/handlers.go:44-48`<br>[T] `ArticleApiRouteAuthBoundaryTest#wrongMethodOnProtectedShapeIs405Not401` 与 `#methodNotAllowedIsBare` **断言了 405**（全绿）⇒ 这正是 AGENTS.md §3.3「绿测试守卫错误答案」的实例 |

---

## DRIFT-002

| 字段 | 内容 |
|---|---|
| **Component** | 请求解析层 |
| **Operation** | OP-1 `CreateArticle`、OP-2 `UpdateArticle`（任何 `Content-Type: application/json` 且 body > 8 MiB 的请求） |
| **Category** | **C. SEMANTIC MISMATCH** |
| **Risk** | 🔴 **HIGH**（Counterfactual Test = NO，但影响面真实） |
| **Go Behavior** | `Input` `Content-Length = 9 MiB`、`Content-Type: application/json` → `Condition` `withJsonBody(r)` 为真 → `Action` `httpx.ParseJsonBody` 用 **`io.LimitReader(r.Body, maxBodyLen)`** 包裹请求体，`maxBodyLen = 8 << 20 = 8388608`（`requests.go:21,119`）→ `Result` JSON 在 8 MiB 处被**静默截断**，`json.Decoder.Decode` 返回 `unexpected EOF`，`jsonx.formatError` 包成 `string: \`…\`, error: …` → handler 的 `httpx.ErrorCtx` → **HTTP 400 + text/plain + nosniff**，**永不调用 article-rpc** |
| **Java Behavior** | `Input` 同上 → `Condition` `GoStyleJsonBodyConverter.read` 只判断 `goReadsBody`（`contentLength > 0 && ct.contains("application/json")`），**没有任何长度上限** → `Action` Jackson 完整解析 9 MiB → `Result` **HTTP 200 + `{code:1015/200/…, data:…}`**，**真的调用 article-rpc** |
| **Difference** | 8 MiB ~ 10 MiB 这条 **2 MiB 宽的带**里，Go 恒 400（到不了业务），Java 恒进业务。`MaxBytes=10485760`（yaml 显式配置）比 go-zero 的 `maxBodyLen` 大，所以这条带**是真实可达的**——一个遵守「请求体不超过 10 MiB」文档的客户端会踩中。 |
| **Why It Matters** | ① **文章正文（含 markdown + 内嵌 base64 图片）超过 8 MiB 是常见场景**，不是理论边界。② Go 侧这个请求**不会落库**，Java 侧**会落库** ⇒ 迁移后**数据面从「拒绝」变成「接受」**，且用户会以为 Go 侧也有这个能力。③ 更糟的是：`MaxBytes` 在 yaml 里被显式调高到 10 MiB，**这在 Go 侧是一个误导性的承诺** —— 上限写着 10 MiB，实际 8 MiB 就拒。 |
| **Observable Impact** | `curl -X POST -H "Content-Type: application/json" --data-binary <9MiB JSON> /v1/article`：Go `400 text/plain`；Java `200 + envelope` |
| **Business-Technical Impact** | Business：Go 侧运营会收到「大文章发不上去」的用户投诉；Java 侧不会 ⇒ 迁移后**投诉消失但下游存储压力上升**。Technical：两套部署的 article 表数据量会分叉；GC 峰值内存曲线不同（Java 需要把 9 MiB JSON 全量驻留）。 |
| **Likely Cause** | `httpx.ParseJsonBody` 里的 `maxBodyLen` 是 **go-zero 的内部常量**，不是 `RestConf` 的字段 —— 它**不在 yaml 里、不可配置、也不在任何框架 tag 里**。逐字段对 yaml 永远看不见它（AGENTS.md §3.1「否定式断言 / 表面配置对法」的又一次发作）。 |
| **Must Preserve** | Go 侧的「> 8 MiB 的 JSON body 一律 400 text/plain」，**包括它是一条静默截断而不是显式拒绝**这个事实（错误文案也来自截断后的解析器，不是任何自定义消息）。 |
| **Recommended Verification** | 在 `GoStyleJsonBodyConverter.read` 里，当 `goReadsBody(...)` 为真且 `contentLength > 8L*1024*1024` 时，走「返回 null」路径（等价于 Go 的空对象路径）**或**抛 `GoStyleBadRequestException`。⚠️ 注意 Go 是**先截断再解析**，所以**任何**超过 8 MiB 的 JSON 都必然失败 —— 不存在「恰好在某个边界上能解析成功」的情况。加完后用 7 MiB（应成功）/ 9 MiB（应 400）双侧对拍。 |
| **Evidence** | [G] `go-facts.txt`: `7MiB JSON body -> err=<nil>` / `9MiB JSON body -> err=string: \`…\` （截断到 8388741 字节）` / `11MiB JSON body -> 同上`<br>[S] `rest/httpx/requests.go:20-21`（`maxBodyLen = 8 << 20`）与 `:117-124`<br>[J] 真 Tomcat 实测：9 MiB 合法 JSON → `200 {"code":1015,...}`<br>[T] `ArticleApiRouteAuthBoundaryTest#withinLimitPassesThrough` 只测了 9 MiB **空 body**（全是空格）⇒ 断言 400，但那是**必填校验**失败，**不是** 8 MiB 截断失败 ⇒ 该测试对这条 drift **完全无感** |

---

## DRIFT-003

| 字段 | 内容 |
|---|---|
| **Component** | OP-7 `UploadImage` |
| **Operation** | `POST /v1/upload` 带非 multipart 的 `Content-Type` |
| **Category** | **C. SEMANTIC MISMATCH** |
| **Risk** | 🔴 **HIGH**（Counterfactual Test = NO） |
| **Go Behavior** | `Input` `POST /v1/upload`，`Content-Type: application/json`，body `{}` → `Condition` `r.FormFile("image")` 内部先调 `ParseMultipartForm`，因 CT 非 multipart 立即失败 → `Action` `UploadImageHandler` 的 `httpx.ErrorCtx(r.Context(), w, err)` → `Result` `errcode.IsGrpcError(err)` 为 false ⇒ `http.Error(w, err.Error(), 400)` ⇒ **HTTP 400 + `text/plain; charset=utf-8` + `nosniff` + `request Content-Type isn't multipart/form-data\n`** |
| **Java Behavior** | `Input` 同上 → `Condition` Spring 的 `StandardServletMultipartResolver` 判定为非 multipart ⇒ `@RequestParam MultipartFile` 解析失败 → `Action` 抛出的是一个**未被 `GlobalExceptionHandler` 的 6 个 `@ExceptionHandler` 覆盖**的异常（其列表只有 `MaxUploadSizeExceededException`，不含其父类 `MultipartException`）→ `Result` 落到兜底 `@ExceptionHandler(Exception)` ⇒ **HTTP 500 + 空 body + 无 Content-Type** |
| **Difference** | 400（带可读文案）→ 500（无 body）。**且 500 会污染熔断器**：Go 侧 `BreakerHandler` 的判据是 `cw.Code < 500 ⇒ Accept()`，Java 侧共享的 `GoStyleBreakerFilter` 同样如此 ⇒ 一次**客户端发错 Content-Type**的请求在 Java 侧会被记为**熔断失败**，Go 侧不会。 |
| **Why It Matters** | ① 400 = 客户端错，500 = 服务端错 —— 客户端重试策略、告警分级、值班响应全部分岔。② 通过熔断计数的传导，一个**外部可触发的输入**在 Java 侧会消耗熔断器配额（DoS 面）。③ 「上传接口对非 multipart 返回 500」是典型的运维工单。 |
| **Observable Impact** | `curl -X POST -H "Content-Type: application/json" -d '{}' /v1/upload`：Go `400 + 文案`；Java `500 + 空 body` |
| **Business-Technical Impact** | Business：Android / 某些 SDK 在上传失败重试时会退化成 JSON 请求 ⇒ Java 侧每次都产出 500 + 告警。Technical：熔断器 `Reject` 计数被外部输入污染。 |
| **Likely Cause** | `GlobalExceptionHandler#handleParseFailure` 的 `@ExceptionHandler({...})` 列表里列了 `MaxUploadSizeExceededException`，但**没列它的父类 `MultipartException`**（`MaxUploadSizeExceededException extends MultipartException`）。把父类漏掉是一个极常见的 Spring 异常族处理疏漏。 |
| **Must Preserve** | Go 侧**两条**上传失败路径（`FormFile` 失败、RPC 失败）都是 **400 text/plain**，且 `errmsg.ErrorMinioUpload(5004)` **只进日志不进响应**。 |
| **Recommended Verification** | 把 `handleParseFailure` 的 `@ExceptionHandler` 列表里的 `MaxUploadSizeExceededException.class` 换成 `org.springframework.web.multipart.MultipartException.class`（父类覆盖子类），加一条用例：`POST /v1/upload` + `Content-Type: application/json` ⇒ 断言 400 + text/plain + nosniff。 |
| **Evidence** | [G] `go-facts.txt`: `upload CT=application/json {} -> err=request Content-Type isn't multipart/form-data`（⇒ 必落 400，见 `httpx/responses.go:150-155`）<br>[J] 真 Tomcat 实测：`upload NO multipart at all -> 500`（body 空）<br>[S] `rest/httpx/responses.go:142-159`；`GoStyleBreakerFilter` 的 `code < 500 ⇒ Accept` |

---

## DRIFT-004

| 字段 | 内容 |
|---|---|
| **Component** | 路由层（路径规范化） |
| **Operation** | 全部 7 个端点；入口是**尾斜杠**或**重复斜杠**的 URL |
| **Category** | **C. SEMANTIC MISMATCH** |
| **Risk** | 🟡 **MEDIUM**（Counterfactual Test = NO） |
| **Go Behavior** | `Input` `GET /v1/articles/` → `Condition` `patRouter.ServeHTTP` 第一行 `reqPath := path.Clean(r.URL.Path)`，`path.Clean("/v1/articles/")` = `"/v1/articles"` ⇒ 命中已注册路由 → `Action` 进 handler 链 → `Result` **HTTP 200 + 正常业务响应**。重复斜杠同理。**且 `PUT /v1/article/1/` 会 Clean 成 `/v1/article/1` ⇒ 命中受保护路由 ⇒ 401**（不是 404） |
| **Java Behavior** | `Input` 同上 → `Condition` `PathPatternParser` 不做 `Clean`，Spring Boot 3+ 的 `useTrailingSlashMatch` **默认 false**（Boot 2 默认 true，Boot 3 改掉了）→ `Result` **HTTP 404 + `404 page not found\n`**。重复斜杠由 Tomcat 在到达 DispatcherServlet 之前就规范化/拒绝 |
| **Difference** | 同一组 URL：Go 全部**进业务**，Java 全部 **404**。这同时改变了**鉴权边界**：Go 上 `PUT /v1/article/1/` 是**受保护**的（401），Java 上是 404（未进鉴权链）。 |
| **Why It Matters** | ① 前端路由库、CDN、反向代理都可能规范化或追加斜杠 ⇒ 同一用户在两侧看到不同结果。② **鉴权边界的形状变了**：Java 侧「`/v1/article/1/`」落在 404 而非 401，看起来像「该路径不存在」，**弱化了「这是一个需要登录的资源」这一信息**。③ 与 DRIFT-001 叠加：`GET /v1/upload/` 在 Go 是 404（方法不匹配），在 Java 是 404（路径不匹配），**同码不同因**。 |
| **Observable Impact** | `curl /v1/articles/`：Go `200 {"code":…}`；Java `404 404 page not found` |
| **Business-Technical Impact** | Business：分享链接 / 书签带尾斜杠时，Java 侧「文章不存在」，Go 侧正常。Technical：缓存键、埋点路径归一化结果不同。 |
| **Likely Cause** | **Spring Boot 3 的破坏性变更**（`setUseTrailingSlashMatch` 默认由 true 改为 false）在迁移时未被识别；这是 Spring 官方 CHANGELOG 里明确列出的行为变更。 |
| **Must Preserve** | Go 侧 `path.Clean` 的三条效果：① 去掉尾斜杠；② 合并重复斜杠；③ 解析 `.` / `..` 段。**只做 ① 成本最低，②③ 需要容器层配合**。 |
| **Recommended Verification** | 最低成本：`WebMvcConfig#addInterceptors` 里同时调 `registry.setUseTrailingSlashMatch(true)`。重复斜杠需另配 Tomcat 的路径规范化（`Connector` 的 `ALLOW_BACKSLASH` / `rejectIllegalHeader` 一类），成本更高，可先只处理尾斜杠并**显式登记为已知差异**。加对拍用例：`GET /v1/articles/` 两侧都应进业务（可用 RPC 失败落 1015 作为「进业务了」的判据）。 |
| **Evidence** | [G] `go-probe`: `trailing slash -> 200 ct="application/json" body={...}`；`double slash path -> 200`；`trailing slash on protected -> 401`<br>[J] `java-probe.sh`: `trailing slash -> 404 body="404 page not found"`；`/v1//articles -> 404`；`/v1/articles// -> 404`；`/v1/articles/. -> 404`<br>[S] `rest/router/patrouter.go:60` |

---

## DRIFT-005

| 字段 | 内容 |
|---|---|
| **Component** | 路由层 404 响应 |
| **Operation** | 全部 7 个端点的「未注册路径」分支 |
| **Category** | **B. OBSERVABLE DIFFERENCE** |
| **Risk** | 🟢 **LOW** |
| **Go Behavior** | `Content-Type: text/plain; charset=utf-8`（**分号后有一个空格**）—— `net/http.Error` 里的字面量 |
| **Java Behavior** | `Content-Type: text/plain;charset=utf-8`（**分号后没有空格**） |
| **Difference** | 同一个 header 名，值差一个空格。⚠️ **注意**：`GoStyleRouterErrorResponses` 的源码里写的是 `"text/plain; charset=utf-8"`（**有空格，意图是对的**）；是 **Tomcat 把 Spring 解析后的 `MediaType` 重新序列化时丢了空格**。⇒ 这是**容器行为**，不是业务代码的 bug。 |
| **Why It Matters** | 对 JSON 客户端**零影响**。对做**字节级响应快照测试**、WAF 规则、或按 header 字符串精确匹配的网关有影响。**HTTP 规范两种都合法**。 |
| **Observable Impact** | `curl -I /v1/nope` 的 `content-type` 行差一个空格 |
| **Business-Technical Impact** | Business：无。Technical：字节级快照测试会对不上。 |
| **Likely Cause** | Tomcat 的 `MimeHeaders` 用 `MediaType.toString()`（Spring 的 `MimeType.toString()` 不加分号后空格）重写 header。 |
| **Must Preserve** | 无实质要求。若要**真正**对齐，需要绕过 Tomcat 的 MediaType 解析（用 `response.setHeader` 直接写原始串，且在 `commit()` 之前）。 |
| **Recommended Verification** | 建议**登记为已知差异，不修**。若决定修：把 `GoStyleRouterErrorResponses.notFound` 从 `ResponseEntity.header(CONTENT_TYPE, …)` 改成直接操作 `HttpServletResponse.setHeader`（需要改成 `ResponseEntity` 之外的错误处理路径，成本远大于收益）。 |
| **Evidence** | [G] `UNREGISTERED /v1/nope -> 404 ct="text/plain; charset=utf-8"`<br>[J] `curl -D-` 原样输出：`Content-Type: text/plain;charset=utf-8`<br>[S] `GoStyleRouterErrorResponses.java:87` 写的是 `"text/plain; charset=utf-8"` ⇒ 意图正确，容器改写 |

---

## DRIFT-006

| 字段 | 内容 |
|---|---|
| **Component** | 解析层 |
| **Operation** | OP-1 / OP-2 的 JSON 字段类型不匹配 |
| **Category** | **B. OBSERVABLE DIFFERENCE** |
| **Risk** | 🟢 **LOW** |
| **Go Behavior** | `type mismatch for field "title", expect "string", actual "number"`（`core/mapping/unmarshaler.go` 的 `newTypeMismatchErrorWithHint`）。⚠️ 而 `mapping.UnmarshalJsonMap` 路径给的是**短版** `type mismatch for field "title"`（`newTypeMismatchError`）—— **Go 自己在两条路径上文案就不一样** |
| **Java Behavior** | `type mismatch for field "title"`（`GoStyleStringFields` 的自定义短版）。另有一处**完全不同的文案**：`{"status":"2"}` ⇒ `Cannot coerce String value ("2") to \`java.lang.Integer\` value (but might if coercion using \`CoercionConfig\` was enabled)` —— 这是 **Jackson 的原始文案**，里面还提到了 Jackson 自己的 API 名 |
| **Difference** | 状态码（400）与 Content-Type 全部一致；**只有 body 文案**。其中 `status="2"` 那条的 Java 文案**泄露了实现栈信息**（Jackson / CoercionConfig），对外部客户端不友好。 |
| **Why It Matters** | ① 若前端把 body 文案直接展示给用户，会出现中英混杂且带 Java 类库名的提示。② 若有客户端按 body 文本做分支，会失效。③ 一般不算契约。 |
| **Observable Impact** | body 字符串差异 |
| **Business-Technical Impact** | Business：文案体验；Technical：可忽略。 |
| **Likely Cause** | Go 的两条文案本身就不统一；Java 侧只对齐了其中一条。`status` 那条走的是 Jackson 抛异常而不是自定义格式化。 |
| **Must Preserve** | 判据（**哪些输入被拒**）必须一致 —— 这条目前是对的。 |
| **Recommended Verification** | 建议在 `GlobalExceptionHandler#handleParseFailure` 里把 Jackson 的 `InvalidFormatException` / `MismatchedInputException` 统一格式化成 Go 的 `type mismatch for field "x"`。⚠️ 抽字段名需要从 `path` 里取（Jackson 3 的字段名在 `JsonMappingException` 的 path 上），实现成本中等，收益低。**优先级最低**。 |
| **Evidence** | [G] `go-facts.txt`: `title=123 -> err=type mismatch for field "title", expect "string", actual "number"`；`mapping {"title":123} -> err=type mismatch for field "title"`（短版）<br>[J] 真 Tomcat 实测：`create title=123 -> 400 body="type mismatch for field \"title\""`；`update status="2" -> 400 body="Cannot coerce String value (\"2\") to \`java.lang.Integer\` value…"` |

---

## DRIFT-007

| 字段 | 内容 |
|---|---|
| **Component** | 序列化 |
| **Operation** | OP-4 / OP-5 / OP-6 的 `Article.ext_info`（`map[string]string`） |
| **Category** | **B. OBSERVABLE DIFFERENCE** |
| **Risk** | 🟢 **LOW** |
| **Go Behavior** | `types.Article.ExtInfo` 是 Go 的 `map[string]string` ⇒ `encoding/json` 在编码 map 时**对 key 排序**（`json/encode.go` 的 `resolveKeyName` + `sort.Slice`）⇒ 输出恒为**字典序** |
| **Java Behavior** | `cloneStringMap(proto.getExtInfoMap())` 用 `LinkedHashMap` 承接 protobuf 的 map 迭代顺序 ⇒ **不排序**，是**插入序** |
| **Difference** | 同一份 `ext_info` 数据，输出 JSON 的**键顺序不同**。JSON 语义等价（解析后 map 相等），**字节不等**。 |
| **Why It Matters** | ① 若有 ETag / 内容哈希 / 响应快照对比会不一致。② 若前端做了 `JSON.stringify(resp)` 的字符串比较会误判。③ 绝大多数 JSON 消费方不受影响。 |
| **Observable Impact** | `data.article.ext_info` 的键序 |
| **Business-Technical Impact** | Business：无。Technical：响应缓存键（若基于响应体哈希）与快照测试。 |
| **Likely Cause** | ⚠️ **Java 源码里的注释是错的**：`ArticleApiSupport.cloneStringMap` 的 javadoc 声称「用 `LinkedHashMap` 保持 Go `encoding/json` 的**字典序**输出」—— **`LinkedHashMap` 保的是插入序，不是字典序**。要真对齐必须用 `TreeMap`。这是 AGENTS.md §3.3 的又一实例：**注释断言了一个未被验证的性质**。 |
| **Must Preserve** | 无硬要求。若要对齐，改用 `TreeMap`（但这只在单篇响应里生效；列表里 `ext_info` 逐条独立，也要一并改）。 |
| **Recommended Verification** | ⚠️ **本次未做活体验证**（需要一个非空 `ext_info` 的真实 article-rpc）。结论基于两条确定事实：① `encoding/json` 对 map key 排序；② `LinkedHashMap` 保插入序。**建议**：先加一条**不依赖下游**的单元测试，直接对 `ArticleApiSupport.cloneStringMap` 输入乱序 map、断言输出 key 有序，把结论钉死。 |
| **Evidence** | [S] `core/jsonx/json.go:12-18`（`json.Encoder`，map key 排序是 `encoding/json` 的既定行为）；`ArticleApiSupport.java` 的 `cloneStringMap` 使用 `LinkedHashMap` 且 javadoc 声称「字典序」⇒ 断言与实现不符 |

---

## DRIFT-008

| 字段 | 内容 |
|---|---|
| **Component** | OP-1 `CreateArticle` |
| **Operation** | `article-rpc` 的 `CreateArticle` 返回 `(nil, nil)` |
| **Category** | **B. OBSERVABLE DIFFERENCE**（理论不可达） |
| **Risk** | ⚵ **INFO** |
| **Go Behavior** | `rpcResp` 是 `*articleservice.CreateArticleResponse`，为 nil 时 `rpcResp.ArticleId` **panic**（nil 指针解引用）⇒ 被 `rest/handler/recoverhandler.go:17` 捕获 ⇒ **HTTP 500 + 空 body** |
| **Java Behavior** | `rpcResp.getArticleId()` 在 protobuf Java 侧是**安全 getter**（对 null 消息返回默认值）⇒ 返回 `{"code":200,"data":{"article_id":""}}` |
| **Difference** | 500 裸响应 vs 200 + 空 id |
| **Why It Matters** | 实际上不可达（gRPC 的 unary 响应不会是 nil）。但它揭示了一个**设计取向**差异：Go 侧对下游契约违约是「崩溃」，Java 侧是「静默降级」。 |
| **Observable Impact** | 仅在下游违约时 |
| **Business-Technical Impact** | 无 |
| **Likely Cause** | 语言差异（Go 指针 vs protobuf Java 的默认值语义） |
| **Must Preserve** | 无 |
| **Recommended Verification** | **保持现状**（Java 侧的注释已经明确说明了这个取舍及其理由，理由成立：把「理论上不该发生的情况」变成生产 500 路径是更坏的选择）。建议**仅补一条注释指向本 DRIFT 编号**，便于日后检索。 |
| **Evidence** | [S] `create_article_logic.go:77-79`；`ArticleCreateService.java` 的类注释「Go 侧 `rpcResp.ArticleId` 会在 nil 上 panic ⇒ recover → 裸 500。本侧等价：proto 的 getter 对 null 消息返回默认值，不会 panic」 |

---

## 已排除的候选差异（记录以免重复排查）

| 候选 | 结论 | 依据 |
|---|---|---|
| `POST /v1/upload` 需要登录 | **不是漂移**。Go 侧第三段 `AddRoutes` 确实**没有** `rest.WithJwt`，upload **是公开的** | [S] routes.go:63-73 |
| 解析失败是 200 + 信封 | **不是漂移**。article-api **没有** error handler ⇒ 400 text/plain | [S] grep 零命中；[G]/[J] 实测 |
| `SecurityRpc` 未建 client | **不是漂移**。Go 侧配了但**零消费者**，不建 client 是正确的 | [S] `grep -rn SecurityRpc service/article/api` 仅 `service_context.go` |
| `CreateArticle` 的 70s 独立超时 | **不是漂移**。Go 有 `WithCallTimeout(70s)`，Java 靠 HTTP 预算 70s —— **本部署下同值** | [S] |
| gRPC client retry | **不是漂移**。Go 无（`RpcClientConf` 无 `Retries`），Java 也无 | [S] |
| 415 响应 | **不是漂移**。两侧都在到达转换器前就把非 JSON 当空对象处理，415 **从不发生** | [G]/[J] 实测 `CT=text/plain` 两侧都是 400 |
| `author_name` 空串的 key 消失 | **不是漂移**。Go `,omitempty` ↔ Java `@JsonInclude(NON_EMPTY)` | [S] |
| `ext_info` 空 map 变 null | **不是漂移**。Go `cloneStringMap` 空→nil ↔ Java 同名方法 | [S] |
| 列表里出现非 PUBLISHED 就整条 5001 | **不是漂移**（实现方论断 7 成立） | [S] + Go 自带测试断言 5001 |
| `status=0` 不下发 | **不是漂移**。两侧都只在 `!= 0` 时下发 | [S] |
