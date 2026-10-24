package com.rag.like.like.api.service;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.dto.GetTargetLikerListReq;
import com.rag.like.like.api.dto.GetTargetLikerListResp;
import com.rag.like.like.api.dto.LikeItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对应 Go {@code api/internal/logic/like/gettargetlikerlistlogic.go}。
 *
 * <p><b>⚠️ 与 {@link LikeCountService} 同：本端点在 Go 侧<b>不做身份解析</b></b>。
 * {@code gettargetlikerlistlogic.go:33-60} 全文没有 {@code ctx.Value("userId")}。
 * 「点赞人列表是公开读」是 Go 的显式设计 ⇒ Java 侧也不注入 HttpServletRequest。
 *
 * <p><b>⚠️ {@code list} 无条件 make</b>：Go 侧
 * {@code gettargetlikerlistlogic.go:49} 是 {@code make([]types.LikeItem, 0, len(...))}，
 * 空结果输出 <b>[]</b> 而非 null。
 */
@Service
public class TargetLikerListService {

    private final LikeRpcClient likeRpcClient;
    private final LikeApiSupport support;

    public TargetLikerListService(LikeRpcClient likeRpcClient, LikeApiSupport support) {
        this.likeRpcClient = likeRpcClient;
        this.support = support;
    }

    public GetTargetLikerListResp getTargetLikerList(GetTargetLikerListReq req) {
        com.rag.like.like.proto.GetTargetLikerListReq rpcReq =
                com.rag.like.like.proto.GetTargetLikerListReq.newBuilder()
                .setTargetType(req.getTargetType())
                .setTargetId(req.getTargetId())
                .setCursor(req.getCursor())
                .setPageSize(req.getPageSize())
                .build();

        com.rag.like.like.proto.GetTargetLikerListResp rpcResp;
        try {
            rpcResp = likeRpcClient.likeRpc().getTargetLikerList(rpcReq);
        } catch (RuntimeException e) {
            throw support.rpcError(e);
        }

        List<LikeItem> list = new ArrayList<>(rpcResp.getListCount());
        for (com.rag.like.like.proto.LikeItem item : rpcResp.getListList()) {
            list.add(new LikeItem(item.getUserId(), item.getTimestamp()));
        }
        return new GetTargetLikerListResp(list, rpcResp.getIsEnd(), rpcResp.getNextCursor());
    }
}
