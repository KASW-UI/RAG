package com.rag.common.protection;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <b>守门：client breaker 的 Bean 在两条装配路径下都<b>只有一个</b>（不会重复注册）。</b>
 *
 * <h2>为什么需要这条（它防的是「修单 2 时引入的新缺陷」）</h2>
 * <p>修复「rpc 侧拿不到 client breaker」有两种做法：
 * <ol>
 *   <li>让 {@link GoStyleGrpcClientBreakerInterceptor} <b>保留</b> {@code @Component}
 *       ＋ 在别处再加一个 {@code @Bean} ⇒ <b>api 侧会注册两个</b>
 *       ⇒ devh 的收集器会把拦截器装进 channel <b>两次</b>
 *       ⇒ 一次调用被计两次成功/失败 ⇒ <b>熔断统计翻倍、误开概率翻倍</b>；</li>
 *   <li><b>摘掉</b> {@code @Component}、只由 {@link GoStyleGrpcClientBreakerConfiguration} 产出
 *       ⇒ <b>单一来源</b> ⇒ 结构上不可能重复。⇒ 本仓取的是这一种。</li>
 * </ol>
 *
 * <h2>为什么不用 {@code @ConditionalOnMissingBean} 来防重复</h2>
 * <p>Spring 官方 javadoc 明确说：该条件<b>只建议用在自动配置类上</b>，
 * 因为它只能匹配「已被处理过」的 bean 定义，而组件扫描产生的 {@code @Component}
 * 在 {@code @Bean} 方法的条件被求值时通常<b>还没注册</b>
 * ⇒ 用它会得到「api 侧与 rpc 侧各一个」⇒ <b>恰好是它想防的那个缺陷</b>。
 * ⇒ 单一来源（摘 {@code @Component}）是结构性的，不依赖条件求值顺序。
 *
 * <h2>两条路径分别是什么</h2>
 * <ul>
 *   <li><b>api 侧</b>：{@code scanBasePackages} 含 {@code com.rag.common} ⇒ 扫到
 *       {@code GoStyleProtectionConfiguration} 与 {@code GoStyleGrpcClientBreakerConfiguration}
 *       ⇒ ⇒ 这里断言「扫到的两个配置类**不会**产出两个 Bean」。</li>
 *   <li><b>rpc 侧</b>：不扫、只 {@code @Import(GoStyleProtectionConfiguration.class)}
 *       ⇒ ⇒ 这里断言「{@code @Import} 链产出<b>恰好一个</b> Bean」。</li>
 * </ul>
 */
class GoStyleGrpcClientBreakerSingleRegistrationTest {

    /**
     * api 侧装配面：两个配置类都在扫描范围里。
     *
     * <p>⚠️ 关键点：{@link GoStyleProtectionConfiguration} 自己 {@code @Import} 了
     * {@link GoStyleGrpcClientBreakerConfiguration} ⇒ 两者<b>同时</b>出现在扫描范围时，
     * Spring 的配置类去重必须让它<b>只被处理一次</b> ⇒ 只产出一个 Bean。
     */
    @Test
    @DisplayName("api 侧（配置类被扫到 + 又被 @Import）⇒ client breaker 只有一个 Bean")
    void apiLikeScanYieldsExactlyOneBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        GoStyleProtectionConfiguration.class,
                        GoStyleGrpcClientBreakerConfiguration.class)
                .run(ctx -> assertThat(ctx)
                        .as("配置类去重必须生效：api 侧不能出现两个 client breaker，"
                                + "否则 devh 会把同一个拦截器装进 channel 两次 ⇒ 熔断统计翻倍")
                        .hasSingleBean(GoStyleGrpcClientBreakerInterceptor.class));
    }

    /**
     * rpc 侧装配面：只 {@code @Import} 链的顶端，不直接给被导入的那个配置类。
     */
    @Test
    @DisplayName("rpc 侧（只 @Import 链顶端）⇒ client breaker 恰好一个 Bean")
    void rpcLikeImportYieldsExactlyOneBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(GoStyleProtectionConfiguration.class)
                .run(ctx -> assertThat(ctx)
                        .as("纯 gRPC 进程必须能拿到 client breaker，且只拿一个")
                        .hasSingleBean(GoStyleGrpcClientBreakerInterceptor.class));
    }

    /**
     * 两侧<b>合起来</b>也不能重复：这是「api 侧扫到 ＋ 同时又被 {@code @Import}」的最坏情况。
     */
    @Test
    @DisplayName("api 侧扫到 + rpc 侧 @Import 同时发生时 ⇒ 仍然只有一个 Bean")
    void bothPathsTogetherStillYieldOneBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(GoStyleProtectionConfiguration.class)
                .withBean(GoStyleGrpcClientBreakerConfiguration.class)
                .run(ctx -> assertThat(ctx)
                        .as("两条装配路径叠加时仍必须只有一个 Bean")
                        .hasSingleBean(GoStyleGrpcClientBreakerInterceptor.class));
    }
}
