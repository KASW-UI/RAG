package com.rag.hot.hot.rpc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.rag.hot.hot.proto.HotArticleItem;
import com.rag.hot.hot.rpc.mq.HotRedisSnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * 锁死 {@code GetHotArticles} 的 L1/L2 契约 —— 对应 Go
 * {@code internal/logic/get_hot_articles_logic.go:33-67}。
 *
 * <p>重点是 TopK 归一与 {@code uint32(score)} 的向零截断：这两条都是<b>可观察</b>的，
 * 且都极易被「顺手优化」改掉。
 */
class HotRankingQueryServiceTest {

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private HotRankingQueryService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        service = new HotRankingQueryService(redis);
    }

    private static ZSetOperations.TypedTuple<String> tuple(String member, double score) {
        return ZSetOperations.TypedTuple.of(member, score);
    }

    @Test
    @DisplayName("TopK ≤ 0 → 归一为 20（Go :35-37）；负数是合法输入（proto int32）")
    void nonPositiveTopKFallsBackTo20() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(new LinkedHashSet<>());

        service.getHotArticles(0);
        verify(zset).reverseRangeWithScores(HotRedisSnapshotStore.HOT_ARTICLES_KEY, 0, 19);

        service.getHotArticles(-5);
        verify(zset, org.mockito.Mockito.times(2))
                .reverseRangeWithScores(HotRedisSnapshotStore.HOT_ARTICLES_KEY, 0, 19);
    }

    @Test
    @DisplayName("TopK > 100 → 截断为 100（Go :38-40）")
    void oversizedTopKIsCappedAt100() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(new LinkedHashSet<>());

        service.getHotArticles(500);

        verify(zset).reverseRangeWithScores(HotRedisSnapshotStore.HOT_ARTICLES_KEY, 0, 99);
    }

    @Test
    @DisplayName("TopK 在区间内 → 原样使用，读取范围是 [0, k-1]")
    void topKInRangeIsUsedAsIs() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(new LinkedHashSet<>());

        service.getHotArticles(37);

        verify(zset).reverseRangeWithScores(HotRedisSnapshotStore.HOT_ARTICLES_KEY, 0, 36);
    }

    @Test
    @DisplayName("key 不存在 → 返回空列表、不报错（Go redis.Nil 分支 :45-48）")
    void missingKeyReturnsEmptyListWithoutError() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(Set.of());

        assertThat(service.getHotArticles(20)).isEmpty();
    }

    @Test
    @DisplayName("null 返回值也按空列表处理（防御，Go 侧不会出现）")
    void nullResultReturnsEmptyList() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong())).thenReturn(null);

        assertThat(service.getHotArticles(20)).isEmpty();
    }

    @Test
    @DisplayName("HotScore 向零截断，不是 Math.round（Go :62 的 uint32(z.Score)）")
    void hotScoreTruncatesTowardZero() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(new LinkedHashSet<>(List.of(
                        tuple("a1", 99.9),
                        tuple("a2", 7.0),
                        tuple("a3", 0.6))));

        List<HotArticleItem> items = service.getHotArticles(20);

        assertThat(items).extracting(HotArticleItem::getArticleId).containsExactly("a1", "a2", "a3");
        assertThat(items).extracting(HotArticleItem::getHotScore).containsExactly(99, 7, 0);
    }

    @Test
    @DisplayName("非字符串 member 被跳过（Go :55-59 的 , ok 保护）")
    void nonStringMemberIsSkipped() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(new LinkedHashSet<>(List.of(tuple("a1", 5.0))));

        assertThat(service.getHotArticles(20)).extracting(HotArticleItem::getArticleId).containsExactly("a1");
    }

    @Test
    @DisplayName("⚠️ 非 Nil 的 Redis 错误必须原样上抛 ⇒ gRPC 侧落 UNKNOWN（Go :49-50）")
    void nonNilRedisErrorPropagates() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenThrow(new QueryTimeoutException("ZRevRangeWithScores failed"));

        // 绝不能被吞掉：吞掉会让客户端拿到「成功但空列表」，把故障伪装成没有热榜
        assertThatThrownBy(() -> service.getHotArticles(20))
                .isInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("读取的 key 恒为 hot:articles（不得读分片 key）")
    void alwaysReadsTheMergedKey() {
        when(zset.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                .thenReturn(new LinkedHashSet<>());

        service.getHotArticles(20);

        verify(zset).reverseRangeWithScores(eq("hot:articles"), anyLong(), anyLong());
    }
}