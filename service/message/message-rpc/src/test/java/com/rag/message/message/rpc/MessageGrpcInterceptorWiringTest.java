package com.rag.message.message.rpc;

import io.grpc.ServerBuilder;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import net.devh.boot.grpc.server.serverfactory.InProcessGrpcServerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * <b>这个测试类是为了钉死一个已经真实发生过的「假绿」</b>（AGENTS.md §6.6.1 DRIFT-MSG-003）。
 *
 * <p><b>坑的形态</b>：与 §6.5 H2 勘误小节记载的完全同型。message-rpc 此前
 * <pre>
 *   @SpringBootApplication(scanBasePackages = {"com.rag.message.message.rpc", "com.rag.common"})
 * </pre>
 * 没有任何 {@code @Import}、也没有任何 {@code GrpcServerConfigurer}。于是：
 * <ol>
 *   <li>{@code com.rag.common} 被全包扫描 ⇒ {@code RpcServerInterceptorFactory} 确实产出了
 *       {@code ServerInterceptor} Bean（断言 {@code ctx.getBean(ServerInterceptor.class)} 非空
 *       会一直是绿的）；</li>
 *   <li>但 <b>grpc-spring-boot-starter 3.1.0.RELEASE 从不自动把它挂到 server 上</b>
 *       （{@code javap} 证据见 user-rpc 的 {@code GrpcInterceptorWiringTest} 类注释）；</li>
 *   <li>⇒ {@code interceptCall} / {@code onHalfClose} 一次都没执行 ⇒
 *       {@code application.yml} 里配着、注释还解释了「对齐 go-zero 默认 2000ms」的
 *       {@code observability.rpc-timeout: 2s} <b>从未生效</b>。</li>
 * </ol>
 *
 * <p><b>后果远不止「少了几个 span」</b>：handler 线程上 {@code DeadlineState} 恒未设置
 * ⇒ 所有 {@code checkDeadline()} 守卫与 B-1 的 {@code setQueryTimeout} 取消在途 SQL 全部失效
 * ⇒ gRPC 慢查询无上界。
 *
 * <p><b>本测试的边界（诚实声明）</b>：它验证两件事 ——
 * ① 本模块的 {@code GrpcServerConfigurer} Bean 确实把**含业务 interceptor 的那条链**交给了 ServerBuilder
 * （⚠️ 2026-10-23 起交出去的不再是「那一个对象」而是 {@code recoverThen(businessInterceptor)}，
 * 见本类第一个用例的 javadoc；断言相应地从「同一个对象」升级为「**链里真的会被调用**」）；
 * ② starter 的 factory 构造器<b>没有</b> ServerInterceptor 形参（即必须自己写 Configurer）。
 * 「starter 会在 build 阶段 apply 所有 Configurer」属于 starter 自身契约，
 * 端到端层面由 {@code tools/difftest/} 的 message 域双端口对拍兜底。
 */
@DisplayName("§6.6.1 DRIFT-MSG-003 gRPC ServerInterceptor 必须真的挂到 server 上（不是只存在于容器里）")
class MessageGrpcInterceptorWiringTest {

    /**
     * <b>⚠️ 2026-10-23（DRIFT-MSG-V-016）本断言的形状变了，但语义没放松。</b>
     *
     * <p><b>为什么改</b>：DRIFT-MSG-V-016 要求 recover 挂在业务观测拦截器的<b>外层</b>
     * （Go 侧 {@code zrpc/internal/server.go:58} 的 append 序 ⇒ 先注册的最外层），
     * 于是 {@code rpcInterceptorConfigurer} 从
     * {@code server -> server.intercept(businessInterceptor)}
     * 改成 {@code server -> server.intercept(recoverThen(businessInterceptor))}。
     * ⇒ 交给 builder 的<b>不再是那一个对象</b>，老的
     * {@code verify(builder).intercept(same(interceptor))} 必然转红。
     *
     * <p><b>语义有没有放松？—— 没有，是从「同一个对象」升级为「链里真的会被调用」</b>：
     * <ol>
     *   <li>老写法只验「builder 收到了那个 Bean」—— 它<b>无法</b>区分
     *       「业务 interceptor 在链里」与「业务 interceptor 被换成了别的什么」；
     *       也无法发现「链被拼反了（recover 在内层）」。</li>
     *   <li>新写法把捕获到的链<b>真跑一遍</b>（{@code interceptCall}），
     *       再断言业务 interceptor 的 {@code interceptCall} <b>确实被调到</b>
     *       —— 这才是 DRIFT-MSG-003 的原意：「超时/trace 所在的 interceptor 真的在链上」。</li>
     * </ol>
     * ⇒ 保留「链顺序」语义的另一半由
     * {@code MessageRpcRecoverInterceptorWiringTest#recoverWrapsTheInnerInterceptor} 承担
     * （它让<b>内层</b>抛异常，只有 recover 在外层时才兜得住）。
     */
    @Test
    @DisplayName("本模块的 GrpcServerConfigurer 必须把 interceptor 交给 ServerBuilder（且链里真的能调到它）")
    void configurerPassesInterceptorToBuilder() {
        io.grpc.ServerInterceptor interceptor = mock(io.grpc.ServerInterceptor.class);
        io.grpc.ServerCall.Listener<Object> listener = new io.grpc.ServerCall.Listener<Object>() {};
        // ⚠️ 必须打桩：Mockito 对返回类型 ServerCall.Listener 的默认答案是 null，
        //    而 SimpleForwardingServerCallListener 的构造器 checkNotNull(delegate) ⇒ 不打桩会 NPE。
        doReturn(listener).when(interceptor).interceptCall(any(), any(), any());

        GrpcServerConfigurer configurer =
                new MessageRpcApplication().rpcInterceptorConfigurer(interceptor);

        ServerBuilder<?> builder = mock(ServerBuilder.class);
        ArgumentCaptor<io.grpc.ServerInterceptor> captor =
                ArgumentCaptor.forClass(io.grpc.ServerInterceptor.class);

        configurer.accept(builder);

        verify(builder).intercept(captor.capture());
        io.grpc.ServerInterceptor chained = captor.getValue();

        // 真跑一遍被交给 builder 的那个拦截器：
        // 它在被调用时必须**真的调用到**业务 interceptor（否则超时/trace 静默消失）。
        io.grpc.ServerCall<Object, Object> call = mock(io.grpc.ServerCall.class);
        io.grpc.ServerCallHandler<Object, Object> next = mock(io.grpc.ServerCallHandler.class);
        doReturn(listener).when(next).startCall(any(), any());

        chained.interceptCall(call, new io.grpc.Metadata(), next);

        verify(interceptor).interceptCall(same(call), any(), same(next));
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
                .as("若本条断言失败，说明升级了 grpc-server-spring-boot-starter、" +
                        "它开始自动注册 ServerInterceptor Bean —— 那时本模块的 Configurer 会变成重复注册，" +
                        "需回头删掉这里的 @Bean 并同步改写本测试")
                .isFalse();
    }

    /**
     * <b>结构性防回归（§3.3：配置写了不等于机制生效）。</b>
     *
     * <p>{@code MessageRpcApplication} 必须持有 {@code @Import(RpcServerInterceptorFactory.class)}，
     * 否则那个 Bean 根本不存在 —— 而<b>它的缺席同样不报任何错</b>，只是 gRPC 观测静默全失。
     * 用反射断言 {@code @Import} 列表，是为了让「哪天有人图省事把 @Import 删了」在这里变红。
     */
    @Test
    @DisplayName("MessageRpcApplication 必须 @Import RpcServerInterceptorFactory（否则 Bean 不存在且不报错）")
    void applicationMustImportRpcServerInterceptorFactory() throws Exception {
        var importAnnotation = MessageRpcApplication.class.getAnnotation(org.springframework.context.annotation.Import.class);
        assertThat(importAnnotation)
                .as("MessageRpcApplication 必须有 @Import；没有它 = common 的 Bean 一个都不装配")
                .isNotNull();

        var imported = java.util.Arrays.asList(importAnnotation.value());
        assertThat(imported)
                .as("@Import 列表必须含 RpcServerInterceptorFactory —— 它产出 ServerInterceptor Bean，" +
                        "漏掉 = observability.rpc-timeout 从未生效（DRIFT-MSG-003），且不报任何错")
                .contains(com.rag.common.spring.RpcServerInterceptorFactory.class);

        assertThat(imported)
                .as("@Import 列表必须含 ObservabilityProperties —— 它承载 observability.rpc-timeout")
                .contains(com.rag.common.spring.ObservabilityProperties.class);
    }
}