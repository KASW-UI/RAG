package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 举报评论请求 —— 对应 Go {@code types.go:85-91}。
 *
 * <pre>
 *   type ReportCommentReq struct {
 *       CommentId  int64  `json:"comment_id"`
 *       TargetType string `json:"target_type"`
 *       TargetId   string `json:"target_id"`
 *       Reason     int32  `json:"reason"`
 *       Detail     string `json:"detail"`
 *   }
 * </pre>
 *
 * <p><b>五个字段全部必填</b>（tag 均无 {@code ,optional}）。
 *
 * <p><b>⚠️ {@code reason} 必须按 open enum 原样透传</b>：
 * Go 侧 {@code reportcommentlogic.go:46} 是 {@code Reason: pb.ReportReason(req.Reason)}，
 * 同样是纯转换。Java 侧必须用 {@code setReasonValue(v)} 而不是
 * {@code setReason(ReportReason.forNumber(v))}，理由同 {@link ManageCommentReq} 的类注释。
 */
public class ReportCommentReq implements GoStyleRequiredFields {

    @JsonProperty("comment_id")
    private Long commentId;

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("reason")
    private Integer reason;

    @JsonProperty("detail")
    private String detail;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("comment_id", commentId);
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("reason", reason);
        GoStyleRequired.field("detail", detail);
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

    public Integer getReason() {
        return reason;
    }

    public void setReason(Integer reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
