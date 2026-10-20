package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 用户信息 DTO —— 对应 Go: service/user/user/api/internal/types/types.go 的 UserInfo。
 *
 * 复用：被 GetUserResp / UpdateUserResp 嵌套。
 *
 * uid 序列化为字符串（与 Go json:"uid,string" 1:1）。
 *
 * <p><b>score 必须无条件输出</b>（DRIFT-005 修复）：Go 侧
 * {@code types.UserInfo.Score uint32 \`json:"score"\`} <b>没有 omitempty</b>，
 * 而 {@code getuserlogic.go} / {@code updateuserlogic.go} 构造 UserInfo 时都不给 Score 赋值
 * ⇒ Go 的响应恒含 {@code "score":0}。此前本字段带 {@code @JsonInclude(NON_NULL)} 且从不赋值
 * ⇒ 整个 key 消失，客户端读 {@code resp.data.user.score} 拿到 {@code undefined} 而非 {@code 0}。
 *
 * <p><b>email / extra_info 相反</b>：Go 侧是 {@code json:"...,omitempty"}，
 * 空值即省略 ⇒ 本侧保持 {@code NON_NULL} 且 Service 层只在非空时 set。
 */
public class UserInfo {

    @JsonProperty("uid")
    private Long uid;
    /**
     * 对齐 Go：{@code json:"score"} 无 omitempty ⇒ 恒输出（未赋值时为 0）。
     * 刻意<b>不</b>加 {@code @JsonInclude(NON_NULL)}。
     */
    private Long score;
    private String username;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("extra_info")
    private Map<String, String> extraInfo;

    /**
     * 序列化为字符串（与 Go json:"uid,string" 对齐）。
     * <p>未赋值时返回 {@code null}：Go 的 {@code json:"uid,string"} 对 0 会输出 {@code "0"}，
     * 但 uid 恒由 Snowflake 生成、无 0 值，实际不可达。
     */
    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
    /**
     * score —— <b>恒输出，且未赋值时输出 {@code 0}</b>。
     *
     * <p>Go 出处 {@code types.UserInfo.Score uint32 `json:"score"`}（无 omitempty）：
     * uint32 的零值就是 {@code 0}，所以 Go 的响应<b>永远</b>是 {@code "score":0}。
     * 这里刻意用 {@code 0L} 兜底而不是让 Jackson 输出 {@code null} ——
     * {@code null} 与 Go 的 {@code 0} 不是一回事（客户端 {@code score === 0} 的判断会失效）。
     */
    public Long getScore() { return score == null ? 0L : score; }
    public void setScore(Long score) { this.score = score; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    @JsonProperty("extra_info")
    public Map<String, String> getExtraInfo() { return extraInfo; }
    public void setExtraInfo(Map<String, String> extraInfo) { this.extraInfo = extraInfo; }
}