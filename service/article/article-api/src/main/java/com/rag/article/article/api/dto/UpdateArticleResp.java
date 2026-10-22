package com.rag.article.article.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 更新文章响应 —— 对应 Go {@code types.go:73-75} 的 {@code UpdateArticleResp}。
 */
public class UpdateArticleResp {

    /**
     * ⚠️ 直接取 RPC 的 {@code GetSuccess()}，<b>不是</b>无条件 {@code true}。
     * Go {@code update_article_logic.go:108-110}：
     * <pre>return &types.UpdateArticleResp{Success: rpcResp.GetSuccess()}, errmsg.Success</pre>
     * ⇒ 「code=200 但 success=false」是<b>合法且可能</b>的响应，不要改成 true。
     */
    private boolean success;

    public UpdateArticleResp() {
    }

    public UpdateArticleResp(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
