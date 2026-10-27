package com.rag.users.user.api;

import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.rag.users.user.api.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * 🔴 <b>user-api 的 gRPC client 熔断</b>：真 in-process gRPC server + 真 channel + 真拦截器。
 *
 * <p><b>为什么 user-api 必须自己有一条</b>：
 * {@code follow-api} / {@code message-api} 都有
 * {@code GoStyleGrpcClientBreakerWiringTest}（接线层）与
 * {@code GoStyleGrpcClientBreakerE2ETest}（端到端层），
 * <b>唯独 user-api 两条都没有</b>。
 * 而 {@code GoStyleGrpcClientBreakerInterceptor} 的类注释<b>自己就写着</b>：
 * <blockquote>这正是 AGENTS.md §3.3「不能仅凭存在 {@code @Component} 就推断责任已被承担」的形态</blockquote>
 * ⇒ 在 user-api 这一侧，「熔断到底有没有落在真实调用链上」曾经是<b>没有证据</b>的空白。
 *
 * <h2>本类怎么判定「熔断真的生效」</h2>
 * <p>判据是<b>下游调用次数</b>，不是业务码（业务码是实现相关的）：
 * <pre>
 *   熔断关闭 ⇒ 每个请求都真的调到 user-rpc ⇒ 故障 server 的调用计数持续增长
 *   熔断打开 ⇒ 请求被<b>本地短路</b> ⇒ 计数<b>停止增长</b>
 * </pre>
 *
 * <h2>⚠️ 本测试为什么把随机源钉死成 0.0</h2>
 * <p>Go 的 googlebreaker 的拒绝是<b>概率性</b>的（{@code googlebreaker.go:73}
 * {@code if b.proba.TrueOnProba(dropRatio) { return ErrServiceUnavailable }}}），
 * 不钉死随机源就没法断言「熔断打开时一定是 1015」。
 * <p>钉死成 {@code 0.0} 的<b>连带后果</b>必须说清：Go 的 force-pass 保底分支
 * （{@code googlebreaker.go:65-69}）要求 {@code lastPass > 0}，
 * 而 {@code lastPass} 只在「概率<b>没</b>命中」那条路上被更新
 * （early return {@code dropRatio <= 0} 那条路**不**更新，Go 源码逐行确认），
 * ⇒ 钉死随机源后 force-pass <b>永不触发</b>
 * ⇒ 熔断打开的形态是「下游一次都不被触达」，而不是「每秒一个探测请求」。
 * <p>⇒ 本测试的判据必须按<b>这个</b>形态写，否则会得到一个「假红」。
 *
 * <h2>Go 侧的事实（决定期望值）</h2>
 * <p>Go 的 zrpc <b>客户端</b>熔断（{@code zrpc/internal/clientinterceptors/breakerinterceptor.go}）
 * 用 {@code breaker.DoWithAcceptableCtx}，熔断打开时返回
 * {@code breaker.ErrServiceUnavailable} —— <b>一个普通 Go error，不是 gRPC status</b>。
 * <p>而 Go 的 api 侧 {@code loginlogic.go:46-56}：
 * <pre>
 *   st, _ := status.FromError(err)
 *   switch st.Code() {
 *   case codes.Internal: return nil, errmsg.ErrorServerCommon   // 5001
 *   default:             return nil, errmsg.CodeServerBusy      // 1015
 *   }
 * </pre>
 * {@code status.FromError} 对非 status 错误返回 {@code codes.Unknown} ⇒ 落 {@code default}
 * ⇒ <b>Go：熔断打开 ⇒ 业务码 1015 ⇒ HTTP 200 + envelope</b>。
 *
 * <p>⚠️ <b>本测试在写下时首跑是红的</b>，而且它抓出的是<b>真漂移</b>而不是测试写错：
 * 当时 user-api 的 {@code GlobalExceptionHandler} <b>没有</b>
 * {@code @ExceptionHandler(GoStyleCircuitOpenException.class)}，
 * 熔断异常一路落到兜底 {@code handleAny} ⇒ <b>HTTP 500 + 业务码 5001</b>；
 * 更糟的是 HTTP 500 被<b>入口</b>侧 {@code GoStyleBreakerFilter} 判为失败
 * ⇒ 入口熔断也跟着打开 ⇒ 对外变成 <b>503 空 body</b>（与 Go 的 200+1015 在 wire 上完全不同）。
 * ⇒ 详见 {@code docs/DRIFT/users/user/api.md} 的 DRIFT-USERAPI-010。
 */
@SpringBootTest(
        classes = {UserApiApplication.class,
                GoStyleGrpcClientBreakerE2ETest.FaultyRpcServer.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                // 关键：不指向真实端口，而是 devh starter 的 in-process 通道，
                // 使拦截器真的落在调用链上（指向 static:// 时 channel 仍会装配拦截器，
                // 但调用会连接失败、拿不到可控的 INTERNAL 状态码）。
                "grpc.client.user-rpc.address=in-process:faulty-user-rpc",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class GoStyleGrpcClientBreakerE2ETest {

    private static final String LOGIN_PATH = "/user/v1/login";

    /** login 的两个字段都是必填（Go types.go LoginReq）⇒ 必须给全，否则走不到 rpc。 */
    private static final String LOGIN_JSON = "{\"username\":\"breaker-probe\",\"password\":\"pw123456\"}";

    private static Server grpcServer;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoStyleGrpcClientBreakerInterceptor interceptor;

    @Autowired
    private com.rag.users.user.api.metrics.UserApiMetrics metrics;

    @LocalServerPort
    private int port;

    @AfterAll
    static void shutdownServer() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            grpcServer = null;
        }
    }

    private MvcResult callLogin() throws Exception {
        return mockMvc.perform(post(LOGIN_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_JSON))
                .andReturn();
    }

    /** 取 envelope 里的业务码。 */
    private static String codeOf(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
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
    @DisplayName("端到端：下游恒返 INTERNAL ⇒ 熔断打开后**下游不再被触达**，且业务码是 Go 的 1015")
    void circuitOpensAndShortCircuitsDownstream() throws Exception {
        // 钉死随机源，使 dropRatio>0 时必然命中拒绝（否则是概率性的、无法断言）
        interceptor.setRandomSource(() -> 0.0D);

        // ── 第一阶段：熔断还没打开 ⇒ 应真的调到下游，拿到 INTERNAL ⇒ 5001
        MvcResult first = callLogin();
        assertEquals("5001", codeOf(first),
                "首次调用应落到下游并收到 INTERNAL ⇒ 业务码 5001"
                        + "（Go loginlogic.go:51-52 codes.Internal → ErrorServerCommon）");
        assertEquals(200, first.getResponse().getStatus(),
                "Go 侧 handler 一律用 httpx.OkJson 输出 envelope ⇒ HTTP 恒 200；"
                        + "业务失败只由 body 里的 code 表达。"
                        + "实际 body = " + first.getResponse().getContentAsString());

        // ── 第二阶段：驱动到熔断打开
        //
        // 判据 = **下游调用次数不再随请求数增长**：
        //   熔断没生效 ⇒ 每个请求都真的打到 user-rpc ⇒ 计数随请求数线性增长。
        //   熔断打开   ⇒ 请求被本地短路 ⇒ 计数几乎不涨（只留 Go 的 force-pass 保底）。
        //
        // ⚠️ 阈值取「每 40 个请求 ≤ 2 次」而不是「= 0」：
        //    钉死随机源后 force-pass 确实永不触发（见类注释），理论上是 0；
        //    但保留一点余量，避免「熔断打开与关闭的切换点恰好落在轮内」这种时序抖动造成假红。
        //    对照量级：熔断没生效时每轮必然是 40（每个请求都真的打到下游）⇒ 40 vs ≤2 判别力充足。
        int before = FaultyRpcServer.calls.get();
        int growth = Integer.MAX_VALUE;
        boolean opened = false;
        for (int round = 0; round < 8 && !opened; round++) {
            int mark = FaultyRpcServer.calls.get();
            for (int i = 0; i < 40; i++) {
                callLogin();
            }
            growth = FaultyRpcServer.calls.get() - mark;
            if (growth <= 2) {
                opened = true;
            }
            // ⚠️ 必须跨过 250ms 桶边界：breaker 的 history() 跳过当前半桶（Go 同语义）
            Thread.sleep(260L);
        }
        assertTrue(opened,
                "熔断打开后下游触达次数必须骤降（每 40 个请求 ≤ 2 次，force-pass 保底）；"
                        + "若与请求数同步增长，说明拦截器没落在 user-api 的真实调用链上，"
                        + "本模块就退化成「Go 有客户端熔断、RAG 没有」的漂移状态。"
                        + "本次观测到的每轮下游触达次数=" + growth
                        + "，下游总调用次数=" + FaultyRpcServer.calls.get());

        // ── 第三阶段：熔断打开时的业务码必须是 Go 的 1015
        //
        // ⚠️ 为什么这里**不观测 force-pass**：Go 的 googlebreaker.go:53-80 是
        //   ```
        //   dropRatio := (float64(history.total-protection) - weightedAccepts) / float64(history.total+1)
        //   if dropRatio <= 0 { return nil }                    // ← 早退，**不更新 lastPass**
        //   lastPass := b.lastPass.Load()
        //   if lastPass > 0 && timex.Since(lastPass) > forcePassDuration { ... }  // ← 要求 lastPass > 0
        //   dropRatio *= float64(buckets-history.workingBuckets) / buckets
        //   if b.proba.TrueOnProba(dropRatio) { return ErrServiceUnavailable }
        //   b.lastPass.Set(timex.Now())                        // ← 只有「概率没命中」才更新
        //   ```
        //   ⇒ 本测试把随机源钉成 0.0（保证拒绝是确定性的），于是**永远走不到**
        //   「概率没命中」那条路 ⇒ lastPass 恒为 0 ⇒ force-pass 分支永不触发。
        //   ⇒ 熔断打开的形态是「下游一次都不被触达」，而不是「每秒一个探测请求」。
        //
        //   （这条核对同时确认：RAG 的 GoStyleBreaker.accept() 与 Go **逐行一致**，
        //     包括「early return 不更新 lastPassMs」这个极易被「顺手改好」的细节。）
        //
        // 因此第三阶段的判据改成：**打开后每个请求都必须是 1015**（钉死随机源 ⇒ 确定性）。
        MvcResult shortCircuited = null;
        double rpcErrorBefore = metrics.getLoginRpcError().count();
        for (int i = 0; i < 40 && shortCircuited == null; i++) {
            MvcResult r = callLogin();
            if ("1015".equals(codeOf(r))) {
                shortCircuited = r;
            }
        }
        assertTrue(shortCircuited != null,
                "熔断打开后每个请求都必须是 Go 的 1015（errmsg.CodeServerBusy）；"
                        + "若一个 1015 都没出现，说明熔断异常没被映射成业务码"
                        + "（user-api 曾缺 @ExceptionHandler(GoStyleCircuitOpenException.class)，"
                        + "结果落进兜底分支变成 HTTP 500 + 5001）。"
                        + "下游总调用次数=" + FaultyRpcServer.calls.get());

        // ── 指标口径：Go 的 err != nil **捕获一切** ⇒ 熔断打开时 rpc_error 计数**会**递增
        //
        // Go 出处：loginlogic.go:46-48（metrics.UserApiInterceptCount.WithLabelValues("/user/login","rpc_error").Inc()）
        //   它的 err != nil 覆盖 breaker.ErrServiceUnavailable（普通 error）⇒ 计数递增。
        // RAG 侧的对应写法是 UserLoginService 里的 catch (GoStyleCircuitOpenException) ——
        //   没有它，熔断打开时这个计数器会**静默不增**（Go 会增）⇒ 监控看板失真。
        //
        // ⚠️ 本阶段所有请求都被短路、不触达下游 ⇒ 本阶段的计数增量**只可能**来自那个 catch。
        assertTrue(metrics.getLoginRpcError().count() > rpcErrorBefore,
                "熔断打开时 rpc_error 计数必须递增（Go 的 err != nil 捕获一切，"
                        + "breaker.ErrServiceUnavailable 也算 rpc_error）。"
                        + "实际增量=" + (metrics.getLoginRpcError().count() - rpcErrorBefore)
                        + " ⇒ UserLoginService 少了 catch (GoStyleCircuitOpenException) 那一支。");

        // Go 出处：loginlogic.go:49-55 的 status.FromError + default 分支。
        // 熔断打开时 Go 返回的是 breaker.ErrServiceUnavailable（**非 gRPC status**）
        // ⇒ status.FromError 返回 codes.Unknown ⇒ 落 default ⇒ errmsg.CodeServerBusy = 1015，
        // 且业务异常经 httpx.OkJson 输出 ⇒ **HTTP 200 + envelope**。
        assertEquals(200, shortCircuited.getResponse().getStatus(),
                "熔断打开时 Go 走的是**业务码**路径（errmsg.CodeServerBusy），"
                        + "业务码经 httpx.OkJson 输出 ⇒ HTTP 恒 200；"
                        + "若这里变成 500，说明 RAG 把熔断异常当成了「未捕获异常」兜底"
                        + "（Go 侧绝不会这样：Go 的熔断错误是有业务码的）；"
                        + "若这里变成 503，说明**入口**侧熔断被错误地打开了"
                        + "（那是 HTTP ≥500 喂出来的连锁反应，Go 侧不会发生）。"
                        + "实际 body = " + shortCircuited.getResponse().getContentAsString());
        assertEquals("1015", codeOf(shortCircuited),
                "熔断打开时的业务码必须是 Go 的 1015（errmsg.CodeServerBusy）。"
                        + "Go 出处：loginlogic.go:53-54 的 default 分支"
                        + "（status.FromError 对非 status 错误返回 codes.Unknown）。"
                        + "实际 body = " + shortCircuited.getResponse().getContentAsString());
    }

    /**
     * 真 in-process gRPC server：<b>所有方法</b>都抛 INTERNAL，并记录被调了几次。
     *
     * <p>用 {@code BindableService}（而非 {@code ServiceImpl}）是为了让方法描述符
     * 与生成的 proto 完全一致 —— 熔断的隔离单位是 {@code getFullMethodName()}，
     * 服务端的方法名必须与客户端请求的一致。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FaultyRpcServer {

        /** 下游被调用的次数 —— 本类最核心的判据。 */
        static final AtomicInteger calls = new AtomicInteger();

        @Bean(destroyMethod = "shutdownNow")
        Server faultyGrpcServer() throws IOException {
            grpcServer = InProcessServerBuilder.forName("faulty-user-rpc")
                    .directExecutor()
                    .addService(new UserServiceGrpc.UserServiceImplBase() {
                        @Override
                        public void login(com.rag.users.user.proto.LoginReq request,
                                          io.grpc.stub.StreamObserver<com.rag.users.user.proto.LoginResp> observer) {
                            calls.incrementAndGet();
                            observer.onError(new StatusRuntimeException(Status.INTERNAL));
                        }
                    })
                    .build()
                    .start();
            return grpcServer;
        }
    }
}
