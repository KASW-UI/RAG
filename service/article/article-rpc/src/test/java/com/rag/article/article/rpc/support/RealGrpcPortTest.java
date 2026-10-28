package com.rag.article.article.rpc.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RealGrpcPort} 的守门测试 —— 防止「端口选取」这个机制<b>自己被改坏</b>，
 * 以及防止「落回 OS 临时端口段」这件事<b>悄悄回来</b>。
 *
 * <h2>为什么必须有它</h2>
 * <p>本类守的是「article-rpc 的 gRPC server 不用 {@code freePort()} / {@code port=0}」这条：
 * {@code 0} 让 OS 从<b>临时端口段</b>（macOS：49152–65535）里挑 ⇒ 那是
 * <b>Docker 随机发布容器端口用的同一段</b> ⇒ 别的会话的容器一旦先占住
 * {@code 0.0.0.0:P}，我们再绑同一个端口 ⇒ <b>EADDRINUSE</b>
 * ⇒ 被测进程起不来 ⇒ <b>整个真启动守门失败</b>
 * ⇒ 表现为「这条用例偶尔红、换个时间又绿」的<b>环境性抖动</b>。
 *
 * <h2>⚠️ 断言口径：<b>不靠「抢生产端口段里的端口」</b></h2>
 * <p>「占住生产端口段的第一个端口，然后断言 {@code pick()} 绕开它」这种写法在<b>全量套件</b>里
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
 *
 * <h2>⚠️ 脚手架段为什么是 {@code 22480–22499}（不是随便挑一段）</h2>
 * <p>global 端口段表把 {@code 22400–22499} <b>整段</b>预分配给 <b>article 域</b>
 * ⇒ ⇒ <b>本域自用</b> ⇒ ⇒ 这里取它的<b>上 20 口</b>做脚手架段，
 * <b>下 80 口（22400–22479）留给真进程 gRPC 端口</b>
 * ⇒ ⇒ <b>不碰 global 的未分配预留段</b>（21950–21999 / 22050–22099 / 22970–22999 等）
 * ⇒ ⇒ **零跨域协调**。
 *
 * <h2>🔴 最后两条是「源扫」—— 它们才是真正<b>堵住缺口</b>的那部分</h2>
 * <p>前 4 条守的是「<b>选取逻辑</b>对不对」；但它们<b>看不见「有人又写回了
 * {@code port=0}」</b>这种回潮 ⇒ ⇒ 所以再加两条<b>扫源码</b>的断言：
 * <ul>
 *   <li>① <b>禁止清单</b>：本模块测试源码里（<b>剥掉注释后</b>）不允许再出现
 *       {@code grpc.server.port=0} / {@code --grpc.server.port=0} /
 *       {@code new ServerSocket(0)} / {@code freePort(} ⇒ ⇒
 *       <b>三种回潮形态一次堵住</b>（属性形态 / 进程参数形态 / 辅助方法形态）；</li>
 *   <li>② <b>守恒</b>：凡（剥注释后）提到 {@code grpc.server.port} 的文件，
 *       <b>必须</b>引用 {@link RealGrpcPort} ⇒ ⇒ 「新增一个测试类却忘了走专用段」会被抓住。</li>
 * </ul>
 * ⇒ ⇒ <b>缺口被机械堵住，不靠人记得</b>（与本域 {@code RealContainerHostPortTest}
 * 那条「容器宿主口」的源扫同款思路）。
 */
class RealGrpcPortTest {

    /**
     * 脚手架端口段：只给本测试用，<b>不与</b> {@link RealGrpcPort} 的生产端口段
     * （22400–22479）重叠，<b>也不与</b>本域留给 Testcontainers 的容器宿主口段
     * （22850–22869 ＋ 22870–22879）重叠。
     */
    private static final int SCRATCH_FIRST = 22480;

    /** 脚手架端口段上界。 */
    private static final int SCRATCH_LAST = 22499;

    // ── 禁止清单（源扫）────────────────────────────────────────────────────────
    // ⚠️ **刻意用拼接构造** ⇒ ⇒ 这些字面量**不会连续出现在本文件里**
    // ⇒ ⇒ 源扫**不会把自己扫成违规**（否则守门会永远红在自己身上）。

    /** 禁止：Spring 属性形态 {@code grpc.server.port=0}。 */
    private static final String BANNED_PROPERTY = "grpc.server.port=" + "0";

    /** 禁止：进程参数形态 {@code --grpc.server.port=0}。 */
    private static final String BANNED_ARGUMENT = "--grpc.server.port=" + "0";

    /** 禁止：裸取临时口 {@code new ServerSocket(0)}。 */
    private static final String BANNED_SERVER_SOCKET = "new ServerSocket(" + "0)";

    /** 禁止：进程内 gRPC server 形态 {@code ServerBuilder.forPort(0)}。 */
    private static final String BANNED_FOR_PORT = "forPort(" + "0)";

    /** 禁止：裸取临时口的辅助方法 {@code freePort(}。 */
    private static final String BANNED_FREE_PORT = "freePort" + "(";

    /** 识别「本模块给 gRPC server 指定端口」的源码特征（剥注释后）。 */
    private static final String GRPC_PORT_KEY = "grpc.server.port";

    /** 识别「走本域专用段」的源码特征（剥注释后）。 */
    /** 识别「进程内 gRPC server」的源码特征（剥注释后）。 */
    private static final String IN_PROCESS_SERVER_KEY = "ServerBuilder.forPort";

    /**
     * 写死端口号的两种形态（正则里的点与括号<b>都转义</b>）
     * ⇒ ⇒ <b>判据自身不会被自己扫到</b>（源码里是带反斜杠的形态 ⇒ 与字面量形态逐字不同）。
     */
    private static final java.util.regex.Pattern LITERAL_PORT =
            java.util.regex.Pattern.compile("grpc\\.server\\.port=(\\d+)|forPort\\((\\d+)");

    private static final String REAL_PORT_USAGE = "RealGrpcPort";

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

    // ── 源扫：把「回潮」堵住 ─────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 本模块测试源码里不允许再出现「落回 OS 临时端口段」的三种形态（源扫 · 剥注释）")
    void noTestSourceFallsBackToTheEphemeralPortSegment() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path file : testSourceFiles()) {
            String code = RealContainerHostPortTest.stripComments(read(file));
            for (String banned : new String[]{BANNED_PROPERTY, BANNED_ARGUMENT,
                    BANNED_FOR_PORT, BANNED_SERVER_SOCKET, BANNED_FREE_PORT}) {
                if (code.contains(banned)) {
                    hits.add(rel(file) + " ⇒ 含「" + banned + "」");
                }
            }
        }
        assertTrue(hits.isEmpty(),
                "这些测试源码又落回 OS 临时端口段了（49152–65535 = Docker 发布口同段）"
                        + "⇒ 真启动守门会偶发 EADDRINUSE ⇒ 必须走 RealGrpcPort：\n  "
                        + String.join("\n  ", hits));
    }

    @Test
    @DisplayName("🔴 凡是给 gRPC server 指定端口的地方，端口必须落在本域专用段内（源扫 · 守恒）")
    void everyGrpcServerPortDeclarationStaysInsideTheDedicatedSegment() throws IOException {
        List<String> violations = new ArrayList<>();
        int declaringFiles = 0;
        for (Path file : testSourceFiles()) {
            String code = RealContainerHostPortTest.stripComments(read(file));
            boolean declares = code.contains(GRPC_PORT_KEY) || code.contains(IN_PROCESS_SERVER_KEY);
            if (!declares) {
                continue;
            }
            declaringFiles++;
            // ① 走 RealGrpcPort（动态挑口）⇒ pick() 保证落在 [FIRST, LAST] ⇒ 合规
            if (code.contains(REAL_PORT_USAGE)) {
                continue;
            }
            // ② 写死字面量口 ⇒ 该字面量必须落在 [FIRST, LAST] 内（否则仍可能落 OS 临时段）
            java.util.List<Integer> literals = literalPorts(code);
            if (literals.isEmpty()) {
                violations.add(rel(file) + " ⇒ 指定了端口，但既没走 RealGrpcPort、也读不出字面量口");
                continue;
            }
            for (Integer p : literals) {
                if (p < RealGrpcPort.FIRST || p > RealGrpcPort.LAST) {
                    violations.add(rel(file) + " ⇒ 字面量端口 " + p + " 不在本域专用段 "
                            + RealGrpcPort.FIRST + "-" + RealGrpcPort.LAST + " 内（可能落进 OS 临时段）");
                }
            }
        }
        assertTrue(declaringFiles > 0,
                "本模块竟然一个提到 grpc.server.port / ServerBuilder.forPort 的测试类都没有"
                        + " ⇒ 源扫面失效（路径变了？）");
        assertTrue(violations.isEmpty(),
                "这些测试类指定的 gRPC server 端口不在本域专用段内 ⇒ 端口仍可能落 OS 临时段：\n  "
                        + String.join("\n  ", violations));
    }

    /**
     * 从源码里抽出「写死的端口号」—— 只认两种形态：{@code grpc.server.port=22500} 与
     * {@code forPort(22500)}。
     *
     * <p>⚠️ <b>判据自身不会被自己扫到</b>：本方法的正则源码里是 {@code grpc\.server\.port=} 与
     * {@code forPort\(}（<b>带反斜杠</b>）⇒ ⇒ 与「字面量形态」<b>逐字不同</b> ⇒ ⇒ 自匹配 = 0。
     */
    private static java.util.List<Integer> literalPorts(String code) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        java.util.regex.Matcher m = LITERAL_PORT.matcher(code);
        while (m.find()) {
            String num = m.group(1) != null ? m.group(1) : m.group(2);
            out.add(Integer.parseInt(num));
        }
        return out;
    }

    // ── 工具 ──────────────────────────────────────────────────────────────────

    private static Path moduleTestSourceRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 10 && dir != null; i++) {
            Path candidate = dir.resolve("service/article/article-rpc/src/test/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("找不到 service/article/article-rpc/src/test/java");
    }

    private static List<Path> testSourceFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(moduleTestSourceRoot())) {
            return stream.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static String rel(Path file) {
        return moduleTestSourceRoot().relativize(file).toString();
    }
}
