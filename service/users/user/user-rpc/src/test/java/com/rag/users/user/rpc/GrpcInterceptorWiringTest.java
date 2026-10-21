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
    @DisplayName("本模块的 GrpcServerConfigurer 必须把 interceptor 交给 ServerBuilder")
    void configurerPassesInterceptorToBuilder() {
        io.grpc.ServerInterceptor interceptor = mock(io.grpc.ServerInterceptor.class);
        GrpcServerConfigurer configurer =
                new UserRpcApplication().rpcInterceptorConfigurer(interceptor);

        ServerBuilder<?> builder = mock(ServerBuilder.class);

        configurer.accept(builder);

        verify(builder).intercept(same(interceptor));
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