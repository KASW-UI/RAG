package com.rag.common.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守门：共享层的<b>关闭路径不得触发 web 类型的类加载</b>。
 *
 * <p><b>缺陷本体</b>（G1 派单 #8 · {@code PITFALL-FOLLOW-005}）：
 * {@code ObservabilityLifecycle} 此前第一句是 {@code HttpMiddleware.shutdown();}
 * 而 {@code HttpMiddleware} 的 {@code newHttpMiddleware(...)} 返回 {@code jakarta.servlet.Filter}
 * 且方法体里有 {@code new Filter() { … }}（匿名类）
 * ⇒ <b>类加载即触发验证 ⇒ 必须先加载 {@code Filter}</b>
 * ⇒ 在<b>纯 gRPC 进程</b>（运行时 classpath 无 servlet-api）里
 * 关闭瞬间抛 {@code NoClassDefFoundError: jakarta/servlet/Filter}
 * ⇒ ① 关闭日志被污染；② 因本方法不吞异常 ⇒ <b>后面的 {@code RpcInterceptor.shutdown()} 根本不执行</b>
 * ⇒ RPC 池没被回收。
 *
 * <p><b>Go 出处（消费点）</b>：Go 侧 rpc 进程的关闭路径<b>不加载任何 web 类型</b> ——
 * {@code service/follow/rpc/follow.go:45} 是 {@code defer s.Stop()}，
 * 而该文件的 import 清单（{@code service/follow/rpc/follow.go:3-21}）
 * <b>零</b> {@code net/http} / servlet 类 ⇒ RAG 侧这是<b>内部缺陷</b>，不是双向语义漂移。
 *
 * <p><b>为什么必须起子进程</b>：同一个 JVM 里无法把已加载的类「卸载」，
 * 而本模块的测试 classpath <b>有</b> servlet-api（{@code spring-webmvc} 是 provided ⇒ provided 作用域
 * <b>进测试 classpath</b>）⇒ 只能起一个新 JVM，用一份<b>剔掉 servlet-api 的 classpath</b> 复刻纯 gRPC 进程。
 */
@DisplayName("共享层关闭路径：不得触发 web 类型的类加载（纯 gRPC 进程形态）")
class HttpPoolShutdownWithoutServletGateTest {

    @Test
    @DisplayName("① 剔掉 servlet-api 的 classpath 上，关闭钩子必须干净返回（不抛 NoClassDefFoundError）")
    void shutdownPathIsWebTypeFree() throws Exception {
        List<String> classpath = classpathWithoutServlet();
        assertThat(classpath)
                .as("复刻「纯 gRPC 进程」的 classpath 前提：必须真的剔掉了 servlet-api，"
                        + "否则这条守门就是在验一个不存在的形态")
                .noneMatch(e -> e.contains("jakarta.servlet") || e.contains("tomcat-embed-core"));

        Process process = new ProcessBuilder(
                javaBinary(), "-cp", String.join(java.io.File.pathSeparator, classpath),
                HttpShutdownProbeWithoutServlet.class.getName())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();

        assertThat(exitCode)
                .as("子进程退出码（完整输出：\n%s）", output)
                .isZero();
        assertThat(output)
                .as("关闭路径必须跑完（完整输出：\n%s）", output)
                .contains("PROBE_OK")
                .doesNotContain("NoClassDefFoundError")
                .doesNotContain("ClassNotFoundException");
    }

    @Test
    @DisplayName("② 常量池里不得出现 HttpMiddleware 引用（否则纯 gRPC 进程仍会加载 servlet 类）")
    void sharedLifecycleMustNotReferenceHttpMiddleware() throws Exception {
        String constantPool;
        try (InputStream in = ObservabilityLifecycle.class
                .getResourceAsStream("/com/rag/common/spring/ObservabilityLifecycle.class")) {
            assertThat(in).as("必须能读到 ObservabilityLifecycle 的 class 文件").isNotNull();
            constantPool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
        assertThat(constantPool)
                .as("ObservabilityLifecycle 的字节码里若出现 HttpMiddleware，"
                        + "纯 gRPC 进程的关闭路径就会去加载 jakarta.servlet.Filter")
                .doesNotContain("com/rag/common/observability/HttpMiddleware");
    }

    @Test
    @DisplayName("③ HTTP 池的回收者只在 servlet 上下文注册（api 侧行为不变 / rpc 侧不加载 web 类）")
    void httpPoolLifecycleOnlyRegistersInServletContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(HttpPoolLifecycle.class)
                .run(ctx -> assertThat(ctx)
                        .as("非 web 上下文（如纯 gRPC 进程）⇒ 不得注册 HTTP 池回收者")
                        .doesNotHaveBean(HttpPoolLifecycle.class));

        new WebApplicationContextRunner()
                .withUserConfiguration(HttpPoolLifecycle.class)
                .run(ctx -> assertThat(ctx)
                        .as("servlet 上下文（api 进程）⇒ 必须注册 HTTP 池回收者（行为不变）")
                        .hasSingleBean(HttpPoolLifecycle.class));
    }

    /** 测试 JVM 的 classpath，剔掉 servlet-api / tomcat-embed-core ⇒ 复刻纯 gRPC 进程。 */
    private static List<String> classpathWithoutServlet() {
        String raw = System.getProperty("surefire.test.class.path");
        if (raw == null || raw.isBlank()) {
            raw = System.getProperty("java.class.path");
        }
        assertThat(raw).as("拿不到测试 classpath（surefire.test.class.path / java.class.path）").isNotBlank();
        return Arrays.stream(raw.split(java.io.File.pathSeparator))
                .filter(e -> e != null && !e.isBlank())
                .filter(e -> !e.contains("jakarta.servlet") && !e.contains("tomcat-embed-core"))
                .toList();
    }

    private static String javaBinary() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        assertThat(Files.exists(java)).as("找不到 java 可执行文件：%s", java).isTrue();
        return java.toString();
    }
}
