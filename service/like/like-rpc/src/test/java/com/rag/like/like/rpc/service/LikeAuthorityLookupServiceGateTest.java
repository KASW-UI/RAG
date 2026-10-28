package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.rag.common.communityfact.Wire;
import com.rag.like.like.rpc.domain.LikeDomainFactOutboxEntity;
import com.rag.like.like.rpc.domain.LikeRecordEntity;
import com.rag.like.like.rpc.mapper.LikeDomainFactOutboxMapper;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>权威读 12 道校验的守门</b> —— 对应 Go
 * {@code service/like/rpc/internal/model/fact_authority.go:12-146}。
 *
 * <h2>它守的是什么</h2>
 *
 * <p>DC 拿到的只是一条冻结信封。它要判断「可以信任这条事实吗」时回查本域的权威读端点；
 * 端点必须逐项重放出与该事件对应的源库状态，<b>任何一项对不上就回答「不可用」</b>。
 * ⇒ <b>少一道校验 = 权威读会对「本域根本没产生过的事实」背书</b>。
 *
 * <h2>🔴 为什么「不可用」必须是 404 而不是 503</h2>
 *
 * <p>Go 用两个不同的返回值：{@code communityfact.ErrAuthorityUnavailable} ⇒ <b>404</b>
 * （「这条事实不可信 / 不存在」）；其它 error（DB 读失败）⇒ <b>503</b>（「源库读失败，请重试」）。
 * ⇒ Java 侧校验不通过必须抛 {@link Wire#ERR_AUTHORITY_UNAVAILABLE}，
 * <b>其它异常一律原样上抛</b>（⇒ 503）⇒ 两者语义不可混同。
 *
 * <h2>为什么本类不碰数据库</h2>
 *
 * <p>本类要验的是<b>校验逻辑</b>（12 道），不是 SQL ⇒ 两个 mapper 用 mock，
 * <b>{@link LikeDeliveryValidator} 用真实实现</b>（它负责严格解码 + 交付合法性 + 载荷哈希对账）。
 * ⇒ 真 PG 的落库链路由 {@code LikeFactDeliveryGoSemanticsGateTest} 负责，两者不可互相顶替。
 */
class LikeAuthorityLookupServiceGateTest {

    private static final String PRODUCER = "rtw.like-mq";
    private static final String SCHEMA_VERSION = "rtw.community-fact.v1";
    private static final String EVENT_TYPE = "community.target.interaction";
    private static final String PAYLOAD_AGGREGATE_ID = "article/42";
    private static final String ENVELOPE_AGGREGATE_ID = "like-state/9001/article/42";
    private static final String SUBJECT_REF = "rtw.identity/platform/9001";
    private static final long SUBJECT_UID = 9001L;
    private static final String OCCURRED_AT = "2026-10-25T06:00:00Z";
    private static final String AVAILABLE_AT = "2026-10-25T06:00:01Z";

    /**
     * 🔴 <b>必须是纯数字</b>：Go 的权威读最后一步要把 {@code payload.OperationID}
     * 解析成 int64（{@code strconv.ParseInt}），解析失败或 ≤0 ⇒ 直接判「不可用」。
     * ⇒ 生产里它来自雪花 ID（数字字符串）⇒ 夹具必须用数字，否则正例都过不了。
     */
    private static final String MSG_ID = "2115499282914717696";

    private LikeDomainFactOutboxMapper outboxMapper;
    private LikeRecordMapper recordMapper;
    private LikeAuthorityLookupService service;

    @BeforeEach
    void setUp() {
        outboxMapper = Mockito.mock(LikeDomainFactOutboxMapper.class);
        recordMapper = Mockito.mock(LikeRecordMapper.class);

        // 生产里注入的是 Jackson2ObjectMapperConfiguration 那个共享 Bean ⇒ 这里照抄它的配置。
        ObjectMapper sharedMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .withCoercionConfig(LogicalType.Textual, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Boolean, cfg -> cfg
                        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail))
                .build();

        service = new LikeAuthorityLookupService(
                outboxMapper, recordMapper, new LikeDeliveryValidator(sharedMapper));
    }

    // ==================================================================================
    // 正例
    // ==================================================================================

    @Test
    @DisplayName("🔴 权威读：一条已投递且自洽的事实 ⇒ 出具权威事实（回执 + 哈希 + 主体）")
    void validDeliveredFactProducesAuthorityFact() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        stubLookup(row, List.of(row));
        stubSourceRecord(1, Long.parseLong(MSG_ID));

        Wire.AuthorityFact fact = service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId());

        assertThat(fact).isNotNull();
        assertThat(fact.event).isNotNull();
        assertThat(fact.event.eventId).isEqualTo(eventId());
        assertThat(fact.subjectRef).isNotNull();
        assertThat(fact.subjectRef.subjectId).isEqualTo(String.valueOf(SUBJECT_UID));
        assertThat(fact.technicalReceipt).isNotNull();
        assertThat(fact.technicalReceipt.technicalStatus).isEqualTo("accepted");
        assertThat(fact.technicalReceipt.receiptId).isEqualTo("receipt-1");
        assertThat(fact.technicalReceipt.offset).isEqualTo(1L);
        assertThat(fact.sourceEventHash).isEqualTo(canonicalHash(row.getDeliveryEnvelope()));
        // old_state == 0 ⇒ 不是「改状态」的事实 ⇒ 没有前驱（Go fact_authority.go:96-101）
        assertThat(fact.predecessorEventId).isNull();
    }

    // ==================================================================================
    // 入参守卫（Go fact_authority.go:54）
    // ==================================================================================

    @Test
    @DisplayName("🔴 权威读：producer 不是本域的 ⇒ 不可用（404）")
    void wrongProducerIsUnavailable() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        stubLookup(row, List.of(row));

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                "rtw.comment-rpc", eventId()));
    }

    @Test
    @DisplayName("🔴 权威读：eventID 为空或超长（>128）⇒ 不可用（404）")
    void emptyOrOversizedEventIdIsUnavailable() {
        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(), PRODUCER, ""));
        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, "x".repeat(129)));
    }

    // ==================================================================================
    // 行自身的技术回执完整性（Go fact_authority.go:15-19）
    // ==================================================================================

    @Test
    @DisplayName("🔴 权威读：未投递成功的事实（delivery_status ≠ accepted）⇒ 不可用（404）")
    void notDeliveredFactIsUnavailable() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        row.setDeliveryStatus(Wire.DeliveryStatus.DELIVERY_PENDING.code());
        stubLookup(row, List.of(row));

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId()));
    }

    @Test
    @DisplayName("🔴 权威读：技术回执不完整（缺回执 ID / 位点 <1 / 哈希非法）⇒ 不可用（404）")
    void incompleteTechnicalReceiptIsUnavailable() {
        LikeDomainFactOutboxEntity noReceiptId = deliveredRow();
        noReceiptId.setTechnicalReceiptId("");
        stubLookup(noReceiptId, List.of(noReceiptId));
        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(), PRODUCER, eventId()));

        LikeDomainFactOutboxEntity zeroOffset = deliveredRow();
        zeroOffset.setTechnicalOffset(0L);
        stubLookup(zeroOffset, List.of(zeroOffset));
        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(), PRODUCER, eventId()));

        LikeDomainFactOutboxEntity badHash = deliveredRow();
        badHash.setTechnicalInputHash("not-a-valid-hash");
        stubLookup(badHash, List.of(badHash));
        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(), PRODUCER, eventId()));
    }

    @Test
    @DisplayName("🔴 权威读：信封哈希与行的 technical_input_hash 对不上 ⇒ 不可用（404）")
    void envelopeHashMismatchIsUnavailable() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        row.setTechnicalInputHash(canonicalHash("{\"tampered\":true}"));
        stubLookup(row, List.of(row));

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId()));
    }

    // ==================================================================================
    // 重放（Go fact_authority.go:74-105）
    // ==================================================================================

    @Test
    @DisplayName("🔴 权威读：fact_version 不连续（跳号）⇒ 不可用（404）")
    void nonContinuousFactVersionIsUnavailable() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        row.setFactVersion(2L);
        stubLookup(row, List.of(row));

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId()));
    }

    @Test
    @DisplayName("🔴 权威读：载荷的状态迁移不合法（like 但 old_state 已是 1）⇒ 不可用（404）")
    void illegalStateTransitionIsUnavailable() {
        String payload = payloadJson();
        String envelope = envelopeJson(payload);
        LikeDomainFactOutboxEntity row = deliveredRow(envelope, payload);
        // 让载荷声称「从已点赞态再点赞一次」⇒ validLikeTransition("like", 1, 1) = false
        row.setDeliveryEnvelope(envelope.replace("\"old_state\":0", "\"old_state\":1"));
        stubLookup(row, List.of(row));

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId()));
    }

    // ==================================================================================
    // 源库状态核对（Go fact_authority.go:106-123）
    // ==================================================================================

    @Test
    @DisplayName("🔴 权威读：源库 like_record 的状态与重放末态对不上 ⇒ 不可用（404）")
    void sourceStateMismatchIsUnavailable() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        stubLookup(row, List.of(row));
        // 重放末态 = 1（已点赞）⇒ 源库必须是 1
        stubSourceRecord(2, Long.parseLong(MSG_ID));

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId()));
    }

    @Test
    @DisplayName("🔴 权威读：源库 last_operation_id 落后于事实的 operation_id ⇒ 不可用（404）")
    void staleSourceOperationIdIsUnavailable() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        stubLookup(row, List.of(row));
        stubSourceRecord(1, Long.parseLong(MSG_ID) - 1);

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId()));
    }

    @Test
    @DisplayName("🔴 权威读：查不到源库行 ⇒ 不可用（404）")
    void missingSourceRecordIsUnavailable() {
        LikeDomainFactOutboxEntity row = deliveredRow();
        stubLookup(row, List.of(row));
        when(recordMapper.selectOneByUserAndTarget(anyLong(), anyString(), anyString())).thenReturn(null);

        assertUnavailable(() -> service.lookup(io.opentelemetry.context.Context.root(),
                PRODUCER, eventId()));
    }

    // ==================================================================================
    // 辅助
    // ==================================================================================

    private void assertUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .as("🔴 校验不通过必须抛 Wire.ERR_AUTHORITY_UNAVAILABLE（⇒ HTTP 404），"
                        + "而不是别的异常（⇒ 503「源库读失败，请重试」，语义完全不同）")
                .isSameAs(Wire.ERR_AUTHORITY_UNAVAILABLE);
    }

    private void stubLookup(LikeDomainFactOutboxEntity requested, List<LikeDomainFactOutboxEntity> rows) {
        when(outboxMapper.selectByEventId(anyString())).thenReturn(requested);
        when(outboxMapper.selectByAggregateIdOrderByVersion(anyString())).thenReturn(rows);
    }

    private void stubSourceRecord(int state, long lastOperationId) {
        LikeRecordEntity record = new LikeRecordEntity();
        record.setUserId(SUBJECT_UID);
        record.setTargetType("article");
        record.setTargetId("42");
        record.setState(state);
        record.setLastOperationId(lastOperationId);
        when(recordMapper.selectOneByUserAndTarget(anyLong(), anyString(), anyString())).thenReturn(record);
    }

    private static LikeDomainFactOutboxEntity deliveredRow() {
        String payload = payloadJson();
        return deliveredRow(envelopeJson(payload), payload);
    }

    private static LikeDomainFactOutboxEntity deliveredRow(String envelope, String payload) {
        LikeDomainFactOutboxEntity row = new LikeDomainFactOutboxEntity();
        row.setEventId(eventId());
        row.setEventType(EVENT_TYPE);
        row.setAggregateId(ENVELOPE_AGGREGATE_ID);
        row.setFactVersion(1L);
        row.setPayload(payload);
        row.setDeliveryEnvelope(envelope);
        row.setDeliveryStatus(Wire.DeliveryStatus.DELIVERY_ACCEPTED.code());
        row.setRetryCount(0);
        row.setTechnicalReceiptId("receipt-1");
        row.setTechnicalInputHash(canonicalHash(envelope));
        row.setTechnicalOffset(1L);
        row.setTechnicalReceivedAt(OffsetDateTime.of(2026, 10, 25, 6, 0, 1, 0, ZoneOffset.UTC));
        row.setDeliveredAt(OffsetDateTime.of(2026, 10, 25, 6, 0, 2, 0, ZoneOffset.UTC));
        return row;
    }

    /** 按 Go {@code likeFact}（{@code domain_fact.go:54-74}）构造载荷 JSON —— 19 个字段。 */
    private static String payloadJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schema_version\":\"").append(SCHEMA_VERSION).append("\"");
        sb.append(",\"event_id\":\"").append(eventId()).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(PAYLOAD_AGGREGATE_ID).append("\"");
        sb.append(",\"aggregate_version\":null");
        sb.append(",\"operation_id\":\"").append(MSG_ID).append("\"");
        sb.append(",\"subject_ref\":\"").append(SUBJECT_REF).append("\"");
        sb.append(",\"target_type\":\"article\"");
        sb.append(",\"target_id\":\"42\"");
        sb.append(",\"target_revision\":null");
        sb.append(",\"revision_status\":\"unknown\"");
        sb.append(",\"operation\":\"like\"");
        sb.append(",\"source_ref\":\"").append(eventId()).append("\"");
        sb.append(",\"old_state\":0");
        sb.append(",\"new_state\":1");
        sb.append(",\"event_time\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"available_at\":\"").append(AVAILABLE_AT).append("\"");
        sb.append("}");
        return sb.toString();
    }

    /** 按 Go {@code likeDeliveryEnvelope}（{@code domain_fact.go:97-104}）构造信封 JSON。 */
    private static String envelopeJson(String payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"event_id\":\"").append(eventId()).append("\"");
        sb.append(",\"event_type\":\"").append(EVENT_TYPE).append("\"");
        sb.append(",\"schema_version\":1");
        sb.append(",\"producer\":\"").append(PRODUCER).append("\"");
        sb.append(",\"aggregate_id\":\"").append(ENVELOPE_AGGREGATE_ID).append("\"");
        sb.append(",\"aggregate_version\":1");
        sb.append(",\"operation_id\":\"").append(MSG_ID).append("\"");
        sb.append(",\"occurred_at\":\"").append(OCCURRED_AT).append("\"");
        sb.append(",\"payload\":").append(payload);
        sb.append("}");
        return sb.toString();
    }

    private static String eventId() {
        return "rtw.like." + MSG_ID;
    }

    private static String canonicalHash(String raw) {
        try {
            return Wire.canonicalHash(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
