package com.rag.comment.comment.rpc.service.impl;

import com.rag.comment.comment.proto.CommentServiceGrpc;
import com.rag.comment.comment.proto.GetCommentReq;
import com.rag.comment.comment.proto.GetCommentResp;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>跨栈兼容别名的守门网</b> —— 锁住「RAG 的 comment-rpc 同时服务 {@code /comment.CommentService/*} 与
 * {@code /pb.CommentService/*}」。
 *
 * <h2>为什么必须有这个别名</h2>
 * <p><b>Go 真源</b>：{@code proto/comment.proto:4} 是 {@code package pb;}
 * ⇒ Go 的 gRPC 方法全名是 {@code /pb.CommentService/*}。
 * <p><b>RAG</b>：{@code comment.proto:3} 是 {@code package comment;}
 * ⇒ RAG 的方法全名是 {@code /comment.CommentService/*}。
 * <p>⇒ 不做别名的话，Go 的 client 调 RAG 的 server 必然 {@code UNIMPLEMENTED}。
 *
 * <h2>本测试锁住的三件事</h2>
 * <ol>
 *   <li>用 <b>Go 的方法全名</b>（{@code /pb.CommentService/GetComment}）调用 ⇒ <b>必须打到实现</b>
 *       （不能是 UNIMPLEMENTED）；</li>
 *   <li>用 <b>RAG 自己的方法全名</b>调用 ⇒ <b>仍然打到实现</b>（别名不能顶掉原路径）；</li>
 *   <li>两条路径<b>打到的是同一个实现</b>（返回值逐字段一致）⇒ 别名不能是「另一份逻辑」。</li>
 * </ol>
 *
 * <h2>🧬 变异实测</h2>
 * <p>把 {@link PbCommentServiceAlias#bindService()} 改成
 * 「服务名用 {@code comment.CommentService}」（= 不做别名）⇒ 用例 ① 变红。
 */
class PbCommentServiceAliasGateTest {

    private static Server server;
    private static ManagedChannel channel;

    /** 桩实现：记录被调用了哪个方法，返回可区分的值。 */
    static class StubService extends CommentServiceGrpc.CommentServiceImplBase {
        final List<String> calls = new ArrayList<>();

        @Override
        public void getComment(GetCommentReq request, StreamObserver<GetCommentResp> responseObserver) {
            calls.add(request.getTargetId());
            responseObserver.onNext(GetCommentResp.newBuilder()
                    .setSubject(com.rag.comment.comment.proto.SubjectInfo.newBuilder()
                            .setTargetType("article")
                            .setTargetId(request.getTargetId())
                            .setTotalCount(7L)
                            .build())
                    .build());
            responseObserver.onCompleted();
        }
    }

    private static StubService stub;

    @BeforeAll
    static void startServer() throws Exception {
        stub = new StubService();
        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor()
                // ① RAG 自己的路径（原样）
                .addService(stub.bindService())
                // ② Go 兼容别名
                .addService(new PbCommentServiceAlias(stub).bindService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (channel != null) {
            channel.shutdownNow();
        }
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** 用指定的方法全名调一次 GetComment。 */
    private static GetCommentResp call(String serviceName) {
        MethodDescriptor<GetCommentReq, GetCommentResp> method =
                CommentServiceGrpc.getGetCommentMethod().toBuilder()
                        .setFullMethodName(serviceName + "/GetComment")
                        .build();
        return ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT,
                GetCommentReq.newBuilder().setTargetType("article").setTargetId("a-alias").build());
    }

    @Test
    @DisplayName("🔴 Go 的方法全名 /pb.CommentService/GetComment ⇒ **必须打到实现**（不是 UNIMPLEMENTED）")
    void goServiceNameReachesTheImplementation() {
        GetCommentResp resp = call(PbCommentServiceAlias.GO_SERVICE_NAME);
        assertNotNull(resp);
        assertEquals(7L, resp.getSubject().getTotalCount(), "应当打到实现并拿到桩返回值");
        assertEquals("a-alias", resp.getSubject().getTargetId());
    }

    @Test
    @DisplayName("🔴 RAG 自己的路径 /comment.CommentService/GetComment ⇒ **仍然可用**（别名不能顶掉原路径）")
    void ragServiceNameStillWorks() {
        GetCommentResp resp = call(PbCommentServiceAlias.RAG_SERVICE_NAME);
        assertNotNull(resp);
        assertEquals(7L, resp.getSubject().getTotalCount());
    }

    @Test
    @DisplayName("🔴 两条路径打到的是**同一个实现**（别名不是另一份逻辑）")
    void bothPathsHitTheSameImplementation() {
        stub.calls.clear();
        call(PbCommentServiceAlias.GO_SERVICE_NAME);
        call(PbCommentServiceAlias.RAG_SERVICE_NAME);
        assertEquals(2, stub.calls.size(), "两次调用都应落到同一个桩实现上");
        assertEquals(List.of("a-alias", "a-alias"), stub.calls);
    }

    @Test
    @DisplayName("🔴 别名的服务名与 Go 真源**逐字一致**（Go proto/comment.proto:4 是 package pb;）")
    void aliasServiceNameMatchesGoProtoPackage() {
        assertEquals("pb.CommentService", PbCommentServiceAlias.GO_SERVICE_NAME);
        assertEquals("comment.CommentService", PbCommentServiceAlias.RAG_SERVICE_NAME);
        assertEquals("pb.CommentService/GetComment",
                PbCommentServiceAlias.goFullMethodName("comment.CommentService/GetComment"));
    }

    @Test
    @DisplayName("🔴 别名服务名下的**方法集合**与原服务**逐个对应**（不多不少）")
    void aliasExposesExactlyTheSameMethods() {
        ServerServiceDefinition original = stub.bindService();
        ServerServiceDefinition alias = new PbCommentServiceAlias(stub).bindService();
        assertEquals(original.getMethods().size(), alias.getMethods().size());

        java.util.Set<String> aliasNames = new java.util.HashSet<>();
        for (io.grpc.ServerMethodDefinition<?, ?> m : alias.getMethods()) {
            aliasNames.add(m.getMethodDescriptor().getFullMethodName());
        }
        for (io.grpc.ServerMethodDefinition<?, ?> m : original.getMethods()) {
            String renamed = PbCommentServiceAlias.goFullMethodName(
                    m.getMethodDescriptor().getFullMethodName());
            assertTrue(aliasNames.contains(renamed),
                    "别名里应当有 " + renamed + "（原 " + m.getMethodDescriptor().getFullMethodName() + "）");
        }
    }

    @Test
    @DisplayName("🔴 未注册的方法名 ⇒ **UNIMPLEMENTED**（别名的存在不能让未知方法变得可调）")
    void unknownMethodStillFailsFast() {
        MethodDescriptor<GetCommentReq, GetCommentResp> method =
                CommentServiceGrpc.getGetCommentMethod().toBuilder()
                        .setFullMethodName("pb.OtherService/GetComment")
                        .build();
        StatusRuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class,
                () -> ClientCalls.blockingUnaryCall(channel, method, CallOptions.DEFAULT,
                        GetCommentReq.getDefaultInstance()));
        assertEquals(Status.Code.UNIMPLEMENTED, e.getStatus().getCode());
    }
}
