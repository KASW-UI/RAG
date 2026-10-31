package com.rag.message.message.api;

import com.rag.message.message.api.client.MessageRpcClient;
import com.rag.message.message.api.support.RealTomcatPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>message-api 的路径归一「真 Tomcat」守门</b>（§5.3e 项⑤ · A3 轴）。
 *
 * <h2>Go 出处（真源 = go-zero v1.10.2 · 注册与匹配<b>两侧</b>都做）</h2>
 * <pre>
 *   rest/router/patrouter.go:48   cleanPath := path.Clean(reqPath)     // 注册时（写进路由树）
 *   rest/router/patrouter.go:60   reqPath  := path.Clean(r.URL.Path)   // 匹配时（清洗请求路径）
 * </pre>
 * ⇒ ⇒ <b>路径变体命中同一条路由</b>。
 *
 * <h3>🟢 Go 侧活体实测（L4 · 真 go-zero {@code rest} server + curl）</h3>
 * <pre>
 *   /v1/x        =&gt; 200
 *   /v1/x/       =&gt; 200   ← 尾斜杠
 *   /v1//x       =&gt; 200   ← 重复斜杠
 *   /v1/x/../x   =&gt; 200   ← ..
 *   /v1/./x      =&gt; 200   ← .
 * </pre>
 *
 * <h2>🔴 为什么这条必须是「真 Tomcat」</h2>
 * <p>MockMvc <b>不经过</b>真实 Tomcat ⇒ 测不到「Tomcat 自己会不会先把这个路径规范化掉」
 * ⇒ ⇒ <b>分不清「是共享层那层在承重」还是「Tomcat 顺手就干了」</b>
 * ⇒ ⇒ 本类用<b>真容器 + {@link HttpURLConnection}</b>（与同域
 * {@code MessageApiTimeoutRealTomcatTest} 同款）。
 *
 * <h2>⚠️ 为什么<b>404 与非 404</b>能当「路由匹配上了」的判据</h2>
 * <p>本域的 {@code MessageJwtAuthFilter} 对<b>未注册路径不验签</b>
 * （其类注释：Go 的链只挂在路由匹配上 ⇒ 未注册的路径根本进不了链 ⇒ 最终仍是 404）
 * ⇒ ⇒ <b>已注册路径 + 无 token ⇒ 401</b>（鉴权在路由匹配之后）
 * ⇒ ⇒ <b>404 = 压根没匹配到路由</b>、<b>401 = 匹配上了但被鉴权拦下</b>
 * ⇒ ⇒ 判据 = 「变体与规范路径<b>同状态</b> ＋ <b>≠ 404</b>」。
 *
 * <h2>本类的三条守门</h2>
 * <ol>
 *   <li><b>前提非空洞</b>：共享层那份 {@link com.rag.common.http.GoStylePathNormalizingFilter} <b>必须在容器里</b>
 *       <b>且没被域内同名 Bean 遮蔽</b>；</li>
 *   <li><b>规范路径 ⇒ 命中路由</b>（阳性对照）；</li>
 *   <li><b>4 种路径变体 ⇒ 与规范路径同状态、且非 404</b>。</li>
 * </ol>
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
class MessageApiPathNormalizationRealTomcatTest {

    /**
     * <b>为什么不用 {@code RANDOM_PORT}</b>：见 {@link RealTomcatPort} 的类注释
     * （OS 临时端口段 49152–65535 正是 Docker 随机发布端口所在段 ⇒ 请求可能打到别的会话的容器上）。
     */
    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext context;

    @MockitoBean
    private MessageRpcClient messageRpcClient;

    /** 本域的一条已注册路由（Go 侧同款：{@code POST /message/v1/conversations/list}）。 */
    private static final String PATH = "/message/v1/conversations/list";
    private static final String BODY = "{}";

    /** 共享层那份归一器的 <b>Bean 名</b>（{@code GoStylePathNormalizingAutoConfiguration} 的 {@code @Bean} 方法名）。 */
    private static final String FILTER_BEAN_NAME = "goStylePathNormalizingFilter";

    /** 路径变体（Go 侧 {@code path.Clean} 的四种改写形态）。 */
    private static final String[] VARIANTS = {
            PATH + "/",                            // 尾斜杠
            "/message//v1/conversations/list",     // 重复斜杠
            "/message/./v1/conversations/list",     // "." 段
            "/message/v1/../v1/conversations/list", // ".." 段
    };

    // ---------------------------------------------------------------- 守门

    @Test
    @DisplayName("前提：共享层那份路径归一 Filter 必须在容器里，且**没被域内同名 Bean 遮蔽**")
    void sharedLayerNormalizingFilterIsPresent() {
        assertTrue(context.containsBean(FILTER_BEAN_NAME),
                "共享层那份路径归一 Filter 必须在容器里（Bean 名 = " + FILTER_BEAN_NAME + "）"
                        + "⇒ 若为 false，说明下面两条守门测的是「压根没有归一层」，不是「归一层对了」");

        Object bean = context.getBean(FILTER_BEAN_NAME);
        assertTrue(bean instanceof com.rag.common.http.GoStylePathNormalizingFilter,
                "按名注册的必须是**共享层那份**（com.rag.common.http.GoStylePathNormalizingFilter）"
                        + "⇒ 共享层的自动配置带 @ConditionalOnMissingBean(name = \"" + FILTER_BEAN_NAME + "\")"
                        + " ⇒ **域内一旦有同名 Bean，共享层那份根本不会注册**（= 遮蔽，不是叠加）"
                        + " ⇒ ⇒ 本域若哪天自己写了一份，共享层那层会静默失效 ⇒ 这条必须拦住。"
                        + "实际注册的类型 = " + bean.getClass().getName());

        Order order = AnnotationUtils.findAnnotation(com.rag.common.http.GoStylePathNormalizingFilter.class, Order.class);
        assertNotNull(order, "com.rag.common.http.GoStylePathNormalizingFilter 必须带 @Order（否则排不到读路径的 Filter 之前）");
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, order.value(),
                "归一层的 @Order 必须是 HIGHEST_PRECEDENCE+2（= 早于 GoStyleTraceparentFilter 的 +3）");
    }

    @Test
    @DisplayName("阳性对照：规范路径 ⇒ 命中路由（404 = 没匹配上；401 = 匹配上了但被鉴权拦下）")
    void canonicalPathReachesTheHandler() throws Exception {
        HttpResult resp = post(PATH);

        assertNotEquals(404, resp.status(),
                "规范路径必须命中本域已注册路由 ⇒ 若 404，说明探针打错了地方（后面那条就全是空断言）。"
                        + "实际 body=" + resp.body());
    }

    @Test
    @DisplayName("4 种路径变体 ⇒ 与规范路径同状态、且**不是 404**（Go 侧 patrouter.go:60 ⇒ path.Clean ⇒ 5 种变体全 200）")
    void pathVariantsReachTheSameRoute() throws Exception {
        HttpResult canonical = post(PATH);
        assertNotEquals(404, canonical.status(),
                "规范路径必须先命中（阳性对照），实际 status=" + canonical.status() + " body=" + canonical.body());

        for (String variant : VARIANTS) {
            HttpResult resp = post(variant);
            System.out.println("[G2 message-api A 组实测] " + variant + " ⇒ " + resp.status());

            assertEquals(canonical.status(), resp.status(),
                    "路径变体「" + variant + "」必须与规范路径**同状态**"
                            + "（Go 侧 go-zero 的 patrouter.go:60 会先 path.Clean 再匹配 ⇒ 变体命中同一条路由）"
                            + "⇒ 若 404 ⇒ 说明本域请求路径没被归一 ⇒ 与 Go 分叉。实际 body=" + resp.body());
            assertNotEquals(404, resp.status(),
                    "路径变体「" + variant + "」绝不能是 404（404 = 压根没匹配到路由 ⇒ 归一层没起作用）");
        }
    }

    // ---------------------------------------------------------------- 工具

    private HttpResult post(String path) throws Exception {
        // ⚠️ **必须用 HttpURLConnection 而不是 java.net.http**：
        //    `java.net.http` 的 `URI` 构造器**自己就规范化路径** ⇒ 变体在发出去之前就被抹平 ⇒ 探针失效。
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
