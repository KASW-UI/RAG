package com.rag.favorite.favorite.rpc;

import com.rag.common.protection.GoStyleProtectionProperties;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <b>DRIFT-MSG-V-026 的逐组件守门</b>：熔断 / 降载链必须<b>真的</b>挂到本组件的 gRPC server 上。
 *
 * <p><b>为什么必须是「组件级」而不是「一条共用测试」</b>（AGENTS.md §3.5）：
 * Go 侧的熔断 / 降载拦截器挂在 zrpc 客户端拦截器链上
 * （{@code go-zero@v1.10.2/zrpc/internal/client.go:112-117}），
 * 熔断 / 降载在 Go 侧是 <b>zrpc 框架级</b>能力，每个 rpc server 都自带；
 * 本仓把链的定义点放在共享模块 {@code GoStyleRpcProtectionChain}，
 * 但<b>每个组件仍然要自己把链交给 ServerBuilder</b> ——
 * 漏掉任何个组件，那个组件就<b>既没有 recover、也没有熔断、也没有降载</b>，
 * 而<b>不报任何错</b>（这正是 §3.3 的经典形态）。
 *
 * <p><b>本测试断言两件事</b>：
 * <ol>
 *   <li>{@code @Import} 列表含 {@code GoStyleProtectionConfiguration}
 *       ⇒ {@code GoStyleProtectionProperties} Bean 真的存在
 *       （它是 {@code @ConfigurationProperties} 类，<b>不会</b>被组件扫描自动注册，
 *       缺了它 {@code rpcInterceptorConfigurer} 根本注入不到参数 ⇒ 上下文起不来）；</li>
 *   <li>交给 ServerBuilder 的那条链<b>真跑一遍</b>后，
 *       业务 interceptor 拿到的 {@code call} <b>不是</b>原始 call
 *       ⇒ <b>证明链上真的有包装层</b>（breaker / shedding 各自 ForwardingServerCall 包了一层），
 *       而不是「配置了但没生效」。</li>
 * </ol>
 *
 * <p>⚠️ <b>为什么断言不能是「同一个对象」</b>：
 * 链上有包装层时，业务 interceptor 收到的是<b>包装后的 call</b> ⇒ {@code same(call)} 必然不成立。
 * ⇒ 断言改成「业务 interceptor <b>确实被调到</b>」+「拿到的 call <b>不是</b>原始 call」——
 * 这比「同一个对象」<b>更强</b>：它能区分「业务 interceptor 在链里」与「链被拼反 / 没挂上」。
 */
@DisplayName("DRIFT-MSG-V-026 FavoriteRpcApplication：熔断/降载链必须真的挂到 gRPC server 上")
class FavoriteProtectionChainWiringTest {

    @Test
    @DisplayName("Application 必须 @Import GoStyleProtectionConfiguration（否则 properties Bean 不存在）")
    void applicationImportsProtectionConfiguration() {
        var importAnnotation = FavoriteRpcApplication.class
                .getAnnotation(org.springframework.context.annotation.Import.class);
        assertThat(importAnnotation)
                .as("FavoriteRpcApplication 必须有 @Import；没有它 = common 的 Bean 一个都不装配")
                .isNotNull();

        assertThat(java.util.Arrays.asList(importAnnotation.value()))
                .as("@Import 列表必须含 GoStyleProtectionConfiguration —— "
                        + "它是 GoStyleProtectionProperties（breaker / cpuShedding 开关与阈值）的唯一注册点，"
                        + "漏掉它 = 上下文起不来（缺 Bean），不是「保护层静默关闭」")
                .contains(com.rag.common.protection.GoStyleProtectionConfiguration.class);
    }

    @Test
    @DisplayName("链上真的有 breaker/shedding 包装层（业务 interceptor 被调到，且拿到的是包装过的 call）")
    void chainReallyWrapsTheBusinessInterceptor() {
        ServerInterceptor interceptor = mock(ServerInterceptor.class);
        ServerCall.Listener<Object> listener = new ServerCall.Listener<Object>() {};
        // ⚠️ 必须打桩：Mockito 对返回类型 ServerCall.Listener 的默认答案是 null，
        //    而 SimpleForwardingServerCallListener 的构造器 checkNotNull(delegate) ⇒ 不打桩会 NPE。
        doReturn(listener).when(interceptor).interceptCall(any(), any(), any());

        // ⚠️ 关掉 CPU 降载：本用例要验的是「链上真的有包装层」，
        //    而降载一旦命中就会短路（handler 不执行）⇒ 断言会变得不确定。
        //    降载本身由 service/common 的 GoStyleRpcSheddingInterceptorTest 单独覆盖。
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getCpuShedding().setEnabled(false);

        GrpcServerConfigurer configurer =
                new FavoriteRpcApplication().rpcInterceptorConfigurer(interceptor, props);

        ServerBuilder<?> builder = mock(ServerBuilder.class);
        ArgumentCaptor<ServerInterceptor> captor = ArgumentCaptor.forClass(ServerInterceptor.class);

        configurer.accept(builder);

        verify(builder).intercept(captor.capture());
        ServerInterceptor chained = captor.getValue();

        // 真跑一遍被交给 builder 的那个拦截器。
        ServerCall<Object, Object> call = mock(ServerCall.class);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        doReturn(listener).when(next).startCall(any(), any());

        // ⚠️⚠️ **必须打桩 getMethodDescriptor()**：链上的 breaker 用它取隔离 key
        //    （Go: {@code breakerName := path.Join(cc.Target(), method)}，
        //     {@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/breakerinterceptor.go:15}）
        //    ⇒ Mockito 默认返回 null ⇒ 链里抛 NPE。
        //    ⚠️ 注意 {@code info.FullMethod} 是 **grpc-java** 的 {@code MethodInfo}，**不是 Go 侧写法**。
        //    ⇒ 而那个 NPE 会被**最外层的 recover 静默兜住**（关掉调用、返回 INTERNAL），
        //    现象是「业务 interceptor 零交互」—— 看起来像"链没生效"，真因却在链里面。
        when(call.getMethodDescriptor()).thenReturn(MethodDescriptor.<Object, Object>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("/favorite.FavoriteService/AddFavorite")
                .setRequestMarshaller(new NoopMarshaller())
                .setResponseMarshaller(new NoopMarshaller())
                .build());

        chained.interceptCall(call, new Metadata(), next);

        ArgumentCaptor<ServerCall<Object, Object>> callCaptor =
                ArgumentCaptor.forClass(ServerCall.class);
        verify(interceptor).interceptCall(callCaptor.capture(), any(), any());

        assertThat(callCaptor.getValue())
                .as("链上有 breaker/shedding ⇒ 业务 interceptor 拿到的必须是包装过的 call。"
                        + "若拿到的是原始 call，说明包装层没挂上 —— 即 breaker/shedding 形同虚设。")
                .isNotSameAs(call);
    }

    /** 最小可用的 marshaller（测试不真的序列化）。 */
    private static final class NoopMarshaller implements MethodDescriptor.Marshaller<Object> {
        @Override
        public java.io.InputStream stream(Object value) {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }

        @Override
        public Object parse(java.io.InputStream stream) {
            return new Object();
        }
    }
}
