package com.rag.users.user.rpc.service.impl;

import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.DeleteUserResp;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import com.rag.users.user.rpc.service.UserDomainService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserServiceGrpcImpl 的 gRPC 边界行为测试。
 *
 * <p>为什么单独一个类：{@code UserDomainServiceTest} 直接构造 domain service，
 * 走不到 {@code UserServiceGrpcImpl} 这一层。而「异常怎么变成 gRPC status code」
 * 恰好是这一层独有的职责——补在这里才能锁住 DRIFT-02。
 *
 * <p>DRIFT-02：重构时 9 个方法只 catch {@code RuntimeException}，缺少参考实现
 * {@code UserServiceServer.invoke()} 里的 {@code catch (Throwable)} 兜底。
 * 后果是未映射的异常被 grpc-java 当成 {@code UNKNOWN} 发给 client，
 * 调用方拿到的状态码从 {@code INTERNAL} 退化成 {@code UNKNOWN}。
 */
class UserServiceGrpcImplTest {

    private UserDomainService domainService;
    private UserServiceGrpcImpl grpc;

    @BeforeEach
    void setUp() {
        domainService = mock(UserDomainService.class);
        grpc = new UserServiceGrpcImpl(domainService);
    }

    // ============== 正常路径 ==============

    @Test
    void success_onNextThenOnCompleted() {
        LoginResp resp = LoginResp.newBuilder().setStatus(0L).setUid(123L).build();
        when(domainService.login(any())).thenReturn(resp);

        Recorder<LoginResp> obs = new Recorder<>();
        grpc.login(LoginReq.newBuilder().setUsername("alice").setPassword("pw").build(), obs);

        assertThat(obs.value).isSameAs(resp);
        assertThat(obs.error).isNull();
        assertThat(obs.completed).isTrue();
    }

    // ============== 已映射的 Status：原样透传 ==============

    @Test
    void statusRuntimeException_passesThroughWithOriginalCodeAndDescription() {
        when(domainService.login(any())).thenThrow(
                Status.NOT_FOUND.withDescription("用户不存在").asRuntimeException());

        Recorder<LoginResp> obs = new Recorder<>();
        grpc.login(LoginReq.newBuilder().setUsername("ghost").setPassword("pw").build(), obs);

        assertThat(obs.completed).isFalse();
        assertThat(obs.error).isNotNull();
        assertThat(statusOf(obs).getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(statusOf(obs).getDescription()).isEqualTo("用户不存在");
    }

    // ============== DRIFT-02：未映射异常必须收敛成 INTERNAL ==============

    @Test
    void unmappedRuntimeException_becomesInternalNotUnknown() {
        when(domainService.login(any())).thenThrow(new IllegalStateException("boom"));

        Recorder<LoginResp> obs = new Recorder<>();
        grpc.login(LoginReq.newBuilder().setUsername("alice").setPassword("pw").build(), obs);

        assertThat(obs.error).isNotNull();
        assertThat(statusOf(obs).getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(statusOf(obs).getDescription()).isEqualTo("IllegalStateException: boom");
    }

    /** 连 RuntimeException 都不是的 Throwable（如 Error）同样要兜住 */
    @Test
    void unmappedError_becomesInternal() {
        when(domainService.deleteUser(any())).thenThrow(new Error("stack overflow"));

        Recorder<DeleteUserResp> obs = new Recorder<>();
        grpc.deleteUser(DeleteUserReq.newBuilder().setUid(1L).build(), obs);

        assertThat(obs.error).isNotNull();
        assertThat(statusOf(obs).getCode()).isEqualTo(Status.Code.INTERNAL);
        assertThat(statusOf(obs).getDescription()).isEqualTo("Error: stack overflow");
    }

    @Test
    void deadlineExceeded_keepsItsOwnCode() {
        when(domainService.login(any())).thenThrow(
                Status.DEADLINE_EXCEEDED.withDescription("rpc deadline reached").asRuntimeException());

        Recorder<LoginResp> obs = new Recorder<>();
        grpc.login(LoginReq.newBuilder().setUsername("alice").setPassword("pw").build(), obs);

        assertThat(statusOf(obs).getCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED);
    }

    // ============== 组合检查：同一路径只终结一次 ==============

    @Test
    void failure_neverCallsOnNextOrOnCompleted() {
        when(domainService.login(any())).thenThrow(new RuntimeException("nope"));

        Recorder<LoginResp> obs = new Recorder<>();
        grpc.login(LoginReq.newBuilder().setUsername("alice").setPassword("pw").build(), obs);

        assertThat(obs.value).isNull();
        assertThat(obs.completed).isFalse();
        assertThat(obs.error).isNotNull();
    }

    @Test
    void domainServiceIsNotTouchedOnSuccess() {
        when(domainService.login(any())).thenReturn(LoginResp.newBuilder().setStatus(0L).build());

        Recorder<LoginResp> obs = new Recorder<>();
        grpc.login(LoginReq.newBuilder().setUsername("alice").setPassword("pw").build(), obs);

        verify(domainService).login(any());
        assertThat(obs.completed).isTrue();
    }

    private static Status statusOf(Recorder<?> obs) {
        assertThat(obs.error).isInstanceOf(StatusRuntimeException.class);
        return ((StatusRuntimeException) obs.error).getStatus();
    }

    /** 记录 StreamObserver 收到的三件事：onNext / onError / onCompleted */
    private static final class Recorder<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;
        private boolean completed;

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
}