package com.rag.common.spring;

/**
 * 守门用的<b>子进程探针</b>：在一个<b>没有 servlet-api 的 classpath</b> 上调用共享层的关闭钩子。
 *
 * <p>⚠️ <b>为什么要在子进程里跑</b>：本类要复刻的是「<b>纯 gRPC 进程</b>」的 classpath 形态
 * （没有 {@code jakarta.servlet-api}）⇒ 同一个 JVM 里无法把已加载的类「卸载」
 * ⇒ 只能起一个**新 JVM**，用一份<b>剔掉 servlet-api 的 classpath</b> 启动它。
 *
 * <p>被验的目标：{@link ObservabilityLifecycle#shutdown()} 的关闭路径
 * ⇒ <b>不得触发任何 web 类型的类加载</b>。
 *
 * <p>⚠️ 类名<b>刻意不以 {@code Test} 结尾</b> ⇒ surefire 不会把它当用例跑
 * （它没有 {@code @Test} 方法，只是一个 {@code main}）。
 */
public final class HttpShutdownProbeWithoutServlet {

    private HttpShutdownProbeWithoutServlet() {
    }

    /**
     * 入口：跑共享层的关闭钩子，跑完打印 {@code PROBE_OK}。
     *
     * <p>⚠️ 若关闭路径碰了 web 类型 ⇒ 这里会抛 {@code NoClassDefFoundError}
     * ⇒ 子进程非零退出 ⇒ 守门用例失败。
     */
    public static void main(String[] args) {
        new ObservabilityLifecycle().shutdown();
        System.out.println("PROBE_OK");
    }
}
