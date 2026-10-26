package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 守门：共享层转换器必须复刻 Go 的 <b>{@code maxBodyLen = 8 MiB}</b> 上限。
 *
 * <h2>Go 出处（真源 = L2 框架层 go-zero@v1.10.2）</h2>
 * <pre>
 * rest/httpx/requests.go:21
 *     maxBodyLen = 8 &lt;&lt; 20 // 8MB
 * rest/httpx/requests.go:117-124
 *     func ParseJsonBody(r *http.Request, v any) error {
 *         if withJsonBody(r) {
 *             reader := io.LimitReader(r.Body, maxBodyLen)   // ← 静默截断在 8 MiB
 *             return mapping.UnmarshalJsonReader(reader, v)
 *         }
 *         return mapping.UnmarshalJsonMap(nil, v)
 *     }
 * </pre>
 *
 * <h2>🔴 为什么这条必须守（2026-10-25 第十六轮 · 双侧探针实测）</h2>
 * <p>修之前 RAG 的转换器<b>整个读进堆里</b> ⇒ 一个 &gt; 8 MiB 的 JSON body：
 * <ul>
 *   <li>Go：被<b>静默截断</b> ⇒ 解析器拿到半个文档 ⇒ <b>报错</b>（域自己的错误形状）；</li>
 *   <li>RAG：<b>成功解析</b> ⇒ 请求继续走到业务 ⇒ <b>两侧行为相反</b>。</li>
 * </ul>
 * <p>⇒ 实测证据：{@code tools/json-body-probe/} 的用例 <b>J20</b>（9 MiB 单个 JSON 对象）：
 * Go = REJECTED，且错误详情长度 = <b>8388643 = 8 MiB + 3</b> ⇒ 截断点精确在 8 MiB；
 * RAG 修之前 = ACCEPTED。
 *
 * <h3>⚠️ 这条影响的是<b>所有</b>走共享转换器的域</h3>
 * <p>like / follow / message / task / user / admin / article 都经由
 * {@code GoStyleJsonBodyConverter} 解析 JSON body ⇒ 上限放在<b>共享层</b>，
 * 一次修复覆盖 7 个域 ⇒ 这也是它属于 global 收口域（而非某个域的局部问题）的原因。
 *
 * <h2>⚠️ 为什么边界要卡到 ±1 字节</h2>
 * <p>{@code io.LimitReader} 的语义是「<b>最多</b>读 N 字节」⇒
 * <b>正好 8 MiB</b> 的合法文档必须能解析成功，而 <b>8 MiB + 1</b> 的合法文档
 * 会在 8 MiB 处被截断 ⇒ 半个文档 ⇒ 解析失败。
 * ⇒ 只测「9 MiB 失败」不够：它无法区分「上限是 8 MiB」与「上限是 1 MiB / 16 MiB / 干脆没上限」。
 */
@DisplayName("🔴 Go 的 maxBodyLen = 8 MiB：共享转换器必须只读前 8 MiB")
class GoStyleJsonBodyMaxBodyLenTest {

    /**
     * Go {@code rest/httpx/requests.go:21} 的 {@code maxBodyLen = 8 << 20} 的**字面值**。
     *
     * <h3>🔴 为什么这里**刻意不用** {@code GoStyleJsonBodyConverter.GO_MAX_BODY_LEN}</h3>
     * <p>变异测试实测（2026-10-25 第十六轮 M2）发现：若用那个<b>常量</b>去构造边界 body，
     * 则把常量改成 1 MiB 之后，测试会<b>跟着缩到 1 MiB</b> ⇒ <b>恒绿</b> ⇒
     * **一条锁死了错误答案的绿测试**（PIT-GLOBAL-011 同族）。
     * ⇒ 故边界一律用<b>这里的字面值</b>，并另配一条断言把常量本身钉住。
     */
    private static final int GO_MAX_BODY_LEN_8MIB = 8 * 1024 * 1024;
    private static final ResolvableType MAP_TYPE = ResolvableType.forClass(Map.class);

    @Test
    @DisplayName("🔴 上限常量必须**恰好**等于 Go 的 8 MiB（Go: rest/httpx/requests.go:21 maxBodyLen = 8 << 20）")
    void maxBodyLenConstantMatchesGo() {
        assertEquals(GO_MAX_BODY_LEN_8MIB, GoStyleJsonBodyConverter.GO_MAX_BODY_LEN,
                "Go 侧 maxBodyLen 是**框架常量**（8 << 20）⇒ RAG 侧必须逐字节相同；"
                        + "⚠️ 本条是 M2 变异（改成 1 MiB）的唯一拦截点 —— "
                        + "若边界测试改用常量构造 body，就会与常量一起漂移 ⇒ 恒绿 ⇒ 守门空转");
    }

    // ==================================================================
    // ① 边界：正好 8 MiB ⇒ 通过
    // ==================================================================

    @Test
    @DisplayName("🔴 正好 8 MiB 的**合法** JSON ⇒ 解析成功（上限是「最多读 8 MiB」，不是「不到 8 MiB」）")
    void exactlyMaxBodyLenIsAccepted() throws java.io.IOException {
        byte[] body = jsonObjectOfSize(GO_MAX_BODY_LEN_8MIB);
        assertEquals(GO_MAX_BODY_LEN_8MIB, body.length, "构造的 body 必须正好 8 MiB");

        Object parsed = read("application/json", body);
        assertNotNull(parsed, "正好 8 MiB 的合法文档必须能解析成功（Go 的 LimitReader 允许读满 8 MiB）");
    }

    // ==================================================================
    // ② 边界：8 MiB + 1 ⇒ 被截断 ⇒ 解析失败
    // ==================================================================

    @Test
    @DisplayName("🔴 8 MiB + 1 的**合法** JSON ⇒ 解析失败（在 8 MiB 处被截断 ⇒ 半个文档）")
    void oneByteOverMaxBodyLenIsRejected() {
        byte[] body = jsonObjectOfSize(GO_MAX_BODY_LEN_8MIB + 1);

        assertThrows(Exception.class, () -> read("application/json", body),
                "Go 侧会在 8 MiB 处截断 ⇒ 解析器拿到半个文档 ⇒ 报错；"
                        + "RAG 若成功 ⇒ 说明没复刻 maxBodyLen");
    }

    // ==================================================================
    // ③ 探针 J20 的同款用例：9 MiB 单个 JSON 对象
    // ==================================================================

    @Test
    @DisplayName("🔴 9 MiB 单个 JSON 对象 ⇒ 解析失败（= 双侧探针的 J20）")
    void nineMiBJsonObjectIsRejected() {
        StringBuilder padding = new StringBuilder();
        for (int i = 0; i < 9 * 1024 * 1024; i++) {
            padding.append('x');
        }
        byte[] body = ("{\"brief\":\"" + padding + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(Exception.class, () -> read("application/json", body),
                "Go 侧 REJECTED（探针 J20 实测）⇒ RAG 必须一致");
    }

    // ==================================================================
    // ④ 上限落在「读了多少字节」上，而不只是「解析失败」
    // ==================================================================

    @Test
    @DisplayName("🔴 底层流最多只被读走 8 MiB 字节（上限是真的，不是「读满了再判」）")
    void underlyingStreamIsCappedAtEightMiB() {
        byte[] body = jsonObjectOfSize(GO_MAX_BODY_LEN_8MIB + 4096);
        CountingStream counting = new CountingStream(body);

        assertThrows(Exception.class, () -> read(counting, "application/json", GO_MAX_BODY_LEN_8MIB + 4096),
                "截断后的半个文档必然解析失败");

        assertTrue(counting.delivered() <= GO_MAX_BODY_LEN_8MIB,
                "底层流最多只能被读走 " + GO_MAX_BODY_LEN_8MIB + " 字节（Go 的 io.LimitReader 语义）；实际读走 "
                        + counting.delivered() + " 字节");
        assertTrue(counting.delivered() > 0, "必须真的读了 body（否则这条用例是空转）");
    }

    // ==================================================================
    // ⑤ 对照：小 body 不受影响
    // ==================================================================

    @Test
    @DisplayName("对照：1 KiB 合法 JSON ⇒ 解析成功（上限不能误伤正常路径）")
    void smallBodyIsUnaffected() throws java.io.IOException {
        byte[] body = jsonObjectOfSize(1024);
        assertNotNull(read("application/json", body), "正常路径必须照常工作");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 造一个<b>正好</b> {@code size} 字节的合法 JSON 对象 {@code {"a":"<padding>"}}。 */
    private static byte[] jsonObjectOfSize(int size) {
        // {"a":"  → 6 字节   "}  → 2 字节
        int padding = size - 8;
        StringBuilder sb = new StringBuilder(size);
        sb.append("{\"a\":\"");
        for (int i = 0; i < padding; i++) {
            sb.append('x');
        }
        sb.append("\"}");
        byte[] out = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (out.length != size) {
            throw new IllegalStateException("构造的 body 长度不符：" + out.length + " != " + size);
        }
        return out;
    }

    private static Object read(String contentType, byte[] body) throws IOException {
        return read(new ByteArrayInputStream(body), contentType, body.length);
    }

    private static Object read(InputStream body, String contentType, long contentLength) throws IOException {
        GoStyleJsonBodyConverter converter =
                new GoStyleJsonBodyConverter(JsonMapper.builder().build());
        return converter.read(MAP_TYPE,
                new SimpleJsonInputMessage(contentType, body, contentLength), Map.of());
    }

    /** 记录「被读走多少字节」的流。 */
    private static final class CountingStream extends InputStream {
        private final InputStream delegate;
        private long delivered;

        CountingStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        long delivered() {
            return delivered;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                delivered++;
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                delivered += n;
            }
            return n;
        }
    }

    /**
     * 最小的真实 {@link HttpInputMessage} 实现 —— <b>刻意不用 Mockito</b>（§3.3）。
     */
    private static final class SimpleJsonInputMessage implements HttpInputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final InputStream body;

        SimpleJsonInputMessage(String rawContentType, InputStream body, long contentLength) {
            // ⚠️ 用 add 而非 setContentType —— 后者会走 MediaType 解析并**归一**，
            //    那就等于把被测的「原始头值」提前改掉了。
            this.headers.add(HttpHeaders.CONTENT_TYPE, rawContentType);
            // ⚠️ **必须**给 Content-Length：Go 的判据是 `r.ContentLength > 0`，
            //    而缺 Content-Length（= chunked）在 Go 侧就是 -1 ⇒ **不读 body**
            //    ⇒ 转换器会返回 null（= UnmarshalJsonMap(nil, v)）⇒ 测不到上限。
            this.headers.setContentLength(contentLength);
            this.body = body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() {
            return body;
        }
    }
}
