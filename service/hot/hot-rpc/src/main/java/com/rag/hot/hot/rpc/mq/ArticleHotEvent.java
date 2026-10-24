package com.rag.hot.hot.rpc.mq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Kafka 消息体（{@code article-hot-events} topic 的 value）。
 *
 * <p>Go 真源：{@code service/hot/rpc/internal/mqs/types.go}（8 行，<b>只有 2 个字段</b>）。
 *
 * <p>⚠️ <b>{@code ignoreUnknown = true} 是硬要求，不是风格问题</b>：
 * Go 的 {@code json.Unmarshal} 默认忽略未知字段，而测试脚手架
 * {@code service/hot/test/cmd/producer/producer.go:18-23} 实测发送的是 <b>4 个字段</b>
 * （多 {@code user_id} / {@code timestamp}）⇒ 若 Java 侧严格拒绝未知字段，
 * 现有生产端发的事件会被<b>全部 drop</b>，热榜永远不更新。
 *
 * <p>字段名对齐 Go 的 json tag：{@code article_id} / {@code type}。
 * Jackson 的 {@code @JsonProperty} 显式钉死，避免依赖命名策略。
 *
 * @param articleId 文章 ID（Go {@code ArticleID}）
 * @param type      事件类型，取值须命中 {@code Interaction.Weights} 的 {@code Name}（like/comment/coin/share）
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArticleHotEvent(
        @com.fasterxml.jackson.annotation.JsonProperty("article_id") String articleId,
        @com.fasterxml.jackson.annotation.JsonProperty("type") String type) {
}