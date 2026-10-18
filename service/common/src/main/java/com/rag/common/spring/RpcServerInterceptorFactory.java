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
 * <p><b>timeout 默认值</b>：{@link Duration#ZERO}（不限时）。grpc 默认无超时，业务如需可通过
 * {@code @Value("${observability.rpc.timeout:0ms}")} 或 {@link ObservabilityProperties}
 * 扩展覆盖。
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
        return RpcInterceptor.newUnaryServerInterceptor(Duration.ZERO, slowThreshold);
    }
}