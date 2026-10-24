package com.rag.like.like.api.controller;

import com.rag.like.like.api.dto.GetLikeCountReq;
import com.rag.like.like.api.dto.GetLikeCountResp;
import com.rag.like.like.api.dto.GetLikeStateReq;
import com.rag.like.like.api.dto.GetLikeStateResp;
import com.rag.like.like.api.dto.GetTargetLikerListReq;
import com.rag.like.like.api.dto.GetTargetLikerListResp;
import com.rag.like.like.api.dto.GetUserLikeListReq;
import com.rag.like.like.api.dto.GetUserLikeListResp;
import com.rag.like.like.api.dto.GetUserTotalLikeReq;
import com.rag.like.like.api.dto.GetUserTotalLikeResp;
import com.rag.like.like.api.dto.LikeActionReq;
import com.rag.like.like.api.dto.LikeActionResp;
import com.rag.like.like.api.service.LikeActionService;
import com.rag.like.like.api.service.LikeCountService;
import com.rag.like.like.api.service.LikeStateService;
import com.rag.like.like.api.service.TargetLikerListService;
import com.rag.like.like.api.service.UserLikeListService;
import com.rag.like.like.api.service.UserTotalLikeService;
import com.rag.users.common.http.GoStyleRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * like-api 的 6 个端点 —— 对应 Go {@code api/internal/handler/routes.go} 的单段路由表：
 * <pre>
 * server.AddRoutes(
 *     []rest.Route{
 *         {Method: POST, Path: "/getlikecount",        Handler: like.GetLikeCountHandler(serverCtx)},
 *         {Method: POST, Path: "/getlikestate",        Handler: like.GetLikeStateHandler(serverCtx)},
 *         {Method: POST, Path: "/gettargetlikerlist",  Handler: like.GetTargetLikerListHandler(serverCtx)},
 *         {Method: POST, Path: "/getuserlikelist",     Handler: like.GetUserLikeListHandler(serverCtx)},
 *         {Method: POST, Path: "/getusertotallike",    Handler: like.GetUserTotalLikeHandler(serverCtx)},
 *         {Method: POST, Path: "/likeaction",          Handler: like.LikeActionHandler(serverCtx)},
 *     },
 *     rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *     rest.WithPrefix("/like/v1"),
 * )
 * </pre>
 *
 * <p><b>6/6 全是 POST，全要 JWT</b>（Go 只有这一段路由表、没有 public 端点）。
 * Java 侧的 {@code WebMvcConfig} 把 {@code LikeJwtAuthInterceptor} 挂在 {@code /like/v1/**}，
 * 覆盖集合与 Go 的 {@code rest.WithPrefix("/like/v1")} + 全量 WithJwt 等价。
 *
 * <p><b>为什么 6 个 Go 的同质薄 handler 合并成一个 Controller</b>：
 * Go 侧每个 handler 都是 15 行完全相同的样板（Parse → NewXxxLogic → Ok/Error），
 * 真正有差异的逻辑全在 logic 层。拆成 6 个 Java 类只会复制 6 份样板，
 * 不产生任何行为差异 —— 属 L4（实现机制）的允许变更。
 *
 * <p><b>⚠️ 全部使用 {@code @RequestBody(required = false)}</b>：
 * Go 侧每个 handler 都调 {@code httpx.Parse}，空 body 会走
 * {@code UnmarshalJsonMap(nil, v)} 并<b>照常执行必填校验</b>
 * （Go 出处与实测见 {@link GoStyleRequest} 的类注释）。
 * 若写成 {@code required = true}，Spring 会在 body 为空时抛
 * {@code HttpMessageNotReadableException} —— 那虽然是同一条 5001 出口，
 * 但会跳过 DTO 的必填校验，且对「零字段 DTO」（{@code GetUserTotalLikeReq}）
 * 造成 Go 不存在的行为差异：Go 让空 body 通过，这里会拒。
 */
@RestController
@RequestMapping("/like/v1")
public class LikeController {

    private final LikeCountService likeCountService;
    private final LikeStateService likeStateService;
    private final TargetLikerListService targetLikerListService;
    private final UserLikeListService userLikeListService;
    private final UserTotalLikeService userTotalLikeService;
    private final LikeActionService likeActionService;

    public LikeController(LikeCountService likeCountService,
                          LikeStateService likeStateService,
                          TargetLikerListService targetLikerListService,
                          UserLikeListService userLikeListService,
                          UserTotalLikeService userTotalLikeService,
                          LikeActionService likeActionService) {
        this.likeCountService = likeCountService;
        this.likeStateService = likeStateService;
        this.targetLikerListService = targetLikerListService;
        this.userLikeListService = userLikeListService;
        this.userTotalLikeService = userTotalLikeService;
        this.likeActionService = likeActionService;
    }

    /**
     * POST /like/v1/getlikecount —— {@code getlikecountlogic.go}。
     * <b>无 uid</b>（公开读），故签名里没有 HttpServletRequest。
     */
    @PostMapping("/getlikecount")
    public GetLikeCountResp getLikeCount(@RequestBody(required = false) GetLikeCountReq req) {
        return likeCountService.getLikeCount(GoStyleRequest.normalize(req, GetLikeCountReq::new));
    }

    /** POST /like/v1/getlikestate —— {@code getlikestatelogic.go}。<b>需 uid</b>。 */
    @PostMapping("/getlikestate")
    public GetLikeStateResp getLikeState(@RequestBody(required = false) GetLikeStateReq req,
                                         HttpServletRequest httpRequest) {
        return likeStateService.getLikeState(
                GoStyleRequest.normalize(req, GetLikeStateReq::new), httpRequest);
    }

    /**
     * POST /like/v1/gettargetlikerlist —— {@code gettargetlikerlistlogic.go}。
     * <b>无 uid</b>（公开读）。4 个字段<b>全必填</b>，见 {@link GetTargetLikerListReq}。
     */
    @PostMapping("/gettargetlikerlist")
    public GetTargetLikerListResp getTargetLikerList(
            @RequestBody(required = false) GetTargetLikerListReq req) {
        return targetLikerListService.getTargetLikerList(
                GoStyleRequest.normalize(req, GetTargetLikerListReq::new));
    }

    /** POST /like/v1/getuserlikelist —— {@code getuserlikelistlogic.go}。<b>需 uid</b>。 */
    @PostMapping("/getuserlikelist")
    public GetUserLikeListResp getUserLikeList(@RequestBody(required = false) GetUserLikeListReq req,
                                               HttpServletRequest httpRequest) {
        return userLikeListService.getUserLikeList(
                GoStyleRequest.normalize(req, GetUserLikeListReq::new), httpRequest);
    }

    /**
     * POST /like/v1/getusertotallike —— {@code getusertotallikelogic.go}。<b>需 uid</b>。
     * 请求体是<b>零字段</b> DTO，但身份仍来自 JWT。
     */
    @PostMapping("/getusertotallike")
    public GetUserTotalLikeResp getUserTotalLike(@RequestBody(required = false) GetUserTotalLikeReq req,
                                                 HttpServletRequest httpRequest) {
        return userTotalLikeService.getUserTotalLike(
                GoStyleRequest.normalize(req, GetUserTotalLikeReq::new), httpRequest);
    }

    /** POST /like/v1/likeaction —— {@code likeactionlogic.go}。<b>需 uid</b>。 */
    @PostMapping("/likeaction")
    public LikeActionResp likeAction(@RequestBody(required = false) LikeActionReq req,
                                     HttpServletRequest httpRequest) {
        return likeActionService.likeAction(
                GoStyleRequest.normalize(req, LikeActionReq::new), httpRequest);
    }
}