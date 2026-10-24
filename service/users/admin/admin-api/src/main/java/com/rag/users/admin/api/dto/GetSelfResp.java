package com.rag.users.admin.api.dto;

/**
 * admin getSelf 用例的 HTTP 响应 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetSelfResp。
 *
 * 字段：Admin（嵌套 AdminInfo）。
 */
public class GetSelfResp {
    private AdminInfo admin;

    public AdminInfo getAdmin() { return admin; }
    public void setAdmin(AdminInfo admin) { this.admin = admin; }
}
