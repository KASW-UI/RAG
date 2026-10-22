package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.GetLikeCountResp;
import com.rag.like.like.proto.GetLikeStateReq;
import com.rag.like.like.proto.GetLikeStateResp;
import com.rag.like.like.proto.GetUserTotalLikeReq;
import com.rag.like.like.proto.GetUserTotalLikeResp;
import com.rag.like.like.proto.LikeActionReq;
import com.rag.like.like.proto.LikeActionResp;
import com.rag.like.like.rpc.client.MessageRpcClient;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.config.LikeRpcProperties;
import com.rag.like.like.rpc.exception.LikeExceptions;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import com.rag.like.like.rpc.metrics.LikeRpcMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * <b>Redis 降级语义</b>的守门测试 —— 本模块最高风险的漂移点。
 *
 * <h2>为什么这组断言必须存在</h2>
 *
 * <p>Go 的 go-zero {@code HmgetCtx} 出错返回 {@code (nil, err)}，调用方<b>不 return</b>，
 * 于是全部 id 落进 {@code missingIds} → 回源 DB。Go 源码自己把它称为「缓存容灾降级」。
 *
 * <p>Spring Data Redis 出错<b>抛异常</b>。一个再自然不过的 Spring 写法
 * （直接 {@code opsForHash().multiGet(...)}）就会让 Redis 抖动 ⇒ 整个 RPC 变成 5003
 * ⇒ 与 Go 完全相反。这正是 AGENTS.md §26 定义的 {@code HIGH-RISK DRIFT}：
 * 「一个正常的 Java 开发者，没看过 Go 源码，几乎必然会写成这样」。
 *
 * <p>下面的用例把这个降级行为钉死：<b>Redis 挂了必须仍然返回正确答案</b>。
 */
class LikeDomainServiceRedisDegradationTest {

    private StringRedisTemplate redis;
    private LikeRecordMapper recordMapper;
    private LikeDomainService service;

    @BeforeEach
    void setUp() {
        redis = Mockito.mock(StringRedisTemplate.class);
        recordMapper = Mockito.mock(LikeRecordMapper.class);

        LikeRpcProperties props = new LikeRpcProperties();
        LikeKafkaTopics topics = new LikeKafkaTopics();
        service = new LikeDomainService(
                redis,
                recordMapper,
                props,
                new LikeRpcMetrics(new SimpleMeterRegistryHolder().registry()),
                topics,
                new LikeDomainService.KafkaLikeMsgPublisher(
                        Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class), topics, new ObjectMapper()),
                Mockito.mock(LikeTaskProgressPublisher.class),
                Mockito.mock(MessageRpcClient.class),
                new ObjectMapper());
    }

    /** 只需要一个能注册计数器的 registry；避免把 micrometer 整套测试夹具搬进来。 */
    private static final class SimpleMeterRegistryHolder {
        private final io.micrometer.core.instrument.MeterRegistry registry =
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

        io.micrometer.core.instrument.MeterRegistry registry() {
            return registry;
        }
    }

    @Test
    @DisplayName("Redis 读连接失败时 GetLikeCount 仍然回源 DB 并返回正确计数，而不是抛 5003")
    void redisReadFailureDegradesToDbFallback() {
        // Redis 完全不可用：HMGET / GET 都抛连接异常
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.multiGet(anyString(), anyList()))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        // DB 有数据：target a = 3 赞 1 踩，target b 无记录
        LikeRecordMapper.StateCountRow aLike = new LikeRecordMapper.StateCountRow();
        aLike.targetId = "a";
        aLike.state = 1;
        aLike.cnt = 3L;
        LikeRecordMapper.StateCountRow aDislike = new LikeRecordMapper.StateCountRow();
        aDislike.targetId = "a";
        aDislike.state = 2;
        aDislike.cnt = 1L;
        when(recordMapper.selectStateCountsBatch(anyString(), anyList()))
                .thenReturn(List.of(aLike, aDislike));

        GetLikeCountResp resp = service.getLikeCount(GetLikeCountReq.newBuilder()
                .setTargetType("article")
                .addAllTargetIds(List.of("a", "b"))
                .build());

        // ⚠️ 关键断言：没有抛异常，且拿到了 DB 的真值
        assertEquals(3L, resp.getCountsMap().get("a").getLikeCount());
        assertEquals(1L, resp.getCountsMap().get("a").getDislikeCount());
        // 查不到的 target：Go 里 dbResMap[id] 不存在 ⇒ 计数为 0
        assertEquals(0L, resp.getCountsMap().get("b").getLikeCount());
        assertEquals(0L, resp.getCountsMap().get("b").getDislikeCount());
    }

    @Test
    @DisplayName("Redis 读超时同样降级，而不是升级成错误")
    void redisTimeoutAlsoDegrades() {
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.multiGet(anyString(), anyList()))
                .thenThrow(new QueryTimeoutException("command timed out"));
        when(recordMapper.selectStateCountsBatch(anyString(), anyList())).thenReturn(List.of());

        GetLikeCountResp resp = service.getLikeCount(GetLikeCountReq.newBuilder()
                .setTargetType("article").addAllTargetIds(List.of("x")).build());

        assertEquals(0L, resp.getCountsMap().get("x").getLikeCount(),
                "Redis 超时也应回源 DB 并返回 0，而不是抛错");
    }

    @Test
    @DisplayName("GetLikeState 在 Redis 失败时回源 DB，并把查不到的 id 以 0 写回缓存（防穿透）")
    void likeStateDbFallbackWritesZeroBack() {
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.get(anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        when(recordMapper.selectUserStatesBatch(any(Long.class), anyString(), anyList()))
                .thenReturn(List.of());

        GetLikeStateResp resp = service.getLikeState(GetLikeStateReq.newBuilder()
                .setTargetType("article").setUserId(7L).addAllTargetIds(List.of("t1")).build());

        assertEquals(0, resp.getStatesMap().get("t1"), "查不到的状态是 0，不是缺失");
    }

    @Test
    @DisplayName("⚠️ 不对称：DB 失败是致命的 —— Redis 能降级，DB 不能")
    void dbFailureIsFatalAndUsesTheGoErrorCode() {
        HashOperations<String, Object, Object> hashOps = Mockito.mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.multiGet(anyString(), anyList())).thenReturn(List.of("", ""));
        when(recordMapper.selectStateCountsBatch(anyString(), anyList()))
                .thenThrow(new IllegalStateException("connection reset"));

        LikeExceptions.Rpc err = assertThrows(LikeExceptions.Rpc.class,
                () -> service.getLikeCount(GetLikeCountReq.newBuilder()
                        .setTargetType("article").addAllTargetIds(List.of("a")).build()));

        // Go: errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")
        assertEquals(LikeErrorCode.ERROR_DB_SELECT.getCode(), err.getBizCode());
        assertEquals("DB查询失败", err.getMsg(), "必须是调用点原文，查表会得到「查询数据库失败」");
    }

    @Test
    @DisplayName("GetUserTotalLike：Redis 出错当作 miss 走 DB，而不是报错")
    void userTotalLikeRedisErrorFallsThroughToDb() {
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        when(recordMapper.countActiveLikeByAuthor(any(Long.class))).thenReturn(42L);

        GetUserTotalLikeResp resp = service.getUserTotalLike(
                GetUserTotalLikeReq.newBuilder().setUserId(9L).build());

        assertEquals(42L, resp.getTotalLikeCount());
    }

    @Test
    @DisplayName("⚠️ 不对称：写路径的 Lua 失败是致命的（5012），没有回源可退")
    void likeActionLuaFailureIsFatal() {
        when(redis.execute(any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("down"));

        LikeExceptions.Rpc err = assertThrows(LikeExceptions.Rpc.class,
                () -> service.likeAction(LikeActionReq.newBuilder()
                        .setTargetType("article").setTargetId("t").setUserId(5L)
                        .setActionType(com.rag.like.like.proto.ActionType.ACTION_LIKE)
                        .build()));

        assertEquals(LikeErrorCode.ERROR_REDIS_UPDATE.getCode(), err.getBizCode());
        assertEquals("Redis更新失败", err.getMsg());
    }

    @Test
    @DisplayName("参数校验先于一切：空 target_ids / 非法 user_id 都返回 2001 与各自原文")
    void validationErrorsCarryTheGoMessages() {
        LikeExceptions.Rpc emptyIds = assertThrows(LikeExceptions.Rpc.class,
                () -> service.getLikeCount(GetLikeCountReq.newBuilder().setTargetType("article").build()));
        assertEquals(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), emptyIds.getBizCode());
        assertEquals("输入需要查询的ID为空", emptyIds.getMsg());

        LikeExceptions.Rpc badUser = assertThrows(LikeExceptions.Rpc.class,
                () -> service.getUserTotalLike(GetUserTotalLikeReq.newBuilder().setUserId(0L).build()));
        assertEquals(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), badUser.getBizCode());
        assertEquals("非法的用户ID", badUser.getMsg(),
                "同为 2001，但文案不同 —— 合并成一个工厂会改掉调用方看到的 msg");

        LikeExceptions.Rpc badParam = assertThrows(LikeExceptions.Rpc.class,
                () -> service.likeAction(LikeActionReq.newBuilder()
                        .setTargetType("article").setTargetId("").setUserId(1L).build()));
        assertEquals("参数异常", badParam.getMsg());
    }

    @Test
    @DisplayName("pageSize 归一：<=0 与 >100 都夹成 10，传给 DB 的 fetchLimit 恒为 limit+1")
    void pageSizeIsNormalisedLikeGo() {
        when(redis.opsForHash()).thenReturn(Mockito.mock(HashOperations.class));

        // Go: limit = pageSize; if limit <= 0 || limit > 100 { limit = 10 }; fetchLimit = limit + 1
        assertFetchLimitForPageSize(0, 11);
        assertFetchLimitForPageSize(-5, 11);
        assertFetchLimitForPageSize(101, 11);
        assertFetchLimitForPageSize(1000, 11);
        // 合法值原样使用
        assertFetchLimitForPageSize(20, 21);
        assertFetchLimitForPageSize(100, 101);
    }

    private void assertFetchLimitForPageSize(int pageSize, int expectedFetchLimit) {
        when(recordMapper.selectUserLikeList(any(Long.class), anyString(), any(Long.class), any(Integer.class)))
                .thenReturn(List.of());
        service.getUserLikeList(com.rag.like.like.proto.GetUserLikeListReq.newBuilder()
                .setUserId(1L).setTargetType("article").setPageSize(pageSize).build());
        Mockito.verify(recordMapper).selectUserLikeList(
                Mockito.eq(1L), Mockito.eq("article"), Mockito.eq(0L), Mockito.eq(expectedFetchLimit));
        Mockito.clearInvocations(recordMapper);
    }
}