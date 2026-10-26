package com.rag.favorite.favorite.rpc;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.spring.ObservabilityLifecycle;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.common.spring.SnowflakeBean;
import com.rag.common.spring.SnowflakeProperties;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.rag.common.protection.GoStyleRpcProtectionChain;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleProtectionConfiguration;

/**
 * favorite-rpc 进程入口 —— 对应 Go 端<b>四个</b> main 合并后的单 JVM：
 * <ol>
 *   <li>{@code rpc/favorite.go}（gRPC server，7 个 RPC）</li>
 *   <li>{@code rpc/cmd/fact-dispatch/main.go}（事实投递，默认关闭）</li>
 *   <li>{@code rpc/cmd/fact-authority/main.go}（权威读，默认关闭）</li>
 *   <li>{@code rpc/cmd/fact-legacy-preflight/main.go}（legacy 只读预检，默认关闭）</li>
 * </ol>
 * 合并依据见 {@code docs/FileTree/favorite/rpc.md} §12.1「四个 Go main → 1 个 Spring Boot JVM」。
 *
 * <h2>⚠️ 五个「不写就静默失效 / 直接起不来」的装配点</h2>
 *
 * <p>这些都是 message-rpc 在 AGENTS.md §6.6 里付出真实代价才补上的
 * （DRIFT-MSG-003 ~ 006），本模块<b>照单继承</b>：
 * <ol>
 *   <li><b>{@code @MapperScan}</b> — 缺它 4 个 Mapper 全部注册不上，
 *       报 {@code No qualifying bean of type '…mapper.FavoriteFolderMapper'}，
 *       上下文直接起不来。</li>
 *   <li><b>{@code @Import} 精确引入 common Bean</b> — 不能用
 *       {@code scanBasePackages={"com.rag.common"}}：那里有 {@code HttpMiddlewareFilter}
 *       （servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来。</li>
 *   <li><b>{@link #rpcInterceptorConfigurer}</b> — devh starter <b>不会</b>自动把用户提供的
 *       {@link ServerInterceptor} 挂到 server 上。没有它，超时 / deadline 传播 / gRPC span
 *       <b>全部静默消失</b>，而 Bean 断言照样是绿的
 *       （AGENTS.md §3.3.3「注解存在 ≠ 机制生效」）。</li>
 *   <li><b>{@code spring-boot-flyway}</b>（在 pom 里）— Boot 4 拆模块后必需。
 *       缺它时<b>启动日志里连一行 Flyway 都没有</b>（不是报错，是根本没加载），
 *       4 张表永不创建 ⇒ {@code FavoriteSchemaGate} 是它的第二道防线。</li>
 *   <li><b>{@code @ConfigurationPropertiesScan}</b> — 没有它
 *       {@code FavoriteRpcProperties}（{@code favorite.*}）不会被绑定，
 *       于是 {@code subjectRefV2Facts} 恒为 false、三个 fact 开关恒为关闭，
 *       <b>且没有任何报错</b>。</li>
 * </ol>
 *
 * <h2>⚠️ 为什么<b>不</b>需要 {@code @EnableScheduling}</h2>
 * <p>like-rpc 需要它是因为 Kafka 的 ticker relay。favorite 侧的事实投递由
 * {@code FavoriteFactDispatchScheduler} 自己起线程（Go 侧是独立进程的
 * {@code for { ... select { case <-ctx.Done() / case <-ticker.C } }}），
 * 不依赖 Spring 的调度器 ⇒ 不引入调度基础设施。
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.rag.favorite.favorite.rpc.config")
@MapperScan("com.rag.favorite.favorite.rpc.mapper")
@Import({
        SnowflakeBean.class,               // folder_id / favorite_id 的雪花生成
        SnowflakeProperties.class,
        LoggerFactoryBean.class,           // 领域服务注入它

        // ===== gRPC 观测三件套 =====
        RpcServerInterceptorFactory.class,  // 产出 ServerInterceptor Bean（还必须配 rpcInterceptorConfigurer）
        ObservabilityProperties.class,      // 承载 observability.rpc-timeout（Go 侧 2000ms）
        ObservabilityLifecycle.class,       // 容器关闭时回收 RpcInterceptor 线程池

        // ===== 超时必须能真正取消在途 SQL =====
        DeadlineQueryTimeoutInjector.class, // 按线程 DeadlineState 给每条 SQL 设 setQueryTimeout
        SqlTimeoutStatusTranslator.class,    // 把 57014 query_canceled 翻成 DEADLINE_EXCEEDED

        // ===== 熔断 / 降载（Go zrpc 框架级能力，每个 rpc server 都自带）=====
        GoStyleProtectionConfiguration.class,  // 熔断/降载链的配置载体（breaker / cpuShedding 的开关与阈值；默认值 = go-zero 的 tag 默认值）

})
public class FavoriteRpcApplication {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteRpcApplication.class);

    /**
     * 把 {@link ServerInterceptor} 真正挂到 gRPC server 上。
     *
     * <p>对应 Go {@code service/favorite/rpc/favorite.go:49}：
     * {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}。
     *
     * <p><b>删掉这行后 {@code ctx.getBean(ServerInterceptor.class)} 断言照样是绿的</b>，
     * 但 {@code interceptCall} / {@code onHalfClose} 一次都不执行 ⇒
     * 超时、deadline 传播、gRPC span 全部静默消失。
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
        SpringApplication.run(FavoriteRpcApplication.class, args);
    }
}