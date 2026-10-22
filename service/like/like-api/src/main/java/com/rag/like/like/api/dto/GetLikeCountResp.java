package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 对应 Go {@code api/internal/types/types.go:11-13}：
 * <pre>
 * type GetLikeCountResp struct {
 *     Counts map[string]LikeCountItem `json:"counts"`
 * }
 * </pre>
 *
 * <p><b>⚠️ {@code counts} 在 Go 上永远非 null</b>：
 * {@code getlikecountlogic.go:48} 是 {@code counts := make(map[string]types.LikeCountItem, len(rpcResp.Counts))}
 * —— 无条件 {@code make}，即便 rpc 返回空也得到一个空 map ⇒ JSON 是 <b>{}</b> 而<b>不是 null</b>。
 * Java 侧因此必须<b>同样无条件 new 一个 map</b>；返回 {@code null} 会让
 * {@code "counts":null}，那是可观察的字节级差异。
 */
public class GetLikeCountResp {

    @JsonProperty("counts")
    private Map<String, LikeCountItem> counts;

    public GetLikeCountResp() {
    }

    public GetLikeCountResp(Map<String, LikeCountItem> counts) {
        this.counts = counts;
    }

    public Map<String, LikeCountItem> getCounts() {
        return counts;
    }

    public void setCounts(Map<String, LikeCountItem> counts) {
        this.counts = counts;
    }
}
