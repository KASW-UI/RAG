package com.rag.common.protection;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

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
 *
 * <h2>⚠️ 2026-10-27：本类新增 {@code @Import(GoStyleGrpcClientBreakerConfiguration.class)}</h2>
 *
 * <p><b>为什么</b>：gRPC <b>client</b> 侧熔断拦截器原先带 {@code @Component} ⇒ 只能靠组件扫描拿到
 * ⇒ 而纯 gRPC 进程<b>刻意不扫</b> {@code com.rag.common} ⇒ ⇒ <b>rpc 侧永远拿不到 client breaker</b>
 * ⇒ <b>出向调用无熔断</b>（Go 侧 zrpc <b>默认开</b>：{@code zrpc/internal/config.go:14}
 * {@code Breaker bool `json:",default=true"`} ＋ {@code zrpc/internal/client.go:112-113} 装配）。
 *
 * <p><b>为什么这样改，而不是「让 10 个 rpc 模块各加一行 {@code @Import}」</b>：
 * 本仓库的纪律是「共享层的装配义务收回包内、下游模块零装配代码」；
 * 而且 10 个 rpc 组件<b>已经全部</b> {@code @Import} 了本类
 * ⇒ ⇒ <b>零域侧改动</b>即让全部 rpc 进程获得 client breaker。
 *
 * <p><b>语义等价论证</b>：Go 侧每个 zrpc client 都装 breaker 拦截器（{@code client.go:112-113}）
 * ⇒ RAG 侧每个 rpc 进程现在也装 ⇒ 两侧「client breaker 是否在链上」<b>不再有域差异</b>；
 * acceptable 判据与默认开关<b>均未改动</b>（分别见
 * {@link GoStyleGrpcClientBreakerInterceptor#isAcceptable} 与
 * {@link GoStyleProtectionProperties#getBreaker()}）。
 *
 * <p>⇒ 锁定用例：{@code GoStyleGrpcClientBreakerRpcAssemblyTest}（真 Spring 上下文，
 * 模拟 rpc 进程的装配面）＋ {@code GoStyleGrpcClientBreakerSingleRegistrationTest}
 * （证明 api 侧与 rpc 侧<b>不会各注册一个</b>）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoStyleProtectionProperties.class)
@Import({GoStyleGrpcClientBreakerConfiguration.class, GoStyleOutboundDeadlineConfiguration.class})
public class GoStyleProtectionConfiguration {
}