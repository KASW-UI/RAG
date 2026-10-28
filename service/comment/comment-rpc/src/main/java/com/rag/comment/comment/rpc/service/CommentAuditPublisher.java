package com.rag.comment.comment.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.comment.comment.rpc.config.CommentRpcProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 评论审计消息生产端 —— 对应 Go 端 {@code service_context.go:28}
 * 的 {@code kq.NewPusher(c.KqPusherConf.Brokers, c.KqPusherConf.Topic)}。
 *
 * <h2>⚠️ 同步还是异步：这是本域的一条 L1 行为，必须同步</h2>
 * <p>Go 的 {@code kq.Pusher.Push} 是<b>同步阻塞</b>写，而
 * {@code create_comment_logic.go:126-132} 把它当作<b>会失败</b>的操作处理：
 * <pre>
 * if err = l.svcCtx.KqPusherClient.Push(ctx, string(msgBytes)); err != nil {
 *     result = "sys_fail"
 *     err = errmsg.NewGrpcErr(errmsg.ErrorKafkaPush, "Kafka推送失败")
 *     return nil, err
 * }
 * </pre>
 * ⇒ <b>Kafka 写失败 ⇒ CreateComment 返回 5009</b>，且<b>响应时间包含 Kafka 往返</b>。
 *
 * <p>Java 的 {@code KafkaTemplate.send} 返回 {@code CompletableFuture}，是异步的。
 * 若不等结果就返回，就会变成「Kafka 挂了但评论创建照样返回成功」——
 * 客户端以为评论发出去了，实际审计链路从未收到消息（评论<b>不会落库</b>，
 * 因为落库发生在 AuditConsumer 里）⇒ 一条<b>永远看不到</b>的评论。
 * 这是<b>方向相反的漂移</b>，比慢一点严重得多。
 *
 * <p>⇒ 本类<b>必须</b> {@code future.get(timeout)}。
 *
 * <p>⚠️ 与 like-rpc 的选择<b>刻意不同</b>：like-rpc 选了 {@code acks=1 + 异步 + 失败回调日志}，
 * 理由是「Go 侧失败也只打日志不返回错误」（likeactionlogic.go 那条链路）。
 * comment 域的 Go 代码<b>真的把错误返回给了客户端</b> ⇒ 不能照抄 like 的选择。
 * 这是「复用模式」与「复用结论」的区别。
 */
@Service
public class CommentAuditPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(CommentAuditPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final CommentRpcProperties props;
    private final ObjectMapper objectMapper;

    public CommentAuditPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                 CommentRpcProperties props,
                                 ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * 推送一条待审计的评论消息。
     *
     * <p>对应 Go {@code json.Marshal(msg)} + {@code Push(ctx, string(msgBytes))}。
     *
     * @throws SerializationFailed 序列化失败（对应 Go 的 {@code ErrorJsonMarshal} 5007）
     * @throws PublishFailed        发送失败或超时（对应 Go 的 {@code ErrorKafkaPush} 5009）
     */
    public void publish(CommentAuditMessage msg) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(msg);
        } catch (Exception e) {
            LOG.error("marshal comment audit message failed: {}", e.getMessage(), e);
            throw new SerializationFailed("Json序列化失败", e);
        }

        String topic = props.getKafka().getAuditTopic();
        try {
            // ⚠️ 同步等待：这是本域 L1 语义的载体，理由见类级 javadoc。
            // 不用 key（Go 侧 kq.Pusher.Push 也不设 key），分区由 broker 按 round-robin 决定。
            kafkaTemplate.send(topic, payload)
                    .get(props.getKafka().getPublishTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.error("kafka push timeout after {}ms, topic={}", props.getKafka().getPublishTimeout().toMillis(), topic, e);
            throw new PublishFailed("Kafka推送失败", e);
        } catch (InterruptedException e) {
            // Go 侧 ctx 被取消时是同步返回错误；Java 侧对应的是线程被中断。
            Thread.currentThread().interrupt();
            throw new PublishFailed("Kafka推送失败", e);
        } catch (Exception e) {
            LOG.error("kafka push failed, topic={}: {}", topic, e.getMessage(), e);
            throw new PublishFailed("Kafka推送失败", e);
        }
        LOG.debug("comment audit message pushed: topic={} comment_id={}", topic, msg.getCommentId());
    }

    /** 对应 Go {@code create_comment_logic.go:122} 的 {@code ErrorJsonMarshal}(5007)。 */
    public static class SerializationFailed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SerializationFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /** 对应 Go {@code create_comment_logic.go:130} 的 {@code ErrorKafkaPush}(5009)。 */
    public static class PublishFailed extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public PublishFailed(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}