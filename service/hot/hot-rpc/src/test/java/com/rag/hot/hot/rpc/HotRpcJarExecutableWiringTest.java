package com.rag.hot.hot.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>DRIFT-HOTRPC-BOOT-001 的「配置层」防回退</b>：hot-rpc 的 pom <b>必须</b>声明
 * {@code spring-boot-maven-plugin}，否则产物是 <b>library jar</b>，{@code java -jar} 起不来。
 *
 * <h2>缺陷形态（2026-10-26 实测）</h2>
 * <p>本模块此前的 pom 的 {@code <build>} 段<b>只有</b> {@code maven-surefire-plugin}（Ryuk 关闭配置），
 * <b>没有</b> {@code spring-boot-maven-plugin} ⇒ 没有 {@code repackage} goal
 * ⇒ 打出来的是 <b>58,873 B 的 library jar</b>（同仓可部署的 rpc 组件是 60~105 MB）
 * ⇒ {@code META-INF/MANIFEST.MF} 里<b>零个</b> {@code Main-Class} / {@code Start-Class}
 * ⇒ {@code java -jar hot-rpc.jar} 报「<b>中没有主清单属性</b>」（no main manifest attribute）
 * ⇒ <b>hot-rpc 从来没被打成过可部署产物</b>。
 *
 * <h2>为什么这里只断言 pom，而真正的产物校验在另一个类里</h2>
 * <p><b>分层是刻意的</b>（不是妥协）：
 * <ul>
 *   <li><b>本类</b>（{@code test} 阶段）断言 <b>配置层</b>：插件挂在 {@code <build><plugins>} 下、
 *       没有写死 {@code <mainClass>}、Ryuk 段还在。
 *       ⇒ 它在<b>任何</b> {@code mvn test} 里都会跑，是「有人把这段删了」的第一道闸。</li>
 *   <li><b>{@link HotRpcFatJarArtifactGateIT}</b>（{@code integration-test} 阶段，在 {@code package} <b>之后</b>）
 *       <b>解包真实产物</b>断言 {@code Main-Class} / {@code Start-Class} / fat jar 结构。
 *       ⇒ surefire 的 {@code test} 阶段跑在 {@code package} <b>之前</b>，
 *       此时 jar <b>还不存在</b> ⇒ 产物层断言只能落在更晚的阶段。</li>
 * </ul>
 *
 * <p>⚠️ <b>只断言 pom 是不够的</b>（AGENTS.md §3.3：「注解/配置存在」≠「机制生效」）：
 * 插件写对了但 repackage 没跑，产物照样不可执行 ⇒ 产物层守门是必需的，不是冗余。
 *
 * <p>⚠️ <b>注释里的字面量会打败结构断言</b> ⇒ 本类断言前<b>先剥掉 XML 注释</b>
 * （本 pom 的说明性注释里就写着「{@code spring-boot-maven-plugin}」这些词）。
 *
 * <h2>本 lane 不做的事</h2>
 * <p>⛔ 不改 {@code tools/difftest/check-jar-executable.sh}（L15 拥有）——
 * 该脚本当前只扫 {@code *-api} ⇒ <b>10 个 rpc 模块的产物可执行性从来没有被任何守门查过</b>
 * ⇒ 这个系统性缺口由 L15 统一补进脚本；本类只是 hot-rpc 的<b>模块级</b>闸。
 */
@DisplayName("DRIFT-HOTRPC-BOOT-001：hot-rpc 的 pom 必须声明 spring-boot-maven-plugin（否则产物是 library jar）")
class HotRpcJarExecutableWiringTest {

    private static String readPom() {
        try {
            File pom = new File("pom.xml");
            assertThat(pom)
                    .as("找不到模块自己的 pom.xml（surefire 的工作目录应为本模块 basedir）")
                    .exists();
            return Files.readString(pom.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("读取 pom.xml 失败", e);
        }
    }

    /** 去掉 {@code <!-- ... -->} 注释块，避免注释里的字面量干扰结构断言。 */
    private static String stripXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    @Test
    @DisplayName("pom 必须有 <build> 段且含 spring-boot-maven-plugin（缺了 ⇒ library jar ⇒ java -jar 起不来）")
    void pomMustDeclareSpringBootMavenPlugin() {
        // ⚠️ 必须剥掉 XML 注释：本 pom 的说明性注释里就写着「spring-boot-maven-plugin」
        //    ⇒ 不剥的话，**删掉 plugin 也照样通过** ⇒ 这条守门会恒真（实测：M1 变异时它没红）。
        String pom = stripXmlComments(readPom());

        assertThat(pom)
                .as("hot-rpc 的 pom 必须有 <build> 段 —— "
                        + "没有它就没有 spring-boot-maven-plugin 的 repackage ⇒ "
                        + "产物是普通 library jar ⇒ `java -jar` 报「中没有主清单属性」")
                .contains("<build>");

        assertThat(pom)
                .as("pom 必须声明 spring-boot-maven-plugin（Boot 3+ 起 jar 不会自动可执行）")
                .contains("spring-boot-maven-plugin");
    }

    @Test
    @DisplayName("plugin 必须挂在 <build><plugins> 下（只在 pluginManagement 里等于没生效）")
    void pluginMustBeUnderBuildPlugins() {
        String pom = stripXmlComments(readPom());

        int buildIdx = pom.indexOf("<build>");
        int pluginsIdx = pom.indexOf("<plugins>", buildIdx);
        int pluginIdx = pom.indexOf("spring-boot-maven-plugin", buildIdx);
        int pluginMgmtIdx = pom.indexOf("<pluginManagement>");

        assertThat(buildIdx).as("pom 必须有 <build> 段").isGreaterThanOrEqualTo(0);
        assertThat(pluginsIdx).as("<build> 下必须有 <plugins>").isGreaterThan(buildIdx);
        assertThat(pluginIdx).as("pom 必须声明 spring-boot-maven-plugin").isGreaterThan(0);
        assertThat(pluginIdx)
                .as("⚠️ plugin 必须挂在 <build><plugins> 下 —— "
                        + "只写在 <pluginManagement> 里**等于没生效**（那只是版本/配置的管理区）")
                .isGreaterThan(pluginsIdx);

        if (pluginMgmtIdx >= 0) {
            assertThat(pluginIdx)
                    .as("⚠️ pluginManagement 段在 <build> 之前；<build> 里的那次声明才是真正生效的")
                    .isGreaterThan(buildIdx);
        }
    }

    @Test
    @DisplayName("不得写死 <mainClass>（自动探测才是与同仓其它 rpc 组件一致的做法）")
    void mustNotHardcodeMainClass() {
        String pom = stripXmlComments(readPom());

        assertThat(pom)
                .as("⚠️ 本模块**故意不写** <mainClass> —— 让 spring-boot-maven-plugin 自动探测 "
                        + "HotRpcApplication（唯一 main：HotRpcApplication.java:111）⇒ "
                        + "产物里 Start-Class 应为 com.rag.hot.hot.rpc.HotRpcApplication。"
                        + "写死它没有收益，却会让「主类改名」这类重构静默留下旧值。")
                .doesNotContain("<mainClass>");
    }

    @Test
    @DisplayName("surefire 的 Ryuk 关闭段必须保留（删了容器测试会挂 4.5 分钟）")
    void surefireRyukBlockMustBePreserved() {
        // ⚠️ 同样要剥注释：Ryuk 段的长注释里就写着 `TESTCONTAINERS_RYUK_DISABLED`
        //    ⇒ 不剥的话，**删掉整个 surefire 段也照样通过**。
        String pom = stripXmlComments(readPom());

        assertThat(pom)
                .as("必须保留 maven-surefire-plugin（删掉它 ⇒ Testcontainers 的 Ryuk 会去拉 "
                        + "testcontainers/ryuk:0.14.0 ⇒ 本机 Docker Hub 不通 ⇒ 整轮 IT 挂 4.5 分钟后失败）")
                .contains("maven-surefire-plugin");

        assertThat(pom)
                .as("surefire 段里的 Ryuk 关闭环境变量必须保留 —— "
                        + "Testcontainers 2.x 的 ResourceReaper.instance() 只读 "
                        + "System.getenv(\"TESTCONTAINERS_RYUK_DISABLED\")，"
                        + "写成 <ryuk.disabled> 这种 system property **完全无效**（字节码取证）")
                .contains("TESTCONTAINERS_RYUK_DISABLED");
    }
}
