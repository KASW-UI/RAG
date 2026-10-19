package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * uploadAvatar 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 UploadAvatarResp。
 *
 * 字段：avatar_url、history（AvatarHistoryItem）。
 */
public class UploadAvatarResp {

    @JsonProperty("avatar_url")
    private String avatarUrl;
    private AvatarHistoryItem history;

    @JsonProperty("avatar_url")
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public AvatarHistoryItem getHistory() { return history; }
    public void setHistory(AvatarHistoryItem history) { this.history = history; }
}