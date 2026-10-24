package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 对应 Go {@code api/internal/types/types.go:70-72}：
 * <pre>
 * type GetFavoritesByFolderResp struct {
 *     Favorites []FavoriteItem `json:"favorites"`
 * }
 * </pre>
 *
 * <p><b>⚠️ {@code favorites} 永远非 null</b>：Go 的
 * {@code get_favorites_by_folder_logic.go:64} 是
 * {@code favorites := make([]types.FavoriteItem, 0, len(rpcResp.Favorites))}
 * —— <b>无条件 make</b>，空结果也是 <b>{@code []}</b> 而非 {@code null}。
 * <p>⇒ Service 侧必须给空集合赋值；DTO 侧<b>不加</b> {@code @JsonInclude(NON_NULL)}。
 */
public class GetFavoritesByFolderResp {

    @JsonProperty("favorites")
    private List<FavoriteItem> favorites;

    public GetFavoritesByFolderResp() {
    }

    public GetFavoritesByFolderResp(List<FavoriteItem> favorites) {
        this.favorites = favorites;
    }

    public List<FavoriteItem> getFavorites() {
        return favorites;
    }

    public void setFavorites(List<FavoriteItem> favorites) {
        this.favorites = favorites;
    }
}
