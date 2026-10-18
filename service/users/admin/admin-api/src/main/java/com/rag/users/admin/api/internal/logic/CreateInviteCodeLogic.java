package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.metrics.Metrics;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.CreateInviteCodeReq;
import com.rag.users.admin.proto.CreateInviteCodeResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/createinvitelogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（七状态机，逐项冻结，对应 Go createinvitelogic.go 第 34-71 行）：
 * <pre>
 *   1. 取当前已认证 admin 的 uid 作为 operator_id
 *      （Go 用 ctx.Value("userId") 拿 —— go-zero rest.WithJwt 实际写这个 key，所以能拿到；
 *       Java 端用 IdentityResolver.claimedUID(request) 拿 "userId" attribute，等价）
 *      → 类型不是 Long 或 <= 0 → metrics "token_invalid" + ErrorTokenRuntime (1006) + log
 *      （Go 端有 2 处 token_invalid metrics 调用 —— claim 错 + parse 错；Java 端合并为 1 处
 *       IdentityResolver.ErrInvalidClaimException，metrics 1 次 increment 即可）
 *
 *   2. 调 adminRpc.CreateInviteCode(CreateInviteCodeReq{OperatorId})
 *      → 失败：
 *         gRPC codes.NotFound → ErrorUserNotExist  (1003)   // 对应 Go 第 57 行
 *         gRPC codes.Internal → ErrorServerCommon  (5001)   // 对应 Go 第 59 行
 *         其它（默认）         → CodeServerBusy     (1015)   // 对应 Go 第 61 行
 *      → metrics "rpc_error"
 *
 *   3. 成功
 *      → Success (200) + 返回 {invite_code, expires_at}
 *      → metrics "success"
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.invite_code} 是 "ADM-" + 16 字符 hex 字符串（admin-rpc 端 SecureRandom 生成），
 *       {@code data.expires_at} 是 unix 秒（admin-rpc 端 now + 10min 计算）</li>
 *   <li><b>Error</b>：5 个固定业务码（1006/1003/5001/1015/200）</li>
 *   <li><b>Log</b>：token_invalid 时 logger.LogBusinessErr(ErrorTokenRuntime=1006, "ctx userId is not json.Number"/"parse uid failed")；
 *       rpc_error 时 logger.LogBusinessErr</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx；admin-rpc 端会开 "Action-Admin-CreateInviteCode" 子 span</li>
 *   <li><b>Metric</b>：3 个 result label（{@code token_invalid / rpc_error / success}）—— Go 端唯一有 token_invalid 的 RPC（DRIFT-NEW-007）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext} 和 {@link Metrics}，无可变状态；可重入</li>
 * </ul>
 */
@Service
public class CreateInviteCodeLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;
    private final LoggerFactoryBean logger;

    public CreateInviteCodeLogic(ServiceContext svcCtx, Metrics metrics, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *CreateinviteLogic) Createinvite(req *types.CreateInviteReq) (resp *types.CreateInviteResp, code int)}。
     */
    public LogicResult<ApiTypes.CreateInviteResp> createInvite(ApiTypes.CreateInviteReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        // 1. operator_id（DRIFT-NEW-003 的反例：Go 端 ctx.Value("userId") 能拿到，Java 端 claimedUID 等价）
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            // 对应 Go 端第 36-46 行的 2 处 token_invalid metrics + logger.LogBusinessErr
            // —— Java 端合并为 1 处（IdentityResolver 一次抛 ErrInvalidClaimException）
            metrics.getInviteTokenInvalid().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);   // 1006
        }

        // 2. 构造 rpcReq（对应 Go 第 49-51 行 —— 1 字段）
        CreateInviteCodeReq rpcReq = CreateInviteCodeReq.newBuilder()
                .setOperatorId(operatorId)
                .build();

        // 3. 调 adminRpc.CreateInviteCode
        CreateInviteCodeResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().createInviteCode(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: metrics.AdminApiInterceptCount.WithLabelValues("/admin/invite/create", "rpc_error").Inc()
            metrics.getInviteRpcError().increment();
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 56-63 行
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);   // 1003
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);   // 5001
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);       // 1015
            }
        }

        // 4. 成功（对应 Go 第 66 行 metrics "success" + 第 67-70 行构造响应）
        metrics.getInviteSuccess().increment();
        ApiTypes.CreateInviteResp resp = new ApiTypes.CreateInviteResp();
        resp.setInviteCode(rpcResp.getInviteCode());
        resp.setExpiresAt(rpcResp.getExpiresAt());
        return LogicResult.ok(resp);
    }
}