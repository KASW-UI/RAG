package com.rag.like.like.rpc;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.spring.ObservabilityLifecycle;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.common.spring.SnowflakeBean;
import com.rag.common.spring.SnowflakeProperties;
import com.rag.like.like.rpc.config.Jackson2ObjectMapperConfiguration;
import com.rag.like.like.rpc.config.LikeKafkaTopics;
import com.rag.like.like.rpc.mq.LikeUpdateKafkaListener;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.mybatis.spring.annotation.MapperScan;

/**
 * like-rpc 进程入口 —— 对应 Go 端<b>四个</b> main 合并后的单 JVM：
 * <ol>
 *   <li>{@code rpc/like.go}（gRPC server）</li>
 *   <li>{@code rpc/internal/mq/mq.go}（Kafka 消费 + outbox relay）</li>
 *   <li>{@code rpc/cmd/fact-dispatch/main.go}（事实投递，默认关闭）</li>
 *   <li>{@code rpc/cmd/fact-authority/main.go}（权威读，默认关闭）</li>
 * </ol>
 *
 * <h2>⚠️ 五个「不写就静默失效 / 直接起不来」的装配点</h2>
 *
 * <p>这些都是 message-rpc 在 AGENTS.md §6.6 里付出真实代价才补上的（DRIFT-MSG-003 ~ 006），
 * 本模块<b>照单继承</b>。
 * ⚠️ 2026-10-23 修正错误断言 F：原文写「<b>且每一条都有对应的 wiring 测试锁死</b>」——
 * 该说法<b>不成立</b>。真实的装配层回归网是
 * {@code LikeRpcSpringContextWiringTest}（<b>9 条</b>，含真 PG Testcontainers），
 * 它覆盖的是第 1 条（{@code @MapperScan} / {@code SqlSessionFactory} / 5 个 Mapper Bean）、
 * Jackson 2 Bean、KafkaTemplate、{@code @KafkaListener} 进 registry、
 * 序列化行为（未知字段忽略 / number→string 报错），
 * 以及第 5 条（{@code @EnableScheduling} —— 见该类里「outbox relay 的 ticker 真的执行过」两条）。
 * <br>🔴 <b>2026-10-23 第五轮更正（原文写「第 3 条与第 5 条没有对应测试」）</b>：
 * 第 5 条<b>已有</b>守门测试了（同一个 wiring 测试类，断言的是
 * 「{@code ScheduledTaskHolder} 真的持有 relay 的任务」+「那个任务真的执行过」，
 * <b>不是</b>「Bean 存在」）；
 * <b>仍然没有</b>对应测试的是<b>第 3 条</b>（{@link #rpcInterceptorConfigurer}）：
 * 删掉它之后 {@code getBean(ServerInterceptor.class)} 断言<b>照样是绿的</b>，
 * 因为 Bean 仍在，只是没被挂进 gRPC 调用链。
 * ⇒ 补第 3 条需验「真挂上调用链」（要能观测到拦截器的**行为**，
 * 而不只是链里有它）—— 属已知测试缺口，见 DRIFT 台账 L-U11。
 * <ol>
 *   <li><b>{@code @MapperScan}</b> — 缺它 5 个 Mapper 全部注册不上，
 *       报 {@code No qualifying bean of type '…mapper.LikeRecordMapper'}，
 *       上下文直接起不来。✅ {@code LikeRpcSpringContextWiringTest} 已覆盖。</li>
 *   <li><b>{@code @Import} 精确引入 common Bean</b> — 不能用
 *       {@code scanBasePackages={"com.rag.common"}}: 那里有 {@code HttpMiddlewareFilter}
 *       （servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来。</li>
 *   <li><b>{@link #rpcInterceptorConfigurer}</b> — devh starter <b>不会</b>自动把用户提供的
 *       {@link ServerInterceptor} 挂到 server 上。没有它，超时 / deadline 传播 / gRPC span
 *       <b>全部静默消失</b>，而 Bean 断言照样是绿的。❌ <b>仍无守门测试</b>。</li>
 *   <li><b>{@code spring-boot-flyway}</b>（在 pom 里）— Boot 4 拆模块后必需。
 *       缺它时<b>启动日志里连一行 Flyway 都没有</b>（不是报错，是根本没加载），
 *       5 张表永不创建 ⇒ 第一次真正执行 SQL 才炸。
 *       {@code LikeSchemaGate} 是它的第二道防线。</li>
 *   <li><b>{@code @EnableScheduling}</b> — outbox relay 的 1 秒 ticker 靠它。
 *       缺它 relay 静默不跑，事件永远积压。✅ <b>2026-10-23 第五轮已补守门测试</b>
 *       （断言「任务进了调度器」+「任务真的执行过」）。</li>
 * </ol>
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.rag.like.like.rpc.config")
@MapperScan("com.rag.like.like.rpc.mapper")
@EnableScheduling
@Import({
        SnowflakeBean.class,               // workerId 解析（Kafka 消息 msg_id / outbox event_id 用它）
        SnowflakeProperties.class,
        LoggerFactoryBean.class,           // 领域服务注入它

        // ===== gRPC 观测三件套 =====
        RpcServerInterceptorFactory.class,  // 产出 ServerInterceptor Bean（还必须配 rpcInterceptorConfigurer）
        ObservabilityProperties.class,      // 承载 observability.rpc-timeout（Go 侧 2000ms）
        ObservabilityLifecycle.class,       // 容器关闭时回收 RpcInterceptor 线程池

        // ===== 超时必须能真正取消在途 SQL =====
        DeadlineQueryTimeoutInjector.class, // 按线程 DeadlineState 给每条 SQL 设 setQueryTimeout
        SqlTimeoutStatusTranslator.class,   // 把 57014 query_canceled 翻成 DEADLINE_EXCEEDED

        // ===== Jackson 2 的 ObjectMapper Bean =====
        Jackson2ObjectMapperConfiguration.class // Boot 4 拆模块后 Jackson 自动配置只产 Jackson 3，缺它上下文起不来
})
public class LikeRpcApplication {

    private static final Logger LOG = LoggerFactory.getLogger(LikeRpcApplication.class);

    /**
     * 把 {@link ServerInterceptor} 真正挂到 gRPC server 上。
     *
     * <p>对应 Go {@code like.go:41}：
     * {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, SlowThreshold()))}。
     *
     * <p><b>删掉这行后 {@code ctx.getBean(ServerInterceptor.class)} 断言照样是绿的</b>，
     * 但 {@code interceptCall} / {@code onHalfClose} 一次都不执行 ⇒
     * 超时、deadline 传播、gRPC span 全部静默消失。
     * 锁死用例：⚠️ <b>目前没有</b>（错误断言 F：原文此处写
     * {@code LikeGrpcInterceptorWiringTest}，该测试类<b>全仓不存在</b>）。
     * {@code LikeRpcSpringContextWiringTest} 只断言 Bean 存在，抓不到「Bean 在但没挂进调用链」。
     */
    @Bean
    public GrpcServerConfigurer rpcInterceptorConfigurer(ServerInterceptor rpcUnaryServerInterceptor) {
        return server -> server.intercept(rpcUnaryServerInterceptor);
    }

    /**
     * 覆盖 Spring Boot 自动配置的 Kafka 监听容器工厂，装上 like 域的错误契约。
     *
     * <p><b>同名 Bean 会替换自动配置的那个</b>（{@code @ConditionalOnMissingBean} 语义），
     * 所以这里必须自己把 {@code consumerFactory} 装回去。
     *
     * <p>两处关键设置：
     * <ol>
     *   <li>{@code AckMode.RECORD} — listener 正常返回后才提交位点。</li>
     *   <li>{@link LikeUpdateKafkaListener#goAlignedErrorHandler()} — <b>0 次进程内重试</b>，
     *       失败即丢弃并继续处理后续消息，与 Go 逐条一致（见该方法的完整真源论证）。</li>
     * </ol>
     *
     * <p>⚠️ 2026-10-23 修正两处此前写反的注释：原文说本处理器「不 seek、不提交、失败消息
     * 在重启后重投」，又说<b>默认</b> recoverer「会 seek ⇒ 丢消息」。
     * 两者都与 Spring Kafka 4.0.7 的实际控制流相反：
     * <ul>
     *   <li>显式给 {@code FixedBackOff(0, 0)} 后，失败<b>不会</b>被 seek（{@code FailedRecordTracker.recovered}
     *       在 {@code nextBackOff == STOP} 时返回 {@code true} ⇒ {@code SeekUtils.doSeeks} 跳过该 offset），
     *       位点<b>照常推进</b> ⇒ 是<b>丢弃</b>，不是重投；</li>
     *   <li>而单参 {@code new DefaultErrorHandler(recoverer)} 默认 {@code FixedBackOff(0, 9)}
     *       才是真正会 seek 并把 listener <b>重跑 9 次</b>的那条路。</li>
     * </ul>
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            LikeKafkaTopics likeKafkaTopics) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(LikeUpdateKafkaListener.goAlignedErrorHandler());

        // 对齐 Go mq.yaml 的 `Kafka.Consumers: 8`
        // （消费点：go-queue@v1.2.2/kq/queue.go:249 `for i := 0; i < q.c.Consumers; i++` ⇒ 8 个消费协程；
        //  默认值同 8：kq/config.go:18）。
        // ⚠️ 必须在这里 setConcurrency —— 因为本方法自己 new 了 factory，
        //    Boot 的 ConcurrentKafkaListenerContainerFactoryConfigurer 不参与，
        //    写 `spring.kafka.listener.concurrency` 在本模块**没有任何消费点**（DRIFT-LIKE-018）。
        //    默认值不设时 ConcurrentKafkaListenerContainerFactory 是 1，即单协程消费 ⇒ ÷8 的积压消化速度。
        factory.setConcurrency(likeKafkaTopics.getConsumers());
        return factory;
    }

    public static void main(String[] args) {
        SpringApplication.run(LikeRpcApplication.class, args);
    }
}