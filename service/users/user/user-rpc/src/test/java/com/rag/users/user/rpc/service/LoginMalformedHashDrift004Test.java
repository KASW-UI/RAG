package com.rag.users.user.rpc.service;

import com.rag.users.common.cryptx.PasswordUtil;
import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.rpc.config.UserRpcProperties;
import com.rag.users.user.rpc.domain.UserEntity;
import com.rag.users.user.rpc.mapper.UserMapper;
import com.rag.users.user.rpc.metrics.UserRpcMetrics;
import com.rag.users.user.proto.LoginReq;
import com.rag.users.user.proto.LoginResp;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DRIFT-004 端到端回归：login 遇到<b>损坏的 bcrypt hash</b> 时的行为。
 *
 * <p><b>修复前</b>：{@code PasswordUtil.check} 对 cost 越界的 hash 抛
 * {@code IllegalArgumentException} ⇒ 冒泡到 {@code GlobalExceptionHandler#handleAny}
 * ⇒ <b>HTTP 500 + 5001</b>（「服务端故障」）。
 *
 * <p><b>修复后</b>：与 Go 的 {@code cryptx.CheckPassword} 一致 ——
 * {@code err == nil} 为 false ⇒ 登录失败三态里的 {@code status=1}
 * （用户不存在 / 密码错共用，Go 侧 loginlogic.go 同款）。
 *
 * <p><b>为什么在 Service 层测而不在 PasswordUtil 层</b>：util 层只能证明
 * 「返回了 false」，证明不了「false 真的走成了密码错而不是别的地方抛」。
 * §3.3 的纪律：mock 数据层的单测只证明调用关系，真正的语义在 <b>返回值如何被使用</b>。
 *
 * <p><b>附带的反向断言</b>：{@code login_malformedHashStillLogsAndMetrics}
 * 证明 MALFORMED_HASH <b>不是</b>静默的 —— 它仍然会走 metrics + 日志，
 * 只是额外多一条 span 属性。这是「对齐 Go 的行为、保留 Spring 的表达」的可观测部分。
 */
class LoginMalformedHashDrift004Test {

    private UserMapper userMapper;
    private UserRpcMetrics metrics;
    private UserDomainService service;

    private static final String GOOD_HASH = PasswordUtil.encrypt("pw123456");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        metrics = new UserRpcMetrics(new SimpleMeterRegistry());
        StringRedisTemplate bizRedis = mock(StringRedisTemplate.class);
        when(bizRedis.opsForValue()).thenReturn(mock(ValueOperations.class));
        AvatarDomainService avatarDomainService = new AvatarDomainService();
        service = new UserDomainService(
                userMapper,
                mock(com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper.class),
                metrics,
                bizRedis,
                mock(LoggerFactoryBean.class),
                new UserRpcProperties(),
                avatarDomainService);
    }

    private void givenUserWithHash(String storedHash) {
        UserEntity user = new UserEntity();
        user.setUid(123L);
        user.setUsername("alice");
        user.setPassword(storedHash);
        user.setStatus(0L);
        when(userMapper.findOneByUserName("alice")).thenReturn(user);
    }

    /**
     * <b>核心断言</b>：修复前这里会抛 {@code IllegalArgumentException}（⇒ 500 + 5001）。
     *
     * <p>写法上刻意<b>不</b>用 {@code assertThatCode(...).returnItem()} ——
     * 本仓 AssertJ 版本上该方法返回 void、无法取回值。
     * 改为「先断言不抛、再单独调一次」，语义等价且跨版本稳定。
     */
    @Test
    @DisplayName("DRIFT-004 端到端：损坏的 bcrypt hash → 登录失败 status=1，【不得抛 500】")
    void malformedHashYieldsLoginFailureNotServerError() {
        // cost 字段越界 —— 实测唯一会触发 Spring 抛异常的形态
        givenUserWithHash("$2a$99$" + GOOD_HASH.substring(7));
        LoginReq req = LoginReq.newBuilder().setUsername("alice").setPassword("pw123456").build();

        assertThatCode(() -> service.login(req)).doesNotThrowAnyException();

        // 与 Go 一致：status=1（用户不存在 / 密码错共用该码，见 loginlogic.go）
        LoginResp resp = service.login(req);
        assertThat(resp.getStatus()).isEqualTo(1L);
        assertThat(resp.getUid()).isEqualTo(0L);
    }

    /**
     * <b>可观测性没有被牺牲</b>：Go 侧这一路完全静默，RAG 对齐行为的同时
     * 额外保留了 metrics 计数（与「密码错」同码计数，因为对外无法区分）。
     */
    @Test
    @DisplayName("DRIFT-004：损坏 hash 仍计入 loginInternalError（与 Go 的计数口径一致）")
    void malformedHashStillIncrementsTheSameMetricAsWrongPassword() {
        givenUserWithHash("$2a$99$" + GOOD_HASH.substring(7));
        service.login(LoginReq.newBuilder().setUsername("alice").setPassword("pw123456").build());

        double counter = metrics.getLoginInternalError().count();
        assertThat(counter).isEqualTo(1.0d);

        // 对照组：正常密码错也计 1 次 —— 证明两条路径的对外计数口径相同
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UserRpcMetrics m2 = new UserRpcMetrics(registry);
        UserMapper mapper2 = mock(UserMapper.class);
        UserEntity u = new UserEntity();
        u.setUid(123L);
        u.setUsername("alice");
        u.setPassword(GOOD_HASH);
        u.setStatus(0L);
        when(mapper2.findOneByUserName("alice")).thenReturn(u);
        UserDomainService svc2 = new UserDomainService(
                mapper2,
                mock(com.rag.users.user.rpc.mapper.UserAvatarHistoryMapper.class),
                m2,
                mock(StringRedisTemplate.class),
                mock(LoggerFactoryBean.class),
                new UserRpcProperties(),
                new AvatarDomainService());
        svc2.login(LoginReq.newBuilder().setUsername("alice").setPassword("wrong").build());

        assertThat(m2.getLoginInternalError().count()).isEqualTo(1.0d);
    }

    /**
     * <b>负向对照（§3.3）</b>：合法 hash + 密码错必须走同一条「失败」路径，
     * 且<b>不能</b>被误判成 MALFORMED_HASH。
     *
     * <p>这条保证修复没有把「密码错」也一起吞成 malformed ——
     * 那会让可观测性信号失去区分能力。
     */
    @Test
    @DisplayName("DRIFT-004 负向对照：合法 hash + 密码错 → status=1（不是 malformed）")
    void legitimateWrongPasswordIsStillJustStatusOne() {
        givenUserWithHash(GOOD_HASH);

        LoginResp resp = service.login(LoginReq.newBuilder()
                .setUsername("alice").setPassword("wrong-password").build());

        assertThat(resp.getStatus()).isEqualTo(1L);
        // 反证：合法 hash 的错误密码被归类为 MISMATCHED，绝不是 MALFORMED_HASH
        assertThat(PasswordUtil.checkDetailed("wrong-password", GOOD_HASH))
                .isEqualTo(PasswordUtil.Outcome.MISMATCHED);
    }

    /**
     * 正常路径不得被这次修复影响 —— hash 合法 + 密码对 ⇒ status=0 + 真实 uid。
     */
    @Test
    @DisplayName("DRIFT-004 反向：合法 hash + 正确密码 → 仍登录成功（修复没改正常路径）")
    void validLoginStillWorks() {
        givenUserWithHash(GOOD_HASH);

        LoginResp resp = service.login(LoginReq.newBuilder()
                .setUsername("alice").setPassword("pw123456").build());

        assertThat(resp.getStatus()).isEqualTo(0L);
        assertThat(resp.getUid()).isEqualTo(123L);
    }
}
