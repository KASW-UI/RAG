package com.rag.message.message.rpc.client;

import org.springframework.stereotype.Component;

/**
 * admin-rpc gRPC client facade（空骨架）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/svc/ServiceContext.go} 的 AdminRpc。
 * <br>调用用途：message-rpc 内部需要校验 admin 域 ID 合法性（sendNotification 等场景）。
 *
 * <p>Phase 0：仅 {@code @Component} 占位，不引 stub。
 * <br>Phase 1 接：
 * <ul>
 *   <li>{@code @GrpcClient("admin-rpc") private AdminServiceGrpc.AdminServiceBlockingStub adminStub;}</li>
 *   <li>封装 {@code getSelf(adminId)} / {@code getUserList(...)} 等 facade 方法</li>
 *   <li>对应 Go AdminRpc: getSelf / getUserList</li>
 * </ul>
 */
@Component
public class AdminRpcClient {

    // Phase 1 引入 admin-proto stub 字段
}
