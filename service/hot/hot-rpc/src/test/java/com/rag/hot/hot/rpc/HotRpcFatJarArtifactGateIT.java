package com.rag.hot.hot.rpc;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>DRIFT-HOTRPC-BOOT-001 的「产物层」防回退</b>：hot-rpc 打出来的 jar
 * <b>必须</b>是 Spring Boot <b>fat jar</b>，否则 {@code java -jar hot-rpc.jar} 起不来。
 *
 * <h2>缺陷形态（2026-10-26 实测）</h2>
 * <p>本模块此前的 pom 的 {@code <build>} 段<b>只有</b> {@code maven-surefire-plugin}（Ryuk 关闭配置），
 * <b>没有</b> {@code spring-boot-maven-plugin} ⇒ 没有 {@code repackage} goal
 * ⇒ 产物是 <b>58,873 B 的 library jar</b>，{@code META-INF/MANIFEST.MF} 里<b>零个</b>
 * {@code Main-Class} / {@code Start-Class}
 * ⇒ {@code java -jar hot-rpc.jar} 报「<b>中没有主清单属性</b>」（no main manifest attribute）。
 *
 * <h2>为什么本类断言的是<b>产物</b>而不是 pom（AGENTS.md §3.3）</h2>
 * <p>「pom 里写了 plugin」<b>不等于</b>「产物可执行」——
 * repackage 没跑 / 绑错阶段 / 主类探测失败，都会留下一个「pom 看着对、jar 起不来」的状态。
 * ⇒ 本类<b>解包真实产物</b>（{@link JarFile}），读 {@code META-INF/MANIFEST.MF} 的真实字节。
 *
 * <h2>⚠️ 为什么本类用 {@code *IT} 命名 + 绑到 {@code verify} 阶段</h2>
 * <p>Surefire 的 {@code test} 阶段跑在 {@code package} <b>之前</b> ⇒ 那一刻 jar <b>还不存在</b>
 * ⇒ 产物层断言必须落在 {@code package} <b>之后</b>的阶段（本模块用 {@code verify}）。
 * <ul>
 *   <li>类名用 {@code *IT} ⇒ surefire <b>默认</b> includes（以 {@code Test} 结尾的那批）<b>不匹配</b>
 *       ⇒ 不会被 {@code test} 阶段的默认执行误捞。</li>
 *   <li>pom 里给它<b>单独</b>挂一个 {@code verify} 阶段的 surefire 执行（见 pom 的
 *       {@code artifact-gate} 执行块）。</li>
 * </ul>
 *
 * <h2>⚠️ 找不到 jar 时的行为：<b>绝不假绿</b></h2>
 * <ul>
 *   <li><b>{@code verify} 阶段</b>（{@code hot.rpc.gate.phase=verify}）：jar 是<b>必须</b>存在的
 *       ⇒ 找不到 ⇒ <b>断言失败</b>（不是 skip，更不是 PASS）。</li>
 *   <li><b>其它阶段</b>（例如被 {@code -Dtest=} 强制在 {@code test} 阶段跑）：
 *       jar 还没打出来 ⇒ <b>明确 skip</b>并写清原因（surefire 报告里是 {@code skipped}，
 *       <b>不是</b> {@code passed}）⇒ 不会冒充「守门通过」。</li>
 * </ul>
 *
 * <p>⚠️ 断言 <b>不只</b>看「有没有 {@code Main-Class}」：
 * 一个手写的空壳 {@code Main-Class} 也能骗过那种写法 ⇒ 本类同时断言
 * {@code Start-Class} 指向本模块的主类、jar 里真有 {@code BOOT-INF/classes} + {@code BOOT-INF/lib}、
 * 且体积<b>远大于</b> library jar。
 *
 * <h2>本 lane 不做的事</h2>
 * <p>⛔ 不改 {@code tools/difftest/check-jar-executable.sh}（L15 拥有）——
 * 该脚本当前只扫 {@code *-api} ⇒ <b>10 个 rpc 模块的产物可执行性从来没被任何守门查过</b>
 * ⇒ 这个系统性缺口由 L15 统一补进脚本；本类只是 hot-rpc 的<b>模块级</b>闸。
 */
@DisplayName("DRIFT-HOTRPC-BOOT-001：hot-rpc 的产物必须是可执行的 Spring Boot fat jar")
class HotRpcFatJarArtifactGateIT {

    /** Boot 4 的启动器全名（<b>注意 {@code .launch.} 子包</b>——不是 Boot 2/3 的 {@code loader.JarLauncher}）。 */
    private static final String EXPECTED_MAIN_CLASS = "org.springframework.boot.loader.launch.JarLauncher";

    /** 自动探测到的启动类（唯一 main：HotRpcApplication.java:111）。 */
    private static final String EXPECTED_START_CLASS = "com.rag.hot.hot.rpc.HotRpcApplication";

    /** library jar 实测 58,873 B；可部署的 fat jar 是 60~105 MB ⇒ 10 MB 是保守得多的下限。 */
    private static final long MIN_FAT_JAR_BYTES = 10L * 1024 * 1024;

    @Test
    @DisplayName("真实产物的 MANIFEST 必须是 JarLauncher + HotRpcApplication，且 jar 必须是 fat jar")
    void artifactMustBeExecutableSpringBootFatJar() throws IOException {
        File jar = locateFatJar();

        if (jar == null) {
            // ⚠️ 绝不允许「找不到 jar ⇒ PASS」。verify 阶段 ⇒ 硬失败；其它阶段 ⇒ 明确 skip。
            if (isVerifyPhase()) {
                throw new AssertionError(
                        "❌ 找不到 hot-rpc 的产物（target/*.jar）—— 但本用例跑在 verify 阶段，"
                                + "此时 package 必然已经执行过 ⇒ 产物缺失本身是缺陷。"
                                + "请先 `mvn -o package -pl service/hot/hot-rpc -am` 看真实报错。");
            }
            Assumptions.assumeTrue(false,
                    "⏭️ hot-rpc 的产物尚未打包（本阶段早于 package）⇒ 本产物层守门本轮未执行。"
                            + "请用 `mvn -o verify -pl service/hot/hot-rpc -am` 跑它。");
            return;
        }

        assertThat(jar)
                .as("产物必须落在本模块的 target/ 下")
                .exists();

        Manifest manifest;
        try (JarFile jarFile = new JarFile(jar)) {
            manifest = jarFile.getManifest();
            Attributes main = manifest.getMainAttributes();

            // ---- ① Main-Class：必须是 Boot 4 的 JarLauncher（全名，含 .launch. 子包） ----
            String mainClass = main.getValue("Main-Class");
            assertThat(mainClass)
                    .as("❌ 产物不是 Spring Boot fat jar —— MANIFEST 的 Main-Class 缺失或不是 JarLauncher。"
                            + "缺它 ⇒ 没有 spring-boot-maven-plugin 的 repackage ⇒ "
                            + "打出来的是普通 library jar ⇒ `java -jar` 报「中没有主清单属性」。"
                            + "（实测修前：58,873 B，MANIFEST 里零个 Main-Class）")
                    .isEqualTo(EXPECTED_MAIN_CLASS);

            // ---- ② Start-Class：自动探测到的主类必须是本模块的（锁住「探测对了」） ----
            String startClass = main.getValue("Start-Class");
            assertThat(startClass)
                    .as("❌ Start-Class 必须是 %s（自动探测的结果）—— 它不对 ⇒ "
                            + "即使 Main-Class 是 JarLauncher，这个 jar 也起不来（"
                            + "Boot 的 JarLauncher 会用它去找真正的启动类）。",
                            EXPECTED_START_CLASS)
                    .isEqualTo(EXPECTED_START_CLASS);

            // ---- ③ fat jar 结构：真有 BOOT-INF/classes 与 BOOT-INF/lib（空壳骗不过去） ----
            List<String> entries = new ArrayList<>();
            jarFile.stream().forEach(e -> entries.add(e.getName()));

            assertThat(entries)
                    .as("❌ jar 里没有 BOOT-INF/classes/ ⇒ 不是 Spring Boot fat jar 的布局")
                    .anyMatch(name -> name.startsWith("BOOT-INF/classes/"));

            long libCount = entries.stream()
                    .filter(name -> name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar"))
                    .count();
            assertThat(libCount)
                    .as("❌ jar 里没有 BOOT-INF/lib/*.jar ⇒ 依赖没被打进去 ⇒ 不是可部署的 fat jar")
                    .isGreaterThan(10L);

            assertThat(entries)
                    .as("❌ jar 里没有 Boot 的启动器类 ⇒ Main-Class 指向的类不在包里 ⇒ java -jar 会直接失败")
                    .anyMatch(name -> name.equals(
                            "org/springframework/boot/loader/launch/JarLauncher.class"));
        }

        // ---- ④ 体积：fat jar 必须远大于 library jar（防「手写 Main-Class 的空壳」） ----
        long size = Files.size(jar.toPath());
        assertThat(size)
                .as("❌ 产物只有 %d B —— 修前实测的 library jar 是 58,873 B，"
                        + "同仓可部署的 rpc 组件是 60~105 MB ⇒ 这个体积说明它不是 fat jar。", size)
                .isGreaterThan(MIN_FAT_JAR_BYTES);
    }

    /**
     * 找本模块产物目录里的 jar —— <b>排除 {@code .jar.original}</b>（repackage 前的备份）。
     */
    private static File locateFatJar() throws IOException {
        Path target = Paths.get("target");
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> files = Files.list(target)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.getFileName().toString().endsWith(".jar.original"))
                    .filter(p -> !p.getFileName().toString().endsWith("-sources.jar"))
                    .filter(p -> !p.getFileName().toString().endsWith("-javadoc.jar"))
                    .filter(p -> p.getFileName().toString().startsWith("hot-rpc-"))
                    .findFirst()
                    .map(Path::toFile)
                    .orElse(null);
        }
    }

    /**
     * 本轮是否跑在 {@code verify} 阶段 —— 由 pom 里 {@code artifact-gate} 执行的
     * {@code systemPropertyVariables} 注入。
     */
    private static boolean isVerifyPhase() {
        return "verify".equals(System.getProperty("hot.rpc.gate.phase"));
    }
}
