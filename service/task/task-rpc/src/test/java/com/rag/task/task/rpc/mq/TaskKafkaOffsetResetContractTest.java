package com.rag.task.task.rpc.mq;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 守门测试：<b>六个 listener 的「初始位点」配置必须真的生效</b>。
 *
 * <h2>它守的是什么（2026-10-24 实测抓到的一条真缺陷）</h2>
 * <p>此前六个 {@code @KafkaListener} 写的是：
 * <pre>
 * properties = "auto-offset-reset=earliest"     // ← 连字符
 * </pre>
 * 而 <b>Kafka 的合法配置名是点分的 {@code auto.offset.reset}</b>。
 * ⚠️ <b>{@code @KafkaListener(properties = ...)} 不走 Spring Boot 的松散绑定</b>
 * ⇒ 这个字符串<b>原样</b>传给 Kafka ⇒ Kafka 认不出连字符形式 ⇒ <b>直接忽略</b>。
 *
 * <p><b>实测证据</b>（真 Kafka，RAG 侧 consumer 的 config dump）：
 * <pre>
 * auto.offset.reset = latest
 * AbstractConfig : These configurations '[auto-offset-reset]' were supplied but are not used yet.
 * </pre>
 * ⇒ 三个 goka listener 写的 {@code earliest} <b>从未生效</b>，
 * 实际生效的是全局的 {@code spring.kafka.consumer.auto-offset-reset: latest}
 * （那个生效，是因为它走 Boot 松散绑定，会被转成点分形式 —— <b>同样的值，两条完全不同的路径</b>）。
 *
 * <h2>为什么此前 326 条测试全绿也抓不到</h2>
 * <p>因为<b>所有既有对拍都是「先起消费者、后灌消息」</b> ⇒
 * 那时 {@code latest} 与 {@code earliest} <b>没有可观察差别</b> ⇒ 绿测试是「假绿」。
 * ⇒ 只有「<b>先灌消息、再起消费者</b>」（全新 group、无已提交位点）才暴露这个差异
 * —— 那正是 {@code tools/difftest/run-task-offset-oldest.sh} 做的事。
 *
 * <h2>本测试的三层断言</h2>
 * <ol>
 *   <li><b>阳性对照</b>：用 Kafka 自己的 {@link ConsumerConfig} 证明
 *       <b>连字符形式确实是不被采纳的</b>（Kafka 静默忽略它，值仍取默认）
 *       ⇒ 否则「本测试抓不到这个缺陷」与「本测试抓到了」就分不清了。</li>
 *   <li><b>精确断言</b>：每个 listener 的 {@code properties} 必须含<b>点分</b>的
 *       {@code auto.offset.reset}，且值与 Go 侧一致（goka 三处 earliest / kq 三处 latest）。</li>
 *   <li><b>通用断言</b>：<b>所有</b> listener 的 <b>每一个</b> {@code properties} 键
 *       都必须是 Kafka {@link ConsumerConfig} 认识的合法配置名
 *       ⇒ 以后再写错键名会被立刻抓住，而不是静默失效。</li>
 * </ol>
 */
class TaskKafkaOffsetResetContractTest {

    /** Go 侧 goka 三个 processor：{@code initGoKa.go:131,173,207} 硬编码 {@code sarama.OffsetOldest}。 */
    private static final List<String> GOKA_LISTENERS = List.of(
            "task-goka-router", "task-goka-user-count", "task-goka-article-count");

    /** Go 侧 kq 三个消费者：{@code initKafkaRaw.go:32} / {@code initUserFilter.go:47} / {@code initArticleFilter.go:40} 硬编码 {@code "latest"}。 */
    private static final List<String> KQ_LISTENERS = List.of(
            "task-raw-diagnostic", "task-user-progress-sink", "task-article-progress-sink");

    /** Kafka 的 ConsumerConfig 强制要求两个 deserializer ⇒ 构造它时必须给。 */
    private static final Map<String, Object> MINIMAL_CONSUMER_PROPS = Map.of(
            "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer",
            "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

    private static Map<String, Object> merged(Map<String, Object> base, Map<String, Object> extra) {
        Map<String, Object> m = new java.util.LinkedHashMap<>(base);
        m.putAll(extra);
        return m;
    }

    private static final String DOTTED = "auto.offset.reset";
    private static final String HYPHENATED = "auto-offset-reset";

    // ==================================================================
    // ① 阳性对照：证明「连字符形式不被 Kafka 采纳」
    // ==================================================================

    @Test
    @DisplayName("🔴 阳性对照：Kafka 忽略连字符形式 auto-offset-reset（只认点分 auto.offset.reset）")
    void kafkaIgnoresHyphenatedOffsetResetKey() {
        // 「完全不给这个键」的基准
        ConsumerConfig baseline = new ConsumerConfig(MINIMAL_CONSUMER_PROPS);
        // 故意用连字符形式给一个 earliest
        ConsumerConfig hyphenated = new ConsumerConfig(merged(MINIMAL_CONSUMER_PROPS, Map.of(HYPHENATED, "earliest")));

        String baselineValue = baseline.getString(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        String effective = hyphenated.getString(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);

        assertEquals(baselineValue, effective,
                "★ 阳性对照失效：加上连字符键 `" + HYPHENATED + "=earliest` 之后，"
                        + "`" + DOTTED + "` 的生效值居然变了（基准=" + baselineValue
                        + "，加了连字符键后=" + effective + "）"
                        + " ⇒ 说明 Kafka 竟然采纳了连字符形式 ⇒ 下面那些「必须用点分形式」的断言"
                        + "可能变成假绿。\n"
                        + "（Kafka 的 ConsumerConfig 只定义 `" + DOTTED + "`；"
                        + "未知键走 ConfigDef.parse 的 unused 分支，只打一条 "
                        + "\"These configurations '...' were supplied but are not used yet.\"）");
    }

    @Test
    @DisplayName("阳性对照：点分形式确实被 Kafka 采纳")
    void kafkaAcceptsDottedOffsetResetKey() {
        ConsumerConfig dotted = new ConsumerConfig(merged(MINIMAL_CONSUMER_PROPS, Map.of(DOTTED, "earliest")));
        assertEquals("earliest", dotted.getString(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG),
                "★ 阳性对照失效：点分形式竟然没被采纳 ⇒ 上一条断言的前提不成立。");
    }

    // ==================================================================
    // ② 精确断言：六个 listener 的初始位点与 Go 一致
    // ==================================================================

    @Test
    @DisplayName("🔴 契约：三个 goka listener 的初始位点是 auto.offset.reset=earliest（与 Go 的 OffsetOldest 一致）")
    void gokaListenersUseEarliest() {
        for (String id : GOKA_LISTENERS) {
            Map<String, Object> props = consumerPropertiesOf(id);
            assertTrue(props.containsKey(DOTTED),
                    "🔴 listener `" + id + "` 没有用**点分**的 `" + DOTTED + "` 覆盖初始位点。"
                            + "实际 properties=" + props + "\n"
                            + "⚠️ 连字符形式 `" + HYPHENATED + "` 会被 Kafka **静默忽略**"
                            + "（`@KafkaListener(properties=...)` 不走 Boot 松散绑定）"
                            + " ⇒ 必须写点分形式。");
            assertEquals("earliest", props.get(DOTTED),
                    "🔴 listener `" + id + "` 的初始位点应是 earliest（Go: sarama.OffsetOldest），实际=" + props.get(DOTTED));
            assertFalse(props.containsKey(HYPHENATED),
                    "🔴 listener `" + id + "` 仍在用连字符形式 `" + HYPHENATED
                            + "` ⇒ 它会被 Kafka 忽略 ⇒ 实际生效的是全局默认，不是这里写的值。");
        }
    }

    @Test
    @DisplayName("🔴 契约：三个 kq listener 的初始位点是 auto.offset.reset=latest（与 Go 的硬编码 \"latest\" 一致）")
    void kqListenersUseLatest() {
        for (String id : KQ_LISTENERS) {
            Map<String, Object> props = consumerPropertiesOf(id);
            assertTrue(props.containsKey(DOTTED),
                    "🔴 listener `" + id + "` 没有用**点分**的 `" + DOTTED + "` 覆盖初始位点。实际 properties=" + props);
            assertEquals("latest", props.get(DOTTED),
                    "🔴 listener `" + id + "` 的初始位点应是 latest（Go: kq 硬编码 \"latest\"），实际=" + props.get(DOTTED));
            assertFalse(props.containsKey(HYPHENATED),
                    "🔴 listener `" + id + "` 仍在用连字符形式 `" + HYPHENATED + "` ⇒ 它会被 Kafka 忽略。");
        }
    }

    // ==================================================================
    // ③ 通用断言：所有 properties 键都必须是 Kafka 认识的合法配置名
    // ==================================================================

    @Test
    @DisplayName("🔴 契约：所有 listener 的每个 properties 键都必须是 Kafka ConsumerConfig 的合法配置名")
    void everyListenerPropertyKeyIsKnownToKafka() {
        Set<String> known = ConsumerConfig.configNames();
        List<String> offenders = consumerPropertiesByListenerId().entrySet().stream()
                .flatMap(e -> e.getValue().keySet().stream()
                        .filter(k -> !known.contains(k))
                        .map(k -> e.getKey() + " → `" + k + "`"))
                .collect(Collectors.toList());

        assertTrue(offenders.isEmpty(),
                "🔴 以下 per-listener consumer property 的键名**不是 Kafka 的合法配置名**"
                        + " ⇒ 会被 Kafka 静默忽略（只打一条 \"supplied but are not used yet\"）"
                        + " ⇒ 该 listener 的这项配置**从未生效**：\n"
                        + String.join("\n", offenders)
                        + "\n⚠️ Kafka 的合法名是点分形式（`auto.offset.reset`），"
                        + "而 `@KafkaListener(properties=...)` **不走 Spring Boot 的松散绑定** ⇒ 原样传递。");
    }

    // ==================================================================
    // 取数：按 listener id 取出它自己的 @KafkaListener.properties
    // ==================================================================

    /**
     * 按 listener id 取出 {@code @KafkaListener(properties = ...)} 声明的键值。
     * <p>用反射读注解 ⇒ 拿到的是「声明了什么」；
     * 「声明的东西有没有真的生效」由 §① 的阳性对照（Kafka 认不认这个键）负责保证。
     */
    private static Map<String, Map<String, Object>> consumerPropertiesByListenerId() {
        return List.of(TaskRawLogRouter.class, TaskUserCountConsumer.class, TaskArticleCountConsumer.class,
                        TaskRawDiagnosticConsumer.class, UserLikeProgressSink.class, ArticleLikeProgressSink.class)
                .stream()
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(KafkaListener.class))
                .collect(Collectors.toMap(
                        m -> m.getAnnotation(KafkaListener.class).id(),
                        m -> parseProperties(m.getAnnotation(KafkaListener.class).properties()),
                        (a, b) -> a));
    }

    private static Map<String, Object> consumerPropertiesOf(String listenerId) {
        Map<String, Map<String, Object>> all = consumerPropertiesByListenerId();
        Map<String, Object> props = all.get(listenerId);
        if (props == null) {
            throw new IllegalStateException("找不到 listener id=" + listenerId + "，已知的=" + all.keySet());
        }
        return props;
    }

    /** 把 {@code "k=v,k2=v2"} 解析成 map（与 Spring Kafka 自己的解析规则一致）。 */
    private static Map<String, Object> parseProperties(String[] properties) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (properties == null) {
            return map;
        }
        for (String p : properties) {
            int i = p.indexOf('=');
            if (i < 0) {
                throw new IllegalArgumentException("无法解析的 property: " + p);
            }
            map.put(p.substring(0, i).trim(), p.substring(i + 1).trim());
        }
        return map;
    }
}
