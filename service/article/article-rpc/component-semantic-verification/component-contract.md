# 组件共同语义边界

> 两侧都遵守、但**跨组件耦合**的不变量。单组件内部的不变量见两份 semantic-model。
> 这类不变量最容易在「每个组件单看都对」时整体失效。

## G-1 审核四元组（审核消费者 ↔ 创建/更新路径）

四个字段必须**同时**成立，否则旧审核任务被丢弃：

```
status == REVIEWING  ∧  article.content == msg.content_path
                      ∧  article.author_id == msg.author_id
                      ∧  ext_info[review_nonce] == msg.review_nonce
```

| | 位置 |
|---|---|
| Go 生产 | `logic/create_article_logic.go:105`（初始 nonce = articleID）、`logic/update_article_logic.go:129-137`（每次重审换新 nonce） |
| Go 消费 | `internal/mqs/article_consumer.go:62-64`（事务外）+ `:105-107`（事务内，`WithArticleTx` 内二次校验） |
| Java 生产 | `service/ArticleDomainService.java:131`、`:425-429` |
| Java 消费 | `service/ArticleAtomicService.java:254-260`（事务内）；事务外判据在 `mq/ArticleReviewConsumer.java:187-193` |

**边界含义**：`review_nonce` 是把「哪一轮审核」与「哪一份正文」绑定的唯一凭据。
若生产者换了 nonce 而消费者不校验（或反之），旧任务会批准新内容。

**状态**：✅ EQUIVALENT（两侧都是两层校验，事务内那次都在行锁内）

---

## G-2 冻结输入（outbox 生产 ↔ 发布回调消费）

发布回调**不允许**从结果消息里取正文，只能读回当初冻结进 `article_sync_outbox.payload` 的那份，并做 6 项比对：

```
op / event_id / version_ms / article_id / author_id  —— 任一不符 ⇒ 抛错让事务回滚
```

| | 位置 |
|---|---|
| Go 生产 | `internal/mqs/article_consumer.go:118-123`（payload 与推送字节同源） |
| Go 消费 | `internal/mqs/article_sync_result_consumer.go:95-107` |
| Java 生产 | `service/ArticleAtomicService.java:510-517`（`insertSyncOutbox`，payload 一次性序列化） |
| Java 推送 | `mq/ArticleSyncOutboxRelay.java:102-106`（推 `event.getPayload()` 原文，不重序列化） |
| Java 消费 | `service/ArticleAtomicService.java:350-370` |

**边界含义**：relay 必须推 **payload 原文**。若改成重新序列化，`jsonb` 会重排键序 / 改空值字段，
比对虽仍能过（JSON 语义），但**字节级可追溯性**丢失。

**状态**：✅ EQUIVALENT

---

## G-3 发布指针单调推进（指针 ↔ 域事件 ↔ revision）

```
article_publication.pointer_version  每次 published/retracted 都 +1
article_domain_outbox               UNIQUE(aggregate_id, aggregate_version)
article_revision                    BEFORE UPDATE OR DELETE 触发器 ⇒ 不可变
```

| | 位置 |
|---|---|
| Go 生产 | `internal/mqs/article_sync_result_consumer.go:130-145`、`internal/mqs/community_article_event.go:126-136` |
| Go 约束 | `internal/model/community_revision.go:45-55`；触发器由 `internal/model/002_community_revision.sql` 建，`internal/model/db.go:61-71` **启动期硬校验** |
| Java 生产 | `service/ArticleAtomicService.java:451-463`、`:201-210` |
| Java 约束 | `V10__create_article_community_revision.sql:16-70`；启动期硬校验 `bootstrap/ArticleSchemaGate.java:73-120` |

**边界含义**：这是「已发布字节不可变」**唯一的强制手段**。三件套（表 / 触发器 / UNIQUE）缺一，
该保证就退化成注释。

**状态**：✅ EQUIVALENT（Go 的 fail-fast panic ↔ Java 的 `ApplicationReadyEvent` 抛异常阻止启动）

---

## G-4 软删除可见性（删除路径 ↔ 全部读路径）

| 查询 | Go | Java |
|---|---|---|
| 作者视角 / 审核消费者 | `FindOne`（GORM 自动追加 `deleted_at IS NULL`） | `ArticleMapper.selectById`（`:42` 显式带 `deleted_at IS NULL`） |
| 同步结果消费者 | `FindOneUnscoped`（唯一 Unscoped 调用点） | `ArticleMapper.selectByIdUnscoped`（`:80`） |
| 事务内重读 | `WithArticleTx` 用 `Unscoped()` + `FOR UPDATE`（`community_revision.go:84-85`） | `selectByIdForUpdate`（`:205`，**不带** `deleted_at` 过滤） |

**边界含义**：`@TableLogic` 被**刻意不引入**（`domain/ArticleEntity.java:145-149`）。
代价：MyBatis-Plus 生成的 `updateById` **不会**追加 `deleted_at IS NULL`，
而 GORM 的 `Save` 会。当前所有 `updateById` 调用点都先查过未删行，**未观察到差异**，
但保证强度不同 ⇒ 见 `unresolved.md` U-3。

**状态**：✅ EQUIVALENT（可观察层）/ ⚠️ 机制层见 U-3

---

## G-5 错误契约（gRPC 边界 ↔ api 边界）

Go 的 api 侧**只按 gRPC code 映射**，不读 description：

| rpc 侧 code | api 侧业务码 |
|---|---|
| `NOT_FOUND` | `ErrorArticleNone` (1002) |
| `PERMISSION_DENIED` | `ErrorArticleForbidden` (1006) |
| `INTERNAL` | `ErrorServerCommon` (5001) |
| **其它一律** `default` | `CodeServerBusy` (1015) |

Go 证据：`Sea-RideTheWind_golang/service/article/api/internal/logic/article/delete_article_logic.go:49-59`。
Java 侧映射由 `service/impl/ArticleServiceGrpcImpl.java:120-138` 保证
（`ArticleExceptions.Rpc` 用自带 code，`Raw` 与其它 `RuntimeException` 一律 `UNKNOWN`）。

**边界含义**：**description 变更 ⇒ 不影响 HTTP 信封**，只影响日志与 `grpcurl`。
**code 变更 ⇒ 影响业务码**。这条决定了 O-1（低危）与 S-6（高危）的分级差异。

**状态**：⚠️ S-6 —— `StatusRuntimeException(DEADLINE_EXCEEDED)` 会被 `invoke` 的
`catch (RuntimeException)` 改写成 `UNKNOWN`，业务码从 Go 的 5001 变成 1015。

---

## G-6 Kafka 失败位点契约（两个消费者 ↔ 容器错误处理器）

**这是两侧最容易整体错位、而每个消费者单看都正确的一条边界。**

Go 的真实控制流（`go-queue@v1.2.2`）：

```go
// kq/config.go:24
ForceCommit bool `json:",default=true"`      // ← Go 的 article.yaml 未设置 ⇒ true

// kq/queue.go:230-242
if err := q.consumeOne(ctx, key, val); err != nil {
    if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }   // article-rpc 未传 ⇒ nil
    if !q.c.ForceCommit { continue }          // ForceCommit=true ⇒ 不 continue
}
if err := q.consumer.CommitMessages(ctx, msg); err != nil { ... }  // ← 失败也提交
```

⇒ **Go 在 handler 返回 error 时依然提交该消息的位点。**

Java 侧显式反向：`ArticleRpcApplication.java:171` `new FixedBackOff(0L, 0L)` +
`:173` `setSeekAfterError(false)`。

**边界含义**：这是 S-3。它不是「重试次数」问题，是**失败消息最终被丢弃还是被无限重投**的问题。
逐消费者看（`ArticleReviewConsumer` / `ArticleSyncResultConsumer` 的丢弃/重投分支）两侧都一致，
**只有这一层共享的失败契约相反**。

**状态**：❌ SEMANTIC MISMATCH（S-3）