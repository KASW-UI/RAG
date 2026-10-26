package com.rag.favorite.favorite.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * favorite 域专属配置 —— 对应 Go 侧三处配置源：
 * <ol>
 *   <li>{@code internal/config/config.go:18} {@code SubjectRefV2Facts bool `json:",optional"`}</li>
 *   <li>{@code cmd/fact-dispatch/main.go:36-38} 的三个环境变量</li>
 *   <li>{@code cmd/fact-authority/main.go:33-35} 的三个环境变量</li>
 *   <li>{@code cmd/fact-legacy-preflight/main.go:20} 的环境变量</li>
 * </ol>
 *
 * <p>Go 侧是<b>四个独立进程</b>，每个进程缺配置就自己退出；
 * RAG 侧合并成单 JVM（依据见 docs/FileTree/favorite/rpc.md §12.1），
 * ⇒ 「缺配置即退出」必须降级为「默认关闭 + 启动告警」，
 * 否则一个可选的下游（DC）没配就会让整个 favorite-rpc 起不来。
 */
@ConfigurationProperties(prefix = "favorite")
public class FavoriteRpcProperties {

    /**
     * 对应 Go {@code config.go:18} {@code SubjectRefV2Facts bool `json:",optional"`}。
     *
     * <p>Go 注释原文：{@code "Omitted is v1"} ⇒ <b>缺省必须是 false（v1）</b>。
     *
     * <p>🔴 它<b>只影响新建收藏</b>（{@code favorite_fact.go:286-289}），
     * 且撤回永远继承建立时冻结的 schema（{@code service/favorite/rpc/internal/model/favorite.go:16-18} 的注释）：
     * 「A retract always inherits the schema frozen in that favorite's assertion,
     * including after a process changes this option.」
     * ⇒ 这个开关可以在进程重启后翻转，**不会**破坏已存在的断言。
     *
     * <p>非 dev/test 环境下打开会被 {@code FavoriteSubjectRefGate} 拒绝启动
     * （等价 Go {@code service/favorite/rpc/favorite.go:31-35} 的 {@code os.Exit(2)}）。
     */
    private boolean subjectRefV2Facts = false;

    private final Fact fact = new Fact();

    public boolean isSubjectRefV2Facts() {
        return subjectRefV2Facts;
    }

    public void setSubjectRefV2Facts(boolean subjectRefV2Facts) {
        this.subjectRefV2Facts = subjectRefV2Facts;
    }

    public Fact getFact() {
        return fact;
    }

    /** 三个 {@code cmd/*} 的配置（合并后的编排层）。 */
    public static class Fact {

        private final Dispatch dispatch = new Dispatch();
        private final Authority authority = new Authority();
        private final LegacyPreflight legacyPreflight = new LegacyPreflight();

        public Dispatch getDispatch() {
            return dispatch;
        }

        public Authority getAuthority() {
            return authority;
        }

        public LegacyPreflight getLegacyPreflight() {
            return legacyPreflight;
        }
    }

    /**
     * 对应 Go {@code cmd/fact-dispatch/main.go:35-40,56}。
     *
     * <p>Go 的判定：{@code dsn == "" || endpoint == "" || token == ""} ⇒ 进程退出。
     * Java 侧 {@link #enabled} 默认 false ⇒ 整段不参与运行。
     */
    public static class Dispatch {

        private boolean enabled = false;

        /** Go {@code DC_PLATFORM_EVENT_URL}。 */
        private String dcEndpoint = "";

        /** Go {@code DC_PLATFORM_SERVICE_TOKEN}。 */
        private String dcToken = "";

        /**
         * Go {@code &http.Client{Timeout: 8 * time.Second}}（cmd/fact-dispatch/main.go:56）。
         *
         * <p>⚠️ 与 {@code Dispatcher} 的单次 10s deadline 是<b>两层</b>超时：
         * HTTP 8s 先到，Dispatcher 的 10s 后到。改这个值到 &gt;10s 会让 10s 那层
         * 永远等不到 HTTP 自己超时 ⇒ {@code cancel(true)} 成为唯一的兜底。
         */
        private java.time.Duration dcTimeout = java.time.Duration.ofSeconds(8);

        /**
         * Go {@code cmd/fact-dispatch/main.go:23} 的 {@code -once} flag。
         *
         * <p>true = 单次投递后退出（适合 CronJob）；false = 常驻循环
         * （1s ticker × 每轮最多 16 条，单条 10s deadline）。
         */
        private boolean once = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDcEndpoint() {
            return dcEndpoint;
        }

        public void setDcEndpoint(String dcEndpoint) {
            this.dcEndpoint = dcEndpoint;
        }

        public String getDcToken() {
            return dcToken;
        }

        public void setDcToken(String dcToken) {
            this.dcToken = dcToken;
        }

        public java.time.Duration getDcTimeout() {
            return dcTimeout;
        }

        public void setDcTimeout(java.time.Duration dcTimeout) {
            this.dcTimeout = dcTimeout;
        }

        public boolean isOnce() {
            return once;
        }

        public void setOnce(boolean once) {
            this.once = once;
        }

        /**
         * 对应 Go {@code cmd/fact-dispatch/main.go:36-40} 的三值判定：
         * {@code dsn == "" || endpoint == "" || token == ""}。
         *
         * <p>⚠️ Go 还要求 {@code FAVORITE_DATABASE_URL}；Java 侧没有这个环境变量
         * （数据源来自 {@code spring.datasource}），但 token 长度约束
         * （{@code Wire.DCEventSender.create} 要求 ≥32）由框架承担，见
         * {@code FavoriteFactDispatchScheduler} 的装配校验。
         */
        public boolean isConfigured() {
            return dcEndpoint != null && !dcEndpoint.isEmpty()
                    && dcToken != null && !dcToken.isEmpty();
        }
    }

    /**
     * 对应 Go {@code cmd/fact-authority/main.go:33-35} +
     * {@code internal/authority/http.go:27-30}。
     *
     * <p>Go 的判定：{@code dsn == "" || listen == "" || len(token) < 32} ⇒ 进程退出；
     * 另外 {@code authority.NewHandler} 本身也校验 {@code reader == nil || len(token) < 32}。
     */
    public static class Authority {

        private boolean enabled = false;

        /** Go {@code FAVORITE_AUTHORITY_TOKEN}；框架要求 ≥32 字节。 */
        private String token = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        /** 对应 Go {@code http.go:29} 的 {@code len(token) < 32}。 */
        public boolean isConfigured() {
            return token != null && token.length() >= 32;
        }
    }

    /**
     * 对应 Go {@code cmd/fact-legacy-preflight/main.go}。
     *
     * <p>Go 用<b>退出码</b>表达结论：0 = clear / 1 = 读取失败 / 2 = 未裁决（cmd/fact-legacy-preflight/main.go:38,50,24）。
     * Spring 进程内没有退出码（{@code System.exit} 会连带杀掉 gRPC 进程），
     * ⇒ 改为结构化报表，由人工/CI 判读日志。判定逻辑 {@code Clear()} 本身 1:1 保留。
     */
    public static class LegacyPreflight {

        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}