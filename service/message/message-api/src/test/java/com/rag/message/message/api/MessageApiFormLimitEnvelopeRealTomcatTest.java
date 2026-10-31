package com.rag.message.message.api;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>A3 轴的【真 Tomcat】探针 —— form 值超限的【业务信封】拒绝形状</b>。
 *
 * <h2>为什么需要这一条（补的是哪个缺口）</h2>
 * 本域 A3（HTTP 保护链）轴上：
 * <ul>
 *   <li>🟡 <b>路径归一</b> ⇒ ✅ <b>已由第十轮的真 Tomcat 探针闭合</b>
 *       （{@code MessageApiPathNormalizationRealTomcatTest} ＋ A/B 对照组
 *       {@code MessageApiPathNormalizationControlTest} ＋ 本域独立变异验证）；</li>
 *   <li>🟡 <b>form 闸门与鉴权的先后</b> ⇒ 已登记为「<b>有界残留 · 刻意不改</b>」；</li>
 *   <li>🔴 <b>「envelope 拒绝形状 / 2049 参数」缺【本域真实请求证据】</b>
 *       ⇒ ⇒ <b>就是本类要补的那一格</b>：此前只有 MockMvc 覆盖
 *       （{@code MessageApiMvcPipelineTest}），而 <b>MockMvc 测不到「Filter/Interceptor
 *       → @RestControllerAdvice → 真容器响应头」这一段</b> ⇒ ⇒ 缺一次【真 Tomcat】的活体。</li>
 * </ul>
 *
 * <h2>断言的标的：Go 出处（逐字）</h2>
 * <pre>
 * Go service/message/api/messagecenter.go:53
 *   httpx.SetErrorHandler(func(err error) (int, interface{}) {
 *       code := messagecommon.ErrorServerCommon   // 5001
 *       msg  := messagecommon.GetErrMsg(code)     // "internal server error"
 *       return http.StatusOK, &amp;response.Response{Code: code, Msg: msg, Data: nil}
 *   })
 * </pre>
 * ⇒ ⇒ 本域的契约是「<b>除鉴权失败外，HTTP 状态码恒为 200，成败全在 body 的 code 里</b>」。
 * ⇒ ⇒ 本类要钉住的就是：<b>form 值超限时，线上看到的是这个业务信封，而不是 Go 的裸 400</b>。
 *
 * <h2>🔴 为什么「MockMvc 绿」不足以说明问题（§3.3 的形态）</h2>
 * <ul>
 *   <li>共享层的 form 闸门在 <b>ENVELOPE 模式</b>下是<b>抛异常</b>（不是写响应）：
 *       {@code GoStyleFormValueLimitInterceptor:90-93} ⇒ 抛
 *       {@code GoStyleFormLimitExceededException} ⇒ 由<b>本域的兜底 handler</b>
 *       渲染成 200 + 信封；</li>
 *   <li>它必须是 <b>Interceptor</b> 而不是 Filter —— 因为 servlet Filter 抛出的异常
 *       <b>到不了</b> {@code @RestControllerAdvice}（Filter 在 DispatcherServlet 之外）
 *       ⇒ ⇒ 换成 Filter 就会退化成 500 ⇒ ⇒ <b>MockMvc 与真容器的行为差异必须由真容器来判</b>。</li>
 * </ul>
 *
 * <h2>为什么这条闸门【先于】鉴权（决定本探针能不能观察到信封）</h2>
 * 共享层 {@code GoStyleFormLimitWebMvcConfig} 用
 * {@code registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE)}
 * ⇒ ⇒ 它<b>先于</b>本域 {@code WebMvcConfig} 注册的鉴权拦截器（默认 order 0）
 * ⇒ ⇒ 超限时先撞闸门、拿到信封 ⇒ ⇒ <b>本探针不必带 token 就能观察到信封形状</b>。
 * ⚠️ 该「先后」本身就是 A3 轴上已登记的「有界残留 · 刻意不改」那一条
 * （Go 侧是 auth 先），本类<b>只记录 RAG 侧实测到的形状</b>，不改判据。
 *
 * <h2>为什么不用 {@code RANDOM_PORT}</h2>
 * 与 {@link MessageApiProtectionRealTomcatTest} 同款理由：{@code RANDOM_PORT} 挑的是
 * OS <b>临时端口段</b>，而别的会话的 Docker 容器正好在那一段发布端口 ⇒ 客户端可能把请求
 * 打到别人的服务 ⇒ <b>看着像被测代码炸了，其实压根没打到</b> ⇒ ⇒ 改用本域专用端口段
 * （见 {@link RealTomcatPort}）。
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
class MessageApiFormLimitEnvelopeRealTomcatTest {

    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private com.rag.common.http.GoStyleFormValueLimitInterceptor formLimitInterceptor;

    @MockitoBean
    private MessageRpcClient rpcClient;

    private static final String LIST_PATH = "/message/v1/conversations/list";

    /**
     * go-zero 的 {@code maxFormParamCount = 2048}（{@code rest/httpx/util.go:15}），
     * 数的是<b>非空值</b>个数 ⇒ ⇒ 取 2100，稳稳越过线。
     */
    private static final int OVER_LIMIT_PARAM_COUNT = 2100;

    /** Go 的裸 400 文案（{@code rest/httpx/util.go} 的 too many form values）—— <b>本域绝不该出现它</b>。 */
    private static final String GO_RAW_FORM_LIMIT_TEXT = "too many form values";

    @Test
    @DisplayName("真容器必须绑到本域专用端口段（RANDOM_PORT 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段）")
    void bindsToDedicatedPortSegment() {
        assertTrue(port >= RealTomcatPort.FIRST && port <= RealTomcatPort.LAST,
                "Tomcat 实际绑定的端口必须在 " + RealTomcatPort.FIRST + "-" + RealTomcatPort.LAST
                        + " 内，实际=" + port
                        + "（若落在 49152-65535，说明 webEnvironment 被改回 RANDOM_PORT 了）");
    }

    @Test
    @DisplayName("前提：本域的 form 值闸门必须是 ENVELOPE（Go: messagecenter.go:53 注册了 SetErrorHandler ⇒ 200 + 业务信封）")
    void formLimitModeMustBeEnvelope() {
        assertEquals(com.rag.common.http.GoStyleFormLimitMode.Mode.ENVELOPE, formLimitInterceptor.mode(),
                "本域 application.yml 必须是 mode: envelope —— Go 侧 messagecenter.go:53 注册了 "
                        + "httpx.SetErrorHandler ⇒ 错误压成 200 + {code:5001,…}；"
                        + "若被改成 raw/off，本类下面那条形状断言就不代表本域契约了。"
                        + "实际 mode=" + formLimitInterceptor.mode());
    }

    @Test
    @DisplayName("真 Tomcat：>2048 个非空 query 值 ⇒ 200 + 业务信封 {code:5001}，**不是** Go 的裸 400")
    void overLimitQueryValuesYieldBusinessEnvelope() throws Exception {
        HttpResponse<String> resp = post(overLimitQuery());

        assertEquals(200, resp.statusCode(),
                "Go 侧 messagecenter.go:53 的 SetErrorHandler 把错误压成 HTTP 200 + 业务信封；"
                        + "RAG 侧若返回 400 ⇒ 说明本域没走信封形状（要么 mode 不是 envelope，"
                        + "要么异常没到 @RestControllerAdvice）。实际 body=" + resp.body());
        assertTrue(resp.body() != null && resp.body().contains("\"code\":5001"),
                "超限时 body 必须是业务信封 {code:5001,…}（Go: messagecommon.ErrorServerCommon）；实际 body=" + resp.body());
        assertFalse(resp.body().contains(GO_RAW_FORM_LIMIT_TEXT),
                "🔴 本域绝不能把 Go 的裸 400 文案泄到 wire 上：Go 侧 SetErrorHandler 只取 code/msg，"
                        + "错误原文不进 wire。实际 body=" + resp.body());
    }

    @Test
    @DisplayName("真 Tomcat：未超限 + 无 token ⇒ 401（证明上一条不是「无条件短路」）")
    void withinLimitStillHitsAuth() throws Exception {
        HttpResponse<String> resp = post("p0=1&p1=1");

        assertEquals(401, resp.statusCode(),
                "未超限时闸门必须放行、请求继续走到鉴权层（无 token ⇒ 401）；"
                        + "若这里也返回 200+信封 ⇒ 说明闸门无条件短路，形状断言就失去判别力。"
                        + "实际 body=" + resp.body());
    }

    private static String overLimitQuery() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OVER_LIMIT_PARAM_COUNT; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append('p').append(i).append("=1");
        }
        return sb.toString();
    }

    private HttpResponse<String> post(String query) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)   // 与 Go 侧默认一致
                .build();
        HttpRequest req = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + LIST_PATH + "?" + query))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
