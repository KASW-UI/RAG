package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

import java.util.Map;

/**
 * admin updateSelf 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 UpdateSelfReq。
 *
 * 字段：username / password / email <b>全部必填</b>；extra_info（map：<b>缺席</b>不校验、
 * <b>显式 null</b>报 mustn't be nil —— E-3 根因 R2）。uid 从 JWT claim 解析。
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
    @JsonProperty("extra_info")
    public Map<String, String> getExtraInfo() { return extraInfo; }
    @JsonProperty("extra_info")
    public void setExtraInfo(Map<String, String> extraInfo) {
        this.extraInfo = extraInfo;
        this.extraInfoPresent = true;   // key 出现过（含 null）
    }
}
