package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteReq;
import com.rag.favorite.favorite.api.dto.DeleteFavoriteResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 删除收藏 —— 对应 Go
 * {@code api/internal/logic/favorite/delete_favorite_logic.go:32-66}。
 *
 * <p><b>L2 业务语义</b>：
 * <ol>
 *   <li>解析 userId（失败 → 5004，HTTP 200）；</li>
 *   <li>调 favorite-rpc 的 {@code DeleteFavorite}；</li>
 *   <li>下游返回 {@code null} <b>或</b> {@code success=false} → <b>5001</b>；</li>
 *   <li>成功 → {@code {success:true}}。</li>
 * </ol>
 *
 * <p><b>⚠️ 「收藏暂不可撤回」（5304）在 rpc 侧</b>：Go 的
 * {@code rpc/internal/logic/delete_favorite_logic.go:86} 用
 * {@code GRPCError(codes.FailedPrecondition, 5304)} 发出，
 * 而 {@code FAILED_PRECONDITION} 不在 {@code BizCodeFromError} 的白名单里
 * ⇒ api 侧<b>只能</b>靠 description 文案反查还原 5304
 * （这正是 {@link com.rag.favorite.favorite.common.errmsg.FavoriteGrpcError} 存在的原因）。
 */
@Service
public class FavoriteDeleteItemService {

    private final FavoriteRpcClient rpcClient;
    private final FavoriteApiSupport support;

    public FavoriteDeleteItemService(FavoriteRpcClient rpcClient, FavoriteApiSupport support) {
        this.rpcClient = rpcClient;
        this.support = support;
    }

    public DeleteFavoriteResp deleteFavorite(DeleteFavoriteReq req, HttpServletRequest httpRequest) {
        long userId = support.userId(httpRequest);

        com.rag.favorite.favorite.proto.DeleteFavoriteReq rpcReq =
                com.rag.favorite.favorite.proto.DeleteFavoriteReq.newBuilder()
                        .setUserId(userId)
                        .setFavoriteId(FavoriteApiSupport.nullToZero(req.getFavoriteId()))
                        .build();

        com.rag.favorite.favorite.proto.DeleteFavoriteResp rpcResp =
                support.invokeRpc(() -> rpcClient.favoriteRpc().deleteFavorite(rpcReq), userId, null);

        // Go: delete_favorite_logic.go:57-62 —— rpcResp == nil || !rpcResp.Success ⇒ 5001
        if (rpcResp == null || !rpcResp.getSuccess()) {
            throw support.nullOrUnsuccessfulResponse(userId, null);
        }

        support.logInfo(String.format("delete favorite success, favorite_id=%d", req.getFavoriteId()),
                userId, null);
        return new DeleteFavoriteResp(true);
    }
}
