package com.rag.message.message.api;

import com.rag.message.message.api.MessageApiApplication;
import com.rag.message.message.api.client.MessageRpcClient;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 保护层的<b>真实 Tomcat</b>守门 —— MockMvc 测不到的那一层（响应头）。
 *
 * <p><b>为什么必须真容器</b>：AGENTS.md 的 DRIFT-024 已实证
 * {@code MockHttpServletResponse} 与真实 Tomcat 在「响应提交时序」上不同 ——
 * MockMvc 里 Content-Type 完好，真实栈 curl 出来是<b>空的</b>。
 * 本类断言的恰恰是响应头（裸 413 不应被容器补上 Content-Type），
 * 用 MockMvc 断言会<b>假绿</b>。
 *
 * <p><b>为什么用 JDK 的 {@link HttpClient} 而不是 {@code TestRestTemplate}</b>：
 * Spring Boot 4 把 {@code TestRestTemplate} 挪进了独立 starter
 * {@code org.springframework.boot.resttestclient}（本项目 pom 未引入）；
 * 而断言的标的正是<b>未经封装的原始响应头</b>，用 {@code RestTestClient} 的断言 DSL
 * 反而更难表达「这个头必须不存在」。JDK 客户端零新依赖、且能直接读
 * {@link HttpResponse#headers()}，是最贴合断言目标的选择。
 *
 * <p><b>Go 侧的期望值来自源码而非活体</b>：
 * {@code rest/handler/maxbyteshandler.go:21} 是裸 {@code w.WriteHeader(413)}，
 * 不调 {@code httpx.Error} ⇒ <b>无 body、无 Content-Type</b>。
 * ⚠️ <b>本环境无法启动 Go 真源</b>做活体双侧对拍：其 yaml 指向 {@code etcd:2379}，
 * 而 {@code zrpc.MustNewClient} 在 {@code internal/svc/servicecontext.go:21}
 * 无 etcd 时直接 panic。故期望值以 Go 源码为准，此处显式标注该证据边界。
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
                "logging.level.com.alibaba.nacos=WARN",
                "rag.protection.max-bytes=1024"
        })
class MessageApiProtectionRealTomcatTest {

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

    private static final String LIST_PATH = "/message/v1/conversations/list";

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

    private HttpResponse<String> post(String body) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)   // 与 Go 侧默认一致，且避免 h2c 升级干扰响应头
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + LIST_PATH))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static String oversizedJson() {
        StringBuilder sb = new StringBuilder("{\"conversation_id\":\"");
        while (sb.length() < 4096) {
            sb.append('a');
        }
        return sb.append("\"}").toString();
    }

    @Test
    @DisplayName("真 Tomcat：超限 → 413 + 空 body + 无 Content-Type（对齐 Go 裸 WriteHeader）")
    void oversizedBodyYieldsBare413() throws Exception {
        HttpResponse<String> resp = post(oversizedJson());

        assertEquals(413, resp.statusCode());
        assertTrue(resp.body() == null || resp.body().isEmpty(),
                "Go 侧 w.WriteHeader(413) 不写任何字节，body 必须为空；实际=[" + resp.body() + "]");
        assertNull(resp.headers().firstValue("content-type").orElse(null),
                "Go 侧 413 没有 Content-Type；容器若补出 text/html 就是漂移。实际="
                        + resp.headers().map());
    }

    @Test
    @DisplayName("真 Tomcat：413 压过鉴权（MaxBytes 在 auth 之外，与 Go chain 顺序一致）")
    void oversizedBodyBeatsAuth() throws Exception {
        // 刻意不带 Authorization：Go 侧 MaxBytes 挂在 engine.go:164，Authorize 由 engine.go:109
        // 在 buildChainWithNativeMiddlewares **之后** append ⇒ 超限时先撞 413 而非 401。
        HttpResponse<String> resp = post(oversizedJson());
        assertEquals(413, resp.statusCode(),
                "MaxBytes 必须先于 JWT 鉴权短路（Go rest/engine.go:105-112 + chain.Append 语义）");
    }

    @Test
    @DisplayName("真 Tomcat：未超限 + 无 token → 401（证明 413 不是无条件短路）")
    void withinLimitStillHitsAuth() throws Exception {
        HttpResponse<String> resp = post("{}");
        assertEquals(401, resp.statusCode());
    }
}