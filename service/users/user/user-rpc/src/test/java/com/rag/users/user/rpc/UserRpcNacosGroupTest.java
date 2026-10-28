package com.rag.users.user.rpc;

import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.rag.users.user.rpc.bootstrap.NacosRegistration;
import net.devh.boot.grpc.server.config.GrpcServerProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 🔴 DRIFT-G1-001 守门 —— user-rpc 注册到 Nacos 的 group 必须**来自配置**，
 * 不能是写死的常量。
 *
 * <h2>Go 出处</h2>
 * <pre>
 *   service/user/user/rpc/etc/user.yaml:47
 *       Etcd: { Key: user.rpc }     ← 服务发现键是**配置项**，部署时改 yaml 就换命名空间
 * </pre>
 *
 * <h2>修前</h2>
 * <p>{@code NacosRegistration} 把 group <b>写死</b>成 {@code "USER_GROUP"}
 * ⇒ {@code NACOS_GROUP} 这个环境变量对 user-rpc <b>完全无效</b>；
 * 而 user-api 的 {@code application.yml} 写的是 {@code group: ${NACOS_GROUP:USER_GROUP}}（<b>读配置</b>）
 * ⇒ ⇒ 部署一旦设了不同的 {@code NACOS_GROUP} ⇒ <b>rpc 注册到 A 组、api 去 B 组找</b>
 * ⇒ ⇒ <b>恒 "No servers found" ⇒ 所有业务码塌成 1015</b>。
 * <p>⇒ 同族教训（{@code DRIFT-FOLLOW-004} 修的是 {@code NACOS_ADDR/PORT} 那一维）：
 * <b>「配置项存在」≠「配置项被消费」</b>。
 *
 * <h2>本测试守什么</h2>
 * <ol>
 *   <li><b>配置被真正消费</b>：{@code spring.cloud.nacos.discovery.group=G1_GROUP} ⇒
 *       注册调用里出现的 group <b>必须</b>是 {@code G1_GROUP}
 *       ⇒ 这一条用**真实的 {@link NacosRegistration}** ＋ <b>真实的 Spring {@code @Value} 解析</b>
 *       ⇒ 断言的是「{@code NamingService.registerInstance} 的第 2 个实参」；
 *       ⚠️ 只 mock 最外层的 {@code NamingService}（Nacos 客户端的真实边界），
 *       <b>不用 mock 被测类本身</b> ⇒ 否则就变成「自己 new 一个、自己传值、自己断言」的假绿。</li>
 *   <li><b>默认行为零变化</b>：不配该属性时解析结果仍是 {@code USER_GROUP}
 *       （= 修前的写死值）⇒ 本次修复<b>不改变</b>默认部署的行为。</li>
 *   <li><b>yml 里那一行还在</b>：删掉 {@code application.yaml} 里的
 *       {@code group: ${NACOS_GROUP:USER_GROUP}} ⇒ 属性消失 ⇒ 又退回「写死」形态
 *       ⇒ 本条守住这个「配置项被删 ⇒ 环境变量失效」的回归。</li>
 * </ol>
 *
 * <h2>⚠️ 本测试的证据边界</h2>
 * <p>它证明的是「<b>配置 → 代码 → Nacos 客户端调用</b>」这一段（装配层）。
 * <b>真 Nacos 是否真的按这个 group 收下实例</b>，由 G1 lane 的<b>真启动 A/B 实验</b>覆盖
 * （真 Nacos + 真进程 + 真 HTTP 请求）⇒ 两者合起来才是完整证据链。
 */
class UserRpcNacosGroupTest {

    private static final String SERVICE_NAME = "user-rpc";

    /** 修前写死的 group —— 默认行为必须与它逐字相同。 */
    private static final String LEGACY_HARDCODED_GROUP = "USER_GROUP";

    // ────────────────────────────────────────────────────────────────────
    // 1. 配置被真正消费
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 DRIFT-G1-001：注册用的 group = 配置的 group（不再是写死的常量）")
    void registrationUsesConfiguredGroup() {
        NamingService namingService = mock(NamingService.class);
        NacosServiceManager manager = mock(NacosServiceManager.class);
        when(manager.getNamingService()).thenReturn(namingService);

        new ApplicationContextRunner()
                .withPropertyValues("spring.cloud.nacos.discovery.group=G1_GROUP")
                .withBean(NacosServiceManager.class, () -> manager)
                .withBean(GrpcServerProperties.class, GrpcServerProperties::new)
                .withBean(NacosRegistration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(NacosRegistration.class).register();

                    verify(namingService).registerInstance(
                            eq(SERVICE_NAME), eq("G1_GROUP"), any(Instance.class));
                });
    }

    // ────────────────────────────────────────────────────────────────────
    // 2. 默认行为零变化
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ 默认行为零变化：不配 group 时仍是 USER_GROUP（= 修前的写死值）")
    void defaultGroupIsUnchanged() {
        NamingService namingService = mock(NamingService.class);
        NacosServiceManager manager = mock(NacosServiceManager.class);
        when(manager.getNamingService()).thenReturn(namingService);

        new ApplicationContextRunner()
                .withBean(NacosServiceManager.class, () -> manager)
                .withBean(GrpcServerProperties.class, GrpcServerProperties::new)
                .withBean(NacosRegistration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    context.getBean(NacosRegistration.class).register();

                    verify(namingService).registerInstance(
                            eq(SERVICE_NAME), eq(LEGACY_HARDCODED_GROUP), any(Instance.class));
                });
    }

    // ────────────────────────────────────────────────────────────────────
    // 3. yml 里那一行还在
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("✅ application.yaml 里仍声明了 group（删掉 ⇒ NACOS_GROUP 又失效）")
    void applicationYamlStillDeclaresGroup() {
        List<PropertySource<?>> sources = loadApplicationYaml();
        assertThat(sources).isNotEmpty();

        Object raw = sources.get(0).getProperty("spring.cloud.nacos.discovery.group");
        assertThat(raw)
                .as("user-rpc 的 application.yaml 必须保留 spring.cloud.nacos.discovery.group，"
                        + "它同时是「NACOS_GROUP 环境变量」的入口和「默认 group」的来源")
                .isNotNull();
        assertThat(String.valueOf(raw)).contains("NACOS_GROUP");
    }

    // ────────────────────────────────────────────────────────────────────
    // helpers
    // ────────────────────────────────────────────────────────────────────

    private static List<PropertySource<?>> loadApplicationYaml() {
        try {
            return new YamlPropertySourceLoader()
                    .load("user-rpc-application", new ClassPathResource("application.yaml"));
        } catch (IOException e) {
            throw new IllegalStateException("读不到 classpath:application.yaml", e);
        }
    }
}
