package com.rag.like.like.api.service;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.dto.GetUserLikeListReq;
import com.rag.like.like.api.dto.GetUserLikeListResp;
import com.rag.like.like.api.dto.LikeRecordItem;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对应 Go {@code api/internal/logic/like/getuserlikelistlogic.go}。
 *
 * <p><b>要 uid</b>：Go 侧 {@code getuserlikelistlogic.go:37-56}，取不到 ⇒ <b>1006</b>。
 *
 * <p><b>⚠️ {@code list} 无条件 make</b>：Go {@code getuserlikelistlogic.go:65}
 * 是 {@code make([]types.LikeRecordItem, 0, len(...))} ⇒ 空结果输出 <b>[]</b>。
 */
@Service
public class UserLikeListService {

    private final LikeRpcClient likeRpcClient;
    private final LikeApiSupport support;

    public UserLikeListService(LikeRpcClient likeRpcClient, LikeApiSupport support) {
        this.likeRpcClient = likeRpcClient;
        this.support = support;
    }

    public GetUserLikeListResp getUserLikeList(GetUserLikeListReq req, HttpServletRequest httpRequest) {
        long uid = support.userId(httpRequest);

        com.rag.like.like.proto.GetUserLikeListReq rpcReq =
                com.rag.like.like.proto.GetUserLikeListReq.newBuilder()
                .setTargetType(req.getTargetType())
                .setUserId(uid)
                .setCursor(req.getCursor())
                .setPageSize(req.getPageSize())
                .build();

        try {
            com.rag.like.like.proto.GetUserLikeListResp rpcResp =
                    likeRpcClient.likeRpc().getUserLikeList(rpcReq);
            List<LikeRecordItem> list = new ArrayList<>(rpcResp.getListCount());
            for (com.rag.like.like.proto.LikeRecordItem item : rpcResp.getListList()) {
                list.add(new LikeRecordItem(
                        item.getTargetType(), item.getTargetId(), item.getTimestamp()));
            }
            return new GetUserLikeListResp(list, rpcResp.getIsEnd(), rpcResp.getNextCursor());
        } catch (RuntimeException e) {
            throw support.rpcError(e);
        }
    }
}
