package com.rag.common.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：go-zero 框架级 Stat 层的「慢调用日志」在 RAG 侧真的落下来了。</b>
 *
 * <h2>钉住的是什么</h2>
 * <p>G1 反馈单 3/3 的共享层半边（Jason 2026-10-30 拍板：<b>只落 Stat 的慢调用日志</b>）：
 * Go 侧的 {@code zrpc/internal/serverinterceptors/statinterceptor.go:85} 会在慢调用时打一行
 * {@code "[RPC] slowcall - <addr> - <method>"} ⇒ ⇒ RAG 侧必须也有一行可观测的慢调用日志。
 *
 * <h2>为什么必须用「真 gRPC ＋ 真日志捕获」</h2>
 * <p>AGENTS.md §3.3：机制生效必须有<b>运行时证据</b> ⇒
 * ① 真 in-process gRPC server ＋ <b>真的</b> {@link RpcInterceptor}（不是 mock）；
 * ② 真 logback appender（<b>直接读日志事件</b>，不是断言「Bean 存在」）。
 *
 * <h2>判据为什么有鉴别力（变异前必红）</h2>
 * <ul>
 *   <li>把 {@code logSlowCall(...)} 那行去掉 ⇒ 「慢调用有日志」这条断言<b>立刻转红</b>；</li>
 *   <li>把阈值判定写成 {@code >=}（而不是 Go 的严格大于）⇒ 「快调用不慢」那条<b>会红</b>
 *       （Go 的 {@code isSlow} 是 {@code duration > threshold}，见 :65-72）。</li>
 * </ul>
 *
 * <h2>Go 出处</h2>
 * <pre>
 * // go-zero@v1.10.2/zrpc/internal/serverinterceptors/statinterceptor.go
 * :19  const defaultSlowThreshold = time.Millisecond * 500
 * :65-72  isSlow：durationThreshold&gt;0 用它，否则用全局兜底 slowThreshold（真源 yaml 无 Middlewares 块 ⇒ 走兜底 500ms）
 * :74-97  logDuration：慢 ⇒ logger.Slowf("[RPC] slowcall - %s - %s", addr, method)
 * </pre>
 */
class GoStyleSlowCallLogGateTest {

    /** 服务端超时（给得很大 ⇒ 慢的原因只能是 handler 自己慢）。 */
    private static final Duration SERVER_TIMEOUT = Duration.ofSeconds(5);
    /** 慢调用阈值（故意给 100ms ⇒ 不用真等 500ms）。 */
    private static final Duration SLOW_THRESHOLD = Duration.ofMillis(100);
    /** 慢 handler 的耗时（必须 > 阈值）。 */
    private static final long SLOW_HANDLER_MS = 300L;

    private static final MethodDescriptor<byte[], byte[]> CALL =
            MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName("test.SlowCallSvc/Call")
                    .setRequestMarshaller(new BytesMarshaller())
                    .setResponseMarshaller(new BytesMarshaller())
                    .build();

    private static Server server;
    private static ManagedChannel channel;
    private static ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logAppender;

    @BeforeAll
    static void startServer() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .addService(ServerServiceDefinition.builder("test.SlowCallSvc")
                        .addMethod(CALL, ServerCalls.asyncUnaryCall((req, obs) -> {
                            if ("slow".equals(new String(req, StandardCharsets.UTF_8))) {
                                try {
                                    Thread.sleep(SLOW_HANDLER_MS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            obs.onNext("ok".getBytes(StandardCharsets.UTF_8));
                            obs.onCompleted();
                        }))
                        .build())
                // ⚠️ 真的 RpcInterceptor（服务端超时 + 慢阈值）
                .intercept(RpcInterceptor.newUnaryServerInterceptor(SERVER_TIMEOUT, SLOW_THRESHOLD))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();

        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RpcInterceptor.class))
                .addAppender(logAppender);
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (logAppender != null) {
            ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(RpcInterceptor.class))
                    .detachAppender(logAppender);
            logAppender.stop();
        }
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("慢调用 ⇒ 真的打出「[RPC] slowcall」那一行（Go 的 Stat 层产出面）")
    void slowCallProducesTheSlowCallLine() {
        logAppender.list.clear();
        call("slow");

        List<String> lines = warnMessages();
        assertThat(lines)
                .as("慢调用（%dms > 阈值 %dms）⇒ 必须有一行 [RPC] slowcall（Go: statinterceptor.go:85）"
                        .formatted(SLOW_HANDLER_MS, SLOW_THRESHOLD.toMillis()))
                .anyMatch(line -> line.contains("[RPC] slowcall")
                        && line.contains("test.SlowCallSvc/Call"));
    }

    @Test
    @DisplayName("快调用 ⇒ <b>不</b>打 slowcall（Go 的 isSlow 是严格大于 ⇒ 等于阈值也不算慢）")
    void fastCallDoesNotProduceTheSlowCallLine() {
        logAppender.list.clear();
        call("fast");

        assertThat(warnMessages())
                .as("快调用 ⇒ 不该有 slowcall 行（否则每次请求都打一行 ⇒ 日志量爆炸）")
                .noneMatch(line -> line.contains("[RPC] slowcall"));
    }

    private static void call(String payload) {
        try {
            ClientCalls.blockingUnaryCall(channel, CALL, CallOptions.DEFAULT,
                    payload.getBytes(StandardCharsets.UTF_8));
        } catch (StatusRuntimeException e) {
            // 服务端 handler 正常结束 ⇒ 不该有异常；真出了就让它显红
            throw new AssertionError("调用不应失败: " + e.getStatus(), e);
        }
    }

    private static List<String> warnMessages() {
        return logAppender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /** 极简 byte[] 编解码器（本守门不关心内容，只关心「有没有那行日志」）。 */
    private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value == null ? new byte[0] : value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
