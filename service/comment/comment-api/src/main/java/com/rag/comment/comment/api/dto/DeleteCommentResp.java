package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 删评论响应 —— 对应 Go {@code types.go:43-46}。
 *
 * <p><b>⚠️ {@code success} 恒为 {@code true}</b>：Go {@code deletecommentlogic.go:58}
 * 写的是字面量 {@code Success: true}，<b>不是</b> {@code rpcResp.Success}。
 * 与 {@code LikeCommentResp} / {@code ManageCommentResp} 等（那些取 rpc 的值）不同 ——
 * 删评论路径上只要没抛异常就一定是成功，返回体不承载任何额外信息。
 */
public class DeleteCommentResp {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("subject_total_count")
    private long subjectTotalCount;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getSubjectTotalCount() {
        return subjectTotalCount;
    }

    public void setSubjectTotalCount(long subjectTotalCount) {
        this.subjectTotalCount = subjectTotalCount;
    }
}
