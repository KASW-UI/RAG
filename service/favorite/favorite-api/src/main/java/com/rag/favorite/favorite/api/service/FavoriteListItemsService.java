package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.FavoriteItem;
import com.rag.favorite.favorite.api.dto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.api.dto.GetFavoritesByFolderResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询收藏夹下的收藏 —— 对应 Go
 * {@code api/internal/logic/favorite/get_favorites_by_folder_logic.go:32-79}。
 *
 * <p><b>L2 业务语义</b>：
 * <ol>
 *   <li>解析 userId（失败 → 5004，HTTP 200）；</li>
 *   <li>调 favorite-rpc 的 {@code GetFavoritesByFolder}；</li>
 *   <li>下游返回 {@code null} → 5001；</li>
 *   <li>成功 → {@code {favorites:[...]}}。</li>
 * </ol>
 *
 * <p><b>⚠️ {@code favorites} 永远非 null</b>：Go 的
 * {@code get_favorites_by_folder_logic.go:64} 是
 * {@code make([]types.FavoriteItem, 0, len(rpcResp.Favorites))} —— <b>无条件 make</b>，
 * 空结果输出 {@code []} 而不是 {@code null}。
 */
@Service
public class FavoriteListItemsService {

    private final FavoriteRpcClient rpcClient;
    private final FavoriteApiSupport support;

    public FavoriteListItemsService(FavoriteRpcClient rpcClient, FavoriteApiSupport support) {
        this.rpcClient = rpcClient;
        this.support = support;
    }

    public GetFavoritesByFolderResp getFavoritesByFolder(GetFavoritesByFolderReq req,
                                                         HttpServletRequest httpRequest) {
        long userId = support.userId(httpRequest);

        com.rag.favorite.favorite.proto.GetFavoritesByFolderReq rpcReq =
                com.rag.favorite.favorite.proto.GetFavoritesByFolderReq.newBuilder()
                        .setUserId(userId)
                        .setFolderId(FavoriteApiSupport.nullToZero(req.getFolderId()))
                        .build();

        com.rag.favorite.favorite.proto.GetFavoritesByFolderResp rpcResp =
                support.invokeRpc(() -> rpcClient.favoriteRpc().getFavoritesByFolder(rpcReq), userId, null);

        // Go: get_favorites_by_folder_logic.go:57-62 —— rpcResp == nil ⇒ 5001
        if (rpcResp == null) {
            throw support.nullOrUnsuccessfulResponse(userId, null);
        }

        List<FavoriteItem> favorites = new ArrayList<>(rpcResp.getFavoritesCount());
        for (com.rag.favorite.favorite.proto.FavoriteItem proto : rpcResp.getFavoritesList()) {
            favorites.add(support.toItemDto(proto));
        }

        support.logInfo(String.format("get favorites by folder success, count=%d", favorites.size()),
                userId, null);
        return new GetFavoritesByFolderResp(favorites);
    }
}
