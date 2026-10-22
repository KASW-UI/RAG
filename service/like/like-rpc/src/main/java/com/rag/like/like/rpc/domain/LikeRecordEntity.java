package com.rag.like.like.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 点赞记录表 ORM 实体 —— 对应 Go 端
 * {@code service/like/rpc/internal/model/data_model.go: LikeRecord}。
 *
 * <p>表结构：{@code V1__create_like_record.sql}。
 *
 * <p><b>⚠️ 故意不标 {@code @TableLogic}</b>：GORM 的 {@code gorm.DeletedAt} 会给**每一条**经由
 * {@code db.Model(&LikeRecord{})} 的查询自动加 {@code delete_at IS NULL}，
 * 而 {@code applyLikeMessage} 又用 {@code tx.Unscoped()} <b>显式绕开</b>这个过滤去读已软删的行
 * （{@code model/like.go:195}），随后还要靠 {@code current.DeleteAt.Valid} 判定并<b>报错</b>。
 * 这两条语义相反，用 {@code @TableLogic} 的隐式行为表达会得到一个既不是 GORM 也不是本模块的第三种东西。
 * 本实现改为：所有需要过滤的查询在 mapper 里<b>显式写</b> {@code delete_at IS NULL}，
 * 需要绕过的那条单独一条 SQL，两处一眼可见。
 *
 * <p>主键：Go 侧 GORM {@code autoIncrement}；DDL 用 BIGSERIAL。Java 侧因此
 * <b>不用</b> {@code IdType.ASSIGN_ID} —— 插入时让 PG 分配，行为与 Go 一致。
 */
@TableName("like_record")
public class LikeRecordEntity {

    /** 对应 Go {@code ID int64 `primaryKey;autoIncrement`}。由 PG 序列分配。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应 Go {@code UserID int64 `not null`}（点赞者）。 */
    private long userId;

    /** 对应 Go {@code TargetType string `varchar(32);not null`}。 */
    private String targetType;

    /** 对应 Go {@code TargetID string `varchar(64);not null`}。 */
    private String targetId;

    /** 对应 Go {@code AuthorID int64 `not null`}（被点赞内容作者）。 */
    private long authorId;

    /**
     * 对应 Go {@code State int32 `smallint;not null;default:0`}。
     *
     * <p>取值域见 {@code LikeStates}：0 无 / 1 已点赞 / 2 已点踩。
     * ⚠️ 消费消息里的 {@code action} 用的是 1~4（like/unlike/dislike/undislike），
     * <b>不是</b>本字段的取值域 —— 两者由 {@code applyLikeMessage} 的 switch 转换，
     * 混淆这两套编码是本域最容易出错的地方。
     */
    private int state;

    /** 对应 Go {@code LastOperationID int64 `column:last_operation_id;not null;default:0`}。 */
    private long lastOperationId;

    /** 对应 Go {@code CreatedAt time.Time `autoCreateTime`}。 */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `autoUpdateTime`}。 */
    private OffsetDateTime updatedAt;

    /** 对应 Go {@code DeleteAt gorm.DeletedAt `index`}（软删除时间，可空）。 */
    private OffsetDateTime deleteAt;

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

    public long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(long authorId) {
        this.authorId = authorId;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getLastOperationId() {
        return lastOperationId;
    }

    public void setLastOperationId(long lastOperationId) {
        this.lastOperationId = lastOperationId;
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

    public OffsetDateTime getDeleteAt() {
        return deleteAt;
    }

    public void setDeleteAt(OffsetDateTime deleteAt) {
        this.deleteAt = deleteAt;
    }
}