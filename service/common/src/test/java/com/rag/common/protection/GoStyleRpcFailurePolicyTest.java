package com.rag.common.protection;

import io.grpc.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GoStyleRpcFailurePolicy} 对齐 Go {@code zrpc/internal/codes/accept.go:9-17}。
 *
 * <p><b>这是熔断器能不能用的前提</b>：判据若写成「非 OK 即失败」，
 * {@code NOT_FOUND}（会话不存在 5202）、{@code PERMISSION_DENIED}（无权限 5205）这类
 * 天天发生的业务错误会大量计成失败 ⇒ 熔断误开 ⇒ 打掉正常流量。
 */
@DisplayName("GoStyleRpcFailurePolicy：熔断失败判据必须与 Go 的 6 码白名单逐条一致")
class GoStyleRpcFailurePolicyTest {

    @Test
    @DisplayName("Go: codes.Acceptable —— 这 6 个码算失败，一个都不能少、不能多")
    void theSixFailureCodesAreNotAcceptable() {
        List<Status.Code> failureCodes = List.of(
                Status.Code.DEADLINE_EXCEEDED,
                Status.Code.INTERNAL,
                Status.Code.UNAVAILABLE,
                Status.Code.DATA_LOSS,
                Status.Code.UNIMPLEMENTED,
                Status.Code.RESOURCE_EXHAUSTED);

        for (Status.Code c : failureCodes) {
            assertThat(GoStyleRpcFailurePolicy.isAcceptable(c))
                    .as("Go accept.go:9-17 —— %s 必须在失败名单里", c).isFalse();
            assertThat(GoStyleRpcFailurePolicy.isFailure(c))
                    .as("%s ⇒ 算熔断失败", c).isTrue();
        }
    }

    @Test
    @DisplayName("业务错误码一律不算失败（含 UNKNOWN —— Go 对非 status 错误返回 Unknown）")
    void businessErrorsAreAcceptable() {
        List<Status.Code> acceptable = List.of(
                Status.Code.OK,
                Status.Code.UNKNOWN,
                Status.Code.INVALID_ARGUMENT,
                Status.Code.NOT_FOUND,
                Status.Code.PERMISSION_DENIED,
                Status.Code.ALREADY_EXISTS,
                Status.Code.FAILED_PRECONDITION,
                Status.Code.ABORTED,
                Status.Code.OUT_OF_RANGE,
                Status.Code.CANCELLED,
                Status.Code.UNAUTHENTICATED);

        for (Status.Code c : acceptable) {
            assertThat(GoStyleRpcFailurePolicy.isAcceptable(c))
                    .as("%s 必须算「可接受」（不计入熔断）", c).isTrue();
            assertThat(GoStyleRpcFailurePolicy.isFailure(c))
                    .as("%s ⇒ 不算熔断失败", c).isFalse();
        }
    }

    @Test
    @DisplayName("null 码按可接受处理（Go: status.Code(nil) == OK ⇒ Acceptable）")
    void nullCodeIsAcceptable() {
        assertThat(GoStyleRpcFailurePolicy.isAcceptable(null)).isTrue();
        assertThat(GoStyleRpcFailurePolicy.isFailure(null)).isFalse();
    }

    @Test
    @DisplayName("🔴 CANCELLED（客户端取消）不算失败 —— Go 的名单里没有 context.Canceled")
    void cancelledIsNotAFailure() {
        assertThat(GoStyleRpcFailurePolicy.isFailure(Status.Code.CANCELLED))
                .as("Go 的 errorx.In 名单只有 context.DeadlineExceeded，没有 context.Canceled")
                .isFalse();
    }
}
