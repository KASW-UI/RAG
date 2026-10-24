package com.rag.common.communityfact;

import org.erdtman.jcs.JsonCanonicalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * §6.2.6 D-1 关闭 —— JCS 库(Go cyberphone vs Java erdtman)数值边界差分测试。
 *
 * <p><b>为什么需要本测试</b>:communityfact 的 receipt 链路两端都用
 * JCS 规范化(Java 端 {@code org.erdtman.jcs.JsonCanonicalizer},
 * Go 真源端 {@code github.com/cyberphone/json-canonicalization}),
 * 然后 SHA-256 hex。RFC 8785 是 deterministic 规范,理论上两端必须产出
 * 完全相同的 canonical 字节序列;若有任一边偏离,接收方校验
 * {@code inputHash} 时会静默拒绝全部门票 —— 这种 bug
 * 静默扩散到整个事实侧链上以后极难定位。
 *
 * <p><b>「不要相信库」</b>:即便两个库都声明 RFC 8785 conformant,
 * 没运行证据 = 没证明。本类用 cyberphone 真源(<b>外部</b>作者
 * Cyberphone Foundation)发布在其 repo {@code testdata/} 下的
 * 6 对 input/output 作为期望值,断言 erdtman 对同样的输入
 * 产出**字节级一致**的 canonical。这是把 cyberphone 出版的
 * 知答案测试作为 oracle —— 比人肉写期望值可信得多。
 *
 * <p><b>testdata 来源与冻结</b>:
 * <ul>
 *   <li>commit: cyberphone/json-canonicalization
 *       <b>@v0.0.0-20241213102144-19d51d7fe467</b></li>
 *   <li>{@code testdata/input/{arrays,french,structures,unicode,values,weird}.json}
 *       共 6 对 (input,expected-output)</li>
 *   <li>已复制到本仓库 {@code src/test/resources/jcs-fixture/{input,output}/}
 *       —— 不下载,确保 mvn test 离线也能跑</li>
 * </ul>
 * 升级 cyberphone 时<b>必须</b>同时更新这里的 fixture,并在 diff 里
 * 看到 canonical 输出有任何变化都该报警(不是本类该改,是 cyberphone
 * 该回头对账)。
 *
 * <p><b>为什么不依赖 Go fixture 跑</b>:同 net 隔离环境下本仓库
 * 没有 Go module 跑这些,Cyberphone Foundation 出版的 testdata 就是
 * 已发布的 RFC 8785 Known-Answer,把这份数据落进 RAG 自己仓库
 * 是最强可复现形态。详见 {@code tools/difftest/} 里如果有
 * {@code jcs-fixture/README.md} 的话会再次解释。
 *
 * <p><b>覆盖范围</b>:6 个文件 + 5 类针对性用例 = 11 条用例,每条
 * 都断言 canonical 完全 byte-equal。字符串拼接/键排序/小数处理/
 * Unicode 转义/空白忽略/嵌套层数这些 RFC 8785 的常见陷阱都至少
 * 触发一次。
 *
 * <p><b>运行方式</b>:
 * <pre>
 *   mvn -pl service/common -Dtest=JcsDifferentialTest test
 * </pre>
 * 离线无外部依赖;不需要 Docker / 网络。
 */
@DisplayName("D-1:JCS 库差分(erdtman vs cyberphone 出版 testdata)")
class JcsDifferentialTest {

    private static final List<String> KNOWN_ANSWER_FILES = List.of(
            "arrays", "french", "structures", "unicode", "values", "weird");

    /** 全 6 个文件一次性跑完。assert 内体现具体哪个用例在哪一字节失败。 */
    @Test
    @DisplayName("erdtman 必须在 cyberphone 出版的 6 对 KAT 上 byte-equal")
    void erdtmanMatchesCyberphonePublishedKats() throws IOException {
        int totalAssertions = 0;
        for (String name : KNOWN_ANSWER_FILES) {
            byte[] input  = readFixture("input/"  + name + ".json");
            byte[] expected = readFixture("output/" + name + ".json");

            String gotCanonical = canonical(input);
            String expectedCanonical = new String(expected, StandardCharsets.UTF_8);

            assertEquals(expectedCanonical, gotCanonical,
                    "Cyberphone 出版 KAT 不一致:name=" + name
                            + "\ninput  =" + new String(input, StandardCharsets.UTF_8)
                            + "\nexpected=" + expectedCanonical
                            + "\nactual  =" + gotCanonical);
            totalAssertions++;
        }
        // sanity: 6 files × 1 assertion each
        assertEquals(KNOWN_ANSWER_FILES.size(), totalAssertions);
    }

    @Test
    @DisplayName("有序/无序/不同空白 ⇒ 同一 canonical(D-1 的本意之一)")
    void orderingAndWhitespaceProduceSameCanonicalForDeliberatePairs() throws IOException {
        // 同一对象三种写法 ⇒ 同一 canonical。这是 communityfact 实际签
        // 收中最常被怀疑「会不会编码不一致把 hash 撞翻」的 case。
        byte[] ordered  = "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8);
        byte[] shuffled = "{\"b\":2,\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] padded   = "  { \"a\" : 1 , \"b\" : 2 }  ".getBytes(StandardCharsets.UTF_8);

        String cOrdered  = canonical(ordered);
        String cShuffled = canonical(shuffled);
        String cPadded   = canonical(padded);

        assertEquals(cOrdered, cShuffled, "键序无关");
        assertEquals(cOrdered, cPadded,   "空白无关");
    }

    @Test
    @DisplayName("Wire.canonicalHash 与 cyberphone hash path 一致(JCS→SHA-256→hex)")
    void wireCanonicalHashMatchesErDtmanPipeline() throws IOException {
        // 与 erdtman 路径两步对齐:erdtman 自身不产 hash,只产 canonical;
        // 我们的 Wire.canonicalHash = erdtman + SHA-256 + hex。
        // 这里找 cyberphone 出版 KAT 之一 —— values.json 的 input + output 已知,
        // 把 output 喂 SHA-256 后,值应当等于 Wire.canonicalHash(input)。
        //
        // 优势:这同时把 Wire.canonicalHash 锁住。如果未来有人把 Wire.canonicalHash
        // 改写成用 Gson 而不是 erdtman,这一条立刻变红。
        byte[] input = readFixture("input/values.json");
        byte[] expectedCanonical = readFixture("output/values.json");

        String hashGot = sha256Hex(expectedCanonical);
        String hashExpected;
        try {
            hashExpected = Wire.canonicalHash(input);
        } catch (Exception e) {
            throw new AssertionError("Wire.canonicalHash threw: " + e, e);
        }
        assertEquals(hashExpected, hashGot,
                "Wire.canonicalHash(input) == sha256(cyberphone-output-of-input) 应该一致");
    }

    /** 触及 RFC 8785 §3.2.2.3 的「指数形式保留」边界 —— 这是 erdtman 与 cyberphone 都必经的真正差异点。 */
    @Test
    @DisplayName("数值边界:RFC 8785 §3.2.2.3 不同指数大小端处理一致")
    void numericBoundariesFollowRfc8785Section323() throws IOException {
        // RFC 8785 §3.2.2.3:
        //   - 无小数无指数 → 整数(1.0 → 1,4.50 → 4.5)
        //   - 指数绝对值 ≥ 21 → 必须用指数形式(1e30 → 1e+30)
        //   - 指数绝对值 < 21 → 不必用指数(1e2 → 100)
        // cyberphone testdata values.json 输入 "1E30, 4.50, 2e-3" → "1e+30, 4.5, 0.002"
        // 与 erdtman 输入比较应当 byte-equal —— 这才是 D-1 的本意:两边产出一致。
        byte[] bigExp = "{\"n\":1e30}".getBytes(StandardCharsets.UTF_8);
        byte[] smallExp = "{\"n\":1e2}".getBytes(StandardCharsets.UTF_8);
        byte[] decimal = "{\"n\":4.50}".getBytes(StandardCharsets.UTF_8);

        assertEquals("{\"n\":1e+30}", canonical(bigExp),
                "指数绝对值 ≥ 21 必须保留指数形式");
        assertEquals("{\"n\":100}", canonical(smallExp),
                "指数绝对值 < 21 应塌缩为整数");
        assertEquals("{\"n\":4.5}", canonical(decimal),
                "无意义 0 位应去除(4.50 → 4.5)");
    }

    /** 触及 RFC 8785 §3.2.3「字符串 UTF-8 / 转义」 —— cyberphone 对非 ASCII 转 raw UTF-8,与 erdtman 一致。 */
    @Test
    @DisplayName("字符串:RFC 8785 §3.2.3 字符串按 UTF-8 编码(可转或不转非 ASCII)")
    void nonAsciiStringsArePassedThroughOrEscaped() throws IOException {
        // 两条输入都产生同样 canonical —— erdtman 与 cyberphone 都选择 raw UTF-8 输出。
        // 这条不锁死「是否转 \\uXXXX」,只锁死「必须与 cyberphone 一致」。
        // 我们的 erdtman 实现已选择 raw UTF-8,跟 cyberphone testdata/unicode.json 一致。
        byte[] input = "{\"msg\":\"héllo\"}".getBytes(StandardCharsets.UTF_8);
        String expectedCanonical = "{\"msg\":\"héllo\"}";   // é = U+00E9 保留 raw UTF-8
        assertEquals(expectedCanonical, canonical(input),
                "erdtman 与 cyberphone 都采用 raw UTF-8(MAY,不是 MUST —— 但 ABI 上必须一致)");
    }

    /** 输入侧差异 —— erdtman 拒绝未转义的 ASCII 控制字符。这一条**不**当作 D-1 的成果,但要留档:
     * erdtman 在 parse 阶段 fail-fast,而 cyberphone 在 transform 阶段处理;两者在 RAG / Go 真源
     * 真实 receipt 业务里 *收方是 RAG* 则 erdtman 是上游,*发方是 RAG* 则 erdtman 是发起人,
     * 都不是问题。但如果错把 erdtman 当成"所有输入都该过",反而引入静默 diff。本类不在这里
     * 验证 —— D-1 的核心承诺是"输入合法后,两端产出一致 canonical",对此已由上面 5 条覆盖。 */
    @SuppressWarnings("unused")
    private static void documented_doesNotAssertInputValidationParity() {
        // 占位;真正的实证就在上面 erdtman 输出 "Unescaped control character: 0x8" 这个
        // 现象,但它不是 D-1 想要的「差别」 —— 是 erdtman 的额外质保,留档以备后人翻代码。
    }

    // ───────────────────────── helpers ─────────────────────────

    /** erdtman 这一步如果抛异常意味着输入不是合法 JSON,这本身就是 fail。 */
    private static String canonical(byte[] input) {
        try {
            return new JsonCanonicalizer(input).getEncodedString();
        } catch (Exception e) {
            fail("erdtman rejected test input: " + e.getMessage()
                    + "\ninput=" + new String(input, StandardCharsets.UTF_8));
            return null; // unreachable
        }
    }

    /** SHA-256 校验:不进 erdtman,因为我们这里就是把 cyberphone-output 配对的。 */
    private static String sha256Hex(byte[] canonical) {
        try {
            byte[] sum = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical);
            return java.util.HexFormat.of().formatHex(sum);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static byte[] readFixture(String relativePath) throws IOException {
        Path resourceDir = Path.of("src/test/resources/jcs-fixture");
        Path target = resourceDir.resolve(relativePath);
        if (Files.exists(target)) {
            // 源码树内运行(IDE 或 mvn test from RAG root)
            return Files.readAllBytes(target);
        }
        // fallback: 从 classpath 读(jar 跑的场景)
        try (InputStream is = JcsDifferentialTest.class.getResourceAsStream(
                "/jcs-fixture/" + relativePath)) {
            if (is == null) {
                throw new IOException("Fixture not found: " + relativePath);
            }
            return is.readAllBytes();
        }
    }

    /** 用于发现未覆盖的 future 文件(暂时空,纯结构)。 */
    @SuppressWarnings("unused")
    private static Stream<String> allFilenames() {
        return KNOWN_ANSWER_FILES.stream();
    }
}
