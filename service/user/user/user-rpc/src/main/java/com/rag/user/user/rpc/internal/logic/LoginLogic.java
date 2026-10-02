package com.rag.user.user.rpc.internal.logic;

import com.rag.user.user.proto.LoginReq;
import com.rag.user.user.proto.LoginResp;
import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import com.rag.user.user.rpc.internal.model.User;
import com.rag.user.user.rpc.internal.model.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对应 Go: internal/logic/loginlogic.go
 *
 * 关键点(严格 1:1):
 *   - Go 端 LoginResp.Status 是 uint64,不是 int64!这是 proto 字段号对应上的关键。
 *     proto 文件里我们写成 uint64 status = 2,Java 端 builder.setStatus(long) 即对。
 *   - 登录业务的三种返回码:
 *       Status=0 成功(返回 uid)
 *       Status=1 用户名/密码错 或 用户不存在(注意 Go 把这两种情况合并为同一种状态!)
 *       Status=2 用户被封号(Status==1)
 *   - 与 Register/Delete 不同,Login **不抛 gRPC status error**,而是用业务字段表达"失败原因"——
 *     这是 RPC 业务接口的设计选择,1:1 复刻
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogic {

    private final UserRepository userRepository;
    private final PasswordEncoderService passwordEncoder;
    private final MeterRegistry meterRegistry;

    /** 对应 Go: metrics.UserLoginCount,label=result */
    private Counter counter(String result) {
        return Counter.builder("user_rpc_login_total")
                .tag("result", result)
                .description("Total number of user logins")
                .register(meterRegistry);
    }

    public LoginResp login(LoginReq req) {
        User user = userRepository.findByUsername(req.getUsername()).orElse(null);

        // 用户不存在 → Status=1,1:1 复刻 Go 的 if err == model.ErrorNotFound 分支
        if (user == null) {
            counter("user_not_found").increment();
            log.info("login failed: user not found, username={}", req.getUsername());
            return LoginResp.newBuilder().setStatus(1L).build();
        }

        // 密码错 → Status=1,1:1 复刻 Go 的 if !correct 分支
        if (!passwordEncoder.matches(user.getPassword(), req.getPassword())) {
            counter("internal_error").increment();
            log.info("login failed: username or password incorrect, username={}", req.getUsername());
            return LoginResp.newBuilder().setStatus(1L).build();
        }

        // 封号 → Status=2,1:1 复刻 Go 的 if user.Status == 1 分支
        if (user.getStatus() != null && user.getStatus() == 1L) {
            log.info("login failed: user banned, username={}", req.getUsername());
            return LoginResp.newBuilder().setStatus(2L).build();
        }

        counter("success").increment();
        log.info("login success, username={}, uid={}", req.getUsername(), user.getUid());

        // 成功:返回 uid + Status=0
        // proto 里 status 字段是 uint64,但 Java builder 不区分 int/uint,setStatus(long) 即可
        return LoginResp.newBuilder()
                .setUid(user.getUid())
                .setStatus(0L)
                .build();
    }
}