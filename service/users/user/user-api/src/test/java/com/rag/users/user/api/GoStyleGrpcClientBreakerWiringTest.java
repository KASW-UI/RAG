package com.rag.users.user.api;

import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.users.user.api.client.UserRpcClient;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证明 gRPC client 熔断拦截器<b>真的挂进了 user-rpc 的 channel</b>，而不是「只是个 Bean」。
 *
 * <p><b>为什么 user-api 侧不能省这一条</b>：
 * <ul>
 *   <li>同仓的 {@code follow-api} / {@code message-api} 都有这条接线测试，
 *       <b>唯独 user-api 没有</b>；</li>
 *   <li>而 {@link GoStyleGrpcClientBreakerInterceptor} 的类注释<b>自己就写着</b>：
 *       「这正是 AGENTS.md §3.3『不能仅凭存在 {@code @Component} 就推断责任已被承担』的形态」
 *       —— 纯单元测试直接 {@code new} 拦截器，8 条全绿，
 *       而真实的 Spring 装配下它<b>完全没生效</b>。</li>
 * </ul>
 * ⇒ 在 user-api 这一侧，「客户端熔断到底有没有落在真实调用链上」曾经是<b>没有证据</b>的空白。
 *
 * <p><b>验证路径</b>：读 starter 自己的 {@link GlobalClientInterceptorRegistry}
 * （它由 {@code GrpcClientAutoConfiguration#nettyGrpcChannelFactory} 消费），
 * 断言本拦截器确实在它收集到的列表里 —— 即 channel factory 会把它装进 channel。
 * 只断言「Bean 存在」是不够的：那只能证明容器里有这个对象，
 * 证明不了 channel factory 会不会消费它。
 *
 * <p><b>Go 出处（三段，缺一不可）</b>：
 * <pre>
 * // ① zrpc/internal/config.go:14            —— 默认开启
 * ClientMiddlewaresConf struct { ... Breaker bool `json:",default=true"` }
 * // ② zrpc/internal/client.go:112-113        —— 装配
 * if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }
 * // ③ zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18 —— 对位物
 * </pre>
 */
@SpringBootTest(
        classes = UserApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // UserRpcClient 整体被 @MockitoBean 替换；给个不会真连、也不走 Nacos name resolver 的地址
                "grpc.client.user-rpc.address=static://127.0.0.1:65535",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class GoStyleGrpcClientBreakerWiringTest {

    @Autowired
    private GlobalClientInterceptorRegistry registry;

    @Autowired
    private GoStyleGrpcClientBreakerInterceptor interceptor;

    @MockitoBean
    private UserRpcClient rpcClient;

    @Test
    @DisplayName("拦截器出现在 starter 的 GlobalClientInterceptorRegistry 中 ⇒ channel factory 会装配它")
    void interceptorIsRegisteredWithTheStarter() {
        assertTrue(registry.getClientInterceptors().contains(interceptor),
                "拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入 user-rpc 的调用链，"
                        + "本模块就退化成「Go 有客户端熔断、RAG 没有」的漂移状态。"
                        + "实际列表=" + registry.getClientInterceptors());
    }

    @Test
    @DisplayName("默认配置下熔断是**开启**的（Go zrpc/internal/config.go:14 default=true）")
    void breakerIsEnabledByDefault() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        assertTrue(props.getBreaker().isEnabled(),
                "默认必须开启，否则就退回「Go 有熔断、RAG 没有」的漂移状态");
    }
}
