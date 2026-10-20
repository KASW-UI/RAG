package com.rag.users.admin.rpc;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.spring.ObservabilityLifecycle;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.common.spring.SnowflakeBean;
import com.rag.common.spring.SnowflakeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

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
 *   <li>{@code @MapperScan} 显式注册 mapper 接口；没有它 AdminMapper / AdminInviteMapper /
 *       UserMapper 都不会成为 bean</li>
 *   <li>{@code @EnableDiscoveryClient} 启用 Nacos 自动发现（admin-api 通过 discovery://admin-rpc 寻址）</li>
 *   <li>grpc-spring-boot-starter 通过 @GrpcService 自动注入 AdminServiceImplBase 实现类</li>
 * </ul>
 *
 * <p>包扫描范围：只扫 {@code com.rag.users.admin.rpc} 本模块。
 */
@SpringBootApplication // 只扫本模块。com.rag.common 不能进 scanBasePackages：
//   那个包下有 HttpMiddlewareFilter（jakarta.servlet.Filter），而 admin-rpc 是纯 gRPC 进程、
//   pom 里刻意不引 servlet 依赖，全包扫描会把 servlet 类拖进来直接启动失败：
//     java.io.FileNotFoundException: class path resource [jakarta/servlet/Filter.class] cannot be opened
//   本模块对 service/common 只用静态工具 com.rag.common.snowflake.Snowflake，
//   不注入其中任何 bean，因此无需扩大扫描范围。
@ConfigurationPropertiesScan("com.rag.users.admin.rpc.config")
@MapperScan("com.rag.users.admin.rpc.mapper") // 没有它 AdminMapper/AdminInviteMapper/UserMapper 都不会注册成 bean
@EnableDiscoveryClient
// ===== H2 修复：admin-rpc 此前**完全没有 @Import**，只有 @ConfigurationPropertiesScan("com.rag.users.admin.rpc.config")，
// 而那只能扫本模块 config 包、不碰 com.rag.common —— 于是本类 javadoc 里写的
//   「serviceName 由 spring.application.name 自动绑定到 LoggerFactoryBean」
//   「Snowflake 由 SnowflakeBean Bean 自动初始化」
// 两条<b>都没有对应 Bean 存在</b>：AdminDomainService 调 Snowflake.nextId() 走的是
// Snowflake 的静态兜底路径，snowflake.worker-id 配置项被静默忽略；
// 同时无 gRPC span / 无 deadline / 无 gRPC 超时。对齐 Go admin/rpc/admin.go:33,43 的显式注册。
@Import({
        SnowflakeBean.class,
        SnowflakeProperties.class,
        LoggerFactoryBean.class,
        RpcServerInterceptorFactory.class, // gRPC Unary ServerInterceptor（Go admin.go:43）
        ObservabilityProperties.class,     // 承载 observability.rpc-timeout（Go 侧 2000ms）
        ObservabilityLifecycle.class       // 容器关闭时回收 RpcInterceptor 线程池
})
public class AdminRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminRpcApplication.class, args);
    }
}
