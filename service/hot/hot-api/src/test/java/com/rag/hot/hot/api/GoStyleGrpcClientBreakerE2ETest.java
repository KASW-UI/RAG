package com.rag.hot.hot.api;

import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotServiceGrpc;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>gRPC client 熔断的行为级证据</b> —— 闭合台账 <b>U4 / D8</b>。
 *
 * <h2>为什么前两层测试都不够</h2>
 * <ul>
 *   <li>{@code com.rag.common.protection} 里的算法单测（8 例）直接 {@code new} 拦截器，
 *       验的是<b>判据与算法</b>，完全不经过 Spring 与 gRPC；</li>
 *   <li>{@link GoStyleGrpcClientBreakerWiringTest}（3 例，第四轮新增）验的是
 *       <b>拦截器进了 starter 的 registry、且 factory 认识本域的 @GrpcClient 锚点</b>
 *       ⇒ 只到「<b>装上了</b>」，不到「<b>真的会在超时/失败后打开</b>」。</li>
 * </ul>
 * 本类把两者连起来：真 in-process gRPC server（恒返 INTERNAL）+ 真 channel + 真拦截器
 * ⇒ 断言「熔断打开后<b>不再触达下游</b>」。
 *
 * <h2>⚠️ 为什么判据是「<b>下游调用次数</b>」而不是「响应码」</h2>
 * <p>Go 侧的熔断抛的是 <b>普通 error</b>（{@code breaker.ErrServiceUnavailable}），
 * 经 {@code MessageGrpcError#bizCodeFromError} 落到 {@code CODE_SERVER_BUSY(1015)}
 * ⇒ <b>Go 的客户端能从响应码看出熔断打开了</b>。
 * <p>而 <b>hot 域的 hot-api 做不到</b>：
 * <ul>
 *   <li>{@code HotArticleQueryService.fetchTopK} 只 catch {@code StatusRuntimeException}
 *       ⇒ 熔断抛的 {@code GoStyleCircuitOpenException}（普通 RuntimeException）
 *       <b>不被它捕获</b> ⇒ 一路冒到 {@code GlobalExceptionHandler.onAnyException}
 *       ⇒ 仍然产出 <b>5001</b>；</li>
 *   <li>⇒ <b>「熔断打开」与「下游报 Internal」在 hot-api 的响应体里长得一模一样。</b></li>
 * </ul>
 * <p>⇒ 唯一能区分的判据是<b>下游被调了几次</b>：
 * 熔断没开 ⇒ 每次请求都打到下游；熔断开了 ⇒ 下游<b>几乎不再被调用</b>
 * （只剩 Go 同款的 <b>force-pass 保底</b>：{@code googlebreaker.go:66-71}，
 * 每 1s 必定放一个请求进去做探测 ⇒ 增长是「每 ~1 次」而不是「每请求 1 次」）。
 *
 * <h2>⚠️ 为什么必须 {@code setRandomSource(() -> 0.0D)}</h2>
 * <p>Go 的熔断是<b>概率性</b>的：{@code googlebreaker.go:73-75} 只有 {@code rand() < dropRatio}
 * 才拒绝 ⇒ 不钉死随机源就没法断言（「有时拒绝有时放行」）。
 * <p>⇒ 钉成 0.0 ⇒ {@code dropRatio > 0} 时<b>必然</b>命中拒绝。
 *
 * <h2>⚠️ 为什么每轮之间要 {@code sleep(260ms)}</h2>
 * <p>breaker 的统计窗口是 <b>10s / 40 个 250ms 桶</b>，
 * 而 {@code history()} <b>跳过当前半桶</b>（Go {@code collection/rollingwindow.go:65-80}
 * 的 {@code ignoreCurrent}，注释：<i>ignore the current bucket, because of partial data</i>）
 * ⇒ 不跨过 250ms 边界就<b>永远攒不出失败桶</b> ⇒ 熔断永远打不开。
 *
 * <h2>Go 出处</h2>
 * <pre>
 *   go-zero v1.10.2 core/breaker/googlebreaker.go:12-21   window=10s buckets=40 k=1.5 minK=1.1 protection=5
 *   go-zero v1.10.2 core/breaker/googlebreaker.go:52-79   accept() 的判据（含概率性拒绝 + force-pass）
 *   go-zero v1.10.2 zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18
 * </pre>
 */
@SpringBootTest(
        classes = {HotApiApplication.class,
                GoStyleGrpcClientBreakerE2ETest.FaultyHotRpcServer.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ 必须关掉 Nacos 自动注册 —— RANDOM_PORT 真起 web server 时，
                //    注册 Nacos 连不上会抛异常 ⇒ **整个上下文起不来**（ErrCode:-401）。
                "spring.cloud.service-registry.auto-registration.enabled=false",
                // 关键：不指向真实端口，而是 devh starter 的 **in-process** 通道
                // ⇒ 调用真的落到我们控制的 server 上（能拿到确定的 INTERNAL 状态码），
                //    且 channel 依然会装配全局拦截器 ⇒ 熔断真的在链上。
                "grpc.client.hot-rpc.address=in-process:faulty-hot-rpc",
                "grpc.client.hot-rpc.negotiation-type=plaintext",
                // article-rpc 不会被打到（hot-rpc 先 fatal），给个不可达地址即可
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class GoStyleGrpcClientBreakerE2ETest {

    /** 熔断关闭时，一次窗口内发起的请求数（用来对比「打开后」）。 */
    private static final int WINDOW = 40;

    private static Server grpcServer;

    /** 下游被调用的次数 —— 熔断打开后它<b>必须停止增长</b>。 */
    private static final AtomicInteger downstreamCalls = new AtomicInteger();

    @Autowired
    private GoStyleGrpcClientBreakerInterceptor interceptor;

    @LocalServerPort
    private int port;

    @AfterAll
    static void shutdownServer() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            grpcServer = null;
        }
    }

    private HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /** 打一次真实 HTTP 请求，返回响应体。 */
    private String callHotArticles() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/hot/v1/articles"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = client().send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        return resp.body();
    }

    /** 从信封里取 {@code code}。 */
    private static String codeOf(String body) {
        int i = body.indexOf("\"code\"");
        if (i < 0) {
            return "NO_CODE:" + body;
        }
        String tail = body.substring(i);
        int c = tail.indexOf(':') + 1;
        while (c < tail.length() && Character.isWhitespace(tail.charAt(c))) {
            c++;
        }
        int e = c;
        while (e < tail.length() && Character.isDigit(tail.charAt(e))) {
            e++;
        }
        return tail.substring(c, e);
    }

    @Test
    @DisplayName("端到端：下游恒返 INTERNAL ⇒ 熔断真的打开（下游调用次数停止增长），"
            + "但 hot-api 的响应码**前后都是 5001**（熔断在响应体里看不见）")
    void internalErrorsEventuallyOpenTheBreakerAndShortCircuitDownstream() throws Exception {
        // 钉死随机源 ⇒ dropRatio>0 时必然拒绝（Go 同款概率性拒绝 ⇒ 不钉死就没法断言）
        interceptor.setRandomSource(() -> 0.0D);

        downstreamCalls.set(0);
        int httpRequests = 0;

        // ── 阶段 A：调用确实**到达了下游** ⇒ 证明计数器有意义（否则后面全是空断言）──
        //
        // ⚠️ 这里**不能**断言「前 40 次都打到下游」—— 第一版就是这么写的，结果**测试直接红**：
        // 实测只有 16 次到达。根因（Go 同款语义）：
        //   go-zero googlebreaker.go:52-79 的 dropRatio = (total - protection - weightedAccepts)/(total+1)，
        //   而 `total` 来自 `history()` ⇒ `collection/rollingwindow.go:65-80` 的 Reduce **ignoreCurrent**
        //   （注释：ignore the current bucket, because of partial data）
        //   ⇒ **只有已完成的 250ms 桶**才计入统计 ⇒ 前几次调用所在的「当前半桶」不计数
        //      ⇒ total 长时间为 0 ⇒ dropRatio ≤ 0 ⇒ 全部放行。
        //   ⇒ 第一个桶完成后，桶内累计的失败才把 total 推过 protection(5) ⇒ 从那时起开始拒绝。
        // ⇒ 这里只断言「**确实到达过下游**」+「响应码是 5001」；
        //    「打开之后不再到达」由阶段 C 断言（那才是本测试的主命题）。
        for (int i = 0; i < 3; i++) {
            assertThat(codeOf(callHotArticles())).isEqualTo("5001");
            httpRequests++;
        }
        assertThat(downstreamCalls.get())
                .as("前几次调用必须**真的到达下游**（否则计数器没意义，阶段 C 的断言是空的）")
                .isGreaterThan(0);

        // ── 阶段 B：持续施压，直到出现「某次调用**没有**到达下游」⇒ 熔断打开 ──────
        boolean sawShortCircuit = false;
        int shortCircuitAt = -1;
        for (int i = 0; i < 300 && !sawShortCircuit; i++) {
            int before = downstreamCalls.get();
            callHotArticles();
            httpRequests++;
            if (downstreamCalls.get() == before) {
                sawShortCircuit = true;
                shortCircuitAt = i;
            }
            // 跨过 250ms 桶边界：统计只认**已完成**的桶（Go 同语义，见上）
            if (i % 40 == 39) {
                Thread.sleep(260L);
            }
        }
        assertThat(sawShortCircuit)
                .as("下游连续返回 INTERNAL 后，熔断必须打开并**短路掉对下游的调用**；"
                        + "若始终每次都到达下游，说明拦截器没落在真实调用链上，"
                        + "或 accept() 的判据被改坏了（GO: breakerinterceptor.go:13-18）")
                .isTrue();

        // ── 阶段 C：熔断打开 ⇒ 下游**几乎不再被调用** ────────────────────────
        // ⚠️ 不是「完全不被调用」：Go 的 force-pass（googlebreaker.go:66-71）
        //    每 1s 必定放一个请求进去做探测 ⇒ 允许少量增长。
        int beforeOpen = downstreamCalls.get();
        for (int i = 0; i < WINDOW; i++) {
            callHotArticles();
        }
        httpRequests += WINDOW;
        int growthWhileOpen = downstreamCalls.get() - beforeOpen;

        assertThat(growthWhileOpen)
                .as("熔断打开后，下游调用次数必须**停止增长**（只剩 Go 同款的 force-pass 保底："
                        + "googlebreaker.go:66-71 每 1s 放一个探测请求进去）；"
                        + "实际增长 = " + growthWhileOpen + " ⇒ 若接近请求数，说明熔断**根本没打开**")
                .isLessThanOrEqualTo(5);

        // ── 阶段 D：响应码**全程都是 5001** ⇒ 熔断在 hot-api 的响应体里不可见 ──
        // ⚠️ 这条是 hot 域与 Go 的**已知不对称**（Go 侧会变成 1015）：
        //    hot-api 的 fetchTopK 只 catch StatusRuntimeException，
        //    熔断抛的 GoStyleCircuitOpenException（普通 RuntimeException）冒到兜底分支 ⇒ 5001。
        //    ⇒ 断言它「仍然是 5001」= 把这条不对称**钉住**，避免有人以后顺手改了它。
        assertThat(codeOf(callHotArticles())).isEqualTo("5001");

        // ── 阶段 E：拦截器确实为 hot-rpc 的方法建了 breaker（不是别的拦截器在干活）──
        assertThat(interceptor.trackedMethods())
                .as("拦截器应只为 hot.HotService/GetHotArticles 建 1 个 breaker")
                .isEqualTo(1);

        // ── 阶段 F：总账 —— HTTP 请求数 ≫ 下游调用数（熔断确实在挡流量）──────────
        assertThat(downstreamCalls.get())
                .as("熔断打开后，总下游调用数必须**远小于**总 HTTP 请求数")
                .isLessThan(httpRequests / 2);
    }

    /**
     * 真 in-process gRPC server：<b>恒返 INTERNAL</b>，并统计被调次数。
     *
     * <p>用 {@code BindableService}（而非 {@code ServiceImpl}）是为了让方法描述符
     * 与生成的 proto 完全一致 —— 熔断的隔离单位是 {@code getFullMethodName()}，
     * 服务端的方法名必须与客户端请求的一致。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FaultyHotRpcServer {

        @Bean(destroyMethod = "shutdownNow")
        Server faultyHotRpcServer() throws IOException {
            grpcServer = InProcessServerBuilder.forName("faulty-hot-rpc")
                    .directExecutor()
                    .addService(new HotServiceGrpc.HotServiceImplBase() {
                        @Override
                        public void getHotArticles(GetHotArticlesRequest request,
                                                  StreamObserver<GetHotArticlesResponse> observer) {
                            downstreamCalls.incrementAndGet();
                            observer.onError(new StatusRuntimeException(Status.INTERNAL));
                        }
                    })
                    .build()
                    .start();
            return grpcServer;
        }
    }
}
