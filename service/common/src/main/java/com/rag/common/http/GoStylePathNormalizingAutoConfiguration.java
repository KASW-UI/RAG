package com.rag.common.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 🔴 <b>共享层</b>的路径归一 Filter 的<b>唯一注册点</b>。
 *
 * <h2>为什么用自动配置，而不是 {@code @Component}</h2>
 * <ul>
 *   <li><b>覆盖面</b>：10 个 api 模块的 {@code @ComponentScan} 配置各不相同
 *       （有的显式列 {@code com.rag.common}，有的用 excludeFilters 排除了某些包）
 *       ⇒ 靠组件扫描来「顺便带上」⇒ <b>漏一个域就静默少一层</b>（AGENTS.md §3.3）。<br>
 *       自动配置走的是 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 *       ⇒ 与组件扫描配置<b>完全解耦</b> ⇒ 只要 classpath 上有 {@code service/common} 就一定生效。</li>
 *   <li><b>单一来源</b>：本类是 Filter 的<b>唯一</b> Bean 定义点
 *       ⇒ 结构上不可能「同一层被注册两次」。</li>
 * </ul>
 *
 * <h2>🔴 {@code @ConditionalOnMissingBean(name = ...)} 的<b>唯一</b>用途：过渡期去重</h2>
 * <p>本归一器原先是 <b>article-api 的域内副本</b>
 * （{@code service/article/article-api/src/main/java/com/rag/article/article/api/config/GoStylePathNormalizingFilter.java}）
 * ⇒ ⇒ 共享层这份落地时，article-api 会同时存在<b>两份</b>。
 * <p>⚠️ 两份的<b>类不同</b>（包名不同）⇒ {@code @ConditionalOnMissingBean(GoStylePathNormalizingFilter.class)}
 * 这种<b>按类型</b>的判据<b>匹配不上</b> ⇒ ⇒ 这里必须<b>按 Bean 名</b>判：
 * <pre>
 *   article-api 的域内副本的默认 Bean 名 = goStylePathNormalizingFilter
 *   本自动配置的 @Bean 方法名           = goStylePathNormalizingFilter
 * </pre>
 * ⇒ ⇒ 「域内副本还在 ⇒ 共享层这份先不注册（不覆盖它）」
 * ⇒ ⇒ 「域内副本删了 ⇒ 共享层这份顶上」
 * <p>⚠️ <b>为什么按 Bean 名判是可靠的（而不是又一次「静默失效」）</b>：
 * <ul>
 *   <li>组件扫描（{@code @ComponentScan}）在 {@code ConfigurationClassParser} 的<b>解析阶段</b>就注册了 Bean 定义；</li>
 *   <li>自动配置是 <b>延迟</b>导入（{@code DeferredImportSelector}）⇒ 在解析阶段<b>之后</b>才求值；</li>
 *   <li>⇒ 求值时域内副本的 Bean 定义<b>已经存在</b> ⇒ 条件成立 ⇒ 共享层这份跳过。</li>
 * </ul>
 * ⇒ ⇒ 而且**万一判据失效**，后果是「两个同名 Bean」⇒ Spring 的
 * {@code allow-bean-definition-overriding} 默认关闭 ⇒ <b>启动直接失败</b>（硬失败，不是静默失效）。
 *
 * <h2>⚠️ 收口条件（这条过渡判据是<b>临时代码</b>）</h2>
 * <p>当 <b>article-api 的域内副本被删除</b>后（那是 article 域的写权，不在本层）
 * ⇒ 本条件恒为「无同名 Bean」⇒ 共享层这份恒注册
 * ⇒ ⇒ 且 <b>类名（simple name）与 Bean 名都与旧副本一致</b>
 * ⇒ ⇒ article-api 既有的「精确 9 项 Filter 链序」守门（{@code ArticleApiAssemblyWiringTest}）
 * <b>不需要任何改动</b>就继续成立。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GoStylePathNormalizingAutoConfiguration {

    /**
     * 注册共享层的路径归一 Filter。
     *
     * <p>⚠️ Bean 名必须叫 {@code goStylePathNormalizingFilter}
     * ⇒ 一是与 article-api 旧副本的默认 Bean 名对齐（过渡期判据靠它），
     * 二是旧副本删除后 article-api 的链序守门看到的 simple name 不变。
     */
    @Bean
    @ConditionalOnMissingBean(name = "goStylePathNormalizingFilter")
    public GoStylePathNormalizingFilter goStylePathNormalizingFilter() {
        return new GoStylePathNormalizingFilter();
    }
}
