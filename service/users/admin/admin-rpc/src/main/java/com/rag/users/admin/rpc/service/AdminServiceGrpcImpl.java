package com.rag.users.admin.rpc.service;

import com.rag.users.admin.proto.AdminInfo;
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
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.admin.rpc.domain.AdminEntity;
import com.rag.users.admin.rpc.domain.UserEntity;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.HashMap;
import java.util.Map;

/**
 * AdminService gRPC 实现 —— 13 个 RPC 方法的 thin wrapper。
 *
 * 对应 Go 端 internal/server/adminserviceserver.go 的 AdminServiceServer（go-zero 自动代码）
 * 和 internal/svc/servicecontext.go 注入的 AdminService。
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>Go 端把"路由表"和"13 个 handler 样板"分两个文件；Java 侧合到本类 —— 类级注解
 *       {@code @GrpcService} 自动被 devh grpc-spring-boot-starter 扫描起 gRPC server</li>
 *   <li>每个 RPC 方法是 thin wrapper：proto req → AdminDomainService → proto resp</li>
 *   <li>proto3 默认值保留：Found 字段 / Success 字段不设；Score 字段不设（1:1 跟随 Go 端）</li>
 * </ul>
 *
 * <p>错误处理：AdminDomainService 内部已经按业务语义抛 {@link StatusRuntimeException}（NOT_FOUND /
 * UNAUTHENTICATED / INTERNAL 等），本类通过 {@link #invoke} 统一转发 —— 任何
 * {@code StatusRuntimeException} 原样透传，其它异常兜底成 {@code Status.INTERNAL}。
 */
@GrpcService
public class AdminServiceGrpcImpl extends AdminServiceGrpc.AdminServiceImplBase {

    private final AdminDomainService adminDomainService;

    public AdminServiceGrpcImpl(AdminDomainService adminDomainService) {
        this.adminDomainService = adminDomainService;
    }

    // ====================== 1. Login ======================

    @Override
    public void login(LoginReq request, StreamObserver<LoginResp> responseObserver) {
        invoke(() -> {
            long uid = adminDomainService.login(request.getUsername(), request.getPassword());
            return LoginResp.newBuilder().setUid(uid).build();
        }, responseObserver);
    }

    // ====================== 2. Logout ======================

    @Override
    public void logout(LogoutReq request, StreamObserver<LogoutResp> responseObserver) {
        invoke(() -> {
            adminDomainService.logout(request.getToken());
            return LogoutResp.newBuilder().build();   // Success 字段保持 proto3 默认 false（1:1 跟随 Go）
        }, responseObserver);
    }

    // ====================== 3. GetSelf ======================

    @Override
    public void getSelf(GetSelfReq request, StreamObserver<GetSelfResp> responseObserver) {
        invoke(() -> {
            AdminEntity admin = adminDomainService.getSelf(request.getUid());
            return GetSelfResp.newBuilder().setAdmin(toProtoAdmin(admin)).build();
        }, responseObserver);
    }

    // ====================== 4. UpdateSelf ======================

    @Override
    public void updateSelf(UpdateSelfReq request, StreamObserver<UpdateSelfResp> responseObserver) {
        invoke(() -> {
            AdminEntity admin = adminDomainService.updateSelf(
                    request.getUid(),
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    request.getExtraInfoMap() == null || request.getExtraInfoMap().isEmpty()
                            ? null
                            : new HashMap<>(request.getExtraInfoMap())
            );
            return UpdateSelfResp.newBuilder()
                    .setSuccess(true)
                    .setAdmin(toProtoAdmin(admin))
                    .build();
        }, responseObserver);
    }

    // ====================== 5. CreateAdmin ======================

    @Override
    public void createAdmin(CreateAdminReq request, StreamObserver<CreateAdminResp> responseObserver) {
        invoke(() -> {
            long uid = adminDomainService.createAdmin(
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    request.getExtraInfoMap() == null || request.getExtraInfoMap().isEmpty()
                            ? null
                            : new HashMap<>(request.getExtraInfoMap()),
                    request.getInviteCode()
            );
            return CreateAdminResp.newBuilder().setUid(uid).build();
        }, responseObserver);
    }

    // ====================== 6. CreateInviteCode ======================

    @Override
    public void createInviteCode(CreateInviteCodeReq request, StreamObserver<CreateInviteCodeResp> responseObserver) {
        invoke(() -> {
            AdminDomainService.InviteCodeResult result = adminDomainService.createInviteCode(request.getOperatorId());
            return CreateInviteCodeResp.newBuilder()
                    .setInviteCode(result.code())
                    .setExpiresAt(result.expiresAt())
                    .build();
        }, responseObserver);
    }

    // ====================== 7. BanUser ======================

    @Override
    public void banUser(BanUserReq request, StreamObserver<BanUserResp> responseObserver) {
        invoke(() -> {
            adminDomainService.banUser(request.getUid(), request.getOperatorId());
            return BanUserResp.newBuilder().setSuccess(true).build();
        }, responseObserver);
    }

    // ====================== 8. UnBanUser ======================

    @Override
    public void unBanUser(UnBanUserReq request, StreamObserver<UnBanUserResp> responseObserver) {
        invoke(() -> {
            adminDomainService.unBanUser(request.getUid(), request.getOperatorId());
            return UnBanUserResp.newBuilder().setSuccess(true).build();
        }, responseObserver);
    }

    // ====================== 9. GetUser ======================

    @Override
    public void getUser(GetUserReq request, StreamObserver<GetUserResp> responseObserver) {
        invoke(() -> {
            UserEntity user = adminDomainService.getUser(request.getUid());
            // DRIFT-002 说明：此处<b>不</b>设 found 是对的 —— Go 的 rpc 层
            // getuserlogic.go:42-50 确实只 return &pb.GetUserResp{User: ...}，没设 pb 的 found。
            // 但 Go 的 <b>api 层</b> getuserlogic.go:59 构造 types.GetUserResp 时写死了 Found: true，
            // 那才是客户端最终看到的值。admin-api 的 AdminGetUserService 已据此显式置 true；
            // 这里保持 false 只描述"rpc 层行为"，不要据此认为端到端 found 是 false。
            return GetUserResp.newBuilder().setUser(toProtoUser(user)).build();
        }, responseObserver);
    }

    // ====================== 10. GetUserList ======================

    @Override
    public void getUserList(GetUserListReq request, StreamObserver<GetUserListResp> responseObserver) {
        invoke(() -> {
            AdminDomainService.UserListResult result = adminDomainService.getUserList(
                    request.getPage(),
                    request.getPageSize(),
                    request.getKeyword()
            );
            GetUserListResp.Builder builder = GetUserListResp.newBuilder().setTotal(result.total());
            for (UserEntity u : result.users()) {
                builder.addList(toProtoUser(u));
            }
            return builder.build();
        }, responseObserver);
    }

    // ====================== 11. UpdateUser ======================

    @Override
    public void updateUser(UpdateUserReq request, StreamObserver<UpdateUserResp> responseObserver) {
        invoke(() -> {
            UserEntity user = adminDomainService.updateUser(
                    request.getUid(),
                    request.getOperatorId(),
                    request.getUsername(),
                    request.getPassword(),
                    request.getEmail(),
                    request.getExtraInfoMap() == null || request.getExtraInfoMap().isEmpty()
                            ? null
                            : new HashMap<>(request.getExtraInfoMap())
            );
            // 🔴 2026-10-23：去掉 setSuccess(true)，对齐 Go（DRIFT-ARPC-002）。
            // Go 出处：service/user/admin/rpc/internal/logic/updateuserlogic.go:105-113
            //     return &pb.UpdateUserResp{
            //         User: &pb.UserInfo{ … },
            //     }, nil
            // Go **只 set 了 User**，没有 set Success ⇒ proto3 bool 的默认值 false
            // ⇒ 线上序列化时该字段缺席，客户端读到 **false**。
            // RAG 原先 setSuccess(true) ⇒ 线上是 varint 1，客户端读到 true。
            // ⇒ 这是 **wire 层可观察差异**（不是「业务上无所谓的默认值」）。
            //
            // ⚠️ **不要透传，也不要在此补 true**：本层必须与 Go 的 rpc 层逐字一致。
            //   admin-api 那层 Go 本来就硬编码 true
            //   （service/user/admin/api/internal/logic/admin/updateuserlogic.go:68），
            //   所以 HTTP 面两侧不变，仍是 {"success":true} —— 与本条无关、也不受影响。
            // 守门：AdminRpcGoWireParityTest#updateUserRespSuccessMatchesGoWire。
            return UpdateUserResp.newBuilder().setUser(toProtoUser(user)).build();
        }, responseObserver);
    }

    // ====================== 12. DeleteUser ======================

    @Override
    public void deleteUser(DeleteUserReq request, StreamObserver<DeleteUserResp> responseObserver) {
        invoke(() -> {
            adminDomainService.deleteUser(request.getUid(), request.getOperatorId());
            return DeleteUserResp.newBuilder().setSuccess(true).build();
        }, responseObserver);
    }

    // ====================== 13. ResetUserPassword ======================

    @Override
    public void resetUserPassword(ResetUserPasswordReq request, StreamObserver<ResetUserPasswordResp> responseObserver) {
        invoke(() -> {
            adminDomainService.resetUserPassword(request.getUid(), request.getOperatorId());
            return ResetUserPasswordResp.newBuilder().setSuccess(true).build();
        }, responseObserver);
    }

    // ====================== 内部 helper：entity → proto ======================

    /**
     * AdminEntity → AdminInfo proto
     * 注意：email 是 nullable（{@code *string}），用 AdminHelpers.stringValue 转成空字符串。
     * extra_info map 空时也要 putAllExtraInfo({}) —— Go 端如此。
     */
    private static AdminInfo toProtoAdmin(AdminEntity admin) {
        AdminInfo.Builder b = AdminInfo.newBuilder()
                .setUid(admin.getUid() == null ? 0L : admin.getUid())
                .setUsername(admin.getUsername() == null ? "" : admin.getUsername())
                .setEmail(AdminHelpers.stringValue(admin.getEmail()));
        Map<String, String> extra = admin.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }
        return b.build();
    }

    /**
     * UserEntity → UserInfo proto
     * 注意：admin-rpc 不 setScore（proto3 默认 0，1:1 跟随 Go 端）。
     * email 非 null（User 表定义），但用 null-safe 兜底。
     */
    private static UserInfo toProtoUser(UserEntity user) {
        UserInfo.Builder b = UserInfo.newBuilder()
                .setUid(user.getUid() == null ? 0L : user.getUid())
                .setUsername(user.getUsername() == null ? "" : user.getUsername())
                .setEmail(user.getEmail() == null ? "" : user.getEmail())
                .setStatus(user.getStatus() == null ? 0L : user.getStatus());
        Map<String, String> extra = user.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }
        return b.build();
    }

    /**
     * 通用转发：成功 obs.onNext + onCompleted；失败 obs.onError。
     * 任何 {@link StatusRuntimeException} 原样透传（保留 code + description），
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
            obs.onError(Status.INTERNAL
                    .withDescription(t.getClass().getSimpleName() + ": " + t.getMessage())
                    .asRuntimeException());
        }
    }
}
