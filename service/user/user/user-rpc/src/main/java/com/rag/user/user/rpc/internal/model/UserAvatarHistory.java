package com.rag.user.user.rpc.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 对应 Go: internal/model/data_model.go 中的 type UserAvatarHistory struct
 *
 * 字段对齐 Go 端的 gorm tag:
 *   Id          uint64    `gorm:"primaryKey"`                         → @Id @GeneratedValue
 *   HistoryID   string    `gorm:"column:history_id;uniqueIndex;..."`  → 业务唯一 ID,snowflake 转 string
 *   Uid         int64     `gorm:"column:uid;index;not null"`         → 加索引,查询某用户历史用
 *   AvatarURL   string    `gorm:"column:avatar_url;not null"`
 *   ContentType string    `gorm:"column:content_type"`
 *   SizeBytes   int64     `gorm:"column:size_bytes"`
 *   IsCurrent   bool      `gorm:"column:is_current;index;..."`        → 加索引,事务里要 where is_current=true
 *   create_time / update_time                                          → 继承自 DataModel 基类
 */
@Entity
@Table(
        name = "user_avatar_history",
        indexes = {
                @Index(name = "idx_uid", columnList = "uid"),
                @Index(name = "idx_is_current", columnList = "is_current")
        }
)
public class UserAvatarHistory extends DataModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 业务主键,由 snowflake 算法生成,Go 端对应 uploadavatarlogic 里的 historySnowflakeID */
    @Column(name = "history_id", unique = true, nullable = false, length = 64)
    private String historyId;

    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "avatar_url", nullable = false, length = 1024)
    private String avatarUrl;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    /**
     * 是否当前正在使用的头像。
     * 对应 Go 端事务逻辑:CreateAvatarHistory 把旧 is_current=true → false,新插入的设为 true
     */
    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    // —— getter / setter ——

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }

    public Long getUid() { return uid; }
    public void setUid(Long uid) { this.uid = uid; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public Boolean getIsCurrent() { return isCurrent; }
    public void setIsCurrent(Boolean isCurrent) { this.isCurrent = isCurrent; }
}