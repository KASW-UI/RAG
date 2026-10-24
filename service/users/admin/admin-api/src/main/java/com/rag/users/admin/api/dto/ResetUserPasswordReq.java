package com.rag.users.admin.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * admin resetUserPassword 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/admin/api/internal/types/types.go 的 ResetUserPasswordReq。
 *
 * <p><b>DRIFT-016</b>：Go 出处 types.go {@code ResetUserPasswordReq.Uid} 的 tag 是
 * {@code `json:"uid,string"`} —— <b>是 json 通道、不是 form</b>（全 13 个 admin Req struct 里
 * 只有 {@code GetUserReq.Uid} 与 {@code GetUserListReq} 用 form:）。实测 drop uid ⇒
 * {@code 400 field "uid" is not set}。
 *
 * 字段：uid；operator_id 从 JWT claim 解析。
 */
public class ResetUserPasswordReq implements GoStyleRequiredFields {
    @JsonProperty("uid")
    private Long uid;

    @Override
    public void assertGoRequiredFields() {
        // Go 出处：types.go ResetUserPasswordReq.Uid 的 tag 是 `json:"uid,string"`，不含 ,optional
        // ⇒ int64 标量必填。实测 probe-go-required-fields.py：drop uid ⇒ 400 field "uid" is not set
        GoStyleRequired.field("uid", uid);
    }

    public String getUid() {
        return uid == null ? null : uid.toString();
    }
    public void setUid(Long uid) { this.uid = uid; }
}
