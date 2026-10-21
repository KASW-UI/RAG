package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * admin getUser 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 GetUserReq。
 *
 * <p><b>wire 契约偏离</b>（AGENTS.md §3.4.1）：Go 是 {@code GET} + query 参数、
 * tag 为 {@code form:"uid,string"}；RAG 统一 {@code POST} + JSON body（tag 名仍是 uid）。
 *
 * <p><b>DRIFT-016</b>：uid 必填 —— 实测三态（probe-go-required-fields.py，2026-10-21）：
 * <pre>
 *   完全不传 uid → 400  field "uid" is not set
 *   uid=abc      → 400  strconv.ParseInt: parsing "abc": invalid syntax
 *   uid=0        → 200  {"code":1003,"msg":"用户不存在"}
 * </pre>
 * 前两条现在与 Go 一致；<b>第三条是用户已签字的 N-3 有意偏离</b>，RAG 返
 * {@code 1017 请求参数错误} 而非 Go 的 {@code 1003 用户不存在}。
 *
 * 字段：uid（必填）。
 */
public class GetUserReq implements GoStyleRequiredFields {
    @JsonProperty("uid")
    private Long uid;

    @Override
    public void assertGoRequiredFields() {
        // Go 出处：types.go GetUserReq.Uid 的 tag 是 `form:"uid,string"`，不含 ,optional
        // ⇒ int64 标量必填（form 通道，缺席 ⇒ unmarshaler.go newInitError）
        GoStyleRequired.field("uid", uid);
    }

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
}
