package com.rag.users.user.rpc.internal.logic;

import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import com.rag.users.user.rpc.internal.metrics.Metrics;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.User;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/loginlogic.go。
 *
 * 三态返回（D-001 / D-002，必须保留）：
 *   Status=0 + uid    → 登录成功
 *   Status=1 + uid=0  → 用户不存在 或 密码错（故意不区分，避免用户名枚举）
 *   Status=2 + uid=0  → 用户被封禁（DB status=1）
 *
 * 注：三态都返回 nil error（不是 gRPC 异常），调用方按 LoginResp.status 判断分支。
 *
 * 框架对照：
 *   Go 把"错误分支"塞进 LoginResp.status，调用方按状态字段分发；
 *   Java 同样把状态放进 resp，调用方读 getStatus()（grpc-java 把 proto uint64 映射成 long）。
 */
@Service
public class LoginLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public LoginLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        // DRIFT-OBS-LOGIN 修复（1:1 复刻 Go 端 logger 调用）：
        // Go 端每个分支都 logger.LogInfo / logger.LogBusinessErr(spanCtx, errmsg.X, err)。
        // Logger 通过 svcCtx.getLogger() 取（构造器签名不变，对应其它 Logic 模式）
    }

    public LoginResp login(LoginReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-User-Login")
                .setAttribute("biz.username", req.getUsername())
                .startSpan();
        // DRIFT-OBS-LOGIN 修复：构造 otelCtx 让 logger.logXxx 拿到 trace_id
        Context otelCtx = Context.current().with(span);
        try {
            // DRIFT-018：方法入口 + 每个业务步骤前检查 deadline，超时立即抛 DEADLINE_EXCEEDED
            svcCtx.checkDeadline();
            // W1 查用户
            User user;
            try {
                svcCtx.checkDeadline();
                user = svcCtx.getUserModel().findOneByUserName(req.getUsername());
            } catch (NotFoundException e) {
                // 用户不存在 → Status=1（D-002：和密码错共用同一个状态码）
                metrics.getLoginUserNotFound().increment();
                // 对应 Go 端 logger.LogInfo(spanCtx, fmt.Sprintf("login failed : user not found,username : %s", in.Username))
                svcCtx.getLogger().logInfo(otelCtx, "login failed : user not found,username : " + req.getUsername());
                return LoginResp.newBuilder().setStatus(1L).build();
            } catch (Exception e) {
                metrics.getLoginDbError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "DB查询失败");
                // 对应 Go 端 logger.LogBusinessErr(spanCtx, errmsg.ErrorDbSelect, err) —— ErrorDbSelect=5003
                svcCtx.getLogger().logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e);
                throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
            }

            // W2 密码比对
            svcCtx.checkDeadline();
            if (!PasswordUtil.check(req.getPassword(), user.getPassword())) {
                // 密码错 → Status=1（与"用户不存在"共用；metrics label = internal_error，与 Go 端 1:1）
                metrics.getLoginInternalError().increment();
                // 对应 Go 端 logger.LogInfo(spanCtx, fmt.Sprintf("login failed: username or password incorrect,username : %s", in.Username))
                // 注：Go 字符串是 "incorrect,username : "（逗号后没空格），保持原样
                svcCtx.getLogger().logInfo(otelCtx, "login failed: username or password incorrect,username : " + req.getUsername());
                return LoginResp.newBuilder().setStatus(1L).build();
            }

            // W3 封禁检查
            if (user.getStatus() != null && user.getStatus() == 1L) {
                // 用户被封禁 → Status=2（无 metrics，与 Go 端一致）
                // 对应 Go 端 logger.LogInfo(spanCtx, fmt.Sprintf("login failed: user banned, username :  %s", in.Username))
                // 注：Go 字符串 username 前有空格、username 后有两个空格（typo），保持原样
                svcCtx.getLogger().logInfo(otelCtx, "login failed: user banned, username :  " + req.getUsername());
                return LoginResp.newBuilder().setStatus(2L).build();
            }

            // W4 成功
            metrics.getLoginSuccess().increment();
            // 对应 Go 端 logger.LogInfo(spanCtx, fmt.Sprintf("login success,username : %s", in.Username))
            svcCtx.getLogger().logInfo(otelCtx, "login success,username : " + req.getUsername());
            return LoginResp.newBuilder()
                    .setUid(user.getUid())
                    .setStatus(0L)
                    .build();
        } finally {
            span.end();
        }
    }
}