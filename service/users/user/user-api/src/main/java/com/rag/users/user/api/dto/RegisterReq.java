package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.Map;

/**
 * register 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 CreateUserReq。
 *
 * 字段：Username（必填）、Password（必填）、Email（必填）、extra_info（map，永不校验）。
 *
 * <p><b>DRIFT-016 · email 是必填的，尽管 tag 写着 {@code ,omitempty}</b>：
 * go-zero 的 {@code parseOption} 只认 {@code ,optional}，{@code ,omitempty} 不在选项表里、
 * 被静默忽略（{@code core/mapping/utils.go}）。实测
 * {@code tools/difftest/probe-go-required-fields.py}：
 * {@code user.register} drop email ⇒ {@code 400 field "email" is not set}。
 *
 * <p><b>extra_info 不校验，尽管它同样只写了 {@code ,omitempty}</b>：必填规则只作用于标量。
 * {@code unmarshaler.go:944-948} 的 {@code Array/Map/Slice} 分支走
 * {@code processFieldNotFromString}、<b>不报错</b>。实测 drop extra_info ⇒ 200。
 */
public class RegisterReq implements GoStyleRequiredFields {

    private String username;
    private String password;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("extra_info")
    private Map<String, String> extraInfo;

    @Override
    public void assertGoRequiredFields() {
        // Go 出处：types.go CreateUserReq — Username/Password 无 tag、Email 是 ,omitempty，
        // 三者都不含 ,optional ⇒ 全是标量必填。Extrainfo 是 map ⇒ 不校验。
        GoStyleRequired.field("username", username);
        GoStyleRequired.field("password", password);
        GoStyleRequired.field("email", email);
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    @JsonProperty("extra_info")
    public Map<String, String> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
}