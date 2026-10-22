package com.rag.message.message.api.config;

import com.rag.message.message.api.interceptor.MessageAdminJwtAuthInterceptor;
import com.rag.message.message.api.interceptor.MessageJwtAuthInterceptor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置 —— 挂两条鉴权路径 + 注册 go-zero 对齐的 Jackson 规则。
 *
 * <p><b>路径注册与 Go 端两段 {@code AddRoutes} 逐条对应</b>：
 * <table border="1">
 *   <caption>拦截器注册</caption>
 *   <tr><th>拦截器</th><th>addPathPatterns</th><th>excludePathPatterns</th></tr>
 *   <tr>
 *     <td>{@link MessageJwtAuthInterceptor}</td>
 *     <td>{@code /message/v1/**}</td>
 *     <td><b>{@code /message/v1/admin/**}</b> —— 必须 exclude，否则两把钥匙都会验一遍</td>
 *   </tr>
 *   <tr>
 *     <td>{@link MessageAdminJwtAuthInterceptor}</td>
 *     <td>{@code /message/v1/admin/**}</td>
 *     <td>—</td>
 *   </tr>
 * </table>
 *
 * <p><b>exclude 是必需的，不是防御性冗余</b>：Go 侧两段路由表各自带一把 key，
 * admin 端点只被 admin key 验签。若 Java 侧 user 拦截器也覆盖 admin 前缀，
 * 拿 admin token 打 admin 端点会先被 user key 拒掉 —— 凭空多出 401。
 *
 * <p><b>注意 Go 侧不存在 public 路径</b>：message api 的 13 个端点<b>全部</b>要求 JWT
 * （两段 {@code AddRoutes} 都带 {@code rest.WithJwt}），所以这里没有 user-api 那种
 * 「public 路径直接放行」的分支。
 *
 * <p><b>关于 {@link GoStyleCoercion}</b>：把 users-common 的两条解析规则施加到<b>本模块 MVC</b>
 * 的 Jackson 3 mapper 上（E-3 根因 R1）。两条规则是
 * 「number/boolean 不能进 String 字段」与「float 不能进整数字段」。
 * 对 message 域的实际影响：{@code content} / {@code title} 这类 String 字段在 Go 侧
 * 收到 {@code {"content":123}} 会报 {@code type mismatch}，规则 1 让 RAG 同样拒绝
 * （最终输出 {code:5001}）；{@code offset} / {@code limit} 收到 {@code 1.5} 会被 Go 的
 * {@code strconv.ParseInt("1.5")} 拒掉，规则 2 让 RAG 同样拒绝。
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

    /** Go 侧 admin 路由组的前缀（{@code rest.WithPrefix("/message/v1/admin")}）。 */
    private static final String ADMIN_PREFIX = "/message/v1/admin/**";

    /** Go 侧 user 路由组的前缀（{@code rest.WithPrefix("/message/v1")}）。 */
    private static final String USER_PREFIX = "/message/v1/**";

    private final MessageJwtAuthInterceptor messageJwtAuthInterceptor;
    private final MessageAdminJwtAuthInterceptor messageAdminJwtAuthInterceptor;

    public WebMvcConfig(MessageJwtAuthInterceptor messageJwtAuthInterceptor,
                        MessageAdminJwtAuthInterceptor messageAdminJwtAuthInterceptor) {
        this.messageJwtAuthInterceptor = messageJwtAuthInterceptor;
        this.messageAdminJwtAuthInterceptor = messageAdminJwtAuthInterceptor;
    }

    @Bean
    public JsonMapperBuilderCustomizer goStyleCoercionCustomizer() {
        // message 域 = users-common 共享规则 + 本模块两条补强（理由见 MessageGoStyleCoercion）
        return MessageGoStyleCoercion::applyTo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(messageJwtAuthInterceptor)
                .addPathPatterns(USER_PREFIX)
                .excludePathPatterns(ADMIN_PREFIX);

        registry.addInterceptor(messageAdminJwtAuthInterceptor)
                .addPathPatterns(ADMIN_PREFIX);
    }
}