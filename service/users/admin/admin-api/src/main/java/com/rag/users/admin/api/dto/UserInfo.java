package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 用户信息 DTO —— 5 字段（uid/status/username/email/extra_info）。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 UserInfo。
 *
 * 复用：被 GetUserResp / UpdateUserResp / GetUserListResp 嵌套。
 *
 * uid 序列化为字符串（与 Go json:"uid,string" 1:1）。
 */
public class UserInfo {

    @JsonProperty("uid")
    private Long uid;
    private long status;
    private String username;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("extra_info")
    private Map<String, String> extraInfo;

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }

    public long getStatus() { return status; }
    public void setStatus(long status) { this.status = status; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @JsonProperty("extra_info")
    public Map<String, String> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
}
