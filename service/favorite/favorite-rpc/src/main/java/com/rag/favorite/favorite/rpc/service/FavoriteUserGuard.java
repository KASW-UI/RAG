package com.rag.favorite.favorite.rpc.service;

import com.rag.common.logger.LogOption;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.rpc.client.UserRpcClient;
import com.rag.favorite.favorite.rpc.config.FavoriteRpcLabels;
import com.rag.favorite.favorite.rpc.exception.FavoriteExceptions;
import com.rag.favorite.favorite.rpc.metrics.FavoriteRpcMetrics;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.UserInfo;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 🔴 跨域身份校验 —— 对应 Go {@code service/favorite/rpc/internal/logic/helper.go:92-124}
 * 的 {@code ensureUserExists}。
 *
 * <p><b>被 2 个 logic 调用</b>：{@code CreateFavoriteFolderLogic}（:68-73）与
 * {@code CreateFavoriteLogic}（:72-73）。其余 5 个用例<b>不做</b>用户校验 ——
 * 它们依赖「收藏夹归属」间接约束了用户身份（folder.UserId != userId ⇒ PermissionDenied）。
 *
 * <p><b>为什么独立成 Bean</b>：这五条分支各自决定返回哪个 gRPC code 与业务码，
 * 是一条<b>跨域</b>（favorite → user）且<b>可独立测试</b>的规则；
 * 与本域 7 个用例的业务逻辑无关。
 *
 * <h2>五条分支逐条对照（Go 出处行号）</h2>
 * <table border="1">
 *   <caption>ensureUserExists 的五条出口</caption>
 *   <tr><th>条件</th><th>Go 行号</th><th>输出</th></tr>
 *   <tr><td>{@code GetUser} 报错且 {@code st.Code()==NotFound}</td><td>:103-106</td>
 *       <td>{@code NotFound} + 5101</td></tr>
 *   <tr><td>{@code GetUser} 报错（其他 code）</td><td>:107</td>
 *       <td>{@code Internal} + 5001</td></tr>
 *   <tr><td>{@code resp == nil || !resp.Found || resp.User == nil}</td><td>:109-112</td>
 *       <td>{@code NotFound} + 5101</td></tr>
 *   <tr><td>🔴 {@code resp.User.Uid != userID || resp.User.Status == nil}</td><td>:113-116</td>
 *       <td>{@code <b>Unavailable</b>} + 5001</td></tr>
 *   <tr><td>🔴 {@code resp.User.GetStatus() != 0}</td><td>:117-120</td>
 *       <td>{@code PermissionDenied} + 5005</td></tr>
 * </table>
 *
 * <p>🔴 <b>第 4 条刻意用 {@code Unavailable} 而不是 {@code Internal}</b>：
 * 它表达的是「下游返回了我无法解释的响应」⇒ <b>可重试</b>；
 * 客户端按 gRPC code 决定重试策略，改成 Internal 会让重试策略整体改变。
 */
@Component
public class FavoriteUserGuard {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteUserGuard.class);

    private final UserRpcClient userRpcClient;
    private final FavoriteRpcMetrics metrics;

    public FavoriteUserGuard(UserRpcClient userRpcClient, FavoriteRpcMetrics metrics) {
        this.userRpcClient = userRpcClient;
        this.metrics = metrics;
    }

    /**
     * 对应 Go {@code ensureUserExists(ctx, svcCtx, userID)}（helper.go:92-124）。
     *
     * <p>正常返回 = 用户存在且 {@code status == 0}（active）；
     * 任何其它情况都抛 {@link FavoriteExceptions.Rpc}，<b>绝不返回 null</b>
     * —— Go 侧是「返回 error」，Java 侧用异常表达同一语义。
     *
     * @param userId 待校验的用户 ID（调用方已保证 {@code > 0}）
     * @param logOptions 传给业务日志的选项（Go 侧各 logic 的 {@code userLogOption(...)}）
     */
    public void ensureExists(long userId, LogOption... logOptions) {
        GetUserResp resp;
        try {
            // Go: svcCtx.UserRpc.GetUser(depCtx, &userservice.GetUserReq{Uid: userID})  (helper.go:98)
            resp = userRpcClient.userRpc().getUser(GetUserReq.newBuilder().setUid(userId).build());
        } catch (StatusRuntimeException e) {
            // Go: service/favorite/rpc/internal/logic/helper.go:100-107
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                        FavoriteRpcLabels.ACTION_USER_GET, FavoriteRpcLabels.RESULT_FAIL);
                throw FavoriteExceptions.userNotFound();
            }
            metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                    FavoriteRpcLabels.ACTION_USER_GET, FavoriteRpcLabels.RESULT_FAIL);
            LOG.error("user-rpc GetUser failed: user_id={} code={} message={}",
                    userId, e.getStatus().getCode(), e.getStatus().getDescription());
            throw FavoriteExceptions.internalServerCommon();
        }

        // Go: service/favorite/rpc/internal/logic/helper.go:109-112 —— resp == nil || !resp.Found || resp.User == nil
        if (resp == null || !resp.getFound() || !resp.hasUser()) {
            metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                    FavoriteRpcLabels.ACTION_USER_GET, FavoriteRpcLabels.RESULT_FAIL);
            throw FavoriteExceptions.userNotFound();
        }

        UserInfo user = resp.getUser();

        // 🔴 Go: service/favorite/rpc/internal/logic/helper.go:113-116
        //   if resp.User.Uid != userID || resp.User.Status == nil {
        //       return favoritecommon.GRPCError(codes.Unavailable, favoritecommon.ErrorServerCommon)
        //   }
        // 两点都要逐字保留：
        //   ① uid 不匹配 = 下游串了请求/数据，不可解释；
        //   ② Status == nil 是 **presence** 检查 —— users.proto:12 声明的是
        //      `optional int64 status = 6`，proto3 的 optional 允许「字段缺失」与「值为 0」区分开。
        //      ⚠️ Java 侧必须用 hasStatus()，不能用 getStatus() != 0 ——
        //      后者会把「下游没给 status」误判成「用户被封禁」，于是 PermissionDenied/5005，
        //      而 Go 给的是 Unavailable/5001。**方向相反的漂移**，
        //      与 AGENTS.md §0 users 域「Login 的 status IS NULL fail-open」是同型陷阱。
        if (user.getUid() != userId || !user.hasStatus()) {
            metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                    FavoriteRpcLabels.ACTION_USER_GET, FavoriteRpcLabels.RESULT_FAIL);
            LOG.warn("user-rpc returned an unusable identity: user_id={} returned_uid={} has_status={}",
                    userId, user.getUid(), user.hasStatus(), logOptions);
            throw FavoriteExceptions.unavailableServerCommon();
        }

        // Go: service/favorite/rpc/internal/logic/helper.go:117-120 —— resp.User.GetStatus() != 0
        if (user.getStatus() != 0L) {
            metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                    FavoriteRpcLabels.ACTION_USER_GET, FavoriteRpcLabels.RESULT_FAIL);
            LOG.warn("user is not active: user_id={} status={}", userId, user.getStatus(), logOptions);
            throw FavoriteExceptions.forbidden();
        }

        // Go: service/favorite/rpc/internal/logic/helper.go:122 —— metrics.ObserveOp("dependency", "user_get", resultSuccess)
        metrics.observeOp(FavoriteRpcLabels.MODULE_DEPENDENCY,
                FavoriteRpcLabels.ACTION_USER_GET, FavoriteRpcLabels.RESULT_SUCCESS);
    }

    /**
     * 本类用到的业务码清单（供测试断言覆盖度用）：
     * {@link FavoriteErrorCode#ERROR_USER_NOT_FOUND}（5101，两条路径）、
     * {@link FavoriteErrorCode#ERROR_SERVER_COMMON}（5001，两条路径但 gRPC code 不同）、
     * {@link FavoriteErrorCode#ERROR_FORBIDDEN}（5005）。
     */
    static Class<?> coveredCodes() {
        return FavoriteErrorCode.class;
    }
}