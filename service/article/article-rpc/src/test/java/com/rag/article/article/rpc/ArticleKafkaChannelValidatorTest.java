package com.rag.article.article.rpc;

import com.rag.article.article.rpc.config.ArticleKafkaChannelValidator;
import com.rag.article.article.rpc.config.ArticleRpcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 Kafka 通道的 {@code brokers} 与全局 {@code spring.kafka.bootstrap-servers} 不一致 ⇒
 * <b>启动失败</b>（而不是静默忽略）。
 *
 * <h2>为什么需要它</h2>
 * Go 侧<b>每个通道各自</b>用自己的 {@code Brokers}
 * （{@code internal/svc/service_context.go:65,66} / {@code internal/article.go:58,60}），
 * 而 Spring Kafka 的客户端是<b>全局单例</b>
 * ⇒ 本模块 yml 里的四个 {@code brokers} 键<b>声明了但没有消费点</b> ⇒ <b>改了不生效</b>。
 *
 * <p>⚠️ <b>「静默忽略」是最坏的一种</b>：运维改了通道的 broker，看到服务正常起来，
 * 于是以为流量已切走 —— 实际上所有通道还连在老 broker 上。
 * ⇒ 本校验把它变成<b>响亮的失败</b>。
 *
 * <h2>判据（AGENTS.md §3.2 idiomatization 留痕）</h2>
 * 语义等价性成立的**前提**是「四个通道的 broker 列表与全局相同」——
 * 当前 yml 四个通道都写成 {@code "${KAFKA_BROKERS:kafka:9092}"} ⇒ <b>同源 ⇒ 必然相同</b>。
 * ⇒ 本类测的正是<b>这个前提被破坏时会不会响亮地失败</b>。
 *
 * @see ArticleKafkaChannelValidator
 */
class ArticleKafkaChannelValidatorTest {

    /** 造一份「四个通道的 brokers 都是同一个值」的配置（= 当前 yml 的形状）。 */
    private static ArticleRpcProperties propertiesWith(String... channelBrokers) {
        ArticleRpcProperties p = new ArticleRpcProperties();
        p.setKqPusherConf(kafkaConf(channelBrokers[0]));
        p.setKqConsumerConf(kafkaConf(channelBrokers[1]));
        p.setArticleSyncPusherConf(kafkaConf(channelBrokers[2]));
        p.setArticleSyncResultConsumerConf(kafkaConf(channelBrokers[3]));
        return p;
    }

    private static ArticleRpcProperties.KafkaConf kafkaConf(String broker) {
        ArticleRpcProperties.KafkaConf c = new ArticleRpcProperties.KafkaConf();
        c.setBrokers(broker == null ? new String[0] : new String[]{broker});
        return c;
    }

    private static KafkaProperties kafkaProperties(String... bootstrapServers) {
        KafkaProperties kp = new KafkaProperties();
        kp.setBootstrapServers(List.of(bootstrapServers));
        return kp;
    }

    // ==================================================================
    // 1. 一致 ⇒ 通过
    // ==================================================================

    @Test
    @DisplayName("四个通道的 brokers 与全局一致 ⇒ 校验通过（= 当前 yml 的形状：都来自 ${KAFKA_BROKERS}）")
    void identicalChannelBrokersPassValidation() {
        ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                propertiesWith("kafka:9092", "kafka:9092", "kafka:9092", "kafka:9092"),
                kafkaProperties("kafka:9092"));
        assertDoesNotThrow(validator::validateOnStartup,
                "四个通道与全局同源 ⇒ 不得报错（否则生产环境会起不来）");
    }

    @Test
    @DisplayName("某个通道的 brokers 为空（未配）⇒ 校验通过（不是分叉点）")
    void emptyChannelBrokersPassValidation() {
        ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                propertiesWith("kafka:9092", null, "kafka:9092", "kafka:9092"),
                kafkaProperties("kafka:9092"));
        assertDoesNotThrow(validator::validateOnStartup,
                "未配 brokers 的通道不是「静默忽略」的分叉点 ⇒ 不得报错");
    }

    // ==================================================================
    // 2. 不一致 ⇒ 启动失败（响亮）
    // ==================================================================

    @Test
    @DisplayName("🔴 消费通道的 brokers 与全局不一致 ⇒ **抛错**（启动失败），且消息说清「不会生效」")
    void mismatchedConsumerChannelBrokersFailFast() {
        ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                propertiesWith("kafka:9092", "other-broker:9092", "kafka:9092", "kafka:9092"),
                kafkaProperties("kafka:9092"));
        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validateOnStartup,
                "通道 brokers 与全局分叉时必须启动失败（静默忽略更危险）");
        String msg = e.getMessage();
        assertTrue(msg.contains("kq-consumer-conf"), "消息必须点名是哪个通道：" + msg);
        assertTrue(msg.contains("other-broker:9092"), "消息必须写明该通道的实际值：" + msg);
        assertTrue(msg.contains("kafka:9092"), "消息必须写明全局值：" + msg);
        assertTrue(msg.contains("不会生效"), "消息必须说明「这个值不生效」：" + msg);
        assertTrue(msg.contains("kq/config.go:12"),
                "消息必须给出**字段声明点**（go-queue@v1.2.2/kq/config.go:12）—— "
                        + "当初台账把 config.go:41（那是 HotEventPusherConf 的字段）当成通道字段，"
                        + "引的就是错的那一层：" + msg);
        assertTrue(msg.contains("service_context.go:65,66") && msg.contains("article.go:58,60"),
                "消息必须给出 Go 出处（文件:行号）：" + msg);
    }

    @Test
    @DisplayName("🔴 生产通道的 brokers 与全局不一致 ⇒ **抛错**（同样响亮）")
    void mismatchedProducerChannelBrokersFailFast() {
        ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                propertiesWith("kafka:9092", "kafka:9092", "other-broker:9092", "kafka:9092"),
                kafkaProperties("kafka:9092"));
        IllegalStateException e = assertThrows(IllegalStateException.class, validator::validateOnStartup,
                "生产通道分叉同样必须启动失败");
        assertTrue(e.getMessage().contains("article-sync-pusher-conf"),
                "消息必须点名是哪个通道：" + e.getMessage());
    }

    @Test
    @DisplayName("🔴 通道 brokers 与全局**顺序/个数**不同也算分叉（不是只比集合）")
    void differentBrokerOrderCountsAsMismatch() {
        ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                propertiesWith("kafka:9092", "kafka:9092", "kafka:9092", "kafka:9092"),
                kafkaProperties("kafka:9092", "kafka:9093"));
        assertThrows(IllegalStateException.class, validator::validateOnStartup,
                "全局有两个 broker 而通道只有一个 ⇒ 分叉 ⇒ 必须失败");
    }

    // ==================================================================
    // 3. 守恒断言：四个通道一个都不能漏
    // ==================================================================

    /**
     * 🔴 <b>守恒断言</b>：四个通道<b>每一个</b>分叉都要被拦住。
     *
     * <p>⚠️ <b>这条是变异验证逼出来的</b>：只测两个通道（消费一个、生产一个）时，
     * 把 {@code channels()} 里<b>另外两个</b>通道删掉，测试<b>照样全绿</b>
     * ⇒ 「批量改 N 处」必须配一条覆盖<b>全部 N 处</b>的断言（AGENTS.md §3.3）。
     * ⇒ 本条把「4」这个 N 钉死。
     */
    @Test
    @DisplayName("🔴 守恒：四个通道**每一个**分叉都要被拦住（不能只测其中两个）")
    void everyOneOfTheFourChannelsIsGuarded() {
        String[] names = {"kq-pusher-conf", "kq-consumer-conf",
                "article-sync-pusher-conf", "article-sync-result-consumer-conf"};
        for (int i = 0; i < names.length; i++) {
            String[] channelBrokers = {"kafka:9092", "kafka:9092", "kafka:9092", "kafka:9092"};
            channelBrokers[i] = "other-broker:9092";

            ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                    propertiesWith(channelBrokers), kafkaProperties("kafka:9092"));
            IllegalStateException e = assertThrows(IllegalStateException.class, validator::validateOnStartup,
                    "通道「" + names[i] + "」分叉却没被拦住 ⇒ 这个通道的校验是漏的");
            assertTrue(e.getMessage().contains(names[i]),
                    "报错必须点名「" + names[i] + "」，实际：" + e.getMessage());
        }
    }

    /**
     * 🔴 <b>逐字比较，不做 trim</b>：与 Go 侧 {@code kafka.TCP(addrs...)} 的逐字使用一致。
     *
     * <p>⚠️ 如果这里改成 trim（更"宽容"），Go 侧带空格的地址是「连不上」、
     * RAG 侧却会「当成相同」⇒ 两侧判定不同 ⇒ 校验层凭空多出一个 Go 没有的宽容度。
     */
    @Test
    @DisplayName("🔴 逐字比较：带空白的通道地址算**分叉**（Go 的 kafka.TCP 也不 trim）")
    void whitespaceIsNotNormalizedAway() {
        ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                propertiesWith(" kafka:9092 ", "kafka:9092", "kafka:9092", "kafka:9092"),
                kafkaProperties("kafka:9092"));
        assertThrows(IllegalStateException.class, validator::validateOnStartup,
                "带空白的地址必须算分叉（Go 侧逐字使用地址，不会被清洗）");
    }

    /**
     * 🔴 全局 {@code bootstrap-servers} 为空 ⇒ <b>跳过</b>校验（无从比较）。
     *
     * <p>⚠️ 这条覆盖的是「{@code globalBrokers.isEmpty()}」那条短路分支。
     * 变异验证时发现：把这个分支去掉，测试<b>照样全绿</b> ⇒ 该分支原本<b>零覆盖</b>
     * ⇒ 补上本条后才算把这条路径钉住。
     *
     * <p>（Boot 的 {@code KafkaProperties} 默认值是 {@code ["localhost:9092"]}，
     * 所以生产上全局几乎不会为空；但显式配成空是可能的，仍要有确定的行为。）
     */
    @Test
    @DisplayName("全局 bootstrap-servers 为空 ⇒ 跳过校验（无从比较，不是分叉）")
    void emptyGlobalBootstrapServersSkipValidation() {
        ArticleKafkaChannelValidator validator = new ArticleKafkaChannelValidator(
                propertiesWith("kafka:9092", "kafka:9092", "kafka:9092", "kafka:9092"),
                kafkaProperties());
        assertDoesNotThrow(validator::validateOnStartup,
                "全局没配时无从比较 ⇒ 不得报错（否则会拦下合法的部署形态）");
    }
}
