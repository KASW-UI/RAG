package com.rag.like.like.rpc.service;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.proto.ActionType;
import com.rag.like.like.proto.LikeActionReq;
import com.rag.like.like.rpc.client.MessageRpcClient;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.config.LikeRpcProperties;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import com.rag.like.like.rpc.metrics.LikeRpcMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 🔴 <b>global E60 的守门：{@code currentUserLikeTotal} 双失败时 error 必须传出去。</b>
 *
 * <h2>漂移本体（Go ↔ RAG 逐字对照）</h2>
 *
 * <p>Go {@code service/like/rpc/internal/logic/likeactionlogic.go:345-364}：
 * <pre>
 * func (l *LikeActionLogic) currentUserLikeTotal(userID int64, targetType string, userListKey string) (int64, error) {
 *     total, err := l.svcCtx.BizRedis.ZcardCtx(l.ctx, userListKey)
 *     if err != nil { total = 0 }
 *     dbTotal, dbErr := l.svcCtx.LikeModel.GetUserActiveLikeCount(l.ctx, userID, targetType)
 *     if dbErr != nil {
 *         if err == nil { return int64(total), nil }   // Redis 成功 + DB 失败 ⇒ 降级用 Redis 值，**不算失败**
 *         return 0, dbErr                                // **双失败 ⇒ error 必须传出去**
 *     }
 *     if dbTotal &gt; int64(total) { return dbTotal, nil }
 *     return int64(total), nil
 * }
 * </pre>
 *
 * <p>Go 的消费方 {@code syncTaskProgress}（{@code :311-320}）是三段式：
 * <pre>
 * totalLikesGiven, err := l.currentUserLikeTotal(...)
 * if err != nil {
 *     logger.LogBusinessErr(l.ctx, errmsg.ErrorRedisSelect,
 *         fmt.Errorf("sync task user progress failed: %w", err))   // ← 记业务错误 5011，**不推 user 路**
 * } else if totalLikesGiven &gt; 0 {
 *     l.svcCtx.TaskUserPusher.PushWithKey(...)                      // ← 只有无错且 &gt;0 才推
 * }
 * </pre>
 *
 * <p>🔴 <b>改前 RAG 把 error 吞了</b>：{@code currentUserLikeTotal} 双失败时只 {@code LOG.warn(...)} +
 * {@code return 0L} ⇒ <b>没有 5011 业务错误记录</b>；user 路只是靠
 * {@code userLikesGiven &gt; 0} 这个<b>巧合守卫</b>才没把 {@code 0} 当进度推出去
 * ⇒ 守卫一旦被改动就会变成「把 0 推给下游」。
 *
 * <h2>本类断言什么（钉死三条，一条都不许退）</h2>
 *
 * <ol>
 *   <li><b>双失败 ⇒ 真的记了 {@code error_code=5011}</b> 的 {@code business_error} 日志
 *       （断言的是<b>真实日志行</b>，不是 mock 调用）；</li>
 *   <li><b>双失败 ⇒ user 路不推</b>；</li>
 *   <li><b>双失败 ⇒ article 路照走</b> —— Go 的 {@code syncTaskProgress} 在路 1 之后<b>没有 return</b>
 *       ⇒ 路 1 的错误<b>只</b>短路 user 路。<b>这条最容易被"顺手"改错</b>
 *       （把分流提到调用方并写成「有错就不调 publish」⇒ 路 2 会被一起跳过 ⇒ 制造 Go 没有的新漂移）。</li>
 * </ol>
 *
 * <h2>为什么用真 publisher + 真 logger + mock KafkaTemplate</h2>
 *
 * <p>本类要验的是「<b>错误分流与推送边界</b>」，
 * 所以 {@link LikeTaskProgressPublisher} 与 {@link LoggerFactoryBean} 都用<b>真实例</b>
 * （日志走真实 SLF4J fluent API ⇒ 用 Logback {@link ListAppender} 捕获<b>真实日志行</b>），
 * 只把 {@link KafkaTemplate} mock 掉以观测「推了哪条 topic」。
 *
 * <p>⚠️ 本类是<b>进程内单测</b>：它证明的是「分支与推送边界」，<b>不</b>证明「Kafka 真收到」
 * ⇒ 真 Kafka 的活体证据是另一条待办（台账 {@code L-U9}），两者不可互相顶替。
 */
class LikeUserLikeTotalDoubleFailureGateTest {

    private static final String LOGGER_NAME = "like-rpc-e60-gate";

    private StringRedisTemplate redis;
    private LikeRecordMapper recordMapper;
    private KafkaTemplate<String, String> kafkaTemplate;
    private LikeKafkaTopics topics;
    private LikeTaskProgressPublisher publisher;
    private LikeDomainService service;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        redis = Mockito.mock(StringRedisTemplate.class);
        recordMapper = Mockito.mock(LikeRecordMapper.class);
        kafkaTemplate = Mockito.mock(KafkaTemplate.class);

        // KafkaTemplate.send 被 mock 后返回 null ⇒ 直接 whenComplete 会 NPE ⇒ 返回已完成 future。
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        ZSetOperations<String, String> zSetOps = Mockito.mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        // Lua 执行成功且 resultCode != 0（Go 的「变更」分支 ⇒ 才会走 likeOnArticle）
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        topics = new LikeKafkaTopics();
        publisher = new LikeTaskProgressPublisher(kafkaTemplate, topics, new ObjectMapper(),
                new LoggerFactoryBean(LOGGER_NAME));
        appender = attachAppender(LOGGER_NAME);

        service = new LikeDomainService(
                redis,
                recordMapper,
                new LikeRpcProperties(),
                new LikeRpcMetrics(new SimpleMeterRegistry()),
                topics,
                new LikeDomainService.KafkaLikeMsgPublisher(kafkaTemplate, topics, new ObjectMapper()),
                publisher,
                Mockito.mock(MessageRpcClient.class),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        detachAppender(LOGGER_NAME, appender);
    }

    // ==================================================================================
    // 场景 1：Redis 与 DB 双失败（E60 本体）
    // ==================================================================================

    @Test
    @DisplayName("🔴 E60：Redis+DB 双失败 ⇒ 记业务错误 5011 + user 路不推 + article 路照走")
    void doubleFailureRecords5011SkipsUserRouteButKeepsArticleRoute() {
        // 计数读回得正常（Redis 的 hash 侧活着）⇒ articleLikes = 7 ⇒ article 路该走
        when(redis.opsForHash().get(anyString(), anyString())).thenReturn("7");
        // 但 ZCARD（user 的赞列表）失败 ⇒ Redis 侧失败
        when(redis.opsForZSet().zCard(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        // DB 侧也失败
        when(recordMapper.countActiveLikeByUserAndType(any(Long.class), anyString()))
                .thenThrow(new QueryTimeoutException("db down"));
        when(recordMapper.countActiveLikeByTarget(anyString(), anyString())).thenReturn(0L);

        service.likeAction(LikeActionReq.newBuilder()
                .setTargetType("article")
                .setTargetId("t1")
                .setUserId(5L)
                .setAuthorId(9L)
                .setActionType(ActionType.ACTION_LIKE)
                .build());

        // ① 真的记了 5011（断言真实日志行，不是 mock 调用）
        ILoggingEvent bizErr = businessErrorEvent();
        assertThat(bizErr)
                .as("🔴 双失败却没记 business_error ⇒ Go 的 LogBusinessErr(ErrorRedisSelect) 语义丢了")
                .isNotNull();
        assertThat(keyValue(bizErr, "error_code"))
                .as("🔴 业务错误码必须是 Go 的 ErrorRedisSelect=5011")
                .isEqualTo(String.valueOf(LikeErrorCode.ERROR_REDIS_SELECT.getCode()));
        assertThat(keyValue(bizErr, "error_reason"))
                .as("🔴 error_reason 必须带 Go 的包裹文案（Go: fmt.Errorf(\"sync task user progress failed: %w\", err)）")
                .contains("sync task user progress failed:");

        // ② user 路不推
        verify(kafkaTemplate, never()).send(eq(topics().getTaskUserTopic()), anyString(), any());
        assertThat(sentTopics())
                .as("🔴 双失败时 user 路必须不推（Go 的 if err != nil 分支不执行 PushWithKey）")
                .doesNotContain(topics().getTaskUserTopic());

        // ③ article 路照走（Go 的 syncTaskProgress 在路 1 之后没有 return）
        assertThat(sentTopics())
                .as("🔴 双失败只短路 user 路；Go 的路 2（article 路）照走 —— "
                        + "若把分流提到调用方，路 2 会被一起跳过 ⇒ 制造 Go 没有的新漂移")
                .contains(topics().getTaskArticleTopic());
    }

    // ==================================================================================
    // 场景 2（对照组）：Redis 成功 + DB 失败 ⇒ 不算失败
    // ==================================================================================

    @Test
    @DisplayName("🔴 对照组：Redis 成功 + DB 失败 ⇒ 不记 5011 且 user 路照推（Go 降级用 Redis 值）")
    void redisOkDbFailDegradesWithoutRecordingBusinessError() {
        when(redis.opsForHash().get(anyString(), anyString())).thenReturn("7");
        // ZCARD 成功（Redis 侧正常）
        when(redis.opsForZSet().zCard(anyString())).thenReturn(3L);
        // 只有 DB 失败
        when(recordMapper.countActiveLikeByUserAndType(any(Long.class), anyString()))
                .thenThrow(new QueryTimeoutException("db down"));
        when(recordMapper.countActiveLikeByTarget(anyString(), anyString())).thenReturn(0L);

        service.likeAction(LikeActionReq.newBuilder()
                .setTargetType("article")
                .setTargetId("t1")
                .setUserId(5L)
                .setAuthorId(9L)
                .setActionType(ActionType.ACTION_LIKE)
                .build());

        assertThat(businessErrorEvent())
                .as("🔴 只有 Redis 成功、DB 失败 ⇒ Go 返回 (total, nil) ⇒ 不该有 5011")
                .isNull();
        assertThat(sentTopics())
                .as("🔴 Redis 值可用 ⇒ user 路必须照推（Go: else if totalLikesGiven > 0）")
                .contains(topics().getTaskUserTopic());
    }

    // ==================================================================================
    // 场景 3（对照组）：两侧都成功
    // ==================================================================================

    @Test
    @DisplayName("🔴 对照组：两侧都成功 ⇒ 不记 5011 且 user 路照推")
    void bothSidesOkPushesWithoutBusinessError() {
        when(redis.opsForHash().get(anyString(), anyString())).thenReturn("7");
        when(redis.opsForZSet().zCard(anyString())).thenReturn(3L);
        when(recordMapper.countActiveLikeByUserAndType(any(Long.class), anyString())).thenReturn(9L);
        when(recordMapper.countActiveLikeByTarget(anyString(), anyString())).thenReturn(0L);

        service.likeAction(LikeActionReq.newBuilder()
                .setTargetType("article")
                .setTargetId("t1")
                .setUserId(5L)
                .setAuthorId(9L)
                .setActionType(ActionType.ACTION_LIKE)
                .build());

        assertThat(businessErrorEvent())
                .as("🔴 两侧都成功 ⇒ 不该有业务错误")
                .isNull();
        assertThat(sentTopics())
                .as("🔴 两侧都成功 ⇒ user 路与 article 路都该推")
                .contains(topics().getTaskUserTopic(), topics().getTaskArticleTopic());
    }

    // ==================================================================================
    // 场景 4：article 路的 Go 守卫（authorId<=0 或 articleLikes<=0 ⇒ 不推）
    // ==================================================================================

    @Test
    @DisplayName("🔴 双失败且 articleLikes=0 ⇒ article 路也不推（Go: articleLikes <= 0 直接 return）")
    void doubleFailureWithZeroArticleLikesSkipsArticleRoute() {
        // 计数读回为空 ⇒ articleLikes = 0
        when(redis.opsForHash().get(anyString(), anyString())).thenReturn(null);
        when(redis.opsForZSet().zCard(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(recordMapper.countActiveLikeByUserAndType(any(Long.class), anyString()))
                .thenThrow(new QueryTimeoutException("db down"));
        when(recordMapper.countActiveLikeByTarget(anyString(), anyString())).thenReturn(0L);

        service.likeAction(LikeActionReq.newBuilder()
                .setTargetType("article")
                .setTargetId("t1")
                .setUserId(5L)
                .setAuthorId(9L)
                .setActionType(ActionType.ACTION_LIKE)
                .build());

        assertThat(businessErrorEvent())
                .as("🔴 双失败 ⇒ 5011 仍要记")
                .isNotNull();
        assertThat(sentTopics())
                .as("🔴 articleLikes=0 ⇒ Go 的 article 路守卫（articleLikes <= 0 ⇒ return）生效")
                .doesNotContain(topics().getTaskArticleTopic());
    }

    // ==================================================================================
    // 辅助
    // ==================================================================================

    private LikeKafkaTopics topics() {
        return topics;
    }

    /** 收集本次测试里 {@link KafkaTemplate#send} 被用到的 topic（按调用顺序）。 */
    private List<String> sentTopics() {
        List<String> sent = new ArrayList<>();
        for (Invocation invocation : Mockito.mockingDetails(kafkaTemplate).getInvocations()) {
            if (!"send".equals(invocation.getMethod().getName())) {
                continue;
            }
            Object[] args = invocation.getArguments();
            if (args.length >= 1 && args[0] instanceof String topic) {
                sent.add(topic);
            }
        }
        return sent;
    }

    /** 找最后一条 {@code message=business_error} 的日志事件。 */
    private ILoggingEvent businessErrorEvent() {
        ILoggingEvent found = null;
        for (ILoggingEvent event : appender.list) {
            if ("business_error".equals(event.getMessage())) {
                found = event;
            }
        }
        return found;
    }

    private static String keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(kv -> key.equals(kv.key))
                .map(kv -> kv.value == null ? null : String.valueOf(kv.value))
                .findFirst()
                .orElse(null);
    }

    private static ListAppender<ILoggingEvent> attachAppender(String loggerName) {
        LoggerContext ctx = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger logger = ctx.getLogger(loggerName);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachAppender(String loggerName, ListAppender<ILoggingEvent> appender) {
        LoggerContext ctx = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        ctx.getLogger(loggerName).detachAppender(appender);
        appender.stop();
    }
}
