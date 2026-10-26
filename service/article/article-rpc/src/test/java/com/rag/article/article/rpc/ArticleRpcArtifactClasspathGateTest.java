package com.rag.article.article.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
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
 * 进程级的「真起得来」由 {@code ArticleRpcRuntimeBootGateTest} 承担（那条需要 Docker ⇒ 无 Docker 时整类 skip）。
 */
class ArticleRpcArtifactClasspathGateTest {

    /** 本模块在仓库里的路径（相对仓库根）。 */
    static final String MODULE_PATH = "service/article/article-rpc";

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
    // helpers（package-private ⇒ 同包的进程级守门类复用）
    // ==================================================================

    /** 解析本模块的<b>运行时</b> classpath（= fat jar 的 BOOT-INF/lib 那一层，不含 test scope）。 */
    static String runtimeClasspath() throws Exception {
        Path outputFile = Files.createTempFile("l12-article-runtime-cp", ".txt");
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

    /** jar 比 pom / 源码旧 ⇒ 构建产物是旧的 ⇒ 必须重建。 */
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
        for (Path input : inputs) {
            if (Files.exists(input) && input.toFile().lastModified() > jarTime) {
                return true;
            }
        }
        return false;
    }

    /** 构建 fat jar（跳过测试 —— 本类自己就是那个测试）。 */
    private static void buildFatJar() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(List.of(
                "mvn", "-o", "-q", "package", "-Dmaven.test.skip=true", "-pl", MODULE_PATH))
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
