package com.rag.users.user.rpc;

import io.grpc.ServerBuilder;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import net.devh.boot.grpc.server.serverfactory.InProcessGrpcServerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * <b>这个测试类是为了钉死一个已经真实发生过的「假绿」</b>（issue #71187874）。
 *
 * <p><b>坑的形态</b>：{@code ObservabilityWiringTest#grpcInterceptorBeanExists} 断言
 * {@code ctx.getBean(ServerInterceptor.class)} 存在 —— 它一直是对的、一直是绿的，
 * 但它<b>只证明 Bean 在容器里</b>，不证明 gRPC server 真的用了它。
 * 而 grpc-spring-boot-starter 3.1.0.RELEASE <b>从不自动注册</b>用户提供的
 * {@code ServerInterceptor} Bean：
 * <pre>
 *   javap GrpcServerFactoryAutoConfiguration
 *     → inProcessGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer,
 *                                 List&lt;GrpcServerConfigurer&gt;)
 *   javap GrpcServerConfigurer → extends Consumer&lt;ServerBuilder&lt;?&gt;&gt;
 *   javap GrpcServerProperties | grep -i intercept → （无字段）
 * </pre>
 *
 * <p><b>后果远不止「少了几个 span」</b>：{@code interceptCall}/{@code onHalfClose} 从未执行
 * ⇒ handler 线程上 {@code DeadlineState} 恒未设置 ⇒
 * {@code checkDeadline()} 守卫与 B-1 的 {@code setQueryTimeout} 注入<b>全部静默失效</b>。
 * 端到端实测：修复前 rpc 收到 {@code timeout=PT2S}，但 {@code interceptCall} 从未被调用，
 * 客户端要等 3170ms（api 层 3s）才返回，PG 查询滞留 4300ms。
 *
 * <p><b>本测试的边界（诚实声明）</b>：它验证两件事 ——
 * ① 本模块新增的 {@code GrpcServerConfigurer} Bean 确实把 interceptor 交给了 ServerBuilder；
 * ② starter 的 factory 构造器<b>没有</b> ServerInterceptor 形参（即必须自己写 Configurer）。
 * 「starter 会在 build 阶段 apply 所有 Configurer」这一点属于 starter 自身契约，
 * 由 {@code tools/difftest/exp-b1-timeout-cancel.sh} 的端到端结果兜底
 * （判据：客户端必须 ≈2s 返回 1015，而非 3s 的 text/plain）。
 */
@DisplayName("§3.3 gRPC ServerInterceptor 必须真的挂到 server 上（不是只存在于容器里）")
class GrpcInterceptorWiringTest {

    @Test
    @DisplayName("本模块的 GrpcServerConfigurer 必须把 interceptor 交给 ServerBuilder（且链里真的能调到它）")
    void configurerPassesInterceptorToBuilder() {
        io.grpc.ServerInterceptor interceptor = mock(io.grpc.ServerInterceptor.class);
        io.grpc.ServerCall.Listener<Object> listener = new io.grpc.ServerCall.Listener<Object>() {};
        // ⚠️ 必须打桩：Mockito 对返回类型 ServerCall.Listener 的默认答案是 null，
        //    而 SimpleForwardingServerCallListener 的构造器 checkNotNull(delegate) ⇒ 不打桩会 NPE。
        org.mockito.Mockito.doReturn(listener).when(interceptor)
                .interceptCall(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        // ⚠️ 关掉 CPU 降载：本用例要验的是「链里能调到业务 interceptor」，
        //    而降载一旦命中就会短路（handler 不执行）⇒ 断言会变得不确定。
        com.rag.common.protection.GoStyleProtectionProperties props =
                new com.rag.common.protection.GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        GrpcServerConfigurer configurer =
                new UserRpcApplication().rpcInterceptorConfigurer(interceptor, props);

        ServerBuilder<?> builder = mock(ServerBuilder.class);
        org.mockito.ArgumentCaptor<io.grpc.ServerInterceptor> captor =
                org.mockito.ArgumentCaptor.forClass(io.grpc.ServerInterceptor.class);

        configurer.accept(builder);

        verify(builder).intercept(captor.capture());
        io.grpc.ServerInterceptor chained = captor.getValue();

        // 真跑一遍被交给 builder 的那个拦截器：
        // 它在被调用时必须**真的调用到**业务 interceptor（否则超时/trace 静默消失）。
        io.grpc.ServerCall<Object, Object> call = mock(io.grpc.ServerCall.class);
        io.grpc.ServerCallHandler<Object, Object> next = mock(io.grpc.ServerCallHandler.class);
        org.mockito.Mockito.doReturn(listener).when(next)
                .startCall(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        // ⚠️⚠️ **必须打桩 getMethodDescriptor()**：链上的 breaker 用它取隔离 key
        //    （Go: breakerName := info.FullMethod）⇒ Mockito 默认返回 null ⇒ 链里抛 NPE。
        //    ⇒ 而那个 NPE 会被**最外层的 recover 静默兜住**（关掉调用、返回 INTERNAL），
        //    现象是「业务 interceptor 零交互」—— 看起来像"链没生效"，真因却在链里面。
        org.mockito.Mockito.when(call.getMethodDescriptor())
                .thenReturn(io.grpc.MethodDescriptor.<Object, Object>newBuilder()
                        .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName("/user.UserService/Login")
                        .setRequestMarshaller(new NoopMarshaller())
                        .setResponseMarshaller(new NoopMarshaller())
                        .build());

        chained.interceptCall(call, new io.grpc.Metadata(), next);

        // ⚠️ 断言**不是**「同一个对象」：2026-10-24（DRIFT-MSG-V-026）起链上有 breaker / shedding，
        //    它们各自 ForwardingServerCall 包了一层 ⇒ 业务 interceptor 拿到的是**包装后的 call**
        //    ⇒ same(call) 必然不成立。
        // ⇒ 换成两条**更强**的断言：
        //  ① 业务 interceptor 确实被调到了（语义没放松）；
        //  ② 业务 interceptor 拿到的 call **不是**原始 call ⇒ **证明链上真的有包装层**
        //     （即 breaker/shedding 确实挂在链上，而不是"配置了但没生效"）。
        org.mockito.ArgumentCaptor<io.grpc.ServerCall<Object, Object>> callCaptor =
                org.mockito.ArgumentCaptor.forClass(io.grpc.ServerCall.class);
        verify(interceptor).interceptCall(callCaptor.capture(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        org.assertj.core.api.Assertions.assertThat(callCaptor.getValue())
                .as("链上有 breaker/shedding ⇒ 业务 interceptor 拿到的必须是包装过的 call。"
                        + "若拿到的是原始 call，说明包装层没挂上 —— 即 breaker/shedding 形同虚设。")
                .isNotSameAs(call);
    }

    /** 最小可用的 marshaller（测试不真的序列化）。 */
    private static final class NoopMarshaller implements io.grpc.MethodDescriptor.Marshaller<Object> {
        @Override
        public java.io.InputStream stream(Object value) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Object parse(java.io.InputStream stream) {
            return new Object();
        }
    }

    @Test
    @DisplayName("对照：starter 的 factory 构造器不接收 ServerInterceptor（所以必须自己写 Configurer）")
    void starterHasNoServerInterceptorParameter() {
        boolean anyCtorTakesInterceptor = false;
        for (var ctor : InProcessGrpcServerFactory.class.getConstructors()) {
            for (var param : ctor.getParameterTypes()) {
                if (io.grpc.ServerInterceptor.class.isAssignableFrom(param)) {
                    anyCtorTakesInterceptor = true;
                }
            }
        }
        assertThat(anyCtorTakesInterceptor)
                .as("若本条断言失败，说明升级了 grpc-server-spring-boot-starter、"
                        + "它开始自动注册 ServerInterceptor Bean —— 那时本模块的 Configurer 会变成重复注册，"
                        + "需回头删掉这里的 @Bean")
                .isFalse();

        // 同时确认构造器确实接受 GrpcServerConfigurer 列表：这是我们唯一的注册入口
        boolean anyCtorTakesConfigurer = false;
        for (var ctor : InProcessGrpcServerFactory.class.getConstructors()) {
            for (var param : ctor.getParameterTypes()) {
                if (List.class.equals(param)
                        && ctor.getGenericParameterTypes().length > 0) {
                    anyCtorTakesConfigurer = true;
                }
            }
        }
        assertThat(anyCtorTakesConfigurer).isTrue();
        assertThat(GrpcServerProperties.class).isNotNull();
    }
}