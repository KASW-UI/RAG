package com.rag.common.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * <b>守门用的最小 Spring 上下文</b>：复刻「一个 rpc 组件的 MyBatis 装配面」。
 *
 * <h2>为什么刻意用 {@code @Import} 声明两个拦截器（而不是 {@code @Bean} 方法）</h2>
 * <p>因为<b>生产就是这样装配的</b>：10 个 rpc 组件的应用类里写的是
 * {@code @Import({DeadlineQueryTimeoutInjector.class, SqlTimeoutStatusTranslator.class})}
 * （例：{@code MessageRpcApplication} / {@code CommentRpcApplication}）。
 * ⇒ ⇒ 守门必须用<b>同一种装配方式</b>，否则证明的就不是生产那条路。
 *
 * <h2>为什么用 plain {@code @Configuration} 而不是 {@code @SpringBootApplication}</h2>
 * <p>{@code @SpringBootApplication} 隐含 {@code @SpringBootConfiguration}
 * ⇒ 若测试类路径上出现两个这样的类，Spring Boot 的测试上下文搜索会抛
 * {@code Found multiple @SpringBootConfiguration annotated classes}
 * ⇒ 这里用 plain {@code @Configuration} + {@code @EnableAutoConfiguration}
 * ⇒ <b>既不制造第二个 {@code @SpringBootConfiguration}</b>，
 * 又能让 MyBatis-Plus 的自动配置（{@code MybatisPlusAutoConfiguration}）真的跑起来。
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@MapperScan("com.rag.common.persistence.probe")
@Import({
        DeadlineQueryTimeoutInjector.class,
        SqlTimeoutStatusTranslator.class
})
public class MybatisPlusProbeApplication {
}
