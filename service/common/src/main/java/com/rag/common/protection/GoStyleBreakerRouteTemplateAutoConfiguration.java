package com.rag.common.protection;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 注册 {@link GoStyleRouteTemplateResolver} —— 让共享层的 HTTP 熔断器
 * 能按 <b>Go 的隔离单位（每条路由一个 breaker）</b>分桶。
 *
 * <p><b>为什么是自动配置而不是 {@code @Component}</b>：
 * 本类引用 {@link RequestMappingHandlerMapping}（Spring MVC）⇒ 而<b>10 个 rpc 组件没有 spring-webmvc</b>
 * ⇒ 若靠组件扫描，在那些进程里会<b>硬失败</b>（{@code NoClassDefFoundError}）
 * ⇒ 必须 {@code @ConditionalOnClass} 保护（AGENTS.md §3.3：注解存在 ≠ 机制生效，
 * 且 Boot 4 拆模块后硬失败与静默失效都要先实测）。
 *
 * <p><b>为什么用 {@code AutoConfiguration.imports} 而不是组件扫描</b>：
 * 共享层里两类机制的可达性不同 —— 保护链的四个 Filter 是<b>被 api 组件扫到才生效</b>
 * （rpc 组件刻意不扫 {@code com.rag.common}）；而<b>路由模板解析</b>必须对
 * <b>所有 servlet 进程</b>可用 ⇒ 走自动配置清单 ⇒ 与各组件的 {@code scanBasePackages} 解耦。
 *
 * <p><b>Go 出处</b>：{@code rest/engine.go:131-132}（每路由建一个 breaker）·
 * {@code rest/handler/breakerhandler.go:15,19}（键 = {@code METHOD://route.Path} 注册期模板）·
 * {@code rest/engine.go:244-268}（404 链里<b>没有</b> breaker）。
 */
@AutoConfiguration
@ConditionalOnClass(RequestMappingHandlerMapping.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GoStyleBreakerRouteTemplateAutoConfiguration {

    /**
     * 注册路由模板解析器。
     *
     * <p>⚠️ <b>刻意用惰性 supplier</b>：Filter 在 Tomcat 启动时构造，
     * 那时 DispatcherServlet 还没初始化 ⇒ 此刻实例化 {@code RequestMappingHandlerMapping}
     * 属于「提前触发」⇒ 惰性化后推迟到第一次请求。
     *
     * <p>⚠️ <b>为什么有 {@code @ConditionalOnMissingBean}</b>：本类位于 {@code com.rag.common.protection}
     * ⇒ <b>10 个 api 组件的 {@code scanBasePackages} 含 {@code com.rag.common}</b>
     * ⇒ 本类会<b>既被组件扫描、又被自动配置清单</b>各注册一次
     * ⇒ 而两者的 {@code @Bean} 方法名<b>相同</b> ⇒ 不同名冲突会被 Spring 判成
     * <b>「重复定义」⇒ 启动直接失败</b>（Boot 默认不允许 bean 覆盖）
     * ⇒ ⇒ 必须去重。
     * <p>与 {@code GoStylePathNormalizingAutoConfiguration} 用的是<b>同一套办法</b>。
     */
    @Bean
    @ConditionalOnMissingBean
    public GoStyleRouteTemplateResolver goStyleRouteTemplateResolver(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings) {
        return new GoStyleRouteTemplateResolver(
                () -> handlerMappings.orderedStream().toList());
    }
}
