package com.rag.users.admin.rpc.internal.logic;

import com.rag.common.observability.Observability;
import com.rag.users.admin.proto.LoginReq;
import com.rag.users.admin.proto.LoginResp;
import com.rag.users.admin.rpc.internal.metrics.Metrics;
import com.rag.users.admin.rpc.internal.model.Admin;
import com.rag.users.admin.rpc.internal.model.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.internal.svc.ServiceContext;
import com.rag.users.common.cryptx.PasswordUtil;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.springframework.stereotype.Service;

/**
 * 对应 Go 端 internal/logic/loginlogic.go：
 *   func (l *LoginLogic) Login(in *pb.LoginReq) (*pb.LoginResp, error)
 *
 * <p>与 user-rpc LoginLogic 关键差异：admin-rpc 的 LoginResp 只有 uid 字段，无 status 三态。
 * 失败通过 gRPC StatusRuntimeException 表达（NOT_FOUND / UNAUTHENTICATED / INTERNAL）。
 *
 * <p>Works（W1~W4）：
 *   W1 OTel span "Action-Admin-Login"，属性 audit.admin_username=in.Username
 *   W2 FindOneAdminByUsername → NotFound / db_error / 命中
 *   W3 PasswordUtil.check(admin.Password, in.Password) → wrong_answer / 命中
 *   W4 metrics.success + LoginResp{Uid=admin.Uid}
 *
 * <p>Metrics 4 类 result label：
 *   - user_not_found（DataModel 抛 NotFoundException）
 *   - db_error（其他 Exception，DB 查询失败）
 *   - wrong_answer（密码错）
 *   - success（最终返回）
 *
 * <p>注：Go 端有 logger.LogBusinessErr / LogInfo 调用，Java 侧跟随 user-rpc 惯例，
 *     不在 Logic 里直接调 com.rag.common.logger.Logger（避免 unit test 必须 init Logger）。
 *     业务日志通过 OTel span 的属性 + gRPC interceptor 链路日志承担。
 */
@Service
public class LoginLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public LoginLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
    }

    public LoginResp login(LoginReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-Admin-Login")
                .setAttribute("audit.admin_username", req.getUsername())
                .startSpan();
        try {
            // W2 查 admin
            Admin admin;
            try {
                admin = svcCtx.getDataModel().findOneAdminByUsername(req.getUsername());
            } catch (NotFoundException e) {
                metrics.getLoginUserNotFound().increment();
                throw Status.NOT_FOUND
                        .withDescription("用户不存在")
                        .asRuntimeException();
            } catch (Exception e) {
                metrics.getLoginDbError().increment();
                Observability.markSystemError(io.opentelemetry.context.Context.current().with(span),
                        e, java.time.Duration.ZERO, null);
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "DB查询失败");
                throw Status.INTERNAL
                        .withDescription("DB查询失败")
                        .asRuntimeException();
            }

            // W3 比对密码
            if (!PasswordUtil.check(req.getPassword(), admin.getPassword())) {
                metrics.getLoginWrongAnswer().increment();
                throw Status.UNAUTHENTICATED
                        .withDescription("密码错误")
                        .asRuntimeException();
            }

            // W4 成功
            metrics.getLoginSuccess().increment();
            return LoginResp.newBuilder()
                    .setUid(admin.getUid())
                    .build();
        } finally {
            span.end();
        }
    }
}