package com.rag.message.message.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>DRIFT-MSG-V-028 的点名式防回退</b>：message-api 的 pom <b>必须</b>声明
 * {@code spring-boot-maven-plugin}，否则产出来的是<b>普通 library jar</b>，{@code java -jar} 直接报错。
 *
 * <h2>缺陷形态（2026-10-24 首次实测到）</h2>
 * <p>此前 message-api 的 pom <b>完全没有 {@code <build>} 段</b>
 * ⇒ 没有 {@code spring-boot-maven-plugin} 的 {@code repackage}
 * ⇒ 打出来的是 <b>76 KB 的 library jar</b>（message-rpc 的可执行 jar 是 69 MB）
 * ⇒ {@code java -jar message-api-*.jar} 报：
 * <pre>
 *   no main manifest attribute, in message-api-0.0.1-SNAPSHOT.jar
 *   （中文环境：「中没有主清单属性」）
 * </pre>
 * ⇒ <b>message-api 从来没被打成过可部署产物</b> ——
 * 此前所有验证都跑在容器里（{@code @SpringBootTest}），<b>从没跑过真 jar</b>。
 *
 * <p>⚠️ 这正是 §3.3「编译通过 ≠ 产物可运行」的形态：
 * 测试全绿、{@code mvn package} 成功，但产物根本起不来。
 *
 * <h2>为什么这里只断言 pom、不断言 jar</h2>
 * <p>因为 surefire 跑在 {@code test} 阶段，而 jar 是 {@code package} 阶段才生成的
 * ⇒ <b>测试运行时 jar 还不存在</b>（写了「jar 不存在就跳过」会引入 skipped，
 * 违反「绿灯必须额外断言 skipped=0」的纪律）。
 * ⇒ 「产物真的可执行」由 {@code tools/difftest/check-jar-executable.sh} 承担
 * （它 build 完之后逐个模块解包 MANIFEST 看 {@code Main-Class}）。
 * ⇒ 本类只负责**堵住「有人把 <build> 段删了」这个具体的回归**。
 */
@DisplayName("DRIFT-MSG-V-028：message-api 的 pom 必须声明 spring-boot-maven-plugin（否则 jar 不可执行）")
class MessageApiJarExecutableWiringTest {

    private static String readPom() {
        try {
            File pom = new File("pom.xml");
            assertThat(pom)
                    .as("找不到模块自己的 pom.xml（工作目录应为本模块 basedir）")
                    .exists();
            return Files.readString(pom.toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("读取 pom.xml 失败", e);
        }
    }

    @Test
    @DisplayName("pom 必须有 <build> 段且含 spring-boot-maven-plugin（缺了 ⇒ library jar ⇒ java -jar 起不来）")
    void pomMustDeclareSpringBootMavenPlugin() {
        String pom = readPom();

        assertThat(pom)
                .as("message-api 的 pom 必须有 <build> 段 —— "
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
        // ⚠️⚠️ **必须先剥掉 XML 注释再取下标** —— 否则会被**注释里提到的同名字符串**打败：
        //    本 pom 的说明性注释里就写着「没有 <build> 段」「spring-boot-maven-plugin」这些词
        //    ⇒ 直接 `indexOf("<build>")` 会先撞到**注释里那一次**（行 157），
        //    而不是真正的那次（行 169）⇒ 断言必然红，且红得莫名其妙。
        //    （这是本测试第一版的真实失败：4 个用例里 1 个红了，根因就是这里。）
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

    /** 去掉 {@code <!-- ... -->} 注释块，避免注释里的字面量干扰结构断言。 */
    private static String stripXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }
}
