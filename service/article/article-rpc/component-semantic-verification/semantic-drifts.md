# 语义漂移台账

> 只记**真差异**。每条含：Component / Operation / Category / Go Behavior / Java Behavior /
> Difference / Why It Matters / Observable Impact / Business-Technical Impact /
> Likely Cause / Must Preserve / Recommended Verification / Evidence。
>
> **反事实测试**（关键栏）：一个**没看过 Go 源码**、只按普通 Spring 需求实现的开发者，
> 是否会自然写成 RAG 现在的样子？会 ⇒ `HIGH-RISK DRIFT`。

**总数：16 条**（`SEMANTIC MISMATCH` 6 / `OBSERVABLE DIFFERENCE` 6 / 另 4 条归入 UNKNOWN 跟踪）
**`HIGH-RISK DRIFT`：4 条（S-1、S-2、S-3、S-5）**

---

# 一、SEMANTIC MISMATCH（6 条）

## S-1 🔴 HIGH-RISK · 公开阅读量自增在只读事务里执行 ⇒ 每次调用必失败

| 栏 | 内容 |
|---|---|
| **Component** | 公开读视图（`ArticlePublicDomainService`） |
| **Operation** | `GetArticle`（`public_only=true`, `incr_view=true`） |
| **Category** | `SEMANTIC MISMATCH` |
| **Go Behavior** | `getPublicArticle` **完全无事务**。`FindPublicOne`（2~3 条独立 SELECT）→ MinIO 读 → `IncrPublicViewCount`（单条带谓词 UPDATE）各自 autocommit，UPDATE 正常提交。`internal/logic/get_article_logic.go:126-168` / `internal/model/article_public.go:151-161` |
| **Java Behavior** | `@Transactional(readOnly = true)` 包住整段（含 3 条 SELECT + 1 次 MinIO 网络读 + 1 条 **UPDATE**）。`ArticlePublicDomainService.java:79` + `:119` + `ArticlePublicMapper.java:252` |
| **Difference** | 事务被标记为只读，PG 拒写。机制链（逐环取证，非推测）：<br>① Spring `DataSourceUtils.setReadOnlyIfPossible` → `Connection.setReadOnly(true)`（`spring-jdbc-7.0.9.jar` 反编译：`DataSourceUtils.setReadOnlyIfPossible` 内 `aload_0 / iconst_1 / invokevirtual Connection.setReadOnly`）<br>② pgjdbc `PgConnection.setReadOnly` 置 `readOnly` 字段（`postgresql-42.7.13.jar` 反编译：`setReadOnly(Z)` → `putfield readOnly`）<br>③ pgjdbc `QueryExecutorImpl` 按该位选 `beginReadOnlyTransactionQuery`，其字符串常量即 **`"BEGIN READ ONLY"`**（字节码常量池 `#123`）<br>⇒ PG 对该事务内的 `UPDATE` 返回 `25006 cannot execute UPDATE in a read-only transaction` |
| **Why It Matters** | 公开详情页「浏览量 +1」是社区域的核心读路径。这是**每次调用 100% 失败**，不是偶发。 |
| **Observable Impact** | 客户端拿到 gRPC `UNKNOWN`（PG 错误文案原样进 description），业务码经 api `default` ⇒ **`CodeServerBusy(1015)`**；Go 侧成功并返回文章。`ArticleServiceGrpcImpl.java:133-137` + Go api `delete_article_logic.go:49-59` |
| **Business-Technical Impact** | 文章详情接口在 `incr_view=true` 时**整体不可用**；`view_count` 永不增长；推荐/热榜依赖的浏览量信号断流。 |
| **Likely Cause** | 「读路径 ⇒ `readOnly=true`」是 Spring 的**惯用写法**，实现者把 Go 的 `listPublicArticles`（那里确实该 readOnly）的心智模型套到了 `getPublicArticle` 上，而后者含写入。 |
| **Must Preserve** | Go 的语义是「**读与计数在独立 autocommit 语句中**」，`incrPublicViewCount` 必须能提交；若要事务，隔离级别可取 `READ_COMMITTED`，但 `readOnly` 必须为 false。 |
| **Recommended Verification** | 新增一条**真 PG + 真代理**的用例：`getPublicArticle(id, true)` ⇒ 断言不抛异常 **且** `view_count` 真的 +1。当前 `ArticlePublicDomainServiceViewTest` 全 Mockito（不装配代理 ⇒ 事务根本不生效），`ArticleTransactionWiringTest` / `ArticleSpringContextWiringTest` 都**没有**经过代理调用过 `getPublicArticle(.., true)` ⇒ 79 条全绿完全覆盖不到。 |
| **Evidence** | Go `get_article_logic.go:156-166`、`article_public.go:151-161`；Java `ArticlePublicDomainService.java:79,119`、`ArticlePublicMapper.java:252-262`；驱动行为见上述三段字节码取证 |

**反事实测试**：一个普通 Spring 开发者写「公开读取服务」时，**几乎必然**会打 `@Transactional(readOnly = true)`。
⇒ **`HIGH-RISK DRIFT`**。这类缺陷按 AGENTS.md §3.3 完全不报错：编译过、装配过、`isAopProxy` 断言也绿。

---

## S-2 🔴 HIGH-RISK · 作者视角列表在 `sort_by` 为空时排序方向反转

| 栏 | 内容 |
|---|---|
| **Component** | RPC 服务端 · 文章领域服务（列表） |
| **Operation** | `ListArticles`（`public_only=false`） |
| **Category** | `SEMANTIC MISMATCH` |
| **Go Behavior** | `model/article.go:161-176`（**消费点**）：<br>`order := "created_at desc"`<br>`if opt.SortBy != "" { switch … ; if opt.Desc { order += " desc" } else { order += " asc" } }`<br>⇒ **`SortBy == ""` 时整个方向分支被跳过，恒 `created_at desc`**（`Desc` 标志被忽略） |
| **Java Behavior** | `ArticleMapper.java:171-181`：字段 `<otherwise>created_at</otherwise>` + 方向 `<when test="desc">DESC</when><otherwise>ASC</otherwise>` ⇒ `SortBy==""` 且 `desc==false` 时得 `created_at ASC` |
| **Difference** | proto3 里 `bool desc` 的默认值是 **false**，`string sort_by` 的默认值是 **""**。因此**任何不显式设置排序的调用**（作者自己的文章列表、最常见的调用形态）在 Go 侧是「最新在前」，在 Java 侧是「最旧在前」——**整页顺序颠倒**。 |
| **Why It Matters** | 作者进入「我的文章」看到自己最早的草稿排在最上面；分页翻页顺序也与线上历史不一致。 |
| **Observable Impact** | `ListArticlesResponse.articles` 的**顺序**不同（集合成员相同）。`page` / `page_size` / `total` 不受影响。 |
| **Business-Technical Impact** | 列表 UX 反常；任何依赖「默认最新在前」的客户端（前端排序假设、埋点、A/B）行为改变。 |
| **Likely Cause** | Go 把方向拼接放在 `if SortBy != ""` 内是**实现瑕疵**；Java 用白名单 + 无条件方向，恰好是「更干净的写法」。RAG 的注释（`ArticleMapper.java:147-152`）已明确识别并**声明**这是有意偏离（「Go 的实现瑕疵，不是有意行为」）。 |
| **Must Preserve** | 按 AGENTS.md §1.1，Go 的行为（含瑕疵）就是基准。若确认要保留 RAG 写法，必须登记为 `docs/DRIFT/_cross/signed-deviations.md` 的**签字偏离**并由用户拍板，而不是写在实现注释里。 |
| **Recommended Verification** | 单模块真 PG 用例：`sortBy=""`、`desc=false`、插入 3 条不同 `created_at` 的行，断言返回顺序为 `desc`。当前 `ArticleDomainServiceSemanticsTest` 无任何一处传过 `SortBy`（`:296,311,317` 全部 `any()/anyBoolean()`）⇒ 该路径零覆盖。 |
| **Evidence** | Go `internal/model/article.go:161-176`（消费点，非 struct 定义） / Java `mapper/ArticleMapper.java:171-181` |

**反事实测试**：白名单 + 无条件方向是**最自然**的 Java 写法，几乎每个开发者都会这么写。
⇒ **`HIGH-RISK DRIFT`**：它不会以「异常」形式暴露，只会安静地让默认列表顺序颠倒。

---

## S-3 🔴 HIGH-RISK · Kafka 失败位点契约与 Go 相反

| 栏 | 内容 |
|---|---|
| **Component** | MQ 消费者组件（两个消费者共用） |
| **Operation** | listener 抛异常后的位点处置 |
| **Category** | `SEMANTIC MISMATCH` |
| **Go Behavior** | `ForceCommit` 在 `kq/config.go:24` 声明为 `bool \`json:",default=true"\``；`etc/article.yaml` **未设置**该字段 ⇒ 生效值 **true**。<br>**消费点** `kq/queue.go:230-242`：<br>`if err := q.consumeOne(...); err != nil { if q.errorHandler != nil {…} ; if !q.c.ForceCommit { continue } }`<br>`if err := q.consumer.CommitMessages(ctx, msg); err != nil {…}`<br>⇒ `ForceCommit==true` ⇒ **不 continue ⇒ 失败消息的位点照样被提交** |
| **Java Behavior** | `ArticleRpcApplication.java:173` `handler.setSeekAfterError(false)` + `:171` `FixedBackOff(0L, 0L)`；被 `ArticleSpringContextWiringTest.java:238` 断言锁死 |
| **Difference** | Go：**失败即提交（消息被丢弃）**。Java：**失败不提交（消息在重启 / rebalance 后重投）**。 |
| **Why It Matters** | 决定「坏消息是消失还是无限循环」。两侧策略各有代价，但**必须是同一个**，否则运维的心智模型与告警口径全部对不上。 |
| **Observable Impact** | 可观察窗口：①**失败记录是该分区最后一条**且此后进程退出、且无任何后续成功提交 ⇒ Go 不再重投，Java 会重投。②稳态下两者都因后续成功提交的更高 offset 而跳过该消息，行为趋同。⇒ 差异**集中在重启边界**。 |
| **Business-Technical Impact** | Java 侧 MinIO / security-rpc 持续故障 ⇒ 审核消息在每次 rebalance 后反复重放；叠加 **S-5**（content security 恒抛）⇒ **永久 poison-pill 循环**。Go 侧则是静默丢消息、文章卡在 REVIEWING。 |
| **Likely Cause** | 实现者读了 `queue.go:235` 的 `if !q.c.ForceCommit { continue }` 这行代码，**但没有消费 `ForceCommit` 的 struct tag 默认值**。这正是 AGENTS.md §3.3.1 所列的「只看声明点不看消费点」的变体——此处连声明点都读了，却漏了 tag。 |
| **Must Preserve** | 若以 Go 为准：失败时**允许提交位点**（Spring 侧等价于 `seekAfterError=true`，但要注意该分支会 `commit(offset+1)`，语义恰好等于 Go）。若要保留 Java 写法，须登记为签字偏离并显式评估 poison-pill 风险。 |
| **Recommended Verification** | 真 Kafka（Testcontainers）双侧对拍：投一条必然失败的消息 + 一条正常消息，断言 `committed` 的 offset 集合。现有 `ArticleSpringContextWiringTest.java:213-242` 只断言了 `isSeekAfterError()==false`，**没有对照 Go 的默认值** ⇒ 这条绿测试正在**给错误方向加固**。 |
| **Evidence** | Go `go-queue@v1.2.2/kq/config.go:24`（声明）+ `kq/queue.go:230-242`（**消费点**） / Java `ArticleRpcApplication.java:162-175` |

**反事实测试**：`setSeekAfterError(false)` 看起来像「更安全 / 更不丢消息」的写法，注释里也煞有介事地引用了 `FailedBatchProcessor.seekOrRecover:255-266`。
但**没有人会先查 `ForceCommit` 的 default tag**。⇒ **`HIGH-RISK DRIFT`**。

---

## S-4 · 消费者并发度 8 vs 1

| 栏 | 内容 |
|---|---|
| **Component** | MQ 消费者组件 |
| **Operation** | 审核消费者 / 同步结果消费者的消息处理并行度 |
| **Category** | `SEMANTIC MISMATCH` |
| **Go Behavior** | `kq/config.go:19` `Processors int \`json:",default=8"\``；`etc/article.yaml` 只设了 `Consumers: 1`，**未设 `Processors`**。<br>**消费点** `kq/queue.go:220` `for i := 0; i < q.c.Processors; i++` ⇒ **8 个并发处理协程** |
| **Java Behavior** | `articleKafkaListenerContainerFactory`（`:189-203`）**未调用 `setConcurrency`** ⇒ 并发度 1，串行 |
| **Difference** | 同一时刻最多处理 8 条 vs 1 条 |
| **Why It Matters** | 吞吐能力差一个量级；且 Go 的 8 协程**乱序提交位点**（`CommitInOrder` 默认 false），与 Java 的严格串行在**并发可见行为**上不同 |
| **Observable Impact** | 消费延迟与积压恢复速度；单分区吞吐上限。**正确性不受影响**（两侧都有 `FOR UPDATE` + 四元组 + 二层幂等兜底） |
| **Business-Technical Impact** | 大促 / 批量导入时的审核积压；`dataclean` 组在 Go 侧 8 并发，迁到 Java 后退化为 1 |
| **Likely Cause** | `application.yml:70` 的注释「Go `Consumers: 1` —— 单消费者；Spring 默认 concurrency=1，行为一致」**只看了 `Consumers`**（那控制的是 reader 协程数，不是 handler 协程数）。 |
| **Must Preserve** | `Processors = 8` 的 handler 并发度 |
| **Recommended Verification** | 真 Kafka 灌 N 条慢消息，测两端到端延迟曲线。 |
| **Evidence** | Go `kq/config.go:19` + `kq/queue.go:220` / Java `ArticleRpcApplication.java:189-203` + `application.yml:66-73` |

**反事实测试**：写 `concurrency=1`（或不写）是默认行为 ⇒ **会**自然写成这样。判定为 HIGH-RISK 的理由是**注释给出了错误的 Go 依据**，会误导后续 review。

---

## S-5 🔴 HIGH-RISK · content security 端口无实现 ⇒ 审核链路无法完成

| 栏 | 内容 |
|---|---|
| **Component** | 外部客户端占位（content security） |
| **Operation** | `SanitizeContent` / `DetectImageAd` |
| **Category** | `SEMANTIC MISMATCH` |
| **Go Behavior** | `svc/service_context.go:60` `zrpc.MustNewClient(c.SecurityConf)`（失败即 panic）；`:72-73` 产出**两个可用实例**（content + image，同一 channel）。消费点 `article_consumer.go:158-166`（4 个开关全 true）、`:253-259`（`ConfidenceThreshold: 0.7`, `EnableTextExtraction: true`） |
| **Java Behavior** | `client/SecurityRpcClient.java` 与 `client/ImageSecurityRpcClient.java` **各只有 4 行**（`package` + `import`，**无任何实现**）。唯一实现是 `ContentSecurityAutoConfiguration.java:31-35` 用 `@ConditionalOnMissingBean` 兜底注册的 `UnavailableContentSecurityClient`，其两个方法（`:62-75`）**无条件抛 `SecurityAuditException`** |
| **Difference** | Go 审核链路可走通；RAG **100% 不可走通**。叠加 S-3 的「失败不提交位点」⇒ 消息被无限重投，文章永远停在 `REVIEWING` |
| **Why It Matters** | `article-rpc` 的**全部业务价值**（审核 → 发布 → 推荐同步）都挂在这条链路上 |
| **Observable Impact** | 无任何文章能被发布；`article_revision` / `article_publication` / `article_domain_outbox` 永不增长；Kafka 审核 topic 持续积压 |
| **Business-Technical Impact** | 整个社区发布功能不可用。这属于 AGENTS.md §0 列出的「security 域从未做过 Go ↔ RAG 对照」的必然结果，但**必须显式登记**，不能被一条绿测试掩盖 |
| **Likely Cause** | security 域尚未迁移（AGENTS.md §0 明确列为未验证子模块），此处只落了端口接口与「fail-closed」兜底 |
| **Must Preserve** | Go 的两条分支：`SanitizeContent` 的 **`!result.Success ⇒ 重投`**（`article_consumer.go:171-173`）与 `DetectImageAd` 的 **`!resp.GetSuccess() ⇒ 重投`**（`:263-265`）。当前 `ContentSecurityPort.TextAuditResult`（`ContentSecurityPort.java:89-124`）**没有 success 字段**，落地真实实现时这两个分支无处安放 |
| **Recommended Verification** | security-rpc 落地后：① 双侧对拍 `SanitizeContent` 的四个开关与返回码语义；② 在 `ArticleReviewConsumerTest` 之外补一条「服务返回 Success=false ⇒ 抛错而非放行」的用例 |
| **Evidence** | Go `svc/service_context.go:60,72-73` + `article_consumer.go:154-173,253-267` / Java `client/SecurityRpcClient.java`（4 行）、`client/ImageSecurityRpcClient.java`（4 行）、`UnavailableContentSecurityClient.java:62-75`、`ContentSecurityPort.java:89-124` |

⚠️ **这条绿测试锁住了功能缺失**：`ArticleSpringContextWiringTest.java:248-266`
断言 `assertInstanceOf(UnavailableContentSecurityClient.class, port, …)`。
即「security 不可用」被当成**期望状态**钉死；将来真的接上 security-rpc，这条测试会红，
并让人误以为「修复」才是漂移方向。

---

## S-6 · SQL 超时的 gRPC code 被降级 ⇒ 业务码 5001 → 1015

| 栏 | 内容 |
|---|---|
| **Component** | RPC 服务端 × 配置与生命周期 |
| **Operation** | 任一 DB 调用撞上 70s deadline |
| **Category** | `SEMANTIC MISMATCH` |
| **Go Behavior** | 按调用点分两种：<br>① 公开读 `FindPublicOne` 失败 ⇒ `status.Error(codes.Internal, "read publication failed")`（`get_article_logic.go:133`）⇒ api `case codes.Internal` ⇒ **`ErrorServerCommon(5001)`**<br>② 作者读 `FindOne` 失败 ⇒ **裸 err**（`get_article_logic.go:62`）⇒ grpc-go 转 `UNKNOWN` ⇒ api `default` ⇒ **`CodeServerBusy(1015)`** |
| **Java Behavior** | `SqlTimeoutStatusTranslator.java:68` 抛 **`Status.DEADLINE_EXCEEDED`（即 `StatusRuntimeException`）**；但 `ArticleServiceGrpcImpl.java:133-137` 的 `catch (RuntimeException e)` **先于**任何 status 处理命中它，改写成 `Status.UNKNOWN` |
| **Difference** | Java 侧**所有** DB 超时都被压成 `UNKNOWN` ⇒ 一律 1015。Go 侧的公开读超时是 `INTERNAL` ⇒ 5001。 |
| **Why It Matters** | 5001 与 1015 是**两套告警口径**（系统内部错误 vs 服务繁忙），混用会让 DB 抖动被误判为流量过载 |
| **Observable Impact** | HTTP 信封里的业务码：`5001` → `1015`（仅公开读路径）。作者读路径两侧都是 1015 ⇒ 不受影响 |
| **Likely Cause** | `ArticleServiceGrpcImpl` 的 javadoc（`:58-59`）写「其余 RuntimeException 也映射成 UNKNOWN 而非 INTERNAL，与 Go 的『非 status error 一律 UNKNOWN』规则一致」—— 但它把 **`StatusRuntimeException` 也当成了「非 status error」**。Go 侧 grpc-go 的 `status.FromError` 会**保留**已有 status，Java 侧没有对应机制。 |
| **Must Preserve** | `StatusRuntimeException` 应被识别并**透传**其 code（等价于 grpc-go 的 `status.FromError`）；`DEADLINE_EXCEEDED` 在 Go 的 api 映射里落 `default` ⇒ 1015，与 Go 一致 |
| **Recommended Verification** | 真 PG + 真代理：让某条 SQL 超时（临时调小 `observability.rpc-timeout`），断言 `observer.onError` 收到的 code 是 `DEADLINE_EXCEEDED` 而非 `UNKNOWN` |
| **Evidence** | Go `get_article_logic.go:62,133` + api `delete_article_logic.go:49-59` / Java `SqlTimeoutStatusTranslator.java:63-72` + `ArticleServiceGrpcImpl.java:125-137` |

---

# 二、OBSERVABLE DIFFERENCE（6 条）

## O-1 · Update / Delete 的 not-found 描述文案不同

| 栏 | 内容 |
|---|---|
| **Component** | RPC 服务端 |
| **Operation** | `UpdateArticle` / `DeleteArticle`（文章不存在时） |
| **Category** | `OBSERVABLE DIFFERENCE` |
| **Go Behavior** | `FindOne` 的 `if err != nil { return nil, err }` **裸返回** `gorm.ErrRecordNotFound`，其 `Error()` 字面量是 **`"record not found"`** ⇒ grpc-go 包成 `UNKNOWN + "record not found"`。注意 `update_article_logic.go:59-63` / `delete_article_logic.go:53-57` 里的 `if article == nil { NotFound }` 分支**不可达**——`FindOne` 从不返回 `(nil, nil)`（`model/article.go:81-88`） |
| **Java Behavior** | `ArticleExceptions.raw(new IllegalStateException("article not found: " + id))` ⇒ `UNKNOWN + "article not found: <id>"`。`ArticleDomainService.java:298`、`:473` |
| **Difference** | **code 相同（UNKNOWN）**，**description 不同**。且 Java 文案额外泄漏了请求里的 article_id |
| **Why It Matters** | 排查线上问题时按 Go 的 `record not found` 建的日志检索规则，在 Java 侧匹配不到 |
| **Observable Impact** | 仅 gRPC description 与业务日志。**HTTP 业务码两侧都是 1015**（Go api `default` 分支只取 code） |
| **Business-Technical Impact** | 低。但「把 id 拼进错误文案」在多租户/对外 API 场景下是信息泄漏面 |
| **Likely Cause** | Java 实现知道 Go 这条路径是「裸返回」（`ArticleExceptions.java:155-176` 已列出 `update:57` / `delete:51`），但把「裸返回」重新包装成了带自定义文案的新异常 |
| **Must Preserve** | code = `UNKNOWN`（已保持 ✅）；文案若要对齐须用 `record not found` |
| **Recommended Verification** | 对拍：不存在的 id ⇒ 比较两侧 status code + description |
| **Evidence** | Go `internal/model/article.go:81-88` + `internal/logic/update_article_logic.go:53-63` / `internal/logic/delete_article_logic.go:47-57` / Java `ArticleDomainService.java:296-299,471-474` |

---

## O-2 · 指标全名改变（命名空间前缀丢失 + Micrometer 后缀）

| 栏 | 内容 |
|---|---|
| **Component** | 指标组件 |
| **Operation** | 全部 6 个指标的 `/metrics` 输出 |
| **Category** | `OBSERVABLE DIFFERENCE` |
| **Go Behavior** | `Namespace = "article_service"`（`metrics.go:6`）作为**全部指标名的前缀**：`article_service_article_total`、`article_service_article_status_total`、`article_service_file_upload_total`、`article_service_minio_request_duration_seconds`、`article_service_minio_request_errors_total`、`article_service_kafka_push_errors_total` |
| **Java Behavior** | `ArticleRpcMetrics.java:37` `NAMESPACE = "article_service"` **声明后从未引用**；`Counter.builder("article_total")` 不带前缀，Micrometer 再自动补后缀 ⇒ 实际输出 `article_total_total`、`article_status_total_total`、`file_upload_total_total`、`minio_request_duration_seconds_seconds`、`minio_request_errors_total_total`、`kafka_push_errors_total_total` |
| **Difference** | **6 个指标的 Prometheus 全名全部改变**。标签名保持一致 |
| **Why It Matters** | Prometheus 抓取的是**全名**。既有 dashboard / 告警规则 / recording rule 全部失配 |
| **Observable Impact** | `/actuator/prometheus` 输出名不同；Grafana 面板无数据 |
| **Business-Technical Impact** | 迁移后所有 article 域告警静默失效（Prometheus 不会报错，只会「没有数据」） |
| **Likely Cause** | 把 `NAMESPACE` 常量抄过来了却没接上；Micrometer 的命名规则（自动加 `_total` / 时钟加单位后缀）没有对照 Go 的 `prometheus.NewCounterVec` 验证 |
| **Must Preserve** | 指标**名**与**标签**是运维可观测契约（AGENTS.md §3.2）。要么用 `management.metrics.tags`/Prometheus registry config 补出前缀，要么逐条改名 |
| **Recommended Verification** | 起真容器后 `curl /actuator/prometheus`，与 Go 的 `/metrics` 输出逐行比对全名 |
| **Evidence** | Go `metrics/metrics.go:6,11-18,21-28,31-38,41-49,52-59,62-69` / Java `metrics/ArticleRpcMetrics.java:37,66,80,88,110,121,138` |

---

## O-3 · 作者视角计阅读量失败时多记一个 MinIO 错误指标

| 栏 | 内容 |
|---|---|
| **Component** | RPC 服务端 × 指标组件 |
| **Operation** | `GetArticle`（作者视角, `incr_view=true`），`IncrViewCount` 执行失败 |
| **Category** | `OBSERVABLE DIFFERENCE` |
| **Go Behavior** | `get_article_logic.go:70-78`：`IncrViewCount` 失败 ⇒ **只 `LogBusinessErr`，不碰任何指标**，然后 `article.ViewCount++` 继续返回 |
| **Java Behavior** | `ArticleDomainService.java:212-214`：catch 里调 `metrics.minioError("get")`，注释写「计数口径对齐 Go 的 ErrorDbUpdate 埋点习惯」 |
| **Difference** | 这是**一次 DB 写失败**，却被记到 `minio_request_errors_total{operation="get"}` 上。Go 侧该指标在这条路径上永远是 0 |
| **Why It Matters** | `minio_request_errors_total` 是判断「对象存储是否故障」的核心信号。DB 抖动会污染它，导致 MinIO 故障告警误报 |
| **Observable Impact** | 该 counter 数值偏高；MinIO 告警可能误触发 |
| **Likely Cause** | 实现者想「失败总得留个痕迹」，选了手边现成的指标；注释里的「对齐 Go 的习惯」找不到对应 Go 代码（Go 这里根本没有埋点） |
| **Must Preserve** | Go 的口径：`IncrViewCount` 失败**只记日志**，不动指标 |
| **Recommended Verification** | 让 `incrViewCount` 抛异常（真 PG 上对已删行执行），断言 `minio_request_errors_total{get}` 不增长 |
| **Evidence** | Go `internal/logic/get_article_logic.go:70-78` / Java `service/ArticleDomainService.java:209-216` |

---

## O-4 · 发布成功通知的 `extra.author_id` 从原串变成数字串

| 栏 | 内容 |
|---|---|
| **Component** | MQ 消费者（同步结果） |
| **Operation** | `notifyArticlePublished` |
| **Category** | `OBSERVABLE DIFFERENCE` |
| **Go Behavior** | `article_sync_result_consumer.go:205-208`：`Extra: map[string]string{"article_id": articleID, "author_id": authorID}` —— `authorID` 是**数据库里那个原始字符串** |
| **Java Behavior** | `MessageRpcClient.java:90` `putExtra("author_id", String.valueOf(recipientId))` —— `recipientId` 是 `:161` 解析出来的 `long` |
| **Difference** | 对**规范十进制** id 完全相同；对前导零 / 非规范写法（`"0012345"`、`" 123"`）会不同 |
| **Why It Matters** | 下游（通知服务）按 `author_id` 回链用户；两侧不一致会让回链失败 |
| **Observable Impact** | 仅非规范 id 场景下 `extra` 字段值不同 |
| **Likely Cause** | Java 的通知入口签名收 `long`，原始字符串在 `parsePositiveUid` 后就丢了 |
| **Must Preserve** | 传**原串** |
| **Recommended Verification** | 造一条 `author_id='0012345'` 的数据，走完整发布回调，断言通知 payload 逐字一致 |
| **Evidence** | Go `internal/mqs/article_sync_result_consumer.go:186,205-208` / Java `mq/ArticleSyncResultConsumer.java:161` + `client/MessageRpcClient.java:90` |

---

## O-5 · Flyway 账本与其它模块共用（三模块撞号）

| 栏 | 内容 |
|---|---|
| **Component** | 配置与生命周期（DDL 迁移） |
| **Operation** | 进程启动时的建表 |
| **Category** | `OBSERVABLE DIFFERENCE` |
| **Go Behavior** | `AutoMigrate`（`db.go:55-58`）—— **无状态、幂等**。Go 两个 rpc 都建同一批表也不冲突 |
| **Java Behavior** | Flyway，`flyway_schema_history` 为**默认表名**；`spring.flyway.table` **全仓零命中**。实测版本号占用：`message-rpc` V1–V3、`like-rpc` V1–V5、`article-rpc` V8–V10 |
| **Difference** | 三者同库同 schema 共用**一本账**。`like-rpc` 先启动 ⇒ `flyway_schema_history` 已有 V1–V5 ⇒ `message-rpc` 启动时它的 V1–V3 被判定「已应用」⇒ **message 的三张表永不创建**，且**不报错** |
| **Why It Matters** | 表缺失只在第一次真正执行 SQL 时才炸 |
| **Observable Impact** | 启动日志正常、健康检查正常；首次访问报错。article-rpc 是第 3 个占用者，冲突面比已登记的 2 个更大 |
| **Likely Cause** | AGENTS.md §6.3 问题 4 已定案「每模块独立账本表名 `spring.flyway.table: flyway_schema_history_<模块>`」但**代码一行未改**（§0 与 §6.3 均标注「本条仍为未修复」） |
| **Must Preserve** | Go 的无状态性 ⇒ 或按已定案配置独立账本，或统一改回幂等重放 `SchemaInitializer`（users 域已用后者） |
| **Recommended Verification** | 一个 PG 容器里按序启动 like-rpc → message-rpc → article-rpc，断言 5+3+5 张表全部存在（现状：`tools/difftest/check-flyway-ledger.py` 守门，未修复态 `EXIT=1`） |
| **Evidence** | Go `internal/model/db.go:55-58` / Java `service/article/article-rpc/src/main/resources/application.yml:48-57` + 全仓 grep `spring.flyway.table` 零命中 + `ls` 各模块 `db/migration` 目录 |

---

## O-6 · message-rpc 客户端超时 3s vs Go 的 2000ms

| 栏 | 内容 |
|---|---|
| **Component** | 配置与生命周期（gRPC client） |
| **Operation** | 发布成功后的 `SendNotification` |
| **Category** | `OBSERVABLE DIFFERENCE` |
| **Go Behavior** | `etc/article.yaml` 的 `MessageRpc:` **没有 `Timeout`** ⇒ 走 go-zero 默认值 **`Timeout int64 \`json:",default=2000"\``**（`go-zero@v1.10.3/zrpc/config.go:31`），即每次调用 2s deadline |
| **Java Behavior** | `application.yml:119-122` 显式 `deadline: 3s` |
| **Difference** | 2s vs 3s |
| **Why It Matters** | 通知是发布成功后的 best-effort 调用（Go `:210-212` 只记日志），超时时长只影响调用方的等待时长与连接占用 |
| **Observable Impact** | Java 侧通知调用最多多等 1s；两侧都只记日志，不影响发布结果 |
| **Likely Cause** | `application.yml:119-121` 的注释写「走 zrpc 的默认值（3s，见 go-zero v1.10.2 zrpc/internal/config.go:14 的 default tag）」。**该引用是错的**：`zrpc/internal/config.go:14` 落在 `ClientMiddlewaresConf` 结构体里，那个 `Timeout bool \`json:",default=true"\`` 是**中间件开关**，不是超时值；真正的 2000 在 `zrpc/config.go:31`。3s 恰是 `rest/config.go:55` 的 `RestConf.Timeout`（HTTP 侧），被串到了 RPC 侧 |
| **Must Preserve** | 2s |
| **Recommended Verification** | 对拍：让 message-rpc 挂起，测两侧调用返回时间；或直接读回 channel 的 default deadline |
| **Evidence** | Go `etc/article.yaml`（`MessageRpc` 段无 Timeout）+ `go-zero@v1.10.3/zrpc/config.go:31`（消费点=client 每次调用设 deadline） / Java `service/article/article-rpc/src/main/resources/application.yml:122` |

⚠️ 这是 §3.3.2 的典型形态：**以「Go: …」措辞写的断言 + 错的 `文件:行号`**。
按 AGENTS.md §3.3.2，该措辞未满足 §3.3.1 三条要求，**不应以「Go: …」形式留存**。

---

## 修复状态（2026-10-23 夜，追加于验证之后）

| ID | 状态 | 做法 |
|---|---|---|
| S-1 | ✅ 已修 | 移除 `getPublicArticle` 上的 `@Transactional(readOnly=true)`（Go 该路径本就无事务），并留痕说明**不可加回** |
| S-2 | ✅ 已修 | ORDER BY 方向改为 `SortBy` 为空时恒 `DESC`，公开路径规则不动 |
| S-3 | ✅ 已修 | `setSeekAfterError(false)` → **`true`**，javadoc 改为按 `ForceCommit` 默认值论证 |
| S-4 | ✅ 已修 | `factory.setConcurrency(8)`（`PROCESSOR_CONCURRENCY` 常量） |
| S-5 | ⛔ **阻塞** | security 域未迁移，无法实现；已把 `ContentSecurityPort` 补上 `success` 字段（S-5b），使 Go 的 `!Success` 分支不再无处安放 |
| S-6 | ✅ 已修 | `ArticleServiceGrpcImpl.invoke` 增加 `catch (StatusRuntimeException)` 透传，放在 `catch(RuntimeException)` **之前** |
| S-7 | ✅ 已修 | `<if>` 条件与 `@Param` 名对齐（`secondaryTagJson`） |
| O-1 | ✅ 已修 | 新增 `ArticleExceptions.articleNotFoundRaw()` → `Raw("record not found")` |
| O-2 | ✅ 已修 | 6 个 meter name 写掉 Micrometer 后缀，输出与 Go 全名逐条一致 |
| O-3 | ✅ 已修 | 移除 DB 失败时的 `minioError("get")`，改为仅记日志 |
| O-4 | ✅ 已修 | `sendArticlePublished` 增加 `authorId` 原串入参 |
| O-5 | ❌ **证伪** | 见 README §1b，不是缺陷 |
| O-6 | ✅ 已修 | `deadline: 3s` → `2s`，注释改为指向 `zrpc/config.go:31`（消费点） |
| U-2 | 🟡 部分 | `retractAndUpdate` 增加 `selectByIdForUpdate`，四条写路径锁序统一为 article → publication；真并发用例仍未写 |

---

# 三、HIGH-RISK DRIFT 汇总

| ID | 为什么「没看过 Go 源码也会这么写」成立 | 为什么危险 |
|---|---|---|
| **S-1** | `@Transactional(readOnly=true)` 是读路径的**标准 Spring 写法** | 100% 失败，且不报任何装配错；现有 79 条测试因用 Mockito/未覆盖该组合而全绿 |
| **S-2** | 白名单 + 无条件方向是最自然的 Java 排序写法 | 只让默认列表顺序颠倒，没有任何信号 |
| **S-3** | `seekAfterError(false)` 看起来像「更安全」 | 真实 Go 行为相反；且**现有绿测试正在给错误方向加固** |
| **S-5** | 端口 + 兜底实现是标准的「依赖未就绪」写法 | 整个发布链路不可用；`ArticleSpringContextWiringTest` 把「不可用」钉成了期望 |

# 四、必须登记为「已签字有意偏离」的候选

按 AGENTS.md §1.1（Go 是唯一真源，含其瑕疵），下列偏离**要么修，要么走 signed-deviations 签字**，
不能只写在实现注释里：

- **S-2** —— 已在 `ArticleMapper.java:147-152` 自称「Go 的实现瑕疵」，但**未进** `docs/DRIFT/_cross/signed-deviations.md`
- **S-3** —— 已在 `ArticleRpcApplication.java:101-160` 用「Go 侧真源」措辞论证，但**依据被证伪**
- **S-7（候选）** —— `ArticlePublicDomainServiceViewTest.java:24-34`、`ArticleMapper.java:145-156` 等处
  大量以「Go: …」形式书写的断言，其中 S-2 相关一条已被本次取证证伪 ⇒ 按 §3.3.2 应改为中性描述