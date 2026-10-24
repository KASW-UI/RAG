package com.rag.comment.comment.api;

import com.rag.comment.comment.api.service.CommentApiSupport;
import com.rag.comment.comment.common.errmsg.CommentErrorCode;
import com.rag.comment.comment.common.errmsg.CommentGrpcError;
import com.rag.comment.comment.proto.CommentServiceGrpc;
import com.rag.comment.comment.proto.CreateCommentReq;
import com.rag.comment.comment.proto.CreateCommentResp;
import com.rag.common.exception.BusinessException;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 🔴 <b>comment-api 的业务码跨模块往返守门</b> —— 锁住 MISMATCH-API-01 的修复。
 *
 * <h2>为什么必须过真实传输层</h2>
 * <p>comment-rpc 把业务码放在<b>自定义 trailer</b> {@code rag-comment-biz-code}
 * （{@code CommentGrpcError.grpcError}），而 grpc-java 的 {@code Status} 只能承载 0..16 的标准码
 * ⇒ {@code grpc-status} 上只剩 {@code UNKNOWN(2)} / {@code INVALID_ARGUMENT(3)}。
 *
 * <p><b>承重假设</b>：「业务码能穿过传输层抵达 api 侧」。
 * 只断言 {@code grpcError(...).getTrailers()} 的值**证明不了**它真的穿过了传输层 ——
 * 本仓此前已多次栽在「单测全绿但真实链路上没生效」上（AGENTS.md §3.3）。
 *
 * <h2>本测试锁住的三种对端形态</h2>
 * <table border="1">
 *   <caption>api 侧 {@code rpcError} 的三条判定线索</caption>
 *   <tr><th>对端</th><th>形态</th><th>期望 envelope.code</th><th>Go 侧对应</th></tr>
 *   <tr><td><b>RAG comment-rpc</b></td><td>trailer 带业务码</td>
 *       <td><b>1012</b></td><td>Go 侧业务码在 grpc-status 里，{@code int(st.Code())} = 1012</td></tr>
 *   <tr><td><b>Go comment-rpc</b>（灰度）</td><td>grpc-status 越界 ⇒ grpc-java 降级成
 *       {@code UNKNOWN + "Unknown code 5003"}</td>
 *       <td><b>5003</b></td><td>Go 侧 {@code int(st.Code())} = 5003</td></tr>
 *   <tr><td>标准码</td><td>{@code NOT_FOUND}</td>
 *       <td><b>5</b></td><td>Go 侧 {@code int(st.Code())} = 5</td></tr>
 * </table>
 *
 * <p>⚠️ <b>修复前的行为</b>：第 1 行会拿到 <b>2</b>（UNKNOWN）而不是 1012
 * —— 这正是本轮修掉的缺陷。
 */
class CommentApiGrpcErrorWireTest {

    private static Server server;
    private static ManagedChannel channel;

    /** Go comment-rpc 的 {@code NewGrpcErr(1012, "评论区不存在")}。 */
    private static final int BIZ_CODE = CommentErrorCode.ERROR_SUBJECT_NOT_EXIST.getCode();
    private static final String BIZ_MSG = "评论区不存在";

    private static final CommentApiSupport SUPPORT = new CommentApiSupport();

    /**
     * 起一个**真 in-process gRPC server**，服务端行为与
     * {@code comment-rpc} 的 {@code CommentServiceGrpcImpl.invoke} 完全一致：
     * 用 {@link CommentGrpcError#grpcError(int, String)} 发出业务码。
     *
     * <p>⚠️ 必须 {@code observer.onError(...)} 而不是 {@code throw}：
     * grpc-java 的 {@code ServerCalls} 从流监听回调里调业务方法，
     * {@code throw} 会逃出回调，被当成传输层内部错误，description 被替换成
     * {@code "Application error processing RPC"}。
     */
    @BeforeAll
    static void startServer() throws IOException {
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(new CommentServiceGrpc.CommentServiceImplBase() {
                    @Override
                    public void createComment(CreateCommentReq request,
                                             StreamObserver<CreateCommentResp> responseObserver) {
                        responseObserver.onError(CommentGrpcError.grpcError(BIZ_CODE, BIZ_MSG));
                    }
                })
                .build().start();
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

    private static StatusRuntimeException callRealRpcAndCatch() {
        return assertThrows(StatusRuntimeException.class, () ->
                CommentServiceGrpc.newBlockingStub(channel)
                        .createComment(CreateCommentReq.newBuilder().build()));
    }

    // ==================================================================
    // 1. RAG comment-rpc 对端：trailer 里的业务码必须原样还原
    // ==================================================================

    @Test
    @DisplayName("🔴 RAG-rpc 对端：业务码穿过传输层后，envelope.code 必须是 1012（不是 UNKNOWN=2）")
    void ragPeerBusinessCodeSurvivesTransport() {
        StatusRuntimeException thrown = callRealRpcAndCatch();

        // --- 前置：trailer 真的穿过了传输层（否则下面的一切都无意义）
        var trailers = Status.trailersFromThrowable(thrown);
        assertNotNull(trailers, "客户端没收到 trailer —— 传输层假设不成立");
        assertEquals(String.valueOf(BIZ_CODE), trailers.get(CommentGrpcError.BIZ_CODE_TRAILER),
                "trailer 里的业务码没无损抵达");

        // --- 本体：api 侧还原出的业务码
        BusinessException mapped = SUPPORT.rpcError(thrown);
        assertEquals(BIZ_CODE, mapped.getCode(),
                "api 侧丢了业务码 —— 客户端会拿到 UNKNOWN(2) 而不是 1012，"
                        + "这是本轮 MISMATCH-API-01 的缺陷本体");
    }

    @Test
    @DisplayName("msg 取 description 原文（不查表）—— 1012 查表得「评论区不存在」，但原文必须逐字保留")
    void messageKeepsRpcDescriptionVerbatim() {
        StatusRuntimeException thrown = callRealRpcAndCatch();

        BusinessException mapped = SUPPORT.rpcError(thrown);
        assertEquals(BIZ_MSG, mapped.getMessage(),
                "msg 必须是 rpc 发出的原文，不能被码表文案顶替");
    }

    // ==================================================================
    // 2. Go comment-rpc 对端（灰度期）：降级 description 捞码
    // ==================================================================

    @Test
    @DisplayName("Go-rpc 对端（灰度）：越界 grpc-status 降级后仍能捞回 5003 —— 与 Go 的 int(st.Code()) 一致")
    void goPeerDegradedStatusStillYieldsRawBusinessCode() {
        // 这就是 Java 客户端收到 Go comment-rpc 时的真实形态
        // （构造方式即 grpc-java 对越界 grpc-status 的解码路径）。
        StatusRuntimeException fromGo = Status.fromCodeValue(5003).asRuntimeException();

        BusinessException mapped = SUPPORT.rpcError(fromGo);
        assertEquals(5003, mapped.getCode(),
                "灰度期至少要保住机器可读的业务码（Go 侧 int(st.Code()) = 5003）");
    }

    // ==================================================================
    // 3. 标准码与兜底：与 Go 的字面搬运保持一致
    // ==================================================================

    @Test
    @DisplayName("标准码仍取原值（NOT_FOUND ⇒ 5），不翻成业务码 —— Go 侧同样是 int(st.Code())")
    void standardGrpcCodeIsReturnedVerbatim() {
        StatusRuntimeException notFound = Status.NOT_FOUND
                .withDescription("comment not exist").asRuntimeException();

        BusinessException mapped = SUPPORT.rpcError(notFound);
        assertEquals(Status.Code.NOT_FOUND.value(), mapped.getCode(),
                "标准码必须原样搬运（Go 侧 int(st.Code()) = 5）");
        assertEquals("comment not exist", mapped.getMessage());
    }

    @Test
    @DisplayName("非 status 错误（如熔断）⇒ 5001 + 「系统内部错误」（Go 的 status.FromError ok=false 分支）")
    void nonStatusErrorFallsBackTo5001() {
        BusinessException mapped = SUPPORT.rpcError(new IllegalStateException("熔断打开"));
        assertEquals(CommentErrorCode.ERROR_SERVER_COMMON.getCode(), mapped.getCode(),
                "Go 的 ok==false 分支是 5001，不是 bizCodeFromError 的 1015");
        assertEquals("系统内部错误", mapped.getMessage());
    }
}
