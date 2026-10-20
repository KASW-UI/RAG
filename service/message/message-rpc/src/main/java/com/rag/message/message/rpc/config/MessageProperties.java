package com.rag.message.message.rpc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * message-rpc 业务配置（空骨架）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/config/config.go} 的 List / Broadcast 段。
 * <br>Phase 1 落地：
 * <ul>
 *   <li>{@code message.list.default-limit}（Go: List.DefaultLimit=20）</li>
 *   <li>{@code message.list.max-limit}（Go: List.MaxLimit=100）</li>
 *   <li>{@code message.broadcast.page-size}（Go: Broadcast.PageSize=200）</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "message")
public class MessageProperties {

    // Phase 1 填字段
}
