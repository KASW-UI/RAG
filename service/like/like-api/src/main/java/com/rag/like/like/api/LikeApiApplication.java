package com.rag.like.like.api;

import com.rag.like.like.api.config.LikeApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * like-center HTTP gateway 入口 —— 对应 Go {@code service/like/api/likecenter.go} 的 {@code func main()}。
 *
 * <p><b>Go main() 的 6 件事与 Spring 的对应关系</b>：
 * <table border="1">
 *   <caption>likecenter.go → Spring</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr>
 *     <td>{@code logger.Init(c.Name)}</td>
 *     <td>无需显式初始化：{@code com.rag.common} 的 {@code LoggerFactoryBean} 负责，
 *         本模块只调 {@code logger.LogBusinessErr(...)}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code observability.DisableNativeRestTimeout(&c.RestConf)}
 *         + {@code server.Use(observability.NewHTTPMiddleware(...))}</td>
 *     <td>{@link com.rag.common.spring.HttpMiddlewareFilter}（由 {@code com.rag.common} 扫描自动注册）。
 *         <br>🔴 <b>2026-10-23 修正错误断言（DRIFT-LIKEAPI-018）</b>：原文此处写的是
 *         「⚠️ <b>本服务没有配置超时</b>：Go 侧 {@code etc/likecenter.yaml} 的 RestConf
 *         <b>没有 {@code Timeout} 字段</b> ⇒ {@code TimeoutFromMillis(0) == 0}
 *         ⇒ 走 {@code timeout <= 0} 的「不设超时」分支；Java 侧因此<b>刻意不配</b>
 *         {@code observability.http-timeout}」—— <b>整条推理都不成立</b>：
 *         <ol>
 *           <li>Go 的 {@code RestConf.Timeout} 由 <b>struct tag</b> 提供 ——
 *               {@code go-zero@v1.10.2/rest/config.go:55}
 *               {@code Timeout int64 `json:",default=3000"`}
 *               ⇒ <b>yaml 写不写都是 3000ms</b>；
 *               “yaml 里没这个字段” <b>不等于</b> “值是 0”（AGENTS.md §3.3.1 同型）。</li>
 *           <li>Java 侧<b>早已显式配了</b> {@code application.yml:27-29}
 *               {@code observability.http-timeout: 3s}，
 *               原文所说的“刻意不配”状态<b>早已不存在</b>。</li>
 *         </ol>
 *         ⇒ 现值 3s 与 Go 的 3000ms <b>同值</b>，属显式写出而非“凭空多出来的出口”。
 *         ⚠️ <b>不要把这一行当冗余配置删掉</b> —— 那正是 DRIFT-LIKEAPI-001 的成因。</td>
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
 *     <td>{@code svc.NewServiceContext(c)}
 *         + {@code likeservice.NewLikeService(zrpc.MustNewClient(c.LikeRpc))}</td>
 *     <td>{@code LikeRpcClient}（{@code @GrpcClient("like-rpc")}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code handler.RegisterHandlers(server, ctx)}<br>
 *         {@code rest.WithJwt(...)} + {@code rest.WithPrefix("/like/v1")}</td>
 *     <td>{@code LikeController}（{@code @RequestMapping("/like/v1")}）
 *         + {@code LikeJwtAuthInterceptor}（由 {@code WebMvcConfig} 挂 {@code /like/v1/**}）</td>
 *   </tr>
 * </table>
 *
 * <p><b>⚠️ 与 message-api 同样重要的一条：鉴权失败是 HTTP 401 + 空 body。</b>
 * Go 侧本服务只有 {@code rest.WithJwt} 一条鉴权路径
 * （{@code rest/handler/authhandler.go:106 unauthorized()} → 只 {@code WriteHeader(401)}，
 * 不写任何字节），因此 Go 自身不存在「两种 401 body」需要收敛的问题。
 *
 * <p><b>scanBasePackages 刻意不含 {@code com.rag.users}</b>：本模块只在 pom 里依赖
 * users-common 的静态工具类（JwtUtil / BearerTokenExtractor / GoStyleCoercion /
 * GoStyleRequired / GoStyleStringInt64），不需要它的任何 Bean；
 * 若扫进去会把 users 域的 {@code SchemaInitializer} 拖进 like-api 上下文。
 * 同理也不含 {@code com.rag.like.like.rpc} —— like-api 与 like-rpc 是两个进程。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.like.like.api",
        "com.rag.common"
})
@EnableConfigurationProperties(LikeApiProperties.class)
@EnableDiscoveryClient
public class LikeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LikeApiApplication.class, args);
    }
}