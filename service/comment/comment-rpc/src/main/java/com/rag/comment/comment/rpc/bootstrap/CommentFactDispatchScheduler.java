package com.rag.comment.comment.rpc.bootstrap;

import com.rag.common.communityfact.Dispatcher;
import com.rag.common.communityfact.Wire;
import com.rag.comment.comment.rpc.config.CommentRpcProperties;
import com.rag.comment.comment.rpc.service.CommentFactDeliveryService;
import io.opentelemetry.context.Context;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 事实流派发调度 —— 对应 Go 端 {@code service/comment/rpc/cmd/fact-dispatch/main.go:52-77}
 * 的 {@code run(once, logger)}（{@code communityfact.RunDispatcher} 的调用点）。
 *
 * <h2>⚠️ 默认关闭，这是刻意的</h2>
 * <p>Go 侧 fact-dispatch 是<b>独立进程</b>：缺 DC 配置时它自己 {@code return error} ⇒ 退出，
 * <b>不会</b>影响 comment-rpc 的可用性。
 * Java 侧合并成单 JVM 后若默认开启，就会把「DC 没配」变成「整个 comment-rpc 起不来」
 * ⇒ 改为「{@code comment.fact.dispatch-enabled=false} + 启动时显式告警」，
 * 关闭时该功能<b>完全不参与运行</b>（不创建 sender、不起调度线程）。
 *
 * <h2>本类<b>不注册任何对外服务</b></h2>
 * <p>它只驱动一个后台循环，投递方向是「本进程 → DataCenter 的 HTTP 端点」，
 * 对外不暴露端口。真正对外的是 {@link CommentAuthorityEndpointConfig}（默认也关闭）。
 *
 * <p>⚠️ like-rpc 的同名文件（{@code LikeFactDispatchScheduler.java}）在 2026-10-25（第二十轮）
 * 之前是<b>未实现骨架</b>，现已按同一形状实现 ⇒ 两域可互为参照。
 *
 * <h2>🔴🔴 必须用 {@link Dispatcher#runAsync}，<b>不能</b>用 {@link Dispatcher#run}</h2>
 * <p>{@code Dispatcher.run} 是<b>阻塞式</b>的：它在<b>调用线程</b>上跑整个投递循环，
 * 直到 {@code stop()} 被调用才返回。
 * 而本类的启动点挂在 {@code @PostConstruct} 上 ⇒ 那是 <b>Spring 上下文刷新线程</b>
 * ⇒ 用 {@code run} 会让<b>上下文永远刷新不完</b> ⇒ <b>整个 comment-rpc 起不来</b>。
 * <p>⚠️ 这类缺陷<b>「开关默认关闭」时完全测不出来</b>：测试全绿，而真启动就挂。
 */
@Component
public class CommentFactDispatchScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CommentFactDispatchScheduler.class);

    private final CommentRpcProperties props;
    private final CommentFactDeliveryService deliveryService;
    private final GrpcServerProperties grpcServerProperties;

    private volatile Dispatcher.DispatcherHandle handle;

    public CommentFactDispatchScheduler(CommentRpcProperties props,
                                        CommentFactDeliveryService deliveryService,
                                        GrpcServerProperties grpcServerProperties) {
        this.props = props;
        this.deliveryService = deliveryService;
        this.grpcServerProperties = grpcServerProperties;
    }

    @PostConstruct
    public void start() {
        CommentRpcProperties.Fact fact = props.getFact();
        if (!fact.isDispatchEnabled()) {
            LOG.info("CommentFactDispatchScheduler: 事实流派发未启用（comment.fact.dispatch-enabled=false）"
                    + "，Go 侧为独立进程，此处默认关闭以免 DC 未配置时拖垮 comment-rpc");
            return;
        }
        if (fact.getDcEndpoint() == null || fact.getDcEndpoint().isEmpty()
                || fact.getDcToken() == null || fact.getDcToken().length() < 32) {
            // Go: cmd/fact-dispatch/main.go:56-58 任一不满足即 return error → 进程退出。
            // Java 侧不能退出（会带走 gRPC 与 Kafka 消费），因此告警后不启动。
            LOG.error("CommentFactDispatchScheduler: 配置不完整（dc-endpoint 非空 + dc-token ≥32 字节），"
                    + "事实流派发不启动。对应 Go cmd/fact-dispatch/main.go:56-58 的进程退出条件");
            return;
        }

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    // 对应 Go cmd/fact-dispatch/main.go:73 的 &http.Client{Timeout: 8 * time.Second}
                    .connectTimeout(fact.getDcTimeout())
                    .build();
            Wire.Sender sender = Wire.DCEventSender.create(
                    fact.getDcEndpoint(), fact.getDcToken(), httpClient);

            boolean once = fact.isOnce();
            // 🔴🔴 必须用 **runAsync**（非阻塞）：@PostConstruct 跑在 Spring 上下文刷新线程上，
            //    而 Dispatcher.run 是**阻塞式**的（循环跑在调用线程上，直到 stop() 才返回）
            //    ⇒ 用 run 会让**上下文永远刷新不完** ⇒ 整个 comment-rpc 起不来。
            //    这类缺陷在「开关默认关闭」时测不出来（绿测试 + 起不来）。
            this.handle = Dispatcher.runAsync(
                    Context.current(),
                    deliveryService::dispatchOnce,
                    sender,
                    LOG,
                    once,
                    false);
            LOG.info("CommentFactDispatchScheduler: 事实流派发已启动，once={} endpoint={}",
                    once, fact.getDcEndpoint());
        } catch (Exception e) {
            LOG.error("CommentFactDispatchScheduler: 启动失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 容器关闭时停掉循环。
     *
     * <p>{@code Dispatcher.run} 默认<b>不</b>注册 JVM shutdown hook（避免与 Spring 的
     * graceful shutdown 时序冲突、测试场景累积），因此必须显式 stop。
     */
    @PreDestroy
    public void stop() {
        Dispatcher.DispatcherHandle current = this.handle;
        if (current != null) {
            current.stop();
            LOG.info("CommentFactDispatchScheduler: 事实流派发已停止");
        }
    }

    /** 便于测试与运维观察：当前是否真的在跑。 */
    public boolean isRunning() {
        return handle != null;
    }

    /** 端口仅用于日志对齐（Go 侧 fact-dispatch 是独立进程，无端口）。 */
    int grpcPort() {
        return grpcServerProperties.getPort();
    }
}