package com.rag.users.user.rpc.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * users_user_avatar_history 表实体 —— MyBatis-Plus @TableName 风格。
 *
 * 对应 Go: service/user/model/user.go 的 UserAvatarHistory struct。
 *   ⚠️ 表名 users_user_avatar_history 与用户 2026-10-22 修改 HUMAN_ONLY.MD 后的版本一致。
 *
 * 框架职责:
 *   - 与 UserEntity 一致的 @TableName / @TableId / @TableField 注解套路
 *   - 取代了原 internal.model.UserAvatarHistory.java（JPA 风格）
 *   - historyId 是 Snowflake 生成的字符串 ID（D-013：服务端生成不接受请求传入）
 *   - isCurrent Boolean getter 用 getIsCurrent()，匹配 Go 端 UserAvatarHistory.IsCurrent 字段
 */
@TableName("users_user_avatar_history")
public class UserAvatarHistoryEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("history_id")
    private String historyId;

    @TableField("uid")
    private Long uid;

    @TableField("avatar_url")
    private String avatarUrl;

    @TableField("content_type")
    private String contentType;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("is_current")
    private Boolean isCurrent = false;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
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