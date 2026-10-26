package com.rag.favorite.favorite.api;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.support.RealTomcatPort;
import com.rag.users.common.jwt.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * favorite-api 的**真实 Tomcat** 门禁 —— 补上 {@code FavoriteApiProtectionWiringTest}
 * 明确标注为「未验」的那一层。
 *
 * <h2>为什么 MockMvc 不够</h2>
 * <p>{@code @SpringBootTest(webEnvironment = MOCK)} 走的是 {@code MockHttpServletResponse}
 * ⇒ <b>不经过 servlet filter 链</b> ⇒ 保护层（MaxConns / Breaker / CpuShedding / MaxBytes）
 * 与 <b>容器输入上限三旋钮</b>在 MockMvc 下<b>一次都没跑过</b>。
 * <p>⚠️ 而且 MockMvc 在「响应提交时序」上与真实 Tomcat 不同（message 域的 DRIFT-024 已实证：
 * MockMvc 里 Content-Type 完好，真实栈 curl 出来是<b>空的</b>）⇒ 用 MockMvc 断言响应头会<b>假绿</b>。
 *
 * <h2>本类验的是「机制生效」，不是「两侧数值等价」</h2>
 * <p>⚠️ <b>证据边界</b>：本类把 {@code rag.protection.max-bytes} 调到 <b>1024</b> 以便触发，
 * 而 Go 侧 favorite-api <b>没有</b>配 MaxBytes ⇒ 走 {@code rest/config.go:53} 的
 * {@code default=1048576}。⇒ <b>本类证明的是「RAG 侧的保护层与三旋钮真的生效」</b>
 * （即 §3.3 的「注解存在 ≠ 机制生效」那一层被验到了），
 * <b>不是</b>「RAG 与 Go 在同一阈值下行为一致」——
 * 后者需要<b>双侧活体对拍</b>，而 Go 侧 favorite-api 依赖 {@code etcd} + favorite-rpc + Redis，
 * 本环境起不来（message 域有同样的边界说明，见
 * {@code MessageApiProtectionRealTomcatTest} 的类注释）。
 *
 * <h2>期望值的来源</h2>
 * <ul>
 *   <li><b>MaxBytes</b>：Go {@code rest/handler/maxbyteshandler.go:21} 是裸
 *       {@code w.WriteHeader(413)}（不调 {@code httpx.Error}）⇒ <b>无 body、无 Content-Type</b>。</li>
 *   <li><b>三旋钮</b>：Go 的目标值见 {@code tools/difftest/check-tomcat-limits.py} 的表头
 *       （{@code relaxed-query-chars} 放行 / {@code max-parameter-count}=2048 /
 *       {@code max-http-request-header-size}=1028KB），与 message 域 2026-10-25 的双侧活体实测同源。</li>
 *   <li><b>链顺序</b>：Go 侧 MaxBytes 挂在 {@code rest/engine.go:164}，
 *       {@code Authorize} 由 {@code engine.go:109} 在 native middlewares <b>之后</b> append
 *       ⇒ 超限时<b>先撞 413 而非 401</b>。</li>
 * </ul>
 *
 * <h2>⚠️ 两个上限的口径不同（本类的核心事实）</h2>
 * <p>2026-10-25 真 Tomcat 实测（4 组判别用例，逐组带一个合法非空 {@code folderId}，
 * 保证「应用层永不拒、字段必在场」⇒ 结果只可能来自 Tomcat 自己）：
 * <pre>
 *   总「值」=2048（名字 1，非空 1） ⇒ 放行（进到 RPC 层）
 *   总「值」=2049（名字 1，非空 1） ⇒ 拒（Tomcat 抛 InvalidParameterException）
 *   总「值」=2049（名字 2，非空 1） ⇒ 拒
 *   总「值」=3000（名字 3000，非空 1）⇒ 拒
 * </pre>
 * ⇒ <b>Tomcat 的 {@code maxParameterCount} 数的是「总参数值个数」（含空值）</b>，
 * <b>不是</b>「参数名字个数」（caseB：名字只有 1 个仍被拒）。
 * <p>⇒ 而 Go 的 {@code httpx.GetFormValues}（{@code rest/httpx/util.go:37-53}）数的是
 * <b>非空值个数</b>（{@code if len(v) == 0 { continue } } 空值不计数），且计数器
 * {@code n} <b>跨所有 key 共享</b>。
 * <p>⇒ 两者只在「值全非空」时同阈值（翻转点都是 2049）；
 * <b>残余窗口 = 总「值」数 2049~10000 且非空值数 ≤2048</b>（Go 放行 / RAG 拒）
 * ⇒ 跨域条目 <b>DRIFT-HOTAPI-030</b>（收口在 {@code global}）。
 * <p>⇒ Go 侧的第二道上限是 {@code net/url} 的
 * {@code defaultMaxParams = 10000}（Go 1.25.11 {@code src/net/url/url.go:1001}）
 * ⇒ 总值数 >10000 时两侧都是「解析失败 ⇒ 5003」。
 */
@SpringBootTest(
        classes = FavoriteApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                "grpc.client.favorite-rpc.address=static://127.0.0.1:65535",
                "grpc.client.favorite-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                "rag.protection.max-bytes=1024",
                // ⚠️ 真 Tomcat 下首个请求要加载 Jackson/MyBatis 等类，可能超过 3s
                //    ⇒ 服务端会回 503 "Request Timeout"（那是预热，不是被测行为）
                "observability.http-timeout=30s"
        })
class FavoriteApiRealTomcatGateTest {

    private static final String FOLDER_CREATE = "/favorite/v1/folder/create";
    private static final String ITEM_LIST = "/favorite/v1/item/list";

    /** favorite-api 的默认 AccessSecret（application.yml:94 的默认值，测试未覆盖它）。 */
    private static final String ACCESS_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    @LocalServerPort
    private int port;

    /**
     * <b>为什么不用 {@code RANDOM_PORT}</b>（2026-10-25 实测踩出来的，见
     * {@link com.rag.favorite.favorite.api.support.RealTomcatPort} 的类注释）：
     * {@code RANDOM_PORT} 挑的是 OS <b>临时端口段</b>，而<b>别的会话的 Docker 容器正好在那一段
     * 发布端口</b>；macOS 上 {@code 127.0.0.1:P} 会<b>抢占</b> Tomcat 绑的 {@code 0.0.0.0:P}
     * ⇒ 客户端把请求打到了 PostgreSQL ⇒ <b>零字节即关</b> ⇒ 报
     * {@code HTTP/1.1 header parser received no bytes} ⇒ <b>看着像被测代码炸了，其实压根没打到</b>。
     *
     * <p>⇒ 改成 <b>自己挑一个确定归我们的端口</b>（专用端口段 + bind 自检）。
     */
    @DynamicPropertySource
    static void serverPort(DynamicPropertyRegistry registry) {
        registry.add("server.port", RealTomcatPort::pick);
    }

    @MockitoBean
    private FavoriteRpcClient rpcClient;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    private HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)   // 与 Go 侧默认一致，且避免 h2c 升级干扰响应头
                .build();
    }

    /**
     * 发请求；<b>把「一个字节都没读到」的传输层失败翻译成人能读懂的诊断</b>。
     *
     * <p>⚠️ 为什么要这层（2026-10-25 实测）：客户端连上 {@code 127.0.0.1:端口} 后
     * <b>读到 0 字节</b> ⇒ 典型症状是 {@code IOException: HTTP/1.1 header parser received no bytes}
     * ⇒ <b>长得像「被测代码炸了」</b>，但真相往往是「那个端口上根本不是我们的 Tomcat」
     * （别的会话的容器占着同号端口 ⇒ 见 {@link RealTomcatPort} 的类注释）
     * ⇒ ⇒ 这里把<b>端口号 + 占用者</b>一并打出来 ⇒ 下一次看到就是一眼定位，不用再查一轮。
     */
    private HttpResponse<String> send(HttpRequest req) throws Exception {
        try {
            return client().send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IOException("端口 " + port + " 上读到了 0 字节（本请求没打到本测试的 Tomcat？）"
                    + "；该端口的监听者=[" + RealTomcatPort.holders(port) + "]"
                    + "；原异常=" + e, e);
        }
    }

    private HttpResponse<String> postJson(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(req);
    }

    /**
     * 带 JWT 的 GET —— 走鉴权 ⇒ 到达 controller ⇒ 才能验应用层的 form 计数语义。
     *
     * <p>⚠️ <b>为什么必须带 token</b>：鉴权（JWT）在 controller 之前 ⇒ 无 token 时
     * 请求在 interceptor 就被 401 短路，<b>永远到不了</b> {@code goFormFirstValue}。
     */
    private HttpResponse<String> getWithToken(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .GET()
                .build();
        return send(req);
    }

    /** 用 favorite-api 的默认 AccessSecret 签一个合法 token（application.yml 的默认值）。 */
    private String token() {
        return JwtUtil.getToken(ACCESS_SECRET, System.currentTimeMillis() / 1000L, 3600L, 42L);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return send(req);
    }

    // ══════════════════ 保护层：MaxBytes ══════════════════

    @Test
    @DisplayName("真 Tomcat：超限 → 413 + 空 body + 无 Content-Type（对齐 Go 裸 WriteHeader）")
    void oversizedBodyYieldsBare413() throws Exception {
        HttpResponse<String> resp = postJson(FOLDER_CREATE, oversizedJson());

        assertEquals(413, resp.statusCode());
        assertTrue(resp.body() == null || resp.body().isEmpty(),
                "Go 侧 w.WriteHeader(413) 不写任何字节，body 必须为空；实际=[" + resp.body() + "]");
        assertNull(resp.headers().firstValue("content-type").orElse(null),
                "Go 侧 413 没有 Content-Type；容器若补出 text/html 就是漂移。实际="
                        + resp.headers().map());
    }

    @Test
    @DisplayName("真 Tomcat：413 压过鉴权（MaxBytes 在 auth 之外，与 Go chain 顺序一致）")
    void oversizedBodyBeatsAuth() throws Exception {
        // 刻意不带 Authorization：超限时先撞 413 而非 401。
        HttpResponse<String> resp = postJson(FOLDER_CREATE, oversizedJson());
        assertEquals(413, resp.statusCode(),
                "MaxBytes 必须先于 JWT 鉴权短路（Go rest/engine.go:105-112 + chain.Append 语义）");
    }

    @Test
    @DisplayName("真 Tomcat：未超限 + 无 token → 401（证明 413 不是无条件短路）")
    void withinLimitStillHitsAuth() throws Exception {
        HttpResponse<String> resp = postJson(FOLDER_CREATE, "{}");
        assertEquals(401, resp.statusCode());
    }

    // ══════════════════ 容器输入上限三旋钮 ══════════════════

    @Test
    @DisplayName("🔴 三旋钮① relaxed-query-chars：query 里的 [ ] 不被 Tomcat 拒（Go 放行）")
    void bracketNotationIsAcceptedLikeGo() throws Exception {
        // Go 侧 net/http 不校验 query 里的 [ ]，且 go-zero httpx.GetFormValues 明确支持方括号记法
        // ⇒ Tomcat 默认会 400 + HTML 错误页（不是业务信封）⇒ 这里必须**不是**那种响应。
        HttpResponse<String> resp = get(ITEM_LIST + "?folderId[]=123");

        assertFalse(isTomcatHtmlErrorPage(resp),
                "Tomcat 拒了带方括号的 query ⇒ 与 Go 不一致（Boot 默认 relaxed-query-chars 是空的）。"
                        + "实际 status=" + resp.statusCode() + " body=[" + resp.body() + "]");
        assertNotTomcat400(resp);
    }

    @Test
    @DisplayName("🔴 Go 的 maxFormParamCount=2048：2049 个非空 form 值 ⇒ 5003")
    void nonEmptyFormValueCountOverLimitIsRejected() throws Exception {
        // Go: httpx/util.go:15 `maxFormParamCount = 2048`，
        //     httpx/util.go:44-45 `if n < maxFormParamCount { … n++ } else { return nil, fmt.Errorf("too many form values…") }`
        // ⇒ Go 侧第 2049 个**非空值**触发 httpx.Parse 失败 ⇒ handler 落 200 + {code:5003}。
        //
        // ⚠️ 计数口径（2026-10-25 真 Tomcat 实测，见类注释）：Tomcat 的 maxParameterCount 数的是
        //    「**总参数值个数**（含空值）」，Go 的 maxFormParamCount 数的是「**非空值个数**」
        //    ⇒ 本用例全是非空值 ⇒ 两侧口径在此重合 ⇒ 翻转点两侧都是 2049。
        HttpResponse<String> resp = getWithToken(ITEM_LIST + "?" + manyFormParams(2049));

        assertEquals(200, resp.statusCode(),
                "应用层拒绝也必须走 HTTP 200 + 业务信封（Go httpx.OkJsonCtx 语义）；body=[" + resp.body()
                        + "] ct=" + resp.headers().firstValue("content-type").orElse("-"));
        assertTrue(resp.body() != null && resp.body().contains("\"code\":5003"),
                "第 2049 个非空 form 值必须触发 Go 的 maxFormParamCount ⇒ 5003；实际 body=["
                        + resp.body() + "]");
    }

    @Test
    @DisplayName("🔴 Go 的 maxFormParamCount=2048：恰好 2048 个非空 form 值【放行】（不是 5003）")
    void nonEmptyFormValueCountAtLimitIsPassedThrough() throws Exception {
        HttpResponse<String> resp = getWithToken(ITEM_LIST + "?" + manyFormParams(2048));

        assertEquals(200, resp.statusCode());
        assertFalse(resp.body() != null && resp.body().contains("\"code\":5003"),
                "恰好 2048 个非空值必须放行（Go httpx/util.go:15 的 `if n < maxFormParamCount`）；"
                        + "若这里也报 5003，说明上限被改小或计数口径漂移。实际 body=[" + resp.body() + "]");
    }

    @Test
    @DisplayName("🔴 容器层参数上限：Tomcat 拒 ⇒ 必须落 200 + 业务信封（修复前是 500 + 5001）")
    void containerParameterLimitIsSurfacedAsGoParseError() throws Exception {
        // 本用例是**容器层**上限（server.tomcat.max-parameter-count=2048）的触发面，
        // 与上面两条的**应用层**上限（Go 逐字复刻的 2048 非空值）刻意用**不同形状**的输入区分：
        //   总「值」数 = 1 + 2048 = 2049  ⇒ 超 Tomcat 上限
        //   非空值数 = 1                  ⇒ Go 侧放行（httpx.GetFormValues 只数非空值）
        //
        // ⚠️ 修复前：Tomcat 抛 org.apache.tomcat.util.http.InvalidParameterException
        //    （**不是** IllegalArgumentException 的子类）⇒ 绕过 GlobalExceptionHandler#handleIllegalArgument
        //    ⇒ 落到 handleAny 兜底 ⇒ **HTTP 500 + {code:5001}**。
        //    而 Go 的 7 个 handler 全部走 httpx.OkJsonCtx ⇒ **HTTP 恒 200** ⇒ 500 是 RAG 独有的形态。
        // ✅ 修复后：异常在 FavoriteApiSupport#parameterMap 被映射成 IllegalArgumentException
        //    ⇒ 落 handleIllegalArgument ⇒ **HTTP 200 + {code:5003, msg:"参数错误"}**。
        HttpResponse<String> resp = getWithToken(ITEM_LIST + "?" + oneNonEmptyPlusEmpty(2048));

        assertEquals(200, resp.statusCode(),
                "容器层的参数上限也必须落 HTTP 200（Go 的 api 层不存在 500 出口）；实际 body=["
                        + resp.body() + "] ct=" + resp.headers().firstValue("content-type").orElse("-"));
        // ⚠️ 5003 是**当前实现**的形态，**不是** Go 的形态：Go 在同一输入下是「放行 ⇒ 200 + 业务结果」。
        //    ⇒ 这一格残余差异（总「值」数 2049~10000 且非空值 ≤2048）见 DRIFT-HOTAPI-030（跨域，收口在 global）。
        //    ⇒ 若全局把 max-parameter-count 放宽到 10000（Go net/url 的真实上限），本断言需同步更新。
        assertTrue(resp.body() != null && resp.body().contains("\"code\":5003"),
                "容器层上限触发后必须落 Go 的解析失败出口 5003（不是兜底的 5001）；实际 body=["
                        + resp.body() + "]");
    }

    @Test
    @DisplayName("🔴 三旋钮③ max-http-request-header-size：20 KiB 的请求行必须放行（Boot 默认 8KB 会拒）")
    void longRequestLineIsAccepted() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20 * 1024; i++) {
            sb.append('a');
        }
        HttpResponse<String> resp = get(ITEM_LIST + "?folderId=" + sb);

        assertFalse(isTomcatHtmlErrorPage(resp),
                "20 KiB 的请求行必须放行（Go 侧上限是 1028KB）；实际 status=" + resp.statusCode());
        assertNotTomcat400(resp);
    }

    // ══════════════════ 辅助 ══════════════════

    private static void assertNotTomcat400(HttpResponse<String> resp) {
        assertFalse(resp.statusCode() == 400 && isTomcatHtmlErrorPage(resp),
                "出现了 Tomcat 的 400 HTML 错误页（Go 侧不会产生这个响应）");
    }

    /** Tomcat 的 400/414 等容器级错误页特征：Content-Type 是 text/html 且 body 含 Tomcat 痕迹。 */
    private static boolean isTomcatHtmlErrorPage(HttpResponse<String> resp) {
        String contentType = resp.headers().firstValue("content-type").orElse("");
        String body = resp.body() == null ? "" : resp.body();
        return contentType.contains("text/html")
                && (body.contains("<html") || body.contains("Apache Tomcat"));
    }

    private static String oversizedJson() {
        StringBuilder sb = new StringBuilder("{\"name\":\"");
        while (sb.length() < 4096) {
            sb.append('a');
        }
        return sb.append("\"}").toString();
    }

    private static String manyFormParams(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append("folderId=").append(i);
        }
        return sb.toString();
    }

    /**
     * 「1 个非空值 + N 个空值」—— 同一 name，总「值」数 = N+1，非空值数 = 1。
     *
     * <p>用来把**容器层**上限（Tomcat 数总「值」数）与**应用层**上限
     * （Go 逐字复刻的「非空值数」）拆开：两者对同一输入给出不同结论。
     */
    private static String oneNonEmptyPlusEmpty(int emptyCount) {
        StringBuilder sb = new StringBuilder("folderId=7");
        for (int i = 0; i < emptyCount; i++) {
            sb.append("&folderId=");
        }
        return sb.toString();
    }
}
