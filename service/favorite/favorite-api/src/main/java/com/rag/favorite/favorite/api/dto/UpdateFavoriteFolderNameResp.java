package com.rag.favorite.favorite.api.dto;

/**
 * 对应 Go {@code api/internal/types/types.go:79-81}：
 * <pre>
 * type UpdateFavoriteFolderNameResp struct {
 *     Success bool `json:"success"`
 * }
 * </pre>
 *
 * <p><b>成功时恒为 {@code true}</b>：Go 的
 * {@code update_favorite_folder_name_logic.go:59-64} 在
 * {@code rpcResp == nil || !rpcResp.Success} 时直接返回 5001，<b>不会走到这里</b>。
 */
public class UpdateFavoriteFolderNameResp {

    private boolean success;

    public UpdateFavoriteFolderNameResp() {
    }

    public UpdateFavoriteFolderNameResp(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
