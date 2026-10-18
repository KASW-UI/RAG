package com.rag.users.user.api.internal.handler;

import com.rag.common.response.Response;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.internal.logic.DeleteUserLogic;
import com.rag.users.user.api.internal.logic.GetAvatarHistoryLogic;
import com.rag.users.user.api.internal.logic.GetUserLogic;
import com.rag.users.user.api.internal.logic.LoginLogic;
import com.rag.users.user.api.internal.logic.LogoutLogic;
import com.rag.users.user.api.internal.logic.LogicResult;
import com.rag.users.user.api.internal.logic.RegisterLogic;
import com.rag.users.user.api.internal.logic.SelectAvatarLogic;
import com.rag.users.user.api.internal.logic.UpdateUserLogic;
import com.rag.users.user.api.internal.logic.UploadAvatarLogic;
import com.rag.users.user.api.internal.types.ApiTypes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对应 Go 端 internal/handler/routes.go + internal/handler/user/*handler.go 的 9 个 endpoint。
 *
 * <p>L4 实现机制差异：Go 端把"路由表"和"endpoint 样板"分两个文件（routes.go 装 AddRoutes，
 * 9 个 handler 文件装 parse+delegate 样板）；Java 侧把两件事合到本类 —— 类级
 * {@code @RequestMapping("/usercenter/v1")} 对应 Go 端 {@code rest.WithPrefix("/usercenter/v1")}，
 * 方法级 {@code @PostMapping("/user/login")} 对应 Go 端 Route{Method, Path, Handler}。
 *
 * <p>L1 外部契约冻结（method + path 1:1 对齐 Go 端 routes.go）：
 * <pre>
 *   POST   /usercenter/v1/user/login             → LoginHandler           (public)
 *   POST   /usercenter/v1/user/register          → RegisterHandler        (public)
 *   DELETE /usercenter/v1/user/delete            → DeleteuserHandler      (JWT)
 *   GET    /usercenter/v1/user/get               → GetuserHandler         (JWT)
 *   GET    /usercenter/v1/user/avatar/history    → GetavatarhistoryHandler (JWT)
 *   POST   /usercenter/v1/user/logout            → LogoutHandler          (JWT)
 *   POST   /usercenter/v1/user/avatar            → UploadavatarHandler    (JWT)
 *   POST   /usercenter/v1/user/avatar/select     → SelectavatarHandler    (JWT)
 *   POST   /usercenter/v1/user/update            → UpdateuserHandler      (JWT)
 * </pre>
 *
 * <p>JWT 由 {@link com.rag.users.user.api.internal.filter.JwtAuthenticationFilter} 拦截，
 * 黑名单由 {@link com.rag.users.user.api.internal.filter.CheckBlacklistFilter} 拦截；
 * 本类只负责 envelope 包装（与 Go 端 handler.go 末尾 {@code httpx.OkJson(w, &response.Response{...})} 一致）。
 */
@RestController
@RequestMapping("/usercenter/v1/user")
public class UserHandler {

    private final LoginLogic loginLogic;
    private final RegisterLogic registerLogic;
    private final LogoutLogic logoutLogic;
    private final GetUserLogic getUserLogic;
    private final UpdateUserLogic updateUserLogic;
    private final DeleteUserLogic deleteUserLogic;
    private final UploadAvatarLogic uploadAvatarLogic;
    private final GetAvatarHistoryLogic getAvatarHistoryLogic;
    private final SelectAvatarLogic selectAvatarLogic;

    public UserHandler(LoginLogic loginLogic,
                       RegisterLogic registerLogic,
                       LogoutLogic logoutLogic,
                       GetUserLogic getUserLogic,
                       UpdateUserLogic updateUserLogic,
                       DeleteUserLogic deleteUserLogic,
                       UploadAvatarLogic uploadAvatarLogic,
                       GetAvatarHistoryLogic getAvatarHistoryLogic,
                       SelectAvatarLogic selectAvatarLogic) {
        this.loginLogic = loginLogic;
        this.registerLogic = registerLogic;
        this.logoutLogic = logoutLogic;
        this.getUserLogic = getUserLogic;
        this.updateUserLogic = updateUserLogic;
        this.deleteUserLogic = deleteUserLogic;
        this.uploadAvatarLogic = uploadAvatarLogic;
        this.getAvatarHistoryLogic = getAvatarHistoryLogic;
        this.selectAvatarLogic = selectAvatarLogic;
    }

    // ============== public（无 JWT） ==============

    /**
     * 对应 Go {@code routes.go:20-21} POST /user/login（public，无 JWT）。
     */
    @PostMapping("/login")
    public Response login(@RequestBody ApiTypes.LoginReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.LoginResp> r = loginLogic.login(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:25-26} POST /user/register（public，无 JWT）。
     */
    @PostMapping("/register")
    public Response register(@RequestBody ApiTypes.CreateUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.CreateUserResp> r = registerLogic.register(req, httpRequest);
        return envelope(r);
    }

    // ============== JWT-protected（7 个） ==============

    /**
     * 对应 Go {@code routes.go:39-40} DELETE /user/delete。
     * 空 body（{@link ApiTypes.DeleteUserReq} 无字段）—— 不带 {@code @RequestBody}，
     * 行为对齐 Go 端 {@code httpx.Parse(r, &req)} 对空 struct 的处理（无 body → 无错）。
     */
    @DeleteMapping("/delete")
    public Response deleteUser(ApiTypes.DeleteUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.DeleteUserResp> r = deleteUserLogic.deleteUser(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:43-44} GET /user/get。
     * 空 body —— 同上不带 {@code @RequestBody}。
     */
    @GetMapping("/get")
    public Response getUser(ApiTypes.GetUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.GetUserResp> r = getUserLogic.getUser(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:48-49} GET /user/avatar/history。
     * 空 body —— 同上不带 {@code @RequestBody}。
     */
    @GetMapping("/avatar/history")
    public Response getAvatarHistory(ApiTypes.AvatarHistoryResp req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.AvatarHistoryResp> r = getAvatarHistoryLogic.getAvatarHistory(httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:53-54} POST /user/logout。
     * 空 body —— 同上不带 {@code @RequestBody}。
     */
    @PostMapping("/logout")
    public Response logout(ApiTypes.LogoutReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.LogoutResp> r = logoutLogic.logout(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:58-59} POST /user/avatar。
     */
    @PostMapping("/avatar")
    public Response uploadAvatar(@RequestBody ApiTypes.UploadAvatarReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.UploadAvatarResp> r = uploadAvatarLogic.uploadAvatar(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:62-63} POST /user/avatar/select。
     */
    @PostMapping("/avatar/select")
    public Response selectAvatar(@RequestBody ApiTypes.SelectAvatarReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.SelectAvatarResp> r = selectAvatarLogic.selectAvatar(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:67-68} POST /user/update。
     */
    @PostMapping("/update")
    public Response updateUser(@RequestBody ApiTypes.UpdateUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.UpdateUserResp> r = updateUserLogic.updateUser(req, httpRequest);
        return envelope(r);
    }

    // ============== envelope 包装（对齐 Go 端 handler.go:27-31） ==============

    /**
     * 对应 Go 端 handler.go 末尾：
     * <pre>
     *   httpx.OkJson(w, &response.Response{
     *       Code: code,
     *       Msg:  errmsg.GetErrMsg(code),
     *       Data: resp,
     *   })
     * </pre>
     *
     * <p>L1 envelope 冻结：{@code {code, msg, data}} 三字段，msg 由 {@code ErrorCode.getMsg(code)} 决定。
     */
    private static <T> Response envelope(LogicResult<T> r) {
        int code = r.code();
        String msg = ErrorCode.getMsg(code);
        return new Response(code, msg, r.data());
    }
}