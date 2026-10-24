package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 删除文章响应 —— 对应 Go {@code types.go:39-41} 的 {@code DeleteArticleResp}。
 */
public class DeleteArticleResp {

    /**
     * ⚠️ 恒为 {@code true}：Go {@code delete_article_logic.go:87-89} 只在
     * {@code DeleteArticle} RPC <b>返回 nil error</b> 时才构造本响应。
     * 本类不设默认 {@code true}（避免「构造了但没赋值」也输出 true），
     * 由 service 在成功分支显式传 {@code true}。
     */
    private boolean success;

    public DeleteArticleResp() {
    }

    public DeleteArticleResp(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
