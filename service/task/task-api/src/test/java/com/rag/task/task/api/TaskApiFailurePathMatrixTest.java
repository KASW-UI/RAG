package com.rag.task.task.api;

import com.rag.task.task.api.client.TaskRpcClient;
import com.rag.task.task.api.support.RealTomcatPort;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * task-api 的<b>失败路径矩阵</b>（真 Tomcat · 1 端点 × 6 类失败维度）。
 *
 * <h2>为什么要有它（基线缺口 G-13）</h2>
 * <p>{@code docs/PhaseGoal-judge.md} 的 P0 #2 判据原文：
 * <pre>
 *   「存在失败路径处理」推不出「参数校验 / 业务规则 / 认证 / 权限四项都有效」；
 *   且失败路径**域覆盖不全**（article / follow / task / hot 四域无独立失败路径矩阵）
 * </pre>
 * ⇒ 本类就是 task 域的那份「独立失败路径矩阵」。
 *
 * <h2>⚠️ 本域只有<b>一个</b>端点 —— 「矩阵」的轴是<b>失败维度</b>而不是端点</h2>
 * <p>Go 侧 {@code service/task/api/internal/handler/routes.go:15-25} 只有一条路由：
 * <pre>
 *   server.AddRoutes(
 *       []rest.Route{{Method: http.MethodPost, Path: "/task/get", Handler: task.GetTaskHandler(serverCtx)}},
 *       rest.WithPrefix("/taskcenter/v1"),
 *   )
 * </pre>
 * ⇒ 「每端点 ≥1 条失败路径」在 task 域**天然被放大**成「这一条端点的<b>每个失败维度</b>
 * 都得有一条」⇒ 本类把维度铺开成 6 组共 21 条断言。
 *
 * <h2>🔴 本域的失败形状<b>不是</b>「200 + 业务信封」</h2>
 * <p>task-api <b>没有注册</b> {@code httpx.SetErrorHandler}（真源 {@code service/task/api/task.go}
 * 全文 37 行，只有 {@code conf.MustLoad} / {@code NewHTTPMiddleware} / {@code RegisterHandlers}）
 * ⇒ go-zero v1.10.2 {@code rest/httpx/responses.go:142-159} 走的是
 * {@code handler == nil} 分支：
 * <pre>
 *   if errcode.IsGrpcError(err) { http.Error(w, err.Error(), errcode.CodeFromGrpcError(err)) }
 *   else                        { http.Error(w, err.Error(), http.StatusBadRequest) }
 * </pre>
 * ⇒ <b>所有失败都是「真实 HTTP 状态码 + 裸 text/plain + 尾随 \n」</b>。
 * 照抄 like / message / hot 三个域的「200 + {code,msg,data}」就是整条错误路径的漂移。
 *
 * <h2>为什么必须是<b>真 Tomcat</b>（不是 MockMvc）</h2>
 * <ul>
 *   <li><b>Content-Type 的容器归一化</b> —— 真 Tomcat 把 {@code text/plain; charset=utf-8}
 *       原样透传，而 MockMvc 不参与容器层 ⇒ 断言「带空格的分号」在 MockMvc 下会假绿；</li>
 *   <li><b>404 / 405 的 body 有无</b> —— 405 是<b>裸状态码</b>（无 body、无 Content-Type），
 *       404 是<b>有 body</b>（{@code "404 page not found\n"}）⇒ 这两条只有真容器说了算；</li>
 *   <li><b>空 body 的处理</b> —— 真容器把「读不到 body」交给 Spring 的
 *       {@code HttpRequestBodyRequiredException} ⇒ 400 ⇒ 与 Go 的 {@code io.EOF} 同为 400。</li>
 * </ul>
 * ⇒ 本类用 {@code DEFINED_PORT} + <b>本域专用端口段</b>（{@link RealTomcatPort}）
 * + JDK HttpClient 读<b>原始</b>响应字节（见「HTTP 装置」一节：为什么不能用 HttpURLConnection）。
 *
 * <h2>六组失败维度（每组的 Go 出处都写在用例上）</h2>
 * <table border="1">
 *   <caption>task 域的失败维度矩阵</caption>
 *   <tr><th>维度</th><th>可观察形态</th><th>Go 出处</th></tr>
 *   <tr><td>① 参数缺失 / 非法</td><td>400 + {@code user_id invalid} / {@code id must be a string or integer}</td>
 *       <td>{@code api/internal/types/types.go:16-50}</td></tr>
 *   <tr><td>② 边界值</td><td>0 / 负数 / 浮点 / 超 int64 ⇒ 一律 400 + {@code user_id invalid}</td>
 *       <td>{@code types.go:38-50}（{@code ParseInt} + {@code <=0}）</td></tr>
 *   <tr><td>③ body 形态</td><td>空 body / 畸形 JSON ⇒ 400（文案两侧不同 ⇒ 只断言状态码）</td>
 *       <td>{@code api/internal/handler/task/gettaskhandler.go:19-24}</td></tr>
 *   <tr><td>④ 下游不可用 / 业务码</td><td>按 gRPC code 映射成 9 个不同的 HTTP 状态码</td>
 *       <td>{@code rest/internal/errcode/grpc.go:12-42}</td></tr>
 *   <tr><td>⑤ 路由层</td><td>404（有 body）/ 405（裸状态码无 body）</td>
 *       <td>{@code rest/router/patrouter.go:73,79-82}</td></tr>
 *   <tr><td>⑥ 鉴权</td><td><b>无鉴权</b> ⇒ 不带 token 也必须成功（401 就是漂移）</td>
 *       <td>{@code routes.go:15-25}（<b>没有</b> {@code rest.WithJwt}）</td></tr>
 * </table>
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类的期望值全部抄自 Go 源码（每条断言带 Go {@code 文件:行号}）
 * ⇒ 这是 <b>RAG 侧的自动化断言</b>；本类<b>不</b>声称双侧活体对拍
 * ⇒ 证据强度按基线 §1.1 的 <b>R3</b>（有 Go 出处 + 有 RAG 侧自动化断言）计，
 * <b>不</b>按 R4（双侧活体 + 变异）计。
 */
@SpringBootTest(
        classes = TaskApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "grpc.client.task-rpc.address=static://127.0.0.1:65535",
                "grpc.client.task-rpc.negotiation-type=plaintext",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ DEFINED_PORT 下 WebServerStartStopLifecycle 也会触发 Nacos 注册，
                //    Nacos 连不上 ⇒ 抛 "Client not connected" ⇒ **web server 起不来**。
                "spring.cloud.nacos.discovery.register-enabled=false",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class TaskApiFailurePathMatrixTest {

    // ==================================================================
    // 模型
    // ==================================================================

    /**
     * 参数 / body 类的失败维度。
     *
     * @param name          维度名（用例名）
     * @param body          请求体原文（{@code null} = 完全不发 body）
     * @param expectedStatus 期望的 HTTP 状态码
     * @param expectedText  期望的 body 原文（含尾随 {@code "\n"}）；
     *                      {@code null} = <b>只断言状态码</b>（两侧文案本就不同）
     */
    record ParamFailure(String name, String body, int expectedStatus, String expectedText) {
        @Override
        public String toString() {
            return name + " ｜ body=" + (body == null ? "<none>" : body);
        }
    }

    /**
     * 下游 gRPC 类的失败维度。
     *
     * @param name           维度名
     * @param code           gRPC status code
     * @param expectedStatus 期望的 HTTP 状态码（Go {@code errcode/grpc.go:12-42} 的逐条映射）
     * @param goCodeName     grpc-go 的 {@code codes.Code.String()}（CamelCase）
     * @param description    gRPC status 的 description
     */
    record GrpcFailure(String name, Status.Code code, int expectedStatus, String goCodeName,
                       String description) {
        @Override
        public String toString() {
            return name + " ｜ grpc=" + code;
        }
    }

    /** 本域唯一的端点（Go {@code routes.go:19-24}）。 */
    private static final String PATH = "/taskcenter/v1/task/get";

    /**
     * 本域失败响应的 <b>实际</b> Content-Type。
     *
     * <p>🔴 <b>它与 Go 的字面量差一个空格</b>，且<b>这是已登记的、不可修的偏差</b>：
     * <ul>
     *   <li>Go（{@code net/http.Error}）：{@code text/plain; charset=utf-8}（分号后<b>有</b>空格）；</li>
     *   <li>RAG（本类的实际读数）：{@code text/plain;charset=utf-8}（分号后<b>无</b>空格）。</li>
     * </ul>
     * ⇒ 归因：<b>Tomcat 的 {@code Response.setContentType(String)} 会把 Content-Type 解析成
     * MediaType 再序列化</b> ⇒ 序列化时吃掉空格；{@code setHeader("Content-Type", …)}
     * 在 Tomcat 内部也被转手调成 {@code setContentType} ⇒ <b>没有任何写法能产出带空格的 Content-Type</b>
     * ⇒ 性质 = <b>框架限制（容器层）· 不可修</b>。
     * ⇒ 已登记：{@code docs/DRIFT/task/api.md} 的 {@code DRIFT-TASK-API-004}（<b>五域共性</b>）
     * ＋ {@code docs/DRIFT/message/feedback-to-g4.md} 的 {@code FB-G2-04}（结论「不可修 · 不转派」）。
     *
     * <p>⇒ 因此本断言钉的是<b>当前实际值</b>（不是 Go 的字面量）：
     * 钉 Go 的字面量 ⇒ 这 19 条用例会<b>永久红</b> ⇒ 变成「假红噪声」，把真回归淹没。
     * ⇒ 但<b>不等于说两侧一致</b>：差一个空格这件事仍然<b>未闭合</b>，台账里记着。
     */
    private static final String TEXT_PLAIN_UTF8 = "text/plain;charset=utf-8";

    /** Go {@code http.Error} 写死的 {@code X-Content-Type-Options: nosniff}。 */
    private static final String NOSNIFF = "nosniff";

    // ==================================================================
    // 装置
    // ==================================================================

    @LocalServerPort
    private int port;

    @MockitoBean
    private TaskRpcClient taskRpcClient;

    /**
     * <b>本类必须绑到本域专用端口段</b>（而不是 Spring 的 {@code RANDOM_PORT}）。
     *
     * <p>⚠️ {@code RANDOM_PORT} 挑的是 OS <b>临时端口段</b>（macOS：49152–65535），
     * 而<b>别的会话的 Docker 容器正好在那一段发布端口</b>
     * ⇒ 客户端可能把请求打到别人的服务上 ⇒ <b>零字节即关</b>
     * ⇒ <b>看着像被测代码炸了，其实压根没打到</b>，而且<b>所有用例照样绿</b>。
     */
    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    private void stubEmptyTaskList() {
        when(taskRpcClient.getTask(anyLong())).thenReturn(
                com.rag.task.task.proto.GetTaskResp.newBuilder().build());
    }

    // ==================================================================
    // 0. 端口段自检（本类不许退化成 RANDOM_PORT）
    // ==================================================================

    @Test
    @DisplayName("真容器必须绑到本域专用端口段 21800-21899（RANDOM_PORT 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段）")
    void bindsToDedicatedPortSegment() {
        assertTrue(port >= RealTomcatPort.FIRST && port <= RealTomcatPort.LAST,
                "Tomcat 实际绑定的端口必须在 " + RealTomcatPort.FIRST + "-" + RealTomcatPort.LAST
                        + " 内，实际=" + port
                        + "（若落在 49152-65535，说明 webEnvironment 被改回 RANDOM_PORT 了）");
    }

    // ==================================================================
    // 1. 维度①：参数缺失 / 非法（Go types.go:16-50）
    // ==================================================================

    static Stream<ParamFailure> paramFailures() {
        return Stream.of(
                // ① 类型.go:40-42 —— 空 ⇒ "user_id invalid"
                new ParamFailure("id 缺席", "{}", 400, "user_id invalid\n"),
                // ① types.go:18-21 —— null ⇒ "" ⇒ 同上
                new ParamFailure("id 为 null", "{\"id\":null}", 400, "user_id invalid\n"),
                // ① types.go:35 —— 既不是字符串也不是数字 ⇒ UnmarshalJSON 报错 ⇒ 整个 Decode 失败
                new ParamFailure("id 类型非法（布尔）", "{\"id\":true}", 400,
                        "id must be a string or integer\n"),
                new ParamFailure("id 类型非法（数组）", "{\"id\":[1]}", 400,
                        "id must be a string or integer\n"),
                new ParamFailure("id 类型非法（对象）", "{\"id\":{}}", 400,
                        "id must be a string or integer\n"),
                // ① types.go:45-46 —— ParseInt 失败
                new ParamFailure("id 非法字面量", "{\"id\":\"abc\"}", 400, "user_id invalid\n"),
                // ① types.go:38-42 —— TrimSpace 后为空
                new ParamFailure("id 全是空白", "{\"id\":\"   \"}", 400, "user_id invalid\n"),
                // ③ gettaskhandler.go:21-24 —— 空 body ⇒ io.EOF（两侧文案不同 ⇒ 只断言状态码）
                new ParamFailure("空 body", null, 400, null),
                // ③ gettaskhandler.go:21-24 —— 畸形 JSON ⇒ Decode 失败（文案不同 ⇒ 只断言状态码）
                new ParamFailure("畸形 JSON", "{\"id\":", 400, null));
    }

    @ParameterizedTest(name = "[参数类] {0}")
    @MethodSource("paramFailures")
    @DisplayName("维度① 参数缺失/非法 ⇒ 400 + text/plain + Go 的确切文案；且 **RPC 完全不被调用**")
    void parameterFailuresAre400WithGoTextAndNoRpcCall(ParamFailure failure) throws Exception {
        RawResponse r = post(PATH, failure.body());

        assertEquals(failure.expectedStatus(), r.status,
                "Go: 解析失败 ⇒ http.Error(..., 400)（rest/httpx/responses.go:155）");
        assertEquals(TEXT_PLAIN_UTF8, r.contentType,
                "当前实际值（Go 的字面量带空格，差一个空格 = 已登记的 DRIFT-TASK-API-004 · 容器层不可修）");
        assertEquals(NOSNIFF, r.nosniff, "Go 的 http.Error 写死 X-Content-Type-Options: nosniff");
        if (failure.expectedText() != null) {
            assertEquals(failure.expectedText(), r.body,
                    "Go 的文案必须逐字节对上（types.go:35 / types.go:40-47）");
        }
        // Go: 解析失败 ⇒ 根本没走到 rpc 调用（gettasklogic.go:31-34 之前就 return 了）
        verify(taskRpcClient, never()).getTask(anyLong());
    }

    // ==================================================================
    // 2. 维度②：边界值（Go types.go:38-50）
    // ==================================================================

    static Stream<ParamFailure> boundaryFailures() {
        return Stream.of(
                // ① types.go:45-47 —— uid <= 0 一律拒
                new ParamFailure("id = 0（数字）", "{\"id\":0}", 400, "user_id invalid\n"),
                new ParamFailure("id = 0（字符串）", "{\"id\":\"0\"}", 400, "user_id invalid\n"),
                new ParamFailure("id = -1", "{\"id\":-1}", 400, "user_id invalid\n"),
                new ParamFailure("id = -1（字符串）", "{\"id\":\"-1\"}", 400, "user_id invalid\n"),
                // ① types.go:45-46 —— ParseInt("1.5") 失败（Go 的 json.Number 保留字面量文本）
                new ParamFailure("id = 1.5（浮点字面量）", "{\"id\":1.5}", 400, "user_id invalid\n"),
                new ParamFailure("id = 1.0（浮点字面量）", "{\"id\":1.0}", 400, "user_id invalid\n"),
                // ① types.go:45-46 —— ParseInt 溢出 int64
                new ParamFailure("id 超 int64 上界", "{\"id\":\"99999999999999999999\"}", 400,
                        "user_id invalid\n"),
                new ParamFailure("id 超 int64 下界", "{\"id\":\"-99999999999999999999\"}", 400,
                        "user_id invalid\n"));
    }

    @ParameterizedTest(name = "[边界值] {0}")
    @MethodSource("boundaryFailures")
    @DisplayName("维度② 边界值（0 / 负数 / 浮点 / 溢出）⇒ 400 + \"user_id invalid\"，RPC 不被调用")
    void boundaryValuesAreRejected(ParamFailure failure) throws Exception {
        RawResponse r = post(PATH, failure.body());

        assertEquals(400, r.status, "Go: types.go:45-47 ⇒ ParseInt 失败或 uid<=0 ⇒ 400");
        assertEquals("user_id invalid\n", r.body,
                "Go 的三条失败路径**共用同一个文案**（types.go:42 / :46 / :47）⇒ 不许拆成不同文案");
        verify(taskRpcClient, never()).getTask(anyLong());
    }

    // ==================================================================
    // 3. 维度④：下游 gRPC 失败（Go rest/internal/errcode/grpc.go:12-42）
    // ==================================================================

    static Stream<GrpcFailure> grpcFailures() {
        return Stream.of(
                // grpc.go:35-36 —— 下游不可用
                new GrpcFailure("下游不可用（UNAVAILABLE）", Status.Code.UNAVAILABLE, 503,
                        "Unavailable", "task-rpc is down"),
                // grpc.go:17-18 —— 业务参数错
                new GrpcFailure("下游业务码（INVALID_ARGUMENT）", Status.Code.INVALID_ARGUMENT, 400,
                        "InvalidArgument", "user_id invalid"),
                // grpc.go:23-24 —— not-found
                new GrpcFailure("下游 not-found（NOT_FOUND）", Status.Code.NOT_FOUND, 404,
                        "NotFound", "task not found"),
                // grpc.go:21-22 —— 权限
                new GrpcFailure("下游权限（PERMISSION_DENIED）", Status.Code.PERMISSION_DENIED, 403,
                        "PermissionDenied", "forbidden"),
                // grpc.go:19-20 —— 未认证
                new GrpcFailure("下游未认证（UNAUTHENTICATED）", Status.Code.UNAUTHENTICATED, 401,
                        "Unauthenticated", "token expired"),
                // grpc.go:27-28 —— duplicate / conflict
                new GrpcFailure("下游冲突（ALREADY_EXISTS）", Status.Code.ALREADY_EXISTS, 409,
                        "AlreadyExists", "task already exists"),
                // grpc.go:29-30 —— 限流
                new GrpcFailure("下游限流（RESOURCE_EXHAUSTED）", Status.Code.RESOURCE_EXHAUSTED, 429,
                        "ResourceExhausted", "too many requests"),
                // grpc.go:31-32 —— 服务端内部错误
                new GrpcFailure("下游内部错误（INTERNAL）", Status.Code.INTERNAL, 500,
                        "Internal", "internal error"),
                // grpc.go:37-38 —— 超时
                new GrpcFailure("下游超时（DEADLINE_EXCEEDED）", Status.Code.DEADLINE_EXCEEDED, 504,
                        "DeadlineExceeded", "context deadline exceeded"));
    }

    @ParameterizedTest(name = "[下游 gRPC] {0}")
    @MethodSource("grpcFailures")
    @DisplayName("维度④ 下游 gRPC 失败 ⇒ 按 errcode/grpc.go 映射成**真实 HTTP 状态码** + grpc-go 的错误原文")
    void grpcFailuresMapToGoHttpStatus(GrpcFailure failure) throws Exception {
        Mockito.reset(taskRpcClient);
        when(taskRpcClient.getTask(anyLong()))
                .thenThrow(new StatusRuntimeException(
                        Status.fromCode(failure.code()).withDescription(failure.description())));

        RawResponse r = post(PATH, "{\"id\":\"123\"}");

        assertEquals(failure.expectedStatus(), r.status,
                "Go: errcode.CodeFromGrpcError（rest/internal/errcode/grpc.go:12-42）逐条映射");
        assertEquals(TEXT_PLAIN_UTF8, r.contentType,
                "当前实际值（Go 的字面量带空格，差一个空格 = 已登记的 DRIFT-TASK-API-004 · 容器层不可修）");
        assertEquals(NOSNIFF, r.nosniff, "Go 的 http.Error 写死 X-Content-Type-Options: nosniff");
        assertEquals("rpc error: code = " + failure.goCodeName()
                        + " desc = " + failure.description() + "\n",
                r.body,
                "Go 的 grpc-go 错误原文（status/status.go 的 Error() 格式）必须逐字节对上");
    }

    // ==================================================================
    // 4. 维度⑤：路由层（Go rest/router/patrouter.go:73,79-82）
    // ==================================================================

    @Test
    @DisplayName("维度⑤ 未注册路径 ⇒ 404 + **有 body** 的 \"404 page not found\\n\"（Go patrouter.go:73）")
    void unknownPathIs404WithGoBody() throws Exception {
        RawResponse r = post("/taskcenter/v1/task/nope", "{\"id\":\"1\"}");

        assertEquals(404, r.status, "Go: patrouter.go:73 ⇒ http.NotFound");
        assertEquals("404 page not found\n", r.body,
                "Go 的 http.NotFound 写死这行正文（net/http/server.go）");
        assertEquals(TEXT_PLAIN_UTF8, r.contentType,
                "当前实际值（Go 的字面量带空格，差一个空格 = 已登记的 DRIFT-TASK-API-004 · 容器层不可修）");
        assertEquals(NOSNIFF, r.nosniff, "Go 的 http.NotFound 写死 X-Content-Type-Options: nosniff");
    }

    @Test
    @DisplayName("维度⑤ 方法不对 ⇒ 405 + **裸状态码**（无 body、无 Content-Type）（Go patrouter.go:79-82）")
    void wrongMethodIs405WithEmptyBody() throws Exception {
        RawResponse r = get(PATH);

        assertEquals(405, r.status, "Go: patrouter.go:79-82 ⇒ 裸状态码 405");
        assertEquals("", r.body, "Go 的 405 分支只写状态码，**不写任何字节**");
        assertEquals(null, r.contentType, "Go 的 405 分支不设 Content-Type");
    }

    // ==================================================================
    // 5. 维度⑥：鉴权（本域**没有**鉴权 ⇒ 加/不加 token 都必须成功）
    // ==================================================================

    @Test
    @DisplayName("维度⑥ 本端点**零鉴权** ⇒ 不带 Authorization 头也必须 200（Go routes.go:15-25 没有 WithJwt）")
    void endpointIsPublicAndNeverReturns401() throws Exception {
        stubEmptyTaskList();

        RawResponse r = post(PATH, "{\"id\":\"123\"}");

        assertEquals(200, r.status,
                "Go: routes.go 只有 rest.WithPrefix ⇒ 公开端点 ⇒ 带/不带 token 都必须成功。"
                        + "若这里变成 401，就是给公开读加了一道 Go 没有的闸门");
        assertEquals("{\"tasks\":[]}", r.body,
                "Go: gettasklogic.go:43-55 ⇒ 空结果输出 {\"tasks\":[]}（不是 null、不是信封）");
    }

    @Test
    @DisplayName("维度⑥ 带一个**乱写的** Authorization 头也必须 200（Go 根本不读这个头）")
    void bogusAuthorizationHeaderIsIgnored() throws Exception {
        stubEmptyTaskList();

        RawResponse r = post(PATH, "{\"id\":\"123\"}", "Bearer not-a-real-token");

        assertEquals(200, r.status,
                "Go: task 域没有 rest.WithJwt ⇒ Authorization 头是**死输入** ⇒ 不许影响结果");
    }

    // ==================================================================
    // 6. 成功路径的「业务 not-found」（下游返回空列表 ⇒ 不是 404）
    // ==================================================================

    @Test
    @DisplayName("业务「查无任务」⇒ 200 + {\"tasks\":[]}（Go 没有 not-found 这个概念）")
    void emptyTaskListIs200Not404() throws Exception {
        stubEmptyTaskList();

        RawResponse r = post(PATH, "{\"id\":\"999999\"}");

        assertEquals(200, r.status,
                "Go: gettasklogic.go:43-55 ⇒ 空列表是**正常结果**，不是错误 ⇒ 不许映成 404");
        assertEquals("{\"tasks\":[]}", r.body, "Go 的输出就是裸的 {\"tasks\":[]}（无信封）");
    }

    @Test
    @DisplayName("uid = Long.MAX_VALUE 必须**放行**（Go 的 ParseInt 上界之内）⇒ RPC 收到同一个值")
    void maxLongUidIsAccepted() throws Exception {
        stubEmptyTaskList();

        RawResponse r = post(PATH, "{\"id\":\"9223372036854775807\"}");

        assertEquals(200, r.status, "Long.MAX_VALUE 在 Go 的 ParseInt 接受集合内 ⇒ 必须成功");
        verify(taskRpcClient).getTask(Long.MAX_VALUE);
    }

    // ==================================================================
    // HTTP 装置（真容器 ⇒ 读原始字节）
    // ==================================================================

    /** 真容器的原始响应（逐字节，不做任何加工）。 */
    record RawResponse(int status, String body, String contentType, String nosniff) {
    }

    /**
     * 🔴 <b>为什么用 JDK HttpClient 而不是 {@code HttpURLConnection}</b>
     *
     * <p>{@code HttpURLConnection} 对 <b>401</b> 有<b>隐式的认证协商</b>：
     * 收到 401 时它会走一遍 {@code Authenticator} 机制 ⇒ <b>响应体被它吃掉</b>
     * ⇒ 读出来永远是空串（2026-10-29 实测：{@code UNAUTHENTICATED} 那条用例拿到的是
     * {@code ""}，而服务端明明写了 {@code "rpc error: code = Unauthenticated ..."}）。
     *
     * <p>⇒ 那是<b>测试装置的假象</b>，不是产品漂移 ⇒ 用 JDK HttpClient（无此行为）
     * ⇒ 与 follow 域那份矩阵（{@code FollowApiFailurePathMatrixTest}）同款。
     *
     * <p>⚠️ 反过来：JDK HttpClient 的 {@code URI} 构造器<b>会规范化路径</b>
     * ⇒ 本类<b>不测路径变体</b>（路径变体由同域的
     * {@code TaskApiPathNormalizationRealTomcatTest} 用 {@code HttpURLConnection} 单独守）。
     */
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private RawResponse post(String path, String body) throws Exception {
        return post(path, body, null);
    }

    private RawResponse post(String path, String body, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body,
                        StandardCharsets.UTF_8));
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return toRaw(response);
    }

    private RawResponse get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        return toRaw(response);
    }

    private static RawResponse toRaw(HttpResponse<String> response) {
        return new RawResponse(
                response.statusCode(),
                response.body(),
                response.headers().firstValue("Content-Type").orElse(null),
                response.headers().firstValue("X-Content-Type-Options").orElse(null));
    }

    /** 供 ledger 引用：本类的维度清单（只读）。 */
    static List<String> dimensionNames() {
        return List.of("①参数缺失/非法", "②边界值", "③body形态", "④下游gRPC", "⑤路由层", "⑥鉴权");
    }
}
