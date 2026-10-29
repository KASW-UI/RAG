package com.rag.comment.comment.api.config;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 🔴 comment-api 的 **验签密钥 fail-closed** 守门（P0 #8 · 2026-10-29 · G3 阶段 1；
 * 2026-10-29 本轮补「未解析占位符」判据）。
 *
 * <h3>守的是什么</h3>
 * <p>主 yaml 的 {@code comment-api.user-auth.access-secret: ${USER_JWT_SECRET}}（**无默认值**）
 * ⇒ ⇒ <b>缺变量 / 空串 / 占位符原文</b> 三条路都要 <b>启动失败</b>；变量正常 ⇒ 正常启动。
 *
 * <h3>⚠️ 守门背后那条<b>实测</b>出来的机制（2026-10-29 · 本类的存在理由）</h3>
 * <p>「把 yaml 的明文默认值去掉」<b>并不自动等于</b>「缺变量 ⇒ 启动失败」——
 * <b>实测</b>（{@link #realYamlWithoutEnvVarFailsTheContext} ＋
 * {@link #prodLikeStartupWithoutEnvVarFails}）发现：
 * <ul>
 *   <li><b>变量没设</b> ⇒ Spring 的 {@code @ConfigurationProperties} 绑定器
 *       <b>不报错</b>，而是把<b>占位符原文</b> {@code "${USER_JWT_SECRET}"}
 *       <b>当成值绑上去</b> ⇒ ⇒ 应用<b>照常启动</b>
 *       ⇒ ⇒ 而这个「密钥」是<b>仓里公开可知的一串字符</b> ⇒ ⇒ 🔴 <b>比空密钥更危险</b>
 *       （HMAC 密钥公开 ⇒ 谁都能伪造 token）；</li>
 *   <li><b>变量是空串</b> ⇒ Spring 把<b>空串当合法值</b>
 *       ⇒ ⇒ 属性绑成 {@code ""} ⇒ ⇒ 应用带着<b>空签名密钥</b>正常起来
 *       ⇒ ⇒ 🔴 <b>token 可伪造</b>（HMAC 空密钥谁都能算）。</li>
 * </ul>
 * ⇒ ⇒ 所以三条都得由 {@code CommentApiProperties#verifyAccessSecret} 显式堵
 * ⇒ ⇒ 本类钉住它在<b>真实装配</b>里真的挡住了。
 *
 * <h3>为什么不能只断言「方法会抛」</h3>
 * <p>AGENTS.md §3.3.3：<b>注解存在 ≠ 机制生效</b> ⇒ ⇒ {@code @PostConstruct} 写对了也可能没人调
 * ⇒ ⇒ 下面全部用<b>真 ApplicationContextRunner</b> / <b>真 SpringApplication</b>
 * 证明「上下文起不来」（运行证据，不是源码断言）。
 */
class CommentApiPropertiesSecretGuardTest {

    /** 正常密钥（形状 = 36 字节 UUID；⚠️ **不是**真源的生产密钥）。 */
    private static final String USER_SECRET = "ae0536f9-6450-4606-8e13-5a19ed505da0";

    /** 空 secret ⇒ 装配期就炸 ⇒ 上下文起不来。 */
    @Test
    @DisplayName("🔴 空签名密钥 ⇒ 启动失败（真 ApplicationContext：上下文起不来 ⇒ fail-closed 生效）")
    void blankSecretFailsTheContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropsConfig.class)
                .withPropertyValues("comment-api.user-auth.access-secret=")
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
                .withPropertyValues("comment-api.user-auth.access-secret=" + USER_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CommentApiProperties.class).getUserAuth().getAccessSecret())
                            .isEqualTo(USER_SECRET);
                });
    }

    /**
     * 🔴 判据本身的边界（纯单元，不依赖容器）。
     *
     * <p>⚠️ <b>「未解析的占位符字面量」这一条是<b>实测</b>逼出来的</b>：
     * 变量没设时绑定器会把<b>占位符原文</b>绑上去 ⇒ ⇒ 只判「空 / 空白」会漏掉它。
     */
    @Test
    @DisplayName("🔴 判据边界：null / 空串 / 全空白 / 未解析占位符字面量 ⇒ 抛；正常值 ⇒ 不抛")
    void guardRejectsBlankAndAcceptsRealSecret() {
        CommentApiProperties props = new CommentApiProperties();

        props.getUserAuth().setAccessSecret(null);
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getUserAuth().setAccessSecret("");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getUserAuth().setAccessSecret("   ");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        // ⚠️ 这一条是**实测**逼出来的：变量没设时绑定器会把**占位符原文**绑上去
        props.getUserAuth().setAccessSecret("${USER_JWT_SECRET}");
        assertThrows(IllegalStateException.class, props::verifyAccessSecret);

        props.getUserAuth().setAccessSecret(USER_SECRET);
        assertDoesNotThrow(props::verifyAccessSecret);
    }

    /**
     * 🔴🔴 <b>读<b>真 yaml</b>的回归网</b>。
     *
     * <p>上面几条用例都用 {@code withPropertyValues("comment-api.user-auth.access-secret=…")}
     * —— 也就是<b>直接喂键路径</b>，<b>从不读</b> {@code src/main/resources/application.yml}
     * ⇒ ⇒ <b>yaml 里的键路径写错，它们照样全绿</b>
     * ⇒ ⇒ 真缺陷已在同仓 article-api 上真踩到（commit {@code 45e0af4} 把 {@code auth:} 整行删掉
     * ⇒ ⇒ article-api 起不来，而那几条用例全绿）⇒ ⇒ 本用例把<b>真</b> yaml 塞进上下文
     * ⇒ ⇒ 再断言嵌套字段真的拿到了值。
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
                .withPropertyValues("USER_JWT_SECRET=" + USER_SECRET)
                .run(context -> {
                    // ⚠️ 绑定落空时，`verifyAccessSecret()` 会在 @PostConstruct 抛 ⇒ 上下文起不来
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CommentApiProperties.class).getUserAuth().getAccessSecret())
                            .isEqualTo(USER_SECRET);
                });
    }

    /**
     * 🔴🔴 <b>真 yaml ＋ 变量没设 ⇒ 启动失败</b>。
     *
     * <p>⚠️ 本条<b>第一次跑时是红的</b>：上下文<b>照常起来了</b> ⇒ 因为 Spring 的绑定器
     * 把未解析的占位符<b>原文</b>绑成了值（{@code "${USER_JWT_SECRET}"}）
     * ⇒ ⇒ 所以「只去默认值」在「变量没设」这条路上<b>原本是不 fail-closed 的</b>
     * ⇒ ⇒ {@code verifyAccessSecret()} 增补了「未解析占位符字面量」这一判据 ⇒ ⇒ 本条现在才绿。
     *
     * <p>⚠️ 这一条<b>不能</b>用 {@code withPropertyValues} 代替 ⇒ 因为
     * {@code ApplicationContextRunner} <b>默认不加载</b> {@code application.yml}
     * （它不走 ConfigData 流程）⇒ ⇒ 必须把真 yaml 显式塞进属性源。
     */
    @Test
    @DisplayName("🔴🔴 真 yaml + 变量未设 ⇒ 启动失败（绑到未解析占位符 ⇒ 被 @PostConstruct 拦下）")
    void realYamlWithoutEnvVarFailsTheContext() {
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
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 🔴🔴 <b>真 yaml ＋ 变量设成空串 ⇒ 启动失败</b>。
     *
     * <p>Spring 的 {@code PropertyPlaceholderHelper} 对「属性存在但值为空串」解析成<b>空串</b>
     * （<b>不走默认值、也不报错</b>）⇒ ⇒ 只去掉 yaml 默认值<b>堵不住</b>这一条
     * ⇒ ⇒ 必须由 {@code @PostConstruct} 显式拦 ⇒ ⇒ 本条就是那条路的<b>真 yaml 证据</b>。
     */
    @Test
    @DisplayName("🔴🔴 真 yaml + 变量为空串 ⇒ 启动失败（空串当合法值 ⇒ 由 @PostConstruct 拦下）")
    void realYamlWithBlankEnvVarFailsTheContext() {
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
                .withPropertyValues("USER_JWT_SECRET=")
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 🔴🔴🔴 <b>生产同款启动路径</b>：真 {@code SpringApplication} + 真 ConfigData 流程
     * ＋ <b>只有</b> {@code application.yml}（= 产物 jar 的真实构成）⇒ <b>变量没设 ⇒ 起不来</b>。
     *
     * <h3>为什么这条比上面几条更硬</h3>
     * <p>上面几条用 {@link ApplicationContextRunner} ⇒ 它<b>不走 ConfigData 流程</b>
     * ⇒ 「真 yaml 怎么被加载、placeholder 怎么被解析」都<b>不是生产那条路</b>。
     * ⇒ ⇒ 本条用 {@link SpringApplicationBuilder} ⇒ <b>完整</b>走一遍 Boot 的配置加载
     * ⇒ ⇒ 并用 {@code spring.config.location=classpath:/application.yml}
     * <b>只</b>加载主 yaml（⇒ 等价于「产物 jar 里只有 application.yml、没有 application.properties」）。
     *
     * <p>⚠️ <b>本条是本类最重要的一条</b>：它是「缺变量 ⇒ 启动失败」这个命题的
     * <b>生产等价</b>证据（AGENTS.md §3.3.3：「纯 mock 单测不算」）。
     */
    @Test
    @DisplayName("🔴🔴🔴 生产同款启动（真 SpringApplication + 真 ConfigData + 只有 application.yml）+ 变量未设 ⇒ 启动失败")
    void prodLikeStartupWithoutEnvVarFails() {
        Throwable failure = null;
        try (ConfigurableApplicationContext ctx = prodLikeStart()) {
            // 走到这里就说明「变量没设也起来了」⇒ 合成一个失败（下面的断言会把它判红）
            CommentApiProperties props = ctx.getBean(CommentApiProperties.class);
            failure = new IllegalStateException(
                    "启动成功了（实际绑到的值 = " + props.getUserAuth().getAccessSecret() + "）");
        } catch (Throwable t) {
            failure = t;
        }
        assertThat(failure).as("变量未设 ⇒ 必须启动失败").isNotNull();
        assertThat(rootCause(failure))
                .as("变量未设 ⇒ 必须是守卫抛的 IllegalStateException（而不是别的异常）")
                .isInstanceOf(IllegalStateException.class);
        assertThat(rootCause(failure).getMessage())
                .contains("comment-api.user-auth.access-secret")
                .contains("USER_JWT_SECRET");
    }

    /**
     * 🔴🔴🔴 生产同款启动 + <b>变量设成空串</b> ⇒ 启动失败。
     *
     * <p>⚠️ 空串在 Spring 里是<b>合法值</b>（{@code PropertyPlaceholderHelper} 不走默认值、也不报错）
     * ⇒ ⇒ yaml 那层<b>挡不住</b> ⇒ ⇒ 必须由 {@code verifyAccessSecret()} 挡。
     */
    @Test
    @DisplayName("🔴🔴🔴 生产同款启动 + 变量为空串 ⇒ 启动失败（空串是合法值 ⇒ 由守卫拦下）")
    void prodLikeStartupWithBlankEnvVarFails() {
        Throwable failure = null;
        try (ConfigurableApplicationContext ctx = prodLikeStart("USER_JWT_SECRET=")) {
            CommentApiProperties props = ctx.getBean(CommentApiProperties.class);
            failure = new IllegalStateException(
                    "启动成功了（实际绑到的值 = [" + props.getUserAuth().getAccessSecret() + "]）");
        } catch (Throwable t) {
            failure = t;
        }
        assertThat(failure).as("变量为空串 ⇒ 必须启动失败").isNotNull();
        assertThat(rootCause(failure))
                .as("变量为空串 ⇒ 必须是守卫抛的 IllegalStateException（而不是别的异常）")
                .isInstanceOf(IllegalStateException.class);
        assertThat(rootCause(failure).getMessage())
                .contains("comment-api.user-auth.access-secret")
                .contains("USER_JWT_SECRET");
    }

    /**
     * 🔴🔴🔴 生产同款启动 + <b>绑到的值是占位符原文</b> ⇒ 启动失败。
     *
     * <p>本条模拟的是「绑定器把<b>未解析的占位符原文</b>绑成值」那个状态
     * ⇒ ⇒ 它是「变量没设」那条路的<b>等价物</b>（绑上去的值长得一模一样）
     * ⇒ ⇒ ⇒ 守卫必须把它也判成不可用。
     *
     * <p>⚠️ <b>为什么这里注入的是「属性路径」而不是「环境变量」</b>：
     * 若把环境变量本身设成 {@code ${USER_JWT_SECRET}}（<b>自引用</b>），
     * Spring 会先抛 {@code PlaceholderResolutionException: Circular placeholder reference}
     * ⇒ ⇒ 那是 <b>Spring 自己</b>挡的，<b>证明不了本守卫有牙齿</b>
     * ⇒ ⇒ 本条注入的是「属性路径 = 占位符原文、环境变量未设」这个
     * <b>与真实失败场景逐字节相同</b>的绑定结果。
     */
    @Test
    @DisplayName("🔴🔴🔴 生产同款启动 + 绑到的值是占位符原文 ⇒ 启动失败（公开可知的「密钥」⇒ 由守卫拦下）")
    void prodLikeStartupWithLiteralPlaceholderBoundValueFails() {
        Throwable failure = null;
        try (ConfigurableApplicationContext ctx =
                prodLikeStart("comment-api.user-auth.access-secret=${USER_JWT_SECRET}")) {
            CommentApiProperties props = ctx.getBean(CommentApiProperties.class);
            failure = new IllegalStateException(
                    "启动成功了（实际绑到的值 = " + props.getUserAuth().getAccessSecret() + "）");
        } catch (Throwable t) {
            failure = t;
        }
        assertThat(failure).as("绑到的值是占位符原文 ⇒ 必须启动失败").isNotNull();
        assertThat(rootCause(failure))
                .as("绑到的值是占位符原文 ⇒ 必须是守卫抛的 IllegalStateException（而不是别的异常）")
                .isInstanceOf(IllegalStateException.class);
        assertThat(rootCause(failure).getMessage())
                .contains("comment-api.user-auth.access-secret")
                .contains("USER_JWT_SECRET");
    }

    /**
     * ✅ 生产同款启动 + <b>变量正常</b> ⇒ 正常启动（对照组：证明守卫没有误伤）。
     *
     * <p>⚠️ 这一条与上面三条是<b>同一段启动代码</b>、只差一个属性 ⇒ ⇒
     * 它是「守卫有牙齿」的<b>反向</b>证明：不误伤。
     */
    @Test
    @DisplayName("✅ 生产同款启动 + 变量正常 ⇒ 正常启动（对照组：证明守卫不误伤）")
    void prodLikeStartupWithRealEnvVarStarts() {
        try (ConfigurableApplicationContext ctx = prodLikeStart("USER_JWT_SECRET=" + USER_SECRET)) {
            assertThat(ctx.getBean(CommentApiProperties.class).getUserAuth().getAccessSecret())
                    .as("变量正常 ⇒ 密钥必须原样绑定（守卫不改动任何值 ⇒ 验签行为零变化）")
                    .isEqualTo(USER_SECRET);
        }
    }

    /**
     * 跑一次「生产同款」启动：真 {@code SpringApplication} + 真 ConfigData 流程，
     * <b>只</b>加载主 {@code application.yml}（= 产物 jar 的真实构成）。
     *
     * @param extraProperties 额外注入的属性（可为空）
     * @return 启动成功的上下文（调用方负责关闭）
     */
    private static ConfigurableApplicationContext prodLikeStart(String... extraProperties) {
        String[] props = new String[extraProperties.length + 2];
        props[0] = "spring.config.location=classpath:/application.yml";
        props[1] = "spring.main.banner-mode=off";
        System.arraycopy(extraProperties, 0, props, 2, extraProperties.length);
        return new SpringApplicationBuilder(PropsConfig.class)
                .web(WebApplicationType.NONE)
                .properties(props)
                .run();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    /** 让 {@link CommentApiProperties} 进入真实 Spring 生命周期（{@code @PostConstruct} 才会被调）。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CommentApiProperties.class)
    static class PropsConfig {
    }
}
