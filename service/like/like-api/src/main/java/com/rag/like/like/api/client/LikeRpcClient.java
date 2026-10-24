package com.rag.like.like.api.client;

import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.like.like.api.config.LikeApiProperties;
import com.rag.like.like.proto.LikeServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

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
 * <p>为什么现在补它风险大于收益：本模块下游 like-rpc 在 RAG 侧尚未实现，
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

    private final LikeApiProperties properties;

    public LikeRpcClient(LikeApiProperties properties) {
        this.properties = properties;
    }

    /**
     * 返回<b>已套上「单次调用上界 + 请求级剩余预算」双重 deadline</b> 的 stub。
     *
     * <p>⚠️ 刻意在<b>这个出口</b>统一套，而不是让 6 个 Service 各写一次：
     * Go 侧靠 {@code r.WithContext(ctx)}（timeouthandler.go:68）把 deadline 传进
     * <b>全部</b>下游调用，RAG 侧唯一等价的位置就是这个 getter ——
     * 所有调用点都经由 {@code likeRpc()}，改一处即全覆盖，漏一处就是一个「有时有 deadline、
     * 有时没有」的不一致（§3.3）。
     *
     * <h2>🔴 为什么是 {@code min(单次上界, HTTP 剩余预算)}（DRIFT-LIKEAPI-017）</h2>
     *
     * <p>Go 侧有<b>两个独立旋钮</b>，缺一不可：
     * <ol>
     *   <li><b>HTTP 总预算 3000ms</b> ——
     *       {@code service/common/observability/http.go:53}
     *       {@code ctx, cancel := context.WithTimeout(r.Context(), timeout)}（timeout 来自
     *       {@code rest/config.go:55} 的 {@code Timeout int64 json:",default=3000"}）。
     *       RAG 侧的等价物是 {@link GoStyleRequestDeadline}（ThreadLocal 载体）。</li>
     *   <li><b>单次 RPC 上界 2000ms</b> ——
     *       {@code zrpc/config.go:31} {@code Timeout int64 json:",default=2000"}
     *       → {@code zrpc/client.go:70-71} {@code WithTimeout(2000ms)}
     *       → {@code zrpc/internal/clientinterceptors/timeoutinterceptor.go:25}
     *       {@code ctx, cancel := context.WithTimeout(ctx, t)}。</li>
     * </ol>
     *
     * <p>Go 的 {@code context.WithTimeout(parent, 2s)} 在「父 ctx 已有更早 deadline」时
     * <b>取更早者</b>（Go {@code WithDeadline} 语义）⇒ 实际有效值 =
     * {@code min(2000ms, 3000ms − 已耗时)}。
     *
     * <p>⚠️ <b>修之前的漂移</b>：RAG 只套了第 ① 个旋钮（HTTP 剩余预算，≤3000ms），
     * <b>第 ② 个旋钮完全缺失</b> ⇒ 下游耗时落在 <b>2s ~ 3s</b> 这一段时
     * Go 返 {@code DEADLINE_EXCEEDED}（⇒ {@code 200 + 5001}）而 RAG <b>成功返回</b>。
     * 另外「无 HTTP 预算」时 Go 的有效值仍是<b>纯 2s</b>，而 RAG 原来是<b>完全没有 deadline</b> ——
     * 那是同一处缺陷的第二个面。
     *
     * <h2>⚠️ 为什么不再用 {@link GoStyleRequestDeadline#applyTo}</h2>
     *
     * <p>{@code applyTo(stub)} 的语义是「把<b>当前线程的剩余预算</b>原样套到 stub 上」，
     * 它<b>没有「上界」这个概念</b> ⇒ 用它就拿不到 {@code min} 的第二个操作数。
     * 而 {@code GoStyleRequestDeadline} 的<b>公开</b> API 已经有
     * {@link GoStyleRequestDeadline#isPresent()} 与
     * {@link GoStyleRequestDeadline#remainingMillis()} ⇒
     * <b>只读</b>这两个方法就能拿到剩余预算，<b>不需要改 common</b>
     * （2026-10-23 第五轮更正了台账里「需连带审 common 的 deadline 组件」的错误断言）。
     *
     * <h2>⚠️ 两处精度取舍（必须随结论一起读）</h2>
     * <ol>
     *   <li><b>ms 粒度</b>：{@code remainingMillis()} 是毫秒精度（Go 的 ctx 是纳秒）。
     *       ⇒ 有效值与 Go 最多差 <b>&lt; 1ms</b>，两侧在同一个毫秒窗口内都会以
     *       {@code DEADLINE_EXCEEDED} 收场 ⇒ <b>不可观察</b>。</li>
     *   <li><b>1ns 下限</b>：预算耗尽时 {@code remainingMillis()} 返回 {@code 0}，
     *       这里取 {@code max(1ns, …)} ⇒ 与 {@code GoStyleRequestDeadline.applyTo}
     *       的「预算耗尽 ⇒ 给 1 纳秒而不是原样返回」<b>同款</b>：
     *       Go 侧 ctx 到期时下游调用会立刻失败，若此刻「原样返回」（= 没有 deadline）
     *       就会变成一次「没有 deadline 的慢调用」—— 那正是要修的那个漂移。</li>
     * </ol>
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：{@code withDeadlineAfter} 每次返回新实例，
     * 缓存会把「第一次调用时的预算」固化下来，之后每个请求都带着过期 deadline。
     */
    public LikeServiceGrpc.LikeServiceBlockingStub likeRpc() {
        // 旋钮②：单次调用上界（Go: zrpc/config.go:31 的 struct tag 默认 2000）
        long capMillis = properties.getLikeRpc().getTimeout().toMillis();

        // 旋钮①：HTTP 总预算的剩余量；-1 表示「当前线程没有请求级 deadline」
        long remainingMillis = GoStyleRequestDeadline.remainingMillis();

        // Go: context.WithTimeout(parent, 2s) 在父 deadline 更早时取更早者 ⇒ min
        long effectiveMillis = (remainingMillis < 0L) ? capMillis : Math.min(capMillis, remainingMillis);

        // 1ns 下限：预算耗尽时也要「立刻到期」，不能退化成「没有 deadline」
        long effectiveNanos = Math.max(1L, effectiveMillis * 1_000_000L);

        return likeServiceBlockingStub.withDeadlineAfter(effectiveNanos, TimeUnit.NANOSECONDS);
    }
}
