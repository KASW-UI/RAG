# article-rpc — Go → Spring Boot 理论 Java 文件树

> Component：`Sea-RideTheWind_golang/service/article/rpc`
> 目标根包：`com.rag.article.article.rpc`
> 推导日期：2026-10-21

---

## 12.1 第一部分：目标 Java 项目组织依据

### 现有包（实际检查到）

RAG 的 `service/article/article-rpc` **只有 `pom.xml`，无任何 Java 代码**。因此本文件的目标包结构由**同仓库已落地的 4 个模块**归纳：

| 参照模块 | 提供的一致性依据 |
|---|---|
| `message/message-rpc` | `@GrpcService` 在 `service/impl/`；`client/` 放下游 gRPC 客户端；`config/` 放 `@ConfigurationProperties`；`bootstrap/` 放 Nacos 注册 |
| `users/user/user-rpc` | `bootstrap/SnowflakeBootstrap`；`config/MybatisPlusOrderingBridgeAutoConfiguration`；`mapper/JsonbTypeHandler` |
| `users/admin/admin-rpc` | 独立事务 Bean 的拆分方式（`AdminCreateAtomicService`）；`bootstrap/AdminSeederBootstrap` |
| `service/common` | `communityfact/{Wire,Dispatcher,AuthorityHandler}`；`observability/{MqConsumer,Observability}`；`snowflake/Snowflake` |

### 已确认的框架机制

- `net.devh:grpc-server-spring-boot-starter`：`@GrpcService` + `@GrpcClient`
- MyBatis-Plus `@Mapper` / `BaseMapper` / `@TableName` / `@TableId(type=ASSIGN_ID)`
- Spring `@ConfigurationProperties`；Spring Cloud Alibaba Nacos
- **Kafka 消费者在 RAG 中无任何先例**（`service/common/observability/MqConsumer` 只提供 span 包装，不提供 listener 容器）→ 本模块需引入 Spring Kafka
- **对象存储（MinIO）在 RAG 中无任何先例** → 本模块需引入 MinIO SDK
- Micrometer `MeterRegistry`；OpenTelemetry（`common/observability`）

### 当前 Component 可以直接复用的能力

- **communityfact 信封类型**：`com.rag.common.communityfact.Wire.Event` / `Wire.SubjectRef` / `Wire.AuthorityFact` 与 Go `communityArticleEnvelope` 的字段名、producer 语义**逐字一致** → `community_article_event.go` 的信封构造可 `REUSE`，不新建 DTO
- **Snowflake ID**：`common/snowflake/Snowflake`（Go 侧同样用 snowflake，位宽一致）
- **MQ span 包装**：`common/observability/MqConsumer`（对应 Go `observability.TraceConsumer`）
- **RPC span**：`common/observability/RpcInterceptor`（经 `RpcServerInterceptorFactory` 暴露） ⚠️ `@Component` ≠ 自动注册：需 `scanBasePackages` 含 `com.rag.common` 或显式 `@Import`。实测 `user-rpc` / `admin-rpc` 均**未注册**（见 `FileTree/common.md` §12.7 #1）。
- **业务异常基类**：`common/exception/BusinessException`

### 需要进一步确认的组织约定

| 项 | 状态 |
|---|---|
| `article_domain_outbox` 的投递方 | Go 侧 **article-rpc 只写不投递**，无 dispatcher 进程。`UNKNOWN`：是否需要在本模块内补 `Dispatcher` 运行（`common/communityfact/Dispatcher` 已具备能力） |
| Flyway 归属 | ✅ 已按 AGENTS.md §6.3（2026-10-21）定案：单一归属 + 全局唯一版本号。article-rpc 的 `article` / `article_sync_outbox` / `article_revision` / `article_publication` / `article_domain_outbox` 五张表**独占**，不与任何服务重叠 → 归 article-rpc；版本号接入全局唯一号段（当前 message-rpc 占 V1-V3、users 占 V1-V4，article 从 V8 起） |
| `HotEventPusher` | Go 侧已 wire 但**零调用点** → 不迁移 |

---

## 12.2 第二部分：Go 原始文件树

```
service/article/rpc/
├── article.go                              # 入口：gRPC server + 2 个 Kafka consumer + outbox relay（ServiceGroup）
├── articleservice/article_service.go        # gRPC 客户端桩
├── internal/
│   ├── config/config.go                     # PG / Kafka×4 / MinIO / Security / Message / Outbox / HotEvent
│   ├── svc/service_context.go               # MinIO 客户端 + bucket bootstrap + snowflake + 3 pusher + 3 rpc client
│   ├── server/article_service_server.go     # 6 个 gRPC 方法 → logic
│   ├── logic/
│   │   ├── create_article_logic.go          # snowflake + MinIO put + insert(REVIEWING) + Kafka push
│   │   ├── get_article_logic.go             # PublicOnly 分支 + revision 冻结 + 浏览量自增
│   │   ├── update_article_logic.go          # 3 条分支 + sha256 对象名 + FOR UPDATE
│   │   ├── delete_article_logic.go          # retract + outbox + delete 单事务
│   │   ├── list_articles_logic.go           # PublicOnly 分支
│   │   └── upload_file_logic.go             # snowflake + mime 嗅探 + MinIO put
│   ├── model/
│   │   ├── article.go                       # Article GORM 模型 + 仓库方法
│   │   ├── article_public.go                # PublicArticle + 公开可见谓词 + ListPublic + IncrPublicViewCount
│   │   ├── article_sync_outbox.go           # outbox 表 + CreateTx/FetchPending/MarkSent/MarkFailed
│   │   ├── community_revision.go            # ArticleRevision / ArticlePublication / ArticleDomainOutbox + WithArticleTx(FOR UPDATE)
│   │   ├── db.go                            # 连接池 + AutoMigrate + 触发器启动门禁
│   │   ├── article.sql                      # 参考 DDL
│   │   └── 002_community_revision.sql       # community 三表 + CHECK + 不可变触发器
│   └── mqs/
│       ├── article_sync_types.go            # Ext* 常量 + ArticleReviewMessage / SyncEvent / SyncResult
│       ├── article_consumer.go              # 审核消费者（广告 + 图片安全）
│       ├── article_sync_result_consumer.go  # 同步结果消费者（发布落地）
│       ├── article_sync_outbox_sender.go    # outbox relay worker
│       └── community_article_event.go       # community 信封 + RetractPublicationTx
├── metrics/metrics.go                       # 6 个 Prometheus 指标
└── pb/                                      # proto/hot.proto 的 article 对应物（生成物）
```

---

## 12.3 第三部分：Go 文件职责分析

### `article.go`
进程入口 + **生命周期编排**：单个 `service.ServiceGroup` 同时启动 gRPC server、2 个 Kafka consumer（`article_creation-topic` / `article-sync-results`）、1 个自建 outbox relay（ticker，默认 1s，`SendPending(batch=100)`）。`service.ServiceGroup` 保证四者同启同停。
- **L4**：Spring Boot main + `@EnableScheduling`；四者的同启同停由容器生命周期承担。

### `internal/svc/service_context.go`
- MinIO 客户端 + **bucket 幂等 bootstrap**（`BucketExists` → `MakeBucket` → `SetBucketPolicy(public-read)`）
- snowflake 初始化
- 3 个 Kafka pusher（review / sync-events / **hot-events 零调用**）
- 3 个下游 gRPC client（Security、ImageSecurity 共用一个 zrpc 连接、Message）
- **L2**：`minio.New` 失败 → panic；bucket 缺失仅 warn 不 panic（`service_context.go:40-56`）

### `internal/logic/create_article_logic.go`
snowflake id → MinIO 写 `articles/content/<id>.md`（`text/markdown`）→ insert `article`（`status=REVIEWING(3)`，`ext_info` 写入 queued/pending_review/reason/review_nonce）→ Kafka push（key=articleID，`ReviewNonce` 设为 articleID 作幂等键）。
- **L2**：`ext_info` 初始键必须与 `mqs.Ext*` 常量一致；**MinIO 与 DB 之间无事务**（先 DB 后 MinIO，孤儿可接受，因文件可再生）。

### `internal/logic/get_article_logic.go`
三条分支：
1. 作者详情：`FindOne` → author 校验（若带 `RequesterId`）→ 可选 `IncrViewCount` → 读 MinIO markdown
2. 公开详情：`FindPublicOne` → 有 `Revision` 用冻结 markdown；否则读 legacy 对象 → `IncrPublicViewCount`
3. `RequesterId` + `IncrView` 同时出现 → **InvalidArgument**（`get_article_logic.go:45`）
- **L2**：`IncrViewCount` 失败**仅记日志不中断**（best-effort），而 `IncrPublicViewCount` 失败**致命**；公开路径自增失败（竞态）→ `NotFound`。这个不对称必须保留。

### `internal/logic/update_article_logic.go`
按"原状态 + 源是否变更"三分支：
- **A**：原=PUBLISHED 且要改成非发布 → `updatePublishedToSourceOnly`（retract + outbox，**单事务**）
- **B**：原=PUBLISHED 且源变更 → 新 `review_nonce` + `status=REVIEWING` + 入队审核
- **C**：默认 → 显式非发布则改状态；回 REVIEWING 且源变更则重置 nonce 并入队
- **L2**：对象名 `"<ArticlePath><id>-<sha256>.md"`（按内容哈希，不可变）；**只有 A 路径建 outbox 行**，B/C 依赖审核消费者在复审后建

### `internal/logic/delete_article_logic.go`
author 校验 → **单事务**：`RetractPublicationTx`（`FOR UPDATE` 取指针 + 写 retract 域事件）+ outbox 创建 + 软删（`gorm.DeletedAt`）。
- **L2**：**不删 MinIO 对象**（需独立保留期确认）；删除后 H03 消费者仍须能读到历史已发布 revision。

### `internal/model/article_public.go`
- `PublicArticle{Article, Revision}` 复合视图
- `publicArticlePredicate`：**复合 SQL 谓词** —— `(publication.state='published' AND current_revision<>'' AND EXISTS(revision))` **OR**（无 publication 行 **AND** `article.status=2`）—— 后者是显式 legacy 兼容
- `ListPublic` 把 count + 分页 + revision 取值包在**同一个只读 repeatable-read 快照**里
- **L2**：标签过滤（`manual_type_tag` / `secondary_tag`）匹配 **revision 的值**，不是 article 当前草稿值 → 重发文章的标签变更不会泄漏给读者
- `IncrPublicViewCount` 是带谓词的原子 `UpdateColumn`，`RowsAffected==1` 才算成功 → 竞态丢失返回 `false`

### `internal/model/community_revision.go`
- `ArticleRevision`：复合 PK `(article_id, revision)`、`content_sha256 char(64)`、`markdown text NOT NULL`；**由触发器强制不可变**
- `ArticlePublication`：source 域指针（`state ∈ {published, retracted}`、`pointer_version bigint`）
- `ArticleDomainOutbox`：H03 域事件 outbox，**唯一键 `(aggregate_id, aggregate_version)`** → 保证事件顺序
- `WithArticleTx`：`SELECT … FOR UPDATE` + 闭包 → 串行化同 article 的并发更新

### `internal/mqs/article_consumer.go`（审核消费者）
查 article → **四元组校验**（`status==REVIEWING` ∧ `ContentPath` 一致 ∧ `AuthorID` 一致 ∧ `ReviewNonce` 一致）→ 重读 MinIO markdown → `SanitizeContent`（广告/HTML/unicode/空白）+ 对每个 markdown 图和 `cover_url` 调 `DetectImageAd` → 任一不过则 `status=REJECTED` 并停止 → 否则在 `WithArticleTx`（FOR UPDATE）内写 outbox 行。
- **L2 幂等**：tx 前后各查一次 `ExtInfo[ExtRecoSyncState]=="pending" && ExtInfo[ExtLastSyncEventID]!=""` → Kafka 重投不重复入队
- **错误处理**：unmarshal 失败 / article 不存在 / 源或状态已变 → **静默 drop（返回 nil）**；`SanitizeContent` RPC 失败 → **返回 err（会重试）**。两者不可混同

### `internal/mqs/article_sync_result_consumer.go`（同步结果消费者）
`Upsert` → `handleUpsertResult`：校验 `EventID` + `VersionMs` 与 `ext_info` 一致 → `REVIEWING` 时载入 outbox payload 校验 → `revision = max(revision)+1` → 插 `ArticleRevision` → upsert `ArticlePublication` 指针（`pointer_version + 1`）→ 建 `ArticleDomainOutbox` → `status=PUBLISHED` → `ExtPublishedOnce` 守卫下**只发一次** `MessageRpc.SendNotification`。
`Delete` → 仅更新同步状态。
- **L2 幂等**：已接受结果的重放是 no-op；`revision_id` 格式 `<articleID>:r<revision>`；`SendNotification` 用 `SenderRole_SYSTEM` + `NotificationKind_ARTICLE_PUBLISHED`

### `internal/mqs/article_sync_outbox_sender.go`
`FetchPending(limit, maxRetry)` → 逐条 `PushWithKey(key=AggregateID, value=Payload)` → 成功 `MarkSent` / 失败 `MarkFailed`（`retry_count+1`）。
- **L2**：单条失败**不中断批次**；`FetchPending` 过滤 `status IN (0,2) AND retry_count < maxRetry`（默认 10）

### `internal/mqs/community_article_event.go`
community 事件信封 + `communityRevisionPayload`；`ArticleDomainOutbox` 行的构造；`RetractPublicationTx`（`FOR UPDATE` 取指针 + 写 retract 事件，单事务）。
- **L2**：`event_id` = `community.article.<syncEventID>.<operation>`；`producer` = `rtw.community.article`；`subject_ref` 固定 `rtw.identity/platform/<AuthorID>`；`wiki_module_id` **显式为 nil**（社区文章永不进知识库）；`search_evidence` 仅 `published` 为 true

### `internal/model/db.go`
连接池（MaxOpen=20 / MaxIdle=5 / ConnMaxIdleTime=5m / ConnMaxLifetime=30m）+ `AutoMigrate(Article, ArticleSyncOutboxEvent)` + **触发器启动门禁**（`article_revision_immutable` 不存在则 panic，提示必须先跑 `002_community_revision.sql`）。

### `metrics/metrics.go`
6 个指标：`article_total{action}`、`article_status_total{status}`、`file_upload_total{type}`、`minio_request_duration_seconds{operation}`、`minio_request_errors_total{operation}`、`kafka_push_errors_total{topic}`。

---

## 12.4 第四部分：职责到 Java 的映射表

| Go 来源及职责 | Java 目标包或现有机制 | 承载方式 | 依据与原因 |
|---|---|---|---|
| `article.go` 进程装配 | `ArticleRpcApplication` | ABSORB | Spring Boot 启动 |
| `article.go` ServiceGroup 四件套同启同停 | Spring 容器生命周期 | ABSORB | gRPC server / Kafka listener / `@Scheduled` 由容器统一管理 |
| `article.go` outbox relay ticker | `mq/ArticleSyncOutboxRelay` `@Scheduled` | ADAPT | 1s 固定间隔 + batch=100 保留 |
| `config/config.go` | `config/ArticleRpcProperties` | ADAPT | PG / Kafka×4 / MinIO / Security / Message / Outbox 全字段绑定 |
| `svc` MinIO 客户端 | `client/ArticleObjectStorageClient` | NEW | RAG 无 MinIO 先例；`client/` 有 `message-rpc` 下游客户端先例 |
| `svc` bucket bootstrap + public-read | `bootstrap/MinioBucketBootstrap` | ADAPT | 幂等语义保留 |
| `svc` snowflake 初始化 | `bootstrap/SnowflakeBootstrap` + `common/Snowflake` | REUSE | 与 users 同款 |
| `svc` 3 个 Kafka pusher | `KafkaTemplate`（Spring 自动配置） | ABSORB | 无需自建类 |
| `svc` SecurityRpc / ImageSecurityRpc / MessageRpc | `client/{SecurityRpcClient,ImageSecurityRpcClient,MessageRpcClient}` | NEW | 与 `message-rpc/client/` 同款 |
| `svc` HotEventPusher | — | — | **零调用点，不迁移** |
| `server/article_service_server.go` | `service/impl/ArticleServiceGrpcImpl`（6 个 override） | ADAPT | 分发由 starter 完成，方法体需写 |
| `logic/create_article_logic.go` | `service/ArticleDomainService.createArticle` | MERGE | 6 个 logic 合 1（同 users 决策） |
| `logic/get_article_logic.go` 三分支 | `ArticleDomainService.getArticle` + `ArticlePublicDomainService` | SPLIT | 作者视角 vs 公开视角是**不同持久化语义**，需分层 |
| `logic/get_article_logic.go` author 校验 | `ArticleDomainService` | ADAPT | 权限判断保留在 rpc 层（Go 侧 HTTP 侧也重复校验，见 api 文件） |
| `logic/update_article_logic.go` 三分支 | `ArticleDomainService.updateArticle` | MERGE | 同上 |
| `logic/update_article_logic.go` A 路径单事务 | `service/ArticleAtomicService.retractAndUpdate` | SPLIT | **self-invocation 风险**：3 步写需独立 `@Service` Bean（AGENTS.md §3.3） |
| `logic/delete_article_logic.go` 单事务 | `ArticleAtomicService.retractAndDelete` | SPLIT | 同上 |
| `logic/list_articles_logic.go` | `ArticleDomainService` / `ArticlePublicDomainService` | SPLIT | 同 getArticle |
| `logic/upload_file_logic.go` | `ArticleDomainService.uploadFile` | MERGE | 同上 |
| `model/article.go` Article | `domain/ArticleEntity` | ADAPT | `@TableName("article")`；列/索引 1:1 |
| `model/article.go` StringArray / JSONMap | `mapper/JsonbTypeHandler` | NEW | jsonb 列（`secondary_tags` / `ext_info`）需自定义 TypeHandler |
| `model/article.go` 仓库方法 | `mapper/ArticleMapper` | ADAPT | `BaseMapper` + `@Select` |
| `model/article_public.go` 复合谓词 | `mapper/ArticlePublicMapper`（`@Select` 原样 SQL） | SPLIT | **公开可见谓词是独立 SQL 资产**，与 Article 基础 CRUD 分离 |
| `model/article_public.go` 只读快照 | `ArticlePublicMapper` + 只读事务 | ADAPT | `ListPublic` 需同一连接的 repeatable-read |
| `model/article_sync_outbox.go` | `domain/ArticleSyncOutboxEntity` + `mapper/ArticleSyncOutboxMapper` | ADAPT | `event_key` 唯一键保留 |
| `model/community_revision.go` Revision/Publication/DomainOutbox | 3 个 `domain/*Entity` + 3 个 `mapper/*Mapper` | SPLIT | 三张表持久化用途不同 |
| `model/community_revision.go` `WithArticleTx` FOR UPDATE | `ArticleAtomicService` 上的 `@Transactional` + Mapper `last("FOR UPDATE")` | ADAPT | 与 `admin-rpc` 邀请码行锁同款 |
| `model/community_revision.go` 不可变触发器 | Flyway `V*__create_article_revision.sql` | ADAPT | DDL 迁移 |
| `model/db.go` 触发器启动门禁 | `bootstrap/`（`@PostConstruct` 校验） | ADAPT | **语义保留**：缺触发器必须 fail-fast |
| `mqs/article_sync_types.go` Ext* 常量 + 3 个 DTO | `mq/ArticleSyncEventTypes` | NEW | 常量是 producer/consumer 共享契约，需独立承载 |
| `mqs/article_consumer.go` | `mq/ArticleReviewConsumer` | NEW | 消息驱动入口，生命周期与错误处理语义与 gRPC 完全不同 |
| `mqs/article_sync_result_consumer.go` | `mq/ArticleSyncResultConsumer` | NEW | 同上 |
| `mqs/article_sync_outbox_sender.go` | `mq/ArticleSyncOutboxRelay` | NEW | `@Scheduled` worker |
| `mqs/community_article_event.go` 信封 | `common/communityfact/Wire.Event` + `mq/ArticleSyncEventTypes` | REUSE | 字段名/producer 语义与 `Wire.Event` 逐字一致 |
| `mqs/community_article_event.go` payload | `mq/ArticleSyncEventTypes.CommunityRevisionPayload` | NEW | article 特有 payload，`Wire` 无对应 |
| `mqs/community_article_event.go` `RetractPublicationTx` | `ArticleAtomicService` | SPLIT | 事务载体 |
| consumer span 包装 | `common/observability/MqConsumer` | REUSE | 对应 Go `observability.TraceConsumer` |
| `metrics/metrics.go` | `metrics/ArticleRpcMetrics` | ADAPT | 6 个指标名/标签/触发点保留 |
| gRPC span | `common/observability/RpcInterceptor` | **ADAPT** | 需在 `ArticleRpcApplication` 显式 `@Import` 该 bean，否则无 span⚠️ `@Component` ≠ 自动注册：需 `scanBasePackages` 含 `com.rag.common` 或显式 `@Import`。实测 `user-rpc` / `admin-rpc` 均**未注册**（见 `FileTree/common.md` §12.7 #1）。

---

## 12.5 第五部分：理论 Java 文件树

```
com/rag/article/article/rpc/
├── ArticleRpcApplication.java
├── bootstrap/
│   ├── NacosRegistration.java
│   ├── SnowflakeBootstrap.java
│   ├── MinioBucketBootstrap.java
│   └── ArticleSchemaGate.java
├── config/
│   ├── ArticleRpcProperties.java
│   └── MybatisPlusOrderingBridgeAutoConfiguration.java
├── domain/
│   ├── ArticleEntity.java
│   ├── ArticleSyncOutboxEntity.java
│   ├── ArticleRevisionEntity.java
│   ├── ArticlePublicationEntity.java
│   └── ArticleDomainOutboxEntity.java
├── exception/
│   └── ArticleExceptions.java
├── mapper/
│   ├── ArticleMapper.java
│   ├── ArticlePublicMapper.java
│   ├── ArticleSyncOutboxMapper.java
│   ├── ArticleRevisionMapper.java
│   ├── ArticlePublicationMapper.java
│   ├── ArticleDomainOutboxMapper.java
│   └── JsonbTypeHandler.java
├── client/
│   ├── ArticleObjectStorageClient.java
│   ├── SecurityRpcClient.java
│   ├── ImageSecurityRpcClient.java
│   └── MessageRpcClient.java
├── mq/
│   ├── ArticleSyncEventTypes.java
│   ├── ArticleReviewConsumer.java
│   ├── ArticleSyncResultConsumer.java
│   └── ArticleSyncOutboxRelay.java
├── metrics/
│   └── ArticleRpcMetrics.java
└── service/
    ├── ArticleDomainService.java
    ├── ArticlePublicDomainService.java
    ├── ArticleAtomicService.java
    └── impl/
        └── ArticleServiceGrpcImpl.java
```

### 新增包说明（§3.3 规则 8）

`mq/` 是**新增包**，理由：
1. **职责无法归入现有包** —— `service/` 承载业务用例（被 gRPC 调用），`service/impl/` 承载 gRPC 接口实现；两个 Kafka 消费者既非 HTTP 入口也非 gRPC 入口，它们有独立的生命周期（listener container）、独立的错误契约（部分错误必须静默 drop、部分必须重试）、独立的幂等校验，且各自持有独立事务边界。
2. **目标项目实际依据** —— RAG 当前无 Kafka 消费者先例，`service/common/observability/MqConsumer` 只提供 span 包装不提供消费框架；引入 Spring Kafka 后，listener 的 ack 模式、重试策略、并发度都是需要显式声明的独立关注点。
3. **不因 Go 有同名文件而新增** —— Go 的目录名是 `mqs`，此处是 `mq`；若沿用 Go 的 `handler/logic/repository/middleware/servicecontext` 等目录则属违规，本包不属于该类。

### 逐文件说明（要点）

| 文件 | 主要职责 | 承载 Go 职责 | 为什么独立 |
|---|---|---|---|
| `ArticleRpcApplication` | 入口 | `article.go` | 框架约定 |
| `bootstrap/NacosRegistration` | 服务注册/注销 | `article.go` server 注册 | 生命周期职责；与 users 同款 |
| `bootstrap/SnowflakeBootstrap` | ID 生成器初始化 | `svc` snowflake init | 复用 `common/Snowflake` |
| `bootstrap/MinioBucketBootstrap` | bucket 幂等创建 + public-read 策略 | `svc/service_context.go:43-56` | 启动期幂等副作用，与业务无关 |
| `bootstrap/ArticleSchemaGate` | 校验 `article_revision_immutable` 触发器存在，否则 fail-fast | `model/db.go:62-71` | **L3 语义**：Go 侧缺触发器直接 panic；静默降级会让"不可变 revision"变成可写 |
| `config/ArticleRpcProperties` | 全部配置绑定 | `config/config.go` | 配置独立关注点 |
| `domain/ArticleEntity` | `@TableName("article")`；jsonb 列挂 TypeHandler | `model/article.go` | ORM 适配 |
| `domain/ArticleSyncOutboxEntity` | `@TableName("article_sync_outbox")` | `model/article_sync_outbox.go` | 不同持久化用途 |
| `domain/ArticleRevisionEntity` | `@TableName("article_revision")` | `model/community_revision.go` | 不可变语义，写路径受限 |
| `domain/ArticlePublicationEntity` | `@TableName("article_publication")` | 同上 | 指针表 |
| `domain/ArticleDomainOutboxEntity` | `@TableName("article_domain_outbox")`；`UNIQUE(aggregate_id, aggregate_version)` | 同上 | 事件顺序保证依赖唯一键 |
| `exception/ArticleExceptions` | 按业务含义聚合异常 | 各 logic 错误路径 | RAG 既有约定 |
| `mapper/ArticleMapper` | `BaseMapper<ArticleEntity>` + 作者视角查询 | `model/article.go` 仓库方法 | ORM 适配 |
| `mapper/ArticlePublicMapper` | 复合公开谓词 SQL + `ListPublic`（只读快照）+ `IncrPublicViewCount`（原子谓词更新） | `model/article_public.go` 全部 | **公开可见谓词是独立 SQL 资产**，含 legacy `status=2` 回退与 revision 标签过滤，混进 `ArticleMapper` 会让"内部视角"与"公开视角"边界消失 |
| `mapper/ArticleSyncOutboxMapper` | `CreateTx` / `FetchPending` / `MarkSent` / `MarkFailed` | `model/article_sync_outbox.go` | outbox 状态机独立 |
| `mapper/ArticleRevisionMapper` | 插 revision、按 id 取 | `model/community_revision.go` | — |
| `mapper/ArticlePublicationMapper` | 指针 upsert + `FOR UPDATE` 取指针 | 同上 | 行锁载体 |
| `mapper/ArticleDomainOutboxMapper` | 插域事件 + 取 pending | 同上 | — |
| `mapper/JsonbTypeHandler` | `secondary_tags`(数组) / `ext_info`(对象) 的 jsonb 映射 | `model/article.go` StringArray/JSONMap | Go struct tag 无 Java 对应物 |
| `client/ArticleObjectStorageClient` | MinIO put/get + content-type + CacheControl + public URL 拼接 | `svc` MinIO + `upload_file_logic.go` URL 拼接 | 与 `message-rpc/client/` 同款定位：外部服务客户端 |
| `client/SecurityRpcClient` | `SanitizeContent` | `svc` SecurityRpc | 同上 |
| `client/ImageSecurityRpcClient` | `DetectImageAd` | `svc` ImageSecurityRpc | 同上 |
| `client/MessageRpcClient` | `SendNotification` | `svc` MessageRpc | 同上 |
| `mq/ArticleSyncEventTypes` | `Ext*` 常量 + `ArticleReviewMessage` / `ArticleSyncEvent` / `ArticleSyncResult` / `CommunityRevisionPayload` | `mqs/article_sync_types.go` + `community_article_event.go` payload 部分 | producer/consumer 共享契约；常量值属 L1 |
| `mq/ArticleReviewConsumer` | Kafka 审核消费者：四元组校验 → 审核 → FOR UPDATE 事务写 outbox | `mqs/article_consumer.go` | 消息入口，独立错误契约（drop vs retry） |
| `mq/ArticleSyncResultConsumer` | Kafka 结果消费者：幂等接受 → revision + publication + domain outbox + 一次性通知 | `mqs/article_sync_result_consumer.go` | 同上 |
| `mq/ArticleSyncOutboxRelay` | `@Scheduled(fixedDelay=1s)` 拉 pending 并投递 | `mqs/article_sync_outbox_sender.go` + `article.go` ticker | 定时 worker；单条失败不中断批次 |
| `metrics/ArticleRpcMetrics` | 6 个指标 | `metrics/metrics.go` | 独立变化原因 |
| `service/ArticleDomainService` | 6 个用例的作者视角实现 | `logic/*.go` | 同一变化原因（文章域）、同一依赖边界 |
| `service/ArticlePublicDomainService` | 公开读路径（frozen revision、legacy 回退、公开浏览量） | `logic/get_article_logic.go` 分支 2 + `list_articles_logic.go` 分支 2 + `model/article_public.go` | **与作者视角是不同持久化语义**（读冻结 revision vs 读当前草稿），合并会让"读者看到什么"的规则无法单独定位 |
| `service/ArticleAtomicService` | 承载 3 处多步事务（retract+update / retract+delete / 消费者写 outbox） | `update_article_logic.go` A 路径、`delete_article_logic.go`、`article_consumer.go` | **AGENTS.md §3.3**：必须独立 `@Service` Bean 才能让 `@Transactional` 经代理生效 |
| `service/impl/ArticleServiceGrpcImpl` | `@GrpcService`，6 个 override | `server/article_service_server.go` | gRPC 契约边界 |

---

## 12.6 第六部分：文件变化表

| Go 来源文件 | Java 理论文件或现有机制 | 关系 | 原因 |
|---|---|---|---|
| `article.go` | `ArticleRpcApplication` + 容器 | ABSORB | 进程装配由框架承担 |
| `article.go`（ServiceGroup） | Spring 容器生命周期 | ABSORB | 同启同停由容器保证 |
| `article.go`（outbox ticker） | `mq/ArticleSyncOutboxRelay` | ADAPT | ticker → `@Scheduled` |
| `config/config.go` | `config/ArticleRpcProperties` | ADAPT | 绑定机制换 Spring，字段 1:1 |
| `svc/service_context.go`（MinIO） | `client/ArticleObjectStorageClient` + `bootstrap/MinioBucketBootstrap` | SPLIT | 客户端与启动期幂等副作用分离 |
| `svc/service_context.go`（snowflake） | `bootstrap/SnowflakeBootstrap` + `common/Snowflake` | REUSE | 已有实现 |
| `svc/service_context.go`（3 pusher） | Spring `KafkaTemplate` | ABSORB | 自动配置 |
| `svc/service_context.go`（HotEventPusher） | — | — | 零调用点，**不迁移**（非 ABSORB，是 Go 侧死配置） |
| `svc/service_context.go`（3 rpc client） | `client/{Security,ImageSecurity,Message}RpcClient` | NEW | 外部服务客户端，RAG 有 `client/` 先例 |
| `server/article_service_server.go` | `service/impl/ArticleServiceGrpcImpl` | ADAPT | 分发由 starter 做 |
| `logic/create_article_logic.go` | `ArticleDomainService.createArticle` | MERGE | 6 logic 合 1 |
| `logic/update_article_logic.go` | `ArticleDomainService.updateArticle` + `ArticleAtomicService` | SPLIT | 分支判断归 domain service，多步事务归原子 Bean |
| `logic/delete_article_logic.go` | `ArticleAtomicService.retractAndDelete` | SPLIT | 事务隔离 |
| `logic/get_article_logic.go` | `ArticleDomainService.getArticle` + `ArticlePublicDomainService` | SPLIT | 作者视角 / 公开视角分层 |
| `logic/list_articles_logic.go` | 同上 | SPLIT | 同上 |
| `logic/upload_file_logic.go` | `ArticleDomainService.uploadFile` | MERGE | — |
| `model/article.go`（实体） | `domain/ArticleEntity` | ADAPT | ORM 适配 |
| `model/article.go`（jsonb） | `mapper/JsonbTypeHandler` | NEW | Go struct tag 无对应 |
| `model/article.go`（查询） | `mapper/ArticleMapper` | ADAPT | — |
| `model/article_public.go` | `mapper/ArticlePublicMapper` | SPLIT | 复合公开谓词独立成 mapper |
| `model/article_sync_outbox.go` | `domain/*Entity` + `mapper/ArticleSyncOutboxMapper` | SPLIT | — |
| `model/community_revision.go`（3 实体） | 3 `domain/*Entity` + 3 `mapper/*Mapper` | SPLIT | 三表用途不同 |
| `model/community_revision.go`（`WithArticleTx`） | `@Transactional` + `FOR UPDATE` | ADAPT | 框架替代闭包，需独立 Bean |
| `model/db.go`（连接池） | `application.yml` Hikari 参数 | ADAPT | — |
| `model/db.go`（AutoMigrate） | Flyway `V*__create_article.sql` | ADAPT | 迁移机制替换 |
| `model/db.go`（触发器门禁） | `bootstrap/ArticleSchemaGate` | ADAPT | fail-fast 语义保留 |
| `model/article.sql` / `002_community_revision.sql` | Flyway 两份 DDL（含 CHECK + 触发器） | ADAPT | — |
| `mqs/article_sync_types.go` | `mq/ArticleSyncEventTypes` | NEW | 共享契约载体 |
| `mqs/article_consumer.go` | `mq/ArticleReviewConsumer` | NEW | 消息入口 |
| `mqs/article_sync_result_consumer.go` | `mq/ArticleSyncResultConsumer` | NEW | 消息入口 |
| `mqs/article_sync_outbox_sender.go` | `mq/ArticleSyncOutboxRelay` | NEW | 定时 worker |
| `mqs/community_article_event.go`（信封） | `common/communityfact/Wire.Event` | REUSE | 字段与 producer 语义逐字一致 |
| `mqs/community_article_event.go`（payload） | `mq/ArticleSyncEventTypes` | MERGE | 与常量同载体 |
| `mqs/community_article_event.go`（`RetractPublicationTx`） | `ArticleAtomicService` | SPLIT | 事务归原子 Bean |
| `metrics/metrics.go` | `metrics/ArticleRpcMetrics` | ADAPT | Micrometer |
| consumer span | `common/observability/MqConsumer` | REUSE | — |

---

## 12.7 第七部分：职责覆盖与组织合理性检查

### 通过项

| 检查项 | 结论 |
|---|---|
| L1 外部契约已识别 | ✅ 6 个 gRPC 方法及完整字段；5 张表与索引；`ext_info` 全部 Ext 键；4 个 Kafka topic；MinIO bucket/path；`event_id`/`revision_id`/`aggregate_version` 格式 |
| L2 业务语义有承载位置 | ✅ 公开读冻结规则、legacy `status=2` 回退、revision 标签过滤、审核四元组、一次性通知守卫、路径 A 才建 outbox —— 逐项落到具体方法 |
| L3 技术语义有承载方案 | ✅ 5 处事务边界 → `ArticleAtomicService`（3 处）+ `@Transactional`；`FOR UPDATE` 行锁 → Mapper `last("FOR UPDATE")`；outbox 状态机 → `ArticleSyncOutboxMapper`；重试上限 10 → `FetchPending` 条件 |
| 业务分支与异常路径已纳入 | ✅ drop vs retry 的区分、浏览量自增失败的不对称（best-effort vs 致命）、`RequesterId+IncrView` 非法组合 |
| 框架吸收有机制依据 | ✅ 进程装配 → Spring Boot；pusher → `KafkaTemplate`；池参数 → `application.yml`；均指出实际机制 |
| 复用组件已明确 | ✅ `Wire.Event`、`Snowflake`、`MqConsumer`、`RpcInterceptor`、`BusinessException` |
| 新增文件有职责依据 | ✅ `mq/*`（消息入口生命周期与错误契约独立）、`client/*`（外部服务客户端）、`ArticleAtomicService`（代理边界）、`ArticleSchemaGate`（fail-fast）—— 均非"Go 有同名文件" |
| 合并有组织依据 | ✅ 6 logic → `ArticleDomainService`：同一变化原因 + 同一依赖边界 |
| 拆分可解释 | ✅ 作者/公开视角、事务原子 Bean、业务/持久化 SQL 分离 |
| 无"为对应 Go 文件而建"的文件 | ✅ 未创建 `Logic.java` / `Repository.java` / `DataModel.java` / `ServiceContext.java` / `Server.java` |
| 未把未实现能力说成现有机制 | ✅ RAG `article-rpc` 现为空骨架，已在 §12.1 明确；Spring Kafka 与 MinIO 标注为"无先例需引入" |

### 不通过项

| # | 问题 | 详情 |
|---|---|---|
| 1 | **`article_domain_outbox` 无投递方** | Go 侧 article-rpc 只写 outbox，**没有 dispatcher 进程**；`fact-dispatch` 只存在于 like。`common/communityfact/Dispatcher` 具备能力但 article 未接。若不补，`article_domain_outbox` 是只增不发的死表。**需用户确认**：H03 投递是本模块职责还是外部独立服务 |
| 2 | **Flyway 归属未定** | 与 users 同一问题（AGENTS.md §6.3） |
| 3 | **Spring Kafka / MinIO 无 RAG 先例** | 本模块需首次引入两个框架。依赖版本、listener ack 模式、重试策略需在实现前确认 |
| 4 | **`article_status_total` 指标不完整** | Go 侧只 `reviewing` 被 +1，`published`/`rejected` **无任何调用点**。若 Java 侧"补全"属新增行为，需拍板 |
| 5 | **Go 侧 `update_article_logic.go:90` 编译疑点** | `__.ArticleStatus(req.Status)` 使用了生成包别名但该文件未 import。迁移时需确认真实语义（应为 `articlepb.ArticleStatus`） |

### UNKNOWN 清单

| 项 | 说明 |
|---|---|
| `related_game_id` 是否被消费 | proto/api/logic 贯穿，但 `ArticleRepo.List` / `ListPublic` 均不按它过滤 |
| `SecurityRpc` 在 api 侧的用途 | 已声明但 api 无调用点 |
| `wiki_module_id` 恒为 nil 的下游契约 | 社区文章永不进知识库模块 |
| `article_favorite_fixture_test.go` 用途 | 文件名指向跨服务 fixture，未开 |
