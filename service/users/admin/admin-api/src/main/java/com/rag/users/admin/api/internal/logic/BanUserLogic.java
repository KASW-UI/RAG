package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.metrics.Metrics;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.BanUserReq;
import com.rag.users.admin.proto.BanUserResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/banuserlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（七状态机，逐项冻结，对应 Go banuserlogic.go 第 35-62 行）：
 * <pre>
 *   1. 取当前已认证 admin 的 uid 作为 operator_id
 *      （Go 用 ctx.Value("uid") 拿 —— <b>DRIFT-NEW-003</b>：go-zero rest.WithJwt 实际只写
 *       "userId" 不写 "uid"，所以 Go 端拿不到，会传 operator_id=0；
 *       Java 端用 IdentityResolver.claimedUID(request) 拿 "userId" attribute，传真实 operator_id）
 *
 *   2. 调 adminRpc.BanUser(BanUserReq{Uid, OperatorId})
 *      → 失败：
 *         gRPC codes.NotFound → ErrorUserNotExist  (1003)   // 对应 Go 第 51 行
 *         gRPC codes.Internal → ErrorServerCommon  (5001)   // 对应 Go 第 53 行
 *         其它（默认）         → CodeServerBusy     (1015)   // 对应 Go 第 55 行
 *      → rpc_error 时 metrics "rpc_error"
 *
 *   3. 成功
 *      → Success (200) + 返回 {success: rpcResp.Success}
 *      → metrics "success"
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.success} 是 admin-rpc 返回的 boolean；失败时 5 个业务码之一</li>
 *   <li><b>Error</b>：5 个固定业务码（1003/5001/1015 + 1006 兜底 / 200）</li>
 *   <li><b>Log</b>：rpc_error 时 logger.LogBusinessErr；success 不打 log（与 Go 一致）</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx；admin-rpc 端会开 "Action-Admin-BanUser" 子 span</li>
 *   <li><b>Metric</b>：2 个 result label 完整保留（{@code rpc_error / success}）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext} 和 {@link Metrics}，无可变状态；可重入</li>
 * </ul>
 */
@Service
public class BanUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;
    private final LoggerFactoryBean logger;

    public BanUserLogic(ServiceContext svcCtx, Metrics metrics, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *BanuserLogic) Banuser(req *types.BanUserReq) (resp *types.BanUserResp, code int)}。
     */
    public LogicResult<ApiTypes.BanUserResp> banUser(ApiTypes.BanUserReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        // 1. operator_id
        // Go 端用 ctx.Value("uid") 拿 —— 但 go-zero rest.WithJwt 实际只写 "userId" 不写 "uid"
        // （DRIFT-NEW-003），所以 Go 端拿不到，会传 operator_id=0
        // Java 端从 "userId" attribute 取（IdentityResolver.claimedUID），传真实 operator_id
        long operatorId;
        try {
            operatorId = IdentityResolver.claimedUID(httpRequest);
        } catch (IdentityResolver.ErrInvalidClaimException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_TOKEN_RUNTIME.getCode(), e);
            return LogicResult.fail(ErrorCode.ERROR_TOKEN_RUNTIME);
        }

        // 2. 构造 rpcReq（对应 Go 第 41-44 行）
        // target uid：ApiTypes.BanUserReq.uid 是 Long，getUid() 返回 String；rpcReq.Uid 是 int64
        Long targetUid = req.getUid() == null ? null : parseLongOrNull(req.getUid());
        if (targetUid == null) {
            return LogicResult.fail(ErrorCode.ERROR_REQUEST_PARAM);
        }
        BanUserReq rpcReq = BanUserReq.newBuilder()
                .setUid(targetUid)
                .setOperatorId(operatorId)
                .build();

        // 3. 调 adminRpc.BanUser
        BanUserResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().banUser(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: metrics.AdminApiInterceptCount.WithLabelValues("/admin/banuser", "rpc_error").Inc()
            metrics.getBanRpcError().increment();
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.Error, err)
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 50-57 行
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

        // 4. 成功（对应 Go 第 59 行 metrics "success" + 第 60-62 行构造响应）
        metrics.getBanSuccess().increment();
        ApiTypes.BanUserResp resp = new ApiTypes.BanUserResp();
        resp.setSuccess(rpcResp.getSuccess());
        return LogicResult.ok(resp);
    }

    /**
     * 解析 String 形式的 uid（json:"uid,string" 反序列化结果）为 long。
     * 对应 Go 端 admin.proto Line 14 {@code Uid int64 `json:"uid,string"`} —— 数字以字符串形式传入。
     * 失败时返回 null（让 Logic 报 ErrorRequestParam，1017）。
     */
    private static Long parseLongOrNull(String s) {
        if (s == null) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}