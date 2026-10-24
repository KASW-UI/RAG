package com.rag.task.task.api;

import com.rag.common.http.GoStyleJsonBodyConverterConfig;
import com.rag.common.protection.GoStyleCpuSheddingFilter;
import com.rag.common.protection.GoStyleBreakerFilter;
import com.rag.common.protection.GoStyleMaxBytesFilter;
import com.rag.common.protection.GoStyleMaxConnsFilter;
import com.rag.common.spring.HttpMiddlewareFilter;
import com.rag.task.task.api.config.TaskApiJsonBodyConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * <b>装配层</b>测试 —— 证明 {@link TaskApiApplication} 的组件扫描真的把该注册的注册了、
 * 该排除的排除了。
 *
 * <h2>为什么这些断言不是「走过场」</h2>
 * 本模块第一版真实踩过一个<b>零报错</b>的坑：在 {@code @SpringBootApplication} 上写了
 * {@code scanBasePackages}、又单独写了一个裸的 {@code @ComponentScan(excludeFilters = …)}。
 * 注解属性不合并 ⇒ meta-annotation 上的 {@code scanBasePackages} 被整体顶掉
 * ⇒ <b>{@code com.rag.common} 整个没被扫到</b>。
 * <p>后果：HTTP 3s 超时中间件 + 四道请求保护闸门<b>全部不注册</b>，
 * 上下文照样启动、测试照样能跑，只是<b>行为全变了</b>（请求不再超时、1 MiB 上限消失）。
 * 这正是 AGENTS.md §3.3 反复强调的形态：<b>缺配置不报错</b>。
 * ⇒ 必须有一条测试钉住「这些 Bean 在容器里」。
 *
 * <p>⚠️ 本类回答的是「<b>装上了吗</b>」，不回答「<b>生效了吗</b>」——
 * 后者由 {@code TaskApiMvcPipelineTest} 的端点级断言回答
 * （例如 {@code textPlainContentTypeIsAccepted} 证明的是请求体转换器<b>真的生效</b>，
 * 而不是「转换器 Bean 存在」）。两条缺一不可。
 */
@SpringBootTest(
        classes = TaskApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "grpc.client.task-rpc.address=static://127.0.0.1:65535",
                "grpc.client.task-rpc.negotiation-type=plaintext",
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class TaskApiWiringTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    @DisplayName("Go task.go:29 —— observability 的 HTTP 中间件必须在容器里（扫到 com.rag.common 的前提）")
    void httpMiddlewareFilterIsRegistered() {
        assertNotNull(ctx.getBean(HttpMiddlewareFilter.class),
                "HttpMiddlewareFilter 未注册 ⇒ com.rag.common 没被扫到 ⇒ HTTP 3s 超时失效");
    }

    @Test
    @DisplayName("Go rest/config.go:12-23,52-53 —— 四道保护闸门（tag 默认全开）必须在容器里")
    void protectionFiltersAreRegistered() {
        assertNotNull(ctx.getBean(GoStyleMaxConnsFilter.class), "MaxConns=10000 失效");
        assertNotNull(ctx.getBean(GoStyleMaxBytesFilter.class), "MaxBytes=1MiB 失效（413 不再返回）");
        assertNotNull(ctx.getBean(GoStyleCpuSheddingFilter.class), "CPU 降载失效（Go CpuThreshold=900）");
        assertNotNull(ctx.getBean(GoStyleBreakerFilter.class), "HTTP 侧熔断失效");
    }

    @Test
    @DisplayName("🔴 本域自己的请求体转换器配置必须在（Go 裸 encoding/json，无媒体类型门）")
    void taskJsonBodyConfigIsRegistered() {
        assertNotNull(ctx.getBean(TaskApiJsonBodyConfig.class));
    }

    @Test
    @DisplayName("🔴 共享的 GoStyleJsonBodyConverterConfig 必须**不在**（它会丢弃非 JSON body）")
    void sharedBodyConverterConfigIsExcluded() {
        // 注册它 ⇒ Content-Type 不含 application/json 时 body 被整段丢弃
        // ⇒ {"id":"123"} + text/plain 会变成 400 user_id invalid，而 Go 是 200。
        // ⚠️ 用 getBeanNamesForType 而不是 getBean(Class)：后者在 Bean 缺席时抛
        //    NoSuchBeanDefinitionException，会把「断言通过」变成「测试报错」——
        //    两者在报告里长得完全一样，极易误读成排除失败。
        assertEquals(0, ctx.getBeanNamesForType(GoStyleJsonBodyConverterConfig.class).length,
                "共享的 body 转换器被扫进来了 ⇒ 非 JSON body 会被丢弃（Go 不会）");
    }

    @Test
    @DisplayName("唯一端点必须注册在 /taskcenter/v1/task/get（少一段 /task 就是全量 404）")
    void controllerIsMappedOnTheGoPath() {
        var mappings = ctx.getBean("requestMappingHandlerMapping",
                org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        var patterns = mappings.getHandlerMethods().entrySet().stream()
                .flatMap(e -> e.getKey().getPathPatternsCondition().getPatterns().stream())
                .map(Object::toString)
                .toList();

        org.junit.jupiter.api.Assertions.assertTrue(
                patterns.contains("/taskcenter/v1/task/get"),
                "未映射到 Go 的完整路径，实际映射 = " + patterns);
    }
}