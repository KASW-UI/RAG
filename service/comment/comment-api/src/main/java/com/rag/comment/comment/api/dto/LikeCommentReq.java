package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 点赞/点踩请求 —— 对应 Go {@code types.go:62-67}。
 *
 * <pre>
 *   type LikeCommentReq struct {
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *       CommentId  int64  `json:"comment_id"`
 *       ActionType int32  `json:"action_type"`
 *   }
 * </pre>
 *
 * <p><b>四个字段全部必填</b>（tag 均无 {@code ,optional}）。
 *
 * <p><b>⚠️ {@code action_type} 是 int32 原样透传</b>：Go 侧
 * {@code pb.LikeCommentReq.ActionType} 在 proto 里就是 {@code int32 action_type = 5}
 * （<b>不是</b>枚举），所以 0~4 与 99 都能原样发到 rpc 层。
 * 本层<b>不做</b>任何范围校验，校验归 comment-rpc。
 */
public class LikeCommentReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("comment_id")
    private Long commentId;

    @JsonProperty("action_type")
    private Integer actionType;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("comment_id", commentId);
        GoStyleRequired.field("action_type", actionType);
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

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public Integer getActionType() {
        return actionType;
    }

    public void setActionType(Integer actionType) {
        this.actionType = actionType;
    }
}
