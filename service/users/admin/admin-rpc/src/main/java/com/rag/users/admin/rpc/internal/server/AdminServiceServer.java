package com.rag.users.admin.rpc.internal.server;

import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.BanUserReq;
import com.rag.users.admin.proto.BanUserResp;
import com.rag.users.admin.proto.CreateAdminReq;
import com.rag.users.admin.proto.CreateAdminResp;
import com.rag.users.admin.proto.CreateInviteCodeReq;
import com.rag.users.admin.proto.CreateInviteCodeResp;
import com.rag.users.admin.proto.DeleteUserReq;
import com.rag.users.admin.proto.DeleteUserResp;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetSelfResp;
import com.rag.users.admin.proto.GetUserListReq;
import com.rag.users.admin.proto.GetUserListResp;
import com.rag.users.admin.proto.GetUserReq;
import com.rag.users.admin.proto.GetUserResp;
import com.rag.users.admin.proto.LoginReq;
import com.rag.users.admin.proto.LoginResp;
import com.rag.users.admin.proto.LogoutReq;
import com.rag.users.admin.proto.LogoutResp;
import com.rag.users.admin.proto.ResetUserPasswordReq;
import com.rag.users.admin.proto.ResetUserPasswordResp;
import com.rag.users.admin.proto.UnBanUserReq;
import com.rag.users.admin.proto.UnBanUserResp;
import com.rag.users.admin.proto.UpdateSelfReq;
import com.rag.users.admin.proto.UpdateSelfResp;
import com.rag.users.admin.proto.UpdateUserReq;
import com.rag.users.admin.proto.UpdateUserResp;
import com.rag.users.admin.rpc.internal.logic.BanUserLogic;
import com.rag.users.admin.rpc.internal.logic.CreateAdminLogic;
import com.rag.users.admin.rpc.internal.logic.CreateInviteCodeLogic;
import com.rag.users.admin.rpc.internal.logic.DeleteUserLogic;
import com.rag.users.admin.rpc.internal.logic.GetSelfLogic;
import com.rag.users.admin.rpc.internal.logic.GetUserListLogic;
import com.rag.users.admin.rpc.internal.logic.GetUserLogic;
import com.rag.users.admin.rpc.internal.logic.LoginLogic;
import com.rag.users.admin.rpc.internal.logic.LogoutLogic;
import com.rag.users.admin.rpc.internal.logic.ResetUserPasswordLogic;
import com.rag.users.admin.rpc.internal.logic.UnBanUserLogic;
import com.rag.users.admin.rpc.internal.logic.UpdateSelfLogic;
import com.rag.users.admin.rpc.internal.logic.UpdateUserLogic;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.stereotype.Component;

/**
 * @GrpcService 暴露 AdminService（13 个 RPC）—— 对应 Go 端
 *   internal/server/adminserviceserver.go 的 AdminServiceServer（go-zero 自动代码）
 *   和 internal/svc/servicecontext.go 注入的 AdminService。
 *
 * 每个 RPC 方法：
 *   1. 调对应 Logic 的业务方法（throw StatusRuntimeException 表示 gRPC error）
 *   2. 成功 → obs.onNext(resp); obs.onCompleted()
 *   3. 失败 → obs.onError(e)
 *
 * 框架对照：
 *   Go:  AdminServiceServer{svcCtx} → func (s) Login(in, out) { l := NewLoginLogic(...); l.Login(in) }
 *   Java: AdminServiceServer{LoginLogic, ...} → 同样的转发
 *
 * Spring Boot DI 自动注入 13 个 Logic（每个标了 @Service）。本次实装进度：
 *   - 全部 13 个 Logic ✓ 已实装（Login / GetUser / GetUserList / GetSelf / UpdateSelf /
 *     UpdateUser / DeleteUser / ResetUserPassword / BanUser / UnBanUser / Logout /
 *     CreateInviteCode / CreateAdmin）。
 *   - admin-rpc 全部 RPC 已可调用。
 *
 * 鉴权：本 server 不做 JWT 校验（D-008 / D-009）—— Go 端 admin-rpc 也没有 JWT 拦截器，
 *       admin 操作通过调用者（admin-api）层注入的 identity 完成 admin 鉴权。
 */
@GrpcService
@Component
public class AdminServiceServer extends AdminServiceGrpc.AdminServiceImplBase {

    private final LoginLogic loginLogic;
    private final GetUserLogic getUserLogic;
    private final GetUserListLogic getUserListLogic;
    private final GetSelfLogic getSelfLogic;
    private final UpdateSelfLogic updateSelfLogic;
    private final UpdateUserLogic updateUserLogic;
    private final DeleteUserLogic deleteUserLogic;
    private final ResetUserPasswordLogic resetUserPasswordLogic;
    private final BanUserLogic banUserLogic;
    private final UnBanUserLogic unBanUserLogic;
    private final LogoutLogic logoutLogic;
    private final CreateInviteCodeLogic createInviteCodeLogic;
    private final CreateAdminLogic createAdminLogic;

    public AdminServiceServer(LoginLogic loginLogic,
                              GetUserLogic getUserLogic,
                              GetUserListLogic getUserListLogic,
                              GetSelfLogic getSelfLogic,
                              UpdateSelfLogic updateSelfLogic,
                              UpdateUserLogic updateUserLogic,
                              DeleteUserLogic deleteUserLogic,
                              ResetUserPasswordLogic resetUserPasswordLogic,
                              BanUserLogic banUserLogic,
                              UnBanUserLogic unBanUserLogic,
                              LogoutLogic logoutLogic,
                              CreateInviteCodeLogic createInviteCodeLogic,
                              CreateAdminLogic createAdminLogic) {
        this.loginLogic = loginLogic;
        this.getUserLogic = getUserLogic;
        this.getUserListLogic = getUserListLogic;
        this.getSelfLogic = getSelfLogic;
        this.updateSelfLogic = updateSelfLogic;
        this.updateUserLogic = updateUserLogic;
        this.deleteUserLogic = deleteUserLogic;
        this.resetUserPasswordLogic = resetUserPasswordLogic;
        this.banUserLogic = banUserLogic;
        this.unBanUserLogic = unBanUserLogic;
        this.logoutLogic = logoutLogic;
        this.createInviteCodeLogic = createInviteCodeLogic;
        this.createAdminLogic = createAdminLogic;
    }

    // ====================== 已实装的 RPC ======================

    @Override
    public void login(LoginReq req, StreamObserver<LoginResp> obs) {
        invoke(() -> loginLogic.login(req), obs);
    }

    @Override
    public void getUser(GetUserReq req, StreamObserver<GetUserResp> obs) {
        invoke(() -> getUserLogic.getUser(req), obs);
    }

    @Override
    public void getUserList(GetUserListReq req, StreamObserver<GetUserListResp> obs) {
        invoke(() -> getUserListLogic.getUserList(req), obs);
    }

    @Override
    public void getSelf(GetSelfReq req, StreamObserver<GetSelfResp> obs) {
        invoke(() -> getSelfLogic.getSelf(req), obs);
    }

    @Override
    public void updateSelf(UpdateSelfReq req, StreamObserver<UpdateSelfResp> obs) {
        invoke(() -> updateSelfLogic.updateSelf(req), obs);
    }

    @Override
    public void updateUser(UpdateUserReq req, StreamObserver<UpdateUserResp> obs) {
        invoke(() -> updateUserLogic.updateUser(req), obs);
    }

    @Override
    public void deleteUser(DeleteUserReq req, StreamObserver<DeleteUserResp> obs) {
        invoke(() -> deleteUserLogic.deleteUser(req), obs);
    }

    @Override
    public void resetUserPassword(ResetUserPasswordReq req, StreamObserver<ResetUserPasswordResp> obs) {
        invoke(() -> resetUserPasswordLogic.resetUserPassword(req), obs);
    }

    @Override
    public void banUser(BanUserReq req, StreamObserver<BanUserResp> obs) {
        invoke(() -> banUserLogic.banUser(req), obs);
    }

    @Override
    public void unBanUser(UnBanUserReq req, StreamObserver<UnBanUserResp> obs) {
        invoke(() -> unBanUserLogic.unBanUser(req), obs);
    }

    @Override
    public void logout(LogoutReq req, StreamObserver<LogoutResp> obs) {
        invoke(() -> logoutLogic.logout(req), obs);
    }

    @Override
    public void createInviteCode(CreateInviteCodeReq req, StreamObserver<CreateInviteCodeResp> obs) {
        invoke(() -> createInviteCodeLogic.createInviteCode(req), obs);
    }

    @Override
    public void createAdmin(CreateAdminReq req, StreamObserver<CreateAdminResp> obs) {
        invoke(() -> createAdminLogic.createAdmin(req), obs);
    }

    /**
     * 通用转发：成功 obs.onNext + onCompleted；失败 obs.onError。
     * 任何 StatusRuntimeException 原样透传（保留 code + description），
     * 其它异常兜底成 INTERNAL（与 Go 端统一行为对齐）。
     */
    private static <Req, Resp> void invoke(
            java.util.function.Supplier<Resp> logic,
            StreamObserver<Resp> obs) {
        try {
            Resp resp = logic.get();
            obs.onNext(resp);
            obs.onCompleted();
        } catch (StatusRuntimeException e) {
            obs.onError(e);
        } catch (Throwable t) {
            obs.onError(io.grpc.Status.INTERNAL
                    .withDescription(t.getClass().getSimpleName() + ": " + t.getMessage())
                    .asRuntimeException());
        }
    }
}