package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 对应 Go {@code api/internal/types/types.go:20-22}：
 * <pre>
 * type GetLikeStateResp struct {
 *     States map[string]int32 `json:"states"`
 * }
 * </pre>
 *
 * <p><b>与 {@link GetLikeCountResp} 的非 nil 保证来源不同，但结果同样非 null</b>：
 * api 侧是直接透传 {@code States: rpcResp.States}（{@code getlikestatelogic.go:66}），
 * 非 nil 由<b>rpc 侧</b>保证 —— {@code getlikestatelogic.go:34-36} 是
 * {@code resp := &pb.GetLikeStateResp{States: make(map[string]int32)}}，
 * 且成功路径上每个 {@code target_ids} 都必然写入一条（Redis 命中或 DB 回源填零值），
 * 所以成功时 {@code states} 既非 null 也非空。
 *
 * <p>value 类型用 {@link Integer}：proto 的 {@code map<string,int32>} 语义等价。
 */
public class GetLikeStateResp {

    @JsonProperty("states")
    private Map<String, Integer> states;

    public GetLikeStateResp() {
    }

    public GetLikeStateResp(Map<String, Integer> states) {
        this.states = states;
    }

    public Map<String, Integer> getStates() {
        return states;
    }

    public void setStates(Map<String, Integer> states) {
        this.states = states;
    }
}
