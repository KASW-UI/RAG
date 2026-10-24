package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * register 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 CreateUserResp。
 *
 * 字段：uid —— JSON 序列化为字符串（Snowflake UID 可能超过 2^53，前端 number 精度丢失）。
 */
public class RegisterResp {

    @JsonProperty("uid")
    private Long uid;

    /** 序列化为字符串（与 Go json:"uid,string" 1:1）。 */
    public String getUid() {
        return uid == null ? null : uid.toString();
    }

    public void setUid(Long uid) { this.uid = uid; }
}