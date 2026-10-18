package com.rag.users.user.rpc.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.LogoutResp;
import com.rag.users.user.rpc.internal.config.Config;
import com.rag.users.user.rpc.internal.model.DataModel;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 D-006 / D-007 关键语义：
 *   D-006：Redis key 必须是字面量 "user:jwt_blacklist:%s"（HTTP middleware 用同一格式查）
 *   D-007：TTL 必须等于 UserAuth.AccessExpire（2592000s）
 *
 * 框架对照：Go 端 BizRedis.SetexCtx("user:jwt_blacklist:%s", "banned", expireSeconds)
 *          Java 端：StringRedisTemplate.opsForValue().set(key, value, Duration)
 */
@ExtendWith(MockitoExtension.class)
class LogoutLogicTest {

    @Mock DataModel dataModel;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    // DRIFT-OBS-LOGIN 修复配套：mock LoggerFactoryBean（lenient: 不调 logger 的 path 不报错）
    @Mock(lenient = true) LoggerFactoryBean logger;

    Config config;
    ServiceContext svcCtx;
    LogoutLogic logic;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.getUserAuth().setAccessSecret("ae0536f9-6450-4606-8e13-5a19ed505da0");
        config.getUserAuth().setAccessExpire(2592000L); // Go 端默认值
        svcCtx = new ServiceContext(config, dataModel, redis, logger);
        logic = new LogoutLogic(svcCtx);
        // 只对需要 redis 的测试 stub，避免 strict 模式 UnnecessaryStubbing
    }

    /** D-006 + D-007 联合验证：key 格式 + TTL = AccessExpire */
    @Test
    void success_writesRedisKeyWithCorrectFormatAndTtl() {
        when(redis.opsForValue()).thenReturn(valueOps);

        String token = "eyJhbGciOiJIUzI1NiJ9.payload.signature";
        LogoutReq req = LogoutReq.newBuilder().setToken(token).build();

        LogoutResp resp = logic.logout(req);

        // 验证 resp
        assertEquals(LogoutResp.getDefaultInstance(), resp, "成功时返空 LogoutResp（Go 端 &pb.LogoutResp{}）");

        // 捕获实际写入的 key/value/ttl
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        // D-006: key 格式字面量 "user:jwt_blacklist:<token>"
        assertEquals("user:jwt_blacklist:" + token, keyCaptor.getValue(),
                "D-006：Redis key 必须是 user:jwt_blacklist:<token> 字面量");

        // value 固定 "banned"
        assertEquals("banned", valueCaptor.getValue());

        // D-007: TTL = UserAuth.AccessExpire
        assertEquals(Duration.ofSeconds(2592000L), ttlCaptor.getValue(),
                "D-007：TTL 必须等于 UserAuth.AccessExpire=2592000s");
    }

    /** 空 token → InvalidArgument（不是 200） */
    @Test
    void emptyToken_throwsInvalidArgument() {
        LogoutReq req = LogoutReq.newBuilder().setToken("").build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.logout(req));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }

    /** Redis 异常 → Internal error */
    @Test
    void redisError_throwsInternal() {
        when(redis.opsForValue()).thenReturn(valueOps);
        org.mockito.Mockito.doThrow(new RuntimeException("redis down"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        LogoutReq req = LogoutReq.newBuilder().setToken("any.jwt.token").build();
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class, () -> logic.logout(req));
        assertEquals(Status.Code.INTERNAL, e.getStatus().getCode());
    }
}