package com.rag.message.message.rpc;

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
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 🔴 <b>message-rpc 产物层守门</b>（2026-10-28 新增 · G3 转派）—— 解包<b>真实构建产物</b>（fat jar），
 * 断言它是一个<b>可执行的 Spring Boot fat jar</b>，且<b>内嵌的那份共享层 jar 带着排序桥</b>。
 *
 * <h2>🔴 为什么本域需要这条（此前是缺口）</h2>
 * <p>G3 在收编域侧排序桥时实测出：<b>各域的产物层守门覆盖并不齐</b> —— 有的域有「解包 fat jar」
 * 的守门，有的域<b>连 fat jar 都从来没被打包过</b>。
 * ⇒ ⇒ 本域此前<b>没有任何产物层守门</b> ⇒ ⇒ 实测本模块的 {@code target/} 下
 * <b>根本没有 jar</b>（{@code clean test} 不走 {@code package} 阶段 ⇒ 不产 fat jar）
 * ⇒ ⇒ 「<b>本域的产物可部署、且内嵌的共享层是当轮那份</b>」这句话<b>从来没人验过</b>。
 *
 * <h2>本类守的三个缺陷形态</h2>
 * <ol>
 *   <li>🔴 <b>产物不可执行</b>：{@code <build>} 段里没有 {@code spring-boot-maven-plugin}
 *       ⇒ 没有 {@code repackage} goal ⇒ 打出来的是<b>普通 library jar</b>
 *       ⇒ {@code java -jar message-rpc.jar} 报「no main manifest attribute」
 *       ⇒ ⇒ <b>该模块从来没被打成过可部署产物</b>。</li>
 *   <li>🔴 <b>主类没被自动探测到</b>：{@code Start-Class} 必须是
 *       {@code com.rag.message.message.rpc.MessageRpcApplication}
 *       ⇒ ⇒ 错了 ⇒ 产物起来就跑错入口。</li>
 *   <li>🔴 <b>内嵌的共享层是旧 jar</b>：域侧排序桥收编到共享层之后，排序边<b>只</b>由共享层
 *       那份提供 ⇒ ⇒ 若 fat jar 里嵌的是 {@code ~/.m2} 里的旧 {@code common} jar
 *       ⇒ ⇒ <b>内嵌 jar 里没有桥</b> ⇒ ⇒ MyBatis-Plus 的 {@code @AutoConfigureAfter}
 *       （Boot 3 的包名）在 Boot 4 下<b>悬空</b> ⇒ ⇒ MP 自动配置在 DataSource 之前求值
 *       ⇒ ⇒ {@code @ConditionalOnSingleCandidate(DataSource)} 失败 ⇒ ⇒
 *       <b>不产 {@code SqlSessionFactory}</b> ⇒ ⇒ <b>真进程启动即挂</b>
 *       （{@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}）。</li>
 * </ol>
 *
 * <h2>🔴 为什么必须<b>自己构建</b> fat jar</h2>
 * <p>本类跑在 surefire 默认的 {@code test} 阶段 ⇒ ⇒ fat jar（{@code package} 阶段的产物）
 * <b>还不存在</b> ⇒ ⇒ 不自己构建就只能验「上一轮的旧副本」⇒ ⇒ 判定失真。
 * ⇒ ⇒ 下面 {@link #fatJar()} 在 jar 缺失或<b>比源码/共享层旧</b>时<b>自己构建</b>
 * ⇒ ⇒ 断言的产物是<b>当轮源码</b>打出来的。
 * <p>⚠️ <b>构建命令必须带 {@code -am}</b>：否则 {@code service/common} 不在本次 reactor 里
 * ⇒ ⇒ 它从 {@code ~/.m2} 取 jar ⇒ ⇒ 内嵌的那份<b>可能没有排序桥</b>
 * ⇒ ⇒ 第 ③ 条断言会红（而红的方向就是真实缺陷）。
 *
 * <h2>⚠️ 本条<b>不是</b>「与 Go 对齐」</h2>
 * <p>本模块的 jar 能不能跑、内嵌的共享层是哪一份，与 Go 侧的行为<b>无关</b>
 * （Go 侧是 go-zero，不产 jar）⇒ ⇒ 本条是「<b>RAG 侧写法非法导致不可部署</b>」的修复，
 * 不是行为漂移修复。
 *
 * <h2>本类<b>不</b>断言 grpc-inprocess</h2>
 * <p>like-rpc / task-rpc 之所以要断言 {@code grpc-inprocess} 在运行时 classpath 上，
 * 是因为那两个模块<b>直接声明</b>了它（scope 一旦写成 {@code test}，Maven「最近者优先」
 * ⇒ 直接声明压过 devh starter 传递进来的 compile 声明 ⇒ ⇒ 整个 artifact 从运行时消失）。
 * ⇒ ⇒ 本模块<b>没有</b>直接声明它 ⇒ ⇒ 那个缺陷形态<b>对本模块不成立</b>
 * ⇒ ⇒ 照抄那条断言只会得到一个「与本缺陷无关」的断言 ⇒ ⇒ **不抄**。
 */
class MessageRpcArtifactClasspathGateTest {

    /** 本模块在仓库里的路径（相对仓库根）。 */
    static final String MODULE_PATH = "service/message/message-rpc";

    /** 生产产物的主类 —— Boot 4 的 {@code JarLauncher} 在 {@code .launch.} 子包下。 */
    private static final String EXPECTED_MAIN_CLASS = "org.springframework.boot.loader.launch.JarLauncher";

    /** 生产产物自动探测到的主类（本模块 pom 里**不写** {@code <mainClass>} ⇒ 由插件探测）。 */
    private static final String EXPECTED_START_CLASS = "com.rag.message.message.rpc.MessageRpcApplication";

    /**
     * 共享层排序桥的<b>全限定类名</b>（字面量，来自共享层的
     * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}）。
     *
     * <p>⚠️ <b>刻意写成字面量而不是引用共享层的常量</b>：守门断言的期望值若与被测代码同源
     * ⇒ 改一处两边一起变 ⇒ 断言恒成立 ⇒ ⇒ 守门变哑 ⇒ ⇒ 这里用<b>独立字面量</b>，
     * 共享层改包名时这条会如实转红（= 提醒同步）。
     */
    private static final String SHARED_LAYER_ORDERING_BRIDGE =
            "com.rag.common.persistence.MybatisPlusOrderingBridgeAutoConfiguration";

    // ==================================================================
    // ① fat jar 的 Main-Class 必须是 Boot 4 的 JarLauncher
    // ==================================================================

    @Test
    @DisplayName("真实 fat jar 的 Main-Class 必须是 Spring Boot 的 JarLauncher（否则 java -jar 起不来）")
    void fatJarMustHaveJarLauncherMainClass() throws Exception {
        Path jar = fatJar();
        String mainClass = manifestValue(jar, "Main-Class");

        assertEquals(EXPECTED_MAIN_CLASS, mainClass,
                "fat jar " + jar.getFileName() + " 的 Main-Class 必须是 " + EXPECTED_MAIN_CLASS
                        + "（Boot 4 的 JarLauncher 在 .launch. 子包下，**不是** Boot 2/3 的 "
                        + "org.springframework.boot.loader.JarLauncher）。"
                        + "缺它时产物是普通 library jar ⇒ `java -jar` 报"
                        + "「no main manifest attribute」（中没有主清单属性）⇒ 模块不可部署。");
    }

    // ==================================================================
    // ② fat jar 的 Start-Class 必须是 MessageRpcApplication
    // ==================================================================

    @Test
    @DisplayName("真实 fat jar 的 Start-Class 必须是 MessageRpcApplication（锁住「自动探测到正确主类」）")
    void fatJarMustStartAtMessageRpcApplication() throws Exception {
        Path jar = fatJar();
        String startClass = manifestValue(jar, "Start-Class");

        assertEquals(EXPECTED_START_CLASS, startClass,
                "fat jar 的 Start-Class 必须是 " + EXPECTED_START_CLASS
                        + "（spring-boot-maven-plugin 自动探测的主类）。"
                        + "本模块 pom 里**不写** <mainClass> ⇒ 由插件探测 "
                        + "service/message/message-rpc/src/main/java/com/rag/message/message/rpc/"
                        + "MessageRpcApplication.java。");
    }

    // ==================================================================
    // ③ fat jar 内嵌的 common jar 必须带共享层排序桥
    // ==================================================================

    @Test
    @DisplayName("真实 fat jar 内嵌的 common jar 必须带共享层排序桥（否则 MP 自动配置退避 ⇒ 进程起不来）")
    void fatJarMustCarrySharedLayerOrderingBridge() throws Exception {
        Path jar = fatJar();
        String imports;
        ZipEntry commonEntry = null;

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements() && commonEntry == null) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("BOOT-INF/lib/common-") && name.endsWith(".jar")) {
                    commonEntry = entry;
                }
            }
            assertNotNull(commonEntry, "fat jar " + jar.getFileName() + " 的 BOOT-INF/lib 里没有 common-*.jar ⇒ "
                    + "共享层不在运行时 classpath 上 ⇒ 本组件的自动配置根本不会被加载。");

            byte[] bytes;
            try (InputStream in = jarFile.getInputStream(commonEntry)) {
                bytes = in.readAllBytes();
            }
            Path tmp = Files.createTempFile("embedded-common", ".jar");
            try {
                Files.write(tmp, bytes);
                try (JarFile inner = new JarFile(tmp.toFile())) {
                    ZipEntry imp = inner.getJarEntry(
                            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
                    imports = imp == null
                            ? ""
                            : new String(inner.getInputStream(imp).readAllBytes(), StandardCharsets.UTF_8);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
        }

        assertTrue(imports.contains(SHARED_LAYER_ORDERING_BRIDGE),
                "内嵌的 " + commonEntry.getName() + " 的 AutoConfiguration.imports 里【没有】共享层排序桥（"
                        + SHARED_LAYER_ORDERING_BRIDGE + "）⇒ ⇒ 排序边不成立 ⇒ ⇒ MyBatis-Plus 的 "
                        + "@AutoConfigureAfter（Boot 3 包名）在 Boot 4 下悬空 ⇒ ⇒ MP 自动配置在 DataSource "
                        + "之前求值 ⇒ ⇒ @ConditionalOnSingleCandidate(DataSource) 失败 ⇒ ⇒ 不产 "
                        + "SqlSessionFactory ⇒ ⇒ **真进程启动即挂**（Property 'sqlSessionFactory' or "
                        + "'sqlSessionTemplate' are required）。\n"
                        + "根因 = 构建 fat jar 的那条 mvn 命令【不带 -am】⇒ service/common 从 ~/.m2 取旧 jar "
                        + "⇒ 旧 jar 里没有桥。\n实际取到的 imports 内容：\n" + imports);
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** 读 {@code META-INF/MANIFEST.MF} 里的某个属性值；不存在则返回 {@code null}。 */
    private static String manifestValue(Path jar, String key) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ZipEntry entry = jarFile.getEntry("META-INF/MANIFEST.MF");
            assertNotNull(entry, "fat jar " + jar.getFileName() + " 里**没有** META-INF/MANIFEST.MF"
                    + " ⇒ 这不是可执行产物");
            try (InputStream in = jarFile.getInputStream(entry)) {
                String manifest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                for (String raw : manifest.split("\r?\n")) {
                    if (raw.startsWith(key + ":")) {
                        return raw.substring(key.length() + 1).trim();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 取本模块的 fat jar；<b>缺失或比源码 / 共享层旧时由本类自己构建</b>
     * （构建产物必须与<b>当轮</b>源码一致 —— 否则断言的是上一轮的字节）。
     */
    private static Path fatJar() throws Exception {
        Path moduleDir = repoRoot().resolve(MODULE_PATH);
        Path target = moduleDir.resolve("target");
        Path jar = newestFatJar(target);
        if (jar == null || isStale(jar, moduleDir)) {
            buildFatJar();
            jar = newestFatJar(target);
        }
        assertNotNull(jar, "找不到本模块的 fat jar（" + target + " 下没有 *.jar）");
        return jar;
    }

    /** {@code target/} 下最新的 fat jar（**排除** {@code .jar.original} 那个 library jar）。 */
    private static Path newestFatJar(Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (var stream = Files.list(target)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith(".jar.original"))
                    .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-javadoc.jar"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
        }
    }

    /**
     * jar 比 pom / 源码 / <b>共享层</b>旧 ⇒ 构建产物是旧的 ⇒ 必须重建。
     *
     * <p>🔴🔴 <b>2026-10-28（G3 转派）：共享层的源码与 pom 也必须算进「新鲜度」的输入集</b>
     * <p>漏了它的两种失真形态：<b>假绿</b>（共享层刚把排序桥删了 ⇒ 陈旧内嵌 jar 里还带着桥
     * ⇒ 第 ③ 条照样绿 ⇒ 共享层的破坏被守门掩盖）· <b>假红</b>（共享层刚改名/新增 ⇒ 陈旧内嵌 jar
     * 里没有新名字 ⇒ 报错指向「构建命令不带 -am」⇒ 而真实原因是守门读了旧字节 ⇒ 排查方向被带偏）。
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
     * 构建本模块的 fat jar（跳过测试 —— 本类自己就是那个测试）。
     *
     * <p>🔴🔴 <b>必须带 {@code -am}</b>：否则 {@code service/common} 不在本次 reactor 里
     * ⇒ ⇒ 它从 {@code ~/.m2} 取 jar ⇒ ⇒ 而那份可能<b>早于共享层排序桥的落码</b>
     * ⇒ ⇒ <b>内嵌的 common jar 里没有桥</b> ⇒ ⇒ 第 ③ 条断言红（且红的方向就是真实缺陷）。
     */
    private static void buildFatJar() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(List.of(
                "mvn", "-o", "-q", "package", "-Dmaven.test.skip=true", "-pl", MODULE_PATH, "-am"))
                .directory(repoRoot().toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        String log = drainToString(process);
        int exit = process.waitFor();
        assertEquals(0, exit, "构建 fat jar 失败（mvn package -pl " + MODULE_PATH + " -am）：\n" + log);
    }

    /**
     * 向上找仓库根。
     *
     * <p>⚠️ <b>不能</b>只按「pom 里出现根 artifactId」来找 ——
     * 本模块的祖先里有 {@code service/pom.xml} / {@code service/message/pom.xml}
     * 两个**中间聚合 pom**，它们的 {@code <parent>} 也写着根 artifactId ⇒ 会误判成根。
     * ⇒ 这里取<b>最高</b>的、且**确实含有本模块路径**的那个祖先。
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
