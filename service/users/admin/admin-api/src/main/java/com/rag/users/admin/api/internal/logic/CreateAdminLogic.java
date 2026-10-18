package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.CreateAdminReq;
import com.rag.users.admin.proto.CreateAdminResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/createlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（五状态机，逐项冻结，对应 Go createlogic.go 第 34-60 行）：
 * <pre>
 *   1. 调 adminRpc.CreateAdmin(CreateAdminReq{Username, Password, Email, InviteCode, ExtraInfo})
 *      → 失败：
 *         gRPC codes.AlreadyExists    → ErrorUserExist              (1001)  // 对应 Go 第 48 行
 *         gRPC codes.PermissionDenied → ErrorAdminInviteCodeWrong   (1016)  // 对应 Go 第 50 行
 *         gRPC codes.Internal         → ErrorServerCommon           (5001)  // 对应 Go 第 52 行
 *         其它（默认）                 → CodeServerBusy              (1015)  // 对应 Go 第 54 行
 *
 *   2. 成功
 *      → Success (200) + 返回 {uid: "..."}（uid 序列化为字符串，对应 Go json:"uid,string"）
 * </pre>
 *
 * <p><b>关键差异（与 Login / GetSelf / Logout 不同）</b>：
 * <ul>
 *   <li>本 RPC 是 <b>public</b>（routes.go:18-22 第一组 AddRoutes，不带 WithJwt） —— 不依赖 JWT 验签；
 *       admin-rpc 端 CreateAdminLogic 内部用 invite_code 校验调用方权限</li>
 *   <li>引入两个新 gRPC code 映射：{@code AlreadyExists} 和 {@code PermissionDenied} —— 前 3 个 RPC 都没用过</li>
 *   <li>不需要 {@link com.rag.users.admin.api.internal.identity.IdentityResolver}（无 token 取 uid）</li>
 * </ul>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.uid} 是字符串形式 UID（json:"uid,string"）；失败时 5 个业务码之一</li>
 *   <li><b>Error</b>：5 个固定业务码（1001/1016/5001/1015/200）</li>
 *   <li><b>Log</b>：rpc_error 时 logger.LogBusinessErr；success 不打 log（与 Go 一致）</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx；admin-rpc 端会开 "Action-Admin-CreateAdmin" 子 span</li>
 *   <li><b>Metric</b>：Go 端 createlogic.go <b>无 metrics 埋点</b> —— 1:1 不补（DRIFT-016 决策）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext}，无可变状态；可重入</li>
 * </ul>
 */
@Service
public class CreateAdminLogic {

    private final ServiceContext svcCtx;
    private final LoggerFactoryBean logger;

    public CreateAdminLogic(ServiceContext svcCtx, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *CreateLogic) Create(req *types.CreateAdminReq) (resp *types.CreateAdminResp, code int)}。
     */
    public LogicResult<ApiTypes.CreateAdminResp> createAdmin(ApiTypes.CreateAdminReq req,
                                                              HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        // DRIFT-NEW-002 修复：之前用反射调 putExtraInfo 但 target 用了已 .build() 的 rpcReq，
        // 导致运行时 IllegalArgumentException: object is not an instance of declaring class。
        // 直接调用 Builder 上的 putExtraInfo 与 setXxx 即可 — javac + admin-proto classpath 下都可解析。
        // 先保留 Builder 实例，最后再 .build()。
        CreateAdminReq.Builder rpcBuilder = CreateAdminReq.newBuilder()
                .setUsername(req.getUsername() == null ? "" : req.getUsername())
                .setPassword(req.getPassword() == null ? "" : req.getPassword())
                .setEmail(req.getEmail() == null ? "" : req.getEmail())
                .setInviteCode(req.getInviteCode() == null ? "" : req.getInviteCode());
        if (req.getExtraInfo() != null && !req.getExtraInfo().isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : req.getExtraInfo().entrySet()) {
                rpcBuilder.putExtraInfo(entry.getKey(), entry.getValue());
            }
        }
        CreateAdminReq rpcReq = rpcBuilder.build();

        CreateAdminResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().createAdmin(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.Error, err)
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 47-56 行 —— 5 个分支（含 AlreadyExists / PermissionDenied）
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case ALREADY_EXISTS:
                    return LogicResult.fail(ErrorCode.ERROR_USER_EXIST);                    // 1001
                case PERMISSION_DENIED:
                    return LogicResult.fail(ErrorCode.ERROR_ADMIN_INVITE_CODE_WRONG);     // 1016
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);                // 5001
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);                    // 1015
            }
        }

        // 成功：uid 由 admin-rpc 端 Snowflake 生成（对应 Go 第 58-60 行）
        ApiTypes.CreateAdminResp resp = new ApiTypes.CreateAdminResp();
        resp.setUid(rpcResp.getUid());
        return LogicResult.ok(resp);
    }
}