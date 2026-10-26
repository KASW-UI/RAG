package com.rag.common.persistence;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * <b>🔴 测试专用的 MyBatis-Plus 自动配置排序桥（Spring Boot 4 专用的 workaround）</b>。
 *
 * <h2>为什么需要它</h2>
 * <p>MyBatis-Plus 的 {@code MybatisPlusAutoConfiguration} 上写着
 * {@code @AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)}。
 * 那是 <b>Spring Boot 3</b> 的包名。Spring Boot 4.0 把该类挪到了
 * {@code org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration}
 * ⇒ ⇒ MP 声明的排序引用<b>静默悬空</b>（Boot 找不到这个类名 ⇒ 不报错）
 * ⇒ ⇒ 随后按 imports 的字母序求值：{@code com.baomidou.*} 在 {@code org.springframework.*} 之前
 * ⇒ ⇒ MP <b>先跑</b> ⇒ {@code @ConditionalOnSingleCandidate(DataSource)} 此时找不到任何 DataSource bean
 * ⇒ ⇒ <b>整个自动配置不生效</b>（不产出 {@code SqlSessionFactory} / {@code SqlSessionTemplate}）。
 *
 * <p><b>这个坑在生产侧已经踩过并修过</b>：9 个 rpc 组件各自带一份
 * {@code MybatisPlusOrderingBridgeAutoConfiguration}
 * （例：{@code service/message/message-rpc/src/main/java/com/rag/message/message/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java}）
 * ⇒ ⇒ 本类是<b>照抄那一份</b>（同样的 {@code @AutoConfiguration(after=…, before=…)} + 同样的空类体）。
 *
 * <h2>🔴 为什么必须落在 <b>test scope</b>，而不能放进 {@code src/main}</h2>
 * <p>排序桥一旦进了 {@code service/common} 的主 jar ⇒ 它会出现在
 * <b>所有 16 个消费者</b>（10 个 api + 10 个 rpc）的 classpath 上
 * ⇒ ⇒ 把 MP 自动配置从「<b>不生效</b>」变成「<b>生效</b>」
 * ⇒ ⇒ 那些<b>本来没有 DataSource 的 api 模块</b>会突然开始要求 DataSource
 * ⇒ ⇒ <b>那是行为变更，不是修漂移</b>（AGENTS.md §3.1：差异只允许出现在表达层）。
 * <p>本类在 {@code src/test/java} + 本模块的 <b>test scope</b> {@code AutoConfiguration.imports}
 * ⇒ ⇒ 既不进主 jar、也不传递给下游
 * ⇒ ⇒ <b>零行为影响</b>，只让本模块的守门能跑起一个真 MP 上下文。
 *
 * <h2>排序机制在 Boot 4 里确实读注解（实测，非推测）</h2>
 * <p>解包 {@code spring-boot-autoconfigure-4.0.8-sources.jar} 的
 * {@code org/springframework/boot/autoconfigure/AutoConfigurationSorter.java}：
 * 源码第 43~44 行注释写明
 * 「respect {@code @AutoConfigureBefore @AutoConfigureAfter} annotations (<b>without loading classes</b>)」
 * ⇒ ⇒ 它用 {@code MetadataReaderFactory} 直接读<b>类文件上的注解</b>
 * ⇒ ⇒ <b>不需要</b> {@code META-INF/spring-autoconfigure-metadata.properties}
 * ⇒ ⇒ 只要本类出现在某个 {@code AutoConfiguration.imports} 里，排序边就成立。
 *
 * <h2>为什么注册进的是 test scope 的 {@code AutoConfiguration.imports}</h2>
 * <p>自动配置排序是<b>在候选集合内部</b>做的 ⇒ 桥类必须出现在候选集合里才会被排序
 * ⇒ ⇒ 必须登记一行（与 9 个 rpc 组件的注册方式逐字一致）。
 * <p>Spring Boot 的 {@code ImportCandidates.load} 走 {@code classLoader.getResources(...)}
 * ⇒ ⇒ 会把 classpath 上<b>同名</b>的多个 {@code AutoConfiguration.imports} <b>合并</b>读入
 * ⇒ ⇒ test scope 那份（{@code target/test-classes}）与主 jar 那份（{@code target/classes}）互不覆盖。
 *
 * <h2>本类的「行为面」是什么</h2>
 * <p>本类<b>不注册任何 bean</b>，也<b>不改变任何运行时行为</b>
 * ⇒ ⇒ 它只是让 {@link MybatisPlusAutoConfiguration} 在<b>本模块的守门上下文里</b>
 * 能够被求值 ⇒ ⇒ 从而让 {@code MybatisPlusInterceptorChainGateTest} 能真正验证
 * 「MP 会不会收集容器里的 {@code Interceptor[]} Bean」这条机制。
 *
 * @see MybatisPlusInterceptorChainGateTest 三层加码断言（收集 / 装载 / 行为）
 */
@AutoConfiguration(
        after = DataSourceAutoConfiguration.class,
        before = MybatisPlusAutoConfiguration.class)
public class MybatisPlusOrderingBridgeAutoConfiguration {
    // 刻意为空：只为建立自动配置排序边，不注册任何 bean。
}
