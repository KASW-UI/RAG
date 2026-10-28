package com.rag.users.user.rpc.support;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 域级守门：**我域（users + follow）不许再有「OS 盲选口」**。
 *
 * <h2>为什么需要它</h2>
 * <p>{@code forPort(0)} / {@code new ServerSocket(0)} / {@code RANDOM_PORT}
 * 三种写法都是<b>让 OS 从临时端口段（macOS：49152–65535）里挑一个</b>
 * ⇒ ⇒ <b>Docker 随机发布容器端口用的就是同一段</b>
 * ⇒ ⇒ 本机是多会话并行的机器 ⇒ ⇒ 盲选口可能落到<b>别的容器已发布的口号</b>上。
 *
 * <p>⚠️ <b>危害形态是「静默被抢」，不是「起不来」</b>（2026-10-28 本机实测，非推理）：
 * <ul>
 *   <li>邻居占<b>通配地址</b> {@code 0.0.0.0:P} ⇒ 我们也绑通配 ⇒ <b>EADDRINUSE</b>（响亮失败，好查）；</li>
 *   <li>邻居占<b>更具体的地址</b> {@code 127.0.0.1:P} ⇒ macOS/BSD 上<b>通配绑定照样成功</b>
 *       ⇒ 我们的 server 起来了、端口号一模一样 ⇒ 但客户端连 {@code 127.0.0.1:P}
 *       会<b>命中更具体的那个</b> ⇒ 请求打到了邻居 ⇒ 对端协议不匹配
 *       ⇒ <b>看起来像「被测代码炸了」，其实压根没被请求到</b>。</li>
 * </ul>
 * <p>📌 实测原文（探针）：邻居占 {@code 127.0.0.1:P} ⇒ {@code NettyServerBuilder.forPort(P)}
 * <b>成功</b>，{@code lsof} 显示<b>两个监听者并存</b>（{@code 127.0.0.1:P} 与 {@code *:P}）；
 * 只探 {@code 0.0.0.0:P} <b>检不出</b> ⇒ ⇒ 必须探<b>两个地址</b>。
 *
 * <h2>🆕 第四种形态：{@code grpc.server.port=0}（2026-10-28 实测炸过）</h2>
 * <p>本守门原先只扫三种写法（{@code forPort(0)} / {@code new ServerSocket(0)} /
 * {@code RANDOM_PORT}）⇒ ⇒ <b>漏掉了 Spring 属性形态</b>
 * {@code properties = {"grpc.server.port=0", ...}} —— 它让 devh 把 0 交给 Netty
 * ⇒ ⇒ <b>同样是 OS 盲选口</b>，只是换了个入口。
 * <p>⚠️ <b>它真的炸过</b>（2026-10-28 · G1 lane 的阶段 1E 回归）：
 * {@code AdminRpcUpdateTimeAdvanceWiringTest} 5 条用例全 error，根因链是
 * {@code Failed to bind to address 0.0.0.0/0.0.0.0:36379} → {@code BindException: Address already in use}
 * ⇒ ⇒ 而 36379 当时被 <b>Docker 的 redis 容器发布代理</b>占着（{@code *:36379}）。
 * <p>📌 <b>机制（为什么内核会挑到一个「已被占」的端口）</b>：macOS 内核的临时口挑选
 * <b>只查 IPv4 占用</b>，而 Docker 的发布代理常把宿主口占在 <b>IPv6 通配</b>
 * ⇒ ⇒ 内核认为该口「IPv4 空闲」而挑中它 ⇒ 我们随后绑 IPv4 通配 ⇒ {@code EADDRINUSE}。
 * ⇒ ⇒ 这与上面「更具体地址被占」的形态<b>互为镜像</b>：一个是<b>挑不到</b>，
 * 一个是<b>挑到但绑不上</b> ⇒ ⇒ <b>都必须用域内 {@code RealGrpcPort.pick()} 取代</b>。
 * <p>⇒ 2026-10-28 起，本守门的扫描面<b>扩到 Spring 属性形态</b>，
 * 且域内 23 个测试类的 {@code grpc.server.port=0} 已全部改为
 * {@code r.add("grpc.server.port", () -> String.valueOf(RealGrpcPort.pick()))}。
 *
 * <h2>合法出口只有两个（写别的不算合法）</h2>
 * <ol>
 *   <li><b>动态挑口</b>：{@code RealGrpcPort.pick()} / {@code RealTomcatPort.pick()}
 *       ⇒ 域内专用段 + 双地址探针；</li>
 *   <li><b>段内字面量固定口</b>：字面量必须落在该域预分配段内
 *       （users 22200–22299 / follow 22300–22399）⇒ 本守门第 ② 条会逐个核。</li>
 * </ol>
 * ⇒ ⚠️ <b>判据只认这两种</b>：第三种写法（随便找个口 / 用 env 变量塞随机值）一律判违规。
 *
 * <h2>本守门守什么</h2>
 * <ol>
 *   <li><b>源扫</b>：我域 6 个模块的 {@code src/test/java} 里<b>零</b>盲选口写法
 *       ⇒ ⚠️ <b>必须先剥注释</b>，否则 javadoc 里引用 {@code forPort(0)} 会假阳性；</li>
 *   <li><b>段归属</b>：9 个端口类的段必须落在<b>共享层预分配给本域</b>的段里
 *       （users 22200–22299 / follow 22300–22399）⇒ 子段互不重叠
 *       ⇒ 不在临时段 ⇒ 不与 Testcontainers 段 22700–22999 重叠；</li>
 *   <li><b>运行期</b>：挑到的口<b>真的能起一个 gRPC server</b>且客户端连得上
 *       ⇒ ⇒ 「探针说空闲」不等于「起得来」；</li>
 *   <li><b>判据自检</b>：造假源码 ⇒ 扫描器必须报出来（否则零命中是假信号）；</li>
 *   <li><b>探针判别力</b>：占住<b>具体地址</b> ⇒ 判定必须为「不可用」
 *       ⇒ 这正是上面那条「静默被抢」的检出点。</li>
 * </ol>
 *
 * <h2>为什么不在 Go 侧对照</h2>
 * <p>本守门与被它守的端口类都是<b>测试设施</b>，不是产品行为 ⇒ Go 侧<b>没有对应物</b>
 * ⇒ ⇒ <b>不存在语义对照义务</b>（它们只影响测试能不能稳定跑到被测代码，不影响任何对外行为）。
 */
class BlindPortGateTest {

    /** 我域 6 个模块（测试树扫描面）。 */
    private static final String[] DOMAIN_MODULE_DIRS = {
            "service/users/user/user-api",
            "service/users/user/user-rpc",
            "service/users/admin/admin-api",
            "service/users/admin/admin-rpc",
            "service/follow/follow-api",
            "service/follow/follow-rpc",
    };

    /** 共享层段表预分配给 users 域的段（global/vs-common.md §4.10.19.3）。 */
    private static final int USERS_BAND_FIRST = 22200;
    private static final int USERS_BAND_LAST = 22299;

    /** 共享层段表预分配给 follow 域的段。 */
    private static final int FOLLOW_BAND_FIRST = 22300;
    private static final int FOLLOW_BAND_LAST = 22399;

    /** Testcontainers 宿主口段（全域共用）⇒ 本域的程序内起服口必须避开它。 */
    private static final int TESTCONTAINERS_BAND_FIRST = 22700;
    private static final int TESTCONTAINERS_BAND_LAST = 22999;

    /** OS 临时端口段（Docker 随机发布口就在这一段）。 */
    private static final int EPHEMERAL_FIRST = 49152;
    private static final int EPHEMERAL_LAST = 65535;

    /** 脚手架端口段：只给本守门用（造「被占」样本），**不与** 8 个端口类的段重叠。 */
    /**
     * 扫描器<b>自己</b>的文件名 => 扫描时<b>按文件名排除</b>。
     *
     * <p>为什么必须排除：本文件里<b>必然</b>含 {@code forPort(0)} 与
     * {@code new ServerSocket(0)} 这些<b>字面量</b>（模式串 ＋ 造样本用的假源码）
     * => => <b>不排除就等于「判据把自己算进去」</b>（第 N 次撞上这一族：§64.6 的
     * {@code ps} 判据自匹配是同一机制）=> => 排除后源扫才是「我域有没有盲选口」的真判据。
     *
     * <p>连带后果（已写进 ④ 的用例）：假样本必须叫 {@code Fake.java}
     * => 因为排除是<b>按文件名</b>的 => 假样本若也叫本名 => 会被一起吞掉 => 测不出扫描器是否活着。
     */
    private static final String SELF_FILE_NAME = "BlindPortGateTest.java";

    private static final int SCRATCH_FIRST = 22280;
    private static final int SCRATCH_LAST = 22299;

    // ==================================================================
    // ① 源扫：我域测试树零盲选口
    // ==================================================================

    @Test
    @DisplayName("① 源扫：我域 6 个模块的测试树里零盲选口（forPort(0) / new ServerSocket(0) / RANDOM_PORT / grpc.server.port=0）")
    void domainHasNoBlindPortLeft() throws IOException {
        ScanResult scan = scanForBlindPorts(repoRoot());
        assertThat(scan.visitedFiles)
                .as("扫描器必须真的扫到文件（否则「零命中」是空转假信号）")
                .isGreaterThanOrEqualTo(100);
        assertThat(scan.modulesScanned)
                .as("6 个模块都要被扫到")
                .hasSize(DOMAIN_MODULE_DIRS.length);
        assertThat(scan.hits)
                .as("我域测试树里不许再有 OS 盲选口（机制与实测见本类 javadoc）")
                .isEmpty();
    }

    // ==================================================================
    // ② 段归属：8 个端口类的段必须落在本域预分配段内
    // ==================================================================

    @Test
    @DisplayName("② 段归属：9 个端口类的段落在本域预分配段内、子段互不重叠、不在临时段、不撞 Testcontainers 段")
    void portClassesStayInsideDomainBand() throws IOException {
        Map<String, int[]> bands = new LinkedHashMap<>();
        for (String module : DOMAIN_MODULE_DIRS) {
            for (String cls : new String[]{"RealGrpcPort", "RealTomcatPort"}) {
                Path f = repoRoot().resolve(module).resolve("src/test/java").resolve(
                        moduleToPackagePath(module)).resolve("support").resolve(cls + ".java");
                if (!Files.isRegularFile(f)) {
                    continue; // 该模块不需要这一类
                }
                bands.put(module + "/" + cls, readBand(f));
            }
        }

        assertThat(bands)
                .as("要有 6 个 RealGrpcPort + 3 个 RealTomcatPort"
                        + "（user-api / admin-api / follow-api 各要一个 Tomcat 口）")
                .hasSize(9);

        List<int[]> ranges = new ArrayList<>();
        for (Map.Entry<String, int[]> e : bands.entrySet()) {
            String key = e.getKey();
            int first = e.getValue()[0];
            int last = e.getValue()[1];
            int domainFirst = key.startsWith("service/follow") ? FOLLOW_BAND_FIRST : USERS_BAND_FIRST;
            int domainLast = key.startsWith("service/follow") ? FOLLOW_BAND_LAST : USERS_BAND_LAST;

            assertThat(first)
                    .as("%s 的段下界必须落在本域预分配段 %d-%d 内（实际=%d）",
                            key, domainFirst, domainLast, first)
                    .isBetween(domainFirst, domainLast);
            assertThat(last)
                    .as("%s 的段上界必须落在本域预分配段 %d-%d 内（实际=%d）",
                            key, domainFirst, domainLast, last)
                    .isBetween(domainFirst, domainLast);
            assertThat(first)
                    .as("%s 的段下界不得大于上界", key)
                    .isLessThanOrEqualTo(last);
            assertThat(last)
                    .as("%s 的段不得落进 OS 临时段 %d-%d（Docker 随机发布口就在那一段）",
                            key, EPHEMERAL_FIRST, EPHEMERAL_LAST)
                    .isLessThan(EPHEMERAL_FIRST);
            assertThat(last)
                    .as("%s 的段不得与 Testcontainers 段 %d-%d 重叠",
                            key, TESTCONTAINERS_BAND_FIRST, TESTCONTAINERS_BAND_LAST)
                    .isLessThan(TESTCONTAINERS_BAND_FIRST);
            ranges.add(new int[]{first, last});
        }

        for (int i = 0; i < ranges.size(); i++) {
            for (int j = i + 1; j < ranges.size(); j++) {
                int[] a = ranges.get(i);
                int[] b = ranges.get(j);
                boolean overlap = a[0] <= b[1] && b[0] <= a[1];
                assertThat(overlap)
                        .as("子段 %d-%d 与 %d-%d 重叠了 ⇒ 同一时刻可能撞口", a[0], a[1], b[0], b[1])
                        .isFalse();
            }
        }
    }

    // ==================================================================
    // ③ 运行期：挑到的口真能起 gRPC server，且客户端连得上
    // ==================================================================

    @Test
    @DisplayName("③ 运行期：挑到的口能起真 gRPC server，且客户端能连上（READY）")
    void pickedPortReallyServesGrpc() throws Exception {
        int port = RealGrpcPort.pick();
        assertThat(port)
                .as("挑到的口必须在本模块段内（%d-%d）", RealGrpcPort.FIRST, RealGrpcPort.LAST)
                .isBetween(RealGrpcPort.FIRST, RealGrpcPort.LAST);

        Server server = NettyServerBuilder.forPort(port).build().start();
        ManagedChannel channel = NettyChannelBuilder
                .forTarget("dns:///127.0.0.1:" + server.getPort())
                .usePlaintext()
                .build();
        try {
            long deadline = System.nanoTime() + 10_000_000_000L;
            boolean ready = false;
            while (System.nanoTime() < deadline) {
                // getState(true) 才会请求连接；false 不请求 => Netty 懒连接 => 永远停在 IDLE => 断言假红
                if (channel.getState(true) == io.grpc.ConnectivityState.READY) {
                    ready = true;
                    break;
                }
                Thread.sleep(50);
            }
            assertThat(ready)
                    .as("客户端必须在 10s 内连上真 gRPC server（端口 %d）⇒ 「探针说空闲」不等于「起得来」",
                            server.getPort())
                    .isTrue();
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
        }
    }

    // ==================================================================
    // ④ 判据自检：扫描器不是空转
    // ==================================================================

    @Test
    @DisplayName("④ 判据自检：造假源码含 forPort(0) / grpc.server.port=0 ⇒ 扫描器必须报出；注释里的字面量必须不报")
    void scannerActuallyReportsBlindPorts() throws IOException {
        Path fakeRoot = Files.createTempDirectory("g1-blind-port-selftest");
        Path javaDir = fakeRoot.resolve("service/users/user/user-rpc/src/test/java/com/rag/fake");
        Files.createDirectories(javaDir);
        Files.writeString(javaDir.resolve("Fake.java"),
                "package com.rag.fake;\n"
                        + "class Fake {\n"
                        + "    void f() {\n"
                        + "        io.grpc.ServerBuilder.forPort(0);\n"
                        + "    }\n"
                        + "}\n",
                StandardCharsets.UTF_8);
        // 🆕 2026-10-28：Spring 属性形态的假样本 —— **必须换名**（排除规则是按文件名的）
        //    ⚠️ 字面量必须写在**代码**里（不能放注释）⇒ 扫描器会先剥注释 ⇒ 放注释等于「测不到」
        Files.writeString(javaDir.resolve("FakeSpringProp.java"),
                "package com.rag.fake;\n"
                        + "class FakeSpringProp {\n"
                        + "    String f() {\n"
                        + "        return \"grpc.server.port=0\";\n"
                        + "    }\n"
                        + "}\n",
                StandardCharsets.UTF_8);

        ScanResult result = scanForBlindPorts(fakeRoot);
        assertThat(result.visitedFiles).isEqualTo(2);
        assertThat(result.hits)
                .as("扫描器必须报出假源码里的两处（否则本守门是空转 ⇒ 零命中是假信号）")
                .hasSize(2);
        // ⚠️ **顺序无关**断言：`Files.walk` 的遍历顺序**不由文件系统保证** ⇒
        //    `hits.get(0)` 到底是谁**不可假设**（2026-10-28 实测踩中：一次跑里 get(0) 是另一个样本）
        assertThat(result.hits)
                .as("api/SDK 形态的假样本必须被报出")
                .anyMatch(h -> h.contains("Fake.java"));
        assertThat(result.hits)
                .as("🆕 Spring 属性形态 grpc.server.port=0 也必须被报出（这正是 2026-10-28 漏掉的那一类）")
                .anyMatch(h -> h.contains("FakeSpringProp.java"));

        // ⚠️ 反例自检：把同一处写进**注释**里 ⇒ 必须**不**报（否则 javadoc 引用会假阳性）
        Files.writeString(javaDir.resolve("CommentOnly.java"),
                "package com.rag.fake;\n"
                        + "/** 说明：原来写的是 ServerBuilder.forPort(0) —— 见 §65。 */\n"
                        + "// ServerBuilder.forPort(0)\n"
                        + "/** 原来是 grpc.server.port=0 */\n"
                        + "class CommentOnly { }\n",
                StandardCharsets.UTF_8);
        ScanResult result2 = scanForBlindPorts(fakeRoot);
        assertThat(result2.hits)
                .as("注释里的字面量必须被剥掉（否则本守门会对着 javadoc 假阳性）")
                .hasSize(2);
        assertThat(result2.hits)
                .as("CommentOnly.java 里只有注释 ⇒ 必须零命中")
                .noneMatch(h -> h.contains("CommentOnly.java"));
    }

    // ==================================================================
    // ⑤ 探针判别力：占住「具体地址」必须判为不可用
    // ==================================================================

    @Test
    @DisplayName("⑤ 探针判别力：占住 127.0.0.1:某口 ⇒ 必须判定为不可用（这正是「静默被抢」的检出点）")
    void probeRejectsPortHeldOnLoopback() throws Exception {
        int scratch = -1;
        for (int p = SCRATCH_FIRST; p <= SCRATCH_LAST; p++) {
            if (isBindable(p)) {
                scratch = p;
                break;
            }
        }
        assertThat(scratch)
                .as("脚手架段 %d-%d 里得有一个可用端口", SCRATCH_FIRST, SCRATCH_LAST)
                .isGreaterThan(0);

        // ⚠️ 关键样本：占住**具体地址** 127.0.0.1:P（macOS 上它与通配绑定**可以共存**
        //    ⇒ 我们的 server 照样起得来、但客户端会被它抢走 ⇒ 探针必须能检出）
        try (ServerSocket hold = new ServerSocket()) {
            hold.setReuseAddress(true);
            hold.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), scratch), 1);
            assertThat(RealGrpcPort.isFree(scratch))
                    .as("端口 %d 被 127.0.0.1 占用 ⇒ 必须判定为**不可用**", scratch)
                    .isFalse();
        }
        assertThat(RealGrpcPort.isFree(scratch))
                .as("释放之后端口 %d 必须重新变成可用", scratch)
                .isTrue();
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 一次扫描的结果 —— <b>带「扫了多少个文件 / 多少个模块」</b>，好让「零命中」可判真假。 */
    static final class ScanResult {
        final List<String> hits = new ArrayList<>();
        final List<String> modulesScanned = new ArrayList<>();
        int visitedFiles;
    }

    private static final Pattern BLIND_PORT = Pattern.compile(
            "forPort\\(0\\)|new ServerSocket\\(0\\)|WebEnvironment\\.RANDOM_PORT"
                    // 🆕 2026-10-28：Spring 属性形态（devh 把 0 交给 Netty ⇒ 同样是 OS 盲选口）
                    //    ⇒ admin-rpc 上真实炸过（36379 被 Docker 发布代理占在 IPv6 通配）
                    + "|grpc\\.server\\.port=0|server\\.port=0");

    private static final Pattern FIRST_CONST = Pattern.compile("public static final int FIRST = (\\d+);");
    private static final Pattern LAST_CONST = Pattern.compile("public static final int LAST = (\\d+);");

    /**
     * 扫「我域 6 个模块的测试树」里有没有 OS 盲选口。
     *
     * <p>⚠️ <b>三个容易踩空的地方，本实现都处理了</b>：
     * <ol>
     *   <li><b>注释</b>：先剥注释再扫 ⇒ javadoc 里引用 {@code forPort(0)} 不会假阳性
     *       （实测踩过：改造后源码里共 17 处字面量，其中 <b>11 处全是我自己写的注释</b>）；</li>
     *   <li><b>路径</b>：{@code service/users/user} 是聚合目录 ⇒ 递归找
     *       {@code /src/test/java/} 里的 .java，而不是拼固定路径；</li>
     *   <li><b>共享层</b>：{@code service/users/users-common/**} 是共享层（写权归 G4）⇒ 不纳入。</li>
     * </ol>
     */
    private static ScanResult scanForBlindPorts(Path root) throws IOException {
        ScanResult result = new ScanResult();
        for (String dir : DOMAIN_MODULE_DIRS) {
            Path base = root.resolve(dir).resolve("src/test/java");
            if (!Files.isDirectory(base)) {
                continue;
            }
            result.modulesScanned.add(dir);
            try (Stream<Path> stream = Files.walk(base)) {
                for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                    if (!p.toString().endsWith(".java")) {
                        continue;
                    }
                    if (SELF_FILE_NAME.equals(p.getFileName().toString())) {
                        continue;   // 扫描器自己 => 排除（见 SELF_FILE_NAME 的说明）
                    }
                    result.visitedFiles++;
                    String code = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                    Matcher m = BLIND_PORT.matcher(code);
                    while (m.find()) {
                        int line = (int) code.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                        result.hits.add(root.relativize(p).toString() + ":" + line + "  →  " + m.group());
                    }
                }
            }
        }
        return result;
    }

    /** 读一个端口类的 {@code FIRST}/{@code LAST} 常量。 */
    private static int[] readBand(Path file) throws IOException {
        String code = stripComments(Files.readString(file, StandardCharsets.UTF_8));
        Matcher f = FIRST_CONST.matcher(code);
        Matcher l = LAST_CONST.matcher(code);
        if (!f.find() || !l.find()) {
            throw new IllegalStateException("读不到端口段常量：" + file);
        }
        return new int[]{Integer.parseInt(f.group(1)), Integer.parseInt(l.group(1))};
    }

    /** 模块目录 → 包路径。 */
    private static String moduleToPackagePath(String module) {
        return switch (module) {
            case "service/users/user/user-api" -> "com/rag/users/user/api";
            case "service/users/user/user-rpc" -> "com/rag/users/user/rpc";
            case "service/users/admin/admin-api" -> "com/rag/users/admin/api";
            case "service/users/admin/admin-rpc" -> "com/rag/users/admin/rpc";
            case "service/follow/follow-api" -> "com/rag/follow/follow/api";
            case "service/follow/follow-rpc" -> "com/rag/follow/follow/rpc";
            default -> throw new IllegalArgumentException("未登记的模块：" + module);
        };
    }

    /** 该口当前在通配地址上可绑吗（只做「脚手架取口」用）。 */
    private static boolean isBindable(int port) {
        try (ServerSocket s = new ServerSocket()) {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 剥注释（保留行数，好让行号可读）。 */
    private static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') {
                        out.append('\n');
                    }
                    i++;
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** 仓库根：从本类的 class 文件位置向上找同时含 {@code service/} 与 {@code pom.xml} 的目录。 */
    private static Path repoRoot() {
        Path here = Path.of(BlindPortGateTest.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath());
        for (Path p = here; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("service")) && Files.isRegularFile(p.resolve("pom.xml"))) {
                return p;
            }
        }
        throw new IllegalStateException("找不到仓库根（从 " + here + "向上没有同时含 service/ 与 pom.xml 的目录）");
    }
}
