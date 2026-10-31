package com.rag.hot.hot.rpc;

import com.rag.hot.hot.proto.CoinArticleRequest;
import com.rag.hot.hot.proto.CommentArticleRequest;
import com.rag.hot.hot.proto.GetHotArticlesRequest;
import com.rag.hot.hot.proto.HotArticleItem;
import com.rag.hot.hot.proto.HotServiceGrpc;
import com.rag.hot.hot.proto.LikeArticleRequest;
import com.rag.hot.hot.proto.ShareArticleRequest;
import com.rag.hot.hot.rpc.service.HotRankingQueryService;
import com.rag.hot.hot.rpc.service.InteractionStubService;
import com.rag.hot.hot.rpc.service.impl.HotServiceGrpcImpl;
import com.rag.hot.hot.rpc.support.RealGrpcPort;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.QueryTimeoutException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * hot-rpc 的<b>gRPC 失败路径矩阵</b>（真 gRPC server · 5 个方法 × 各自的失败/成功形态）。
 *
 * <h2>为什么要有它（基线缺口 G-13 · P0 #2）</h2>
 * <p>hot 域有两个对外面：<b>hot-api 的 1 个 HTTP 端点</b>（见 {@code HotApiFailurePathMatrixTest}）
 * ＋ <b>hot-rpc 的 5 个 gRPC 方法</b>（本类）。
 * <p>已有的 {@code HotServiceGrpcImplErrorMappingTest} 只在 <b>observer 回调层</b>断言
 * ⇒ 它证明不了「这个 status 能不能<b>穿过 netty 传输层</b>到达客户端」。
 * ⇒ 本类起一个<b>真 gRPC server</b>，用<b>真 client</b>调 ⇒ 断言的是<b>线上字节</b>。
 *
 * <h2>⚠️ 本域最容易被「统一风格」抹掉的不对称</h2>
 * <pre>
 *   GetHotArticles  → 正常返回 items；Redis 挂掉 ⇒ **UNKNOWN**（Go 裸 error 的落地码）
 *   LikeArticle     → UNIMPLEMENTED "LikeArticle not implemented yet"
 *   CoinArticle     → UNIMPLEMENTED "CoinArticle not implemented yet"
 *   ShareArticle    → UNIMPLEMENTED "ShareArticle not implemented yet"
 *   CommentArticle  → **success = true**（唯一不对称的那个）
 * </pre>
 * ⚠️ 把 4 个互动方法统一成「全是 UNIMPLEMENTED」或「全是 success」
 * ⇒ 客户端拿到的结果就变了（CommentArticle 从成功变成错误码）⇒ 那是<b>行为变更</b>。
 *
 * <h2>Go 出处（逐条）</h2>
 * <ul>
 *   <li>{@code service/hot/rpc/internal/logic/like_article_logic.go:44} ⇒ UNIMPLEMENTED；</li>
 *   <li>{@code service/hot/rpc/internal/logic/coin_article_logic.go:36} ⇒ UNIMPLEMENTED；</li>
 *   <li>{@code service/hot/rpc/internal/logic/share_article_logic.go:36} ⇒ UNIMPLEMENTED；</li>
 *   <li>{@code service/hot/rpc/internal/logic/comment_article_logic.go:30} ⇒ success=true；</li>
 *   <li>{@code service/hot/rpc/internal/logic/get_hot_articles_logic.go:44-51} ⇒
 *       {@code redis.Nil} ⇒ 空列表；其它 error ⇒ {@code return nil, err}
 *       ⇒ grpc-go 的 {@code status.FromError} 对非 status error 返回 ok=false ⇒ <b>UNKNOWN</b>。</li>
 * </ul>
 *
 * <h2>⚠️ 证据强度</h2>
 * <p>本类<b>不</b>声称双侧活体对拍（没有 Go 侧进程）⇒ 证据强度 <b>R3</b>
 * （有 Go 出处 + 有 RAG 侧自动化断言 + 变异必红）。
 */
class HotRpcFailurePathMatrixTest {

    private static Server server;
    private static ManagedChannel channel;
    private static HotServiceGrpc.HotServiceBlockingStub stub;

    /**
     * Redis 侧的替身 —— 默认让每次调用都抛「Redis 不可用」。
     *
     * <p>⚠️ 为什么用 mock 而不是真 Redis：本类要验的是
     * <b>「Redis 失败 ⇒ gRPC status 是 UNKNOWN 而不是 INTERNAL」</b>这条映射
     * ⇒ 必须让数据层<b>失败</b>；用真 Redis 反而要把它弄挂（不可重复、且会污染别的会话）。
     * ⇒ 本类<b>不</b>声称覆盖 Redis 客户端本身的行为。
     */
    private static final HotRankingQueryService RANKING_QUERY =
            Mockito.mock(HotRankingQueryService.class);

    @BeforeAll
    static void startServer() throws IOException {
        int port = RealGrpcPort.pick();
        server = ServerBuilder.forPort(port)
                .addService(new HotServiceGrpcImpl(RANKING_QUERY, new InteractionStubService()))
                .build()
                .start();
                // 🔴 **必须用显式 `dns:///` scheme，不能用 `forAddress(...)`**（2026-10-31 实测踩坑）：
                //    devh 的 `GrpcClientAutoConfiguration:124` 会在**起 Spring 上下文**时
                //    把自己的 `discovery` NameResolverProvider 注册进 **grpc-java 的进程级全局**
                //    `NameResolverRegistry.getDefaultRegistry()` ⇒ ⇒ **一旦本 JVM 里起过一次 devh 上下文，
                //    该注册就是进程级的** ⇒ ⇒ 之后**任何**无显式 scheme 的手搓 channel
                //    （`ManagedChannelBuilder.forAddress("127.0.0.1", P)`）都会被 devh 的 discovery resolver
                //    **接管** ⇒ ⇒ 把 `127.0.0.1:P` 当**服务名**去 Nacos 查 ⇒ ⇒ `UNAVAILABLE`
                //    ⇒ ⇒ 实测表现：同一个模块里，某个 `@SpringBootTest` 先跑 ⇒ 本条就红；
                //    单独跑本类 ⇒ 5/5 全绿 ⇒ ⇒ **「单跑绿、同跑红」＝ 上下文污染，不是判据问题**。
                //    ⇒ 显式 `dns:///` 直接选定 grpc-java 自带的 DNS resolver ⇒ 不受 devh 注册影响。
        channel = ManagedChannelBuilder.forTarget("dns:///127.0.0.1:" + port).usePlaintext().build();
        stub = HotServiceGrpc.newBlockingStub(channel);
    }

    /**
     * 每个用例开始前把替身复位成「Redis 失败」——
     * 免得某个用例改了桩之后**污染后面的用例**（用例顺序不保证）。
     */
    @BeforeEach
    void resetRankingQuery() {
        Mockito.reset(RANKING_QUERY);
        when(RANKING_QUERY.getHotArticles(anyInt()))
                .thenThrow(new QueryTimeoutException("Redis ZRevRangeWithScores failed: timeout"));
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow();
            channel.awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ==================================================================
    // 0. 端口段自检
    // ==================================================================

    @Test
    @DisplayName("真 gRPC server 必须绑到本域专用端口段 22500-22579（不许落进 OS 临时段）")
    void bindsToDedicatedPortSegment() {
        int port = server.getPort();
        assertTrue(port >= RealGrpcPort.FIRST && port <= RealGrpcPort.LAST,
                "gRPC server 实际端口必须在 " + RealGrpcPort.FIRST + "-" + RealGrpcPort.LAST
                        + " 内，实际=" + port);
    }

    // ==================================================================
    // 1. GetHotArticles —— 成功 / Redis 失败
    // ==================================================================

    @Test
    @DisplayName("GetHotArticles·正常 ⇒ 200 等价（无错误）＋ items 原样下发")
    void getHotArticlesSucceeds() {
        // ⚠️ 必须用 doReturn(...).when(...) 而不是 when(...).thenReturn(...)：
        //    后者会**真的调用一次** mock 方法来记录桩 ⇒ 而此时 mock 已被桩成「抛异常」
        //    ⇒ 异常在**测试线程**里就炸了，根本到不了 server。
        org.mockito.Mockito.doReturn(List.of(
                        HotArticleItem.newBuilder().setArticleId("a1").setHotScore(42).build()))
                .when(RANKING_QUERY).getHotArticles(anyInt());

        var resp = stub.getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(20).build());

        assertEquals(1, resp.getItemsCount());
        assertEquals("a1", resp.getItems(0).getArticleId());
        assertEquals(42, resp.getItems(0).getHotScore());
    }

    @Test
    @DisplayName("GetHotArticles·Redis 失败 ⇒ **UNKNOWN**（Go 裸 error 的落地码），**不是** INTERNAL")
    void redisFailureMapsToUnknownNotInternal() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.getHotArticles(GetHotArticlesRequest.newBuilder().setTopK(20).build()));

        assertEquals(Status.Code.UNKNOWN, e.getStatus().getCode(),
                "Go: get_hot_articles_logic.go:50 `return nil, err` 是**裸 error** ⇒ "
                        + "grpc-go 的 status.FromError 返回 ok=false ⇒ 落到 codes.Unknown");
        assertTrue(e.getStatus().getDescription().contains("Redis ZRevRangeWithScores failed"),
                "Go: description 就是原始错误文本（status.FromError 对非 status error 取 err.Error()），实际 = "
                        + e.getStatus().getDescription());
    }

    // ==================================================================
    // 2. 三个 UNIMPLEMENTED（逐条带精确文案）
    // ==================================================================

    @Test
    @DisplayName("LikeArticle ⇒ UNIMPLEMENTED + 精确文案 \"LikeArticle not implemented yet\"")
    void likeArticleIsUnimplemented() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.likeArticle(LikeArticleRequest.newBuilder().setArticleId("a1").build()));

        assertEquals(Status.Code.UNIMPLEMENTED, e.getStatus().getCode(),
                "Go: like_article_logic.go:44 status.Error(codes.Unimplemented, …)");
        assertEquals("LikeArticle not implemented yet", e.getStatus().getDescription(),
                "Go: like_article_logic.go:44 的文案逐字照抄");
    }

    @Test
    @DisplayName("CoinArticle ⇒ UNIMPLEMENTED + 精确文案 \"CoinArticle not implemented yet\"")
    void coinArticleIsUnimplemented() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.coinArticle(CoinArticleRequest.newBuilder().setArticleId("a1").build()));

        assertEquals(Status.Code.UNIMPLEMENTED, e.getStatus().getCode(),
                "Go: coin_article_logic.go:36 status.Error(codes.Unimplemented, …)");
        assertEquals("CoinArticle not implemented yet", e.getStatus().getDescription(),
                "Go: coin_article_logic.go:36 的文案逐字照抄");
    }

    @Test
    @DisplayName("ShareArticle ⇒ UNIMPLEMENTED + 精确文案 \"ShareArticle not implemented yet\"")
    void shareArticleIsUnimplemented() {
        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> stub.shareArticle(ShareArticleRequest.newBuilder().setArticleId("a1").build()));

        assertEquals(Status.Code.UNIMPLEMENTED, e.getStatus().getCode(),
                "Go: share_article_logic.go:36 status.Error(codes.Unimplemented, …)");
        assertEquals("ShareArticle not implemented yet", e.getStatus().getDescription(),
                "Go: share_article_logic.go:36 的文案逐字照抄");
    }

    // ==================================================================
    // 3. 唯一不对称的那个
    // ==================================================================

    @Test
    @DisplayName("CommentArticle ⇒ **success = true**（Go 唯一不报 Unimplemented 的互动方法）")
    void commentArticleSucceeds() {
        var resp = stub.commentArticle(CommentArticleRequest.newBuilder().setArticleId("a1").build());

        assertTrue(resp.getSuccess(),
                "Go: comment_article_logic.go:30 `return &pb.CommentArticleResponse{Success: true}, nil`");
    }

    // ==================================================================
    // 4. 未知方法（未注册）⇒ UNIMPLEMENTED（框架默认）
    // ==================================================================

    @Test
    @DisplayName("未注册的 RPC 名 ⇒ UNIMPLEMENTED（Go 侧 UnimplementedHotServiceServer 的默认行为）")
    void unknownMethodIsUnimplemented() {
        // Go: hot_service_server.go:12 内嵌 pb.UnimplementedHotServiceServer
        //   ⇒ 未实现的方法由 grpc-go 框架回 UNIMPLEMENTED
        // 走 channel 的通用 unary 调用（绕开生成的 stub）
        io.grpc.MethodDescriptor<com.google.protobuf.BytesValue, com.google.protobuf.BytesValue> md =
                io.grpc.MethodDescriptor.<com.google.protobuf.BytesValue, com.google.protobuf.BytesValue>newBuilder()
                        .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName("hot.HotService/NoSuchMethod")
                        .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                com.google.protobuf.BytesValue.getDefaultInstance()))
                        .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                                com.google.protobuf.BytesValue.getDefaultInstance()))
                        .build();

        StatusRuntimeException e = assertThrows(StatusRuntimeException.class,
                () -> io.grpc.stub.ClientCalls.blockingUnaryCall(
                        channel, md, io.grpc.CallOptions.DEFAULT,
                        com.google.protobuf.BytesValue.getDefaultInstance()));

        assertEquals(Status.Code.UNIMPLEMENTED, e.getStatus().getCode(),
                "Go: 未实现的方法 ⇒ grpc-go 回 UNIMPLEMENTED");
    }
}
