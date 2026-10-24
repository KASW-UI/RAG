package com.rag.favorite.favorite.api.dto;

/**
 * 对应 Go {@code api/internal/types/types.go:30-32}：
 * <pre>
 * type DeleteFavoriteFolderResp struct {
 *     Success bool `json:"success"`
 * }
 * </pre>
 *
 * <p><b>成功时恒为 {@code true}</b>：Go 的
 * {@code delete_favorite_folder_logic.go:57-62} 在 {@code rpcResp == nil || !rpcResp.Success}
 * 时直接返回 5001，<b>不会走到这里</b>；走到就意味着删除已成功。
 */
public class DeleteFavoriteFolderResp {

    private boolean success;

    public DeleteFavoriteFolderResp() {
    }

    public DeleteFavoriteFolderResp(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
