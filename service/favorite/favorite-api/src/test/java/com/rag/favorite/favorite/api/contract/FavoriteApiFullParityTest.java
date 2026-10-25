package com.rag.favorite.favorite.api.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 🔴🔴🔴 <b>api 整域</b>的双侧活体对拍 —— favorite 域。
 *
 * <h2>它补的是什么缺口</h2>
 * <p>台账 {@code docs/DRIFT/favorite/api.md} §4 第一行写着「🔴 <b>未做</b>：双侧活体对拍
 * （真 Go favorite-api vs RAG）」⇒ 本类就是把它变成「✅ 已验」的那一步。
 * <p>已有的 {@code tools/api-envelope-probe/favorite/} 只验<b>响应字节</b>（且 RAG 侧是
 * <b>单侧断言</b>，没有 Go 侧参与）；本探针验的是<b>整个 HTTP 面</b>：
 * <pre>
 *   路由 / method / JWT 中间件 / 黑名单中间件 / <b>gRPC 往返</b> / 状态码 / 响应头
 * </pre>
 * <p>为什么这条链值得单独活体对：favorite-api 的<b>业务码不在 gRPC status 里</b>，
 * 而是在 <b>description 文案里</b>（Go {@code favoritecommon.BizCodeFromError}
 * 反查 ⇒ RAG {@code FavoriteGrpcError.bizCodeFromError}）⇒
 * 「api 的业务码对不对」这件事，<b>只有真的把请求从 HTTP 打通到 gRPC 再打回来</b>才能验。
 * 在此之前，它只被 rpc 级对拍（{@code tools/grpc-contract-probe/favorite/}）<b>间接</b>覆盖。
 *
 * <h2>两侧怎么跑</h2>
 * <ul>
 *   <li><b>Go 侧</b> = 真源 {@code service/favorite/api} + 真源 {@code service/favorite/rpc}
 *       的<b>逐字节复制件</b>（run.sh 逐个 sha256 校验）+ 真源自己的
 *       {@code rest.MustNewServer} + {@code zrpc.MustNewServer} ⇒ <b>真 HTTP server + 真 gRPC server</b>
 *       + <b>真 PG</b>；下游 user-rpc / article-rpc <b>故意不接</b>（死地址）
 *       ⇒ 走的是「下游不可用」这条契约分支。
 *       <br>（Go 出处：{@code service/favorite/api/favorite.go:29-37} 的 HTTP server 接线；
 *       {@code service/favorite/rpc/favorite.go:42-43} 的 gRPC server 接线）</li>
 *   <li><b>RAG 侧</b> = favorite-api / favorite-rpc 的<b>真 Spring Boot 进程</b>
 *       （{@code java -jar} 打出来的 fat jar ⇒ 就是可部署产物本身）
 *       + 真 Flyway + 真 MyBatis + <b>真 PG</b>；下游同样<b>故意不接</b>。</li>
 *   <li>两侧<b>共用同一份 seed.sql</b> ⇒ 读到同一批行 ⇒ 响应字节可比。</li>
 *   <li>两侧<b>共用同一个 Redis 容器</b> ⇒ 黑名单 key（{@code user:jwt_blacklist:<token>}）
 *       在**同一个命名空间**里 ⇒ 「同一个被拉黑的 token」两侧必然查到同一条记录
 *       ⇒ 这同时验了<b>黑名单 key 的拼法</b>（前缀、大小写敏感 TrimPrefix）。
 *       <br>（Go 出处：{@code service/favorite/api/internal/middleware/checkblacklist_middleware.go:36-45}）</li>
 * </ul>
 *
 * <h2>🔴 为什么用<b>同一个</b> HTTP 客户端打两侧</h2>
 * <p>与 rpc 级探针同款理由：换掉客户端，结论就不成立。请求矩阵、请求头、请求体、
 * 路径、query 全部由本类生成 ⇒ 客户端自身的差异被消掉，只留下<b>服务端</b>差异。
 *
 * <h2>expected.txt 的五档（语义见 README）</h2>
 * <ul>
 *   <li><b>SAME</b> —— HTTP 状态码 + Content-Type + <b>body 逐字节</b>全等</li>
 *   <li><b>SAME_CODE</b> —— 状态码全等 <b>且</b> 业务码全等（两侧 body 里都能取到码）</li>
 *   <li><b>SAME_BODY</b> —— 状态码全等 <b>且</b> body 全等（<b>Content-Type 允许不同</b>）</li>
 *   <li><b>SAME_STATUS</b> —— <b>只</b>要求状态码全等（body 与 Content-Type 允许不同；
 *       用于已签字的 body 形态偏离：Go 的 401 是<b>空 body</b> ⇒ Go 侧取不到业务码）</li>
 *   <li><b>OBS</b> —— 两侧 body <b>必须不同</b>（框架层差异）⇒ 变成一样反而是回归</li>
 *   <li><b>SKIP</b> —— 显式跳过，理由必须写在 expected.txt 的注释里</li>
 * </ul>
 *
 * <h2>⚠️ 为什么不比 Content-Length / Date</h2>
 * <p>{@code Date} 是<b>每次请求都会变</b>的（两侧各自取本机时钟）⇒ 比它必然假红；
 * {@code Content-Length} 由 body 长度决定，body 已经逐字节比了 ⇒ 冗余。
 * <p>⇒ 只比 <b>status + Content-Type + body</b>；完整响应头 key 集合另开一个 OBS 用例量化。
 *
 * <h2>⚠️ 用例顺序 = 副作用顺序</h2>
 * <p>写入类用例（改名 / 删除）会改库。矩阵里<b>所有只读用例在前、所有写入用例在后</b>，
 * 且最后补一个只读用例验证「写入确实生效」⇒ 顺序本身就是被冻结的契约的一部分。
 */
@EnabledIfEnvironmentVariable(named = "PROBE_GO_API", matches = ".+")
class FavoriteApiFullParityTest {

    // ══════════════════════════ 环境 ══════════════════════════

    private static String env(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + key + "（探针必须由 run.sh 编排，不要单跑本测试）");
        }
        return v;
    }

    /**
     * 本次矩阵要用的 token —— 全部由 Go 侧签发（探针的 {@code cmd/token}）。
     *
     * <p>⚠️ 为什么 token 必须能被 Go 侧的 {@code extractUserID} 还原成同一个 int64：
     * 真源 {@code service/favorite/api/internal/logic/favorite/helper.go:14-34}
     * 对 {@code json.Number} 走 {@code v.Int64()} 分支
     * （Go 侧验签器用 {@code jwt.WithJSONNumber()} ⇒ 数字 claim 是 {@code json.Number}）
     * ⇒ 探针签出的 token 必须是**数字** claim，否则两侧拿到的 userId 会分叉。
     */
    private static String token(String key) {
        return env("PROBE_TOKEN_" + key);
    }

    // ══════════════════════════ 用例模型 ══════════════════════════

    /**
     * 一个 HTTP 用例。
     *
     * @param id           用例 id（同时是 expected.txt 的主键）
     * @param method       HTTP method
     * @param path         相对 base 的路径（<b>不含</b> /favorite/v1 前缀，前缀在 base 里）
     * @param query        query string（可空）
     * @param auth         Authorization 头的<b>完整值</b>（null = 不带这个头）
     * @param body         请求体（null = 不带 body）
     * @param json         是否带 {@code Content-Type: application/json}
     * @param fullHeaders  是否记录<b>完整</b>响应头 key 集合（OBS 用例专用）
     */
    private record Case(String id,
                        String method,
                        String path,
                        String query,
                        String auth,
                        String body,
                        boolean json,
                        boolean fullHeaders) {
    }

    private static final String JSON = "application/json";

    /**
     * 请求矩阵。
     *
     * <p>⚠️ <b>顺序即契约</b>：只读用例全部在前，写入用例全部在后，最后一个只读用例验证写入生效。
     *
     * <p>每个用例的期望出口都标了 Go 出处行号 —— Go 的分支顺序决定了「哪个校验先触发」：
     * <ul>
     *   <li>路由与鉴权：{@code service/favorite/api/internal/handler/routes.go:16-59}
     *       （{@code rest.WithMiddlewares([CheckBlacklistMiddleware], …)} + {@code rest.WithJwt(…)}
     *        + {@code rest.WithPrefix("/favorite/v1")}）</li>
     *   <li>入参解析：go-zero v1.10.2 {@code rest/httpx/requests.go:53-82}（{@code Parse}）
     *       ⇒ <b>无条件</b>跑 {@code ParseJsonBody}，但 {@code withJsonBody}（同文件 :153-155）
     *       要求 {@code ContentLength > 0} <b>且</b> Content-Type 含 {@code application/json}</li>
     *   <li>业务分支：{@code service/favorite/api/internal/logic/favorite/*.go}</li>
     * </ul>
     */
    private static List<Case> matrix() {
        List<Case> cs = new ArrayList<>();

        // ─────────── 1. 鉴权组（JWT 中间件 + 黑名单中间件）───────────
        // Go: rest/handler/authhandler.go:53-71（Authorize）+ rest/token/tokenparser.go:80-84
        //     + service/favorite/api/internal/middleware/checkblacklist_middleware.go:22-50
        cs.add(new Case("a01_no_auth_header", "GET", "/folder/list", null, null, null, false, false));
        cs.add(new Case("a02_bad_signature", "GET", "/folder/list", null,
                "Bearer " + token("BADSIG"), null, false, false));
        cs.add(new Case("a03_expired_token", "GET", "/folder/list", null,
                "Bearer " + token("EXPIRED"), null, false, false));
        cs.add(new Case("a04_bearer_prefix_only", "GET", "/folder/list", null,
                "Bearer ", null, false, false));
        // a05: 小写 bearer 前缀。Go 的验签取法是大小写不敏感的
        //      （golang-jwt/jwt/v4 request/oauth2.go stripBearerPrefixFromTokenString）
        //      ⇒ 应当照样通过验签 ⇒ 走到业务分支（而不是 401）。
        cs.add(new Case("a05_lowercase_bearer", "GET", "/folder/list", null,
                "bearer " + token("VALID_USER1"), null, false, false));
        // a06: 已被拉黑（run.sh 在跑测试前把 user:jwt_blacklist:<token> 写进共用的 Redis）
        //      ⇒ Go 与 RAG 都必须 401。
        cs.add(new Case("a06_blacklisted_token", "GET", "/folder/list", null,
                "Bearer " + token("BLACKLISTED"), null, false, false));

        // ─────────── 2. 路由组（404 / 405 不经鉴权）───────────
        // Go: rest/engine.go:101-118 bindRoute 只把 chain 挂到匹配上的路由
        //     ⇒ 未注册路径恒 404，与有没有 token 无关
        cs.add(new Case("r01_unknown_path", "GET", "/nope", null, null, null, false, false));
        cs.add(new Case("r02_method_not_allowed_post", "POST", "/folder/list", null,
                "Bearer " + token("VALID_USER1"), null, false, false));
        cs.add(new Case("r03_method_not_allowed_get", "GET", "/folder/create", null,
                "Bearer " + token("VALID_USER1"), null, false, false));

        // ─────────── 3. 解析组（httpx.Parse 的每一档）───────────
        // p01: body 非 JSON ⇒ ParseJsonBody 失败 ⇒ 5003
        cs.add(new Case("p01_invalid_json_body", "POST", "/folder/create", null,
                "Bearer " + token("VALID_USER1"), "{not-json", true, false));
        // p02: name 是必填（无 optional tag）⇒ 缺失 ⇒ 5003
        cs.add(new Case("p02_missing_required_name", "POST", "/folder/create", null,
                "Bearer " + token("VALID_USER1"), "{}", true, false));
        // p03: name 类型不对（数字给 string 字段）⇒ 5003
        cs.add(new Case("p03_wrong_type_name", "POST", "/folder/create", null,
                "Bearer " + token("VALID_USER1"), "{\"name\":123}", true, false));
        // p04: GET /item/list 走 form 通道（types.GetFavoritesByFolderReq 是 form tag）⇒ 缺 folderId ⇒ 5003
        cs.add(new Case("p04_item_list_missing_folder_id", "GET", "/item/list", null,
                "Bearer " + token("VALID_USER1"), null, false, false));
        // p05: folderId 非数字 ⇒ 5003
        cs.add(new Case("p05_item_list_non_numeric_folder_id", "GET", "/item/list",
                "folderId=abc", "Bearer " + token("VALID_USER1"), null, false, false));
        // p06: 带了 body 但 **Content-Type 不是 json** ⇒ Go 的 withJsonBody 为 false
        //      ⇒ body 根本没被解析 ⇒ name 保持空 ⇒ 落到业务分支「收藏夹名称不能为空」(5202)
        //      ⇒ 这一档专门验「Content-Type 决定通道」这条语义。
        cs.add(new Case("p06_json_body_without_content_type", "POST", "/folder/create", null,
                "Bearer " + token("VALID_USER1"), "{\"name\":\"NoContentType\"}", false, false));
        // p07: DELETE 的 body 走 json 通道 ⇒ 缺 folderId ⇒ 5003
        cs.add(new Case("p07_delete_folder_missing_id", "DELETE", "/folder/delete", null,
                "Bearer " + token("VALID_USER1"), "{}", true, false));

        // ─────────── 4. 业务组（穿过 gRPC 往返 ⇒ 业务码靠 description 反查）───────────
        // b01: create ⇒ rpc ⇒ ensureUserExists ⇒ user-rpc 不在 ⇒ 5001
        //     （Go: service/favorite/rpc/internal/logic/helper.go:103-107）
        cs.add(new Case("b01_create_folder_downstream_absent", "POST", "/folder/create", null,
                "Bearer " + token("VALID_USER1"), "{\"name\":\"NewFolder\"}", true, false));
        // b02: 同上（item 侧）
        cs.add(new Case("b02_create_item_downstream_absent", "POST", "/item/create", null,
                "Bearer " + token("VALID_USER1"),
                "{\"folderId\":\"9001\",\"targetId\":\"t-9\",\"targetType\":\"post\"}", true, false));
        // b03: 收藏夹不存在 ⇒ 5201
        cs.add(new Case("b03_delete_folder_not_found", "DELETE", "/folder/delete", null,
                "Bearer " + token("VALID_USER1"), "{\"folderId\":\"9999\"}", true, false));
        // b04: 收藏不存在 ⇒ 5301
        cs.add(new Case("b04_delete_item_not_found", "DELETE", "/item/delete", null,
                "Bearer " + token("VALID_USER1"), "{\"favoriteId\":\"9999\"}", true, false));
        // b05: 改名不存在的收藏夹 ⇒ 5201
        cs.add(new Case("b05_update_folder_not_found", "POST", "/folder/update", null,
                "Bearer " + token("VALID_USER1"), "{\"folderId\":\"9999\",\"name\":\"Z\"}", true, false));
        // b06: 删别人的收藏夹（9003 属于 user 2002）⇒ 5005
        cs.add(new Case("b06_delete_folder_forbidden", "DELETE", "/folder/delete", null,
                "Bearer " + token("VALID_USER1"), "{\"folderId\":\"9003\"}", true, false));
        // b07: 读别人的收藏夹里的条目 ⇒ 5005
        cs.add(new Case("b07_list_items_forbidden", "GET", "/item/list", "folderId=9003",
                "Bearer " + token("VALID_USER1"), null, false, false));

        // ─────────── 5. 只读成功组（seed 写死 ⇒ 两侧字节可比）───────────
        cs.add(new Case("s01_list_folders", "GET", "/folder/list", null,
                "Bearer " + token("VALID_USER1"), null, false, false));
        cs.add(new Case("s02_list_items_of_9001", "GET", "/item/list", "folderId=9001",
                "Bearer " + token("VALID_USER1"), null, false, false));
        cs.add(new Case("s03_list_items_of_empty_folder", "GET", "/item/list", "folderId=9002",
                "Bearer " + token("VALID_USER1"), null, false, false));
        cs.add(new Case("s04_list_folders_empty_user", "GET", "/folder/list", null,
                "Bearer " + token("VALID_USER2"), null, false, false));

        // ─────────── 6. 响应头面（OBS：框架层差异，单独量化）───────────
        cs.add(new Case("h01_response_header_surface", "GET", "/folder/list", null,
                "Bearer " + token("VALID_USER1"), null, false, true));

        // ─────────── 7. 写入组（副作用；必须排在只读组之后）───────────
        cs.add(new Case("w01_update_folder_name", "POST", "/folder/update", null,
                "Bearer " + token("VALID_USER1"), "{\"folderId\":\"9002\",\"name\":\"BetaRenamed\"}", true, false));
        cs.add(new Case("w02_delete_item", "DELETE", "/item/delete", null,
                "Bearer " + token("VALID_USER1"), "{\"favoriteId\":\"7001\"}", true, false));
        cs.add(new Case("w03_delete_folder", "DELETE", "/folder/delete", null,
                "Bearer " + token("VALID_USER1"), "{\"folderId\":\"9002\"}", true, false));

        // ─────────── 8. 写入生效验证（必须最后）───────────
        cs.add(new Case("z01_list_folders_after_writes", "GET", "/folder/list", null,
                "Bearer " + token("VALID_USER1"), null, false, false));

        return cs;
    }

    // ══════════════════════════ 主流程 ══════════════════════════

    @Test
    @DisplayName("api 整域：Go 真源 favorite-api ↔ RAG favorite-api 双侧活体对拍")
    void apiFullParity() throws Exception {
        String goBase = env("PROBE_GO_API");
        String ragBase = env("PROBE_RAG_API");

        // ⚠️ 同一个客户端打两侧 ⇒ 消掉客户端自身差异（与 rpc 级探针同款纪律）
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Map<String, String> goOut = new LinkedHashMap<>();
        Map<String, String> ragOut = new LinkedHashMap<>();

        for (Case c : matrix()) {
            goOut.put(c.id(), render(call(client, goBase, c), c.fullHeaders()));
            ragOut.put(c.id(), render(call(client, ragBase, c), c.fullHeaders()));
        }

        writeLines(env("PROBE_OUT_GO"), goOut);
        writeLines(env("PROBE_OUT_RAG"), ragOut);
    }

    // ══════════════════════════ HTTP ══════════════════════════

    private static HttpResponse<String> call(HttpClient client, String base, Case c) throws Exception {
        String url = base + c.path() + (c.query() == null ? "" : "?" + c.query());
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30));
        if (c.auth() != null) {
            b.header("Authorization", c.auth());
        }
        if (c.json()) {
            b.header("Content-Type", JSON);
        }
        if (c.body() != null) {
            b.method(c.method(), HttpRequest.BodyPublishers.ofString(c.body(), StandardCharsets.UTF_8));
        } else {
            b.method(c.method(), HttpRequest.BodyPublishers.noBody());
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    // ══════════════════════════ 渲染 ══════════════════════════

    /**
     * 把一个响应渲染成可逐字节比对的字符串。
     *
     * <p>格式：{@code status=<code>|ct=<content-type 已归一>|body=<body>|biz=<业务码>}
     *
     * <p>🔴 <b>Content-Type 的 OWS 归一</b>：Go 写 {@code application/json; charset=utf-8}
     * （分号后<b>有</b>空格），Spring 渲染成 {@code application/json;charset=utf-8}（<b>无</b>空格）
     * ⇒ HTTP 媒体类型参数分隔符两侧的空白是可选的（RFC 9110 §5.6.2 的 OWS）
     * ⇒ 任何解析器都会归一化 ⇒ <b>无客户端能分辨</b> ⇒ 属表达层差异
     * ⇒ 只对 Content-Type 这一个字段做 OWS 归一，<b>body 仍逐字节比较</b>。
     */
    private static String render(HttpResponse<String> resp, boolean fullHeaders) {
        StringBuilder sb = new StringBuilder();
        sb.append("status=").append(resp.statusCode());
        sb.append("|ct=").append(normalizeContentType(resp.headers().firstValue("content-type").orElse("-")));
        sb.append("|body=").append(escape(resp.body()));
        sb.append("|biz=").append(bizCode(resp.body()));
        if (fullHeaders) {
            sb.append("|hkeys=").append(String.join(",", new TreeSet<>(
                    resp.headers().map().keySet())));
        }
        return sb.toString();
    }

    /**
     * 转义控制字符 —— 让渲染值能安全地塞进 **TSV**（{@code id\tvalue}）。
     *
     * <p>🔴 <b>为什么必须转义</b>：Go 的 404 body 是 {@code "404 page not found\n"}
     * （{@code go-zero@v1.10.2/rest/router/patrouter.go:93-97} 的 {@code handleNotFound}
     * → {@code http.NotFound} → {@code http.Error} 用 {@code fmt.Fprintln} 写出
     * ⇒ **带一个尾随换行**）
     * ⇒ 不转义就会把一行 TSV 撑成两行 ⇒ 解析出来的 case 会**少一个**，
     * 而且看起来「只是少了一个 case」，**不会报错** ⇒ 静默丢覆盖。
     *
     * <p>⇒ 转义只影响**记录方式**，两侧用<b>同一个</b>函数转义 ⇒ 不影响比较结果。
     */
    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String normalizeContentType(String ct) {
        return ct.replace(";", ";").replaceAll(";\\s*", ";").trim();
    }

    /** 业务码：从 body 的 JSON 里取 {@code "code":<数字>}；取不到就是 {@code -}。 */
    private static String bizCode(String body) {
        if (body == null || body.isEmpty()) {
            return "-";
        }
        Matcher m = Pattern.compile("\"code\"\\s*:\\s*(\\d+)").matcher(body);
        return m.find() ? m.group(1) : "-";
    }

    private static void writeLines(String path, Map<String, String> data) throws IOException {
        Path p = Paths.get(path);
        Files.createDirectories(p.getParent());
        List<String> lines = new ArrayList<>();
        data.forEach((k, v) -> lines.add(k + "\t" + v));
        Files.write(p, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }
}
