# 等价矩阵

> `Result` 列只取：`EQUIVALENT` / `IMPLEMENTATION DIFFERENCE` / `OBSERVABLE DIFFERENCE` / `SEMANTIC MISMATCH` / `UNKNOWN`
> 行号 = 消费点 / 判定点。
> 维度取自 §「重点维度」，按各 operation 的实际情况选取，不硬凑。

---

## 一、RPC 服务端

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C1-01 | `CreateArticle` | Transaction Boundary | 无事务，5 步独立 | 无 `@Transactional`，5 步独立 | `EQUIVALENT` | Go `create:50-130` / Java `ArticleDomainService.java:104-163` |
| EV-C1-02 | `CreateArticle` | State Semantics（初始） | `status=REVIEWING`，`ext_info` **8 键**，nonce=articleID | 同（`LinkedHashMap` 保序） | `EQUIVALENT` | Go `create:96-106` / Java `:122-142` |
| EV-C1-03 | `CreateArticle` | External Interaction（MinIO） | `<articlePath><id>.md`，`text/markdown`，**无 CacheControl** | 同 | `EQUIVALENT` | Go `create:59-68` / Java `:109-113` |
| EV-C1-04 | `CreateArticle` | Error Identity（DB 失败） | `status.Error(codes.Internal, err.Error())` → 业务码 **5001** | 无捕获 ⇒ 裸 RuntimeException ⇒ `UNKNOWN` → 业务码 **1015** | `OBSERVABLE DIFFERENCE` | Go `create:109-113` / Java `:147` + `ArticleServiceGrpcImpl.java:133-137` + Go api `delete_article_logic.go:49-59` |
| EV-C1-05 | `CreateArticle` | Error→去向（Kafka 失败） | `Internal`，**行不回滚** | `Internal`，行不回滚 | `EQUIVALENT` | Go `create:125-130` / Java `:154-160` |
| EV-C1-06 | `CreateArticle` | Concurrency | 雪花 ID 保证唯一，无锁 | 同 | `EQUIVALENT` | Go `create:50-56` / Java `:105-106` |
| EV-C1-07 | `CreateArticle` | Side Effect（指标） | 5 个指标 +1 | 5 个指标 +1 | `EQUIVALENT` | Go `create:69,72,76,114,115` / Java `:118,119,148,149` |
| EV-C1-08 | `GetArticle` 作者 | Branch 顺序 | `PublicOnly` → `requester+incrView` → 作者校验 | 同 | `EQUIVALENT` | Go `get:41,44,66` / Java `:184,193,204` |
| EV-C1-09 | `GetArticle` 作者 | Error Identity（not found） | `NotFound("article not found")` | 同 | `EQUIVALENT` | Go `get:56-59` / Java `:199-201` |
| EV-C1-10 | `GetArticle` 作者 | Error→去向（计阅读量失败） | **Continue**，只记日志 | Continue，只吞 | `EQUIVALENT`（行为）／`OBSERVABLE DIFFERENCE`（指标） | Go `get:70-78` / Java `:209-216`；指标差异见 **O-3** |
| EV-C1-11 | `GetArticle` 作者 | Concurrency（计阅读量） | `view_count = view_count + 1` 原子 | 同 | `EQUIVALENT` | Go `article.go:121-124` / Java `ArticleMapper.java:98-99` |
| EV-C1-12 | `GetArticle` 作者 | Serialization（`ext_info`） | 空 map ⇒ nil | 空 map ⇒ 不输出 | `EQUIVALENT` | Go `get:191-201` / Java `:256-258` |
| EV-C1-13 | `GetArticle` 公开 | **Transaction Boundary** | **无事务** | **`@Transactional(readOnly=true)`，内含 UPDATE** | **`SEMANTIC MISMATCH`** | Go `article_public.go:151-161`（无 tx） / Java `ArticlePublicDomainService.java:79` + `:119` + `ArticlePublicMapper.java:252` → **S-1** |
| EV-C1-14 | `GetArticle` 公开 | Branch（三分支） | 无指针+非PUBLISHED / 指针非published / 正常 | 同 | `EQUIVALENT` | Go `article_public.go:40-56` / Java `:92-107` |
| EV-C1-15 | `GetArticle` 公开 | External Interaction | 有 revision ⇒ **不读 MinIO**；legacy ⇒ 读 MinIO，两条失败文案同一 | 同 | `EQUIVALENT` | Go `get:137-155` / Java `:110-116, 214-220` |
| EV-C1-16 | `GetArticle` 公开 | Error Identity（自增失败） | `Internal("increment public view failed")` → 5001 | 走到 UPDATE 即被 25006 打断 ⇒ `UNKNOWN` → 1015 | **`SEMANTIC MISMATCH`**（S-1 的表现） | Go `get:160` / Java + `ArticleServiceGrpcImpl.java:133-137` |
| EV-C1-17 | `GetArticle` 公开 | Result（字段来源） | 有 revision 取 revision；legacy 取 article；status 恒 PUBLISHED；update_time 取 published_at | 同 | `EQUIVALENT` | Go `get:170-189` / Java `:242-277` |
| EV-C1-18 | `UpdateArticle` | Error Identity（not found） | `UNKNOWN` + `"record not found"` | `UNKNOWN` + `"article not found: <id>"` | `OBSERVABLE DIFFERENCE` | Go `article.go:81-88` + `update:53-57` / Java `:296-299` → **O-1** |
| EV-C1-19 | `UpdateArticle` | Branch A/B/C 顺序与条件 | A 优先于 B；B 的 `SetSyncState` 第 4 参传 `""` | 同 | `EQUIVALENT` | Go `update:80-123`（`:87` 传 `""`） / Java `:311-349`（`:330` 传 `""`） |
| EV-C1-20 | `UpdateArticle` | Transaction Boundary | A 有 tx（retract→update→outbox）；B/C 无 | 同（A 走 `ArticleAtomicService.retractAndUpdate`） | `EQUIVALENT` | Go `update:216-227` / Java `ArticleAtomicService.java:112-125` |
| EV-C1-21 | `UpdateArticle` | State Semantics（对象命名） | `<path><id>-<sha256(md)>.md` | 同 | `EQUIVALENT` | Go `update:192-195` / Java `:413-422` |
| EV-C1-22 | `UpdateArticle` | Precondition（`secondary_tags`） | `len() > 0` | `getSecondaryTagsCount() > 0` | `EQUIVALENT` | Go `update:184` / Java `:405` |
| EV-C1-23 | `DeleteArticle` | Precondition（鉴权） | 空 operator 与非作者 ⇒ 同一码同一文案 | 同 | `EQUIVALENT` | Go `delete:58-62` / Java `:476-478` |
| EV-C1-24 | `DeleteArticle` | State Semantics（软删） | 软删，**不删对象** | 同 | `EQUIVALENT` | Go `delete:64-66,84-96` / Java `ArticleAtomicService.java:134-137,159` |
| EV-C1-25 | `DeleteArticle` | Transaction Boundary（顺序） | retract → outbox → softDelete | 同 | `EQUIVALENT` | Go `delete:85-91` / Java `ArticleAtomicService.java:150-159` |
| EV-C1-26 | `DeleteArticle` | Concurrency（锁） | 事务内**不重取**文章；publication 行 FOR UPDATE | 事务内**额外** `article FOR UPDATE`；publication 行 FOR UPDATE | `UNKNOWN` | Go `delete:84-91` vs Java `ArticleAtomicService.java:142` → **U-2**（锁顺序反转 / 死锁风险） |
| EV-C1-27 | `ListArticles` 作者 | **Branch（排序）** | `SortBy==""` ⇒ **恒 `created_at desc`**（desc 拼接在 `if SortBy!=""` 内） | `SortBy==""` ⇒ `created_at` + `<when desc>DESC else ASC>` ⇒ **默认 ASC** | **`SEMANTIC MISMATCH`** | Go `article.go:161-176`（消费点） / Java `ArticleMapper.java:171-181` → **S-2** |
| EV-C1-28 | `ListArticles` 作者 | Branch（`sortBy` 非空） | `view_count/like_count` 白名单，未知值回落 `created_at` + 方向 | 同 | `EQUIVALENT` | Go `article.go:163-175` / Java `:172-181` |
| EV-C1-29 | `ListArticles` 作者 | Pagination | offset 夹 0；page/page_size 原样回显 | 同 | `EQUIVALENT` | Go `article.go:156-159` + `logic:98-99` / Java `:526-529,545-546` |
| EV-C1-30 | `ListArticles` 作者 | Result（`markdown_content`） | 填**对象 key**（Go 自身的不一致，照搬） | 同 | `EQUIVALENT` | Go `logic:79` / Java `:538` |
| EV-C1-31 | `ListArticles` 作者 | Concurrency（count/page 快照） | 两条独立语句，无共同快照 | 同 | `EQUIVALENT` | Go `article.go:152,178` / Java `:524,531` |
| EV-C1-32 | `ListArticles` 公开 | **Transaction Boundary** | `Transaction(RepeatableRead, ReadOnly)` 包 count+page+revisions | `@Transactional(readOnly=true, isolation=REPEATABLE_READ)` | `EQUIVALENT` | Go `article_public.go:65,128` / Java `ArticlePublicDomainService.java:144` |
| EV-C1-33 | `ListArticles` 公开 | Branch（排序） | 字段 3 选项（含 `comment_count`）；方向恒随 `Desc`；**附加 `article.id` 同方向** | 同 | `EQUIVALENT` | Go `article_public.go:132-147` / Java `ArticlePublicMapper.java:167-182` |
| EV-C1-34 | `ListArticles` 公开 | Pagination | 先归一（<1⇒1, <1⇒20, >100⇒100）后回显 | 同 | `EQUIVALENT` | Go `article_public.go:92-100` + `logic:124-132` / Java `:149-156,199-200` |
| EV-C1-35 | `ListArticles` 公开 | Branch（标签过滤匹配 revision） | 匹配 revision，legacy 回落 article | 同 | `EQUIVALENT` | Go `article_public.go:67-85` / Java `ArticlePublicMapper.java:107-119,150-162` |
| EV-C1-36 | `ListArticles` 公开 | Result（无正文） | markdown 恒 `""` | 同 | `EQUIVALENT` | Go `logic:122` / Java `:192` |
| EV-C1-37 | `UploadFile` | Configuration（content-type） | `mime.TypeByExtension`（**系统** mime.types） | `URLConnection.guessContentTypeFromName`（JDK 内置表） | `IMPLEMENTATION DIFFERENCE` | Go `upload:58-61` / Java `:618-626`（RAG 已在 `:563-569` 留痕） |
| EV-C1-38 | `UploadFile` | State Semantics（对象名） | `<imagePath><id><ext>`，ext 含点 | 同 | `EQUIVALENT` | Go `upload:55-56` / Java `:580-585` |
| EV-C1-39 | `UploadFile` | Serialization（CacheControl） | 仅图片带 `public, max-age=31536000, immutable` | 同 | `EQUIVALENT` | Go `upload:70` / Java `:592-593` |
| EV-C1-40 | `UploadFile` | Result（URL 三段 trim） | base 去右、bucket 两侧、object 去左；base 空 ⇒ `scheme://TrimSpace(endpoint)` | 同 | `EQUIVALENT` | Go `upload:93-108` / Java `ArticleObjectStorageClient.java:270-280` |
| EV-C1-41 | 全部 6 个 RPC | Error Identity（gRPC 边界） | 裸 error ⇒ `UNKNOWN`；`status.Error` ⇒ 其 code | `ArticleExceptions.Rpc` ⇒ 自带 code；`Raw` 及其它 RuntimeException ⇒ `UNKNOWN` | `IMPLEMENTATION DIFFERENCE` | Go `article_service_server.go:26-53` / Java `ArticleServiceGrpcImpl.java:120-138` |
| EV-C1-42 | **SQL 超时** | **Timeout Scope + Error Identity** | `FindPublicOne` 错 ⇒ `Internal` ⇒ 5001；`FindOne` 错 ⇒ 裸 ⇒ UNKNOWN ⇒ 1015 | `SqlTimeoutStatusTranslator` 产 `DEADLINE_EXCEEDED`（`StatusRuntimeException`），被 `invoke` 的 `catch(RuntimeException)` 改写为 `UNKNOWN` | **`SEMANTIC MISMATCH`** | Go `get:133` / `get:62` / Java `SqlTimeoutStatusTranslator.java:68` + `ArticleServiceGrpcImpl.java:133-137` → **S-6** |
| EV-C1-43 | 全部 6 个 RPC | Timeout Scope（服务端） | `Timeout: 70000`（移到 interceptor 强制） | `observability.rpc-timeout: 70s` + `GrpcServerConfigurer` 挂载 | `EQUIVALENT` | Go `article.go:36,51` + `observability/rpc.go:19-23` / Java `application.yml:228-229` + `ArticleRpcApplication.java:93-96` |

---

## 二、MQ 消费者

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C2-01 | 审核消费者 | **Retry（失败位点契约）** | `ForceCommit` 默认 **true** ⇒ `consumeOne` 报错后**仍执行** `CommitMessages` | `setSeekAfterError(false)` + `FixedBackOff(0,0)` ⇒ 失败位点不提交，重投 | **`SEMANTIC MISMATCH`** | Go `go-queue@v1.2.2/kq/config.go:24`（声明） + `kq/queue.go:230-242`（**消费点**） / Java `ArticleRpcApplication.java:171,173` → **S-3** |
| EV-C2-02 | 两个消费者 | **Concurrency（处理并发度）** | `Processors` 默认 **8** ⇒ 8 个并发处理协程 | `concurrency` 未设 ⇒ **1**（串行） | **`SEMANTIC MISMATCH`** | Go `kq/config.go:19` + `kq/queue.go:220` / Java `ArticleRpcApplication.java:189-203`（无 `setConcurrency`） → **S-4** |
| EV-C2-03 | 两个消费者 | Ack 粒度 | 逐条 `CommitMessages` | `AckMode.RECORD` | `EQUIVALENT` | Go `kq/queue.go:240` / Java `:200-201` |
| EV-C2-04 | 两个消费者 | Configuration（起始位点） | `Offset: first`（yaml 显式） | `auto-offset-reset: earliest` | `EQUIVALENT` | Go `etc/article.yaml` `Offset: first` + `kq/config.go:16`（默认 last，未触发） / Java `application.yml:69` |
| EV-C2-05 | 审核消费者 | Error→去向（5 丢弃分支） | JSON 失败 / 文章不存在 / 四元组 / 幂等×2 ⇒ nil | 全部正常 return | `EQUIVALENT` | Go `consumer:49-52,55-57,62-68,85-87,111-113` / Java `ArticleReviewConsumer.java:113-118,122-125,128-132,149-153` |
| EV-C2-06 | 审核消费者 | Error→去向（3 重投分支） | MinIO / 审核 RPC / 事务 ⇒ err | 全部 throw | `EQUIVALENT` | Go `consumer:70-81,89-91,133-136` / Java `:139-144,242-247,170-172` |
| EV-C2-07 | 审核消费者 | Precondition（四元组） | status + content + author + nonce 全等 | 同 | `EQUIVALENT` | Go `consumer:62-64` / Java `:187-193` |
| EV-C2-08 | 审核消费者 | Concurrency（行锁 + 二次幂等） | `WithArticleTx`：`Unscoped` + `FOR UPDATE` + 事务内二次五元组 + 二次幂等 | 同 | `EQUIVALENT` | Go `community_revision.go:81-89` + `consumer:105-113` / Java `ArticleAtomicService.java:248-268` + `ArticleMapper.java:205-207` |
| EV-C2-09 | 审核消费者 | State Semantics（入队后 ext_info） | `reco_queued / pending / reason / eventID / versionMs / ""` | 同 | `EQUIVALENT` | Go `consumer:118-127` / Java `ArticleAtomicService.java:275-282` |
| EV-C2-10 | 审核 | **External Interaction（content security）** | `zrpc.MustNewClient(SecurityConf)` + 两个可用服务实例 | 唯一实现恒抛异常；两个 gRPC 客户端是**空文件** | **`SEMANTIC MISMATCH`** | Go `svc:60,72-73` + `consumer:158,253` / Java `client/SecurityRpcClient.java`（4 行）、`client/ImageSecurityRpcClient.java`（4 行）、`UnavailableContentSecurityClient.java:62-75` → **S-5** |
| EV-C2-11 | 审核 | **Error Identity（服务返回失败）** | `!result.Success` ⇒ err（重投）；`!resp.GetSuccess()` ⇒ err | `ContentSecurityPort` **无 success 字段**，Java 消费者只看 `isAd()` | **`SEMANTIC MISMATCH`**（随 S-5） | Go `consumer:171-173,263-265` / Java `ContentSecurityPort.java:89-124` + `ArticleReviewConsumer.java:221-225` |
| EV-C2-12 | 审核 | State Semantics（拒稿） | `REJECTED` + `(rejected, failed, pending_sync_reason, "", now, content_rejected/image_rejected)` | 同 | `EQUIVALENT` | Go `consumer:174-182,194-202` / Java `:264-272` |
| EV-C2-13 | 审核 | Branch（文本不过即返回） | 不继续查图 | 同 | `EQUIVALENT` | Go `consumer:174-182` / Java `:222-225` |
| EV-C2-14 | 审核图片 | Processing（对象名解析） | `url.Parse` → 去首 `/` → `SplitN(...,2)` → `parts[1]`；段数<2 ⇒ err | `URI.create` → `getPath()` → `split("/",2)` → `parts[1]`；<2 ⇒ throw | `EQUIVALENT` | Go `consumer:221-230` / Java `:318-327` |
| EV-C2-15 | 审核图片 | Serialization（mime 默认） | 只认 png/webp，**其余 `image/jpeg`** | 同 | `EQUIVALENT` | Go `consumer:244-250` / Java `:345-354` |
| EV-C2-16 | 审核图片 | Serialization（dataURI） | `data:<mime>;base64,<b64>`（带 padding） | 同 | `EQUIVALENT` | Go `consumer:243,251` / Java `:336-337` |
| EV-C2-17 | 审核图片 | Branch（封面顺序） | 正文图之后追加封面 | 同 | `EQUIVALENT` | Go `consumer:184-187` / Java `:229-231` |
| EV-C2-18 | 审核图片 | External Interaction（读时点） | `minio-go` 先 HEAD ⇒ 立刻报错 | Java SDK 不校验存在性 ⇒ 补一次 `statObject` | `IMPLEMENTATION DIFFERENCE` | Go `consumer:70-74` / Java `ArticleObjectStorageClient.java:210-236`（有意对齐） |
| EV-C2-19 | 同步结果消费者 | Branch（4 丢弃分支） | JSON / 空 id（TrimSpace）/ 文章不存在 / 未知 op | 同 | `EQUIVALENT` | Go `result_consumer:42-48,51-55,65-67` / Java `ArticleSyncResultConsumer.java:86-96,100-105,107-115` |
| EV-C2-20 | 同步结果消费者 | Concurrency（判定在行锁内） | 全部判定 + 写入在 `WithArticleTx` 内 | 同 | `EQUIVALENT` | Go `result_consumer:74-153` / Java `ArticleAtomicService.java:319-381` |
| EV-C2-21 | 同步结果消费者 | Branch（判定链顺序 ①→⑥） | 已删 / 三条 no-op / 失败 / 非 REVIEWING / 6 项比对 / 发布 | 同序 | `EQUIVALENT` | Go `result_consumer:75,81-85,87-91,92-94,95-107,108-152` / Java `:322,330-335,340,346,350-370,431-477` |
| EV-C2-22 | 同步结果消费者 | State Semantics（revision 编号） | `max+1`，`<id>:r<n>`，`sync_event_id = eventID` | 同 | `EQUIVALENT` | Go `result_consumer:108-124` / Java `:432-449` |
| EV-C2-23 | 同步结果消费者 | State Semantics（delete 分支不对称） | `SetSyncState` 在 `DeletedAt` 判断之前；已删不写回 | 同 | `EQUIVALENT` | Go `result_consumer:167-176` / Java `:189-203` |
| EV-C2-24 | 同步结果消费者 | Lifecycle（通知时机） | 事务返回后才发；uid 解析失败只记日志；RPC 失败只记日志 | 同 | `EQUIVALENT` | Go `result_consumer:160-163,186-190,210-212` / Java `:134-146,160-167` + `MessageRpcClient.java:92-100` |
| EV-C2-25 | 同步结果消费者 | Serialization（通知 `extra.author_id`） | 传**原串** `authorID` | 传**解析后的数字串** | `OBSERVABLE DIFFERENCE` | Go `result_consumer:205-208` / Java `MessageRpcClient.java:90` → **O-4** |

---

## 三、MQ 生产 / outbox

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C3-01 | outbox 创建 | Serialization（payload） | `MustMarshalSyncEvent(event)` 一次性 | `writeJson(event)` 一次性；relay 推原文 | `EQUIVALENT` | Go `consumer:118-123` + `sender:30` / Java `ArticleAtomicService.java:510-517` + `ArticleSyncOutboxRelay.java:102-106` |
| EV-C3-02 | outbox 键 | Serialization | 三段各自 `TrimSpace`，`:` 连接 | 三段各自 `trim()` | `EQUIVALENT` | Go `article_sync_types.go:90-92` / Java `ArticleSyncEventTypes.java:149-155` |
| EV-C3-03 | `SendPending` | Configuration（捞取） | `status IN (0,2)` + `maxRetry>0` 时 `retry_count < maxRetry` + `created_at ASC` + `LIMIT` | 同 | `EQUIVALENT` | Go `article_sync_outbox.go:52-61` / Java `ArticleSyncOutboxMapper.java:64-71` |
| EV-C3-04 | `SendPending` | Branch（逐条独立） | 单条失败 ⇒ `MarkFailed` + `continue` | 同 | `EQUIVALENT` | Go `sender:31-38` / Java `ArticleSyncOutboxRelay.java:108-119` |
| EV-C3-05 | `SendPending` | Concurrency（Kafka key） | key = `AggregateID`（保证同文章分区内有序） | 同 | `EQUIVALENT` | Go `sender:30` / Java `:103-105` |
| EV-C3-06 | relay 调度 | Lifecycle | goroutine + ticker + `context.WithCancel` | `@Scheduled(fixedRate)` | `IMPLEMENTATION DIFFERENCE` | Go `article.go:91-113` / Java `ArticleSyncOutboxRelay.java:78-86` |
| EV-C3-07 | relay 配置兜底 | Configuration | `interval<=0⇒1s`，`batchSize<=0⇒100`（在 `main()`） | 兜底放在 relay 方法内（`@Scheduled` 不接受 ≤0） | `EQUIVALENT` | Go `article.go:62-69` / Java `:130-139` |
| EV-C3-08 | outbox 状态机 | State Semantics | `MarkSent` / `MarkFailed`（`retry_count+1`） | 同 | `EQUIVALENT` | Go `article_sync_outbox.go:63-82` / Java `ArticleSyncOutboxMapper.java:80-93` |
| EV-C3-09 | 超过 maxRetry | State Semantics | **永久搁置**，不进死信、不告警 | 同（无额外处理） | `EQUIVALENT` | Go `article_sync_outbox.go:56-58` / Java `ArticleSyncOutboxMapper.java:67` |
| EV-C3-10 | `SetSyncState` | State Semantics（条件赋值） | `versionMs>0` 才写版本；`syncReason!=""` 才写待同步原因；版本号存**字符串** | 同 | `EQUIVALENT` | Go `article_sync_types.go:82-87` / Java `ArticleSyncEventTypes.java:134-138` |

---

## 四、对象存储

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C4-01 | client 构造 | Lifecycle | `minio.New` 失败 ⇒ **panic** | SDK 基本不在构造期失败 | `IMPLEMENTATION DIFFERENCE` | Go `svc:35-41` / Java `ArticleObjectStorageClient.java:66-74` |
| EV-C4-02 | bucket bootstrap | Branch（不对称） | 创建成功 ⇒ 设 policy；失败+已存在 ⇒ **空分支**；失败+不存在 ⇒ 只记日志不 panic | 同（`ensureBucketWithPublicRead` 返回 false 两种情况，policy 仅成功时设置） | `EQUIVALENT` | Go `svc:43-56` / Java `:143-172` |
| EV-C4-03 | policy 内容 | Serialization | `s3:GetObject` / `Principal AWS:*` / 空 `Sid` | 逐字照搬 | `EQUIVALENT` | Go `svc:51` / Java `:180-184` |
| EV-C4-04 | `Secure` 语义 | Configuration | `minio.Options{Secure: UseSSL}` | 8.5.x 无 `secure()` ⇒ 按 `UseSSL` 拼 endpoint 协议 | `IMPLEMENTATION DIFFERENCE` | Go `svc:37` / Java `:51-83` |
| EV-C4-05 | `get` | Error→去向（失败时点） | minio-go 先 HEAD ⇒ 立刻 error | SDK 不校验 ⇒ 主动 `statObject` 探测 | `IMPLEMENTATION DIFFERENCE`（有意对齐） | Go `get:83-91` / Java `:226-236` |

---

## 五、持久化 / Repository

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C5-01 | 建表 | Lifecycle | `AutoMigrate`（**无状态幂等**） | Flyway（**有状态账本**） | `OBSERVABLE DIFFERENCE` | Go `db.go:55-58` / Java `application.yml:53-57` → **O-5** |
| EV-C5-02 | 社区表闸门 | Lifecycle（fail-fast） | 触发器缺失 ⇒ `error` ⇒ `panic` ⇒ 进程不启动 | `ApplicationReadyEvent` 抛 `IllegalStateException` ⇒ 启动失败 | `EQUIVALENT` | Go `db.go:61-71` / Java `ArticleSchemaGate.java:73-120` |
| EV-C5-03 | jsonb 往返 | Serialization | 自定义 `Valuer`/`Scanner`（`StringArray`/`JSONMap`） | `PGobject` + 两个 `BaseTypeHandler` | `EQUIVALENT` | Go `article.go:40-73` / Java `JsonbTypeHandler.java:58-63,70-93,100+` |
| EV-C5-04 | typeHandler 生效 | Lifecycle | GORM 自动应用 | `autoResultMap=true` **+** 自定义 `@Select` 全部 `@ResultMap` | `EQUIVALENT` | Java `ArticleEntity.java:46,81-82,117-118` + `ArticleMapper.java:43-48,81,185-186` |
| EV-C5-05 | 连接池 | Configuration | 20 / 5 / 5m / 30m | `maximum-pool-size: 20` / `minimum-idle: 5` / `idle-timeout: 300000` / `max-lifetime: 1800000` | `EQUIVALENT` | Go `db.go:75-78` / Java `application.yml:22-30` |
| EV-C5-06 | 软删可见性 | State Semantics | `FindOne` 带过滤 / `FindOneUnscoped` 不带 | `selectById` 带 `deleted_at IS NULL` / `selectByIdUnscoped` 不带 | `EQUIVALENT` | Go `article.go:81-97` / Java `ArticleMapper.java:42,80` |
| EV-C5-07 | 行锁 | Concurrency | `Unscoped` + `FOR UPDATE` | `SELECT ... FOR UPDATE`（无 `deleted_at` 过滤） | `EQUIVALENT` | Go `community_revision.go:84-85` / Java `ArticleMapper.java:205-207` |
| EV-C5-08 | 公开可见性谓词 | Branch | `article_public.go:20-26` | `ArticlePublicMapper.java:62-66`（+ CDATA 版 `:85`） | `EQUIVALENT` | 同上 |
| EV-C5-09 | `IncrPublicViewCount` | Concurrency（原子判定） | 单条带谓词 UPDATE，`RowsAffected==1` | 同 | `EQUIVALENT` | Go `article_public.go:151-161` / Java `ArticlePublicMapper.java:252-262` |
| EV-C5-10 | `@TableLogic` 缺席 | State Semantics | GORM `Save`/`Updates` 自动追加 `deleted_at IS NULL` | MP `updateById` **不追加** | `UNKNOWN` | Go `article.go:99-105` / Java `ArticleEntity.java:145-149` → **U-3** |
| EV-C5-11 | 时间列语义 | Serialization | DSN `TimeZone=Asia/Shanghai`；列 `timestamptz` | 列同为 `timestamptz`；写入用 UTC，读取经 `toInstant()` 归一 | `EQUIVALENT`（proto 面） | Go `db.go:31` + `V8__create_article.sql:36-38` / Java `application.yml:238` + `ArticleDomainService.java:244-245` |

---

## 六、配置与生命周期

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C6-01 | 配置绑定 | Configuration | struct + `conf.MustLoad` | `@ConfigurationProperties` | `IMPLEMENTATION DIFFERENCE` | Go `config/config.go:16-43` / Java `ArticleRpcProperties.java:49-402` |
| EV-C6-02 | 服务端超时 | Timeout Scope | `Timeout: 70000`（interceptor 强制） | `observability.rpc-timeout: 70s` + 显式 `GrpcServerConfigurer` | `EQUIVALENT` | Go `article.go:36,51` / Java `application.yml:228-229` + `ArticleRpcApplication.java:93-96` |
| EV-C6-03 | **message-rpc 超时** | Timeout Scope | 未配 ⇒ `RpcClientConf.Timeout` 默认 **2000ms** | `deadline: 3s` | `OBSERVABLE DIFFERENCE` | Go `go-zero@v1.10.3/zrpc/config.go:31` / Java `application.yml:122` → **O-6** |
| EV-C6-04 | security-rpc 超时 | Timeout Scope | yaml 显式 `10000` | `deadline: 10s` | `EQUIVALENT` | Go `etc/article.yaml` `SecurityConf.Timeout: 10000` / Java `application.yml:114` |
| EV-C6-05 | 服务发现 | Configuration | etcd key `security.rpc` / `message.rpc` | Nacos `discovery://security-rpc` / `message-rpc` | `IMPLEMENTATION DIFFERENCE` | Go `svc:60,74` / Java `application.yml:108-117` |
| EV-C6-06 | Flyway 账本 | Lifecycle | 无状态（AutoMigrate） | 共用默认 `flyway_schema_history`，`spring.flyway.table` 未设 | `OBSERVABLE DIFFERENCE` | Go `db.go:55` / Java `application.yml:53-57` + 全仓 grep 零命中 → **O-5** |
| EV-C6-07 | 起始位点 | Configuration | `Offset: first`（两个 consumer） | `auto-offset-reset: earliest` | `EQUIVALENT` | Go `etc/article.yaml` / Java `application.yml:69` |
| EV-C6-08 | 死配置 `HotEventPusher` | Configuration | 零调用点 | **刻意不绑定** | `EQUIVALENT` | Go `svc:27,68-71`（仅构造）/ `ArticleRpcProperties.java:42-47` |
| EV-C6-09 | `spring.jackson.time-zone` | Configuration | DSN `TimeZone=Asia/Shanghai` | 自定义 `ObjectMapper` 绕过 Boot 属性绑定 ⇒ **该键不生效** | `UNKNOWN` | Java `Jackson2ObjectMapperConfiguration.java:55-60` + `application.yml:238` → **U-4** |
| EV-C6-10 | `spring.jpa.*` | Configuration | 不存在 | pom 无 JPA starter ⇒ 残留死配置 | `EQUIVALENT`（无行为） | Java `application.yml:43-46` + pom artifactId 列表无 `data-jpa` |
| EV-C6-11 | 同启同停 | Lifecycle | `service.ServiceGroup` 四件套 | 容器托管（gRPC server / 2 listener / 1 scheduled） | `IMPLEMENTATION DIFFERENCE` | Go `article.go:38-80` / Java `ArticleRpcApplication.java:61-79` |

---

## 七、指标

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C7-01 | 全部 6 个指标 | Serialization（**指标全名**） | `article_service_` 前缀：`article_service_article_total` 等 | `NAMESPACE` **声明未用**；Micrometer 输出 `article_total_total`、`minio_request_duration_seconds_seconds` 等 | `OBSERVABLE DIFFERENCE` | Go `metrics.go:6,11-69` / Java `ArticleRpcMetrics.java:37,66,80,88,110,121,138` → **O-2** |
| EV-C7-02 | `ArticleStatusTotal` | State Semantics（不完整埋点） | **只有 `reviewing`** 有调用点 | 只有 `:149` create 一处 | `EQUIVALENT` | Go `create:115` / Java `ArticleDomainService.java:149` |
| EV-C7-03 | `MinioRequestDuration` | Configuration（buckets） | `[.005,.01,.025,.05,.1,.25,.5,1]` 秒 | 同（转 Duration） | `EQUIVALENT` | Go `metrics.go:46` / Java `ArticleRpcMetrics.java:40-48` |
| EV-C7-04 | `KafkaPushErrors` | Serialization（标签取值） | 标签名 `topic`，取值是**用途名** `article_review` / `article_sync_event` | 同 | `EQUIVALENT` | Go `create:127` / `sender:32` / Java `:157,445` + `ArticleSyncOutboxRelay.java:109` |
| EV-C7-05 | `MinioRequestErrors` | State Semantics（多记一次） | DB 计阅读量失败**只记日志** | 额外 `minioError("get")` | `OBSERVABLE DIFFERENCE` | Go `get:72-75` / Java `ArticleDomainService.java:212-214` → **O-3** |

---

## 八、外部客户端占位

| ID | Operation | Semantic Dimension | Go | Spring Boot | Result | Evidence |
|---|---|---|---|---|---|---|
| EV-C8-01 | content security 装配 | Lifecycle | `zrpc.MustNewClient` 失败 ⇒ panic | `@ConditionalOnMissingBean` ⇒ 兜底实现 | `OBSERVATIVE` → **`SEMANTIC MISMATCH`** | Go `svc:60` / Java `ContentSecurityAutoConfiguration.java:31-35` → **S-5** |
| EV-C8-02 | `SanitizeContent` | External Interaction | 4 个开关全 true 的真实 RPC | **无实现**，唯一端口恒抛 | **`SEMANTIC MISMATCH`** | Go `consumer:158-166` / Java `UnavailableContentSecurityClient.java:62-68` → **S-5** |
| EV-C8-03 | `DetectImageAd` | External Interaction | `ConfidenceThreshold: 0.7` + `EnableTextExtraction: true` | 无实现 | **`SEMANTIC MISMATCH`** | Go `consumer:253-259` / Java `:70-75` → **S-5** |
| EV-C8-04 | nil 保护 | Error→去向 | `SecurityRpc == nil` ⇒ error（fail-closed） | 兜底实现恒抛（fail-closed） | `EQUIVALENT`（方向） | Go `consumer:154-156` / Java `:63-75` |
| EV-C8-05 | `SendNotification` | External Interaction | 真实 gRPC | 真实 `@GrpcClient` | `EQUIVALENT` | Go `result_consumer:197-209` / Java `MessageRpcClient.java:53-54,93` |
| EV-C8-06 | `SendNotification` | Error→去向 | 捕获**任何** err，只记日志 | 只捕获 `StatusRuntimeException` | `IMPLEMENTATION DIFFERENCE` | Go `result_consumer:210-212` / Java `MessageRpcClient.java:95-100` |

---

## 统计

| Result | 条数 |
|---|---:|
| `EQUIVALENT` | 62 |
| `IMPLEMENTATION DIFFERENCE` | 7 |
| `OBSERVABLE DIFFERENCE` | 6（EV-C1-04 / O-1 / O-3 / O-4 / O-6 部分计入矩阵） |
| `SEMANTIC MISMATCH` | 6（S-1…S-6） |
| `UNKNOWN` | 4（EV-C1-26 / EV-C5-10 / EV-C6-09 / U-4） |

> 注：单行可能同时暴露多条差异（例如 `EV-C1-13` 同时是 Transaction Boundary 与 Error Identity 差异），
> 计数按**去重后的真差异条目**统计，见 [`semantic-drifts.md`](semantic-drifts.md)。