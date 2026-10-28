package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteFolderResp;
import com.rag.favorite.favorite.common.span.FavoriteSpans;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 删除收藏夹 —— 对应 Go
 * {@code api/internal/logic/favorite/delete_favorite_folder_logic.go:32-66}。
 *
 * <p><b>L2 业务语义</b>：
 * <ol>
 *   <li>解析 userId（失败 → 5004，HTTP 200）；</li>
 *   <li>调 favorite-rpc 的 {@code DeleteFavoriteFolder}；</li>
 *   <li>下游返回 {@code null} <b>或</b> {@code success=false} → <b>5001</b>
 *       （Go 把这两个情形合并成同一条分支）；</li>
 *   <li>成功 → {@code {success:true}}。</li>
 * </ol>
 *
 * <p><b>⚠️ 「他人收藏夹」的归属校验在 favorite-rpc 侧</b>（Go 的 rpc logic 返回
 * {@code PermissionDenied} → api 侧还原成 5005）。api 层不做归属判断。
 */
@Service
public class FavoriteDeleteFolderService {

    private final FavoriteRpcClient rpcClient;
    private final FavoriteApiSupport support;

    public FavoriteDeleteFolderService(FavoriteRpcClient rpcClient, FavoriteApiSupport support) {
        this.rpcClient = rpcClient;
        this.support = support;
    }

    public DeleteFavoriteFolderResp deleteFavoriteFolder(DeleteFavoriteFolderReq req, HttpServletRequest httpRequest) {
        // 🔴 Go 出处（api/internal/logic/favorite/delete_favorite_folder_logic.go:33-45）：
        //     ctx, span := otel.Tracer("favorite-api").Start(l.ctx, "FavoriteAPI.DeleteFavoriteFolder")
        //     defer span.End()
        //     userID, err := extractUserID(ctx)      ← 失败 ⇒ 直接 return（**不 RecordError**、不设属性）
        //     span.SetAttributes(attribute.Int64("biz.user_id", userID),
        //                         attribute.Int64("biz.folder_id", req.FolderId))
        return FavoriteSpans.tracedApi(
                FavoriteSpans.API_DELETE_FAVORITE_FOLDER,
                () -> support.userId(httpRequest),
                (span, userId) -> {
                    span.setAttribute(FavoriteSpans.ATTR_USER_ID, userId);
                    span.setAttribute(FavoriteSpans.ATTR_FOLDER_ID,
                            FavoriteApiSupport.nullToZero(req.getFolderId()));
                },
                userId -> {
                    com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq rpcReq =
                            com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq.newBuilder()
                                    .setUserId(userId)
                                    .setFolderId(FavoriteApiSupport.nullToZero(req.getFolderId()))
                                    .build();

                    com.rag.favorite.favorite.proto.DeleteFavoriteFolderResp rpcResp =
                            support.invokeRpc(() -> rpcClient.favoriteRpc().deleteFavoriteFolder(rpcReq), userId, null);

                    // Go: delete_favorite_folder_logic.go:57-62 —— rpcResp == nil || !rpcResp.Success ⇒ 5001
                    if (rpcResp == null || !rpcResp.getSuccess()) {
                        throw support.nullOrUnsuccessfulResponse(userId, null);
                    }

                    support.logInfo(String.format("delete favorite folder success, folder_id=%d", req.getFolderId()),
                            userId, null);
                    return new DeleteFavoriteFolderResp(true);
                });
    }
}
