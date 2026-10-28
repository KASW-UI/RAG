package com.rag.users.user.api.support;

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
 * user-api 真 Tomcat 测试的<b>端口选取 + 归属诊断</b>（测试设施 ⇒ 只影响测试能不能稳定跑到被测代码，<b>不影响任何对外行为</b>）。
 *
 * <h2>为什么不能用 Spring 的 {@code RANDOM_PORT}</h2>
 * <p>{@code RANDOM_PORT} = 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑一个
 * ⇒ ⇒ <b>Docker 随机发布容器端口用的就是同一段</b>
 * ⇒ ⇒ 本机是<b>多会话并行</b>的机器 ⇒ ⇒ 盲选口可能<b>正好落在别的容器已发布的口号上</b>。
 *
 * <p>⚠️ <b>危害形态是「静默被抢」</b>（2026-10-28 本机实测）：
 * 邻居占着<b>更具体的地址</b> {@code 127.0.0.1:P} ⇒ macOS/BSD 上 Tomcat 的<b>通配绑定照样成功</b>
 * ⇒ 启动日志照样打「started on port P」⇒ 但客户端连 {@code 127.0.0.1:P} 会<b>命中更具体的那个</b>
 * ⇒ 请求打到了邻居 ⇒ 对端协议不匹配（一个字节都不回就关连接）
 * ⇒ <b>看起来像「被测代码炸了」，其实压根没被请求到</b>。
 *
 * <p>📌 <b>本机实测（2026-10-28 · G1 lane 探针，非推理）</b>：
 * 邻居占 {@code 127.0.0.1:P} ⇒ {@code NettyServerBuilder.forPort(P)} <b>成功</b>，
 * {@code lsof} 显示<b>两个监听者并存</b> ⇒ ⇒ <b>危害真实存在</b>；
 * 只探 {@code 0.0.0.0:P} <b>检不出</b> ⇒ ⇒ 必须探<b>两个地址</b>。
 *
 * <h2>本类怎么消除它</h2>
 * <ol>
 *   <li><b>自己挑端口</b>：从<b>本域专用端口段</b>（{@link #FIRST}–{@link #LAST}）里挑，
 *       <b>避开 OS 临时端口段</b> ⇒ ⇒ 与 Docker 随机发布端口<b>不同段</b>；</li>
 *   <li><b>挑之前先在两个地址上 bind 自检</b>：{@code 0.0.0.0:P} 与 {@code 127.0.0.1:P}
 *       都可绑才算空闲（macOS 上二者<b>可以共存</b>，都带 {@code SO_REUSEADDR}
 *       ⇒ 只探一个会漏）⇒ ⇒ 交出去的是<b>确定归我们自己</b>的端口。</li>
 * </ol>
 *
 * <h2>怎么用</h2>
 * <p>{@code @SpringBootTest(webEnvironment = DEFINED_PORT)} ＋
 * {@code @DynamicPropertySource} 里 {@code registry.add("server.port", RealTomcatPort::pick)}
 * ⇒ ⇒ 测试不再让 OS 盲选 ⇒ 端口归属确定。
 *
 * <h2>段归属</h2>
 * <p>global 端口段表（{@code docs/DRIFT/global/vs-common.md} §4.10.19.3）把 {@code 22200–22299}
 * 预分配给 <b>users 域</b> ⇒ ⇒ 本类取其中 {@code 22210–22219}（user-api 子段）
 * ⚠️ <b>共享层段表在 G4 写权面内</b> ⇒ 本类只按<b>已预分配</b>的段取号，<b>不改共享层台账</b>。
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b> ⇒ <b>不存在语义对照义务</b>。
 *
 * <h2>惯例来源</h2>
 * <p>G3 域 favorite-api 的同名类（{@code service/favorite/favorite-api/src/test/.../support/RealTomcatPort.java}）
 * ⇒ ⇒ 本类沿用其形状 ⇒ 跨域形状一致。
 */
public final class RealTomcatPort {

    /** 本模块真 Tomcat 测试专用端口段下界（含）。 */
    public static final int FIRST = 22210;

    /** 本模块真 Tomcat 测试专用端口段上界（含）。 */
    public static final int LAST = 22219;

    /** 单次 bind 自检的超时（bind 是本地的，给 1s 足够）。 */
    private static final int BIND_TIMEOUT_MS = 1000;

    private RealTomcatPort() {
    }

    /**
     * 挑一个<b>确实可绑</b>的端口。
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
                "真 Tomcat 端口段 " + FIRST + "-" + LAST + " 已被占满"
                        + "（本机并行会话太多？）⇒ 换一段端口再试");
    }

    /**
     * 该端口能不能<b>同时</b>满足两件事：{@code 0.0.0.0:P} 可绑 + {@code 127.0.0.1:P} 可绑。
     *
     * <p>📌 <b>包级可见</b>：域级守门测试直接调它断言「被占 ⇒ 判定为不可用」。
     *
     * @param port 端口号
     * @return {@code true} = 两个地址都能绑（= 没有第三方持有者）
     */
    static boolean isFree(int port) {
        try (ServerSocket wildcard = new ServerSocket();
             ServerSocket specific = new ServerSocket()) {
            wildcard.setReuseAddress(true);
            wildcard.bind(new InetSocketAddress(port), 1);
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
     * @return 人类可读的占用者描述（查不到时返回说明文字）
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
