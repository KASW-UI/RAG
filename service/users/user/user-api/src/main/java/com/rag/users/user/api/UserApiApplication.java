package com.rag.users.user.api;

import com.rag.users.user.api.config.UserApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * User Center HTTP gateway 入口。
 *
 * 对应 Go: service/user/user/api/usercenter.go 的 func main()。
 *
 * Spring 化拆分：
 *   - serviceName 由 spring.application.name（= user-api）自动绑定到 LoggerFactoryBean
 *   - HTTP observability filter 由 HttpMiddlewareFilter Bean 自动注册
 *   - 本进程不调 Snowflake.init()（user-api 不生成 UID；user-rpc 进程的 SnowflakeBean 才负责）
 *   - @EnableDiscoveryClient 启用 Nacos 自动发现 user-rpc（grpc.client.user-rpc.address=discovery:///user-rpc）
 *   - grpc-spring-boot-starter 通过 @GrpcClient 自动注入 UserServiceBlockingStub
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.users.user.api",
        "com.rag.common"
})
@EnableConfigurationProperties(UserApiProperties.class)
@EnableDiscoveryClient
public class UserApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApiApplication.class, args);
    }
}