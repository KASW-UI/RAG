package com.rag.task.task.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationExcludeFilter;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * task-api 进程入口 —— 对应 Go {@code service/task/api/task.go} 的 {@code func main()}。
 *
 * <h2>Go main() 的 6 件事与 Spring 的对应关系</h2>
 * <table border="1">
 *   <caption>task.go → Spring</caption>
 *   <tr><th>Go</th><th>Java</th></tr>
 *   <tr><td>{@code conf.MustLoad(*configFile, &c)}（{@code :25}）</td>
 *       <td>{@code application.yml} + Spring Boot 原生属性</td></tr>
 *   <tr><td>{@code observability.DisableNativeRestTimeout(&c.RestConf)}（{@code :27}）</td>
 *       <td>无需「先关再开」两步：Spring MVC 没有第二层原生超时，预算由
 *           {@code observability.http-timeout: 3s} 单点表达</td></tr>
 *   <tr><td>{@code rest.MustNewServer(c.RestConf)}（{@code :28}）</td>
 *       <td>Spring Boot Web 容器 + {@code com.rag.common.protection} 的四道闸门</td></tr>
 *   <tr><td>{@code server.Use(observability.NewHTTPMiddleware(...))}（{@code :29}）</td>
 *       <td>{@code com.rag.common.spring.HttpMiddlewareFilter}（HTTP 3s 超时 + 观测 span）</td></tr>
 *   <tr><td>{@code svc.NewServiceContext(c)}（{@code :32}）</td>
 *       <td>构造器注入：{@code TaskRpcClient} / {@code TaskGetService}</td></tr>
 *   <tr><td>{@code handler.RegisterHandlers(server, ctx)}（{@code :33}）</td>
 *       <td>{@code TaskController}（<b>无鉴权拦截器</b>，Go 侧没有）</td></tr>
 * </table>
 *
 * <h2>⚠️ {@code scanBasePackages} 必须含 {@code com.rag.common}</h2>
 * 漏掉它会同时失去三样东西，且<b>全部不报错</b>（AGENTS.md §3.3 的典型形态）：
 * <ol>
 *   <li>{@code HttpMiddlewareFilter}（HTTP 3s 超时 + 观测 span）；</li>
 *   <li>{@code com.rag.common.protection} 的四道请求保护闸门
 *       （MaxConns / MaxBytes 1 MiB / CPU 降载 / 熔断）—— 这四道在 Go 侧
 *       {@code rest/config.go:12-23,52-56} 的 tag 默认里<b>全是开的</b>；</li>
 *   <li>{@code logger.LoggerFactoryBean}。</li>
 * </ol>
 *
 * <h2>🔴 为什么要 {@code excludeFilters} 排除共享的
 * {@link com.rag.common.http.GoStyleJsonBodyConverterConfig}</h2>
 * 那个共享配置是<b>无条件 {@code @Configuration}</b>，扫到 {@code com.rag.common} 就会生效，
 * 它复刻的是 go-zero {@code httpx.Parse} 的 {@code withJsonBody} 判据 ——
 * <b>Content-Type 不含 {@code application/json} 时丢弃整个请求体</b>。
 * <pre>
 *   POST /taskcenter/v1/task/get
 *     Content-Type: text/plain
 *     body: {"id":"123"}
 *   ⇒ Go（裸 encoding/json）: 200 {"tasks":[...]}
 *   ⇒ 若不排除:  body 被丢弃 → GetTaskReq 零值 → 400 "user_id invalid"      ← 漂移
 * </pre>
 * 本模块改由 {@code config.TaskApiJsonBodyConfig} 接管读侧，语义 = Go 的裸 decoder。
 * <p>⚠️ <b>排除范围只有这一个类</b>，不要图省手把 {@code com.rag.common.http} 整包排掉 ——
 * {@link com.rag.common.http.GoStyleRouterErrorResponses}（404 / 405 的字节形状）就在同一个包里，
 * 排掉它会让未知路径与错误方法的响应形状整个变样。
 *
 * <h2>🔴🔴 必须在 {@code @ComponentScan} 上<b>同时</b>写 {@code basePackages} 与 {@code excludeFilters}</h2>
 * 注解属性<b>不合并</b>：一旦在类上直接写了 {@code @ComponentScan}，
 * {@code @SpringBootApplication} 内建的那个（作为 meta-annotation 的
 * {@code @ComponentScan(excludeFilters = …, scanBasePackages = …)}）就被<b>整体顶掉</b>，
 * 而不是叠加。
 * <p>第一版就踩了这个：{@code @SpringBootApplication(scanBasePackages = {...})}
 * 配一个裸的 {@code @ComponentScan(excludeFilters = {...})}，
 * 结果 {@code scanBasePackages} 悄悄退回「声明类所在的包」
 * ⇒ <b>{@code com.rag.common} 整个没被扫到</b> ⇒ HTTP 超时中间件与四道保护闸门全部不注册，
 * <b>而且不报任何错</b>（AGENTS.md §3.3 的典型形态）。
 * ⇒ 故 {@code basePackages} 与两个内建 filter（{@code TypeExcludeFilter} /
 * {@code AutoConfigurationExcludeFilter}）都在这里<b>显式重写</b>，不依赖 meta-annotation 兜底。
 *
 * <h2>scanBasePackages 刻意<b>不含</b> {@code com.rag.task.task.rpc}</h2>
 * task-api 与 task-rpc 是两个进程；扫进去会把 task-rpc 的 Bean 拖进 HTTP 网关的上下文。
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.rag.task.task.api",
                "com.rag.common"
        },
        excludeFilters = {
                // 前两个是 @SpringBootApplication 内建 @ComponentScan 的原样复制（见下方警告），
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = TypeExcludeFilter.class),
                @ComponentScan.Filter(type = FilterType.CUSTOM, classes = AutoConfigurationExcludeFilter.class),
                // 第三个才是本模块要的：排除共享的 GoStyleJsonBodyConverterConfig
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.rag.common.http.GoStyleJsonBodyConverterConfig.class)
        })
@EnableDiscoveryClient
public class TaskApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}