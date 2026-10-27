package com.rag.follow.follow.rpc;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.spring.ObservabilityLifecycle;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.rag.common.protection.GoStyleRpcProtectionChain;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleProtectionConfiguration;

/**
 * follow-rpc 进程入口 —— 对应 Go 端 {@code service/follow/rpc/follow.go} 的 {@code main()}。
 *
 * <p><b>Go 侧 main 的五步，本类逐条对应</b>：
 * <ol>
 *   <li>{@code metrics.InitMetrics()} → 由 {@code FollowRpcMetrics} 构造时注册
 *       （Micrometer 自动注册到 {@code MeterRegistry}，对应 {@code prometheus.MustRegister}）；</li>
 *   <li>{@code conf.MustLoad(*configFile, &c)} + {@code logx.MustSetup} +
 *       {@code logger.Init(c.Name)} → Spring 的 {@code application.yml} + {@code LoggerFactoryBean}；</li>
 *   <li>{@code svc.NewServiceContext(c)}（建 PG 连接 + UserRpc client）→
 *       Spring 容器的 {@code DataSourceAutoConfiguration} + {@code UserRpcClient}；</li>
 *   <li>{@code db.AutoMigrate(...)} → {@code FlywayAutoConfiguration}（见下方第 5 点）；</li>
 *   <li>{@code zrpc.MustNewServer(...)} + {@code s.AddUnaryInterceptors(...)} +
 *       {@code reflection.Register} → devh starter + 下方 {@link #rpcInterceptorConfigurer}。</li>
 * </ol>
 *
 * <h2>⚠️ 五个「不写就静默失效 / 直接起不来」的装配点</h2>
 *
 * <p>这些都是本仓已付出真实代价才补上的（见 AGENTS.md §3.3 与 message-rpc 的 DRIFT-MSG-003~006、
 * like-rpc 的 DRIFT-LIKE-013/014），本模块<b>照单继承</b>：
 * <ol>
 *   <li><b>{@code @MapperScan}</b> — 缺它 {@code FollowRelationMapper} /
 *       {@code BlockRelationMapper} 全部注册不上，报
 *       {@code No qualifying bean of type '…mapper.FollowRelationMapper'}，上下文直接起不来。</li>
 *   <li><b>{@code @Import} 精确引入 common Bean</b> — 不能用
 *       {@code scanBasePackages={"com.rag.common"}}：那里有 {@code HttpMiddlewareFilter}
 *       （servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来。</li>
 *   <li><b>{@link #rpcInterceptorConfigurer}</b> — devh starter 3.1.0 <b>不会</b>自动把
 *       用户提供的 {@link ServerInterceptor} 挂到 server 上。没有它，超时 / deadline 传播 /
 *       gRPC span <b>全部静默消失</b>，而「Bean 存在」的断言照样是绿的。
 *       这正是 AGENTS.md §3.3「断言 Bean 存在 ≠ 断言机制生效」的反面教材。</li>
 *   <li><b>{@code spring-boot-flyway}</b>（在 pom 里）— Boot 4 拆模块后必需。
 *       缺它时<b>启动日志里连一行 Flyway 都没有</b>（不是报错，是根本没加载），
 *       {@code follow_relation} / {@code block_relation} 永不创建 ⇒ 第一次真正执行 SQL 才炸。</li>
 *   <li><b>{@code MybatisPlusOrderingBridgeAutoConfiguration}</b>（<b>2026-10-27 起由共享层提供</b>
 *       —— 本模块那份域内副本及其 {@code AutoConfiguration.imports} 注册行<b>已删除</b>，
 *       改由 {@code com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration}
 *       经 {@code service/common} 的 {@code AutoConfiguration.imports} 注册；
 *       Boot 的 {@code ImportCandidates} 用 {@code ClassLoader.getResources}（复数）
 *       读取 classpath 上<b>全部</b>同名资源 ⇒ 共享层那份对本模块同样生效）
 *       —— Boot 4 改了 {@code DataSourceAutoConfiguration}
 *       的包名，MyBatis-Plus 声明的排序引用<b>静默悬空</b> ⇒ 它先于 DataSource 求值 ⇒
 *       找不到 DataSource ⇒ 整个 MP 自动配置不生效 ⇒ mapper 报
 *       {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。</li>
 * </ol>
 *
 * <p><b>不需要 {@code @EnableScheduling}</b>：follow 域没有任何定时任务
 * （Go 侧 {@code follow.go} 也没有 ticker / cron），不像 like-rpc 的 outbox relay。
 */
@SpringBootApplication // 只扫本模块（com.rag.follow.follow.rpc）；com.rag.common 的 Bean 一律 @Import 精确引入
@ConfigurationPropertiesScan(basePackages = "com.rag.follow.follow.rpc.config")
@MapperScan("com.rag.follow.follow.rpc.mapper")
@Import({
        LoggerFactoryBean.class,            // FollowDomainService / FollowRecommendationService 注入它做结构化日志

        // ===== gRPC 观测三件套：缺任一都「不报错、但机制全无」=====
        // RpcServerInterceptorFactory：产出 ServerInterceptor Bean。
        //   **注意光有它不够** —— starter 3.1.0 不会自动把它挂到 server 上，
        //   必须再由下面的 GrpcServerConfigurer 接一手。
        RpcServerInterceptorFactory.class,
        ObservabilityProperties.class,      // 承载 observability.rpc-timeout（Go 侧 2000ms）
        ObservabilityLifecycle.class,       // 容器关闭时回收 RpcInterceptor 的线程池

        // ===== 超时必须能真正取消在途 SQL =====
        // DeadlineQueryTimeoutInjector：按线程上的 DeadlineState 给每条 SQL 设 setQueryTimeout，
        //   令 pgjdbc 到点向 PG 发 CancelRequest（等价 Go ctx cancel；JDBC 不响应线程 interrupt）。
        //   ⚠️ 对 follow 域尤其重要：GetRecommendations 的 BFS 会为每一层每个源用户发一次查询，
        //   没有这个兜底，一个慢查询就能让整个推荐请求无限拖下去。
        // SqlTimeoutStatusTranslator：把随之抛出的 57014 query_canceled 翻成 DEADLINE_EXCEEDED。
        DeadlineQueryTimeoutInjector.class,
        SqlTimeoutStatusTranslator.class,

        // ===== 熔断 / 降载（Go zrpc 框架级能力，每个 rpc server 都自带）=====
        GoStyleProtectionConfiguration.class,  // 熔断/降载链的配置载体（breaker / cpuShedding 的开关与阈值；默认值 = go-zero 的 tag 默认值）

})
public class FollowRpcApplication {

    /**
     * <b>把 {@link ServerInterceptor} 真正挂到 gRPC server 上</b>。
     *
     * <p><b>Go 出处</b>：{@code service/follow/rpc/follow.go:44}
     * {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}，
     * 以及 {@code follow.go:34} 的
     * {@code observability.DisableNativeRpcTimeout(&c.RpcServerConf)} ——
     * 后者把 go-zero 自带的 2 秒超时关掉、改由自建 interceptor 施加，
     * 所以 yaml 里的 {@code observability.rpc-timeout: 2s} 是<b>唯一</b>生效的超时来源。
     *
     * <p><b>为什么这一行不能删</b>：删掉之后 {@code rpcUnaryServerInterceptor} Bean 依然存在
     * （{@code ctx.getBean(ServerInterceptor.class)} 断言照样绿），
     * 但 {@code interceptCall} / {@code onHalfClose} 一次都不执行 ⇒
     * 超时、deadline 传播、gRPC span 全部静默消失，且
     * {@code DeadlineQueryTimeoutInjector} 因拿不到 DeadlineState 而不给 SQL 设 queryTimeout。
     */
    @Bean
    public GrpcServerConfigurer rpcInterceptorConfigurer(ServerInterceptor rpcUnaryServerInterceptor,
            GoStyleProtectionProperties protectionProps) {
        // ⚠️ DRIFT-MSG-V-026（2026-10-24）：链的定义点在**共享模块**
        //   com.rag.common.protection.GoStyleRpcProtectionChain ——
        //   熔断 / 降载在 Go 侧是 **zrpc 框架级**能力（每个 rpc server 都自带），
        //   10 个 rpc 组件挂的是同一条链，顺序若分散在各处各写一遍，就是 10 个可能写错的地方。
        //   本仓的链：recover → breaker → shedding → 业务观测拦截器。
        return server -> server.intercept(GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps));
    }

    public static void main(String[] args) {
        SpringApplication.run(FollowRpcApplication.class, args);
    }
}