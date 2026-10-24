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
 * 拉黑 —— 对应 Go {@code logic/follow/blocklogic.go} 的 {@code BlockLogic.Block}。
 *
 * <p><b>Go body（逐字）</b>：与 {@link FollowService} 完全同构，只换
 * {@code const route = "/follow/v1/block"} 与 {@code FollowRpc.Block}。
 *
 * <p><b>⚠️ 拉黑的副作用全部在 follow-rpc</b>：Go 侧 {@code blocklogic.go} 只发一次 RPC，
 * 「拉黑是否连带取消关注 / 是否解除对方关注」这类副作用由
 * {@code follow/rpc/internal/logic/blocklogic.go} + {@code FollowBlockTransactionService} 承担。
 * 本层<b>不</b>预判「已经拉黑过」就直接返回成功 —— 那会把 5204 之类的业务码吃掉。
 */
@Service
public class BlockService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);

    /** Go: {@code const route = "/follow/v1/block"}。 */
    static final String ROUTE = "/follow/v1/block";

    public BlockService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public RelationActionResp block(RelationActionReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long userId = requireIdentity(ROUTE, httpRequest);

            RelationReq rpcReq = RelationReq.newBuilder()
                    .setUserId(userId)
                    .setTargetId(req.targetId())
                    .build();
            BaseResp resp;
            try {
                resp = rpc().block(rpcReq);
            } catch (StatusRuntimeException e) {
                BusinessException mapped = support().rpcError(e);
                log.warn("block rpc failed, userId={}, code={}", userId, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return RelationActionResp.ok();
        });
    }
}