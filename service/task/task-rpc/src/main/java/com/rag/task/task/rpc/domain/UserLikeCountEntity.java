package com.rag.task.task.rpc.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 用户点赞总数 —— 对应 Go 端 {@code internal/Model/taskModel.go:43-50} 的 {@code UserLikeCount}
 * （以及 {@code internal/sink/like_sink.go:17-25} 的同表结构体，两者是同一张表）。
 *
 * <p>⚠️ <b>{@code userId} 在本表是 String / TEXT，在 {@link TaskProgressEntity} 里是 Long / BIGINT。</b>
 * 这是 Go 的现状（{@code UserLikeCount.UserID string} vs {@code TaskProgress.UserID int64}），
 * 不要"顺手统一"：统一会让 Go 侧写入的行在 Java 侧查不出来（或反之）。
 *
 * <p>写入语义是<b>只增不减</b>：{@code like_sink.go} 的 upsertChunk 用
 * {@code GREATEST(user_like_count.like_count, EXCLUDED.like_count)}，
 * 因此乱序重投不会把计数写小。
 *
 * <p>⚠️ task-rpc <b>自己不读本表</b>，只写。
 */
@TableName("user_like_count")
public class UserLikeCountEntity {

    /** Go {@code UserID string} ⇒ <b>TEXT</b>（与 task_progress.user_id 的 BIGINT 不同）。 */
    @TableId(value = "user_id")
    private String userId;

    /** Go {@code LikeCount int64} ⇒ BIGINT。 */
    private Long likeCount;

    /** Go {@code UpdatedAt time.Time `gorm:"column:updated_at;autoUpdateTime"`} ⇒ TIMESTAMPTZ。 */
    private OffsetDateTime updatedAt;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Long likeCount) {
        this.likeCount = likeCount;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}