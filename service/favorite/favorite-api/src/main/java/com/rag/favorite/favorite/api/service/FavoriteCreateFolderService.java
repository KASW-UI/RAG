package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.api.dto.CreateFavoriteFolderResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 新建收藏夹 —— 对应 Go
 * {@code api/internal/logic/favorite/create_favorite_folder_logic.go:32-66}。
 *
 * <p><b>L2 业务语义</b>：
 * <ol>
 *   <li>解析 userId（失败 → 5004，HTTP 200）；</li>
 *   <li>调 favorite-rpc 的 {@code CreateFavoriteFolder}；</li>
 *   <li>下游返回 {@code null} → 5001；</li>
 *   <li>成功 → {@code {folderId}}（<b>出参是字符串</b>，对齐 Go 的 {@code ,string}）。</li>
 * </ol>
 *
 * <p><b>⚠️ 本层不校验 folder name 是否为空 / 是否重名</b>：
 * 那两条业务规则在 favorite-rpc 侧（Go 的 {@code rpc/internal/logic/create_favorite_folder_logic.go}），
 * api 层只是透传。Go 的 api 侧同样不校验。
 */
@Service
public class FavoriteCreateFolderService {

    private final FavoriteRpcClient rpcClient;
    private final FavoriteApiSupport support;

    public FavoriteCreateFolderService(FavoriteRpcClient rpcClient, FavoriteApiSupport support) {
        this.rpcClient = rpcClient;
        this.support = support;
    }

    public CreateFavoriteFolderResp createFavoriteFolder(CreateFavoriteFolderReq req, HttpServletRequest httpRequest) {
        long userId = support.userId(httpRequest);

        com.rag.favorite.favorite.proto.CreateFavoriteFolderReq rpcReq =
                com.rag.favorite.favorite.proto.CreateFavoriteFolderReq.newBuilder()
                        .setUserId(userId)
                        .setName(FavoriteApiSupport.nullToEmpty(req.getName()))
                        .build();

        com.rag.favorite.favorite.proto.CreateFavoriteFolderResp rpcResp =
                support.invokeRpc(() -> rpcClient.favoriteRpc().createFavoriteFolder(rpcReq), userId, null);

        // Go: create_favorite_folder_logic.go:57-62 —— rpcResp == nil ⇒ 5001
        // ⚠️ Go 这一支【只判 nil】（响应消息里没有 success 字段）⇒ 用 nullResponse，
        //    不是 nullOrUnsuccessfulResponse。
        if (rpcResp == null) {
            throw support.nullResponse(userId, null);
        }

        support.logInfo(String.format("create favorite folder success, folder_id=%d", rpcResp.getFolderId()),
                userId, null);
        return new CreateFavoriteFolderResp(rpcResp.getFolderId());
    }
}
