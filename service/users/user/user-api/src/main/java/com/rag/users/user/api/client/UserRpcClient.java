package com.rag.users.user.api.client;

import com.rag.users.user.proto.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * user-rpc gRPC client —— 持有 UserServiceBlockingStub。
 *
 * 对应 Go: service/user/user/api/internal/svc/servicecontext.go 中的 UserRpc（go-zero 自动生成）。
 *
 * 框架职责:
 *   - @GrpcClient("user-rpc") 自动注入阻塞式 stub（grpc.client.user-rpc.address=discovery:///user-rpc）
 *   - devh grpc-client-spring-boot-starter 通过 Nacos 自动发现 user-rpc 多实例（修 DRIFT-NEW-001）
 *   - 取代了原 internal/internal.svc.ServiceContext.UserRpc 字段
 *   - 所有 Service 类通过 UserRpcClient 调用下游 user-rpc
 *
 * Phase 2b 填肉阶段：可加 retry / circuit breaker / deadline propagation（DRIFT-018）
 */
@Component
public class UserRpcClient {

    @GrpcClient("user-rpc")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    public UserServiceGrpc.UserServiceBlockingStub userRpc() {
        return userServiceBlockingStub;
    }
}