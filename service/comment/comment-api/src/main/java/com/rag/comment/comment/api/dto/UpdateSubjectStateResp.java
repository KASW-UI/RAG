package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 改评论区状态响应 —— 对应 Go {@code types.go:113-115}，取自 rpc（{@code updatesubjectstatelogic.go:57-59}）。 */
public class UpdateSubjectStateResp {

    @JsonProperty("success")
    private boolean success;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
