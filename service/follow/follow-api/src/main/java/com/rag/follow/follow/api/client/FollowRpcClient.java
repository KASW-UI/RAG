package com.rag.follow.follow.api.client;

import com.rag.follow.follow.proto.FollowServiceGrpc;
import net.devh.boot.grpc.client.config.GrpcChannelsProperties;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * follow-rpc gRPC client —— 持有 {@code FollowServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/follow/api/internal/svc/servicecontext.go:22-30}
 * <pre>
 * type ServiceContext struct {
 *     Config                   config.Config
 *     FollowRpc                followservice.FollowService      // ← 本类对应物
 *     CheckBlacklistMiddleware rest.Middleware
 * }
 * func NewServiceContext(c config.Config) *ServiceContext {
 *     redisDB := redis.MustNewRedis(c.BizRedis)
 *     return &amp;ServiceContext{
 *         Config:                   c,
 *         FollowRpc:                followservice.NewFollowService(zrpc.MustNewClient(c.FollowRpc)),
 *         CheckBlacklistMiddleware: middleware.NewCheckBlacklistMiddleware(redisDB).Handle,
 *     }
 * }
 * </pre>
 *
 * <p><b>框架职责</b>：
 * <ul>
 *   <li>{@code @GrpcClient("follow-rpc")} 由 devh grpc-client-spring-boot-starter
 *       自动注入阻塞式 stub；寻址走 {@code grpc.client.follow-rpc.address=discovery:///follow-rpc}，
 *       即 Nacos 自动发现（对应 Go 端的 etcd key {@code follow.rpc}）。</li>
 *   <li>取代了 Go 的 {@code zrpc.MustNewClient} + {@code followservice.NewFollowService} 两层。</li>
 *   <li>Go 侧 api → rpc 的调用是<b>同步阻塞</b>的（8 个 logic 全是
 *       {@code resp, err := l.svcCtx.FollowRpc.Xxx(l.ctx, ...)}，无 goroutine / 无 errgroup），
 *       所以用 {@code BlockingStub} 而不是 future stub —— 这属于 L3
 *       （并发与失败传播语义），不是可以随手换的风格差异。</li>
 * </ul>
 *
 * <p><b>⚠️ retry：Go 侧确实没有，禁止加</b>。
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但 follow api 用的是前者），
 * 且 {@code etc/followcenter.yaml} 也没配。加 retry 会让「一次失败 → 业务码 1015」
 * 变成「重试 N 次后仍失败」，可观察的<b>调用次数与延迟</b>都变了。
 * 1015 = {@code CodeServerBusy}（{@code errmsg.go:37}）。
 *
 * <p><b>⚠️ circuit breaker：Go 侧<b>有</b>，且默认开启</b> —— 由
 * {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor} 承担，
 * 它带 devh 的 {@code @GrpcGlobalClientInterceptor}，被本模块的
 * {@code scanBasePackages} 扫到即<b>自动注册</b>，无需在此手工挂。
 * <p>Go 侧证据：
 * <ul>
 *   <li>{@code zrpc/internal/config.go:14}：
 *       {@code ClientMiddlewaresConf.Breaker bool json:",default=true"}</li>
 *   <li>{@code zrpc/internal/client.go:112-113}：
 *       {@code if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }}</li>
 *   <li>{@code etc/followcenter.yaml} 未配置 {@code Middlewares} ⇒ 走 {@code default=true} ⇒ <b>是开的</b>。</li>
 * </ul>
 * <p>开关由 {@code rag.protection.breaker.enabled} 控制（默认 true，见 application.yml），
 * 与 Go 的 {@code default=true} 同向。
 *
 * <p><b>⚠️ 熔断只对「基础设施故障」生效，不对业务错误生效</b> ——
 * 判据是 Go {@code zrpc/internal/codes/accept.go:9-17} 的 {@code codes.Acceptable}：
 * 只把 {@code DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented /
 * ResourceExhausted} 记为失败，其余（含 {@code OK}）记为成功。
 * <p>follow-rpc 侧的业务错误里<b>确实有 {@code codes.Internal}</b>
 * （{@code follow/rpc/internal/logic/**} 用它表达 {@code ErrorDbSelect / ErrorDbUpdate /
 * ErrorServerCommon}），按 Go 的判据它们<b>会</b>计入熔断统计 ——
 * 这正是 Go 的行为，RAG 侧必须同样计，否则「下游频繁返回业务错误时的熔断阈值」
 * 就与 Go 不同。共享实现的 {@code isAcceptable} 已逐条对齐那六个 code。
 *
 * <h2>🔴 DRIFT-G1-002：启动期必须能连上 follow-rpc，否则进程起不来</h2>
 *
 * <p><b>Go 出处（逐层到消费点）</b>：
 * <pre>
 *   service/follow/api/internal/svc/servicecontext.go:27
 *       FollowRpc: followservice.NewFollowService(zrpc.MustNewClient(c.FollowRpc)),
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
 * <p>⇒ Go 侧启动时<b>阻塞拨号 3s</b>，连不上就 panic ⇒ <b>进程根本起不来</b>。
 * ⇒ 修之前 RAG 侧是懒连接 ⇒ 表现为「<b>Pod Ready 但 100% 请求失败</b>」——
 * <b>就绪探针语义不同</b>，K8s 里排障方向完全不一样。
 *
 * <h3>实现方式：用 devh 的<b>原生</b> {@code immediate-connect-timeout}，不自己写拨号检查</h3>
 *
 * <p>devh grpc-client-spring-boot-starter 3.1.0 的
 * {@code AbstractChannelFactory.newManagedChannel(name)} 里已经有这套机制：
 * <pre>
 *   final Duration timeout = this.properties.getChannel(name).getImmediateConnectTimeout();
 *   if (!timeout.isZero()) { connectOnStartup(name, channel, timeout); }
 *   ...
 *   if (!readyLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
 *       throw new IllegalStateException("Can't connect to channel " + name);   // ← 启动直接失败
 *   }
 * </pre>
 * ⇒ <b>与 Go 的 {@code zrpc.MustNewClient} 逐条同型</b>：都是「连不上 ⇒ 进程起不来」，
 * 而不是「起来但请求全失败」。
 *
 * <p>本模块在 {@code application.yml} 里<b>只对 follow-rpc 这一个 channel</b> 打开它
 * （{@code grpc.client.follow-rpc.immediate-connect-timeout: 3s}），<b>不设全局</b> ⇒ 理由：
 * Go 侧 follow-api 的 {@code ServiceContext} 里<b>只有</b> {@code FollowRpc} 一个下游依赖
 * （{@code service/follow/api/internal/svc/servicecontext.go:16-30}）
 * ⇒ 若把超时设成全局，就会凭空多出一条 Go 侧不存在的启动期硬依赖。
 *
 * <h3>🔴 踩坑：<b>不能</b>用 {@code stub.getChannel()} 自己读连通性状态</h3>
 *
 * <p>生产里 channel 会被 {@code ClientInterceptors.interceptForward} 包成
 * {@code io.grpc.ClientInterceptors$InterceptorChannel}，它<b>不暴露</b> {@code getState}
 * （{@code getState} 只存在于 {@code io.grpc.ManagedChannel}）⇒ 手写检查在生产<b>永远为 false</b>
 * ⇒ <b>follow-api 在任何环境下都起不来</b>。
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
 */
@Component
public class FollowRpcClient implements InitializingBean {

    /** devh 里的 channel 名 = {@code @GrpcClient("follow-rpc")} 的值。 */
    private static final String CHANNEL_NAME = "follow-rpc";

    /**
     * 拨号预算 —— 对齐 go-zero {@code zrpc/internal/client.go:18} 的 {@code dialTimeout = time.Second * 3}。
     *
     * <p>⚠️ 这个值<b>不</b>由本类持有，而是由 {@code application.yml} 的
     * {@code grpc.client.follow-rpc.immediate-connect-timeout} 持有 ⇒
     * 本常量只是<b>断言用</b>的期望值（{@code FollowApiStartupFailFastTest} 会核对 yml 里的真实值）。
     */
    public static final Duration DIAL_TIMEOUT = Duration.ofSeconds(3);

    /**
     * gRPC <b>客户端</b>超时（毫秒）—— 对应 Go {@code zrpc/config.go:34} 的
     * {@code RpcClientConf.Timeout int64 `json:",default=2000"`}。
     *
     * <p><b>⚠️ 这是 Go 的 struct 默认值，不是 yaml 字段</b>：
     * {@code service/follow/api/etc/followcenter.yaml} 的 {@code FollowRpc} 段
     * 只有 {@code Etcd.{Hosts,Key}}，<b>没有</b> {@code Timeout}
     * ⇒ 实际生效值来自 go-zero 的 {@code default=2000}。
     * <p>它与「HTTP 超时 3s」是<b>两层</b>预算，<b>取更小的生效</b>：
     * Go 侧下游挂死时是 <b>2s 先到</b> ⇒ api 返回 <b>200 + 业务码 1015</b>；
     * 若只带 3s 的 ctx 预算，就会一路顶到 HTTP 超时分支 ⇒ 返回 503 "Request Timeout"。
     */
    private static final long CLIENT_TIMEOUT_MILLIS = 2000L;

    @GrpcClient("follow-rpc")
    private FollowServiceGrpc.FollowServiceBlockingStub followServiceBlockingStub;

    /**
     * 仅为「拿到 channel 名对应的有效配置」而存在 —— 用来断言
     * {@code immediate-connect-timeout} 确实已武装（见类注释末段）。
     */
    private final GrpcChannelsProperties channelProperties;

    private static final Logger LOG = LoggerFactory.getLogger(FollowRpcClient.class);

    public FollowRpcClient(GrpcChannelsProperties channelProperties) {
        this.channelProperties = channelProperties;
    }

    @Override
    public void afterPropertiesSet() {
        Duration timeout = channelProperties.getChannel(CHANNEL_NAME).getImmediateConnectTimeout();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalStateException(String.format(
                    "follow-rpc 的启动期拨号检查**未武装**：grpc.client.%s.immediate-connect-timeout=%s。"
                            + "对齐 Go service/follow/api/internal/svc/servicecontext.go:27 的 "
                            + "zrpc.MustNewClient（连不上 ⇒ 进程起不来）需要它为正数；"
                            + "devh 的默认值是 0（= 关闭），"
                            + "机制一旦被静默关掉，现象会退化成「Pod Ready 但 100%% 请求失败」。"
                            + "请在 service/follow/follow-api/src/main/resources/application.yml 的 "
                            + "grpc.client.%s.immediate-connect-timeout 处显式写 %dms。",
                    CHANNEL_NAME, timeout, CHANNEL_NAME, DIAL_TIMEOUT.toMillis()));
        }
        LOG.info("follow-rpc 启动期拨号检查已武装：immediate-connect-timeout={}ms"
                + "（对齐 go-zero zrpc/internal/client.go:18 dialTimeout=3s）", timeout.toMillis());
    }

    /**
     * 返回<b>已套上请求级 deadline</b> 的 stub。
     *
     * <p>理由与 {@code MessageRpcClient#messageRpc()} 完全相同：Go 侧靠
     * {@code timeouthandler.go:68} 的 {@code r.WithContext(ctx)} 把 deadline 传进
     * <b>全部</b>下游调用；RAG 侧唯一能一处覆盖所有调用点的位置就是这个 getter ——
     * 8 个 Service 的全部调用点都经由 {@code followRpc()}，改一处即全覆盖，
     * 漏一处就是一个「有时有 deadline、有时没有」的不一致。
     *
     * <p>无 deadline 时（未启用 HTTP 超时 / 非超时路径）<b>原样返回</b>，
     * 对应 Go「没装 TimeoutHandler 就没有 ctx deadline」。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     *
     * <p><b>⚠️ 还要再压一层「客户端超时」（2s）—— 这一层不能省</b>。
     * <p>Go 侧<b>同时</b>有两层预算，<b>取更小的那个生效</b>：
     * <ul>
     *   <li><b>ctx 预算 3s</b>：来自 rest 超时中间件
     *       （{@code followcenter.go:40-42} DisableNativeRestTimeout + NewHTTPMiddleware，
     *       {@code timeouthandler.go:67-68} 的 {@code r.WithContext(ctx)}）。</li>
     *   <li><b>zrpc 客户端超时 2s</b>：见 {@link #CLIENT_TIMEOUT_MILLIS}。</li>
     * </ul>
     * ⇒ Go 侧下游挂死时，<b>2s 的客户端超时先到</b> ⇒ api 拿到 rpc 错误 ⇒
     * <b>返回 200 + 业务码 1015</b>（不是 HTTP 503）。
     * <p>而只带 ctx 预算（3s）时，下游挂死会一路顶到 HTTP 超时分支
     * ⇒ 返回 <b>503 "Request Timeout"</b>；且业务线程与超时线程会抢同一个 response，
     * 还会出现「HTTP 200 + body "Request Timeout"」这种<b>状态码与 body 不自洽</b>的响应
     * （2026-10-24 双侧活体对拍实测，见 docs/DRIFT/follow/api.md）。
     */
    public FollowServiceGrpc.FollowServiceBlockingStub followRpc() {
        return capByClientTimeout(
                com.rag.common.observability.GoStyleRequestDeadline.applyTo(followServiceBlockingStub));
    }

    /**
     * 把 deadline 压到「ctx 预算」与「客户端超时」里的<b>较小者</b>。
     *
     * <p><b>语义</b>：Go 侧客户端拦截器
     * {@code zrpc/internal/clientinterceptors/timeoutinterceptor.go} 做的是
     * {@code ctx, cancel := context.WithTimeout(ctx, c.Timeout)} ——
     * 即「在已有 ctx 预算之上再套一层 2s」，<b>取更小的生效</b>。
     *
     * <p><b>⚠️ 没有 ctx 预算时也要套 2s</b>：Go 的客户端超时是
     * <b>拦截器级别</b>的，与有没有 HTTP 超时中间件无关；
     * 若此时原样返回，就等于「有时有客户端超时、有时没有」——
     * 那正是 Go 的 ctx 语义里不存在的不一致。
     */
    private FollowServiceGrpc.FollowServiceBlockingStub capByClientTimeout(
            FollowServiceGrpc.FollowServiceBlockingStub stub) {
        io.grpc.Deadline deadline = stub.getCallOptions().getDeadline();
        long remainingNanos = deadline == null
                ? Long.MAX_VALUE
                : deadline.timeRemaining(java.util.concurrent.TimeUnit.NANOSECONDS);
        long capNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(CLIENT_TIMEOUT_MILLIS);
        if (remainingNanos <= 0L) {
            // 预算已耗尽 ⇒ 立刻失败（Go 侧 ctx 到期同理）
            return stub.withDeadlineAfter(1L, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
        return stub.withDeadlineAfter(Math.min(remainingNanos, capNanos),
                java.util.concurrent.TimeUnit.NANOSECONDS);
    }
}