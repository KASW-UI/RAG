package com.rag.users.admin.rpc;

import com.rag.users.admin.rpc.internal.config.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AdminService gRPC server 入口
 * 对应 Go 端 service/user/admin/rpc/admin.go 的 func main()
 *
 * <p>Spring 化（业务模块迁移）：serviceName 由 spring.application.name 自动绑定
 * （{@code LoggerFactoryBean} Bean），Snowflake 由 {@code SnowflakeBean} Bean 在容器启动时
 * 自动初始化（workerId 计算、fail-fast）。本类无需手写静态 init。
 *
 * <p>{@code AdminBootstrap}（{@code @EventListener(ApplicationReadyEvent)}）承载启动期业务
 * （ensureDefaultAdmin），失败时抛 IllegalStateException 让 SpringApplication 启动失败
 * （对齐 Go 端 logx.Must 行为）。
 */
@SpringBootApplication
@EnableConfigurationProperties(Config.class)
@EnableDiscoveryClient
public class AdminRpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdminRpcApplication.class, args);
    }
}