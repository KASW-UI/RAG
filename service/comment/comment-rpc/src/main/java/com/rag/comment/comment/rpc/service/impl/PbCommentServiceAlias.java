package com.rag.comment.comment.rpc.service.impl;

import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCallHandler;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import net.devh.boot.grpc.server.service.GrpcService;
import com.rag.comment.comment.proto.CommentServiceGrpc;


/**
 * 🔴 <b>Go 兼容别名</b> —— 把同一套实现再挂到 Go 真源的方法全名前缀 {@code /pb.CommentService/*} 上。
 *
 * <h2>为什么需要它</h2>
 * <p><b>Go 事实</b>：{@code proto/comment.proto:4} 是 {@code package pb;}
 * ⇒ Go 的 gRPC 方法全名是 {@code /pb.CommentService/*}。
 * <p><b>RAG 现状</b>：{@code service/comment/comment-proto/.../comment.proto:3} 是 {@code package comment;}
 * ⇒ RAG 的方法全名是 {@code /comment.CommentService/*}。
 * <p><b>后果</b>：Go 的 client 调 RAG 的 server（或反过来）⇒ 方法全名对不上
 * ⇒ grpc 直接回 {@code UNIMPLEMENTED} ⇒ <b>调用必失败</b>。
 * <p>⚠️ <b>仓内自产自销不受影响</b>（RAG api → RAG rpc 两侧都是 {@code /comment.}），
 * 但**灰度期混跑**（Go api 与 RAG api 后面挂同一批 rpc 实例）会踩到。
 *
 * <h2>为什么是「加别名」而不是「改 RAG 的 proto package」</h2>
 * <p>改回 {@code package pb;} 会推翻 <b>已签字</b>的偏离 {@code SD-2026-10-23-04}
 * （proto {@code package} 归一为 {@code comment}）⇒ AGENTS.md 明确「不得顺手修正回 Go 的旧行为」。
 * <p>⇒ 本类<b>只增不改</b>：{@code /comment.CommentService/*} 原样保留，
 * 额外注册一份 {@code /pb.CommentService/*} 指向<b>同一个实现</b>。
 *
 * <h2>⚠️ 本别名只解决「连通性」，解决不了「业务码」</h2>
 * <p>Go 的 api 读业务码靠 {@code int(st.Code())}（即 {@code grpc-status} 上的数字）。
 * 而 {@code io.grpc.Status} 是<b>只有 17 个常量的 final 类</b>（grpc-api 1.63.0）
 * ⇒ grpc-java <b>发不出</b> {@code grpc-status: 1012}。
 * <p>⇒ RAG 的 rpc 把业务码放在<b>自定义 trailer</b> {@code rag-comment-biz-code} 里
 * （{@code CommentGrpcError.grpcError}）⇒ <b>Go 侧读不到</b>
 * ⇒ 跨栈时错误码会退化成 {@code UNKNOWN(2)}。
 * <p>⇒ <b>结论</b>：跨栈调用<b>不是灰度期的可行方案</b>，混跑必须<b>同栈配对</b>
 * （RAG api 只连 RAG rpc，Go api 只连 Go rpc）。
 * ⇒ 本别名是<b>兜底</b>：让混跑时的失败从「连不上（UNIMPLEMENTED）」降级为
 * 「能通、但错误码退化」，**不是**让跨栈变得完全可用。
 * ⇒ 台账见 {@code docs/DRIFT/comment/proto.md} 与
 * {@code docs/Intentional_Deviations/comment/proto.md}。
 *
 * <h2>语义等价论证</h2>
 * <p>本类<b>不新增任何业务逻辑</b>：它把 {@link CommentServiceGrpcImpl#bindService()}
 * 里已有的 {@code ServerMethodDefinition} 逐个<b>只改方法全名</b>
 * （{@code comment.CommentService/X} ⇒ {@code pb.CommentService/X}），
 * <b>handler 原样复用</b>（{@code ServerMethodDefinition#getServerCallHandler()}）。
 * <p>⇒ 两个服务名下跑的是<b>同一段代码</b>、同一套鉴权、同一套业务码出口。
 * ⇒ 差异<b>只在 gRPC 方法全名</b>这一层 —— 那是**表达层**，符合 AGENTS.md §3。
 */
@GrpcService
public class PbCommentServiceAlias implements BindableService {

    /** Go 真源的 proto package（{@code proto/comment.proto:4}）⇒ {@code /pb.CommentService/*}。 */
    public static final String GO_SERVICE_NAME = "pb.CommentService";

    /** RAG 自己的 proto package（{@code comment.proto:3}）⇒ {@code /comment.CommentService/*}。 */
    public static final String RAG_SERVICE_NAME = "comment.CommentService";

    /** 被复用的实现。生产传 {@link CommentServiceGrpcImpl}；测试可传桩实现。 */
    private final CommentServiceGrpc.CommentServiceImplBase delegate;

    public PbCommentServiceAlias(CommentServiceGrpc.CommentServiceImplBase delegate) {
        this.delegate = delegate;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ServerServiceDefinition bindService() {
        ServerServiceDefinition original = delegate.bindService();
        ServerServiceDefinition.Builder builder = ServerServiceDefinition.builder(GO_SERVICE_NAME);
        for (ServerMethodDefinition<?, ?> method : original.getMethods()) {
            MethodDescriptor<?, ?> source = method.getMethodDescriptor();
            MethodDescriptor<?, ?> renamed = source.toBuilder()
                    .setFullMethodName(goFullMethodName(source.getFullMethodName()))
                    .build();
            // ⚠️ handler 与 descriptor 无关：它在调用时才收到 descriptor
            // （ServerCallHandler#startCall(MethodDescriptor, ServerCallContext)）
            // ⇒ 直接复用是安全的，且保证两个服务名下**行为完全一致**。
            builder.addMethod((MethodDescriptor) renamed, (ServerCallHandler) method.getServerCallHandler());
        }
        return builder.build();
    }

    /**
     * 把 {@code comment.CommentService/GetComment} 改成 {@code pb.CommentService/GetComment}。
     *
     * <p>⚠️ 只改<b>服务名</b>那一段，方法名（{@code GetComment}）与包体（method 名后半段）原样保留
     * ⇒ 与 Go 的 {@code /pb.CommentService/GetComment} 逐字一致。
     */
    static String goFullMethodName(String ragFullMethodName) {
        int slash = ragFullMethodName.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("not a full method name: " + ragFullMethodName);
        }
        return GO_SERVICE_NAME + ragFullMethodName.substring(slash);
    }
}
