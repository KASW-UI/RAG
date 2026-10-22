package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;




/**
 * 发私信响应 —— 对应 Go {@code types.go} 的 {@code SendChatResp}。
 *
 * <pre>
 * Go:
 *   type SendChatResp struct {
 *       ConversationId int64           `json:"conversation_id,string"`
 *       Message        ChatMessageItem `json:"message"`
 *       Status         int32           `json:"status"`
 *       CanSend        bool            `json:"can_send"`
 *   }
 * </pre>
 *
 * <p><b>{@code message} 永远是对象</b>：Go 的 {@code shared.ToSendChatResp} 在
 * {@code in.Message == nil} 时用零值 struct 占位，
 * 所以客户端永远可以 {@code resp.message.content} 而不必判空。
 * Java 侧在映射时保持：proto message 为 null 时给一个全零 DTO，<b>不返回 null</b>。
 *
 * <p>使用方：{@code SendUserChatMessage}（user）与 {@code SendAdminChatMessage}（admin），
 * 两个端点共用本 DTO —— Go 侧也是共用 {@code types.SendChatResp}。
 */
public class SendChatResp {

    @JsonProperty("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long conversationId;

    /** 不允许为 null：Go 用零值 struct 占位。 */
    private ChatMessageItem message = new ChatMessageItem();

    private int status;
    @JsonProperty("can_send")
    private boolean canSend;

    @JsonProperty("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public long getConversationId() {
        return conversationId;
    }

    public void setConversationId(long conversationId) {
        this.conversationId = conversationId;
    }

    public ChatMessageItem getMessage() {
        return message;
    }

    public void setMessage(ChatMessageItem message) {
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @JsonProperty("can_send")
    public boolean isCanSend() {
        return canSend;
    }

    public void setCanSend(boolean canSend) {
        this.canSend = canSend;
    }
}
