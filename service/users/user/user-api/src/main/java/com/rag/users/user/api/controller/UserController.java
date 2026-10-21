package com.rag.users.user.api.controller;

import com.rag.users.common.http.GoStyleRequest;
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
 *
 * <p><b>全部 9 个端点用 {@code @RequestBody(required = false)}</b>
 * <pre>
 *   Go 出处（三段，全部实测源码，非推断；<b>与 admin 侧分别取证，未套用结论</b> —— AGENTS.md §3.4.3 N-3
 *   明确警告过「admin 侧取参机制不同，不可跨模块套用结论」）：
 *   1. service/user/user/api/internal/handler/user/*.go —— 9 个 handler 一律
 *      {@code var req types.XxxReq} + {@code if err := httpx.Parse(r, &amp;req); err != nil}。
 *   2. go-zero v1.10.2 rest/httpx/requests.go:153 {@code withJsonBody} =
 *      {@code r.ContentLength > 0 && ContentType 含 application/json}。
 *      空 body ⇒ false ⇒ {@code ParseJsonBody} 走 {@code mapping.UnmarshalJsonMap(nil, v)}。
 *   3. user 侧 api/internal/types/types.go：8 个 Req struct、<b>零个</b> {@code validate:} tag、
 *      <b>零个</b> {@code Validate()} 方法；且整个 user 服务<b>从未</b>调 {@code httpx.SetValidator}
 *      ⇒ {@code getValidator()} 返回 nil，{@code httpx.Parse} 的校验环节整体不生效
 *      （即 go-zero 自己的 {@code validation} 框架不介入，只剩 mapping 的 tag 必填）。
 *
 *   <b>⚠️ 2026-10-21 勘误（DRIFT-016）</b>：本段此前断言「{@code UnmarshalJsonMap(nil, v)}
 *   对 struct 是 <b>no-op，不报错</b>，所以 Go 对空 body 一律不报错，req 停在零值 struct，
 *   由 Logic 决定业务码，HTTP 恒 200 + envelope」。
 *
 *   <b>后半句是错的。</b>{@code core/mapping/jsonunmarshaler.go:19-21} 的
 *   {@code UnmarshalJsonMap(m map[string]any, v any)} 收的是<b>有类型的 nil map</b>，
 *   传进 {@code Unmarshal(i any, ...)} 后在 {@code unmarshaler.go:988} 的 type switch 里
 *   <b>命中 {@code case map[string]any}</b>（不会落到 default 的 errUnsupportedType），
 *   于是照常 {@code unmarshalValuer} 遍历全部字段；空 map 下 {@code getValue} 全部落空，
 *   标量必填字段照样走 {@code newInitError} ⇒ {@code field %q is not set}。
 *
 *   <b>实测坐实</b>（probe-go-required-fields.py，2026-10-21）：真空 body（Content-Length: 0）
 *   与 {@code {}} 都返回 {@code 400 field "username" is not set}。
 *
 *   这与 AGENTS.md §3.4.4 DRIFT-009 是<b>同一类病灶</b>：引用了真实文件、但把行为读反了 ——
 *   而且当时正是这个错误结论，让 22 个端点全部设成 {@code required = false} + 零值归一化。
 *   <b>4 个有必填字段的端点（register / login / avatar / avatar/select）现已改走
 *   {@code GoStyleRequest.normalize}</b>，把必填校验加回来。
 * </pre>
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
    // L4 idiomatization（DRIFT-016）：Go 出处 types.go CreateUserReq — username/password/email
    // 三个标量都不含 ,optional ⇒ 必填；extra_info 是 map ⇒ 不校验。
    // 走 GoStyleRequest.normalize 而非手写 if (req == null)，因为「空 body → 零值 struct」
    // 与「必填校验」必须是同一次调用：Go 侧两者都在 httpx.Parse 内发生。
    public RegisterResp register(@RequestBody(required = false) RegisterReq req, HttpServletRequest httpRequest) {
        return userRegisterService.register(GoStyleRequest.normalize(req, RegisterReq::new), httpRequest);
    }

    @PostMapping("/login")
    // L4 idiomatization（DRIFT-016）：Go 出处 types.go LoginReq — username/password 标量必填。
    // 修复前 loginlogic.go 的零值 username/password 直接透传给 rpc、由 rpc 判错返业务码，
    // 与 Go 实际的 400 field "xxx" is not set 不一致。
    public LoginResp login(@RequestBody(required = false) LoginReq req, HttpServletRequest httpRequest) {
        return userLoginService.login(GoStyleRequest.normalize(req, LoginReq::new), httpRequest);
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
    // L4 idiomatization（同 register）：updateuserlogic.go 的零值 UpdateUserReq 直接进业务层。
    public UpdateUserResp updateUser(@RequestBody(required = false) UpdateUserReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            req = new UpdateUserReq();
        }
        return userUpdateService.updateUser(req, httpRequest);
    }

    @PostMapping("/delete")
    // L4 idiomatization（同 logout/get）：Go 端 DeleteUserReq{} 空 body。
    public DeleteUserResp deleteUser(@RequestBody(required = false) DeleteUserReq req, HttpServletRequest httpRequest) {
        return userDeleteService.deleteUser(req, httpRequest);
    }

    @PostMapping("/avatar/select")
    // L4 idiomatization（DRIFT-016）：Go 出处 types.go SelectAvatarReq.HistoryId 无 tag ⇒ 必填。
    public SelectAvatarResp selectAvatar(@RequestBody(required = false) SelectAvatarReq req, HttpServletRequest httpRequest) {
        return avatarSelectService.selectAvatar(GoStyleRequest.normalize(req, SelectAvatarReq::new), httpRequest);
    }

    @PostMapping("/avatar")
    // L4 idiomatization（DRIFT-016）：Go 出处 types.go UploadAvatarReq.AvatarUrl 无 tag ⇒ 必填；
    // content_type / size_bytes 是 ,optional ⇒ 不校验。
    public UploadAvatarResp uploadAvatar(@RequestBody(required = false) UploadAvatarReq req, HttpServletRequest httpRequest) {
        return avatarUploadService.uploadAvatar(GoStyleRequest.normalize(req, UploadAvatarReq::new), httpRequest);
    }

    @PostMapping("/avatar/history")
    // L4 idiomatization：Go 端 GetAvatarHistoryReq{} 空 struct。
    public GetAvatarHistoryResp getAvatarHistory(@RequestBody(required = false) GetAvatarHistoryReq req, HttpServletRequest httpRequest) {
        return avatarHistoryService.getAvatarHistory(req, httpRequest);
    }
}
