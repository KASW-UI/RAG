package com.rag.article.article.api.service;

import com.rag.article.article.api.client.ArticleRpcClient;
import com.rag.article.article.api.dto.Article;
import com.rag.article.article.api.dto.GetArticleResp;
import com.rag.article.article.api.form.GoFormValues;
import com.rag.article.article.common.errmsg.ErrorCode;
import com.rag.article.article.proto.GetArticleRequest;
import com.rag.article.article.proto.GetArticleResponse;
import com.rag.common.exception.BusinessException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

/**
 * 公开读单篇 —— 对应 Go {@code logic/article/get_article_logic.go}。
 *
 * <pre>
 * Go:
 *   func (l *GetArticleLogic) GetArticle(req *types.GetArticleReq) (resp *types.GetArticleResp, code int) {
 *       res, err := l.svcCtx.ArticleRpc.GetArticle(l.ctx, &amp;articleservice.GetArticleRequest{
 *           ArticleId: req.ArticleId, IncrView: req.IncrView, PublicOnly: true,
 *       })
 *       if err != nil {
 *           st, _ := status.FromError(err)
 *           switch st.Code() {
 *           case codes.NotFound: return nil, errmsg.ErrorArticleNone        // 1002
 *           case codes.Internal: return nil, errmsg.ErrorServerCommon       // 5001
 *           default:              return nil, errmsg.CodeServerBusy          // 1015
 *           }
 *       }
 *       if res == nil || res.Article == nil || res.Article.Status != articlepb.ArticleStatus_PUBLISHED {
 *           return nil, errmsg.ErrorArticleNone                                // 1002
 *       }
 *       …
 *       enrichArticleAuthor(l.ctx, l.svcCtx, &amp;resp.Article)
 *       return resp, errmsg.Success
 *   }
 * </pre>
 *
 * <h2>🔴 三个请求字段<b>一个都不能少</b>（Go 测试逐条断言过）</h2>
 * <pre>
 *   ArticleId  ← path
 *   IncrView   ← query（**有副作用**：rpc 侧据此 +1 阅读量）
 *   PublicOnly ← **恒 true**，不来自入参
 * </pre>
 * Go 侧 {@code public_read_handler_test.go:145-149} 的断言：
 * <pre>
 *   rpc.getCalls[0].PublicOnly &amp;&amp; rpc.getCalls[0].IncrView
 *   rpc.getCalls[1].PublicOnly &amp;&amp; rpc.getCalls[2].PublicOnly
 *   rpc.getCalls[3].RequesterId == "2002" &amp;&amp; !rpc.getCalls[3].PublicOnly   ← 作者读法不同
 * </pre>
 *
 * <h2>🔴 {@code Status != PUBLISHED → 1002} 是<b>安全闸门</b>，不是冗余防御</h2>
 *
 * <p>{@code public_only=true} 是「请只返回已发布版本」的<b>请求</b>，
 * 而「真的只返回已发布版本」依赖对端实现。若对端是未升级的旧版本会无视这个字段，
 * 草稿就会从<b>公开接口</b>漏出去 ⇒ api 层<b>必须</b>自己再判一次。
 *
 * <p>⚠️ 选 <b>1002（不存在）</b>而不是「无权限」也是语义选择：
 * 对公开接口返回 403 会泄露「这个 id 确实存在」。
 * Go 测试的 {@code stale-backend} 用例断言的就是「不泄露草稿正文，且报 1002」。
 *
 * <h2>⚠️ 与「作者读」的三处差异（不要合并成同一个 service）</h2>
 * <table border="1">
 *   <caption>public 读 vs 作者读</caption>
 *   <tr><th></th><th>{@code GetArticle}（本类）</th><th>{@code GetAuthorArticle}</th></tr>
 *   <tr><td>路由</td><td>{@code GET /v1/article/:id}（公开）</td><td>{@code GET /v1/me/article/:id}（JWT）</td></tr>
 *   <tr><td>{@code PublicOnly}</td><td><b>true</b></td><td><b>false</b></td></tr>
 *   <tr><td>{@code RequesterId}</td><td><b>不设</b>（空串）</td><td><b>= 当前用户</b></td></tr>
 *   <tr><td>{@code IncrView}</td><td><b>来自 query</b></td><td><b>恒 false</b></td></tr>
 *   <tr><td>非 PUBLISHED</td><td>1002</td><td><b>不拦</b>（作者可看自己的草稿）</td></tr>
 * </table>
 *
 * <h2>⚠️ 本类<b>不</b>做「取不到 userId」的判定</h2>
 * 这个端点<b>公开</b>：Go 侧的 {@code GetArticleLogic} 压根不调 {@code extractCurrentUserID}。
 * 加一个「必须有登录用户」的判断会把公开接口变成半公开。
 */
@Service
public class ArticleGetService {

    private final ArticleRpcClient articleRpcClient;
    private final ArticleApiSupport support;
    private final ArticleAuthorResolver authorResolver;

    public ArticleGetService(ArticleRpcClient articleRpcClient,
                             ArticleApiSupport support,
                             ArticleAuthorResolver authorResolver) {
        this.articleRpcClient = articleRpcClient;
        this.support = support;
        this.authorResolver = authorResolver;
    }

    /**
     * @param rawIncrView <b>原始</b> query string 值（可能 null / 空串）；
     *                    真值表由 {@link GoFormValues#bool} 按 Go 的
     *                    {@code convertTypeFromString} 判定（{@code 1/0} 而非 {@code true/false}）
     */
    public GetArticleResp getArticle(String articleId, String rawIncrView) {
        boolean incrView = GoFormValues.bool(rawIncrView, "incr_view", false);

        GetArticleResponse res;
        try {
            res = articleRpcClient.articleRpc().getArticle(GetArticleRequest.newBuilder()
                    .setArticleId(articleId)
                    .setIncrView(incrView)
                    .setPublicOnly(true)
                    .build());
        } catch (StatusRuntimeException e) {
            // Go: switch st.Code() —— 只有三个分支，PermissionDenied 落进 default
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                throw business(ErrorCode.ERROR_ARTICLE_NONE);
            }
            if (code == Status.Code.INTERNAL) {
                throw business(ErrorCode.ERROR_SERVER_COMMON);
            }
            throw business(ErrorCode.CODE_SERVER_BUSY);
        }

        // 🔴 安全闸门：老版本 rpc 忽略 public_only ⇒ 这里必须自己再判一次
        // ⚠️ hasArticle() 对应 Go 的 `res.Article == nil`（proto3 message field 有 presence）
        if (res == null || !res.hasArticle()
                || !ArticleApiSupport.isPublished(res.getArticle().getStatusValue())) {
            throw business(ErrorCode.ERROR_ARTICLE_NONE);
        }

        Article article = ArticleApiSupport.toArticle(res.getArticle());
        support.enrichArticleAuthor(article, authorResolver);
        return new GetArticleResp(article);
    }

    static BusinessException business(ErrorCode code) {
        return new BusinessException(code.getCode(), ArticleApiSupport.msg(code));
    }
}