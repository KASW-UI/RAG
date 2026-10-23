package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 评论管理响应 —— 对应 Go {@code types.go:81-83}，取自 rpc（{@code managecommentlogic.go:59-61}）。 */
public class ManageCommentResp {

    @JsonProperty("success")
    private boolean success;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
