package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.GetUserReq;
import com.rag.users.admin.proto.GetUserResp;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/admin/getuserlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（五状态机，逐项冻结，对应 Go getuserlogic.go 第 33-60 行）：
 * <pre>
 *   1. 调 adminRpc.GetUser(GetUserReq{Uid})
 *      → 失败：
 *         gRPC codes.NotFound → ErrorUserNotExist  (1003)   // 对应 Go 第 43 行
 *         gRPC codes.Internal → ErrorServerCommon  (5001)   // 对应 Go 第 45 行
 *         其它（默认）         → CodeServerBusy     (1015)   // 对应 Go 第 47 行
 *
 *   2. 成功
 *      → Success (200) + 返回 {user: {uid, username, email, status, extra_info}, found: true}
 *      → 复制 5 字段（uid / username / email / status / extra_info），<b>不</b> setScore（proto3 默认 0）
 *      → email "" 跳过（@JsonInclude(NON_NULL)）；extra_info 空 Map 跳过
 *      → <b>Found 字段</b>：admin-api 显式 setFound(true)，覆盖 admin-rpc 端 proto3 default false（与 Logout 同模式）
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.user} 是 {@link ApiTypes.UserInfo}（5 字段：uid/username/email/status/extra_info，<b>不含 score</b>）
 *       + {@code data.found} = true；失败时 4 个业务码</li>
 *   <li><b>Error</b>：4 个固定业务码（1003/5001/1015/200）</li>
 *   <li><b>Log</b>：rpc_error 时 logger.LogBusinessErr；success 不打 log（与 Go 一致）</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx</li>
 *   <li><b>Metric</b>：Go 端 getuserlogic.go <b>无 metrics 埋点</b> —— 1:1 不补（DRIFT-016 决策）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext}，无可变状态；可重入</li>
 * </ul>
 *
 * <p><b>与 GetSelf 的关键差异</b>：
 * <ul>
 *   <li>GetSelf 调 {@code adminRpc.GetSelf(uid)}（admin 查自己身份，需要 IdentityResolver.resolveAdmin 二次校验身份）</li>
 *   <li>GetUser 调 {@code adminRpc.GetUser(uid)}（admin 查任意 user，<b>不</b>调 IdentityResolver —— JWT 鉴权已确保 admin 合法）</li>
 *   <li>GetSelf 的 AdminInfo 没有 status 字段；GetUser 的 UserInfo 有 status 字段</li>
 *   <li>uid 来源：GetSelf 从 JWT claim 取；GetUser 从 query parameter {@code ?uid=xxx} 取</li>
 * </ul>
 */
@Service
public class GetUserLogic {

    private final ServiceContext svcCtx;
    private final LoggerFactoryBean logger;

    public GetUserLogic(ServiceContext svcCtx, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *GetuserLogic) Getuser(req *types.GetUserReq) (resp *types.GetUserResp, code int)}。
     *
     * <p>参数：{@code uidString} 从 query string {@code ?uid=xxx} 取（与 Go {@code form:"uid,string"} 对齐）。
     * Logic 内部负责 String → long 解析，失败时返回 {@link ErrorCode#ERROR_REQUEST_PARAM} (1017)。
     */
    public LogicResult<ApiTypes.GetUserResp> getUser(String uidString, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        // uid 解析（Go form:"uid,string" 对应 query string）
        Long targetUid = parseLongOrNull(uidString);
        if (targetUid == null) {
            return LogicResult.fail(ErrorCode.ERROR_REQUEST_PARAM);
        }

        GetUserReq rpcReq = GetUserReq.newBuilder().setUid(targetUid).build();
        GetUserResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().getUser(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.Error, err)
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 42-49 行
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

        // 成功：组装 envelope（对应 Go 第 51-60 行）
        // 复制 5 字段：uid / username / email / status / extra_info
        // 不 setScore（proto3 默认 0）—— 1:1 跟随 Go
        com.rag.users.admin.proto.UserInfo protoUser = rpcResp.getUser();
        ApiTypes.UserInfo apiUser = new ApiTypes.UserInfo();
        apiUser.setUid(protoUser.getUid());
        apiUser.setUsername(protoUser.getUsername());
        // email "" 跳过（@JsonInclude(NON_NULL) 效果对齐 Go json:"email,omitempty"）
        String email = protoUser.getEmail();
        if (email != null && !email.isEmpty()) {
            apiUser.setEmail(email);
        }
        // status 是 uint64 → long（Go 第 56 行 Status: int64(rpcResp.User.Status)）
        apiUser.setStatus(protoUser.getStatus());
        // extra_info 字段：getExtraInfoCount() > 0 代表有 entry
        if (protoUser.getExtraInfoCount() > 0) {
            apiUser.setExtraInfo(protoUser.getExtraInfoMap());
        }

        ApiTypes.GetUserResp resp = new ApiTypes.GetUserResp();
        resp.setUser(apiUser);
        // Found 字段：<b>必须显式 setFound(true)</b>，不能透传 rpcResp.getFound()
        // —— admin-rpc 端 proto3 default false；Go 端第 59 行 admin-api 显式 Found: true
        // （与 Logout 的 Success 字段覆盖模式同源 —— 详见 LogoutLogic 注释）
        resp.setFound(true);
        return LogicResult.ok(resp);
    }

    /**
     * 解析 String 形式的 uid（来自 query string）为 long。
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