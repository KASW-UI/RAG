import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
import com.rag.favorite.favorite.common.errmsg.FavoriteGrpcError;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ⚠️ tools/errmsg-probe/favorite 的 Java 半边，**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code com.rag.favorite.favorite.common.errmsg.*}
 * （favorite-common 模块的编译产物，不是自己 new 出来的一个副本）输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/favorite/go/main.go}）逐字节相同：
 * <pre>
 * CONST|&lt;code&gt;                 枚举登记的码值（升序）
 * TABLE|&lt;code&gt;|&lt;msg&gt;           每个码经 FavoriteErrorMessages.getMsg(int) 取回的文案（升序）
 * FALLBACK|&lt;code&gt;|&lt;msg&gt;        未登记码的回落结果
 * WIRE|&lt;bizCode&gt;|&lt;grpcCode&gt;|&lt;description&gt;   每个码经 FavoriteGrpcError.grpcError 构造出的 wire 形态
 * BIZCODE|&lt;case&gt;|&lt;code&gt;        FavoriteGrpcError.bizCodeFromError 的还原结果
 * OBS|&lt;case&gt;|&lt;value&gt;           **已知不可达差异**（两侧不同，单列）
 * </pre>
 *
 * <p>⚠️ 三条必须注意的细节：
 * <ol>
 *   <li><b>不得抄写文案</b>：本探针只调真实产物里的方法，
 *       自己不持有任何码值/文案常量 —— 否则探针就变成了「拿 RAG 的值验 RAG 的值」，
 *       必然自洽、必然假绿（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。</li>
 *   <li><b>必须显式用 {@code \n}</b>：{@code System.out.println} 用的是平台行分隔符，
 *       跨平台会变成 {@code \r\n} 导致字节不一致 ⇒ 这里走
 *       {@link PrintStream#write(byte[])} 直接吐 UTF-8 字节。</li>
 *   <li><b>排序</b>：Go 的 map 遍历顺序随机，Java 的 {@code values()} 是声明顺序，
 *       两侧都必须按码值升序才能逐字节比对。</li>
 * </ol>
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
        for (FavoriteErrorCode c : FavoriteErrorCode.values()) {
            constCodes.add(c.getCode());
        }
        List<Integer> tableCodes = new ArrayList<>(FavoriteErrorMessages.MSG_BY_CODE.keySet());
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
            b.append("TABLE|").append(code).append('|').append(FavoriteErrorMessages.getMsg(code)).append('\n');
        }
        // FALLBACK
        for (int code : cases) {
            b.append("FALLBACK|").append(code).append('|').append(FavoriteErrorMessages.getMsg(code)).append('\n');
        }

        // WIRE：favorite 域最核心的 L1 契约 —— **业务码不在 grpc-status 里，而在 description 里**。
        for (int code : codes) {
            StatusException se = FavoriteGrpcError.grpcError(Status.Code.INTERNAL, code);
            Status st = se.getStatus();
            b.append("WIRE|").append(code)
                    .append('|').append(st.getCode().name().toLowerCase())
                    .append('|').append(st.getDescription())
                    .append('\n');
        }

        // BIZCODE：业务码还原矩阵（与 Go 半边同构、同序）。
        for (BizCase c : bizCodeCases()) {
            b.append("BIZCODE|").append(c.name).append('|')
                    .append(FavoriteGrpcError.bizCodeFromError(c.err)).append('\n');
        }

        // OBS：已知不可达差异（两侧不同，单列）。
        for (BizCase c : obsCases()) {
            b.append("OBS|").append(c.name).append('|')
                    .append(FavoriteGrpcError.bizCodeFromError(c.err)).append('\n');
        }

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** 一条 BizCodeFromError 的还原样本。 */
    private record BizCase(String name, Throwable err) {
    }

    /**
     * BizCodeFromError 的还原矩阵 —— 必须与 Go 半边 {@code bizCodeCases} <b>同构同序</b>。
     *
     * <p>其中 4 条专门钉「文案反查优先于 gRPC 码类别」这条语义：
     * <ul>
     *   <li>{@code desc_5304}：FAILED_PRECONDITION 不在 Go 的 switch 白名单里 ⇒ 只有反查能给 5304；</li>
     *   <li>{@code desc_5304_unauth}：UNAUTHENTICATED <b>在</b>白名单（否则会给 5004）⇒ 反查优先必须给 5304；</li>
     *   <li>{@code desc_ok_text} / {@code desc_fail_text}：{@code "OK"} / {@code "FAIL"} 是
     *       Success / Error 的文案，但这两个码被排除在反查表外 ⇒ 都落到 INTERNAL 的兜底 5001。</li>
     * </ul>
     */
    private static List<BizCase> bizCodeCases() {
        return List.of(
                new BizCase("desc_5304", status(Status.Code.FAILED_PRECONDITION, "收藏暂不可撤回")),
                new BizCase("desc_5007", status(Status.Code.INTERNAL, "数据库查询失败")),
                new BizCase("desc_5008", status(Status.Code.INTERNAL, "数据库更新失败")),
                new BizCase("desc_5203", status(Status.Code.ALREADY_EXISTS, "收藏夹名称已存在")),
                new BizCase("desc_5301", status(Status.Code.NOT_FOUND, "收藏记录不存在")),
                new BizCase("desc_5101", status(Status.Code.NOT_FOUND, "用户不存在")),
                new BizCase("desc_1015", status(Status.Code.INTERNAL, "服务繁忙")),
                new BizCase("desc_5001", status(Status.Code.INTERNAL, "系统内部错误")),
                new BizCase("desc_unknown", status(Status.Code.INTERNAL, "未知文案xyz")),
                new BizCase("desc_unknown_failedpre", status(Status.Code.FAILED_PRECONDITION, "未知文案xyz")),
                new BizCase("desc_unknown_unavail", status(Status.Code.UNAVAILABLE, "未知文案xyz")),
                new BizCase("desc_unknown_unauth", status(Status.Code.UNAUTHENTICATED, "未知文案xyz")),
                new BizCase("desc_ok_text", status(Status.Code.INTERNAL, "OK")),
                new BizCase("desc_fail_text", status(Status.Code.INTERNAL, "FAIL")),
                new BizCase("desc_5304_unauth", status(Status.Code.UNAUTHENTICATED, "收藏暂不可撤回")),
                new BizCase("plain_error", new IllegalStateException("boom")),
                new BizCase("nil_error", null));
    }

    /**
     * OBS：两侧必然不同的已知差异（不可达 ⇒ 不构成行为漂移，但必须显式登记）。
     *
     * <p>⚠️ 这两条不是「期望两侧一致」，而是「把已知的实现层差异钉死」：
     * 若将来 Java 侧改了语义，OBS 段的值会变 ⇒ 探针变红 ⇒ 逼人重新评估。
     */
    private static List<BizCase> obsCases() {
        return List.of(
                // Go: status.Error(codes.OK, msg) 返回 nil ⇒ BizCodeFromError(nil) ⇒ 200。
                // Java: grpcError(OK, ...) 返回一个 code=OK 的 StatusException（非 nil）⇒ 走反查/兜底。
                // ⚠️ 用 asException()（不是 asRuntimeException()）—— 后者对 OK 会抛 IllegalStateException。
                new BizCase("obs_ok_status",
                        Status.fromCode(Status.Code.OK).withDescription("系统内部错误").asException()),
                // Go: status.FromError 认「顶层实现 GRPCStatus()」⇒ 文案反查命中 ⇒ 5007。
                // Java: extractStatus 只认 StatusException / StatusRuntimeException ⇒ 认不出 ⇒ 1015。
                new BizCase("obs_custom_grpcstatus", new IllegalStateException("数据库查询失败")));
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
