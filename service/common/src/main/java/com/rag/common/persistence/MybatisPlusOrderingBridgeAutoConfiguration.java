package com.rag.common.persistence;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 🔴 <b>共享层</b>的 MyBatis-Plus 自动配置<b>排序桥</b>（Spring Boot 4 专用的 workaround）。
 *
 * <h2>为什么需要它</h2>
 * <p>MyBatis-Plus 的 {@code MybatisPlusAutoConfiguration} 上写着
 * {@code @AutoConfigureAfter(org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration)}。
 * 那是 <b>Spring Boot 3</b> 的包名。Spring Boot 4.0 把该类挪到了
 * {@code org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration}
 * ⇒ ⇒ MP 声明的排序引用<b>静默悬空</b>（Boot 找不到这个类名 ⇒ <b>不报错</b>）
 * ⇒ ⇒ 随后按 imports 的字母序求值：{@code com.baomidou.*} 在 {@code org.springframework.*} 之前
 * ⇒ ⇒ MP <b>先跑</b> ⇒ {@code @ConditionalOnSingleCandidate(DataSource)} 此时找不到任何 DataSource bean
 * ⇒ ⇒ <b>整个自动配置不生效</b>（不产出 {@code SqlSessionFactory} / {@code SqlSessionTemplate}）
 * ⇒ ⇒ mapper 报 {@code Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required}。
 *
 * <p><b>运行期铁证</b>（2026-10-27，实测 {@code mvn test} 的启动日志）：
 * <pre>
 *   WARN o.s.core.annotation.MergedAnnotation : Failed to introspect meta-annotation
 *        @AutoConfigureAfter on class com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration:
 *        java.lang.TypeNotPresentException:
 *        Type org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration not present
 * </pre>
 *
 * <h2>🔴🔴 为什么这里用<b>字符串</b>形式（{@code afterName} / {@code beforeName}）而不是类字面量</h2>
 * <p>本类在<b>共享层的主 jar</b>里 ⇒ ⇒ 它会出现在<b>所有 16 个消费者</b>
 * （10 个 api + 10 个 rpc，其中 9 个 rpc 另有 6 个 rpc/11 个 api 的重叠）的 classpath 上
 * ⇒ ⇒ 其中<b>没有 MyBatis-Plus 的那 11 个</b>（10 个 api ＋ {@code hot-rpc}）
 * <b>根本解析不了</b> {@code MybatisPlusAutoConfiguration.class} 这个类字面量。
 * <p>⇒ 若写成类字面量形式：
 * <pre>
 *   &#64;AutoConfiguration(after = DataSourceAutoConfiguration.class,
 *                        before = MybatisPlusAutoConfiguration.class)   // ← 类字面量
 * </pre>
 * ⇒ 一旦有消费者在<b>反射</b>层面读这个注解（而不是用 ASM 读元数据），
 * 就会抛 {@code TypeNotPresentException} ⇒ ⇒ <b>那些消费者的上下文直接起不来</b>。
 * <p>⇒ 而字符串形式（Boot 4 的 {@code @AutoConfiguration} 用
 * {@code @AliasFor(annotation = AutoConfigureBefore.class, attribute = "name")} 把它落到
 * {@code AutoConfigureBefore.name}）：
 * <pre>
 *   &#64;AutoConfiguration(
 *           afterName  = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
 *           beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
 * </pre>
 * ⇒ ⇒ <b>类名只是字符串</b> ⇒ ⇒ 目标类在不在 classpath 上都<b>不影响</b>本类的加载
 * ⇒ ⇒ <b>对「没有 MyBatis-Plus」的消费者结构性安全</b>
 * ⇒ ⇒ 且排序机制照样成立（{@code AutoConfigurationSorter} 读的就是这两个字符串）。
 *
 * <h2>🔴 与 9 个 rpc 组件各自的域侧桥的关系</h2>
 * <p>那 9 个 rpc 组件<b>各自</b>有一份同名的域侧桥（例：
 * {@code service/message/message-rpc/src/main/java/com/rag/message/message/rpc/config/MybatisPlusOrderingBridgeAutoConfiguration.java}）。
 * <p>⇒ 两份桥声明的是<b>同向</b>的排序边（都是「DataSource 之后、MybatisPlus 之前」）⇒
 * ⇒ 排序图里<b>多一个同向节点、无环、功能等价</b> ⇒ ⇒ 共存期间<b>不改变任何行为</b>。
 * <p>⇒ ⇒ 域侧那 9 份的删除属于<b>各域的写权</b> ⇒ 共享层<b>只提供这份标准件</b>，
 * <b>不代删</b> ⇒ ⇒ 删完之后，全仓只剩<b>这一份</b>排序桥。
 *
 * <h2>🔴🔴 域侧删副本时：回归【必须带 {@code mvn clean}】</h2>
 * <p>（2026-10-28 · G1 实测带出 ⇒ 本层写进共享层 javadoc，因为<b>踩坑点在共享层这份被依赖的方式上</b>）
 * <p>域侧删掉那份域内副本时，<b>必须同批删掉该模块 {@code AutoConfiguration.imports} 里注册它的那一行</b>
 * ⇒ ⇒ 只删 java 文件、不删那一行 ⇒ Boot 启动期找不到类 ⇒ 上下文起不来。
 * <p>🔴 <b>但即使两处都删对了，回归也可能是红的</b> ⇒ <b>原因不在代码，在 stale 产物</b>：
 * <ul>
 *   <li>{@code maven-resources-plugin} <b>不会删除已删源文件的副本</b> ⇒ ⇒ {@code target/classes} 里
 *       <b>旧的 {@code .imports} 与旧的 {@code .class} 仍在</b>；</li>
 *   <li>⇒ ⇒ Boot 照旧注册那个「<b>源码已不存在</b>」的类 ⇒ ⇒ 报
 *       {@code IllegalStateException: Unable to read meta-data for class …}
 *       （栈顶 {@code AutoConfigurationSorter$AutoConfigurationClass.getAnnotationMetadata}）；</li>
 *   <li>⇒ ⇒ <b>看起来像「收编失败」，其实是 stale 产物</b>。</li>
 * </ul>
 * ⇒ ⇒ <b>处置 = 回归带 {@code mvn clean}</b>（或至少清掉该模块的 {@code target/classes}）
 * ⇒ ⇒ <b>判据</b>：红在 {@code Unable to read meta-data for class …} ⇒ <b>先查 stale 产物，别急着判收编失败</b>。
 *
 * <h2>🔴 本类【没有】{@code @ConditionalOnMissingBean} ⇒ 「遮蔽」不成立</h2>
 * <p>（2026-10-28 · G1 实测订正本层台账的旧表述）
 * <p>域内副本与本类<b>并存</b>时：<b>两份都会被注册</b>（两份都刻意类体为空、<b>不注册任何 bean</b>）
 * ⇒ ⇒ <b>零行为影响，但不是「遮蔽」</b> ⇒ ⇒ 真正的差别只是「<b>排序图上多一个同向节点</b>」
 * （域内用类字面量、共享层用字符串，但指向<b>同一对 FQN</b> ⇒ 同向等价、无环）⇒ ⇒ 删除仍<b>零行为差</b>。
 * <p>⚠️ <b>别与 Redis 那份混谈</b>：{@code GoStyleRedisClientAutoConfiguration} <b>确实有</b>
 * {@code @ConditionalOnMissingBean} ⇒ ⇒ <b>那里「遮蔽」是准确表述</b> ⇒ ⇒
 * <b>「遮蔽」一词在两处含义不同 ⇒ 不能互相套用</b>。
 *
 * <h2>本类的「行为面」是什么</h2>
 * <p>本类<b>不注册任何 bean</b>，也<b>不改变任何运行时行为</b>
 * ⇒ ⇒ 它只是让 {@code MybatisPlusAutoConfiguration} 能够被求值
 * ⇒ ⇒ 从而让 {@code MybatisPlusInterceptorChainGateTest} 能真正验证
 * 「MyBatis-Plus 会不会收集容器里的 {@code Interceptor[]} Bean」这条机制。
 *
 * @see MybatisPlusInterceptorChainGateTest 三层加码断言（收集 / 装载 / 行为）
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        beforeName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
public class MybatisPlusOrderingBridgeAutoConfiguration {
    // 刻意为空：只为建立自动配置排序边，不注册任何 bean。
}
