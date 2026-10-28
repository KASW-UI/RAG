package com.rag.like.like.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>产物层守门（不需要 Docker / 不需要起进程）</b> —— 断言本模块的
 * <b>运行时 classpath</b> 与 <b>fat jar</b> 上<b>真的有</b> {@code io.grpc:grpc-inprocess}。
 *
 * <h2>它守的是什么缺陷</h2>
 * <p>Maven「最近者优先」：本模块**直接声明</b> 的 {@code io.grpc:grpc-inprocess}
 * 压过 devh starter（{@code net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE}）
 * 传递进来的 **compile** 声明 ⇒ scope 一旦写成 {@code test}，
 * <b>运行时 classpath 上整个 artifact 消失</b>（fat jar 的 {@code BOOT-INF/lib/} 里也没有它）
 * ⇒ devh 的 {@code InProcessChannelFactory} 链接失败 ⇒
 * {@code NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder}
 * ⇒ <b>整个服务起不来</b>（硬失败，不是静默失效）。
 *
 * <h2>为什么这三条断言在 <b>test classpath</b> 上是测不出来的</h2>
 * <p>测试跑在 test classpath 上 ⇒ {@code grpc-inprocess} 本来就在 ⇒
 * 「pom 里 scope 写错」这件事<b>永远测不到</b>；而所有 wiring 测试又把地址覆盖成
 * {@code static://127.0.0.1:1} ⇒ 生产地址也永远测不到。
 * ⇒ 这正是 AGENTS.md §3.3 说的「<b>跑绿 ≠ 对</b>」。
 * ⇒ 所以本类解析的是 <b>runtime classpath</b>（{@code -DincludeScope=runtime}
 * ⇒ 与 fat jar 的 {@code BOOT-INF/lib} 同源），并**解包真 fat jar** 看条目在不在。
 *
 * <h2>为什么本类<b>不</b>需要 Docker</h2>
 * <p>它只看<b>产物</b>（classpath + jar 条目），不起进程、不连库 ⇒
 * <b>无 Docker 的 CI 也能跑</b> ⇒ 这是本缺陷的**环境无关**判据；
 * 进程级的「真起得来」由 {@code LikeRpcRuntimeBootGateTest} 承担（那条需要 Docker ⇒ 无 Docker 时整类 skip）。
 */
class LikeRpcArtifactClasspathGateTest {

    /** 本模块在仓库里的路径（相对仓库根）。 */
    static final String MODULE_PATH = "service/like/like-rpc";

    // ==================================================================
    // ① runtime classpath 上必须有 grpc-inprocess（scope 不得是 test）
    // ==================================================================

    @Test
    @DisplayName("运行时 classpath 上必须有 io.grpc:grpc-inprocess（scope 不得是 test）")
    void runtimeClasspathContainsGrpcInprocess() throws Exception {
        String classpath = runtimeClasspath();

        assertTrue(classpath.contains("grpc-inprocess"),
                "运行时 classpath 上没有 grpc-inprocess ⇒ devh 的 InProcessChannelFactory 链接失败 ⇒ "
                        + "NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder ⇒ "
                        + "**整个服务起不来**。"
                        + "根因是 Maven「最近者优先」：本模块的直接声明压过 devh starter 传递进来的 "
                        + "compile 声明 ⇒ scope 一旦写成 test，运行时 classpath 上整个 artifact 消失。");
    }

    // ==================================================================
    // ② 真 fat jar 里必须有 grpc-inprocess（test scope 的依赖不进 fat jar）
    // ==================================================================

    @Test
    @DisplayName("真 fat jar 的 BOOT-INF/lib 里必须有 grpc-inprocess（test scope 的依赖不进 fat jar）")
    void fatJarContainsGrpcInprocess() throws Exception {
        Path jar = fatJar();

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            boolean found = jarFile.stream()
                    .anyMatch(entry -> entry.getName().startsWith("BOOT-INF/lib/grpc-inprocess-"));
            assertTrue(found,
                    "fat jar " + jar.getFileName() + " 的 BOOT-INF/lib 里没有 grpc-inprocess ⇒ "
                            + "真启动会抛 NoClassDefFoundError: io/grpc/inprocess/InProcessChannelBuilder。");
        }
    }

    // ==================================================================
    // ③ fat jar 内嵌的共享层 jar 必须带 MyBatis-Plus 排序桥（2026-10-27 第十七轮新增）
    // ==================================================================

    /**
     * 🔴 <b>产物层第 ③ 条守门</b>：断言 fat jar 里内嵌的那份 {@code common-*.jar}
     * <b>带着共享层的 MyBatis-Plus 自动配置排序桥</b>。
     *
     * <p><b>它守的是什么缺陷</b>：域侧排序桥收编到共享层之后，排序边<b>只</b>由共享层那份提供
     * ⇒ ⇒ <b>内嵌的 common jar 必须是当轮构建的那份</b>（否则里面没有桥）
     * ⇒ ⇒ 缺桥 ⇒ MyBatis-Plus 的 {@code @AutoConfigureAfter}（Boot 3 包名）在 Boot 4 下悬空
     * ⇒ ⇒ MP 自动配置在 DataSource 之前求值 ⇒
     * {@code @ConditionalOnSingleCandidate(DataSource)} 失败 ⇒ ⇒
     * <b>不产 {@code SqlSessionFactory}</b> ⇒ ⇒ <b>真进程启动即挂</b>
     * （{@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}）。
     *
     * <p>⚠️ <b>为什么这条守门不可省</b>：同包的
     * {@code LikeRpcRuntimeBootGateTest}（进程级）已经能抓到「起不来」，
     * <b>但它是在「起不来」之后才报</b> ⇒ ⇒ 失败点离根因很远（要读进程日志尾部才能反推）
     * ⇒ ⇒ 本条把判据前移到<b>产物层</b>：内嵌 jar 里有没有那行注册，<b>一眼可判</b>。
     *
     * <p>⚠️ <b>期望值不能与被测代码同源</b>：这里断言的是<b>共享层那一行全限定名</b>
     * （字面量，来自 {@code service/common} 的
     * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}），
     * <b>不是</b>从被测代码里取常量 ⇒ ⇒ 共享层改了包名 ⇒ 这条会<b>如实转红</b>（= 提醒同步）。
     */
    @Test
    @DisplayName("真 fat jar 内嵌的 common jar 必须带共享层排序桥（否则 MP 自动配置退避 ⇒ 进程起不来）")
    void fatJarCarriesSharedLayerOrderingBridge() throws Exception {
        Path jar = fatJar();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            JarEntry entry = jarFile.stream()
                    .filter(e -> e.getName().startsWith("BOOT-INF/lib/common-")
                            && e.getName().endsWith(".jar"))
                    .findFirst()
                    .orElse(null);
            assertNotNull(entry, "fat jar " + jar.getFileName() + " 的 BOOT-INF/lib 里没有 common-*.jar ⇒ "
                    + "共享层不在运行时 classpath 上 ⇒ 本模块的自动配置根本不会被加载。");

            byte[] bytes;
            try (InputStream in = jarFile.getInputStream(entry)) {
                bytes = in.readAllBytes();
            }
            Path tmp = Files.createTempFile("embedded-common", ".jar");
            String imports;
            try {
                Files.write(tmp, bytes);
                try (JarFile inner = new JarFile(tmp.toFile())) {
                    JarEntry imp = inner.getJarEntry(
                            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
                    imports = imp == null
                            ? ""
                            : new String(inner.getInputStream(imp).readAllBytes(), StandardCharsets.UTF_8);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }

            assertTrue(imports.contains(SHARED_LAYER_ORDERING_BRIDGE),
                    "内嵌的 " + entry.getName() + " 的 AutoConfiguration.imports 里【没有】共享层排序桥（"
                            + SHARED_LAYER_ORDERING_BRIDGE + "）⇒ ⇒ 排序边不成立 ⇒ ⇒ MyBatis-Plus 的 "
                            + "@AutoConfigureAfter（Boot 3 包名）在 Boot 4 下悬空 ⇒ ⇒ MP 自动配置在 "
                            + "DataSource 之前求值 ⇒ ⇒ @ConditionalOnSingleCandidate(DataSource) 失败 ⇒ ⇒ "
                            + "不产 SqlSessionFactory ⇒ ⇒ **真进程启动即挂**（Property 'sqlSessionFactory' or "
                            + "'sqlSessionTemplate' are required）。\n"
                            + "根因 = 构建 fat jar 的那条 mvn 命令【不带 -am】⇒ service/common 从 ~/.m2 取旧 jar "
                            + "⇒ 旧 jar 里没有桥。\n实际取到的 imports 内容：\n" + imports);
        }
    }

    /**
     * 共享层排序桥的<b>全限定类名</b>（字面量，来自共享层的
     * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}）。
     *
     * <p>⚠️ <b>刻意写成字面量而不是引用共享层的常量</b>：守门断言的期望值若与被测代码同源
     * ⇒ 改一处两边一起变 ⇒ 断言恒成立 ⇒ ⇒ 守门变哑 ⇒ ⇒ 这里用<b>独立字面量</b>，
     * 共享层改包名时这条会如实转红。
     */
    private static final String SHARED_LAYER_ORDERING_BRIDGE =
            "com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration";

    // ==================================================================
    // helpers（package-private ⇒ 同包的进程级守门类复用）
    // ==================================================================

    /** 解析本模块的<b>运行时</b> classpath（= fat jar 的 BOOT-INF/lib 那一层，不含 test scope）。 */
    static String runtimeClasspath() throws Exception {
        Path outputFile = Files.createTempFile("l12-like-runtime-cp", ".txt");
        ProcessBuilder builder = new ProcessBuilder(List.of(
                "mvn", "-o", "-q", "-pl", MODULE_PATH, "dependency:build-classpath",
                "-DincludeScope=runtime",
                "-Dmdep.outputFile=" + outputFile.toAbsolutePath()))
                .directory(repoRoot().toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        String log = drainToString(process);
        int exit = process.waitFor();
        assertEquals(0, exit, "解析运行时 classpath 失败（mvn dependency:build-classpath）：\n" + log);
        return Files.readString(outputFile, StandardCharsets.UTF_8);
    }

    /** 取本模块的 fat jar；缺失或比源码旧时重新构建（构建产物必须与当前源码一致）。 */
    static Path fatJar() throws Exception {
        Path moduleDir = repoRoot().resolve(MODULE_PATH);
        Path jar = newestFatJar(moduleDir.resolve("target"));
        if (jar == null || isStale(jar, moduleDir)) {
            buildFatJar();
            jar = newestFatJar(moduleDir.resolve("target"));
        }
        assertNotNull(jar, "找不到本模块的 fat jar（" + moduleDir.resolve("target") + " 下没有 *.jar）");
        return jar;
    }

    private static Path newestFatJar(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> files = Files.list(target)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith(".jar.original"))
                    .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-javadoc.jar"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }

    /**
     * jar 比 pom / 源码旧 ⇒ 构建产物是旧的 ⇒ 必须重建。
     *
     * <p>🔴🔴 <b>2026-10-28（G3 转派）：共享层的源码与 pom 也必须算进「新鲜度」的输入集</b>
     * <p><b>漏了它会发生什么（机制）</b>：本守门断言的是<b>fat jar 里内嵌的那份 common jar</b>
     * ⇒ ⇒ 而内嵌的那份是由<b>构建时</b>的 {@code service/common} 决定的
     * ⇒ ⇒ 若<b>只有共享层改了、本模块没改</b> ⇒ ⇒ 本模块的 {@code pom.xml} 与 {@code src/main}
     * 都没变 ⇒ ⇒ 「旧于 jar」这个判据<b>不成立</b> ⇒ ⇒ **fat jar 不会重建**
     * ⇒ ⇒ 守门读到的是<b>上一轮的陈旧内嵌 jar</b> ⇒ ⇒
     * <ul>
     *   <li><b>假绿</b>：共享层刚把排序桥删了 ⇒ 陈旧内嵌 jar 里<b>还带着桥</b> ⇒ ⇒ 第 ③ 条断言照样绿
     *       ⇒ ⇒ <b>共享层的破坏被守门掩盖</b>；</li>
     *   <li><b>假红</b>：共享层刚把排序桥<b>改名/新增</b> ⇒ 陈旧内嵌 jar 里<b>没有</b>新名字
     *       ⇒ ⇒ 第 ③ 条断言红 ⇒ ⇒ 报错指向「构建命令不带 -am」
     *       ⇒ ⇒ <b>而真实原因是守门读了旧字节</b> ⇒ ⇒ 排查方向被带偏。</li>
     * </ul>
     * <p>⇒ ⇒ 所以输入集必须包含：{@code service/common/pom.xml} ＋
     * {@code service/common/src/main} 下的全部文件。
     * <p>⚠️ <b>为什么不能只加「共享层的 jar」</b>：本守门在 {@code test} 阶段跑，
     * 此时 reactor 还没到 {@code package} ⇒ ⇒ 共享层的当轮 jar <b>还不存在</b>
     * ⇒ ⇒ 只能以<b>源码 + pom</b> 的 mtime 作判据（G3 同款修法）。
     */
    private static boolean isStale(Path jar, Path moduleDir) throws IOException {
        long jarTime = jar.toFile().lastModified();
        List<Path> inputs = new ArrayList<>();
        inputs.add(moduleDir.resolve("pom.xml"));
        Path main = moduleDir.resolve("src").resolve("main");
        if (Files.isDirectory(main)) {
            try (Stream<Path> files = Files.walk(main)) {
                files.filter(Files::isRegularFile).forEach(inputs::add);
            }
        }
        // 🔴 共享层（service/common）也必须算进输入集 —— 见上方 javadoc 的两种失真形态。
        Path sharedModule = repoRoot().resolve("service").resolve("common");
        inputs.add(sharedModule.resolve("pom.xml"));
        Path sharedMain = sharedModule.resolve("src").resolve("main");
        if (Files.isDirectory(sharedMain)) {
            try (Stream<Path> files = Files.walk(sharedMain)) {
                files.filter(Files::isRegularFile).forEach(inputs::add);
            }
        }
        for (Path input : inputs) {
            if (Files.exists(input) && input.toFile().lastModified() > jarTime) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建 fat jar（跳过测试 —— 本类自己就是那个测试）。
     *
     * <p>🔴🔴 <b>2026-10-27 第十七轮：必须带 {@code -am}（否则测的不是本树代码）</b>
     * <p><b>不带 {@code -am} 时会发生什么（实测）**：{@code service/common} <b>不在</b>本次
     * reactor 里 ⇒ ⇒ 它从 <b>{@code ~/.m2}</b> 取 jar ⇒ ⇒ 而 {@code ~/.m2} 里那份
     * {@code common-0.0.1-SNAPSHOT.jar} <b>可能早于共享层排序桥的落码</b>
     * ⇒ ⇒ <b>内嵌的 common jar 里没有排序桥</b> ⇒ ⇒ MyBatis-Plus 的
     * {@code @AutoConfigureAfter}（<b>Boot 3 的包名</b>）在 Boot 4 下<b>悬空</b>
     * ⇒ ⇒ MP 自动配置在 DataSource 之前求值 ⇒
     * {@code @ConditionalOnSingleCandidate(DataSource)} 失败 ⇒ ⇒
     * <b>不产 {@code SqlSessionFactory}</b> ⇒ ⇒
     * <b>fat jar 真启动即挂</b>（{@code Property 'sqlSessionFactory' or
     * 'sqlSessionTemplate' are required}）。
     * <p>⚠️ <b>这条曾被域内排序桥掩盖住</b>：域内那份桥在 {@code BOOT-INF/classes} 里
     * ⇒ ⇒ 不管内嵌的 common jar 多旧，排序边都成立 ⇒ ⇒ <b>fat jar 一直是绿的</b>
     * ⇒ ⇒ <b>域内桥一收编，这条潜伏的构建路径缺陷才暴露</b>（本轮 like-rpc 首次撞上）。
     * <p>⇒ ⇒ 带 {@code -am} ⇒ reactor 会先产出 <b>当轮</b>的
     * {@code service/common/target/common-*.jar} ⇒ ⇒ fat jar 内嵌的就是它
     * ⇒ ⇒ <b>排序桥在内嵌 jar 里</b>（已实测：内嵌 jar 的
     * {@code AutoConfiguration.imports} 第 4 行 = 共享层排序桥）。
     */
    private static void buildFatJar() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(List.of(
                "mvn", "-o", "-q", "package", "-Dmaven.test.skip=true", "-pl", MODULE_PATH, "-am"))
                .directory(repoRoot().toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        String log = drainToString(process);
        int exit = process.waitFor();
        assertEquals(0, exit, "构建 fat jar 失败：\n" + log);
    }

    /**
     * 向上找仓库根。
     *
     * <p>⚠️ <b>不能</b>只按「pom 里出现根 artifactId」来找 ——
     * 本模块的祖先里有 {@code service/pom.xml} / {@code service/like/pom.xml}
     * 两个**中间聚合 pom**，它们的 {@code <parent>} 也写着根 artifactId ⇒ 会误判成根。
     * ⇒ 这里取<b>最高</b>的、且**确实含有本模块路径</b>的那个祖先。
     */
    static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        Path found = null;
        for (int i = 0; i < 10 && dir != null; i++) {
            if (Files.exists(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve(MODULE_PATH))) {
                found = dir;
            }
            dir = dir.getParent();
        }
        if (found == null) {
            throw new IllegalStateException(
                    "找不到仓库根目录（工作目录：" + Paths.get("").toAbsolutePath()
                            + "，要求祖先里有 " + MODULE_PATH + "）");
        }
        return found;
    }

    private static String drainToString(Process process) throws IOException {
        StringBuilder sink = new StringBuilder();
        try (java.io.InputStream in = process.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                sink.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        }
        return sink.toString();
    }
}
