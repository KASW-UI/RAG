package com.rag.message.message.rpc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>点名式防回退</b>：把「三个（现在是四个）会让本组件<b>直接起不来</b> / 静默失效」的装配点
 * <b>逐条点名</b>断言，而不是靠「上下文能起来」间接锁住。
 *
 * <h2>为什么必须点名（UNRESOLVED-MSG-004 的原话）</h2>
 * <p>此前这几点只被 {@code MessageTransactionWiringTest} 的「上下文能起」<b>间接</b>锁住：
 * 有人删掉其中任何一项，测试<b>会红</b>，但<b>红的原因不点名</b> ——
 * 事后翻日志才知道是哪一条 ⇒ 修的时候容易改错地方、或顺手改个别的来「让它绿」。
 * ⇒ 本类把它们逐条拆开、各自点名。
 *
 * <h2>本类点名的四项</h2>
 * <table border="1">
 *   <caption>点名清单</caption>
 *   <tr><th>项</th><th>删掉后的症状</th><th>来源</th></tr>
 *   <tr><td>{@code @MapperScan}</td>
 *       <td>{@code No qualifying bean of type '…mapper.NotificationMapper'}
 *           ⇒ {@code UnsatisfiedDependencyException: messageAtomicService}</td>
 *       <td>DRIFT-MSG-005</td></tr>
 *   <tr><td>NacosRegistration 的<b>显式 Bean 名</b></td>
 *       <td>与 {@code NacosServiceRegistryAutoConfiguration} 的默认 Bean 名
 *           {@code nacosRegistration} <b>撞车</b> ⇒ 上下文启动即失败
 *           （Boot 2.1+ 默认 {@code allow-bean-definition-overriding=false}）</td>
 *       <td>DRIFT-MSG-004</td></tr>
 *   <tr><td>{@code spring-boot-flyway} 在 classpath</td>
 *       <td>Boot 4 把 Flyway 自动配置拆成独立模块 ⇒ 缺它时
 *           <b>启动日志里连一行 Flyway 都没有</b>（不是报错，是根本没加载）
 *           ⇒ 迁移脚本从不执行 ⇒ 表永不创建 ⇒ 第一次打 SQL 才炸</td>
 *       <td>DRIFT-MSG-006</td></tr>
 *   <tr><td>{@code spring.flyway.baseline-version: 0}</td>
 *       <td>共享库里 schema 已非空 ⇒ baseline 落在 version 1 ⇒
 *           <b>V1 被整条跳过</b> ⇒ {@code message_notification} 建不出来
 *           ⇒ V5 失败 ⇒ <b>服务起不来</b></td>
 *       <td>DRIFT-MSG-V-027</td></tr>
 * </table>
 *
 * <h2>可证伪性</h2>
 * 逐条删/改上面四项中的任意一项，本类<b>应当立刻红</b>，且红的信息里<b>点名</b>是哪一项。
 */
@DisplayName("点名式防回退：四项「起不来 / 静默失效」的装配点必须逐条点名")
class MessageContextWiringTest {

    // ==================================================================
    // ① @MapperScan（DRIFT-MSG-005）
    // ==================================================================

    @Test
    @DisplayName("① @MapperScan 必须指向本模块的 mapper 包（删掉 ⇒ 3 个 Mapper 注册不上 ⇒ 上下文起不来）")
    void mapperScanMustPointAtThisModule() {
        var scan = MessageRpcApplication.class
                .getAnnotation(org.mybatis.spring.annotation.MapperScan.class);
        assertThat(scan)
                .as("@MapperScan 缺失 ⇒ NotificationMapper / ConversationMapper / "
                        + "ConversationMessageMapper 三个 Bean 全部注册不上")
                .isNotNull();
        assertThat(List.of(scan.value()))
                .as("@MapperScan 的包名必须是 com.rag.message.message.rpc.mapper")
                .contains("com.rag.message.message.rpc.mapper");
    }

    // ==================================================================
    // ② NacosRegistration 的显式 Bean 名（DRIFT-MSG-004）
    // ==================================================================

    @Test
    @DisplayName("② NacosRegistration 必须用显式 Bean 名（默认名 nacosRegistration 会与 Nacos 自动配置撞车）")
    void nacosRegistrationMustUseExplicitBeanName() {
        var component = com.rag.message.message.rpc.bootstrap.NacosRegistration.class
                .getAnnotation(org.springframework.stereotype.Component.class);
        assertThat(component)
                .as("NacosRegistration 必须带 @Component")
                .isNotNull();
        assertThat(component.value())
                .as("⚠️ 默认 Bean 名 'nacosRegistration' 与 NacosServiceRegistryAutoConfiguration 撞车 "
                        + "⇒ 上下文启动即失败。必须显式命名（user-rpc / admin-rpc 早就这么写了）")
                .isEqualTo("messageRpcNacosRegistration");
    }

    // ==================================================================
    // ③ spring-boot-flyway 在 classpath（DRIFT-MSG-006）
    // ==================================================================

    @Test
    @DisplayName("③ spring-boot-flyway 必须在 classpath（Boot 4 拆模块后缺它 ⇒ 迁移脚本从不执行）")
    void flywayAutoConfigurationMustBeOnClasspath() {
        // ⚠️ **不能**去读 spring-boot-autoconfigure 那份 AutoConfiguration.imports 来找 FlywayAutoConfiguration：
        //    Boot 4 把 Flyway 自动配置**拆到了独立模块** spring-boot-flyway，
        //    它带的是**自己那份** META-INF/spring/...imports（类路径上有两份同名资源，
        //    ClassLoader.getResource() 只返回**第一份** ⇒ 读到的往往是 autoconfigure 那份，里面没有 Flyway）。
        // ⇒ 直接断言**类可加载**才是可靠的判据（这正是 DRIFT-MSG-006 的失效点：
        //    缺 spring-boot-flyway 时，这个类根本不在 classpath 上 ⇒ 自动配置从未加载）。
        assertThat(classPresent("org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"))
                .as("spring-boot-flyway 不在 classpath ⇒ Flyway 自动配置**从未加载** ⇒ "
                        + "启动日志里连一行 Flyway 都没有（不是报错，是根本没加载）⇒ "
                        + "表永不创建 ⇒ 第一次打 SQL 才炸")
                .isTrue();

        assertThat(classPresent("org.flywaydb.core.Flyway"))
                .as("flyway-core（引擎本体）必须在 classpath 上")
                .isTrue();
    }

    /** 类是否在 classpath 上（只判存在，不初始化）。 */
    private static boolean classPresent(String fqn) {
        try {
            Class.forName(fqn, false, MessageContextWiringTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    // ==================================================================
    // ④ Flyway 的 baseline-version（DRIFT-MSG-V-027）
    // ==================================================================

    @Test
    @DisplayName("④ spring.flyway.baseline-version 必须是 0（否则共享库里 V1 被整条跳过 ⇒ 服务起不来）")
    void flywayBaselineVersionMustBeZero() {
        String yml = readApplicationYml();
        assertThat(yml)
                .as("application.yml 必须显式钉 baseline-version: 0 —— "
                        + "共享库（first_db）里 schema 已被 users 域建表弄成非空 ⇒ "
                        + "默认 baseline-version=1 会让 V1 被整条跳过")
                .contains("baseline-version: 0");
        assertThat(yml)
                .as("独立账本表名必须显式指定（三个模块同库共用 public schema，账本撞号 ⇒ 后启动者的迁移永远建不出来）")
                .contains("flyway_schema_history_message");
    }

    private static String readApplicationYml() {
        try (InputStream in = new ClassPathResource("application.yml").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("读取 application.yml 失败", e);
        }
    }
}
