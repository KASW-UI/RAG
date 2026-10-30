package com.rag.test.api;

import com.rag.common.protection.GoStyleProtectionConfiguration;
import net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * <b>模拟一个 api 进程的装配面</b>（api 侧的组件扫描口径）。
 *
 * <h2>为什么这个类存在</h2>
 * <ul>
 *   <li><b>不能依赖域侧模块</b>：本文件属于共享层（{@code service/common}）⇒
 *       它的测试<b>不允许</b>依赖某个域的 jar（否则共享层就绑死在某个域上）。</li>
 *   <li><b>必须与 api 侧同形</b>：10 个 api 组件里有 9 个用
 *       {@code @SpringBootApplication(scanBasePackages = {…, "com.rag.common"})}，
 *       {@code task-api} 用 {@code @ComponentScan} ⇒
 *       ⇒ <b>api 侧靠「包扫描」拿到共享层的配置类</b>，
 *       而 rpc 侧靠 {@code @Import} ⇒ ⇒ <b>两侧的装配机制不同</b>，
 *       ⇒ ⇒ 共享层<b>必须分别证明</b>两条机制都能拿到出向 deadline 拦截器
 *       （rpc 侧那条见 {@code GoStyleOutboundDeadlineRpcAssemblyTest}）。</li>
 * </ul>
 *
 * <h2>为什么用 includeFilters 精确只扫一个类</h2>
 * <p>{@code com.rag.common.protection} 这一包里还有 4 个 {@code @Component} 的
 * servlet Filter（MaxBytes / MaxConns / CpuShedding / Breaker）⇒ 全包扫描会把它们
 * 一起注册进来 ⇒ 那些 Filter 可能依赖本测试不提供的 Bean ⇒ 上下文起不来 ⇒
 * ⇒ 这里用 {@code useDefaultFilters = false} ＋ {@code includeFilters}
 * <b>只注册</b> {@link GoStyleProtectionConfiguration} 一个类。
 * <p>⚠️ <b>这个选择的效力边界（必须写清）</b>：本上下文证明的是
 * 「<b>包扫描能发现 {@code GoStyleProtectionConfiguration} ⇒ 它的
 * {@code @Import} 链会产出拦截器 ⇒ 拦截器进了 devh 的 registry</b>」，
 * <b>不等于</b>「某个具体 api 进程（带它自己的 yml 与全部共享层 Bean）也一定起得来」
 * ⇒ ⇒ 后者需要域侧各自的守门（本 lane 无域侧写权 ⇒ 登记为转派项）。
 *
 * <h2>为什么用 plain {@code @Configuration} 而不是 {@code @SpringBootApplication}</h2>
 * <p>{@code @SpringBootApplication} 隐含 {@code @SpringBootConfiguration} ⇒
 * 若测试类路径上出现<b>两个</b>这样的类，Spring Boot 的测试上下文搜索会抛
 * {@code Found multiple @SpringBootConfiguration annotated classes}
 * ⇒ 本类用 plain {@code @Configuration} ＋ {@code @ImportAutoConfiguration}
 * ⇒ <b>既不制造第二个 {@code @SpringBootConfiguration}</b>，
 * 又能让 devh 的自动配置（{@link GrpcClientAutoConfiguration}）真的跑起来。
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(GrpcClientAutoConfiguration.class)
@ComponentScan(
        basePackages = "com.rag.common.protection",
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = GoStyleProtectionConfiguration.class))
public class ApiLikeApplication {
}
