package com.rag.users.user.api.internal.logic;

import com.rag.common.logger.Logger;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.internal.identity.IdentityResolver;
import com.rag.users.user.api.internal.svc.ServiceContext;
import com.rag.users.user.api.internal.types.ApiTypes;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 对应 Go 端 internal/logic/user/getuserlogic_test.go（{@code TestGetUserUsesAuthenticatedRPCIdentity}）。
 *
 * <p>七个测试 case 1:1 翻译，验证：
 * <ol>
 *   <li>合法 claim + 合法 RPC 回执 → Success + 1 次 RPC 调用 + 回执 UID 与 claim UID 一致</li>
 *   <li>banned (status=1) → ErrorUserBanned</li>
 *   <li>旧 RPC（缺 status）→ CodeServerBusy</li>
 *   <li>RPC NotFound → ErrorUserNotExist</li>
 *   <li>UID mismatch (9911 ≠ 9123) → ErrorServerCommon</li>
 *   <li>claim uid=0 → ErrorTokenRuntime + 0 次 RPC 调用（不查 RPC）</li>
 *   <li>claim 类型错（int64 而非 Long/Number）→ ErrorTokenRuntime + 0 次 RPC 调用</li>
 * </ol>
 */
class GetUserLogicTest {

    @BeforeAll
    static void initLogger() {
        // 对齐 Go 端 logger.Init("user-identity-test")
        Logger.init("user-identity-test");
    }

    @Test
    void valid_returnsSuccess() {
        runCase("valid",
                "9123",                                                                                       // claim 字符串
                GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(9123L).setUsername("member").setStatus(0L).build()).build(),
                null,
                ErrorCode.SUCCESS.getCode(),
                1,
                true,
                9123L);
    }

    @Test
    void banned_returnsUserBanned() {
        runCase("banned",
                "9123",
                GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(9123L).setStatus(1L).build()).build(),
                null,
                ErrorCode.ERROR_USER_BANNED.getCode(),
                1,
                false,
                null);
    }

    @Test
    void oldRpc_missingStatus_returnsServerBusy() {
        runCase("old-rpc",
                "9123",
                GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(9123L).build()).build(),
                null,
                ErrorCode.CODE_SERVER_BUSY.getCode(),
                1,
                false,
                null);
    }

    @Test
    void deleted_returnsUserNotExist() {
        runCase("deleted",
                "9123",
                null,
                new StatusRuntimeException(Status.NOT_FOUND.withDescription("gone")),
                ErrorCode.ERROR_USER_NOT_EXIST.getCode(),
                1,
                false,
                null);
    }

    @Test
    void mismatched_returnsServerCommon() {
        runCase("mismatched",
                "9123",
                GetUserResp.newBuilder().setFound(true)
                        .setUser(UserInfo.newBuilder().setUid(9911L).build()).build(),
                null,
                ErrorCode.ERROR_SERVER_COMMON.getCode(),
                1,
                false,
                null);
    }

    @Test
    void zeroClaim_returnsTokenRuntimeWithoutRpcCall() {
        runCase("zero",
                "0",
                null,
                null,
                ErrorCode.ERROR_TOKEN_RUNTIME.getCode(),
                0,
                false,
                null);
    }

    @Test
    void wrongTypeClaim_returnsTokenRuntimeWithoutRpcCall() {
        // Go 端 claim 是 int64(9123)，绕过 json.Number 的类型检查 → ErrInvalidClaim
        // Java 侧 request attribute 是 Long 时 instanceof Long 才合法；
        // 模拟"非 Long 类型"—— 用 String 假装 userId
        GetUserLogic logic = new GetUserLogic(new ServiceContext(null, mock(UserServiceGrpc.UserServiceBlockingStub.class)), new com.rag.common.spring.LoggerFactoryBean("user-api-test"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute("userId", Integer.valueOf(9123));   // 类型错：Integer 不是 Long

        LogicResult<ApiTypes.GetUserResp> r = logic.getUser(new ApiTypes.GetUserReq(), req);
        assertEquals(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), r.code());
        assertNull(r.data());
    }

    // ============== helper ==============

    private static void runCase(String name,
                                String claimStr,
                                GetUserResp rpcResp,
                                StatusRuntimeException rpcErr,
                                int expectedCode,
                                int expectedRpcCalls,
                                boolean expectResultUser,
                                Long expectedUid) {
        UserServiceGrpc.UserServiceBlockingStub stub = mock(UserServiceGrpc.UserServiceBlockingStub.class);
        int[] callCount = new int[1];
        when(stub.getUser(any(GetUserReq.class))).thenAnswer(inv -> {
            callCount[0]++;
            if (rpcErr != null) throw rpcErr;
            return rpcResp;
        });

        GetUserLogic logic = new GetUserLogic(new ServiceContext(null, stub), new com.rag.common.spring.LoggerFactoryBean("user-api-test"));
        MockHttpServletRequest req = new MockHttpServletRequest();
        // 与 Go 端 claim 类型对齐：Go 用 json.Number("9123")，Java 用 Long.valueOf(9123L)
        if (claimStr != null) {
            req.setAttribute("userId", Long.valueOf(claimStr));
        }

        LogicResult<ApiTypes.GetUserResp> r = logic.getUser(new ApiTypes.GetUserReq(), req);
        assertEquals(expectedCode, r.code(), name + ": code");
        assertEquals(expectedRpcCalls, callCount[0], name + ": rpc calls");

        if (expectResultUser) {
            assertNotNull(r.data(), name + ": data should not be null");
            ApiTypes.GetUserResp out = r.data();
            assertTrue(out.isFound(), name + ": Found");
            assertEquals(String.valueOf(expectedUid), out.getUser().getUid(), name + ": uid");
            assertEquals("member", out.getUser().getUsername(), name + ": username");
        } else {
            assertNull(r.data(), name + ": data should be null on failure");
        }
    }
}