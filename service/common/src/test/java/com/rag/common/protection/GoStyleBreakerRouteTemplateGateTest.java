package com.rag.common.protection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守门：共享层 HTTP 熔断器的<b>键必须是「注册期路由模板」</b>（Go 语义），
 * 且<b>未命中路由（404）不参与 breaker</b>。
 *
 * <p><b>Go 出处（消费点）</b>：
 * <ul>
 *   <li>{@code rest/engine.go:131-132} ⇒ 每条路由各建一个 breaker，
 *       传的是 {@code route.Path}（<b>注册期模板</b>）；</li>
 *   <li>{@code rest/handler/breakerhandler.go:19} ⇒ 键 = {@code METHOD://route.Path}；</li>
 *   <li>{@code rest/engine.go:244-268} ⇒ 404 的链只有 Trace + Log，<b>没有 breaker</b>。</li>
 * </ul>
 *
 * <p><b>为什么必须是真 Tomcat + 真 Spring MVC</b>：
 * 断言的是「Filter 阶段就能拿到注册期模板」⇒ 需要真实的
 * {@code RequestMappingHandlerMapping} 与真实的 DispatcherServlet 流程；
 * 自己 new 一个 mapper 或用 MockMvc 造属性都是<b>假绿</b>（AGENTS.md §3.3）。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = GoStyleBreakerRouteTemplateGateTest.TestApp.class)
@DisplayName("共享层 HTTP breaker：键 = 注册期路由模板；404 不进 breaker")
class GoStyleBreakerRouteTemplateGateTest {

    @LocalServerPort
    int port;

    @Autowired
    GoStyleBreakerFilter breakerFilter;

    @Autowired
    GoStyleRouteTemplateResolver resolver;

    @Autowired
    RequestMappingHandlerMapping handlerMapping;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private int get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    @DisplayName("① 同一模板下的不同具体 URI 必须共用一个 breaker（Go：/article/:id 一个闸门）")
    void sameTemplateSharesOneBreaker() throws Exception {
        int before = breakerFilter.trackedRoutes();

        assertThat(get("/x/1")).isEqualTo(200);
        assertThat(get("/x/2")).isEqualTo(200);

        // ⚠️ 核心断言：两个具体 URI ⇒ 同一个模板 ⇒ **只应新增一个** breaker
        assertThat(breakerFilter.trackedRoutes())
                .as("同模板的两个具体 URI 必须共用一个 breaker（否则熔断爆炸半径与 Go 不同）")
                .isEqualTo(before + 1);

        // 另一条<b>不同</b>模板 ⇒ 必须另开一个 breaker
        assertThat(get("/x/static")).isEqualTo(200);
        assertThat(breakerFilter.trackedRoutes())
                .as("不同模板必须各占一个 breaker")
                .isEqualTo(before + 2);
    }

    @Test
    @DisplayName("② 未命中路由（404）不得创建 breaker（Go 的 404 链里没有 breaker）")
    void unmatchedRouteDoesNotCreateBreaker() throws Exception {
        int before = breakerFilter.trackedRoutes();

        assertThat(get("/definitely-not-a-route")).isEqualTo(404);

        assertThat(breakerFilter.trackedRoutes())
                .as("404 不得凭空造出 breaker（Go：notFoundHandler 只有 Trace + Log）")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("③ 每条已注册路由都必须能解析出模板（防「解析失败 ⇒ 静默失去熔断保护」）")
    void everyRegisteredRouteResolvesToTemplate() {
        int checked = 0;
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            for (String pattern : info.getPatternValues()) {
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.setMethod(firstMethod(info));
                request.setRequestURI(sampleUri(pattern));
                request.setContextPath("");

                GoStyleRouteResolution resolution = resolver.resolve(request);

                assertThat(resolution.kind())
                        .as("已注册路由必须能解析出模板：%s（解析不出来 ⇒ breaker 会静默失效）", pattern)
                        .isEqualTo(GoStyleRouteResolution.Kind.TEMPLATE);
                assertThat(resolution.template()).isEqualTo(pattern);
                checked++;
            }
        }
        assertThat(checked).as("守门本身必须真的检查到了路由（否则是假绿）").isGreaterThan(0);
    }

    /** 把模板里的 {@code {var}} 换成一个具体取值，得到一条可匹配的 URI。 */
    private static String sampleUri(String pattern) {
        return pattern.replaceAll("\\{[^}]+}", "1");
    }

    /** 取该路由允许的第一个方法；没限定方法时用 GET（只影响「造请求」这一步）。 */
    private static String firstMethod(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream()
                .findFirst()
                .map(Enum::name)
                .orElse("GET");
    }

    @SpringBootApplication
    static class TestApp {

        @RestController
        static class EchoController {

            @GetMapping("/x/{id}")
            String byId(@PathVariable String id) {
                return id;
            }

            @GetMapping("/x/static")
            String stat() {
                return "static";
            }
        }
    }
}
