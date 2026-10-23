package com.rag.comment.comment.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * comment 域 Kafka 主题常量 —— 对应 Go 端 {@code etc/comment.yaml} 的两个 Topic 字段。
 *
 * <p>⚠️ <b>「生产与消费同一个 topic」是 Go 的现状，不是笔误</b>：
 * <ul>
 *   <li>生产者：{@code KqPusherConf.Topic: comment-audit-topic}（{@code CreateComment} 推
 *       {@code CommentKafkaMsg}）；</li>
 *   <li>消费者：{@code KqConsumerConf.Topic: comment-audit-topic}
 *       （group=comment-audit-group，offset=last，consumers=8）。</li>
 * </ul>
 * 两个 topic 值一致，很多工具（配置生成、IDE 的补全）会误判成「配置冲突」而"修正"其中一个，
 * 这里显式说明以防被改。
 *
 * <p><b>为什么要单独一个类而不是直接读 {@link CommentRpcProperties}</b>：
 * {@code @KafkaListener} 的 {@code topics} 属性走 SpEL，而 {@code @ConfigurationProperties}
 * Bean 在容器里的可用时点与 SpEL 求值时点<b>不同</b> ——
 * 用 {@code #{@commentKafkaTopics.auditTopic}} 会在解析阶段抛 EL1058E
 * （like-rpc 已在 {@code LikeUpdateKafkaListener.java:77-80} 踩过这个坑并留了注释）。
 * ⇒ 这里让 topic 由 {@link #auditTopic()} 这个<b>普通方法</b>提供，
 * {@code @KafkaListener} 一律用 {@code ${comment.kafka.audit-topic}} 占位符。
 */
@ConfigurationProperties(prefix = "comment.kafka")
public class CommentKafkaTopics {

    /** 见 {@link CommentRpcProperties.Kafka#getAuditTopic()}。 */
    private String auditTopic = "comment-audit-topic";

    /** 见 {@link CommentRpcProperties.Kafka#getAuditConcurrency()}。 */
    private int auditConcurrency = 8;

    /** 对应 Go {@code KqConsumerConf.Group: comment-audit-group}。 */
    public String auditGroupId() {
        return "comment-audit-group";
    }

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
}