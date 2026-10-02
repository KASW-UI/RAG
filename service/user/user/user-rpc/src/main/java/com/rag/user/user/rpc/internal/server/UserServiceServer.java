package com.rag.user.user.rpc.internal.server;

import com.rag.user.user.proto.CreateUserReq;
import com.rag.user.user.proto.CreateUserResp;
import com.rag.user.user.proto.DeleteUserReq;
import com.rag.user.user.proto.DeleteUserResp;
import com.rag.user.user.proto.GetAvatarHistoryReq;
import com.rag.user.user.proto.GetAvatarHistoryResp;
import com.rag.user.user.proto.GetUserReq;
import com.rag.user.user.proto.GetUserResp;
import com.rag.user.user.proto.LoginReq;
import com.rag.user.user.proto.LoginResp;
import com.rag.user.user.proto.LogoutReq;
import com.rag.user.user.proto.LogoutResp;
import com.rag.user.user.proto.SelectAvatarReq;
import com.rag.user.user.proto.SelectAvatarResp;
import com.rag.user.user.proto.UpdateUserReq;
import com.rag.user.user.proto.UpdateUserResp;
import com.rag.user.user.proto.UploadAvatarReq;
import com.rag.user.user.proto.UploadAvatarResp;
import com.rag.user.user.proto.UserServiceGrpc;
import com.rag.user.user.rpc.internal.logic.DeleteUserLogic;
import com.rag.user.user.rpc.internal.logic.GetAvatarHistoryLogic;
import com.rag.user.user.rpc.internal.logic.GetUserLogic;
import com.rag.user.user.rpc.internal.logic.LoginLogic;
import com.rag.user.user.rpc.internal.logic.LogoutLogic;
import com.rag.user.user.rpc.internal.logic.RegisterLogic;
import com.rag.user.user.rpc.internal.logic.SelectAvatarLogic;
import com.rag.user.user.rpc.internal.logic.UpdateUserLogic;
import com.rag.user.user.rpc.internal.logic.UploadAvatarLogic;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * 对应 Go: internal/server/userserviceserver.go
 *
 * Go 端的 server 包是 goctl 生成的骨架:
 *   func (s *UserServiceServer) GetUser(ctx, in) (out, error) {
 *       l := logic.NewGetUserLogic(ctx, s.svcCtx)
 *       return l.GetUser(in)
 *   }
 *
 * Java 端用 grpc-spring-boot-starter:
 *   - @GrpcService 自动注册到 gRPC Server(对应 Go 的 RegisterUserServiceServer)
 *   - 继承 UserServiceGrpc.UserServiceImplBase(对应 Go 的 pb.UnimplementedUserServiceServer 嵌入)
 *   - 每个 RPC 方法 @Override,内部调 Logic(Logic 负责业务,Server 只做协议适配)
 *
 * 设计分层(对应 Go 的 server/logic 边界):
 *   - Server:协议层,只做"请求 → Logic → 响应"的转发,不做业务判断
 *   - Logic:业务编排,可复用,跨协议(Web/RPC)都能调
 *   - GrpcExceptionHandler:把 Logic 抛的 BusinessException 转 StatusException(见 server/GrpcExceptionHandler.java)
 *
 * ⚠️ 关键差异:Go 的 Logout 返回 LogoutResp,Java 端 grpc 生成的方法签名是
 *    `void logout(LogoutReq, StreamObserver<LogoutResp>)`,不是直接 return T&out——这是 gRPC 异步流的体现,
 *    Java 用 StreamObserver.onNext + onCompleted 完成。
 */
@GrpcService
@RequiredArgsConstructor
public class UserServiceServer extends UserServiceGrpc.UserServiceImplBase {

    private final RegisterLogic registerLogic;
    private final LoginLogic loginLogic;
    private final LogoutLogic logoutLogic;
    private final GetUserLogic getUserLogic;
    private final UpdateUserLogic updateUserLogic;
    private final DeleteUserLogic deleteUserLogic;
    private final UploadAvatarLogic uploadAvatarLogic;
    private final SelectAvatarLogic selectAvatarLogic;
    private final GetAvatarHistoryLogic getAvatarHistoryLogic;

    // —— 普通一元调用:StreamObserver 适配 ——

    @Override
    public void getUser(GetUserReq req, StreamObserver<GetUserResp> obs) {
        obs.onNext(getUserLogic.getUser(req));
        obs.onCompleted();
    }

    @Override
    public void register(CreateUserReq req, StreamObserver<CreateUserResp> obs) {
        obs.onNext(registerLogic.register(req));
        obs.onCompleted();
    }

    @Override
    public void login(LoginReq req, StreamObserver<LoginResp> obs) {
        obs.onNext(loginLogic.login(req));
        obs.onCompleted();
    }

    @Override
    public void logout(LogoutReq req, StreamObserver<LogoutResp> obs) {
        logoutLogic.logout(req);
        obs.onNext(LogoutResp.newBuilder().setSuccess(true).build());
        obs.onCompleted();
    }

    @Override
    public void updateUser(UpdateUserReq req, StreamObserver<UpdateUserResp> obs) {
        obs.onNext(updateUserLogic.updateUser(req));
        obs.onCompleted();
    }

    @Override
    public void deleteUser(DeleteUserReq req, StreamObserver<DeleteUserResp> obs) {
        obs.onNext(deleteUserLogic.deleteUser(req));
        obs.onCompleted();
    }

    @Override
    public void uploadAvatar(UploadAvatarReq req, StreamObserver<UploadAvatarResp> obs) {
        obs.onNext(uploadAvatarLogic.uploadAvatar(req));
        obs.onCompleted();
    }

    @Override
    public void selectAvatar(SelectAvatarReq req, StreamObserver<SelectAvatarResp> obs) {
        obs.onNext(selectAvatarLogic.selectAvatar(req));
        obs.onCompleted();
    }

    @Override
    public void getAvatarHistory(GetAvatarHistoryReq req, StreamObserver<GetAvatarHistoryResp> obs) {
        obs.onNext(getAvatarHistoryLogic.getAvatarHistory(req));
        obs.onCompleted();
    }
}