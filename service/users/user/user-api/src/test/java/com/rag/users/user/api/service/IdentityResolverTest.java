package com.rag.users.user.api.service;

import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdentityResolver 单元测试 —— 验证 claimedUID / resolveUser / 5 个 sentinel exception 行为。
 *
 * <p>对应 Go 端 identity_contract.md §1 冻结的 6 个可观察面：
 * <ol>
 *   <li>Return：resolveUser → proto.UserInfo（status=0）</li>
 *   <li>Error：5 个 sentinel exception</li>
 *   <li>Log：本测试不验证日志</li>
 *   <li>Trace/Context：从 request attribute "userId" 取正整数 UID</li>
 *   <li>Metric：本类不写 metrics</li>
 *   <li>Lifecycle：纯函数，无状态</li>
 * </ol>
 */
class IdentityResolverTest {

    // ============== claimedUID 测试 ==============

    @Test
    void claimedUID_requestNull_throwsErrInvalidClaim() {
        IdentityResolver.ErrInvalidClaimException ex = assertThrows(
                IdentityResolver.ErrInvalidClaimException.class,
                () -> IdentityResolver.claimedUID(null)
        );
        assertEquals("invalid authenticated user claim", ex.getMessage());
    }

    @Test
    void claimedUID_attrMissing_throwsErrInvalidClaim() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getAttribute("userId")).thenReturn(null);
        assertThrows(IdentityResolver.ErrInvalidClaimException.class,
                () -> IdentityResolver.claimedUID(req));
    }

    @Test
    void claimedUID_attrWrongType_throwsErrInvalidClaim() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getAttribute("userId")).thenReturn("not-a-long");
        assertThrows(IdentityResolver.ErrInvalidClaimException.class,
                () -> IdentityResolver.claimedUID(req));
    }

    @Test
    void claimedUID_attrZeroOrNegative_throwsErrInvalidClaim() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getAttribute("userId")).thenReturn(0L);
        assertThrows(IdentityResolver.ErrInvalidClaimException.class,
                () -> IdentityResolver.claimedUID(req));

        Mockito.when(req.getAttribute("userId")).thenReturn(-1L);
        assertThrows(IdentityResolver.ErrInvalidClaimException.class,
                () -> IdentityResolver.claimedUID(req));
    }

    @Test
    void claimedUID_attrValidLong_returnsUid() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getAttribute("userId")).thenReturn(12345L);
        assertEquals(12345L, IdentityResolver.claimedUID(req));
    }

    // ============== resolveUser 测试 ==============

    @Test
    void resolveUser_usersNull_throwsErrIdentityMismatch() {
        HttpServletRequest req = mockRequestWithUid(100L);
        assertThrows(IdentityResolver.ErrIdentityMismatchException.class,
                () -> IdentityResolver.resolveUser(req, null));
    }

    @Test
    void resolveUser_rpcNotFound_throwsErrUserNotFound() {
        HttpServletRequest req = mockRequestWithUid(100L);
        UserServiceGrpc.UserServiceBlockingStub stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        Mockito.when(stub.getUser(Mockito.any(GetUserReq.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(IdentityResolver.ErrUserNotFoundException.class,
                () -> IdentityResolver.resolveUser(req, stub));
    }

    @Test
    void resolveUser_rpcFoundFalse_throwsErrUserNotFound() {
        HttpServletRequest req = mockRequestWithUid(100L);
        UserServiceGrpc.UserServiceBlockingStub stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        GetUserResp resp = GetUserResp.newBuilder().setFound(false).build();
        Mockito.when(stub.getUser(Mockito.any(GetUserReq.class))).thenReturn(resp);

        assertThrows(IdentityResolver.ErrUserNotFoundException.class,
                () -> IdentityResolver.resolveUser(req, stub));
    }

    @Test
    void resolveUser_uidMismatch_throwsErrIdentityMismatch() {
        HttpServletRequest req = mockRequestWithUid(100L);
        UserServiceGrpc.UserServiceBlockingStub stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        UserInfo rpcUser = UserInfo.newBuilder().setUid(200L).setStatus(0L).build();
        GetUserResp resp = GetUserResp.newBuilder().setFound(true).setUser(rpcUser).build();
        Mockito.when(stub.getUser(Mockito.any(GetUserReq.class))).thenReturn(resp);

        assertThrows(IdentityResolver.ErrIdentityMismatchException.class,
                () -> IdentityResolver.resolveUser(req, stub));
    }

    @Test
    void resolveUser_statusMissing_throwsErrStatusUnavailable() {
        HttpServletRequest req = mockRequestWithUid(100L);
        UserServiceGrpc.UserServiceBlockingStub stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        UserInfo rpcUser = UserInfo.newBuilder().setUid(100L).build();   // status 字段缺失
        GetUserResp resp = GetUserResp.newBuilder().setFound(true).setUser(rpcUser).build();
        Mockito.when(stub.getUser(Mockito.any(GetUserReq.class))).thenReturn(resp);

        assertThrows(IdentityResolver.ErrStatusUnavailableException.class,
                () -> IdentityResolver.resolveUser(req, stub));
    }

    @Test
    void resolveUser_statusNonZero_throwsErrUserInactive() {
        HttpServletRequest req = mockRequestWithUid(100L);
        UserServiceGrpc.UserServiceBlockingStub stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        UserInfo rpcUser = UserInfo.newBuilder().setUid(100L).setStatus(1L).build();   // banned
        GetUserResp resp = GetUserResp.newBuilder().setFound(true).setUser(rpcUser).build();
        Mockito.when(stub.getUser(Mockito.any(GetUserReq.class))).thenReturn(resp);

        assertThrows(IdentityResolver.ErrUserInactiveException.class,
                () -> IdentityResolver.resolveUser(req, stub));
    }

    @Test
    void resolveUser_statusZero_returnsUser() {
        HttpServletRequest req = mockRequestWithUid(100L);
        UserServiceGrpc.UserServiceBlockingStub stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        UserInfo rpcUser = UserInfo.newBuilder().setUid(100L).setUsername("alice").setStatus(0L).build();
        GetUserResp resp = GetUserResp.newBuilder().setFound(true).setUser(rpcUser).build();
        Mockito.when(stub.getUser(Mockito.any(GetUserReq.class))).thenReturn(resp);

        UserInfo result = IdentityResolver.resolveUser(req, stub);
        assertEquals(100L, result.getUid());
        assertEquals("alice", result.getUsername());
        assertEquals(0L, result.getStatus());
    }

    // ============== SubjectRef 测试 ==============

    @Test
    void resolveSubjectRef_returnsPlatformAuthorityPlatform() {
        HttpServletRequest req = mockRequestWithUid(100L);
        UserServiceGrpc.UserServiceBlockingStub stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        UserInfo rpcUser = UserInfo.newBuilder().setUid(100L).setStatus(0L).build();
        GetUserResp resp = GetUserResp.newBuilder().setFound(true).setUser(rpcUser).build();
        Mockito.when(stub.getUser(Mockito.any(GetUserReq.class))).thenReturn(resp);

        IdentityResolver.SubjectRef ref = IdentityResolver.resolveSubjectRef(req, stub);
        assertEquals("rtw.identity", ref.getAuthorityId());
        assertEquals("platform", ref.getTenantId());
        assertEquals("100", ref.getSubjectId());
    }

    private HttpServletRequest mockRequestWithUid(long uid) {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        Mockito.when(req.getAttribute("userId")).thenReturn(uid);
        return req;
    }
}
