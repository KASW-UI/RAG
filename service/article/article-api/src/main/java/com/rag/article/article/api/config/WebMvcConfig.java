package com.rag.article.article.api.config;

import com.rag.article.article.api.interceptor.JwtAuthInterceptor;
import com.rag.users.common.http.GoStyleCoercion;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 —— 挂鉴权拦截器 + 注册 go-zero 对齐的 Jackson 规则。
 *
 * <h2>拦截器只挂 {@code /v1/**}，真正的判据在拦截器内部</h2>
 * <p>Go 侧三段 {@code AddRoutes} 的鉴权差异（第一段带 {@code rest.WithJwt}，
 * 后两段不带）由 {@code JwtAuthInterceptor} 内部的
 * {@code ProtectedRoute}（method + path）表承担，见该类注释「为什么必须带 method 一起匹配」。
 * 这里只做一件粗筛：把 {@code /v1/**} 交给它，{@code /actuator/**} 等路径不进入鉴权逻辑。
 *
 * <h2>⚠️ <b>不要</b>用 {@code addPathPatterns("/v1/article/**")} 之类的宽前缀</h2>
 * <p>那会把公开的 {@code GET /v1/article/:id}（public 读详情）也纳入鉴权，
 * 凭空要求它带 token —— 而 Go 侧它是明确公开的（第二段 {@code AddRoutes} 无 {@code WithJwt}）。
 * 这类「看名字像同一个资源就一起保护」的直觉，恰好会破坏本组件最关键的一条 L1 契约。
 *
 * <h2>关于 {@link GoStyleCoercion}</h2>
 * <p>把 users-common 的两条解析规则施加到<b>本模块 MVC</b> 的 Jackson 3 mapper 上：
 * <ol>
 *   <li>number / boolean 不能进 String 字段；</li>
 *   <li>float 不能进整数字段。</li>
 * </ol>
 * 对 article 域的实际影响：
 * <ul>
 *   <li>{@code {"title":123}} 在 Go 侧是 {@code type mismatch for field "title"}
 *       （{@code core/mapping/unmarshaler.go:1202}）⇒ 400 text/plain；
 *       规则 1 让 RAG 同样拒绝，最终落到<b>同一个</b> 400 text/plain。</li>
 *   <li>{@code {"status":"2"}} 在 Go 侧同样是 {@code type mismatch}
 *       （proto 侧的 {@code ,string} 只在<b>出参</b>，入参 {@code status} 没有它）；
 *       规则 2 让 RAG 同样拒绝。</li>
 * </ul>
 *
 * <p>⚠️ <b>为什么直接用共享的 {@code GoStyleCoercion}、不像 message 域那样叠加域专属规则</b>：
 * GoStyleCoercion 的第 3 条豁免「String → 整数字段保持接受」是 users 域按真源实测得出的
 * （users 的 uid 带 {@code ,string}）。<b>article 的入参里没有任何带 {@code ,string} 的字段</b>
 * —— {@code types.go} 全文没有一个 {@code ,string} option。
 * ⇒ 那条豁免对 article <b>同样不成立</b>，需要「拒绝 String → int」的域专属规则。
 * 见 {@code ArticleGoStyleCoercion}。
 *
 * <p><b>注册点必须是 builder</b>：Spring Boot 4（Spring Framework 7）用的是 Jackson 3
 * （{@code tools.jackson.*}），coercion 只能在 {@code JsonMapper.Builder} 上配，
 * {@code ObjectMapper} 上没有该 API。扩展点是 {@link JsonMapperBuilderCustomizer}。
 *
 * <p><b>只动 MVC 这一个 mapper</b>：gRPC 用 protobuf marshaller，不走 Jackson。
 *
 * <p><b>CORS 不在这里</b>：Go 的 {@code rest.WithCors()} 包在<b>router 外面</b>
 * （{@code corsRouter.ServeHTTP}），即 404 / 405 也带 CORS 头 ——
 * 那是 Servlet {@code Filter} 层的职责，不是 {@code WebMvcConfigurer} 的
 * {@code addCorsMappings}（后者只对匹配到的 handler 生效）。
 * 实现见 {@code ArticleGoStyleCorsFilter}。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Bean
    public JsonMapperBuilderCustomizer goStyleCoercionCustomizer() {
        return ArticleGoStyleCoercion::applyTo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/v1/**");
    }
}