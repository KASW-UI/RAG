package com.rag.comment.comment.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 评论举报记录 ORM 实体 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/data_model.go:55-68} 的 {@code ReportRecord}。
 *
 * <p>表结构：{@code V5__create_comment_report_record.sql}。
 *
 * <p><b>唯一键 {@code (user_id, comment_id)} ⇒ 一个人对同一条评论只能举报一次</b>。
 * 重复举报由 {@code InsertReport} 的 {@code ON CONFLICT DO NOTHING}
 * （comment.go:243，<b>不带 Columns</b>）静默吞掉，接口仍返回 {@code success = true}。
 *
 * <p><b>reason 是 int 而不是 proto 枚举</b>：Go 侧 {@code Reason int32}
 * 来自 proto 的 {@code ReportReason}（0..99），但落库就是整数。
 * ⚠️ 该值<b>不做合法性校验</b>：proto3 的未知枚举值在 Java 生成物里会落到
 * {@code UNRECOGNIZED}，其 {@code getNumber()} 仍返回线上的原始数值 ——
 * 必须用 {@code getReasonValue()} 而不是 {@code getReason().getNumber()}，
 * 后者对 {@code UNRECOGNIZED} 会返回 {@code null} 并触发 NPE。
 *
 * <p><b>本表没有 updated_at</b>：Go data_model.go:63 只给了 {@code CreatedAt autoCreateTime}。
 */
@TableName("report_record")
public class ReportRecordEntity {

    /** 对应 Go {@code Id int64 `primaryKey;autoIncrement`}。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应 Go {@code UserId int64 `not null`} —— 举报人 ID。 */
    private long userId;

    /** 对应 Go {@code CommentId int64 `not null`} —— 被举报评论 ID。 */
    private long commentId;

    /** 对应 Go {@code TargetType string `type:varchar(32);not null`}。 */
    private String targetType;

    /** 对应 Go {@code TargetId string `type:varchar(64);not null`}。 */
    private String targetId;

    /** 对应 Go {@code Reason int32 `not null`} —— 举报原因枚举数值。 */
    private int reason;

    /** 对应 Go {@code Detail string `type:varchar(255)`} —— 举报详情（可空，<b>超 255 会被 PG 截断报错</b>）。 */
    private String detail;

    /** 对应 Go {@code CreatedAt time.Time `autoCreateTime`}。 */
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getCommentId() {
        return commentId;
    }

    public void setCommentId(long commentId) {
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

    public int getReason() {
        return reason;
    }

    public void setReason(int reason) {
        this.reason = reason;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}