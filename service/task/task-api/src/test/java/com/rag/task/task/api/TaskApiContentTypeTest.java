package com.rag.task.task.api;

import com.rag.task.task.api.client.TaskRpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <b>DRIFT-TASK-API-003 的守门测试</b>：{@code @RequestBody} 必须<b>无视 Content-Type</b>按 JSON 读原始 body。
 *
 * <h2>为什么必须用真 Tomcat（RANDOM_PORT）而不是 MockMvc</h2>
 * 本条漂移的根因在<b>容器层</b>与 <b>Spring 的 message-input 层</b>：
 * <ul>
 *   <li>Spring 的 {@code ServletServerHttpRequest#getBody()} 对 form POST 会走
 *       「从表单参数重建 body」的分支（spring-web 7.0.9 {@code :227-234}）；</li>
 *   <li>而 body 在<b>转换器被调用之前</b>就被换掉了 ——
 *       {@code EmptyBodyCheckingHttpInputMessage} 的<b>构造器</b>就调了一次 {@code getBody()}
 *       （spring-webmvc 7.0.9 {@code AbstractMessageConverterMethodArgumentResolver:355-380}）。</li>
 * </ul>
 * ⇒ MockMvc 不经过真实 Tomcat 的表单解析，<b>测不到</b>这条漂移。
 *
 * <h2>本测试守的三件事</h2>
 * <ol>
 *   <li>六种 Content-Type（含 form-urlencoded / multipart）都返回 <b>200</b>；</li>
 *   <li>不带 Content-Type 也返回 <b>200</b>（Go 侧对 Content-Type 零判据）；</li>
 *   <li><b>阳性对照</b>：畸形 JSON 仍返回 <b>400</b> ——
 *       证明「返回 200」不是因为断言写松了/解析被整体跳过。</li>
 * </ol>
 *
 * <h2>⚠️ 故意<b>不</b>在 {@code @SpringBootTest(properties=…)} 里覆盖 multipart 开关</h2>
 * multipart 的修法是<b>配置层</b>的 {@code spring.servlet.multipart.enabled=false}
 * （写在 {@code application.yml} 里）⇒ 测试必须依赖那份配置本身，
 * 否则测试自己再写一遍就<b>守不住配置被误删/写错属性名</b>这种回归
 * —— Boot 4 的属性名<b>没有 {@code mvc.}</b>，写错就是静默失效
 * （{@code @ConditionalOnBooleanProperty("spring.servlet.multipart.enabled", matchIfMissing=true)}，
 *  在 spring-boot-servlet-4.0.8.jar 里）。
 * ⇒ 另有一条断言直接读 {@link Environment} 确认该属性<b>确实被解析成 false</b>。
 */
@SpringBootTest(
        classes = TaskApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "grpc.client.task-rpc.address=static://127.0.0.1:65535",
                "grpc.client.task-rpc.negotiation-type=plaintext",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ RANDOM_PORT 下 WebServerStartStopLifecycle 会触发 Nacos 注册，
                //    而 Nacos 客户端连不上 ⇒ 抛 "Client not connected" ⇒ **web server 起不来**。
                //    （MOCK 环境下没有 web server，所以 MockMvc 的测试碰不到这一步。）
                "spring.cloud.nacos.discovery.register-enabled=false",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class TaskApiContentTypeTest {

    private static final String PATH = "/taskcenter/v1/task/get";

    @LocalServerPort
    int port;

    @Autowired
    Environment environment;

    @MockitoBean
    private TaskRpcClient taskRpcClient;

    // ---------------------------------------------------------------- 守门

    @ParameterizedTest(name = "Content-Type: {0} ⇒ 200")
    @ValueSource(strings = {
            "application/json",
            "text/plain",
            "application/xml",
            "application/x-www-form-urlencoded",
            "multipart/form-data",
            "application/octet-stream"
    })
    @DisplayName("DRIFT-TASK-API-003：JSON body 配任意 Content-Type 都应被收下（Go: gettaskhandler.go:19 裸 decoder）")
    void anyContentTypeIsAccepted(String contentType) throws Exception {
        stubTaskRpc();
        HttpResponse<String> resp = post(contentType, "{\"id\":1001}");
        assertEquals(200, resp.statusCode(),
                "Content-Type=" + contentType + " 应返回 200（Go 侧对 Content-Type 零判据），实际 body=" + resp.body());
        assertTaskPayload(resp.body());
    }

    @Test
    @DisplayName("DRIFT-TASK-API-003：不带 Content-Type 也应返回 200")
    void noContentTypeIsAccepted() throws Exception {
        stubTaskRpc();
        HttpResponse<String> resp = post(null, "{\"id\":1001}");
        assertEquals(200, resp.statusCode(), "不带 Content-Type 应返回 200，实际 body=" + resp.body());
        assertTaskPayload(resp.body());
    }

    // ---------------------------------------------------------------- 阳性对照

    @ParameterizedTest(name = "Content-Type: {0} + 畸形 JSON ⇒ 400")
    @ValueSource(strings = {
            "application/json",
            "application/x-www-form-urlencoded",
            "multipart/form-data"
    })
    @DisplayName("阳性对照：畸形 JSON 必须仍被拒（400）—— 证明上面那组不是「解析被整体跳过」")
    void malformedJsonIsStillRejected(String contentType) throws Exception {
        stubTaskRpc();
        HttpResponse<String> resp = post(contentType, "{not json");
        assertEquals(400, resp.statusCode(),
                "畸形 JSON 必须 400（Go: decoder.Decode 失败 ⇒ httpx.ErrorCtx），实际 body=" + resp.body());
    }

    @Test
    @DisplayName("阳性对照：空 body 必须仍被拒（400）")
    void emptyBodyIsStillRejected() throws Exception {
        stubTaskRpc();
        HttpResponse<String> resp = post("application/json", "");
        assertEquals(400, resp.statusCode(),
                "空 body 必须 400（Go: decoder.Decode 遇 EOF ⇒ httpx.ErrorCtx），实际 body=" + resp.body());
    }

    // ---------------------------------------------------------------- 配置项本身

    @Test
    @DisplayName("multipart 开关必须真的被解析成 false（Boot 4 属性名无 mvc.，写错即静默失效）")
    void multipartSwitchIsActuallyApplied() {
        String raw = environment.getProperty("spring.servlet.multipart.enabled");
        assertNotNull(raw, "spring.servlet.multipart.enabled 必须在 application.yml 里显式写 false");
        assertEquals("false", raw,
                "multipart 开关必须是 false（Go 侧没有 multipart 概念；开着会让 multipart resolver 抢消费 body）");
        // ⚠️ 顺带钉住「错属性名」这个坑：Boot 4 的 MultipartAutoConfiguration 认的是
        //    spring.servlet.multipart.enabled；写成 spring.mvc.servlet.multipart.enabled
        //    会**静默失效**（没人读那个属性）⇒ 这里显式断言错名字**不存在**。
        assertEquals(null, environment.getProperty("spring.mvc.servlet.multipart.enabled"),
                "不得再出现 spring.mvc.servlet.multipart.enabled（Boot 4 无此属性，写了就是静默失效）");
    }

    // ---------------------------------------------------------------- 辅助

    private void stubTaskRpc() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder()
                        .addTask(com.rag.task.task.proto.Task.newBuilder()
                                .setTaskId(190L).setName("t").setDesc("d")
                                .setCompletionProgress(1L).setRequiredProgress(5L))
                        .build());
    }

    private HttpResponse<String> post(String contentType, String body) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest.Builder builder = HttpRequest
                .newBuilder(URI.create("http://127.0.0.1:" + port + PATH))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 响应体必须是「一个 task 的 tasks 数组」—— 证明 body 真的被解析成了 DTO，而不是原样回显。 */
    private void assertTaskPayload(String body) {
        JsonNode root = JsonMapper.builder().build().readTree(body);
        JsonNode tasks = root.get("tasks");
        assertNotNull(tasks, "响应体应是 {\"tasks\":[…]}，实际=" + body);
        assertTrue(tasks.isArray() && tasks.size() == 1, "tasks 应是长度 1 的数组，实际=" + body);
        assertEquals(190, tasks.get(0).get("taskId").asInt(), "taskId 应为 stub 的 190，实际=" + body);
    }
}
