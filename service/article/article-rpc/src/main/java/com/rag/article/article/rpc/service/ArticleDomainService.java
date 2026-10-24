package com.rag.article.article.rpc.service;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.CreateArticleRequest;
import com.rag.article.article.proto.CreateArticleResponse;
import com.rag.article.article.proto.DeleteArticleRequest;
import com.rag.article.article.proto.DeleteArticleResponse;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.article.article.proto.ListArticlesRequest;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.article.article.proto.UpdateArticleRequest;
import com.rag.article.article.proto.UpdateArticleResponse;
import com.rag.article.article.proto.UploadFileRequest;
import com.rag.article.article.proto.UploadFileResponse;
import com.rag.article.article.rpc.client.ArticleObjectStorageClient;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.exception.ArticleExceptions;
import com.rag.article.article.rpc.mapper.ArticleMapper;
import com.rag.article.article.rpc.mapper.JsonbTypeHandler;
import com.rag.article.article.rpc.metrics.ArticleRpcMetrics;
import com.rag.article.article.rpc.mq.ArticleSyncEventTypes;
import com.rag.common.spring.SnowflakeBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * article 域的六个用例（作者视角 + 文件上传）—— 对应 Go 端 {@code internal/logic/} 下的
 * {@code create / get / update / delete / list / upload_file} 六个 logic。
 *
 * <h2>为什么六个 logic 合并成一个 Bean</h2>
 * 它们有<b>同一变化原因</b>（文章领域用例）与<b>同一依赖边界</b>（article 表 + MinIO + Kafka）。
 * 拆分到六个类的收益只是文件名对应，而代价是六个几乎相同的依赖清单。
 * <br>⚠️ <b>但多步事务不能放在这里</b>：它们在 {@link ArticleAtomicService}
 * （self-invocation 会让 {@code @Transactional} 静默失效，AGENTS.md §3.3）。
 * 公开读路径同理在 {@link ArticlePublicDomainService}（不同持久化语义）。
 *
 * <h2>⚠️ 本类的 6 个方法与 Go logic 的<b>语句顺序逐条一致</b></h2>
 * 尤其是 create：<b>先 MinIO 后 DB</b>（Go {@code create_article_logic.go:61-113}）。
 * 这意味着 MinIO 成功但 DB 失败时会留下孤儿对象 —— <b>Go 明确接受</b>：
 * 「孤儿可接受，因文件可再生」（FileTree §12.3）。
 * 不要"改成先写 DB"或"加补偿删除"：那会改变失败时的可观察状态。
 */
@Service
public class ArticleDomainService {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleDomainService.class);

    private final ArticleMapper articleMapper;
    private final ArticleObjectStorageClient storage;
    private final ArticleAtomicService atomicService;
    private final ArticlePublicDomainService publicService;
    private final ArticleRpcMetrics metrics;
    private final SnowflakeBean snowflake;
    private final com.rag.article.article.rpc.config.ArticleRpcProperties properties;
    private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    public ArticleDomainService(ArticleMapper articleMapper,
                                ArticleObjectStorageClient storage,
                                ArticleAtomicService atomicService,
                                ArticlePublicDomainService publicService,
                                ArticleRpcMetrics metrics,
                                SnowflakeBean snowflake,
                                com.rag.article.article.rpc.config.ArticleRpcProperties properties,
                                org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate) {
        this.articleMapper = articleMapper;
        this.storage = storage;
        this.atomicService = atomicService;
        this.publicService = publicService;
        this.metrics = metrics;
        this.snowflake = snowflake;
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
    }

    // ==================================================================
    // 1. CreateArticle
    // ==================================================================

    /**
     * 对应 Go {@code create_article_logic.go:42-133}。
     *
     * <p><b>语句顺序（不可调换）</b>：
     * <ol>
     *   <li>雪花 ID（失败 ⇒ Internal）</li>
     *   <li>MinIO put {@code <articlePath><id>.md}，{@code text/markdown}
     *       （失败 ⇒ <b>Internal</b>，且<b>不</b>计 file_upload 指标）</li>
     *   <li>插入 article 行（status = <b>REVIEWING</b>，ext_info 写 8 个初始键）</li>
     *   <li>推 Kafka 审核消息（key = articleID；失败 ⇒ <b>Internal</b>，<b>行已插入不回滚</b>）</li>
     * </ol>
     *
     * <p>⚠️ <b>第 4 步失败不回滚第 3 步的行</b>：Go 没有事务，文章行会留下一条
     * {@code REVIEWING} 且无审核任务的记录。这是 Go 的既有行为
     * （人工/补偿可恢复），<b>不要"顺手"加上事务</b>。
     */
    public CreateArticleResponse createArticle(CreateArticleRequest in) {
        long id = snowflake.nextId();
        String articleId = String.valueOf(id);

        // ① MinIO 写正文（Go: objectName = ArticlePath + articleID + ".md"，注意<b>无哈希</b>）
        String objectName = storage.getObjectNamePrefixForArticle() + articleId + ".md";
        long start = System.nanoTime();
        try {
            storage.put(objectName, in.getMarkdownContent().getBytes(StandardCharsets.UTF_8),
                    "text/markdown", null);
        } catch (Exception e) {
            metrics.minioError("put");
            throw ArticleExceptions.raw(e);
        }
        metrics.observeMinio("put", System.nanoTime() - start);
        metrics.fileUpload("markdown");

        // ② 组装实体。ext_info 的 8 个初始键是 L1 契约（消费者按它们判幂等）
        Map<String, String> extInfo = new LinkedHashMap<>();
        extInfo.put(ArticleSyncEventTypes.EXT_PUBLISH_STAGE, "queued");
        extInfo.put(ArticleSyncEventTypes.EXT_RECO_SYNC_STATE, "pending_review");
        extInfo.put(ArticleSyncEventTypes.EXT_LAST_SYNC_ERROR, "");
        extInfo.put(ArticleSyncEventTypes.EXT_LAST_SYNC_EVENT_ID, "");
        extInfo.put(ArticleSyncEventTypes.EXT_LAST_SYNC_VERSION, "0");
        extInfo.put(ArticleSyncEventTypes.EXT_PENDING_SYNC_REASON, ArticleSyncEventTypes.REASON_CREATE);
        extInfo.put(ArticleSyncEventTypes.EXT_LAST_SYNC_REASON, ArticleSyncEventTypes.REASON_CREATE);
        // ⚠️ review_nonce 初始值 = articleID 本身（Go: mqs.ExtReviewNonce: articleID）
        extInfo.put(ArticleSyncEventTypes.EXT_REVIEW_NONCE, articleId);

        ArticleEntity article = new ArticleEntity();
        article.setId(articleId);
        article.setTitle(in.getTitle());
        article.setBrief(in.hasBrief() ? in.getBrief() : "");
        article.setContent(objectName);
        article.setCoverImageUrl(in.hasCoverImageUrl() ? in.getCoverImageUrl() : "");
        article.setManualTypeTag(in.getManualTypeTag());
        article.setSecondaryTags(new ArrayList<>(in.getSecondaryTagsList()));
        article.setAuthorId(in.getAuthorId());
        article.setStatus(ArticleStatus.REVIEWING_VALUE);
        article.setExtInfo(extInfo);
        article.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        article.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        articleMapper.insert(article);
        metrics.article("create");
        metrics.articleStatus("reviewing");

        // ③ 推审核消息（Go: ReviewNonce = articleID，作幂等键）
        ArticleSyncEventTypes.ArticleReviewMessage msg = new ArticleSyncEventTypes.ArticleReviewMessage(
                articleId, in.getAuthorId(), objectName, articleId);
        try {
            kafkaTemplate.send(reviewTopic(), articleId, JsonbTypeHandler.writeJson(msg)).get();
        } catch (Exception e) {
            metrics.kafkaPushError("article_review");
            // Go: status.Error(codes.Internal, err.Error()) —— 注意行不回滚
            throw ArticleExceptions.internal(e.getMessage(), e);
        }

        return CreateArticleResponse.newBuilder().setArticleId(articleId).build();
    }

    // ==================================================================
    // 2. GetArticle
    // ==================================================================

    /**
     * 对应 Go {@code get_article_logic.go:40-124} + 公开分支 {@code :126-168}。
     *
     * <p><b>⚠️ 三条分支的入口判定顺序不可调换</b>：
     * <pre>
     * if in.PublicOnly { return getPublicArticle(in) }              // ① 最先
     * if in.RequesterId != "" &amp;&amp; in.IncrView {
     *     return InvalidArgument("author read cannot increment view") // ② 其次
     * }
     * </pre>
     * ⇒ {@code PublicOnly=true} 时<b>跳过</b>第 ② 条的校验。
     * 把两条判断调换会让"公开读 + 作者身份 + 计阅读量"从 InvalidArgument 变成正常返回。
     */
    public GetArticleResponse getArticle(GetArticleRequest in) {
        // ① 公开读走独立路径（读者视角：冻结 revision）
        if (in.getPublicOnly()) {
            ArticlePublicDomainService.PublicArticleView view =
                    publicService.getPublicArticle(in.getArticleId(), in.getIncrView());
            return GetArticleResponse.newBuilder()
                    .setArticle(ArticlePublicDomainService.toProtoArticle(view))
                    .build();
        }

        // ② 作者身份 + 计阅读量 = 非法组合
        if (!in.getRequesterId().isEmpty() && in.getIncrView()) {
            throw ArticleExceptions.authorReadCannotIncrementView();
        }

        // ③ 作者视角：读当前源行
        ArticleEntity article = articleMapper.selectById(in.getArticleId());
        if (article == null) {
            throw ArticleExceptions.articleNotFound();
        }

        // ④ 作者校验（仅当传了 requesterId）
        if (!in.getRequesterId().isEmpty() && !article.getAuthorId().equals(in.getRequesterId())) {
            throw ArticleExceptions.articleAuthorDiffers();
        }

        // ⑤ 浏览量自增：🔴 best-effort，失败只记日志（Go :72-75）
        if (in.getIncrView()) {
            try {
                articleMapper.incrViewCount(in.getArticleId());
            } catch (Exception e) {
                // ⚠️ 此处**不能记任何指标**。Go get_article_logic.go:70-78 失败时只 LogBusinessErr；
                //    曾调 minioError("get") —— 那是一次 **DB 写失败**，记到 MinIO 错误计数上会脏化
                //    「对象存储是否故障」这个核心信号，让 MinIO 告警误报。
                //    并且 Go 这条路径的该计数器永远是 0。
                LOG.warn("increment article view count failed (best-effort, ignored): articleId={} err={}",
                        in.getArticleId(), e.getMessage());
            }
            article.setViewCount(article.getViewCount() + 1);
        }

        // ⑥ 读正文（MinIO）
        String markdown;
        long start = System.nanoTime();
        try {
            markdown = storage.readString(article.getContent());
        } catch (Exception e) {
            metrics.minioError("get");
            throw ArticleExceptions.raw(e);
        }
        metrics.observeMinio("get", System.nanoTime() - start);

        return GetArticleResponse.newBuilder()
                .setArticle(toAuthorProtoArticle(article, markdown))
                .build();
    }

    /** 作者视角的 article → proto（字段全部取<b>当前源行</b>，与公开视角不同）。 */
    static Article toAuthorProtoArticle(ArticleEntity a, String markdown) {
        Article.Builder builder = Article.newBuilder()
                .setId(nullToEmpty(a.getId()))
                .setTitle(nullToEmpty(a.getTitle()))
                .setBrief(nullToEmpty(a.getBrief()))
                .setMarkdownContent(nullToEmpty(markdown))
                .setCoverImageUrl(nullToEmpty(a.getCoverImageUrl()))
                .setManualTypeTag(nullToEmpty(a.getManualTypeTag()))
                .setAuthorId(nullToEmpty(a.getAuthorId()))
                .setCreateTime(a.getCreatedAt() == null ? 0L : a.getCreatedAt().toInstant().toEpochMilli())
                .setUpdateTime(a.getUpdatedAt() == null ? 0L : a.getUpdatedAt().toInstant().toEpochMilli())
                // ⚠️ 作者视角 status 是**真实状态**（可能是 REVIEWING），与公开视角恒 PUBLISHED 不同
                .setStatus(ArticleStatus.forNumber(a.getStatus()))
                .setViewCount(a.getViewCount())
                .setLikeCount(a.getLikeCount())
                .setCommentCount(a.getCommentCount())
                .setShareCount(a.getShareCount());
        if (a.getSecondaryTags() != null) {
            builder.addAllSecondaryTags(a.getSecondaryTags());
        }
        // Go cloneStringMap: 空 map ⇒ 返回 nil（protobuf 里 map 为空 = 不输出该字段）
        if (a.getExtInfo() != null && !a.getExtInfo().isEmpty()) {
            builder.putAllExtInfo(a.getExtInfo());
        }
        return builder.build();
    }

    // ==================================================================
    // 3. UpdateArticle —— 三条分支
    // ==================================================================

    /**
     * 对应 Go {@code update_article_logic.go:46-127}。
     *
     * <h2>🔴 三条分支的判定顺序是本方法的核心语义</h2>
     * <pre>
     * switch {
     * case prevPublished &amp;&amp; in.Status != nil &amp;&amp; *in.Status != UNSPECIFIED &amp;&amp; requestedStatus != PUBLISHED:
     *     updatePublishedToSourceOnly(...)                    // A：撤回发布
     * case prevPublished &amp;&amp; sourceChanged:
     *     SetSyncState(queued, pending_review, update, "", now, "")
     *     status = REVIEWING; setReviewNonce; Update; enqueueReview   // B：重审
     * default:
     *     if 显式非发布 { status = requestedStatus }
     *     review := status == REVIEWING &amp;&amp; (sourceChanged || prevStatus != REVIEWING)
     *     if review { setReviewNonce }
     *     Update
     *     if review { enqueueReview }                        // C
     * }
     * </pre>
     *
     * <p><b>⚠️ A 优先于 B</b>：已发布文章<b>同时</b>改了内容并显式降级时，
     * 走 A（撤回发布）而<b>不是</b> B（重审）。B 会把文章留在 PUBLISHED 并重审，
     * 那会让"作者主动降级"变成"重新审核"，是语义反转。
     *
     * <p><b>⚠️ B 路径<b>不</b>把 last_sync_event_id 清成非空</b>：Go 传的是 {@code ""}，
     * 见 {@code update_article_logic.go:87} 的参数 {@code SetSyncState(article, "queued", "pending_review", …, "", now.UnixMilli(), "")}。
     * 第五个参数是 eventID，置空 ⇒ 审核消费者的幂等判据
     * {@code pending && lastSyncEventID != ""} 为 false ⇒ 新一轮审核可以正常入队。
     */
    public UpdateArticleResponse updateArticle(UpdateArticleRequest in) {
        // ⚠️ Go 这里是**裸返回**（update_article_logic.go:53-63），不是 NotFound：
        //    model/article.go:81-88 的 FindOne 在找不到时返回 (nil, gorm.ErrRecordNotFound)，
        //    而 err.Error() 的字面量就是 "record not found" ⇒ grpc-go 包成 UNKNOWN + 该文案。
        //    注意 Go 里那个 `if article == nil { NotFound }` 分支**不可达**（FindOne 从不返回 (nil, nil)）。
        //    ⇒ 本处用 articleNotFoundMessage() 返回 **UNKNOWN + "record not found"**，
        //      既保住 code（→ api default → 1015），又把文案对齐。
        ArticleEntity article = articleMapper.selectById(in.getArticleId());
        if (article == null) {
            throw ArticleExceptions.articleNotFoundRaw();
        }
        int prevStatus = article.getStatus();
        boolean prevPublished = prevStatus == ArticleStatus.PUBLISHED_VALUE;

        boolean sourceChanged = applySourceUpdates(article, in);

        int requestedStatus = prevStatus;
        if (in.hasStatus() && in.getStatus() != ArticleStatus.ARTICLE_STATUS_UNSPECIFIED) {
            requestedStatus = in.getStatusValue();
        }
        long nowMs = System.currentTimeMillis();

        boolean explicitNonPublish = in.hasStatus()
                && in.getStatus() != ArticleStatus.ARTICLE_STATUS_UNSPECIFIED
                && requestedStatus != ArticleStatus.PUBLISHED_VALUE;

        if (prevPublished && explicitNonPublish) {
            // ---------- 分支 A：已发布 → 显式降级 ----------
            String eventId = newEventId();
            article.setStatus(requestedStatus);
            Map<String, String> extInfo = ArticleSyncEventTypes.ensureExtInfo(article.getExtInfo());
            article.setExtInfo(extInfo);
            ArticleSyncEventTypes.setSyncState(extInfo, "source_only", "pending",
                    ArticleSyncEventTypes.REASON_STATUS_CHANGE, eventId, nowMs, "");
            atomicService.retractAndUpdate(article, eventId, nowMs,
                    ArticleSyncEventTypes.REASON_STATUS_CHANGE);
        } else if (prevPublished && sourceChanged) {
            // ---------- 分支 B：已发布 + 源变更 → 重审 ----------
            Map<String, String> extInfo = ArticleSyncEventTypes.ensureExtInfo(article.getExtInfo());
            article.setExtInfo(extInfo);
            ArticleSyncEventTypes.setSyncState(extInfo, "queued", "pending_review",
                    ArticleSyncEventTypes.REASON_UPDATE, "", nowMs, "");
            article.setStatus(ArticleStatus.REVIEWING_VALUE);
            setReviewNonce(article);
            articleMapper.updateById(article);
            enqueueReview(article);
        } else {
            // ---------- 分支 C：其余 ----------
            if (explicitNonPublish) {
                article.setStatus(requestedStatus);
            }
            boolean review = article.getStatus() == ArticleStatus.REVIEWING_VALUE
                    && (sourceChanged || prevStatus != ArticleStatus.REVIEWING_VALUE);
            if (review) {
                setReviewNonce(article);
            }
            articleMapper.updateById(article);
            if (review) {
                enqueueReview(article);
            }
        }

        metrics.article("update");
        return UpdateArticleResponse.newBuilder().setSuccess(true).build();
    }

    /**
     * 应用源字段变更 —— 对应 Go {@code applySourceUpdates}（{@code update_article_logic.go:139-190}）。
     *
     * <h2>⚠️ 正文对象名带内容哈希，这是不可变性的关键</h2>
     * Go {@code articleMarkdownObjectName}（{@code :192-195}）：
     * {@code "<ArticlePath><articleID>-<sha256(markdown)>.md</b>"}。
     * ⇒ <b>每次改内容都写一个新对象</b>，已批准 revision 的 {@code source_object} 指向的对象
     * <b>永不被覆盖</b>。若改成固定名（像 create 那样），已发布的字节会被后续编辑改写，
     * "已发布不可变"当场失效。
     * <br>⚠️ 同理 create 路径<b>不带</b>哈希（{@code <id>.md}）：首版对象名固定，
     * 之后所有编辑都走带哈希的新名。
     *
     * @return 是否发生源变更（决定分支 B / C 的 review 判定）
     */
    private boolean applySourceUpdates(ArticleEntity article, UpdateArticleRequest in) {
        boolean sourceChanged = false;
        if (in.hasTitle()) {
            article.setTitle(in.getTitle());
            sourceChanged = true;
        }
        if (in.hasBrief()) {
            article.setBrief(in.getBrief());
            sourceChanged = true;
        }
        if (in.hasMarkdownContent()) {
            String markdown = in.getMarkdownContent();
            String objectName = articleMarkdownObjectName(
                    storage.getObjectNamePrefixForArticle(), article.getId(), markdown);
            long start = System.nanoTime();
            try {
                storage.put(objectName, markdown.getBytes(StandardCharsets.UTF_8),
                        "text/markdown", null);
            } catch (Exception e) {
                metrics.minioError("put");
                throw ArticleExceptions.raw(e);
            }
            metrics.observeMinio("put", System.nanoTime() - start);
            metrics.fileUpload("markdown");
            article.setContent(objectName);
            sourceChanged = true;
        }
        if (in.hasCoverImageUrl()) {
            article.setCoverImageUrl(in.getCoverImageUrl());
            sourceChanged = true;
        }
        if (in.hasManualTypeTag()) {
            article.setManualTypeTag(in.getManualTypeTag());
            sourceChanged = true;
        }
        // ⚠️ Go 用 len() > 0 判定（update_article_logic.go:184），**不是 hasSecondaryTags**
        if (in.getSecondaryTagsCount() > 0) {
            article.setSecondaryTags(new ArrayList<>(in.getSecondaryTagsList()));
            sourceChanged = true;
        }
        return sourceChanged;
    }

    /** 对应 Go {@code articleMarkdownObjectName}（{@code update_article_logic.go:192-195}）。 */
    static String articleMarkdownObjectName(String prefix, String articleID, String markdown) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hash = HexFormat.of().formatHex(
                    digest.digest(markdown.getBytes(StandardCharsets.UTF_8)));
            return prefix + articleID + "-" + hash + ".md";
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** 对应 Go {@code setReviewNonce}（{@code update_article_logic.go:129-137}）：换一个新的雪花 ID。 */
    private void setReviewNonce(ArticleEntity article) {
        Map<String, String> extInfo = ArticleSyncEventTypes.ensureExtInfo(article.getExtInfo());
        article.setExtInfo(extInfo);
        extInfo.put(ArticleSyncEventTypes.EXT_REVIEW_NONCE, newEventId());
    }

    /**
     * 对应 Go {@code enqueueReview}（{@code update_article_logic.go:232-245}）。
     *
     * <p>⚠️ 失败返回 <b>Internal</b>（不是 raw）：Go 这里用 {@code status.Error(codes.Internal, err.Error())}，
     * 而 create 路径也是 Internal。两处一致。
     */
    private void enqueueReview(ArticleEntity article) {
        String reviewNonce = article.getExtInfo() == null
                ? null : article.getExtInfo().get(ArticleSyncEventTypes.EXT_REVIEW_NONCE);
        ArticleSyncEventTypes.ArticleReviewMessage msg = new ArticleSyncEventTypes.ArticleReviewMessage(
                article.getId(), article.getAuthorId(), article.getContent(), reviewNonce);
        try {
            kafkaTemplate.send(reviewTopic(), article.getId(), JsonbTypeHandler.writeJson(msg)).get();
        } catch (Exception e) {
            metrics.kafkaPushError("article_review");
            throw ArticleExceptions.internal(e.getMessage(), e);
        }
    }

    // ==================================================================
    // 4. DeleteArticle
    // ==================================================================

    /**
     * 对应 Go {@code delete_article_logic.go:40-100}。
     *
     * <h2>⚠️ 三条不可改的语义</h2>
     * <ol>
     *   <li><b>操作人校验在事务之外且先于一切</b>：
     *       {@code if in.GetOperatorId() == "" || in.GetOperatorId() != article.AuthorID { PermissionDenied }}
     *       —— 未传操作人<b>与</b>操作人非作者返回<b>同一个</b>码与文案。</li>
     *   <li><b>不删 MinIO 对象</b>（见 {@link ArticleAtomicService#retractAndDelete} 的说明）。</li>
     *   <li><b>重复删除不再发 retract</b>：Go 用 {@code FindOne}（带软删除过滤），
     *       已删文章查不到 ⇒ 返回 NotFound。
     *       Go 测试 {@code community_article_test.go:306-309} 断言域事件数<b>不变</b>。
     *       ⇒ 本实现用 {@code selectById}（带 {@code deleted_at IS NULL}），
     *       已删文章走 null 分支直接返回，不会二次发事件。</li>
     * </ol>
     */
    public DeleteArticleResponse deleteArticle(DeleteArticleRequest in) {
        ArticleEntity article = articleMapper.selectById(in.getArticleId());
        if (article == null) {
            // ⚠️ 同 update：Go 这里也是裸返回（delete_article_logic.go:47-57）⇒ UNKNOWN + "record not found"
            throw ArticleExceptions.articleNotFoundRaw();
        }
        // ⚠️ 空 operator_id 与非作者合并为同一个错误（Go: || 短路）
        if (in.getOperatorId().isEmpty() || !in.getOperatorId().equals(article.getAuthorId())) {
            throw ArticleExceptions.forbidden();
        }

        String eventId = newEventId();
        long versionMs = System.currentTimeMillis();
        atomicService.retractAndDelete(in.getArticleId(), eventId, versionMs);

        metrics.article("delete");
        return DeleteArticleResponse.newBuilder().setSuccess(true).build();
    }

    // ==================================================================
    // 5. ListArticles —— 作者视角
    // ==================================================================

    /**
     * 对应 Go {@code list_articles_logic.go:30-101} 的非公开分支。
     *
     * <h2>⚠️ 作者视角与公开视角的 page/size 处理<b>不同</b></h2>
     * <ul>
     *   <li>作者视角：<b>直接透传</b> {@code in.Page} / {@code in.PageSize}，
     *       响应里也<b>原样回显</b>（Go {@code :98-99}）。
     *       只在计算 offset 时把负值夹到 0（{@code model/article.go:157-159}）。
     *       ⇒ 传 page=0 会得到 {@code LIMIT pageSize OFFSET 0} 且响应 page=0。</li>
     *   <li>公开视角：<b>先归一</b>（page&lt;1⇒1，size&lt;1⇒20，size&gt;100⇒100），
     *       响应回显<b>归一后的值</b>（Go {@code :124-133}）。</li>
     * </ul>
     * 对称化任何一边都会改变响应里的 {@code page} / {@code page_size} 字段。
     *
     * <p>⚠️ 作者视角的 {@code MarkdownContent} 填的是 <b>{@code article.Content}（对象 key）</b>，
     * 不是正文（Go {@code :79}）—— 这是 Go 的一处<b>不一致</b>（同一 proto 字段在两条路径含义不同）。
     * 照搬：改成真正文需要额外的 MinIO 读取，会让列表响应体暴涨。
     */
    public ListArticlesResponse listArticles(ListArticlesRequest in) {
        if (in.getPublicOnly()) {
            return publicService.listPublicArticles(in);
        }

        int page = in.getPage();
        int size = in.getPageSize();
        String manualTypeTag = in.hasManualTypeTag() ? in.getManualTypeTag() : null;
        String secondaryTag = in.hasSecondaryTag() ? in.getSecondaryTag() : null;
        String authorId = in.hasAuthorId() ? in.getAuthorId() : null;
        String secondaryTagJson = (secondaryTag == null || secondaryTag.isEmpty())
                ? null
                : JsonbTypeHandler.writeJson(List.of(secondaryTag));

        long total = articleMapper.countAuthorView(manualTypeTag, secondaryTagJson, authorId);

        long offset = (long) (page - 1) * size;
        if (offset < 0) {
            offset = 0;
        }

        List<ArticleEntity> articles = articleMapper.selectAuthorView(
                manualTypeTag, secondaryTagJson, authorId,
                in.getSortBy(), in.getDesc(), size, offset);

        List<Article> pbArticles = new ArrayList<>();
        if (articles != null) {
            for (ArticleEntity a : articles) {
                pbArticles.add(toAuthorProtoArticle(a, a.getContent()));
            }
        }

        return ListArticlesResponse.newBuilder()
                .addAllArticles(pbArticles)
                .setTotal(total)
                .setPage(page)
                .setPageSize(size)
                .build();
    }

    // ==================================================================
    // 6. UploadFile
    // ==================================================================

    /**
     * 对应 Go {@code upload_file_logic.go:40-91}。
     *
     * <h2>⚠️ content-type 的三条来源（Go :55-61）</h2>
     * <pre>
     * ext := filepath.Ext(in.FileName)                    // 含点，如 ".png"；无扩展名 ⇒ ""
     * contentType := mime.TypeByExtension(ext)
     * if contentType == "" { contentType = "application/octet-stream" }
     * </pre>
     * <br>⚠️ Go 的 {@code mime.TypeByExtension} 查的是<b>系统 mime.types</b>，
     * 同一个扩展名在不同容器里结果可能不同（这是 Go 的行为）。
     * Java 侧用 {@code URLConnection.guessContentTypeFromName}，
     * 它查<b>JDK 内置表</b>（Spring 的 {@code MediaTypeFactory} 也是同一份）。
     * ⇒ 两侧在<b>常见扩展名上</b>（.png/.jpg/.gif/.webp/.svg/.pdf）一致；
     * 在冷门扩展名上可能不同。这是无法在 Java 侧复刻系统 mime.types 的固有差异，
     * <b>已在此留痕</b>，不通过"猜一个更准的表"来掩盖。
     * <br>⚠️ 图片上传<b>带</b> {@code CacheControl: public, max-age=31536000, immutable}，
     * markdown 上传<b>不带</b>。
     *
     * <p>⚠️ 对象名 = {@code <imagePath><snowflakeId><ext>}；<b>ext 含点</b>，
     * 且 imagePath 带尾斜杠（见 {@code ArticleObjectStorageClient} 的类级说明）。
     */
    public UploadFileResponse uploadFile(UploadFileRequest in) {
        long id = snowflake.nextId();
        String fileName = in.getFileName();
        // Go: filepath.Ext —— 取最后一个点之后的部分（含点）；无点则空串
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            ext = fileName.substring(dot);
        }
        String objectName = storage.getObjectNamePrefixForImage() + id + ext;

        String contentType = guessContentType(ext);

        byte[] content = in.getContent().toByteArray();
        long start = System.nanoTime();
        try {
            storage.put(objectName, content, contentType,
                    "public, max-age=31536000, immutable");
        } catch (Exception e) {
            metrics.minioError("put");
            throw ArticleExceptions.raw(e);
        }
        metrics.observeMinio("put", System.nanoTime() - start);
        metrics.fileUpload("image");

        return UploadFileResponse.newBuilder()
                .setFileUrl(storage.buildPublicFileURL(objectName))
                .build();
    }

    /**
     * Go {@code mime.TypeByExtension(ext)} ＋ {@code if contentType == "" { contentType = "application/octet-stream" }}
     * 的<b>合并</b>等价物（{@code upload_file_logic.go:58-61}）。
     *
     * <p><b>⚠️ 回落必须在本方法内完成</b>，而不是留给调用点判断 ——
     * Go 那两行是同一步逻辑，拆开就可能出现"某个调用点忘了加回落"，
     * 让未知扩展名以空 Content-Type 写进对象存储（S3 会存成
     * {@code binary/octet-stream} 或直接拒绝）。
     *
     * @param ext 含点的扩展名，如 {@code ".png"}；无扩展名传空串
     * @return 永不为 null / 永不为空的 content-type
     */
    static String guessContentType(String ext) {
        if (ext != null && !ext.isEmpty()) {
            String guessed = java.net.URLConnection.guessContentTypeFromName("file" + ext);
            if (guessed != null && !guessed.isEmpty()) {
                return guessed;
            }
        }
        return "application/octet-stream";
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    /** 新事件 ID —— 对应 Go 的 {@code newEventID}（雪花 ID 的十进制字符串）。 */
    String newEventId() {
        return String.valueOf(snowflake.nextId());
    }

    /**
     * 审核 topic —— 对应 Go {@code config.KqPusherConf.Topic}
     * 与 {@code config.KqConsumerConf.Topic}（两者在 yaml 里是<b>同一个</b>值）。
     *
     * <p>⚠️ <b>取 pusher 侧的值</b>：Go 的 push 用 {@code svcCtx.KqPusher}
     * （建自 {@code KqPusherConf.Topic}），consume 用 {@code svcCtx.Config.KqConsumerConf.Topic}。
     * yaml 里两者都是 {@code article-creation-topic}，但<b>配置上是两个独立字段</b>。
     * 改成只保留一个会让运维无法在不改消费端的情况下调整推送目标。
     */
    String reviewTopic() {
        return properties.getKqPusherConf().getTopic();
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}