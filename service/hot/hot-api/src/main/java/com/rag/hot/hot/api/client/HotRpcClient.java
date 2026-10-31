package com.rag.hot.hot.api.client;

import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * hot-rpc gRPC client —— 对应 Go {@code api/internal/svc/serviceContext.go:16-18} 的
 * 窄接口 {@code HotRPC}（只暴露 {@code GetHotArticles}）。
 *
 * <pre>
 *   Go: HotRpc: hotRPCAdapter{client: hotpb.NewHotServiceClient(zrpc.MustNewClient(c.HotRpcConf))}
 * </pre>
 *
 * <h2>框架接管了什么</h2>
 * <ul>
 *   <li>{@code @GrpcClient("hot-rpc")} 由 devh grpc-client-spring-boot-starter 注入
 *       <b>阻塞</b> stub；寻址走 {@code grpc.client.hot-rpc.address=discovery:///hot-rpc}
 *       （Nacos discovery），对应 Go 的 {@code etcd Key: hot.rpc}
 *       （{@code etc/hotcenter.yaml:25-29}）。
 *       ⇒ Go 的 {@code zrpc.MustNewClient} + {@code NewHotServiceClient} + 手写 adapter
 *       三层由框架与 {@code BlockingStub} 吸收。</li>
 *   <li>用 BlockingStub 而非 future stub：Go 侧 api→rpc 是<b>同步阻塞</b>调用，
 *       这属 L3（并发与失败传播语义），不是可随手换的风格差异。</li>
 *   <li><b>client 熔断无需本类承担</b>：Go {@code zrpc/internal/config.go:14}
 *       {@code ClientMiddlewaresConf.Breaker bool `json:",default=true"`} 且 yaml 未覆盖
 *       ⇒ 默认开启；Java 侧由 {@code com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor}
 *       实现，扫到 {@code com.rag.common} 即生效。<b>不要</b>在这里再加一个拦截器。</li>
 * </ul>
 *
 * <h2>⚠️ retry：Go 侧没有，禁止加</h2>
 * {@code zrpc.RpcClientConf}（{@code zrpc/config.go:24-35}）<b>没有 {@code Retries} 字段</b>
 * （注意别与 {@code zrpc.ClientConf} 混淆，后者有 Retries，但 api 用的是前者）。
 * 加 retry 会把「一次失败 → 整包 5001」变成「重试 N 次后仍失败」，
 * 可观察的<b>调用次数与延迟</b>都变了。
 *
 * <h2>⚠️ deadline 由调用方传入的原因</h2>
 * {@link com.rag.common.observability.GoStyleRequestDeadline} 是 ThreadLocal，
 * 由 {@code HttpMiddleware} 设在跑业务的 worker 线程上；而本服务的补全跑在<b>线程池 worker</b>上，
 * 那里的 ThreadLocal 为空 ⇒ 在本类里读预算会静默得到「无限等待」。
 * 因此预算在请求线程上取一次后显式传入，见 {@link GoStyleGrpcCallDeadline}。
 *
 * <p>⚠️ <b>2s 的客户端上界不在本类</b>（🆕 2026-10-31 · G3 lane 第十三轮收敛）：
 * 上界由共享层 {@code GoStyleOutboundDeadlineInterceptor} 按本域 yml 的
 * {@code rag.protection.outbound-deadline.cap-millis} 施加
 * ⇒ ⇒ 本类（连同 {@link GoStyleGrpcCallDeadline}）只负责<b>传播请求预算</b>，<b>不封顶</b>
 * ⇒ ⇒ 二者合起来 = min(2s, 请求剩余预算) = Go 的 {@code context.WithTimeout(ctx, 2s)}。
 */
@Component
public class HotRpcClient {

    @GrpcClient("hot-rpc")
    private HotServiceGrpc.HotServiceBlockingStub hotServiceBlockingStub;

    /**
     * 取热度 TopK —— 对应 Go {@code hotpb.HotServiceClient.GetHotArticles}。
     *
     * <p>⚠️ <b>不吞异常</b>：Go 侧这一层失败是 <b>fatal</b>（整包回 5001），
     * 与 article-rpc 的 best-effort 相反。这个不对称由 {@code HotArticleQueryService} 决定，
     * client 层不预判。
     *
     * @param topK             Go {@code getHotArticlesLogic.go:63} 硬编码 {@code maxHotArticles=100}
     * @param remainingBudgetMs 请求剩余预算；{@code -1} 表示无 HTTP 超时预算
     */
    public GetHotArticlesResponse getHotArticles(int topK, long remainingBudgetMs) {
        return GoStyleGrpcCallDeadline.apply(hotServiceBlockingStub, remainingBudgetMs)
                .getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(topK).build());
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    HotServiceGrpc.HotServiceBlockingStub rawStub() {
        return hotServiceBlockingStub;
    }

    /** 仅供测试注入捕获型 stub（package-private，非生产 API）。 */
    void rawStubField(HotServiceGrpc.HotServiceBlockingStub stub) {
        this.hotServiceBlockingStub = stub;
    }
}