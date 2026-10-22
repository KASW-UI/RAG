# Unresolved — `article-api`

已查代码、但**无法确定**的问题。按 §31 的格式：`Question / Go Evidence / Java Evidence / Why Unknown / Impact / Required Confirmation`

---

## UNRESOLVED-001：Go 侧 handler 的活体行为无法端到端验证

| 字段 | 内容 |
|---|---|
| **Question** | `article-api` 的 **handler 内部**逻辑（7 个 logic 的 gRPC 调用参数、错误分支）在**运行时**是否与 Java 侧完全一致？ |
| **Go Evidence** | `svc.NewServiceContext(c)` 对三个 `RpcClientConf` 全部调 `zrpc.MustNewClient`（`service_context.go:24,25,26`），而 `etc/article-api.yaml:10,17,24` 的 `Etcd.Hosts` 均指向 `etcd:2379`。`zrpc.MustNewClient` 在 resolver 无法建立连接时直接 panic ⇒ **本机无 etcd 时该进程无法启动**。<br>替代证据：① Go 自带测试 `internal/handler/article/public_read_handler_test.go` 用桩 RPC 直接调 handler，断言了 6 次调用的**全部参数字段**（`PublicOnly`/`IncrView`/`RequesterId`）与 6 个响应码；② 我逐行读了 7 个 logic 的源码。 |
| **Java Evidence** | `ArticleRpcClient` / `UserRpcClient` 用 devh `@GrpcClient`，`grpc.client.*.address=discovery:///...` 指向 Nacos。本次以 `static://127.0.0.1:65535` 启动 ⇒ 真实调用全部落 `UNAVAILABLE` ⇒ 7 个端点实测均返回 `code:1015`（Go 的 `default` 分支）。<br>`ArticleRpcClient.articleRpc()` / `UserRpcClient.userRpc()` 每调用一次都 `GoStyleRequestDeadline.applyTo(...)`。 |
| **Why Unknown** | 「Go 源码分支」与「Java 实测分支」两侧都无法在**同一个真实下游**上跑。因此只能证明「两侧的映射表一致」，不能证明「同一下游返回时两侧输出一致」。特别是：**当 article-rpc 真的可用时**，Java 侧的 proto→DTO 映射（`ArticleApiSupport.toArticle`）从未被真数据验证过。 |
| **Impact** | 中。业务码映射的**静态**对应关系已逐条核对（矩阵 G 组），风险点在「下游真实返回时 DTO 字段是否漏/多」。 |
| **Required Confirmation** | 起一套 etcd + Nacos + PostgreSQL + MinIO + Kafka（或至少 etcd + 一个桩 article-rpc），两侧同跑 CASE-018~024、CASE-047~056、CASE-101~104，比对**完整响应体**。 |
| **备注** | ⚠️ briefing 称「`article-rpc` 仍是空骨架」—— **实测不成立**：`RAG/service/article/article-rpc/src/main/java/` 有 40+ 源文件（`ArticleServiceGrpcImpl` / `ArticleDomainService` / 3 个 Flyway 迁移 / minio / kafka 消费者 / 3 个测试类）。⇒ 上面的双跑在**原理上**已经可行，缺的只是外部中间件。 |

---

## UNRESOLVED-002：70s 超时的活体行为

| 字段 | 内容 |
|---|---|
| **Question** | 整体超过 70s 时，两侧的**响应形状**与**下游 goroutine/线程的处置**是否一致？ |
| **Go Evidence** | `observability.NewHTTPMiddleware`（`common/observability/http.go:53,80-89`）：`context.WithTimeout(r.Context(), 70s)` → handler 在独立 goroutine 跑 → 超时分支 `http.Error(w, "Request Timeout", http.StatusServiceUnavailable)` 并**丢弃缓冲**。goroutine **不 kill**（`:68` 无 select kill），但其 ctx 已到期 ⇒ ctx-aware 的 gRPC 立即中止。 |
| **Java Evidence** | `HttpMiddlewareFilter` → `com.rag.common.observability.HttpMiddleware`（同一套结构的 Java 实现）+ `GoStyleRequestDeadline.begin/clear`。⚠️ 该类的 javadoc **自陈**：「Go ctx 到期 ⇒ 客户端看到 `DEADLINE_EXCEEDED`（在 breaker 失败名单里，计为失败）；Java `Future.cancel(true)` ⇒ 客户端看到 `CANCELLED`（**不在**名单里，**计为成功**）」。 |
| **Why Unknown** | 两侧都无法在本次环境中触发 70s（需要真实慢下游）。更关键的是 Java 侧**自己声明了**一个未验证的 `DEADLINE_EXCEEDED` vs `CANCELLED` 差异，那个差异一旦为真，会**传导到熔断器计数**。 |
| **Impact** | 中（正常流量下不可见；慢下游/降级场景下影响熔断决策）。 |
| **Required Confirmation** | 用一个可控的慢桩 article-rpc（sleep 80s），两侧各发一次请求，记录：① HTTP 响应状态码与 body；② breaker 的 `Reject` 计数是否 +1；③ 下游 handler 是否被中断。 |

---

## UNRESOLVED-003：`ext_info` 的键序（DRIFT-007 的活体验证）

| 字段 | 内容 |
|---|---|
| **Question** | 同一份非空 `ext_info`，两侧输出的 JSON **字节**是否一致（尤其是键序）？ |
| **Go Evidence** | `encoding/json` 在编码 `map[string]string` 时**对 key 排序**（这是标准库的既定行为）；`types.Article.ExtInfo` 是裸 `map[string]string`，经 `cloneStringMap` 仍是 map ⇒ 输出字典序。 |
| **Java Evidence** | `ArticleApiSupport.cloneStringMap` 用 `LinkedHashMap` 承接 `protoArticle.getExtInfoMap()` 的迭代顺序 ⇒ **不排序**。⚠️ 该方法上方的 javadoc 声称「用 `LinkedHashMap` 保持 Go `encoding/json` 的**字典序**输出」—— **该断言与实现不符**（`LinkedHashMap` 保插入序，要字典序必须用 `TreeMap`）。 |
| **Why Unknown** | 需要一个 `ext_info` 有 2 个以上键且**非字典序**的 article-rpc 响应。本次 article-rpc 不可达 ⇒ 该字段在所有实测响应中都是 `null`（走的是 `cloneStringMap` 的「空 → null」分支），**从未进入排序/迭代分支**。 |
| **Impact** | 低（JSON 语义等价，仅字节不同）。 |
| **Required Confirmation** | 一条**不依赖下游**的单元测试即可钉死：直接调 `ArticleApiSupport.cloneStringMap`，传入 `{"z":"1","a":"2"}`，断言返回 Map 的 key 迭代顺序。**建议这条立即补**——它把推断变成事实，且成本极低。 |

---

## UNRESOLVED-004：chunked（无 Content-Length）请求的容器行为

| 字段 | 内容 |
|---|---|
| **Question** | `Transfer-Encoding: chunked`（无 `Content-Length`）的超大请求，两侧是否都「放行 MaxBytes」并最终以**同样的状态码**失败？ |
| **Go Evidence** | `maxbyteshandler.go:19` 只判 `r.ContentLength > n`；chunked 时 Go 的 `r.ContentLength == -1` ⇒ `-1 > 10485760` 为 false ⇒ **放行**。随后 `/upload` 的 `r.FormFile("image")` 因 reader 超限失败 ⇒ `httpx.ErrorCtx` ⇒ **400 text/plain**。JSON 端点则会在 `io.LimitReader(8MiB)` 处截断 ⇒ 400（见 DRIFT-002）。 |
| **Java Evidence** | `GoStyleMaxBytesFilter` 只判 `request.getContentLengthLong() > maxBytes`；Servlet 规范里 chunked 时同样返回 -1 ⇒ 放行（该 Filter 的 javadoc 显式论证了这一点）。但**之后**由 Tomcat 的 `spring.servlet.multipart.max-request-size: 10MB` 兜底 ⇒ 抛 `MaxUploadSizeExceededException` ⇒ 被映射为 **400**。 |
| **Why Unknown** | 本次两侧实测都只用了带 `Content-Length` 的请求（curl 默认行为）。**没有构造 chunked 请求**，因此「放行后会发生什么」只有源码推导，没有运行证据。⚠️ 特别是 Tomcat 是否在更早阶段用自己的 `maxPostSize`/`maxSwallowSize` 拦下，源码里看不到。 |
| **Impact** | 低-中（chunked 上传是少见但真实的客户端行为）。 |
| **Required Confirmation** | 两侧各发一个 `Transfer-Encoding: chunked` 且无 `Content-Length` 的 12 MiB 请求到 `/upload`，比对状态码与 body。 |

---

## UNRESOLVED-005（非漂移，仅记录）：`useTrailingSlashMatch` 与重复斜杠的完整对齐成本

| 字段 | 内容 |
|---|---|
| **Question** | 若要同时对齐「尾斜杠」与「重复斜杠」，Spring 侧的正确配置点在哪里？ |
| **Go Evidence** | `patrouter.go:60` 的 `path.Clean(r.URL.Path)` 一次性处理了：去尾斜杠、合并重复斜杠、解析 `.` 与 `..` 段。实测 `//v1/articles` → **200**、`/v1/articles/.` → 200。 |
| **Java Evidence** | `PathPatternParser` 不做 Clean；`useTrailingSlashMatch` 只解决尾斜杠；重复斜杠与 `.`/`..` 由 **Tomcat connector** 层面的规范化处理，与 Spring MVC 无关。当前实测：4 种变体（`/v1/articles/`、`/v1/articles//`、`/v1//articles`、`/v1/articles/.`）**全部 404**。 |
| **Why Unknown** | 「Spring 侧要怎么配才能复刻 `path.Clean` 的全部三条语义」需要改容器层（`Connector` 属性 / 自定义 `ServletContext` 前缀映射），**不在 `WebMvcConfig` 的能力范围内**。本次未做配置实验。 |
| **Impact** | 低（尾斜杠可低成本解决；重复斜杠的收益极低）。 |
| **Required Confirmation** | 在一个临时 Tomcat 配置上验证 `setUseTrailingSlashMatch(true)` 能解决尾斜杠；重复斜杠是否需要 `TomcatConnectorCustomizer` 或 `server.tomcat.…` 开关（本次未验证哪个开关有效）。**建议先只解决尾斜杠，把重复斜杠显式登记为「已知不修」。** |

---

## 判定说明

`README.md` 的最终判定是 **`SEMANTICALLY NOT EQUIVALENT`** 而**不是** `UNRESOLVED`，理由：

- 定性为 NOT EQUIVALENT 的依据是 **3 项已被双侧实测证实的 SEMANTIC MISMATCH**（DRIFT-001 / 002 / 003）与 1 项实测证实的路由差异（DRIFT-004）。这些**不依赖**任何上述 UNRESOLVED 项。
- 上述 5 条 UNRESOLVED 影响的是「已判定等价的那 130 个维度里，是否有隐藏差异」，**不会推翻**已有的 NOT EQUIVALENT 结论。
- 若把 UNRESOLVED 全部按最坏情况计入，结论只会**更强**（更多 MISMATCH），不会反转。
