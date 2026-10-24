package com.rag.users.user.api.service;

import com.rag.common.exception.BusinessException;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.LogoutReq;
import com.rag.users.user.api.dto.LogoutResp;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * logout 用例 — HTTP DTO ↔ gRPC proto + 4 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/logoutlogic.go 的 LogoutLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（4 状态机，1:1 复刻）：
 * <pre>
 *   1. 从 request attribute "jwt_token" 取 token（JwtAuthInterceptor 在 blacklist 校验后写入）
 *      → 取不到 → ErrorTokenNotExist (1004) + 日志 "Token不存在"
 *      （对应 Go 端 errmsg.ErrorTokenNotExist=1004；
 *        注意：errmsg.ErrorTokenTypeWrong=1005 是 user-rpc LogoutLogic 用的，不是 user-api）
 *
 *   2. 调 userRpc.logout(LogoutReq{Token})
 *      → gRPC codes.Internal → ErrorServerCommon (5001)
 *      → 其它（默认）         → CodeServerBusy   (1015)
 *
 *   3. 成功
 *      → Success + 返回 {success: true}
 * </pre>
 *
 * <p>Go 端 LogoutLogic 不写 metrics —— 1:1 不补。
 *
 * <p><b>🔴 DRIFT-USERAPI-003 已修（2026-10-23）</b>：本类原先只做
 * {@code catch (StatusRuntimeException e) { throw e; }}，把映射甩给
 * {@code GlobalExceptionHandler.mapRPCStatusToCode}。而那个通用映射里
 * {@code INVALID_ARGUMENT → 500}，Go 侧却没有这层 ——
 * <pre>
 *   Go 出处 service/user/user/api/internal/logic/user/logoutlogic.go:45-54
 *     if err != nil {
 *       logger.LogBusinessErr(l.ctx, errmsg.Error, err)
 *       st, _ := status.FromError(err)
 *       switch st.Code() {
 *       case codes.Internal:  return nil, errmsg.ErrorServerCommon   // 5001
 *       default:              return nil, errmsg.CodeServerBusy      // 1015
 *       }
 *     }
 * </pre>
 * ⇒ Go 的 logout <b>只区分 Internal 与「其它一律 1015」</b>，
 * <b>没有任何一条分支把 INVALID_ARGUMENT 变成 500</b>。
 * <p><b>这条分支是真实可达的，不是理论风险</b>：user-rpc 的
 * {@code UserDomainService.java:768} 在 token 为空时抛
 * {@code Status.INVALID_ARGUMENT "Token不能为空"}（Go 侧对应
 * {@code service/user/user/rpc/internal/logic/logoutlogic.go:33}），
 * 而空 token 在 API 层是可达输入。
 * <p>⇒ 修前实测语义差：空 token 注销，Go 返回 {@code 1015 服务繁忙}，RAG 返回 {@code 500 系统内部错误}。
 * <p>⚠️ 本类 javadoc 早就把 Go 的两条映射写对了（见上），<b>只是代码没照做</b> ——
 * 这正是 §3.2「注释不能替代实现」的实例。
 */
@Service
public class UserLogoutService {

    private static final Logger log = LoggerFactory.getLogger(UserLogoutService.class);

    private final UserRpcClient userRpcClient;

    public UserLogoutService(UserRpcClient userRpcClient) {
        this.userRpcClient = userRpcClient;
    }

    public LogoutResp logout(LogoutReq req, HttpServletRequest httpRequest) {
        // 对应 Go: l.ctx.Value("jwt_token")
        Object tokenAttr = httpRequest.getAttribute("jwt_token");
        if (tokenAttr == null) {
            log.error("Token不存在");
            throw new BusinessException(ErrorCode.ERROR_TOKEN_NOT_EXIST.getCode(), ErrorCode.ERROR_TOKEN_NOT_EXIST.getMsg());
        }
        String token = (String) tokenAttr;

        com.rag.users.user.proto.LogoutReq rpcReq = com.rag.users.user.proto.LogoutReq.newBuilder()
                .setToken(token).build();
        try {
            userRpcClient.userRpc().logout(rpcReq);
        } catch (StatusRuntimeException e) {
            log.error("logout RPC error", e);
            // Go logoutlogic.go:45-54：只区分 Internal（5001）与「其它一律 1015」。
            // ⚠️ 刻意**不**复用 GlobalExceptionHandler.mapRPCStatusToCode —— 那个通用映射
            // 会把 INVALID_ARGUMENT 映成 500，而 Go 的 logout switch 里根本没有这一格。
            // 与 AvatarSelectService:98-99 同一修法（根因同形：通用映射覆盖 per-Service switch）。
            if (e.getStatus().getCode() == io.grpc.Status.Code.INTERNAL) {
                throw new BusinessException(
                        ErrorCode.ERROR_SERVER_COMMON.getCode(), ErrorCode.ERROR_SERVER_COMMON.getMsg());
            }
            throw new BusinessException(
                    ErrorCode.CODE_SERVER_BUSY.getCode(), ErrorCode.CODE_SERVER_BUSY.getMsg());
        }

        LogoutResp resp = new LogoutResp();
        resp.setSuccess(true);
        return resp;
    }
}
