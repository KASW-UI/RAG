package com.rag.like.like.rpc.service.impl;

import com.rag.like.like.common.errmsg.LikeErrorCode;
import com.rag.like.like.common.errmsg.LikeGrpcError;
import com.rag.like.like.proto.GetLikeCountReq;
import com.rag.like.like.proto.GetUserTotalLikeReq;
import com.rag.like.like.proto.LikeServiceGrpc;
import com.rag.like.like.rpc.exception.LikeExceptions;
import com.rag.like.like.rpc.service.LikeDomainService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * gRPC 边界的<b>跨进程契约</b>守门。
 *
 * <p>like-api 侧 {@code LikeApiSupport#rpcError} 的还原逻辑是
 * 「业务码从 trailer 取、原文从 {@code status.getDescription()} 取」，
 * 因此本类必须保证 <b>like-rpc 发出去的东西能被那样还原</b>。
 *
 * <p>用<b>真 in-process server + 真 channel</b>发调用，而不是直接断言 {@code Status} 对象：
 * 后者只证明「同一个对象在内存里过一遍」，证明不了业务码真的穿过传输层
 * （AGENTS.md §3.3 的教训：单测全绿但机制没生效）。
 */
class LikeServiceGrpcImplWireTest {

    private static Server server;
    private static ManagedChannel channel;

    @BeforeAll
    static void startServer() throws IOException {
        String name = InProcessServerBuilder.generateName();

        // 领域层抛「与 Go 同码、同文案」的错误：
        //   Go getlikecountlogic.go:44
        //   return nil, errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "输入需要查询的ID为空")
        //   Go getusertotallikelogic.go:41
        //   return nil, errmsg.NewGrpcErr(errmsg.ErrorInputWrong, "非法的用户ID")
        LikeDomainService domain = Mockito.mock(LikeDomainService.class);
        Mockito.when(domain.getLikeCount(Mockito.any()))
                .thenThrow(LikeExceptions.inputWrong("输入需要查询的ID为空"));
        Mockito.when(domain.getUserTotalLike(Mockito.any()))
                .thenThrow(LikeExceptions.illegalUserId("非法的用户ID"));

        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                // devh 的 @GrpcService 在容器里会做同样的包装（直接 AsyncService → bindService）；
                // 这里是同一份绑定逻辑的最小复现，用于验证 wire 契约。
                .addService(LikeServiceGrpc.bindService(
                        new LikeServiceGrpcImpl(domain)))
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void shutdown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("业务码跨真 gRPC 链路无损：2001 能被 like-api 还原出来")
    void bizCodeSurvivesRealTransport() {
        StatusRuntimeException err = catchStatus(() ->
                LikeServiceGrpc.newBlockingStub(channel).getLikeCount(
                        GetLikeCountReq.newBuilder().setTargetType("article").build()));

        assertNotNull(err, "应当抛 gRPC 状态错误");
        assertEquals(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), LikeGrpcError.bizCodeFromError(err),
                "业务码没能穿过传输层 ⇒ api 侧 envelope.code 会错");
    }

    @Test
    @DisplayName("description 是 Go 的**原文**：查表会得到「输入有误」，那与 Go 不同")
    void descriptionKeepsGoMessageVerbatim() {
        StatusRuntimeException err = catchStatus(() ->
                LikeServiceGrpc.newBlockingStub(channel).getLikeCount(
                        GetLikeCountReq.newBuilder().setTargetType("article").build()));

        assertEquals("输入需要查询的ID为空", err.getStatus().getDescription());
    }

    @Test
    @DisplayName("同为 2001 的两个用例各自保留自己的文案（非法用户ID ≠ 输入有误）")
    void sameCodeKeepsItsOwnMessage() {
        StatusRuntimeException err = catchStatus(() ->
                LikeServiceGrpc.newBlockingStub(channel).getUserTotalLike(
                        GetUserTotalLikeReq.newBuilder().setUserId(0L).build()));

        assertEquals(LikeErrorCode.ERROR_INPUT_WRONG.getCode(), LikeGrpcError.bizCodeFromError(err));
        assertEquals("非法的用户ID", err.getStatus().getDescription(),
                "两个 2001 的文案不同 —— 合并成一个错误类型会改掉调用方看到的 msg");
    }

    /** 阻塞 stub 把 checked 异常包成 {@link StatusRuntimeException} 抛出。 */
    private static StatusRuntimeException catchStatus(Runnable call) {
        try {
            call.run();
            return null;
        } catch (StatusRuntimeException e) {
            return e;
        }
    }
}