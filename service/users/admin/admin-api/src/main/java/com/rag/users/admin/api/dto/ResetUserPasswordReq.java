package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * admin resetUserPassword 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 ResetUserPasswordReq。
 *
 * 字段：uid；operator_id 从 JWT claim 解析。
 */
public class ResetUserPasswordReq {
    @JsonProperty("uid")
    private Long uid;

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
}
