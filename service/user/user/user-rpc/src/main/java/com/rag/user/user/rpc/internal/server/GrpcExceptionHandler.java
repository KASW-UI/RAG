package com.rag.user.user.rpc.internal.server;

import com.rag.user.user.rpc.internal.model.ErrorCode.BusinessException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.server.advice.GrpcAdvice;
import net.devh.boot.grpc.server.advice.GrpcExceptionHandler;

/**
 * 对应 Go: Logic 内部手写的 status.Error(codes.Xxx, "msg")
 *
 * Go 端每个 Logic 失败分支都直接 `return nil, status.Error(codes.NotFound, "用户不存在")`,
 * 错误码 + 描述就地写,9 个 logic 加起来有几十处。
 *
 * Java 端把"业务错误 → gRPC status"的映射集中到 ErrorCode enum + 这里的一个全局拦截器,
 * Logic 不再散落 magic number,异常路径由 @GrpcAdvice 统一处理。
 *
 * 工作原理:
 *   - Logic 抛 BusinessException
 *   - @GrpcExceptionHandler(BusinessException.class) 拦截
 *   - 翻译成 StatusRuntimeException,grpc-spring-boot-starter 把 Status 序列化到 response trailer
 *   - Client 侧 io.grpc.stub 抛 StatusRuntimeException
 */
@GrpcAdvice
public class GrpcExceptionHandler {

    @GrpcExceptionHandler(BusinessException.class)
    public StatusRuntimeException handleBusinessException(BusinessException e) {
        return Status.INTERNAL
                .withCause(e)
                .withDescription(e.getMessage())
                .asRuntimeException();
    }
}