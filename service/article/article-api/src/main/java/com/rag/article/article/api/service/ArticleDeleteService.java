package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.DeleteArticleReq;
import com.rag.article.article.api.dto.DeleteArticleResp;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.article.article.proto.DeleteArticleRequest;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

/**
 * 删除文章 —— 对应 Go {@code logic/article/delete_article_logic.go}。
 *
 * <h2>🔴 与更新<b>同型</b>：先读后写，读的那次就是鉴权</h2>
 * <pre>
 *   Go: delete_article_logic.go:42-85
 *       articleResp, err := ArticleRpc.GetArticle(&amp;GetArticleRequest{
 *           ArticleId: req.ArticleId, IncrView: false, RequesterId: operatorID,
 *       })
 *       … 4 分支映射 …
 *       if articleResp == nil || articleResp.Article == nil { return nil, errmsg.ErrorArticleNone }
 *       if articleResp.Article.AuthorId != operatorID    { return nil, errmsg.ErrorArticleForbidden }
 *
 *       _, err = ArticleRpc.DeleteArticle(&amp;DeleteArticleRequest{
 *           ArticleId: req.ArticleId, OperatorId: operatorID,
 *       })
 *       … **同一个 4 分支映射** …
 * </pre>
 *
 * <h3>两个 id 都要带，理由不同</h3>
 * <ul>
 *   <li>{@code RequesterId}：让<b>对端</b>也能做一次鉴权（纵深防御）；</li>
 *   <li>{@code OperatorId}：对端记录「谁删的」（审计），并可能用于管理员豁免。</li>
 * </ul>
 * ⚠️ <b>只带其中一个</b>就是丢行为：只带 RequesterId ⇒ 对端记不到操作人；
 * 只带 OperatorId ⇒ 对端无法在 rpc 层拒越权（只剩 api 层的兜底）。
 *
 * <h2>⚠️ 与 {@code UpdateArticle} 的差别：删除的写分支<b>也</b>有 4 个</h2>
 * Go 里读、写两处的 switch <b>逐字相同</b>（都含 {@code PermissionDenied → 1006}），
 * 与 {@code UpdateArticle} 的写分支（3 个）不同。
 * 照抄 Go，不要「统一成三个分支」。
 *
 * <h2>⚠️ 成功恒 {@code success = true}</h2>
 * Go 只在 {@code DeleteArticle} 返回 nil error 时才构造 resp，
 * 所以 {@code true} 是唯一的值；<b>不存在</b>「200 + success=false」的响应。
 * （对比 {@code UpdateArticleResp}，那个取 rpc 的 {@code GetSuccess()}，可能是 false。）
 */
@Service
public class ArticleDeleteService {

    private final ArticleRpcClient articleRpcClient;

    public ArticleDeleteService(ArticleRpcClient articleRpcClient) {
        this.articleRpcClient = articleRpcClient;
    }

    public DeleteArticleResp deleteArticle(DeleteArticleReq req) {
        String operatorId = req.getOperatorId();

        // ── ① 鉴权读 ──
        GetArticleResponse articleResp;
        try {
            articleResp = articleRpcClient.articleRpc().getArticle(GetArticleRequest.newBuilder()
                    .setArticleId(req.getArticleId())
                    .setIncrView(false)
                    .setRequesterId(operatorId)
                    .build());
        } catch (StatusRuntimeException e) {
            throw mapError(e);
        }

        if (articleResp == null || !articleResp.hasArticle()) {
            throw business(ErrorCode.ERROR_ARTICLE_NONE);
        }
        if (!operatorId.equals(articleResp.getArticle().getAuthorId())) {
            throw business(ErrorCode.ERROR_ARTICLE_FORBIDDEN);
        }

        // ── ② 删除 ──
        try {
            articleRpcClient.articleRpc().deleteArticle(DeleteArticleRequest.newBuilder()
                    .setArticleId(req.getArticleId())
                    .setOperatorId(operatorId)
                    .build());
        } catch (StatusRuntimeException e) {
            // Go 侧读、写两处是**同一个** 4 分支 switch
            throw mapError(e);
        }

        return new DeleteArticleResp(true);
    }

    /**
     * Go {@code delete_article_logic.go} 的 4 分支 switch，读写两处<b>共用同一个</b>：
     * <pre>
     *   codes.NotFound         → errmsg.ErrorArticleNone       // 1002
     *   codes.PermissionDenied → errmsg.ErrorArticleForbidden   // 1006
     *   codes.Internal         → errmsg.ErrorServerCommon       // 5001
     *   default                → errmsg.CodeServerBusy          // 1015
     * </pre>
     */
    private static BusinessException mapError(StatusRuntimeException e) {
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

    static BusinessException business(ErrorCode code) {
        return new BusinessException(code.getCode(), ArticleApiSupport.msg(code));
    }
}