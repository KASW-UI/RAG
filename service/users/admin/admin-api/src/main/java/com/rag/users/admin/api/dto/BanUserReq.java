package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * admin banUser 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 BanUserReq。
 *
 * 字段：uid（必填）；operator_id 从 JWT claim 解析（不在 body）。
 */
public class BanUserReq {
    @JsonProperty("uid")
    private Long uid;

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
}
