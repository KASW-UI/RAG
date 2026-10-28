package com.rag.like.like.api.config;

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
 * 🔴 like-api 的 **验签密钥 fail-closed** 守门（P0 #8 · 2026-10-29 · R1·T-1.3）。
 *
 * <h3>守的是什么</h3>
 * <p>主 yaml 的 {@code like-api.user-auth.access-secret: ${USER_JWT_SECRET}}（**无默认值**）
 * ⇒ 变量没设时启动就失败；但 ⚠️ **变量设成空串时 Spring 会把空串当合法值**
 * （{@code PropertyPlaceholderHelper}：**不走默认值、也不报错**）⇒ ⇒ 属性绑成 {@code ""}
 * ⇒ ⇒ 应用带着**空签名密钥**正常起来 ⇒ ⇒ 🔴 **token 可伪造**（HMAC 空密钥谁都能算）。
 * ⇒ ⇒ 本类钉住 {@code LikeApiProperties#verifyAccessSecret} 在**真实装配**里真的挡住了。
 *
 * <h3>为什么不能只断言「方法会抛」</h3>
 * <p>AGENTS.md §3.3.3：<b>注解存在 ≠ 机制生效</b> ⇒ ⇒ {@code @PostConstruct} 写对了也可能没人调
 * ⇒ ⇒ 第 ① 条用**真 ApplicationContextRunner** 证明「上下文起不来」（运行证据，不是源码断言）。
 *
 * <h3>第 ④ 条为什么必须有（同一个坑已经咬过一次）</h3>
 * <p>2026-10-29 · article-api：改 yaml 时把 {@code auth:} 整行删掉了 ⇒ 属性**绑不上**
 * ⇒ 组件**根本起不来**，而当时全部用例**全绿**（因为它们只用 {@code withPropertyValues}，
 * **从不读真 yaml**）⇒ ⇒ 「跑绿 ≠ 对」的教科书案例 ⇒ ⇒ 第 ④ 条**读真 yaml**。
 */
class LikeApiPropertiesSecretGuardTest {

    /** 空 secret ⇒ 装配期就炸 ⇒ 上下文起不来。 */
    @Test
    @DisplayName("🔴 空签名密钥 ⇒ 启动失败（真 ApplicationContext：上下文起不来 ⇒ fail-closed 生效）")
    void blankSecretFailsTheContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropsConfig.class)
                .withPropertyValues("like-api.user-auth.access-secret=")
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
                .withPropertyValues("like-api.user-auth.access-secret=ae0536f9-6450-4606-8e13-5a19ed505da0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LikeApiProperties.class).getUserAuth().getAccessSecret())
                            .isEqualTo("ae0536f9-6450-4606-8e13-5a19ed505da0");
                });
    }

    /** 判据本身的边界（纯单元，不依赖容器）。 */
    @Test
    @DisplayName("🔴 判据边界：null / 空串 / 全空白 ⇒ 抛；正常值 ⇒ 不抛")
    void guardRejectsBlankAndAcceptsRealSecret() {
        LikeApiProperties props = new LikeApiProperties();

        props.getUserAuth().setAccessSecret(null);
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getUserAuth().setAccessSecret("");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getUserAuth().setAccessSecret("   ");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getUserAuth().setAccessSecret("ae0536f9-6450-4606-8e13-5a19ed505da0");
        assertDoesNotThrow(props::verifyAccessSecret);
    }

    /**
     * 🔴🔴 <b>读<b>真 yaml</b>的回归网</b>（同款写法见 article-api 的同名用例）。
     *
     * <p>上面三条用例都用 {@code withPropertyValues("like-api.user-auth.access-secret=…")}
     * —— 也就是<b>直接喂键路径</b>，<b>从不读</b> {@code src/main/resources/application.yml}
     * ⇒ ⇒ <b>yaml 里的键路径写错，它们照样全绿</b>。
     *
     * <p>⇒ 本用例把<b>真</b> {@code application.yml} 当成属性源塞进上下文
     * ⇒ ⇒ 再断言<b>嵌套字段</b> {@code getUserAuth().getAccessSecret()} 真的拿到了值
     * ⇒ ⇒ yaml 少一层 / 键名写错 ⇒ 断言立刻红。
     */
    @Test
    @DisplayName("🔴🔴 主 application.yml 的 access-secret 必须真的绑到 UserAuth 嵌套字段（读真 yaml，不靠 withPropertyValues）")
    void realYamlBindsSecretIntoNestedUserAuth() {
        new ApplicationContextRunner()
                .withInitializer(context -> {
                    try {
                        Resource yaml = new ClassPathResource("application.yml");
                        new YamlPropertySourceLoader().load("main-yaml", yaml)
                                .forEach(ps -> context.getEnvironment().getPropertySources().addLast(ps));
                    } catch (IOException e) {
                        throw new IllegalStateException("读不到主 application.yml（src/main/resources）", e);
                    }
                })
                .withUserConfiguration(PropsConfig.class)
                .withPropertyValues("USER_JWT_SECRET=ae0536f9-6450-4606-8e13-5a19ed505da0")
                .run(context -> {
                    // ⚠️ 绑定落空时，`verifyAccessSecret()` 会在 @PostConstruct 抛 ⇒ 上下文起不来
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LikeApiProperties.class).getUserAuth().getAccessSecret())
                            .isEqualTo("ae0536f9-6450-4606-8e13-5a19ed505da0");
                });
    }

    /** 让 {@link LikeApiProperties} 进入真实 Spring 生命周期（{@code @PostConstruct} 才会被调）。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LikeApiProperties.class)
    static class PropsConfig {
    }
}
