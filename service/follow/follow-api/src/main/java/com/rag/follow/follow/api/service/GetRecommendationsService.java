package com.rag.follow.follow.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.follow.follow.api.client.FollowRpcClient;
import com.rag.follow.follow.api.dto.FollowListReq;
import com.rag.follow.follow.api.dto.RecommendListResp;
import com.rag.follow.follow.api.metrics.FollowApiMetrics;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.RecommendResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 推荐列表 —— 对应 Go {@code logic/follow/getrecommendationslogic.go} 的 {@code GetRecommendationsLogic}。
 *
 * <p><b>Go body（逐字）</b>：
 * <pre>
 * const route = "/follow/v1/recommendations"
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
 * rpcResp, rpcErr := l.svcCtx.FollowRpc.GetRecommendations(l.ctx, &amp;followservice.ListReq{...})
 * ...
 * users := make([]types.RecommendUser, 0, len(rpcResp.Users))
 * for _, user := range rpcResp.Users {
 *     if user == nil { continue }
 *     users = append(users, types.RecommendUser{TargetId: user.TargetId, MutualScore: user.MutualScore})
 * }
 * return &amp;types.RecommendListResp{Users: users}, nil
 * </pre>
 *
 * <h3>⚠️ 本端点<b>有</b>归属校验 —— 推荐是私有的</h3>
 * <p>本域 4 个 list 端点里做归属校验的两个之一（另一个是 {@link GetBlockListService}）。
 * 理由与 Go 一致：推荐结果基于「我的关注图」计算，替他算没有意义且泄漏社交图。
 * <p>违规出口：<b>HTTP 200</b> + {@code {"code":5005,"msg":"forbidden","data":null}}。
 *
 * <p><b>⚠️ 出参 {@code target_id} 是 JSON 字符串</b>（Go tag 带 {@code ,string}），
 * 而 {@code user_ids} 是 JSON 数字 —— 详见 {@code RecommendUser} 的注释。
 * <p><b>⚠️ 空结果输出 {@code "users":[]}</b>：Go 的 {@code make(..., 0, n)} 保证非 nil，
 * 见 {@code RecommendListResp} 的注释。
 *
 * <p>推荐算法本身（层数 / 扇出 / 互相关度）<b>全部在 follow-rpc</b>，
 * api 层只做身份、归属校验与字段搬运。
 */
@Service
public class GetRecommendationsService extends AbstractFollowApiService {

    private static final Logger log = LoggerFactory.getLogger(GetRecommendationsService.class);

    /** Go: {@code const route = "/follow/v1/recommendations"}。 */
    static final String ROUTE = "/follow/v1/recommendations";

    public GetRecommendationsService(FollowRpcClient rpcClient, FollowApiSupport support, FollowApiMetrics metrics) {
        super(rpcClient, support, metrics);
    }

    public RecommendListResp getRecommendations(FollowListReq req, HttpServletRequest httpRequest) {
        return invoke(ROUTE, () -> {
            long currentUserID = requireIdentity(ROUTE, httpRequest);

            // ⚠️ 归属校验（5005 + forbidden_scope 埋点）
            long queryUserID = requireSelfScope(ROUTE, currentUserID, req.userId());

            ListReq rpcReq = ListReq.newBuilder()
                    .setUserId(queryUserID)
                    .setOffset(req.offset())
                    .setLimit(req.limit())
                    .build();
            RecommendResp resp;
            try {
                resp = rpc().getRecommendations(rpcReq);
            } catch (StatusRuntimeException e) {
                BusinessException mapped = support().rpcError(e);
                log.warn("getRecommendations rpc failed, userId={}, code={}", queryUserID, mapped.getCode(), e);
                throw mapped;
            }

            support().requireRpcSuccess(resp.getCode(), resp.getMsg());
            return support().toRecommendListResp(resp);
        });
    }
}