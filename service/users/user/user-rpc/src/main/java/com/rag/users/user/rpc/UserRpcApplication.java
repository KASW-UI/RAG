package com.rag.users.user.rpc;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.spring.ObservabilityLifecycle;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.common.spring.SnowflakeBean;
import com.rag.common.spring.SnowflakeProperties;
import com.rag.users.user.rpc.config.UserRpcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

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
                        // @ComponentScan：启用组件扫描，默认扫描启动类所在包及其所有子包（本模块自己的 bean）。
                        //                      注意：**不能**把 com.rag.common 加进 scanBasePackages —— 那个包下有
                        //                      HttpMiddlewareFilter（jakarta.servlet.Filter），而 user-rpc 是纯 gRPC
                        //                      进程、故意不引 web 依赖，全包扫描会把 servlet 类拖进来直接启动失败。
                        //                      只需要的 bean 用 @Import 精确引入，见下。
                        // @EnableAutoConfiguration：启用 Spring Boot 的自动配置机制，根据 classpath 上的依赖自动装配 Bean（比如引入 grpc-spring-boot-starter 后自动配置 gRPC server）。
@EnableConfigurationProperties(UserRpcProperties.class) // 让 Spring 识别并注册那些标了 @ConfigurationProperties 的类，把它们变成容器里的 Bean
@EnableDiscoveryClient // Spring Cloud 提供的通用注解，用来启用服务发现客户端的能力。
@MapperScan("com.rag.users.user.rpc.mapper") // application.yaml 注释里声明依赖它，但代码一直没加 → UserMapper/UserAvatarHistoryMapper 注册不上
@Import({
        SnowflakeBean.class,          // workerId 解析（Snowflake 的唯一生成状态由它注入）
        SnowflakeProperties.class,
        LoggerFactoryBean.class,
        // ===== H2 修复：以下 3 个此前漏掉，导致本进程完全没有 gRPC 观测能力 =====
        // RpcServerInterceptorFactory：产出 ServerInterceptor Bean，被 grpc-spring-boot-starter 自动注册。
        //   漏掉它 = 无 gRPC span / 无 deadline 传播 / 无 gRPC 超时，且**不报任何错**。
        //   对齐 Go user/rpc/user.go:32,43 的两段显式注册。
        RpcServerInterceptorFactory.class,
        ObservabilityProperties.class, // 承载 observability.rpc-timeout（Go 侧 2000ms）
        ObservabilityLifecycle.class   // 容器关闭时回收 RpcInterceptor 的线程池
})
public class UserRpcApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserRpcApplication.class, args);
    }
}