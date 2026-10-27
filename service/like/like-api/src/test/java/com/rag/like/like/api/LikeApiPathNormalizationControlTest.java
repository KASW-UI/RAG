package com.rag.like.like.api;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.support.RealTomcatPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 🔴 <b>like-api 路径归一的「对照组」</b>（§5.3e 项⑤ · A3 轴）—— <b>证明共享层那层是承重的</b>。
 *
 * <h2>为什么要有对照组</h2>
 * <p>本类与 {@link LikeApiPathNormalizationRealTomcatTest} 的<b>唯一差别</b>：
 * <b>把共享层那层归一器从容器里摘掉</b>
 * （{@code spring.autoconfigure.exclude=com.rag.common.http.GoStylePathNormalizingAutoConfiguration}）
 * ⇒ ⇒ <b>同一份域服务、同一通真实请求</b>，唯一变量 = 「那层机制在不在」
 * ⇒ ⇒ 只有这样才能区分两种可能：
 * <ul>
 *   <li>「<b>是共享层那层</b>在把路径变体归一」 ⇒ 本类里变体应当 <b>404</b>；</li>
 *   <li>「<b>是 Tomcat 顺手就干了</b>」 ⇒ 本类里变体仍应当非 404 ⇒ ⇒ 那么 A 组的绿<b>证明不了任何事</b>
 *       （这正是 AGENTS.md §3.3 的「静默失效」形态）。</li>
 * </ul>
 *
 * <h2>⚠️ 本类是<b>对照组</b>，不是「产品缺陷的证明」</h2>
 * <p>Go 侧 <b>永远</b>会归一（{@code patrouter.go:60}）⇒ ⇒ 「摘掉归一层 ⇒ 变体 404」
 * 证明的是<b>RAG 侧的归一层是必需的</b>，<b>不是</b>「RAG 有缺陷」。
 * <p>⇒ 本类<b>只在探针里</b>排除该自动配置，<b>不动任何生产配置</b>。
 */
@SpringBootTest(
        classes = LikeApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                "grpc.client.like-rpc.address=static://127.0.0.1:65535",
                "grpc.client.like-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 🔴 **本对照组的唯一变量**：把共享层的路径归一自动配置摘掉
                //    ⇒ 那层 Filter 压根不在容器里 ⇒ 等价于「合入共享层之前」的状态。
                "spring.autoconfigure.exclude=com.rag.common.http.GoStylePathNormalizingAutoConfiguration"
        })
class LikeApiPathNormalizationControlTest {

    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    @LocalServerPort
    int port;

    @MockitoBean
    private LikeRpcClient likeRpcClient;

    private static final String PATH = "/like/v1/getlikecount";
    private static final String BODY = "{\"targetId\":1001,\"targetType\":1}";

    private static final String[] VARIANTS = {
            PATH + "/",
            "/like//v1/getlikecount",
            "/like/./v1/getlikecount",
            "/like/v1/../v1/getlikecount",
    };

    // ---------------------------------------------------------------- 对照组

    @Test
    @DisplayName("对照：摘掉共享层归一层后，规范路径仍然命中路由（证明路由本身没问题，变的只有归一）")
    void canonicalPathStillReachesTheHandlerWithoutNormalization() throws Exception {
        HttpResult resp = post(PATH);

        assertNotEquals(404, resp.status(),
                "对照组里规范路径必须仍然命中路由（⇒ 说明下面那些 404 是「归一层缺席」造成的，"
                        + "不是「路由本身不通」）。实际 status=" + resp.status() + " body=" + resp.body());
    }

    @Test
    @DisplayName("对照：摘掉共享层归一层后，4 种路径变体**全部** 404（⇒ 证明「变体能命中路由」全是那层机制在承重）")
    void pathVariantsAreNotRoutedWithoutNormalization() throws Exception {
        StringBuilder matrix = new StringBuilder();
        for (String variant : VARIANTS) {
            int status = post(variant).status();
            matrix.append(variant).append(" ⇒ ").append(status).append("；");
            assertEquals(404, status,
                    "对照组（归一层缺席）里，路径变体「" + variant + "」必须是 404"
                            + "⇒ Tomcat / Spring 自己**不会**把它匹配到 `/like/v1/getlikecount`"
                            + "⇒ ⇒ A 组能命中**只能**是共享层那层归一器的功劳 ⇒ A 组的绿不是空断言。"
                            + "实测矩阵：" + matrix);
        }
        System.out.println("[G2 like-api 对照组实测矩阵] " + matrix);
    }

    // ---------------------------------------------------------------- 工具

    private HttpResult post(String path) throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        int status = conn.getResponseCode();
        InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
        String text = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return new HttpResult(status, text);
    }

    /** 状态码 + 响应体。 */
    private record HttpResult(int status, String body) {
    }
}
