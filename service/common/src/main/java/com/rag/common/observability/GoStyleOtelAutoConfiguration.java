package com.rag.common.observability;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 🔴 **全局 OpenTelemetry 的装配点** —— 对应 Go 侧
 * {@code rest/server.go:49}（{@code c.SetUp()}）→
 * {@code core/service/serviceconf.go:67}（{@code trace.StartAgent(sc.Telemetry)}）。
 *
 * <h2>🔴🔴 为什么装配点必须是 <b>BeanFactoryPostProcessor</b>，而不是普通 Bean</h2>
 * <p>OTel Java 的 {@code GlobalOpenTelemetry.set(...)} 有两条硬约束：
 * <ol>
 *   <li><b>只能调一次</b>；</li>
 *   <li>🔴 <b>先调 {@code get()} 会让后续 {@code set()} 抛
 *       {@code IllegalStateException}</b>（{@code get()} 会先把全局置成 no-op 占位）。</li>
 * </ol>
 * 而本仓有<b>四个地方在构造器里就调 {@code get()}</b>：
 * <ul>
 *   <li>{@code service/common/.../communityfact/AuthorityHandler.java:116}（构造器）</li>
 *   <li>{@code service/message/message-rpc/.../MessageDomainService.java:122}（构造器）</li>
 *   <li>{@code service/users/user/user-rpc/.../UserDomainService.java:105}（构造器）</li>
 *   <li>{@code service/favorite/favorite-rpc/.../FavoriteFactDispatchScheduler.java:146}（构造器）</li>
 * </ul>
 * ⇒ ⇒ <b>普通 Bean 装配会被构造器抢跑</b>（bean 创建顺序不确定）⇒ 全局永远装不上。
 * ⇒ ⇒ {@code BeanFactoryPostProcessor} 在<b>所有单例创建之前</b>执行 ⇒ 唯一安全的位置。
 *
 * <h2>🔴🔴 为什么用 {@code AutoConfiguration.imports}，而不是组件扫描</h2>
 * <p>本仓的共享层靠 {@code scanBasePackages} 扫 {@code com.rag.common} 装配
 * （见 {@code GoStyleProtectionConfiguration} 的类注释），但
 * ⚠️ <b>rpc 模块刻意不扫 {@code com.rag.common}</b>
 * （因为保护层四个 Filter 依赖 servlet API）⇒
 * 走组件扫描的话，<b>rpc 侧的 trace 永远装不上</b>
 * ⇒ 而 Go 侧<b>api 与 rpc 都装</b>（每个服务自己的 {@code etc/*.yaml} 都有 {@code Telemetry}）
 * ⇒ ⇒ 必须走 Spring Boot 的<b>自动配置清单</b>（{@code META-INF/spring/…AutoConfiguration.imports}）：
 * 它<b>不依赖组件扫描</b> ⇒ 只要进程依赖了 {@code service/common} 且是 Spring Boot 应用，就自动生效
 * ⇒ ⇒ <b>零域模块改动</b>（favorite 域代码同样零改动）。
 *
 * <p>⚠️ 本仓此前<b>没有</b>使用自动配置清单机制（一切靠组件扫描）⇒
 * 这是本仓<b>第一处</b>自动配置 ⇒ 理由与影响面见上面两段（不是为了「更地道」，
 * 而是<b>组件扫描覆盖不到 rpc 模块</b>这一硬约束）。
 *
 * <h2>装配失败<b>不阻断启动</b></h2>
 * <p>照 Go 的 {@code core/trace/agent.go:49-51}（{@code startAgent} 失败只 {@code logx.Error}）
 * ⇒ 见 {@link GoStyleOtelAgent#install}。
 */
@AutoConfiguration
public class GoStyleOtelAutoConfiguration {

    /**
     * 🔴 <b>必须是 {@code static}</b>：静态 {@code @Bean} 方法不需要先实例化配置类本身
     * ⇒ 能在最早期被取到 ⇒ 不会与「构造器里调 {@code GlobalOpenTelemetry.get()}」抢跑。
     */
    @Bean
    static BeanFactoryPostProcessor goStyleOtelGlobalRegistrar() {
        return new OtelGlobalRegistrar();
    }

    /**
     * 真正的装配器：<b>同时</b>实现 {@link BeanFactoryPostProcessor} 与 {@link EnvironmentAware}。
     *
     * <p>⚠️ 为什么 {@code EnvironmentAware} 而不是从 {@code ConfigurableListableBeanFactory}
     * 上取 {@code getEnvironment()}：<b>后者不存在</b>（{@code getEnvironment()} 在
     * {@code ConfigurableApplicationContext} 上，不在 BeanFactory 上）⇒ 编译期就会暴露。
     */
    static final class OtelGlobalRegistrar implements BeanFactoryPostProcessor, EnvironmentAware {

        private Environment environment;

        @Override
        public void setEnvironment(Environment environment) {
            this.environment = environment;
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
            GoStyleOtelAgent.install(GoStyleOtelConfig.from(environment));
        }
    }
}
