# Spring Boot 语义模型

> 模块：`/Users/pr/Documents/java&golang/RAG/service/article/article-rpc`
> 本文件**结构与 [`go-semantic-model.md`](go-semantic-model.md) 逐节对齐**，
> 每节都标注 Java 侧的 `文件:行号` 与与 Go 的判定。
> ⚠️ 本文件**不复述** Java 侧注释作为事实；注释只在其**结论被本次取证证伪**时被点名。

---

## 组件 1：RPC 服务端

### 1.1 进程装配 `ArticleRpcApplication.java`

| 维度 | Java |
|---|---|
| Lifecycle | `@SpringBootApplication` + `@MapperScan`（`:63`）+ `@EnableScheduling`（`:64`）+ `@ConfigurationPropertiesScan`（`:62`）+ `@Import`（`:65-78`） |
| gRPC 装配 | `@GrpcService` 自动注册；interceptor 需显式 `@Bean GrpcServerConfigurer`（`:93-96`）才挂上 server |
| Boot 4 依赖 | `spring-boot-kafka`（pom:125）、`spring-boot-flyway`（pom:165）、`minio`（pom:131）、`mybatis-plus-spring-boot3-starter`（pom:138） |
| Error | 容器启动失败即退出（与 Go panic 等价） |
| Kafka 错误契约 | `:162-175` `DefaultErrorHandler` + `FixedBackOff(0L, 0L)` + **`setSeekAfterError(false)`**（`:173`）；`:200-201` `AckMode.RECORD` |
| **判定** | ❌ 与 Go 的失败位点契约**相反** → **S-3**；并发度不同 → **S-4** |

### 1.2 `createArticle` `service/ArticleDomainService.java:104-163`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| Processing | ① `snowflake.nextId()`（`:105`，**无 try/catch**，Go 显式判 err ⇒ Internal）② `storage.put(<articlePath><id>.md, utf8(md), "text/markdown", null)`（`:112-113`）③ 组装实体，`status = REVIEWING_VALUE`（`:142`），`ext_info` 写 **8 键**（`:122-131`，`LinkedHashMap` 保序）④ `articleMapper.insert`（`:147`）⑤ `kafkaTemplate.send(reviewTopic(), articleId, json).get()`（`:155`） |
| Transaction | **无**（无 `@Transactional`） | ✅ 一致 |
| Error→去向 | MinIO 失败 ⇒ `Raw`（UNKNOWN）；insert 失败 ⇒ **无捕获，裸 RuntimeException ⇒ UNKNOWN**；Kafka 失败 ⇒ `Internal` | ⚠️ Go 的 insert 失败是 `codes.Internal`（`create:112`），Java 落到 `UNKNOWN` → **业务码 5001→1015** |
| Side Effect | `minioError{put}` / `observeMinio{put}` / `fileUpload{markdown}` / `article{create}` / `articleStatus{reviewing}` | ✅ 一致 |
| **判定** | ⚠️ DB 失败路径的 Error Identity 不同（并入 S-6 同族，见 `equivalence-matrix.md` EV-C2） |

### 1.3 `getArticle`（作者视角）`ArticleDomainService.java:182-232`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| Branch（顺序） | ① `:184` `PublicOnly` ⇒ 公开分支 ② `:193` `requesterId!="" && incrView` ⇒ `InvalidArgument` ③ `:204` 作者校验 ⇒ `PermissionDenied("article author differs")` | ✅ 顺序完全一致 |
| not-found | `:198-201` `selectById == null` ⇒ `articleNotFound()` = `NOT_FOUND("article not found")` | ✅ 与 Go `:56-59` 一致 |
| 浏览量 | `:209-216` 失败**只吞**（catch 里还多记了一个 minioError，见 O-3） | ⚠️ 指标多记一次 → **O-3** |
| 正文 | `:221-227` `storage.readString`；失败 ⇒ `Raw` | ✅ 与 Go 裸 err 一致 |
| **判定** | ⚠️ O-3 |

### 1.4 `getArticle`（公开视角）`service/ArticlePublicDomainService.java:79-129`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 可见性 | `:81` `selectPublicById`（`ArticlePublicMapper.java:88-93`，谓词逐字一致，含 legacy 回退） | ✅ |
| Branch | `:92-107` 三分支：无指针 + 非 PUBLISHED ⇒ `NotFound`；指针非 published/空版本 ⇒ `NotFound`；否则取 revision，`revision == null` ⇒ `Internal("read publication failed")` | ✅ 与 Go `model/article_public.go:40-56` 三分支一致 |
| 正文 | `:110-116` 有 revision ⇒ **不读对象存储**（`:111-112`）；legacy ⇒ `readLegacyMarkdown`，失败文案单一 `"read legacy article failed"` | ✅ |
| 浏览量 | `:118-126` `incrPublicViewCount` 返回 0 ⇒ `NotFound("article no longer published")` | ✅ 判据一致 |
| **Transaction** | **`:79` `@Transactional(readOnly = true)`** | ❌ **Go 无任何事务**，且该事务内含一条 **UPDATE** ⇒ **S-1（最严重）** |
| Result | `buildView`（`:242-277`）：`status` 恒 PUBLISHED、`update_time` 取 `revision.publishedAt`、`ext_info` 二选一 | ✅ 逐字段一致 |

### 1.5 `updateArticle` `ArticleDomainService.java:295-353`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| not-found | `:296-299` `selectById == null` ⇒ `ArticleExceptions.raw(IllegalStateException("article not found: " + id))` ⇒ **UNKNOWN + 描述含 id** | ⚠️ Go 是 `UNKNOWN + "record not found"` ⇒ **O-1** |
| 判据拆分 | `:311-313` `explicitNonPublish` = Go `:81` 条件的前三段 | ✅ |
| 分支 A | `:315-324` `prevPublished && explicitNonPublish` ⇒ `atomicService.retractAndUpdate(...)` | ✅ 与 Go `:82` 一致 |
| 分支 B | `:325-334` `prevPublished && sourceChanged` ⇒ `setSyncState(queued, pending_review, update, **""**, nowMs, "")` → REVIEWING → `setReviewNonce` → `updateById` → `enqueueReview` | ✅ 与 Go `:87-100` 一致（含第 4 参传空串） |
| 分支 C | `:335-349` default；`review = status==REVIEWING && (sourceChanged \|\| prevStatus != REVIEWING)`；`updateById` 后若 `review` 才 `enqueueReview` | ✅ 与 Go `:101-122` 一致 |
| 源字段 | `:369-410`：`secondary_tags` 用 **`getSecondaryTagsCount() > 0`**（`:405`） | ✅ 与 Go `:184` 的 `len() > 0` 一致 |
| 对象命名 | `:413-422` `<articlePath><id>-<sha256hex(md)>.md` | ✅ 与 Go `:192-195` 一致 |
| Transaction | A 走 `ArticleAtomicService.retractAndUpdate`（`@Transactional`）；B/C 无 | ✅ |

### 1.6 `deleteArticle` `ArticleDomainService.java:470-486`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| not-found | `:471-474` `Raw("article not found: " + id)` | ⚠️ **O-1** |
| 鉴权 | `:476` `operatorId.isEmpty() \|\| !operatorId.equals(authorId)` ⇒ `forbidden()` | ✅ 与 Go `:58` 短路一致 |
| 对象存储 | 不删（`ArticleAtomicService.java:134-137` 注释 + 无 delete 调用） | ✅ |
| Transaction | `atomicService.retractAndDelete`（`:138-160`） | ⚠️ 事务内**额外**做 `selectByIdForUpdate`（`:142`），Go 没有 → **锁顺序反转风险**（U-2） |
| 顺序 | `:150` retract → `:156` 建同步 outbox → `:159` `softDelete` | ✅ 与 Go `:85-91` 一致 |

### 1.7 `listArticles`（作者视角）`ArticleDomainService.java:510-548`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 筛选 | `:517-522` 三个条件 `!= null && !isEmpty()`；二级标签用 `CAST(? AS jsonb)` **参数绑定** | ✅ 语义等价（比 Go 的字符串拼接更安全，行为同） |
| 分页 | `:526-529` offset 负值夹 0；`:545-546` page/page_size **原样回显** | ✅ 与 Go `logic:98-99` 一致 |
| **排序** | `:531-533` 传 `sortBy`/`desc` 给 `ArticleMapper.selectAuthorView`；其 ORDER BY 在 `ArticleMapper.java:171-181`：字段白名单 `<otherwise>created_at</otherwise>` **+ `<when test="desc">DESC</when><otherwise>ASC</otherwise>`** | ❌ **S-2**：`sortBy==""` 且 `desc==false` 时 Java 得 `created_at ASC`，Go 得 `created_at desc` |
| Result | `:538` `toAuthorProtoArticle(a, a.getContent())` ⇒ `markdown_content` 填**对象 key** | ✅ 与 Go `logic:79` 的「Go 不一致」照搬 |
| Count/Page | `:524` `countAuthorView` 与 `:531` `selectAuthorView` **两条独立语句，无共同快照** | ✅ 与 Go 一致 |

### 1.8 `listArticles`（公开视角）`ArticlePublicDomainService.java:144-202`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| Transaction | `:144` `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` | ✅ 与 Go `article_public.go:65,128` 等价（**此路径无写入，readOnly 安全**） |
| Pagination | `:149-156` page<1⇒1、size<1⇒20、size>100⇒100；`:199-200` 回显归一值 | ✅ 与 Go `:92-100` / `logic:124-132` 一致 |
| **排序** | `ArticlePublicMapper.java:167-182`：字段 `view_count/like_count/comment_count` + `<otherwise>a.created_at`；方向 `<when desc>DESC else ASC`；**追加 `a.id` 同方向** | ✅ 与 Go `publicArticleOrder`（`:132-147`）**完全一致**（含 `SortBy==""&&!Desc ⇒ ASC`） |
| 谓词 | `ArticlePublicMapper.java:62-66` 与 `:85` 的 CDATA 版 | ✅ 逐字一致（CDATA 是为规避 `XMLLanguageDriver` 把 `<>` 当标签，属实现细节） |
| Result | `:192` `buildView(a, rev, "")` ⇒ markdown 恒空 | ✅ 与 Go `logic:122` 一致 |

### 1.9 `uploadFile` `ArticleDomainService.java:576-604`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 雪花 | `:577` `snowflake.nextId()`，**无错误分支**（Go `:48-52` 判 err ⇒ 裸 err） | ⚠️ 实现差异（雪花在 Java 侧不返回异常） |
| ext | `:580-584` `lastIndexOf('.')`；`dot >= 0` ⇒ 含点子串 | ⚠️ **Go 用 `filepath.Ext`：`"a/b.png"` ⇒ `.png`；Java 同。`".gitignore"` ⇒ Go `filepath.Ext(".gitignore")` = `".gitignore"`（整串！），Java `lastIndexOf('.')==0` ⇒ `".gitignore"`（整串）。同** |
| content-type | `:618-626` `URLConnection.guessContentTypeFromName("file"+ext)`，空 ⇒ `application/octet-stream` | ⚠️ **IMPLEMENTATION DIFFERENCE**：Go 用 `mime.TypeByExtension`（读**系统** mime.types），Java 用 JDK 内置表。常见扩展名一致，冷门扩展名可能不同。RAG 已在 `:563-569` 留痕 |
| CacheControl | `:592-593` `"public, max-age=31536000, immutable"`（**仅图片**） | ✅ 与 Go `:70` 一致 |
| URL | `ArticleObjectStorageClient.java:270-280` 三段 trim 方向不同；base 空 ⇒ `scheme://TrimSpace(endpoint)` | ✅ 与 Go `:93-108` 一致 |

---

## 组件 2：MQ 消费者

### 2.1 审核消费者 `mq/ArticleReviewConsumer.java:100-176`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 配置取 topic | `:107-108` 用 **`${}` 占位符**而非 SpEL `#{@bean}`（理由见 `:102-106`，属 AGENTS.md §3.3 已登记的求值时序问题） | ✅ 等价（读同一个 yml 键） |
| Branch 丢弃 | `:113-118` JSON 失败 ⇒ return；`:122-125` 文章不存在 ⇒ return；`:128-132` 四元组不匹配 ⇒ return；`:149-153` 幂等命中 ⇒ return | ✅ 与 Go `:49-68` / `:85-87` 一致 |
| 四元组 | `:187-193` `matchesReviewTuple`：status + content + author + nonce 全等 | ✅ 与 Go `:62-64` 一致 |
| Branch 重投 | `:139-144` MinIO 失败 ⇒ throw；`:242-247` 图片审核失败 ⇒ throw；`:141-145` `atomicService.queueSyncOutboxWithLock` 失败 ⇒ throw | ✅ 方向一致（抛异常 ⇒ 不提交位点） |
| 顺序 | MinIO 读（`:138`）**在**幂等检查（`:149`）**之前** | ✅ 与 Go `:70-87` 一致 |
| 并发 | `ArticleAtomicService.queueSyncOutboxWithLock`（`:244-284`）`@Transactional` + `selectByIdForUpdate`（`:248`） | ✅ 与 Go `WithArticleTx` 等价 |

### 2.2 `auditArticle` `ArticleReviewConsumer.java:220-253`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 外部端口 | `ContentSecurityPort`；**唯一实现是 `UnavailableContentSecurityClient`（恒抛 `SecurityAuditException`）** | ❌ **S-5**：Go 是真实 gRPC 客户端（`svc:72-73`） |
| **失败语义** | `:221` 只看 `text.isAd()`；**没有 `!result.Success ⇒ 抛错` 的对应分支** | ❌ Go `article_consumer.go:171-173` 有该分支，Java 端口契约里**没有 success 字段** |
| 拒稿 | `:264-272` `rejectAsAd(reason)`：`REJECTED` + `setSyncState(rejected, failed, **pending_sync_reason**, "", now, reason)` + `updateById` | ✅ 与 Go `:176` / `:196` 一致 |
| 文本不过即返回 | `:222-225` return | ✅ 与 Go `:174-182` 一致 |
| 封面顺序 | `:229-231` 追加在正文图之后 | ✅ 与 Go `:185-187` 一致 |

### 2.3 图片审核 `ArticleReviewConsumer.java:317-354`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 路径解析 | `:318-327` `URI.create` → `getPath()` 去首 `/` → `split("/", 2)` → **取 `parts[1]`**；段数 <2 ⇒ 抛异常 | ✅ 与 Go `:224-230` 一致 |
| mime | `:345-354` `.png`→png、`.webp`→webp、**其余 `image/jpeg`** | ✅ 与 Go `:244-250` 一致 |
| 正则 | `:67` `!\[.*?]\((.*?)\)`；`:293` 判据 `groupCount() >= 1` | ✅ 与 Go `:209` / `:213` 一致（`>= 1` 是对的，Go 判 `len(match) > 1` 即子匹配数 ≥1） |
| dataURI | `:336-337` `data:<mime>;base64,<b64>` | ✅ 与 Go `:251` 一致 |
| 覆盖 | `:331-333` 先 `statObject` 再 `getObject` | ✅ **实现差异**（为对齐 minio-go 的「立刻报错」时点，见 `ArticleObjectStorageClient.java:210-225`） |

### 2.4 同步结果消费者 `mq/ArticleSyncResultConsumer.java:77-205`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| Branch | `:86-90` JSON 失败 ⇒ return；`:94-96` `articleId == null \|\| trim().isEmpty()` ⇒ return；`:100-105` `selectByIdUnscoped == null` ⇒ return；`:107-115` 未知 op ⇒ return | ✅ 与 Go `:42-68` 一致 |
| 判定链 | `ArticleAtomicService.acceptSyncResult`（`:316-381`）① `:322` 已删 ② `:330-335` 三条 no-op ③ `:340` 失败 ④ `:346` 非 REVIEWING ⑤ `:350-370` 读回 payload + 6 项比对 | ✅ 与 Go `:75-107` 逐条一致（含顺序） |
| 六项比对 | `:364-368` op / eventId / versionMs / articleId / authorId（**共 5 项 + op**，Go `:103-105` 同为 5 个条件） | ✅ |
| revision | `:431-477` `highest+1`、`<id>:r<n>`、`sha256(approved.markdown)`、`publishedAt = now(UTC)` | ✅ 与 Go `:108-124` 一致 |
| 通知时机 | `ArticleSyncResultConsumer.java:134-146` 事务**返回后**才发 | ✅ 与 Go `:160-163` 一致 |
| delete 分支 | `:188-205` `setSyncState` 在 `deletedAt` 判断**之前**；已软删不写回 | ✅ 与 Go `:166-183` 一致 |
| 通知 uid | `:161-165` `parsePositiveUid`，`<= 0` ⇒ 只记日志并 return | ✅ 与 Go `:186-190` 一致 |
| 通知 extra | `MessageRpcClient.java:90` `putExtra("author_id", String.valueOf(recipientId))`（**解析后的数字**） | ⚠️ Go `article_sync_result_consumer.go:207` 传**原串** `authorID` ⇒ **O-4** |

---

## 组件 3：MQ 生产 / outbox

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 调度 | `ArticleSyncOutboxRelay.java:78` `@Scheduled(fixedRateString="${article-rpc.article-sync-outbox.poll-interval-ms:1000}")` | ✅ 替代 goroutine+ticker |
| 配置兜底 | `:130-139` `effectiveIntervalMs()` / `effectiveBatchSize()`：`<= 0 ⇒ 1000 / 100` | ✅ 与 Go `article.go:63-69` 一致 |
| 捞取 | `ArticleSyncOutboxMapper.java:64-71` `status IN (0,2)` + `maxRetry > 0` 时 `retry_count < maxRetry` + `created_at ASC` + `LIMIT` | ✅ 与 Go `article_sync_outbox.go:52-61` 一致 |
| 逐条 | `:98-120` 每条独立；失败 ⇒ `kafkaPushError{"article_sync_event"}` + `markFailed` + 继续下一条 | ✅ 与 Go `sender:29-43` 的 `continue` 一致 |
| 推送 | `:102-106` `send(articleSyncPusherConf.topic, **aggregateId**, **payload 原文**).get()` | ✅ 与 Go `sender:30` 一致（`.get()` 把 Spring 的异步 send 拉回同步语义） |
| 状态机 | `ArticleSyncOutboxMapper.java:80-93` `markSent`(status=1, last_error='') / `markFailed`(status=2, retry_count+1) | ✅ |
| 事件键 | `ArticleSyncEventTypes.java:149-155` 三段各自 `trim()` | ✅ 与 Go `article_sync_types.go:90-92` 一致 |
| `SetSyncState` | `ArticleSyncEventTypes.java:127-140` 两条**条件**赋值；版本号 `String.valueOf` | ✅ 与 Go `:75-88` 一致 |

---

## 组件 4：对象存储 `client/ArticleObjectStorageClient.java`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 构造 | `:66-74` `MinioClient.builder().endpoint(normalizeEndpoint(...)).credentials(...)` | ⚠️ SDK 8.5.x 无 `secure()`，改用 endpoint 协议（`:77-83`）；等价 |
| 构造失败 | SDK 不抛（只做本地校验） | ⚠️ **实现差异**：Go `minio.New` 失败 panic，Java 构造期基本不会失败 |
| Bucket | `:143-172` `makeBucket` 失败 ⇒ `bucketExists` ⇒ 存在 ⇒ return false（**不修正策略**）；不存在 ⇒ return false（只记日志） | ✅ 与 Go `svc:43-56` 的不对称一致 |
| Policy | `:162-170` **仅创建成功时** `setBucketPolicy`；JSON 逐字照搬（`:180-184`） | ✅ 与 Go `svc:51` 一致 |
| 读时点 | `:226-236` `statObject` + `getObject` | ✅ **有意对齐** minio-go 的 HEAD 行为 |
| put | `:194-208` 图片传 CacheControl、markdown 传 null | ✅ 与 Go 一致 |

---

## 组件 5：持久化 / Repository

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 连接池 | `application.yml:22-30` max=20 / min-idle=5 / idle=300000 / max-lifetime=1800000 | ✅ 与 Go `db.go:75-78` 逐项一致 |
| 建表 | Flyway `V8/V9/V10`（`application.yml:53-57`）+ `ArticleSchemaGate` 启动期硬校验（`:73-120`） | ⚠️ **O-5**：Flyway 是**有状态账本**，Go `AutoMigrate` 无状态 |
| jsonb | `JsonbTypeHandler.java:58-63` `PGobject` + `StringListTypeHandler`（`:70-93`）/ `StringMapTypeHandler`（`:100+`） | ✅ 与 Go `article.go:40-73` 的 Valuer/Scanner 等价 |
| typeHandler 生效 | `ArticleEntity.java:46` `@TableName(value="article", **autoResultMap=true**)` + `:81-82`/`:117-118` `@TableField(typeHandler=…)`；**自定义 `@Select` 一律 `@ResultMap("articleEntityResultMap")`** | ✅ **关键**：`ArticleMapper.java:42-49` 把 resultMap 声明在 `selectById` 上，其余自定义查询全部引用（`:81, :185-186`）。这正是 AGENTS.md §3.3 已登记的 `@Select` 覆盖会让 typeHandler 静默失效的规避 |
| 软删 | 无 `@TableLogic`（`ArticleEntity.java:145-149` 显式说明）；可见性由**每个查询显式写** `deleted_at IS NULL` | ⚠️ 机制层差异，见 U-3 |
| 可见性谓词 | `ArticlePublicMapper.java:62-66`（select）+ `:252-262`（update 的对偶形式） | ✅ 逐字等价 |
| 行锁 | `ArticleMapper.java:205-207` `SELECT * FROM article WHERE id=#{id} FOR UPDATE`（**不带** `deleted_at` 过滤，对应 Go 的 `Unscoped()`）；`ArticlePublicationMapper.java:64-65` `... FOR UPDATE` | ✅ 与 Go `community_revision.go:84-85` / `community_article_event.go:113-114` 一致 |

---

## 组件 6：配置与生命周期

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 配置绑定 | `ArticleRpcProperties.java`（`@ConfigurationProperties(prefix="article-rpc")`），字段名照抄 Go struct | ✅ |
| rpc 超时 | `application.yml:228-229` `observability.rpc-timeout: 70s` + `RpcServerInterceptorFactory` | ✅ 与 Go `Timeout: 70000` 一致（**不是** `grpc.server.timeout`，yml `:98-102` 已注明） |
| client 超时 | `application.yml:114` security `deadline: 10s`；`:122` message `deadline: 3s` | ❌ **O-6**：Go 的 message 未配 ⇒ `RpcClientConf.Timeout` 默认 **2000ms** |
| Kafka | `:69` `auto-offset-reset: earliest`（对齐 Go `Offset: first`）；`:71` `enable-auto-commit: false`；`:80` `acks: all` | ✅（Go yaml 两个 consumer 都写 `Offset: first`） |
| 消费并发 | 未设 `concurrency` ⇒ 默认 **1** | ❌ **S-4**：Go `Processors` 默认 **8** |
| Nacos | `bootstrap/NacosRegistration.java`，显式 Bean 名 `articleRpcNacosRegistration`（`:36`）避免与自动配置撞名 | ✅ 实现差异（Go 用 etcd） |
| 死配置 | `HotEventPusherConf` **刻意不绑定**（`ArticleRpcProperties.java:42-47`） | ✅ 与 Go 零调用点一致（`grep HotEventPusher` 只命中构造行与定义行） |
| **失效配置** | `application.yml:238` `spring.jackson.time-zone: Asia/Shanghai`；但 `config/Jackson2ObjectMapperConfiguration.java:55-60` 自定义 `ObjectMapper` ⇒ Boot 的 Jackson 属性绑定**被绕过**，该键**不生效** | ⚠️ 见 U-4（对 proto 面无影响，但注释宣称的「对齐 Go DSN 时区」未达成） |
| 死配置 | `application.yml:43-46` `spring.jpa.*`；pom 无 `spring-boot-starter-data-jpa` | ⚠️ 无害但为残留 |
| Schema 闸门 | `ArticleSchemaGate.java:73-120`：`ApplicationReadyEvent` + 5 表 + `article_revision_immutable` 触发器，缺失即抛 | ✅ 与 Go `db.go:61-71` 的 panic 等价 |

---

## 组件 7：指标 `metrics/ArticleRpcMetrics.java`

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 命名空间 | `:37` `NAMESPACE = "article_service"` —— **声明但从未使用**（`Counter.builder("article_total")` 未加前缀） | ❌ **O-2** |
| 指标名 | Micrometer 会补后缀：`article_total_total`、`minio_request_duration_seconds_seconds`、`minio_request_errors_total_total`、`kafka_push_errors_total_total` | ❌ Go 是 `article_service_article_total` 等 |
| 标签 | `action` / `status` / `type` / `operation` / `topic` | ✅ 标签名一致 |
| `articleStatus` | **只有 `:149`（create 路径）一处调用** | ✅ 与 Go 只有 `reviewing` 一致（不补 published/rejected） |
| buckets | `:40-41` `{0.005,0.01,0.025,0.05,0.1,0.25,0.5,1}` 秒 ⇒ `:48` 转 Duration | ✅ 数值一致 |
| `kafkaPushError` 取值 | `article_review` / `article_sync_event`（**用途名不是真 topic**） | ✅ 与 Go 一致 |
| **额外埋点** | `ArticleDomainService.java:213` 在 **DB** 失败时也调 `minioError("get")` | ❌ **O-3** |

---

## 组件 8：外部客户端占位（content security）

| 维度 | Java | 与 Go 对照 |
|---|---|---|
| 装配 | `client/ContentSecurityAutoConfiguration.java:31-35` `@ConditionalOnMissingBean(ContentSecurityPort.class)` ⇒ 注册 `UnavailableContentSecurityClient` | — |
| 实现 | `UnavailableContentSecurityClient.java:62-75` **两个方法都无条件抛 `SecurityAuditException`** | ❌ **S-5** |
| 真实客户端 | `client/SecurityRpcClient.java` 与 `client/ImageSecurityRpcClient.java` —— **各 4 行，仅 `package` + `import`，无任何实现** | ❌ Go `svc:72-73` 是两个可用实例 |
| 端口契约 | `ContentSecurityPort.TextAuditResult` 有 `ad` / `adConfidence` / `sanitizedText` / `errorMessage`，**无 `success` 字段** | ❌ Go 消费者判 `!result.Success`（`article_consumer.go:171-173`）与 `!resp.GetSuccess()`（`:263-265`）—— Java 端口**无处安放**这两个分支 |
| message-rpc | `MessageRpcClient.java:64-101` 真实 `@GrpcClient("message-rpc")` | ✅ Go `svc:74` 对应 |
| 失败方向 | `sendArticlePublished` 只捕获 `StatusRuntimeException`（`:96`）并返回 false | ⚠️ Go 捕获**任何** err（`:210`）；非 StatusRuntimeException 会逃逸 |