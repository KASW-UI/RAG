package com.rag.common.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 「<b>本类属 api 侧 rest 中间件 ⇒ 只在 api 侧注册</b>」的<b>单一真源</b>条件注解。
 *
 * <h2>Go 出处（为什么 rpc 域不该有这些类）</h2>
 * <p>Go 侧的 rpc 服务<b>全部</b>是 {@code zrpc.MustNewServer}
 * （{@code service/{article,comment,favorite,follow,hot,like,message,task}/rpc/*.go}）
 * ⇒ ⇒ <b>一个 rest 中间件都没挂</b>；
 * 而 {@code rest.With*}（路径归一 / content-type / 熔断 / 降载 / 参数上限 …）<b>全在 api 侧</b>
 * （{@code service/<域>/api/internal/handler/routes.go}）
 * ⇒ ⇒ ⇒ <b>「rpc 域的观测端口上挂着 Go 的 api 侧中间件」= 一处真偏离</b>
 * ⇒ ⇒ ⇒ 共享层的 api 侧中间件必须在 rpc 域<b>不注册</b>。
 *
 * <h2>⚠️ 为什么是这一条（2026-10-30 · G4 lane · 第九次订正）</h2>
 * <p><b>此前 {@code rag.side=api} 只罩住了 2 个类</b>
 * （{@code GoStylePathNormalizingAutoConfiguration} ＋ {@code GoStyleJsonContentTypeAdvice}）⇒ ⇒
 * <b>其余 11 个 api 侧中间件没有这层限定</b> ⇒ ⇒ 而它们此前「在 rpc 域不注册」的依据是
 * <b>域侧约定</b>（各 rpc 域的 {@code @SpringBootApplication} 刻意不扫 {@code com.rag.common}）
 * ⇒ ⇒ <b>不是这些类自己的条件</b> ⇒ ⇒ ⇒
 * <b>一旦有人把 {@code com.rag.common} 加进某个 rpc 域的 {@code scanBasePackages}，这 11 个类会全部活起来</b>
 * ⇒ ⇒ ⇒ 而 {@code rag.side=rpc} <b>救不了它们</b>（它们不看那个属性）⇒ ⇒ ⇒
 * ⇒ ⇒ <b>护栏必须从「域侧自觉」收回「共享层自己兜住」</b> ⇒ ⇒ 本注解就是那层兜底。
 *
 * <h2>语义等价论证（三条）</h2>
 * <ol>
 *   <li><b>api 域行为逐字不变</b>：api 域<b>不设</b> {@code rag.side}
 *       ⇒ ⇒ 条件按 {@code matchIfMissing=true} 成立 ⇒ ⇒ 本类照旧注册、照旧生效；</li>
 *   <li><b>rpc 域与 Go 同形</b>：rpc 域设 {@code rag.side=rpc} ⇒ ⇒ 本类不注册
 *       ⇒ ⇒ 与 Go 的 {@code zrpc.MustNewServer}（零 rest 中间件）一致；</li>
 *   <li><b>护栏强度提升</b>：即便某个 rpc 域误扫 {@code com.rag.common}
 *       ⇒ ⇒ 本类仍被 {@code rag.side=rpc} 挡住 ⇒ ⇒ <b>不再依赖域侧约定</b>。</li>
 * </ol>
 *
 * <h2>⚠️ 边界（必须写清）</h2>
 * <ul>
 *   <li>本注解只管「<b>注册与否</b>」，<b>不管内容语义</b>；api 域的中间件内容本轮一个字没改；</li>
 *   <li>本注解是<b>元注解</b>（{@code @ConditionalOnProperty} 通过元注解生效）
 *       ⇒ ⇒ <b>元注解的装配是否真生效，必须有运行证据</b>（AGENTS.md §3.3：注解存在 ≠ 机制生效）
 *       ⇒ ⇒ 证据在 {@code GoStyleApiSideMiddlewareActivationGateTest}
 *       （形态③与形态⑤<b>只差 {@code rag.side} 一个变量</b> ⇒ ⇒ 差异被隔离到该变量）；</li>
 *   <li><b>默认是 api</b> ⇒ ⇒ <b>rpc 域必须显式写 {@code rag.side: rpc}</b>
 *       ⇒ ⇒ 那一行在<b>各域 yml</b>（域侧写权）⇒ ⇒ 不在本包写权面
 *       ⇒ ⇒ <b>已转派合并收口执行者</b>（已分别通知 G1 / G2 / G3）。</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "rag.side", havingValue = "api", matchIfMissing = true)
public @interface ConditionalOnApiSide {
}
