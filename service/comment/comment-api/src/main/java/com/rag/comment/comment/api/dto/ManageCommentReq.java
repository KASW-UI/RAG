package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 评论管理请求（置顶/精选）—— 对应 Go {@code types.go:74-79}。
 *
 * <pre>
 *   type ManageCommentReq struct {
 *       CommentId  int64  `json:"comment_id"`
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *       ActionType int32  `json:"action_type"`
 *   }
 * </pre>
 *
 * <p><b>四个字段全部必填</b>（tag 均无 {@code ,optional}）。
 *
 * <p><b>⚠️ {@code action_type} 必须按 open enum 原样透传</b>：
 * Go 侧 {@code managecommentlogic.go:46} 写的是 {@code ActionType: pb.ManageType(req.ActionType)}
 * —— 纯类型转换，没有 {@code forNumber} 之类的有效性检查，
 * 因此 proto3 开放枚举下 <b>未知值（如 99）也能原样发出去</b>。
 * Java 侧若用 {@code setActionType(ManageType.forNumber(v))}，{@code forNumber(99)} 返回
 * {@code null} ⇒ 字段退化为缺省 0 ⇒ <b>静默改写客户端意图</b>。
 * 正确写法见 {@code ManageCommentService}：{@code setActionTypeValue(v)}。
 */
public class ManageCommentReq implements GoStyleRequiredFields {

    @JsonProperty("comment_id")
    private Long commentId;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("action_type")
    private Integer actionType;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("comment_id", commentId);
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("action_type", actionType);
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

    public Integer getActionType() {
        return actionType;
    }

    public void setActionType(Integer actionType) {
        this.actionType = actionType;
    }
}
