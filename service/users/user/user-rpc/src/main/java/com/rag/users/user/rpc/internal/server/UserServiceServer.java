package com.rag.users.user.rpc.internal.server;

import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.CreateUserResp;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.DeleteUserResp;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.GetAvatarHistoryResp;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.LogoutResp;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.SelectAvatarResp;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UpdateUserResp;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.proto.UploadAvatarResp;
import com.rag.users.user.proto.UserServiceGrpc;
import com.rag.users.user.rpc.internal.logic.DeleteUserLogic;
import com.rag.users.user.rpc.internal.logic.GetAvatarHistoryLogic;
import com.rag.users.user.rpc.internal.logic.GetUserLogic;
import com.rag.users.user.rpc.internal.logic.LoginLogic;
import com.rag.users.user.rpc.internal.logic.LogoutLogic;
import com.rag.users.user.rpc.internal.logic.RegisterLogic;
import com.rag.users.user.rpc.internal.logic.SelectAvatarLogic;
import com.rag.users.user.rpc.internal.logic.UpdateUserLogic;
import com.rag.users.user.rpc.internal.logic.UploadAvatarLogic;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.stereotype.Component;

/**
 * @GrpcService 暴露 UserService（9 个 RPC）—— 对应 Go 端
 *   internal/server/userserviceserver.go 的 UserServiceServer（go-zero 自动代码）
 *   和 internal/svc/servicecontext.go 注入的 UserService。
 *
 * 每个 RPC 方法：
 *   1. 调对应 Logic 的业务方法（throw StatusRuntimeException 表示 gRPC error）
 *   2. 成功 → obs.onNext(resp); obs.onCompleted()
 *   3. 失败 → obs.onError(e)
 *
 * 框架对照：
 *   Go:  UserServiceServer{svcCtx} → func (s) Register(in, out) { l := NewRegisterLogic(...); l.Register(in) }
 *   Java: UserServiceServer{RegisterLogic, LoginLogic, ...} → 同样的转发
 *
 * Spring Boot DI 自动注入 9 个 Logic（每个标了 @Service）。
 *
 * 鉴权：本 server 不做 JWT 校验（D-008 / D-009）—— Go 端 user-rpc 也没有 JWT 拦截器，
 *       真实身份由调用方（HTTP gateway user-api 或其他 RTW 服务）注入到 request.uid。
 */
@GrpcService
@Component
public class UserServiceServer extends UserServiceGrpc.UserServiceImplBase {

    private final RegisterLogic registerLogic;
    private final LoginLogic loginLogic;
    private final LogoutLogic logoutLogic;
    private final GetUserLogic getUserLogic;
    private final UpdateUserLogic updateUserLogic;
    private final DeleteUserLogic deleteUserLogic;
    private final UploadAvatarLogic uploadAvatarLogic;
    private final GetAvatarHistoryLogic getAvatarHistoryLogic;
    private final SelectAvatarLogic selectAvatarLogic;

    public UserServiceServer(
            RegisterLogic registerLogic,
            LoginLogic loginLogic,
            LogoutLogic logoutLogic,
            GetUserLogic getUserLogic,
            UpdateUserLogic updateUserLogic,
            DeleteUserLogic deleteUserLogic,
            UploadAvatarLogic uploadAvatarLogic,
            GetAvatarHistoryLogic getAvatarHistoryLogic,
            SelectAvatarLogic selectAvatarLogic) {
        this.registerLogic = registerLogic;
        this.loginLogic = loginLogic;
        this.logoutLogic = logoutLogic;
        this.getUserLogic = getUserLogic;
        this.updateUserLogic = updateUserLogic;
        this.deleteUserLogic = deleteUserLogic;
        this.uploadAvatarLogic = uploadAvatarLogic;
        this.getAvatarHistoryLogic = getAvatarHistoryLogic;
        this.selectAvatarLogic = selectAvatarLogic;
    }

    // ============== 9 个 RPC 转发 ==============

    @Override
    public void register(CreateUserReq req, StreamObserver<CreateUserResp> obs) {
        invoke(() -> registerLogic.register(req), obs);
    }

    @Override
    public void login(LoginReq req, StreamObserver<LoginResp> obs) {
        invoke(() -> loginLogic.login(req), obs);
    }

    @Override
    public void logout(LogoutReq req, StreamObserver<LogoutResp> obs) {
        invoke(() -> logoutLogic.logout(req), obs);
    }

    @Override
    public void getUser(GetUserReq req, StreamObserver<GetUserResp> obs) {
        invoke(() -> getUserLogic.getUser(req), obs);
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
    public void uploadAvatar(UploadAvatarReq req, StreamObserver<UploadAvatarResp> obs) {
        invoke(() -> uploadAvatarLogic.uploadAvatar(req), obs);
    }

    @Override
    public void getAvatarHistory(GetAvatarHistoryReq req, StreamObserver<GetAvatarHistoryResp> obs) {
        invoke(() -> getAvatarHistoryLogic.getAvatarHistory(req), obs);
    }

    @Override
    public void selectAvatar(SelectAvatarReq req, StreamObserver<SelectAvatarResp> obs) {
        invoke(() -> selectAvatarLogic.selectAvatar(req), obs);
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