package com.rag.message.message.api;

import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.common.protection.GoStyleGrpcClientBreakerInterceptor;
import com.rag.message.message.api.MessageApiApplication;
import com.rag.message.message.proto.MessageServiceGrpc;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.rag.message.message.api.support.RealTomcatPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * gRPC client 熔断的<b>端到端</b>守门：真 in-process gRPC server + 真 channel + 真拦截器。
 *
 * <p><b>为什么前两层测试都不够</b>：
 * <ul>
 *   <li>{@link GoStyleGrpcClientBreakerInterceptorTest}（8 例）直接 {@code new} 拦截器，
 *       验的是<b>判据与算法</b>，完全不经过 Spring 与 gRPC；</li>
 *   <li>{@link GoStyleGrpcClientBreakerWiringTest}（2 例）验的是<b>拦截器进了 starter 的 registry</b>。</li>
 * </ul>
 * 但本项目其余测试全部把 {@code MessageRpcClient} 用 {@code @MockitoBean} 顶掉，
 * ⇒ <b>熔断拦截器在那条路径上根本没被执行过</b>。
 * 本类把它们连起来：server 恒返回 {@code INTERNAL}，断言 HTTP 响应码先��� 5001、
 * 后变<b>1015</b> —— 这个「5001 → 1015」的转变<b>只有在熔断真的生效时才会出现</b>。
 *
 * <p><b>为什么必须靠 1015 来判定</b>：
 * {@code MessageGrpcError#bizCodeFromError} 的映射是
 * {@code INTERNAL → ERROR_SERVER_COMMON(5001)}；
 * 熔断打开后抛的 {@code GoStyleCircuitOpenException} 因不是 gRPC status，
 * {@code extractStatus} 返回 null ⇒ 落到末尾的 {@code CODE_SERVER_BUSY(1015)}。
 * ⇒ <b>5001 是「调到了下游但它报 Internal」，1015 是「根本没调下游」</b>，语义截然不同。
 */
@SpringBootTest(
        classes = {MessageApiApplication.class,
                GoStyleGrpcClientBreakerE2ETest.FaultyRpcServer.class},
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                // 关键：不指向真实端口，而是 devh starter 的 in-process 通道，
                // 使拦截器真的落在调用链上（指向 static:// 时 channel 仍会装配拦截器，
                // 但调用会连接失败、拿不到可控的 INTERNAL 状态码）。
                "grpc.client.message-rpc.address=in-process:faulty-message-rpc",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
class GoStyleGrpcClientBreakerE2ETest {

    /**
     * <b>为什么不用 {@code RANDOM_PORT}</b>（2026-10-25 favorite 域实测踩出来的，
     * 见 {@link com.rag.message.message.api.support.RealTomcatPort} 的类注释）：
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

    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 3600L;
    private static final long CLAIM_UID = 31337L;
    private static final String UNREAD_PATH = "/message/v1/unread";

    private static Server grpcServer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GoStyleGrpcClientBreakerInterceptor interceptor;

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

    private static String userToken() {
        return JwtUtil.getToken(USER_SECRET, System.currentTimeMillis() / 1000L, EXPIRE_SEC, CLAIM_UID);
    }

    private MvcResult callUnread() throws Exception {
        return mockMvc.perform(get(UNREAD_PATH).header("Authorization", "Bearer " + userToken()))
                .andExpect(status().isOk())      // 业务错误恒 200 + envelope
                .andReturn();
    }

    private static String codeOf(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"code\"");
        if (i < 0) {
            return "NO_CODE:" + body;
        }
        String tail = body.substring(i);
        int c = tail.indexOf(':') + 1;
        int e = c;
        while (e < tail.length() && Character.isDigit(tail.charAt(e))) {
            e++;
        }
        return tail.substring(c, e);
    }

    /**
     * 熔断打开时 envelope 的 {@code msg}。
     *
     * <p><b>为什么单独抽一个方法</b>：code 与 msg 是<b>两条独立</b>的可观察面。
     * 只断言 code=1015 无法区分「msg 来自码表」与「msg 是异常文本
     * {@code circuit breaker is open}」—— 后者意味着某个域的异常处理器
     * 直接把 {@code getMessage()} 回传了，业务文案就与 Go 不一致
     * （Go 侧 msg 恒来自 {@code errmsg.go} 的码表）。
     */
    private static String msgOf(MvcResult r) throws Exception {
        String body = r.getResponse().getContentAsString();
        int i = body.indexOf("\"msg\"");
        if (i < 0) {
            return "NO_MSG:" + body;
        }
        String tail = body.substring(i);
        int c = tail.indexOf(':') + 1;
        while (c < tail.length() && Character.isWhitespace(tail.charAt(c))) {
            c++;
        }
        int e = tail.indexOf('"', c + 1);
        return e < 0 ? "NO_MSG:" + body : tail.substring(c + 1, e);
    }

    @Test
    @DisplayName("端到端：下游恒返 INTERNAL ⇒ 先 5001，跨桶累积后**变成 1015**（熔断真的生效）")
    void internalErrorsEventuallyBecomeCircuitOpen() throws Exception {
        // 钉死随机源，使 dropRatio>0 时必然命中拒绝（否则是概率性的、无法断言）
        interceptor.setRandomSource(() -> 0.0D);

        // ── 第一阶段：熔断还没打开 ⇒ 应看到 5001（INTERNAL → ERROR_SERVER_COMMON）
        String first = codeOf(callUnread());
        assertEquals("5001", first,
                "首次调用应落到下游并收到 INTERNAL ⇒ bizCode 5001（映射表 INTERNAL → ERROR_SERVER_COMMON）");

        // ── 第二阶段：跨桶累积失败，熔断应打开 ⇒ 响应码变为 1015
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        boolean sawCircuitOpen = false;
        for (int round = 0; round < 4 && !sawCircuitOpen; round++) {
            for (int i = 0; i < 40; i++) {
                String c = codeOf(callUnread());
                seen.add(c);
                if ("1015".equals(c)) {
                    sawCircuitOpen = true;
                    break;
                }
            }
            // ⚠️ 必须跨过 250ms 桶边界：breaker 的 history() 跳过当前半桶（Go 同语义）
            Thread.sleep(260L);
        }

        assertTrue(sawCircuitOpen,
                "下游连续返回 INTERNAL 后，熔断必须打开并让响应码从 5001 变成 1015；"
                        + "若恒为 5001，说明拦截器没落在真实调用链上，或"
                        + "GoStyleCircuitOpenException 被兜底分支吃成了 5001；"
                        + "本次实际观察到的业务码集合=" + seen);

        // ── 第三阶段：熔断打开后不再触达下游
        int trackedBefore = interceptor.trackedMethods();
        MvcResult afterOpen = null;
        for (int i = 0; i < 3; i++) {
            afterOpen = callUnread();
            assertEquals("1015", codeOf(afterOpen), "熔断打开后每次都应是 1015");
        }
        assertEquals(trackedBefore, interceptor.trackedMethods(),
                "熔断打开后不应再为新方法建 breaker（此处 1 个方法恒为 1）");

        // ── 第四阶段：1015 的 msg 必须来自码表，不是异常文本
        //
        // 这条断言原先只存在于 service/common 的纯单测里（静态调用 bizCodeFromError + getMsg）。
        // 保护包下沉到 common 后，那条断言无法留在 common：
        // code→msg 的码表是 **message 域**的（message-common），
        // 而 common 是所有业务模块的**下游**，反向依赖会把依赖树倒过来。
        // ⇒ 改在真运行时（读真实 envelope）断言，证据强度高于原来的静态映射断言：
        // 它同时证明了「异常被谁接住」「接到哪个码」「文案从哪来」三件事。
        assertEquals("service busy", msgOf(afterOpen),
                "1015 的 msg 必须来自 message 域码表（errmsg.go 的 CodeServerBusy），"
                        + "而不是 GoStyleCircuitOpenException 的异常文本 —— "
                        + "若异常处理器直接回传 getMessage()，这里会得到 \"circuit breaker is open\"");
    }

    /**
     * 真 in-process gRPC server：<b>所有方法</b>都抛 INTERNAL。
     *
     * <p>用 {@code BindableService}（而非 {@code ServiceImpl}）是为了让方法描述符
     * 与生成的 proto 完全一致 —— 熔断的隔离单位是 {@code getFullMethodName()}，
     * 服务端的方法名必须与客户端请求的一致。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class FaultyRpcServer {

        @Bean(destroyMethod = "shutdownNow")
        Server faultyGrpcServer() throws IOException {
            grpcServer = InProcessServerBuilder.forName("faulty-message-rpc")
                    .directExecutor()
                    .addService(new MessageServiceGrpc.MessageServiceImplBase() {
                        @Override
                        public void getUnreadSummary(
                                com.rag.message.message.proto.UnreadSummaryReq request,
                                io.grpc.stub.StreamObserver<com.rag.message.message.proto.UnreadSummaryResp> observer) {
                            observer.onError(new StatusRuntimeException(Status.INTERNAL));
                        }
                    })
                    .build()
                    .start();
            return grpcServer;
        }
    }
}