package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 对应 Go {@code api/internal/types/types.go:14-20}：
 * <pre>
 * type CreateFavoriteReq struct {
 *     FolderId   int64  `json:"folderId,string"`
 *     TargetId   string `json:"targetId"`
 *     TargetType string `json:"targetType"`
 *     Title      string `json:"title,optional"`
 *     Cover      string `json:"cover,optional"`
 * }
 * </pre>
 *
 * <p><b>必填 / 可选的分界</b>：
 * <ul>
 *   <li><b>必填</b>：{@code folderId}、{@code targetId}、{@code targetType}
 *       （tag 里没有 {@code ,optional}）；</li>
 *   <li><b>可选</b>：{@code title}、{@code cover}（tag 里有 {@code ,optional}）。
 *       Go 出处：{@code core/mapping/unmarshaler.go:856-862}
 *       {@code if mapValue == nil { if opts.optional() { return nil } ... }}
 *       ⇒ <b>缺席与显式 null 都合法</b>。</li>
 * </ul>
 * 实测：
 * <pre>
 *   {"folderId":"1","targetId":"a","targetType":"article"}                      → OK
 *   {"folderId":"1","targetId":"a"}                                             → field "targetType" is not set ⇒ 5003
 *   {"folderId":"1","targetType":"article"}                                     → field "targetId" is not set   ⇒ 5003
 *   {"folderId":"1","targetId":123,"targetType":"article"}                      → type mismatch for field "targetId" ⇒ 5003
 *   {"folderId":"1","targetId":"a","targetType":"article","title":null}         → OK（optional 显式 null 合法）
 * </pre>
 */
public class CreateFavoriteReq implements GoStyleRequiredFields {

    @JsonProperty("folderId")
    private Long folderId;

    @JsonProperty("targetId")
    private String targetId;

    @JsonProperty("targetType")
    private String targetType;

    @JsonProperty("title")
    private String title;

    @JsonProperty("cover")
    private String cover;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("folderId", folderId);
        GoStyleRequired.field("targetId", targetId);
        GoStyleRequired.field("targetType", targetType);
        // title / cover 是 ,optional ⇒ 不做必填校验（加了就把合法请求变成解析失败）
    }

    public Long getFolderId() {
        return folderId;
    }

    /**
     * <b>入参必须是 {@code Object}</b> —— 见 {@link DeleteFavoriteFolderReq#setFolderId(Object)}。
     */
    public void setFolderId(Object raw) {
        this.folderId = GoStyleStringInt64.parse(raw, "folderId");
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }
}
