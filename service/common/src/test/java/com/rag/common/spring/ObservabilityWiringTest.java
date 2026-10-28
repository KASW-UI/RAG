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
 *
 * <h2>🔴 2026-10-28：本类<b>验不到什么</b>（避免被当成「超时链路已锁」的证据）</h2>
 * <p>本类<b>只证「Bean 在容器里存在」</b> ⇒ ⇒ 它<b>证不了</b>「那个 interceptor 真的被挂到了
 * gRPC {@code ServerBuilder} 上」⇒ ⇒ 因为 devh starter 的三个工厂方法
 * （{@code shadedNettyGrpcServerFactory} / {@code nettyGrpcServerFactory} /
 * {@code inProcessGrpcServerFactory}）<b>全都只注入 {@code List<GrpcServerConfigurer>}</b>，
 * <b>从不</b>挂 {@code ServerInterceptor}（{@code javap} 证据见
 * {@link RpcServerInterceptorFactory} 的类注释）
 * ⇒ ⇒ <b>Bean 存在 + Configurer 缺失 = 静默失效</b>（不报错、不告警）。
 * <p>⇒ ⇒ 真正的「进了 {@code ServerBuilder}」由<b>域侧</b>的接线守门锁住
 * （<b>域侧写权，共享层不重复造</b>）：
 * {@code service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/GrpcInterceptorWiringTest.java}
 * ⇒ ⇒ <b>本类里那条 {@code grpcInterceptorBeanExists} 是弱断言</b>，已就地标注。
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
    @DisplayName("H2（🔴 弱断言）：RpcServerInterceptorFactory 产出的 ServerInterceptor 必须是容器里的真 Bean"
            + " —— 但这只证「Bean 存在」，**不证**它进了 ServerBuilder")
    void grpcInterceptorBeanExists() {
        ServerInterceptor interceptor = ctx.getBean(ServerInterceptor.class);
        assertThat(interceptor)
                .as("ServerInterceptor 未注册 = user-rpc/admin-rpc 无 gRPC span、无 deadline 传播、无 gRPC 超时")
                .isNotNull();
        // 🔴 2026-10-28：本断言的**边界**（此前本类被 RpcServerInterceptorFactory 的 javadoc
        //    当成「运行时后果」的证据引用 ⇒ 那是个**失效指针** ⇒ 已订正）。
        //
        //    ⚠️ **弱断言**：它只证「容器里有一个 ServerInterceptor Bean」，
        //       **零判别力**地回答不了「这个 Bean 有没有被交给 ServerBuilder」。
        //       ⇒ 因为 starter 三个工厂方法都只注入 List<GrpcServerConfigurer>，
        //         **从不**挂 ServerInterceptor（见 RpcServerInterceptorFactory 类注释的 javap 证据）
        //       ⇒ ⇒ Bean 存在 + Configurer 缺失 = **静默失效**（不报错）。
        //    ⇒ ⇒ 真正的「进了 ServerBuilder」由**域侧**接线守门锁住（域侧写权，共享层不重复造）：
        //         service/users/user/user-rpc/src/test/java/com/rag/users/user/rpc/GrpcInterceptorWiringTest.java
        //         （verify(builder).intercept(captor.capture()) + 链里真调得到）
        //    ⇒ ⇒ **别把本条当成「超时链路已锁」的证据**（AGENTS.md §3.3.3：
        //         断言「Bean 存在」≠ 断言机制生效）。
        //
        //    ⚠️ 为什么共享层**不**自己造这条装配级守门：
        //       ① GrpcServerConfigurer 的类型只在 net.devh:grpc-server-spring-boot-starter 里，
        //          而 service/common 依赖的是 **client** starter ⇒ 共享层引用不到该类型
        //          （且 service/common/pom.xml 不在共享层写权面内）；
        //       ② 共享层**没有** GrpcServerConfigurer 这个 Bean —— 它由各 rpc 模块的
        //          *RpcApplication 自己声明 ⇒ 共享层要测它只能自己造一个假 Configurer
        //          ⇒ ⇒ 那测的是**测试自己**，不是生产装配 ⇒ ⇒ 那是**另一种假绿**。
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