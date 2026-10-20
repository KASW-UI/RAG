package com.rag.message.message.rpc.client;

import com.rag.message.message.proto.SenderRole;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * user-rpc gRPC client facade —— 对应 Go 端
 * {@code service/message/rpc/internal/svc/ServiceContext.go} 的 UserRpc。
 *
 * <p>调用用途：message-rpc 内部需要 user 域 profile 信息（ListConversations 时 peer profile）。
 * Go 端 {@code loadProfile(ctx, svcCtx, role=USER, id)} ——
 * Java 端封装为 {@link #getProfile(long)}：失败时返回 {@code "User {id}"} 兜底字符串，
 * 与 Go 端 status error 兜底行为 1:1。
 */
@Component
public class UserRpcClient {

    private static final Logger LOG = LoggerFactory.getLogger(UserRpcClient.class);

    private final UserServiceGrpc.UserServiceBlockingStub userStub;

    public UserRpcClient(@GrpcClient("user-rpc") UserServiceGrpc.UserServiceBlockingStub userStub) {
        this.userStub = userStub;
    }

    /**
     * 对应 Go 端 {@code loadProfile(ctx, svcCtx, role=USER, id)}：
     * 拉 user profile 的 (username, email)。失败 / 找不到时返回 {@code ["User {id}", ""]} 兜底，
     * 与 Go 端 1:1（profile 仅用于展示，失败不阻塞业务）。
     */
    public String[] getProfile(long uid) {
        if (uid <= 0) {
            return new String[]{"User " + uid, ""};
        }
        try {
            var resp = userStub.getUser(GetUserReq.newBuilder().setUid(uid).build());
            if (resp != null && resp.getFound() && resp.getUser() != null) {
                return new String[]{resp.getUser().getUsername(), resp.getUser().getEmail()};
            }
            return new String[]{"User " + uid, ""};
        } catch (StatusRuntimeException sre) {
            return new String[]{"User " + uid, ""};
        } catch (RuntimeException e) {
            LOG.warn("getUser({}) failed: {}", uid, e.getMessage());
            return new String[]{"User " + uid, ""};
        }
    }

    /** 复用 facade 入口（与 Go 端 loadProfile(role, id) 签名一致）。 */
    public String[] loadProfile(SenderRole role, long id) {
        if (role == SenderRole.USER) {
            return getProfile(id);
        }
        return new String[]{"Unknown " + id, ""};
    }
}