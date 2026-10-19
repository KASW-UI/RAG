package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * admin getUser 用例的 HTTP 请求 DTO（GET query 参数）。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetUserReq。
 *
 * 字段：uid（form，json:"uid,string"）。
 */
public class GetUserReq {
    @JsonProperty("uid")
    private Long uid;

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
}
