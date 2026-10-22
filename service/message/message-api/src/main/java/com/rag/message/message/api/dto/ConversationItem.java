package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;




/**
 * 会话列表项 —— 对应 Go {@code types.go} 的 {@code ConversationItem}。
 *
 * <pre>
 * Go:
 *   type ConversationItem struct {
 *       ConversationId, PeerId int64 `json:",string"`   // 两个是字符串
 *       PeerRole     int32  `json:"peer_role"`
 *       PeerName     string `json:"peer_name"`
 *       PeerEmail    string `json:"peer_email"`
 *       Status       int32  `json:"status"`             // proto 的 ConversationStatus → 数字
 *       UnreadCount  int64  `json:"unread_count"`       // 数字
 *       LatestMessage     string `json:"latest_message"`
 *       LatestMessageAt   int64  `json:"latest_message_at"`   // 数字
 *       UpdatedAt         int64  `json:"updated_at"`          // 数字
 *       CanSend     bool   `json:"can_send"`
 *   }
 * </pre>
 *
 * <p>⚠️ 三个时间/计数字段都是 <b>JSON 数字</b>（Go 内部是 Unix 秒），
 * 只有两个 id 是字符串 —— 同一条记录里混用两种表示是 Go 的既有形态。
 *
 * <p><b>{@code peer_name} / {@code peer_email} 可能为空串</b>：
 * 对方是 admin 时 email 可能没有。Go 侧不做任何占位替换，Java 侧也不加。
 */
public class ConversationItem {

    @JsonProperty("conversation_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long conversationId;

    @JsonProperty("peer_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long peerId;

    @JsonProperty("peer_role")
    private int peerRole;
    @JsonProperty("peer_name")
    /** Go 零值 struct 里 string 字段恒为 ""（不是 null），故显式初始化；
     *  零值占位路径（proto message 缺失）依赖它输出 "" 而不是 null。 */
    private String peerName = "";
    @JsonProperty("peer_email")
    /** Go 零值 struct 里 string 字段恒为 ""（不是 null），故显式初始化；
     *  零值占位路径（proto message 缺失）依赖它输出 "" 而不是 null。 */
    private String peerEmail = "";
    private int status;
    @JsonProperty("unread_count")
    private long unreadCount;
    @JsonProperty("latest_message")
    /** Go 零值 struct 里 string 字段恒为 ""（不是 null），故显式初始化；
     *  零值占位路径（proto message 缺失）依赖它输出 "" 而不是 null。 */
    private String latestMessage = "";

    @JsonProperty("latest_message_at")
    private long latestMessageAt;

    @JsonProperty("updated_at")
    private long updatedAt;

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

    @JsonProperty("peer_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public long getPeerId() {
        return peerId;
    }

    public void setPeerId(long peerId) {
        this.peerId = peerId;
    }

    @JsonProperty("peer_role")
    public int getPeerRole() {
        return peerRole;
    }

    public void setPeerRole(int peerRole) {
        this.peerRole = peerRole;
    }

    @JsonProperty("peer_name")
    public String getPeerName() {
        return peerName;
    }

    public void setPeerName(String peerName) {
        this.peerName = peerName;
    }

    @JsonProperty("peer_email")
    public String getPeerEmail() {
        return peerEmail;
    }

    public void setPeerEmail(String peerEmail) {
        this.peerEmail = peerEmail;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @JsonProperty("unread_count")
    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }

    @JsonProperty("latest_message")
    public String getLatestMessage() {
        return latestMessage;
    }

    public void setLatestMessage(String latestMessage) {
        this.latestMessage = latestMessage;
    }

    @JsonProperty("latest_message_at")
    public long getLatestMessageAt() {
        return latestMessageAt;
    }

    public void setLatestMessageAt(long latestMessageAt) {
        this.latestMessageAt = latestMessageAt;
    }

    @JsonProperty("updated_at")
    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @JsonProperty("can_send")
    public boolean isCanSend() {
        return canSend;
    }

    public void setCanSend(boolean canSend) {
        this.canSend = canSend;
    }
}
