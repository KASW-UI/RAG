package com.rag.message.message.rpc.service;

import org.springframework.stereotype.Component;

/**
 * 跨 RPC 共享业务工具（空骨架）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/logic/helper.go}（MessageHelpers）。
 * <br>承担：
 * <ul>
 *   <li>UserID / AdminID 解析（claimUID / claimAdminID）</li>
 *   <li>RPC error → domain exception 转换</li>
 *   <li>proto ↔ DTO 转换</li>
 *   <li>聊天消息 / 通知消息 builder（ChatMessageBuilder / NotificationBuilder）</li>
 * </ul>
 *
 * <p>Phase 0：仅 {@code @Component} 占位。
 * <br>Phase 1 补 helper 方法（具体内容从 Java 镜像 {@code MessageHelpers.java} 1:1 复刻）。
 */
@Component
public class MessageRpcSupport {

    // Phase 1 补 helper 方法
}
