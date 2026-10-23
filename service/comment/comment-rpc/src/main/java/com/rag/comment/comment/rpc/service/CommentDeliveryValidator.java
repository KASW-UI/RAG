package com.rag.comment.comment.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.communityfact.Wire;
import com.rag.comment.comment.rpc.domain.CommentDomainFactOutboxEntity;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 冻结信封合法性校验 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/fact_delivery.go:20-48} 的
 * {@code validCommentDelivery} 与 {@code :129-134} 的 {@code decodeCommentDelivery}。
 *
 * <h2>它是「事实能不能发出去」的唯一闸门</h2>
 * <p>校验失败的处理不是「跳过这条」，而是
 * <b>把 delivery_status 置为 BLOCKED(3) 并返回 {@code ErrFrozenEnvelopeBlocked}</b>
 * （fact_delivery.go:78-90）—— 这条事实从此不再被调度器领走，等人工迁移处理。
 *
 * <p>理由（Go 注释，:86-88）：信封是<b>事务内冻结</b>的，若它与源行对不上，
 * 说明有人在冻结之后改动过事实，继续发只会让 DC 收到一条来源不可信的事实。
 * 「先发出去再说」在这里是错的。
 */
@Service
public class CommentDeliveryValidator {

    /**
     * 专用 mapper：只负责「把信封里的 payload 对象重新序列化」。
     *
     * <p>刻意与 {@code Jackson2ObjectMapperConfiguration} 那个共享 Bean 分开用 ——
     * 那个的 {@code FAIL_ON_UNKNOWN_PROPERTIES=false} 与标量强转禁令是为
     * <b>Kafka 审计消息</b>对齐 Go 的；这里处理的是<b>已经由 Go 冻结过</b>的事实载荷，
     * 判定非法载荷要靠 {@code Wire.strictDecode}（禁止未知字段），
     * 用错 mapper 会让「载荷里多一个字段」这种篡改悄悄通过校验。
     */
    private final ObjectMapper objectMapper;

    public CommentDeliveryValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 对应 Go {@code fact_delivery.go:18} {@code const commentFactProducer = "rtw.comment-rpc"}。 */
    private static final String COMMENT_FACT_PRODUCER = CommentDomainFactService.PRODUCER;

    /** 对应 Go {@code domain_fact.go:99} 的 schema 版本标识。 */
    private static final String COMMENT_SCHEMA_VERSION = CommentDomainFactService.SCHEMA_VERSION;

    /**
     * 对应 Go {@code decodeCommentDelivery}：strict decode 信封。
     *
     * <p>用 {@code Wire.strictDecode}（禁止未知字段 + 禁止尾部多余 JSON），
     * 与 Go 的 {@code communityfact.StrictDecode} 同一套规则。
     */
    public Wire.Event decodeEnvelope(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("decode comment delivery envelope: empty envelope");
        }
        try {
            return Wire.strictDecode(new StringReader(raw), Wire.Event.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("decode comment delivery envelope: " + e.getMessage(), e);
        }
    }

    /**
     * 对应 Go {@code validCommentDelivery(row, event)}（fact_delivery.go:20-48），逐条照抄。
     *
     * <p><b>14 个判据分四组，顺序即短路顺序</b>：
     * <ol>
     *   <li><b>行自身完整性</b>：aggregate_id / fact_version / delivery_envelope 三者非空；</li>
     *   <li><b>信封 ⇔ 行的身份一致</b>：event_id / event_type / aggregate_id / aggregate_version
     *       四项必须两两相等；schema_version 必须为 1；producer 必须是 {@code rtw.comment-rpc}；</li>
     *   <li><b>信封自身合法性</b>：operation_id 非空；payload 是合法 JSON；
     *       occurred_at 能按 RFC3339Nano 解析；</li>
     *   <li><b>载荷 ⇔ 信封交叉校验</b>（fact_delivery.go:33-41，最关键的一段）：
     *       <ul>
     *         <li>payload.schema_version 必须等于 {@code rtw.community-fact.v1}；</li>
     *         <li>payload.event_id / event_type / producer / operation_id 必须等于信封同名值；</li>
     *         <li>payload.aggregate_id 必须等于信封的；</li>
     *         <li>⚠️ <b>payload.aggregate_version 必须为 null</b> ——
     *             「版本号只存在于信封，不存在于载荷」是防伪造的关键；
     *             若载荷里也带一个版本号，两处不一致时无法判定哪个是真的；</li>
     *         <li>payload.comment_id 必须等于 aggregate_id；</li>
     *         <li>payload.subject_ref / target_type / target_id 必须非空；</li>
     *         <li>payload.occurred_at 必须与信封 occurred_at <b>是同一时刻</b>
     *             （Go 用 {@code .Equal()} 比较瞬时，不是字符串相等 —— 两种时区写法也算相等）；</li>
     *         <li>payload 的规范化哈希必须等于行 payload 列的规范化哈希
     *             （<b>JCS 规范化后</b>比，因为 jsonb 列会重排键序与空白）。</li>
     *       </ul></li>
     * </ol>
     *
     * @return 载荷与信封是否构成一对可投递的冻结事实
     */
    public boolean isValidDelivery(CommentDomainFactOutboxEntity row, Wire.Event event) {
        if (row == null || event == null) {
            return false;
        }
        // --- ① 行自身完整性
        if (row.getAggregateId() == null || row.getFactVersion() == null
                || row.getDeliveryEnvelope() == null) {
            return false;
        }
        // --- ② 信封 ⇔ 行的身份一致
        if (!equals(event.eventId, row.getEventId())
                || !equals(event.eventType, row.getEventType())
                || event.schemaVersion != 1
                || !COMMENT_FACT_PRODUCER.equals(event.producer)
                || !equals(event.aggregateId, row.getAggregateId())
                || event.aggregateVersion != row.getFactVersion().longValue()) {
            return false;
        }
        if (event.operationId == null || event.operationId.isEmpty()) {
            return false;
        }
        // --- ③ 信封自身合法性
        Instant occurred;
        try {
            occurred = Instant.parse(event.occurredAt);
        } catch (Exception e) {
            return false;
        }
        CommentDomainFactService.CommentFact payload;
        try {
            payload = decodePayload(event);
        } catch (Exception e) {
            return false;
        }
        if (payload == null) {
            return false;
        }

        // --- ④ 载荷 ⇔ 信封交叉校验
        if (!COMMENT_SCHEMA_VERSION.equals(payload.schemaVersion)
                || !equals(payload.eventId, event.eventId)
                || !equals(payload.eventType, event.eventType)
                || !equals(payload.producer, event.producer)
                || !equals(payload.aggregateId, event.aggregateId)
                // ⚠️ 载荷里不得带版本号（版本号的唯一权威在信封）
                || payload.aggregateVersion != null
                || !equals(payload.operationId, event.operationId)
                || !equals(payload.commentId, event.aggregateId)
                || isBlank(payload.subjectRef)
                || isBlank(payload.targetType)
                || isBlank(payload.targetId)) {
            return false;
        }
        // payload.occurred_at 是 RFC3339 字符串（Go time.Time 的 JSON 形态）；
        // 比较的是**瞬时**而非字符串 —— 两种时区写法（Z vs +08:00）也算相等（Go 的 .Equal()）。
        if (isBlank(payload.occurredAt)) {
            return false;
        }
        try {
            if (!Instant.parse(payload.occurredAt).equals(occurred)) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        // --- ⑤ 规范化哈希比对（jjsonb 会重排键序 ⇒ 必须 JCS 规范化后比）
        try {
            String payloadHash = Wire.canonicalHash(objectMapperBytes(event));
            String rowHash = Wire.canonicalHash(row.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return payloadHash.equals(rowHash);
        } catch (Exception e) {
            return false;
        }
    }

    /** 严格解码信封内的 payload（与 Go 的 {@code StrictDecode(…event.Payload, &payload)} 同规则）。 */
    public CommentDomainFactService.CommentFact decodePayload(Wire.Event event) {
        try {
            String raw = objectMapper.writeValueAsString(event.payload);
            return Wire.strictDecode(new StringReader(raw), CommentDomainFactService.CommentFact.class);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] objectMapperBytes(Wire.Event event) throws Exception {
        // 信封 payload 是 Object；重新序列化后与行 payload 做 JCS 规范化比对。
        // jsonb 规范化过的文本与信封内嵌文本只在空白/键序上不同，JCS 后相等。
        return objectMapper.writeValueAsBytes(event.payload);
    }

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}