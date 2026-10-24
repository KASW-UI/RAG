package com.rag.favorite.favorite.rpc.fact;

import com.rag.favorite.favorite.rpc.config.FavoriteRpcProperties;
import com.rag.favorite.favorite.rpc.domain.FavoriteAuthorityFact;
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
import java.util.Collections;
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
 *   <tr><td>{@code Cache-Control: no-store}</td><td>:33 / :64</td><td>第一个字节就写</td></tr>
 *   <tr><td>{@code Authorization} 头必须<b>恰好 1 个</b>且 {@code Bearer } 前缀</td><td>:34-36</td>
 *       <td>遍历 {@code getHeaders} 全部取值</td></tr>
 *   <tr><td>恒定时间比较</td><td>:36</td><td>{@link MessageDigest#isEqual}</td></tr>
 *   <tr><td>不匹配 ⇒ 401 + body {@code "unauthorized"}</td><td>:37</td><td>逐字相同</td></tr>
 *   <tr><td>🔴 {@code r.URL.RawQuery != ""} ⇒ <b>404</b>（不是 400）</td><td>:40-43</td>
 *       <td>直接 404</td></tr>
 *   <tr><td>单次 lookup 的 3s 超时</td><td>:44</td><td>见下</td></tr>
 *   <tr><td>{@code ErrFavoriteFactUnavailable} ⇒ <b>404</b></td><td>:47-50</td>
 *       <td>{@link FavoriteExceptions.FactUnavailableException} ⇒ 404</td></tr>
 *   <tr><td>其它 error ⇒ <b>503</b> + body {@code "source unavailable"}</td><td>:51-56</td>
 *       <td>逐字相同</td></tr>
 *   <tr><td>成功 ⇒ 200 + {@code Content-Type: application/json}</td><td>:57-61</td>
 *       <td>同</td></tr>
 *   <tr><td>🔴 v2 额外要求 reader 支持 V2，否则 404</td><td>:75-79</td>
 *       <td>本实现<b>始终</b>支持 V2（见下）</td></tr>
 * </table>
 *
 * <h2>⚠️ 关于 Go 的 3s 单次超时</h2>
 * <p>Go 是 {@code context.WithTimeout(r.Context(), 3*time.Second)}，
 * 它约束的是 <b>DB 查询</b>。本实现的 {@code FavoriteAuthorityLookupService}
 * 走 MyBatis-Plus + HikariCP，<b>没有</b>把该 deadline 接到连接上。
 * ⇒ 这一条是<b>已知未对齐项</b>：Go 会在 3s 后放弃，Java 会等到 Hikari 的
 * {@code connection-timeout}。功能上仍是「查不到 → 404 / 出错 → 503」，
 * 但慢查询下的响应时长不同。记录在剩余问题里，不在这里假装对齐。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "favorite.fact.authority", name = "enabled", havingValue = "true")
public class FavoriteAuthorityEndpointConfig {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteAuthorityEndpointConfig.class);

    /** Go {@code http.go:32} 与 :63 的两个路径前缀。 */
    private static final String V1_PREFIX = "/internal/v1/favorite/facts/";
    private static final String V2_PREFIX = "/internal/v2/favorite/facts/";

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
     * 与 Go 的 {@code ListenOn: 0.0.0.0:8082} 一致。
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
            // Go: :33 / :64 —— 第一个字节就写，任何分支都不缓存
            resp.setHeader("Cache-Control", "no-store");

            // Go: :34-39 —— Authorization 必须恰好 1 个 + Bearer 前缀 + 恒定时间比较
            List<String> authorization = Collections.list(req.getHeaders("Authorization"));
            if (authorization.size() != 1 || !authorization.get(0).startsWith("Bearer ")) {
                writePlain(resp, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
                return;
            }
            byte[] presented = authorization.get(0).substring("Bearer ".length())
                    .getBytes(StandardCharsets.UTF_8);
            boolean tokenOk = presented.length == tokenBytes.length
                    && MessageDigest.isEqual(presented, tokenBytes);
            if (!tokenOk) {
                writePlain(resp, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
                return;
            }

            // 🔴 Go: :40-43 —— 只要带 query 就 404（不是 400）
            if (req.getQueryString() != null && !req.getQueryString().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Go: r.PathValue("producer") / r.PathValue("event_id")
            String[] pathParams = extractPathParams(req, v2);
            if (pathParams == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String producer = pathParams[0];
            String eventId = pathParams[1];

            FavoriteAuthorityFact.FactV1 fact;
            try {
                fact = v2
                        ? lookupV2(producer, eventId)
                        : lookupService.authoritativeFactV1(producer, eventId);
            } catch (FavoriteExceptions.FactUnavailableException e) {
                // Go: :47-50 —— ErrFavoriteFactUnavailable ⇒ 404
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            } catch (RuntimeException e) {
                // Go: :51-56 —— 其它 error ⇒ 503
                LOG.atError()
                        .addKeyValue("event", v2 ? "favorite.authority.v2.lookup_failed"
                                : "favorite.authority.lookup_failed")
                        .addKeyValue("error_code", "SOURCE_READ_FAILED")
                        .setCause(e)
                        .log("favorite authority lookup failed");
                writePlain(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "source unavailable");
                return;
            }

            // Go: :57-61 —— 200 + application/json
            resp.setContentType("application/json");
            byte[] body = FavoriteRpcSupport.STRICT_JSON.writeValueAsBytes(fact);
            resp.setStatus(HttpServletResponse.SC_OK);
            try {
                resp.getOutputStream().write(body);
            } catch (IOException e) {
                // Go: :58-60 —— Encode 失败只记日志，不改状态码
                LOG.atWarn()
                        .addKeyValue("event", v2 ? "favorite.authority.v2.response_interrupted"
                                : "favorite.authority.response_interrupted")
                        .addKeyValue("error_code", "WRITE_FAILED")
                        .setCause(e)
                        .log("favorite authority response interrupted");
            }
        }

        private FavoriteAuthorityFact.FactV1 lookupV2(String producer, String eventId) {
            return lookupService.authoritativeFactV2(producer, eventId);
        }


        /**
         * 从 {@code /internal/v{1,2}/favorite/facts/{producer}/{event_id}} 里取出两段路径值。
         *
         * <p>⚠️ <b>逐段 URL-decode</b>：Go 的 {@code r.PathValue} 返回的是<b>已解码</b>的值，
         * 而 {@code getRequestURI} 是原始形态。
         * ⇒ 未经解码会让含 {@code %xx} 的 producer/event_id 与 Go 不一致。
         *
         * @return {@code [producer, eventId]}；段数不对时返回 {@code null}
         */
        private static String[] extractPathParams(HttpServletRequest req, boolean v2) {
            String prefix = v2 ? V2_PREFIX : V1_PREFIX;
            String uri = req.getRequestURI();
            if (uri == null || !uri.startsWith(prefix)) {
                return null;
            }
            String rest = uri.substring(prefix.length());
            if (rest.isEmpty() || rest.indexOf('/') != rest.lastIndexOf('/')) {
                return null;      // 必须恰好两段
            }
            int slash = rest.indexOf('/');
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

        /**
         * Go 的 {@code http.Error(w, "unauthorized", 401)}（net/http 语义）：
         * <b>状态码 + 纯文本 body + {@code X-Content-Type-Options: nosniff}</b>。
         *
         * <p>⚠️ {@code http.Error} 还会写 {@code Content-Type: text/plain; charset=utf-8}。
         * 本实现在 Tomcat 下同样如此 ⇒ 与 Go 一致
         * （本仓已有一条关于 400 响应丢 Content-Type 的实测教训，见
         * {@code tools/difftest/check-plaintext400-headers.sh}）。
         */
        private static void writePlain(HttpServletResponse resp, int status, String body) throws IOException {
            resp.setStatus(status);
            resp.setContentType("text/plain; charset=utf-8");
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.getWriter().write(body);
        }
    }
}