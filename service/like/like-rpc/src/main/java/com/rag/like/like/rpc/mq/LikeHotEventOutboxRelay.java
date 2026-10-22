package com.rag.like.like.rpc.mq;

import com.rag.like.like.rpc.domain.LikeOutboxEventEntity;
import com.rag.like.like.rpc.mapper.LikeOutboxEventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 热点事件发件箱中继 —— 1:1 对应 Go 端
 * {@code service/like/rpc/internal/mq/internal/mqs/like_outbox_sender.go} 的 {@code SendPending}
 * ＋ {@code internal/mq/mq.go} 里 1 秒一次的 ticker 循环。
 *
 * <p><b>为什么用 {@code @Scheduled} 而不是常驻线程</b>：Go 用
 * {@code service.NewServiceGroup()} 起一个 goroutine + {@code time.Ticker}。
 * Spring 的 {@code @Scheduled(fixedRate)} 由容器托管生命周期、异常不会打死循环，
 * 正好替代手写的 goroutine + select（那属于「自己实现调度器」）。
 *
 * <h2>⚠️ 三条不能改的行为</h2>
 * <ol>
 *   <li><b>捞取条件</b>：{@code status IN (0,2) AND retry_count < 3}，
 *       按 {@code created_at ASC} 取前 100 条。失败 3 次后<b>永久搁置</b>，不进死信、不告警。</li>
 *   <li><b>逐条独立</b>：一条推失败只 {@code MarkFailed} 并 {@code continue}，
 *       不影响同批其它事件（Go 明确写了 {@code continue}）。</li>
 *   <li><b>推送内容就是 payload 原文</b>：Go {@code Push(ctx, string(event.Payload))}，
 *       不是重新序列化 —— 保持与事务里写入的字节完全一致。</li>
 * </ol>
 */
@Component
public class LikeHotEventOutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(LikeHotEventOutboxRelay.class);

    /** Go {@code mq.go:39}：{@code s.sender.SendPending(s.ctx, 100)}。 */
    private static final int BATCH_LIMIT = 100;

    private final LikeOutboxEventMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.rag.like.like.rpc.config.LikeKafkaTopics topics;

    public LikeHotEventOutboxRelay(LikeOutboxEventMapper outboxMapper,
                                   KafkaTemplate<String, String> kafkaTemplate,
                                   com.rag.like.like.rpc.config.LikeKafkaTopics topics) {
        this.outboxMapper = outboxMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.topics = topics;
    }

    /**
     * 每秒扫一次发件箱。
     *
     * <p>⚠️ {@code fixedRate} 语义对齐 Go 的 {@code time.Ticker}：按<b>开始时刻</b>间隔触发，
     * 上一次没跑完不会并发触发（Spring 默认单线程调度器即满足）。
     * Kafka 客户端是异步的，所以这里的「发送成功」只代表<b>已交给客户端</b>，
     * 与 Go 的同步 {@code Push} 在这一点上有差异（见 {@code application.yml} 的 acks 注释）。
     */
    @Scheduled(fixedRateString = "${like.outbox-relay-interval:1000}")
    public void sendPending() {
        try {
            relayOnce();
        } catch (Exception e) {
            // Go: logger.LogBusinessErr(ctx, errmsg.ErrorKafkaPush, err) —— 不让循环停掉
            LOG.warn("like hot-event outbox relay iteration failed: {}", e.getMessage());
        }
    }

    /** 单次中继；抽出来便于测试直接调用，不依赖调度器。 */
    public void relayOnce() {
        List<LikeOutboxEventEntity> events = outboxMapper.selectPending(BATCH_LIMIT);
        if (events == null || events.isEmpty()) {
            return;
        }

        for (LikeOutboxEventEntity event : events) {
            try {
                // Go: HotEventPusher.Push(ctx, string(event.Payload)) —— **不带 key**，且用 payload 原文
                kafkaTemplate.send(topics.getHotEventTopic(), null, event.getPayload());
                outboxMapper.markSent(event.getEventId());
            } catch (Exception e) {
                // Go: 记日志 → MarkFailed → continue（不影响同批其它事件）
                LOG.warn("push hot event failed: eventId={} err={}", event.getEventId(), e.getMessage());
                try {
                    outboxMapper.markFailed(event.getEventId());
                } catch (Exception markErr) {
                    LOG.warn("mark hot event failed error: eventId={} err={}",
                            event.getEventId(), markErr.getMessage());
                }
            }
        }
    }
}