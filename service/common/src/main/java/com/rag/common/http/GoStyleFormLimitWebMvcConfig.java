package com.rag.common.http;

import com.rag.common.web.ConditionalOnApiSide;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 把 {@link GoStyleFormValueLimitInterceptor} 注册到 Spring MVC 的拦截器链上。
 *
 * <h2>🔴 为什么 {@code order} 取 {@link Ordered#HIGHEST_PRECEDENCE}</h2>
 * <p>各域自己的鉴权 / 黑名单拦截器（{@code JwtAuthInterceptor} 等）都是
 * {@code registry.addInterceptor(x)} <b>不指定 order</b> ⇒ 默认 {@code 0}。
 * <br>⇒ {@link InterceptorRegistry} 按 {@link org.springframework.web.servlet.config.annotation.InterceptorRegistration#order(int)}
 * <b>升序</b>排 ⇒ 本拦截器取 {@code HIGHEST_PRECEDENCE} ⇒ <b>必然先于</b>鉴权拦截器执行。
 *
 * <p>⚠️ 这与 Go 的顺序<b>相反</b>（Go：{@code rest.WithJwt} 是中间件，先于 handler 内的
 * {@code httpx.Parse}）⇒ 「既无合法 token、又带 2049 个参数」的请求：Go 返 401、RAG 返 400/5001。
 * <br>⚠️ 这是<b>本条修之前就存在</b>的差异（article 的同名 filter 一直跑在鉴权之前），
 * 本轮<b>刻意不改</b>（避免扩大 scope）⇒ 记为有界残留。
 *
 * <h2>🔴 为什么用 {@code WebMvcConfigurer} 而不是 {@code FilterRegistrationBean}</h2>
 * <p>因为 {@link GoStyleFormLimitMode#ENVELOPE} 模式需要<b>把异常抛给
 * {@code @RestControllerAdvice}</b> ⇒ 只有 DispatcherServlet <b>内部</b>的拦截器才做得到
 * （servlet Filter 在 DispatcherServlet 之外 ⇒ 抛出的异常到不了 advice）。
 * <br>⇒ 副作用（也是优点）：<b>未匹配到路由的路径（404 / 405）不会走本闸门</b>
 * ⇒ 与 Go 一致（Go 的 404 分支在 {@code patrouter.go:73} 直接返回，<b>不解析 form</b>）。
 * <p>⚠️ <b>2026-10-30 · G4 lane：本类属 api 侧 rest 中间件 ⇒ 已挂 {@link com.rag.common.web.ConditionalOnApiSide}</b>
 * ⇒ ⇒ 此前「rpc 域不注册本类」的依据是<b>域侧不扫 {@code com.rag.common} 这道约定</b>（不是本类的条件）
 * ⇒ ⇒ 现由共享层自己兜住 ⇒ ⇒ Go 出处与语义等价论证见该注解。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnApiSide
public class GoStyleFormLimitWebMvcConfig implements WebMvcConfigurer {

    private final GoStyleFormValueLimitInterceptor interceptor;

    public GoStyleFormLimitWebMvcConfig(GoStyleFormValueLimitInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE);
    }
}
