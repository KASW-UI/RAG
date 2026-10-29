import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeErrorMessages;
import com.rag.like.like.common.errmsg.LikeGrpcError;

import io.grpc.Metadata;
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
 * ⚠️ tools/errmsg-probe/like 的 Java 半边，**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code com.rag.like.like.common.errmsg.*}
 * （like-common 模块的编译产物，不是自己 new 出来的一个副本）输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/like/go/main.go}）逐字节相同：
 * <pre>
 * CONST|&lt;code&gt;                      枚举登记的码值（升序）
 * TABLE|&lt;code&gt;|&lt;msg&gt;                每个码经 LikeErrorMessages.getMsg(int) 取回的文案（升序）
 * FALLBACK|&lt;code&gt;|&lt;msg&gt;             未登记码的回落结果
 * WIRE|&lt;bizCode&gt;|&lt;description&gt;       每个码经 LikeGrpcError.grpcError 构造出的文案载体
 * WIRECODE|&lt;bizCode&gt;|&lt;carrier&gt;      grpc-status 承载体的形态（**两侧必然不同** ⇒ 白名单豁免）
 * BIZCODE|&lt;case&gt;|&lt;code&gt;             业务码还原矩阵（error → 码）
 * OBS|&lt;case&gt;|&lt;value&gt;                **两侧必然不同**的语义差异（单列）
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
        for (LikeErrorCode c : LikeErrorCode.values()) {
            constCodes.add(c.getCode());
        }
        List<Integer> tableCodes = new ArrayList<>(LikeErrorMessages.MSG_BY_CODE.keySet());
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
            b.append("TABLE|").append(code).append('|').append(LikeErrorMessages.getMsg(code)).append('\n');
        }
        // FALLBACK
        for (int code : cases) {
            b.append("FALLBACK|").append(code).append('|').append(LikeErrorMessages.getMsg(code)).append('\n');
        }

        // WIRE / WIRECODE：业务码的载体形态。
        // ⚠️ WIRE 钉「文案逐字透传」；WIRECODE 钉「业务码放在哪个载体上」
        //    （Go 放 grpc-status / Java 放 trailer ⇒ 两侧必然不同 ⇒ 白名单豁免）。
        for (int code : codes) {
            String msg = LikeErrorMessages.getMsg(code);
            StatusException ex = LikeGrpcError.grpcError(code, msg);
            Status st = ex.getStatus();
            b.append("WIRE|").append(code).append('|').append(st.getDescription()).append('\n');
            Metadata trailers = Status.trailersFromThrowable(ex);
            String biz = trailers == null ? "-" : trailers.get(LikeGrpcError.BIZ_CODE_TRAILER);
            b.append("WIRECODE|").append(code)
                    .append("|status=").append(st.getCode().name().toLowerCase())
                    .append(";trailer=").append(biz == null ? "-" : biz)
                    .append('\n');
        }

        // BIZCODE：业务码还原矩阵（与 Go 半边同构、同序）。
        for (BizCase c : bizCodeCases()) {
            b.append("BIZCODE|").append(c.name).append('|')
                    .append(LikeGrpcError.bizCodeFromError(c.err)).append('\n');
        }

        // OBS：已知不可达差异（两侧不同，单列）。
        for (BizCase c : obsCases()) {
            b.append("OBS|").append(c.name).append('|')
                    .append(LikeGrpcError.bizCodeFromError(c.err)).append('\n');
        }

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** 一条业务码还原样本。 */
    private record BizCase(String name, Throwable err) {
    }

    /**
     * 业务码还原矩阵 —— 必须与 Go 半边 {@code bizCodeCases} <b>同构同序</b>。
     *
     * <p>{@code own_*} 五条钉「本域自己的错误」：Go 侧业务码在 grpc-status 里，
     * Java 侧业务码在自定义 trailer 里 ⇒ 两侧都必须还原出同一个业务码。
     *
     * <p>{@code foreign_*} 五条钉「下游发来的、不是本域业务码的错误」：
     * Go like-api 直接把原始 gRPC 码当业务码（13 / 5 / 3 / 16 / 5001），
     * RAG 侧收敛到自己的代表码（1015 / 2001 / 1009）⇒ **两侧必然不同** ⇒ 白名单豁免
     * （依据：{@code docs/DRIFT/like/vs-common.md} 的 D-7，已登记、待拍板）。
     */
    private static List<BizCase> bizCodeCases() {
        return List.of(
                new BizCase("own_5003", LikeGrpcError.grpcError(5003, LikeErrorMessages.getMsg(5003))),
                new BizCase("own_2001", LikeGrpcError.grpcError(2001, LikeErrorMessages.getMsg(2001))),
                new BizCase("own_1015", LikeGrpcError.grpcError(1015, LikeErrorMessages.getMsg(1015))),
                new BizCase("own_3001", LikeGrpcError.grpcError(3001, LikeErrorMessages.getMsg(3001))),
                new BizCase("own_1003", LikeGrpcError.grpcError(1003, LikeErrorMessages.getMsg(1003))),
                new BizCase("foreign_internal", status(Status.Code.INTERNAL, "数据库查询失败")),
                new BizCase("foreign_notfound", status(Status.Code.NOT_FOUND, "用户不存在")),
                new BizCase("foreign_invalid_argument", status(Status.Code.INVALID_ARGUMENT, "输入有误")),
                new BizCase("foreign_unauthenticated", status(Status.Code.UNAUTHENTICATED, "未登录")),
                new BizCase("foreign_plain", new IllegalStateException("boom")));
    }

    /**
     * OBS：两侧必然不同的已知差异（不可达 ⇒ 不构成行为漂移，但必须显式登记）。
     *
     * <p>⚠️ 这三条不是「期望两侧一致」，而是「把已知的实现层差异钉死」：
     * 若将来 Java 侧改了语义，OBS 段的值会变 ⇒ 探针变红 ⇒ 逼人重新评估。
     */
    private static List<BizCase> obsCases() {
        return List.of(
                // Go: 还原只在 `if err != nil` 之内被调用 ⇒ nil 不可达（探针按兜底分支取值 5001）。
                // Java: bizCodeFromError(null) 显式返回 SUCCESS(200)（防御性取值）。
                new BizCase("obs_nil_error", null),
                // Go: status.Error(codes.OK, msg) 返回 nil ⇒ 落到兜底分支 ⇒ 5001。
                // Java: asException() 得到一个 code=OK 的 StatusException（非 nil）⇒ 走反查/兜底 ⇒ 1015。
                // ⚠️ 用 asException()（不是 asRuntimeException()）—— 后者对 OK 会抛 IllegalStateException。
                new BizCase("obs_ok_status",
                        Status.fromCode(Status.Code.OK).withDescription("系统内部错误").asException()),
                // Go: status.FromError 认「顶层实现 GRPCStatus()」⇒ 还原出原始 gRPC 码 13。
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
