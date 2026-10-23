package com.rag.follow.follow.rpc.service;

import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.rpc.client.UserRpcClient;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * {@link FollowUserExistenceService} 的三分支契约 —— 对照 Go
 * {@code service/follow/rpc/internal/logic/helper.go:81-94}。
 *
 * <h2>为什么这一类必须单独钉死</h2>
 * <p>本模块<b>已经有 37 条测试全绿</b>，却曾经把 Go 的分支①整条删掉：
 * {@code catch (StatusRuntimeException) → 一律 5001}，把「下游明确回 NOT_FOUND」
 * 也折叠成了「服务端故障」。后果是 6 个 RPC 对「用户不存在」的返回从
 * {@code NOT_FOUND + 5101} 变成 {@code INTERNAL + 5001}，而
 * <b>指标照样记 {@code result=fail}、响应码种类也没变少</b> ⇒ 监控上看不出来。
 *
 * <p>更隐蔽的是：当时 {@link UserRpcClient#isNotFound} 被写成静态方法却<b>零调用方</b> ——
 * 一个没人用的「看起来该用上」的方法，正是分支被删掉的现场痕迹。
 *
 * <p>本类把三条分支逐一钉死，并<b>直接用 {@link FollowUserExistenceService}</b>
 * （而不是绕过它测 client），确保 ① / ② / ③ 各自独立可辨。
 */
class FollowUserExistenceServiceTest {

    private UserRpcClient userRpcClient;
    private FollowUserExistenceService service;

    private void stubResponse(GetUserResp resp) {
        userRpcClient = Mockito.mock(UserRpcClient.class);
        when(userRpcClient.getUser(Mockito.anyLong())).thenReturn(resp);
        service = new FollowUserExistenceService(userRpcClient);
    }

    private void stubFailure(Status.Code code) {
        userRpcClient = Mockito.mock(UserRpcClient.class);
        when(userRpcClient.getUser(Mockito.anyLong()))
                .thenThrow(Status.fromCode(code).asRuntimeException());
        service = new FollowUserExistenceService(userRpcClient);
    }

    // =============================================================================
    // 通过路径
    // =============================================================================

    @Test
    @DisplayName("成功：found=true 且 user 非空 ⇒ 不抛异常")
    void successWhenUserExists() {
        stubResponse(GetUserResp.newBuilder()
                .setFound(true)
                .setUser(UserInfo.newBuilder().setUid(42L))
                .build());

        assertThatCode(() -> service.ensureUserExists(42L)).doesNotThrowAnyException();
    }

    // =============================================================================
    // 分支①：下游明确回 NOT_FOUND ⇒ 5101（本次修复的那条）
    // =============================================================================

    @Test
    @DisplayName("★ 分支①：下游回 NOT_FOUND ⇒ NOT_FOUND + 5101「user not found」")
    void downstreamNotFoundMapsToUserNotFound() {
        stubFailure(Status.Code.NOT_FOUND);

        assertThatThrownBy(() -> service.ensureUserExists(999L))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> {
                    StatusRuntimeException sre = (StatusRuntimeException) e;
                    assertThat(sre.getStatus().getCode())
                            .as("必须保留 NOT_FOUND，不能降级成 INTERNAL")
                            .isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(sre.getStatus().getDescription()).isEqualTo("user not found");
                    assertThat(FollowGrpcError.bizCodeFromError(sre))
                            .isEqualTo(FollowErrorCode.ERROR_USER_NOT_FOUND.getCode())
                            .isEqualTo(5101);
                });
    }

    // =============================================================================
    // 分支②：其余一切下游故障 ⇒ 5001
    // =============================================================================

    @Test
    @DisplayName("分支②：UNAVAILABLE ⇒ INTERNAL + 5001（不把下游码透传出去）")
    void downstreamUnavailableMapsToServerCommon() {
        stubFailure(Status.Code.UNAVAILABLE);

        assertThatThrownBy(() -> service.ensureUserExists(1L))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> {
                    StatusRuntimeException sre = (StatusRuntimeException) e;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(sre.getStatus().getDescription()).isEqualTo("internal server error");
                    assertThat(FollowGrpcError.bizCodeFromError(sre))
                            .isEqualTo(FollowErrorCode.ERROR_SERVER_COMMON.getCode())
                            .isEqualTo(5001);
                });
    }

    @Test
    @DisplayName("分支②：DEADLINE_EXCEEDED / INTERNAL / UNKNOWN 一律折叠成 5001")
    void allOtherDownstreamCodesCollapseTo5001() {
        for (Status.Code code : new Status.Code[]{
                Status.Code.DEADLINE_EXCEEDED, Status.Code.INTERNAL,
                Status.Code.UNKNOWN, Status.Code.CANCELLED, Status.Code.RESOURCE_EXHAUSTED}) {
            stubFailure(code);
            assertThatThrownBy(() -> service.ensureUserExists(1L))
                    .as("下游码 %s 必须折叠成 5001", code)
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(FollowGrpcError.bizCodeFromError((StatusRuntimeException) e))
                            .isEqualTo(FollowErrorCode.ERROR_SERVER_COMMON.getCode()));
        }
    }

    // =============================================================================
    // 分支③：下游成功但用户为空 ⇒ 5101
    // =============================================================================

    @Test
    @DisplayName("分支③：found=false ⇒ 5101")
    void foundFalseMapsToUserNotFound() {
        stubResponse(GetUserResp.newBuilder().setFound(false).build());

        assertThatThrownBy(() -> service.ensureUserExists(7L))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> {
                    StatusRuntimeException sre = (StatusRuntimeException) e;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(FollowGrpcError.bizCodeFromError(sre))
                            .isEqualTo(FollowErrorCode.ERROR_USER_NOT_FOUND.getCode());
                });
    }

    @Test
    @DisplayName("分支③：⚠️ found=true 但 user 未设置 ⇒ 仍 5101（必须用 hasUser，不能用 getUser()!=null）")
    void foundTrueButNoUserStillMapsToUserNotFound() {
        stubResponse(GetUserResp.newBuilder().setFound(true).build());

        assertThatThrownBy(() -> service.ensureUserExists(7L))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(FollowGrpcError.bizCodeFromError((StatusRuntimeException) e))
                        .as("proto3 的未设置 message 字段：getUser() 返回默认实例而非 null，"
                                + "用它判空会把「空用户」当成「用户存在」")
                        .isEqualTo(FollowErrorCode.ERROR_USER_NOT_FOUND.getCode()));
    }

    // =============================================================================
    // 防回退：isNotFound 必须有调用方
    // =============================================================================

    @Test
    @DisplayName("防回退：UserRpcClient.isNotFound 被 ensureUserExists 真实调用（防「零调用方」式静默删分支）")
    void isNotFoundMustActuallyBeCalled() {
        // 上一轮的漂移形态：分支①被删、isNotFound 变成零调用方的死方法。
        // 这里不 mock 掉 isNotFound（它是 static，直接用真实现），而是断言
        // 「下游 NOT_FOUND ⇒ 5101」这条可观察结果 —— 它在分支被删时会立刻变红。
        stubFailure(Status.Code.NOT_FOUND);
        assertThatThrownBy(() -> service.ensureUserExists(1L))
                .satisfies(e -> assertThat(FollowGrpcError.bizCodeFromError((StatusRuntimeException) e))
                        .as("这条红了就说明分支①又被折叠掉了")
                        .isEqualTo(5101));

        assertThat(UserRpcClient.isNotFound(
                Status.fromCode(Status.Code.NOT_FOUND).asRuntimeException())).isTrue();
        assertThat(UserRpcClient.isNotFound(
                Status.fromCode(Status.Code.UNAVAILABLE).asRuntimeException())).isFalse();
    }
}