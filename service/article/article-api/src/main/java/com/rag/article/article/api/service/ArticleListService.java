package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.Article;
import com.rag.article.article.api.dto.ListArticlesReq;
import com.rag.article.article.api.dto.ListArticlesResp;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.article.article.proto.ListArticlesRequest;
import com.rag.article.article.proto.ListArticlesResponse;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 公开文章列表 —— 对应 Go {@code logic/article/list_articles_logic.go}。
 *
 * <h2>🔴 四个筛选字段<b>全部</b>以「非 nil 指针」下发（哪怕是空串）</h2>
 * <pre>
 *   Go: list_articles_logic.go:37-47
 *       res, err := l.svcCtx.ArticleRpc.ListArticles(l.ctx, &amp;articleservice.ListArticlesRequest{
 *           ManualTypeTag: &amp;req.ManualTypeTag,      // ← &amp;：空串也下发，proto 里是 has 语义
 *           SecondaryTag:  &amp;req.SecondaryTag,
 *           RelatedGameId: &amp;req.RelatedGameId,
 *           AuthorId:      &amp;req.AuthorId,
 *           Page: req.Page, PageSize: req.PageSize, SortBy: req.SortBy, Desc: req.Desc,
 *           PublicOnly: true,
 *       })
 * </pre>
 *
 * <p>⚠️ <b>这是 proto3 optional 的 presence 语义</b>：{@code optional string manual_type_tag = 1}
 * 区分「没传」与「传了空串」。
 * Go 侧<b>总是</b>传（{@code &req.ManualTypeTag} 是非 nil 指针，值为 {@code ""}），
 * 所以 rpc 看到的是 {@code has = true, value = ""}。
 * Java 侧<b>不能</b>用 {@code .setManualTypeTag("")} 一把梭 ——
 * protobuf 的 {@code setXxx("")} 会置 {@code has}，这没问题；
 * 但<b>不能</b>用 {@code clearXxx()} 或省略 setter，否则变成 {@code has = false}，
 * rpc 侧「空串筛选」与「不筛选」就会<b>走不同分支</b>
 * （{@code list_articles_logic.go} 里是 {@code if req.ManualTypeTag != nil &amp;&amp; *req.ManualTypeTag != "" {…}}）。
 *
 * <h2>🔴 状态校验失败时<b>整条请求</b>返回 5001（不是跳过该行）</h2>
 * <pre>
 *   Go: list_articles_logic.go:63-68
 *       for _, item := range res.Articles {
 *           if item == nil || item.Status != articlepb.ArticleStatus_PUBLISHED {
 *               // An old RPC instance ignores public_only. Do not serialize its
 *               // source rows through a public HTTP endpoint.
 *               return nil, errmsg.ErrorServerCommon
 *           }
 *           articles = append(articles, …)
 *       }
 * </pre>
 * ⚠️ <b>注意是 {@code return} 不是 {@code continue}</b>：
 * 一旦发现任何一行非已发布，<b>整个响应变成 5001</b>，
 * 之前已 append 的行<b>全部丢弃</b>（Go 直接返回 nil resp）。
 * 「过滤掉那几行再返回其余」是一个很容易被「顺手优化」的分支，
 * 但它把「对端不可信」变成了「部分可用」，而 Go 侧选择的是「整体失败」。
 *
 * <h2>⚠️ {@code Page} / {@code PageSize} / {@code Total} <b>取自 rpc 响应</b>，不取自请求</h2>
 * Go {@code list_articles_logic.go:90-95}：
 * <pre>return &amp;types.ListArticlesResp{Articles: articles, Total: res.Total, Page: res.Page, PageSize: res.PageSize}</pre>
 * ⇒ 回显的是<b>对端实际使用的</b>分页（rpc 可能对越界值做了钳制）。
 * 用 {@code req.Page} 回显会在 rpc 钳制时给出与实际数据不符的元信息。
 *
 * <h2>⚠️ {@code articles} 空时是 {@code null} 而非 {@code []}</h2>
 * 见 {@link ListArticlesResp} 的类注释。
 */
@Service
public class ArticleListService {

    private final ArticleRpcClient articleRpcClient;
    private final ArticleApiSupport support;
    private final ArticleAuthorResolver authorResolver;

    public ArticleListService(ArticleRpcClient articleRpcClient,
                              ArticleApiSupport support,
                              ArticleAuthorResolver authorResolver) {
        this.articleRpcClient = articleRpcClient;
        this.support = support;
        this.authorResolver = authorResolver;
    }

    public ListArticlesResp listArticles(ListArticlesReq req) {
        ListArticlesResponse res;
        try {
            res = articleRpcClient.articleRpc().listArticles(ListArticlesRequest.newBuilder()
                    .setManualTypeTag(req.getManualTypeTag())
                    .setSecondaryTag(req.getSecondaryTag())
                    .setRelatedGameId(req.getRelatedGameId())
                    .setAuthorId(req.getAuthorId())
                    .setPage(req.getPage())
                    .setPageSize(req.getPageSize())
                    .setSortBy(req.getSortBy())
                    .setDesc(req.isDesc())
                    .setPublicOnly(true)
                    .build());
        } catch (StatusRuntimeException e) {
            // Go: 只有两个分支 —— Internal → 5001，其余（含 NotFound）→ 1015
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.INTERNAL) {
                throw business(ErrorCode.ERROR_SERVER_COMMON);
            }
            throw business(ErrorCode.CODE_SERVER_BUSY);
        }

        if (res == null) {
            // Go: list_articles_logic.go:58-60 `if res == nil { return nil, errmsg.ErrorServerCommon }`
            throw business(ErrorCode.ERROR_SERVER_COMMON);
        }

        List<Article> articles = null;
        for (com.rag.article.article.proto.Article item : res.getArticlesList()) {
            // 🔴 任一行非已发布 ⇒ 整条请求失败（Go 是 return nil，不是 continue）
            if (!ArticleApiSupport.isPublished(item.getStatusValue())) {
                throw business(ErrorCode.ERROR_SERVER_COMMON);
            }
            if (articles == null) {
                articles = new ArrayList<>(res.getArticlesCount());
            }
            articles.add(ArticleApiSupport.toArticle(item));
        }
        // ⚠️ 保持 null：Go 的 `var articles []types.Article` 在无行时是 nil slice
        //    ⇒ JSON "articles":null。本类刻意不用 Collections.emptyList()。

        support.enrichArticleAuthors(articles, authorResolver);

        ListArticlesResp out = new ListArticlesResp();
        out.setArticles(articles);
        out.setTotal(res.getTotal());      // ← 取 rpc 的，不是 req 的
        out.setPage(res.getPage());
        out.setPageSize(res.getPageSize());
        return out;
    }

    static BusinessException business(ErrorCode code) {
        return new BusinessException(code.getCode(), ArticleApiSupport.msg(code));
    }
}