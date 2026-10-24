# Equivalence Matrix — `article-api`

**Result 取值**：`EQUIVALENT` / `IMPLEMENTATION DIFFERENCE` / `OBSERVABLE DIFFERENCE` / `SEMANTIC MISMATCH` / `UNKNOWN`
**证据标记**：`[G]` = Go 侧实测（`go-probe`，真 go-zero v1.10.2 引擎）；`[J]` = Java 侧实测（真 Tomcat，`java-probe.sh`）；`[S]` = 源码逐行；`[T]` = 两侧测试（Go 自带 / Java 43 条）

---

## A. 全局 / 组件级

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| 全局 | 进程入口 | `main()` 手写 7 步 | `@SpringBootApplication` + 自动装配 | IMPLEMENTATION DIFFERENCE | [S] article.go:23-41 |
| 全局 | 响应封装注册 | **无** `SetOkHandler`/`SetErrorHandler`（grep 零命中） | **无**等价注册（advice 直接包信封，但语义等价于「手写信封」） | EQUIVALENT | [S]+[T] grep；[G] 400 text/plain；[J] 400 text/plain |
| 全局 | 路由前缀 | `rest.WithPrefix("/v1")` × 3 段 | `@RequestMapping("/v1")` | EQUIVALENT | [S] routes.go:43,60,71 |
| 全局 | CORS 启用 | `rest.WithCors()`（无参 ⇒ `*`） | `ArticleGoStyleCorsFilter`（`*`，不写 Allow-Credentials） | EQUIVALENT | [S] server.go:148-153；[G]/[J] 5 头全命中 |
| 全局 | CORS 位置 | `corsRouter` 包在 router **外** ⇒ 404/预检也带 CORS 头 | Filter `HIGHEST_PRECEDENCE` ⇒ 404/预检也带 CORS 头 | EQUIVALENT | [S] server.go:355-357；[G]/[J] 404 `acao=*` |
| 全局 | `OPTIONS` | **204 空 body，不进 handler**（任意路径） | **204 空 body，不进 chain** | EQUIVALENT | [G]/[J] 已注册与未注册路径均 204 |
| 全局 | HTTP 端口/地址 | `Host: 0.0.0.0` `Port: 8889` | `server.port: ${ARTICLE_API_PORT:8889}` `address: 0.0.0.0` | EQUIVALENT | [S] yaml:2-3 |
| 全局 | 依赖发现 | etcd flat key | Nacos discovery | IMPLEMENTATION DIFFERENCE | [S] |

## B. 路由与框架层（🔴 漂移最集中）

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| 路由 | **方法不匹配的状态码** | **404** | **405** | **SEMANTIC MISMATCH** | [G] `MISMATCH POST /v1/article/1 -> 404`；[J] `-> 405 allow="PUT, DELETE, GET"`；[S] `WithCors` 装 `cors.NotAllowedHandler`（handlers.go:47 返回 404） |
| 路由 | 方法不匹配的 body | **空** | **空** | EQUIVALENT | [G]/[J] body="" |
| 路由 | 方法不匹配的 Content-Type | **无** | **无** | EQUIVALENT | [G]/[J] ct="" |
| 路由 | 方法不匹配的 `Allow` 头 | **无** | `PUT, DELETE, GET` | **SEMANTIC MISMATCH** | [G] allow=""；[J] allow 非空 |
| 路由 | 方法不匹配是否进鉴权链 | **不进** | **不进** | EQUIVALENT | [S] engine.go:101-116；[J] `handler instanceof HandlerMethod` 守卫 |
| 路由 | 未注册路径状态码 | 404 | 404 | EQUIVALENT | [G]/[J] |
| 路由 | 未注册路径 body | `404 page not found\n`（19 B） | `404 page not found\n`（19 B，xxd 实测） | EQUIVALENT | [G]/[J] |
| 路由 | 未注册路径 `nosniff` | 有 | 有 | EQUIVALENT | [G]/[J] |
| 路由 | **未注册路径 Content-Type** | `text/plain; charset=utf-8`（**有空格**） | `text/plain;charset=utf-8`（**无空格**） | **OBSERVABLE DIFFERENCE** | [G]/[J] 原样头对比 |
| 路由 | **尾斜杠路径** `/v1/articles/` | **200**（`path.Clean` 去掉） | **404** | **SEMANTIC MISMATCH** | [G] `trailing slash -> 200`；[J] `-> 404` |
| 路由 | **重复斜杠路径** | **200** | **404** | **SEMANTIC MISMATCH** | [G] `double slash path -> 200`；[J] `-> 404` |
| 路由 | 尾斜杠 + 受保护路由 `PUT /v1/article/1/` | **401**（Clean 后命中受保护路由） | **404** | **SEMANTIC MISMATCH** | [G] `trailing slash on protected -> 401` |

## C. 鉴权（AuthN / AuthZ）

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| AuthN | 受保护路由集合 | 4 条（POST/PUT/DELETE `/v1/article*` + GET `/v1/me/article/{id}`） | 4 条（method + PathPattern 双匹配） | EQUIVALENT | [S] routes.go:19-45；[T] `authBoundarySnapshot` |
| AuthN | 公开路由集合 | 3 条（GET `/v1/article/{id}`、GET `/v1/articles`、POST `/v1/upload`） | 3 条 | EQUIVALENT | [S]/[T] |
| AuthN | 「/v1/articles」不被「/v1/article」前缀误伤 | 逐段精确匹配 | PathPattern 精确匹配 | EQUIVALENT | [G]/[J]；[T] `pluralPathIsNotProtectedBySingularRule` |
| AuthN | 无 token 响应 | **401 + 空 body + 无 Content-Type** | **401 + 空 body + 无 Content-Type** | EQUIVALENT | [G]/[J]；[J] 用 `ResponseEntity<Void>` 避免容器补 CT |
| AuthN | `Bearer ` 前缀大小写敏感 | **不敏感** | 不敏感 | EQUIVALENT | [G] `lowercase bearer -> ACCEPTED`；[J] `create lowercase bearer -> 200` |
| AuthN | `Bearer ` 前缀可省略 | **可省略**（裸 token 直接当 token） | 可省略 | EQUIVALENT | [G] `raw <tok> -> ACCEPTED`；[J] `create raw token -> 200` |
| AuthN | `Basic <token>` | **401** | 未单独实测 | UNKNOWN | [G] REJECTED |
| AuthN | claim 数值类型 | `json.Number`（`jwt.WithJSONNumber()`） | jjwt 的解析类型，**不转换** | EQUIVALENT | [S] tokenparser.go:123；[G] `userId=1001 (type json.Number)`；[J] 数字与字符串 claim 均进业务 |
| AuthZ | claim 取值校验的**位置** | 在 **logic**（`extractCurrentUserID`） | 在 **support**（`ArticleApiSupport.userId`），拦截器**不**校验 | EQUIVALENT | [S] helper.go:17-39；[J] 实测无 `userId` ⇒ 200 + 1005 |
| AuthZ | 「有 token 无 userId」的响应 | **200 + `{code:1005}`** | **200 + `{code:1005}`** | EQUIVALENT | [G] 源码；[J] 实测 |
| AuthZ | `userId = "<nil>"` 显式排除 | 是（`helper.go:33`） | 是（`resolveUserIdClaim`） | EQUIVALENT | [S] 两侧均显式 |

## D. 请求保护层

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| MaxBytes | 生效值 | **10485760**（route 级与 conf 级同值） | **10485760** | EQUIVALENT | [S] engine.go:173-179；[T] `maxBytesIsTenMiB` |
| MaxBytes | 适用范围 | **全部 7 个端点** | 全局 Filter | EQUIVALENT | [G]/[J] protected+public+upload 三条全 413 |
| MaxBytes | 判据 | `r.ContentLength > n` | `getContentLengthLong() > n` | EQUIVALENT | [S] maxbyteshandler.go:19 |
| MaxBytes | chunked 绕过 | `-1 > n` 为 false ⇒ 放行 | 同 | EQUIVALENT（源码级，未实测 chunked） | [S] |
| MaxBytes | 响应形状 | 裸 413，无 body 无 CT | 裸 413，`Content-Length: 0`，无 CT | EQUIVALENT | [G]/[J] |
| MaxBytes | **与 401 的先后** | **413 压过 401** | **413 压过 401** | EQUIVALENT | [G] 无 token + 20MB ⇒ 413；[J] `413 protected (no token) -> 413` |
| MaxConns | 值 | 10000（tag 默认） | 10000 | EQUIVALENT | [S] config.go:52 |
| MaxConns | 与线程池的关系 | goroutine 无上限，闸门即瓶颈 | Tomcat 200 线程 ⇒ **排队先于拒绝** | OBSERVABLE DIFFERENCE（过载时） | [S] 推断，未压测 |
| Breaker | 是否开 | 开（tag `default=true`） | 开 | EQUIVALENT | [S] |
| Breaker | 成功判据 | **`cw.Code < 500 ⇒ Accept()`** ⇒ 业务错误（200）**不算失败** | 同实现 | EQUIVALENT | [S] breakerhandler.go:33 |
| Breaker | 每实例粒度 | 每 `method+path` 一个 | 每路由一个 | EQUIVALENT | [S] |
| Shedding | CPU 阈值 | 900（千分比 90%） | 900（`cpu-source: SYSTEM`） | EQUIVALENT | [S] config.go:56 |
| Recover | panic | 裸 500 | `@ExceptionHandler(Exception)` ⇒ 裸 500 | EQUIVALENT | [S] recoverhandler.go:17 |

## E. 超时 / 重试

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| 超时 | 生效值 | **70s**（yaml `Timeout: 70000`） | **70s**（`observability.http-timeout: 70s`） | EQUIVALENT | [S] yaml:4 |
| 超时 | 原生超时是否并存 | `DisableNativeRestTimeout` 关掉 | 无需动作 | EQUIVALENT | [S] |
| 超时 | scope | **整个 operation** | 整个 operation | EQUIVALENT | [S] observability/http.go:53 |
| 超时 | 触发后响应 | **503 + `Request Timeout\n`** | 503 + `Request Timeout` | EQUIVALENT（源码级） | [S]；**未实测** |
| 超时 | 是否 kill handler | **不 kill**，但 ctx 到期 ⇒ 下游 I/O 中止 | 依赖 `Future.cancel` | EQUIVALENT（源码级） | [S]；**未实测** |
| 超时 | 向下游传播 | `r.WithContext(ctx)` ⇒ 全部 gRPC 继承 | `GoStyleRequestDeadline` + `withDeadlineAfter` | EQUIVALENT | [S] |
| 超时 | `CreateArticle` 显式 70s | 有（`zrpc.WithCallTimeout`） | **无**（靠 HTTP 预算） | EQUIVALENT（本部署下同值） | [S] 两处都 70s |
| 超时 | `ReadTimeout`/`WriteTimeout` | **不设置**（`hasTimeout()` 为 false） | Tomcat 默认 | OBSERVABLE DIFFERENCE | [S] engine.go:368-382；**未实测** |
| 超时 | gRPC 超时码 | `DeadlineExceeded` | `DeadlineExceeded`（正常路径）/ `CANCELLED`（取消路径） | UNKNOWN | [S] `GoStyleRequestDeadline` javadoc 自陈此差异；**未实测** |
| Retry | HTTP 层 retry | 无 | 无 | EQUIVALENT | [S] |
| Retry | gRPC retry | **无**（`zrpc.RpcClientConf` 无 `Retries`） | 无 | EQUIVALENT | [S] |
| Retry | gRPC client breaker | 开，失败名单 6 个 code | 开，同名单 | EQUIVALENT | [S] |

## F. 解析层（输入解释）

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| 解析 | 解析顺序 | path → form → header → JSON | path/query 由注解绑定，JSON 由 converter | IMPLEMENTATION DIFFERENCE | [S] requests.go:49-77 |
| 解析 | 读 body 的前提 | `CL>0 && CT contains "application/json"` | 同（`goReadsBody`） | EQUIVALENT | [S]；[G]/[J] `CT=text/plain` ⇒ 两侧都 `field "title" is not set` |
| 解析 | 不满足前提时的行为 | **丢弃整个 body**，当空对象 | `read` 返回 `null`，零值 DTO | EQUIVALENT | [G]/[J] |
| 解析 | 必填缺席 | `field "x" is not set` | 同文案 | EQUIVALENT | [G]；[J] 实测 |
| 解析 | 必填显式 null | `field "x" mustn't be nil` | 同文案 | EQUIVALENT | [G]；[J] 实测 |
| 解析 | `,optional` 接受显式 null | **接受** | 接受 | EQUIVALENT | [G] `brief:null → nil` / `status:null → nil`；[J] 两项均 200 |
| 解析 | `,omitempty` 不等于 optional | 必填（`parseOption` 不认） | N/A（Java 无此 tag） | EQUIVALENT（本文无此 tag） | [S] utils.go:372-385 |
| 解析 | 类型不符的状态码 | 400 text/plain | 400 text/plain | EQUIVALENT | [G]/[J] |
| 解析 | **类型不符的 body 文案** | `type mismatch for field "title", expect "string", actual "number"` | `type mismatch for field "title"` | **OBSERVABLE DIFFERENCE** | [G]；[J] 实测 |
| 解析 | String 字段收 JSON 数字 | 拒绝（type mismatch） | 拒绝（`GoStyleCoercion` 规则 1） | EQUIVALENT | [G] `title=123`；[J] `title=123 → 400` |
| 解析 | int 字段收 JSON 字符串 | 拒绝（type mismatch） | 拒绝（`ArticleGoStyleCoercion` 补强） | EQUIVALENT | [G] `status="2"`；[J] `status="2" → 400` |
| 解析 | 空串作为合法值 | 接受 | 接受 | EQUIVALENT | [G] `title="" → nil` |
| 解析 | 未知字段 | 忽略 | 忽略 | EQUIVALENT | [G] `zzz:1 → nil` |
| 解析 | **JSON body > 8 MiB** | **截断 ⇒ 400 text/plain** | **完整解析 ⇒ 进业务** | **SEMANTIC MISMATCH** | [G] `7MiB→nil` / `9MiB→error(截断到 8388741)`；[J] 9 MiB → 200 |
| query | 空值处理 | **整体丢弃**（= 缺席） | 丢弃（`raw()` 返回 null） | EQUIVALENT | [S] util.go:44-45；[G]/[J] `?page=&sort_by=` 均 200 |
| query | bool 真值表 | `1/true/0/false`（EqualFold） | 同 | EQUIVALENT | [G]/[J] `desc=1`→200；`desc=yes`→400 |
| query | 非法 bool 文案 | `type mismatch for field "desc"` | 同文案 | EQUIVALENT | [G]/[J] |
| query | int 进制 | base-10（`?page=010` → **10**） | base-10（`Integer.parseInt(v,10)`） | EQUIVALENT | [S] utils.go:122；[J] `?page=010 → 200` |
| query | 非法 int | 400 | 400 | EQUIVALENT | [G]/[J] |
| query | 默认值触发条件 | 仅 key **缺席** | 仅 `raw()==null` | EQUIVALENT | [S] unmarshaler.go:919 |
| query | `?page=abc` 是否回落默认 | **不回落，400** | 不回落，400 | EQUIVALENT | [S]/[G]/[J] |
| query | 多值参数（`?page=1&page=2`） | `WithFromArray` ⇒ 取第 1 个 | Spring `@RequestParam String` ⇒ 取第 1 个 | EQUIVALENT | [S] requests.go:31；未实测 |
| query | 数组记法 `?ids[]=` | 支持（`util.go:57-59`） | Spring 不支持 `[]` 后缀 | UNKNOWN | 未实测 |

## G. 业务语义（逐端点）

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| OP-1 Create | 必填字段 | `title` / `content` | 同 | EQUIVALENT | [G]/[J] |
| OP-1 Create | `brief`/`cover_image_url` 下发方式 | **恒 `&req.X`（哪怕空串）** ⇒ proto `has=true` | **恒 `setX("")`** | EQUIVALENT | [S] create_article_logic.go:47,49；[S] ArticleCreateService |
| OP-1 Create | `secondary_tags` nil vs 空 | `req.SecondaryTags` 原样 | `addAllSecondaryTags`（空 list 是 no-op） | EQUIVALENT | [S] |
| OP-1 Create | 成功 | `{code:200,data:{article_id}}` | 同 | EQUIVALENT | [S]；[T] |
| OP-1 Create | `AlreadyExists` | 1001，`data:null` | 1001，`data:null` | EQUIVALENT | [S]；[T] 日志 `code=1001` |
| OP-1 Create | **`FailedPrecondition`** | **1004 + `data:{article_id}`** | **1004 + `data:{article_id}`** | EQUIVALENT | [S] create_article_logic.go:62-65；[T] 日志 `CreateFailure code=1004 articleId=1792670400123` |
| OP-1 Create | **`DeadlineExceeded`** | **1003 + `data:{article_id}`** | **1003 + `data:{article_id}`** | EQUIVALENT | [S] :66-69；[T] |
| OP-1 Create | `Internal` | 5001，data null | 同 | EQUIVALENT | [S] |
| OP-1 Create | 其它 | 1015，data null | 同 | EQUIVALENT | [G]/[J] 实测全落 1015 |
| OP-1 Create | article_id 提取规则 | `LastIndex(":")` + 两个边界 | 逐字复刻 | EQUIVALENT | [S] 两处 |
| OP-1 Create | rpc 返回 nil | **panic ⇒ recover ⇒ 裸 500** | 返回 `article_id:""` + 200 | OBSERVABLE DIFFERENCE（理论不可达） | [S]；Java 注释显式承认 |
| OP-1 Create | HTTP 超时 | 显式 70s | 靠 HTTP 预算 | EQUIVALENT（本部署） | [S] |
| OP-2 Update | 鉴权读 | `GetArticle(incr_view=false, requester_id=me)` | 同 | EQUIVALENT | [S] |
| OP-2 Update | 归属检查 | `AuthorId != me ⇒ 1006` | 同 | EQUIVALENT | [S] |
| OP-2 Update | 可选字段下发 | **仅非空串才 set** | 同（`setIfNotEmpty`） | EQUIVALENT | [S] |
| OP-2 Update | `status` 下发条件 | `!= 0` **且** `Enum()` 非 nil | `!= 0` **且** `forNumber` 非 null | EQUIVALENT | [S] |
| OP-2 Update | `secondary_tags` | 恒下发 | 恒下发 | EQUIVALENT | [S] |
| OP-2 Update | 写分支 code 数 | **3 个**（无 `PERMISSION_DENIED`） | 3 个 | EQUIVALENT | [S] |
| OP-3 Delete | 鉴权读 + 写 | 两次 RPC | 两次 RPC | EQUIVALENT | [S] |
| OP-3 Delete | 写分支 code 数 | **4 个**（含 `PERMISSION_DENIED`→1006） | 4 个 | EQUIVALENT | [S]；⚠️ 与 OP-2 的不对称被正确保留 |
| OP-3 Delete | 成功 `success` 值 | **恒 true** | 恒 true | EQUIVALENT | [S] |
| OP-4 Author | `public_only` | **false** | false | EQUIVALENT | [S]；[T] Go 断言 `!getCalls[3].PublicOnly` |
| OP-4 Author | `incr_view` | **恒 false** | 恒 false（controller 不声明该参数） | EQUIVALENT | [S] |
| OP-4 Author | 非 PUBLISHED | **不拦**（作者可看草稿） | 不拦 | EQUIVALENT | [S] |
| OP-4 Author | 归属不符 | 1006 | 1006 | EQUIVALENT | [S]；[T] 日志 |
| OP-4 Author | `ext_info` 键序 | 字典序 | 非字典序 | OBSERVABLE DIFFERENCE | DRIFT-007 |
| OP-5 Public | `public_only` | **恒 true** | true | EQUIVALENT | [S]；[T] Go 断言 `getCalls[0].PublicOnly` |
| OP-5 Public | `requester_id` | **不设**（空串） | 不设 | EQUIVALENT | [S] |
| OP-5 Public | `incr_view` 来自 query | 是 | 是 | EQUIVALENT | [S] |
| OP-5 Public | 非 PUBLISHED | **1002**（安全闸门） | 1002 | EQUIVALENT | [S]；[T] Go `stale-backend` 断言 1002 且不含草稿正文 |
| OP-5 Public | RPC `NOT_FOUND` | 1002 | 1002 | EQUIVALENT | [S] |
| OP-5 Public | RPC `PERMISSION_DENIED` | **落 default ⇒ 1015** | 落 default ⇒ 1015 | EQUIVALENT | [S] 两处 switch 均只有 3 个分支 |
| OP-5 Public | `res==nil` 映射 | **1002** | 1002 | EQUIVALENT | [T] `publicArticleDetailIsNotProtected` |
| OP-6 List | 4 筛选字段下发 | **恒 `&req.X`（空串也 set）** | 恒 set | EQUIVALENT | [S] list_articles_logic.go:38-41 |
| OP-6 List | `public_only` | 恒 true | true | EQUIVALENT | [S] |
| OP-6 List | **任一行非 PUBLISHED** | **整条 5001（`return`，已 append 的丢弃）** | 同 | EQUIVALENT | [S] :63-68；[T] Go `old` 用例断言 5001 且不含 `draft` |
| OP-6 List | `res==nil` 映射 | **5001**（与 OP-5 的 1002 不同） | 5001 | EQUIVALENT | [S] :58-60 |
| OP-6 List | RPC 失败分支数 | **2 个**（`NOT_FOUND` 也是 1015） | 2 个 | EQUIVALENT | [S] |
| OP-6 List | 昵称去重 | **请求内 map** | 请求内 `LinkedHashMap` | EQUIVALENT | [S] helper.go:53,60-67 |
| OP-6 List | 空列表 | `articles` 为 nil ⇒ `"articles":null` | `cloneStringList(空) → null` | EQUIVALENT | [S] |
| 昵称 | 4 条失败分支 | 全部 `fallbackAuthorName`，不冒泡 | 同 | EQUIVALENT | [S] helper.go:71-96；[S] ArticleAuthorResolver |
| 昵称 | `uid<=0` 也回落 | 是 | 是（`"0"`/`"-5"` 都能解析成功，靠 `<=0` 兜住） | EQUIVALENT | [S] |
| 昵称 | 日志失败不得改变响应 | Go logger 不抛 | Java **额外包了 try/catch** | EQUIVALENT（方向一致） | [S] |
| OP-7 Upload | 字段名 | `image` | `image` | EQUIVALENT | [S] |
| OP-7 Upload | filename 处理 | **不清洗**（`header.Filename`） | `getOriginalFilename()` 不清洗 | EQUIVALENT | [S] |
| OP-7 Upload | **成功响应形状** | **裸 `{"image_url":…}`**（`OkJsonCtx`） | **裸**（`ResponseEntity` 绕过 advice） | EQUIVALENT | [G]（`httpx.OkJsonCtx` 无 okHandler）；[J] 实测 |
| OP-7 Upload | 缺 `image` 字段 | **400 text/plain** | 400 text/plain | EQUIVALENT | [G] `http: no such file`；[J] 同文案 |
| OP-7 Upload | 下游 RPC 失败 | **400 text/plain**（5004 只进日志） | 400 text/plain | EQUIVALENT | [S] upload_image_logic.go:45-48；[J] 实测 |
| OP-7 Upload | **非 multipart 请求** | **400 text/plain** | **500 裸 body** | **SEMANTIC MISMATCH** | [G] `err=request Content-Type isn't multipart/form-data`（⇒ `ErrorCtx` ⇒ 400）；[J] 实测 `-> 500` |
| OP-7 Upload | chunked 超 10 MiB | 放行 ⇒ `FormFile` 失败 ⇒ **400** | 放行 ⇒ Tomcat `max-request-size` ⇒ `MaxUploadSizeExceededException` ⇒ 映射 **400** | EQUIVALENT（源码级推断） | [S]；未实测 chunked |

## H. 序列化

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| 信封 | 字段 | `{code,msg,data}`，无 `omitempty` ⇒ `data` 恒出现 | 同（`Response` 无 `@JsonInclude`） | EQUIVALENT | [S] |
| 信封 | HTTP 状态码 | 恒 **200**（含所有业务失败） | 恒 200 | EQUIVALENT | [J] 实测 1002/1005/1006/5001/1015 全是 200 |
| 信封 | msg 取值 | `errmsg.GetErrMsg(code)` | `ErrorCode.getMsg(code)` | EQUIVALENT | [T] `ArticleErrorCodeContractTest` |
| `Article` | 字段名 | snake_case | 显式 `@JsonProperty`（仓库无命名策略） | EQUIVALENT | [S] |
| `Article` | `author_name` 空串 | **key 消失**（`,omitempty`） | `@JsonInclude(NON_EMPTY)` | EQUIVALENT | [S] |
| `Article` | `secondary_tags` 空 | **`null`** | `null`（`cloneStringList` 空→null） | EQUIVALENT | [S] |
| `Article` | `ext_info` 空 map | **`null`**（`cloneStringMap` 空→nil） | `null` | EQUIVALENT | [S] |
| `Article` | **`ext_info` 键序** | **字典序**（`encoding/json` 排序 map key） | **`LinkedHashMap` 保 proto 迭代序（不排序）** | **OBSERVABLE DIFFERENCE** | [S]；未做活体对比 |
| `Article` | `status` | `int32(枚举数值)` | `getStatusValue()` | EQUIVALENT | [S] |
| `Article` | `content` 来源 | `MarkdownContent` | `getMarkdownContent()` | EQUIVALENT | [S] |
| `Article` | HTML 字符转义 | `SetEscapeHTML(false)` ⇒ 不转义 | Jackson 默认不转义 | EQUIVALENT | [S] jsonx.go:18 |
| 成功 CT | | `application/json` | `application/json` | EQUIVALENT | [G]/[J] |
| 400 CT | | `text/plain; charset=utf-8` | `text/plain;charset=utf-8` | OBSERVABLE DIFFERENCE | DRIFT-005 |
| 400 body | | 文案 + `\n` | 文案 + `\n` | EQUIVALENT | [S] `GoStylePlainTextErrors` |
| 400 body 文案 | | go-zero 原生英文 | Java 侧对齐了 `is not set` / `mustn't be nil` / `type mismatch`，但**整数解析**用自定义文案 | EQUIVALENT（判据一致） | [G]/[J] |

## I. 并发 / 事务 / 生命周期

| Operation | Semantic Dimension | Go | Java | Result | Evidence |
|---|---|---|---|---|---|
| 并发 | 组件内共享可变状态 | 无 | 无（仅 ThreadLocal，有 begin/clear 配对） | EQUIVALENT | [S] |
| 并发 | 幂等性 | 无保护 | 无保护 | EQUIVALENT | [S] |
| 并发 | OP-2/OP-3 的读-写原子性 | 非原子（两个 RPC） | 非原子 | EQUIVALENT | [S] |
| 并发 | 过载时的客户端可观察行为 | 立刻 503 拒绝 | **先排队**（Tomcat 200 线程）再拒绝 | OBSERVABLE DIFFERENCE（推断，未压测） | [S] |
| 事务 | 组件内事务边界 | **无** | **无** | EQUIVALENT | [S] |
| 生命周期 | 启动期硬依赖失败形态 | `zrpc.MustNewClient` **panic** | Nacos 注册抛异常 ⇒ context 启动失败 | UNKNOWN（Java 已实测，Go 未实测） | [J] `ErrCode:-401` |
| 生命周期 | 优雅关闭 | `defer server.Stop()` + go-zero 默认 | Spring context 关闭链 | EQUIVALENT | [S] |
| 生命周期 | 观测端点 | `:16066/metrics`（框架内建） | `:8889/actuator/{health,info,prometheus}` | OBSERVABLE DIFFERENCE（运维配置） | [S] |
| 生命周期 | `AccessExpire` | 配置了但**不被读取** | 配置了但**不被读取** | EQUIVALENT | [S] 两侧均未消费 |

---

## 汇总统计

| Result | 条数 |
|---|---|
| EQUIVALENT | 116 |
| IMPLEMENTATION DIFFERENCE | 3 |
| OBSERVABLE DIFFERENCE | 10 |
| **SEMANTIC MISMATCH** | **7**（DRIFT-001 的 4 个维度 + DRIFT-002 + DRIFT-003 + DRIFT-004 的 2 个路径） |
| UNKNOWN | 5 |
