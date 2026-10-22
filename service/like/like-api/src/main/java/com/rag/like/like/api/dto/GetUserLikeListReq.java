package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 对应 Go {@code api/internal/types/types.go:37-41}：
 * <pre>
 * type GetUserLikeListReq struct {
 *     TargetType string `json:"target_type"`
 *     Cursor     int64  `json:"cursor"`
 *     PageSize   int32  `json:"page_size"`
 * }
 * </pre>
 *
 * <p>3 个字段全是标量 ⇒ <b>全部必填</b>。理由与包装类型的必要性同
 * {@link GetTargetLikerListReq}（Go {@code processNamedFieldWithoutValue} 的
 * {@code default:} 分支）。
 *
 * <p>⚠️ 本 DTO <b>没有 userId 字段</b>：uid 来自 JWT claim，
 * 由 {@code LikeApiSupport#userId(HttpServletRequest)} 从 request attribute 取，
 * 取不到返回 <b>1006</b>（Go {@code getuserlikelistlogic.go:37-49}）。
 */
public class GetUserLikeListReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;
    @JsonProperty("cursor")
    private Long cursor;
    @JsonProperty("page_size")
    private Integer pageSize;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("cursor", cursor);
        GoStyleRequired.field("page_size", pageSize);
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Long getCursor() {
        return cursor;
    }

    public void setCursor(Long cursor) {
        this.cursor = cursor;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}