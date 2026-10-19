package com.rag.users.admin.api.service;

import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.AdminServiceGrpc;
import com.rag.users.admin.proto.GetSelfReq;
import com.rag.users.admin.proto.GetSelfResp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdentityResolver 单元测试 —— 验证 claimedUID / resolveAdmin / 3 个 sentinel exception 行为。
 *
 * <p>对应 Go 端 admin 域 identity_contract.md §1 冻结的可观察面（与 user-api 5 个 exception 不同 —— admin 域
 * AdminInfo <b>没有</b> status 字段，所以没有 ErrAdminInactiveException / ErrStatusUnavailableException）：
 * <ol>
 *   <li>Return：resolveAdmin → proto.AdminInfo</li>
 *   <li>Error：3 个 sentinel exception（InvalidClaim / AdminNotFound / IdentityMismatch）</li>
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
        assertEquals("invalid authenticated admin claim", ex.getMessage());
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

    // ============== resolveAdmin 测试 ==============

    @Test
    void resolveAdmin_requestNull_throwsErrInvalidClaim() {
        AdminServiceGrpc.AdminServiceBlockingStub stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        assertThrows(IdentityResolver.ErrInvalidClaimException.class,
                () -> IdentityResolver.resolveAdmin(null, stub));
    }

    @Test
    void resolveAdmin_stubNull_throwsErrIdentityMismatch() {
        HttpServletRequest req = mockRequestWithUid(100L);
        assertThrows(IdentityResolver.ErrIdentityMismatchException.class,
                () -> IdentityResolver.resolveAdmin(req, null));
    }

    @Test
    void resolveAdmin_rpcNotFound_throwsErrAdminNotFound() {
        HttpServletRequest req = mockRequestWithUid(100L);
        AdminServiceGrpc.AdminServiceBlockingStub stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        Mockito.when(stub.getSelf(Mockito.any(GetSelfReq.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        assertThrows(IdentityResolver.ErrAdminNotFoundException.class,
                () -> IdentityResolver.resolveAdmin(req, stub));
    }

    @Test
    void resolveAdmin_respNull_throwsErrAdminNotFound() {
        HttpServletRequest req = mockRequestWithUid(100L);
        AdminServiceGrpc.AdminServiceBlockingStub stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        Mockito.when(stub.getSelf(Mockito.any(GetSelfReq.class))).thenReturn(null);

        assertThrows(IdentityResolver.ErrAdminNotFoundException.class,
                () -> IdentityResolver.resolveAdmin(req, stub));
    }

    @Test
    void resolveAdmin_adminNullOrDefault_throwsErrAdminNotFound() {
        HttpServletRequest req = mockRequestWithUid(100L);
        AdminServiceGrpc.AdminServiceBlockingStub stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);

        // admin = null
        GetSelfResp respNullAdmin = GetSelfResp.newBuilder().build();   // admin 默认 null
        Mockito.when(stub.getSelf(Mockito.any(GetSelfReq.class))).thenReturn(respNullAdmin);
        assertThrows(IdentityResolver.ErrAdminNotFoundException.class,
                () -> IdentityResolver.resolveAdmin(req, stub));

        // admin = default（empty）
        GetSelfResp respDefaultAdmin = GetSelfResp.newBuilder()
                .setAdmin(AdminInfo.getDefaultInstance()).build();
        Mockito.when(stub.getSelf(Mockito.any(GetSelfReq.class))).thenReturn(respDefaultAdmin);
        assertThrows(IdentityResolver.ErrAdminNotFoundException.class,
                () -> IdentityResolver.resolveAdmin(req, stub));
    }

    @Test
    void resolveAdmin_uidMismatch_throwsErrIdentityMismatch() {
        HttpServletRequest req = mockRequestWithUid(100L);
        AdminServiceGrpc.AdminServiceBlockingStub stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        AdminInfo rpcAdmin = AdminInfo.newBuilder().setUid(200L).setUsername("alice").build();
        GetSelfResp resp = GetSelfResp.newBuilder().setAdmin(rpcAdmin).build();
        Mockito.when(stub.getSelf(Mockito.any(GetSelfReq.class))).thenReturn(resp);

        assertThrows(IdentityResolver.ErrIdentityMismatchException.class,
                () -> IdentityResolver.resolveAdmin(req, stub));
    }

    @Test
    void resolveAdmin_uidMatches_returnsAdmin() {
        HttpServletRequest req = mockRequestWithUid(100L);
        AdminServiceGrpc.AdminServiceBlockingStub stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        AdminInfo rpcAdmin = AdminInfo.newBuilder().setUid(100L).setUsername("alice").setEmail("a@b.c").build();
        GetSelfResp resp = GetSelfResp.newBuilder().setAdmin(rpcAdmin).build();
        Mockito.when(stub.getSelf(Mockito.any(GetSelfReq.class))).thenReturn(resp);

        AdminInfo result = IdentityResolver.resolveAdmin(req, stub);
        assertEquals(100L, result.getUid());
        assertEquals("alice", result.getUsername());
        assertEquals("a@b.c", result.getEmail());
    }

    // ============== SubjectRef 测试 ==============

    @Test
    void resolveSubjectRef_returnsPlatformAuthorityPlatform() {
        HttpServletRequest req = mockRequestWithUid(100L);
        AdminServiceGrpc.AdminServiceBlockingStub stub = Mockito.mock(AdminServiceGrpc.AdminServiceBlockingStub.class);
        AdminInfo rpcAdmin = AdminInfo.newBuilder().setUid(100L).build();
        GetSelfResp resp = GetSelfResp.newBuilder().setAdmin(rpcAdmin).build();
        Mockito.when(stub.getSelf(Mockito.any(GetSelfReq.class))).thenReturn(resp);

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