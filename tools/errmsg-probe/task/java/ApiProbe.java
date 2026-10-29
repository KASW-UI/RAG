package com.rag.task.task.probe;

import com.rag.task.task.api.advice.GlobalExceptionHandler;
import com.rag.task.task.api.client.TaskRpcClient;
import com.rag.task.task.api.controller.TaskController;
import com.rag.task.task.api.service.TaskGetService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * ⚠️ tools/errmsg-probe/task 的 Java 半边（**api 层**），**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code TaskController} ＋ <b>真实的</b> {@code TaskGetService}
 * ＋ <b>真实的</b> {@code GlobalExceptionHandler} ＋ <b>真实的</b> {@code GetTaskReq}
 * （Jackson 反序列化）走一遍 <b>真实 MVC 管线</b>，输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/task/go/api_probe.go}）逐字节相同：
 * <pre>
 * API|&lt;case&gt;|&lt;httpStatus&gt;|&lt;escaped body&gt;   一次真实 HTTP 请求的**状态码 + body 逐字节**
 * OBS|&lt;case&gt;|&lt;value&gt;                     **已知不可达差异**（两侧不同，单列）
 * </pre>
 *
 * <p>⚠️ 边界（诚实声明）：
 * <ul>
 *   <li>本探针用 {@code MockMvcBuilders.standaloneSetup}，<b>不是</b> Spring Boot 上下文
 *       ⇒ <b>不覆盖</b>「{@code TaskApiJsonBodyConfig} 的媒体类型认领」这一层
 *       （那是 Content-Type 层的契约，由 {@code TaskApiMvcPipelineTest} 与
 *       {@code docs/DRIFT/task/api.md} 负责）。本探针所有用例都发
 *       {@code Content-Type: application/json}。</li>
 *   <li>唯一被替换的是 gRPC 边界（{@code TaskRpcClient}）—— 被测语义全在 api 进程内。</li>
 *   <li>日志必须静音（{@code GlobalExceptionHandler} 会 {@code LOG.warn}），
 *       否则日志行混进 stdout ⇒ dump 被搅黄。</li>
 * </ul>
 */
public final class ApiProbe {

    /** Go 真源 {@code service/task/api/internal/handler/routes.go:15-25} 的唯一路由。 */
    private static final String PATH = "/taskcenter/v1/task/get";

    private ApiProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: ApiProbe <cases-file>");
            System.exit(2);
        }
        silenceLogging();

        FakeRpcClient rpcClient = new FakeRpcClient();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(new TaskGetService(rpcClient)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        List<Case> cases = readCases(Path.of(args[0]));

        StringBuilder b = new StringBuilder();

        for (Case c : cases) {
            rpcClient.error = c.rpcCode == null ? null
                    : Status.fromCodeValue(c.rpcCode).withDescription(c.rpcDesc).asRuntimeException();
            MvcResult result = mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(c.body))
                    .andReturn();
            b.append("API|").append(c.name).append('|')
                    .append(result.getResponse().getStatus()).append('|')
                    .append(escape(new String(result.getResponse().getContentAsByteArray(),
                            StandardCharsets.UTF_8)))
                    .append('\n');
        }

        // ── OBS：两侧必然不同的已知不可达差异 ─────────────────────────────────────
        for (Case c : obsCases()) {
            rpcClient.error = null;
            MvcResult result = mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(c.body))
                    .andReturn();
            b.append("OBS|").append(c.name).append('|')
                    .append(result.getResponse().getStatus()).append('|')
                    .append(escape(new String(result.getResponse().getContentAsByteArray(),
                            StandardCharsets.UTF_8)))
                    .append('\n');
        }

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * 把一段文本压成<b>单行</b>可逐字节比对的形态。
     *
     * <p>⚠️ 转义规则必须与 Go 半边（{@code api_probe.go} 的 {@code escape}）<b>逐条一致</b>：
     * <pre>
     * {@code \} → {@code \\}   {@code |} → {@code \|}   LF → {@code \n}   CR → {@code \r}
     * </pre>
     */
    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '|' -> b.append("\\|");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /** 一条 HTTP 用例。 */
    private record Case(String name, String body, Integer rpcCode, String rpcDesc) {
    }

    /**
     * {@code TaskRpcClient} 的**受控**替身。
     *
     * <p>⚠️ 真源的 api 层是「纯转发」：{@code gettasklogic.go:36-41} 把 rpc 的错误
     * <b>原样</b>往上抛 ⇒ 要测「保不保得住 code」就必须能<b>控制</b>下游返回什么。
     */
    private static final class FakeRpcClient extends TaskRpcClient {

        private RuntimeException error;

        @Override
        public com.rag.task.task.proto.GetTaskResp getTask(long userId) {
            if (error != null) {
                throw error;
            }
            return com.rag.task.task.proto.GetTaskResp.getDefaultInstance();
        }
    }

    /**
     * OBS 样本 —— 两侧<b>必然</b>不同的已知差异。
     *
     * <p>⚠️ 这两条都是<b>库/框架形态差异</b>，不是 RAG 的行为漂移：
     * <ul>
     *   <li>{@code obs_api_empty_body}：Go 的 {@code decoder.Decode} 对空 body 返回
     *       {@code io.EOF} ⇒ 400 + {@code "EOF\n"}；Spring 侧是
     *       {@code HttpRequestBodyRequiredException} ⇒ 400 +
     *       {@code "Required request body is missing\n"}。</li>
     *   <li>{@code obs_api_malformed_json}：Go {@code encoding/json} 的措辞 vs
     *       Jackson 的措辞（+ Spring 的 {@code "JSON parse error: "} 前缀）。</li>
     * </ul>
     */
    private static List<Case> obsCases() {
        List<Case> out = new ArrayList<>();
        out.add(new Case("obs_api_empty_body", "", null, null));
        out.add(new Case("obs_api_malformed_json", "{", null, null));
        return out;
    }

    /**
     * 把 SLF4J 根 logger 的级别压到 {@code OFF}（见 {@code RpcProbe#silenceLogging}）。
     */
    private static void silenceLogging() {
        try {
            Object root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            for (Method m : root.getClass().getMethods()) {
                if ("setLevel".equals(m.getName()) && m.getParameterCount() == 1) {
                    // ⚠️ logback 1.5.x 的 Level **不是 enum**（是带静态实例的 final class）
                    //    ⇒ 不能用 Enum.valueOf（会抛 IllegalArgumentException），
                    //    必须直接取那个静态字段。
                    Object off = m.getParameterTypes()[0].getField("OFF").get(null);
                    m.invoke(root, off);
                    return;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException | NoSuchFieldException
                | RuntimeException ignored) {
            // 压不下去就压不下去 —— 让 dump 被日志污染并让对拍变红，比假绿好。
        }
    }

    /**
     * 读 cases 文件：{@code name|body|rpcCode|rpcDesc} 一行一条。
     *
     * <p>⚠️ {@code rpcCode} 取 grpc 的<b>数字</b>码（与 Go 半边同一张表）；
     * {@code "-"} ⇒ 不注入下游错误（用例应在 uid 解析阶段就失败）。
     */
    private static List<Case> readCases(Path path) throws Exception {
        List<Case> out = new ArrayList<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\|", 4);
            if (parts.length != 4) {
                System.err.println("bad case line (want name|body|rpcCode|rpcDesc): " + line);
                System.exit(2);
            }
            Integer code = "-".equals(parts[2]) ? null : Integer.valueOf(parts[2]);
            out.add(new Case(parts[0], parts[1], code, parts[3]));
        }
        return out;
    }
}
