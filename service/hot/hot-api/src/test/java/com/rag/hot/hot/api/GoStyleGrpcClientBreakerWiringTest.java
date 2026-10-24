package com.rag.hot.hot.api;

import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 证明 gRPC client 熔断拦截器<b>真的挂进了 hot-api 的两个下游 channel</b>，而不是「只是个 Bean」。
 *
 * <h2>为什么 hot 域也要有这一份</h2>
 * <p>拦截器本体在 {@code service/common}（跨域），common 域有自己的
 * {@code GoStyleGrpcClientBreakerInterceptorTest}（验判据与算法）。
 * ⚠️ 但那些用例**直接 {@code new} 拦截器，完全不经过 Spring**
 * ⇒ 若拦截器因为任何原因没被 starter 收进 channel（Bean 未被扫描、名字不匹配、
 * starter 版本行为差异），它们**照样全绿**。
 * <p>而「本域的 channel 有没有被装配」是**逐域**的事实 ——
 * {@code follow-api} 与 {@code message-api} 各有一份同名的 wiring 测试，
 * <b>hot-api 此前没有</b> ⇒ 这正是 {@code docs/DRIFT/hot/api.md} 的 <b>U4/D8</b> 缺口。
 *
 * <h2>本类比 message-api 那份多断言的一条：<b>本域的两个 channel 真被建出来了</b></h2>
 * <p>「全局拦截器在 registry 里」只证明<b>starter 会把它装进它建的每一个 channel</b>；
 * 若 hot-api 的两个 {@code @GrpcClient} 根本没建 channel，那这条断言就<b>悬空</b>。
 * ⇒ 用 {@link GrpcChannelFactory#getConnectivityState()}（channel 名 → 连接状态）
 * 断言 {@code hot-rpc} / {@code article-rpc} **两个 channel 都存在**。
 * <p>⚠️ <b>因此本类刻意不 mock 掉 client</b>：mock 掉 {@code @GrpcClient} 字段
 * ⇒ channel 不会被创建 ⇒ 上面的断言会变成「断言一个不存在的东西」而恒真/恒假，无意义。
 * ⚠️ devh 的 channel 是<b>懒连接</b> ⇒ 不 mock 也不会在启动期真去连 {@code 127.0.0.1:65535}。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code zrpc/internal/config.go:14} ——
 *       {@code ClientMiddlewaresConf.Breaker bool `json:",default=true"`} ⇒ 默认开启；</li>
 *   <li>{@code zrpc/internal/client.go:112-113} ——
 *       {@code if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }}；</li>
 *   <li>{@code zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18} —— 拦截器本体。</li>
 * </ul>
 */
@SpringBootTest(
        classes = HotApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.hot-rpc.address=static://127.0.0.1:65535",
                "grpc.client.hot-rpc.negotiation-type=plaintext",
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class GoStyleGrpcClientBreakerWiringTest {

    @Autowired
    private GlobalClientInterceptorRegistry registry;

    @Autowired
    private GoStyleGrpcClientBreakerInterceptor interceptor;

    @Autowired
    private GrpcChannelFactory channelFactory;

    @Test
    @DisplayName("🔴 拦截器出现在 starter 的 GlobalClientInterceptorRegistry 中 ⇒ channel factory 会装配它")
    void interceptorIsRegisteredWithTheStarter() {
        assertThat(registry.getClientInterceptors())
                .as("拦截器必须在 starter 收集到的 ClientInterceptor 列表里；"
                        + "否则它只是个 Bean，永远不会进入 hot-api 的调用链。")
                .contains(interceptor);
    }

    @Test
    @DisplayName("🔴 hot-api 的两个下游 channel：factory 认识它们，且 @GrpcClient 锚点指向它们")
    void bothDownstreamChannelsAreDeclaredAndKnownToFactory() {
        // ① devh 的 factory 认识这两个 channel 名
        //    ✅ 变异 M-W1（把 @GrpcClient 改成 "hot-rpc-renamed"）⇒ **本条变红**
        //    ⇒ 说明它对「channel 名被改掉」是**敏感**的。
        //    ⚠️ 教训：第一次跑 M-W1 时它**没红**，当时误判成「这条不敏感」并把断言降级；
        //    真因是**变异脚本**用 `replace(old,new,1)` 命中了 javadoc 注释里的同名文本，
        //    **注解那行根本没被改到**。⇒ 变异脚本必须断言「只命中 1 处」。
        assertThat(channelFactory.getConnectivityState())
                .as("hot-api 的两个 @GrpcClient（hot-rpc / article-rpc）必须都在 factory 的 channel 名里")
                .containsKeys("hot-rpc", "article-rpc");

        // ② **装配锚点**：本域两个 client 上的 @GrpcClient 注解值必须与之一致
        //    ⇒ 直接读注解，不依赖 devh 内部实现 ⇒ 断言更稳。
        assertThat(grpcClientNameOf(HotRpcClient.class))
                .as("HotRpcClient 的 @GrpcClient 锚点变了 ⇒ 上面的 factory 断言会悬空")
                .isEqualTo("hot-rpc");
        assertThat(grpcClientNameOf(ArticleRpcClient.class)).isEqualTo("article-rpc");
    }

    /** 读 client 类里 {@code @GrpcClient} 注解的 channel 名。 */
    private static String grpcClientNameOf(Class<?> clientClass) {
        for (java.lang.reflect.Field field : clientClass.getDeclaredFields()) {
            net.devh.boot.grpc.client.inject.GrpcClient annotation =
                    field.getAnnotation(net.devh.boot.grpc.client.inject.GrpcClient.class);
            if (annotation != null) {
                return annotation.value();
            }
        }
        throw new IllegalStateException(clientClass.getName() + " 上没有 @GrpcClient 字段");
    }

    @Test
    @DisplayName("🔴 默认配置下熔断是**开启**的（Go zrpc/internal/config.go:14 default=true）")
    void breakerIsEnabledByDefault() {
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();

        assertThat(props.getBreaker().isEnabled())
                .as("默认必须开启，否则 hot-api 的下游调用就没有熔断保护")
                .isTrue();
    }
}
