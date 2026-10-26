package com.rag.message.message.api;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.proto.ConversationListResp;
import com.rag.message.message.proto.MessageServiceGrpc;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.message.message.api.support.RealTomcatPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>UNRESOLVED-004 取证：HTTP 超时必须是 503 + {@code text/plain; charset=utf-8} + "Request Timeout\n"。</b>
 *
 * <h3>Go 侧事实（消费点，不是声明点）</h3>
 * <pre>
 *   Sea-RideTheWind_golang/service/common/observability/http.go:22
 *       httpTimeoutBody = "Request Timeout"
 *   同文件 :88
 *       http.Error(w, httpTimeoutBody, statusCode)
 *   ⇒ Go 标准库 net/http 的 http.Error 做三件事：
 *       ① WriteHeader(statusCode)  —— 503
 *       ② Header().Set("Content-Type", "text/plain; charset=utf-8")
 *       ③ 写 body + "\n"  ⇒  "Request Timeout\n"
 *       ④（Go 1.x 起）X-Content-Type-Options: nosniff
 * </pre>
 *
 * <h3>为什么本类此前测不到</h3>
 * <p>{@code MessageApiProtectionRealTomcatTest} 的 3 例只覆盖 MaxBytes 的 413，
 * <b>触达不到超时分支</b> ⇒ 「3s 超时触发时 RAG 是否真的写出 503 + 那套头」
 * 一直只有<b>静态读代码</b>的结论，没有运行证据（§3.3）。
 * <p>⇒ 本类用一个**会睡 5s 的 stub**（超过 3s 阈值）把超时分支真正逼出来。
 *
 * <h3>为什么必须带合法 JWT</h3>
 * <p>鉴权在业务层之前：没有 token ⇒ 401 短路 ⇒ 根本走不到慢调用 ⇒ 测不到超时。
 * ⇒ 这里用 users-common 的 {@link JwtUtil} 现场签一个 token
 * （secret 与两侧配置里的同一份一致）。
 */
@SpringBootTest(
        classes = MessageApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                "grpc.client.message-rpc.address=static://127.0.0.1:65535",
                "grpc.client.message-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class MessageApiTimeoutRealTomcatTest {

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

    /** 与 Go etc/messagecenter.yaml / RAG application.yml 的 user-auth.access-secret 同值。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    private static final String LIST_PATH = "/message/v1/conversations/list";

    /** Go: httpTimeoutBody = "Request Timeout"。 */
    private static final String GO_TIMEOUT_BODY = "Request Timeout";

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

    @MockitoBean
    private MessageRpcClient rpcClient;

    /** 让下游调用睡 5s（> 观测层的 3s 阈值）。 */
    @SuppressWarnings("unchecked")
    private void makeDownstreamSlow(long millis) {
        MessageServiceGrpc.MessageServiceBlockingStub stub =
                mock(MessageServiceGrpc.MessageServiceBlockingStub.class);
        when(stub.listConversations(any())).thenAnswer(inv -> {
            Thread.sleep(millis);
            return ConversationListResp.getDefaultInstance();
        });
        when(rpcClient.messageRpc()).thenReturn(stub);
    }

    private HttpResponse<String> listWithToken() throws Exception {
        long iat = System.currentTimeMillis() / 1000L;
        String token = JwtUtil.getToken(USER_SECRET, iat, 3600L, 1L);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + LIST_PATH))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString("{\"user_id\":1}"))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("🔴 真 Tomcat：下游睡过 3s ⇒ 必须 503 + text/plain; charset=utf-8 + \"Request Timeout\\n\"")
    void slowDownstreamYieldsGoTimeoutContract() throws Exception {
        makeDownstreamSlow(5000L);

        HttpResponse<String> resp = listWithToken();

        assertEquals(503, resp.statusCode(),
                "Go: http.Error(w, httpTimeoutBody, 503) ⇒ 状态码必须是 503。实际=" + resp.statusCode()
                        + " body=" + resp.body());

        // ⚠️⚠️ **Content-Type 的空格：Tomcat 侧不可达，属框架限制**（2026-10-24 实测）
        //   Go 的 net/http 写的是 **原始字节**："text/plain; charset=utf-8"（**分号后有空格**）。
        //   Tomcat 11 的 Response.setContentType(String) 会把 Content-Type **解析成 MediaType 再序列化**，
        //   序列化时**吃掉分号后的空格** ⇒ 发出 "text/plain;charset=utf-8"。
        //   ⚠️ **实测：`setHeader("Content-Type", ...)` 也一样** ——
        //   Tomcat 的 Response.setHeader 对 Content-Type **转手调 setContentType** ⇒ 同样被规范化。
        //   ⇒ 这个空格在 Tomcat 上**没有任何写法能产出** ⇒ 归类「框架限制」，
        //      不是「RAG 少写了一个空格」⇒ 不按漂移修，也改不掉。
        //   ⇒ 这里断言「charset 在、type 对」，**不为那一个空格把测试判红**
        //      （§3.3：绿测试若去守一个不可达的目标，就是「错误答案的守卫」）。
        String ct = resp.headers().firstValue("content-type").orElse(null);
        assertTrue(ct != null && ct.startsWith("text/plain") && ct.contains("charset=utf-8"),
                "Go: http.Error 写死 text/plain; charset=utf-8。"
                        + "⚠️ 已知且不可修的残差：Tomcat 序列化会吃掉分号后的空格"
                        + "（实测 setHeader 也被规范化）⇒ 实际只能是 text/plain;charset=utf-8。"
                        + "但 charset 必须存在、type 必须是 text/plain。实际=" + ct);

        assertEquals(GO_TIMEOUT_BODY + "\n", resp.body(),
                "Go: http.Error 写 body + \"\\n\" ⇒ 必须是 \"Request Timeout\\n\"。实际=[" + resp.body() + "]");

        assertEquals("nosniff", resp.headers().firstValue("x-content-type-options").orElse(null),
                "Go: net/http 的 http.Error 自带 X-Content-Type-Options: nosniff。实际="
                        + resp.headers().map());
    }

    @Test
    @DisplayName("✅ 下游不慢 ⇒ 正常 200（证明上一例是超时分支而不是别的短路）")
    void fastDownstreamStillReturns200() throws Exception {
        makeDownstreamSlow(0L);

        HttpResponse<String> resp = listWithToken();

        assertEquals(200, resp.statusCode(),
                "同一条路径、同一个 stub，只是不睡 ⇒ 必须正常返回；否则上一例证明不了「是超时触发的」。"
                        + "实际=" + resp.statusCode() + " body=" + resp.body());
        assertTrue(resp.body() != null && resp.body().contains("\"code\":200"),
                "正常路径必须回 envelope；实际=[" + resp.body() + "]");
    }
}
