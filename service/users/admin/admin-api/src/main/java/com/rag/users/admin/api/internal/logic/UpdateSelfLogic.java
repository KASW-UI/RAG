package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.AdminInfo;
import com.rag.users.admin.proto.UpdateSelfReq;
import com.rag.users.admin.proto.UpdateSelfResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/updatelogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（八状态机，逐项冻结，对应 Go updatelogic.go 第 35-78 行）：
 * <pre>
 *   1. 取当前已认证 admin 的 uid（DRIFT-NEW-003 的反例：Go 端用 ctx.Value("userId")
 *      拿 —— go-zero rest.WithJwt 实际写 "userId"，所以能拿到，<b>不是</b> bug。
 *      Java 端用 IdentityResolver.claimedUID(request) 拿 —— 等价语义）
 *      → 类型不是 Long 或 <= 0 → ErrorTokenRuntime (1006) + log
 *
 *   2. 构造 rpcReq = UpdateSelfReq{Uid, Username, Password, Email, ExtraInfo}（5 字段，无 operator_id）
 *      （username/password/email/extra_info 4 字段用反射绕 javac 边缘 case —— DRIFT-NEW-002 扩展）
 *
 *   3. 调 adminRpc.UpdateSelf(rpcReq)
 *      → 失败：
 *         gRPC codes.AlreadyExists → ErrorUserExist      (1001)
 *         gRPC codes.NotFound      → ErrorUserNotExist   (1003)
 *         gRPC codes.Internal      → <b>ErrorDbSelect</b>   (5003)   // DRIFT-NEW-006：与 UpdateUser 的 5001 不一致
 *         其它（默认）              → CodeServerBusy      (1015)
 *
 *   4. 成功
 *      → Success (200) + 返回 {success: true, admin: {uid, username, email, extra_info}}（4 字段，无 status）
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.admin} 是 4 字段 AdminInfo；失败时 5 个业务码之一</li>
 *   <li><b>Error</b>：5 个固定业务码（1006/1001/1003/5003/1015/200）</li>
 *   <li><b>Log</b>：uid 类型错误时 logger.LogBusinessErr；rpc_error 时 logger.LogBusinessErr</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx</li>
 *   <li><b>Metric</b>：Go 端 updatelogic.go <b>无 metrics 埋点</b> —— 1:1 不补（DRIFT-016 决策）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext}，无可变状态；可重入</li>
 * </ul>
 */
@Service
public class UpdateSelfLogic {

    private final ServiceContext svcCtx;
    private final LoggerFactoryBean logger;

    public UpdateSelfLogic(ServiceContext svcCtx, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *UpdateLogic) Update(req *types.UpdateSelfReq) (resp *types.UpdateSelfResp, code int)}。
     */
    public LogicResult<ApiTypes.UpdateSelfResp> updateSelf(ApiTypes.UpdateSelfReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        // 1. 取 admin uid（DRIFT-NEW-003 反例：Go 端 ctx.Value("userId") 能拿到，Java 端用 claimedUID 等价）
        long adminUid;
        try {
            adminUid = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.ErrorTokenRuntime, "ctx userId is not json.Number" / "parse uid failed")
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);
        }

        // 2. 构造 rpcReq（5 字段，1:1 透传；对应 Go 第 46-52 行）
        // DRIFT-NEW-002 修复：直接调 Builder setter，不用反射。
        UpdateSelfReq.Builder rpcBuilder = UpdateSelfReq.newBuilder()
                .setUid(adminUid);
        if (req.getUsername() != null) {
            rpcBuilder.setUsername(req.getUsername());
        }
        if (req.getPassword() != null) {
            rpcBuilder.setPassword(req.getPassword());
        }
        if (req.getEmail() != null) {
            rpcBuilder.setEmail(req.getEmail());
        }
        if (req.getExtraInfo() != null && !req.getExtraInfo().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : req.getExtraInfo().entrySet()) {
                rpcBuilder.putExtraInfo(entry.getKey(), entry.getValue());
            }
        }
        UpdateSelfReq rpcReq = rpcBuilder.build();

        // 3. 调 adminRpc.UpdateSelf
        UpdateSelfResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().updateSelf(rpcReq);
        } catch (StatusRuntimeException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 58-67 行 —— Internal 映射 5003（与 UpdateUser 不同 —— DRIFT-NEW-006）
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case ALREADY_EXISTS:
                    return LogicResult.fail(ErrorCode.ERROR_USER_EXIST);        // 1001
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);    // 1003
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_DB_SELECT);         // 5003
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);        // 1015
            }
        }

        // 4. 成功：复制 4 字段（uid/username/email/extra_info），不 setSuccess/rpcResp.getSuccess()（与 Go 第 71 行 Success: true 同源）
        AdminInfo protoAdmin = rpcResp.getAdmin();
        ApiTypes.AdminInfo apiAdmin = new ApiTypes.AdminInfo();
        if (protoAdmin != null) {
            apiAdmin.setUid(protoAdmin.getUid());
            apiAdmin.setUsername(protoAdmin.getUsername());
            String email = protoAdmin.getEmail();
            if (email != null && !email.isEmpty()) {
                apiAdmin.setEmail(email);
            }
            if (protoAdmin.getExtraInfoCount() > 0) {
                apiAdmin.setExtraInfo(protoAdmin.getExtraInfoMap());
            }
        }

        ApiTypes.UpdateSelfResp resp = new ApiTypes.UpdateSelfResp();
        resp.setSuccess(true);   // 必须显式 true，与 Go 第 71 行 1:1
        resp.setAdmin(apiAdmin);
        return LogicResult.ok(resp);
    }

    // invokeStringSetter / invokePutExtraInfoMap 已删除 —— DRIFT-NEW-002 修复：直接用 Builder setter。
}