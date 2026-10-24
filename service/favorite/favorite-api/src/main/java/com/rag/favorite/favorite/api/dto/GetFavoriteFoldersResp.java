package com.rag.favorite.favorite.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 对应 Go {@code api/internal/types/types.go:62-64}：
 * <pre>
 * type GetFavoriteFoldersResp struct {
 *     Folders []FavoriteFolder `json:"folders"`
 * }
 * </pre>
 *
 * <p><b>⚠️ {@code folders} 永远非 null</b>：Go 的
 * {@code get_favorite_folders_logic.go:58} 是
 * {@code folders := make([]types.FavoriteFolder, 0, len(rpcResp.Folders))}
 * —— <b>无条件 make</b>，空结果也是 <b>{@code []}</b> 而非 {@code null}。
 * <p>⇒ Service 侧必须给空集合赋值；DTO 侧<b>不加</b> {@code @JsonInclude(NON_NULL)}。
 */
public class GetFavoriteFoldersResp {

    @JsonProperty("folders")
    private List<FavoriteFolder> folders;

    public GetFavoriteFoldersResp() {
    }

    public GetFavoriteFoldersResp(List<FavoriteFolder> folders) {
        this.folders = folders;
    }

    public List<FavoriteFolder> getFolders() {
        return folders;
    }

    public void setFolders(List<FavoriteFolder> folders) {
        this.folders = folders;
    }
}
