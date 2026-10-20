package com.rag.message.message.rpc.bootstrap;

import org.springframework.stereotype.Component;

/**
 * Nacos 注册（空骨架）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/messageservice.go} 的 etcd registry 行为。
 * <br>Java 镜像有：{@code @EventListener(ApplicationReadyEvent)} 显式注册 + {@code @PreDestroy} 反注册。
 *
 * <p>Phase 0：仅 {@code @Component} 占位。
 * <br>Phase 1 接：
 * <ul>
 *   <li>引入 {@code spring-cloud-starter-alibaba-nacos-discovery} 依赖</li>
 *   <li>用 {@code NamingService} 显式注册（与 Java 镜像 NacosBootstrap 行为 1:1）</li>
 *   <li>{@code @PreDestroy} 反注册</li>
 * </ul>
 */
@Component
public class NacosRegistration {

    // Phase 1 引入 NamingService 字段
}
