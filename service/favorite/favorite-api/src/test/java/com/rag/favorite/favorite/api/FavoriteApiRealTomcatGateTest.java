package com.rag.favorite.favorite.api;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.support.RealTomcatPort;
import com.rag.favorite.favorite.api.support.RealTomcatHttp;
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
 *
 * <h2>🔴 订正（2026-10-27）：上面的「残余窗口」已闭合，容器层<b>不再</b>当判定权</h2>
 * <p>⚠️ <b>上面那段 2026-10-25 的实测与结论描述的是「容器上限 = 2048」那个时代</b>
 * ⇒ ⇒ <b>不改写原文</b>（历史事实保留），此处加订正：
 * <ul>
 *   <li>共享层新增 {@code GoStyleTomcatParameterLimitCustomizer}（G4 落码 {@code cd893d1}）
 *       ⇒ <b>无条件</b>把 Tomcat 的 {@code maxParameterCount} 抬到 <b>10001</b>
 *       （= Go 的 {@code net/url} 段数上限 10000 + 1）⇒ ⇒ <b>判定权交还应用层</b>
 *       ⇒ ⇒ 容器层<b>不再</b>比 Go 更早拒；</li>
 *   <li>⇒ ⇒ 「总「值」数 2049~10000 且非空值数 ≤2048」这一格：
 *       <b>Go 放行 / RAG 也放行</b> ⇒ ⇒ 旧漂移 <b>DRIFT-HOTAPI-030</b>（跨域，收口在 global）
 *       的这一格<b>已闭合</b>（另见 {@code docs/DRIFT/global/vs-common.md} §4.10.25）；</li>
 *   <li>⇒ ⇒ 下面 {@code containerParameterLimitIsSurfacedAsGoParseError} 那条用例的断言
 *       <b>已从「必须落 5003」改成 Go 语义「放行 ⇒ 进业务层」</b>
 *       ⇒ 它<b>方法名保留旧名</b>是为了不打断 {@code global/vs-common.md} §4.10.25.4 的引用
 *       ⇒ ⇒ <b>以该方法的 DisplayName 与断言为准</b>；</li>
 *   <li>⚠️ <b>仍然存在、本次不覆盖</b>的残余：总「值」数<b>超过 Go 的 10000 段上限</b>时，
 *       Go 拒（400 + {@code net/url} 原文）；而 favorite 域<b>没有</b>实现这道段数上限
 *       （域内判据只数<b>非空值</b>；共享层的段数闸门因 favorite = {@code OFF}（域自捕获）而不装）
 *       ⇒ ⇒ 该区间两侧不一致 ⇒ 归 DRIFT-HOTAPI-030 的<b>后续格</b>，<b>显式登记，不假装闭合</b>。</li>
 * </ul>
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
                "observability.http-timeout=30s",
                // 让判据**只**取决于「本类要验的那条路径」，不被 CPU 自适应降载抢先返回：
                // 降载命中时 GoStyleCpuSheddingFilter#doFilter 走 `response.setStatus(503)` 并**直接 return**
                // ⇒ **裸 503、无 body、无业务信封**（与上面 http-timeout 那个 503 是两回事）
                // ⇒ ⇒ 本类要验的「HTTP 200 + 业务信封」判据被抢先盖掉 ⇒ 机器一忙就**红在错的地方**。
                // ⚠️ 这是**测试专用**覆盖，不改生产默认值：默认 true 对齐 Go 侧
                //    rest/config.go:22 `Middlewares.Shedding bool json:",default=true"`
                //    （默认值与 Go 出处见 GoStyleProtectionProperties.CpuShedding#enabled）。
                // ⚠️ 本类**不验**降载行为（8 条用例没有一条断言 503/降载）⇒ 关掉它不损失任何覆盖。
                "rag.protection.cpu-shedding.enabled=false"
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
            // 🔴 走 RealTomcatHttp：JDK HttpClient 对 interim(1xx) 的上限是 8 条且**硬编码**，
            //    而 Tomcat 11.0.24 在这类请求上会连发 103 Early Hints ⇒ ⇒ 撞上限就回落 HttpURLConnection。
            //    详见 RealTomcatHttp 的类注释（裸 socket 实测证据）。
            return RealTomcatHttp.send(req);
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
    @DisplayName("🔴 Go 放行（1 个非空值 + 2048 个空值 ⇒ 非空值数 1 ≤ 2048）⇒ RAG 必须放行进业务层（不是 5003）")
    void containerParameterLimitIsSurfacedAsGoParseError() throws Exception {
        // ⚠️ **方法名沿用旧名**（"容器层上限 ⇒ Go 解析失败"）：那描述的是**修复前**的形态
        //    （当时容器上限 = 2048 ⇒ Tomcat 在第 2049 个条目就拒 ⇒ 域内映射成 5003）。
        //    ⇒ 2026-10-27 共享层把容器上限抬到 10001（GoStyleTomcatParameterLimitCustomizer，
        //      G4 落码 cd893d1）⇒ **判定权交还应用层** ⇒ 本用例断言已改为 **Go 语义（放行）**。
        //    ⇒ 方法名保留是为了不打断 docs/DRIFT/global/vs-common.md §4.10.25.4 的引用
        //      ⇒ ⇒ **以本方法的 DisplayName 与断言为准**。
        //
        // Go 出处（真源逐行，非转述）：
        //   ① Go 1.25 net/url/url.go:1001 `const defaultMaxParams = 10000`
        //      ⇒ query 段数 = 2049 ≤ 10000 ⇒ **不触发** Go 的段数上限；
        //   ② go-zero@v1.10.2/rest/httpx/util.go:15 `maxFormParamCount = 2048`
        //      ＋ :44 `if len(v) == 0 { continue }` ⇒ **空值不计数** ⇒ 非空值数 = 1 ≤ 2048
        //      ⇒ ⇒ **Go 放行**（两道上限都不触发）；
        //   ③ 放行后 handler 进业务层：favorite/api/internal/handler/favorite/
        //      get_favorites_by_folder_handler.go:27-33 ⇒ `httpx.OkJsonCtx`
        //      ⇒ **HTTP 恒 200 + 业务信封**（Go 的 api 层没有 5xx 出口）；
        //   ④ 业务失败时的码 = codeFromRPCError(rpcErr)
        //      （favorite/api/internal/logic/favorite/helper.go:44-46 ⇒ favoritecommon.BizCodeFromError）
        //      ⇒ favorite/common/errmsg.go:113-116：普通 error ⇒ status.FromError 的 ok=false
        //         ⇒ **CodeServerBusy = 1015**（:12 ＋ 文案「服务繁忙」:46）
        //         ⇒ gRPC Unavailable 也落 default 分支（:135-136）⇒ 同样是 1015。
        // 语义等价论证：本用例断言的「200 + {code:1015}」= Go 在**同一输入**下的**同一出口**
        //   （放行 ⇒ 进业务层 ⇒ 业务失败 ⇒ 1015）⇒ ⇒ **不是** RAG 独有的形态。
        //
        // 📌 订正注记（2026-10-28 · G3，按 G4 §4.10.27 的要求）：**断言不再钉死业务码**。
        //   旧断言是 contains("\"code\":1015") —— 1015 是「当前实现恰好落『服务繁忙』」的**实现细节**
        //   （下游 folder 不存在 / RPC 不可用 ⇒ default 分支 ⇒ 1015）⇒ ⇒ 一旦下游状态或错误码分段变化，
        //   这条断言就会**假红**，而请求其实仍然「放行进了业务层」⇒ ⇒ 断言的对象应当是**可观察的不变量**：
        //   ① 不是 5003（不是「解析失败」那一格）② 不是 Tomcat HTML ③ **是业务信封**。
        //   ⇒ ⇒ 原文保留（不改写），以本注记 + 下方断言为准。
        HttpResponse<String> resp = getWithToken(ITEM_LIST + "?" + oneNonEmptyPlusEmpty(2048));

        assertEquals(200, resp.statusCode(),
                "Go 侧这一格是「放行 ⇒ 进业务层 ⇒ httpx.OkJsonCtx」⇒ HTTP 恒 200（api 层无 5xx 出口）；"
                        + "实际 body=[" + resp.body() + "] ct="
                        + resp.headers().firstValue("content-type").orElse("-"));
        assertFalse(resp.body() != null && resp.body().contains("\"code\":5003"),
                "Go 在这一格是**放行**（非空值数 = 1 ≤ 2048，且段数 2049 ≤ 10000）⇒ 不得落 5003"
                        + "（5003 是「解析失败」那一格）；实际 body=[" + resp.body() + "]");
        // 🔴 只钉「是业务信封」⇒ 不钉死具体业务码（理由见上方 📌 订正注记）
        assertTrue(isBusinessEnvelope(resp),
                "必须证明请求**真的进了业务层**（响应是业务信封，而不是容器层/框架层的错误页）；"
                        + "实际 status=" + resp.statusCode() + " body=[" + resp.body() + "] ct="
                        + resp.headers().firstValue("content-type").orElse("-"));
        assertFalse(isTomcatHtmlErrorPage(resp),
                "不得出现 Tomcat 的 HTML 错误页（Go 侧不存在这个响应形态）；实际 status="
                        + resp.statusCode() + " body=[" + resp.body() + "]");
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

    /**
     * 业务信封 ＝ {@code {"code":…,"msg":…,"data":…}} 的 JSON 形态。
     *
     * <p>🔴 <b>为什么不钉死 code</b>（2026-10-28 · G3，按 G4 §4.10.27 的要求）：
     * 旧断言是 {@code contains("\"code\":1015")}，而 1015 是「当前实现恰好落『服务繁忙』」的
     * <b>实现细节</b>（下游 folder 不存在 / RPC 不可用 ⇒ default 分支）⇒ ⇒ 下游状态一变就<b>假红</b>，
     * 而请求其实仍然「放行进了业务层」⇒ ⇒ 断言的对象应是<b>可观察的不变量</b>（是业务信封 ⇒ 进过业务层），
     * 而非<b>某个具体码</b>。
     */
    private static boolean isBusinessEnvelope(HttpResponse<String> resp) {
        String contentType = resp.headers().firstValue("content-type").orElse("");
        String body = resp.body() == null ? "" : resp.body();
        return contentType.contains("application/json")
                && body.contains("\"code\"")
                && body.contains("\"msg\"");
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
