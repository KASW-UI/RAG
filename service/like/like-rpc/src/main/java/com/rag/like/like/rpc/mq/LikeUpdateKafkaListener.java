package com.rag.like.like.rpc.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;

import com.rag.like.like.rpc.metrics.LikeMqMetrics;
import com.rag.like.like.rpc.service.LikeMessageIngestService;

import java.util.List;

/**
 * 点赞事件消费入口 —— 1:1 对应 Go 端
 * {@code service/like/rpc/internal/mq/internal/mqs/likemq.go} 的 {@code LikeUpdateService.Consume}。
 *
 * <h2>⚠️ 错误契约必须区分 drop 与 retry（这是本类存在的全部理由）</h2>
 *
 * <p>Go {@code Consume} 的返回值决定 Kafka 是否确认位点：
 * <table border="1">
 *   <caption>Go 五条出口</caption>
 *   <tr><th>分支</th><th>Go 返回</th><th>后果</th><th>指标</th></tr>
 *   <tr><td>JSON 解析失败</td><td>{@code nil}</td><td><b>丢弃</b>，提交位点</td><td>{@code receive/json_error}</td></tr>
 *   <tr><td>字段校验失败</td><td>{@code nil}</td><td><b>丢弃</b>，提交位点</td><td>{@code receive/empty_msg_id}</td></tr>
 *   <tr><td>构造 outbox 失败（雪花 ID）</td><td>{@code err}</td><td><b>重投</b>，不提交</td><td>{@code receive/outbox_error}</td></tr>
 *   <tr><td>DB 事务失败</td><td>{@code err}</td><td><b>重投</b>，不提交</td><td>{@code receive/tx_error}</td></tr>
 *   <tr><td>成功</td><td>{@code nil}</td><td>提交位点</td><td>{@code receive/committed}</td></tr>
 * </table>
 *
 * <p>坏消息要<b>丢弃</b>：它们不可修复（JSON 坏了就是坏了），一直重投会卡死整个分区，
 * 后面的正常消息永远处理不了。
 * 持久层失败要<b>重投</b>：DB 可能只是短暂不可用，消息本身没问题。
 *
 * <h2>Java 侧的契约方向是相反的</h2>
 *
 * <p>Spring Kafka：<b>listener 正常返回 ⇒ 提交位点；抛异常 ⇒ 不提交</b>。
 * （Go 是「返回 error ⇒ 不确认」，Java 是「抛异常 ⇒ 不确认」。）
 * 所以 drop 分支<b>正常 return</b>，retry 分支<b>抛异常</b>。
 *
 * <h2>⚠️ 为什么不做进程内重试</h2>
 *
 * <p>Go 的 kq 在 {@code Consume} 返回 error 后<b>没有进程内重试循环</b>：消息只是没被确认，
 * 等消费者重启 / rebalance 后自然重投。
 * 若在 Java 侧加 {@code FixedBackOff} 做 N 次进程内重试，那是<b>新增行为</b>（改变重试节奏与日志量）。
 * 因此错误处理器由 {@link #noCommitErrorHandler()} 提供，只记日志、不 seek、不提交。
 *
 * <p>⚠️ 连带后果：位点不提交时，同分区<b>后续消息仍会被拉取处理</b>（Kafka 不会因一条失败而停），
 * 这与 Go 相同。落库侧有 inbox 幂等 + {@code last_operation_id} 单调守卫兜底，乱序重放不会造成状态错误。
 */
@Component
public class LikeUpdateKafkaListener {

    private static final Logger LOG = LoggerFactory.getLogger(LikeUpdateKafkaListener.class);

    private final ObjectMapper objectMapper;
    private final LikeMessageIngestService ingestService;
    private final LikeMqMetrics metrics;

    public LikeUpdateKafkaListener(ObjectMapper objectMapper,
                                   LikeMessageIngestService ingestService,
                                   LikeMqMetrics metrics) {
        this.objectMapper = objectMapper;
        this.ingestService = ingestService;
        this.metrics = metrics;
    }

    /**
     * 消费一条点赞事件。
     *
     * @param value 消息体（{@code KafkaLikeMsg} 的 JSON）
     */
    @KafkaListener(id = "like-update-consumer",
            // ⚠️ 用 ${} 占位符而不是 SpEL 的 #{@likeKafkaTopics.likeTopic}：
            //    SpEL 里的 BeanFactory 引用由 KafkaListenerAnnotationBeanPostProcessor 在
            //    **Bean 后处理阶段**求值，那时 ConfigurationPropertiesScan 注册的定义
            //    未必可见，实测直接抛 EL1058E / NoSuchBeanDefinitionException。
            //    ${} 走 Environment，不依赖注册顺序。两条路径读的是同一个 yml 键，值完全相同。
            //    同款修法见 article-rpc 的 ArticleReviewConsumer（它早已踩过并改掉，本模块漏了）。
            topics = "${like.kafka.like-topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record, String value) {
        // ---------- 分支 1：JSON 解析失败 ⇒ 丢弃（正常返回 ⇒ 提交位点）----------
        LikeEventContracts.KafkaLikeMsg msg;
        try {
            msg = objectMapper.readValue(value, LikeEventContracts.KafkaLikeMsg.class);
        } catch (Exception e) {
            metrics.consume("receive", "json_error");
            LOG.warn("drop like message with invalid json: err={}", e.getMessage());
            return;
        }

        // ---------- 分支 2：字段校验失败 ⇒ 丢弃 ----------
        if (!isValid(msg)) {
            metrics.consume("receive", "empty_msg_id");
            LOG.warn("drop like message with invalid fields: msgId={} userId={} state={}",
                    msg.msgId, msg.userId, msg.state);
            return;
        }

        LikeMessageIngestService.LikeProcessPayload payload = new LikeMessageIngestService.LikeProcessPayload();
        payload.msgId = msg.msgId;
        payload.userId = msg.userId;
        payload.targetType = msg.targetType;
        payload.targetId = msg.targetId;
        payload.authorId = msg.authorId;
        payload.action = msg.state;
        payload.occurredAt = msg.createdAt;
        payload.isFirst = msg.isFirst;

        // ---------- 分支 3：构造 outbox 失败 ⇒ 重投（抛异常 ⇒ 不提交）----------
        try {
            payload.firstLikeOutbox = ingestService.buildFirstLikeOutbox(payload);
        } catch (Exception e) {
            metrics.consume("receive", "outbox_error");
            LOG.warn("build first-like outbox failed, will retry: msgId={} err={}", msg.msgId, e.getMessage());
            throw e;
        }

        // ---------- 分支 4：DB 事务失败 ⇒ 重投 ----------
        // Go 注释写得很清楚：
        //   "Kafka can acknowledge only after inbox, record and domain fact commit."
        try {
            ingestService.processBatch(List.of(payload));
        } catch (Exception e) {
            metrics.consume("receive", "tx_error");
            LOG.warn("commit like message failed, will retry: msgId={} err={}", msg.msgId, e.getMessage());
            throw e;
        }

        metrics.consume("receive", "committed");
    }

    /**
     * 对应 Go {@code likemq.go:92-97} 的复合校验：
     *
     * <pre>
     * operationID, parseErr := strconv.ParseInt(msg.MsgID, 10, 64)
     * if parseErr != nil || operationID &lt;= 0 || msg.UserId &lt;= 0 ||
     *    msg.TargetType == "" || msg.TargetId == "" || msg.State &lt; 1 || msg.State &gt; 4 { … }
     * </pre>
     *
     * <p>⚠️ {@code msg_id} 必须能解析成<b>正整数</b>：它同时是 {@code last_operation_id}
     * 的比较基准与 {@code event_id} 的后缀，不能是 0 或非数字。
     */
    private static boolean isValid(LikeEventContracts.KafkaLikeMsg msg) {
        if (msg.msgId == null || msg.msgId.isBlank()) {
            return false;
        }
        final long operationId;
        try {
            operationId = Long.parseLong(msg.msgId.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        return operationId > 0
                && msg.userId > 0
                && msg.targetType != null && !msg.targetType.isEmpty()
                && msg.targetId != null && !msg.targetId.isEmpty()
                && msg.state >= 1 && msg.state <= 4;
    }

    /**
     * 构造「只记日志、不提交位点」的错误处理器。
     *
     * <p>Spring Kafka 的 {@code DefaultErrorHandler} 在重试耗尽后会调用 recoverer；
     * 默认 recoverer 会 seek 到当前位点，等于<b>把失败消息标记为已处理</b> ⇒ 丢消息。
     * 那是<b>新增的丢消息路径</b>，与 Go 不符。
     *
     * <p>这里传入 no-op recoverer：失败消息的位点永远不提交 ⇒ 重启 / rebalance 后重投。
     */
    public static CommonErrorHandler noCommitErrorHandler() {
        return new DefaultErrorHandler((record, exception) ->
                LOG.error("like message failed, offset NOT committed (redelivered after restart/rebalance): "
                                + "topic={} partition={} offset={} err={}",
                        record.topic(), record.partition(), record.offset(), exception.getMessage()));
    }
}