package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.CreateFavoriteReq;
import com.rag.favorite.favorite.api.dto.CreateFavoriteResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 新增收藏 —— 对应 Go
 * {@code api/internal/logic/favorite/create_favorite_logic.go:33-73}。
 *
 * <p><b>L2 业务语义</b>：
 * <ol>
 *   <li>解析 userId（失败 → 5004，HTTP 200）；</li>
 *   <li>调 favorite-rpc 的 {@code CreateFavorite}；</li>
 *   <li>下游返回 {@code null} → 5001；</li>
 *   <li>成功 → {@code {favoriteId}}（<b>出参是字符串</b>）。</li>
 * </ol>
 *
 * <p><b>⚠️ 日志里的 articleId 就是 {@code targetId}</b>：Go 的
 * {@code create_favorite_logic.go:61,71} 用
 * {@code articleLogOption(req.TargetId)} —— 即「被收藏对象的 ID」，
 * 在 favorite 域里 target_id 存的就是文章 ID，但<b>字段名仍叫 target_id</b>。
 * 本实现原样透传（不 trim —— Go 的 {@code articleLogOption} 里有
 * {@code strings.TrimSpace}，见 {@link FavoriteApiSupport}）。
 */
@Service
public class FavoriteCreateItemService {

    private final FavoriteRpcClient rpcClient;
    private final FavoriteApiSupport support;

    public FavoriteCreateItemService(FavoriteRpcClient rpcClient, FavoriteApiSupport support) {
        this.rpcClient = rpcClient;
        this.support = support;
    }

    public CreateFavoriteResp createFavorite(CreateFavoriteReq req, HttpServletRequest httpRequest) {
        long userId = support.userId(httpRequest);
        String targetId = req.getTargetId();

        com.rag.favorite.favorite.proto.CreateFavoriteReq rpcReq =
                com.rag.favorite.favorite.proto.CreateFavoriteReq.newBuilder()
                        .setUserId(userId)
                        .setFolderId(FavoriteApiSupport.nullToZero(req.getFolderId()))
                        .setTargetId(FavoriteApiSupport.nullToEmpty(targetId))
                        .setTargetType(FavoriteApiSupport.nullToEmpty(req.getTargetType()))
                        // ⚠️ title / cover 是 ,optional 字段，【缺席时在 Go 侧是零值 ""】，
                        // 而 protobuf 生成的 setXxx(null) 会抛 NullPointerException
                        // （内部是 Objects.requireNonNull）。
                        // ⇒ 显式把 null 归一成 ""，与 Go 的零值 struct 同构。
                        // Go 出处：api/internal/types/types.go:18-19 的 tag 是 ,optional，
                        //   core/mapping/unmarshaler.go:856-862 的
                        //   `if mapValue == nil { if opts.optional() { return nil } ... }`
                        //   ⇒ 缺席与显式 null 都让字段保持零值 ""。
                        .setTitle(FavoriteApiSupport.nullToEmpty(req.getTitle()))
                        .setCover(FavoriteApiSupport.nullToEmpty(req.getCover()))
                        .build();

        com.rag.favorite.favorite.proto.CreateFavoriteResp rpcResp =
                support.invokeRpc(() -> rpcClient.favoriteRpc().createFavorite(rpcReq), userId, targetId);

        // Go: create_favorite_logic.go:64-69 —— rpcResp == nil ⇒ 5001
        if (rpcResp == null) {
            throw support.nullResponse(userId, targetId);
        }

        support.logInfo(String.format("create favorite success, favorite_id=%d", rpcResp.getFavoriteId()),
                userId, targetId);
        return new CreateFavoriteResp(rpcResp.getFavoriteId());
    }
}
