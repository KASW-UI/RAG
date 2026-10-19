package com.rag.users.admin.api.dto;

/**
 * admin login 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 LoginResp。
 *
 * 字段：token（HS256 JWT）。
 */
public class LoginResp {
    private String token;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
