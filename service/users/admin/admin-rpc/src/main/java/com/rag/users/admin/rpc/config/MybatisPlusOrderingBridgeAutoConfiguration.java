package com.rag.users.admin.rpc.config;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * MyBatis-Plus 自动配置排序桥接（Spring Boot 4 专用的 workaround）。
 *
 * <p><b>问题</b>：MyBatis-Plus 3.5.14 的 {@code MybatisPlusAutoConfiguration} 上写着
 * {@code @AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)}。
 * 那是 <b>Spring Boot 3</b> 的包名。Spring Boot 4.0 把该类挪到了
 * {@code org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration}，
 * 于是 MP 声明的排序引用<b>静默悬空</b>——Spring Boot 找不到这个类，只会在 debug 日志里留一句
 * "Could not find auto-configuration class"，然后按 imports 顺序求值。
 *
 * <p><b>后果</b>：{@code MybatisPlusAutoConfiguration} 先于 DataSource 自动配置求值，
 * 此时 {@code @ConditionalOnSingleCandidate(DataSource)} 找不到任何 DataSource bean，
 * 整个自动配置 <b>不生效</b>，不产出 SqlSessionFactory / SqlSessionTemplate，
 * 最终 mapper 报 {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。
 *
 * <p>实测证据（2026-10-20，{@code java -jar user-rpc-0.0.1-SNAPSHOT.jar --debug}）：
 * <pre>
 *   DataSourceAutoConfiguration matched:                       ← DataSource 自动配置本身是好的
 *   MybatisPlusAutoConfiguration:
 *      Did not match:
 *         - @ConditionalOnSingleCandidate (types: javax.sql.DataSource) did not find any beans
 * </pre>
 *
 * <p><b>为什么用空类做桥</b>：Spring Boot 的自动配置排序是一张有向图，
 * 每个自动配置上的 before/after 会连成边。本类声明
 * {@code DataSourceAutoConfiguration → 本类 → MybatisPlusAutoConfiguration}，
 * 就把 MP 强行顶到 DataSource 之后求值。类体为空是刻意的——
 * 它只为排序而存在，不承担任何 bean 创建职责。
 *
 * <p><b>什么时候可以删</b>：升级到已经用 Boot 4 新包名声明
 * {@code @AutoConfiguration(after = ...)} 的 MyBatis-Plus 版本之后。
 * 届时删掉本类与 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 里的注册行即可。
 */
@AutoConfiguration(
        after = DataSourceAutoConfiguration.class,
        before = MybatisPlusAutoConfiguration.class)
public class MybatisPlusOrderingBridgeAutoConfiguration {
    // 刻意为空：只为建立自动配置排序边，不注册任何 bean。
}