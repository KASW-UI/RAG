package com.rag.hot.hot.rpc;

import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.hot.hot.rpc.config.Jackson2ObjectMapperConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * hot-rpc 进程入口 —— 对应 Go {@code service/hot/rpc/hot.go}（75 行）。
 *
 * <h2>四个 Go 决策点如何落到 Spring</h2>
 * <ol>
 *   <li><b>2s 服务端 deadline</b>：Go {@code hot.go:32} 调
 *       {@code observability.DisableNativeRpcTimeout(&c.RpcServerConf)} 把 go-zero 默认的 2000ms
 *       清零、交回观测拦截器，再在 {@code :42} 用 {@code NewUnaryServerInterceptor(rpcTimeout, ...)} 施加。
 *       Java 侧无需「先关再开」两步 —— 直接由
 *       {@link RpcServerInterceptorFactory} 从 {@link ObservabilityProperties} 读
 *       {@code observability.rpc-timeout} 施加（application.yml 配 {@code 2s}）。
 *       ⚠️ <b>两个 Bean 都必须显式 {@code @Import}</b>：
 *       {@code com.rag.common} 已不再被全包扫描（纯 gRPC 进程会把 servlet 类拖进来），
 *       不引则 {@code ServerInterceptor} Bean 永远不存在 ⇒ gRPC span 与 deadline 全部失效
 *       <b>且不报任何错</b>。</li>
 *   <li><b>reflection</b>：Go 仅在 {@code c.Mode == DevMode/TestMode} 时注册（{@code :38-40}），
 *       三份 yaml 都没写 {@code Mode} ⇒ 默认 {@code pro} ⇒ <b>生产不暴露 reflection</b>。
 *       Java 侧在 {@code application.yml} 显式 {@code reflection-service-enabled: false} 对齐。</li>
 *   <li><b>权重表按 Name 建 map</b>（Go {@code :47-50}，{@code Code} 被丢弃）⇒ 由
 *       {@code HotEventConsumer} 构造时完成，{@code Code} 只保留在配置里。</li>
 *   <li><b>两条后台定时任务</b>（Go {@code :76-77} 的裸 goroutine）⇒ {@link EnableScheduling}
 *       打开 30s 快照（{@code HotRedisSnapshotStore}）与 10s 合并（{@code HotMergeScheduler}）。</li>
 * </ol>
 *
 * <h2>⚠️ 一处有意收敛（Go 既有形态）</h2>
 * Go 的 consumer 是裸 {@code go func()}（{@code hot.go:65-71}），<b>不在
 * {@code service.ServiceGroup} 内</b> ⇒ 进程收到信号时不参与同启同停；
 * 且两条 ticker goroutine 用的是 {@code context.Background()}，<b>永远不退出</b>
 * （{@code hot_handler.go:76-77}）。Java 侧随 Spring 容器统一关闭 ——
 * 这是<b>表达层收敛</b>，刻意标注为有意差异（关闭行为更正确，且不影响任何业务可观察结果）。
 */
@SpringBootApplication
@EnableScheduling
@Import({
        RpcServerInterceptorFactory.class,       // 产出 ServerInterceptor Bean：gRPC span + 2s deadline
        ObservabilityProperties.class,          // 承载 observability.rpc-timeout
        Jackson2ObjectMapperConfiguration.class, // 产出 Jackson 2 的 ObjectMapper Bean（Boot 4 拆模块后的缺口）
})
public class HotRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotRpcApplication.class, args);
    }
}