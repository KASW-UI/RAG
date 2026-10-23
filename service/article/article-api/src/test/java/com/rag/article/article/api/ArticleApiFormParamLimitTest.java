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
    // helpers
    // =====================================================================

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
