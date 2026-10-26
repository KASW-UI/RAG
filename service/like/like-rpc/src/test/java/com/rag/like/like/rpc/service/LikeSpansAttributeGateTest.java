package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.observability.MqConsumer;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.GetLikeStateReq;
import com.rag.like.like.proto.GetTargetLikerListReq;
import com.rag.like.like.proto.GetUserLikeListReq;
import com.rag.like.like.proto.GetUserTotalLikeReq;
import com.rag.like.like.proto.LikeActionReq;
import com.rag.like.like.rpc.client.MessageRpcClient;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.config.LikeRpcProperties;
import com.rag.like.like.rpc.exception.LikeExceptions;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import com.rag.like.like.rpc.metrics.LikeMqMetrics;
import com.rag.like.like.rpc.metrics.LikeRpcMetrics;
import com.rag.like.like.rpc.mq.LikeUpdateKafkaListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>like 域 A9（trace/otel）的守门</b>：Go 侧的 span 属性必须<b>真的落到 span 上</b>。
 *
 * <h2>Go 出处（逐条，声明点 + 消费点）</h2>
 * <pre>
 *   getlikestatelogic.go:37-42    span := trace.SpanFromContext(l.ctx) ⇒ query.user_id / query.target_type / query.batch_size
 *   getlikestatelogic.go:69-72    span.SetAttributes(cache.hits, cache.misses)
 *   getlikecountlogic.go:37-41     query.target_type / query.batch_size
 *   getlikecountlogic.go:90-93     cache.hits / cache.misses
 *   gettargetlikerlistlogic.go:35-42  query.target_type / query.target_id / query.cursor / query.page_size
 *   gettargetlikerlistlogic.go:86 / :90  cache.status = hit / miss
 *   getuserlikelistlogic.go:34-40     query.user_id / query.target_type / query.cursor / query.page_size
 *   getuserlikelistlogic.go:80 / :84   cache.status = hit / miss
 *   getusertotallikelogic.go:34-37     query.user_id
 *   getusertotallikelogic.go:52 / :64  cache.status = hit / miss
 *   likeactionlogic.go:161-167         like.user_id / like.target_type / like.target_id / like.action_type
 *   likemq.go:83-84 / :137-143        observability.TraceConsumer(…, likeMessageAttrs("like-topic", key), …)
 * </pre>
 *
 * <h2>🔴 本守门为什么断言「<b>位置</b>」而不只是「有属性」</h2>
 * <p>Go 的 {@code query.*} 那一组属性写在<b>参数校验之前</b>、{@code cache.*} 写在
 * <b>DB 回源之前</b> ⇒ <b>非法请求 / 回源失败</b>的调用<span 上同样有这些属性</span>。
 * ⇒ 所以下面几条用例<b>故意走异常路径</b>，并断言属性<b>依然在</b>。
 * 只断言「成功路径上有属性」的话，把属性挪到校验之后也能全绿 ⇒ 守门会失效。
 *
 * <h2>⚠️ 本守门验的是「域侧写法」，不是「共享层前提」</h2>
 * <p>域侧用 {@code Span.current().setAttribute(...)}（等价于 Go 的
 * {@code trace.SpanFromContext(ctx).SetAttributes(...)}）。
 * 真链上「handler 执行期当前 span 就是 server span」这件事<b>由共享层负责</b>
 * （{@code com.rag.common.observability.RpcInterceptor}；已作为 <b>FB-04</b> 派给共享层常设执行者），
 * ⇒ 本守门在<b>域内</b>把 span 设为 current 后断言属性落位 ⇒
 * <b>验证域侧代码正确</b>，且<b>不依赖</b>共享层那次改动。
 *
 * <h2>为什么用真 OTel SDK 而不是 mock</h2>
 * <p>AGENTS.md §3.3：纯 mock 单测不算「机制生效」的证据。
 * 本类用<b>真 {@link SdkTracerProvider} + 真 exporter</b> ⇒ 断言的是「导出的 span 上真的有这个属性」，
 * 而不是「代码调用了 setAttribute」。
 */
class LikeSpansAttributeGateTest {

    // ------------------------------------------------------------------ OTel 装置

    private static final List<SpanData> EXPORTED = Collections.synchronizedList(new ArrayList<>());

    private static final class CollectingExporter implements SpanExporter {
        @Override
        public CompletableResultCode export(Collection<SpanData> batch) {
            EXPORTED.addAll(batch);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }

    private static OpenTelemetrySdk testSdk;
    private static Tracer tracer;

    @BeforeAll
    static void installSdk() {
        GlobalOpenTelemetry.resetForTest();
        testSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(new CollectingExporter()))
                        .build())
                .build();
        GlobalOpenTelemetry.set(testSdk);
        // ⚠️ 必须用 **SDK 自己的** TracerProvider 取 tracer（不能比 GlobalOpenTelemetry.get() 本身 ——
        //    OTel Java 的 set() 存的是包装器 ⇒ get() 永远不等于 sdk，见 LikeRpcSlowThresholdGateTest 的同款说明）。
        tracer = testSdk.getTracerProvider().get("like-spans-gate");
    }

    @AfterAll
    static void uninstallSdk() {
        GlobalOpenTelemetry.resetForTest();
    }

    /** 在「当前 span = 本测试的 span」的作用域里跑一段代码。 */
    private static void runInSpan(Runnable body) {
        EXPORTED.clear();
        Span span = tracer.spanBuilder("gate").startSpan();
        try (Scope scope = span.makeCurrent()) {
            body.run();
        } finally {
            span.end();
        }
    }

    private static SpanData lastSpan() {
        assertEquals(1, EXPORTED.size(),
                "本守门每个用例只应产生 1 条 span，实际导出 = "
                        + EXPORTED.stream().map(SpanData::getName).toList());
        return EXPORTED.get(0);
    }

    private static String str(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.stringKey(key));
    }

    private static Long lng(SpanData span, String key) {
        return span.getAttributes().get(AttributeKey.longKey(key));
    }

    // ------------------------------------------------------------------ 夹具

    private StringRedisTemplate redis;
    private LikeRecordMapper recordMapper;
    private LikeDomainService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = Mockito.mock(StringRedisTemplate.class);
        recordMapper = Mockito.mock(LikeRecordMapper.class);

        // Redis 全部「读不到」⇒ 默认全 miss（每个用例需要 hit 时单独再 mock）
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.multiGet(anyString(), anyList())).thenReturn(List.of());
        when(hashOps.get(anyString(), anyString())).thenReturn(null);

        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(null);

        ZSetOperations<String, String> zsetOps = Mockito.mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zsetOps);
        when(zsetOps.reverseRangeByScoreWithScores(anyString(), any(Double.class), any(Double.class),
                anyLong(), any(Integer.class))).thenReturn(Set.of());

        when(recordMapper.selectStateCountsBatch(anyString(), anyList())).thenReturn(List.of());
        when(recordMapper.selectUserStatesBatch(anyLong(), anyString(), anyList())).thenReturn(List.of());
        when(recordMapper.selectUserLikeList(anyLong(), anyString(), anyLong(), any(Integer.class)))
                .thenReturn(List.of());
        when(recordMapper.selectTargetLikerList(anyString(), anyString(), anyLong(), any(Integer.class)))
                .thenReturn(List.of());
        when(recordMapper.countActiveLikeByAuthor(anyLong())).thenReturn(0L);

        LikeRpcProperties props = new LikeRpcProperties();
        LikeKafkaTopics topics = new LikeKafkaTopics();
        service = new LikeDomainService(
                redis,
                recordMapper,
                props,
                new LikeRpcMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                topics,
                new LikeDomainService.KafkaLikeMsgPublisher(
                        Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class), topics, new ObjectMapper()),
                Mockito.mock(LikeTaskProgressPublisher.class),
                Mockito.mock(MessageRpcClient.class),
                new ObjectMapper());
    }

    // ================================================================== GetLikeCount

    @Test
    @DisplayName("GetLikeCount：query.target_type / query.batch_size 必须在「空 ID 校验」**之前**落到 span 上"
            + "（Go: getlikecountlogic.go:37-41 在 :42 的空校验之前）")
    void getLikeCountSetsQueryAttrsBeforeValidation() {
        GetLikeCountReq req = GetLikeCountReq.newBuilder().setTargetType("article").build();

        LikeExceptions.Rpc ex = assertThrows(LikeExceptions.Rpc.class,
                () -> runInSpan(() -> service.getLikeCount(req)));
        assertEquals(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), ex.getBizCode(),
                "空 target_ids 必须仍然报「输入需要查询的ID为空」");

        SpanData span = lastSpan();
        assertEquals("article", str(span, "query.target_type"),
                "Go 在参数校验之前就打了 query.target_type ⇒ 非法请求的 span 上也必须有它");
        assertEquals(0L, lng(span, "query.batch_size"),
                "Go 的 query.batch_size = len(in.TargetIds) ⇒ 空请求是 0");
    }

    @Test
    @DisplayName("GetLikeCount：cache.hits / cache.misses 必须落在 span 上（Go: getlikecountlogic.go:90-93）")
    void getLikeCountSetsCacheHitsAndMisses() {
        GetLikeCountReq req = GetLikeCountReq.newBuilder()
                .setTargetType("article").addTargetIds("a").addTargetIds("b").build();

        runInSpan(() -> service.getLikeCount(req));

        SpanData span = lastSpan();
        assertEquals(2L, lng(span, "query.batch_size"), "query.batch_size = len(in.TargetIds)");
        assertEquals(0L, lng(span, "cache.hits"), "Redis 全 miss ⇒ hits = 0");
        assertEquals(2L, lng(span, "cache.misses"), "Redis 全 miss ⇒ misses = 2");
    }

    // ================================================================== GetLikeState

    @Test
    @DisplayName("GetLikeState：query.user_id / query.target_type / query.batch_size + cache.hits / cache.misses"
            + "（Go: getlikestatelogic.go:37-42 与 :69-72）")
    void getLikeStateSetsQueryAndCacheAttrs() {
        GetLikeStateReq req = GetLikeStateReq.newBuilder()
                .setUserId(42L).setTargetType("video").addTargetIds("t-1").build();

        runInSpan(() -> service.getLikeState(req));

        SpanData span = lastSpan();
        assertEquals(42L, lng(span, "query.user_id"), "Go: attribute.Int64(\"query.user_id\", in.UserId)");
        assertEquals("video", str(span, "query.target_type"), "Go: attribute.String(\"query.target_type\", …)");
        assertEquals(1L, lng(span, "query.batch_size"), "Go: attribute.Int(\"query.batch_size\", len(in.TargetIds))");
        assertEquals(0L, lng(span, "cache.hits"), "Redis miss ⇒ hits = 0");
        assertEquals(1L, lng(span, "cache.misses"), "Redis miss ⇒ misses = 1");
    }

    // ================================================================== GetUserTotalLike

    @Test
    @DisplayName("GetUserTotalLike：query.user_id 必须在「userId<=0 校验」**之前**落到 span 上"
            + "（Go: getusertotallikelogic.go:34-37 在 :39 的校验之前）")
    void getUserTotalLikeSetsQueryAttrBeforeValidation() {
        GetUserTotalLikeReq req = GetUserTotalLikeReq.newBuilder().setUserId(0L).build();

        assertThrows(LikeExceptions.Rpc.class, () -> runInSpan(() -> service.getUserTotalLike(req)));

        SpanData span = lastSpan();
        assertEquals(0L, lng(span, "query.user_id"),
                "Go 在参数校验之前就打了 query.user_id ⇒ 非法请求的 span 上也必须有它");
    }

    @Test
    @DisplayName("GetUserTotalLike：命中 ⇒ cache.status=hit；未命中 ⇒ cache.status=miss"
            + "（Go: getusertotallikelogic.go:52 / :64）")
    void getUserTotalLikeSetsCacheStatus() {
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);

        when(valueOps.get(anyString())).thenReturn("7");
        runInSpan(() -> service.getUserTotalLike(
                GetUserTotalLikeReq.newBuilder().setUserId(42L).build()));
        assertEquals("hit", str(lastSpan(), "cache.status"), "Go: cache.status = \"hit\"");

        when(valueOps.get(anyString())).thenReturn(null);
        runInSpan(() -> service.getUserTotalLike(
                GetUserTotalLikeReq.newBuilder().setUserId(42L).build()));
        assertEquals("miss", str(lastSpan(), "cache.status"), "Go: cache.status = \"miss\"");
    }

    // ================================================================== GetUserLikeList

    @Test
    @DisplayName("GetUserLikeList：query.user_id / target_type / cursor / page_size + cache.status=miss"
            + "（Go: getuserlikelistlogic.go:34-40 与 :84）")
    void getUserLikeListSetsQueryAttrsAndCacheStatus() {
        GetUserLikeListReq req = GetUserLikeListReq.newBuilder()
                .setUserId(42L).setTargetType("article").setCursor(123L).setPageSize(999).build();

        runInSpan(() -> service.getUserLikeList(req));

        SpanData span = lastSpan();
        assertEquals(42L, lng(span, "query.user_id"), "Go: attribute.Int64(\"query.user_id\", …)");
        assertEquals("article", str(span, "query.target_type"), "Go: attribute.String(\"query.target_type\", …)");
        assertEquals(123L, lng(span, "query.cursor"), "Go: attribute.Int64(\"query.cursor\", in.Cursor)");
        assertEquals(999L, lng(span, "query.page_size"),
                "Go 的 query.page_size = int64(in.PageSize) —— **原始入参**，不是归一化后的 limit（10）");
        assertEquals("miss", str(span, "cache.status"), "ZSet 为空 ⇒ Go: cache.status = \"miss\"");
    }

    // ================================================================== GetTargetLikerList

    @Test
    @DisplayName("GetTargetLikerList：query.target_type / target_id / cursor / page_size + cache.status=miss"
            + "（Go: gettargetlikerlistlogic.go:35-42 与 :90）")
    void getTargetLikerListSetsQueryAttrsAndCacheStatus() {
        GetTargetLikerListReq req = GetTargetLikerListReq.newBuilder()
                .setTargetType("article").setTargetId("t-9").setCursor(77L).setPageSize(20).build();

        runInSpan(() -> service.getTargetLikerList(req));

        SpanData span = lastSpan();
        assertEquals("article", str(span, "query.target_type"), "Go: attribute.String(\"query.target_type\", …)");
        assertEquals("t-9", str(span, "query.target_id"), "Go: attribute.String(\"query.target_id\", …)");
        assertEquals(77L, lng(span, "query.cursor"), "Go: attribute.Int64(\"query.cursor\", in.Cursor)");
        assertEquals(20L, lng(span, "query.page_size"), "Go: attribute.Int64(\"query.page_size\", int64(in.PageSize))");
        assertEquals("miss", str(span, "cache.status"), "ZSet 为空 ⇒ Go: cache.status = \"miss\"");
    }

    // ================================================================== LikeAction

    @Test
    @DisplayName("LikeAction：like.user_id / target_type / target_id / action_type 必须在「参数异常」校验**之前**"
            + "落到 span 上（Go: likeactionlogic.go:161-167 在 :169 的校验之前）")
    void likeActionSetsAttrsBeforeValidation() {
        LikeActionReq req = LikeActionReq.newBuilder()
                .setUserId(42L).setTargetType("article").setTargetId("").setActionType(
                        com.rag.like.like.proto.ActionType.ACTION_LIKE).build();

        assertThrows(LikeExceptions.Rpc.class, () -> runInSpan(() -> service.likeAction(req)));

        SpanData span = lastSpan();
        assertEquals(42L, lng(span, "like.user_id"), "Go: attribute.Int64(\"like.user_id\", in.UserId)");
        assertEquals("article", str(span, "like.target_type"), "Go: attribute.String(\"like.target_type\", …)");
        assertEquals("", str(span, "like.target_id"), "Go: attribute.String(\"like.target_id\", in.TargetId)");
        assertEquals("like", str(span, "like.action_type"),
                "Go 的 getActionString(ACTION_LIKE) = \"like\"");
    }

    // ================================================================== MQ 消费路径

    @Test
    @DisplayName("MQ 消费：必须产生一个 CONSUMER span（名字 LikeUpdateService.Consume）并带上 4 个 messaging.* 属性"
            + "（Go: likemq.go:83-84 + :137-143）")
    void mqConsumerSpanCarriesMessagingAttrs() {
        LikeUpdateKafkaListener listener = new LikeUpdateKafkaListener(
                new ObjectMapper(),
                Mockito.mock(LikeMessageIngestService.class),
                new LikeMqMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

        String json = "{\"msg_id\":\"123\",\"user_id\":7,\"target_type\":\"article\","
                + "\"target_id\":\"t-1\",\"author_id\":9,\"state\":1,\"is_first\":false,"
                + "\"created_at\":1700000000}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("like_promotion_topic", 0, 1L, "msg-key-1", json);

        EXPORTED.clear();
        listener.onMessage(record, json);

        assertEquals(1, EXPORTED.size(),
                "消费一条消息必须产生且只产生 1 条 span（Go: TraceConsumer 包住整个消费体）");
        SpanData span = EXPORTED.get(0);
        assertEquals(LikeSpans.MQ_SPAN_NAME, span.getName(),
                "Go: observability.TraceConsumer(ctx, \"like-mq\", \"LikeUpdateService.Consume\", …)");
        assertEquals(SpanKind.CONSUMER, span.getKind(),
                "Go: trace.WithSpanKind(trace.SpanKindConsumer)");
        assertEquals("kafka", str(span, "messaging.system"), "Go: attribute.String(\"messaging.system\", \"kafka\")");
        assertEquals("like-topic", str(span, "messaging.destination"),
                "Go: likeMessageAttrs(\"like-topic\", key) —— **字面量**，不是配置里的 like_promotion_topic");
        assertEquals("consume", str(span, "messaging.operation"), "Go: attribute.String(\"messaging.operation\", \"consume\")");
        assertEquals("msg-key-1", str(span, "messaging.message.key"),
                "Go: attribute.String(\"messaging.message.key\", key) —— key = 消费到的这条消息的 Kafka key");
    }

    @Test
    @DisplayName("MQ 消费：JSON 解析失败 ⇒ 仍然产生 span（Go 的 json_error 分支在 TraceConsumer 回调体内）")
    void mqConsumerSpanExistsOnJsonError() {
        LikeUpdateKafkaListener listener = new LikeUpdateKafkaListener(
                new ObjectMapper(),
                Mockito.mock(LikeMessageIngestService.class),
                new LikeMqMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("like_promotion_topic", 0, 2L, "msg-key-2", "{not-json");

        EXPORTED.clear();
        listener.onMessage(record, "{not-json");

        assertEquals(1, EXPORTED.size(), "Go: TraceConsumer 的 span 覆盖整个回调体（含 json_error 分支）");
        assertEquals(LikeSpans.MQ_SPAN_NAME, EXPORTED.get(0).getName());
    }

    @Test
    @DisplayName("MqConsumer.traceConsumer 的属性 map 必须与 Go 的 likeMessageAttrs 逐条一致")
    void messagingAttrsMapMatchesGo() {
        var attrs = LikeSpans.messagingAttributes("k-1");
        assertEquals("kafka", attrs.get("messaging.system"));
        assertEquals("like-topic", attrs.get("messaging.destination"));
        assertEquals("consume", attrs.get("messaging.operation"));
        assertEquals("k-1", attrs.get("messaging.message.key"));
        assertNotNull(MqConsumer.class, "本用例只是钉住 map 的形状；traceConsumer 的落位由上一个用例覆盖");
    }
}
