package com.rag.follow.follow.api.client;

import com.rag.follow.follow.proto.FollowServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

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
 */
@Component
public class FollowRpcClient {

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