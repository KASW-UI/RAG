package com.rag.task.task.rpc.service.impl;

import com.rag.task.task.proto.GetTaskReq;
import com.rag.task.task.proto.GetTaskResp;
import com.rag.task.task.proto.TaskServiceGrpc;
import com.rag.task.task.rpc.service.TaskDomainService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * {@code TaskService} 的 gRPC 入口 —— 对应 Go 端
 * {@code internal/server/task_service_server.go}。
 *
 * <h2>⚠️ Go 的 server 层没有任何逻辑</h2>
 * <p>{@code task_service_server.go:26-29} 只有「构造 logic 并转发」，
 * 全部业务在 {@code internal/logic/*}。本类保持同样的分层：
 * 薄转发 + <b>唯一的错误转换点</b>。
 *
 * <h2>错误出口：两类异常两个去向</h2>
 * <table border="1">
 *   <caption>Java → gRPC 的映射依据</caption>
 *   <tr><th>Go 侧写法</th><th>grpc-go 产出</th><th>Java 侧</th></tr>
 *   <tr><td>{@code status.Error(codes.InvalidArgument, "user_id invalid")}
 *       （get_task_logic.go:44）</td>
 *       <td>{@code INVALID_ARGUMENT} + 原文 description</td>
 *       <td>领域层抛 {@link StatusRuntimeException} ⇒ <b>透传</b></td></tr>
 *   <tr><td>{@code return nil, err}（裸 error，get_task_logic.go:59）</td>
 *       <td>{@code UNKNOWN} + {@code err.Error()} 作 description</td>
 *       <td>领域层抛任意 RuntimeException ⇒ 兜底翻成 {@code UNKNOWN}</td></tr>
 * </table>
 *
 * <h3>⚠️ {@code StatusRuntimeException} 必须透传，不能改写成 UNKNOWN</h3>
 * <p>grpc-go 的 {@code status.FromError} 见到已是 status 的 error 会<b>保留</b>它的 code。
 * 若本 catch 把 {@link StatusRuntimeException} 当成普通 RuntimeException 改写成 UNKNOWN，
 * {@code INVALID_ARGUMENT} 会退化成 {@code UNKNOWN} ⇒ 调用方
 * （Go {@code task-api} 的 {@code gettasklogic.go:39}）拿到的错误语义变了。
 *
 * <p>⚠️ task 域<b>没有 -common 模块</b>（Go 侧 service/task 下只有 api 与 rpc），
 * 所以本模块不需要 like/message 那种业务码 ↔ gRPC code 转换类 —— 与 article-rpc 同款。
 */
@GrpcService
@Component
public class TaskServiceGrpcImpl extends TaskServiceGrpc.TaskServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(TaskServiceGrpcImpl.class);

    private final TaskDomainService domainService;

    public TaskServiceGrpcImpl(TaskDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void getTask(GetTaskReq request, StreamObserver<GetTaskResp> responseObserver) {
        invoke(() -> domainService.getTask(request), responseObserver);
    }

    /**
     * 统一出口模板。
     *
     * <p><b>必须用 {@code observer.onError(...)} 而不是让异常逃逸</b>：
     * 异常若逃出 listener 回调，grpc-java 会把它当成<b>传输层内部错误</b>，
     * description 被替换成 {@code "Application error processing RPC"}，
     * 错误码与原始文案当场全丢（article-rpc 的同款实测见其类注释）。
     *
     * <p>⚠️ {@link Error} 不捕获：让容器线程的默认处理接管，
     * 与 grpc-java 默认行为一致（不把 {@code OutOfMemoryError} 当成业务错误）。
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (StatusRuntimeException e) {
            // 🔴 必须透传（见类注释）：INVALID_ARGUMENT 等已定好的 code 不能被抹成 UNKNOWN
            observer.onError(e);
        } catch (RuntimeException e) {
            // Go 侧 `return nil, err`（裸 error）⇒ grpc-go 转成 UNKNOWN
            LOG.error("task-rpc handler failed", e);
            observer.onError(new StatusRuntimeException(
                    Status.UNKNOWN.withDescription(e.getMessage()).withCause(e)));
        }
    }
}