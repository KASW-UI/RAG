package com.rag.comment.comment.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.comment.comment.rpc.client.MessageRpcClient;
import com.rag.message.message.proto.NotificationKind;
import com.rag.comment.comment.rpc.domain.CommentIndexEntity;
import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.common.observability.MqConsumer;
import io.opentelemetry.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评论审计消费者 —— 对应 Go 端
 * {@code service/comment/rpc/internal/mqs/audit_consumer.go:33-132} 的
 * {@code AuditConsumer.Consume} + {@code notifyComment} + {@code previewComment}。
 *
 * <h2>⚠️ 四条出口的方向<b>就是</b>本类最重要的语义</h2>
 * <table border="1">
 *   <caption>Go audit_consumer.go:36-51 的四条出口</caption>
 *   <tr><th>情形</th><th>Go 动作</th><th>位点后果</th></tr>
 *   <tr><td>JSON 解析失败</td><td>记日志 + {@code return nil}</td><td><b>提交</b>（丢弃该消息）</td></tr>
 *   <tr><td>落库失败</td><td>记日志 + {@code return err}</td><td><b>不提交</b>（重投）</td></tr>
 *   <tr><td>缓存失效失败</td><td>只记日志</td><td>正常提交</td></tr>
 *   <tr><td>通知发送失败</td><td>只记日志</td><td>正常提交</td></tr>
 * </table>
 * <b>方向反了就是数据丢失或重复</b>：把解析失败改成 throw ⇒ 坏消息无限重投打满；
 * 把落库失败改成 return ⇒ 消息被确认但评论<b>永远不落库</b>。
 *
 * <p>⚠️ 解析失败「丢弃」是<b>有意的</b>：坏消息重投一万次也不会变好，
 * 而它会<b>堵住该分区的后续消息</b>。
 *
 * <p><b>⚠️ 「落库失败重投」依赖两个前提</b>：{@code ack-mode: record}
 * + listener 抛异常时<b>不</b>提交位点。见 {@code application.yml} 与
 * {@code CommentRpcApplication#kafkaListenerContainerFactory}。
 */
@Component
public class CommentAuditListener {

    private static final Logger LOG = LoggerFactory.getLogger(CommentAuditListener.class);

    /** Go {@code audit_consumer.go:81} {@code messagepb.NotificationKind_ARTICLE_COMMENT}。 */
    private static final String TITLE_ARTICLE = "文章收到新评论";

    /** Go {@code audit_consumer.go:89} {@code messagepb.NotificationKind_COMMENT_REPLY}。 */
    private static final String TITLE_REPLY = "评论收到新回复";

    /** Go {@code audit_consumer.go:122-132} 的截断长度：40 个 rune。 */
    private static final int PREVIEW_RUNE_LIMIT = 40;

    private final ObjectMapper objectMapper;
    private final CommentTransactionalService transactionalService;
    private final CommentCacheService cacheService;
    private final CommentSensitiveFilter sensitiveFilter;
    private final MessageRpcClient messageRpcClient;
    private final CommentIndexMapper commentIndexMapper;

    public CommentAuditListener(ObjectMapper objectMapper,
                                CommentTransactionalService transactionalService,
                                CommentCacheService cacheService,
                                CommentSensitiveFilter sensitiveFilter,
                                MessageRpcClient messageRpcClient,
                                CommentIndexMapper commentIndexMapper) {
        this.objectMapper = objectMapper;
        this.transactionalService = transactionalService;
        this.cacheService = cacheService;
        this.sensitiveFilter = sensitiveFilter;
        this.messageRpcClient = messageRpcClient;
        this.commentIndexMapper = commentIndexMapper;
    }

    /**
     * 消费一条待审计的评论消息。
     *
     * <p>对应 Go {@code kq.Queue} 注册的 {@code AuditConsumer.Consume}。
     *
     * <p>⚠️ <b>topic 用 {@code ${}} 占位符而不是 {@code #{@bean}}</b>：
     * SpEL 取 {@code @ConfigurationProperties} Bean 会在解析阶段抛 EL1058E
     * （like-rpc 已在 {@code LikeUpdateKafkaListener.java:77-80} 踩过）。
     * 并发度对应 Go {@code KqConsumerConf.Consumers: 8}。
     *
     * @param value Kafka 的消息体（UTF-8 JSON）
     */
    @KafkaListener(
            topics = "${comment.kafka.audit-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${comment.kafka.audit-concurrency:8}")
    public void onMessage(String key, String value) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("messaging.system", "kafka");
        attrs.put("messaging.destination", "${comment.kafka.audit-topic}");
        attrs.put("messaging.operation", "consume");
        attrs.put("messaging.message.key", key == null ? "" : key);

        Throwable outcome = MqConsumer.traceConsumer(Context.current(), "comment-rpc",
                "AuditConsumer.Consume", attrs, ctx -> consume(value));
        if (outcome != null) {
            // Go 的 kq：Consume 返回 error ⇒ 不确认位点 ⇒ 重投。
            // 这里必须**抛出**而不是吞掉，否则失败的消息会被当成已处理。
            throw toRuntime(outcome);
        }
    }

    private RuntimeException toRuntime(Throwable t) {
        return (t instanceof RuntimeException re) ? re : new IllegalStateException(t);
    }

    /** 消费主体；返回非 null 即代表「失败，要重投」。 */
    private Throwable consume(String value) {
        // --- ① 解析失败 ⇒ 丢弃（Go :36-39 return nil）
        CommentAuditMessage msg;
        try {
            msg = objectMapper.readValue(value, CommentAuditMessage.class);
        } catch (Exception e) {
            LOG.error("parse comment kafka message failed: {}", e.getMessage(), e);
            return null;   // ← 关键：返回 null 而不是抛异常
        }

        // --- ② 敏感词判定（Go :41-46）：只改 status，**不改内容**
        boolean isSensitive = sensitiveFilter.match(msg.getContent());
        int status = 0;
        if (isSensitive) {
            LOG.info("comment {} matched sensitive word \"{}\"", msg.getCommentId(),
                    sensitiveFilter.firstHit(msg.getContent()));
            status = 1;
        }

        // --- ③ 落库（Go :48-51）：失败 ⇒ 重投
        try {
            transactionalService.insertCommentTx(msg, status);
        } catch (RuntimeException e) {
            LOG.error("persist comment {} failed: {}", msg.getCommentId(), e.getMessage(), e);
            return e;
        }

        // --- ④ 缓存失效（Go :52-59）：失败只记日志
        try {
            cacheService.invalidateTargetCaches(msg.getTargetType(), msg.getTargetId());
        } catch (RuntimeException e) {
            LOG.error("invalidate comment target cache failed, comment {}: {}",
                    msg.getCommentId(), e.getMessage());
        }
        if (msg.getParentId() != 0) {
            try {
                cacheService.deleteCommentIndexCache(msg.getParentId());
            } catch (RuntimeException e) {
                LOG.error("invalidate parent comment cache failed, parent {}: {}",
                        msg.getParentId(), e.getMessage());
            }
        }

        // --- ⑤ 只有「正常」评论才发通知（Go :61-63）
        if (status == 0) {
            notifyComment(msg);
        }

        LOG.info("comment pipeline finished, id={}, status={}", msg.getCommentId(), status);
        return null;
    }

    /**
     * 对应 Go {@code notifyComment}（audit_consumer.go:79-120）。
     *
     * <p>三条规则：
     * <ol>
     *   <li><b>有父评论 ⇒ 收件人是父评论作者</b>，且 kind 变成「评论收到新回复」；</li>
     *   <li><b>查父评论失败就直接放弃通知</b>（Go :87-90 {@code return}），
     *       不回退到通知 up 主 —— 否则会通知错的人；</li>
     *   <li><b>{@code recipientID <= 0 || recipientID == msg.UserId} ⇒ 不发</b>
     *       （Go :97-99）—— 防止「自己评论自己收到通知」。</li>
     * </ol>
     *
     * <p>发送失败<b>只记日志</b>（Go :117-119）：此时评论已落库、缓存已失效，
     * 抛错会导致整条消息重投 ⇒ 重复落库 + 重复通知。
     */
    private void notifyComment(CommentAuditMessage msg) {
        long recipientId = msg.getOwnerId();
        NotificationKind kind = NotificationKind.ARTICLE_COMMENT;
        String title = TITLE_ARTICLE;
        String content = "你的文章收到了一条新评论：" + previewComment(msg.getContent());

        if (msg.getParentId() != 0) {
            CommentIndexEntity parent;
            try {
                parent = commentIndexMapper.findOneById(msg.getParentId());
            } catch (RuntimeException e) {
                LOG.error("query parent comment {} for notification failed: {}",
                        msg.getParentId(), e.getMessage());
                return;
            }
            if (parent == null) {
                // Go 的 First 返回 ErrRecordNotFound ⇒ 同样直接 return（:87-90）
                LOG.error("query parent comment {} for notification failed: not found", msg.getParentId());
                return;
            }
            recipientId = parent.getUserId();
            kind = NotificationKind.COMMENT_REPLY;
            title = TITLE_REPLY;
            content = "你的评论收到了新的回复：" + previewComment(msg.getContent());
        }

        if (recipientId <= 0 || recipientId == msg.getUserId()) {
            return;
        }

        messageRpcClient.sendNotification(recipientId, msg.getUserId(), kind, title, content,
                msg.getTargetType(), msg.getTargetId(), msg.getCommentId(), msg.getRootId(), msg.getParentId());
    }

    /**
     * 对应 Go {@code previewComment}（audit_consumer.go:122-132）：
     * 空 ⇒ {@code "(no content)"}；≤40 rune ⇒ 原样；否则前 40 个 rune + {@code "..."}。
     *
     * <p>⚠️ <b>按 rune 而不是 char 截断</b>：Go 的 {@code utf8.RuneCountInString} 数的是码点，
     * Java 的 {@code String.length()} 数的是 UTF-16 code unit。
     * 含 emoji / 代理对的评论在 Java 侧会少截一半字符 ⇒ 用 {@code codePointCount}。
     */
    static String previewComment(String content) {
        if (content == null) {
            return "(no content)";
        }
        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            return "(no content)";
        }
        int runeCount = trimmed.codePointCount(0, trimmed.length());
        if (runeCount <= PREVIEW_RUNE_LIMIT) {
            return trimmed;
        }
        int endIndex = trimmed.offsetByCodePoints(0, PREVIEW_RUNE_LIMIT);
        return trimmed.substring(0, endIndex) + "...";
    }
}