package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;





/**
 * 会话消息列表响应 —— 对应 Go {@code types.go} 的 {@code ConversationMessagesResp}。
 *
 * <pre>
 * Go:
 *   type ConversationMessagesResp struct {
 *       Items             []ChatMessageItem `json:"items"`
 *       Status            int32             `json:"status"`
 *       CanSend           bool              `json:"can_send"`
 *       PendingSenderId   int64             `json:"pending_sender_id,string"`   // 字符串
 *       PendingSenderRole int32             `json:"pending_sender_role"`
 *   }
 * </pre>
 *
 * <p><b>会话状态机（{@code status}）</b>由 message-rpc 判定，api 层只透传：
 * <pre>
 *   PENDING(1)  首条消息已发、等对方回复 —— can_send=false, pending_sender_id=对方
 *   OPEN(2)     双向可发 —— can_send=true
 * </pre>
 * 「只有管理员能发起」等规则（{@code ErrorAdminOnlyInit}=5205）在 rpc 侧，
 * api 层<b>不</b>预判，否则会出现同一规则两处实现。
 */
public class ConversationMessagesResp {

    private List<ChatMessageItem> items = new ArrayList<>();
    private int status;
    @JsonProperty("can_send")
    private boolean canSend;

    @JsonProperty("pending_sender_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private long pendingSenderId;

    @JsonProperty("pending_sender_role")
    private int pendingSenderRole;

    public List<ChatMessageItem> getItems() {
        return items;
    }

    public void setItems(List<ChatMessageItem> items) {
        this.items = items;
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

    @JsonProperty("can_send")
    public void setCanSend(boolean canSend) {
        this.canSend = canSend;
    }

    @JsonProperty("pending_sender_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public long getPendingSenderId() {
        return pendingSenderId;
    }

    @JsonProperty("pending_sender_id")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public void setPendingSenderId(long pendingSenderId) {
        this.pendingSenderId = pendingSenderId;
    }

    @JsonProperty("pending_sender_role")
    public int getPendingSenderRole() {
        return pendingSenderRole;
    }

    @JsonProperty("pending_sender_role")
    public void setPendingSenderRole(int pendingSenderRole) {
        this.pendingSenderRole = pendingSenderRole;
    }
}
