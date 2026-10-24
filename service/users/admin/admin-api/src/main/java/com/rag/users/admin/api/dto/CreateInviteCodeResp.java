package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * admin createInviteCode 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 CreateInviteResp。
 *
 * 字段：invite_code（"ADM-" + 16 hex 大写）、expires_at（unix 秒）。
 */
public class CreateInviteCodeResp {
    @JsonProperty("invite_code")
    private String inviteCode;
    @JsonProperty("expires_at")
    private long expiresAt;

    @JsonProperty("invite_code")
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    @JsonProperty("expires_at")
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
}
