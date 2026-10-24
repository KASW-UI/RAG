package com.rag.users.admin.api.dto;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * admin login 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 LoginReq。
 *
 * 字段：Username（必填）、Password（必填）。
 *
 * <p><b>DRIFT-016</b>：Go 出处 types.go {@code LoginReq} 两个字段都无 tag ⇒ 标量必填。
 */
public class LoginReq implements GoStyleRequiredFields {
    private String username;
    private String password;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("username", username);
        GoStyleRequired.field("password", password);
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
