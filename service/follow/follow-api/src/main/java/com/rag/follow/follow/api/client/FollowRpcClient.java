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
     */
    public FollowServiceGrpc.FollowServiceBlockingStub followRpc() {
        return com.rag.common.observability.GoStyleRequestDeadline.applyTo(followServiceBlockingStub);
    }
}