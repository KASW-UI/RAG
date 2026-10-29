package com.rag.common.spring;

import com.rag.common.observability.HttpMiddleware;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.stereotype.Component;

/**
 * <p><b>Go 出处</b>：{@code service/common/observability/http.go:36}（Go 侧的 HTTP 中间件池由<b>进程生命周期</b>托管，Go 侧无显式关闭）
 * ⇒ ⇒ Java 侧<b>必须</b>显式关（线程是 daemon ⇒ 不关也不阻止 JVM 退出，但会与 Spring 的 graceful shutdown 错开）
 * ⇒ ⇒ 这是<b>表达层差异</b>，Go 侧<b>没有对应物</b> ⇒ <b>不构成行为漂移</b>。
 * <b>HTTP 观测层线程池的关闭钩子 —— 只在 servlet 进程里存在</b>。
 *
 * <h2>为什么必须与 {@link ObservabilityLifecycle} 分开</h2>
 *
 * <p>{@link HttpMiddleware} 的 {@code newHttpMiddleware(...)} 返回类型是
 * {@code jakarta.servlet.Filter}，方法体里还有 {@code new Filter() { … }}（匿名类）
 * ⇒ <b>该类被加载时，字节码验证器就必须加载 {@code Filter}</b>
 * ⇒ 运行时 classpath 上没有 servlet-api 时 ⇒ {@code NoClassDefFoundError: jakarta/servlet/Filter}。
 *
 * <p>而<b>纯 gRPC 进程</b>的运行时 classpath 上<b>确实没有</b> servlet-api
 * （实测 {@code mvn dependency:list}：只有 {@code spring-web}，无 {@code jakarta.servlet-api}）
 * ⇒ 那些进程里 {@code HttpMiddleware} <b>不该被加载</b>。
 *
 * <p>⇒ 回收 HTTP 池的动作必须<b>待在一个只会在 servlet 进程里出现的 Bean</b> 里。
 *
 * <h2>为什么用 {@code @ConditionalOnWebApplication(type = SERVLET)}</h2>
 *
 * <ul>
 *   <li>本类位于 {@code com.rag.common.spring} ⇒ <b>api 模块</b>（10 个，全包扫描 {@code com.rag.common}）
 *       会扫到它 ⇒ servlet 进程 ⇒ 条件成立 ⇒ Bean 注册 ⇒ <b>HTTP 池照旧被回收</b>；</li>
 *   <li><b>rpc 模块一律不扫 {@code com.rag.common}</b>（会把 servlet 类拖进来）
 *       ⇒ 本类在 rpc 进程里<b>根本不会被注册</b>
 *       ⇒ 关闭路径<b>不碰</b> {@code HttpMiddleware} ⇒ 不再有 {@code NoClassDefFoundError}。</li>
 * </ul>
 *
 * <p>⚠️ <b>为什么不用 {@code @ConditionalOnClass(Filter.class)}</b>：那个条件只回答
 * 「classpath 上有没有这个类」，而 {@code service/common} 把 {@code spring-webmvc} 声明为
 * <b>provided</b> ⇒ 它<b>不传递</b> ⇒ 消费者进程自己有没有 servlet-api 取决于各自的依赖
 * ⇒ 用「有没有类」判断会把<b>恰好带 web 依赖但进程不是 web 的</b>那种组件也算进去。
 * <b>「是不是 servlet 进程」才是这里要回答的问题</b>（进程形态，不是 classpath 形态）。
 *
 * <p><b>默认行为零变化</b>：api 侧 HTTP 池仍被回收（只是换了回收者）；rpc 侧的 HTTP 池
 * <b>从未被创建过</b>（{@code HTTP_EXECUTOR} 是 {@code HttpMiddleware} 的 static 字段，
 * 而该类在 rpc 进程里从不加载）⇒ 本来就没什么可关 ⇒ 语义等价。
 *
 * <p>幂等：{@code shutdown()} 是 {@code shutdownNow()}，重复调用安全。
 *
 * <p>守门：{@code HttpPoolShutdownWithoutServletGateTest}（3 条：关闭路径无 web 类型 ·
 * 常量池无 {@code HttpMiddleware} 引用 · servlet 上下文注册 / 非 web 上下文不注册）。
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class HttpPoolLifecycle {

    /**
     * Spring 容器关闭时回收 HTTP 侧的观测层 worker 线程池。
     *
     * <p>⚠️ 只有<b>servlet 进程</b>才会走到这里 ⇒ 调用 {@link HttpMiddleware#shutdown()}
     * 是安全的（该进程必然有 servlet-api）。
     */
    @PreDestroy
    public void shutdown() {
        HttpMiddleware.shutdown();
    }
}
