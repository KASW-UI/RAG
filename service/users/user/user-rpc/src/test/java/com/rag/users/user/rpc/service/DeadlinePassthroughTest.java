package com.rag.users.user.rpc.service;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.rpc.config.UserRpcProperties;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.metrics.UserRpcMetrics;
import com.rag.users.user.proto.DeleteUserReq;
import com.rag.users.user.proto.GetUserReq;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.SelectAvatarReq;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * B-1 缓解的<b>业务侧回归网</b>：DEADLINE_EXCEEDED 必须能穿过 {@code UserDomainService}。
 *
 * <p><b>本批修的是什么（issue #71187874）</b>
 * <p>为了把 {@code SqlTimeoutStatusTranslator} 翻译出的 {@code DEADLINE_EXCEEDED}
 * 送到 api 层（→ 1015，与 Go 一致），必须先让它<b>不被业务层吞掉</b>。
 * 此前 {@code UserDomainService} 里每个 DB 调用点都是
 * {@code catch (Exception e) { throw Status.INTERNAL.withDescription("DB查询失败")... }}，
 * DEADLINE_EXCEEDED 是 {@code RuntimeException}，会被一并吞掉。
 *
 * <p><b>顺带修掉一个既有缺陷</b>：{@code checkDeadline()} 抛的就是
 * {@code StatusRuntimeException(DEADLINE_EXCEEDED)}，而它被写在 {@code try} 块内 ——
 * 即 DRIFT-018 那 11 处守卫里，<b>凡是落在 try 内部的都从未生效</b>，
 * 会被同方法的 {@code catch (Exception e)} 改写成 INTERNAL。
 * {@link #deadlineAlreadyExpired_isNotSwallowedByLogin} 专门锁住这一点。
 *
 * <p><b>§3.3 边界声明</b>：本测试用 mock mapper，验证的是
 * 「异常能否穿过 catch 分支」这段<b>纯控制流</b>；它<b>不能</b>证明拦截器被注册、
 * {@code setQueryTimeout} 真的取消了 PG 查询 —— 那两项由端到端实验
 * {@code tools/difftest/exp-b1-timeout-cancel.sh} 覆盖。
 */
class DeadlinePassthroughTest {

    private UserMapper userMapper;
    private UserAvatarHistoryMapper avatarMapper;
    private UserDomainService service;

    /** {@code SqlTimeoutStatusTranslator} 翻译后到达业务层的形态。 */
    private static StatusRuntimeException canceledByDeadline() {
        return Status.DEADLINE_EXCEEDED
                .withDescription("sql statement canceled at rpc deadline")
                .asRuntimeException();
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        avatarMapper = mock(UserAvatarHistoryMapper.class);
        StringRedisTemplate bizRedis = mock(StringRedisTemplate.class);
        when(bizRedis.opsForValue()).thenReturn(mock(ValueOperations.class));
        service = new UserDomainService(
                userMapper,
                avatarMapper,
                new UserRpcMetrics(new SimpleMeterRegistry()),
                bizRedis,
                mock(LoggerFactoryBean.class),
                new UserRpcProperties(),
                new AvatarDomainService());
    }

    private static void assertDeadlineExceeded(Throwable thrown) {
        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        StatusRuntimeException sre = (StatusRuntimeException) thrown;
        assertThat(sre.getStatus().getCode())
                .as("DB 超时必须保持 DEADLINE_EXCEEDED，不能被改写成 INTERNAL")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("login：mapper 抛 DEADLINE_EXCEEDED ⇒ 原样上抛，不是 INTERNAL")
    void login_passesDeadlineThrough() {
        when(userMapper.findOneByUserName(anyString())).thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.login(
                LoginReq.newBuilder().setUsername("alice").setPassword("pw").build()))
                .satisfies(DeadlinePassthroughTest::assertDeadlineExceeded);
    }

    @Test
    @DisplayName("getUser：mapper 抛 DEADLINE_EXCEEDED ⇒ 原样上抛")
    void getUser_passesDeadlineThrough() {
        when(userMapper.findOneByUid(anyLong())).thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.getUser(
                GetUserReq.newBuilder().setUid(1L).build()))
                .satisfies(DeadlinePassthroughTest::assertDeadlineExceeded);
    }

    @Test
    @DisplayName("deleteUser：写路径上的 DEADLINE_EXCEEDED 同样上抛（事务会因此回滚，与 Go ctx cancel 一致）")
    void deleteUser_passesDeadlineThrough() {
        when(userMapper.deleteUserByUid(anyLong())).thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.deleteUser(
                DeleteUserReq.newBuilder().setUid(1L).build()))
                .satisfies(DeadlinePassthroughTest::assertDeadlineExceeded);
    }

    @Test
    @DisplayName("selectAvatar：avatar mapper 上的 DEADLINE_EXCEEDED 也上抛")
    void selectAvatar_passesDeadlineThrough() {
        when(avatarMapper.selectAvatarHistory(any(), anyLong(), anyString()))
                .thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.selectAvatar(
                SelectAvatarReq.newBuilder().setUid(1L).setHistoryId("1234567890").build()))
                .satisfies(DeadlinePassthroughTest::assertDeadlineExceeded);
    }

    /**
     * <b>既有缺陷的防回退锁</b>：本线程的 deadline 已经过期时，
     * {@code login} 第一行 try 内部的 {@code checkDeadline()} 就会抛 DEADLINE_EXCEEDED。
     *
     * <p>修复前这个异常会被同方法的 {@code catch (Exception e)} 吞掉、改写成
     * {@code Status.INTERNAL("DB查询失败")} —— 即 DRIFT-018 的守卫在 try 内<b>形同虚设</b>。
     */
    @Test
    @DisplayName("checkDeadline 在 try 内抛出的 DEADLINE_EXCEEDED 不被吞（DRIFT-018 守卫此前在 try 内失效）")
    void deadlineAlreadyExpired_isNotSwallowedByLogin() {
        long expired = System.currentTimeMillis() - 1_000;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(expired)) {
            assertThatThrownBy(() -> service.login(
                    LoginReq.newBuilder().setUsername("alice").setPassword("pw").build()))
                    .satisfies(DeadlinePassthroughTest::assertDeadlineExceeded);
        }
        // 关键反向断言：mapper 一次都没被调用 —— 说明是 checkDeadline 先拦下的，
        // 而不是「发起了查询才失败」。
        org.mockito.Mockito.verifyNoInteractions(userMapper);
    }

    /**
     * 反向断言：<b>真正的数据库错误仍然报 INTERNAL</b>。
     *
     * <p>这条是上一条的镜像 —— 如果有人为了「让超时通过」而把 catch 顺序写反，
     * 或者干脆删掉 {@code catch (Exception e)} 的 INTERNAL 映射，
     * DB 故障就会被伪装成超时、与 Go 的 5001 表达不符。
     */
    @Test
    @DisplayName("真实 DB 错误仍报 INTERNAL，不会被误判成 DEADLINE_EXCEEDED")
    void genuineDbFailureStillReportsInternal() {
        when(userMapper.findOneByUserName(anyString()))
                .thenThrow(new IllegalStateException("connection pool exhausted"));

        assertThatThrownBy(() -> service.login(
                LoginReq.newBuilder().setUsername("alice").setPassword("pw").build()))
                .satisfies(t -> assertThat(t).isInstanceOf(StatusRuntimeException.class))
                .satisfies(t -> assertThat(((StatusRuntimeException) t).getStatus().getCode())
                        .isEqualTo(Status.Code.INTERNAL));
    }

    @Test
    @DisplayName("用户不存在仍走三态 status=1，不受本次改动影响")
    void userNotFoundStillReturnsStatusOne() {
        when(userMapper.findOneByUserName("ghost"))
                .thenThrow(new com.rag.users.user.rpc.exception.UserExceptions.NotFoundException("用户不存在"));

        assertThat(service.login(
                LoginReq.newBuilder().setUsername("ghost").setPassword("pw").build()).getStatus())
                .isEqualTo(1L);
    }
}