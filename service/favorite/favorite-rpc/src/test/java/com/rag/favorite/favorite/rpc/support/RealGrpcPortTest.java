package com.rag.favorite.favorite.rpc.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RealGrpcPort} 的守门测试 —— 防止「端口选取」这个机制<b>自己被改坏</b>。
 *
 * <h2>为什么必须有它</h2>
 * <p>本类守的是「favorite-rpc 的 gRPC server 不用 {@code port=0}」这条：
 * {@code 0} 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑 ⇒ 那是
 * <b>Docker 随机发布容器端口用的同一段</b> ⇒ 别的会话的容器一旦先占住
 * {@code 0.0.0.0:P}，我们再绑同一个端口 ⇒ <b>EADDRINUSE</b>
 * ⇒ devh 的 gRPC server 起不来 ⇒ <b>整个测试类启动失败</b>
 * ⇒ 表现为「这条用例偶尔红、换个时间又绿」的<b>环境性抖动</b>。
 *
 * <h2>⚠️ 断言口径：<b>不靠「抢端口段里的端口」</b></h2>
 * <p>「占住端口段的第一个端口，然后断言 {@code pick()} 绕开它」这种写法在<b>全量套件</b>里
 * 会直接挂：同一个 JVM 里<b>上一个测试类的 gRPC server 还活着</b>
 * （Spring 会缓存测试上下文）⇒ 它的端口<b>本来就是</b>端口段的第一个
 * ⇒ 测试想占的那个端口<b>早就被占了</b> ⇒ {@code BindException: Address already in use}
 * ⇒ <b>守门测试自己被环境左右</b>。
 * ⇒ ⇒ 改成：
 * <ul>
 *   <li>① 判定逻辑（{@link RealGrpcPort#isFree(int)}）<b>包级可见</b> ⇒ 直接断言它；</li>
 *   <li>② 「被占」的样本用<b>独立的脚手架端口段</b>（{@link #SCRATCH_FIRST} 起），
 *       <b>不碰</b>生产用的端口段 ⇒ 与「谁还活着」完全无关。</li>
 * </ul>
 */
class RealGrpcPortTest {

    /**
     * 脚手架端口段：只给本测试用，<b>不与</b> {@link RealGrpcPort} 的端口段重叠，
     * 也<b>不与</b> favorite-api 的 {@code RealTomcatPort}（21300–21399）及其
     * 脚手架段（21950–21999）重叠。
     */
    private static final int SCRATCH_FIRST = 22050;

    /** 脚手架端口段上界。 */
    private static final int SCRATCH_LAST = 22099;

    /** 在脚手架段里找一个当前空闲的端口（bind 自检后立刻释放）。 */
    private static int scratchPort() {
        for (int p = SCRATCH_FIRST; p <= SCRATCH_LAST; p++) {
            try (ServerSocket probe = new ServerSocket()) {
                probe.setReuseAddress(true);
                probe.bind(new InetSocketAddress(p), 1);
                return p;
            } catch (IOException ignored) {
                // 换一个
            }
        }
        throw new IllegalStateException(
                "脚手架端口段 " + SCRATCH_FIRST + "-" + SCRATCH_LAST + " 没有空闲端口");
    }

    @Test
    @DisplayName("选出来的端口必须落在专用段内（避开 OS 临时端口段，不与 Docker 发布端口抢）")
    void picksFromDedicatedRange() {
        int port = RealGrpcPort.pick();
        assertTrue(port >= RealGrpcPort.FIRST && port <= RealGrpcPort.LAST,
                "端口必须在 " + RealGrpcPort.FIRST + "-" + RealGrpcPort.LAST
                        + " 内（实际=" + port + "）");
    }

    @Test
    @DisplayName("选出来的端口：0.0.0.0 与 127.0.0.1 都必须真的能绑（= 没有第三方持有者）")
    void pickedPortHasNoThirdPartyHolder() {
        int port = RealGrpcPort.pick();
        assertTrue(RealGrpcPort.isFree(port),
                "pick() 返回的端口 " + port + " 必须两个地址都能绑"
                        + "（否则客户端连 127.0.0.1 会被更具体的监听者抢走）");
    }

    @Test
    @DisplayName("127.0.0.1 上的端口被别人占着 ⇒ 必须判定为不可用（Docker 发布端口那种占用）")
    void rejectsPortHeldOnLoopback() throws IOException {
        int p = scratchPort();
        try (ServerSocket hold = new ServerSocket()) {
            hold.setReuseAddress(true);
            hold.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), p), 1);
            assertFalse(RealGrpcPort.isFree(p),
                    "127.0.0.1:" + p + " 已被占 ⇒ 必须判定为不可用");
        }
    }

    @Test
    @DisplayName("0.0.0.0 上的端口被别人占着 ⇒ 必须判定为不可用（另一个会话的容器占通配地址那种占用）")
    void rejectsPortHeldOnWildcard() throws IOException {
        int p = scratchPort();
        try (ServerSocket hold = new ServerSocket()) {
            hold.setReuseAddress(true);
            hold.bind(new InetSocketAddress(p), 1);
            assertFalse(RealGrpcPort.isFree(p),
                    "0.0.0.0:" + p + " 已被占 ⇒ 必须判定为不可用");
        }
    }
}
