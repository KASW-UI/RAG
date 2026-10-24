package com.rag.users.admin.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Admin Center HTTP gateway 入口。
 *
 * 对应 Go: service/user/admin/api/admincenter.go 的 func main()。
 *
 * <p>Spring 化拆分：
 * <ul>
 *   <li>{@code serviceName} 由 spring.application.name（= admin-api）自动绑定到 LoggerFactoryBean</li>
 *   <li>HTTP observability filter 由 HttpMiddlewareFilter Bean 自动注册到 Servlet 容器</li>
 *   <li>{@code @EnableDiscoveryClient} 启用 Nacos 自动发现 admin-rpc（grpc.client.admin-rpc.address=discovery:///admin-rpc）</li>
 *   <li>grpc-spring-boot-starter 通过 @GrpcClient 自动注入 AdminServiceBlockingStub</li>
 * </ul>
 *
 * <p>Phase 4 包扫描路径：{@code com.rag.users.admin.api} + {@code com.rag.common}。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.users.admin.api",
        "com.rag.common"
})
@ConfigurationPropertiesScan("com.rag.users.admin.api.config")
@EnableDiscoveryClient
public class AdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApiApplication.class, args);
    }
}
