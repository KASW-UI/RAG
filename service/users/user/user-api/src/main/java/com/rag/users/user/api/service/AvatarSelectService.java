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
            throw e;
        }

        SelectAvatarResp resp = new SelectAvatarResp();
        resp.setAvatarUrl(rpcResp.getAvatarUrl());
        resp.setHistory(support.fromProto(rpcResp.getHistory()));
        return resp;
    }
}
