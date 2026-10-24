package com.rag.follow.follow.api.config;

import com.rag.follow.follow.api.interceptor.FollowBlacklistInterceptor;
import com.rag.follow.follow.api.interceptor.FollowJwtAuthInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;

/**
 * Spring MVC 配置 —— 挂两条鉴权路径 + 注册 go-zero 对齐的 Jackson 规则。
 *
 * <p><b>拦截器注册顺序 = Go 的 chain 顺序，逐字对照</b>：
 * <pre>
 *   Go 出处：rest/engine.go:101-116 bindRoute
 *     chn := ng.buildChainWithNativeMiddlewares(...)   // native 中间件
 *     chn  = ng.appendAuthHandler(fr, chn, verifier)  // ← rest.WithJwt 插在这里
 *     for _, mw := range ng.middlewares { chn = chn.Append(mw) }  // ← 用户中间件（黑名单）
 *     handle := chn.ThenFunc(route.Handler)
 *
 *   rest/chain/chain.go 的 Then 是 m0(m1(...mN(h)))，索引 0 最外层、先执行
 *   ⇒ 真实顺序：… native … → Authorize(JWT) → CheckBlacklist → handler
 * </pre>
 * Spring 的 {@link InterceptorRegistry#addInterceptor} 同理「先注册先执行」，
 * 所以本类<b>必须先注册 JWT、再注册黑名单</b>。顺序反了会怎样：
 * 一个<b>签名无效但已在黑名单中</b>的 token，Go 是 {@code Authorize} 先失败 → 401 空 body、
 * <b>黑名单从不查询</b>；反过来则会对畸形 token 产生一次多余的 Redis 读，
 * 且最终响应仍是 401 空 body（只是多了一次下游依赖调用，压测下可观测）。
 *
 * <p><b>路径集合</b>：{@code /follow/v1/**} 覆盖 Go 侧全部 8 个端点，
 * <b>没有 exclude、没有 public 路径</b> —— Go 的
 * {@code routes.go:16-64} 是一段 {@code AddRoutes} 同时挂
 * {@code rest.WithJwt} 与 {@code CheckBlacklistMiddleware}。
 *
 * <p><b>关于 {@link FollowGoStyleCoercion}</b>：把 users-common 的共享规则 +
 * follow 域那条补强施加到<b>本模块 MVC</b> 的 Jackson 3 mapper 上。
 * 对 follow 域的实际影响：{@code offset} / {@code limit} 这类<b>无 {@code ,string}</b> 的
 * 整数字段收到 JSON 字符串 {@code {"offset":"10"}} 时，Go 报 {@code type mismatch}
 * （最终输出 {code:5001}），本规则让 RAG 同样拒绝。
 *
 * <p><b>注册点必须是 builder</b>：Spring Boot 4（Spring Framework 7）用的是 Jackson 3
 * （{@code tools.jackson.*}），coercion 只能在 {@code JsonMapper.Builder} 上配，
 * {@code ObjectMapper} 上没有该 API；Boot 4 的
 * {@code Jackson2ObjectMapperBuilderCustomizer} 也已随自动配置模块化消失。
 * 扩展点因此是 {@link JsonMapperBuilderCustomizer}。
 *
 * <p><b>只动 MVC 这一个 mapper</b>：gRPC 用 protobuf marshaller，不走 Jackson。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Go 侧唯一一段路由组的前缀（{@code rest.WithPrefix("/follow/v1")}）。 */
    private static final String FOLLOW_PREFIX = "/follow/v1/**";

    private final FollowJwtAuthInterceptor followJwtAuthInterceptor;
    private final FollowBlacklistInterceptor followBlacklistInterceptor;

    public WebMvcConfig(FollowJwtAuthInterceptor followJwtAuthInterceptor,
                        FollowBlacklistInterceptor followBlacklistInterceptor) {
        this.followJwtAuthInterceptor = followJwtAuthInterceptor;
        this.followBlacklistInterceptor = followBlacklistInterceptor;
    }

    @Bean
    public JsonMapperBuilderCustomizer goStyleCoercionCustomizer() {
        // follow 域 = users-common 共享规则 + 本模块一条补强（理由见 FollowGoStyleCoercion）
        return FollowGoStyleCoercion::applyTo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ⚠️ 顺序即语义：JWT 在外、黑名单在内（见类注释的 Go chain 逐行对照）。
        registry.addInterceptor(followJwtAuthInterceptor)
                .addPathPatterns(FOLLOW_PREFIX);

        registry.addInterceptor(followBlacklistInterceptor)
                .addPathPatterns(FOLLOW_PREFIX);
    }
}