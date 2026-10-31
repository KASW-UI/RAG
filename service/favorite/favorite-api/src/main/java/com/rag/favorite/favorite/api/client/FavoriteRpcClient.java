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
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go:24-36}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但 favorite api 用的是前者）。
 * 加 retry 会让「一次失败 → 业务码 X」变成「重试 N 次后仍失败」，
 * 可观察的<b>调用次数与延迟</b>都变了。
 *
 * <p><b>✅ circuit breaker：Go 侧有且默认开，RAG 侧<b>也已接入</b>。</b>
 * 由 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor} 承担，
 * 它带 devh 的 {@code @GrpcGlobalClientInterceptor}，被本模块
 * {@code FavoriteApiApplication} 的 {@code scanBasePackages}（含 {@code com.rag.common}）扫到即<b>自动注册</b>。
 * <p>Go 侧证据：
 * <ul>
 *   <li>{@code zrpc/internal/config.go:14}：
 *       {@code ClientMiddlewaresConf.Breaker bool json:",default=true"}；</li>
 *   <li>{@code zrpc/internal/client.go:112-113}：
 *       {@code if c.middlewares.Breaker { interceptors = append(interceptors, clientinterceptors.BreakerInterceptor) }}；</li>
 *   <li>{@code etc/favorite.yaml:34-37} 的 {@code FavoriteRpc} 段只配了 {@code Etcd.{Hosts,Key}}，
 *       <b>没有</b>覆盖 {@code Middlewares} ⇒ 走 {@code default=true} ⇒ <b>是开的</b>。</li>
 * </ul>
 * <p>开关由 {@code rag.protection.breaker.enabled} 控制（默认 true，见
 * {@code GoStyleProtectionProperties}），与 Go 的 {@code default=true} 同向。
 *
 * <p><b>⚠️ 熔断只对「基础设施故障」生效，不对业务错误生效</b> ——
 * 判据是 Go {@code zrpc/internal/codes/accept.go:9-17} 的 {@code codes.Acceptable}：
 * 只把 {@code DeadlineExceeded / Internal / Unavailable / DataLoss / Unimplemented /
 * ResourceExhausted} 记为失败，其余（含 {@code OK}）记为成功。
 * <p>favorite-rpc 侧的业务错误里<b>大量</b>有 {@code codes.Internal} 与 {@code codes.Unavailable}
 * （{@code rpc/internal/logic/**} 用它们表达 {@code ErrorDbSelect / ErrorDbUpdate /
 * ErrorServerCommon}；实测分布见 {@code FavoriteGrpcError} 的类注释），
 * 按 Go 的判据它们<b>会</b>计入熔断统计 ——
 * <b>这正是 Go 的行为</b>，RAG 侧必须同样计，否则「下游频繁返回业务错误时的熔断阈值」
 * 就与 Go 不同。共享实现的 {@code isAcceptable} 已逐条对齐那六个 code。
 *
 * <p><b>⚠️ 装配证据</b>：本条断言由 {@code FavoriteApiProtectionWiringTest} 用<b>运行证据</b>钉住 ——
 * 它读 starter 自己的 {@code GlobalClientInterceptorRegistry}，断言本拦截器
 * <b>确实在它收集到的列表里</b>（只断言「Bean 存在」不够：那证明不了 channel factory
 * 会不会消费它，见 AGENTS.md §3.3 的四类静默失效）。
 *
 * <p><b>⚠️ 本类的历史</b>：本注释此前写的是「Java 侧<b>尚未接入</b>（已登记的已知偏离）」——
 * 那是一条<b>过时的错误断言</b>（既不成立，也从未登记）：
 * {@code docs/Intentional_Deviations/favorite/api.md} 至今为 0 字节。
 * 2026-10-25 实测（{@code FavoriteApiProtectionWiringTest} 3/3 绿）确认熔断<b>早已生效</b>，
 * 故改正。⚠️ <b>不要</b>再按「尚未接入」的思路去「补」一遍。
 */
@Component
public class FavoriteRpcClient {

    @GrpcClient("favorite-rpc")
    private FavoriteServiceGrpc.FavoriteServiceBlockingStub favoriteServiceBlockingStub;

    /**
     * 出向 stub（<b>不</b>带任何域侧 deadline）。
     *
     * <p>🆕 <b>2026-10-31 · G3 lane 第十三轮收敛</b>：本类<b>原先</b>自带一份上界
     * （{@code capByClientTimeout(...)} = min(2s, 请求剩余预算)）
     * ⇒ ⇒ 那个 <b>2s</b> 是<b>第二个上界来源</b>
     * ⇒ ⇒ 与共享层「唯一实现」的形态冲突（共享层棘轮
     * {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④：
     * 「cap 的来源必须是共享层参数，域侧私有常量 = 违规」）。
     *
     * <p><b>Go 侧那个 2s 是哪来的</b>（读法不变，只是<b>承担者</b>变了）：
     * <ul>
     *   <li>{@code go-zero@v1.10.2/zrpc/config.go:31} ——
     *       {@code RpcClientConf.Timeout int64 `json:",default=2000"`}
     *       ⇒ <b>struct tag 默认值</b>，不是 yaml 字段；
     *       {@code service/favorite/api/etc/favorite.yaml:34-38} 的
     *       {@code FavoriteRpc} 段只有 {@code Etcd.{Hosts,Key}} ⇒ <b>没有</b>覆盖 ⇒ 走默认；</li>
     *   <li>消费点：{@code zrpc/client.go:70-71} ⇒ {@code zrpc/internal/client.go:113-117}
     *       ⇒ {@code zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
     *       （{@code context.WithTimeout(ctx, t)} ⇒ <b>取更早者</b>）；</li>
     *   <li>它与「HTTP 超时 3s」（{@code observability.http-timeout: 3s}，对应 Go
     *       {@code rest/config.go:55} 的 {@code Timeout default=3000}）是<b>两层</b>预算，
     *       <b>取更小的生效</b>：Go 侧下游挂死时是 <b>2s 先到</b> ⇒ api 返回
     *       <b>200 + 业务码 1015</b>；若只带 3s 的 ctx 预算，就会一路顶到 HTTP 超时分支
     *       ⇒ 返回 <b>503 "Request Timeout"</b>。</li>
     * </ul>
     *
     * <p><b>现在生效的那一行</b>：本域 {@code application.yml} 的
     * {@code rag.protection.outbound-deadline.cap-millis: ${FAVORITE_API_OUTBOUND_DEADLINE_CAP_MILLIS:2000}}
     * ⇒ ⇒ 由共享层 {@code GoStyleOutboundDeadlineInterceptor}（channel 级 ⇒ 装在<b>每个</b>出向调用上）
     * 以 <b>min</b> 语义并入：min(入向剩余预算, 2000ms)
     * ⇒ ⇒ 与 Go 侧 {@code context.WithTimeout(ctx, 2s)} 逐字同形。
     *
     * <p>⚠️ <b>不要再在本类写 {@code withDeadlineAfter}</b>：那会重新造出第二份上界
     * ⇒ ⇒ 共享层棘轮会判红（点位级）⇒ ⇒ 详见
     * {@code FavoriteRpcClientTimeoutTest}（本域 api 侧 cap 守门 · 7 例）。
     */
    public FavoriteServiceGrpc.FavoriteServiceBlockingStub favoriteRpc() {
        return favoriteServiceBlockingStub;
    }
}