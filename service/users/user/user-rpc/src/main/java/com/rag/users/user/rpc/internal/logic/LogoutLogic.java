package com.rag.users.user.rpc.internal.logic;

import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.LogoutResp;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 对应 Go 端 internal/logic/logoutlogic.go：
 *   func (l *LogoutLogic) Logout(in *pb.LogoutReq) (*pb.LogoutResp, error)
 *
 * Works：
 *   W1 校验 token 非空
 *   W2 Redis Setex("user:jwt_blacklist:%s", "banned", AccessExpire)
 *
 * 关键 Decision：
 *   D-006 Redis key 必须是字面量 "user:jwt_blacklist:%s"——HTTP 中间件用同一格式去查
 *   D-007 TTL 必须等于 JWT 过期秒数（UserAuth.AccessExpire=2592000），不能写死 24h
 */
@Service
public class LogoutLogic {

    private final ServiceContext svcCtx;

    public LogoutLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public LogoutResp logout(LogoutReq req) {
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();
        // W1
        if (req.getToken().isEmpty()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Token不能为空")
                    .asRuntimeException();
        }

        // W2（D-006 key + D-007 TTL）
        String key = "user:jwt_blacklist:" + req.getToken();
        long ttlSeconds = svcCtx.getConfig().getUserAuth().getAccessExpire();
        try {
            svcCtx.getBizRedis().opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("注销失败,Redis写入异常")
                    .asRuntimeException();
        }

        // 成功：Go 端返 &pb.LogoutResp{}（空 + nil error）
        return LogoutResp.newBuilder().build();
    }
}