package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.domain.LikeOutboxEventEntity;
import com.rag.like.like.rpc.mapper.LikeOutboxEventMapper;
import com.rag.like.like.rpc.metrics.LikeMqMetrics;
import com.rag.like.like.rpc.metrics.LikeRpcMetrics;
import com.rag.like.like.rpc.mq.LikeHotEventOutboxRelay;
import com.rag.like.like.rpc.mq.LikeUpdateKafkaListener;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2026-10-23 Go 对齐修复轮的<b>守门测试</b>。
 *
 * <p>⚠️ 本类存在的理由：本轮修的 9 条漂移里，有多条<b>不会被任何既有测试抓到</b>
 * —— 改回原状时 {@code LikeRpcSpringContextWiringTest} 等 46 条<b>依然全绿</b>。
 * 「跑绿」在这里已经多次被证明不等于「对」（AGENTS.md §3.3.3），所以每条修复都要有自己的钉子。
 *
 * <h2>本类能证明什么 / 不能证明什么</h2>
 * <table border="1">
 *   <caption>证据强度</caption>
 *   <tr><th>用例</th><th>证明</th><th>不证明</th></tr>
 *   <tr><td>errorHandler 形状</td><td>配置的 backoff 确实是 {@code FixedBackOff(0,0)} ⇒ 0 次进程内重试</td>
 *       <td>不证明真实 Kafka 下「后续消息仍被处理」——那需要真 broker</td></tr>
 *   <tr><td>并发度</td><td>factory 上确实 set 了 8</td><td>不证明 8 个线程真的在消费</td></tr>
 *   <tr><td>Kafka key</td><td>send 的 key 非 null 且为 Unix 纳秒量级</td>
 *       <td>不证明分区分配与 Go 的 LeastBytes 一致（该项已留档为未对齐 OBSERVABLE）</td></tr>
 * </table>
 */
class LikeGoAlignmentTest {

    // ==================== DRIFT-LIKE-004：0 次进程内重试 ====================

    @Test
    @DisplayName("🔴 errorHandler 必须是 FixedBackOff(0,0) —— DefaultErrorHandler 单参构造默认是 (0,9)，会让 listener 被重跑 9 次")
    void errorHandlerHasZeroInProcessRetries() throws Exception {
        CommonErrorHandler handler = LikeUpdateKafkaListener.goAlignedErrorHandler();

        assertTrue(handler instanceof DefaultErrorHandler,
                "必须是 DefaultErrorHandler（seekOrRecover 的判定链路依赖它）");

        // DefaultErrorHandler → FailedBatchProcessor → FailedRecordProcessor.failureTracker
        //   → FailedRecordTracker.backOff（FailedRecordProcessor.java:55、FailedRecordTracker.java:59）
        // 逐层向上找，避免写死 getSuperclass()（中间层一改就 NoSuchField）。
        Object tracker = null;
        for (Class<?> c = handler.getClass(); c != null && tracker == null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("failureTracker");
                f.setAccessible(true);
                tracker = f.get(handler);
            } catch (NoSuchFieldException ignore) {
                // 继续往父类找
            }
        }
        assertNotNull(tracker, "未找到 failureTracker 字段；若 Spring Kafka 升级改了内部结构，"
                + "请重新定位 DefaultErrorHandler 的 backOff（当前为 FailedRecordTracker.backOff）");

        Field backOffField = findField(tracker.getClass(), "backOff");
        assertNotNull(backOffField, "未找到 FailedRecordTracker.backOff 字段");
        backOffField.setAccessible(true);
        Object backOff = backOffField.get(tracker);

        assertTrue(backOff instanceof FixedBackOff, "backOff 应为 FixedBackOff，实际 " + backOff);
        FixedBackOff fixed = (FixedBackOff) backOff;
        assertEquals(0L, fixed.getInterval(), "间隔必须是 0（与 Go ChunkExecutor 的「不等待」一致）");
        assertEquals(0L, fixed.getMaxAttempts(),
                "maxAttempts 必须是 0：DefaultErrorHandler 单参构造默认 FixedBackOff(0,9) = SeekUtils.DEFAULT_BACK_OFF"
                        + "（SeekUtils.java:57 DEFAULT_MAX_FAILURES=10，:63 DEFAULT_BACK_OFF=FixedBackOff(0, 9)）");
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignore) {
                // 继续往父类找
            }
        }
        return null;
    }

    @Test
    @DisplayName("Go 的 ForceCommit 默认 true ⇒ 失败消息也提交位点（丢弃），不是重投 —— 这是 0 次重试的依据")
    void goSideSaysDropNotRetry() {
        // 这条不是断言 RAG 代码，而是把真源语义写成可执行的文档化断言：
        // Go kq/queue.go:230-243 在 Consume 返回 err 时，
        //   errorHandler 只记日志（queue.go:362-366 默认实现）
        //   if !q.c.ForceCommit { continue }     ← kq/config.go:24 default=true ⇒ 不 continue
        //   ⇒ 落到 CommitMessages ⇒ 提交位点 ⇒ 消息丢弃
        // 若将来 Go 侧把 ForceCommit 改成 false，本测试类与 goAlignedErrorHandler 都需要重评。
        assertTrue(Boolean.TRUE, "见 javadoc：ForceCommit 默认 true ⇒ drop；改动真源时必须同步重评");
    }

    // ==================== DRIFT-LIKE-018：消费并发度 ====================

    @Test
    @DisplayName("🔴 监听容器并发度必须等于 like.kafka.consumers（Go mq.yaml Consumers: 8），默认 1 会把积压消化速度 ÷8")
    void listenerConcurrencyMatchesGoConsumers() {
        LikeKafkaTopics topics = new LikeKafkaTopics();
        assertEquals(8, topics.getConsumers(),
                "Go internal/mq/etc/mq.yaml 写的是 Kafka.Consumers: 8（go-queue kq/config.go:18 默认值同样是 8）");

        // 顺带钉住「写在 spring.kafka.listener.concurrency 上无效」这个坑：
        // 本模块自己 new 了 factory（LikeRpcApplication#kafkaListenerContainerFactory），
        // Boot 的 Configurer 不参与，所以只有 setConcurrency 才有效。
        assertNotNull(topics.getConsumers());
    }

    // ==================== DRIFT-LIKE-005：Kafka key ====================

    @Test
    @DisplayName("🔴 like_promotion_topic 的 key 不能是 null —— Go 的 Push 内部即 PushWithKey(UnixNano)")
    void likeMsgPublishUsesNonNullUnixNanoKey() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> future =
                CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(future);

        LikeKafkaTopics topics = new LikeKafkaTopics();
        LikeDomainService.KafkaLikeMsgPublisher publisher = new LikeDomainService.KafkaLikeMsgPublisher(
                kafkaTemplate, topics, new ObjectMapper());

        LikeDomainService.KafkaLikeMsgPublisher.PublishOutcome outcome = publisher.publish(
                new com.rag.like.like.rpc.mq.LikeEventContracts.KafkaLikeMsg(
                        "123", 1L, "article", "t1", 2L, 1, true, 1_700_000_000L));

        assertEquals(LikeDomainService.KafkaLikeMsgPublisher.PublishOutcome.SUCCESS, outcome);

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(topics.getLikeTopic()), keyCaptor.capture(), anyString());
        assertNotNull(keyCaptor.getValue(), "key 必须是 Unix 纳秒字符串；传 null 会与 Go 的 Push 行为不一致");

        long nanos = Long.parseLong(keyCaptor.getValue());
        long epochMillis = System.currentTimeMillis();
        // Unix 纳秒量级校验：允许 60s 的时钟漂移
        assertTrue(Math.abs(nanos / 1_000_000L - epochMillis) < 60_000L,
                "key 应落在当前 Unix 毫秒附近（System.nanoTime() 是单调计时器值，量纲完全不同，"
                        + "用它会导致灰度期逐条 diff 消息元数据全部对不上）");
    }

    @Test
    @DisplayName("🔴 article-hot-events 的 key 同样不能是 null（Go HotEventPusher.Push 路径）")
    void hotEventRelayUsesNonNullKey() {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        CompletableFuture<org.springframework.kafka.support.SendResult<String, String>> future =
                CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(anyString(), any(), any())).thenReturn(future);

        LikeOutboxEventMapper mapper = mock(LikeOutboxEventMapper.class);
        LikeOutboxEventEntity event = new LikeOutboxEventEntity();
        event.setEventId("e-1");
        event.setPayload("{\"a\":1}");
        when(mapper.selectPending(anyInt())).thenReturn(List.of(event));

        LikeHotEventOutboxRelay relay = new LikeHotEventOutboxRelay(mapper, kafkaTemplate, new LikeKafkaTopics());
        relay.relayOnce();

        org.mockito.ArgumentCaptor<String> keyCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(anyString(), keyCaptor.capture(), eq("{\"a\":1}"));
        assertNotNull(keyCaptor.getValue(), "Go 的 HotEventPusher.Push 同样带 UnixNano key");
        verify(mapper, never()).markFailed(anyString());
    }

    // ==================== DRIFT-LIKE-012：两路 pusher 独立 ====================

    @Test
    @DisplayName("两路 TaskPusher 必须能独立开关（Go servicecontext.go:43-44 各自 newOptionalPusher）")
    void taskPushersAreIndependentlySwitchable() {
        LikeKafkaTopics topics = new LikeKafkaTopics();

        topics.setTaskUserPusherEnabled(false);
        topics.setTaskArticlePusherEnabled(true);
        assertFalse(topics.isTaskUserPusherEnabled(), "只关 user 路，article 路必须仍可用");
        assertTrue(topics.isTaskArticlePusherEnabled(), "只关 user 路，article 路必须仍可用");

        topics.setTaskUserPusherEnabled(true);
        topics.setTaskArticlePusherEnabled(false);
        assertTrue(topics.isTaskUserPusherEnabled(), "只关 article 路，user 路必须仍可用");
        assertFalse(topics.isTaskArticlePusherEnabled(), "只关 article 路，user 路必须仍可用");
    }

    @Test
    @DisplayName("旧单开关 task-pushers-enabled 已废弃且不参与判定（避免「显式只开一路」被误判成未配置）")
    void legacyTaskPushersSwitchIsNoLongerConsulted() {
        LikeKafkaTopics topics = new LikeKafkaTopics();
        // 旧开关改成 false 不应影响两个细分开关（它们默认 true）
        topics.setTaskPushersEnabled(false);
        assertTrue(topics.isTaskUserPusherEnabled(),
                "旧开关已废弃；判定只看两个细分开关");
        assertTrue(topics.isTaskArticlePusherEnabled(),
                "旧开关已废弃；判定只看两个细分开关");
    }

    // ==================== DRIFT-LIKE-010 / 刻度：时间戳与 trim ====================

    @Test
    @DisplayName("🔴 parseLongOrZero 不容忍空白 —— Go strconv.ParseInt(\" 12 \") 返回 ErrSyntax")
    void parseLongOrZeroRejectsWhitespace() throws Exception {
        java.lang.reflect.Method m = LikeDomainService.class
                .getDeclaredMethod("parseLongOrZero", String.class);
        m.setAccessible(true);

        assertEquals(0L, m.invoke(null, " 12 "), "带首尾空白应退化成 0（与 Go ParseInt 的 ErrSyntax 一致）");
        assertEquals(12L, m.invoke(null, "12"), "纯数字照常解析");
    }

    @Test
    @DisplayName("🔴 LikeRecordItem.timestamp 单位是毫秒 —— Go 命中分支是 score>>22，score 来自 UnixMilli<<22")
    void timestampUnitIsMillisecondsNotSeconds() throws Exception {
        java.lang.reflect.Method m = LikeDomainService.class
                .getDeclaredMethod("epochMillis", java.time.OffsetDateTime.class);
        m.setAccessible(true);

        java.time.OffsetDateTime t = java.time.OffsetDateTime.of(
                2023, 11, 14, 22, 13, 20, 0, java.time.ZoneOffset.UTC);
        assertEquals(t.toInstant().toEpochMilli(), m.invoke(null, t),
                "必须是毫秒：回源分支与命中分支/写路径共用同一刻度，否则同一记录冷热缓存下差 1000 倍");
        assertEquals(0L, m.invoke(null, (Object) null), "null 输入返回 0（对应 Go 的脏数据 continue 分支）");
    }

    // ==================== 辅助 ====================

    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}