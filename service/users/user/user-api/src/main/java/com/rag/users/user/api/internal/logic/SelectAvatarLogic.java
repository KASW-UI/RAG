package com.rag.users.user.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.internal.identity.IdentityResolver;
import com.rag.users.user.api.internal.svc.ServiceContext;
import com.rag.users.user.api.internal.types.ApiTypes;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.SelectAvatarResp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/user/selectavatarlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（5 状态机，逐项冻结）：
 * <pre>
 *   1. AvatarHelpers.currentUserID(request) 取 uid
 *      → 失败 → ErrorTokenRuntime (1006)
 *
 *   2. history_id 空字符串检查（strings.TrimSpace(req.HistoryId) == ""）
 *      → Error (500)
 *
 *   3. 调 userRpc.SelectAvatar(SelectAvatarReq{Uid, HistoryId})
 *      → 失败：
 *         codes.InvalidArgument → Error           (500)
 *         codes.NotFound        → Error           (500)   ← 注意：这里是 Error 不是 ErrorUserNotExist
 *         codes.Internal        → ErrorServerCommon (5001)
 *         default               → CodeServerBusy   (1015)
 *
 *   4. 成功
 *      → Success (200) + 返回 {avatarUrl, history}
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.avatarUrl} + {@code data.history}</li>
 *   <li><b>Metric</b>：Go 端 SelectAvatarLogic <b>无 metrics 埋点</b> —— 1:1 不补</li>
 * </ul>
 */
@Service
public class SelectAvatarLogic {

    private final ServiceContext svcCtx;
    private final AvatarHelpers avatarHelpers;
    private final LoggerFactoryBean logger;

    public SelectAvatarLogic(ServiceContext svcCtx, AvatarHelpers avatarHelpers, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.avatarHelpers = avatarHelpers;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *SelectavatarLogic) Selectavatar(req *types.SelectAvatarReq) (resp *types.SelectAvatarResp, code int)}。
     */
    public LogicResult<ApiTypes.SelectAvatarResp> selectAvatar(ApiTypes.SelectAvatarReq req,
                                                                HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        long uid;
        try {
            uid = avatarHelpers.currentUserID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);
        }

        // Go: strings.TrimSpace(req.HistoryId) == "" 检查
        String historyId = req.getHistoryId() == null ? "" : req.getHistoryId().trim();
        if (historyId.isEmpty()) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(),
                    new RuntimeException("history_id is empty"));
            return LogicResult.fail(ErrorCode.ERROR);
        }

        SelectAvatarReq rpcReq = SelectAvatarReq.newBuilder()
                .setUid(uid)
                .setHistoryId(historyId)
                .build();

        SelectAvatarResp rpcResp;
        try {
            rpcResp = svcCtx.getUserRpc().selectAvatar(rpcReq);
        } catch (StatusRuntimeException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.INVALID_ARGUMENT) {
                return LogicResult.fail(ErrorCode.ERROR);
            }
            if (code == Status.Code.NOT_FOUND) {
                // Go 端这里也是 Error (500)，不是 ErrorUserNotExist (1003)
                return LogicResult.fail(ErrorCode.ERROR);
            }
            if (code == Status.Code.INTERNAL) {
                return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);
            }
            return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);
        }

        ApiTypes.SelectAvatarResp resp = new ApiTypes.SelectAvatarResp();
        resp.setAvatarUrl(rpcResp.getAvatarUrl());
        resp.setHistory(avatarHelpers.avatarHistoryItemFromPB(rpcResp.getHistory()));
        return LogicResult.ok(resp);
    }
}