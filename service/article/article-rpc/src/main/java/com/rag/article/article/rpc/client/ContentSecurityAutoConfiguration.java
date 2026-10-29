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
 *
 * <h2>🔴 2026-10-29 订正：本类的注释原先引用了一个<b>不存在的测试类</b></h2>
 * <p>原文写「锁死用例：{@code ArticleContentSecurityWiringTest}」——
 * 但<b>全仓没有这个类</b>（{@code find service -name "ArticleContentSecurityWiringTest.java"} ⇒ 0 命中）。
 * 这正是 AGENTS.md §3.3.2 点名的形态：<b>以断言形式写下的、不存在的证据</b>。
 * ⇒ 真实的锁死用例是
 * {@code ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud}
 * （断言容器里<b>恰好有一个</b> {@code ContentSecurityPort}，
 * 且拿到的是兜底实现时调用必然抛出 —— 两件事一起断言，
 * 避免"Bean 数对了但类型错了"这种半绿）。
 *
 * <h2>🔴 2026-10-29 订正：<b>「移除条件」不成立</b>（S-5 改判为「范围外」）</h2>
 * <p>原文写「{@code security-proto} 落地并提供实现后，本配置整体失效」——
 * 那个措辞暗示「security 域迟早会迁进来」。<b>但</b>
 * {@code AGENTS.md:43} 写明「Go 侧 {@code knowledge}/{@code points}/{@code security}
 * <b>范围外 ⇒ 不是待办</b>」⇒ ⇒ <b>security 域按用户决策不会落进本仓</b>
 * ⇒ ⇒ 本兜底实现<b>不是临时占位，而是本仓的<b>最终设计</b></b>。
 * <br>⇒ 若将来 security 域真的落地（<b>超出本项目范围</b>），
 * 正确顺序仍是「<b>先改端口再接</b>」（{@code ContentSecurityPort} 已带 {@code success} 字段），
 * 且<b>必须先删/改</b> {@code ArticleSpringContextWiringTest#contentSecurityPortIsExactlyOneAndFailsLoud}
 * 里「端口<b>就是</b>兜底实现」那条断言（它锁的是本项目的最终设计，不是未完成态）。
 */
@AutoConfiguration
public class ContentSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContentSecurityPort.class)
    ContentSecurityPort unavailableContentSecurityClient() {
        return new UnavailableContentSecurityClient();
    }
}