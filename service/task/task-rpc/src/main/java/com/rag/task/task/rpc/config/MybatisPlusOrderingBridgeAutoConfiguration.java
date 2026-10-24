package com.rag.task.task.rpc.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * MyBatis-Plus 自动配置排序桥接（Spring Boot 4 专用 workaround）。
 *
 * <p><b>与 message-rpc / admin-rpc / user-rpc / article-rpc / like-rpc 同款</b>，
 * 本类是照抄 —— 六个模块用的是同一个 MyBatis-Plus 启动器、同一个 Spring Boot 4、同一个悬空引用。
 *
 * <p><b>问题</b>：MyBatis-Plus 的 {@code MybatisPlusAutoConfiguration} 上写着
 * {@code @AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)}，
 * 那是 <b>Spring Boot 3</b> 的包名。Spring Boot 4.0 把该类挪到了
 * {@code org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration}，
 * 于是 MP 声明的排序引用<b>静默悬空</b> —— Spring Boot 找不到这个类，
 * 随后按 imports 顺序求值：{@code com.baomidou.*} 字母序在 {@code org.springframework.*} 之前，
 * 所以 MP 先跑。
 *
 * <p><b>后果</b>：{@code @ConditionalOnSingleCandidate(DataSource)} 此时找不到任何 DataSource bean，
 * 整个自动配置<b>不生效</b>，不产出 SqlSessionFactory / SqlSessionTemplate，
 * 最终 mapper 报 {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。
 *
 * <p><b>为什么用空类做桥</b>：自动配置排序是一张有向图，本类声明
 * {@code DataSourceAutoConfiguration → 本类 → MybatisPlusAutoConfiguration}，
 * 把 MP 强行顶到 DataSource 之后求值。类体为空是刻意的 —— 它只为排序而存在。
 *
 * <p><b>注册</b>：必须同时在
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 里写一行本类全名，否则不会被当作自动配置加载，排序边也就不会建立。
 *
 * <p><b>什么时候可以删</b>：升级到已用 Boot 4 新包名声明 {@code @AutoConfiguration(after = ...)}
 * 的 MyBatis-Plus 版本之后。
 */
@AutoConfiguration(
        after = DataSourceAutoConfiguration.class,
        before = MybatisPlusAutoConfiguration.class)
public class MybatisPlusOrderingBridgeAutoConfiguration {
    // 刻意为空：只为建立自动配置排序边，不注册任何 bean。
}