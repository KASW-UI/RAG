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
import java.util.List;
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
 * <h2>⚠️ 为什么它不跑在 surefire 的默认 {@code test} 阶段</h2>
 * <p>surefire 的默认执行绑在 {@code test} 阶段，而 fat jar 是 {@code package} 阶段
 * （{@code maven-jar-plugin:jar} + {@code spring-boot:repackage}）才生成的
 * ⇒ {@code test} 阶段 jar <b>还不存在</b>。
 * <p>⇒ 本类命名为 {@code *IT}（<b>不</b>匹配 surefire 默认 include），
 * 由 task-rpc 的 pom 里<b>第二个 surefire execution</b>（绑在 {@code verify} 阶段）单独挑出来跑
 * ⇒ <b>产物必然是本次构建刚打出来的</b>（不是上一轮的旧副本），
 * 且判定仍落在 <b>surefire 的 XML</b>（PIT-GLOBAL-021：判定只认 XML，不认 stdout）。
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
class TaskRpcFatJarArtifactGateIT {

    /** 生产产物的主类 —— Boot 4 的 {@code JarLauncher} 在 {@code .launch.} 子包下。 */
    private static final String EXPECTED_MAIN_CLASS = "org.springframework.boot.loader.launch.JarLauncher";

    /** 生产产物自动探测到的主类（由 {@code spring-boot-maven-plugin} 探测 {@code *RpcApplication}）。 */
    private static final String EXPECTED_START_CLASS = "com.rag.task.task.rpc.TaskRpcApplication";

    @Test
    @DisplayName("真实 fat jar 的 Main-Class 必须是 Spring Boot 的 JarLauncher（否则 java -jar 起不来）")
    void fatJarMustHaveJarLauncherMainClass() throws IOException {
        Path jar = locateFatJar();
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
    void fatJarMustStartAtTaskRpcApplication() throws IOException {
        Path jar = locateFatJar();
        String startClass = manifestValue(jar, "Start-Class");

        assertEquals(EXPECTED_START_CLASS, startClass,
                "fat jar 的 Start-Class 必须是 " + EXPECTED_START_CLASS
                        + "（spring-boot-maven-plugin 自动探测的主类）。"
                        + "本模块 pom 里**不写** <mainClass> ⇒ 由插件探测 "
                        + "service/task/task-rpc/src/main/java/com/rag/task/task/rpc/TaskRpcApplication.java。");
    }

    @Test
    @DisplayName("真实产物必须是 fat jar（体积 ≫ library jar，不能是空壳）")
    void fatJarMustBeMuchLargerThanLibraryJar() throws IOException {
        Path jar = locateFatJar();
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
    void fatJarMustCarryGrpcInprocessOnRuntimeClasspath() throws IOException {
        Path jar = locateFatJar();
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

    /** 找到本次构建产出的 fat jar（**排除** {@code .jar.original} 那个 library jar）。 */
    private static Path locateFatJar() throws IOException {
        Path target = locateModuleDir().resolve("target");
        assertTrue(Files.isDirectory(target),
                "找不到 " + target + " ⇒ 本模块没构建过 ⇒ 无法验产物。"
                        + "本门禁绑在 verify 阶段跑，构建产物必然存在。");

        try (var stream = Files.list(target)) {
            List<Path> jars = stream
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith(".jar.original"))
                    .sorted()
                    .toList();
            assertTrue(!jars.isEmpty(),
                    target + " 下没有 *.jar 产物（只有 .jar.original 的话说明 repackage 没跑）"
                            + " ⇒ 产物不可执行");
            return jars.get(0);
        }
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
