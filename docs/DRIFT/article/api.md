# DRIFT 台账 · `service/article/article-api`

> 本文件**两代内容并存**：§0 是 2026-10-23 早先那次「文件计数实测」，原文逐字保留不删改；
> §1 起是 2026-10-23 本轮 **Go ↔ RAG 语义对照**（首次）的完整台账（spec §31-b 结构）。
> 编号空间统一为 `DRIFT-ARTICLE-API-0xx`；RAG 源码注释里遗留的 `DRIFT-001…007` 是上游实现期的
> 内部编号，**未被本轮验证**，逐条对应关系见 §7.5。

---

## 0. 前置核对（2026-10-23 早先实测，原文保留）

> ⚠️ **本文件当前无搬移内容**。AGENTS.md §0 里关于 article-api 的记录只有一句否定式断言
> （「`article-api`（29 个文件）仍全是 4 行空骨架」），该断言**已被 2026-10-23 00:1x 的实测证伪**。

### 2026-10-23 实测核对（真实执行产出，非推断）

依据 AGENTS.md §6.2 的阅读约定第 1 条 —— 「每行必须带最后核对日期，且该日期必须由一次真实执行产出」：

| 项 | 实测值 | 与 AGENTS.md §0 的冲突 |
|---|---|---|
| `src/main/java` 文件数 | **36** | §0 记「29 个文件」 |
| ≤15 行的空骨架数 | **0** | §0 记「仍全是 4 行空骨架」 |
| 主源码总行数 | **4,275** | — |
| 最新写入时间 | **2026-10-22 23:56** | 晚于 AGENTS.md 最后编辑（23:40） |

已实现的实体（节选）：`GlobalExceptionHandler`(339) / `ArticleApiSupport`(304) / `Article`(258) /
`ArticleController`(231) / `JwtAuthInterceptor`(228) / `ArticleUpdateService`(189) /
`ArticleCreateService`(187) / `CreateArticleReq`(151) / `GoFormValues`(148) /
`ArticleListService`(140) / `ArticleGoStyleCorsFilter`(137)。

### 结论

- **AGENTS.md §0「article 域整体仍未完成」的计数已过期。**
- article-api 的源码已落地，但**从未做过 Go ↔ RAG 对照**，也**没有对应的 DRIFT 台账内容**。
- ⇒ 本文件是**空白起点**，不是「已核对无漂移」。下一个接手 article-api 的 agent 应以此为第一件事。

### 尚未处理

- Go `service/article/article/api` ↔ RAG `article-api` 逐字段对照（**从未做过**）
- 本模块的 `HUMAN_ONLY.MD`（全仓仅 4 个 users 域有）
- 根目录 `HUMAN_ONLY/` 下无 article 相关包指南

### 0.1 2026-10-23 复核：原文的计数已被本轮实测**再次修正**（原文保留，不抹除）

> 依据：任务简报要求「核一下实际 `.java` 文件数与原文记的 36 是否一致」。
> 命令：`find service/article/article-api/src/main/java -name "*.java" | wc -l`（本会话实跑）

| 项 | §0 原文（00:1x 实测） | **本轮复核实测（02:0x）** | 结论 |
|---|---|---|---|
| `src/main/java` `.java` 文件数 | 36 | **38** | ⬆ 已被推翻，见 DRIFT-ARTICLE-API-008 |
| `src/test/java` `.java` 文件数 | 未记 | **2** | 新增信息 |
| 主源码总行数 | 4,275 | **4,666** | ⬆ +391 |
| `src/main/java` 最新写入 | 2026-10-22 23:56 | **2026-10-23**（`advice/`、`config/`、`service/`、`dto/` 共 7 个文件当日改过） | ⬆ |

**不改写 §0 原文**：那是一次真实执行的结果，抹掉它等于抹掉证据。正确做法是追加本节。
**新增 2 个文件的实际来源**（`ArticleApiSupport` 与 `GoStyleStringFields` 的 javadoc 里留有
「第一次实现漏了这条 / 已被实测推翻」的自述）：

| 新增文件 | 首次写入日期 | 说明 |
|---|---|---|
| `dto/GoStyleStringFields.java` | 2026-10-23 | 修 `{"title":123}` 被接受的问题（注释自述「第一次实现漏了这条」） |
| `config/GoStylePathNormalizingFilter.java` | 2026-10-23 | 修 `path.Clean` 归一（注释标 DRIFT-004 已修） |
| `config/GoStyleJsonBodyLimitFilter.java` | 2026-10-23 | 修 8 MiB JSON body 上限（注释标 DRIFT-002 已修） |

⇒ **教训**：§0 那次「实测」在**同一天内**就已经过期。
计数类指标必须带**时间戳 + 该时间之后是否还有写入**，否则会被下一个会话当成现状。

---

## 1. Component Contract（§4 + §5）

| 项 | 内容 | 证据 |
|---|---|---|
| **Component** | `article-api` —— 文章域的 HTTP 网关 | Go `service/article/api/article.go` |
| **Purpose** | 把 HTTP 请求（认证 / 入参解释 / 错误映射 / 响应成形）翻译成对 `article-rpc` 与 `user-rpc` 的 gRPC 调用 | — |
| **Public Operations** | 7 个端点（见下表） | Go `internal/handler/routes.go:19-73` |
| **Inputs** | path 段 / query string / JSON body / multipart file / `Authorization: Bearer` | — |
| **Outputs** | 6 个端点 = `{"code","msg","data"}` 信封（HTTP 200）；`POST /v1/upload` = **裸** `{"image_url":…}`；失败路径分三种（400 text/plain / 401 空 body / 裸 4xx-5xx） | Go 6 个 handler + `upload_image_handler.go:28` |
| **Dependencies** | `article-rpc`（6 个方法）、`user-rpc`（`GetUser`，仅用于昵称补全）、JWT secret、注册中心（Go=etcd / RAG=Nacos）、PG/MinIO **间接**（经 rpc） | Go `internal/svc/service_context.go:24-26` |
| **State** | **无组件级可变状态**。请求内状态（去重 map、body buffer）全在栈上 | `helper.go:53`（局部 map） |
| **Side Effects** | 仅转发：`incr_view=true` 会让 rpc 侧 `+1` 阅读量 | `get_article_logic.go:38` |
| **Failure Model** | 三层：① 保护层裸状态码（413/503/499）② 框架层（401 空 body / 404 两种 / 裸 500）③ 业务层（HTTP 200 + 非 200 业务码，或 400 text/plain） | 见 §5 |

### 1.1 路由与鉴权契约（L1，逐条对齐）

| # | Method | Path | Go 段 | JWT | Go 证据 | RAG 证据 | Result |
|---|---|---|---|---|---|---|---|
| 1 | POST | `/v1/article` | 第一段 | ✅ | `routes.go:22-25` | `ArticleController.java:122` | EQUIVALENT |
| 2 | PUT | `/v1/article/:id` | 第一段 | ✅ | `routes.go:27-30` | `ArticleController.java:136` | EQUIVALENT |
| 3 | DELETE | `/v1/article/:id` | 第一段 | ✅ | `routes.go:32-35` | `ArticleController.java:151` | EQUIVALENT |
| 4 | GET | `/v1/me/article/:id` | 第一段 | ✅ | `routes.go:37-40` | `ArticleController.java:166` | EQUIVALENT |
| 5 | GET | `/v1/article/:id` | 第二段 | ❌ | `routes.go:50-53` | `ArticleController.java:179` | EQUIVALENT |
| 6 | GET | `/v1/articles` | 第二段 | ❌ | `routes.go:55-58` | `ArticleController.java:193` | EQUIVALENT |
| 7 | POST | `/v1/upload` | 第三段 | ❌ **公开** | `routes.go:66-69` | `ArticleController.java:227` | EQUIVALENT |

> ⚠️ **第 7 条是本组件最反直觉的 L1 契约**：`POST /v1/upload` **不带 JWT**。
> 「上传图片要登录」凭直觉一定会加，但 Go 侧第三段 `AddRoutes` 没有 `rest.WithJwt`。
> RAG 侧靠 `JwtAuthInterceptor.PROTECTED_ROUTES`（4 条，`JwtAuthInterceptor.java:117-121`）
> 精确排除它 —— 这张表必须**同时带 method**，否则 `/v1/article/{id}` 会把公开的 GET 一起拦掉。

### 1.2 「briefing 提到的黑名单中间件」在本组件**不存在**（已核实，非遗漏）

`grep -rn "Blacklist|blacklist" service/article/api/` → **0 命中**。
Go 侧 `rest.WithJwt`（`rest/server.go:185`）→ `engine.go:74` `handler.Authorize(secret, ...)`
→ `rest/handler/authhandler.go:45-84`，该链里**没有**黑名单查表。
⇒ Go / RAG 两侧**都没有**黑名单 ⇒ EQUIVALENT（不是漏实现）。

### 1.3 `SecurityRpc`：Go 配了但**零消费者**，RAG 刻意不建 client —— 判定 EQUIVALENT

Go 侧三处命中全部是「定义它」：`api/etc/article-api.yaml:14`、`api/internal/config/config.go:18`、
`api/internal/svc/service_context.go:17,25`；`grep -rn "SecurityRpc|ImageSecurity" api/` 在
`internal/logic/` 与 `internal/handler/` 下**零命中**。
RAG `ArticleApiProperties` 类注释声称「刻意不建这个 client」—— **核对成立**。

但「不建」是否少了一个**启动期**行为？逐点核对：

- Go `service_context.go:25` 会执行 `zrpc.MustNewClient(c.SecurityRpcConf)`。
- `RpcClientConf.NonBlock bool \`json:",default=true"\``（`zrpc/config.go:30`，
  `article-api.yaml` 未配 ⇒ 走 default）⇒ 消费点 `zrpc/internal/client.go:78-80`
  `if !cliOpts.NonBlock { options = append(options, grpc.WithBlock()) }` ⇒ **不加 `WithBlock`**
  ⇒ 非阻塞 dial，`security.rpc` 解析不到也**不 panic**。
- `BuildTarget()` 需要 `Etcd.Key`，yaml 已给 `security.rpc` ⇒ 不返回 error。

⇒ **启动行为与响应行为均不可观测** ⇒ EQUIVALENT。
**但这是「两条错误路径恰好同值」，不是设计对齐** —— 若日后 Go 侧给 `SecurityRpcConf` 显式写
`NonBlock: false`，这条结论立刻失效。已记入 §9 UNRESOLVED-007。

---

## 2. GO-SEMANTIC-MODEL（§7 / §33）

### OP-01 `POST /v1/article` → `CreateArticle`

| 字段 | 内容 |
|---|---|
| Input | JSON body（`Content-Length>0` 且 `Content-Type` 含 `application/json` 才读，否则**丢弃 body**）；path 无；query 无 |
| Precondition | JWT 通过（否则 401 空 body）；`userId` claim 可解析（否则 **200 + 1005**） |
| Trigger | `POST /v1/article` |
| Processing | ①`httpx.Parse`（path→form→header→json 四通道，**任一失败即 400**）②`extractCurrentUserID` ③调 `article-rpc.CreateArticle` ④`status.FromError` 五分支映射 |
| External Calls | `article-rpc.CreateArticle`，**显式 `zrpc.WithCallTimeout(70*time.Second)`** |
| External Call Parameters | `Title`(必填) `Brief`(&恒下发) `MarkdownContent`(必填) `CoverImageUrl`(&恒下发) `ManualTypeTag` `SecondaryTags` `AuthorId` |
| Result | `{"code","msg","data":{"article_id"}}`，HTTP **恒 200** |
| State Change | 无本地状态；rpc 侧建行 |
| Side Effects | 触发 rpc 侧发布流水线（可能异步） |
| Error | 见下方五分支表 |
| Timeout | HTTP 70s（唯一执行者）+ 本调用 70s 独立预算 |
| Retry | **无**（`zrpc.RpcClientConf` 无 `Retries` 字段，`zrpc/config.go:24-35`） |
| Concurrency | 每请求一 goroutine，无共享写状态 |
| Ordering | 无 |
| Observable | 信封；失败时 `data` 可非空（1003/1004 两条） |
| Evidence | `create_article_handler.go:17-33`、`create_article_logic.go:38-92` |

**Go 侧五分支（`create_article_logic.go:55-75`）—— `data` 的有无逐条不同：**

| `status.Code()` | 业务码 | `data` | 备注 |
|---|---|---|---|
| `AlreadyExists` | 1001 | **null** | |
| `FailedPrecondition` | 1004 | **`{"article_id":…}`** | id 从 status **文案**里抠 |
| `DeadlineExceeded` | 1003 | **`{"article_id":…}`** | 同上 |
| `Internal` | 5001 | null | |
| default | 1015 | null | |

`extractArticleIDFromStatusMessage`（`create_article_logic.go:82-92`）：
取**最后一个** `:` 之后的内容并 `TrimSpace`；无 `:` ⇒ `""`；`:` 在末尾 ⇒ `""`。

### OP-02 `PUT /v1/article/:id` → `UpdateArticle`

| 字段 | 内容 |
|---|---|
| Input | path `:id` + 7 个全 `,optional` 的 JSON 字段（**空 body 合法，等于「什么都不改」**） |
| Precondition | JWT + userId |
| Processing | ①`extractCurrentUserID` ②**先读** `GetArticle{IncrView:false, RequesterId:uid}` ③`article==nil` ⇒ 1002 ④`AuthorId != uid` ⇒ 1006 ⑤组装：5 个字符串**非空才下发**，`SecondaryTags` **恒下发**，`Status != 0` 且是合法枚举才下发 ⑥`UpdateArticle` |
| External Calls | `article-rpc.GetArticle` → `article-rpc.UpdateArticle` |
| Result | `{"code":200,"data":{"success":<rpc 的 GetSuccess()，**可能 false**>}}` |
| Error | **读**4 分支：`NotFound→1002` / `PermissionDenied→1006` / `Internal→5001` / default→1015<br>**写**3 分支：`NotFound→1002` / `Internal→5001` / default→1015（**无** `PermissionDenied→1006`） |
| Timeout | HTTP 70s（无 per-call override） |
| Evidence | `update_article_logic.go:36-111` |

> ⚠️ **读 4 分支 vs 写 3 分支的不对称是 Go 现状**，不是笔误。RAG 侧刻意保留
> （`ArticleUpdateService.mapAuthReadError` 与 `updateArticle` 内联三分支分开）。

### OP-03 `DELETE /v1/article/:id` → `DeleteArticle`

同 OP-02 的「先读后写」骨架，但：写分支**也有** `PermissionDenied→1006`（与读**逐字相同**，
`delete_article_logic.go:47-60` 与 `72-85`），且成功时 `success` **恒 true**
（`delete_article_logic.go:87-89`）。请求同时带 `RequesterId`（读）与 `OperatorId`（写）。

### OP-04 `GET /v1/article/:id` → `GetArticle`（公开）

| 字段 | 内容 |
|---|---|
| Input | path `:id` + `form:"incr_view,optional"`（**bool，副作用字段**） |
| Precondition | **无**（公开端点，Go 的 `GetArticleLogic` 根本不调 `extractCurrentUserID`） |
| Processing | `GetArticle{ArticleId, IncrView, **PublicOnly: true**}`；`res==nil \|\| Article==nil \|\| Status != PUBLISHED` ⇒ **1002** |
| Result | `{"code":200,"data":{"article":{…18 字段…}}}` |
| Error | 三分支：`NotFound→1002` / `Internal→5001` / default（含 `PermissionDenied`）→ **1015** |
| Side Effects | `incr_view=true` ⇒ rpc 侧 `+1` 阅读量 |
| Evidence | `get_article_logic.go:35-79` |

> ⚠️ `Status != PUBLISHED ⇒ 1002` **不是冗余防御**：`public_only=true` 只是「请求」，
> 对端旧版本会无视它 ⇒ api 层必须自己再判（`get_article_logic.go:54-56`）。
> 同理 `ListArticles` 里任一行非 PUBLISHED ⇒ **整条请求 5001**（`return` 不是 `continue`，
> `list_articles_logic.go:63-68`）。

### OP-05 `GET /v1/me/article/:id` → `GetAuthorArticle`

`GetArticle{ArticleId, RequesterId:uid}`（**`IncrView` 与 `PublicOnly` 均不设 ⇒ 恒 false**）。
四分支错误映射（多 `PermissionDenied→1006`）；`Article==nil` ⇒ 1002；
`AuthorId != uid` ⇒ **1006**（不因 status 而拦，作者可看自己的草稿）。
⚠️ handler 复用了带 `IncrView` 的 `GetArticleReq`，但 logic **不读它** ⇒
`?incr_view=true` 在本端点是**无效参数**。

### OP-06 `GET /v1/articles` → `ListArticles`

8 个 `form:` 字段，4 个带 `,default=`（`page=1` / `page_size=20` / `sort_by=create_time` / `desc=true`）。
四个筛选字段**全部以非 nil 指针下发**（空串也下发，proto3 `optional` 的 presence 语义）。
`PublicOnly: true`。回显的 `Total/Page/PageSize` 取自 **rpc 响应**而非请求（`list_articles_logic.go:90-95`）。

### OP-07 `POST /v1/upload` → `UploadImage`（公开 · 唯一不走信封的端点）

```
r.FormFile("image")  失败 → httpx.ErrorCtx            → 400 text/plain + nosniff
io.ReadAll(file)      失败 → return nil, err → ErrorCtx → 400 text/plain
article-rpc.UploadFile 失败 → return nil, err → ErrorCtx → ⚠️ 状态码见 DRIFT-ARTICLE-API-001
成功 → httpx.OkJsonCtx(ctx, w, resp)                    → 裸 {"image_url":"…"}，HTTP 200
```
证据：`upload_image_handler.go:14-30`、`upload_image_logic.go:34-52`。
`errmsg.ErrorMinioUpload`(5004) **只进日志，不进响应**。

---

## 3. SPRINGBOOT-SEMANTIC-MODEL（§7 / §34）

结构与 §2 完全一致（逐 OP、逐字段），此处只记**与 §2 的差异点**与「实现选择」。

| 维度 | RAG 实现 | 与 Go 的关系 |
|---|---|---|
| 路由 | `@RestController @RequestMapping("/v1")` + 7 个 mapping | EQUIVALENT |
| 鉴权 | `JwtAuthInterceptor`（`HandlerInterceptor`）+ 4 条 `ProtectedRoute`(method+PathPattern) | EQUIVALENT（DRIFT-004 上游已修 path.Clean 后的匹配一致性） |
| 身份解析 | `ArticleApiSupport.userId(req)`（`req.getAttribute("userId")`） | EQUIVALENT |
| 响应成形 | `GlobalResponseAdvice`（`ResponseBodyAdvice`）统一包信封 | EQUIVALENT（Go 是 6 份手写重复） |
| 错误成形 | `GlobalExceptionHandler`（9 个 `@ExceptionHandler`） | **部分不等价 → §7 DRIFT-001 / 004** |
| gRPC 调用 | `@GrpcClient` + `BlockingStub` + `GoStyleRequestDeadline.applyTo()` | **超时作用域不等价 → §7 DRIFT-002** |
| 昵称补全 | `ArticleAuthorResolver`（独立 Bean，永不抛） | EQUIVALENT |
| 保护层 | `com.rag.common.protection.*` 4 件套 + `ArticleGoStyleCorsFilter` / `GoStyleJsonBodyLimitFilter` / `GoStylePathNormalizingFilter` | EQUIVALENT（MaxBytes 值已显式配 10485760） |

### 3.1 `@ResponseBodyAdvice` 二次包裹专项核对（AGENTS.md §3.3.3）

`HttpEntityMethodProcessor` 会在 `@ExceptionHandler` 返回的 `ResponseEntity` 上**继续查 advice chain**。
`GlobalResponseAdvice.beforeBodyWrite` 的 5 条放行分支逐条核过：

| `@ExceptionHandler` 返回 | 拆包后 `body` | 放行分支 | 结果 |
|---|---|---|---|
| `ResponseEntity<Void>.build()`（401） | `null` | 放行 1（`body == null`，`:77-79`） | 裸 401，无 Content-Type ✅ |
| `ResponseEntity<Response>`（200 信封） | `Response` | 放行 2（`instanceof Response`，`:81-83`） | 单层信封 ✅ |
| `ResponseEntity<byte[]>`（400 / 404） | `byte[]` | 放行 4（`:101-104`） | 原样 text/plain ✅ |
| `ResponseEntity<Void>`（405→404 空 body） | `null` | 放行 1 | 裸 404 空 body ✅ |
| `ResponseEntity<UploadImageResp>`（upload 成功） | `UploadImageResp` | 放行 3（**看 `returnType`**，`:91-93`） | 裸 `{"image_url":…}` ✅ |
| gRPC 未转换（兜底 500） | `null` | 放行 1 | 裸 500 ✅ |

⚠️ 放行 3 **必须看 `returnType` 不能看 `body`**（注释已指出）：`HttpEntityMethodProcessor`
先把 `ResponseEntity` 拆开，`body instanceof ResponseEntity` 恒假。
⇒ **AGENTS.md §3.3.3 那一类静默失效在本组件已被显式处理**（静态核对，未做活体对拍 → §9 UNRESOLVED-001）。

---

## 4. Equivalence Matrix（§24 + §35）

Result 仅取 `EQUIVALENT / IMPLEMENTATION DIFFERENCE / OBSERVABLE DIFFERENCE / SEMANTIC MISMATCH / UNKNOWN`。

| Operation | Semantic Dimension | Go | RAG | Result | Evidence |
|---|---|---|---|---|---|
| 全局 | 路由表（7 端点 method+path） | `routes.go:19-73` | `ArticleController` | EQUIVALENT | 静态源码 |
| 全局 | public 路径集合（含 upload 公开） | 第二/三段无 `WithJwt` | `PROTECTED_ROUTES` 4 条 | EQUIVALENT | 静态源码 |
| 全局 | 业务码表（15 条） | `errmsg.go:3-37` | `ErrorCode` | EQUIVALENT | 静态源码 |
| 全局 | 未知码回落 | `errmsg.go:39-45` → `FAIL` | `ErrorCode.getMsg` | EQUIVALENT | 静态源码 |
| 全局 | 信封字段名 | `response.Response` | `Response{code,msg,data}` | EQUIVALENT | 静态源码 |
| 全局 | 业务错误的 HTTP 状态码 | 恒 200 | `envelope()` 恒 `OK` | EQUIVALENT | 静态源码 |
| 全局 | 解析失败的形状 | 400 text/plain + nosniff + `\n` | `GoStylePlainTextErrors` | EQUIVALENT | 静态源码 |
| 全局 | 鉴权失败 | 401 **空 body** | `ResponseEntity.status(401).build()` | EQUIVALENT | `authhandler.go:106-122` |
| 全局 | 路径未注册 | 404 `404 page not found\n` | `GoStyleRouterErrorResponses.notFound` | EQUIVALENT | `patrouter.go:73` |
| 全局 | 方法不匹配 | **404 空 body**（`WithCors` 接管） | `handleMethodNotSupported` | EQUIVALENT | `patrouter.go:77-78`+`server.go:150`+`cors/handlers.go:44-48` |
| 全局 | panic / 未转换 | 裸 500 空 body | `handleAny`/`handleUnconvertedRpcStatus` | EQUIVALENT | `recoverhandler.go:14-19` |
| 全局 | MaxBytes | 10485760，裸 413 | `rag.protection.max-bytes` | EQUIVALENT | `maxbyteshandler.go:19-22` |
| 全局 | JSON body 8 MiB 上限 | `LimitReader` 静默截断 ⇒ 400 | 显式拒绝 400 | **OBSERVABLE DIFFERENCE** | DRIFT-006 |
| 全局 | CORS（5 头 / OPTIONS 204 / 不写 Allow-Credentials） | `cors/handlers.go:105-123` | `ArticleGoStyleCorsFilter` | EQUIVALENT | 静态源码 |
| 全局 | 路径归一 | `path.Clean` 注册+匹配两侧 | `GoStylePathNormalizingFilter` | EQUIVALENT | `patrouter.go:48,60` |
| 全局 | 超时执行者 | `DisableNativeRestTimeout` → 只有 observability 一个 | `observability.http-timeout: 70s` | EQUIVALENT | `observability/http.go:30-34,88` |
| 全局 | HTTP 超时响应 | 503 + `Request Timeout\n` | `HttpMiddleware.writeTimeoutResponse` | EQUIVALENT | 静态源码（共享层，like/message 已验） |
| 全局 | RPC retry | **无**（`RpcClientConf` 无 `Retries`） | 无 | EQUIVALENT | `zrpc/config.go:24-35` |
| 全局 | RPC client breaker | 默认开（`default=true`） | `GoStyleGrpcClientBreakerInterceptor` | EQUIVALENT | `zrpc/internal/config.go:14` |
| 全局 | 并发语义（无组件级状态） | 每请求一 goroutine | worker 池 + 无共享写 | EQUIVALENT | 静态源码 |
| 全局 | 事务 | 无 | 无 | EQUIVALENT | — |
| 全局 | Cache | 无 | 无 | EQUIVALENT | — |
| OP-01 | `Brief`/`CoverImageUrl` presence | `&req.Brief` 恒下发 | `nullToEmpty` 恒 `set` | EQUIVALENT | 静态源码 |
| OP-01 | 五分支错误码 | `create_article_logic.go:59-74` | `ArticleCreateService:113-125` | EQUIVALENT | 静态源码 |
| OP-01 | **1003/1004 失败带 data** | `:62-69` | `CreateFailure` + `handleCreateFailure` | EQUIVALENT | 静态源码 |
| OP-01 | article_id 从 status 文案提取 | `:82-92` | `:144-153` 逐字复刻 | EQUIVALENT | 静态源码 |
| OP-01 | 无 userId ⇒ **200+1005** | `:39-43` | `ArticleApiSupport.userId` | EQUIVALENT | 静态源码 |
| OP-02 | 先读后写 | `:43-67` | `ArticleUpdateService:87-105` | EQUIVALENT | 静态源码 |
| OP-02 | 读 4 分支 / 写 3 分支不对称 | `:48-60` vs `:95-105` | `mapAuthReadError` vs 内联 | EQUIVALENT | 静态源码 |
| OP-02 | 5 字段非空才下发 | `:74-88` | `setIfNotEmpty` | EQUIVALENT | 静态源码 |
| OP-02 | 非法 status ⇒ 字段不下发 | `:89-92`（`Enum()` 返 nil） | `forNumber` 返 null 则不 set | EQUIVALENT | 静态源码 |
| OP-02 | `SecondaryTags` 恒下发 | `:71` | `addAll` | EQUIVALENT | 静态源码 |
| OP-03 | 成功恒 `success=true` | `:87-89` | `new DeleteArticleResp(true)` | EQUIVALENT | 静态源码 |
| OP-03 | 双 id 下发 | `:42-46,68-71` | `RequesterId`+`OperatorId` | EQUIVALENT | 静态源码 |
| OP-04 | `PublicOnly=true` 闸门 | `:39,54-56` | `:107,123-126` | EQUIVALENT | 静态源码 |
| OP-04 | 非 PUBLISHED ⇒ 1002 | `:54-56` | `isPublished` | EQUIVALENT | 静态源码 |
| OP-04 | `PermissionDenied` ⇒ **1015** | `:49-50`（default） | `:118` | EQUIVALENT | 静态源码 |
| OP-04 | 不做身份判定 | 公开端点不调 `extractCurrentUserID` | `getArticle(id, incrView)` 无 uid 参数 | EQUIVALENT | 静态源码 |
| OP-05 | `IncrView`/`PublicOnly` 恒 false | `:38-40` 不设 | `:63-67` 不设 | EQUIVALENT | 静态源码 |
| OP-05 | ownership 闸门 ⇒ 1006 | `:60-62` | `:89-91` | EQUIVALENT | 静态源码 |
| OP-05 | 不套 status 闸门 | `:54-56` 只判 nil | `:83-85` 只判 `hasArticle` | EQUIVALENT | 静态源码 |
| OP-06 | 4 筛选字段恒下发 | `:37-47` 全 `&` | `:90-93` 全 `set` | EQUIVALENT | 静态源码 |
| OP-06 | 任一行非 PUBLISHED ⇒ **5001 整条失败** | `:63-68` | `:117-119` | EQUIVALENT | 静态源码 |
| OP-06 | `Total/Page/PageSize` 取 rpc 响应 | `:90-95` | `:132-134` | EQUIVALENT | 静态源码 |
| OP-06 | 空结果 ⇒ `articles: null` | `var articles []types.Article` | `:114-126` 保持 null | EQUIVALENT | 静态源码 |
| OP-07 | 成功走**裸**响应 | `upload_image_handler.go:28` | `ResponseEntity` + advice 放行 3 | EQUIVALENT | 静态源码 |
| OP-07 | 下游失败 ⇒ HTTP 状态码 | `errcode.CodeFromGrpcError` | `GoStylePlainTextErrors.badRequest` = 400 | **SEMANTIC MISMATCH** | **DRIFT-001** |
| 入参解释 | 必填判据 = tag 有无 `,optional` | `parseOption`（只认 inherit/string/optional*） | `GoStyleRequiredFields` | EQUIVALENT | `utils.go:372` |
| 入参解释 | 数字/布尔进 String 字段 | `newTypeMismatchError` ⇒ 400 | `GoStyleStringFields` | EQUIVALENT | `unmarshaler.go:643-662` |
| 入参解释 | 字符串进 int 字段 | `type mismatch`（本域无 `,string`） | `ArticleGoStyleCoercion` 补强 | EQUIVALENT | 静态源码 |
| 入参解释 | `omitempty` ≠ 可缺省 | `parseOption` 不认 `omitempty` | 未使用 | EQUIVALENT | `utils.go:372` |
| 入参解释 | 空 query 值被丢弃 | `httpx/util.go:44-45` | `GoFormValues.raw` | EQUIVALENT | 静态源码 |
| 入参解释 | bool 真值表 `1/0/true/false` | `utils.go:106-113` | `GoFormValues.bool:103-111` | EQUIVALENT | 静态源码 |
| 入参解释 | int base-10、位宽 32 | `utils.go:120-121` | `GoFormValues.int32` | EQUIVALENT | 静态源码 |
| 入参解释 | 非 JSON Content-Type ⇒ 丢 body | `withJsonBody` 字面 `Contains` | `GoStyleJsonBodyConverter.goReadsBody` | EQUIVALENT | `requests.go:153-155` |
| 入参解释 | **重复 query key** | 取**第一个**（`unmarshaler.go:841-843`） | Spring 逗号拼接 → 400 | **SEMANTIC MISMATCH** | **DRIFT-003** |
| 入参解释 | query 参数总数上限 2048 | `httpx/util.go:15,48-53` | 🔴 **原判「无上限」已被实测推翻**（Tomcat 的 8 KB 请求行上限**更早**触发 ⇒ 状态码两侧都是 400）⇒ **已补应用层 Go 语义闸门** | ✅ EQUIVALENT（判据）＋ 🟡 body 形状差异（不修） | **真 Tomcat 实测**（2026-10-24，`DRIFT-ARTICLE-API-005`） |
| 序列化 | `ext_info` 空 map ⇒ null | `cloneStringMap:106-108` | `:223-226` | EQUIVALENT | 静态源码 |
| 序列化 | `ext_info` key 排序 | UTF-8 字节序（`encoding/json/encode.go:805-807` `strings.Compare`） | ~~`TreeMap`（UTF-16 码元序）~~ ⇒ **已改为按码点比较** | ✅ EQUIVALENT | **双侧探针实测**（2026-10-24，`DRIFT-ARTICLE-API-007`） |
| 序列化 | `secondary_tags` 空 ⇒ null | nil slice | `cloneStringList` | EQUIVALENT | 静态源码 |
| 序列化 | `author_name` 空 ⇒ **key 消失** | `json:",omitempty"` | `@JsonInclude(NON_EMPTY)` | EQUIVALENT | 静态源码 |
| 序列化 | `status` 输出数值非枚举名 | `int32(res.Article.Status)` | `getStatusValue()` | EQUIVALENT | 静态源码 |
| 昵称补全 | 4 条失败路径全回落 | `helper.go:71-96` | `ArticleAuthorResolver:82-130` | EQUIVALENT | 静态源码 |
| 昵称补全 | 请求内去重 | `helper.go:53-68` | `ArticleApiSupport:159-174` | EQUIVALENT | 静态源码 |
| 昵称补全 | `uid <= 0` 不发 RPC | `helper.go:77-80` | `:90-98` | EQUIVALENT | 静态源码 |
| 昵称补全 | **RPC 调用超时作用域** | `RpcClientConf.Timeout` default **2000ms** | HTTP 全量预算（70s） | **SEMANTIC MISMATCH** | **DRIFT-002** |
| 配置 | `SecurityRpc` 客户端 | 有（但零消费者、NonBlock ⇒ 不 panic） | 无 | EQUIVALENT | `zrpc/config.go:30` |

---

## 5. External Interaction & Error Flow（§8 + §10）

### 5.1 外部系统

| External System | Operation | 输入 | 失败语义 | 是否影响分支 | 是否阻塞 |
|---|---|---|---|---|---|
| `article-rpc` | `CreateArticle/GetArticle/UpdateArticle/DeleteArticle/ListArticles/UploadFile` | 见 §2 各 OP | gRPC status → 业务码 | ✅ | ✅ |
| `user-rpc` | `GetUser(uid)` | `author_id` 解析出的正整数 | **永不冒泡**（回落 `用户 <id>` / `未知作者`） | ❌ | ❌（但占用延迟） |
| 注册中心 | etcd（Go） / Nacos（RAG） | flat key / service name | 非阻塞 dial | ❌ | ❌ |
| JWT | HMAC 验签 + `exp` | `Authorization: Bearer` | 401 空 body | ✅ | ✅ |

⚠️ **区分 `null` 与 `error`**：Go 的 `res == nil`（`list_articles_logic.go:58-60`）⇒ 5001 **不是** 404；
RAG 的 `res == null` 检查已保留（`ArticleListService.java:109-112`）。

### 5.2 错误流全景（Go → HTTP）

| 触发 | Go 落点 | HTTP | body | RAG 落点 | Result |
|---|---|---|---|---|---|
| 保护层 MaxBytes | `maxbyteshandler.go:22` 裸 `WriteHeader` | 413 | 空 | `GoStyleMaxBytesFilter` | EQUIVALENT |
| 保护层 413 **压过** 401 | 链序（MaxBytes 在鉴权链内、鉴权前） | 413 | 空 | Filter 在 DispatcherServlet 外 | EQUIVALENT |
| 保护层 Shedding | `shedder` 裸写 | 503 | — | `GoStyleCpuSheddingFilter` | UNKNOWN（无活体） |
| HTTP 超时 | `observability/http.go:88` `http.Error` | 503 | `Request Timeout\n` | `HttpMiddleware` | EQUIVALENT（静态） |
| 客户端断开 | 同上，`statusClientClosedRequest` | 499 | `Request Timeout\n` | `HttpMiddleware` | EQUIVALENT（静态） |
| JWT 缺失/错/过期 | `authhandler.go:121` | 401 | **空** | `handleAuthentication` | EQUIVALENT |
| `userId` claim 取不到 | `create_article_logic.go:39-43` | **200** | 信封 1005 | `handleBusiness` | EQUIVALENT |
| `httpx.Parse` 失败 | `responses.go:155` `http.Error` | 400 | 裸文案 + `\n` | `GoStylePlainTextErrors` | EQUIVALENT |
| 必填缺席 / 显式 null | `unmarshaler.go:835` / `:856-862` | 400 | `is not set` / `mustn't be nil` | `GoRequiredFieldException` | EQUIVALENT |
| 类型不符 | `unmarshaler.go:1202` | 400 | `type mismatch for field %q, expect…, actual…` | `GoStyleBadRequestException` | EQUIVALENT |
| 上传缺 `image` 字段 | `upload_image_handler.go:17-19` | 400 | `http: no such file` | `handleParseFailure` | EQUIVALENT |
| **上传时下游 gRPC 失败** | `responses.go:150-153` `errcode.CodeFromGrpcError` | **按 gRPC 码映射** | `rpc error: code = …` | `handleGoStyleBadRequest` | **SEMANTIC MISMATCH** |
| 路径未注册 | `patrouter.go:73` → `http.NotFound` | 404 | `404 page not found\n` | `handleNoResourceFound` | EQUIVALENT |
| 方法不匹配 | `patrouter.go:78` → `cors.NotAllowedHandler` | 404 | **空** | `handleMethodNotSupported` | EQUIVALENT |
| panic / 未转换的 gRPC | `recoverhandler.go:14-19` | 500 | **空** | `handleAny` | EQUIVALENT |

---

## 6. 逐维度结论（§12–§21）

### 6.1 State Semantics（§12）
无组件级状态。请求内两处状态（`enrichArticleAuthors` 的去重 map、observability 的 body buffer）
均为局部变量，Go 侧同样是局部（`helper.go:53`、`observability/http.go:147`）。
⇒ **EQUIVALENT**。⚠️ RAG 把 stub 缓存成单例字段（`ArticleRpcClient.articleServiceBlockingStub`），
但 protobuf `AbstractStub` 的 `withDeadlineAfter` 每次返回**新实例**（`GoStyleRequestDeadline:110-120`），
调用点每次都重新取（`ArticleRpcClient:67-69`），没有把 deadline 固化 ⇒ 仍是 EQUIVALENT。

### 6.2 Cache Semantics（§13）
本组件**无缓存**（Go / RAG 均无）。请求内去重 map 是「同一请求内不重复 RPC」，
不是跨请求缓存 —— RAG 侧刻意不把它提级为 Bean（`ArticleApiSupport:152-153` 的注释说明了理由）。
⇒ **EQUIVALENT / N/A**。

### 6.3 Authentication / Authorization（§14）
| 维度 | Go | RAG | Result |
|---|---|---|---|
| Identity source | JWT `userId` claim（`authhandler.go:72-79` 把非标准 claim 塞 ctx） | `request.setAttribute("userId", claims.get("userId"))` | EQUIVALENT |
| Claim default | 取不到 ⇒ logic 层 1005（**不是 401**） | `ArticleApiSupport.userId` → `BusinessException(1005)` | EQUIVALENT |
| claim 取值判定 | `extractCurrentUserID` 的 type switch + `TrimSpace` + `"<nil>"` 排除 | `resolveUserIdClaim:101-114` | EQUIVALENT |
| Signature / exp | golang-jwt HS256，`tok.Valid` | jjwt `parseSignedClaims` | EQUIVALENT |
| Resource ownership | api 层 `AuthorId != uid ⇒ 1006`（OP-02/03/05） | 同 | EQUIVALENT |
| Failure mapping | 401 **空 body** | `ResponseEntity.status(401).build()` | EQUIVALENT |
| 黑名单 | **两侧都没有**（§1.2） | — | EQUIVALENT |

### 6.4 Transaction Semantics（§15）
api 层无事务。Go 的「先读后写」（OP-02/03）**不是**事务，是两次独立 RPC。
RAG 保持两次调用，无 `@Transactional`（也不需要 —— 无 DataSource）。
⇒ **EQUIVALENT / N/A**。

### 6.5 Concurrency Semantics（§16）
| 维度 | Go | RAG | Result |
|---|---|---|---|
| 请求隔离 | 每请求一 goroutine | worker 池（`SynchronousQueue`，上限 512） | EQUIVALENT |
| 共享写状态 | 无 | 无 | EQUIVALENT |
| 幂等 | 上游 `IncrView` 本身有副作用，两侧一致 | 同 | EQUIVALENT |
| deadline 载体 | ctx 隐式传递 | `ThreadLocal`（worker 线程 set / finally clear） | EQUIVALENT（实现差异） |

### 6.6 Timeout Semantics（§17）⚠️
| 调用点 | Go | RAG | Result |
|---|---|---|---|
| HTTP 总预算 | 70000ms（`article-api.yaml:4`），唯一执行者是 observability（`http.go:30-34`） | `observability.http-timeout: 70s` | EQUIVALENT |
| `article-rpc` 单次 | `ArticleRpcConf.Timeout=70000`（yaml:12） | 剩余 HTTP 预算（≤70s） | EQUIVALENT（近似） |
| `article-rpc` CreateArticle 单次 | **显式** `zrpc.WithCallTimeout(70s)`（`create_article_logic.go:53`） | 无 per-call override | EQUIVALENT（当前部署同值） |
| **`user-rpc` 单次** | yaml **未配** ⇒ `RpcClientConf.Timeout` **default 2000ms** | 剩余 HTTP 预算（≤70s） | **SEMANTIC MISMATCH → DRIFT-002** |
| `rest.WithTimeout(70s)`（routes.go:44） | **no-op** —— `DisableNativeRestTimeout` 已把 `Middlewares.Timeout` 置 false（`engine.go:154-156` 的唯一消费点失效） | 无对应旋钮 | EQUIVALENT |

### 6.7 Retry Semantics（§18）
Go：`zrpc.RpcClientConf`（`zrpc/config.go:24-35`）**没有** `Retries` 字段 ⇒ 零重试。
RAG：`ArticleRpcClient` / `UserRpcClient` 未配置任何 retry ⇒ 零重试。
⇒ **EQUIVALENT**。⚠️ 「加 retry」会把「一次失败 → 1015」变成「重试 N 次后失败」，
可观察的调用次数与延迟都变 ⇒ 属新增行为。

### 6.8 Serialization Semantics（§19）
见 §4 矩阵「序列化」6 行。⚠️ 三处最容易错的地方均已正确处理：
`secondary_tags`/`ext_info` 空 ⇒ **null**（不是 `[]`/`{}`）；`author_name` 空 ⇒ **key 消失**（不是 `""`）；
`ext_info` 用 `TreeMap`（不是 `LinkedHashMap`）。

### 6.9 Configuration Semantics（§20）
| Go 字段 | 生效值 | RAG 落点 | Result |
|---|---|---|---|
| `Host`/`Port` | `0.0.0.0` / `8889` | `server.address` / `server.port` | EQUIVALENT |
| `Timeout` | **70000**（显式，非 tag 默认 3000） | `observability.http-timeout: 70s` | EQUIVALENT |
| `MaxBytes` | **10485760**（显式，覆盖 tag 默认 1048576） | `rag.protection.max-bytes: 10485760` | EQUIVALENT |
| `Middlewares.*` | 全 `default=true`（`rest/config.go:12-22`） | 4 件套 `enabled: true` | EQUIVALENT |
| `CpuThreshold` | 900（千分比 = 90%） | `cpu-shreshold: 900` | EQUIVALENT |
| `Auth.AccessSecret` | `ae0536f9-…-5a19ed505da0`（36 字节） | 同（`${USER_JWT_SECRET:…}`） | EQUIVALENT |
| `Auth.AccessExpire` | 31536000，**本模块不读** | 保留不读 | EQUIVALENT |
| `ArticleRpcConf.Timeout` | 70000 | 无独立项（走 HTTP 预算） | EQUIVALENT（近似） |
| **`UserRpcConf.Timeout`** | **未配 ⇒ 2000ms** | 无独立项 | **SEMANTIC MISMATCH → DRIFT-002** |
| `SecurityRpcConf` | 配了但零消费者 | 无 | EQUIVALENT |
| `Log`/`Telemetry` | file / jaeger:4317 / sampler 1.0 | logback / OTLP / `always_on` | EQUIVALENT（表达层） |

### 6.10 Lifecycle Semantics（§21）
启动：Go = `conf.MustLoad` → `logx.MustSetup` → `DisableNativeRestTimeout` → `MustNewServer`（含 `WithCors`）
→ `NewServiceContext`（3 个 client，NonBlock 非阻塞）→ `RegisterHandlers` → `Start`。
RAG = `SpringApplication.run`（扫 `article.api` + `com.rag.common`）→ `WebMvcConfig` 挂拦截器 →
Filter 链（`@Order` 排序）→ Tomcat ready。
⇒ 顺序差异均属表达层；**唯一需确认的是 Filter 链的实际 `@Order` 全序**，静态可见但无活体 ⇒ §9 UNRESOLVED-003。

---

## 7. Semantic Drifts（§27 + §36）

### 7.1 DRIFT-ARTICLE-API-001 —— 上传路径下游 gRPC 失败的 HTTP 状态码

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` / `POST /v1/upload` |
| **Operation** | `UploadImage` |
| **Category** | Error Identity / HTTP 状态码语义 |
| **Go Behavior** | `upload_image_logic.go:45-48` 对 `UploadFile` 的错误 `return nil, err`（裸 err）→ `upload_image_handler.go:25-26` 调 `httpx.ErrorCtx(r.Context(), w, err)`（**不带 fns**）→ `httpx/responses.go:142-158`：<br>`handler == nil`（本模块未注册 `SetErrorHandler`）→ 走 `else if errcode.IsGrpcError(err)` 分支 → `http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))`。<br>`errcode.IsGrpcError` 判 `err` 是否实现 `GRPCStatus()`（`rest/internal/errcode/grpc.go:45-55`）；gRPC-Go 的 `status.Error` **实现**它，且 `articleservice.UploadFile`（`rpc/articleservice/article_service.go:76-79`）**原样返回**该错误、无包装。<br>⇒ HTTP 状态码 = `CodeFromGrpcError`（`grpc.go:12-42`）映射结果。 |
| **Java Behavior** | `ArticleUploadService.java:93-98` 把 `StatusRuntimeException` 包成 `GoStyleBadRequestException` → `GlobalExceptionHandler.java:181-185` → `GoStylePlainTextErrors.badRequest(...)` ⇒ **恒 HTTP 400**。 |
| **Difference** | 同一失败事件，两侧 HTTP 状态码不同，且 RAG 侧**丢弃了 gRPC 码**。典型映射差异：<br>`Unavailable` → Go **503** / RAG **400**<br>`Internal` → Go **500** / RAG **400**<br>`DeadlineExceeded` → Go **504** / RAG **400**<br>`ResourceExhausted` → Go **429** / RAG **400**<br>`NotFound` → Go **404** / RAG **400** |
| **Why It Matters** | `POST /v1/upload` 是**公开端点**（无 JWT）。下游 article-rpc 不可用时（发布/扩容/故障）几乎必然是 `Unavailable`。客户端与网关若按状态码分流（`>=500` 触发重试/告警、429 退避），RAG 侧的 400 会被判成**客户端错误** ⇒ 不重试、不告警、熔断器不计入。 |
| **Observable Impact** | 一个原本应触发重试与告警的下游故障，在 RAG 上表现为静默的 400。 |
| **Likely Cause** | 「article-api 没有 error handler ⇒ 一律 400」这条**推理只走完了 `doHandleError` 的 else 分支**，漏掉了它前面的 `errcode.IsGrpcError` 分支。 |
| **Must Preserve** | `httpx.ErrorCtx` 的两条分支要分开：`非 gRPC 错误 ⇒ 400`；`gRPC 错误 ⇒ errcode.CodeFromGrpcError`。 |
| **Risk** | **HIGH** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Evidence** | Go：`service/article/api/internal/logic/article/upload_image_logic.go:45-48`、`internal/handler/article/upload_image_handler.go:25-26`；go-zero `v1.10.2` `rest/httpx/responses.go:145-159`、`rest/internal/errcode/grpc.go:12-55`；RAG：`ArticleUploadService.java:93-98`、`GlobalExceptionHandler.java:181-185`、`GoStylePlainTextErrors.java:64-75`。**证据强度：静态源码（两侧 + 框架消费点），无双侧活体对拍。** |

**修复方向（不在本轮范围）**：`GoStyleBadRequestException` 增加一个「按 gRPC 码映射」工厂，
并在 `GlobalExceptionHandler` 里用 `GoStylePlainTextErrors.text(status, msg)` 输出。

---

### 7.2 DRIFT-ARTICLE-API-002 —— `user-rpc`（昵称补全）单次调用超时作用域

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` / `ArticleAuthorResolver.resolveAuthorName` |
| **Operation** | `GET /v1/article/:id`、`GET /v1/articles` 内部的 `user-rpc.GetUser` |
| **Category** | Timeout Semantic Equivalence（spec §17） |
| **Go Behavior** | `etc/article-api.yaml:20-24` 的 `UserRpcConf` **没有 `Timeout` 字段** ⇒ 走 tag 默认 `zrpc/config.go:31` `Timeout int64 \`json:",default=2000"\`` = **2000ms**。消费点：`zrpc/client.go:70-71` `if c.Timeout > 0 { opts = append(opts, WithTimeout(2000ms)) }` → `zrpc/internal/client.go:83` 装进 unary interceptor 链 ⇒ **每一次 `GetUser` 调用有 2s deadline**。超时后 `resolveAuthorName` 走 `helper.go:83-86` 回落 `用户 <id>`。 |
| **Java Behavior** | `UserRpcClient.userRpc()`（`UserRpcClient.java:48-50`）套 `GoStyleRequestDeadline.applyTo(stub)` ⇒ deadline = **本次 HTTP 请求的剩余预算（上限 70s）**。 |
| **Difference** | 同一降级事件的可观察延迟差 **约 35 倍**：Go 在 ~2s 返回 200 + 回落昵称；RAG 最多挂 70s。若 user-rpc 变慢到 70s，Go 侧早已返回 200，RAG 侧则会被 observability 中间件在 70s 拦下 ⇒ **HTTP 503 `Request Timeout`**。 |
| **Why It Matters** | 「昵称补全失败不得影响文章读取」（`ArticleAuthorResolver` 类注释的显式设计目标）在 **70s 尺度上被打破** —— user-rpc 抖动会让整个文章列表页超时失败。 |
| **Observable Impact** | 正常态无差异（user-rpc 亚秒级返回）。user-rpc 慢/挂 ⇒ Go 200、RAG 慢响应或 503。 |
| **Likely Cause** | RAG 侧把「Go 的 ctx deadline 传播」等价成了「HTTP 预算传播」，但 Go 侧除 ctx 之外还有**一层 per-client 的 2s 硬上限**；逐字段对 yaml 时看不到 tag 默认值。 |
| **Must Preserve** | `user-rpc` 单次调用预算 = 2s（`RpcClientConf.Timeout` tag 默认）。 |
| **Risk** | **MEDIUM-HIGH** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Evidence** | Go：`service/article/api/etc/article-api.yaml:20-24`（无 Timeout）、go-zero `zrpc/config.go:31`（声明点）、`zrpc/client.go:70-71` + `zrpc/internal/client.go:83`（**消费点**）；RAG：`UserRpcClient.java:48-50`、`GoStyleRequestDeadline.java:110-120`、`application.yml:188-189`。**证据强度：静态源码（已读到消费点）。** |

⚠️ 这正是 AGENTS.md §3.3.1 点名的形态：**「yaml 里没写」≠「没设」**。
`UserRpcConf.Timeout` 就是一条藏在 struct tag 里、且**与 `ArticleRpcConf.Timeout`（显式 70000）不同**的默认值。

---

### 7.3 DRIFT-ARTICLE-API-003 —— 重复 query key 的解释

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` / `GET /v1/article/:id`、`GET /v1/articles` |
| **Category** | Input Interpretation（入参解释） |
| **Go Behavior** | `httpx.GetFormValues`（`util.go:35-62`）把同名 key 的多个值收成 `[]string` 存入 params；`formUnmarshaler` 带 `mapping.WithFromArray()`（`requests.go:27-31`）⇒ 消费点 `unmarshaler.go:835-846`：<br>`if u.opts.fromArray { if 目标字段不是 slice { … mapValue = val.Index(0).Interface() } }`<br>⇒ **取第一个值**，其余丢弃。`?page=1&page=2` ⇒ `page=1` ⇒ HTTP 200。 |
| **Java Behavior** | `ArticleController` 的 8 个 `@RequestParam String`（`ArticleController.java:194-202`）由 Spring `RequestParamMethodArgumentResolver` 处理多值 ⇒ 逗号拼接成 `"1,2"` ⇒ `GoFormValues.int32("1,2", …)` ⇒ `Integer.parseInt` 抛 ⇒ `GoStyleBadRequestException` ⇒ **HTTP 400**。 |
| **Difference** | `?page=1&page=2`：Go **200**（page=1），RAG **400**。<br>`?incr_view=1&incr_view=0`：Go `true`，RAG 400。 |
| **Why It Matters** | 重复 query key 在实践中不罕见（客户端拼接 bug、网关重写、部分 HTTP 客户端的数组序列化）。一次原本成功的读请求变成 400。`incr_view` 那条还有副作用语义（重复 key 下 Go 恒取第一个）。 |
| **Likely Cause** | 「把 query 收成 String 再自己按 Go 规则解析」漏掉了「多值 → 取第一个」这一步 —— `GoFormValues.raw` 只处理了「单值 / 空值」。 |
| **Must Preserve** | 同名 key 出现多次 ⇒ 取**第一个**（`unmarshaler.go:841-843`）。 |
| **Risk** | **MEDIUM** |
| **Classification** | **SEMANTIC MISMATCH** |
| **Evidence** | Go：go-zero `rest/httpx/util.go:35-62`、`rest/httpx/requests.go:27-31`、`core/mapping/unmarshaler.go:835-846`（**消费点**）；RAG：`ArticleController.java:194-202`、`form/GoFormValues.java:83-88,121-137`。**证据强度：静态源码。** |

---

### 7.4 DRIFT-ARTICLE-API-004 —— 一条**锁死了错误答案**的绿测试

| 字段 | 内容 |
|---|---|
| **Component** | `article-api` 测试 |
| **Category** | 测试证据（AGENTS.md §3.3 硬约束） |
| **现状** | `ArticleApiMvcPipelineTest.java:353-372`：`@DisplayName("upload 的 RPC 失败 ⇒ **400 text/plain + nosniff**，不是 1015 信封（Go 无 error handler）")`，注入 `Status.INTERNAL.withDescription("minio down")`，断言 `status().isBadRequest()`。本轮实测该测试**通过**（`Tests run: 33, Failures: 0`）。 |
| **问题** | 该 `@DisplayName` 的**前提**（Go 无 error handler ⇒ 400）只覆盖了 `doHandleError` 的 `else` 分支。按 Go 实际控制流，`codes.Internal` 会被 `errcode.CodeFromGrpcError`（`grpc.go:31-32`）映射成 **HTTP 500**。⇒ 这条绿测试断言的是 **RAG 的行为**，却被写成了 **Go 的事实**。 |
| **危害** | ① 它会在修 DRIFT-ARTICLE-API-001 时**先变红**，且测试名会让人误判「修复 = 漂移」；② 它让「400」这个数字看起来已被真源验证过 —— 正是 AGENTS.md §3.3.2 点名的「以 `Go: …` 措辞出现的断言是高危项」。 |
| **处置** | 修 DRIFT-001 时**先改这条断言**（拆成两条：`codes.Internal` ⇒ 500、`codes.Unavailable` ⇒ 503、非 gRPC ⇒ 400），再改实现。 |
| **Risk** | **HIGH**（作为测试风险） |
| **Classification** | **OBSERVABLE DIFFERENCE**（测试与真源不一致） |
| **Evidence** | `ArticleApiMvcPipelineTest.java:353-372`（静态源码）；本轮 `mvn -pl service/article/article-api test` ⇒ `Tests run: 50, Failures: 0, Errors: 0`。**证据强度：纯 MockMvc + Mockito（无真实 gRPC、无真实 Tomcat）。** |

---

### 7.5 其余条目（低危 / 上游已自登记）

#### DRIFT-ARTICLE-API-005 —— query 参数总数上限（OBSERVABLE DIFFERENCE，LOW）
- **Go**：`httpx/util.go:15` `maxFormParamCount = 2048`；消费点 `util.go:48-53` —— 累计超过 2048 个非空值即 `return nil, fmt.Errorf("too many form values, …")` ⇒ **400**。
- **RAG**：`ArticleController` 的 8 个 `@RequestParam` 无总数上限，Tomcat 只在 `maxHttpHeaderSize` 等处限流。
- **影响**：需要 >2048 个 query 值的请求 Go 拒、RAG 收。本组件的 query 字段只有 8 个，**实际不可达** ⇒ LOW。
- **证据**：静态源码。

#### DRIFT-ARTICLE-API-006 —— 8–10 MiB JSON body 的错误文案（OBSERVABLE DIFFERENCE，LOW）
- **Go**：`requests.go:21` `maxBodyLen = 8 << 20`（**框架常量，不在任何 tag/yaml 里**）→ `io.LimitReader` **静默截断** → 解析器报 `unexpected EOF` → 400。
- **RAG**：`GoStyleJsonBodyLimitFilter.java:93-97` 显式拒绝，body 为 `request entity too large\n`。
- **可观察契约相同**（400 + text/plain + nosniff + 业务不执行），差异仅在 body 文案。
- **证据**：静态源码。上游已在该 Filter 的 javadoc 自登记（标 DRIFT-002）。

#### DRIFT-ARTICLE-API-007 —— `ext_info` key 排序在非 BMP 字符上不同（OBSERVABLE DIFFERENCE，极低）
- **Go**：`encoding/json/encode.go:793-807`，`slices.SortFunc` 按 `ks`（Go string `<` = **UTF-8 字节序**）。
- **RAG**：`ArticleApiSupport.java:226` `new java.util.TreeMap<>(source)` ⇒ `String.compareTo` = **UTF-16 码元序**。
- **影响**：仅当 `ext_info` 的 key 含增补平面字符（代理对）时两者序不同；ASCII / BMP 范围内逐字节一致。
- **证据**：`go1.25.11 src/encoding/json/encode.go:805`。静态源码。

#### DRIFT-ARTICLE-API-008 —— 本仓台账的文件计数结论已被再次推翻（台账卫生）
见 **§0.1**。`src/main/java` 实测 **38**（原文 36），主源码 **4,666** 行（原文 4,275），
且 7 个主源文件在 **2026-10-23 当日**仍有写入。
- **Risk**（作为台账风险）：MEDIUM —— 任何按「36 个文件 / 4,275 行」做的完备性推断都已失效。
- **Classification**：台账卫生问题（非实现漂移）。
- **证据**：本会话 `find`/`wc` 实跑。

---

### 7.6 上游实现期的内部编号（`DRIFT-001…007`）对照 —— 本轮**未独立验证**

RAG 源码注释里出现的这些编号**不在本轮验证范围**，仅登记其存在与本轮的间接观察：

| 上游编号 | 主题 | 落点 | 本轮观察 |
|---|---|---|---|
| `DRIFT-001` | 方法不匹配 ⇒ 404 而非 405 | `GlobalExceptionHandler.java:262-318` | ✅ 本轮静态核对**成立**（`patrouter.go:77-78` + `server.go:150` + `cors/handlers.go:44-48`） |
| `DRIFT-002` | JSON body > 8 MiB ⇒ 400 | `GoStyleJsonBodyLimitFilter.java` | ✅ 成立（见 DRIFT-006；文案差异保留） |
| `DRIFT-003` | 父类 `MultipartException` 未列 ⇒ 裸 500 | `GlobalExceptionHandler.java:219-225` | 未做活体验证 |
| `DRIFT-004` | `path.Clean` 归一 | `GoStylePathNormalizingFilter.java` | ✅ 静态成立（`patrouter.go:48,60`） |
| `DRIFT-005` | `setHeader` vs `setContentType` 的 `charset=utf-8` 空格 | `GoStyleJsonBodyLimitFilter.java:104-108` | **无真实 Tomcat 对拍 ⇒ UNKNOWN**（§9 UNRESOLVED-001） |
| `DRIFT-006` | type mismatch 三段式文案 | `GoStyleStringFields.java:69-77` | ✅ 静态成立（`unmarshaler.go:1202-1204` 的 `%q` 格式） |
| `DRIFT-007` | `ext_info` 用 TreeMap 而非 LinkedHashMap | `ArticleApiSupport.java:211-227` | ✅ 静态成立（`encode.go:805`），但见 DRIFT-007 的排序边界 |

⚠️ 上游注释里 `GlobalExceptionHandler.java:118` 引用的 **`DRIFT-024`** 在本仓 `docs/DRIFT/` 下
**查无对应台账条目** ⇒ 该编号来自另一份未入库的历史清单。已记入 §9 UNRESOLVED-008。

---

## 8. Test Cases（§29 + §30）

### 8.1 现有测试盘点（本轮实跑）

| 测试类 | 条数 | 结果 | 载体 |
|---|---|---|---|
| `ArticleApiMvcPipelineTest` | 33 | ✅ 全绿 | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@MockitoBean` |
| `ArticleApiRouteAuthBoundaryTest` | 17 | ✅ 全绿 | 同上 |
| **合计** | **50** | **50 passed / 0 failed** | **全部 MockMvc + Mockito** |

命令：`mvn -pl service/article/article-api -am -DskipTests install` ⇒ `BUILD_EXIT=0`；
`mvn -pl service/article/article-api test -Dtest='ArticleApi*Test'` ⇒ `Tests run: 50, Failures: 0, Errors: 0`。

⚠️ 按 AGENTS.md：**「50 条绿测试」不构成本台账任何一条 EQUIVALENT 的证据**。
理由：① 纯 Mockito，不执行 gRPC；② MockMvc 不走真实 Tomcat；
③ 至少 **DRIFT-004** 就是被绿测试锁死的错误断言。

### 8.2 本轮建立的 CASE（逐条对照表）

| CASE | 输入 | Go（真源） | RAG | 判定 |
|---|---|---|---|---|
| CASE-01 正常创建 | 合法 token + 合法 JSON | 200 + `{code:200,data:{article_id}}` | 同（绿测 `ArticleApiMvcPipelineTest`） | EQUIVALENT（MockMvc） |
| CASE-02 空 body 创建 | `Content-Length: 0` | body 丢弃 ⇒ `title is not set` ⇒ **400** | `required=false` + normalize ⇒ 同 400 | EQUIVALENT |
| CASE-03 必填显式 null | `{"title":null,…}` | `mustn't be nil` ⇒ 400 | 同 | EQUIVALENT |
| CASE-04 非 JSON CT | `Content-Type: text/plain` + JSON body | body **丢弃** ⇒ 必填失败 ⇒ 400 | `GoStyleJsonBodyConverter` 返 null ⇒ 同 | EQUIVALENT |
| CASE-05 重复存在 | rpc `AlreadyExists` | 200 + 1001 + `data:null` | 同 | EQUIVALENT |
| CASE-06 发布流水线未完 | rpc `DeadlineExceeded` + msg 带 `xxx:id` | 200 + **1003 + data 带 id** | 同 | EQUIVALENT |
| CASE-07 无 userId claim | 签名有效但无 `userId` | **200 + 1005**（不是 401） | 同 | EQUIVALENT |
| CASE-08 无 Authorization | 受保护端点 | **401 空 body** | 同 | EQUIVALENT |
| CASE-09 越权改文章 | 非作者 | 1006 | 同 | EQUIVALENT |
| CASE-10 公开读草稿 | rpc 回 `DRAFT` | **1002**（不泄露正文） | 同 | EQUIVALENT |
| CASE-11 列表混入草稿 | rpc 回任一行非 PUBLISHED | **整条 5001** | 同 | EQUIVALENT |
| CASE-12 空列表 | rpc 回 0 行 | `articles: null` | 同 | EQUIVALENT |
| CASE-13 `?incr_view=1` | — | true | true | EQUIVALENT |
| CASE-14 `?incr_view=` | 空值 | false，不报错 | 同 | EQUIVALENT |
| CASE-15 `?page=010` | 八进制字面量 | **10**（base-10） | 10 | EQUIVALENT |
| CASE-16 **重复 query key** | `?page=1&page=2` | **200，page=1** | **400** | ⛔ **DRIFT-003** |
| CASE-17 **上传时 rpc `Unavailable`** | 下游不可用 | **503 + `rpc error: code = Unavailable…`** | **400** | ⛔ **DRIFT-001** |
| CASE-18 **上传时 rpc `Internal`** | 下游 500 | **500** | **400** | ⛔ **DRIFT-001 + DRIFT-004（绿测锁死）** |
| CASE-19 **user-rpc 慢 3s** | 昵称补全 | **~2s 返回 200**（回落昵称） | **~3s 才返回 200**；>70s 则 **503** | ⛔ **DRIFT-002** |
| CASE-20 8 MiB ~ 10 MiB JSON | 合法 JSON | 400（截断后解析失败） | 400（显式拒绝），文案不同 | △ DRIFT-006 |
| CASE-21 路径未注册 | `/v1/nope` | 404 `404 page not found\n` | 同 | EQUIVALENT |
| CASE-22 方法不匹配 | `POST /v1/article/1` | **404 空 body** | 同 | EQUIVALENT |
| CASE-23 OPTIONS 预检 | `OPTIONS /v1/任意` | 204 空 + CORS 头 | 同 | EQUIVALENT |
| CASE-24 超 10 MiB | — | **裸 413** | 同 | EQUIVALENT |
| CASE-25 尾斜杠 | `/v1/articles/` | 200（`path.Clean`） | 200 | EQUIVALENT |
| CASE-26 并发 | 同一 author 出现在 N 行 | 1 次 `GetUser` | 1 次 | EQUIVALENT |
| CASE-27 事务回滚 | — | 无事务 | 无事务 | N/A |
| CASE-28 缓存命中/未命中 | — | 无缓存 | 无缓存 | N/A |

### 8.3 建议补的探针（本轮**未做**，列为待办）

1. **CASE-17/18 双侧活体对拍**：Go 侧用 `httptest` + 真实 `UploadImageHandler` 注入一个
   `status.Error(codes.Unavailable, …)` 的 fake client，断言 HTTP 503；RAG 侧同理。**这是修 DRIFT-001 的前置**。
2. **真实 Tomcat 探针**：参照 `like-api` 的 `LikeApiRouterErrorRealTomcatTest`，
   起 `RANDOM_PORT` 容器，断言 400 响应的 `Content-Type: text/plain; charset=utf-8`（**带空格**）
   与 `X-Content-Type-Options: nosniff` ⇒ 用于关闭 §9 UNRESOLVED-001。
3. **user-rpc 慢响应探针**：Go 侧把 `UserRpcConf.Timeout` 显式写成 0/2000 并注入慢 client，
   实测首个 `GetUser` 何时返回；RAG 侧注入慢 stub 实测端到端延迟 ⇒ 用于关闭 DRIFT-002。
4. **重复 query key 探针**：`?page=1&page=2` 双侧各跑一次 ⇒ 用于关闭 DRIFT-003。

---

## 9. Unresolved（§37 · 证据不足，不得猜）

### UNRESOLVED-001 —— 400/401/404 响应头在**真实 Tomcat** 下的形状
- **Question**：RAG 侧 400 text/plain 的 `Content-Type` 是否逐字节等于 Go 的 `text/plain; charset=utf-8`（**分号后有空格**）？`nosniff` 是否真的到客户端？
- **Go Evidence**：`net/http.Error` 设 `Content-Type: text/plain; charset=utf-8` + `X-Content-Type-Options: nosniff`。
- **Java Evidence**：`GoStylePlainTextErrors.java:69-75` 用 `ResponseEntity.header(...)` 设同值；`GoStyleJsonBodyLimitFilter.java:110-115` 刻意用 `setHeader` 而非 `setContentType`（注释称 `setContentType` 会被 Tomcat 改写成无空格形式）。
- **Why Unknown**：本组件 50 条测试**全部是 MockMvc**，不经 Tomcat 的 header 规范化。
  同仓 `message-api` / `like-api` 已实测踩过同类坑（`HttpMiddleware` 的 E-1「Content-Type 完全丢失」），
  说明**静态正确 ≠ 客户端收到**。上游 `DRIFT-005` 正是这条。
- **Impact**：低（仅文案字节），但它曾是 E-1 事故的形态。
- **Required Confirmation**：起真实 Tomcat 容器打一次 400 请求，抓原始响应头。

### UNRESOLVED-002 —— **依赖 `article-rpc` 的响应语义（跨组件，需与 `docs/DRIFT/article/rpc.md` 合并判读）**
- **Question**：article-api 的三处安全闸门是否真的有效？
  ① `GetArticle` 是否尊重 `public_only=true`；② `ListArticles` 是否尊重 `public_only=true`；
  ③ `ListArticles` / `GetArticle` 返回的 `status` 是否真的等于 `PUBLISHED(枚举值)`；
  ④ `CreateArticle` 在 `FailedPrecondition` / `DeadlineExceeded` 时，**status 文案**是否真的按
  `create_article_logic.go:87-91` 期望的「冒号分隔、冒号后是 article_id」格式拼（这是约定式协议，无契约保证）。
- **Go Evidence**：`article-rpc` 侧 `internal/logic/*`、`internal/model/article_public.go`（**本轮未读**）。
- **Java Evidence**：`ArticleGetService.java:121-126`、`ArticleListService.java:117-119`、
  `ArticleCreateService.java:144-153`（api 侧的三道兜底与解析器）。
- **Why Unknown**：`article-rpc` 正由**另一个并行会话**审查；本轮**没有**对其做任何验证，
  按纪律**不能替对端背书**。api 侧的兜底是「对端可能失约」的补偿，不是对端正确的证明。
- **Impact**：若对端忽略 `public_only`，api 侧闸门能挡住草稿泄露（这是设计意图）；
  但若对端连 `status` 都不填，api 侧会把**所有文章判成非 PUBLISHED ⇒ 恒 1002 / 恒 5001**。
- **Required Confirmation**：与 `docs/DRIFT/article/rpc.md` 合并判读；建议提升为**跨组件条目**
  （`public_only` 契约由两个组件共同承担，见 §7 各条）。

### UNRESOLVED-003 —— Filter 链的实际 `@Order` 全序
- **Question**：`GoStylePathNormalizingFilter`(HIGHEST) / `ArticleGoStyleCorsFilter`(HIGHEST) /
  `GoStyleMaxConnsFilter`(+1) / `GoStyleCpuSheddingFilter`(+3) / `HttpMiddlewareFilter`(+5) /
  `GoStyleMaxBytesFilter`(+6) / `GoStyleJsonBodyLimitFilter`(+7) 的**实际生效顺序**是否与 Go 链序同构？
- **Java Evidence**：各 Filter 上的 `@Order` 注解（静态可见）。
- **Why Unknown**：两个 Filter 同为 `HIGHEST_PRECEDENCE`，实际先后取决于 bean 名 / 注册序，
  **只有容器起来才知道**。§3.3 同款：「注解存在」≠「生效」。
- **Impact**：顺序错 ⇒ 413/503 是否带 CORS 头、`path.Clean` 是否在鉴权前生效。
- **Required Confirmation**：启动容器打印 `FilterRegistrationBean` 顺序，或补一条装配层断言。

### UNRESOLVED-004 —— `@GrpcClient` 是否真在容器装配 + Nacos 寻址
- **Question**：`ArticleRpcClient` / `UserRpcClient` 的 `@GrpcClient("article-rpc")` 阻塞 stub
  是否真被注入？`discovery:///article-rpc` 在本部署能否解析到实例？
- **Java Evidence**：`ArticleRpcClient.java:52-53`、`application.yml:56-69`
  （`negotiation-type: plaintext` 是必须的，否则 devh 默认 TLS）。
- **Why Unknown**：AGENTS.md §3.3 的 Boot-4 条款要求「凡用 `@XxxListener` 须有装配层测试」。
  本组件**没有**任何装配层测试；50 条测试全部用 `@MockitoBean` 把 client 换掉了 ——
  **恰好把要验证的那一段替换掉了**。
- **Impact**：若装配失败，所有端点在首次调用时报 NPE/IllegalStateException ⇒ 被 `handleAny` 兜成裸 500。
- **Required Confirmation**：起真实 Spring 上下文（不 mock client）断言两个 stub 非 null。

### UNRESOLVED-005 —— 真实 Tomcat 下 405→404 空 body 是否仍无 `Content-Type`
- **Question**：`ResponseEntity.status(404).build()`（`GlobalExceptionHandler.java:314-318`）
  在真实容器里会不会被 Tomcat 补上 `Content-Type`？
- **Why Unknown**：同 UNRESOLVED-001；MockMvc 测不到。
- **Impact**：低（客户端可据此区分两种 404，Go 侧同样区分）。
- **Required Confirmation**：同 UNRESOLVED-001 的真实 Tomcat 探针。

### UNRESOLVED-006 —— 共享层（`com.rag.common`）在 article-api 上的真实生效
- **Question**：`GoStyleGrpcClientBreakerInterceptor` / `GoStyleCpuSheddingFilter` / `GoStyleMaxConnsFilter` /
  `HttpMiddlewareFilter` / `GoStyleJsonBodyConverterConfig` 这 5 个共享件是否**真**被 `scanBasePackages={"…","com.rag.common"}` 扫到？
- **Java Evidence**：`ArticleApiApplication.java:81-84`。
- **Why Unknown**：扫描是运行期行为；且 `HttpMiddleware.java:105-129` 的 worker 池
  （`SynchronousQueue` + 512 上限 + `AbortPolicy`）在饱和时**抛 `RejectedExecutionException`**，
  该分支的 HTTP 输出本轮未验证。
- **Impact**：漏扫 ⇒ 保护层**静默消失**且不报错（AGENTS.md §3.3）。
- **Required Confirmation**：补一条「上下文里这些 Bean 都存在」的装配层测试。

### UNRESOLVED-007 —— `SecurityRpc` 的启动期行为在 `NonBlock:false` 下的差异
- **Question**：若 Go 侧日后给 `SecurityRpcConf` 显式写 `NonBlock: false`，`zrpc.MustNewClient`
  会在 etcd 解析不到 `security.rpc` 时 **panic**，而 RAG 侧无此 client ⇒ **启动失败 vs 启动成功**。
- **Why Unknown**：当前 yaml 未配 ⇒ 走 default `true`（`zrpc/config.go:30`）⇒ 不阻塞 dial。
  结论**依赖默认值**，不是设计对齐。
- **Impact**：当前为零；改动 yaml 后立刻显形。
- **Required Confirmation**：与真源维护者确认 `NonBlock` 是否会变；变更时同步登记为 DRIFT。

### UNRESOLVED-008 —— 上游注释引用的 `DRIFT-024` 在本仓无对应台账
- **Question**：`GlobalExceptionHandler.java:118` 的「DRIFT-024 同款教训（多出来的 Content-Type / nosniff）」
  指向何处？
- **Why Unknown**：`docs/DRIFT/` 全树无 `DRIFT-024` 条目；`docs/DRIFT/_MANIFEST.json` 亦未登记。
- **Impact**：低（只是一条引用注释），但意味着**存在一份未入库的历史漂移清单**，
  可能包含本轮重复发现或遗漏的条目。
- **Required Confirmation**：向实现该层的会话索取原始清单，或全仓检索同款文案。

### UNRESOLVED-009 —— RAG 侧 `dto/UpdateArticleReq.java` 的 Go 行号引用有误
- **Question**：`UpdateArticleReq.java:8` 的 javadoc 写「Go `types.go:61-71`」，
  实际 Go 的 `UpdateArticleReq` 在 `service/article/api/internal/types/types.go:74-83`。
- **Why It Matters**：不是行为差异，是**证据指针错误**。AGENTS.md §3.3 要求证据行号指向消费点；
  一条指错的行号会让后来者按 §3.1 的纪律去核对时读到 `ListArticlesResp`。
- **Required Confirmation**：修正 javadoc 行号（本轮**只读不写源码**，未改）。

### UNRESOLVED-010 —— Go `net/url` 的三个 query 解析行为，RAG 未覆盖（2026-10-24 新增）
- **Question**：`httpx.GetFormValues` 之前还有一层 Go **标准库** `net/url` 的解析，
  它有三条行为会让请求**直接 400**；本轮补的 `GoStyleFormParamCountFilter`
  **刻意只复刻了 go-zero 的 2048 判据**，这三条未覆盖：
  | # | Go 行为 | 出处（Go 1.25 标准库） |
  |---|---|---|
  | ① | `defaultMaxParams = 10000` ⇒ 超限 400「number of URL query parameters exceeded limit」 | `net/url/url.go:1001,1019-1022` |
  | ② | query 里出现 `;` ⇒ 记错并**返 400**「invalid semicolon separator in query」 | `net/url/url.go:1026-1029` |
  | ③ | query 里出现非法 `%` 转义 ⇒ **返 400** | `net/url/url.go:1034-1045` |
- **Why Unknown**：本 filter 按 Go 的**分段规则**处理这三种输入（②③ 跳过该段），
  但**不**复刻 Go 的「返 400」这一层 ⇒ 两侧在②③ 上行为不同。
- **Impact**: 低。②③ 需要客户端发畸形 query；① 需要 >10000 个分段
  （默认配置下 Tomcat 的 8 KB 请求行上限会先拒 ⇒ 不可达）。
- **Required Confirmation**：先量 Tomcat 对 `;` 与非法 `%` 的实际处理
  （Tomcat 自己会 400 吗？还是当字面量放过？），再决定是补进 filter 还是记为签字偏离。

---

## 10. 最终判定（§39 · 18 项逐条）

| # | 检查项 | 结论 | 依据 / 编号 |
|---|---|---|---|
| 1 | 两边的 public behavior 是否一致 | ⚠️ **部分** | 路由/鉴权/信封一致；上传失败状态码不一致（DRIFT-001） |
| 2 | 正常路径是否一致 | ✅ | §4 矩阵 60+ 行 EQUIVALENT；CASE-01 |
| 3 | 边界输入是否一致 | ⚠️ **部分** | 空值/类型/默认值一致；重复 query key 不一致（DRIFT-003、CASE-16） |
| 4 | NotFound / Empty / Null 是否一致 | ✅ | `articles:null`、`secondary_tags:null`、`ext_info:null`、`author_name` 缺席 —— 四处逐条对齐 |
| 5 | Error meaning 是否一致 | ⚠️ **部分** | 业务码全一致；上传路径丢失 gRPC 码语义（DRIFT-001） |
| 6 | Error propagation 是否一致 | ⚠️ **部分** | 业务异常一致；`extractCurrentUserID` 的「200+1005」一致；panic 兜底一致 |
| 7 | External dependency failure 是否一致 | ⚠️ **部分** | article-rpc 各码映射一致；**上传路径不一致**（DRIFT-001）；user-rpc 失败均回落但**超时作用域不同**（DRIFT-002） |
| 8 | Cache hit / miss 是否一致 | ✅ N/A | 两侧无缓存（§6.2） |
| 9 | Transaction boundary 是否一致 | ✅ N/A | 两侧无事务（§6.4） |
| 10 | State transition 是否一致 | ✅ | 无组件级状态（§6.1） |
| 11 | Side effects 是否一致 | ✅ | `incr_view` → rpc `+1`；请求内昵称去重 |
| 12 | Ordering 是否一致 | ⚠️ UNKNOWN | Filter `@Order` 实际生效序未验（UNRESOLVED-003） |
| 13 | Retry 是否一致 | ✅ | 两侧零重试（§6.7） |
| 14 | Timeout scope 是否一致 | ❌ **否** | **DRIFT-002**（`user-rpc` 2s vs 70s） |
| 15 | Concurrency semantics 是否一致 | ✅ | 无共享写状态（§6.5） |
| 16 | Authentication / Authorization 是否一致 | ✅ | 7 端点鉴权边界、401 空 body、1005/1006 语义全一致（§6.3） |
| 17 | Serialization semantics 是否一致 | ⚠️ **基本一致** | 5/6 项一致；`ext_info` 排序在非 BMP key 上有边界差（DRIFT-007）；query 上限缺失（DRIFT-005） |
| 18 | Lifecycle 是否一致 | ⚠️ UNKNOWN | 静态顺序一致；Filter 链与装配未验（UNRESOLVED-003/004/006） |

### 三值结论

```
SEMANTICALLY NOT EQUIVALENT
```

理由（spec §38 要求的归纳链）：

```
Component   article-api
   ↓
Operation   POST /v1/upload（失败路径）
   ↓
Dimension   Error Identity —— HTTP 状态码
   ↓
Go          codes.Unavailable → errcode.CodeFromGrpcError → HTTP 503
   ↓
Java        StatusRuntimeException → GoStyleBadRequestException → HTTP 400
   ↓
Comparison  状态码语义不同；客户端按 ≥500 分流的重试/告警/熔断全部失效
   ↓
Risk        HIGH
```

**次要结论（同一判定下的另外两条）**：
- `GET /v1/article/:id` / `GET /v1/articles` 的 `user-rpc` 调用超时作用域 2s vs 70s（DRIFT-002，Risk MEDIUM-HIGH）。
- 重复 query key 从「取第一个」变成 400（DRIFT-003，Risk MEDIUM）。

### 证据强度声明

| 类别 | 本轮占比 | 说明 |
|---|---|---|
| 静态源码（两侧 + go-zero `v1.10.2` 消费点） | **全部** | 每条 Go 事实都带 `文件:行号`，且行号指向**消费点**（`responses.go:145-159`、`errcode/grpc.go:12-42`、`zrpc/client.go:70-71`、`unmarshaler.go:835-846`、`httpx/util.go:44-45` …） |
| 纯 Mockito / MockMvc | 50 条测试 | **不作为任何 EQUIVALENT 的证据**；仅用于「现状未被绿测锁死」的说明 |
| 双侧活体对拍 | **0 条** | 本组件**从未做过**，本轮也未做（Go 侧未运行、RAG 侧未起真实 Tomcat / 真实 gRPC） |
| 真 PG | 不适用 | api 层无 DB |
| 装配层 / 容器层 | **0 条** | UNRESOLVED-003/004/006 |

⇒ 按 AGENTS.md §6.3 状态表纪律：**未标注证据强度的断言一律按 `UNKNOWN` 处理**；
本台账 §4 矩阵中所有 EQUIVALENT 行的证据强度均为「静态源码」，
**它们是「读 Go 读得对」的证明，不是「双侧行为一致」的证明**。
---

<!-- @fix 2026-10-24 · 由 align/article 工作树产出 · 基线 commit 8422116 -->

## 2026-10-24 修复轮：4 条未修漂移已修 + 1 条新发现，全部经**变异验证**

> 本节是**增量追加**，上文 §0~§10 任何结论**一字未删**（含 §7.1 DRIFT-001 的原始描述）。
> **核对日期 = 2026-10-24。** 基线 commit `8422116`（`align/article` 工作树，从 `do-my-own-gh` 切出）。
>
> **证据强度四档**（比上一轮强一档）：
> **真 MVC 管线（MockMvc）+ Mockito**（本轮）· 纯 Mockito · 静态源码（Go 侧逐行读到消费点）· 双侧活体对拍（**仍 0 条**）。

### 0. 本轮做了什么

| # | 条目 | 上一轮状态 | 本轮状态 | 守门网 | 变异验证 |
|---|---|---|---|---|---|
| **F-1** | `DRIFT-ARTICLE-API-001` 上传失败恒 400 | ❌ 未修 | ✅ **已修** | `ArticleApiMvcPipelineTest` × 3 | ✅ **3/3 转红** |
| **F-1b** | 🆕 **F-1 顺带发现**：熔断打开时 Go 400 / RAG **500** | — | ✅ **已修** | 同上 × 1 | ✅ 转红 |
| **F-2** | `DRIFT-ARTICLE-API-002` `user-rpc` 超时 2s vs 70s | ❌ 未修 | ✅ **已修** | `UserRpcClientDeadlineTest` × 5 | ✅ **2/2 转红** |
| **F-3** | `DRIFT-ARTICLE-API-003` 重复 query key → 400 | ❌ 未修 | ✅ **已修** | `ArticleApiMvcPipelineTest` × 3 | ✅ **2/2 转红** |
| **F-4** | `DRIFT-ARTICLE-API-004` 锁死错误答案的绿测试 | ❌ 未改 | ✅ **已改断言** | 随 F-1 | — |

⇒ `mvn -o -pl service/article/article-api test` ⇒ **60 tests / 0 failures / 0 errors / 0 skipped**（原 50）。
⚠️ 按 §3.3：**「60 条全绿」本身不是证据**；本轮的证据是**下面那 7 条变异全部转红**。

---

### F-1 —— `DRIFT-ARTICLE-API-001` 已修：上传失败按 gRPC 码映射状态码

**Go 侧证据（读到消费点，非推断）**

```
go-zero v1.10.2 · rest/httpx/responses.go:142-159
  if handler == nil {                                  // ← article-api 没有注册 error handler
      if len(fns) > 0 { … }
      else if errcode.IsGrpcError(err) {               // ① 先问「是不是 gRPC 错误」
          http.Error(w, err.Error(), errcode.CodeFromGrpcError(err))   // ② 按 gRPC 码映射
      } else {
          http.Error(w, err.Error(), http.StatusBadRequest)            // ③ 非 gRPC ⇒ 恒 400
      }
  }
```

判据链（逐环实测）：
- `rest/internal/errcode/grpc.go:45-55` `IsGrpcError` ⇒ 判 `err` 是否实现 `GRPCStatus() *status.Status`；
- `rpc/articleservice/article_service.go:76-79` `UploadFile` **原样返回**该错误、**不包装**；
- `zrpc/internal/clientinterceptors/` 的 `duration/prometheus/tracing` 三个拦截器**都是 `return err`**，不包装 ⇒ ② 命中；
- 映射表 `rest/internal/errcode/grpc.go:12-42`：`Unavailable⇒503`、`Internal⇒500`、`DeadlineExceeded⇒504`、
  `ResourceExhausted⇒429`、`NotFound⇒404`、未知码⇒**500**（Go 的 switch **没有 default**，落 `:41`）。

**修法**：`GoStyleGrpcStatusMapper`（新增，逐条复刻映射表）+ `GoStyleHttpErrorException`（新增，携带 HTTP 状态码）
+ `GlobalExceptionHandler#handleGoStyleHttpError`（新增，走 `GoStylePlainTextErrors.text(status, msg)`）。
`ArticleUploadService` 的 `catch (StatusRuntimeException)` 改为抛 `GoStyleHttpErrorException`。

**🔴 body 文案也必须逐字对齐（wire 层可见，上一轮没查到）**

Go 写的是 `err.Error()`，而 grpc-go 的 `*status.Error.Error()`（`internal/status/status.go:211-212`）
转发到 `Status.String()`（`:201-203`）：

```go
fmt.Sprintf("rpc error: code = %s desc = %s", s.Code(), s.Message())
```

⇒ Go 的 body 形如 `rpc error: code = Unavailable desc = minio down`。
⚠️ Java 的 `StatusRuntimeException.getMessage()` 是 `UNAVAILABLE: minio down` ⇒ **字面不同**。
且 Go 的 `codes.Code.String()`（`codes/code_string.go:31+`）用的是 **CamelCase**（`Canceled` / `DeadlineExceeded`），
Java 的 `Status.Code.name()` 是 **SCREAMING_SNAKE** ⇒ 照抄 `name()` 仍不一致。
⇒ 新增 `GoStyleGrpcStatusMapper#goCodeName` 显式维护 Go 的规范名表。

**守门网**：`ArticleApiMvcPipelineTest` 3 条
（`codes.Internal⇒500` + body 逐字断言、`codes.Unavailable⇒503`、非 gRPC⇒400）。

---

### F-1b —— 🆕 顺带发现：**熔断打开**时 Go 是 400、RAG 却是 500

上一轮只查到「gRPC 错误 → 按码映射」，**漏了非 gRPC 错误这一侧**。实测：

- Go：熔断打开时 `breaker.DoWithAcceptableCtx` 返回
  `breaker.ErrServiceUnavailable = errors.New("circuit breaker is open")`（`core/breaker/breaker.go:19`）
  —— **普通 error**，不实现 `GRPCStatus()` ⇒ `IsGrpcError` 为**假** ⇒ 走 ③ 分支 ⇒ **400**。
- RAG：`GoStyleCircuitOpenException`（`service/common`，**刻意**不是 `StatusRuntimeException`）
  ⇒ 原先无人接住 ⇒ 一路落到 `GlobalExceptionHandler#handleAny` ⇒ **裸 500**。

⇒ 修法：`ArticleUploadService` 增接 `catch (GoStyleCircuitOpenException)` ⇒ 400。

> ⚠️ **注意 RAG 侧的熔断拦截器是真装上了的**：
> `GoStyleGrpcClientBreakerInterceptor` 同时带 `@Component` + `@GrpcGlobalClientInterceptor`
> （devh 的 `AnnotationGlobalClientInterceptorConfigurer` **只认这个注解**，不认「是不是 ClientInterceptor」）
> ⇒ 该路径**可达**，不是理论分支。

**守门网**：`uploadNonGrpcFailureIs400`（注入 `GoStyleCircuitOpenException` ⇒ 断言 400 + body 前缀）。

---

### F-2 —— `DRIFT-ARTICLE-API-002` 已修：`user-rpc` 单次调用预算 2s

**Go 侧证据（三段，缺一不可）**

```
① 声明点：go-zero v1.10.2 · zrpc/config.go:31
     Timeout int64 `json:",default=2000"`
② yaml：service/article/api/etc/article-api.yaml:20-24 —— UserRpcConf **没有** Timeout 字段
     ⇒ 走 ① 的 tag 默认 = 2000ms
     （⚠️ 同一份 yaml 里 ArticleRpcConf.Timeout 是**显式** 70000 ⇒ 两个 client 差 35 倍）
③ 消费点：zrpc/client.go:70-71 → zrpc/internal/client.go:83（buildUnaryInterceptors）
        → zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
     ctx, cancel := context.WithTimeout(ctx, t)
```

**🔴 修法上最关键的一点：不能链式 `withDeadlineAfter`**

Go 的 `context.WithTimeout(ctx, 2s)` 语义是**取父子 deadline 中较早的**。
而 grpc-java 的 `io.grpc.CallOptions.withDeadline`（**grpc-api 1.66.0 字节码实测**）是：

```
builder.deadline = deadline;        // ← **直接覆盖**，不是取最小
```

⇒ `applyTo(stub).withDeadlineAfter(2, SECONDS)` 会把 HTTP 剩余预算**顶掉**
（剩 0.5s 时反而给 2s）⇒ 请求可能**超出 HTTP 总预算**。

⇒ 修法：`UserRpcClient#userRpc()` **显式算 min**：

```java
long remaining = GoStyleRequestDeadline.remainingMillis();   // 无预算 ⇒ -1
long effective = remaining < 0L ? 2000L : Math.min(remaining, 2000L);
return userServiceBlockingStub.withDeadlineAfter(effective, TimeUnit.MILLISECONDS);
```

**守门网**：`UserRpcClientDeadlineTest` 5 条
（无预算⇒2s / 预算 10s⇒仍 2s / 预算 500ms⇒500ms / 预算耗尽⇒立即到期 / 常量防漂移）。

---

### F-3 —— `DRIFT-ARTICLE-API-003` 已修：重复 query key 取第一个

**Go 侧证据（两段，顺序不能反）**

```
① rest/httpx/util.go:35-62 —— 同名 key 的多值收成 []string；**空值先被整个丢掉**
     for _, v := range values { if len(v) == 0 { continue }; filtered = append(filtered, v) }
     if len(filtered) > 0 { params[name] = filtered }        // ← 存的是**切片**
② core/mapping/unmarshaler.go:835-846（消费点）—— 目标不是 slice 时取 Index(0)
     if u.opts.fromArray { if fieldKind != Slice && != Array { mapValue = val.Index(0).Interface() } }
```

⚠️ **顺序**：先过滤空值、再取首个 ⇒ `?page=&page=2` 落进 map 的是 `["2"]` ⇒ Go 得 **2**。
若先取首个再过滤，会得到默认值 1 ⇒ 与 Go 不同。

**RAG 侧原状**：`@RequestParam String` ⇒ Spring 把多值**拼成一个字符串**（`"1,2"`）
⇒ `GoFormValues.int32("1,2")` ⇒ `parseInt` 抛 ⇒ **400**。

**修法**：`GoFormValues.first(String[])`（新增）+ controller 的 9 个 query 参数改成 `String[]` 再取首。
⚠️ 这条**不能**只改 `GoFormValues` —— Spring 的拼接发生在**参数解析阶段**，
必须让 controller 拿到**原始数组**才拿得到「第一个」。

**守门网**：`ArticleApiMvcPipelineTest` 3 条
（`?page=1&page=2⇒page=1` / `?incr_view=1&incr_view=0⇒true` / `?page=&page=2⇒page=2`）。

---

### F-4 —— `DRIFT-ARTICLE-API-004` 已改：那条**锁死错误答案**的绿测试

原用例 `uploadRpcFailureIsBareTextPlain400` 的 `@DisplayName` 写
「upload 的 RPC 失败 ⇒ **400 text/plain**（Go 无 error handler）」——
它把 **RAG 的行为**写成了 **Go 的事实**（AGENTS.md §3.3.2 点名的形态）。

⇒ 本轮**先改断言**（按 §3.3「先改/删错误断言，再改实现」的顺序），
拆成 3 条按 gRPC 码分别断言的用例，再改实现。

> ⚠️ **上一轮的 P-1 坑档（`docs/Pitfalls/article/api.md`）早就把这条写清楚了** ——
> 也就是说 **2026-10-23 那轮「记了坑、没修漂移」**。
> ⇒ **坑档 ≠ 修复**；本条是本轮补上的最后一环。

---

### 变异验证（§3.3 硬要求：证明新回归网**非假绿**）

方法：把修复**改回去**（或改成等价错误实现），重跑对应用例，看是否转红。

| # | 变异内容 | 结果 |
|---|---|---|
| 1 | `GoStyleGrpcStatusMapper.httpStatusOf` 恒返回 400 | ✅ **转红**（`expected:<500> but was:<400>`） |
| 2 | `goErrorMessage` 改用 Java 的 `"X: Y"` 格式 | ✅ **转红** |
| 3 | `ArticleUploadService` 不再接住 `GoStyleCircuitOpenException` | ✅ **转红**（`expected:<400> but was:<500>`） |
| 4 | `UserRpcClient` 去掉 `min`（恒 2s）· 预算 500ms 场景 | ✅ **转红**（实际 1999ms） |
| 5 | `UserRpcClient` 去掉 `min`（恒 2s）· 预算耗尽场景 | ✅ **转红**（实际 1999ms） |
| 6 | `GoFormValues.first` 不丢空值（先取首再过滤） | ✅ **转红**（`expected:<2> but was:<1>`） |
| 7 | `GoFormValues.first` 改成取**最后一个** | ✅ **转红**（`expected:<1> but was:<2>`） |

⇒ **7/7 全部转红** ⇒ 本轮新增的回归网**非假绿**。

⚠️ **一条方法论教训**：首轮变异脚本用 `sed` 做多行替换，BSD sed 不支持 `\n` ⇒
把文件改坏导致**编译失败**，脚本把「没跑到测试」误判成「仍全绿」。
⇒ **变异验证脚本必须三态判定**（转红 / 仍全绿 / **变异无效导致编译失败**），
否则会产出一条**假的「假绿」结论**。

---

### 本轮**未**关掉的（保持 OPEN，不得当已修）

| 条目 | 状态 | 说明 |
|---|---|---|
| `DRIFT-ARTICLE-API-005` query 参数总数上限 2048 | 🔴 OPEN（LOW，实际不可达） | 本轮未动 |
| `DRIFT-ARTICLE-API-006` 8–10 MiB body 文案 | 🔴 OPEN（LOW） | 本轮未动 |
| `DRIFT-ARTICLE-API-007` `ext_info` key 排序（非 BMP） | 🔴 OPEN（极低） | 本轮未动 |
| `UNRESOLVED-001` / `-005` 真实 Tomcat 下的 400/401/404 响应头 | 🔴 OPEN | MockMvc 测不到，**必须真容器** |
| `UNRESOLVED-003` Filter 链实际 `@Order` 全序 | 🔴 OPEN | 同为容器级 |
| `UNRESOLVED-004` `@GrpcClient` 真装配 + Nacos 寻址 | 🔴 OPEN | 本轮 60 条测试仍**全部**用 `@MockitoBean` 把 client 换掉了 —— **恰好把要验证的那一段替换掉了** |
| `UNRESOLVED-006` 共享层 `com.rag.common` 真被扫到 | 🔴 OPEN | 同上 |
| `UNRESOLVED-002` 依赖 `article-rpc` 的响应语义 | 🟡 部分关闭 | 本轮**已在 rpc 侧**核对 `public_only` / `status` 的消费方式（见 [`rpc.md`](./rpc.md) 与 [`proto.md`](./proto.md)） |
| `UNRESOLVED-008` 上游注释引用的 `DRIFT-024` | 🔴 OPEN | 仍查无对应台账 |
| `UNRESOLVED-009` `UpdateArticleReq.java:8` 的 Go 行号引用有误 | 🔴 OPEN | 本轮**只读不写**该文件；建议下一轮顺手改 |

### 判定变化

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-001` 上传失败状态码 | ❌ SEMANTIC MISMATCH（HIGH） | ✅ 已修 + 变异验证 |
| `DRIFT-002` `user-rpc` 超时作用域 | ❌ SEMANTIC MISMATCH（MEDIUM-HIGH） | ✅ 已修 + 变异验证 |
| `DRIFT-003` 重复 query key | ❌ SEMANTIC MISMATCH（MEDIUM） | ✅ 已修 + 变异验证 |
| `DRIFT-004` 锁死错误答案的绿测试 | 🔴 OBSERVABLE DIFFERENCE | ✅ 已改断言 |
| **F-1b** 熔断打开时的状态码 | ❌ **未记录**（RAG 500 vs Go 400） | ✅ 已修 + 变异验证 |
| 其余 005~007 / UNRESOLVED-* | OPEN | **OPEN** |
| **组件判定** | `SEMANTICALLY NOT EQUIVALENT` | **`SEMANTICALLY NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮只关掉 4 条**已记录**漂移 + 1 条**新发现**；
`DRIFT-005~007` 与 8 条 `UNRESOLVED-*` 仍在，且**证据强度上限仍是 MockMvc/Mockito**
（**无双侧活体对拍、无真实 Tomcat、无装配层测试**）⇒
本轮全部 ✅ 一律读作「**在 MockMvc 级证据下一致**」，不是「已闭环」。

---

<!-- @fix 2026-10-24 · 第二轮 · 由 align/article 工作树产出 · 基线 commit 8422116 -->

## 2026-10-24 第二轮：**装配层真容器测试** —— 关闭 5 条 `UNRESOLVED`，并首次确认 1 条真漂移

> **核对日期 = 2026-10-24。** 基线 commit `8422116`（`align/article` 工作树）。
> 新增测试类：`ArticleApiAssemblyWiringTest`（7 条）。
> **证据强度：真容器（`webEnvironment = RANDOM_PORT` ⇒ 真 Tomcat）+ 真 Spring 上下文 + 真实时钟**
> + **变异验证 4/4 转红**。

### 0. 为什么这一轮必须做

上一轮的 60 条测试**全部**是 `@SpringBootTest + @AutoConfigureMockMvc + @MockitoBean`，
而 `@MockitoBean` **恰好把要验证的那一段替换掉了**：

| 台账条目 | 为什么 MockMvc/Mockito 测不到 |
|---|---|
| `UNRESOLVED-004` `@GrpcClient` 是否真装配 | client 被 `@MockitoBean` 整个换掉 |
| `UNRESOLVED-006` 共享层是否真被扫到 | 需要看**真实**上下文里的 Bean 集合 |
| `UNRESOLVED-003` Filter 链实际 `@Order` 全序 | MockMvc 不走 Filter 链 |
| `UNRESOLVED-001` / `-005` 400/401/404 响应头 | MockMvc **不经过 Tomcat 的 header 规范化** |

⇒ 本轮新增的测试类**刻意不 mock 任何东西**，gRPC channel 指向必然连不上的
`static://127.0.0.1:1`（只验证装配，不发调用），
HTTP 侧用 **JDK 的 `HttpClient`**（绕开任何框架再加工，断言的就是客户端真正收到的字节）。

### 1. ✅ `UNRESOLVED-004` 已关闭 —— `@GrpcClient` 真装配

| 项 | 值 |
|---|---|
| 断言 | `articleRpcClient.articleRpc()` 与 `userRpcClient.userRpc()` **非 null** |
| 变异 | 摘掉 `@GrpcClient("article-rpc")` 注解 ⇒ 字段不再被注入 ⇒ **转红** ✅ |

⚠️ 附带确认：devh 的 `@GrpcClient` 在 **Bean 初始化期**就建 channel（不是懒建）
⇒ 测试环境没有 Nacos 时 `discovery://` 解析失败 ⇒ **上下文起不来**
⇒ 必须覆盖成 `static://`。
⚠️ 另一条：**必须整个关掉服务发现**（`spring.cloud.discovery.enabled=false`）——
否则 Nacos 客户端连不上会让 `webServerStartStop` 直接失败
（`Client not connected, current status:STARTING`），**上下文根本起不来**。

### 2. ✅ `UNRESOLVED-006` 已关闭 —— 共享层真被扫到

断言 6 个共享件 Bean 存在 + **`GoStyleJsonBodyConverter` 真在 MVC 的 converter 链里**。

⚠️ **`GoStyleJsonBodyConverter` 本身不是 Bean**（它在
`GoStyleJsonBodyConverterConfig.extendMessageConverters` 里 new 出来）
⇒ 断言「Bean 存在」测不到「它真被插进了转换器链」
⇒ 必须查 `RequestMappingHandlerAdapter.getMessageConverters()`。
（第一版写成了 `ctx.getBean(GoStyleJsonBodyConverter.class)` ⇒ 直接 `NoSuchBeanDefinition`。）

| 变异 | 结果 |
|---|---|
| `scanBasePackages` 去掉 `"com.rag.common"` | ✅ **转红**（保护层整体消失） |

### 3. ✅ `UNRESOLVED-003` 已关闭 —— Filter 链的**实际生效顺序**

**Go 出处**（go-zero v1.10.2 · `rest/engine.go:130-166` `buildChainWithNativeMiddlewares`）：

```go
chn := chain.New()
chn = chn.Append(TraceHandler)          // ①
chn = chn.Append(logHandler)             // ②
chn = chn.Append(PrometheusHandler)      // ③
chn = chn.Append(MaxConnsHandler)        // ④
chn = chn.Append(BreakerHandler)         // ⑤
chn = chn.Append(SheddingHandler)        // ⑥
chn = chn.Append(TimeoutHandler)         // ⑦
chn = chn.Append(RecoverHandler)         // ⑧
chn = chn.Append(MetricHandler)          // ⑨
chn = chn.Append(MaxBytesHandler)        // ⑩
chn = chn.Append(GunzipHandler)          // ⑪
```

⇒ 与本组件相关的子序列：**MaxConns → Breaker → Shedding → Timeout → MaxBytes**。

**真容器实测链序（2026-10-24）**：

```
1. ArticleGoStyleCorsFilter           @Order(HIGHEST_PRECEDENCE)
2. GoStylePathNormalizingFilter       @Order(HIGHEST_PRECEDENCE)
3. GoStyleMaxConnsFilter              @Order(HIGHEST+1)
4. GoStyleBreakerFilter               @Order(HIGHEST+2)
5. GoStyleCpuSheddingFilter           @Order(HIGHEST+3)
6. HttpMiddlewareFilter               @Order(HIGHEST+5)
7. GoStyleMaxBytesFilter              @Order(HIGHEST+6)
8. GoStyleJsonBodyLimitFilter         @Order(HIGHEST+7)
```

⇒ **3→8 与 Go 的链序逐条一致** ✅
⇒ **1、2 两位与 Go 也一致**：Go 侧 `WithCors`（`server.go:148-153`）包装的是 **router**，
而 `path.Clean`（`patrouter.go:48,60`）在 router **内部**
⇒ Go = **CORS 在外、path.Clean 在内** ⇒ RAG 实测同构 ✅（**不是巧合**）。

> ⚠️ **前两位同为 `@Order(HIGHEST_PRECEDENCE)`** ⇒ 它们的**相对**顺序由 **Bean 注册序**决定，
> **不是 `@Order` 表达的** ⇒ 这一条只能靠**真容器**测出来。
> ⇒ 测试里把**完整链序**钉死，既是回归网，也把「相对顺序是容器决定的」这件事留了痕。

| 变异 | 结果 |
|---|---|
| 把 `GoStyleCpuSheddingFilter` 的 `@Order` 从 `+3` 改成 `+9` | ✅ **转红**（链序被打乱） |

> 🔴 **本条变异第一次判成「假绿」—— 是我的变异工装有问题**：
> 被变异的是 `service/common`，而命令是 `mvn -pl service/article/article-api test`
> ⇒ **不重编 `service/common`**，跑的是本地仓库里的**旧 jar** ⇒ 变异根本没生效。
> ⇒ 加 `-am`（并补 `-Dsurefire.failIfNoSpecifiedTests=false`）后才真正转红。
> ⇒ **教训：变异一个非目标模块的源码时，必须确认它被重编了**，否则会产出一条**假的「仍全绿」**。
> ⇒ 已记入 [`docs/Pitfalls/article/api.md`](../../Pitfalls/article/api.md)。

### 4. 🔴 `UNRESOLVED-001` / `-005` 已关闭 —— **并首次确认一条真漂移**

真 Tomcat 实测结果：

| 场景 | Go | RAG 实测 | 判定 |
|---|---|---|---|
| 400 的 body | `http.Error` ⇒ 裸文案 + `\n` | ✅ 一致 | ✅ |
| 400 的 `X-Content-Type-Options` | `nosniff` | ✅ 一致 | ✅ |
| 400/404 的 **`Content-Type`** | `text/plain; charset=utf-8`（**分号后有空格**） | `text/plain;charset=utf-8`（**无空格**） | 🔴 **差一个空格** |
| 401 的 body | 空 | ✅ 空 | ✅ |
| 401 的 `Content-Type` | **无**（Go 只 `WriteHeader(401)`） | ✅ 无 | ✅ |
| 404 的 body | `404 page not found\n` | ✅ 一致 | ✅ |
| 方法不匹配 | **404 空 body**（不是 405） | ✅ 一致 | ✅ |

⇒ **`UNRESOLVED-001` 从 `UNKNOWN` 升级为「已确认的 LOW OBSERVABLE DIFFERENCE」**：
`Content-Type` 的参数分隔符后少一个空格。

**根因（已定位到具体机制，不是推断）**

1. Go 的 `net/http.Error`（`net/http/server.go`）写的是**字面量**
   `h.Set("Content-Type", "text/plain; charset=utf-8")` ⇒ **带空格**。
2. RAG 侧 `GoStylePlainTextErrors` 写的**也是**带空格的同一个字面量
   （`ResponseEntity.header(CONTENT_TYPE, "text/plain; charset=utf-8")`）。
3. ⚠️ **但这个原值根本没到达客户端** ——
   Spring 的 `ServletServerHttpResponse.writeHeaders()` 在
   `servletResponse.getContentType() == null` 时会调
   `setContentType(mediaType.toString())`，
   而 **Java 的 `MediaType.toString()` 不在分号后加空格**
   ⇒ 原始 header 被**覆盖**成 `text/plain;charset=utf-8`。
4. **实测证明（对照变异）**：把 RAG 的常量从带空格改成无空格 ⇒ **输出字节不变** ⇒
   证明「原值不过那层归一化就出不来」。

| 变异 | 结果 | 含义 |
|---|---|---|
| 把常量改成 `"text/plain"`（去掉 charset） | ✅ **转红** | 测试是**真网**（输出字节真的变了会被抓到） |
| 把常量改成 `"text/plain;charset=utf-8"`（去掉空格） | ⚪ **仍全绿**（**预期**） | ⇒ **证明 RAG 写的原值到不了客户端** |

> ⚠️ **上游只在一处绕过了这个归一化**：
> `GoStyleJsonBodyLimitFilter`（Filter 里用 `setHeader` 裸写）能保住空格，
> 而**异常处理这条路**（`ResponseEntity` ⇒ Spring 写响应）**没绕过** ⇒ 这就是差异的来源。

**为什么不修（记录理由，供后续决策）**

- 该差异**语义等价**：按 RFC 9110，`;` 后的可选空白（OWS）不是语法的一部分
  ⇒ **任何** HTTP 客户端解析两者一致。
- 要修必须**绕过 Spring/Tomcat 的 `setContentType` 路径**（例如在异常处理器里直接写
  `HttpServletResponse`），那会动到**本组件风险最高的一条路径**（错误写出），
  并连带影响 `GlobalResponseAdvice` 的「放行 4」分支与 401/404 空 body 路径。
- ⇒ 判定：**已确认的 LOW OBSERVABLE DIFFERENCE，本轮不修**。
  测试里用**characterization test** 锁住实测现状，且 `@DisplayName` 明说「与 Go 差一个空格」
  —— **不是把 RAG 现状写成 Go 的事实**。

### 5. 判定变化

| 项 | 修复前 | 修复后 |
|---|---|---|
| `UNRESOLVED-001` / `-005` 真 Tomcat 响应头 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭**（并确认 1 条 LOW 漂移） |
| `UNRESOLVED-003` Filter 链实际顺序 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭**（实测链序已钉死） |
| `UNRESOLVED-004` `@GrpcClient` 真装配 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭** |
| `UNRESOLVED-006` 共享层真被扫到 | 🔴 OPEN（UNKNOWN） | ✅ **已关闭** |
| `UNRESOLVED-002` 依赖 `article-rpc` 的响应语义 | 🟡 部分关闭 | 🟡 部分关闭（本轮在 rpc 侧核对了 `public_only`/`status` 的消费方式） |
| `UNRESOLVED-007` `SecurityRpc` 的 `NonBlock:false` 差异 | 🔴 OPEN | 🔴 OPEN（当前 yaml 未配 ⇒ 影响为零） |
| `UNRESOLVED-008` 上游注释引用的 `DRIFT-024` | 🔴 OPEN | 🔴 OPEN（仍查无对应台账） |
| `UNRESOLVED-009` `UpdateArticleReq.java:8` 的 Go 行号引用有误 | 🔴 OPEN | 🔴 OPEN（本轮只读不写该文件） |
| `DRIFT-005~007` | 🔴 OPEN | 🔴 OPEN |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮关掉的是 **5 条「从未验证」的 UNRESOLVED**（证据强度提升），
**没有关掉任何一条 DRIFT**；另**新增确认 1 条 LOW OBSERVABLE DIFFERENCE**（`Content-Type` 空格）。
且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）。

### 6. ✅ `UNRESOLVED-009` 已关闭 —— javadoc 的 Go 行号引用更正

`dto/UpdateArticleReq.java:8` 原写「Go `types.go:61-71` 的 `UpdateArticleReq`」，
但实测 **`types.go:61-71` 是 `ListArticlesReq` / `ListArticlesResp`**，
`UpdateArticleReq` 的真实位置是 **`types.go:74-83`**（2026-10-24 实测确认）。

⇒ 已改。⚠️ 这条本身**不是**行为差异，是**证据指针错误** ——
但按 AGENTS.md §3.3「证据行号指向消费点」，一条指错的行号会让后来者
按 §3.1 的纪律去核对时**读到另一个结构体** ⇒ 属于「必须顺手修掉」的那一类。

### 7. 本轮**未**关掉的（保持 OPEN）

| 条目 | 状态 | 说明 |
|---|---|---|
| `DRIFT-005` query 参数总数上限 2048 | 🟡 **部分关闭**（2026-10-24） | 🔴 **原定性（「RAG 无上限」）已被真容器实测推翻**：Tomcat 的 `server.max-http-request-header-size`（默认 8 KB）**更早**触发 ⇒ **状态码两侧都是 400**，且 RAG 阈值**更严**（≈2040 < Go 的 2049）⇒ 不存在 DoS 暴露。已**补应用层 Go 语义闸门**（`GoStyleFormParamCountFilter`），真容器实测 body 与 Go **逐字节一致**（2/2 变异验证）。🟡 **残留**：默认部署下拒绝发生在连接器层 ⇒ body 是 Tomcat 的 HTML 页（Go 是 text/plain）⇒ **LOW，决定不修** |
| `DRIFT-006` 8–10 MiB body 文案 | 🔴 OPEN（LOW） | 可观察契约相同（400 + text/plain + nosniff），差异仅在 body 文案（Go 是截断后解析失败 ⇒ `unexpected EOF`） |
| `DRIFT-007` `ext_info` key 排序（非 BMP） | ✅ **已修**（2026-10-24） | 已按**码点序**比较（= Go 的 UTF-8 字节序），见本文件末尾「第三轮」节；`DRIFT-005` / `DRIFT-006` 仍 🔴 OPEN |
| `UNRESOLVED-002` 依赖 `article-rpc` 的响应语义 | 🟡 部分关闭 | rpc 侧已核过 `public_only` / `status` 的消费方式（见 [`proto.md`](./proto.md)）；仍缺**跨栈活体对拍** |
| `UNRESOLVED-007` `SecurityRpc` 的 `NonBlock:false` | 🔴 OPEN（当前影响为零） | 结论依赖 go-zero 的 tag 默认值，不是设计对齐 |
| `UNRESOLVED-008` 上游注释引用的 `DRIFT-024` | 🔴 OPEN | `docs/DRIFT/` 全树仍查无此编号 ⇒ 存在一份未入库的历史漂移清单 |
| 🔴 **新增确认**：`Content-Type` 少一个空格 | 🟡 **已确认为 LOW OBSERVABLE DIFFERENCE** | 本轮首次由真 Tomcat 确认；**决定不修**（理由见上节），已用 characterization test 锁住实测现状 |

---

<!-- @fix 2026-10-24 第三轮 · 由 align/article 工作树产出 · 基线 commit e440d37 -->

## 2026-10-24 第三轮：`DRIFT-ARTICLE-API-007` 已修（`ext_info` key 按**码点序**输出）

### ✅ FIX-09 —— `DRIFT-ARTICLE-API-007` 已修（排序的**非 BMP 边界**）

#### Go 出处（消费点，Go 1.25 **标准库**，不是 go-zero）

```
$GOROOT/src/encoding/json/encode.go:805-807
  slices.SortFunc(sv, func(i, j reflectWithString) int {
      return strings.Compare(i.ks, j.ks)      // ← 逐字节比 UTF-8
  })
```

⚠️ **台账原引的 `encode.go:805` 是 Go 标准库 `encoding/json`**，不是 go-zero
（go-zero v1.10.2 的 `core/mapping/` 下**没有** `encode.go`；只有 `marshaler.go` / `unmarshaler.go`）。
⇒ 本条已把出处更正到标准库的具体文件与行号。

⇒ `strings.Compare` 是**逐字节**比较 UTF-8 ⇒ 等价于**码点序**。

#### Java 侧（修复前）

`ArticleApiSupport.cloneStringMap` 用 `new TreeMap<>(source)` ⇒ 自然序 = `String.compareTo`
= **UTF-16 码元序**。

#### 🔴 差异只在**非 BMP** 字符上出现

- 对 **BMP 内**字符：UTF-16 码元序 == 码点序 == UTF-8 字节序 ⇒ 两侧**一致** ⇒ ASCII 用例测不出来。
- 对**非 BMP**（增补平面，如 U+1F600 😀）：UTF-16 把它拆成代理对 `D83D DE00`（落在 U+D800–U+DFFF），
  **排在 U+E000–U+FFFF 之前**；码点序里它排在它们**之后** ⇒ 两侧**顺序不同**。

#### 双侧探针实测（2026-10-24，Go 1.25.11 + JDK 21）

同一组 key：`a` / `中`(U+4E2D) / U+E000 / U+FFFD / `😀`(U+1F600)

| 实现 | 顺序 |
|---|---|
| Go `encoding/json` | `a, 中, U+E000, U+FFFD, 😀` ← **期望** |
| Java `TreeMap` 自然序（修复前） | `a, 中, 😀, U+E000, U+FFFD` ← ❌ |
| Java `TreeMap` + 码点比较器（修复后） | `a, 中, U+E000, U+FFFD, 😀` ← ✅ 与 Go 一致 |

#### 修复

`ArticleApiSupport` 新增 `CODE_POINT_ORDER` 比较器（逐码点比较，短者为前缀时短者在前），
`cloneStringMap` 改为 `new TreeMap<>(CODE_POINT_ORDER)` + `putAll`。

⚠️ 比较器与 `equals` **自洽**（两个不同的字符串码点序列必不同）⇒ TreeMap 不会出现"键冲突丢数据"。

#### 证据

- **双侧探针**：Go 侧 `encoding/json.Marshal` 与 Java 侧两种比较器同跑（上面的表）⇒ 差异是**实测**的，不是推理的。
- **回归网**：`ArticleApiMvcPipelineTest#extInfoKeysWithNonBmpCharactersUseCodePointOrder`
  （真 MockMvc 管线 + 真 Jackson 序列化 + 真 proto map ⇒ 非 BMP key 走完整条路）。
  ⚠️ 读 body 必须用 `getContentAsByteArray()` + **UTF-8** 解码：
  `MockHttpServletResponse.getContentAsString()` 用的是 response 的 character encoding
  （没带 charset 时是 ISO-8859-1）⇒ 非 ASCII 会乱码。
- **变异验证（三态判定）**：`MUT-RED`
  —— 把 `cloneStringMap` 改回 `new TreeMap<>(source)`（自然序）⇒ 用例转红。
- **模块级**：`ArticleApiMvcPipelineTest` **39 条全绿**（原 38 ⇒ +1）；
  `article-api` **68 tests / 0 failures / 0 errors / 0 skipped**（原 67 ⇒ +1）；
  连同 `article-rpc` 115 + `article-common` 15 ⇒ **article 域合计 198 / 0 / 0 / 0**（`BUILD SUCCESS`）。

#### 判定变化（FIX-09）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-API-007` `ext_info` key 排序（非 BMP） | ❌ OBSERVABLE DIFFERENCE（极低） | ✅ **已修** + 双侧探针 + 变异验证 |
| `DRIFT-005` query 参数总数上限 2048 | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `DRIFT-006` 8–10 MiB body 文案 | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `UNRESOLVED-002` / `007` / `008` | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `Content-Type` 空格（已确认不修） | 🟡 LOW OBSERVABLE DIFFERENCE | 🟡 **不变**（已用 characterization test 锁住） |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

⚠️ **判定不变的理由**：本轮只修掉 1 条**极低**风险漂移；
`DRIFT-005` / `DRIFT-006` 与 3 条 `UNRESOLVED` 仍在，
且**无双侧活体对拍**（Go 侧仍需 `etcd:2379`）⇒ 本条的 ✅ 读作「**在双侧探针证据下一致**」。

#### 📌 一条可复用的元教训

**"ASCII 用例全绿"不等于"排序语义一致"。**
⇒ 对"排序/比较"类契约，测试**必须**挑一个**两侧会分歧的输入**。
本例的分歧点是**代理对**（非 BMP），需要刻意构造 —— 随手写的用例几乎必然落在 BMP 内，
于是"两侧一致"被**假绿**地确认了。
⇒ 对照本轮 `DRIFT-010`（直方图桶）：**同一类错误的两面** ——
一个测了名字没测桶，一个测了 BMP 没测非 BMP。
⇒ **通用判据**：写"两侧应当一致"的用例前，先问一句
**「什么样的输入会让两侧不一致？」**，并把它写进用例。

---

<!-- @fix 2026-10-24 第四轮 · 由 align/article 工作树产出 · 基线 commit bedd9a5 -->

## 2026-10-24 第四轮：`DRIFT-ARTICLE-API-005`（query 参数上限）—— 🔴 **台账原定性被实测推翻**

### 🔴 先说结论：台账原写的「RAG **无上限**」是**错的**

原台账（§4 对照表 + §7「本轮未关掉的」表）写：

> `DRIFT-005` query 参数总数上限 2048 —— Go `httpx/util.go:15,48-53` 累计超 2048 个非空值即 400；**RAG 无上限**。

**2026-10-24 真 Tomcat 实测推翻了后半句**：

| 场景（真 Tomcat，默认配置） | 实测 |
|---|---|
| 2049 个非空 query 值（≈ 8196 字节） | **400**，body = **Tomcat 的 HTML 错误页** |
| **2048** 个非空 query 值（≈ 8192 字节） | **400**（同样） |
| 3000 个**空值** query 参数（≈ 6000 字节） | 请求**通过** ⇒ 走到 controller ⇒ 下游 gRPC 不可达 ⇒ 500 |

⇒ **RAG 并非"无上限"**：Tomcat 自己在**连接器层**就拒了（Boot 4 的
`server.max-http-request-header-size`，默认 **8 KB**）。
⇒ 而且 **2048 个（Go 允许的上限）在 RAG 默认配置下就已经被拒** ⇒
**RAG 的阈值比 Go 更严**（≈2040 个值 < Go 的 2049）⇒ **不存在"无上限解析"的 DoS 暴露**。

⇒ 因此原来的定性「RAG 无上限 ⇒ OBSERVABLE DIFFERENCE」**站不住**，
真实的差异只剩**响应体形状**（见下）。

### ✅ FIX-10 —— 新增 `GoStyleFormParamCountFilter`（Go 的 2048 判据落到应用层）

**Go 出处（逐行，消费点）**

```
go-zero@v1.10.2/rest/httpx/util.go:11-17
  const (
      // most servers and clients have a limit of 8192 bytes (8 KB)
      // one parameter at least take 4 chars, for example `?a=b&c=d`
      maxFormParamCount = 2048
  )

go-zero@v1.10.2/rest/httpx/util.go:44-56  GetFormValues
  for name, values := range r.Form {
      for _, v := range values {
          if len(v) == 0 { continue }                             // ← 空值不计数
          if n < maxFormParamCount { filtered = append(filtered, v); n++ }
          else { return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode()) }
      }
  }
```

调用链：handler 的 `httpx.Parse`（`internal/handler/article/*.go:21`）→ `httpx.ParseForm`
→ `httpx.GetFormValues` ⇒ 超限 ⇒ `httpx.ErrorCtx` → `httpx/responses.go:142-159`
（未注册 error handler ⇒ `handler == nil`；该 error 非 gRPC error ⇒ 走 else 分支）
→ `http.Error(w, err.Error(), 400)`
⇒ **400** + text/plain + nosniff + body = `"too many form values, error: " + r.Form.Encode()` + `"\n"`。

**实现**：`service/article/article-api/.../config/GoStyleFormParamCountFilter.java`
（`@Order(HIGHEST_PRECEDENCE + 8)`，紧贴 controller —— Go 侧这个上限在**路由 handler 内**，
即所有中间件之后 ⇒ Java 侧放在最后一个 filter 位最贴近）。

⚠️ **刻意放在 api 自己的 config 包**，**不**动 `service/common`（被 16 个组件依赖，
与 `align/common` 工作树并发修改会冲突，AGENTS.md §5.3）。

**逐条复刻的 Go 语义**

| 语义 | Go 出处 | 本实现 |
|---|---|---|
| 上限 2048（数**非空值**） | `util.go:15,48-53` | `MAX_FORM_PARAM_COUNT = 2048` + `countNonEmptyValues` |
| 分段规则（`&` 切分、`;` 跳过、空 key 跳过、首个 `=` 切分、解码失败跳过） | `net/url/url.go:1019-1054` | `parseGoQuery` |
| 错误 body 的 `r.Form.Encode()` | `net/url/url.go:1055-1073` | `encodeGoValues` |
| `QueryEscape`（`A-Za-z0-9-_.~` 不转义、空格 ⇒ `+`、其余 ⇒ `%XX` 大写） | `url.go:108-161,282-284` | `queryEscape` |
| key 排序 = **字节序**（= 码点序） | `url.go:1060` `slices.Sorted` | `compareByCodePoint`（与 `DRIFT-ARTICLE-API-007` 同一个坑） |
| `http.Error` 的响应形状 | `net/http/server.go` | `writeGoStyleBadRequest`（与 `GoStyleJsonBodyLimitFilter` 同款） |

#### 证据（真 Tomcat + 真 HTTP）

| 用例 | 断言 |
|---|---|
| `tooManyFormValuesIsGoShapedBadRequest` | 2049 个非空值 ⇒ **400** + body **逐字节** = `"too many form values, error: " + r.Form.Encode() + "\n"`（`a=1` × 2049）✅ |
| `encodedFormMatchesGoUrlValuesEncode` | body 里的 `r.Form.Encode()` = `a=%E4%B8%AD&a=~&a=%2A&b=a+b&z=1&…` —— **key 字节序** + Go 的转义 ✅ |
| `exactlyMaxFormValuesIsAccepted` | 恰好 2048 ⇒ **不**触发（Go 的判据是 `n < 2048`）✅ |
| `emptyValuesAreNotCounted` | 3000 个**空值** ⇒ **不**触发（Go 的 `len(v)==0 ⇒ continue`）✅ |

⚠️ **Go 探针实测**（Go 1.25.11，`net/url`）用来钉死转义规则：
`url.QueryEscape("*") == "%2A"`（`*` **不在** Go 的 unreserved 集合里 ⇒ 要转义）、
`"~"` 不转义、`" "` ⇒ `"+"`、`"中"` ⇒ `"%E4%B8%AD"`；
`url.Values{a:[中,~,*], b:[a b]}.Encode() == "a=%E4%B8%AD&a=~&a=%2A&b=a+b"`。
⇒ **本实现与 Go 的 `Values.Encode()` 逐字节一致。**

#### 变异验证（三态判定）

| # | 变异内容 | 结果 |
|---|---|---|
| 1 | `MAX_FORM_PARAM_COUNT` 2048 ⇒ 2047 | ✅ **转红**（"2048 个值恰好在上限内"用例） |
| 2 | 不再跳过空值（把 `if (!v.isEmpty())` 去掉） | ✅ **转红**（"3000 个空值不该被拒"用例） |

⇒ **2/2 转红** ⇒ 守门网**非假绿**。

**测试计数**：`ArticleApiFormParamLimitTest` **4 条全绿**（新建，真 Tomcat + 真 HTTP）；
`ArticleApiAssemblyWiringTest` **10 条全绿**（原 7 ⇒ +3：链序新增一个 filter + 两条 DRIFT-005 事实）；
`article-api` **75 tests / 0 failures / 0 errors / 0 skipped**（原 67 ⇒ +8）；
连同 `article-rpc` 115 + `article-common` 15 ⇒ **article 域合计 205 / 0 / 0 / 0**（`BUILD SUCCESS`）。

### 判定变化（FIX-10）

| 项 | 修复前 | 修复后 |
|---|---|---|
| `DRIFT-ARTICLE-API-005` 的**定性** | 「RAG **无上限**」 ⇒ OBSERVABLE DIFFERENCE | 🔴 **原定性被推翻**：RAG 有上限（Tomcat 的 8 KB，更严）；真实差异只剩**响应体形状** |
| 应用层的 Go 2048 判据 | 🔴 无 | ✅ **已实现**（`GoStyleFormParamCountFilter`，真容器实测 body 与 Go 逐字节一致） |
| 默认部署下的 400 **body** | Tomcat 的 HTML 错误页 | Tomcat 的 HTML 错误页（**不变**）⇒ 🟡 **LOW OBSERVABLE DIFFERENCE，决定不修** |
| `DRIFT-006` 8–10 MiB body 文案 | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| `UNRESOLVED-002` / `007` / `008` | 🔴 OPEN | 🔴 **OPEN**（本轮未动） |
| **组件判定** | `NOT EQUIVALENT` | **`NOT EQUIVALENT`（不变）** |

#### ⚠️ 为什么默认部署下的 **body 差异不修**

默认配置下，超长 query 在**连接器层**被拒 ⇒ 产出的是 Tomcat 的 HTML 错误页
（`<!doctype html>…HTTP Status 400 – Bad Request`），而 Go 是 text/plain 的
`too many form values, error: …`。
⇒ 要改它就得替换 Tomcat 的错误页产出（`ErrorReportValve`）⇒
**风险远大于收益**（且属于容器级改动，不是本组件该做的事）。
⇒ 登记为 **LOW OBSERVABLE DIFFERENCE**，并用 characterization test 钉住实测现状。
⚠️ **状态码本身两侧已一致（都 400）** ⇒ 这一条**不是**「无上限」问题。

#### ⚠️ 本条**未**覆盖的相邻 Go 行为（另立，不在本条范围）

| Go 行为 | 出处 | RAG 现状 |
|---|---|---|
| `net/url` 自己的 `defaultMaxParams = 10000` ⇒ 超限 400「number of URL query parameters exceeded limit」 | `url.go:1001,1020-1022` | Tomcat 的 `maxParameterCount` 默认也是 10000，但**判据不同**（数"参数"vs 数"`&` 分段"） |
| query 里出现 `;` ⇒ Go 记错并**返 400**「invalid semicolon separator in query」 | `url.go:1026-1029` | 本 filter 按 Go 的分段规则**跳过**该段，**不返 400** |
| query 里出现非法 `%` 转义 ⇒ Go **返 400** | `url.go:1034-1045` | 本 filter **跳过**该段，**不返 400** |

⇒ 这三条都是**独立的**相邻差异，未修 ⇒ 已在 §9 的 UNRESOLVED 里登记方向。

#### 📌 一条可复用的元教训

> **台账写「RAG 无上限」之前，应该问一句：「RAG 所在的那一层，会不会已经有上限？」**
>
> 本轮的教训链：
> ① 台账凭**源码**判定"RAG 无上限"（Spring 的 `@RequestParam` 确实不限制总数）；
> ② 补真容器用例时才发现 —— **Tomcat 在连接器层就先拒了**，而且**比 Go 更严**；
> ③ 于是这条漂移从「DoS 暴露」降级成「响应体形状差异」。
>
> ⇒ 对照 AGENTS.md §3.1「不要拿未验证的结论当基准」：
> **"RAG 侧没有 X" 这类断言，必须在**运行时**验证，而不是只看 RAG 的源码** ——
> 因为 RAG 跑在**容器**里，容器自带一堆上限。
> ⇒ 同 AGENTS.md §3.3.1「配置里没写 ≠ 没开」的镜像版：
> **「你的代码里没写 ≠ 运行时没有」**。
