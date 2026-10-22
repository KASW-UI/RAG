package com.rag.hot.hot.rpc.service.impl;

import java.util.List;
import java.util.function.Supplier;

import com.rag.hot.hot.proto.CoinArticleRequest;
import com.rag.hot.hot.proto.CoinArticleResponse;
import com.rag.hot.hot.proto.CommentArticleRequest;
import com.rag.hot.hot.proto.CommentArticleResponse;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import com.rag.hot.hot.proto.HotServiceGrpc;
import com.rag.hot.hot.proto.LikeArticleRequest;
import com.rag.hot.hot.proto.LikeArticleResponse;
import com.rag.hot.hot.proto.ShareArticleRequest;
import com.rag.hot.hot.proto.ShareArticleResponse;
import com.rag.hot.hot.rpc.service.HotRankingQueryService;
import com.rag.hot.hot.rpc.service.InteractionStubService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * gRPC 契约边界：5 个方法转发到 service 层 —— 对应 Go
 * {@code internal/server/hot_service_server.go}（46 行，纯分发，无逻辑）。
 *
 * <h2>⚠️ 错误码映射（L1，不可照抄 message-rpc 的模板）</h2>
 * Go 的 gRPC 方法返回两种 error，映射结果<b>不同</b>：
 * <pre>
 *   ① 明确的 status.Error(codes.Unimplemented, ...)
 *      → go-zero interceptor 原样传递 ⇒ codes.UNIMPLEMENTED
 *   ② 裸 error（GetHotArticles 里 Redis 非 Nil 错误：return nil, err）
 *      → grpc-go 的 status.FromError(err) 对非 status error 返回 ok=false
 *        ⇒ codes.Unknown，description 就是原始错误文本
 * </pre>
 * 因此本类<b>不能</b>像 message-rpc 的 {@code invoke} 那样把兜底分支统一成
 * {@code INTERNAL}：Go 侧这里落到的是 <b>UNKNOWN</b>。
 * 一律兜底成 INTERNAL 会让「Redis 挂了」在客户端表现为 INTERNAL，
 * 与 Go 不一致（两者的可重试语义判断也不同）。
 */
@Component
@GrpcService
public class HotServiceGrpcImpl extends HotServiceGrpc.HotServiceImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(HotServiceGrpcImpl.class);

    private final HotRankingQueryService rankingQuery;
    private final InteractionStubService interactionStub;

    public HotServiceGrpcImpl(HotRankingQueryService rankingQuery, InteractionStubService interactionStub) {
        this.rankingQuery = rankingQuery;
        this.interactionStub = interactionStub;
    }

    @Override
    public void getHotArticles(GetHotArticlesRequest request,
                               StreamObserver<GetHotArticlesResponse> responseObserver) {
        invoke(() -> {
            List<HotArticleItem> items = rankingQuery.getHotArticles(request.getTopK());
            return GetHotArticlesResponse.newBuilder().addAllItems(items).build();
        }, responseObserver);
    }

    @Override
    public void likeArticle(LikeArticleRequest request,
                            StreamObserver<LikeArticleResponse> responseObserver) {
        invoke(() -> {
            throw interactionStub.likeArticle();
        }, responseObserver);
    }

    @Override
    public void commentArticle(CommentArticleRequest request,
                               StreamObserver<CommentArticleResponse> responseObserver) {
        // ★ 唯一返回 success=true 的方法（Go comment_article_logic.go:30）
        invoke(interactionStub::commentArticle, responseObserver);
    }

    @Override
    public void coinArticle(CoinArticleRequest request,
                            StreamObserver<CoinArticleResponse> responseObserver) {
        invoke(() -> {
            throw interactionStub.coinArticle();
        }, responseObserver);
    }

    @Override
    public void shareArticle(ShareArticleRequest request,
                             StreamObserver<ShareArticleResponse> responseObserver) {
        invoke(() -> {
            throw interactionStub.shareArticle();
        }, responseObserver);
    }

    /**
     * 统一异常转发。
     *
     * <ul>
     *   <li>{@link StatusRuntimeException}：service 层已显式构造好状态（含 3 个 UNIMPLEMENTED）⇒ 原样转发。</li>
     *   <li>其它 {@link RuntimeException}：对应 Go 的<b>裸 error</b> ⇒ 映射为
     *       {@link Status#UNKNOWN}（<b>不是</b> INTERNAL，理由见类注释）。</li>
     * </ul>
     */
    private static <Resp> void invoke(Supplier<Resp> handler, StreamObserver<Resp> observer) {
        try {
            Resp resp = handler.get();
            observer.onNext(resp);
            observer.onCompleted();
        } catch (StatusRuntimeException sre) {
            observer.onError(sre);
        } catch (RuntimeException e) {
            LOG.error("[Hot] unhandled error: {}", e.getMessage(), e);
            observer.onError(Status.UNKNOWN
                    .withDescription(e.getMessage() == null ? e.getClass().getName() : e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}