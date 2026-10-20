package com.rag.message.message.rpc.client;

import org.springframework.stereotype.Component;

/**
 * user-rpc gRPC client facade（空骨架）。
 *
 * <p>对应 Go 端 {@code service/message/rpc/internal/svc/ServiceContext.go} 的 UserRpc。
 * <br>调用用途：message-rpc 内部需要校验 user 域 ID 合法性（sendChatMessage 等场景）。
 *
 * <p>Phase 0：仅 {@code @Component} 占位，不引 stub。
 * <br>Phase 1 接：
 * <ul>
 *   <li>{@code @GrpcClient("user-rpc") private UserServiceGrpc.UserServiceBlockingStub userStub;}</li>
 *   <li>封装 {@code getUser(uid)} / {@code batchGetUsers(uids)} 等 facade 方法</li>
 *   <li>对应 Go UserRpc: getUser / batchGetUsers</li>
 * </ul>
 */
@Component
public class UserRpcClient {

    // Phase 1 引入 user-proto stub 字段
}
