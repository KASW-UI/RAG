package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.user.api.client.UserRpcClient;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import jakarta.servlet.http.HttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <p><b>DRIFT-USERAPI-003 的守门网</b>：逐格锁定「Go 每个 Logic 的 switch ⇒ RAG 对应 Service 的返回码」。
 *
 * <h3>Go 出处（逐个 Logic 的 switch，原文，非推断）</h3>
 * <pre>
 *   user/api/internal/logic/user/logoutlogic.go:48-53
 *       case codes.Internal → errmsg.ErrorServerCommon (5001)
 *       default             → errmsg.CodeServerBusy    (1015)
 * </pre>
 * ⇒ <b>Go 的 logout switch 只有两格</b>：Internal 与「其它一律 1015」。
 * <b>不存在</b>「INVALID_ARGUMENT → 500」这一格。
 *
 * <h3>为什么这条曾经是真漂移而不是理论风险</h3>
 * user-rpc 侧 {@code UserDomainService.java:768} 对空 token 抛
 * {@code Status.INVALID_ARGUMENT "Token不能为空"}
 * （Go 对应 {@code user/rpc/internal/logic/logoutlogic.go:33}），
 * 空 token 在 API 层可达 ⇒ 修前 Go 返回 <b>1015 服务繁忙</b>、RAG 返回 <b>500 系统内部错误</b>。
 *
 * <h3>对照组的作用</h3>
 * 用例 3/4 是<b>对照组</b>：若只断言「INVALID_ARGUMENT 变 1015」，
 * 下一个 agent 完全可能把 <b>所有</b> code 都映成 1015，连 Internal 的 5001 也一起吃掉 ——
 * 那就成了新的漂移。**没有对照组的定向修复等于没修。**
 *
 * <p>证据强度：<b>真 MVC 之外的纯单元层</b>（mock 掉 {@code UserRpcClient} 这个 gRPC 边界），
 * 故本类是<b>回归守门，不是一致性证据</b>；一致性证据是 {@code docs/DRIFT/users/user/api.md} §7
 * 里逐格列出的两侧 switch 对照，以及 user-rpc 侧实际抛出的 code 全量枚举。
 */
class UserLogoutErrorCodeParityTest {

    private final UserRpcClient client = mock(UserRpcClient.class);

    /** 造一个「rpc 抛出指定 code」的 Service；gRPC 边界被 mock，其余全是真实实现。 */
    private UserLogoutService serviceWithRpcFailure(Status.Code code, String description) {
        com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub stub =
                Mockito.mock(com.rag.users.user.proto.UserServiceGrpc.UserServiceBlockingStub.class);
        Mockito.when(stub.logout(Mockito.any()))
                .thenThrow(new StatusRuntimeException(Status.fromCode(code).withDescription(description)));
        Mockito.when(client.userRpc()).thenReturn(stub);
        return new UserLogoutService(client);
    }

    /**
     * 带 "jwt_token" 属性的 request。
     *
     * <p>⚠️ 不设这个属性的话，{@code logout} 会在第一行就抛 1004
     * （Token 不存在），<b>根本走不到 RPC</b>，用例就会「全红但一条断言都没测到」——
     * 这与 {@code PIT-USERSCOMMON-001}（断言被短路）是同一类。
     */
    private static HttpServletRequest reqWithToken() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute("jwt_token")).thenReturn("a-token");
        return req;
    }

    @Test
    @DisplayName("Go logoutlogic.go:49 → Internal 必须 5001 系统内部错误")
    void internalMapsTo5001() {
        UserLogoutService svc = serviceWithRpcFailure(Status.Code.INTERNAL, "注销失败,Redis写入异常");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.logout(null, reqWithToken()));
        assertEquals(5001, ex.getCode());
    }

    @Test
    @DisplayName("Go logoutlogic.go:52 default → InvalidArgument 必须 1015 服务繁忙（修复前是 500）")
    void invalidArgumentMapsTo1015Not500() {
        UserLogoutService svc = serviceWithRpcFailure(Status.Code.INVALID_ARGUMENT, "Token不能为空");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.logout(null, reqWithToken()));
        assertEquals(1015, ex.getCode(), "Go 的 logout switch 没有 INVALID_ARGUMENT 这一格，必须落 default");
    }

    // ── 对照组：下面两格 Go 也走 default，RAG 必须同样落 1015 ──

    @Test
    @DisplayName("对照组：NotFound / AlreadyExists 在 Go 的 logout switch 里同样落 default ⇒ 1015")
    void otherCodesAlsoFallToDefault() {
        for (Status.Code c : new Status.Code[]{Status.Code.NOT_FOUND, Status.Code.ALREADY_EXISTS,
                Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED, Status.Code.UNKNOWN}) {
            UserLogoutService svc = serviceWithRpcFailure(c, "x");
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> svc.logout(null, reqWithToken()));
            assertEquals(1015, ex.getCode(), () -> c + " 在 Go 的 logout switch 里应落 default(1015)");
        }
    }

    @Test
    @DisplayName("对照组：Internal 不得被一起映成 1015（防「全映 1015」这种过度修复）")
    void internalIsNotCollapsedIntoDefault() {
        // 显式再断言一次 5001，与上面 internalMapsTo5001 构成对照
        UserLogoutService svc = serviceWithRpcFailure(Status.Code.INTERNAL, "boom");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.logout(null, reqWithToken()));
        assertEquals(5001, ex.getCode(), "Internal 被一起映成 1015 ⇒ 修复过头，制造了新漂移");
    }
}