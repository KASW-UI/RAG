package com.rag.article.article.api;

import com.rag.article.article.api.config.ArticleApiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * article-api HTTP gateway 入口 —— 对应 Go {@code service/article/api/article.go} 的 {@code func main()}。
 *
 * <h2>Go {@code main()} 的 7 件事与 Spring 的对应关系</h2>
 * <table border="1">
 *   <caption>article.go 的 main() → Spring</caption>
 *   <tr><th>Go（{@code article.go}）</th><th>Java</th></tr>
 *   <tr>
 *     <td>（<b>无</b> {@code metrics.InitMetrics()}）</td>
 *     <td>本模块<b>没有</b>自己的 Metrics Bean —— Go 侧 {@code service/article/api}
 *         全目录<b>无 metrics 包</b>（实测）。HTTP 指标由
 *         {@code HttpMiddlewareFilter}（{@code com.rag.common}）以 OTel/Micrometer 形式产出。</td>
 *   </tr>
 *   <tr>
 *     <td>{@code conf.MustLoad(*configFile, &amp;c)}</td>
 *     <td>{@code @ConfigurationProperties(prefix = "article-api")}（见 {@link ArticleApiProperties}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code logx.MustSetup(c.Log)} + {@code logger.Init(c.Name)}</td>
 *     <td>logback + {@code com.rag.common.spring.LoggerFactoryBean}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code observability.DisableNativeRestTimeout(&amp;c.RestConf)}</td>
 *     <td>无需动作：Go 侧是为了关掉 go-zero 原生 TimeoutHandler 以便超时只有一个执行者；
 *         Spring 侧本来就没有第二层原生超时。预算由
 *         {@code observability.http-timeout} 单点表达。</td>
 *   </tr>
 *   <tr>
 *     <td>{@code rest.MustNewServer(c.RestConf, rest.WithCors())}</td>
 *     <td>{@code spring-boot-starter-web} + {@code server.port}
 *         + {@code ArticleGoStyleCorsFilter}（CORS 必须在 <b>Filter</b> 层，
 *         因为 Go 的 {@code corsRouter} 包在 router 外面，404/405 也带 CORS 头）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code server.Use(observability.NewHTTPMiddleware(c.Name, httpTimeout, slowThreshold))}</td>
 *     <td>{@code com.rag.common.spring.HttpMiddlewareFilter}（扫描 {@code com.rag.common} 即注册）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code handler.RegisterHandlers(server, ctx)}</td>
 *     <td>{@code ArticleController} + {@code JwtAuthInterceptor}（见 {@code WebMvcConfig}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code svc.NewServiceContext(c)}</td>
 *     <td>构造器注入（{@code ArticleRpcClient} / {@code UserRpcClient}）</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ 本模块<b>刻意没有</b> {@code httpx.SetOkHandler} / {@code SetErrorHandler} 的等价物</h2>
 * <p>Go {@code article.go} 全文 42 行，<b>没有</b>这两句（message / like / hot 都有）。
 * 后果是本模块的<b>响应形状与那三个域不同</b>：
 * <ul>
 *   <li>6 个业务端点：<b>成功</b>走 {@code GlobalResponseAdvice} 包成
 *       {@code {code:200,msg:"OK",data:…}}（Go 侧是 handler 手写的同一段）；</li>
 *   <li>解析失败 / 上传失败：<b>400 + text/plain + nosniff</b>
 *       （而不是 200 + 信封）；</li>
 *   <li>{@code POST /v1/upload}：<b>裸</b> {@code {"image_url":…}}，不走信封。</li>
 * </ul>
 * 详见 {@code GlobalExceptionHandler} 的类注释与 {@code ArticleUploadService}。
 * ⚠️ 这是 AGENTS.md §3.1 的直接应用点：<b>不能照抄 message-api 的 advice 层</b>。
 *
 * <h2>scanBasePackages 刻意<b>不含</b> {@code com.rag.users}</h2>
 * <p>本模块只在 pom 里依赖 users-common 的几个<b>静态工具类</b>
 * （{@code JwtUtil} / {@code BearerTokenExtractor} / {@code GoStyleCoercion} /
 * {@code GoStyleRequired} / {@code GoStyleRequest}），不需要它的任何 Bean；
 * 若扫进去会把 {@code SchemaInitializer} 这类 users 域的 Bean 拖进上下文。
 *
 * <h2>⚠️ 必须扫 {@code com.rag.common}</h2>
 * <p>保护层四道闸门、观测中间件、{@code GoStyleJsonBodyConverter}（复现 go-zero 的
 * {@code withJsonBody} 语义）、{@code GoStyleRouterErrorResponses}（404/405 字节形状）
 * 全在这个包里。漏掉它 ⇒ 「保护层静默消失」与「非 JSON body 从 200 变 415」两种漂移，
 * 而且都<b>不报错</b>。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.article.article.api",
        "com.rag.common"
})
@EnableConfigurationProperties(ArticleApiProperties.class)
@EnableDiscoveryClient
public class ArticleApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArticleApiApplication.class, args);
    }
}