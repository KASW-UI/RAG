package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.Map;

/**
 * admin createAdmin 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 CreateAdminReq。
 *
 * 字段：Username（必填）、Password（必填）、Email（必填）、InviteCode（必填）、
 * extra_info（map：<b>缺席</b>不校验、<b>显式 null</b>报 mustn't be nil —— E-3 根因 R2）。
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

    /**
     * {@code extra_info} 这个 key 是否<b>真的出现在 JSON body 里</b>。
     *
     * <p><b>为什么必须单独记一个标志</b>：Go 把「缺席」与「显式 null」当成两件不同的事
     * —— 缺席放行、显式 null 报 {@code field "extra_info" mustn't be nil}
     * （E-3 根因 R2，见 {@code GoStyleRequired#explicitNull} 的完整说明）。
     * 只看 {@code extraInfo == null} 会把两者混成同一个分支，从而<b>错杀缺席</b>。
     *
     * <p><b>presence 从哪来</b>：Jackson <b>只在字段真的出现在 JSON 里时才调 setter</b>，
     * 所以 setter 被调过 == 该 key 出现过。这是「不引入第二份解析器」的最省办法。
     */
    @JsonIgnore
    private boolean extraInfoPresent;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("username", username);
        GoStyleRequired.field("password", password);
        GoStyleRequired.field("email", email);
        GoStyleRequired.field("invite_code", inviteCode);
        // R2：map 字段不参与「缺席」判定，但**显式 null 必须报**
        // （go-zero unmarshaler.go:856-862 对 map 同样判 mustn't be nil）。
        GoStyleRequired.explicitNull("extra_info", extraInfoPresent, extraInfo);
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
    @JsonProperty("extra_info")
    public void setExtraInfo(Map<String, String> extraInfo) {
        this.extraInfo = extraInfo;
        this.extraInfoPresent = true;   // key 出现过（含 null）
    }
}
