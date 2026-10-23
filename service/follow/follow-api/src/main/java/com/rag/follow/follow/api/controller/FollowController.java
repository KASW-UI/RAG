package com.rag.follow.follow.api.controller;

import com.rag.follow.follow.api.dto.FollowListReq;
import com.rag.follow.follow.api.dto.FollowUserListResp;
import com.rag.follow.follow.api.dto.RecommendListResp;
import com.rag.follow.follow.api.dto.RelationActionReq;
import com.rag.follow.follow.api.dto.RelationActionResp;
import com.rag.follow.follow.api.service.BlockService;
import com.rag.follow.follow.api.service.FollowService;
import com.rag.follow.follow.api.service.GetBlockListService;
import com.rag.follow.follow.api.service.GetFollowListService;
import com.rag.follow.follow.api.service.GetFollowerListService;
import com.rag.follow.follow.api.service.GetRecommendationsService;
import com.rag.follow.follow.api.service.UnblockService;
import com.rag.follow.follow.api.service.UnfollowService;
import com.rag.users.common.http.GoStyleRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * follow 域全部 8 个端点 —— 对应 Go {@code service/follow/api/internal/handler/routes.go:16-64}
 * <b>唯一那一段</b> {@code server.AddRoutes(...)}。
 *
 * <p><b>8 个端点逐条对齐</b>（Go routes.go 的 Method + Path + Handler）：
 * <pre>
 *   POST /follow/v1/block            ← BlockHandler              → BlockService
 *   POST /follow/v1/block/list       ← GetBlockListHandler       → GetBlockListService
 *   POST /follow/v1/follow           ← FollowHandler             → FollowService
 *   POST /follow/v1/follow/list      ← GetFollowListHandler      → GetFollowListService
 *   POST /follow/v1/follower/list    ← GetFollowerListHandler    → GetFollowerListService
 *   POST /follow/v1/recommendations  ← GetRecommendationsHandler → GetRecommendationsService
 *   POST /follow/v1/unblock          ← UnblockHandler            → UnblockService
 *   POST /follow/v1/unfollow         ← UnfollowHandler           → UnfollowService
 * </pre>
 *
 * <p><b>方法全是 POST</b>（Go 侧 8 条 {@code Method: http.MethodPost}），
 * <b>没有 GET、没有 DELETE</b> —— 与 favorite-api 那种混合方法域不同。
 *
 * <p><b>路径差异只有表达层一处</b>：Go 用 {@code rest.WithPrefix("/follow/v1")} + 相对 path
 * 拼出完整路径，Spring 用 {@code @RequestMapping("/follow/v1")} + 类内相对 path。
 *
 * <h3>⚠️ 为什么每个端点都显式声明 {@code @RequestBody(required = false)}</h3>
 * <p>Go 侧 8 个 handler <b>无一例外</b>都调 {@code httpx.Parse(r, &req)}，
 * 即便 body 为空也会走一遍解析并可能失败。
 * 若 Java 侧不声明 {@code @RequestBody}，Spring 根本不读 body ⇒
 * 「畸形 JSON」「必填字段缺失」这两类 Go 会返回 {@code {code:5001}} 的请求
 * 在 RAG 上会变成成功 —— 凭空多出一条 Go 没有的路径。
 * 声明 {@code required = false} 是为了让「空 body」能落到
 * {@code GoStyleRequest.normalize(null, factory)}（= Go 的 {@code UnmarshalJsonMap(nil, v)}），
 * 由 DTO 自己的必填校验决定结局。
 *
 * <h3>⚠️ 两类 DTO 的归一化方式不同</h3>
 * <ul>
 *   <li><b>{@link RelationActionReq}</b>（4 个动作端点）：有必填字段 {@code target_id}
 *       ⇒ 用 {@link GoStyleRequest#normalize}，空 body 会触发必填校验 ⇒ {@code {code:5001}}。</li>
 *   <li><b>{@link FollowListReq}</b>（4 个 list 端点）：三字段全 {@code ,optional}
 *       ⇒ 用 {@link #zeroIfNull} 产出零值 DTO，<b>不</b>触发任何校验
 *       （Go 侧空 body 同样得到零值 struct 并继续往下走）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/follow/v1")
public class FollowController {

    private final FollowService followService;
    private final UnfollowService unfollowService;
    private final BlockService blockService;
    private final UnblockService unblockService;
    private final GetFollowListService getFollowListService;
    private final GetFollowerListService getFollowerListService;
    private final GetBlockListService getBlockListService;
    private final GetRecommendationsService getRecommendationsService;

    public FollowController(FollowService followService,
                            UnfollowService unfollowService,
                            BlockService blockService,
                            UnblockService unblockService,
                            GetFollowListService getFollowListService,
                            GetFollowerListService getFollowerListService,
                            GetBlockListService getBlockListService,
                            GetRecommendationsService getRecommendationsService) {
        this.followService = followService;
        this.unfollowService = unfollowService;
        this.blockService = blockService;
        this.unblockService = unblockService;
        this.getFollowListService = getFollowListService;
        this.getFollowerListService = getFollowerListService;
        this.getBlockListService = getBlockListService;
        this.getRecommendationsService = getRecommendationsService;
    }

    // ============== 动作类（4 个）==============

    @PostMapping("/follow")
    public RelationActionResp follow(@RequestBody(required = false) RelationActionReq req,
                                     HttpServletRequest httpRequest) {
        return followService.follow(GoStyleRequest.normalize(req, RelationActionReq::new), httpRequest);
    }

    @PostMapping("/unfollow")
    public RelationActionResp unfollow(@RequestBody(required = false) RelationActionReq req,
                                       HttpServletRequest httpRequest) {
        return unfollowService.unfollow(GoStyleRequest.normalize(req, RelationActionReq::new), httpRequest);
    }

    @PostMapping("/block")
    public RelationActionResp block(@RequestBody(required = false) RelationActionReq req,
                                    HttpServletRequest httpRequest) {
        return blockService.block(GoStyleRequest.normalize(req, RelationActionReq::new), httpRequest);
    }

    @PostMapping("/unblock")
    public RelationActionResp unblock(@RequestBody(required = false) RelationActionReq req,
                                      HttpServletRequest httpRequest) {
        return unblockService.unblock(GoStyleRequest.normalize(req, RelationActionReq::new), httpRequest);
    }

    // ============== 列表类（4 个）==============

    @PostMapping("/follow/list")
    public FollowUserListResp getFollowList(@RequestBody(required = false) FollowListReq req,
                                            HttpServletRequest httpRequest) {
        return getFollowListService.getFollowList(zeroIfNull(req), httpRequest);
    }

    @PostMapping("/follower/list")
    public FollowUserListResp getFollowerList(@RequestBody(required = false) FollowListReq req,
                                              HttpServletRequest httpRequest) {
        return getFollowerListService.getFollowerList(zeroIfNull(req), httpRequest);
    }

    @PostMapping("/block/list")
    public FollowUserListResp getBlockList(@RequestBody(required = false) FollowListReq req,
                                           HttpServletRequest httpRequest) {
        return getBlockListService.getBlockList(zeroIfNull(req), httpRequest);
    }

    @PostMapping("/recommendations")
    public RecommendListResp getRecommendations(@RequestBody(required = false) FollowListReq req,
                                               HttpServletRequest httpRequest) {
        return getRecommendationsService.getRecommendations(zeroIfNull(req), httpRequest);
    }

    /**
     * 空 body → 零值 DTO —— 对应 Go 侧 {@code mapping.UnmarshalJsonMap(nil, v)}。
     *
     * <p>{@link FollowListReq} 的三个字段全是 {@code ,optional}，
     * 所以零值 DTO <b>不需要</b>再跑一次必填校验（跑也是空转）。
     */
    private static FollowListReq zeroIfNull(FollowListReq req) {
        return req == null ? new FollowListReq() : req;
    }
}