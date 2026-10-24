package com.rag.comment.comment.rpc.service.impl;

import com.rag.comment.comment.common.errmsg.CommentErrorCode;
import com.rag.comment.comment.common.errmsg.CommentErrorMessages;
import com.rag.comment.comment.common.errmsg.CommentGrpcError;
import com.rag.comment.comment.proto.CommentServiceGrpc;
import com.rag.comment.comment.proto.CreateCommentReq;
import com.rag.comment.comment.proto.CreateCommentResp;
import com.rag.comment.comment.proto.DeleteCommentReq;
import com.rag.comment.comment.proto.DeleteCommentResp;
import com.rag.comment.comment.proto.GetCommentReq;
import com.rag.comment.comment.proto.GetCommentResp;
import com.rag.comment.comment.proto.LikeCommentReq;
import com.rag.comment.comment.proto.LikeCommentResp;
import com.rag.comment.comment.proto.ManageCommentReq;
import com.rag.comment.comment.proto.ManageCommentResp;
import com.rag.comment.comment.proto.ReportCommentReq;
import com.rag.comment.comment.proto.ReportCommentResp;
import com.rag.comment.comment.proto.UpdateSubjectStateReq;
import com.rag.comment.comment.proto.UpdateSubjectStateResp;
import com.rag.comment.comment.rpc.exception.CommentExceptions;
import com.rag.comment.comment.rpc.service.CommentAuditListener;
import com.rag.comment.comment.rpc.service.CommentDomainService;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * gRPC 服务入口 —— 对应 Go 端 {@code internal/server/comment_service_server.go:25-57} 的
 * {@code CommentServiceServer}（7 个方法，每个方法两行：new logic + 调用）。
 *
 * <p><b>Go 的 server 层没有任何逻辑</b>，只有「构造 logic 并转发」；
 * 全部业务在 {@code internal/logic/*}。本类对应 Java 侧的
 * {@link CommentDomainService}，保持同样的分层。
 *
 * <h2>错误出口（唯一转换点）</h2>
 * <p>业务层抛 {@link CommentExceptions.Rpc}（带业务码），
 * 这里统一转成 {@link CommentGrpcError#grpcError(int, String)} 上线；
 * 其它 {@link RuntimeException} 兜底为 5001。
 *
 * <p>⚠️ <b>兜底必须是 5001 而不是「把异常原文发给客户端」</b>：
 * 原生异常消息会泄漏 SQL / 连接串等内部信息。
 *
 * <p>⚠️ <b>必须用 {@code observer.onError(...)} 而不是让异常逃逸</b>：
 * 异常若逃出 listener 回调，grpc-java 会当成<b>传输层内部错误</b>，
 * description 被替换成 {@code "Application error processing RPC"}，
 * 业务码与原始 msg 当场全丢。
 */
@GrpcService
@Component
public class CommentServiceGrpcImpl extends CommentServiceGrpc.CommentServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(CommentServiceGrpcImpl.class);

    private final CommentDomainService domainService;

    public CommentServiceGrpcImpl(CommentDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void createComment(CreateCommentReq request, StreamObserver<CreateCommentResp> responseObserver) {
        invoke(() -> domainService.createComment(request), responseObserver);
    }

    @Override
    public void getComment(GetCommentReq request, StreamObserver<GetCommentResp> responseObserver) {
        invoke(() -> domainService.getComment(request), responseObserver);
    }

    @Override
    public void deleteComment(DeleteCommentReq request, StreamObserver<DeleteCommentResp> responseObserver) {
        invoke(() -> domainService.deleteComment(request), responseObserver);
    }

    @Override
    public void likeComment(LikeCommentReq request, StreamObserver<LikeCommentResp> responseObserver) {
        invoke(() -> domainService.likeComment(request), responseObserver);
    }

    @Override
    public void reportComment(ReportCommentReq request, StreamObserver<ReportCommentResp> responseObserver) {
        invoke(() -> domainService.reportComment(request), responseObserver);
    }

    @Override
    public void manageComment(ManageCommentReq request, StreamObserver<ManageCommentResp> responseObserver) {
        invoke(() -> domainService.manageComment(request), responseObserver);
    }

    @Override
    public void updateSubjectState(UpdateSubjectStateReq request,
                                   StreamObserver<UpdateSubjectStateResp> responseObserver) {
        invoke(() -> domainService.updateSubjectState(request), responseObserver);
    }

    /**
     * 统一出口模板。
     *
     * @see CommentAuditListener#onMessage 是另一条链路（Kafka），错误出口方向不同，
     *      别把两处混用：gRPC 出口必须走 {@code CommentGrpcError}，
     *      Kafka 出口必须按 audit_consumer.go 的四条方向处理。
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (CommentExceptions.Rpc business) {
            // 带业务码的领域异常 ⇒ 还原成 Go 的 status.Error(codes.Code(bizCode), msg)
            // ⚠️ 必须用 getMsg()（原始文案）而不是 getMessage()（ErrCode:X, Errmsg:Y 的包装格式），
            //    后者会导致 description 变成双重包装，客户端看到的 msg 与 Go 不一致。
            observer.onError(CommentGrpcError.grpcError(business.getBizCode(), business.getMsg()));
        } catch (StatusRuntimeException sre) {
            observer.onError(sre);
        } catch (RuntimeException e) {
            LOG.error("comment-rpc handler failed", e);
            // ⚠️ 兜底也必须走 CommentGrpcError（而不是裸 Status.INTERNAL）：
            //    comment-api 侧从 trailer 取业务码、从 description 取原文。
            //    裸 Status.INTERNAL 没有 trailer ⇒ 那边只能落到默认码，
            //    envelope.code 就与 Go 的 5001 不一致了。
            observer.onError(CommentGrpcError.grpcError(
                    CommentErrorCode.ERROR_SERVER_COMMON.getCode(),
                    CommentErrorMessages.getMsg(CommentErrorCode.ERROR_SERVER_COMMON.getCode())));
        }
    }
}