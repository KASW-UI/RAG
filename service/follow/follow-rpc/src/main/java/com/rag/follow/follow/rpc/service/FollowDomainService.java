package com.rag.follow.follow.rpc.service;

import com.rag.common.logger.LogOption;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.proto.RelationReq;
import com.rag.follow.follow.proto.UserListResp;
import com.rag.follow.follow.rpc.config.FollowRpcProperties;
import com.rag.follow.follow.rpc.mapper.BlockRelationMapper;
import com.rag.follow.follow.rpc.mapper.FollowRelationMapper;
import com.rag.follow.follow.rpc.mapper.GoStyleLimitOffset;
import com.rag.follow.follow.rpc.metrics.FollowRpcMetrics;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * follow 域 8 个 gRPC 用例的业务编排 —— 逐个对应 Go 端
 * {@code service/follow/rpc/internal/logic/} 下的 8 个 logic 文件。
 *
 * <h2>本类与 Go 的 8 个 logic 的对应关系</h2>
 * <table border="1">
 *   <caption>方法 ↔ Go logic 一一对应</caption>
 *   <tr><th>本类方法</th><th>Go 文件</th><th>validate</th><th>ensureUserExists</th><th>DB</th></tr>
 *   <tr><td>{@link #follow}</td><td>followlogic.go</td><td>✔</td><td>target</td><td>ExistsAnyBlock → CreateFollow</td></tr>
 *   <tr><td>{@link #unfollow}</td><td>unfollowlogic.go</td><td>✔</td><td><b>✘</b></td><td>DeleteFollow</td></tr>
 *   <tr><td>{@link #block}</td><td>blocklogic.go</td><td>✔</td><td>target</td><td>CreateBlockAndCleanup</td></tr>
 *   <tr><td>{@link #unblock}</td><td>unblocklogic.go</td><td>✔</td><td><b>✘</b></td><td>DeleteBlock</td></tr>
 *   <tr><td>{@link #getFollowList}</td><td>getfollowlistlogic.go</td><td>normalize</td><td>self</td><td>ListFollowTargets</td></tr>
 *   <tr><td>{@link #getBlockList}</td><td>getblocklistlogic.go</td><td>normalize</td><td>self</td><td>ListBlockTargets</td></tr>
 *   <tr><td>{@link #getFollowerList}</td><td>getfollowerlistlogic.go</td><td>normalize</td><td>self</td><td>ListFollowerUsers</td></tr>
 *   <tr><td>{@link #getRecommendations}</td><td>getrecommendationslogic.go</td><td>normalize</td><td>self</td><td>见 {@link FollowRecommendationService}</td></tr>
 * </table>
 *
 * <h2>⚠️ 三条最容易被「统一风格」抹平的语义</h2>
 * <ol>
 *   <li><b>Unfollow / Unblock 不校验用户存在性</b>。
 *       两个创建操作（Follow / Block）会先 {@code ensureUserExists(targetId)}，
 *       两个删除操作<b>不</b>。所以「关注一个不存在的人」被拒，
 *       而「取消关注一个不存在的人」<b>照样成功</b>（删 0 行）。
 *       ⚠️ 这在直觉上像 bug，但它是 Go 的实际行为，且 L3 上可观察 ⇒ 照搬，不要补。</li>
 *   <li><b>只有 Follow 做拉黑检查</b>（{@code ErrorRelationBlocked} / 5202）。
 *       Unfollow / Block / Unblock / 三个列表 / 推荐<b>都没有</b>这一步。</li>
 *   <li><b>指标口径</b>：4 个写方法上报 <b>两个</b>指标（{@code ObserveRPC} + {@code ObserveRelation}），
 *       4 个读方法只上报 {@code ObserveRPC}。Go 每个 logic 的 defer 明确区分，
 *       这里用 try/catch/finally 复刻。</li>
 * </ol>
 *
 * <h2>指标与日志的对应实现</h2>
 * <p>Go 用「命名返回值 + defer」实现：defer 里读 {@code err} 判断成功/失败。
 * Java 没有命名返回值，等价形态是 <b>{@code catch 里赋值 + finally 上报}</b>：
 * 正常返回时 {@code failure} 保持 null，异常时先记下再重抛。语义与 Go 一致。
 */
@Service
public class FollowDomainService {

    private final FollowRelationMapper followRelationMapper;
    private final BlockRelationMapper blockRelationMapper;
    private final FollowRpcSupport support;
    private final FollowUserExistenceService userExistence;
    private final FollowBlockTransactionService blockTransaction;
    private final FollowRecommendationService recommendationService;
    private final FollowRpcProperties properties;
    private final FollowRpcMetrics metrics;
    private final LoggerFactoryBean logger;

    public FollowDomainService(FollowRelationMapper followRelationMapper,
                               BlockRelationMapper blockRelationMapper,
                               FollowRpcSupport support,
                               FollowUserExistenceService userExistence,
                               FollowBlockTransactionService blockTransaction,
                               FollowRecommendationService recommendationService,
                               FollowRpcProperties properties,
                               FollowRpcMetrics metrics,
                               LoggerFactoryBean logger) {
        this.followRelationMapper = followRelationMapper;
        this.blockRelationMapper = blockRelationMapper;
        this.support = support;
        this.userExistence = userExistence;
        this.blockTransaction = blockTransaction;
        this.recommendationService = recommendationService;
        this.properties = properties;
        this.metrics = metrics;
        this.logger = logger;
    }

    // =============================================================================
    // 4 个写方法
    // =============================================================================

    /**
     * 对应 Go {@code FollowLogic.Follow}（{@code followlogic.go:31-68}）。
     *
     * <p>执行顺序<b>严格照 Go</b>，每一步都是一道闸门：
     * <ol>
     *   <li>validateRelationReq → 5003 / 5201</li>
     *   <li>ensureUserExists(<b>targetId</b>) → 5101 / 5001</li>
     *   <li>ExistsAnyBlock → DB 错 5007；<b>已拉黑 ⇒ 5202</b></li>
     *   <li>CreateFollow（ON CONFLICT DO NOTHING）→ DB 错 5008</li>
     * </ol>
     *
     * <p><b>⚠️ 重复关注返回 SUCCESS</b>（第 4 步插入 0 行、无异常），
     * 这是 {@code uk_follow_pair} + {@code DO NOTHING} 的组合语义，
     * <b>不是</b> 5006 already exists。
     */
    public BaseResp follow(RelationReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            long userId = in.getUserId();
            long targetId = in.getTargetId();

            try {
                support.validateRelationReq(in);
            } catch (RuntimeException e) {
                logBusinessErr(e, userId);
                throw e;
            }

            try {
                userExistence.ensureUserExists(targetId);
            } catch (RuntimeException e) {
                logBusinessErr(e, userId);
                throw e;
            }

            long blockCount = dbSelect("follow", "exists_any_block",
                    () -> blockRelationMapper.countAnyBlock(userId, targetId), userId);
            if (blockCount > 0) {
                StatusRuntimeException denied = FollowRpcSupport.rpcError(
                        Status.Code.PERMISSION_DENIED, FollowErrorCode.ERROR_RELATION_BLOCKED.getCode());
                logBusinessErr(denied, FollowErrorCode.ERROR_RELATION_BLOCKED.getCode(), userId);
                throw denied;
            }

            dbUpdate("follow", "create_follow",
                    () -> insertFollow(userId, targetId), userId);

            logInfo(userId, "follow relation updated");
            return support.successBaseResp();
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("Follow", startedAtNanos, failure);
            metrics.observeRelation("follow", failure);
        }
    }

    /**
     * 对应 Go {@code UnfollowLogic.Unfollow}（{@code unfollowlogic.go:31-51}）。
     *
     * <p><b>⚠️ 不调 ensureUserExists</b>（见类注释第 1 条）；删 0 行照样 SUCCESS。
     */
    public BaseResp unfollow(RelationReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            long userId = in.getUserId();
            long targetId = in.getTargetId();

            try {
                support.validateRelationReq(in);
            } catch (RuntimeException e) {
                logBusinessErr(e, userId);
                throw e;
            }

            dbUpdate("unfollow", "delete_follow",
                    () -> followRelationMapper.deleteByPair(userId, targetId), userId);

            logInfo(userId, "follow relation removed");
            return support.successBaseResp();
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("Unfollow", startedAtNanos, failure);
            metrics.observeRelation("unfollow", failure);
        }
    }

    /**
     * 对应 Go {@code BlockLogic.Block}（{@code blocklogic.go:31-56}）。
     *
     * <p><b>⚠️ Block 不检查「是否已被对方拉黑」</b>——它直接建立拉黑关系。
     * 也就是说 A 拉黑 B 时，即使 B 已经拉黑了 A，两个 block 行都会存在。
     * 这是 Go 的实际行为（{@code ExistsAnyBlock} 只在 Follow 路径调用），照搬。</p>
     */
    public BaseResp block(RelationReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            long userId = in.getUserId();
            long targetId = in.getTargetId();

            try {
                support.validateRelationReq(in);
            } catch (RuntimeException e) {
                logBusinessErr(e, userId);
                throw e;
            }

            try {
                userExistence.ensureUserExists(targetId);
            } catch (RuntimeException e) {
                logBusinessErr(e, userId);
                throw e;
            }

            dbUpdate("block", "create_block_and_cleanup",
                    () -> blockTransaction.createBlockAndCleanup(userId, targetId), userId);

            logInfo(userId, "block relation updated");
            return support.successBaseResp();
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("Block", startedAtNanos, failure);
            metrics.observeRelation("block", failure);
        }
    }

    /**
     * 对应 Go {@code UnblockLogic.Unblock}（{@code unblocklogic.go:31-51}）。
     *
     * <p><b>⚠️ 不调 ensureUserExists</b>；解一个不存在的拉黑照样 SUCCESS。</p>
     */
    public BaseResp unblock(RelationReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            long userId = in.getUserId();
            long targetId = in.getTargetId();

            try {
                support.validateRelationReq(in);
            } catch (RuntimeException e) {
                logBusinessErr(e, userId);
                throw e;
            }

            dbUpdate("unblock", "delete_block",
                    () -> blockRelationMapper.deleteByPair(userId, targetId), userId);

            logInfo(userId, "block relation removed");
            return support.successBaseResp();
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("Unblock", startedAtNanos, failure);
            metrics.observeRelation("unblock", failure);
        }
    }

    // =============================================================================
    // 3 个列表方法（结构同构，只有归一化参数与 SQL 不同）
    // =============================================================================

    /** 对应 Go {@code GetFollowListLogic.GetFollowList}（{@code getfollowlistlogic.go:31-57}）。 */
    public UserListResp getFollowList(ListReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            FollowRpcSupport.NormalizedListReq req = normalizeList(in);
            ensureSelf(req.userId());

            List<Long> userIds = dbSelect("get_follow_list", "list_follow_targets",
                    () -> followRelationMapper.listFollowTargets(
                            req.userId(), GoStyleLimitOffset.limitOffset(req.limit(), req.offset())),
                    req.userId());

            logInfo(req.userId(), "follow list loaded");
            return support.successUserListResp(userIds);
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("GetFollowList", startedAtNanos, failure);
        }
    }

    /** 对应 Go {@code GetBlockListLogic.GetBlockList}（{@code getblocklistlogic.go:31-57}）。 */
    public UserListResp getBlockList(ListReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            FollowRpcSupport.NormalizedListReq req = normalizeList(in);
            ensureSelf(req.userId());

            List<Long> userIds = dbSelect("get_block_list", "list_block_targets",
                    () -> blockRelationMapper.listBlockTargets(
                            req.userId(), GoStyleLimitOffset.limitOffset(req.limit(), req.offset())),
                    req.userId());

            logInfo(req.userId(), "block list loaded");
            return support.successUserListResp(userIds);
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("GetBlockList", startedAtNanos, failure);
        }
    }

    /** 对应 Go {@code GetFollowerListLogic.GetFollowerList}（{@code getfollowerlistlogic.go:31-57}）。 */
    public UserListResp getFollowerList(ListReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            FollowRpcSupport.NormalizedListReq req = normalizeList(in);
            ensureSelf(req.userId());

            // ⚠️ 过滤列是 target_id、投影列是 user_id —— 与另两个列表不同，别抄错
            List<Long> userIds = dbSelect("get_follower_list", "list_follower_users",
                    () -> followRelationMapper.listFollowerUsers(
                            req.userId(), GoStyleLimitOffset.limitOffset(req.limit(), req.offset())),
                    req.userId());

            logInfo(req.userId(), "follower list loaded");
            return support.successUserListResp(userIds);
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("GetFollowerList", startedAtNanos, failure);
        }
    }

    // =============================================================================
    // 推荐
    // =============================================================================

    /** 对应 Go {@code GetRecommendationsLogic.GetRecommendations}（{@code getrecommendationslogic.go:32-187}）。 */
    public RecommendResp getRecommendations(ListReq in) {
        long startedAtNanos = System.nanoTime();
        Throwable failure = null;
        try {
            // ⚠️ 用 Recommend 的 (20, 50)，不是 List 的 (20, 100)
            FollowRpcSupport.NormalizedListReq req = normalizeRecommendList(in);
            ensureSelf(req.userId());

            int fanout = support.clampFanout(properties.getRecommend().getMaxFanout());
            int depth = support.clampRecommendDepth(properties.getRecommend().getMaxDepth());

            // ⚠️ 此处**不**再包一层 catch 记日志：推荐算法内部的 4 个 DB 失败点
            //    （Go :59/:70/:77/:114）已在 FollowRecommendationService 逐点记录
            //    LogBusinessErr(ErrorDbSelect, dbErr, userLogOption(userID)) 并上报指标。
            //    再包一层会打两条日志，与 Go 的「每个失败点一条」不符。
            List<RecommendResp.RecommendUser> users = recommendationService.recommend(
                    req.userId(), req.offset(), req.limit(), fanout, depth);

            if (users == null || users.isEmpty()) {
                // Go 在这两条路径上传 successRecommendResp(nil) 且**不**打 LogInfo
                //（:64 直接 return，:168 直接 return）—— 只更新 gauge。
                return support.successRecommendResp(null);
            }

            logInfo(req.userId(), "recommendations generated");
            return support.successRecommendResp(users);
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            metrics.observeRpc("GetRecommendations", startedAtNanos, failure);
        }
    }

    // =============================================================================
    // 内部：归一化 / 存在性 / DB 错误映射 / 日志
    // =============================================================================

    /** 对应 3 个列表 logic 开头的 {@code normalizeListReq(List.DefaultLimit, List.MaxLimit, in)}。 */
    private FollowRpcSupport.NormalizedListReq normalizeList(ListReq in) {
        try {
            return support.normalizeListReq(
                    properties.getList().getDefaultLimit(), properties.getList().getMaxLimit(), in);
        } catch (RuntimeException e) {
            // ⚠️ Go 这条日志**不带** userLogOption（三个列表 logic 的 :39-41 都是裸调用），
            //    因为此时 userId 可能 <= 0，不适合当「用户」写进日志字段。逐条照搬。
            logBusinessErr(e, FollowGrpcError.bizCodeFromError(e), null);
            throw e;
        }
    }

    /** 推荐专用的归一化（默认/上限来自 RecommendConf 而非 ListConf）。 */
    private FollowRpcSupport.NormalizedListReq normalizeRecommendList(ListReq in) {
        try {
            return support.normalizeListReq(
                    properties.getRecommend().getDefaultLimit(),
                    properties.getRecommend().getMaxLimit(), in);
        } catch (RuntimeException e) {
            logBusinessErr(e, FollowGrpcError.bizCodeFromError(e), null);
            throw e;
        }
    }

    /** 三个列表与推荐的 {@code ensureUserExists(userID)} —— 注意是 <b>自己</b>，不是 target。 */
    private void ensureSelf(long userId) {
        try {
            userExistence.ensureUserExists(userId);
        } catch (RuntimeException e) {
            logBusinessErr(e, userId);
            throw e;
        }
    }

    /** 对应 Go 侧 create_time / update_time 由 GORM tag 填充（见 FollowRelationEntity 注释）。 */
    private int insertFollow(long userId, long targetId) {
        java.time.OffsetDateTime now = java.time.OffsetDateTime.now();
        return followRelationMapper.insertIgnoreConflict(userId, targetId, now, now);
    }

    /**
     * DB 查询失败 ⇒ 5007（{@code ErrorDbSelect}）。
     *
     * <p>对应 Go 各 logic 里的三行：
     * <pre>
     * metrics.ObserveDBError(action, type)
     * logger.LogBusinessErr(ctx, followcommon.ErrorDbSelect, dbErr, userLogOption(userID))
     * return nil, followcommon.GRPCError(codes.Internal, followcommon.ErrorDbSelect)
     * </pre>
     *
     * <p>⚠️ 日志记的是<b>原始 DB 异常</b>（含 SQL / 连接信息），而<b>发给客户端的只有
     * {@code "database query failed"}</b> —— 内部细节绝不外泄。这条分界不能模糊。
     *
     * <p><b>⚠️ 捕获范围是 {@link RuntimeException} 而非 {@code DataAccessException}</b>：
     * Go 侧写的是 {@code if dbErr != nil} —— 覆盖<b>任何</b>错误。
     * 收窄到 Spring 的 DB 异常层级会漏掉两类真实路径，它们都会把 5007 降级成 5001：
     * <ol>
     *   <li>{@code org.apache.ibatis.exceptions.PersistenceException} 且 cause 不是
     *       {@code SQLException} 时，MyBatis-Plus 不会包装成 {@code DataAccessException}；</li>
     *   <li>{@code com.rag.common.persistence.SqlTimeoutStatusTranslator} 把 pg 的
     *       {@code 57014 query_canceled} 翻成 DEADLINE_EXCEEDED 时抛的那一类，
     *       同样<b>不是</b> {@code DataAccessException}。</li>
     * </ol>
     * 这是本项目首个 follow 域实例里出现的形态；同款收窄在别的域需一并复核。
     *
     * <p><b>安全性</b>：{@code query} 的函数体<b>只有</b> mapper 调用，
     * 不会抛本项目自己的 {@code rpcError} ⇒ 不会把自己造的 5201/5101 误翻成 5007。
     */
    private <T> T dbSelect(String action, String type, Supplier<T> query, Long userId) {
        try {
            return query.get();
        } catch (RuntimeException e) {
            metrics.observeDbError(action, type);
            logBusinessErr(e, FollowErrorCode.ERROR_DB_SELECT.getCode(), userId);
            throw FollowRpcSupport.rpcError(Status.Code.INTERNAL,
                    FollowErrorCode.ERROR_DB_SELECT.getCode());
        }
    }

    /**
     * DB 写入失败 ⇒ 5008（{@code ErrorDbUpdate}）。与 {@link #dbSelect} 同构，只差业务码，
     * 捕获范围同样放宽到 {@link RuntimeException}（理由见 {@link #dbSelect}）。
     *
     * <p>⚠️ 这里覆盖的不只是 {@code createBlockAndCleanup}（其事务由
     * {@code FollowBlockTransactionService} 负责）：异常先逃出 {@code @Transactional} 方法边界
     * ⇒ Spring 先完成回滚 ⇒ 才回到这里被翻译成 5007/5008。<b>顺序不能倒</b>。
     */
    private void dbUpdate(String action, String type, Runnable write, Long userId) {
        try {
            write.run();
        } catch (RuntimeException e) {
            metrics.observeDbError(action, type);
            logBusinessErr(e, FollowErrorCode.ERROR_DB_UPDATE.getCode(), userId);
            throw FollowRpcSupport.rpcError(Status.Code.INTERNAL,
                    FollowErrorCode.ERROR_DB_UPDATE.getCode());
        }
    }

    // ---- 日志（对应 Go 的 logger.LogBusinessErr / LogInfo）----

    /**
     * 对应 Go 的 {@code logger.LogBusinessErr(ctx, BizCodeFromError(err), err, userLogOption(uid))}。
     *
     * <p>{@code Context.current()} 等价于 Go 的 {@code l.ctx}：gRPC server interceptor 已把
     * OTel span 放进当前线程的 Context。
     */
    private void logBusinessErr(Throwable err, long userId) {
        logBusinessErr(err, FollowGrpcError.bizCodeFromError(err), userId);
    }

    /**
     * @param userId 传 {@code null} 表示<b>不带</b> user_log 字段
     *                （对应 Go 里裸调 {@code LogBusinessErr(ctx, code, err)} 的那几处）
     */
    private void logBusinessErr(Throwable err, int bizCode, Long userId) {
        LogOption[] opts = userId == null ? new LogOption[0]
                : new LogOption[]{FollowRpcSupport.userLogOptionOf(userId)};
        logger.logBusinessErr(Context.current(), bizCode, err, opts);
    }

    /** 对应 Go 的 {@code logger.LogInfo(l.ctx, "...", userLogOption(uid))}。 */
    private void logInfo(long userId, String message) {
        logger.logInfo(Context.current(), message, FollowRpcSupport.userLogOptionOf(userId));
    }
}