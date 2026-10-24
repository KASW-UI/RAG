package com.rag.like.like.rpc.service.impl;

import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeErrorMessages;
import com.rag.like.like.common.errmsg.LikeGrpcError;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.GetLikeCountResp;
import com.rag.like.like.proto.GetLikeStateReq;
import com.rag.like.like.proto.GetLikeStateResp;
import com.rag.like.like.proto.GetTargetLikerListReq;
import com.rag.like.like.proto.GetTargetLikerListResp;
import com.rag.like.like.proto.GetUserLikeListReq;
import com.rag.like.like.proto.GetUserLikeListResp;
import com.rag.like.like.proto.GetUserTotalLikeReq;
import com.rag.like.like.proto.GetUserTotalLikeResp;
import com.rag.like.like.proto.LikeActionReq;
import com.rag.like.like.proto.LikeActionResp;
import com.rag.like.like.proto.LikeServiceGrpc;
import com.rag.like.like.rpc.exception.LikeExceptions;
import com.rag.like.like.rpc.service.LikeDomainService;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * gRPC 服务入口 —— 对应 Go 端 {@code internal/server/likeserviceserver.go} 的
 * {@code LikeServiceServer}（6 个方法，每个方法两行：new logic + 调用）。
 *
 * <p><b>Go 的 server 层没有任何逻辑</b>，只有「构造 logic 并转发」；
 * 全部业务在 {@code internal/logic/*}。本类对应 Java 侧的
 * {@link LikeDomainService}，保持同样的分层。
 *
 * <h2>错误出口（唯一转换点）</h2>
 * <p>业务层抛 {@link LikeExceptions.Rpc}（带业务码），
 * 这里统一转成 {@code LikeGrpcError.grpcError(bizCode, msg)} 上线。
 * 其它 {@link RuntimeException} 兜底为 5001（与 message-rpc 的兜底一致）。
 *
 * <p>⚠️ <b>兜底必须是 5001 而不是「把异常原文发给客户端」</b>：
 * 原生异常消息会泄漏 SQL / 连接串等内部信息。
 */
@GrpcService
@Component
public class LikeServiceGrpcImpl extends LikeServiceGrpc.LikeServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(LikeServiceGrpcImpl.class);

    private final LikeDomainService domainService;

    public LikeServiceGrpcImpl(LikeDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void likeAction(LikeActionReq request, StreamObserver<LikeActionResp> responseObserver) {
        invoke(() -> domainService.likeAction(request), responseObserver);
    }

    @Override
    public void getLikeCount(GetLikeCountReq request, StreamObserver<GetLikeCountResp> responseObserver) {
        invoke(() -> domainService.getLikeCount(request), responseObserver);
    }

    @Override
    public void getLikeState(GetLikeStateReq request, StreamObserver<GetLikeStateResp> responseObserver) {
        invoke(() -> domainService.getLikeState(request), responseObserver);
    }

    @Override
    public void getUserLikeList(GetUserLikeListReq request, StreamObserver<GetUserLikeListResp> responseObserver) {
        invoke(() -> domainService.getUserLikeList(request), responseObserver);
    }

    @Override
    public void getTargetLikerList(GetTargetLikerListReq request,
                                   StreamObserver<GetTargetLikerListResp> responseObserver) {
        invoke(() -> domainService.getTargetLikerList(request), responseObserver);
    }

    @Override
    public void getUserTotalLike(GetUserTotalLikeReq request,
                                 StreamObserver<GetUserTotalLikeResp> responseObserver) {
        invoke(() -> domainService.getUserTotalLike(request), responseObserver);
    }

    /**
     * 统一出口模板。
     *
     * <p><b>必须用 {@code observer.onError(...)} 而不是让异常逃逸</b>：
     * 异常若逃出 listener 回调，grpc-java 会把它当成<b>传输层内部错误</b>，
     * description 被替换成 {@code "Application error processing RPC"}，
     * 业务码与原始 msg 当场全丢。like-common 的 {@code LikeGrpcErrorRoundTripTest}
     * 已实测过这一点（throw ⇒ description 被覆盖；onError ⇒ 原样保留）。
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (LikeExceptions.Rpc business) {
            // 带业务码的领域异常 ⇒ 还原成 Go 的 status.Error(codes.Code(bizCode), msg)
            // ⚠️ 必须用 getMsg()（原始文案）而不是 getMessage()（ErrCode:X, Errmsg:Y 的包装格式），
            //    后者会导致 LikeGrpcError 再包一层，客户端看到的 msg 与 Go 不一致。
            observer.onError(LikeGrpcError.grpcError(business.getBizCode(), business.getMsg()));
        } catch (StatusRuntimeException sre) {
            // 领域层理论上不抛 gRPC 异常（message-rpc 允许，这里同样放行以防将来引入）
            observer.onError(sre);
        } catch (RuntimeException e) {
            LOG.error("like-rpc handler failed", e);
            // ⚠️ 兜底也必须走 LikeGrpcError（而不是裸 Status.INTERNAL）：
            //    like-api 侧 LikeApiSupport#rpcError 从 trailer 取业务码、从 description 取原文。
            //    裸 Status.INTERNAL 没有 trailer ⇒ 那边只能落到默认码，
            //    envelope.code 就与 Go 的 5001 不一致了。
            //    同时：绝不能把异常原文（可能含 SQL / 连接串）发给客户端。
            observer.onError(LikeGrpcError.grpcError(
                    LikeErrorCode.ERROR_SERVER_COMMON.getCode(),
                    LikeErrorMessages.getMsg(LikeErrorCode.ERROR_SERVER_COMMON.getCode())));
        }
    }
}