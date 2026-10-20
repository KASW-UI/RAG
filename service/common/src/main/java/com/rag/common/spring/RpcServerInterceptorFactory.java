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
 * <p>grpc-spring-boot-starter（{@code net.devh:grpc-server-spring-boot-starter}）会自动把所有
 * {@code ServerInterceptor} 类型的 Bean 注册到 gRPC server，业务模块无需手写
 * {@code grpcServer.use(...)}。
 *
 * <p><b>H2 重要前提</b>：「自动注册」只对<b>已经存在于容器里</b>的 {@code ServerInterceptor} 生效。
 * 本类自己带 {@code @Component}，但按 §6.3 问题 3 的定案，{@code com.rag.common} 已不再被任何模块
 * 全包扫描（纯 gRPC 进程会把 servlet 类拖进来），所以每个 rpc 模块<b>必须</b>用
 * {@code @Import(RpcServerInterceptorFactory.class)} 显式引入，否则本 Bean 永远不存在，
 * gRPC span / deadline 传播 / gRPC 超时全部失效 —— 且不报错。对齐 Go 侧三处显式注册：
 * {@code user/rpc/user.go:32,43}、{@code admin/rpc/admin.go:33,43}、{@code hot/rpc/hot.go:32,42}。
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
     * gRPC Unary ServerInterceptor Bean（{@code @Bean} 让 grpc-spring-boot-starter 自动注册）。
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