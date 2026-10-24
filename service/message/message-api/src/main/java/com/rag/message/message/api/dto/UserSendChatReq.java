package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;



/**
 * 用户发私信请求 —— 对应 Go {@code types.go} 的 {@code UserSendChatReq}。
 *
 * <pre>
 * Go:
 *   type UserSendChatReq struct {
 *       RecipientId    int64  `json:"recipient_id,string,optional"`
 *       ConversationId int64  `json:"conversation_id,string,optional"`
 *       Content        string `json:"content"`
 *   }
 * </pre>
 *
 * <p><b>只有 {@code content} 必填</b>：两个 id 都是 {@code ,optional}。
 * 这<b>不是</b>「两个 id 可以同时为空」—— 空 id 的组合语义由 message-rpc 判定
 * （Go {@code rpc/internal/logic/sendchatmessagelogic.go}：
 * {@code conversation_id == 0 && recipient_id == 0} 报 INVALID_ARGUMENT，
 * 自发私信报 SELF_CHAT，首条消息超长报 FIRST_MSG_TOO_LONG 等）。
 * api 层<b>不</b>提前校验这三条，否则会与 rpc 的判定重复且可能不一致。
 *
 * <p>{@code content} 是 json 通道的 String：<b>空串是合法值</b>
 * （Go {@code unmarshaler.go} 的 {@code processFieldPrimitive} 对 String 直接写入，
 * 只判 null 不判空串）。内容长度/空内容是否拒绝由 message-rpc 判定。
 *
 * <p>使用方：{@code SendUserChatMessage}（sender_role=USER / recipient_role=USER）。
 */
public class UserSendChatReq implements GoStyleRequiredFields {

    private Long recipientId;
    private Long conversationId;
    private String content;

    /** {@code recipient_id}：可选；JSON 字符串与整数<b>都接受</b>（Go 语义，见 {@link GoStyleStringInt64}）。 */
    @JsonProperty("recipient_id")
    public void setRecipientId(Object raw) {
        this.recipientId = GoStyleStringInt64.parse(raw, "recipient_id");
    }

    /** {@code conversation_id}：可选；JSON 字符串与整数<b>都接受</b>（Go 语义，见 {@link GoStyleStringInt64}）。 */
    @JsonProperty("conversation_id")
    public void setConversationId(Object raw) {
        this.conversationId = GoStyleStringInt64.parse(raw, "conversation_id");
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("content", content);
    }

    /** 缺席或显式 null 时为 {@code null}；空串是合法值。 */
    public Long recipientId() {
        return recipientId;
    }

    public Long conversationId() {
        return conversationId;
    }

    public String content() {
        return content;
    }
}