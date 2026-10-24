package com.rag.follow.follow.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.follow.follow.api.client.FollowRpcClient;
import com.rag.follow.follow.api.dto.FollowListReq;
import com.rag.follow.follow.api.dto.FollowUserListResp;
import com.rag.follow.follow.api.metrics.FollowApiMetrics;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.UserListResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 粉丝列表 —— 对应 Go {@code logic/follow/getfollowerlistlogic.go} 的 {@code GetFollowerListLogic}。
 *
 * <p><b>Go body（逐字）</b>：与 {@link GetFollowListService} 完全同构，只换
 * {@code const route = "/follow/v1/follower/list"} 与 {@code FollowRpc.GetFollowerList}。
 *
 * <h3>⚠️ 同样<b>没有</b>归属校验 —— 粉丝列表也是公开可查的</h3>
 * <p>Go 出处：{@code getfollowerlistlogic.go:45-51} 算完 {@code queryUserID} 直接发 rpc，
 * 中间没有 Forbidden 分支。与关注列表同属「公开」的两个端点，
 * 与 {@link GetBlockListService} / {@link GetRecommendationsService} 的「私有」两个端点相对。
 *
 * <p><b>方向差异只在 rpc 方法名上</b>：本端点取的是「谁关注了我」
 * （{@code GetFollowerList}），而 {@code GetFollowListService} 取的是「我关注了谁」。
 * 两者的 proto 请求/响应类型<b>完全相同</b>（{@code ListReq} / {@code UserListResp}），
 * 但<b>不能因此共用同一个 Service</b> —— route 常量与 rpc 方法都不同，
 * 埋点 label 会串（{@code follow_api_request_total{route=...}} 会指向错误的端点）。
 */
@Service
public class GetFollowerListService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(GetFollowerListService.class);

    /** Go: {@code const route = "/follow/v1/follower/list"}。 */
    static final String ROUTE = "/follow/v1/follower/list";

    public GetFollowerListService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public FollowUserListResp getFollowerList(FollowListReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long currentUserID = requireIdentity(ROUTE, httpRequest);

            // ⚠️ 无归属校验，与 Go 一致（见类注释）
            long queryUserID = support().resolveUserId(currentUserID, req.userId());

            ListReq rpcReq = ListReq.newBuilder()
                    .setUserId(queryUserID)
                    .setOffset(req.offset())
                    .setLimit(req.limit())
                    .build();
            UserListResp resp;
            try {
                resp = rpc().getFollowerList(rpcReq);
            } catch (StatusRuntimeException e) {
                BusinessException mapped = support().rpcError(e);
                log.warn("getFollowerList rpc failed, currentUserId={}, queryUserId={}, code={}",
                        currentUserID, queryUserID, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return support().toUserListResp(resp);
        });
    }
}