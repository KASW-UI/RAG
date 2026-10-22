package com.rag.like.like.api.client;

import com.rag.like.like.proto.LikeServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * like-rpc gRPC client —— 持有 {@code LikeServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/like/api/internal/svc/servicecontext.go:13-23}
 * <pre>
 * type ServiceContext struct {
 *     Config  config.Config
 *     LikeRpc likeservice.LikeService
 * }
 * func NewServiceContext(c config.Config) *ServiceContext {
 *     return &amp;ServiceContext{ ..., LikeRpc: likeservice.NewLikeService(zrpc.MustNewClient(c.LikeRpc)) }
 * }
 * </pre>
 *
 * <p><b>框架职责</b>：
 * <ul>
 *   <li>{@code @GrpcClient("like-rpc")} 由 devh grpc-client-spring-boot-starter
 *       自动注入阻塞式 stub；寻址走
 *       {@code grpc.client.like-rpc.address=discovery:///like-rpc}（Nacos 自动发现），
 *       对应 Go 端的 {@code etcd Key: like.rpc}（{@code likecenter.yaml:31-36}）。</li>
 *   <li>取代了 Go 的 {@code zrpc.MustNewClient} + {@code likeservice.NewLikeService} 两层。</li>
 *   <li>Go 侧 api → rpc 的调用是<b>同步阻塞</b>的（无 goroutine / 无 errgroup），
 *       所以用 {@code BlockingStub} 而非 future stub —— 这属于 L3
 *       （并发与失败传播语义），不是可随手换的风格差异。</li>
 * </ul>
 *
 * <p><b>⚠️ retry：Go 侧没有，禁止加。</b>
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但 like api 用的是前者）。
 * 加 retry 会让「一次失败 → 业务码 X」变成「重试 N 次后仍失败」，
 * 可观察的<b>调用次数与延迟</b>都变了。
 *
 * <p><b>⚠️ client 熔断：Go 侧有且默认开，Java 侧尚未接入（已登记的已知偏离）</b>。
 * 证据：{@code zrpc/internal/config.go:14} 的
 * {@code ClientMiddlewaresConf.Breaker bool json:",default=true"}
 * + {@code zrpc/internal/client.go:112-113} 在 {@code c.middlewares.Breaker} 时
 * append {@code clientinterceptors.BreakerInterceptor}
 * + {@code zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18}
 * 的 {@code breaker.DoWithAcceptableCtx(...)}（每个 RPC 方法一个 breaker）。
 * {@code etc/likecenter.yaml} 未配 {@code Middlewares} ⇒ 走 {@code default=true} ⇒ <b>是开的</b>。
 * <p><b>为什么现在补它风险大于收益</b>：本模块下游 like-rpc 在 RAG 侧尚未实现，
 * 没有可熔断的对象；而 message 域已有的实现（{@code GoStyleBreaker} 等）位于
 * message-api 模块内，跨域复用需要先下沉到公共模块 —— 那是超出 like/api 迁移范围的改动。
 * <b>登记为已知偏离，等 like-rpc 落地后与 message 域一起收敛</b>。
 * <p>补它时<b>必须</b>逐条对齐 {@code zrpc/internal/codes/accept.go:9-17} 的
 * {@code codes.Acceptable}（只把 {@code DeadlineExceeded / Internal / Unavailable /
 * DataLoss / Unimplemented / ResourceExhausted} 记为失败）。
 * 本域 {@link com.rag.like.like.common.errmsg.LikeGrpcError} 刻意把业务码映到
 * {@code INVALID_ARGUMENT} / {@code UNKNOWN}（都在名单外）正是为此预留的前提。
 */
@Component
public class LikeRpcClient {

    @GrpcClient("like-rpc")
    private LikeServiceGrpc.LikeServiceBlockingStub likeServiceBlockingStub;

    /**
     * 返回<b>已套上请求级 deadline</b> 的 stub。
     *
     * <p>⚠️ 刻意在<b>这个出口</b>统一套，而不是让 6 个 Service 各写一次：
     * Go 侧靠 {@code r.WithContext(ctx)}（timeouthandler.go:68）把 deadline 传进
     * <b>全部</b>下游调用，RAG 侧唯一等价的位置就是这个 getter ——
     * 所有调用点都经由 {@code likeRpc()}，改一处即全覆盖，漏一处就是一个「有时有 deadline、
     * 有时没有」的不一致（§3.3）。
     *
     * <p>无 deadline 时（未启用 HTTP 超时 / 非超时路径）<b>原样返回</b>，
     * 对应 Go「没装 TimeoutHandler 就没有 ctx deadline」。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     */
    public LikeServiceGrpc.LikeServiceBlockingStub likeRpc() {
        return com.rag.common.observability.GoStyleRequestDeadline.applyTo(likeServiceBlockingStub);
    }
}