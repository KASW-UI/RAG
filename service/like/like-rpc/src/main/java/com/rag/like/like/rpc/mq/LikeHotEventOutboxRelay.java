package com.rag.like.like.rpc.mq;

import com.rag.like.like.rpc.domain.LikeOutboxEventEntity;
import com.rag.like.like.rpc.mapper.LikeOutboxEventMapper;
import com.rag.like.like.rpc.service.LikeKafkaPushKey;
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
     * Kafka 客户端是异步的，所以这里的「发送成功」只代表<b>已交给客户端</b>。
     *
     * <p>⚠️ 2026-11-01 修正错误断言 D：原文写「与 Go 的<b>同步</b> {@code Push} 在这一点上有差异」⇒ ⇒
     * <b>该前提已被证伪</b> —— Go 侧同样<b>不是</b>同步阻塞写：
     * <ul>
     *   <li>{@code github.com/zeromicro/go-queue@v1.2.2/kq/pusher.go:82}
     *       ⇒ {@code NewPusher} 默认挂 {@code executors.NewChunkExecutor}；</li>
     *   <li>同文件 {@code :140-142} ⇒ {@code if p.executor != nil { return p.executor.Add(msg, len(v)) }}
     *       ⇒ <b>只入队即返回</b>；</li>
     *   <li>{@code github.com/zeromicro/go-zero@v1.10.2/core/executors/chunkexecutor.go:45-52}
     *       ⇒ {@code ChunkExecutor.Add} 把任务塞进队列后 {@code return nil}；</li>
     *   <li>{@code .../core/executors/bulkexecutor.go:37} ＋ {@code vars.go:5}
     *       ⇒ {@code NewPeriodicalExecutor(flushInterval, …)}，{@code defaultFlushInterval = time.Second}
     *       ⇒ <b>每秒</b>后台批量刷一次（真正的 {@code WriteMessages} 在那里）。</li>
     * </ul>
     * ⇒ ⇒ <b>两侧都是「交出去就返回」</b> ⇒ 这里<b>不存在</b>「Go 同步 / Java 异步」的差异；
     * 真正要对齐的是 <b>acks</b>（是否等 broker 确认）⇒ 见 {@code application.yml} 的 acks 注释
     * （那里已写明 Go {@code kafka.Writer} 的默认 {@code RequiredAcks(1)} 与本模块 {@code acks: 1} 一致）。
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
                // Go: HotEventPusher.Push(ctx, string(event.Payload))
                //   —— 「用 payload 原文」这半句是对的；
                //   —— 「不带 key」这半句**是错的**（Go 的 Push 内部即 PushWithKey(UnixNano)，pusher.go:123-125）。
                final String pushKey = LikeKafkaPushKey.unixNano();
                kafkaTemplate.send(topics.getHotEventTopic(), pushKey, event.getPayload())
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                LOG.error("push hot event failed (async): eventId={} err={}",
                                        event.getEventId(), error.getMessage());
                            }
                        });
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