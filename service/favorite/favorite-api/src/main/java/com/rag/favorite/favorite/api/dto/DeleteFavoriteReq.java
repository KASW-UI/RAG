package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.rag.users.common.http.GoStyleRequired;
import com.rag.users.common.http.GoStyleRequiredFields;
import com.rag.users.common.http.GoStyleStringInt64;

/**
 * 对应 Go {@code api/internal/types/types.go:34-37}：
 * <pre>
 * type DeleteFavoriteReq struct {
 *     FavoriteId int64 `json:"favoriteId,string"`
 * }
 * </pre>
 *
 * <p>{@code ,string} 的宽松入参语义见 {@link DeleteFavoriteFolderReq} 的类注释
 * （Go 侧 {@code ,string} 对 JSON 字符串与数字都接受）。
 */
public class DeleteFavoriteReq implements GoStyleRequiredFields {

    @JsonProperty("favoriteId")
    private Long favoriteId;

    @Override
    public void assertGoRequiredFields() {
        GoStyleRequired.field("favoriteId", favoriteId);
    }

    public Long getFavoriteId() {
        return favoriteId;
    }

    /**
     * <b>入参必须是 {@code Object}</b> —— 见 {@link DeleteFavoriteFolderReq#setFolderId(Object)}。
     */
    public void setFavoriteId(Object raw) {
        this.favoriteId = GoStyleStringInt64.parse(raw, "favoriteId");
    }
}
