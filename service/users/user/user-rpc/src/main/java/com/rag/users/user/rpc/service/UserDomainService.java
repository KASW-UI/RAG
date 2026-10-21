package com.rag.users.user.rpc.service;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.common.snowflake.Snowflake;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.users.common.errmsg.ErrorCode;
import com.rag.users.user.proto.CreateUserReq;
import com.rag.users.user.proto.CreateUserResp;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.DeleteUserResp;
import com.rag.users.user.proto.GetAvatarHistoryReq;
import com.rag.users.user.proto.GetAvatarHistoryResp;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.GetUserResp;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.LogoutResp;
import com.rag.users.user.proto.SelectAvatarReq;
import com.rag.users.user.proto.SelectAvatarResp;
import com.rag.users.user.proto.UpdateUserReq;
import com.rag.users.user.proto.UpdateUserResp;
import com.rag.users.user.proto.UploadAvatarReq;
import com.rag.users.user.proto.UploadAvatarResp;
import com.rag.users.user.proto.UserInfo;
import com.rag.users.user.rpc.config.UserRpcProperties;
import com.rag.users.user.rpc.domain.UserAvatarHistoryEntity;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.exception.UserExceptions.AvatarHistoryNotFoundException;
import com.rag.users.user.rpc.exception.UserExceptions.NotFoundException;
import com.rag.users.user.rpc.exception.UserExceptions.PasswordMismatchException;
import com.rag.users.user.rpc.exception.UserExceptions.UsernameExistsException;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.metrics.UserRpcMetrics;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户域服务 —— 把原 9 个 Logic 类合并到一个类。
 *
 * 对应 Go: service/user/user/rpc/internal/logic/ 目录下 9 个 *Logic.go
 *   （loginlogic / registerlogic / getuserlogic / updateuserlogic / deleteuserlogic /
 *    selectavatarlogic / uploadavatarlogic / getavatarhistorylogic / logoutlogic）
 *
 * 框架职责:
 *   - Spring @Service —— 一个类装一个领域的所有用例方法
 *   - 取代了原 internal.logic.{LoginLogic, RegisterLogic, ...} 9 个独立 @Service 类
 *   - 取代了 internal.svc.ServiceContext —— 本类直接 @Autowired 注入所有需要的依赖（Tracer / Redis / Logger / Properties）
 *   - 9 个方法体一对一从原 Logic 翻译，保留 trace + metrics + 三态 status + DB error code
 *   - 异常映射：捕获 NotFoundException → gRPC Status.NOT_FOUND；Exception → Status.INTERNAL
 *
 * 关键 Decision（沿用 Go parity）：
 *   D-001：user not found → throw NotFoundException，Logic catch 后映射 NOT_FOUND
 *   D-002：login 三态（status=0/1/2）：成功 / 用户名错或密码错 / 封禁
 *   D-005：selectAvatar / uploadAvatar 多步原子（双写 user + avatar_history）
 *   D-006/D-007：Logout Redis key 格式 + TTL 用 config.accessExpire
 *   D-010：listAvatarHistory limit clamp [0,30]
 *   D-013：history_id 服务端 Snowflake 生成
 *   D-014：双写 user.extra_info["avatar"]
 *   D-015：history 查询必须带 uid 条件
 *   D-016：metrics 只在 login/register 埋点（最小覆盖）
 *   D-017：updateUserById 只更新 4 列
 *   D-018：每次业务步骤前 checkDeadline，超时立即抛 DEADLINE_EXCEEDED
 */
@Service
public class UserDomainService {

    private final UserMapper userMapper;
    private final UserAvatarHistoryMapper avatarHistoryMapper;
    private final UserRpcMetrics metrics;
    private final StringRedisTemplate bizRedis;
    private final Tracer tracer;
    private final LoggerFactoryBean logger;
    private final UserRpcProperties properties;
    private final AvatarDomainService avatarDomainService;

    public UserDomainService(UserMapper userMapper,
                            UserAvatarHistoryMapper avatarHistoryMapper,
                            UserRpcMetrics metrics,
                            StringRedisTemplate bizRedis,
                            LoggerFactoryBean logger,
                            UserRpcProperties properties,
                            AvatarDomainService avatarDomainService) {
        this.userMapper = userMapper;
        this.avatarHistoryMapper = avatarHistoryMapper;
        this.metrics = metrics;
        this.bizRedis = bizRedis;
        this.logger = logger;
        this.properties = properties;
        this.avatarDomainService = avatarDomainService;
        // 对应 Go 端 otel.Tracer("user-rpc")
        this.tracer = GlobalOpenTelemetry.getTracer("user-rpc");
    }

    /**
     * DRIFT-018 修复：检查当前线程是否已超过 deadline；
     * 任一为 true 抛 {@code StatusRuntimeException(DEADLINE_EXCEEDED)}。
     *
     * 业务 Logic 在每次 DB / bcrypt / snowflake / 下游调用前调此方法。
     * 配合 {@link DeadlineState#markExpired(long)}（由 {@code RpcInterceptor} 调度）
     * 或 {@link Thread#interrupt()}（由 {@code RpcInterceptor.handle()} 路径的
     * {@code Future.cancel(true)} 触发），可让 handler 在 timeout 后立即感知并退出。
     *
     * 未注册 deadline 时为 no-op（业务不受影响）。
     */
    private void checkDeadline() {
        if (Thread.currentThread().isInterrupted() || DeadlineState.isExpired()) {
            throw Status.DEADLINE_EXCEEDED
                    .withDescription("rpc deadline reached")
                    .asRuntimeException();
        }
    }

    // ============== 1. LoginLogic → login() ==============

    /**
     * 对应 Go 端 internal/logic/loginlogic.go。
     *
     * 三态返回（D-001 / D-002，必须保留）：
     *   Status=0 + uid    → 登录成功
     *   Status=1 + uid=0  → 用户不存在 或 密码错（故意不区分，避免用户名枚举）
     *   Status=2 + uid=0  → 用户被封禁（DB status=1）
     *
     * 注：三态都返回 nil error（不是 gRPC 异常），调用方按 LoginResp.status 判断分支。
     */
    public LoginResp login(LoginReq req) {
        Span span = tracer.spanBuilder("Action-User-Login")
                .setAttribute("biz.username", req.getUsername())
                .startSpan();
        Context otelCtx = Context.current().with(span);
        try {
            checkDeadline();
            // W1 查用户
            UserEntity user;
            try {
                checkDeadline();
                user = userMapper.findOneByUserName(req.getUsername());
            } catch (NotFoundException e) {
                // 用户不存在 → Status=1（D-002：和密码错共用同一个状态码）
                metrics.getLoginUserNotFound().increment();
                logger.logInfo(otelCtx, "login failed : user not found,username : " + req.getUsername());
                return LoginResp.newBuilder().setStatus(1L).build();
            } catch (Exception e) {
                metrics.getLoginDbError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "DB查询失败");
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e);
                throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
            }

            // W2 密码比对
            checkDeadline();
            PasswordUtil.Outcome pwOutcome =
                    PasswordUtil.checkDetailed(req.getPassword(), user.getPassword());
            if (pwOutcome != PasswordUtil.Outcome.MATCHED) {
                // DRIFT-004（已修，2026-10-21 用户签字）：hash 格式非法时 Go 同样报密码错，
                // 对外行为逐字一致；但 RAG 额外留一道信号——Go 侧这一路完全静默。
                // 见 PasswordUtil 的 javadoc（实测行为谱：只有 cost 越界才走这一支）。
                if (pwOutcome == PasswordUtil.Outcome.MALFORMED_HASH) {
                    span.setAttribute("auth.malformed_hash", true);
                    logger.logBusinessErr(otelCtx, ErrorCode.ERROR_REQUEST_PARAM.getCode(),
                            new IllegalArgumentException(
                                    "stored bcrypt hash is malformed (username=" + req.getUsername() + ")"));
                }
                metrics.getLoginInternalError().increment();
                // 注：Go 字符串是 "incorrect,username : "（逗号后没空格），保持原样
                logger.logInfo(otelCtx, "login failed: username or password incorrect,username : " + req.getUsername());
                return LoginResp.newBuilder().setStatus(1L).build();
            }

            // W3 封禁检查
            if (user.getStatus() != null && user.getStatus() == 1L) {
                // 用户被封禁 → Status=2（无 metrics，与 Go 端一致）
                // 注：Go 字符串 username 前有空格、username 后有两个空格（typo），保持原样
                logger.logInfo(otelCtx, "login failed: user banned, username :  " + req.getUsername());
                return LoginResp.newBuilder().setStatus(2L).build();
            }

            // W4 成功
            metrics.getLoginSuccess().increment();
            logger.logInfo(otelCtx, "login success,username : " + req.getUsername());
            return LoginResp.newBuilder()
                    .setUid(user.getUid())
                    .setStatus(0L)
                    .build();
        } finally {
            span.end();
        }
    }

    // ============== 2. RegisterLogic → register() ==============

    /**
     * 对应 Go 端 internal/logic/registerlogic.go。
     *
     * 执行序（W1~W5）：
     *   W1 查重 FindOneByUserName → 命中返 AlreadyExists
     *   W2 PasswordUtil.encrypt(req.Password) → bcrypt hash（cost=10）
     *   W3 Snowflake.nextId() → uid
     *   W4 DataModel.insert(User{...})
     *   W5 metrics 成功 / fail_exists / db_error / internal_error
     */
    public CreateUserResp register(CreateUserReq req) {
        Span span = tracer.spanBuilder("Action-User-Register")
                .setAttribute("biz.username", req.getUsername())
                .startSpan();
        Context otelCtx = Context.current().with(span);
        checkDeadline();

        try {
            // W1 查重
            try {
                userMapper.findOneByUserName(req.getUsername());
                metrics.getRegisterFailExists().increment();
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_USER_EXIST.getCode(),
                        new RuntimeException("username has existed"));
                throw Status.ALREADY_EXISTS.withDescription("用户名已存在").asRuntimeException();
            } catch (NotFoundException e) {
                // 用户名可用，继续
            } catch (StatusRuntimeException e) {
                // 上面刚抛的 ALREADY_EXISTS：原样上抛，不能掉进下面的 db_error 分支
                throw e;
            } catch (Exception e) {
                // DRIFT-009：Go 出处 registerlogic.go:50-54 —— 「非 ErrorNotFound」单独成一支，
                // 打 db_error 指标 + ErrorDbSelect 日志 + span.RecordError，再返 Internal。
                // 缺了这一支，DB 抖动在本进程里既无指标也无业务错误日志，完全静默。
                metrics.getRegisterDbError().increment();
                span.recordException(e);
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e);
                throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
            }

            // W2 bcrypt
            String hash;
            try {
                hash = PasswordUtil.encrypt(req.getPassword());
            } catch (Exception e) {
                metrics.getRegisterInternalError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "密码生成失败");
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_SERVER_COMMON.getCode(), e);
                throw Status.INTERNAL.withDescription("密码生成失败").asRuntimeException();
            }

            // W3 snowflake
            long uid;
            try {
                uid = Snowflake.nextId();
            } catch (Exception e) {
                metrics.getRegisterInternalError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "uid生成失败");
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_SERVER_COMMON.getCode(), e);
                throw Status.INTERNAL.withDescription("uid生成失败").asRuntimeException();
            }

            // W4 Insert
            UserEntity u = new UserEntity();
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
                userMapper.insert(u);
            } catch (Exception e) {
                metrics.getRegisterDbError().increment();
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "DB更新失败");
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_UPDATE.getCode(), e);
                throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
            }

            // W5 成功
            metrics.getRegisterSuccess().increment();
            logger.logInfo(otelCtx, "register success,uid : " + uid);
            return CreateUserResp.newBuilder().setUid(uid).build();
        } finally {
            span.end();
        }
    }

    // ============== 3. GetUserLogic → getUser() ==============

    /**
     * 对应 Go 端 internal/logic/getuserlogic.go。
     *
     * 入口：GetUserReq{Uid}
     * 出口：
     *   - 成功 → GetUserResp{User:{Uid, Username, Email, ExtraInfo, Status}, Found=true}
     *   - NotFound → StatusRuntimeException(NOT_FOUND, "用户不存在")
     *   - DB 异常 → StatusRuntimeException(INTERNAL, "DB查询失败")
     *
     * 关键 Decision：
     *   D-003：NotFound → throw StatusRuntimeException（不构造半成品 resp）
     *   D-011：Status 字段必须填（proto3 optional int64）
     *
     * 没有 metrics / OTel span（Go 端 getuserlogic.go 也没埋点）。
     */
    public GetUserResp getUser(GetUserReq req) {
        checkDeadline();
        Context otelCtx = Context.current();
        UserEntity user;
        try {
            user = userMapper.findOneByUid(req.getUid());
        } catch (NotFoundException e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_USER_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        // DRIFT-011：Go 侧 Status 是值类型 int64（GORM 把 NULL 扫进 int64 会直接报错 →
        // FindOneByUid 返回非 sentinel 错误 → ErrorDbSelect + codes.Internal "DB查询失败"），
        // 且 protoc 对 `optional int64 status = 6` **只生成 setStatus(long)**（无 Long 重载），
        // 这里直接 setStatus(user.getStatus()) 会在 status 为 null 时自动拆箱抛 NPE，
        // 被 UserServiceGrpcImpl 兜底成 INTERNAL + "NullPointerException: ..."，
        // 既丢了 ErrorDbSelect 日志，description 也与 Go 不同。
        // 对齐做法：null 视同 Go 的扫描失败，走与 Go 同一条错误路径。
        // 绝不 fallback 成 0 —— status=0 在 identity 解析里意为「活跃」，
        // 把 NULL 默认成活跃是朝错误方向的 fail-open。
        if (user.getStatus() == null) {
            logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(),
                    new IllegalStateException("users_user.status is NULL for uid=" + req.getUid()));
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        UserInfo.Builder b = UserInfo.newBuilder()
                .setUid(user.getUid())
                .setUsername(user.getUsername() == null ? "" : user.getUsername())
                .setEmail(user.getEmail() == null ? "" : user.getEmail())
                .setStatus(user.getStatus()); // D-011: Long（非 null）
        Map<String, String> extra = user.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }

        logger.logInfo(otelCtx, "search user success,uid : " + req.getUid());
        return GetUserResp.newBuilder()
                .setUser(b.build())
                .setFound(true)
                .build();
    }

    // ============== 4. UpdateUserLogic → updateUser() ==============

    /**
     * 对应 Go 端 internal/logic/updateuserlogic.go。
     *
     * 部分更新——只更新"显式传入且非空"的字段。
     * Works（W1~W5）：
     *   W1 Username 重名检查（仅 in.Username 非空时）
     *   W2 按需 bcrypt（仅 in.Password 非空时）
     *   W3 Email 覆盖（仅非空时）
     *   W4 ExtraInfo 覆盖（仅非 nil 时）
     *   W5 updateUserById(uid, toUpdate) → 仅更新非空字段（D-017）
     *   W6 重读 user 构造 resp
     *
     * D-004：Username 重名检查不排除自己——若用户把自己的 Username 改成现值也会 AlreadyExists。
     * 没有 metrics / OTel span。
     */
    public UpdateUserResp updateUser(UpdateUserReq req) {
        checkDeadline();
        UserEntity toUpdate = new UserEntity();

        // W1 Username 重名检查
        if (!req.getUsername().isEmpty()) {
            UserEntity existing;
            try {
                existing = userMapper.findOneByUserName(req.getUsername());
            } catch (NotFoundException e) {
                existing = null;
            } catch (Exception e) {
                // DRIFT-009：Go 出处 updateuserlogic.go:41-44 —— 查库故障单独成一支
                logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_SELECT.getCode(), e);
                throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
            }
            if (existing != null) {
                // D-004：不排除自己
                // Go 出处 updateuserlogic.go:38 同样打 ErrorUserExist（它传的 err 是 nil，
                // 这里传一个描述性异常代替——业务码一致，Throwable 内容不参与 wire）。
                logger.logBusinessErr(Context.current(), ErrorCode.ERROR_USER_EXIST.getCode(),
                        new RuntimeException("username already exists: " + req.getUsername()));
                throw Status.ALREADY_EXISTS.withDescription("用户名已存在").asRuntimeException();
            }
            toUpdate.setUsername(req.getUsername());
        }

        // W2 bcrypt
        if (!req.getPassword().isEmpty()) {
            try {
                toUpdate.setPassword(PasswordUtil.encrypt(req.getPassword()));
            } catch (Exception e) {
                // Go 出处 updateuserlogic.go:50
                logger.logBusinessErr(Context.current(), ErrorCode.ERROR_SERVER_COMMON.getCode(), e);
                throw Status.INTERNAL.withDescription("密码生成失败").asRuntimeException();
            }
        }

        // W3 Email
        if (!req.getEmail().isEmpty()) {
            toUpdate.setEmail(req.getEmail());
        }

        // W4 ExtraInfo
        if (req.getExtraInfoCount() > 0) {
            Map<String, String> map = new HashMap<>();
            req.getExtraInfoMap().forEach(map::put);
            toUpdate.setExtraInfo(map);
        }

        // W5 Update（D-017：动态 JPQL 只写非空字段）
        try {
            userMapper.updateUserById(req.getUid(), toUpdate);
        } catch (Exception e) {
            // Go 出处 updateuserlogic.go:65
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_UPDATE.getCode(), e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }

        // W6 重读
        UserEntity fresh;
        try {
            fresh = userMapper.findOneByUid(req.getUid());
        } catch (NotFoundException e) {
            // Go 出处 updateuserlogic.go:73
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_USER_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            // Go 出处 updateuserlogic.go:76
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_SELECT.getCode(), e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        UserInfo.Builder b = UserInfo.newBuilder()
                .setUid(fresh.getUid())
                .setUsername(fresh.getUsername() == null ? "" : fresh.getUsername())
                .setEmail(fresh.getEmail() == null ? "" : fresh.getEmail());
        Map<String, String> extra = fresh.getExtraInfo();
        if (extra != null && !extra.isEmpty()) {
            b.putAllExtraInfo(extra);
        } else {
            b.putAllExtraInfo(new HashMap<>());
        }
        // 注：resp 不含 Status 字段，对齐 Go 端 UpdateUserResp.UserInfo 只填 Uid/Username/Email/ExtraInfo

        return UpdateUserResp.newBuilder().setUser(b.build()).build();
    }

    // ============== 5. DeleteUserLogic → deleteUser() ==============

    /**
     * 对应 Go 端 internal/logic/deleteuserlogic.go。
     *
     * Works：
     *   W1 OTel span "Action-User-Delete"，属性 biz-uid=in.Uid
     *   W2 FindOneByUid(uid) → 必须存在
     *   W3 deleteUserByUid(uid)
     *
     * D-003：NotFound → StatusRuntimeException。
     * UN-1：Go 端 DeleteUser 不级联删 user_avatar_history；是否级联由 Postgres FK ON DELETE CASCADE 决定。
     */
    public DeleteUserResp deleteUser(DeleteUserReq req) {
        Span span = tracer.spanBuilder("Action-User-Delete")
                .setAttribute("biz-uid", req.getUid())
                .startSpan();
        // Go 出处 deleteuserlogic.go:35-36 —— spanCtx = tracer.Start(l.ctx, ...)，日志全部走 spanCtx，
        // 这样 LogBusinessErr 里的 trace_id 才能和这个 span 对上。此前 RAG 侧没建 otelCtx，
        // 新增的 ErrorDbSelect/ErrorDbUpdate 日志会丢失 span 关联。
        Context otelCtx = Context.current().with(span);
        checkDeadline();

        try {
            // W2
            try {
                userMapper.findOneByUid(req.getUid());
            } catch (NotFoundException e) {
                // Go 出处 deleteuserlogic.go:43
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_USER_NOT_EXIST.getCode(), e);
                throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
            } catch (Exception e) {
                // Go 出处 deleteuserlogic.go:48-49（span.RecordError + ErrorDbSelect）
                span.recordException(e);
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_SELECT.getCode(), e);
                throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
            }

            // W3
            try {
                userMapper.deleteUserByUid(req.getUid());
            } catch (Exception e) {
                // Go 出处 deleteuserlogic.go:57
                span.recordException(e);
                logger.logBusinessErr(otelCtx, ErrorCode.ERROR_DB_UPDATE.getCode(), e);
                throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
            }

            return DeleteUserResp.newBuilder().setSuccess(true).build();
        } finally {
            span.end();
        }
    }

    // ============== 6. SelectAvatarLogic → selectAvatar() ==============

    /**
     * 对应 Go 端 internal/logic/selectavatarlogic.go。
     *
     * Works（W1~W3）：
     *   W1 校验 history_id trim 后非空
     *   W2 selectAvatarHistory(uid, historyId) —— 5 步原子事务（D-005）
     *   W3 构造 resp
     *
     * D-005 / D-014 / D-015：必须原子；history 查询必须带 uid 条件（防越权）。
     */
    public SelectAvatarResp selectAvatar(SelectAvatarReq req) {
        checkDeadline();
        // W1
        String historyId = avatarDomainService.normalizeHistoryId(req.getHistoryId());
        if (historyId.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("history_id is required").asRuntimeException();
        }

        // W2
        UserAvatarHistoryEntity history;
        try {
            history = avatarHistoryMapper.selectAvatarHistory(userMapper, req.getUid(), historyId);
        } catch (NotFoundException e) {
            // Go 出处 selectavatarlogic.go:42（ErrorUserNotExist）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_USER_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (AvatarHistoryNotFoundException e) {
            // Go 出处 selectavatarlogic.go:45 —— 它这里传的是 errmsg.Error(=500)，
            // 显然是漏填（错误码表里有 ErrorAvatarHistoryNotExist=1018）。
            // 日志码不参与 wire，用语义正确的 1018；响应码仍由 AvatarSelectService 按 Go 映射。
            logger.logBusinessErr(Context.current(),
                    ErrorCode.ERROR_AVATAR_HISTORY_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND.withDescription("头像历史不存在").asRuntimeException();
        } catch (Exception e) {
            // Go 出处 selectavatarlogic.go:48（ErrorDbUpdate）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_UPDATE.getCode(), e);
            throw Status.INTERNAL.withDescription("DB更新失败").asRuntimeException();
        }

        // W3
        return SelectAvatarResp.newBuilder()
                .setAvatarUrl(history.getAvatarUrl() == null ? "" : history.getAvatarUrl())
                .setHistory(avatarDomainService.fromModel(history))
                .build();
    }

    // ============== 7. UploadAvatarLogic → uploadAvatar() ==============

    /**
     * 对应 Go 端 internal/logic/uploadavatarlogic.go。
     *
     * Works（W1~W4）：
     *   W1 校验 avatar_url trim 后非空
     *   W2 Snowflake.nextId() → history_id（D-013）
     *   W3 createAvatarHistory(uid, historyId, avatarURL, normalizedCT, sizeBytes) —— 4 步原子（D-005）
     *   W4 构造 resp
     */
    public UploadAvatarResp uploadAvatar(UploadAvatarReq req) {
        checkDeadline();
        // W1
        String avatarUrl = avatarDomainService.normalizeAvatarUrl(req.getAvatarUrl());
        if (avatarUrl.isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("avatar_url is required").asRuntimeException();
        }

        // W2
        String historyId;
        try {
            historyId = String.valueOf(Snowflake.nextId());
        } catch (Exception e) {
            // Go 出处 uploadavatarlogic.go:41（ErrorSnowflakeID）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_SNOWFLAKE_ID.getCode(), e);
            throw Status.INTERNAL.withDescription("history_id生成失败").asRuntimeException();
        }

        // W3
        String contentType = avatarDomainService.normalizeContentType(req.getContentType());
        UserAvatarHistoryEntity history;
        try {
            history = avatarHistoryMapper.createAvatarHistory(
                    userMapper, req.getUid(), historyId, avatarUrl, contentType, req.getSizeBytes());
        } catch (NotFoundException e) {
            // Go 出处 uploadavatarlogic.go:56（ErrorUserNotExist）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_USER_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (AvatarHistoryNotFoundException e) {
            // createAvatarHistory 不会抛这个，保留兜底以免落进下面的 db_error 分支
            logger.logBusinessErr(Context.current(),
                    ErrorCode.ERROR_AVATAR_HISTORY_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND.withDescription("头像历史不存在").asRuntimeException();
        } catch (Exception e) {
            // Go 出处 uploadavatarlogic.go:59（ErrorDbInsert）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_INSERT.getCode(), e);
            throw Status.INTERNAL.withDescription("DB插入失败").asRuntimeException();
        }

        // W4
        return UploadAvatarResp.newBuilder()
                .setAvatarUrl(avatarUrl)
                .setHistory(avatarDomainService.fromModel(history))
                .build();
    }

    // ============== 8. GetAvatarHistoryLogic → getAvatarHistory() ==============

    /**
     * 对应 Go 端 internal/logic/getavatarhistorylogic.go。
     *
     * Works：
     *   W1 FindOneByUid(uid) → 用户必须存在
     *   W2 listAvatarHistory(uid, limit) → ORDER BY create_time DESC
     *
     * D-010：limit <= 0 || limit > 30 静默 clamp 到 30（DataModel.listAvatarHistory 内部已实现）。
     */
    public GetAvatarHistoryResp getAvatarHistory(GetAvatarHistoryReq req) {
        checkDeadline();
        // W1
        try {
            userMapper.findOneByUid(req.getUid());
        } catch (NotFoundException e) {
            // Go 出处 getavatarhistorylogic.go:34（ErrorUserNotExist）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_USER_NOT_EXIST.getCode(), e);
            throw Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException();
        } catch (Exception e) {
            // Go 出处 getavatarhistorylogic.go:37（ErrorDbSelect）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_SELECT.getCode(), e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        // W2（D-010：limit clamp 在 mapper.listAvatarHistory 内部完成）
        List<UserAvatarHistoryEntity> histories;
        try {
            histories = avatarHistoryMapper.listAvatarHistory(req.getUid(), req.getLimit());
        } catch (Exception e) {
            // Go 出处 getavatarhistorylogic.go:43（ErrorDbSelect）
            logger.logBusinessErr(Context.current(), ErrorCode.ERROR_DB_SELECT.getCode(), e);
            throw Status.INTERNAL.withDescription("DB查询失败").asRuntimeException();
        }

        List<com.rag.users.user.proto.AvatarHistoryItem> items = new ArrayList<>(histories.size());
        for (UserAvatarHistoryEntity h : histories) {
            items.add(avatarDomainService.fromModel(h));
        }

        return GetAvatarHistoryResp.newBuilder().addAllList(items).build();
    }

    // ============== 9. LogoutLogic → logout() ==============

    /**
     * 对应 Go 端 internal/logic/logoutlogic.go。
     *
     * Works：
     *   W1 校验 token 非空
     *   W2 Redis Setex("user:jwt_blacklist:%s", "banned", AccessExpire)
     *
     * D-006：Redis key 必须是字面量 "user:jwt_blacklist:%s"（HTTP 中间件用同一格式去查）
     * D-007：TTL 必须等于 JWT 过期秒数（UserAuth.AccessExpire=2592000），不能写死 24h
     */
    public LogoutResp logout(LogoutReq req) {
        checkDeadline();
        // W1
        if (req.getToken().isEmpty()) {
            throw Status.INVALID_ARGUMENT.withDescription("Token不能为空").asRuntimeException();
        }

        // W2（D-006 key + D-007 TTL）
        String key = "user:jwt_blacklist:" + req.getToken();
        long ttlSeconds = properties.getUserAuth().getAccessExpire();
        try {
            bizRedis.opsForValue().set(key, "banned", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            throw Status.INTERNAL.withDescription("注销失败,Redis写入异常").asRuntimeException();
        }

        return LogoutResp.newBuilder().build();
    }
}