package com.rag.article.article.rpc;

import com.rag.article.article.rpc.mq.ArticleReviewConsumer;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import org.mybatis.spring.annotation.MapperScan;

/**
 * article-rpc 进程入口 —— 对应 Go 端 {@code service/article/rpc/article.go} 的 {@code main()}。
 *
 * <h2>Go 的 {@code service.ServiceGroup} 四件套 → Spring 容器生命周期</h2>
 * <table border="1">
 *   <caption>同启同停的四个部件</caption>
 *   <tr><th>Go</th><th>Java</th><th>谁保证启停</th></tr>
 *   <tr><td>{@code zrpc.MustNewServer}</td><td>{@code @GrpcService}</td><td>devh starter</td></tr>
 *   <tr><td>{@code kq.MustNewQueue(KqConsumerConf, ArticleConsumer)}</td>
 *       <td>{@code @KafkaListener}</td><td>Spring Kafka listener container</td></tr>
 *   <tr><td>{@code kq.MustNewQueue(SyncResultConsumerConf, …)}</td>
 *       <td>{@code @KafkaListener}</td><td>Spring Kafka listener container</td></tr>
 *   <tr><td>{@code articleSyncRelayService}（goroutine + ticker）</td>
 *       <td>{@code @Scheduled}</td><td>Spring 调度器</td></tr>
 * </table>
 *
 * <h2>⚠️ 六个「不写就静默失效 / 直接起不来」的装配点</h2>
 * <p>这些是 message-rpc 在 AGENTS.md §6.6 付出真实代价才补上的
 * （DRIFT-MSG-003 ~ 006），本模块<b>照单继承</b>，每条都有 wiring 测试锁死：
 * <ol>
 *   <li><b>{@code @MapperScan}</b> —— 缺它 6 个 Mapper 全部注册不上，
 *       报 {@code No qualifying bean of type '…mapper.ArticleMapper'}，上下文直接起不来。</li>
 *   <li><b>{@code @Import} 精确引入 common Bean</b> —— 不能用
 *       {@code scanBasePackages={"com.rag.common"}}：那里有 {@code HttpMiddlewareFilter}
 *       （servlet Filter），纯 gRPC 进程全包扫描会把 web 类拖进来。</li>
 *   <li><b>{@link #rpcInterceptorConfigurer}}</b> —— devh starter <b>不会</b>自动把用户提供的
 *       {@link ServerInterceptor} 挂到 server 上。没有它，
 *       {@code observability.rpc-timeout: 70s}（对齐 Go 的 {@code Timeout: 70000}）
 *       连同 deadline 传播、SQL 超时取消<b>全部静默失效</b>，
 *       而"Bean 存在"的断言照样是绿的。</li>
 *   <li><b>{@code spring-boot-flyway}（在 pom 里）</b> —— Boot 4 拆模块后必需。
 *       缺它时<b>启动日志里连一行 Flyway 都没有</b>（不是报错，是根本没加载），
 *       五张表永不创建 ⇒ 第一次真正执行 SQL 才炸。</li>
 *   <li><b>{@code @EnableScheduling}</b> —— outbox relay 的 1 秒 ticker 靠它，
 *       缺它 relay 静默不跑，事件永远积压。</li>
 *   <li><b>{@link #articleKafkaListenerContainerFactory}</b> —— 两个消费者共用一个
 *       {@link CommonErrorHandler}，<b>与 Go 的 {@code ForceCommit=true} 一致：失败即提交位点</b>；
 *       并发度对齐 Go 的 {@code Processors} 默认值 8。</li>
 * </ol>
 */
@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.rag.article.article.rpc.config")
@MapperScan("com.rag.article.article.rpc.mapper")
@EnableScheduling
@Import({
        com.rag.common.spring.SnowflakeBean.class,               // workerId 解析（文章 ID / 事件 ID 用它）
        com.rag.common.spring.SnowflakeProperties.class,
        com.rag.common.spring.LoggerFactoryBean.class,           // 领域服务注入它

        // ===== gRPC 观测三件套：缺任一都「不报错、但机制全无」=====
        com.rag.common.spring.RpcServerInterceptorFactory.class,  // 产出 ServerInterceptor Bean
        com.rag.common.spring.ObservabilityProperties.class,      // 承载 observability.rpc-timeout（Go 70000ms）
        com.rag.common.spring.ObservabilityLifecycle.class,       // 容器关闭时回收线程池

        // ===== 超时必须能真正取消在途 SQL =====
        com.rag.common.persistence.DeadlineQueryTimeoutInjector.class, // 按线程 DeadlineState 给每条 SQL 设 queryTimeout
        com.rag.common.persistence.SqlTimeoutStatusTranslator.class,    // 把 57014 query_canceled 翻成 DEADLINE_EXCEEDED

        // ===== 熔断 / 降载（Go zrpc 框架级能力，每个 rpc server 都自带）=====
        com.rag.common.protection.GoStyleProtectionConfiguration.class,  // 熔断/降载链的配置载体（breaker / cpuShedding 的开关与阈值；默认值 = go-zero 的 tag 默认值）

})
public class ArticleRpcApplication {

    /**
     * Kafka handler 的并发度 —— 对应 Go {@code kq.KqConf.Processors} 的默认值 8。
     *
     * <p>Go 出处：<b>声明点</b> {@code kq/config.go:19}
     * {@code Processors int `json:",default=8"}`；<b>消费点</b>
     * {@code kq/queue.go:220} {@code for i := 0; i < q.c.Processors; i++}。
     * article-rpc 的 yaml 只配了 {@code Consumers: 1}，未配 {@code Processors} ⇒ 取默认 8。
     *
     * <p>⚠️ <b>不要与 {@code Consumers} 混淆</b>：{@code Consumers} 控制 reader 协程
     * （{@code kq/queue.go:243} {@code startProducers}），{@code Processors} 才控制
     * 调用 handler 的并发度。把它当"单消费者 = 单并发"是本次验证抓到的错误依据之一。
     */
    static final int PROCESSOR_CONCURRENCY = 8;

    /**
     * 把 {@link ServerInterceptor} <b>真正挂到 gRPC server 上</b>。
     *
     * <p>对应 Go {@code article.go:50}：
     * {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}。
     *
     * <p><b>删掉这行后 {@code ctx.getBean(ServerInterceptor.class)} 断言照样是绿的</b>，
     * 但 {@code interceptCall} / {@code onHalfClose} 一次都不执行 ⇒
     * 超时、deadline 传播、gRPC span 全部静默消失。
     * 这正是 AGENTS.md §3.3「断言 Bean 存在 ≠ 断言机制生效」的反面教材。
     * 锁死用例：{@code ArticleGrpcInterceptorWiringTest}。
     */
    @Bean
    public GrpcServerConfigurer rpcInterceptorConfigurer(ServerInterceptor rpcUnaryServerInterceptor,
            com.rag.common.protection.GoStyleProtectionProperties protectionProps) {
        // ⚠️ DRIFT-MSG-V-026（2026-10-24）：链的定义点在**共享模块**
        //   com.rag.common.protection.GoStyleRpcProtectionChain ——
        //   熔断 / 降载在 Go 侧是 **zrpc 框架级**能力（每个 rpc server 都自带），
        //   10 个 rpc 组件挂的是同一条链，顺序若分散在各处各写一遍，就是 10 个可能写错的地方。
        //   本仓的链：recover → breaker → shedding → 业务观测拦截器。
        return server -> server.intercept(com.rag.common.protection.GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps));
    }

    /**
     * article 域的 Kafka {@link CommonErrorHandler} —— <b>零次进程内重试、失败即提交位点</b>。
     *
     * <h3>Go 侧真源（go-queue v1.2.2）：判定在 tag 的默认值上，不在 continue 那行</h3>
     * <p>失败位点的处置由 <b>{@code ForceCommit} 这个 struct tag 的默认值</b>决定：
     * <pre>{@code
     * // kq/config.go:24    ← 声明点，默认值在这里
     * ForceCommit   bool   `json:",default=true"`
     *
     * // kq/queue.go:230-242   ← 消费点，真正的判定在这里
     * if err := q.consumeOne(ctx, string(msg.Key), string(msg.Value)); err != nil {
     *     if q.errorHandler != nil { q.errorHandler(ctx, msg, err) }  // article-rpc 未传 ⇒ nil
     *     if !q.c.ForceCommit { continue }                             // ForceCommit=true ⇒ 不 continue
     * }
     * if err := q.consumer.CommitMessages(ctx, msg); err != nil { ... } // ← 失败也照样提交
     * }</pre>
     * article-rpc 的 yaml <b>没有</b>配置 {@code ForceCommit} ⇒ 生效值 <b>true</b>
     * ⇒ <b>Go 在 handler 返回 error 时依然提交该消息的位点</b>（消息被丢弃），
     * 且进程内<b>无重试循环</b>。
     *
     * <h3>⚠️ 曾经的错误：本方法原先设 {@code seekAfterError=false}，依据是被证伪的</h3>
     * <p>原注释写「Go 的 {@code continue} 说明失败位点不提交」。但那行 {@code continue} 前面有
     * <b>{@code if !q.c.ForceCommit} 的前置判定</b>，而 {@code ForceCommit} 取默认 {@code true}
     * ⇒ <b>该 {@code continue} 永远不执行</b>。
     * <br>（AGENTS.md §3.3.1：判断 Go 默认行为是否生效，必须读到<b>消费该 tag 的那段控制流</b>，
     * 只贴 struct 定义行不算证据。）
     * <br>后果：失败消息在 Java 侧不提交位点 ⇒ rebalance / 重启后<b>无限重投</b>，
     * 与 Go 相反的 poison-pill 语义。
     *
     * <h3>为什么 {@code seekAfterError=true} 正好等于 Go 的行为</h3>
     * <p>{@code FailedBatchProcessor.seekOrRecover}（spring-kafka 4.0.7 反编译确认）在该分支里是：
     * <pre>{@code
     * if (isSeekAfterError()) {
     *     SeekUtils.seekOrRecover(thrownException, remaining, consumer, container, false, ...);
     *     ConsumerRecord&lt;?, ?&gt; recovered = remaining.get(0);
     *     commit(consumer, container,
     *             singletonMap(new TopicPartition(...),
     *                    ListenerUtils.createOffsetAndMetadata(container, recovered.offset() + 1)));
     * }
     * }</pre>
     * 提交的正是<b>失败记录自己的 {@code offset + 1}</b>，与 Go 的
     * {@code CommitMessages(ctx, msg)} 逐条提交<b>同构</b>。
     * ⇒ 本实现<b>显式</b>写 {@code setSeekAfterError(true)}（虽与 Spring 默认值相同），
     * 是为了让这个<b>与直觉相反</b>的选择在代码里可见、可 review、可被测试锁死。
     *
     * <p>⚠️ recoverer 只记日志、不做任何恢复动作 —— 与 Go 的 {@code errorHandler == nil}
     * （未传 {@code WithHandleError}）不冲突：Go 那条分支本来就只做 {@code CommitMessages}。
     *
     * <h3>与 Spring Kafka 出厂默认的差异</h3>
     * <table border="1">
     *   <caption>BackOff</caption>
     *   <tr><th>项</th><th>Spring Kafka 默认</th><th>后果</th><th>本实现</th></tr>
     *   <tr><td>BackOff</td>
     *       <td>{@code SeekUtils.DEFAULT_BACK_OFF} = <b>9 次重试、零延迟</b></td>
     *       <td>同一进程内把同一失败消息连打 9 次后才放弃</td>
     *       <td>{@code new FixedBackOff(0L, 0L)} ⇒ <b>0 次</b>，与 Go 一致</td></tr>
     * </table>
     *
     * <p>做成具名 Bean 而不是工厂方法内部的局部变量：<b>可被测试读到</b>
     * （{@code createContainer(...)} 是 public，测试能拿到工厂真正产出的容器并读它的
     * {@code getCommonErrorHandler()}），且<b>单一真相源</b>——两个 listener 共用同一契约。
     * 锁死用例：{@code ArticleSpringContextWiringTest#kafkaContainersCarryTheForceCommitErrorHandler}。
     */
    @Bean
    public CommonErrorHandler articleNoCommitErrorHandler() {
        Logger logger = LoggerFactory.getLogger(ArticleRpcApplication.class);
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> logger.error(
                        "article kafka message failed, offset committed as in Go kq (ForceCommit=true): "
                                + "the message is dropped, not redelivered: "
                                + "topic={} partition={} offset={} err={}",
                        record.topic(), record.partition(), record.offset(), exception.getMessage()),
                // 0 次进程内重试：Go 的 kq 返回 error 后立即取下一条，没有重试循环
                new FixedBackOff(0L, 0L));
        // 🔴 与 Go 的 ForceCommit 默认值 true 对齐：失败记录同样提交位点（消息被丢弃）。
        //    写成 false 会让失败消息在 rebalance/重启后无限重投 —— 与 Go 相反。
        handler.setSeekAfterError(true);
        return handler;
    }

    /**
     * article 域的 Kafka listener 容器工厂 —— 用上面那个"不提交失败位点"的错误处理器。
     *
     * <p><b>⚠️ 为什么必须自定义</b>：Spring Kafka 出厂默认在重试耗尽后会
     * <b>把失败记录的位点 commit 掉</b>（即丢消息），这与 Go 的
     * 「返回 error ⇒ 不确认、靠重启/rebalance 重投」不符。
     * 具体机理与纠正手段见 {@link #articleNoCommitErrorHandler()} 的 javadoc。
     *
     * <p><b>⚠️ 为什么用 RECORD 而非 BATCH</b>：RECORD 是「listener 正常返回后提交当前记录的位点」，
     * 与 Go kq 的逐条 {@code CommitMessages} 一致；BATCH 会把位点推到整个 poll 批次的末尾，
     * 一条失败可能连带丢掉同批次已成功的其它消息。
     */
    @Bean
    @ConditionalOnMissingBean(name = "articleKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> articleKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler articleNoCommitErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // ⚠️ 两个消费者共用同一错误契约：审核与同步结果的 drop/retry 规则不同，
        //    但"失败都不提交位点"这一条相同（见两个 Consumer 的类级注释）。
        factory.setCommonErrorHandler(articleNoCommitErrorHandler);
        // 🔴 Go 的 handler 并发度是 8（kq/config.go:19 Processors default=8，消费点 kq/queue.go:220）
        //    Spring 默认 concurrency=1 会退化成严格串行。
        factory.setConcurrency(PROCESSOR_CONCURRENCY);
        factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }

    public static void main(String[] args) {
        SpringApplication.run(ArticleRpcApplication.class, args);
    }
}