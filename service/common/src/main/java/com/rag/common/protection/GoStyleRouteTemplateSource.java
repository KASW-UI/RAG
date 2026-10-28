package com.rag.common.protection;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 「注册期路由模板」解析能力的<b>窄接口</b>。
 *
 * <p>⚠️ <b>为什么不直接让 Filter 依赖 {@code GoStyleRouteTemplateResolver}</b>：
 * 那个实现类引用 {@code org.springframework.web.servlet.HandlerMapping}
 * ⇒ 没有 spring-webmvc 的进程（10 个 rpc 组件）<b>加载不了那个类</b>
 * ⇒ 而 Filter 的依赖注入点一旦写成 {@code ObjectProvider<GoStyleRouteTemplateResolver>}，
 * Spring 为了判定 Bean 类型<b>仍会加载那个类</b> ⇒ {@code NoClassDefFoundError} ⇒ 硬失败。
 * <p>⇒ 抽出本接口后，Filter 只需认识「能解析出路由模板」这件事，
 * 具体实现（要不要 Spring MVC）由自动配置按 {@code @ConditionalOnClass} 决定。
 */
@FunctionalInterface
public interface GoStyleRouteTemplateSource {

    /**
     * 解析这条请求对应的<b>注册期</b>路由模板。
     *
     * @param request 当前请求
     * @return 三态结果；{@link GoStyleRouteResolution.Kind#UNKNOWN}
     *         ⇒ 调用方必须回落旧行为（按 raw URI 分桶、照常进 breaker）
     */
    GoStyleRouteResolution resolve(HttpServletRequest request);
}
