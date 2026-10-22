package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:83-87}：
 * <pre>
 * type LikeRecordItem struct {
 *     TargetType string `json:"target_type"`
 *     TargetId   string `json:"target_id"`
 *     Timestamp  int64  `json:"timestamp"`
 * }
 * </pre>
 */
public class LikeRecordItem {

    @JsonProperty("target_type")
    private String targetType;

    @JsonProperty("target_id")
    private String targetId;

    @JsonProperty("timestamp")
    private long timestamp;

    public LikeRecordItem() {
    }

    public LikeRecordItem(String targetType, String targetId, long timestamp) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.timestamp = timestamp;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
