package com.rag.common.redis;

import io.lettuce.core.ClientOptions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * <b>L4 idiomatization</b>：把 Lettuce 客户端的「<b>断连行为</b>」收进共享层，
 * 对齐 Go 的 <b>Redis 失败 ⇒ 尽快失败 ⇒ 上层 fail-open</b> 语义。
 *
 * <p><b>Go 出处（消费点，不是声明点）</b>：
 * <pre>
 *   service/user/user/api/internal/middleware/checkblacklistmiddleware.go:35
 *       exists, err := m.Redis.ExistsCtx(r.Context(), blackListKey)
 *   service/user/user/api/internal/middleware/checkblacklistmiddleware.go:36
 *       if err == nil &amp;&amp; exists { … 401 … }
 *         ⇒ err != nil 时<b>不拦截</b> ⇒ <b>fail-open 放行</b>
 *
 *   service/user/admin/api/internal/middleware/checkblacklistmiddleware.go  （admin 域同款）
 *   service/follow/api/internal/middleware/checkblacklistmiddleware.go       （follow 域同款）
 *     ⇒ 三份文件内容<b>不同</b>（md5 各异），但「err != nil ⇒ 放行」这一条<b>三份一致</b>。
 *
 *   core/stores/redis/redisclientmanager.go:18  maxRetries = 3
 *   core/stores/redis/redisclientmanager.go:27-46
 *       red.NewClient(&amp;red.Options{Addr, MaxRetries: maxRetries, MinIdleConns: idleConns})
 *       ⇒ <b>没有</b>设置 DialTimeout / ReadTimeout ⇒ 用 go-redis v9 默认值，
 *         且 MaxRetries=3 <b>明确开启重试</b>；连接被拒时每次重试都是<b>立即失败</b>
 *         ⇒ 整体<b>快速失败</b>（users 域实测 ~1.7s）。
 * </pre>
 *
 * <p><b>为什么 Lettuce 默认行为与 Go 不同（这是本类存在的唯一理由）</b>：
 * <ul>
 *   <li><b>Lettuce 默认</b>（{@link ClientOptions.DisconnectedBehavior#DEFAULT}）：
 *       断连期间到达的命令被<b>挂起排队</b>，等重连成功后补发
 *       ⇒ 一次黑名单查询要等到 {@code spring.data.redis.timeout}（users 域 3s）才失败
 *       ⇒ <b>请求超时预算先耗尽</b> ⇒ 对外 <b>503</b>（Go 是 <b>200</b>）。</li>
 *   <li><b>{@link ClientOptions.DisconnectedBehavior#REJECT_COMMANDS}</b>：
 *       断连时<b>立即拒单</b>（命令直接失败，不排队）
 *       ⇒ Redis 挂掉时失败耗时 ≪ 请求预算 ⇒ 进业务层 ⇒ <b>fail-open 200</b>，与 Go 一致。</li>
 * </ul>
 *
 * <p><b>语义等价论证</b>：
 * <ol>
 *   <li>两侧在「Redis 不可用」时都<b>尽快让命令失败</b>（Go：MaxRetries=3 + 立即失败的重试；
 *       RAG：断连即拒单）。</li>
 *   <li>失败都<b>被上层捕获并 fail-open</b>：Go 是 {@code if err == nil &amp;&amp; exists}；
 *       RAG 侧各 api 的黑名单拦截器是 {@code try { exists = redis.hasKey(key); }
 *       catch (Throwable) { /* 放行 *\/ }}（例如 users 域
 *       {@code JwtAuthInterceptor.isBlacklisted}）。</li>
 *   <li>⇒ <b>对外都是「放行 + 业务层正常响应」</b>。</li>
 * </ol>
 *
 * <p><b>⚠️ 为什么这一段曾经是「users 域私产」，现在要上移</b>：
 * {@code service/users/user/user-api} 里有一份同款配置
 * （{@code RedisDisconnectedBehaviorConfig}，2026-10-24 第九轮修 {@code DRIFT-USERAPI-014} 时落的）。
 * 但<b>黑名单 fail-open 是所有域共有的需求</b>，而那份配置<b>只对 user-api 生效</b>：
 * <ul>
 *   <li><b>follow-api</b>：{@code FollowBlacklistInterceptor} 同样查 Redis 黑名单
 *       ⇒ Redis 断连时**同样**会撞超时预算 ⇒ <b>同样的漂移</b>；</li>
 *   <li><b>favorite-api</b>：{@code FavoriteJwtAuthInterceptor} 同款；</li>
 *   <li><b>各 rpc 组件</b>：comment / like / hot / task 的缓存读写在 Redis 断连时
 *       同样会<b>挂起</b>而不是快速失败 ⇒ 与 Go 的「快速失败」不一致。</li>
 * </ul>
 * ⇒ 上移到共享层 ⇒ <b>零域侧改动</b>即让全部用 Redis 的组件获得 Go 的失败语义。
 *
 * <p><b>⚠️ 过渡期与 users 域那份并存 ⇒ 幂等，不是冲突</b>：
 * 两份都把 {@code disconnectedBehavior} 设成 {@code REJECT_COMMANDS} ⇒
 * Boot 的 {@code LettuceClientOptionsBuilderCustomizer} 会把<b>所有</b> customizer 依次应用
 * ⇒ 后者覆盖前者，<b>值相同 ⇒ 结果相同</b>。
 * ⇒ ⇒ users 域那份可在合并后单独删除（纯删除、零行为变化）。
 *
 * <p><b>⚠️ 为什么用自动配置而不是 {@code @Component}</b>：
 * 本类引用 {@link io.lettuce.core.ClientOptions} 与 {@link LettuceConnectionFactory}
 * —— 而<b>纯 gRPC 组件与不使用 Redis 的 api 组件根本没有这两个类</b>
 * ⇒ 若用 {@code @Component}，组件扫描会在这类进程里<b>硬失败</b>
 * （{@code NoClassDefFoundError}）⇒ 必须用 {@code @ConditionalOnClass} + 自动配置清单。
 *
 * <p><b>⚠️ 为什么不动命令超时（{@code timeoutOptions}）</b>：
 * Boot 的 {@code LettuceConnectionConfiguration} <b>先</b>把
 * {@code spring.data.redis.timeout} 写进 {@code ClientOptions.Builder.timeoutOptions}，
 * <b>再</b>依次应用 customizer ⇒ customizer 里再调一次 {@code timeoutOptions(...)}
 * 会<b>整体覆盖</b>掉运维配的命令超时 ⇒ 那才是真的新漂移。
 * ⇒ 本类<b>只</b>改断连行为，<b>不碰</b>超时。
 * （「Redis 命令感知请求 deadline」= global 台账 <b>E66</b>，属另一条，
 * 需要在保留既有命令超时的前提下另立一轮，见 {@code docs/DRIFT/global/vs-common.md}。）
 */
@AutoConfiguration
@ConditionalOnClass({LettuceConnectionFactory.class, ClientOptions.class,
        LettuceClientOptionsBuilderCustomizer.class})
public class GoStyleRedisClientAutoConfiguration {

    /**
     * 注册「断连即拒单」的客户端选项定制器。
     *
     * <p>⚠️ Bean 名固定为 {@code goStyleRedisClientOptionsCustomizer}
     * ⇒ 便于守门按名字精确断言「共享层这一份确实在容器里」。
     *
     * <p>⚠️ <b>为什么有 {@code @ConditionalOnMissingBean}</b>：本类位于 {@code com.rag.common.redis}
     * ⇒ <b>api 组件的 {@code scanBasePackages} 含 {@code com.rag.common}</b>
     * ⇒ 本类会<b>既被组件扫描、又被自动配置清单</b>各注册一次
     * ⇒ 两者 {@code @Bean} 方法名相同 ⇒ 不同名冲突 ⇒ <b>启动直接失败</b>
     * ⇒ ⇒ 必须去重（与 {@code GoStylePathNormalizingAutoConfiguration} 同一套办法）。
     */
    @Bean
    @ConditionalOnMissingBean
    public LettuceClientOptionsBuilderCustomizer goStyleRedisClientOptionsCustomizer() {
        return builder -> builder.disconnectedBehavior(
                ClientOptions.DisconnectedBehavior.REJECT_COMMANDS);
    }
}
