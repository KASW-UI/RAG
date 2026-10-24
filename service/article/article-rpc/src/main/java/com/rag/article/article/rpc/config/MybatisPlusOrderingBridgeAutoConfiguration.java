package com.rag.article.article.rpc.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * MyBatis-Plus 自动配置排序桥接（Spring Boot 4 专用 workaround）—— <b>与 message-rpc / admin-rpc 同款</b>。
 *
 * <p><b>问题</b>：MyBatis-Plus 的 {@code MybatisPlusAutoConfiguration} 上写着
 * {@code @AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)}，
 * 那是 <b>Spring Boot 3</b> 的包名。Spring Boot 4.0 把它挪到了
 * {@code org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration}，
 * 于是 MP 声明的排序引用<b>静默悬空</b>：Spring Boot 找不到这个类，
 * 随后按 imports 顺序求值（{@code com.baomidou.*} 字母序在 {@code org.springframework.*} 之前），
 * MP 先跑 ⇒ {@code @ConditionalOnSingleCandidate(DataSource)} 找不到任何 DataSource bean ⇒
 * 整个自动配置不生效 ⇒ 不产出 SqlSessionFactory ⇒ mapper 报
 * {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。
 *
 * <p><b>用法</b>：类体为空是刻意的 —— 它只为建立排序边
 * {@code DataSourceAutoConfiguration → 本类 → MybatisPlusAutoConfiguration}。
 * 必须同时在 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 里登记，否则不会被当作自动配置加载。
 *
 * <p><b>什么时候可以删</b>：升级到已用 Boot 4 新包名声明 {@code @AutoConfiguration(after = ...)}
 * 的 MyBatis-Plus 版本之后。
 *
 * <p>锁死用例：{@code ArticleSpringContextWiringTest}（断言 6 个 Mapper Bean 全部存在
 * —— 它们存在与否正是这条排序边是否生效的直接证据）。
 */
@AutoConfiguration(
        after = DataSourceAutoConfiguration.class,
        before = MybatisPlusAutoConfiguration.class)
public class MybatisPlusOrderingBridgeAutoConfiguration {
    // 刻意为空：只为建立自动配置排序边，不注册任何 bean。
}