package com.rag.article.article.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 Go 的 query 参数总数上限（{@code maxFormParamCount = 2048}）—— <b>应用层闸门</b>的真容器证据。
 *
 * <h2>为什么这个类要单独开（而不是并进 {@code ArticleApiAssemblyWiringTest}）</h2>
 * <b>默认配置下这条闸门不可达</b>：Tomcat 的请求行/头部长度上限默认 8 KB，
 * 而 2049 个<b>最短</b>的非空 query 值（{@code a=1&} × 2049 ≈ 8196 字节）<b>本身就超了</b>
 * ⇒ Tomcat 在<b>连接器层</b>就以 400 拒掉，请求<b>根本到不了</b> servlet filter
 * （2026-10-24 真容器实测，见 {@code ArticleApiAssemblyWiringTest} 里的两条用例）。
 *
 * <p>⇒ 本类把 {@code server.max-http-header-size} <b>调高到 64 KB</b>，
 * 让请求能进到应用层 ⇒ 才能验证闸门本身的语义（Go 的 2048 判据 + Go 的错误 body）。
 * <br>⚠️ 这不是为了"让测试通过"，而是把<b>两件事分开测</b>：
 * <ol>
 *   <li>默认部署下的**端到端**结果（连接器层就拒了）⇒ 在装配层测试类里；</li>
 *   <li>应用层闸门的**语义**（Go 的判据与 body）⇒ 在本类。</li>
 * </ol>
 *
 * <h2>Go 出处（逐行，消费点）</h2>
 * <pre>
 * go-zero@v1.10.2/rest/httpx/util.go:11-17
 *   const (
 *       // most servers and clients have a limit of 8192 bytes (8 KB)
 *       // one parameter at least take 4 chars, for example `?a=b&amp;c=d`
 *       maxFormParamCount = 2048
 *   )
 *
 * go-zero@v1.10.2/rest/httpx/util.go:44-56
 *   for _, v := range values {
 *       if len(v) == 0 { continue }                          // ← 空值不计数
 *       if n &lt; maxFormParamCount { filtered = append(filtered, v); n++ }
 *       else { return nil, fmt.Errorf("too many form values, error: %s", r.Form.Encode()) }
 *   }
 * </pre>
 *
 * <p>⇒ 调用链：handler 的 {@code httpx.Parse} → {@code httpx.ParseForm} → {@code httpx.GetFormValues}
 * ⇒ 超限 ⇒ {@code httpx.ErrorCtx} → {@code httpx/responses.go:142-159} 的 {@code doHandleError}
 * （本模块未注册 error handler ⇒ {@code handler == nil}；该 error 非 gRPC error ⇒ 走 else 分支）
 * ⇒ {@code http.Error(w, err.Error(), 400)}
 * ⇒ <b>400</b> + text/plain + nosniff + body = {@code "too many form values, error: " + r.Form.Encode()} + {@code "\n"}。
 */
@SpringBootTest(
        classes = ArticleApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.user-rpc.negotiation-type=plaintext",
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.discovery.register-enabled=false",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "logging.level.com.alibaba.nacos=WARN",
                "management.otlp.tracing.export.enabled=false",
                // ⭐ 本类存在的**唯一**理由：把 Tomcat 的请求行/请求头上限抬到 64 KB，
                //    好让请求能进到应用层的闸门（默认 8 KB 时 Tomcat 先拒，闸门不可达）。
                // ⚠️ Boot 4 的属性名是 server.max-http-request-header-size（默认 8 KB）；
                //    旧的 server.max-http-header-size 在 Boot 4 **已不存在** ⇒ 写它会被**静默忽略**
                //    （实测：写了它，请求仍被 Tomcat 以 400 拒掉，闸门根本没执行）。
                "server.max-http-request-header-size=64KB"
        })
class ArticleApiFormParamLimitTest {

    @Value("${local.server.port}")
    private int port;

    /** ⚠️ 用 JDK 的 HttpClient，断言「客户端真正收到的那份字节」。 */
    private final HttpClient http = HttpClient.newHttpClient();

    private String base() {
        return "http://localhost:" + port;
    }

    // =====================================================================
    // 1. 超限 ⇒ 400 + Go 的 body（逐字）
    // =====================================================================

    @Test
    @DisplayName("🔴 Go: 2049 个非空 query 值 ⇒ **400** + body 逐字 = "
            + "\"too many form values, error: \" + r.Form.Encode()")
    void tooManyFormValuesIsGoShapedBadRequest() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + goQuery(2049)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode(),
                "Go 侧 httpx/util.go:48-53 超限 ⇒ http.Error(400)");
        // ⚠️ r.Form 是 {"a": [2049 个 "1"]} ⇒ Encode() = "a=1" 用 & 连 2049 次
        String expected = "too many form values, error: " + goQuery(2049) + "\n";
        assertEquals(expected, resp.body(),
                "body 必须逐字等于 Go 的 fmt.Errorf(\"too many form values, error: %s\", r.Form.Encode())"
                        + "（外加 http.Error 追加的 \\n）");
        // ⚠️ 实测是**无空格**版（Go 的 http.Error 写 "text/plain; charset=utf-8"）
        //    ⇒ 这是**已登记**的 LOW 漂移（Tomcat 规范化 Content-Type），不是本条引入的。
        assertEquals("text/plain;charset=utf-8",
                resp.headers().firstValue("Content-Type").orElse(null),
                "⚠️ 实测是**无空格**版（Go 的 http.Error 写 text/plain; charset=utf-8）—— 已登记的 LOW 漂移");
        assertEquals("nosniff",
                resp.headers().firstValue("X-Content-Type-Options").orElse(null),
                "Go 的 http.Error 会写 X-Content-Type-Options: nosniff");
    }

    // =====================================================================
    // 2. 边界：恰好 2048 ⇒ 不触发
    // =====================================================================

    @Test
    @DisplayName("Go: 恰好 2048 个非空 query 值 ⇒ **不**被这条闸门拒掉（判据是 n < 2048）")
    void exactlyMaxFormValuesIsAccepted() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + goQuery(2048)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertNotEquals(400, resp.statusCode(),
                "2048 个值**恰好在**上限内 ⇒ 不该被这条闸门拒掉"
                        + "（下游 gRPC 不可达导致的其他状态码是另一回事）；实测=" + resp.statusCode());
    }

    // =====================================================================
    // 3. 空值不计数
    // =====================================================================

    @Test
    @DisplayName("Go: 3000 个**空值** query 参数 ⇒ **不**触发（Go 的 len(v)==0 就 continue）")
    void emptyValuesAreNotCounted() throws Exception {
        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + emptyGoQuery(3000)))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertNotEquals(400, resp.statusCode(),
                "Go 只数非空值 ⇒ 空值再多也不该被拒；实测=" + resp.statusCode());
    }

    // =====================================================================
    // 4. r.Form.Encode() 的转义与排序必须与 Go 一致
    // =====================================================================

    /**
     * 🔴 Go 的 {@code url.Values.Encode()}：<b>按 key 排序</b> + {@code QueryEscape}。
     *
     * <p>逐条对齐：
     * <ul>
     *   <li>key <b>字节序</b>（= 码点序）⇒ {@code b} 在 {@code a} 之前；</li>
     *   <li>同名 key 的多个值<b>保持原序</b>；</li>
     *   <li>{@code QueryEscape}：{@code A-Za-z0-9-_.~} 不转义、空格 ⇒ {@code +}、其余 ⇒ {@code %XX}（大写）。
     *       ⚠️ <b>{@code *} 会被转义成 {@code %2A}</b> —— 它不在 Go 的 unreserved 集合
     *       （{@code - _ . ~} + 字母数字）里 ⇒ 实测 {@code url.QueryEscape("*") == "%2A"}。</li>
     * </ul>
     * ⚠️ 注意 {@code +}：{@code a+b} 解码成 {@code "a b"}，再编码回 {@code "a+b"}（不是 {@code "a%20b"}）。
     */
    @Test
    @DisplayName("Go: 超限时的 body 里 r.Form.Encode() 必须按 **key 字节序** + Go 的 QueryEscape")
    void encodedFormMatchesGoUrlValuesEncode() throws Exception {
        // 2049 个值：z 排在最后（key 字节序），b 与 a 各若干，另有需要转义的字符
        StringBuilder query = new StringBuilder();
        for (int i = 0; i < 2047; i++) {
            query.append("z=1&");
        }
        query.append("b=a+b&a=%E4%B8%AD&a=~&a=*");

        HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(base() + "/v1/articles?" + query))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(400, resp.statusCode());
        // Go 的 Encode()：key 排序后 a 先、b 次、z 最后；同名 key 的值保持原序
        // ⚠️ Go 探针实测（net/url，Go 1.25.11）：
        //   url.QueryEscape("*") == "%2A"、url.QueryEscape("~") == "~"、
        //   url.QueryEscape("中") == "%E4%B8%AD"、url.QueryEscape("a b") == "a+b"
        //   url.Values{a:[中,~,*], b:[a b]}.Encode() == "a=%E4%B8%AD&a=~&a=%2A&b=a+b"
        String zChain = "z=1&".repeat(2047);
        zChain = zChain.substring(0, zChain.length() - 1);   // 去掉末尾的 &
        String expectedEncoded = "a=%E4%B8%AD&a=~&a=%2A&b=a+b&" + zChain;
        assertEquals("too many form values, error: " + expectedEncoded + "\n", resp.body(),
                "r.Form.Encode() 的 key 排序 / 转义必须与 Go 的 url.Values.Encode() 逐字节一致");
    }

    // =====================================================================
    // 5. Go 的 net/url 三条相邻行为（UNRESOLVED-010）
    // =====================================================================

    /**
     * <h2>Go 出处（逐行，消费点）</h2>
     * <pre>
     * Go 标准库 net/url/url.go:1019-1045  parseQuery
     *   if strings.Contains(key, ";") {
     *       err = fmt.Errorf("invalid semicolon separator in query")
     *       continue
     *   }
     *   …
     *   key, err1 := QueryUnescape(key)          // 失败 ⇒ err = err1
     *   value, err1 := QueryUnescape(value)      // ⇒ "invalid URL escape \"%zz\""
     *   …
     *   if len(vs) &gt; maxParams {              // maxParams = defaultMaxParams = 10000
     *       err = errors.New("number of URL query parameters exceeded limit")
     *       break
     *   }
     * </pre>
     *
     * <p>⇒ 这些 error 由 {@code net/http.Request.ParseForm} 返回，
     * 而 go-zero 的 {@code httpx.GetFormValues}（{@code rest/httpx/util.go:25-27}）
     * <b>把它原样往上抛</b>（不是吞掉）：
     * <pre>
     *   if err := r.ParseForm(); err != nil {
     *       return nil, err
     *   }
     * </pre>
     * ⇒ {@code httpx.Parse} 返回 error ⇒ handler 调 {@code httpx.ErrorCtx}
     * ⇒ {@code httpx/responses.go:142-159}（未注册 error handler ⇒ 走 else 分支）
     * ⇒ {@code http.Error(w, err.Error(), 400)}
     * ⇒ <b>400</b> + text/plain + nosniff + body = {@code err.Error()} + {@code "\n"}。
     *
     * <p><b>Go 侧探针实测</b>（Go 1.25.11 + go-zero v1.10.2 的<b>真包</b> {@code httpx.Parse}）：
     * <pre>
     * ?a=1;b=2     ⇒ "invalid semicolon separator in query\n"
     * ?a=%zz       ⇒ "invalid URL escape \"%zz\"\n"
     * ?a=1& ×10001 ⇒ "number of URL query parameters exceeded limit\n"
     * </pre>
     */
    @Test
    @DisplayName("🔴 Go: query 里有 `;` ⇒ **400** + body 逐字 = \"invalid semicolon separator in query\\n\"")
    void semicolonInQueryIsRejectedLikeGo() throws Exception {
        RawResponse resp = rawGet("/v1/article/1?a=1;b=2");
        assertEquals(400, resp.status, "Go 对 `;` 返 400（net/url parseQuery），实际响应：\n" + resp);
        assertEquals("invalid semicolon separator in query\n", resp.body,
                "body 必须与 Go 逐字一致（Go 的 http.Error(w, err.Error(), 400)）");
    }

    @Test
    @DisplayName("🔴 Go: query 里有非法 `%` 转义 ⇒ **400** + body 逐字 = \"invalid URL escape \\\"%zz\\\"\\n\"")
    void invalidPercentEscapeInQueryIsRejectedLikeGo() throws Exception {
        RawResponse resp = rawGet("/v1/article/1?a=%zz");
        assertEquals(400, resp.status, "Go 对非法 % 转义返 400，实际响应：\n" + resp);
        assertEquals("invalid URL escape \"%zz\"\n", resp.body,
                "body 必须与 Go 逐字一致（Go 的 http.Error(w, err.Error(), 400)）");
    }

    /**
     * ⚠️ characterization（<b>不是</b> Go 对齐）：<b>请求行里有原始非 ASCII 字节</b> ⇒
     * <b>Tomcat 在连接器层就拒了</b>，请求<b>根本到不了应用层</b>。
     *
     * <p>Go 侧（真包 {@code httpx.Parse}，探针实测 {@code ?a=%中}）：
     * <pre>
     * net/url.ParseQuery  err=invalid URL escape "%\xe4\xb8"   ← 按**字节**切片 ⇒ 截断了「中」
     * ⇒ 客户端看到的 body = "invalid URL escape \"%\\xe4\\xb8\"\n"
     * </pre>
     * ⇒ <b>状态码两侧都是 400</b>（一致）⇒ 差异只剩 <b>body 形状</b>：
     * Go 是 text/plain 的那句文案，RAG 是 Tomcat 的 HTML 错误页。
     *
     * <p>⚠️ <b>为什么这条不修</b>：拒它的是 <b>Tomcat 的 URI 校验</b>（连接器层），
     * <b>应用层 filter 无从插手</b> ⇒ 与 {@code DRIFT-005} 的残留同一类
     * （Tomcat 的 HTML 错误页 vs Go 的 text/plain）⇒ <b>LOW，决定不修</b>。
     * 本用例锁住现状，将来有人调 Tomcat 的 URI 校验时能立刻看到差异。
     */
    @Test
    @DisplayName("⚠️ characterization: `%` 后面接**多字节字符** ⇒ Tomcat 在**连接器层**就 400（HTML 错误页）—— 应用层不可达")
    void invalidEscapeFollowedByMultiByteCharIsRejectedAtConnectorLayer() throws Exception {
        RawResponse resp = rawGet("/v1/article/1?a=%中");
        assertEquals(400, resp.status, "Tomcat 连接器层就拒了（与 Go 的 400 一致）");
        assertTrue(resp.body.startsWith("<!doctype html>"),
                "body 是 Tomcat 的 HTML 错误页（连接器层拒绝）⇒ 与 Go 的 text/plain 文案不同，"
                        + "但**状态码一致** ⇒ LOW，不修；实际 body：" + resp.body);
    }

    /**
     * ⚠️ characterization：<b>query 里有原始非 ASCII（合法 UTF-8、无 {@code %}）</b>。
     *
     * <p>Go 侧（真包 {@code httpx.Parse}，探针实测 {@code ?a=中}）：{@code err=<nil>}
     * ⇒ <b>Go 接受</b>（200）。
     * <br>RAG 侧：<b>Tomcat 在连接器层就 400</b>（HTML 错误页）⇒ <b>请求到不了应用层</b>。
     *
     * <p>⚠️ <b>为什么这条不修</b>：同上一条 —— 拒它的是 <b>Tomcat 的 URI 校验</b>，
     * 应用层无从插手 ⇒ LOW。而且<b>正常客户端不会这样发</b>：
     * 实测 {@code ?a=%E4%B8%AD}（合规的百分号编码）⇒ 两侧都 200。
     */
    @Test
    @DisplayName("⚠️ characterization: query 里有原始非 ASCII ⇒ Tomcat 连接器层 400（Go 侧接受）—— 应用层不可达，不修")
    void rawNonAsciiInQueryIsRejectedAtConnectorLayer() throws Exception {
        RawResponse resp = rawGet("/v1/article/1?a=中");
        assertEquals(400, resp.status, "Tomcat 连接器层就拒了（Go 侧是接受的 ⇒ 差异只在这条畸形输入上）");
        assertTrue(resp.body.startsWith("<!doctype html>"),
                "body 是 Tomcat 的 HTML 错误页 ⇒ LOW，不修；实际 body：" + resp.body);

        // 对照：<b>合规</b>的百分号编码 ⇒ 两侧都 200（这条是「正常客户端不会那样发」的证据）
        RawResponse ok = rawGet("/v1/article/1?a=%E4%B8%AD");
        assertEquals(200, ok.status, "合规的百分号编码必须正常通过（Tomcat 与 Go 都接受）");
    }

    @Test
    @DisplayName("🔴 Go: query 超过 10000 个参数 ⇒ **400** + body 逐字 = \"number of URL query parameters exceeded limit\\n\"")
    void tooManyQuerySegmentsIsRejectedLikeGo() throws Exception {
        RawResponse resp = rawGet("/v1/article/1?" + goQuery(10001));
        assertEquals(400, resp.status, "Go 对 >10000 个 query 参数返 400，实际响应：\n" + resp);
        assertEquals("number of URL query parameters exceeded limit\n", resp.body,
                "body 必须与 Go 逐字一致（Go 的 http.Error(w, err.Error(), 400)）");
    }

    // =====================================================================
    // helpers
    // =====================================================================

    /** 裸 socket 收到的那份响应（状态码 + body）。 */
    private record RawResponse(int status, String body, String raw) {
        @Override
        public String toString() {
            return "status=" + status + " body=" + body;
        }
    }

    /**
     * 用<b>裸 socket</b>发请求 —— JDK 的 {@code URI}/{@code HttpClient} <b>自己就会拒绝</b>
     * 非法 {@code %} 转义（{@code Malformed escape pair}），根本发不出去 ⇒ 测不到那条路径。
     *
     * <p>⚠️ 请求行必须按 <b>UTF-8</b> 编码：query 里的非 ASCII 字符若按 ISO-8859-1 编码，
     * Java 会把它替换成 {@code ?}（不可映射字符）⇒ 服务端收到的是**另一个** query，
     * 测的就不是那条路径了（实测踩过：{@code ?a=%中} 被发成 {@code ?a=%?}）。
     */
    private RawResponse rawGet(String rawTarget) throws Exception {
        try (java.net.Socket s = new java.net.Socket("localhost", port)) {
            s.setSoTimeout(15_000);
            String request = "GET " + rawTarget + " HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Connection: close\r\n\r\n";
            s.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            s.getOutputStream().flush();
            byte[] buf = s.getInputStream().readAllBytes();
            String raw = new String(buf, java.nio.charset.StandardCharsets.ISO_8859_1);
            int sep = raw.indexOf("\r\n\r\n");
            String head = sep < 0 ? raw : raw.substring(0, sep);
            String body = sep < 0 ? "" : raw.substring(sep + 4);
            int sp1 = head.indexOf(' ');
            int sp2 = head.indexOf(' ', sp1 + 1);
            int status = (sp1 < 0 || sp2 < 0) ? -1 : Integer.parseInt(head.substring(sp1 + 1, sp2).trim());
            return new RawResponse(status, body, raw);
        }
    }

    /** 造 N 个非空 query 值（{@code a=1&a=1&…} ⇒ Go 的 r.Form = {"a": [N 个 "1"]}）。 */
    private static String goQuery(int n) {
        StringBuilder sb = new StringBuilder(n * 4);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("a=1");
        }
        return sb.toString();
    }

    /** 造 N 个<b>空值</b> query 参数（{@code a&a&…} ⇒ Go 的 value 为空 ⇒ 不计数）。 */
    private static String emptyGoQuery(int n) {
        StringBuilder sb = new StringBuilder(n * 2);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append('a');
        }
        return sb.toString();
    }
}
