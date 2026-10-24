package com.rag.task.task.rpc;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.spring.ObservabilityLifecycle;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.task.task.rpc.config.Jackson2ObjectMapperConfiguration;
import com.rag.task.task.rpc.mq.ArticleLikeProgressSink;
import com.rag.task.task.rpc.mq.TaskArticleCountConsumer;
import com.rag.task.task.rpc.mq.TaskRawDiagnosticConsumer;
import com.rag.task.task.rpc.mq.TaskRawLogRouter;
import com.rag.task.task.rpc.mq.TaskUserCountConsumer;
import com.rag.task.task.rpc.mq.UserLikeProgressSink;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;
import com.rag.common.protection.GoStyleRpcProtectionChain;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleProtectionConfiguration;

/**
 * task-rpc 进程入口 —— 对应 Go 端<b>单个</b> main
 * {@code service/task/rpc/task.go}（该文件把 gRPC server 与 4 条常驻 goroutine 一起拉起）。
 *
 * <p>Go 侧被 {@code task.go:31-34} 启动的四个 goroutine，在本模块里的落点：
 * <table border="1">
 *   <caption>Go goroutine → Java 组件</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr><td>{@code Init.StartTaskKafkaRaw}</td><td>{@link TaskRawDiagnosticConsumer}</td></tr>
 *   <tr><td>{@code Init.StartTaskGoKa}（主组）</td><td>{@link TaskRawLogRouter}</td></tr>
 *   <tr><td>—（子组）</td><td>{@link TaskUserCountConsumer} / {@link TaskArticleCountConsumer}</td></tr>
 *   <tr><td>{@code Init.StartTaskKafkaUserFilter}</td><td>{@link UserLikeProgressSink}</td></tr>
 *   <tr><td>{@code Init.StartTaskKafkaArticleFilter}</td><td>{@link ArticleLikeProgressSink}</td></tr>
 *   <tr><td>{@code //go Init.StartInitReward}</td><td><b>不实现</b>（Go 侧被注释，见下）</td></tr>
 * </table>
 *
 * <h2>⚠️ {@code StartInitReward} 刻意不实现</h2>
 * <p>{@code task.go:35} 的调用被注释，{@code internal/Init/initReward.go} 整个文件是注释，
 * {@code reward.Worker} 依赖的 {@code PointsClient} 在 {@code service_context.go:18-19}
 * 就被注释掉了 ⇒ 线上<b>没有任何奖励消费者</b>。
 * 本模块只保留 {@code XADD} 生产侧（两个 sink 的完成分支），
 * 补消费者会引入对 points 域的依赖，而 points <b>不在本项目范围内</b>（AGENTS.md §1）。
 *
 * <h2>🔴 六个「不写就静默失效 / 直接起不来」的装配点</h2>
 * <p>这些是 message-rpc / like-rpc 在 AGENTS.md §3.3 里付出真实代价才补上的，本模块<b>照单继承</b>：
 * <ol>
 *   <li><b>{@code @MapperScan}</b> — 缺它两个 Mapper 全部注册不上，
 *       报 {@code No qualifying bean of type '…mapper.TaskProgressMapper'}，上下文直接起不来。</li>
 *   <li><b>{@code @Import} 精确引入 common Bean</b> — 不能用
 *       {@code scanBasePackages={"com.rag.common"}}：那里有 {@code HttpMiddlewareFilter}
 *       （servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来。</li>
 *   <li><b>{@link #rpcInterceptorConfigurer}</b> — devh starter <b>不会</b>自动把用户提供的
 *       {@link ServerInterceptor} 挂到 server 上。没有它，超时 / deadline 传播 / gRPC span
 *       <b>全部静默消失</b>，而 Bean 断言照样是绿的。</li>
 *   <li><b>{@code spring-boot-flyway}</b>（在 pom 里）— Boot 4 拆模块后必需。
 *       缺它时<b>启动日志里连一行 Flyway 都没有</b>，两张表永不创建
 *       ⇒ 第一次真正查库才炸。{@code TaskSchemaGate} 是第二道防线。</li>
 *   <li><b>{@code spring-boot-kafka}</b>（在 pom 里）— 同理，缺它则没有
 *       {@code KafkaTemplate} / {@code ConsumerFactory} ⇒ <b>组件起不来</b>。</li>
 *   <li><b>{@code @EnableScheduling}</b> — 两个 sink 的 1 秒刷盘 ticker 靠它。
 *       缺它 {@code @Scheduled} 静默不跑，delta 永远堆积不落库。</li>
 * </ol>
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.rag.task.task.rpc.config")
@MapperScan("com.rag.task.task.rpc.mapper")
@EnableScheduling
@Import({
        LoggerFactoryBean.class,             // 领域服务注入它

        // ===== gRPC 观测三件套 =====
        RpcServerInterceptorFactory.class,  // 产出 ServerInterceptor Bean（还必须配 rpcInterceptorConfigurer）
        ObservabilityProperties.class,      // 承载 observability.rpc-timeout（Go 侧未配 ⇒ 默认 2000ms）
        ObservabilityLifecycle.class,       // 容器关闭时回收 RpcInterceptor 线程池

        // ===== 超时必须能真正取消在途 SQL =====
        DeadlineQueryTimeoutInjector.class, // 按线程 DeadlineState 给每条 SQL 设 setQueryTimeout
        SqlTimeoutStatusTranslator.class,   // 把 57014 query_canceled 翻成 DEADLINE_EXCEEDED

        // ===== Jackson 2 的 ObjectMapper Bean =====
        Jackson2ObjectMapperConfiguration.class, // Boot 4 拆模块后 Jackson 自动配置只产 Jackson 3

        // ⚠️ **这里刻意不 @Import TaskKafkaTopics / TaskSinkProperties**
        //
        // 类上方的 @ConfigurationPropertiesScan(basePackages = "com.rag.task.task.rpc.config")
        // 已经把它们注册成 Bean（bean 名分别是 task.kafka-… / task.definitions-…）。
        // 若这里再 @Import 一次，容器里就会**同时存在两个同类型 Bean**：
        //   NoUniqueBeanDefinitionException: found 2:
        //     com.rag.task.task.rpc.config.TaskSinkProperties,
        //     task.definitions-com.rag.task.task.rpc.config.TaskSinkProperties
        // ⇒ 两个 sink 的构造注入立刻失败 ⇒ **整个组件起不来**。
        //
        // ⚠️ 这个缺陷是靠 TaskProgressUpsertParityTest 那个「装配 Spring 上下文」的测试抓到的；
        // 纯 Mockito 单测全绿、编译通过、业务测试全绿 —— 典型的
        // 「跑绿 ≠ 能启动」（AGENTS.md §3.3）。

        // ===== 熔断 / 降载（Go zrpc 框架级能力，每个 rpc server 都自带）=====
        GoStyleProtectionConfiguration.class,  // 熔断/降载链的配置载体（breaker / cpuShedding 的开关与阈值；默认值 = go-zero 的 tag 默认值）

})
public class TaskRpcApplication {

    private static final Logger LOG = LoggerFactory.getLogger(TaskRpcApplication.class);

    /**
     * 把 {@link ServerInterceptor} 真正挂到 gRPC server 上。
     *
     * <p>对应 Go {@code task.go:44}：
     * {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}。
     *
     * <p><b>删掉这行后 {@code ctx.getBean(ServerInterceptor.class)} 断言照样是绿的</b>，
     * 但 {@code interceptCall} / {@code onHalfClose} 一次都不执行
     * ⇒ 超时、deadline 传播、gRPC span 全部静默消失。
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

    /**
     * 覆盖 Spring Boot 自动配置的 Kafka 监听容器工厂，装上本域的错误契约。
     *
     * <p><b>同名 Bean 会替换自动配置的那个</b>（{@code @ConditionalOnMissingBean} 语义），
     * 所以这里必须自己把 {@code consumerFactory} 装回去。
     *
     * <p>两处关键设置：
     * <ol>
     *   <li>{@code AckMode.RECORD} —— listener 正常返回后才提交位点。
     *       这是 Go kq「{@code Consume} 返回 error ⇒ 不确认」的等价载体
     *       （{@code go-queue/kq/queue.go:235-238}）。</li>
     *   <li>{@link #noCommitErrorHandler()} —— 只记日志、<b>不 seek、不提交</b>，
     *       让失败消息在重启后重投。</li>
     * </ol>
     *
     * <h2>⚠️ 为什么不做进程内重试（以及一处必须说明的差异）</h2>
     * <p>Go 的 kq 在 {@code Consume} 返回 error 后<b>没有进程内重试循环</b>。
     * 若在 Java 侧加 {@code FixedBackOff} 做 N 次进程内重试，那是<b>新增行为</b>
     * （改变重试节奏与日志量）。所以错误处理器只记日志、不 seek、不提交。
     *
     * <p>⚠️ <b>但要诚实说明一处差异</b>：Go 用的 segmentio/kafka-go Reader 带
     * {@code CommitInterval: 1s}（{@code go-queue/kq/queue.go:125,151}），
     * 它会<b>周期性自动提交已 fetch 的位点</b>，因此 Go 那句
     * 「error ⇒ 不 CommitMessages」在实践中<b>会被自动提交抵消</b>（约 1 秒后照样提交）。
     * Java 侧 {@code enable-auto-commit: false} + {@code AckMode.RECORD}
     * 是<b>严格更强</b>的「失败不提交」。
     * ⇒ 失败消息在 Java 侧的重投次数可能多于 Go 侧。
     * 这是本仓 like-rpc / article-rpc 已统一采纳的约定
     * （取 Go 代码声明的契约，而非 kafka-go 的实现副作用），此处保持一致。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(noCommitErrorHandler());
        return factory;
    }

    /**
     * 只记日志、不提交位点的错误处理器。
     *
     * <p>⚠️ 不用默认 recoverer：默认 {@code DefaultErrorHandler} 的行为是
     * 在重试耗尽后<b>提交该条位点</b>（等价于丢弃消息）。
     * Go 侧这些失败消息是"不确认、等重投"的（kq 的 errorHandler 默认只打日志）。
     */
    private static CommonErrorHandler noCommitErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> LOG.error("task consumer failed (offset not committed, will redeliver): "
                        + "topic={} partition={} offset={} key={} err={}",
                        record.topic(), record.partition(), record.offset(), record.key(),
                        ex.getMessage()),
                // ⚠️ 不做进程内重试：Go kq 没有重试循环，重试次数必须为 0，
                //    否则是新增行为。用 maxAttempts=1 表达"第一次失败就直接交给 errorHandler"。
                new org.springframework.util.backoff.FixedBackOff(1000L, 1L));
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(false);
        // ⚠️ 关键：不因失败而 seek 到当前位点（那等于把失败消息标记为已处理 ⇒ 丢消息）
        handler.setSeekAfterError(false);
        return handler;
    }

    public static void main(String[] args) {
        SpringApplication.run(TaskRpcApplication.class, args);
    }
}