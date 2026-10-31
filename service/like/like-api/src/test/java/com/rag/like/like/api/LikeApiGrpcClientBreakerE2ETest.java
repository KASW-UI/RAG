package com.rag.like.like.api;

import com.rag.like.like.api.controller.LikeController;
import com.rag.like.like.proto.LikeServiceGrpc;
import com.rag.users.common.jwt.JwtUtil;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.rag.like.like.api.support.RealTomcatPort;
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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>like-api 的 gRPC client 熔断「端到端」守门</b>：真 in-process gRPC server ＋ 真 channel ＋ 真拦截器。
 *
 * <h2>为什么本域必须有这一条</h2>
 * <p>共享层已有两层测试，但<b>都到不了 like-api 的真实调用链</b>：
 * <ul>
 *   <li>{@code GoStyleGrpcClientBreakerInterceptorTest}（共享层，9 例）直接 {@code new} 拦截器，
 *       验的是<b>判据与算法</b>，完全不经过 Spring 与 gRPC；</li>
 *   <li>共享层的 wiring 测试验的是「拦截器进了 starter 的 registry」。</li>
 * </ul>
 * 而 like-api 的其余测试<b>把 {@code LikeRpcClient} 整个用 {@code @MockitoBean} 顶掉</b>
 * ⇒ <b>熔断拦截器在那条路径上根本没被执行过</b>。
 * ⇒ 本类把三者连起来：真 server 恒返 {@code INTERNAL} ⇒ 断言<b>下游不再被调用</b>。
 *
 * <h2>🔴 为什么用「下游调用计数」而不是「业务码」来判别</h2>
 * <p>message-api 的同款守门用「5001 → 1015 的转变」判别（{@code GoStyleGrpcClientBreakerE2ETest}）。
 * <b>like 域不能用这招</b>：like 的 {@code LikeGrpcError#mapGrpcCodeToBizCode} 里
 * {@code INTERNAL} 落在 {@code default} 分支 ⇒ <b>也是 1015</b>；而熔断打开抛的
 * {@code GoStyleCircuitOpenException} 不是 gRPC status ⇒ 也落到 1015
 * ⇒ <b>两条路径的码完全一样 ⇒ 码没有判别力</b>。
 * <p>⇒ 本类改用<b>语义上更强</b>的判别信号：<b>下游有没有真的被调用</b>。
 * <pre>
 *   熔断拒绝发生在 next.newCall(...) **之前**
 *   （GoStyleGrpcClientBreakerInterceptor.java:293-300）
 *   ⇒ 熔断打开 ⇒ **一次都不会触达下游** ⇒ 服务端计数停止增长
 * </pre>
 * ⇒ 这个判别信号<b>不依赖业务码</b>，也就<b>不会</b>把「码的取值」锁死成守门
 * （码的取值的漂移属 A4 轴，另行处理 —— 见
 * {@code docs/DRIFT/like/vs-common.md} 的 A4 行）。
 *
 * <h2>Go 出处（两侧都应是 Y）</h2>
 * <pre>
 *   zrpc/internal/config.go:14          ClientMiddlewaresConf.Breaker bool `json:",default=true"`
 *   zrpc/internal/client.go:112-113    c.middlewares.Breaker ⇒ append clientinterceptors.BreakerInterceptor
 *   zrpc/internal/clientinterceptors/breakerinterceptor.go:13-18
 *                                       breaker.DoWithAcceptableCtx(...)（每个 (服务,方法) 一个 breaker）
 *   zrpc/internal/codes/accept.go:9-17 6 个失败码（DEADLINE_EXCEEDED/Internal/Unavailable/
 *                                       DataLoss/Unimplemented/ResourceExhausted）
 *   etc/likecenter.yaml                 未配 Middlewares ⇒ 走 default=true ⇒ **是开的**
 * </pre>
 */
@SpringBootTest(
        classes = {LikeApiApplication.class,
                LikeApiGrpcClientBreakerE2ETest.FaultyRpcServer.class},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                // 🔴 关键：**不指向真实端口**，而是 devh starter 的 in-process 通道
                // ⇒ 拦截器真的落在调用链上，且能拿到可控的 INTERNAL 状态码。
                "grpc.client.like-rpc.address=in-process:faulty-like-rpc",
                "grpc.client.like-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class LikeApiGrpcClientBreakerE2ETest {

    /**
     * <b>为什么不用 {@code RANDOM_PORT}</b>（2026-10-25 favorite 域实测踩出来的，
     * 见 {@link com.rag.like.like.api.support.RealTomcatPort} 的类注释）：
     * {@code RANDOM_PORT} 挑的是 OS <b>临时端口段</b>，而<b>别的会话的 Docker 容器正好在那一段
     * 发布端口</b>；macOS 上 {@code 127.0.0.1:P} 会<b>抢占</b> Tomcat 绑的 {@code 0.0.0.0:P}
     * ⇒ 客户端把请求打到了别的服务（实测那次打到了 PostgreSQL）⇒ <b>零字节即关</b>
     * ⇒ 报 {@code HTTP/1.1 header parser received no bytes} ⇒ <b>看着像被测代码炸了，
     * 其实压根没打到</b>。
     *
     * <p>⇒ 改成 <b>自己挑一个确定归我们的端口</b>（本域专用端口段 + 双绑定自检）。
     */
    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;
    private static final String PATH = "/like/v1/getlikecount";
    private static final String BODY = "{\"target_type\":\"article\",\"target_ids\":[\"t-1\"]}";

    private static Server grpcServer;

    /** 下游被调用的次数 —— 本守门的<b>判别信号</b>。 */
    private static final AtomicInteger DOWNSTREAM_CALLS = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor interceptor;

    @LocalServerPort
    private int port;
        /**
     * <b>本类必须绑到本域专用端口段</b>（而不是 Spring 的 {{@code RANDOM_PORT}}）。
     *
     * <p>⚠️ <b>为什么这条断言不能省</b>：{{@code RANDOM_PORT}} 挑的是 OS <b>临时端口段</b>
     * （macOS：49152–65535），而<b>别的会话的 Docker 容器正好在那一段发布端口</b>
     * ⇒ 客户端可能把请求打到别人的服务（实测那次打到了 PostgreSQL）⇒ <b>零字节即关</b>
     * ⇒ <b>看着像被测代码炸了，其实压根没打到</b> ⇒ 而且<b>所有用例照样绿</b>
     * （§3.3 的「静默失效」形态）。
     * ⇒ 这条断言的作用就是：<b>有人把 {{@code webEnvironment}} 改回 {{@code RANDOM_PORT}}
     * 时，这里立刻红</b>。
     */
    @Test
    @DisplayName("真容器必须绑到本域专用端口段（RANDOM_PORT 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段）")
    void bindsToDedicatedPortSegment() {{
        assertTrue(port >= RealTomcatPort.FIRST && port <= RealTomcatPort.LAST,
                "Tomcat 实际绑定的端口必须在 " + RealTomcatPort.FIRST + "-" + RealTomcatPort.LAST
                        + " 内，实际=" + port
                        + "（若落在 49152-65535，说明 webEnvironment 被改回 RANDOM_PORT 了）");
    }}

    @AfterAll
    static void shutdownServer() throws InterruptedException {
        if (grpcServer != null) {
            grpcServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            grpcServer = null;
        }
    }

    private static String bearer() {
        long now = System.currentTimeMillis() / 1000L;
        return "Bearer " + JwtUtil.getToken(SECRET, now, EXPIRE_SEC, CLAIM_UID);
    }

    private MvcResult callGetLikeCount() throws Exception {
        return mockMvc.perform(post(PATH)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())      // 业务错误恒 200 + envelope
                .andReturn();
    }

    private static String codeOf(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"code\"");
        if (i < 0) {
            return "NO_CODE:" + body;
        }
        int c = body.indexOf(':', i) + 1;
        int e = c;
        while (e < body.length() && Character.isDigit(body.charAt(e))) {
            e++;
        }
        return body.substring(c, e);
    }

    @Test
    @DisplayName("端到端：下游恒返 INTERNAL ⇒ 调用先真的到达下游，熔断打开后**一次都不再触达下游**"
            + "（Go: zrpc/internal/client.go:112-113 + clientinterceptors/breakerinterceptor.go:13-18）")
    void failuresEventuallyShortCircuitDownstreamCalls() throws Exception {
        // 钉死随机源：熔断的拒绝是概率性的（rand() < dropRatio）⇒ 不钉死就无法断言
        interceptor.setRandomSource(() -> 0.0D);

        // ── 阶段 1：证明「正常路径确实放行到下游」（否则后面的「不再触达」没有对照）
        int before = DOWNSTREAM_CALLS.get();
        for (int i = 0; i < 3; i++) {
            MvcResult r = callGetLikeCount();
            assertTrue(codeOf(r).matches("\\d+"),
                    "每次调用都应返回带业务码的 envelope（like 的业务错误恒 200 + envelope）；实际 body = "
                            + r.getResponse().getContentAsString());
        }
        int afterPassThrough = DOWNSTREAM_CALLS.get();
        assertEquals(before + 3, afterPassThrough,
                "前 3 次调用必须**真的触达下游** ⇒ 否则「后面不再触达」就证明不了熔断，"
                        + "只证明了下游一开始就连不上");

        // ── 阶段 2：持续失败 ⇒ 熔断打开 ⇒ 某次调用**不再触达下游**
        boolean sawShortCircuit = false;
        int callsAtShortCircuit = -1;
        for (int round = 0; round < 4 && !sawShortCircuit; round++) {
            for (int i = 0; i < 40; i++) {
                int c0 = DOWNSTREAM_CALLS.get();
                MvcResult r = callGetLikeCount();
                int c1 = DOWNSTREAM_CALLS.get();
                if (c1 == c0) {
                    sawShortCircuit = true;
                    callsAtShortCircuit = c1;
                    assertTrue(codeOf(r).matches("\\d+"),
                            "短路时也要返回带业务码的 envelope（熔断异常被 api 的异常处理转成 envelope）");
                    break;
                }
            }
            // ⚠️ 必须跨过 250ms 桶边界：breaker 的 history() 跳过当前半桶（Go 同语义）
            Thread.sleep(260L);
        }

        assertTrue(sawShortCircuit,
                "下游连续返回 INTERNAL 后，熔断必须打开并让调用**不再触达下游**；"
                        + "若下游调用次数一直增长，说明拦截器没落在真实调用链上，"
                        + "或熔断的失败判据没把 INTERNAL 计入失败（Go: zrpc/internal/codes/accept.go:9-17）"
                        + "；本次下游累计被调用 " + DOWNSTREAM_CALLS.get() + " 次");

        // ── 阶段 3：熔断打开后连续 3 次，**下游计数一次都不许涨**
        for (int i = 0; i < 3; i++) {
            callGetLikeCount();
        }
        assertEquals(callsAtShortCircuit, DOWNSTREAM_CALLS.get(),
                "熔断打开后每次调用都应被短路 ⇒ 下游计数不得再增长"
                        + "（短路发生在 next.newCall 之前 ⇒ 根本不该建 ClientCall）");

        // ── 阶段 4：breaker 的隔离单位 = (下游服务, 方法) ⇒ 本场景恒为 1 对
        assertEquals(1, interceptor.trackedMethods(),
                "breaker 键 = (服务身份, 方法名) ⇒ 本场景只有 like-rpc × GetLikeCount 一对。"
                        + "若大于 1，说明 key 没带 channel 身份（T-9 的语义）");
    }

    /**
     * 真 in-process gRPC server：{@code GetLikeCount} <b>恒返 INTERNAL</b> 并计数。
     *
     * <p>用 {@code BindableService}（而非 {@code ServiceImpl}）是为了让方法描述符与生成的 proto
     * 完全一致 —— 熔断的隔离单位是 {@code getFullMethodName()}，服务端的方法名必须与客户端请求的一致。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FaultyRpcServer {

        @Bean(destroyMethod = "shutdownNow")
        Server faultyGrpcServer() throws IOException {
            grpcServer = InProcessServerBuilder.forName("faulty-like-rpc")
                    .directExecutor()
                    .addService(new LikeServiceGrpc.LikeServiceImplBase() {
                        @Override
                        public void getLikeCount(
                                com.rag.like.like.proto.GetLikeCountReq request,
                                StreamObserver<com.rag.like.like.proto.GetLikeCountResp> observer) {
                            DOWNSTREAM_CALLS.incrementAndGet();
                            observer.onError(new StatusRuntimeException(Status.INTERNAL));
                        }
                    })
                    .build()
                    .start();
            return grpcServer;
        }
    }
}
