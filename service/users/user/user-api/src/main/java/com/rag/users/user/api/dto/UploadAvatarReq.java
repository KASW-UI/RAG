package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * uploadAvatar 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 UploadAvatarReq。
 *
 * 字段：avatar_url（必填）、content_type（可选）、size_bytes（可选）。
 */
public class UploadAvatarReq {

    @JsonProperty("avatar_url")
    private String avatarUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("content_type")
    private String contentType;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @JsonProperty("avatar_url")
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    @JsonProperty("content_type")
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    @JsonProperty("size_bytes")
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
}