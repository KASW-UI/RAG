package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;




/**
 * 会话列表响应 —— 对应 Go {@code types.go} 的 {@code ConversationListResp}。
 *
 * <pre>
 * Go:
 *   type ConversationListResp struct {
 *       Items       []ConversationItem `json:"items"`
 *       Total       int64              `json:"total"`
 *       UnreadCount int64              `json:"unread_count"`
 *   }
 * </pre>
 *
 * <p>{@code items} 无数据时为 {@code []}（Go 的 {@code make([]T, 0, len)}），
 * 原因与 {@link NotificationListResp} 相同。
 *
 * <p><b>{@code unread_count} 的语义</b>：该操作人<b>全部</b>会话的未读总数，
 * 不是当前页的未读之和 —— Go 侧 rpc 端按 recipient 聚合，与分页无关。
 * 分页取数与聚合是两件事，api 层原样透传。
 */
public class ConversationListResp {

    private List<ConversationItem> items = new ArrayList<>();
    private long total;
    @JsonProperty("unread_count")
    private long unreadCount;

    public List<ConversationItem> getItems() {
        return items;
    }

    public void setItems(List<ConversationItem> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    @JsonProperty("unread_count")
    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
