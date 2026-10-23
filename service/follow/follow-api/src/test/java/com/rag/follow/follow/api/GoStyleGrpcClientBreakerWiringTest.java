package com.rag.follow.follow.api;

import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.follow.follow.api.client.FollowRpcClient;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证明 gRPC client 熔断拦截器<b>真的挂进了 follow-rpc 的 channel</b>，而不是「只是个 Bean」。
 *
 * <p><b>为什么这条对 follow 域尤其不能省</b>：
 * {@link FollowRpcClient} 的类注释里断言「带 {@code @GrpcGlobalClientInterceptor}，
 * 被扫到即自动注册，无需手工挂」——
 * <b>如果这条断言是假的，本模块就处于 message-api 记录的 DRIFT-MSGAPI-004 状态</b>
 * （Go 侧 zrpc 默认开熔断、RAG 侧没有），而且<b>没有任何报错</b>：
 * 编译通过、上下文能启动、测试全绿，只有下游故障时的可观察行为不同。
 * 这正是 AGENTS.md §3.3「注解存在 ≠ 机制生效」要求的验证层。
 *
 * <p><b>验证路径</b>：读 starter 自己的 {@code GlobalClientInterceptorRegistry}
 * （它由 {@code GrpcClientAutoConfiguration#nettyGrpcChannelFactory} 消费），
 * 断言本拦截器确实在它收集到的列表里 —— 即 channel factory 会把它装进 channel。
 * 只断言「Bean 存在」是不够的：那只能证明容器里有这个对象，
 * 证明不了 channel factory 会不会消费它。
 */
@SpringBootTest(
        classes = FollowApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.follow-rpc.address=static://127.0.0.1:65535",
                "grpc.client.follow-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class GoStyleGrpcClientBreakerWiringTest {

    @Autowired
    private GlobalClientInterceptorRegistry registry;

    @Autowired
    private GoStyleGrpcClientBreakerInterceptor interceptor;

    @MockitoBean
    private FollowRpcClient rpcClient;

    @Test
    @DisplayName("拦截器出现在 starter 的 GlobalClientInterceptorRegistry 中 ⇒ channel factory 会装配它")
    void interceptorIsRegisteredWithTheStarter() {
        assertTrue(registry.getClientInterceptors().contains(interceptor),
                "拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入 follow-rpc 的调用链，"
                        + "本模块就退化成「Go 有熔断、RAG 没有」的漂移状态。"
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