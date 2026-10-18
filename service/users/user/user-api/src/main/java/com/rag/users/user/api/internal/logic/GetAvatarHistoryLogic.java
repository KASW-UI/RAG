package com.rag.users.user.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.internal.identity.IdentityResolver;
import com.rag.users.user.api.internal.svc.ServiceContext;
import com.rag.users.user.api.internal.types.ApiTypes;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对应 Go 端 internal/logic/user/getavatarhistorylogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（4 状态机，逐项冻结）：
 * <pre>
 *   1. AvatarHelpers.currentUserID(request) 取 uid
 *      → 失败 → ErrorTokenRuntime (1006)
 *
 *   2. 调 userRpc.GetAvatarHistory(GetAvatarHistoryReq{Uid, Limit: 30})
 *      注：Limit = 30 是 <b>硬编码</b>（与 Go 端一致，DRIFT-016 决策：1:1 不配置化）
 *
 *   3. 失败：
 *         codes.NotFound  → ErrorUserNotExist (1003)
 *         codes.Internal  → ErrorServerCommon (5001)
 *         default         → CodeServerBusy    (1015)
 *
 *   4. 成功
 *      → Success (200) + 返回 {list}（每项由 AvatarHelpers.avatarHistoryItemFromPB 转换）
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.list[]} 是 AvatarHistoryItem DTO 列表</li>
 *   <li><b>Metric</b>：Go 端 GetAvatarHistoryLogic <b>无 metrics 埋点</b> —— 1:1 不补</li>
 * </ul>
 */
@Service
public class GetAvatarHistoryLogic {

    /** 与 Go 端硬编码一致：Limit = 30（DRIFT-016 决策，1:1 不改）。 */
    private static final int LIMIT = 30;

    private final ServiceContext svcCtx;
    private final AvatarHelpers avatarHelpers;
    private final LoggerFactoryBean logger;

    public GetAvatarHistoryLogic(ServiceContext svcCtx, AvatarHelpers avatarHelpers, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.avatarHelpers = avatarHelpers;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *GetavatarhistoryLogic) Getavatarhistory() (resp *types.AvatarHistoryResp, code int)}。
     */
    public LogicResult<ApiTypes.AvatarHistoryResp> getAvatarHistory(HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        long uid;
        try {
            uid = avatarHelpers.currentUserID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);
        }

        GetAvatarHistoryReq rpcReq = GetAvatarHistoryReq.newBuilder()
                .setUid(uid)
                .setLimit(LIMIT)
                .build();

        com.rag.users.user.proto.GetAvatarHistoryResp rpcResp;
        try {
            rpcResp = svcCtx.getUserRpc().getAvatarHistory(rpcReq);
        } catch (StatusRuntimeException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);
            }
            if (code == Status.Code.INTERNAL) {
                return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);
            }
            return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);
        }

        List<ApiTypes.AvatarHistoryItem> list = new ArrayList<>(rpcResp.getListCount());
        for (com.rag.users.user.proto.AvatarHistoryItem item : rpcResp.getListList()) {
            list.add(avatarHelpers.avatarHistoryItemFromPB(item));
        }

        ApiTypes.AvatarHistoryResp resp = new ApiTypes.AvatarHistoryResp();
        resp.setList(list);
        return LogicResult.ok(resp);
    }
}