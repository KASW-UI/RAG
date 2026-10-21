package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.Map;

/**
 * admin updateUser 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 UpdateUserReq。
 *
 * <p><b>⚠️ DRIFT-016 · uid 必填，但 username / password / email 也全必填</b>。
 * Go 出处 types.go：{@code Uid} 的 tag 无 {@code ,optional}，
 * 而 username/password/email 写的是 {@code ,omitempty} —— <b>那不是 {@code ,optional}</b>，
 * 于是这四个标量字段全部落进 go-zero 的必填分支。
 * 实测 probe-go-required-fields.py：drop uid ⇒ {@code 400 field "uid" is not set}。
 *
 * <p>语义后果：Go 端这个「更新某个用户」的接口<b>也无法做部分更新</b>，且<b>不传 uid 就直接 400</b>。
 * 注意这与 user-api 侧的 {@code user.update}（全 {@code ,optional}、支持部分更新）**恰好相反** ——
 * 两端用的是同名结构但 tag 不同，不要跨模块套用结论（AGENTS.md §3.4.3 N-3 的同款警告）。
 *
 * 字段：uid（必填）；username/password/email（必填）；extra_info（map，不校验）；
 * operator_id 从 JWT claim 解析。
 */
public class UpdateUserReq implements GoStyleRequiredFields {
    @JsonProperty("uid")
    private Long uid;
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
        GoStyleRequired.field("uid", uid);
        GoStyleRequired.field("username", username);
        GoStyleRequired.field("password", password);
        GoStyleRequired.field("email", email);
    }

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }

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
