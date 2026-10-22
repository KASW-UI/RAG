package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 读单篇响应 —— 对应 Go {@code types.go:49-51} 的 {@code GetArticleResp}。
 */
public class GetArticleResp {

    private Article article;

    public GetArticleResp() {
    }

    public GetArticleResp(Article article) {
        this.article = article;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }
}
