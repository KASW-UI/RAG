package com.rag.users.admin.rpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AdminService gRPC server 入口。
 *
 * 对应 Go: service/user/admin/rpc/admin.go 的 func main()。
 *
 * <p>Spring 化拆分：
 * <ul>
 *   <li>{@code serviceName} 由 spring.application.name（= admin-rpc）自动绑定到 LoggerFactoryBean</li>
 *   <li>Snowflake 由 SnowflakeBean Bean 在容器启动时自动初始化（workerId 计算、fail-fast），
 *       通过 {@link com.rag.users.admin.rpc.bootstrap.SnowflakeBootstrap} 触发</li>
 *   <li>{@code AdminSeederBootstrap}（{@code @EventListener(ApplicationReadyEvent)}）承载启动期
 *       业务（ensureDefaultAdmin），失败时抛 IllegalStateException 让 SpringApplication 启动失败
 *       （对齐 Go 端 logx.Must 行为）</li>
 *   <li>{@code @EnableDiscoveryClient} 启用 Nacos 自动发现（admin-api 通过 discovery://admin-rpc 寻址）</li>
 *   <li>grpc-spring-boot-starter 通过 @GrpcService 自动注入 AdminServiceImplBase 实现类</li>
 * </ul>
 *
 * <p>Phase 3 包扫描路径：{@code com.rag.users.admin.rpc} + {@code com.rag.common}。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.users.admin.rpc",
        "com.rag.common"
})
@ConfigurationPropertiesScan("com.rag.users.admin.rpc.config")
@EnableDiscoveryClient
public class AdminRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminRpcApplication.class, args);
    }
}
