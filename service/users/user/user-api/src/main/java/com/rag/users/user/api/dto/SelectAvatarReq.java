package com.rag.users.user.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * selectAvatar 用例的 HTTP 请求 DTO。
 * 对应 Go: service/user/user/api/internal/types/types.go 的 SelectAvatarReq。
 *
 * 字段：history_id。
 */
public class SelectAvatarReq {

    @JsonProperty("history_id")
    private String historyId;

    @JsonProperty("history_id")
    public String getHistoryId() { return historyId; }
    public void setHistoryId(String historyId) { this.historyId = historyId; }
}