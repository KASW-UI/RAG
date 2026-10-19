package com.rag.users.user.rpc;

import com.rag.users.user.rpc.config.UserRpcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * UserService gRPC server 入口。
 *
 * 对应 Go: service/user/user/rpc/user.go 的 func main()。
 *
 * <p>Spring 化拆分（业务模块迁移）：
 * <ul>
 *   <li>serviceName 由 spring.application.name 自动绑定（{@code LoggerFactoryBean} Bean）</li>
 *   <li>Snowflake 初始化由 {@code SnowflakeBootstrap} 在 ApplicationReadyEvent 时触发</li>
 *   <li>Nacos 服务注册由 {@code NacosRegistration} 在 ApplicationReadyEvent 时触发</li>
 *   <li>{@code @EnableConfigurationProperties(UserRpcProperties.class)} 把 user-rpc.* 段绑定到 UserRpcProperties</li>
 *   <li>{@code @EnableDiscoveryClient} 启用 Nacos 自动注册</li>
 *   <li>grpc-spring-boot-starter 通过 {@code @GrpcService} 自动起 gRPC server（UserServiceGrpcImpl 已实装）</li>
 * </ul>
 */
@SpringBootApplication // 等价于 @SpringBootConfiguration、@EnableAutoConfiguration、@ComponentScan 三者的组合
                        // @ComponentScan：启用组件扫描，默认扫描启动类所在包及其所有子包，把 @Component、@Service、@Repository、@Controller 等注解的类注册为 Bean。
                        // @EnableAutoConfiguration：启用 Spring Boot 的自动配置机制，根据 classpath 上的依赖自动装配 Bean（比如引入 grpc-spring-boot-starter 后自动配置 gRPC server）。
@EnableConfigurationProperties(UserRpcProperties.class) // 让 Spring 识别并注册那些标了 @ConfigurationProperties 的类，把它们变成容器里的 Bean
@EnableDiscoveryClient // Spring Cloud 提供的通用注解，用来启用服务发现客户端的能力。
public class UserRpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserRpcApplication.class, args);
    }
}