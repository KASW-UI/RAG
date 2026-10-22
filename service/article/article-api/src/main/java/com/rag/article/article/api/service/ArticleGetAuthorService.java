package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.Article;
import com.rag.article.article.api.dto.GetArticleResp;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

/**
 * 作者读自己的文章 —— 对应 Go {@code logic/article/get_author_article_logic.go}。
 *
 * <h2>Go 源码里那段注释必须连注释一起迁移</h2>
 * <pre>
 *   // Keep the HTTP authorization decision even while RPC instances are
 *   // upgraded; an older RPC ignores requester_id.
 *   if a.AuthorId != authorID { return nil, errmsg.ErrorArticleForbidden }
 * </pre>
 *
 * <p>即：{@code requester_id} 同样是「<b>请求</b>，对端是否认是<b>对端的事</b>」。
 * 对端是旧版本时会无视 {@code requester_id}，于是任意登录用户都能读到别人的草稿。
 * ⇒ api 层的 {@code AuthorId != authorID} 比对是<b>唯一</b>的兜底，不能省。
 *
 * <h2>⚠️ 本端点<b>不</b>套 {@code PublicOnly} 闸门，但<b>要</b>自己比 ownership</h2>
 * 两层分工：
 * <ul>
 *   <li>{@code PublicOnly=false}：允许读到草稿（作者要看自己的草稿）；</li>
 *   <li>{@code AuthorId != 当前用户 → 1006}：挡住越权。</li>
 * </ul>
 * 少了任何一层都会出问题：少了前者作者看不到自己的草稿；
 * 少了后者在旧 rpc 上会泄露别人的草稿。
 *
 * <h2>⚠️ {@code IncrView} <b>恒 false</b></h2>
 * Go {@code get_author_article_logic.go:38-40} 构造请求时<b>没有</b> {@code IncrView} 字段
 * ⇒ proto 默认 {@code false}。
 * ⚠️ 本端点复用了 {@code GetArticleReq} 这个 Go struct（它<b>有</b> {@code IncrView} 字段），
 * 但作者读的 handler <b>忽略</b>它 ——
 * 即 {@code GET /v1/me/article/x?incr_view=true} <b>不会</b>加阅读量。
 * Java 侧刻意<b>不</b>接受该参数（签名里根本没有它），让这条差异从类型上消失。
 */
@Service
public class ArticleGetAuthorService {

    private final ArticleRpcClient articleRpcClient;
    private final ArticleApiSupport support;
    private final ArticleAuthorResolver authorResolver;

    public ArticleGetAuthorService(ArticleRpcClient articleRpcClient,
                                   ArticleApiSupport support,
                                   ArticleAuthorResolver authorResolver) {
        this.articleRpcClient = articleRpcClient;
        this.support = support;
        this.authorResolver = authorResolver;
    }

    public GetArticleResp getAuthorArticle(String articleId, String authorId) {
        GetArticleResponse res;
        try {
            res = articleRpcClient.articleRpc().getArticle(GetArticleRequest.newBuilder()
                    .setArticleId(articleId)
                    .setRequesterId(authorId)
                    // IncrView / PublicOnly 均不设 ⇒ false，与 Go 一致
                    .build());
        } catch (StatusRuntimeException e) {
            // Go: 四个分支 —— 这里比 public 读**多一个** PermissionDenied → 1006
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                throw business(ErrorCode.ERROR_ARTICLE_NONE);
            }
            if (code == Status.Code.PERMISSION_DENIED) {
                throw business(ErrorCode.ERROR_ARTICLE_FORBIDDEN);
            }
            if (code == Status.Code.INTERNAL) {
                throw business(ErrorCode.ERROR_SERVER_COMMON);
            }
            throw business(ErrorCode.CODE_SERVER_BUSY);
        }

        if (res == null || !res.hasArticle()) {
            throw business(ErrorCode.ERROR_ARTICLE_NONE);
        }

        // 🔴 ownership 闸门（Go 的注释解释过：旧 rpc 会忽略 requester_id）
        Article article = ArticleApiSupport.toArticle(res.getArticle());
        if (!authorId.equals(article.getAuthorId())) {
            throw business(ErrorCode.ERROR_ARTICLE_FORBIDDEN);
        }

        support.enrichArticleAuthor(article, authorResolver);
        return new GetArticleResp(article);
    }

    static BusinessException business(ErrorCode code) {
        return new BusinessException(code.getCode(), ArticleApiSupport.msg(code));
    }
}