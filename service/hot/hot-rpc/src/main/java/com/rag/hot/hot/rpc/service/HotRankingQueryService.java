package com.rag.hot.hot.rpc.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.rag.hot.hot.rpc.domain.HeavyKeeper;
import com.rag.hot.hot.rpc.mq.HotRedisSnapshotStore;
import com.rag.hot.hot.proto.HotArticleItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * {@code GetHotArticles} 的唯一业务实现 —— 对应 Go
 * {@code service/hot/rpc/internal/logic/get_hot_articles_logic.go}（67 行）。
 *
 * <h2>L1 / L2 契约（逐条对照 Go）</h2>
 * <ol>
 *   <li><b>TopK 归一</b>：{@code k <= 0 → 20}（{@code :35-37}）、{@code k > 100 → 100}（{@code :38-40}）。
 *       proto 字段是 {@code int32}，负数是合法输入 ⇒ 走 {@code <= 0} 分支。</li>
 *   <li><b>读取</b>：{@code ZREVRANGE hot:articles 0 k-1 WITHSCORES}（{@code :43}）。</li>
 *   <li><b>key 不存在 → 返回空 items、不报错</b>（Go {@code :45-48} 判 {@code redis.Nil}）。
 *       Java 侧 Spring Data Redis 对不存在的 key 返回<b>空集合且不抛异常</b>，语义天然一致。</li>
 *   <li><b>其它 Redis 错误 → 原样向上抛</b>（Go {@code :49-50} {@code return nil, err}）。
 *       ⚠️ 这里<b>不能</b>吞掉：Go 返回的是<b>裸 error</b>，
 *       经 gRPC {@code status.FromError} 后落到 {@code codes.Unknown}
 *       ⇒ 由 {@code HotServiceGrpcImpl} 映射为 {@code Status.UNKNOWN}，
 *       <b>不是</b>业务码、<b>不是</b> INTERNAL。</li>
 *   <li><b>member 非字符串 → 跳过</b>（Go {@code :55-59} 的 {@code , ok} 保护）。</li>
 *   <li><b>{@code HotScore = uint32(z.Score)} 向零截断</b>（Go {@code :62}）——
 *       <b>不能</b> {@code Math.round}。Redis score 是 float64，截断后与原排序可能出现并列。</li>
 * </ol>
 */
@Service
public class HotRankingQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(HotRankingQueryService.class);

    /** Go {@code defaultTopK = 20}。 */
    public static final int DEFAULT_TOP_K = 20;

    /** Go {@code maxTopK = 100}。 */
    public static final int MAX_TOP_K = 100;

    private final StringRedisTemplate redis;

    public HotRankingQueryService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @param topK 请求里的 {@code top_k}（proto int32，可为负）
     * @return 按热度降序的条目；key 不存在时返回空列表
     * @throws org.springframework.dao.DataAccessException Redis 访问失败（非 key 不存在）时向上抛
     */
    public List<HotArticleItem> getHotArticles(int topK) {
        long k = topK;
        if (k <= 0) {
            k = DEFAULT_TOP_K;
        }
        if (k > MAX_TOP_K) {
            k = MAX_TOP_K;
        }

        Set<ZSetOperations.TypedTuple<String>> results =
                redis.opsForZSet().reverseRangeWithScores(HotRedisSnapshotStore.HOT_ARTICLES_KEY, 0, k - 1);
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }

        List<HotArticleItem> items = new ArrayList<>(results.size());
        for (ZSetOperations.TypedTuple<String> z : results) {
            Object member = z.getValue();
            if (!(member instanceof String articleId)) {
                // Go :55-59：非字符串 member → Slowf 后跳过
                LOG.warn("redis member is not string: {}", member);
                continue;
            }
            Double score = z.getScore();
            if (score == null) {
                continue;
            }
            items.add(HotArticleItem.newBuilder()
                    .setArticleId(articleId)
                    .setHotScore(HeavyKeeper.scoreToCount(score))
                    .build());
        }
        return items;
    }
}