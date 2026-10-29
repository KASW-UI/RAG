package com.rag.task.task.probe;

import com.rag.task.task.proto.GetTaskReq;
import com.rag.task.task.proto.GetTaskResp;
import com.rag.task.task.rpc.mapper.TaskProgressMapper;
import com.rag.task.task.rpc.service.TaskDomainService;
import com.rag.task.task.rpc.service.impl.TaskServiceGrpcImpl;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ⚠️ tools/errmsg-probe/task 的 Java 半边（**rpc 层**），**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code com.rag.task.task.rpc.service.impl.TaskServiceGrpcImpl}
 * ＋ <b>真实的</b> {@code TaskDomainService}（task-rpc 模块的生产编译产物，
 * 不是自己 new 出来的一个副本）输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/task/go/rpc_probe.go}）逐字节相同：
 * <pre>
 * RPCUID|&lt;case&gt;|&lt;grpcCode&gt;|&lt;desc&gt;     uid &lt;= 0 ⇒ 领域层抛 status error
 * RPCBARE|&lt;case&gt;|&lt;grpcCode&gt;|&lt;desc&gt;    裸 error（DB 回源失败）⇒ 兜底翻成 UNKNOWN
 * OBS|&lt;case&gt;|&lt;value&gt;                    **已知不可达差异**（两侧不同，单列）
 * </pre>
 *
 * <p>⚠️ 三条必须注意的细节：
 * <ol>
 *   <li><b>不得抄写文案</b>：本探针只调真实产物里的方法，
 *       自己不持有任何码值/文案常量 —— 否则探针就变成了「拿 RAG 的值验 RAG 的值」，
 *       必然自洽、必然假绿（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。
 *       ⚠️ 例外：OBS 段的 {@code obs_wrapped_status} 的**外层文案**是探针自造的输入，
 *       两侧必须用**同一个**字面量才有可比性。</li>
 *   <li><b>必须显式用 {@code \n}</b>：{@code System.out.println} 用的是平台行分隔符，
 *       跨平台会变成 {@code \r\n} 导致字节不一致 ⇒ 这里走
 *       {@link PrintStream#write(byte[])} 直接吐 UTF-8 字节。</li>
 *   <li><b>日志必须静音</b>：{@code TaskServiceGrpcImpl} 的兜底分支会 {@code LOG.error}，
 *       {@code TaskDomainService} 会 {@code LOG.warn} —— Spring Boot 的默认 console appender
 *       写 <b>stdout</b> ⇒ 不静音就会混进 dump，把逐字节对拍搅黄。</li>
 * </ol>
 */
public final class RpcProbe {

    private RpcProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: RpcProbe <uid-cases-file> <bare-cases-file>");
            System.exit(2);
        }
        silenceLogging();

        List<Case> uidCases = readCases(Path.of(args[0]));
        List<Case> bareCases = readCases(Path.of(args[1]));

        StringBuilder b = new StringBuilder();

        // ── RPCUID：uid <= 0 ⇒ 领域层直接抛 status error ─────────────────────────
        // ⚠️ redis / mapper 都传 null：uid 校验在 TaskDomainService.getTask 的**最前面**，
        //    两个字段根本不会被碰到（与 Go 侧「uid 校验早于任何 Redis / DB 访问」对称）。
        for (Case c : uidCases) {
            TaskServiceGrpcImpl impl = newImpl(null);
            CapturingObserver obs = new CapturingObserver();
            impl.getTask(GetTaskReq.newBuilder().setUserId(Long.parseLong(c.value)).build(), obs);
            b.append("RPCUID|").append(c.name).append('|')
                    .append(obs.grpcCode()).append('|')
                    .append(escape(obs.grpcDescription())).append('\n');
        }

        // ── RPCBARE：DB 回源失败 ⇒ 领域层抛裸 RuntimeException ⇒ 兜底翻成 UNKNOWN ──
        for (Case c : bareCases) {
            TaskServiceGrpcImpl impl = newImpl(throwingMapper(c.value));
            CapturingObserver obs = new CapturingObserver();
            impl.getTask(GetTaskReq.newBuilder().setUserId(1L).build(), obs);
            b.append("RPCBARE|").append(c.name).append('|')
                    .append(obs.grpcCode()).append('|')
                    .append(escape(obs.grpcDescription())).append('\n');
        }

        // ── OBS：两侧必然不同的已知不可达差异 ─────────────────────────────────────
        // ⚠️ OBS 的 value 是**已经算好的** "code|desc"（obsCases() 里就地跑了一遍真实的
        //    TaskServiceGrpcImpl）⇒ 这里只负责落行，不能再当「要抛的异常文案」用一次。
        for (Case c : obsCases()) {
            b.append("OBS|").append(c.name).append('|').append(c.value).append('\n');
        }

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** 造一个「领域层抛裸 RuntimeException」的 mapper 替身。 */
    private static TaskProgressMapper throwingMapper(String message) {
        return (TaskProgressMapper) Proxy.newProxyInstance(
                TaskProgressMapper.class.getClassLoader(),
                new Class<?>[]{TaskProgressMapper.class},
                (proxy, method, args) -> {
                    if ("selectByUserId".equals(method.getName())) {
                        throw new RuntimeException(message);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 造一个「领域层抛包装过的 StatusRuntimeException」的 mapper 替身（OBS 用）。
     *
     * <p>⚠️ 外层文案必须与 Go 半边 {@code fmt.Errorf("outer: %w", ...)} 产生的**完全一致**，
     * 否则两侧的 OBS 值就没有可比性（差异会变成「文案不同」而不是「code 不同」）。
     */
    private static TaskProgressMapper wrappingMapper(String outerMessage, StatusRuntimeException cause) {
        return (TaskProgressMapper) Proxy.newProxyInstance(
                TaskProgressMapper.class.getClassLoader(),
                new Class<?>[]{TaskProgressMapper.class},
                (proxy, method, args) -> {
                    if ("selectByUserId".equals(method.getName())) {
                        throw new RuntimeException(outerMessage, cause);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 造出真实的 {@link TaskServiceGrpcImpl}。
     *
     * <p>⚠️ redis 传 {@code null}：{@code TaskDomainService.readProgressCache} 会
     * {@code catch (Exception)} 掉 NPE 并返回 {@code null} ⇒ 与 Go 侧
     * 「Redis 报错也算未命中、一律回源」的分支**同构**（{@code get_task_logic.go:51}）。
     */
    private static TaskServiceGrpcImpl newImpl(TaskProgressMapper mapper) {
        return new TaskServiceGrpcImpl(new TaskDomainService(null, mapper));
    }

    /**
     * 把一段文本压成<b>单行</b>可逐字节比对的形态。
     *
     * <p>⚠️ 转义规则必须与 Go 半边（{@code rpc_probe.go} 的 {@code escape}）<b>逐条一致</b>：
     * <pre>
     * {@code \} → {@code \\}   {@code |} → {@code \|}   LF → {@code \n}   CR → {@code \r}
     * </pre>
     *
     * <p>⚠️ 为什么 rpc 层也要转义：description 是<b>错误原文</b>，可以是任意文本
     * （本探针刻意塞了一条含 {@code |} 的样本）⇒ 不转义就会把一行拆成两行。
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

    /** 一条输入样本（name + value）。 */
    private record Case(String name, String value) {
    }

    /** 捕获 {@code StreamObserver} 收到的错误。 */
    private static final class CapturingObserver implements StreamObserver<GetTaskResp> {

        private Throwable error;

        @Override
        public void onNext(GetTaskResp value) {
            // 错误路径不会有 onNext。
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            // 错误路径不会有 onCompleted。
        }

        /**
         * 取客户端**真正收到**的 gRPC 状态。
         *
         * <p>⚠️ 不能用 {@code Status.fromThrowable(t)}：它会**沿 cause 链穿透**，
         * 而 {@code TaskServiceGrpcImpl} 送出去的那个 {@code StatusRuntimeException}
         * 才是 wire 上真实的东西 ⇒ 这里直接取它的 {@code getStatus()}。
         */
        String grpcCode() {
            Status st = statusOf();
            return st.getCode().name().toLowerCase(Locale.ROOT);
        }

        String grpcDescription() {
            Status st = statusOf();
            return st.getDescription() == null ? "" : st.getDescription();
        }

        private Status statusOf() {
            if (error instanceof StatusRuntimeException sre) {
                return sre.getStatus();
            }
            if (error instanceof io.grpc.StatusException se) {
                return se.getStatus();
            }
            return Status.UNKNOWN.withDescription(error == null ? "" : error.getMessage());
        }
    }

    /**
     * OBS 样本。
     *
     * <p>⚠️ 这两条是**两侧必然不同**的已知不可达差异：
     * <ul>
     *   <li>{@code obs_ok_status}：Go 的 {@code status.Error(codes.OK, msg)} 返回
     *       <b>null</b>（grpc-go {@code internal/status/status.go} 的
     *       {@code func (s *Status) Err() error}：{@code if s.Code() == codes.OK { return nil }}）
     *       ⇒ 「OK 状态不是错误」；Java 的 {@code Status.OK.asRuntimeException()}
     *       造出来的是一个<b>非 nil</b>、code=OK、<b>带 description</b> 的异常。</li>
     *   <li>{@code obs_wrapped_status}：Go 的 {@code status.FromError} 会用
     *       {@code errors.As} <b>穿透包装</b>找到内层 status（保住 code），
     *       但把 description 换成<b>外层 error 的全文</b>
     *       （grpc-go {@code status/status.go}：{@code p.Message = err.Error()}）；
     *       Java 侧只认<b>直接抛出</b>的 {@code StatusRuntimeException}
     *       ⇒ 包装过就落进 {@code catch (RuntimeException)} ⇒ 兜底成 UNKNOWN。</li>
     * </ul>
     */
    private static List<Case> obsCases() {
        List<Case> out = new ArrayList<>();

        // obs_ok_status：领域层抛一个 code=OK 的 StatusRuntimeException。
        CapturingObserver okObs = new CapturingObserver();
        TaskServiceGrpcImpl okImpl = newImpl(
                (TaskProgressMapper) Proxy.newProxyInstance(
                        TaskProgressMapper.class.getClassLoader(),
                        new Class<?>[]{TaskProgressMapper.class},
                        (proxy, method, args) -> {
                            if ("selectByUserId".equals(method.getName())) {
                                throw Status.OK.withDescription("user_id invalid").asRuntimeException();
                            }
                            throw new UnsupportedOperationException(method.getName());
                        }));
        okImpl.getTask(GetTaskReq.newBuilder().setUserId(1L).build(), okObs);
        out.add(new Case("obs_ok_status", okObs.grpcCode() + "|" + escape(okObs.grpcDescription())));

        // obs_wrapped_status：领域层抛一个**包装过**的 StatusRuntimeException。
        // ⚠️ 外层文案与 Go 半边 fmt.Errorf("outer: %w", status.Error(...)) 的产物逐字一致。
        String outer = "outer: rpc error: code = InvalidArgument desc = user_id invalid";
        CapturingObserver wrapObs = new CapturingObserver();
        TaskServiceGrpcImpl wrapImpl = newImpl(
                wrappingMapper(outer, Status.INVALID_ARGUMENT.withDescription("user_id invalid")
                        .asRuntimeException()));
        wrapImpl.getTask(GetTaskReq.newBuilder().setUserId(1L).build(), wrapObs);
        out.add(new Case("obs_wrapped_status",
                wrapObs.grpcCode() + "|" + escape(wrapObs.grpcDescription())));

        return out;
    }

    /**
     * 把 SLF4J 根 logger 的级别压到 {@code OFF}。
     *
     * <p>⚠️ 用反射而不是直接引 logback 的类：探针的编译 classpath 未必带 logback-classic
     * （SLF4J 的实现是运行期绑定）。找不到就跳过 —— 此时日志会混进 stdout，
     * 逐字节对拍会红 ⇒ run-parity.sh 会把它报成「真漂移」⇒ 守门失效（宁可红不可假绿）。
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

    /** 读 cases 文件：{@code name|value} 一行一条，空行与 {@code #} 注释行忽略。 */
    private static List<Case> readCases(Path path) throws Exception {
        List<Case> out = new ArrayList<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf('|');
            if (idx < 0) {
                System.err.println("bad case line (want name|value): " + line);
                System.exit(2);
            }
            out.add(new Case(line.substring(0, idx), line.substring(idx + 1)));
        }
        return out;
    }
}
