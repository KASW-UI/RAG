package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;




/**
 * 单条私信 —— 对应 Go {@code types.go} 的 {@code ChatMessageItem}。
 *
 * <pre>
 * Go:
 *   type ChatMessageItem struct {
 *       Id, ConversationId, SenderId, RecipientId int64 `json:",string"`   // 四个都是字符串
 *       SenderRole    int32  `json:"sender_role"`
 *       RecipientRole int32  `json:"recipient_role"`
 *       Content       string `json:"content"`
 *       IsRead        bool   `json:"is_read"`
 *       CreatedAt     int64  `json:"created_at"`                          // 数字
 *   }
 * </pre>
 *
 * <p><b>零值语义</b>：Go 的 {@code shared.ToSendChatResp} 在 {@code in.Message == nil} 时
 * 用<b>全零值</b>的 {@code types.ChatMessageItem{}} 填 {@code message}，
 * 所以 {@code message} 永远是对象（最坏情况是
 * {@code {"id":"0","conversation_id":"0","sender_id":"0","sender_role":0,...}}），
 * <b>不会是 null</b>。Java 侧在 {@code MessageApiSupport} 里保持同样处理。
 */
public class ChatMessageItem {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long id;

    @JsonProperty("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long conversationId;

    @JsonProperty("sender_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long senderId;

    @JsonProperty("sender_role")
    private int senderRole;

    @JsonProperty("recipient_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long recipientId;

    @JsonProperty("recipient_role")
    private int recipientRole;
    /** Go 零值 struct 里 string 字段恒为 ""（不是 null），故显式初始化；
     *  零值占位路径（proto message 缺失）依赖它输出 "" 而不是 null。 */
    private String content = "";
    private boolean read;

    @JsonProperty("created_at")
    private long createdAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @JsonProperty("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public long getConversationId() {
        return conversationId;
    }

    public void setConversationId(long conversationId) {
        this.conversationId = conversationId;
    }

    @JsonProperty("sender_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public long getSenderId() {
        return senderId;
    }

    public void setSenderId(long senderId) {
        this.senderId = senderId;
    }

    @JsonProperty("sender_role")
    public int getSenderRole() {
        return senderRole;
    }

    public void setSenderRole(int senderRole) {
        this.senderRole = senderRole;
    }

    @JsonProperty("recipient_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(long recipientId) {
        this.recipientId = recipientId;
    }

    @JsonProperty("recipient_role")
    public int getRecipientRole() {
        return recipientRole;
    }

    public void setRecipientRole(int recipientRole) {
        this.recipientRole = recipientRole;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @JsonProperty("is_read")
    public boolean isRead() {
        return read;
    }

    @JsonProperty("is_read")
    public void setRead(boolean read) {
        this.read = read;
    }

    @JsonProperty("created_at")
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
