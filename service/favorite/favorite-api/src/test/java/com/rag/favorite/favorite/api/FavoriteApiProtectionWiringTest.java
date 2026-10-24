package com.rag.favorite.favorite.api;

import com.rag.common.protection.GoStyleBreakerFilter;
import com.rag.common.protection.GoStyleCpuSheddingFilter;
import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.common.protection.GoStyleMaxBytesFilter;
import com.rag.common.protection.GoStyleMaxConnsFilter;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 证明 favorite-api 的**请求保护层真的装配上了**，而不是「配置里写了 / 类上标了注解」。
 *
 * <p><b>为什么这条不能省</b>：
 * {@link FavoriteRpcClient} 的类注释曾断言「client 熔断<b>尚未接入</b>」——
 * 那是一条 {@code Go: …} 形态的断言，而 AGENTS.md §3.3.2 明确指出
 * 「以 Go: … 措辞出现的断言本身就是高危项」，必须用**运行证据**判定，不能靠推断。
 * <p>Go 侧的对应事实（真源）：
 * <ul>
 *   <li>{@code zrpc/internal/config.go:16}
 *       {@code Breaker bool json:",default=true"}；</li>
 *   <li>{@code zrpc/internal/client.go:112-113}
 *       {@code if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }}；</li>
 *   <li>{@code rest/config.go:15-21} 的六个中间件开关（Breaker / Shedding / MaxConns / MaxBytes …）
 *       全部 {@code default=true}；</li>
 *   <li>{@code rest/config.go:52-56}：{@code MaxConns=10000}、{@code MaxBytes=1048576}、
 *       {@code CpuThreshold=900}。</li>
 * </ul>
 *
 * <p><b>验证路径</b>：读 starter 自己的 {@code GlobalClientInterceptorRegistry}
 * （它由 {@code GrpcClientAutoConfiguration#nettyGrpcChannelFactory} 消费），
 * 断言本拦截器确实在它收集到的列表里 —— 即 channel factory 会把它装进 channel。
 * 只断言「Bean 存在」是不够的：那只能证明容器里有这个对象，
 * 证明不了 channel factory 会不会消费它（AGENTS.md §3.3 的四类静默失效）。
 *
 * <p><b>⚠️ 本类的证据边界</b>：服务端四层 filter（MaxConns / Breaker / CpuShedding / MaxBytes）
 * 在此断言的是「<b>Bean 存在且 {@code @Order} 与 Go 的链序一致</b>」，
 * <b>不是</b>「真 Tomcat 的 filter chain 里真的跑了它」——
 * MockMvc <b>不走</b> servlet filter chain（{@code @SpringBootTest(webEnvironment = MOCK)}），
 * 这正是 AGENTS.md §3.3 记录过的坑（{@code check-plaintext400-headers.sh} 就是为它而生的）。
 * ⇒ 「filter 真在 chain 里生效」需要真容器验证，登记在
 * {@code docs/DRIFT/favorite/api.md} 的证据边界一节，<b>不要</b>把本类当它已验。
 */
@SpringBootTest(
        classes = FavoriteApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.favorite-rpc.address=static://127.0.0.1:65535",
                "grpc.client.favorite-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FavoriteApiProtectionWiringTest {

    @Autowired
    private GlobalClientInterceptorRegistry registry;

    @Autowired
    private GoStyleGrpcClientBreakerInterceptor interceptor;

    @Autowired
    private GoStyleProtectionProperties props;

    @Autowired
    private GoStyleMaxConnsFilter maxConnsFilter;

    @Autowired
    private GoStyleBreakerFilter breakerFilter;

    @Autowired
    private GoStyleCpuSheddingFilter cpuSheddingFilter;

    @Autowired
    private GoStyleMaxBytesFilter maxBytesFilter;

    @MockitoBean
    private FavoriteRpcClient rpcClient;

    @Test
    @DisplayName("client 熔断拦截器在 starter 的 GlobalClientInterceptorRegistry 中 ⇒ channel factory 会装配它")
    void clientBreakerInterceptorIsRegisteredWithTheStarter() {
        assertTrue(registry.getClientInterceptors().contains(interceptor),
                "拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入 favorite-rpc 的调用链，"
                        + "本模块就退化成「Go 有熔断、RAG 没有」的漂移状态。"
                        + "实际列表=" + registry.getClientInterceptors());
    }

    @Test
    @DisplayName("保护层默认开关与 Go 的 default=true 逐条一致（Breaker / Shedding / MaxConns / MaxBytes）")
    void protectionDefaultsMatchGo() {
        assertTrue(props.getBreaker().isEnabled(),
                "Go rest/config.go:16 Breaker default=true ⇒ 熔断必须默认开启");
        assertTrue(props.getCpuShedding().isEnabled(),
                "Go rest/config.go:17 Shedding default=true ⇒ CPU 降载必须默认开启");
        assertEquals(10000, props.getMaxConns(),
                "Go rest/config.go:52 MaxConns default=10000");
        assertEquals(1048576L, props.getMaxBytes(),
                "Go rest/config.go:53 MaxBytes default=1048576（1 MiB）");
        assertEquals(900L, props.getCpuShedding().getCpuThreshold(),
                "Go rest/config.go:56 CpuThreshold default=900");
    }

    @Test
    @DisplayName("服务端四层 filter 都装配成 Bean，且 @Order 与 Go 的链序一致")
    void serverSideProtectionFiltersAreBeansInGoChainOrder() {
        assertNotNull(maxConnsFilter, "MaxConns filter 必须是 Bean");
        assertNotNull(breakerFilter, "Breaker filter 必须是 Bean");
        assertNotNull(cpuSheddingFilter, "CpuShedding filter 必须是 Bean");
        assertNotNull(maxBytesFilter, "MaxBytes filter 必须是 Bean");

        // Go rest/server.go 的中间件顺序：MaxConns → Breaker → Shedding → MaxBytes
        // （RAG 侧用 @Order(HIGHEST_PRECEDENCE + n) 复刻，见各 Filter 类的 @Order 注解）
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1, orderOf(maxConnsFilter), "MaxConns 应最先");
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, orderOf(breakerFilter), "Breaker 次之");
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 3, orderOf(cpuSheddingFilter), "CpuShedding 再次");
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 6, orderOf(maxBytesFilter), "MaxBytes 最后");
    }

    private static int orderOf(Object filter) {
        Order order = AnnotationUtils.findAnnotation(filter.getClass(), Order.class);
        return order == null ? Ordered.LOWEST_PRECEDENCE : order.value();
    }
}
