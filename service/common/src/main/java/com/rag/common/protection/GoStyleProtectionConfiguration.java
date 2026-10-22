package com.rag.common.protection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 让 {@link GoStyleProtectionProperties} <b>随包自动注册</b>，不要求每个业务模块写一行装配。
 *
 * <h2>为什么必须有这个类（2026-10-22 实测踩出来的）</h2>
 *
 * <p>保护层下沉到 {@code com.rag.common.protection} 后，四个 Filter 跟着进了组件扫描范围。
 * 它们构造器都要 {@link GoStyleProtectionProperties}，而
 * <b>{@code @ConfigurationProperties} 类不会被组件扫描自动注册</b>
 * —— 必须由 {@code @EnableConfigurationProperties} 或 {@code @ConfigurationPropertiesScan} 声明。
 *
 * <p>实测后果：在下沉当时，只有 {@code message-api} 写了那一行声明，
 * 而 {@code service/users/admin/admin-api} 也扫 {@code com.rag.common}（它同样有 web 栈），
 * 于是它的 {@code AdminApiMvcPipelineTest} <b>23 条全红</b>，报错是：
 * <pre>
 *   Error creating bean with name 'goStyleBreakerFilter'
 *   ... Unsatisfied dependency expressed through constructor parameter 0:
 *   No qualifying bean of type 'com.rag.common.protection.GoStyleProtectionProperties' available
 * </pre>
 * ⇒ <b>一个组件从「私有」变成「共享」，它的装配义务必须跟着一起共享。</b>
 * 把注册写成「每个下游模块各加一行 {@code @EnableConfigurationProperties}」是错的：
 * 那是把同一个义务复制 N 份，漏一份就是一个<b>启动即崩</b>的模块。
 *
 * <p>本类把这个义务收回包内：只要某个进程的 {@code scanBasePackages} 含 {@code com.rag.common}，
 * 扫描到这个 {@code @Configuration} 时就会顺带注册 properties Bean。
 * <b>下游模块零装配代码。</b>
 *
 * <h2>为什么本类<b>不</b>加 {@code @ConditionalOnWebApplication}</h2>
 *
 * <p>四个 Filter 各自挂了 {@code @ConditionalOnWebApplication(SERVLET)}，本类没有 ——
 * 于是非 web 进程里仍会存在一个 {@code GoStyleProtectionProperties} Bean（一个纯 POJO，无副作用），
 * 而 Filter 一个都不注册。这样分层的原因：
 * <ul>
 *   <li><b>Filter</b> 是 {@code jakarta.servlet.Filter} 的实现，没 servlet 就连类都加载不了 ⇒ 必须条件化。</li>
 *   <li><b>properties</b> 只是字段容器，加不加条件都不影响启动 ⇒ 保持无条件，
 *       免得「配置没绑上」和「上下文类型不对」两件事混成同一个难查的现象。</li>
 * </ul>
 * 两条边界各有 {@code message-api} 侧的测试钉住，见
 * {@code ProtectionNotRegisteredInNonWebContextTest} 与 {@code MessageApiProtectionMvcTest}。
 *
 * <h2>本类不是 Spring Boot 自动配置</h2>
 *
 * <p>它<b>不</b>放在 {@code META-INF/spring/...AutoConfiguration.imports} 里。
 * 本仓库没有 {@code spring-boot-autoconfigure} 的自动配置清单机制在用，
 * 一律靠 {@code scanBasePackages} 扫 {@code com.rag.common}。
 * 所以「共享」在本项目里的准确含义是<b>「被扫到即生效」</b>，不是「进了自动配置清单才生效」。
 * ⚠️ 这也是为什么 {@code admin-rpc} / {@code user-rpc} / {@code message-rpc} / {@code like-rpc}
 * 刻意不扫 {@code com.rag.common}：一旦扫了，本类与四个 Filter 都会被拉进来，
 * 而它们依赖 servlet API —— 那正是四个 Filter 上那个条件注解要挡住的情形。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoStyleProtectionProperties.class)
public class GoStyleProtectionConfiguration {
}