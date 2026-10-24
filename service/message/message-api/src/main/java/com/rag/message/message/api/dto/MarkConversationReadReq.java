package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;



/**
 * 标记会话已读请求 —— 对应 Go {@code types.go} 的 {@code MarkConversationReadReq}。
 *
 * <pre>
 * Go:
 *   type MarkConversationReadReq struct {
 *       ConversationId int64 `json:"conversation_id,string"`   // 必填 + ,string
 *   }
 * </pre>
 *
 * <p>{@code ,string} 与必填的语义见 {@link ConversationMessagesReq} 的类注释（同一套机制）。
 *
 * <p>使用方：{@code MarkConversationRead}（user，operator_role=USER）与
 * {@code MarkAdminConversationRead}（admin，operator_role=ADMIN）。
 * 两个端点共用本 DTO —— Go 侧也是同一个 {@code MarkConversationReadReq} 被两个 handler 用。
 */
public class MarkConversationReadReq implements GoStyleRequiredFields {

    private Long conversationId;

    /** {@code conversation_id} 的唯一反序列化入口；JSON 字符串与整数<b>都接受</b>（Go 语义，见 {@link GoStyleStringInt64}）。 */
    @JsonProperty("conversation_id")
    public void setConversationId(Object raw) {
        this.conversationId = GoStyleStringInt64.parse(raw, "conversation_id");
    }

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("conversation_id", conversationId);
    }

    public Long conversationId() {
        return conversationId;
    }
}