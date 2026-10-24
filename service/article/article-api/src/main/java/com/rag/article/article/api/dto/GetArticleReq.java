package com.rag.article.article.api.dto;

/**
 * 读单篇文章的入参 —— 对应 Go {@code types.go:44-47} 的 {@code GetArticleReq}。
 *
 * <pre>
 * Go:
 *   type GetArticleReq struct {
 *       ArticleId string `path:"id"`
 *       IncrView  bool   `form:"incr_view,optional"`
 *   }
 * </pre>
 *
 * <p><b>两个字段都不来自 JSON body</b>，所以本类<b>不</b>是 Jackson 绑定的目标，
 * 而是由 controller 从 {@code @PathVariable} + {@code @RequestParam} 显式构造。
 *
 * <h2>为什么 {@code incr_view} 要走 {@link com.rag.article.article.api.form.GoFormValues}</h2>
 * <p>它有<b>副作用</b>（RPC 侧据此 {@code +1} 阅读量），且 Go 的真值表是
 * {@code 1/0} 而非 Spring 默认的 {@code true/false}。详见该类注释「规则 1 / 规则 2」。
 *
 * <h2>⚠️ 本类被<b>两个不同鉴权级别的端点</b>复用</h2>
 * <pre>
 *   GET /v1/article/:id       （public，无 JWT）→ incrView 来自 query，可为 true
 *   GET /v1/me/article/:id   （JWT）         → incrView **恒为 false**
 * </pre>
 * 差异在 {@code GetAuthorArticle} 里<b>不</b>读 {@code IncrView}，
 * 而是固定传 {@code false}（见该 service）。Go 侧两个 handler 都用同一个
 * {@code GetArticleReq}，但只有 public 那个把 {@code req.IncrView} 透传给 RPC。
 */
public class GetArticleReq {

    private final String articleId;
    private final boolean incrView;

    public GetArticleReq(String articleId, boolean incrView) {
        this.articleId = articleId;
        this.incrView = incrView;
    }

    public String getArticleId() {
        return articleId;
    }

    public boolean isIncrView() {
        return incrView;
    }
}
