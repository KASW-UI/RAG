package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:52-54}：
 * <pre>
 * type GetUserTotalLikeResp struct {
 *     TotalLikeCount int64 `json:"total_like_count"`
 * }
 * </pre>
 */
public class GetUserTotalLikeResp {

    @JsonProperty("total_like_count")
    private long totalLikeCount;

    public GetUserTotalLikeResp() {
    }

    public GetUserTotalLikeResp(long totalLikeCount) {
        this.totalLikeCount = totalLikeCount;
    }

    public long getTotalLikeCount() {
        return totalLikeCount;
    }

    public void setTotalLikeCount(long totalLikeCount) {
        this.totalLikeCount = totalLikeCount;
    }
}
