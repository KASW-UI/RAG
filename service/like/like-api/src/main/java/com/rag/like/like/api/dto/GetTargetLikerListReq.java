package com.rag.like.like.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;

/**
 * 对应 Go {@code api/internal/types/types.go:24-29}：
 * <pre>
 * type GetTargetLikerListReq struct {
 *     TargetType string `json:"target_type"`
 *     TargetId   string `json:"target_id"`
 *     Cursor     int64  `json:"cursor"`
 *     PageSize   int32  `json:"page_size"`
 * }
 * </pre>
 *
 * <p><b>⚠️ 本 DTO 的 4 个字段全是标量 ⇒ 4 个全部必填</b>，
 * 包括客户端极易遗忘的 {@code cursor} 与 {@code page_size}。
 * Go 出处仍是 {@code processNamedFieldWithoutValue} 的 {@code default:} 分支
 * （标量 + 非 optional ⇒ {@code newInitError} ⇒ {@code field "x" is not set}）。
 *
 * <p>⇒ 客户端<b>必须</b>显式发 {@code cursor} / {@code page_size}，
 * 连首页的 {@code cursor:0} / {@code page_size:20} 也必须发。
 * 少发任一个，Go 与 Java 两侧都返回 <b>解析失败</b>。
 *
 * <p><b>用包装类型而不是基本类型</b>：{@code Long} / {@code Integer} 能区分
 * 「缺席（null）」与「显式传 0」—— 这是上面那条规则能成立的前提。
 * 若用 {@code long} / {@code int}，缺席时是 0、无法区分，必填校验会静默失效
 * （AGENTS.md §3.3「把没看过的东西变成看过了」的同款形态）。
 *
 * <p><b>「必填」= 键必须出现，不是值不能为零</b>：Go 侧
 * {@code {"cursor":0}} 是合法的（必填校验只看键在不在）。
 */
public class GetTargetLikerListReq implements GoStyleRequiredFields {

    @JsonProperty("target_type")
    private String targetType;
    @JsonProperty("target_id")
    private String targetId;
    @JsonProperty("cursor")
    private Long cursor;
    @JsonProperty("page_size")
    private Integer pageSize;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("target_type", targetType);
        GoStyleRequired.field("target_id", targetId);
        GoStyleRequired.field("cursor", cursor);
        GoStyleRequired.field("page_size", pageSize);
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