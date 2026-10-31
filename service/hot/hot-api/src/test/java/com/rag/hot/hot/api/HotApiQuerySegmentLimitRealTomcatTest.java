package com.rag.hot.hot.api;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.hot.hot.api.client.ArticleRpcClient;
import com.rag.hot.hot.api.client.HotRpcClient;
import com.rag.hot.hot.api.support.RealTomcatPort;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>A3 轴的「query 段数上限」真 Tomcat 门禁</b> —— 补的是<b>本域一处取证缺口</b>：
 * {@code hot-api} 此前<b>没有任何一条</b>段数断言（{@code HotApiRealTomcatGateTest} 只覆盖
 * prometheus 形态 / 404 / 405 / percent-encoding / 三个容器旋钮）⇒
 * 「{@code DRIFT-HOTAPI-030} 后续格（段数轴）」在 hot 域<b>只有静态取证、没有活体证据</b>。
 *
 * <h2>🔴 本类要判定的到底是什么（别把「段数轴」和「容器参数轴」混成一条）</h2>
 * <ul>
 *   <li><b>Go 侧（真源 · 消费点）</b>：
 *       <pre>
 * Go service/hot/api/internal/handler/hot/getHotArticlesHandler.go:17-20
 *   if err := httpx.Parse(r, &amp;req); err != nil {
 *       httpx.ErrorCtx(r.Context(), w, err)
 *       return
 *   }
 *
 * Go service/hot/api/hotcenter.go:53-65（httpx.SetErrorHandler）
 *   code := hotcommon.ErrorServerCommon      // 5001
 *   msg  := hotcommon.GetErrMsg(code)        // "internal server error"
 *   return http.StatusOK, &amp;response.Response{Code: code, Msg: msg, Data: nil}
 *
 * Go go-zero@v1.10.2/rest/httpx/responses.go:142-161（doHandleError）
 *   handler != nil ⇒ code, body := handler(err) ⇒ **HTTP 200 + 业务信封**
 * </pre>
 *   ⇒ ⇒ <b>Go hot 在「query 段数超限」时看到的是 HTTP 200 + {code:5001}</b>，
 *   <b>不是</b> 400，也不是裸文本。</li>
 *   <li><b>Go 的段数上限</b>（go1.25.11 <b>真源逐字</b>）：
 *       <pre>
 * Go src/net/url/url.go:1001   const defaultMaxParams = 10000
 * Go src/net/url/url.go:1003-1016
 *   func urlParamsWithinMax(params int) bool {
 *       withinDefaultMax := params &lt;= defaultMaxParams
 *       if urlmaxqueryparams.Value() == "" { return withinDefaultMax }
 *       ...
 *   }
 * Go src/net/url/url.go:1019-1021
 *   func parseQuery(m Values, query string) (err error) {
 *       if !urlParamsWithinMax(strings.Count(query, "&amp;") + 1) {
 *           return errors.New("number of URL query parameters exceeded limit")
 *       }
 * </pre>
 *       ⇒ ⇒ 段数 = {@code Count("&") + 1} ⇒ <b>≤ 10000 放行 / 第 10001 段就拒</b>
 *       ⇒ ⚠️ 判据是<b>消费点</b>（{@code parseQuery} 里那一行），不是常量声明行。</li>
 *   <li><b>RAG 侧（消费点 · 逐字同形）</b>：
 *       {@code service/common/.../http/GoStyleFormValues.java:215}
 *       （{@code MAX_URL_QUERY_PARAMS = 10000}）· {@code :262}
 *       （{@code countQuerySegments(query) > MAX_URL_QUERY_PARAMS} ⇒ 拒）· {@code :307}
 *       （{@code countQuerySegments} = {@code 1 + count('&')} ⇒ <b>与 Go 逐字同形</b>）
 *       ⇒ 共享层 {@code GoStyleFormValueLimitInterceptor:83-92}（{@code mode != OFF} ⇒ 生效；
 *       {@code ENVELOPE} ⇒ 抛 {@code GoStyleFormLimitExceededException} ⇒ 由<b>本域</b>兜底 handler
 *       渲染成 200 + {@code {code:5001}}）
 *       ⇒ 本域 {@code application.yml:141} = {@code mode: envelope}。</li>
 * </ul>
 *
 * <h2>⚠️ 为什么容器上限（{@code maxParameterCount = 10001}）<b>不是</b>这条的判据</h2>
 * <p>容器的 {@code maxParameterCount} 管的是<b>参数个数</b>，且 Tomcat 是在
 * {@code getParameterMap()} 时才惰性解析并检查 ⇒ 而共享层的闸门是
 * <b>Interceptor.preHandle</b>（{@code Ordered.HIGHEST_PRECEDENCE}）⇒
 * <b>它在参数绑定之前就抛了</b> ⇒ ⇒ <b>本类观察到的翻转点由「共享层判据」决定（10001 段），
 * 不是由容器上限决定（10002 段）</b>。
 * ⇒ ⚠️ 共享层<b>自己的设计文档</b>也是这么写的
 * （{@code service/common/.../http/GoStyleTomcatParameterLimitCustomizer.java:73-76}：
 * 「<b>= 10001</b>：= Go 的段数上限 10000 <b>+1</b> ⇒ ① ≤ 10001 个参数 ⇒ Tomcat 放行
 * ⇒ 由 {@code GoStyleFormValues} 按 Go 的语义判 ② <b>10001 段</b> ⇒ Go 的 net/url 会拒（&gt; 10000）
 * ⇒ 我们的 filter 也拒」）
 * ⇒ ⇒ **台账里「RAG 翻转点 = 10002 段」的说法与共享层的设计意图、与 Go 真源都矛盾**
 * ⇒ ⇒ 本类把这件事<b>变成实测</b>，而不是继续沿用推断。
 *
 * <h2>⚠️ 为什么「未超限」用 {@code code=200} 而不是 401 做对照</h2>
 * <p>本域<b>没有鉴权</b>：Go 侧 {@code service/hot/api/internal/handler/routes.go} 只注册了
 * {@code rest.WithPrefix}，<b>没有</b> {@code rest.WithJwt} ⇒ {@code GET /hot/v1/articles} 是
 * <b>公开读</b>端点（见 {@code HotArticleController} 的类注释）⇒ ⇒ 对照组只能看
 * <b>业务信封里的 code</b>：<b>10000 段 ⇒ code=200（闸门放行，走业务）</b>，
 * <b>10001 段 ⇒ code=5001（闸门拒）</b> ⇒ <b>两者 HTTP 状态码都是 200</b>
 * ⇒ ⇒ 判别力<b>只在 body 的 code 上</b> ⇒ <b>这正是本类必须存在的原因</b>
 * （只看状态码 ⇒ 两条用例都绿 ⇒ 判据无牙齿）。
 *
 * <h2>为什么用 {@link RealTomcatPort} 而不是 {@code RANDOM_PORT}</h2>
 * <p>同域 {@code HotApiRealTomcatGateTest} 用的是 {@code RANDOM_PORT} ⇒ 端口落在 OS 临时段
 * （macOS 49152–65535）⇒ 而别的会话的 Docker 容器正好在那一段发布端口 ⇒
 * 请求可能打到别人的服务 ⇒ <b>零字节即关</b>而<b>所有用例照样绿</b>
 * ⇒ ⇒ 本类改用本域专用端口段 {@link RealTomcatPort}（{@code 18146–18150}）。
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
class HotApiQuerySegmentLimitRealTomcatTest {

    /** Go {@code hot/common/errmsg.go} 的 {@code ErrorServerCommon}。 */
    private static final int CODE_SERVER_COMMON = 5001;

    /** Go {@code hot/common/errmsg.go} 的 {@code Success}。 */
    private static final int CODE_SUCCESS = 200;

    /**
     * Go {@code net/url/url.go:1001} 的 {@code defaultMaxParams = 10000}
     * ⇒ <b>第 10001 段</b>就是解析失败的那一段。
     */
    private static final int OVER_LIMIT_SEGMENTS = 10001;

    /** 恰好在 Go 的上限内 ⇒ 必须放行（对照组）。 */
    private static final int AT_LIMIT_SEGMENTS = 10000;

    /**
     * 🔴 <b>本类的刺激量必须用【空值】</b>，否则测的根本不是「段数轴」。
     *
     * <p>Go 的三条解析规则<b>按顺序</b>判（{@code service/common/.../http/GoStyleFormValues.java:261-276}
     * 逐字复刻）：
     * <ol>
     *   <li>{@code net/url} 的<b>段数</b>上限（{@code > 10000} ⇒ 拒）；</li>
     *   <li>{@code net/url} 的分段解析错误（percent-encoding 等）；</li>
     *   <li>go-zero 的 <b>2048 非空值</b>计数（{@code rest/httpx/util.go}）。</li>
     * </ol>
     * ⇒ ⚠️ <b>本类第一版用的刺激量是「非空值」</b>（{@code p0=1&p1=1…}）⇒
     * <b>10000 段就撞上了第 ③ 条（2048 非空值）</b> ⇒ ⇒ 对照组被拒 ⇒
     * <b>看起来像「RAG 在 10000 段就拒 ⇒ 早于 Go」</b> ⇒ <b>结论完全错</b>
     * ⇒ ⇒ 所以刺激量必须是<b>空值</b>（{@code p0=&p1=&…}）：
     * <b>段数照数、非空值恒为 0</b> ⇒ <b>只有第 ① 条可能触发</b> ⇒
     * ⇒ 这才是<b>真的在测段数轴</b>。
     *
     * <p>⚠️ 这也解释了 favorite 域那条同名门禁为什么用 {@code ?a=1& ×10001} 仍能测到段数：
     * 它<b>只测「超限」那一侧</b>（超限时 ① ③ 都触发 ⇒ 观察到的拒绝**无法区分是哪条**），
     * <b>没有对照组</b> ⇒ ⇒ 本类比它多一条<b>对照组</b>，把「10000 段必须放行」钉住。
     */
    private static final boolean EMPTY_VALUES = true;

    /**
     * Go 的裸 400 文案（{@code net/url/url.go:1019-1021}）——
     * <b>本域绝不该出现它</b>（Go 侧 {@code SetErrorHandler} 只取 code/msg，错误原文不进 wire）。
     */
    private static final String GO_RAW_SEGMENT_LIMIT_TEXT = "number of URL query parameters exceeded limit";

    @LocalServerPort
    private int port;

    @Autowired
    private com.rag.common.http.GoStyleFormValueLimitInterceptor formLimitInterceptor;

    @MockitoBean
    private HotRpcClient hotRpcClient;

    @MockitoBean
    private ArticleRpcClient articleRpcClient;

    private HttpClient http;

    @BeforeEach
    void setUp() {
        http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)   // 与 Go 侧默认一致
                .build();
        when(hotRpcClient.getHotArticles(anyInt(), anyLong())).thenReturn(
                GetHotArticlesResponse.newBuilder()
                        .addItems(HotArticleItem.newBuilder().setArticleId("a1").setHotScore(4242))
                        .build());
        // ⚠️ 下游文章补全也要给桩 —— 否则对照组的「走完业务路径」会撞上 mock 返回 null，
        //   让判据依赖「补全链对 null 的容错」而不是「闸门放行」⇒ 判据变哑。
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

    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    @Test
    @DisplayName("真容器必须绑到本域专用端口段（RANDOM_PORT 会落进 OS 临时段 ⇒ 与 Docker 发布端口同段）")
    void bindsToDedicatedPortSegment() {
        assertTrue(port >= RealTomcatPort.FIRST && port <= RealTomcatPort.LAST,
                "Tomcat 实际绑定的端口必须在 " + RealTomcatPort.FIRST + "-" + RealTomcatPort.LAST
                        + " 内，实际=" + port
                        + "（若落在 49152-65535，说明 webEnvironment 被改回 RANDOM_PORT 了）");
    }

    @Test
    @DisplayName("前提：本域的 form 闸门必须是 ENVELOPE（Go: hotcenter.go:53 注册了 SetErrorHandler ⇒ 200 + 业务信封）")
    void formLimitModeMustBeEnvelope() {
        assertEquals(com.rag.common.http.GoStyleFormLimitMode.Mode.ENVELOPE, formLimitInterceptor.mode(),
                "本域 application.yml 必须是 mode: envelope —— Go 侧 hotcenter.go:53 注册了 "
                        + "httpx.SetErrorHandler ⇒ 错误压成 200 + {code:5001,…}；"
                        + "若被改成 raw/off，本类下面那条形状断言就不代表本域契约了。"
                        + "实际 mode=" + formLimitInterceptor.mode());
    }

    @Test
    @DisplayName("真 Tomcat：第 10001 段 ⇒ 200 + 业务信封 {code:5001}（与 Go 的 net/url 翻转点同在第 10001 段）")
    void overLimitQuerySegmentsYieldBusinessEnvelope() throws Exception {
        HttpResponse<String> resp = get("/hot/v1/articles?" + segments(OVER_LIMIT_SEGMENTS));

        assertEquals(200, resp.statusCode(),
                "Go 侧 hotcenter.go:53 的 SetErrorHandler 把解析错误压成 HTTP 200 + 业务信封；"
                        + "RAG 侧若返回 400 ⇒ 说明本域没走信封形状（要么 mode 不是 envelope，"
                        + "要么异常没到 @RestControllerAdvice）。实际 body=" + resp.body());
        assertTrue(resp.body() != null && resp.body().contains("\"code\":" + CODE_SERVER_COMMON),
                "第 " + OVER_LIMIT_SEGMENTS + " 段时 body 必须是业务信封 {code:" + CODE_SERVER_COMMON
                        + ",…}（Go: hotcommon.ErrorServerCommon）；实际 body=" + resp.body());
        assertFalse(resp.body().contains(GO_RAW_SEGMENT_LIMIT_TEXT),
                "🔴 本域绝不能把 Go 的裸 400 文案泄到 wire 上：Go 侧 SetErrorHandler 只取 code/msg，"
                        + "错误原文不进 wire。实际 body=" + resp.body());
    }

    @Test
    @DisplayName("对照组：恰好 10000 段 ⇒ 闸门放行 ⇒ 走业务路径 ⇒ body 的 code=200（证明上一条不是无条件短路）")
    void exactlyAtLimitSegmentsArePassedThrough() throws Exception {
        HttpResponse<String> resp = get("/hot/v1/articles?" + segments(AT_LIMIT_SEGMENTS));

        assertEquals(200, resp.statusCode(),
                "恰好 10000 段在 Go 侧是放行的（net/url 的上限是 > 10000 才拒）⇒ "
                        + "RAG 侧也必须放行并走完业务路径。实际 body=" + resp.body());
        assertTrue(resp.body() != null && resp.body().contains("\"code\":" + CODE_SUCCESS),
                "对照组必须看到**业务成功**信封 {code:" + CODE_SUCCESS + ",…} —— "
                        + "若这里也返回 {code:" + CODE_SERVER_COMMON + ",…} ⇒ 说明闸门无条件短路，"
                        + "上一条形状断言就失去判别力。实际 body=" + resp.body());
        assertFalse(resp.body().contains(GO_RAW_SEGMENT_LIMIT_TEXT),
                "未超限时不该出现 Go 的裸 400 文案。实际 body=" + resp.body());
    }

    /**
     * 造出 {@code n} 段 query。
     *
     * <p>⚠️ <b>每段的「值」必须留空</b>（见 {@link #EMPTY_VALUES} 的说明）：
     * 只有空值才能保证「非空值个数 = 0」⇒ <b>go-zero 的 2048 非空值规则不可能先触发</b>
     * ⇒ ⇒ 被拒时<b>只能是段数规则</b>干的 ⇒ 断言才有判别力。
     */
    private static String segments(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append('p').append(i).append(EMPTY_VALUES ? "=" : "=1");
        }
        return sb.toString();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }
}
