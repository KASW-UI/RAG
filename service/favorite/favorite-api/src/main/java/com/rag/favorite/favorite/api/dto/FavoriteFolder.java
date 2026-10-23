package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:42-47}：
 * <pre>
 * type FavoriteFolder struct {
 *     FolderId  int64  `json:"folderId,string"`
 *     Name      string `json:"name"`
 *     CreatedAt int64  `json:"createdAt"`
 *     UpdatedAt int64  `json:"updatedAt"`
 * }
 * </pre>
 *
 * <p><b>⚠️ 两种 int64 的编码方式不同，别统一</b>：
 * <ul>
 *   <li>{@code folderId} 带 {@code ,string} ⇒ 出参是 <b>JSON 字符串</b> {@code "123"}；</li>
 *   <li>{@code createdAt} / {@code updatedAt} <b>没有</b> {@code ,string}
 *       ⇒ 出参是 <b>JSON 数字</b>（Go 的 {@code encoding/json} 对 int64 默认写数字）。</li>
 * </ul>
 * 这是 Go 的 {@code types.go} 里 {@code FavoriteFolder} 与 {@code FavoriteItem} 的共同形态，
 * 混掉任何一个都会改变 L1 出参。
 *
 * <p><b>时间戳单位</b>：{@code createdAt} / {@code updatedAt} 来自 favorite-rpc 的
 * {@code favorite.FavoriteFolder.created_at / updated_at}（Unix <b>毫秒</b>，
 * 见 favorite-rpc 侧 {@code helper.go:69,83} 的 {@code UnixMilli()}）。
 * 本层<b>原样透传</b>，不做任何单位换算。
 */
public class FavoriteFolder {

    @JsonProperty("folderId")
    private Long folderId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("createdAt")
    private long createdAt;

    @JsonProperty("updatedAt")
    private long updatedAt;

    public FavoriteFolder() {
    }

    public FavoriteFolder(Long folderId, String name, long createdAt, long updatedAt) {
        this.folderId = folderId;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 出参为字符串（对齐 Go 的 {@code json:"folderId,string"}）。 */
    public String getFolderId() {
        return folderId == null ? null : folderId.toString();
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** 无 {@code ,string} ⇒ 出参是 JSON 数字。 */
    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /** 无 {@code ,string} ⇒ 出参是 JSON 数字。 */
    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
