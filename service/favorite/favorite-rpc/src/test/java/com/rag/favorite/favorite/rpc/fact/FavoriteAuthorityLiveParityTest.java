package com.rag.favorite.favorite.rpc.fact;

import com.rag.favorite.favorite.rpc.service.FavoriteAuthorityLookupService;
import com.rag.favorite.favorite.rpc.support.RealGrpcPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 🔴🔴 权威读 HTTP 端点的**双侧活体对拍**（Java 侧）。
 *
 * <h2>为什么以前做不了（2026-10-25 第十二轮推翻）</h2>
 * <p>此前 favorite 域判「环境受限 ⇒ 拿不到双侧活体对拍」，理由是
 * 「Go 侧 favorite-rpc 依赖 etcd + Kafka + Redis + article-rpc / user-rpc」。
 * <b>那个理由只对 gRPC 整域成立</b>，对<b>权威读 HTTP 面不成立</b>：
 * <ul>
 *   <li>Go {@code cmd/fact-authority/main.go:32-37} 只要 PG + 端口 + token；</li>
 *   <li>RAG {@code FavoriteAuthorityEndpointConfig} 只要 PG；</li>
 *   <li>两边的读取链路（{@code AuthoritativeFavoriteFact} /
 *       {@code FavoriteAuthorityLookupService}）<b>只查 favorite_fact_outbox +
 *       favorite_item 两张表</b> ⇒ 不碰 user-rpc / article-rpc。</li>
 * </ul>
 *
 * <h2>本类做什么</h2>
 * <p>① 用**真 PG**（Testcontainers）+ **真 Flyway**（RAG 侧 schema）建表；
 * ② 应用**冻结的 seed.sql**（与 Go 侧<b>同一份</b>数据 ⇒ 两侧读到<b>同一批行</b>）；
 * ③ 用<b>真</b> {@link FavoriteAuthorityLookupService}（真 MyBatis）+ <b>真</b>
 * {@code FavoriteAuthorityServlet} 跑<b>与 Go 侧同一套请求矩阵</b>；
 * ④ 把结果与 <b>expected.txt</b>（= Go 侧实测值，冻结）逐条比对。
 *
 * <h2>为什么用 MockHttpServletRequest 而不是真 Tomcat</h2>
 * <p>🔴 favorite-rpc 是<b>纯 gRPC 进程，不带 web starter</b>（pom 注释明写
 * 「不引 spring-boot-starter-web，否则纯 gRPC 进程会被拉起 web 容器」）
 * ⇒ 本模块<b>没有</b>嵌入式容器可起 ⇒ 只能测到 <b>handler 层</b>。
 * <p>⇒ <b>证据边界（必须一并读）</b>：容器层（Tomcat 的重复头合并、405 错误页渲染、
 * chunked 传输等）<b>不在本探针覆盖内</b>。这属于 DEV-FAV-RPC-004 登记的
 * 「部署形态差异」的一部分，见 docs/DRIFT/favorite/rpc.md §19。
 * <p>⇒ 但 handler 层<b>不是</b> mock：{@code MockHttpServletRequest} 是 Spring 的
 * <b>真实实现类</b>（不是 Mockito mock），servlet 的路径解析 / 鉴权 / JSON 序列化
 * <b>全是真代码在跑</b>。
 *
 * <h2>变异验证</h2>
 * <p>本类断言的是「<b>逐条等值</b>」，不是「某个字段存在」⇒ 把 RAG 的
 * {@code writeGoError} 去掉尾随 {@code "\n"} 或把 {@code subject_ref} 改回三字段，
 * 本类必红 ⇒ 不是假绿。
 */
@Testcontainers
@org.testcontainers.junit.jupiter.EnabledIfDockerAvailable
@SpringBootTest(
        classes = com.rag.favorite.favorite.rpc.FavoriteRpcApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                // 绕过 Nacos：devh 的 @GrpcClient 在 Bean 初始化期就建 channel（不是懒建）。
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.user-rpc.address=static://127.0.0.1:1",
                "grpc.client.article-rpc.address=static://127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class FavoriteAuthorityLiveParityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("first_db")
                    .withUsername("admin")
                    .withPassword("password");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("favorite.fact.dispatch.enabled", () -> "false");
        registry.add("favorite.fact.authority.enabled", () -> "false");
        registry.add("favorite.fact.legacy-preflight.enabled", () -> "false");
        // 🔴 gRPC server 端口走**本域专用段**（RealGrpcPort），**不用 `port=0`**：
        //   `port=0` 让 OS 从临时端口段（macOS 49152–65535）里挑 ⇒ 那是 Docker 随机发布
        //   容器端口用的同一段 ⇒ 别的会话的容器先占住 0.0.0.0:P ⇒ 我们再绑同一个端口
        //   ⇒ EADDRINUSE ⇒ **整个测试类启动失败**（表现为「偶尔红、换个时间又绿」的抖动）。
        registry.add("grpc.server.port", RealGrpcPort::pick);
    }

    /** Go {@code http.go:28-30} 要求 token ≥ 32 字节。 */
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private static final String PRODUCER = "rtw.community.favorite";
    private static final int FAVORITE_ID = 3001;
    private static final int FAVORITE_ID_V2 = 3002;

    /** Go {@code favorite_authority.go:154}：event_id = {@code favorite.<id>.v<聚合版本>}。 */
    private static final String V1_EVENT_ID = "favorite.3001.v1";
    private static final String V2_EVENT_ID = "favorite.3002.v1";

    @Autowired
    private FavoriteAuthorityLookupService lookupService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ==================== 主用例 ====================

    @Test
    @Timeout(180)
    @DisplayName("🔴 权威读 HTTP 面：与 Go 侧同一矩阵、同一份数据、逐条等值")
    void authorityEndpointMatchesGoByteForByte() throws Exception {
        applySeed();

        Map<String, String> actual = new LinkedHashMap<>();
        for (Case c : cases()) {
            actual.put(c.id, run(c));
        }

        // ① 先把实际值落盘（run.sh 用它跟 Go 侧逐条比对）
        Path out = envPath("PROBE_OUT");
        if (out != null) {
            Files.createDirectories(out.getParent());
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : actual.entrySet()) {
                sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
            }
            Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
        }

        // ② 再与冻结的 expected.txt（= Go 侧实测）逐条断言
        Path expected = expectedPath();
        List<String> lines = Files.readAllLines(expected, StandardCharsets.UTF_8);
        Map<String, String> frozen = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            // ⚠️ expected.txt 是**三列**：case_id \t kind \t value
            //    （kind ∈ SAME / OBS / SKIP）⇒ 值在**第二个** tab 之后。
            String[] cols = line.split("\t", 3);
            if (cols.length < 3) {
                throw new IllegalStateException("expected.txt 格式错误（应为 3 列）：" + line);
            }
            frozen.put(cols[0], cols[2]);
        }
        // expected.txt 的 kind 列：SAME = 两侧必须逐字节相同；OBS = 两侧**必须不同**（已登记的差异）。
        Map<String, String> kinds = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", 3);
            if (parts.length >= 2) {
                kinds.put(parts[0], parts[1]);
            }
        }


        StringBuilder problems = new StringBuilder();
        for (Map.Entry<String, String> e : frozen.entrySet()) {
            String id = e.getKey();
            String want = e.getValue();
            String got = actual.get(id);
            if (got == null) {
                problems.append("  · ").append(id).append(" —— RAG 侧没跑这个 case\n");
                continue;
            }
            String kind = kinds.getOrDefault(id, "SAME");
            if ("SKIP".equals(kind)) {
                // SKIP = **容器层**行为（探针的 transport 是 Mock / recorder ⇒ 覆盖不到）
                //       ⇒ 只要求「两侧都跑过这个 case」，不要求字节相同。
                //       ⚠️ SKIP 不是「忽略」：它必须**显式登记理由**（expected.txt 的注释行）。
                continue;
            }
            if ("OBS".equals(kind)) {
                // OBS 段：断言的是「**两侧仍然不同**」⇒ 哪天变成一样了 ⇒ 必须失败、逼人重评。
                if (got.equals(want)) {
                    problems.append("  · ").append(id)
                            .append(" —— OBS 段期望 RAG 值 ≠ Go 值，但**两侧变成一样**了 ⇒ "
                                    + "已登记的差异消失了 ⇒ 请重评台账\n");
                }
            } else if (!got.equals(want)) {
                problems.append("  · ").append(id)
                        .append(" —— 与 Go 侧不一致\n      Go : ").append(want)
                        .append("\n      RAG: ").append(got).append('\n');
            }
        }
        for (String id : actual.keySet()) {
            if (!frozen.containsKey(id)) {
                problems.append("  · ").append(id).append(" —— expected.txt 里没有这个 case\n");
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(problems.length() == 0,
                "权威读 HTTP 面与 Go 侧不一致：\n" + problems);
    }

    // ==================== 矩阵 ====================

    private record Case(String id, String method, String path, String queryString, List<String> auth) {
    }

    private List<Case> cases() {
        List<String> auth = List.of("Bearer " + TOKEN);
        String v1 = "/internal/v1/favorite/facts/" + PRODUCER + "/" + V1_EVENT_ID;
        String v2 = "/internal/v2/favorite/facts/" + PRODUCER + "/" + V2_EVENT_ID;
        List<Case> cs = new ArrayList<>();
        cs.add(new Case("v1_ok", "GET", v1, null, auth));
        cs.add(new Case("v2_ok", "GET", v2, null, auth));
        cs.add(new Case("auth_missing", "GET", v1, null, List.of()));
        cs.add(new Case("auth_no_bearer_prefix", "GET", v1, null, List.of(TOKEN)));
        cs.add(new Case("auth_wrong_token", "GET", v1, null, List.of("Bearer wrong-token-value-000000000000")));
        cs.add(new Case("auth_empty_after_bearer", "GET", v1, null, List.of("Bearer ")));
        cs.add(new Case("auth_duplicate_header", "GET", v1, null,
                List.of("Bearer " + TOKEN, "Bearer " + TOKEN)));
        cs.add(new Case("auth_wrong_case_prefix", "GET", v1, null, List.of("bearer " + TOKEN)));
        cs.add(new Case("mux_path_clean_redirect", "GET",
                "/internal/v1//favorite/facts/" + PRODUCER + "/" + V1_EVENT_ID, null, auth));
        cs.add(new Case("mux_path_short", "GET", "/internal/v1/favorite/facts/" + PRODUCER, null, auth));
        cs.add(new Case("mux_path_long", "GET", v1 + "/extra", null, auth));
        cs.add(new Case("mux_wrong_prefix", "GET",
                "/internal/v9/favorite/facts/" + PRODUCER + "/" + V1_EVENT_ID, null, auth));
        cs.add(new Case("mux_method_not_allowed", "POST", v1, null, auth));
        cs.add(new Case("query_present", "GET", v1, "x=1", auth));
        cs.add(new Case("query_empty_value", "GET", v1, "x=", auth));
        cs.add(new Case("event_not_found", "GET",
                "/internal/v1/favorite/facts/" + PRODUCER + "/favorite.999999.v1", null, auth));
        cs.add(new Case("producer_mismatch", "GET",
                "/internal/v1/favorite/facts/rtw.other.producer/" + V1_EVENT_ID, null, auth));
        cs.add(new Case("v1_endpoint_on_v2_schema", "GET",
                "/internal/v1/favorite/facts/" + PRODUCER + "/" + V2_EVENT_ID, null, auth));
        cs.add(new Case("v2_endpoint_on_v1_schema", "GET",
                "/internal/v2/favorite/facts/" + PRODUCER + "/" + V1_EVENT_ID, null, auth));
        cs.add(new Case("event_id_too_long", "GET",
                "/internal/v1/favorite/facts/" + PRODUCER + "/" + "x".repeat(129), null, auth));
        cs.add(new Case("event_id_with_slash", "GET",
                "/internal/v1/favorite/facts/" + PRODUCER + "/a%2Fb", null, auth));
        return cs;
    }

    private String run(Case c) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(c.method(), c.path());
        req.setQueryString(c.queryString());
        for (String a : c.auth()) {
            req.addHeader("Authorization", a);
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        boolean v2 = c.path().startsWith("/internal/v2/");
        FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet servlet =
                new FavoriteAuthorityEndpointConfig.FavoriteAuthorityServlet(
                        TOKEN.getBytes(StandardCharsets.UTF_8), lookupService, v2);
        servlet.service(req, resp);
        return describe(resp);
    }

    /**
     * 把响应压成**单行、可逐字节比**的描述 —— 格式定义见**探针源码**
     * （{@code tools/authority-live-probe/favorite/go/service/favorite/rpc/cmd/probe/main.go}
     * 的 {@code describe()}）⇒ 两侧必须**逐字同格式**，否则逐行比对会误判。
     * <p>列顺序：status | content-type | cache-control | location | allow | nosniff | body
     */
    private static String describe(MockHttpServletResponse resp) {
        // ⚠️ getContentAsString() 声明 throws UnsupportedEncodingException（历史遗留签名）
        //    ⇒ 直接用字节数组 + UTF-8 解码，避免那个 checked exception。
        String body = new String(resp.getContentAsByteArray(), StandardCharsets.UTF_8);
        return String.join("|",
                String.valueOf(resp.getStatus()),
                nullToEmpty(resp.getHeader("Content-Type")),
                nullToEmpty(resp.getHeader("Cache-Control")),
                nullToEmpty(resp.getHeader("Location")),
                nullToEmpty(resp.getHeader("Allow")),
                nullToEmpty(resp.getHeader("X-Content-Type-Options")),
                goQuote(body));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /**
     * {@code strconv.Quote} 的等价转义 —— 必须与 Go 标准库的 {@code %q} 逐字相同
     * （Go 出处：{@code strconv/quote.go:125} 的 {@code Quote}；
     * {@code fmt/format.go:450} 的 {@code fmtQ} → {@code strconv.AppendQuote}）。
     * <p>本探针的 body 只含 ASCII 可见字符 + {@code \n} ⇒ 覆盖这几类即可；
     * 其它控制字符按 Go 的 {@code \xNN} 形式输出。
     */
    static String goQuote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20 || ch == 0x7f) {
                        sb.append(String.format("\\x%02x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ==================== 数据 ====================

    /**
     * 应用**冻结的 seed.sql** —— 与**探针侧**<b>同一份</b>文件 ⇒ 两侧读到<b>同一批行</b>。
     * <p>⚠️ seed.sql 由 {@code tools/authority-live-probe/favorite/run.sh} 的
     * {@code --emit-seed} 模式生成：走<b>真源</b>的
     * {@code service/favorite/rpc/internal/model/favorite_fact.go:270}（{@code InsertFavorite}）+
     * {@code service/favorite/rpc/internal/model/favorite_delivery.go:131}
     * （{@code DispatchFavoriteFactOnce}）⇒ payload 的信封形状与技术回执的 hash
     * <b>都是真源自己算的</b>。
     */
    private void applySeed() throws Exception {
        Path seed = repoRoot().resolve("tools/authority-live-probe/favorite/seed.sql");
        if (!Files.exists(seed)) {
            throw new IllegalStateException("缺少冻结的种子数据：" + seed);
        }
        for (String stmt : Files.readAllLines(seed, StandardCharsets.UTF_8)) {
            String s = stmt.trim();
            if (s.isEmpty() || s.startsWith("--")) {
                continue;
            }
            jdbcTemplate.execute(s);
        }
    }

    // ==================== 路径 ====================

    private static Path envPath(String key) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? null : Paths.get(v);
    }

    /** 冻结的期望值（= **探针侧**实测）—— 与本测试同目录的探针资源。 */
    private static Path expectedPath() {
        Path env = envPath("PROBE_EXPECTED");
        if (env != null) {
            return env;
        }
        return repoRoot().resolve("tools/authority-live-probe/favorite/expected.txt");
    }

    /** 向上找仓库根（含 {@code tools/authority-live-probe/favorite/}）。 */
    private static Path repoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (cur != null) {
            if (Files.isDirectory(cur.resolve("tools/authority-live-probe/favorite"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("找不到仓库根（user.dir=" + System.getProperty("user.dir") + "）");
    }
}
