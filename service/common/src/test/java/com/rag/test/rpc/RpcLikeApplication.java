package com.rag.test.rpc;

import com.rag.common.protection.GoStyleProtectionConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;

/**
 * <b>模拟一个纯 gRPC 进程的装配面</b>（rpc 侧的组件扫描与 {@code @Import} 口径）。
 *
 * <h2>为什么这个类存在（而不是直接用某个 rpc 模块的 Application）</h2>
 * <ul>
 *   <li><b>不能依赖域侧模块</b>：本文件属于共享层（{@code service/common}）⇒
 *       它的测试<b>不允许</b>依赖某个域的 jar（否则共享层就绑死在某个域上）。</li>
 *   <li><b>必须与 rpc 侧同形</b>：rpc 进程<b>刻意不扫</b> {@code com.rag.common}
 *       （那一族里有 {@code jakarta.servlet.Filter}），改用 {@code @Import} 精确引入
 *       ⇒ 所以本类<b>既不扫描、只 {@code @Import}</b>
 *       ⇒ 与 {@code GoStyleProtectionConfiguration} 的类注释里写的纪律一致。</li>
 * </ul>
 *
 * <h2>为什么用 plain {@code @Configuration} 而不是 {@code @SpringBootApplication}</h2>
 * <p>{@code @SpringBootApplication} 隐含 {@code @SpringBootConfiguration} ⇒
 * 若测试类路径上出现<b>两个</b>这样的类，Spring Boot 的测试上下文搜索会抛
 * {@code Found multiple @SpringBootConfiguration annotated classes}
 * ⇒ 本类用 plain {@code @Configuration} + {@code @EnableAutoConfiguration}
 * ⇒ <b>既不制造第二个 {@code @SpringBootConfiguration}</b>，
 * 又能让 devh 的自动配置（{@link GrpcClientAutoConfiguration}）真的跑起来。
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(GrpcClientAutoConfiguration.class)
@Import(GoStyleProtectionConfiguration.class)
public class RpcLikeApplication {
}
