package com.rag.comment.comment.rpc.service;

import com.rag.common.communityfact.AuthorityHandler;
import com.rag.common.communityfact.Wire;
import com.rag.comment.comment.rpc.domain.CommentDomainFactOutboxEntity;
import com.rag.comment.comment.rpc.domain.CommentIndexEntity;
import com.rag.comment.comment.rpc.domain.CommentLikeEntity;
import com.rag.comment.comment.rpc.mapper.CommentContentMapper;
import com.rag.comment.comment.rpc.mapper.CommentDomainFactOutboxMapper;
import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.comment.comment.rpc.mapper.CommentLikeMapper;
import io.opentelemetry.context.Context;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 权威读 —— 对应 Go 端 {@code service/comment/rpc/internal/model/fact_authority.go:12-214} 的
 * <b>全部六个</b>函数：{@code deliveredCommentAuthority} / {@code validCommentRevision} /
 * {@code AuthoritativeFact} / {@code authoritativeCommentInteraction} /
 * {@code validReactionTransition} / {@code sameStringPointer} / {@code subjectID}。
 *
 * <p>它们同属一条「链路重放校验」，拆开反而会割裂语义，故合成一个类
 * （这不是「为了对应 Go 文件」——Go 侧它们本来就是同一个文件的一组私有函数）。
 *
 * <h2>它回答的问题：DC 拿到的这条事实，是不是真的来自本域</h2>
 * <p>DC 侧只拿到一条冻结信封。它要判断「可以信任这条事实吗」时，回查本端点，
 * 本端点必须逐项重放出与该事件对应的源库状态，<b>任何一项对不上就回答「不可用」</b>
 * （HTTP 404）。它<b>只出具已有本地 DC 技术回执的事实</b> ——
 * 还没投递成功的事实不出具（否则等于替 DC 背书一条尚未被 DC 接收的事件）。
 *
 * <h2>⚠️ 「不可用」的表达方式</h2>
 * <p>Go 用 {@code communityfact.ErrAuthorityUnavailable} ⇒ HTTP 404；
 * Java 侧必须抛 {@link AuthorityHandler.AuthorityUnavailableException}
 * （= {@code Wire.ERR_AUTHORITY_UNAVAILABLE}），否则会被当成 503
 * （「源库读失败，请重试」），语义完全不同。
 */
@Service
public class CommentAuthorityLookupService implements AuthorityHandler.AuthorityLookup {

    private final CommentDomainFactOutboxMapper outboxMapper;
    private final CommentIndexMapper commentIndexMapper;
    private final CommentContentMapper commentContentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final CommentDeliveryValidator validator;

    public CommentAuthorityLookupService(CommentDomainFactOutboxMapper outboxMapper,
                                         CommentIndexMapper commentIndexMapper,
                                         CommentContentMapper commentContentMapper,
                                         CommentLikeMapper commentLikeMapper,
                                         CommentDeliveryValidator validator) {
        this.outboxMapper = outboxMapper;
        this.commentIndexMapper = commentIndexMapper;
        this.commentContentMapper = commentContentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.validator = validator;
    }

    /**
     * 对应 Go {@code AuthoritativeFact(ctx, producer, eventID)}。
     *
     * @throws AuthorityHandler.AuthorityUnavailableException 任何一项对不上 ⇒ 404
     */
    @Override
    public Wire.AuthorityFact lookup(Context ctx, String producer, String eventId) {
        // --- 入参守卫（fact_authority.go:52-54）
        if (!CommentDomainFactService.PRODUCER.equals(producer)
                || eventId == null || eventId.isEmpty() || eventId.length() > 128) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        CommentDomainFactOutboxEntity row = outboxMapper.findByEventId(eventId);
        if (row == null) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        DeliveredAuthority delivered = deliveredCommentAuthority(row);
        if (delivered == null) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        Wire.AuthorityFact authority = delivered.authority;
        CommentDomainFactService.CommentFact payload = delivered.payload;

        // comment_id 必须是正整数且等于其十进制字面量（fact_authority.go:67-70）
        long commentId;
        try {
            commentId = Long.parseLong(payload.commentId);
        } catch (NumberFormatException e) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        if (commentId <= 0 || !String.valueOf(commentId).equals(payload.commentId)) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        CommentIndexEntity comment = commentIndexMapper.findOneByIdPlain(commentId);
        if (comment == null
                || !eq(comment.getTargetType(), payload.targetType)
                || !eq(comment.getTargetId(), payload.targetId)
                || (!CommentDomainFactOutboxMapper.EVENT_TYPE_INTERACTION.equals(payload.eventType)
                    && !String.valueOf(comment.getUserId())
                            .equals(stripSubjectPrefix(payload.subjectRef)))) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        return switch (payload.eventType) {
            case CommentDomainFactOutboxMapper.EVENT_TYPE_CREATED ->
                    authoritativeCreated(authority, payload, eventId, comment, commentId);
            case CommentDomainFactOutboxMapper.EVENT_TYPE_DELETED ->
                    authoritativeDeleted(authority, payload, eventId, comment, commentId);
            case CommentDomainFactOutboxMapper.EVENT_TYPE_INTERACTION ->
                    authoritativeInteraction(authority, payload, row.getFactVersion());
            default -> throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        };
    }

    /**
     * 对应 Go {@code fact_authority.go:82-104} 的 {@code "community.comment.created"} 分支。
     *
     * <p>校验：operation 必须是 {@code create}；source_ref 必须是 {@code rtw.comment/<id>}；
     * event_id 必须是 {@code rtw.comment.<id>.created}；正文行<b>恰好 1 条</b>；
     * 若评论已删除（state==2）则对应的 retract 事实<b>恰好 1 条</b>。
     */
    private Wire.AuthorityFact authoritativeCreated(Wire.AuthorityFact authority,
                                                    CommentDomainFactService.CommentFact payload,
                                                    String eventId,
                                                    CommentIndexEntity comment,
                                                    long commentId) {
        String expectedSourceRef = "rtw.comment/" + payload.commentId;
        if (!"create".equals(payload.operation)
                || !eq(expectedSourceRef, payload.sourceRef)
                || !eventId.equals("rtw.comment." + payload.commentId + ".created")) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        long contentCount = commentContentMapper.countByCommentId(commentId);
        if (contentCount != 1) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        if (comment.getState() == 2) {
            long retractCount = outboxMapper.countByEventId("rtw.comment." + payload.commentId + ".deleted");
            if (retractCount != 1) {
                throw Wire.ERR_AUTHORITY_UNAVAILABLE;
            }
        }
        return authority;
    }

    /**
     * 对应 Go {@code fact_authority.go:105-124} 的 {@code "community.comment.deleted"} 分支。
     *
     * <p>额外校验：评论状态必须是 2（已删除）；必须有<b>已成功投递</b>的前序 created 事实，
     * 且前序的 DC offset < 本条的 offset（否则「删除」早于「创建」到达 DC），
     * 且两条的 subject / target / source_ref / target_revision 全部一致。
     */
    private Wire.AuthorityFact authoritativeDeleted(Wire.AuthorityFact authority,
                                                    CommentDomainFactService.CommentFact payload,
                                                    String eventId,
                                                    CommentIndexEntity comment,
                                                    long commentId) {
        String expectedSourceRef = "rtw.comment/" + payload.commentId;
        if (!"retract".equals(payload.operation)
                || !eq(expectedSourceRef, payload.sourceRef)
                || !eventId.equals("rtw.comment." + payload.commentId + ".deleted")
                || comment.getState() != 2) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        String predecessorId = "rtw.comment." + payload.commentId + ".created";
        CommentDomainFactOutboxEntity predecessorRow = outboxMapper.findByEventId(predecessorId);
        if (predecessorRow == null) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        DeliveredAuthority prior = deliveredCommentAuthority(predecessorRow);
        if (prior == null) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        if (!(prior.authority.technicalReceipt.offset < authority.technicalReceipt.offset)
                || !eq(prior.authority.subjectRef.issuer, authority.subjectRef.issuer)
                || !eq(prior.authority.subjectRef.subjectId, authority.subjectRef.subjectId)
                || !eq(prior.payload.targetType, payload.targetType)
                || !eq(prior.payload.targetId, payload.targetId)
                || !eq(prior.payload.sourceRef, payload.sourceRef)
                || !sameStringPointer(prior.payload.targetRevision, payload.targetRevision)) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        authority.predecessorEventId = predecessorId;
        return authority;
    }

    /**
     * 对应 Go {@code authoritativeCommentInteraction}（fact_authority.go:132-187）。
     *
     * <p>它做的事是<b>把整条互动链从头重放一遍</b>：
     * 按 {@code fact_version, event_id} 顺序取出该用户对该评论的全部互动事实，
     * 要求每条的 {@code old_state} 恰好等于上一条的 {@code new_state}
     * （链首的期望值是 0），且每一跳的 {@code operation} 与状态转移自洽。
     * 重放出的最终状态还必须与 {@code comment_like} 当前行<b>完全一致</b>。
     *
     * <p>⇒ 任何一条历史事实被篡改/漏发/乱序，链子都会在这里断掉 ⇒ 404。
     */
    private Wire.AuthorityFact authoritativeInteraction(Wire.AuthorityFact authority,
                                                        CommentDomainFactService.CommentFact payload,
                                                        Long requestedVersion) {
        if (requestedVersion == null || payload.oldState == null || payload.newState == null
                || !eq(payload.sourceRef, payload.eventId)
                || payload.eventId == null
                || !payload.eventId.startsWith("rtw.comment.interaction.")) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        List<CommentDomainFactOutboxEntity> rows = outboxMapper.selectInteractionStream(
                CommentDomainFactOutboxMapper.EVENT_TYPE_INTERACTION,
                payload.subjectRef, payload.commentId);
        if (rows == null) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        int state = 0;
        String previous = "";
        boolean found = false;
        for (CommentDomainFactOutboxEntity candidate : rows) {
            if (candidate.getDeliveryEnvelope() == null || candidate.getFactVersion() == null) {
                throw Wire.ERR_AUTHORITY_UNAVAILABLE;
            }
            Wire.Event event;
            try {
                event = validator.decodeEnvelope(candidate.getDeliveryEnvelope());
            } catch (RuntimeException e) {
                throw Wire.ERR_AUTHORITY_UNAVAILABLE;
            }
            if (!validator.isValidDelivery(candidate, event)) {
                throw Wire.ERR_AUTHORITY_UNAVAILABLE;
            }
            CommentDomainFactService.CommentFact current = validator.decodePayload(event);
            if (current == null || current.oldState == null || current.newState == null
                    || current.oldState != state
                    || !eq(current.subjectRef, payload.subjectRef)
                    || !eq(current.commentId, payload.commentId)
                    || !eq(current.targetType, payload.targetType)
                    || !eq(current.targetId, payload.targetId)
                    || !sameStringPointer(current.targetRevision, payload.targetRevision)
                    || !validReactionTransition(current.operation, current.oldState, current.newState)) {
                throw Wire.ERR_AUTHORITY_UNAVAILABLE;
            }
            if (candidate.getEventId().equals(payload.eventId)) {
                found = true;
                // 只有「非链首的那一跳」才有前驱事件
                if (current.oldState != 0) {
                    authority.predecessorEventId = previous;
                }
            }
            state = current.newState;
            previous = candidate.getEventId();
        }

        if (!found || (payload.oldState != 0 && (authority.predecessorEventId == null
                || authority.predecessorEventId.isEmpty()))) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }

        // payload.commentId 在 lookup() 入口已校验为正整数字面量，这里安全地转成 long
        CommentLikeEntity record = commentLikeMapper.findOneByUserAndComment(
                subjectId(authority), Long.parseLong(payload.commentId));
        if (record == null || record.getState() != state
                || !eq(record.getTargetType(), payload.targetType)
                || !eq(record.getTargetId(), payload.targetId)) {
            throw Wire.ERR_AUTHORITY_UNAVAILABLE;
        }
        return authority;
    }

    // ====================================================================================
    // 已投递权威事实的解析
    // ====================================================================================

    /**
     * 对应 Go {@code deliveredCommentAuthority(row)}（fact_authority.go:12-40）。
     *
     * <p><b>「已投递」的 7 个条件缺一不可</b>：
     * {@code delivery_status == ACCEPTED} 且 {@code delivered_at} 非空
     * 且 {@code technical_received_at} 非空 且 {@code technical_receipt_id} 非空
     * 且 {@code technical_offset >= 1} 且 {@code technical_input_hash} 是合法 sha256
     * 且 {@code delivery_envelope} 非空；
     * 再叠加：信封可解码、{@code validCommentDelivery} 通过、
     * {@code subject_ref} 可解析成 RTW 主体、{@code validCommentRevision} 通过、
     * {@code source_ref} 非空、{@code event_time}/{@code available_at} 非零值、
     * 以及<b>信封的规范化哈希必须等于落库的 technical_input_hash</b>。
     *
     * <p>最后一条是关键：它证明「我们投递给 DC 的字节」与「我们记账的字节」是同一份。
     *
     * @return null 表示不满足 ⇒ 调用方 404
     */
    private DeliveredAuthority deliveredCommentAuthority(CommentDomainFactOutboxEntity row) {
        if (row.getDeliveryStatus() != Wire.DeliveryStatus.DELIVERY_ACCEPTED.code()
                || row.getDeliveredAt() == null
                || row.getTechnicalReceivedAt() == null
                || row.getTechnicalReceiptId() == null || row.getTechnicalReceiptId().isEmpty()
                || row.getTechnicalOffset() < 1
                || !Wire.validHash(row.getTechnicalInputHash())
                || row.getDeliveryEnvelope() == null) {
            return null;
        }
        Wire.Event event;
        try {
            event = validator.decodeEnvelope(row.getDeliveryEnvelope());
        } catch (RuntimeException e) {
            return null;
        }
        if (!validator.isValidDelivery(row, event)) {
            return null;
        }
        CommentDomainFactService.CommentFact payload = validator.decodePayload(event);
        if (payload == null) {
            return null;
        }
        Wire.Result<Wire.SubjectRef> subject = Wire.parseRTWSubject(payload.subjectRef);
        if (!subject.isOk()
                || !validCommentRevision(payload.targetRevision, payload.revisionStatus)
                || isBlank(payload.sourceRef)
                // Go 用 payload.EventTime.IsZero() / AvailableAt.IsZero()：
                // 零值时间序列化成 "0001-01-01T00:00:00Z"。等价判定 = 该字面量或空。
                || isGoZeroTime(payload.eventTime)
                || isGoZeroTime(payload.availableAt)) {
            return null;
        }

        String hash;
        try {
            hash = Wire.canonicalHash(row.getDeliveryEnvelope().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
        if (!hash.equals(row.getTechnicalInputHash())) {
            return null;
        }

        Wire.AuthorityFact authority = new Wire.AuthorityFact();
        authority.event = event;
        authority.subjectRef = subject.get();
        authority.technicalReceipt = new Wire.TechnicalReceipt();
        authority.technicalReceipt.eventId = row.getEventId();
        authority.technicalReceipt.producer = event.producer;
        authority.technicalReceipt.technicalStatus = "accepted";
        authority.technicalReceipt.receiptId = row.getTechnicalReceiptId();
        authority.technicalReceipt.inputHash = row.getTechnicalInputHash();
        authority.technicalReceipt.offset = row.getTechnicalOffset();
        authority.technicalReceipt.receivedAt = row.getTechnicalReceivedAt()
                .withOffsetSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
        authority.sourceEventHash = hash;

        return new DeliveredAuthority(authority, payload);
    }

    /**
     * 对应 Go {@code validCommentRevision}（fact_authority.go:42-47）：
     * {@code revision == nil ⇒ status 必须 "unknown"；否则 status 必须 "resolved"}。
     *
     * <p>comment 域的每条事实都不带 {@code target_revision}（Go 侧从未赋值）⇒
     * 本域恒走第一条分支。这是「事实自洽」检查，不是业务规则。
     */
    private static boolean validCommentRevision(String revision, String status) {
        if (revision == null) {
            return "unknown".equals(status);
        }
        return !revision.isEmpty() && "resolved".equals(status);
    }

    /**
     * 对应 Go {@code validReactionTransition}（fact_authority.go:189-202）：
     * 四种操作的合法状态转移。
     */
    private static boolean validReactionTransition(String operation, int oldState, int newState) {
        return switch (operation == null ? "" : operation) {
            case "like" -> newState == 1 && oldState != 1;
            case "unlike" -> oldState == 1 && newState == 0;
            case "dislike" -> newState == 2 && oldState != 2;
            case "undislike" -> oldState == 2 && newState == 0;
            default -> false;
        };
    }

    /** 对应 Go {@code sameStringPointer}：两个可空字符串「同为空」或「都非空且相等」。 */
    private static boolean sameStringPointer(String left, String right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.equals(right);
    }

    /** Go {@code time.Time} 零值的 JSON 形态（0001-01-01T00:00:00Z），或空串。 */
    private static boolean isGoZeroTime(String wireTime) {
        return isBlank(wireTime) || wireTime.startsWith("0001-01-01T00:00:00");
    }

    /** 对应 Go {@code subjectID(subject)}：从 {@code SubjectRef.SubjectID} 取整数（解析失败取 0）。 */
    private static long subjectId(Wire.AuthorityFact authority) {
        try {
            return Long.parseLong(authority.subjectRef.subjectId);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    /** 对应 Go {@code strings.TrimPrefix(payload.SubjectRef, "rtw.identity/platform/")}。 */
    private static String stripSubjectPrefix(String subjectRef) {
        final String prefix = "rtw.identity/platform/";
        return subjectRef != null && subjectRef.startsWith(prefix)
                ? subjectRef.substring(prefix.length())
                : subjectRef;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    /** {@code deliveredCommentAuthority} 的返回值对（authority + payload）。 */
    private record DeliveredAuthority(Wire.AuthorityFact authority,
                                      CommentDomainFactService.CommentFact payload) {
    }
}