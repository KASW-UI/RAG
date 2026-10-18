package com.rag.users.user.api.internal.identity;

import com.rag.users.user.proto.AvatarHistoryItem;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 对应 Go 端 internal/identity/resolve_test.go。
 *
 * <p>六个可观察面行为测试 —— 1:1 翻译 Go 端的语义断言，不重写测试设计。
 */
class IdentityResolverTest {

    /**
     * 对应 Go {@code TestResolveSubjectRefFromVerifiedGoZeroJWT} 的核心断言：
     * 已认证的 UID 经过 RPC 核对后，必须生成
     * {@code {authority_id:"rtw.identity", tenant_id:"platform", subject_id:"<UID>"}}
     * 三元组；恶意请求体 / 头部不能覆盖服务端生成值。
     */
    @Test
    void resolveSubjectRefBuildsFixedPlatformTuple() {
        UserServiceGrpc.UserServiceBlockingStub stub = mock(UserServiceGrpc.UserServiceBlockingStub.class);
        UserInfo rpcUser = UserInfo.newBuilder()
                .setUid(9123L)
                .setUsername("member")
                .setStatus(0L)
                .build();
        when(stub.getUser(any(GetUserReq.class)))
                .thenReturn(GetUserResp.newBuilder().setFound(true).setUser(rpcUser).build());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("userId", 9123L);
        // 模拟恶意 client 通过 header / body 注入的"假"subject —— IdentityResolver 必须忽略
        req.addHeader("X-User-ID", "666");
        req.setParameter("subject_ref", "evil-value");

        IdentityResolver.SubjectRef ref = IdentityResolver.resolveSubjectRef(req, stub);
        assertEquals("rtw.identity", ref.getAuthorityId());
        assertEquals("platform", ref.getTenantId());
        assertEquals("9123", ref.getSubjectId());
    }

    /**
     * 对应 Go {@code TestResolveUserRejectsInvalidOrMismatchedIdentity} 的多 case 表驱动断言：
     * 6 种 claim 缺失/类型错 → ErrInvalidClaim；8 种 RPC 错误 / UID 不匹配 / status 缺失/非零 → 各自对应 sentinel
     */
    @Test
    void resolveUserRejectsInvalidOrMismatchedIdentity() {
        // Part A: claim 缺失 / 类型错 → ErrInvalidClaim
        // 注：Java 侧 IdentityResolver.claimedUID 只接受 Long 类型；
        // 对齐 Go 端 json.Number 类型检查 —— 非 Long 类型视为类型错
        for (Object badClaim : new Object[]{
                null,            // 完全没设 userId attr
                "9123",          // 类型错：String 而非 Long
                Integer.valueOf(9123),  // 类型错：Integer 而非 Long
                Long.valueOf(0L),   // ≤0
                Long.valueOf(-1L),  // 负数
                Long.valueOf(-9223372036854775808L)  // 溢出（即使 Long 范围也 ≤ 0）
        }) {
            MockHttpServletRequest req = new MockHttpServletRequest();
            if (badClaim != null) {
                req.setAttribute("userId", badClaim);
            }
            try {
                IdentityResolver.resolveUser(req, null);
                fail("setAttribute(" + badClaim + ") should throw ErrInvalidClaim");
            } catch (IdentityResolver.ErrInvalidClaimException expected) {
                // OK
            }
        }

        // Part B: 有效 claim + 各种 RPC 错误 → 各自 sentinel
        // 每个 case 用独立的 mock stub 避免 Mockito 状态污染

        // B1: gRPC NotFound → ErrUserNotFound
        runUserRpcCase("B1_notFound",
                        new StatusRuntimeException(Status.NOT_FOUND.withDescription("missing")),
                        IdentityResolver.ErrUserNotFoundException.class,
                        expected -> assertTrue(expected.getMessage().contains("user RPC")));

        // B2: resp.Found=false → ErrUserNotFound
        runUserRpcCase("B2_foundFalse",
                        GetUserResp.newBuilder().setFound(false).build(),
                        IdentityResolver.ErrUserNotFoundException.class,
                        null);

        // B3: resp.User=null → ErrUserNotFound
        runUserRpcCase("B3_nullUser",
                        GetUserResp.newBuilder().setFound(true).build(),
                        IdentityResolver.ErrUserNotFoundException.class,
                        null);

        // B4: resp.User.Uid != claim uid → ErrIdentityMismatch
        runUserRpcCase("B4_uidMismatch",
                        GetUserResp.newBuilder().setFound(true)
                                .setUser(UserInfo.newBuilder().setUid(7777L).setStatus(0L).build()).build(),
                        IdentityResolver.ErrIdentityMismatchException.class,
                        null);

        // B5: UserInfo.status 字段缺失（旧 RPC） → ErrStatusUnavailable
        runUserRpcCase("B5_oldRpc",
                        GetUserResp.newBuilder().setFound(true)
                                .setUser(UserInfo.newBuilder().setUid(9123L).build()).build(),
                        IdentityResolver.ErrStatusUnavailableException.class,
                        null);

        // B6: status=1 banned → ErrUserInactive
        runUserRpcCase("B6_banned",
                        GetUserResp.newBuilder().setFound(true)
                                .setUser(UserInfo.newBuilder().setUid(9123L).setStatus(1L).build()).build(),
                        IdentityResolver.ErrUserInactiveException.class,
                        null);

        // B7: status=2 unknown nonzero → ErrUserInactive（与 Go 端注释一致）
        runUserRpcCase("B7_status2",
                        GetUserResp.newBuilder().setFound(true)
                                .setUser(UserInfo.newBuilder().setUid(9123L).setStatus(2L).build()).build(),
                        IdentityResolver.ErrUserInactiveException.class,
                        null);

        // B8: upstream 错误（非 NotFound）必须透传
        StatusRuntimeException unavailable = new StatusRuntimeException(Status.UNAVAILABLE.withDescription("down"));
        runUserRpcCase("B8_upstream",
                        unavailable,
                        StatusRuntimeException.class,
                        e -> assertEquals(Status.Code.UNAVAILABLE, ((StatusRuntimeException) e).getStatus().getCode()));
    }

    private void runUserRpcCase(String name,
                                Object rpcResultOrThrow,
                                Class<? extends Throwable> expectedExceptionClass,
                                java.util.function.Consumer<Throwable> extraAssertion) {
        UserServiceGrpc.UserServiceBlockingStub stub = mock(UserServiceGrpc.UserServiceBlockingStub.class);
        // 用 doReturn().when() 模式（不调用 stub.getUser 本身），避免 grpc-java BlockingStub
        // 内部方法在 stubbing 期间被调用的状态污染
        if (rpcResultOrThrow instanceof Throwable) {
            org.mockito.Mockito.doThrow((StatusRuntimeException) rpcResultOrThrow)
                    .when(stub).getUser(any(GetUserReq.class));
        } else {
            org.mockito.Mockito.doReturn((GetUserResp) rpcResultOrThrow)
                    .when(stub).getUser(any(GetUserReq.class));
        }
        try {
            IdentityResolver.resolveUser(validRequest(9123L), stub);
            fail(name + " should throw " + expectedExceptionClass.getSimpleName());
        } catch (Throwable actual) {
            if (!expectedExceptionClass.isInstance(actual)) {
                throw new AssertionError(name + ": expected "
                        + expectedExceptionClass.getSimpleName() + " but got "
                        + actual.getClass().getSimpleName() + ": " + actual.getMessage(), actual);
            }
            if (extraAssertion != null) {
                extraAssertion.accept(actual);
            }
        }
    }

    /**
     * 对应 Go {@code TestResolveSubjectRefKeepsDecimalUIDPrecision}：
     * MaxInt64 UID 必须保持十进制字符串精度，不能因 Long.toString() 浮点转换丢失。
     */
    @Test
    void resolveSubjectRefKeepsDecimalUIDPrecision() {
        UserServiceGrpc.UserServiceBlockingStub stub = mock(UserServiceGrpc.UserServiceBlockingStub.class);
        long maxUid = Long.MAX_VALUE;
        when(stub.getUser(any(GetUserReq.class)))
                .thenReturn(GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(maxUid).setStatus(0L).build()).build());

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("userId", maxUid);
        IdentityResolver.SubjectRef ref = IdentityResolver.resolveSubjectRef(req, stub);

        assertEquals("rtw.identity", ref.getAuthorityId());
        assertEquals("platform", ref.getTenantId());
        assertEquals(Long.toString(maxUid), ref.getSubjectId());
        assertEquals("9223372036854775807", ref.getSubjectId());
    }

    /**
     * 对应 Go {@code TestUserStatusProto3Presence}：
     * 三种 wire 序列化结果（old-provider 缺字段 / active=0 / banned=1）必须可区分，
     * IdentityResolver 对缺字段抛 {@link IdentityResolver.ErrStatusUnavailableException}，
     * 对非零 status 抛 {@link IdentityResolver.ErrUserInactiveException}。
     */
    @Test
    void userStatusProto3Presence() {
        UserServiceGrpc.UserServiceBlockingStub stub = mock(UserServiceGrpc.UserServiceBlockingStub.class);

        // Case 1: 旧 RPC 回执（status 字段未设置）
        when(stub.getUser(any(GetUserReq.class)))
                .thenReturn(GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(9123L).build()).build());
        assertThrows(IdentityResolver.ErrStatusUnavailableException.class,
                () -> IdentityResolver.resolveUser(validRequest(9123L), stub));

        // Case 2: active (status=0)
        when(stub.getUser(any(GetUserReq.class)))
                .thenReturn(GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(9123L).setStatus(0L).build()).build());
        UserInfo activeUser = IdentityResolver.resolveUser(validRequest(9123L), stub);
        assertEquals(9123L, activeUser.getUid());
        assertTrue(activeUser.hasStatus());
        assertEquals(0L, activeUser.getStatus());

        // Case 3: banned (status=1)
        when(stub.getUser(any(GetUserReq.class)))
                .thenReturn(GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(9123L).setStatus(1L).build()).build());
        assertThrows(IdentityResolver.ErrUserInactiveException.class,
                () -> IdentityResolver.resolveUser(validRequest(9123L), stub));
    }

    private static MockHttpServletRequest validRequest(long uid) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setAttribute("userId", uid);
        return r;
    }
}