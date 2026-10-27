package com.rag.favorite.favorite.rpc.service;

import com.rag.article.article.proto.Article;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.logger.LogOption;
import com.rag.favorite.favorite.common.span.FavoriteSpans;
import com.rag.favorite.favorite.rpc.client.ArticleRpcClient;
import com.rag.favorite.favorite.rpc.config.FavoriteRpcLabels;
import com.rag.favorite.favorite.rpc.exception.FavoriteExceptions;
import com.rag.favorite.favorite.rpc.metrics.FavoriteRpcMetrics;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 🔴 文章公开修订冻结 —— 对应 Go {@code service/favorite/rpc/internal/logic/helper.go:126-178}
 * 的 {@code resolveArticleSnapshot} 与 {@code :180-202} 的 {@code articleCover}。
 *
 * <p><b>这是 favorite 域最核心的业务规则</b>：收藏一条文章时，
 * title / cover / revision <b>只</b>来自 article-rpc 的公开（已发布）投影，
 * 请求参数里的 title/cover <b>不能</b>覆盖它。
 *
 * <p>Go 的注释（create_favorite_logic.go:113-115）写得很直白：
 * <pre>
 * // Article metadata comes only from the published projection. Request
 * // fields must not replace a frozen revision with a draft/client value.
 * </pre>
 *
 * <p><b>为什么独立成 Bean</b>：它有 12 条判定与一套 revision 号编码约定
 * （{@code <articleId>:r<N>}），是本域最值得逐条单测的一段；
 * 混进 7 个用例的领域服务里就没有独立可测的边界了。
 *
 * <h2>逐条判定（Go 行号）</h2>
 * <ol>
 *   <li>{@code normalizedTargetID = strings.TrimSpace(targetID)}（:130）</li>
 *   <li>调 {@code GetArticle{ArticleId, IncrView:false, PublicOnly:true}}（:133-137）</li>
 *   <li>报错且 NotFound ⇒ {@code NotFound/5002}；其他 ⇒ {@code Internal/5001}（:142-146）</li>
 *   <li>{@code resp == nil || resp.Article == nil} ⇒ {@code NotFound/5002}（:148-151）</li>
 *   <li>🔴 {@code article.Id != normalizedTargetID || article.Status != PUBLISHED}
 *       ⇒ {@code <b>Unavailable</b>/5001}（:153-156）</li>
 *   <li>revision 非空 ⇒ 必须 {@code <id>:r<N>}、{@code N != 0}、{@code publication_gap == ""}
 *       （:159-166）</li>
 *   <li>revision 为空 ⇒ 必须 {@code publication_gap == "legacy_revision_missing"}（:167-170）</li>
 *   <li>返回 {@code Title=TrimSpace(article.Title)}、{@code Cover=articleCover(article)}、
 *       {@code RevisionID}（:173-177）</li>
 * </ol>
 */
@Component
public class FavoriteArticleSnapshotResolver {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteArticleSnapshotResolver.class);

    /**
     * revision 号在 {@code ext_info} 里的 key —— Go {@code helper.go:158}
     * {@code metadata["published_revision_id"]}。
     *
     * <p>⚠️ <b>它不是 proto 字段</b>，而是 {@code Article.ext_info}（map&lt;string,string&gt;，
     * article.proto:34）里的一个<b>字符串约定</b>，写入方是 article-rpc。
     */
    private static final String PUBLISHED_REVISION_ID = "published_revision_id";

    /**
     * 发布空洞标记 —— Go {@code helper.go:163,167} {@code metadata["publication_gap"]}。
     *
     * <p>唯一合法值：{@link #GAP_LEGACY_REVISION_MISSING}。
     */
    private static final String PUBLICATION_GAP = "publication_gap";

    /** Go {@code helper.go:167}：{@code extInfo["publication_gap"] == "legacy_revision_missing"}。 */
    private static final String GAP_LEGACY_REVISION_MISSING = "legacy_revision_missing";

    /**
     * {@code articleCover} 的 ext_info 回退键序 —— Go {@code helper.go:194}：
     * {@code keys := []string{"cover", "cover_url", "coverUrl", "cover_image_url", "coverImageUrl"}}。
     *
     * <p>🔴 <b>顺序是 L2 语义</b>：取<b>第一个</b>非空值。调换顺序会在多个键同时存在时
     * 返回不同的封面。
     */
    private static final List<String> COVER_EXT_INFO_KEYS =
            List.of("cover", "cover_url", "coverUrl", "cover_image_url", "coverImageUrl");

    private final ArticleRpcClient articleRpcClient;
    private final FavoriteRpcMetrics metrics;

    public FavoriteArticleSnapshotResolver(ArticleRpcClient articleRpcClient, FavoriteRpcMetrics metrics) {
        this.articleRpcClient = articleRpcClient;
        this.metrics = metrics;
    }

    /** Go {@code articleSnapshot} struct（helper.go:42-46）：{@code Title / Cover / RevisionID}。 */
    public static final class ArticleSnapshot {

        /** {@code Title string} —— 已 {@code TrimSpace}。 */
        public final String title;
        /** {@code Cover string} —— 已 {@code TrimSpace}。 */
        public final String cover;
        /**
         * {@code RevisionID string} —— Go 注释：{@code "empty only for a legacy PUBLISHED
         * row without a publication pointer"}。
         * <p><b>空串有含义</b>（= 该收藏不带修订），与 null 不可混。
         */
        public final String revisionId;

        public ArticleSnapshot(String title, String cover, String revisionId) {
            this.title = title;
            this.cover = cover;
            this.revisionId = revisionId;
        }

        /** 是否带修订号；对应 Go 的 {@code if snapshot.RevisionID != ""}（create_favorite_logic.go:116）。 */
        public boolean hasRevision() {
            return revisionId != null && !revisionId.isEmpty();
        }
    }

    /**
     * 对应 Go {@code resolveArticleSnapshot(ctx, svcCtx, targetID)}（helper.go:126-178）。
     *
     * @param rawTargetId <b>未</b> TrimSpace 的目标 ID；本方法内部做一次（Go :130）
     */
    public ArticleSnapshot resolve(String rawTargetId, LogOption... logOptions) {
        String normalizedTargetId = rawTargetId == null ? "" : rawTargetId.trim();

        // 🔴 Go 出处（rpc/internal/logic/helper.go:126-178 · resolveArticleSnapshot）：
        //     depCtx, span := otel.Tracer("favorite-rpc").Start(ctx, "Dependency.Article.GetArticle")
        //     defer span.End()
        //     normalizedTargetID := strings.TrimSpace(targetID)              // ← :130
        //     span.SetAttributes(attribute.String("biz.article_id", normalizedTargetID))  // ← :131
        //     resp, err := svcCtx.ArticleRpc.GetArticle(depCtx, …)          // ← :133-137
        //     if err != nil { span.RecordError(err) … }                     // ← :139 **只有这里记错**
        //     if resp == nil || resp.Article == nil { … }                   // ← :148-151 **不记**
        // ⇒ 用 FavoriteSpans.tracedDependency 的两段式表达同一个时序：
        //    「下游调用失败 ⇒ 记错」/「调用后的业务判定失败 ⇒ 不记」。
        GetArticleResponse resp = FavoriteSpans.tracedDependency(
                FavoriteSpans.DEPENDENCY_ARTICLE_GET_ARTICLE,
                // Go: helper.go:130-131 —— **已 TrimSpace** 的 targetID
                span -> span.setAttribute(FavoriteSpans.ATTR_ARTICLE_ID, normalizedTargetId),
                // Go: helper.go:133-137（三个 L1 字段固化在 ArticleRpcClient#getPublicArticle）
                () -> articleRpcClient.getPublicArticle(normalizedTargetId),
                // Go: helper.go:139-146 —— **这里**是唯一记错的地方（记的是原始 gRPC 错误）
                e -> onDownstreamFailure(normalizedTargetId, e, logOptions),
                // Go: helper.go:148+ —— 调用后的业务判定**不记错**（下面原样保留）
                r -> r);

        // Go: service/favorite/rpc/internal/logic/helper.go:148-151
        if (resp == null || !resp.hasArticle()) {
            fail();
            throw FavoriteExceptions.articleNotFound();
        }

        Article article = resp.getArticle();

        // 🔴 Go: service/favorite/rpc/internal/logic/helper.go:153-156 —— 刻意用 Unavailable（不是 Internal）：
        //   article.Id != normalizedTargetID  ⇒ 下游返回了不属于本次请求的文章（不可解释）
        //   article.Status != PUBLISHED      ⇒ 公开投影不该返回未发布内容（下游有 bug 或数据不一致）
        // 两种都表达「上游状态不对」⇒ 可重试。
        if (!normalizedTargetId.equals(article.getId()) || article.getStatus() != ArticleStatus.PUBLISHED) {
            fail();
            LOG.warn("article public projection unusable: requested_id={} returned_id={} status={}",
                    normalizedTargetId, article.getId(), article.getStatus(), logOptions);
            throw FavoriteExceptions.unavailableServerCommon();
        }

        Map<String, String> metadata = article.getExtInfoMap();
        String revisionId = trimToEmpty(metadata.get(PUBLISHED_REVISION_ID));

        if (!revisionId.isEmpty()) {
            // Go: service/favorite/rpc/internal/logic/helper.go:159-166
            //   prefix := normalizedTargetID + ":r"
            //   number, parseErr := strconv.ParseUint(strings.TrimPrefix(revisionID, prefix), 10, 64)
            //   if !strings.HasPrefix(revisionID, prefix) || parseErr != nil || number == 0 ||
            //      metadata["publication_gap"] != "" { → Unavailable/5001 }
            String prefix = normalizedTargetId + ":r";
            if (!revisionId.startsWith(prefix)) {
                fail();
                LOG.warn("published_revision_id has wrong article prefix: id={} revision_id={}",
                        normalizedTargetId, revisionId, logOptions);
                throw FavoriteExceptions.unavailableServerCommon();
            }
            String suffix = revisionId.substring(prefix.length());
            long number;
            try {
                // ⚠️ Go 用 ParseUint（无符号）+ 位宽 64；用 Long.parseLong 会让
                // 一个形如 "99999999999999999999" 的值以 NumberFormatException 失败，
                // 结果与 Go 的 ParseUint 一致（都失败），但负数 "-1" 在 Go 会因无符号而失败、
                // 在 Java Long.parseLong 会成功 —— 所以这里必须显式挡掉负号与前导 '+'。
                if (suffix.isEmpty() || suffix.startsWith("-") || suffix.startsWith("+")) {
                    throw new NumberFormatException(suffix);
                }
                number = Long.parseUnsignedLong(suffix);
            } catch (NumberFormatException e) {
                fail();
                LOG.warn("published_revision_id is not a valid revision number: id={} revision_id={}",
                        normalizedTargetId, revisionId, logOptions);
                throw FavoriteExceptions.unavailableServerCommon();
            }
            if (number == 0L) {
                fail();
                LOG.warn("published_revision_id revision number is zero: id={} revision_id={}",
                        normalizedTargetId, revisionId, logOptions);
                throw FavoriteExceptions.unavailableServerCommon();
            }
            if (!trimToEmpty(metadata.get(PUBLICATION_GAP)).isEmpty()) {
                fail();
                LOG.warn("publication_gap must be empty when a revision exists: id={} revision_id={} gap={}",
                        normalizedTargetId, revisionId, metadata.get(PUBLICATION_GAP), logOptions);
                throw FavoriteExceptions.unavailableServerCommon();
            }
        } else {
            // Go: service/favorite/rpc/internal/logic/helper.go:167-170 —— 无修订号时，唯一合法情形是明确标记为 legacy 空洞。
            if (!GAP_LEGACY_REVISION_MISSING.equals(metadata.get(PUBLICATION_GAP))) {
                fail();
                LOG.warn("article has no revision and no legacy marker: id={} gap={}",
                        normalizedTargetId, metadata.get(PUBLICATION_GAP), logOptions);
                throw FavoriteExceptions.unavailableServerCommon();
            }
        }

        // Go: service/favorite/rpc/internal/logic/helper.go:172 —— metrics.ObserveOp("dependency", "article_get", resultSuccess)
        metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                FavoriteRpcLabels.ACTION_ARTICLE_GET, FavoriteRpcLabels.RESULT_SUCCESS);

        // Go: service/favorite/rpc/internal/logic/helper.go:173-177
        return new ArticleSnapshot(trimToEmpty(article.getTitle()), articleCover(article), revisionId);
    }

    /**
     * 🔴 对应 Go {@code articleCover}（helper.go:180-202）。
     *
     * <p>三级回退，<b>顺序不可换</b>：
     * <ol>
     *   <li>{@code article.cover_image_url}（proto 字段，article.proto:22）</li>
     *   <li>{@code ext_info} 按 {@link #COVER_EXT_INFO_KEYS} 顺序取第一个非空</li>
     *   <li>都没有 ⇒ {@code ""}</li>
     * </ol>
     *
     * <p>⚠️ 每一步都 {@code TrimSpace}：Go 是
     * {@code if value := strings.TrimSpace(...); value != "" { return value }}。
     * 返回的是<b>已 trim 的值</b>，不是原值。
     */
    private static String articleCover(Article article) {
        if (article == null) {
            return "";
        }
        String coverImageUrl = trimToEmpty(article.getCoverImageUrl());
        if (!coverImageUrl.isEmpty()) {
            return coverImageUrl;
        }
        Map<String, String> extra = article.getExtInfoMap();
        if (extra.isEmpty()) {
            return "";
        }
        for (String key : COVER_EXT_INFO_KEYS) {
            String value = trimToEmpty(extra.get(key));
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** Go 的 map 取值语义：缺失键得到 {@code ""}（不是 null）
     *     （{@code service/favorite/rpc/internal/logic/helper.go:157-158}）。 */
    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** Go 的 {@code metrics.ObserveOp("dependency", "article_get", resultFail)}
     *     （{@code service/favorite/rpc/internal/logic/helper.go:140,149,154,164,168,172}）。 */
    private void fail() {
        metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                FavoriteRpcLabels.ACTION_ARTICLE_GET, FavoriteRpcLabels.RESULT_FAIL);
    }

    /**
     * Go {@code helper.go:139-146} 的错误分支 —— <b>唯一</b>会 {@code span.RecordError(err)} 的地方。
     *
     * <p>⚠️ <b>顺序必须与 Go 一致</b>：Go 是 {@code span.RecordError(err)} → {@code metrics.ObserveOp(...resultFail)}
     * → 再按 status 分支 ⇒ 本方法里 metrics 的观察发生在「翻译成业务码」之前。
     * <br>（{@code FavoriteSpans.tracedDependency} 已经在那之前完成了 {@code recordException}。）
     */
    private RuntimeException onDownstreamFailure(String normalizedTargetId, Throwable e, LogOption... logOptions) {
        if (!(e instanceof StatusRuntimeException sre)) {
            // ⚠️ 原实现只 catch StatusRuntimeException ⇒ 其它异常原样上抛（不被翻译）。
            //    这里逐字保留该行为，**不**在本次落码里顺手「改成 Go 的 status.FromError 语义」。
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(e);
        }
        fail();
        // Go: service/favorite/rpc/internal/logic/helper.go:142-146
        if (sre.getStatus().getCode() == Status.Code.NOT_FOUND) {
            throw FavoriteExceptions.articleNotFound();
        }
        LOG.error("article-rpc GetArticle failed: article_id={} code={} message={}",
                normalizedTargetId, sre.getStatus().getCode(), sre.getStatus().getDescription(), logOptions);
        throw FavoriteExceptions.internalServerCommon();
    }
}