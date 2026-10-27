package com.rag.favorite.favorite.rpc.support;

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
 * favorite-rpc 真进程的<b>端口选取 + 归属诊断</b>（gRPC server 专用）。
 *
 * <h2>为什么不能直接用 {@code grpc.server.port=0}</h2>
 * <p>{@code 0} = 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑一个。
 * ⚠️ <b>Docker 随机发布容器端口用的就是同一段</b>，而本机是<b>多会话并行</b>的机器
 * （别的会话的容器随时可能在 {@code 0.0.0.0:某端口} 上挂一个 PG / Redis）。
 * <p>⇒ 后果与 favorite-api 的 {@code RealTomcatPort} 同款：
 * <ul>
 *   <li>别的容器占着 {@code 0.0.0.0:P} ⇒ 我们再绑 {@code 0.0.0.0:P} ⇒ <b>EADDRINUSE</b>
 *       ⇒ devh 的 gRPC server 起不来 ⇒ <b>整个测试类启动失败</b>
 *       ⇒ 表现为「这条用例偶尔红、换个时间又绿」的<b>环境性抖动</b>；</li>
 *   <li>别的容器占着 {@code 127.0.0.1:P}（更具体）⇒ macOS/BSD 上我们绑通配地址<b>照样成功</b>
 *       ⇒ 但任何连 {@code 127.0.0.1:P} 的客户端都会<b>命中那个更具体的监听者</b>
 *       ⇒ 请求打不到我们 ⇒ 「看着像被测代码炸了，其实压根没被请求到」。</li>
 * </ul>
 *
 * <h2>本类怎么消除它</h2>
 * <ol>
 *   <li><b>自己挑端口</b>：从<b>本域专用端口段</b>（{@link #FIRST}–{@link #LAST}）里挑，
 *       <b>避开 OS 临时端口段</b> ⇒ 与 Docker 随机发布端口<b>不同段</b>；</li>
 *   <li><b>挑之前先在两个地址上 bind 自检</b>：{@code 0.0.0.0:P} 与 {@code 127.0.0.1:P}
 *       都可绑才算空闲（macOS 上二者<b>可以共存</b>，都带 {@code SO_REUSEADDR}
 *       ⇒ 只探一个会漏）；</li>
 *   <li>⇒ 交出去的是<b>确定归我们自己</b>的端口。</li>
 * </ol>
 *
 * <h2>⚠️ 端口段与 favorite-api 的关系</h2>
 * <p>favorite-api 用 {@code 21300–21399}（{@code RealTomcatPort}）⇒
 * <b>本类刻意用 {@code 22100–22199}</b> ⇒ 两个段<b>互不重叠</b>
 * ⇒ 两域的测试并发跑时不会互相抢端口。
 *
 * <h2>🔴 2026-10-27：本段从 {@code 21400–21499} 迁到 {@code 22100–22199}</h2>
 * <p>原段 {@code 21400–21499} <b>嵌套在 favorite 域的「探针段」{@code 21400–21599} 内</b>
 * （global 端口段表 §4.10.19.3）⇒ ⇒ <b>两边都以为对方不在自己段里</b>
 * ⇒ ⇒ 这就是「段表必须把探针段也列进去」的全部意义（表里少一行 = 隐性重叠）。
 * <p>⇒ ⇒ 迁到 global 段表<b>预分配给 favorite-rpc</b>的 {@code 22100–22199}
 * （2026-10-27 实测整段可绑 100/100）⇒ ⇒
 * <b>探针段 {@code 21400–21599} 不动</b>、<b>脚手架段 {@code 22050–22099} 不动</b>
 * ⇒ ⇒ {@code tools/probe-gate/favorite/check-port-ownership.sh} 的段断言<b>也不用动</b>
 * （它守的是**探针工具自己的段**，不是本类的段）。
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b> ⇒ <b>不存在语义对照义务</b>。
 * 它只影响「RAG 侧测试能不能稳定跑到被测代码」，<b>不影响任何对外行为</b>。
 *
 * <h2>⚠️ 域作用域</h2>
 * <p>同款 {@code port=0} 暴露面<b>在别的域也有</b> ⇒ 需要<b>各自复制</b>本类
 * ⇒ 登记在 {@code global} 收口域（本类属 favorite 域的<b>域内文件</b>）。
 */
public final class RealGrpcPort {

    /**
     * favorite-rpc 真进程测试专用端口段（<b>刻意避开 OS 临时端口段 49152–65535</b>）。
     *
     * <p>⚠️ 这是一段<b>共享</b>端口：别的会话若也起 favorite-rpc 测试，会靠
     * {@link #isFree(int)} 的自检<b>各拿各的</b>（同一段内先到先得）。
     */
    public static final int FIRST = 22100;

    /** 端口段上界（含）。 */
    public static final int LAST = 22199;

    /** 单次 bind 自检的超时（bind 本身是本地的，给 1s 足够）。 */
    private static final int BIND_TIMEOUT_MS = 1000;

    private RealGrpcPort() {
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
                "真进程测试端口段 " + FIRST + "-" + LAST + " 已被占满"
                        + "（本机并行会话太多？）⇒ 换一段端口再试");
    }

    /**
     * 该端口能不能<b>同时</b>满足两件事：
     * <ol>
     *   <li><b>{@code 0.0.0.0:P} 可绑</b> —— gRPC server 将来要绑的就是通配地址
     *       ⇒ 若已有人占着通配地址（例如<b>另一个会话的容器</b>），绑定会
     *       {@code EADDRINUSE} ⇒ <b>测试类直接起不来</b>；</li>
     *   <li><b>{@code 127.0.0.1:P} 可绑</b> —— 客户端连的就是 127.0.0.1
     *       ⇒ 若已有人占着这个<b>更具体</b>的地址，我们的通配绑定<b>照样成功</b>，
     *       但客户端会被<b>抢走</b> ⇒ 请求打不到我们。</li>
     * </ol>
     *
     * <p>⚠️ <b>两个都要探，缺一不可</b>：只探 {@code 127.0.0.1} ⇒ 会漏掉「另一个会话的容器
     * 占着通配地址」⇒ 两个测试类拿到<b>同一个端口</b> ⇒ 后一个类启动时失败。
     *
     * <p>⚠️ macOS/BSD 上 {@code 127.0.0.1:P} 与 {@code 0.0.0.0:P} <b>可以共存</b>（都带
     * {@code SO_REUSEADDR}）⇒ 本方法<b>同时绑住两个</b>来验证「两个地址都没有第三方持有者」。
     *
     * <p>📌 <b>包级可见</b>：守门测试 {@code RealGrpcPortTest} 直接调它断言
     * 「被占 ⇒ 判定为不可用」⇒ 这样守门测试<b>不需要去抢端口段里的端口</b>
     * （全量套件里端口段可能已被上一个测试类的 server 占着）。
     *
     * @param port 端口号
     * @return {@code true} = 两个地址都能绑（= 没有第三方持有者）
     */
    static boolean isFree(int port) {
        try (ServerSocket wildcard = new ServerSocket();
             ServerSocket specific = new ServerSocket()) {
            wildcard.setReuseAddress(true);
            wildcard.bind(new InetSocketAddress(port), 1);   // 0.0.0.0 —— gRPC server 将来要绑的那个
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
