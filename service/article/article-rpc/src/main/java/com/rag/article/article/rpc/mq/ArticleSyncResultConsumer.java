package com.rag.article.article.rpc.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.rpc.client.MessageRpcClient;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.mapper.ArticleSyncOutboxMapper;
import com.rag.article.article.rpc.service.ArticleAtomicService;
import com.rag.article.article.rpc.service.ArticleSpans;
import com.rag.common.observability.MqConsumer;
import io.opentelemetry.context.Context;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 同步结果消费者 —— 对应 Go 端 {@code internal/mqs/article_sync_result_consumer.go} 的
 * {@code ArticleSyncResultConsumer.Consume} + {@code handleUpsertResult} + {@code handleDeleteResult}。
 *
 * <h2>🔴 这是整个组件里幂等语义最重的一处：<b>迟到 / 重放的结果绝不能改变状态</b></h2>
 * <p>{@code handleUpsertResult} 的判据链（Go {@code :78-94}），<b>顺序不可调换</b>：
 * <pre>
 * if article.DeletedAt.Valid { return nil }                                  // ① 已删
 * if ext[last_sync_event_id] != result.EventID
 *    || ext[last_sync_version] != FormatInt(result.VersionMs)
 *    || article.Status == PUBLISHED { return nil }                          // ② 已处理 / 已发布 / 版本不符
 * if !result.Success {
 *     status = REVIEWING; SetSyncState(reco_failed, failed, …); Save; return // ③ 失败
 * }
 * if article.Status != REVIEWING { return nil }                              // ④ 已不是审核中
 * approved := 读回 outbox payload
 * if approved.Op/EventID/VersionMs/ArticleID/AuthorID 任一不符 { return error }  // ⑤ 冻结输入比对
 * … 建 revision + 推进指针 + 写域事件 + status=PUBLISHED + published_once
 * </pre>
 * ② 在 ③ <b>之前</b>：这保证"已发布的结果重放"不会走进失败分支把状态改回 REVIEWING。
 * ② 在 ④ 之前：这保证"版本不匹配的迟到结果"不会推进指针。
 *
 * <h2>⚠️ 六项冻结输入比对（⑤）是防篡改的最后一道闸</h2>
 * 结果消息只带 {@code event_id / article_id / op / version_ms / success}，
 * <b>不带正文</b>。批准用的字节必须来自<b>当初冻结进 outbox 的那份 payload</b>。
 * 比对失败 ⇒ 返回 error ⇒ 事务回滚 ⇒ Kafka 重投 ⇒ 人工介入。
 * ⚠️ <b>不要改成"信任结果消息里的 id 就用 outbox 的正文"</b>：
 * 那样跨文章/跨事件的错配会被静默接受，发布出一篇<b>别的</b>文章的内容。
 */
@Component
public class ArticleSyncResultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleSyncResultConsumer.class);

    private final ObjectMapper objectMapper;
    private final ArticleMapper articleMapper;
    private final ArticleSyncOutboxMapper syncOutboxMapper;
    private final ArticleAtomicService atomicService;
    private final MessageRpcClient messageRpcClient;

    public ArticleSyncResultConsumer(ObjectMapper objectMapper,
                                     ArticleMapper articleMapper,
                                     ArticleSyncOutboxMapper syncOutboxMapper,
                                     ArticleAtomicService atomicService,
                                     MessageRpcClient messageRpcClient) {
        this.objectMapper = objectMapper;
        this.articleMapper = articleMapper;
        this.syncOutboxMapper = syncOutboxMapper;
        this.atomicService = atomicService;
        this.messageRpcClient = messageRpcClient;
    }

    /**
     * 消费一条同步结果。
     *
     * <p>⚠️ <b>topic 与 group 取自各自独立的配置字段</b>
     * （{@code article-sync-result-consumer-conf.topic} / {@@code .group}），
     * 不要与审核消费者共用。
     */
    @KafkaListener(
            id = "article-sync-result-consumer",
            topics = "${article-rpc.article-sync-result-consumer-conf.topic}",
            groupId = "${article-rpc.article-sync-result-consumer-conf.group}",
            containerFactory = "articleKafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record, String value) {
        // 🔴 L-10（Go 出处：article_sync_result_consumer.go:39-40）：
        //     func (l *ArticleSyncResultConsumer) Consume(ctx context.Context, key, val string) error {
        //         return observability.TraceConsumer(ctx, "article-rpc", "ArticleSyncResultConsumer.Consume",
        //             articleMessageAttrs("article_sync_result", key), func(ctx context.Context) error { … })
        //     }
        //    ⇒ 共享层 MqConsumer.traceConsumer 与 Go 的 TraceConsumer **逐项同构** ⇒ 纯域侧接线。
        //    ⚠️ messaging.destination 是**逻辑名** "article_sync_result"，
        //      **不是** kafka topic（真源 topic 是 article-sync-results）。
        Map<String, Object> attrs = ArticleSpans.consumerAttrs(
                ArticleSpans.DESTINATION_SYNC_RESULT, record == null ? null : record.key());
        Throwable outcome = MqConsumer.traceConsumer(Context.current(), ArticleSpans.TRACER_NAME,
                ArticleSpans.CONSUMER_SYNC_RESULT, attrs, ctx -> consume(value));
        if (outcome != null) {
            // Go 的 kq：Consume 返回 error ⇒ 不确认位点 ⇒ 重投 ⇒ 必须**抛出**而不是吞掉。
            throw toRuntime(outcome);
        }
    }

    /**
     * 消费主体；返回非 null 即代表「失败，要重投」。
     *
     * <p>🔴 语义等价论证见 {@code ArticleReviewConsumer#consume}：
     * Go 的 {@code TraceConsumer} 在 {@code err != nil} 时
     * <b>既</b> {@code MarkSystemError}/{@code MarkTimeout} <b>又</b> {@code return err}
     * ⇒ 本实现：lambda 内转成返回值（让共享层能标错），lambda 外原样抛出（保重投）。
     */
    private Throwable consume(String value) {
        try {
            doConsume(value);
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private RuntimeException toRuntime(Throwable t) {
        return (t instanceof RuntimeException re) ? re : new IllegalStateException(t);
    }

    private void doConsume(String value) {
        // ---------- 分支 1：JSON 解析失败 ⇒ 丢弃（Go :42-45）----------
        ArticleSyncEventTypes.ArticleSyncResult result;
        try {
            result = objectMapper.readValue(value, ArticleSyncEventTypes.ArticleSyncResult.class);
        } catch (Exception e) {
            LOG.warn("drop article sync result with invalid json: {}", e.getMessage());
            return;
        }

        // ---------- 分支 2：article_id 为空 ⇒ 丢弃（Go :46-48）----------
        // ⚠️ Go 用 strings.TrimSpace 后判空
        if (result.articleId == null || result.articleId.trim().isEmpty()) {
            return;
        }

        // ⚠️ 必须用 Unscoped 查询（Go :50 FindOneUnscoped）：
        //    已删除的文章仍要能收到同步结果，才能把 delete 的同步状态落库。
        ArticleEntity article = articleMapper.selectByIdUnscoped(result.articleId);
        // ---------- 分支 3：文章不存在（含已删）⇒ 丢弃（Go :51-55）----------
        if (article == null) {
            LOG.info("article sync result ignored for missing article: articleId={}", result.articleId);
            return;
        }

        if (ArticleSyncEventTypes.OP_UPSERT.equals(result.op)) {
            handleUpsertResult(article, result);
        } else if (ArticleSyncEventTypes.OP_DELETE.equals(result.op)) {
            handleDeleteResult(article, result);
        } else {
            // ---------- 分支 4：未知 op ⇒ 丢弃（Go :65-67）----------
            LOG.info("unknown article sync result op ignored: articleId={} op={}",
                    result.articleId, result.op);
        }
    }

    /**
     * 对应 Go {@code handleUpsertResult}（{@code article_sync_result_consumer.go:72-164}）。
     *
     * <h2>🔴 为什么判定必须在<b>事务 + 行锁</b>内完成</h2>
     * Go 的 {@code WithArticleTx} 闭包里第一件事就是<b>再次</b>取文章并加 {@code FOR UPDATE}。
     * 外层的 {@code FindOneUnscoped} 只用于区分「文章不存在 → 丢弃」，<b>不承载任何判定</b>。
     * 若在锁外判定，两条并发结果消息可能都通过"仍是 REVIEWING"的检查，
     * 然后都算出相同的 {@code max(revision)+1} ⇒ 撞 article_revision 主键。
     * ⇒ 全部判定与写入都在 {@link ArticleAtomicService#acceptSyncResult} 内（带 {@code @Transactional}）。
     *
     * <p>⚠️ 通知在<b>事务提交之后</b>发（Go {@code :160-163}）。失败只记日志，不影响发布结果。
     */
    void handleUpsertResult(ArticleEntity outer, ArticleSyncEventTypes.ArticleSyncResult result) {
        String authorId;
        String title;
        try {
            ArticleAtomicService.PublishOutcome outcome =
                    atomicService.acceptSyncResult(result, objectMapper);
            if (outcome == null || !outcome.notifyRequired()) {
                return;
            }
            authorId = outcome.authorId();
            title = outcome.title();
        } catch (Exception e) {
            LOG.warn("persist article revision and domain outbox failed, will retry: articleId={} err={}",
                    result.articleId, e.getMessage());
            throw new IllegalStateException("persist article revision and domain outbox failed", e);
        }
        notifyPublished(authorId, result.articleId, title);
    }

    /**
     * 对应 Go {@code notifyArticlePublished}（{@code :185-212}）。
     *
     * <h2>⚠️ 三条不可改的语义</h2>
     * <ol>
     *   <li><b>uid 解析失败只记日志并返回</b>（Go :186-190）—— 不是错误，
     *       因为此时文章<b>已经发布成功</b>，不能因通知收件人解析失败而回滚。</li>
     *   <li><b>无标题文案与有标题文案不同</b>，且判定用 {@code TrimSpace}。</li>
     *   <li><b>RPC 失败只记日志</b>（Go :210-212）。</li>
     * </ol>
     */
    private void notifyPublished(String authorId, String articleId, String title) {
        long uid = ArticleSyncEventTypes.parsePositiveUid(authorId);
        // ⚠️ authorId 是 **DB 里的原始字符串**，不是 uid 回退后的数字串。
        //    Go 传的就是原串（article_sync_result_consumer.go:205-208）；
        //    对规范十进制 id 两者相同，对 "0012345" / " 123" 等非规范写法会不同。
        if (uid <= 0) {
            LOG.warn("invalid article author id for notification: authorId={} articleId={}",
                    authorId, articleId);
            return;
        }
        messageRpcClient.sendArticlePublished(uid, authorId, articleId, title);
    }

    /**
     * 对应 Go {@code handleDeleteResult}（{@code article_sync_result_consumer.go:166-183}）。
     *
     * <h2>⚠️ 两条不对称</h2>
     * <ol>
     *   <li><b>已软删的行不写回</b>（Go {@code :174-176}）：删除后行已不可见，
     *       再写 {@code ext_info} 没有读者，且会复活一个本该消失的状态。
     *       <b>但 SetSyncState 仍然执行了</b>（Go :168-172 在 DeletedAt 判断之前），
     *       只是结果没落库。</li>
     *   <li><b>这一路径不重试</b>：Go 用 {@code ArticleRepo.Update}（单条 UPDATE），
     *       失败返回 error ⇒ 重投。本实现同样抛异常。</li>
     * </ol>
     *
     * <p>⚠️ <b>注意与 upsert 的差异</b>：upsert 的成功路径写 {@code (published, succeeded)}，
     * delete 的成功路径写 {@code (source_only, succeeded)}——
     * publish_stage 是 {@code source_only} 不是 {@code published}
     * （删除不代表"已发布"），这个差别会影响运维看板的分组。
     */
    void handleDeleteResult(ArticleEntity article, ArticleSyncEventTypes.ArticleSyncResult result) {
        Map<String, String> extInfo = ArticleSyncEventTypes.ensureExtInfo(article.getExtInfo());
        article.setExtInfo(extInfo);
        if (result.success) {
            ArticleSyncEventTypes.setSyncState(extInfo, "source_only", "succeeded",
                    ArticleSyncEventTypes.REASON_DELETE, result.eventId, result.versionMs, "");
        } else {
            ArticleSyncEventTypes.setSyncState(extInfo, "delete_failed", "failed",
                    ArticleSyncEventTypes.REASON_DELETE, result.eventId, result.versionMs,
                    result.errorMessage);
        }

        if (article.getDeletedAt() != null) {
            // Go: if article.DeletedAt.Valid { return nil } —— 不写回
            return;
        }
        articleMapper.updateById(article);
    }
}