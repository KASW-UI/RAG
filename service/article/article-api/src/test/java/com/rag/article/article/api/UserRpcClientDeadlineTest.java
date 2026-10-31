package com.rag.article.article.api;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code user-rpc} 方向的<b>per-client 单次调用预算</b> —— DRIFT-ARTICLE-API-002 的守门网。
 *
 * <h2>Go 出处（三段，缺一不可）</h2>
 * <pre>
 * // ① 声明点：go-zero v1.10.2 · zrpc/config.go:31
 *   Timeout int64 `json:",default=2000"`
 *
 * // ② yaml：service/article/api/etc/article-api.yaml:20-24 —— UserRpcConf **没有** Timeout
 *   ⇒ 走 ① 的 tag 默认 = 2000ms
 *   （⚠️ 同一份 yaml 里 ArticleRpcConf.Timeout 是**显式** 70000 ⇒ 两个 client 差 35 倍，
 *     而逐字段对 yaml 时看不出这个差别 —— AGENTS.md §3.3.1 点名的形态）
 *
 * // ③ 消费点：zrpc/client.go:70-71 → zrpc/internal/client.go:83（buildUnaryInterceptors）
 * //            → zrpc/internal/clientinterceptors/timeoutinterceptor.go:25
 *   ctx, cancel := context.WithTimeout(ctx, t)
 * </pre>
 *
 * <h2>⚠️ 语义是「取父子 deadline 中较早的」，不是「覆盖」</h2>
 * <p>Go 的 {@code context.WithTimeout(ctx, 2s)}：父 ctx（HTTP 70s 预算）剩余不足 2s 时，
 * <b>父 ctx 赢</b>。⇒ Java 侧必须显式算 {@code min}。
 *
 * <p>⚠️ <b>grpc-java 不能替我们做这个合并</b>：
 * {@code io.grpc.CallOptions.withDeadline} 的字节码是 {@code builder.deadline = deadline}
 * —— <b>直接覆盖</b>（grpc-api 1.66.0 实测）。
 * ⇒ 若写成 {@code applyTo(stub).withDeadlineAfter(2, SECONDS)}，HTTP 剩余预算会被顶掉
 * （剩 0.5s 时反而给 2s）⇒ 请求可能超出 HTTP 总预算。
 *
 * <h2>🆕 2026-11-02 · G3 lane：断言对象从「域侧内联副本」换成「共享层机制 ＋ 域 yml 参数」</h2>
 * <p>本类原先断言的是 {@code UserRpcClient} <b>自带</b>的那份内联副本
 * （{@code min(剩余, 2s)} + 域侧私有常量）⇒ ⇒ 那是<b>第二个上界来源</b>
 * ⇒ ⇒ 该副本已<b>剪除</b>（上界改由共享层 {@code GoStyleOutboundDeadlineInterceptor}
 * 承担，域侧只配 {@code cap-millis-by-service.user.UserService: 2000}）。
 * <p>⇒ ⇒ <b>本类的语义一条不减</b>（仍是「user-rpc 方向的上界是 2s、且按 min 取更早者」），
 * 只是<b>被观测的对象</b>从「stub 上自带的 deadline」换成
 * 「<b>真实出向调用</b>在服务端被观测到的 deadline」。
 * <p>⚠️ <b>为什么必须走「真发调用 + 服务端观测」</b>：拦截器在<b>出向调用发起时</b>才施加
 * deadline ⇒ ⇒ 断言「stub 上有没有 deadline」<b>测不到</b>它
 * ⇒ ⇒ 且那样断言会<b>假绿</b>（域侧副本若还在，stub 上照样有 deadline）。
 */
class UserRpcClientDeadlineTest {

    /** Go 侧的 per-client 上限：{@code zrpc/config.go:31} 的 tag 默认 2000ms。 */
    private static final long GO_CLIENT_TIMEOUT_MILLIS = 2000L;

    /** 本域 yml 里那个 per-service 键的<b>下游 gRPC 服务全名</b>。 */
    private static final String USER_SERVICE_NAME = "user.UserService";

    private Server server;
    private ManagedChannel channel;

    /** 服务端观测到的「客户端给的 deadline 剩余毫秒」；{@code -1} = 服务端<b>没看到</b>任何 deadline。 */
    private final AtomicLong serverObservedDeadlineMillis = new AtomicLong(-1L);

    @AfterEach
    void tearDown() {
        GoStyleRequestDeadline.clear();
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("无 HTTP 预算 ⇒ user-rpc 的 deadline 是 **2000ms**（Go 的 zrpc tag 默认）")
    void withoutHttpBudgetUsesGoClientDefault() throws Exception {
        callUserRpc();

        long observed = serverObservedDeadlineMillis.get();

        assertTrue(observed > 0L && observed <= GO_CLIENT_TIMEOUT_MILLIS,
                "无 HTTP 预算时 deadline 应为 2000ms 之内，实际=" + observed);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("HTTP 预算 10s ⇒ deadline 仍是 **2000ms**（取 min，不是 10s）")
    void httpBudgetLargerThanClientCapStillUsesClientCap() throws Exception {
        beginBudget(10_000L);

        callUserRpc();

        long observed = serverObservedDeadlineMillis.get();

        assertTrue(observed > 0L && observed <= GO_CLIENT_TIMEOUT_MILLIS,
                "客户端 2s 上限应压过 10s 的 HTTP 预算，实际=" + observed);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("HTTP 预算 500ms ⇒ deadline 是 **500ms**（取 min：grpc-java 的 withDeadline 是覆盖，必须自己算）")
    void httpBudgetSmallerThanClientCapWins() throws Exception {
        beginBudget(500L);

        callUserRpc();

        long observed = serverObservedDeadlineMillis.get();

        assertTrue(observed > 0L && observed <= 500L,
                "HTTP 剩余预算更短时应以它为准（Go 的 context.WithTimeout 取父子较早者），实际=" + observed);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("HTTP 预算已耗尽 ⇒ 出向调用 **立即到期**（不能因为 2s 上限而重新获得时间）")
    void exhaustedHttpBudgetStaysExpired() throws Exception {
        // 已经过去的时刻 ⇒ 预算耗尽
        GoStyleRequestDeadline.begin(System.nanoTime() - TimeUnit.SECONDS.toNanos(1L));
        Channel capped = startEchoUserRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> UserServiceGrpc.newBlockingStub(capped)
                        .getUser(GetUserReq.newBuilder().setUid(1L).build()),
                "预算耗尽时出向调用必须立即失败");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "预算耗尽 ⇒ 失败码必须是 DEADLINE_EXCEEDED（Go 侧 ctx 已到期同样是 DeadlineExceeded）");
        assertTrue(elapsedMillis <= 1_000L,
                "预算耗尽时应立即失败（实测耗时 = " + elapsedMillis
                        + "ms）⇒ ⇒ 若实测接近 2000ms ⇒ 说明 2s 上界把已耗尽的预算又续上了。");
    }

    @Test
    @DisplayName("Go 的上限就是 2000ms（防「有人顺手改成 30s」—— yaml 里看不到这个数）")
    void goClientTimeoutConstantIsTwoSeconds() {
        GoStyleProtectionProperties props = domainProperties(null);

        assertEquals(GO_CLIENT_TIMEOUT_MILLIS,
                props.getOutboundDeadline().getCapMillisByService().get(USER_SERVICE_NAME),
                "🔴 域 yml 的 rag.protection.outbound-deadline.cap-millis-by-service." + USER_SERVICE_NAME
                        + " 必须是 2000；zrpc/config.go:31 的 tag 默认是 2000；改了它就是一条未登记的语义变更"
                        + "⇒ ⇒ 实测绑出的表 = " + props.getOutboundDeadline().getCapMillisByService());
    }

    // ==================== helpers ====================

    private void beginBudget(long millis) {
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis));
    }

    /**
     * 真发一次 {@code GetUser}（下游立即返回），并把<b>服务端观测到的</b>客户端 deadline 记下来。
     *
     * <p>⚠️ 用的是<b>裸 stub</b> ⇒ ⇒ 观测到的上界<b>只可能</b>来自共享层拦截器。
     */
    private void callUserRpc() throws Exception {
        Channel capped = startEchoUserRpc(domainProperties(null));
        UserServiceGrpc.newBlockingStub(capped)
                .getUser(GetUserReq.newBuilder().setUid(1L).build());
    }

    /**
     * 起一个 in-process {@code user-rpc} 下游：<b>立即</b>返回，并记录服务端看到的客户端 deadline。
     *
     * <p>⚠️ 装的是<b>生产拦截器</b> {@code GoStyleOutboundDeadlineInterceptor}
     * ⇒ ⇒ 与 devh 的装配动作同一条（{@code AbstractChannelFactory#createChannel}
     * 最后一步 {@code ClientInterceptors.interceptForward(channel, interceptors)}
     * ⇒ ⇒ 拦截器装在 channel 上 ⇒ ⇒ 裸 stub 同样经过）。
     */
    private Channel startEchoUserRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new UserServiceGrpc.UserServiceImplBase() {
                    @Override
                    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
                        Deadline d = Context.current().getDeadline();
                        serverObservedDeadlineMillis.set(d == null ? -1L : d.timeRemaining(TimeUnit.MILLISECONDS));
                        responseObserver.onNext(GetUserResp.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).build();
        return ClientInterceptors.intercept(channel, new GoStyleOutboundDeadlineInterceptor(props));
    }

    /**
     * 用本域<b>自己的</b> {@code application.yml}（<b>磁盘上的那份</b>）绑出
     * {@link GoStyleProtectionProperties}。
     */
    private static GoStyleProtectionProperties domainProperties(Map<String, Object> envOverrides) {
        ConfigurableEnvironment env = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            for (PropertySource<?> ps : loader.load("domain-application-yml",
                    new FileSystemResource(domainYamlPath()))) {
                env.getPropertySources().addLast(ps);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（" + domainYamlPath() + "）", e);
        }
        if (envOverrides != null && !envOverrides.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("simulated-os-env", envOverrides));
        }
        ConfigurationPropertySources.attach(env);
        Binder binder = new Binder(ConfigurationPropertySources.get(env),
                new PropertySourcesPlaceholdersResolver(env));
        return binder.bind("rag.protection", GoStyleProtectionProperties.class)
                .orElseGet(GoStyleProtectionProperties::new);
    }

    /** 本域 {@code application.yml} 的<b>磁盘路径</b>。 */
    private static Path domainYamlPath() {
        return moduleRoot().resolve("src").resolve("main").resolve("resources").resolve("application.yml");
    }

    /** 定位本模块的源码根（含 {@code src/main/java}）—— <b>不依赖 surefire 的工作目录</b>。 */
    private static Path moduleRoot() {
        try {
            Path loc = Path.of(UserRpcClientDeadlineTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path p = Files.isDirectory(loc) ? loc : loc.getParent();
            if (p != null && "target".equals(p.getFileName().toString())) {
                p = p.getParent();
            }
            if (p != null && Files.isDirectory(p.resolve("src").resolve("main").resolve("java"))) {
                return p;
            }
        } catch (Exception ignored) {
            // 落到下面的兜底
        }
        Path cur = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && cur != null; up++) {
            if (Files.isDirectory(cur.resolve("src").resolve("main").resolve("java"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到本模块的源码根（src/main/java）"
                + "⇒ ⇒ 配置面判据失效（判据自己的问题，不是被测物的问题）");
    }
}
