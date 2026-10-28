package com.rag.users.user.rpc.support;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 极简 TCP 透传代理 —— 只为制造一种故障形态：<b>服务端优雅断开（FIN）</b>。
 *
 * <h2>为什么不用 {@code container.stop()}</h2>
 * <p>那发的是 <b>RST</b> ⇒ Lettuce 往 socket 写命令时立刻失败 ⇒ 压根不经过
 * {@code disconnectedBehavior} 分支 ⇒ 测不出「断连时命令是被排队还是被拒绝」这条漂移。
 *
 * <h2>⚠️ 为什么抽成共享 support 类（2026-10-28 · G1 lane）</h2>
 * <p>本代理原先是 {@code UserRpcRedisDisconnectedFastFailTest} 里的一个**嵌套类**（域内另有两份同款副本）。
 * <p>本类被抽出来的**直接原因不是去重**，而是<b>方法学要求</b>：
 * <ul>
 *   <li>{@code DRIFT-USERRPC-V-014} 的 A/B 受控对照里，**A 组与 B 组必须用【同一个】代理实现**
 *       ⇒ ⇒ 否则「同口径」这句话就不成立 ⇒ ⇒ 两组各写一份 ⇒ 一旦有人改了其中一份 ⇒
 *       对照就悄悄换了变量 ⇒ ⇒ **结论作废且看不出来**；</li>
 *   <li>⇒ ⇒ 抽成<b>同一份</b> ⇒ ⇒ 两组必然同款。</li>
 * </ul>
 * <p>⚠️ **抽取时逐字搬运了实现**（只改类名/包名/可见性）⇒ ⇒ 行为零变化 ⇒ ⇒
 * 抽取后**必须重跑原测试**确认没漂（见 §48.1 的回归口径）。
 *
 * <h2>它制造的故障形态（两侧必须一致，否则数字不可比）</h2>
 * <p><b>先关监听口</b>（⇒ 重连被 connection refused 挡掉）<b>＋ 再对已建立连接半关闭</b>
 * （⇒ 客户端读到 FIN 而不是 RST）。
 *
 * <p>⚠️ {@code shutdownOutput()} 而不是 {@code close()}：socket 上若还有未读数据，
 * {@code close()} 会发 <b>RST</b> ⇒ 就退回成「Connection reset」那条
 * <b>不经过 {@code disconnectedBehavior}</b> 的路径了。
 *
 * <p>⚠️ <b>上游地址延迟解析</b>（{@code accept} 时才取）⇒ ⇒ 代理可以在容器启动**之前**就建好
 * ⇒ ⇒ {@code @DynamicPropertySource} 能在 Spring 上下文创建前就拿到端口。
 */
public final class GoStyleRedisDisconnectProxy implements AutoCloseable {

    private final ServerSocket listener;
    private final Supplier<String> upstream;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "uu-redis-rpc-proxy");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean closed;

    private GoStyleRedisDisconnectProxy(Supplier<String> upstream) throws IOException {
        this.upstream = upstream;
        this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    }

    public static GoStyleRedisDisconnectProxy create(Supplier<String> upstream) {
        try {
            GoStyleRedisDisconnectProxy p = new GoStyleRedisDisconnectProxy(upstream);
            p.start();
            return p;
        } catch (IOException e) {
            throw new IllegalStateException("透传代理起不来", e);
        }
    }

    /** 本代理监听的端口（{@code @DynamicPropertySource} 用它把 app 指过来）。 */
    public int port() {
        return listener.getLocalPort();
    }

    private void start() {
        pool.submit(() -> {
            while (!closed) {
                try {
                    Socket client = listener.accept();
                    clients.add(client);
                    String[] hp = upstream.get().split(":");
                    Socket server = new Socket(hp[0], Integer.parseInt(hp[1]));
                    pool.submit(() -> pipe(client, server));
                    pool.submit(() -> pipe(server, client));
                } catch (Exception e) {
                    if (!closed) {
                        // 上游还没起来等一小会儿再来
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
            }
        });
    }

    private static void pipe(Socket from, Socket to) {
        byte[] buf = new byte[8192];
        try (Socket f = from) {
            int n;
            while ((n = f.getInputStream().read(buf)) > 0) {
                to.getOutputStream().write(buf, 0, n);
                to.getOutputStream().flush();
            }
        } catch (Exception ignored) {
            // 断连是预期内的
        } finally {
            try {
                to.shutdownOutput();
            } catch (Exception ignored) {
                // 已关闭
            }
        }
    }

    /**
     * 制造断连：<b>先关监听口</b>（⇒ 重连被 connection refused 挡掉）
     * ⇒ <b>再半关闭已建立的连接</b>（⇒ 客户端收到 FIN）。
     */
    public void disconnectGracefully() {
        closed = true;
        try {
            listener.close();
        } catch (Exception ignored) {
            // 已关闭
        }
        for (Socket s : clients) {
            try {
                s.shutdownOutput();
            } catch (Exception ignored) {
                // 已关闭
            }
        }
        clients.clear();
    }

    @Override
    public void close() {
        disconnectGracefully();
        pool.shutdownNow();
    }
}
