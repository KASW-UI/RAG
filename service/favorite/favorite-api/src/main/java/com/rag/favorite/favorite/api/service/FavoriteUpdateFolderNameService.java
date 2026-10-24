package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.api.dto.UpdateFavoriteFolderNameResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 修改收藏夹名字 —— 对应 Go
 * {@code api/internal/logic/favorite/update_favorite_folder_name_logic.go:32-68}。
 *
 * <p><b>L2 业务语义</b>：
 * <ol>
 *   <li>解析 userId（失败 → 5004，HTTP 200）；</li>
 *   <li>调 favorite-rpc 的 {@code UpdateFavoriteFolderName}；</li>
 *   <li>下游返回 {@code null} <b>或</b> {@code success=false} → <b>5001</b>；</li>
 *   <li>成功 → {@code {success:true}}。</li>
 * </ol>
 *
 * <p><b>⚠️ 收藏夹不存在（5201）/ 名称为空（5202）/ 名称已存在（5203）都在 rpc 侧</b>，
 * api 层原样透传（Go 侧由 {@code BizCodeFromError} 的「按 description 反查文案」还原）。
 */
@Service
public class FavoriteUpdateFolderNameService {

    private final FavoriteRpcClient rpcClient;
    private final FavoriteApiSupport support;

    public FavoriteUpdateFolderNameService(FavoriteRpcClient rpcClient, FavoriteApiSupport support) {
        this.rpcClient = rpcClient;
        this.support = support;
    }

    public UpdateFavoriteFolderNameResp updateFavoriteFolderName(UpdateFavoriteFolderNameReq req,
                                                                HttpServletRequest httpRequest) {
        long userId = support.userId(httpRequest);

        com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq rpcReq =
                com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq.newBuilder()
                        .setUserId(userId)
                        .setFolderId(FavoriteApiSupport.nullToZero(req.getFolderId()))
                        .setName(FavoriteApiSupport.nullToEmpty(req.getName()))
                        .build();

        com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameResp rpcResp =
                support.invokeRpc(() -> rpcClient.favoriteRpc().updateFavoriteFolderName(rpcReq), userId, null);

        // Go: update_favorite_folder_name_logic.go:59-64 —— nil || !Success ⇒ 5001
        if (rpcResp == null || !rpcResp.getSuccess()) {
            throw support.nullOrUnsuccessfulResponse(userId, null);
        }

        support.logInfo(String.format("update favorite folder name success, folder_id=%d", req.getFolderId()),
                userId, null);
        return new UpdateFavoriteFolderNameResp(true);
    }
}
