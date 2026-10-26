package com.rag.favorite.favorite.rpc.fact;

import com.rag.common.communityfact.Dispatcher;
import com.rag.common.communityfact.Wire;
import com.rag.favorite.favorite.rpc.bootstrap.FavoriteFactProcessLogging;
import com.rag.favorite.favorite.rpc.config.FavoriteRpcProperties;
import com.rag.favorite.favorite.rpc.service.FavoriteFactDispatchService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;

/**
 * 🔴 事实投递的编排层 —— 对应 Go
 * {@code service/favorite/rpc/cmd/fact-dispatch/main.go:34-89} 的 {@code run(once, logger)}。
 *
 * <h2>循环形状逐条照抄（{@code cmd/fact-dispatch/main.go:67-89}）</h2>
 * <pre>
 * ticker := time.NewTicker(time.Second)          // 节奏 1s
 * for {
 *     for i := 0; i &lt; 16; i++ {                // 每轮最多 16 条
 *         attempt, cancel := context.WithTimeout(ctx, 10*time.Second)   // 单条 10s
 *         sent, dispatchErr := store.DispatchFavoriteFactOnce(attempt, sender)
 *         if dispatchErr != nil { 记日志; break }   // ⚠️ 单条失败即跳出内层
 *         if !sent { break }                          // ⚠️ 队列空即跳出内层
 *     }
 *     select { case &lt;-ctx.Done(): return; case &lt;-ticker.C: }
 * }
 * </pre>
 * ⇒ 这四个形状（<b>1s / 16 / 10s / 单条失败即 break</b>）由
 * {@link Dispatcher#run} 直接承担，本类只负责装配 + 生命周期。
 *
 * <h2>⚠️ Go 是独立进程，Java 是单 JVM 的可选组件</h2>
 * <p>Go 侧 {@code cmd/fact-dispatch/main.go:35-40} 要求三个环境变量齐全，缺一即进程退出。
 * 单 JVM 里<b>不能</b>让「没配 DC」拖垮 favorite-rpc ⇒
 * {@code favorite.fact.dispatch.enabled} 默认 <b>false</b>，
 * 未启用时本类<b>完全不启动任何线程</b>（不是「启动后发现没配置」）。
 *
 * <p>⚠️ <b>多副本下的行为放大</b>：Go 是「1 个 dispatch 进程」，
 * Java 合并后是「每副本 1 个」。这在语义上<b>安全</b> ——
 * {@code FOR UPDATE SKIP LOCKED} + 前驱屏障本就是为多实例设计的
 * （见 {@code FavoriteFactOutboxMapper#claimNextDeliverable}），
 * 只是吞吐上限从「1 进程」变成「每 JVM 16 × N 副本」。
 */
@Component
public class FavoriteFactDispatchScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteFactDispatchScheduler.class);

    private final FavoriteRpcProperties properties;
    private final FavoriteFactDispatchService dispatchService;
    private final FavoriteFactProcessLogging processLogging;

    /** {@code Dispatcher.run(...)} 返回的句柄；{@code @PreDestroy} 用它停止循环。 */
    private volatile Dispatcher.DispatcherHandle handle;

    public FavoriteFactDispatchScheduler(FavoriteRpcProperties properties,
                                         FavoriteFactDispatchService dispatchService,
                                         FavoriteFactProcessLogging processLogging) {
        this.properties = properties;
        this.dispatchService = dispatchService;
        this.processLogging = processLogging;
    }

    /**
     * 对应 Go {@code main()} 的 {@code run(once, logger)}（cmd/fact-dispatch/main.go:34-89）。
     *
     * <p>监听 {@link ApplicationReadyEvent}：与 Go 侧一样，必须在
     * Flyway 建表完成之后才启动投递循环，否则第一轮取件会撞上「表不存在」。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        FavoriteRpcProperties.Dispatch config = properties.getFact().getDispatch();
        if (!config.isEnabled()) {
            LOG.info("FavoriteFactDispatchScheduler: 未启用（favorite.fact.dispatch.enabled=false）"
                    + " ⇒ 事实投递完全不参与运行");
            return;
        }
        if (!config.isConfigured()) {
            // Go: cmd/fact-dispatch/main.go:36-40 —— 缺配置即 return error → 进程退出。
            // ⚠️ 单 JVM 里刻意**不**退出：只告警并保持停用。
            LOG.error("FavoriteFactDispatchScheduler: 已启用但配置不完整"
                    + "（favorite.fact.dispatch.dc-endpoint / dc-token 为空）⇒ 保持停用，不启动投递循环");
            return;
        }

        Logger processLogger =
                processLogging.loggerFor(FavoriteFactProcessLogging.SERVICE_DISPATCH, "fact-dispatch");

        final Wire.Sender sender;
        try {
            // Go: cmd/fact-dispatch/main.go:56 —— NewFavoriteDCEventSender(endpoint, token, &http.Client{Timeout: 8s})
            sender = Wire.DCEventSender.create(config.getDcEndpoint(), config.getDcToken(),
                    HttpClient.newBuilder().connectTimeout(config.getDcTimeout()).build());
        } catch (RuntimeException e) {
            LOG.error("FavoriteFactDispatchScheduler: DC sender 装配失败 ⇒ 保持停用: {}", e.getMessage(), e);
            return;
        }

        // Go: cmd/fact-dispatch/main.go:36 —— DC_PLATFORM_EVENT_URL
        LOG.info("FavoriteFactDispatchScheduler: 启动事实投递 once={} dc_endpoint={}",
                config.isOnce(), config.getDcEndpoint());

        // ── 以下四个参数由 Dispatcher.run 直接承担，逐条对应 Go cmd/fact-dispatch/main.go:57-89 ──
        //   批次 16（Dispatcher.java:178）      ← Go cmd/fact-dispatch/main.go:70  for i := 0; i < 16; i++
        //   节奏 1s（Dispatcher.java:199）      ← Go cmd/fact-dispatch/main.go:67  time.NewTicker(time.Second)
        //   单次 10s（Dispatcher.java:122）     ← Go cmd/fact-dispatch/main.go:71  context.WithTimeout(ctx, 10*time.Second)
        //   单条失败即 break（Dispatcher.java:190）← Go cmd/fact-dispatch/main.go:74-78
        //
        // ⚠️ registerShutdownHook = false：Spring 化路径由 @PreDestroy 显式 stop()，
        //    避免 JVM hook 与 SpringApplication 的 graceful shutdown 时序冲突。
        Context ctx = Context.current();
        this.handle = Dispatcher.run(ctx,
                (dispatchCtx, wireSender, logger) -> dispatchService.dispatchFactOnce(dispatchCtx, wireSender),
                sender,
                processLogger,
                config.isOnce(),
                false);
    }

    /**
     * 容器关闭时停止投递循环 —— 对应 Go 的 {@code ctx.Done()} 分支
     * （cmd/fact-dispatch/main.go:85-86 {@code case <-ctx.Done(): return nil}）。
     *
     * <p>⚠️ Go 的 5s 优雅关闭（fact-authority/main.go:66-69）在 dispatch 侧不存在 ——
     * fact-dispatch 是 {@code return nil} 直接退出。所以本类<b>不</b>等待在途投递完成。
     */
    @PreDestroy
    public void stop() {
        Dispatcher.DispatcherHandle current = this.handle;
        if (current != null) {
            current.stop();
            LOG.info("FavoriteFactDispatchScheduler: 投递循环已停止");
        }
    }

    /** 保留引用：otel 的全局 provider 在 Dispatcher 内部使用；此处显式 touch 以固定依赖。 */
    @SuppressWarnings("unused")
    private static void touchOtel() {
        GlobalOpenTelemetry.get().getTracer("sea.rtw.communityfact");
    }
}