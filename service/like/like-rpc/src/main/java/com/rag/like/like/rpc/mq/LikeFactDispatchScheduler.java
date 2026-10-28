package com.rag.like.like.rpc.mq;

import com.rag.common.communityfact.Dispatcher;
import com.rag.common.communityfact.Wire;
import com.rag.like.like.rpc.config.LikeRpcProperties;
import com.rag.like.like.rpc.service.LikeFactDispatchService;
import io.opentelemetry.context.Context;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/**
 * 事实流派发调度 —— 对应 Go 端 {@code service/like/rpc/cmd/fact-dispatch/main.go:51-77}
 * 的 {@code run(once, logger)}（{@code communityfact.RunDispatcher} 的调用点）。
 *
 * <h2>⚠️ 默认关闭，这是刻意的</h2>
 * <p>Go 侧 fact-dispatch 是<b>独立进程</b>：缺 DC 配置时它自己 {@code return error} ⇒ 退出，
 * <b>不会</b>影响 like-rpc 的可用性。
 * Java 侧合并成单 JVM 后若默认开启，就会把「DC 没配」变成「整个 like-rpc 起不来」
 * ⇒ 改为「{@code like.fact.dispatch-enabled=false} + 启动时显式告警」，
 * 关闭时该功能<b>完全不参与运行</b>（不创建 sender、不起调度线程）。
 *
 * <h2>本类<b>不注册任何对外服务</b></h2>
 * <p>它只驱动一个后台循环，投递方向是「本进程 → DataCenter 的 HTTP 端点」，
 * 对外不暴露端口。
 *
 * <h2>🔴 批次 / 节奏 / 超时 / 失败即 break —— <b>四项都在共享层，本类不重复实现</b></h2>
 * <pre>
 *   批次 16（Dispatcher.java:178）        ← Go communityfact/process.go:44  for i := 0; i &lt; 16; i++
 *   节奏 1s（Dispatcher.java:199）        ← Go communityfact/process.go:41  time.NewTicker(time.Second)
 *   单次 10s（Dispatcher.java:122）       ← Go communityfact/process.go:28  context.WithTimeout(attempt, 10*time.Second)
 *   单条失败即 break（Dispatcher.java:190）← Go communityfact/process.go:46-51
 * </pre>
 * ⇒ 共享层的 {@link Dispatcher} 已按 Go 的形状实现（2026-10-25 第十九轮实测确认）
 * ⇒ 本类<b>只负责装配 + 生命周期</b>，不复制那四项 ⇒ 杜绝「两份实现漂移」。
 *
 * <h2>⚠️ 范围：本类只做<b>投递方向</b>（like-rpc → DC）</h2>
 * <p>另一个方向（DC → like-rpc 的<b>权威读端点</b>，Go 的 {@code cmd/fact-authority}）
 * 在本域是 {@code LikeAuthorityEndpointConfig} —— 它<b>仍是未实现骨架</b>，
 * 且自带说明：「监听地址与端口 UNKNOWN，需给 like-rpc 新增 HTTP connector（wire 面变更，<b>待拍板</b>）」。
 * ⇒ 本轮<b>不碰</b>那一半（属 wire 面变更 ⇒ 需用户拍板）⇒ 只做投递方向。
 *
 * <h2>🔴🔴 必须用 {@link Dispatcher#runAsync}，<b>不能</b>用 {@link Dispatcher#run}</h2>
 * <p>{@code Dispatcher.run} 是<b>阻塞式</b>的：它在<b>调用线程</b>上跑整个投递循环，
 * 直到 {@code stop()} 被调用才返回。
 * 而本类的启动点挂在 {@code @PostConstruct} 上 ⇒ 那是 <b>Spring 上下文刷新线程</b>
 * ⇒ 用 {@code run} 会让<b>上下文永远刷新不完</b> ⇒ <b>整个 like-rpc 起不来</b>。
 * <p>⚠️ 这类缺陷<b>「开关默认关闭」时完全测不出来</b>：测试全绿，而真启动就挂。
 * ⇒ 守门见 {@code LikeFactDispatchSchedulerStartupTest}（真启动 + 真后台线程 + 真停止）。
 */
@Component
public class LikeFactDispatchScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(LikeFactDispatchScheduler.class);

    private final LikeRpcProperties props;
    private final LikeFactDispatchService deliveryService;

    private volatile Dispatcher.DispatcherHandle handle;

    public LikeFactDispatchScheduler(LikeRpcProperties props, LikeFactDispatchService deliveryService) {
        this.props = props;
        this.deliveryService = deliveryService;
    }

    @PostConstruct
    public void start() {
        LikeRpcProperties.Fact fact = props.getFact();
        if (!fact.isDispatchEnabled()) {
            LOG.info("LikeFactDispatchScheduler: 事实流派发未启用（like.fact.dispatch-enabled=false）"
                    + "，Go 侧为独立进程，此处默认关闭以免 DC 未配置时拖垮 like-rpc");
            return;
        }
        // Go: cmd/fact-dispatch/main.go:55 `if dsn == "" || endpoint == "" || len(token) < 32`
        //     ⇒ Java 侧不检查 dsn（DB 就是本进程自己的连接），其余两条逐条照抄。
        if (fact.getDcEndpoint() == null || fact.getDcEndpoint().isEmpty()
                || fact.getDcToken() == null || fact.getDcToken().length() < 32) {
            // Go: 同一处是 main.go:56 `return errors.New("like fact dispatcher configuration incomplete")` → 进程退出。
            // Java 侧不能退出（会带走 gRPC 与 Kafka 消费），因此告警后不启动。
            LOG.error("LikeFactDispatchScheduler: 配置不完整（dc-endpoint 非空 + dc-token ≥32 字节），"
                    + "事实流派发不启动。对应 Go cmd/fact-dispatch/main.go:55-56 的进程退出条件");
            return;
        }

        try {
            HttpClient httpClient = HttpClient.newBuilder()
                    // 对应 Go cmd/fact-dispatch/main.go:72 的 &http.Client{Timeout: 8 * time.Second}
                    .connectTimeout(fact.getDcTimeout())
                    .build();
            Wire.Sender sender = Wire.DCEventSender.create(
                    fact.getDcEndpoint(), fact.getDcToken(), httpClient);

            boolean once = fact.isOnce();
            // ⚠️ registerShutdownHook = false：Spring 化路径由 @PreDestroy 显式 stop()，
            //    避免 JVM hook 与 SpringApplication 的 graceful shutdown 时序冲突。
            // 🔴🔴 必须用 **runAsync**（非阻塞）：@PostConstruct 跑在 Spring 上下文刷新线程上，
            //    而 Dispatcher.run 是**阻塞式**的（循环跑在调用线程上，直到 stop() 才返回）
            //    ⇒ 用 run 会让**上下文永远刷新不完** ⇒ 整个 like-rpc 起不来。
            //    这类缺陷在「开关默认关闭」时测不出来（绿测试 + 起不来）⇒ 必须有真启动守门。
            this.handle = Dispatcher.runAsync(
                    Context.current(),
                    deliveryService::dispatchFactOnce,
                    sender,
                    LOG,
                    once,
                    false);
            LOG.info("LikeFactDispatchScheduler: 事实流派发已启动，once={} endpoint={}",
                    once, fact.getDcEndpoint());
        } catch (Exception e) {
            LOG.error("LikeFactDispatchScheduler: 启动失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 容器关闭时停掉循环 —— 对应 Go 的 {@code ctx.Done()} 分支
     * （{@code service/common/communityfact/process.go:56-60}）。
     *
     * <p>⚠️ Go 的 fact-dispatch 是 {@code return nil} 直接退出 ⇒ <b>不</b>等待在途投递完成
     * ⇒ 本类也<b>不</b>等待。
     */
    @PreDestroy
    public void stop() {
        Dispatcher.DispatcherHandle current = this.handle;
        if (current != null) {
            current.stop();
            LOG.info("LikeFactDispatchScheduler: 事实流派发已停止");
        }
    }

    /** 便于测试与运维观察：当前是否真的在跑。 */
    public boolean isRunning() {
        return handle != null;
    }
}
