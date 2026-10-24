package com.rag.follow.follow.rpc.service;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.rpc.mapper.BlockRelationMapper;
import com.rag.follow.follow.rpc.mapper.FollowRelationMapper;
import com.rag.follow.follow.rpc.metrics.FollowRpcMetrics;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 二度人脉推荐算法的行为契约 —— 逐条对照 Go 端
 * {@code service/follow/rpc/internal/logic/getrecommendationslogic.go:53-186}。
 *
 * <p><b>测试用确定性的社交图，而不是随机数据</b>：BFS 的分数累加、排序、分页窗口
 * 三者叠加后，随机输入下「期望值」只能靠重跑得出 —— 那不是回归网。
 * 这里把图写死，使每条断言都能手工推导出唯一答案。
 *
 * <p><b>测试图</b>（fanout=50、depth 可变）：
 * <pre>
 *   1 → 2                 （1 的直关）
 *   2 → 3, 4, 8, 9        （8 被 1 拉黑；9 被 1 拉黑 ⇒ 两者必须被过滤）
 *   3 → 5
 *   4 → 5, 6
 * </pre>
 *
 * <p><b>depth=3 的手工推导</b>（weight(3,2)=20、weight(3,3)=10）：
 * <pre>
 *   第1层 = [2]
 *   depth=2：source=2 → {3:20, 4:20, 8:20*, 9:20*}   (* 会被 blockedSet/blockerSet 过滤)
 *             nextLayer = [3,4]
 *   depth=3：source=3 → 5:+10   source=4 → 5:+10, 6:+10
 *             ⇒ scores = {3:20, 4:20, 5:20, 6:10}
 *   排序（分数降序、id 升序）⇒ [3, 4, 5, 6]
 * </pre>
 *
 * <p><b>depth=2</b>：只有一轮展开 ⇒ scores = {3:20, 4:20} ⇒ 结果 [3, 4]。
 */
class FollowRecommendationServiceTest {

    private FollowRelationMapper followRelationMapper;
    private BlockRelationMapper blockRelationMapper;
    private FollowRpcMetrics metrics;
    private FollowRecommendationService service;

    /** 社交图：userId → 关注的 id 列表。 */
    private final Map<Long, List<Long>> followGraph = new HashMap<>();

    /** 阻塞集合：返回的 Long 会被当作 blockedSet（我拉黑的）。 */
    private List<Long> blockedTargets = new ArrayList<>();

    /** 阻塞集合：返回的 Long 会被当作 blockerSet（拉黑我的）。 */
    private List<Long> blockers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        followRelationMapper = Mockito.mock(FollowRelationMapper.class);
        blockRelationMapper = Mockito.mock(BlockRelationMapper.class);
        metrics = new FollowRpcMetrics(new SimpleMeterRegistry());

        followGraph.put(1L, List.of(2L));
        followGraph.put(2L, List.of(3L, 4L, 8L, 9L));
        followGraph.put(3L, List.of(5L));
        followGraph.put(4L, List.of(5L, 6L));
        blockedTargets = List.of(8L, 9L);   // 1 拉黑了 8 与 9
        blockers = List.of();               // 没有人拉黑 1

        when(followRelationMapper.listAllFollowTargets(anyLong(), anyString()))
                .thenAnswer(inv -> followGraph.getOrDefault(inv.getArgument(0), List.of()));
        when(blockRelationMapper.listAllBlockTargets(anyLong()))
                .thenAnswer(inv -> blockedTargets);
        when(blockRelationMapper.listBlockerUsers(anyLong()))
                .thenAnswer(inv -> blockers);

        service = new FollowRecommendationService(
                followRelationMapper, blockRelationMapper,
                new FollowRpcSupport(), metrics, Mockito.mock(LoggerFactoryBean.class));
    }

    // =============================================================================

    @Test
    @DisplayName("depth=3：分数累加 + 三重过滤 + 排序，产出 [3,4,5,6]")
    void depthThreeProducesExpectedRanking() {
        List<RecommendResp.RecommendUser> users = service.recommend(1L, 0, 10, 50, 3);

        assertThat(users).isNotNull();
        assertThat(users.stream().map(RecommendResp.RecommendUser::getTargetId))
                .containsExactly(3L, 4L, 5L, 6L);
        // 3/4 是二度（depth2, weight20），5 被两条路径各命中一次累加到 20，6 只有一条路径
        Map<Long, Integer> scores = users.stream().collect(
                Collectors.toMap(RecommendResp.RecommendUser::getTargetId, RecommendResp.RecommendUser::getMutualScore));
        assertThat(scores).containsEntry(3L, 20).containsEntry(4L, 20)
                .containsEntry(5L, 20).containsEntry(6L, 10);
    }

    @Test
    @DisplayName("⚠️ blockedSet / blockerSet 双向过滤：被我拉黑(8,9)与拉黑我的人绝不出现")
    void blockFilteringIsBidirectional() {
        List<RecommendResp.RecommendUser> users = service.recommend(1L, 0, 10, 50, 3);
        assertThat(users.stream().map(RecommendResp.RecommendUser::getTargetId))
                .as("8/9 在 2 的关注列表里，但 1 拉黑了它们 ⇒ 必须被 blockedSet 过滤")
                .doesNotContain(8L, 9L);

        // 现在反过来：让「有人拉黑 1」成立，8 必须消失
        blockers = List.of(8L);
        List<RecommendResp.RecommendUser> after = service.recommend(1L, 0, 10, 50, 3);
        assertThat(after.stream().map(RecommendResp.RecommendUser::getTargetId))
                .as("8 拉黑了 1 ⇒ blockerSet 也必须过滤掉它")
                .doesNotContain(8L, 9L);
    }

    @Test
    @DisplayName("推荐结果永不含「我自己」—— 绕回自己必须被 candidateId==userId 拦掉")
    void neverRecommendsSelf() {
        // 让 2 反向关注 1：2 → [3,4,8,9,1]
        followGraph.put(2L, List.of(3L, 4L, 8L, 9L, 1L));

        List<RecommendResp.RecommendUser> users = service.recommend(1L, 0, 10, 50, 3);
        assertThat(users.stream().map(RecommendResp.RecommendUser::getTargetId))
                .as("1 自己绝不能出现在推荐里（Go :119-121 的第一道过滤）")
                .doesNotContain(1L);
    }

    @Test
    @DisplayName("直关为空 ⇒ 返回 null + gauge=0，且**不再查** blocked/blocker（Go :62-65 的提前返回）")
    void emptyDirectFollowsShortCircuits() {
        followGraph.clear();

        List<RecommendResp.RecommendUser> users = service.recommend(1L, 0, 10, 50, 3);

        assertThat(users).isNull();
        assertThat(metrics.currentRecommendationSize()).isZero();
        verify(blockRelationMapper, never()).listAllBlockTargets(anyLong());
        verify(blockRelationMapper, never()).listBlockerUsers(anyLong());
    }

    @Test
    @DisplayName("⚠️ depth=2 只展开一层：5/6 不出现（最后一层不展开，且这里根本到不了第二层）")
    void depthTwoStopsAfterOneRound() {
        List<RecommendResp.RecommendUser> users = service.recommend(1L, 0, 10, 50, 2);

        assertThat(users.stream().map(RecommendResp.RecommendUser::getTargetId))
                .containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("最后一层只计分、不再展开：depth=3 时 3/4 的下游(5,6)不会被当作下一跳起点")
    void lastLayerScoresButDoesNotExpand() {
        // 若最后一层还展开，depth=3 下 5 会再贡献一层（depth 已到上限 ⇒ 循环结束）
        // 断言的方式是：3/4 的下游 5 拿到 10 分（只来自 depth3 一轮），不是 10+20
        Map<Long, Integer> scores = service.recommend(1L, 0, 10, 50, 3).stream().collect(
                Collectors.toMap(RecommendResp.RecommendUser::getTargetId, RecommendResp.RecommendUser::getMutualScore));
        assertThat(scores.get(5L)).isEqualTo(20);
    }

    @Test
    @DisplayName("分页窗口：offset 切片 + limit 截断，排序结果不变")
    void paginationSlicesSortedCandidates() {
        assertThat(service.recommend(1L, 0, 2, 50, 3).stream()
                .map(RecommendResp.RecommendUser::getTargetId)).containsExactly(3L, 4L);
        assertThat(service.recommend(1L, 2, 2, 50, 3).stream()
                .map(RecommendResp.RecommendUser::getTargetId)).containsExactly(5L, 6L);
    }

    @Test
    @DisplayName("⚠️ offset 越界 ⇒ 返回 null + gauge=0，**不报错**（Go :165-169）")
    void offsetBeyondRangeReturnsNullNotError() {
        assertThat(service.recommend(1L, 99, 10, 50, 3)).isNull();
        assertThat(metrics.currentRecommendationSize()).isZero();
    }

    @Test
    @DisplayName("同分候选按 id 升序 —— 无序会导致跨 JVM 结果不稳定（Go :158-163 的次关键字）")
    void tiesBrokenByTargetIdAscending() {
        // 只保留一个二度邻居：3 与 4 同为 20 分
        followGraph.put(2L, List.of(4L, 3L));   // 输入顺序故意倒过来

        List<RecommendResp.RecommendUser> users = service.recommend(1L, 0, 10, 50, 2);
        assertThat(users.stream().map(RecommendResp.RecommendUser::getTargetId))
                .as("输入顺序倒置，输出仍必须按 id 升序")
                .containsExactly(3L, 4L);
    }

    @Test
    @DisplayName("DB 失败 ⇒ INTERNAL + 5007，并上报 db_error_total(type=list_direct_follows)")
    void dbFailureMapsToSelectError() {
        when(followRelationMapper.listAllFollowTargets(anyLong(), anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("pg down"));

        assertThatThrownBy(() -> service.recommend(1L, 0, 10, 50, 3))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> {
                    StatusRuntimeException sre = (StatusRuntimeException) e;
                    assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
                    assertThat(FollowGrpcError.bizCodeFromError(sre))
                            .as("必须还原成 5007 而不是 switch 兜底的 5001")
                            .isEqualTo(FollowErrorCode.ERROR_DB_SELECT.getCode());
                });
    }

    @Test
    @DisplayName("第 2 层展开失败 ⇒ 同样 5007，且 type 标签是 list_layer_follow_targets")
    void layerExpansionFailureUsesDistinctDbErrorType() {
        when(followRelationMapper.listAllFollowTargets(anyLong(), anyString()))
                .thenAnswer(inv -> {
                    long source = inv.getArgument(0);
                    if (source == 1L) {
                        return followGraph.getOrDefault(1L, List.of());
                    }
                    throw new org.springframework.dao.DataAccessResourceFailureException("pg down");
                });

        assertThatThrownBy(() -> service.recommend(1L, 0, 10, 50, 3))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(e -> assertThat(FollowGrpcError.bizCodeFromError((StatusRuntimeException) e))
                        .isEqualTo(FollowErrorCode.ERROR_DB_SELECT.getCode()));
    }
}