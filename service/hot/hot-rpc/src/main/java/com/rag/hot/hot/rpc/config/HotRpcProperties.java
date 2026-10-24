package com.rag.hot.hot.rpc.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * hot-rpc 业务配置绑定（{@code application.yml} 的 {@code hot-rpc.*} 段）。
 *
 * <p>Go 真源：{@code service/hot/rpc/internal/config/config.go}（40 行）+ {@code etc/hot.yaml}。
 * <p>推导依据：{@code FileTree/hot/rpc.md §12.5}。
 *
 * <h2>配置边界：本类只承载「业务语义」，基础设施交给 Spring 原生属性</h2>
 * <table border="1">
 *   <caption>Go config.go → Java 落点</caption>
 *   <tr><th>Go</th><th>Java</th><th>理由</th></tr>
 *   <tr><td>{@code zrpc.RpcServerConf}（ListenOn）</td><td>{@code grpc.server.port}</td><td>Spring/devh 原生</td></tr>
 *   <tr><td>{@code RedisConf{Host,Pass}}</td><td>{@code spring.data.redis.*}</td><td>Spring Data Redis 原生</td></tr>
 *   <tr><td>{@code KqConsumerConf{Brokers}</td><td>{@code spring.kafka.bootstrap-servers}</td><td>Spring Kafka 原生</td></tr>
 *   <tr><td>{@code KqConsumerConf.Group}</td><td>{@code spring.kafka.consumer.group-id}</td><td>Spring Kafka 原生</td></tr>
 *   <tr><td>{@code KqConsumerConf.Topic}</td><td>{@code hot-rpc.kafka.topic}（本类）</td><td>业务契约，Go 三份 yaml 一致</td></tr>
 *   <tr><td>{@code HeavyKeeper}</td><td>{@link HeavyKeeper}（本类）</td><td>业务算法参数</td></tr>
 *   <tr><td>{@code Interaction}</td><td>{@link Interaction}（本类）</td><td>业务算法参数</td></tr>
 * </table>
 *
 * <p><b>刻意不迁移的死配置</b>（Go 侧从未被读取，迁移即删，不建结构）：
 * {@code RedisConf.Type}（{@code service_context.go:17-20} 只用 {@code Addr}+{@code Password}）、
 * {@code KqPusherConf}（pusher 建了零调用）、{@code KqConsumerConf.Name}（hot 未读）。
 *
 * <p><b>刻意保留的死字段</b>：{@link Weight#getCode()}。Go 的 {@code IndexWeight.Code} 在
 * {@code hot.go:47-50} 按 {@code Name} 建 map，{@code Code} 被丢弃；但 {@code hot2/hot3.yaml}
 * 与 {@code hot.yaml} 的唯一结构差异就是它的缺失（实测 diff 确认）⇒
 * 移植必须保留该字段，否则配置结构与 Go 不一致，将来按 Code 分发时无据可依。
 */
@Component
@ConfigurationProperties(prefix = "hot-rpc")
public class HotRpcProperties {

    private HeavyKeeper heavyKeeper = new HeavyKeeper();
    private Interaction interaction = new Interaction();
    private Kafka kafka = new Kafka();

    public HeavyKeeper getHeavyKeeper() {
        return heavyKeeper;
    }

    public void setHeavyKeeper(HeavyKeeper heavyKeeper) {
        this.heavyKeeper = heavyKeeper;
    }

    public Interaction getInteraction() {
        return interaction;
    }

    public void setInteraction(Interaction interaction) {
        this.interaction = interaction;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    /**
     * 对应 Go {@code heavykeeper.Config}（{@code etc/hot*.yaml} 的 {@code HeavyKeeper} 段）。
     *
     * <p>默认值取自 Go {@code heavykeeper.DefaultConfig()}，与三份 yaml 完全一致
     * （Width=1000 / Depth=5 / Decay=0.9 / TopK=100）。
     */
    public static class HeavyKeeper {
        /** 桶数/行宽。 */
        private int width = 1000;
        /** 行数 = 哈希函数个数。 */
        private int depth = 5;
        /** 冲突时让位的概率基数 {@code decay^count}。 */
        private double decay = 0.9;
        /** 保留的 Top-K 条目数。 */
        private int topK = 100;

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        public double getDecay() {
            return decay;
        }

        public void setDecay(double decay) {
            this.decay = decay;
        }

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }

    /**
     * 对应 Go {@code config.InteractionConfig}。
     *
     * <p>⚠️ {@link #getPartitionCount()} 与 Kafka topic 的<b>实际分区数必须相等</b>：
     * Go {@code mergePartitions} 只读 {@code p0..p{N-1}}（{@code hot_handler.go:361-363}）。
     * topic 分区数 &gt; {@code partitionCount} 时，多出来的分片快照照写不误但
     * <b>永远不参与合并</b> ⇒ 那些文章不会出现在 {@code hot:articles}。
     * <b>Go 侧同样有该问题，此处按 §15「不擅自修复 Go 行为」原样保留</b>，只在配置注释留痕。
     */
    public static class Interaction {
        /** 全局计数器阈值：每 N 条事件触发一次分区快照。Go {@code Interaction.SyncEvery}（100）。 */
        private long syncEvery = 100L;
        /** 合并后大 key {@code hot:articles} 的 TTL 秒数。Go {@code Interaction.TTL}（3600）。 */
        private long ttlSeconds = 3600L;
        /** 参与合并的分片数。Go {@code Interaction.PartitionCount}（3）。 */
        private int partitionCount = 3;
        /** 事件类型 → 权重。Go {@code Interaction.Weights}。 */
        private List<Weight> weights = new ArrayList<>();

        public long getSyncEvery() {
            return syncEvery;
        }

        public void setSyncEvery(long syncEvery) {
            this.syncEvery = syncEvery;
        }

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }

        public int getPartitionCount() {
            return partitionCount;
        }

        public void setPartitionCount(int partitionCount) {
            this.partitionCount = partitionCount;
        }

        public List<Weight> getWeights() {
            return weights;
        }

        public void setWeights(List<Weight> weights) {
            this.weights = weights;
        }
    }

    /**
     * 对应 Go {@code config.IndexWeight}。
     *
     * <p>⚠️ {@link #getCode()} 在 Go 侧<b>零读取</b>（{@code hot.go:47-50} 只按 {@code Name} 建 map），
     * 但字段必须保留 —— 理由见类注释「刻意保留的死字段」。
     */
    public static class Weight {
        /** Go {@code IndexWeight.Code}。保留但不参与分发。 */
        private int code;
        /** 事件类型名（{@code like} / {@code comment} / {@code coin} / {@code share}）。分发键。 */
        private String name;
        /** 该类型的权重。 */
        private int weight;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }
    }

    /** 对应 Go {@code KqConsumerConf.Topic}。Go 三份 yaml 均为 {@code article-hot-events}。 */
    public static class Kafka {
        private String topic = "article-hot-events";

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }
    }
}