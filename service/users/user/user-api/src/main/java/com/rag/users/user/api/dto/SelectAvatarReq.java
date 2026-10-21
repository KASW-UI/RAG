package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * selectAvatar 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 SelectAvatarReq。
 *
 * 字段：history_id（必填）。
 *
 * <p><b>DRIFT-016</b>：Go 出处 types.go {@code SelectAvatarReq.HistoryId} 无 tag ⇒ 标量必填。
 * 实测 probe-go-required-fields.py：drop history_id ⇒ {@code 400 field "history_id" is not set}。
 */
public class SelectAvatarReq implements GoStyleRequiredFields {

    @JsonProperty("history_id")
    private String historyId;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("history_id", historyId);
    }

    @JsonProperty("history_id")
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
}