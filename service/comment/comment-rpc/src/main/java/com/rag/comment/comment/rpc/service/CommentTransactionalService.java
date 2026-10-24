package com.rag.comment.comment.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.comment.comment.rpc.domain.CommentContentEntity;
import com.rag.comment.comment.rpc.domain.CommentDomainFactOutboxEntity;
import com.rag.comment.comment.rpc.domain.CommentIndexEntity;
import com.rag.comment.comment.rpc.domain.CommentLikeEntity;
import com.rag.comment.comment.rpc.domain.SubjectEntity;
import com.rag.comment.comment.rpc.exception.CommentExceptions;
import com.rag.comment.comment.rpc.mapper.CommentContentMapper;
import com.rag.comment.comment.rpc.mapper.CommentDomainFactOutboxMapper;
import com.rag.comment.comment.rpc.mapper.CommentIndexMapper;
import com.rag.comment.comment.rpc.mapper.CommentLikeMapper;
import com.rag.comment.comment.rpc.mapper.SubjectMapper;
import com.rag.comment.comment.rpc.metrics.CommentRpcMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 三个事务边界 —— 对应 Go 端 {@code service/comment/rpc/internal/model/comment.go} 里
 * {@code InsertCommentTx}(:28)、{@code DeleteCommentTx}(:252)、{@code LikeCommentTx}(:472)
 * 三个 {@code conn.Transaction(func(tx) error {...})} 闭包。
 *
 * <h2>⚠️ 为什么必须独立于 {@link CommentDomainService}</h2>
 * <p>Go 侧<b>只有这三个方法</b>是事务性的，其余十三个（FindOne / GetReplyIDsByPage / BatchGet /
 * InsertReport / ManageCommentAttribute / UpdateSubjectState / GetOwnerId …）都<b>没有</b>事务
 * （comment.go:157,184,242 即无事务方法的代表）。
 * 事务边界逐方法不同，挂在同一个 service 上会把
 * {@code GetComment} 这类纯只读路径也拖进事务
 * —— 那会改变隔离级别、加长快照持有时间、并让只读请求可被写事务阻塞。
 *
 * <h2>⚠️ 三个事务里的行锁不可替换</h2>
 * <table border="1">
 *   <caption>悲观锁位置</caption>
 *   <tr><th>事务</th><th>锁</th><th>保护什么</th></tr>
 *   <tr><td>{@link #insertCommentTx}</td><td>无显式锁，靠 {@code uk_comment_fact_stream}
 *       与 {@code idx_target} 的唯一约束仲裁</td>
 *       <td>幂等探针与版本取号</td></tr>
 *   <tr><td>{@link #deleteCommentTx}</td><td>{@code SELECT … FOR UPDATE} on comment_index(:258)</td>
 *       <td>「只有首次删除才做计数回退并产出 retract 事实」</td></tr>
 *   <tr><td>{@link #likeCommentTx}</td><td>{@code SELECT … FOR UPDATE} on comment_like(:489)</td>
 *       <td>同一用户对同一评论的连续互动严格串行</td></tr>
 * </table>
 * 把它们换成乐观重试或应用层锁，都会改变并发下的最终状态。
 */
@Service
public class CommentTransactionalService {

    private static final Logger LOG = LoggerFactory.getLogger(CommentTransactionalService.class);

    private final SubjectMapper subjectMapper;
    private final CommentIndexMapper commentIndexMapper;
    private final CommentContentMapper commentContentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final CommentDomainFactOutboxMapper outboxMapper;
    private final CommentDomainFactService domainFactService;
    private final CommentRpcMetrics metrics;
    private final ObjectMapper objectMapper;

    public CommentTransactionalService(SubjectMapper subjectMapper,
                                       CommentIndexMapper commentIndexMapper,
                                       CommentContentMapper commentContentMapper,
                                       CommentLikeMapper commentLikeMapper,
                                       CommentDomainFactOutboxMapper outboxMapper,
                                       CommentDomainFactService domainFactService,
                                       CommentRpcMetrics metrics,
                                       ObjectMapper objectMapper) {
        this.subjectMapper = subjectMapper;
        this.commentIndexMapper = commentIndexMapper;
        this.commentContentMapper = commentContentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.outboxMapper = outboxMapper;
        this.domainFactService = domainFactService;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    // ====================================================================================
    // ① InsertCommentTx —— Go comment.go:28-155
    // ====================================================================================

    /**
     * 对应 Go {@code InsertCommentTx(ctx, msg, status)}。
     *
     * <p><b>这是 Kafka 审计消费者的唯一落库入口</b>，也是本域最长的一条写路径（10 步）。
     * 逐步对照：
     * <ol>
     *   <li>{@code subjectRef(msg.UserId)} —— uid ≤ 0 直接失败（:29-31）；</li>
     *   <li>三项参数校验（:33-35）；</li>
     *   <li><b>幂等探针</b>（:39-60）：同一条 Kafka 消息重投必须无副作用，
     *       但「同 ID + 身份不同」必须报错 —— 见方法内注释；</li>
     *   <li>插 {@code comment_content}（:66-77）→ 插 {@code comment_index}（:80-96）；</li>
     *   <li>upsert {@code subject} 计数（:99-123）；</li>
     *   <li>父评论回复数 +1，作者回复时置 attribute 第 1 位（:126-141）；</li>
     *   <li>追加 created 事实（:143-153）。</li>
     * </ol>
     *
     * @param status 敏感词判定结果：0 正常 / 1 审核中（决定 {@code comment_index.state}）
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void insertCommentTx(CommentAuditMessage msg, int status) {
        // --- ① 主体引用（uid ≤ 0 ⇒ 失败，Go comment.go:29-31）
        String subjectRef = CommentDomainFactService.subjectRef(msg.getUserId());
        // --- ② 参数校验（Go comment.go:33-35）
        if (msg.getCommentId() <= 0 || isBlank(msg.getTargetType()) || isBlank(msg.getTargetId())) {
            throw new IllegalArgumentException("invalid comment creation target or id");
        }

        // --- ③ 幂等探针（Go comment.go:39-60）
        //      同一条 Kafka 消息重投 ⇒ 无副作用；同 ID 但身份不同 ⇒ 报错。
        CommentIndexEntity existing = null;
        try {
            existing = commentIndexMapper.findOneById(msg.getCommentId());
        } catch (RuntimeException e) {
            metrics.observePostgresError("InsertCommentTx", "query");
            throw e;
        }
        if (existing != null) {
            if (!existing.getTargetType().equals(msg.getTargetType())
                    || !existing.getTargetId().equals(msg.getTargetId())
                    || existing.getUserId() != msg.getUserId()
                    || existing.getRootId() != msg.getRootId()
                    || existing.getParentId() != msg.getParentId()) {
                throw new IllegalStateException("comment id " + msg.getCommentId()
                        + " reused with conflicting identity");
            }
            CommentContentEntity content = commentContentMapper.selectByIds(List.of(msg.getCommentId())).stream()
                    .findFirst().orElse(null);
            if (content == null) {
                throw new IllegalStateException("comment content missing for reused id "
                        + msg.getCommentId());
            }
            if (!java.util.Objects.equals(content.getContent(), msg.getContent())) {
                throw new IllegalStateException("comment id " + msg.getCommentId()
                        + " reused with conflicting content");
            }
            // 完全一致 ⇒ 幂等命中，直接提交（无任何写操作）
            LOG.debug("idempotent comment insert skipped: comment_id={}", msg.getCommentId());
            return;
        }

        Instant createTime = Instant.ofEpochSecond(msg.getCreateTime());
        OffsetDateTime createdAt = createTime.atOffset(ZoneOffset.UTC);
        String meta = normalizeCommentMeta(msg.getMeta());

        // --- ④ 插 comment_content（Go comment.go:66-77）
        // ⚠️ created_at = Kafka 消息的 create_time（评论创建时刻）；
        //    updated_at 由 SQL 写 CURRENT_TIMESTAMP（入库时刻）——
        //    对齐 Go 的 GORM autoUpdateTime（curTime）。见 CommentContentMapper#insert。
        CommentContentEntity content = new CommentContentEntity();
        content.setCommentId(msg.getCommentId());
        content.setContent(msg.getContent());
        content.setMeta(meta);
        content.setCreatedAt(createdAt);
        try {
            commentContentMapper.insert(content);
        } catch (RuntimeException e) {
            metrics.observePostgresError("InsertCommentTx", "insert");
            throw e;
        }

        // --- ⑤ 插 comment_index（Go comment.go:80-96）
        // ⚠️ created_at = Kafka 消息的 create_time（评论创建时刻）；
        //    updated_at 由 SQL 写 CURRENT_TIMESTAMP（入库时刻）——
        //    对齐 Go 的 GORM autoUpdateTime（curTime）。见 CommentIndexMapper#insert。
        CommentIndexEntity index = new CommentIndexEntity();
        index.setId(msg.getCommentId());
        index.setTargetType(msg.getTargetType());
        index.setTargetId(msg.getTargetId());
        index.setUserId(msg.getUserId());
        index.setRootId(msg.getRootId());
        index.setParentId(msg.getParentId());
        index.setState(status);
        index.setAttribute(msg.getAttribute());
        index.setCreatedAt(createdAt);
        try {
            commentIndexMapper.insert(index);
        } catch (RuntimeException e) {
            metrics.observePostgresError("InsertCommentTx", "insert");
            throw e;
        }

        // --- ⑥ upsert subject 计数（Go comment.go:99-123）
        try {
            subjectMapper.upsertCommentSubject(msg.getTargetType(), msg.getTargetId(),
                    msg.getRootId() == 0 ? 1 : 0, msg.getOwnerId());
        } catch (RuntimeException e) {
            metrics.observePostgresError("InsertCommentTx", "upsert");
            throw e;
        }

        // --- ⑦ 父评论回复数 +1（Go comment.go:126-141）
        if (msg.getParentId() != 0) {
            int affected;
            try {
                affected = commentIndexMapper.incrementParentReplyCount(
                        msg.getParentId(), msg.getUserId() == msg.getOwnerId());
            } catch (RuntimeException e) {
                metrics.observePostgresError("InsertCommentTx", "update");
                throw e;
            }
            if (affected == 0) {
                // Go comment.go:138-140：父评论不存在 ⇒ 整个事务回滚
                throw new CommentExceptions.CommentNotFound();
            }
        }

        // --- ⑧ 追加 created 事实（Go comment.go:143-153）
        String commentIdStr = String.valueOf(msg.getCommentId());
        String factId = "rtw.comment." + commentIdStr + ".created";
        CommentDomainFactService.CommentFact fact = new CommentDomainFactService.CommentFact();
        fact.eventId = factId;
        fact.eventType = CommentDomainFactOutboxMapper.EVENT_TYPE_CREATED;
        fact.aggregateId = commentIdStr;
        fact.operationId = factId;
        fact.subjectRef = subjectRef;
        fact.targetType = msg.getTargetType();
        fact.targetId = msg.getTargetId();
        fact.operation = "create";
        fact.sourceRef = "rtw.comment/" + commentIdStr;
        fact.commentId = commentIdStr;
        if (msg.getParentId() > 0) {
            fact.parentCommentId = String.valueOf(msg.getParentId());
        }
        fact.visibilityState = status;
        fact.eventTime = CommentDomainFactService.DateTimes.toRfc3339Nano(createTime);
        domainFactService.append(fact);
    }

    /**
     * 对应 Go {@code normalizeCommentMeta}（comment.go:222-240），逐字照抄：
     * <pre>
     * meta = strings.TrimSpace(meta)
     * if meta == "" { return "{}" }
     * var payload any
     * if err := json.Unmarshal([]byte(meta), &amp;payload); err == nil { return meta }   // ← 原样返回
     * fallback, _ := json.Marshal(map[string]string{"raw": meta})
     * if err != nil { return "{}" }
     * return string(fallback)
     * </pre>
     *
     * <p><b>⚠️ 两条易错点</b>：
     * <ol>
     *   <li>「合法 JSON ⇒ <b>原样</b>返回」—— 不做重排、不做规范化。
     *       重排会让「同一条评论的 meta 在缓存与库里字节不同」，虽然 JSON 等价。</li>
     *   <li>兜底的 {@code json.Marshal} <b>开启 HTML 转义</b>
     *       （{@code <} → {@code \u003c}），Jackson 默认<b>不</b>转义
     *       ⇒ 用 Jackson 直接序列化会产出与 Go 不同的字节。见 {@link #goJsonQuote}。</li>
     * </ol>
     */
    String normalizeCommentMeta(String meta) {
        if (meta == null) {
            return "{}";
        }
        String trimmed = meta.strip();
        if (trimmed.isEmpty()) {
            return "{}";
        }
        try {
            objectMapper.readTree(trimmed);
            // Go 侧只要能 Unmarshal 成 any 就算合法 —— JSON 标量（"1"、true）同样合法
            return trimmed;
        } catch (Exception parseFail) {
            return "{\"raw\":" + goJsonQuote(trimmed) + "}";
        }
    }

    /**
     * 按 Go {@code encoding/json} 的字符串转义规则加引号。
     *
     * <p>与 Jackson 的差异只在 <b>HTML 字符</b>：Go 会把 {@code <} {@code >} {@code &}
     * 转成 {@code \u003c} {@code \u003e} {@code \u0026}（{@code SetEscapeHTML(true)} 是默认行为），
     * Jackson 不会。这里补上这三处，其余（引号、反斜杠、控制字符、U+2028/2029）两者一致。
     */
    static String goJsonQuote(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 2);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                case 0x2028 -> sb.append("\\u2028");
                case 0x2029 -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    // ====================================================================================
    // ② DeleteCommentTx —— Go comment.go:252-353
    // ====================================================================================

    /**
     * 对应 Go {@code DeleteCommentTx(ctx, commentId, userId, targetType, targetId)}，返回剩余总数。
     *
     * <p><b>⚠️ 幂等删除 + 只扣一次计数</b>（Go comment.go:287 的 {@code if comment.State != 2}）：
     * 重复删除同一条评论时第二次 {@code state} 已是 2，<b>跳过</b>计数回退与 retract 事实，
     * 只重读 remainCount 返回。少了这个判断，连续两次删除会让总评论数少 2。
     *
     * @return 删除后该评论区的剩余总评论数
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public long deleteCommentTx(long commentId, long userId, String targetType, String targetId) {
        // --- ① 锁评论行（Go comment.go:257-269）
        CommentIndexEntity comment = commentIndexMapper.selectForUpdate(commentId);
        if (comment == null) {
            throw new CommentExceptions.CommentNotFound();
        }
        if (!comment.getTargetType().equals(targetType) || !comment.getTargetId().equals(targetId)) {
            // Go comment.go:267-269：target 对不上也报 CommentNotFound（不泄露「存在但不属于该 target」）
            throw new CommentExceptions.CommentNotFound();
        }

        // --- ② 查 subject（Go comment.go:273-281）
        SubjectEntity subject = subjectMapper.findOneByTarget(targetType, targetId);
        if (subject == null) {
            throw new CommentExceptions.SubjectNotFound();
        }

        // --- ③ 鉴权（Go comment.go:282-284）：本人 或 up 主
        if (userId != comment.getUserId() && userId != subject.getOwnerId()) {
            throw new CommentExceptions.CommentForbidden();
        }

        // --- ④ 只有首次删除才做计数回退 + 产出 retract 事实（Go comment.go:287-341）
        if (comment.getState() != 2) {
            try {
                commentIndexMapper.markDeleted(commentId);
                subjectMapper.decrementCounts(targetType, targetId, comment.getRootId() == 0);
            } catch (RuntimeException e) {
                metrics.observePostgresError("DeleteCommentTx", "update");
                throw e;
            }

            if (comment.getParentId() != 0) {
                int affected;
                try {
                    affected = commentIndexMapper.decrementParentReplyCount(comment.getParentId());
                } catch (RuntimeException e) {
                    metrics.observePostgresError("DeleteCommentTx", "update");
                    throw e;
                }
                if (affected == 0) {
                    throw new CommentExceptions.CommentNotFound();
                }
            }

            String commentIdStr = String.valueOf(commentId);
            String factId = "rtw.comment." + commentIdStr + ".deleted";
            CommentDomainFactService.CommentFact fact = new CommentDomainFactService.CommentFact();
            fact.eventId = factId;
            fact.eventType = CommentDomainFactOutboxMapper.EVENT_TYPE_DELETED;
            fact.aggregateId = commentIdStr;
            fact.operationId = factId;
            fact.subjectRef = CommentDomainFactService.subjectRef(comment.getUserId());
            fact.operatorRef = CommentDomainFactService.subjectRef(userId);
            fact.targetType = targetType;
            fact.targetId = targetId;
            fact.operation = "retract";
            fact.sourceRef = "rtw.comment/" + commentIdStr;
            fact.commentId = commentIdStr;
            if (comment.getParentId() > 0) {
                fact.parentCommentId = String.valueOf(comment.getParentId());
            }
            fact.visibilityState = 2;
            fact.eventTime = CommentDomainFactService.DateTimes.toRfc3339Nano(Instant.now());
            domainFactService.append(fact);
        }

        // --- ⑤ 重读 subject 取剩余总数（Go comment.go:344-347）
        SubjectEntity remain = subjectMapper.selectForRemainCount(targetType, targetId);
        if (remain == null) {
            // Go 用 First，零行 ⇒ ErrRecordNotFound ⇒ 整个事务回滚
            throw new CommentExceptions.SubjectNotFound();
        }
        return remain.getTotalCount();
    }

    // ====================================================================================
    // ③ LikeCommentTx —— Go comment.go:472-598
    // ====================================================================================

    /**
     * 对应 Go {@code LikeCommentTx(ctx, userId, commentId, targetType, targetId, actionType, ownerId)}。
     *
     * <p><b>actionType → (state, likeDiff, dislikeDiff) 的映射是本域最易错的一处</b>：
     * <pre>
     * 1 like      : oldState==1 ⇒ no-op；否则 newState=1, like=+1（若 oldState==2 则 dislike=-1）
     * 2 unlike    : oldState!=1 ⇒ no-op；否则 newState=0, like=-1
     * 3 dislike   : oldState==2 ⇒ no-op；否则 newState=2, dislike=+1（若 oldState==1 则 like=-1）
     * 4 undislike : oldState!=2 ⇒ no-op；否则 newState=0, dislike=-1
     * </pre>
     * ⚠️ <b>no-op 时<b>不</b>产出事实</b>（Go comment.go:518,528,534,542 直接 {@code return nil}）——
     * 重复点赞不会在事实流里留下噪声。这是 L2 语义。
     *
     * <p>⚠️ 但 <b>锁锚行已经插入了</b>：no-op 的 {@code return nil} 发生在锁锚插入<b>之后</b>，
     * 所以首次重复点击仍会把 {@code comment_like} 的那一行落库。这是 Go 的现状。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void likeCommentTx(long userId, long commentId, String targetType, String targetId,
                              int actionType, long ownerId) {
        // --- ① 主体引用（Go comment.go:473-476）
        String actorRef = CommentDomainFactService.subjectRef(userId);
        // --- ② 参数校验（Go comment.go:477-479）
        if (commentId <= 0 || isBlank(targetType) || isBlank(targetId)) {
            throw new IllegalArgumentException("invalid comment interaction target or id");
        }

        // --- ③ 播锁锚（Go comment.go:483-487）
        commentLikeMapper.insertLockAnchor(userId, commentId, targetType, targetId);

        // --- ④ 锁住它（Go comment.go:488-495）
        CommentLikeEntity likeRecord = commentLikeMapper.selectForUpdate(userId, commentId);
        if (likeRecord == null) {
            metrics.observePostgresError("LikeCommentTx", "lock_query");
            throw new IllegalStateException("comment_like lock anchor missing for user=" + userId
                    + " comment=" + commentId);
        }

        // --- ⑤ 读评论（Go comment.go:497-510）；⚠️ state==2（已删除）也算「不存在」
        CommentIndexEntity comment = commentIndexMapper.findOneByIdPlain(commentId);
        if (comment == null) {
            throw new CommentExceptions.CommentNotFound();
        }
        if (!comment.getTargetType().equals(targetType) || !comment.getTargetId().equals(targetId)
                || comment.getState() == 2) {
            throw new CommentExceptions.CommentNotFound();
        }

        // --- ⑥ 计算状态差值（Go comment.go:512-549）
        int oldState = likeRecord.getState();
        int newState;
        long likeDiff = 0L;
        long dislikeDiff = 0L;
        switch (actionType) {
            case 1 -> {
                if (oldState == 1) {
                    return;                       // 重复点赞 = no-op，不产出事实
                }
                newState = 1;
                likeDiff = 1L;
                if (oldState == 2) {
                    dislikeDiff = -1L;
                }
            }
            case 2 -> {
                if (oldState != 1) {
                    return;
                }
                newState = 0;
                likeDiff = -1L;
            }
            case 3 -> {
                if (oldState == 2) {
                    return;
                }
                newState = 2;
                dislikeDiff = 1L;
                if (oldState == 1) {
                    likeDiff = -1L;
                }
            }
            case 4 -> {
                if (oldState != 2) {
                    return;
                }
                newState = 0;
                dislikeDiff = -1L;
            }
            default -> throw new IllegalArgumentException("未知的操作类型");
        }

        // --- ⑦ 更新互动行（Go comment.go:552-558）
        try {
            commentLikeMapper.updateState(userId, commentId, newState);
        } catch (RuntimeException e) {
            metrics.observePostgresError("LikeCommentTx", "update");
            throw e;
        }

        // --- ⑧ 更新评论统计（Go comment.go:561-583）
        boolean ownerAttrSet = (userId == ownerId) && (newState == 1);
        boolean ownerAttrClear = (userId == ownerId) && (newState != 1);
        if (likeDiff != 0 || dislikeDiff != 0 || ownerAttrSet || ownerAttrClear) {
            try {
                commentIndexMapper.applyInteractionDeltas(commentId, likeDiff, dislikeDiff,
                        ownerAttrSet, ownerAttrClear);
            } catch (RuntimeException e) {
                metrics.observePostgresError("LikeCommentTx", "update");
                throw e;
            }
        }

        // --- ⑨ 产出互动事实（Go comment.go:585-596）
        String factId = CommentDomainFactService.newCommentInteractionId();
        CommentDomainFactService.CommentFact fact = new CommentDomainFactService.CommentFact();
        fact.eventId = factId;
        fact.eventType = CommentDomainFactOutboxMapper.EVENT_TYPE_INTERACTION;
        fact.aggregateId = String.valueOf(commentId);
        fact.operationId = factId;
        fact.subjectRef = actorRef;
        fact.targetType = targetType;
        fact.targetId = targetId;
        fact.operation = switch (actionType) {
            case 1 -> "like";
            case 2 -> "unlike";
            case 3 -> "dislike";
            case 4 -> "undislike";
            default -> throw new IllegalStateException("unreachable action type " + actionType);
        };
        fact.sourceRef = factId;
        fact.commentId = String.valueOf(commentId);
        if (comment.getParentId() > 0) {
            fact.parentCommentId = String.valueOf(comment.getParentId());
        }
        fact.oldState = oldState;
        fact.newState = newState;
        fact.visibilityState = comment.getState();
        fact.eventTime = CommentDomainFactService.DateTimes.toRfc3339Nano(Instant.now());
        domainFactService.append(fact);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }
}