package com.rag.hot.hot.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>真 Tomcat</b>下的 HTTP 边界取证 —— 一条测试类同时闭合三条「MockMvc 测不到」的未知项：
 *
 * <table border="1">
 *   <caption>本类覆盖的未知项</caption>
 *   <tr><th>编号</th><th>内容</th></tr>
 *   <tr>
 *     <td><b>U11</b></td>
 *     <td>Prometheus 实际导出形态（{@code _bucket{le=}} 边界序列 + {@code _total_total} 命名 + 全局标签）</td>
 *   </tr>
 *   <tr>
 *     <td><b>U6</b></td>
 *     <td>404 / 405 在<b>真 Tomcat</b>下的响应头与 body 是否逐字节同</td>
 *   </tr>
 *   <tr>
 *     <td><b>U8</b></td>
 *     <td>percent-encoding 在真 Tomcat 下的绑定值</td>
 *   </tr>
 *   <tr>
 *     <td><b>D21</b></td>
 *     <td>容器旋钮 {@code server.tomcat.relaxed-query-chars}：{@code ?page[]=5} 必须被放行</td>
 *   </tr>
 *   <tr>
 *     <td><b>D22</b></td>
 *     <td>容器旋钮 {@code server.tomcat.max-parameter-count}：form 值个数阈值必须等于 Go 的 2048</td>
 *   </tr>
 *   <tr>
 *     <td><b>D23</b></td>
 *     <td>容器旋钮 {@code server.max-http-request-header-size}：请求行上限必须等于 Go 的 1 MiB+4 KiB</td>
 *   </tr>
 * </table>
 *
 * <h2>为什么必须是<b>真 Tomcat</b>（MockMvc 测不到什么）</h2>
 * <ul>
 *   <li><b>U6</b>：{@code MockMvc} <b>不走真实容器</b> ⇒ 它对 404/405 的响应头是<b>自己造的</b>，
 *       而不是 Tomcat 实际发出的。本仓已经吃过一次这个亏
 *       （见 {@code docs/Pitfalls/}：MockMvc 全绿、真 Tomcat 丢 {@code Content-Type}）。</li>
 *   <li><b>U8</b>：{@code MockMvc} <b>不解码</b> query（{@code %2B5} 原样传入），
 *       而 Tomcat 会解码 ⇒ 两者的绑定值天然不同，只能在真容器上比。</li>
 *   <li><b>U11</b>：{@code /actuator/prometheus} 的输出由 <b>registry + 端点</b> 决定，
 *       手工 {@code scrape()} 只能验一半（绕过了 actuator 端点这一层）。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么用 JDK 自带 {@link HttpClient} 而不是 {@code TestRestTemplate}</h2>
 * <p>{@code TestRestTemplate} 在 <b>Boot 4 已被拆走</b>（{@code org.springframework.boot.test.web.client}
 * 包不存在，新模块名是 {@code spring-boot-resttestclient}）⇒ 用它就得给 pom 加一个新模块。
 * ⇒ 改用 <b>JDK 自带</b> {@link HttpClient}：<b>零新依赖</b>，而且能拿到<b>原始响应头</b>
 * （与 {@code curl -sv} 看到的是同一份），比 RestTemplate 更接近对拍场景。
 *
 * <h2>Go 出处</h2>
 * <ul>
 *   <li>{@code service/hot/api/internal/metrics/metrics.go:24-29} ——
 *       {@code APIRequestDuration} 是 {@code prometheus.HistogramVec}，
 *       {@code Buckets: prometheus.DefBuckets}，标签 {@code route}；</li>
 *   <li>{@code prometheus/client_golang@v1.21.1/prometheus/histogram.go:271} ——
 *       {@code DefBuckets = []float64{.005, .01, .025, .05, .1, .25, .5, 1, 2.5, 5, 10}}（<b>源码取值</b>）；</li>
 *   <li>{@code service/hot/api/internal/metrics/metrics.go:14-16} ——
 *       {@code Subsystem: "hot_api"} + {@code Name: "request_total"} ⇒ Prometheus 全名
 *       {@code hot_api_request_total_total}（client_golang <b>不去重</b> {@code _total}）；</li>
 *   <li>go-zero {@code router/patrouter.go:59-83} —— 404/405 直出、不进任何 handler；
 *       {@code net/http.NotFound} ⇒ {@code 404 page not found\n} + {@code text/plain} + {@code nosniff}。</li>
 * </ul>
 */
@SpringBootTest(
        classes = HotApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.hot-rpc.address=static://127.0.0.1:65535",
                "grpc.client.hot-rpc.negotiation-type=plaintext",
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                // ⚠️ 必须关掉 Nacos 自动注册 —— 否则 RANDOM_PORT（真起 web server）时
                //    Spring Cloud Alibaba 会在 web server 启动阶段注册 Nacos，
                //    而 127.0.0.1:1 连不上 ⇒ 注册抛异常 ⇒ **整个上下文起不来**
                //    （实测：Caused by ErrCode:-401, ErrMsg:Client not connected, current status:STARTING，
                //      at NacosServiceRegistry.register）。
                //    ⚠️ MOCK 环境不启动 web server ⇒ 现有测试没踩到这个坑。
                //    ⇒ 这条只是**测试配置**；生产配置下的真实行为单独立档（DRIFT-HOTAPI-018）。
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class HotApiRealTomcatGateTest {

    /**
     * Go {@code prometheus.DefBuckets}（{@code client_golang@v1.21.1/prometheus/histogram.go:271}）
     * 的<b>源码取值</b> —— 不是从 RAG 侧的 {@code GO_DEF_BUCKETS} 反抄的。
     */
    private static final double[] GO_DEF_BUCKETS = {
            .005, .01, .025, .05, .1, .25, .5, 1, 2.5, 5, 10
    };

    @LocalServerPort
    private int port;

    @MockitoBean
    private HotRpcClient hotRpcClient;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        stubHappyPath();
    }

    private void stubHappyPath() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("a1").setHotScore(4242))
                        .build());
        when(articleRpcClient.getPublicArticle(any(), anyLong())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return GetArticleResponse.newBuilder()
                    .setArticle(Article.newBuilder()
                            .setId(id)
                            .setTitle("title-" + id)
                            .setStatus(ArticleStatus.PUBLISHED)
                            .build())
                    .build();
        });
    }

    // ====================================================================================
    // U11 —— Prometheus 实际导出形态
    // ====================================================================================

    @Test
    @DisplayName("🔴 U11 —— /actuator/prometheus 的 _bucket{le=} 边界序列逐条等于 Go 的 prometheus.DefBuckets")
    void prometheusBucketBoundariesMatchGoDefBuckets() throws Exception {
        get("/hot/v1/articles?page=1&page_size=20");

        String scrape = get("/actuator/prometheus");
        assertThat(scrape).isNotNull();

        List<String> les = leValues(scrape, "hot_api_request_duration_seconds_bucket");
        assertThat(les)
                .as("bucket 边界数必须是 Go 的 DefBuckets(%d) + +Inf", GO_DEF_BUCKETS.length)
                .hasSize(GO_DEF_BUCKETS.length + 1);
        for (int i = 0; i < GO_DEF_BUCKETS.length; i++) {
            assertThat(Double.parseDouble(les.get(i)))
                    .as("第 %d 个 le 边界必须等于 Go 的 DefBuckets[%d]", i + 1, i)
                    .isEqualTo(GO_DEF_BUCKETS[i]);
        }
        assertThat(les.get(les.size() - 1)).as("最后一个 bucket 必须是 +Inf").isEqualTo("+Inf");
        // ⚠️ 反向断言：Micrometer 的 exponential percentile 边界（publishPercentileHistogram 带来的）
        //    **一个都不许出现** —— 它们是本条漂移的现场特征。
        assertThat(les).as("不得混入 Micrometer 的 exponential percentile 边界（0.001048576 等）")
                .doesNotContain("0.001048576", "0.001398101", "1.073741824", "10.021590356");
    }

    @Test
    @DisplayName("🔴 U11 —— Counter 的 Prometheus 名是 hot_api_request_total（两侧都**不**重复加 _total）")
    void counterNameKeepsGoSingleTotalSuffix() throws Exception {
        get("/hot/v1/articles?page=1&page_size=20");

        String scrape = get("/actuator/prometheus");
        assertThat(scrape).isNotNull();

        // Go：client_golang 的 CounterOpts.Name **原样**成为指标名 ⇒ hot_api_request_total
        // RAG：Micrometer 看到名字已以 _total 结尾 ⇒ 也不重复加 ⇒ hot_api_request_total
        assertThat(scrape).contains("hot_api_request_total{");
        // ⚠️ 断言「**不是**双 _total 形态」—— 若哪天 Micrometer 或 client_golang 改成去重/加后缀，这里会变红。
        assertThat(scrape).doesNotContain("hot_api_request_total_total{");
    }

    @Test
    @DisplayName("⚠️ U11 —— RAG 侧多了全局标签 application=\"hot-api\"（Go 的默认 registry 没有）")
    void globalApplicationTagIsRagOnly() throws Exception {
        get("/hot/v1/articles?page=1&page_size=20");

        String scrape = get("/actuator/prometheus");
        assertThat(scrape).isNotNull();

        // Go：prometheus.MustRegister 到默认 registry ⇒ **无任何全局标签**
        // RAG：management.metrics.tags.application=hot-api ⇒ 每个指标多一个 application 标签
        assertThat(scrape).contains("application=\"hot-api\"");
    }

    // ====================================================================================
    // U6 —— 真 Tomcat 下的 404 / 405 响应
    // ====================================================================================

    @Test
    @DisplayName("🔴 U6 —— 404 在真 Tomcat 下是裸响应 + text/plain + nosniff")
    void notFoundIsBareGoStyleResponse() throws Exception {
        HttpResponse<String> resp = getResp("/hot/v1/no-such-path");

        assertThat(resp.statusCode()).isEqualTo(404);
        assertThat(resp.body()).isEqualTo("404 page not found\n");
        assertThat(header(resp, "Content-Type")).as("Go 的 http.NotFound 是 text/plain")
                .startsWith("text/plain");
        assertThat(header(resp, "X-Content-Type-Options")).as("Go 的 http.Error 会加 nosniff")
                .isEqualTo("nosniff");
    }

    @Test
    @DisplayName("🔴 U6 —— 405 在真 Tomcat 下是裸状态码 + Allow，无 body")
    void methodNotAllowedIsBareStatusCode() throws Exception {
        HttpResponse<String> resp = post("/hot/v1/articles");

        assertThat(resp.statusCode()).isEqualTo(405);
        assertThat(resp.body()).as("Go 的 405 直出、无 body").isNullOrEmpty();
        assertThat(header(resp, "Allow")).contains("GET");
    }

    // ====================================================================================
    // U8 —— percent-encoding 在真 Tomcat 下的绑定值
    // ====================================================================================

    @Test
    @DisplayName("🔴 U8 —— 真 Tomcat 下 %2B5 解码成 '+5'，Go 的 strconv.ParseInt 也接受前导 '+' ⇒ 等价")
    void percentEncodedPlusIsDecodedAndAccepted() throws Exception {
        // Go：url.ParseQuery 解 %2B → '+' ⇒ strconv.ParseInt("+5", 10, 64) **接受**前导 '+' ⇒ page=5
        // ⚠️ 断言的是「不报错且 page=5」，不是「绑定了什么字符串」。
        HttpResponse<String> resp = getResp("/hot/v1/articles?page=%2B5");

        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("\"page\":5").doesNotContain("\"page\":0");
        // ⚠️ 关键：若真 Tomcat 下 %2B 被解成 ' '（空格），Integer.parseInt 会抛 ⇒ 这里必须是 5
        assertThat(resp.body()).doesNotContain("\"code\":5001");
    }

    // ====================================================================================
    // D21 / D22 / D23 —— 容器输入上限三旋钮（真 Tomcat 下的**运行时后果**）
    // ====================================================================================
    //
    // ⚠️ **为什么这三条不能用 JDK HttpClient**：`URI.create("...?page[]=5")` 会抛
    //    URISyntaxException（`[` `]` 在 RFC 3986 里只允许出现在 host 的 IPv6 字面量里），
    //    而这正是本条要测的输入 ⇒ 用 JDK HttpClient 等于**测不到被测对象**。
    //    ⇒ 改用**裸 socket**逐字节构造请求行（见 rawGet）。
    //
    // ⚠️ **为什么必须是「真 Tomcat」而不是 MockMvc**：三旋钮全是**容器层**配置，
    //    MockMvc 根本不经过容器 ⇒ 在 MockMvc 上这三条永远绿（假绿）。
    //    （这正是 D21 当初「第二轮 CASE-04b/04c 的 ✅ 结论被推翻」的原因。）

    /**
     * D21 —— {@code server.tomcat.relaxed-query-chars}。
     *
     * <p>Go 出处：{@code net/url.ParseRequestURI} **不校验 query**（实测 err=nil），
     * 且 go-zero {@code rest/httpx/util.go} 的 {@code GetFormValues} 明确支持
     * {@code ?page[]=5} 这种方括号数组记法 ⇒ Go 返回 200 且 {@code page=5}。
     * <p>Tomcat 默认**拒绝** query 里的 {@code [} {@code ]} ⇒ 400 + **HTML 错误页**
     * （不是业务信封）⇒ wire 形状完全不同。
     */
    @Test
    @DisplayName("🔴 D21 —— ?page[]=5 在真 Tomcat 下必须 200 且 page=5（Boot 默认会 400 + HTML）")
    void bracketArrayNotationIsAcceptedLikeGo() throws Exception {
        RawHttp resp = rawGet("/hot/v1/articles?page[]=5");

        assertThat(resp.status).as("Go 对 ?page[]=5 返回 200").isEqualTo(200);
        assertThat(resp.contentType).as("必须是业务信封的 JSON，不能是 Tomcat 的 HTML 错误页")
                .contains("application/json");
        assertThat(resp.body).as("Go 侧 page=5（方括号记法被正确绑定）").contains("\"page\":5");
        // ⚠️ 反向断言：Tomcat 的 HTML 错误页**绝不能**出现（它是本次漂移的现场特征）
        assertThat(resp.body).doesNotContain("<html").doesNotContain("<!doctype");
    }

    /**
     * D22 —— {@code server.tomcat.max-parameter-count}。
     *
     * <p>Go 出处：go-zero {@code rest/httpx/util.go:15} {@code maxFormParamCount = 2048}
     * ⇒ 第 **2049** 个**非空** form 值即整体报错（wire 上是 200 + {@code {code:5001}}）。
     * <p>Boot 4 的 {@code server.tomcat.max-parameter-count} 默认 **1000** ⇒ Tomcat 先拒
     * ⇒ 1001~2048 这段区间 Go 成功 / RAG 失败。
     */
    @Test
    @DisplayName("🔴 D22 —— form 值个数：2048 个通过、第 2049 个按 Go 语义被拒（200 + {code:5001}，不是 400 HTML）")
    void formValueCountLimitMatchesGo() throws Exception {
        RawHttp atLimit = rawGet("/hot/v1/articles?" + queryOf(2048));
        assertThat(atLimit.status).as("第 2048 个值仍应通过").isEqualTo(200);
        assertThat(atLimit.body).contains("\"code\":200");

        RawHttp overLimit = rawGet("/hot/v1/articles?" + queryOf(2049));
        assertThat(overLimit.status).as("Go 对第 2049 个值是 200 + 业务信封里的 5001")
                .isEqualTo(200);
        assertThat(overLimit.body).contains("\"code\":5001");
        // ⚠️ 关键：Tomcat 层拒绝会是 400 + text/html ⇒ 与 Go 的 wire 形状不同
        assertThat(overLimit.contentType).as("拒绝必须走业务信封，不能是 Tomcat 的 HTML 错误页")
                .contains("application/json");
        assertThat(overLimit.body).doesNotContain("<html");
    }

    /**
     * D23 —— {@code server.max-http-request-header-size}。
     *
     * <p>Go 出处：{@code net/http/server.go:931} {@code DefaultMaxHeaderBytes = 1 << 20}
     * ＋ {@code :941} {@code initialReadLimitSize() = maxHeaderBytes() + 4096 // bufio slop}
     * ⇒ Go 的**真实**上限是 **1 MiB + 4 KiB**（不是 1 MiB）。
     * <p>Boot 4 默认 **8KB** ⇒ 8 KiB~1 MiB 的请求行 Go 处理、RAG 400 + HTML。
     */
    @Test
    @DisplayName("🔴 D23 —— 请求行长度：20 KiB 必须放行（Boot 默认 8KB 会拒）、超 1 MiB+4 KiB 必须被拒")
    void requestLineLengthLimitMatchesGo() throws Exception {
        // ① 20 KiB：Boot 4 默认 8KB 会拒（400 + HTML）⇒ 这条断言专门抓「旋钮没生效」
        RawHttp ok = rawGet("/hot/v1/articles?a=" + "x".repeat(20 * 1024));
        assertThat(ok.status).as("Go 对 20 KiB 的 query 正常放行").isEqualTo(200);
        assertThat(ok.body).contains("\"code\":200");

        // ② 超过 Go 的真实上限（1 MiB + 4 KiB）⇒ 两侧都被拒
        RawHttp tooBig = rawGet("/hot/v1/articles?a=" + "x".repeat(1024 * 1024 + 4096));
        assertThat(tooBig.status).as("超过 Go 的 1 MiB+4 KiB 上限必须被拒").isNotEqualTo(200);
        // ⚠️ RAG 是 400 + Tomcat HTML，Go 是 431 + text/plain（"Request Header Fields Too Large"）
        //    —— 这是 Tomcat **连接器层**的拒绝，没有配置开关能改成 431 ⇒ 框架限制，
        //    已登记（docs/DRIFT/hot/api.md 的 DRIFT-HOTAPI-031）。
        //    这里断言 RAG 侧的**实际**形态，把差异钉死而不是让它悄悄漂。
        assertThat(tooBig.status).isEqualTo(400);
    }

    // ====================================================================================
    // helpers
    // ====================================================================================

    /**
     * 构造 {@code n} 个最省的 form 值（{@code a=1&a=1&…}）—— 与
     * {@code tools/difftest/probe-hot-limits.py} 的 {@code build_query_values} 同款，
     * 保证「探针量的阈值」与「测试断言的阈值」是同一个东西。
     */
    private static String queryOf(int n) {
        StringBuilder sb = new StringBuilder(n * 4);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("a=1");
        }
        return sb.toString();
    }

    /** 裸 socket 的 HTTP 响应（只取本测试需要的三个字段）。 */
    private static final class RawHttp {
        final int status;
        final String contentType;
        final String body;

        RawHttp(int status, String contentType, String body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }
    }

    /**
     * 用**裸 socket**发一个 GET —— 逐字节构造请求行，不经过 {@link java.net.URI} 的语法校验。
     *
     * <p>⚠️ 必须走 socket 而不是 {@link HttpClient}：{@code URI.create} 会拒绝
     * {@code ?page[]=5}（{@code [} {@code ]} 在 RFC 3986 里不属于 query 的合法字符），
     * 而那**正是** D21 要测的输入 ⇒ 用 HttpClient 等于把被测对象过滤掉了。
     */
    private RawHttp rawGet(String pathAndQuery) throws Exception {
        try (java.net.Socket socket = new java.net.Socket("localhost", port)) {
            socket.setSoTimeout(60_000);
            byte[] request = ("GET " + pathAndQuery + " HTTP/1.1\r\n"
                    + "Host: localhost:" + port + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
            socket.getOutputStream().write(request);
            socket.getOutputStream().flush();

            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            java.io.InputStream in = socket.getInputStream();
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            String raw = buf.toString(java.nio.charset.StandardCharsets.ISO_8859_1);

            int headEnd = raw.indexOf("\r\n\r\n");
            String head = headEnd < 0 ? raw : raw.substring(0, headEnd);
            String body = headEnd < 0 ? "" : raw.substring(headEnd + 4);

            int status = -1;
            String contentType = null;
            for (String line : head.split("\r\n")) {
                if (line.startsWith("HTTP/")) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        status = Integer.parseInt(parts[1]);
                    }
                } else if (line.regionMatches(true, 0, "Content-Type:", 0, 13)) {
                    contentType = line.substring(13).trim();
                }
            }
            return new RawHttp(status, contentType, body);
        }
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> getResp(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private String get(String path) throws Exception {
        return getResp(path).body();
    }

    private static String header(HttpResponse<String> resp, String name) {
        Optional<String> v = resp.headers().firstValue(name);
        return v.orElse(null);
    }

    /** 取某个 meter 的全部 {@code le=} 值（文本，含 "+Inf"）。 */
    private static List<String> leValues(String scrape, String metric) {
        List<String> out = new ArrayList<>();
        for (String line : scrape.split("\n")) {
            if (!line.startsWith(metric)) {
                continue;
            }
            int at = line.indexOf("le=\"");
            if (at < 0) {
                continue;
            }
            int end = line.indexOf('"', at + 4);
            out.add(line.substring(at + 4, end));
        }
        return out;
    }
}
