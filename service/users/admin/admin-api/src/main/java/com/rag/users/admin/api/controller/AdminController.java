package com.rag.users.admin.api.controller;

import com.rag.users.common.http.GoStyleRequest;
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
 *      {@code mapping.UnmarshalJsonMap(nil, v)} 分支。
 *   3. admin 侧 types.go 共 13 个 Req struct，<b>零个</b> {@code Validate()} 方法、
 *      <b>零个</b> {@code validate:} tag；admin 服务也<b>从未</b>调 {@code httpx.SetValidator}
 *      ⇒ {@code getValidator()} 返回 nil，{@code httpx.Parse} 的校验环节整体不生效
 *      （即 go-zero 自己的 validation 框架不介入，只剩 mapping 的 tag 必填）。
 *
 *   <b>⚠️ 2026-10-21 勘误（DRIFT-016）</b>：本段此前断言「{@code UnmarshalJsonMap(nil, v)}
 *   对 struct 是 <b>no-op，不报错</b>，所以 Go 对空 body 一律不报错，req 停在零值 struct，
 *   由 Logic 自己决定业务码，HTTP 恒 200 + envelope」。
 *
 *   <b>后半句是错的。</b>{@code core/mapping/jsonunmarshaler.go:19-21} 的
 *   {@code UnmarshalJsonMap(m map[string]any, v any)} 收的是<b>有类型的 nil map</b>，
 *   传进 {@code Unmarshal(i any, ...)} 后在 {@code unmarshaler.go:988} 的 type switch 里
 *   <b>命中 {@code case map[string]any}</b>（不会落到 default 的 errUnsupportedType），
 *   于是照常 {@code unmarshalValuer} 遍历全部字段；空 map 下 {@code getValue} 全部落空，
 *   标量必填字段照样走 {@code newInitError} ⇒ {@code field %q is not set}。
 *
 *   <b>实测坐实</b>（probe-go-required-fields.py，2026-10-21）：真空 body（Content-Length: 0）
 *   与 {@code {}} 都返回 {@code 400 field "xxx" is not set}。
 *
 *   这与 AGENTS.md §3.4.4 DRIFT-009 是<b>同一类病灶</b>：引用了真实文件、但把行为读反了 ——
 *   而且当时正是这个错误结论，让 22 个端点全部设成 {@code required = false} + 零值归一化。
 *   <b>10 个有必填字段的端点现已改走 {@code GoStyleRequest.normalize}</b>，
 *   3 个空 struct 端点（logout / getself / invite/create）保持原样。
 * </pre>
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
        return adminLoginService.login(GoStyleRequest.normalize(req, LoginReq::new), httpRequest);
    }

    @PostMapping("/create")
    public CreateAdminResp createAdmin(@RequestBody(required = false) CreateAdminReq req, HttpServletRequest httpRequest) {
        return adminCreateService.createAdmin(GoStyleRequest.normalize(req, CreateAdminReq::new), httpRequest);
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
        return adminUpdateSelfService.updateSelf(GoStyleRequest.normalize(req, UpdateSelfReq::new), httpRequest);
    }

    @PostMapping("/updateuser")
    public UpdateUserResp updateUser(@RequestBody(required = false) UpdateUserReq req, HttpServletRequest httpRequest) {
        return adminUpdateUserService.updateUser(GoStyleRequest.normalize(req, UpdateUserReq::new), httpRequest);
    }

    @PostMapping("/delete")
    public DeleteUserResp deleteUser(@RequestBody(required = false) DeleteUserReq req, HttpServletRequest httpRequest) {
        return adminDeleteUserService.deleteUser(GoStyleRequest.normalize(req, DeleteUserReq::new), httpRequest);
    }

    @PostMapping("/resetuserpassword")
    public ResetUserPasswordResp resetUserPassword(@RequestBody(required = false) ResetUserPasswordReq req, HttpServletRequest httpRequest) {
        return adminResetUserPasswordService.resetUserPassword(GoStyleRequest.normalize(req, ResetUserPasswordReq::new), httpRequest);
    }

    @PostMapping("/banuser")
    public BanUserResp banUser(@RequestBody(required = false) BanUserReq req, HttpServletRequest httpRequest) {
        return adminBanUserService.banUser(GoStyleRequest.normalize(req, BanUserReq::new), httpRequest);
    }

    @PostMapping("/unbanuser")
    public UnBanUserResp unBanUser(@RequestBody(required = false) UnBanUserReq req, HttpServletRequest httpRequest) {
        return adminUnBanUserService.unBanUser(GoStyleRequest.normalize(req, UnBanUserReq::new), httpRequest);
    }

    @PostMapping("/getuser")
    public GetUserResp getUser(@RequestBody(required = false) GetUserReq req, HttpServletRequest httpRequest) {
        return adminGetUserService.getUser(GoStyleRequest.normalize(req, GetUserReq::new), httpRequest);
    }

    @PostMapping("/getuserlist")
    public GetUserListResp getUserList(@RequestBody(required = false) GetUserListReq req, HttpServletRequest httpRequest) {
        return adminListUserService.getUserList(GoStyleRequest.normalize(req, GetUserListReq::new), httpRequest);
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
