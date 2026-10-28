package com.rag.task.task.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.rag.common.http.GoStylePathNormalizingFilter;
import com.rag.task.task.api.client.TaskRpcClient;
import com.rag.task.task.api.support.RealTomcatPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>task-api 的路径归一「真 Tomcat」守门</b>（§5.3e 项⑤ · A3 轴收口的前置）。
 *
 * <h2>Go 出处（真源 = go-zero v1.10.2 · 注册与匹配<b>两侧</b>都做）</h2>
 * <pre>
 *   rest/router/patrouter.go:48   cleanPath := path.Clean(reqPath)     // 注册时（写进路由树）
 *   rest/router/patrouter.go:60   reqPath  := path.Clean(r.URL.Path)   // 匹配时（清洗请求路径）
 * </pre>
 * ⇒ 路由表里存的是<b>已 Clean 的模板</b>，匹配时也把请求路径 Clean 一次
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
 * ⇒ ⇒ <b>分不清「是共享层那层在承重」还是「Tomcat 顺手就干了」</b>。
 * <p>⇒ 本类用<b>真容器 + {@link HttpURLConnection}</b>（与同域
 * {@code TaskApiFormLimitReachabilityTest} 同款）⇒ 连「Tomcat 的 URI 处理」这条容器层路径一起覆盖。
 *
 * <h2>本类的三条守门</h2>
 * <ol>
 *   <li><b>前提非空洞</b>：共享层那份 {@link GoStylePathNormalizingFilter} <b>必须在容器里</b>
 *       （否则下面两条可能只是「压根没有这层」而不是「这层对了」）；</li>
 *   <li><b>规范路径 ⇒ 命中路由</b>（阳性对照：证明路由本身是通的）；</li>
 *   <li><b>4 种路径变体 ⇒ 与规范路径<b>同状态、同业务分支</b></b>
 *       ⇒ 这是本类的核心断言 ⇒ Go 侧 5 种变体全 200 ⇒ RAG 侧不许 404。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么用 {@link HttpURLConnection} 而不是 {@code java.net.http}</h2>
 * <p>{@code java.net.http} 的 {@code URI} 构造器<b>自己就规范化路径</b>
 * （{@code URI.create("…/a/../b")} ⇒ 变成 {@code …/b}）⇒ ⇒ 变体在<b>发出去之前</b>就被抹平了
 * ⇒ ⇒ 测到的会是「规范路径」⇒ <b>探针失效</b>。
 * <p>⇒ {@link URL} 不校验也不规范化路径 ⇒ 请求行里原样带上变体（与 Go 的 {@code net/http} 同形：
 * Go 侧 {@code r.URL.Path} 拿到的是<b>客户端发的原样路径</b>，由 go-zero 自己 Clean）。
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
class TaskApiPathNormalizationRealTomcatTest {

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

    /** 共享层那层归一器（前提断言用）。 */
    @Autowired
    org.springframework.context.ApplicationContext context;

    @MockitoBean
    private TaskRpcClient taskRpcClient;

    /** 本域唯一的路由（Go 侧同款：{@code routes.go:16-25} · {@code POST /task/get} + prefix {@code /taskcenter/v1}）。 */
    private static final String PATH = "/taskcenter/v1/task/get";
    private static final String BODY = "{\"id\":1001}";

    /** 共享层那份归一器的 <b>Bean 名</b>（{@code GoStylePathNormalizingAutoConfiguration} 的 {@code @Bean} 方法名）。 */
    private static final String FILTER_BEAN_NAME = "goStylePathNormalizingFilter";

    /** 路径变体（Go 侧 {@code path.Clean} 的四种改写形态）。 */
    private static final String[] VARIANTS = {
            PATH + "/",            // 尾斜杠
            "/taskcenter//v1/task/get",   // 重复斜杠
            "/taskcenter/./v1/task/get",   // "." 段
            "/taskcenter/v1/task/../task/get", // ".." 段
    };

    // ---------------------------------------------------------------- 守门

    @Test
    @DisplayName("前提：共享层那份路径归一 Filter 必须在容器里，且**没被域内同名 Bean 遮蔽**")
    void sharedLayerNormalizingFilterIsPresent() {
        // 🔴 **为什么按 Bean 名 + 类型两步断言，而不是直接 @Autowired 那个类**：
        //    直接注入具体类型 ⇒ 一旦共享层那份被遮蔽，**上下文启动期就炸**
        //    （`NoSuchBeanDefinitionException`）⇒ ⇒ 守门会「红在启动」而不是「红在断言」
        //    ⇒ ⇒ 那样**看不出是哪条判据挂了**，也无法区分「压根没这层」与「这层不对」。
        //    ⇒ 改成按名取 + 断言类型 ⇒ 遮蔽会**红在下面这条断言上**。
        assertTrue(context.containsBean(FILTER_BEAN_NAME),
                "共享层那份路径归一 Filter 必须在容器里（Bean 名 = " + FILTER_BEAN_NAME + "）"
                        + "⇒ 若为 false，说明下面两条守门测的是「压根没有归一层」，不是「归一层对了」");

        Object bean = context.getBean(FILTER_BEAN_NAME);
        assertTrue(bean instanceof GoStylePathNormalizingFilter,
                "按名注册的必须是**共享层那份**（com.rag.common.http.GoStylePathNormalizingFilter）"
                        + "⇒ 共享层的自动配置带 @ConditionalOnMissingBean(name = \"" + FILTER_BEAN_NAME + "\")"
                        + " ⇒ **域内一旦有同名 Bean，共享层那份根本不会注册**（= 遮蔽，不是叠加）"
                        + " ⇒ ⇒ 本域若哪天自己写了一份，共享层那层会静默失效 ⇒ 这条必须拦住。"
                        + "实际注册的类型 = " + bean.getClass().getName());

        // ⚠️ 顺序约束：必须在「所有会读路径的 Filter」之前
        //   （共享层 GoStyleTraceparentFilter = HIGHEST_PRECEDENCE + 3，它要读 getRequestURI() 取 span 名）
        //   ⇒ 本层取 HIGHEST_PRECEDENCE + 2 ⇒ 早于它。
        Order order = AnnotationUtils.findAnnotation(GoStylePathNormalizingFilter.class, Order.class);
        assertNotNull(order, "GoStylePathNormalizingFilter 必须带 @Order（否则排不到读路径的 Filter 之前）");
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 2, order.value(),
                "归一层的 @Order 必须是 HIGHEST_PRECEDENCE+2（= 早于 GoStyleTraceparentFilter 的 +3）"
                        + "⇒ 若变了 ⇒ 读路径的那层可能先看到未清洗的路径");
    }

    @Test
    @DisplayName("阳性对照：规范路径 ⇒ 命中路由且拿到业务载荷（证明路由本身是通的）")
    void canonicalPathReachesTheHandler() throws Exception {
        stubTaskRpc();
        HttpResult resp = post(PATH);

        assertEquals(200, resp.status(),
                "规范路径必须命中本域唯一路由（Go 侧 routes.go:16-25 ⇒ POST /taskcenter/v1/task/get）"
                        + "⇒ 若不是 200，说明探针打错了地方（后面几条就全是空断言）。实际 body=" + resp.body());
        assertTaskPayload(resp.body());
    }

    @Test
    @DisplayName("4 种路径变体 ⇒ 与规范路径同状态、同业务分支（Go 侧 patrouter.go:60 ⇒ path.Clean ⇒ 5 种变体全 200）")
    void pathVariantsReachTheSameRoute() throws Exception {
        stubTaskRpc();
        HttpResult canonical = post(PATH);
        assertEquals(200, canonical.status(), "规范路径必须先通（阳性对照），实际 body=" + canonical.body());

        for (String variant : VARIANTS) {
            HttpResult resp = post(variant);
            System.out.println("[G2 A 组实测] " + variant + " ⇒ " + resp.status());

            assertEquals(canonical.status(), resp.status(),
                    "路径变体「" + variant + "」必须与规范路径**同状态**"
                            + "（Go 侧 go-zero 的 patrouter.go:60 会先 path.Clean 再匹配 ⇒ 变体命中同一条路由）"
                            + "⇒ 若 404 ⇒ 说明本域请求路径没被归一 ⇒ 与 Go 分叉。实际 body=" + resp.body());
            assertNotEquals(404, resp.status(),
                    "路径变体「" + variant + "」绝不能是 404（404 = 压根没匹配到路由 ⇒ 归一层没起作用）");
            assertTaskPayload(resp.body());
        }
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

    private HttpResult post(String path) throws Exception {
        // ⚠️ **必须用 HttpURLConnection 而不是 java.net.http**：
        //    `java.net.http` 的 `URI` 构造器**自己就规范化路径**（`/a/../b` ⇒ `/b`）
        //    ⇒ 变体在发出去之前就被抹平 ⇒ 探针失效。
        //    `java.net.URL` 不校验也不规范化 ⇒ 请求行里原样带上变体。
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

    /** 响应体必须是「一个 task 的 tasks 数组」—— 证明 body 真的被解析并走到了业务分支。 */
    private void assertTaskPayload(String body) throws Exception {
        JsonNode root = JsonMapper.builder().build().readTree(body);
        JsonNode tasks = root.get("tasks");
        assertNotNull(tasks, "响应体应是 {\"tasks\":[…]}，实际=" + body);
        assertTrue(tasks.isArray() && tasks.size() == 1, "tasks 应是长度 1 的数组，实际=" + body);
        assertEquals(190, tasks.get(0).get("taskId").asInt(), "taskId 应为 stub 的 190，实际=" + body);
    }
}
