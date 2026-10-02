package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.LogoutReq;
import com.rag.user.user.rpc.internal.config.Config;
import com.rag.user.user.rpc.internal.model.ErrorCode;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 对应 Go: internal/logic/logoutlogic.go
 *
 * 业务逻辑(严格 1:1):
 *   1. Token 非空校验 → InvalidArgument
 *   2. Redis 写黑名单:key = "user:jwt_blacklist:{token}",value = "banned",TTL = AccessExpire
 *
 * 设计要点:
 *   - TTL 必须 ≥ JWT AccessExpire;否则 token 过期后黑名单还在白占内存
 *   - 用 Config.UserAuth.AccessExpire 而非写死 30 天,1:1 复刻 Go 的 l.svcCtx.Config.UserAuth.AccessExpire
 *   - StringRedisTemplate.opsForValue().set(key, value, ttl, TimeUnit.SECONDS) 等价 Go 的 SetexCtx
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutLogic {

    private final StringRedisTemplate redisTemplate;
    private final Config config;

    /** 对应 Go: fmt.Sprintf("user:jwt_blacklist:%s", in.Token) */
    private static String blacklistKey(String token) {
        return "user:jwt_blacklist:" + token;
    }

    public void logout(LogoutReq req) {
        // 1. Token 非空校验
        if (req.getToken() == null || req.getToken().isEmpty()) {
            log.warn("logout failed: token is empty");
            throw new BusinessException(ErrorCode.Code.TOKEN_EMPTY);
        }

        // 2. 写 Redis 黑名单,TTL = AccessExpire(秒)
        long expireSeconds = config.getAccessExpire();
        try {
            redisTemplate.opsForValue().set(
                    blacklistKey(req.getToken()),
                    "banned",
                    expireSeconds,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.error("logout failed: redis write error, token={}", req.getToken(), e);
            throw new BusinessException(ErrorCode.Code.REDIS_WRITE_ERROR);
        }

        log.info("logout success, token blacklisted!");
    }
}