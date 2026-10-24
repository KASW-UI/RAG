package com.rag.comment.comment.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 comment-rpc 的**启动形态**守门测试。
 *
 * <h2>为什么需要它</h2>
 * <p>本模块的 pom 里有两条 web 相关依赖：
 * <ol>
 *   <li>{@code spring-boot-web-server} —— 仅为满足 nacos-discovery 的反射引用；</li>
 *   <li>{@code jakarta.servlet:jakarta.servlet-api}（<b>provided</b>）—— 仅供
 *       {@code CommentAuthorityEndpointConfig} 的 {@code ServletRegistrationBean<HttpServlet>} 编译。</li>
 * </ol>
 * 两条都<b>不含</b>嵌入式 servlet 容器（无 {@code tomcat-embed-core}）⇒ 按设计是<b>纯 gRPC 进程</b>。
 *
 * <p>🔴 <b>但 Spring Boot 的启动形态判定不看容器，只看 classpath</b>：
 * {@code WebApplicationType.deduceFromClasspath()} 只要发现
 * {@code jakarta.servlet.Servlet} 就在，就判定为 {@code SERVLET}；
 * 而 <b>{@code spring-boot-maven-plugin} 的 repackage 会把 provided 作用域的 jar 也打进
 * {@code BOOT-INF/lib}</b> ⇒ 判定成立 ⇒ 启动期去找 {@code ServletWebServerFactory} ⇒ 找不到 ⇒
 * <b>APPLICATION FAILED TO START: "Unable to start web server"</b> ⇒ <b>组件根本起不来</b>。
 *
 * <p><b>实测（2026-10-24 双侧活体对拍）：</b>
 * comment-rpc 的 fat jar 里确实有 {@code BOOT-INF/lib/jakarta.servlet-api-6.1.0.jar}；
 * message-rpc / like-rpc <b>没有</b>声明这个依赖 ⇒ 它们的 fat jar 里没有 ⇒ 不受影响。
 * ⇒ 这是 comment 域<b>独有</b>的缺陷。
 *
 * <h2>本测试守的是「装配层」，不是「行为层」</h2>
 * <p>它做两件事，缺一不可：
 * <ol>
 *   <li><b>复现危险</b>：断言 classpath 上<b>确实</b>有 {@code jakarta.servlet.Servlet}
 *       —— 否则这个测试会因为「危险不存在」而空转；</li>
 *   <li><b>断言缓解存在</b>：断言打包配置里<b>确实</b>声明了
 *       {@code spring.main.web-application-type: none}。</li>
 * </ol>
 * ⇒ 只做第 2 件事的话，把那行删掉测试仍然绿（危险仍在）⇒ 那就是「因错误原因而绿」的假守卫。
 */
class CommentRpcWebApplicationTypeGateTest {

    @Test
    @DisplayName("🔴 危险存在：classpath 上有 jakarta.servlet.Servlet ⇒ Boot 会判定成 web 应用")
    void servletApiIsOnTheClasspathSoBootWouldDeduceServletWebApplicationType() {
        assertThat(ClassUtils.isPresent("jakarta.servlet.Servlet", null))
                .as("jakarta.servlet.Servlet 在 classpath 上 ⇒ Boot 的 deduceFromClasspath() 会判定为 SERVLET")
                .isTrue();
        // 对照组：确实**没有**嵌入式 servlet 容器 ⇒ 「判成 web 应用但没有容器」= 启动失败
        assertThat(ClassUtils.isPresent("org.apache.catalina.startup.Tomcat", null))
                .as("没有嵌入式 Tomcat ⇒ ServletWebServerFactory 不会被创建 ⇒ 启动期必然失败")
                .isFalse();
    }

    @Test
    @DisplayName("🔴 缓解存在：打包配置必须声明 spring.main.web-application-type: none")
    void packagedConfigurationDeclaresNonWebApplicationType() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("comment-rpc", new ClassPathResource("application.yml"));

        assertThat(sources).isNotEmpty();
        Object value = sources.get(0).getProperty("spring.main.web-application-type");
        assertThat(value)
                .as("comment-rpc 是纯 gRPC 进程（Go 侧 comment.go 也只起 zrpc server + promhttp goroutine）"
                        + "；缺这一行 ⇒ Boot 判定成 SERVLET ⇒ 找不到 ServletWebServerFactory ⇒ 进程起不来")
                .isEqualTo("none");
    }
}
