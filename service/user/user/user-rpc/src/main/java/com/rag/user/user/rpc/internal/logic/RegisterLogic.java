package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.CreateUserReq;
import com.rag.user.user.proto.CreateUserResp;
import com.rag.user.user.rpc.internal.model.ErrorCode;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.User;
import com.rag.user.user.rpc.internal.model.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 对应 Go: internal/logic/registerlogic.go
 *
 * 业务流(严格 1:1):
 *   1. 按用户名查 —— 已存在?返回 ALREADY_EXISTS
 *   2. 密码加密 (BCrypt)
 *   3. snowflake 生成 uid
 *   4. INSERT users 表
 *   5. metrics: success / fail_exists / db_error / internal_error 四个 label
 *
 * 与 Go 的差异点:
 *   - Go 用 otel.Tracer 起 span "Action-User-Register"
 *   - Java 端用 Micrometer Observation API 自动从 gRPC interceptor 接 trace,
 *     Logic 不再手动 span(语义保持,代码更轻)
 *   - Go 直接 status.Error(...);Java 抛 BusinessException,由 @GrpcAdvice 转 StatusException
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterLogic {

    private final UserRepository userRepository;
    private final PasswordEncoderService passwordEncoder;
    private final SnowflakeGenerator snowflake;
    private final MeterRegistry meterRegistry;

    /** 对应 Go: metrics.UserRegisterCount(CounterVec, label=result) */
    private Counter counter(String result) {
        return Counter.builder("user_rpc_register_total")
                .tag("result", result)
                .description("Total number of user registrations")
                .register(meterRegistry);
    }

    public CreateUserResp register(CreateUserReq req) {
        // 1. 重名校验
        if (userRepository.findByUsername(req.getUsername()).isPresent()) {
            counter("fail_exists").increment();
            log.warn("register failed: username exists, username={}", req.getUsername());
            throw new BusinessException(ErrorCode.Code.USERNAME_EXISTS);
        }

        // 2. 密码加密
        String hashed;
        try {
            hashed = passwordEncoder.encrypt(req.getPassword());
        } catch (Exception e) {
            counter("internal_error").increment();
            log.error("password encrypt failed", e);
            throw new BusinessException(ErrorCode.Code.PASSWORD_ENCRYPT_ERROR);
        }

        // 3. 雪片取 uid
        long uid;
        try {
            uid = snowflake.nextId();
        } catch (Exception e) {
            counter("internal_error").increment();
            log.error("snowflake id generate failed", e);
            throw new BusinessException(ErrorCode.Code.SNOWFLAKE_ID_ERROR);
        }

        // 4. INSERT
        User newUser = new User();
        newUser.setUid(uid);
        newUser.setUsername(req.getUsername());
        newUser.setPassword(hashed);
        newUser.setEmail(req.getEmail());
        newUser.setStatus(0L);
        newUser.setScore(0);
        Map<String, String> extraInfo = new HashMap<>(req.getExtraInfoMap());
        newUser.setExtraInfo(extraInfo);

        try {
            userRepository.save(newUser);
        } catch (Exception e) {
            counter("db_error").increment();
            log.error("insert user failed, uid={}", uid, e);
            throw new BusinessException(ErrorCode.Code.DB_INSERT_ERROR);
        }

        counter("success").increment();
        log.info("register success, uid={}", uid);
        return CreateUserResp.newBuilder().setUid(uid).build();
    }
}