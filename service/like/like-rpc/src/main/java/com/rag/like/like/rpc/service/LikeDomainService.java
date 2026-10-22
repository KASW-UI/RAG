package com.rag.like.like.rpc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.common.snowflake.Snowflake;
import com.rag.like.like.rpc.client.MessageRpcClient;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.config.LikeRpcProperties;
import com.rag.like.like.rpc.exception.LikeExceptions;
import com.rag.like.like.rpc.mapper.LikeRecordMapper;
import com.rag.like.like.rpc.metrics.LikeRpcMetrics;
import com.rag.like.like.rpc.mq.LikeEventContracts;
import com.rag.like.like.proto.ActionType;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.GetLikeCountResp;
import com.rag.like.like.proto.GetLikeStateReq;
import com.rag.like.like.proto.GetLikeStateResp;
import com.rag.like.like.proto.GetTargetLikerListReq;
import com.rag.like.like.proto.GetTargetLikerListResp;
import com.rag.like.like.proto.GetUserLikeListReq;
import com.rag.like.like.proto.GetUserLikeListResp;
import com.rag.like.like.proto.GetUserTotalLikeReq;
import com.rag.like.like.proto.GetUserTotalLikeResp;
import com.rag.like.like.proto.LikeActionReq;
import com.rag.like.like.proto.LikeActionResp;
import com.rag.like.like.proto.LikeCountItem;
import com.rag.like.like.proto.LikeItem;
import com.rag.like.like.proto.LikeRecordItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * like 域 6 个 gRPC 用例的业务实现 —— 逐个对应 Go 端
 * {@code service/like/rpc/internal/logic/*.go} 的 6 个 logic。
 *
 * <h2>⚠️ 本类最重要的一条语义：Redis 读失败<b>不是</b>致命错误</h2>
 *
 * <p>Go 侧全部 6 个 logic 的 Redis 读都是这个形状：
 * <pre>
 * vals, err := redis.HmgetCtx(...)
 * if err != nil {
 *     span.RecordError(err)
 *     metrics...("redis_error").Add(n)
 *     logger.LogBusinessErr(ctx, errmsg.ErrorRedisSelect, err)
 * }
 * // ← 注意：没有 return，继续往下走
 * </pre>
 * go-zero 的 {@code HmgetCtx} 失败时返回 <b>(nil, err)</b>，而后续代码用
 * {@code if len(vals) > i} 保护下标，于是 <b>全部 id 落进 missingIds → 回源 DB</b>。
 * Go 源码自己的注释把这条设计意图写得很清楚（{@code getlikecountlogic.go:139-142}）：
 * <pre>
 * 这意味着程序会继续往下走，vals 是空的…所有的 ID 都会被丢进 missingIds，然后自动降级去查询 DB！
 * 这是一种极其优秀的"缓存容灾降级"策略，保证了 Redis 宕机时，业务依然能（勉强）运作！
 * </pre>
 *
 * <p>⚠️ <b>Spring Data Redis 是抛异常的</b>
 * （{@code RedisConnectionFailureException} / {@code QueryTimeoutException} 等）。
 * 如果直接 {@code opsForHash().multiGet(...)} 而不包 try/catch，
 * Redis 一抖动就会把整个 RPC 变成 5003 —— 与 Go 完全相反，且**正是那种「看起来更地道的 Spring 写法」**
 * 会引入的漂移（AGENTS.md §26 判据：HIGH-RISK DRIFT）。
 *
 * <p>本类的 {@link #hmgetLenient} / {@link #hgetLenient} / {@link #zrevrangeLenient} 就是这个适配层：
 * <b>失败 ⇒ 返回空结果 ⇒ 上层自然走 DB 回源</b>。
 *
 * <h2>⚠️ 与之对称的一条：DB 读失败<b>是</b>致命错误</h2>
 *
 * <p>同一段代码里 {@code GetBatchLikeCount} 失败却直接
 * {@code return nil, errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")}。
 * <b>Redis 降级、DB 致命</b>这个不对称是 Go 的刻意设计，不要「统一风格」抹平它。
 *
 * <h2>⚠️ 写路径的 Redis 失败也是致命的</h2>
 *
 * <p>{@code likeAction} 的 Lua 执行失败 ⇒ {@code errmsg.NewGrpcErr(ErrorRedisUpdate, "Redis更新失败")}。
 * 写没有回源可退：Lua 要么原子生效要么没生效，不能「假装成功」。
 */
@Service
public class LikeDomainService {

    private static final Logger LOG = LoggerFactory.getLogger(LikeDomainService.class);

    /**
     * Go {@code getuserlikelistlogic.go:42} / {@code gettargetlikerlistlogic.go:45}：
     * {@code if limit <= 0 || limit > 100 { limit = 10 }}。
     */
    private static final int PAGE_SIZE_DEFAULT = 10;
    private static final int PAGE_SIZE_MAX = 100;

    /** Go {@code strings.EqualFold(in.TargetType, "article")} —— 文章专属副作用的判定目标类型。 */
    private static final String TARGET_TYPE_ARTICLE = "article";

    /** Go {@code getActionString} 的未知分支返回值，仅用于 metrics label 与 span 属性。 */
    private static final String ACTION_UNKNOWN = "unknown";

    private final StringRedisTemplate redis;
    private final LikeRecordMapper recordMapper;
    private final LikeRpcProperties properties;
    private final LikeRpcMetrics metrics;
    private final LikeKafkaTopics topics;
    private final KafkaLikeMsgPublisher likeMsgPublisher;
    private final LikeTaskProgressPublisher taskProgressPublisher;
    private final MessageRpcClient messageRpcClient;
    private final ObjectMapper objectMapper;

    public LikeDomainService(StringRedisTemplate redis,
                             LikeRecordMapper recordMapper,
                             LikeRpcProperties properties,
                             LikeRpcMetrics metrics,
                             LikeKafkaTopics topics,
                             @Qualifier("likeMsgPublisher") KafkaLikeMsgPublisher likeMsgPublisher,
                             LikeTaskProgressPublisher taskProgressPublisher,
                             MessageRpcClient messageRpcClient,
                             ObjectMapper objectMapper) {
        this.redis = redis;
        this.recordMapper = recordMapper;
        this.properties = properties;
        this.metrics = metrics;
        this.topics = topics;
        this.likeMsgPublisher = likeMsgPublisher;
        this.taskProgressPublisher = taskProgressPublisher;
        this.messageRpcClient = messageRpcClient;
        this.objectMapper = objectMapper;
    }

    // ==================================================================================
    // OP-01  GetLikeCount —— 对应 Go internal/logic/getlikecountlogic.go
    // ==================================================================================

    /**
     * 查询某类作品的一批点赞 / 点踩数。
     *
     * <p>流程：Redis 批量读 → 命中部分直接用；缺失部分查 DB → <b>回填 Redis</b>。
     */
    public GetLikeCountResp getLikeCount(GetLikeCountReq request) {
        GetLikeCountResp.Builder resp = GetLikeCountResp.newBuilder();

        // Go: if len(in.TargetIds) == 0 { return nil, NewGrpcErr(ErrorInputWrong, "输入需要查询的ID为空") }
        if (request.getTargetIdsCount() == 0) {
            throw LikeExceptions.inputWrong("输入需要查询的ID为空");
        }

        List<String> ids = request.getTargetIdsList();
        String likeCountKey = LikeRedisKeys.likeCountHash(request.getTargetType());
        String dislikeCountKey = LikeRedisKeys.dislikeCountHash(request.getTargetType());

        // 两次 hmget 各自独立容错：Redis 挂了 ⇒ 两个都空 ⇒ 全部落 missingIds ⇒ 全量回源 DB
        List<String> likeVals = hmgetLenient(likeCountKey, ids);
        List<String> dislikeVals = hmgetLenient(dislikeCountKey, ids);

        List<String> missingIds = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            // Go: if len(likeVals) > i { likeStr = likeVals[i] } —— 越界时保持零值 ""
            String likeStr = i < likeVals.size() ? likeVals.get(i) : "";
            String dislikeStr = i < dislikeVals.size() ? dislikeVals.get(i) : "";

            // Go: if likeStr == "" { missingIds = append(...); continue }
            // ⚠️ 判定只看 like：Redis 里存了 like 却没有 dislike 是常态（没点踩过）
            if (likeStr.isEmpty()) {
                missingIds.add(id);
                continue;
            }
            // Go: if dislikeStr == "" { dislikeStr = "0" }
            if (dislikeStr.isEmpty()) {
                dislikeStr = "0";
            }
            resp.putCounts(id, LikeCountItem.newBuilder()
                    .setLikeCount(parseLongOrZero(likeStr))
                    .setDislikeCount(parseLongOrZero(dislikeStr))
                    .build());
        }

        int hits = ids.size() - missingIds.size();
        int misses = missingIds.size();
        if (hits > 0) {
            metrics.cache(request.getTargetType(), "hit", hits);
        }

        if (misses > 0) {
            metrics.cache(request.getTargetType(), "miss", misses);
            // Go: return nil, errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败") —— DB 失败即致命
            Map<String, Map<Integer, Long>> dbCounts =
                    loadStateCounts(request.getTargetType(), missingIds);

            for (String id : missingIds) {
                long likeCount = 0L;
                long dislikeCount = 0L;
                Map<Integer, Long> byState = dbCounts.get(id);
                if (byState != null) {
                    // Go: likeCount = counts[1]; dislikeCount = counts[2]
                    likeCount = byState.getOrDefault(1, 0L);
                    dislikeCount = byState.getOrDefault(2, 0L);
                }
                resp.putCounts(id, LikeCountItem.newBuilder()
                        .setLikeCount(likeCount)
                        .setDislikeCount(dislikeCount)
                        .build());

                // Go: 回填 Redis；写失败只记日志，不影响本次响应
                hsetLenient(likeCountKey, id, String.valueOf(likeCount));
                hsetLenient(dislikeCountKey, id, String.valueOf(dislikeCount));
            }
        }

        return resp.build();
    }

    /**
     * 对应 Go {@code GetBatchLikeCount} 的结果整形：
     * {@code map[target_id]map[state]count}。
     *
     * <p>DB 查询失败时抛 5003（致命）—— 这是本域「Redis 降级 / DB 致命」不对称中的 DB 那一半。
     */
    private Map<String, Map<Integer, Long>> loadStateCounts(String targetType, List<String> targetIds) {
        List<LikeRecordMapper.StateCountRow> rows;
        try {
            rows = recordMapper.selectStateCountsBatch(targetType, targetIds);
        } catch (Exception e) {
            throw LikeExceptions.dbSelect("DB查询失败");
        }
        Map<String, Map<Integer, Long>> result = new HashMap<>();
        for (LikeRecordMapper.StateCountRow row : rows) {
            result.computeIfAbsent(row.getTargetId(), k -> new HashMap<>())
                    .put(row.getState(), row.getCnt());
        }
        return result;
    }

    // ==================================================================================
    // OP-02  GetLikeState —— 对应 Go internal/logic/getlikestatelogic.go
    // ==================================================================================

    /**
     * 查询某用户对一批作品的状态。
     *
     * <p>⚠️ <b>防穿透行为</b>：DB 回源时查不到的 id 取 Go map 的<b>零值 0</b> 并<b>写回 Redis</b>
     * （Go 注释：「否则如果多次连续发 Redis 中不存在的数据, 一直调用 SQL, 会导致很大的问题」）。
     * 照搬：把「查不到」也当作 {@code state=0} 缓存起来。
     */
    public GetLikeStateResp getLikeState(GetLikeStateReq request) {
        GetLikeStateResp.Builder resp = GetLikeStateResp.newBuilder();

        // Go: if len(in.TargetIds) == 0 { ... "输入需要查询的ID为空" }
        if (request.getTargetIdsCount() == 0) {
            throw LikeExceptions.inputWrong("输入需要查询的ID为空");
        }

        List<String> ids = request.getTargetIdsList();
        String field = String.valueOf(request.getUserId());
        List<String> missingIds = new ArrayList<>();

        for (String id : ids) {
            String stateKey = LikeRedisKeys.likeStateHash(request.getTargetType(), id);
            // Go: stateStr, err := HgetCtx(...); if err != nil { log }; if stateStr == "" { missing }
            String stateStr = hgetLenient(stateKey, field);
            if (stateStr.isEmpty()) {
                missingIds.add(id);
                continue;
            }
            resp.putStates(id, (int) parseLongOrZero(stateStr));
        }

        int hits = ids.size() - missingIds.size();
        if (hits > 0) {
            metrics.cache(request.getTargetType(), "hit", hits);
        }

        if (!missingIds.isEmpty()) {
            metrics.cache(request.getTargetType(), "miss", missingIds.size());

            Map<String, Integer> dbStates;
            try {
                dbStates = loadUserStates(request.getUserId(), request.getTargetType(), missingIds);
            } catch (LikeExceptions.Rpc e) {
                throw e;
            } catch (Exception e) {
                throw LikeExceptions.dbSelect("DB查询失败");
            }

            for (String id : missingIds) {
                // Go: state := dbRespMap[id] —— 查不到即 map 零值 0，并写回 Redis 防穿透
                int state = dbStates.getOrDefault(id, 0);
                resp.putStates(id, state);
                hsetLenient(LikeRedisKeys.likeStateHash(request.getTargetType(), id), field, String.valueOf(state));
            }
        }

        return resp.build();
    }

    private Map<String, Integer> loadUserStates(long userId, String targetType, List<String> targetIds) {
        List<LikeRecordMapper.TargetStateRow> rows =
                recordMapper.selectUserStatesBatch(userId, targetType, targetIds);
        Map<String, Integer> result = new HashMap<>();
        for (LikeRecordMapper.TargetStateRow row : rows) {
            result.put(row.getTargetId(), row.getState());
        }
        return result;
    }

    // ==================================================================================
    // OP-03  GetUserTotalLike —— 对应 Go internal/logic/getusertotallikelogic.go
    // ==================================================================================

    /**
     * 查询某作者的累计获赞数。
     *
     * <p>本域<b>唯一</b>一个用 String + TTL 的缓存（其余都是 Hash/ZSet 且多数无 TTL）。
     *
     * <p>⚠️ 两个细节：
     * <ul>
     *   <li><b>命中后要续期</b>：Go {@code ExpireCtx(ctx, redisKey, ttl)} 把 TTL 重置，
     *       所以热门作者的计数不会被固定窗口截断。</li>
     *   <li>Redis 出错时 Go 走的是 {@code if err != nil {log} else if val != "" {return}}，
     *       错误分支<b>不 return</b> ⇒ 落到 miss 分支查 DB。<b>Redis 故障被当成 miss</b>。</li>
     * </ul>
     */
    public GetUserTotalLikeResp getUserTotalLike(GetUserTotalLikeReq request) {
        long userId = request.getUserId();
        // Go: if in.UserId <= 0 { ... "非法的用户ID" }
        if (userId <= 0) {
            throw LikeExceptions.illegalUserId("非法的用户ID");
        }

        String key = LikeRedisKeys.userTotalLikeString(userId);
        String cached = getLenient(key);

        if (!cached.isEmpty()) {
            metrics.cache("user_total", "hit");
            long count = parseLongOrZero(cached);
            // Go: _ = ExpireCtx(ctx, redisKey, int(ttl)) —— 续期，失败也忽略
            expireLenient(key, properties.getRedis().getCacheTtl());
            return GetUserTotalLikeResp.newBuilder().setTotalLikeCount(count).build();
        }

        metrics.cache("user_total", "miss");

        final long totalCount;
        try {
            totalCount = recordMapper.countActiveLikeByAuthor(userId);
        } catch (Exception e) {
            // Go: return nil, errmsg.NewGrpcErr(errmsg.ErrorDbSelect, "DB查询失败")
            throw LikeExceptions.dbSelect("DB查询失败");
        }

        // Go: SetexCtx(...) 失败只记日志，不影响返回值
        setexLenient(key, String.valueOf(totalCount), properties.getRedis().getCacheTtl());
        return GetUserTotalLikeResp.newBuilder().setTotalLikeCount(totalCount).build();
    }

    // ==================================================================================
    // OP-04  GetUserLikeList —— 对应 Go internal/logic/getuserlikelistlogic.go
    // ==================================================================================

    /**
     * 某用户给出的点赞列表（游标分页）。
     *
     * <h3>⚠️ 两处 Go 的「不一致刻度」，必须原样复刻</h3>
     *
     * <p><b>(1) cursor 同时当两种刻度用</b>：Go 把同一个 cursor 既当作 ZSet 的 {@code maxScore}
     * （{@code maxScore = cursor - 1}），又当作 DB 的 {@code id < cursor} 上界。
     * 而 cursor 是 {@code score} 本身（{@code timestamp << 22}），不是 {@code like_record.id}。
     * 两套刻度不一致 ⇒ 翻页在某些数据分布下会跳/重。<b>这是 Go 的现状，不修</b>。
     *
     * <p><b>(2) DB 回源写进 ZSet 的 score 用「秒」，而 Redis 写入路径用「毫秒」</b>：
     * <ul>
     *   <li>写路径（Lua 的 {@code score}）来自 {@link LikeCursorIdGenerator} = {@code UnixMilli << 22}；</li>
     *   <li>回源路径 {@code score = timestamp << 22}，而 {@code timestamp} 来自
     *       {@code UserLikeListResult.CreateTime int64} —— GORM v1.31.1
     *       {@code schema/field.go:671-678} 对「int64 目标 + time.Time 源」取的是
     *       {@code data.Unix()}，即<b>秒</b>。</li>
     * </ul>
     * 后果：某条目一旦被 DB 回源「扶正」进 ZSet，之后走 Redis 命中分支时
     * {@code score >> 22} 反解出来的 {@code timestamp} 会比写路径写入的小 1000 倍。
     * 这是<b>真实存在的可观察行为</b>（响应里的 {@code LikeRecordItem.timestamp}），
     * 按 AGENTS.md §15「不擅自修复 Go 的行为」原样保留，并在此显式留痕。
     */
    public GetUserLikeListResp getUserLikeList(GetUserLikeListReq request) {
        int limit = normalizePageSize(request.getPageSize());
        int fetchLimit = limit + 1;

        String redisKey = LikeRedisKeys.userLikeListZSet(request.getUserId(), request.getTargetType());
        long maxScore = request.getCursor() > 0 ? request.getCursor() - 1 : Long.MAX_VALUE;

        // Go: res, err := ZrevrangebyscoreWithScoresAndLimitCtx(...); if err != nil { log } —— 不 return
        List<ZSetOperations.TypedTuple<String>> res = zrevrangeLenient(redisKey, maxScore, fetchLimit);

        List<LikeRecordItem> list = new ArrayList<>();
        long nextCursor = 0L;
        boolean isEnd = true;

        if (!res.isEmpty()) {
            List<ZSetOperations.TypedTuple<String>> effective = res;
            if (effective.size() > limit) {
                isEnd = false;
                effective = effective.subList(0, limit);
            }
            for (ZSetOperations.TypedTuple<String> pair : effective) {
                long score = toLongBits(pair.getScore());
                // Go: realTimestamp := score >> 22
                list.add(LikeRecordItem.newBuilder()
                        .setTargetId(pair.getValue())
                        // Go: TargetType: in.TargetType —— 注意用的是**请求里的**类型，不是库里存的
                        .setTargetType(request.getTargetType())
                        .setTimestamp(LikeCursorIdGenerator.timestampOf(score))
                        .build());
                nextCursor = score;
            }
        }

        if (!list.isEmpty()) {
            metrics.cache(request.getTargetType(), "hit");
        }

        if (list.isEmpty()) {
            metrics.cache(request.getTargetType(), "miss");

            final List<LikeRecordMapper.UserLikeRow> dbResults;
            try {
                dbResults = recordMapper.selectUserLikeList(request.getUserId(), request.getTargetType(),
                        request.getCursor(), fetchLimit);
            } catch (Exception e) {
                throw LikeExceptions.dbSelect("DB查询失败");
            }

            if (dbResults.size() > limit) {
                isEnd = false;
            }

            for (int i = 0; i < dbResults.size(); i++) {
                LikeRecordMapper.UserLikeRow row = dbResults.get(i);
                // ⚠️ Go: var timestamp int64 = r.CreateTime —— GORM 给的是 **Unix 秒**，见方法注释
                long timestamp = epochSeconds(row.getCreatedAt());
                if (timestamp == 0L) {
                    // Go: 脏数据（缺时间戳）→ 记日志并跳过，**且不计入返回的 limit 条数**
                    LOG.warn("like list dirty data without timestamp: id={} type={} target={}",
                            row.getId(), row.getTargetType(), row.getTargetId());
                    continue;
                }
                long score = LikeCursorIdGenerator.scoreOf(timestamp);
                zaddLenient(redisKey, score, row.getTargetId());
                if (i < limit) {
                    list.add(LikeRecordItem.newBuilder()
                            .setTargetId(row.getTargetId())
                            // Go: TargetType: r.TargetType —— 这条走的是**库里存的**类型
                            .setTargetType(row.getTargetType())
                            .setTimestamp(timestamp)
                            .build());
                    nextCursor = score;
                }
            }

            if (!dbResults.isEmpty()) {
                // Go: if len(dbResults) > 0 { ExpireCtx(redisKey, 3600*24*7) }
                expireLenient(redisKey, properties.getRedis().getListTtl());
            }
        }

        if (isEnd) {
            // Go: if isEnd { nextCursor = 0 } —— 到头时把游标清零，客户端据此停止翻页
            nextCursor = 0L;
        }

        return GetUserLikeListResp.newBuilder()
                .addAllList(list)
                .setIsEnd(isEnd)
                .setNextCursor(nextCursor)
                .build();
    }

    // ==================================================================================
    // OP-05  GetTargetLikerList —— 对应 Go internal/logic/gettargetlikerlistlogic.go
    // ==================================================================================

    /**
     * 某作品的点赞人列表（游标分页）。
     *
     * <p>与 {@link #getUserLikeList} 同构，差异仅在：member 是 user_id（需 parse 成 long）、
     * 返回类型是 {@link LikeItem}（无 target_type 字段）。同样的「秒/毫秒刻度不一致」见该方法注释。
     */
    public GetTargetLikerListResp getTargetLikerList(GetTargetLikerListReq request) {
        int limit = normalizePageSize(request.getPageSize());
        int fetchLimit = limit + 1;

        String redisKey = LikeRedisKeys.targetLikerListZSet(request.getTargetType(), request.getTargetId());
        long maxScore = request.getCursor() > 0 ? request.getCursor() - 1 : Long.MAX_VALUE;

        List<ZSetOperations.TypedTuple<String>> res = zrevrangeLenient(redisKey, maxScore, fetchLimit);

        List<LikeItem> list = new ArrayList<>();
        long nextCursor = 0L;
        boolean isEnd = true;

        if (!res.isEmpty()) {
            List<ZSetOperations.TypedTuple<String>> effective = res;
            if (effective.size() > limit) {
                isEnd = false;
                effective = effective.subList(0, limit);
            }
            for (ZSetOperations.TypedTuple<String> pair : effective) {
                long score = toLongBits(pair.getScore());
                list.add(LikeItem.newBuilder()
                        // Go: userId, _ := strconv.ParseInt(pair.Key, 10, 64) —— 解析失败取 0
                        .setUserId(parseLongOrZero(pair.getValue()))
                        .setTimestamp(LikeCursorIdGenerator.timestampOf(score))
                        .build());
                nextCursor = score;
            }
        }

        if (!list.isEmpty()) {
            metrics.cache(request.getTargetType(), "hit");
        }

        if (list.isEmpty()) {
            metrics.cache(request.getTargetType(), "miss");

            final List<LikeRecordMapper.TargetLikerRow> dbResults;
            try {
                dbResults = recordMapper.selectTargetLikerList(request.getTargetType(), request.getTargetId(),
                        request.getCursor(), fetchLimit);
            } catch (Exception e) {
                throw LikeExceptions.dbSelect("DB查询失败");
            }

            if (dbResults.size() > limit) {
                isEnd = false;
            }

            for (int i = 0; i < dbResults.size(); i++) {
                LikeRecordMapper.TargetLikerRow row = dbResults.get(i);
                // ⚠️ 同样是 Unix **秒**，见 getUserLikeList 的方法注释
                long timestamp = epochSeconds(row.getCreatedAt());
                if (timestamp == 0L) {
                    LOG.warn("target liker list dirty data without timestamp: id={} userId={}",
                            row.getId(), row.getUserId());
                    continue;
                }
                long score = LikeCursorIdGenerator.scoreOf(timestamp);
                zaddLenient(redisKey, score, String.valueOf(row.getUserId()));
                if (i < limit) {
                    list.add(LikeItem.newBuilder()
                            .setUserId(row.getUserId())
                            .setTimestamp(timestamp)
                            .build());
                    nextCursor = score;
                }
            }

            if (!dbResults.isEmpty()) {
                // Go: ExpireCtx(redisKey, 86400*7)
                expireLenient(redisKey, properties.getRedis().getListTtl());
            }
        }

        if (isEnd) {
            nextCursor = 0L;
        }

        return GetTargetLikerListResp.newBuilder()
                .addAllList(list)
                .setIsEnd(isEnd)
                .setNextCursor(nextCursor)
                .build();
    }

    // ==================================================================================
    // OP-06  LikeAction —— 对应 Go internal/logic/likeactionlogic.go
    // ==================================================================================

    /**
     * 执行一次点赞 / 取消点赞 / 点踩 / 取消点踩。
     *
     * <p>本方法是全模块语义密度最高的一段，按 Go 的执行顺序严格对齐：
     * <pre>
     * 1. 参数校验（target_id / target_type 非空，user_id &gt; 0）
     * 2. 生成游标 ID，执行 Lua（6 个 key 原子改）
     * 3. 按 Lua 返回值分三支：0 幂等（不发 Kafka）/ 1 变更 / 2 首次点赞（isFirst=true）
     * 4. HGET 回读最终点赞数与点踩数
     * 5. 仅当 resInt != 0 且 action == LIKE 且 targetType 是 article 时：
     *    a. 用 max(Redis, DB) 修正点赞数
     *    b. 推任务进度（用户路 + 文章路）
     *    c. 若 author_id &gt; 0 且 user_id != author_id，给作者发通知
     * </pre>
     *
     * <p><b>⚠️ 第 4 步无条件执行</b>：Go 在 {@code resInt == 0}（幂等）时<b>不会短路</b>，
     * 仍然 HGET 两次并返回真实计数。所以「重复点赞」返回的是当前计数，而不是「什么都没做」。
     */
    public LikeActionResp likeAction(LikeActionReq request) {
        String actionStr = actionName(request.getActionTypeValue());

        // Go: if in.TargetId == "" || in.TargetType == "" || in.UserId <= 0 { ... "参数异常" }
        if (request.getTargetId().isEmpty()
                || request.getTargetType().isEmpty()
                || request.getUserId() <= 0) {
            metrics.action(request.getTargetType(), actionStr, "invalid_param");
            throw LikeExceptions.invalidActionParam("参数异常");
        }

        String countKey = LikeRedisKeys.likeCountHash(request.getTargetType());
        String stateKey = LikeRedisKeys.likeStateHash(request.getTargetType(), request.getTargetId());
        String userListKey = LikeRedisKeys.userLikeListZSet(request.getUserId(), request.getTargetType());
        String targetListKey = LikeRedisKeys.targetLikerListZSet(request.getTargetType(), request.getTargetId());
        String dislikeCountKey = LikeRedisKeys.dislikeCountHash(request.getTargetType());
        String authorTotalKey = LikeRedisKeys.userTotalLikeString(request.getAuthorId());

        long score = LikeCursorIdGenerator.nextId();

        // ⚠️ Lua 执行失败是**致命**的：写路径没有回源可退
        final Long raw;
        try {
            raw = redis.execute(
                    LikeActionScript.INSTANCE,
                    List.of(countKey, stateKey, userListKey, targetListKey, dislikeCountKey, authorTotalKey),
                    LikeActionScript.args(request.getTargetId(), request.getUserId(),
                            request.getActionTypeValue(), score));
        } catch (Exception e) {
            metrics.action(request.getTargetType(), actionStr, "redis_error");
            throw LikeExceptions.redisUpdate("Redis更新失败");
        }
        long resultCode = raw == null ? 0L : raw;

        if (resultCode == LikeActionScript.RESULT_IDEMPOTENT) {
            metrics.action(request.getTargetType(), actionStr, "idempotent");
        } else {
            boolean isFirst = resultCode == LikeActionScript.RESULT_FIRST_LIKE;
            // Go: 雪花 ID 失败只记指标，**继续往下发消息**（msgId=0 → 消费侧会判为非法并丢弃）
            String msgId;
            try {
                msgId = String.valueOf(Snowflake.nextId());
            } catch (Exception e) {
                metrics.action(request.getTargetType(), actionStr, "snowflake_error");
                msgId = "0";
            }

            LikeEventContracts.KafkaLikeMsg msg = new LikeEventContracts.KafkaLikeMsg(
                    msgId, request.getUserId(), request.getTargetType(), request.getTargetId(),
                    request.getAuthorId(), request.getActionTypeValue(), isFirst,
                    // Go: CreatedAt: time.Now().Unix() —— Unix 秒
                    java.time.Instant.now().getEpochSecond());

            // ⚠️ Kafka 推送失败**不影响** RPC 返回值（Go 只打日志）
            if (likeMsgPublisher.publish(msg)) {
                metrics.action(request.getTargetType(), actionStr, "success");
            } else {
                metrics.action(request.getTargetType(), actionStr, "kafka_error");
            }
        }

        // ---- 以下无条件执行（Go 在 resInt==0 时也不短路）----
        long finalLikeCount = hgetCountLenient(countKey, request.getTargetId(), "like_count");
        long finalDislikeCount = hgetCountLenient(dislikeCountKey, request.getTargetId(), "dislike_count");

        boolean likeOnArticle = resultCode != 0
                && request.getActionType() == ActionType.ACTION_LIKE
                // Go: strings.EqualFold(in.TargetType, "article") —— 忽略大小写
                && TARGET_TYPE_ARTICLE.equalsIgnoreCase(request.getTargetType());

        if (likeOnArticle) {
            // Go: finallikeCount = l.currentArticleLikeTotal(targetType, targetId, finallikeCount)
            finalLikeCount = currentArticleLikeTotal(request.getTargetType(), request.getTargetId(), finalLikeCount);

            // Go: l.syncTaskProgress(in, userListKey, finallikeCount)
            long userLikesGiven = currentUserLikeTotal(request.getUserId(), request.getTargetType(), userListKey);
            taskProgressPublisher.publish(request.getUserId(), request.getAuthorId(),
                    request.getTargetId(), userLikesGiven, finalLikeCount);
        }

        if (likeOnArticle
                && request.getAuthorId() > 0
                && request.getUserId() != request.getAuthorId()) {
            // Go: notifyArticleLiked(in) —— 失败只记日志
            messageRpcClient.sendArticleLiked(request.getAuthorId(), request.getUserId(),
                    request.getTargetType(), request.getTargetId());
        }

        return LikeActionResp.newBuilder()
                // Go: 无论成功与否都返回 Success: true —— 错误是通过 gRPC error 表达的，不是这个字段
                .setSuccess(true)
                .setMessage("action success")
                .setLikeCount(finalLikeCount)
                .setDislikeCount(finalDislikeCount)
                .build();
    }

    /**
     * 对应 Go {@code currentArticleLikeTotal}：{@code max(redisTotal, dbTotal)}。
     *
     * <p>DB 出错时返回 redisTotal（不抛）—— 缓存已经有值，降级用它。
     */
    private long currentArticleLikeTotal(String targetType, String targetId, long redisTotal) {
        long dbTotal;
        try {
            dbTotal = recordMapper.countActiveLikeByTarget(targetType, targetId);
        } catch (Exception e) {
            return redisTotal;
        }
        return dbTotal > redisTotal ? dbTotal : redisTotal;
    }

    /**
     * 对应 Go {@code currentUserLikeTotal}。
     *
     * <p>⚠️ Go 这里的错误处理有个容易看错的分支：
     * <pre>
     * total, err := ZcardCtx(...)
     * if err != nil { total = 0 }
     * dbTotal, dbErr := GetUserActiveLikeCount(...)
     * if dbErr != nil {
     *     if err == nil { return int64(total), nil }   // 只有 Redis 成功、DB 失败才返回 total
     *     return 0, dbErr                                // 两者都失败才返回错误
     * }
     * </pre>
     * 即：Redis 与 DB **同时**失败才算失败，且此时返回 {@code 0} + error。
     * 调用方 {@code syncTaskProgress} 只在出错时记日志，不影响点赞结果。
     */
    private long currentUserLikeTotal(long userId, String targetType, String userListKey) {
        long total = 0L;
        boolean redisOk = true;
        try {
            Long card = redis.opsForZSet().zCard(userListKey);
            total = card == null ? 0L : card;
        } catch (Exception e) {
            redisOk = false;
            total = 0L;
        }

        long dbTotal;
        try {
            dbTotal = recordMapper.countActiveLikeByUserAndType(userId, targetType);
        } catch (Exception e) {
            if (redisOk) {
                return total;
            }
            LOG.warn("both redis and db failed for user like total: userId={} type={}", userId, targetType);
            return 0L;
        }

        return dbTotal > total ? dbTotal : total;
    }

    // ==================================================================================
    // Redis 适配层（错误语义的关键，全部 private，语义见方法注释）
    // ==================================================================================

    /**
     * {@code HMGET} 的容错封装。
     *
     * <p>Go：出错时返回 {@code (nil, err)}，调用方按「全空」处理 ⇒ 全部当 miss。
     * <p>Java：出错抛异常 ⇒ <b>必须捕获并返回空 List</b>，否则整个 RPC 变 5003。
     *
     * <p>⚠️ 返回<b>空 List</b>而不是等长的空串 List：Go 的 nil slice 让
     * {@code if len(vals) > i} 全部不成立；等长空串虽然结果一样，但会让
     * 「Redis 挂了」与「Redis 里确实没有」在调试时无法区分。
     */
    private List<String> hmgetLenient(String key, List<String> fields) {
        try {
            List<Object> raw = redis.opsForHash().multiGet(key, new ArrayList<>(fields));
            List<String> result = new ArrayList<>(fields.size());
            if (raw != null) {
                for (Object o : raw) {
                    // Go: Hmget 对不存在的 field 返回 ""；Lettuce 返回 null —— 归一化成 ""
                    result.add(o == null ? "" : String.valueOf(o));
                }
            }
            return result;
        } catch (Exception e) {
            metrics.cache(fields.isEmpty() ? "unknown" : keyFieldType(key), "redis_error", fields.size());
            LOG.warn("redis hmget failed, degrade to full miss: key={} err={}", key, e.getMessage());
            return List.of();
        }
    }

    /** {@code HGET} 的容错封装：出错返回 {@code ""}（= miss）。 */
    private String hgetLenient(String key, String field) {
        try {
            Object v = redis.opsForHash().get(key, field);
            return v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            LOG.warn("redis hget failed, degrade to miss: key={} field={} err={}", key, field, e.getMessage());
            return "";
        }
    }

    /** {@code HSET} 的容错封装：出错只记日志（回填失败不影响本次响应）。 */
    private void hsetLenient(String key, String field, String value) {
        try {
            redis.opsForHash().put(key, field, value);
        } catch (Exception e) {
            LOG.warn("redis hset failed (ignored): key={} field={} err={}", key, field, e.getMessage());
        }
    }

    /** {@code GET} 的容错封装：出错返回 {@code ""}。 */
    private String getLenient(String key) {
        try {
            String v = redis.opsForValue().get(key);
            return v == null ? "" : v;
        } catch (Exception e) {
            metrics.cache("user_total", "redis_error");
            LOG.warn("redis get failed, degrade to miss: key={} err={}", key, e.getMessage());
            return "";
        }
    }

    /** {@code SETEX} 的容错封装：出错只记日志。 */
    private void setexLenient(String key, String value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            LOG.warn("redis setex failed (ignored): key={} err={}", key, e.getMessage());
        }
    }

    /** {@code EXPIRE} 的容错封装：出错只记日志。 */
    private void expireLenient(String key, Duration ttl) {
        try {
            redis.expire(key, ttl);
        } catch (Exception e) {
            LOG.warn("redis expire failed (ignored): key={} err={}", key, e.getMessage());
        }
    }

    /**
     * {@code ZREVRANGEBYSCORE … WITHSCORES LIMIT 0 n} 的容错封装。
     *
     * <p>Spring Data Redis 4.x 里带分数的变体叫 {@code reverseRangeByScoreWithScores}
     * （{@code reverseRangeByScore} 只返回 member，不含 score —— 那样就拿不到游标了）。
     *
     * <p>出错返回<b>空 List</b>而不是 null：上层判据统一写成 {@code res.isEmpty()}，
     * 免得 null 判空漏一处。
     */
    private List<ZSetOperations.TypedTuple<String>> zrevrangeLenient(String key, long maxScore, int count) {
        try {
            // Go: ZrevrangebyscoreWithScoresAndLimitCtx(ctx, key, 0, maxScore, 0, fetchLimit)
            Set<ZSetOperations.TypedTuple<String>> result =
                    redis.opsForZSet().reverseRangeByScoreWithScores(key, 0D, (double) maxScore, 0L, count);
            return result == null ? List.of() : new ArrayList<>(result);
        } catch (Exception e) {
            metrics.cache(keyFieldType(key), "redis_error");
            LOG.warn("redis zrevrangebyscore failed, degrade to miss: key={} err={}", key, e.getMessage());
            return List.of();
        }
    }

    /** {@code ZADD} 的容错封装：出错只记日志（Go 同样只记日志 + 计 redis_error）。 */
    private void zaddLenient(String key, double score, String member) {
        try {
            redis.opsForZSet().add(key, member, score);
        } catch (Exception e) {
            metrics.cache(keyFieldType(key), "redis_error");
            LOG.warn("redis zadd failed (ignored): key={} member={} err={}", key, member, e.getMessage());
        }
    }

    /**
     * 读取点赞数 / 点踩数并解析。
     *
     * <p>Go 的两处细节：
     * <ul>
     *   <li>读失败 ⇒ 计数保持 0，并记 {@code redis_read_error} 指标 + 日志；</li>
     *   <li>解析失败 ⇒ 同样保持 0，记 {@code ErrorTypeTransfer} 日志。</li>
     * </ul>
     * <b>两种情况都不影响响应</b>：返回 0，而不是报错。
     */
    private long hgetCountLenient(String hashKey, String field, String metricTargetType) {
        String raw;
        try {
            Object v = redis.opsForHash().get(hashKey, field);
            raw = v == null ? "" : String.valueOf(v);
        } catch (Exception e) {
            metrics.action(metricTargetType, "n/a", "redis_read_error");
            LOG.warn("redis read count failed: key={} field={} err={}", hashKey, field, e.getMessage());
            return 0L;
        }
        if (raw.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // Go: strconv.ParseInt 出错被忽略，计数保持 0
            LOG.warn("redis count value is not a number: key={} field={} raw={}", hashKey, field, raw);
            return 0L;
        }
    }

    // ==================================================================================
    // 工具方法
    // ==================================================================================

    /**
     * 对应 Go {@code strconv.ParseInt(s, 10, 64)} <b>忽略 error</b> 的写法。
     *
     * <p>Go 侧所有解析都是 {@code x, _ := strconv.ParseInt(...)}，出错即取 0。
     * 这里显式返回 0 而不是抛异常，避免把「脏数据」升级成「请求失败」。
     */
    private static long parseLongOrZero(String raw) {
        if (raw == null || raw.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** Go {@code limit <= 0 || limit > 100 ⇒ 10}。 */
    private static int normalizePageSize(int pageSize) {
        return (pageSize <= 0 || pageSize > PAGE_SIZE_MAX) ? PAGE_SIZE_DEFAULT : pageSize;
    }

    /** 对应 Go {@code getActionString}：仅用于 metrics label 与日志。 */
    private static String actionName(int actionTypeValue) {
        return switch (actionTypeValue) {
            case 1 -> "like";
            case 2 -> "unlike";
            case 3 -> "dislike";
            case 4 -> "undislike";
            default -> ACTION_UNKNOWN;
        };
    }

    /**
     * ZSet 的 score 在 Java 侧是 {@code double}，而游标是 {@code long} 位的整数。
     *
     * <p>⚠️ Go 侧 score 本身就是 {@code float64}（Redis 的 ZSet score 就是 double），
     * 只是因为 {@code timestamp << 22} 的值远小于 2^53 才没丢精度。这里同样直接转 longBits。
     */
    private static long toLongBits(Double score) {
        return score == null ? 0L : (long) (double) score;
    }

    /**
     * {@code OffsetDateTime} → Unix <b>秒</b>。
     *
     * <p>⚠️ 这个「秒」是刻意的：Go 侧 GORM 把 {@code timestamptz} 扫进 {@code int64} 字段时
     * 取的是 {@code data.Unix()}（gorm v1.31.1 {@code schema/field.go:671-678}）。
     * 若这里改成毫秒，DB 回源写进 ZSet 的 score 会与写路径差 1000 倍，
     * 从而改变 {@code LikeRecordItem.timestamp} 的取值 —— 属行为变更。
     */
    private static long epochSeconds(java.time.OffsetDateTime value) {
        return value == null ? 0L : value.toEpochSecond();
    }

    /**
     * 从 key 前缀推断 metrics 的 {@code target_type} label。
     *
     * <p>Go 的 metrics label 是 {@code in.TargetType}（请求里的值），而回填/读取时手里只有 key。
     * 为避免「同一个逻辑用两套 label 值」（会让 Prometheus 查询漏数据），
     * 这里从 key 的第二段反解 —— 对 {@code like_count:article} 得到 {@code "article"}，
     * 与 Go 的 label 完全一致。
     */
    private static String keyFieldType(String key) {
        int idx = key.indexOf(':');
        if (idx < 0 || idx + 1 >= key.length()) {
            return "unknown";
        }
        String rest = key.substring(idx + 1);
        int second = rest.indexOf(':');
        return second < 0 ? rest : rest.substring(0, second);
    }

    // ==================================================================================
    // Kafka 消息发布（独立成 Bean 以便 LikeAction 的 @Transactional/代理边界干净）
    // ==================================================================================

    /**
     * 点赞事件发布器。
     *
     * <p><b>为什么要独立成 Bean</b>：本域没有 @Transactional 的自调用问题（点赞路径无事务），
     * 抽出纯粹是为了让 {@link LikeDomainService} 的构造依赖保持在「读缓存 / 读库 / 发消息」三类里可辨认。
     *
     * <p><b>⚠️ 失败语义</b>：Go {@code KafkaPusher.Push} 出错只 {@code LogBusinessErr(ErrorKafkaPush)}，
     * <b>不返回错误</b>。本类返回 boolean 仅供 metrics 打点用，调用方<b>不</b>据此改变行为。
     */
    @org.springframework.stereotype.Component("likeMsgPublisher")
    public static class KafkaLikeMsgPublisher {

        private final org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
        private final LikeKafkaTopics topics;
        private final ObjectMapper objectMapper;

        public KafkaLikeMsgPublisher(org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate,
                                     LikeKafkaTopics topics,
                                     ObjectMapper objectMapper) {
            this.kafkaTemplate = kafkaTemplate;
            this.topics = topics;
            this.objectMapper = objectMapper;
        }

        /**
         * @return true 表示消息已成功交给 Kafka 客户端；false 表示序列化或发送失败
         */
        public boolean publish(LikeEventContracts.KafkaLikeMsg msg) {
            final String payload;
            try {
                payload = objectMapper.writeValueAsString(msg);
            } catch (Exception e) {
                LOG.warn("marshal like msg failed: {}", e.getMessage());
                return false;
            }
            try {
                // Go: KafkaPusher.Push(ctx, string(msgBytes)) —— **不带 key**
                kafkaTemplate.send(topics.getLikeTopic(), null, payload);
                return true;
            } catch (Exception e) {
                // Go: LogBusinessErr(..., ErrorKafkaPush, "Kafka消息发送失败, 丢失消息: %s")
                LOG.warn("push like kafka msg failed, message dropped: {} err={}", payload, e.getMessage());
                return false;
            }
        }
    }
}