package com.rag.users.admin.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.admin.api.client.AdminRpcClient;
import com.rag.users.admin.api.config.AdminApiProperties;
import com.rag.users.admin.api.metrics.AdminApiMetrics;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.common.jwt.JwtUtil;
import com.rag.users.admin.proto.AdminServiceGrpc;
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
 * AdminLoginService 单元测试 —— 验证 login 4 状态机业务码映射 + JWT 签发。
 *
 * <p>对应 Go 端 admin LoginLogic 的 4 状态机：
 * <ol>
 *   <li>gRPC codes.Internal → 抛 BusinessException(ErrorServerCommon 5001) + loginRpcError</li>
 *   <li>gRPC codes.NotFound  → 抛 BusinessException(ErrorUserNotExist 1003) + loginRpcError</li>
 *   <li>gRPC codes.Unauthenticated（密码错）→ 抛 BusinessException(ErrorLoginWrong 1002)</li>
 *   <li>gRPC codes.其它      → 抛 BusinessException(CodeServerBusy 1015) + loginRpcError</li>
 *   <li>JwtUtil.getToken 抛异常 → 抛 BusinessException(ErrorServerCommon 5001) + loginInternalError</li>
 *   <li>成功 → loginSuccess + 返回 LoginResp(token)</li>
 * </ol>
 */
class AdminLoginServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-256-bits-of-entropy-12345";   // 测试专用 ≥32 字节
    private static final long EXPIRE_SEC = 2592000L;

    private AdminServiceGrpc.AdminServiceBlockingStub stub;
    private AdminRpcClient rpcClient;
    private AdminApiProperties properties;
    private AdminApiMetrics metrics;
    private AdminLoginService service;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        rpcClient = Mockito.mock(AdminRpcClient.class);
        Mockito.when(rpcClient.adminRpc()).thenReturn(stub);

        properties = new AdminApiProperties();
        properties.getAdminAuth().setAccessSecret(SECRET);
        properties.getAdminAuth().setAccessExpire(EXPIRE_SEC);

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        metrics = new AdminApiMetrics(registry);

        service = new AdminLoginService(rpcClient, properties, metrics);
    }

    @Test
    void login_rpcInternalError_throwsBusinessException5001() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.ERROR_SERVER_COMMON.getCode(), ex.getCode());
    }

    @Test
    void login_rpcNotFoundError_throwsBusinessException1003() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.ERROR_USER_NOT_EXIST.getCode(), ex.getCode());
    }

    @Test
    void login_rpcOtherError_throwsBusinessException1015() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAVAILABLE));

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.CODE_SERVER_BUSY.getCode(), ex.getCode());
    }

    @Test
    void login_returnsToken() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenReturn(com.rag.users.admin.proto.LoginResp.newBuilder().setUid(12345L).build());

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        com.rag.users.admin.api.dto.LoginResp resp = service.login(req, Mockito.mock(HttpServletRequest.class));
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
        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenReturn(com.rag.users.admin.proto.LoginResp.newBuilder().setUid(42L).build());

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        long nowSec = System.currentTimeMillis() / 1000L;
        String expected = JwtUtil.getToken(SECRET, nowSec, EXPIRE_SEC, 42L);

        // 不直接 compare（nowSec 在 service 内部取），只验证 token 能被相同 secret 验签且 userId=42
        com.rag.users.admin.api.dto.LoginResp resp = service.login(req, Mockito.mock(HttpServletRequest.class));
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
        AdminApiMetrics m = new AdminApiMetrics(registry);
        AdminLoginService svc = new AdminLoginService(rpcClient, properties, m);

        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenReturn(com.rag.users.admin.proto.LoginResp.newBuilder().setUid(1L).build());

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        Counter loginSuccessCounter = registry.find("admin_api_intercept_total")
                .tag("path", "/admin/login").tag("result", "success").counter();
        assertNotNull(loginSuccessCounter);
        assertEquals(0.0, loginSuccessCounter.count());

        svc.login(req, Mockito.mock(HttpServletRequest.class));

        assertEquals(1.0, loginSuccessCounter.count());
    }

    @Test
    void login_metricsRpcErrorCounterIncremented() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AdminApiMetrics m = new AdminApiMetrics(registry);
        AdminLoginService svc = new AdminLoginService(rpcClient, properties, m);

        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        Counter loginRpcErrorCounter = registry.find("admin_api_intercept_total")
                .tag("path", "/admin/login").tag("result", "rpc_error").counter();
        assertNotNull(loginRpcErrorCounter);
        assertEquals(0.0, loginRpcErrorCounter.count());

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        assertThrows(BusinessException.class,
                () -> svc.login(req, Mockito.mock(HttpServletRequest.class)));

        assertEquals(1.0, loginRpcErrorCounter.count());
    }

    /**
     * 回归：admin-rpc 的 LoginLogic 对「密码错误」抛 Status.UNAUTHENTICATED，
     * 参考侧映射成 1002「用户名或密码错误」。
     * 曾经 admin-api 端漏了这个 case，落到 default 1015「服务繁忙」，
     * 把最常见的用户可见失败报成服务端故障。
     */
    @Test
    void login_rpcUnauthenticatedError_throwsBusinessException1002() {
        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenThrow(new StatusRuntimeException(Status.UNAUTHENTICATED));

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("wrong-password");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.ERROR_LOGIN_WRONG.getCode(), ex.getCode());
        assertEquals(1002, ex.getCode());
    }

    /**
     * 回归：JWT 签发失败属于服务端内部错误 → 5001，与参考侧 LoginLogic:104-115 一致。
     * 曾经映射成 1006「TOKEN已过期」，那是 token <b>校验</b>失败的码，语义完全不同。
     *
     * <p>用 null secret 触发签发异常（JwtUtil 内部 NPE → 被 Service 的 catch(Throwable) 捕获）。
     */
    @Test
    void login_tokenGenerationFailure_throwsBusinessException5001() {
        properties.getAdminAuth().setAccessSecret(null);

        Mockito.when(stub.login(Mockito.any(com.rag.users.admin.proto.LoginReq.class)))
                .thenReturn(com.rag.users.admin.proto.LoginResp.newBuilder().setUid(12345L).build());

        com.rag.users.admin.api.dto.LoginReq req = new com.rag.users.admin.api.dto.LoginReq();
        req.setUsername("alice");
        req.setPassword("pwd");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(req, Mockito.mock(HttpServletRequest.class)));
        assertEquals(ErrorCode.ERROR_SERVER_COMMON.getCode(), ex.getCode());
        assertEquals(5001, ex.getCode());
    }
}