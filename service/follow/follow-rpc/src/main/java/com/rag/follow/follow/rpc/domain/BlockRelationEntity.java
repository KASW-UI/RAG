package com.rag.follow.follow.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 拉黑关系表 ORM 实体 —— 对应 Go 端
 * {@code service/follow/rpc/internal/model/data_model.go:17-27} 的 {@code BlockRelation}。
 *
 * <p>表结构：{@code V2__create_block_relation.sql}。
 *
 * <p>与 {@link FollowRelationEntity} <b>结构同构</b>，唯一差别在唯一索引名
 * （Go 侧分别是 {@code uniqueIndex:uk_follow_pair} / {@code uniqueIndex:uk_block_pair}）。
 * 两张表刻意<b>不复用</b>同一个 Entity：Go 侧也是两个 struct + 两个 TableName()，
 * 而共享一个类会让 {@code @TableName} 只能写一个 —— 那正是把 Go 的结构照搬进 Java 的典型错误。
 *
 * <p>主键 / 时间列 / 时间戳来源的语义与 {@link FollowRelationEntity} 完全一致，见该类注释。
 */
@TableName("block_relation")
public class BlockRelationEntity {

    /** 对应 Go {@code ID uint64 `gorm:"primaryKey"`} ⇒ BIGSERIAL。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应 Go {@code UserId int64 `column:user_id;not null;index;uniqueIndex:uk_block_pair"`}（拉黑者）。 */
    private long userId;

    /** 对应 Go {@code TargetId int64 `column:target_id;not null;index;uniqueIndex:uk_block_pair"`}（被拉黑者）。 */
    private long targetId;

    /** 对应 Go {@code CreateTime time.Time `column:create_time;autoCreateTime`}。 */
    private OffsetDateTime createTime;

    /** 对应 Go {@code UpdateTime time.Time `column:update_time;autoUpdateTime`}。 */
    private OffsetDateTime updateTime;

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

    public long getTargetId() {
        return targetId;
    }

    public void setTargetId(long targetId) {
        this.targetId = targetId;
    }

    public OffsetDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(OffsetDateTime createTime) {
        this.createTime = createTime;
    }

    public OffsetDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(OffsetDateTime updateTime) {
        this.updateTime = updateTime;
    }
}