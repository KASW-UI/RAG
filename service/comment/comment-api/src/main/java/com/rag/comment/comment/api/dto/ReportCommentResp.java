package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 举报响应 —— 对应 Go {@code types.go:93-95}，取自 rpc（{@code reportcommentlogic.go:61-63}）。 */
public class ReportCommentResp {

    @JsonProperty("success")
    private boolean success;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
