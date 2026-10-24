package com.rag.favorite.favorite.api.dto;

/**
 * 对应 Go {@code api/internal/types/types.go:38-40}：
 * <pre>
 * type DeleteFavoriteResp struct {
 *     Success bool `json:"success"`
 * }
 * </pre>
 *
 * <p><b>成功时恒为 {@code true}</b>：Go 的
 * {@code delete_favorite_logic.go:57-62} 在 {@code rpcResp == nil || !rpcResp.Success}
 * 时直接返回 5001，<b>不会走到这里</b>。
 */
public class DeleteFavoriteResp {

    private boolean success;

    public DeleteFavoriteResp() {
    }

    public DeleteFavoriteResp(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}
