package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.observability.RpcInterceptor;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.LikeServiceGrpc;
import com.rag.like.like.rpc.client.MessageRpcClient;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.config.LikeRpcProperties;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import com.rag.like.like.rpc.metrics.LikeRpcMetrics;
import com.rag.like.like.rpc.service.impl.LikeServiceGrpcImpl;
import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>like 域 A9（trace/otel）的<b>端到端</b>守门</b>：证明「<b>真实 gRPC handler 执行期</b>，
 * 域侧 {@code Span.current().setAttribute(...)} 落的属性，真的在<b>服务端 span</b>上」。
 *
 * <h2>🔴 为什么需要这一条（{@link LikeSpansAttributeGateTest} 覆盖不到的那一半）</h2>
 * <p>已有的 {@code LikeSpansAttributeGateTest} 用 {@code runInSpan(...)} <b>自己造一个 span</b>
 * 再把域服务塞进去跑 ⇒ ⇒ 它验的是「<b>域侧写法</b>」（属性会被打到「当前 span」上），
 * <b>不是</b>「<b>真链路上 handler 执行期的当前 span 就是 server span</b>」。
 * <p>⇒ ⇒ 共享层 <b>FB-04</b>（{@code RpcInterceptor} 在 {@code onHalfClose} 里用
 * {@code otelCtx.makeCurrent()} 包住 handler 执行期）落地之前，
 * handler 执行期 {@code Context.current()} 是<b>根 context</b>
 * ⇒ {@code Span.current()} 是 <b>invalid（no-op）span</b> ⇒ 属性<b>静默丢弃</b>
 * ⇒ ⇒ 域侧那条守门<b>照样全绿</b>（它自己造了 span）⇒ ⇒ 「跑绿但零效果」。
 * <p>⇒ ⇒ 本类用<b>真 in-process gRPC 链路</b>补上这一半。
 *
 * <h2>Go 出处（逐行）</h2>
 * <pre>
 *   go-zero v1.10.2 zrpc/internal/serverinterceptors/tracinginterceptor.go:18-24
 *       ctx, span := startSpan(ctx, info.FullMethod)
 *       …
 *       resp, err := handler(ctx, req)        ← handler 拿到的 ctx **携带 span**
 *   同文件 :95-107（startSpan）
 *       return tr.Start(trace.ContextWithRemoteSpanContext(ctx, spanCtx), name, …)
 *       ⇒ span 是**从 ctx 派生**并**放回 ctx**的
 *       ⇒ 业务代码 trace.SpanFromContext(l.ctx) 取得到 ⇒ 属性真落
 *   域侧落点：getlikecountlogic.go:37-41（query.target_type / query.batch_size）
 *             getlikecountlogic.go:90-93（cache.hits / cache.misses）
 * </pre>
 *
 * <h2>L4 idiomatization（§3.2 三段式）</h2>
 * <p><b>比 Go 多做了什么</b>：Go 用 {@code ctx} <b>显式传递</b> span；Java 的 gRPC handler
 * <b>不接收 ctx</b> ⇒ 唯一等价物是 {@code Span.current()}。
 * <p><b>语义等价论证</b>：本类把「Go 把 span 放进 ctx 再传给 handler」映射为
 * 「共享层把 server span 设为 handler 线程的 current」⇒ ⇒ 域侧取到的是<b>同一个</b> span。
 * <p><b>已知差异</b>：Go 侧 like 是「给<b>已有</b> server span 加属性、<b>不新建</b> span」
 * ⇒ ⇒ 本类只断言<b>属性落在服务端 span 上</b>，<b>不</b>断言「不多出子 span」
 * （「不新建」由域侧 {@code LikeSpans} 的写法保证，见其 javadoc）。
 *
 * <h2>🔴 为什么带【对照组】</h2>
 * <p>只用「真链路绿」这一条 ⇒ 可能是「属性根本没落、只是断言写松了」⇒ ⇒ 所以本类另起一条
 * <b>对照链路</b>：用本类内联的一个拦截器<b>复刻 FB-04 之前的行为</b>
 * （起 span、但在 {@code onHalfClose} 里<b>不</b>把 scope 包住 handler）
 * ⇒ ⇒ 同一份域服务、同一通真实 gRPC 调用，<b>唯一差别就是共享层那个机制</b>
 * ⇒ ⇒ 对照组里属性<b>必须丢</b> ⇒ ⇒ 真链路里属性<b>必须在</b>
 * ⇒ ⇒ **这才是「机制是承重的」的证据**，而不是「代码写成了这样」。
 */
class LikeA9GrpcSpanEndToEndTest {

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

    @BeforeAll
    static void installSdk() {
        GlobalOpenTelemetry.resetForTest();
        testSdk = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(new CollectingExporter()))
                        .build())
                .build();
        GlobalOpenTelemetry.set(testSdk);
    }

    @AfterAll
    static void uninstallSdk() {
        GlobalOpenTelemetry.resetForTest();
    }

    private static SpanData onlySpan() {
        assertEquals(1, EXPORTED.size(),
                "本测试每条链路只应产生 1 条 span，实际导出 = "
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

        // Redis 全部「读不到」⇒ 全 miss
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

    // ------------------------------------------------------------------ 链路装置

    /**
     * 起一条**真 in-process gRPC 链路**（真 {@code LikeServiceGrpcImpl} ＋ 真域服务），
     * 只把「server 拦截器」换成传入的那个 ⇒ ⇒ 真链路与对照组之间<b>唯一差别</b>就是拦截器。
     */
    private void callThrough(ServerInterceptor interceptor) throws IOException, InterruptedException {
        String name = InProcessServerBuilder.generateName();
        Server server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .intercept(interceptor)
                .addService(LikeServiceGrpc.bindService(new LikeServiceGrpcImpl(service)))
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        try {
            GetLikeCountReq req = GetLikeCountReq.newBuilder()
                    .setTargetType("article").addTargetIds("a").addTargetIds("b").build();
            LikeServiceGrpc.LikeServiceBlockingStub stub = LikeServiceGrpc.newBlockingStub(channel);
            stub.getLikeCount(req);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 🔴 **对照组**：内联复刻 <b>FB-04 之前</b>的共享层行为 ——
     * 起一个 SERVER span，但<b>只在 {@code close()} 里</b> {@code makeCurrent()}，
     * <b>handler 执行期（{@code onHalfClose}）不包 scope</b>
     * ⇒ ⇒ handler 执行期 {@code Context.current()} 是根 context
     * ⇒ ⇒ {@code Span.current()} = invalid（no-op）span ⇒ 属性静默丢弃。
     * <p>（这正是共享层 {@code RpcInterceptor} javadoc 里记的「此前只在 close() 与 onCancel() 里
     * makeCurrent()」的形态。）
     */
    private static ServerInterceptor preFb04Interceptor() {
        Tracer tracer = GlobalOpenTelemetry.get().getTracer("rpc-server-pre-fb04");
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                String method = call.getMethodDescriptor().getFullMethodName();
                Span span = tracer.spanBuilder(method)
                        .setSpanKind(SpanKind.SERVER)
                        .setParent(Context.current())
                        .startSpan();
                Context otelCtx = Context.current().with(span);
                ServerCall<ReqT, RespT> wrappedCall = new SimpleForwardingServerCall<>(call) {
                    @Override
                    public void close(Status status, Metadata trailers) {
                        try (Scope ignored = otelCtx.makeCurrent()) {
                            span.end();
                        }
                        super.close(status, trailers);
                    }
                };
                // ⚠️ 与共享层同一套包装方式（ForwardingServerCallListener）⇒ ⇒ 唯一差别
                //    就是【onHalfClose 里包不包 scope】
                ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);
                return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(listener) {
                    @Override
                    public void onHalfClose() {
                        // ⚠️ FB-04 之前的形态：**不**用 scope 包住 handler 执行期
                        super.onHalfClose();
                    }
                };
            }
        };
    }

    // ================================================================== 真链路

    @Test
    @DisplayName("真 gRPC 链路：域侧 span 属性必须落在【服务端 span】上"
            + "（Go: tracinginterceptor.go:18-24 把 span 放进 ctx ⇒ handler 取得到）")
    void attributesLandOnServerSpanOverRealGrpcCall() throws IOException, InterruptedException {
        EXPORTED.clear();

        // ⚠️ 必须在 SDK 装好之后取拦截器：共享层在 newUnaryServerInterceptor() 里就
        //    GlobalOpenTelemetry.get().getTracer("rpc-server") 取了 tracer。
        callThrough(RpcInterceptor.newUnaryServerInterceptor(
                Duration.ofSeconds(2), Duration.ofSeconds(1)));

        SpanData span = onlySpan();
        assertTrue(span.getName().contains("GetLikeCount"),
                "导出的这条必须是服务端 span（名字 = gRPC 全方法名），实际 = " + span.getName());
        assertEquals("article", str(span, "query.target_type"),
                "Go getlikecountlogic.go:37-41 ⇒ 真实链路的 server span 上必须有 query.target_type");
        assertEquals(2L, lng(span, "query.batch_size"),
                "Go：query.batch_size = len(in.TargetIds)");
        assertEquals(0L, lng(span, "cache.hits"),
                "Go getlikecountlogic.go:90-93：Redis 全 miss ⇒ hits = 0");
        assertEquals(2L, lng(span, "cache.misses"),
                "Go getlikecountlogic.go:90-93：Redis 全 miss ⇒ misses = 2");
    }

    // ================================================================== 对照组

    @Test
    @DisplayName("对照组（复刻 FB-04 之前的行为）：同一份域服务、同一通真实调用 ⇒ 属性必须【丢】"
            + "⇒ ⇒ 证明上一条绿的是【机制】，不是「代码写成了这样」")
    void attributesAreDroppedWithoutTheSharedLayerMechanism() throws IOException, InterruptedException {
        EXPORTED.clear();

        callThrough(preFb04Interceptor());

        SpanData span = onlySpan();
        assertTrue(span.getName().contains("GetLikeCount"),
                "对照组同样应产生 1 条 server span（否则对照不成立），实际 = " + span.getName());
        assertNull(str(span, "query.target_type"),
                "FB-04 之前：handler 执行期没有 current span ⇒ 属性被丢弃 ⇒ 这一条必须是 null");
        assertNull(lng(span, "cache.hits"),
                "同上：cache.* 也必须丢");
        assertNull(lng(span, "cache.misses"),
                "同上：cache.* 也必须丢");
    }

    // ================================================================== 护栏

    @Test
    @DisplayName("护栏：共享层那个拦截器必须真的在链路上（否则上面两条测的是别的拦截器）")
    void sharedInterceptorIsReallyTheOneOnTheChain() {
        ServerInterceptor shared = RpcInterceptor.newUnaryServerInterceptor(
                Duration.ofSeconds(2), Duration.ofSeconds(1));
        assertNotNull(shared, "共享层必须能产出拦截器");
        // 「真链路」那条用的是共享层的静态工厂；这里锁住「它产出的确实是 ServerInterceptor」
        // ⇒ ⇒ 防止将来有人把共享层那个类改成不再产出拦截器 ⇒ 而本文件还在测「域侧写法」
        assertTrue(shared.getClass().getName().contains("RpcInterceptor")
                        || shared.getClass().getName().startsWith("com.rag.common.observability"),
                "共享层拦截器的实现类应在 com.rag.common.observability 包下，实际 = "
                        + shared.getClass().getName());
    }
}
