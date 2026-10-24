package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;



/**
 * 未读汇总响应 —— 对应 Go {@code types.go} 的 {@code UnreadSummaryResp}。
 *
 * <pre>
 * Go:
 *   type UnreadSummaryResp struct {
 *       NotificationUnread int64 `json:"notification_unread"`   // ← 注意：无 ,string
 *       ConversationUnread int64 `json:"conversation_unread"`
 *       TotalUnread        int64 `json:"total_unread"`
 *   }
 * </pre>
 *
 * <p><b>⚠️ 本响应的三个字段都是 JSON <b>数字</b>，不是字符串。</b>
 * Go 只有写了 {@code ,string} 的字段才序列化成字符串；这三个 tag 里没有，
 * 于是 {@code encoding/json} 直接写数字。同一个服务里
 * {@code ConversationItem.ConversationId} 却是 {@code ,string}（字符串）——
 * <b>混用是 Go 的既有形态</b>，不要"顺手统一"。
 *
 * <p>Java 侧因此<b>不能</b>给这三个字段加 {@code @JsonFormat(shape = STRING)}，
 * 与 {@link ConversationItem} 的处理正好相反。
 */
public class UnreadSummaryResp {

    @JsonProperty("notification_unread")
    private long notificationUnread;
    @JsonProperty("conversation_unread")
    private long conversationUnread;
    @JsonProperty("total_unread")
    private long totalUnread;

    public long getNotificationUnread() {
        return notificationUnread;
    }

    @JsonProperty("notification_unread")
    public void setNotificationUnread(long notificationUnread) {
        this.notificationUnread = notificationUnread;
    }

    @JsonProperty("conversation_unread")
    public long getConversationUnread() {
        return conversationUnread;
    }

    @JsonProperty("conversation_unread")
    public void setConversationUnread(long conversationUnread) {
        this.conversationUnread = conversationUnread;
    }

    @JsonProperty("total_unread")
    public long getTotalUnread() {
        return totalUnread;
    }

    public void setTotalUnread(long totalUnread) {
        this.totalUnread = totalUnread;
    }
}
