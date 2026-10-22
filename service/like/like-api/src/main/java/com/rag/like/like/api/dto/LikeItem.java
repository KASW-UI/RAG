package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:78-81}：
 * <pre>
 * type LikeItem struct {
 *     UserId    int64 `json:"user_id"`
 *     Timestamp int64 `json:"timestamp"`
 * }
 * </pre>
 */
public class LikeItem {

    @JsonProperty("user_id")
    private long userId;

    @JsonProperty("timestamp")
    private long timestamp;

    public LikeItem() {
    }

    public LikeItem(long userId, long timestamp) {
        this.userId = userId;
        this.timestamp = timestamp;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
