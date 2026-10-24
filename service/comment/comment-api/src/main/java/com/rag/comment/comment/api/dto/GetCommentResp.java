package com.rag.comment.comment.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * 拉评论响应 —— 对应 Go {@code types.go:57-60}。
 *
 * <pre>
 *   type GetCommentResp struct {
 *       Comment []CommentItem `json:"comment"`
 *       Subject SubjectInfo   `json:"subject"`
 *   }
 * </pre>
 *
 * <p><b>⚠️ 两个字段在 wire 上都恒不为 null</b>：
 * Go {@code getcommentlogic.go:78-80} 先建 {@code resp = &types.GetCommentResp{
 * Comment: make([]types.CommentItem, 0)}}，且 {@code Subject} 是<b>值类型</b>
 * ⇒ 空结果输出 {@code "comment":[], "subject":{全零}}，而不是 {@code null} 或被省略。
 * Java 侧据此初始化，见 {@code GetCommentService}。
 */
public class GetCommentResp {

    @JsonProperty("comment")
    private List<CommentItem> comment = new ArrayList<>();

    @JsonProperty("subject")
    private SubjectInfo subject = new SubjectInfo();

    public List<CommentItem> getComment() {
        return comment;
    }

    public void setComment(List<CommentItem> comment) {
        this.comment = comment == null ? new ArrayList<>() : comment;
    }

    public SubjectInfo getSubject() {
        return subject;
    }

    public void setSubject(SubjectInfo subject) {
        this.subject = subject;
    }
}
