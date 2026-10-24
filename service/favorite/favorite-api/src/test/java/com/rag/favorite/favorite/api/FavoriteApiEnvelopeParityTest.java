package com.rag.favorite.favorite.api;

import com.rag.common.response.Response;
import com.rag.favorite.favorite.api.advice.GlobalExceptionHandler;
import com.rag.favorite.favorite.api.advice.GlobalResponseAdvice;
import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.FavoriteFolder;
import com.rag.favorite.favorite.api.dto.FavoriteItem;
import com.rag.favorite.favorite.api.dto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
import com.rag.users.common.jwt.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴🔴 favorite api 的 <b>响应字节</b>双侧对拍 —— 补 {@code docs/DRIFT/favorite/api.md} §4
 * 证据边界里的未验项。
 *
 * <h2>期望值的来源</h2>
 * <p>🔴 <b>全部期望值来自 Go 真源的实测</b>，不是推断：
 * {@code tools/api-envelope-probe/favorite/} 用<b>真的 go-zero v1.10.2 库</b>
 * （{@code rest/httpx} + {@code rest/handler}）与<b>从 Go 真源逐字节复制</b>的
 * {@code response.go} / {@code types.go} / {@code errmsg.go} 算出响应字节，
 * 落在 {@code expected.txt}。
 * ⇒ <b>Go 真源变了 ⇒ 探针的 diff 会失败</b> ⇒ 提醒更新本测试（而不是让 RAG 静默漂移）。
 *
 * <h2>本轮补上的四件事</h2>
 * <ol>
 *   <li><b>「Go 侧「非空值 &gt;2048」时的逐字节响应」</b> —— 原为 🟡 源码级 ⇒ 本轮升级为<b>活体对拍</b>。
 *       （Go 侧用<b>真的</b> {@code httpx.Parse} ⇒ {@code maxFormParamCount=2048} 判据真的生效。）</li>
 *   <li><b>「两种 int64 编码方式」</b> —— {@code ,string} ⇒ JSON 字符串 / 无 {@code ,string} ⇒ JSON 数字
 *       —— 原为读源码判定 ⇒ 本轮<b>实测</b>（且用 2^53+1 的雪花 ID 证明「写成数字会丢精度」）。</li>
 *   <li><b>「go-zero 的 {@code ,optional} 不是 {@code omitempty}」</b> —— 原为读源码判定 ⇒ 本轮<b>实测</b>
 *       （{@code title}/{@code cover} 为空时<b>仍然出现</b>，值是 {@code ""}）。</li>
 *   <li><b>「Go 的 RecoverHandler 写 500 + text/plain」</b> —— 🔴 <b>原结论是错的</b>：
 *       真库的 {@code handler.RecoverHandler} 是 {@code w.WriteHeader(500)} ⇒ <b>空 body、无 Content-Type</b>
 *       ⇒ 本轮用<b>真库</b>取证并把它降级为 OBS 段（两侧本该不同）。</li>
 * </ol>
 *
 * <h2>为什么用「真 MVC 管线」而不是自己 new 一个 ObjectMapper</h2>
 * <p>序列化字节取决于 <b>容器里那个 mapper</b> 的配置（{@code JsonMapperBuilderCustomizer} 家族），
 * 自己 {@code new ObjectMapper()} 就是「拿自己的一份副本自证」（AGENTS.md §3.3）。
 * ⇒ 本类注入 <b>Spring 管理的</b> {@link ObjectMapper}，并用<b>真实</b>的
 * {@link GlobalResponseAdvice} 走一遍 advice ⇒ 产出与容器里完全相同的字节。
 * <p>⇒ 另有一条用例走<b>真 HTTP</b>（{@code webEnvironment = RANDOM_PORT}），
 * 用真实响应体反证「advice + mapper == 真实管线」这条等价关系成立。
 */
@SpringBootTest(
        classes = FavoriteApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "spring.cloud.nacos.discovery.enabled=false",
                "grpc.client.favorite-rpc.address=static://127.0.0.1:65535",
                "grpc.client.favorite-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN",
                // ⚠️ 真 Tomcat 下首个请求要加载 Jackson/MyBatis 等类，可能超过 3s
                //    ⇒ 服务端会回 503 "Request Timeout"（那是预热，不是被测行为）
                "observability.http-timeout=30s"
        })
class FavoriteApiEnvelopeParityTest {

    private static final String ITEM_LIST = "/favorite/v1/item/list";

    /** favorite-api 的默认 AccessSecret（application.yml:94 的默认值，测试未覆盖它）。 */
    private static final String ACCESS_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    @LocalServerPort
    private int port;

    @MockitoBean
    private FavoriteRpcClient rpcClient;

    /** 容器里那个 mapper —— 序列化字节必须由它产出。 */
    @Autowired
    private ObjectMapper objectMapper;

    /** 真实的 advice —— envelope 与 Content-Type 都由它决定。 */
    @Autowired
    private GlobalResponseAdvice advice;

    /** 真实的异常处理器 —— 500 兜底 body 由它决定。 */
    @Autowired
    private GlobalExceptionHandler exceptionHandler;

    // ══════════════════ 期望值（Go 真源实测） ══════════════════

    private static Map<String, String> expected() {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : readExpected()) {
            if (line.isBlank()) {
                continue;
            }
            // ⚠️ limit 必须是 3：body 里还有 '|'（如 Content-Type 之外的内容）⇒
            //    limit=2 会让 parts[1] 变成「整行剩余部分」⇒ key 拼不出来。
            String[] parts = line.split("\\|", 3);
            map.put(parts[0] + "|" + parts[1], line);
        }
        return map;
    }

    /**
     * 读 {@code tools/api-envelope-probe/favorite/expected.txt}。
     *
     * <p>⚠️ 路径按候选逐个试：maven 跑测试时工作目录是<b>模块目录</b>，
     * 但 IDE / 直接跑时可能是仓库根 ⇒ 不写死一条路径。
     */
    private static List<String> readExpected() {
        List<Path> candidates = List.of(
                Path.of("tools/api-envelope-probe/favorite/expected.txt"),
                Path.of("../tools/api-envelope-probe/favorite/expected.txt"),
                Path.of("../../../tools/api-envelope-probe/favorite/expected.txt"));
        String override = System.getProperty("favorite.envelope.expected");
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try {
                    return Files.readAllLines(p, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new IllegalStateException("读不到 expected.txt：" + p.toAbsolutePath(), e);
                }
            }
        }
        throw new IllegalStateException(
                "找不到 expected.txt（试过 " + candidates + "）—— 先跑 tools/api-envelope-probe/favorite/run.sh");
    }

    private static String expectedLine(String kind, String name) {
        String line = expected().get(kind + "|" + name);
        if (line == null) {
            throw new IllegalStateException(
                    "expected.txt 里没有 " + kind + "|" + name + "（探针与本测试的用例集不一致）");
        }
        return normalizeContentTypeInLine(line);
    }

    /**
     * 把一行里的 <b>Content-Type 字段</b>做 OWS 归一（其余字段原样）。
     *
     * ⚠️ 冻结文件里存的是 Go 的<b>原样</b>（{@code application/json; charset=utf-8}，
     * {@code go-zero@v1.10.2/rest/internal/header/headers.go:17}），
     * 而 Spring 渲染成 {@code application/json;charset=utf-8}
     * ⇒ 比较前把<b>两边</b>都归一到同一形式（去掉分号后的空格）。
     * <p>⚠️ 只动第 4 段（Content-Type），<b>body 一律逐字节比较</b>。
     */
    private static String normalizeContentTypeInLine(String line) {
        String[] parts = line.split("\\|", 5);
        if (parts.length < 5) {
            return line;
        }
        parts[3] = normalizeContentType(parts[3]);
        return String.join("|", parts);
    }

    // ══════════════════ 产出 RAG 侧的规范化行 ══════════════════

    /**
     * 只为「接住 advice 写进响应头的 Content-Type」而存在的最小 {@link ServerHttpResponse}。
     *
     * <p>⚠️ Spring 7 的 spring-test **只有 reactive 版**的 {@code MockServerHttpResponse}
     * （{@code org.springframework.mock.http.server.reactive}）⇒ servlet 栈的
     * {@code ServerHttpResponse} 没有现成的 mock ⇒ 这里自己实现这 5 个方法。
     * <p>⚠️ 它**只**用来观察 advice 写了什么头，<b>不参与字节产出</b>
     * （字节由容器里那个 mapper 产出）⇒ 不会变成「拿自己的一份副本自证」。
     */
    private static final class CapturingResponse implements ServerHttpResponse {

        private final HttpHeaders headers = new HttpHeaders();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public OutputStream getBody() {
            return body;
        }

        @Override
        public void setStatusCode(HttpStatusCode status) {
            // 本测试不观察状态码（业务错误也是 HTTP 200）。
        }

        @Override
        public void flush() {
            // no-op
        }

        @Override
        public void close() {
            // no-op
        }
    }

    /** 一条响应在「状态码 / Content-Type / body」三个维度上的可观察形态。 */
    private record Shape(int status, String contentType, String body) {

        String asCaseLine(String name) {
            return "CASE|" + name + "|" + status + "|" + contentType + "|" + body;
        }
    }

    /**
     * 归一化 Content-Type 里参数分隔符后的<b>可选空白</b>（RFC 9110 §5.6.2 的 OWS）。
     *
     * <p>⚠️ <b>为什么需要它</b>：Go 侧写的是 {@code application/json; charset=utf-8}
     * （{@code go-zero@v1.10.2/rest/internal/header/headers.go:17}）
     * （<b>分号后有空格</b>），而 Spring 的 {@code MediaType#toString()} 渲染成
     * {@code application/json;charset=utf-8}（<b>没有空格</b>）⇒ 两者**字面不同**。
     * <p>⚠️ <b>这不是行为漂移</b>：HTTP 的媒体类型参数分隔符两侧的空白是<b>可选</b>（OWS），
     * 任何 HTTP 解析器都会归一化 ⇒ <b>没有任何客户端能分辨</b> ⇒ 属**表达层**差异
     * （AGENTS.md §3：差异只允许出现在表达层）。
     * <p>⚠️ <b>归一化只作用于 Content-Type 这一个字段</b>；<b>body 仍然逐字节比较</b>
     * ⇒ 不会把真实差异洗掉。
     */
    private static String normalizeContentType(String raw) {
        return raw == null ? "" : raw.replace("; ", ";");
    }

    /**
     * 用<b>真实</b>的 advice + <b>容器里那个</b> mapper 产出一条 {@link Shape}。
     *
     * <p>⚠️ 状态码恒为 200：favorite api 的业务错误也走 HTTP 200（Go 的 {@code httpx.OkJsonCtx} 语义），
     * 这正是 api.md §1.2「envelope：字段集合 + HTTP 恒 200」。
     */
    private Shape envelopeShape(Object data) throws Exception {
        CapturingResponse response = new CapturingResponse();
        Object wrapped = advice.beforeBodyWrite(data, null, MediaType.APPLICATION_JSON, null, null, response);
        byte[] bytes = objectMapper.writeValueAsBytes(wrapped);
        MediaType contentType = response.getHeaders().getContentType();
        return new Shape(200, normalizeContentType(contentType == null ? null : contentType.toString()),
                new String(bytes, StandardCharsets.UTF_8));
    }

    private String envelopeCase(String name, Object data) throws Exception {
        return envelopeShape(data).asCaseLine(name);
    }

    /** 产出一行 {@code OBS|...}：500 兜底。 */
    private String panicRecoverCase() throws Exception {
        ResponseEntity<Response> entity = exceptionHandler.handleAny(new IllegalStateException("boom"));
        CapturingResponse response = new CapturingResponse();
        Object wrapped = advice.beforeBodyWrite(entity.getBody(), null, MediaType.APPLICATION_JSON,
                null, null, response);
        byte[] bytes = objectMapper.writeValueAsBytes(wrapped);
        MediaType contentType = response.getHeaders().getContentType();
        return "OBS|panic_recover_500|" + entity.getStatusCode().value() + "|"
                + normalizeContentType(contentType == null ? null : contentType.toString())
                + "|" + new String(bytes, StandardCharsets.UTF_8);
    }

    // ══════════════════ 用例 ══════════════════

    @Test
    @DisplayName("🔴 响应 envelope + DTO 编码：与 Go 真源逐字节一致（9 条）")
    void envelopeAndDtoEncodingMatchGoByteForByte() throws Exception {
        // ① 成功：收藏夹。folderId 带 ,string ⇒ JSON 字符串；createdAt/updatedAt 不带 ⇒ JSON 数字。
        //    ⚠️ 用 2^53+1 的雪花 ID：写成裸 JSON 数字会丢精度（变成 ...992）⇒ 这条能抓住它。
        assertEquals(expectedLine("CASE", "ok_folder_snowflake"),
                envelopeCase("ok_folder_snowflake", new FavoriteFolder(
                        9007199254740993L, "技术", 1700000000123L, 1700000000456L)));

        // ② 零值：Go 的 encoding/json 不省略零值（没有 omitempty）⇒ 必须原样写出。
        assertEquals(expectedLine("CASE", "ok_folder_zero_values"),
                envelopeCase("ok_folder_zero_values", new FavoriteFolder(0L, "", 0L, 0L)));

        // ③ 成功：收藏条目（title/cover 带 ,optional）。
        assertEquals(expectedLine("CASE", "ok_item_full"),
                envelopeCase("ok_item_full", new FavoriteItem(
                        9007199254740993L, 7001L, "2001", "article", "标题",
                        "https://example.invalid/c.png", 1700000000123L)));

        // 🔴 ④ go-zero mapping 的 ,optional **不是** encoding/json 的 omitempty
        //    ⇒ title/cover 为空时**仍然出现**（值是 ""），不是 null、也不是消失。
        assertEquals(expectedLine("CASE", "ok_item_empty_optional"),
                envelopeCase("ok_item_empty_optional",
                        new FavoriteItem(1L, 2L, "3", "article", "", "", 0L)));

        // ⑤ 空集合是 [] 不是 null（Go 侧无条件 make）。
        assertEquals(expectedLine("CASE", "ok_folders_empty_list"),
                envelopeCase("ok_folders_empty_list", new GetFavoriteFoldersResp(new ArrayList<>())));

        // ⑥ 列表里有一个收藏夹。
        assertEquals(expectedLine("CASE", "ok_folders_one"),
                envelopeCase("ok_folders_one", new GetFavoriteFoldersResp(
                        List.of(new FavoriteFolder(7L, "收藏夹", 1L, 2L)))));

        // ⑦⑧⑨ 失败：data 为 nil ⇒ 仍是三字段 envelope（"data":null）。
        assertEquals(expectedLine("CASE", "err_invalid_param"),
                envelopeCase("err_invalid_param", new Response(
                        FavoriteErrorCode.ERROR_INVALID_PARAM.getCode(),
                        FavoriteErrorMessages.getMsg(FavoriteErrorCode.ERROR_INVALID_PARAM.getCode()), null)));
        assertEquals(expectedLine("CASE", "err_unauthorized"),
                envelopeCase("err_unauthorized", new Response(
                        FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode(),
                        FavoriteErrorMessages.getMsg(FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode()), null)));
        assertEquals(expectedLine("CASE", "err_not_found"),
                envelopeCase("err_not_found", new Response(
                        FavoriteErrorCode.ERROR_NOT_FOUND.getCode(),
                        FavoriteErrorMessages.getMsg(FavoriteErrorCode.ERROR_NOT_FOUND.getCode()), null)));
    }

    @Test
    @DisplayName("🔴 Go 侧「非空值 >2048」时的逐字节响应：与 Go 真源一致（真 HTTP）")
    void tooManyFormParamsMatchGoByteForByte() throws Exception {
        // Go 侧用**真的** httpx.Parse（maxFormParamCount = 2048）⇒ 第 2049 个非空值触发失败
        // ⇒ handler 落 200 + {code:5003, msg:"参数错误", data:null}。
        // ⇒ RAG 侧用**真 HTTP**（真 Tomcat + 真 filter 链 + 真 controller）走同一条路。
        HttpResponse<String> resp = getWithToken(ITEM_LIST + "?" + manyFormParams(2049));

        assertEquals(200, resp.statusCode(),
                "应用层拒绝也必须走 HTTP 200（Go httpx.OkJsonCtx 语义）；body=[" + resp.body() + "]");

        String contentType = normalizeContentType(resp.headers().firstValue("content-type").orElse(""));
        String actual = "CASE|err_too_many_form_params|" + resp.statusCode() + "|" + contentType
                + "|" + resp.body();
        assertEquals(expectedLine("CASE", "err_too_many_form_params"), actual);
    }

    @Test
    @DisplayName("✅ 「advice + 容器 mapper」== 真实管线（用真 HTTP 的 401 反证等价关系）")
    void advicePlusMapperEqualsRealPipeline() throws Exception {
        // ⚠️ 这条是本测试类的方法论守卫：
        //    上面那些用例用「advice + mapper」取字节（因为 DTO 形状要在那一层才定下来），
        //    ⇒ 必须证明这条路 == 真实管线写出的字节，否则那些断言就是「拿自己的一份副本自证」。
        // ⇒ 这里用**真 HTTP** 打一次无 token 的请求（真实 interceptor → 真实异常处理器 → 真实 advice → 真实转换器），
        //    再与「advice + mapper」产出的字节比。
        //
        // ⚠️ **只比 body + Content-Type，不比状态码**：
        //    真实路径是「JWT interceptor 拒 ⇒ HTTP 401」，而 Go 侧对应的
        //    `rest.WithJwt` 是 `writer.WriteHeader(401)` + **空 body** ⇒
        //    那个 body 形态差异**已登记**（DEV-FAV-API-001，2026-10-25 已签字）。
        //    ⇒ 本用例只用来证明「advice 产出的 body 字节 == 真实管线写出的 body 字节」。
        HttpResponse<String> resp = get(ITEM_LIST);

        Shape viaAdvice = envelopeShape(new Response(
                FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode(),
                FavoriteErrorMessages.getMsg(FavoriteErrorCode.ERROR_UNAUTHORIZED.getCode()), null));

        assertEquals(viaAdvice.body(), resp.body(),
                "「advice + 容器 mapper」必须与真实管线写出**逐字节相同**的 body，"
                        + "否则上面那些用例断言的不是生产路径");
        assertEquals(viaAdvice.contentType(),
                normalizeContentType(resp.headers().firstValue("content-type").orElse("")),
                "真实管线的 Content-Type 必须与 advice 钉死的 Go 取值一致（OWS 归一后）");
        assertEquals(expectedLine("CASE", "err_unauthorized").split("\\|", 5)[4], resp.body(),
                "真实管线的 401 body 必须与 Go 真源实测的 5004 envelope 逐字节一致");
    }

    @Test
    @DisplayName("📌 OBS：500 兜底的 body 形态 —— 两侧本该不同（差异消失时要重新评估）")
    void panicRecoverDiffersFromGoAndThatDifferenceIsRegistered() throws Exception {
        // 🔴 Go 侧：go-zero 的 handler.RecoverHandler 是 `w.WriteHeader(500)`
        //    ⇒ **空 body、无 Content-Type**（台账此前写的「500 + text/plain」是**错的**，已改正）。
        // RAG 侧：GlobalExceptionHandler#handleAny ⇒ 500 + JSON envelope。
        // ⇒ 两侧**本该不同** ⇒ 本断言的是「差异仍然存在」。
        String goSide = expectedLine("OBS", "panic_recover_500");
        String javaSide = panicRecoverCase();

        assertNotEquals(goSide, javaSide,
                "500 兜底的形态**变了** —— 请重新评估并更新台账与 "
                        + "docs/Intentional_Deviations/favorite/api.md（这不再是「两侧一致」，也不再是「未登记差异」）；"
                        + "\n  Go   = " + goSide + "\n  Java = " + javaSide);
    }

    // ══════════════════ 真 HTTP 辅助 ══════════════════

    private HttpClient client() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    private String token() {
        return JwtUtil.getToken(ACCESS_SECRET, System.currentTimeMillis() / 1000L, 3600L, 42L);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return client().send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getWithToken(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token())
                .GET()
                .build();
        return client().send(req, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 造 n 个**非空**的 query 参数，<b>第一个固定是 folderId</b>。
     *
     * <p>⚠️ ① 必须非空：Go 的 {@code httpx.GetFormValues} 数的是**非空值个数**（空值不计数，
     * {@code go-zero@v1.10.2/rest/httpx/util.go:22,48}）。
     * <p>⚠️ ② 必须带一个合法 {@code folderId}：{@code GetFavoritesByFolderReq} 的
     * {@code form:"folderId,string"} 是**必填** ⇒ 缺了它会先因「field "folderId" is not set」失败
     * ⇒ 那测的就不是「参数个数上限」而是「缺字段」了。
     * <p>⇒ 与 Go 侧探针的 {@code manyParams} **同形**（否则两侧不是同一个输入）。
     */
    private static String manyFormParams(int n) {
        StringBuilder b = new StringBuilder("folderId=123");
        for (int i = 1; i < n; i++) {
            b.append("&p").append(i).append("=v");
        }
        return b.toString();
    }

    @Test
    @DisplayName("🔴 探针与本测试的用例集必须完全一致（防止有人只改了一边）")
    void expectedFileAndThisTestCoverTheSameCases() {
        // ⚠️ 本测试逐条断言 expected.txt 里的行 ⇒ **少一条会 NPE**（测不出来）、**多一条不会报错**。
        // ⇒ 这里显式锁住「用例集」：Go 探针产的与 Java 侧产的必须是同一组。
        java.util.Set<String> expectedNames = new java.util.TreeSet<>();
        for (String line : readExpected()) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", 3);
            expectedNames.add(parts[0] + "|" + parts[1]);
        }

        java.util.Set<String> producedNames = new java.util.TreeSet<>(java.util.List.of(
                "CASE|ok_folder_snowflake",
                "CASE|ok_folder_zero_values",
                "CASE|ok_item_full",
                "CASE|ok_item_empty_optional",
                "CASE|ok_folders_empty_list",
                "CASE|ok_folders_one",
                "CASE|err_invalid_param",
                "CASE|err_unauthorized",
                "CASE|err_not_found",
                "CASE|err_too_many_form_params",
                "OBS|panic_recover_500"));

        assertEquals(expectedNames, producedNames,
                "Go 探针与 Java 侧的用例集必须一致（少一条 = 假绿，多一条 = 没人在比）");
    }
}
