package com.rag.users.user.rpc.service.impl;

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
import com.rag.users.user.rpc.service.UserDomainService;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * UserService gRPC 实现 —— 9 个 RPC 方法的 thin wrapper。
 *
 * 对应 Go: service/user/user/rpc/internal/server/userserviceserver.go
 *   （go-zero 自动生成的 UserServiceServer + svc 注入的 UserService）
 *
 * 框架职责:
 *   - devh grpc-spring-boot-starter 的 @GrpcService 自动被扫描起 gRPC server
 *   - extends UserServiceGrpc.UserServiceImplBase —— protoc 生成的 service 基类
 *   - 9 个方法是 thin wrapper：把 gRPC 请求转给 UserDomainService，再把响应回写
 *   - grpc-java 框架机制约束（一个 service 一个实现类）—— 非主动合并
 *   - 取代了原 internal.server.UserServiceServer.java
 *
 * 错误处理: UserDomainService 已经把 NotFoundException / AvatarHistoryNotFoundException
 *   映射成 io.grpc.StatusRuntimeException（直接 throw），gRPC 框架自动捕获并发回 client。
 *   本类不需要 try/catch。
 */
@GrpcService
public class UserServiceGrpcImpl extends UserServiceGrpc.UserServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(UserServiceGrpcImpl.class);

    private final UserDomainService userDomainService;

    public UserServiceGrpcImpl(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    @Override
    public void login(LoginReq request, StreamObserver<LoginResp> responseObserver) {
        try {
            LoginResp resp = userDomainService.login(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("login RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void register(CreateUserReq request, StreamObserver<CreateUserResp> responseObserver) {
        try {
            CreateUserResp resp = userDomainService.register(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("register RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void logout(LogoutReq request, StreamObserver<LogoutResp> responseObserver) {
        try {
            LogoutResp resp = userDomainService.logout(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("logout RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
        try {
            GetUserResp resp = userDomainService.getUser(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("getUser RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void updateUser(UpdateUserReq request, StreamObserver<UpdateUserResp> responseObserver) {
        try {
            UpdateUserResp resp = userDomainService.updateUser(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("updateUser RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void deleteUser(DeleteUserReq request, StreamObserver<DeleteUserResp> responseObserver) {
        try {
            DeleteUserResp resp = userDomainService.deleteUser(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("deleteUser RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void selectAvatar(SelectAvatarReq request, StreamObserver<SelectAvatarResp> responseObserver) {
        try {
            SelectAvatarResp resp = userDomainService.selectAvatar(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("selectAvatar RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void uploadAvatar(UploadAvatarReq request, StreamObserver<UploadAvatarResp> responseObserver) {
        try {
            UploadAvatarResp resp = userDomainService.uploadAvatar(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("uploadAvatar RPC failed", e);
            responseObserver.onError(e);
        }
    }

    @Override
    public void getAvatarHistory(GetAvatarHistoryReq request, StreamObserver<GetAvatarHistoryResp> responseObserver) {
        try {
            GetAvatarHistoryResp resp = userDomainService.getAvatarHistory(request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (RuntimeException e) {
            log.error("getAvatarHistory RPC failed", e);
            responseObserver.onError(e);
        }
    }
}