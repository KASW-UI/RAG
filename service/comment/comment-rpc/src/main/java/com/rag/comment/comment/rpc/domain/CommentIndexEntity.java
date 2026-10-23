package com.rag.comment.comment.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 评论索引表 ORM 实体 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/data_model.go:22-40} 的 {@code CommentIndex}。
 *
 * <p>表结构：{@code V2__create_comment_index.sql}。
 *
 * <p><b>「索引表」= 统计与结构，与 {@link CommentContentEntity} 的正文分表存储。</b>
 * 之所以拆开：列表页要按 target/root 分页读统计，但只有命中的那几条才需要正文，
 * 拆开后 ListSize=N 时正文读取量是 N 而不是全表。
 *
 * <p><b>主键不自增</b>：Go 侧是 {@code primaryKey;autoIncrement:false}，值来自雪花算法
 * （create_comment_logic.go:93），并由 Kafka 审计消息携带落库。
 * ⇒ 用 {@link IdType#INPUT}：MyBatis-Plus 必须原样插入这个 ID，
 * 若用 ASSIGN_ID 会被重新生成一个 ⇒ 主键与审计链路里的 comment_id 脱钩。
 *
 * <p><b>state 取值域</b>（Go data_model.go:32 注释）：0 正常 / 1 审核中 / 2 已删除 / 3 已封禁。
 * ⚠️ 与 {@link SubjectEntity#getState()} 的 0/1/2（正常/关闭/仅粉丝）<b>不是同一套编码</b>。
 * 尤其：{@code state == 2} 在本表表示「已删除」，在 subject 表表示「仅粉丝可互动」。
 *
 * <p><b>attribute 位图</b>（Go comment.proto:77-79 注释）：
 * 第 0 位 = 作者赞过，第 1 位 = 作者回复过，第 2 位 = 置顶，第 3 位 = 精选。
 * 三个写点：InsertCommentTx 父评论（comment.go:128-130）、LikeCommentTx 作者点赞（:568-573）、
 * ManageCommentAttribute 置顶/精选（:157-182）。
 */
@TableName("comment_index")
public class CommentIndexEntity {

    /** 对应 Go {@code Id int64 `primaryKey;autoIncrement:false`} —— 雪花 ID，原样落库。 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 对应 Go {@code TargetType string `type:varchar(32);not null`}。 */
    private String targetType;

    /** 对应 Go {@code TargetId string `type:varchar(64);not null`}。 */
    private String targetId;

    /** 对应 Go {@code UserId int64 `not null`} —— 发布者 uid。 */
    private long userId;

    /** 对应 Go {@code RootId int64 `not null;default:0`} —— 根评论 ID；0 表示本条就是根评论。 */
    private long rootId;

    /** 对应 Go {@code ParentId int64 `not null;default:0`} —— 直接父评论 ID；0 表示根评论。 */
    private long parentId;

    /** 对应 Go {@code LikeCount int64 `not null;default:0`}。 */
    private long likeCount;

    /** 对应 Go {@code DislikeCount int64 `not null;default:0`}。 */
    private long dislikeCount;

    /** 对应 Go {@code ReplyCount int64 `not null;default:0`} —— 子评论数。 */
    private long replyCount;

    /** 对应 Go {@code State int32 `not null;default:0`}：0 正常 / 1 审核中 / 2 已删除 / 3 已封禁。 */
    private int state;

    /** 对应 Go {@code Attribute int64 `not null;default:0`} —— 属性位图。 */
    private long attribute;

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

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getRootId() {
        return rootId;
    }

    public void setRootId(long rootId) {
        this.rootId = rootId;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(long dislikeCount) {
        this.dislikeCount = dislikeCount;
    }

    public long getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(long replyCount) {
        this.replyCount = replyCount;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public long getAttribute() {
        return attribute;
    }

    public void setAttribute(long attribute) {
        this.attribute = attribute;
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