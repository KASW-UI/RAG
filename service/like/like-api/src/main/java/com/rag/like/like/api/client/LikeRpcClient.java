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
 * <p><b>⚠️ client 熔断：Go 侧有且默认开，Java 侧<b>已接入</b>（共享层扫到即生效）</b>。
 * 证据：{@code zrpc/internal/config.go:14} 的
 * {@code ClientMiddlewaresConf.Breaker bool json:",default=true"}
 * + {@code zrpc/internal/client.go:112-113} 在 {@code c.middlewares.Breaker} 时
 * append {@code clientinterceptors.BreakerInterceptor}
 * + {@code zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18}
 * 的 {@code breaker.DoWithAcceptableCtx(...)}（每个 (下游服务, 方法) 一个 breaker）。
 * {@code etc/likecenter.yaml} 未配 {@code Middlewares} ⇒ 走 {@code default=true} ⇒ <b>是开的</b>。
 *
 * <p><b>⚠️ 2026-10-27 订正：本段此前写「Java 侧尚未接入（已登记的已知偏离）」⇒ 那是<b>过期断言</b>。</b>
 * 共享层 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor}
 * （{@code @Component} + {@code @GrpcGlobalClientInterceptor}）随本模块
 * {@code scanBasePackages}（含 {@code com.rag.common}）<b>扫到即生效</b>
 * ⇒ 失败判据逐条对齐 {@code zrpc/internal/codes/accept.go:9-17} 的
 * {@code codes.Acceptable}（只把 {@code DeadlineExceeded / Internal / Unavailable /
 * DataLoss / Unimplemented / ResourceExhausted} 记为失败）。
 * 本域 {@link com.rag.like.like.common.errmsg.LikeGrpcError} 刻意把业务码映到
 * {@code INVALID_ARGUMENT} / {@code UNKNOWN}（都在名单外）正是为此预留的前提。
 *
 * <p><b>运行期证据</b>（§3.3.3：「Bean 存在」≠「机制生效」）：
 * {@code LikeApiGrpcClientBreakerE2ETest} —— 真 in-process gRPC server ＋ 真 channel ＋ 真拦截器
 * ⇒ 断言「下游连续返 INTERNAL ⇒ 熔断打开 ⇒ <b>调用不再触达下游</b>」
 * ⇒ 证明的是<b>机制真在链上</b>。
 * ⚠️ 该守门<b>不用业务码做判别</b>：本域 {@code INTERNAL} 经 {@code bizCodeFromError} 映到 1015，
 * 而「熔断打开」在 <b>api 响应层</b>是 <b>5001</b>（见下）⇒ 两码<b>本有判别力</b>，但本守门改用
 * 「下游有没有真被调用」⇒ ⇒ <b>不要拿业务码当本守门的判别依据</b>。
 * <p><b>⚠️ 熔断打开这条路径：两侧业务码相同（都是 5001）</b>：
 * 熔断打开时抛的 {@code GoStyleCircuitOpenException} 不是 gRPC status ⇒ 消费点
 * {@code LikeApiSupport#rpcError} <b>先判 {@code status == null}</b> ⇒ 返回 {@code ERROR_SERVER_COMMON}
 * ⇒ <b>5001</b>；Go 侧 {@code status.FromError(err)} 的 {@code ok=false} 分支同样是
 * {@code errmsg.NewErrCode(errmsg.ErrorServerCommon)} ⇒ <b>5001</b> ⇒ ⇒ <b>两侧一致</b>。
 * ⚠️ 真正的 A4/D-7 差异是<b>下游非本域 status 码</b>（Go 13/3/5/16 ⇒ Java 1015/2001/1015/1009）
 * ⇒ 已于 2026-10-31 签字（{@code SD-2026-10-31-01}）⇒ <b>勿把两者混为一谈</b>。
 */
@Component
public class LikeRpcClient {

    @GrpcClient("like-rpc")
    private LikeServiceGrpc.LikeServiceBlockingStub likeServiceBlockingStub;

    public LikeRpcClient() {
        // ⚠️ 2026-10-31（P2-T04 · G2 lane）：域侧**不再自己算**客户端上界
        // ⇒ ⇒ 不再需要 LikeApiProperties（cap 真值由 yml 的
        // rag.protection.outbound-deadline.cap-millis 持有 ⇒ 见 likeRpc() 的 javadoc）
    }

    /**
     * 返回<b>出向 stub</b>（<b>本类不再自己套 deadline</b>）。
     *
     * <h2>🔴 2026-10-31（P2-T04 · G2 lane）：单次调用上界已下沉共享层</h2>
     * <p>原先本 getter 自己算 {@code min(单次上界 2000ms, HTTP 剩余预算)} 并套到 stub 上
     * ⇒ ⇒ 那是<b>域侧的第二份上界实现</b>（副本）⇒ ⇒ 现在<b>删掉</b>，理由两条：
     * <ul>
     *   <li><b>副本把上界写死在 {@code CallOptions} 里</b> ⇒ 共享层再对它取 min
     *       ⇒ ⇒ <b>min 只压小、不压大</b> ⇒ ⇒ 「想放宽上界」会被副本压回 2000ms
     *       ⇒ ⇒ 「上界可 env 覆盖」在<b>放宽方向</b>上被架空；</li>
     *   <li><b>共享层已在同一个位置做同样的事</b>：{@code GoStyleOutboundDeadlineInterceptor}
     *       带 devh 的 {@code @GrpcGlobalClientInterceptor} ⇒ ⇒ 装在<b>每个</b>出向 devh 调用上
     *       ⇒ ⇒ 与调用点无关 ⇒ ⇒ 其 {@code effectiveCallOptions} 取
     *       <b>min(入向剩余纳秒, 客户端硬上界 cap-millis, 调用点自带 deadline)</b>
     *       ⇒ ⇒ <b>入向预算的传播一并由它承担</b> ⇒ ⇒ 删掉本方法的内联后<b>传播不丢</b>
     *       （且它用<b>纳秒</b>精度，比原先域侧的毫秒精度更贴近 Go 的 ctx）。</li>
     * </ul>
     *
     * <p>⇒ ⇒ <b>Go 出处（形态同构）</b>：{@code go-zero@v1.10.2/zrpc/config.go:31}
     * {@code Timeout int64 json:",default=2000"} ⇒ {@code go-zero@v1.10.2/zrpc/client.go:70-71}
     * {@code if c.Timeout > 0 { opts = append(opts, WithTimeout(2000ms)) }}
     * ⇒ ⇒ Go 把上界加在<b>client 的 CallOptions</b> 上（⇒ 每个出向调用都带）
     * ⇒ ⇒ 与共享层拦截器<b>同位置同语义</b>。
     * <p>⇒ ⇒ 本域只配参数：{@code rag.protection.outbound-deadline.cap-millis=2000}
     * （{@code service/like/like-api/src/main/resources/application.yml} 的 {@code rag.protection} 段）
     * ⇒ ⇒ env 可覆盖，且各域独立（与 Go 的 {@code RpcClientConf.Timeout} 同为域级字段）。
     *
     * <h2>为什么「所有调用点都经由 {@code likeRpc()}」这件事仍然重要</h2>
     * <p>Go 侧靠 {@code r.WithContext(ctx)}（timeouthandler.go:68）把 deadline 传进
     * <b>全部</b>下游调用；RAG 侧的等价位置现在就是<b>共享层拦截器</b>（装在 channel 上 ⇒ 覆盖全部调用点）
     * ⇒ ⇒ 本 getter 的职责退化为「<b>取 stub</b>」，deadline 由通道统一施加
     * ⇒ ⇒ 漏一处调用点的风险从「有时有 deadline、有时没有」降为「<b>不可能</b>漏」。
     *
     * <p>⚠️ <b>不要把返回值缓存到字段上</b>：stub 每次取都应是<b>当前</b>实例
     * （缓存会把「第一次调用时的状态」固化下来）。
     */
    public LikeServiceGrpc.LikeServiceBlockingStub likeRpc() {
        // ⚠️ 刻意**不再**在这里套 deadline ⇒ 上界与入向传播都由共享层拦截器承担
        // （见本方法 javadoc 的「单次调用上界已下沉共享层」一节）
        return likeServiceBlockingStub;
    }
}
