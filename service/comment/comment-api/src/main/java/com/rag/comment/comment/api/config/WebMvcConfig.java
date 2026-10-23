package com.rag.comment.comment.api.config;

import com.rag.comment.comment.api.interceptor.CommentJwtAuthInterceptor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 —— 挂鉴权拦截器 + 注册 go-zero 对齐的 Jackson 规则。
 *
 * <p><b>路径注册与 Go 端路由表逐条对应</b>：
 * <p>Go {@code service/comment/api/internal/handler/routes.go:15-56} 只有<b>一段</b>
 * {@code AddRoutes}，7 条路由全部挂在同一个 prefix + 同一把 key 下：
 * <pre>
 *   server.AddRoutes(
 *       []rest.Route{
 *           {POST, "/comment/create"},          {POST, "/comment/delete"},
 *           {POST, "/comment/like"},             {POST, "/comment/list"},
 *           {POST, "/comment/manage"},           {POST, "/comment/report"},
 *           {POST, "/subject/state"},
 *       },
 *       rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *       rest.WithPrefix("/comment/v1"),
 *   )
 * </pre>
 * 展开后即：
 * <pre>
 *   POST /comment/v1/comment/create   POST /comment/v1/comment/delete
 *   POST /comment/v1/comment/like      POST /comment/v1/comment/list
 *   POST /comment/v1/comment/manage    POST /comment/v1/comment/report
 *   POST /comment/v1/subject/state
 * </pre>
 *
 * <p><b>⇒ 与 message-api 的关键差异：本域只有一把钥匙、一段前缀，没有 admin 分组</b>，
 * 因此只需要<b>一个</b>拦截器，不需要 {@code excludePathPatterns}。
 * （message-api 有两段 {@code AddRoutes}，两把 key，必须互相 exclude。）
 *
 * <p><b>⚠️ 全部 7 个端点都要求 JWT，Go 侧不存在 public 路径</b> ——
 * 连「只读的 {@code /comment/list}」也要 token，因为它是同一段 {@code AddRoutes} 的成员。
 * 写「public 路径直接放行」的分支在这里会是凭空新增的行为。
 *
 * <p><b>关于 {@link CommentGoStyleCoercion}</b>：把 go-zero 解析层的标量强转规则
 * 施加到本模块 MVC 的 Jackson 3 mapper 上，使 {@code {"root_id":"123"}} 之类的输入
 * 在 RAG 侧同样被拒（与 Go 的 {@code type mismatch} 一致），
 * 而不是被 Jackson 静默强转后放行。
 *
 * <p><b>注册点必须是 builder</b>：Spring Boot 4（Spring Framework 7）用的是 Jackson 3
 * （{@code tools.jackson.*}），coercion 只能在 {@code JsonMapper.Builder} 上配，
 * {@code ObjectMapper} 上没有该 API；Boot 4 的
 * {@code Jackson2ObjectMapperBuilderCustomizer} 也已随自动配置模块化消失。
 * 扩展点因此是 {@link JsonMapperBuilderCustomizer}。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Go 侧唯一一段路由组的前缀（{@code rest.WithPrefix("/comment/v1")}）。 */
    private static final String COMMENT_V1_PATTERN = "/comment/v1/**";

    private final CommentJwtAuthInterceptor commentJwtAuthInterceptor;

    public WebMvcConfig(CommentJwtAuthInterceptor commentJwtAuthInterceptor) {
        this.commentJwtAuthInterceptor = commentJwtAuthInterceptor;
    }

    @Bean
    public JsonMapperBuilderCustomizer goStyleCoercionCustomizer() {
        return CommentGoStyleCoercion::applyTo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(commentJwtAuthInterceptor)
                .addPathPatterns(COMMENT_V1_PATTERN);
    }
}