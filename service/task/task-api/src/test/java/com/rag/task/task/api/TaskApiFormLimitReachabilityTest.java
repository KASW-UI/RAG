package com.rag.task.task.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rag.task.task.api.client.TaskRpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.rag.task.task.api.support.RealTomcatPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>task-api 的 form 值上限闸门「语义可达性」守门</b>（2026-10-27 · 阶段 2 · D-1）。
 *
 * <h2>Go 出处（逐条，声明点 + 消费点）</h2>
 * <pre>
 *   service/task/api/internal/handler/task/gettaskhandler.go:19
 *       decoder := json.NewDecoder(r.Body)      ← **裸 JSON 解码器**
 *       decoder.UseNumber()
 *   service/task/api/internal/handler/routes.go:16-25
 *       唯一路由：POST /task/get（WithPrefix("/taskcenter/v1")）
 * </pre>
 * ⇒ 本域 Go 侧<b>从不</b>调 {@code httpx.Parse} / {@code httpx.ParseForm} / {@code httpx.GetFormValues}
 * （全仓 grep：task 域这三个符号命中数 = **0**）⇒ <b>query / form 从不被解析</b>
 * ⇒ go-zero 的 {@code maxFormParamCount = 2048}（{@code rest/httpx/util.go:15}）
 * <b>在本域根本不可达</b>
 * ⇒ <b>Go 侧：带 3000 个 query 参数的请求照常处理 ⇒ 200</b>。
 *
 * <h2>🔴 为什么这条必须是「真 Tomcat」</h2>
 * <p>MockMvc <b>不经过</b>真实 Tomcat 的查询串解析 ⇒ 测不到「Tomcat 自己会不会先拒」。
 * 本类用<b>真容器 + java.net.http</b>（与同域 {@code TaskApiContentTypeTest} 同款）⇒
 * 连「Tomcat 头大小 / 参数个数上限」这条容器层路径一起覆盖。
 *
 * <h2>三条守门 + 一条阳性对照</h2>
 * <ol>
 *   <li><b>&gt;2048 个非空 query 值</b> ⇒ 200（Go 侧该上限不可达 ⇒ 不许拒绝）</li>
 *   <li><b>{@code ?a=%zz}</b>（畸形转义）⇒ 200（Go 侧不解析 ⇒ 不受影响）</li>
 *   <li><b>{@code ?a=1;b=2}</b>（分号分隔）⇒ 200（同上）</li>
 *   <li><b>阳性对照</b>：正常请求 ⇒ 200 <b>且 body 是正常的 task 载荷</b>
 *       ⇒ 证明上面三条不是「断言写松了 / 整条链路被跳过」</li>
 * </ol>
 *
 * <h2>⚠️ 本守门<b>不</b>断言「Go 原文」这类形状</h2>
 * <p>闸门关掉后本域就<b>没有</b>「超限拒绝」这个出口了 ⇒ 没有形状可断言。
 * 本类只断言<b>状态码 = 200</b> ＋ <b>body 是正常业务载荷</b>。
 *
 * <h2>配套的域内改动</h2>
 * <p>{@code service/task/task-api/src/main/resources/application.yml} 的
 * {@code rag.http.go-form-limit.mode}：{@code raw} ⇒ {@code off}
 * （理由见该文件里的注释；「off 的两个前提」也逐条写在那里）。
 */
@SpringBootTest(
        classes = TaskApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "grpc.client.task-rpc.address=static://127.0.0.1:65535",
                "grpc.client.task-rpc.negotiation-type=plaintext",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ DEFINED_PORT 下 WebServerStartStopLifecycle 同样会触发 Nacos 注册，
                //    而 Nacos 客户端连不上 ⇒ 抛 "Client not connected" ⇒ **web server 起不来**。
                "spring.cloud.nacos.discovery.register-enabled=false",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class TaskApiFormLimitReachabilityTest {

    /**
     * <b>为什么不用 {@code RANDOM_PORT}</b>（2026-10-25 favorite 域实测踩出来的，
     * 见 {@link com.rag.task.task.api.support.RealTomcatPort} 的类注释）：
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

    private static final String PATH = "/taskcenter/v1/task/get";
    private static final String BODY = "{\"id\":1001}";

    /**
     * Go 的上限是 {@code maxFormParamCount = 2048}（数<b>非空值</b>）
     * ⇒ 这里取 2100，稳稳越过线。
     */
    private static final int OVER_LIMIT_PARAM_COUNT = 2100;

    @LocalServerPort
    int port;
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
    private TaskRpcClient taskRpcClient;

    @Autowired
    private com.rag.common.http.GoStyleFormValueLimitInterceptor formLimitInterceptor;

    // ---------------------------------------------------------------- 守门

    @Test
    @DisplayName("本域的 form 值上限闸门必须是 OFF（Go: gettaskhandler.go:19 裸 decoder ⇒ query/form 从不解析 ⇒ 上限不可达）")
    void formLimitGateMustBeOff() {
        // 🔴 这条断言是**本守门的非空洞前提**：光断言「请求返回 200」区分不了
        //    「闸门被关掉」与「闸门根本没装」—— 两者都表现为 200。
        //    ⇒ 必须**直接**断言生效中的模式。
        assertEquals(com.rag.common.http.GoStyleFormLimitMode.Mode.OFF, formLimitInterceptor.mode(),
                "本域的 rag.http.go-form-limit.mode 必须是 OFF：Go 侧 gettaskhandler.go:19 用裸 "
                        + "json.NewDecoder ⇒ 从不调 httpx.Parse/ParseForm ⇒ go-zero 的 2048 上限在本域不可达；"
                        + "RAG 侧若开闸门 ⇒ 多出一道 Go 没有的拒绝。实际生效模式 = " + formLimitInterceptor.mode());
    }

    @Test
    @DisplayName(">2048 个非空 query 值 ⇒ 必须 200（Go: gettaskhandler.go:19 裸 decoder ⇒ 从不解析 query ⇒ 上限不可达）")
    void overLimitNonEmptyQueryValuesAreAccepted() throws Exception {
        stubTaskRpc();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OVER_LIMIT_PARAM_COUNT; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("p").append(i).append("=1");
        }
        HttpResult resp = post(sb.toString(), BODY);

        assertEquals(200, resp.status(),
                "Go 侧本域从不解析 query ⇒ 2048 上限不可达 ⇒ Go 侧是 200；"
                        + "RAG 侧若拒绝 ⇒ 就是多出一道 Go 没有的拒绝（漂移）。"
                        + "本次 query 参数个数 = " + OVER_LIMIT_PARAM_COUNT + "，实际 body=" + resp.body());
        assertTaskPayload(resp.body());
    }

    @Test
    @DisplayName("?a=%zz（畸形转义）⇒ 必须 200（Go 侧不解析 query ⇒ 畸形 query 与本域无关）")
    void malformedPercentEncodingQueryIsAccepted() throws Exception {
        stubTaskRpc();
        HttpResult resp = post("a=%zz", BODY);

        assertEquals(200, resp.status(),
                "Go 侧 task handler 从不读 query ⇒ 畸形 query 不影响处理 ⇒ 200。"
                        + "RAG 侧若 400 ⇒ 说明有某一层在解析 query（与 Go 不同）。实际 body=" + resp.body());
        assertTaskPayload(resp.body());
    }

    @Test
    @DisplayName("?a=1;b=2（分号分隔）⇒ 必须 200（Go 侧不解析 query ⇒ 分号与本域无关）")
    void semicolonSeparatedQueryIsAccepted() throws Exception {
        stubTaskRpc();
        HttpResult resp = post("a=1;b=2", BODY);

        assertEquals(200, resp.status(),
                "Go 侧 task handler 从不读 query ⇒ 分号分隔与本域无关 ⇒ 200。"
                        + "RAG 侧若 400 ⇒ 说明有某一层在解析 query（与 Go 不同）。实际 body=" + resp.body());
        assertTaskPayload(resp.body());
    }

    // ---------------------------------------------------------------- 阳性对照

    @Test
    @DisplayName("阳性对照：无 query 的正常请求 ⇒ 200 且 body 是正常 task 载荷（证明上面三条不是「链路被整体跳过」）")
    void normalRequestStillWorks() throws Exception {
        stubTaskRpc();
        HttpResult resp = post(null, BODY);

        assertEquals(200, resp.status(), "正常请求必须 200，实际 body=" + resp.body());
        assertTaskPayload(resp.body());
    }

    // ---------------------------------------------------------------- 工具

    private void stubTaskRpc() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder()
                        .addTask(com.rag.task.task.proto.Task.newBuilder()
                                .setTaskId(190L).setName("t").setDesc("d")
                                .setCompletionProgress(1L).setRequiredProgress(5L))
                        .build());
    }

    private HttpResult post(String query, String body) throws Exception {
        // ⚠️ **必须用 HttpURLConnection 而不是 java.net.http**：
        //    `URI.create("…?a=%zz")` 在**发请求之前**就抛
        //    `IllegalArgumentException: Malformed escape pair` ⇒ 根本到不了服务器
        //    ⇒ 用它测「畸形转义的 query」测的是 URI 解析器，不是本域的行为。
        //    `java.net.URL` 不校验转义 ⇒ query 串**原样上线**（与 Go 的 net/http 同形：
        //    Go 侧 `net/url.ParseQuery` 会报错，但 **task handler 从不读 query** ⇒ 200）。
        URL url = new URL("http://127.0.0.1:" + port + PATH + (query == null ? "" : "?" + query));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(20000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
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

    /** 响应体必须是「一个 task 的 tasks 数组」—— 证明 body 真的被解析并走到了业务分支。 */
    private void assertTaskPayload(String body) throws Exception {
        JsonNode root = JsonMapper.builder().build().readTree(body);
        JsonNode tasks = root.get("tasks");
        assertNotNull(tasks, "响应体应是 {\"tasks\":[…]}，实际=" + body);
        assertTrue(tasks.isArray() && tasks.size() == 1, "tasks 应是长度 1 的数组，实际=" + body);
        assertEquals(190, tasks.get(0).get("taskId").asInt(), "taskId 应为 stub 的 190，实际=" + body);
    }
}
