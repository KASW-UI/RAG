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
 * 关注列表 —— 对应 Go {@code logic/follow/getfollowlistlogic.go} 的 {@code GetFollowListLogic}。
 *
 * <p><b>Go body（逐字）</b>：
 * <pre>
 * const route = "/follow/v1/follow/list"
 * ...
 * currentUserID, err := extractUserID(l.ctx)
 * if err != nil { metrics.ObserveReject(route, "user_id_missing"); ... }
 *
 * queryUserID := resolveUserID(currentUserID, req.UserId)
 * rpcResp, rpcErr := l.svcCtx.FollowRpc.GetFollowList(l.ctx, &amp;followservice.ListReq{
 *     UserId: queryUserID, Offset: req.Offset, Limit: req.Limit,
 * })
 * ...
 * return &amp;types.FollowUserListResp{UserIds: rpcResp.UserIds}, nil
 * </pre>
 *
 * <h3>⚠️ 本端点<b>没有</b>归属校验 —— 关注列表是公开可查的</h3>
 * <p>注意 {@code queryUserID} 算完之后<b>直接</b>发给 rpc，中间<b>没有</b>
 * {@code if queryUserID != currentUserID → ErrorForbidden} 那一段
 * （对比 {@link GetBlockListService} 与 {@link GetRecommendationsService}，两者都有）。
 * ⇒ 任意登录用户都能传 {@code user_id} 查任意人的关注列表。
 * <p>这是 Go 侧既定行为（{@code getfollowlistlogic.go:45-51}），
 * <b>必须原样保留</b>：加上校验会把「查得到」变成 5005，是新增的拒绝行为。
 *
 * <p>分页参数<b>原样透传</b>：Go 侧 api 不做 offset/limit 归一化，
 * {@code default=20 / max-limit=100} 的夹取在 follow-rpc（{@code ListConf}）里做。
 */
@Service
public class GetFollowListService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(GetFollowListService.class);

    /** Go: {@code const route = "/follow/v1/follow/list"}。 */
    static final String ROUTE = "/follow/v1/follow/list";

    public GetFollowListService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public FollowUserListResp getFollowList(FollowListReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long currentUserID = requireIdentity(ROUTE, httpRequest);

            // ⚠️ 用 resolveUserId（取值归一化），**不是** requireSelfScope（归属校验）—— 见类注释
            long queryUserID = support().resolveUserId(currentUserID, req.userId());

            ListReq rpcReq = ListReq.newBuilder()
                    .setUserId(queryUserID)
                    .setOffset(req.offset())
                    .setLimit(req.limit())
                    .build();
            UserListResp resp;
            try {
                resp = rpc().getFollowList(rpcReq);
            } catch (StatusRuntimeException e) {
                BusinessException mapped = support().rpcError(e);
                log.warn("getFollowList rpc failed, currentUserId={}, queryUserId={}, code={}",
                        currentUserID, queryUserID, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return support().toUserListResp(resp);
        });
    }
}