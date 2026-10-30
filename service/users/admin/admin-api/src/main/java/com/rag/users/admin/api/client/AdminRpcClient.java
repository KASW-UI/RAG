package com.rag.users.admin.api.client;

import com.rag.users.admin.proto.AdminServiceGrpc;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * admin-rpc gRPC client —— 持有 AdminServiceBlockingStub。
 *
 * <p>对应 Go: service/user/admin/api/internal/svc/servicecontext.go 中的 AdminRpc（go-zero 自动生成）。
 *
 * <p>框架职责:
 * <ul>
 *   <li>{@code @GrpcClient("admin-rpc")} 自动注入阻塞式 stub（grpc.client.admin-rpc.address=discovery:///admin-rpc）</li>
 *   <li>devh grpc-client-spring-boot-starter 通过 Nacos 自动发现 admin-rpc 多实例（修 DRIFT-NEW-001）</li>
 *   <li>取代了原 internal.svc.ServiceContext.AdminRpc 字段</li>
 *   <li>所有 Service 类通过 AdminRpcClient.adminRpc() 调用下游 admin-rpc</li>
 * </ul>
 *
 * <p>Phase 4 填肉阶段：可加 retry / circuit breaker / deadline propagation（DRIFT-018）。
 *
 * <h2>🔴 DRIFT-ADMINAPI-009：启动期必须能连上 admin-rpc，否则进程起不来</h2>
 *
 * <p><b>Go 出处（逐层到消费点）</b>：
 * <pre>
 *   service/user/admin/api/internal/svc/servicecontext.go:27
 *       AdminRpc: adminservice.NewAdminService(zrpc.MustNewClient(c.AdminRpc)),
 *   go-zero v1.10.2 zrpc/client.go:50-54
 *       func MustNewClient(c RpcClientConf, ...) Client {
 *           cli, err := NewClient(c, options...)
 *           logx.Must(err)          ← 失败即 panic
 *           return cli
 *       }
 *   go-zero v1.10.2 zrpc/internal/client.go:122-142（消费点）
 *       timeCtx, cancel := context.WithTimeout(context.Background(), dialTimeout)   // dialTimeout = 3s（:18）
 *       conn, err := grpc.DialContext(timeCtx, server, options...)                 // options 含 WithBlock()
 *       if err != nil { return fmt.Errorf("rpc dial: %s, ... make sure rpc service %q is already started", ...) }
 * </pre>
 *
 * <p>⇒ Go 侧 {@code grpc.DialContext} 带 {@code WithBlock()}（{@code NonBlock} 默认 false ⇒
 * {@code zrpc/client.go:65-69} 走 {@code WithBlock()} 分支）⇒
 * <b>启动时阻塞拨号直到连上，或 3s 超时后返回 error ⇒ {@code logx.Must} ⇒ panic ⇒ 进程根本起不来。</b>
 *
 * <p>⇒ <b>RAG 侧必须复刻这个「启动期硬失败」语义</b>。
 * 修之前：{@code @GrpcClient} 是<b>懒连接</b>，Nacos 不可达时进程照样启动、
 * 就绪探针报 Ready，但每个请求各自失败 ⇒ K8s 里表现为
 * 「Pod Ready 但 100% 请求失败」，而 Go 表现为「Pod 起不来」——
 * <b>就绪探针语义不同</b>，是 §3.1 要求覆盖的行为差异。
 *
 * <h3>实现方式：用 devh 的<b>原生</b> {@code immediate-connect-timeout}，不自己写拨号检查</h3>
 *
 * <p>devh grpc-client-spring-boot-starter 3.1.0 的
 * {@code AbstractChannelFactory.newManagedChannel(name)} 里已经有这套机制：
 * <pre>
 *   final Duration timeout = this.properties.getChannel(name).getImmediateConnectTimeout();
 *   if (!timeout.isZero()) {
 *       connectOnStartup(name, channel, timeout);
 *   }
 *   ...
 *   private void connectOnStartup(String name, ManagedChannel channel, Duration timeout) {
 *       channel.getState(true);                                  // 请求连接
 *       final CountDownLatch readyLatch = new CountDownLatch(1);
 *       waitForReady(channel, readyLatch);
 *       boolean connected = readyLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
 *       if (!connected) {
 *           throw new IllegalStateException("Can't connect to channel " + name);   // ← 启动直接失败
 *       }
 *   }
 * </pre>
 * 它的 javadoc 写得很直白：<i>"If the connection fails, the stub will fail to create with an
 * exception which in turn causes the application context startup to fail."</i>
 * ⇒ <b>与 Go 的 {@code zrpc.MustNewClient} 逐条同型</b>：都是「连不上 ⇒ 进程起不来」，
 * 而不是「起来但请求全失败」。
 *
 * <p>本模块在 {@code application.yml} 里<b>只对 admin-rpc 这一个 channel</b> 打开它
 * （{@code grpc.client.admin-rpc.immediate-connect-timeout: 3s}），
 * <b>不设全局</b> ⇒ 理由：Go 侧 admin-api 的 {@code ServiceContext} 里<b>只有</b> {@code AdminRpc}
 * 一个下游依赖（{@code service/user/admin/api/internal/svc/servicecontext.go:24-28}），
 * 本模块 yml 里那条 {@code grpc.client.user-rpc} 是<b>没有对应 Go 依赖的遗留配置</b>
 * （本模块无任何 {@code @GrpcClient("user-rpc")} 注入点）
 * ⇒ 若把超时设成全局，就会凭空多出一条 Go 侧不存在的启动期硬依赖。
 *
 * <h3>🔴 踩坑：<b>不能</b>用 {@code stub.getChannel()} 自己读连通性状态</h3>
 *
 * <p>本类第一版实现是手写拨号检查，判据是
 * {@code stub.getChannel() instanceof ManagedChannel} + {@code managed.getState(true)}。
 * <b>它在单元测试里全绿，在真栈里让 admin-api <b>完全起不来</b></b>。根因：
 * <ul>
 *   <li>{@code devh} 的 {@code AbstractChannelFactory.createChannel(name)} 最后一行是
 *       {@code ClientInterceptors.interceptForward(channel, interceptors)} ⇒
 *       stub 拿到的是<b>包装后</b>的 channel；</li>
 *   <li>而 {@code io.grpc.Channel}（grpc-api 1.63.0）只声明了 {@code newCall} 与 {@code authority}
 *       —— <b>根本没有 {@code getState}</b>；{@code getState} 只存在于
 *       {@code io.grpc.ManagedChannel}；</li>
 *   <li>{@code io.grpc.ClientInterceptors$InterceptorChannel} 直接继承 {@code io.grpc.Channel}
 *       且<b>不暴露</b> {@code getState} ⇒ 包装后<b>拿不到</b>连通性状态。</li>
 * </ul>
 * <p>⇒ 而生产里<b>必然</b>有全局拦截器：
 * {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor} 是
 * {@code @Component + @GrpcGlobalClientInterceptor}，admin-api 依赖 service/common ⇒
 * <b>channel 一定被包装</b> ⇒ {@code instanceof ManagedChannel} <b>永远为 false</b>。
 * <p>⚠️ 为什么单测没抓到：{@code AdminApiStartupFailFastTest} 用的是
 * {@code ApplicationContextRunner} + 只引 {@code GrpcClientAutoConfiguration} 的<b>最小上下文</b>，
 * <b>没有装配那个全局拦截器</b> ⇒ 测出来的 channel 是裸 {@code ManagedChannel}
 * ⇒ <b>测试验的装配和生产不是同一个</b>（§3.3「纯 Mockito / 最小上下文的单测不算数」的又一种形态）。
 * <p>⇒ 结论：<b>启动期连通性检查必须落在 devh 自己持有的原始 channel 上</b>，
 * 而 devh 的 {@code immediate-connect-timeout} 正好就是这么做的 ⇒ 用它，不要自己摸 channel。
 *
 * <h3>为什么还要留 {@link #afterPropertiesSet()} 这一道自检</h3>
 *
 * <p>{@code immediate-connect-timeout} 的<b>默认值是 {@code Duration.ZERO}（= 关闭）</b>
 * （{@code GrpcChannelProperties.DEFAULT_IMMEDIATE_CONNECT}）⇒
 * <b>「yml 里没写」就等于「机制没武装」</b>。
 * 若有人删掉那一行，启动期硬失败会<b>静默消失</b>，而现象是
 * 「Pod Ready 但 100% 请求失败」——<b>与 Go 的差异又回来了，而且没人会发现</b>。
 * ⇒ 本方法在上下文启动的最后一刻断言<b>机制确实已武装</b>，否则直接让启动失败。
 * 这是 §3.3「配置里没写 ≠ 没开」的直接落地。
 */
@Component
public class AdminRpcClient implements InitializingBean {

    /** devh 里的 channel 名 = {@code @GrpcClient("admin-rpc")} 的值。 */
    private static final String CHANNEL_NAME = "admin-rpc";

    /**
     * 拨号预算 —— 对齐 go-zero {@code zrpc/internal/client.go:18} 的 {@code dialTimeout = time.Second * 3}。
     *
     * <p>⚠️ 这个值<b>不</b>由本类持有，而是由 {@code application.yml} 的
     * {@code grpc.client.admin-rpc.immediate-connect-timeout} 持有 ⇒
     * 本常量只是<b>断言用</b>的期望值（{@code AdminApiStartupFailFastTest} 会核对 yml 里的真实值）。
     */
    public static final Duration DIAL_TIMEOUT = Duration.ofSeconds(3);

    private static final Logger LOG = LoggerFactory.getLogger(AdminRpcClient.class);

    @GrpcClient("admin-rpc")
    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;

    /**
     * 仅为「拿到 channel 名对应的有效配置」而存在 —— 用来断言
     * {@code immediate-connect-timeout} 确实已武装（见类注释末段）。
     */
    private final GrpcChannelsProperties channelProperties;

    public AdminRpcClient(GrpcChannelsProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    @Override
    public void afterPropertiesSet() {
        Duration timeout = channelProperties.getChannel(CHANNEL_NAME).getImmediateConnectTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(String.format(
                    "admin-rpc 的启动期拨号检查**未武装**：grpc.client.%s.immediate-connect-timeout=%s。"
                            + "对齐 Go service/user/admin/api/internal/svc/servicecontext.go:27 的 "
                            + "zrpc.MustNewClient（连不上 ⇒ 进程起不来）需要它为正数；"
                            + "devh 的默认值是 0（= 关闭），"
                            + "机制一旦被静默关掉，现象会退化成「Pod Ready 但 100%% 请求失败」。"
                            + "请在 service/users/admin/admin-api/src/main/resources/application.yml 的 "
                            + "grpc.client.%s.immediate-connect-timeout 处显式写 %dms。",
                    CHANNEL_NAME, timeout, CHANNEL_NAME, DIAL_TIMEOUT.toMillis()));
        }
        LOG.info("admin-rpc 启动期拨号检查已武装：immediate-connect-timeout={}ms"
                + "（对齐 go-zero zrpc/internal/client.go:18 dialTimeout=3s）", timeout.toMillis());
    }

    /**
     * <b>zrpc 客户端超时 = 2s</b>。
     *
     * <p><b>Go 出处</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
     * —— {@code RpcClientConf.Timeout int64 `json:",default=2000"`}
     * ⇒ <b>默认 2000ms</b>；且 Go 侧 {@code service/user/admin/api/etc/admincenter.yaml}
     * 的 {@code AdminRpc:} 段<b>没有显式 Timeout</b>（只有 {@code Etcd}）⇒ ⇒
     * <b>Go 侧出向 admin-rpc 的调用恒带 2s 客户端超时</b>。
     *
     * <p><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
     * —— {@code context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>
     * ⇒ ⇒ <b>Go 的出向 deadline = min(入站 ctx 剩余, 2s 客户端超时)</b>。
     *
     * <p><b>语义等价论证</b>：{@link com.rag.common.observability.GoStyleRequestDeadline#applyTo}
     * 负责「入站剩余」（无入站预算时原样返回 ⇒ 剩 {@link Long#MAX_VALUE}），
     * {@link #capByClientTimeout} 负责「2s 上界」⇒ 取更早者
     * ⇒ <b>与 Go 的 {@code context.WithTimeout(ctx, 2s)} 逐字同形</b>。
     *
     * <p><b>为什么 RAG 侧必须自己补这一层</b>：Go 的 2s 客户端超时是
     * <b>go-zero client interceptor 级</b>的（框架自带）；RAG 侧 {@code devh} 注入的
     * <b>裸 stub 没有 call deadline</b> ⇒ 下游挂死时请求一路顶到 HTTP 超时分支
     * ⇒ 对外 <b>503 {@code Request Timeout}</b>；Go 侧则是
     * <b>HTTP 200 + {@code {"code":1015,"msg":"服务繁忙"}}</b>
     * ⇒ ⇒ <b>可观察差异</b>（状态码 + 响应体 + 时刻三者都不同）。
     *
     * <p><b>⚠️ 差异的准确边界（不要读宽）</b>：RAG 侧下游 {@code admin-rpc} <b>自己</b>带服务端 2s 超时
     * （{@code service/users/admin/admin-rpc/src/main/resources/application.yml:191} = {@code rpc-timeout: 2s}；
     * 接线在位由 {@code check-grpc-interceptor-wiring.py} = EXIT=0 实测确认）
     * ⇒ ⇒ <b>形态①「请求到达下游、业务挂死」</b>：RAG 由<b>服务端</b> 2s 收口 ⇒ {@code DEADLINE_EXCEEDED}
     * ⇒ 本域映射 ⇒ <b>1015</b> ＋ HTTP 200 ⇒ <b>与 Go 同形</b>；
     * ⇒ ⇒ <b>形态②「请求没到达下游」</b>（进程不可达 / 网络黑洞 / 连接建好但无响应）：
     * Go 由<b>客户端</b> 2s 收口（= min(剩余 3s ctx, 2s)，3s 预算见
     * {@code service/common/observability/http.go:30-34} ＋ {@code :53-56}）⇒ 200 + 1015；
     * RAG 修前<b>无客户端收口</b> ⇒ 只能等 3s HTTP 预算 ⇒ 503
     * ⇒ ⇒ <b>形态②才是本条要修的可观察差异</b>。
     * ⇒ 同款修法在 follow 域已落地（{@code service/follow/follow-api/.../FollowRpcClient.java:231}）
     * ⇒ 本处照该形状落地。
     */
    public static final long CLIENT_TIMEOUT_MILLIS = 2000L;

    /**
     * 取出出向 admin-rpc 的 stub —— <b>每次调用都重新套一次 deadline</b>。
     *
     * <p>⚠️ <b>不要缓存返回的 stub</b>：deadline 按<b>调用时刻</b>算
     * ⇒ 缓存会把「第一次调用时的预算」固化下来。
     */
    public AdminServiceGrpc.AdminServiceBlockingStub adminRpc() {
        return capByClientTimeout(
                com.rag.common.observability.GoStyleRequestDeadline.applyTo(adminServiceBlockingStub));
    }

    /**
     * 把 deadline 压到「入站 ctx 预算」与「Go 侧 2s 客户端超时」里的<b>较小者</b>。
     *
     * <p>包可见 ⇒ 供本包的守门测试直接验证 min 语义（AGENTS.md §3.3.3）。
     */
    static AdminServiceGrpc.AdminServiceBlockingStub capByClientTimeout(
            AdminServiceGrpc.AdminServiceBlockingStub stub) {
        io.grpc.Deadline deadline = stub.getCallOptions().getDeadline();
        long remainingNanos = deadline == null
                ? Long.MAX_VALUE
                : deadline.timeRemaining(java.util.concurrent.TimeUnit.NANOSECONDS);
        long capNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(CLIENT_TIMEOUT_MILLIS);
        if (remainingNanos <= 0L) {
            return stub.withDeadlineAfter(1L, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
        return stub.withDeadlineAfter(Math.min(remainingNanos, capNanos),
                java.util.concurrent.TimeUnit.NANOSECONDS);
    }
}
