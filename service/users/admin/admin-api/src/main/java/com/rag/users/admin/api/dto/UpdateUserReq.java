package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 * 字段：uid（必填）；username/password/email（必填）；
 * extra_info（map：<b>缺席</b>不校验、<b>显式 null</b>报 mustn't be nil —— E-3 根因 R2）；
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
        GoStyleRequired.field("uid", uid);
        GoStyleRequired.field("username", username);
        GoStyleRequired.field("password", password);
        GoStyleRequired.field("email", email);
        // R2：map 字段不参与「缺席」判定，但**显式 null 必须报**
        // （go-zero unmarshaler.go:856-862 对 map 同样判 mustn't be nil）。
        GoStyleRequired.explicitNull("extra_info", extraInfoPresent, extraInfo);
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
    @JsonProperty("extra_info")
    public void setExtraInfo(Map<String, String> extraInfo) {
        this.extraInfo = extraInfo;
        this.extraInfoPresent = true;   // key 出现过（含 null）
    }
}
