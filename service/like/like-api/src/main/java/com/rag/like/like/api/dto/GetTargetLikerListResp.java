package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 对应 Go {@code api/internal/types/types.go:31-35}：
 * <pre>
 * type GetTargetLikerListResp struct {
 *     List       []LikeItem `json:"list"`
 *     IsEnd      bool       `json:"is_end"`
 *     NextCursor int64      `json:"next_cursor"`
 * }
 * </pre>
 *
 * <p><b>⚠️ getter 刻意叫 {@code getIsEnd()} 而不是 {@code isEnd()}</b>：
 * Jackson 对布尔 {@code isXxx()} getter 会<b>剥掉 "is" 前缀</b>推导属性名，
 * 于是 {@code isEnd()} → 属性名 {@code "end"}，而字段 {@code isEnd} → 属性名
 * {@code "isEnd"}，两者<b>是两个不同的属性</b>，序列化会同时吐出
 * {@code "end"} 与 {@code "isEnd"} 两个键。
 * 用 {@code getIsEnd()} 使 getter 与字段推导出<b>同一个</b>内部名 {@code isEnd}，
 * 再由 {@code @JsonProperty("is_end")} 统一改名。
 *
 * <p><b>⚠️ {@code list} 在 Go 上永远非 nil</b>：
 * {@code gettargetlikerlistlogic.go:49} 是
 * {@code list := make([]types.LikeItem, 0, len(rpcResp.List))} —— 无条件 make，
 * 空结果也是 <b>[]</b> 而非 null。
 */
public class GetTargetLikerListResp {

    @JsonProperty("list")
    private List<LikeItem> list;

    @JsonProperty("is_end")
    private boolean isEnd;

    @JsonProperty("next_cursor")
    private long nextCursor;

    public GetTargetLikerListResp() {
    }

    public GetTargetLikerListResp(List<LikeItem> list, boolean isEnd, long nextCursor) {
        this.list = list;
        this.isEnd = isEnd;
        this.nextCursor = nextCursor;
    }

    public List<LikeItem> getList() {
        return list;
    }

    public void setList(List<LikeItem> list) {
        this.list = list;
    }

    public boolean getIsEnd() {
        return isEnd;
    }

    public void setIsEnd(boolean isEnd) {
        this.isEnd = isEnd;
    }

    public long getNextCursor() {
        return nextCursor;
    }

    public void setNextCursor(long nextCursor) {
        this.nextCursor = nextCursor;
    }
}
