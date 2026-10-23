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
 * 解拉黑 —— 对应 Go {@code logic/follow/unblocklogic.go} 的 {@code UnblockLogic.Unblock}。
 *
 * <p><b>Go body（逐字）</b>：与 {@link FollowService} 完全同构，只换
 * {@code const route = "/follow/v1/unblock"} 与 {@code FollowRpc.Unblock}。
 *
 * <p><b>⚠️ 解拉黑<b>不</b>恢复关注关系</b>：Go 侧 {@code unblocklogic.go} 只发一次 RPC，
 * 「解除拉黑是否连带恢复关注」由 follow-rpc 决定。本层不预判。
 */
@Service
public class UnblockService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(UnblockService.class);

    /** Go: {@code const route = "/follow/v1/unblock"}。 */
    static final String ROUTE = "/follow/v1/unblock";

    public UnblockService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public RelationActionResp unblock(RelationActionReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long userId = requireIdentity(ROUTE, httpRequest);

            RelationReq rpcReq = RelationReq.newBuilder()
                    .setUserId(userId)
                    .setTargetId(req.targetId())
                    .build();
            BaseResp resp;
            try {
                resp = rpc().unblock(rpcReq);
            } catch (StatusRuntimeException e) {
                BusinessException mapped = support().rpcError(e);
                log.warn("unblock rpc failed, userId={}, code={}", userId, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return RelationActionResp.ok();
        });
    }
}