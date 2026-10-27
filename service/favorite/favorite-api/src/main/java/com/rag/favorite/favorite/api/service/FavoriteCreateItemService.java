package com.rag.favorite.favorite.api.service;

import com.rag.favorite.favorite.api.client.FavoriteRpcClient;
import com.rag.favorite.favorite.api.dto.CreateFavoriteReq;
import com.rag.favorite.favorite.api.dto.CreateFavoriteResp;
import com.rag.favorite.favorite.common.span.FavoriteSpans;
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
        // 🔴 Go 出处（api/internal/logic/favorite/create_favorite_logic.go:34-48）：
        //     ctx, span := otel.Tracer("favorite-api").Start(l.ctx, "FavoriteAPI.CreateFavorite")
        //     defer span.End()
        //     userID, err := extractUserID(ctx)      ← 失败 ⇒ 直接 return（**不 RecordError**、不设属性）
        //     span.SetAttributes(
        //         attribute.Int64("biz.user_id", userID),
        //         attribute.Int64("biz.folder_id", req.FolderId),
        //         attribute.String("biz.target_id", strings.TrimSpace(req.TargetId)),   ← **trim 过**
        //         attribute.String("biz.target_type", req.TargetType))                  ← **未规范化**
        return FavoriteSpans.tracedApi(
                FavoriteSpans.API_CREATE_FAVORITE,
                () -> support.userId(httpRequest),
                (span, userId) -> {
                    span.setAttribute(FavoriteSpans.ATTR_USER_ID, userId);
                    span.setAttribute(FavoriteSpans.ATTR_FOLDER_ID,
                            FavoriteApiSupport.nullToZero(req.getFolderId()));
                    // 🔴 Go 侧这**三个**属性的取值各不相同，别「顺手统一」：
                    //    · target_id   ⇒ TrimSpace 过
                    //    · target_type ⇒ 原样（不 lower、不 trim）
                    //    · folder_name ⇒ 原样（不 trim）
                    span.setAttribute(FavoriteSpans.ATTR_TARGET_ID,
                            FavoriteApiSupport.nullToEmpty(req.getTargetId()).trim());
                    span.setAttribute(FavoriteSpans.ATTR_TARGET_TYPE,
                            FavoriteApiSupport.nullToEmpty(req.getTargetType()));
                },
                userId -> {
                    // ⚠️ 下游请求里带的是**未 trim 的原值**（Go: create_favorite_logic.go:58 `TargetId: req.TargetId`）
                    //    ⇒ 与 span 属性（trim 过）不是同一个值 —— 这是 Go 的形状，不是 bug。
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
                });
    }
}
