package com.rag.article.article.api.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 🔴 article-api 的 **验签密钥 fail-closed** 守门（P0 #8 · 2026-10-29 · G3 阶段 1）。
 *
 * <h3>守的是什么</h3>
 * <p>主 yaml 的 {@code article-api.auth.access-secret: ${USER_JWT_SECRET}}（**无默认值**）
 * ⇒ 变量没设时启动就失败；但 ⚠️ **变量设成空串时 Spring 会把空串当合法值**
 * （{@code PropertyPlaceholderHelper}：**不走默认值、也不报错**）⇒ ⇒ 属性绑成 {@code ""}
 * ⇒ ⇒ 应用带着**空签名密钥**正常起来 ⇒ ⇒ 🔴 **token 可伪造**（HMAC 空密钥谁都能算）。
 * ⇒ ⇒ 本类钉住 {@code ArticleApiProperties#verifyAccessSecret} 在**真实装配**里真的挡住了。
 *
 * <h3>为什么不能只断言「方法会抛」</h3>
 * <p>AGENTS.md §3.3.3：<b>注解存在 ≠ 机制生效</b> ⇒ ⇒ {@code @PostConstruct} 写对了也可能没人调
 * ⇒ ⇒ ④ 那条用**真 ApplicationContextRunner** 证明「上下文起不来」（运行证据，不是源码断言）。
 */
class ArticleApiPropertiesSecretGuardTest {

    /** 空 secret ⇒ 装配期就炸 ⇒ 上下文起不来。 */
    @Test
    @DisplayName("🔴 空签名密钥 ⇒ 启动失败（真 ApplicationContext：上下文起不来 ⇒ fail-closed 生效）")
    void blankSecretFailsTheContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropsConfig.class)
                .withPropertyValues("article-api.auth.access-secret=")
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
                .withPropertyValues("article-api.auth.access-secret=ae0536f9-6450-4606-8e13-5a19ed505da0")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ArticleApiProperties.class).getAuth().getAccessSecret())
                            .isEqualTo("ae0536f9-6450-4606-8e13-5a19ed505da0");
                });
    }

    /** 判据本身的边界（纯单元，不依赖容器）。 */
    @Test
    @DisplayName("🔴 判据边界：null / 空串 / 全空白 ⇒ 抛；正常值 ⇒ 不抛")
    void guardRejectsBlankAndAcceptsRealSecret() {
        ArticleApiProperties props = new ArticleApiProperties();

        props.getAuth().setAccessSecret(null);
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getAuth().setAccessSecret("");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getAuth().setAccessSecret("   ");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getAuth().setAccessSecret("ae0536f9-6450-4606-8e13-5a19ed505da0");
        assertDoesNotThrow(props::verifyAccessSecret);
    }

    /** 让 {@link ArticleApiProperties} 进入真实 Spring 生命周期（{@code @PostConstruct} 才会被调）。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ArticleApiProperties.class)
    static class PropsConfig {
    }
}
