package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.SelectAvatarReq;
import com.rag.users.user.proto.UserServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@code AvatarSelectService} 的 gRPC → 业务码映射测试。
 *
 * <p><b>为什么需要这个类（DRIFT-003）</b>
 * <p>Go 出处 {@code service/user/user/api/internal/logic/user/selectavatarlogic.go:51-61}：
 * <pre>
 *   switch st.Code() {
 *   case codes.InvalidArgument: return nil, errmsg.Error          // 500
 *   case codes.NotFound:        return nil, errmsg.Error          // 500  ← 注意不是 1003
 *   case codes.Internal:        return nil, errmsg.ErrorServerCommon // 5001
 *   default:                    return nil, errmsg.CodeServerBusy    // 1015
 *   }
 * </pre>
 * RAG 原实现直接 {@code throw e}，落到 {@code GlobalExceptionHandler.mapRPCStatusToCode}
 * 的通用映射 {@code NOT_FOUND → 1003}。两个后果：
 * <ol>
 *   <li>响应码从 Go 的 <b>500</b> 变成 <b>1003</b>；</li>
 *   <li>更严重的是它<b>抹掉了 Go 刻意保留的区分</b> —— 「头像历史不存在」(500) 与
 *       「用户不存在」(1003) 合并后，客户端可以据此枚举某个 uid 是否存在。</li>
 * </ol>
 * 该类此前的 javadoc 已经写对了（{@code codes.NotFound → Error (500) ← 注意：这里是 Error
 * 不是 ErrorUserNotExist}），代码没照做，所以没有任何测试能发现。
 *
 * <p>对照组：另外 5 个直接 rethrow 的 Service（register / update / delete / get /
 * uploadAvatar / avatarHistory）逐个核对后 NOT_FOUND 映射与 Go 一致，本类只测这一个。
 */
class AvatarSelectServiceTest {

    private UserServiceGrpc.UserServiceBlockingStub stub;
    private UserRpcClient rpcClient;
    private UserApiSupport support;
    private AvatarSelectService service;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        stub = Mockito.mock(UserServiceGrpc.UserServiceBlockingStub.class);
        rpcClient = Mockito.mock(UserRpcClient.class);
        Mockito.when(rpcClient.userRpc()).thenReturn(stub);

        support = new UserApiSupport();
        service = new AvatarSelectService(rpcClient, support);

        // claimedUID 只需 attribute "userId" 是一个正整数 Long
        request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getAttribute("userId")).thenReturn(1001L);
    }

    private SelectAvatarReq req() {
        SelectAvatarReq req = new SelectAvatarReq();
        req.setHistoryId("H-1");
        return req;
    }

    @Test
    @DisplayName("DRIFT-003：gRPC NOT_FOUND 必须映成 500（Error），不能落到通用映射的 1003")
    void notFound_mapsToError500_notUserNotExist1003() {
        Mockito.when(stub.selectAvatar(Mockito.any(com.rag.users.user.proto.SelectAvatarReq.class)))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.selectAvatar(req(), request));

        assertEquals(ErrorCode.ERROR.getCode(), ex.getCode(),
                "Go 的 selectavatarlogic.go:56 把 NOT_FOUND 映成 errmsg.Error(500)");
        assertEquals(500, ex.getCode());
    }

    @Test
    @DisplayName("InvalidArgument → 500（与 NOT_FOUND 同码，这是 Go 的既定行为）")
    void invalidArgument_mapsToError500() {
        Mockito.when(stub.selectAvatar(Mockito.any(com.rag.users.user.proto.SelectAvatarReq.class)))
                .thenThrow(new StatusRuntimeException(Status.INVALID_ARGUMENT));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.selectAvatar(req(), request));

        assertEquals(ErrorCode.ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("Internal → 5001（ErrorServerCommon），走通用映射即可，与 Go 一致")
    void internal_mapsToServerCommon5001() {
        Mockito.when(stub.selectAvatar(Mockito.any(com.rag.users.user.proto.SelectAvatarReq.class)))
                .thenThrow(new StatusRuntimeException(Status.INTERNAL));

        // Internal 不被本类拦截，原样上抛交给 GlobalExceptionHandler.mapRPCStatusToCode
        StatusRuntimeException ex = assertThrows(StatusRuntimeException.class,
                () -> service.selectAvatar(req(), request));
        assertEquals(Status.Code.INTERNAL, ex.getStatus().getCode());
    }

    @Test
    @DisplayName("history_id 为空白 → 500，且不得发起任何 RPC（对齐 Go 的 TrimSpace 检查）")
    void blankHistoryId_failsBeforeRpc() {
        SelectAvatarReq req = new SelectAvatarReq();
        req.setHistoryId("   ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.selectAvatar(req, request));

        assertEquals(ErrorCode.ERROR.getCode(), ex.getCode());
        Mockito.verify(stub, Mockito.never())
                .selectAvatar(Mockito.any(com.rag.users.user.proto.SelectAvatarReq.class));
    }

    @Test
    @DisplayName("claimedUID 失败 → 1006（ErrorTokenRuntime），不发 RPC")
    void invalidClaim_mapsToTokenRuntime1006() {
        Mockito.when(request.getAttribute("userId")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.selectAvatar(req(), request));

        assertEquals(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ex.getCode());
        Mockito.verify(stub, Mockito.never())
                .selectAvatar(Mockito.any(com.rag.users.user.proto.SelectAvatarReq.class));
    }
}
