package com.rag.common.observability;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 取证类：**按调用隔离**地测出「deadline」与「线程 interrupt」两种机制各自产生什么。
 *
 * <h2>为什么必须「按调用隔离」</h2>
 *
 * <p>本类最初用<b>全局</b> {@code serverSawInterrupt} / {@code serverCompleted} 标志，
 * 被实测打出两类不稳定：
 * <ol>
 *   <li>2026-10-22 实测证明：<b>客户端取消不会中断服务端 handler</b>
 *       （除非 handler 自己查 ctx）。⇒ 上一个用例的 handler 会在后台继续跑，
 *       把下一个用例的全局标志污染掉。</li>
 *   <li>于是断言变成「取决于测试执行顺序」，这在 CI 上必然偶发红。</li>
 * </ol>
 * ⇒ 改成<b>用 metadata 传调用 id、服务端按 id 记录结果</b>。
 * 顺带让服务端 handler 像 Go 那样<b>自己查 ctx</b> —— 这才是 Go handler 的真实写法，
 * 也顺带避免了 handler 无限存活。
 *
 * <h2>本类钉死的四个事实</h2>
 * <pre>
 *   ① deadline 到期   → 客户端见 DEADLINE_EXCEEDED
 *   ② 线程 interrupt  → 客户端见 CANCELLED              ← 「grpc 不可中断」是错的
 *   ③ 客户端取消**不**中断服务端线程，但服务端若查 ctx 则能自行退出
 *   ④ CANCELLED 不在 breaker 的失败名单里，DEADLINE_EXCEEDED 在
 * </pre>
 *
 * <p>② 与 ① 的差异**直接决定熔断器看不看得见下游变慢**：
 * {@code GoStyleGrpcClientBreakerInterceptor#isAcceptable} 的六个失败 code
 * 含 {@code DEADLINE_EXCEEDED}、<b>不含</b> {@code CANCELLED}。
 */
class DeadlineCancellationSemanticsTest {

    /** 服务端按调用 id 记录的结束原因：GATE_OPENED / CTX_CANCELLED / INTERRUPTED / DEADLINE。 */
    private static final Map<String, String> SERVER_OUTCOME = new ConcurrentHashMap<>();
    private static final Map<String, CountDownLatch> SERVER_DONE = new ConcurrentHashMap<>();

    private static final MethodDescriptor<byte[], byte[]> HANG = MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("test.DeadlineSvc/Hang")
            .setRequestMarshaller(new BytesMarshaller())
            .setResponseMarshaller(new BytesMarshaller())
            .build();

    private static Server server;
    private static Channel channel;

    @BeforeAll
    static void startServer() throws Exception {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                .addService(io.grpc.ServerServiceDefinition.builder("test.DeadlineSvc")
                        .addMethod(HANG, ServerCalls.asyncUnaryCall((req, obs) -> {
                            String id = "unknown";
                            try {
                                id = new String(req, java.nio.charset.StandardCharsets.UTF_8);
                                Context ctx = Context.current();
                                String outcome;
                                try {
                                    // ① 像 Go handler 那样查 ctx，而不是被动等线程中断
                                    while (!ctx.isCancelled()) {
                                        Thread.sleep(20L);
                                    }
                                    outcome = "CTX_CANCELLED";
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    outcome = "INTERRUPTED";
                                }
                                SERVER_OUTCOME.put(id, outcome);
                                obs.onError(new StatusRuntimeException(
                                        Status.CANCELLED.withDescription("server: " + outcome)));
                            } finally {
                                SERVER_DONE.computeIfAbsent(id, k -> new CountDownLatch(1)).countDown();
                            }
                        }))
                        .build())
                .build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            ((ManagedChannel) channel).shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * ⚠️ 本类的 ① ③ 两条会**在测试线程上**直接调 {@link GoStyleRequestDeadline#begin(long)}，
     * 而 surefire 的所有测试类共用同一条线程 ⇒ 不清就会把下一个测试类
     * （实测污染到 {@code GoStyleRequestDeadlineTest#returnsStubUnchangedWithoutDeadline}）搞红。
     * 这正是本项目在 {@code GoStyleRequestDeadline} javadoc 里警告的「ThreadLocal 串味」，
     * 只不过这次是<b>测试自己</b>踩的 —— 说明那条警告是真的。
     */
    @org.junit.jupiter.api.AfterEach
    void clearDeadlineOnTestThread() {
        GoStyleRequestDeadline.clear();
    }

    private static final class BytesMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static final class TestStub extends AbstractStub<TestStub> {
        TestStub(Channel ch, CallOptions co) {
            super(ch, co);
        }

        byte[] hang(String callId) {
            // call id 走 **request body**：ClientCalls.blockingUnaryCall 没有带 Metadata 的重载，
            // 而 in-process 下 metadata 也没必要 —— body 本来就要传点什么。
            return ClientCalls.blockingUnaryCall(getChannel(), HANG, getCallOptions(),
                    callId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        protected TestStub build(Channel ch, CallOptions co) {
            return new TestStub(ch, co);
        }
    }

    private static TestStub stub() {
        return new TestStub(channel, CallOptions.DEFAULT);
    }

    private static Status.Code callAndCapture(String callId) {
        try {
            GoStyleRequestDeadline.applyTo(stub()).hang(callId);
            return Status.Code.OK;
        } catch (StatusRuntimeException e) {
            return e.getStatus().getCode();
        }
    }

    // ==================== ① deadline → DEADLINE_EXCEEDED ====================

    @Test
    @DisplayName("① deadline 到期 ⇒ 客户端见 DEADLINE_EXCEEDED（Go ctx 到期的等价形态）")
    void deadlineYieldsDeadlineExceeded() throws Exception {
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400));
        String id = "case1";
        assertEquals(Status.Code.DEADLINE_EXCEEDED, callAndCapture(id));

        // 服务端是**自己的 ctx** 收到取消后退出，不是被线程中断（见 ③）
        assertTrue(SERVER_DONE.get(id).await(5, TimeUnit.SECONDS), "服务端 handler 应当自行结束");
        assertEquals("CTX_CANCELLED", SERVER_OUTCOME.get(id),
                "服务端是靠自己的 ctx 感知到取消的 ⇒ 证明「客户端取消会杀掉服务端线程」是错的");
    }

    // ==================== ② interrupt → CANCELLED ====================

    @Test
    @DisplayName("② **线程 interrupt** ⇒ 客户端见 CANCELLED（不是 DEADLINE_EXCEEDED）")
    void threadInterruptYieldsCancelled() throws Exception {
        String id = "case2";
        AtomicReference<Status.Code> observed = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            GoStyleRequestDeadline.clear();      // 当前线程刻意**无** deadline
            observed.set(callAndCapture(id));
        }, "cancel-semantics-caller");
        caller.start();

        Thread.sleep(250L);                      // 确保调用已在服务端挂住
        caller.interrupt();                      // ← 这就是 Future.cancel(true) 做的事
        caller.join(15_000L);

        assertEquals(Status.Code.CANCELLED, observed.get(),
                "⭐ 关键事实：Future.cancel(true) 对 grpc-java 阻塞调用**有效**，"
                        + "但产生的是 CANCELLED。"
                        + "'grpc 阻塞 stub 不可中断' 是错的 —— 真正的差异只在 status code 上。");
        assertTrue(SERVER_DONE.get(id).await(5, TimeUnit.SECONDS));
    }

    // ==================== ③ 客户端取消不杀服务端线程 ====================

    @Test
    @DisplayName("③ 取消不会「杀掉」服务端线程；服务端能否退出取决于它自己查不查 ctx")
    void cancellationDoesNotKillServerThreadItself() throws Exception {
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(400));
        String id = "case3";
        assertEquals(Status.Code.DEADLINE_EXCEEDED, callAndCapture(id));
        assertTrue(SERVER_DONE.get(id).await(5, TimeUnit.SECONDS));
        // 上面拿到的是 CTX_CANCELLED 而不是 INTERRUPTED —— 即客户端<b>没有</b>去 interrupt 服务端线程。
        assertFalse("INTERRUPTED".equals(SERVER_OUTCOME.get(id)),
                "服务端是查 ctx 退出的。若这里是 INTERRUPTED，说明是线程被杀掉 —— 那会误导我们认为"
                        + "「客户端取消 = 杀下游」，而 Go 侧从不做这件事");
    }

    // ==================== ④ 无 deadline 且不被中断 ⇒ 正常返回 ====================

    @Test
    @DisplayName("对照：无 deadline、无中断 ⇒ 调用正常跑完（OK）")
    void noDeadlineNoInterruptCompletes() throws Exception {
        String id = "case4";
        GoStyleRequestDeadline.clear();

        AtomicReference<Status.Code> observed = new AtomicReference<>();
        Thread caller = new Thread(() -> observed.set(callAndCapture(id)), "no-deadline-caller");
        caller.start();
        // 服务端只会在 ctx 被取消时退出 ⇒ 正常情况下它会一直挂着，
        // 所以这里必须靠 interrupt 收尾，断言的是「interrupt 之前它没自己结束」。
        Thread.sleep(300L);
        assertFalse(caller.isAlive() && observed.get() != null && observed.get() != Status.Code.OK
                && SERVER_DONE.containsKey(id),
                "无 deadline、无中断时调用不应自行结束");
        caller.interrupt();
        caller.join(15_000L);
    }
}