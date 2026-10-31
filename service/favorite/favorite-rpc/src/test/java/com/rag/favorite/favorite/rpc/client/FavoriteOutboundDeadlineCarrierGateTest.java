package com.rag.favorite.favorite.rpc.client;

import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.rpc.support.RealGrpcPort;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCalls;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.yaml.snakeyaml.Yaml;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>E65 域侧守门</b>：favorite-rpc 的出向出口
 * <b>真的继承</b>入向 deadline，且继承精度是<b>纳秒</b>（不是毫秒）。
 *
 *
 * <h2>🔴 2026-10-31 第十一轮 · 判据主体换成<b>共享层的全局客户端拦截器</b></h2>
 * <p>本类此前测的是<b>域内私有副本</b> {@code GoStyleClientCallTimeout}
 * （{@code capByClientTimeout(applyTo(stub))}）的算术 ⇒ ⇒ 本轮那份副本<b>已删除</b>
 * （<b>副本数 = 漂移面</b>：每份副本各自实现一遍「取 min」语义 ⇒ 极易有的取 min、
 * 有的写成覆盖 ⇒ ⇒ 共享层守门 {@code GoStyleClientCallTimeoutSingleSourceGateTest}
 * 的终态口径 = 「域侧 0 份，只配参数」）。
 * <br>⇒ ⇒ 本类现在测的是<b>生产同款</b>的施加上界的那一层：
 * <ul>
 *   <li><b>共享层</b> {@code GoStyleOutboundDeadlineInterceptor}
 *       （devh 的 {@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向 devh 调用上
 *       ⇒ ⇒ 连<b>裸 stub</b> 都过链）⇒ 上界 =
 *       {@code min(入向剩余预算, 客户端硬上界, 调用点自带)}；</li>
 *   <li><b>本域</b> 只配参数：{@code rag.protection.outbound-deadline.cap-millis=2000}
 *       （共享层默认 {@code capMillis=0} = <b>不启用</b> ⇒ ⇒ 不配 = 出向零期限 = 比 Go 松）
 *       ⇒ ⇒ 「配置面」那条用例把<b>配置面</b>也钉住（直接读本域 {@code application.yml}）。</li>
 * </ul>
 * <h2>🔴 为什么本域需要这一条（2026-10-31）</h2>
 * <p>本域此前的证据只到 <b>L2 源码级</b>：
 * <ul>
 *   <li>{@code docs/DRIFT/favorite/rpc.md} 第三轮新增段逐条核了「调用链 ⇒ 调用线程」
 *       （两条出向出口都在 {@code FavoriteDomainService} 链上 ⇒ gRPC handler 线程）；</li>
 *   <li>共享层的探针（{@code tools/semantic-probe/deadline-downstream/}）<b>测不到域内出口</b>
 *       —— 它的 RAG 半边<b>手搓 Netty channel</b>，绕开 devh 的全局拦截器装配
 *       ⇒ 探针的 {@code outgoing_carrier} 键<b>不能</b>当「域内出口没继承」的证据
 *       （那是<b>探针自己的构造</b>，不是域侧缺陷）；</li>
 * </ul>
 * ⇒ ⇒ 「域内出口确实继承载体」这件事<b>一直缺运行证据</b> ⇒ 本类补上。
 *
 * <h2>🔴 五条用例的分工（每条各有<b>专属</b>判别维度 · 缺一条都不够）</h2>
 * <ol>
 *   <li><b>真 wire + 真共享层拦截器</b> ⇒ 证明<b>载体在 handler 线程真的绑上了</b>；</li>
 *   <li><b>入向预算 &gt; 2s</b> ⇒ 证明 <b>2s 客户端封顶真的生效</b>（专治「不压任何 deadline」）；</li>
 *   <li><b>入向预算 &lt; 2s</b> ⇒ 证明出向上界<b>真的继承了入向剩余</b>（专治「无脑套 2s」）；</li>
 *   <li><b>已知绝对纳秒</b> ⇒ 证明<b>域内出向计算点不截断亚毫秒</b>（可确定性分辨毫秒版）；</li>
 *   <li><b>无载体</b> ⇒ 证明 2s 客户端封顶<b>无条件</b>在（对齐 Go 的拦截器级 {@code TimeoutInterceptor}）。</li>
 * </ol>
 *
 * <h2>🔴 为什么「继承」这一半<b>不是</b>本类的判据</h2>
 * <p>「继承请求 deadline」在生产链路上是 <b>devh 全局客户端拦截器链</b>给的
 * （{@code GoStyleOutboundDeadlineInterceptor}，{@code @GrpcGlobalClientInterceptor}，
 * {@code enabled} <b>默认 true</b>；devh {@code AbstractChannelFactory:61/:88/:95}
 * 把全局拦截器 {@code interceptForward} 到 channel 上 ⇒ 本域 main 代码零命中自建 channel
 * ⇒ 出向 stub <b>全部</b>是 {@code @GrpcClient} 注入 ⇒ <b>无一例外</b>）。
 * <br>⇒ ⇒ 本类**不重复**验那一层（那是<b>装配面</b>，归共享层 / global）；
 * ⇒ ⇒ 本类验的是<b>域内这一半</b>：<b>2s 客户端上界的计算点</b>（拦截器层的
 * {@code capMillis} <b>默认 0 = 不启用</b> ⇒ Go 的 2000ms 上界<b>只在域侧</b>）
 * ⇒ ⇒ 且域内那份 {@code applyTo} 是「{@code min(请求剩余, 2s)}」的<b>输入</b>（要先有请求剩余）。
 *
 * <h2>Go 出处（行为层，逐条对应）</h2>
 * <ul>
 *   <li><b>出向取 min</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
 *       {@code ctx, cancel := context.WithTimeout(ctx, t)} ⇒ 父 ctx 更早则<b>先到者生效</b>；</li>
 *   <li><b>纳秒精度</b>：同上一行 ⇒ {@code t} 是<b>纳秒</b> ⇒ Go 侧<b>不</b>做毫秒截断；</li>
 *   <li><b>无条件 2s</b>：{@code go-zero@v1.10.2/zrpc/internal/client.go:113-117}
 *       ＋ {@code go-zero@v1.10.2/zrpc/internal/config.go:15}
 *       ＋ {@code go-zero@v1.10.2/zrpc/config.go:31}；</li>
 *   <li><b>父 ctx 的 deadline 来自入向</b>：
 *       {@code go-zero@v1.10.2/zrpc/internal/serverinterceptors/timeoutinterceptor.go:34}。</li>
 * </ul>
 *
 * <h2>🔴 为什么第一条必须用<b>真 socket</b>而不是 in-process</h2>
 * <p>in-process transport <b>不走 wire</b> ⇒ 没有 {@code grpc-timeout} 头
 * ⇒ 服务端 {@code io.grpc.Context.current().getDeadline()} 读到 {@code null}
 * ⇒ 载体<b>根本不会绑</b> ⇒ 用 in-process 写这条 ⇒ <b>必然假红</b>。
 * <br>⇒ 本类用 {@link RealGrpcPort#pick()}（本域段 22100–22199）起<b>真 socket</b>。
 *
 * <h2>🔴 为什么载体在 handler 线程可见</h2>
 * <p>载体是 <b>ThreadLocal</b>（{@code GoStyleRequestDeadline.java:64}）
 * ⇒ 只在<b>绑它的那个线程</b>可见 ⇒ 共享层在 {@code RpcInterceptor.onHalfClose}
 * （{@code RpcInterceptor.java:494}）里绑定 ⇒ ⇒ handler 必须<b>跑在同一个线程</b>上。
 * <br>⇒ 链路：grpc-java 的 {@code ServerBuilder} <b>默认 executor = directExecutor</b>
 * ⇒ {@code ServerCalls.asyncUnaryCall} 最终用 {@code call.getExecutor()}
 * ⇒ ⇒ 本类<b>不</b>覆写 executor ⇒ 与生产形态<b>同款</b>（同线程）。
 *
 * <h2>🔴 判据设计的两处坑（已被实测踩过 · 防下一个人重踩）</h2>
 * <ol>
 *   <li><b>绝对剩余值不能当判据</b>：本机「两次 {@code System.nanoTime()} 调用之间」的机器开销
 *       约 <b>0.3–0.5ms</b> ⇒ 用绝对值判会<b>时红时绿</b>（假信号）
 *       ⇒ 必须拿「载体当下的剩余」当基线，只测<b>相对损失</b>；</li>
 *   <li><b>第一次跑要付类加载 + JIT 的钱</b>（实测 ≈ <b>0.15ms</b>）⇒ 那笔钱会混进「相对损失」
 *       ⇒ 必须<b>先热身</b>（跑一遍同款路径）再测，否则会<b>误判成精度损失</b>。</li>
 * </ol>
 */
class FavoriteOutboundDeadlineCarrierGateTest {

    /** 客户端给的 deadline（毫秒）—— 必须<b>穿过 wire</b>，才能证明服务端真的收到了。 */
    private static final long CLIENT_DEADLINE_MILLIS = 1_500L;

    /**
     * 另一个场景：客户端给的 deadline <b>长于</b> 2s 客户端封顶（5s）。
     *
     * <p>⚠️ <b>为什么必须有这个场景</b>：入向预算<b>短于</b> 2s 时，「继承入向预算」与
     * 「原样返回（不压任何 deadline）」算出来的上界<b>完全相同</b>
     * ⇒ ⇒ 单一「短预算」场景<b>分辨不了</b>这两种实现
     * ⇒ ⇒ 必须再加一个「长于 2s」的场景（正确实现 = 2s，不压的实现 = 入向预算）。
     */
    private static final long LONG_CLIENT_DEADLINE_MILLIS = 5_000L;

    /**
     * 第二条用例里手动绑定的剩余预算：<b>10.9ms</b>（刻意取非整毫秒 ⇒ 亚毫秒部分 = 0.9ms）。
     *
     * <p>⚠️ 亚毫秒部分要<b>足够大</b>才能与测量开销拉开距离
     * ⇒ 毫秒版（读 {@code remainingMillis()}）会把 10.9ms 砍成 <b>10ms</b> ⇒ 精度损失 ≈ 0.9ms
     * ⇒ 而纳秒版损失 ≈ 几 μs ⇒ ⇒ 两者相差两个数量级 ⇒ 判据稳。
     */
    private static final long KNOWN_BUDGET_NANOS = 10_900_000L;

    /** 精度损失的判据上界（纳秒）：<b>0.4ms</b>。 */
    private static final long PRECISION_LOSS_TOLERANCE_NANOS = 400_000L;

    private static Server server;
    private static ManagedChannel channel;

    /** handler 内观测到的载体状态（跨线程传递 ⇒ 必须用引用类型）。 */
    private static final AtomicReference<Boolean> carrierPresent = new AtomicReference<>();
    private static final AtomicReference<Long> carrierRemainingNanos = new AtomicReference<>();
    private static final AtomicReference<Long> outboundRemainingNanos = new AtomicReference<>();

    /**
     * 本域配置的客户端硬上界（毫秒）—— <b>必须等于 Go 侧的 {@code zrpc} client Timeout</b>。
     *
     * <p>⚠️ <b>这条常量与本域 {@code application.yml} 的
     * {@code rag.protection.outbound-deadline.cap-millis} 是同一个数的两处写法</b>
     * ⇒ ⇒ 「配置面」那条用例专门直接读 yml ⇒ ⇒ 两者不会悄悄分叉。
     */
    private static final long CONFIGURED_CAP_MILLIS = 2_000L;

    /**
     * 共享层的出向 deadline 拦截器（<b>生产同款类</b>）。
     *
     * <p>⚠️ <b>为什么这里直接 new 而不是从 Spring 上下文里取</b>：本类要测的是
     * <b>「这个拦截器 + 这条配置值 ⇒ 算出什么上界」</b> ⇒ ⇒ 用<b>同一个类</b> +
     * <b>同一个配置键的值</b>构造即可 ⇒ ⇒ 而「它真的被装配进 devh 的 registry 并装到
     * 每个 channel 上」那半由<b>共享层</b>的守门负责
     * （{@code GoStyleOutboundDeadlineRpcAssemblyTest} + 主干
     * {@code GoStyleDevhOutboundDeadlineAssemblyGateTest}）⇒ ⇒ 两边合起来才是完整证据链。
     */
    private static com.rag.common.protection.GoStyleOutboundDeadlineInterceptor sharedInterceptor;

    @BeforeAll
    static void startServer() throws Exception {
        int port = RealGrpcPort.pick();

        // 🔴 共享层的出向拦截器：**用本域配置的那个值**（2000ms）构造
        //    ⇒ ⇒ 「域侧只配参数」这条纪律在测试里也成立（不是另写一份算术）。
        GoStyleProtectionProperties props = new GoStyleProtectionProperties();
        props.getOutboundDeadline().setCapMillis(CONFIGURED_CAP_MILLIS);
        sharedInterceptor = new com.rag.common.protection.GoStyleOutboundDeadlineInterceptor(props);

        // ⚠️ 这里**只给 service name**（不给 `getServiceDescriptor()`）：
        //    带 descriptor 构造 ⇒ grpc-java 会要求「descriptor 里的每个方法都必须被绑定」
        //    ⇒ 而本类只关心 deadline 载体 ⇒ 只绑一个方法。
        ServerServiceDefinition service = ServerServiceDefinition.builder(
                        FavoriteServiceGrpc.getServiceDescriptor().getName())
                .addMethod(FavoriteServiceGrpc.getGetFavoriteFoldersMethod(),
                        ServerCalls.asyncUnaryCall((GetFavoriteFoldersReq request,
                                                   io.grpc.stub.StreamObserver<GetFavoriteFoldersResp> observer) -> {
                            // 🔴 下面几行跑在 **gRPC handler 线程** ⇒ 共享层 `RpcInterceptor.onHalfClose`
                            //    在**同一线程**上绑的 ThreadLocal 载体**在这里可见**。
                            carrierPresent.set(com.rag.common.observability.GoStyleRequestDeadline.isPresent());
                            carrierRemainingNanos.set(com.rag.common.observability.GoStyleRequestDeadline.remainingNanos());

                            // 域内真实消费点：**共享层拦截器算出来的那个出向上界**
                            //（生产同款：devh 把 `@GrpcGlobalClientInterceptor` 装到**每个**
                            //  出向 channel 上 ⇒ 连裸 stub 都过链 ⇒ 域侧调用点不再自己套 deadline）
                            // ⇒ 这里用**同一个拦截器** + 一个「只记 CallOptions、不真发」的假 channel
                            //    取它的产物（真发会打回本服务 ⇒ 递归）。
                            io.grpc.Deadline deadline = sharedOutboundDeadline();
                            outboundRemainingNanos.set(deadline == null
                                    ? -1L
                                    : deadline.timeRemaining(TimeUnit.NANOSECONDS));

                            observer.onNext(GetFavoriteFoldersResp.getDefaultInstance());
                            observer.onCompleted();
                        }))
                .build();

        // 🔴 真装共享层的拦截器：`Duration.ZERO` = 服务端**不配**自己的预算
        //    ⇒ 生效预算 = min(无, 客户端 deadline) = **客户端 deadline**
        //    ⇒ 与本仓生产的 rpc 组件形态一致（本域未配 `grpc.server.timeout`）。
        ServerServiceDefinition intercepted = ServerInterceptors.intercept(service,
                com.rag.common.observability.RpcInterceptor.newUnaryServerInterceptor(Duration.ZERO, Duration.ofSeconds(1)));

        server = ServerBuilder.forPort(port).addService(intercepted).build().start();
                // 🔴 **必须用显式 `dns:///` scheme，不能用 `forAddress(...)`**（2026-10-31 实测踩坑）：
                //    devh 的 `GrpcClientAutoConfiguration:124` 会在**起 Spring 上下文**时
                //    把自己的 `discovery` NameResolverProvider 注册进 **grpc-java 的进程级全局**
                //    `NameResolverRegistry.getDefaultRegistry()` ⇒ ⇒ **一旦本 JVM 里起过一次 devh 上下文，
                //    该注册就是进程级的** ⇒ ⇒ 之后**任何**无显式 scheme 的手搓 channel
                //    （`ManagedChannelBuilder.forAddress("127.0.0.1", P)`）都会被 devh 的 discovery resolver
                //    **接管** ⇒ ⇒ 把 `127.0.0.1:P` 当**服务名**去 Nacos 查 ⇒ ⇒ `UNAVAILABLE`
                //    ⇒ ⇒ 实测表现：同一个模块里，某个 `@SpringBootTest` 先跑 ⇒ 本条就红；
                //    单独跑本类 ⇒ 5/5 全绿 ⇒ ⇒ **「单跑绿、同跑红」＝ 上下文污染，不是判据问题**。
                //    ⇒ 显式 `dns:///` 直接选定 grpc-java 自带的 DNS resolver ⇒ 不受 devh 注册影响。
        channel = ManagedChannelBuilder.forTarget("dns:///127.0.0.1:" + port).usePlaintext().build();
    }


    /**
     * 取「<b>共享层拦截器</b>算出来的那个出向 deadline」—— 生产同款。
     *
     * <p>⚠️ <b>为什么用假 channel 而不是真发一次</b>：拦截器在 {@code interceptCall} 里就把
     * deadline 算好并交给 {@code next.newCall(...)} ⇒ ⇒ 只要 {@code next} 是「只记不真发」，
     * 就能取到<b>真正落到 wire 的那个</b> {@code CallOptions}
     * ⇒ ⇒ 既不用起第二个服务、也不会自递归。
     */
    private static io.grpc.Deadline sharedOutboundDeadline() {
        RecordingChannel recorder = new RecordingChannel();
        sharedInterceptor.interceptCall(UserServiceGrpc.getGetUserMethod(), CallOptions.DEFAULT, recorder);
        CallOptions captured = recorder.captured();
        return captured == null ? null : captured.getDeadline();
    }

    /**
     * 只记 {@code CallOptions} 的假 channel（取拦截器产物用）。
     *
     * <p>⚠️ {@code Channel#newCall} 的 {@code next} 语义：拦截器算完 deadline 后
     * 调 {@code next.newCall(method, effectiveOptions)} ⇒ ⇒ 这里收到的就是
     * <b>真正会落到 wire 的那份 options</b>。
     */
    private static final class RecordingChannel extends Channel {

        private final AtomicReference<CallOptions> captured = new AtomicReference<>();

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions) {
            captured.set(callOptions);
            return new ClientCall<ReqT, RespT>() {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    // 本守门只取 CallOptions ⇒ 不真发 ⇒ 什么都不做
                }

                @Override
                public void request(int numMessages) {
                }

                @Override
                public void cancel(String message, Throwable cause) {
                }

                @Override
                public void halfClose() {
                }

                @Override
                public void sendMessage(ReqT message) {
                }
            };
        }

        @Override
        public String authority() {
            return "recording-channel";
        }

        CallOptions captured() {
            return captured.get();
        }
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 一次「客户端带 deadline 调 → 读 handler 侧观测」的结果。 */
    private static final class Observation {
        final boolean carrierPresent;
        final long carrierRemainingNanos;
        final long outboundRemainingNanos;
        final Status.Code statusCode;

        Observation(boolean carrierPresent, long carrierRemainingNanos,
                    long outboundRemainingNanos, Status.Code statusCode) {
            this.carrierPresent = carrierPresent;
            this.carrierRemainingNanos = carrierRemainingNanos;
            this.outboundRemainingNanos = outboundRemainingNanos;
            this.statusCode = statusCode;
        }
    }

    /** 客户端带 deadline 调一次 ⇒ 返回 handler 线程上的观测。 */
    private static Observation callAndObserve(long clientDeadlineMillis) {
        carrierPresent.set(null);
        carrierRemainingNanos.set(null);
        outboundRemainingNanos.set(null);

        FavoriteServiceGrpc.FavoriteServiceBlockingStub stub =
                FavoriteServiceGrpc.newBlockingStub(channel)
                        .withDeadlineAfter(clientDeadlineMillis, TimeUnit.MILLISECONDS);

        Status.Code code = Status.Code.OK;
        try {
            stub.getFavoriteFolders(GetFavoriteFoldersReq.getDefaultInstance());
        } catch (StatusRuntimeException e) {
            code = e.getStatus().getCode();
        }

        return new Observation(
                Boolean.TRUE.equals(carrierPresent.get()),
                carrierRemainingNanos.get() == null ? -1L : carrierRemainingNanos.get(),
                outboundRemainingNanos.get() == null ? -1L : outboundRemainingNanos.get(),
                code);
    }

    @Test
    @DisplayName("🔴 真 wire + 真共享层拦截器 ⇒ 出向载体在 gRPC handler 线程真的绑上，且客户端的 deadline 真的到了服务端")
    void carrierIsReallyBoundOnHandlerThreadOverRealWire() {
        Observation o = callAndObserve(CLIENT_DEADLINE_MILLIS);

        assertThat(o.statusCode)
                .as("客户端调用必须成功返回（服务端没有超时调度器，超时只可能来自客户端自己的 deadline）")
                .isEqualTo(Status.Code.OK);

        assertThat(o.carrierPresent)
                .as("🔴 共享层 `RpcInterceptor.onHalfClose` 绑的载体必须在 **gRPC handler 线程**上可见"
                        + "⇒ 否则本域出向出口<b>根本没有继承入向 deadline</b>（⇒ E65 在本域不成立）")
                .isTrue();

        assertThat(o.carrierRemainingNanos)
                .as("载体剩余预算必须是**纳秒**且 > 0（-1 = 载体在位但没有预算 ⇒ 异常）")
                .isPositive();

        assertThat(o.carrierRemainingNanos)
                .as("🔴 客户端给的 %dms 必须**穿过 wire** 到了服务端", CLIENT_DEADLINE_MILLIS)
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(CLIENT_DEADLINE_MILLIS) - 300_000_000L);
    }

    @Test
    @DisplayName("🔴 入向预算**长于** 2s ⇒ 出向上界被压到 2s（客户端封顶真的生效，不是原样返回）")
    void outboundIsCappedAtTwoSecondsWhenInboundBudgetIsLonger() {
        Observation o = callAndObserve(LONG_CLIENT_DEADLINE_MILLIS);

        assertThat(o.statusCode).as("客户端调用必须成功返回").isEqualTo(Status.Code.OK);
        assertThat(o.carrierPresent).as("载体必须在 handler 线程可见").isTrue();
        assertThat(o.carrierRemainingNanos)
                .as("载体剩余应 ≈ %dms（客户端给了 > 2s 的预算 ⇒ 载体上界是它、不是 2s）",
                        LONG_CLIENT_DEADLINE_MILLIS)
                .isGreaterThan(TimeUnit.MILLISECONDS.toNanos(LONG_CLIENT_DEADLINE_MILLIS) - 300_000_000L);

        long twoSeconds = TimeUnit.MILLISECONDS.toNanos(CONFIGURED_CAP_MILLIS);
        assertThat(o.outboundRemainingNanos)
                .as("🔴 入向预算（%dms）> 2s ⇒ 出向上界必须是 **2s 客户端封顶**"
                        + "（若等于入向预算 ⇒ 说明「根本没压 2s」）", LONG_CLIENT_DEADLINE_MILLIS)
                .isLessThanOrEqualTo(twoSeconds);
        assertThat(o.outboundRemainingNanos)
                .as("出向上界必须贴近 2s（不能是别的数）")
                .isGreaterThan(twoSeconds - 100_000_000L);
    }

    @Test
    @DisplayName("🔴 入向预算**短于** 2s ⇒ 出向上界继承入向剩余（不是无脑套 2s）")
    void outboundInheritsInboundBudgetWhenItIsShorter() {
        Observation o = callAndObserve(CLIENT_DEADLINE_MILLIS);

        assertThat(o.outboundRemainingNanos)
                .as("🔴 域内出向出口的上界必须**继承**了载体预算（不是无脑套 2s）")
                .isPositive();
        assertThat(o.outboundRemainingNanos)
                .as("出向上界 = min(载体剩余, 2s) ⇒ 必须**不宽于**载体剩余")
                .isLessThanOrEqualTo(o.carrierRemainingNanos);
        assertThat(o.outboundRemainingNanos)
                .as("🔴 入向预算只有 %dms ⇒ 出向上界必须**明显小于 2s**（否则就是「无脑套 2s」）",
                        CLIENT_DEADLINE_MILLIS)
                .isLessThan(TimeUnit.MILLISECONDS.toNanos(2_000L));
    }

    @Test
    @DisplayName("🔴 域内出向计算点按**纳秒**取预算（毫秒版会把 10.9ms 砍成 10ms ⇒ 本条转红）")
    void outboundBudgetKeepsNanosecondPrecision() {
        long carrierNow;
        long outbound;
        long budgetNanos = KNOWN_BUDGET_NANOS;

        // 🔴🔴 **必须先热身**：整条路径（`bindAbsoluteNanos` → 拦截器读载体 →
        //    `Deadline.after` → `timeRemaining`）第一次跑要付
        //    **类加载 + JIT** 的钱（本机实测 ≈ 0.15ms）⇒ ⇒ 那笔钱会**混进 loss**
        //    ⇒ ⇒ 不热身就会读到「loss ≈ 0.15ms」而误判成精度损失。
        //    ⇒ 热身跑的是**同一条路径**（同样在 scope 内、同样带载体）。
        try (com.rag.common.observability.GoStyleRequestDeadline.Scope ignored = com.rag.common.observability.GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + budgetNanos)) {
            sharedOutboundDeadline();
        }

        try (com.rag.common.observability.GoStyleRequestDeadline.Scope ignored = com.rag.common.observability.GoStyleRequestDeadline.bindAbsoluteNanos(
                System.nanoTime() + budgetNanos)) {
            // 🔴 **基线**：此刻载体自己还剩多少（在同一线程、同一载体上直接读）。
            //    之所以要拿它当基线，是因为「两次 nanoTime 调用之间的机器开销」
            //    （本机实测约 0.3–0.5ms：类加载 + JIT）会**混进**绝对值里
            //    ⇒ ⇒ 判据必须测**相对损失**（出向比载体窄了多少），
            //    而不是测绝对剩余值（那会把测量开销误读成精度损失）。
            carrierNow = com.rag.common.observability.GoStyleRequestDeadline.remainingNanos();

            io.grpc.Deadline deadline = sharedOutboundDeadline();
            outbound = deadline == null ? -1L : deadline.timeRemaining(TimeUnit.NANOSECONDS);
        }

        assertThat(outbound)
                    .as("出向必须拿到 deadline（无 deadline = 出向完全没上限 ⇒ 与 Go 相反）")
                    .isPositive();
            assertThat(outbound)
                    .as("出向上界**不得比载体预算更宽**（Go 侧是 min(父 ctx 剩余, 2s)）")
                    .isLessThanOrEqualTo(carrierNow);

            long loss = carrierNow - outbound;
            assertThat(loss)
                    .as("🔴 出向上界相对载体预算的**精度损失**必须 < %d ns（0.4ms）"
                            + "；预算 %d ns（10.9ms）的亚毫秒部分若被毫秒截断 ⇒ 损失 ≈ %d ns"
                            + " ⇒ 毫秒版（读 remainingMillis）会转红",
                            PRECISION_LOSS_TOLERANCE_NANOS, budgetNanos, 900_000L)
                    .isLessThan(PRECISION_LOSS_TOLERANCE_NANOS);
    }

    @Test
    @DisplayName("🔴 线程上没有载体 ⇒ 出向仍套 2s 客户端封顶（Go 侧 TimeoutInterceptor 是拦截器级别的）")
    void noCarrierStillGetsTwoSecondClientCap() {
        assertThat(com.rag.common.observability.GoStyleRequestDeadline.isPresent())
                    .as("本用例的前提：当前线程**没有**载体（否则测的就不是「无预算」这条路径）")
                    .isFalse();

        io.grpc.Deadline deadline = sharedOutboundDeadline();

    assertThat(deadline)
                .as("🔴 没有请求预算时**也必须**有 deadline（否则出向调用完全没有上限 ⇒ 与 Go 相反）")
                .isNotNull();

        long remaining = deadline.timeRemaining(TimeUnit.NANOSECONDS);
        long twoSeconds = TimeUnit.MILLISECONDS.toNanos(CONFIGURED_CAP_MILLIS);
        assertThat(remaining)
                .as("出向上界必须是 2s 封顶（%d ns）", twoSeconds)
                .isLessThanOrEqualTo(twoSeconds);
        assertThat(remaining)
                .as("出向上界必须贴近 2s（不能是别的数）")
                .isGreaterThan(twoSeconds - 100_000_000L);
    }

    // ====================================================================================
    // ④ 配置面：域侧只配参数（共享层默认 0 = 不启用 ⇒ 不配 = 出向零期限 = 比 Go 松）
    // ====================================================================================

    @Test
    @DisplayName("🔴 本域 yml 配了 `rag.protection.outbound-deadline.cap-millis=2000`（不配 = 共享层机制没开）")
    @SuppressWarnings("unchecked")
    void domainConfiguresTheSharedTwoSecondCap() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertThat(in)
                    .as("本域 `application.yml` 必须在测试类路径上（src/main/resources）")
                    .isNotNull();

            Map<String, Object> root = new Yaml().load(in);
            assertThat(root).as("`application.yml` 必须能解析成 Map").isNotNull();

            Map<String, Object> rag = (Map<String, Object>) root.get("rag");
            assertThat(rag).as("🔴 `rag:` 段必须存在").isNotNull();

            Map<String, Object> protection = (Map<String, Object>) rag.get("protection");
            assertThat(protection).as("🔴 `rag.protection:` 段必须存在（共享层属性前缀）").isNotNull();

            Map<String, Object> outboundDeadline = (Map<String, Object>) protection.get("outbound-deadline");
            assertThat(outboundDeadline).as("🔴 `rag.protection.outbound-deadline:` 段必须存在").isNotNull();

            assertThat(outboundDeadline.get("cap-millis"))
                    .as("🔴 客户端硬上界必须是 **2000**（Go 侧 `zrpc/config.go:31` 的 "
                            + "`Timeout int64 json:\",default=2000\"`；本域 Go 侧 `etc/favorite.yaml` 的 `UserRpc:` / `ArticleRpc:` 两段**都没写** `Timeout` ⇒ 走这个默认值）"
                            + "；⚠️ 共享层默认 `capMillis=0` = 不启用 ⇒ 漏配 = 出向零期限 = 比 Go 松")
                    .isEqualTo(2000);
        }
    }

    // ====================================================================================
    // ⑤ 无副本：域侧不得再有第二份「客户端硬上界」实现（副本数 = 漂移面）
    // ====================================================================================

    @Test
    @DisplayName("🔴 本域不得再有「客户端硬上界」的私有副本（终态 = 域侧 0 份，只配参数）")
    void noDomainSidePrivateCopyOfTheClientCap() {
        Path copy = Path.of("service/favorite/favorite-rpc/src/main/java/com/rag/favorite/favorite/rpc/client/GoStyleClientCallTimeout.java");

        assertThat(Files.exists(copy))
                .as("🔴 域内又出现了 `GoStyleClientCallTimeout` 私有副本 ⇒ ⇒ **副本数 = 漂移面** ⇒ ⇒ "
                        + "正确形态 = **共享层给唯一实现**"
                        + "（`service/common/src/main/java/com/rag/common/protection/"
                        + "GoStyleOutboundDeadlineInterceptor.java`）**＋ 域侧只配参数**"
                        + "（`rag.protection.outbound-deadline.cap-millis`）⇒ ⇒ 请改成配参数，别再复制一份逻辑")
                .isFalse();
    }

    // ====================================================================================
    // ④ 配置面**真绑定**：不是「yml 文本里写了」，而是「真能绑进共享层读的那个对象」
    // ====================================================================================

    @Test
    @DisplayName("🔴 本域 yml 的 `cap-millis` 真的能绑定进共享层拦截器读的那个属性对象")
    void domainYamlBindsIntoSharedProtectionProperties() throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        java.util.List<PropertySource<?>> sources =
                loader.load("application", new ClassPathResource("application.yml"));
        assertThat(sources)
                .as("本域 `application.yml` 必须能被 Spring 的 yml 加载器读成 PropertySource")
                .isNotEmpty();

        MutablePropertySources mutable = new MutablePropertySources();
        sources.forEach(mutable::addLast);

        GoStyleProtectionProperties props = new Binder(ConfigurationPropertySources.from(mutable))
                .bind("rag.protection", GoStyleProtectionProperties.class)
                .orElseThrow(() -> new IllegalStateException("`rag.protection` 段没绑上"));

        assertThat(props.getOutboundDeadline().getCapMillis())
                .as("🔴 域侧 yml 的 `rag.protection.outbound-deadline.cap-millis` 必须真的绑定进共享层的 "
                        + "`GoStyleProtectionProperties.OutboundDeadline#capMillis`"
                        + "（共享层 `GoStyleOutboundDeadlineInterceptor` 的构造器读的就是它）"
                        + "；⚠️ 默认 0 = 不启用 ⇒ 绑不上 = 出向零期限 = 比 Go 松")
                .isEqualTo(2000L);

        assertThat(props.getOutboundDeadline().isEnabled())
                .as("🔴 出向 deadline 传播开关必须仍是**开**（共享层默认 `enabled=true`；"
                        + "Go 侧 `zrpc/internal/config.go:15` 的 `Timeout bool json:\",default=true\"`）"
                        + "⇒ ⇒ 本域不得把它关掉 ⇒ 否则 = 出向零期限 = 比 Go 松")
                .isTrue();
    }
}
