package com.rag.common.protection;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <b>产出出向 deadline 传播拦截器的 Bean（E65 · 2026-10-30 · G4 lane）。</b>
 *
 * <h2>为什么要有这个类（装配面）</h2>
 * <p>{@link GoStyleOutboundDeadlineInterceptor} 带 devh 的
 * {@code @GrpcGlobalClientInterceptor} 注解，而 devh 的
 * {@code AnnotationGlobalClientInterceptorConfigurer} 是用
 * {@code applicationContext.getBeansWithAnnotation(GrpcGlobalClientInterceptor.class)}
 * 收集的 ⇒ ⇒ <b>它必须是一个 Bean</b>（否则「类在、但永远不被收集」）。
 *
 * <p>⚠️ <b>纯 gRPC 进程刻意不扫 {@code com.rag.common}</b>（那一族里有
 * {@code jakarta.servlet.Filter}，全包扫描会把 web 类拖进 gRPC 进程）⇒ ⇒
 * rpc 侧只能靠 {@code @Import} 精确引入 ⇒ 而 <b>10 个 rpc 组件已经全部
 * {@code @Import} 了 {@link GoStyleProtectionConfiguration}</b> ⇒ ⇒
 * 本类只要被它 {@code @Import}，<b>全部 rpc 进程零改动</b>即获得「出向 deadline 传播」。
 * <p>api 侧（<b>10 个</b> api 模块都含 {@code com.rag.common}：9 个用
 * {@code @SpringBootApplication(scanBasePackages = {…, "com.rag.common"})}，
 * {@code task-api} 用 {@code @ComponentScan}）则<b>靠扫描</b>拿到本类
 * ⇒ ⇒ 两侧走<b>同一条装配路径</b>、拿到<b>同一个定义点</b>
 * ⇒ ⇒ 结构上不可能「api 有、rpc 没有」。
 * <p>⚠️ <b>「10 个 api 都会扫到」目前是源码级断言</b>（逐个读过 10 个
 * {@code *Application.java} 的扫描声明）⇒ 运行时证据 =
 * {@code GoStyleOutboundDeadlineApiAssemblyTest}（用<b>与 api 同款</b>的
 * {@code scanBasePackages} 起真上下文 ⇒ 断言拦截器进了 devh 的 registry）。
 *
 * <h2>为什么不用 {@code @ConditionalOnMissingBean} 避重</h2>
 * <p>与 {@link GoStyleGrpcClientBreakerConfiguration} 同款理由：该条件只在
 * <b>自动配置类</b>上可靠，在 {@code @Bean} 方法上只能匹配「已被处理过」的定义
 * ⇒ 用它会得到重复注册 ⇒ ⇒ 本仓采用<b>单一来源</b>：拦截器类<b>不带</b>
 * {@code @Component}，<b>只由本类产出</b>。
 *
 * <h2>{@code @ConditionalOnClass} 为什么用 {@code name = ...}</h2>
 * <p>本类的 {@code @Bean} 方法返回类型引用了 {@code io.grpc.ClientInterceptor}
 * ⇒ 若 grpc 不在 classpath，<b>加载本类就会失败</b> ⇒ 条件必须写成字符串形式，
 * 让 Spring 用 ASM 读元数据、<b>在加载本类之前</b>完成判定。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {
        "net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor",
        "io.grpc.ManagedChannel"
})
public class GoStyleOutboundDeadlineConfiguration {

    /**
     * 产出出向 deadline 传播拦截器。
     *
     * <p>⚠️ <b>Bean 的存在还不够</b>（AGENTS.md §3.3）：devh 认的是<b>类上的注解</b>，
     * 而真正的收集发生在 starter 的 {@code GlobalClientInterceptorRegistry}
     * ⇒ ⇒ 锁定用例 = {@code GoStyleOutboundDeadlineRpcAssemblyTest}
     * （真 Spring 上下文，断言它在 registry 里 ⇒ 由此可推出 channel factory 会装配它）。
     */
    @Bean
    public GoStyleOutboundDeadlineInterceptor goStyleOutboundDeadlineInterceptor(
            GoStyleProtectionProperties props) {
        return new GoStyleOutboundDeadlineInterceptor(props);
    }
}
