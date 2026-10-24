package com.rag.follow.follow.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 关注关系表 ORM 实体 —— 对应 Go 端
 * {@code service/follow/rpc/internal/model/data_model.go:5-15} 的 {@code FollowRelation}。
 *
 * <p>表结构：{@code V1__create_follow_relation.sql}。
 *
 * <p><b>主键</b>：Go 侧 {@code ID uint64 `gorm:"primaryKey"`} 未显式关闭 autoIncrement，
 * 而 GORM 对整数主键<b>默认开启</b> ⇒ 产物是 {@code bigserial}。
 * Java 侧因此用 {@link IdType#AUTO}，插入时让 PG 序列分配。
 * ⚠️ 若改成 {@code IdType.ASSIGN_ID}（像 users 域那样）就变成雪花 ID ——
 * 那是新增行为，且与 Go 侧序列产生的主键空间不同。
 *
 * <p><b>⚠️ createTime / updateTime 由业务代码显式赋值，不由框架填充</b>：
 * Go 侧的 {@code autoCreateTime} / {@code autoUpdateTime} 是 <b>GORM struct tag</b>，
 * GORM 在 INSERT 前把当时的时刻写进 struct 再落库 —— 时间戳<b>由 Go 进程产生</b>，
 * 不是 DB 的 {@code DEFAULT now()}。
 * Java 侧若用 MyBatis-Plus 的 {@code MetaObjectHandler} 自动填充，效果等价但多一层隐式全局行为；
 * 这里选择由 {@code FollowRelationMapper} 的调用点显式 set，语义一眼可见。
 * ⚠️ 不要给这两列加 {@code DEFAULT CURRENT_TIMESTAMP}：那会把时间来源从应用进程换成 DB，
 * 时区与时钟都跟着变（follow_relation 是多副本部署）。
 *
 * <p><b>时间类型选 {@link OffsetDateTime} 而非 {@code LocalDateTime}</b>：
 * DDL 是 {@code TIMESTAMPTZ}（GORM 对 Go {@code time.Time} 的映射），
 * 带时区语义；{@code LocalDateTime} 会丢掉 offset，读出值随 JVM 默认时区漂移。
 */
@TableName("follow_relation")
public class FollowRelationEntity {

    /** 对应 Go {@code ID uint64 `gorm:"primaryKey"`} ⇒ BIGSERIAL，由 PG 序列分配。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对应 Go {@code UserId int64 `gorm:"column:user_id;not null;index;uniqueIndex:uk_follow_pair"`}（关注者）。 */
    private long userId;

    /** 对应 Go {@code TargetId int64 `column:target_id;not null;index;uniqueIndex:uk_follow_pair"`}（被关注者）。 */
    private long targetId;

    /** 对应 Go {@code CreateTime time.Time `column:create_time;autoCreateTime`}。列表排序键（{@code create_time desc}）。 */
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