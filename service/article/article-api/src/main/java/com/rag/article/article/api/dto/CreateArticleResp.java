package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 创建文章响应 —— 对应 Go {@code types.go:35-37} 的 {@code CreateArticleResp}。
 */
public class CreateArticleResp {

    /**
     * ⚠️ <b>本字段在两条失败路径上也会被填</b>，不是「成功才有值」：
     * Go {@code create_article_logic.go:60-69} 对 {@code FailedPrecondition} 与
     * {@code DeadlineExceeded} 返回<b>带 article_id 的 resp</b>，
     * 同时给 {@code code = 1004 / 1003}。
     * ⇒ 信封形状是 {@code {"code":1004,"msg":"文章发布失败","data":{"article_id":"…"}}}。
     * 见 {@code ArticleCreateService}。
     */
    @JsonProperty("article_id")
    private String articleId;

    public CreateArticleResp() {
    }

    public CreateArticleResp(String articleId) {
        this.articleId = articleId;
    }

    public String getArticleId() {
        return articleId;
    }

    public void setArticleId(String articleId) {
        this.articleId = articleId;
    }
}
