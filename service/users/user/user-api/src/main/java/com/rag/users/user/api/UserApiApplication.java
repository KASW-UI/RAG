package com.rag.users.user.api;

import com.rag.users.user.api.internal.config.Config;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * User Center HTTP gateway 入口
 * 对应 Go 端 service/user/user/api/usercenter.go 的 func main()
 *
 * <p>Spring 化（业务模块迁移）：
 * <ul>
 *   <li>{@code serviceName} 由 {@code spring.application.name}（= user-api）自动绑定
 *       到 {@code LoggerFactoryBean} Bean —— 无需手写 {@code Logger.init("user-api")}</li>
 *   <li>HTTP observability filter 由 {@code HttpMiddlewareFilter} Bean 自动注册到 Servlet 容器
 *       —— 无需手写 {@code @Bean FilterRegistrationBean}</li>
 *   <li>本进程不调 {@code Snowflake.init()}（user-api 不生成 UID）；user-rpc 进程的
 *       {@code SnowflakeBean} 才负责 Snowflake 初始化</li>
 * </ul>
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.users.user.api",
        "com.rag.common"
})
@EnableConfigurationProperties(Config.class)
@EnableDiscoveryClient
public class UserApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApiApplication.class, args);
    }
}