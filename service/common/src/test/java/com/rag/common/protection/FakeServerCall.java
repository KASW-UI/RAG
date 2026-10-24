package com.rag.common.protection;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 测试用�� {@link ServerCall} —— 只实现服务端拦截器真正用到的那几个方法。
 *
 * <p><b>为什么需要它</b>：拦截器的判据观察点是 {@code ServerCall#close(Status, Metadata)}，
 * 没有可断言的返回值 ⇒ 必须用一个能<b>记录</b> close 的假 call。
 */
final class FakeServerCall extends ServerCall<Object, Object> {

    private final String fullMethodName;

    /** 被 close 时的状态；null 表示还没 close。 */
    Status closedStatus;
    Metadata closedTrailers;

    /** handler 是否被执行过（用来断言「拒绝时 handler 不跑」）。 */
    boolean handlerRan;

    FakeServerCall(String fullMethodName) {
        this.fullMethodName = fullMethodName;
    }

    static String methodNameOf(String service, String method) {
        return "/" + service + "/" + method;
    }

    @Override
    public void request(int numMessages) {
    }

    @Override
    public void sendHeaders(Metadata headers) {
    }

    @Override
    public void sendMessage(Object message) {
    }

    @Override
    public void close(Status status, Metadata trailers) {
        this.closedStatus = status;
        this.closedTrailers = trailers;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public MethodDescriptor<Object, Object> getMethodDescriptor() {
        return MethodDescriptor.<Object, Object>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(fullMethodName)
                .setRequestMarshaller(new StringMarshaller())
                .setResponseMarshaller(new StringMarshaller())
                .build();
    }

    /** 最小可用的 marshaller（测试里不真的序列化）。 */
    static final class StringMarshaller implements MethodDescriptor.Marshaller<Object> {
        @Override
        public InputStream stream(Object value) {
            return new ByteArrayInputStream(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Object parse(InputStream stream) {
            return "parsed";
        }
    }
}
