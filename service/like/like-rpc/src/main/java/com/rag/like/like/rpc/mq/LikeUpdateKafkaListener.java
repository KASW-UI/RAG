package com.rag.like.like.rpc.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.observability.MqConsumer;
import io.opentelemetry.context.Context;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.FixedBackOff;

import com.rag.like.like.rpc.metrics.LikeMqMetrics;
import com.rag.like.like.rpc.service.LikeMessageIngestService;
import com.rag.like.like.rpc.service.LikeSpans;

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
 *   <tr><td>构造 outbox 失败（雪花 ID）</td><td>{@code err}</td><td><b>丢弃</b>（<b>不是重投</b>，见下）</td><td>{@code receive/outbox_error}</td></tr>
 *   <tr><td>DB 事务失败</td><td>{@code err}</td><td><b>丢弃</b>（<b>不是重投</b>，见下）</td><td>{@code receive/tx_error}</td></tr>
 *   <tr><td>成功</td><td>{@code nil}</td><td>提交位点</td><td>{@code receive/committed}</td></tr>
 * </table>
 *
 * <p>⚠️ <b>「返回 err ⇒ 重投」是错的</b>，Go 侧返回 err 后位点<b>照样推进</b>。
 * 真实机制见下方「失败消息的最终归宿」一节（含 {@code ForceCommit} 的消费点）。
 * ⚠️ 由此得到一个容易踩空的结论：<b>五条出口的最终归宿都是「丢弃」</b>，
 * 区别只在<b>指标标签</b>与<b>日志级别</b>上。落库侧的 inbox 幂等 + {@code last_operation_id}
 * 单调守卫只在<b>重复投递</b>时生效，管不了"消息已被丢弃"。
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
 * <h2>⚠️ 失败消息的最终归宿：<b>丢弃</b>，且<b>继续处理后续消息</b>（2026-10-23 复核修正）</h2>
 *
 * <p>本段此前写的是「位点不提交、等重启/rebalance 后重投」，<b>那是错的</b>。
 * 真源侧的实际控制流（{@code go-queue@v1.2.2/kq/queue.go:230-243}）：
 * <pre>
 * if err := q.consumeOne(ctx, string(msg.Key), string(msg.Value)); err != nil {
 *     if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }   // 默认实现只 logc.Errorf
 *     if !q.c.ForceCommit { continue }                            // ← ForceCommit 默认 true ⇒ 不 continue
 * }
 * if err := q.consumer.CommitMessages(ctx, msg); err != nil { … } // ← 落到这里 ⇒ **照样提交位点**
 * </pre>
 * 配合 {@code kq/config.go:24} {@code ForceCommit bool `json:",default=true"`}
 * 与 {@code service/like/rpc/internal/mq/etc/mq.yaml}（<b>没有覆盖该字段</b>），
 * 得到确定结论：<b>{@code Consume} 返回 error ⇒ 位点照样推进 ⇒ 该消息永久丢弃、不会重投</b>，
 * 且循环<b>继续处理下一条</b>。
 *
 * <p>⚠️ 顺带记录一处<b>真源自身的意图与实现不符</b>：{@code internal/model/like.go:164-179}
 * 的 inbox 幂等第②层写「{@code payload_hash} 不同 ⇒ <b>报错重投</b>（不跳过）」，
 * 但按上面的控制流，"报错"最终会被 {@code ForceCommit=true} 吃掉并提交位点 ⇒ <b>重投从未发生</b>。
 * 这是 Go 侧的缺陷，<b>不是 RAG 要对齐的设计</b>；此处只记录，不在 RAG 侧"顺手修正"（§5.4）。
 *
 * <h3>Java 侧必须显式关掉进程内重试</h3>
 *
 * <p>{@code DefaultErrorHandler} 的单参构造 {@code new DefaultErrorHandler(recoverer)}
 * <b>内部就是 {@code FixedBackOff(0, 9)}</b>
 * （消费点：{@code kq} 无关，Spring 侧
 * {@code DefaultErrorHandler.java:81-83} {@code this(recoverer, SeekUtils.DEFAULT_BACK_OFF)}；
 * {@code SeekUtils.java:57,63} {@code DEFAULT_MAX_FAILURES = 10} ⇒ {@code DEFAULT_BACK_OFF = FixedBackOff(0, 9)}）。
 * ⇒ 失败消息会<b>真的被重新 poll 并重跑 listener 共 9 次</b>（每次 seek 回原位，
 * {@code SeekUtils.seekOrRecover} 抛 {@code RecordInRetryException} 让容器重来），
 * 而 Go 侧是 <b>0 次</b> ⇒ 这是<b>新增的重试行为</b>。
 *
 * <p>因此 {@link #goAlignedErrorHandler()} <b>显式传 {@code FixedBackOff(0, 0)}</b>：
 * 第一次失败即 {@code nextBackOff == STOP}
 * ⇒ {@code FailedRecordTracker.recovered} 走 else 分支（{@code :185-195}）⇒ 调 recoverer 后
 * <b>返回 {@code true}</b> ⇒ {@code SeekUtils.doSeeks} <b>不 seek</b>
 * ⇒ 容器继续处理后续消息、位点随之推进 ⇒ <b>丢弃 + 继续</b>，与 Go 逐条一致。
 *
 * <p>⚠️ 不要把 {@code recovered} 的返回值与「位点是否提交」混为一谈：
 * {@code seekOrRecover} 里 {@code commitRecovered} 只在 {@code AckMode.MANUAL_IMMEDIABLE} 下生效，
 * 本模块是 {@code RECORD}；真正的位点推进来自「跳过该条 ⇒ 后续记录正常处理 ⇒ 提交更大的 offset」。
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
    /**
     * 消费一条点赞事件 —— <b>Go {@code likemq.go:83-84} 的 {@code observability.TraceConsumer} 包装</b>。
     *
     * <p>Go 出处（逐字）：
     * <pre>
     *   func (l *LikeUpdateService) Consume(ctx context.Context, key, val string) error {
     *       return observability.TraceConsumer(ctx, "like-mq", "LikeUpdateService.Consume",
     *           likeMessageAttrs("like-topic", key), func(ctx context.Context) error { … })
     *   }
     * </pre>
     * ⇒ Go 侧整个消费体被包在一个 <b>CONSUMER 类型的 span</b> 里，span 上带 {@code messaging.*} 4 个属性
     * （{@code likemq.go:137-143}）。RAG 侧此前是<b>裸 listener</b> ⇒ 没有任何 consumer span
     * ⇒ {@code messaging.*} 无处可落。
     *
     * <p>⚠️ <b>错误契约的方向差异（本次改动的关键点）</b>：
     * <ul>
     *   <li>Go：回调<b>返回 error</b> ⇒ {@code TraceConsumer}（{@code mq.go:32-39}）把它标成
     *       {@code MarkSystemError} ⇒ span 上留 {@code sea.error} / {@code sea.error_kind} 等标记；</li>
     *   <li>Java：listener <b>抛异常</b>（Spring Kafka：抛异常 ⇒ 不提交位点）。</li>
     * </ul>
     * ⇒ 本方法把消费体改成<b>返回 Throwable</b>（让 {@code TraceConsumer} 完成 Go 侧那一步「标成
     * system error」），再在 <b>span 之外</b>把<b>同一个异常实例</b>抛出去
     * ⇒ <b>Java 契约（抛异常 ⇒ 不提交位点）一字未改</b>，同时 span 的错误标记与 Go 一致。
     */
    public void onMessage(ConsumerRecord<String, String> record, String value) {
        Throwable failure = MqConsumer.traceConsumer(
                Context.current(),
                LikeSpans.MQ_TRACER_NAME,
                LikeSpans.MQ_SPAN_NAME,
                LikeSpans.messagingAttributes(record.key()),
                ctx -> consume(value));
        if (failure == null) {
            return;
        }
        // ⚠️ 必须是**同一个异常实例**：Spring Kafka 的错误处理器靠它做日志与「丢弃 + 继续」判定。
        if (failure instanceof RuntimeException re) {
            throw re;
        }
        if (failure instanceof Error err) {
            throw err;
        }
        throw new IllegalStateException(failure);
    }

    /**
     * 消费体 —— 对应 Go {@code TraceConsumer} 的回调体。
     *
     * @return {@code null} = 正常（含 Go 侧的两条 drop 分支）；非 null = 失败（Go 侧对应 {@code return err}）
     */
    private Throwable consume(String value) {
        // ---------- 分支 1：JSON 解析失败 ⇒ 丢弃（正常返回 ⇒ 提交位点）----------
        LikeEventContracts.KafkaLikeMsg msg;
        try {
            msg = objectMapper.readValue(value, LikeEventContracts.KafkaLikeMsg.class);
        } catch (Exception e) {
            metrics.consume("receive", "json_error");
            LOG.warn("drop like message with invalid json: err={}", e.getMessage());
            return null;
        }

        // ---------- 分支 2：字段校验失败 ⇒ 丢弃 ----------
        if (!isValid(msg)) {
            metrics.consume("receive", "empty_msg_id");
            LOG.warn("drop like message with invalid fields: msgId={} userId={} state={}",
                    msg.msgId, msg.userId, msg.state);
            return null;
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

        // ---------- 分支 3：构造 outbox 失败 ⇒ 失败（Go 侧对应分支也是 return err）----------
        // ⚠️ Go 侧这一条**不是重投**：ForceCommit=true ⇒ 位点照样推进 ⇒ 消息丢弃。
        //    失败只是让本条**不计入 committed 指标**，语义上仍走 recoverer 后丢弃。
        try {
            payload.firstLikeOutbox = ingestService.buildFirstLikeOutbox(payload);
        } catch (Exception e) {
            metrics.consume("receive", "outbox_error");
            LOG.warn("build first-like outbox failed, will be dropped: msgId={} err={}", msg.msgId, e.getMessage());
            return e;
        }

        // ---------- 分支 4：DB 事务失败 ⇒ 失败（同上，Go 侧亦为丢弃）----------
        // Go 注释写得很清楚：
        //   "Kafka can acknowledge only after inbox, record and domain fact commit."
        try {
            ingestService.processBatch(List.of(payload));
        } catch (Exception e) {
            metrics.consume("receive", "tx_error");
            LOG.warn("commit like message failed, will be dropped: msgId={} err={}", msg.msgId, e.getMessage());
            return e;
        }

        metrics.consume("receive", "committed");
        return null;
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
            // ⚠️ 2026-10-23 修正（与 DRIFT-LIKE-010 同型）：Go 侧是
            //    strconv.ParseInt(msg.MsgID, 10, 64) —— **不忽略首尾空白**，
            //    故 " 123 " 在 Go 侧 parseErr != nil ⇒ 整条被丢弃。
            //    此前这里 parseLong(msg.msgId.trim()) 会让带空白的消息**被接受**，与 Go 相反。
            operationId = Long.parseLong(msg.msgId);
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
     * 构造与 Go 逐条一致的错误处理器：<b>0 次进程内重试 + 失败即丢弃 + 继续处理后续消息</b>。
     *
     * <p>对应真源 {@code kq/queue.go:230-243}：{@code Consume} 返回 error ⇒ 默认 errorHandler 只
     * {@code logc.Errorf}（{@code kq/queue.go:362-366}）⇒ {@code ForceCommit=true}（{@code kq/config.go:24}，
     * yaml 未覆盖）⇒ <b>提交位点</b> ⇒ 该消息丢弃，循环继续下一条。
     *
     * <p><b>{@code new FixedBackOff(0, 0)} 是本方法唯一的关键参数</b>：
     * {@link DefaultErrorHandler} 的单参构造默认吃 {@code SeekUtils.DEFAULT_BACK_OFF}
     * （= {@code FixedBackOff(0, 9)}），会让失败消息被重新 poll 并重跑 listener 共 9 次
     * —— Go 侧是 0 次。显式给 0 后，第一次失败即 {@code nextBackOff == STOP}
     * ⇒ {@code FailedRecordTracker.recovered}（{@code :185-195}）返回 {@code true}
     * ⇒ {@code SeekUtils.doSeeks} 不 seek ⇒ 后续消息继续处理、位点推进 ⇒ <b>丢弃 + 继续</b>。
     *
     * <p>recoverer 只负责<b>记日志</b>（对齐 Go 默认 errorHandler 的
     * {@code logc.Errorf(ctx, "consume: %s, error: %v", …)}），<b>不改变控制流</b>。
     */
    public static CommonErrorHandler goAlignedErrorHandler() {
        return new DefaultErrorHandler(
                (record, exception) ->
                        LOG.error("like message dropped (offset advances, matches Go ForceCommit=true): "
                                        + "topic={} partition={} offset={} err={}",
                                record.topic(), record.partition(), record.offset(), exception.getMessage()),
                new FixedBackOff(0, 0));
    }
}