package com.rag.follow.follow.rpc;

import io.grpc.NameResolver;
import io.grpc.SynchronizationContext;
import net.devh.boot.grpc.client.nameresolver.DiscoveryClientResolverFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

/**
 * <b>生产 gRPC client 地址的守门测试</b> —— 锁住 {@code grpc.client.user-rpc.address} 的写法。
 *
 * <h2>为什么需要这条测试（它挡住的是「服务根本起不来」这一类硬失败）</h2>
 * <p>2026-10-24 的 follow 域组件级对照挖出两条**硬启动阻塞**，而当时
 * {@code follow-rpc} 的 <b>63 条测试全绿</b>、{@code mvn package} 通过、
 * Spring 上下文在测试里能起来：
 * <ol>
 *   <li>本模块的 {@code io.grpc:grpc-inprocess} 被写成 {@code <scope>test</scope>}
 *       ⇒ 运行时 classpath 上没有它 ⇒ devh 的 channel 工厂链接失败
 *       ⇒ {@code NoClassDefFoundError} ⇒ <b>整个服务起不来</b>。</li>
 *   <li>本模块的 client 地址被写成 {@code discovery://user-rpc}（<b>两斜杠</b>）
 *       ⇒ devh 判 {@code uri.getPath()} 长度为 0 ⇒ {@code IllegalArgumentException}
 *       ⇒ <b>整个服务起不来</b>。</li>
 * </ol>
 *
 * <p><b>为什么 63 条测试都没发现</b>：{@code FollowRpcWiringTest} 用
 * {@code @SpringBootTest(properties = "grpc.client.user-rpc.address=static://127.0.0.1:1")}
 * 把地址覆盖成了 static ⇒ <b>生产地址从未被任何测试跑过</b>；
 * 而 {@code grpc-inprocess} 本来就在 test classpath 上 ⇒ <b>生产 classpath 也没被跑过</b>。
 * 这正是 AGENTS.md §3.3 说的「<b>跑绿 ≠ 对</b>」——
 * 「测试全绿」与「服务能启动」之间隔着一个 classpath 和一个配置文件。
 *
 * <h2>本类验的是<b>生产文件</b>，不是测试覆盖值</h2>
 * <p>断言直接读 {@code src/main/resources/application.yml}（并顺带核对
 * {@code target/classes} 里打包进去的那份是否一致），
 * <b>不经过任何 {@code @SpringBootTest(properties=...)} 覆盖</b>。
 *
 * <h2>为什么还要真的调 devh 的 {@code newNameResolver}</h2>
 * <p>光断言「字符串是三斜杠」只证明<b>我们自己写对了</b>，
 * 不证明<b>devh 接受</b>。本类直接调用 devh 真正的消费点
 * {@link DiscoveryClientResolverFactory#newNameResolver}：
 * <ul>
 *   <li>生产地址 ⇒ <b>不抛</b>异常（否则服务起不来）；</li>
 *   <li>两斜杠写法 ⇒ <b>必须抛</b> {@link IllegalArgumentException}
 *       （证明这条守门测试真的在守，而不是恒真）。</li>
 * </ul>
 * 判据出处（devh 3.1.0.RELEASE）：
 * {@code net/devh/boot/grpc/client/nameresolver/DiscoveryClientResolverFactory.java}
 * 的 {@code newNameResolver} —— 它取 {@code targetUri.getPath()}，
 * 要求<b>非 null、长度 > 1、以 "/" 开头</b>。
 *
 * <h2>Go 出处与语义等价论证</h2>
 * <p><b>Go 出处</b>：{@code service/follow/rpc/etc/follow.yaml:37-41}
 * {@code UserRpc.Etcd.Key: user.rpc} —— Go 用 etcd 做服务发现，键是 {@code user.rpc}；
 * RAG 侧换成 Nacos，服务名是 {@code user-rpc}（{@code spring.application.name}）。
 * <p><b>语义等价论证</b>：两侧都是「按<b>服务名</b>解析出目标地址」这一件事，
 * 只是注册中心不同（etcd vs Nacos）⇒ 表达式不同但行为同构。
 * 写法的硬约束来自 devh 的 URI 格式校验，与 Go 无关。
 */
class FollowRpcProductionAddressTest {

    /** 生产 client 的服务名 —— 必须与 {@code spring.application.name} 对应的 Nacos 服务名一致。 */
    private static final String EXPECTED_SERVICE_NAME = "user-rpc";

    /** 生产地址的完整写法：<b>三个斜杠</b>。 */
    private static final String EXPECTED_ADDRESS = "discovery:///user-rpc";

    @Test
    @DisplayName("生产 application.yml 的 grpc client 地址必须是 discovery:///user-rpc（三斜杠）")
    void productionAddressUsesThreeSlashes() throws IOException {
        String address = productionAddress();

        assertEquals(EXPECTED_ADDRESS, address,
                "生产 gRPC client 地址必须是 " + EXPECTED_ADDRESS + "。"
                        + "两斜杠（discovery://user-rpc）会让 devh 判 uri.getPath() 长度为 0 ⇒ "
                        + "启动期抛 IllegalArgumentException ⇒ 整个服务起不来；"
                        + "而现有测试都把地址覆盖成 static://，所以这条永远测不到。");
    }

    @Test
    @DisplayName("生产地址必须能被 devh 真正的消费点接受（两斜杠写法必须被拒绝）")
    void devhNameResolverAcceptsProductionAddressAndRejectsTwoSlash() throws IOException {
        DiscoveryClientResolverFactory factory =
                new DiscoveryClientResolverFactory(mock(DiscoveryClient.class));
        NameResolver.Args args = NameResolver.Args.newBuilder()
                .setDefaultPort(50051)
                .setProxyDetector(target -> null)
                .setSynchronizationContext(new SynchronizationContext((d, e) -> { }))
                // ServiceConfigParser 是**抽象类**（不是函数接口），必须匿名子类
                .setServiceConfigParser(new NameResolver.ServiceConfigParser() {
                    @Override
                    public NameResolver.ConfigOrError parseServiceConfig(Map<String, ?> rawServiceConfig) {
                        return null;
                    }
                })
                .build();

        String production = productionAddress();

        assertDoesNotThrow(
                () -> factory.newNameResolver(URI.create(production), args),
                "生产地址 " + production + " 必须能被 devh 的 DiscoveryClientResolverFactory 接受"
                        + "（它取 uri.getPath()，要求非 null、长度>1、以 '/' 开头）");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> factory.newNameResolver(URI.create("discovery://user-rpc"), args),
                "两斜杠写法必须被 devh 拒绝 —— 这条断言失败说明本守门测试已经恒真，失去意义");
        assertTrue(thrown.getMessage().contains("Incorrectly formatted target uri"),
                "拒绝原因应是 URI 格式错误，实际：" + thrown.getMessage());
    }

    @Test
    @DisplayName("生产地址解析出的服务名必须是 user-rpc（与 Nacos 注册名一致）")
    void productionAddressResolvesToExpectedServiceName() throws IOException {
        String path = URI.create(productionAddress()).getPath();

        assertEquals("/" + EXPECTED_SERVICE_NAME, path,
                "devh 会把 path 去掉首斜杠当服务名 ⇒ 必须等于 Nacos 上的服务名 " + EXPECTED_SERVICE_NAME
                        + "（Go 侧对应 etcd 键 user.rpc，service/follow/rpc/etc/follow.yaml:37-41）");
    }

    // ==================================================================
    // 取生产配置
    // ==================================================================

    /**
     * 读<b>生产</b> application.yml 里的 {@code grpc.client.user-rpc.address}。
     *
     * <p>刻意不走 {@code ClassPathResource("application.yml")}：classpath 上可能同时存在
     * 多个模块的 application.yml（依赖 jar 里的 + 本模块的），命中哪个不确定 ⇒
     * 这里按<b>源码树路径</b>定位本模块的那份，并用 {@code target/classes} 里打包的副本交叉核对。
     */
    private static String productionAddress() throws IOException {
        Path yml = locateModuleYml();
        String address = readAddress(new FileSystemResource(yml));

        Path packaged = yml.getParent().getParent().getParent().getParent()
                .resolve("target").resolve("classes").resolve("application.yml");
        if (Files.exists(packaged)) {
            String packagedAddress = readAddress(new FileSystemResource(packaged));
            assertEquals(address, packagedAddress,
                    "target/classes 里打包的 application.yml 与源码树不一致 —— "
                            + "说明上一次构建产物是旧的，测试结论不可信");
        }
        return address;
    }

    private static String readAddress(org.springframework.core.io.Resource resource) throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application.yml", resource);
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty("grpc.client.user-rpc.address");
            if (value != null) {
                return value.toString();
            }
        }
        fail("application.yml 里没有 grpc.client.user-rpc.address —— 生产 client 无地址，服务起不来");
        return null;
    }

    /**
     * 向上找本模块的 {@code src/main/resources/application.yml}。
     *
     * <p>Surefire 的工作目录默认是模块目录（{@code ${basedir}}），
     * 但从仓库根跑 {@code mvn -pl} 时也可能不同 ⇒ 逐级向上找，
     * 且要求命中路径里带 {@code follow-rpc}，避免误取别的模块。
     */
    private static Path locateModuleYml() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("src").resolve("main").resolve("resources")
                    .resolve("application.yml");
            if (Files.exists(candidate) && dir.toString().contains("follow-rpc")) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "找不到 follow-rpc 的 src/main/resources/application.yml（工作目录：" + Paths.get("").toAbsolutePath() + "）");
    }
}
