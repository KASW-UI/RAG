package com.rag.favorite.favorite.api.client;

import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * favorite-rpc gRPC client —— 持有 {@code FavoriteServiceBlockingStub}。
 *
 * <p><b>Go 出处</b>：{@code service/favorite/api/internal/svc/service_context.go:13-26}
 * <pre>
 * type ServiceContext struct {
 *     Config      config.Config
 *     FavoriteRpc favoriteservice.FavoriteService
 *     CheckBlacklistMiddleware rest.Middleware
 * }
 * func NewServiceContext(c config.Config) *svc.ServiceContext {
 *     redisDB := redis.MustNewRedis(c.BizRedis)
 *     return &amp;svc.ServiceContext{
 *         Config:      c,
 *         FavoriteRpc: favoriteservice.NewFavoriteService(zrpc.MustNewClient(c.FavoriteRpc)),
 *         ...
 *     }
 * }
 * </pre>
 *
 * <p><b>框架职责</b>：
 * <ul>
 *   <li>{@code @GrpcClient("favorite-rpc")} 由 devh grpc-client-spring-boot-starter
 *       自动注入阻塞式 stub；寻址走
 *       {@code grpc.client.favorite-rpc.address=discovery:///favorite-rpc}（Nacos 自动发现），
 *       对应 Go 端的 {@code FavoriteRpc.Etcd.Key: favorite.rpc}
 *       （{@code etc/favorite.yaml:34-37}）。</li>
 *   <li>取代了 Go 的 {@code zrpc.MustNewClient} + {@code favoriteservice.NewFavoriteService}
 *       两层。</li>
 *   <li>Go 侧 api → rpc 的调用是<b>同步阻塞</b>的（无 goroutine / 无 errgroup），
 *       所以用 {@code BlockingStub} 而非 future stub —— 这属于 L3
 *       （并发与失败传播语义），不是可随手换的风格差异。</li>
 * </ul>
 *
 * <p><b>⚠️ retry：Go 侧没有，禁止加。</b>
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但 favorite api 用的是前者）。
 * 加 retry 会让「一次失败 → 业务码 X」变成「重试 N 次后仍失败」，
 * 可观察的<b>调用次数与延迟</b>都变了。
 *
 * <p><b>⚠️ client 熔断：Go 侧有且默认开，Java 侧尚未接入（已登记的已知偏离）</b>。
 * 证据：{@code zrpc/internal/config.go:14} 的
 * {@code ClientMiddlewaresConf.Breaker bool json:",default=true"}
 * + {@code zrpc/internal/client.go:112-113} 在 {@code c.middlewares.Breaker} 时
 * append {@code clientinterceptors.BreakerInterceptor}。
 * {@code etc/favorite.yaml} 未配 {@code Middlewares} ⇒ 走 {@code default=true} ⇒ <b>是开的</b>。
 * <p><b>为什么现在不补</b>：共享实现 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor}
 * 已存在于 {@code service/common}，但把它接到本模块需要先确认它的失败名单
 * （{@code zrpc/internal/codes/accept.go:9-17} 的 {@code codes.Acceptable} 只把
 * {@code DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented / ResourceExhausted}
 * 记为失败）；而 favorite 域的 Go 侧<b>大量</b>使用 {@code Internal} 与 {@code Unavailable}
 * 表示业务错误（见 {@code FavoriteGrpcError} 类注释），接上熔断会让「业务错误」被计成
 * 「依赖故障」而打开熔断器 —— 那与 Go 侧一致，但需要先与 favorite-rpc 的错误分布对齐。
 * <b>登记为已知偏离，等 favorite-rpc 落地后与 message / like 域一起收敛。</b>
 */
@Component
public class FavoriteRpcClient {

    @GrpcClient("favorite-rpc")
    private FavoriteServiceGrpc.FavoriteServiceBlockingStub favoriteServiceBlockingStub;

    /**
     * 返回<b>已套上请求级 deadline</b> 的 stub。
     *
     * <p>⚠️ 刻意在<b>这个出口</b>统一套，而不是让 7 个 Service 各写一次：
     * Go 侧靠 {@code r.WithContext(ctx)}（timeouthandler.go:68）把 deadline 传进
     * <b>全部</b>下游调用，RAG 侧唯一等价的位置就是这个 getter ——
     * 所有调用点都经由 {@code favoriteRpc()}，改一处即全覆盖，漏一处就是一个
     * 「有时有 deadline、有时没有」的不一致（§3.3）。
     *
     * <p>无 deadline 时（未启用 HTTP 超时 / 非超时路径）<b>原样返回</b>，
     * 对应 Go「没装 TimeoutHandler 就没有 ctx deadline」。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     */
    public FavoriteServiceGrpc.FavoriteServiceBlockingStub favoriteRpc() {
        return com.rag.common.observability.GoStyleRequestDeadline.applyTo(favoriteServiceBlockingStub);
    }
}
