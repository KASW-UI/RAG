package com.rag.users.admin.rpc;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
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
 *   <li>{@code AdminSeederBootstrap}（{@code @PostConstruct}）承载启动期
 *       业务（ensureDefaultAdmin），失败时抛 IllegalStateException 让 SpringApplication 启动失败
 *       （对齐 Go 端 logx.Must 行为）。<b>DRIFT-010</b>：原为
 *       {@code @EventListener(ApplicationReadyEvent)}，而该事件在 gRPC server 开始收流量<b>之后</b>
 *       才发布；{@code @PostConstruct} 在 {@code finishBeanFactoryInitialization} 阶段执行，
 *       早于 {@code finishRefresh} 里的 {@code SmartLifecycle.start()}，与 Go 端
 *       {@code NewServiceContext} → {@code ensureDefaultAdmin} → {@code zrpc.MustNewServer} 的次序一致</li>
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
//   本模块对 service/common 的业务调用主要用静态工具 com.rag.common.snowflake.Snowflake；
//   AdminSeederBootstrap 例外地<b>注入</b>了 SnowflakeBean（DRIFT-010 修法）——
//   目的是用 Spring 的 bean 创建顺序保证「workerId 已按配置设定」发生在 seeder 生成 uid 之前，
//   否则 @PostConstruct 阶段会用静态兜底 workerId 产出与后续不同 workerId 的 ID，存在撞号风险。
//   这只是构造器注入一个已在 @Import 列表里的 Bean，不需要扩大 scanBasePackages。
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
        ObservabilityLifecycle.class,      // 容器关闭时回收 RpcInterceptor 线程池

        // ===== B-1 缓解：超时必须能真正取消在途 SQL（与 user-rpc 同一套，两个 @Import 都缺过东西）=====
        // DeadlineQueryTimeoutInjector：按线程上的 DeadlineState 给每条 SQL 设 setQueryTimeout，
        //   令 pgjdbc 到点向 PG 发 CancelRequest（等价 Go ctx cancel；JDBC 不响应线程 interrupt）。
        // SqlTimeoutStatusTranslator：把随之抛出的 57014 query_canceled 翻成 DEADLINE_EXCEEDED，
        //   否则被业务层 catch(Exception) 吞成 INTERNAL → api 层渲染 5001（离 Go 的 1015 更远）。
        DeadlineQueryTimeoutInjector.class,
        SqlTimeoutStatusTranslator.class
})
public class AdminRpcApplication {

    /**
     * <p><b>把 {@link io.grpc.ServerInterceptor} 真正挂到 gRPC server 上</b>
     * （2026-10-21 修，见 issue #71187874）。
     *
     * <p>与 user-rpc 同款缺陷、同款修法。grpc-spring-boot-starter 3.1.0.RELEASE
     * <b>不会自动注册</b>容器里的 {@code ServerInterceptor} Bean，唯一入口是
     * {@code GrpcServerConfigurer}（{@code javap GrpcServerFactoryAutoConfiguration}
     * 显示它只注入 {@code List<GrpcServerConfigurer>}；{@code GrpcServerProperties}
     * 里压根没有 interceptor 字段）。
     *
     * <p><b>代价不只是少了 span</b>：{@code onHalfClose} 没跑 ⇒ handler 线程上
     * {@code DeadlineState} 恒未设置 ⇒ admin-rpc 那 13 处 {@code checkDeadline()}
     * 与 B-1 的 {@code setQueryTimeout} 注入<b>全部失效</b>，gRPC 也没有超时。
     *
     * <p>Go 出处：{@code service/user/admin/rpc/admin.go:33,43} 显式注册
     * {@code NewUnaryServerInterceptor}。
     */
    @org.springframework.context.annotation.Bean
    public net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer rpcInterceptorConfigurer(
            io.grpc.ServerInterceptor rpcUnaryServerInterceptor) {
        return server -> server.intercept(rpcUnaryServerInterceptor);
    }

    public static void main(String[] args) {
        SpringApplication.run(AdminRpcApplication.class, args);
    }
}
