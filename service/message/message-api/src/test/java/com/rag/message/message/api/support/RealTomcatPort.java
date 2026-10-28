package com.rag.message.message.api.support;

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
 * 真 Tomcat 测试的<b>端口选取 + 归属诊断</b>。
 *
 * <h2>为什么不能用 Spring 的 {@code RANDOM_PORT}</h2>
 * <p>{@code RANDOM_PORT} = 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑一个。
 * ⚠️ <b>Docker 随机发布容器端口用的就是同一段</b>，而本机是<b>多会话并行</b>的机器
 * （别人的容器随时可能在 {@code 127.0.0.1:某端口} 上挂一个 PG / Redis）。
 * <p>更糟的是 macOS/BSD 的 socket 语义：
 * <ul>
 *   <li>{@code 127.0.0.1:P} 与 {@code 0.0.0.0:P} <b>可以共存</b>（都带 {@code SO_REUSEADDR}）
 *       ⇒ <b>Tomcat 绑 {@code 0.0.0.0:P} 会照样启动成功</b>，启动日志照样打「started on port P」
 *       ⇒ <b>它并不知道这个端口号还有另一个更具体的监听者</b>；</li>
 *   <li>客户端连 {@code 127.0.0.1:P} ⇒ <b>命中更具体的那个</b>（实测：{@code specific-127.0.0.1}
 *       而不是 {@code wildcard-0.0.0.0}）。</li>
 * </ul>
 * ⇒ 于是客户端把 HTTP 请求打到了一个<b>不是 HTTP 的服务</b>上（2026-10-25 实测那次打到了
 * <b>PostgreSQL</b>：别的会话的 {@code cdifftest-postgres} 占了 {@code 127.0.0.1:55440}）
 * ⇒ 对端协议不匹配 ⇒ <b>一个字节都不回就关连接</b>
 * ⇒ 客户端报 {@code IOException: HTTP/1.1 header parser received no bytes}
 * ⇒ <b>看起来像「被测代码炸了」，其实是被测代码压根没被请求到</b>。
 *
 * <h2>本类怎么消除它</h2>
 * <ol>
 *   <li><b>自己挑端口</b>：从<b>本域专用端口段</b>（{@link #FIRST}–{@link #LAST}）里挑，
 *       <b>避开 OS 临时端口段</b> ⇒ 与 Docker 随机发布端口<b>不同段</b>；</li>
 *   <li><b>挑之前先在 127.0.0.1 上 bind 自检</b>：被占 ⇒ 跳过该端口
 *       （实测：{@code bind('127.0.0.1', 55440)} 在 Docker 占用时返回
 *       {@code EADDRINUSE} ⇒ <b>这个自检能可靠识别「端口被别人的容器占了」</b>）；</li>
 *   <li>⇒ 交出去的是<b>确定归我们自己</b>的端口（127.0.0.1 上没有任何别的监听者），
 *       <b>客户端连 127.0.0.1:P 只可能命中我们的 Tomcat</b>。</li>
 * </ol>
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b> ⇒ <b>不存在语义对照义务</b>。
 * 它只影响「RAG 侧测试能不能稳定跑到被测代码」，<b>不影响任何对外行为</b>。
 *
 * <h2>⚠️ 域作用域</h2>
 * <p>同款 {@code RANDOM_PORT} 暴露面<b>在别的域也有</b>（本机实测别的会话的容器就占着
 * {@code 127.0.0.1:55440}）⇒ 需要<b>各自复制</b>本类 ⇒ 登记在 {{@code global}} 收口域。
 * <p>⚠️ <b>为什么不能放到共享层共用一份</b>：{@code service/common} <b>不发布 test-jar</b>
 * （实测：{@code service/common/pom.xml} 无 {@code maven-jar-plugin} 的 test-jar 目标）
 * ⇒ 业务模块的 test scope <b>拿不到</b>共享层的测试类 ⇒ 只能复制。
 */
public final class RealTomcatPort {

    /**
     * 本域真 Tomcat 测试专用端口段（<b>刻意避开 OS 临时端口段 49152–65535</b>）。
     *
     * <p>⚠️ 这是一段<b>本域专用</b>端口：别的会话若也起 message-api 测试，会靠
     * {@link #isFree(int)} 的自检<b>各拿各的</b>（同一段内先到先得）。
     * <p>⚠️ <b>选段口径</b>：本段 [21700–21799] 刻意避开
     * favorite 的 <b>21300–21399</b>、favorite 探针的 <b>21400–21599</b>、
     * favorite 脚手架的 <b>21950–21999</b>、OS 临时端口段 <b>49152–65535</b>
     * （Docker 随机发布端口就在那一段）。
     * ⇒ <b>全局端口段表归 {@code global} 收口域协调</b>（本域只登记自己这段）。
     */
    public static final int FIRST = 21700;

    /** 端口段上界（含）。 */
    public static final int LAST = 21799;

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
                "真 Tomcat 测试端口段 " + FIRST + "-" + LAST + " 已被占满"
                        + "（本机并行会话太多？）⇒ 换一段端口再试");
    }

    /**
     * 该端口能不能<b>同时</b>满足两件事：
     * <ol>
     *   <li><b>{@code 0.0.0.0:P} 可绑</b> —— Tomcat 将来要绑的就是通配地址
     *       ⇒ 若已有人占着通配地址（例如<b>另一个 Tomcat</b>，同 JVM 里上一个测试类的上下文还活着），
     *       Tomcat 会直接抛 {@code PortInUseException} <b>启动失败</b>
     *       ⇒ 这是<b>响亮的失败</b>，但仍要提前避开。</li>
     *   <li><b>{@code 127.0.0.1:P} 可绑</b> —— 客户端连的就是 127.0.0.1
     *       ⇒ 若已有人占着这个<b>更具体</b>的地址（例如<b>别的会话的 Docker 容器发布端口</b>），
     *       Tomcat 的通配绑定<b>照样成功</b>，但客户端会被<b>抢走</b> ⇒ 请求打不到我们 ⇒ 零字节即关。</li>
     * </ol>
     *
     * <p>⚠️ <b>两个都要探，缺一不可</b>（2026-10-25 实测踩到）：
     * 只探 {@code 127.0.0.1} ⇒ 会漏掉「另一个 Tomcat 占着通配地址」⇒
     * 两个测试类拿到<b>同一个端口</b> ⇒ 后一个类启动时 {@code PortInUseException} ⇒ <b>测试直接红</b>。
     *
     * <p>⚠️ macOS/BSD 上 {@code 127.0.0.1:P} 与 {@code 0.0.0.0:P} <b>可以共存</b>（都带
     * {@code SO_REUSEADDR}）⇒ 本方法<b>同时绑住两个</b>来验证「两个地址都没有第三方持有者」。
     *
     * <p>📌 <b>包级可见</b>：守门测试 {@code RealTomcatPortTest} 直接调它断言
     * 「被占 ⇒ 判定为不可用」⇒ 这样守门测试<b>不需要去抢端口段里的端口</b>
     * （全量套件里端口段可能已被上一个 Spring 测试类的 Tomcat 占着）。
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
