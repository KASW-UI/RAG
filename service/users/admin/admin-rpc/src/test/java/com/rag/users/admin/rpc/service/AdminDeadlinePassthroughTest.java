package com.rag.users.admin.rpc.service;

import com.rag.common.observability.RpcInterceptor.DeadlineState;
import com.rag.users.admin.rpc.config.AdminRpcProperties;
import com.rag.users.admin.rpc.domain.UserEntity;
import com.rag.users.admin.rpc.exception.AdminExceptions.NotFoundException;
import com.rag.users.admin.rpc.mapper.AdminInviteMapper;
import com.rag.users.admin.rpc.mapper.AdminMapper;
import com.rag.users.admin.rpc.mapper.UserMapper;
import com.rag.users.admin.rpc.metrics.AdminRpcMetrics;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * B-1 缓解在 {@code admin-rpc} 侧的回归网 —— 与 user-rpc 的
 * {@code DeadlinePassthroughTest} 同构。
 *
 * <p><b>为什么 admin 侧要单独再写一遍</b>：两个 rpc 是<b>两个独立的类</b>，
 * 46 处放行分支是分别插入的，user 侧全绿完全不能推出 admin 侧也全绿。
 * admin 侧的方法更多（14 个 vs 9 个）、且有 {@code @Transactional} 写路径，
 * 超时在事务内抛出还必须触发回滚（与 Go 的 ctx cancel 语义一致）。
 *
 * <p><b>§3.3 边界声明</b>：mock mapper，只验证「异常能否穿过 catch 分支」；
 * 拦截器注册与真实的 CancelRequest 由端到端实验覆盖。
 */
class AdminDeadlinePassthroughTest {

    private AdminMapper adminMapper;
    private UserMapper userMapper;
    private AdminDomainService service;

    private static StatusRuntimeException canceledByDeadline() {
        return Status.DEADLINE_EXCEEDED
                .withDescription("sql statement canceled at rpc deadline")
                .asRuntimeException();
    }

    /**
     * 不启 Spring 容器时，MyBatis-Plus 的 lambda cache 是空的，
     * {@code LambdaUpdateWrapper.set(UserEntity::getStatus, …)} 会先抛
     * {@code MybatisPlusException: can not find lambda cache for this entity}。
     * 与 AdminSemanticDriftRegressionTest 同款 hack。
     *
     * <p>⚠️ 顺带说明这个坑为什么值得写下来：那个 MybatisPlusException <b>不是</b>
     * StatusRuntimeException，所以它<b>正确地</b>被 {@code catch (Exception e)} 吞成了 INTERNAL ——
     * 也就是说，只要缺这一步初始化，测试会以「放行分支没生效」的面貌失败，
     * 容易把人引去查错误方向。
     */
    @BeforeAll
    static void initMyBatisPlusTableInfo() throws Exception {
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        java.lang.reflect.Method initMethod =
                com.baomidou.mybatisplus.core.metadata.TableInfoHelper.class.getDeclaredMethod(
                        "initTableInfo",
                        org.apache.ibatis.session.Configuration.class,
                        String.class,
                        Class.class);
        initMethod.setAccessible(true);
        initMethod.invoke(null, config, "com.rag.users.admin.rpc.mapper.UserMapper", UserEntity.class);
        initMethod.invoke(null, config, "com.rag.users.admin.rpc.mapper.AdminMapper",
                com.rag.users.admin.rpc.domain.AdminEntity.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        adminMapper = mock(AdminMapper.class);
        userMapper = mock(UserMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
        service = new AdminDomainService(
                adminMapper,
                mock(AdminInviteMapper.class),
                userMapper,
                redis,
                new AdminRpcProperties(),
                new AdminRpcMetrics(new SimpleMeterRegistry()),
                mock(AdminCreateAtomicService.class));
    }

    private static void assertDeadlineExceeded(Throwable thrown) {
        assertThat(thrown).isInstanceOf(StatusRuntimeException.class);
        assertThat(((StatusRuntimeException) thrown).getStatus().getCode())
                .as("DB 超时必须保持 DEADLINE_EXCEEDED，不能被改写成 INTERNAL")
                .isEqualTo(Status.Code.DEADLINE_EXCEEDED);
    }

    @Test
    @DisplayName("getUser：DEADLINE_EXCEEDED 原样上抛")
    void getUser_passesDeadlineThrough() {
        when(userMapper.selectOne(any(Wrapper.class))).thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.getUser(1L))
                .satisfies(AdminDeadlinePassthroughTest::assertDeadlineExceeded);
    }

    @Test
    @DisplayName("banUser：写路径上的 DEADLINE_EXCEEDED 上抛，事务据此回滚（对齐 Go ctx cancel）")
    void banUser_passesDeadlineThrough() {
        when(userMapper.update(isNull(), any(Wrapper.class))).thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.banUser(1L, 9L))
                .satisfies(AdminDeadlinePassthroughTest::assertDeadlineExceeded);
    }

    @Test
    @DisplayName("unBanUser：DEADLINE_EXCEEDED 原样上抛")
    void unBanUser_passesDeadlineThrough() {
        when(userMapper.update(isNull(), any(Wrapper.class))).thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.unBanUser(1L, 9L))
                .satisfies(AdminDeadlinePassthroughTest::assertDeadlineExceeded);
    }

    @Test
    @DisplayName("getSelf：DEADLINE_EXCEEDED 原样上抛")
    void getSelf_passesDeadlineThrough() {
        when(adminMapper.selectOne(any(Wrapper.class))).thenThrow(canceledByDeadline());

        assertThatThrownBy(() -> service.getSelf(1L))
                .satisfies(AdminDeadlinePassthroughTest::assertDeadlineExceeded);
    }

    /**
     * <b>既有缺陷的防回退锁</b>：deadline 已过期时，方法入口的 {@code checkDeadline()}
     * 就在 try 块<b>内</b>的路径上抛 DEADLINE_EXCEEDED —— 修复前会被
     * {@code catch (Exception e)} 吞成 INTERNAL，等于 13 处守卫全部失效。
     */
    @Test
    @DisplayName("deadline 已过期时 checkDeadline 的 DEADLINE_EXCEEDED 不被吞（mapper 一次都不该被调）")
    void deadlineAlreadyExpired_isNotSwallowed() {
        long expired = System.currentTimeMillis() - 1_000;

        try (DeadlineState.Scope ignored = DeadlineState.bindToCurrent(expired)) {
            assertThatThrownBy(() -> service.getUser(1L))
                    .satisfies(AdminDeadlinePassthroughTest::assertDeadlineExceeded);
        }
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("真实 DB 错误仍报 INTERNAL，不会被误判成超时")
    void genuineDbFailureStillReportsInternal() {
        when(userMapper.selectOne(any(Wrapper.class)))
                .thenThrow(new IllegalStateException("connection pool exhausted"));

        assertThatThrownBy(() -> service.getUser(1L))
                .satisfies(t -> assertThat(((StatusRuntimeException) t).getStatus().getCode())
                        .isEqualTo(Status.Code.INTERNAL));
    }

    @Test
    @DisplayName("用户不存在仍报 NOT_FOUND（三态语义不受本次改动影响）")
    void userNotFoundStillReportsNotFound() {
        when(userMapper.selectOne(any(Wrapper.class))).thenThrow(new NotFoundException("用户不存在"));

        assertThatThrownBy(() -> service.getUser(1L))
                .satisfies(t -> assertThat(((StatusRuntimeException) t).getStatus().getCode())
                        .isEqualTo(Status.Code.NOT_FOUND));
    }

    @Test
    @DisplayName("正常路径不受影响：查到用户就返回用户")
    void happyPathStillWorks() {
        UserEntity user = new UserEntity();
        user.setUid(1L);
        user.setUsername("alice");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(user);

        assertThat(service.getUser(1L).getUsername()).isEqualTo("alice");
    }
}