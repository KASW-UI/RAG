package com.rag.users.user.rpc.internal.logic;

import com.rag.common.snowflake.Snowflake;
import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.CreateUserResp;
import com.rag.users.user.rpc.internal.metrics.Metrics;
import com.rag.users.user.rpc.internal.model.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.internal.model.User;
import com.rag.users.user.rpc.internal.svc.ServiceContext;
import io.grpc.Status;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go 端 internal/logic/registerlogic.go：
 *   func (l *RegisterLogic) Register(in *pb.CreateUserReq) (*pb.CreateUserResp, error)
 *
 * 执行序（W1~W5）：
 *   W1 查重 FindOneByUserName → 命中返 AlreadyExists
 *   W2 PasswordUtil.encrypt(req.Password) → bcrypt hash（cost=10，对齐 Go bcrypt.DefaultCost）
 *   W3 Snowflake.nextId() → uid
 *   W4 DataModel.insert(User{...})
 *   W5 metrics 成功 / fail_exists / db_error / internal_error
 *
 * OTel：span "Action-User-Register"，属性 biz.username=req.Username
 * Go 端其他 8 个 RPC 都没有 metrics/span（用户已经按 D-016 复刻 Go 的最小覆盖）。
 */
@Service
public class RegisterLogic {

    private final ServiceContext svcCtx;
    private final Metrics metrics;

    public RegisterLogic(ServiceContext svcCtx, Metrics metrics) {
        this.svcCtx = svcCtx;
        this.metrics = metrics;
        // DRIFT-OBS-LOGIN 修复（1:1 复刻 Go 端 logger 调用）：
        // Go 端每个分支都 logger.LogInfo / logger.LogBusinessErr(spanCtx, errmsg.X, err)。
        // Logger 通过 svcCtx.getLogger() 取（构造器签名不变，对应其它 Logic 模式）
    }

    public CreateUserResp register(CreateUserReq req) {
        Span span = svcCtx.getTracer().spanBuilder("Action-User-Register")
                .setAttribute("biz.username", req.getUsername())
                .startSpan();
        // DRIFT-OBS-LOGIN 修复：构造 otelCtx 让 logger.logXxx 拿到 trace_id
        Context otelCtx = Context.current().with(span);
        // DRIFT-018: timeout -> DEADLINE_EXCEEDED
        svcCtx.checkDeadline();

                try {
            // W1 查重
            try {
                svcCtx.getUserModel().findOneByUserName(req.getUsername());
                metrics.getRegisterFailExists().increment();
                // 对应 Go 端 logger.LogBusinessErr(spanCtx, errmsg.ErrorUserExist, fmt.Errorf("username has existed")) —— ErrorUserExist=1001
                svcCtx.getLogger().logBusinessErr(otelCtx, ErrorCode.ERROR_USER_EXIST.getCode(),
                        new RuntimeException("username has existed"));
                throw Status.ALREADY_EXISTS
                        .withDescription("用户名已存在")
                        .asRuntimeException();
            } catch (NotFoundException e) {
                // 用户名可用，继续
            }

            // W2 bcrypt
            String hash;
            try {
                hash = PasswordUtil.encrypt(req.getPassword());
            } catch (Exception e) {
                metrics.getRegisterInternalError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "密码生成失败");
                // 对应 Go 端 logger.LogBusinessErr(spanCtx, errmsg.ErrorServerCommon, err) —— ErrorServerCommon=5001
                svcCtx.getLogger().logBusinessErr(otelCtx, ErrorCode.ERROR_SERVER_COMMON.getCode(), e);
                throw Status.INTERNAL.withDescription("密码生成失败").asRuntimeException();
            }

            // W3 snowflake（com.rag.common.snowflake.Snowflake 是静态工具，不能注入）
            long uid;
            try {
                uid = Snowflake.nextId();
            } catch (Exception e) {
                metrics.getRegisterInternalError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "uid生成失败");
                // 对应 Go 端 logger.LogBusinessErr(spanCtx, errmsg.ErrorServerCommon, err) —— ErrorServerCommon=5001
                svcCtx.getLogger().logBusinessErr(otelCtx, ErrorCode.ERROR_SERVER_COMMON.getCode(), e);
                throw Status.INTERNAL.withDescription("uid生成失败").asRuntimeException();
            }

            // W4 Insert
            User u = new User();
            u.setUid(uid);
            u.setUsername(req.getUsername());
            u.setPassword(hash);
            u.setEmail(req.getEmail());
            u.setScore(0);
            u.setStatus(0L);
            if (req.getExtraInfoCount() > 0) {
                Map<String, String> map = new HashMap<>();
                req.getExtraInfoMap().forEach(map::put);
                u.setExtraInfo(map);
            }
            try {
                svcCtx.getUserModel().insert(u);
            } catch (Exception e) {
                metrics.getRegisterDbError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "DB更新失败");
                // 对应 Go 端 logger.LogBusinessErr(spanCtx, errmsg.ErrorDbUpdate, err) —— ErrorDbUpdate=5002
                svcCtx.getLogger().logBusinessErr(otelCtx, ErrorCode.ERROR_DB_UPDATE.getCode(), e);
                throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
            }

            // W5 成功
            metrics.getRegisterSuccess().increment();
            // 对应 Go 端 logger.LogInfo(spanCtx, fmt.Sprintf("register success,uid : %d", uid))
            svcCtx.getLogger().logInfo(otelCtx, "register success,uid : " + uid);
            return CreateUserResp.newBuilder().setUid(uid).build();
        } finally {
            span.end();
        }
    }
}