package com.rag.users.admin.api.dto;

/**
 * admin banUser 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 BanUserResp。
 *
 * 字段：Success。
 */
public class BanUserResp {
    private boolean success;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
