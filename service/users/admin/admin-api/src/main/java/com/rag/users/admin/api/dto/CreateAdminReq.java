package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.Map;

/**
 * admin createAdmin 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 CreateAdminReq。
 *
 * 字段：Username（必填）、Password（必填）、Email（必填）、InviteCode（必填）、extra_info（map，不校验）。
 *
 * <p><b>DRIFT-016</b>：email 与 invite_code 都是必填的，尽管 email 的 tag 写着
 * {@code ,omitempty} —— go-zero 的 {@code parseOption} 只认 {@code ,optional}
 * （{@code core/mapping/utils.go}）。实测 probe-go-required-fields.py：
 * drop email ⇒ {@code 400 field "email" is not set}；
 * drop invite_code ⇒ {@code 400 field "invite_code" is not set}。
 */
public class CreateAdminReq implements GoStyleRequiredFields {
    private String username;
    private String password;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    @JsonProperty("invite_code")
    private String inviteCode;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("extra_info")
    private Map<String, String> extraInfo;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("username", username);
        GoStyleRequired.field("password", password);
        GoStyleRequired.field("email", email);
        GoStyleRequired.field("invite_code", inviteCode);
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    @JsonProperty("invite_code")
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    @JsonProperty("extra_info")
    public Map<String, String> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
}
