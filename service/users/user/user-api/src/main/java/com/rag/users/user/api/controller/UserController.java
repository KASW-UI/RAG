package com.rag.users.user.api.controller;

import com.rag.users.user.api.dto.DeleteUserReq;
import com.rag.users.user.api.dto.DeleteUserResp;
import com.rag.users.user.api.dto.GetAvatarHistoryReq;
import com.rag.users.user.api.dto.GetAvatarHistoryResp;
import com.rag.users.user.api.dto.GetUserReq;
import com.rag.users.user.api.dto.GetUserResp;
import com.rag.users.user.api.dto.LoginReq;
import com.rag.users.user.api.dto.LoginResp;
import com.rag.users.user.api.dto.LogoutReq;
import com.rag.users.user.api.dto.LogoutResp;
import com.rag.users.user.api.dto.RegisterReq;
import com.rag.users.user.api.dto.RegisterResp;
import com.rag.users.user.api.dto.SelectAvatarReq;
import com.rag.users.user.api.dto.SelectAvatarResp;
import com.rag.users.user.api.dto.UpdateUserReq;
import com.rag.users.user.api.dto.UpdateUserResp;
import com.rag.users.user.api.dto.UploadAvatarReq;
import com.rag.users.user.api.dto.UploadAvatarResp;
import com.rag.users.user.api.service.AvatarHistoryService;
import com.rag.users.user.api.service.AvatarSelectService;
import com.rag.users.user.api.service.AvatarUploadService;
import com.rag.users.user.api.service.UserDeleteService;
import com.rag.users.user.api.service.UserGetService;
import com.rag.users.user.api.service.UserLoginService;
import com.rag.users.user.api.service.UserLogoutService;
import com.rag.users.user.api.service.UserRegisterService;
import com.rag.users.user.api.service.UserUpdateService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Center HTTP gateway —— 9 个 REST 端点（与 Go 端 handler 1:1 对应）。
 *
 * 对应 Go: service/user/user/api/internal/handler/routes.go + internal/handler/user/*handler.go。
 *
 * <p>L1 外部契约冻结（HTTP method + path 1:1 对齐 Go 端 routes.go，但按 HUMAN_ONLY.MD 风格用 {@code /user/v1} 前缀）：
 * <pre>
 *   POST /user/v1/register          → UserRegisterService       (public)
 *   POST /user/v1/login             → UserLoginService          (public)
 *   POST /user/v1/logout            → UserLogoutService         (JWT)
 *   POST /user/v1/get               → UserGetService            (JWT)
 *   POST /user/v1/update            → UserUpdateService         (JWT)
 *   POST /user/v1/delete            → UserDeleteService         (JWT)
 *   POST /user/v1/avatar/select     → AvatarSelectService       (JWT)
 *   POST /user/v1/avatar            → AvatarUploadService       (JWT)
 *   POST /user/v1/avatar/history    → AvatarHistoryService      (JWT)
 * </pre>
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>Go 端用 go-zero HTTP method 多样（POST/GET/DELETE）；HUMAN_ONLY.MD 决定统一用 POST，
 *       取消 method 区别带来的客户端 SDK 复杂度</li>
 *   <li>Controller 只负责"接 DTO → 委托 Service → 返回 DTO"三步；envelope 包装由
 *       {@code GlobalResponseAdvice} 统一处理；异常处理由 {@code GlobalExceptionHandler} 统一处理</li>
 *   <li>JWT 鉴权由 {@code JwtAuthInterceptor} 在进入 Controller 之前完成；userId / jwt_token
 *       写到 request attribute，Service 直接读</li>
 * </ul>
 */
@RestController
@RequestMapping("/user/v1")
public class UserController {

    private final UserRegisterService userRegisterService;
    private final UserLoginService userLoginService;
    private final UserLogoutService userLogoutService;
    private final UserGetService userGetService;
    private final UserUpdateService userUpdateService;
    private final UserDeleteService userDeleteService;
    private final AvatarSelectService avatarSelectService;
    private final AvatarUploadService avatarUploadService;
    private final AvatarHistoryService avatarHistoryService;

    public UserController(UserRegisterService userRegisterService,
                          UserLoginService userLoginService,
                          UserLogoutService userLogoutService,
                          UserGetService userGetService,
                          UserUpdateService userUpdateService,
                          UserDeleteService userDeleteService,
                          AvatarSelectService avatarSelectService,
                          AvatarUploadService avatarUploadService,
                          AvatarHistoryService avatarHistoryService) {
        this.userRegisterService = userRegisterService;
        this.userLoginService = userLoginService;
        this.userLogoutService = userLogoutService;
        this.userGetService = userGetService;
        this.userUpdateService = userUpdateService;
        this.userDeleteService = userDeleteService;
        this.avatarSelectService = avatarSelectService;
        this.avatarUploadService = avatarUploadService;
        this.avatarHistoryService = avatarHistoryService;
    }

    // ============== public（无 JWT） ==============

    @PostMapping("/register")
    public RegisterResp register(@RequestBody RegisterReq req, HttpServletRequest httpRequest) {
        return userRegisterService.register(req, httpRequest);
    }

    @PostMapping("/login")
    public LoginResp login(@RequestBody LoginReq req, HttpServletRequest httpRequest) {
        return userLoginService.login(req, httpRequest);
    }

    // ============== JWT-protected（7 个） ==============

    @PostMapping("/logout")
    // L4 idiomatization（强制留痕 + Go ↔ RAG 对照）：
    //   Go 出处：service/user/user/api/internal/handler/user/logouthandler.go —— Go 端
    //     handler.Entries() 取 `LogoutReq{}` 空 struct，未要求请求带 body；
    //   改 required=false 后空 body 的 POST /user/v1/logout 与 Go 端等价调用（uid 走 JWT）。
    //   语义等价论证：本端从 JWT attribute 取操作人，body 字段不参与，故 body 缺失不影响业务分支。
    public LogoutResp logout(@RequestBody(required = false) LogoutReq req, HttpServletRequest httpRequest) {
        return userLogoutService.logout(req, httpRequest);
    }

    @PostMapping("/get")
    // L4 idiomatization：Go 端 handler 取的是空 struct（uid 由调用方走 JWT 解析逻辑，
    // 不在 body），故 required=false；Java 端由 query/body 改为走 JWT 解析只取 uid，
    // body 不再携带任何字段。
    public GetUserResp getUser(@RequestBody(required = false) GetUserReq req, HttpServletRequest httpRequest) {
        return userGetService.getUser(req, httpRequest);
    }

    @PostMapping("/update")
    public UpdateUserResp updateUser(@RequestBody UpdateUserReq req, HttpServletRequest httpRequest) {
        return userUpdateService.updateUser(req, httpRequest);
    }

    @PostMapping("/delete")
    // L4 idiomatization（同 logout/get）：Go 端 DeleteUserReq{} 空 body。
    public DeleteUserResp deleteUser(@RequestBody(required = false) DeleteUserReq req, HttpServletRequest httpRequest) {
        return userDeleteService.deleteUser(req, httpRequest);
    }

    @PostMapping("/avatar/select")
    public SelectAvatarResp selectAvatar(@RequestBody SelectAvatarReq req, HttpServletRequest httpRequest) {
        return avatarSelectService.selectAvatar(req, httpRequest);
    }

    @PostMapping("/avatar")
    public UploadAvatarResp uploadAvatar(@RequestBody UploadAvatarReq req, HttpServletRequest httpRequest) {
        return avatarUploadService.uploadAvatar(req, httpRequest);
    }

    @PostMapping("/avatar/history")
    // L4 idiomatization：Go 端 GetAvatarHistoryReq{} 空 struct。
    public GetAvatarHistoryResp getAvatarHistory(@RequestBody(required = false) GetAvatarHistoryReq req, HttpServletRequest httpRequest) {
        return avatarHistoryService.getAvatarHistory(req, httpRequest);
    }
}
