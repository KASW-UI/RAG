package com.rag.follow.follow.rpc.service;

import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowErrorMessages;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.RelationReq;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FollowRpcSupport} 的行为契约 —— 对照 Go 端
 * {@code service/follow/rpc/internal/logic/helper.go:23-147} 逐条验证。
 *
 * <p>本类是<b>纯单测</b>（不装配 Spring、不碰 DB、不碰 gRPC）：
 * helper.go 里的校验 / 归一化 / 响应工厂 / 权重函数全是纯函数，
 * 不该也不能为了它们付出上下文启动的代价。
 * 真 PG / 真启动的验证分别在 {@code FollowPersistenceContractTest}
 * 与 {@code FollowGrpcWiringTest}。
 */
class FollowRpcSupportTest {

    private final FollowRpcSupport support = new FollowRpcSupport();

    // =============================================================================
    // validateRelationReq —— helper.go:27-35
    // =============================================================================

    @Nested
    @DisplayName("validateRelationReq：两条错误的 gRPC 码相同、业务码不同")
    class ValidateRelationReq {

        @Test
        @DisplayName("userId<=0 ⇒ INVALID_ARGUMENT + 5003")
        void nonPositiveUserIdIsInvalidParam() {
            assertThatThrownBy(() -> support.validateRelationReq(
                    RelationReq.newBuilder().setUserId(0).setTargetId(2).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> {
                        StatusRuntimeException sre = (StatusRuntimeException) e;
                        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                        assertThat(FollowGrpcErrorCodes.bizCodeOf(sre))
                                .isEqualTo(FollowErrorCode.ERROR_INVALID_PARAM.getCode());
                    });
        }

        @Test
        @DisplayName("targetId<=0 ⇒ 5003（不是 5201）—— 顺序不可调换")
        void nonPositiveTargetIdIsInvalidParamNotSelfRelation() {
            assertThatThrownBy(() -> support.validateRelationReq(
                    RelationReq.newBuilder().setUserId(1).setTargetId(-1).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(FollowGrpcErrorCodes.bizCodeOf((StatusRuntimeException) e))
                            .isEqualTo(FollowErrorCode.ERROR_INVALID_PARAM.getCode()));
        }

        @Test
        @DisplayName("userId==targetId ⇒ INVALID_ARGUMENT + 5201（自关系）")
        void selfRelationHasDistinctBizCode() {
            assertThatThrownBy(() -> support.validateRelationReq(
                    RelationReq.newBuilder().setUserId(7).setTargetId(7).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> {
                        StatusRuntimeException sre = (StatusRuntimeException) e;
                        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
                        // ⭐ 靠 description 文案反查才能拿到 5201；这是 follow-api 侧业务码的唯一来源
                        assertThat(FollowGrpcErrorCodes.bizCodeOf(sre))
                                .isEqualTo(FollowErrorCode.ERROR_SELF_RELATION.getCode());
                        assertThat(sre.getStatus().getDescription())
                                .as("description 必须逐字等于 Go 的 GetErrMsg(ErrorSelfRelation)")
                                .isEqualTo("self relation is not allowed");
                    });
        }

        @Test
        @DisplayName("合法请求不抛异常")
        void validRequestPasses() {
            support.validateRelationReq(RelationReq.newBuilder().setUserId(1).setTargetId(2).build());
        }
    }

    // =============================================================================
    // normalizeListReq —— helper.go:37-56
    // =============================================================================

    @Nested
    @DisplayName("normalizeListReq：三段归一化的顺序与短路条件")
    class NormalizeListReq {

        @Test
        @DisplayName("userId<=0 ⇒ 5003")
        void nonPositiveUserIdRejected() {
            assertThatThrownBy(() -> support.normalizeListReq(20, 100,
                    ListReq.newBuilder().setUserId(0).build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(e -> assertThat(FollowGrpcErrorCodes.bizCodeOf((StatusRuntimeException) e))
                            .isEqualTo(FollowErrorCode.ERROR_INVALID_PARAM.getCode()));
        }

        @Test
        @DisplayName("offset<0 ⇒ 归零（不是报错）")
        void negativeOffsetClampedToZero() {
            FollowRpcSupport.NormalizedListReq req =
                    support.normalizeListReq(20, 100, ListReq.newBuilder().setUserId(1).setOffset(-5).build());
            assertThat(req.offset()).isZero();
        }

        @Test
        @DisplayName("limit<=0 ⇒ 换成 defaultLimit")
        void nonPositiveLimitBecomesDefault() {
            assertThat(support.normalizeListReq(20, 100,
                    ListReq.newBuilder().setUserId(1).setLimit(0).build()).limit()).isEqualTo(20);

            assertThat(support.normalizeListReq(20, 100,
                    ListReq.newBuilder().setUserId(1).setLimit(-9).build()).limit()).isEqualTo(20);
        }

        @Test
        @DisplayName("limit>maxLimit ⇒ 截断到 maxLimit")
        void oversizedLimitTruncated() {
            assertThat(support.normalizeListReq(20, 100,
                    ListReq.newBuilder().setUserId(1).setLimit(9999).build()).limit()).isEqualTo(100);
        }

        @Test
        @DisplayName("⚠️ maxLimit<=0 ⇒ 整条截断短路（不限制），Go 的前置条件 maxLimit>0 不可删")
        void nonPositiveMaxLimitDisablesTruncation() {
            // Go: `if maxLimit > 0 && limit > maxLimit` —— maxLimit=0 时后半段短路
            assertThat(support.normalizeListReq(20, 0,
                    ListReq.newBuilder().setUserId(1).setLimit(500).build()).limit())
                    .as("maxLimit=0 意味着「不截断」，不是「截断到 0」")
                    .isEqualTo(500);
        }

        @Test
        @DisplayName("⚠️ defaultLimit > maxLimit 时：先补默认值再截断 ⇒ limit 停在 maxLimit")
        void truncationHappensAfterDefaultFill() {
            // 若顺序颠倒，limit 会被补成 defaultLimit=500 且不再被截断
            assertThat(support.normalizeListReq(500, 100,
                    ListReq.newBuilder().setUserId(1).setLimit(0).build()).limit()).isEqualTo(100);
        }

        @Test
        @DisplayName("推荐接口的 maxLimit 是 50（不是列表的 100）")
        void recommendMaxLimitIsFifty() {
            assertThat(support.normalizeListReq(20, 50,
                    ListReq.newBuilder().setUserId(1).setLimit(100).build()).limit()).isEqualTo(50);
        }
    }

    // =============================================================================
    // 响应工厂 —— helper.go:58-79
    // =============================================================================

    @Nested
    @DisplayName("响应工厂：成功也走 body，code/msg 不得留默认值")
    class ResponseFactories {

        @Test
        @DisplayName("BaseResp 必须是 code=200 + msg=\"OK\"")
        void baseRespCarriesSuccessCode() {
            assertThat(support.successBaseResp().getCode()).isEqualTo(200);
            assertThat(support.successBaseResp().getMsg()).isEqualTo("OK");
        }

        @Test
        @DisplayName("空 userIds 与 Go 的空切片在 wire 上等价")
        void emptyUserListStillCarriesSuccessCode() {
            var resp = support.successUserListResp(List.of());
            assertThat(resp.getCode()).isEqualTo(200);
            assertThat(resp.getMsg()).isEqualTo("OK");
            assertThat(resp.getUserIdsList()).isEmpty();
        }

        @Test
        @DisplayName("⚠️ successRecommendResp(null) 与 (空列表) 都不写 users 字段，但都不报错")
        void recommendRespAcceptsNullAndEmpty() {
            assertThat(support.successRecommendResp(null).getUsersList()).isEmpty();
            assertThat(support.successRecommendResp(List.of()).getUsersList()).isEmpty();
            assertThat(support.successRecommendResp(null).getCode()).isEqualTo(200);
        }
    }

    // =============================================================================
    // 推荐算法纯函数 —— helper.go:96-147
    // =============================================================================

    @Nested
    @DisplayName("推荐算法纯函数")
    class RecommendationPrimitives {

        @Test
        @DisplayName("sortedKeys 必须升序 —— 这是遍历确定性的唯一来源（Go mapKeys）")
        void sortedKeysIsAscending() {
            assertThat(support.sortedKeys(Set.of(30L, 10L, 20L))).containsExactly(10L, 20L, 30L);
        }

        @Test
        @DisplayName("scoreWeight(maxDepth=3, depth=2)=20；depth=3 ⇒ 10（下界）")
        void scoreWeightMatchesGoFormula() {
            assertThat(support.scoreWeight(3, 2)).isEqualTo(20);
            assertThat(support.scoreWeight(3, 3)).isEqualTo(10);
        }

        @Test
        @DisplayName("⚠️ scoreWeight 恒 >= 10：所以 Go 里 `if score<=0 continue` 是纯防御")
        void scoreWeightNeverNegative() {
            for (int maxDepth = 0; maxDepth <= 6; maxDepth++) {
                for (int depth = 0; depth <= 6; depth++) {
                    assertThat(support.scoreWeight(maxDepth, depth))
                            .as("maxDepth=%d depth=%d", maxDepth, depth)
                            .isGreaterThanOrEqualTo(10);
                }
            }
        }

        @Test
        @DisplayName("⚠️ clampRecommendDepth(<2) ⇒ 3（不是 2），>5 ⇒ 5")
        void clampDepthMatchesGo() {
            assertThat(support.clampRecommendDepth(0)).isEqualTo(3);
            assertThat(support.clampRecommendDepth(1)).isEqualTo(3);
            assertThat(support.clampRecommendDepth(2)).isEqualTo(2);
            assertThat(support.clampRecommendDepth(5)).isEqualTo(5);
            assertThat(support.clampRecommendDepth(99)).isEqualTo(5);
        }

        @Test
        @DisplayName("clampFanout：<=0 ⇒ 50，>200 ⇒ 200")
        void clampFanoutMatchesGo() {
            assertThat(support.clampFanout(0)).isEqualTo(50);
            assertThat(support.clampFanout(-1)).isEqualTo(50);
            assertThat(support.clampFanout(200)).isEqualTo(200);
            assertThat(support.clampFanout(201)).isEqualTo(200);
            assertThat(support.clampFanout(30)).isEqualTo(30);
        }
    }

    /** 测试内的小工具：从抛出的异常里反查业务码（模拟 follow-api 的还原路径）。 */
    static final class FollowGrpcErrorCodes {
        private FollowGrpcErrorCodes() {
        }

        static int bizCodeOf(StatusRuntimeException e) {
            return com.rag.follow.follow.common.errmsg.FollowGrpcError.bizCodeFromError(e);
        }
    }

    @Test
    @DisplayName("文案反查表能把 5 类业务码还原出来（5003/5201/5101/5007/5008）")
    void reverseLookupCoversGoCallSites() {
        // Go 真实调用点 → 期望业务码。这些码在 switch 兜底里拿不到，必须靠文案反查。
        assertThat(FollowGrpcErrorCodes.bizCodeOf(
                FollowRpcSupport.rpcError(Status.Code.INVALID_ARGUMENT, FollowErrorCode.ERROR_INVALID_PARAM.getCode())))
                .isEqualTo(5003);
        assertThat(FollowGrpcErrorCodes.bizCodeOf(
                FollowRpcSupport.rpcError(Status.Code.INVALID_ARGUMENT, FollowErrorCode.ERROR_SELF_RELATION.getCode())))
                .isEqualTo(5201);
        assertThat(FollowGrpcErrorCodes.bizCodeOf(
                FollowRpcSupport.rpcError(Status.Code.PERMISSION_DENIED, FollowErrorCode.ERROR_RELATION_BLOCKED.getCode())))
                .isEqualTo(5202);
        assertThat(FollowGrpcErrorCodes.bizCodeOf(
                FollowRpcSupport.rpcError(Status.Code.NOT_FOUND, FollowErrorCode.ERROR_USER_NOT_FOUND.getCode())))
                .isEqualTo(5101);
        assertThat(FollowGrpcErrorCodes.bizCodeOf(
                FollowRpcSupport.rpcError(Status.Code.INTERNAL, FollowErrorCode.ERROR_DB_SELECT.getCode())))
                .isEqualTo(5007);
        assertThat(FollowGrpcErrorCodes.bizCodeOf(
                FollowRpcSupport.rpcError(Status.Code.INTERNAL, FollowErrorCode.ERROR_DB_UPDATE.getCode())))
                .isEqualTo(5008);
    }

    @Test
    @DisplayName("rpcError 的 description 必须逐字等于 Go 的 GetErrMsg（否则 api 侧反查落空）")
    void rpcErrorDescriptionIsGoMsg() {
        assertThat(FollowRpcSupport.rpcError(Status.Code.INTERNAL,
                FollowErrorCode.ERROR_DB_SELECT.getCode()).getStatus().getDescription())
                .isEqualTo(FollowErrorMessages.getMsg(FollowErrorCode.ERROR_DB_SELECT.getCode()))
                .isEqualTo("database query failed");
    }
}