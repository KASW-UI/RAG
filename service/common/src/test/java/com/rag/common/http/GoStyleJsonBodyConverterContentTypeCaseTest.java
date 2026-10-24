package com.rag.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * <h2>本类钉死什么</h2>
 *
 * <p><b>T-1（2026-10-25 修复）</b>：Go 判据是
 * {@code strings.Contains(r.Header.Get("Content-Type"), "application/json")}
 * ⇒ 读的是<b>客户端发来的原始头值</b> ⇒ <b>大小写敏感</b>。
 *
 * <p>而修复前的 RAG 实现读的是 {@code getContentType().toString()}
 * ⇒ Spring 已把 type/subtype <b>小写化</b> ⇒ 判定变成<b>大小写不敏感</b>
 * ⇒ {@code Content-Type: Application/JSON} 在 Go 侧<b>不读 body</b>、在 RAG 侧<b>读 body</b>
 * ⇒ <b>两侧行为方向相反</b>（不是「RAG 更严」那种可签字的偏离）。
 *
 * <h2>为什么不用 Mockito mock</h2>
 *
 * <p>AGENTS.md §3.3.3：纯 mock 单测不算数。这里被测的机制是
 * <b>Spring 的 {@link HttpHeaders} 到底返回什么</b> ——
 * 恰恰是「mock 掉就等于把被测对象本身删掉」的那一层。
 * ⇒ 本类用<b>真 {@code HttpHeaders}</b>（真解析、真序列化）+ <b>真 converter</b>，
 * 只把 {@link HttpInputMessage} 这个两方法的接口用最小的真实实现顶掉。
 *
 * <h2>Go 出处（真源 = L2 框架层 go-zero@v1.10.2）</h2>
 *
 * <pre>
 * // rest/httpx/requests.go:153-155
 * func withJsonBody(r *http.Request) bool {
 *     return r.ContentLength &gt; 0 &amp;&amp; strings.Contains(r.Header.Get(header.ContentType), header.ApplicationJson)
 * }
 * // rest/internal/header/headers.go:5  ⇒  ApplicationJson = "application/json"
 * </pre>
 */
@DisplayName("T-1：Go 判据读的是原始 Content-Type 头值 ⇒ 大小写敏感（对齐 go-zero withJsonBody）")
class GoStyleJsonBodyConverterContentTypeCaseTest {

    private static final String BODY = "{\"target_type\":\"article\",\"target_ids\":[\"1\"]}";
    private static final ResolvableType DTO_TYPE = ResolvableType.forClass(java.util.Map.class);

    // ==================== ① 大小写：核心漂移点 ====================

    @Test
    @DisplayName("① Content-Type: Application/JSON（大写）⇒ **不读 body**（Go 同形）")
    void mixedCaseContentTypeIsNotRead() throws java.io.IOException {
        // Go: strings.Contains("Application/JSON", "application/json") == false ⇒ 不读 body
        assertNull(readWithContentType("Application/JSON"),
                "Go 侧大小写敏感 ⇒ 不读 body；RAG 若读了 ⇒ 与 Go 相反");
    }

    @Test
    @DisplayName("① 变体：APPLICATION/JSON / application/JSON ⇒ 同样**不读 body**")
    void upperAndLowerVariantAreNotRead() throws java.io.IOException {
        assertNull(readWithContentType("APPLICATION/JSON"), "全大写 ⇒ Go 不读");
        assertNull(readWithContentType("application/JSON"), "仅 J 大写 ⇒ Go 不读");
    }

    @Test
    @DisplayName("① 对照：标准小写 + 有体 ⇒ **读 body**（修复没把正常路径也打掉）")
    void lowerCaseContentTypeIsStillRead() throws java.io.IOException {
        Object parsed = readWithContentType("application/json");
        assertNotNull(parsed, "标准 JSON 头必须照常读 body");
    }

    @Test
    @DisplayName("① 对照：带 charset 参数 ⇒ **读 body**（Go 是 Contains 不是精确相等）")
    void contentTypeWithCharsetIsRead() throws java.io.IOException {
        assertNotNull(readWithContentType("application/json; charset=utf-8"),
                "Go 是子串包含 ⇒ 带 charset 依然命中");
        assertNotNull(readWithContentType("application/json;charset=utf-8"),
                "Go 是子串包含 ⇒ 无空格写法同样命中");
    }

    // ==================== ② 畸形头：Go 不抛，修复前 RAG 抛 ====================

    @Test
    @DisplayName("② Spring 能容忍的头内畸形（Go 同样按字面 Contains 命中）⇒ **不抛异常**")
    void toleratedMalformedContentTypeDoesNotThrow() throws java.io.IOException {
        // Go: strings.Contains("application/json;;charset", "application/json") == true ⇒ 读 body
        // ⚠️ 修复前：getContentType() → MediaType.parseMediaType 抛 InvalidMediaTypeException
        //    （unchecked）⇒ 请求 500 ⇒ 与 Go 相反。
        Object parsed = readWithContentType("application/json;;charset");
        assertNotNull(parsed, "Go 侧字面 Contains 命中 ⇒ 读 body；RAG 不该抛异常");
    }

    @Test
    @DisplayName("② 逗号多值头（Go 只做字面 Contains，不会抛）⇒ **不抛异常**")
    void commaJoinedContentTypeDoesNotThrow() throws java.io.IOException {
        // Go: strings.Contains("application/json, application/xml", "application/json") == true
        //   ⇒ 命中 ⇒ Go 照常读 body（Go 从不解析 MIME ⇒ 不会抛）
        // ⚠️ 修 A1 之前：抛点在 super.read() 内部的 MediaType 解析
        //   （InvalidMediaTypeException: Invalid token character ','）⇒ 请求 500 ⇒ 与 Go 相反
        Object parsed = readWithContentType("application/json, application/xml");
        assertNotNull(parsed, "Go 是字面 Contains ⇒ 命中即读 body；RAG 不该抛异常");
    }

    @Test
    @DisplayName("② charset=gbk ⇒ Go 的 encoding/json 只认 UTF-8 ⇒ **解析失败**（RAG 同形）")
    void nonUtf8CharsetFailsLikeGo() {
        // Go: encoding/json 只认 UTF-8 ⇒ GBK 字节是非法 UTF-8 ⇒ 解码失败
        // ⚠️ 若把 charset 透传给 Jackson ⇒ 会按 GBK 解码 ⇒ 变成「成功」⇒ 与 Go 相反
        //   ⇒ A1 的净化层刻意**去掉 charset** ⇒ 回归成 UTF-8 ⇒ 与 Go 同形
        byte[] gbk = new byte[]{(byte) 0xD6, (byte) 0xD0, (byte) 0xCE, (byte) 0xC4};
        byte[] body = concat("{\"a\":\"".getBytes(StandardCharsets.UTF_8), gbk,
                "\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> readWithBytes("application/json; charset=gbk", body),
                "Go 的 encoding/json 只认 UTF-8 ⇒ 非 UTF-8 字节必须解析失败（RAG 同形）");
    }

    private static byte[] concat(byte[]... parts) {
        int len = 0;
        for (byte[] p : parts) {
            len += p.length;
        }
        byte[] out = new byte[len];
        int off = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, off, p.length);
            off += p.length;
        }
        return out;
    }

    // ==================== ③ 唯一真源与复制品的一致性 ====================

    @Test
    @DisplayName("③ goReadsBody 直接断言：大小写敏感边界")
    void goReadsBodyBoundaries() throws java.io.IOException {
        assertEquals(false, GoStyleJsonBodyConverter.goReadsBody("application/json", 0L),
                "Content-Length 为 0 ⇒ 不读");
        assertEquals(false, GoStyleJsonBodyConverter.goReadsBody("application/json", -1L),
                "chunked（-1）⇒ 不读（Go 的 r.ContentLength 对 chunked 就是 -1）");
        assertEquals(false, GoStyleJsonBodyConverter.goReadsBody(null, 10L),
                "缺 Content-Type ⇒ 不读");
        assertEquals(false, GoStyleJsonBodyConverter.goReadsBody("text/plain", 10L),
                "非 JSON ⇒ 不读");
        assertEquals(false, GoStyleJsonBodyConverter.goReadsBody("application/vnd.api+json", 10L),
                "⚠️ 字面子串匹配：+json 后缀**不含** application/json ⇒ 不读");
        assertEquals(true, GoStyleJsonBodyConverter.goReadsBody("application/json", 10L),
                "正常 JSON + 有体 ⇒ 读");
        assertEquals(true, GoStyleJsonBodyConverter.goReadsBody("application/json; charset=utf-8", 10L),
                "带 charset ⇒ 读（Go 也是 contains，不是精确相等）");
        // 🔴 T-1 新增边界：**大小写敏感**
        assertEquals(false, GoStyleJsonBodyConverter.goReadsBody("Application/JSON", 10L),
                "🔴 大小写敏感：Go 的 strings.Contains 不做归一 ⇒ 大写不读 body");
    }

    // ==================== 辅助 ====================

    /**
     * 用给定的原始 Content-Type 头走一遍<b>真</b> {@link GoStyleJsonBodyConverter#read}。
     *
     * @return 解析结果；判据不满足时为 {@code null}（= Go 的 {@code UnmarshalJsonMap(nil, v)}）
     */
    private static Object readWithContentType(String rawContentType) throws java.io.IOException {
        return readWithBytes(rawContentType, BODY.getBytes(StandardCharsets.UTF_8));
    }

    private static Object readWithBytes(String rawContentType, byte[] body) throws java.io.IOException {
        GoStyleJsonBodyConverter converter =
                new GoStyleJsonBodyConverter(JsonMapper.builder().build());
        return converter.read(DTO_TYPE, new SimpleJsonInputMessage(rawContentType, body), Map.of());
    }

    /**
     * 最小的真实 {@link HttpInputMessage} 实现 —— <b>刻意不用 Mockito</b>：
     * 头部用真 {@link HttpHeaders}（真解析），body 用真流。
     */
    private static final class SimpleJsonInputMessage implements HttpInputMessage {
        private final HttpHeaders headers = new HttpHeaders();
        private final byte[] body;

        SimpleJsonInputMessage(String rawContentType, byte[] body) {
            // ⚠️ 用 add 而非 setContentType —— setContentType 会走 MediaType 解析并**归一化**，
            //    那就等于把被测的「原始头值」提前改掉了。
            this.headers.add(HttpHeaders.CONTENT_TYPE, rawContentType);
            this.headers.setContentLength(body.length);
            this.body = body;
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() throws java.io.IOException {
            return new ByteArrayInputStream(body);
        }
    }
}
