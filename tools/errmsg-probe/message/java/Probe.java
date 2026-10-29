package com.rag.message.message.common.errmsg;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ⚠️ tools/errmsg-probe/message 的 Java 半边，**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code com.rag.message.message.common.errmsg.*}
 * （message-common 模块的编译产物，不是自己 new 出来的一个副本）输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/message/go/main.go}）逐字节相同：
 * <pre>
 * CONST|&lt;code&gt;                 枚举登记的码值（升序）
 * TABLE|&lt;code&gt;|&lt;msg&gt;           每个码经 MessageErrorMessages.getMsg(int) 取回的文案（升序）
 * FALLBACK|&lt;code&gt;|&lt;msg&gt;        未登记码的回落结果
 * CODEERR|&lt;code&gt;|&lt;errText&gt;     MessageGrpcError.newCode(int) 的 getMessage()（升序）
 * WIRE|&lt;bizCode&gt;|&lt;grpcCode&gt;|&lt;desc&gt;   每个码经 MessageGrpcError.grpcError 构造出的 wire 形态
 * WIREX|&lt;grpcCode&gt;|&lt;bizCode&gt;|&lt;desc&gt;  grpc 码 × 业务码 的 wire 矩阵
 * BIZCODE|&lt;case&gt;|&lt;code&gt;        MessageGrpcError.bizCodeFromError 的还原结果
 * OBS|&lt;case&gt;|&lt;value&gt;           **已知不可达差异**（两侧不同，单列）
 * </pre>
 *
 * <p>⚠️ 三条必须注意的细节：
 * <ol>
 *   <li><b>不得抄写文案</b>：本探针只调真实产物里的方法，
 *       自己不持有任何码值/文案常量 —— 否则探针就变成了「拿 RAG 的值验 RAG 的值」，
 *       必然自洽、必然假绿（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。
 *       ⚠️ 例外：bizCodeCases 里的 description 是<b>反查表的输入</b>，必须写字面量才能命中某条文案；
 *       但这些字面量的<b>期望输出码</b>由真源自己算出来，不是手抄的。</li>
 *   <li><b>必须显式用 {@code \n}</b>：{@code System.out.println} 用的是平台行分隔符，
 *       跨平台会变成 {@code \r\n} 导致字节不一致 ⇒ 这里走
 *       {@link PrintStream#write(byte[])} 直接吐 UTF-8 字节。</li>
 *   <li><b>排序</b>：Go 的 map 遍历顺序随机，Java 的 {@code values()} 是声明顺序，
 *       两侧都必须按码值升序才能逐字节比对。</li>
 * </ol>
 *
 * <p>⚠️ <b>本探针放在 message-common 的包内</b>（不是默认包）：
 * {@code MessageErrorMessages.MSG_BY_CODE} 是<b>包级可见</b>（对应 Go 的包级私有
 * {@code var codeMsg}）⇒ 同包才能读它做「枚举码集合 ≡ 文案表键集合」的自检。
 */
public final class Probe {

    private Probe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: Probe <cases-file>");
            System.exit(2);
        }

        List<Integer> cases = readCases(Path.of(args[0]));

        // ── Java 侧自检：枚举登记的码集合 ≡ MSG_BY_CODE 的键集合 ──────────────
        // 与 Go 半边的 selfCheck 对称：任何一侧「加了码忘了加文案」都会在这里响。
        List<Integer> constCodes = new ArrayList<>();
        for (MessageErrorCode c : MessageErrorCode.values()) {
            constCodes.add(c.getCode());
        }
        List<Integer> tableCodes = new ArrayList<>(MessageErrorMessages.MSG_BY_CODE.keySet());
        if (!constCodes.containsAll(tableCodes) || !tableCodes.containsAll(constCodes)) {
            System.err.println("JAVA SELF-CHECK FAILED: enum codes != MSG_BY_CODE keys");
            System.exit(3);
        }

        Integer[] codes = constCodes.stream().sorted().toArray(Integer[]::new);

        StringBuilder b = new StringBuilder();

        // CONST
        for (int code : codes) {
            b.append("CONST|").append(code).append('\n');
        }
        // TABLE
        for (int code : codes) {
            b.append("TABLE|").append(code).append('|').append(MessageErrorMessages.getMsg(code)).append('\n');
        }
        // FALLBACK
        for (int code : cases) {
            b.append("FALLBACK|").append(code).append('|').append(MessageErrorMessages.getMsg(code)).append('\n');
        }
        // CODEERR
        for (int code : codes) {
            b.append("CODEERR|").append(code).append('|')
                    .append(MessageGrpcError.newCode(code).getMessage()).append('\n');
        }

        // WIRE：message 域最核心的 L1 契约 —— **业务码不在 grpc-status 里，而在 description 里**。
        for (int code : codes) {
            StatusException se = MessageGrpcError.grpcError(Status.Code.INTERNAL, code);
            Status st = se.getStatus();
            b.append("WIRE|").append(code)
                    .append('|').append(st.getCode().name().toLowerCase())
                    .append('|').append(st.getDescription())
                    .append('\n');
        }

        // WIREX：grpc 码 × 业务码 的 wire 矩阵（与 Go 半边同构、同序）。
        for (WireCode gc : WIRE_CODES) {
            for (int biz : WIRE_BIZ_CODES) {
                StatusException se = MessageGrpcError.grpcError(gc.code, biz);
                Status st = se.getStatus();
                b.append("WIREX|").append(gc.name)
                        .append('|').append(biz)
                        .append('|').append(st.getCode().name().toLowerCase())
                        .append('|').append(st.getDescription())
                        .append('\n');
            }
        }

        // BIZCODE：业务码还原矩阵（与 Go 半边同构、同序）。
        for (BizCase c : bizCodeCases()) {
            b.append("BIZCODE|").append(c.name).append('|')
                    .append(MessageGrpcError.bizCodeFromError(c.err)).append('\n');
        }

        // OBS：已知不可达差异（两侧不同，单列）。
        for (BizCase c : obsCases()) {
            b.append("OBS|").append(c.name).append('|')
                    .append(MessageGrpcError.bizCodeFromError(c.err)).append('\n');
        }

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** 一条 BizCodeFromError 的还原样本。 */
    private record BizCase(String name, Throwable err) {
    }

    /** WIREX 矩阵用到的 grpc 码（与 Go 半边 wireGrpcCodes 同序）。 */
    private record WireCode(String name, Status.Code code) {
    }

    /**
     * WIREX 矩阵用到的 grpc 码。
     *
     * <p>⚠️ 取值理由：前 6 个<b>在</b> Go 的 BizCodeFromError switch 白名单里
     * （{@code errmsg.go:117-132}），后 2 个（UNAVAILABLE / DEADLINE_EXCEEDED）<b>不在</b>
     * ⇒ 用来钉「default ⇒ 1015」。
     */
    private static final List<WireCode> WIRE_CODES = List.of(
            new WireCode("invalid_argument", Status.Code.INVALID_ARGUMENT),
            new WireCode("unauthenticated", Status.Code.UNAUTHENTICATED),
            new WireCode("permission_denied", Status.Code.PERMISSION_DENIED),
            new WireCode("already_exists", Status.Code.ALREADY_EXISTS),
            new WireCode("not_found", Status.Code.NOT_FOUND),
            new WireCode("internal", Status.Code.INTERNAL),
            new WireCode("unavailable", Status.Code.UNAVAILABLE),
            new WireCode("deadline_exceeded", Status.Code.DEADLINE_EXCEEDED));

    /**
     * WIREX 矩阵用到的业务码（与 Go 半边 wireBizCodes 同序）。
     *
     * <p>⚠️ 取值理由：5101 / 5201 是 message 域<b>专属</b>码（别的域没有 ⇒ 撞车即漂移），
     * 5007 是 DB 码。
     */
    private static final List<Integer> WIRE_BIZ_CODES = List.of(
            MessageErrorCode.ERROR_USER_NOT_FOUND.getCode(),
            MessageErrorCode.ERROR_SELF_CHAT.getCode(),
            MessageErrorCode.ERROR_DB_SELECT.getCode());

    /**
     * BizCodeFromError 的还原矩阵 —— 必须与 Go 半边 {@code bizCodeCases} <b>同构同序</b>。
     *
     * <p>⚠️ message 域与 follow 域的<b>判定顺序相反</b>（Go 真源的既定语义）：
     * Go {@code errmsg.go:104-106} 是<b>先</b>判 {@code err.(*CodeError)}、<b>后</b>判 status；
     * 本实现的 {@code bizCodeFromError} 也照此排 ⇒ {@code code_error} / {@code code_error_wrapped}
     * 两条专门钉这个顺序（把顺序对调 ⇒ 这两条会红）。
     *
     * <p>其中多条专门钉「文案反查优先于 gRPC 码类别」这条语义：
     * <ul>
     *   <li>{@code desc_5101}：NOT_FOUND 在 Go 的 switch 白名单里（否则会给 5002）⇒ 反查必须给 5101；</li>
     *   <li>{@code desc_5201}：PERMISSION_DENIED 在白名单（否则会给 5005）⇒ 反查必须给 5201；</li>
     *   <li>{@code desc_5201_unauth}：UNAUTHENTICATED <b>在</b>白名单（否则会给 5004）⇒ 反查优先必须给 5201；</li>
     *   <li>{@code desc_5207}：INTERNAL 在白名单（否则会给 5001）⇒ 反查必须给 5207；</li>
     *   <li>{@code desc_ok_text} / {@code desc_fail_text}：{@code "OK"} / {@code "FAIL"} 是
     *       Success / Error 的文案，但这两个码被排除在反查表外 ⇒ 都落到 INTERNAL 的兜底 5001。</li>
     *   <li>{@code code_error_wrapped}：Go 的 {@code err.(*CodeError)} 是<b>顶层直接类型断言</b>
     *       （不走 cause 链）⇒ 包一层就认不出 ⇒ 1015。</li>
     * </ul>
     */
    private static List<BizCase> bizCodeCases() {
        return List.of(
                new BizCase("nil_error", null),
                new BizCase("plain_error", new IllegalStateException("boom")),
                new BizCase("code_error", MessageGrpcError.newCodeMsg(
                        MessageErrorCode.ERROR_INVALID_PARAM.getCode(), "invalid parameter")),
                new BizCase("code_error_wrapped", new RuntimeException("wrapped",
                        MessageGrpcError.newCodeMsg(
                                MessageErrorCode.ERROR_INVALID_PARAM.getCode(), "invalid parameter"))),
                new BizCase("desc_5001", status(Status.Code.INTERNAL, "internal server error")),
                new BizCase("desc_5002", status(Status.Code.NOT_FOUND, "record not found")),
                new BizCase("desc_5007", status(Status.Code.INTERNAL, "database query failed")),
                new BizCase("desc_5101", status(Status.Code.NOT_FOUND, "user not found")),
                new BizCase("desc_5201", status(Status.Code.PERMISSION_DENIED, "self chat is not allowed")),
                new BizCase("desc_5207", status(Status.Code.INTERNAL, "unsupported sender role")),
                new BizCase("desc_5201_unauth", status(Status.Code.UNAUTHENTICATED, "self chat is not allowed")),
                new BizCase("desc_5002_runtime", status(Status.Code.NOT_FOUND, "record not found")),
                new BizCase("desc_5002_exception",
                        Status.fromCode(Status.Code.NOT_FOUND).withDescription("record not found").asException()),
                new BizCase("wrapped_unknown",
                        new RuntimeException("wrapped", status(Status.Code.NOT_FOUND, "record not found"))),
                new BizCase("desc_unknown", status(Status.Code.INTERNAL, "unknown text xyz")),
                new BizCase("desc_unknown_invalid", status(Status.Code.INVALID_ARGUMENT, "unknown text xyz")),
                new BizCase("desc_unknown_unauth", status(Status.Code.UNAUTHENTICATED, "unknown text xyz")),
                new BizCase("desc_unknown_perm", status(Status.Code.PERMISSION_DENIED, "unknown text xyz")),
                new BizCase("desc_unknown_exists", status(Status.Code.ALREADY_EXISTS, "unknown text xyz")),
                new BizCase("desc_unknown_notfound", status(Status.Code.NOT_FOUND, "unknown text xyz")),
                new BizCase("desc_unknown_unavail", status(Status.Code.UNAVAILABLE, "unknown text xyz")),
                new BizCase("desc_unknown_deadline", status(Status.Code.DEADLINE_EXCEEDED, "unknown text xyz")),
                new BizCase("desc_ok_text", status(Status.Code.INTERNAL, "OK")),
                new BizCase("desc_fail_text", status(Status.Code.INTERNAL, "FAIL")));
    }

    /**
     * OBS：两侧必然不同的已知差异（不可达 ⇒ 不构成行为漂移，但必须显式登记）。
     *
     * <p>⚠️ 这三条不是「期望两侧一致」，而是「把已知的实现层差异钉死」：
     * 若将来 Java 侧改了语义，OBS 段的值会变 ⇒ 探针变红 ⇒ 逼人重新评估。
     */
    private static List<BizCase> obsCases() {
        return List.of(
                // Go: status.Error(codes.OK, msg) 返回 nil ⇒ BizCodeFromError(nil) ⇒ 200。
                // Java: 造出来的是一个 code=OK 的 StatusException（非 nil）⇒ 走反查/兜底。
                // ⚠️ 用 asException()（不是 asRuntimeException()）—— 后者对 OK 会抛 IllegalStateException。
                new BizCase("obs_ok_status",
                        Status.fromCode(Status.Code.OK).withDescription("internal server error").asException()),
                // Go: status.FromError 认「顶层实现 GRPCStatus()」⇒ 文案反查命中 ⇒ 5101。
                // Java: extractStatus 只认 StatusException / StatusRuntimeException ⇒ 认不出 ⇒ 1015。
                new BizCase("obs_custom_grpcstatus", new IllegalStateException("user not found")),
                // Go: fmt.Errorf("%w") 的 Error() 拼上内层文本（"rpc error: ..."）⇒ 反查落空 ⇒ 5002。
                // Java: Throwable#getMessage() 只有外层文本 ⇒ 反查命中 ⇒ 5101。
                new BizCase("obs_wrapped_outer_msg",
                        new RuntimeException("user not found", status(Status.Code.NOT_FOUND, "user not found"))));
    }

    /** 构造一个带 description 的 gRPC 异常（对应 Go 的 {@code status.Error(code, msg)}）。 */
    private static StatusRuntimeException status(Status.Code code, String description) {
        return Status.fromCode(code).withDescription(description).asRuntimeException();
    }

    /** 读 cases 文件：一行一个 int，空行与 {@code #} 注释行忽略。 */
    private static List<Integer> readCases(Path path) throws Exception {
        List<Integer> out = new ArrayList<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            out.add(Integer.valueOf(line));
        }
        return out;
    }
}
