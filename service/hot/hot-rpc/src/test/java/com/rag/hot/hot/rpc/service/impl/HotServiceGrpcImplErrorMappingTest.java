package com.rag.hot.hot.rpc.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.GetHotArticlesResponse;
import com.rag.hot.hot.proto.LikeArticleRequest;
import com.rag.hot.hot.proto.LikeArticleResponse;
import com.rag.hot.hot.proto.CommentArticleRequest;
import com.rag.hot.hot.proto.CommentArticleResponse;
import com.rag.hot.hot.proto.HotArticleItem;
import com.rag.hot.hot.rpc.service.HotRankingQueryService;
import com.rag.hot.hot.rpc.service.InteractionStubService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

/**
 * 锁死 gRPC 边界的<b>错误码映射</b>。
 *
 * <p>⚠️ 这里最容易被 message-rpc 的模板带偏：message-rpc 的兜底分支统一映射 {@code INTERNAL}，
 * 但 hot-rpc <b>不能</b>这样写。Go 侧 {@code get_hot_articles_logic.go:50} 返回的是<b>裸 error</b>，
 * grpc-go 的 {@code status.FromError} 对非 status error 返回 {@code ok=false}
 * ⇒ 落到 {@code codes.Unknown}，而不是 INTERNAL。
 */
class HotServiceGrpcImplErrorMappingTest {

    private final HotRankingQueryService rankingQuery = mock(HotRankingQueryService.class);
    private final HotServiceGrpcImpl impl =
            new HotServiceGrpcImpl(rankingQuery, new InteractionStubService());

    @Test
    @DisplayName("Redis 失败 → UNKNOWN（Go 裸 error 的落地码），不是 INTERNAL")
    void redisFailureMapsToUnknownNotInternal() {
        when(rankingQuery.getHotArticles(anyInt()))
                .thenThrow(new QueryTimeoutException("Redis ZRevRangeWithScores failed: timeout"));

        CapturingObserver<GetHotArticlesResponse> observer = new CapturingObserver<>();
        impl.getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(20).build(), observer);

        assertThat(observer.values).isEmpty();
        assertThat(observer.error).isNotNull();
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
                .as("Go 的裸 error 经 status.FromError 落到 UNKNOWN；写成 INTERNAL 就是行为变更")
                .isEqualTo(Status.Code.UNKNOWN);
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
                .isNotEqualTo(Status.Code.INTERNAL);
    }

    @Test
    @DisplayName("显式 StatusRuntimeException 原样转发（like → UNIMPLEMENTED）")
    void statusRuntimeExceptionIsForwardedAsIs() {
        CapturingObserver<LikeArticleResponse> observer = new CapturingObserver<>();
        impl.likeArticle(LikeArticleRequest.newBuilder().setArticleId("a1").build(), observer);

        assertThat(observer.values).isEmpty();
        assertThat(observer.error).isNotNull();
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(((StatusRuntimeException) observer.error).getStatus().getDescription())
                .isEqualTo("LikeArticle not implemented yet");
    }

    @Test
    @DisplayName("commentArticle 成功返回 success=true（不对称的那个）")
    void commentArticleSucceeds() {
        CapturingObserver<CommentArticleResponse> observer = new CapturingObserver<>();
        impl.commentArticle(CommentArticleRequest.newBuilder().setArticleId("a1").build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.get(0).getSuccess()).isTrue();
        assertThat(observer.completed).isTrue();
    }

    @Test
    @DisplayName("正常路径：响应 items 原样下发并正常完成")
    void normalPathCompletesWithItems() {
        List<HotArticleItem> items = new ArrayList<>();
        items.add(HotArticleItem.newBuilder().setArticleId("a1").setHotScore(42).build());
        when(rankingQuery.getHotArticles(anyInt())).thenReturn(items);

        CapturingObserver<GetHotArticlesResponse> observer = new CapturingObserver<>();
        impl.getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(20).build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.completed).isTrue();
        assertThat(observer.values).hasSize(1);
        assertThat(observer.values.get(0).getItemsList()).hasSize(1);
        assertThat(observer.values.get(0).getItems(0).getArticleId()).isEqualTo("a1");
        assertThat(observer.values.get(0).getItems(0).getHotScore()).isEqualTo(42);
    }

    /** 捕获 StreamObserver 三个回调，用于断言 gRPC 边界的实际输出。 */
    private static final class CapturingObserver<T> implements StreamObserver<T> {
        private final List<T> values = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }

    @Test
    @DisplayName("错误对象确实是 StatusRuntimeException，可被 grpc 框架直接回传")
    void errorIsStatusRuntimeException() {
        when(rankingQuery.getHotArticles(anyInt())).thenThrow(new IllegalStateException("boom"));

        CapturingObserver<GetHotArticlesResponse> observer = new CapturingObserver<>();
        impl.getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(20).build(), observer);

        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) observer.error).getStatus().getCode())
                .isEqualTo(Status.Code.UNKNOWN);
    }
}