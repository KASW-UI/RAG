package com.rag.follow.follow.rpc.service.impl;

import com.rag.follow.follow.common.errmsg.FollowErrorCode;
import com.rag.follow.follow.common.errmsg.FollowGrpcError;
import com.rag.follow.follow.proto.BaseResp;
import com.rag.follow.follow.proto.ListReq;
import com.rag.follow.follow.proto.RecommendResp;
import com.rag.follow.follow.proto.RelationReq;
import com.rag.follow.follow.proto.UserListResp;
import com.rag.follow.follow.rpc.service.FollowDomainService;
import com.rag.follow.follow.rpc.service.FollowRpcSupport;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * gRPC 错误出口的契约 —— 验证 {@code FollowServiceGrpcImpl} 用
 * {@code observer.onError(...)} 而非让异常逃逸。
 *
 * <h2>为什么这条必须单独测（AGENTS.md §3.3）</h2>
 *
 * <p>grpc-java 在 {@code UnaryServerCallListener.onHalfClose} 里调用业务方法。
 * 若异常逃逸出该回调，会被当成<b>传输层内部错误</b>，
 * description 被替换成 {@code "Application error processing RPC"}。
 *
 * <p>而 follow 域的业务码<b>唯一载体就是 description 文案</b>
 * （follow-api 靠 {@code FollowGrpcError} 的 {@code BIZ_CODE_BY_MSG} 反查）。
 * ⇒ description 一丢，5201 / 5202 / 5101 / 5007 / 5008 全部退化成 switch 兜底的
 * 5003 / 5005 / 5002 / 5001。
 *
 * <p>本类<b>不启动真实 gRPC server</b>：被测的正是「异常是否交给 onError」
 * 这一层分派逻辑，用捕获式 {@link StreamObserver} 断言即可 ——
 * 真 server 的行为由 {@code FollowGrpcWiringTest} 覆盖。
 */
class FollowServiceGrpcImplTest {

    private FollowDomainService domainService;
    private FollowServiceGrpcImpl impl;

    @BeforeEach
    void setUp() {
        domainService = Mockito.mock(FollowDomainService.class);
        impl = new FollowServiceGrpcImpl(domainService);
    }

    /** 捕获式 observer：记录 onNext / onError / onCompleted 三条路径各被调用了几次。 */
    private static final class CapturingObserver<T> implements StreamObserver<T> {
        T value;
        Throwable error;
        boolean completed;

        @Override
        public void onNext(T t) {
            this.value = t;
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
    @DisplayName("⚠️ 业务错误必须走 onError：description 逐字保留，可反查回 5202")
    void businessErrorGoesThroughOnErrorPreservingDescription() {
        when(domainService.follow(ArgumentMatchers.any(RelationReq.class)))
                .thenThrow(FollowRpcSupport.rpcError(
                        Status.Code.PERMISSION_DENIED,
                        FollowErrorCode.ERROR_RELATION_BLOCKED.getCode()));

        CapturingObserver<BaseResp> observer = new CapturingObserver<>();
        impl.follow(RelationReq.newBuilder().setUserId(1).setTargetId(2).build(), observer);

        assertThat(observer.error).isInstanceOf(io.grpc.StatusRuntimeException.class);
        io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        assertThat(sre.getStatus().getDescription())
                .as("⭐ 若这里变成 \"Application error processing RPC\"，业务码就永久丢失了")
                .isEqualTo("blocked relation exists");
        assertThat(FollowGrpcError.bizCodeFromError(sre))
                .as("follow-api 侧靠这一步反查回 5202")
                .isEqualTo(FollowErrorCode.ERROR_RELATION_BLOCKED.getCode());

        assertThat(observer.value).as("出错时不得同时发响应体").isNull();
        assertThat(observer.completed).as("出错时不得 onCompleted").isFalse();
    }

    @Test
    @DisplayName("正常路径：onNext + onCompleted，且不调 onError")
    void successPathEmitsResponseAndCompletes() {
        BaseResp expected = BaseResp.newBuilder().setCode(200).setMsg("OK").build();
        when(domainService.unfollow(ArgumentMatchers.any(RelationReq.class))).thenReturn(expected);

        CapturingObserver<BaseResp> observer = new CapturingObserver<>();
        impl.unfollow(RelationReq.newBuilder().setUserId(1).setTargetId(2).build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.value).isSameAs(expected);
        assertThat(observer.completed).isTrue();
    }

    @Test
    @DisplayName("⚠️ 未预期异常兜底成 INTERNAL + \"internal server error\"，绝不能泄漏原始消息")
    void unexpectedExceptionIsCollapsedWithoutLeakingInternals() {
        when(domainService.getFollowList(ArgumentMatchers.any(ListReq.class)))
                .thenThrow(new IllegalStateException(
                        "connection to jdbc:postgresql://postgres:5432/first_db refused, password=hunter2"));

        CapturingObserver<UserListResp> observer = new CapturingObserver<>();
        impl.getFollowList(ListReq.newBuilder().setUserId(1).build(), observer);

        io.grpc.StatusRuntimeException sre = (io.grpc.StatusRuntimeException) observer.error;
        assertThat(sre.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(sre.getStatus().getDescription())
                .isEqualTo("internal server error")
                .as("⭐ 原始异常里含连接串与口令，绝不能上 wire")
                .doesNotContain("password");
        assertThat(FollowGrpcError.bizCodeFromError(sre))
                .isEqualTo(FollowErrorCode.ERROR_SERVER_COMMON.getCode());
    }

    @Test
    @DisplayName("8 个 RPC 方法全部接到 domainService，没有漏实现或错位")
    void allEightMethodsAreWired() {
        BaseResp base = BaseResp.newBuilder().setCode(200).setMsg("OK").build();
        UserListResp list = UserListResp.newBuilder().setCode(200).setMsg("OK").build();
        RecommendResp rec = RecommendResp.newBuilder().setCode(200).setMsg("OK").build();

        when(domainService.follow(ArgumentMatchers.any())).thenReturn(base);
        when(domainService.unfollow(ArgumentMatchers.any())).thenReturn(base);
        when(domainService.block(ArgumentMatchers.any())).thenReturn(base);
        when(domainService.unblock(ArgumentMatchers.any())).thenReturn(base);
        when(domainService.getFollowList(ArgumentMatchers.any())).thenReturn(list);
        when(domainService.getBlockList(ArgumentMatchers.any())).thenReturn(list);
        when(domainService.getFollowerList(ArgumentMatchers.any())).thenReturn(list);
        when(domainService.getRecommendations(ArgumentMatchers.any())).thenReturn(rec);

        CapturingObserver<BaseResp> o1 = new CapturingObserver<>();
        CapturingObserver<BaseResp> o2 = new CapturingObserver<>();
        CapturingObserver<BaseResp> o3 = new CapturingObserver<>();
        CapturingObserver<BaseResp> o4 = new CapturingObserver<>();
        CapturingObserver<UserListResp> o5 = new CapturingObserver<>();
        CapturingObserver<UserListResp> o6 = new CapturingObserver<>();
        CapturingObserver<UserListResp> o7 = new CapturingObserver<>();
        CapturingObserver<RecommendResp> o8 = new CapturingObserver<>();

        RelationReq rel = RelationReq.newBuilder().setUserId(1).setTargetId(2).build();
        ListReq lreq = ListReq.newBuilder().setUserId(1).build();

        impl.follow(rel, o1);
        impl.unfollow(rel, o2);
        impl.block(rel, o3);
        impl.unblock(rel, o4);
        impl.getFollowList(lreq, o5);
        impl.getBlockList(lreq, o6);
        impl.getFollowerList(lreq, o7);
        impl.getRecommendations(lreq, o8);

        for (CapturingObserver<?> o : List.of(o1, o2, o3, o4, o5, o6, o7, o8)) {
            assertThat(o.error).as("8 个方法都不应抛错").isNull();
            assertThat(o.completed).isTrue();
        }
        Mockito.verify(domainService).follow(rel);
        Mockito.verify(domainService).unfollow(rel);
        Mockito.verify(domainService).block(rel);
        Mockito.verify(domainService).unblock(rel);
        Mockito.verify(domainService).getFollowList(lreq);
        Mockito.verify(domainService).getBlockList(lreq);
        Mockito.verify(domainService).getFollowerList(lreq);
        Mockito.verify(domainService).getRecommendations(lreq);
    }
}