package com.rag.article.article.api.dto;

/**
 * 删除文章的入参 —— 对应 Go {@code types.go:35-37} 的 {@code DeleteArticleReq}。
 *
 * <pre>
 * Go:
 *   type DeleteArticleReq struct {
 *       ArticleId string `path:"id"`
 *   }
 * </pre>
 *
 * <p>Go 侧是零必填校验（{@code path:"id"} 只有一个字段，且它只在<b>路由命中</b>时才会被读），
 * 所以本类不实现 {@code GoStyleRequiredFields}。
 *
 * <p><b>它携带两个 id</b>：{@code articleId} 来自 URL（待删对象），
 * {@code operatorId} 来自 JWT（操作人，Go 的 {@code extractCurrentUserID}）。
 * 两个都要带的理由见 {@code ArticleDeleteService}：
 * Go 侧先用 {@code operatorId} 做一次「读 + 比对 author_id」的鉴权，再带
 * {@code OperatorId} 调 {@code DeleteArticle}，<b>两道都不能省</b>。
 */
public class DeleteArticleReq {

    private final String articleId;
    private final String operatorId;

    public DeleteArticleReq(String articleId, String operatorId) {
        this.articleId = articleId;
        this.operatorId = operatorId;
    }

    public String getArticleId() {
        return articleId;
    }

    public String getOperatorId() {
        return operatorId;
    }
}
