package com.rag.common.spring;

import com.rag.common.observability.Observability;
import com.rag.common.observability.RpcInterceptor;
import io.grpc.ServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Spring 入口：把 gRPC Unary ServerInterceptor 暴露为 Spring Bean。
 *
 * <p><b>⚠️ starter 不会自动注册它（H2，已由字节码证伪）</b>：
 * {@code net.devh:grpc-server-spring-boot-starter} <b>从不</b>把容器里的 {@code ServerInterceptor}
 * 挂到 server 上。<b>实测依据</b>（{@code 3.1.0.RELEASE}，对 {@code ~/.m2} 里的 jar 直接
 * {@code javap}，2026-10-23 复核）：
 * <pre>
 *   javap net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration
 *     → shadedNettyGrpcServerFactory(GrpcServerProperties, GrpcServiceDiscoverer,
 *                                    List&lt;GrpcServerConfigurer&gt;)     ← 无 ServerInterceptor 形参
 *     → nettyGrpcServerFactory(…同上…)
 *     → inProcessGrpcServerFactory(…同上…)
 *   javap net.devh.boot.grpc.server.serverfactory.GrpcServerConfigurer
 *     → interface GrpcServerConfigurer extends java.util.function.Consumer&lt;ServerBuilder&lt;?&gt;&gt;
 * </pre>
 * 三个工厂方法<b>全都</b>只注入 {@code List<GrpcServerConfigurer>}，
 * {@code GrpcServerProperties} 里也没有任何 interceptor 字段。
 * <b>⇒ 唯一注册入口是 {@code GrpcServerConfigurer}</b>，必须在 {@code accept(ServerBuilder)} 里调
 * {@code server.intercept(...)}。只产出一个 {@code ServerInterceptor} Bean 而没有 Configurer，
 * 链路上<b>什么都不会发生</b>，且不报错。
 *
 * <p><b>所以每个 rpc 模块要配两件，缺任一件都静默失效</b>：
 * <ol>
 *   <li>{@code @Import(RpcServerInterceptorFactory.class)} —— 否则本 Bean 根本不存在。
 *       按 §6.3 问题 3 的定案，{@code com.rag.common} 已不再被 rpc 进程全包扫描
 *       （纯 gRPC 进程会把 servlet 类拖进来），所以 rpc 模块一律走 {@code @Import}。</li>
 *   <li>{@code @Bean GrpcServerConfigurer} —— 否则 Bean 存在但 starter 不用它（H2 本体）。</li>
 * </ol>
 *
 * <p><b>Go 侧对应物</b>：go-zero 的 {@code zrpc} 同样没有自动注册，每个 rpc 服务都<b>显式</b>调
 * {@code AddUnaryInterceptors(NewUnaryServerInterceptor(...))}。范围内 10 个服务逐条取证
 * （<code>DisableNativeRpcTimeout</code> 行 : <code>AddUnaryInterceptors</code> 行）：
 * <pre>
 *   service/user/user/rpc/user.go        :32 → :43      service/user/admin/rpc/admin.go   :33 → :43
 *   service/article/rpc/article.go       :36 → :51      service/task/rpc/task.go           :28 → :44
 *   service/hot/rpc/hot.go               :32 → :42      service/like/rpc/like.go           :30 → :41
 *   service/message/rpc/message.go       :34 → :44      service/comment/rpc/comment.go     :33 → :54
 *   service/follow/rpc/follow.go         :34 → :44      service/favorite/rpc/favorite.go   :40 → :49
 * </pre>
 * （另有范围外的 {@code service/security/rpc/security.go:43}。）
 * ⚠️ 「RAG 侧有 {@code GrpcServerConfigurer}」只说明<b>注册动作发生</b>，
 * 不代表拦截器<b>带着配置的 timeout</b> —— 后者要断言运行时后果，见
 * {@code ObservabilityWiringTest} 的说明。
 *
 * <p><b>timeout（H1 修复）</b>：从 {@link ObservabilityProperties#getRpcTimeout()} 读取，
 * 为 null 时回退 {@link Duration#ZERO}（不限时）。此前硬编码 {@code Duration.ZERO}，
 * 导致即使 Bean 被注册也没有 gRPC 超时。Go 侧 user 系 yaml 未配置 {@code Timeout}，
 * 实际生效的是 go-zero {@code RpcServerConf.Timeout} 默认值 2000ms，
 * 由各模块 yaml 的 {@code observability.rpc-timeout: 2s} 承载。
 *
 * <p><b>slowThreshold</b>：从 {@link ObservabilityProperties#getSlowThreshold()} 读取，
 * 为 null 时回退到 {@link Observability#slowThreshold()}（env var fallback）。
 */
@Component
public class RpcServerInterceptorFactory {

    /**
     * gRPC Unary ServerInterceptor Bean。
     *
     * <p>⚠️ <b>产出本 Bean 不足以让它生效</b> —— starter 不会自动注册（见类注释的 javap 证据）。
     * 消费方必须再提供 {@code @Bean GrpcServerConfigurer}，否则它是容器里一个没人用的 Bean。
     * 锁死用例：各 rpc 模块的 {@code *GrpcInterceptorWiringTest}（断言 {@code server.intercept(...)} 真被调用）。
     */
    @Bean
    public ServerInterceptor rpcUnaryServerInterceptor(ObservabilityProperties props) {
        Duration slowThreshold = props != null && props.getSlowThreshold() != null
                ? props.getSlowThreshold()
                : Observability.slowThreshold();
        Duration rpcTimeout = props != null ? props.getRpcTimeout() : null;
        return RpcInterceptor.newUnaryServerInterceptor(rpcTimeout, slowThreshold);
    }
}