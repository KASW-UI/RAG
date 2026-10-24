package com.rag.article.article.rpc.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 🔴 校验「四个 Kafka 通道各自的 {@code brokers}」与全局 {@code spring.kafka.bootstrap-servers}
 * 是否一致 —— <b>不一致就启动失败</b>，而不是静默忽略。
 *
 * <h2>Go 出处（逐层：类型声明点 → 字段声明点 → 消费点）</h2>
 * <pre>
 * 类型声明点  rpc/internal/config/config.go:19-22
 *              KqPusherConf / KqConsumerConf / ArticleSyncPusherConf / ArticleSyncResultConsumerConf
 *              四个通道的类型都是 kq.KqConf
 *
 * 字段声明点  go-queue@v1.2.2/kq/config.go:12
 *              Brokers []string          ← 就在 KqConf 里，四个通道各有一份
 *
 * 消费点(生产) go-queue@v1.2.2/kq/pusher.go:44-47
 *              kafka.Writer{Addr: kafka.TCP(addrs...)}   ← addrs 就是各通道自己的 Brokers
 * 消费点(消费) go-queue@v1.2.2/kq/queue.go:121
 *              kafka.ReaderConfig{Brokers: c.Brokers}     ← 同理
 *
 * 仓内调用点(生产) rpc/internal/svc/service_context.go:65,66
 *              kq.NewPusher(c.KqPusherConf.Brokers, …)
 *              kq.NewPusher(c.ArticleSyncPusherConf.Brokers, …)
 * 仓内调用点(消费) rpc/article.go:58,60
 *              kq.MustNewQueue(c.KqConsumerConf, consumer)
 *              kq.MustNewQueue(c.ArticleSyncResultConsumerConf, resultConsumer)
 * </pre>
 * ⇒ Go 侧<b>改某个通道的 {@code brokers} 会切 broker</b>（每个通道一个独立的客户端）。
 *
 * <p>⚠️ <b>别引错行号</b>：Go 仓内 {@code rpc/internal/config/config.go:41} 也有一行
 * {@code Brokers []string}，但它属于 {@code HotEventPusherConf}（第 40 行那个匿名 struct），
 * <b>不是</b>四个通道的字段。四个通道的字段声明点在 go-queue 包里（上面第 12 行）。
 *
 * <h3>为什么只有四个通道，{@code HotEventPusherConf} 不在其中</h3>
 * Go 侧第五个通道 {@code HotEventPusherConf} 在 {@code service_context.go:68-70} 也建了 Pusher，
 * 但<b>全模块零调用点</b>（{@code grep HotEventPusher} 只命中 svc 的字段声明、构造行与 config 的定义行，
 * 没有任何 {@code .Push(...)}）⇒ 它是 Go 侧的<b>死配置</b>。
 * Java 侧按 {@code ArticleRpcProperties} 的既定口径<b>不迁移</b>它 ⇒ 自然不在本校验的四个通道里。
 * （它的 topic {@code article-hot-events} 的真正消费方在 like 域。）
 *
 * <h3>Go 侧 yaml 的实际取值（语义等价论证的另一半）</h3>
 * <pre>
 * rpc/etc/article.yaml:29-30   KqPusherConf.Brokers            = [kafka:9092]
 * rpc/etc/article.yaml:46-47   KqConsumerConf.Brokers          = [kafka:9092]
 * rpc/etc/article.yaml:56-57   ArticleSyncPusherConf.Brokers   = [kafka:9092]
 * rpc/etc/article.yaml:62-63   ArticleSyncResultConsumerConf.Brokers = [kafka:9092]
 * </pre>
 * ⇒ Go 侧四个通道连的是<b>同一个</b> broker ⇒ 与 RAG 侧「四通道共用全局单例」的实际效果一致。
 *
 * <h2>Java 侧现状：Spring Kafka 的客户端是<b>全局单例</b></h2>
 * <pre>
 *   两个生产者 → 自动装配的 KafkaTemplate（全局 spring.kafka.bootstrap-servers）
 *   两个消费者 → 共用 articleKafkaListenerContainerFactory → 全局 ConsumerFactory
 * </pre>
 * ⇒ 本模块的 {@code article-rpc.kq-*.brokers} 等四个键<b>声明了但没有任何消费点</b>
 * （全仓 grep {@code getBrokers()}，除<b>本校验器自己</b>外只命中 getter 本身）
 * ⇒ <b>改了不生效</b>。
 *
 * <h2>⚠️ 为什么是「启动失败」而不是「继续跑」</h2>
 * <b>静默忽略</b>是最坏的一种：运维改了通道的 broker，看到「服务正常起来了」，
 * 于是以为流量已经切走 —— <b>实际上所有通道还连在老 broker 上</b>。
 * ⇒ 本校验把它变成<b>响亮的失败</b>：配置一旦分叉，<b>启动即失败</b>，并说清原因与出路。
 *
 * <h2>⚠️ 语义等价性论证（AGENTS.md §3.2）</h2>
 * <b>本实现比 Go 多了什么？</b> —— 少了「逐通道独立 broker」这个能力。
 * <b>语义还一样吗？</b> —— <b>在当前与默认配置下完全一样</b>：
 * <ul>
 *   <li>四个通道的 {@code brokers} 在 yml 里都写成 <code>"${KAFKA_BROKERS:kafka:9092}"</code>
 *       ⇒ <b>同一个环境变量</b> ⇒ 四个值<b>必然相同</b>；</li>
 *   <li>全局的 <code>spring.kafka.bootstrap-servers</code> 也是 <code>${KAFKA_BROKERS:kafka:9092}</code>
 *       ⇒ 与通道值<b>同源</b> ⇒ 生效的 broker 列表与 Go <b>逐项相同</b>；</li>
 *   <li>Go 侧 yaml 四个通道硬编码 <code>kafka:9092</code>
 *       （{@code rpc/etc/article.yaml:29-30,46-47,56-57,62-63}）⇒ 两边<b>默认连同一个 broker</b>；</li>
 *   <li>⇒ <b>实际连接到的 broker、topic、group 全部一致</b> ⇒ 无可观察差异。</li>
 * </ul>
 * <b>边界</b>：一旦有人把某个通道的 {@code brokers} 改成<b>不同的值</b>，
 * Go 会切 broker 而 RAG 不会 ⇒ <b>行为开始分叉</b> ⇒ 本校验<b>在那一刻让启动失败</b>，
 * 把「静默分叉」变成「显式报错」。
 *
 * <h2>⚠️ 为什么不去「给四个通道各建一套工厂」</h2>
 * 那意味着新增 4 个 {@code KafkaTemplate} / {@code ConsumerFactory} Bean，
 * 而 Boot 4 的 {@code KafkaAutoConfiguration} 对这两种类型都是
 * {@code @ConditionalOnMissingBean} ⇒ <b>新增任何一个都会顶掉 Boot 自动装配的那个</b>
 * ⇒ 而 {@code ArticleOutboxRelayScheduleTest} 还自带一个 mock 的 {@code KafkaTemplate} Bean
 * ⇒ 一旦引入，"哪个模板被注入"就变成装配顺序问题（AGENTS.md §3.3 的老坑）。
 * ⇒ 为一个<b>当前取值全相同</b>的能力付这个复杂度不划算 ⇒ 改用<b>启动即失败</b>兜底。
 */
@Component
public class ArticleKafkaChannelValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleKafkaChannelValidator.class);

    private final ArticleRpcProperties properties;
    private final List<String> globalBrokers;

    public ArticleKafkaChannelValidator(ArticleRpcProperties properties, KafkaProperties kafkaProperties) {
        this.properties = properties;
        this.globalBrokers = normalize(kafkaProperties.getBootstrapServers());
    }

    /**
     * 启动期校验 —— <b>抛异常即启动失败</b>。
     *
     * <p>⚠️ 放在 {@code @PostConstruct}（Bean 初始化期）⇒ 早于容器开始收流量
     * （同 {@code DRIFT-ARTICLE-RPC-005} 的 {@code ArticleSchemaGate} 那条纪律：
     * 校验必须发生在<b>对外服务之前</b>）。
     */
    @PostConstruct
    public void validateOnStartup() {
        for (Channel channel : channels()) {
            List<String> channelBrokers = normalize(Arrays.asList(channel.conf.getBrokers()));
            if (channelBrokers.isEmpty()) {
                // 该通道没配 brokers ⇒ Go 侧会用空列表（客户端起不来）⇒ 不是本实现的分叉点，跳过
                continue;
            }
            if (globalBrokers.isEmpty()) {
                // 全局没配 ⇒ 无从比较 ⇒ 跳过（Boot 自己会用默认值）
                continue;
            }
            if (!channelBrokers.equals(globalBrokers)) {
                throw new IllegalStateException(describeMismatch(channel, channelBrokers));
            }
        }
        LOG.info("Kafka 通道校验通过：四个通道的 brokers 与全局 spring.kafka.bootstrap-servers 一致（{}）",
                globalBrokers);
    }

    /** 四个通道（Go 侧各自独立 Brokers 的那四个）。 */
    private List<Channel> channels() {
        return List.of(
                new Channel("kq-pusher-conf（生产 article-creation-topic）", properties.getKqPusherConf()),
                new Channel("kq-consumer-conf（消费 article-creation-topic）", properties.getKqConsumerConf()),
                new Channel("article-sync-pusher-conf（生产 article-sync-events）",
                        properties.getArticleSyncPusherConf()),
                new Channel("article-sync-result-consumer-conf（消费 article-sync-results）",
                        properties.getArticleSyncResultConsumerConf()));
    }

    private String describeMismatch(Channel channel, List<String> channelBrokers) {
        return "article-rpc 的 Kafka 通道「" + channel.label + "」的 brokers=[" + channelBrokers
                + "] 与全局 spring.kafka.bootstrap-servers=[" + globalBrokers + "] 不一致，"
                + "但该值**不会生效**。\n"
                + "  原因：Go 侧每个通道各自用自己的 Brokers —— 字段声明点 go-queue@v1.2.2/kq/config.go:12，"
                + "消费点 kq/pusher.go:44-47（生产）/ kq/queue.go:121（消费），"
                + "仓内调用点 internal/svc/service_context.go:65,66 / internal/article.go:58,60；"
                + "而 Spring Kafka 的 KafkaTemplate / ConsumerFactory 是**全局单例**，"
                + "本模块的四个通道共用 spring.kafka.bootstrap-servers。\n"
                + "  出路：① 把该通道的 brokers 改成与全局一致（当前 yml 四个通道都写成 "
                + "\"${KAFKA_BROKERS:kafka:9092}\"，本就同源）；"
                + "② 或改造本模块的 Kafka 装配，给该通道单独建 ProducerFactory / ConsumerFactory。\n"
                + "  ⚠️ 静默忽略更危险（运维以为已切走 broker，实际还连在老 broker 上）⇒ 这里选择启动失败。";
    }

    /**
     * 归一化：只丢 {@code null} 与空串，<b>不做 trim</b>。
     *
     * <p>⚠️ <b>为什么不 trim</b>：Go 侧 {@code kafka.TCP(addrs...)} 对地址<b>逐字使用</b>，
     * 带空格的地址在 Go 侧是「连不上」而不是「被清洗」⇒ 这里也逐字比较，
     * 免得在校验层引入一个 Go 没有的宽容度（无声地让两侧判定不同）。
     *
     * <p>空串被丢掉 ⇒ 「通道的 brokers 为空」= <b>没配</b> ⇒ 跳过校验
     * （Go 侧空列表会让客户端起不来；RAG 侧回落到全局 ⇒ 这个边界记在签字偏离里）。
     */
    private static List<String> normalize(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String s : raw) {
            if (s != null && !s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    /** 一个通道（标签 + 配置）。 */
    private record Channel(String label, ArticleRpcProperties.KafkaConf conf) {
    }
}
