package com.rag.users.admin.api.dto;

/**
 * admin deleteUser 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 DeleteUserResp。
 *
 * 字段：Success。
 */
public class DeleteUserResp {
    private boolean success;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
