package com.rag.follow.follow.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.follow.follow.api.client.FollowRpcClient;
import com.rag.follow.follow.api.dto.RelationActionReq;
import com.rag.follow.follow.api.dto.RelationActionResp;
import com.rag.follow.follow.api.metrics.FollowApiMetrics;
import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.RelationReq;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 关注用户 —— 对应 Go {@code logic/follow/followlogic.go} 的 {@code FollowLogic.Follow}。
 *
 * <p><b>Go body（逐字）</b>：
 * <pre>
 * const route = "/follow/v1/follow"
 * started := time.Now()
 * defer func() { metrics.ObserveRequest(route, started, err) }()
 *
 * userID, err := extractUserID(l.ctx)
 * if err != nil { metrics.ObserveReject(route, "user_id_missing"); return nil, NewErrCode(ErrorUnauthorized) }
 *
 * rpcResp, rpcErr := l.svcCtx.FollowRpc.Follow(l.ctx, &amp;followservice.RelationReq{
 *     UserId:   userID,
 *     TargetId: req.TargetId,
 * })
 * if rpcErr != nil { code := codeFromRPCError(rpcErr); return nil, NewErrCode(code) }
 * if rpcResp.Code != int32(Success) { return nil, NewErrCodeMsg(int(rpcResp.Code), rpcResp.Msg) }
 *
 * return &amp;types.RelationActionResp{Success: true}, nil
 * </pre>
 *
 * <p><b>⚠️ 本端点没有归属校验</b>：{@code TargetId} 可以是任意值（自己的 id 也行）。
 * Go 侧的自关注 / 重复关注 / 被拉黑等判定<b>全部在 follow-rpc</b>
 * （{@code ErrorSelfRelation=5201} / {@code ErrorAlreadyExist} / {@code ErrorRelationBlocked=5202}）。
 * api 层提前加校验会把业务码换成 5003/5001 —— 那是新增行为。
 *
 * <p><b>4 个动作端点（follow / unfollow / block / unblock）的请求与响应形状完全相同</b>，
 * 只有<b>调用的 RPC 方法</b>与 {@code route} 常量不同。
 */
@Service
public class FollowService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(FollowService.class);

    /** Go: {@code const route = "/follow/v1/follow"}。 */
    static final String ROUTE = "/follow/v1/follow";

    public FollowService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public RelationActionResp follow(RelationActionReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long userId = requireIdentity(ROUTE, httpRequest);

            RelationReq rpcReq = RelationReq.newBuilder()
                    .setUserId(userId)
                    .setTargetId(req.targetId())
                    .build();
            BaseResp resp;
            try {
                resp = rpc().follow(rpcReq);
            } catch (StatusRuntimeException e) {
                // Go: code := codeFromRPCError(rpcErr) → BizCodeFromError（文本先于 gRPC code）
                BusinessException mapped = support().rpcError(e);
                log.warn("follow rpc failed, userId={}, code={}", userId, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return RelationActionResp.ok();
        });
    }
}