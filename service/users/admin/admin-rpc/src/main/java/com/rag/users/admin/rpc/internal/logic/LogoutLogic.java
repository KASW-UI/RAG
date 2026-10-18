package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.LogoutReq;
import com.rag.users.admin.proto.LogoutResp;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 对应 Go 端 internal/logic/logoutlogic.go：
 *   func (l *LogoutLogic) Logout(in *pb.LogoutReq) (*pb.LogoutResp, error)
 *
 * <p>把 admin 的 JWT token 写入 Redis 黑名单，TTL 等于 JWT 过期秒数。
 *
 * <p>Works（W1~W3）：
 *   W1 校验 token 非空 → 空则 INVALID_ARGUMENT "Token不能为空"
 *   W2 Redis Setex("admin:jwt_blacklist:%s", "banned", AdminAuth.AccessExpire)
 *   W3 Redis 写入异常 → INTERNAL "注销失败,Redis写入异常"
 *   W5 成功 → LogoutResp{}（Success 字段保持 proto3 默认 false，1:1 跟随 Go 端未设）
 *
 * <p>关键 Decision：
 *   D-006 Redis key 必须是字面量 "admin:jwt_blacklist:%s" —— 与 user-rpc 的 "user:jwt_blacklist:%s" 不同（admin 前缀）
 *   D-007 TTL 必须等于 JWT 过期秒数（AdminAuth.AccessExpire=2592000），不能写死 24h
 *
 * <p>没有 OTel span / metrics（Go 端 logoutlogic.go 也没埋点，D-016 1:1 保留）。
 */
@Service
public class LogoutLogic {

    /** D-006：Redis key 字面量前缀，必须与 user-rpc 的 "user:jwt_blacklist:" 区分 */
    private static final String BLACKLIST_KEY_PREFIX = "admin:jwt_blacklist:";

    private final ServiceContext svcCtx;

    public LogoutLogic(ServiceContext svcCtx) {
        this.svcCtx = svcCtx;
    }

    public LogoutResp logout(LogoutReq req) {
        // W1
        if (req.getToken().isEmpty()) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("Token不能为空")
                    .asRuntimeException();
        }

        // W2（D-006 key + D-007 TTL）
        String key = BLACKLIST_KEY_PREFIX + req.getToken();
        long ttlSeconds = svcCtx.getConfig().getAdminAuth().getAccessExpire();
        try {
            svcCtx.getBizRedis().opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw Status.INTERNAL
                    .withDescription("注销失败,Redis写入异常")
                    .asRuntimeException();
        }

        // W4 成功：Go 端返 &pb.LogoutResp{}（空 + nil error），Success 字段保持 proto3 默认 false
        return LogoutResp.newBuilder().build();
    }
}