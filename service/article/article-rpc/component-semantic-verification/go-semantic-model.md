# Go 语义模型（唯一基准）

> 仓库：`/Users/pr/Documents/java&golang/Sea-RideTheWind_golang/service/article/rpc`
> 依赖：`go-zero v1.10.2`（`go.mod:17`）、`go-queue v1.2.2`（`go.mod:16`）
> 本文件**只描述 Go**。所有路径相对 Go 仓库根。

---

## 组件 1：RPC 服务端（gRPC 入口 / 文章领域服务 / 公开读视图 / 事务原子服务）

### 1.1 进程装配 `article.go`

| 维度 | Go |
|---|---|
| Operation | `main()` |
| Input | `-f etc/article.yaml` |
| Processing | `conf.MustLoad` → `logx.MustSetup` → `DisableNativeRpcTimeout`（`:36`，把 `conf.Timeout` 置 0 并返回 70s）→ `NewArticleRepo` → `NewServiceContext` → `zrpc.MustNewServer` → 两个 `kq.MustNewQueue` → relay service |
| Lifecycle | `service.NewServiceGroup()` 同启同停四件（server / 两个 consumer / relay），`defer serviceGroup.Stop()` |
| Branch | reflection **仅在 DevMode/TestMode** 注册（`:47-49`） |
| Concurrency | interceptor 显式 `AddUnaryInterceptors`（`:51`），**无全局自动注册** |
| Timeout | `Timeout: 70000`（`etc/article.yaml`），由 `observability/rpc.go:19-23` 搬到 interceptor |
| Error | `Must*` 系列（`MustLoad`/`MustNewServer`/`MustNewQueue`/`MustNewClient`）失败即 panic ⇒ 不启动 |

### 1.2 `CreateArticle` `internal/logic/create_article_logic.go:42-133`

| 维度 | Go |
|---|---|
| Input | `CreateArticleRequest{title, brief?, markdown_content, cover_image_url?, manual_type_tag, secondary_tags[], author_id}` |
| Precondition | **无任何校验**（title 空、author_id 空都照写） |
| Processing | ① `snowflake.GetID()`（`:50`，失败 ⇒ `Internal`）② MinIO `PutObject` `<articlePath><id>.md`，`ContentType: text/markdown`，**无 CacheControl**（`:61-68`）③ 组装实体，`status = REVIEWING`，`ext_info` 写 **8 个键**（`:97-106`）④ `ArticleRepo.Insert`（`:109`，失败 ⇒ `Internal`）⑤ `KqPusher.PushWithKey(ctx, articleID, msg)`（`:125`，失败 ⇒ `Internal`） |
| Transaction | **无事务**。`:50-130` 每步独立 |
| Result | `CreateArticleResponse{ArticleId}` |
| State Change | MinIO 对象 + `article` 行（已提交） |
| Error→去向 | snowflake/MinIO/DB/Kafka 全部 **Terminate**；KQ 失败**不回滚已插入的行** |
| Error Identity | 全部 `status.Error(codes.Internal, err.Error())`（用原始 error 的 message） |
| Side Effect | `metrics`: MinioDuration{put}、MinioErrors{put}、FileUploadTotal{markdown}、ArticleTotal{create}、ArticleStatusTotal{reviewing} |
| Concurrency | 无锁；article_id 由雪花保证唯一 |
| Lifecycle | — |

`ext_info` 初始 8 键（`:98-105`，**顺序即 JSON 键序**）：
`publish_stage="queued"`, `reco_sync_state="pending_review"`, `last_sync_error=""`,
`last_sync_event_id=""`, `last_sync_version_ms="0"`, `pending_sync_reason="create"`,
`last_sync_reason="create"`, `review_nonce=<articleID>`

### 1.3 `GetArticle`（作者视角）`internal/logic/get_article_logic.go:40-124`

| 维度 | Go |
|---|---|
| Branch（**顺序即语义**） | ① `:41` `in.PublicOnly` ⇒ 走公开分支；② `:44` `RequesterId != "" && IncrView` ⇒ `InvalidArgument("author read cannot increment view")`；③ `:66` `RequesterId != "" && author.AuthorID != RequesterId` ⇒ `PermissionDenied("article author differs")` |
| Processing | `FindOne`（`:54`）→ **`:56-59` 显式判 `gorm.ErrRecordNotFound` ⇒ `NotFound("article not found")`** → `IncrViewCount`（`:70-78`）→ MinIO `GetObject`（`:83`）+ `io.ReadAll`（`:96`） |
| Error→去向 | `FindOne` 非 NotFound ⇒ **Terminate（裸 err ⇒ UNKNOWN）**；`IncrViewCount` 失败 ⇒ **Continue（只记日志，`:73-74`）**；MinIO get/read 失败 ⇒ Terminate（裸 err） |
| Concurrency | `IncrViewCount` 是 `view_count = view_count + 1` 的原子 UPDATE（`model/article.go:121-124`），无读-改-写 |
| Result | `ext_info` 经 `cloneStringMap`（`:191-201`）：**空 map ⇒ nil**（protobuf 中即不输出） |

### 1.4 `GetArticle`（公开视角）`get_article_logic.go:126-189`

| 维度 | Go |
|---|---|
| Processing | `FindPublicOne`（`:127`）→ `ErrRecordNotFound` ⇒ `NotFound("article not published")`；其它错误 ⇒ `Internal("read publication failed")`（`:133`） |
| Branch | 有 `revision` ⇒ markdown 取 `revision.Markdown`（**不读 MinIO**，`:137-138`）；无 revision（legacy）⇒ 读 MinIO（`:142-154`），两条失败文案**相同** `"read legacy article failed"` |
| Concurrency | `IncrPublicViewCount` 是**带谓词的单条 UPDATE**（`model/article_public.go:151-161`），`RowsAffected==1` 才算成功；失败 ⇒ `Internal`（`:160`）；返回 0 行 ⇒ `NotFound("article no longer published")`（`:163`） |
| **Transaction** | **无事务**。`FindPublicOne` 的 2~3 条 SELECT、MinIO 读、UPDATE 各自 autocommit |
| Result | `publishedArticleResponse`（`:170-189`）：`status` **恒 `PUBLISHED`**；`ext_info` = 有 revision ⇒ `{published_revision_id}`，legacy ⇒ `{publication_gap: legacy_revision_missing}`；`update_time` 有 revision 时取 **`revision.published_at`** |

### 1.5 `UpdateArticle` `update_article_logic.go:46-127`

| 维度 | Go |
|---|---|
| Processing | `FindOne`（`:53`）→ **错误直接裸返回（`:57`），未判 `ErrRecordNotFound`** ⇒ not-found 落 `article==nil` 分支（`:59-63`，**实际不可达**）⇒ `codes.Unknown` + `"record not found"` |
| Precondition | `applySourceUpdates`（`:139-190`）逐字段应用；`in.SecondaryTags` 用 **`len() > 0`** 判（`:184`），不是 `!= nil` |
| Branch（**顺序即语义**） | **A** `prevPublished && 显式非 UNSPECIFIED && requestedStatus != PUBLISHED` ⇒ `updatePublishedToSourceOnly`（`:82`）<br>**B** `prevPublished && sourceChanged` ⇒ `SetSyncState(queued, pending_review, update, **""**, now, "")` → `status=REVIEWING` → `setReviewNonce` → `Update` → `enqueueReview`（`:87-100`）<br>**C** default ⇒ 若显式非发布则改状态；`review := status==REVIEWING && (sourceChanged \|\| prevStatus != REVIEWING)`；`Update` → 若 `review` 则 `enqueueReview`（`:101-122`） |
| Transaction | **A 有**（`RunInTx` `:216-227`：RetractPublicationTx → UpdateTx → CreateTx）；**B / C 无** |
| 对象命名 | `applySourceUpdates` 内：`articleMarkdownObjectName` = `<articlePath><id>-<sha256(md)>.md`（`:192-195`）⇒ **每次编辑写新对象**，已批准字节永不被覆盖。create 路径**无哈希** |
| Result | `UpdateArticleResponse{Success: true}` |
| Side Effect | `metrics.ArticleTotal{update}` |

### 1.6 `DeleteArticle` `delete_article_logic.go:40-100`

| 维度 | Go |
|---|---|
| Processing | `FindOne`（`:47`）→ 裸返回错误（`:51`，not-found ⇒ `UNKNOWN "record not found"`） |
| Precondition | `:58` `operator_id == "" \|\| operator_id != author_id` ⇒ `PermissionDenied("forbidden")`（**两种情形同一码同一文案**） |
| State Change | **软删**（`DeletedAt gorm.DeletedAt`）；**不删 MinIO 对象**（`:64-66` 显式注释） |
| Transaction | `RunInTx`（`:84-96`）：RetractPublicationTx → CreateTx(同步 outbox) → DeleteTx。**事务内不再取文章**，用事务外那份实例 |
| Result | `DeleteArticleResponse{Success: true}` |

### 1.7 `ListArticles`（作者视角）`list_articles_logic.go:30-101` + `model/article.go:136-183`

| 维度 | Go |
|---|---|
| Precondition | 三个筛选条件均 **`!= ""`** 才生效（`model/article.go:142-150`）；二级标签用 `secondary_tags @> '["tag"]'`（字符串**拼接**，有引号转义风险） |
| Pagination | offset 负值夹 0（`:156-159`）；**page / page_size 不夹**，响应原样回显（`logic:98-99`） |
| **排序** | `model/article.go:161-176`：`order := "created_at desc"`；**`desc` 的拼接整体位于 `if opt.SortBy != ""` 之内** ⇒ `SortBy==""` 时**恒 `created_at desc`** |
| Result | 每条的 `MarkdownContent` 填的是 **`article.Content`（MinIO 对象 key）**，不是正文（`logic:79`） |
| Count/Page | 同一 `db` 对象承载 `Count`（`:152`）与 `Find`（`:178`），**两次独立语句、无共同快照** |

### 1.8 `ListArticles`（公开视角）`list_articles_logic.go:103-133` + `model/article_public.go:62-147`

| 维度 | Go |
|---|---|
| 可见性谓词 | `article_public.go:20-26`：`(p.state='published' AND p.current_revision<>'' AND EXISTS(revision))` **OR** `(p.article_id IS NULL AND a.status = 2)` |
| 标签过滤 | 匹配 **revision** 的标签（已批准值），legacy 回落匹配 `article` 行（`:67-85`） |
| Pagination | **先归一再用**：page<1⇒1、size<1⇒**20**、size>100⇒**100**（`:92-100`），响应回显**归一后的值**（`logic:124-132`） |
| **排序** | `publicArticleOrder`（`:132-147`）：`view_count/like_count/comment_count`（**比作者路径多 comment_count**）；`direction := "DESC"; if !opt.Desc { "ASC" }` ⇒ **`SortBy=="" && !Desc` ⇒ ASC**；**总是附加 `, article.id 同方向`** |
| **Transaction** | `:65,128` `Transaction(fn, &sql.TxOptions{Isolation: LevelRepeatableRead, ReadOnly: true})` ⇒ count + 分页 + 取 revision 在**同一只读可重复读快照** |
| Result | 每条 markdown 恒 `""`（`logic:122`）⇒ **永不返回正文，也不返回对象 key** |

### 1.9 `UploadFile` `upload_file_logic.go:40-108`

| 维度 | Go |
|---|---|
| Processing | `snowflake.GetID()`（`:47`，失败 ⇒ **裸 err**）→ `ext = filepath.Ext(FileName)`（`:55`，含点）→ `objectName = <imagePath><id><ext>`（`:56`） |
| External | `PutObject` 带 `CacheControl: "public, max-age=31536000, immutable"` + `ContentType: mime.TypeByExtension(ext)`，**空则回落 `application/octet-stream`**（`:58-61`） |
| Result | `buildPublicFileURL`（`:93-108`）：base 为空 ⇒ `scheme://TrimSpace(Endpoint)`（`scheme` 由 `UseSSL` 决定）；三段各自 trim 方向**不同**：base 去**右**、bucket 去**两**侧、object 去**左** |

---

## 组件 2：MQ 消费者

### 2.1 审核消费者 `internal/mqs/article_consumer.go:44-142`

| 维度 | Go |
|---|---|
| Input | `ArticleReviewMessage{article_id, author_id, content_path, review_nonce,omitempty}`（`article_sync_types.go:35-40`） |
| Branch（**五条丢弃 + 三条重投**） | JSON 解析失败 ⇒ **nil（丢弃）** `:49-52`；文章不存在（`ErrRecordNotFound`）⇒ **nil** `:55-57`；四元组不匹配 ⇒ **nil** `:62-68`；幂等命中（`reco_sync_state=="pending" && last_sync_event_id!=""`）⇒ **nil** `:85-87` **和** `:111-113`；MinIO 读失败 ⇒ **err** `:70-81`；审核 RPC 失败 ⇒ **err** `:89-91`；事务失败 ⇒ **err** `:133-136` |
| Processing | MinIO 读正文 → 幂等检查 → `auditArticle` → 若 `status==REJECTED` 结束（`:92-95`）→ 生成 eventID（事务外，`:97-101`）→ `WithArticleTx` |
| Concurrency | `WithArticleTx`（`community_revision.go:81-89`）：`Transaction` + **`Unscoped()` + `clause.Locking{Strength:"UPDATE"}` ⇒ `SELECT … FOR UPDATE`**，事务内**二次**校验五元组（deleted/status/content/author/nonce）+ 二次幂等 |
| State Change | `SetSyncState(reco_queued, pending, syncReason, eventID, versionMs, "")` → `tx.Save` → `CreateTx(outbox)`，**同一事务** |
| Timeout | `observability.TraceConsumer`（`:45`）—— 仅 trace |

### 2.2 审核 `auditArticle` `article_consumer.go:153-206`

| 维度 | Go |
|---|---|
| Precondition | `SecurityRpc == nil` ⇒ 返回 error（`:154-156`） |
| External | `SanitizeContent`，四个开关**全 true**：`EnableAdDetection/EnableHtmlSanitization/EnableUnicodeNormalization/EnableWhitespaceNormalization`（`:158-166`） |
| Branch | RPC err ⇒ err（重投）`:167-170`；**`!result.Success` ⇒ err（重投）** `:171-173`；`result.IsAd` ⇒ `REJECTED` + `SetSyncState(rejected, failed, pending_sync_reason, **""**, now, "content_rejected")` + `Update` ⇒ return nil `:174-182` |
| Branch | 否则逐图审（`:188-203`）：`isAd` ⇒ `REJECTED` + `SetSyncState(rejected, failed, …, "image_rejected")` |
| Branch | **文本不过 ⇒ 立即返回，不继续查图**（`:174-182` 的 `return nil`） |
| 副作用 | `syncReason` 取 **`pending_sync_reason`**（`:176`/`:196`），不是 `last_sync_reason` |

### 2.3 图片审核 `article_consumer.go:220-268`

| 维度 | Go |
|---|---|
| Processing | `url.Parse` → `u.Path` 去首 `/` → `SplitN(path,"/",2)` → **取 `parts[1]`** 作对象名（`:224-230`）；**段数 <2 ⇒ error（整篇重投，不是跳过这张图）** |
| mime | **只认 `.png`→`image/png`、`.webp`→`image/webp`，其余一律 `image/jpeg`**（`:244-250`，默认不是 octet-stream） |
| Encoding | `data:<mime>;base64,<b64>`（标准 base64，**带 padding**） |
| Branch | `!resp.GetSuccess()` ⇒ error（重投，`:263-265`） |
| 覆盖图 | `cover_image_url` **追加在正文图片之后**（`:185-187`） |

### 2.4 同步结果消费者 `article_sync_result_consumer.go:39-212`

| 维度 | Go |
|---|---|
| Branch | JSON 失败 ⇒ nil `:42-45`；`article_id` **TrimSpace 后**为空 ⇒ nil `:46-48`；`FindOneUnscoped` 找不到 ⇒ nil `:51-55`；`op` 非 upsert/delete ⇒ nil `:65-67` |
| Concurrency | `handleUpsertResult` 的**全部判定与写入都在 `WithArticleTx`（FOR UPDATE）内**（`:74-153`）；外层 `FindOneUnscoped` 只用于「文章不存在 ⇒ 丢弃」 |
| 判定链（**顺序即幂等语义**） | ① `:75` 已删 ⇒ nil<br>② `:81-85` `last_sync_event_id != eventID \|\| last_sync_version != FormatInt(versionMs) \|\| status==PUBLISHED` ⇒ nil<br>③ `:87-91` `!Success` ⇒ `status=REVIEWING` + `SetSyncState(reco_failed, failed, …)` + Save<br>④ `:92-94` `status != REVIEWING` ⇒ nil<br>⑤ `:95-107` 读回 outbox payload + 6 项比对，不符 ⇒ error（回滚 + 重投）<br>⑥ `:108-152` 建 revision（`revision = max+1`, `revision_id = "<id>:r<n>"`, `sync_event_id = eventID`）+ 推进指针 + 写域事件 + `status=PUBLISHED` + `published_once="true"` |
| Branch（delete） | `:166-183`：`SetSyncState` **在 `DeletedAt` 判断之前**执行（`:168-172`）；已软删 ⇒ 不写回（`:174-176`） |
| 通知 | `:160-163` **事务提交后**才发；uid 解析失败只记日志（`:186-190`）；RPC 失败只记日志（`:210-212`） |

---

## 组件 3：MQ 生产 / outbox

### 3.1 outbox 模型 `internal/model/article_sync_outbox.go`

| 维度 | Go |
|---|---|
| Status | `pending=0 / sent=1 / failed=2`（`:10-14`） |
| `FetchPending` | `:52-61` `status IN (0,2)`；`maxRetry > 0` 时**追加** `retry_count < maxRetry`；`Order("created_at asc").Limit(limit)` |
| `MarkSent` | `:63-71` `status=1, last_error=""` |
| `MarkFailed` | `:73-82` `status=2, retry_count = retry_count + 1, last_error = <err>` |
| 唯一约束 | `event_key` 上的 `uk_article_sync_outbox_event_key`（`:18`）；key = `TrimSpace(articleID) + ":" + TrimSpace(op) + ":" + TrimSpace(eventID)`（`article_sync_types.go:90-92`） |

### 3.2 outbox relay `article.go:91-107` + `mqs/article_sync_outbox_sender.go:21-46`

| 维度 | Go |
|---|---|
| 调度 | goroutine + `time.NewTicker(interval)`，`context.WithCancel` 支持优雅停机 |
| 配置兜底 | `article.go:63-65` `interval <= 0 ⇒ 1s`；`:67-69` `batchSize <= 0 ⇒ 100` |
| Processing | 逐条：`PushWithKey(ctx, **AggregateID**, **payload 原文**)`（`:30`） |
| Branch | 单条失败 ⇒ `KafkaPushErrors{"article_sync_event"}` + `MarkFailed` + **`continue`**（不中断整批，`:34-38`）；成功 ⇒ `MarkSent`（失败只记日志，`:40-42`） |
| Retry | 无进程内重试；超过 `maxRetry`（yaml=10）后**永久搁置，不进死信、不告警** |

### 3.3 `SetSyncState` 条件赋值 `article_sync_types.go:75-88`

```go
ExtInfo[publish_stage]   = publishStage
ExtInfo[reco_sync_state] = syncState
ExtInfo[last_sync_error] = syncError
ExtInfo[last_sync_event_id] = eventID
ExtInfo[last_sync_reason]   = syncReason
if versionMs > 0    { ExtInfo[last_sync_version_ms] = strconv.FormatInt(versionMs, 10) }  // ← 条件
if syncReason != "" { ExtInfo[pending_sync_reason]  = syncReason }                    // ← 条件
```

版本号**存成字符串**。两条条件赋值是幂等判据的地基。

---

## 组件 4：对象存储 `internal/svc/service_context.go:35-56`

| 维度 | Go |
|---|---|
| 构造 | `minio.New(Endpoint, {Creds: StaticV4, Secure: UseSSL})`；**失败 ⇒ panic**（`:39-41`） |
| Bucket | `MakeBucket`（`:43`）→ 失败则 `BucketExists`：存在 ⇒ **空分支（连 warn 都没有、不修正策略）**；不存在 ⇒ `logx.Errorf` **但不 panic**（`:44-49`） |
| Policy | **仅在本次真的创建成功时** `SetBucketPolicy`（public-read，`:51-55`）；失败只记日志 |
| 读失败时点 | `minio-go/v7` 的 `GetObject` **先发 HEAD**，对象不存在时**立刻返回 error**（与 MinIO Java SDK 不同） |

---

## 组件 5：持久化 / Repository

### 5.1 连接与建表 `internal/model/db.go`

| 维度 | Go |
|---|---|
| DSN | `:30-37` **显式带 `TimeZone=Asia/Shanghai`** |
| GORM 配置 | `:39-45` `SkipDefaultTransaction: true`、`DisableForeignKeyConstraintWhenMigrating: true`、`NamingStrategy{SingularTable: true}`（表名单数） |
| 建表 | `:55-58` `AutoMigrate(&Article{}, &ArticleSyncOutboxEvent{})` —— **无状态、幂等** |
| 社区表 | `:61-71` 启动期**硬校验** `article_revision_immutable` 触发器；缺失 ⇒ `error` ⇒ 上层 `panic` ⇒ **进程不启动** |
| 连接池 | `:75-78` MaxOpen=20 / MaxIdle=5 / ConnMaxIdleTime=5m / ConnMaxLifetime=30m |

### 5.2 jsonb 自定义类型 `internal/model/article.go:40-73`

`StringArray`（`[]string`）与 `JSONMap`（`map[string]string`）各自实现 `driver.Valuer` / `sql.Scanner`。
`Scan(nil)` ⇒ 置 nil；非 `[]byte` ⇒ `errors.New("type assertion to []byte failed")`。

### 5.3 可见性谓词 `internal/model/article_public.go:20-26`

```sql
(
  (p.state = 'published' AND p.current_revision <> ''
   AND EXISTS (SELECT 1 FROM article_revision r
                WHERE r.article_id = a.id AND r.revision_id = p.current_revision))
  OR (p.article_id IS NULL AND a.status = 2)
)
```
`Inc` 版本（`:152-159`）是它的**对偶形式**（UPDATE 目标表不能自 join），
`RowsAffected == 1` 才算「可见且已计数」。

### 5.4 行锁

| 位置 | 锁 |
|---|---|
| `community_revision.go:84-85` | `tx.Unscoped().Clauses(clause.Locking{Strength:"UPDATE"}).Where("id=?").First()` |
| `community_article_event.go:113-114` | `tx.Clauses(clause.Locking{Strength:"UPDATE"}).Where("article_id=?").First()` |

---

## 组件 6：配置与生命周期

### 6.1 配置 `internal/config/config.go` + `etc/article.yaml`

| Go 字段 | yaml 值 | 备注 |
|---|---|---|
| `RpcServerConf.Timeout` | `70000` | 被 `DisableNativeRpcTimeout` 搬到 interceptor |
| `Postgres` | postgres:5432 / first_db | 密码走 `${POSTGRES_PASSWORD}` |
| `KqPusherConf.Topic` | `article-creation-topic` | 与 `KqConsumerConf.Topic` 同值但**是两个独立字段** |
| `KqConsumerConf` | group=dataclean, `Offset: first`, `Consumers: 1` | ⚠️ **`Processors` 未配 ⇒ 默认 8** |
| `ArticleSyncResultConsumerConf` | group=article-sync-result, `Offset: first`, `Consumers: 1` | 同上 |
| `ArticleSyncOutbox` | 1000 / 100 / **10** | |
| `MinIO` | minio:9000, bucket=articles, `UseSSL: false`, `ImagePath: "articles/images/"`, `ArticlePath: "articles/content/"`（**均带尾斜杠**） | |
| `SecurityConf` | etcd key=security.rpc, **`Timeout: 10000`** | |
| `MessageRpc` | etcd key=message.rpc，**未配 Timeout** | ⇒ 走 go-zero 默认，见 O-6 |
| `HotEventPusherConf` | topic=article-hot-events | ⚠️ **死配置**：`grep HotEventPusher` 只命中 `svc` 构造行与 config 定义行，零调用点 |

### 6.2 Go 侧默认值（消费点优先）

| 默认值 | 声明点 | 消费点 |
|---|---|---|
| `KqConf.ForceCommit = true` | `go-queue@v1.2.2/kq/config.go:24` | `kq/queue.go:235` `if !q.c.ForceCommit { continue }` ⇒ **失败也 CommitMessages** |
| `KqConf.Processors = 8` | `kq/config.go:19` | `kq/queue.go:220` `for i := 0; i < q.c.Processors; i++` ⇒ **8 个并发处理协程** |
| `KqConf.Offset = last` | `kq/config.go:16` | yaml 显式写 `first`，故不触发 |
| `RpcClientConf.Timeout = 2000` | `go-zero@v1.10.3/zrpc/config.go:31` | `zrpc` client 每次调用设 deadline |
| `RpcServerConf.Timeout = 2000` | `go-zero@v1.10.3/zrpc/config.go:46` | 本项目 yaml 写 70000，故不触发 |

---

## 组件 7：指标 `metrics/metrics.go`

| 指标 | Prometheus 全名 | 标签 | 调用点 |
|---|---|---|---|
| ArticleTotal | `article_service_article_total` | `action`(create/update/delete) | create / update / delete |
| ArticleStatusTotal | `article_service_article_status_total` | `status` | **仅 `reviewing`**（create 路径 `:115`）。`published` / `rejected` **无调用点** |
| FileUploadTotal | `article_service_file_upload_total` | `type`(image/markdown) | upload / create / update |
| MinioRequestDuration | `article_service_minio_request_duration_seconds` | `operation`(put/get) | buckets `[.005,.01,.025,.05,.1,.25,.5,1]` |
| MinioRequestErrors | `article_service_minio_request_errors_total` | `operation` | put / get |
| KafkaPushErrors | `article_service_kafka_push_errors_total` | `topic`（**取值其实是用途名**：`article_review` / `article_sync_event`） | create / update / relay |

`Namespace = "article_service"`（`:6`）是**所有指标名的前缀**。

---

## 组件 8：外部客户端占位（content security）

| 维度 | Go |
|---|---|
| 构造 | `zrpc.MustNewClient(c.SecurityConf)`（`svc:60`）—— **失败即 panic** |
| 实例 | `contentsecurityservice.NewContentSecurityService(securityClient)` + `imagesecurityservice.NewImageSecurityService(securityClient)`（`svc:72-73`）—— **同一个 client，暴露两个服务** |
| 消费点 | `article_consumer.go:158` `SanitizeContent`；`:253` `DetectImageAd`（`ConfidenceThreshold: 0.7`, `EnableTextExtraction: true`） |
| nil 保护 | `:154-156` `SecurityRpc == nil` ⇒ error（**fail-closed**） |