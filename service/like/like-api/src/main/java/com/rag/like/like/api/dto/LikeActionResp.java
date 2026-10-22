package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:66-71}：
 * <pre>
 * type LikeActionResp struct {
 *     Success      bool   `json:"success"`
 *     Message      string `json:"message"`
 *     LikeCount    int64  `json:"like_count"`
 *     DislikeCount int64  `json:"dislike_count"`
 * }
 * </pre>
 *
 * <p><b>⚠️⚠️ {@code success} 是「HTTP 成功」标志，恒为 true，绝不是业务结果标志</b>。
 * Go 出处 {@code likeactionlogic.go:70-75}：
 * <pre>
 * return &amp;types.LikeActionResp{
 *     Success:      true,                 ← 硬编码字面量
 *     Message:      rpcResp.Message,
 *     LikeCount:    rpcResp.LikeCount,
 *     DislikeCount: rpcResp.DislikeCount,
 * }, nil
 * </pre>
 *
 * <p>注意 Go <b>没有</b>读 {@code rpcResp.Success}（proto 里那个 bool 字段被完全忽略），
 * 只透传 {@code Message} / 两个计数。
 *
 * <p><b>语义反转是本项目已经栽过的坑</b>：users 域曾把同形的 {@code success} 当成
 * 「点赞是否真的生效」的业务结果，导致失败路径被写成 {@code success:false} + HTTP 200，
 * 客户端按 {@code code == 200 && success} 判成功时会把失败当成功。
 * 这里<b>保持 Go 的硬编码 true</b>；业务成败的判据是 envelope 的 {@code code}。
 *
 * <p><b>推论</b>：点赞「执行成功但业务上被拒（如防刷 / 热点降级）」时，
 * Go 返回的仍是 {@code success:true}，失败信息在 {@code message} 里。
 * Java 侧必须同样只透传 {@code message}。
 */
public class LikeActionResp {

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("like_count")
    private long likeCount;

    @JsonProperty("dislike_count")
    private long dislikeCount;

    public LikeActionResp() {
    }

    public LikeActionResp(boolean success, String message, long likeCount, long dislikeCount) {
        this.success = success;
        this.message = message;
        this.likeCount = likeCount;
        this.dislikeCount = dislikeCount;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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