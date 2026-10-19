package com.rag.users.admin.api.client;

import com.rag.users.admin.proto.AdminServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

/**
 * admin-rpc gRPC client —— 持有 AdminServiceBlockingStub。
 *
 * 对应 Go: service/user/admin/api/internal/svc/servicecontext.go 中的 AdminRpc（go-zero 自动生成）。
 *
 * <p>框架职责:
 * <ul>
 *   <li>{@code @GrpcClient("admin-rpc")} 自动注入阻塞式 stub（grpc.client.admin-rpc.address=discovery:///admin-rpc）</li>
 *   <li>devh grpc-client-spring-boot-starter 通过 Nacos 自动发现 admin-rpc 多实例（修 DRIFT-NEW-001）</li>
 *   <li>取代了原 internal.svc.ServiceContext.AdminRpc 字段</li>
 *   <li>所有 Service 类通过 AdminRpcClient.adminRpc() 调用下游 admin-rpc</li>
 * </ul>
 *
 * <p>Phase 4 填肉阶段：可加 retry / circuit breaker / deadline propagation（DRIFT-018）。
 */
@Component
public class AdminRpcClient {

    @GrpcClient("admin-rpc")
    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;

    public AdminServiceGrpc.AdminServiceBlockingStub adminRpc() {
        return adminServiceBlockingStub;
    }
}
