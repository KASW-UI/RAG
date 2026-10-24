package com.rag.follow.follow.api;

import com.rag.follow.follow.api.config.FollowApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * followcenter HTTP gateway 入口。
 *
 * <p>对应 Go: {@code service/follow/api/followcenter.go} 的 {@code func main()}。
 *
 * <p><b>Go 侧 main() 的 8 件事与 Spring 的对应关系</b>：
 * <table border="1">
 *   <caption>Go main() → Spring</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr>
 *     <td>{@code metrics.InitMetrics()}</td>
 *     <td>无需显式注册：Micrometer 由 {@code MeterRegistry} 自动注册，
 *         本模块三个 metric 在 {@code FollowApiMetrics} 里声明</td>
 *   </tr>
 *   <tr>
 *     <td>{@code conf.MustLoad(*configFile, &c)}</td>
 *     <td>{@code @ConfigurationProperties(prefix = "follow-api")}（见 {@link FollowApiProperties}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code rest.MustNewServer(c.RestConf)}</td>
 *     <td>{@code spring-boot-starter-web} + {@code server.port}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code redis.MustNewRedis(c.BizRedis)}</td>
 *     <td>{@code spring-boot-starter-data-redis} + {@code spring.data.redis.*}
 *         （只在 {@code FollowBlacklistInterceptor} 里做一次 {@code hasKey}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code observability.DisableNativeRestTimeout(&c.RestConf)}
 *         + {@code server.Use(observability.NewHTTPMiddleware(...))}</td>
 *     <td>{@code observability.http-timeout: 3s} + {@code HttpMiddlewareFilter}
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
 *     <td>{@code zrpc.MustNewClient(c.FollowRpc)}</td>
 *     <td>{@code @GrpcClient("follow-rpc")}（见 {@code FollowRpcClient}）</td>
 *   </tr>
 * </table>
 *
 * <p><b>⚠️ 本域的 401 有<b>两种 body 形状</b>，不可收敛</b>：
 * Go 侧 {@code rest/handler/authhandler.go:106} 的验签失败只 {@code WriteHeader(401)}（空 body），
 * 而 {@code internal/middleware/checkblacklistmiddleware.go:42-48} 的黑名单分支
 * 写 {@code {"code":5004,"msg":"unauthorized"}}。
 * 两段对应 {@code FollowJwtAuthInterceptor} 与 {@code FollowBlacklistInterceptor}，
 * 详见那两个类的注释。
 *
 * <p><b>scanBasePackages 刻意不含 {@code com.rag.users}</b>：本模块只在 pom 里依赖
 * users-common 的静态工具类（JwtUtil / BearerTokenExtractor / GoStyleCoercion /
 * GoStyleStringInt64 / GoStyleRequired / GoStyleRequest），不需要它的任何 Bean；
 * 若扫进去会把 {@code SchemaInitializer} 这类 users 域的 Bean 拖进 follow-api 上下文。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.follow.follow.api",
        "com.rag.common"
})
@EnableConfigurationProperties(FollowApiProperties.class)
@EnableDiscoveryClient
public class FollowApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FollowApiApplication.class, args);
    }
}