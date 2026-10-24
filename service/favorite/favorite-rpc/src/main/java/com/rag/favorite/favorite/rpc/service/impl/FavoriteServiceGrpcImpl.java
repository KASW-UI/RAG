package com.rag.favorite.favorite.rpc.service.impl;

import com.rag.favorite.favorite.common.errmsg.FavoriteErrorCode;
import com.rag.favorite.favorite.common.errmsg.FavoriteErrorMessages;
import com.rag.favorite.favorite.common.errmsg.FavoriteGrpcError;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderReq;
import com.rag.favorite.favorite.proto.CreateFavoriteFolderResp;
import com.rag.favorite.favorite.proto.CreateFavoriteReq;
import com.rag.favorite.favorite.proto.CreateFavoriteResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteFolderResp;
import com.rag.favorite.favorite.proto.DeleteFavoriteReq;
import com.rag.favorite.favorite.proto.DeleteFavoriteResp;
import com.rag.favorite.favorite.proto.FavoriteServiceGrpc;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersReq;
import com.rag.favorite.favorite.proto.GetFavoriteFoldersResp;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderReq;
import com.rag.favorite.favorite.proto.GetFavoritesByFolderResp;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameReq;
import com.rag.favorite.favorite.proto.UpdateFavoriteFolderNameResp;
import com.rag.favorite.favorite.rpc.exception.FavoriteExceptions;
import com.rag.favorite.favorite.rpc.service.FavoriteDomainService;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * gRPC 服务入口 —— 对应 Go
 * {@code service/favorite/rpc/internal/server/favorite_service_server.go:26-59} 的
 * {@code FavoriteServiceServer}（7 个方法，每个方法两行：new logic + 转发）。
 *
 * <p><b>Go 的 server 层零业务逻辑</b>，全部业务在 {@code internal/logic/*}。
 * 本类对应 Java 侧的 {@link FavoriteDomainService}，保持同样的分层。
 *
 * <h2>🔴 错误出口：本域<b>唯一</b>的转换点</h2>
 *
 * <p>业务层抛 {@link FavoriteExceptions.Rpc}（<b>同时带 gRPC 码与业务码</b>），
 * 这里统一转成 {@code FavoriteGrpcError.grpcError(grpcCode, bizCode)} 上线。
 *
 * <p>⚠️ <b>必须用 {@code responseObserver.onError(...)} 而不是让异常逃逸</b>：
 * grpc-java 在 {@code UnaryServerCallListener.onHalfClose} 里调用业务方法，
 * 在那里 {@code throw} 会让异常逃出流监听回调，被当成<b>传输层内部错误</b>，
 * description 被替换成 {@code "Application error processing RPC"} ——
 * <b>文案当场全丢</b>，而文案正是本域还原业务码的唯一载体
 * （见 {@code FavoriteGrpcError} 的类注释）。
 *
 * <p>🔴 <b>与 like 域的关键差异</b>：like 的 {@code LikeGrpcError.grpcError(bizCode, msg)}
 * 把业务码塞进 {@code grpc-status}；本域的
 * {@code FavoriteGrpcError.grpcError(Status.Code, bizCode)} 是
 * <b>标准 gRPC 码 + 查表文案</b>。因此这里<b>原样透传</b>业务层给的 gRPC 码，
 * 绝不为「躲开熔断失败码」改写成 {@code UNKNOWN} —— 本域 Go 侧本来就大量发
 * {@code Internal} 与 {@code Unavailable}，它们确实计入下游失败统计。
 */
@GrpcService
@Component
public class FavoriteServiceGrpcImpl extends FavoriteServiceGrpc.FavoriteServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(FavoriteServiceGrpcImpl.class);

    private final FavoriteDomainService domainService;

    public FavoriteServiceGrpcImpl(FavoriteDomainService domainService) {
        this.domainService = domainService;
    }

    @Override
    public void createFavoriteFolder(CreateFavoriteFolderReq request,
                                      StreamObserver<CreateFavoriteFolderResp> responseObserver) {
        invoke(() -> domainService.createFavoriteFolder(request), responseObserver);
    }

    @Override
    public void createFavorite(CreateFavoriteReq request,
                               StreamObserver<CreateFavoriteResp> responseObserver) {
        invoke(() -> domainService.createFavorite(request), responseObserver);
    }

    @Override
    public void deleteFavoriteFolder(DeleteFavoriteFolderReq request,
                                     StreamObserver<DeleteFavoriteFolderResp> responseObserver) {
        invoke(() -> domainService.deleteFavoriteFolder(request), responseObserver);
    }

    @Override
    public void deleteFavorite(DeleteFavoriteReq request,
                               StreamObserver<DeleteFavoriteResp> responseObserver) {
        invoke(() -> domainService.deleteFavorite(request), responseObserver);
    }

    @Override
    public void updateFavoriteFolderName(UpdateFavoriteFolderNameReq request,
                                         StreamObserver<UpdateFavoriteFolderNameResp> responseObserver) {
        invoke(() -> domainService.updateFavoriteFolderName(request), responseObserver);
    }

    @Override
    public void getFavoriteFolders(GetFavoriteFoldersReq request,
                                   StreamObserver<GetFavoriteFoldersResp> responseObserver) {
        invoke(() -> domainService.getFavoriteFolders(request), responseObserver);
    }

    @Override
    public void getFavoritesByFolder(GetFavoritesByFolderReq request,
                                     StreamObserver<GetFavoritesByFolderResp> responseObserver) {
        invoke(() -> domainService.getFavoritesByFolder(request), responseObserver);
    }

    /**
     * 统一出口模板。
     *
     * @param handler 业务调用；抛 {@link FavoriteExceptions.Rpc} 表示可解释的业务失败
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (FavoriteExceptions.Rpc business) {
            // ⚠️ 必须用 getMsg()（原始文案）而不是 getMessage()（ErrCode:X 包装格式），
            //    后者会导致 description 被二次包装，客户端看到的 msg 与 Go 不一致。
            observer.onError(FavoriteGrpcError.grpcError(
                    business.getGrpcCode(), business.getBizCode()));
        } catch (StatusRuntimeException sre) {
            // 领域层理论上不抛 gRPC 异常；放行以防将来引入。
            observer.onError(sre);
        } catch (RuntimeException e) {
            LOG.error("favorite-rpc handler failed", e);
            // ⚠️ 兜底必须是 5001 而不是「把异常原文发给客户端」：
            //    原生异常消息会泄漏 SQL / 连接串等内部信息。
            //    走 FavoriteGrpcError 保证 favorite-api 侧能从 description 反查出业务码。
            observer.onError(FavoriteGrpcError.grpcError(
                    io.grpc.Status.Code.INTERNAL,
                    FavoriteErrorCode.ERROR_SERVER_COMMON.getCode()));
        }
    }

    /** 保留引用：兜底文案取自 favorite-common 的同一张表（避免两处硬编码不同文案）。 */
    @SuppressWarnings("unused")
    private static String fallbackMsg() {
        return FavoriteErrorMessages.getMsg(FavoriteErrorCode.ERROR_SERVER_COMMON.getCode());
    }
}