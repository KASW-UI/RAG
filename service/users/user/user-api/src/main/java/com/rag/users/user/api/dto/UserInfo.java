package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 用户信息 DTO —— 4 字段（uid/username/email/extra_info）。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 UserInfo。
 *
 * 复用：被 GetUserResp / UpdateUserResp 嵌套。
 *
 * uid 序列化为字符串（与 Go json:"uid,string" 1:1）。
 * score 字段不返回：Go 端 UserInfo 包含但 HTTP 层 GetUser/UpdateUserResp 只透传 4 字段。
 */
public class UserInfo {

    @JsonProperty("uid")
    private Long uid;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long score;
    private String username;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("extra_info")
    private Map<String, String> extraInfo;

    /** 序列化为字符串（与 Go json:"uid,string" 对齐）。 */
    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
    public Long getScore() { return score; }
    public void setScore(Long score) { this.score = score; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    @JsonProperty("extra_info")
    public Map<String, String> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
}