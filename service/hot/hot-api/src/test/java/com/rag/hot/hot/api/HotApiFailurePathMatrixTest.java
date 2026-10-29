package com.rag.hot.hot.api;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.api.support.RealTomcatPort;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import io.grpc.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * hot-api 的<b>失败路径矩阵</b>（真 Tomcat · 1 端点 × 6 类失败维度）。
 *
 * <h2>为什么要有它（基线缺口 G-13 · P0 #2）</h2>
 * <p>「存在失败路径处理」推不出「参数校验 / 业务规则 / 认证 / 权限 / 下游不可用 各项都有效」
 * ⇒ 本类把 hot-api 唯一的端点 {@code GET /hot/v1/articles} 按<b>失败维度</b>铺开，
 * 每条断言都落到<b>具体的错误码 / HTTP 状态码</b>。
 *
 * <h2>为什么必须是<b>真 Tomcat</b>（不是 MockMvc）</h2>
 * <p>MockMvc 有三处系统性地测不到：
 * <ol>
 *   <li><b>Content-Type 的容器归一化</b> —— 真 Tomcat 把 {@code text/plain; charset=utf-8}
 *       归一化成 {@code text/plain;charset=utf-8}（分号后无空格）；</li>
 *   <li><b>Content-Length</b> —— 由容器依据实际写入字节数计算，MockMvc 不参与；</li>
 *   <li><b>body 是否被容器补写</b> —— 405 这类「body 为 null」的响应，
 *       容器会不会自作主张补空 body 或补 Content-Type，只有真容器说了算。</li>
 * </ol>
 * ⇒ 本类用 {@code DEFINED_PORT} + <b>本段专用端口段</b>（{@link RealTomcatPort}）
 * + JDK HttpClient 读<b>原始</b>响应字节。
 *
 * <h2>🔴 hot-api 与 article-api 的<b>决定性不对称</b></h2>
 * <p>同一个「下游失败」概念，两域的 wire 形状<b>完全不同</b>：
 * <table border="1">
 *   <caption>两域的失败形状</caption>
 *   <tr><th>场景</th><th>article-api</th><th>hot-api</th></tr>
 *   <tr>
 *     <td>解析层失败</td>
 *     <td><b>400 + text/plain + nosniff</b>（Go <b>没有</b>注册 SetErrorHandler）</td>
 *     <td><b>200 + {code:5001}</b>（Go <b>注册了</b> SetErrorHandler）</td>
 *   </tr>
 *   <tr>
 *     <td>下游 fatal</td>
 *     <td>200 + {code:1015} / {code:5001}</td>
 *     <td>200 + {code:5001, msg:"internal server error"}</td>
 *   </tr>
 * </table>
 * ⇒ <b>不要照抄 article 的判据</b>（AGENTS.md §3.1「不要拿别的域的实测结论当对照基准」）。
 *
 * <h2>🔴 hot 域最核心的一条：<b>fatal 与 best-effort 的不对称</b></h2>
 * <p>同一个「下游失败」概念，两层方向<b>相反</b>：
 * <table border="1">
 *   <caption>两层失败语义（不可统一）</caption>
 *   <tr><th>层</th><th>Go 分支</th><th>结果</th><th>HTTP</th></tr>
 *   <tr>
 *     <td>hot-rpc</td>
 *     <td>{@code getHotArticlesLogic.go:65-75}</td>
 *     <td><b>fatal</b>：整包 5001</td><td>200 + {@code {code:5001,data:null}}</td>
 *   </tr>
 *   <tr>
 *     <td>article-rpc</td>
 *     <td>{@code getHotArticlesLogic.go:137-154}（在 Hydrator 里）</td>
 *     <td><b>best-effort</b>：跳过该条，其余照常返回</td><td>200 + 正常信封</td>
 *   </tr>
 * </table>
 * 把二者统一（例如都抛异常）会让「article-rpc 抖动一次」从「少一条」变成「整包失败」——
 * 那是<b>可用性</b>级别的漂移，不只是错误码。
 *
 * <h2>⚠️ 证据强度</h2>
 * <p>本类的期望值<b>全部抄自 Go 源码</b>（每条断言带 Go {@code 文件:行号}）
 * ⇒ 这是 <b>RAG 侧的自动化断言</b>；本类<b>不</b>声称双侧活体对拍
 * ⇒ 证据强度按基线 §1.1 的 <b>R3</b>（有 Go 出处 + 有 RAG 侧自动化断言 + 变异必红）计。
 */
@SpringBootTest(
        classes = HotApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                // ⚠️ 必须关掉 Nacos 自动注册 —— DEFINED_PORT 真的会启动 webServer ⇒
                //   Spring Cloud Alibaba 会在 web server 启动阶段注册 Nacos，
                //   而 127.0.0.1:1 连不上 ⇒ 注册抛异常 ⇒ **整个上下文起不来**（实测）。
                "spring.cloud.service-registry.auto-registration.enabled=false",
                "grpc.client.hot-rpc.address=static://127.0.0.1:65535",
                "grpc.client.hot-rpc.negotiation-type=plaintext",
                "grpc.client.article-rpc.address=static://127.0.0.1:65535",
                "grpc.client.article-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ 让判据只取决于「本类要验的那条路径」：CPU 自适应降载命中时
                //   GoStyleCpuSheddingFilter 走 response.setStatus(503) 并**直接 return**
                //   ⇒ 裸 503、无 body、无信封 ⇒ 把本类要验的判据抢先盖掉。
                "rag.protection.cpu-shedding.enabled=false"
        })
class HotApiFailurePathMatrixTest {

    /** Go {@code hot/common/errmsg.go} 的 {@code ErrorServerCommon}。 */
    private static final int CODE_SERVER_COMMON = 5001;
    /** Go {@code hot/common/errmsg.go} 的 {@code Success}。 */
    private static final int CODE_SUCCESS = 200;

    @LocalServerPort
    private int port;

    @MockitoBean
    private HotRpcClient hotRpcClient;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    @BeforeEach
    void setUp() {
        Mockito.reset(hotRpcClient, articleRpcClient);
    }

    /**
     * <b>本类必须绑到本段专用端口段</b>（而不是 Spring 的 {@code RANDOM_PORT}）。
     *
     * <p>⚠️ {@code RANDOM_PORT} 挑的是 OS <b>临时端口段</b>（macOS：49152–65535），
     * 而<b>别的会话的 Docker 容器正好在那一段发布端口</b>
     * ⇒ 客户端可能把请求打到别人的服务 ⇒ <b>零字节即关</b>
     * ⇒ <b>看着像被测代码炸了，其实压根没打到</b>，而且<b>所有用例照样绿</b>。
     */
    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    // ==================================================================
    // 0. 端口段自检（本类不许退化成 RANDOM_PORT）
    // ==================================================================

    @Test
    @DisplayName("真容器必须绑到本域专用端口段 18146-18150（RANDOM_PORT 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段）")
    void bindsToDedicatedPortSegment() {
        assertTrue(port >= RealTomcatPort.FIRST && port <= RealTomcatPort.LAST,
                "Tomcat 实际绑定的端口必须在 " + RealTomcatPort.FIRST + "-" + RealTomcatPort.LAST
                        + " 内，实际=" + port
                        + "（若落在 49152-65535，说明 webEnvironment 被改回 RANDOM_PORT 了）");
    }

    // ==================================================================
    // 1. 认证维度（hot-api **零鉴权**）
    // ==================================================================

    @ParameterizedTest(name = "[认证·{0}]")
    @ValueSource(strings = {"<无 Authorization 头>", "<乱 token>"})
    @DisplayName("认证·无 token / 乱 token ⇒ **进业务**（Go routes.go:16-25 只有 WithPrefix，**没有** WithJwt）")
    void noAuthIsRequiredAtAll(String variant) throws Exception {
        stubHappyPath();

        HttpResponse<String> r = variant.contains("乱")
                ? get("/hot/v1/articles", "Bearer not-a-real-token")
                : get("/hot/v1/articles", null);

        assertTrue(r.statusCode() != 401 && r.statusCode() != 403,
                "Go: hot api 的 AddRoutes 只有 rest.WithPrefix ⇒ **零鉴权** ⇒ 不得 401/403，实际=" + r.statusCode());
        assertEquals(200, r.statusCode());
        assertSuccessEnvelope(r);
    }

    // ==================================================================
    // 2. 参数校验维度
    // ==================================================================

    @ParameterizedTest(name = "[参数校验·{0}]")
    @ValueSource(strings = {"page=abc", "page_size=abc", "page=1&page_size=xyz"})
    @DisplayName("参数校验·query 值不是数字 ⇒ **200 + {code:5001,msg:\"internal server error\"}**（不是 400）")
    void unparsableQueryIs200With5001(String query) throws Exception {
        // Go: getHotArticlesHandler.go:18-20 ⇒ httpx.Parse 失败 ⇒ httpx.ErrorCtx
        //   ⇒ hotcenter.go:53-66 注册了 SetErrorHandler ⇒ 返回 http.StatusOK + {code:5001}
        // ⇒ 关键：**HTTP 恒 200**，错误码在 body 里（article 那边是 400 text/plain）
        HttpResponse<String> r = get("/hot/v1/articles?" + query);

        assertEquals(200, r.statusCode(),
                "Go: SetErrorHandler 的返回值字面量就是 http.StatusOK ⇒ 解析失败也是 200");
        assertErrorEnvelope(r, CODE_SERVER_COMMON, "internal server error");
        verify(hotRpcClient, never()).getHotArticles(anyInt(), anyLong());
    }

    // ==================================================================
    // 3. 边界值维度
    // ==================================================================

    @ParameterizedTest(name = "[边界·{0}]")
    @ValueSource(strings = {"page=0", "page=-1", "page=0&page_size=0", "page=-5&page_size=-5"})
    @DisplayName("边界·page / page_size ≤ 0 ⇒ **回落默认 1 / 20**（Go normalizePagination:172-190）")
    void nonPositivePaginationFallsBackToDefault(String query) throws Exception {
        stubHappyPath();

        HttpResponse<String> r = get("/hot/v1/articles?" + query);

        assertEquals(200, r.statusCode());
        assertSuccessEnvelope(r);
        assertTrue(r.body().contains("\"page\":1"),
                "Go: getHotArticlesLogic.go:177-179 `if req.Page > 0` ⇒ page≤0 ⇒ defaultPage=1，实际 body=" + r.body());
        assertTrue(r.body().contains("\"page_size\":20"),
                "Go: getHotArticlesLogic.go:180-182 `if req.PageSize > 0` ⇒ pageSize≤0 ⇒ defaultPageSize=20，实际 body=" + r.body());
    }

    @Test
    @DisplayName("边界·page_size > 50 ⇒ **夹取到 50**（Go maxPageSize=50，getHotArticlesLogic.go:27,184-188）")
    void pageSizeIsClampedTo50() throws Exception {
        stubHappyPath();

        HttpResponse<String> r = get("/hot/v1/articles?page_size=9999");

        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"page_size\":50"),
                "Go: getHotArticlesLogic.go:184-188 ⇒ pageSize>maxPageSize(50) ⇒ 夹取成 50，实际 body=" + r.body());
    }

    @Test
    @DisplayName("边界·page 极大 ⇒ int32 乘法溢出 ⇒ **空页**（不是报错、也不是超长 offset）")
    void int32OverflowYieldsEmptyPage() throws Exception {
        stubHappyPath();

        // Go: getHotArticlesLogic.go:197 `start := int((page - 1) * pageSize)`
        //   ⇒ (1000000-1)*50 在 int32 里溢出成负 ⇒ start<0 ⇒ 返回 [0,0] ⇒ 空页
        HttpResponse<String> r = get("/hot/v1/articles?page=1000000&page_size=50");

        assertEquals(200, r.statusCode(), "Go: 溢出落在 paginate 的 start<0 分支 ⇒ **不是错误**");
        assertSuccessEnvelope(r);
        assertTrue(r.body().contains("\"items\":[]"),
                "Go: paginate 返回 [0,0] ⇒ pagedItems 为空，实际 body=" + r.body());
    }

    @Test
    @DisplayName("边界·同名多值 query 取**第一个**（Go util.go:35-62 + unmarshaler.go:835-846），不是 400")
    void duplicatedQueryKeyTakesFirstValue() throws Exception {
        stubHappyPath();

        HttpResponse<String> r = get("/hot/v1/articles?page=3&page=9");

        assertEquals(200, r.statusCode(),
                "Go: 同名多值取第 0 个 ⇒ page=3 ⇒ 不是 400（Go 侧 httpx.Parse 成功）");
        assertTrue(r.body().contains("\"page\":3"),
                "Go: core/mapping/unmarshaler.go:835-846 fromArray ⇒ 取 Index(0)，实际 body=" + r.body());
    }

    // ==================================================================
    // 4. 下游不可用维度（fatal）
    // ==================================================================

    @Test
    @DisplayName("下游·hot-rpc UNAVAILABLE ⇒ **fatal** ⇒ 200 + {code:5001}（Go getHotArticlesLogic.go:65-69）")
    void hotRpcUnavailableIsFatal() throws Exception {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong()))
                .thenThrow(Status.UNAVAILABLE.withDescription("transport is closing").asRuntimeException());

        HttpResponse<String> r = get("/hot/v1/articles");

        assertEquals(200, r.statusCode(), "Go: fatal 分支也走 SetErrorHandler ⇒ HTTP 200");
        assertErrorEnvelope(r, CODE_SERVER_COMMON, "internal server error");
    }

    @Test
    @DisplayName("下游·hot-rpc 返回 **null** ⇒ 也是 fatal ⇒ 200 + {code:5001}（Go getHotArticlesLogic.go:70-75）")
    void hotRpcNilResponseIsFatal() throws Exception {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(null);

        HttpResponse<String> r = get("/hot/v1/articles");

        assertEquals(200, r.statusCode());
        assertErrorEnvelope(r, CODE_SERVER_COMMON, "internal server error");
    }

    // ==================================================================
    // 5. 下游补全失败维度（best-effort ⇒ 跳过该条，**不**整包失败）
    // ==================================================================

    @Test
    @DisplayName("下游·article-rpc 失败 ⇒ **只跳过该条**（best-effort），其余照常返回 200（Go :137-142）")
    void articleRpcFailureSkipsOnlyThatItem() throws Exception {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("a1").setHotScore(90))
                        .addItems(HotArticleItem.newBuilder().setArticleId("a2").setHotScore(80))
                        .build());
        when(articleRpcClient.getPublicArticle(anyString(), anyLong())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if ("a1".equals(id)) {
                throw Status.UNAVAILABLE.withDescription("article-rpc down").asRuntimeException();
            }
            return GetArticleResponse.newBuilder()
                    .setArticle(Article.newBuilder()
                            .setId(id).setTitle("t-" + id).setStatus(ArticleStatus.PUBLISHED))
                    .build();
        });

        HttpResponse<String> r = get("/hot/v1/articles");

        assertEquals(200, r.statusCode(),
                "Go: article-rpc 失败是 best-effort（skip）⇒ **不得**整包 5001");
        assertSuccessEnvelope(r);
        assertTrue(r.body().contains("\"total\":1"),
                "Go: total 是**过滤后**的窗口大小（:78 len(items)）⇒ 被 skip 的那条不计入，实际 body=" + r.body());
        assertTrue(r.body().contains("\"article_id\":\"a2\""),
                "Go: 保序过滤后剩下的应是 a2，实际 body=" + r.body());
        assertTrue(!r.body().contains("\"article_id\":\"a1\""),
                "Go: 补全失败的条目必须被 skip（nil），实际 body=" + r.body());
    }

    @Test
    @DisplayName("下游·article 未发布 / 缺失 ⇒ 同样**只跳过**（Go :143-154 的三条 skip 分支）")
    void unpublishedOrMissingArticleIsSkipped() throws Exception {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("draft").setHotScore(90))
                        .addItems(HotArticleItem.newBuilder().setArticleId("missing").setHotScore(80))
                        .addItems(HotArticleItem.newBuilder().setArticleId("ok").setHotScore(70))
                        .build());
        when(articleRpcClient.getPublicArticle(anyString(), anyLong())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if ("draft".equals(id)) {
                // Go: :150-154 ⇒ Status != PUBLISHED ⇒ skip
                return GetArticleResponse.newBuilder()
                        .setArticle(Article.newBuilder().setId(id).setStatus(ArticleStatus.DRAFT))
                        .build();
            }
            if ("missing".equals(id)) {
                // Go: :143-147 ⇒ articleResp.Article == nil ⇒ skip
                return GetArticleResponse.newBuilder().build();
            }
            return GetArticleResponse.newBuilder()
                    .setArticle(Article.newBuilder()
                            .setId(id).setTitle("t").setStatus(ArticleStatus.PUBLISHED))
                    .build();
        });

        HttpResponse<String> r = get("/hot/v1/articles");

        assertEquals(200, r.statusCode());
        assertSuccessEnvelope(r);
        assertTrue(r.body().contains("\"total\":1"),
                "Go: draft / missing 两条都被 skip ⇒ total 只剩 1，实际 body=" + r.body());
        assertTrue(r.body().contains("\"article_id\":\"ok\""),
                "Go: 保序过滤后剩下的应是 ok，实际 body=" + r.body());
    }

    @Test
    @DisplayName("下游·hot 项的 article_id 为空 ⇒ **跳过**（Go :127-130 skipReasonEmptyID）")
    void emptyArticleIdIsSkipped() throws Exception {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("").setHotScore(90))
                        .build());

        HttpResponse<String> r = get("/hot/v1/articles");

        assertEquals(200, r.statusCode());
        assertSuccessEnvelope(r);
        assertTrue(r.body().contains("\"total\":0"),
                "Go: 空 article_id ⇒ skip ⇒ total=0，实际 body=" + r.body());
        verify(articleRpcClient, never()).getPublicArticle(anyString(), anyLong());
    }

    // ==================================================================
    // 6. 路由层维度（404 / 405）
    // ==================================================================

    @Test
    @DisplayName("路由·未注册路径 ⇒ 404 + \"404 page not found\\n\" + text/plain + nosniff（Go patrouter.go:73）")
    void unregisteredPathIsGoNotFound() throws Exception {
        HttpResponse<String> r = get("/hot/v1/no-such-endpoint", null);

        assertEquals(404, r.statusCode());
        assertEquals("404 page not found\n", r.body(),
                "Go 的 http.Error + fmt.Fprintln ⇒ body 带尾随换行");
        assertTextPlainWithNosniff(r);
    }

    @Test
    @DisplayName("路由·方法不匹配 ⇒ **405** + Allow: GET + 空 body（Go patrouter.go:79-82）")
    void methodNotAllowedIs405WithAllowHeader() throws Exception {
        // ⚠️ hot api **没有** rest.WithCors()（Go hotcenter.go:41 `rest.MustNewServer(c.RestConf)`）
        //    ⇒ notAllowed 不被 CORS 接管 ⇒ 走 go-zero 原生的 Allow + 405
        // ⇒ 与 article-api（唯一用 WithCors 的那个 ⇒ 404 空 body）**形状相反**
        HttpResponse<String> r = post("/hot/v1/articles");

        assertEquals(405, r.statusCode());
        assertEquals("", r.body(), "Go 侧只有 WriteHeader、没有 Fprintln ⇒ body 必须为空");
        assertNull(r.headers().firstValue("Content-Type").orElse(null),
                "Go 侧 405 从未设置 Content-Type；body 为空时容器也不应补");
        assertTrue(r.headers().firstValue("Allow").orElse("").contains("GET"),
                "Go: patrouter.go:79-82 会写 Allow 头");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 正常路径：hot-rpc 给两条，article-rpc 都返回已发布文章。 */
    private void stubHappyPath() {
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("a1").setHotScore(90))
                        .addItems(HotArticleItem.newBuilder().setArticleId("a2").setHotScore(80))
                        .build());
        when(articleRpcClient.getPublicArticle(anyString(), anyLong())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return GetArticleResponse.newBuilder()
                    .setArticle(Article.newBuilder()
                            .setId(id).setTitle("t-" + id).setStatus(ArticleStatus.PUBLISHED))
                    .build();
        });
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null);
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).GET();
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        return client().send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        return client().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpClient client() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    /**
     * 断言<b>错误</b>信封 —— 顶层 code / msg / data。
     *
     * <p>⚠️ 「顶层 code 必须等于期望值」这一条同时守住了 <b>DRIFT-FRAMEWORK-001</b>：
     * 若 envelope 被多包一层，顶层 code 会变成 200。
     */
    private static void assertErrorEnvelope(HttpResponse<String> r, int expectedCode, String expectedMsg) {
        assertEquals(200, r.statusCode(), "Go: SetErrorHandler 恒返回 http.StatusOK");
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                "Go 的 httpx.WriteJson 写 Content-Type: application/json，实际 = "
                        + r.headers().firstValue("Content-Type").orElse("<无>"));
        String body = r.body();
        assertTrue(body.contains("\"code\":" + expectedCode),
                "信封的 code 必须是 " + expectedCode + "（顶层！被多包一层就会变成 200），实际 body = " + body);
        assertTrue(body.contains("\"msg\":\"" + expectedMsg + "\""),
                "信封的 msg 必须是 \"" + expectedMsg + "\"（Go hotcommon.GetErrMsg(5001)），实际 body = " + body);
        assertTrue(body.contains("\"data\":null"),
                "Go: SetErrorHandler 的响应是 &response.Response{Data: nil} ⇒ data 恒 null，实际 body = " + body);
    }

    /** 断言<b>成功</b>信封 —— 顶层 code/msg 为 200/OK。 */
    private static void assertSuccessEnvelope(HttpResponse<String> r) {
        assertEquals(200, r.statusCode());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                "Go 的 httpx.WriteJson 写 Content-Type: application/json");
        String body = r.body();
        assertTrue(body.contains("\"code\":" + CODE_SUCCESS),
                "成功信封的顶层 code 必须是 200，实际 body = " + body);
        assertTrue(body.contains("\"msg\":\"OK\""),
                "Go: hotcommon.GetErrMsg(Success) = \"OK\"，实际 body = " + body);
    }

    /**
     * 断言「Go 的 {@code http.Error} 形状」—— text/plain + nosniff。
     *
     * <p>⚠️ 比<b>解析后</b>的值：真 Tomcat 把 {@code "text/plain; charset=utf-8"} 归一化成
     * {@code "text/plain;charset=utf-8"}（分号后无空格）⇒ 按 RFC 9110 两者语义相同。
     */
    private static void assertTextPlainWithNosniff(HttpResponse<String> r) {
        String ct = r.headers().firstValue("Content-Type").orElse(null);
        assertNotNull(ct, "Go 的 http.Error 无条件写 Content-Type: text/plain; charset=utf-8");
        assertEquals("text/plain;charset=utf-8", ct.replace(" ", ""),
                "Go 的 http.Error 写死 text/plain + charset=utf-8；charset 整个丢掉或退化成 text/html 都是回归");
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").orElse(null),
                "net/http.Error 无条件写 X-Content-Type-Options: nosniff");
    }
}
