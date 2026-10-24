package com.rag.hot.hot.rpc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.rag.hot.hot.proto.CommentArticleResponse;
import java.util.List;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 锁死 4 个互动方法的<b>不对称</b>与 gRPC 状态码 —— 对应 Go
 * {@code logic/{like,comment,coin,share}_article_logic.go}。
 *
 * <p>这条不对称最容易被「统一风格」顺手抹平，而抹平就是行为变更：
 * {@code CommentArticle} 会从返回 {@code success=true} 变成返回 UNIMPLEMENTED。
 */
class InteractionStubServiceTest {

    private final InteractionStubService service = new InteractionStubService();

    @Test
    @DisplayName("like/coin/share 三个方法都是 UNIMPLEMENTED，且描述逐字对齐 Go")
    void threeMethodsAreUnimplementedWithGoDescriptions() {
        assertUnimplemented(service.likeArticle(), "LikeArticle not implemented yet");
        assertUnimplemented(service.coinArticle(), "CoinArticle not implemented yet");
        assertUnimplemented(service.shareArticle(), "ShareArticle not implemented yet");
    }

    @Test
    @DisplayName("commentArticle 是唯一不对称的那个：返回 success=true 且不抛异常")
    void commentArticleReturnsSuccessTrue() {
        CommentArticleResponse response = service.commentArticle();
        assertThat(response.getSuccess()).isTrue();
    }

    @Test
    @DisplayName("不对称本身：4 个方法里恰好 3 个 UNIMPLEMENTED、1 个 success")
    void asymmetryIsExactlyThreeToOne() {
        // 桩方法是「返回待抛出的异常」而非直接抛出（Go 侧是 return nil, status.Error(...)）
        List<StatusRuntimeException> stubs = List.of(
                service.likeArticle(), service.coinArticle(), service.shareArticle());

        long unimplemented = stubs.stream()
                .filter(e -> e.getStatus().getCode() == Status.Code.UNIMPLEMENTED)
                .count();
        long success = service.commentArticle().getSuccess() ? 1 : 0;

        assertThat(unimplemented).isEqualTo(3);
        assertThat(success).isEqualTo(1);
    }

    private static void assertUnimplemented(StatusRuntimeException e, String expectedDescription) {
        assertThat(e).isNotNull();
        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(e.getStatus().getDescription()).isEqualTo(expectedDescription);
    }

    @Test
    @DisplayName("request 参数一律被忽略：三个 UNIMPLEMENTED 方法不接收任何业务参数")
    void requestsAreIgnored() {
        // Go 侧连字段都没读；桩实现也不因参数而改变行为
        assertThat(service.likeArticle().getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(service.coinArticle().getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        assertThat(service.shareArticle().getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
    }
}
