package com.rag.users.admin.api;

import com.rag.users.admin.api.client.AdminRpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static com.rag.users.admin.api.support.GoStyleHostPortPinning.pinContainerPort;

/**
 * E-1 取证（2026-10-22）：定位「RAG 的 text/plain 400 丢了 Content-Type」的机制层成因。
 *
 * <p><b>现象（curl 实测，两端同请求，逐字对照）</b>：
 * <pre>
 *   Go : HTTP/1.1 400 Bad Request
 *        Content-Type: text/plain; charset=utf-8
 *        X-Content-Type-Options: nosniff
 *        Content-Length: 95
 *
 *   RAG: HTTP/1.1 400                  &lt;-- 连 reason phrase 都没有
 *        （无 Content-Type）
 *        （无 X-Content-Type-Options）
 *        Transfer-Encoding: chunked
 *        Connection: close
 * </pre>
 * 正文是<b>对齐</b>的（DRIFT-006 对齐的正是正文），所以只比 body 的 difftest
 * 连续 4 轮全绿 —— 缺陷藏在<b>响应头</b>这一层，从来没被比对过。
 *
 * <p><b>本类的目的</b>：把「advice 根本没设 Content-Type」与
 * 「设了但被 HttpMiddleware 的 wrapper 吞掉」这两种可能<b>用证据区分开</b>，
 * 而不是靠静态推理下结论。
 *
 * <p><b>为什么用真 MVC + 真 filter</b>（§3.3）：{@code standaloneSetup} 不挂 filter，
 * 测不到 wrapper；这是 {@code AdminApiMvcPipelineTest} 当初栽过的坑
 * （见 §3.4.2「N-1 的二次坍塌」——standaloneSetup 全绿而真实管线出 HTTP 200）。
 */
@SpringBootTest(
        classes = AdminApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.admin-rpc.address=static://127.0.0.1:65535",
                "grpc.client.admin-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
@AutoConfigureMockMvc
@Testcontainers
class AdminApiPlainText400HeaderProbeTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withCreateContainerCmdModifier(cmd -> pinContainerPort(cmd, 6379));

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminRpcClient adminRpcClient;

    @Test
    @DisplayName("取证：畸形 JSON → 400 text/plain 时 Content-Type 是否真的丢了")
    void probeMalformedJson() throws Exception {
        MvcResult r = mockMvc.perform(post("/admin/v1/create")
                        .contentType("application/json")
                        .header("Authorization", "Bearer probe")
                        .content("{bad json"))
                .andReturn();

        System.out.println("======== E-1 取证输出 ========");
        System.out.println("status                 = " + r.getResponse().getStatus());
        System.out.println("Content-Type           = " + r.getResponse().getContentType());
        System.out.println("X-Content-Type-Options = "
                + r.getResponse().getHeader("X-Content-Type-Options"));
        System.out.println("body                   = ["
                + r.getResponse().getContentAsString().replace("\n", "\\n") + "]");
        System.out.println("=============================");

        assertEquals(400, r.getResponse().getStatus());
    }

    /**
     * 对照组：走 200 的 JSON 路径，看 Content-Type 是否<b>正常</b>。
     *
     * <p>这一条是关键的鉴别器：
     * <ul>
     *   <li>若这里 Content-Type 正常、而上一条缺失 ⇒ 问题<b>只</b>出在 text/plain 分支
     *       （即 advice 的 header 或 wrapper 对 String body 的处理），不是全局性的；</li>
     *   <li>若这里也缺失 ⇒ wrapper 在<b>所有</b>响应上都吞 header，是更严重的普遍问题。</li>
     * </ul>
     */
    @Test
    @DisplayName("取证对照：200 JSON 响应的 Content-Type 是否正常（鉴别 wrapper 是否普遍吞 header）")
    void probeJsonOkPath() throws Exception {
        // login 路径（用户不存在 → 200 + envelope JSON）
        MvcResult r = mockMvc.perform(post("/admin/v1/login")
                        .contentType("application/json")
                        .content("{\"username\":\"probe-user\",\"password\":\"pw123456\"}"))
                .andReturn();

        System.out.println("======== E-1 取证输出（对照组）========");
        System.out.println("status       = " + r.getResponse().getStatus());
        System.out.println("Content-Type = " + r.getResponse().getContentType());
        System.out.println("body         = ["
                + r.getResponse().getContentAsString().replace("\n", "\\n") + "]");
        System.out.println("========================================");
    }
}