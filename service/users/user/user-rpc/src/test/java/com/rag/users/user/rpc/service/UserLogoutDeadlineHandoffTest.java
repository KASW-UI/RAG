package com.rag.users.user.rpc.service;

import com.rag.common.spring.LoggerFactoryBean;
import com.rag.users.user.proto.LogoutReq;
import com.rag.users.user.proto.LogoutResp;
import com.rag.users.user.rpc.config.UserRpcProperties;
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
 * logout 的「期限感知上移到共享层」回归网（<b>user 域</b> · 与 admin 侧同款 · 2026-10-31）。
 *
 * <p><b>背景 · Go 出处</b>：{@code service/user/user/rpc/internal/logic/logoutlogic.go}
 * （<b>全文 44 行</b> · 本轮逐行读过）—— <b>没有 ctx 预检</b>；{@code :37}
 * {@code err := l.svcCtx.BizRedis.SetexCtx(l.ctx, blackListKey, "banned", int(expireTime))}
 * <b>写绑 ctx</b>；{@code :38-40} 写失败 ⇒
 * {@code status.Error(codes.Internal, "注销失败,Redis写入异常")}。
 * ⇒ <b>Go 的 handler 级码 = INTERNAL(5001)</b>，且 Go <b>不是</b>在入口拒请求，
 * 而是<b>让写去失败</b>。
 *
 * <p><b>为什么 user 域要补这一份</b>：admin 侧早就有同款
 * {@code AdminLogoutDeadlineHandoffTest}，而 user 侧此前<b>只有</b>入口那处
 * {@code checkDeadline()}（抢先闸门）⇒ ⇒ 期限已过期时 RAG 侧 handler 级码是
 * {@code DEADLINE_EXCEEDED}(1015)，与 Go 的 {@code 5001} <b>不同构</b>
 * ⇒ ⇒ 共享层 {@code GoStyleRedisDeadline} 的 javadoc 里点名要 G1 去掉那处抢先检查。
 * ⇒ ⇒ 本类就是去掉之后的<b>回归网</b>（与 admin 侧同款）。
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
 *       {@code service/common/.../redis/GoStyleRedisDeadline.java}）⇒ 代理在位时同款；
 *       但<b>代理的装配接线</b>由 G4 侧的 Redis 期限守门守 ⇒ <b>跨 lane</b>。</li>
 *   <li><b>真链路复跑</b>（真 Redis ＋ 真 gRPC ＋ 真实期限耗尽）属阶段二复跑项
 *       ⇒ 与共享层代理<b>同一个提交</b>才成立。</li>
 * </ul>
 */
class UserLogoutDeadlineHandoffTest {

    /** Go 侧 {@code logoutlogic.go:35} 的 key 拼法：{@code fmt.Sprintf("user:jwt_blacklist:%s", in.Token)}。 */
    private static final String KEY_PREFIX = "user:jwt_blacklist:";

    /** Go 侧 {@code logoutlogic.go:36} 取的是 {@code UserAuth.AccessExpire}（默认 2592000 秒 = 30 天）。 */
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

        UserDomainService svc = newService(redis);

        Thread.currentThread().interrupt();
        LogoutResp resp;
        try {
            resp = svc.logout(LogoutReq.newBuilder().setToken("tok-1").build());
        } finally {
            Thread.interrupted();
        }

        assertThat(resp)
                .as("期限耗尽不应在域内被拒（Go 侧不是入口拒，而是让写去失败）")
                .isNotNull();
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

        UserDomainService svc = newService(redis);

        assertThatThrownBy(() -> svc.logout(LogoutReq.newBuilder().setToken("tok-2").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(t -> {
                    assertThat(Status.fromThrowable(t).getCode())
                            .as("Go 侧 logoutlogic.go:38-40 = codes.Internal ⇒ RAG 侧必须是 INTERNAL")
                            .isEqualTo(Status.Code.INTERNAL);
                    assertThat(Status.fromThrowable(t).getDescription())
                            .as("文案必须与 Go 的 status.Error(codes.Internal, \"注销失败,Redis写入异常\") 逐字相同")
                            .isEqualTo("注销失败,Redis写入异常");
                });
    }

    /**
     * 反向：token 为空仍走 Go 的 {@code InvalidArgument} 分支（{@code logoutlogic.go:31-34}），
     * <b>不</b>受本次期限改造影响 ⇒ 防止「撤闸门」顺手撤掉了参数校验。
     */
    @Test
    @DisplayName("反向：token 为空 ⇒ 仍是 INVALID_ARGUMENT「Token不能为空」（Go logoutlogic.go:31-34）")
    void emptyTokenStillRejectedAsInvalidArgument() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UserDomainService svc = newService(redis);

        assertThatThrownBy(() -> svc.logout(LogoutReq.newBuilder().setToken("").build()))
                .isInstanceOf(StatusRuntimeException.class)
                .satisfies(t -> {
                    assertThat(Status.fromThrowable(t).getCode())
                            .isEqualTo(Status.Code.INVALID_ARGUMENT);
                    assertThat(Status.fromThrowable(t).getDescription())
                            .isEqualTo("Token不能为空");
                });
    }

    private UserDomainService newService(StringRedisTemplate redis) {
        UserRpcProperties props = new UserRpcProperties();
        props.getUserAuth().setAccessExpire(ACCESS_EXPIRE_SECONDS);
        // 其余依赖传 null：logout 路径只碰 redis + properties（不碰 mapper / metrics / avatar）
        // ⚠️ logger 必须给 mock：② 的 catch(Exception) 分支会调 logger.logBusinessErr(..., e)
        return new UserDomainService(null, null, null, redis, mock(LoggerFactoryBean.class), props, null);
    }
}
