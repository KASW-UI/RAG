package com.rag.users.user.api.service;

import com.rag.common.protection.GoStyleCircuitOpenException;
import com.rag.users.user.api.client.UserRpcClient;
import com.rag.users.user.api.dto.RegisterReq;
import com.rag.users.user.api.dto.RegisterResp;
import com.rag.users.user.api.metrics.UserApiMetrics;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * register 用例 — HTTP DTO ↔ gRPC proto + 3 状态机业务码映射。
 *
 * 对应 Go: service/user/user/api/internal/logic/user/registerlogic.go 的 RegisterLogic。
 *
 * <p>L1 外部契约 + L2 业务语义（3 状态机，1:1 复刻）：
 * <pre>
 *   1. 调 userRpc.register(CreateUserReq{Username, Password, Email, ExtraInfo})
 *      → gRPC codes.Internal      → ErrorServerCommon (5001) + registerRpcError
 *      → gRPC codes.AlreadyExists → ErrorUserExist    (1001) + registerRpcError
 *      → 其它（默认）             → CodeServerBusy    (1015) + registerRpcError
 *
 *   2. 成功
 *      → registerSuccess + 返回 {uid: rpcResp.Uid}
 * </pre>
 *
 * <p><b>L4 注</b>：proto 生成的 RPC 方法名是 {@code register}（不是 {@code createUser}），
 * 因为 protoc 对 {@code rpc Register(CreateUserReq)} 的 method 名取 service 名，Java 端生成 {@code register} 方法。
 */
@Service
public class UserRegisterService {

    private static final Logger log = LoggerFactory.getLogger(UserRegisterService.class);

    private final UserRpcClient userRpcClient;
    private final UserApiMetrics metrics;

    public UserRegisterService(UserRpcClient userRpcClient, UserApiMetrics metrics) {
        this.userRpcClient = userRpcClient;
        this.metrics = metrics;
    }

    public RegisterResp register(RegisterReq req, HttpServletRequest httpRequest) {
        com.rag.users.user.proto.CreateUserReq rpcReq = com.rag.users.user.proto.CreateUserReq.newBuilder()
                .setUsername(req.getUsername() == null ? "" : req.getUsername())
                .setPassword(req.getPassword() == null ? "" : req.getPassword())
                .setEmail(req.getEmail() == null ? "" : req.getEmail())
                .putAllExtraInfo(req.getExtraInfo() == null ? Collections.emptyMap() : req.getExtraInfo())
                .build();

        com.rag.users.user.proto.CreateUserResp rpcResp;
        try {
            rpcResp = userRpcClient.userRpc().register(rpcReq);
        } catch (GoStyleCircuitOpenException e) {
            // Go 出处：registerlogic.go:45-47 的 err != nil 分支 —— 它的 err != nil
            // **捕获一切**，所以熔断抛出的 breaker.ErrServiceUnavailable（普通 error，
            // 不是 gRPC status）也会命中 ⇒ Go 侧的 rpc_error 计数**会**递增。
            // 本 catch 只为对齐这个计数；业务码映射交给 advice 的 handleCircuitOpen
            // （Go 侧 status.FromError ⇒ codes.Unknown ⇒ default ⇒ 1015）。
            metrics.getRegisterRpcError().increment();
            log.error("register RPC error (circuit breaker open), username={}", req.getUsername(), e);
            throw e;
        } catch (StatusRuntimeException e) {
            metrics.getRegisterRpcError().increment();
            log.error("register RPC error, username={}", req.getUsername(), e);
            // 顶层 GlobalExceptionHandler 按 gRPC code 映射业务码（INTERNAL→5001, ALREADY_EXISTS→1001, default→1015）
            throw e;
        }

        metrics.getRegisterSuccess().increment();

        RegisterResp resp = new RegisterResp();
        resp.setUid(rpcResp.getUid());
        return resp;
    }
}
