package com.rag.users.user.api.internal.svc;

import com.rag.users.user.api.internal.config.Config;
import com.rag.users.user.proto.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

/**
 * 装配 Config + UserRpc gRPC stub。
 * 对应 Go 端 internal/svc/servicecontext.go：
 * <pre>
 * type ServiceContext struct {
 *     Config                   config.Config
 *     UserRpc                  userservice.UserService
 *     CheckBlacklistMiddleware rest.Middleware
 * }
 * </pre>
 *
 * <p>L4 实现机制差异：
 * <ul>
 *   <li>{@code Config} —— Go 整 Config 持有；Java 侧 Spring 注入同样把整个 {@link Config} bean 注入</li>
 *   <li>{@code UserRpc}（gRPC 客户端 stub）—— Go 用 {@code userservice.NewUserService(zrpc.MustNewClient(...))}；
 *       Java 侧用 devh grpc-client-spring-boot-starter 的 {@code @GrpcClient("user-rpc")}
 *       自动注入 {@link UserServiceGrpc.UserServiceBlockingStub}</li>
 *   <li>{@code CheckBlacklistMiddleware}（含 Redis 客户端）—— Go 把 Redis client 装在 Middleware 结构体里；
 *       Java 侧 {@link com.rag.users.user.api.internal.filter.CheckBlacklistFilter} 是独立 {@code @Component}，
 *       Redis 由 Spring Data Redis 自动装配的 {@code StringRedisTemplate} 直接注入，<b>不</b>走 ServiceContext</li>
 * </ul>
 *
 * <p>L1 外部契约冻结：{@link #userRpc} 始终指向 user-rpc :49210 的 gRPC server（Infrastructure.md §3）。
 */
@Service
public class ServiceContext {

    private final Config config;
    private final UserServiceGrpc.UserServiceBlockingStub userRpc;

    public ServiceContext(Config config,
                          @GrpcClient("user-rpc") UserServiceGrpc.UserServiceBlockingStub userRpc) {
        this.config = config;
        this.userRpc = userRpc;
    }

    public Config getConfig() { return config; }

    public UserServiceGrpc.UserServiceBlockingStub getUserRpc() { return userRpc; }
}