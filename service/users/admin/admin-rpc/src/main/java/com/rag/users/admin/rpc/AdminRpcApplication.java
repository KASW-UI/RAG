package com.rag.users.admin.rpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.mybatis.spring.annotation.MapperScan;
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
public class AdminRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminRpcApplication.class, args);
    }
}
