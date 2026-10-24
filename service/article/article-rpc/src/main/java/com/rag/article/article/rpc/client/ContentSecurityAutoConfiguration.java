package com.rag.article.article.rpc.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 内容安全端口的兜底注册 —— <b>只做一件事</b>：当容器里还没有任何
 * {@link ContentSecurityPort} 实现时，装上 {@link UnavailableContentSecurityClient}。
 *
 * <h2>为什么必须是自动配置而不是 {@code @Component}</h2>
 * {@code @ConditionalOnMissingBean} 的判断语义是「<b>处理本配置类之后</b>
 * 容器里是否已有该类型的 Bean」。这个判断只有在自动配置类上才是确定的 ——
 * 自动配置在用户 {@code @Component} <b>之后</b>求值。
 * 若把条件写在被组件扫描的普通类上，扫描顺序不确定，可能误判"已有 Bean"
 * 而让兜底实现<b>不被注册</b> ⇒ 审核消费者拿到 {@code null} 端口 ⇒ NPE。
 *
 * <p>这与 AGENTS.md §3.3「不得凭注解名称断定机制已启用」同源：
 * <b>注解在 ≠ 按预期生效</b>。
 * 锁死用例：{@code ArticleContentSecurityWiringTest}
 * （断言容器里<b>恰好有一个</b> {@code ContentSecurityPort}，
 * 且拿到的是兜底实现时调用必然抛出 —— 两件事一起断言，
 * 避免"Bean 数对了但类型错了"这种半绿）。
 *
 * <h2>移除条件</h2>
 * {@code security-proto} 落地并提供 {@code @Component} 实现后，本配置整体失效。
 */
@AutoConfiguration
public class ContentSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContentSecurityPort.class)
    ContentSecurityPort unavailableContentSecurityClient() {
        return new UnavailableContentSecurityClient();
    }
}