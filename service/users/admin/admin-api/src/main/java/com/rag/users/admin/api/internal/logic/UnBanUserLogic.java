package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.identity.IdentityResolver;
import com.rag.users.admin.api.internal.metrics.Metrics;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.UnBanUserReq;
import com.rag.users.admin.proto.UnBanUserResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/unbanuserlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（七状态机，逐项冻结，对应 Go unbanuserlogic.go 第 36-62 行）：
 * <pre>
 *   1. 取当前已认证 admin 的 uid 作为 operator_id
 *      （Go 用 ctx.Value("uid") 拿 —— <b>DRIFT-NEW-003</b>：go-zero rest.WithJwt 实际只写
 *       "userId" 不写 "uid"，所以 Go 端拿不到，会传 operator_id=0；
 *       Java 端用 IdentityResolver.claimedUID(request) 拿 "userId" attribute，传真实 operator_id）
 *
 *   2. 调 adminRpc.UnBanUser(UnBanUserReq{Uid, OperatorId})
 *      → 失败：
 *         gRPC codes.NotFound → ErrorUserNotExist  (1003)   // 对应 Go 第 51 行
 *         gRPC codes.Internal → <b>ErrorDbSelect</b>  (5003)   // 对应 Go 第 53 行（与 banuser 不同：banuser 是 5001）
 *         其它（默认）         → CodeServerBusy     (1015)   // 对应 Go 第 55 行
 *      → rpc_error 时 metrics "rpc_error"
 *
 *   3. 成功
 *      → Success (200) + 返回 {success: rpcResp.Success}
 *      → metrics "success"
 * </pre>
 *
 * <p><b>与 banuser 的差异</b>：codes.Internal 映射不同 —— banuser 是 ErrorServerCommon (5001)，
 * unbanuser 是 ErrorDbSelect (5003)。这是 Go 端 admin/api 两个 mutation 错误码不一致（DRIFT-NEW-004：
 * 同一个 update 操作，banuser 报"DB更新失败"5001，unbanuser 报"DB查询失败"5003——语义上都是 update）。
 * 1:1 复刻保留。
 */
@Service
public class UnBanUserLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;
    private final LoggerFactoryBean logger;

    public UnBanUserLogic(ServiceContext svcCtx, Metrics metrics, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *UnbanuserLogic) Unbanuser(req *types.UnBanUserReq) (resp *types.UnBanUserResp, code int)}。
     */
    public LogicResult<ApiTypes.UnBanUserResp> unBanUser(ApiTypes.UnBanUserReq req, HttpServletRequest httpRequest) {
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
        Long targetUid = req.getUid() == null ? null : parseLongOrNull(req.getUid());
        if (targetUid == null) {
            return LogicResult.fail(ErrorCode.ERROR_REQUEST_PARAM);
        }
        UnBanUserReq rpcReq = UnBanUserReq.newBuilder()
                .setUid(targetUid)
                .setOperatorId(operatorId)
                .build();

        // 3. 调 adminRpc.UnBanUser
        UnBanUserResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().unBanUser(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: metrics.AdminApiInterceptCount.WithLabelValues("/admin/unbanuser", "rpc_error").Inc()
            metrics.getUnbanRpcError().increment();
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.Error, err)
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 50-57 行 —— 与 banuser 的差异：INTERNAL 映射 5003
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case NOT_FOUND:
                    return LogicResult.fail(ErrorCode.ERROR_USER_NOT_EXIST);   // 1003
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_DB_SELECT);       // 5003（与 banuser 的 5001 不同 —— DRIFT-NEW-004）
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);       // 1015
            }
        }

        // 4. 成功（对应 Go 第 59 行 metrics "success" + 第 60-62 行构造响应）
        metrics.getUnbanSuccess().increment();
        ApiTypes.UnBanUserResp resp = new ApiTypes.UnBanUserResp();
        resp.setSuccess(rpcResp.getSuccess());
        return LogicResult.ok(resp);
    }

    /**
     * 解析 String 形式的 uid（json:"uid,string" 反序列化结果）为 long。
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