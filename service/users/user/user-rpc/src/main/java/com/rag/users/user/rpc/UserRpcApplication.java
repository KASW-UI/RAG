package com.rag.users.user.rpc;

import com.rag.users.user.rpc.internal.config.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * UserService gRPC server 入口
 * 对应 Go 端 service/user/user/rpc/user.go 的 func main()
 *
 * <p>Spring 化（业务模块迁移）：serviceName 由 spring.application.name 自动绑定
 * （{@code LoggerFactoryBean} Bean），Snowflake 由 {@code SnowflakeBean} Bean 在容器启动时
 * 自动初始化（workerId 计算、fail-fast）。本类无需手写静态 init。
 *
 * <ul>
 *   <li>{@code @SpringBootApplication} 启用 Spring Boot auto-config</li>
 *   <li>grpc-server-spring-boot-starter 通过 {@code @GrpcService} 自动起 gRPC server
 *       （{@code UserServiceServer} 已实装）</li>
 *   <li>{@code @EnableConfigurationProperties(Config.class)} 启用 user-rpc.* 配置段绑定到 Config
 *       （对应 Go 端 conf.MustLoad）</li>
 *   <li>{@code @EnableDiscoveryClient} 注册到 Nacos，让 user-api 通过 discovery://user-rpc 发现</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(Config.class)
@EnableDiscoveryClient
public class UserRpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserRpcApplication.class, args);
    }
}