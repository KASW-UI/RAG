package com.rag.hot.hot.rpc.support;

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
 * hot-rpc 真进程的<b>端口选取 + 归属诊断</b>（gRPC server 专用）。
 *
 * <h2>为什么不能直接用 {@code grpc.server.port=0}（或 {@code freePort()}）</h2>
 * <p>{@code 0} ＝ 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑一个。
 * ⚠️ <b>Docker 随机发布容器端口用的就是同一段</b>（实测本机 {@code Sea-kafka} 发布
 * {@code 0.0.0.0:49992}· {@code Sea-redis} 发布 {@code 49380}，两者都在该段内），
 * 而本机是<b>多会话并行</b>的机器（别的会话的容器随时可能在 {@code 0.0.0.0:某端口} 上挂 PG / Redis）。
 * <p>⇒ 后果与 favorite / comment / article 域的同款：
 * <ul>
 *   <li>别的容器占着 {@code 0.0.0.0:P} ⇒ 我们再绑 {@code 0.0.0.0:P} ⇒ <b>EADDRINUSE</b>
 *       ⇒ 被测进程起不来 ⇒ <b>整个测试类启动失败</b>
 *       ⇒ 表现为「这条用例偶尔红、换个时间又绿」的<b>环境性抖动</b>
 *       ⇒ ⚠️ 且症状会被误读成「被测行为坏了」（G2 在 message 域实测到同形态的
 *       {@code BindException: Address already in use} ⇒ 2026-10-28）；</li>
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
 * <h2>⚠️ 为什么是 {@code 22500–22579}（不是随便挑一段）</h2>
 * <p>global 端口段表（{@code docs/DRIFT/global/vs-common.md} §4.10.19.3）把
 * {@code 22500–22599} 整段<b>预分配给 hot 域</b> ⇒ ⇒ <b>本段就是本域的</b> ⇒ ⇒ 本次把它<b>内部再切一刀</b>：
 * <ul>
 *   <li><b>{@code 22500–22579}</b>（80 口）＝ <b>本类生产用段</b>（真进程 gRPC server 端口）；</li>
 *   <li><b>{@code 22580–22599}</b>（20 口）＝ <b>只给守门用</b>的脚手架段
 *       （{@code RealGrpcPortTest} 的「被占样本」端口）。</li>
 * </ul>
 * ⇒ ⇒ **切分的理由**（照 comment / article 域的同款做法）：守门要造一个「已被占」的样本，
 * 而**全量套件里上一个测试类的 gRPC server 还活着**（Spring 会缓存测试上下文）
 * ⇒ ⇒ 若守门去占<b>生产段里的</b>端口，就可能撞上那个还活着的 server
 * ⇒ ⇒ **守门段必须与生产段不相交** ⇒ ⇒ 切一刀即可，**零跨域协调**。
 * <p>⚠️ 与本域<b>容器宿主口段</b>（{@code 22940–22969}，见 {@link RealContainerHostPort}）
 * <b>零重叠</b> ⇒ ⇒ 两个机制互不抢口。
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本类是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b> ⇒ <b>不存在语义对照义务</b>。
 * 它只影响「RAG 侧测试能不能稳定跑到被测代码」，<b>不影响任何对外行为</b>。
 *
 * <h2>⚠️ 一条诚实的边界（TOCTOU）</h2>
 * <p>{@link #pick()} 返回时端口是空闲的，但<b>调用方真正 bind 它之前</b>存在一个窗口
 * ⇒ ⇒ 别的进程若恰好在这窗口里抢走同一个端口，仍可能撞 {@code EADDRINUSE}。
 * ⇒ ⇒ <b>本类只能把概率压低，不能归零</b> ⇒ ⇒ 这也是为什么本段要<b>独占</b>给本域
 * ⇒ ⇒ 而不是「大家共用一个大段、各自碰运气」。
 *
 * <h2>⚠️ 域作用域</h2>
 * <p>同款 {@code port=0} 暴露面<b>在别的域也有</b> ⇒ 需要<b>各自复制</b>本类
 * ⇒ ⇒ 登记在 {@code global} 收口域（本类属 hot 域的<b>域内文件</b>）。
 */
public final class RealGrpcPort {

    /**
     * hot-rpc 真进程测试专用端口段（<b>刻意避开 OS 临时端口段 49152–65535</b>）。
     *
     * <p>⚠️ <b>为什么是 22500–22579</b>：global 端口段表（§4.10.19.3）把
     * {@code 22500–22599} 预分配给 <b>hot 域</b> ⇒ ⇒ 本类只取其<b>下 80 口</b>，
     * <b>上 20 口（22580–22599）留给守门脚手架</b>（见 {@code RealGrpcPortTest}）
     * ⇒ ⇒ 与其它段**零重叠**：favorite-api 21300–21399 · favorite 探针 21400–21599 ·
     * like 21600–21699 · message 21700–21799 · task 21800–21899 ·
     * favorite-api 脚手架 21950–21999 · favorite-rpc 脚手架 22050–22099 ·
     * favorite-rpc 22100–22199 · users 22200–22299 · follow 22300–22399 ·
     * article 22400–22499 · <b>本段 22500–22579</b> · comment-rpc 22600–22699 ·
     * article 容器宿主口 22850–22879 · comment 容器宿主口 22880–22909 ·
     * favorite 容器宿主口 22910–22939 · hot 容器宿主口 22940–22969。
     *
     * <p>⚠️ 这是一段<b>共享</b>端口：别的会话若也起 hot-rpc 测试，会靠
     * {@link #isFree(int)} 的自检<b>各拿各的</b>（同一段内先到先得）。
     */
    public static final int FIRST = 22500;

    /** 端口段上界（含）。 */
    public static final int LAST = 22579;

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
     * 挑一个<b>确实可绑</b>的端口，且<b>排除</b>指定的那个。
     *
     * <p>🔴 <b>为什么需要这个重载</b>（2026-10-30 · 补 Actuator 载体时新增）：
     * 补上载体后，rpc 进程<b>同时</b>要占<b>两个</b>口 ——
     * <ul>
     *   <li><b>业务面</b>：gRPC server 端口（{@code grpc.server.port}）；</li>
     *   <li><b>观测面</b>：Actuator 的 servlet 容器端口（{@code server.port}）
     *       ⇒ 默认值 = Go 的观测端口（article 9092 / favorite 16062 / hot 19091），
     *       探针并行起时用 env 覆盖成别的口。</li>
     * </ul>
     * ⇒ 两个面<b>必须占不同的口</b>（Go 侧本来就是两个独立端口）
     * ⇒ ⇒ 挑第二个时必须把第一个<b>排除掉</b>
     * ⇒ ⇒ 否则两次 {@code pick()} 可能挑到<b>同一个</b>空口 ⇒ 第二个 bind 必
     * {@code EADDRINUSE} ⇒ 表现为「真启动守门<b>偶发红</b>」。
     *
     * <p>⚠️ 本类原名带「Grpc」，但本域的<b>观测面</b>端口也走本段 ——
     * 这是<b>刻意</b>的：两个面共用同一段 ⇒ 天然互不跨域，也不会与别的域抢口。
     *
     * @param forbidden 不许挑中的端口（通常是已经挑给另一个面的那个）
     * @return 端口号（一定在 {@link #FIRST}–{@link #LAST} 内，且 ≠ {@code forbidden}）
     * @throws IllegalStateException 整段都被占满时
     */
    public static int pick(int forbidden) {
        for (int port = FIRST; port <= LAST; port++) {
            if (port != forbidden && isFree(port)) {
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
     *       {@code EADDRINUSE} ⇒ <b>被测进程直接起不来</b>；</li>
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
     * 「被占 ⇒ 判定为不可用」⇒ 这样守门测试<b>不需要去抢生产端口段里的端口</b>
     * （全量套件里端口段可能已被上一个测试类的 server 占着）。
     *
     * @param port 端口号
     * @return {@code true} ＝ 两个地址都能绑（= 没有第三方持有者）
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
