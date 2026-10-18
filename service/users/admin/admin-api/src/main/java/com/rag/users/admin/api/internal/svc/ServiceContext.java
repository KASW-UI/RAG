package com.rag.users.admin.api.internal.svc;

import com.rag.users.admin.api.internal.config.Config;
import com.rag.users.admin.proto.AdminServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

/**
 * 装配 Config + AdminRpc gRPC stub。
 * 对应 Go 端 internal/svc/servicecontext.go：
 * <pre>
 * type ServiceContext struct {
 *     Config                   config.Config
 *     AdminRpc                 adminservice.AdminService
 *     CheckBlacklistMiddleware rest.Middleware
 * }
 * </pre>
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>{@code Config} —— Go 整 Config 持有；Java 侧 Spring 注入同样把整个 {@link Config} bean 注入</li>
 *   <li>{@code AdminRpc}（gRPC 客户端 stub）—— Go 用 {@code adminservice.NewAdminService(zrpc.MustNewClient(...))}；
 *       Java 侧用 devh grpc-client-spring-boot-starter 的 {@code @GrpcClient("admin-rpc")}
 *       自动注入 {@link AdminServiceGrpc.AdminServiceBlockingStub}</li>
 *   <li>{@code CheckBlacklistMiddleware}（含 Redis 客户端）—— Go 把 Redis client 装在 Middleware 结构体里；
 *       Java 侧 {@link com.rag.users.admin.api.internal.filter.CheckBlacklistFilter} 是独立 {@code @Component}，
 *       Redis 由 Spring Data Redis 自动装配的 {@code StringRedisTemplate} 直接注入，<b>不</b>走 ServiceContext</li>
 * </ul>
 *
 * <p>L1 外部契约冻结：{@link #adminRpc} 始终指向 admin-rpc :49211 的 gRPC server（Infrastructure.md §3）。
 */
@Service
public class ServiceContext {

    private final Config config;
    private final AdminServiceGrpc.AdminServiceBlockingStub adminRpc;

    public ServiceContext(Config config,
                          @GrpcClient("admin-rpc") AdminServiceGrpc.AdminServiceBlockingStub adminRpc) {
        this.config = config;
        this.adminRpc = adminRpc;
    }

    public Config getConfig() { return config; }

    public AdminServiceGrpc.AdminServiceBlockingStub getAdminRpc() { return adminRpc; }
}