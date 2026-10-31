package com.rag.users.user.api.client;

import com.rag.users.user.proto.UserServiceGrpc;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * user-rpc gRPC client —— 持有 UserServiceBlockingStub。
 *
 * 对应 Go: service/user/user/api/internal/svc/servicecontext.go 中的 UserRpc（go-zero 自动生成）。
 *
 * 框架职责:
 *   - @GrpcClient("user-rpc") 自动注入阻塞式 stub（grpc.client.user-rpc.address=discovery:///user-rpc）
 *   - devh grpc-client-spring-boot-starter 通过 Nacos 自动发现 user-rpc 多实例（修 DRIFT-NEW-001）
 *   - 取代了原 internal/internal.svc.ServiceContext.UserRpc 字段
 *   - 所有 Service 类通过 UserRpcClient 调用下游 user-rpc
 *
 * Phase 2b 填肉阶段：可加 retry / circuit breaker / deadline propagation（DRIFT-018）
 *
 * <h2>🔴 DRIFT-G1-002：启动期必须能连上 user-rpc，否则进程起不来</h2>
 *
 * <p><b>Go 出处（逐层到消费点）</b>：
 * <pre>
 *   service/user/user/api/internal/svc/servicecontext.go:26
 *       UserRpc: userservice.NewUserService(zrpc.MustNewClient(c.UserRpc)),
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
 * <p>⇒ Go 侧 {@code grpc.DialContext} 带 {@code WithBlock()} ⇒ <b>启动时阻塞拨号直到连上，
 * 或 3s 超时后返回 error ⇒ {@code logx.Must} ⇒ panic ⇒ 进程根本起不来</b>。
 *
 * <p>⇒ <b>RAG 侧必须复刻这个「启动期硬失败」语义</b>。修之前：{@code @GrpcClient} 是<b>懒连接</b>，
 * 下游不可达时进程照样启动、就绪探针报 Ready，但每个请求各自失败 ⇒ K8s 里表现为
 * 「Pod Ready 但 100% 请求失败」，而 Go 表现为「Pod 起不来」——<b>就绪探针语义不同</b>。
 *
 * <h3>实现方式：用 devh 的<b>原生</b> {@code immediate-connect-timeout}，不自己写拨号检查</h3>
 *
 * <p>devh grpc-client-spring-boot-starter 3.1.0 的
 * {@code AbstractChannelFactory.newManagedChannel(name)} 里已经有这套机制：
 * <pre>
 *   final Duration timeout = this.properties.getChannel(name).getImmediateConnectTimeout();
 *   if (!timeout.isZero()) { connectOnStartup(name, channel, timeout); }
 *   ...
 *   private void connectOnStartup(String name, ManagedChannel channel, Duration timeout) {
 *       channel.getState(true);
 *       final CountDownLatch readyLatch = new CountDownLatch(1);
 *       waitForReady(channel, readyLatch);
 *       boolean connected = readyLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
 *       if (!connected) { throw new IllegalStateException("Can't connect to channel " + name); }
 *   }
 * </pre>
 * ⇒ <b>与 Go 的 {@code zrpc.MustNewClient} 逐条同型</b>：都是「连不上 ⇒ 进程起不来」，
 * 而不是「起来但请求全失败」。
 *
 * <p>本模块在 {@code application.yml} 里<b>只对 user-rpc 这一个 channel</b> 打开它
 * （{@code grpc.client.user-rpc.immediate-connect-timeout: 3s}），<b>不设全局</b> ⇒ 理由：
 * Go 侧 user-api 的 {@code ServiceContext} 里<b>只有</b> {@code UserRpc} 一个下游依赖
 * （{@code service/user/user/api/internal/svc/servicecontext.go:16-28}）
 * ⇒ 若把超时设成全局，就会凭空多出一条 Go 侧不存在的启动期硬依赖。
 *
 * <h3>🔴 踩坑：<b>不能</b>用 {@code stub.getChannel()} 自己读连通性状态</h3>
 *
 * <p>判据必须是 devh 自己持有的<b>原始</b> {@code ManagedChannel}
 * （{@code getState} 只存在于 {@code io.grpc.ManagedChannel}，
 * 而生产里 channel 会被 {@code ClientInterceptors.interceptForward} 包成
 * {@code io.grpc.ClientInterceptors$InterceptorChannel}，它<b>不暴露</b> {@code getState}）
 * ⇒ 手写检查在生产<b>永远为 false</b> ⇒ <b>user-api 在任何环境下都起不来</b>。
 * ⇒ 结论：用 devh 的 {@code immediate-connect-timeout}，不要自己摸 channel。
 * （完整经过见 admin-api 的 {@code AdminRpcClient} 同款注释。）
 *
 * <h3>为什么还要留 {@link #afterPropertiesSet()} 这一道自检</h3>
 *
 * <p>{@code immediate-connect-timeout} 的<b>默认值是 {@code Duration.ZERO}（= 关闭）</b>
 * ⇒ <b>「yml 里没写」就等于「机制没武装」</b>。
 * 若有人删掉那一行，启动期硬失败会<b>静默消失</b>，而现象是
 * 「Pod Ready 但 100% 请求失败」——<b>与 Go 的差异又回来了，而且没人会发现</b>。
 * ⇒ 本方法在上下文启动的最后一刻断言<b>机制确实已武装</b>，否则直接让启动失败。
 * 这是 §3.3「配置里没写 ≠ 没开」的直接落地。
 */
@Component
public class UserRpcClient implements InitializingBean {

    /** devh 里的 channel 名 = {@code @GrpcClient("user-rpc")} 的值。 */
    private static final String CHANNEL_NAME = "user-rpc";

    /**
     * 拨号预算 —— 对齐 go-zero {@code zrpc/internal/client.go:18} 的 {@code dialTimeout = time.Second * 3}。
     *
     * <p>⚠️ 这个值<b>不</b>由本类持有，而是由 {@code application.yml} 的
     * {@code grpc.client.user-rpc.immediate-connect-timeout} 持有 ⇒
     * 本常量只是<b>断言用</b>的期望值（{@code UserApiStartupFailFastTest} 会核对 yml 里的真实值）。
     */
    public static final Duration DIAL_TIMEOUT = Duration.ofSeconds(3);

    private static final Logger LOG = LoggerFactory.getLogger(UserRpcClient.class);

    @GrpcClient("user-rpc")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    /**
     * 仅为「拿到 channel 名对应的有效配置」而存在 —— 用来断言
     * {@code immediate-connect-timeout} 确实已武装（见类注释末段）。
     */
    private final GrpcChannelsProperties channelProperties;

    public UserRpcClient(GrpcChannelsProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    @Override
    public void afterPropertiesSet() {
        Duration timeout = channelProperties.getChannel(CHANNEL_NAME).getImmediateConnectTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(String.format(
                    "user-rpc 的启动期拨号检查**未武装**：grpc.client.%s.immediate-connect-timeout=%s。"
                            + "对齐 Go service/user/user/api/internal/svc/servicecontext.go:26 的 "
                            + "zrpc.MustNewClient（连不上 ⇒ 进程起不来）需要它为正数；"
                            + "devh 的默认值是 0（= 关闭），"
                            + "机制一旦被静默关掉，现象会退化成「Pod Ready 但 100%% 请求失败」。"
                            + "请在 service/users/user/user-api/src/main/resources/application.yml 的 "
                            + "grpc.client.%s.immediate-connect-timeout 处显式写 %dms。",
                    CHANNEL_NAME, timeout, CHANNEL_NAME, DIAL_TIMEOUT.toMillis()));
        }
        LOG.info("user-rpc 启动期拨号检查已武装：immediate-connect-timeout={}ms"
                + "（对齐 go-zero zrpc/internal/client.go:18 dialTimeout=3s）", timeout.toMillis());
    }

    /**
     * <b>zrpc 客户端超时 = 2s</b>。
     *
     * <p><b>Go 出处</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
     * —— {@code RpcClientConf.Timeout int64 `json:",default=2000"`}
     * ⇒ <b>默认 2000ms</b>；且 Go 侧 {@code service/user/user/api/etc/usercenter.yaml}
     * 的 {@code UserRpc:} 段<b>没有显式 Timeout</b>（只有 {@code Etcd}）⇒ ⇒
     * <b>Go 侧出向 user-rpc 的调用恒带 2s 客户端超时</b>。
     *
     * <p><b>消费点</b>：{@code go-zero@v1.10.2/zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
     * —— {@code ctx, cancel := context.WithTimeout(ctx, t)}
     * ⇒ <b>Go 的 {@code context.WithTimeout} 取更早者</b>（父 deadline 更早则保留父的）
     * ⇒ ⇒ <b>Go 的出向 deadline = min(入站 ctx 剩余, 2s 客户端超时)</b>。
     *
     * <p><b>语义等价论证</b>：Go 的 min 语义现在由<b>共享层</b>一次性做出来 ——
     * {@code service/common/src/main/java/com/rag/common/protection/GoStyleOutboundDeadlineInterceptor}
     * 的 {@code effectiveCallOptions} 对「入站剩余 / 客户端硬上界 / 调用点自带」<b>三项取 min</b>
     * ⇒ <b>与 Go 的 {@code context.WithTimeout(ctx, 2s)} 逐字同形</b>。
     *
     * <p><b>🔴 2026-10-31（P2-T04 · G1 lane）：本类<b>不再</b>自己套客户端上界</b> ——
     * 上界由共享层的 {@code GoStyleOutboundDeadlineInterceptor} <b>唯一</b>承担
     * （{@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向 devh 调用上 ⇒ 与调用点无关），
     * 本域只配参数 {@code rag.protection.outbound-deadline.cap-millis=2000}
     * （{@code service/users/user/user-api/src/main/resources/application.yml} 的 {@code rag.protection} 段）
     * ⇒ ⇒ <b>为什么必须删掉域侧私有副本</b>：副本把上界<b>写死</b>在 {@code CallOptions} 里，
     * 共享层再对它取 min ⇒ ⇒ <b>min 只压小、不压大</b>
     * ⇒ ⇒ <b>「想放宽上界」会被副本压回 2000ms</b>（{@code USER_API_OUTBOUND_DEADLINE_CAP_MILLIS} 配得比 2000 大也不生效）
     * ⇒ ⇒ 动作②「上界可 env 覆盖」在<b>放宽方向</b>上被架空 ⇒ ⇒ 副本必删（棘轮
     * {@code GoStyleClientCallTimeoutSingleSourceGateTest}：域侧<b>只配参数</b>）。
     * ⇒ ⇒ <b>本常量仍在</b>：它现在是<b>断言用的期望值</b>（真实值由上面的 yml 持有），守门
     * {@code UserRpcClientDeadlineCapTest} 核对「yml 绑出的值 == 本常量 == 2000」。
     *
     * <p><b>为什么 RAG 侧需要这一层</b>：Go 的 2s 客户端超时是
     * <b>go-zero client interceptor 级</b>的（框架自带）；RAG 侧
     * {@code devh} 的 {@code @GrpcClient} 注入的 stub 若无上界
     * ⇒ 下游挂死时请求会<b>一路顶到 HTTP 超时分支</b>（{@code http-timeout: 3s}）
     * ⇒ 对外是 <b>503 {@code Request Timeout}</b>；而 Go 侧是
     * <b>HTTP 200 + {@code {"code":1015,"msg":"服务繁忙"}}</b>
     * ⇒ ⇒ <b>可观察差异</b>（状态码 + 响应体 + 时刻三者都不同）。
     *
     * <p><b>⚠️ 差异的准确边界（不要读宽）</b>：RAG 侧下游 {@code user-rpc} <b>自己</b>带服务端 2s 超时
     * （{@code service/users/user/user-rpc/src/main/resources/application.yaml:209} = {@code rpc-timeout: 2s}；
     * 接线在位由 {@code check-grpc-interceptor-wiring.py} = EXIT=0 实测确认）
     * ⇒ ⇒ <b>形态①「请求到达下游、业务挂死」</b>：RAG 由<b>服务端</b> 2s 收口 ⇒ {@code DEADLINE_EXCEEDED}
     * ⇒ 本域映射 ⇒ <b>1015</b> ＋ HTTP 200 ⇒ <b>与 Go 同形</b>；
     * ⇒ ⇒ <b>形态②「请求没到达下游」</b>（进程不可达 / 网络黑洞 / 连接建好但无响应）：
     * Go 由<b>客户端</b> 2s 收口（= min(剩余 3s ctx, 2s)，3s 预算见
     * {@code service/common/observability/http.go:30-34} ＋ {@code :53-56}）⇒ 200 + 1015；
     * RAG 修前<b>无客户端收口</b> ⇒ 只能等 3s HTTP 预算 ⇒ 503
     * ⇒ ⇒ <b>形态②才是本条要修的可观察差异</b>。<p>⚠️ <b>2026-10-31 起本常量只是【Go 默认值的文档锚点】</b>：出向封顶由共享层唯一实现承担 ⇒ ⇒ 本常量<b>不参与任何行为</b> ⇒ ⇒ 守门<b>不</b>断言它（断言一个不参与行为的数字 = 恒绿的形式断言）。
     */
    public static final long CLIENT_TIMEOUT_MILLIS = 2000L;

    /**
     * 取出出向 user-rpc 的 stub —— <b>每次调用都重新套一次入站预算</b>。
     *
     * <p>⚠️ <b>不要缓存返回的 stub</b>：deadline 是<b>按调用时刻</b>算的
     * （入站预算在请求推进中不断变短）⇒ 缓存会把「第一次调用时的预算」固化下来
     * （同 {@code GoStyleRequestDeadline.applyTo} javadoc 的警告）。
     *
     * <p>🔴 <b>2026-10-31（P2-T04）：此处<b>不再</b>套「2s 客户端上界」</b> ——
     * 上界由共享层的出向 deadline 拦截器在 <b>channel 层</b>承担
     * （{@code @GrpcGlobalClientInterceptor} ⇒ 与调用点无关 ⇒ 凡是本 channel 上的
     * stub 都带 ≤2000ms 上界）⇒ ⇒ 本方法只保留「入站预算」这一维。
     */
    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        return com.rag.common.observability.GoStyleRequestDeadline.applyTo(userServiceBlockingStub);
    }
}