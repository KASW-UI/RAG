package com.rag.favorite.favorite.rpc.fact;

import com.rag.common.communityfact.Wire;
import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.favorite.favorite.rpc.config.FavoriteRpcProperties;
import com.rag.favorite.favorite.rpc.exception.FavoriteExceptions;
import com.rag.favorite.favorite.rpc.service.FavoriteAuthorityLookupService;
import com.rag.favorite.favorite.rpc.service.FavoriteRpcSupport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 🔴 权威读 HTTP 端点 —— 对应 Go
 * {@code service/favorite/rpc/internal/authority/http.go:27-99} 的 {@code NewHandler}。
 *
 * <h2>🔴 wire 形态：Go favorite <b>自持 path 参数版</b>，不是通用 handler</h2>
 *
 * <pre>
 * GET /internal/v1/favorite/facts/{producer}/{event_id}     ← http.go:32
 * GET /internal/v2/favorite/facts/{producer}/{event_id}     ← http.go:63
 * </pre>
 *
 * <p>而 {@code com.rag.common.spring.AuthorityHandlerFactory} 是<b>另一个</b> handler：
 * <pre>
 * GET /internal/v1/community/facts?producer=…&amp;event_id=…    ← AuthorityHandlerFactory.java:30
 * </pre>
 * ⇒ <b>形态不同</b>（path vs query、路径前缀不同、返回体的 {@code subject_ref}
 * 形状也不同：v1 是三字段、Wire.AuthorityFact 是两字段）。
 *
 * <p>本实现<b>按 Go 原样写 favorite 专用 servlet</b>，理由：
 * L1 外部契约是「下游 binder（BTW）按这个 URL 取证据」，
 * 改成 query 形态会让所有下游调用方同时失配。
 *
 * <h2>🔴 逐条行为对照（全部来自 http.go）</h2>
 * <table border="1">
 *   <caption>v1 / v2 端点的行为</caption>
 *   <tr><th>行为</th><th>Go 行号</th><th>本实现</th></tr>
 *   <tr><td>🔴 <b>【mux 层】path 需要清理</b>（{@code //}、{@code /./}、{@code /../}）⇒ <b>301</b> + {@code Location}</td>
 *       <td>Go 1.25 {@code net/http/server.go} 的 {@code findHandler}：{@code cleanPath} 变了就 301</td>
 *       <td>逐字复刻 {@code cleanPath} + {@code escape(path, encodePath)} + {@code htmlEscape} ⇒ <b>先于鉴权</b></td></tr>
 *   <tr><td>🔴 <b>【mux 层】模式不匹配</b>（少一段 / 多一段）⇒ <b>404 page not found</b></td>
 *       <td>同上（{@code ServeMux} 匹配失败）</td>
 *       <td><b>先于鉴权</b>；段数判定写成 {@code slash < 0 || slash != lastIndexOf}
 *           （原写法在「没有 /」时两者都是 -1 ⇒ 会走进 {@code substring(0,-1)} 抛异常 ⇒ 500）</td></tr>
 *   <tr><td>{@code Cache-Control: no-store}</td><td>:33 / :64</td><td>第一个字节就写</td></tr>
 *   <tr><td>{@code Authorization} 头必须<b>恰好 1 个</b>且 {@code Bearer } 前缀</td><td>:34-36</td>
 *       <td>遍历 {@code getHeaders} 全部取值</td></tr>
 *   <tr><td>恒定时间比较</td><td>:36</td><td>{@link MessageDigest#isEqual}</td></tr>
 *   <tr><td>不匹配 ⇒ 401 + body {@code "unauthorized\n"}</td><td>:37</td>
 *       <td>逐字相同（<b>末尾的 {@code \n} 是 {@code http.Error} 的 {@code fmt.Fprintln} 带来的</b>）</td></tr>
 *   <tr><td>🔴 {@code r.URL.RawQuery != ""} ⇒ <b>404</b>（不是 400）</td><td>:40-43</td>
 *       <td>直接 404（body 与 mux 层同形状：{@code "404 page not found\n"}）</td></tr>
 *   <tr><td>单次 lookup 的 3s 超时</td><td>:44</td><td>见下（<b>已对齐</b>，U4 / DEV-FAV-RPC-005）</td></tr>
 *   <tr><td>{@code ErrFavoriteFactUnavailable} ⇒ <b>404</b></td><td>:47-50</td>
 *       <td>{@link FavoriteExceptions.FactUnavailableException} ⇒ 404</td></tr>
 *   <tr><td>其它 error ⇒ <b>503</b> + body {@code "source unavailable\n"}</td><td>:51-56</td>
 *       <td>逐字相同</td></tr>
 *   <tr><td>成功 ⇒ 200 + {@code Content-Type: application/json}</td><td>:57-61</td>
 *       <td>同</td></tr>
 *   <tr><td>🔴 v2 额外要求 reader 支持 V2，否则 404</td><td>:75-79</td>
 *       <td>本实现<b>始终</b>支持 V2（见下）</td></tr>
 * </table>
 *
 * <p>🔴 <b>整张表的双侧对拍由 {@code tools/authority-path-probe/favorite/} 守着</b>
 * （Go 侧用标准库复刻 {@code ServeMux} + {@code http.Error} / {@code http.NotFound} / {@code http.Redirect}，
 * 15 行矩阵逐行比对；Java 侧是 {@code FavoriteAuthorityPathParityTest} 的 12 条断言）。
 *
 * <h2>✅ 关于 Go 的 3s 单次超时（本轮已对齐，U4 / DEV-FAV-RPC-005）</h2>
 * <p>Go 是 {@code context.WithTimeout(r.Context(), 3*time.Second)}（{@code http.go:44} / {@code :80}），
 * 它约束的是 <b>DB 查询</b>：{@code m.conn.WithContext(ctx)} 把 ctx 带进 GORM ⇒
 * pg 驱动在 ctx 到点时发 CancelRequest ⇒ 查询在服务端立刻停止 ⇒
 * {@code query.Error != nil} ⇒ Go <b>原样上抛</b>（<b>不</b>包成 {@code ErrFavoriteFactUnavailable}）
 * ⇒ handler 走 {@code err != nil} 分支 ⇒ <b>503 "source unavailable"</b>。
 *
 * <p><b>🔴 Java 侧曾经缺这一环</b>（2026-10-23 台账 U4）：
 * {@code FavoriteAuthorityLookupService} 走 MyBatis-Plus + HikariCP，
 * <b>没有</b>把该 deadline 接到查询上 ⇒ 慢查询会一路顶到 Hikari 的
 * {@code connection-timeout}（<b>连接</b>超时，<b>量纲不同</b>）⇒ 响应时长与 Go 不一致。
 *
 * <p><b>本轮怎么修的</b>：仓库里<b>已有</b>成套机制，且本模块<b>已经注册</b>了
 * （{@code FavoriteRpcApplication:79-80}）：
 * <ul>
 *   <li>{@code DeadlineQueryTimeoutInjector}（MyBatis 拦截器）——
 *       拦 {@code StatementHandler.prepare}，按<b>当前线程</b>的
 *       {@link DeadlineState#remainingMillis()} 给每条 SQL 设
 *       {@code setQueryTimeout(秒)} ⇒ pgjdbc 到点发 CancelRequest ⇒
 *       <b>与服务端语义同一条，且连接保可用</b>（对比 {@code socketTimeout}：那是读超时，连接会报废）。</li>
 *   <li>{@code SqlTimeoutStatusTranslator}（MyBatis 拦截器）——
 *       把 CancelRequest 产生的 SQLState {@code 57014} 翻成
 *       {@code DEADLINE_EXCEEDED}。</li>
 * </ul>
 * ⇒ 本类只需在<b>调用点</b>把 deadline 绑到当前线程（{@link DeadlineState#bindToCurrent}），
 * 两条 SQL（outbox 取行 + 业务行核对）就<b>共用同一个 3s 预算</b> ——
 * 与 Go 的「一个 ctx 覆盖整次 lookup」逐字一致。
 *
 * <p><b>⚠️ 为什么这一段必须写在 servlet 里而不是 service 里</b>：
 * Go 的超时挂在 <b>handler</b>（{@code http.go:44}），
 * 即「一次 HTTP 请求 = 一个 3s 预算」；
 * 若下沉到 service，则 service 的<b>其它</b>调用方也会被套上这个预算（Go 侧没有这层）。
 * ⇒ 放在 servlet 与 Go 的绑定点一致。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "favorite.fact.authority", name = "enabled", havingValue = "true")
public class FavoriteAuthorityEndpointConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteAuthorityEndpointConfig.class);

    /** Go {@code http.go:32} 与 :63 的两个路径前缀。 */
    private static final String V1_PREFIX = "/internal/v1/favorite/facts/";
    private static final String V2_PREFIX = "/internal/v2/favorite/facts/";

    /**
     * 单次权威读 lookup 的查询预算 —— Go {@code http.go:44} / {@code :80} 的
     * {@code context.WithTimeout(r.Context(), 3*time.Second)}。
     *
     * <p>⚠️ <b>刻意不做成可配置项</b>：Go 侧是<b>硬编码常量</b>，
     * 做成配置就等于允许部署时改成别的值 ⇒ 那时 RAG 与 Go 的行为就不一致了
     * （配置项属于「多出来的表达层自由度」，不是对齐）。
     */
    private static final long GO_LOOKUP_TIMEOUT_MILLIS = 3_000L;

    private final FavoriteAuthorityLookupService lookupService;
    private final FavoriteRpcProperties properties;

    public FavoriteAuthorityEndpointConfig(FavoriteAuthorityLookupService lookupService,
                                           FavoriteRpcProperties properties) {
        this.lookupService = lookupService;
        this.properties = properties;
    }

    /**
     * 注册 favorite 专用 servlet。
     *
     * <p>⚠️ <b>整个类被 {@code @ConditionalOnProperty} 门禁</b>：
     * {@code favorite.fact.authority.enabled} 默认 false ⇒
     * <b>不注册任何 HTTP 端点</b>，favorite-rpc 保持「纯 gRPC、零 HTTP 端口」，
     * 与 Go 的 {@code ListenOn: 0.0.0.0:8082} 一致
     * （{@code service/favorite/rpc/etc/favorite.yaml:2}，配置结构
     * {@code service/favorite/rpc/internal/config/config.go:6}）。
     *
     * <p>⇒ 开启它需要给纯 gRPC 进程新增 web connector（Spring MVC），
     * 这是一处<b>部署形态变更</b>，见剩余问题。
     */
    @Bean
    public ServletRegistrationBean<HttpServlet> favoriteAuthorityServletRegistration() {
        FavoriteRpcProperties.Authority config = properties.getFact().getAuthority();
        if (!config.isConfigured()) {
            // Go: http.go:29 —— reader == nil || len(token) < 32 ⇒ 启动即失败
            // 单 JVM 里不退出，改为不注册端点 + ERROR 告警。
            LOG.error("FavoriteAuthorityEndpointConfig: token 未配置或短于 32 字节 ⇒ 不注册权威读端点");
            return new ServletRegistrationBean<>(new HttpServlet() {
            }, V1_PREFIX + "__disabled__");
        }
        byte[] tokenBytes = config.getToken().getBytes(StandardCharsets.UTF_8);
        return new ServletRegistrationBean<>(
                new FavoriteAuthorityServlet(tokenBytes, lookupService, false), V1_PREFIX + "*");
    }

    /** v2 端点：与 v1 共用同一个 Servlet 实例是<b>错的</b>（返回体形状不同），故独立注册。 */
    @Bean
    public ServletRegistrationBean<HttpServlet> favoriteAuthorityV2ServletRegistration() {
        FavoriteRpcProperties.Authority config = properties.getFact().getAuthority();
        if (!config.isConfigured()) {
            return new ServletRegistrationBean<>(new HttpServlet() {
            }, V2_PREFIX + "__disabled__");
        }
        byte[] tokenBytes = config.getToken().getBytes(StandardCharsets.UTF_8);
        return new ServletRegistrationBean<>(
                new FavoriteAuthorityServlet(tokenBytes, lookupService, true), V2_PREFIX + "*");
    }

    /** favorite 自持的权威读 servlet（path 参数版）。 */
    static class FavoriteAuthorityServlet extends HttpServlet {

        private static final long serialVersionUID = 1L;

        private final byte[] tokenBytes;
        private final FavoriteAuthorityLookupService lookupService;
        private final boolean v2;

        FavoriteAuthorityServlet(byte[] tokenBytes, FavoriteAuthorityLookupService lookupService, boolean v2) {
            this.tokenBytes = tokenBytes;
            this.lookupService = lookupService;
            this.v2 = v2;
        }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            // ═══════════════════════════════════════════════════════════════
            // 【Go mux 层】—— 发生在 handler 之前 ⇒ **也在鉴权之前**
            // ═══════════════════════════════════════════════════════════════
            //
            // Go: http.NewServeMux() 的 findHandler：（net/http/server.go:2695）
            //   ① path 需要清理（// 、 /./ 、 /../）⇒ **301** 重定向到清理后的路径；
            //   ② 模式不匹配 ⇒ **404 page not found**。
            // ⚠️ 两者都**先于** handler ⇒ 先于鉴权 ⇒
            //    「畸形 path + 无 token」在 Go 是 404（不是 401）。
            String rawUri = req.getRequestURI();
            String cleanedUri = goCleanPath(rawUri);
            if (!cleanedUri.equals(rawUri)) {
                // Go: mux 的 path 清理 ⇒ 301 Moved Permanently（net/http/server.go:2716）
                writeMovedPermanently(resp, goRedirectLocation(cleanedUri, req.getQueryString()));
                return;
            }

            // Go: r.PathValue("producer") / r.PathValue("event_id")（net/http/request.go:1469）
            // ⚠️ 用**清理后**的 path（Go 的匹配也发生在清理后的路径上）。
            String[] pathParams = extractPathParams(cleanedUri, v2);
            if (pathParams == null) {
                // Go: mux 模式不匹配 ⇒ http.NotFound（net/http/server.go:2735-2748）
                writeNotFound(resp);
                return;
            }

            // ═══════════════════════════════════════════════════════════════
            // 【Go handler 层】—— http.go:32 之后
            // ═══════════════════════════════════════════════════════════════
            // Go: :33 / :64 —— 第一个字节就写，任何分支都不缓存
            resp.setHeader("Cache-Control", "no-store");

            // Go: service/favorite/rpc/internal/authority/http.go:34-39 —— Authorization 必须恰好 1 个 + Bearer 前缀 + 恒定时间比较
            List<String> authorization = Collections.list(req.getHeaders("Authorization"));
            if (authorization.size() != 1 || !authorization.get(0).startsWith("Bearer ")) {
                writeGoError(resp, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
                return;
            }
            byte[] presented = authorization.get(0).substring("Bearer ".length())
                    .getBytes(StandardCharsets.UTF_8);
            boolean tokenOk = presented.length == tokenBytes.length
                    && MessageDigest.isEqual(presented, tokenBytes);
            if (!tokenOk) {
                writeGoError(resp, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
                return;
            }

            // 🔴 Go: service/favorite/rpc/internal/authority/http.go:40-43 —— 只要带 query 就 404（不是 400）
            if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
                writeNotFound(resp);
                return;
            }

            String producer = pathParams[0];
            String eventId = pathParams[1];

            // ⚠️ 类型是 Object：v1 返回 FavoriteAuthorityFact.FactV1（三字段 subject_ref），
            //    v2 返回 Wire.AuthorityFact（两字段 subject_ref）—— 两者是【不同的 wire 形状】。
            //    Jackson 按运行时类型序列化 ⇒ 声明成父类不会丢字段。
            Object fact;
            try {
                // ═══ Go: http.go:44 / :80 ═══
                // ctx, cancel := context.WithTimeout(r.Context(), 3*time.Second)
                // defer cancel()
                // fact, err := reader.AuthoritativeFavoriteFact(ctx, …)
                //
                // ⚠️ 这一行是 U4 / DEV-FAV-RPC-005 的修复本体：把 3s 预算绑到当前线程，
                //    让已注册的 DeadlineQueryTimeoutInjector 给本次 lookup 的每条 SQL
                //    设 setQueryTimeout ⇒ pg 侧到点发 CancelRequest ⇒ 查询被真正掐断。
                //    绑在【调用点】⇒ 与 Go 的绑定点（handler）一致，
                //    且本次 lookup 的多条查询共用同一个预算（Go 的一个 ctx 覆盖整次 lookup）。
                try (DeadlineState.Scope ignored =
                             DeadlineState.bindToCurrent(System.currentTimeMillis() + GO_LOOKUP_TIMEOUT_MILLIS)) {
                    fact = v2
                            ? lookupV2(producer, eventId)
                            : lookupService.authoritativeFactV1(producer, eventId);
                }
            } catch (FavoriteExceptions.FactUnavailableException e) {
                // Go: service/favorite/rpc/internal/authority/http.go:47-50 —— ErrFavoriteFactUnavailable ⇒ 404
                writeNotFound(resp);
                return;
            } catch (RuntimeException e) {
                // Go: :51-56 —— 其它 error ⇒ 503
                // ⚠️ 这里包含【超时】：Go 侧 DB 错误（含 ctx deadline）是原样上抛的
                //    （favorite_authority.go:215-217 `if query.Error != nil { return …, query.Error }`），
                //    不是 ErrFavoriteFactUnavailable ⇒ 同样落 503 分支 ⇒ 两侧一致。
                LOG.atError()
                        .addKeyValue("event", v2 ? "favorite.authority.v2.lookup_failed"
                                : "favorite.authority.lookup_failed")
                        .addKeyValue("error_code", "SOURCE_READ_FAILED")
                        .setCause(e)
                        .log("favorite authority lookup failed");
                writeGoError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "source unavailable");
                return;
            }

            // Go: service/favorite/rpc/internal/authority/http.go:57-61 —— 200 + application/json
            resp.setContentType("application/json");
            byte[] body = FavoriteRpcSupport.STRICT_JSON.writeValueAsBytes(fact);
            resp.setStatus(HttpServletResponse.SC_OK);
            try {
                resp.getOutputStream().write(body);
            } catch (IOException e) {
                // Go: service/favorite/rpc/internal/authority/http.go:58-60 —— Encode 失败只记日志，不改状态码
                LOG.atWarn()
                        .addKeyValue("event", v2 ? "favorite.authority.v2.response_interrupted"
                                : "favorite.authority.response_interrupted")
                        .addKeyValue("error_code", "WRITE_FAILED")
                        .setCause(e)
                        .log("favorite authority response interrupted");
            }
        }

        private Wire.AuthorityFact lookupV2(String producer, String eventId) {
            return lookupService.authoritativeFactV2(producer, eventId);
        }


        /**
         * 从 {@code /internal/v{1,2}/favorite/facts/{producer}/{event_id}} 里取出两段路径值。
         *
         * <p>⚠️ <b>逐段 URL-decode</b>：Go 的 {@code r.PathValue}（{@code net/http/request.go:1469}）
         * 返回的是<b>已解码</b>的值，
         * 而 {@code getRequestURI} 是原始形态。
         * ⇒ 未经解码会让含 {@code %xx} 的 producer/event_id 与 Go 不一致。
         *
         * <p>⚠️ <b>入参是「清理后」的 path</b>（Go 的模式匹配也发生在清理后的路径上）。
         *
         * @return {@code [producer, eventId]}；段数不对时返回 {@code null}
         */
        private static String[] extractPathParams(String cleanedUri, boolean v2) {
            String prefix = v2 ? V2_PREFIX : V1_PREFIX;
            if (cleanedUri == null || !cleanedUri.startsWith(prefix)) {
                return null;
            }
            String rest = cleanedUri.substring(prefix.length());
            if (rest.isEmpty()) {
                return null;
            }
            int slash = rest.indexOf('/');
            // 🔴 修复（原实现在这里会抛 StringIndexOutOfBoundsException）：
            //    原判据是 `rest.indexOf('/') != rest.lastIndexOf('/')` ⇒ **没有 '/' 时两者都是 -1**
            //    ⇒ 判据不成立 ⇒ 继续走 ⇒ `rest.substring(0, -1)` 抛异常 ⇒ HTTP 500。
            //    而 Go 的 ServeMux 对「少一段」的路径是**模式不匹配 ⇒ 404**。
            if (slash < 0 || slash != rest.lastIndexOf('/')) {
                return null;      // 必须恰好两段
            }
            String producer = decode(rest.substring(0, slash));
            String eventId = decode(rest.substring(slash + 1));
            if (producer.isEmpty() || eventId.isEmpty()) {
                return null;
            }
            return new String[]{producer, eventId};
        }

        private static String decode(String raw) {
            try {
                return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return raw;
            }
        }

        // ══════════════════ Go mux 层的 path 清理与重定向 ══════════════════

        /**
         * Go 的 {@code net/http.cleanPath}（{@code net/http/server.go:2629}）——
         * <b>等价复刻</b>（<b>不是逐字</b>：Go 1.25 的原实现多一条 fast path，见下）。
         *
         * <pre>
         * func cleanPath(p string) string {
         *     if p == "" { return "/" }
         *     if p[0] != '/' { p = "/" + p }
         *     np := path.Clean(p)
         *     if p[len(p)-1] == '/' &amp;&amp; np != "/" { np += "/" }
         *     return np
         * }
         * </pre>
         *
         * <p>⚠️ <b>Go 原实现里的 fast path 被本实现省略了，但语义相同</b>：
         * Go 在 {@code net/http/server.go:2639-2646} 多一层
         * {@code if len(p) == len(np)+1 &amp;&amp; strings.HasPrefix(p, np) { np = p } else { np += "/" }}。
         * 由于 {@code np = path.Clean(p)} 会去掉结尾的 {@code "/"}，
         * 该 fast path 的命中条件等价于「{@code p == np + "/"}」⇒ {@code np = p} 与
         * {@code np += "/"} <b>结果完全相同</b> ⇒ 省略它不改变任何可观察行为。
         * <p>⚠️ <b>输入必须是「未解码」的 path</b>（Go 的 mux 用的是 {@code r.URL.EscapedPath()}）⇒
         * {@code %2E%2E} 不会被当成 {@code ..} ⇒ 实测 Go 对
         * {@code /…/%2E%2E} 返回 <b>200</b>（不重定向）—— 本实现同款。
         *
         * <p>⚠️ <b>不能用 {@code Paths.get(p).normalize()}</b>：那是**文件系统**语义
         * （会保留前导 {@code ..}、且受平台分隔符影响）⇒ 与 Go 的纯词法语义不同。
         */
        private static String goCleanPath(String p) {
            if (p == null || p.isEmpty()) {
                return "/";
            }
            if (p.charAt(0) != '/') {
                p = "/" + p;
            }
            String np = goPathClean(p);
            if (p.charAt(p.length() - 1) == '/' && !np.equals("/")) {
                np += "/";
            }
            return np;
        }

        /** Go 的 {@code path.Clean}（纯词法：合并重复分隔符、消除 "."、回退 ".."）
         *     （{@code path/path.go:72}）。 */
        private static String goPathClean(String p) {
            Deque<String> segments = new ArrayDeque<>();
            for (String segment : p.split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".")) {
                    continue;
                }
                if (segment.equals("..")) {
                    segments.pollLast();
                    continue;
                }
                segments.addLast(segment);
            }
            if (segments.isEmpty()) {
                return "/";
            }
            StringBuilder sb = new StringBuilder();
            for (String segment : segments) {
                sb.append('/').append(segment);
            }
            return sb.toString();
        }

        /**
         * Go 的 {@code mux} 重定向目标：{@code (&url.URL{Path: path, RawQuery: rawQuery}).String()}
         * （{@code net/http/server.go:2403}）。
         *
         * <p>⚠️ <b>这里会二次转义</b>，而且是 Go 的<b>既有行为</b>（不是本实现引入的）：
         * Go 构造 {@code url.URL{Path: path}} 时<b>没有</b>设 {@code RawPath}
         * ⇒ {@code EscapedPath()} 走 {@code escape(u.Path, encodePath)}
         * ⇒ 把 path 里已有的 {@code %} 再转义成 {@code %25}。
         * ⇒ 实测：{@code /…//a%2Fb} ⇒ Location = {@code /…/a%252Fb}。
         * ⇒ <b>照抄</b>：偏离会让客户端跟随到不同路径 ⇒ 那是可观察的行为差异。
         */
        private static String goRedirectLocation(String cleanedPath, String rawQuery) {
            String location = goEscapePath(cleanedPath);
            if (rawQuery != null && !rawQuery.isEmpty()) {
                location = location + "?" + rawQuery;
            }
            return hexEscapeNonAscii(location);
        }

        /**
         * Go 的 {@code escape(s, encodePath)}（{@code net/url/url.go:292}）。
         *
         * <p>Go 的 {@code shouldEscape(c, encodePath)}：{@code encodePath} 模式下
         * <b>只有 {@code ?} 需要转义</b>（§3.3：{@code $ &amp; + , / : ; = @} 都保留），
         * unreserved（{@code A-Za-z0-9-_.~}）也不转义；其余一律百分号转义。
         */
        private static String goEscapePath(String s) {
            StringBuilder sb = new StringBuilder();
            for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
                int c = b & 0xFF;
                if (goShouldEscapePath(c)) {
                    sb.append('%');
                    sb.append(Character.toUpperCase(Character.forDigit(c >> 4, 16)));
                    sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
                } else {
                    sb.append((char) c);
                }
            }
            return sb.toString();
        }

        private static boolean goShouldEscapePath(int c) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                return false;
            }
            switch (c) {
                case '-':
                case '_':
                case '.':
                case '~':                       // §2.3 unreserved (mark)
                case '$':
                case '&':
                case '+':
                case ',':
                case '/':
                case ':':
                case ';':
                case '=':
                case '@':                       // §2.2 reserved —— encodePath 下都保留
                    return false;
                default:
                    return true;                // 含 '?' 与全部非 ASCII
            }
        }

        /** Go 的 {@code hexEscapeNonASCII}：非 ASCII 字节转成 %XX（ASCII 原样）。
         *     （{@code net/http/http.go:143}） */
        private static String hexEscapeNonAscii(String s) {
            StringBuilder sb = new StringBuilder();
            for (byte b : s.getBytes(StandardCharsets.UTF_8)) {
                int c = b & 0xFF;
                if (c < 0x80) {
                    sb.append((char) c);
                } else {
                    sb.append('%');
                    sb.append(Character.toUpperCase(Character.forDigit(c >> 4, 16)));
                    sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
                }
            }
            return sb.toString();
        }

        /** Go 的 {@code htmlEscape}（Go 1.25 的 htmlReplacer 在
         *     {@code net/http/server.go:2458}，不在 net/http/html.go ——
         *     该文件在 Go 1.25 已不存在）。 */
        private static String htmlEscape(String s) {
            return s.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&#34;")
                    .replace("'", "&#39;");
        }

        // ══════════════════ Go 的三个错误响应形状 ══════════════════

        /**
         * Go 的 {@code http.Error(w, msg, code)}（{@code net/http/server.go:2337}）：
         * <b>状态码 + {@code Content-Type: text/plain; charset=utf-8}
         * + {@code X-Content-Type-Options: nosniff} + body = {@code msg + "\n"}</b>。
         *
         * <p>🔴 <b>body 末尾那个 {@code "\n"} 是 {@code fmt.Fprintln} 带来的</b> ——
         * 少了它就是**逐字节不一致**（原实现只写了 {@code body}，没有换行）。
         *
         * <p>⚠️ {@code http.Error} 会先 {@code h.Del("Content-Length")}，
         * 且 {@code http.Error} 与 {@code http.NotFound} 都走这条路径。
         */
        private static void writeGoError(HttpServletResponse resp, int status, String message)
                throws IOException {
            resp.setStatus(status);
            resp.setContentType("text/plain; charset=utf-8");
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.getWriter().write(message + "\n");
        }

        /**
         * Go 的 {@code http.NotFound(w, r)}（{@code net/http/server.go:2358}）⇒
         * {@code http.Error(w, "404 page not found", 404)}。
         *
         * <p>⚠️ Go 的 mux 层「模式不匹配」与 handler 层的
         * 「带 query」/「ErrFavoriteFactUnavailable」<b>用的是同一个 404 形状</b>
         * ⇒ 本实现三条 404 分支统一走这里。
         */
        private static void writeNotFound(HttpServletResponse resp) throws IOException {
            writeGoError(resp, HttpServletResponse.SC_NOT_FOUND, "404 page not found");
        }

        /**
         * Go 的 {@code http.Redirect(w, r, url, http.StatusMovedPermanently)}
         * （{@code net/http/server.go:2403}）。
         *
         * <p>🔴 <b>body 末尾是<b>两个</b> {@code "\n"}</b>：Go 的实现是
         * {@code body := "<a href=\"…\">Moved Permanently</a>.\n"} 再交给
         * {@code fmt.Fprintln(w, body)} ⇒ 又追加一个换行 ⇒ 实测 body 以 {@code "\n\n"} 结尾。
         *
         * <p>⚠️ {@code http.Redirect} 对 GET 会写 {@code Content-Type: text/html; charset=utf-8}。
         */
        private static void writeMovedPermanently(HttpServletResponse resp, String location)
                throws IOException {
            resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            resp.setHeader("Location", location);
            resp.setContentType("text/html; charset=utf-8");
            resp.getWriter().write("<a href=\"" + htmlEscape(location)
                    + "\">Moved Permanently</a>.\n\n");
        }
    }
}