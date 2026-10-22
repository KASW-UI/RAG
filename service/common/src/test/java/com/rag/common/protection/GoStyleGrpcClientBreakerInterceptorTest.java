package com.rag.common.protection;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GoStyleGrpcClientBreakerInterceptor} 对 Go
 * {@code zrpc/internal/clientinterceptors/breakerinterceptor.go} 的行为锁定。
 *
 * <p><b>本类存在的理由（DRIFT-MSGAPI-004）</b>：RAG 原本<b>完全没有</b> gRPC client 侧熔断，
 * 而 Go 侧默认开着（{@code zrpc/internal/config.go:14} 的 {@code default=true}）。
 * 更麻烦的是这个缺陷<b>测不出来</b>——因为 message-rpc 的业务错误全都不计入熔断，
 * 正常流量下两侧行为一致 ⇒ 任何只跑业务路径的用例都会假绿。
 */
class GoStyleGrpcClientBreakerInterceptorTest {

    // ══════════════════════════════════════════════════════════════════
    // 1. acceptable 判据：这是本层唯一真正的语义，必须逐条锁死
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("六个失败 code 判为不可接受（Go codes.Acceptable 的完整名单，一个不多一个不少）")
    void theSixFailureCodesAreNotAcceptable() {
        List<Status.Code> failures = List.of(
                Status.Code.DEADLINE_EXCEEDED,
                Status.Code.INTERNAL,
                Status.Code.UNAVAILABLE,
                Status.Code.DATA_LOSS,
                Status.Code.UNIMPLEMENTED,
                Status.Code.RESOURCE_EXHAUSTED);
        for (Status.Code c : failures) {
            assertFalse(GoStyleGrpcClientBreakerInterceptor.isAcceptable(Status.fromCode(c)),
                    c + " 在 Go 的失败名单里，必须判为不可接受");
        }
    }

    /**
     * 2026-10-22 新增：把「超时到底以什么形态到达 breaker」钉死。
     *
     * <p><b>为什么这条现在才成立</b>：在 {@code com.rag.common.observability.GoStyleRequestDeadline}
     * 落地之前，本项目<b>没有任何机制</b>能让 gRPC 调用带 deadline 超时，
     * {@code DEADLINE_EXCEEDED} 实际<b>永远不可能出现</b> ——
     * 所以「它在不在失败名单里」当时只是一条抄来的、未被触发的静态判据。
     * <p>落地后它开始真实出现，而实测证明：<b>走 deadline 路径</b>得到 {@code DEADLINE_EXCEEDED}（失败），
     * <b>走线程 interrupt 路径</b>（{@code Future.cancel(true)}）得到 {@code CANCELLED}（<b>成功</b>）。
     * ⇒ 同一个「下游超时」事件，两条路径对 breaker 的意义<b>完全相反</b>。
     * 用户 2026-10-22 明确拍板<b>接受</b>「超时计入 breaker 失败」，与 Go 一致。
     */
    @Test
    @DisplayName("超时的两条路径对 breaker 意义相反：DEADLINE_EXCEEDED=失败，CANCELLED=成功")
    void timeoutPathsDifferForTheBreaker() {
        assertFalse(GoStyleGrpcClientBreakerInterceptor.isAcceptable(Status.DEADLINE_EXCEEDED),
                "deadline 路径：在失败名单里（与 zrpc/internal/codes/accept.go 一致）");
        assertTrue(GoStyleGrpcClientBreakerInterceptor.isAcceptable(Status.CANCELLED),
                "⚠️ CANCELLED 不在 Go 的六个失败 code 里 ⇒ 判为可接受。"
                        + "这不是缺陷而是 Go 原样：Go 侧 ctx 取消给 DEADLINE_EXCEEDED，"
                        + "Java 的线程中断给 CANCELLED。补 deadline 传播正是为了让超时落到前者。");
    }

    @Test
    @DisplayName("⚠️ 业务错误（NOT_FOUND / PERMISSION_DENIED / INVALID_ARGUMENT）判为**可接受**")
    void businessErrorsAreAcceptable() {
        // 这一条是本层最容易写反的地方。message-rpc 的业务错误只用这三个 code，
        // Go 的 codes.Acceptable 不把它们算作失败 ⇒ 业务错误**不参与熔断**。
        // 若有人改成「非 OK 即失败」，这些天天发生的正常错误会大量计成失败、误开熔断。
        for (Status.Code c : List.of(Status.Code.NOT_FOUND,
                Status.Code.PERMISSION_DENIED,
                Status.Code.INVALID_ARGUMENT,
                Status.Code.ALREADY_EXISTS,
                Status.Code.UNAUTHENTICATED,
                Status.Code.FAILED_PRECONDITION,
                Status.Code.ABORTED,
                Status.Code.OUT_OF_RANGE,
                Status.Code.UNKNOWN,
                Status.Code.OK)) {
            assertTrue(GoStyleGrpcClientBreakerInterceptor.isAcceptable(Status.fromCode(c)),
                    c + " 不在 Go 的失败名单里，必须判为可接受（业务错误不参与熔断）");
        }
    }

    @Test
    @DisplayName("null status 判为可接受（Go: status.Code(nil) == codes.OK）")
    void nullStatusIsAcceptable() {
        assertTrue(GoStyleGrpcClientBreakerInterceptor.isAcceptable(null));
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. 端到端：真把 interceptor 驱动一遍（假 Channel，但走真实 interceptCall 路径）
    // ══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("业务错误连续发生**不会**打开熔断（与 Go 一致）")
    void repeatedBusinessErrorsNeverTripTheBreaker() throws InterruptedException {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(true, () -> 0.0D);   // random=0 最易熔断
        FakeChannel channel = new FakeChannel(Status.Code.NOT_FOUND);

        for (int round = 0; round < 4; round++) {
            for (int i = 0; i < 40; i++) {
                assertDoesNotThrow(() -> drive(interceptor, channel),
                        "业务错误不得参与熔断，第 " + round + " 轮第 " + i + " 次必须放行");
            }
            sleepOneBucket();
        }
        assertEquals(160, channel.calls, "所有调用都必须真的打到下游（熔断一次都不该开）");
    }

    @Test
    @DisplayName("INTERNAL 连续失败会打开熔断，抛出普通异常（非 StatusRuntimeException）")
    void repeatedInternalErrorsTripTheBreaker() throws InterruptedException {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(true, () -> 0.0D);
        FakeChannel channel = new FakeChannel(Status.Code.INTERNAL);

        // ⚠️ 必须**跨桶**累积失败：googlebreaker 的 history() 按 Go 语义跳过当前半桶
        // （collection/rollingwindow.go 的 IgnoreCurrentBucket）。
        // 同一个 250ms 桶里的几百次失败对 history() 而言等于 0 —— Go 侧同样如此。
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 40; i++) {
                try {
                    drive(interceptor, channel);
                } catch (GoStyleCircuitOpenException e) {
                    // 已经打开，后面继续驱动即可
                }
            }
            sleepOneBucket();
        }

        boolean tripped = false;
        for (int i = 0; i < 50 && !tripped; i++) {
            try {
                drive(interceptor, channel);
            } catch (GoStyleCircuitOpenException e) {
                tripped = true;
            }
            sleepOneBucket();
        }
        assertTrue(tripped, "下游连续返回 INTERNAL 时，熔断必须打开（Go 侧同样会开）");

        // 熔断后的每次调用都应立即被拒，且**不触达下游**
        int downstreamBefore = channel.calls;
        for (int i = 0; i < 5; i++) {
            assertThrows(GoStyleCircuitOpenException.class, () -> drive(interceptor, channel));
        }
        assertEquals(downstreamBefore, channel.calls, "熔断打开后不得再调用下游");
    }

    /**
     * 只锁<b>机制</b>：熔断拒绝抛的是普通异常，不是 gRPC status。
     *
     * <p><b>为什么这条断言留在 common 而「码表映射」不在</b>：
     * 「不是 StatusRuntimeException」是本类的固有属性，与业务域无关；
     * 而「落到 1015 / msg 为 service busy」是 message 域的码表，
     * {@code service/common} <b>不能</b>依赖 {@code message-common}（common 是所有业务模块的
     * 下游，反向依赖会把整棵依赖树倒过来）。⇒ 码表映射那条断言搬到
     * {@code message-api} 的 {@code GoStyleGrpcClientBreakerE2ETest}，
     * 且在那里是<b>真运行时</b>断言（读真实 envelope），比这里的静态映射断言更强。
     *
     * <p>Go 出处：{@code core/breaker/googlebreaker.go:52-79} 返回
     * {@code breaker.ErrServiceUnavailable}，它是<b>普通 error</b>；
     * 调用侧 {@code core/status/status.go} 的 {@code status.FromError(err)} 对它
     * 返回 {@code ok=false} ⇒ 走 1015 分支。
     */
    @Test
    @DisplayName("熔断拒绝抛的是普通异常而非 gRPC status（业务码映射在调用方，不在本层）")
    void circuitOpenExceptionIsNotAGrpcStatus() {
        GoStyleCircuitOpenException e = new GoStyleCircuitOpenException("circuit breaker is open");
        // 用 Class::isInstance 而非 instanceof —— 编译器能静态判定二者不相交会直接报错
        assertFalse(io.grpc.StatusException.class.isInstance(e));
        assertFalse(io.grpc.StatusRuntimeException.class.isInstance(e),
                "若改成 StatusRuntimeException，各域的 status 提取逻辑就会返回该 status，"
                        + "最终 msg 会变成 \"circuit breaker is open\" 而非 Go 的码表文案");
        assertEquals(RuntimeException.class, e.getClass().getSuperclass(),
                "必须是直接继承 RuntimeException 的普通异常，否则各域的兜底分支可能先接住它");
    }

    @Test
    @DisplayName("breaker 按 RPC 方法隔离")
    void breakersAreIsolatedPerMethod() throws InterruptedException {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(true, () -> 0.0D);
        FakeChannel bad = new FakeChannel(Status.Code.INTERNAL);
        FakeChannel ok = new FakeChannel(Status.Code.OK);

        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 40; i++) {
                try {
                    drive(interceptor, bad, MethodA.METHOD);
                } catch (GoStyleCircuitOpenException ignored) {
                    // 目标是打开 A 的熔断
                }
            }
            sleepOneBucket();
        }
        assertDoesNotThrow(() -> drive(interceptor, ok, MethodA.OTHER_METHOD),
                "另一个 RPC 方法不得受 A 的熔断影响");
    }

    @Test
    @DisplayName("enabled=false 时完全不介入（Go: Middlewares.Breaker=false）")
    void disabledInterceptorIsTransparent() {
        GoStyleGrpcClientBreakerInterceptor interceptor =
                new GoStyleGrpcClientBreakerInterceptor(false, () -> 0.0D);
        FakeChannel channel = new FakeChannel(Status.Code.INTERNAL);
        for (int i = 0; i < 300; i++) {
            assertDoesNotThrow(() -> drive(interceptor, channel));
        }
        assertEquals(300, channel.calls, "关闭时每次都必须真的打到下游");
    }

    // ══════════════════════════════════════════════════════════════════
    // 3. 测试脚手架
    // ══════════════════════════════════════════════════════════════════

    /** 走完 interceptCall → start → onClose 的真实路径，只把「最终状态码」做成可控。 */
    @SuppressWarnings("unchecked")
    private static <ReqT, RespT> void drive(ClientInterceptor interceptor, FakeChannel channel) {
        drive(interceptor, channel, MethodA.METHOD);
    }

    @SuppressWarnings("unchecked")
    private static <ReqT, RespT> void drive(ClientInterceptor interceptor, FakeChannel channel,
                                           MethodDescriptor<byte[], byte[]> method) {
        ClientCall<byte[], byte[]> call =
                interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
        final AtomicBoolean closed = new AtomicBoolean(false);
        call.start(new ClientCall.Listener<>() {
            @Override
            public void onClose(Status status, Metadata trailers) {
                closed.set(true);
            }
        }, new Metadata());
        call.halfClose();
        channel.fireClose(call);
        assertTrue(closed.get(), "脚手架未把 onClose 触发出去，断言会失去意义");
    }

    private static final class MethodA {
        static final MethodDescriptor<byte[], byte[]> METHOD = methodOf("/m.v1.Svc/A");
        static final MethodDescriptor<byte[], byte[]> OTHER_METHOD = methodOf("/m.v1.Svc/B");

        private static MethodDescriptor<byte[], byte[]> methodOf(String name) {
            return MethodDescriptor.<byte[], byte[]>newBuilder()
                    .setType(MethodDescriptor.MethodType.UNARY)
                    .setFullMethodName(name)
                    .setRequestMarshaller(new ByteMarshaller())
                    .setResponseMarshaller(new ByteMarshaller())
                    .build();
        }
    }

    /** 跨过 250ms 桶边界，让写入落到「下一个」桶 —— 熔断只统计**已完成**桶。 */
    private static void sleepOneBucket() throws InterruptedException {
        Thread.sleep(260L);
    }

    private static final class ByteMarshaller implements MethodDescriptor.Marshaller<byte[]> {
        @Override
        public java.io.InputStream stream(byte[] value) {
            return new java.io.ByteArrayInputStream(value == null ? new byte[0] : value);
        }

        @Override
        public byte[] parse(java.io.InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** 可控结果码的假 Channel：{@code newCall} 返回的 ClientCall 会记住 listener，测试手动触发 onClose。 */
    private static final class FakeChannel extends Channel {
        final Status.Code result;
        int calls;
        private final List<ClientCall.Listener<byte[]>> pending = new ArrayList<>();

        FakeChannel(Status.Code result) {
            this.result = result;
        }

        @Override
        public String authority() {
            return "test";
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            calls++;
            return new ClientCall<>() {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    @SuppressWarnings("unchecked")
                    Listener<byte[]> l = (Listener<byte[]>) responseListener;
                    pending.add(l);
                }

                @Override
                public void request(int numMessages) {
                }

                @Override
                public void cancel(String message, Throwable cause) {
                }

                @Override
                public void halfClose() {
                }

                @Override
                public void sendMessage(ReqT message) {
                }
            };
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        void fireClose(ClientCall<?, ?> call) {
            // interceptor 的 start() 已经把带记账的 listener 注册到了 pending 里；
            // 这里不需要区分原 listener，统一用最近注册的那个。
            ClientCall.Listener<byte[]> l = pending.remove(pending.size() - 1);
            l.onClose(Status.fromCode(result), new Metadata());
        }
    }
}