package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * uploadAvatar 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 UploadAvatarReq。
 *
 * 字段：avatar_url（必填）、content_type（optional）、size_bytes（optional）。
 *
 * <p><b>DRIFT-016</b>：Go 出处 types.go {@code UploadAvatarReq} 只有
 * {@code AvatarUrl} 无 tag，其余两个都是 {@code ,optional}。
 * 实测 probe-go-required-fields.py：drop avatar_url ⇒
 * {@code 400 field "avatar_url" is not set}；drop content_type / drop size_bytes ⇒ 200。
 */
public class UploadAvatarReq implements GoStyleRequiredFields {

    @JsonProperty("avatar_url")
    private String avatarUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("content_type")
    private String contentType;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("size_bytes")
    private Long sizeBytes;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("avatar_url", avatarUrl);
    }

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