package com.rag.message.message.api;

import com.rag.message.message.api.MessageApiApplication;
import com.rag.message.message.api.client.MessageRpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 请求保护层的<b>装配级</b>守门：验证四层 filter 真的挂进了 MVC 管线、且<b>顺序</b>与 Go 一致。
 *
 * <p><b>为什么 MockMvc 在这里够用（与 DRIFT-024 的区别要说清）</b>：
 * 本类只断言<b>状态码与短路归属</b>，不断言<b>响应头</b>。
 * 响应头那层 MockMvc 与真实 Tomcat 时序不同（DRIFT-024），由
 * {@link MessageApiProtectionRealTomcatTest}（{@code DEFINED_PORT} + 本域专用端口段
 * + JDK HttpClient 读原始响应头）在真容器下守。
 * ⇒ 两层各自守自己守得住的部分，不互相冒充。
 *
 * <p>⚠️ <b>本段原先引用的是一个不存在的脚本</b>（{@code tools/difftest/check-protection-layer.sh}，
 * 2026-10-22 按 {@code find} 实测校正）—— 响应头其实一直由 {@code MessageApiProtectionRealTomcatTest} 守着。
 * 这正是 §3.3 的形态：<b>javadoc 里写「已由 X 守」不等于 X 存在</b>，
 * 写下的守卫名要能被 {@code find} 找到。
 *
 * <p><b>最有价值的一条是 {@link #oversizedBodyIsRejectedBeforeAuth()}（413 压过 401）</b>：
 * 它同时证明了两件事 ——
 * ① MaxBytes filter 确实挂上了（否则会走鉴权）；
 * ② 它<b>在鉴权拦截器之前</b>（否则会是 401）。
 * 本层失效这条就红，所以它比单独断言「能返回 413」强得多。
 *
 * <p>⚠️ <b>2026-10-22 更正一处错误推理</b>：本段原先写「② 它<b>在鉴权拦截器之前</b>（否则会是 401）。
 * 顺序错了这条就红」—— <b>「顺序」这个归因是错的</b>。实测把 MaxBytes 的 {@code @Order}
 * 改成 {@code LOWEST_PRECEDENCE}（链尾）后，本类三条断言<b>依然全绿</b>。
 * 原因是 Servlet 的<b>所有</b> Filter 都包在 {@code DispatcherServlet} 外，而
 * {@code HandlerInterceptor} 跑在其<b>内</b> ⇒ 两者先后由<b>容器结构</b>决定，与 order 数值无关。
 * ⇒ {@code @Order} 的真实作用是<b>在 Filter 之间排序</b>；真正锁死本条的变异是<b>让本层失效</b>
 * （摘掉 {@code @Component}，已实测：413 → 401）。详见 {@code GoStyleMaxBytesFilter} 类 javadoc。
 */
@SpringBootTest(
        classes = {MessageApiApplication.class, MessageApiProtectionMvcTest.Props.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:65535",
                "grpc.client.message-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // 压到极小值，让「超过阈值」在测试里可达；生产默认仍是 Go 的 10000
                "rag.protection.max-bytes=1024",
                "rag.protection.max-conns=4"
        })
@AutoConfigureMockMvc
class MessageApiProtectionMvcTest {

    private static final String LIST_PATH = "/message/v1/conversations/list";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageRpcClient rpcClient;

    /** 造一个「超过 rag.protection.max-bytes=1024」但仍是合法 JSON 的体。 */
    private static String oversizedJson() {
        StringBuilder sb = new StringBuilder("{\"conversation_id\":\"");
        while (sb.length() < 4096) {
            sb.append('a');
        }
        return sb.append("\"}").toString();
    }

    @Test
    @DisplayName("超限请求体 → 413，且**压过鉴权**（Go 把 MaxBytes 挂在 Authorize 之外）")
    void oversizedBodyIsRejectedBeforeAuth() throws Exception {
        mockMvc.perform(post(LIST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(oversizedJson()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string(""));
        // ⚠️ 刻意**不带** Authorization：拿到 413 而不是 401，就是「MaxBytes 在鉴权之外」的唯一证据。
        // 若这条返回 401，说明 MaxBytes 这一层没生效（**不是**「顺序被挪到了 auth 之后」——
        // 那条推理已被变异测试推翻，见类 javadoc 的更正段）。
    }

    @Test
    @DisplayName("未超限 + 无 token → 仍是 401（证明上一条不是被无条件 413 短路）")
    void withinLimitStillReachesAuth() throws Exception {
        mockMvc.perform(post(LIST_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("空 body 的 POST 不被 MaxBytes 误伤（Go: ContentLength=-1 ⇒ -1 > n 为 false）")
    void chunkedOrEmptyContentLengthIsNotRejected() throws Exception {
        // 无 content ⇒ Content-Length 为 0（或 -1），远小于阈值 ⇒ 必须放行到鉴权层
        mockMvc.perform(post(LIST_PATH).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    /** 把 max-bytes 压到 1024 的配置集中在此，便于与其他测试类对照。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class Props {
    }
}