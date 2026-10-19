package com.rag.users.user.api.dto;

/**
 * updateUser 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 UpdateUserResp。
 *
 * 字段：user（4 字段：uid/username/email/extra_info）。
 */
public class UpdateUserResp {

    private UserInfo user;

    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}