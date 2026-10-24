package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.UpdateArticleReq;
import com.rag.article.article.api.dto.UpdateArticleResp;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.article.article.proto.ArticleStatus;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.article.article.proto.UpdateArticleRequest;
import com.rag.article.article.proto.UpdateArticleResponse;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

/**
 * 更新文章 —— 对应 Go {@code logic/article/update_article_logic.go}。
 *
 * <h2>🔴 <b>先读后写</b>：写之前必须先 {@code GetArticle} 做鉴权</h2>
 * <pre>
 *   Go: update_article_logic.go:43-67
 *       articleResp, err := l.svcCtx.ArticleRpc.GetArticle(l.ctx, &amp;articleservice.GetArticleRequest{
 *           ArticleId: req.ArticleId, IncrView: false, RequesterId: currentUserID,
 *       })
 *       … 错误映射 4 分支 …
 *       if articleResp == nil || articleResp.Article == nil { return nil, errmsg.ErrorArticleNone }
 *       if articleResp.Article.AuthorId != currentUserID { return nil, errmsg.ErrorArticleForbidden }
 * </pre>
 *
 * <h3>为什么不能省这次读（三条理由，缺一不可）</h3>
 * <ol>
 *   <li><b>ownership 判定</b>：{@code AuthorId != 当前用户 → 1006}。rpc 侧虽然也校验，
 *       但那条依赖对端实现；api 层自己判一次才是兜底（同 {@code GetAuthorArticle} 的理由）。</li>
 *   <li><b>错误码形状</b>：{@code NotFound → 1002} / {@code PermissionDenied → 1006}
 *       在<b>这次读</b>上就已经产生了。若直接调 {@code UpdateArticle}，
 *       Go 的 {@code UpdateArticle} 分支<b>没有</b> {@code PermissionDenied → 1006}
 *       （只有 NotFound / Internal / default），越权会变成 <b>1015</b>。</li>
 *   <li><b>不存在 vs 无权限的顺序</b>：先读再判 ⇒ 「文章不存在」优先于「不是你的」。</li>
 * </ol>
 * ⚠️ {@code IncrView: false} —— 鉴权用的读<b>不能</b>加阅读量。
 *
 * <h2>🔴 五个可选字段<b>非空才下发</b>，{@code secondary_tags} <b>恒下发</b>
 * <pre>
 *   Go: update_article_logic.go:69-92
 *       rpcReq := &amp;articleservice.UpdateArticleRequest{
 *           ArticleId:     req.ArticleId,
 *           SecondaryTags: req.SecondaryTags,      // ← 恒定（repeated，空/nil 同形）
 *       }
 *       if req.Title != ""          { rpcReq.Title = &amp;req.Title }
 *       if req.Brief != ""          { rpcReq.Brief = &amp;req.Brief }
 *       if req.Content != ""        { rpcReq.MarkdownContent = &amp;req.Content }
 *       if req.CoverImageUrl != ""  { rpcReq.CoverImageUrl = &amp;req.CoverImageUrl }
 *       if req.ManualTypeTag != ""  { rpcReq.ManualTypeTag = &amp;req.ManualTypeTag }
 *       if req.Status != 0          { rpcReq.Status = __.ArticleStatus(req.Status).Enum() }
 * </pre>
 *
 * <p>⚠️ <b>「非空才下发」不是 DTO 的职责，是业务语义</b>：它表达的是
 * 「不修改这个字段」，而<b>不是</b>「把字段设成空串」。
 * ⇒ 客户端<b>无法</b>通过本接口清空标题/简介/封面 —— 这是 Go 侧既有的能力缺口，
 * 照搬（不要「修复」它）。
 *
 * <h2>⚠️ {@code Status != 0} 且非法枚举值 ⇒ <b>字段不下发</b></h2>
 * <pre>
 *   Go: __.ArticleStatus(req.Status).Enum()     // 未知数值返回 nil
 *       ⇒ rpcReq.Status = nil  ⇒ proto has_status = false
 * </pre>
 * Java 侧等价：{@link ArticleStatus#forNumber(Integer)} 对未知值返回 {@code null}，
 * 此时<b>不调用</b> {@code setStatus}。写成 {@code setStatusValue(7)} 会让
 * 「非法状态」变成「以非法状态下发」，与 Go 不一致。
 */
@Service
public class ArticleUpdateService {

    private final ArticleRpcClient articleRpcClient;

    public ArticleUpdateService(ArticleRpcClient articleRpcClient) {
        this.articleRpcClient = articleRpcClient;
    }

    /**
     * @param currentUserId 已通过 {@code ArticleApiSupport#userId} 解析的当前用户
     *                      （该方法可能抛 {@code BusinessException(1005)}）
     */
    public UpdateArticleResp updateArticle(String articleId, UpdateArticleReq req, String currentUserId) {
        // ── ① 先读后写：鉴权 + 错误码形状 ──
        GetArticleResponse articleResp;
        try {
            articleResp = articleRpcClient.articleRpc().getArticle(GetArticleRequest.newBuilder()
                    .setArticleId(articleId)
                    // ⚠️ 鉴权用的读**不能**加阅读量
                    .setIncrView(false)
                    .setRequesterId(currentUserId)
                    .build());
        } catch (StatusRuntimeException e) {
            throw mapAuthReadError(e);
        }

        if (articleResp == null || !articleResp.hasArticle()) {
            throw business(ErrorCode.ERROR_ARTICLE_NONE);
        }
        // 🔴 ownership 闸门（Go: update_article_logic.go:65-67）
        if (!currentUserId.equals(articleResp.getArticle().getAuthorId())) {
            throw business(ErrorCode.ERROR_ARTICLE_FORBIDDEN);
        }

        // ── ② 组装可选字段 ──
        UpdateArticleRequest.Builder builder = UpdateArticleRequest.newBuilder().setArticleId(articleId);
        setIfNotEmpty(builder, req.getTitle(), UpdateArticleRequest.Builder::setTitle);
        setIfNotEmpty(builder, req.getBrief(), UpdateArticleRequest.Builder::setBrief);
        setIfNotEmpty(builder, req.getContent(), UpdateArticleRequest.Builder::setMarkdownContent);
        setIfNotEmpty(builder, req.getCoverImageUrl(), UpdateArticleRequest.Builder::setCoverImageUrl);
        setIfNotEmpty(builder, req.getManualTypeTag(), UpdateArticleRequest.Builder::setManualTypeTag);

        // Go: SecondaryTags 恒下发（repeated 字段，nil 与空 slice 在 proto 层同形）
        if (req.getSecondaryTags() != null) {
            builder.addAllSecondaryTags(req.getSecondaryTags());
        }

        // Go: if req.Status != 0 { rpcReq.Status = ArticleStatus(req.Status).Enum() }
        // ⚠️ Enum() 对未知数值返回 nil ⇒ 字段不下发
        Integer status = req.getStatus();
        if (status != null && status != 0) {
            ArticleStatus enumValue = ArticleStatus.forNumber(status);
            if (enumValue != null) {
                builder.setStatus(enumValue);
            }
        }

        // ── ③ 写 ──
        UpdateArticleResponse rpcResp;
        try {
            rpcResp = articleRpcClient.articleRpc().updateArticle(builder.build());
        } catch (StatusRuntimeException e) {
            // Go: update_article_logic.go:95-106 —— 只有三个分支
            // ⚠️ **没有** PermissionDenied → 1006；越权在这里会落进 default → 1015。
            //    但正常路径下 ① 已经把越权拦在 1006 了，所以这条分支实际不可达越权场景。
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                throw business(ErrorCode.ERROR_ARTICLE_NONE);
            }
            if (code == Status.Code.INTERNAL) {
                throw business(ErrorCode.ERROR_SERVER_COMMON);
            }
            throw business(ErrorCode.CODE_SERVER_BUSY);
        }

        // Go: return &types.UpdateArticleResp{Success: rpcResp.GetSuccess()}, errmsg.Success
        return new UpdateArticleResp(rpcResp.getSuccess());
    }

    /**
     * 鉴权那次读的 4 分支映射 —— Go {@code update_article_logic.go:48-61}。
     *
     * <p>⚠️ 与「写」那条的 3 分支<b>刻意不同</b>（这里有 {@code PermissionDenied → 1006}）。
     * 把两处合并成一个 mapper 就是一次 L2 漂移。
     */
    private static BusinessException mapAuthReadError(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        if (code == Status.Code.NOT_FOUND) {
            return business(ErrorCode.ERROR_ARTICLE_NONE);
        }
        if (code == Status.Code.PERMISSION_DENIED) {
            return business(ErrorCode.ERROR_ARTICLE_FORBIDDEN);
        }
        if (code == Status.Code.INTERNAL) {
            return business(ErrorCode.ERROR_SERVER_COMMON);
        }
        return business(ErrorCode.CODE_SERVER_BUSY);
    }

    /**
     * 对应 Go 的 {@code if req.X != "" { rpcReq.X = &amp;req.X }}。
     *
     * <p>形参是 {@link java.util.function.BiConsumer} 而非 {@code Consumer}：
     * protobuf 的 setter 是<b>实例方法</b>（{@code Builder.setTitle(String)}），
     * {@code Consumer<String>} 会把它解析成未绑定的 {@code Builder::setTitle} 而编译失败。
     */
    private static void setIfNotEmpty(UpdateArticleRequest.Builder builder,
                                       String value,
                                       java.util.function.BiConsumer<UpdateArticleRequest.Builder, String> setter) {
        if (value != null && !value.isEmpty()) {
            setter.accept(builder, value);
        }
    }

    static BusinessException business(ErrorCode code) {
        return new BusinessException(code.getCode(), ArticleApiSupport.msg(code));
    }
}