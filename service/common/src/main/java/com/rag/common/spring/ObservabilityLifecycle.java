package com.rag.common.spring;

import com.rag.common.observability.HttpMiddleware;
import com.rag.common.observability.RpcInterceptor;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/**
 * M9 修复：给 {@link HttpMiddleware} / {@link RpcInterceptor} 的静态线程池补上真正的关闭钩子。
 *
 * <p><b>背景</b>：两个类的 javadoc 都写着
 * 「业务模块在 {@code @PreDestroy} 里显式调 {@link #shutdown()}」——
 * 但全仓<b>没有任何一个调用者</b>，那段 javadoc 描述的是一个不存在的契约。
 * 两个池分别是：
 * <ul>
 *   <li>{@code HttpMiddleware.HTTP_EXECUTOR}（cached pool，线程名 {@code obs-http-worker}）</li>
 *   <li>{@code RpcInterceptor.TIMEOUT_SCHEDULER} + {@code RpcInterceptor.HANDLE_EXECUTOR}</li>
 * </ul>
 *
 * <p>因为线程都 setDaemon(true)，不关也不会阻止 JVM 退出，所以问题不显形；
 * 但 Spring 的 graceful shutdown 与「容器已关闭、观测层线程池仍在收尾」的时序会错开，
 * 优雅停机期间可能丢最后几个 span。
 *
 * <p><b>为什么用 Spring Bean 而不是继续让业务各写一遍</b>：这两个池是 static 的，
 * 每模块各写一份 {@code @PreDestroy} 只是把同一个调用复制 N 份、漏一份就静默失效。
 * 收口到本 Bean 后，<b>凡是注册了本 Bean 的模块都自动生效</b>。
 *
 * <p><b>注册方式</b>：
 * <ul>
 *   <li>全包扫描 {@code com.rag.common} 的模块（user-api / admin-api / message-rpc）自动生效</li>
 *   <li>显式 {@code @Import} 的 rpc 模块需在 import 列表里带上本类</li>
 * </ul>
 *
 * <p>幂等：两个 {@code shutdown()} 都是 {@code shutdownNow()}，重复调用安全。
 */
@Component
public class ObservabilityLifecycle {

    /**
     * Spring 容器关闭时回收观测层线程池。
     *
     * <p>本方法不吞异常：即使某个池关闭出错，也要保证另一个池被尝试关闭，
     * 因此两个调用各自独立、异常向上抛（Spring 会在 destroy 阶段记录并继续销毁其余 bean）。
     */
    @PreDestroy
    public void shutdown() {
        HttpMiddleware.shutdown();
        RpcInterceptor.shutdown();
    }
}