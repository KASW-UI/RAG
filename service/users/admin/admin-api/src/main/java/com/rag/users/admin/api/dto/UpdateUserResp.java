package com.rag.users.admin.api.dto;

/**
 * admin updateUser 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 UpdateUserResp。
 *
 * 字段：Success、User。
 */
public class UpdateUserResp {
    private boolean success;
    private UserInfo user;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }
}
