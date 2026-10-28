package com.rag.task.task.rpc;

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
 * <b>task-rpc 产物层守门</b> —— 解包**真实构建产物**（fat jar）断言它是一个
 * <b>可执行的 Spring Boot fat jar</b>，且<b>运行时 classpath 上有 {@code grpc-inprocess}</b>。
 *
 * <h2>为什么必须验<b>产物</b>，不能只验 pom</h2>
 * <p>本模块此前有<b>两条</b>产物层缺陷：
 * <ol>
 *   <li>🔴 <b>jar 不可执行</b>：{@code <build>} 段里<b>没有</b> {@code spring-boot-maven-plugin}
 *       ⇒ 没有 {@code repackage} goal ⇒ 打出来的是**普通 library jar**
 *       （实测 83,827 B）⇒ {@code java -jar task-rpc.jar} 报
 *       「no main manifest attribute」（中文：「中没有主清单属性」）
 *       ⇒ <b>该模块从来没有被打成过可部署产物</b>。</li>
 *   <li>🔴 <b>{@code grpc-inprocess} 是 {@code <scope>test</scope>}</b>
 *       ⇒ Maven「最近者优先」⇒ 直接声明压过
 *           {@code net.devh:grpc-server-spring-boot-starter} 传递进来的 compile 声明
 *       ⇒ <b>运行时 classpath 上整个 artifact 消失</b>
 *       ⇒ 不在 fat jar 的 {@code BOOT-INF/lib/} 里。</li>
 * </ol>
 *
 * <p>⚠️ <b>「pom 里写了 plugin」不等于「产物可执行」</b>（AGENTS.md §3.3）：
 * plugin 写错位置 / 写进 {@code pluginManagement} / 目标没绑到 phase ⇒
 * <b>编译通过、测试全绿，产物照样是空壳</b>。
 * ⇒ 本类<b>解包真实 jar</b>看 {@code META-INF/MANIFEST.MF} 与 {@code BOOT-INF/lib/}，
 * <b>不看 pom 字符串</b>。
 *
 * <h2>🔴 2026-10-28：为什么本类<b>从 {@code *IT} 改回 {@code *Test}</b>（形状对齐 like-rpc）</h2>
 * <p><b>原形状的问题</b>：本类曾命名为 {@code *IT}，由 task-rpc 的 pom 里<b>第二个 surefire
 * execution</b>（绑在 {@code verify} 阶段）单独挑出来跑 —— 理由是「surefire 的默认执行绑在
 * {@code test} 阶段，而 fat jar 是 {@code package} 阶段才生成的 ⇒ {@code test} 阶段 jar
 * <b>还不存在</b>」。
 * <p>⇒ ⇒ <b>这个理由成立，但它带来一个更贵的代价</b>：{@code *IT} <b>不</b>匹配 surefire 的
 * 默认 include（{@code Test*.java} / {@code *Test.java} / …）⇒ ⇒
 * <b>收口回归（{@code mvn clean test}）跑不到它</b> ⇒ ⇒
 * <b>本域的产物层守门在收口口径里是缺失的</b>（实测：task-rpc 的 {@code clean test} 条数
 * 不含这 5 条 ⇒ ⇒ 「产物层已被验证」这句话在收口口径下<b>不成立</b>）。
 * <p>⇒ ⇒ <b>改法（照 like-rpc 的已验证形状）</b>：本类改名为 {@code *Test} ⇒ ⇒ 进默认执行
 * ⇒ ⇒ <b>并且</b>把「jar 还不存在」这个前提<b>消掉</b>：<b>由测试自己构建 fat jar</b>
 * ⇒ ⇒ 断言的产物是<b>当轮源码</b>打出来的（不是上一轮的旧副本）⇒ ⇒
 * 既进了收口口径，又比原形状<b>更严</b>（原形状在 {@code verify} 阶段读到的是本次构建的 jar，
 * 但收口口径里根本没有这条断言）。
 * <p>⚠️ <b>构建命令必须带 {@code -am}</b>：否则 {@code service/common} 不在 reactor 里
 * ⇒ ⇒ 它从 {@code ~/.m2} 取旧 jar ⇒ ⇒ <b>内嵌的 common jar 里没有共享层排序桥</b>
 * ⇒ ⇒ 第 ⑤ 条断言会红（且红的方向就是真实缺陷）。
 * <p>⚠️ <b>「新鲜度」必须把共享层算进去</b>：否则「共享层改了而本模块没改」时本模块的
 * fat jar <b>不会重建</b> ⇒ ⇒ 守门读到<b>陈旧的内嵌 jar</b> ⇒ ⇒ 假绿（掩盖共享层的破坏）
 * 或假红（报错方向被带偏）。
 *
 * <h2>「产物体积 ≫ library jar」怎么判</h2>
 * <p>{@code spring-boot:repackage} 会把原始 library jar <b>改名</b>成
 * {@code <artifactId>-<version>.jar.original} 留在同一个 {@code target/} 下
 * ⇒ <b>可以拿它当对照</b>：断言 fat jar 体积 <b>≫</b> library jar，
 * 就不可能被「一个手写的空 Main-Class」蒙混过关。
 *
 * <h2>⚠️ 本条<b>不是</b>「与 Go 对齐」</h2>
 * <p>本模块的 jar 能不能跑、classpath 上有没有某个 jar，
 * 与 Go 侧的行为<b>无关</b>（Go 侧是 go-zero，不产 jar）。
 * ⇒ 本条是「<b>RAG 侧写法非法导致不可部署</b>」的修复，不是行为漂移修复。
 */
class TaskRpcFatJarArtifactGateTest {

    /** 生产产物的主类 —— Boot 4 的 {@code JarLauncher} 在 {@code .launch.} 子包下。 */
    private static final String EXPECTED_MAIN_CLASS = "org.springframework.boot.loader.launch.JarLauncher";

    /** 生产产物自动探测到的主类（由 {@code spring-boot-maven-plugin} 探测 {@code *RpcApplication}）。 */
    private static final String EXPECTED_START_CLASS = "com.rag.task.task.rpc.TaskRpcApplication";

    @Test
    @DisplayName("真实 fat jar 的 Main-Class 必须是 Spring Boot 的 JarLauncher（否则 java -jar 起不来）")
    void fatJarMustHaveJarLauncherMainClass() throws Exception {
        Path jar = fatJar();
        String mainClass = manifestValue(jar, "Main-Class");

        assertEquals(EXPECTED_MAIN_CLASS, mainClass,
                "fat jar " + jar.getFileName() + " 的 Main-Class 必须是 " + EXPECTED_MAIN_CLASS
                        + "（Boot 4 的 JarLauncher 在 .launch. 子包下，**不是** Boot 2/3 的 "
                        + "org.springframework.boot.loader.JarLauncher）。"
                        + "实测缺它时产物是 83 KB 的 library jar ⇒ `java -jar` 报"
                        + "「no main manifest attribute」（中没有主清单属性）⇒ 模块不可部署。");
    }

    @Test
    @DisplayName("真实 fat jar 的 Start-Class 必须是 TaskRpcApplication（锁住「自动探测到正确主类」）")
    void fatJarMustStartAtTaskRpcApplication() throws Exception {
        Path jar = fatJar();
        String startClass = manifestValue(jar, "Start-Class");

        assertEquals(EXPECTED_START_CLASS, startClass,
                "fat jar 的 Start-Class 必须是 " + EXPECTED_START_CLASS
                        + "（spring-boot-maven-plugin 自动探测的主类）。"
                        + "本模块 pom 里**不写** <mainClass> ⇒ 由插件探测 "
                        + "service/task/task-rpc/src/main/java/com/rag/task/task/rpc/TaskRpcApplication.java。");
    }

    @Test
    @DisplayName("真实产物必须是 fat jar（体积 ≫ library jar，不能是空壳）")
    void fatJarMustBeMuchLargerThanLibraryJar() throws Exception {
        Path jar = fatJar();
        Path libraryJar = locateLibraryJar();

        long fatSize = Files.size(jar);
        long librarySize = Files.size(libraryJar);

        assertTrue(librarySize < 1_000_000L,
                "对照用的 library jar（" + libraryJar.getFileName() + "）应该有 10 万字节量级，"
                        + "实际 " + librarySize + " ⇒ 构建产物状态异常，本门禁结论不可信");
        assertTrue(fatSize > librarySize * 10L,
                "fat jar（" + fatSize + " B）必须远大于 library jar（" + librarySize + " B）"
                        + "（实测比值约 1183 倍）。"
                        + "只断言「有 Main-Class」会放过「一个手写的空 Main-Class」⇒ 必须同时比体积。");
    }

    @Test
    @DisplayName("真实 fat jar 的 BOOT-INF/lib/ 里必须有 grpc-inprocess（运行时 classpath 不能缺）")
    void fatJarMustCarryGrpcInprocessOnRuntimeClasspath() throws Exception {
        Path jar = fatJar();
        List<String> inprocessEntries = new ArrayList<>();

        try (JarFile jarFile = new JarFile(jar.toFile())) {
            var entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("BOOT-INF/lib/") && name.contains("grpc-inprocess")) {
                    inprocessEntries.add(name);
                }
            }
        }

        assertTrue(!inprocessEntries.isEmpty(),
                "fat jar 的 BOOT-INF/lib/ 里**没有** grpc-inprocess ⇒ 运行时 classpath 上它整个消失 ⇒ "
                        + "devh 的 net/devh/boot/grpc/server/serverfactory/InProcessGrpcServerFactory "
                        + "硬引用 io/grpc/inprocess/InProcessServerBuilder（实测 javap：该类 4 处引用 "
                        + "io/grpc/inprocess）⇒ 运行期链接失败 ⇒ 服务起不来。"
                        + "根因是 Maven 的「最近者优先」：模块里的**直接声明**（原为 <scope>test</scope>）"
                        + "压过了 net.devh:grpc-server-spring-boot-starter 传递进来的 compile 声明。");
    }

    /**
     * 🔴 <b>产物层第 ④ 条守门（2026-10-27 第十七轮新增）</b>：断言 fat jar 里内嵌的那份
     * {@code common-*.jar} <b>带着共享层的 MyBatis-Plus 自动配置排序桥</b>。
     *
     * <p><b>它守的是什么缺陷</b>：域侧排序桥收编到共享层之后，排序边<b>只</b>由共享层那份提供
     * ⇒ ⇒ 若 fat jar 里嵌的是<b>旧</b>的 {@code ~/.m2} 里的 {@code common} jar
     * （构建 fat jar 的命令<b>不带 {@code -am}</b> ⇒ {@code service/common} 不在 reactor 里
     * ⇒ ⇒ 只能从本地仓库取）⇒ ⇒ <b>桥不在 fat jar 里</b> ⇒ ⇒ MyBatis-Plus 的
     * {@code @AutoConfigureAfter}（<b>Boot 3 的包名</b>）在 Boot 4 下<b>悬空</b>
     * ⇒ ⇒ MP 自动配置在 DataSource 之前求值 ⇒ ⇒
     * {@code @ConditionalOnSingleCandidate(DataSource)} 失败 ⇒ ⇒
     * <b>不产 {@code SqlSessionFactory}</b> ⇒ ⇒ <b>真进程启动即挂</b>
     * （{@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}）。
     *
     * <p>⚠️ <b>为什么本类必须自己守这条</b>：本类<b>只解包、不起进程</b> ⇒ ⇒ 它<b>抓不到</b>
     * 那个缺陷（症状要到 like 域的 {@code LikeRpcRuntimeBootGateTest} 才暴露）⇒ ⇒
     * **本域的产物层守门存在一个「解包但不起进程 ⇒ 看不见排序边」的缺口** ⇒ ⇒ **本条把它补上**。
     *
     * <p>⚠️ <b>期望值用独立字面量</b>（共享层那个全限定名，来自共享层的
     * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}），
     * <b>不</b>从被测代码取常量 ⇒ ⇒ 共享层改包名时这条会<b>如实转红</b>（= 提醒同步）。
     */
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
            assertNotNull(commonEntry, "fat jar " + jar.getFileName() + " 的 BOOT-INF/lib/ 里没有 common-*.jar ⇒ "
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
     *
     * <p>🔴 <b>为什么必须自己构建</b>：本类跑在 surefire 默认的 {@code test} 阶段
     * ⇒ ⇒ fat jar（{@code package} 阶段的产物）<b>还不存在</b>
     * ⇒ ⇒ 不自己构建就只能验「上一轮的旧副本」⇒ ⇒ 判定失真。
     * ⇒ ⇒ 自建还顺带解决了「{@code test} 阶段没有 jar」这个原 {@code *IT} 形状的理由
     * ⇒ ⇒ 本类因此可以回到默认执行 ⇒ ⇒ <b>进收口口径</b>。
     */
    private static Path fatJar() throws Exception {
        Path moduleDir = locateModuleDir();
        Path target = moduleDir.resolve("target");
        Path jar = newestFatJar(target);
        if (jar == null || isStale(jar, moduleDir)) {
            buildFatJar(moduleDir);
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
     * <p><b>漏了它会发生什么（机制）</b>：本守门断言的是<b>fat jar 里内嵌的那份 common jar</b>
     * ⇒ ⇒ 而内嵌的那份由<b>构建时</b>的 {@code service/common} 决定
     * ⇒ ⇒ 若<b>只有共享层改了、本模块没改</b> ⇒ ⇒ 本模块的 {@code pom.xml} 与 {@code src/main}
     * 都没变 ⇒ ⇒ 「旧于 jar」这个判据<b>不成立</b> ⇒ ⇒ <b>fat jar 不会重建</b>
     * ⇒ ⇒ 守门读到<b>上一轮的陈旧内嵌 jar</b> ⇒ ⇒
     * <ul>
     *   <li><b>假绿</b>：共享层刚把排序桥删了 ⇒ 陈旧内嵌 jar 里<b>还带着桥</b>
     *       ⇒ ⇒ 第 ⑤ 条断言照样绿 ⇒ ⇒ <b>共享层的破坏被守门掩盖</b>；</li>
     *   <li><b>假红</b>：共享层刚把排序桥改名/新增 ⇒ 陈旧内嵌 jar 里<b>没有</b>新名字
     *       ⇒ ⇒ 第 ⑤ 条断言红 ⇒ ⇒ 报错指向「构建命令不带 -am」
     *       ⇒ ⇒ <b>而真实原因是守门读了旧字节</b> ⇒ ⇒ 排查方向被带偏。</li>
     * </ul>
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
        Path sharedModule = moduleDir.getParent().getParent().resolve("common");
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
     * ⇒ ⇒ 它从 <b>{@code ~/.m2}</b> 取 jar ⇒ ⇒ 而那份可能<b>早于共享层排序桥的落码</b>
     * ⇒ ⇒ <b>内嵌的 common jar 里没有桥</b> ⇒ ⇒ 第 ⑤ 条断言红（且红的方向就是真实缺陷）。
     */
    private static void buildFatJar(Path moduleDir) throws Exception {
        Path repoRoot = moduleDir.getParent().getParent().getParent();
        assertTrue(Files.exists(repoRoot.resolve("pom.xml")),
                "推算仓库根失败（" + repoRoot + " 下没有 pom.xml）⇒ 无法以 -pl 构建本模块。");
        ProcessBuilder builder = new ProcessBuilder(List.of(
                "mvn", "-o", "-q", "package", "-Dmaven.test.skip=true",
                "-pl", "service/task/task-rpc", "-am"))
                .directory(repoRoot.toFile())
                .redirectErrorStream(true);
        Process process = builder.start();
        String log = drainToString(process);
        int exit = process.waitFor();
        assertEquals(0, exit, "构建 fat jar 失败（mvn package -pl service/task/task-rpc -am）：\n" + log);
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

    /** 找到 {@code spring-boot:repackage} 改名留下的 library jar（体积对照用）。 */
    private static Path locateLibraryJar() throws IOException {
        Path target = locateModuleDir().resolve("target");
        try (var stream = Files.list(target)) {
            List<Path> originals = stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar.original"))
                    .sorted()
                    .toList();
            assertTrue(!originals.isEmpty(),
                    target + " 下**没有** *.jar.original ⇒ 说明 spring-boot:repackage 没跑过 "
                            + "⇒ 产物是普通 library jar（不可执行）。"
                            + "实测无 plugin 时产物只有 83,827 B，`java -jar` 直接报「中没有主清单属性」。");
            return originals.get(0);
        }
    }

    /**
     * 向上找本模块目录。
     *
     * <p>Surefire 的工作目录默认是模块目录（{@code ${basedir}}），
     * 但从仓库根跑 {@code mvn -pl} 时也可能不同 ⇒ 逐级向上找，
     * 且要求命中路径里带 {@code task-rpc}，避免误取别的模块。
     */
    private static Path locateModuleDir() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            if (dir.toString().contains("task-rpc")
                    && Files.exists(dir.resolve("pom.xml"))
                    && Files.exists(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "找不到 task-rpc 模块目录（工作目录：" + Paths.get("").toAbsolutePath() + "）");
    }
}
