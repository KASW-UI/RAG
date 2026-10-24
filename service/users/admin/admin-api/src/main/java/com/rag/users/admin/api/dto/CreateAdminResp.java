package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * admin createAdmin 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 CreateAdminResp。
 *
 * 字段：uid —— JSON 序列化为字符串（Snowflake UID）。
 */
public class CreateAdminResp {
    @JsonProperty("uid")
    private Long uid;

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
}
