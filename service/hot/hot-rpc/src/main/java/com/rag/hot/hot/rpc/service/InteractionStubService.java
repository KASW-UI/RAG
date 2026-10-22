package com.rag.hot.hot.rpc.service;

import com.rag.hot.hot.proto.CommentArticleResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.stereotype.Service;

/**
 * 4 个互动方法的<b>既定桩行为</b> —— 对应 Go
 * {@code internal/logic/{like,comment,coin,share}_article_logic.go}。
 *
 * <h2>⚠️ 必须逐个照抄的不对称</h2>
 * <pre>
 * LikeArticle     → status.Error(codes.Unimplemented, "LikeArticle not implemented yet")     like_article_logic.go:44
 * CommentArticle  → &amp;pb.CommentArticleResponse{Success: true}, nil                        comment_article_logic.go:30
 * CoinArticle     → status.Error(codes.Unimplemented, "CoinArticle not implemented yet")     coin_article_logic.go:36
 * ShareArticle    → status.Error(codes.Unimplemented, "ShareArticle not implemented yet")    share_article_logic.go:36
 * </pre>
 *
 * <p><b>3 个 UNIMPLEMENTED + 1 个 {@code success=true}</b>。
 * 若为了「统一风格」把它们全改成 UNIMPLEMENTED（或全改成 success），
 * 就是<b>行为变更</b>：客户端对 {@code CommentArticle} 会从拿到成功变成拿到错误码。
 * 把不对称集中在这一个类里，就是为了让 review 能一眼看到它。
 *
 * <p>四个方法的 request 全部<b>原样忽略</b>（Go 侧连字段都没读）。
 * 注释里承诺的未来实现是「更新 PG 计数 + 发布 {@code ArticleHotEvent} 到 Kafka」，
 * 但 Go 侧只写了 TODO —— PG 更新归属 article 域还是 hot 域尚未确定，故此处不做任何实现。
 */
@Service
public class InteractionStubService {

    /** Go {@code like_article_logic.go:44}。 */
    public static final String LIKE_UNIMPLEMENTED_MSG = "LikeArticle not implemented yet";

    /** Go {@code coin_article_logic.go:36}。 */
    public static final String COIN_UNIMPLEMENTED_MSG = "CoinArticle not implemented yet";

    /** Go {@code share_article_logic.go:36}。 */
    public static final String SHARE_UNIMPLEMENTED_MSG = "ShareArticle not implemented yet";

    /** 对应 Go {@code status.Error(codes.Unimplemented, ...)}。 */
    public StatusRuntimeException likeArticle() {
        return Status.UNIMPLEMENTED.withDescription(LIKE_UNIMPLEMENTED_MSG).asRuntimeException();
    }

    /** 对应 Go {@code coin_article_logic.go:36}。 */
    public StatusRuntimeException coinArticle() {
        return Status.UNIMPLEMENTED.withDescription(COIN_UNIMPLEMENTED_MSG).asRuntimeException();
    }

    /** 对应 Go {@code share_article_logic.go:36}。 */
    public StatusRuntimeException shareArticle() {
        return Status.UNIMPLEMENTED.withDescription(SHARE_UNIMPLEMENTED_MSG).asRuntimeException();
    }

    /**
     * <b>唯一不对称的那个</b>：Go {@code comment_article_logic.go:30}
     * {@code return &amp;pb.CommentArticleResponse{Success: true}, nil}。
     *
     * <p>方案 A 由评论系统自行推 Kafka，本 RPC 仅作同步备选。
     */
    public CommentArticleResponse commentArticle() {
        return CommentArticleResponse.newBuilder().setSuccess(true).build();
    }

    /** 供 gRPC 层复用的 UNKNOWN 构造：Go 的裸 error 经 status.FromError 后落到 codes.Unknown。 */
    public static StatusRuntimeException unknown(String description, Throwable cause) {
        return Status.UNKNOWN.withDescription(description).withCause(cause).asRuntimeException();
    }
}