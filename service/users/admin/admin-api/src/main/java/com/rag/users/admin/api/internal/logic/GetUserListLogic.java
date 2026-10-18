package com.rag.users.admin.api.internal.logic;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.admin.api.internal.svc.ServiceContext;
import com.rag.users.admin.api.internal.types.ApiTypes;
import com.rag.users.admin.proto.GetUserListReq;
import com.rag.users.admin.proto.GetUserListResp;
import com.rag.users.admin.proto.UserInfo;
import com.rag.users.common.errmsg.ErrorCode;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.context.Context;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对应 Go 端 internal/logic/admin/getuserlistlogic.go。
 *
 * <p>L1 外部契约 + L2 业务语义（四状态机，逐项冻结，对应 Go getuserlistlogic.go 第 33-66 行）：
 * <pre>
 *   1. 调 adminRpc.GetUserList(GetUserListReq{Page, PageSize, Keyword})（3 字段 1:1 透传）
 *      → 失败：
 *         gRPC codes.Internal → ErrorServerCommon  (5001)   // 对应 Go 第 45 行
 *         其它（默认）         → CodeServerBusy     (1015)   // 对应 Go 第 47 行
 *         <b>没有</b> codes.NotFound 分支（与 GetUser 不同 —— 列表查询不该 404）
 *
 *   2. 成功
 *      → Success (200) + 返回 {list: [{uid, username, email, status, extra_info}], total}
 *      → 逐行 user 复制 5 字段（不 setScore，proto3 默认 0）
 *      → email "" 跳过；extra_info 空 Map 跳过
 * </pre>
 *
 * <p>六个可观察面保留：
 * <ul>
 *   <li><b>Return</b>：成功时 {@code data.list} 是 5 字段 UserInfo 数组（不含 score），{@code data.total} 是 long</li>
 *   <li><b>Error</b>：3 个固定业务码（5001/1015/200）</li>
 *   <li><b>Log</b>：rpc_error 时 logger.LogBusinessErr；success 不打 log（与 Go 一致）</li>
 *   <li><b>Trace</b>：从 {@link Context#current()} 取 OTel ctx</li>
 *   <li><b>Metric</b>：Go 端 getuserlistlogic.go <b>无 metrics 埋点</b> —— 1:1 不补（DRIFT-016 决策）</li>
 *   <li><b>Lifecycle</b>：仅依赖 Spring 注入的 {@link ServiceContext}，无可变状态；可重入</li>
 * </ul>
 */
@Service
public class GetUserListLogic {

    private final ServiceContext svcCtx;
    private final LoggerFactoryBean logger;

    public GetUserListLogic(ServiceContext svcCtx, LoggerFactoryBean logger) {
        this.svcCtx = svcCtx;
        this.logger = logger;
    }

    /**
     * 对应 Go {@code func (l *GetuserlistLogic) Getuserlist(req *types.GetUserListReq) (resp *types.GetUserListResp, code int)}。
     */
    public LogicResult<ApiTypes.GetUserListResp> getUserList(ApiTypes.GetUserListReq req, HttpServletRequest httpRequest) {
        Context otelCtx = Context.current();

        // 构造 rpcReq（3 字段，1:1 透传；对应 Go 第 34-38 行）
        GetUserListReq rpcReq = GetUserListReq.newBuilder()
                .setPage(req.getPage())
                .setPageSize(req.getPageSize())
                .setKeyword(req.getKeyword() == null ? "" : req.getKeyword())
                .build();

        GetUserListResp rpcResp;
        try {
            rpcResp = svcCtx.getAdminRpc().getUserList(rpcReq);
        } catch (StatusRuntimeException e) {
            // 对应 Go: logger.LogBusinessErr(l.ctx, errmsg.Error, err)
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR.getCode(), e);
            // 对应 Go: switch st.Code() 第 44-49 行 —— 只 Internal + default 两路（<b>没有 NotFound 分支</b>）
            Status.Code code = e.getStatus().getCode();
            switch (code) {
                case INTERNAL:
                    return LogicResult.fail(ErrorCode.ERROR_SERVER_COMMON);   // 5001
                default:
                    return LogicResult.fail(ErrorCode.CODE_SERVER_BUSY);       // 1015
            }
        }

        // 成功：逐行复制 5 字段（对应 Go 第 51-62 行）
        // 复制 uid/username/email/status/extra_info；不 setScore（proto3 默认 0）
        List<ApiTypes.UserInfo> apiList = new ArrayList<>();
        for (UserInfo protoUser : rpcResp.getListList()) {
            ApiTypes.UserInfo apiUser = new ApiTypes.UserInfo();
            apiUser.setUid(protoUser.getUid());
            apiUser.setUsername(protoUser.getUsername());
            String email = protoUser.getEmail();
            if (email != null && !email.isEmpty()) {
                apiUser.setEmail(email);
            }
            apiUser.setStatus(protoUser.getStatus());
            if (protoUser.getExtraInfoCount() > 0) {
                apiUser.setExtraInfo(protoUser.getExtraInfoMap());
            }
            apiList.add(apiUser);
        }

        ApiTypes.GetUserListResp resp = new ApiTypes.GetUserListResp();
        resp.setList(apiList);
        resp.setTotal(rpcResp.getTotal());
        return LogicResult.ok(resp);
    }
}