package com.rag.article.article.api.client;

import com.rag.article.article.proto.ArticleServiceGrpc;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.observability.GoStyleRequestDeadline;
import com.rag.common.protection.GoStyleOutboundDeadlineInterceptor;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Context;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 {@code article-api} 的<b>按方向分档</b>出向 deadline 守门 ——
 * 域侧内联副本<b>剪除</b>后的<b>运行期证据</b>。
 *
 * <h2>它守的是什么</h2>
 * <p>本组件有两个出向方向，Go 侧的<b>客户端硬上界</b>不同（差 35 倍）：
 * <ul>
 *   <li>{@code article-rpc} 方向 = <b>70000ms</b> ——
 *       Go {@code service/article/api/etc/article-api.yaml:12} 的
 *       {@code ArticleRpcConf.Timeout: 70000}（<b>显式</b>）；</li>
 *   <li>{@code user-rpc} 方向 = <b>2000ms</b> ——
 *       Go 同文件 {@code :20-24} 的 {@code UserRpcConf} 段<b>没有</b> {@code Timeout}
 *       ⇒ ⇒ 走 zrpc 的 struct tag 默认
 *       {@code go-zero@v1.10.2/zrpc/config.go:31} 的
 *       {@code Timeout int64 `json:",default=2000"`}
 *       ⇒ ⇒ <b>逐字段对 yaml 时看不出这个差别</b>（AGENTS.md §3.3.1 点名的形态）。</li>
 * </ul>
 * ⇒ ⇒ 组件级<b>一个</b>上界表达不了 ⇒ ⇒ 必须<b>按下游 gRPC 服务全名分档</b>。
 *
 * <h2>🆕 2026-11-01 · G3 lane：域侧内联副本<b>剪除</b>（per-service 覆盖到位 ⇒ 副本冗余）</h2>
 * <p>此前 {@code UserRpcClient} 自带一份内联副本（{@code min(请求剩余, 2000)} + 域侧私有常量）
 * ⇒ ⇒ 那正是共享层棘轮
 * {@code GoStyleClientCallTimeoutSingleSourceGateTest} 判据 ④ 登记的<b>唯一</b>点位级例外
 * ⇒ ⇒ 副本数 = 漂移面 ⇒ ⇒ 本轮把它<b>剪掉</b>，上界改由共享层承担：
 * <ul>
 *   <li>共享层（唯一实现）：
 *       {@code GoStyleOutboundDeadlineInterceptor}（带 devh 的
 *       {@code @GrpcGlobalClientInterceptor} ⇒ 装在<b>每个</b>出向调用上）；</li>
 *   <li>域侧（只配参数）：
 *       {@code rag.protection.outbound-deadline.cap-millis-by-service}（本域
 *       {@code application.yml}）⇒ ⇒ <b>域侧零代码改动</b>。</li>
 * </ul>
 *
 * <h2>⚠️ 本类的<b>鉴别力</b>从哪来 —— 为什么必须用<b>裸 stub</b></h2>
 * <p>若经 {@code UserRpcClient.userRpc()} 取 stub ⇒ ⇒ 观测到的 2000ms
 * <b>可能</b>来自域侧内联副本 ⇒ ⇒ 守门成<b>假绿</b>（证明不了「共享层真的在起作用」）。
 * ⇒ ⇒ 本类一律用 {@code UserServiceGrpc.newBlockingStub(capped)}
 * —— <b>裸 stub</b>，<b>绕开</b>域侧 getter ⇒ ⇒ 观测到的 deadline
 * <b>只可能</b>来自共享层的 per-service 覆盖。
 *
 * <h2>为什么用<b>服务端观测</b>而不是干等</h2>
 * <p>{@code article} 方向的上界是 70s ⇒ 干等要 70 秒 ⇒ ⇒ 不可接受。
 * ⇒ ⇒ 改为在<b>服务端 handler 里</b>读
 * {@code io.grpc.Context.current().getDeadline()}
 * —— gRPC 会把客户端 deadline 通过 {@code grpc-timeout} 头传播到服务端
 * ⇒ ⇒ <b>一眼就读出</b>客户端给的是 2000 还是 70000 ⇒ ⇒ 既不慢、又能分辨
 * 「按方向分档」与「一刀切」。
 *
 * <h2>⚠️ 诚实边界</h2>
 * <ul>
 *   <li>本类<b>不起</b>真实 article-rpc / user-rpc、<b>不连</b> Nacos ⇒ 它证的是
 *       「<b>本域配的 per-service 表真被共享层读到了、且真在出向调用上生效</b>」，
 *       <b>不</b>证「整条生产链路的端到端超时表现」；</li>
 *   <li>「裸 stub 也经过全局拦截器」这一前提由<b>共享层</b>的装配面守门承担
 *       （devh 的 {@code AbstractChannelFactory#createChannel} 最后一步是
 *       {@code ClientInterceptors.interceptForward(channel, interceptors)}
 *       ⇒ ⇒ 拦截器装在 <b>channel</b> 上 ⇒ ⇒ 裸 stub 同样经过）；</li>
 *   <li>本类<b>不带</b>生产链路上的 {@code GoStyleGrpcClientBreakerInterceptor}。</li>
 * </ul>
 */
class ArticleApiOutboundDeadlinePerServiceCapGateTest {

    // ── Go 真值（逐条来自 Go 侧，见类 javadoc）────────────────────────────

    /** {@code user-rpc} 方向：Go {@code zrpc/config.go:31} 的 tag 默认。 */
    private static final long GO_USER_DIRECTION_CAP_MILLIS = 2000L;

    /** {@code article-rpc} 方向：Go {@code article-api.yaml:12} 的显式 Timeout。 */
    private static final long GO_ARTICLE_DIRECTION_CAP_MILLIS = 70_000L;

    /** 下游挂起时长（毫秒）—— 必须<b>大于</b>上界 ⇒ 才能分辨「有上界」与「没上界」。 */
    private static final long HANG_MILLIS = 8_000L;

    /** 真发调用那条用例的下界 —— 防止「秒失败」被误判成「超时生效」。 */
    private static final long MIN_EXPECTED_WAIT_MILLIS = 1_500L;

    /** 允许的额外耗时（毫秒）—— 排除「别的机制提前掐断」与墙钟抖动。 */
    private static final long SLACK_MILLIS = 1_500L;

    /** 显式请求预算（毫秒）—— 必须<b>远小于</b> 2s ⇒ 才能分辨「取更早者」与「取更晚者」。 */
    private static final long REQUEST_BUDGET_MILLIS = 300L;

    /**
     * 域侧私有 cap 的形态 ①：形如 {@code XXX_TIMEOUT_MILLIS = 2000L} 的静态常量。
     *
     * <p>⚠️ 与共享层棘轮判据 ④ 的同一套口径（{@code GoStyleClientCallTimeoutSingleSourceGateTest}
     * 里的 {@code PRIVATE_CAP_CONSTANT}）—— 域侧这条是<b>本域自己的</b>副本，
     * 不依赖共享层那条是否在跑。
     *
     * <p>⚠️ <b>只在【代码行】上匹配</b>：共享层那条用「剥注释与字符串」的口径，
     * 本条用「代码行」口径 ⇒ ⇒ 两者等价、且都不会被 javadoc 里提到常量名**误伤**。
     */
    private static final Pattern PRIVATE_CAP_CONSTANT = Pattern.compile(
            "\\b[A-Z][A-Z0-9_]*(?:TIMEOUT|CAP)[A-Z0-9_]*\\b");

    /** 本域的出向下游服务全名（= proto 的 {@code package.Service}）。 */
    private static final String USER_SERVICE_NAME = "user.UserService";
    private static final String ARTICLE_SERVICE_NAME = "article.ArticleService";

    private Server server;
    private ManagedChannel channel;

    /** 服务端观测到的「客户端给的 deadline 剩余毫秒」；{@code -1} = 服务端<b>没看到</b>任何 deadline。 */
    private final AtomicLong serverObservedUserDeadlineMillis = new AtomicLong(-1L);
    private final AtomicLong serverObservedArticleDeadlineMillis = new AtomicLong(-1L);

    @AfterEach
    void tearDown() {
        GoStyleRequestDeadline.clear();
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow();
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ① 配置面
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("① 域 yml 必须配 cap-millis-by-service 的【两个方向】的键，且组件级 cap-millis 仍然【不配】")
    void domainYmlDeclaresPerServiceCapForBothDirections() {
        String userRaw = rawYamlValue(
                "rag.protection.outbound-deadline.cap-millis-by-service." + USER_SERVICE_NAME);
        String articleRaw = rawYamlValue(
                "rag.protection.outbound-deadline.cap-millis-by-service." + ARTICLE_SERVICE_NAME);

        assertNotNull(userRaw,
                "🔴 本域 application.yml 必须配 rag.protection.outbound-deadline.cap-millis-by-service."
                        + USER_SERVICE_NAME + " ⇒ ⇒ 共享层 interceptor 的 per-service 表 miss ⇒ ⇒ "
                        + "回落组件级（本域刻意不配 ⇒ 等于 0 ⇒ 不启用）⇒ ⇒ user-rpc 方向【没有客户端硬上界】"
                        + "⇒ ⇒ 而 Go 侧恒有 2000ms（zrpc/config.go:31 的 tag 默认）⇒ ⇒ 那是一条真漂移。");
        assertNotNull(articleRaw,
                "🔴 本域 application.yml 必须配 rag.protection.outbound-deadline.cap-millis-by-service."
                        + ARTICLE_SERVICE_NAME + " ⇒ ⇒ 同上：article-rpc 方向会【没有】客户端硬上界"
                        + "⇒ ⇒ 而 Go 侧 article-api.yaml:12 显式配了 70000 ⇒ ⇒ 那是一条真漂移。");

        assertEquals(String.valueOf(GO_USER_DIRECTION_CAP_MILLIS), userRaw,
                "🔴 user-rpc 方向的上界必须是 Go 的 " + GO_USER_DIRECTION_CAP_MILLIS
                        + "ms（Go article-api.yaml:20-24 的 UserRpcConf 段【没有】Timeout ⇒ 走 zrpc/config.go:31 的 tag 默认）"
                        + "（实测 = " + userRaw + "）⇒ ⇒ 配别的值就是一条未登记的语义变更。");
        assertEquals(String.valueOf(GO_ARTICLE_DIRECTION_CAP_MILLIS), articleRaw,
                "🔴 article-rpc 方向的上界必须是 Go 的 " + GO_ARTICLE_DIRECTION_CAP_MILLIS
                        + "ms（Go article-api.yaml:12 的 ArticleRpcConf.Timeout: 70000 显式）"
                        + "（实测 = " + articleRaw + "）");

        assertNull(rawYamlValue("rag.protection.outbound-deadline.cap-millis"),
                "🔴 组件级 cap-millis 必须【仍然不配】⇒ ⇒ 配 2000 会把 article-rpc 方向改严 35 倍；"
                        + "配 70000 会把 user-rpc 方向放宽 35 倍 ⇒ ⇒ 两个值都不对 ⇒ ⇒ 组件级只能【不配】，"
                        + "上界全部由 cap-millis-by-service 分档表达。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ② 绑定面
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("② 域 yml 绑出的 per-service 表必须含两个方向的键值（含【带点号的键】不被拆坏）")
    void perServiceCapBindsFromDomainYml() {
        GoStyleProtectionProperties props = domainProperties(null);

        Map<String, Long> byService = props.getOutboundDeadline().getCapMillisByService();

        assertNotNull(byService, "🔴 per-service 表不得为 null");
        assertEquals(GO_USER_DIRECTION_CAP_MILLIS, byService.get(USER_SERVICE_NAME),
                "🔴 域 yml 的 rag.protection.outbound-deadline.cap-millis-by-service." + USER_SERVICE_NAME
                        + " 必须真的绑定到 GoStyleProtectionProperties.OutboundDeadline#capMillisByService"
                        + "⇒ ⇒ **键名写错 / 被 relaxed binding 拆坏**就是【配了不生效】"
                        + "（AGENTS.md §3.3.1）⇒ ⇒ 实测绑出的表 = " + byService);
        assertEquals(GO_ARTICLE_DIRECTION_CAP_MILLIS, byService.get(ARTICLE_SERVICE_NAME),
                "🔴 article 方向同上 ⇒ ⇒ 实测绑出的表 = " + byService);
        assertTrue(props.getOutboundDeadline().isEnabled(),
                "🔴 出向 deadline 传播开关默认开（Go 侧 zrpc/internal/config.go:15 的 Timeout 默认 true）"
                        + "⇒ ⇒ 本域不得把它关掉。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ③ 运行期 · user-rpc 方向（裸 stub ⇒ 观测到的上界只可能来自 per-service 覆盖）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("③ 【裸 stub + 真发调用】⇒ user-rpc 方向必须在 ≤2000ms 处 DEADLINE_EXCEEDED（Go 的 zrpc tag 默认）")
    void userDirectionIsCappedAtGoDefaultWithoutRequestBudget() throws Exception {
        Channel capped = startHangingUserRpc(domainProperties(null));

        long startedAt = System.nanoTime();
        StatusRuntimeException thrown = assertThrows(StatusRuntimeException.class,
                () -> UserServiceGrpc.newBlockingStub(capped)
                        .getUser(GetUserReq.newBuilder().setUid(1L).build()),
                "下游不响应时，article-api 必须靠【客户端硬上界】收口，而不是一直挂着");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;

        assertEquals(Status.Code.DEADLINE_EXCEEDED, thrown.getStatus().getCode(),
                "下游挂死 ⇒ 客户端 deadline 到点 ⇒ status 必须是 DEADLINE_EXCEEDED"
                        + "（Go 侧 ctx 到期同样是 DeadlineExceeded）");
        assertTrue(elapsedMillis >= MIN_EXPECTED_WAIT_MILLIS,
                "必须是「等到 deadline 到期」而不是「立刻失败」，实际耗时 = " + elapsedMillis + "ms");
        assertTrue(elapsedMillis <= GO_USER_DIRECTION_CAP_MILLIS + SLACK_MILLIS,
                "不应比 2s 客户端硬上界晚太多（说明上界没生效或被别的机制顶掉），实际耗时 = "
                        + elapsedMillis + "ms");

        long observed = serverObservedUserDeadlineMillis.get();
        assertTrue(observed > 0L && observed <= GO_USER_DIRECTION_CAP_MILLIS,
                "🔴 服务端观测到的客户端 deadline 必须在 (0, 2000] 毫秒内（实测 = " + observed
                        + "）⇒ ⇒ 这是【共享层 per-service 覆盖】的直接证据"
                        + "⇒ ⇒ 若为 null ⇒ 说明根本没设 deadline（per-service 表 miss ⇒ 回落组件级 0）"
                        + "⇒ ⇒ 若 > 2000 ⇒ 说明被别的方向的值污染了。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ④ 运行期 · article-rpc 方向（证明是【按方向分档】，不是一刀切）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("④ 【裸 stub】⇒ article-rpc 方向必须是 ≈70000ms ⇒ 【证明按方向分档，不是一刀切 2000】")
    void articleDirectionKeepsItsOwnSeventySecondCap() throws Exception {
        Channel capped = startEchoArticleRpc(domainProperties(null));

        // ⚠️ 服务端**立即**返回 ⇒ 不用干等 70s ⇒ 只观测「客户端给的是多少」
        GetArticleResponse resp = ArticleServiceGrpc.newBlockingStub(capped)
                .getArticle(GetArticleRequest.newBuilder().setArticleId("1").build());
        assertNotNull(resp, "下游应正常返回（这条只观测 deadline，不观测超时）");

        long observed = serverObservedArticleDeadlineMillis.get();
        assertTrue(observed > 0L,
                "🔴 服务端必须观测到客户端 deadline（实测 = null）⇒ ⇒ article-rpc 方向【没有】客户端硬上界"
                        + "⇒ ⇒ 而 Go 侧 article-api.yaml:12 显式配了 70000 ⇒ ⇒ 那是一条真漂移。");
        assertTrue(observed <= GO_ARTICLE_DIRECTION_CAP_MILLIS,
                "🔴 article-rpc 方向的上界不得大于 Go 的 " + GO_ARTICLE_DIRECTION_CAP_MILLIS
                        + "ms（实测 = " + observed + "）");
        assertTrue(observed > GO_USER_DIRECTION_CAP_MILLIS * 10L,
                "🔴 article-rpc 方向的上界实测 = " + observed + "ms ⇒ ⇒ 它必须明显大于 user-rpc 方向的 "
                        + GO_USER_DIRECTION_CAP_MILLIS + "ms ⇒ ⇒ **证明是按方向分档**"
                        + "⇒ ⇒ 若这里也落在 2000 附近 ⇒ 说明 per-service 覆盖没生效、"
                        + "组件级一刀切在管 ⇒ ⇒ 那就与 Go 真值差 35 倍。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑤ 运行期 · min 语义（Go 的 context.WithTimeout 取父子较早者）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 40, unit = TimeUnit.SECONDS)
    @DisplayName("⑤ 显式请求预算 300ms < 上界 2000ms ⇒ 实际 deadline 必须是【300ms】（钉的是 min，不是覆盖）")
    void requestBudgetWinsOverPerServiceCap() throws Exception {
        Channel capped = startEchoUserRpc(domainProperties(null));
        GoStyleRequestDeadline.begin(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(REQUEST_BUDGET_MILLIS));

        UserServiceGrpc.newBlockingStub(capped)
                .getUser(GetUserReq.newBuilder().setUid(1L).build());

        long observed = serverObservedUserDeadlineMillis.get();
        assertTrue(observed > 0L && observed <= REQUEST_BUDGET_MILLIS,
                "🔴 请求预算 " + REQUEST_BUDGET_MILLIS + "ms < 上界 " + GO_USER_DIRECTION_CAP_MILLIS
                        + "ms ⇒ 有效值必须是【更早者】= " + REQUEST_BUDGET_MILLIS + "ms（实测 = " + observed + "）"
                        + "⇒ ⇒ 若落在 2000 附近 ⇒ 说明变成了【覆盖】而不是【取更早者】"
                        + "⇒ ⇒ 那就与 Go 的 context.WithTimeout(parent, 2s) 方向相反了。");
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ⑥ 形态面 —— 域侧零副本（棘轮）
    // ────────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("⑥ 本域【不得】再有自带 cap 的内联副本：UserRpcClient 不得出现 withDeadlineAfter，也不得有私有 cap 常量")
    void domainSideMustNotCarryItsOwnOutboundCap() throws Exception {
        Path clientFile = moduleRoot().resolve("src").resolve("main").resolve("java")
                .resolve("com/rag/article/article/api/client/UserRpcClient.java");

        // ⚠️ 只认【代码行】：javadoc / 行注释里提到 withDeadlineAfter 或常量名都不算
        //    （否则 javadoc 里引用 Go 出处的 `Timeout int64 `json:",default=2000"`` 会误伤）
        List<String> codeLines = Files.readAllLines(clientFile, StandardCharsets.UTF_8).stream()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("*") && !l.startsWith("//") && !l.startsWith("/*"))
                .toList();

        boolean hasCodeLevelDeadlineCall = codeLines.stream()
                .anyMatch(l -> l.contains("withDeadlineAfter"));

        assertFalse(hasCodeLevelDeadlineCall,
                "🔴 UserRpcClient 里出现了【代码行】级 withDeadlineAfter ⇒ ⇒ 域侧又自带了一份"
                        + "「客户端硬上界」副本 ⇒ ⇒ 副本数 = 漂移面"
                        + "⇒ ⇒ 上界只该由共享层承担（域侧只配 cap-millis-by-service）"
                        + "⇒ ⇒ 共享层棘轮判据 ④ 也会判红 ⇒ ⇒ 别在这里复活那份封顶逻辑。");
        assertFalse(codeLines.stream().anyMatch(l -> PRIVATE_CAP_CONSTANT.matcher(l).find()),
                "🔴 UserRpcClient 里出现了「域侧私有 cap 常量」"
                        + "（形如 XXX_TIMEOUT_MILLIS / XXX_CAP 的全大写标识符）"
                        + "⇒ ⇒ 那就是【第二个上界来源】⇒ ⇒ 上界只该由共享层承担。");
    }

    @Test
    @DisplayName("⑥ 之二：剪除后 userRpc() 返回的是【裸 stub】⇒ 它自己【不带】deadline（上界由 channel 级的共享层拦截器施加）")
    void userRpcReturnsBareStubWithoutItsOwnDeadline() {
        UserRpcClient client = new UserRpcClient();
        io.grpc.Channel mockChannel = org.mockito.Mockito.mock(io.grpc.Channel.class);
        org.springframework.test.util.ReflectionTestUtils.setField(client, "userServiceBlockingStub",
                UserServiceGrpc.newBlockingStub(mockChannel));

        Deadline deadline = client.userRpc().getCallOptions().getDeadline();

        assertNull(deadline,
                "🔴 userRpc() 返回的 stub 上【不得】自带 deadline ⇒ ⇒ 域侧内联副本已剪净"
                        + "⇒ ⇒ 出向上界改由共享层的 GoStyleOutboundDeadlineInterceptor"
                        + "（channel 级 ⇒ 裸 stub 同样经过）在出向调用时施加。"
                        + "（实测 = " + deadline + "）");
    }

    // ================== 装置 ==================

    /**
     * 起一个 in-process {@code user-rpc} 下游：<b>挂起 {@link #HANG_MILLIS}</b> 才响应，
     * 并把<b>服务端观测到的</b>客户端 deadline 记下来。
     *
     * <p>⚠️ 装的是<b>生产拦截器</b>（{@code GoStyleOutboundDeadlineInterceptor}），
     * 且 stub 是<b>裸的</b>（不经 {@code UserRpcClient#userRpc()}）
     * ⇒ ⇒ 观测到的上界<b>只可能</b>来自共享层的 per-service 覆盖。
     */
    private Channel startHangingUserRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new UserServiceGrpc.UserServiceImplBase() {
                    @Override
                    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
                        serverObservedUserDeadlineMillis.set(observedDeadlineMillis());
                        try {
                            Thread.sleep(HANG_MILLIS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        responseObserver.onNext(GetUserResp.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        return cappedChannel(serverName, props);
    }

    /** 起一个 in-process {@code user-rpc} 下游：<b>立即</b>返回（只观测 deadline，不观测超时）。 */
    private Channel startEchoUserRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new UserServiceGrpc.UserServiceImplBase() {
                    @Override
                    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
                        serverObservedUserDeadlineMillis.set(observedDeadlineMillis());
                        responseObserver.onNext(GetUserResp.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        return cappedChannel(serverName, props);
    }

    /** 起一个 in-process {@code article-rpc} 下游：<b>立即</b>返回（只观测 deadline）。 */
    private Channel startEchoArticleRpc(GoStyleProtectionProperties props) throws Exception {
        String serverName = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(serverName)
                .addService(new ArticleServiceGrpc.ArticleServiceImplBase() {
                    @Override
                    public void getArticle(GetArticleRequest request,
                            StreamObserver<GetArticleResponse> responseObserver) {
                        serverObservedArticleDeadlineMillis.set(observedDeadlineMillis());
                        responseObserver.onNext(GetArticleResponse.newBuilder().build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();
        return cappedChannel(serverName, props);
    }

    /**
     * ⚠️ 与 devh 的装配动作同一条：devh 的 {@code AbstractChannelFactory#createChannel}
     * 最后一行是 {@code ClientInterceptors.interceptForward(channel, interceptors)}
     * ⇒ ⇒ 全局拦截器装在 <b>channel</b> 上 ⇒ ⇒ <b>裸 stub</b>同样经过它。
     */
    private Channel cappedChannel(String serverName, GoStyleProtectionProperties props) {
        channel = InProcessChannelBuilder.forName(serverName).build();
        return ClientInterceptors.intercept(channel, new GoStyleOutboundDeadlineInterceptor(props));
    }

    /**
     * 服务端侧读到的客户端 deadline 剩余毫秒。
     *
     * <p>⚠️ gRPC 会把客户端 deadline 通过 {@code grpc-timeout} 头传播到服务端
     * ⇒ ⇒ 服务端 {@code Context.current().getDeadline()} 读到的就是客户端给的那个。
     *
     * @return 剩余毫秒；<b>没有</b>任何 deadline 时返回 -1
     */
    private static long observedDeadlineMillis() {
        Deadline d = Context.current().getDeadline();
        if (d == null) {
            return -1L;
        }
        return d.timeRemaining(TimeUnit.MILLISECONDS);
    }

    // ================== 域配置读取 ==================

    /**
     * 用本域<b>自己的</b> {@code application.yml}（<b>磁盘上的那份</b>）绑出
     * {@link GoStyleProtectionProperties}。
     */
    private static GoStyleProtectionProperties domainProperties(Map<String, Object> envOverrides) {
        ConfigurableEnvironment env = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        try {
            for (PropertySource<?> ps : loader.load("domain-application-yml",
                    new FileSystemResource(domainYamlPath()))) {
                env.getPropertySources().addLast(ps);
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（" + domainYamlPath() + "）", e);
        }
        if (envOverrides != null && !envOverrides.isEmpty()) {
            env.getPropertySources().addFirst(new MapPropertySource("simulated-os-env", envOverrides));
        }
        ConfigurationPropertySources.attach(env);
        Binder binder = new Binder(ConfigurationPropertySources.get(env),
                new PropertySourcesPlaceholdersResolver(env));
        return binder.bind("rag.protection", GoStyleProtectionProperties.class)
                .orElseGet(GoStyleProtectionProperties::new);
    }

    /** 本域 {@code application.yml} 里某个键的<b>未解析</b>原值（占位符仍在）。 */
    private static String rawYamlValue(String key) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                    .load("domain-application-yml", new FileSystemResource(domainYamlPath()));
            for (PropertySource<?> ps : sources) {
                Object v = ps.getProperty(key);
                if (v != null) {
                    return String.valueOf(v);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("读不到本域的 application.yml（" + domainYamlPath() + "）", e);
        }
        return null;
    }

    /** 本域 {@code application.yml} 的<b>磁盘路径</b>。 */
    private static Path domainYamlPath() {
        return moduleRoot().resolve("src").resolve("main").resolve("resources").resolve("application.yml");
    }

    /** 定位本模块的源码根（含 {@code src/main/java}）—— <b>不依赖 surefire 的工作目录</b>。 */
    private static Path moduleRoot() {
        try {
            Path loc = Path.of(ArticleApiOutboundDeadlinePerServiceCapGateTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            Path p = Files.isDirectory(loc) ? loc : loc.getParent();
            if (p != null && "target".equals(p.getFileName().toString())) {
                p = p.getParent();
            }
            if (p != null && Files.isDirectory(p.resolve("src").resolve("main").resolve("java"))) {
                return p;
            }
        } catch (Exception ignored) {
            // 落到下面的兜底
        }
        Path cur = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && cur != null; up++) {
            if (Files.isDirectory(cur.resolve("src").resolve("main").resolve("java"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("定位不到本模块的源码根（src/main/java）"
                + "⇒ ⇒ 形态面判据失效（判据自己的问题，不是被测物的问题）");
    }
}
