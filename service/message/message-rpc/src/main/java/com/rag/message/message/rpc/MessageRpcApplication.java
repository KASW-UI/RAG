package com.rag.message.message.rpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * message-rpc 启动入口。
 *
 * <p>对应 Go 端 {@code service/message/rpc/messageservice.go} 的 ListenOn 启动路径。
 *
 * <p>scanBasePackages 加入 {@code com.rag.common} 让 service/common 的 Bean（LoggerFactoryBean /
 * SnowflakeBean / NacosServiceManager / 等等）能被 Spring 自动注入；
 * message-rpc 自身包（{@code com.rag.message.message.rpc.*}）覆盖本模块所有 Bean。
 *
 * <p>{@code @ConfigurationPropertiesScan(basePackages = "com.rag.message.message.rpc.config")}
 * 显式激活 {@link com.rag.message.message.rpc.config.MessageProperties} 的 @ConfigurationProperties 绑定。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.message.message.rpc",
        "com.rag.common"
})
@ConfigurationPropertiesScan(basePackages = "com.rag.message.message.rpc.config")
public class MessageRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageRpcApplication.class, args);
    }
}