package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;





/**
 * 单条通知 —— 对应 Go {@code types.go} 的 {@code NotificationItem}。
 *
 * <pre>
 * Go:
 *   type NotificationItem struct {
 *       Id, RecipientId, SenderId int64             `json:",string"`   // 三个都是字符串
 *       SenderRole  int32          `json:"sender_role"`
 *       Kind        int32          `json:"kind"`
 *       Title       string         `json:"title"`
 *       Content     string         `json:"content"`
 *       IsRead      bool           `json:"is_read"`
 *       CreatedAt   int64          `json:"created_at"`                   // ← 数字，不是字符串
 *       Extra       map[string]string `json:"extra"`
 *   }
 * </pre>
 *
 * <p><b>⚠️ 同一条记录里三种数字表示并存</b>：id 三兄弟是字符串、sender_role/kind 是数字、
 * created_at 也是数字。这是 Go tag 的原样，必须保留 —— 见 {@link UnreadSummaryResp} 的同类说明。
 *
 * <p><b>{@code extra} 没有 {@code ,optional}</b>，但它是 map：Go 对 map 永不做必填校验，
 * 缺席时序列化成 {@code "extra":null}（Go 的 nil map 走 encoding/json 的 null 分支）。
 * Java 侧因此<b>不</b>加 {@code @JsonInclude(NON_NULL)}，让 null 原样输出。
 *
 * <p><b>{@code sender_role} / {@code kind} 是 int32 而非字符串枚举名</b>：
 * Go 侧 {@code shared.ToNotificationListResp} 显式写了 {@code int32(item.SenderRole)}，
 * 即「proto 枚举 → 数字」。Java 侧从 proto 读 {@code getSenderRoleValue()} 拿原始 int。
 */
public class NotificationItem {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long id;

    @JsonProperty("recipient_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long recipientId;

    @JsonProperty("sender_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long senderId;

    @JsonProperty("sender_role")
    private int senderRole;
    private int kind;
    /** Go 零值 struct 里 string 字段恒为 ""（不是 null），故显式初始化；
     *  零值占位路径（proto message 缺失）依赖它输出 "" 而不是 null。 */
    private String title = "";
    /** Go 零值 struct 里 string 字段恒为 ""（不是 null），故显式初始化；
     *  零值占位路径（proto message 缺失）依赖它输出 "" 而不是 null。 */
    private String content = "";
    private boolean read;

    @JsonProperty("created_at")
    private long createdAt;

    private Map<String, String> extra;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @JsonProperty("recipient_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(long recipientId) {
        this.recipientId = recipientId;
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

    public int getKind() {
        return kind;
    }

    public void setKind(int kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    /** JSON key = {@code is_read}（不是 {@code read}）。 */
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

    public Map<String, String> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, String> extra) {
        this.extra = extra;
    }
}
