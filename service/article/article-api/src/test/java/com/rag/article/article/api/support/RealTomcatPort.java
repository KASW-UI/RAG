package com.rag.article.article.api.support;

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
 * article-api 真 Tomcat 测试的<b>端口选取 + 归属诊断</b>。
 *
 * <h2>为什么 article-api 需要它</h2>
 * <p>本模块此前的 <b>8 个测试类全是 {@code webEnvironment = MOCK}</b>（实测：
 * {@code ArticleApiAssemblyWiringTest} / {@code ArticleApiFormParamLimitTest} 用
 * {@code RANDOM_PORT}，其余 6 个用 {@code MOCK}）
 * ⇒ <b>真 Tomcat 覆盖率是零</b> ⇒ 而 MockMvc 有三处系统性地测不到：
 * <ol>
 *   <li><b>Content-Type 的容器归一化</b> —— 真 Tomcat 把 {@code text/plain; charset=utf-8}
 *       归一化成 {@code text/plain;charset=utf-8}（分号后无空格）⇒ MockMvc 里断言
 *       「带空格」的写法<b>在 MockMvc 下会绿、在真容器下会红</b>；</li>
 *   <li><b>Content-Length</b> —— 由容器依据实际写入字节数计算，MockMvc 不参与；</li>
 *   <li><b>body 是否被容器补写</b> —— 405 这类「body 为 null」的响应，容器会不会自作主张
 *       补空 body 或补 Content-Type，只有真容器说了算（DRIFT-024 当初漏网即因此）。</li>
 * </ol>
 * ⇒ 本类把「真容器要绑哪个口」这件事固定下来，使
 * {@code ArticleApiFailurePathMatrixTest} 能在<b>真 Tomcat</b>上跑失败路径矩阵。
 *
 * <h2>为什么不能用 Spring 的 {@code RANDOM_PORT}</h2>
 * <p>{@code RANDOM_PORT} 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑一个。
 * ⚠️ <b>Docker 随机发布容器端口用的就是同一段</b>，而本机是<b>多会话并行</b>的机器。
 * <p>更糟的是 macOS/BSD 的 socket 语义：
 * <ul>
 *   <li>{@code 127.0.0.1:P} 与 {@code 0.0.0.0:P} <b>可以共存</b>（都带 {@code SO_REUSEADDR}）
 *       ⇒ <b>Tomcat 绑 {@code 0.0.0.0:P} 会照样启动成功</b>，启动日志照样打「started on port P」
 *       ⇒ <b>它并不知道这个端口号还有另一个更具体的监听者</b>；</li>
 *   <li>客户端连 {@code 127.0.0.1:P} ⇒ <b>命中更具体的那个</b>。</li>
 * </ul>
 * ⇒ 客户端把 HTTP 请求打到了一个<b>不是 HTTP 的服务</b>上 ⇒ <b>一个字节都不回就关连接</b>
 * ⇒ 客户端报 {@code IOException: HTTP/1.1 header parser received no bytes}
 * ⇒ <b>看起来像「被测代码炸了」，其实是被测代码压根没被请求到</b>。
 *
 * <h2>本类怎么消除它</h2>
 * <ol>
 *   <li><b>自己挑端口</b>：从<b>本段专用端口段</b>（{@link #FIRST}–{@link #LAST}）里挑，
 *       <b>避开 OS 临时端口段</b>；</li>
 *   <li><b>挑之前先在 127.0.0.1 上 bind 自检</b>：被占 ⇒ 跳过该端口；</li>
 *   <li>⇒ 交出去的是<b>确定归我们自己</b>的端口（127.0.0.1 上没有任何别的监听者）。</li>
 * </ol>
 *
 * <h2>⚠️ 端口段口径（2026-10-29 · R2 lane · article 域）</h2>
 * <p>本段 = <b>{@code 18141–18145}</b>（5 口），由本轮 lane 协调分配：
 * <ul>
 *   <li><b>{@code 18141–18145}</b> = article 域真容器测试段（<b>本类</b>）；</li>
 *   <li><b>{@code 18146–18150}</b> = hot 域真容器测试段（<code>hot-api</code> 侧同名类）。</li>
 * </ul>
 * ⚠️ <b>为什么不用 global 段表里 article 域的 {@code 22400–22499}</b>：
 * 该段已被 <b>article-rpc 整段吃满</b>（实测）——
 * <ul>
 *   <li>{@code 22400–22479} = {@code article-rpc/.../support/RealGrpcPort.java:94-97}
 *       （真进程 gRPC server 端口）；</li>
 *   <li>{@code 22480–22499} = {@code article-rpc/.../support/RealGrpcPortTest.java:74-77}
 *       （守门脚手架段，用来造「端口已被占」的样本）；</li>
 * </ul>
 * ⇒ <b>段内没有留给 article-api 的切片</b> ⇒ 本轮用 lane 分配的 {@code 18141–18145}。
 * 📌 global 段表（{@code docs/DRIFT/global/vs-common.md} §4.10.19.3，G4 写权）里
 * article 域那一行仍是「预分配 22400–22499」⇒ <b>该表需要 G4 在收口时补记本段</b>
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
     * article-api 真 Tomcat 测试专用端口段（<b>刻意避开 OS 临时端口段 49152–65535</b>）。
     *
     * <p>⚠️ 这是一段<b>本 lane 内本域专用</b>端口：别的会话若也起 article-api 测试，
     * 会靠 {@link #isFree(int)} 的自检<b>各拿各的</b>（同一段内先到先得）。
     */
    public static final int FIRST = 18141;

    /** 端口段上界（含）。 */
    public static final int LAST = 18145;

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
                "article-api 真 Tomcat 测试端口段 " + FIRST + "-" + LAST + " 已被占满"
                        + "（本机并行会话太多？）⇒ 换一段端口再试");
    }

    /**
     * 该端口能不能<b>同时</b>满足两件事：
     * <ol>
     *   <li><b>{@code 0.0.0.0:P} 可绑</b> —— Tomcat 将来要绑的就是通配地址
     *       ⇒ 若已有人占着通配地址，Tomcat 会抛 {@code PortInUseException} <b>启动失败</b>；</li>
     *   <li><b>{@code 127.0.0.1:P} 可绑</b> —— 客户端连的就是 127.0.0.1
     *       ⇒ 若已有人占着这个<b>更具体</b>的地址（别的会话的 Docker 发布端口），
     *       Tomcat 的通配绑定<b>照样成功</b>，但客户端会被<b>抢走</b> ⇒ 请求打不到我们。</li>
     * </ol>
     *
     * <p>⚠️ <b>两个都要探，缺一不可</b>：只探 {@code 127.0.0.1} ⇒ 会漏掉「另一个 Tomcat 占着通配地址」
     * ⇒ 两个测试类拿到<b>同一个端口</b> ⇒ 后一个类启动时 {@code PortInUseException}。
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
