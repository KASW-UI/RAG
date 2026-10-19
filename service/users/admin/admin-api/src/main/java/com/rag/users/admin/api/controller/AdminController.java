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
    public LoginResp login(@RequestBody LoginReq req, HttpServletRequest httpRequest) {
        return adminLoginService.login(req, httpRequest);
    }

    @PostMapping("/create")
    public CreateAdminResp createAdmin(@RequestBody CreateAdminReq req, HttpServletRequest httpRequest) {
        return adminCreateService.createAdmin(req, httpRequest);
    }

    // ============== JWT-protected（11 个） ==============

    @PostMapping("/logout")
    public LogoutResp logout(@RequestBody LogoutReq req, HttpServletRequest httpRequest) {
        return adminLogoutService.logout(req, httpRequest);
    }

    @PostMapping("/getself")
    public GetSelfResp getSelf(@RequestBody GetSelfReq req, HttpServletRequest httpRequest) {
        return adminGetSelfService.getSelf(req, httpRequest);
    }

    @PostMapping("/update")
    public UpdateSelfResp updateSelf(@RequestBody UpdateSelfReq req, HttpServletRequest httpRequest) {
        return adminUpdateSelfService.updateSelf(req, httpRequest);
    }

    @PostMapping("/updateuser")
    public UpdateUserResp updateUser(@RequestBody UpdateUserReq req, HttpServletRequest httpRequest) {
        return adminUpdateUserService.updateUser(req, httpRequest);
    }

    @PostMapping("/delete")
    public DeleteUserResp deleteUser(@RequestBody DeleteUserReq req, HttpServletRequest httpRequest) {
        return adminDeleteUserService.deleteUser(req, httpRequest);
    }

    @PostMapping("/resetuserpassword")
    public ResetUserPasswordResp resetUserPassword(@RequestBody ResetUserPasswordReq req, HttpServletRequest httpRequest) {
        return adminResetUserPasswordService.resetUserPassword(req, httpRequest);
    }

    @PostMapping("/banuser")
    public BanUserResp banUser(@RequestBody BanUserReq req, HttpServletRequest httpRequest) {
        return adminBanUserService.banUser(req, httpRequest);
    }

    @PostMapping("/unbanuser")
    public UnBanUserResp unBanUser(@RequestBody UnBanUserReq req, HttpServletRequest httpRequest) {
        return adminUnBanUserService.unBanUser(req, httpRequest);
    }

    @PostMapping("/getuser")
    public GetUserResp getUser(@RequestBody GetUserReq req, HttpServletRequest httpRequest) {
        return adminGetUserService.getUser(req, httpRequest);
    }

    @PostMapping("/getuserlist")
    public GetUserListResp getUserList(@RequestBody GetUserListReq req, HttpServletRequest httpRequest) {
        return adminListUserService.getUserList(req, httpRequest);
    }

    @PostMapping("/invite/create")
    public CreateInviteCodeResp createInviteCode(@RequestBody CreateInviteCodeReq req, HttpServletRequest httpRequest) {
        return adminCreateInviteCodeService.createInviteCode(req, httpRequest);
    }
}
