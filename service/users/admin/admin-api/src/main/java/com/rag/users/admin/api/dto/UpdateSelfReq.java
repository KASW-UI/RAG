package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.Map;

/**
 * admin updateSelf 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 UpdateSelfReq。
 *
 * 字段：username / password / email <b>全部必填</b>；extra_info（map，不校验）。uid 从 JWT claim 解析。
 *
 * <p><b>⚠️ DRIFT-016 · 本 DTO 三个 string 全是必填，尽管 tag 都写着 {@code ,omitempty}</b>。
 * 这是本批最反直觉的一条：{@code ,omitempty} 在 go-zero 里<b>不是</b> {@code ,optional}，
 * {@code core/mapping/utils.go} 的 {@code parseOption} 静默忽略它，
 * 于是 {@code UpdateSelfReq} 的 username/password/email 全部落进标量必填分支。
 *
 * <p>实测 probe-go-required-fields.py：drop username / drop password / drop email
 * 三者都返回 {@code 400 field "xxx" is not set}。
 *
 * <p><b>语义后果</b>：Go 端这个「更新自己」的接口<b>无法做部分更新</b> ——
 * 只想改密码就必须把 username 与 email 一起重发。RAG 照搬该行为（§1「Go 是唯一真源」），
 * 不因为「更符合直觉」而放宽容忍度。
 */
public class UpdateSelfReq implements GoStyleRequiredFields {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String username;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String password;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("extra_info")
    private Map<String, String> extraInfo;

    @Override
    public void assertGoRequiredFields() {
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
