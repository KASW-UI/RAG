package com.rag.comment.comment.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.rag.comment.comment.rpc.mapper.JsonbTypeHandler;

import java.time.OffsetDateTime;

/**
 * 评论正文表 ORM 实体 —— 对应 Go 端
 * {@code service/comment/rpc/internal/model/data_model.go:42-53} 的 {@code CommentContent}。
 *
 * <p>表结构：{@code V3__create_comment_content.sql}。
 *
 * <p><b>主键 comment_id 就是 {@link CommentIndexEntity#getId()} 本身</b>（Go data_model.go:43 注释
 * 「CommentId就是CommentIndex中的Id」），不是外键序列 ⇒ {@link IdType#INPUT}。
 *
 * <p><b>meta 是 jsonb</b>，因此本类是本域唯一需要 {@code autoResultMap = true} 的业务实体：
 * {@code @TableName} 不带这个开关时，MyBatis-Plus 生成的 resultMap 不会带上
 * {@code @TableField(typeHandler=…)}，读回的 jsonb 列会退化成 PGobject 的 toString()。
 *
 * <p><b>meta 为什么用 String 而不是 POJO</b>：Go 侧 normalizeCommentMeta（comment.go:222-240）
 * 的契约是「输入不是合法 JSON 就包成 {@code {"raw": <原文>}}」，合法则**原样保留**；
 * 而 GetComment 把 meta 直接塞进 {@code CommentItem.meta}（string 字段）上 wire。
 * 用 String 存取才能保证「原样保留」这条语义，且不被任何 POJO 往返改写。
 */
@TableName(value = "comment_content", autoResultMap = true)
public class CommentContentEntity {

    /** 对应 Go {@code CommentId int64 `primaryKey;autoIncrement:false`}。 */
    @TableId(type = IdType.INPUT)
    private Long commentId;

    /** 对应 Go {@code Content string `type:text;not null`} —— 评论正文。 */
    private String content;

    /** 对应 Go {@code Meta string `type:jsonb`} —— 扩展内容（可空）。 */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String meta;

    /** 对应 Go {@code CreatedAt time.Time `autoCreateTime`}。 */
    private OffsetDateTime createdAt;

    /** 对应 Go {@code UpdatedAt time.Time `autoUpdateTime`}。 */
    private OffsetDateTime updatedAt;

    public Long getCommentId() {
        return commentId;
    }

    public void setCommentId(Long commentId) {
        this.commentId = commentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getMeta() {
        return meta;
    }

    public void setMeta(String meta) {
        this.meta = meta;
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