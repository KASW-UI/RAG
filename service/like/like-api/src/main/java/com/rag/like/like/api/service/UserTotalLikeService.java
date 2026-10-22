package com.rag.like.like.api.service;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.dto.GetUserTotalLikeReq;
import com.rag.like.like.api.dto.GetUserTotalLikeResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go {@code api/internal/logic/like/getusertotallikelogic.go}。
 *
 * <p><b>要 uid</b>：Go 侧 {@code getusertotallikelogic.go:37-52} 从 ctx 取
 * {@code userId}，取不到 ⇒ <b>1006</b>。因此这里<b>先</b>解析 uid 再调 rpc，
 * 顺序与 Go 一致（Go 也是先解析 uid 再构造 rpcReq）。
 *
 * <p><b>⚠️ 入参 {@link GetUserTotalLikeReq} 是零字段 DTO</b>，但 uid 仍必需 ——
 * 它来自 JWT 而非 body。这是本模块唯一「请求体为空却仍需身份」的端点。
 */
@Service
public class UserTotalLikeService {

    private final LikeRpcClient likeRpcClient;
    private final LikeApiSupport support;

    public UserTotalLikeService(LikeRpcClient likeRpcClient, LikeApiSupport support) {
        this.likeRpcClient = likeRpcClient;
        this.support = support;
    }

    public GetUserTotalLikeResp getUserTotalLike(GetUserTotalLikeReq req, HttpServletRequest httpRequest) {
        // Go 顺序：先 ctx 取 uid（失败 → 1006），再构造 rpcReq
        long uid = support.userId(httpRequest);

        com.rag.like.like.proto.GetUserTotalLikeReq rpcReq =
                com.rag.like.like.proto.GetUserTotalLikeReq.newBuilder()
                .setUserId(uid)
                .build();

        try {
            return new GetUserTotalLikeResp(
                    likeRpcClient.likeRpc().getUserTotalLike(rpcReq).getTotalLikeCount());
        } catch (RuntimeException e) {
            throw support.rpcError(e);
        }
    }
}
