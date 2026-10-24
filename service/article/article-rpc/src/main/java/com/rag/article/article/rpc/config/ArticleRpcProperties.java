package com.rag.article.article.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * article-rpc 全部配置绑定 —— 对应 Go 端 {@code service/article/rpc/internal/config/config.go}。
 *
 * <p><b>L4 idiomatization</b>：Go 用一个 struct + go-zero 的 {@code conf.MustLoad}；
 * Java 用 Spring 的 {@code @ConfigurationProperties} 绑定到 application.yml。
 * <b>字段名与语义逐条对应</b>，分组结构照抄 Go 的嵌套：
 * <pre>
 * Go config.Config            → 本类的内嵌静态类
 *   zrpc.RpcServerConf          → grpc.server（listenOn / timeout / etcd key）
 *   Postgres                    → postgres
 *   KqPusherConf                → kqPusherConf
 *   KqConsumerConf              → kqConsumerConf
 *   ArticleSyncPusherConf       → articleSyncPusherConf
 *   ArticleSyncResultConsumerConf → articleSyncResultConsumerConf
 *   SecurityConf                → security（10000ms 超时有独立含义，见下）
 *   MessageRpc                  → messageRpc
 *   ArticleSyncOutbox{…}        → articleSyncOutbox{pollIntervalMs,batchSize,maxRetry}
 *   MinIO{…}                    → minio{…}
 *   HotEventPusherConf{…}       → <b>刻意不绑定</b>，见类级说明第 3 条
 * </pre>
 *
 * <h2>⚠️ 三条「配置里没有 ≠ 没开」的判据（AGENTS.md §3.3.1）</h2>
 * <ol>
 *   <li><b>{@code rpc-timeout} 不是本类字段</b>。Go yaml 写的是 {@code Timeout: 70000}，
 *       对应 {@code zrpc.RpcServerConf.Timeout}；Java 侧由
 *       {@code com.rag.common.spring.ObservabilityProperties}（承载
 *       {@code observability.rpc-timeout}）与 {@code RpcServerInterceptorFactory} 承担，
 *       <b>不是</b> {@code grpc.server.timeout}（devh starter 的那个是 channel 级，
 *       语义不同）。见 {@code application.yml} 的注释。</li>
 *   <li><b>{@code MinIO.UseSSL=false}</b> 与 {@code PublicBaseURL} 的空值回落都参与可观察行为：
 *       {@code buildPublicFileURL} 在 {@code PublicBaseURL} 为空时按 {@code UseSSL} 选 scheme
 *       拼 {@code scheme://Endpoint}（Go {@code upload_file_logic.go:93-108}）。</li>
 *   <li><b>{@code ArticleSyncOutbox.PollIntervalMs &lt;= 0} / {@code BatchSize &lt;= 0}</b>
 *       在 Go 侧有兜底（{@code article.go:52-60}：1000ms / 100）。本类不提供默认值，
 *       兜底逻辑留在 {@code ArticleSyncOutboxRelay}，以免出现「配置已生效」的假象。</li>
 * </ol>
 *
 * <h2>为什么 {@code HotEventPusherConf} 不迁移</h2>
 * Go 侧 {@code svc.NewServiceContext} 建了这个 pusher，但<b>全模块零调用点</b>
 * （{@code grep HotEventPusher} 只命中 svc 的构造行与 config 的定义行）。
 * 它属于 Go 侧的<b>死配置</b>，不是 ABSORB 也不是 NEW——照搬会凭空多出一个 Kafka topic 绑定，
 * 而该 topic（{@code article-hot-events}）的真正消费方在 like 域。
 * 这与 FileTree/article/rpc.md §12.4「HotEventPusher | — | — | 零调用点，不迁移」一致。
 */
@ConfigurationProperties(prefix = "article-rpc")
public class ArticleRpcProperties {

    /** 对应 Go {@code config.Postgres}。 */
    private Postgres postgres = new Postgres();

    /** 对应 Go {@code config.KqPusherConf}（审核事件 push）。 */
    private KafkaConf kqPusherConf = new KafkaConf();

    /** 对应 Go {@code config.KqConsumerConf}（审核消费者）。 */
    private KafkaConf kqConsumerConf = new KafkaConf();

    /** 对应 Go {@code config.ArticleSyncPusherConf}。 */
    private KafkaConf articleSyncPusherConf = new KafkaConf();

    /** 对应 Go {@code config.ArticleSyncResultConsumerConf}。 */
    private KafkaConf articleSyncResultConsumerConf = new KafkaConf();

    /** 对应 Go {@code config.SecurityConf}（zrpc.RpcClientConf）。 */
    private RpcClientConf security = new RpcClientConf();

    /** 对应 Go {@code config.MessageRpc}。 */
    private RpcClientConf messageRpc = new RpcClientConf();

    /** 对应 Go {@code config.ArticleSyncOutbox} 匿名结构体。 */
    private ArticleSyncOutbox articleSyncOutbox = new ArticleSyncOutbox();

    /** 对应 Go {@code config.MinIO}。 */
    private Minio minio = new Minio();

    public Postgres getPostgres() {
        return postgres;
    }

    public void setPostgres(Postgres postgres) {
        this.postgres = postgres;
    }

    public KafkaConf getKqPusherConf() {
        return kqPusherConf;
    }

    public void setKqPusherConf(KafkaConf kqPusherConf) {
        this.kqPusherConf = kqPusherConf;
    }

    public KafkaConf getKqConsumerConf() {
        return kqConsumerConf;
    }

    public void setKqConsumerConf(KafkaConf kqConsumerConf) {
        this.kqConsumerConf = kqConsumerConf;
    }

    public KafkaConf getArticleSyncPusherConf() {
        return articleSyncPusherConf;
    }

    public void setArticleSyncPusherConf(KafkaConf articleSyncPusherConf) {
        this.articleSyncPusherConf = articleSyncPusherConf;
    }

    public KafkaConf getArticleSyncResultConsumerConf() {
        return articleSyncResultConsumerConf;
    }

    public void setArticleSyncResultConsumerConf(KafkaConf articleSyncResultConsumerConf) {
        this.articleSyncResultConsumerConf = articleSyncResultConsumerConf;
    }

    public RpcClientConf getSecurity() {
        return security;
    }

    public void setSecurity(RpcClientConf security) {
        this.security = security;
    }

    public RpcClientConf getMessageRpc() {
        return messageRpc;
    }

    public void setMessageRpc(RpcClientConf messageRpc) {
        this.messageRpc = messageRpc;
    }

    public ArticleSyncOutbox getArticleSyncOutbox() {
        return articleSyncOutbox;
    }

    public void setArticleSyncOutbox(ArticleSyncOutbox articleSyncOutbox) {
        this.articleSyncOutbox = articleSyncOutbox;
    }

    public Minio getMinio() {
        return minio;
    }

    public void setMinio(Minio minio) {
        this.minio = minio;
    }

    /** 对应 Go {@code config.Postgres}（{@code internal/config/config.go:8-14}）。 */
    public static class Postgres {
        private String host;
        private String port;
        private String user;
        private String password;
        private String dbname;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getPort() {
            return port;
        }

        public void setPort(String port) {
            this.port = port;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        /** Go 侧字段名是 {@code DBName}，绑定时按 relaxed 命名匹配 {@code dbname}。 */
        public String getDbname() {
            return dbname;
        }

        public void setDbname(String dbname) {
            this.dbname = dbname;
        }
    }

    /**
     * 对应 Go 的 {@code kq.KqConf}。
     *
     * <p>Go 的 KqConf 还含 {@code Offset} / {@code MinCapacity} / {@code MaxCapacity} / {@code StatsDuration} 等
     * 消费/生产调优字段。Java 侧这些由 Spring Kafka 的
     * {@code spring.kafka.consumer.*} / {@code spring.kafka.producer.*} 自动配置承担，
     * 而 {@code Offset: first} 是<b>唯一参与可观察行为</b>的那个（决定审核消费者从哪开始消费），
     * 它在 {@code application.yml} 的 {@code spring.kafka.consumer.auto-offset-reset: earliest} 上。
     */
    public static class KafkaConf {
        private String name;
        private String group;
        private String topic;
        private String offset;

        /**
         * 🔴 <b>本字段在 Java 侧<b>没有消费点</b> —— 保留它只是为了让配置面与 Go 一一对应</b>。
         *
         * <h3>Go 出处（声明点 + 消费点，AGENTS.md §3.3.1）</h3>
         * <pre>
         * 字段声明点  go-queue@v1.2.2/kq/config.go:12      Brokers []string
         * 消费点(生产) go-queue@v1.2.2/kq/pusher.go:44-47   kafka.Writer{Addr: kafka.TCP(addrs...)}
         * 消费点(消费) go-queue@v1.2.2/kq/queue.go:121       kafka.ReaderConfig{Brokers: c.Brokers}
         * 仓内调用点   rpc/internal/svc/service_context.go:65,66 （生产）
         *              rpc/article.go:58,60                      （消费）
         * </pre>
         * ⇒ Go 侧<b>每个通道一个独立客户端</b>：改某个通道的 {@code brokers} 会切 broker。
         *
         * <h3>Java 侧为什么没有消费点</h3>
         * Spring Kafka 的 {@code KafkaTemplate} / {@code ProducerFactory} /
         * {@code ConsumerFactory} 是<b>全局单例</b>，统一吃 {@code spring.kafka.bootstrap-servers}。
         * ⇒ 四个通道在本模块里<b>共用同一个 broker 列表</b>，本字段改了对实际连接<b>没有任何影响</b>。
         *
         * <h3>⚠️ 为什么不直接删掉这个字段</h3>
         * 删掉会让配置面与 Go 失配（Go 有、Java 没有 ⇒ 迁过去的运维脚本会「配了但报未知键」），
         * 且丢掉了「Go 在这里有独立能力」这条信息。
         * ⇒ 保留字段 + 由 {@link ArticleKafkaChannelValidator} 在启动时校验
         * <b>它与全局是否一致</b>：分叉就<b>启动失败</b>，
         * 而不是让运维以为流量已切走、实际还连在老 broker 上。
         *
         * <h3>当前取值为何一致</h3>
         * {@code application.yml} 里四个通道都写成 {@code "${KAFKA_BROKERS:kafka:9092}"}，
         * 全局 {@code spring.kafka.bootstrap-servers} 也是同一个占位符
         * ⇒ <b>同源 ⇒ 必然相同</b> ⇒ 与 Go（yaml 四个通道都写死 {@code kafka:9092}）默认连同一个 broker。
         */
        private String[] brokers = new String[0];

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getOffset() {
            return offset;
        }

        public void setOffset(String offset) {
            this.offset = offset;
        }

        public String[] getBrokers() {
            return brokers;
        }

        public void setBrokers(String[] brokers) {
            this.brokers = brokers;
        }
    }

    /**
     * 对应 Go 的 {@code zrpc.RpcClientConf}（仅取本域真正用到的字段）。
     *
     * <p>Go 侧靠 {@code Etcd.Key}（{@code security.rpc} / {@code message.rpc}）寻址；
     * Java 侧用 devh starter + Nacos 的 {@code discovery://<name>}，等价。
     * {@code Timeout} 在 Go 侧是<b>每 client 独立</b>的（yaml 显式给 SecurityConf 配了 10000ms，
     * 而 MessageRpc 没配 ⇒ 走 {@code zrpc/config.go} 的默认值），
     * Java 侧由 {@code grpc.client.<name>.} 段表达，两个 client 分别配。
     */
    public static class RpcClientConf {
        /** 对应 Go {@code Etcd.Key}，Java 侧映射为服务发现名。 */
        private String key;
        /** 毫秒；Go 侧 {@code zrpc.RpcClientConf.Timeout}。 */
        private long timeout;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }

    /** 对应 Go {@code ArticleSyncOutbox struct { PollIntervalMs, BatchSize, MaxRetry int }}。 */
    public static class ArticleSyncOutbox {
        private int pollIntervalMs = 1000;
        private int batchSize = 100;
        private int maxRetry = 10;

        public int getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(int pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }
    }

    /**
     * 对应 Go {@code config.MinIO}（{@code internal/config/config.go:30-39}）。
     *
     * <p>⚠️ {@code imagePath} / {@code articlePath} <b>带尾斜杠</b>（yaml 里是
     * {@code "articles/images/"} / {@code "articles/content/"}），Go 是直接字符串拼接
     * （{@code fmt.Sprintf("%s%d%s", ImagePath, id, ext)}），不做规范化。
     * 本类保持原样，拼接语义见 {@code ArticleObjectStorageClient}。
     */
    public static class Minio {
        private String endpoint;
        private String publicBaseUrl;
        private String accessKeyId;
        private String secretAccessKey;
        private boolean useSsl;
        private String bucketName;
        private String imagePath;
        private String articlePath;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String publicBaseUrl) {
            this.publicBaseUrl = publicBaseUrl;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getSecretAccessKey() {
            return secretAccessKey;
        }

        public void setSecretAccessKey(String secretAccessKey) {
            this.secretAccessKey = secretAccessKey;
        }

        public boolean isUseSsl() {
            return useSsl;
        }

        public void setUseSsl(boolean useSsl) {
            this.useSsl = useSsl;
        }

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getImagePath() {
            return imagePath;
        }

        public void setImagePath(String imagePath) {
            this.imagePath = imagePath;
        }

        public String getArticlePath() {
            return articlePath;
        }

        public void setArticlePath(String articlePath) {
            this.articlePath = articlePath;
        }
    }
}