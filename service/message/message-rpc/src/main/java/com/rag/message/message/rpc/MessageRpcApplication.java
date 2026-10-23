package com.rag.message.message.rpc;

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
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

/**
 * message-rpc 启动入口 —— 对应 Go 端 {@code service/message/rpc/message.go} 的 {@code main()}。
 *
 * <p><b>2026-10-22 重写（DRIFT-MSG-003，AGENTS.md §6.6.1）</b>：此前本类是
 * <pre>
 *   @SpringBootApplication(scanBasePackages = {"com.rag.message.message.rpc", "com.rag.common"})
 * </pre>
 * 而<b>没有</b> {@code @Import}、<b>没有</b> {@code GrpcServerConfigurer}，导致
 * {@code observability.rpc-timeout: 2s}（yaml 里配着、注释还解释了「对齐 go-zero 默认 2000ms」）
 * <b>从未生效</b>。两个原因叠加：
 * <ol>
 *   <li>devh {@code grpc-server-spring-boot-starter} 3.1.0.RELEASE <b>从不自动注册</b>
 *       用户提供的 {@code ServerInterceptor} Bean（javap 证据见 §6.5「H2 勘误小节」）；</li>
 *   <li>本类因此也没有 {@code GrpcServerConfigurer} —— 全模块零处
 *       {@code server.intercept(...)}（grep 实证）。</li>
 * </ol>
 * 代价远不止「少了几个 span」：{@code interceptCall} / {@code onHalfClose} 从未执行 ⇒
 * handler 线程上 {@code DeadlineState} 恒未设置 ⇒ {@code checkDeadline()} 守卫与
 * B-1 的 {@code setQueryTimeout} 取消在途 SQL 全部失效，gRPC 慢查询无上界。
 *
 * <p><b>本类的两处对齐改动</b>（与 {@code UserRpcApplication} / {@code AdminRpcApplication} 完全同构）：
 * <ol>
 *   <li>{@code @Import} 精确引入 {@code com.rag.common} 下本模块真正需要的 Bean。
 *       <b>顺带修掉一个潜在问题</b>：原 {@code scanBasePackages} 含 {@code "com.rag.common"}，
 *       而该包下有 {@code HttpMiddlewareFilter}（{@code jakarta.servlet.Filter}）。
 *       user-rpc / admin-rpc 的注释都明写「不能把 com.rag.common 加进 scanBasePackages」——
 *       纯 gRPC 进程全包扫描会把 servlet 类拖进来。</li>
 *   <li>新增 {@link #rpcInterceptorConfigurer} —— 唯一能把 interceptor 真正挂上 server 的入口。</li>
 * </ol>
 *
 * <p><b>⚠️ {@code @MapperScan} 是必填的（2026-10-22 补，DRIFT-MSG-005）</b>：缺它时
 * {@code NotificationMapper} / {@code ConversationMapper} / {@code ConversationMessageMapper}
 * 三个 Bean 全部注册不上，报错是
 * <pre>
 *   No qualifying bean of type '…mapper.NotificationMapper' available
 *   └─ UnsatisfiedDependencyException: Error creating bean 'messageAtomicService'
 * </pre>
 * 同款问题在 {@code UserRpcApplication} 上已经踩过一次，它的 {@code @MapperScan} 注释写着
 * 「application.yaml 注释里声明依赖它，但代码一直没加 → Mapper 注册不上」。
 * <b>本模块此前的 9 条测试全是纯 Mockito 单测、且 difftest 从未纳入 message 域</b>
 * ⇒ 「上下文能不能真起来」从未被执行过 ⇒ 这个缺陷与 DRIFT-MSG-004（Bean 名撞车）
 * 是一对<b>「必须真的启动一次才会暴露」</b>的潜伏问题。
 *
 * <p><b>锁定用例</b>：{@code MessageGrpcInterceptorWiringTest}（3 条）+ {@code MessageTransactionWiringTest}
 * （7 条，真 PG）。防回退断言：{@code server.intercept(...)}、{@code @MapperScan}、{@code @Import}
 * 三行都不许被删。
 */
@SpringBootApplication // 只扫本模块（com.rag.message.message.rpc）；com.rag.common 的 Bean 一律 @Import 精确引入
@ConfigurationPropertiesScan(basePackages = "com.rag.message.message.rpc.config")
@MapperScan("com.rag.message.message.rpc.mapper") // ← 2026-10-22 补，DRIFT-MSG-005：见下方说明
@Import({
        SnowflakeBean.class,               // workerId 解析（Snowflake 的唯一生成状态由它注入）
        SnowflakeProperties.class,
        LoggerFactoryBean.class,           // MessageDomainService 构造注入它

        // ===== gRPC 观测三件套：缺任一都「不报错、但机制全无」=====
        // RpcServerInterceptorFactory：产出 ServerInterceptor Bean。**注意光有它不够** ——
        //   starter 3.1.0 不会自动把它挂到 server 上，必须再由下面的 GrpcServerConfigurer 接一手。
        RpcServerInterceptorFactory.class,
        ObservabilityProperties.class,     // 承载 observability.rpc-timeout（Go 侧 2000ms）
        ObservabilityLifecycle.class,      // 容器关闭时回收 RpcInterceptor 的线程池

        // ===== B-1 缓解：超时必须能真正取消在途 SQL（与 user-rpc / admin-rpc 同一套）=====
        // DeadlineQueryTimeoutInjector：按线程上的 DeadlineState 给每条 SQL 设 setQueryTimeout，
        //   令 pgjdbc 到点向 PG 发 CancelRequest（等价 Go ctx cancel；JDBC 不响应线程 interrupt）。
        // SqlTimeoutStatusTranslator：把随之抛出的 57014 query_canceled 翻成 DEADLINE_EXCEEDED。
        // 两者都是 org.apache.ibatis.plugin.Interceptor Bean，MyBatis-Plus 自动配置会
        //   收集容器内全部 Interceptor 并 setPlugins，因此这里只需保证它们成为 Bean。
        DeadlineQueryTimeoutInjector.class,
        SqlTimeoutStatusTranslator.class,

        // ===== DRIFT-MSG-V-016：服务端熔断 / CPU 降载的配置载体（2026-10-24）=====
        // GoStyleProtectionConfiguration 携带 @EnableConfigurationProperties(GoStyleProtectionProperties)，
        //   两者必须一起 @Import —— 只 Import properties 类不会触发绑定。
        // ⚠️ 为什么 rpc 模块要显式 @Import：本模块**刻意不扫** com.rag.common
        //   （扫了会把 jakarta.servlet.Filter 那一族拖进纯 gRPC 进程），
        //   所以 protection 包里的 @Configuration 不会被扫到 ⇒ properties Bean 不会自动注册。
        //   GoStyleProtectionConfiguration 本身无 @ConditionalOnWebApplication（它只带一个纯 POJO），
        //   非 web 进程里 Import 它是安全的。
        com.rag.common.protection.GoStyleProtectionConfiguration.class
})
public class MessageRpcApplication {

    /**
     * <b>把 {@link io.grpc.ServerInterceptor} 真正挂到 gRPC server 上</b>。
     *
     * <p><b>Go 出处</b>：{@code service/message/rpc/message.go} 的
     * {@code zrpc.MustNewServer(...)} 路径 —— go-zero 的 {@code RpcServer.Start()} 会把
     * {@code UnaryServerInterceptor} 注册进 server，等价 2000ms 的
     * {@code RpcServerConf.Timeout}（yaml 未配 ⇒ 走结构体默认值
     * {@code zrpc/config.go:46 default=2000}）。
     *
     * <p><b>为什么这一行不能删</b>：删掉之后 {@code rpcUnaryServerInterceptor} Bean
     * 依然存在（{@code ctx.getBean(ServerInterceptor.class)} 断言照样是绿的），
     * 但 {@code interceptCall} / {@code onHalfClose} 一次都不会执行 ⇒ 超时、deadline 传播、
     * gRPC span 全部静默消失。<b>这正是 §3.3「断言 Bean 存在 ≠ 断言机制生效」的反面教材。</b>
     */
    @org.springframework.context.annotation.Bean
    public net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer rpcInterceptorConfigurer(
            io.grpc.ServerInterceptor rpcUnaryServerInterceptor,
            com.rag.common.protection.GoStyleProtectionProperties protectionProps) {
        // ⚠️ **顺序在这里被显式钉死，不用 server.intercept(a, b) 的多次调用**。
        //
        // Go 侧的注册序（zrpc/server.go:144-163 + zrpc/internal/server.go:58「append 到尾部」
        // ⇒ 先注册的最外层）：Trace → Recover → Stat → Prometheus → Breaker → Shedding
        //   → Timeout → observability（本仓的观测拦截器是最后加的，因此最内层）
        // ⇒ recover 必须在业务观测拦截器之**外**：否则观测拦截器自身或业务链上的 panic 兜不住。
        //
        // 为什么手工串链而不直接 server.intercept(recover, observability)：
        // io.grpc.ServerBuilder#intercept 每次只收一个，且多次调用的执行顺序是
        // 「added in reverse order」—— 依赖它等于把嵌套关系交给一个容易记错的细节。
        //
        // ⚠️ 2026-10-24（DRIFT-MSG-V-016）：链的定义点已**上提到共享模块**
        //   com.rag.common.protection.GoStyleRpcProtectionChain —— 因为 breaker / shedding
        //   是**框架级**能力（Go 侧在 zrpc 库里），10 个 rpc 组件都要挂同一套链，
        //   顺序若分散在 10 个组件里各写一遍，就是 10 个可能写错的地方。
        //   本仓的链：recover → breaker → shedding → 业务观测拦截器。
        return server -> server.intercept(com.rag.common.protection.GoStyleRpcProtectionChain
                .build(rpcUnaryServerInterceptor, protectionProps));
    }

    public static void main(String[] args) {
        SpringApplication.run(MessageRpcApplication.class, args);
    }
}