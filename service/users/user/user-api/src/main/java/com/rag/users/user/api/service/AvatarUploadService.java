package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.UploadAvatarReq;
import com.rag.users.user.api.dto.UploadAvatarResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * uploadAvatar 用例 — claim uid + avatar_url trim 检查 + 5 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/uploadavatarlogic.go 的 UploadAvatarLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（5 状态机，1:1 复刻）：
 * <pre>
 *   1. IdentityResolver.claimedUID(request) → uid
 *      → 失败 → ErrorTokenRuntime (1006)
 *
 *   2. avatar_url 空字符串检查（TrimSpace == ""）
 *      → Error (500)
 *
 *   3. avatar_url trim 后传给 RPC（与 Go 一致）
 *
 *   4. 调 userRpc.uploadAvatar(UploadAvatarReq{Uid, AvatarUrl, ContentType?, SizeBytes?})
 *      → 失败：
 *         codes.InvalidArgument → Error           (500)
 *         codes.NotFound        → ErrorUserNotExist (1003)
 *         codes.Internal        → ErrorServerCommon (5001)
 *         default               → CodeServerBusy   (1015)
 *
 *   5. 成功
 *      → Success + 返回 {avatarUrl, history}
 * </pre>
 *
 * <p>Go 端 UploadAvatarLogic 不写 metrics —— 1:1 不补。
 */
@Service
public class AvatarUploadService {

    private static final Logger log = LoggerFactory.getLogger(AvatarUploadService.class);

    private final UserRpcClient userRpcClient;
    private final UserApiSupport support;

    public AvatarUploadService(UserRpcClient userRpcClient, UserApiSupport support) {
        this.userRpcClient = userRpcClient;
        this.support = support;
    }

    public UploadAvatarResp uploadAvatar(UploadAvatarReq req, HttpServletRequest httpRequest) {
        long uid;
        try {
            uid = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("uploadAvatar ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        // Go: strings.TrimSpace(req.AvatarUrl) == "" 检查
        String avatarUrl = support.normalizeAvatarUrl(req.getAvatarUrl());
        if (avatarUrl.isEmpty()) {
            log.error("avatar_url is empty");
            throw new BusinessException(ErrorCode.ERROR.getCode(), ErrorCode.ERROR.getMsg());
        }

        com.rag.users.user.proto.UploadAvatarReq.Builder builder = com.rag.users.user.proto.UploadAvatarReq.newBuilder()
                .setUid(uid)
                .setAvatarUrl(avatarUrl);
        if (req.getContentType() != null) builder.setContentType(req.getContentType());
        if (req.getSizeBytes() != null) builder.setSizeBytes(req.getSizeBytes());

        com.rag.users.user.proto.UploadAvatarResp rpcResp;
        try {
            rpcResp = userRpcClient.userRpc().uploadAvatar(builder.build());
        } catch (StatusRuntimeException e) {
            log.error("uploadAvatar RPC error", e);
            throw e;
        }

        UploadAvatarResp resp = new UploadAvatarResp();
        resp.setAvatarUrl(rpcResp.getAvatarUrl());
        resp.setHistory(support.fromProto(rpcResp.getHistory()));
        return resp;
    }
}
