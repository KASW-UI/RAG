package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.SelectAvatarReq;
import com.rag.users.user.api.dto.SelectAvatarResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * selectAvatar 用例 — claim uid + 历史 id trim 检查 + 5 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/selectavatarlogic.go 的 SelectAvatarLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（5 状态机，1:1 复刻）：
 * <pre>
 *   1. IdentityResolver.claimedUID(request) → uid
 *      → 失败 → ErrorTokenRuntime (1006)
 *
 *   2. history_id 空字符串检查（TrimSpace == ""）
 *      → Error (500)
 *
 *   3. 调 userRpc.selectAvatar(SelectAvatarReq{Uid, HistoryId})
 *      → 失败：
 *         codes.InvalidArgument → Error           (500)
 *         codes.NotFound        → Error           (500)   ← 注意：这里是 Error 不是 ErrorUserNotExist
 *         codes.Internal        → ErrorServerCommon (5001)
 *         default               → CodeServerBusy   (1015)
 *
 *   4. 成功
 *      → Success + 返回 {avatarUrl, history}
 * </pre>
 *
 * <p>Go 端 SelectAvatarLogic 不写 metrics —— 1:1 不补。
 */
@Service
public class AvatarSelectService {

    private static final Logger log = LoggerFactory.getLogger(AvatarSelectService.class);

    private final UserRpcClient userRpcClient;
    private final UserApiSupport support;

    public AvatarSelectService(UserRpcClient userRpcClient, UserApiSupport support) {
        this.userRpcClient = userRpcClient;
        this.support = support;
    }

    public SelectAvatarResp selectAvatar(SelectAvatarReq req, HttpServletRequest httpRequest) {
        long uid;
        try {
            uid = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("selectAvatar ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        // Go: strings.TrimSpace(req.HistoryId) == "" 检查
        String historyId = support.normalizeHistoryId(req.getHistoryId());
        if (historyId.isEmpty()) {
            log.error("history_id is empty");
            throw new BusinessException(ErrorCode.ERROR.getCode(), ErrorCode.ERROR.getMsg());
        }

        com.rag.users.user.proto.SelectAvatarReq rpcReq = com.rag.users.user.proto.SelectAvatarReq.newBuilder()
                .setUid(uid)
                .setHistoryId(historyId)
                .build();

        com.rag.users.user.proto.SelectAvatarResp rpcResp;
        try {
            rpcResp = userRpcClient.userRpc().selectAvatar(rpcReq);
        } catch (StatusRuntimeException e) {
            log.error("selectAvatar RPC error", e);
            // DRIFT-003 修复：不能直接 rethrow。
            // rethrow 会落到 GlobalExceptionHandler.mapRPCStatusToCode 的**通用**映射
            //   NOT_FOUND        → 1003（用户不存在）  ← 与 Go 不符
            //   INVALID_ARGUMENT →  500
            // 而 Go 出处 api/internal/logic/user/selectavatarlogic.go:54-61 的 switch 是：
            //   case codes.InvalidArgument: return nil, errmsg.Error             // 500
            //   case codes.NotFound:        return nil, errmsg.Error             // 500
            //   case codes.Internal:        return nil, errmsg.ErrorServerCommon // 5001
            //   default:                    return nil, errmsg.CodeServerBusy    // 1015
            // NOT_FOUND 的差异不是"看起来更合理"的改写，而是**抹掉了 Go 刻意保留的区分**：
            // 「头像历史不存在」(500) 与「用户不存在」(1003) 被合并后，
            // 客户端可以据此枚举某个 uid 是否存在。2026-10-21 审计前，
            // 本类自己的 javadoc 已经写对了（"codes.NotFound → Error (500) ← 注意：这里是
            // Error 不是 ErrorUserNotExist"），代码没照做。
            //
            // INVALID_ARGUMENT 经通用映射也落到 500、与 Go 一致，这里仍显式写出：
            // 让 Go 的整个 switch 在代码里可见，不再依赖通用映射永远不被改动 ——
            // 这次的缺陷本身就是"通用映射悄悄覆盖了 per-Service switch"造成的。
            io.grpc.Status.Code code = e.getStatus().getCode();
            if (code == io.grpc.Status.Code.NOT_FOUND
                    || code == io.grpc.Status.Code.INVALID_ARGUMENT) {
                throw new BusinessException(ErrorCode.ERROR.getCode(), ErrorCode.ERROR.getMsg());
            }
            throw e;
        }

        SelectAvatarResp resp = new SelectAvatarResp();
        resp.setAvatarUrl(rpcResp.getAvatarUrl());
        resp.setHistory(support.fromProto(rpcResp.getHistory()));
        return resp;
    }
}
