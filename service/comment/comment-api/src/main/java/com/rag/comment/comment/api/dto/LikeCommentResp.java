package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 点赞/点踩响应 —— 对应 Go {@code types.go:69-72}，取自 rpc（{@code likecommentlogic.go:58-61}）。 */
public class LikeCommentResp {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("like_count")
    private long likeCount;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }
}
