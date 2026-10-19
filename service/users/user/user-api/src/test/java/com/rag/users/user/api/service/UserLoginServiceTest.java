package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.config.UserApiProperties;
import com.rag.users.user.api.metrics.UserApiMetrics;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserLoginService 单元测试 —— 验证 login 5 状态机业务码映射 + JWT 签发。
 *
 * <p>对应 Go 端 LoginLogic 的 6 状态机：
 * <ol>
 *   <li>gRPC codes.Internal → 抛 BusinessException(ErrorServerCommon 5001)</li>
 *   <li>gRPC codes.其它 → 抛 BusinessException(CodeServerBusy 1015)</li>
 *   <li>rpcResp.Status == 1 → 抛 BusinessException(ErrorLoginWrong 1002)</li>
 *   <li>rpcResp.Status == 2 → 抛 BusinessException(ErrorUserBanned 1011)</li>
 *   <li>JwtUtil.getToken 抛异常 → 抛 BusinessException(ErrorTokenRuntime 1006)</li>
 *   <li>成功 → 返回 LoginResp(token)</li>
 * </ol>
 */
class UserLoginServiceTest {

    private static final String SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";
    private static final long EXPIRE_SEC = 2592000L;

    private UserServiceGrpc.UserServiceBlockingStub stub;
    private UserRpcClient rpcClient;
    private UserApiProperties properties;
    private UserApiMetrics metrics;
    private UserLoginService service;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        rpcClient = Mockito.mock(UserRpcClient.class);
        Mockito.when(rpcClient.userRpc()).thenReturn(stub);

        properties = new UserApiProperties();
        properties.getUserAuth().setAccessSecret(SECRET);
        properties.getUserAuth().setAccessExpire(EXPIRE_SEC);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metrics = new UserApiMetrics(registry);

        service = new UserLoginService(rpcClient, properties, metrics);
    }

    @Test
    void login_rpcInternalError_throwsBusinessException5001() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.user.proto.LoginReq.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        com.rag.users.user.api.dto.LoginReq req = new com.rag.users.user.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.ERROR_SERVER_COMMON.getCode(), ex.getCode());
    }

    @Test
    void login_rpcOtherError_throwsBusinessException1015() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.user.proto.LoginReq.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        com.rag.users.user.api.dto.LoginReq req = new com.rag.users.user.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.CODE_SERVER_BUSY.getCode(), ex.getCode());
    }

    @Test
    void login_statusOne_throwsBusinessException1002() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.user.proto.LoginReq.class)))
                .thenReturn(LoginResp.newBuilder().setUid(1L).setStatus(1L).build());

        com.rag.users.user.api.dto.LoginReq req = new com.rag.users.user.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("wrong");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.ERROR_LOGIN_WRONG.getCode(), ex.getCode());
    }

    @Test
    void login_statusTwo_throwsBusinessException1011() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.user.proto.LoginReq.class)))
                .thenReturn(LoginResp.newBuilder().setUid(1L).setStatus(2L).build());

        com.rag.users.user.api.dto.LoginReq req = new com.rag.users.user.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.ERROR_USER_BANNED.getCode(), ex.getCode());
    }

    @Test
    void login_statusZero_returnsToken() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.user.proto.LoginReq.class)))
                .thenReturn(LoginResp.newBuilder().setUid(12345L).setStatus(0L).build());

        com.rag.users.user.api.dto.LoginReq req = new com.rag.users.user.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        com.rag.users.user.api.dto.LoginResp resp = service.login(req, Mockito.mock(HttpServletRequest.class));
        assertNotNull(resp.getToken());
        // 解析 JWT 检查 userId claim
        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = io.jsonwebtoken.Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(resp.getToken());
        assertEquals(12345, ((Number) jws.getPayload().get("userId")).longValue());
    }

    @Test
    void login_success_matchesGoJwtUtilGetToken() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.user.proto.LoginReq.class)))
                .thenReturn(LoginResp.newBuilder().setUid(42L).setStatus(0L).build());

        com.rag.users.user.api.dto.LoginReq req = new com.rag.users.user.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        long nowSec = System.currentTimeMillis() / 1000L;
        String expected = JwtUtil.getToken(SECRET, nowSec, EXPIRE_SEC, 42L);

        // 不直接 compare（nowSec 在 service 内部取），只验证 token 能被相同 secret 验签且 userId=42
        com.rag.users.user.api.dto.LoginResp resp = service.login(req, Mockito.mock(HttpServletRequest.class));
        assertNotNull(resp.getToken());
        io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws = io.jsonwebtoken.Jwts.parser()
                .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(resp.getToken());
        assertEquals(42L, ((Number) jws.getPayload().get("userId")).longValue());
        // 期望 token 至少与 expected 同样长度（避免奇怪的字符串拼接）
        assertTrue(resp.getToken().length() >= expected.length() - 10);
    }

    @Test
    void login_metricsCounterIncremented() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserApiMetrics m = new UserApiMetrics(registry);
        UserLoginService svc = new UserLoginService(rpcClient, properties, m);

        Mockito.when(stub.login(Mockito.any(com.rag.users.user.proto.LoginReq.class)))
                .thenReturn(LoginResp.newBuilder().setUid(1L).setStatus(0L).build());

        com.rag.users.user.api.dto.LoginReq req = new com.rag.users.user.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        Counter loginSuccessCounter = registry.find("user_api_intercept_total")
                .tag("path", "/user/login").tag("result", "success").counter();
        assertNotNull(loginSuccessCounter);
        assertEquals(0.0, loginSuccessCounter.count());

        svc.login(req, Mockito.mock(HttpServletRequest.class));

        assertEquals(1.0, loginSuccessCounter.count());
    }
}
