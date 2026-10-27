package com.rag.follow.follow.api;

import com.rag.follow.follow.api.client.FollowRpcClient;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.Server;
import net.devh.boot.grpc.client.config.GrpcChannelProperties;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 DRIFT-G1-002 守门 —— follow-rpc 不可达时，follow-api 必须**起不来**；
 * follow-rpc 可达时，follow-api 必须**能起来**。
 *
 * <h2>Go 出处（逐层到消费点）</h2>
 * <pre>
 *   service/follow/api/internal/svc/servicecontext.go:27
 *       FollowRpc: followservice.NewFollowService(zrpc.MustNewClient(c.FollowRpc)),
 *   go-zero v1.10.2 zrpc/client.go:50-54
 *       func MustNewClient(c RpcClientConf, ...) Client {
 *           cli, err := NewClient(c, options...)
 *           logx.Must(err)          ← 失败即 panic，进程起不来
 *           return cli
 *       }
 *   go-zero v1.10.2 zrpc/internal/client.go:18 / :122-142
 *       dialTimeout = time.Second * 3
 *       timeCtx, cancel := context.WithTimeout(context.Background(), dialTimeout)
 *       conn, err := grpc.DialContext(timeCtx, server, options...)   // options 含 WithBlock()
 *       if err != nil { return fmt.Errorf("rpc dial: ... make sure rpc service %q is already started", ...) }
 * </pre>
 *
 * <p>⇒ Go 侧启动时<b>阻塞拨号 3s</b>，连不上就 panic ⇒ <b>进程根本起不来</b>。
 * ⇒ 修之前 RAG 侧是懒连接 ⇒ 表现为「<b>Pod Ready 但 100% 请求失败</b>」——
 * <b>就绪探针语义不同</b>，K8s 里排障方向完全不一样。
 *
 * <h2>实现落在 devh 的原生机制上（不是手写拨号检查）</h2>
 *
 * <p>{@code grpc.client.follow-rpc.immediate-connect-timeout: 3s}
 * ⇒ {@code AbstractChannelFactory.newManagedChannel → connectOnStartup}：
 * 阻塞等 READY，超时则 {@code throw new IllegalStateException("Can't connect to channel follow-rpc")}
 * ⇒ stub 创建失败 ⇒ <b>上下文启动失败</b>。
 *
 * <h2>🔴 为什么必须有「可达 ⇒ 能启动」的正向用例</h2>
 *
 * <p>只测「不可达 ⇒ 失败」会漏掉「fail-fast 被写成永远失败」这种反向回归
 * （admin-api 的同款测试第一版就踩过：手写 {@code stub.getChannel() instanceof ManagedChannel}
 * 在生产永远为 false ⇒ 任何环境下都起不来）。
 * ⇒ 本类同时覆盖「不可达 ⇒ 失败」「<b>可达 ⇒ 能启动</b>」「生产装配（全局 client 拦截器）在场」
 * 三件事。
 *
 * <h2>为什么用 {@link ApplicationContextRunner} 而不是 {@code @SpringBootTest}</h2>
 *
 * <p>{@code @SpringBootTest} 在上下文启动失败时会直接让<b>测试类</b>失败，
 * 无法在同一个类里断言「失败」这件事本身。
 * ⇒ 用 {@code ApplicationContextRunner}：它把「上下文启动失败」变成一个
 * <b>可断言的结果</b>（{@code ctx.hasFailed()}），且不引入 Tomcat / Nacos / PG 等无关依赖。
 */
class FollowApiStartupFailFastTest {

    /**
     * 找一个**确定没人监听**的端口 ⇒ 拨号必然失败（connection refused），
     * 而「连不上」正是本测试要制造的输入。
     */
    private static int findClosedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("找不到空闲端口", e);
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 1. 不可达 ⇒ 启动失败
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ follow-rpc 不可达 ⇒ 上下文启动失败（对齐 Go 的 zrpc.MustNewClient）")
    void followApiFailsToStartWhenFollowRpcUnreachable() {
        int closedPort = findClosedPort();

        new ApplicationContextRunner()
                .withUserConfiguration(GrpcClientOnlyConfig.class)
                .withPropertyValues(
                        "grpc.client.follow-rpc.address=static://127.0.0.1:" + closedPort,
                        "grpc.client.follow-rpc.negotiation-type=plaintext",
                        "grpc.client.follow-rpc.immediate-connect-timeout=3s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("follow-rpc")
                            .hasMessageContaining("Can't connect to channel");
                });
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. 可达 ⇒ 启动成功（正向守卫：守住「别把 fail-fast 写成永远失败」）
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ follow-rpc 可达 ⇒ 上下文**能启动**（fail-fast 不能写成永远失败）")
    void followApiStartsWhenFollowRpcReachable() {
        Server server = startRealGrpcServer();
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(GrpcClientOnlyConfig.class)
                    .withPropertyValues(
                            "grpc.client.follow-rpc.address=static://127.0.0.1:" + server.getPort(),
                            "grpc.client.follow-rpc.negotiation-type=plaintext",
                            "grpc.client.follow-rpc.immediate-connect-timeout=10s") // 可达用例用**宽预算**：3s 是**生产值**（另有 yml 静态断言守住），本用例只验「可达 ⇒ 不该启动失败」；冷 JVM + 满负载下 TCP+HTTP/2 握手可能超 3s ⇒ **偶发红**（2026-10-27 admin-api 侧实测同类现象：全量红 / 隔离绿 ⇒ 属测试自身预算过紧，不是产品缺陷）
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(FollowRpcClient.class);
                    });
        } finally {
            server.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 3. 生产装配（全局 client 拦截器在场）⇒ 上面两条依然成立
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ 生产装配（全局 client 拦截器）下：follow-rpc 不可达 ⇒ 仍然启动失败")
    void failFastStillWorksWithGlobalClientInterceptor() {
        int closedPort = findClosedPort();

        new ApplicationContextRunner()
                .withUserConfiguration(GrpcClientOnlyConfig.class, WithGlobalInterceptorConfig.class)
                .withPropertyValues(
                        "grpc.client.follow-rpc.address=static://127.0.0.1:" + closedPort,
                        "grpc.client.follow-rpc.negotiation-type=plaintext",
                        "grpc.client.follow-rpc.immediate-connect-timeout=3s")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("Can't connect to channel");
                });
    }

    @Test
    @DisplayName("✅ 生产装配（全局 client 拦截器）下：follow-rpc 可达 ⇒ 仍然能启动")
    void startsWithGlobalClientInterceptor() {
        Server server = startRealGrpcServer();
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(GrpcClientOnlyConfig.class, WithGlobalInterceptorConfig.class)
                    .withPropertyValues(
                            "grpc.client.follow-rpc.address=static://127.0.0.1:" + server.getPort(),
                            "grpc.client.follow-rpc.negotiation-type=plaintext",
                            "grpc.client.follow-rpc.immediate-connect-timeout=10s") // 可达用例用**宽预算**：3s 是**生产值**（另有 yml 静态断言守住），本用例只验「可达 ⇒ 不该启动失败」；冷 JVM + 满负载下 TCP+HTTP/2 握手可能超 3s ⇒ **偶发红**（2026-10-27 admin-api 侧实测同类现象：全量红 / 隔离绿 ⇒ 属测试自身预算过紧，不是产品缺陷）
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(FollowRpcClient.class);
                    });
        } finally {
            server.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // 4. yml 静态断言：机制必须**已武装**，且预算是 3s
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ application.yml 真的写上了 immediate-connect-timeout=3s（devh 默认 0=关闭）")
    void applicationYmlArmsImmediateConnectTimeout() {
        List<PropertySource<?>> sources = loadApplicationYml();
        assertThat(sources).isNotEmpty();

        // ① 属性必须存在
        String raw = (String) sources.get(0).getProperty("grpc.client.follow-rpc.immediate-connect-timeout");
        assertThat(raw)
                .as("application.yml 必须写 grpc.client.follow-rpc.immediate-connect-timeout，"
                        + "否则 devh 的启动期拨号检查处于「默认 0 = 关闭」状态，"
                        + "fail-fast 会静默消失（现象退化成「Pod Ready 但 100%% 请求失败」）")
                .isNotNull();

        // ② 绑定后的**有效值**必须正好是 3s（对齐 go-zero zrpc/internal/client.go:18）
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));
        GrpcChannelProperties channelProps = binder
                .bind("grpc.client.follow-rpc", GrpcChannelProperties.class)
                .orElseGet(GrpcChannelProperties::new);
        assertThat(channelProps.getImmediateConnectTimeout())
                .as("拨号预算必须对齐 go-zero 的 dialTimeout=3s")
                .isEqualTo(Duration.ofSeconds(3));
        assertThat(FollowRpcClient.DIAL_TIMEOUT).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("✅ FollowRpcClient 的启动期自检真的会在「未武装」时让启动失败（删掉 yml 那行 ⇒ 红）")
    void afterPropertiesSetRejectsUnarmedChannel() {
        new ApplicationContextRunner()
                .withUserConfiguration(GrpcClientOnlyConfig.class)
                // 刻意**不**给 immediate-connect-timeout ⇒ devh 默认 0 = 关闭
                .withPropertyValues(
                        "grpc.client.follow-rpc.address=static://127.0.0.1:" + findClosedPort(),
                        "grpc.client.follow-rpc.negotiation-type=plaintext")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("immediate-connect-timeout");
                });
    }

    // ────────────────────────────────────────────────────────────────────
    // helpers
    // ────────────────────────────────────────────────────────────────────

    private static List<PropertySource<?>> loadApplicationYml() {
        try {
            return new YamlPropertySourceLoader()
                    .load("follow-api-application", new ClassPathResource("application.yml"));
        } catch (IOException e) {
            throw new IllegalStateException("读不到 classpath:application.yml", e);
        }
    }

    /**
     * 起一个**真的** gRPC server（不注册任何 service 也要）。
     *
     * <p>为什么非要真的 server：{@code immediate-connect-timeout} 的判据是
     * 「channel 到达 READY」，而 READY 的前提是<b>TCP + HTTP/2 握手完成</b>
     * ⇒ 光开一个 {@code ServerSocket} 不够，必须是 gRPC server。
     */
    private static Server startRealGrpcServer() {
        try {
            return NettyServerBuilder.forPort(0).build().start();
        } catch (IOException e) {
            throw new IllegalStateException("起不了本地 gRPC server", e);
        }
    }

    /**
     * 只装「follow-rpc 的 gRPC client」这一个 bean 的最小上下文。
     *
     * <p>刻意<b>不</b>引入整个 {@code FollowApiApplication}：
     * 那会把 Nacos / datasource / Redis / Tomcat 全拖进来，
     * 而本测试要验的只是「启动期可达性检查会不会让启动失败」这一件事。
     */
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration.class)
    static class GrpcClientOnlyConfig {

        @Bean
        FollowRpcClient followRpcClient(GrpcChannelsProperties channelProperties) {
            return new FollowRpcClient(channelProperties);
        }
    }

    /**
     * 复刻生产的<b>拦截器装配</b>。
     *
     * <p>生产里 follow-api 依赖 service/common，而那里的
     * {@code GoStyleGrpcClientBreakerInterceptor} 是 {@code @Component + @GrpcGlobalClientInterceptor}
     * ⇒ devh 会用 {@code ClientInterceptors.interceptForward} 把 channel 包一层。
     * <p>⚠️ 不装它 ⇒ 测的是裸 channel，与生产不符 ⇒ 假绿。
     */
    @Configuration(proxyBeanMethods = false)
    static class WithGlobalInterceptorConfig {

        @Bean
        NoopGlobalInterceptor noopGlobalInterceptor() {
            return new NoopGlobalInterceptor();
        }
    }

    /** 一个什么都不做的全局 client 拦截器 —— 只为把 channel 包起来，复现生产装配。 */
    @GrpcGlobalClientInterceptor
    static class NoopGlobalInterceptor implements ClientInterceptor {
        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return next.newCall(method, callOptions);
        }
    }
}
