package com.rag.comment.comment.rpc;

import com.rag.comment.comment.rpc.config.Jackson2ObjectMapperConfiguration;
import com.rag.comment.comment.rpc.service.CommentAuditListener;
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
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import com.rag.common.protection.GoStyleRpcProtectionChain;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleProtectionConfiguration;

/**
 * comment-rpc 进程入口 —— 对应 Go 端<b>四个</b> main 合并后的单 JVM：
 * <ol>
 *   <li>{@code rpc/comment.go}（gRPC server + Kafka 审计消费者 + promhttp）</li>
 *   <li>{@code rpc/cmd/fact-dispatch/main.go}（事实投递，默认关闭）</li>
 *   <li>{@code rpc/cmd/fact-authority/main.go}（权威读，默认关闭）</li>
 * </ol>
 *
 * <h2>⚠️ 四个「不写就静默失效 / 直接起不来」的装配点</h2>
 * <ol>
 *   <li><b>{@code @MapperScan}</b> —— 缺它 7 个 Mapper 全部注册不上，
 *       报 {@code No qualifying bean of type '…mapper.SubjectMapper'}，上下文直接起不来。</li>
 *   <li><b>{@link #rpcInterceptorConfigurer}</b> —— devh starter <b>不会</b>自动把用户提供的
 *       {@link ServerInterceptor} 挂到 server 上。没有它，超时 / deadline 传播 / gRPC span
 *       <b>全部静默消失</b>，而 Bean 断言照样是绿的
 *       （先例：like-rpc LikeRpcApplication.java:83-97）。</li>
 *   <li><b>{@code @Import} 精确引入 common Bean</b> —— 不能用
 *       {@code scanBasePackages={"com.rag.common"}}：那里有 {@code HttpMiddlewareFilter}
 *       （servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来。</li>
 *   <li><b>{@code spring-boot-flyway}（在 pom 里）</b> —— Boot 4 拆模块后必需。
 *       缺它时<b>启动日志里连一行 Flyway 都没有</b>，7 张表永不创建。
 *       {@code CommentSchemaGate} 是第二道防线。</li>
 * </ol>
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.rag.comment.comment.rpc.config")
@MapperScan("com.rag.comment.comment.rpc.mapper")
@Import({
        SnowflakeBean.class,                // CreateComment 的雪花 ID（create_comment_logic.go:93）
        SnowflakeProperties.class,
        LoggerFactoryBean.class,

        // ===== gRPC 观测三件套 =====
        RpcServerInterceptorFactory.class,  // 产出 ServerInterceptor Bean（还必须配 rpcInterceptorConfigurer）
        ObservabilityProperties.class,      // 承载 observability.rpc-timeout（Go 侧 2000ms）
        ObservabilityLifecycle.class,       // 容器关闭时回收 RpcInterceptor 线程池

        // ===== 超时必须能真正取消在途 SQL =====
        DeadlineQueryTimeoutInjector.class,
        SqlTimeoutStatusTranslator.class,

        // ===== Jackson 2 的 ObjectMapper Bean =====
        Jackson2ObjectMapperConfiguration.class,

        // ===== 熔断 / 降载（Go zrpc 框架级能力，每个 rpc server 都自带）=====
        GoStyleProtectionConfiguration.class,  // 熔断/降载链的配置载体（breaker / cpuShedding 的开关与阈值；默认值 = go-zero 的 tag 默认值）

})
public class CommentRpcApplication {

    private static final Logger LOG = LoggerFactory.getLogger(CommentRpcApplication.class);

    /**
     * 把 {@link ServerInterceptor} 真正挂到 gRPC server 上。
     *
     * <p>对应 Go {@code comment.go:54}：
     * {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, SlowThreshold()))}。
     *
     * <p><b>删掉这个方法后 {@code ctx.getBean(ServerInterceptor.class)} 断言照样是绿的</b>，
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

    /**
     * 覆盖自动配置的 Kafka 监听容器工厂，装上本域的 drop/retry 契约。
     *
     * <p><b>同名 Bean 会替换自动配置的那个</b>（{@code @ConditionalOnMissingBean} 语义），
     * 所以这里必须自己把 {@code consumerFactory} 装回去。
     *
     * <p>两处关键设置：
     * <ol>
     *   <li>{@code AckMode.RECORD} —— listener 正常返回后才提交位点。
     *       这是「落库失败 → 不提交 → 重投」契约的载体
     *       （Go：{@code Consume} 返回 error ⇒ kq 不确认）。</li>
     *   <li>{@link CommentAuditListener#onMessage} 自己决定「返回 null 还是抛异常」，
     *       错误处理器只<b>记日志、不 seek、不提交</b>。</li>
     * </ol>
     *
     * <p>⚠️ 不用默认 recoverer：默认会 seek 到当前位点，
     * 等于把失败消息标记成已处理 ⇒ <b>丢消息</b>。那是<b>新增的丢消息路径</b>，与 Go 不符。
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler(
                (record, exception) ->
                        LOG.error("comment audit kafka message failed, will NOT commit offset: {}",
                                exception.getMessage(), exception)));
        return factory;
    }

    public static void main(String[] args) {
        SpringApplication.run(CommentRpcApplication.class, args);
    }
}