package com.rag.hot.hot.rpc;

import com.rag.common.protection.GoStyleProtectionConfiguration;
import com.rag.common.protection.GoStyleProtectionProperties;
import com.rag.common.protection.GoStyleRpcProtectionChain;
import com.rag.common.spring.ObservabilityProperties;
import com.rag.common.spring.RpcServerInterceptorFactory;
import com.rag.hot.hot.rpc.config.Jackson2ObjectMapperConfiguration;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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
 *       ⚠️ <b>要真正生效，必须下面两件同时齐</b>（缺任一件都<b>静默失效</b>、不报任何错）：
 *       <ol>
 *         <li>{@code @Import(RpcServerInterceptorFactory.class)} ——
 *             {@code com.rag.common} 已不再被全包扫描（纯 gRPC 进程会把 servlet 类拖进来），
 *             不引则 {@code ServerInterceptor} Bean 根本不存在；</li>
 *         <li>{@link #rpcInterceptorConfigurer} —— devh starter <b>从不</b>自动把容器里的
 *             {@code ServerInterceptor} 挂到 gRPC server 上（已用 {@code javap} 对
 *             {@code GrpcServerFactoryAutoConfiguration} 证伪：三个工厂方法只注入
 *             {@code List<GrpcServerConfigurer>}，没有 {@code ServerInterceptor} 形参）。
 *             只产出 Bean 而没有 Configurer ⇒ 链路上<b>什么都不会发生</b>。</li>
 *       </ol>
 *       🔴 <b>2026-10-25 修复</b>：本类此前<b>只有</b>第 ① 件，缺第 ② 件
 *       ⇒ {@code interceptCall} / {@code onHalfClose} 从未执行 ⇒ gRPC 无 span、
 *       <b>无 deadline 传播</b>（业务层 {@code checkDeadline()} 全是死代码）、
 *       {@code DeadlineQueryTimeoutInjector} 因拿不到 {@code DeadlineState} 而不给 SQL 设
 *       {@code queryTimeout}、gRPC 本身无超时（客户端只能等 api 层 {@code http-timeout}）。
 *       ⇒ 台账见 {@code docs/DRIFT/hot/rpc.md}（DRIFT-COMMON-004 的域内认领与修复）。</li>
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
        GoStyleProtectionConfiguration.class,    // 产出 GoStyleProtectionProperties：熔断 / 降载的开关与阈值
})
public class HotRpcApplication {

    /**
     * 把 {@link ServerInterceptor} 真正挂到 gRPC server 上 —— <b>缺它则上面那套全部静默失效</b>。
     *
     * <p><b>Go 出处（消费点，不是声明点）</b>：
     * <ul>
     *   <li>{@code service/hot/rpc/hot.go:42} ——
     *       {@code s.AddUnaryInterceptors(observability.NewUnaryServerInterceptor(rpcTimeout, observability.SlowThreshold()))}</li>
     *   <li>{@code service/hot/rpc/hot.go:31} ——
     *       {@code s := zrpc.MustNewServer(c.RpcServerConf, …)} ⇒ Go 侧由 zrpc <b>框架级</b>补上
     *       Trace → Recover → Stat → Prometheus → Breaker → Shedding
     *       （消费点 {@code go-zero/zrpc/server.go:144-163}，
     *       注册序由 {@code go-zero/zrpc/internal/server.go:58}「append 到尾部 ⇒ 先注册的最外层」决定）。</li>
     * </ul>
     *
     * <p><b>语义等价论证</b>：Go 侧这条链的<b>业务观测层</b>（Timeout / trace）
     * 由 {@code AddUnaryInterceptors} 显式注册，而 Recover / Breaker / Shedding 三层由
     * {@code zrpc.MustNewServer} 在框架级自动补齐 ⇒ <b>两层缺一不可</b>。
     * Java 侧与之同构：{@link GoStyleRpcProtectionChain#build} 把
     * <b>recover → breaker → shedding → 业务观测拦截器</b> 按同一顺序串成一条链，
     * 再由本方法交给 {@code server.intercept(...)} 挂上 server
     * ⇒ 与 Go 的注册序（由外到内）一致。
     *
     * <p>⚠️ <b>为什么用共享的链而不是就地拼</b>（DRIFT-MSG-V-026）：
     * 熔断 / 降载是 Go 侧 <b>zrpc 框架级</b>能力（每个 rpc server 都自带），
     * 若顺序分散在 10 个 rpc 组件各写一遍，就是 10 个可能写错的地方
     * ⇒ 链的定义点收回共享模块 {@code com.rag.common.protection}，各组件只负责「挂上去」。
     *
     * <p>⚠️ <b>本方法不可省、也不可用「断言 Bean 存在」代替</b>：
     * 删掉它之后 {@code ctx.getBean(ServerInterceptor.class)} 断言<b>照样是绿的</b>，
     * 而超时 / deadline 传播 / gRPC span 已全部静默消失
     * ⇒ 守门用例断言的是<b>运行时后果</b>（服务端超时真的把请求掐断），
     * 见 {@code HotGrpcInterceptorWiringTest}。
     */
    @Bean
    public GrpcServerConfigurer rpcInterceptorConfigurer(ServerInterceptor rpcUnaryServerInterceptor,
            GoStyleProtectionProperties protectionProps) {
        return server -> server.intercept(
                GoStyleRpcProtectionChain.build(rpcUnaryServerInterceptor, protectionProps));
    }

    public static void main(String[] args) {
        SpringApplication.run(HotRpcApplication.class, args);
    }
}
