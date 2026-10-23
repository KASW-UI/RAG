package com.rag.comment.comment.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 评论互动（赞/踩）记录 ORM 实体 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/data_model.go:87-101} 的 {@code CommentLike}。
 *
 * <p>表结构：{@code V4__create_comment_like.sql}。
 *
 * <p><b>唯一键 {@code (user_id, comment_id)} 是并发正确性的载体</b>，不是去重优化。
 * {@code LikeCommentTx}（comment.go:480-495）刻意先播一行 {@code state = 0} 的「锁锚」再
 * {@code SELECT … FOR UPDATE} 取它：
 * <pre>
 * seed := CommentLike{…State: 0}
 * tx.Clauses(clause.OnConflict{DoNothing: true}).Create(&amp;seed)   // 第一次来才插入
 * tx.Clauses(clause.Locking{Strength: "UPDATE"}).
 *    Where("user_id = ? AND comment_id = ?", …).First(&amp;likeRecord) // 锁住它算差量
 * </pre>
 * 注释原文：「A state-0 row is the lock anchor even for the first concurrent action.」
 * 没有这条唯一约束，两个并发首次点赞会各自插一行、算出两份差量、计数翻倍。
 *
 * <p><b>state 取值域 0/1/2（不是 1~4）</b>：0 无状态 / 1 已点赞 / 2 已点踩（data_model.go:93 注释）。
 * ⚠️ 消息与请求里的 {@code action_type} 用的是 {@code 1=like 2=unlike 3=dislike 4=undislike}，
 * <b>不是</b>本列的取值域 —— 两者由 {@code LikeCommentTx} 的 switch 转换（comment.go:516-549）。
 * 混淆这两套编码是本域最容易出错的地方。
 */
@TableName("comment_like")
public class CommentLikeEntity {

    /** 对应 Go {@code Id int64 `primaryKey;autoIncrement`}。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应 Go {@code UserId int64 `not null`} —— 互动者 uid。 */
    private long userId;

    /** 对应 Go {@code CommentId int64 `not null`} —— 被互动评论 ID。 */
    private long commentId;

    /** 对应 Go {@code TargetType string `type:varchar(32);not null`}。 */
    private String targetType;

    /** 对应 Go {@code TargetId string `type:varchar(64);not null`}。 */
    private String targetId;

    /** 对应 Go {@code State int32 `not null;default:0`}：0 无状态 / 1 已点赞 / 2 已点踩。 */
    private int state;

    /** 对应 Go {@code CreatedAt time.Time `autoCreateTime`}。 */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `autoUpdateTime`}。 */
    private OffsetDateTime updatedAt;

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

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}