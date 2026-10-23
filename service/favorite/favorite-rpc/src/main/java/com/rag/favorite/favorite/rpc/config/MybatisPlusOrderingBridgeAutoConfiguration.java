package com.rag.favorite.favorite.rpc.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * MyBatis-Plus 自动配置排序桥接（Spring Boot 4 专用的 workaround）。
 *
 * <p><b>与 message-rpc / like-rpc / admin-rpc / user-rpc / article-rpc 同款</b>：
 * 见 {@code service/like/like-rpc/.../config/MybatisPlusOrderingBridgeAutoConfiguration.java}。
 * 本类是照抄 —— 六个模块用的是同一个 MyBatis-Plus 启动器、同一个 Spring Boot 4、
 * 同一个悬空引用；其余五个已修，<b>favorite-rpc 原先根本没写过后端代码</b>，所以第一次遇到。
 *
 * <h2>⚠️ 实测证据（2026-10-23，本模块首次落地的第一条装配证据）</h2>
 * <p>{@code FavoriteSchemaParityTest} 首次运行时，Spring 上下文在装配
 * {@code favoriteFactOutboxMapper} 时失败：
 * <pre>
 * Error creating bean with name 'favoriteFactOutboxMapper':
 *   Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required
 * Caused by: java.lang.IllegalArgumentException:
 *   Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required
 * </pre>
 * ⚠️ 这正是 AGENTS.md §3.3.3 的典型形态：<b>不报错说「MyBatis-Plus 没生效」</b>，
 * 只在某个 Mapper 被实例化时才抛，且报错信息完全指不到根因。
 *
 * <h2>问题</h2>
 * <p>MyBatis-Plus 的 {@code MybatisPlusAutoConfiguration} 上写着
 * {@code @AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)}。
 * 那是 <b>Spring Boot 3</b> 的包名。Spring Boot 4.0 把该类挪到了
 * {@code org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration}，
 * 于是 MP 声明的排序引用<b>静默悬空</b> —— Spring Boot 找不到这个类，
 * 随后按 imports 顺序求值：{@code com.baomidou.*} 字母序在 {@code org.springframework.*} 之前，
 * 所以 MP 先跑。
 *
 * <p><b>后果</b>：{@code @ConditionalOnSingleCandidate(DataSource)} 此时找不到任何 DataSource bean，
 * 整个自动配置<b>不生效</b>，不产出 SqlSessionFactory / SqlSessionTemplate。
 *
 * <h2>为什么用空类做桥</h2>
 * <p>自动配置排序是一张有向图，本类声明
 * {@code DataSourceAutoConfiguration → 本类 → MybatisPlusAutoConfiguration}，
 * 把 MP 强行顶到 DataSource 之后求值。类体为空是刻意的 —— 它只为排序而存在。
 *
 * <h2>注册</h2>
 * <p>必须同时在
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 里写一行 {@code com.rag.favorite.favorite.rpc.config.MybatisPlusOrderingBridgeAutoConfiguration}，
 * 否则本类不会被当作自动配置加载，排序边也就不会建立。
 *
 * <h2>什么时候可以删</h2>
 * <p>升级到已用 Boot 4 新包名声明 {@code @AutoConfiguration(after = ...)}
 * 的 MyBatis-Plus 版本之后。
 */
@AutoConfiguration(
        after = DataSourceAutoConfiguration.class,
        before = MybatisPlusAutoConfiguration.class)
public class MybatisPlusOrderingBridgeAutoConfiguration {
    // 刻意为空：只为建立自动配置排序边，不注册任何 bean。
}