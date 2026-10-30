package com.rag.message.message.rpc.support;

import io.grpc.ManagedChannel;
import io.grpc.Server;

import java.util.concurrent.TimeUnit;

/**
 * 🔴 **rpc 测试的 gRPC 资源优雅拆除器**（`DRIFT-MSG-V-032` 加固 · 2026-10-30）
 *
 * <h2>为什么需要它（V-032 的根因）</h2>
 * <p>{@code DRIFT-MSG-V-032} 登记的现象 = 本域的 {@code MessageRpcSlowThresholdGateTest}
 * 在<b>全量并发回归</b>里偶发
 * {@code StatusRuntime UNAVAILABLE: Network closed for unknown reason}
 * （2026-10-27 三域全量回归，该模块 {@code Tests run: 84, Failures: 0, Errors: 1} ⇒ BUILD FAILURE）。
 * <p>2026-10-30 复核确认：<b>不是</b>被测行为坏了（不是 slow 标记、不是阈值比较、不是空转守门），
 * 而是<b>被测环境自己拆除得太急</b> —— 旧写法是
 * 「发一次调用 ⇒ <b>立刻</b> {@code ch.shutdownNow()} + {@code server.shutdownNow()}」
 * ⇒ 客户端 channel 在服务端连接被硬拆的<b>同时</b>还有在途帧
 * ⇒ netty 侧读到「连接已关」⇒ 客户端抛 {@code UNAVAILABLE: Network closed}。
 * <p>⇒ 它威胁的是<b>阶段一验收证据本身</b>（全量回归当场 BUILD FAILURE ⇒ 收口拿不到绿证据）。
 *
 * <h2>修法</h2>
 * <p><b>优雅关闭</b>：{@code shutdown()}（让在途调用自然收尾）⇒ {@code awaitTermination(...)}
 * （等真的终止）⇒ <b>只有超时</b>才降级成 {@code shutdownNow()}。
 * <p>⚠️ <b>顺序不能反</b>：先关 client channel（等在途调用收尾）⇒ 再关 server。
 * 反过来（先关 server）⇒ 客户端在途调用立刻收到断链 ⇒ 复现 V-032 的症状。
 *
 * <h2>为什么抽成本类（而不是留在测试类里）</h2>
 * <p>§3.3：<b>守门必须测「生产用的那个函数」</b> ⇒ 若把关闭逻辑留在测试类的 private 方法里，
 * 守门就只能测一份<b>副本</b> ⇒ 副本与正身可以分叉 ⇒ 「你测的是 A、跑的是 B」。
 * ⇒ 故抽到 {@code support} 包（与 {@link GrpcTestPort} 同层）⇒ 守门与被测用例<b>共用同一个函数</b>。
 *
 * <h2>⚠️ 本类的性质</h2>
 * <p>它是<b>被测环境的一部分</b>，不是断言 ⇒ ⇒ 它<b>不引入新的断言口径</b>；
 * 断言「优雅关闭真的生效」的那条守门是
 * {@code MessageRpcSlowThresholdTeardownGateTest}（与本类同目录）。
 */
public final class GrpcTestLifecycle {

    /** 优雅关闭的等待上限（秒）—— 超过才降级成 {@code shutdownNow()}。 */
    private static final long TERMINATION_TIMEOUT_SECONDS = 5L;

    private GrpcTestLifecycle() {
    }

    /**
     * 优雅关闭 gRPC 资源（<b>先 channel 后 server</b>）。
     *
     * <p>语义：{@code shutdown()} ⇒ {@code awaitTermination(...)} ⇒ 超时才 {@code shutdownNow()}。
     * ⇒ 在途调用能<b>正常完成</b>；而 {@code shutdownNow()} 会把它取消掉。
     *
     * @param channel 客户端 channel（可空 ⇒ 跳过）
     * @param server  gRPC server（可空 ⇒ 跳过）
     */
    public static void shutdownGracefully(ManagedChannel channel, Server server) {
        if (channel != null) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                    channel.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                    server.awaitTermination(TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
