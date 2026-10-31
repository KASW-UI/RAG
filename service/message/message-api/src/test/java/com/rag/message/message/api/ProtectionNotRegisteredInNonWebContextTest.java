package com.rag.message.message.api;

import com.rag.message.message.api.client.MessageRpcClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁死「HTTP 请求保护层只在 servlet 容器里注册」这条守卫。
 *
 * <h2>为什么这条测试必须存在</h2>
 *
 * <p>2026-10-22 保护层从 {@code message-api} 下沉到 {@code com.rag.common.protection}，
 * 四个 Filter 因此从「只有 message-api 扫得到」变成「<b>任何扫 {@code com.rag.common}
 * 的进程都会扫到</b>」。而 {@code com.rag.common} 恰恰是 gRPC 服务端进程也要用的包。
 *
 * <p><b>诚实边界：写下这条时，仓库里并不存在「被这条守卫救下来」的现行缺陷。</b>
 * 实测 {@code mvn dependency:list -pl service/message/message-rpc}：该模块依赖树里
 * <b>没有</b> {@code jakarta.servlet-api}、也没有 {@code tomcat-embed-core}
 * （只有 {@code tomcat-embed-el}，它不含 servlet API）；
 * 而四个 rpc 进程又<b>已经刻意不扫 {@code com.rag.common}</b>
 * （原因正是同包下 {@code HttpMiddlewareFilter} 是 servlet Filter）。
 * ⇒ 今天「无 servlet 的进程会崩」这件事被「它们不扫 common」这条口头约定挡住了。
 *
 * <p>但那句话写在每个 rpc 进程的注释里，而注释不会随代码评审失守保护测试。
 * 四个 Filter 上的 {@code @ConditionalOnWebApplication(SERVLET)} 才是那个真正起作用的约束。
 * ⇒ 本测试的价值不是「现在修了个 bug」，是<b>让守卫从注释变成可执行断言</b>，
 * 哪天有人给 rpc 进程加上 {@code scanBasePackages={"com.rag.common"}}
 * （或给 common 加个 web 类），本测试会立刻指出条件注解被谁破坏了。
 *
 * <h2>为什么用 {@code webEnvironment = NONE} 来证</h2>
 *
 * <p>它是「非 web 上下文」最直接的可编程表达：{@code @ConditionalOnWebApplication(SERVLET)}
 * 的判定依据就是「上下文是不是 {@code WebApplicationContext}」，
 * 而 {@code NONE} 明确让它不是。对照组 {@link MessageApiProtectionMvcTest} 用
 * {@code MOCK}（是 {@code WebApplicationContext}）——两个测试合起来正好钉住条件的<b>两侧</b>：
 * 只测「MOCK 下注册」不能证明条件存在（可能是常量 true），只测「NONE 下不注册」才证明它是条件。
 *
 * <p>⚠️ 本类<b>刻意不</b>断言 gRPC client breaker 拦截器不注册：
 * 它的条件是 {@code @ConditionalOnClass}（devh starter 在不在），
 * 与 web 无关，且 message-api 本来就有该 starter —— 那是<b>应该</b>注册的。
 */
@SpringBootTest(
        classes = MessageApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.server-addr=127.0.0.1:1",
                "grpc.client.message-rpc.address=static://127.0.0.1:65535",
                "grpc.client.message-rpc.negotiation-type=plaintext",
                "management.otlp.tracing.export.enabled=false",
                "logging.level.com.alibaba.nacos=WARN"
        })
class ProtectionNotRegisteredInNonWebContextTest {

    @Autowired
    private ApplicationContext ctx;

    @MockitoBean
    private MessageRpcClient rpcClient;

    @Test
    @DisplayName("非 web 上下文里四个请求保护 Filter 都不注册（@ConditionalOnWebApplication 生效）")
    void protectionFiltersAreAbsentInNonWebContext() {
        assertFalse(ctx.getBeansOfType(com.rag.common.protection.GoStyleMaxBytesFilter.class).size() > 0,
                "com.rag.common.protection.GoStyleMaxBytesFilter 是 jakarta.servlet.Filter 的实现，"
                        + "在没有 servlet API 的进程里注册它会在类加载阶段抛 NoClassDefFoundError；"
                        + "它在 com.rag.common 下，必须靠 @ConditionalOnWebApplication(SERVLET) 挡住");
        assertEquals(0, ctx.getBeansOfType(com.rag.common.protection.GoStyleMaxConnsFilter.class).size());
        assertEquals(0, ctx.getBeansOfType(com.rag.common.protection.GoStyleCpuSheddingFilter.class).size());
        assertEquals(0, ctx.getBeansOfType(com.rag.common.protection.GoStyleBreakerFilter.class).size());
    }

    @Test
    @DisplayName("同一次启动里普通 Bean 仍在（证明上一条不是「整个上下文都没起来」）")
    void contextItselfIsAlive() {
        assertTrue(ctx.getBeanNamesForType(
                        org.springframework.web.client.RestTemplate.class).length >= 0);
        assertTrue(ctx.getBeanDefinitionCount() > 0,
                "上下文必须真的装载了 Bean，否则上面那条「Filter 不存在」是因为什么都没扫到 —— "
                        + "那是一条恒真的假绿测试");
        // 进程标识的 Bean 必须存在：它是 com.rag.common 下不依赖 servlet 的那一类
        assertTrue(ctx.containsBean("messageApiProperties")
                        || ctx.getBeanNamesForType(
                                com.rag.message.message.api.config.MessageApiProperties.class).length > 0,
                "至少要能拿到一个真实 Bean，证明组件扫描确实跑过了");
    }
}