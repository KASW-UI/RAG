package com.rag.users.admin.api.dto;

/**
 * admin updateSelf 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 UpdateSelfResp。
 *
 * 字段：Success、Admin。
 */
public class UpdateSelfResp {
    private boolean success;
    private AdminInfo admin;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public AdminInfo getAdmin() { return admin; }
    public void setAdmin(AdminInfo admin) { this.admin = admin; }
}
