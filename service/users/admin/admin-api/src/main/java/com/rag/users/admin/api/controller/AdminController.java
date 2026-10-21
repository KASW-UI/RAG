package com.rag.users.admin.api.controller;

import com.rag.users.admin.api.dto.BanUserReq;
import com.rag.users.admin.api.dto.BanUserResp;
import com.rag.users.admin.api.dto.CreateAdminReq;
import com.rag.users.admin.api.dto.CreateAdminResp;
import com.rag.users.admin.api.dto.CreateInviteCodeReq;
import com.rag.users.admin.api.dto.CreateInviteCodeResp;
import com.rag.users.admin.api.dto.DeleteUserReq;
import com.rag.users.admin.api.dto.DeleteUserResp;
import com.rag.users.admin.api.dto.GetSelfReq;
import com.rag.users.admin.api.dto.GetSelfResp;
import com.rag.users.admin.api.dto.GetUserListReq;
import com.rag.users.admin.api.dto.GetUserListResp;
import com.rag.users.admin.api.dto.GetUserReq;
import com.rag.users.admin.api.dto.GetUserResp;
import com.rag.users.admin.api.dto.LoginReq;
import com.rag.users.admin.api.dto.LoginResp;
import com.rag.users.admin.api.dto.LogoutReq;
import com.rag.users.admin.api.dto.LogoutResp;
import com.rag.users.admin.api.dto.ResetUserPasswordReq;
import com.rag.users.admin.api.dto.ResetUserPasswordResp;
import com.rag.users.admin.api.dto.UnBanUserReq;
import com.rag.users.admin.api.dto.UnBanUserResp;
import com.rag.users.admin.api.dto.UpdateSelfReq;
import com.rag.users.admin.api.dto.UpdateSelfResp;
import com.rag.users.admin.api.dto.UpdateUserReq;
import com.rag.users.admin.api.dto.UpdateUserResp;
import com.rag.users.admin.api.service.AdminBanUserService;
import com.rag.users.admin.api.service.AdminCreateInviteCodeService;
import com.rag.users.admin.api.service.AdminCreateService;
import com.rag.users.admin.api.service.AdminDeleteUserService;
import com.rag.users.admin.api.service.AdminGetSelfService;
import com.rag.users.admin.api.service.AdminGetUserService;
import com.rag.users.admin.api.service.AdminListUserService;
import com.rag.users.admin.api.service.AdminLoginService;
import com.rag.users.admin.api.service.AdminLogoutService;
import com.rag.users.admin.api.service.AdminResetUserPasswordService;
import com.rag.users.admin.api.service.AdminUnBanUserService;
import com.rag.users.admin.api.service.AdminUpdateSelfService;
import com.rag.users.admin.api.service.AdminUpdateUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Center HTTP gateway —— 13 个 REST 端点（与 Go 端 handler 1:1 对应）。
 *
 * 对应 Go: service/user/admin/api/internal/handler/routes.go + internal/handler/admin/*handler.go。
 *
 * <p>L1 外部契约冻结（HTTP method + path 1:1 对齐 Go 端 routes.go，但按 HUMAN_ONLY.MD 风格用 {@code /admin/v1} 前缀）：
 * <pre>
 *   POST /admin/v1/login             → AdminLoginService               (public)
 *   POST /admin/v1/create            → AdminCreateService               (public)
 *   POST /admin/v1/logout            → AdminLogoutService               (JWT)
 *   POST /admin/v1/getself           → AdminGetSelfService              (JWT)
 *   POST /admin/v1/update            → AdminUpdateSelfService           (JWT)
 *   POST /admin/v1/updateuser        → AdminUpdateUserService           (JWT)
 *   POST /admin/v1/delete            → AdminDeleteUserService           (JWT)
 *   POST /admin/v1/resetuserpassword → AdminResetUserPasswordService    (JWT)
 *   POST /admin/v1/banuser           → AdminBanUserService              (JWT)
 *   POST /admin/v1/unbanuser         → AdminUnBanUserService            (JWT)
 *   POST /admin/v1/getuser           → AdminGetUserService              (JWT)
 *   POST /admin/v1/getuserlist       → AdminListUserService             (JWT)
 *   POST /admin/v1/invite/create     → AdminCreateInviteCodeService     (JWT)
 * </pre>
 *
 * <p>Method 统一用 POST（HUMAN_ONLY.MD 决策）—— 取消 method 区别带来的客户端 SDK 复杂度。
 *
 * <p><b>L4 idiomatization：全部 13 个端点用 {@code @RequestBody(required = false)}</b>
 * <pre>
 *   Go 出处（三段，全部实测源码，非推断）：
 *   1. service/user/admin/api/internal/handler/admin/*.go —— 13 个 handler 一律
 *      {@code if err := httpx.Parse(r, &req); err != nil}，req 是包级 {@code var req types.XxxReq}。
 *   2. go-zero v1.10.2 rest/httpx/requests.go:153 {@code withJsonBody} =
 *      {@code r.ContentLength > 0 && ContentType 含 application/json}。
 *      空 body ⇒ {@code withJsonBody} 为 false ⇒ 走 {@code ParseJsonBody} 的
 *      {@code mapping.UnmarshalJsonMap(nil, v)} 分支 —— 对 struct 是 <b>no-op，不报错</b>。
 *   3. admin 侧 types.go 共 13 个 Req struct，<b>零个</b> {@code Validate()} 方法、
 *      <b>零个</b> {@code validate:} tag；admin 服务也<b>从未</b>调 {@code httpx.SetValidator}
 *      ⇒ {@code getValidator()} 返回 nil，{@code httpx.Parse} 的校验环节整体不生效。
 *
 *   结论：Go 对空 body 的 POST 一律 <b>不报错</b>，req 停在<b>零值 struct</b>，
 *   由各 Logic 自己决定业务码。以 login 为例（loginlogic.go:37-56）：
 *   零值 username/password <b>直接透传给 rpc</b>，由 rpc 判定
 *   NotFound → 1003 / Unauthenticated → 1008，<b>HTTP 恒 200 + envelope</b>。
 *
 *   语义等价论证：{@code required=false} + Controller 侧把 null 归一化成 new XxxReq()，
 *   得到的正是 Go 的零值 struct，业务码由 Service 决定，与 Go 逐条一致。
 *
 *   <b>修复前</b>：13 个端点全是 {@code required=true}，空 body 触发
 *   {@code HttpMessageNotReadableException} → {@code handleUnreadableBody} → <b>HTTP 400 + 空 body</b>。
 *   与 Go 的「200 + 业务码」不一致，且客户端拿不到任何可读的错误原因。
 * </pre>
 *
 * <p><b>与 user-api 的差异说明</b>：user-api 侧只把 body 字段不参与业务的 4 个端点
 * 改成了 {@code required=false}（logout / get / delete / avatar/history），
 * 其余 5 个仍为 {@code required=true}。按上面第 3 条取证，Go 对 login / register / update
 * 等端点的空 body 同样不报错，因此 user-api 那 5 个端点<b>仍与 Go 存在同一处漂移</b>，
 * 本轮未一并处理（见 AGENTS.md §6.2 待办）。
 */
@RestController
@RequestMapping("/admin/v1")
public class AdminController {

    private final AdminLoginService adminLoginService;
    private final AdminLogoutService adminLogoutService;
    private final AdminGetSelfService adminGetSelfService;
    private final AdminUpdateSelfService adminUpdateSelfService;
    private final AdminCreateService adminCreateService;
    private final AdminCreateInviteCodeService adminCreateInviteCodeService;
    private final AdminBanUserService adminBanUserService;
    private final AdminUnBanUserService adminUnBanUserService;
    private final AdminGetUserService adminGetUserService;
    private final AdminListUserService adminListUserService;
    private final AdminUpdateUserService adminUpdateUserService;
    private final AdminDeleteUserService adminDeleteUserService;
    private final AdminResetUserPasswordService adminResetUserPasswordService;

    public AdminController(AdminLoginService adminLoginService,
                           AdminLogoutService adminLogoutService,
                           AdminGetSelfService adminGetSelfService,
                           AdminUpdateSelfService adminUpdateSelfService,
                           AdminCreateService adminCreateService,
                           AdminCreateInviteCodeService adminCreateInviteCodeService,
                           AdminBanUserService adminBanUserService,
                           AdminUnBanUserService adminUnBanUserService,
                           AdminGetUserService adminGetUserService,
                           AdminListUserService adminListUserService,
                           AdminUpdateUserService adminUpdateUserService,
                           AdminDeleteUserService adminDeleteUserService,
                           AdminResetUserPasswordService adminResetUserPasswordService) {
        this.adminLoginService = adminLoginService;
        this.adminLogoutService = adminLogoutService;
        this.adminGetSelfService = adminGetSelfService;
        this.adminUpdateSelfService = adminUpdateSelfService;
        this.adminCreateService = adminCreateService;
        this.adminCreateInviteCodeService = adminCreateInviteCodeService;
        this.adminBanUserService = adminBanUserService;
        this.adminUnBanUserService = adminUnBanUserService;
        this.adminGetUserService = adminGetUserService;
        this.adminListUserService = adminListUserService;
        this.adminUpdateUserService = adminUpdateUserService;
        this.adminDeleteUserService = adminDeleteUserService;
        this.adminResetUserPasswordService = adminResetUserPasswordService;
    }

    // ============== public（无 JWT） ==============

    @PostMapping("/login")
    public LoginResp login(@RequestBody(required = false) LoginReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new LoginReq();
        }
        return adminLoginService.login(req, httpRequest);
    }

    @PostMapping("/create")
    public CreateAdminResp createAdmin(@RequestBody(required = false) CreateAdminReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new CreateAdminReq();
        }
        return adminCreateService.createAdmin(req, httpRequest);
    }

    // ============== JWT-protected（11 个） ==============

    @PostMapping("/logout")
    public LogoutResp logout(@RequestBody(required = false) LogoutReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new LogoutReq();
        }
        return adminLogoutService.logout(req, httpRequest);
    }

    @PostMapping("/getself")
    public GetSelfResp getSelf(@RequestBody(required = false) GetSelfReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new GetSelfReq();
        }
        return adminGetSelfService.getSelf(req, httpRequest);
    }

    @PostMapping("/update")
    public UpdateSelfResp updateSelf(@RequestBody(required = false) UpdateSelfReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new UpdateSelfReq();
        }
        return adminUpdateSelfService.updateSelf(req, httpRequest);
    }

    @PostMapping("/updateuser")
    public UpdateUserResp updateUser(@RequestBody(required = false) UpdateUserReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new UpdateUserReq();
        }
        return adminUpdateUserService.updateUser(req, httpRequest);
    }

    @PostMapping("/delete")
    public DeleteUserResp deleteUser(@RequestBody(required = false) DeleteUserReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new DeleteUserReq();
        }
        return adminDeleteUserService.deleteUser(req, httpRequest);
    }

    @PostMapping("/resetuserpassword")
    public ResetUserPasswordResp resetUserPassword(@RequestBody(required = false) ResetUserPasswordReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new ResetUserPasswordReq();
        }
        return adminResetUserPasswordService.resetUserPassword(req, httpRequest);
    }

    @PostMapping("/banuser")
    public BanUserResp banUser(@RequestBody(required = false) BanUserReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new BanUserReq();
        }
        return adminBanUserService.banUser(req, httpRequest);
    }

    @PostMapping("/unbanuser")
    public UnBanUserResp unBanUser(@RequestBody(required = false) UnBanUserReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new UnBanUserReq();
        }
        return adminUnBanUserService.unBanUser(req, httpRequest);
    }

    @PostMapping("/getuser")
    public GetUserResp getUser(@RequestBody(required = false) GetUserReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new GetUserReq();
        }
        return adminGetUserService.getUser(req, httpRequest);
    }

    @PostMapping("/getuserlist")
    public GetUserListResp getUserList(@RequestBody(required = false) GetUserListReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new GetUserListReq();
        }
        return adminListUserService.getUserList(req, httpRequest);
    }

    @PostMapping("/invite/create")
    public CreateInviteCodeResp createInviteCode(@RequestBody(required = false) CreateInviteCodeReq req, HttpServletRequest httpRequest) {
        if (req == null) {
            // Go 出处：httpx.Parse 在 ContentLength == 0 时走 UnmarshalJsonMap(nil, v)，
            // 把 req 留在**零值 struct**（不是 null），且不报错。此处归一化保证与 Go 同构。
            req = new CreateInviteCodeReq();
        }
        return adminCreateInviteCodeService.createInviteCode(req, httpRequest);
    }
}
