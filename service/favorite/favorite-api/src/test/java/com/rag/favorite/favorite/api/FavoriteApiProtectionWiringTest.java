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
 * 在此断言的是「<b>Bean 存在，且四个 filter 的<b>相对</b>次序与 Go 的链序一致</b>」，
 * <b>不是</b>「真 Tomcat 的 filter chain 里真的跑了它」——
 * MockMvc <b>不走</b> servlet filter chain（{@code @SpringBootTest(webEnvironment = MOCK)}），
 * 这正是 AGENTS.md §3.3 记录过的坑（{@code check-plaintext400-headers.sh} 就是为它而生的）。
 * ⇒ 「filter 真在 chain 里生效」需要真容器验证，登记在
 * {@code docs/DRIFT/favorite/api.md} 的证据边界一节，<b>不要</b>把本类当它已验。
 *
 * <p><b>⚠️ 为什么断言「相对链序」而不是「{@code @Order} 的绝对值」</b>（2026-10-26 合并当天改）：
 * <ul>
 *   <li>Go 的链序真源是 <b>go-zero v1.10.2</b> {@code rest/engine.go:130-168}
 *       （{@code buildChainWithNativeMiddlewares}）：
 *       Trace({@code :135}) → Log({@code :140}) → Prometheus({@code :143}) →
 *       <b>MaxConns({@code :146}) → Breaker({@code :149}) → Shedding({@code :152})</b> →
 *       Timeout → Recover → Metrics → <b>MaxBytes({@code :164})</b>。
 *       ⇒ 四个保护中间件的<b>相对次序</b> = MaxConns → Breaker → Shedding → MaxBytes。</li>
 *   <li>而这四个 filter 的 {@code @Order} <b>绝对值属于共享层</b>
 *       （{@code service/common} 的写权在 {@code global} 域）——
 *       {@code global} 第十九轮（T-8）为了把 {@code GoStyleTraceparentFilter}
 *       放到「CORS 之后、MaxConns 之前」（Go 侧 Trace 是链内第一个，{@code engine.go:135}
 *       ⇒ 被 MaxConns 拒成 503 的响应<b>也带</b> traceparent），
 *       把保护层四件套<b>整体后移</b>（{@code +1..+6} ⇒ {@code +4..+8}），
 *       <b>各 filter 之间的相对顺序完全不变</b>。</li>
 *   <li>⇒ 本类只锁「Go 侧真正承载语义的那部分」（相对次序），
 *       <b>不锁共享层的排版细节</b>（绝对数值）：
 *       绝对数值一旦被别的域<b>合法</b>调整，本域守门网就会<b>假红</b>
 *       （合并当天实测：旧断言 {@code +1/+2/+3/+6} 在 {@code +4/+5/+6/+8} 下直接红，
 *       而两侧的 Go 语义<b>完全一致</b>）⇒ 台账 {@code docs/DRIFT/favorite/api.md} §12。</li>
 *   <li>🔴 <b>本类不覆盖的不变量（归 {@code global} 域）</b>：
 *       「四个保护 filter 整块相对 Trace/Log/Prometheus/共享中间件的位置」
 *       ——那是共享层的链布局不变量，改法落在 {@code service/common}
 *       ⇒ 已回执 {@code global} 域，请其在<b>所有者</b>侧加门禁。</li>
 * </ul>
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
    @DisplayName("服务端四层 filter 都装配成 Bean")
    void serverSideProtectionFiltersAreBeans() {
        assertNotNull(maxConnsFilter, "MaxConns filter 必须是 Bean");
        assertNotNull(breakerFilter, "Breaker filter 必须是 Bean");
        assertNotNull(cpuSheddingFilter, "CpuShedding filter 必须是 Bean");
        assertNotNull(maxBytesFilter, "MaxBytes filter 必须是 Bean");
    }

    @Test
    @DisplayName("Go 链序：MaxConns 在 Breaker 之前（go-zero v1.10.2 rest/engine.go:146 → :149）")
    void maxConnsRunsBeforeBreaker() {
        int maxConns = orderOf(maxConnsFilter);
        int breaker = orderOf(breakerFilter);
        assertTrue(maxConns < breaker,
                "Go 的链序要求 MaxConns 先于 Breaker"
                        + "（go-zero v1.10.2 rest/engine.go:146 chn.Append(MaxConnsHandler) → :149 BreakerHandler）；"
                        + "实际 MaxConns=" + maxConns + " Breaker=" + breaker);
    }

    @Test
    @DisplayName("Go 链序：Breaker 在 CpuShedding 之前（go-zero v1.10.2 rest/engine.go:149 → :152）")
    void breakerRunsBeforeCpuShedding() {
        int breaker = orderOf(breakerFilter);
        int shedding = orderOf(cpuSheddingFilter);
        assertTrue(breaker < shedding,
                "Go 的链序要求 Breaker 先于 CpuShedding"
                        + "（go-zero v1.10.2 rest/engine.go:149 chn.Append(BreakerHandler) → :152 SheddingHandler）；"
                        + "实际 Breaker=" + breaker + " CpuShedding=" + shedding);
    }

    @Test
    @DisplayName("Go 链序：CpuShedding 在 MaxBytes 之前（go-zero v1.10.2 rest/engine.go:152 → :164）")
    void cpuSheddingRunsBeforeMaxBytes() {
        int shedding = orderOf(cpuSheddingFilter);
        int maxBytes = orderOf(maxBytesFilter);
        assertTrue(shedding < maxBytes,
                "Go 的链序要求 CpuShedding 先于 MaxBytes"
                        + "（go-zero v1.10.2 rest/engine.go:152 chn.Append(SheddingHandler) → :164 MaxBytesHandler）；"
                        + "实际 CpuShedding=" + shedding + " MaxBytes=" + maxBytes);
    }

    private static int orderOf(Object filter) {
        Order order = AnnotationUtils.findAnnotation(filter.getClass(), Order.class);
        return order == null ? Ordered.LOWEST_PRECEDENCE : order.value();
    }
}
