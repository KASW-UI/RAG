package com.rag.article.article.api;

import com.rag.article.article.api.config.GoStyleGetFormValuesFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 🔴 Go {@code strconv.Quote} 的<b>逐字节</b>对拍 —— 覆盖 {@code isGoPrintable} 的
 * <b>Latin-1 补充段</b>（{@code 0xA1..0xFF}）。
 *
 * <h2>为什么这条要单独测（而不是靠端到端）</h2>
 * {@code goQuote} 的<b>唯一</b>调用点是 {@code escapeErrorMessage}，而它拿到的输入
 * 是 <b>query 串</b>里 {@code %} 开头的切片。
 * <p>⚠️ <b>query 串里的非 ASCII 字节在 HTTP 入口就被 Tomcat 挡掉了</b>（连接器层，
 * 应用层<b>不可达</b>）：
 * <ul>
 *   <li>HTTP/1.1：{@code Http11InputBuffer} 解析请求行时逐字节查
 *       {@code HttpParser.isQueryRelaxed}，不通过即 400；</li>
 *   <li>HTTP/2：{@code StreamProcessor} 对 {@code :path} 的 query 段做<b>同一个</b>检查。</li>
 * </ul>
 * 而 {@code isQueryRelaxed} 对任何 {@code >= 0x80} 的字节都返回 false
 * （{@code HttpParser} 的 {@code IS_QUERY_RELAXED} 只由 ASCII 集合构成，
 * {@code relaxedQueryChars} 也只放宽 ASCII 的 {@code IS_RELAXABLE} 字符）
 * ⇒ <b>query 串恒为纯 ASCII</b> ⇒ {@code isGoPrintable} 的 Latin-1 分支
 * 在端到端路径上是<b>结构性死代码</b>。
 * <p>⇒ 正因为端到端<b>测不到</b>，才必须用<b>直接单元测试</b>把契约钉死：
 * 否则这段分支既无回归网、又在源码里<b>悄悄偏离 Go</b>（本类建立前就是那样）。
 *
 * <h2>Go 出处（逐行，消费点）</h2>
 * <pre>
 * Go 标准库 strconv/quote.go:31-56  appendQuotedWith
 *   for width := 0; len(s) &gt; 0; s = s[width:] {
 *       r := rune(s[0]); width = 1
 *       if r &gt;= utf8.RuneSelf { r, width = utf8.DecodeRuneInString(s) }
 *       if width == 1 &amp;&amp; r == utf8.RuneError {      // ← :46-51 非法 UTF-8 ⇒ \xNN
 *           buf = append(buf, `\x`...)
 *           buf = append(buf, lowerhex[s[0]&gt;&gt;4], lowerhex[s[0]&amp;0xF])
 *           continue
 *       }
 *       buf = appendEscapedRune(buf, r, quote, ASCIIonly, graphicOnly)
 *   }
 *
 * Go 标准库 strconv/quote.go:68-119  appendEscapedRune
 *   if r == rune(quote) || r == '\\' { buf = append(buf, '\\'); buf = append(buf, byte(r)); return buf }
 *   …
 *   } else if IsPrint(r) || graphicOnly &amp;&amp; isInGraphicList(r) {
 *       return utf8.AppendRune(buf, r)                 // ← :79-81 可打印 ⇒ 原样输出
 *   }
 *
 * Go 标准库 strconv/quote.go:522-563  IsPrint
 *   if r &lt;= 0xFF {
 *       if 0x20 &lt;= r &amp;&amp; r &lt;= 0x7E { return true }      // ← :525-528
 *       if 0xA1 &lt;= r &amp;&amp; r &lt;= 0xFF { return r != 0xAD }   // ← :529-532 软连字符例外
 *       return false
 *   }
 * </pre>
 *
 * <h2>Oracle 的来源</h2>
 * <b>Go 侧探针</b>（Go 1.25.11 标准库 {@code strconv.Quote}，逐字节十六进制输出）
 * —— 探针源码与<b>逐字输出</b>见本域台账
 * {@code docs/DRIFT/article/api.md}（第十二轮节）。
 * <p>⇒ 本类表里的 {@code expectedHex} 就是那份探针的<b>逐字</b>输出，不是手推的。
 */
class ArticleGoStrconvQuoteParityTest {

    /** RAG 侧的 {@code goQuote}（private static）—— 用反射调用，不为测试改生产代码的可见性。 */
    private static String goQuote(byte[] s) throws Exception {
        Method m = GoStyleGetFormValuesFilter.class.getDeclaredMethod("goQuote", byte[].class);
        m.setAccessible(true);
        return (String) m.invoke(null, (Object) s);
    }

    private static byte[] hex(String s) {
        String h = s.replace(" ", "");
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static void assertQuote(String expectedHex, String inputHex, String why) throws Exception {
        byte[] in = hex(inputHex);
        byte[] actual = goQuote(in).getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(hex(expectedHex), actual,
                why + "\n  输入字节 = " + inputHex + "\n  Go 期望 = " + expectedHex
                        + "\n  RAG 实得 = " + toHex(actual));
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }

    // =====================================================================
    // 1. Latin-1 补充段（0xA1..0xFF）—— 本类存在的**唯一**理由
    // =====================================================================

    @Test
    @DisplayName("🔴 Go: Latin-1 补充段（0xA1..0xFF）⇒ strconv.Quote **原样输出**，不转 \\u00XX")
    void latin1SupplementIsPrintedRawLikeGo() throws Exception {
        // Go 的 IsPrint：0xA1..0xFF ⇒ true（唯一例外 0xAD 软连字符）⇒ utf8.AppendRune 原样输出
        assertQuote("2225c3a922", "25c3a9",
                "Go: 'é'(U+00E9) 可打印 ⇒ strconv.Quote 原样输出 UTF-8 字节 c3 a9，"
                        + "**不能**写成 \\u00e9");
        assertQuote("2225c2a922", "25c2a9",
                "Go: '©'(U+00A9) 可打印 ⇒ 原样输出 c2 a9");
        assertQuote("2225c3bf22", "25c3bf",
                "Go: 'ÿ'(U+00FF) 可打印 ⇒ 原样输出 c3 bf");
    }

    @Test
    @DisplayName("🔴 Go: Latin-1 补充段的**唯一例外** U+00AD 软连字符 ⇒ \\u00ad（不是原样）")
    void softHyphenIsTheOnlyLatin1Exception() throws Exception {
        // Go quote.go:529-532 ⇒ return r != 0xAD ⇒ 0xAD **不可打印** ⇒ 落到 U+00XX 分支
        assertQuote("22255c753030616422", "25c2ad",
                "Go: 软连字符 U+00AD 是 IsPrint 在 Latin-1 段的唯一例外 ⇒ 输出 \\u00ad");
    }

    @Test
    @DisplayName("🔴 Go: U+00A0（NBSP，Latin-1 段**下界之下**）⇒ \\u00a0（不可打印）")
    void nbspBelowLatin1RangeIsNotPrintable() throws Exception {
        // Go quote.go:529 ⇒ 0xA1 <= r 才进 Latin-1 段；0xA0 落在下面 ⇒ false
        assertQuote("225c753030613022", "c2a0",
                "Go: U+00A0 是 unicode 的 Zs（空格分隔符）⇒ 不在 L/M/N/P/S 里 ⇒ 不可打印");
    }

    // =====================================================================
    // 2. 对照组：ASCII / 控制字符 / 非法 UTF-8 / 多字节
    // =====================================================================

    @Test
    @DisplayName("Go: ASCII 与控制字符的转义必须逐字节一致（对照组）")
    void asciiAndControlCharsMatchGo() throws Exception {
        assertQuote("22257a7a22", "257a7a",
                "Go: 纯 ASCII 可打印字符 ⇒ 原样（只有 \" 与 \\ 需转义）");
        assertQuote("222022", "20", "Go: 空格是 U+0020 ⇒ Go 的 IsPrint 含 ASCII 空格 ⇒ 原样");
        assertQuote("225c78376622", "7f", "Go: DEL(0x7F) 不可打印 ⇒ \\x7f");
        assertQuote("225c78303022", "00", "Go: NUL 不可打印 ⇒ \\x00");
        assertQuote("225c78303122", "01", "Go: SOH 不可打印 ⇒ \\x01");
        assertQuote("225c6122", "07", "Go: BEL ⇒ \\a");
        assertQuote("225c7622", "0b", "Go: VT ⇒ \\v");
        assertQuote("22255c2222", "2522", "Go: 字符串里的 \" ⇒ 永远加反斜杠");
        assertQuote("22255c5c22", "255c", "Go: 字符串里的 \\ ⇒ 永远加反斜杠");
    }

    @Test
    @DisplayName("Go: 非法 UTF-8 字节 ⇒ \\xNN（小写）；完整多字节字符 ⇒ 原样（对照组）")
    void invalidUtf8AndMultibyteMatchGo() throws Exception {
        // Go quote.go:46-51 —— width==1 且解码出 RuneError ⇒ 按**字节**输出 \xNN
        assertQuote("22255c7865325c78383222", "25e282",
                "Go: €(e2 82 ac) 被截成 2 字节 ⇒ 非法 UTF-8 ⇒ \\xe2\\x82");
        assertQuote("22255c7865345c78623822", "25e4b8",
                "Go: 中(e4 b8 ad) 被截成 2 字节 ⇒ 非法 UTF-8 ⇒ \\xe4\\xb8");
        // 完整的多字节字符 ⇒ 可打印 ⇒ 原样
        assertQuote("22e4b8ad22", "e4b8ad", "Go: 完整的中 ⇒ 原样 UTF-8");
        assertQuote("22f09f988022", "f09f9880", "Go: 完整的 emoji（非 BMP）⇒ 原样 UTF-8");
    }

    // =====================================================================
    // 3. 结构守卫：确认反射拿到的是真的 goQuote（防「测了个寂寞」）
    // =====================================================================

    @Test
    @DisplayName("结构守卫：反射必须真的命中 GoStyleGetFormValuesFilter.goQuote（否则上面全是假绿）")
    void reflectionActuallyHitsGoQuote() throws Exception {
        Method m = GoStyleGetFormValuesFilter.class.getDeclaredMethod("goQuote", byte[].class);
        assertNotNull(m, "goQuote 方法必须存在（重命名会让本类静默失效）");
        assertEquals("private static java.lang.String goQuote(byte[])",
                m.toString().replace("com.rag.article.article.api.config.GoStyleGetFormValuesFilter.", ""),
                "反射到的必须是 goQuote(byte[]) ⇒ 若签名变了，本类会假绿");
        // 冒烟：ASCII 输入的输出必须是 ASCII（Go 的 strconv.Quote 形状）
        assertEquals("\"%zz\"", goQuote(hex("257a7a")),
                "冒烟：ASCII 输入 ⇒ 输出 \"%zz\"");
    }
}
