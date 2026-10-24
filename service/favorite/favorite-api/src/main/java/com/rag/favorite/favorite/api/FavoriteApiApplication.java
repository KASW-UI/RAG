package com.rag.favorite.favorite.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * favorite-api HTTP gateway 入口。
 *
 * <p>对应 Go: {@code service/favorite/api/favorite.go:20} 的 {@code func main()}。
 *
 * <p><b>Spring 化拆分</b>：
 * <ul>
 *   <li>{@code serviceName} 由 {@code spring.application.name}（= favorite-api）自动绑定到
 *       {@code LoggerFactoryBean}（Go 侧是 {@code logger.Init(c.Name)}）；</li>
 *   <li>HTTP observability filter 由 {@code HttpMiddlewareFilter} Bean 自动注册到 Servlet 容器
 *       （Go 侧是 {@code server.Use(observability.NewHTTPMiddleware(...))}）；</li>
 *   <li>{@code @EnableDiscoveryClient} 启用 Nacos 自动发现 favorite-rpc
 *       （{@code grpc.client.favorite-rpc.address=discovery:///favorite-rpc}，
 *       对应 Go 侧 {@code FavoriteRpc.Etcd.Key: favorite.rpc}）；</li>
 *   <li>grpc-client-spring-boot-starter 通过 {@code @GrpcClient} 自动注入
 *       {@code FavoriteServiceBlockingStub}（Go 侧是
 *       {@code favoriteservice.NewFavoriteService(zrpc.MustNewClient(c.FavoriteRpc))}）。</li>
 * </ul>
 *
 * <p><b>scanBasePackages 刻意不含 {@code com.rag.users.*}</b>：favorite-api 只用到
 * {@code users-common} 里的静态工具类（{@code JwtUtil} / {@code BearerTokenExtractor} /
 * {@code GoStyle*}），把 users 域的 Bean（如 {@code SchemaInitializer}）拖进本进程的
 * Spring 上下文是多余的副作用。
 *
 * <p><b>包含 {@code com.rag.common}</b>：{@code Response} envelope、{@code BusinessException} /
 * {@code AuthenticationException}、{@code GoStyleRouterErrorResponses}、
 * {@code HttpMiddlewareFilter}、{@code LoggerFactoryBean} 都在该包里。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.favorite.favorite.api",
        "com.rag.common"
})
@ConfigurationPropertiesScan("com.rag.favorite.favorite.api.config")
@EnableDiscoveryClient
public class FavoriteApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FavoriteApiApplication.class, args);
    }
}
