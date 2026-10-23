package com.rag.comment.comment.rpc.service;

import com.rag.common.communityfact.Wire;
import com.rag.comment.comment.rpc.domain.CommentDomainFactOutboxEntity;
import com.rag.comment.comment.rpc.mapper.CommentDomainFactOutboxMapper;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 单条事实投递 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/fact_delivery.go:53-127} 的 {@code DispatchFactOnce}。
 *
 * <h2>⚠️ 本方法<b>在一个事务里做网络 IO</b>，这是 Go 的刻意选择</h2>
 * <p>Go 的 {@code DispatchFactOnce} 手工 {@code Begin()} → 取件（{@code FOR UPDATE SKIP LOCKED}）
 * → <b>持锁期间</b>调 {@code sender.Send} → 落回执 → {@code Commit()}。
 * 方法注释说得很直白（fact_delivery.go:50-52）：
 * <pre>
 * // DispatchFactOnce keeps one source row locked until a matching DataCenter
 * // technical receipt is committed. Earlier versions of the same comment stream
 * // must be accepted first, so SKIP LOCKED cannot reorder a retract.
 * </pre>
 * ⇒ 「源行在拿到匹配技术回执之前不能被第二个投递进程领走」是<b>正确性要求</b>，不是性能选择。
 * 把 {@code sender.Send} 挪到事务外（先提交再发）会产生：
 * 进程在提交与发送之间崩溃 ⇒ 该行永远停在 pending，而下一个进程可能已经领走了它的事实流后继。
 * 事务内的行锁把这条路径彻底关掉。
 *
 * <p>⚠️ 代价是<b>事务持有时间包含 DC 的往返</b>（Go 侧用 {@code context.WithTimeout(10s)} 兜底，
 * 由 {@code common.Dispatcher} 施加，Java 侧沿用同一条路径）。这是等式的一部分，不做优化。
 *
 * <p><b>本方法默认不启用</b>：由 {@code bootstrap.CommentFactDispatchScheduler} 在
 * {@code comment.fact.dispatch-enabled=true} 时才驱动（见该类注释）。
 */
@Service
public class CommentFactDeliveryService {

    private static final Logger LOG = LoggerFactory.getLogger(CommentFactDeliveryService.class);

    private final CommentDomainFactOutboxMapper outboxMapper;
    private final CommentDeliveryValidator validator;

    public CommentFactDeliveryService(CommentDomainFactOutboxMapper outboxMapper,
                                      CommentDeliveryValidator validator) {
        this.outboxMapper = outboxMapper;
        this.validator = validator;
    }

    /**
     * 对应 Go {@code DispatchFactOnce}：领一条 → 校验 → 投递 → 落回执。
     *
     * <p><b>四条出口的方向必须保持</b>：
     * <ol>
     *   <li>outbox 空 ⇒ 返回 {@code false}（不抛错），调度器据此结束内层批次；</li>
     *   <li>冻结信封非法 ⇒ 置 <b>BLOCKED</b> 并抛
     *       {@link Wire#ERR_FROZEN_ENVELOPE_BLOCKED}（<b>不是</b>可重试的失败）；</li>
     *   <li>投递失败或回执不匹配 ⇒ 置 <b>FAILED</b> + {@code retry_count + 1} 并抛错（可重试）；</li>
     *   <li>成功 ⇒ 置 <b>ACCEPTED</b> 并写入 6 列技术元数据，返回 {@code true}。</li>
     * </ol>
     *
     * @return {@code true} = 已成功投递一条；{@code false} = outbox 里没有可投递的事实
     */
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public boolean dispatchOnce(io.opentelemetry.context.Context ctx, Wire.Sender sender, Logger logger)
            throws Exception {
        long started = System.nanoTime();
        if (sender == null || logger == null) {
            throw new IllegalStateException("comment fact dispatcher is not configured");
        }

        // --- ① 取件（FOR UPDATE SKIP LOCKED + 前序屏障，锁持有到本方法返回）
        CommentDomainFactOutboxEntity row = outboxMapper.selectNextDispatchableForUpdate();
        if (row == null) {
            return false;              // outbox 空 ⇒ 不是错误
        }

        // --- ② 校验冻结信封
        Wire.Event event;
        try {
            event = validator.decodeEnvelope(row.getDeliveryEnvelope());
        } catch (RuntimeException e) {
            event = null;
        }
        if (event == null || !validator.isValidDelivery(row, event)) {
            outboxMapper.markBlocked(row.getEventId());
            logger.warn("comment fact frozen envelope blocked: event={} event_id={} producer={} "
                            + "outcome={} error_code={} duration_ms={}",
                    "comment.fact_delivery.blocked", row.getEventId(), CommentDomainFactService.PRODUCER,
                    "manual_migration_required", "INVALID_FROZEN_ENVELOPE", millisSince(started));
            throw Wire.ERR_FROZEN_ENVELOPE_BLOCKED;
        }

        logger.info("comment fact delivery started: event={} event_id={} producer={} aggregate_version={}",
                "comment.fact_delivery.started", event.eventId, event.producer, event.aggregateVersion);
        Span span = Span.current();

        // --- ③ 投递 + 回执校验（持锁期间）
        Wire.TechnicalReceipt receipt = null;
        Exception sendErr = null;
        try {
            receipt = sender.send(ctx, event, rawBytes(row.getDeliveryEnvelope()));
        } catch (Exception e) {
            sendErr = e;
        }
        Wire.Result<Instant> validated = receipt == null
                ? new Wire.Result<>(null, false)
                : Wire.validateReceipt(event, receipt);

        if (sendErr != null || !validated.isOk()) {
            if (sendErr == null) {
                sendErr = new IllegalStateException(
                        "DataCenter technical receipt does not match comment event");
            }
            outboxMapper.markFailed(row.getEventId());
            logger.warn("comment fact delivery deferred: event={} event_id={} producer={} outcome={} "
                            + "error_code={} duration_ms={} retry_count={}",
                    "comment.fact_delivery.deferred", event.eventId, event.producer, "retryable",
                    "DELIVERY_ATTEMPT_FAILED", millisSince(started), row.getRetryCount() + 1);
            throw sendErr;
        }

        // --- ④ 落技术回执
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        outboxMapper.markAccepted(row.getEventId(), receipt.receiptId, receipt.inputHash,
                receipt.offset, now, now);
        logger.info("comment fact technical receipt committed: event={} event_id={} producer={} "
                        + "outcome={} duration_ms={} receipt_id={} offset={}",
                "comment.fact_delivery.accepted", event.eventId, event.producer, "succeeded",
                millisSince(started), receipt.receiptId, receipt.offset);
        if (span != null) {
            span.setAttribute("comment.fact.event_id", event.eventId);
            span.setAttribute("comment.fact.aggregate_version", event.aggregateVersion);
        }
        return true;
    }

    private static byte[] rawBytes(String envelope) {
        return envelope.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}