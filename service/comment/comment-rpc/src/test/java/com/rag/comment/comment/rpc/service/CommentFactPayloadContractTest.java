package com.rag.comment.comment.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事实载荷与冻结信封的 JSON 契约 —— 对应 Go
 * {@code service/comment/rpc/internal/model/domain_fact.go:43-80}。
 *
 * <p><b>这是本域最容易被「顺手优化」破坏的地方</b>：
 * {@code aggregate_version} 与 {@code target_revision} <b>没有</b> omitempty（不填时输出 null），
 * 而 {@code operator_ref} / {@code parent_comment_id} / {@code old_state} / {@code new_state}
 * <b>有</b> omitempty（不填时字段整个消失）。两组规则相反，
 * 而 {@code validCommentDelivery}（fact_delivery.go:36）要断言
 * {@code payload.aggregate_version == null} —— 把 omitempty 加上去会让「版本号缺失」
 * 与「版本号为空」变成同一种输入，伪造者就能用一个不带版本号的载荷通过校验。
 */
class CommentFactPayloadContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static CommentDomainFactService.CommentFact createdFact() {
        CommentDomainFactService.CommentFact f = new CommentDomainFactService.CommentFact();
        f.eventId = "rtw.comment.123.created";
        f.eventType = "community.comment.created";
        f.producer = CommentDomainFactService.PRODUCER;
        f.aggregateId = "123";
        f.operationId = "rtw.comment.123.created";
        f.subjectRef = "rtw.identity/platform/7";
        f.targetType = "article";
        f.targetId = "a1";
        f.revisionStatus = CommentDomainFactService.REVISION_STATUS_UNKNOWN;
        f.operation = "create";
        f.sourceRef = "rtw.comment/123";
        f.commentId = "123";
        f.visibilityState = 0;
        f.searchEvidence = CommentDomainFactService.SEARCH_EVIDENCE;
        f.schemaVersion = CommentDomainFactService.SCHEMA_VERSION;
        // Go 的 time.Time ⇒ JSON 是 RFC3339Nano 字符串
        f.eventTime = "2026-10-23T10:00:00Z";
        f.occurredAt = f.eventTime;
        f.availableAt = f.eventTime;
        return f;
    }

    @Test
    @DisplayName("🔴 无 omitempty 的字段在未填时必须输出 null（Go 里是 nil 指针）")
    void nullableFieldsAreEmittedAsNull() throws Exception {
        String json = MAPPER.writeValueAsString(createdFact());
        ObjectNode node = (ObjectNode) MAPPER.readTree(json);
        // aggregate_version 未填 ⇒ 输出 null，而不是字段消失
        assertThat(node.has("aggregate_version")).isTrue();
        assertThat(node.get("aggregate_version").isNull()).isTrue();
        // target_revision 同理
        assertThat(node.has("target_revision")).isTrue();
        assertThat(node.get("target_revision").isNull()).isTrue();
    }

    @Test
    @DisplayName("🔴 有 omitempty 的字段在未填时必须整个消失")
    void omitemptyFieldsDisappear() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(createdFact()));
        // 根评论：没有 parent_comment_id / old_state / new_state / operator_ref
        assertThat(node.has("parent_comment_id")).isFalse();
        assertThat(node.has("old_state")).isFalse();
        assertThat(node.has("new_state")).isFalse();
        assertThat(node.has("operator_ref")).isFalse();
    }

    @Test
    @DisplayName("⚠️ old_state=0 必须被输出（它是 omitempty 字段但 0 是有效值）")
    void zeroOldStateIsARealValueNotAbsence() throws Exception {
        CommentDomainFactService.CommentFact f = createdFact();
        f.operation = "like";
        f.oldState = 0;
        f.newState = 1;
        ObjectNode node = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(f));
        // Go: `old_state *int32 json:"old_state,omitempty"` —— 非 nil 指针即输出（含 0）
        assertThat(node.has("old_state")).isTrue();
        assertThat(node.get("old_state").asInt()).isZero();
        assertThat(node.get("new_state").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("schema_version / producer / revision_status / search_evidence 取 Go 的固定值")
    void fixedConstantsMatchGo() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(createdFact()));
        assertThat(node.get("schema_version").asText()).isEqualTo("rtw.community-fact.v1");
        assertThat(node.get("producer").asText()).isEqualTo("rtw.comment-rpc");
        assertThat(node.get("revision_status").asText()).isEqualTo("unknown");
        assertThat(node.get("search_evidence").asBoolean()).isFalse();
        // ⚠️ 时间必须是 RFC3339 字符串（Go time.Time 的 JSON 形态），不是数字/数组
        assertThat(node.get("event_time").isTextual()).isTrue();
        assertThat(node.get("event_time").asText()).isEqualTo("2026-10-23T10:00:00Z");
    }

    @Test
    @DisplayName("字段顺序与 Go 结构体声明顺序一致")
    void fieldOrderMatchesGoDeclarationOrder() throws Exception {
        ObjectNode node = (ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(createdFact()));
        java.util.List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        // created 事实没有 operator_ref（omitempty），因此它不出现在输出里
        assertThat(names).containsExactly(
                "schema_version", "event_id", "event_type", "producer", "aggregate_id",
                "aggregate_version", "operation_id", "subject_ref",
                "target_type", "target_id", "target_revision", "revision_status", "operation",
                "source_ref", "comment_id",
                "visibility_state", "search_evidence", "event_time", "occurred_at", "available_at");

        // deleted 事实带 operator_ref：它必须落在 subject_ref 之后、target_type 之前
        CommentDomainFactService.CommentFact deleted = createdFact();
        deleted.operation = "retract";
        deleted.operatorRef = "rtw.identity/platform/9";
        deleted.parentCommentId = "11";
        java.util.List<String> deletedNames = new java.util.ArrayList<>();
        ((ObjectNode) MAPPER.readTree(MAPPER.writeValueAsString(deleted)))
                .fieldNames().forEachRemaining(deletedNames::add);
        assertThat(deletedNames).containsExactly(
                "schema_version", "event_id", "event_type", "producer", "aggregate_id",
                "aggregate_version", "operation_id", "subject_ref", "operator_ref",
                "target_type", "target_id", "target_revision", "revision_status", "operation",
                "source_ref", "comment_id", "parent_comment_id",
                "visibility_state", "search_evidence", "event_time", "occurred_at", "available_at");
    }

    @Test
    @DisplayName("subject_ref 必须是 rtw.identity/platform/{uid}，uid ≤ 0 直接失败")
    void subjectRefFormatAndGuard() {
        assertThat(CommentDomainFactService.subjectRef(7L)).isEqualTo("rtw.identity/platform/7");
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> CommentDomainFactService.subjectRef(0L))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> CommentDomainFactService.subjectRef(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("互动事实 ID 前缀固定（权威读靠它识别互动事件）")
    void interactionIdPrefix() {
        String id = CommentDomainFactService.newCommentInteractionId();
        assertThat(id).startsWith("rtw.comment.interaction.");
        assertThat(id).isNotEqualTo(CommentDomainFactService.newCommentInteractionId());
    }
}