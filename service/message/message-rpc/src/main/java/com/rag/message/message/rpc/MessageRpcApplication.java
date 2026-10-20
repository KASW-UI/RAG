package com.rag.message.message.rpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * message-rpc 启动入口（空骨架）。
 *
 * <p>Phase 0 目标：建目录树 + 最小可编译壳，不接业务。
 * <br>Phase 1 待接：
 * <ul>
 *   <li>{@code scanBasePackages} 加入 {@code com.rag.common}（ServiceContext / NacosBootstrap 来源）</li>
 *   <li>{@code @EnableConfigurationProperties(MessageProperties.class)} 显式激活</li>
 *   <li>gRPC server 端口（来自 grpc.server.port 配置）由 devh starter 接管</li>
 * </ul>
 *
 * <p>对应 Go 端 {@code service/message/rpc/messageservice.go} 的 ListenOn 启动路径。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.message.message.rpc",
        // Phase 1: "com.rag.common"  // ServiceContext / NacosBootstrap / Metrics
})
@ConfigurationPropertiesScan(basePackages = "com.rag.message.message.rpc.config")
public class MessageRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageRpcApplication.class, args);
    }
}
