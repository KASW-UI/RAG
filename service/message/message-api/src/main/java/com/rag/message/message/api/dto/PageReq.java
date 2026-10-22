package com.rag.message.message.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;



/**
 * 分页请求 —— 对应 Go {@code service/message/api/internal/types/types.go} 的 {@code PageReq}。
 *
 * <pre>
 * Go:
 *   type PageReq struct {
 *       Offset     int32 `json:"offset,optional"`
 *       Limit      int32 `json:"limit,optional"`
 *       UnreadOnly bool  `json:"unread_only,optional"`
 *   }
 * </pre>
 *
 * <p><b>三个字段全部 {@code ,optional}</b> ⇒ 永不做必填校验，缺席即零值
 * （Go 的零值 struct：offset=0 / limit=0 / unread_only=false）。
 *
 * <p><b>为什么用原始 {@code int} 而不是 {@code Integer}</b>：本 DTO 没有「缺席 vs 显式 null」
 * 的语义差 —— 两个都等于零值。message-rpc 的
 * {@code MessageProperties.list.default-limit=20 / max-limit=100} 负责把 0 归一化成默认分页，
 * 对应 Go {@code rpc/etc/message.yaml} 的 {@code List { DefaultLimit: 20, MaxLimit: 100 }}。
 * 归一化<b>不在 api 层做</b>（Go 侧也在 rpc 侧做）。
 *
 * <p>使用方：{@code ListConversations} / {@code ListNotifications}（user 侧）、
 * {@code ListAdminConversations}（admin 侧）。
 */
public class PageReq {

    private int offset;
    private int limit;
    @JsonProperty("unread_only")
    private boolean unreadOnly;

    public int offset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int limit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean unreadOnly() {
        return unreadOnly;
    }

    @JsonProperty("unread_only")
    public void setUnreadOnly(boolean unreadOnly) {
        this.unreadOnly = unreadOnly;
    }
}