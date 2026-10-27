package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.FavoriteFolder;
import com.rag.favorite.favorite.api.dto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.common.span.FavoriteSpans;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询收藏夹列表 —— 对应 Go
 * {@code api/internal/logic/favorite/get_favorite_folders_logic.go:32-70}。
 *
 * <p><b>L2 业务语义</b>：
 * <ol>
 *   <li>解析 userId（失败 → 5004，HTTP 200）；</li>
 *   <li>调 favorite-rpc 的 {@code GetFavoriteFolders}；</li>
 *   <li>下游返回 {@code null} → 5001；</li>
 *   <li>成功 → {@code {folders:[...]}}。</li>
 * </ol>
 *
 * <p><b>⚠️ {@code folders} 永远非 null</b>：Go 的
 * {@code get_favorite_folders_logic.go:58} 是
 * {@code make([]types.FavoriteFolder, 0, len(rpcResp.Folders))} —— <b>无条件 make</b>，
 * 空结果输出 {@code []} 而不是 {@code null}。⇒ 本实现无条件给空集合赋值。
 */
@Service
public class FavoriteListFoldersService {

    private final FavoriteRpcClient rpcClient;
    private final FavoriteApiSupport support;

    public FavoriteListFoldersService(FavoriteRpcClient rpcClient, FavoriteApiSupport support) {
        this.rpcClient = rpcClient;
        this.support = support;
    }

    public GetFavoriteFoldersResp getFavoriteFolders(HttpServletRequest httpRequest) {
        // 🔴 Go 出处（api/internal/logic/favorite/get_favorite_folders_logic.go:33-42）：
        //     ctx, span := otel.Tracer("favorite-api").Start(l.ctx, "FavoriteAPI.GetFavoriteFolders")
        //     defer span.End()
        //     userID, err := extractUserID(ctx)      ← 失败 ⇒ 直接 return（**不 RecordError**、不设属性）
        //     span.SetAttributes(attribute.Int64("biz.user_id", userID))    ← **只有这一个属性**
        return FavoriteSpans.tracedApi(
                FavoriteSpans.API_GET_FAVORITE_FOLDERS,
                () -> support.userId(httpRequest),
                (span, userId) -> span.setAttribute(FavoriteSpans.ATTR_USER_ID, userId),
                userId -> {
                    com.rag.favorite.favorite.proto.GetFavoriteFoldersReq rpcReq =
                            com.rag.favorite.favorite.proto.GetFavoriteFoldersReq.newBuilder()
                                    .setUserId(userId)
                                    .build();

                    com.rag.favorite.favorite.proto.GetFavoriteFoldersResp rpcResp =
                            support.invokeRpc(() -> rpcClient.favoriteRpc().getFavoriteFolders(rpcReq), userId, null);

                    // Go: get_favorite_folders_logic.go:51-56 —— rpcResp == nil ⇒ 5001
                    // ⚠️ Go 这一支【只判 nil】（响应消息里没有 success 字段）⇒ 用 nullResponse。
                    if (rpcResp == null) {
                        throw support.nullResponse(userId, null);
                    }

                    List<FavoriteFolder> folders = new ArrayList<>(rpcResp.getFoldersCount());
                    for (com.rag.favorite.favorite.proto.FavoriteFolder proto : rpcResp.getFoldersList()) {
                        folders.add(support.toFolderDto(proto));
                    }

                    support.logInfo(String.format("get favorite folders success, count=%d", folders.size()), userId, null);
                    return new GetFavoriteFoldersResp(folders);
                });
    }
}
