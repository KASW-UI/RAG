package com.rag.hot.hot.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * hot-api（Go 侧 {@code hotcenter}）进程入口 —— 对应 Go {@code service/hot/api/hotcenter.go} 的
 * {@code func main()}。
 *
 * <h2>Go main() 的 6 件事与 Spring 的对应关系</h2>
 * <table border="1">
 *   <caption>hotcenter.go → Spring</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr>
 *     <td>{@code metrics.InitMetrics()}（{@code :33}）</td>
 *     <td>{@code HotApiMetrics}：Micrometer 声明即注册，5 个指标名/标签 1:1</td>
 *   </tr>
 *   <tr>
 *     <td>{@code conf.MustLoad(*configFile, &amp;c)}（{@code :36}）</td>
 *     <td>{@code application.yml} + Spring Boot 原生属性（无专属 Properties 类）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code logx.MustSetup(c.Log)} + {@code logger.Init(c.Name)}（{@code :37-38}）</td>
 *     <td>logback + {@code com.rag.common.spring.LoggerFactoryBean}
 *         （本模块只调 {@code logBusinessErr} / {@code logInfo}）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code observability.DisableNativeRestTimeout(&amp;c.RestConf)}（{@code :40}）</td>
 *     <td>无需「先关再开」两步：Spring 没有第二层原生超时，
 *         预算由 {@code observability.http-timeout: 3s} 单点表达</td>
 *   </tr>
 *   <tr>
 *     <td>{@code server.Use(observability.NewHTTPMiddleware(...))}（{@code :42}）</td>
 *     <td>{@code com.rag.common.spring.HttpMiddlewareFilter}
 *         （<b>必须</b>扫到 {@code com.rag.common} 才注册）</td>
 *   </tr>
 *   <tr>
 *     <td>{@code httpx.SetOkHandler} / {@code SetErrorHandler}（{@code :45-66}）</td>
 *     <td>{@code GlobalResponseAdvice} / {@code GlobalExceptionHandler}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code svc.NewServiceContext(c)}（{@code :68}）</td>
 *     <td>构造器注入：{@code HotRpcClient} / {@code ArticleRpcClient}</td>
 *   </tr>
 *   <tr>
 *     <td>{@code handler.RegisterHandlers(server, ctx)}（{@code :69}）</td>
 *     <td>{@code HotArticleController}（<b>无鉴权拦截器</b>，Go 侧没有）</td>
 *   </tr>
 * </table>
 *
 * <h2>⚠️ {@code scanBasePackages} 必须含 {@code com.rag.common}</h2>
 * 漏掉它会同时失去三样东西，且<b>全部不报错</b>：
 * <ol>
 *   <li>{@code HttpMiddlewareFilter}（HTTP 3s 超时 + 观测 span）；</li>
 *   <li>{@code LoggerFactoryBean}（业务错误日志，直接抛
 *       {@code IllegalStateException: logger not initialized}）；</li>
 *   <li>{@code com.rag.common.protection} 的<b>四道请求保护闸门</b>
 *       （MaxConns / MaxBytes / CPU 降载 / 熔断）—— 这四道在 Go 侧
 *       {@code rest/config.go:12-22,52-56} 的 tag 默认里<b>全是开的</b>，
 *       见 {@code application.yml} 的 {@code rag.protection} 段。</li>
 * </ol>
 *
 * <h2>scanBasePackages 刻意<b>不含</b> {@code com.rag.hot.hot.rpc}</h2>
 * hot-api 与 hot-rpc 是两个进程；扫进去会把 hot-rpc 的
 * {@code HotEventConsumer} / {@code HotRedisSnapshotStore} / {@code SchemaInitializer}
 * 拖进 HTTP 网关的上下文。
 */
@SpringBootApplication(scanBasePackages = {
        "com.rag.hot.hot.api",
        "com.rag.common"
})
@EnableDiscoveryClient
public class HotApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HotApiApplication.class, args);
    }
}