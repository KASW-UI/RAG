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
 * 黑名单列表 —— 对应 Go {@code logic/follow/getblocklistlogic.go} 的 {@code GetBlockListLogic}。
 *
 * <p><b>Go body（逐字）</b>：
 * <pre>
 * const route = "/follow/v1/block/list"
 * ...
 * currentUserID, err := extractUserID(l.ctx)
 * if err != nil { metrics.ObserveReject(route, "user_id_missing"); ... }
 *
 * queryUserID := resolveUserID(currentUserID, req.UserId)
 * if queryUserID != currentUserID {
 *     metrics.ObserveReject(route, "forbidden_scope")
 *     return nil, followcommon.NewErrCode(followcommon.ErrorForbidden)
 * }
 *
 * rpcResp, rpcErr := l.svcCtx.FollowRpc.GetBlockList(l.ctx, &amp;followservice.ListReq{...})
 * ...
 * return &amp;types.FollowUserListResp{UserIds: rpcResp.UserIds}, nil
 * </pre>
 *
 * <h3>⚠️ 本端点<b>有</b>归属校验 —— 黑名单是私有的</h3>
 * <p>这是本域 4 个 list 端点里<b>两个</b>做归属校验的之一
 * （另一个是 {@link GetRecommendationsService}）；{@link GetFollowListService} 与
 * {@link GetFollowerListService} <b>没有</b>。
 * <p>违规时的出口：<b>HTTP 200</b> + {@code {"code":5005,"msg":"forbidden","data":null}}
 * —— <b>不是 403</b>（Go 注册了 {@code httpx.SetErrorHandler}，一切业务错误恒 200）。
 *
 * <p>分页参数原样透传，归一化在 follow-rpc 侧（{@code ListConf}）。
 */
@Service
public class GetBlockListService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(GetBlockListService.class);

    /** Go: {@code const route = "/follow/v1/block/list"}。 */
    static final String ROUTE = "/follow/v1/block/list";

    public GetBlockListService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public FollowUserListResp getBlockList(FollowListReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long currentUserID = requireIdentity(ROUTE, httpRequest);

            // ⚠️ 归属校验（5005 + forbidden_scope 埋点），与 GetFollowListService 的关键差异
            long queryUserID = requireSelfScope(ROUTE, currentUserID, req.userId());

            ListReq rpcReq = ListReq.newBuilder()
                    .setUserId(queryUserID)
                    .setOffset(req.offset())
                    .setLimit(req.limit())
                    .build();
            UserListResp resp;
            try {
                resp = rpc().getBlockList(rpcReq);
            } catch (StatusRuntimeException e) {
                BusinessException mapped = support().rpcError(e);
                log.warn("getBlockList rpc failed, userId={}, code={}", queryUserID, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return support().toUserListResp(resp);
        });
    }
}