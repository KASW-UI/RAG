package com.rag.hot.hot.common.errmsg;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * ⚠️ tools/errmsg-probe/hot 的 Java 半边，**不是生产代码**。
 *
 * <p>它用 <b>真实的</b> {@code HotErrorCode} + {@code HotErrorMessages}
 * （hot-common 模块的编译产物，不是自己 new 出来的一个副本）输出规范化 dump，
 * 交给 {@code run-parity.sh} 与 Go 半边逐字节比对。
 *
 * <p>输出格式必须与 Go 半边（{@code tools/errmsg-probe/hot/go/main.go}）逐字节相同：
 * <pre>
 * CONST|&lt;code&gt;              枚举登记的码值（升序）
 * TABLE|&lt;code&gt;|&lt;msg&gt;        codeMsg 表每一条经 {@link HotErrorMessages#getMsg(int)} 取回的文案（升序）
 * FALLBACK|&lt;code&gt;|&lt;msg&gt;     未登记码经 {@link HotErrorMessages#getMsg(int)} 的回落结果
 * </pre>
 *
 * <p>⚠️ 四条必须注意的细节：
 * <ol>
 *   <li><b>不得抄写文案</b>：本探针只调 {@code HotErrorMessages.getMsg(int)}，
 *       自己不持有任何码值/文案常量 —— 否则探针就变成了「拿 RAG 的值验 RAG 的值」，
 *       必然自洽、必然假绿（AGENTS.md §3.3「绿测试可能是错误答案的守卫」）。</li>
 *   <li><b>本类与 hot-common 同包</b>（{@code com.rag.hot.hot.common.errmsg}）：
 *       这样才能读到 <b>包级私有</b>的 {@code HotErrorMessages.MSG_BY_CODE}
 *       —— 也就是 Go 侧那个包级私有的 {@code var codeMsg}。
 *       ⇒ TABLE 一节枚举的是<b>真实的表键</b>，而不是枚举值 ⇒
 *       「表里多了一条 / 少了一条」这类漂移<b>能被逐字节比对抓到</b>
 *       （若改成枚举 {@code HotErrorCode.values()} 当表键，多出来的表项就看不见了）。</li>
 *   <li><b>必须显式用 {@code \n}</b>：{@code System.out.println} 用的是平台行分隔符，
 *       跨平台会变成 {@code \r\n} 导致字节不一致 ⇒ 这里走
 *       {@link PrintStream#write(byte[])} 直接吐 UTF-8 字节。</li>
 *   <li><b>排序</b>：Go 的 map 遍历顺序随机，Java 的 {@code Map} 是插入序，
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

        // CONST：枚举登记的码值（升序）。
        Integer[] codes = Arrays.stream(HotErrorCode.values())
                .map(HotErrorCode::getCode)
                .sorted()
                .toArray(Integer[]::new);

        // TABLE：真实的 codeMsg 表键（升序）—— 刻意**不**复用上面的 codes，
        // 这样「表多一条 / 少一条」才会让两侧输出产生 diff。
        Integer[] tableKeys = HotErrorMessages.MSG_BY_CODE.keySet().stream()
                .sorted()
                .toArray(Integer[]::new);

        StringBuilder b = new StringBuilder();
        for (int code : codes) {
            b.append("CONST|").append(code).append('\n');
        }
        for (int code : tableKeys) {
            b.append("TABLE|").append(code).append('|').append(HotErrorMessages.getMsg(code)).append('\n');
        }
        for (int code : cases) {
            b.append("FALLBACK|").append(code).append('|').append(HotErrorMessages.getMsg(code)).append('\n');
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
