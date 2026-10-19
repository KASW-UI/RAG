package com.rag.users.admin.api.dto;

/**
 * admin unBanUser 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 UnBanUserResp。
 *
 * 字段：Success。
 */
public class UnBanUserResp {
    private boolean success;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
}
