package com.rag.hot.hot.api.client;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;

import java.util.concurrent.TimeUnit;

/**
 * 测试用的<b>捕获型</b> gRPC 拦截器：在最外层截断调用，记录「真正发出去的请求」与
 * 「真正带上的 CallOptions（deadline 在里面）」，然后直接回一个预置响应。
 *
 * <h2>为什么需要它（AGENTS.md §3.3：断言 Bean 存在 ≠ 断言机制生效）</h2>
 * 本组件最容易被「测成假绿」的两条断言是：
 * <ul>
 *   <li><b>{@code PublicOnly=true / IncrView=false}</b>：这两个 flag 是 Go 侧
 *       {@code getHotArticlesLogic_test.go:35-37} 用 fake 客户端主动断言的不变量；
 *       用 Mockito mock 掉 {@code ArticleRpcClient} 就等于<b>把被测行为本身 mock 掉了</b>。</li>
 *   <li><b>2 秒 deadline</b>：devh starter 不提供默认 deadline，
 *       它由 {@link GoStyleGrpcCallDeadline} 施加 —— 只断言「方法被调用过」完全测不到它。</li>
 * </ul>
 * ⇒ 必须把真实 stub 装上、让请求真的走到拦截器，才能读到「实际下发的请求」和「实际 deadline」。
 */
final class CapturingClientInterceptor implements ClientInterceptor {

    /** 最近一次真实下发的请求。 */
    private volatile Object capturedRequest;

    /** 最近一次真实下发的 CallOptions。 */
    private volatile CallOptions capturedCallOptions;

    private final Object cannedResponse;

    CapturingClientInterceptor(Object cannedResponse) {
        this.cannedResponse = cannedResponse;
    }

    Object capturedRequest() {
        return capturedRequest;
    }

    CallOptions capturedCallOptions() {
        return capturedCallOptions;
    }

    /** 真实下发的 deadline 剩余毫秒；无 deadline 时为 {@code null}。 */
    Long capturedDeadlineMillis() {
        CallOptions options = capturedCallOptions;
        if (options == null || options.getDeadline() == null) {
            return null;
        }
        return options.getDeadline().timeRemaining(TimeUnit.MILLISECONDS);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                               CallOptions callOptions,
                                                               Channel next) {
        capturedCallOptions = callOptions;
        Object response = cannedResponse;
        return new ClientCall<>() {
            private Listener<RespT> listener;

            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                this.listener = responseListener;
            }

            @Override
            public void request(int numMessages) {
                // 不转发：调用在 halfClose 时直接完成
            }

            @Override
            public void cancel(String message, Throwable cause) {
                // 不转发
            }

            @Override
            public void halfClose() {
                listener.onHeaders(new Metadata());
                @SuppressWarnings("unchecked")
                RespT typed = (RespT) response;
                listener.onMessage(typed);
                listener.onClose(Status.OK, new Metadata());
            }

            @Override
            public void sendMessage(ReqT message) {
                capturedRequest = message;
            }
        };
    }

    /**
     * 造一个「永远不会连上任何东西」的 channel —— 真正的调用被上面那个拦截器截断，
     * channel 从不被使用。端口 1 保证即便漏掉拦截器也不会误连到别的服务。
     */
    static ManagedChannel unusedChannel() {
        return ManagedChannelBuilder.forAddress("127.0.0.1", 1).usePlaintext().build();
    }
}