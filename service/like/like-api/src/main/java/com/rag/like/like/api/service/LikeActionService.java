package com.rag.like.like.api.service;

import com.rag.like.like.api.client.LikeRpcClient;
import com.rag.like.like.api.dto.LikeActionReq;
import com.rag.like.like.api.dto.LikeActionResp;
import com.rag.like.like.proto.ActionType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go {@code api/internal/logic/like/likeactionlogic.go}。
 *
 * <p><b>要 uid</b>：Go 侧 {@code likeactionlogic.go:37-59}，取不到 ⇒ <b>1006</b>。
 *
 * <h3>本端点有 3 处「看着像 bug 但是行为」的映射，全部必须保留</h3>
 *
 * <p><b>① {@code success} 恒为 true</b>（Go {@code likeactionlogic.go:71}）：
 * Go <b>不读</b> proto 里的 {@code success} 字段，硬编码 {@code true}，
 * 只透传 {@code message} / 两个计数。理由与后果见 {@link LikeActionResp}。
 *
 * <p><b>② {@code weight} 由 float64 窄化成 float32</b>（Go {@code likeactionlogic.go:56}）：
 * <pre>
 * Weight: float32(req.Weight),      // JSON 里的 double → proto 的 float
 * </pre>
 * proto 的 {@code float weight = 6;} 是 32 位，JSON 侧是 64 位 double，
 * <b>必然发生窄化</b>。Java 侧用 {@code (float)} 显式窄化，
 * 与 Go 的 {@code float32(...)} 语义一致（含超大值溢出成 {@code +Inf} 的行为）。
 * <p><b>⚠️ 不要「顺手」把它改成保留 double</b>：那会让 RAG 比 Go 多保留一位精度，
 * 下游 {@code promotion_id} 结算口径两侧就对不上了。
 *
 * <p><b>③ {@code action_type} 是 int64 → enum 的强转</b>（Go {@code likeactionlogic.go:54}）：
 * {@code pb.ActionType(req.ActionType)}。取值域已由 DTO 的
 * {@code options=1|2|3|4} 在解析期挡住，这里只会拿到 1/2/3/4 四个合法值，
 * 与 proto 的 {@code ACTION_LIKE=1 / UNLIKE=2 / DISLIKE=3 / UNDISLIKE=4} 逐字对齐。
 */
@Service
public class LikeActionService {

    private final LikeRpcClient likeRpcClient;
    private final LikeApiSupport support;

    public LikeActionService(LikeRpcClient likeRpcClient, LikeApiSupport support) {
        this.likeRpcClient = likeRpcClient;
        this.support = support;
    }

    public LikeActionResp likeAction(LikeActionReq req, HttpServletRequest httpRequest) {
        long uid = support.userId(httpRequest);

        com.rag.like.like.proto.LikeActionReq rpcReq =
                com.rag.like.like.proto.LikeActionReq.newBuilder()
                .setTargetType(req.getTargetType())
                .setTargetId(req.getTargetId())
                .setUserId(uid)
                .setActionType(toActionType(req.getActionType()))
                .setAuthorId(req.getAuthorId())
                // Go: Weight: float32(req.Weight) —— 刻意窄化，见类注释 ②
                .setWeight(req.getWeight() == null ? 0f : (float) req.getWeight().doubleValue())
                .setClientIp(req.getClientIp() == null ? "" : req.getClientIp())
                .setPromotionId(req.getPromotionId() == null ? "" : req.getPromotionId())
                .build();

        try {
            com.rag.like.like.proto.LikeActionResp rpcResp = likeRpcClient.likeRpc().likeAction(rpcReq);
            // Go: Success: true（硬编码，不读 rpcResp.getSuccess()）
            return new LikeActionResp(true, rpcResp.getMessage(),
                    rpcResp.getLikeCount(), rpcResp.getDislikeCount());
        } catch (RuntimeException e) {
            throw support.rpcError(e);
        }
    }

    /**
     * Go {@code pb.ActionType(req.ActionType)} 的等价物。
     *
     * <p>{@code options=1|2|3|4} 已保证取值合法，这里 {@code forNumber} 不会返回 null；
     * 保留 null 兜底是为了万一 DTO 校验被绕过时不至于 NPE（那会被 {@code handleAny}
     * 折叠成 5001，与 Go 的兜底一致）。
     */
    private static ActionType toActionType(Long raw) {
        ActionType actionType = raw == null ? null : ActionType.forNumber(raw.intValue());
        return actionType == null ? ActionType.ACTION_UNSPECIFIED : actionType;
    }
}