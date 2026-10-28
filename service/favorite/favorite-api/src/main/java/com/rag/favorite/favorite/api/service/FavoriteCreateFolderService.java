package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.api.dto.CreateFavoriteFolderResp;
import com.rag.favorite.favorite.common.span.FavoriteSpans;
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
        // 🔴 Go 出处（api/internal/logic/favorite/create_favorite_folder_logic.go:33-45）：
        //     ctx, span := otel.Tracer("favorite-api").Start(l.ctx, "FavoriteAPI.CreateFavoriteFolder")
        //     defer span.End()
        //     userID, err := extractUserID(ctx)      ← 失败 ⇒ 直接 return（**不 RecordError**、不设属性）
        //     span.SetAttributes(attribute.Int64("biz.user_id", userID),
        //                         attribute.String("biz.folder_name", req.Name))   ← **未 trim 的原值**
        return FavoriteSpans.tracedApi(
                FavoriteSpans.API_CREATE_FAVORITE_FOLDER,
                () -> support.userId(httpRequest),
                (span, userId) -> {
                    span.setAttribute(FavoriteSpans.ATTR_USER_ID, userId);
                    // 🔴 Go 记的是 req.Name 的**原值**（不 trim）—— 与落库用的 normalizeFolderName 不同
                    span.setAttribute(FavoriteSpans.ATTR_FOLDER_NAME,
                            FavoriteApiSupport.nullToEmpty(req.getName()));
                },
                userId -> {
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
                });
    }
}
