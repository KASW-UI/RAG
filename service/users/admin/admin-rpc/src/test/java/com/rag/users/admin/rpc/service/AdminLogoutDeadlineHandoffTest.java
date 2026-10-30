package com.rag.users.admin.rpc.service;

import com.rag.users.admin.rpc.config.AdminRpcProperties;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * logout 的「期限感知上移到共享层」回归网（2026-10-30 新增）。
 *
 * <p><b>背景 · Go 出处</b>：{@code service/user/admin/rpc/internal/logic/logoutlogic.go}
 * （<b>全文 46 行</b>）—— <b>没有 ctx 预检</b>；{@code :38}
 * {@code l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))}
 * <b>写绑 ctx</b>；{@code :39-42} 写失败 ⇒
 * {@code status.Error(codes.Internal, "注销失败,Redis写入异常")}。
 * ⇒ <b>Go 的 handler 级码 = INTERNAL(5001)</b>，且 Go <b>不是</b>在入口拒请求，
 * 而是<b>让写去失败</b>。
 *
 * <p><b>本类守两件事</b>（对应 §3.3「先改断言、再改实现」的两层）：
 * <ol>
 *   <li><b>① 域侧不再抢先拦</b>：期限已耗尽时，{@code logout} 仍要<b>调用到</b> Redis 写
 *       ⇒ 与 Go 同形（这一层<b>与共享层代理在不在位无关</b>，两侧都成立）。</li>
 *   <li><b>② handler 级码映射</b>：Redis 写以<b>期限耗尽</b>失败时，
 *       handler 级码必须是 {@code INTERNAL}「注销失败,Redis写入异常」
 *       ⇒ 与 Go 的 {@code codes.Internal} 逐字同形。</li>
 * </ol>
 *
 * <p>⚠️ <b>本类的证据边界（诚实声明，别把「跑绿」读成「已验证」）</b>：
 * <ul>
 *   <li>本类用 <b>Mockito 替换数据层</b> ⇒ 它<b>只</b>证明「域服务的控制流与异常映射」，
 *       <b>不</b>证明「共享层代理真的在 Redis 调用路径上」。</li>
 *   <li>② 用的是共享层代理<b>实际抛出的异常类型</b>
 *       （{@code org.springframework.dao.QueryTimeoutException}，见
 *       {@code service/common/.../redis/GoStyleRedisDeadline.java:88-92}）
 *       ⇒ 代理在位时同款；但<b>代理的装配接线</b>由 G4 侧的
 *       {@code GoStyleRedisDeadlineGateTest} 守 ⇒ <b>跨 lane</b>。</li>
 *   <li><b>真链路复跑</b>（真 Redis ＋ 真 gRPC ＋ 真实期限耗尽）属于
 *       <b>主干合入后</b>的复跑项 ⇒ 与共享层代理<b>同一个提交</b>才成立
 *       （合并约束：单独合入 ⇒ 域内无期限感知 ⇒ RAG 写、Go 不写 ⇒ 暴露 E66）。</li>
 * </ul>
 */
class AdminLogoutDeadlineHandoffTest {

    /** Go 侧 {@code logoutlogic.go:36} 的 key 拼法：{@code fmt.Sprintf("admin:jwt_blacklist:%s", in.Token)}。 */
    private static final String KEY_PREFIX = "admin:jwt_blacklist:";

    /** Go 侧 {@code logoutlogic.go:37} 取的是 {@code AdminAuth.AccessExpire}（默认 2592000 秒 = 30 天）。 */
    private static final long ACCESS_EXPIRE_SECONDS = 2592000L;

    @AfterEach
    void clearInterrupt() {
        // JUnit 同线程跑所有方法；残留的 interrupt 标记会让别的用例假红
        Thread.interrupted();
    }

    /**
     * ① 域侧不再抢先拦。
     *
     * <p><b>这条为什么有牙</b>：若 {@code logout} 入口还留着 deadline 闸门，
     * 本线程已被标记 interrupt ⇒ 方法第一行就抛 {@code DEADLINE_EXCEEDED}
     * ⇒ <b>Redis 写根本不会被调用</b> ⇒ {@code verify(...).set(...)} 失败 ⇒ 变红。
     */
    @Test
    @DisplayName("① 域侧不再抢先拦：期限已耗尽时 logout 仍调用到 Redis 写（与 Go logoutlogic.go 无 ctx 预检同形）")
    void logoutReachesRedisWriteEvenWhenDeadlineAlreadyExpired() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        AdminDomainService svc = newService(redis);

        Thread.currentThread().interrupt();
        boolean ok;
        try {
            ok = svc.logout("tok-1");
        } finally {
            Thread.interrupted();
        }

        assertThat(ok)
                .as("期限耗尽不应在域内被拒（Go 侧不是入口拒，而是让写去失败）")
                .isTrue();
        verify(ops).set(eq(KEY_PREFIX + "tok-1"), eq("banned"), any(Duration.class));
    }

    /**
     * ② handler 级码映射 = Go 的 {@code codes.Internal}。
     *
     * <p>共享层 Redis 期限代理在期限耗尽时抛的是 Spring 的
     * {@link QueryTimeoutException}（<b>不是</b> grpc 的 {@code StatusRuntimeException}）
     * ⇒ 它<b>不</b>被 B-1 的 {@code catch (StatusRuntimeException)} 拦下
     * ⇒ 落进 {@code catch (Exception)} ⇒ {@code INTERNAL}「注销失败,Redis写入异常」。
     */
    @Test
    @DisplayName("② handler 级码 = INTERNAL「注销失败,Redis写入异常」（与 Go codes.Internal 逐字同形）")
    void logoutMapsRedisDeadlineTimeoutToInternalLikeGo() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doThrow(new QueryTimeoutException("redis deadline exhausted"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        AdminDomainService svc = newService(redis);

        assertThatThrownBy(() -> svc.logout("tok-2"))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(t -> {
                    assertThat(Status.fromThrowable(t).getCode())
                            .as("Go 侧 logoutlogic.go:39-42 = codes.Internal ⇒ RAG 侧必须是 INTERNAL")
                            .isEqualTo(Status.Code.INTERNAL);
                    assertThat(Status.fromThrowable(t).getDescription())
                            .as("文案必须与 Go 的 status.Error(codes.Internal, \"注销失败,Redis写入异常\") 逐字相同")
                            .isEqualTo("注销失败,Redis写入异常");
                });
    }

    /**
     * 反向：token 为空仍走 Go 的 {@code InvalidArgument} 分支（{@code logoutlogic.go:32-35}），
     * <b>不</b>受本次期限改造影响 ⇒ 防止「撤闸门」顺手撤掉了参数校验。
     */
    @Test
    @DisplayName("反向：token 为空 ⇒ 仍是 INVALID_ARGUMENT「Token不能为空」（Go logoutlogic.go:32-35）")
    void emptyTokenStillRejectedAsInvalidArgument() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        AdminDomainService svc = newService(redis);

        assertThatThrownBy(() -> svc.logout(""))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(t -> {
                    assertThat(Status.fromThrowable(t).getCode())
                            .isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(Status.fromThrowable(t).getDescription())
                            .isEqualTo("Token不能为空");
                });
    }

    private AdminDomainService newService(StringRedisTemplate redis) {
        AdminRpcProperties props = new AdminRpcProperties();
        AdminRpcProperties.AdminAuth auth = new AdminRpcProperties.AdminAuth();
        auth.setAccessExpire(ACCESS_EXPIRE_SECONDS);
        props.setAdminAuth(auth);
        // 其余依赖传 null：logout 路径只碰 redis + properties（不碰 mapper / metrics）
        return new AdminDomainService(null, null, null, redis, props, null, null);
    }
}
