package com.rag.comment.comment.api;

import com.rag.common.spring.HttpMiddlewareFilter;
import com.rag.comment.comment.api.config.CommentApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * comment-center HTTP gateway 入口。
 *
 * <p>对应 Go：{@code service/comment/api/commentservice.go} 的 {@code func main()}。
 *
 * <p><b>Go 侧 main() 的逐条对应</b>：
 * <table border="1">
 *   <caption>Go main() → Spring</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr>
 *     <td>{@code conf.MustLoad(*configFile, &c)}</td>
 *     <td>{@code @ConfigurationProperties(prefix = "comment-api")}（见 {@link CommentApiProperties}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code logger.Init(c.Name)}</td>
 *     <td>{@code LoggerFactoryBean}（由 {@code com.rag.common} 扫描注册）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code rest.MustNewServer(c.RestConf)}</td>
 *     <td>{@code spring-boot-starter-web} + {@code server.port}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code observability.DisableNativeRestTimeout(&c.RestConf)} +
 *         {@code server.Use(observability.NewHTTPMiddleware(...))}</td>
 *     <td>{@code observability.http-timeout} + {@link HttpMiddlewareFilter}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code httpx.SetOkHandler(...)}（{@code commentservice.go:39-45}）</td>
 *     <td>{@code CommentGlobalResponseAdvice}（{@code @ResponseBodyAdvice}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code httpx.SetErrorHandler(...)}（{@code commentservice.go:47-60}）</td>
 *     <td>{@code CommentGlobalExceptionHandler}（{@code @RestControllerAdvice}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code svc.NewServiceContext(c)} → {@code zrpc.MustNewClient(c.CommentRpc)}</td>
 *     <td>{@code @GrpcClient("comment-rpc")}（见 {@code CommentRpcClient}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code handler.RegisterHandlers(server, ctx)}</td>
 *     <td>{@code CommentController} + {@code WebMvcConfig} 里的拦截器注册</td>
 *   </tr>
 * </table>
 *
 * <p><b>⚠️ 本服务的对外契约：除鉴权失败外，HTTP 状态码恒为 200</b>，
 * 成败与错误详情全在 body 的 {@code code}/{@code msg} 里。
 * 这是 Go {@code commentservice.go:55} 的 {@code return http.StatusOK, &response.Response{...}} 决定的，
 * 与 Go 侧 user-api（未注册 error handler ⇒ 400 + text/plain）<b>不同</b>。
 *
 * <p><b>鉴权失败是唯一的例外，且 body 为空</b>：Go
 * {@code rest/handler/authhandler.go:106 unauthorized()} 只调
 * {@code writer.WriteHeader(http.StatusUnauthorized)}，不写任何字节。
 *
 * <p><b>scanBasePackages 刻意不含 {@code com.rag.users}</b>：本模块只在 pom 里依赖
 * users-common 的 3 个<b>静态工具类</b>（JwtUtil / BearerTokenExtractor / GoStyleCoercion），
 * 不需要它的任何 Bean；扫进去会把 {@code SchemaInitializer} 这类 users 域 Bean
 * 拖进 comment-api 的上下文。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.comment.comment.api",
        "com.rag.common"
})
@EnableConfigurationProperties(CommentApiProperties.class)
@EnableDiscoveryClient
public class CommentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentApiApplication.class, args);
    }
}