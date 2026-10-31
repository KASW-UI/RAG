package com.rag.message.message.api;

import com.rag.message.message.api.MessageApiApplication;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.message.message.api.client.MessageRpcClient;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证明 gRPC client 熔断拦截器<b>真的挂进了 message-rpc 的 channel</b>，而不是「只是个 Bean」。
 *
 * <p><b>为什么单独一个类只证明「装配」这一件事</b>：
 * {@link GoStyleGrpcClientBreakerInterceptorTest} 验证的是判据与算法，
 * 但它直接 {@code new} 出拦截器，<b>完全不经过 Spring</b>。
 * 若拦截器因为任何原因没被 starter 收进 channel（Bean 未被扫描、
 * 名字不匹配、starter 版本行为差异），那些用例<b>照样全绿</b>。
 * 这正是 AGENTS.md §3.3「不能仅凭存在 {@code @Component} 就推断责任已被承担」的形态。
 *
 * <p><b>验证路径</b>：读 starter 自己的 {@code GlobalClientInterceptorRegistry}
 * （它由 {@code GrpcClientAutoConfiguration#nettyGrpcChannelFactory} 消费），
 * 断言本拦截器确实在它收集到的列表里 —— 即 channel factory 会把它装进 channel。
 */
@SpringBootTest(
        classes = MessageApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:65535",
                "grpc.client.message-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class GoStyleGrpcClientBreakerWiringTest {

    @Autowired
    private GlobalClientInterceptorRegistry registry;

    @Autowired
    private com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor interceptor;

    @MockitoBean
    private MessageRpcClient rpcClient;

    @Test
    @DisplayName("拦截器出现在 starter 的 GlobalClientInterceptorRegistry 中 ⇒ channel factory 会装配它")
    void interceptorIsRegisteredWithTheStarter() {
        assertTrue(registry.getClientInterceptors().contains(interceptor),
                "拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入 message-rpc 的调用链。"
                        + "实际列表=" + registry.getClientInterceptors());
    }

    @Test
    @DisplayName("默认配置下熔断是**开启**的（Go zrpc/internal/config.go:14 default=true）")
    void breakerIsEnabledByDefault() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        assertTrue(props.getBreaker().isEnabled(),
                "默认必须开启，否则就退回 DRIFT-MSGAPI-004 的状态");
    }
}