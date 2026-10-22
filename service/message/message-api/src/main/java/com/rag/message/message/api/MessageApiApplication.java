package com.rag.message.message.api;

import com.rag.common.spring.HttpMiddlewareFilter;
import com.rag.message.message.api.config.MessageApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * message-center HTTP gateway 入口。
 *
 * <p>对应 Go: {@code service/message/api/messagecenter.go} 的 {@code func main()}。
 *
 * <p><b>Go 侧 main() 的 7 件事与 Spring 的对应关系</b>：
 * <table border="1">
 *   <caption>Go main() → Spring</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr>
 *     <td>{@code metrics.InitMetrics()}</td>
 *     <td>无需显式注册：Micrometer 由 {@code MeterRegistry} 自动注册，
 *         本模块三个 metric 在 {@code MessageApiMetrics} 里声明</td>
 *   </tr>
 *   <tr>
 *     <td>{@code conf.MustLoad(*configFile, &c)}</td>
 *     <td>{@code @ConfigurationProperties(prefix = "message-api")}（见 {@link MessageApiProperties}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code rest.MustNewServer(c.RestConf)}</td>
 *     <td>{@code spring-boot-starter-web} + {@code server.port}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code observability.DisableNativeRestTimeout(&c.RestConf)}
 *         + {@code server.Use(observability.NewHTTPMiddleware(...))}</td>
 *     <td>{@code observability.http-timeout: 3s} + {@link HttpMiddlewareFilter}
 *         （由 {@code com.rag.common} 扫描自动注册，超时只有一个执行者）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code httpx.SetOkHandler(...)}</td>
 *     <td>{@code GlobalResponseAdvice}（{@code @ResponseBodyAdvice}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code httpx.SetErrorHandler(...)}</td>
 *     <td>{@code GlobalExceptionHandler}（{@code @RestControllerAdvice}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code zrpc.MustNewClient(c.MessageRpc)}</td>
 *     <td>{@code @GrpcClient("message-rpc")}（见 {@code MessageRpcClient}）</td>
 *   </tr>
 * </table>
 *
 * <p><b>⚠️ 鉴权失败是 HTTP 401 + 空 body</b>，与 user-api / admin-api 的「401 + 完整 envelope」
 * <b>不同</b>。Go 侧本服务只有 {@code rest.WithJwt} 一条鉴权路径
 * （{@code rest/handler/authhandler.go:106} {@code unauthorized()} → 只 {@code WriteHeader(401)}，
 * 不写任何 body），不存在 Go 自身两路径不一致的问题，因此没有 N-1 那类需要收敛的分歧。
 * 详见 {@code MessageJwtAuthInterceptor} 与 {@code GlobalExceptionHandler} 的注释。
 *
 * <p><b>scanBasePackages 刻意不含 {@code com.rag.users}</b>：本模块只在 pom 里依赖
 * users-common 的 4 个<b>静态工具类</b>（JwtUtil / BearerTokenExtractor / GoStyleCoercion /
 * GoStyleRequired），不需要它的任何 Bean；若扫进去会把 {@code SchemaInitializer}
 * 这类 users 域的 Bean 拖进 message-api 上下文。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.message.message.api",
        "com.rag.common"
})
/**
 * ⚠️ 这里<b>刻意不再</b>列 {@code GoStyleProtectionProperties}（2026-10-22）。
 * 保护层下沉到 {@code com.rag.common.protection} 后，它的注册随包一起下沉到
 * {@code GoStyleProtectionConfiguration}（扫描 {@code com.rag.common} 即自动注册）。
 * 此前只有本模块写了那一行，导致同样扫 {@code com.rag.common} 的 admin-api
 * 启动即崩（No qualifying bean of type GoStyleProtectionProperties，23 条测试全红）。
 * ⇒ 共享组件的装配义务必须共享，不能靠「每个下游记得加一行」。
 */
@EnableConfigurationProperties(MessageApiProperties.class)
@EnableDiscoveryClient
public class MessageApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApiApplication.class, args);
    }
}