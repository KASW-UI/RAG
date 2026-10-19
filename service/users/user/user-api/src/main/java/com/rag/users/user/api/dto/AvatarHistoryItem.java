package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 头像历史项 DTO —— 6 字段（id/avatar_url/content_type/size_bytes/is_current/create_time）。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 AvatarHistoryItem。
 *
 * 复用：被 SelectAvatarResp / UploadAvatarResp / GetAvatarHistoryResp 嵌套。
 *
 * L1 wire 契约冻结：所有字段 JSON 名用 snake_case。
 * Jackson 默认按 bean getter 推断属性名（isCurrent() → current，sizeBytes() → sizeBytes），
 * 必须显式 @JsonProperty 才能输出 is_current / size_bytes。修 U-010 / U-011。
 */
public class AvatarHistoryItem {

    @JsonProperty("id")
    private String id;
    @JsonProperty("avatar_url")
    private String avatarUrl;
    @JsonProperty("content_type")
    private String contentType;
    @JsonProperty("size_bytes")
    private long sizeBytes;
    @JsonProperty("is_current")
    private boolean isCurrent;
    @JsonProperty("create_time")
    private String createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    @JsonProperty("size_bytes")
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    @JsonProperty("is_current")
    public boolean isCurrent() { return isCurrent; }
    public void setCurrent(boolean current) { isCurrent = current; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
}