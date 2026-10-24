package com.rag.task.task.rpc.mq;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 两个 sink 的<b>消费侧与聚合侧</b>行为测试（不落库）。
 *
 * <p>Go 出处：{@code internal/sink/like_sink.go:81-108}（Consume + delta 合并）
 * 与 {@code internal/sink/article_sink.go:93-124}（Consume + 合并）。
 *
 * <h2>本类只断言「不进 delta 的那些分支」与「delta 的合并规则」</h2>
 * <p>落库侧（Redis pipeline / PG upsert）由 {@code TaskProgressSqlParityTest}
 * 接真 PG + 真 Redis 验证；这里用 mock 把分支钉死。
 */
class TaskSinkConsumeTest {

    private static <V> ConsumerRecord<String, V> record(String key, V value) {
        return new ConsumerRecord<>("t", 0, 0L, key, value);
    }

    // ==================== 用户点赞 sink ====================

    private UserLikeProgressSink userSink() {
        return new UserLikeProgressSink(
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                mock(com.rag.task.task.rpc.service.TaskProgressWriter.class),
                mock(com.rag.task.task.rpc.mapper.UserLikeCountMapper.class),
                mock(com.rag.task.task.rpc.service.TaskRewardPublisher.class),
                new com.rag.task.task.rpc.config.TaskSinkProperties());
    }

    @Test
    @DisplayName("解析失败的 key ⇒ 抛异常 ⇒ 位点不提交（Go: return err ⇒ kq 不 CommitMessages）")
    void userSinkUnparsableKeyThrows() {
        UserLikeProgressSink sink = userSink();

        assertThrows(RuntimeException.class, () -> sink.onMessage(record("not-a-number", "5")));
    }

    @Test
    @DisplayName("uid <= 0 ⇒ 正常返回（Go: return nil ⇒ 照常提交位点，丢弃该条）")
    void userSinkNonPositiveUidIsDropped() {
        UserLikeProgressSink sink = userSink();

        sink.onMessage(record("0", "5"));       // 不抛
        sink.onMessage(record("-1", "5"));      // 不抛
        assertEquals(0, sink.snapshotDelta().size(), "这两条都不该进 delta");
    }

    @Test
    @DisplayName("value 为空 / 非法 ⇒ total 回落 1（Go: total := 1 之后解析失败不赋值）")
    void userSinkUnparsableValueFallsBackToOne() {
        UserLikeProgressSink sink = userSink();

        sink.onMessage(record("100", ""));            // Go: val == "" ⇒ total 保持 1
        sink.onMessage(record("200", "not-a-number")); // Go: 解析失败 ⇒ total 保持 1

        assertEquals(1L, sink.snapshotDelta().get(100L));
        assertEquals(1L, sink.snapshotDelta().get(200L),
                "★ 必须是 1 而不是丢弃或 0 —— 这是 like_sink.go:91-96 最易写错处");
    }

    @Test
    @DisplayName("total <= 0 ⇒ 丢弃（Go: if total <= 0 { return nil }）")
    void userSinkNonPositiveTotalIsDropped() {
        UserLikeProgressSink sink = userSink();

        sink.onMessage(record("100", "0"));
        sink.onMessage(record("200", "-3"));

        assertEquals(0, sink.snapshotDelta().size());
    }

    @Test
    @DisplayName("🔴 delta 取 max：乱序消息不会把计数写小（Go: if total > current）")
    void userSinkDeltaKeepsMaximum() {
        UserLikeProgressSink sink = userSink();

        sink.onMessage(record("100", "7"));
        sink.onMessage(record("100", "3"));   // 乱序的更小值
        sink.onMessage(record("100", "5"));
        sink.onMessage(record("100", "9"));

        assertEquals(9L, sink.snapshotDelta().get(100L));
    }

    @Test
    @DisplayName("不同 uid 各自聚合")
    void userSinkAggregatesPerUid() {
        UserLikeProgressSink sink = userSink();

        sink.onMessage(record("100", "2"));
        sink.onMessage(record("200", "4"));
        sink.onMessage(record("100", "3"));

        assertEquals(3L, sink.snapshotDelta().get(100L));
        assertEquals(4L, sink.snapshotDelta().get(200L));
        assertEquals(2, sink.snapshotDelta().size());
    }

    // ==================== 文章 sink ====================

    private ArticleLikeProgressSink articleSink(KafkaTemplate<String, String> kafka,
                                               com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new ArticleLikeProgressSink(
                mock(org.springframework.data.redis.core.StringRedisTemplate.class),
                mock(com.rag.task.task.rpc.service.TaskProgressWriter.class),
                mock(com.rag.task.task.rpc.service.TaskRewardPublisher.class),
                new com.rag.task.task.rpc.config.TaskSinkProperties(),
                mapper);
    }

    @Test
    @DisplayName("key 为空 ⇒ 正常返回（Go: if key == \"\" { return nil }）")
    void articleSinkEmptyKeyIsDropped() {
        ArticleLikeProgressSink sink = articleSink(mock(KafkaTemplate.class), mapper());

        sink.onMessage(record("", "{\"user_id\":\"5\",\"cur\":2}"));
        assertEquals(0, sink.snapshotDelta().size());
    }

    @Test
    @DisplayName("key 解析失败 ⇒ 抛异常（Go: return err ⇒ 不提交位点）")
    void articleSinkUnparsableKeyThrows() {
        ArticleLikeProgressSink sink = articleSink(mock(KafkaTemplate.class), mapper());

        assertThrows(RuntimeException.class,
                () -> sink.onMessage(record("abc", "{\"user_id\":\"5\",\"cur\":2}")));
    }

    @Test
    @DisplayName("🔴 key 解析成 0 ⇒ 正常返回且**不进 delta**（Go 在 parseVal 之前就 return）")
    void articleSinkZeroArticleIdDoesNotEnterDelta() {
        ArticleLikeProgressSink sink = articleSink(mock(KafkaTemplate.class), mapper());

        sink.onMessage(record("0", "{\"user_id\":\"5\",\"cur\":2}"));

        assertEquals(0, sink.snapshotDelta().size(),
                "★ 若漏了这个提前返回，delta[0] 会被挂上并在 flush 时触发 completeArticleLike");
    }

    @Test
    @DisplayName("载荷解析失败 / user_id 为 0 ⇒ 正常返回（Go: return nil）")
    void articleSinkBadPayloadIsDropped() {
        ArticleLikeProgressSink sink = articleSink(mock(KafkaTemplate.class), mapper());

        sink.onMessage(record("1001", "not-json"));
        sink.onMessage(record("1001", "{\"cur\":5}"));            // 无 user_id
        sink.onMessage(record("1001", "{\"user_id\":\"abc\",\"cur\":5}")); // 非数字

        assertEquals(0, sink.snapshotDelta().size());
    }

    @Test
    @DisplayName("🔴 合并按 cur 取 max，且**相等时保留旧 user_id**（Go article_sink.go:116-120）")
    void articleSinkDeltaKeepsMaxAndOldestUserOnTie() {
        ArticleLikeProgressSink sink = articleSink(mock(KafkaTemplate.class), mapper());

        sink.onMessage(record("9001", "{\"user_id\":\"11\",\"cur\":3}"));
        sink.onMessage(record("9001", "{\"user_id\":\"22\",\"cur\":2}")); // 更小 ⇒ 整体忽略
        sink.onMessage(record("9001", "{\"user_id\":\"22\",\"cur\":3}")); // 相等 ⇒ 保留旧 userId
        sink.onMessage(record("9001", "{\"user_id\":\"33\",\"cur\":5}")); // 更大 ⇒ 连 userId 一起换

        ArticleLikeProgressSink.ArticleAgg agg = sink.snapshotDelta().get(9001L);
        assertEquals(5L, agg.total());
        assertEquals(33L, agg.userId(), "★ cur 变大时 user_id 必须一起更新（Go: cur.UserID = userID）");
    }

    @Test
    @DisplayName("聚合的 key 是**文章 ID**，不是用户 ID（Go delta map[int64]articleAgg）")
    void articleSinkAggregatesPerArticleNotPerUser() {
        ArticleLikeProgressSink sink = articleSink(mock(KafkaTemplate.class), mapper());

        sink.onMessage(record("9001", "{\"user_id\":\"11\",\"cur\":3}"));
        sink.onMessage(record("9002", "{\"user_id\":\"11\",\"cur\":4}"));

        assertEquals(2, sink.snapshotDelta().size(), "同一用户的不同文章要各自成一条");
        assertEquals(3L, sink.snapshotDelta().get(9001L).total());
        assertEquals(4L, sink.snapshotDelta().get(9002L).total());
    }

    // ==================== goka 路由组 ====================

    @Test
    @DisplayName("路由：同一份 raw 原文分别以 user_id / article_id 为 key 发出")
    @SuppressWarnings("unchecked")
    void routerForwardsRawPayloadVerbatim() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TaskRawLogRouter router = new TaskRawLogRouter(
                kafka,
                new com.rag.task.task.rpc.config.TaskKafkaTopics(),
                mapper());

        String raw = "{\"user_id\":\"42\",\"article_id\":\"9001\",\"extra\":\"keep-me\"}";
        router.route(record("ignored", raw));

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);

        verify(kafka).send(eq("task.logs.raw.User"), keyCap.capture(), valCap.capture());
        verify(kafka).send(eq("task.logs.raw.Article"), keyCap.capture(), valCap.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of("42", "9001"), keyCap.getAllValues());
        // ⚠️ value 必须是原文：重新序列化会改变字节序列（字段顺序/多余字段）
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(raw, raw), valCap.getAllValues());
    }

    @Test
    @DisplayName("🔴 路由：解析失败也照常 emit（两个 key 都是空串），且不抛异常")
    @SuppressWarnings("unchecked")
    void routerStillEmitsWhenPayloadIsInvalid() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TaskRawLogRouter router = new TaskRawLogRouter(
                kafka,
                new com.rag.task.task.rpc.config.TaskKafkaTopics(),
                mapper());

        router.route(record("ignored", "garbage"));

        // Go: Unmarshal 失败只 log，随后用零值 LogEvent emit
        verify(kafka, times(1)).send(eq("task.logs.raw.User"), eq(""), anyValue());
        verify(kafka, times(1)).send(eq("task.logs.raw.Article"), eq(""), anyValue());
    }

    private static String anyValue() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    // ==================== goka 计数组 ====================

    @Test
    @DisplayName("用户计数：按 key 累加，value 是**十进制字符串**（goka codec.Int64，非二进制）")
    @SuppressWarnings("unchecked")
    void userCounterEmitsDecimalString() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TaskUserCountConsumer consumer = new TaskUserCountConsumer(
                kafka, new com.rag.task.task.rpc.config.TaskKafkaTopics(), newCountingStore());

        consumer.onMessage(record("42", "raw"));
        consumer.onMessage(record("42", "raw"));
        consumer.onMessage(record("42", "raw"));

        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        verify(kafka, atLeastOnce()).send(eq("task.logs.filtered.User"), eq("42"), valCap.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.List.of("1", "2", "3"), valCap.getAllValues(),
                "★ goka 的 codec.Int64 编的是十进制字符串；按二进制实现会让下游 sink 全部解析失败");
    }

    @Test
    @DisplayName("文章计数：value 是 {\"user_id\":..,\"cur\":..}，user_id 来自 raw 而非 key")
    @SuppressWarnings("unchecked")
    void articleCounterEmitsUserIdFromPayload() {
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TaskArticleCountConsumer consumer = new TaskArticleCountConsumer(
                kafka, new com.rag.task.task.rpc.config.TaskKafkaTopics(),
                newCountingStore(), mapper());

        // key 是文章 ID，但 user_id 来自 raw 载荷
        consumer.onMessage(record("9001", "{\"user_id\":\"42\",\"article_id\":\"9001\"}"));

        ArgumentCaptor<String> valCap = ArgumentCaptor.forClass(String.class);
        verify(kafka, times(1)).send(eq("task.logs.filtered.Article"), eq("9001"), valCap.capture());

        org.junit.jupiter.api.Assertions.assertEquals(
                "{\"user_id\":\"42\",\"cur\":1}", valCap.getValue(),
                "★ user_id 必须来自 raw 载荷 —— 用 key(文章ID) 当 user_id 会把任务记错人");
    }

    @Test
    @DisplayName("TaskCountStore：首次 key 从 1 起（Go: nil ⇒ cur=0 ⇒ cur++ ⇒ 1）")
    void countStoreStartsAtOne() {
        TaskCountStore store = newCountingStore();

        assertEquals(1L, store.increment("g", "k"));
        assertEquals(2L, store.increment("g", "k"));
        assertEquals(1L, store.increment("g", "other"), "不同 key 各自计数");
        assertEquals(2L, store.current("g", "k"));
    }

    @Test
    @DisplayName("🔴🔴 TaskCountStore：两个 goka group 的计数必须互相隔离（2026-10-23 修正）")
    void countStoreIsolatesGroups() {
        TaskCountStore store = newCountingStore();
        final String userGroup = "task.rpc.consumer61";    // initGoKa.go:115
        final String articleGroup = "task.rpc.consumer71"; // initGoKa.go:116

        // 用户路先记 3 次
        store.increment(userGroup, "190");
        store.increment(userGroup, "190");
        store.increment(userGroup, "190");
        // 文章路用**同一个 id 字符串**记 1 次
        assertEquals(1L, store.increment(articleGroup, "190"),
                "★ user_id 与 article_id 的 id 空间会相交；共用一张表会让两条链路的计数互相污染。"
                        + " Go 侧两个 processor 各有独立 store（goka processor.go:82 按 group 名分目录）");

        assertEquals(3L, store.current(userGroup, "190"), "用户路的计数不能被文章路影响");
        assertEquals(1L, store.current(articleGroup, "190"));
    }

    @Test
    @DisplayName("诊断消费者不抛异常（Go PrintConsumer 恒 return nil ⇒ 照常提交位点）")
    void diagnosticConsumerNeverThrows() {
        TaskRawDiagnosticConsumer consumer = new TaskRawDiagnosticConsumer();

        consumer.onMessage(record("k", "v"));
        consumer.onMessage(record(null, null));
        // 能正常返回即通过：Go 的 PrintConsumer 恒 return nil，位点照常提交
    }

    private static com.fasterxml.jackson.databind.ObjectMapper mapper() {
        return com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * 一个「表现得像 Redis」 的 {@link TaskCountStore}：用内存 Map 实现 INCR/GET 语义。
     *
     * <p>为什么不用 mock 逐条 stub：那样测的就不是「同一个 key 会累加」这件事本身了。
     * 本类只要求 <b>计数语义</b>正确（per-key 累加、per-group 隔离），不要求 Redis 本身可用 ——
     * Redis 客户端的连通性属于装配层，由 {@code TaskProgressUpsertParityTest} 那条真 PG 路线覆盖。
     */
    @SuppressWarnings("unchecked")
    private static TaskCountStore newCountingStore() {
        java.util.Map<String, java.util.concurrent.atomic.AtomicLong> backing =
                new java.util.concurrent.ConcurrentHashMap<>();
        org.springframework.data.redis.core.StringRedisTemplate redis =
                mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, String> ops =
                mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(anyString()))
                .thenAnswer(inv -> {
                    String k = inv.getArgument(0);
                    return backing.computeIfAbsent(k, x -> new java.util.concurrent.atomic.AtomicLong())
                            .incrementAndGet();
                });
        when(ops.get(anyString()))
                .thenAnswer(inv -> {
                    java.util.concurrent.atomic.AtomicLong v = backing.get(inv.getArgument(0));
                    return v == null ? null : String.valueOf(v.get());
                });
        return new TaskCountStore(redis);
    }
}