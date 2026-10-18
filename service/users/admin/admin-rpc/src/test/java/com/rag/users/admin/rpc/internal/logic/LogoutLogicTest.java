package com.rag.users.admin.rpc.internal.logic;

import com.rag.users.admin.proto.LogoutReq;
import com.rag.users.admin.proto.LogoutResp;
import com.rag.users.admin.rpc.internal.config.Config;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.DataModel;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 Logout 关键语义：
 *   - token 非空 → resp{Success=false 默认}，Redis SETEX key="admin:jwt_blacklist:%s" value="banned" ttl=AccessExpire
 *   - token 为空 → INVALID_ARGUMENT，不调 Redis
 *   - Redis 写入异常 → INTERNAL
 *
 * 与 user-rpc LogoutLogicTest 关键差异：key 前缀 "admin:" 而非 "user:"；Config 用 adminAuth 而非 userAuth。
 */
@ExtendWith(MockitoExtension.class)
class LogoutLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    Config config;
    Metrics metrics;
    ServiceContext svcCtx;
    LogoutLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.getAdminAuth().setAccessExpire(2592000L); // Go 端默认 30 天
        svcCtx = new ServiceContext(config, dataModel, redis);
        metrics = new Metrics(new SimpleMeterRegistry());
        logic = new LogoutLogic(svcCtx);
    }

    /** 命中：Redis SETEX key 必须以 "admin:jwt_blacklist:" 开头，value="banned"，TTL=AccessExpire 秒 */
    @Test
    void success_writesRedisBlacklist() {
        when(redis.opsForValue()).thenReturn(valueOps);

        LogoutResp resp = logic.logout(LogoutReq.newBuilder()
                .setToken("jwt-abc-123")
                .build());

        // 1:1 跟随 Go 端 &pb.LogoutResp{}，Success 字段保持 proto3 默认 false
        assertFalse(resp.getSuccess(), "1:1 跟随 Go 端未设 Success 字段（proto3 默认 false）");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertEquals("admin:jwt_blacklist:jwt-abc-123", keyCaptor.getValue(),
                "D-006: key 必须是字面量 admin:jwt_blacklist:<token>");
        assertEquals("banned", valueCaptor.getValue());
        assertEquals(2592000L, ttlCaptor.getValue().getSeconds(),
                "D-007: TTL 必须等于 AdminAuth.AccessExpire（2592000秒）");
    }

    /** token 为空 → INVALID_ARGUMENT，不调 Redis */
    @Test
    void emptyToken_throwsInvalidArgument() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.logout(LogoutReq.newBuilder()
                        .setToken("")
                        .build()));

        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
        verify(redis, org.mockito.Mockito.never()).opsForValue();
    }

    /** Redis 写入异常 → INTERNAL */
    @Test
    void redisError_throwsInternal() {
        when(redis.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("connection refused"))
                .when(valueOps).set(any(String.class), any(String.class), any(Duration.class));

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () ->
                logic.logout(LogoutReq.newBuilder()
                        .setToken("jwt-abc-123")
                        .build()));

        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}