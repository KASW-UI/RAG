package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:49-57}：
 * <pre>
 * type FavoriteItem struct {
 *     FavoriteId int64  `json:"favoriteId,string"`
 *     FolderId   int64  `json:"folderId,string"`
 *     TargetId   string `json:"targetId"`
 *     TargetType string `json:"targetType"`
 *     Title      string `json:"title,optional"`
 *     Cover      string `json:"cover,optional"`
 *     CreatedAt  int64  `json:"createdAt"`
 * }
 * </pre>
 *
 * <p><b>⚠️ 编码方式与 {@code ,optional} 的双重陷阱</b>：
 * <ul>
 *   <li>{@code favoriteId} / {@code folderId} 带 {@code ,string} ⇒ 出参是 <b>JSON 字符串</b>；</li>
 *   <li>{@code createdAt} <b>没有</b> {@code ,string} ⇒ 出参是 <b>JSON 数字</b>；</li>
 *   <li>{@code title} / {@code cover} 的 {@code ,optional} 是 <b>go-zero mapping 的选项</b>，
 *       <b>不是</b> Go {@code encoding/json} 的 {@code omitempty} ⇒
 *       <b>这两个字段永远出现</b>，为空时输出 {@code ""}（不是 null、也不是消失）。</li>
 * </ul>
 * ⇒ 本 DTO 的 {@code title} / {@code cover} <b>刻意不加</b> {@code @JsonInclude(NON_NULL)}，
 * 与 admin-api 的 {@code GetUserListResp#list} 同款理由。
 *
 * <p><b>时间戳单位</b>：{@code createdAt} 来自 favorite-rpc 的
 * {@code favorite.FavoriteItem.created_at}（Unix <b>毫秒</b>），本层原样透传。
 */
public class FavoriteItem {

    @JsonProperty("favoriteId")
    private Long favoriteId;

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

    @JsonProperty("createdAt")
    private long createdAt;

    public FavoriteItem() {
    }

    public FavoriteItem(Long favoriteId, Long folderId, String targetId, String targetType,
                        String title, String cover, long createdAt) {
        this.favoriteId = favoriteId;
        this.folderId = folderId;
        this.targetId = targetId;
        this.targetType = targetType;
        this.title = title;
        this.cover = cover;
        this.createdAt = createdAt;
    }

    /** 出参为字符串（对齐 Go 的 {@code json:"favoriteId,string"}，
     *     {@code service/favorite/api/internal/types/types.go:50}）。 */
    public String getFavoriteId() {
        return favoriteId == null ? null : favoriteId.toString();
    }

    public void setFavoriteId(Long favoriteId) {
        this.favoriteId = favoriteId;
    }

    /** 出参为字符串（对齐 Go 的 {@code json:"folderId,string"}，
     *     {@code service/favorite/api/internal/types/types.go:51}）。 */
    public String getFolderId() {
        return folderId == null ? null : folderId.toString();
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
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

    /** 无 {@code ,string} ⇒ 出参是 JSON 数字。 */
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
