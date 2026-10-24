package com.rag.comment.comment.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * comment 域业务配置 —— 对应 Go 端 {@code service/comment/rpc/internal/config/config.go:9-22}
 * 与 {@code etc/comment.yaml:23-40} 中<b>不属于</b> Spring 标准自动配置的那部分。
 *
 * <p>刻意<b>不</b>映射进来的字段（每一条都有理由，避免「映射了反而像生效了」）：
 * <ul>
 *   <li>{@code ListenOn} / {@code Etcd} —— 由 {@code grpc.server.port} 与 Nacos 承担；</li>
 *   <li>{@code DB.DataSource} —— 由 {@code spring.datasource} 承担；</li>
 *   <li>{@code BizRedis} —— 由 {@code spring.data.redis} 承担；</li>
 *   <li>{@code Log} / {@code Telemetry} —— 由 logging.* 与 otel.* 承担；</li>
 *   <li>{@code MessageRpc} / {@code ArticleRpc} —— 由 {@code grpc.client.*} + Nacos 承担。</li>
 * </ul>
 *
 * <p><b>黑名单词库</b>是唯一被「外提」的实现细节：Go 把它硬编码在
 * {@code service_context.go:29}（{@code blackwords := []string{"傻逼", "我草"}}），
 * Java 侧放进配置以便运营调整。<b>默认值与 Go 逐字一致</b>，
 * 不配置时行为与 Go 完全相同。
 */
@ConfigurationProperties(prefix = "comment")
public class CommentRpcProperties {

    private final Kafka kafka = new Kafka();

    private List<String> sensitiveWords = List.of("傻逼", "我草");

    private final Fact fact = new Fact();

    private final Authority authority = new Authority();

    /** Go {@code service_context.go:29} 的 {@code blackwords := []string{"傻逼", "我草"}}。 */
    public List<String> getSensitiveWords() {
        return sensitiveWords;
    }

    public void setSensitiveWords(List<String> sensitiveWords) {
        this.sensitiveWords = sensitiveWords;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Fact getFact() {
        return fact;
    }

    public Authority getAuthority() {
        return authority;
    }

    /** 对应 Go {@code etc/comment.yaml:23-35} 的 KqPusherConf + KqConsumerConf。 */
    public static class Kafka {

        /**
         * Go {@code KqPusherConf.Topic: comment-audit-topic}，
         * 同时也是 {@code KqConsumerConf.Topic}。
         *
         * <p>★ 「生产与消费同一个 topic」是 Go 的现状，<b>不是笔误</b>：
         * CreateComment 把 {@code CommentKafkaMsg} 推上去，AuditConsumer 再消费回来做
         * 敏感词判定与落库。改成两个 topic 会让链路断掉。
         */
        private String auditTopic = "comment-audit-topic";

        /** 对应 Go {@code KqConsumerConf.Consumers: 8}（同进程 8 个并发 Consume）。 */
        private int auditConcurrency = 8;

        /**
         * 对应 go-queue {@code Pusher} 的<b>同步阻塞</b>写语义。
         *
         * <p>⚠️ 这是本域最容易被「优化掉」的一处 L1 行为：
         * create_comment_logic.go:126-132 在 {@code Push} 失败时返回
         * {@code ERROR_KAFKA_PUSH(5009)}，即「Kafka 失败 ⇒ CreateComment 失败」。
         * Java 的 {@code KafkaTemplate.send} 是<b>异步</b>的，若不等结果，
         * 就会变成「Kafka 挂了但评论创建照样返回成功」—— 方向相反的漂移。
         * ⇒ {@code CommentAuditPublisher} 必须 {@code future.get(timeout)}。
         *
         * <p>上界取 kafka-go {@code Writer} 的默认 {@code WriteTimeout} 10s。
         */
        private Duration publishTimeout = Duration.ofSeconds(10);

        public String getAuditTopic() {
            return auditTopic;
        }

        public void setAuditTopic(String auditTopic) {
            this.auditTopic = auditTopic;
        }

        public int getAuditConcurrency() {
            return auditConcurrency;
        }

        public void setAuditConcurrency(int auditConcurrency) {
            this.auditConcurrency = auditConcurrency;
        }

        public Duration getPublishTimeout() {
            return publishTimeout;
        }

        public void setPublishTimeout(Duration publishTimeout) {
            this.publishTimeout = publishTimeout;
        }
    }

    /** 对应 Go {@code cmd/fact-dispatch/main.go:52-77} 的进程配置。 */
    public static class Fact {

        /**
         * 默认 <b>false</b>。
         *
         * <p>Go 侧 fact-dispatch 是<b>独立进程</b>，缺 DC 配置时它自己退出，
         * 不会影响 comment-rpc。Java 侧合并成单 JVM 后，若默认开启就会让
         * 「DC 没配」变成「整个 comment-rpc 起不来」⇒ 默认关闭 + 启动时告警。
         */
        private boolean dispatchEnabled = false;

        /** 对应 Go 环境变量 {@code DC_PLATFORM_EVENT_URL}。 */
        private String dcEndpoint = "";

        /** 对应 Go 环境变量 {@code DC_PLATFORM_SERVICE_TOKEN}（Go 校验长度 ≥ 32）。 */
        private String dcToken = "";

        /** 对应 Go {@code cmd/fact-dispatch/main.go:73} 的 {@code &http.Client{Timeout: 8 * time.Second}}。 */
        private Duration dcTimeout = Duration.ofSeconds(8);

        /**
         * 对应 Go {@code cmd/fact-dispatch/main.go:26} 的 {@code -once} 标志：
         * {@code true} = 最多投递一条后退出（Go 的 CronJob 形态）；
         * {@code false} = 常驻循环。
         */
        private boolean once = false;

        public boolean isDispatchEnabled() {
            return dispatchEnabled;
        }

        public void setDispatchEnabled(boolean dispatchEnabled) {
            this.dispatchEnabled = dispatchEnabled;
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

        public Duration getDcTimeout() {
            return dcTimeout;
        }

        public void setDcTimeout(Duration dcTimeout) {
            this.dcTimeout = dcTimeout;
        }

        public boolean isOnce() {
            return once;
        }

        public void setOnce(boolean once) {
            this.once = once;
        }
    }

    /** 对应 Go {@code cmd/fact-authority/main.go:50-55} 的进程配置。 */
    public static class Authority {

        /**
         * 默认 <b>false</b>。
         *
         * <p>⚠️ Go 侧该端点跑在<b>独立进程的独立监听端口</b>上
         * （环境变量 {@code COMMENT_FACT_AUTHORITY_LISTEN}）。
         * 合并进单 JVM 后要暴露它就必须引入 servlet 容器
         * ⇒ <b>新增 HTTP 端口 = wire 面变更</b>。
         * 本仓库 message-rpc / like-rpc / article-rpc 三个先例都没有这么做，
         * 且 like-rpc 的同款文件自记为「端口待拍板」。
         * ⇒ 默认关闭；打开需要同时引入 web starter 并显式确定监听端口。
         */
        private boolean enabled = false;

        /** 对应 Go 环境变量 {@code COMMENT_FACT_AUTHORITY_TOKEN}（Go 校验长度 ≥ 32）。 */
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
    }
}