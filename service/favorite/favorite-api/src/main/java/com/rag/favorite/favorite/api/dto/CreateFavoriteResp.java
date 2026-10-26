package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 对应 Go {@code api/internal/types/types.go:22-24}：
 * <pre>
 * type CreateFavoriteResp struct {
 *     FavoriteId int64 `json:"favoriteId,string"`
 * }
 * </pre>
 *
 * <p>出参的 {@code ,string} 语义见 {@link CreateFavoriteFolderResp} 的类注释
 * （<b>只写字符串</b>，与入参方向的宽松相反）。
 */
public class CreateFavoriteResp {

    @JsonProperty("favoriteId")
    private Long favoriteId;

    public CreateFavoriteResp() {
    }

    public CreateFavoriteResp(Long favoriteId) {
        this.favoriteId = favoriteId;
    }

    /** 出参为字符串（对齐 Go 的 {@code json:"favoriteId,string"}，
     *     {@code service/favorite/api/internal/types/types.go:23}）。 */
    public String getFavoriteId() {
        return favoriteId == null ? null : favoriteId.toString();
    }

    public void setFavoriteId(Long favoriteId) {
        this.favoriteId = favoriteId;
    }
}
