package com.rag.like.like.api.service;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.dto.GetLikeStateReq;
import com.rag.like.like.api.dto.GetLikeStateResp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对应 Go {@code api/internal/logic/like/getlikestatelogic.go}。
 *
 * <p><b>要 uid</b>：Go 侧 {@code getlikestatelogic.go:37-55} 从 ctx 取
 * {@code userId} 并塞进 rpcReq，取不到 ⇒ <b>1006</b>。
 *
 * <p><b>⚠️ {@code states} 直接透传</b>（Go {@code getlikestatelogic.go:66}），
 * 非 nil 由 rpc 侧保证（详见 {@link GetLikeStateResp}）。
 */
@Service
public class LikeStateService {

    private final LikeRpcClient likeRpcClient;
    private final LikeApiSupport support;

    public LikeStateService(LikeRpcClient likeRpcClient, LikeApiSupport support) {
        this.likeRpcClient = likeRpcClient;
        this.support = support;
    }

    public GetLikeStateResp getLikeState(GetLikeStateReq req, HttpServletRequest httpRequest) {
        long uid = support.userId(httpRequest);

        List<String> ids = req.getTargetIds() == null ? List.of() : req.getTargetIds();
        com.rag.like.like.proto.GetLikeStateReq rpcReq =
                com.rag.like.like.proto.GetLikeStateReq.newBuilder()
                .setTargetType(req.getTargetType())
                .setUserId(uid)
                .addAllTargetIds(GoStyleTargetIds.normalize(ids))
                .build();

        try {
            Map<String, Integer> states = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> entry
                    : likeRpcClient.likeRpc().getLikeState(rpcReq).getStatesMap().entrySet()) {
                states.put(entry.getKey(), entry.getValue());
            }
            return new GetLikeStateResp(states);
        } catch (RuntimeException e) {
            throw support.rpcError(e);
        }
    }
}
