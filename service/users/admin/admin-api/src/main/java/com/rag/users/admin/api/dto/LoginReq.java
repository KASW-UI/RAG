package com.rag.users.admin.api.dto;

/**
 * admin login 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 LoginReq。
 *
 * 字段：Username、Password。
 */
public class LoginReq {
    private String username;
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
