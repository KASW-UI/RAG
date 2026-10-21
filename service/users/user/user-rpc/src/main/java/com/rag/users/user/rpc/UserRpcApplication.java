package com.rag.users.user.rpc;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
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
        ObservabilityLifecycle.class,  // 容器关闭时回收 RpcInterceptor 的线程池

        // ===== B-1 缓解：超时必须能真正取消在途 SQL =====
        // DeadlineQueryTimeoutInjector：按线程上的 DeadlineState 给每条 SQL 设 setQueryTimeout，
        //   令 pgjdbc 到点向 PG 发 CancelRequest（等价 Go ctx cancel；JDBC 不响应线程 interrupt）。
        // SqlTimeoutStatusTranslator：把随之抛出的 57014 query_canceled 翻成 DEADLINE_EXCEEDED，
        //   否则会被业务层的 catch(Exception) 吞成 INTERNAL → api 层渲染 5001（离 Go 的 1015 更远）。
        // 两者都是 org.apache.ibatis.plugin.Interceptor Bean，MyBatis-Plus 自动配置会
        //   收集容器内全部 Interceptor 并 setPlugins，因此这里只需保证它们**成为 Bean**。
        // ⚠️ 绝不能改成 @Component + 全包扫描 com.rag.common —— 那会拖进 HttpMiddlewareFilter。
        DeadlineQueryTimeoutInjector.class,
        SqlTimeoutStatusTranslator.class
})
public class UserRpcApplication {

    /**
     * <p><b>把 {@link ServerInterceptor} 真正挂到 gRPC server 上</b>（2026-10-21 修，见 issue #71187874）。
     *
     * <p><b>此前完全没接上，而且没有任何报错</b>。{@code RpcServerInterceptorFactory} 的
     * {@code @Bean rpcUnaryServerInterceptor(...)} 确实产出了 {@code ServerInterceptor} Bean
     * （{@code ObservabilityWiringTest#grpcInterceptorBeanExists} 断言它存在，一直是绿的），
     * <b>但 grpc-spring-boot-starter 3.1.0.RELEASE 根本不会自动注册它</b>：
     * <pre>
     *   javap GrpcServerFactoryAutoConfiguration
     *     → nettyGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer,
     *                            List&lt;GrpcServerConfigurer&gt;)      ← 只认 Configurer
     *   javap GrpcServerConfigurer
     *     → interface GrpcServerConfigurer extends Consumer&lt;ServerBuilder&lt;?&gt;&gt;
     *   javap GrpcServerProperties | grep -i intercept
     *     → （无任何 interceptor 字段）
     * </pre>
     * 唯一入口是 {@code GrpcServerConfigurer} —— 在 {@code accept(ServerBuilder)} 里
     * 调 {@code server.intercept(...)}。
     *
     * <p><b>这个缺陷的真实代价（不只是"少了几个 span"）</b>：
     * {@code interceptCall} / {@code onHalfClose} 从没跑过 ⇒
     * <ul>
     *   <li>gRPC 无 deadline 传播 ⇒ {@link com.rag.common.observability.RpcInterceptor.DeadlineState}
     *       在 handler 线程上<b>恒为未设置</b>（{@code remainingMillis()} 返回 {@code Long.MAX_VALUE}）</li>
     *   <li>⇒ 所有 {@code checkDeadline()} 守卫与 B-1 的 {@code setQueryTimeout} 注入全部失效</li>
     *   <li>gRPC 无超时（客户端只能等 api 层的 http-timeout）⇒ 超时场景响应体退化成
     *       {@code text/plain "Request Timeout"}，与 Go 的 {@code {"code":1015}} 不一致</li>
     * </ul>
     * 实测（端到端）：修复前 rpc 收 timeout=PT2S，但 {@code interceptCall} 从未被调用，
     * 客户端 3170ms 才返回（api 层 3s），PG 查询滞留 4300ms。
     *
     * <p><b>Go 出处</b>：{@code service/user/user/rpc/user.go:32,43} 显式注册
     * {@code NewUnaryServerInterceptor}。这里是它的 Spring 等价物。
     */
    @org.springframework.context.annotation.Bean
    public net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer rpcInterceptorConfigurer(
            io.grpc.ServerInterceptor rpcUnaryServerInterceptor) {
        return server -> server.intercept(rpcUnaryServerInterceptor);
    }

    public static void main(String[] args) {
        SpringApplication.run(UserRpcApplication.class, args);
    }
}