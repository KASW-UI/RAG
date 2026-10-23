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
 * 取关 —— 对应 Go {@code logic/follow/unfollowlogic.go} 的 {@code UnfollowLogic.Unfollow}。
 *
 * <p><b>Go body（逐字）</b>：与 {@link FollowService} 完全同构，只换
 * {@code const route = "/follow/v1/unfollow"} 与 {@code FollowRpc.Unfollow}。
 *
 * <p><b>⚠️ 取关「不存在的关系」不是错误</b>：Go 侧本 logic <b>没有</b>「先查是否存在」
 * 这一步，幂等性由 follow-rpc 决定（{@code ErrorFollowNotFound=5203} 是否返回取决于
 * {@code FollowModel.DeleteRelation} 的实现）。api 层不预判、不补偿。
 */
@Service
public class UnfollowService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(UnfollowService.class);

    /** Go: {@code const route = "/follow/v1/unfollow"}。 */
    static final String ROUTE = "/follow/v1/unfollow";

    public UnfollowService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public RelationActionResp unfollow(RelationActionReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long userId = requireIdentity(ROUTE, httpRequest);

            RelationReq rpcReq = RelationReq.newBuilder()
                    .setUserId(userId)
                    .setTargetId(req.targetId())
                    .build();
            BaseResp resp;
            try {
                resp = rpc().unfollow(rpcReq);
            } catch (StatusRuntimeException e) {
                BusinessException mapped = support().rpcError(e);
                log.warn("unfollow rpc failed, userId={}, code={}", userId, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return RelationActionResp.ok();
        });
    }
}