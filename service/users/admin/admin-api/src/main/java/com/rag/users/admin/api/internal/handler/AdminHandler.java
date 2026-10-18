package com.rag.users.admin.api.internal.handler;

import com.rag.common.response.Response;
import com.rag.users.admin.api.internal.logic.BanUserLogic;
import com.rag.users.admin.api.internal.logic.CreateAdminLogic;
import com.rag.users.admin.api.internal.logic.CreateInviteCodeLogic;
import com.rag.users.admin.api.internal.logic.DeleteUserLogic;
import com.rag.users.admin.api.internal.logic.GetSelfLogic;
import com.rag.users.admin.api.internal.logic.GetUserListLogic;
import com.rag.users.admin.api.internal.logic.GetUserLogic;
import com.rag.users.admin.api.internal.logic.LoginLogic;
import com.rag.users.admin.api.internal.logic.LogicResult;
import com.rag.users.admin.api.internal.logic.LogoutLogic;
import com.rag.users.admin.api.internal.logic.ResetUserPasswordLogic;
import com.rag.users.admin.api.internal.logic.UnBanUserLogic;
import com.rag.users.admin.api.internal.logic.UpdateSelfLogic;
import com.rag.users.admin.api.internal.logic.UpdateUserLogic;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.common.errmsg.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对应 Go 端 internal/handler/routes.go + internal/handler/admin/*handler.go 的 13 个 endpoint。
 *
 * <p>L4 实现机制差异：Go 端把"路由表"和"endpoint 样板"分两个文件（routes.go 装 AddRoutes，
 * 13 个 handler 文件装 parse+delegate 样板）；Java 侧把两件事合到本类 —— 类级
 * {@code @RequestMapping("/admincenter/v1/admin")} 对应 Go 端 {@code rest.WithPrefix("/admincenter/v1/admin")}，
 * 方法级 {@code @PostMapping("/login")} 对应 Go 端 Route{Method, Path, Handler}。
 *
 * <p>L1 外部契约冻结（method + path 1:1 对齐 Go 端 routes.go）：
 * <pre>
 *   POST   /admincenter/v1/admin/create               → CreateAdminHandler      (public)
 *   POST   /admincenter/v1/admin/login                → LoginHandler            (public)
 *   POST   /admincenter/v1/admin/banuser              → BanuserHandler          (JWT)
 *   DELETE /admincenter/v1/admin/delete               → DeleteHandler           (JWT)
 *   POST   /admincenter/v1/admin/invite/create        → CreateinviteHandler     (JWT)
 *   GET    /admincenter/v1/admin/getself              → GetselfHandler          (JWT)
 *   GET    /admincenter/v1/admin/getuser              → GetuserHandler          (JWT)
 *   GET    /admincenter/v1/admin/getuserlist          → GetuserlistHandler      (JWT)
 *   POST   /admincenter/v1/admin/logout               → LogoutHandler           (JWT)
 *   POST   /admincenter/v1/admin/resetuserpassword    → ResetuserpasswordHandler(JWT)
 *   POST   /admincenter/v1/admin/unbanuser            → UnbanuserHandler        (JWT)
 *   POST   /admincenter/v1/admin/update               → UpdateHandler           (JWT)
 *   POST   /admincenter/v1/admin/updateuser           → UpdateuserHandler       (JWT)
 * </pre>
 *
 * <p>JWT 由 {@link com.rag.users.admin.api.internal.filter.JwtAuthenticationFilter} 拦截，
 * 黑名单由 {@link com.rag.users.admin.api.internal.filter.CheckBlacklistFilter} 拦截；
 * 本类只负责 envelope 包装（与 Go 端 handler.go 末尾 {@code httpx.OkJson(w, &response.Response{...})} 一致）。
 *
 * <p><b>本轮交付状态</b>：只实装 {@code POST /admincenter/v1/admin/login}（一个 RPC）。
 * 其它 12 个 endpoint 待后续轮次按 Go 端 handler.go + logic.go 1:1 迁移；每轮加一个 RPC 即可。
 */
@RestController
@RequestMapping("/admincenter/v1/admin")
public class AdminHandler {

    private final LoginLogic loginLogic;
    private final LogoutLogic logoutLogic;
    private final GetSelfLogic getSelfLogic;
    private final GetUserLogic getUserLogic;
    private final CreateAdminLogic createAdminLogic;
    private final BanUserLogic banUserLogic;
    private final UnBanUserLogic unBanUserLogic;
    private final DeleteUserLogic deleteUserLogic;
    private final ResetUserPasswordLogic resetUserPasswordLogic;
    private final UpdateUserLogic updateUserLogic;
    private final UpdateSelfLogic updateSelfLogic;
    private final GetUserListLogic getUserListLogic;
    private final CreateInviteCodeLogic createInviteCodeLogic;

    public AdminHandler(LoginLogic loginLogic,
                        LogoutLogic logoutLogic,
                        GetSelfLogic getSelfLogic,
                        GetUserLogic getUserLogic,
                        GetUserListLogic getUserListLogic,
                        CreateAdminLogic createAdminLogic,
                        BanUserLogic banUserLogic,
                        UnBanUserLogic unBanUserLogic,
                        DeleteUserLogic deleteUserLogic,
                        ResetUserPasswordLogic resetUserPasswordLogic,
                        UpdateUserLogic updateUserLogic,
                        UpdateSelfLogic updateSelfLogic,
                        CreateInviteCodeLogic createInviteCodeLogic) {
        this.loginLogic = loginLogic;
        this.logoutLogic = logoutLogic;
        this.getSelfLogic = getSelfLogic;
        this.getUserLogic = getUserLogic;
        this.getUserListLogic = getUserListLogic;
        this.createAdminLogic = createAdminLogic;
        this.banUserLogic = banUserLogic;
        this.unBanUserLogic = unBanUserLogic;
        this.deleteUserLogic = deleteUserLogic;
        this.resetUserPasswordLogic = resetUserPasswordLogic;
        this.updateUserLogic = updateUserLogic;
        this.updateSelfLogic = updateSelfLogic;
        this.createInviteCodeLogic = createInviteCodeLogic;
    }

    // ============== public（无 JWT） ==============

    /**
     * 对应 Go {@code routes.go:25-26} POST /admin/login（public，无 JWT）。
     */
    @PostMapping("/login")
    public Response login(@RequestBody ApiTypes.LoginReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.LoginResp> r = loginLogic.login(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:18-21} POST /admin/create（public，无 JWT）。
     *
     * <p>权限校验在 admin-rpc 端做：用 invite_code 校验调用方是否有权限创建 admin（PermissionDenied → 1016）。
     * 本 HTTP 层不做任何 auth —— 5 个字段全部来自 JSON body（username/password/email/invite_code/extra_info）。
     */
    @PostMapping("/create")
    public Response createAdmin(@RequestBody ApiTypes.CreateAdminReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.CreateAdminResp> r = createAdminLogic.createAdmin(req, httpRequest);
        return envelope(r);
    }

    // ============== JWT-protected ==============

    /**
     * 对应 Go {@code routes.go:67-70} POST /admin/logout。
     *
     * <p>空 body（{@link ApiTypes.LogoutReq} 无字段）—— 不带 {@code @RequestBody}，
     * 行为对齐 Go 端 {@code httpx.Parse(r, &req)} 对空 struct 的处理（无 body → 无错）。
     *
     * <p>Token 不从 body 取，从 request attribute {@code "jwt_token"} 取
     * （由 {@link com.rag.users.admin.api.internal.filter.CheckBlacklistFilter}
     * 在 JWT 验签通过后写入），对应 Go 端 ctx.Value("jwt_token")。
     */
    @PostMapping("/logout")
    public Response logout(ApiTypes.LogoutReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.LogoutResp> r = logoutLogic.logout(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:52-55} GET /admin/getself。
     *
     * <p>空 body（{@link ApiTypes.GetSelfReq} 无字段）—— 不带 {@code @RequestBody}，
     * 行为对齐 Go 端 {@code httpx.Parse(r, &req)} 对空 struct 的处理（无 body → 无错）。
     *
     * <p>UID 不从 body / query 取，从 JWT claim {@code "userId"}（写入 request attribute {@code "userId"}）取，
     * 由 {@link com.rag.users.admin.api.internal.identity.IdentityResolver#claimedUID(HttpServletRequest)} 解析。
     */
    @GetMapping("/getself")
    public Response getSelf(ApiTypes.GetSelfReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.GetSelfResp> r = getSelfLogic.getSelf(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:57-60} GET /admin/getuser。
     *
     * <p>Query 参数：{@code ?uid=xxx}（Go 端 form:"uid,string"）—— 不是 JSON body。
     * 1:1 复刻 Go 第 19-23 行 handler 用 {@code httpx.Parse(r, &req)} 解析 query string。
     */
    @GetMapping("/getuser")
    public Response getUser(@RequestParam("uid") String uid, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.GetUserResp> r = getUserLogic.getUser(uid, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:62-66} GET /admin/getuserlist。
     *
     * <p>Query 参数（3 个，Go 端 form tag + default）：
     * <ul>
     *   <li>{@code ?page=1}（default 1）</li>
     *   <li>{@code ?page_size=20}（default 20）</li>
     *   <li>{@code ?keyword=xxx}（optional）</li>
     * </ul>
     * 1:1 复刻 Go 端 httpx.Parse 对 query string 的解析行为。
     */
    @GetMapping("/getuserlist")
    public Response getUserList(
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "page_size", defaultValue = "20") long pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest httpRequest) {
        ApiTypes.GetUserListReq req = new ApiTypes.GetUserListReq();
        req.setPage(page);
        req.setPageSize(pageSize);
        req.setKeyword(keyword);
        LogicResult<ApiTypes.GetUserListResp> r = getUserListLogic.getUserList(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:37-40} POST /admin/banuser。
     *
     * <p>请求体：{@link ApiTypes.BanUserReq}，必填字段 {@code uid}（json:"uid,string"，序列化为字符串避免前端 number 精度丢失）。
     * 操作者身份（{@code operator_id}）由
     * {@link com.rag.users.admin.api.internal.identity.IdentityResolver#claimedUID(HttpServletRequest)} 解析
     * JWT claim，<b>不</b>来自 body / query。
     */
    @PostMapping("/banuser")
    public Response banUser(@RequestBody ApiTypes.BanUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.BanUserResp> r = banUserLogic.banUser(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:77-80} POST /admin/unbanuser。
     *
     * <p>请求体：{@link ApiTypes.UnBanUserReq}，必填字段 {@code uid}（json:"uid,string"）。
     * 操作者身份（{@code operator_id}）由
     * {@link com.rag.users.admin.api.internal.identity.IdentityResolver#claimedUID(HttpServletRequest)} 解析 JWT claim。
     */
    @PostMapping("/unbanuser")
    public Response unBanUser(@RequestBody ApiTypes.UnBanUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.UnBanUserResp> r = unBanUserLogic.unBanUser(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:41-44} DELETE /admin/delete。
     *
     * <p>请求体：{@link ApiTypes.DeleteUserReq}，必填字段 {@code uid}。
     */
    @DeleteMapping("/delete")
    public Response deleteUser(@RequestBody ApiTypes.DeleteUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.DeleteUserResp> r = deleteUserLogic.deleteUser(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:71-75} POST /admin/resetuserpassword。
     *
     * <p>请求体：{@link ApiTypes.ResetUserPasswordReq}，必填字段 {@code uid}。
     */
    @PostMapping("/resetuserpassword")
    public Response resetUserPassword(@RequestBody ApiTypes.ResetUserPasswordReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.ResetUserPasswordResp> r = resetUserPasswordLogic.resetUserPassword(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:81-84} POST /admin/updateuser。
     *
     * <p>请求体：{@link ApiTypes.UpdateUserReq}，必填字段 {@code uid}，可选 username/password/email/extra_info。
     * 至少传一个可改字段（admin-rpc 端 Go 行为会因 password/email 为空而跳过相关更新）。
     */
    @PostMapping("/updateuser")
    public Response updateUser(@RequestBody ApiTypes.UpdateUserReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.UpdateUserResp> r = updateUserLogic.updateUser(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:86-90} POST /admin/update（admin 改自己）。
     *
     * <p>请求体：{@link ApiTypes.UpdateSelfReq}，可选字段 username/password/email/extra_info。
     * <b>uid 不在 body 里</b>，从 JWT claim 解析（admin 只能改自己）—— 对应 Go ctx.Value("userId")。
     * 操作者身份就是被操作者（admin 改自己），没有 operator_id 概念。
     */
    @PostMapping("/update")
    public Response updateSelf(@RequestBody ApiTypes.UpdateSelfReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.UpdateSelfResp> r = updateSelfLogic.updateSelf(req, httpRequest);
        return envelope(r);
    }

    /**
     * 对应 Go {@code routes.go:46-50} POST /admin/invite/create。
     *
     * <p>请求体：{@link ApiTypes.CreateInviteReq}（空 struct，无字段）—— 不带 {@code @RequestBody}，
     * 行为对齐 Go 端 {@code httpx.Parse(r, &req)} 对空 struct 的处理（无 body → 无错）。
     *
     * <p>操作者身份（{@code operator_id}）从 JWT claim 解析 —— admin 只能为自己创建 invite code（用来再创建新 admin）。
     * invite_code 由 admin-rpc 端用 SecureRandom 生成（"ADM-" + 16 字符 hex 大写，10 分钟 TTL）。
     */
    @PostMapping("/invite/create")
    public Response createInvite(ApiTypes.CreateInviteReq req, HttpServletRequest httpRequest) {
        LogicResult<ApiTypes.CreateInviteResp> r = createInviteCodeLogic.createInvite(req, httpRequest);
        return envelope(r);
    }

    // ============== envelope 包装（对齐 Go 端 admin/handler/admin/loginhandler.go:27-31） ==============

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