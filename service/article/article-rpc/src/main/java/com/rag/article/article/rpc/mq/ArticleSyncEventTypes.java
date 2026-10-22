package com.rag.article.article.rpc.mq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.rpc.domain.ArticleEntity;
import com.rag.article.article.rpc.domain.ArticleRevisionEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * article 域的消息与事件契约 —— 对应 Go 端
 * {@code mqs/article_sync_types.go}（常量 + 三个消息结构）与
 * {@code mqs/community_article_event.go}（community 信封与 payload）。
 *
 * <h2>🔴 这些 JSON 字段名是 <b>L1 跨服务契约</b>，逐字不可改</h2>
 * 消费者在别的服务（Sea-BreakTheWaves）、发件箱里已冻结的历史 payload、
 * 以及 Kafka 里已积压的消息<b>都按这些字段名解析</b>。
 * 改一个 {@code @JsonProperty} 的值就会让<b>已冻结的 outbox 行解不出来</b>
 * （发布回调要读回 payload 做六项比对）。
 *
 * <h2>⚠️ {@code ,omitempty} 必须原样复刻</h2>
 * Go 侧 {@code ArticleSyncEvent} 有<b>七个</b> {@code omitempty} 字段
 * （title / brief / cover_url / manual_type_tag / secondary_tags / markdown）
 * 与<b>七个非</b> omitempty 字段。
 * 这直接决定冻结 payload 的<b>字节内容</b>：空值字段出现与否会改变 JSON 文本，
 * 而 relay 推送的是<b>原文</b>。用 Jackson 的
 * {@link JsonInclude.Include#NON_EMPTY} 复刻（见各字段注释）。
 *
 * <p>⚠️ 注意 Go 的 {@code omitempty} 对<b>空切片</b>与<b>空字符串</b>都省略，
 * 对 {@code int64 version_ms == 0} <b>不省略</b>（0 是 int 的零值 ⇒ 会被省略！）。
 * 实际使用中 {@code versionMs} 恒为 {@code time.Now().UnixMilli()}（非 0），
 * 但 {@code status} 与 {@code op} 是<b>无 omitempty</b> 的必出字段。
 */
public final class ArticleSyncEventTypes {

    private ArticleSyncEventTypes() {
    }

    // ==================== scope / op / reason（Go article_sync_types.go:13-22） ====================

    public static final String ARTICLE_SYNC_SCOPE = "article_sync";

    public static final String OP_UPSERT = "upsert";
    public static final String OP_DELETE = "delete";

    public static final String REASON_CREATE = "create";
    public static final String REASON_UPDATE = "update";
    public static final String REASON_STATUS_CHANGE = "status_change";
    public static final String REASON_DELETE = "delete";

    // ==================== ext_info 键名（Go article_sync_types.go:24-32） ====================
    // ⚠️ 这些字符串同时是 article.ext_info jsonb 的键，属 L1 契约：
    //    审核消费者与发布回调都按它们比对，改名会让已冻结的 ext_info 全部失配。

    public static final String EXT_PUBLISH_STAGE = "publish_stage";
    public static final String EXT_RECO_SYNC_STATE = "reco_sync_state";
    public static final String EXT_LAST_SYNC_ERROR = "last_sync_error";
    public static final String EXT_LAST_SYNC_EVENT_ID = "last_sync_event_id";
    public static final String EXT_LAST_SYNC_VERSION = "last_sync_version_ms";
    public static final String EXT_LAST_SYNC_REASON = "last_sync_reason";
    public static final String EXT_PUBLISHED_ONCE = "published_once";
    public static final String EXT_PENDING_SYNC_REASON = "pending_sync_reason";
    public static final String EXT_REVIEW_NONCE = "review_nonce";

    /** legacy 发布缺口标记（Go {@code get_article_logic.go:180} 与 {@code community_article_event.go:117}）。 */
    public static final String LEGACY_GAP_KEY = "publication_gap";
    public static final String LEGACY_GAP_VALUE = "legacy_revision_missing";

    /** H03 缺口标记（Go {@code RetractPublicationTx} 在无指针行时写 {@code h03_publication_gap}）。 */
    public static final String H03_GAP_KEY = "h03_publication_gap";

    /** community 信封常量（Go {@code community_article_event.go:65,91,94}）。 */
    public static final String COMMUNITY_PRODUCER = "rtw.community.article";
    public static final String COMMUNITY_SUBJECT_ISSUER = "rtw.identity";
    public static final String COMMUNITY_SUBJECT_TENANT = "platform";
    /**
     * 对应 Go 常量 {@code SchemaVersion: 1}（{@code community_article_event.go:91}）。
     *
     * <p><b>是 int 而非 String</b>：Go 的 {@code communityArticleEnvelope.SchemaVersion} 是
     * {@code int}（{@code community_article_event.go:45}），序列化成 JSON number；
     * 本仓共享的 {@code com.rag.common.communityfact.Wire.Event#schemaVersion} <b>同样是 int</b>
     * （{@code Wire.java:123}），like 域的 Go 生产侧也是把 1 写进 int 字段
     * （{@code service/like/rpc/internal/model/domain_fact.go:45,125}）。
     * ⇒ 两侧一致用 number。
     * <br>⚠️ 顺带记一条排查经验：下游 Sea-BreakTheWaves 里<b>另有一批</b>
     * {@code schema_version string} 的结构体（如 {@code async/rpc/.../wiki_compile_native_*.go}），
     * 那是 wiki 编译票据的 payload 版本，与本信封<b>不是同一个字段</b>。
     * 看到"下游是 string"就改这里会改错。
     */
    public static final int COMMUNITY_SCHEMA_VERSION = 1;

    // ==================== ext_info 操作（Go article_sync_types.go:69-92） ====================

    /**
     * 对应 Go {@code EnsureExtInfo}：{@code if article.ExtInfo == nil { article.ExtInfo = JSONMap{} }}。
     *
     * <p>⚠️ <b>必须返回同一个 map 实例</b>并让调用方写回实体 ——
     * Go 是指针接收者上的 map 写入，Java 的 {@code Map} 同样是引用类型，
     * 但实体字段可能为 {@code null}，此时直接 put 会 NPE。
     */
    public static Map<String, String> ensureExtInfo(Map<String, String> current) {
        if (current == null) {
            return new LinkedHashMap<>();
        }
        return current;
    }

    /**
     * 对应 Go {@code SetSyncState}（{@code article_sync_types.go:75-88}）。
     *
     * <p><b>⚠️ 两条条件赋值不能改成无条件</b>：
     * <pre>
     * if versionMs &gt; 0 { article.ExtInfo[ExtLastSyncVersion] = strconv.FormatInt(versionMs, 10) }
     * if syncReason != "" { article.ExtInfo[ExtPendingSyncReason] = syncReason }
     * </pre>
     * ⇒ {@code versionMs <= 0} 时<b>保留旧版本号</b>；{@code syncReason == ""} 时<b>保留旧待同步原因</b>。
     * 无条件赋值会让「不携带版本号的重试」把版本号清成 0 / 待同步原因清成空，
     * 进而让发布回调的 {@code ExtLastSyncVersion != FormatInt(result.VersionMs)} 判据失效。
     *
     * <p>⚠️ 版本号<b>存成字符串</b>（{@code String.valueOf}），与 Go 的 {@code FormatInt} 一致。
     */
    public static void setSyncState(Map<String, String> extInfo, String publishStage, String syncState,
                                    String syncReason, String eventId, long versionMs, String syncError) {
        extInfo.put(EXT_PUBLISH_STAGE, publishStage);
        extInfo.put(EXT_RECO_SYNC_STATE, syncState);
        extInfo.put(EXT_LAST_SYNC_ERROR, syncError);
        extInfo.put(EXT_LAST_SYNC_EVENT_ID, eventId);
        extInfo.put(EXT_LAST_SYNC_REASON, syncReason);
        if (versionMs > 0) {
            extInfo.put(EXT_LAST_SYNC_VERSION, String.valueOf(versionMs));
        }
        if (syncReason != null && !syncReason.isEmpty()) {
            extInfo.put(EXT_PENDING_SYNC_REASON, syncReason);
        }
    }

    /**
     * 对应 Go {@code ArticleSyncEventKey}（{@code article_sync_types.go:90-92}）：
     * {@code TrimSpace(articleID) + ":" + TrimSpace(op) + ":" + TrimSpace(eventID)}。
     *
     * <p>⚠️ 三段各自 trim，<b>段间不额外 trim</b>。这是 {@code uk_article_sync_outbox_event_key}
     * 唯一索引的唯一键来源。
     */
    public static String articleSyncEventKey(String articleID, String op, String eventID) {
        return trim(articleID) + ":" + trim(op) + ":" + trim(eventID);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    // ==================== 消息结构 ====================

    /**
     * 对应 Go {@code ArticleReviewMessage}（{@code article_sync_types.go:35-40}）。
     *
     * <p><b>⚠️ 四个字段构成"审核四元组"</b>：审核消费者要求
     * {@code status==REVIEWING ∧ content==ContentPath ∧ authorID==AuthorID ∧ extInfo[review_nonce]==ReviewNonce}
     * 四项<b>全部</b>成立才继续。四元组的作用是<b>丢弃过期的审核任务</b>：
     * 作者在上一轮审核期间又改了文章，旧任务必须作废。
     * ⇒ 任何字段名改动都会削弱这层保护。
     */
    @JsonPropertyOrder({"article_id", "author_id", "content_path", "review_nonce"})
    public static class ArticleReviewMessage {

        @JsonProperty("article_id")
        public String articleId;

        @JsonProperty("author_id")
        public String authorId;

        @JsonProperty("content_path")
        public String contentPath;

        /** Go 是 {@code json:"review_nonce,omitempty"}。 */
        @JsonProperty("review_nonce")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String reviewNonce;

        public ArticleReviewMessage() {
        }

        public ArticleReviewMessage(String articleId, String authorId, String contentPath, String reviewNonce) {
            this.articleId = articleId;
            this.authorId = authorId;
            this.contentPath = contentPath;
            this.reviewNonce = reviewNonce;
        }
    }

    /**
     * 对应 Go {@code ArticleSyncEvent}（{@code article_sync_types.go:42-57}）——
     * <b>outbox 冻结与推送的载荷本体</b>。
     *
     * <p>⚠️ <b>{@code status} 的取值规则特殊</b>（Go {@code NewArticleSyncEvent:95-98}）：
     * <pre>
     * status := pb.ArticleStatus(article.Status).String()
     * if op == ArticleSyncOpUpsert { status = pb.ArticleStatus_PUBLISHED.String() }
     * </pre>
     * 即 <b>upsert 一律报 {@code "PUBLISHED"}</b>（无论当前行是什么状态），
     * 而 delete 报当前状态的<b>枚举名字</b>（如 {@code "DRAFT"}）。
     * 用<b>枚举名</b>而非数值，因为下游按名字消费。
     */
    @JsonPropertyOrder({"event_scope", "event_id", "article_id", "op", "reason", "author_id", "status",
            "version_ms", "title", "brief", "cover_url", "manual_type_tag", "secondary_tags", "markdown"})
    public static class ArticleSyncEvent {

        @JsonProperty("event_scope")
        public String eventScope;

        @JsonProperty("event_id")
        public String eventId;

        @JsonProperty("article_id")
        public String articleId;

        @JsonProperty("op")
        public String op;

        @JsonProperty("reason")
        public String reason;

        @JsonProperty("author_id")
        public String authorId;

        @JsonProperty("status")
        public String status;

        @JsonProperty("version_ms")
        public long versionMs;

        @JsonProperty("title")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String title;

        @JsonProperty("brief")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String brief;

        @JsonProperty("cover_url")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String coverUrl;

        @JsonProperty("manual_type_tag")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String manualTypeTag;

        @JsonProperty("secondary_tags")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public List<String> secondaryTags;

        @JsonProperty("markdown")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String markdown;

        public ArticleSyncEvent() {
        }
    }

    /**
     * 对应 Go {@code ArticleSyncResult}（{@code article_sync_types.go:59-67}）——
     * 下游回传的审核/同步结果。
     *
     * <p>⚠️ {@code version_ms} 是<b>幂等判据之一</b>：
     * 发布回调要求 {@code extInfo[last_sync_version_ms] == FormatInt(result.VersionMs)}。
     */
    @JsonPropertyOrder({"event_scope", "event_id", "article_id", "op", "version_ms", "success", "error_message"})
    public static class ArticleSyncResult {

        @JsonProperty("event_scope")
        public String eventScope;

        @JsonProperty("event_id")
        public String eventId;

        @JsonProperty("article_id")
        public String articleId;

        @JsonProperty("op")
        public String op;

        @JsonProperty("version_ms")
        public long versionMs;

        @JsonProperty("success")
        public boolean success;

        @JsonProperty("error_message")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String errorMessage;

        public ArticleSyncResult() {
        }
    }

    /**
     * 对应 Go {@code communitySubject}（{@code community_article_event.go:17-21}）。
     *
     * <p>⚠️ <b>字段名与 Go 不一致是有意的</b>：Go 的三个字段是
     * {@code AuthorityID / TenantID / SubjectID}，但 <b>JSON 名</b>是
     * {@code authority_id / tenant_id / subject_id}。
     * 而 Go 构造时传的是 {@code communitySubject{"rtw.identity", "platform", article.AuthorID}}
     * —— 三个位置分别对应 JSON 的三个名字。Java 侧照此对齐。
     */
    @JsonPropertyOrder({"authority_id", "tenant_id", "subject_id"})
    public static class CommunitySubject {

        @JsonProperty("authority_id")
        public String authorityId;

        @JsonProperty("tenant_id")
        public String tenantId;

        @JsonProperty("subject_id")
        public String subjectId;

        public CommunitySubject() {
        }

        public CommunitySubject(String authorityId, String tenantId, String subjectId) {
            this.authorityId = authorityId;
            this.tenantId = tenantId;
            this.subjectId = subjectId;
        }
    }

    /**
     * 对应 Go {@code communityRevisionPayload}（{@code community_article_event.go:23-40}）。
     *
     * <h2>⚠️ 三条强约束（Go 侧是显式校验，改动会让"社区文章永不进知识库"失效）</h2>
     * <ol>
     *   <li>{@link #wikiModuleId} <b>恒为 null</b> —— Go 注释
     *       {@code // never inferred from a community article}。
     *       非空会让下游把社区文章误挂到知识库模块上。</li>
     *   <li>{@link #searchEvidence} <b>仅 {@code published} 为 true</b>；
     *       retracted 事件为 false（它是删除通知，不是可检索证据）。</li>
     *   <li>{@link #markdown} 是 {@code omitempty} —— retracted 事件<b>不带正文</b>
     *       （Go {@code community_article_event.go:83-89} 的 else 分支不设 Markdown）。</li>
     * </ol>
     */
    @JsonPropertyOrder({"article_id", "revision_id", "revision", "subject_ref", "source_ref", "source_object",
            "content_sha256", "title", "brief", "cover_image_url", "manual_type_tag", "secondary_tags",
            "markdown", "operation", "search_evidence", "wiki_module_id"})
    public static class CommunityRevisionPayload {

        @JsonProperty("article_id")
        public String articleId;

        @JsonProperty("revision_id")
        public String revisionId;

        @JsonProperty("revision")
        public long revision;

        @JsonProperty("subject_ref")
        public CommunitySubject author;

        @JsonProperty("source_ref")
        public String sourceRef;

        @JsonProperty("source_object")
        public String sourceObject;

        @JsonProperty("content_sha256")
        public String contentSha256;

        @JsonProperty("title")
        public String title;

        @JsonProperty("brief")
        public String brief;

        @JsonProperty("cover_image_url")
        public String coverImageUrl;

        @JsonProperty("manual_type_tag")
        public String manualTypeTag;

        @JsonProperty("secondary_tags")
        public List<String> secondaryTags;

        @JsonProperty("markdown")
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String markdown;

        @JsonProperty("operation")
        public String operation;

        @JsonProperty("search_evidence")
        public boolean searchEvidence;

        @JsonProperty("wiki_module_id")
        public String wikiModuleId;

        public CommunityRevisionPayload() {
        }
    }

    /**
     * 对应 Go {@code communityArticleEnvelope}（{@code community_article_event.go:42-52}）。
     *
     * <p>⚠️ 本仓已有 {@code com.rag.common.communityfact.Wire.Event}，字段名与 producer 语义
     * 与本类<b>逐字一致</b>（FileTree §12.1 的 REUSE 结论）。
     * 但本类<b>仍然保留</b>，原因是<b>序列化契约</b>不同：
     * {@code Wire.Event.payload} 是 {@code Object} 且由调用方自选序列化方式，
     * 而这里需要<b>冻结字段顺序</b>（{@link JsonPropertyOrder}）与
     * {@code occurred_at} 的 RFC3339Nano 精度。
     * 若直接复用 {@code Wire.Event}，payload 类型不确定会让冻结字节不可控。
     */
    @JsonPropertyOrder({"event_id", "event_type", "schema_version", "producer", "aggregate_id",
            "aggregate_version", "operation_id", "occurred_at", "payload"})
    public static class CommunityArticleEnvelope {

        @JsonProperty("event_id")
        public String eventId;

        @JsonProperty("event_type")
        public String eventType;

        @JsonProperty("schema_version")
        public int schemaVersion;

        @JsonProperty("producer")
        public String producer;

        @JsonProperty("aggregate_id")
        public String aggregateId;

        @JsonProperty("aggregate_version")
        public long aggregateVersion;

        @JsonProperty("operation_id")
        public String operationId;

        /** Go：{@code at.UTC().Format(time.RFC3339Nano)}。 */
        @JsonProperty("occurred_at")
        public String occurredAt;

        @JsonProperty("payload")
        public CommunityRevisionPayload payload;

        public CommunityArticleEnvelope() {
        }
    }

    // ==================== 事件工厂（Go NewArticleSyncEvent / articleDomainEvent） ====================

    /**
     * 对应 Go {@code NewArticleSyncEvent}（{@code article_sync_types.go:94-116}）。
     *
     * <p>⚠️ {@link ArticleSyncEvent#status} 的特殊规则见该类说明（upsert 一律 PUBLISHED）。
     * <br>⚠️ {@code SecondaryTags} 用 {@code new ArrayList<>(...)} 做<b>防御性拷贝</b>：
     * Go 的 {@code append([]string(nil), article.SecondaryTags...)} 同样是拷贝。
     * 不拷贝会让事件与实体共享同一个 list，后续修改互相污染。
     */
    public static ArticleSyncEvent newArticleSyncEvent(ArticleEntity article, String markdown,
                                                       String op, String reason,
                                                       String eventId, long versionMs) {
        String status = articleStatusName(article.getStatus());
        if (OP_UPSERT.equals(op)) {
            status = "PUBLISHED";
        }
        ArticleSyncEvent event = new ArticleSyncEvent();
        event.eventScope = ARTICLE_SYNC_SCOPE;
        event.eventId = eventId;
        event.articleId = article.getId();
        event.op = op;
        event.reason = reason;
        event.authorId = article.getAuthorId();
        event.status = status;
        event.versionMs = versionMs;
        event.title = article.getTitle();
        event.brief = article.getBrief();
        event.coverUrl = article.getCoverImageUrl();
        event.manualTypeTag = article.getManualTypeTag();
        event.secondaryTags = article.getSecondaryTags() == null
                ? null : new ArrayList<>(article.getSecondaryTags());
        event.markdown = markdown;
        return event;
    }

    /**
     * 对应 Go {@code articleDomainEvent}（{@code community_article_event.go:54-104}）的
     * <b>校验 + 构造</b>部分。
     *
     * <h2>⚠️ 五条前置校验必须保留（Go 用 error 返回，Java 用异常）</h2>
     * <ol>
     *   <li>article 非空、ID 非空、AuthorID 非空、{@code aggregateVersion > 0}、
     *       syncEventID 非空、operation ∈ {published, retracted}；</li>
     *   <li><b>{@code AuthorID} 必须能解析成 uid &gt; 0</b>
     *       （Go {@code strconv.ParseInt(article.AuthorID, 10, 64)}）——
     *       它同时进 {@code subject_ref.subject_id} 与下游鉴权；</li>
     *   <li>published 时 revision 必须<b>六项齐全</b>：articleID 匹配、revision &gt; 0、
     *       revisionID 非空、contentSHA256 非空、markdown 非空；</li>
     *   <li>retracted 时 {@code previousRevision} 必须非空（否则无法指明撤回的是哪一版）；</li>
     *   <li>{@code wiki_module_id} <b>恒 null</b>、{@code search_evidence} 仅 published 为 true。</li>
     * </ol>
     *
     * <p>⚠️ <b>校验失败必须让整个事务失败</b>（抛异常），
     * 而不是"跳过这条事件继续"。Go 返回 error 会让
     * {@code RetractPublicationTx} / {@code handleUpsertResult} 的事务回滚 ——
     * 这正是 Go 测试 {@code TestArticleDomainOutboxFailureRollsBackPublication} 断言的。
     *
     * @param previousRevision 仅 retracted 用；published 传 {@code null}
     * @return 可直接落库的 {@code CommunityArticleEnvelope}
     */
    public static CommunityArticleEnvelope articleDomainEvent(ArticleEntity article,
                                                               ArticleRevisionEntity revision,
                                                               String previousRevision,
                                                               String operation, String syncEventID,
                                                               long aggregateVersion, String occurredAtRfc3339Nano) {
        if (article == null || isBlank(article.getId()) || isBlank(article.getAuthorId())
                || aggregateVersion <= 0 || isBlank(syncEventID)
                || !(OP_PUBLISHED.equals(operation) || OP_RETRACTED.equals(operation))) {
            throw new IllegalArgumentException("invalid community article event");
        }
        long uid = parsePositiveUid(article.getAuthorId());
        if (uid <= 0) {
            throw new IllegalArgumentException("invalid community article author UID");
        }

        String eventId = "community.article." + syncEventID + "." + operation;

        CommunityRevisionPayload payload = new CommunityRevisionPayload();
        payload.articleId = article.getId();
        payload.author = new CommunitySubject(COMMUNITY_SUBJECT_ISSUER, COMMUNITY_SUBJECT_TENANT,
                article.getAuthorId());
        payload.operation = operation;
        // Go: SearchEvidence: operation == "published"
        payload.searchEvidence = OP_PUBLISHED.equals(operation);
        // Go: WikiModule: nil —— 恒 null，社区文章永不进知识库
        payload.wikiModuleId = null;

        if (OP_PUBLISHED.equals(operation)) {
            if (revision == null || !article.getId().equals(revision.getArticleId())
                    || revision.getRevision() == null || revision.getRevision() <= 0
                    || isBlank(revision.getRevisionId()) || isBlank(revision.getContentSha256())
                    || isBlank(revision.getMarkdown())) {
                throw new IllegalArgumentException("published article revision is incomplete");
            }
            payload.revisionId = revision.getRevisionId();
            payload.revision = revision.getRevision();
            payload.sourceRef = "rtw.community.article/" + revision.getRevisionId();
            payload.sourceObject = revision.getSourceObject();
            payload.contentSha256 = revision.getContentSha256();
            payload.title = revision.getTitle();
            payload.brief = revision.getBrief();
            payload.coverImageUrl = revision.getCoverImageUrl();
            payload.manualTypeTag = revision.getManualTypeTag();
            payload.secondaryTags = revision.getSecondaryTags() == null
                    ? null : new ArrayList<>(revision.getSecondaryTags());
            payload.markdown = revision.getMarkdown();
        } else {
            if (isBlank(previousRevision)) {
                throw new IllegalArgumentException("retraction requires the published revision");
            }
            payload.revisionId = previousRevision;
            payload.sourceRef = "rtw.community.article/" + previousRevision;
        }

        CommunityArticleEnvelope envelope = new CommunityArticleEnvelope();
        envelope.eventId = eventId;
        envelope.eventType = "community.article." + operation;
        envelope.schemaVersion = COMMUNITY_SCHEMA_VERSION;
        envelope.producer = COMMUNITY_PRODUCER;
        envelope.aggregateId = article.getId();
        envelope.aggregateVersion = aggregateVersion;
        envelope.operationId = syncEventID;
        envelope.occurredAt = occurredAtRfc3339Nano;
        envelope.payload = payload;
        return envelope;
    }

    public static final String OP_PUBLISHED = "published";
    public static final String OP_RETRACTED = "retracted";

    /**
     * 对应 Go {@code strconv.ParseInt(article.AuthorID, 10, 64)} + {@code uid &lt;= 0} 检查。
     *
     * <p>⚠️ 解析失败<b>返回 -1 而不是抛异常</b>：Go 侧是把 err 与 uid 一起交给调用点判断，
     * 由调用点决定是"拒绝事件"还是"标记缺口"。这里返回哨兵值让调用点显式处理。
     */
    public static long parsePositiveUid(String authorId) {
        if (isBlank(authorId)) {
            return -1L;
        }
        try {
            return Long.parseLong(authorId.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * proto {@code ArticleStatus} 的<b>枚举名</b>映射 —— 对应 Go 的
     * {@code pb.ArticleStatus(n).String()}。
     *
     * <p>⚠️ <b>用名字而不是数值</b>：下游按字符串消费（Go {@code .String()} 返回枚举标识符）。
     *
     * <p><b>为什么委托给 protobuf 生成的枚举而不是自己写 switch</b>：
     * 生成代码由 {@code article-proto} 从 <b>逐字复制自 Go 的</b> {@code article.proto} 产出，
     * 它的 {@code toString()} 与 Go 的 {@code .String()} 同源（都取 proto 里的枚举标识符）。
     * 自己再写一份 switch 等于<b>多一个可能与 proto 漂移的真相源</b>：
     * 日后 proto 增删枚举值时，switch 会静默失配，而生成代码自动跟随。
     * 这与 AGENTS.md §3.2「idiomatization 必须有出处」一致 —— 这里是"复用生成代码"而非"自己实现"。
     *
     * <p>⚠️ <b>未知数值的回落必须保留</b>：Go 的 {@code .String()} 对未登记的数值返回
     * <b>该数字的十进制字符串</b>（protoc-gen-go 的实现），而不是 "UNKNOWN"。
     * protobuf-java 的 {@code forNumber} 对未知值返回 {@code null}，
     * 所以这里显式回落成 {@code String.valueOf(status)}，与 Go 对齐。
     * 若改成返回 "UNRECOGNIZED" 或抛异常，未知状态的事件文本就会与 Go 不同。
     */
    public static String articleStatusName(int status) {
        ArticleStatus value = ArticleStatus.forNumber(status);
        return value != null ? value.toString() : String.valueOf(status);
    }
}