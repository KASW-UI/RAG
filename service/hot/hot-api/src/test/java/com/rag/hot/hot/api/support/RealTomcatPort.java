package com.rag.hot.hot.api.support;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * hot-api 真 Tomcat 测试的<b>端口选取 + 归属诊断</b>。
 *
 * <h2>为什么 hot-api 需要它</h2>
 * <p>hot-api 此前只有一个真容器测试类（{@code HotApiRealTomcatGateTest}），
 * 且它用的是 Spring 的 {@code RANDOM_PORT} ⇒ 端口落在<b>OS 临时端口段</b>
 * （macOS：49152–65535）⇒ 而<b>别的会话的 Docker 容器正好在那一段发布端口</b>
 * ⇒ 客户端可能把请求打到别人的服务上 ⇒ <b>零字节即关</b>
 * ⇒ <b>看着像被测代码炸了，其实压根没打到</b>，而且<b>所有用例照样绿</b>。
 * <p>⇒ 本类把「真容器要绑哪个口」固定下来，使
 * {@code HotApiFailurePathMatrixTest} 能在<b>真 Tomcat</b>上跑失败路径矩阵。
 *
 * <h2>⚠️ 端口段口径（2026-10-29 · R2 lane · hot 域）</h2>
 * <p>本段 = <b>{@code 18146–18150}</b>（5 口），由本轮 lane 协调分配：
 * <ul>
 *   <li><b>{@code 18141–18145}</b> = article 域真容器测试段；</li>
 *   <li><b>{@code 18146–18150}</b> = hot 域真容器测试段（<b>本类</b>）。</li>
 * </ul>
 * ⚠️ <b>为什么不用 global 段表里 hot 域的 {@code 22500–22599}</b>：
 * 该段已被 <b>hot-rpc 整段吃满</b>（实测）——
 * <ul>
 *   <li>{@code 22500–22579} = {@code hot-rpc/.../support/RealGrpcPort.java:91-94}
 *       （真进程 gRPC server 端口）；</li>
 *   <li>{@code 22580–22599} = {@code hot-rpc/.../support/RealGrpcPortTest.java:48-51}
 *       （守门脚手架段，用来造「端口已被占」的样本）；</li>
 * </ul>
 * ⇒ <b>段内没有留给 hot-api 的切片</b> ⇒ 本轮用 lane 分配的 {@code 18146–18150}。
 * 📌 global 段表（{@code docs/DRIFT/global/vs-common.md} §4.10.19.3，G4 写权）里
 * hot 域那一行仍是「预分配 22500–22599」⇒ <b>该表需要 G4 在收口时补记本段</b>
 * ⇒ 本域只在自己的台账里登记，<b>不改共享文件</b>。
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b> ⇒ <b>不存在语义对照义务</b>。
 * 它只影响「RAG 侧测试能不能稳定跑到被测代码」，<b>不影响任何对外行为</b>。
 *
 * <h2>⚠️ 域作用域</h2>
 * <p>同款 {@code RANDOM_PORT} 暴露面<b>在别的域也有</b> ⇒ 需要<b>各自复制</b>本类
 * ⇒ 登记在 {@code global} 收口域。
 * <p>⚠️ <b>为什么不能放到共享层共用一份</b>：{@code service/common} <b>不发布 test-jar</b>
 * ⇒ 业务模块的 test scope <b>拿不到</b>共享层的测试类 ⇒ 只能复制。
 */
public final class RealTomcatPort {

    /**
     * hot-api 真 Tomcat 测试专用端口段（<b>刻意避开 OS 临时端口段 49152–65535</b>）。
     */
    public static final int FIRST = 18146;

    /** 端口段上界（含）。 */
    public static final int LAST = 18150;

    /** 单次 bind 自检的超时（bind 本身是本地的，给 1s 足够）。 */
    private static final int BIND_TIMEOUT_MS = 1000;

    private RealTomcatPort() {
    }

    /**
     * 挑一个<b>在 127.0.0.1 上确实可绑</b>的端口。
     *
     * @return 端口号（一定在 {@link #FIRST}–{@link #LAST} 内）
     * @throws IllegalStateException 整段都被占满时
     */
    public static int pick() {
        for (int port = FIRST; port <= LAST; port++) {
            if (isFree(port)) {
                return port;
            }
        }
        throw new IllegalStateException(
                "hot-api 真 Tomcat 测试端口段 " + FIRST + "-" + LAST + " 已被占满"
                        + "（本机并行会话太多？）⇒ 换一段端口再试");
    }

    /**
     * 该端口能不能<b>同时</b>满足两件事：
     * <ol>
     *   <li><b>{@code 0.0.0.0:P} 可绑</b> —— Tomcat 将来要绑的就是通配地址；</li>
     *   <li><b>{@code 127.0.0.1:P} 可绑</b> —— 客户端连的就是 127.0.0.1。</li>
     * </ol>
     *
     * @param port 端口号
     * @return {@code true} = 两个地址都能绑（= 没有第三方持有者）
     */
    static boolean isFree(int port) {
        try (ServerSocket wildcard = new ServerSocket();
             ServerSocket specific = new ServerSocket()) {
            wildcard.setReuseAddress(true);
            wildcard.bind(new InetSocketAddress(port), 1);   // 0.0.0.0 —— Tomcat 将来要绑的那个
            specific.setReuseAddress(true);
            specific.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 诊断用：<b>谁占着这个端口</b>。
     *
     * @param port 端口号
     * @return 人类可读的占用者描述（查不到时返回「查不到占用者（lsof 不可用？）」）
     */
    public static String holders(int port) {
        List<String> lines = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("lsof", "-nP", "-iTCP:" + port, "-sTCP:LISTEN")
                    .redirectErrorStream(true)
                    .start();
            try (InputStream in = p.getInputStream()) {
                String out = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String line : out.split("\n")) {
                    if (line.isBlank() || line.startsWith("COMMAND")) {
                        continue;
                    }
                    lines.add(line.trim());
                }
            }
            p.waitFor(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (IOException | InterruptedException e) {
            return "（查不到占用者：lsof 不可用 —— " + e + "）";
        }
        if (lines.isEmpty()) {
            return "（lsof 没查到监听者）";
        }
        return String.join(" | ", lines);
    }
}
