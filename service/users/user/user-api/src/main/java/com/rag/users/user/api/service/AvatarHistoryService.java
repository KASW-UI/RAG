package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.AvatarHistoryItem;
import com.rag.users.user.api.dto.GetAvatarHistoryResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * getAvatarHistory 用例 — claim uid + 4 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/getavatarhistorylogic.go 的 GetAvatarHistoryLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（4 状态机，1:1 复刻）：
 * <pre>
 *   1. IdentityResolver.claimedUID(request) → uid
 *      → 失败 → ErrorTokenRuntime (1006)
 *
 *   2. 调 userRpc.getAvatarHistory(pb.GetAvatarHistoryReq{Uid, Limit: 30})
 *
 * <p><b>⚠️ 2026-10-23（DRIFT-USERAPI-005）：本方法<b>不接收任何入参 DTO</b>，这是刻意为之。</b>
 * 原签名带一个 {@code GetAvatarHistoryReq req} 参数，但它<b>在方法体里一次都没被用过</b>
 * （uid 走 JWT、limit 走常量），而 Controller 上挂着它就等于让 Spring 去解析 body ——
 * Go 侧 {@code getavatarhistoryhandler.go} 全文 26 行<b>没有 httpx.Parse</b>，根本不读 body。
 * ⇒ 连同 DTO 一起删除。删除前已 grep 全仓确认零消费者。
 * 理由详见 {@code UserController#getAvatarHistory} 的 javadoc。
 *      注：Limit = 30 是硬编码（与 Go 端一致，DRIFT-016 决策：1:1 不配置化）
 *
 *   3. 失败：
 *         codes.NotFound  → ErrorUserNotExist (1003)
 *         codes.Internal  → ErrorServerCommon (5001)
 *         default         → CodeServerBusy    (1015)
 *
 *   4. 成功
 *      → Success + 返回 {list}（每项由 UserApiSupport.fromProto 转换）
 * </pre>
 *
 * <p>Go 端 GetAvatarHistoryLogic 不写 metrics —— 1:1 不补。
 */
@Service
public class AvatarHistoryService {

    private static final Logger log = LoggerFactory.getLogger(AvatarHistoryService.class);

    /** 与 Go 端硬编码一致：Limit = 30（DRIFT-016 决策，1:1 不改）。 */
    private static final int LIMIT = 30;

    private final UserRpcClient userRpcClient;
    private final UserApiSupport support;

    public AvatarHistoryService(UserRpcClient userRpcClient, UserApiSupport support) {
        this.userRpcClient = userRpcClient;
        this.support = support;
    }

    public GetAvatarHistoryResp getAvatarHistory(HttpServletRequest httpRequest) {
        long uid;
        try {
            uid = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            log.error("getAvatarHistory ErrInvalidClaim", e);
            throw new BusinessException(ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), ErrorCode.ERROR_TOKEN_RUNTIME.getMsg());
        }

        com.rag.users.user.proto.GetAvatarHistoryReq rpcReq = com.rag.users.user.proto.GetAvatarHistoryReq.newBuilder()
                .setUid(uid)
                .setLimit(LIMIT)
                .build();

        com.rag.users.user.proto.GetAvatarHistoryResp rpcResp;
        try {
            rpcResp = userRpcClient.userRpc().getAvatarHistory(rpcReq);
        } catch (StatusRuntimeException e) {
            log.error("getAvatarHistory RPC error", e);
            throw e;
        }

        List<AvatarHistoryItem> list = new ArrayList<>(rpcResp.getListCount());
        for (com.rag.users.user.proto.AvatarHistoryItem item : rpcResp.getListList()) {
            list.add(support.fromProto(item));
        }

        GetAvatarHistoryResp resp = new GetAvatarHistoryResp();
        resp.setList(list);
        return resp;
    }
}
