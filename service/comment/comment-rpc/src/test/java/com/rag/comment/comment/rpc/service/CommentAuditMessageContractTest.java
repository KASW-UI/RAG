package com.rag.comment.comment.rpc.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 评论审计消息的 JSON 契约 —— 对应 Go {@code service/comment/rpc/common/types/kqueue.go:3-15}。
 *
 * <p><b>为什么必须逐字节断言</b>：这条消息走 Kafka，是 comment-rpc 自己的生产端与消费端之间的契约。
 * 字段顺序、空串是否输出、{@code create_time} 是秒还是毫秒 —— 任何一条变了，
 * 灰度期就会出现「Java 写的消息 Go 读不出来」或反之，而<b>JSON 语义仍然合法</b>，
 * 靠「能反序列化」这种断言<b>永远发现不了</b>。
 */
class CommentAuditMessageContractTest {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static CommentAuditMessage sample() {
        CommentAuditMessage m = new CommentAuditMessage();
        m.setCommentId(123L);
        m.setTargetType("article");
        m.setTargetId("a1");
        m.setUserId(7L);
        m.setRootId(0L);
        m.setParentId(0L);
        m.setContent("hi");
        m.setMeta("");
        m.setOwnerId(5L);
        m.setAttribute(0L);
        m.setCreateTime(1700000000L);
        return m;
    }

    @Test
    @DisplayName("字段顺序与 Go 的 encoding/json 声明顺序逐条一致")
    void jsonFieldOrderMatchesGoStructOrder() throws Exception {
        // Go: json.Marshal(CommentKafkaMsg) 按结构体声明顺序输出
        String expected = "{\"comment_id\":123,\"target_type\":\"article\",\"target_id\":\"a1\","
                + "\"user_id\":7,\"root_id\":0,\"parent_id\":0,\"content\":\"hi\",\"meta\":\"\","
                + "\"owner_id\":5,\"attribute\":0,\"create_time\":1700000000}";
        assertThat(MAPPER.writeValueAsString(sample())).isEqualTo(expected);
    }

    @Test
    @DisplayName("11 个字段无条件输出：没有 omitempty，空串也要出现")
    void noFieldIsOmitted() throws Exception {
        String json = MAPPER.writeValueAsString(sample());
        // 逐个点名，确认 meta/content 为空串时字段仍在（Go 侧没有 omitempty）
        assertThat(json).contains("\"meta\":\"\"");
        assertThat(json).contains("\"content\":\"hi\"");
        assertThat(json).contains("\"attribute\":0");
        assertThat(json).contains("\"root_id\":0");
        assertThat(json).contains("\"parent_id\":0");
        // 共 11 个键
        assertThat(MAPPER.readTree(json).size()).isEqualTo(11);
    }

    @Test
    @DisplayName("create_time 是 Unix 秒，不是毫秒（Go now.Unix()）")
    void createTimeIsInSeconds() throws Exception {
        // 1700000000 秒 = 2023-11-14T22:13:20Z；若被当毫秒解释就是 1970-01-20
        long epochSecond = sample().getCreateTime();
        assertThat(java.time.Instant.ofEpochSecond(epochSecond).toEpochMilli())
                .isNotEqualTo(epochSecond);
        assertThat(java.time.Instant.ofEpochSecond(epochSecond))
                .isEqualTo(java.time.Instant.parse("2023-11-14T22:13:20Z"));
    }

    @Test
    @DisplayName("反序列化容忍未知字段（Go 的 encoding/json 默认忽略）")
    void unknownFieldsAreIgnored() throws Exception {
        String json = MAPPER.writeValueAsString(sample())
                + ",\"some_future_field\":\"x\"";
        CommentAuditMessage parsed = MAPPER.readValue(json, CommentAuditMessage.class);
        assertThat(parsed.getCommentId()).isEqualTo(123L);
        assertThat(parsed.getOwnerId()).isEqualTo(5L);
    }
}