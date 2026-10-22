package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;




/**
 * 通知列表响应 —— 对应 Go {@code types.go} 的 {@code NotificationListResp}。
 *
 * <pre>
 * Go:
 *   type NotificationListResp struct {
 *       Items       []NotificationItem `json:"items"`
 *       Total       int64              `json:"total"`          // 数字
 *       UnreadCount int64              `json:"unread_count"`   // 数字
 *   }
 * </pre>
 *
 * <p><b>{@code items} 在无数据时是 {@code []} 而不是 {@code null}</b>：
 * Go 侧 {@code shared.ToNotificationListResp} 第一行就是
 * {@code items := make([]types.NotificationItem, 0, len(in.Items))}，
 * 非 nil 空切片被 {@code encoding/json} 序列化成 {@code []}。
 * Java 侧必须初始化成空 List，否则会输出 {@code "items":null} —— 客户端的
 * {@code for (item of items)} 会直接抛 NPE。
 *
 * <p>另外 Go 的映射循环里有 {@code if item == nil { continue }}，
 * 即 proto 的 repeated 元素为 nil 时<b>跳过</b>而不是产出空对象。
 * Java 侧 protobuf 不会给 repeated 塞 null，天然满足。
 */
public class NotificationListResp {

    /** 初始化为空列表，保证序列化出 {@code []}。 */
    private List<NotificationItem> items = new java.util.ArrayList<>();

    private long total;
    @JsonProperty("unread_count")
    private long unreadCount;

    public List<NotificationItem> getItems() {
        return items;
    }

    public void setItems(List<NotificationItem> items) {
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
