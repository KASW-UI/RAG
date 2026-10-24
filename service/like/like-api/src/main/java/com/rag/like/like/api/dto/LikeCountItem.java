package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:73-76}：
 * <pre>
 * type LikeCountItem struct {
 *     LikeCount    int64 `json:"like_count"`
 *     DislikeCount int64 `json:"dislike_count"`
 * }
 * </pre>
 *
 * <p>L1 wire key 逐字对齐 {@code like_count} / {@code dislike_count}，
 * 来自 proto {@code LikeCountItem} 的同名字段。
 */
public class LikeCountItem {

    @JsonProperty("like_count")
    private long likeCount;

    @JsonProperty("dislike_count")
    private long dislikeCount;

    public LikeCountItem() {
    }

    public LikeCountItem(long likeCount, long dislikeCount) {
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getDislikeCount() {
        return dislikeCount;
    }

    public void setDislikeCount(long dislikeCount) {
        this.dislikeCount = dislikeCount;
    }
}
