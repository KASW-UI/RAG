package com.rag.like.like.api.service;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.dto.GetLikeCountReq;
import com.rag.like.like.api.dto.GetLikeCountResp;
import com.rag.like.like.api.dto.LikeCountItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对应 Go {@code api/internal/logic/like/getlikecountlogic.go}。
 *
 * <p><b>⚠️ 本端点在 Go 侧<b>不做身份解析</b></b>：
 * {@code getlikecountlogic.go:33-57} 全文没有 {@code ctx.Value("userId")}。
 * 它虽然同样受 {@code rest.WithJwt} 保护（token 必须有效才进得来），
 * 但<b>不读</b> uid、也不因 uid 异常而失败。
 * ⇒ Java 侧<b>刻意不注入 HttpServletRequest、不调 {@code userId(...)}</b>。
 * 把 uid 提取加到这里就是凭空多出一条 Go 没有的失败路径（1006）。
 *
 * <p>对应的对称端点是 {@link TargetLikerListService}（点赞人列表是公开读），
 * 而 {@link LikeStateService} / {@link UserLikeListService} /
 * {@link UserTotalLikeService} / {@link LikeActionService} 这 4 个是<b>要</b> uid 的。
 * <b>这个鉴权不对称是 Go 的显式行为</b>，不是遗漏。
 */
@Service
public class LikeCountService {

    private final LikeRpcClient likeRpcClient;
    private final LikeApiSupport support;

    public LikeCountService(LikeRpcClient likeRpcClient, LikeApiSupport support) {
        this.likeRpcClient = likeRpcClient;
        this.support = support;
    }

    /**
     * 对应 Go：
     * <pre>
     * rpcResp, err := l.svcCtx.LikeRpc.GetLikeCount(l.ctx, rpcReq)
     * if err != nil { ... }
     * counts := make(map[string]types.LikeCountItem, len(rpcResp.Counts))
     * for k, v := range rpcResp.Counts { counts[k] = types.LikeCountItem{ v.LikeCount, v.DislikeCount } }
     * return &amp;types.GetLikeCountResp{Counts: counts}, nil
     * </pre>
     *
     * <p><b>⚠️ {@code counts} 无条件 {@code make}</b>：Go 侧即使 rpc 返回空 map，
     * 结果也是 <b>{}</b> 而非 null（详见 {@link GetLikeCountResp}）。
     */
    public GetLikeCountResp getLikeCount(GetLikeCountReq req) {
        var rpcReq = com.rag.like.like.proto.GetLikeCountReq.newBuilder()
                .setTargetType(req.getTargetType())
                .addAllTargetIds(idList(req.getTargetIds()))
                .build();

        com.rag.like.like.proto.GetLikeCountResp rpcResp;
        try {
            rpcResp = likeRpcClient.likeRpc().getLikeCount(rpcReq);
        } catch (RuntimeException e) {
            throw support.rpcError(e);
        }

        Map<String, LikeCountItem> counts = new LinkedHashMap<>();
        for (Map.Entry<String, com.rag.like.like.proto.LikeCountItem> entry
                : rpcResp.getCountsMap().entrySet()) {
            counts.put(entry.getKey(), new LikeCountItem(
                    entry.getValue().getLikeCount(),
                    entry.getValue().getDislikeCount()));
        }
        return new GetLikeCountResp(counts);
    }

    /** 缺席的 {@code target_ids} 在 Go 上是<b>空 slice</b>（不是 null），见 {@link GetLikeCountReq}。 */
    private static List<String> idList(List<String> ids) {
        return ids == null ? List.of() : ids;
    }
}
