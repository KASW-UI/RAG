package com.rag.users.admin.api.config;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 🔴 admin-api 的 <b>签名/验签密钥 fail-closed</b> 守门（P0 #8 · 2026-10-29 · G1 阶段 1）。
 *
 * <h3>守的是什么</h3>
 * <p>主 yaml 的 {@code admin-api.admin-auth.access-secret: ${ADMIN_JWT_SECRET}}（<b>无默认值</b>）
 * ⇒ 变量没设时本该启动失败；但 ⚠️ <b>「变量没设」绑出来的不是 {@code null}，而是占位符字面量</b>
 * （Spring Boot 4 的 {@code SpringConfigurationPropertySource} 取<b>原始标量</b>，
 *   而 {@code PropertySourcesPlaceholdersResolver} 的 {@code PropertyPlaceholderHelper}
 *   带 {@code ignoreUnresolvablePlaceholders = true} ⇒ <b>解析不出来时占位符原样留下</b>）
 * ⇒ ⇒ 那个字面量是<b>公开在仓里的常量</b> ⇒ 拿它当 HMAC 密钥 = 密钥公开
 * ⇒ ⇒ 必须由 {@code AdminApiProperties#verifyAccessSecret} <b>显式判掉</b>。
 * ⇒ ⇒ 本类钉住它在<b>真实装配</b>里真的挡住了（运行证据，不是源码断言）。
 */
class AdminApiPropertiesSecretGuardTest {

    /** 与真源 Go 端 {@code service/user/admin/api/etc/admincenter.yaml:11} 同值（15 字节口令形状）。 */
    private static final String SECRET = "yU-hCm)2*?NQmWG";

    /** 环境变量名（yaml 里的占位符）。 */
    private static final String ENV = "ADMIN_JWT_SECRET";

    /** 绑定路径（<b>必须与 yaml 的嵌套层级一致</b>：{@code admin-api → admin-auth → access-secret}）。 */
    private static final String PROPERTY = "admin-api.admin-auth.access-secret";

    /** 主配置文件名。 */
    private static final String MAIN_YAML = "application.yml";

    /** 空 secret ⇒ 装配期就炸 ⇒ 上下文起不来。 */
    @Test
    @DisplayName("🔴 空签名密钥 ⇒ 启动失败（真 ApplicationContext：上下文起不来 ⇒ fail-closed 生效）")
    void blankSecretFailsTheContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropsConfig.class)
                .withPropertyValues(PROPERTY + "=")
                .run(context -> assertThat(context).hasFailed());

        new ApplicationContextRunner()
                .withUserConfiguration(PropsConfig.class)
                .run(context -> assertThat(context).hasFailed());
    }

    /** 正常 secret ⇒ 上下文正常起来（⇒ 不是「无脑拒绝启动」）。 */
    @Test
    @DisplayName("✅ 正常密钥 ⇒ 上下文正常起来（对照组：证明上一条不是「永远失败」）")
    void properSecretStartsTheContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropsConfig.class)
                .withPropertyValues(PROPERTY + "=" + SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AdminApiProperties.class).getAdminAuth().getAccessSecret())
                            .isEqualTo(SECRET);
                });
    }

    /** 判据本身的边界（纯单元，不依赖容器）。 */
    @Test
    @DisplayName("🔴 判据边界：null / 空串 / 全空白 ⇒ 抛；正常值 ⇒ 不抛")
    void guardRejectsBlankAndAcceptsRealSecret() {
        AdminApiProperties props = new AdminApiProperties();

        props.getAdminAuth().setAccessSecret(null);
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getAdminAuth().setAccessSecret("");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getAdminAuth().setAccessSecret("   ");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getAdminAuth().setAccessSecret(SECRET);
        assertDoesNotThrow(props::verifyAccessSecret);
    }

    /**
     * 🔴🔴 <b>判据必须能识别「未解析的占位符」</b> —— 这是<b>只判 null/空串</b>漏掉的那条路。
     *
     * <p>Spring Boot 4 的绑定路径：{@code SpringConfigurationPropertySource} 取<b>原始标量</b>
     * ⇒ 占位符解析交给 {@code PropertySourcesPlaceholdersResolver}
     * ⇒ 它的 {@code PropertyPlaceholderHelper(..., ignoreUnresolvablePlaceholders = true)}
     * ⇒ ⇒ <b>解析不出来时占位符原样留下</b> ⇒ ⇒ 绑进去的是 {@code "${ADMIN_JWT_SECRET}"}。
     *
     * <p>⚠️ 那个字面量是<b>公开在仓里的常量</b> ⇒ 若判据只判 null/空串 ⇒ 应用会带着它正常起来
     * ⇒ ⇒ 「缺变量 ⇒ 启动失败」是<b>假的</b> ⇒ ⇒ 必须显式判掉。
     */
    @Test
    @DisplayName("🔴 未解析的占位符字面量 ⇒ 判据必须抛（只判 null/空串会漏掉这条路）")
    void guardRejectsUnresolvedPlaceholder() {
        AdminApiProperties props = new AdminApiProperties();

        props.getAdminAuth().setAccessSecret(String.format("${%s}", ENV));
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getAdminAuth().setAccessSecret(String.format("${%s:%s}", ENV, "some-default"));
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);
    }

    /**
     * 🔴🔴 <b>读<b>真 yaml</b>的回归网</b>（2026-10-29 · G1 阶段 1 · P0 #8）。
     *
     * <h3>它挡的是什么</h3>
     * <p>上面几条用例都用 {@code withPropertyValues("admin-api.admin-auth.access-secret=…")} ——
     * 也就是<b>直接喂键路径</b>，<b>从不读</b> {@code src/main/resources/application.yml}。
     * ⇒ ⇒ <b>yaml 里的键路径写错（少一层 / 写错名），它们照样全绿</b>。
     *
     * <p>⚠️ 已有先例：P0 #8 的参考实现（{@code article-api}）在改 yaml 时<b>把 {@code auth:} 整行删掉了</b>
     * ⇒ 绑定路径变成 {@code article-api.access-secret} ⇒ 绑不上 ⇒ 服务根本起不来
     * ⇒ ⇒ 而<b>全部用例仍全绿</b>（因为它们只喂 {@code withPropertyValues}）⇒ 「跑绿 ≠ 对」。
     */
    @Test
    @DisplayName("🔴🔴 主 application.yml 的 access-secret 必须真的绑到 AdminAuth 嵌套字段（读真 yaml，不靠 withPropertyValues）")
    void realYamlBindsSecretIntoNestedAuth() {
        runnerWithRealYaml()
                .withPropertyValues(ENV + "=" + SECRET)
                .run(context -> {
                    // ⚠️ 绑定落空时，`verifyAccessSecret()` 会在 @PostConstruct 抛 ⇒ 上下文起不来
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AdminApiProperties.class).getAdminAuth().getAccessSecret())
                            .isEqualTo(SECRET);
                });
    }

    /**
     * 🔴🔴🔴 <b>真 yaml + 缺环境变量 ⇒ 启动失败</b>（这才是部署时真正会遇到的那条路）。
     *
     * <p>⚠️ 本用例是本模块与「参考实现」的关键差别：只判 null/空串的判据在本用例下
     * <b>会放行</b>（绑上来的是占位符字面量，不是 null）⇒ ⇒ 必须额外判「仍是占位符」。
     */
    @Test
    @DisplayName("🔴🔴🔴 真 yaml + 缺环境变量 ⇒ 上下文起不来（部署时的真实路径）")
    void realYamlWithoutEnvVarFailsTheContext() {
        runnerWithRealYaml()
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("access-secret");
                });
    }

    /** 把<b>真</b>主配置文件当属性源塞进上下文（不经 {@code withPropertyValues} ⇒ 不碰键路径）。 */
    private static ApplicationContextRunner runnerWithRealYaml() {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    try {
                        Resource yaml = new ClassPathResource(MAIN_YAML);
                        new YamlPropertySourceLoader().load("main-yaml", yaml)
                                .forEach(ps -> context.getEnvironment().getPropertySources().addLast(ps));
                    } catch (IOException e) {
                        throw new IllegalStateException("读不到主 " + MAIN_YAML + "（src/main/resources）", e);
                    }
                })
                .withUserConfiguration(PropsConfig.class);
    }

    /** 让 {@link AdminApiProperties} 进入真实 Spring 生命周期（{@code @PostConstruct} 才会被调）。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AdminApiProperties.class)
    static class PropsConfig {
    }
}
