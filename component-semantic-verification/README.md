# article-api 组件语义一致性验证报告

**验证对象**：`article-api`（Go → Spring Boot 迁移后的 HTTP gateway，7 个端点）
**Go 真源**：`Sea-RideTheWind_golang/service/article/api` + `service/article/common/errmsg` + `service/common/{response,observability,logger}`
**框架真源**：`go-zero v1.10.2`（`rest/` `core/mapping/` `core/jsonx/` `core/conf/`）——框架 tag 里的默认值、`engine.go` 的链序、`httpx` 的错误处理都属于行为定义
**Spring 侧**：`RAG/service/article/article-api` + `RAG/service/common`（共享实现）
**验证日期**：2026-10-23
**验证者**：Verifier（独立验证，未修改任何源码）

---

## 0. 最终判定

> # `SEMANTICALLY NOT EQUIVALENT`
>
> 存在 **3 项会改变系统行为的差异**（DRIFT-001 / DRIFT-002 / DRIFT-003）与 **4 项外部可观察差异**（DRIFT-004~007）。
> 其中 DRIFT-001 被**两条全绿测试锁死**，测试断言的是一个**事实错误**的 Go 行为。

**实现质量评价**：整体水平明显高于「照抄 message-api」的平均水平。9 条被要求验证的论断中，**8 条成立**（第 3 条的**结论**成立但**依据**是错的）。真正的问题不在实现层，而在**框架默认行为的差异**上——这恰好是「绿测试守卫错误答案」的典型样本（AGENTS.md §3.3 元教训）。

---

## 1. 实现方 9 条论断的独立取证结果

| # | 论断 | 结论 | 证据 |
|---|---|---|---|
| 1 | `article.go` 未调 `SetOkHandler`/`SetErrorHandler`；解析失败是 400+text/plain+nosniff；upload 成功返回裸结构 | ✅ **成立** | `grep -rn "SetErrorHandler" service/article/api/**/*.go` → **零命中**；`message/like/hot` 各有 1 命中。go-zero `httpx/responses.go:145-159` `handler==nil ⇒ http.Error(400)`。Spring 侧实测：`create missing title → 400 field "title" is not set`（text/plain+nosniff）；`upload ok → 裸 {"image_url":...}` |
| 2 | 7 个端点恰好 4 个要 JWT；upload 与两个 GET 公开 | ✅ **成立** | `routes.go:19-45` 第一段带 `rest.WithJwt`，后两段不带。两侧实测：4 个受保护无 token → 401；3 个公开 → 进业务 |
| 3 | 拦截器必须按 method+path 匹配，否则 `POST /v1/article/1` 从 Go 的 **405** 变成 401 | ⚠️ **结论成立，依据错误** | Go **实测返回 404，不是 405**（探针：`MISMATCH POST /v1/article/1 -> 404 ct="" allow=""`）。根因：`rest.WithCors()` 安装了 `cors.NotAllowedHandler`（`server.go:150`），它对非 OPTIONS 返回 **404**。而 Java 侧**确实**返回 405+`Allow` ⇒ 构成 DRIFT-001 |
| 4 | MaxBytes 生效值 10485760，对全部端点 | ✅ **成立** | `engine.go:173-179 checkedMaxBytes`：route 级 >0 用之否则用 conf 级；`routes.go:16` route 级 = 10*1024*1024 = 10485760，yaml conf 级 = 10485760，**同值**。两侧实测：protected / public / upload 三条路由 20MB body 全部 **裸 413** |
| 5 | HTTP 超时 70s（yaml `Timeout: 70000`），不是 tag 默认 3000ms | ✅ **成立**（源码级） | `article-api.yaml:4 Timeout: 70000`；`observability.DisableNativeRestTimeout` 取 `TimeoutFromMillis(70000)=70s` 并把 `Middlewares.Timeout=false`；`application.yml observability.http-timeout: 70s`。⚠️ 未做活体超时实测（见 §6） |
| 6 | `CreateArticle` 两条失败分支带 `data.article_id`，id 从 gRPC status 文案按「最后一个冒号」抠出 | ✅ **成立** | `create_article_logic.go:58,62-69,82-92`；`ArticleCreateService.extractArticleIdFromStatusMessage` 逐字复刻（含 `idx+1>=len` 与无冒号两个边界）。Java 测试日志实测 `CreateFailure code=1004 ... articleId=1792670400123` |
| 7 | 公开列表任一行非 PUBLISHED → 整条请求 5001（`return` 不是 `continue`） | ✅ **成立** | `list_articles_logic.go:63-68` 是 `return nil, errmsg.ErrorServerCommon`；Go 自带测试 `public_read_handler_test.go:140` 断言 5001。Java `ArticleListService` 逐字照抄 |
| 8 | query 真值表是 `1/0`、空值丢弃、整数 base-10 | ✅ **成立** | `core/mapping/utils.go:106-119`（bool：1/true/0/false，其余 `errTypeMismatch`）、`:120-125`（`ParseInt(str,10,32)`）；`httpx/util.go:44-45,56-61`（空值丢弃）。两侧实测：`desc=1 → 200`；`desc=yes → 400`；`page=010 → 200`（=10 非八进制）；`page= → 走 default` |
| 9 | Go `SecurityRpc` 配了但无消费者，Java 刻意不建 client | ✅ **成立** | `grep -rn "SecurityRpc" service/article/api` 仅命中 `service_context.go:25`（构造 + 类型声明），**零 logic 消费**。Java 无 `SecurityRpcClient` |

> ⚠️ **briefing 里另一条假设不成立**：`article-rpc` **不是**空骨架。实测 `RAG/service/article/article-rpc/src/main/java/...` 含 40+ 源文件（`ArticleServiceGrpcImpl` / `ArticleDomainService` / `ArticleTransactionWiringTest` / 3 个 Flyway 迁移 / minio / kafka 消费者）。这使端到端双跑**在原理上可行**，只是本次缺 etcd/nacos/PG 环境。

---

## 2. 两侧行为对照总表（同一矩阵，两侧实测）

探针：Go = `go-probe/`（真实 go-zero v1.10.2 引擎 + article-api 的真实接线）；Java = 真 Tomcat 起 `ArticleApiApplication`（`java-probe.sh`）。

| 用例 | Go（实测） | Java（实测） | 判定 |
|---|---|---|---|
| `GET /v1/article/abc` | 200 + envelope | 200 + envelope | EQUIVALENT |
| **`POST /v1/article/1`** | **404**，空 body，**无 Allow** | **405** + `Allow: PUT, DELETE, GET` | **SEMANTIC MISMATCH (DRIFT-001)** |
| `PATCH /v1/articles` | 404 | 405 + `Allow: GET` | DRIFT-001 |
| `DELETE /v1/articles` | 404 | 405 + `Allow: GET` | DRIFT-001 |
| `POST /v1/me/article/1` | 404 | 405 + `Allow: GET` | DRIFT-001 |
| `GET /v1/nope` | 404 + `404 page not found\n` + nosniff + CT `text/plain; charset=utf-8` | 404 + 同 body(19B) + nosniff + CT **`text/plain;charset=utf-8`** | **OBSERVABLE DIFFERENCE (DRIFT-005)** |
| `GET /v1/article/1/extra` | 404 | 404 | EQUIVALENT |
| `POST /v1/article` 无 token | **401**，空 body | **401**，空 body | EQUIVALENT |
| `GET /v1/me/article/1` 无 token | 401 空 | 401 空 | EQUIVALENT |
| `OPTIONS` 已注册 / 未注册 | **204** 空 | **204** 空 | EQUIVALENT |
| **`GET /v1/articles/`**（尾斜杠） | **200**（`path.Clean` 去掉） | **404** | **SEMANTIC MISMATCH (DRIFT-004)** |
| 重复斜杠 + `/v1/articles` | **200** | **404** | DRIFT-004 |
| 413（20MB，protected / public / upload） | **裸 413**，0 body，无 CT | **裸 413**，`Content-Length: 0`，无 CT | EQUIVALENT |
| CORS 5 头 + 无 Allow-Credentials | 全部命中 | 全部命中 | EQUIVALENT |
| `?page=&sort_by=` | 200（走 default） | 200 | EQUIVALENT |
| `?desc=yes` | 400 `type mismatch for field "desc"` | 400 同文案 | EQUIVALENT |
| `?page=abc` | 400 ParseInt 错误 | 400 ParseInt 错误 | EQUIVALENT |
| `{"title":123,...}` | 400 `type mismatch for field "title", expect "string", actual "number"` | 400 `type mismatch for field "title"` | **OBSERVABLE DIFFERENCE (DRIFT-006)** |
| `{"title":null,...}` | 400 `field "title" mustn't be nil` | 400 同 | EQUIVALENT |
| `{"brief":null,...}` | **接受**（`,optional`） | 接受 | EQUIVALENT |
| `{"status":null}` | **接受**（`,optional`） | 接受 | EQUIVALENT |
| `{"status":"2"}` | 400 `type mismatch for field "status"` | 400（Jackson 文案） | EQUIVALENT（码一致） |
| `CT: text/plain` + 合法 JSON | 400 `field "title" is not set`（body 被丢弃） | 400 同 | EQUIVALENT |
| 无 CT / 空 body | 400 `field "title" is not set` | 400 同 | EQUIVALENT |
| 未知字段 `zzz` | 忽略 | 忽略 | EQUIVALENT |
| token 无 `userId` claim | 200 + `{code:1005}` | 200 + `{code:1005}` | EQUIVALENT |
| `Authorization: <token>`（无 Bearer 前缀） | **接受** | 接受 | EQUIVALENT |
| `Authorization: bearer <token>` | **接受** | 接受 | EQUIVALENT |
| **`POST /v1/upload` 非 multipart** | **400** `request Content-Type isn't multipart/form-data` + text/plain | **500 裸 body** | **SEMANTIC MISMATCH (DRIFT-003)** |
| `POST /v1/upload` multipart 无 image 字段 | 400 `http: no such file` | 400 | EQUIVALENT |
| **9 MiB 合法 JSON** | **400**（`LimitReader(8MiB)` 截断） | **200 进业务** | **SEMANTIC MISMATCH (DRIFT-002)** |

---

## 3. Checklist（§38-40）

| 项 | 结果 | 依据 |
|---|---|---|
| public behavior | ❌ **不通过** | DRIFT-001（方法不匹配 Go 404 / Java 405）；DRIFT-004（尾斜杠 Go 200 / Java 404） |
| 正常路径 | ✅ 通过 | 6 个信封端点 + 1 个裸响应端点，成功形状逐字一致 |
| 边界输入 | ⚠️ **部分不通过** | 必填/`null`/类型/空 body 全部一致；但 9 MiB body 分歧（DRIFT-002）、非 multipart 上传分歧（DRIFT-003） |
| NotFound-Empty-Null | ⚠️ 部分不通过 | `ext_info`/`secondary_tags` 的 null 与 `author_name` 的缺席均正确；但 `ext_info` **键序**非字典序（DRIFT-007） |
| Error meaning | ⚠️ 部分不通过 | 15 个业务码语义映射全部一致；但框架层错误码（404/405/500）有 3 处分歧 |
| Error propagation | ✅ 通过 | gRPC code → 业务码的 7 个 logic 映射表逐条一致（含 Update 写分支**没有** PermissionDenied 这一处不对称） |
| 外部依赖失败 | ✅ 通过 | 每个 RPC 失败分支都落到 Go 的同一业务码；上传 RPC 失败 → 400 text/plain（5004 只进日志）两侧一致 |
| Cache hit-miss | ✅ 通过 | 列表内作者昵称去重（请求内 map / LinkedHashMap）、`resolveAuthorName` 的 4 条失败分支全部回落 `fallbackAuthorName`，均不冒泡 |
| Transaction boundary | N/A | gateway 无本地事务；跨服务无事务边界可比（两侧一致地没有） |
| State transition | ✅ 通过 | 无组件内可变状态；唯一状态是 `GoStyleRequestDeadline` 的 ThreadLocal（Go 侧是 ctx 传播） |
| Side effects | ✅ 通过 | `incr_view` 透传语义、删除/更新的「先读后写」鉴权读（`IncrView=false`）均一致 |
| Ordering | ⚠️ 部分不通过 | **拦截链序一致**（413 压过 401 两侧实测）；但 `enrichArticleAuthors` 的顺序在两侧都依赖 map 迭代，Java 侧更不确定（见 DRIFT-007） |
| Retry | ✅ 通过 | 两侧都**无** retry（`zrpc.RpcClientConf` 无 `Retries` 字段；Java 未加） |
| Timeout scope | ⚠️ **UNKNOWN** | 两侧都是「整个 operation 70s + 向下游传播 deadline」，源码层一致；但**未做活体超时实测**（Go 侧无法启动，见 §6） |
| Concurrency | ✅ 通过 | 无共享可变状态；两侧都靠框架/容器线程池；`ThreadLocal` 有 `begin`/`clear` 配对（Go 侧是 ctx，天然无复用问题） |
| AuthN-AuthZ | ❌ **不通过** | 鉴权**判定**完全一致（4/7 受保护、401 空 body、1005 走 200）；但方法不匹配的响应码错（DRIFT-001） |
| Serialization | ⚠️ 部分不通过 | 字段名/类型/null/缺席全部一致；`ext_info` 键序（DRIFT-007）、`Content-Type` 分号空格（DRIFT-005）、类型错误文案（DRIFT-006）三处字节级差异 |
| Lifecycle | ⚠️ 部分不通过 | 启动/关闭路径无差异；但**两侧都从未被同时启动过做端到端验证**（Go 侧受 etcd 阻塞） |

**Checklist 汇总**：通过 10 / 部分不通过 6 / 不通过 2 / N/A 1 / UNKNOWN 1

---

## 4. DRIFT 列表（按严重程度排序）

| ID | 组件/操作 | 分类 | 风险 | 一句话差异 |
|---|---|---|---|---|
| **DRIFT-001** | 路由层 / 方法不匹配 | **SEMANTIC MISMATCH** | **CRITICAL · HIGH-RISK DRIFT** | Go 返回 **404**（空 body、无 `Allow`）；Java 返回 **405 + `Allow: PUT, DELETE, GET`**。且被两条全绿测试锁死 |
| **DRIFT-002** | `CreateArticle` / `UpdateArticle` / JSON body 8–10 MiB | **SEMANTIC MISMATCH** | **HIGH** | Go 的 `io.LimitReader(r.Body, 8<<20)` 截断 ⇒ **400 text/plain**，永远到不了业务；Java 完整解析 ⇒ 进业务。10 MiB 的 `MaxBytes` 与 8 MiB 的 JSON 上限之间有一条 **2 MiB 的「Go 拒绝 / Java 接受」带** |
| **DRIFT-003** | `UploadImage` / 非 multipart 请求 | **SEMANTIC MISMATCH** | **HIGH** | Go：`r.FormFile` 失败 ⇒ `httpx.ErrorCtx` ⇒ **400 text/plain**；Java：异常未被 handler 族覆盖 ⇒ `handleAny` ⇒ **裸 500** |
| **DRIFT-004** | 路由层 / 路径规范化 | **SEMANTIC MISMATCH** | **MEDIUM** | Go `path.Clean` 容忍尾斜杠与重复斜杠；Spring Boot 3+ 默认 `useTrailingSlashMatch=false` ⇒ **404** |
| **DRIFT-005** | 路由层 / 404 响应头 | OBSERVABLE DIFFERENCE | LOW | Go `Content-Type: text/plain; charset=utf-8`（分号后**有空格**）；Tomcat 把 Java 设的同一值重写成 `text/plain;charset=utf-8`（**无空格**） |
| **DRIFT-006** | 解析层 / 类型不匹配文案 | OBSERVABLE DIFFERENCE | LOW | Go `type mismatch for field "title", expect "string", actual "number"`；Java `type mismatch for field "title"`。状态码与 Content-Type 一致，只有 body 少一段 |
| **DRIFT-007** | 序列化 / `ext_info` 键序 | OBSERVABLE DIFFERENCE | LOW | Go `encoding/json` 对 map key **排序**；Java 用 `LinkedHashMap` 承接 protobuf 的 map 迭代顺序（**不排序**）。Java 源码注释声称 LinkedHashMap「保持 Go 的字典序」——**该断言为假** |
| **DRIFT-008** | `CreateArticle` / rpc 返回 nil | OBSERVABLE DIFFERENCE（理论不可达） | INFO | Go 会在 `rpcResp.ArticleId` 上 panic ⇒ recover ⇒ 裸 500；Java 返回 `article_id:""` + 200。Java 侧已在注释中显式承认并选择不 panic |

---

## 5. 我实际执行过的检查

### 5.1 读取的源码

**Go 侧（全文逐行）**
`service/article/api/article.go` · `etc/article-api.yaml` · `internal/handler/routes.go` · `internal/handler/article/*.go`（7 handler + 1 测试）· `internal/logic/article/*.go`（7 logic + helper）· `internal/types/types.go` · `internal/config/config.go` · `internal/svc/service_context.go`
`service/article/common/errmsg/errmsg.go` · `service/common/response/response.go` · `service/common/observability/{http.go,observability.go,rpc.go}` · `service/common/logger/`

**go-zero v1.10.2**（`/Users/pr/go/pkg/mod/github.com/zeromicro/go-zero@v1.10.2`）
`rest/engine.go`（链序 130-171、`checkedMaxBytes` 173-179、`checkedTimeout` 181-188、`withNetworkTimeout` 368-382）
`rest/chain/chain.go`（`Append`/`Then` 语义 38-91）
`rest/server.go`（`WithCors` 147-153、`WithJwt` 184-191、`WithPrefix` 252-266、`WithTimeout` 299-304）
`rest/config.go`（`MiddlewaresConf` 九个 `default=true` 12-22；`MaxBytes`/`Timeout` tag 默认 52-56）
`rest/httpx/requests.go`（`Parse` 49-77、`ParseJsonBody` 116-124、`withJsonBody` 153-155、`maxBodyLen = 8<<20` 21）
`rest/httpx/responses.go`（`doHandleError` 142-159、`doWriteJson` 175-196）
`rest/httpx/util.go`（`GetFormValues` 空值丢弃 22-65）
`rest/router/patrouter.go`（`ServeHTTP` 59-83、`methodsAllowed` 101-120）
`rest/handler/authhandler.go`（`Authorize` 44-84、`unauthorized` 106-122）
`rest/handler/maxbyteshandler.go` · `maxconnshandler.go` · `breakerhandler.go` · `sheddinghandler.go` · `recoverhandler.go` · `timeouthandler.go`
`rest/token/tokenparser.go`（`newParser` 122-124 = `jwt.WithJSONNumber()`）
`rest/internal/cors/handlers.go`（七个头 10-27、`Middleware` 52-68、`NotAllowedHandler` 36-50）
`rest/internal/errcode/grpc.go`（`CodeFromGrpcError` / `IsGrpcError`）
`core/mapping/unmarshaler.go`（`processNamedFieldWithValue` 856-862 = `,optional` 接受 null；`processNamedFieldWithoutValue` 919+ 默认值；`newTypeMismatchError` 1202）
`core/mapping/utils.go`（`parseOption` 372-385；`convertTypeFromString` 105-125）
`core/jsonx/json.go`（`SetEscapeHTML(false)` 18）

**Java 侧（全文逐行）**
`ArticleApiApplication` · `application.yml`（100 行全部）· `ArticleApiProperties` · `ArticleGoStyleCoercion` · `ArticleGoStyleCorsFilter` · `WebMvcConfig`
`controller/ArticleController` · `interceptor/JwtAuthInterceptor`
`advice/GlobalExceptionHandler` · `advice/GlobalResponseAdvice`
`http/GoStyleBadRequestException` · `http/GoStylePlainTextErrors`
`service/*.java`（9 个）· `dto/*.java`（13 个，重点 `Article` / `CreateArticleReq` / `ListArticlesReq`）· `form/GoFormValues` · `client/{ArticleRpcClient,UserRpcClient}`
测试：`ArticleApiMvcPipelineTest` · `ArticleApiRouteAuthBoundaryTest`
共享层：`com/rag/common/{http/GoStyleRouterErrorResponses,http/GoStyleJsonBodyConverter,protection/GoStyleMaxBytesFilter,spring/HttpMiddlewareFilter,observability/GoStyleRequestDeadline,response/Response}`

### 5.2 运行的命令

| # | 命令 | 目的 | 结果 |
|---|---|---|---|
| 1 | `mvn -o -pl service/article/article-api test` | 复现「43 测试全绿」 | **EXIT=0**；`ArticleApiMvcPipelineTest` **28 passed** + `ArticleApiRouteAuthBoundaryTest` **15 passed** = **43/43** |
| 2 | 自建 Go 模块 `go-probe/`（复用 Sea-RideTheWind 的 go.mod/go.sum，**未改任何仓库文件**），`go build` 后 `rest.MustNewServer(...)` + **真实监听 127.0.0.1:18899**，21 条 HTTP 用例 + 25 条 `httpx.Parse`/`token`/`FormFile` 事实用例 | 取 Go 侧**活体**期望值 | 见 §2 左列 |
| 3 | `java -cp target/classes:<surefire 提取的 classpath> ArticleApiApplication -Dserver.port=18898`（真 Tomcat，Nacos 自动注册关闭、gRPC 指向 `127.0.0.1:65535`） | 取 Java 侧**活体**期望值 | 见 §2 右列 |
| 4 | `curl` 逐条打两侧同一矩阵（路由 / CORS / 413 / 401 / 解析 / JWT / upload / 9 MiB body） | 逐维度对拍 | 见 §2 |
| 5 | `xxd` 取 Java 404 body 原始字节 | 验证尾换行 | `404 page not found` + `\n` = 19 字节，与 Go 一致 |
| 6 | `ps aux` 查 mvn 进程 | 检查并发构建污染 | **4 个 mvn/maven 进程**在跑 ⇒ 全仓口径不可信，本次只跑**单模块隔离**，不引用全仓数字 |

---

## 6. 明确未被验证的部分

| 未验证项 | 原因 | 影响 |
|---|---|---|
| **Go 侧 handler 的活体行为**（真 `article-api` 进程） | `etc/article-api.yaml` 指向 `etcd:2379`；`svc.NewServiceContext` 走 `zrpc.MustNewClient`，无 etcd 时直接 panic | 我的 Go 探针**复现了完全相同的引擎接线**（`MustNewServer` + `WithCors` + 同样的 3 段 `AddRoutes` + 同样的 yaml 值 + 同构的 observability 中间件），但 handler 是桩。⇒ **框架层结论可信；handler 内业务分支的结论来自源码 + Go 自带测试 `public_read_handler_test.go`（含 6 次 RPC 调用参数断言）** |
| **70s 超时的活体行为** | 同上 | 两侧都是「整个 operation 70s deadline + 向下游传播 + 裸 503 `Request Timeout`」，**源码级一致**；未实测触发 |
| **真实 article-rpc / user-rpc 的对拍** | 需 etcd + Nacos + PostgreSQL + MinIO + Kafka；本次环境无 | 「Java 侧业务码映射与 Go 一致」依据：Go 侧源码逐分支 + Go 自带测试断言的码值；Java 侧实测跑到了 `code:1015/1005/1006/1002/5001/1001/1003/1004`（无下游，全部落到 `default → 1015`，与 Go 的 `default` 分支一致） |
| **`ext_info` 键序（DRIFT-007）** | 需要真实的非空 `ext_info` map | 结论基于 Go `encoding/json` 对 map 排序这一确定事实 + Java 用 `LinkedHashMap`（保插入序）这一确定事实。**推断**：字节序不一致。**未做活体对比** |
| **并发 / 压测下的保护层行为**（MaxConns=10000、CPU shedding、breaker） | 无压测环境 | 两侧共用 `com.rag.common.protection` 的实现，Go 侧逻辑已逐行读过；**运行时行为未实测** |
| **Tomcat 对 chunked（无 Content-Length）请求的处理** | 未构造 chunked 请求 | Go：`-1 > 10485760` 为 false ⇒ 放行；Java：`getContentLengthLong()` 同样返回 -1 ⇒ 放行。**源码级推断，未实测** |
| **`article-api` 的 Nacos 注册 / gRPC 通道真实连通** | 无 Nacos | Java 侧以 `static://127.0.0.1:65535` 启动，注册关闭。**部署形态未验证** |

---

## 7. 给实现方的修复建议（按性价比排序）

1. **DRIFT-001（必须先做）**：删掉 `GlobalExceptionHandler#handleMethodNotSupported` 的 405 映射，改为 404 形状。Go 的 404 分两支：未注册路径有 body（`404 page not found\n`），**方法不匹配无 body**。需要按「是否路径命中」区分。**同时必须改掉 `ArticleApiRouteAuthBoundaryTest` 的两条 405 断言**——它们锁的是错误答案。
   ⚠️ 注意：`GoStyleRouterErrorResponses` 是 `service/common` 的共享实现，message/like/hot 也在用。**先确认它们是否也返回 405**（它们同样没有覆盖 Spring 默认的 405 处理器），再决定是在共享层改还是加开关。

2. **DRIFT-003（改动最小、收益最高）**：给 `GlobalExceptionHandler` 加一条 `@ExceptionHandler(org.springframework.web.multipart.MultipartException.class)`（把现在只列了 `MaxUploadSizeExceededException` 的那组扩到父类）→ 400 text/plain。一处改动。

3. **DRIFT-002**：在 `GoStyleJsonBodyConverter.read` 里，对 `goReadsBody` 为真但 `contentLength > 8*1024*1024` 的请求返回 `null`（或抛 `GoStyleBadRequestException`）。注意：Go 是**先截断再解析**，截断后 JSON 必然不完整 ⇒ 必然 400。

4. **DRIFT-004**：在 `WebMvcConfig` 加 `registry.setUseTrailingSlashMatch(true)`（Boot 3+ 默认已改为 false，需显式打开）。重复斜杠还需要容器侧路径规范化配置，成本更高，可先只处理尾斜杠。

5. **DRIFT-005 / 006 / 007**：低危。005 与 007 需要动共享层或容器，先登记不修。006 建议补一个统一的类型错误文案格式化器。

6. **源码注释修正**（低成本、高可信度收益）：
   - `JwtAuthInterceptor` 类注释「Go: 该路由未注册 → 405」⇒ 改为 **404**
   - `ArticleApiRouteAuthBoundaryTest` 类注释「`patrouter.go:79` 方法不匹配 → Allow + 405」⇒ 改为「`WithCors` 覆盖了 notAllowed handler ⇒ 404 无 Allow」
   - `ArticleApiSupport.cloneStringMap` 注释「用 LinkedHashMap 保持 Go encoding/json 的字典序」⇒ **该说法不成立**，应改为「键序不保证一致，见 DRIFT-007」

---

## 8. 交付物清单

| 文件 | 内容 |
|---|---|
| `README.md` | 本文件：判定 + checklist + DRIFT 摘要 + 已执行/未验证边界 |
| `component-contract.md` | 组件契约 |
| `go-semantic-model.md` | Go 侧语义模型（不含 Java） |
| `springboot-semantic-model.md` | Spring 侧语义模型（与 Go 完全相同的章节结构） |
| `equivalence-matrix.md` | 逐 operation × 逐维度对照表（核心输出） |
| `semantic-drifts.md` | 8 条 DRIFT 记录 |
| `test-cases.md` | 42 条 CASE 用例集 |
| `unresolved.md` | 4 条 UNRESOLVED |
| `go-probe/` · `java-probe.sh` · `go-facts.txt` · `mvn-article-api-test.log` | 本次验证的原始记录与可复现探针 |
