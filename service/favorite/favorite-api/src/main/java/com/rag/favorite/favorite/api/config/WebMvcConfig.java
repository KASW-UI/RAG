package com.rag.favorite.favorite.api.config;

import com.rag.favorite.favorite.api.interceptor.FavoriteJwtAuthInterceptor;
import com.rag.users.common.http.GoStyleCoercion;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.accept.FixedContentNegotiationStrategy;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 —— 挂 JWT + 黑名单鉴权，并注册 go-zero 对齐的 Jackson 3 规则。
 *
 * <p><b>路径注册与 Go 端逐条对应</b>：
 * <pre>
 *   Go  : server.AddRoutes(
 *           rest.WithMiddlewares([]rest.Middleware{serverCtx.CheckBlacklistMiddleware},
 *                               []rest.Route{ 7 个路由 }),
 *           rest.WithJwt(serverCtx.Config.UserAuth.AccessSecret),
 *           rest.WithPrefix("/favorite/v1"))
 * </pre>
 *
 * <p><b>为什么只有一条拦截器、且没有 excludePathPatterns</b>：
 * Go 侧 favorite api <b>只注册过一段路由表</b>，7/7 全量 {@code rest.WithJwt}，
 * <b>不存在 public 路径</b>（对照 users 域的 user/admin api：那边有 login / create 两条 public 段，
 * 本域一个都没有）。因此这里不需要 exclude —— 加了反而凭空制造分歧。
 *
 * <p><b>⚠️ 一个必须显式说明的表达层差异</b>：Go 的 {@code rest.WithPrefix("/favorite/v1")}
 * 与 {@code /folder/create} 拼接出的是精确路径 {@code /favorite/v1/folder/create}；
 * 而 Spring 的 {@code /favorite/v1/**} 是<b>前缀通配</b>，会覆盖
 * {@code /favorite/v1/ 下任何路径}。因为本服务的 7 个 Controller 端点都挂在
 * {@code @RequestMapping("/favorite/v1")} 下、且全部需要鉴权，两者覆盖集合等价。
 * 真正多出来的只有「未注册的子路径」，那些会由 DispatcherServlet 返回
 * {@code NoResourceFoundException} → 404（见 {@code GlobalExceptionHandler}），
 * 而 Go 的 router 同样不匹配 → 404，两侧一致。
 *
 * <p><b>关于 {@link GoStyleCoercion}</b>：把 go-zero 的解析规则施加到
 * <b>本模块 MVC</b> 的 Jackson 3 mapper 上。对 favorite 域的实际影响：
 * {@code target_id} / {@code target_type} / {@code name} 这类 String 字段收到
 * {@code {"target_id":123}} 两侧都拒（Go 是 {@code type mismatch for field "target_id"}）；
 * 而 {@code folderId} 这类 {@code ,string} int64 字段由
 * {@link com.rag.users.common.http.GoStyleStringInt64} 自己接管，不受本规则影响。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** Go 侧路由前缀（{@code rest.WithPrefix("/favorite/v1")}）。 */
    private static final String FAVORITE_PREFIX = "/favorite/v1/**";

    private final FavoriteJwtAuthInterceptor favoriteJwtAuthInterceptor;

    public WebMvcConfig(FavoriteJwtAuthInterceptor favoriteJwtAuthInterceptor) {
        this.favoriteJwtAuthInterceptor = favoriteJwtAuthInterceptor;
    }

    @Bean
    public JsonMapperBuilderCustomizer goStyleCoercionCustomizer() {
        return GoStyleCoercion::applyTo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(favoriteJwtAuthInterceptor)
                .addPathPatterns(FAVORITE_PREFIX);
    }

    /**
     * <b>关掉响应侧的内容协商</b> —— 对齐 Go 的 {@code httpx.OkJsonCtx}。
     *
     * <p><b>Go 出处</b>：{@code rest/httpx/responses.go:175-183} 的 {@code doWriteJson}
     * <pre>
     *   bs, err := jsonx.Marshal(v)
     *   w.Header().Set(ContentType, header.ContentTypeJson)   // ← 无条件写 application/json
     *   w.WriteHeader(code)
     *   w.Write(bs)
     * </pre>
     * ⇒ <b>go-zero 根本不做内容协商</b>：请求头里 {@code Accept: text/plain}、
     * {@code Accept: application/xml}、{@code Accept: *} 都会被<b>无视</b>，
     * 响应一律是 {@code HTTP 200 + Content-Type: application/json + JSON envelope}。
     *
     * <p><b>⚠️ 不关掉的后果（实测过）</b>：Spring 默认按 {@code Accept} 选转换器，
     * {@code Accept: text/plain} 时找不到能写出该 DTO 的转换器 ⇒ 抛
     * {@code HttpMediaTypeNotAcceptableException} ⇒ 落到兜底分支 ⇒ <b>HTTP 500</b>。
     * 而 Go 在同样的请求下是 <b>200 + 正常 envelope</b>。
     * ⇒ 这是<b>可观察的 L1 差异</b>（状态码 500 vs 200，且客户端拿不到业务码）。
     *
     * <p><b>⚠️ {@code ignoreAcceptHeader(true)} 必须同时补一个策略，否则起不来</b>：
     * Spring 7 的 {@code ContentNegotiationManagerFactoryBean.build()}（spring-web 7.0.9:241-265）
     * <pre>
     *   if (!this.ignoreAcceptHeader) { strategies.add(new HeaderContentNegotiationStrategy()); }
     *   if (this.defaultNegotiationStrategy != null) { strategies.add(this.defaultNegotiationStrategy); }
     *   ...
     *   new ContentNegotiationManager(strategies)   // ← 策略为空时抛
     *                                             //   "At least one ContentNegotiationStrategy is expected"
     * </pre>
     * ⇒ 只写 {@code ignoreAcceptHeader(true)} 会让策略列表<b>变成空</b>，
     * 容器直接起不来（{@code mvcContentNegotiationManager} Bean 创建失败）。
     * 故这里同时给一个 {@link FixedContentNegotiationStrategy}，语义上正好等价于 Go 的
     * 「无条件写 application/json」。
     *
     * <p><b>只关响应侧</b>：{@code ignoreAcceptHeader} 只影响「写什么 Content-Type」，
     * <b>不影响请求侧的 {@code Content-Type}</b> ——
     * 后者仍由 {@code @RequestBody} 按 Go 的 {@code withJsonBody} 规则判定
     * （非 JSON ⇒ 5003，见 {@code GlobalExceptionHandler#handleMediaTypeNotSupported}）。
     */
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.ignoreAcceptHeader(true);
        configurer.defaultContentTypeStrategy(
                new FixedContentNegotiationStrategy(MediaType.APPLICATION_JSON));
    }
}
