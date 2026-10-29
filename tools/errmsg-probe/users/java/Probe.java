import com.rag.users.common.errmsg.ErrorCode;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ⚠️ tools/errmsg-probe/users 的 Java 半边，**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code com.rag.users.common.errmsg.ErrorCode}
 * （users-common 模块的编译产物，不是自己 new 出来的一个副本）输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>⚠️ <b>users 域的 RAG 侧结构与其它域不同</b>：{@code users-common/…/errmsg/} 下
 * <b>只有 {@code ErrorCode.java} 一个类</b> ⇒ <b>没有独立的文案表类</b> ——
 * <b>枚举本身就是那张表</b>（每个常量同时带 code 与 msg），
 * 查表入口是 {@code ErrorCode.getMsg(int)}（{@code ErrorCode.java:93-95}）。
 * 未知码的回落语义在 {@code ErrorCode.of(int)}（{@code ErrorCode.java:88-90}）：
 * 找不到 ⇒ 返回 {@code ERROR} ⇒ 提示语 {@code "FAIL"}。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/users/go/main.go}）逐字节相同：
 * <pre>
 * CONST|&lt;code&gt;            枚举登记的码值（升序）
 * TABLE|&lt;code&gt;|&lt;msg&gt;      每个码经 ErrorCode.getMsg(int) 取回的文案（升序）
 * FALLBACK|&lt;code&gt;|&lt;msg&gt;   未登记码的回落结果
 * </pre>
 *
 * <p>⚠️ 两条必须注意的细节：
 * <ol>
 *   <li><b>不得抄写文案</b>：本探针只调真实产物里的方法，
 *       自己不持有任何码值/文案常量 —— 否则探针就变成了「拿 RAG 的值验 RAG 的值」，
 *       必然自洽、必然假绿（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。</li>
 *   <li><b>必须显式用 {@code \n}</b>：{@code System.out.println} 用的是平台行分隔符，
 *       跨平台会变成 {@code \r\n} 导致字节不一致 ⇒ 这里走
 *       {@link PrintStream#write(byte[])} 直接吐 UTF-8 字节。</li>
 * </ol>
 *
 * <p>⚠️ <b>本域没有 WIRE / BIZCODE 层</b>：users 域**没有** {@code *GrpcError} 这样的
 * gRPC 错误载体类（真源 {@code service/user/common/errmsg/errmsg.go} 也**不 import grpc**，
 * 只有 const / codeMsg / GetErrMsg 三样）⇒ 无「业务码放在哪个载体上」的契约可比。
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

        // ── Java 侧自检：枚举里不得有**重复码** ────────────────────────────────
        // ⚠️ ErrorCode 用 HashMap 建 code→枚举 的反查表（ErrorCode.java:74-85）
        //    ⇒ 一旦有人写了两个同值常量，HashMap 会**静默吞掉一个** ⇒ 表与枚举不再等价。
        //    这里显式断言「枚举条数 == 去重后的码数」。
        List<Integer> constCodes = new ArrayList<>();
        for (ErrorCode c : ErrorCode.values()) {
            constCodes.add(c.getCode());
        }
        Set<Integer> distinct = new HashSet<>(constCodes);
        if (distinct.size() != constCodes.size()) {
            System.err.println("JAVA SELF-CHECK FAILED: duplicate codes in ErrorCode");
            System.exit(3);
        }

        Integer[] codes = constCodes.stream().sorted().toArray(Integer[]::new);

        StringBuilder b = new StringBuilder();

        // CONST
        for (int code : codes) {
            b.append("CONST|").append(code).append('\n');
        }
        // TABLE
        // ⚠️ Go 侧对 1017 / 1018 会回落到 "FAIL"（真源 codeMsg 里没登记这两个码），
        //    RAG 侧枚举自带中文文案 ⇒ 这两行必然不同 ⇒ 由白名单逐条豁免（见 run-parity.sh）。
        for (int code : codes) {
            b.append("TABLE|").append(code).append('|').append(ErrorCode.getMsg(code)).append('\n');
        }
        // FALLBACK
        for (int code : cases) {
            b.append("FALLBACK|").append(code).append('|').append(ErrorCode.getMsg(code)).append('\n');
        }

        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        out.write(b.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
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
