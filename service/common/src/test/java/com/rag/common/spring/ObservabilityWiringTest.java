package com.rag.common.spring;

import io.grpc.ServerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §3.3 强制测试：<b>真正装配 Spring 上下文</b>，验证 H1 / H2 的机制确实生效。
 *
 * <p>为什么这个测试类必须存在
 * <p>service/common 原有的 9 个测试类全是纯单元测试：不启动 Spring 容器、不跑
 * {@code @ConfigurationProperties} binder、不实例化任何 {@code @Component}。
 * 这类测试<b>按定义</b>测不到「Bean 根本没被注册」：
 * <ul>
 *   <li>H1：{@code HttpMiddlewareFilter} / {@code RpcServerInterceptorFactory} 把 timeout
 *       硬编码成 {@code Duration.ZERO}，而 {@code ObservabilityProperties} 当时连 timeout 字段都没有
 *       —— 纯单测里直接 new 一下 filter，照样「通过」</li>
 *   <li>H2：{@code UserRpcApplication} / {@code AdminRpcApplication} 的 {@code @Import} 列表漏了
 *       {@code RpcServerInterceptorFactory} —— 这只有真正起容器才看得见</li>
 * </ul>
 *
 * <p>本测试用 {@code @SpringBootTest} + 真实的 {@code @ConfigurationProperties} 绑定，
 * 把这两类缺陷重新变成可回归的失败。
 */
@SpringBootTest(classes = ObservabilityWiringTest.TestApp.class,
        properties = {
                "spring.application.name=common-wiring-test",
                "observability.http-timeout=3s",
                "observability.rpc-timeout=2s"
        })
@DisplayName("§3.3 observability 装配必须真正生效")
class ObservabilityWiringTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private ObservabilityProperties props;

    @Test
    @DisplayName("H1：observability.http-timeout / rpc-timeout 必须被 binder 绑定，而不是硬编码 ZERO")
    void timeoutPropertiesAreBound() {
        // 这两个字段在修复前根本不存在；即使存在，两个 factory 也传的是 Duration.ZERO
        assertThat(props.getHttpTimeout())
                .as("observability.http-timeout 应绑定为 3s（对齐 go-zero rest.RestConf.Timeout default=3000）")
                .isEqualTo(Duration.ofSeconds(3));
        assertThat(props.getRpcTimeout())
                .as("observability.rpc-timeout 应绑定为 2s（对齐 go-zero RpcServerConf.Timeout default=2000）")
                .isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("H2：RpcServerInterceptorFactory 产出的 ServerInterceptor 必须是容器里的真 Bean")
    void grpcInterceptorBeanExists() {
        ServerInterceptor interceptor = ctx.getBean(ServerInterceptor.class);
        assertThat(interceptor)
                .as("ServerInterceptor 未注册 = user-rpc/admin-rpc 无 gRPC span、无 deadline 传播、无 gRPC 超时")
                .isNotNull();
    }

    @Test
    @DisplayName("H2：HttpMiddlewareFilter 必须是容器里的真 Bean（@Component 生效而非仅声明）")
    void httpFilterBeanExists() {
        // 按**类型**断言，不按 bean 名：@Import 注册的 bean 名字是全限定类名，
        // 而组件扫描才会得到 "httpMiddlewareFilter" 这种短名。
        // 断言名字会把测试焊死在某一种注册方式上，而生产用的恰恰是 @Import。
        assertThat(ctx.getBeanNamesForType(HttpMiddlewareFilter.class))
                .as("HttpMiddlewareFilter 未注册 = HTTP 超时/响应分类/span 全部失效")
                .hasSize(1);
        assertThat(ctx.getBean(HttpMiddlewareFilter.class)).isNotNull();
    }

    @Test
    @DisplayName("M9：ObservabilityLifecycle 必须注册（否则 shutdown() 永远不被调用）")
    void lifecycleBeanExists() {
        assertThat(ctx.getBeanNamesForType(ObservabilityLifecycle.class))
                .as("ObservabilityLifecycle 未注册 = HttpMiddleware/RpcInterceptor 的线程池永不关闭")
                .hasSize(1);
        assertThat(ctx.getBean(ObservabilityLifecycle.class)).isNotNull();
    }

    /**
     * 最小测试应用：只装配被测 bean，不引业务代码。
     *
     * <p>刻意<b>不</b>用 {@code @ComponentScan} 全扫 {@code com.rag.common} ——
     * 那正是 §6.3 定案禁止的姿势。这里显式列出每个 bean，
     * 与 {@code UserRpcApplication} / {@code AdminRpcApplication} 的 {@code @Import} 写法保持一致，
     * 让「少 import 一个」在本测试里也能暴露出来。
     *
     * <p><b>也不加 {@code @EnableConfigurationProperties}</b>：{@code ObservabilityProperties} 自带
     * {@code @Component}，两种注册方式会各产生一个 Bean（名字分别是
     * {@code observability-com.rag.common.spring.ObservabilityProperties} 和
     * {@code com.rag.common.spring.ObservabilityProperties}），
     * 导致按类型注入时 {@code NoUniqueBeanDefinition}。
     * 生产模块走的是 {@code @Import} 路径，本测试必须照抄同一条路径，
     * 否则测的就不是线上真实装配了。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            ObservabilityProperties.class,
            HttpMiddlewareFilter.class,
            RpcServerInterceptorFactory.class,
            ObservabilityLifecycle.class
    })
    static class TestApp {
    }
}