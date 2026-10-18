package com.rag.users.admin.api;

import com.rag.users.admin.api.internal.config.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Admin Center HTTP gateway 入口
 * 对应 Go 端 service/user/admin/api/admincenter.go 的 func main()
 *
 * <p>Spring 化（业务模块迁移）：
 * <ul>
 *   <li>{@code serviceName} 由 {@code spring.application.name}（= admin-api）自动绑定
 *       到 {@code LoggerFactoryBean} Bean —— 无需手写 {@code Logger.init("admin-api")}</li>
 *   <li>HTTP observability filter 由 {@code HttpMiddlewareFilter} Bean 自动注册到 Servlet 容器
 *       （{@code @Order(Ordered.HIGHEST_PRECEDENCE + 5)} 与原 {@code FilterRegistrationBean}
 *       等价）—— 无需手写 {@code @Bean FilterRegistrationBean}</li>
 *   <li>本进程不调 {@code Snowflake.init()}（admin-api 不生成 UID）</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.users.admin.api",
        "com.rag.common"
})
@EnableConfigurationProperties(Config.class)
@EnableDiscoveryClient
public class AdminApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApiApplication.class, args);
    }
}