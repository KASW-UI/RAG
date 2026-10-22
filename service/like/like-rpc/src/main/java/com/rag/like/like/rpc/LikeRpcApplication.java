package com.rag.like.like.rpc;

import com.rag.common.persistence.DeadlineQueryTimeoutInjector;
import com.rag.common.persistence.SqlTimeoutStatusTranslator;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.common.spring.ObservabilityLifecycle;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.common.spring.SnowflakeBean;
import com.rag.common.spring.SnowflakeProperties;
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
 * 本模块<b>照单继承</b>，且每一条都有对应的 wiring 测试锁死：
 * <ol>
 *   <li><b>{@code @MapperScan}</b> — 缺它 5 个 Mapper 全部注册不上，
 *       报 {@code No qualifying bean of type '…mapper.LikeRecordMapper'}，
 *       上下文直接起不来。</li>
 *   <li><b>{@code @Import} 精确引入 common Bean</b> — 不能用
 *       {@code scanBasePackages={"com.rag.common"}}: 那里有 {@code HttpMiddlewareFilter}
 *       （servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来。</li>
 *   <li><b>{@link #rpcInterceptorConfigurer}</b> — devh starter <b>不会</b>自动把用户提供的
 *       {@link ServerInterceptor} 挂到 server 上。没有它，超时 / deadline 传播 / gRPC span
 *       <b>全部静默消失</b>，而 Bean 断言照样是绿的。</li>
 *   <li><b>{@code spring-boot-flyway}</b>（在 pom 里）— Boot 4 拆模块后必需。
 *       缺它时<b>启动日志里连一行 Flyway 都没有</b>（不是报错，是根本没加载），
 *       5 张表永不创建 ⇒ 第一次真正执行 SQL 才炸。
 *       {@code LikeSchemaGate} 是它的第二道防线。</li>
 *   <li><b>{@code @EnableScheduling}</b> — outbox relay 的 1 秒 ticker 靠它。
 *       缺它 relay 静默不跑，事件永远积压。</li>
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
        SqlTimeoutStatusTranslator.class    // 把 57014 query_canceled 翻成 DEADLINE_EXCEEDED
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
     * 锁死用例：{@code LikeGrpcInterceptorWiringTest}。
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
     *   <li>{@code AckMode.RECORD} — listener 正常返回后才提交位点。
     *       这是 drop/retry 契约的载体（Go：{@code Consume} 返回 error ⇒ 不确认）。</li>
     *   <li>{@link LikeUpdateKafkaListener#noCommitErrorHandler()} — 只记日志、
     *       <b>不 seek、不提交</b>，让失败消息在重启后重投，与 Go kq 无进程内重试的行为一致。</li>
     * </ol>
     *
     * <p>⚠️ 不用默认 recoverer：默认会 seek 到当前位点，等于把失败消息标记为已处理 ⇒ 丢消息。
     * 那是<b>新增的丢消息路径</b>，与 Go 不符。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(LikeUpdateKafkaListener.noCommitErrorHandler());
        return factory;
    }

    public static void main(String[] args) {
        SpringApplication.run(LikeRpcApplication.class, args);
    }
}