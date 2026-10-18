package com.rag.users.user.rpc.internal.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 对应 Go 端 internal/model/user.go 的 UserAvatarHistory struct（GORM 表 user_avatar_history）。
 *
 * 表结构（与 Go 端 AutoMigrate 一致）：
 *   id           BIGSERIAL PRIMARY KEY
 *   history_id   VARCHAR NOT NULL UNIQUE  -- snowflake 生成的字符串 ID
 *   uid          BIGINT NOT NULL           -- 索引（Go gorm:"column:uid;index;not null"）
 *   avatar_url   VARCHAR NOT NULL
 *   content_type VARCHAR
 *   size_bytes   BIGINT
 *   is_current   BOOLEAN NOT NULL DEFAULT FALSE -- 索引（Go gorm:"column:is_current;index;not null;default:false"）
 *   create_time  TIMESTAMP
 *   update_time  TIMESTAMP
 */
@Entity
@Table(name = "user_avatar_history")
public class UserAvatarHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "history_id", nullable = false, unique = true)
    private String historyId;

    @Column(name = "uid", nullable = false)
    private Long uid;

    @Column(name = "avatar_url", nullable = false)
    private String avatarUrl;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = false;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

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

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}