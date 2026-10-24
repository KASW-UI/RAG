package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 删评论请求 —— 对应 Go {@code types.go:37-41}。
 *
 * <pre>
 * Go:
 *   type DeleteCommentReq struct {
 *       CommentId  int64  `json:"comment_id"`
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *   }
 * </pre>
 *
 * <p><b>三个字段全部必填</b>（tag 均无 {@code ,optional}）——
 * 缺任一字段 ⇒ 解析失败 ⇒ 200 + 5001。
 *
 * <p><b>⚠️ 请求里没有 {@code user_id}</b>：操作人来自 JWT claim 的 {@code userId}，
 * 由 {@code CommentApiSupport#userId} 取出后塞进 RPC 请求（Go: {@code extractUserID(l.ctx)}）。
 * 客户端<b>无法</b>通过 body 指定操作人。
 */
public class DeleteCommentReq implements GoStyleRequiredFields {

    @JsonProperty("comment_id")
    private Long commentId;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("comment_id", commentId);
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
    }

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }
}
